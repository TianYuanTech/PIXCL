/**
 * @description: McPatch进度回调接口，用于将更新进度传递给HMCL的UI组件
 */
package mcpatch.callback

interface ProgressCallback {

    /**
     * @description: 更新标题文字
     * @param title 标题内容
     */
    fun updateTitle(title: String)

    /**
     * @description: 更新状态标签文字
     * @param label 状态描述
     */
    fun updateLabel(label: String)

    /**
     * @description: 更新进度条文字和进度值
     * @param text 进度条显示的文字
     * @param value 进度值（0-1000）
     */
    fun updateProgress(text: String, value: Int)

    /**
     * @description: 检查是否应该中断操作
     * @return true表示应该中断，false表示继续
     */
    fun shouldInterrupt(): Boolean

    /**
     * @description: 显示完成消息
     * @param hasUpdates 是否有更新内容
     */
    fun showCompletionMessage(hasUpdates: Boolean)
}