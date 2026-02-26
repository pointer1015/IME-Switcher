/**
 * extension.ts
 * VS Code IME Switcher 扩展入口。
 *
 * 功能：
 *  - 监听光标/选区变化，debounce 后调用 detector 检测语言上下文
 *  - 通过 ime-switcher.exe 切换 Windows 输入法
 *  - 状态栏显示当前 IME 模式
 *  - 支持手动切换命令与快捷键
 *  - 用户手动切换后短暂暂停自动切换
 */

import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import * as child_process from 'child_process';
import { detectContext, LangContext } from './detector';

// ─── 全局状态 ────────────────────────────────────────────────

let statusBarItem: vscode.StatusBarItem;
let debounceTimer: ReturnType<typeof setTimeout> | undefined;
let manualPauseTimer: ReturnType<typeof setTimeout> | undefined;
let isManuallyPaused = false;
let outputChannel: vscode.OutputChannel;

/** 上一次调用 exe 的目标语言（避免重复调用） */
let lastSwitchedLang: 'zh' | 'en' | undefined;

// ─── 激活 ────────────────────────────────────────────────────

export function activate(context: vscode.ExtensionContext) {
  outputChannel = vscode.window.createOutputChannel('IME Switcher');

  // 状态栏
  statusBarItem = vscode.window.createStatusBarItem(
    vscode.StatusBarAlignment.Right,
    100
  );
  statusBarItem.command = 'imeSwitcher.toggleEnabled';
  statusBarItem.tooltip = '点击启用/禁用 IME Switcher';
  context.subscriptions.push(statusBarItem);
  updateStatusBar();

  // 注册命令
  context.subscriptions.push(
    vscode.commands.registerCommand('imeSwitcher.toggleEnabled', async () => {
      const cfg = vscode.workspace.getConfiguration('imeSwitcher');
      const current = cfg.get<boolean>('enabled', true);
      await cfg.update('enabled', !current, vscode.ConfigurationTarget.Global);
      updateStatusBar();
    }),

    vscode.commands.registerCommand('imeSwitcher.switchToZh', () => {
      triggerManualSwitch('zh', context);
    }),

    vscode.commands.registerCommand('imeSwitcher.switchToEn', () => {
      triggerManualSwitch('en', context);
    })
  );

  // 监听选区变化（光标移动）
  context.subscriptions.push(
    vscode.window.onDidChangeTextEditorSelection(e => {
      handleSelectionChange(e.textEditor, context);
    })
  );

  // 监听活跃编辑器切换
  context.subscriptions.push(
    vscode.window.onDidChangeActiveTextEditor(editor => {
      if (editor) handleSelectionChange(editor, context);
    })
  );

  // 监听配置变更
  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration(e => {
      if (e.affectsConfiguration('imeSwitcher')) {
        updateStatusBar();
      }
    })
  );

  log('IME Switcher 已激活');
}

export function deactivate() {
  if (debounceTimer) clearTimeout(debounceTimer);
  if (manualPauseTimer) clearTimeout(manualPauseTimer);
}

// ─── 选区变化处理 ─────────────────────────────────────────────

function handleSelectionChange(
  editor: vscode.TextEditor,
  context: vscode.ExtensionContext
) {
  const cfg = vscode.workspace.getConfiguration('imeSwitcher');
  if (!cfg.get<boolean>('enabled', true)) return;
  if (isManuallyPaused) return;
  if (!isLanguageAllowed(editor.document.languageId)) return;

  const delayMs = cfg.get<number>('delayMs', 300);

  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    runDetectionAndSwitch(editor, context);
  }, delayMs);
}

function runDetectionAndSwitch(
  editor: vscode.TextEditor,
  context: vscode.ExtensionContext
) {
  const selection = editor.selection;

  // 若有非空选区 → 混合情况，不自动切换
  if (!selection.isEmpty) return;

  const line = editor.document.lineAt(selection.active.line);
  const charIndex = selection.active.character;

  const ctx: LangContext = detectContext(line.text, charIndex);

  log(`检测结果: ${ctx}  (行${selection.active.line + 1}, 列${charIndex})`);

  if (ctx === 'zh' || ctx === 'en') {
    switchIME(ctx, context);
  }
  // 'mixed' / 'unknown' 不自动切换
}

// ─── IME 切换 ─────────────────────────────────────────────────

interface HelperInfo {
  type: 'exe' | 'ps1';
  path: string;
}

