/**
 * ime-switcher.exe — Windows IME 切换 native helper
 *
 * 接口：
 *   ime-switcher.exe set zh     在微软拼音 IME 中切换到中文模式
 *   ime-switcher.exe set en     在微软拼音 IME 中切换到英文模式
 *   ime-switcher.exe query      查询当前输入状态，输出 "zh" 或 "en"
 *
 * 退出码：0 成功，1 失败
 *
 * 实现原理（三重方案）：
 *
 *   查询（query）：
 *     通过 IMM32 IMC_GETCONVERSIONMODE 读取实际 IME 转换模式，
 *     反映微软拼音当前真实的中/英文状态。
 *
 *   设置（set）：
 *   1. SendInput 注入按键（最可靠）：
 *      微软拼音（TSF 架构）在系统输入管道中截获 Shift / Ctrl 键并
 *      实际切换中英模式。跨进程无法修改其线程隔间，注入按键是唯一
 *      可靠方式。通过 --key 参数指定:
 *        --key=shift  仅尝试 Shift（微软拼音默认）
 *        --key=ctrl   仅尝试 Ctrl（部分用户自定义配置）
 *        --key=auto   先尝试 Shift；若 100ms 后状态未变则尝试 Ctrl
 *
 *   2. TSF 全局隔间写入（GUID_COMPARTMENT_KEYBOARD_INPUTMODE_CONVERSION）：
 *      同步写入，部分旧版或第三方拼音 IME 会监听此隔间。
 *      注意：Windows 10/11 微软拼音监听的是线程隔间，跨进程写全局
 *      隔间对其无效，但写入后 query 可正确反映状态。
 *
 *   3. 备用：IMM32 WM_IME_CONTROL + SendMessageTimeout
 *      适用于旧版 IMM32 输入法（Windows 7/8）。
 *
 * 注意：使用 INITGUID 内联展开 GUID 定义，无需链接 msctf.lib。
 */

#define COBJMACROS
#define INITGUID
#include <initguid.h>
#include <windows.h>
#include <imm.h>
#include <ole2.h>
#include <msctf.h>
#include <stdio.h>
#include <string.h>

/* ── 常量 ─────────────────────────────────────────────────────── */

#ifndef IME_CMODE_NATIVE
#define IME_CMODE_NATIVE  0x0001
#endif

#define IMC_GETCONVERSIONMODE  0x0001
#define IMC_SETCONVERSIONMODE  0x0002
#define TF_CONVERSIONMODE_NATIVE  0x0001

/*
 * GUID_COMPARTMENT_KEYBOARD_INPUTMODE_CONVERSION
 * {CCBE29E4-7A86-474F-8C64-5B12DDE00D1D}
 *
 * 控制微软拼音"中文/英文"转换模式的 TSF 全局隔间 GUID。
 * Value 含有 TF_CONVERSIONMODE_NATIVE(1) = 中文，否则 = 英文。
 */
static const GUID GUID_KBD_INPUTMODE_CONVERSION = {
    0xCCBE29E4, 0x7A86, 0x474F,
    {0x8C, 0x64, 0x5B, 0x12, 0xDD, 0xE0, 0x0D, 0x1D}
};

/* ── 打印用法 ─────────────────────────────────────────────────── */

static void print_usage(void) {
    fprintf(stderr, "Usage:\n");
    fprintf(stderr, "  ime-switcher.exe set zh [--key=shift|ctrl|auto]\n");
    fprintf(stderr, "  ime-switcher.exe set en [--key=shift|ctrl|auto]\n");
    fprintf(stderr, "  ime-switcher.exe query\n");
    fprintf(stderr, "  --key  切换按键模式: shift(默认)/ctrl/auto(先shift再ctrl)\n");
}

/* ── IMM32 辅助 ───────────────────────────────────────────────── */

