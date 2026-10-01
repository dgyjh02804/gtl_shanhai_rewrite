// ============================================================================
// gen_pf_kjs.js —— 「原初物质定型」2457 条：数据包 json → KubeJS
//
// 🔴 为什么源是【数据包里的 json】而不是 kubejs export：
//   · gen_pf_forming.js 的输入是 kubejs export（环境变量 SH_KJS_EXPORT 指定的目录）。
//     实测：实例里那份 export 的 mtime 是 2026-09-27 12:05，而当前 jar 是 2026-10-01 21:36
//     ⇒ 用它重跑【只会得到另一个集合】（现算：extruder 1317 / fluid_solidifier 1104，
//     而当时的产出是 1344 / 1113）⇒ 那不是"迁移"，那是"换数据"。
//   · 而数据包里那 2457 个 json 的 sha256 与 temp\pf-fix\manifest-v3.json【逐条 2457/2457 一致】
//     ⇒ 它就是当初生成器的产物本身，也是当前 jar 里装的那一份。
//   ⇒ 所以本脚本从【那 2457 个 json】转 KJS —— 这才叫"把这批配方从数据包搬到 KJS"。
//
// 用法（不需要任何环境变量）：
//   node kubejs\_generators\gen_pf_kjs.js
//
// 产物：kubejs\server_scripts\[server_scripts]shanhai_primordial_forming.js
// 发射逻辑：kubejs\_generators\_pf_kjs_emit.js（与 gen_pf_forming.js 共用，保证同源）
// ============================================================================
'use strict'

const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const emit = require('./_pf_kjs_emit.js')

const REPO = path.join(__dirname, '..', '..')
const SRC = path.join(REPO, 'shanhai-rewrite', 'src', 'main', 'resources', 'data', 'shanhai', 'recipes', 'primordial_forming')
const OUT = path.join(REPO, 'kubejs', 'server_scripts', '[server_scripts]shanhai_primordial_forming.js')

// ── 读源：只认 .json，按文件名排序（保证产物可复现） ────────────────────────
if (!fs.existsSync(SRC)) {
    throw new Error('🔴 源目录不存在：' + SRC + '\n   （如果数据包已经被搬走，请先把 json 拷回来再跑本脚本）')
}
const names = fs.readdirSync(SRC).filter(n => n.endsWith('.json')).sort()
if (!names.length) throw new Error('🔴 源目录里没有 json：' + SRC)

let bytes = 0
const rows = []
const sha = {}
for (const n of names) {
    const buf = fs.readFileSync(path.join(SRC, n))
    bytes += buf.length
    sha[n] = crypto.createHash('sha256').update(buf).digest('hex')
    let j
    try { j = JSON.parse(buf.toString('utf8')) } catch (e) {
        throw new Error('🔴 ' + n + ' 解析失败：' + e.message)
    }
    rows.push(emit.jsonToRow(n.replace(/\.json$/, ''), j))
}

const text = emit.emitFile(rows)

// ── 产物自检：写之前先确认产物本身形状对 ──────────────────────────────────
const lines = text.split('\n')
const rowLines = lines.filter(l => /^\{n:/.test(l))
if (rowLines.length !== rows.length) {
    throw new Error('🔴 产物里的数据行数 ' + rowLines.length + ' ≠ 输入条数 ' + rows.length)
}
if (lines.filter(l => /^\{n:/.test(l)).length !== emit.DECLARED) {
    throw new Error('🔴 产物行数 ≠ ' + emit.DECLARED)
}

fs.writeFileSync(OUT, text, 'utf8')

// ── 写出后回读核对（防止"写是写了、内容不是那个"）────────────────────────
const back = fs.readFileSync(OUT, 'utf8')
if (back !== text) throw new Error('🔴 回读内容与写出的不一致')
const backRows = back.split('\n').filter(l => /^\{n:/.test(l)).length

console.log('=== gen_pf_kjs ===')
console.log('源目录        = ' + SRC)
console.log('源 json 条数  = ' + names.length + '  （合计 ' + bytes + ' B / ' + (bytes / 1048576).toFixed(2) + ' MB）')
console.log('产物          = ' + OUT)
console.log('产物行数      = ' + backRows + '  (期望 ' + emit.DECLARED + ')')
console.log('产物字节      = ' + Buffer.byteLength(back, 'utf8') + ' B (' + (Buffer.byteLength(back, 'utf8') / 1024).toFixed(1) + ' KiB)')
console.log('产物总行数    = ' + back.split('\n').length)
console.log('sha256(产物)  = ' + crypto.createHash('sha256').update(back, 'utf8').digest('hex'))
// 供 verify_pf_kjs.js 对账：把源 sha 写一份旁证
fs.mkdirSync(path.join(REPO, 'temp', 'pf-kjs'), { recursive: true })
fs.writeFileSync(path.join(REPO, 'temp', 'pf-kjs', 'source-sha256.json'),
    JSON.stringify(sha, null, 1), 'utf8')
console.log('源 sha256 旁证 = temp\\pf-kjs\\source-sha256.json')