function switchIME(
  lang: 'zh' | 'en',
  context: vscode.ExtensionContext
) {
  if (lang === lastSwitchedLang) return; // 无需重复切换

  const helper = resolveHelper(context);
  if (!helper) {
    vscode.window.showErrorMessage(
      'IME Switcher: 未找到 ime-switcher.exe 或 ime-switcher.ps1，请检查设置 imeSwitcher.executable'
    );
    return;
  }

  // 始终使用 set 子命令在微软拼音 IME 内部切换中/英文模式，
  // 不切换键盘布局（HKL）
  const cfg = vscode.workspace.getConfiguration('imeSwitcher');
  const toggleKey = cfg.get<string>('toggleKey', 'auto');
  // exe 用 --key=xxx 格式；ps1 用 PowerShell 命名参数 -Key xxx
  const exeArgs_base: string[] = ['set', lang, `--key=${toggleKey}`];
  const ps1Args_base: string[] = ['set', lang];

  let exeFile: string;
  let exeArgs: string[];

  if (helper.type === 'exe') {
    exeFile = helper.path;
    exeArgs = exeArgs_base;
  } else {
    // PowerShell fallback
    exeFile = 'powershell.exe';
    exeArgs = [
      '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass',
      '-File', helper.path,
      ...ps1Args_base,
      '-Key', toggleKey,
    ];
  }

  log(`调用: ${exeFile} ${exeArgs.join(' ')}`);

  child_process.execFile(exeFile, exeArgs, (err, stdout, stderr) => {
    if (err) {
      log(`切换失败: ${err.message}\n${stderr}`);
      return;
    }
    log(`切换输出: ${stdout.trim()}`);
    lastSwitchedLang = lang;
    updateStatusBar(lang);
  });
}

function triggerManualSwitch(
  lang: 'zh' | 'en',
  context: vscode.ExtensionContext
) {
  const cfg = vscode.workspace.getConfiguration('imeSwitcher');
  const pauseMs = cfg.get<number>('pauseAfterManualSwitchMs', 3000);

  // 手动切换后暂停自动切换
  if (manualPauseTimer) clearTimeout(manualPauseTimer);
  isManuallyPaused = true;
  manualPauseTimer = setTimeout(() => {
    isManuallyPaused = false;
  }, pauseMs);

  lastSwitchedLang = undefined; // 强制刷新
  switchIME(lang, context);
}

// ─── 辅助 ─────────────────────────────────────────────────────

function resolveHelper(context: vscode.ExtensionContext): HelperInfo | undefined {
  const cfg = vscode.workspace.getConfiguration('imeSwitcher');
  const customPath = cfg.get<string>('executable', '').trim();
  if (customPath) {
    return { type: 'exe', path: customPath };
  }

  // 优先用内置 exe
  const bundledExe = path.join(context.extensionPath, 'bin', 'ime-switcher.exe');
  if (fs.existsSync(bundledExe)) {
    return { type: 'exe', path: bundledExe };
  }

  // 回退到 PowerShell 脚本
  const bundledPs1 = path.join(context.extensionPath, 'bin', 'ime-switcher.ps1');
  if (fs.existsSync(bundledPs1)) {
    return { type: 'ps1', path: bundledPs1 };
  }

  return undefined;
}

function isLanguageAllowed(languageId: string): boolean {
  const cfg = vscode.workspace.getConfiguration('imeSwitcher');
  const whitelist = cfg.get<string[]>('whitelist', []);
  const blacklist = cfg.get<string[]>('blacklist', ['markdown', 'plaintext']);

  if (blacklist.includes(languageId)) return false;
  if (whitelist.length > 0 && !whitelist.includes(languageId)) return false;
  return true;
}

function updateStatusBar(lang?: 'zh' | 'en') {
  const cfg = vscode.workspace.getConfiguration('imeSwitcher');
  const enabled = cfg.get<boolean>('enabled', true);

  if (!enabled) {
    statusBarItem.text = '$(circle-slash) IME';
    statusBarItem.backgroundColor = undefined;
    statusBarItem.show();
    return;
  }

  const current = lang ?? lastSwitchedLang;
  if (current === 'zh') {
    statusBarItem.text = '$(symbol-keyword) 中文';
  } else if (current === 'en') {
    statusBarItem.text = '$(symbol-string) EN';
  } else {
    statusBarItem.text = '$(keyboard) IME';
  }
  statusBarItem.backgroundColor = undefined;
  statusBarItem.show();
}

function log(msg: string) {
  const cfg = vscode.workspace.getConfiguration('imeSwitcher');
  if (cfg.get<boolean>('log', false)) {
    outputChannel.appendLine(`[${new Date().toISOString()}] ${msg}`);
  }
}
