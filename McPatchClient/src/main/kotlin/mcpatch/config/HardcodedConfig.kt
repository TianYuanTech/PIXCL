package mcpatch.config

import mcpatch.logging.Log
import mcpatch.util.File2

/**
 * @description: 硬编码配置类，包含项目的所有配置信息
 */
object HardcodedConfig {

    // 海外API切换相关常量
    private const val KOKUGAI_FILENAME = "kokugai"
    private const val KOKUGAI_CONTENT = "gaikoku"

    // 服务器地址常量
    private const val DOMESTIC_SERVER_HOST = "http://api.pixellive.cn"
    private const val OVERSEAS_SERVER_HOST = "http://tkapi.pixellive.cn"

    /**
     * @description: 检查是否应该使用海外API
     * 检查.hmcl文件夹下是否存在名为"kokugai"的文件，且文件内容为"gaikoku"
     * @return boolean - 如果应该使用海外API返回true，否则返回false
     */
    private fun shouldUseOverseasApi(): Boolean {
        try {
            // 获取.hmcl目录路径
            val hmclDirectory = getHmclDirectory()
            if (hmclDirectory == null) {
                Log.debug("未找到.hmcl目录，使用国内API")
                return false
            }

            val kokugaiFile = hmclDirectory + KOKUGAI_FILENAME

            // 检查文件是否存在
            if (kokugaiFile.exists.not()) {
                Log.debug("kokugai文件不存在，使用国内API")
                return false
            }

            // 检查是否为普通文件
            if (kokugaiFile.isFile.not()) {
                Log.debug("kokugai不是普通文件，使用国内API")
                return false
            }

            // 读取文件内容并检查
            val content = kokugaiFile.content
            val trimmedContent = content.trim()
            val useOverseas = KOKUGAI_CONTENT == trimmedContent

            Log.info("检查kokugai文件 - 内容: '$trimmedContent', 使用海外API: $useOverseas")
            return useOverseas

        } catch (e: Exception) {
            Log.warn("检查kokugai文件时发生错误: ${e.message}")
            return false
        }
    }

    /**
     * @description: 获取.hmcl目录路径
     * @return File2? - .hmcl目录路径，如果找不到返回null
     */
    private fun getHmclDirectory(): File2? {
        try {
            // 获取当前工作目录
            val currentDir = File2(System.getProperty("user.dir"))

            // 向上搜索包含.hmcl目录的父目录，最多搜索10层
            var searchDir = currentDir
            for (i in 0 until 10) {
                val hmclDir = searchDir + ".hmcl"
                if (hmclDir.exists && hmclDir.isDirectory) {
                    Log.debug("找到.hmcl目录: ${hmclDir.path}")
                    return hmclDir
                }

                val parent = searchDir.parent
                if (parent == null || parent.path == searchDir.path) {
                    break
                }
                searchDir = parent
            }

            // 如果向上搜索没有找到，尝试一些常见位置
            val commonLocations = listOf(
                File2(System.getProperty("user.home")) + ".hmcl",
                currentDir + ".hmcl",
                currentDir.parent + ".hmcl"
            )

            for (location in commonLocations) {
                if (location.exists && location.isDirectory) {
                    Log.debug("在常见位置找到.hmcl目录: ${location.path}")
                    return location
                }
            }

        } catch (e: Exception) {
            Log.warn("搜索.hmcl目录时发生错误: ${e.message}")
        }

        return null
    }

    /**
     * @description: 生成服务器地址列表
     * 根据kokugai文件决定使用国内还是海外服务器
     * @return List<String> - 服务器地址列表
     */
    private fun generateServerUrls(): List<String> {
        val useOverseas = shouldUseOverseasApi()
        val serverHost = if (useOverseas) {
            Log.info("使用海外服务器: $OVERSEAS_SERVER_HOST")
            OVERSEAS_SERVER_HOST
        } else {
            Log.info("使用国内服务器: $DOMESTIC_SERVER_HOST")
            DOMESTIC_SERVER_HOST
        }

        // 生成端口范围6701-6710的服务器列表
        return (6701..6710).map { port ->
            "$serverHost:$port"
        }
    }