static BOOL is_chinese_ime(HWND hwnd) {
    DWORD tid  = GetWindowThreadProcessId(hwnd, NULL);
    HKL   hkl  = GetKeyboardLayout(tid);
    WORD  lang = PRIMARYLANGID(LOWORD((ULONG_PTR)hkl));
    return (lang == LANG_CHINESE);
}

/* IMM32 查询，返回 1=中文 0=英文 -1=失败 */
static int imm32_query(HWND fg) {
    if (!fg || !is_chinese_ime(fg)) return -1;
    HWND ime = ImmGetDefaultIMEWnd(fg);
    if (!ime) return -1;
    DWORD_PTR sr = 0;
    LRESULT r = SendMessageTimeoutW(ime, WM_IME_CONTROL,
        (WPARAM)IMC_GETCONVERSIONMODE, 0,
        SMTO_ABORTIFHUNG | SMTO_BLOCK, 500, &sr);
    if (!r) return -1;
    return (sr & IME_CMODE_NATIVE) ? 1 : 0;
}

/* IMM32 设置：通过 SendMessageTimeout 同步写入转换模式（兼容旧版 IMM32 IME）*/
static int imm32_set(HWND fg, BOOL set_zh) {
    if (!fg || !is_chinese_ime(fg)) return -1;
    HWND ime = ImmGetDefaultIMEWnd(fg);
    if (!ime) return -1;
    DWORD_PTR sr = 0;
    LRESULT r = SendMessageTimeoutW(ime, WM_IME_CONTROL,
        (WPARAM)IMC_GETCONVERSIONMODE, 0,
        SMTO_ABORTIFHUNG | SMTO_BLOCK, 500, &sr);
    if (!r) return -1;
    DWORD nm = set_zh ? ((DWORD)sr | IME_CMODE_NATIVE)
                      : ((DWORD)sr & ~(DWORD)IME_CMODE_NATIVE);
    /* 使用 SendMessageTimeout 同步发送，避免旧版 IME 丢失 PostMessage */
    SendMessageTimeoutW(ime, WM_IME_CONTROL,
        (WPARAM)IMC_SETCONVERSIONMODE, (LPARAM)nm,
        SMTO_ABORTIFHUNG | SMTO_BLOCK, 500, &sr);
    return 0;
}

/* ── SendInput 方案 ──────────────────────────────────────────── */

typedef enum { KEY_AUTO = 0, KEY_SHIFT, KEY_CTRL } ToggleKey;

/* 注入指定虚拟键 Down + Up，不检查当前状态。
 * 返回 0 = 成功注入 / -1 = 失败 */
static int sendinput_inject_vk(WORD vk) {
    INPUT inputs[2];
    ZeroMemory(inputs, sizeof(inputs));
    inputs[0].type       = INPUT_KEYBOARD;
    inputs[0].ki.wVk     = vk;
    inputs[0].ki.dwFlags = 0;
    inputs[1].type       = INPUT_KEYBOARD;
    inputs[1].ki.wVk     = vk;
    inputs[1].ki.dwFlags = KEYEVENTF_KEYUP;
    return (SendInput(2, inputs, sizeof(INPUT)) == 2) ? 0 : -1;
}

/* 查询当前状态，仅在需要切换时才注入指定按键。
 * 返回 0 = 已注入 / 1 = 无需切换（已是目标状态）/ -1 = 无法查询 */
static int sendinput_toggle_if_needed(HWND fg, BOOL set_zh, WORD vk) {
    int cur = imm32_query(fg);
    if (cur < 0) return -1;          /* 非中文键盘或无 IME 窗口，跳过 */
    if ((cur == 1) == (int)set_zh) return 1; /* 已是目标状态，无需操作 */
    return sendinput_inject_vk(vk);
}

/* ── TSF 方案 ────────────────────────────────────────────────── */

