package mcpatch

import com.github.kasuminova.GUI.SetupSwing
import mcpatch.callback.ProgressCallback
import mcpatch.config.HardcodedConfig
import mcpatch.data.GlobalOptions
import mcpatch.exception.*
import mcpatch.extension.RuntimeExtension.usedMemory
import mcpatch.gui.McPatchWindow
import mcpatch.localization.LangNodes
import mcpatch.localization.Localization
import mcpatch.logging.ConsoleHandler
import mcpatch.logging.FileHandler
import mcpatch.logging.Log
import mcpatch.util.DialogUtils
import mcpatch.util.Environment
import mcpatch.util.File2
import mcpatch.util.MiscUtils
import org.json.JSONException
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.InterruptedIOException
import java.nio.channels.ClosedByInterruptException
import java.util.jar.JarFile
import kotlin.system.exitProcess

class McPatchClient
{
    /**
     * McPatchClient主逻辑
     * @param graphicsMode 是否以图形模式启动（桌面环境通常以图形模式启动，安卓环境通常不以图形模式启动）
     * @param hasStandaloneProgress 程序是否拥有独立的进程。从JavaAgent参数启动没有独立进程，双击启动有独立进程（java -jar xx.jar也属于独立启动）
     * @param externalConfigFile 可选的外部配置文件路径，如果为空则使用硬编码配置
     * @param enableLogFile 是否写入日志文件
     * @param disableTheme 是否禁用主题，此选项和配置文件中的选项任意一个为true都会禁用主题
     */
    fun run(
        graphicsMode: Boolean,
        hasStandaloneProgress: Boolean,
        externalConfigFile: File2?,
        enableLogFile: Boolean,
        disableTheme: Boolean,
    ): Boolean {
        try {
            val workDir = getWorkDirectory()
            val progDir = getProgramDirectory(workDir)

            // 使用硬编码配置，服务器地址由外部传入
            val defaultServerUrl = ""
            val options = GlobalOptions.CreateFromMap(readConfig(externalConfigFile, workDir, defaultServerUrl))
            val updateDir = getUpdateDirectory(workDir, options)

            // 初始化日志系统
            if (enableLogFile) {
                val logFilePath = getLogFilePath(workDir, progDir, graphicsMode)
                Log.addHandler(FileHandler(Log, logFilePath))
            }

            val consoleLogLevel = if (Environment.IsProduction)
                (if (graphicsMode || !enableLogFile) Log.LogLevel.DEBUG else Log.LogLevel.INFO)
            else
                Log.LogLevel.DEBUG
            Log.addHandler(ConsoleHandler(Log, consoleLogLevel))
            if (!hasStandaloneProgress)
                Log.openTag("McPatchClient")

            // 收集并打印环境信息
            Log.info("RAM: " + MiscUtils.convertBytes(Runtime.getRuntime().usedMemory()))
            Log.info("Graphics Mode: $graphicsMode")
            Log.info("Standalone: $hasStandaloneProgress")
            val jvmVersion = System.getProperty("java.version")
            val jvmVender = System.getProperty("java.vendor")
            val osName = System.getProperty("os.name")
            val osArch = System.getProperty("os.arch")
            val osVersion = System.getProperty("os.version")
            Log.info("Updating Directory:   ${updateDir.path}")
            Log.info("Working Directory:    ${workDir.path}")
            Log.info("Executable Directory: ${if(Environment.IsProduction) Environment.JarFile!!.parent.path else "dev-mode"}")
            Log.info("Application Version:  ${Environment.Version} (${Environment.GitCommit})")
            Log.info("Java virtual Machine: $jvmVender $jvmVersion")
            Log.info("Operating System: $osName $osVersion $osArch")

            // 记录服务器配置信息
            val serverList = options.server
            Log.info("Server Configuration: ${serverList.size} servers configured")
            serverList.forEachIndexed { index, server ->
                Log.info("  Server ${index + 1}: $server")
            }

            Localization.init(readLangs())

            // 应用主题
            if (graphicsMode && !disableTheme && !options.disableTheme)
                SetupSwing.init()

            // 初始化UI
            val window = if (graphicsMode) McPatchWindow() else null

            // 弹出窗口
            if (!options.quietMode)
                window?.show()

            // 初始化窗口
            window?.titleTextSuffix = ""
            window?.titleText = Localization[LangNodes.window_title]
            window?.labelText = Localization[LangNodes.connecting_message]

            // 将更新任务单独放进单独线程执行，方便随时打断线程
            val workthread = WorkThread(window, options, updateDir, progDir)
            var exception: Throwable? = null
            workthread.isDaemon = true
            workthread.setUncaughtExceptionHandler { _, e -> exception = e }

            // 点击窗口的叉时停止更新任务
            window?.onWindowClosing?.once { _ ->
                if (workthread.isAlive)
                    workthread.interrupt()
            }

            // 启动更新任务
            workthread.start()
            workthread.join()

            // 退出窗口
            window?.destroy()

            // 处理工作线程里的异常
            if (exception != null)
            {
                val ex = exception!!

                if (ex !is InterruptedException &&
                    ex !is InterruptedIOException &&
                    ex !is ClosedByInterruptException)
                {
                    try {
                        Log.error(ex.javaClass.name)
                        Log.error(ex.stackTraceToString())
                    } catch (e: Exception) {
                        println("------------------------")
                        println(e.javaClass.name)
                        println(e.stackTraceToString())
                    }

                    if (graphicsMode)
                    {
                        val appVersion = "${Environment.Version} (${Environment.GitCommit})"
                        val className = if (ex !is BaseException) ex.javaClass.name + "\n" else ""
                        val errMessage = MiscUtils.stringBreak(className + (ex.message ?: "<No Exception Message>"), 80)
                        val title = "发生错误 $appVersion"
                        var content = errMessage + "\n"
                        content += if (!hasStandaloneProgress) "点击\"是\"显示错误详情并停止启动Minecraft，" else "点击\"是\"显示错误详情并退出，"
                        content += if (!hasStandaloneProgress) "点击\"否\"继续启动Minecraft" else "点击\"否\"直接退出程序"
                        val choice = DialogUtils.confirm(title, content)

                        if (!hasStandaloneProgress)
                        {
                            if (choice)
                            {
                                DialogUtils.error("错误详情 $appVersion", ex.stackTraceToString())
                                throw ex
                            }
                        } else {
                            if (choice)
                                DialogUtils.error("错误详情 $appVersion", ex.stackTraceToString())
                            throw ex
                        }
                    } else {
                        if (options.noThrowing)
                            println("文件更新失败！但因为设置了no-throwing参数，游戏仍会继续运行！\n\n\n")
                        else
                            throw ex
                    }
                } else {
                    Log.info("updating thread interrupted by user")
                }
            } else {
                return workthread.downloadedVersionCount > 0
            }
        } catch (e: UpdateDirNotFoundException) {
            if (graphicsMode)
                DialogUtils.error("", e.message ?: "<No Exception Message>")
            else
                throw e
        } catch (e: ConfigFileNotFoundException) {
            if (graphicsMode)
                DialogUtils.error("", e.message ?: "<No Exception Message>")
            else
                throw e
        } catch (e: FailedToParsingException) {
            if (graphicsMode)
                DialogUtils.error("", e.message ?: "<No Exception Message>")
            else
                throw e
        } catch (e: InvalidConfigFileException) {
            if (graphicsMode)
                DialogUtils.error("", e.message ?: "<No Exception Message>")
            else
                throw e
        } finally {
            Log.info("RAM: " + MiscUtils.convertBytes(Runtime.getRuntime().usedMemory()))

            if (hasStandaloneProgress)
                exitProcess(0)
        }

        return false
    }

