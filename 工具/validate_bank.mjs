// 数据完整性校验（移动版质量门禁·数据层）。用法：node validate_bank.mjs
// 校验从 HTML 抽取的 assets/*.json 结构正确、字段齐全、无孤儿章节、科三 必带 disc。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const A = path.resolve(__dirname, '../app/src/main/assets');
const read = (f) => JSON.parse(fs.readFileSync(path.join(A, f), 'utf8'));

const bank = read('bank.json');
const syllabus = read('syllabus.json');
const defaultSyll = read('default_syllabus.json');
const autoSyll = read('auto_syll.json');
const knowledge = read('knowledge.json');

let pass = 0, fail = 0;
const assert = (n, c, e = '') => { if (c) { pass++; console.log('🟢', n, e); } else { fail++; console.log('🔴', n, e); } };
const reqFields = ['id', 'subject', 'chapter', 'q'];

// 题库
const exam = bank.exam || [];
assert('题库非空', exam.length > 0, '=' + exam.length);
assert('题库=3312', exam.length === 3312, '=' + exam.length);
const ids = new Set(exam.map((e) => e.id));
assert('题库 id 无重复', ids.size === exam.length, 'unique=' + ids.size);
let missing = 0;
for (const e of exam) {
  let bad = false;
  for (const f of reqFields) if (!e[f]) { bad = true; break; }
  if (bad) { missing++; continue; }
  // 客观题(opt 非空)必须有 answer；主观题(opt 空)按对/错评判，answer 可空
  if (e.opt && !e.answer) missing++;
}
assert('题库核心字段齐全(id/科目/章节/题干/客观题含答案)', missing === 0, '异常=' + missing);
// 软质量（应用可容错，不阻断门禁）
const noSection = exam.filter((e) => !e.section).length;
const noAnalysis = exam.filter((e) => !e.analysis).length;
console.log('⚠️ 缺 section', noSection, '| 缺 analysis', noAnalysis, '（应用容错：显示章节/“暂无解析”）');
const s3 = exam.filter((e) => e.subject === '科三');
assert('科三全部带 disc', s3.every((e) => !!e.disc), '无disc=' + s3.filter((e) => !e.disc).length);
assert('套卷=6', (bank.papers || []).length === 6, '=' + (bank.papers || []).length);

// 大纲（DEFAULT_SYLLABUS 含三科，用于孤儿检测）
const defMap = {};
for (const s of defaultSyll) defMap[s.subject] = new Set(s.chapters.map((c) => c.name));

let orphan = 0;
for (const e of exam) {
  const set = defMap[e.subject];
  if (!set) { orphan++; continue; }
  if (!set.has(e.chapter)) orphan++;
}
assert('无孤儿章节(题的章节均在大纲中)', orphan === 0, '孤儿=' + orphan);

// 归类规则
assert('AUTO_SYLL 为数组', Array.isArray(autoSyll) && autoSyll.length >= 2, '=' + autoSyll.length);
let badRule = 0;
for (const s of autoSyll) for (const r of (s.rules || [])) if (!r.ch || !r.sec || !Array.isArray(r.kws)) badRule++;
assert('AUTO_SYLL 规则结构正确', badRule === 0, '异常=' + badRule);

// 知识库
assert('知识库=42', knowledge.length === 42, '=' + knowledge.length);
assert('知识库字段', knowledge.every((k) => k.id && k.title && k.content), '');

console.log('\n=== 数据校验结果 ===');
console.log(`🟢 ${pass}  🔴 ${fail}`);
console.log('科一', exam.filter((e) => e.subject === '科一').length,
  '| 科二', exam.filter((e) => e.subject === '科二').length,
  '| 科三', s3.length, '| 学科数', new Set(s3.map((e) => e.disc)).size);
process.exit(fail === 0 ? 0 : 1);
