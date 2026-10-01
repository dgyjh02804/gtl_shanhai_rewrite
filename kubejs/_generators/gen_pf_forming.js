// ============================================================================
// 「原初物质定型」数据包生成器 v3 —— 来源修正版（2026-10-01 用户裁决）
//
// 🔴 为什么换源（bug 1 的根因）：
//   · 用户原话：「它里面有【冲压机床】的配方（我要的是【压模器】和【流体固化器】）」
//   · 机器 id ↔ 中文名，取自 gtceu-1.20.1-1.4.4.jar 里的 assets/gtceu/lang/zh_cn.json：
//       "gtceu.extruder"         : "压模器"        ← ✅ 要的
//       "gtceu.fluid_solidifier" : "流体固化器"    ← ✅ 要的
//       "gtceu.forming_press"    : "冲压机床"      ← 🔴 v1/v2 误当成了「压模器」
//   · 还有两条同义的旁证键（同一份语言文件内自洽）：
//       "block.gtceu.lv_extruder"        : "基础压模器§r"
//       "block.gtceu.lv_fluid_solidifier": "基础流体固化器§r"
//       "block.gtceu.lv_forming_press"   : "基础冲压机床§r"
//       "compass.node.gtceu.machines/extruder"        : "压模器"
//       "compass.node.gtceu.machines/fluid_solidifier": "流体固化器"
//       "compass.node.gtceu.machines/forming_press"   : "冲压机床"
//
// 🔴 bug 2（"还有制作模具的配方"）其实是 bug 1 的同一根因：
//   · 冲压机床里有 33 条「空模板 → 模具/模头」的复制配方（copy_mold_* / copy_shape_*）
//     —— 这就是用户在图 1 里看到的「制作模具」。
//   · 压模器(extruder) 与 流体固化器(fluid_solidifier) 里【这种配方一条都没有】（全量扫过，见验证器）。
//   · v2 只按"产出是 *_extruder_mold"剔了 19 条，漏掉 14 条 *_casting_mold ⇒ 用户仍然看得见。
//   ⇒ v3 换源之后两个 bug 同时消失；另外【仍然保留】"产出是模具 ⇒ 剔除"这道兜底闸门
//     （判据按物品 id，不按配方名），防上游以后往这两台机器里加料。
//
// 口径纪律：
//   · 本脚本【不写任何机器绝对路径】：仓库内路径按 __dirname 推；仓库外的 export 目录走环境变量
//     SH_KJS_EXPORT，缺了就响亮抛错退出（照抄 kubejs\_generators\parse_pf.js 的做法）。
// ============================================================================
'use strict'

const fs = require('fs')
const path = require('path')
const crypto = require('crypto')
const EMIT = require('./_pf_kjs_emit.js')   // 🔴 与 gen_pf_kjs.js 共用同一个发射器 ⇒ 两边产物必然同源

const REPO = path.join(__dirname, '..', '..')   // kubejs\_generators → 仓库根

function envPath(name, what, example) {
    const v = process.env[name]
    if (v === undefined || String(v).trim() === '') {
        throw new Error('🔴 缺少环境变量 ' + name + '（' + what + '）\n'
            + '   ⇒ 请先设置它，例如（PowerShell）：$env:' + name + " = '" + example + "'\n"
            + '   ⇒ 本脚本【拒绝】在缺少它的前提下继续运行：那会拿错路径、静默产出错产物。')
    }
    return String(v).trim()
}

const EXPORT = envPath('SH_KJS_EXPORT', 'kubejs export 目录（内含 recipes\\<ns>\\<type>\\*.json）',
    'X:\\...\\local\\kubejs\\export')
const RECIPES = path.join(EXPORT, 'recipes')

// ── 2026-10-01 新增：三个【只为安全试跑存在的】路径覆盖 ──────────────────────
//   默认值 = 原来那两个路径，【行为一个字不改】。
//   加它们的原因：想验证"改动后的生成器还能跑通"时，必须能把产物落到 temp 里，
//   否则会 ①把真数据包 rmSync 掉 ②把 temp\pf-fix 里的留档（manifest-v3 等）覆盖掉。
//   ⚠️ 它们【不影响】正式产出：不设环境变量就是老路径。
function override(name, dflt) {
    const v = process.env[name]
    return (v === undefined || String(v).trim() === '') ? dflt : String(v).trim()
}
const RES = override('SH_PF_RES',
    path.join(REPO, 'shanhai-rewrite', 'src', 'main', 'resources', 'data', 'shanhai', 'recipes', 'primordial_forming'))