static int tsf_query(void) {
    int result = -1;
    HRESULT hr = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    if (FAILED(hr) && hr != RPC_E_CHANGED_MODE) return -1;

    ITfThreadMgr *pTM = NULL;
    hr = CoCreateInstance(&CLSID_TF_ThreadMgr, NULL, CLSCTX_INPROC_SERVER,
                          &IID_ITfThreadMgr, (void **)&pTM);
    if (FAILED(hr)) goto tq_done;

    TfClientId cid = 0;
    if (FAILED(ITfThreadMgr_Activate(pTM, &cid))) goto tq_rel;

    ITfCompartmentMgr *pGCM = NULL;
    if (FAILED(ITfThreadMgr_GetGlobalCompartment(pTM, &pGCM))) goto tq_deact;

    ITfCompartment *pC = NULL;
    GUID g = GUID_KBD_INPUTMODE_CONVERSION;
    if (FAILED(ITfCompartmentMgr_GetCompartment(pGCM, &g, &pC))) goto tq_relgcm;

    VARIANT v; VariantInit(&v);
    if (SUCCEEDED(ITfCompartment_GetValue(pC, &v)) && v.vt == VT_I4)
        result = (v.lVal & TF_CONVERSIONMODE_NATIVE) ? 1 : 0;
    VariantClear(&v);
    ITfCompartment_Release(pC);

tq_relgcm: ITfCompartmentMgr_Release(pGCM);
tq_deact:  ITfThreadMgr_Deactivate(pTM);
tq_rel:    ITfThreadMgr_Release(pTM);
tq_done:   CoUninitialize();
    return result;
}

static int tsf_set(BOOL set_zh) {
    int result = -1;
    HRESULT hr = CoInitializeEx(NULL, COINIT_APARTMENTTHREADED);
    if (FAILED(hr) && hr != RPC_E_CHANGED_MODE) return -1;

    ITfThreadMgr *pTM = NULL;
    hr = CoCreateInstance(&CLSID_TF_ThreadMgr, NULL, CLSCTX_INPROC_SERVER,
                          &IID_ITfThreadMgr, (void **)&pTM);
    if (FAILED(hr)) goto ts_done;

    TfClientId cid = 0;
    if (FAILED(ITfThreadMgr_Activate(pTM, &cid))) goto ts_rel;

    ITfCompartmentMgr *pGCM = NULL;
    if (FAILED(ITfThreadMgr_GetGlobalCompartment(pTM, &pGCM))) goto ts_deact;

    ITfCompartment *pC = NULL;
    GUID g = GUID_KBD_INPUTMODE_CONVERSION;
    if (FAILED(ITfCompartmentMgr_GetCompartment(pGCM, &g, &pC))) goto ts_relgcm;

    /* 读当前值，保留其他标志位 */
    VARIANT vOld; VariantInit(&vOld);
    long cur = 0;
    if (SUCCEEDED(ITfCompartment_GetValue(pC, &vOld)) && vOld.vt == VT_I4)
        cur = vOld.lVal;
    VariantClear(&vOld);

    long nv = set_zh ? (cur |  TF_CONVERSIONMODE_NATIVE)
                     : (cur & ~TF_CONVERSIONMODE_NATIVE);

    VARIANT vNew; VariantInit(&vNew);
    vNew.vt = VT_I4; vNew.lVal = nv;
    if (SUCCEEDED(ITfCompartment_SetValue(pC, cid, &vNew)))
        result = 0;

    ITfCompartment_Release(pC);
ts_relgcm: ITfCompartmentMgr_Release(pGCM);
ts_deact:  ITfThreadMgr_Deactivate(pTM);
ts_rel:    ITfThreadMgr_Release(pTM);
ts_done:   CoUninitialize();
    return result;
}

/* ── 命令实现 ─────────────────────────────────────────────────── */

/* query：仅用 IMM32 读取真实 IME 状态（不读 TSF 全局隔间）
 *
 * TSF 全局隔间可能被本工具上次写入污染，不代表微软拼音当前真实状态；
 * IMM32 的 IMC_GETCONVERSIONMODE 消息通过 SendMessageTimeout 同步读取
 * 微软拼音 IME 窗口的实际转换模式，结果更可靠。
 */
