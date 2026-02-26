package com.imeswitcher

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * ImeSettingsConfigurable.kt
 *
 * 插件设置 UI，注册于 File → Settings → Tools → IME Switcher。
 * 使用 Swing 表单（FormBuilder）构建，避免引入 Compose for Desktop 依赖。
 */
class ImeSettingsConfigurable : Configurable {

    // ── 控件 ────────────────────────────────────────────────
    private var panel: JPanel?          = null
    private var enabledBox              = JBCheckBox("启用自动切换")
    private var logBox                  = JBCheckBox("启用详细日志")
    private var delayField              = JBTextField(8)
    private var pauseField              = JBTextField(8)
    private var execField               = JBTextField(30)
    private var toggleKeyField          = JBTextField(8)
    private var blacklistField          = JBTextField(30)
    private var whitelistField          = JBTextField(30)

    override fun getDisplayName() = "IME Switcher"

    override fun getHelpTopic(): String? = null

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addComponent(enabledBox)
            .addLabeledComponent(JBLabel("防抖延迟 (ms)："), delayField)
            .addLabeledComponent(JBLabel("手动暂停时间 (ms)："), pauseField)
            .addSeparator()
            .addLabeledComponent(JBLabel("切换键 (shift / ctrl / auto)："), toggleKeyField)
            .addLabeledComponent(JBLabel("可执行文件路径 (空 = 内置)："), execField)
            .addSeparator()
            .addLabeledComponent(JBLabel("黑名单语言 ID (逗号分隔)："), blacklistField)
            .addLabeledComponent(JBLabel("白名单语言 ID (空 = 不限，逗号分隔)："), whitelistField)
            .addSeparator()
            .addComponent(logBox)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = ImeSettings.getInstance()
        return enabledBox.isSelected     != s.enabled             ||
               logBox.isSelected         != s.logEnabled          ||
               delayField.text           != s.delayMs.toString()  ||
               pauseField.text           != s.pauseAfterManualSwitchMs.toString() ||
               execField.text            != s.executablePath      ||
               toggleKeyField.text       != s.toggleKey           ||
               blacklistField.text       != s.state.blacklist     ||
               whitelistField.text       != s.state.whitelist
    }

    override fun apply() {
        val s = ImeSettings.getInstance()
        s.enabled                    = enabledBox.isSelected
        s.logEnabled                 = logBox.isSelected
        s.delayMs                    = delayField.text.toLongOrNull() ?: s.delayMs
        s.pauseAfterManualSwitchMs   = pauseField.text.toLongOrNull() ?: s.pauseAfterManualSwitchMs
        s.executablePath             = execField.text.trim()
        s.toggleKey                  = toggleKeyField.text.trim().ifEmpty { "auto" }
        s.state.blacklist            = blacklistField.text.trim()
        s.state.whitelist            = whitelistField.text.trim()
    }

    override fun reset() {
        val s = ImeSettings.getInstance()
        enabledBox.isSelected   = s.enabled
        logBox.isSelected       = s.logEnabled
        delayField.text         = s.delayMs.toString()
        pauseField.text         = s.pauseAfterManualSwitchMs.toString()
        execField.text          = s.executablePath
        toggleKeyField.text     = s.toggleKey
        blacklistField.text     = s.state.blacklist
        whitelistField.text     = s.state.whitelist
    }

    override fun disposeUIResources() {
        panel = null
    }
}
