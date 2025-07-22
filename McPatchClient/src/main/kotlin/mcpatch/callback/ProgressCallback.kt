package mcpatch.callback

/**
 * @description: 进度回调接口，用于向HMCL等外部系统反馈进度信息
 */
interface ProgressCallback {

    /**
     * @description: 更新窗口标题
     * @param title 新的标题文本
     */
    fun updateTitle(title: String)

    /**
     * @description: 更新状态标签文本
     * @param label 新的标签文本
     */
    fun updateLabel(label: String)

    /**
     * @description: 更新进度条和进度文本
     * @param progressText 进度文本显示
     * @param progressValue 进度值（0-1000）
     */
    fun updateProgress(progressText: String, progressValue: Int)

    /**
     * @description: 检查是否应该中断当前操作
     * @return Boolean 如果应该中断返回true
     */
    fun shouldInterrupt(): Boolean

    /**
     * @description: 显示完成消息
     * @param hasUpdates 是否有文件更新
     */
    fun showCompletionMessage(hasUpdates: Boolean)

    /**
     * @description: 显示更新记录
     * @param title 更新记录标题
     * @param content 更新记录内容
     * @param autoCloseSeconds 自动关闭秒数，0表示不自动关闭
     */
    fun showChangeLogs(title: String, content: String, autoCloseSeconds: Int) {
        // 默认实现：简单的日志输出
        println("=== $title ===")
        println(content)
    }
}