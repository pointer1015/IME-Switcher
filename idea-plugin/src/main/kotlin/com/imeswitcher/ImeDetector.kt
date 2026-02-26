package com.imeswitcher

/**
 * ImeDetector.kt
 * 字符分类与光标上下文语言检测逻辑——与 vscode-extension/src/detector.ts 保持逻辑一致。
 */

/** 光标语言上下文 */
enum class LangContext { ZH, EN, MIXED, UNKNOWN }

object ImeDetector {

    // ─── Unicode 区间判断 ────────────────────────────────────

    /** 是否为 CJK 汉字及相关表意文字 */
    fun isCJK(cp: Int): Boolean = cp in 0x4E00..0x9FFF ||   // CJK 统一表意字符
            cp in 0x3400..0x4DBF ||                          // CJK 扩展 A
            cp in 0x20000..0x2A6DF ||                        // CJK 扩展 B
            cp in 0x2A700..0x2CEAF ||                        // CJK 扩展 C/D/E
            cp in 0xF900..0xFAFF                             // CJK 兼容表意字符

    /** 是否为常见中文全角标点 */
    fun isChinesePunctuation(cp: Int): Boolean =
        cp in 0x3000..0x303F ||   // CJK 标点符号
                cp in 0xFF00..0xFFEF || // 半角/全角形式
                cp == 0x201C || cp == 0x201D || // ""
                cp == 0x2018 || cp == 0x2019 || // ''
                cp == 0x2026 || cp == 0x2014    // … —

    /** 是否为 ASCII 可见字符（字母、数字、半角标点） */
    fun isAsciiVisible(cp: Int): Boolean = cp in 0x21..0x7E  // 排除空格(0x20)

    fun isChineseChar(cp: Int) = isCJK(cp) || isChinesePunctuation(cp)
    fun isEnglishChar(cp: Int) = isAsciiVisible(cp)

    // ─── 上下文检测 ──────────────────────────────────────────

    /**
     * 检测光标附近的语言上下文。
     *
     * @param lineText  光标所在行文本
     * @param charOffset 光标在行内字符偏移（列索引）
     * @param lookAround 前后各检测的字符数，默认 1
     */
    fun detectContext(
        lineText: String,
        charOffset: Int,
        lookAround: Int = 1
    ): LangContext {
        val chars = mutableListOf<Int>()
        for (i in (charOffset - lookAround) until (charOffset + lookAround)) {
            if (i in lineText.indices) {
                chars += lineText[i].code
            }
        }
        if (chars.isEmpty()) return LangContext.UNKNOWN

        var zhCount = 0
        var enCount = 0
        for (cp in chars) {
            when {
                isChineseChar(cp) -> zhCount++
                isEnglishChar(cp) -> enCount++
            }
        }

        return when {
            zhCount > 0 && enCount == 0 -> LangContext.ZH
            enCount > 0 && zhCount == 0 -> LangContext.EN
            zhCount > 0 && enCount > 0  -> LangContext.MIXED
            else                         -> LangContext.UNKNOWN
        }
    }
}
