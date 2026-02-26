package com.imeswitcher

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * ImeService.kt
 *
 * Application 级别主控服务，协调：
 *  - 防抖调度（对应 extension.ts 中的 debounce）
 *  - 通过 ImeController 执行切换
 *  - 维护最后切换状态与手动暂停标志
 *  - 发出通知（切换失败时提示）
 */
@Service(Service.Level.APP)
class ImeService : Disposable {

    private val log = logger<ImeService>()

    // 单线程调度器——防抖与 exe 调用都在此线程，不占用 EDT
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ime-switcher-worker").also { it.isDaemon = true }
    }

    private var debounceFuture: ScheduledFuture<*>? = null
    private var pauseFuture: ScheduledFuture<*>? = null

    private val isManuallyPaused = AtomicBoolean(false)

    /** 上一次成功切换到的语言，避免重复调用 exe */
    val lastLang = AtomicReference<LangContext?>(null)

    // 状态栏刷新回调（由 ImeStatusBarWidget 注入）
    var onStateChanged: ((LangContext?) -> Unit)? = null

    // ─── 控制器（尽早初始化，依赖插件目录） ──────────────────

    private val controllerRef = AtomicReference<ImeController?>(null)

    init {
        // 尝试在服务初始化阶段立即解析插件路径，避免 Startup Activity
        // 尚未执行时因 controller 为 null 而误报警告
        val path = PluginManagerCore
            .getPlugin(PluginId.getId("com.imeswitcher.idea"))
            ?.pluginPath
            ?.toAbsolutePath()
            ?.toString()
        if (path != null) {
            controllerRef.set(ImeController(path))
        }
    }

    /**
     * 由 ImeStartupActivity 在项目打开后调用，若 init 阶段已初始化则只做 helper 可用性校验。
     */
    fun setPluginBasePath(path: String) {
        // 若 init 阶段已成功设置则不重复创建；否则（极罕见情况）补设
        if (controllerRef.get() == null) {
            controllerRef.set(ImeController(path))
        }
        // 校验 helper 是否可用，不可用时发出一次性警告
        val settings = ImeSettings.getInstance()
        val ctrl = controllerRef.get() ?: return
        if (!ctrl.canResolveHelper(settings.executablePath)) {
            showHelperNotFoundError()
        }
    }

    private fun controller(): ImeController? = controllerRef.get()

    // ─── 防抖触发（由光标监听器调用） ─────────────────────

    /**
     * 光标变化时调用；在防抖延迟后执行检测与切换。
     *
     * @param editor     当前编辑器
     * @param languageId 文档语言 ID
     */
    fun scheduleDetect(editor: Editor, languageId: String) {
        val settings = ImeSettings.getInstance()
        if (!settings.enabled) return
        if (isManuallyPaused.get()) return
        if (!settings.isLanguageAllowed(languageId)) return

        debounceFuture?.cancel(false)
        debounceFuture = scheduler.schedule({
            runDetectAndSwitch(editor)
        }, settings.delayMs, TimeUnit.MILLISECONDS)
    }

    private fun runDetectAndSwitch(editor: Editor) {
        // 确保在 EDT 上读取编辑器模型
        val (lineText, charOffset) = ApplicationManager.getApplication().runReadAction<Pair<String?, Int>> {
            try {
                val caret = editor.caretModel.primaryCaret
                if (!caret.hasSelection()) {
                    val doc  = editor.document
                    val line = doc.getLineNumber(caret.offset)
                    val lineStart = doc.getLineStartOffset(line)
                    val lineEnd   = doc.getLineEndOffset(line)
                    val text = doc.getText(
                        com.intellij.openapi.util.TextRange(lineStart, lineEnd)
                    )
                    text to (caret.offset - lineStart)
                } else {
                    null to 0   // 有选区时不切换
                }
            } catch (e: Exception) {
                null to 0
            }
        }

        if (lineText == null) return

        val ctx = ImeDetector.detectContext(lineText, charOffset)
        log.debug("检测结果: $ctx  offset=$charOffset text='$lineText'")

        if (ctx == LangContext.ZH || ctx == LangContext.EN) {
            performSwitch(ctx)
        }
    }

    // ─── 手动切换 ────────────────────────────────────────────

    fun manualSwitch(lang: LangContext, @Suppress("UNUSED_PARAMETER") project: Project?) {
        val settings = ImeSettings.getInstance()

        // 手动切换后暂停自动切换
        pauseFuture?.cancel(false)
        isManuallyPaused.set(true)
        pauseFuture = scheduler.schedule({
            isManuallyPaused.set(false)
        }, settings.pauseAfterManualSwitchMs, TimeUnit.MILLISECONDS)

        lastLang.set(null) // 强制刷新
        performSwitch(lang)
    }

    fun toggleEnabled() {
        val settings = ImeSettings.getInstance()
        settings.enabled = !settings.enabled
        onStateChanged?.invoke(lastLang.get())
    }

    // ─── 内部执行切换 ────────────────────────────────────────

    private fun performSwitch(lang: LangContext) {
        if (lang == lastLang.get()) return   // 与上次一致，无需重复切换

        val ctrl = controller()
        if (ctrl == null) {
            // controller 尚未初始化（Startup Activity 还未执行），静默跳过
            log.debug("IME Switcher: controller 未就绪，跳过本次切换")
            return
        }

        val target = when (lang) {
            LangContext.ZH -> ImeController.TargetLang.ZH
            else           -> ImeController.TargetLang.EN
        }
        val settings = ImeSettings.getInstance()
        val ok = ctrl.switchIme(target, settings.executablePath, settings.toggleKey)
        if (ok) {
            lastLang.set(lang)
            onStateChanged?.invoke(lang)
            if (settings.logEnabled) log.info("IME 已切换到: $lang")
        } else {
            log.warn("IME 切换失败 target=$lang")
        }
    }

    // ─── 通知 ────────────────────────────────────────────────

    private var helperErrorShown = false

    private fun showHelperNotFoundError() {
        if (helperErrorShown) return
        helperErrorShown = true
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("IME Switcher")
                .createNotification(
                    "IME Switcher：未找到 ime-switcher.exe",
                    "请在 <b>Settings → Tools → IME Switcher</b> 中指定可执行文件路径，" +
                            "或将 ime-switcher.exe 放入插件 bin/ 目录。",
                    NotificationType.WARNING
                )
                .notify(null)
        }
    }

    // ─── Disposable ──────────────────────────────────────────

    override fun dispose() {
        debounceFuture?.cancel(false)
        pauseFuture?.cancel(false)
        scheduler.shutdownNow()
    }

    companion object {
        @JvmStatic
        fun getInstance(): ImeService =
            ApplicationManager.getApplication().getService(ImeService::class.java)
    }
}
