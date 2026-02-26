package com.imeswitcher.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.imeswitcher.ImeService
import com.imeswitcher.LangContext

/** 手动切换到中文输入模式（Ctrl+Alt+Z）*/
class SwitchToZhAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ImeService.getInstance().manualSwitch(LangContext.ZH, e.project)
    }
}

/** 手动切换到英文输入模式（Ctrl+Alt+E）*/
class SwitchToEnAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ImeService.getInstance().manualSwitch(LangContext.EN, e.project)
    }
}

/** 启用 / 禁用 IME 自动切换（Ctrl+Alt+I）*/
class ToggleEnabledAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        ImeService.getInstance().toggleEnabled()
    }

    override fun update(e: AnActionEvent) {
        // 在菜单中显示当前状态
        val enabled = com.imeswitcher.ImeSettings.getInstance().enabled
        e.presentation.text = if (enabled) "禁用 IME 自动切换" else "启用 IME 自动切换"
    }
}
