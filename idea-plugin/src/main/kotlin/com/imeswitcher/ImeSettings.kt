package com.imeswitcher

import com.intellij.openapi.components.*

/**
 * ImeSettings.kt
 *
 * 使用 PersistentStateComponent 将插件配置持久化到 IDE 的 xml 存储中。
 * 对应 VS Code 扩展的 imeSwitcher.* 配置项。
 */
@State(
    name   = "ImeSettings",
    storages = [Storage("ime-switcher.xml")]
)
@Service(Service.Level.APP)
class ImeSettings : PersistentStateComponent<ImeSettings.State> {

    data class State(
        /** 是否启用自动切换 */
        var enabled: Boolean = true,

        /** 防抖延迟（毫秒），对应 imeSwitcher.delayMs */
        var delayMs: Long = 300L,

        /**
         * 黑名单语言 ID（逗号分隔）。
         * 对应 imeSwitcher.blacklist，默认禁止纯文本与 Markdown。
         */
        var blacklist: String = "TEXT,Markdown",

        /**
         * 白名单语言 ID（逗号分隔，空表示不限制）。
         * 对应 imeSwitcher.whitelist。
         */
        var whitelist: String = "",

        /** 自定义 ime-switcher.exe 路径，空表示使用插件内置 */
        var executablePath: String = "",

        /** 切换按键模式：shift / ctrl / auto */
        var toggleKey: String = "auto",

        /**
         * 手动切换后暂停自动切换的时间（毫秒），
         * 对应 imeSwitcher.pauseAfterManualSwitchMs。
         */
        var pauseAfterManualSwitchMs: Long = 3000L,

        /** 是否启用详细日志 */
        var logEnabled: Boolean = false
    )

    private var _state = State()

    override fun getState(): State = _state

    override fun loadState(state: State) {
        _state = state
    }

    // ─── 便捷访问器 ──────────────────────────────────────────

    var enabled: Boolean
        get() = _state.enabled
        set(v) { _state.enabled = v }

    var delayMs: Long
        get() = _state.delayMs
        set(v) { _state.delayMs = v }

    val blacklistSet: Set<String>
        get() = _state.blacklist
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    val whitelistSet: Set<String>
        get() = _state.whitelist
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()

    var executablePath: String
        get() = _state.executablePath
        set(v) { _state.executablePath = v }

    var toggleKey: String
        get() = _state.toggleKey
        set(v) { _state.toggleKey = v }

    var pauseAfterManualSwitchMs: Long
        get() = _state.pauseAfterManualSwitchMs
        set(v) { _state.pauseAfterManualSwitchMs = v }

    var logEnabled: Boolean
        get() = _state.logEnabled
        set(v) { _state.logEnabled = v }

    /** 判断当前语言 ID 是否允许自动切换 */
    fun isLanguageAllowed(languageId: String): Boolean {
        val white = whitelistSet
        val black = blacklistSet
        if (black.contains(languageId)) return false
        if (white.isNotEmpty() && !white.contains(languageId)) return false
        return true
    }

    companion object {
        @JvmStatic
        fun getInstance(): ImeSettings = service()
    }
}