    /**
     * 向上搜索，直到有一个父目录包含.minecraft目录，然后返回这个父目录。最大搜索7层目录
     * @param basedir 从哪个目录开始向上搜索
     * @return 包含.minecraft目录的父目录。如果找不到则返回Null
     */
    fun searchDotMinecraft(basedir: File2): File2?
    {
        try {
            var d = basedir

            for (i in 0 until 7)
            {
                if (d.contains(".minecraft"))
                    return d

                d = d.parent
            }
        } catch (e: NullPointerException) {
            return null
        }

        return null
    }

    /**
     * @description: 获取配置信息，接收外部传入的服务器URL
     * @param external1 外部配置文件路径（如果为null则使用硬编码配置）
     * @param workDir 工作目录
     * @param serverUrl 外部传入的服务器URL地址
     * @return 包含服务器地址的配置文件对象
     */
    fun readConfig(external1: File2?, workDir: File2, serverUrl: String): Map<String, Any>
    {
        return when {
            Environment.IsProduction -> {
                Log.info("使用生产环境配置，服务器地址: $serverUrl")
                HardcodedConfig.getProductionConfig(serverUrl)
            }
            else -> {
                Log.info("使用开发环境配置，服务器地址: $serverUrl")
                HardcodedConfig.getDevConfig(serverUrl)
            }
        }
    }

