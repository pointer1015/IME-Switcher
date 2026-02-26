/**
 * detector.ts
 * 字符分类与光标上下文语言检测逻辑。
 */

/** 光标语言上下文 */
export type LangContext = 'zh' | 'en' | 'mixed' | 'unknown';

// ─── Unicode 区间 ───────────────────────────────────────────

/** 判断是否为 CJK 汉字及相关表意文字 */
export function isCJK(ch: string): boolean {
  if (!ch) return false;
  const cp = ch.codePointAt(0)!;
  return (
    (cp >= 0x4e00 && cp <= 0x9fff) ||   // CJK 统一表意字符
    (cp >= 0x3400 && cp <= 0x4dbf) ||   // CJK 扩展 A
    (cp >= 0x20000 && cp <= 0x2a6df) || // CJK 扩展 B
    (cp >= 0x2a700 && cp <= 0x2ceaf) || // CJK 扩展 C/D/E
    (cp >= 0xf900 && cp <= 0xfaff)      // CJK 兼容表意字符
  );
}

/**
 * 判断是否为常见中文全角标点
 * （句号、逗号、顿号、书名号、引号、感叹号、问号等）
 */
export function isChinesePunctuation(ch: string): boolean {
  if (!ch) return false;
  const cp = ch.codePointAt(0)!;
  // 中文标点区间及常用全角字符
  return (
    (cp >= 0x3000 && cp <= 0x303f) || // CJK 标点符号
    (cp >= 0xff00 && cp <= 0xffef) || // 半角/全角形式
    cp === 0x201c || cp === 0x201d || // ""
    cp === 0x2018 || cp === 0x2019 || // ''
    cp === 0x2026 || cp === 0x2014   // …  —
  );
}

/**
 * 判断是否为 ASCII 英文字符（字母、数字、常用半角标点）
 */
export function isAscii(ch: string): boolean {
  if (!ch) return false;
  const cp = ch.codePointAt(0)!;
  return cp >= 0x20 && cp <= 0x7e;
}

/** 判断字符是否属于"中文"语境 */
export function isChineseChar(ch: string): boolean {
  return isCJK(ch) || isChinesePunctuation(ch);
}

/** 判断字符是否属于"英文/ASCII"语境 */
export function isEnglishChar(ch: string): boolean {
  // 只算 ASCII 且排除纯空格（空格视为"中性"）
  return isAscii(ch) && ch !== ' ' && ch !== '\t' && ch !== '\n' && ch !== '\r';
}

// ─── 上下文检测 ──────────────────────────────────────────────

export interface DetectOptions {
  /** 光标前后各检测的最大字符数，默认 1 */
  lookAround?: number;
}

/**
 * 分析编辑器当前光标（第一个选区）前后字符，
 * 返回应切换的目标语言，或 'unknown' / 'mixed'。
 *
 * @param lineText  光标所在行全文
 * @param charIndex 光标在行内的字符偏移
 * @param opts      检测选项
 */
export function detectContext(
  lineText: string,
  charIndex: number,
  opts: DetectOptions = {}
): LangContext {
  const look = opts.lookAround ?? 1;

  const chars: string[] = [];
  for (let i = charIndex - look; i < charIndex + look; i++) {
    if (i >= 0 && i < lineText.length) {
      chars.push(lineText[i]);
    }
  }

  if (chars.length === 0) return 'unknown';

  let zhCount = 0;
  let enCount = 0;

  for (const ch of chars) {
    if (isChineseChar(ch)) zhCount++;
    else if (isEnglishChar(ch)) enCount++;
    // 其他字符（空白、符号等）不计入
  }

  if (zhCount === 0 && enCount === 0) return 'unknown';
  if (zhCount > 0 && enCount === 0) return 'zh';
  if (enCount > 0 && zhCount === 0) return 'en';
  return 'mixed';
}
