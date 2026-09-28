// 核心算法 node 仿真：在移植到 Kotlin 之前，先用真实题库验证算法正确性。
// 用法：node logic_sim.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const A = path.resolve(__dirname, '../app/src/main/assets');
const bank = JSON.parse(fs.readFileSync(path.join(A, 'bank.json'), 'utf8'));
const autoSyll = JSON.parse(fs.readFileSync(path.join(A, 'auto_syll.json'), 'utf8'));
const exam = bank.exam || [];
const papers = bank.papers || [];

// ---------- 自动归类 (移植自 HTML autoClassify / classifyText) ----------
function classifyText(text, subjHint) {
  const t = (text || '').toLowerCase();
  const subjects = subjHint ? autoSyll.filter((s) => s.subj === subjHint) : autoSyll;
  let best = null;
  let bestScore = 0;
  for (const s of subjects) {
    for (const r of s.rules) {
      let score = 0;
      for (const kw of r.kws) {
        if (t.includes(kw.toLowerCase())) score += 1;
      }
      if (score > bestScore) { bestScore = score; best = { subj: s.subj, ch: r.ch, sec: r.sec }; }
    }
  }
  return bestScore > 0 ? best : (subjHint ? { subj: subjHint, ch: '', sec: '' } : null);
}

// ---------- 薄弱评分 (移植自 HTML weakness) ----------
// weakness = 错误率*0.65 + 临期*0.35；未练过给 0.5 基线（确保新题也能进入"薄弱优先"）
function weakness(right, wrong, dueTs) {
  const total = right + wrong;
  if (total === 0) return 0.5;
  const errRate = wrong / total;
  const now = Date.now();
  const due = dueTs || 0;
  const overdue = due > 0 && due <= now;
  const dueWeight = overdue ? 1 : 0;
  return Math.min(1, errRate * 0.65 + dueWeight * 0.35);
}

// ---------- 归类题目（优先保留已有归类；否则按科一/科二关键词；科三按大纲章节名回退） ----------
const syllabus = JSON.parse(fs.readFileSync(path.join(A, 'default_syllabus.json'), 'utf8'));
function classifyQuestion(e, subjHint) {
  if (e.subject && e.chapter && e.section) return { subj: e.subject, ch: e.chapter, sec: e.section };
  const hint = subjHint || e.subject;
  if (hint === '科三') {
    const s3 = syllabus.find((s) => s.subject === '科三');
    if (s3) {
      const t = (e.q || '').toLowerCase();
      for (const c of s3.chapters) for (const sec of c.sections) {
        if (t.includes(sec.toLowerCase())) return { subj: '科三', ch: c.name, sec };
      }
    }
    return { subj: '科三', ch: e.chapter || '', sec: e.section || '' };
  }
  return classifyText(e.q, hint);
}

// ---------- 抽题：全科模考蓝图 (移植自 HTML startBlueprintPaper / CHAPTER_WEIGHT) ----------
// 按章节权重加权抽 50 题：科一/科二/科三 ≈ 33/33/34，科三取当前 disc，卷内无重复，不串其他 disc。
function blueprintSample(disc, count = 50) {
  const bySubj = { '科一': [], '科二': [], '科三': [] };
  for (const e of exam) {
    if (e.subject === '科三') { if (e.disc === disc) bySubj['科三'].push(e); }
    else bySubj[e.subject]?.push(e);
  }
  const plan = [
    { subj: '科一', n: Math.round(count * 0.33) },
    { subj: '科二', n: Math.round(count * 0.33) },
    { subj: '科三', n: count - Math.round(count * 0.33) * 2 },
  ];
  const pick = (arr, n) => {
    const pool = [...arr];
    const out = [];
    while (out.length < n && pool.length) out.push(pool.splice(Math.floor(Math.random() * pool.length), 1)[0]);
    return out;
  };
  const out = [];
  for (const p of plan) out.push(...pick(bySubj[p.subj] || [], p.n));
  // 去重校验
  const ids = new Set(out.map((e) => e.id));
  return { out, ids: ids.size, bySubjCount: { '科一': out.filter((e) => e.subject === '科一').length, '科二': out.filter((e) => e.subject === '科二').length, '科三': out.filter((e) => e.subject === '科三').length } };
}