    /**
     * 从Jar文件内读取语言配置文件（仅图形模式启动时有效）
     * @return 语言配置文件对象
     * @throws ConfigFileNotFoundException 配置文件找不到时
     * @throws FailedToParsingException 配置文件无法解码时
     */
    fun readLangs(): Map<String, String>
    {
        try {
            val content: String
            if (Environment.IsProduction)
                JarFile(Environment.JarFile!!.path).use { jar ->
                    val langFileInZip = jar.getJarEntry("lang.yml") ?: throw ConfigFileNotFoundException("lang.yml")
                    jar.getInputStream(langFileInZip).use { content = it.readBytes().decodeToString() }
                }
            else
                content = (File2(System.getProperty("user.dir")) + "src/main/resources/lang.yml").content

            return Yaml().load(content)
        } catch (e: JSONException) {
            throw FailedToParsingException("语言配置文件lang.yml", "yaml", e.message ?: "")
        }
    }

    /**
     * 获取进程的工作目录
     */
    fun getWorkDirectory(): File2
    {
        return System.getProperty("user.dir").run {
            if(Environment.IsProduction)
                File2(this)
            else
                File2("$this${File.separator}testdir").also { it.mkdirs() }
        }
    }

    /**
     * 获取需要更新的起始目录
     * @throws UpdateDirNotFoundException 当.minecraft目录搜索不到时
     */
    fun getUpdateDirectory(workDir: File2, options: GlobalOptions): File2
    {
        return if(Environment.IsProduction) {
            if (options.basePath != "") Environment.JarFile!!.parent + options.basePath
            else searchDotMinecraft(workDir) ?: throw UpdateDirNotFoundException()
        } else {
            workDir
        }.apply { mkdirs() }
    }

    /**
     * 获取Jar文件所在的目录
     */
    fun getProgramDirectory(workDir: File2): File2
    {
        return if(Environment.IsProduction) Environment.JarFile!!.parent else workDir
    }

    /**
     * @description: 获取日志文件的存储路径，优先使用.minecraft\logs目录
     * @param workDir 工作目录
     * @param progDir 程序目录
     * @param graphicsMode 是否为图形模式
     * @return 日志文件的完整路径
     */
    fun getLogFilePath(workDir: File2, progDir: File2, graphicsMode: Boolean): File2 {
        try {
            val minecraftParentDir = searchDotMinecraft(workDir)

            if (minecraftParentDir != null) {
                val minecraftDir = minecraftParentDir + ".minecraft"
                val logsDir = minecraftDir + "logs"
                logsDir.mkdirs()

                val logFileName = if (graphicsMode) "mc-patch.log" else "mc-patch.log.txt"
                val logFilePath = logsDir + logFileName

                Log.debug("日志文件路径设置为: ${logFilePath.path}")
                return logFilePath
            }
        } catch (e: Exception) {
            Log.warn("无法访问.minecraft\\logs目录，使用默认日志路径: ${e.message}")
        }

        val fallbackFileName = if (graphicsMode) "mc-patch.log" else "mc-patch.log.txt"
        val fallbackPath = progDir + fallbackFileName
        Log.debug("使用备用日志文件路径: ${fallbackPath.path}")
        return fallbackPath
    }

    companion object {
        /**
         * @description: 从ModLoader启动（带进度回调版本）
         * @param enableLogFile 是否启用日志文件
         * @param disableTheme 是否禁用主题
         * @param progressCallback 进度回调接口，用于向HMCL反馈进度信息
         * @param serverUrl 外部传入的服务器URL地址
         * @return 是否有文件更新，如果有返回true。其它情况返回false
         */
        @JvmStatic
        fun modloaderWithProgress(
            enableLogFile: Boolean,
            disableTheme: Boolean,
            progressCallback: ProgressCallback?,
            serverUrl: String
        ): Boolean {
            val result = McPatchClient().runWithProgress(
                graphicsMode = false,
                hasStandaloneProgress = false,
                externalConfigFile = null,
                enableLogFile = enableLogFile,
                disableTheme = disableTheme,
                progressCallback = progressCallback,
                serverUrl = serverUrl
            )
            Log.info("finished!")
            return result
        }

        /**
         * @description: 向后兼容的modloader方法（不推荐使用）
         * 保留用于其他可能的调用点，使用默认国内服务器
         */
        @JvmStatic
        @Deprecated("使用带serverUrl参数的modloaderWithProgress方法")
        fun modloader(enableLogFile: Boolean, disableTheme: Boolean): Boolean {
            return modloaderWithProgress(enableLogFile, disableTheme, null, "http://api.pixellive.cn:8080")
        }
    }