const OUT = override('SH_PF_OUT', path.join(REPO, 'temp', 'pf-fix'))
const KJS_OUT = override('SH_PF_KJS_OUT',
    path.join(REPO, 'kubejs', 'server_scripts', '[server_scripts]shanhai_primordial_forming.js'))

const NEW_TYPE = 'gtceu:primordial_matter_forming'
const NEW_NS = 'shanhai'
const NEW_DIR = 'primordial_forming'

// ── 来源（口径 = 用户原话里的两台机器）────────────────────────────────────────
//    顺序有意义：排在前面的来源在同名冲突时保留"不带后缀"的文件名。
const SOURCES = [
    { dir: 'extruder', zh: '压模器' },
    { dir: 'fluid_solidifier', zh: '流体固化器' },
]
// 🔴 明确【不取】的来源（写死在代码里当闸门，不是靠人记得）
const FORBIDDEN = [{ dir: 'forming_press', zh: '冲压机床' }]

// ── 判据：产出是"模具/模头" ⇒ 剔除（按【物品 id】，不按配方名）──────────────────
//    33 种真名 = gtceu:*_extruder_mold(19) + gtceu:*_casting_mold(14)
const MOLD_OUT = /_mold$/

// ── 电路映射表（33 个格子，0~32；GT 电路上限恰好 32）──────────────────────────
//    编号口径 = 【上一轮已经发布、用户已经在 JEI 里验收过的号，一个都不许变】
//      · 模具族（casting）：v2 的 0..13 原样保留（族内英文 id 字典序）
//      · 模头族（extruder）：v2 当时只剩 cell_extruder_mold 一个在用、占了 14 号
//        ⇒ 把它【钉在 14】，其余 18 个按字典序接在后面（15..32）
//    ⇒ 已发布过的 15 个号（0~14）语义逐号不变；新增的 18 个号全部 ≥15。
//    ⚠️ 若改成"模头族整体按字典序从 14 排起"，14 号会从「模头（单元）」变成「模头（块）」——
//       那是【改掉一个已发布的号】，不做。
const MOLD_TABLE = [
    // 模具（casting，14 种）→ 电路 0..13（= v2 已发布，原样保留）
    'gtceu:anvil_casting_mold', 'gtceu:ball_casting_mold', 'gtceu:block_casting_mold',
    'gtceu:bottle_casting_mold', 'gtceu:credit_casting_mold', 'gtceu:cylinder_casting_mold',
    'gtceu:gear_casting_mold', 'gtceu:ingot_casting_mold', 'gtceu:name_casting_mold',
    'gtceu:nugget_casting_mold', 'gtceu:pill_casting_mold', 'gtceu:plate_casting_mold',
    'gtceu:rotor_casting_mold', 'gtceu:small_gear_casting_mold',
    // 模头（extruder，19 种）→ 电路 14..32（14 = v2 已发布的 cell_extruder_mold，钉住）
    'gtceu:cell_extruder_mold',
    'gtceu:block_extruder_mold', 'gtceu:bolt_extruder_mold', 'gtceu:bottle_extruder_mold',
    'gtceu:foil_extruder_mold', 'gtceu:gear_extruder_mold', 'gtceu:huge_pipe_extruder_mold',
    'gtceu:ingot_extruder_mold', 'gtceu:large_pipe_extruder_mold', 'gtceu:long_rod_extruder_mold',
    'gtceu:normal_pipe_extruder_mold', 'gtceu:plate_extruder_mold', 'gtceu:ring_extruder_mold',
    'gtceu:rod_extruder_mold', 'gtceu:rotor_extruder_mold', 'gtceu:small_gear_extruder_mold',
    'gtceu:small_pipe_extruder_mold', 'gtceu:tiny_pipe_extruder_mold', 'gtceu:wire_extruder_mold',
]
const CIRCUIT_MAX = 32
if (MOLD_TABLE.length > CIRCUIT_MAX + 1) {
    throw new Error('🔴 模具表 ' + MOLD_TABLE.length + ' 种 > 可用电路号 ' + (CIRCUIT_MAX + 1) + ' 个')
}
const MOLD_CIRCUIT = new Map(MOLD_TABLE.map((m, i) => [m, i]))

