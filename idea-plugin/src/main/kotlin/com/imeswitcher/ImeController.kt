package com.imeswitcher

import com.intellij.openapi.diagnostic.logger
import java.io.File

/**
 * ImeController.kt
 *
 * 负责与 ime-switcher.exe（或备用的 ime-switcher.ps1）通信，
 * 执行实际的 Windows 输入法切换。
 *
 * 调用方式复刻 VS Code 扩展的 extension.ts 中的 switchIME 逻辑：
 *   exe : ime-switcher.exe set zh/en [--key=shift|ctrl|auto]
 *   ps1 : powershell.exe -File ime-switcher.ps1 set zh/en -Key shift|ctrl|auto
 */
class ImeController(private val pluginBasePath: String) {

    private val log = logger<ImeController>()

    sealed class TargetLang { object ZH : TargetLang(); object EN : TargetLang() }

    // ─── Helper 解析 ────────────────────────────────────────

    private sealed class Helper {
        data class Exe(val path: String) : Helper()
        data class Ps1(val path: String) : Helper()
    }

    private fun resolveHelper(customPath: String): Helper? {
        if (customPath.isNotBlank()) {
            return Helper.Exe(customPath)
        }

        val binDir = File(pluginBasePath, "bin")

        val exe = File(binDir, "ime-switcher.exe")
        if (exe.exists()) return Helper.Exe(exe.absolutePath)

        val ps1 = File(binDir, "ime-switcher.ps1")
        if (ps1.exists()) return Helper.Ps1(ps1.absolutePath)

        return null
    }

    // ─── 公开接口 ────────────────────────────────────────────

    /**
     * 检查当前插件目录与自定义路径下是否能找到 helper。
     * 在 setPluginBasePath 完成后调用，用于判断是否需要显示配置警告。
     */
    fun canResolveHelper(customPath: String): Boolean = resolveHelper(customPath) != null

    /**
     * 异步切换输入法；在调用方提供的线程上执行，不阻塞 EDT。
     *
     * @param lang       目标语言
     * @param customPath 用户自定义 exe 路径（空字符串表示使用内置）
     * @param toggleKey  切换按键模式：shift / ctrl / auto
     * @return 切换是否成功
     */
    fun switchIme(
        lang: TargetLang,
        customPath: String = "",
        toggleKey: String = "auto"
    ): Boolean {
        val helper = resolveHelper(customPath)
        if (helper == null) {
            log.warn("IME Switcher: 未找到 ime-switcher.exe 或 ime-switcher.ps1")
            return false
        }

        val langArg = if (lang is TargetLang.ZH) "zh" else "en"

        val (binary, args) = when (helper) {
            is Helper.Exe -> helper.path to listOf("set", langArg, "--key=$toggleKey")
            is Helper.Ps1 -> "powershell.exe" to listOf(
                "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", helper.path,
                "set", langArg,
                "-Key", toggleKey
            )
        }

        log.debug("IME 调用: $binary ${args.joinToString(" ")}")

        return try {
            val process = ProcessBuilder(binary, *args.toTypedArray())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            log.debug("IME 输出: $output (exit=$exitCode)")
            exitCode == 0
        } catch (e: Exception) {
            log.warn("IME 切换失败: ${e.message}")
            false
        }
    }

    /**
     * 查询当前输入法状态（同步，在后台线程调用）。
     * @return "zh" | "en" | null（查询失败）
     */
    fun queryIme(customPath: String = ""): String? {
        val helper = resolveHelper(customPath) ?: return null

        val (binary, args) = when (helper) {
            is Helper.Exe -> helper.path to listOf("query")
            is Helper.Ps1 -> "powershell.exe" to listOf(
                "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-File", helper.path,
                "query"
            )
        }

        return try {
            val process = ProcessBuilder(binary, *args.toTypedArray())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim().lowercase()
            val exitCode = process.waitFor()
            if (exitCode == 0 && (output == "zh" || output == "en")) output else null
        } catch (e: Exception) {
            log.warn("IME 查询失败: ${e.message}")
            null
        }
    }
}