static int cmd_query(void) {
    HWND fg = GetForegroundWindow();
    int r = imm32_query(fg);
    if (r >= 0) { printf("%s\n", r ? "zh" : "en"); return 0; }
    /* 非中文键盘或无法查询，默认报告 en */
    printf("en\n");
    return 0;
}

/* set：多重方案确保微软拼音在 Windows 10/11 下实际切换
 *
 * 优先级：
 *   1. SendInput(按键) ── 最可靠，微软拼音在系统输入管道拦截
 *      key=shift  : 仅注入 Shift
 *      key=ctrl   : 仅注入 Ctrl
 *      key=auto   : 先注入 Shift，等待 100ms 验证；若未生效再注入 Ctrl
 *   2. TSF 全局隔间     ── 对部分旧版/第三方拼音 IME 有效
 *   3. IMM32 消息       ── 兼容 Windows 7/8 旧版 IMM32 输入法
 */
static int cmd_set(const char *lang, ToggleKey tk) {
    BOOL set_zh;
    if (strcmp(lang, "zh") == 0)      set_zh = TRUE;
    else if (strcmp(lang, "en") == 0) set_zh = FALSE;
    else { fprintf(stderr, "Unknown language: %s\n", lang); return 1; }

    HWND fg = GetForegroundWindow();

    /* 方案1：SendInput 注入按键 */
    if (tk == KEY_SHIFT || tk == KEY_CTRL) {
        /* 指定单一按键模式 */
        WORD vk = (tk == KEY_SHIFT) ? VK_SHIFT : VK_CONTROL;
        int r = sendinput_toggle_if_needed(fg, set_zh, vk);
        if (r >= 0) { printf("ok\n"); return 0; }
    } else {
        /* auto 模式：先尝试 Shift，验证后必要时尝试 Ctrl */
        int r = sendinput_toggle_if_needed(fg, set_zh, VK_SHIFT);
        if (r == 1) { printf("ok\n"); return 0; } /* 已是目标状态 */
        if (r == 0) {
            /* Shift 已注入，等待 IME 处理（最多 100ms）*/
            Sleep(100);
            int after = imm32_query(fg);
            if (after >= 0 && (after == 1) == (int)set_zh) {
                printf("ok\n"); return 0; /* Shift 生效 */
            }
            /* Shift 未生效，尝试 Ctrl */
            if (sendinput_inject_vk(VK_CONTROL) == 0) {
                printf("ok\n"); return 0;
            }
        }
        /* imm32_query 失败（非中文键盘），跳过 SendInput 方案 */
    }

    /* 方案2：TSF 全局隔间（对部分 IME 有效） */
    if (tsf_set(set_zh) == 0) { printf("ok\n"); return 0; }

    /* 方案3：IMM32 消息（旧版 IME 兼容） */
    if (imm32_set(fg, set_zh) == 0) { printf("ok\n"); return 0; }

    fprintf(stderr, "Failed: all methods failed\n");
    return 1;
}

/* ── 入口 ─────────────────────────────────────────────────────── */

int main(int argc, char *argv[]) {
    if (argc < 2) { print_usage(); return 1; }
    if (strcmp(argv[1], "query") == 0) return cmd_query();
    if (strcmp(argv[1], "set") == 0) {
        if (argc < 3) { print_usage(); return 1; }
        /* 解析可选 --key=shift|ctrl|auto（默认 auto）*/
        ToggleKey tk = KEY_AUTO;
        for (int i = 3; i < argc; i++) {
            if (strncmp(argv[i], "--key=", 6) == 0) {
                const char *kv = argv[i] + 6;
                if (strcmp(kv, "shift") == 0)      tk = KEY_SHIFT;
                else if (strcmp(kv, "ctrl") == 0)  tk = KEY_CTRL;
                else if (strcmp(kv, "auto") == 0)  tk = KEY_AUTO;
            }
        }
        return cmd_set(argv[2], tk);
    }
    print_usage();
    return 1;
}
