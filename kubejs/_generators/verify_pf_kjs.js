// ============================================================================
// verify_pf_kjs.js —— 「原初物质定型」迁移的离线自证（不启动游戏、不碰 jar）
//
// 它做四件事：
//   ① 从【产物 KJS 文本】里把 2457 行数据表【重新解析】出来（不复用发射器的行对象）；
//   ② 按【字节码实证的语义】把这些行【反推回配方 json】；
//   ③ 与【数据包里原始的 2457 个 json】逐字段深比对（全量 2457 条，不是抽 5 条）；
//   ④ 跑【负对照】：故意改动若干处，确认比对器真的会报错（证明它不是在自我循环地报 ✅）。
//
// 🔴 自我否定纪律（本仓库 2026-09-20 血账）：检查器自己的假设错了，会给出"假 ✅"。
//    所以 ④ 不是可选项。④ 全过，③ 的 ✅ 才算数。
//
// 用法：node kubejs\_generators\verify_pf_kjs.js
// ============================================================================
'use strict'

const fs = require('fs')
const path = require('path')
const emit = require('./_pf_kjs_emit.js')

const REPO = path.join(__dirname, '..', '..')
function override(name, dflt) {
    const v = process.env[name]
    return (v === undefined || String(v).trim() === '') ? dflt : String(v).trim()
}
// 🔴 三个路径可覆盖 —— 只为"拿另一份数据试跑"验证发射器【不是只对 2457 条过拟合】。
//    不设环境变量时就是正式那三个路径。
const KJS = override('SH_VF_KJS', path.join(REPO, 'kubejs', 'server_scripts', '[server_scripts]shanhai_primordial_forming.js'))
const SRC = override('SH_VF_SRC', path.join(REPO, 'shanhai-rewrite', 'src', 'main', 'resources', 'data', 'shanhai', 'recipes', 'primordial_forming'))
const MANIFEST = override('SH_VF_MANIFEST', path.join(REPO, 'temp', 'pf-fix', 'manifest-v3.json'))
const SMOKE = process.env.SH_PF_ALLOW_COUNT_DRIFT === '1'   // 试跑模式：不拿 2457 当硬判据

let FAIL = 0
function bad(msg) { FAIL++; console.log('🔴 ' + msg) }
function ok(msg) { console.log('✅ ' + msg) }

// ═══════════════════════════════════════════════════════════════════════════
// §1 解析产物 KJS 的数据行（严格：形状不对就报错，绝不"大概解析"）
// ═══════════════════════════════════════════════════════════════════════════
function splitTop(s) {
    const out = []; let depth = 0, cur = '', inStr = false
    for (let i = 0; i < s.length; i++) {
        const ch = s[i]
        if (inStr) { cur += ch; if (ch === "'") inStr = false; continue }
        if (ch === "'") { inStr = true; cur += ch; continue }
        if (ch === '[') { depth++; cur += ch; continue }
        if (ch === ']') { depth--; cur += ch; continue }
        if (ch === ',' && depth === 0) { out.push(cur); cur = ''; continue }
        cur += ch
    }
    if (cur !== '') out.push(cur)
    return out
}
function unq(v) {
    if (v.length < 2 || v[0] !== "'" || v[v.length - 1] !== "'") throw new Error('不是单引号字符串：' + v)
    return v.slice(1, -1)
}
function parseStrArray(v) {
    if (v[0] !== '[' || v[v.length - 1] !== ']') throw new Error('不是数组：' + v)
    const inner = v.slice(1, -1)
    if (inner === '') return []
    return splitTop(inner).map(unq)
}
const ROW_KEYS = ['n', 'nc', 'a', 'c', 'f', 'o', 'd', 'e', 'r']
function parseRow(line, lineno) {
    const s = line.replace(/,$/, '')
    if (s[0] !== '{' || s[s.length - 1] !== '}') throw new Error('第 ' + lineno + ' 行不是对象字面量')
    const row = {}
    for (const p of splitTop(s.slice(1, -1))) {
        const i = p.indexOf(':')
        if (i < 0) throw new Error('第 ' + lineno + ' 行字段缺冒号：' + p)
        const k = p.slice(0, i), v = p.slice(i + 1)
        if (ROW_KEYS.indexOf(k) < 0) throw new Error('第 ' + lineno + ' 行出现未登记字段 ' + k)
        if (v[0] === '[') row[k] = parseStrArray(v)
        else if (v[0] === "'") row[k] = unq(v)
        else {
            if (!/^-?\d+$/.test(v)) throw new Error('第 ' + lineno + ' 行字段 ' + k + ' 不是整数：' + v)
            row[k] = Number(v)
        }
    }
    return row
}

