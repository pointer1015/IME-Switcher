<#
.SYNOPSIS
  IME Switcher — PowerShell 版 native helper（备用，等同于 ime-switcher.exe 接口）

.DESCRIPTION
  通过 .NET P/Invoke 调用 Win32 IMM API 与 SendInput，在微软拼音 IME 内部
  切换中/英文转换模式。

  查询（query）：通过 IMM32 IMC_GETCONVERSIONMODE 读取实际 IME 状态。
  设置（set）：
    1. 优先 SendInput 模拟 Shift 键 —— 微软拼音（Windows 10/11 TSF 架构）
       默认以 Shift 切换中/英模式，SendInput 注入系统输入队列后由微软拼音
       在输入管道中截获，是跨进程场景最可靠的方法。
    2. IMM32 WM_IME_CONTROL + SendMessageTimeout —— 兼容旧版 IMM32 输入法。

.USAGE
  ime-switcher.ps1 set zh    在微软拼音中切换到中文模式
  ime-switcher.ps1 set en    在微软拼音中切换到英文模式
  ime-switcher.ps1 query     查询当前输入法状态（输出 zh / en）
#>

param(
    [Parameter(Position=0, Mandatory=$true)]
    [ValidateSet('set','query')]
    [string]$Command,

    [Parameter(Position=1)]
    [string]$Arg1,

    # 切换按键模式： shift(微软拼音默认) / ctrl(自定义 Ctrl 切换) / auto(先 Shift 再 Ctrl)
    [string]$Key = 'auto'
)

