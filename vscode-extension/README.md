# IME Switcher

**Windows 专用 VS Code 扩展：根据光标邻近字符，在微软拼音 IME 内自动切换中/英文输入模式。**

光标移到中文字符附近 → 自动切换到中文模式；  
光标移到英文字符附近 → 自动切换到英文模式。

---

## 功能

| 功能 | 说明 |
|------|------|
| 自动检测 | 光标前后各 1 个字符（可配置），识别 CJK / ASCII |
| 防抖切换 | 默认 300ms 防抖，避免快速移动光标时频繁切换 |
| 手动切换 | 快捷键 `Ctrl+Alt+Z` / `Ctrl+Alt+E` 或命令面板手动切换 |
| 暂停机制 | 手动切换后暂停 3s 自动切换，避免冲突 |
| 状态栏指示 | 右下角实时显示当前 IME 模式 |
| 语言黑/白名单 | 可在设置中禁用特定文件类型（默认禁用 Markdown/纯文本） |

---

## 安装

### 方法 1：从 VSIX 安装（推荐）

1. 编译 native helper（见下文）。
2. 把编译好的 `ime-switcher.exe` 放到 `bin/` 目录。
3. 在扩展目录运行 `npm install && npm run compile`。
4. 打包：`npx vsce package`。
5. VS Code → 扩展 → `…` → 从 VSIX 安装。

### 方法 2：直接在工作区调试

1. 打开 `vscode-extension/` 目录。
2. 运行 `npm install && npm run compile`。
3. 按 `F5` 启动扩展开发宿主。

---

## 编译 native helper

> 需要 MSVC Build Tools（推荐）或 MinGW。

```bat
cd native-helper
build.bat
```

编译成功后，`bin/ime-switcher.exe` 会自动放入 `vscode-extension/bin/`。

---

## 配置项

在 VS Code 设置（`settings.json`）中调整：

```jsonc
{
  // 是否启用自动切换（默认 true）
  "imeSwitcher.enabled": true,

  // 防抖延迟毫秒（默认 300）
  "imeSwitcher.delayMs": 300,

  // 自定义 exe 路径（留空使用内置）
  "imeSwitcher.executable": "",

  // 仅在这些语言文件中启用（留空=全部）
  "imeSwitcher.whitelist": [],

  // 在这些语言文件中禁用
  "imeSwitcher.blacklist": ["markdown", "plaintext"],

  // 是否打印调试日志到输出面板
  "imeSwitcher.log": false,

  // 手动切换后暂停自动切换的毫秒数
  "imeSwitcher.pauseAfterManualSwitchMs": 3000
}
```

---

## 命令与快捷键

| 命令 | 快捷键 | 说明 |
|------|--------|------|
| `IME Switcher: 启用/禁用自动切换` | — | 点击状态栏或在命令面板运行 |
| `IME Switcher: 手动切换到中文` | `Ctrl+Alt+Z` | 立即切换到中文 |
| `IME Switcher: 手动切换到英文` | `Ctrl+Alt+E` | 立即切换到英文 |

---

## 运行测试

```bat
cd vscode-extension
npm install
npm run compile
npm test
```

---

## 安全说明

`ime-switcher.exe` 会通过 TSF（Text Services Framework）API 在微软拼音 IME 内部切换中/英文转换模式，
不切换键盘布局（HKL）。如果杀毒软件误报，请将 exe 添加到白名单，或自行从源码编译以获得信任。

---

## 平台支持

- **Windows 10 / 11**（仅支持 Windows）
- 必须使用微软拼音（Microsoft Pinyin）IME；通过 TSF 隔间 API 切换中/英文模式，不依赖 HKL 键盘布局切换

---

## 许可证

MIT
