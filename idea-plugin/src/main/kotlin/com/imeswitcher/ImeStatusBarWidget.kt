package com.imeswitcher

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

/**
 * ImeStatusBarWidget.kt
 *
 * 在 IDE 状态栏右侧显示当前 IME 状态（中文 / EN / IME）。
 * 点击可切换"启用/禁用自动切换"，与 VS Code 扩展状态栏行为一致。
 */
class ImeStatusBarWidget(project: Project) :
    EditorBasedWidget(project), StatusBarWidget.TextPresentation {

    companion object {
        const val WIDGET_ID = "com.imeswitcher.statusBar"
    }

    private var displayText = "IME"

    // ─── StatusBarWidget ─────────────────────────────────────

    override fun ID(): String = WIDGET_ID

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        super.install(statusBar)
        // 注入状态变更回调到服务
        ImeService.getInstance().onStateChanged = { lang ->
            updateDisplay(lang)
        }
        updateDisplay(ImeService.getInstance().lastLang.get())
    }

    override fun dispose() {
        ImeService.getInstance().onStateChanged = null
        super.dispose()
    }

    // ─── TextPresentation ────────────────────────────────────

    override fun getText(): String = displayText

    override fun getTooltipText(): String {
        val settings = ImeSettings.getInstance()
        return if (settings.enabled)
            "IME Switcher: 自动切换已启用（点击禁用）"
        else
            "IME Switcher: 自动切换已禁用（点击启用）"
    }

    override fun getAlignment() = 0f

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        ImeService.getInstance().toggleEnabled()
    }

    // ─── 刷新显示文本 ────────────────────────────────────────

    private fun updateDisplay(lang: LangContext?) {
        val settings = ImeSettings.getInstance()
        displayText = when {
            !settings.enabled -> "○ IME"
            lang == LangContext.ZH -> "中"
            lang == LangContext.EN -> "EN"
            else -> "IME"
        }
        myStatusBar?.updateWidget(WIDGET_ID)
    }
}

// ─── WidgetFactory ───────────────────────────────────────────

class ImeStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId() = ImeStatusBarWidget.WIDGET_ID

    override fun getDisplayName() = "IME Switcher"

    override fun isAvailable(project: Project) = true

    override fun createWidget(project: Project): StatusBarWidget =
        ImeStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) = widget.dispose()

    override fun canBeEnabledOn(statusBar: StatusBar) = true
}