// ─────────────────────────────────────────────────────────────── 读源
function walk(dir, acc) {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
        const p = path.join(dir, e.name)
        if (e.isDirectory()) walk(p, acc)
        else if (e.name.endsWith('.json')) acc.push(p)
    }
    return acc
}

const allDirs = SOURCES.concat(FORBIDDEN).map(s => s.dir)
const files = walk(RECIPES, [])
const src = []
let parseFail = 0
for (const f of files) {
    const rel = path.relative(RECIPES, f).replace(/\\/g, '/')
    const parts = rel.split('/')
    if (parts.length < 3) continue
    if (allDirs.indexOf(parts[1]) < 0) continue
    let j
    try { j = JSON.parse(fs.readFileSync(f, 'utf8')) } catch (e) { parseFail++; console.log('PARSE FAIL ' + rel + ' : ' + e.message); continue }
    src.push({ ns: parts[0], typeDir: parts[1], id: parts[0] + ':' + parts.slice(2).join('/').replace(/\.json$/, ''), file: rel, raw: j })
}
if (parseFail) throw new Error('🔴 有 ' + parseFail + ' 个源 json 解析失败 —— 拒绝继续（会静默丢配方）')

const byDir = {}
for (const r of src) byDir[r.typeDir] = (byDir[r.typeDir] || 0) + 1

// ── "禁止来源"的条目只读进来【计数】，永远不进 target（见下面的闸门 ①）
const fromForbidden = src.filter(r => FORBIDDEN.some(x => x.dir === r.typeDir)).length

// ── 只取两台目标机器
const target = src.filter(r => SOURCES.some(s => s.dir === r.typeDir))
// 🔴 闸门 ①：发射集合里【一条】都不许来自禁止来源
{
    const leak = target.filter(r => FORBIDDEN.some(x => x.dir === r.typeDir))
    if (leak.length) throw new Error('🔴 禁止来源漏进来了 ' + leak.length + ' 条：' + leak.slice(0, 5).map(x => x.id).join(' , '))
}

// ── 闸门 ②：产出是模具/模头 ⇒ 剔除（判据按物品 id）
function itemOutputs(raw) {
    const out = []
    for (const slot of ((raw.outputs && raw.outputs.item) || [])) {
        const c = slot.content
        if (!c) continue
        if (c.type === 'gtceu:sized' && c.ingredient && c.ingredient.item) out.push({ id: c.ingredient.item, count: c.count })
        else if (c.ingredient && c.ingredient.item) out.push({ id: c.ingredient.item, count: c.count })
    }
    return out
}
const excluded = target.filter(r => itemOutputs(r.raw).some(o => MOLD_OUT.test(o.id)))

if (process.env.SH_PF_NO_MOLD_GATE === '1') {
    console.log('⚠️ SH_PF_NO_MOLD_GATE=1 ⇒ 【故意关闭】"产出是模具就剔除"的闸门（只用于预期失败档）')
}
const kept = process.env.SH_PF_NO_MOLD_GATE === '1'
    ? target.slice()
    : target.filter(r => !excluded.some(x => x.id === r.id && x.ns === r.ns))

// ── 模型：把"不消耗的模具/模头"换成编程电路
function moldInputs(raw) {
    const got = []
    for (const slot of ((raw.inputs && raw.inputs.item) || [])) {
        const c = slot.content
        if (!c || c.type !== 'gtceu:sized') continue
        const ing = c.ingredient
        if (!ing || typeof ing !== 'object' || !ing.item) continue
        if (!MOLD_CIRCUIT.has(ing.item)) continue
        if (slot.chance !== 0) continue          // 只换【不消耗】的；消耗中的空模板是材料，不动
        got.push({ slot, mold: ing.item, circuit: MOLD_CIRCUIT.get(ing.item) })
    }
    return got
}