const text = fs.readFileSync(KJS, 'utf8')
const lines = text.split('\n')
const rows = []
for (let i = 0; i < lines.length; i++) if (/^\{n:/.test(lines[i])) rows.push(parseRow(lines[i], i + 1))
if (!SMOKE) {
    if (rows.length !== emit.DECLARED) bad('产物数据行数 ' + rows.length + ' ≠ ' + emit.DECLARED)
    else ok('产物数据行数 = ' + rows.length)
} else {
    ok('试跑模式：产物数据行数 = ' + rows.length + '（不拿 ' + emit.DECLARED + ' 当判据）')
}

const rowByName = new Map()
for (const r of rows) {
    if (rowByName.has(r.n)) bad('产物里配方名重复：' + r.n)
    rowByName.set(r.n, r)
}

// ═══════════════════════════════════════════════════════════════════════════
// §2 行 → 配方 json（按字节码实证的语义反推）
//    每个 slot 的三个 chance 字段值都有出处：
//      · itemInputs / inputFluids / itemOutputs → chance=10000（GTRecipeJS 构造器默认 = getMaxChancedValue()）
//      · notConsumable                          → chance=0（GTRecipeJS.notConsumable 字节码：暂存 chance、置 0、inputItems、还原）
//      · circuit(n)                             → chance=0（circuit() 字节码 = notConsumable(InputItem.of(IntCircuitIngredient.circuitInput(n),1))）
//      · maxChance 恒 10000、tierChanceBoost 恒 0（构造器默认，且全量 json 现算确认只有这两个值）
// ═══════════════════════════════════════════════════════════════════════════
function itemSlotFromTok(tok, chance) {
    const m = /^(\d+)x (#?)(.+)$/.exec(tok)
    if (!m) throw new Error('物品槽记法不认识：' + tok)
    const count = Number(m[1])
    const ing = m[2] === '#' ? { tag: m[3] } : { item: m[3] }
    return { content: { type: 'gtceu:sized', count: count, ingredient: ing }, chance: chance, maxChance: 10000, tierChanceBoost: 0 }
}
function fluidSlotFromTok(tok) {
    const sp = tok.indexOf(' ')
    if (sp < 0) throw new Error('流体槽记法不认识：' + tok)
    const head = tok.slice(0, sp), amount = Number(tok.slice(sp + 1))
    const ing = head[0] === '#' ? { tag: head.slice(1) } : { fluid: head }
    return { content: { amount: amount, value: [ing] }, chance: 10000, maxChance: 10000, tierChanceBoost: 0 }
}
function reconstruct(r) {
    const itemIn = []
    if (r.nc) for (const t of r.nc) itemIn.push(itemSlotFromTok(t, 0))
    if (r.a) for (const t of r.a) itemIn.push(itemSlotFromTok(t, 10000))
    if (r.c !== undefined) itemIn.push({
        content: { type: 'gtceu:sized', count: 1, ingredient: { type: 'gtceu:circuit', configuration: r.c } },
        chance: 0, maxChance: 10000, tierChanceBoost: 0
    })
    const inputs = {}
    if (itemIn.length) inputs.item = itemIn
    if (r.f) inputs.fluid = r.f.map(fluidSlotFromTok)
    const j = {
        type: emit.TYPE,
        duration: r.d,
        data: { euTier: emit.getTierByVoltage(r.e) },
        inputs: inputs,
        tickInputs: { eu: [{ content: r.e, chance: 10000, maxChance: 10000, tierChanceBoost: 0 }] },
        outputs: { item: r.o.map(t => itemSlotFromTok(t, 10000)) },
        tickOutputs: {},
        inputChanceLogics: {}, outputChanceLogics: {},
        tickInputChanceLogics: {}, tickOutputChanceLogics: {}
    }
    if (r.r) j.recipeConditions = [{ type: 'cleanroom', data: { cleanroom: 'cleanroom' } }]
    return j
}

// ═══════════════════════════════════════════════════════════════════════════
// §3 比对：先归一化（丢掉空对象/空数组 —— 数据包里有的文件带 tickOutputs:{} 有的不带，
//    语义上都是"空"，不归一化会造出假不一致），再逐字段深比对并给出路径
// ═══════════════════════════════════════════════════════════════════════════
function isEmptyContainer(v) {
    if (Array.isArray(v)) return v.length === 0
    if (v && typeof v === 'object') return Object.keys(v).length === 0
    return false
}
function normalize(v) {
    if (Array.isArray(v)) return v.map(normalize)
    if (v && typeof v === 'object') {
        const o = {}
        for (const k of Object.keys(v)) {
            if (isEmptyContainer(v[k])) continue
            o[k] = normalize(v[k])
        }
        return o
    }
    return v
}
function diff(a, b, p, out) {
    out = out || []
    p = p || ''
    if (out.length > 12) return out
    const ta = a === null ? 'null' : Array.isArray(a) ? 'array' : typeof a
    const tb = b === null ? 'null' : Array.isArray(b) ? 'array' : typeof b
    if (ta !== tb) { out.push(p + ' 类型不同：' + ta + ' vs ' + tb); return out }
    if (ta === 'array') {
        if (a.length !== b.length) { out.push(p + ' 长度不同：' + a.length + ' vs ' + b.length); return out }
        for (let i = 0; i < a.length; i++) diff(a[i], b[i], p + '[' + i + ']', out)
        return out
    }
    if (ta === 'object') {
        const ka = Object.keys(a).sort(), kb = Object.keys(b).sort()
        if (ka.join(',') !== kb.join(',')) { out.push(p + ' 键集不同：[' + ka.join(',') + '] vs [' + kb.join(',') + ']'); return out }
        for (const k of ka) diff(a[k], b[k], p + '.' + k, out)
        return out
    }
    if (a !== b) out.push(p + ' 值不同：' + JSON.stringify(a) + ' vs ' + JSON.stringify(b))
    return out
}

// ═══════════════════════════════════════════════════════════════════════════
// §4 全量比对 2457 条
// ═══════════════════════════════════════════════════════════════════════════
const srcNames = fs.readdirSync(SRC).filter(n => n.endsWith('.json')).sort()
const manifest = JSON.parse(fs.readFileSync(MANIFEST, 'utf8'))
const srcTypeByName = new Map()
for (const r of manifest.rows) srcTypeByName.set(r.file.replace(/\.json$/, ''), r.srcType)

const mismatched = []
const srcByName = new Map()
for (const n of srcNames) {
    const name = n.replace(/\.json$/, '')
    const raw = JSON.parse(fs.readFileSync(path.join(SRC, n), 'utf8'))
    srcByName.set(name, raw)
    if (!rowByName.has(name)) { mismatched.push(name + '  : 产物里没有这一条'); continue }
    const got = normalize(reconstruct(rowByName.get(name)))
    const want = normalize(raw)
    const d = diff(want, got, '', [])
    if (d.length) mismatched.push(name + '  :\n      ' + d.join('\n      '))
}
const extra = [...rowByName.keys()].filter(n => !srcByName.has(n))
if (extra.length) mismatched.push('产物多出 ' + extra.length + ' 条：' + JSON.stringify(extra.slice(0, 5)))

console.log('')
console.log('=== 全量逐字段比对（' + srcNames.length + ' 条 json ↔ 产物反推）===')
if (mismatched.length === 0) ok('2457/2457 逐字段一致（0 处差异）')
else { bad('不一致 ' + mismatched.length + ' 条'); mismatched.slice(0, 10).forEach(m => console.log('   · ' + m)) }

// 反面：源里有没有、产物里也有没有 —— 双向计数
console.log('源 json 条数 = ' + srcNames.length + ' ; 产物数据行数 = ' + rows.length + ' ; 差 = ' + (srcNames.length - rows.length))

// ═══════════════════════════════════════════════════════════════════════════
// §5 两侧独立计数（不经过反推，直接数文本里的记号）—— 与源侧现算数字对账
// ═══════════════════════════════════════════════════════════════════════════
let nNc = 0, nIn = 0, nCircuit = 0, nFluid = 0, nOut = 0, nClean = 0
for (const r of rows) {
    if (r.nc) nNc += r.nc.length
    if (r.a) nIn += r.a.length
    if (r.c !== undefined) nCircuit++
    if (r.f) nFluid += r.f.length
    nOut += r.o.length
    if (r.r) nClean++
}
// 源侧独立现算
let sNc = 0, sIn = 0, sCircuit = 0, sFluid = 0, sOut = 0, sClean = 0
for (const raw of srcByName.values()) {
    for (const s of (raw.inputs.item || [])) {
        const ing = s.content.ingredient
        if (ing.type === 'gtceu:circuit') sCircuit++
        else if (s.chance === 0) sNc++
        else sIn++
    }
    sFluid += (raw.inputs.fluid || []).length
    sOut += (raw.outputs.item || []).length
    if (raw.recipeConditions) sClean++
}
const pairs = [['不消耗(notConsumable)', nNc, sNc], ['普通物品输入', nIn, sIn], ['编程电路', nCircuit, sCircuit],
    ['流体输入', nFluid, sFluid], ['物品输出', nOut, sOut], ['洁净室条件', nClean, sClean]]
console.log('')
console.log('=== 槽位计数（产物文本 vs 源 json 现算）===')
for (const [k, a, b] of pairs) {
    if (a === b) ok(k + ' = ' + a)
    else bad(k + ' 产物 ' + a + ' ≠ 源 ' + b)
}

// 来源分布：extruder 1344 / fluid_solidifier 1113（只在 manifest 覆盖本批时才断言）
const covered = rows.every(r => srcTypeByName.has(r.n))
const byType = {}
for (const r of rows) { const t = srcTypeByName.get(r.n) || '未收录'; byType[t] = (byType[t] || 0) + 1 }
console.log('来源分布（据 temp\\pf-fix\\manifest-v3.json）= ' + JSON.stringify(byType))
if (!covered || SMOKE) {
    console.log('   （试跑模式 / manifest 未覆盖本批 ⇒ 跳过 1344＋1113 的断言）')
} else if (byType.extruder === 1344 && byType.fluid_solidifier === 1113) {
    ok('压模器 1344 ＋ 流体固化器 1113 = 2457')
} else bad('来源分布不是 1344/1113')

// ═══════════════════════════════════════════════════════════════════════════
// §6 负对照：故意改坏，必须【每一条都被抓到】，否则上面那些 ✅ 不算数
// ═══════════════════════════════════════════════════════════════════════════
console.log('')
console.log('=== 负对照（改坏后必须报差异）===')
const NEG = []
// ⚠️ 负对照必须挑【确实有那个字段】的行，否则改动是空操作 ⇒ 报"没抓到"是假警报。
//    2026-10-01 第一次跑就踩了：F 挑的是 rows[100]（没有 nc 字段）⇒ 改动无效。
function neg(label, mutate) {
    const name = rows[100].n
    const r0 = JSON.parse(JSON.stringify(rowByName.get(name)))
    let detected = false
    try {
        const r2 = mutate(JSON.parse(JSON.stringify(r0)))
        const d = diff(normalize(srcByName.get(name)), normalize(reconstruct(r2)), '', [])
        detected = d.length > 0
    } catch (e) { detected = true }
    NEG.push([label, detected])
}
neg('A 物品数量 1x→2x', r => { r.a[0] = r.a[0].replace(/^(\d+)x/, '2x'); return r })
neg('B 物品 id 末位改字', r => { r.a[0] = r.a[0] + 'X'; return r })
neg('C 电路号 +1', r => { r.c = (r.c === undefined ? 1 : r.c) + 1; return r })
neg('D EU 改值', r => { r.e = r.e + 1; return r })
neg('E 时长改值', r => { r.d = r.d + 1; return r })
neg('G 删掉一条输出', r => { r.o = []; return r })
neg('H 加一条不存在的输入', r => { r.a = (r.a || []).concat(['1x minecraft:stone']); return r })
// 带流体 & 带洁净室 & 带 tag 的样本单独做负对照
// ⚠️ 试跑模式（另一份数据）可能没有这些类的行 ⇒ 那时候【跳过】而不是拿 undefined 去改
const fName = rows.find(r => r.f && r.f.length && r.f[0][0] !== '#')
const tName = rows.find(r => r.f && r.f.length && r.f[0][0] === '#')
const cName = rows.find(r => r.r)
function neg2(label, rowObj, mutate) {
    if (!rowObj) { NEG.push([label + '（本批没有这一类的行，跳过）', null]); return }
    let detected = false
    try {
        const r2 = mutate(JSON.parse(JSON.stringify(rowObj)))
        detected = diff(normalize(srcByName.get(rowObj.n)), normalize(reconstruct(r2)), '', []).length > 0
    } catch (e) { detected = true }
    NEG.push([label, detected])
}
neg2('F 不消耗→普通输入', rows.find(r => r.nc), r => { if (r.nc) { r.a = (r.a || []).concat(r.nc); delete r.nc }; return r })
neg2('I 流体数量改值', fName, r => { const s = r.f[0], sp = s.lastIndexOf(' '); r.f[0] = s.slice(0, sp) + ' ' + (Number(s.slice(sp + 1)) + 1); return r })
neg2('J 标签流体 → 具体流体', tName, r => { r.f[0] = r.f[0].replace('#', '').replace(/\//g, '_'); return r })
neg2('K 去掉洁净室标记', cName, r => { delete r.r; return r })

let negBad = 0, negSkip = 0
for (const [label, det] of NEG) {
    if (det === null) { negSkip++; console.log('   ⏭  跳过 ' + label); continue }
    if (det) console.log('   ✅ 抓到 ' + label)
    else { negBad++; console.log('   🔴 没抓到 ' + label + '  ⇒ 比对器是空的，上面的 ✅ 不算数') }
}
if (negBad === 0) ok('负对照 ' + (NEG.length - negSkip) + '/' + (NEG.length - negSkip) + ' 全部被抓到' + (negSkip ? '（另有 ' + negSkip + ' 条按条件跳过）' : ''))
else bad('负对照有 ' + negBad + ' 条没抓到')

// ═══════════════════════════════════════════════════════════════════════════
// §7 抽 5 条（extruder 3 ＋ fluid_solidifier 2，含带流体的）打印逐字段原文
// ═══════════════════════════════════════════════════════════════════════════
console.log('')
console.log('=== 抽样逐字段（extruder 3 ＋ fluid_solidifier 2）===')
const sam = []
if (covered && !SMOKE) {
    for (const r of rows) {
        const t = srcTypeByName.get(r.n)
        if (t === 'extruder' && sam.filter(x => x.t === 'extruder').length < 3) sam.push({ t, r })
        if (t === 'fluid_solidifier' && sam.filter(x => x.t === 'fluid_solidifier').length < 2) sam.push({ t, r })
        if (sam.length === 5) break
    }
} else {
    // 试跑模式：按特征挑 5 条，保证覆盖 电路/物品/标签物品/标签流体/不消耗
    const want = [
        ['带电路', r => r.c !== undefined],
        ['带电路号 0', r => r.c === 0],
        ['带标签物品输入', r => r.a && r.a.some(x => x.indexOf('#') > 0)],
        ['带标签流体', r => r.f && r.f[0] && r.f[0][0] === '#'],
        ['带不消耗', r => r.nc]
    ]
    for (const [t, pred] of want) { const r = rows.find(pred); if (r) sam.push({ t: t, r: r }) }
}
for (const s of sam) {
    console.log('')
    console.log('--- [' + s.t + '] ' + s.r.n + ' ---')
    console.log('  KJS 行 : ' + lines.find(l => l.indexOf("{n:'" + s.r.n + "'") === 0).trim())
    console.log('  反推 json : ' + JSON.stringify(normalize(reconstruct(s.r))))
    console.log('  原始 json : ' + JSON.stringify(normalize(srcByName.get(s.r.n))))
    console.log('  比对 : ' + (diff(normalize(srcByName.get(s.r.n)), normalize(reconstruct(s.r)), '', []).length === 0 ? '字段逐一相同' : '不一致'))
}
// 带标签流体的那一条也打出来（最容易写错的一类）
if (tName) {
    console.log('')
    console.log('--- [带标签流体] ' + tName.n + ' ---')
    console.log('  KJS 行 : ' + lines.find(l => l.indexOf("{n:'" + tName.n + "'") === 0).trim())
    console.log('  同比对 : ' + (diff(normalize(srcByName.get(tName.n)), normalize(reconstruct(tName)), '', []).length === 0 ? '字段逐一相同' : '不一致'))
}

// ═══════════════════════════════════════════════════════════════════════════
console.log('')
console.log(FAIL === 0 ? '=== 结论：全部通过（失败项 0）===' : '=== 结论：失败项 ' + FAIL + ' 条 ===')
process.exitCode = FAIL === 0 ? 0 : 1
