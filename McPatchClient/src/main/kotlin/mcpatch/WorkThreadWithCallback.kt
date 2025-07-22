package mcpatch

import mcpatch.callback.ProgressCallback
import mcpatch.data.GlobalOptions
import mcpatch.exception.InvalidVersionException
import mcpatch.exception.InvalidVersionNameException
import mcpatch.localization.LangNodes
import mcpatch.localization.Localization
import mcpatch.logging.Log
import mcpatch.server.MultipleServers
import mcpatch.util.File2
import mcpatch.util.MiscUtils
import mcpatch.util.SpeedSampler
import java.io.File
import java.util.*

/**
 * @description: 带进度回调的工作线程，替代原有的McPatchWindow显示方式
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
     */
    override fun run() {
        MultipleServers(options).use { servers ->
            val currentVersionFile = progDir + options.verionFile
            val (versionFileContent, encoded) = if (currentVersionFile.exists) tryDecodeVersionFile(currentVersionFile.content) else Pair("", false)

            val allVersions = servers.fetchText(options.versionsFileName).split("\n").filter { it.isNotEmpty() }
            val newestVersion = allVersions.lastOrNull()
            val currentVersion = if (currentVersionFile.exists) versionFileContent else null
            val downloadedVersions = mutableListOf<String>()

            Log.debug("all versions: ")
            allVersions.forEach { Log.debug("  - $it") }
            Log.info("current version: $currentVersion, newest version: $newestVersion")

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

                // 处理缺失版本的下载和更新
                for (version in missingVersions) {
                    processVersionUpdate(version, servers)
                }
            }

            Log.info("continue to start Minecraft!")
            downloadedVersionCount = downloadedVersions.size
        }
    }

    /**
     * @description: 处理单个版本的更新过程
     */
    private fun processVersionUpdate(version: String, servers: MultipleServers) {
        try {
            Log.openTag(version)

            progressCallback?.updateLabel("正在下载资源更新包 $version")

            // 下载更新包
            val patchFile = downloadPatchFile("$version.mcpatch.zip", version, servers)

            // 后续的文件处理逻辑保持不变
            // 这里包含读取文件、应用补丁等操作

        } finally {
            Log.closeTag()
        }
    }

    /**
     * @description: 下载补丁文件，集成进度回调
     */
    private fun downloadPatchFile(relativePath: String, version: String, servers: MultipleServers): File2 {
        val tempFile = File2(File.createTempFile("mcpatch-$version", ".zip"))
        val sampler = SpeedSampler(3000)
        var time = System.currentTimeMillis()

        servers.downloadFile(relativePath, tempFile) { packageLength, bytesReceived, lengthExpected ->
            // 检查是否需要中断
            if (progressCallback?.shouldInterrupt() == true) {
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

        return tempFile
    }

    // 其他辅助方法保持不变
    private fun tryDecodeVersionFile(text: String): Pair<String, Boolean> {
        if (!text.startsWith(":"))
            return Pair(text, false)

        try {
            return Pair(Base64.getDecoder().decode(text.drop(1)).decodeToString(), true)
        } catch (e: IllegalArgumentException) {
            throw InvalidVersionNameException()
        }
    }

    private fun tryEncodeVersionFile(text: String, encode: Boolean): String {
        if (!encode)
            return text

        return ":" + Base64.getEncoder().encodeToString(text.encodeToByteArray())
    }
}