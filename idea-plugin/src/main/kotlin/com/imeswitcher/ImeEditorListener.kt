package com.imeswitcher

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.diagnostic.logger

/**
 * ImeEditorListener.kt
 *
 * 监听两类事件触发输入法切换：
 *  1. FileEditorManagerListener：文件/标签页切换时
 *  2. CaretListener：光标位置变化时（通过 addEditorListeners 临时注入）
 *
 * 与 VS Code extension.ts 中的 onDidChangeTextEditorSelection
 * 和 onDidChangeActiveTextEditor 对应。
 */
class ImeEditorListener : FileEditorManagerListener {

    private val log = logger<ImeEditorListener>()

    // 当前正在监听的编辑器 → 其 CaretListener
    private val caretListeners = mutableMapOf<Editor, CaretListener>()

    // ─── FileEditorManagerListener ───────────────────────────

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val editor = (source.getSelectedEditor(file) as? TextEditor)?.editor ?: return
        attachCaretListener(editor, file.fileType.name)
        triggerDetect(editor, file.fileType.name)
    }

    override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
        val newEditor = (event.newEditor as? TextEditor)?.editor ?: return
        val file = event.newFile ?: return
        attachCaretListener(newEditor, file.fileType.name)
        triggerDetect(newEditor, file.fileType.name)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        // 清理已释放编辑器上的监听器，避免内存泄漏
        caretListeners.entries.removeIf { (ed, listener) ->
            ed.isDisposed.also { disposed ->
                if (disposed) ed.caretModel.removeCaretListener(listener)
            }
        }
    }

    // ─── CaretListener 注册 ──────────────────────────────────

    private fun attachCaretListener(editor: Editor, languageId: String) {
        if (caretListeners.containsKey(editor)) return   // 已注册

        val listener = object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                triggerDetect(editor, languageId)
            }
        }

        editor.caretModel.addCaretListener(listener)
        caretListeners[editor] = listener
        log.debug("CaretListener 已附加到编辑器，languageId=$languageId")
    }

    // ─── 触发检测 ─────────────────────────────────────────────

    private fun triggerDetect(editor: Editor, languageId: String) {
        if (editor.isDisposed) return
        ImeService.getInstance().scheduleDetect(editor, languageId)
    }
}
