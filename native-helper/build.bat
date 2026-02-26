@echo off
:: ============================================================
:: build.bat — 编译 ime-switcher.exe
:: 优先使用 MSVC (cl.exe)，若不可用则尝试 MinGW (gcc)
:: 输出文件：..\vscode-extension\bin\ime-switcher.exe
:: ============================================================

setlocal

set OUT_DIR=..\vscode-extension\bin
set OUT_EXE=%OUT_DIR%\ime-switcher.exe

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

:: ---- 尝试 cl.exe (MSVC) ----
where cl.exe >nul 2>&1
if %ERRORLEVEL% == 0 (
    echo [build] 使用 MSVC cl.exe 编译...
    cl.exe /nologo /O2 /W3 ^
        ime-switcher.c ^
        /Fe:"%OUT_EXE%" ^
        /link user32.lib kernel32.lib imm32.lib ole32.lib oleaut32.lib uuid.lib
    if %ERRORLEVEL% == 0 (
        echo [build] 编译成功: %OUT_EXE%
        goto :done
    ) else (
        echo [build] MSVC 编译失败，尝试 MinGW...
    )
)

:: ---- 尝试 gcc (MinGW / MSYS2 / Dev-Cpp) ----
:: 按优先级依次在常见安装位置查找
set GCC_EXE=
for %%G in (
    "C:\msys64\ucrt64\bin\gcc.exe"
    "C:\msys64\mingw64\bin\gcc.exe"
    "D:\Dev-Cpp\MinGW64\bin\gcc.exe"
    "C:\MinGW\bin\gcc.exe"
    "C:\mingw64\bin\gcc.exe"
) do (
    if exist %%G (
        set GCC_EXE=%%~G
        goto :found_gcc
    )
)

where gcc >nul 2>&1
if %ERRORLEVEL% == 0 (
    set GCC_EXE=gcc
    goto :found_gcc
)

echo [build] 错误：未找到 cl.exe 或 gcc，请安装 MSVC Build Tools 或 MinGW 后重试。
exit /b 1

:found_gcc
echo [build] 使用 gcc: %GCC_EXE%
"%GCC_EXE%" -std=c99 -O2 -static -Wall -Wno-unused-function -o "%OUT_EXE%" ime-switcher.c -luser32 -lkernel32 -limm32 -lole32 -loleaut32 -luuid
    if %ERRORLEVEL% == 0 (
        echo [build] 编译成功: %OUT_EXE%
        goto :done
    ) else (
        echo [build] gcc 编译失败。
        exit /b 1
    )

:done
endlocal
