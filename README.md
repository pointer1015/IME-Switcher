# IME Switcher

[![CI](https://github.com/pointer1015/ime-switcher/actions/workflows/ci.yml/badge.svg)](https://github.com/pointer1015/ime-switcher/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![VS Code Engine](https://img.shields.io/badge/vscode-%5E1.80.0-blue)](https://code.visualstudio.com/)
[![Platform](https://img.shields.io/badge/platform-Windows%2010%2F11-lightgrey)]()

> 在 VS Code 中根据光标邻近字符，**自动切换 Windows 输入法**（中文 ↔ 英文）。

---

## 功能特性

- **自动切换**：光标移动到中文/英文上下文时，自动切换到对应输入法。
- **防抖保护**：默认 300 ms 防抖，避免频繁调用系统 API。
- **手动切换**：快捷键 `Ctrl+Alt+Z`（中文）/ `Ctrl+Alt+E`（英文），点击状态栏也可切换。
- **白名单 / 黑名单**：按语言 ID 控制生效范围（默认排除 `markdown`、`plaintext`）。
- **状态栏指示**：实时显示当前 IME 状态。
- **原生 Helper**：C 语言实现的 `ime-switcher.exe`，直接调用 Win32 API，性能好；提供 PowerShell 备用脚本。

---

## 系统要求

| 要求 | 版本 |
|------|------|
| 操作系统 | Windows 10 / 11 |
| VS Code | ≥ 1.80.0 |
| 构建工具（可选）| MSVC 或 GCC（仅需自行编译 helper 时） |

---

## 安装

### 方式一：从 VSIX 安装（推荐）

1. 前往 [Releases](https://github.com/your-username/ime-switcher/releases) 下载最新 `.vsix` 文件。
2. VS Code → 扩展面板 → 右上角 `···` → **从 VSIX 安装…**
3. 选择下载的 `.vsix` 文件，重新加载窗口。

### 方式二：从源码构建

```bash
# 1. 克隆仓库
git clone https://github.com/your-username/ime-switcher.git
cd ime-switcher

# 2. 编译 native helper（需要 Windows 环境 + MSVC 或 GCC）
cd native-helper
build.bat
# 会将 ime-switcher.exe 输出到 vscode-extension/bin/

# 3. 构建扩展
cd ../vscode-extension
npm install
npm run compile

# 4. 打包为 VSIX
npx vsce package
```

---

## 快速上手

安装后扩展会自动激活，无需额外配置即可使用：

- **编辑代码时**：光标移到中文字符附近 → 自动切换到中文输入法；移到英文/代码区域 → 切回英文。
- **手动切换**：
  - `Ctrl+Alt+Z` — 切换到中文
  - `Ctrl+Alt+E` — 切换到英文
- **暂停/恢复**：点击状态栏右下角的 IME 状态标识，或使用命令面板 (`Ctrl+Shift+P`) 搜索 `IME Switcher`。

---

## 配置项

在 VS Code 设置（`settings.json`）中可配置以下选项：

```jsonc
{
  // 是否启用自动切换（默认: true）
  "imeSwitcher.enabled": true,

  // 防抖延迟，毫秒（默认: 300）
  "imeSwitcher.delayMs": 300,

  // 白名单模式：只在这些语言 ID 下生效（优先级高于黑名单）
  "imeSwitcher.whitelist": [],

  // 黑名单模式：在这些语言 ID 下不生效
  "imeSwitcher.blacklist": ["markdown", "plaintext"],

## IntelliJ IDEA 插件（idea-plugin）

项目包含一个基于 Kotlin 的 IntelliJ IDEA 插件实现，位于 `idea-plugin/`。该插件为实验性功能，提供与 IDE 集成的输入法自动切换支持（与 VS Code 扩展原理类似）。

构建与调试

- 要求：JDK 17（已在 CI 中使用），以及可选的本地 Gradle；仓库已包含 Gradle Wrapper。
- 在 Windows 下使用项目内的 Gradle Wrapper 构建插件：

```powershell
cd idea-plugin
.\gradlew.bat buildPlugin --no-daemon
.\gradlew.bat verifyPluginConfiguration --no-daemon
```

- 构建产物位于 `idea-plugin/build/distributions/*.zip`，可在 IntelliJ IDEA 的插件管理中安装该 ZIP 进行测试。
- 在开发时可使用沙箱运行或调试插件：

```powershell
cd idea-plugin
.\gradlew.bat runIde
```

注意事项

- 仓库已提交 Gradle Wrapper（`idea-plugin/gradle/wrapper/gradle-wrapper.jar`），无需预先安装 Gradle。
- 本插件主要在 Windows 上做过集成测试；在 macOS/Linux 上的行为未验证。
  // 自定义 ime-switcher.exe 路径（留空则使用内置）
  "imeSwitcher.executable": ""
}
```

---

## 项目结构

```
ime-switcher/
├── native-helper/          # C 语言原生 helper
│   ├── ime-switcher.c      # Win32 API 实现（set/query/hkl 接口）
│   └── build.bat           # 构建脚本（MSVC/GCC 自动检测）
├── vscode-extension/       # VS Code 扩展（TypeScript）
│   ├── src/
│   │   ├── extension.ts    # 扩展入口、事件监听、状态栏
│   │   └── detector.ts     # 字符分类与光标上下文检测
│   ├── bin/
│   │   ├── ime-switcher.exe    # 预编译的 native helper（构建后生成）
│   │   └── ime-switcher.ps1   # PowerShell 备用脚本
│   └── test/
│       └── detector.test.ts   # 单元测试
└── idea-plugin/            # IntelliJ IDEA 插件（Kotlin，实验性）
```

---

## 开发贡献

欢迎提交 Issue 和 Pull Request！详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