// ---------- 间隔复习 (简化 Leitner) ----------
const INTERVALS = [1, 3, 7, 15, 30]; // 天
function nextDue(lastResult, streak, fromTs = Date.now()) {
  const day = 86400000;
  if (lastResult !== 'right') return fromTs + day; // 答错/未练：明天
  const idx = Math.min(streak, INTERVALS.length - 1);
  return fromTs + INTERVALS[idx] * day;
}

// ================= 断言验证 =================
let pass = 0, fail = 0;
function assert(name, cond, extra = '') { if (cond) { pass++; console.log('🟢', name, extra); } else { fail++; console.log('🔴', name, extra); } }

console.log('题库总量:', exam.length, ' 套卷:', papers.length);

// 1) 归类：科三·美术题保留已有归类（科三 无关键词规则，靠 disc+chapter 预归类，与 HTML 一致）
const artQ = exam.find((e) => e.subject === '科三' && e.disc === '美术' && e.chapter && e.section);
if (artQ) {
  const c = classifyQuestion(artQ, '科三');
  assert('归类-科三美术保留已有归类', c && c.ch === artQ.chapter && c.sec === artQ.section, JSON.stringify(c));
} else assert('归类-科三美术样本存在', false);

// 1b) 归类：科二·教学原则题保留已有归类（验证科二分支同样走"保留已有"）
const k2 = exam.find((e) => e.subject === '科二' && e.chapter && e.section);
if (k2) {
  const c = classifyQuestion(k2, '科二');
  assert('归类-科二保留已有归类', c && c.ch === k2.chapter && c.sec === k2.section, JSON.stringify(c));
} else assert('归类-科二样本存在', false);

// 2) 归类：科一素质教育题
const q1 = exam.find((e) => e.subject === '科一' && /素质教育/.test(e.q));
if (q1) {
  const c = classifyText(q1.q, '科一');
  assert('归类-科一素质教育→教育观', c && c.sec === '教育观', JSON.stringify(c));
}

// 3) 薄弱评分：高错误率+逾期 ≈ 高位
const wHigh = weakness(1, 9, Date.now() - 1000); // 90%错 + 逾期
assert('薄弱-高错逾期接近1', wHigh > 0.8, '=' + wHigh.toFixed(3));
const wNew = weakness(0, 0, 0); // 未练
assert('薄弱-未练=0.5', Math.abs(wNew - 0.5) < 1e-9, '=' + wNew);

// 4) 模考蓝图：50题，三科覆盖，科三全同disc，无重复
const bp = blueprintSample('美术', 50);
assert('蓝图-总量50', bp.out.length === 50, '=' + bp.out.length);
assert('蓝图-无重复', bp.ids === 50, 'unique=' + bp.ids);
assert('蓝图-覆盖三科', bp.bySubjCount['科一'] > 0 && bp.bySubjCount['科二'] > 0 && bp.bySubjCount['科三'] > 0, JSON.stringify(bp.bySubjCount));
assert('蓝图-科三≥10且不串', bp.bySubjCount['科三'] >= 10 && bp.out.filter((e) => e.subject === '科三').every((e) => e.disc === '美术'), JSON.stringify(bp.bySubjCount));

// 5) 间隔复习：答错→明天；连对→间隔递增
assert('间隔-答错明天', nextDue('wrong', 0) - Date.now() <= 86400000 + 1000);
assert('间隔-连对30天', nextDue('right', 5) - Date.now() >= 29 * 86400000);

console.log('\n=== 仿真结果 ===');
console.log(`🟢 ${pass}  🔴 ${fail}`);
process.exit(fail === 0 ? 0 : 1);