    /**
     * @description: 获取完整的项目配置信息
     * @return 包含所有配置项的Map对象
     */
    fun getConfig(): Map<String, Any> {
        return mapOf(
            // 服务器配置 - 动态生成的服务器地址列表
            "server" to generateServerUrls(),

            // 界面主题配置
            "disable-theme" to false,

            // 用户界面交互配置
            "show-finish-message" to true,
            "show-changelogs-message" to true,
            "changelogs-auto-close" to 0,

            // 运行模式配置
            "quiet-mode" to false,
            "no-throwing" to false,

            // 网络连接超时配置
            "http-connect-timeout" to 3000,
            "http-response-timeout" to 5000,
            "retry-times" to 5,

            // 多线程下载配置
            "concurrent-threads" to 4,
            "concurrent-block-size" to 4194304, // 4MB

            // 版本管理配置
            "auto-restart-version" to true,
            "version-file" to ".minecraft/config/mc-patch-version.txt",
            "server-versions-file-name" to "versions.txt",

            // HTTP协议配置
            "http-headers" to mapOf<String, String>(),
            "ignore-https-certificate" to false,
            "http-fallback-file-size" to 1073741824, // 1 GiB

            // 路径配置
            "base-path" to ""
        )
    }

    /**
     * @description: 获取生产环境配置（优化生产环境使用体验）
     * @return 适用于生产环境的配置Map
     */
    fun getProductionConfig(): Map<String, Any> {
        val baseConfig = getConfig().toMutableMap()

        // 生产环境优化配置
        baseConfig["quiet-mode"] = true // 静默模式，减少干扰
        baseConfig["show-finish-message"] = false // 不显示完成消息
        baseConfig["changelogs-auto-close"] = 5 // 5秒后自动关闭更新记录
        baseConfig["no-throwing"] = false // 确保错误时停止运行

        return baseConfig
    }

    /**
     * @description: 获取开发环境配置（便于调试和测试）
     * @return 适用于开发环境的配置Map
     */
    fun getDevConfig(): Map<String, Any> {
        val baseConfig = getConfig().toMutableMap()

        // 开发环境调试配置
        baseConfig["quiet-mode"] = false // 显示详细进度
        baseConfig["show-finish-message"] = true // 显示完成消息便于调试
        baseConfig["changelogs-auto-close"] = 0 // 不自动关闭，便于查看
        baseConfig["no-throwing"] = true // 允许错误后继续运行

        // 开发环境网络配置优化
        baseConfig["http-connect-timeout"] = 10000 // 更长的连接超时
        baseConfig["http-response-timeout"] = 15000 // 更长的响应超时
        baseConfig["retry-times"] = 3 // 减少重试次数加快调试

        return baseConfig
    }

    /**
     * @description: 获取自定义服务器配置的方法
     * @param serverUrls 自定义的服务器URL列表
     * @return 使用自定义服务器的配置Map
     */
    fun getConfigWithCustomServers(serverUrls: List<String>): Map<String, Any> {
        val baseConfig = getConfig().toMutableMap()
        baseConfig["server"] = serverUrls
        Log.info("使用自定义服务器配置: $serverUrls")
        return baseConfig
    }

    /**
     * @description: 获取高性能配置（适用于高速网络环境）
     * @return 高性能网络环境的配置Map
     */
    fun getHighPerformanceConfig(): Map<String, Any> {
        val baseConfig = getConfig().toMutableMap()

        // 高性能网络配置
        baseConfig["concurrent-threads"] = 8 // 增加并发线程数
        baseConfig["concurrent-block-size"] = 8388608 // 8MB块大小
        baseConfig["http-connect-timeout"] = 1500 // 更短的连接超时
        baseConfig["http-response-timeout"] = 3000 // 更短的响应超时
        baseConfig["retry-times"] = 3 // 减少重试次数

        return baseConfig
    }

    /**
     * @description: 获取低速网络配置（适用于网络条件较差的环境）
     * @return 低速网络环境的配置Map
     */
    fun getLowSpeedNetworkConfig(): Map<String, Any> {
        val baseConfig = getConfig().toMutableMap()

        // 低速网络优化配置
        baseConfig["concurrent-threads"] = 2 // 减少并发线程数
        baseConfig["concurrent-block-size"] = 1048576 // 1MB块大小
        baseConfig["http-connect-timeout"] = 8000 // 更长的连接超时
        baseConfig["http-response-timeout"] = 15000 // 更长的响应超时
        baseConfig["retry-times"] = 8 // 增加重试次数

        return baseConfig
    }

    /**
     * @description: 手动刷新服务器配置
     * 当用户动态修改kokugai文件后，可以调用此方法重新生成配置
     * @return 刷新后的配置Map
     */
    fun refreshConfig(): Map<String, Any> {
        Log.info("手动刷新服务器配置")
        return getConfig()
    }
}