// ── 组装（含同名冲突处理）
function sanitize(s) { return s.replace(/[^A-Za-z0-9_]/g, '_') }

const prepared = kept.map(r => {
    const json = JSON.parse(JSON.stringify(r.raw))
    json.type = NEW_TYPE
    const molds = []
    for (const m of moldInputs(json)) {
        m.slot.content.ingredient = { type: 'gtceu:circuit', configuration: m.circuit }
        molds.push({ mold: m.mold, circuit: m.circuit })
    }
    return {
        ns: r.ns, srcId: r.id, srcType: r.typeDir, srcFile: r.file,
        base: sanitize(r.ns) + '__' + sanitize(r.id.substring(r.id.indexOf(':') + 1)),
        molds, json,
    }
})

// 同名分组：同组里第一个 SOURCES 序优先的保留裸名，其余加 __<来源> 后缀
const byBase = new Map()
for (const p of prepared) {
    if (!byBase.has(p.base)) byBase.set(p.base, [])
    byBase.get(p.base).push(p)
}
const renamed = []
for (const [base, group] of byBase) {
    if (group.length === 1) { group[0].name = base; continue }
    const rank = d => { const i = SOURCES.findIndex(s => s.dir === d); return i < 0 ? 99 : i }
    group.sort((a, b) => rank(a.srcType) - rank(b.srcType) || a.srcId.localeCompare(b.srcId))
    group.forEach((p, i) => {
        p.name = i === 0 ? base : base + '__' + sanitize(p.srcType)
        renamed.push({ name: p.name, srcType: p.srcType, srcId: p.srcId })
    })
}
const usedNames = new Set()
for (const p of prepared) {
    if (usedNames.has(p.name)) throw new Error('🔴 文件名仍然冲突: ' + p.name)
    usedNames.add(p.name)
}

// ── 发射数据包
fs.rmSync(RES, { recursive: true, force: true })
fs.mkdirSync(RES, { recursive: true })
let totalBytes = 0, maxBytes = 0
const manifest = []
for (const p of prepared) {
    const txt = JSON.stringify(p.json)
    fs.writeFileSync(path.join(RES, p.name + '.json'), txt, 'utf8')
    const b = Buffer.byteLength(txt)
    totalBytes += b; maxBytes = Math.max(maxBytes, b)
    manifest.push({
        newId: NEW_NS + ':' + NEW_DIR + '/' + p.name,
        file: p.name + '.json',
        srcNs: p.ns, srcType: p.srcType, srcId: p.srcId, srcFile: p.srcFile,
        circuit: p.molds.length === 1 ? p.molds[0].circuit : null,
        molds: p.molds.map(m => m.mold).join(','),
        sha256: crypto.createHash('sha256').update(txt, 'utf8').digest('hex'),
        bytes: b,
    })
}

// ── 产物：清单 / 电路表 / 剔除清单
fs.mkdirSync(OUT, { recursive: true })
fs.writeFileSync(path.join(OUT, 'manifest-v3.json'), JSON.stringify({
    newType: NEW_TYPE, generator: 'kubejs\\_generators\\gen_pf_forming.js',
    sources: SOURCES, forbidden: FORBIDDEN, moldTable: MOLD_TABLE,
    counts: { perSource: byDir, excludedMoldOut: excluded.length, emitted: prepared.length },
    renamed,
    rows: manifest,
}, null, 1), 'utf8')

fs.writeFileSync(path.join(OUT, 'manifest-v3.tsv'),
    ['# newId\tsrcType\tsrcId\tsrcFile\tcircuit\tsha256']
        .concat(manifest.map(m => [m.newId, m.srcType, m.srcId, m.srcFile, m.circuit === null ? '-' : m.circuit, m.sha256].join('\t')))
        .join('\n'), 'utf8')

const useCount = {}
for (const p of prepared) for (const m of p.molds) useCount[m.circuit] = (useCount[m.circuit] || 0) + 1
const md = ['| 电路 | 模头/模具（id） | 族 | 被多少条新配方用到 |', '|---|---|---|---|']
MOLD_TABLE.forEach((m, i) => {
    md.push('| ' + i + ' | `' + m + '` | ' + (/_casting_mold$/.test(m) ? '模具(casting)' : '模头(extruder)')
        + ' | ' + (useCount[i] || 0) + ' |')
})
fs.writeFileSync(path.join(OUT, 'mold-circuit-map-v3.md'), md.join('\n'), 'utf8')