    /**
     * @description: 带进度回调和服务器URL的运行方法
     * @param graphicsMode 是否以图形模式启动
     * @param hasStandaloneProgress 程序是否拥有独立的进程
     * @param externalConfigFile 可选的外部配置文件路径
     * @param enableLogFile 是否写入日志文件
     * @param disableTheme 是否禁用主题
     * @param progressCallback 进度回调接口
     * @param serverUrl 外部传入的服务器URL地址
     * @return 是否有文件更新
     */
    fun runWithProgress(
        graphicsMode: Boolean,
        hasStandaloneProgress: Boolean,
        externalConfigFile: File2?,
        enableLogFile: Boolean,
        disableTheme: Boolean,
        progressCallback: ProgressCallback?,
        serverUrl: String
    ): Boolean {
        try {
            val workDir = getWorkDirectory()
            val progDir = getProgramDirectory(workDir)

            val options = GlobalOptions.CreateFromMap(readConfig(externalConfigFile, workDir, serverUrl))
            val updateDir = getUpdateDirectory(workDir, options)

            if (enableLogFile) {
                val logFilePath = getLogFilePath(workDir, progDir, graphicsMode)
                Log.addHandler(FileHandler(Log, logFilePath))
            }

            val consoleLogLevel = if (Environment.IsProduction)
                (if (graphicsMode || !enableLogFile) Log.LogLevel.DEBUG else Log.LogLevel.INFO)
            else
                Log.LogLevel.DEBUG
            Log.addHandler(ConsoleHandler(Log, consoleLogLevel))
            if (!hasStandaloneProgress)
                Log.openTag("McPatchClient")

            Log.info("RAM: " + MiscUtils.convertBytes(Runtime.getRuntime().usedMemory()))
            Log.info("Graphics Mode: $graphicsMode")
            Log.info("Standalone: $hasStandaloneProgress")
            Log.info("使用服务器地址: $serverUrl")

            val jvmVersion = System.getProperty("java.version")
            val jvmVender = System.getProperty("java.vendor")
            val osName = System.getProperty("os.name")
            val osArch = System.getProperty("os.arch")
            val osVersion = System.getProperty("os.version")
            Log.info("Updating Directory:   ${updateDir.path}")
            Log.info("Working Directory:    ${workDir.path}")
            Log.info("Executable Directory: ${if(Environment.IsProduction) Environment.JarFile!!.parent.path else "dev-mode"}")
            Log.info("Application Version:  ${Environment.Version} (${Environment.GitCommit})")
            Log.info("Java virtual Machine: $jvmVender $jvmVersion")
            Log.info("Operating System: $osName $osVersion $osArch")

            Localization.init(readLangs())

            if (graphicsMode && !disableTheme && !options.disableTheme)
                SetupSwing.init()

            progressCallback?.updateTitle(Localization[LangNodes.window_title])
            progressCallback?.updateLabel(Localization[LangNodes.connecting_message])

            val workthread = WorkThreadWithCallback(progressCallback, options, updateDir, progDir)
            var exception: Throwable? = null
            workthread.isDaemon = true
            workthread.setUncaughtExceptionHandler { _, e -> exception = e }

            workthread.start()
            workthread.join()

            if (exception != null) {
                val ex = exception!!

                if (ex !is InterruptedException &&
                    ex !is InterruptedIOException &&
                    ex !is ClosedByInterruptException) {

                    try {
                        Log.error(ex.javaClass.name)
                        Log.error(ex.stackTraceToString())
                    } catch (e: Exception) {
                        println("------------------------")
                        println(e.javaClass.name)
                        println(e.stackTraceToString())
                    }

                    if (options.noThrowing) {
                        println("文件更新失败！但因为设置了no-throwing参数，游戏仍会继续运行！\n\n\n")
                    } else {
                        throw ex
                    }
                } else {
                    Log.info("updating thread interrupted by user")
                }
            } else {
                progressCallback?.showCompletionMessage(workthread.downloadedVersionCount > 0)
                return workthread.downloadedVersionCount > 0
            }

        } catch (e: UpdateDirNotFoundException) {
            throw e
        } catch (e: ConfigFileNotFoundException) {
            throw e
        } catch (e: FailedToParsingException) {
            throw e
        } catch (e: InvalidConfigFileException) {
            throw e
        } finally {
            Log.info("RAM: " + MiscUtils.convertBytes(Runtime.getRuntime().usedMemory()))

            if (hasStandaloneProgress)
                exitProcess(0)
        }

        return false
    }
}