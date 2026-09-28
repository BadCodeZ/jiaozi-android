// 从现有「综合教资备考工作台.html」抽取 4 个数据全局，导出为干净 JSON，供 Android 内置。
// 用法：node extract_assets.mjs
// 依赖：仅需 node（无需第三方包）。
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SRC = path.resolve(__dirname, '../../个人学习工作台/综合教资备考工作台.html');
const OUT = path.resolve(__dirname, '../app/src/main/assets');

const html = fs.readFileSync(SRC, 'utf8');

// 平衡括号提取：从 openChar 起，跟踪字符串与括号深度，到深度回 0 为止。
function extractBalanced(text, marker, openChar) {
  const start = text.indexOf(marker);
  if (start < 0) throw new Error('未找到标记: ' + marker);
  const ob = text.indexOf(openChar, start);
  if (ob < 0) throw new Error('标记后未找到 ' + openChar + ': ' + marker);
  const closeChar = openChar === '{' ? '}' : ']';
  let depth = 0;
  let i = ob;
  let inStr = null;
  let prev = '';
  for (; i < text.length; i++) {
    const c = text[i];
    if (inStr) {
      if (c === '\\') { i++; continue; } // 跳过转义
      if (c === inStr) inStr = null;
      prev = c; continue;
    }
    if (c === '"' || c === "'" || c === '`') { inStr = c; prev = c; continue; }
    if (c === '{' || c === '[' || c === '(') depth++;
    else if (c === '}' || c === ']' || c === ')') {
      depth--;
      if (depth === 0 && (c === closeChar)) { i++; break; }
    }
    prev = c;
  }
  return text.slice(ob, i);
}

function evalExpr(expr, scope = {}) {
  // 用 Function 求值为真实 JS 值（兼容单引号/未加引号键）；scope 提供种子内引用的辅助函数
  const keys = Object.keys(scope);
  const vals = keys.map((k) => scope[k]);
  const fn = new Function(...keys, 'return (' + expr + ');');
  return fn(...vals);
}

// 知识库种子引用的辅助函数（inject.js 注释：运行时由脚本提供）
let _kid = 0;
const knowledgeScope = {
  uid: () => 'kid' + (++_kid),
  shiftDay: (n) => { const d = new Date(); d.setDate(d.getDate() + (n || 0)); return d.toISOString().slice(0, 10); },
  todayStr: () => new Date().toISOString().slice(0, 10),
};

const targets = [
  { key: 'bank', file: 'bank.json', marker: 'window.__BUILTIN_BANK__ =', open: '{' },
  { key: 'syllabus', file: 'syllabus.json', marker: 'const SYLLABUS =', open: '[' },
  { key: 'defaultSyllabus', file: 'default_syllabus.json', marker: 'const DEFAULT_SYLLABUS =', open: '[' },
  { key: 'autoSyll', file: 'auto_syll.json', marker: 'const AUTO_SYLL =', open: '[' },
  { key: 'knowledge', file: 'knowledge.json', marker: 'const KNOWLEDGE_SEED =', open: '[', scope: 'knowledgeScope' },
];

const report = [];
fs.mkdirSync(OUT, { recursive: true });
for (const t of targets) {
  const expr = extractBalanced(html, t.marker, t.open);
  const scope = t.scope ? eval(t.scope) : {};
  const val = evalExpr(expr, scope);
  const json = JSON.stringify(val, null, 0);
  fs.writeFileSync(path.join(OUT, t.file), json, 'utf8');
  // 计数
  let count = 'n/a';
  if (t.key === 'bank') count = val.exam ? val.exam.length + ' 题' + (val.papers ? ' / ' + val.papers.length + ' 套卷' : '') : '?';
  else if (Array.isArray(val)) count = val.length + ' 条';
  report.push({ file: t.file, bytes: json.length, count });
  console.log('✅ ' + t.file + '  →  ' + count + '  (' + (json.length / 1024).toFixed(1) + ' KB)');
}

// 题库字段抽样校验
const bank = evalExpr(extractBalanced(html, 'window.__BUILTIN_BANK__ =', '{'));
const exam = bank.exam || [];
const sample = exam[0] || {};
const fields = Object.keys(sample);
console.log('\n题库字段:', fields.join(', '));
// 按科目统计
const bySubj = {};
for (const e of exam) bySubj[e.subject] = (bySubj[e.subject] || 0) + 1;
console.log('按科目:', JSON.stringify(bySubj));
// 科三按学科(disc)统计
const byDisc = {};
for (const e of exam) if (e.subject === '科三') { const d = e.disc || '(无disc)'; byDisc[d] = (byDisc[d] || 0) + 1; }
console.log('科三按学科:', JSON.stringify(byDisc));

console.log('\n=== 抽取完成 ===');
console.log('输出目录:', OUT);
