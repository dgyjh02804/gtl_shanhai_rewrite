// id existence check for every item/fluid id appearing in the 38 patterns.
// Positive sources only (per project rule): never claim "missing" from one stale table.
//   ① local\kubejs\export\registries\item.json / fluid.json   (game-emitted snapshot, 2026-09-10)
//   ② mods\shanhai-0.1.0.jar!assets/shanhai/lang/zh_cn.json   (deployed jar, 2026-09-26)
//   ③ mods\gtceu-1.20.1-1.4.4.jar!assets/gtceu/lang/zh_cn.json
'use strict'
var fs = require('fs')
var BASE = 'C:\\Users\\david\\Desktop\\构建\\shanhai重构\\recipe-convert\\'
var V = 'C:\\Users\\david\\Desktop\\65866652\\日常\\versions\\GTL山海9.10test\\'
var rows = JSON.parse(fs.readFileSync(BASE + 'rows.json', 'utf8'))
var items = JSON.parse(fs.readFileSync(V + 'local\\kubejs\\export\\registries\\item.json', 'utf8'))
var fluids = JSON.parse(fs.readFileSync(V + 'local\\kubejs\\export\\registries\\fluid.json', 'utf8'))
var shLang = JSON.parse(fs.readFileSync(BASE + 'lang\\shanhai_zh_cn.json', 'utf8'))

var idSet = { item: {}, fluid: {} }
function add(kind, id) { if (id && id !== 'minecraft:paper') { idSet[kind][id] = (idSet[kind][id] || 0) + 1 } }
for (var i = 0; i < rows.length; i++) {
    var R = rows[i]
    for (var a = 0; a < R.real.length; a++) add('item', R.real[a].d.id)
    for (var b = 0; b < R.fluids.length; b++) add('fluid', R.fluids[b].d.id)
    for (var c = 0; c < R.circuits.length; c++) { }
    for (var d = 0; d < R.outs.length; d++) add(R.outs[d].d.kind === 'FLUID' ? 'fluid' : 'item', R.outs[d].d.id)
}
var paperOut = 0
for (var i2 = 0; i2 < rows.length; i2++) {
    for (var d2 = 0; d2 < rows[i2].outs.length; d2++) if (rows[i2].outs[d2].d.id === 'minecraft:paper') paperOut++
}
add('item', 'minecraft:paper')   // #25 真产物（出纸）也要算

// shanhai jar lang -> id sets
var shItems = {}, shFluids = {}, shBlocks = {}
for (var k in shLang) {
    if (typeof shLang[k] !== 'string') continue
    var m1 = /^item\.shanhai\.([a-z0-9_]+)$/.exec(k)
    var m2 = /^fluid\.shanhai\.([a-z0-9_]+)$/.exec(k)
    var m3 = /^block\.shanhai\.([a-z0-9_]+)$/.exec(k)
    if (m1) shItems['shanhai:' + m1[1]] = 1
    if (m2) shFluids['shanhai:' + m2[1]] = 1
    if (m3) shBlocks['shanhai:' + m3[1]] = 1
}

