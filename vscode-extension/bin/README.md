# bin/

此目录用于存放 `ime-switcher.exe`。

## 获取方式

运行 `native-helper/build.bat` 编译后，exe 会自动输出到此目录。

## 手动放置

1. 在 `native-helper/` 目录执行 `build.bat`
2. 或将已编译好的 `ime-switcher.exe` 直接复制到此目录

exe 将被扩展通过 `child_process.execFile` 调用，接口：
- `ime-switcher.exe set zh`   → 切换到中文输入法
- `ime-switcher.exe set en`   → 切换到英文输入法
- `ime-switcher.exe query`    → 查询当前输入法（输出 `zh` 或 `en`）
