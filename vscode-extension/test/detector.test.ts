/**
 * detector.test.ts
 * detector.ts 的单元测试（Mocha，无需 VS Code 运行时）
 */

import * as assert from 'assert';
import {
  isCJK,
  isChinesePunctuation,
  isAscii,
  isChineseChar,
  isEnglishChar,
  detectContext,
} from '../src/detector';

// ─── isCJK ───────────────────────────────────────────────────

describe('isCJK', () => {
  it('应识别常见汉字', () => {
    assert.strictEqual(isCJK('你'), true);
    assert.strictEqual(isCJK('好'), true);
    assert.strictEqual(isCJK('中'), true);
    assert.strictEqual(isCJK('文'), true);
  });

  it('应识别 CJK 扩展 A 区', () => {
    assert.strictEqual(isCJK('\u3400'), true); // 0x3400
  });

  it('不应将 ASCII 识别为 CJK', () => {
    assert.strictEqual(isCJK('a'), false);
    assert.strictEqual(isCJK('Z'), false);
    assert.strictEqual(isCJK('1'), false);
  });

  it('不应将常见标点识别为 CJK', () => {
    assert.strictEqual(isCJK('.'), false);
    assert.strictEqual(isCJK(','), false);
  });

  it('空字符串应返回 false', () => {
    assert.strictEqual(isCJK(''), false);
  });
});

// ─── isChinesePunctuation ────────────────────────────────────

describe('isChinesePunctuation', () => {
  it('应识别中文句号', () => {
    assert.strictEqual(isChinesePunctuation('。'), true);
  });

  it('应识别中文逗号', () => {
    assert.strictEqual(isChinesePunctuation('，'), true);
  });

  it('应识别全角感叹号', () => {
    assert.strictEqual(isChinesePunctuation('！'), true);
  });

  it('应识别省略号 …', () => {
    assert.strictEqual(isChinesePunctuation('…'), true);
  });

  it('不应将 ASCII 标点识别为中文标点', () => {
    assert.strictEqual(isChinesePunctuation('.'), false);
    assert.strictEqual(isChinesePunctuation('!'), false);
  });
});

// ─── isAscii ────────────────────────────────────────────────

describe('isAscii', () => {
  it('字母应为 ASCII', () => {
    assert.strictEqual(isAscii('a'), true);
    assert.strictEqual(isAscii('Z'), true);
  });

  it('数字应为 ASCII', () => {
    assert.strictEqual(isAscii('0'), true);
    assert.strictEqual(isAscii('9'), true);
  });

  it('空格应为 ASCII', () => {
    assert.strictEqual(isAscii(' '), true);
  });

  it('CJK 不应为 ASCII', () => {
    assert.strictEqual(isAscii('中'), false);
  });
});

// ─── isEnglishChar ──────────────────────────────────────────

describe('isEnglishChar', () => {
  it('字母应为英文字符', () => {
    assert.strictEqual(isEnglishChar('a'), true);
    assert.strictEqual(isEnglishChar('Z'), true);
  });

  it('空格不算英文字符', () => {
    assert.strictEqual(isEnglishChar(' '), false);
    assert.strictEqual(isEnglishChar('\t'), false);
  });

  it('CJK 不应为英文字符', () => {
    assert.strictEqual(isEnglishChar('中'), false);
  });
});

// ─── detectContext ───────────────────────────────────────────

describe('detectContext', () => {
  it('光标前为中文 → zh', () => {
    // 光标在索引 1，字符串只含"你"，后无字符
    const result = detectContext('你', 1);
    assert.strictEqual(result, 'zh');
  });

  it('光标前为英文 → en', () => {
    const result = detectContext('hello', 3);
    assert.strictEqual(result, 'en');
  });

  it('光标前后均为中文 → zh', () => {
    // "你好"，光标在 1
    const result = detectContext('你好', 1);
    assert.strictEqual(result, 'zh');
  });

  it('混合中英文 → mixed', () => {
    // "你a"，光标在 1，前=你(zh) 后=a(en)
    const result = detectContext('你a', 1);
    assert.strictEqual(result, 'mixed');
  });

  it('空行 → unknown', () => {
    const result = detectContext('', 0);
    assert.strictEqual(result, 'unknown');
  });

  it('光标在行首，前无字符 → 仅取后 1 字符', () => {
    // 光标=0，后字符为 "H"
    const result = detectContext('Hello', 0);
    assert.strictEqual(result, 'en');
  });

  it('光标在行末，后无字符 → 仅取前 1 字符', () => {
    const result = detectContext('你好', 2);
    assert.strictEqual(result, 'zh');
  });

  it('中文标点应被识别为 zh', () => {
    // 句号在索引 0
    const result = detectContext('。', 0);
    assert.strictEqual(result, 'zh');
  });

  it('lookAround=2 的多字符检测', () => {
    // "你好ab"，光标 2，前2=["你","好"] 后2=["a","b"] → mixed
    const result = detectContext('你好ab', 2, { lookAround: 2 });
    assert.strictEqual(result, 'mixed');
  });

  it('连续英文单词 → en', () => {
    const result = detectContext('const x = 1', 5);
    assert.strictEqual(result, 'en');
  });

  it('空白字符周围为 unknown', () => {
    // 空格不计入中英文计数
    const result = detectContext('   ', 1);
    assert.strictEqual(result, 'unknown');
  });
});
