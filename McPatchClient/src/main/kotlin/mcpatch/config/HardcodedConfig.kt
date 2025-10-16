package mcpatch.config

import mcpatch.logging.Log

/**
 * @description: 硬编码配置类，提供纯配置参数管理
 * 注意：此类不再包含任何服务器地址信息，服务器地址由外部传入
 */
object HardcodedConfig {

    /**
     * @description: 获取基础配置参数（不包含服务器地址）
     * @return 包含所有配置项的Map对象
     */
    private fun getBaseConfig(): Map<String, Any> {
        return mapOf(
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
     * @description: 获取标准配置（添加外部传入的服务器地址）
     * @param serverUrl 外部传入的服务器URL地址
     * @return 包含服务器地址的完整配置Map
     */
    fun getConfig(serverUrl: String): Map<String, Any> {
        val config = getBaseConfig().toMutableMap()
        config["server"] = listOf(serverUrl)
        Log.debug("生成标准配置，服务器地址: $serverUrl")
        return config
    }

    /**
     * @description: 获取生产环境配置（优化生产环境使用体验）
     * @param serverUrl 外部传入的服务器URL地址
     * @return 适用于生产环境的配置Map
     */
    fun getProductionConfig(serverUrl: String): Map<String, Any> {
        val config = getBaseConfig().toMutableMap()

        // 添加服务器地址
        config["server"] = listOf(serverUrl)

        // 生产环境优化配置
        config["quiet-mode"] = true
        config["show-finish-message"] = false
        config["changelogs-auto-close"] = 5
        config["no-throwing"] = false

        Log.debug("生成生产环境配置，服务器地址: $serverUrl")
        return config
    }

    /**
     * @description: 获取开发环境配置（便于调试和测试）
     * @param serverUrl 外部传入的服务器URL地址
     * @return 适用于开发环境的配置Map
     */
    fun getDevConfig(serverUrl: String): Map<String, Any> {
        val config = getBaseConfig().toMutableMap()

        // 添加服务器地址
        config["server"] = listOf(serverUrl)

        // 开发环境调试配置
        config["quiet-mode"] = false
        config["show-finish-message"] = true
        config["changelogs-auto-close"] = 0
        config["no-throwing"] = true

        // 开发环境网络配置优化
        config["http-connect-timeout"] = 10000
        config["http-response-timeout"] = 15000
        config["retry-times"] = 3

        Log.debug("生成开发环境配置，服务器地址: $serverUrl")
        return config
    }

    /**
     * @description: 获取高性能配置（适用于高速网络环境）
     * @param serverUrl 外部传入的服务器URL地址
     * @return 高性能网络环境的配置Map
     */
    fun getHighPerformanceConfig(serverUrl: String): Map<String, Any> {
        val config = getBaseConfig().toMutableMap()

        config["server"] = listOf(serverUrl)
        config["concurrent-threads"] = 8
        config["concurrent-block-size"] = 8388608 // 8MB
        config["http-connect-timeout"] = 1500
        config["http-response-timeout"] = 3000
        config["retry-times"] = 3

        Log.debug("生成高性能配置，服务器地址: $serverUrl")
        return config
    }

    /**
     * @description: 获取低速网络配置（适用于网络条件较差的环境）
     * @param serverUrl 外部传入的服务器URL地址
     * @return 低速网络环境的配置Map
     */
    fun getLowSpeedNetworkConfig(serverUrl: String): Map<String, Any> {
        val config = getBaseConfig().toMutableMap()

        config["server"] = listOf(serverUrl)
        config["concurrent-threads"] = 2
        config["concurrent-block-size"] = 1048576 // 1MB
        config["http-connect-timeout"] = 8000
        config["http-response-timeout"] = 15000
        config["retry-times"] = 8

        Log.debug("生成低速网络配置，服务器地址: $serverUrl")
        return config
    }
}