# 贡献指南

感谢你对 IME Switcher 的兴趣！以下指南会帮助你顺利参与贡献。

---

## 开始之前

- 在提交 Issue 或 PR 之前，请先搜索是否已有相同内容。
- 对于较大的功能改动，建议先开 Issue 讨论方案，再动手实现。

---

## 本地开发环境搭建

### 前提条件

| 工具 | 版本要求 |
|------|----------|
| Windows | 10 / 11 |
| Node.js | ≥ 18 |
| npm | ≥ 9 |
| JDK | 17（仅 IDEA 插件） |
| MSVC 或 GCC | 任意（仅 native helper） |

### 克隆与安装

```bash
git clone https://github.com/your-username/ime-switcher.git
cd ime-switcher
```

### VS Code 扩展（主要开发区域）

```bash
cd vscode-extension
npm install       # 安装依赖
npm run compile   # 编译 TypeScript
npm test          # 运行单元测试
```

在 VS Code 中按 `F5` 可打开"扩展开发宿主"进行调试。

### Native Helper

```bash
cd native-helper
build.bat         # 编译，输出到 vscode-extension/bin/ime-switcher.exe
```

### IntelliJ IDEA 插件

```bash
cd idea-plugin
.\gradlew.bat runIde        # 在沙箱 IDE 中运行
.\gradlew.bat buildPlugin   # 打包
```

---

## 代码风格

- **TypeScript**：遵循项目中 `tsconfig.json` 的配置；使用 2 空格缩进。
- **Kotlin**：遵循 Kotlin 官方代码风格；使用 4 空格缩进。
- **C**：遵循项目现有风格；使用 4 空格缩进。
- 提交前请确保 `npm run compile` 无报错。

---

## Commit Message 规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <subject>

[optional body]
[optional footer]
```

常用 type：

| type | 用途 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档更新 |
| `refactor` | 代码重构 |
| `test` | 测试相关 |
| `chore` | 构建/工具链 |
| `perf` | 性能优化 |

示例：
```
feat(detector): 增加对全角英文字符的识别
fix(extension): 修复快速移动光标时的竞态问题
```

---

## 提交 Pull Request

1. Fork 本仓库，在自己的 fork 上新建分支：
   ```bash
   git checkout -b feat/your-feature-name
   ```
2. 实现功能并添加测试。
3. 确保 `npm test` 全部通过。
4. Push 并创建 PR，填写 PR 模板中的所有内容。
5. 等待 CI 通过和 Code Review。

---

## 报告 Issue

使用 [Bug Report 模板](.github/ISSUE_TEMPLATE/bug_report.yml) 或 [Feature Request 模板](.github/ISSUE_TEMPLATE/feature_request.yml)，提供尽可能详细的信息。

---

## 行为准则

请友善、尊重地与所有贡献者交流。本项目遵循"包容、建设性"原则，对任何形式的骚扰零容忍。
