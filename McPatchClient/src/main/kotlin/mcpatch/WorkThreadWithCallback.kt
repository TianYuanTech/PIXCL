package mcpatch

import com.lee.bsdiff.BsPatch
import mcpatch.callback.ProgressCallback
import mcpatch.core.PatchFileReader
import mcpatch.data.GlobalOptions
import mcpatch.data.ModificationMode
import mcpatch.exception.DoNotHideFileException
import mcpatch.exception.InvalidVersionException
import mcpatch.exception.InvalidVersionNameException
import mcpatch.exception.PatchCorruptedException
import mcpatch.extension.FileExtension.bufferedInputStream
import mcpatch.extension.FileExtension.bufferedOutputStream
import mcpatch.extension.StreamExtension.copyAmountTo1
import mcpatch.localization.LangNodes
import mcpatch.localization.Localization
import mcpatch.logging.FileHandler
import mcpatch.logging.Log
import mcpatch.server.MultipleServers
import mcpatch.util.*
import java.io.File
import java.io.FileNotFoundException
import java.util.*

/**
 * @description: 带进度回调的工作线程，替代原有的McPatchWindow显示方式
 * 实现完整的文件更新逻辑，包括补丁应用和版本号更新
 */
class WorkThreadWithCallback(
    private val progressCallback: ProgressCallback?,
    val options: GlobalOptions,
    val updateDir: File2,
    val progDir: File2,
) : Thread() {

    var downloadedVersionCount = 0

    /**
     * @description: McPatch工作线程主逻辑
     * 执行完整的文件更新流程，与WorkThread逻辑保持一致
     */
    override fun run() {
        MultipleServers(options).use { servers ->
            // 修正：使用正确的字段名 versionFile
            val currentVersionFile = progDir + options.verionFile
            val (versionFileContent, encoded) = if (currentVersionFile.exists) tryDecodeVersionFile(currentVersionFile.content) else Pair("", false)

            val allVersions = servers.fetchText(options.versionsFileName).split("\n").filter { it.isNotEmpty() }
            val newestVersion = allVersions.lastOrNull()
            val currentVersion = if (currentVersionFile.exists) versionFileContent else null
            val downloadedVersions = mutableListOf<String>()

            Log.debug("all versions: ")
            allVersions.forEach { Log.debug("  - $it") }
            Log.info("current version: $currentVersion, newest version: $newestVersion")
            Log.info("version file path: ${currentVersionFile.path}")

            if (newestVersion == null)
                throw InvalidVersionException("服务端版本号列表为空，更新失败！")

            // 版本检查和更新处理
            if (currentVersion != newestVersion) {
                progressCallback?.updateLabel(Localization[LangNodes.fetch_metadata])

                if (currentVersion !in allVersions && currentVersion != null) {
                    if (!options.autoRestartVersion)
                        throw InvalidVersionException("当前客户端版本号 $currentVersion 不在服务端的版本号列表里，无法确定版本前后关系，更新失败！")

                    Log.info("restarted the version")
                }

                val position = allVersions.indexOf(currentVersion)
                val missingVersions = allVersions.drop(if (position == -1) 0 else position + 1)
                downloadedVersions.addAll(missingVersions)

                Log.info("missing versions: $missingVersions")

                // 收集到的更新记录
                val changeLogs = mutableListOf<Pair<String, String>>()

                try {
                    // 处理缺失版本的下载和更新
                    for (version in missingVersions) {
                        processVersionUpdate(version, servers, currentVersionFile, encoded, changeLogs)
                    }

                    // 最终版本号更新 - 确保使用最新版本号
                    if (downloadedVersions.isNotEmpty()) {
                        updateFinalVersion(currentVersionFile, newestVersion, encoded)
                    }

                } finally {
                    // 处理更新记录显示
                    if (changeLogs.isNotEmpty()) {
                        showChangeLogs(changeLogs)
                    }
                }
            } else {
                Log.info("当前版本已是最新版本: $currentVersion")
            }

            // 提示没有更新
            if (downloadedVersions.isEmpty() && options.showFinishMessage && !options.quietMode) {
                progressCallback?.showCompletionMessage(false)
            }

            // 输出调试信息
            if (downloadedVersions.isNotEmpty())
                Log.info("successfully applied these versions：$downloadedVersions")
            else
                Log.info("no missing versions and all files is up-to-date!")

            Log.info("continue to start Minecraft!")
            downloadedVersionCount = downloadedVersions.size
        }
    }

    /**
     * @description: 处理单个版本的完整更新过程
     * 包括下载、补丁应用、版本号更新等完整逻辑
     */
    private fun processVersionUpdate(
        version: String,
        servers: MultipleServers,
        currentVersionFile: File2,
        encoded: Boolean,
        changeLogs: MutableList<Pair<String, String>>
    ) {
        try {
            Log.openTag(version)

            progressCallback?.updateLabel("正在下载资源更新包 $version")

            // 下载更新包
            val patchFile = downloadPatchFile("$version.mcpatch.zip", version, servers)

            // 读取文件
            val reader = PatchFileReader(version, patchFile)
            val meta = reader.meta

            // 不能更新自己
            val jarPath = Environment.JarFile
            if (jarPath != null) {
                val relativePath = jarPath.relativizedBy(updateDir)

                if (meta.oldFiles.remove(relativePath))
                    Log.warn("skiped the old file $relativePath, because it is not allowed to update the McPatchClient execuable file itself")

                if (meta.newFiles.removeIf { it.path == relativePath })
                    Log.warn("skiped the new file $relativePath, because it is not allowed to update the McPatchClient execuable file itself")
            }

            progressCallback?.updateLabel("正在解压更新包 $version")

            // 输出日志
            meta.moveFiles.forEach { Log.debug("move files: ${it.from} => ${it.to}") }
            meta.oldFiles.forEach { Log.debug("old files: $it") }
            meta.oldFolders.forEach { Log.debug("old dirs:  $it") }
            meta.newFiles.forEach { Log.debug("new files: $it") }
            meta.newFolders.forEach { Log.debug("new dirs:  $it") }

            // 不能更新日志文件
            val logFile = (Log.handlers.firstOrNull { it is FileHandler } as FileHandler?)?.logFile
            var logFileUpdated = false
            logFileUpdated = logFileUpdated or meta.moveFiles.removeIf { (updateDir + it.from) == logFile || (updateDir + it.to) == logFile }
            logFileUpdated = logFileUpdated or meta.oldFiles.removeIf { (updateDir + it) == logFile }
            logFileUpdated = logFileUpdated or meta.newFiles.removeIf { (updateDir + it.path) == logFile }

            if (logFileUpdated)
                Log.warn("Do not try to update the logging file of McPatchClient!")

            // 处理移动，删除和创建目录
            meta.moveFiles.forEach {
                val from = updateDir + it.from
                val to = updateDir + it.to
                if (from.exists)
                    from.move(to)
            }
            meta.oldFiles.map { (updateDir + it) }.forEach { it.delete() }
            meta.oldFolders.map { (updateDir + it) }.forEach { it.delete() }
            meta.newFolders.map { (updateDir + it) }.forEach { it.mkdirs() }

            // 处理所有新文件
            val timer = IntervalTimer(150)
            val skipped = mutableListOf<String>()

            for (entry in reader) {
                val rawFile = updateDir + entry.newFile.path
                val tempFile = rawFile.parent + (rawFile.name + ".mcpatch-temporal.bin")

                rawFile.parent.mkdirs()

                // 更新UI进度显示
                if (timer.timeout) {
                    progressCallback?.updateProgress("正在处理: ${entry.newFile.path}", 0)
                }

                when (entry.mode) {
                    ModificationMode.Empty -> {
                        Log.info("Empty: ${entry.newFile.path}")
                        rawFile.delete()
                        rawFile.create()
                    }

                    ModificationMode.Fill -> {
                        Log.info("Fill: ${entry.newFile.path}")

                        // 如果本地文件已经存在，且校验一致，就跳过更新
                        if (rawFile.exists && HashUtils.sha1(rawFile.file) == entry.newFile.newHash) {
                            skipped.add(entry.newFile.path)
                            continue
                        }

                        var time = System.currentTimeMillis() + 300

                        tempFile.file.bufferedOutputStream().use { dest ->
                            entry.getInputStream().use { src ->

                                // 解压数据
                                src.copyAmountTo1(dest, entry.newFile.rawLength, callback = { copied, total ->
                                    if (System.currentTimeMillis() - time < 100)
                                        return@copyAmountTo1
                                    time = System.currentTimeMillis()
                                    val progress = ((copied.toFloat() / total.toFloat()) * 1000).toInt()
                                    progressCallback?.updateProgress("解压: ${entry.newFile.path}", progress)
                                })
                            }
                        }

                        if (HashUtils.sha1(tempFile.file) != entry.newFile.newHash)
                            throw PatchCorruptedException(version, "中的文件更新后数据校验不通过 ${entry.newFile.path} (fill)", 0)
                    }

                    ModificationMode.Modify -> {
                        val notFound = !rawFile.exists
                        val notMatched =
                            if (notFound) false else HashUtils.sha1(rawFile.file) != entry.newFile.oldHash

                        // 文件不存在或者校验不匹配
                        val extraMsg = if (notFound) " (skip because the old file not found)" else
                            if (notMatched) " (skip because hash not matched)" else ""

                        Log.info("Modify: ${entry.newFile.path}$extraMsg")

                        if (extraMsg.isNotEmpty()) {
                            skipped.add(entry.newFile.path)
                            continue
                        }

                        rawFile.file.bufferedInputStream().use { old ->
                            entry.getInputStream().use { patch ->
                                tempFile.file.bufferedOutputStream().use { result ->
                                    BsPatch().bspatch(old, patch, result, old.available(), entry.newFile.rawLength.toInt())
                                }
                            }
                        }

                        if (HashUtils.sha1(tempFile.file) != entry.newFile.newHash)
                            throw PatchCorruptedException(version, "中的文件更新后数据校验不通过 ${entry.newFile.path} (modify)", 0)
                    }
                }
            }

            progressCallback?.updateLabel("正在合并更新数据，请不要关闭程序")

            // 合并临时文件
            for ((index, newFile) in meta.newFiles.withIndex()) {
                val rawFile = updateDir + newFile.path
                val tempFile = rawFile.parent + (rawFile.name + ".mcpatch-temporal.bin")

                if ((newFile.mode == ModificationMode.Fill || newFile.mode == ModificationMode.Modify)
                    && newFile.path !in skipped
                ) {
                    tempFile.move(rawFile)
                }

                if (timer.timeout) {
                    val progress = index * 1000 / meta.newFiles.size
                    progressCallback?.updateProgress("合并文件: $index/${meta.newFiles.size}", progress)
                }
            }

            progressCallback?.updateLabel("正在更新版本号")

            // 更新版本号 - 关键修复点
            try {
                // 确保版本文件所在目录存在
                val versionFileParent = currentVersionFile.parent
                if (!versionFileParent.exists) {
                    versionFileParent.mkdirs()
                    Log.debug("创建版本文件目录: ${versionFileParent.path}")
                }

                // 写入版本号
                currentVersionFile.content = tryEncodeVersionFile(version, encoded)

                Log.info("版本号已更新为: $version")
                Log.debug("版本文件路径: ${currentVersionFile.path}")

                // 验证写入是否成功
                if (currentVersionFile.exists) {
                    val verifyContent = if (encoded) {
                        tryDecodeVersionFile(currentVersionFile.content).first
                    } else {
                        currentVersionFile.content
                    }

                    if (verifyContent == version) {
                        Log.debug("版本文件验证成功，版本号: $verifyContent")
                    } else {
                        Log.warn("版本文件验证失败，期望: $version, 实际: $verifyContent")
                    }
                } else {
                    Log.error("版本文件写入失败，文件不存在: ${currentVersionFile.path}")
                }

            } catch (e: FileNotFoundException) {
                throw DoNotHideFileException(currentVersionFile)
            } catch (e: Exception) {
                Log.error("更新版本文件时发生错误: ${e.message}")
                Log.error(e.stackTraceToString())
                throw e
            }

            progressCallback?.updateLabel("正在做最后的清理工作")

            // 删除更新包
            patchFile.delete()

            progressCallback?.updateLabel("更新完成")

            // 处理更新记录
            if (options.showChangelogs) {
                changeLogs.add(Pair(version, meta.changeLogs.trim()))
            } else {
                val content = meta.changeLogs.trim()
                Log.info("========== $version ==========")
                if (content.isNotEmpty()) {
                    Log.info(content)
                    Log.info("")
                }
            }

            Log.info("版本 $version 处理完成")

        } catch (e: Exception) {
            Log.error("处理版本 $version 时发生错误: ${e.message}")
            throw e
        } finally {
            Log.closeTag()
        }
    }

    /**
     * @description: 更新最终版本号
     * 确保版本文件包含最新的版本号
     */
    private fun updateFinalVersion(currentVersionFile: File2, newestVersion: String, encoded: Boolean) {
        try {
            progressCallback?.updateLabel("正在完成最终版本号更新")

            // 确保版本文件所在目录存在
            val versionFileParent = currentVersionFile.parent
            if (!versionFileParent.exists) {
                versionFileParent.mkdirs()
                Log.debug("创建版本文件目录: ${versionFileParent.path}")
            }

            // 将版本号更新为最新版本
            currentVersionFile.content = tryEncodeVersionFile(newestVersion, encoded)

            Log.info("最终版本号已更新为: $newestVersion")
            Log.debug("最终版本文件路径: ${currentVersionFile.path}")

        } catch (e: Exception) {
            Log.error("最终版本号更新失败: ${e.message}")
            Log.error(e.stackTraceToString())
            throw e
        }
    }

    /**
     * @description: 显示更新记录
     * 通过进度回调显示更新记录信息
     */
    private fun showChangeLogs(changeLogs: List<Pair<String, String>>) {
        if (changeLogs.isNotEmpty()) {
            val content = changeLogs.joinToString("\n\n\n") { cl ->
                val title = cl.first
                val content = cl.second.ifEmpty { "已更新" }
                "========== $title ==========\n$content"
            }

            // 通过回调显示更新记录
            progressCallback?.showChangeLogs("更新记录", content, options.autoCloseChangelogs)
        }
    }

    /**
     * @description: 下载补丁文件，集成进度回调
     * 与WorkThread保持相同的下载逻辑
     */
    private fun downloadPatchFile(relativePath: String, version: String, servers: MultipleServers): File2 {
        val tempFile = File2(File.createTempFile("mcpatch-$version", ".zip"))
        val sampler = SpeedSampler(3000)
        var time = System.currentTimeMillis()

        Log.info("开始下载补丁文件: $relativePath")
        Log.debug("临时文件路径: ${tempFile.path}")

        servers.downloadFile(relativePath, tempFile) { packageLength, bytesReceived, lengthExpected ->
            // 检查是否需要中断
            if (progressCallback?.shouldInterrupt() == true) {
                Log.info("下载被用户中断")
                return@downloadFile
            }

            sampler.feed(packageLength.toInt())

            // 每隔200毫秒更新一次UI
            if (System.currentTimeMillis() - time < 200)
                return@downloadFile
            time = System.currentTimeMillis()

            val progress = bytesReceived / lengthExpected.toFloat() * 100
            val progressText = String.format("%.1f", progress)
            val currentBytes = MiscUtils.convertBytes(bytesReceived)
            val totalBytes = MiscUtils.convertBytes(lengthExpected)
            val speedText = MiscUtils.convertBytes(sampler.speed)

            val displayText = "$progressText%  -  $currentBytes/$totalBytes   -   $speedText/s"
            val progressValue = (progress * 10).toInt()

            progressCallback?.updateProgress(displayText, progressValue)
        }

        Log.info("补丁文件下载完成: $relativePath")
        return tempFile
    }

    /**
     * @description: 尝试解码版本文件内容
     * @param text 版本文件原始内容
     * @return Pair<解码后的版本号, 是否为编码格式>
     */
    private fun tryDecodeVersionFile(text: String): Pair<String, Boolean> {
        if (!text.startsWith(":"))
            return Pair(text, false)

        try {
            return Pair(Base64.getDecoder().decode(text.drop(1)).decodeToString(), true)
        } catch (e: IllegalArgumentException) {
            throw InvalidVersionNameException()
        }
    }

    /**
     * @description: 尝试编码版本文件内容
     * @param text 要编码的版本号
     * @param encode 是否需要编码
     * @return 编码后的内容（如果需要编码）或原始内容
     */
    private fun tryEncodeVersionFile(text: String, encode: Boolean): String {
        if (!encode)
            return text

        return ":" + Base64.getEncoder().encodeToString(text.encodeToByteArray())
    }
}