fs.writeFileSync(path.join(OUT, 'excluded-molds-v3.json'), JSON.stringify(
    excluded.map(r => ({ srcId: r.id, srcType: r.typeDir, srcFile: r.file, outputs: itemOutputs(r.raw) })), null, 1), 'utf8')

// ── 2026-10-01 新增：用【同一份发射器】把刚发射出去的 json 也写成 KJS ──────────
//    🔴 为什么不再写一段生成代码：另写就会有两个实现，迟早不一致。
//       这里把 prepared[].json 直接喂给共享发射器 —— 与 gen_pf_kjs.js 读 json 转 KJS
//       走的是完全相同的 jsonToRow / emitFile 两条函数。
//    ⚠️ 条数锚点：EMIT.DECLARED 必须等于 Java 侧
//       ShanhaiRecipeTypes.PRIMORDIAL_MATTER_FORMING_DECLARED_RECIPES。两边同时改才允许过。
const ALLOW_DRIFT = process.env.SH_PF_ALLOW_COUNT_DRIFT === '1'
if (ALLOW_DRIFT) {
    console.log('⚠️ SH_PF_ALLOW_COUNT_DRIFT=1 ⇒ 【故意允许】条数偏离 Java 锚点 ' + EMIT.DECLARED
        + '（只用于"拿过期 export 试跑整条链"，正式产出【绝不许】带这个开关）')
}
// 🔴 按 name 排序后再发射：walk() 用的是 fs.readdirSync 的目录顺序，而 gen_pf_kjs.js 是排过序的。
//    两边都排序之后，两条路的 KJS 产物 sha256 会【完全相同】—— 这就是"同源"的可验证证据。
const kjsRows = prepared.slice()
    .sort((a, b) => (a.name < b.name ? -1 : (a.name > b.name ? 1 : 0)))
    .map(p => EMIT.jsonToRow(p.name, p.json))
const kjsText = EMIT.emitFile(kjsRows, ALLOW_DRIFT ? kjsRows.length : EMIT.DECLARED)
fs.mkdirSync(path.dirname(KJS_OUT), { recursive: true })
fs.writeFileSync(KJS_OUT, kjsText, 'utf8')
if (fs.readFileSync(KJS_OUT, 'utf8') !== kjsText) throw new Error('🔴 KJS 回读与写出不一致')

// ── 摘要
const esc = {}
for (const r of excluded) esc[r.typeDir] = (esc[r.typeDir] || 0) + 1
console.log('=== gen_pf_forming v3 ===')
console.log('来源目录条数        = ' + JSON.stringify(byDir))
console.log('禁止来源被读到的条数 = ' + fromForbidden + '  （只计数、不发射；发射集合里必须 0 条）')
console.log('发射集合来自禁止来源 = ' + prepared.filter(p => FORBIDDEN.some(x => x.dir === p.srcType)).length + '  (必须为 0)')
console.log('目标两机器合计      = ' + target.length)
console.log('剔除（产出是模具）  = ' + excluded.length + '   分布=' + JSON.stringify(esc))
console.log('发射配方            = ' + prepared.length)
console.log('电路号用到          = ' + Object.keys(useCount).length + ' 个 / 表内 ' + MOLD_TABLE.length + ' 个')
console.log('同名改名            = ' + JSON.stringify(renamed))
console.log('单文件最大 = ' + maxBytes + ' B ; 合计 = ' + totalBytes + ' B (' + (totalBytes / 1048576).toFixed(2) + ' MB)')
console.log('写出目录 = ' + RES)
console.log('KJS 写出       = ' + KJS_OUT)
console.log('KJS 条数       = ' + kjsRows.length + ' ; 字节 = ' + Buffer.byteLength(kjsText, 'utf8'))
console.log('KJS sha256     = ' + crypto.createHash('sha256').update(kjsText, 'utf8').digest('hex'))