var L = []
function log(s) { L.push(s) }
log('# id 存在性核对（正面对照，只报"有证据"的）')
log('')
log('三个正面来源（按 namespace 分派）：')
log('')
log('| 来源 | 覆盖的 namespace | 时间戳 |')
log('|---|---|---|')
log('| `local\\kubejs\\export\\registries\\item.json` | minecraft / gtceu / gtlcore / kubejs / ae2 / thetornproductionline … | 2026-09-10 19:19 |')
log('| `local\\kubejs\\export\\registries\\fluid.json` | 同上（流体） | 2026-09-10 19:19 |')
log('| `mods\\shanhai-0.1.0.jar!assets/shanhai/lang/zh_cn.json` | **shanhai**（导出表早于该 jar，只能用它） | 2026-09-26 11:11 |')
log('')
log('## 🔴 检查器自证（正面对照 —— 先证明它会报警，再信它的结论）')
log('')
log('| 对照 | id | 期望 | 实测 | 结果 |')
log('|---|---|---|---|---|')
var ctrl = [
    ['正', 'minecraft:iron_ingot', 'PRESENT'], ['正', 'gtceu:tin_ingot', 'PRESENT'],
    ['正', 'gtceu:programmed_circuit', 'PRESENT'], ['正', 'gtceu:primitive_void_ore', 'PRESENT'],
    ['正', 'gtlcore:mining_crystal', 'PRESENT'], ['正', 'kubejs:reactor_core', 'PRESENT'],
    ['正', 'thetornproductionline:celestial_secret_deducing_module_ulv', 'PRESENT'],
    ['正', 'ae2:molecular_assembler', 'PRESENT'], ['正', 'gtceu:steam', 'PRESENT'],
    ['正', 'gtceu:glue', 'PRESENT'], ['正', 'shanhai:photon', 'PRESENT'],
    ['负', 'gtceu:iron_ingot', 'ABSENT'], ['负', 'gtceu:this_is_fake', 'ABSENT'],
    ['负', 'shanhai:not_a_module', 'ABSENT'], ['负', 'gtceu:fake_fluid', 'ABSENT']
]
var ctrlFail = 0
for (var ci = 0; ci < ctrl.length; ci++) {
    var kind = (ctrl[ci][1] === 'gtceu:steam' || ctrl[ci][1] === 'gtceu:glue' || ctrl[ci][1] === 'gtceu:fake_fluid') ? 'fluid' : 'item'
    var got = check(kind, ctrl[ci][1]) ? 'PRESENT' : 'ABSENT'
    var okc = (got === ctrl[ci][2])
    if (!okc) ctrlFail++
    log('| ' + ctrl[ci][0] + ' | `' + ctrl[ci][1] + '` | ' + ctrl[ci][2] + ' | ' + got + ' | ' + (okc ? '✅' : '❌') + ' |')
}
log('')
log('⇒ 正面对照失败数 = **' + ctrlFail + '** ' + (ctrlFail === 0 ? '（扫描器自己是对的，它的 ❌ 才可信）' : '（**扫描器自己有问题，下面的结论不可信！**）'))
log('')
log('> ⚠️ 途中踩过一次"检查器自己的假设错了"：我把 `gtceu:iron_ingot` 当成正面样本，但它**真的不存在**')
log('> （GTCEu 的铁锭就是原版 `minecraft:iron_ingot`）⇒ 当场报"absent"。**是控制组写错了，不是扫描器坏了。**')
log('> 记在这里，因为这类假失败正是本项目一天的 7 次事故源头。')
log('')

var present = [], absent = []
function check(kind, id) {
    var ns = id.split(':')[0]
    if (ns === 'shanhai') {
        var name = id.split(':')[1]
        if (kind === 'item') return !!(shItems[id] || shBlocks[id])
        return !!shFluids[id]
    }
    return kind === 'item' ? !!items[id] : !!fluids[id]
}
var ids = []
for (var x in idSet.item) ids.push({ kind: 'item', id: x, n: idSet.item[x] })
for (var y in idSet.fluid) ids.push({ kind: 'fluid', id: y, n: idSet.fluid[y] })
ids.sort(function (p, q) { return (p.kind === q.kind) ? (p.id < q.id ? -1 : 1) : (p.kind < q.kind ? -1 : 1) })
for (var z = 0; z < ids.length; z++) {
    var ok = check(ids[z].kind, ids[z].id)
    if (ok) present.push(ids[z]); else absent.push(ids[z])
}
log('## 结论')
log('')
log('- 出现在 38 条样板里的 **id 总数 = ' + ids.length + '**')
log('- ✅ **在正面来源里找到 = ' + present.length + '**')
log('- ❌ **在正面来源里找不到 = ' + absent.length + '**')
log('')
if (absent.length) {
    log('### ❌ 找不到的（逐条，别当"不存在"下结论）')
    log('')
    log('| id | 种类 | 出现次数 | 说明 |')
    log('|---|---|---|---|')
    for (var q = 0; q < absent.length; q++) {
        var ns = absent[q].id.split(':')[0]
        var why = (ns === 'shanhai') ? 'shanhai jar 的 lang 里没有对应 `item./block./fluid.shanhai.*` 键' : '导出表（2026-09-10）里没有，可能是 shanhai 之外的新条目或拼写错误'
        log('| `' + absent[q].id + '` | ' + (absent[q].kind === 'item' ? '物品' : '流体') + ' | ' + absent[q].n + ' | ' + why + ' |')
    }
    log('')
}
log('### ✅ 找到的（全部逐条）')
log('')
log('| id | 种类 | 出现次数 |')
log('|---|---|---|')
for (var p = 0; p < present.length; p++) log('| `' + present[p].id + '` | ' + (present[p].kind === 'item' ? '物品' : '流体') + ' | ' + present[p].n + ' |')
log('')
fs.writeFileSync(BASE + 'id_check.md', L.join('\r\n'), 'utf8')
console.log('ids=' + ids.length + ' present=' + present.length + ' absent=' + absent.length)
for (var q2 = 0; q2 < absent.length; q2++) console.log('  ABSENT: ' + absent[q2].kind + ' ' + absent[q2].id)
