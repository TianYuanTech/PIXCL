package mcpatch.config

/**
 * @description: 硬编码配置类，包含项目的所有配置信息
 */
object HardcodedConfig {

    /**
     * @description: 获取完整的项目配置信息
     * @return 包含所有配置项的Map对象
     */
    fun getConfig(): Map<String, Any> {
        return mapOf(
            // 服务器配置 - 多个备用更新源
            "server" to listOf(
                "http://api.pixellive.cn:6701",
                "http://api.pixellive.cn:6702",
                "http://api.pixellive.cn:6703",
                "http://api.pixellive.cn:6704",
                "http://api.pixellive.cn:6705",
                "http://api.pixellive.cn:6706",
                "http://api.pixellive.cn:6707",
                "http://api.pixellive.cn:6708",
                "http://api.pixellive.cn:6709",
                "http://api.pixellive.cn:6710"
            ),

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
}