# ── Win32 API P/Invoke ──────────────────────────────────────────────
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public class ImeHelper {
    // user32
    [DllImport("user32.dll", SetLastError = true)]
    public static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll", SetLastError = true)]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    [DllImport("user32.dll", CharSet = CharSet.Auto)]
    public static extern IntPtr GetKeyboardLayout(uint idThread);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    public static extern IntPtr SendMessage(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool PostMessage(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern IntPtr SendMessageTimeout(IntPtr hWnd, uint Msg, IntPtr wParam, IntPtr lParam,
        uint fuFlags, uint uTimeout, out IntPtr lpdwResult);

    // SendInput
    [DllImport("user32.dll", SetLastError = true)]
    public static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    // imm32
    [DllImport("imm32.dll")]
    public static extern IntPtr ImmGetDefaultIMEWnd(IntPtr hWnd);

    public const uint WM_IME_CONTROL       = 0x0283;
    public const uint IMC_GETCONVERSIONMODE = 0x0001;
    public const uint IMC_SETCONVERSIONMODE = 0x0002;
    public const uint IME_CMODE_NATIVE      = 0x0001;   // 中文模式标志位
    public const uint SMTO_ABORTIFHUNG     = 0x0002;
    public const uint SMTO_BLOCK           = 0x0001;
    public const int  LANG_CHINESE          = 0x04;

    // VK_SHIFT = 0x10
    public const ushort VK_SHIFT = 0x10;
    public const uint   KEYEVENTF_KEYUP = 0x0002;
    public const uint   INPUT_KEYBOARD  = 1;
}

[StructLayout(LayoutKind.Sequential)]
public struct KEYBDINPUT {
    public ushort wVk;
    public ushort wScan;
    public uint   dwFlags;
    public uint   time;
    public IntPtr dwExtraInfo;
}

[StructLayout(LayoutKind.Explicit)]
public struct INPUT_UNION {
    [FieldOffset(0)] public KEYBDINPUT ki;
}

[StructLayout(LayoutKind.Sequential)]
public struct INPUT {
    public uint       type;
    public INPUT_UNION union;
}
'@ -Language CSharp

# ── 辅助函数 ────────────────────────────────────────────────────────

function Get-PrimaryLang([IntPtr]$hkl) {
    $langId = [int64]$hkl -band 0xFFFF
    return $langId -band 0x3FF
}

function Test-ChineseIme([IntPtr]$hwnd) {
    $uint = [uint32]0
    $tid  = [ImeHelper]::GetWindowThreadProcessId($hwnd, [ref]$uint)
    $hkl  = [ImeHelper]::GetKeyboardLayout($tid)
    return (Get-PrimaryLang $hkl) -eq [ImeHelper]::LANG_CHINESE
}

# ── query ───────────────────────────────────────────────────────────
# 仅通过 IMM32 读取实际 IME 状态，不依赖 TSF 全局隔间
# （TSF 全局隔间可被外部写入污染，不反映微软拼音真实状态）

function Invoke-Query {
    $hwnd = [ImeHelper]::GetForegroundWindow()
    if ($hwnd -eq [IntPtr]::Zero) { Write-Output "en"; exit 0 }

    if (-not (Test-ChineseIme $hwnd)) { Write-Output "en"; exit 0 }

    $imeWnd = [ImeHelper]::ImmGetDefaultIMEWnd($hwnd)
    if ($imeWnd -eq [IntPtr]::Zero) { Write-Output "en"; exit 0 }

    $outResult = [IntPtr]::Zero
    $ret = [ImeHelper]::SendMessageTimeout(
        $imeWnd,
        [ImeHelper]::WM_IME_CONTROL,
        [IntPtr][ImeHelper]::IMC_GETCONVERSIONMODE,
        [IntPtr]::Zero,
        ([ImeHelper]::SMTO_ABORTIFHUNG -bor [ImeHelper]::SMTO_BLOCK),
        500,
        [ref]$outResult
    )
    $convMode = if ($ret -ne [IntPtr]::Zero) { [int64]$outResult } else { 0 }

    if ($convMode -band [ImeHelper]::IME_CMODE_NATIVE) { Write-Output "zh" }
    else                                                { Write-Output "en" }
    exit 0
}

# ── set ─────────────────────────────────────────────────────────────
# 切换策略（优先级从高到低）：
#   1. SendInput(Shift)     —— 跨进程最可靠，微软拼音在输入管道拦截
#   2. IMM32 ICM_SETCONVERSIONMODE —— 兼容旧版 IMM32 输入法

function Invoke-Set([string]$lang) {
    if ($lang -ne "zh" -and $lang -ne "en") {
        Write-Error "Unknown language: $lang"
        exit 1
    }

    $hwnd = [ImeHelper]::GetForegroundWindow()
    if ($hwnd -eq [IntPtr]::Zero) {
        Write-Error "No foreground window"
        exit 1
    }

    # 非中文键盘时静默跳过
    if (-not (Test-ChineseIme $hwnd)) {
        Write-Output "skipped"
        exit 0
    }

    $imeWnd = [ImeHelper]::ImmGetDefaultIMEWnd($hwnd)
    if ($imeWnd -eq [IntPtr]::Zero) {
        Write-Error "ImmGetDefaultIMEWnd failed"
        exit 1
    }

    # 查询当前真实状态
    $outResult = [IntPtr]::Zero
    $ret = [ImeHelper]::SendMessageTimeout(
        $imeWnd,
        [ImeHelper]::WM_IME_CONTROL,
        [IntPtr][ImeHelper]::IMC_GETCONVERSIONMODE,
        [IntPtr]::Zero,
        ([ImeHelper]::SMTO_ABORTIFHUNG -bor [ImeHelper]::SMTO_BLOCK),
        500,
        [ref]$outResult
    )
    $curMode = if ($ret -ne [IntPtr]::Zero) { [int64]$outResult } else { 0 }
    $curZh   = ($curMode -band [ImeHelper]::IME_CMODE_NATIVE) -ne 0
    $wantZh  = ($lang -eq "zh")

    # 当前已是目标状态，无需操作
    if ($curZh -eq $wantZh) {
        Write-Output "ok"
        exit 0
    }

    # ── 方案1：SendInput 注入按键 ────────────────────────────────────
    # 微软拼音（Windows 10/11 TSF 架构）在输入管道中截获 Shift/Ctrl 并切换模式。
    # 辅助函数：注入单个虚拟键 Down+Up
    function Send-Vk([ushort]$vk) {
        $inp = New-Object 'INPUT[]' 2
        foreach ($i in 0,1) { $inp[$i] = New-Object INPUT; $inp[$i].type = [ImeHelper]::INPUT_KEYBOARD; $inp[$i].union = New-Object INPUT_UNION; $inp[$i].union.ki = New-Object KEYBDINPUT }
        $inp[0].union.ki.wVk = $vk; $inp[0].union.ki.dwFlags = 0
        $inp[1].union.ki.wVk = $vk; $inp[1].union.ki.dwFlags = [ImeHelper]::KEYEVENTF_KEYUP
        return [ImeHelper]::SendInput(2, $inp, [System.Runtime.InteropServices.Marshal]::SizeOf([INPUT]))
    }

    $keyMode = $Key.ToLower()
    if ($keyMode -eq 'shift') {
        if ((Send-Vk ([ImeHelper]::VK_SHIFT)) -eq 2) { Write-Output "ok"; exit 0 }
    } elseif ($keyMode -eq 'ctrl') {
        if ((Send-Vk ([uint16]0x11)) -eq 2) { Write-Output "ok"; exit 0 }   # VK_CONTROL = 0x11
    } else {
        # auto 模式：先注入 Shift，100ms 后验证；若未生效再注入 Ctrl
        if ((Send-Vk ([ImeHelper]::VK_SHIFT)) -eq 2) {
            Start-Sleep -Milliseconds 100
            $outAfter = [IntPtr]::Zero
            $retAfter = [ImeHelper]::SendMessageTimeout(
                $imeWnd, [ImeHelper]::WM_IME_CONTROL,
                [IntPtr][ImeHelper]::IMC_GETCONVERSIONMODE, [IntPtr]::Zero,
                ([ImeHelper]::SMTO_ABORTIFHUNG -bor [ImeHelper]::SMTO_BLOCK),
                500, [ref]$outAfter)
            if ($retAfter -ne [IntPtr]::Zero) {
                $afterZh = (([int64]$outAfter) -band [ImeHelper]::IME_CMODE_NATIVE) -ne 0
                if ($afterZh -eq $wantZh) { Write-Output "ok"; exit 0 }  # Shift 生效
            }
            # Shift 未生效，尝试 Ctrl
            if ((Send-Vk ([uint16]0x11)) -eq 2) { Write-Output "ok"; exit 0 }
        }
    }

    # ── 方案2：IMM32 WM_IME_CONTROL（旧版 IMM32 IME 兼容）──────────
    if ($wantZh) {
        $newMode = $curMode -bor  [int64][ImeHelper]::IME_CMODE_NATIVE
    } else {
        $newMode = $curMode -band (-bnot [int64][ImeHelper]::IME_CMODE_NATIVE)
    }

    [ImeHelper]::SendMessageTimeout(
        $imeWnd,
        [ImeHelper]::WM_IME_CONTROL,
        [IntPtr][ImeHelper]::IMC_SETCONVERSIONMODE,
        [IntPtr]$newMode,
        ([ImeHelper]::SMTO_ABORTIFHUNG -bor [ImeHelper]::SMTO_BLOCK),
        500,
        [ref]$outResult
    ) | Out-Null

    Write-Output "ok"
    exit 0
}

# ── 入口 ─────────────────────────────────────────────────────────────

switch ($Command) {
    'query' { Invoke-Query }
    'set'   {
        if (-not $Arg1) { Write-Error "Missing language argument"; exit 1 }
        Invoke-Set $Arg1
    }
}
