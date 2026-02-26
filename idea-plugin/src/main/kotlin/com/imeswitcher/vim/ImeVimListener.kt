package com.imeswitcher.vim

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFile
import com.imeswitcher.ImeService
import com.imeswitcher.LangContext

/**
 * ImeVimListener.kt
 *
 * IdeaVim 集成（通过 META-INF/ime-switcher-vim.xml 中的 optional depends 加载）。
 *
 * 实现方案：在每次光标变化时通过反射检查当前编辑器的 Vim 模式，若处于
 * Normal / Visual / Command 等非插入模式，强制切换到英文；
 * 进入 Insert / Replace 模式后由主 ImeEditorListener 接管上下文检测。
 *
 * 使用反射而非直接导入 IdeaVim 类，可降低对特定 IdeaVim 版本的硬依赖风险。
 */
class ImeVimListener : FileEditorManagerListener {

    private val log = logger<ImeVimListener>()

    // 缓存反射查找结果，避免每次调用都反射
    private var commandStateClass: Class<*>?                = null
    private var getModeMethod: java.lang.reflect.Method?    = null
    private var instanceMethod: java.lang.reflect.Method?   = null
    private var insertModeName: String?                     = null
    private var replaceModeName: String?                    = null

    init { initReflection() }

    private fun initReflection() {
        try {
            commandStateClass = Class.forName("com.maddyhome.idea.vim.command.CommandState")
            instanceMethod    = commandStateClass!!.getMethod("getInstance", Editor::class.java)
            getModeMethod     = commandStateClass!!.getMethod("getMode")
            val modeClass     = getModeMethod!!.returnType
            insertModeName    = modeClass.enumConstants
                ?.firstOrNull { it.toString().startsWith("INSERT") }?.toString()
            replaceModeName   = modeClass.enumConstants
                ?.firstOrNull { it.toString().startsWith("REPLACE") }?.toString()
            log.info("IdeaVim 反射初始化成功，INSERT=$insertModeName REPLACE=$replaceModeName")
        } catch (e: Exception) {
            log.warn("IdeaVim CommandState 类未找到，Vim 模式监听降级为无操作：${e.message}")
        }
    }

    // ─── FileEditorManagerListener ───────────────────────────

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        (source.getSelectedEditor(file) as? TextEditor)?.editor?.let { attachVimCaretListener(it) }
    }

    override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
        (event.newEditor as? TextEditor)?.editor?.let { attachVimCaretListener(it) }
    }

    // ─── CaretListener ──────────────────────────────────────

    private val attachedEditors = mutableSetOf<Editor>()

    private fun attachVimCaretListener(editor: Editor) {
        if (attachedEditors.contains(editor)) return
        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(e: CaretEvent) {
                checkAndSwitchByVimMode(editor)
            }
        })
        attachedEditors += editor
        log.debug("Vim CaretListener 已附加到编辑器")
    }

    private fun checkAndSwitchByVimMode(editor: Editor) {
        val instMth = instanceMethod ?: return
        val modeMth = getModeMethod  ?: return

        try {
            val state    = instMth.invoke(null, editor) ?: return
            val mode     = modeMth.invoke(state)?.toString() ?: return
            val isInsert = mode.startsWith(insertModeName  ?: "INSERT") ||
                           mode.startsWith(replaceModeName ?: "REPLACE")
            if (!isInsert) {
                // Normal / Visual / Command 模式 → 强制英文
                val service = ImeService.getInstance()
                if (service.lastLang.get() != LangContext.EN) {
                    service.manualSwitch(LangContext.EN, null)
                }
            }
            // Insert / Replace 模式由普通 ImeEditorListener 的上下文检测接管
        } catch (_: Exception) { /* 静默忽略反射失败 */ }
    }
}
