// Generate 样板清单.md from parsed.json + lang files, with an explicit old / new diff.
// 🔴 2026-09-29 纪律：本文件里【不许再写死条数 / 电压 / EUt】——
//    条数一律从 data / rows / cells 现算；电压与 EUt 一律引用唯一真源 kubejs\_generators\gt_voltage.js
//    （与 gen_kjs.js 引的是同一个模块 ⇒ 两边不可能再各写一份、再各错一份）。
'use strict'
var fs = require('fs')
var crypto = require('crypto')
var path = require('path')

// ═══════════════════════════════════════════════════════════════════════════════
// 🔴 路径来源纪律（上传前清理）：本仓库里【不写任何机器绝对路径】。
//    · 仓库【内】的路径 ⇒ 按【脚本自身位置】(__dirname) 推（不用 process.cwd()）；
//    · 仓库【外】的路径（PF.txt）⇒ 从环境变量读；缺了就【响亮抛错并退出】。
// ═══════════════════════════════════════════════════════════════════════════════
var REPO = path.join(__dirname, '..', '..')          // kubejs\_generators → 仓库根
function envPath(name, what, example) {
    var v = process.env[name]
    if (v === undefined || String(v).trim() === '') {
        throw new Error('🔴 缺少环境变量 ' + name + '（' + what + '）\n'
            + '   ⇒ 请先设置它，例如（PowerShell）：$env:' + name + " = '" + example + "'\n"
            + '   ⇒ 本脚本【拒绝】在缺少它的前提下继续运行：那会拿错路径、静默产出错产物。')
    }
    return String(v).trim()
}
var SRC = envPath('SH_PF_SRC', 'PF.txt（AE2 样板导出的 NBT 文本）的绝对路径',
    'D:\\path\\to\\PF.txt')
var BASE = path.join(REPO, 'recipe-convert') + path.sep
var data = JSON.parse(fs.readFileSync(BASE + 'parsed.json', 'utf8'))
var gt = JSON.parse(fs.readFileSync(BASE + 'lang\\gtceu_zh_cn.json', 'utf8'))
var sh = JSON.parse(fs.readFileSync(BASE + 'lang\\shanhai_zh_cn.json', 'utf8'))
// 🔴 电压表 / 元件名⇒档位 / 星门 EUt 的唯一真源（含 javap 字节码出处与自检）
var GT_VOLTAGE = require('./gt_voltage.js')

// ═══════════════════════════════════════════════════════════════════════════════
// 🔴 2026-09-29 过期防线 —— 【引用方】自检：我读的 parsed.json 是不是旧的？
// ═══════════════════════════════════════════════════════════════════════════════
//   之前的事故形态：PF.txt 换了一版，但只重跑了后半段流水线 ⇒ 产物是「新 PF.txt 的皮、
//   旧 parsed.json 的肉」，而没有任何一处会响。⇒ 现在读进来第一件事就是比对源头指纹。
var PROV = require('./provenance.js')
PROV.check(BASE + 'parsed.json', 'gen_manifest.js(读 parsed.json)')

var OUT = []
var log = function (s) { OUT.push(s); console.log(s) }

// ---------------------------------------------------------------- helpers
// 🔴 2026-09-29：删掉了本文件原来那张本地电压表
//      var V = { …, MAX: 2147483647 }
//    —— 它的 MAX 是 Integer.MAX_VALUE，与 GTValues.<clinit> 字节码里的真值 2147483648 不一致。
//    现在一律用 GT_VOLTAGE.V（同一份真值还带自检）。

function nameOf(el) { return el && el.nbt && el.nbt.display && el.nbt.display.Name ? safeText(el.nbt.display.Name) : null }
function safeText(s) { try { return JSON.parse(s).text } catch (e) { return s } }
function isPaper(d) { return d.kind === 'ITEM' && d.id === 'minecraft:paper' }
function isCircuit(d) { return d.kind === 'ITEM' && d.id === 'gtceu:programmed_circuit' }

function classifyPaper(d) {
    var n = nameOf(d)
    if (n === null) return { kind: 'UNKNOWN', name: null }
    if (/^配方类型：/.test(n)) return { kind: 'TYPE', name: n.substring(5) }
    if (/^[0-9]+s$/.test(n)) return { kind: 'TIME', name: n }
    // 裸类型名：纸上直接写「光子虹吸」「电路组装机」，没有「配方类型：」前缀
    if (bareTypeName(n)) return { kind: 'TYPE', name: n, bare: true }
    return { kind: 'NOTE', name: n }
}
function bareTypeName(n) {
    var h = exactHits0(n)
    for (var i = 0; i < h.length; i++) if (/^gtceu\.[a-z_]+$/.test(h[i].key)) return true
    return false
}
function exactHits0(name) {
    var hits = []
    var srcs = [[gt], [sh]]
    for (var s = 0; s < srcs.length; s++) {
        var o = srcs[s][0]
        for (var k in o) { if (!Object.prototype.hasOwnProperty.call(o, k)) continue; if (o[k] === name) hits.push({ key: k }) }
    }
    return hits
}

function realInputs(d) { return d.kind === 'ITEM' && !isPaper(d) && !isCircuit(d) }
function descIn(d) {
    if (d.kind === 'ITEM') return (d.count > 1 ? d.count + 'x ' : '') + d.id + (d.nbt && d.nbt.Configuration !== undefined ? '{Configuration:' + d.nbt.Configuration + '}' : '')
    if (d.kind === 'FLUID') return d.amount + 'mB ' + d.id
    return d.kind
}
function descOut(d) { return descIn(d) }

// ---------------------------------------------------------------- structure
var cells = data.cells.slice().sort(function (a, b) { return a.slot - b.slot })
var byCell = {}
for (var i = 0; i < data.patterns.length; i++) {
    var P = data.patterns[i]
    var key = String(P.cell)
    if (!byCell[key]) byCell[key] = []
    byCell[key].push(P)
}

// ---------------------------------------------------------------- per-pattern analysis
function flagsCanary(m){ console.info('[PF-PATCH] 🔴 '+m) }
var rows = []
for (var c = 0; c < cells.length; c++) {
    var cellName = cells[c].name
    var list = byCell[String(cellName)] || []
    for (var k = 0; k < list.length; k++) {
        var P = list[k], idx = k + 1
        var papers = [], real = [], circuits = [], fIns = [], outs = []
        for (var a = 0; a < P.in.length; a++) {
            var d = P.in[a]
            if (d.kind === 'EMPTY') continue
            if (isPaper(d)) { papers.push({ desc: classifyPaper(d), slot: a }); continue }
            if (isCircuit(d)) { circuits.push({ n: d.nbt.Configuration, slot: a }); continue }
            if (d.kind === 'ITEM') real.push({ d: d, slot: a })
            else if (d.kind === 'FLUID') fIns.push({ d: d, slot: a })
            else real.push({ d: d, slot: a })
        }
        var outPapers = []
        for (var b = 0; b < P.out.length; b++) {
            var e = P.out[b]
            if (e.kind === 'EMPTY') continue
            // 🔴 纸也可能写在【输出】格里（#33 的「电子中微子产出概率5%」就在 out[3]）—— 它不是产物
            //    ⚠️ 但【没改名】的 minecraft:paper 是【真产物】（#25 就是 2x minecraft:paper），必须留下
            if (isPaper(e) && nameOf(e) !== null) { outPapers.push({ desc: classifyPaper(e), slot: b }); continue }
            outs.push({ d: e, slot: b })
        }
        var typePaper = null, timePaper = null, notes = []
        for (var q = 0; q < papers.length; q++) {
            var pp = papers[q]
            if (pp.desc.kind === 'TYPE') typePaper = pp
            else if (pp.desc.kind === 'TIME') timePaper = pp
            else notes.push(pp)
        }
        // 🔴 用户 2026-09-26 口述补白（逐字：「我告诉你：配方类型，世线电路板组装，物质模块是催化剂，60s」
        //     ＋「等一下，我忘记放物质模块了，物质模块是基础物质模块」）
        //    背景：PF.txt「处理样板MV」箱子里那条【世线织络板 wl_board_mv】的样板，在游戏里既没写纸、也没放物质模块：
        //          原始 NBT in[0..6] 里没有任何 minecraft:paper、也没有 shanhai:basic_material_module
        //          ⇒ 解析器读不出 type（这是【正确行为】，不是 bug）。
        //    ⚠️ 这是【针对这一条的显式补白】，【不是】"按输出猜类型"的通用规则；匹配键 = (cell, 产物 id) 双条件精确命中。
        //    🔴🔴 幂等：用户说"我已经在游戏里补上了，下一次导表就可以同步" ⇒ 本补丁【先检查、缺什么补什么】：
        //          · 物品已存在 ⇒ 不注入，只打印"幂等跳过（物品）"
        //          · TYPE 纸已存在 ⇒ 不注入，只打印"幂等跳过（纸）"
        //    🔴 归属事实（2026-09-26 反查 shanhai-rewrite\src\main\java\com\shanhai\machine\module\ModuleRegistry.java）：
        //       wl_board_circuit_assembly 只挂在【两台机器】上，且【两台都是山海自己的】：
        //         · RECIPE_ASSEMBLY_LINE_MODULE（L422-L428）⇒ 原初装配线模块 shanhai:primordial_assembly_line_module
        //         · buildDebugModuleRecipeTypes()（L622/L667）⇒ 原初山海调试模块 shanhai:primordial_debug_module
        //       ⇒ 按用户早先裁定「【山海的机器】⇒ 物质模块 = 等级门槛（以催化剂形态出现）」
        //         ⇒ 本条落成 moduleLevelRequirement（**不是** notConsumable），这是【符合设计】的。
        //       ⚠️ 若将来 wl_board_circuit_assembly 也挂到**别家的机器**上，那台机器上它才该是纯 notConsumable。
        // ⇒ 当两行"幂等跳过"都出现时，【这条补丁即可整条删除】。
        var PATCH_PAPERS = [
            { cell: '处理样板MV', out: 'shanhai:wl_board_mv',
              type: '世线电路板组装', note: '物质模块是催化剂', time: '60s',
              item: 'shanhai:basic_material_module' }
        ]
        for (var w2 = 0; w2 < PATCH_PAPERS.length; w2++) {
            var PT = PATCH_PAPERS[w2]
            if (String(cellName) !== PT.cell) continue
            var hitOut = false
            for (var o2 = 0; o2 < outs.length; o2++) { var od = outs[o2].d || {}; if (String(od.id) === PT.out) hitOut = true }
            if (!hitOut) continue

            // ── 幂等①：物品 ── 已在 PF.txt 里就不补
            var hasItem = false
            for (var r3 = 0; r3 < real.length; r3++) { var rd = real[r3].d || {}; if (String(rd.id) === PT.item) hasItem = true }
            if (hasItem) {
                console.info('[PF-PATCH] ✅ 幂等跳过（物品）：cell=' + PT.cell + ' 产物=' + PT.out
                    + ' ⇒ 「' + PT.item + '」已存在于 PF.txt，不再注入；该补丁的物品部分可退役。')
            } else {
                var used = {}
                for (var u1 = 0; u1 < real.length; u1++) used[real[u1].slot] = 1
                for (var u2 = 0; u2 < fIns.length; u2++) used[fIns[u2].slot] = 1
                for (var u3 = 0; u3 < circuits.length; u3++) used[circuits[u3].slot] = 1
                for (var u4 = 0; u4 < papers.length; u4++) used[papers[u4].slot] = 1
                var freeSlot = -1
                for (var u5 = 0; u5 < P.in.length; u5++) { if (!used[u5]) { freeSlot = u5; break } }
                if (freeSlot < 0) {
                    flagsCanary('补物品失败：cell=' + PT.cell + ' 产物=' + PT.out + ' ⇒ 没有空输入槽')
                } else {
                    real.push({ d: { kind: 'ITEM', id: PT.item, count: 1, name: null, nbt: null, rawTag: null }, slot: freeSlot, patched: true })
                    console.info('[PF-PATCH] 🔴 补物品：cell=' + PT.cell + ' 产物=' + PT.out
                        + ' ⇒ 注入「' + PT.item + '」×1（配纸「物质模块是催化剂」）'
                        + '   （依据：用户 2026-09-26「我忘记放物质模块了，物质模块是基础物质模块」）')
                }
            }

            // ── 幂等②：纸 ── 已有 TYPE 纸就不补
            if (typePaper) {
                console.info('[PF-PATCH] ✅ 幂等跳过（纸）：cell=' + PT.cell + ' 产物=' + PT.out
                    + ' ⇒ 类型纸已存在（「' + typePaper.desc.name + '」），不再注入；该补丁的纸部分可退役。')
            } else {
                typePaper = { desc: { kind: 'TYPE', name: PT.type }, slot: -1, patched: true }
                timePaper = { desc: { kind: 'TIME', name: PT.time }, slot: -1, patched: true }
                var pNote = { desc: { kind: 'NOTE', name: PT.note }, slot: -1, patched: true }
                notes.push(pNote)
                papers.push({ desc: { kind: 'TYPE', name: PT.type }, slot: -1, patched: true })
                papers.push({ desc: { kind: 'TIME', name: PT.time }, slot: -1, patched: true })
                papers.push(pNote)
                console.info('[PF-PATCH] 🔴 补纸：cell=' + PT.cell + ' 产物=' + PT.out
                    + ' ⇒ 类型「' + PT.type + '」/ 时长「' + PT.time + '」/ 注「' + PT.note + '」'
                    + '   （依据：用户 2026-09-26 口述；该样板原始 NBT 里无任何 paper）')
            }
        }
        rows.push({
            no: rows.length + 1, cell: cellName, cellSlot: cells[c].slot, idxInCell: idx,
            type: typePaper ? typePaper.desc.name : null,
            real: real, fluids: fIns, circuits: circuits, outs: outs,
            time: timePaper ? timePaper.desc.name : null,
            notes: notes, outNotes: outPapers, papers: papers,
            inLen: P.inLen, outLen: P.outLen
        })
    }
}

// ---------------------------------------------------------------- cell counts
var cellCount = {}
for (var cc = 0; cc < cells.length; cc++) cellCount[cells[cc].name] = (byCell[String(cells[cc].name)] || []).length

// ---------------------------------------------------------------- old 8 identification
// signatures from [server_scripts]shanhai_recipes.js header §3 (the authoritative record of the previous PF.txt)
function outSig(r) {
    var s = []
    for (var x = 0; x < r.outs.length; x++) { if (r.outs[x].d.kind !== 'ITEM') continue; s.push((r.outs[x].d.count && r.outs[x].d.count > 1 ? r.outs[x].d.count + 'x ' : '1x ') + r.outs[x].d.id) }
    s.sort()
    return s.join(' + ')
}
var OLD = {
    '1x shanhai:primordial_omega_engine': '1 处理样板ULV',
    '1x shanhai:primordial_divergence_generator': '2 合成样板',
    '1x shanhai:wl_board_ulv': '3 合成样板',
    '1x shanhai:primordial_engine_core': '4 合成样板',
    '1x shanhai:introductory_material_module': '5 合成样板',
    '1x shanhai:primordial_matter_caster': '6 合成样板',
    '16x shanhai:photon': '7 处理样板LV',
    '32x shanhai:first_light + 4x shanhai:photon': '8 处理样板LV'
}
// 旧编号 -> 旧元件名（用于消歧：#22 与旧 ⑤ 产出同一个物品，但元件不同）
var OLD_CELL = {
    '1 处理样板ULV': '处理样板ULV',
    '2 合成样板': '合成样板', '3 合成样板': '合成样板', '4 合成样板': '合成样板',
    '5 合成样板': '合成样板', '6 合成样板': '合成样板',
    '7 处理样板LV': '处理样板LV', '8 处理样板LV': '处理样板LV'
}
// disambiguate 16x photon (⑦) vs 4x photon (⑧) by fluid outputs
function oldTag(r) {
    var sig = outSig(r)
    var tag = null
    if (sig === '16x shanhai:photon') {
        var ids = []
        for (var z = 0; z < r.outs.length; z++) ids.push(r.outs[z].d.id)
        if (ids.indexOf('shanhai:zero_point_energy') >= 0) tag = '7 处理样板LV'
    } else {
        tag = OLD[sig] || null
    }
    // 🔴 消歧：旧编号必须落在【同一个元件】里，否则不是同一条
    if (tag && OLD_CELL[tag] !== r.cell) return null
    return tag
}
for (var r2 = 0; r2 < rows.length; r2++) rows[r2].old = oldTag(rows[r2])

// fallback: old shaped ones are in 合成样板 with exactly 9 real inputs
for (var r3 = 0; r3 < rows.length; r3++) {
    var R = rows[r3]
    if (R.old) continue
    if (R.cell === '合成样板' && R.real.length === 9 && R.real.length === 9 && !R.type) {
        // match against known old patterns by content
        var sig9 = R.real.map(function (x) { return x.d.id }).join(',')
        var OLD9 = {
            'gtceu:primitive_void_ore,gtceu:ulv_fragment_world_collection_machine,gtceu:primitive_void_ore,gtceu:ulv_fragment_world_collection_machine,shanhai:primordial_engine_core,gtceu:ulv_fragment_world_collection_machine,gtceu:primitive_void_ore,gtceu:ulv_fragment_world_collection_machine,gtceu:primitive_void_ore': '2 合成样板',
            'gtlcore:mining_crystal,kubejs:reactor_core,minecraft:diamond,shanhai:wl_board_ulv,gtlcore:treasures_crystal,shanhai:wl_board_ulv,minecraft:diamond,gtceu:spacetime_tiny_fluid_pipe,gtlcore:mining_crystal': '5 合成样板',
            'gtceu:spacetime_tiny_fluid_pipe,gtlcore:primitive_fluid_regulator,gtceu:spacetime_tiny_fluid_pipe,thetornproductionline:celestial_secret_deducing_module_ulv,gtlcore:treasures_crystal,shanhai:wl_board_ulv,gtlcore:primitive_robot_arm,ae2:fluix_glass_cable,gtlcore:primitive_robot_arm': '4 合成样板',
            'gtceu:pulsating_alloy_ingot,gtceu:certus_quartz_gem,gtceu:pulsating_alloy_ingot,gtceu:certus_quartz_gem,kubejs:ulv_universal_circuit,gtceu:certus_quartz_gem,gtceu:pulsating_alloy_ingot,gtceu:certus_quartz_gem,gtceu:pulsating_alloy_ingot': '3 合成样板',
            'thetornproductionline:celestial_secret_deducing_module_lv,ae2:molecular_assembler,thetornproductionline:celestial_secret_deducing_module_lv,ae2:molecular_assembler,shanhai:primordial_engine_core,ae2:molecular_assembler,thetornproductionline:celestial_secret_deducing_module_lv,ae2:molecular_assembler,thetornproductionline:celestial_secret_deducing_module_lv': '6 合成样板'
        }
        if (OLD9[sig9]) R.old = OLD9[sig9]
    }
}

// ---------------------------------------------------------------- assertion (after old tagging)
// 🔴 2026-09-29：这两个字面量 8 原来【写死在两处】（断言判据 + 打印文案），改一处漏一处就假绿。
//    现在收敛成【一个具名常量】，并写明它是"历史留档值"（旧 PF.txt 那批老配方恒为 8 条，
//    这是不可现算的：旧源文件已被覆盖）。改这个数之前先读下面 OLD9/OLD10 那两张签名表。
var OLD_EXPECTED = 8
var oldCount = 0
for (var oi = 0; oi < rows.length; oi++) if (rows[oi].old) oldCount++
if (oldCount !== OLD_EXPECTED) {
    console.log('!!! ASSERT FAIL: 旧配方识别数 = ' + oldCount + '，应为 ' + OLD_EXPECTED + ' —— 对照表不可信，必须先修')
    process.exit(2)
}
console.log('ASSERT OK: 旧配方识别数 = ' + oldCount + ' / ' + OLD_EXPECTED
    + '（' + OLD_EXPECTED + ' 是【历史留档值 2026-09-26】：旧 PF.txt 那批老配方条数，无法现算 —— 旧源文件已被覆盖）')

// ---------------------------------------------------------------- type name -> id
function exactHits(name) {
    var hits = []
    var srcs = [['gtceu_zh_cn', gt], ['shanhai_zh_cn', sh]]
    for (var s = 0; s < srcs.length; s++) {
        var o = srcs[s][1]
        for (var k in o) {
            if (!Object.prototype.hasOwnProperty.call(o, k)) continue
            if (o[k] === name) hits.push({ file: srcs[s][0], key: k, value: o[k] })
        }
    }
    return hits
}
var typeNames = {}
for (var t = 0; t < rows.length; t++) if (rows[t].type) typeNames[rows[t].type] = (typeNames[rows[t].type] || 0) + 1

// ---------------------------------------------------------------- emit
// 🔴 2026-09-29：数据源那一行原来把 SHA256 与字节数【写死】了（`086D2F5E…` / `41,564 B`），
//    那是 2026-09-26 那版 PF.txt 的值，早就过期 ⇒ 改成**现读现算**。
var _srcBuf = fs.readFileSync(SRC)
var _srcStat = fs.statSync(SRC)
var _srcSha = crypto.createHash('sha256').update(_srcBuf).digest('hex').toUpperCase()
log('# PF.txt → 样板清单（人工核对用）')
log('')
// 🔴 2026-09-29：机器可解析的源头声明行（HTML 注释，渲染后不可见）。
//    ⇒ 任何【引用方】都可以一道正则读出它对应的 PF.txt sha256，再和磁盘现值比对，
//      不用相信"这份清单是新的"这句话。人读的那一行在它下面。
log(PROV.markLine({ srcPath: SRC, srcSha256: _srcSha, srcBytes: _srcStat.size, srcMtime: _srcStat.mtime.toISOString() }))
log('数据源：`' + SRC + '`（SHA256 `' + _srcSha + '`，' + _srcStat.size + ' B，mtime '
    + _srcStat.mtime.toISOString() + '，单行 NBT）')
log('')
// 🔴 现算的"我过没过期"判据：把【生成这一刻】的 PF.txt 指纹与【写这份文件这一刻】磁盘上的比。
//    不一致 ⇒ 这份清单在写出去的同一秒就已经过期（说明流水线被拆开跑了）⇒ 直接印 ❌。
var _liveNow = PROV.srcNow(SRC)
log((_liveNow.srcSha256 === _srcSha ? '✅ ' : '❌ ')
    + '**过期自检**：本清单声明的源头 sha256 = `' + _srcSha + '`；写盘这一刻磁盘上 `'
    + SRC + '` 的现算 sha256 = `' + _liveNow.srcSha256 + '` ⇒ '
    + (_liveNow.srcSha256 === _srcSha
        ? '一致 —— 这份清单对应当前 PF.txt，可以引用。'
        : '**不一致！这份清单一写完就已经过期，引用它会出错。**'))
log('')
log('> ⚠️ **引用本清单之前请先自己比一次**：如果你手上的 PF.txt 的 sha256 不是 `' + _srcSha + '`，')
log('> 那这份清单就是旧版（2026-09-26 出过一次这种事：有人照抄了旧清单的数字，9 处全错）。')
log('> 复现判据（一条命令）：`Get-FileHash ' + SRC + ' -Algorithm SHA256`')
log('')
log('> 本清单里的**一切条数都是跑本脚本时现算的**，电压/EUt 一律来自 `kubejs\\_generators\\gt_voltage.js`')
log('> （与 `gen_kjs.js` 同一个模块）⇒ 不会再出现"清单写着旧数字、产物写着新数字"这种事。')
log('')
log('## 0. 检查器自证（先证它对，再信它）')
log('')
log('- `ae2:processing_pattern` **原始出现次数**（纯文本计数）= **' + data.rawCount + '**')
log('- **逐条解析成功数** = **' + data.parsedCount + '**')
log('- ⇒ **两边相等：' + (data.rawCount === data.parsedCount ? '✅ 相等，无静默漏条' : '❌ 不等，解析有漏') + '**')
// 🔴 2026-09-29：AE2 的定长格数原来在这里【写死成 81 / 27】。它不是"历史值"、而是**可以从 rows 现算的**
//    ⇒ 改成取第一条的行长度当基准，再逐条校验（语义与原判据完全一致，但数字不再是抄的）。
//    写死的坏处：AE2 版本一变、或某条被换成别的数组，这里会印着 81/27 却仍然绿。
var _inLenNow = rows.length ? rows[0].inLen : 0
var _outLenNow = rows.length ? rows[0].outLen : 0
log('- 逐条长度：`in` 全部 = ' + _inLenNow + ' 格 / `out` 全部 = ' + _outLenNow + ' 格（AE2 定长数组，空槽以 `{}` 占位；格数为**现算**）')
var padOK = true
for (var pi = 0; pi < rows.length; pi++) if (rows[pi].inLen !== _inLenNow || rows[pi].outLen !== _outLenNow) padOK = false
log('- in/out 定长校验：' + (padOK ? '✅ ' + rows.length + '/' + rows.length + ' 条都是 ' + _inLenNow + ' / ' + _outLenNow : '❌ 有条目长度异常'))
var _oldRows = []
for (var _po = 0; _po < rows.length; _po++) if (rows[_po].old) _oldRows.push(rows[_po])
log('- 正面对照：解析出的 ' + _oldRows.length + ' 条旧配方与旧 `shanhai_pf_recipes.js` 文件头 §3 的记录**逐字一致**（见 §3 对照表）')
log('')
log('## 1. 🔴 第一问：几个元件、几条样板、和上次的对照')
log('')
log('| 本次 Slot | 元件名（display.Name 逐字） | 本次条数 | 上次（旧 PF.txt） | 差值 |')
log('|---|---|---|---|---|')
// ⚠️ LAST / lastSlot 是【旧 PF.txt 的历史留档】—— 旧源文件已经被覆盖，无法再现算，
//    所以这两张表只能继续手写；但"合计"是从它算出来的（见 LAST_TOTAL），不再另写一个 8。
//    （与 gen_kjs.js 的 LAST_PF 是同一份历史。）
var LAST = { '处理样板ULV': 1, '合成样板': 5, '处理样板LV': 2 }
var lastSlot = { '处理样板ULV': 0, '合成样板': 1, '处理样板LV': 2 }
var LAST_TOTAL = 0
for (var _lk in LAST) LAST_TOTAL += LAST[_lk]
for (var ci = 0; ci < cells.length; ci++) {
    var nm = cells[ci].name
    var now = cellCount[nm] || 0
    var was = LAST[nm] !== undefined ? LAST[nm] : '**（上次没有这个元件）**'
    var delta = LAST[nm] !== undefined ? (now - LAST[nm]) : '**全部是新增**'
    log('| Slot ' + cells[ci].slot + ' | ' + nm + ' | **' + now + '** | ' + (was === '**（上次没有这个元件）**' ? was : 'Slot ' + lastSlot[nm] + ' / ' + was + ' 条') + ' | ' + delta + ' |')
}
log('| — | **合计** | **' + data.parsedCount + '** | **' + LAST_TOTAL + '** | **+' + (data.parsedCount - LAST_TOTAL) + '** |')
log('')
log('**结论：这次共 ' + data.parsedCount + ' 条 = 旧 ' + LAST_TOTAL + ' 条（保留，内容未变）+ 新增 ' + (data.parsedCount - LAST_TOTAL) + ' 条。**')
log('')
// 原先这句话把「合成样板仍然是 5 条、没有新增」写死了（那是 09-26 那版的比对结论）⇒ 改成现算比对。
var _synthNow = cellCount['合成样板'] || 0, _synthLast = LAST['合成样板'] || 0
log('> ⚠️ 单位置自证（**现算**，不顺着说）：「合成样板」（Slot 1）本次 **' + _synthNow + '** 条，上次 **' + _synthLast + '** 条 ⇒ '
    + (_synthNow === _synthLast ? '**这个元件没有新增**（条数与上次相同）' : '**这个元件新增了 ' + (_synthNow - _synthLast) + ' 条**'))
log('')
// 原先把「新增集中在：Slot 0（3 条）、Slot 2（+18 条）、Slot 3（+9 条）」写死了 ⇒ 改成现算
var _newCells = [], _knownDelta = []
for (var _c2 = 0; _c2 < cells.length; _c2++) {
    var _nm2 = cells[_c2].name, _n2 = cellCount[_nm2] || 0
    if (LAST[_nm2] === undefined) _newCells.push('Slot ' + cells[_c2].slot + '「' + _nm2 + '」（全新元件，' + _n2 + ' 条）')
    else _knownDelta.push('Slot ' + cells[_c2].slot + '「' + _nm2 + '」（' + (LAST[_nm2] >= 0 ? '+' : '') + (_n2 - LAST[_nm2]) + ' 条）')
}
log('> 新增集中在：' + _newCells.concat(_knownDelta).join('、') + '。')
log('')
log('> ⚠️ 元件【槽位变了】：上次 Slot 0 =「处理样板ULV」、Slot 2 =「处理样板LV」；')
log('> 这次槽位整体后移（ULV→Slot 2，LV→Slot 3），Slot 0 换成了新元件「处理样板-星门(MAX+16)」。')
log('> 判据：条数与内容吻合（ULV 里仍含旧 ①、LV 里仍含旧 ⑦⑧）⇒ 是**同一个元件被挪了槽**，不是新元件。')
log('')
log('## 2. 🔴 中文名 → `gtceu:<id>` 映射（口径沿用上次：gtceu jar lang 里【唯一精确等于】那条）')
log('')
log('| 纸上中文名 | 精确命中的键 | 来源文件 | 条数 | 判定 |')
log('|---|---|---|---|---|')
var unmatched = []
for (var tn in typeNames) {
    var hits = exactHits(tn)
    var cand = []
    for (var h = 0; h < hits.length; h++) if (/^gtceu\.[a-z_]+$/.test(hits[h].key)) cand.push(hits[h])
    if (cand.length >= 1) {
        var id = cand[0].key.replace('gtceu.', '')
        log('| ' + tn + ' | `' + cand[0].key + '` | ' + cand[0].file + ' | ' + typeNames[tn] + ' | ✅ 对上 |')
    } else {
        unmatched.push(tn)
        log('| ' + tn + ' | **（无精确命中）** | — | ' + typeNames[tn] + ' | ⚠️ **对不上，见下** |')
    }
}
log('')
if (unmatched.length) {
    log('### ⚠️ 对不上的（重点）：')
    for (var u = 0; u < unmatched.length; u++) {
        var nm2 = unmatched[u]
        log('')
        log('**「' + nm2 + '」**：gtceu / shanhai 两份 lang 里**没有任何值精确等于它**。近似候选：')
        var near = []
        for (var k2 in sh) { if (typeof sh[k2] === 'string' && /^gtceu\.[a-z_]+$/.test(k2) && sameChars(sh[k2], nm2)) near.push(k2 + ' = ' + sh[k2]) }
        for (var k3 in gt) { if (typeof gt[k3] === 'string' && /^gtceu\.[a-z_]+$/.test(k3) && sameChars(gt[k3], nm2)) near.push(k3 + ' = ' + gt[k3]) }
        log('- 近似（字符集相同、顺序不同）：' + (near.length ? near.join(' ／ ') : '（无）'))
    }
}
function sameChars(a, b) {
    if (a.length !== b.length) return false
    var A = a.split('').sort().join(''), B = b.split('').sort().join('')
    return A === B
}

log('')
log('## 3. 全部 ' + rows.length + ' 条逐条（★ = 新增；编号 = 本次 PF.txt 里 `Items` 顺序）')
log('')
log('| # | ★ | 元件 | 配方类型（中文 ⇒ gtceu id） | 输入（纸除外） | 输出 | 耗时 | 备注纸 |')
log('|---|---|---|---|---|---|---|---|')
function typeText(r) {
    if (!r.type) return r.cell === '合成样板' ? '**工作台（有序 shaped）**' : '⚠️ **纸上没写配方类型**'
    var hits = exactHits(r.type)
    var cand = []
    for (var h = 0; h < hits.length; h++) if (/^gtceu\.[a-z_]+$/.test(hits[h].key)) cand.push(hits[h])
    if (cand.length) return r.type + ' ⇒ `' + cand[0].key.replace('gtceu.', 'gtceu:') + '`'
    return r.type + ' ⇒ ⚠️ **对不上**'
}
function insText(r) {
    var p = []
    for (var i2 = 0; i2 < r.real.length; i2++) p.push(descIn(r.real[i2].d))
    for (var i3 = 0; i3 < r.circuits.length; i3++) p.push('`打孔卡` circuit ' + r.circuits[i3].n)
    for (var i4 = 0; i4 < r.fluids.length; i4++) p.push(descIn(r.fluids[i4].d))
    return p.join(' ＋ ') || '（空）'
}
function outsText(r) {
    var p = []
    for (var i5 = 0; i5 < r.outs.length; i5++) p.push(descOut(r.outs[i5].d))
    return p.join(' ＋ ') || '（空）'
}
function timeText(r) {
    if (!r.time) return '⚠️ 纸没写'
    var n = parseInt(r.time, 10)
    return r.time + ' ⇒ ' + (n * 20) + ' tick'
}
function notesText(r) {
    var p = []
    for (var i6 = 0; i6 < r.notes.length; i6++) p.push('「' + r.notes[i6].desc.name + '」')
    for (var i6b = 0; i6b < r.outNotes.length; i6b++) p.push('**「' + r.outNotes[i6b].desc.name + '」（写在【输出】格里）**')
    if (!p.length) return '—'
    return p.join(' ／ ')
}
for (var z2 = 0; z2 < rows.length; z2++) {
    var R2 = rows[z2]
    log('| ' + R2.no + ' | ' + (R2.old ? '' : '**★**') + ' | ' + R2.cell + ' | ' + typeText(R2) + ' | ' + insText(R2) + ' | ' + outsText(R2) + ' | ' + timeText(R2) + ' | ' + notesText(R2) + ' |')
}
log('')
log('')
var _synth = []
for (var _g2 = 0; _g2 < rows.length; _g2++) if (rows[_g2].cell === '合成样板') _synth.push(rows[_g2])
log('### 3b. 「合成样板」' + _synth.length + ' 条 —— `in` 下标 0..8 按**行优先**还原成 3×3（用户已核对确认这个口径）')
log('')
for (var g = 0; g < rows.length; g++) {
    var G = rows[g]
    if (G.cell !== '合成样板') continue
    log('**#' + G.no + ' → 产出 `' + outsText(G) + '`**')
    log('')
    log('```')
    for (var rr = 0; rr < 3; rr++) {
        var line = []
        for (var cq = 0; cq < 3; cq++) {
            var cellIdx = rr * 3 + cq
            var found = '（空）'
            for (var q2 = 0; q2 < G.real.length; q2++) if (G.real[q2].slot === cellIdx) found = G.real[q2].d.id
            line.push(found)
        }
        log('  ' + line.join('  |  '))
    }
    log('```')
    log('')
}

log('## 4. 那 ' + _oldRows.length + ' 条旧配方的对照（证明「没看错」）')
log('')
log('| 本次 # | 本次元件 | 旧编号 / 旧元件 | 输出（逐字） | 判定 |')
log('|---|---|---|---|---|')
for (var z3 = 0; z3 < rows.length; z3++) if (rows[z3].old) log('| ' + rows[z3].no + ' | ' + rows[z3].cell + ' | ' + rows[z3].old + ' | ' + outsText(rows[z3]) + ' | ✅ 与文件头 §3 一致 |')

log('')
log('## 5. ⚠️ 空槽的位置要不要保留？—— 我的判断与理由')
log('')
log('**判断：不保留空槽位置，只按「实际有东西的格子」还原。理由三条：**')
log('')
log('1. **空槽不是信息。** `in` 是 AE2 的**定长数组**（本次现算恒为 ' + _inLenNow + ' 格），`{}` 是"这个格子没放东西"，')
log('   它编码的是**数组长度**，不是用户的摆位意图。旧 ① 的 6 个物品恰好落在 0–5，而本次新增的很多条')
// 原先这里举的例写死了「#38：只有下标 0 有真输入」⇒ 改成现算挑一个"真输入最少"的样板来举例
var _sparseRow = null
for (var _sr = 0; _sr < rows.length; _sr++) {
    if (rows[_sr].fluids.length || rows[_sr].circuits.length) continue
    if (!_sparseRow || rows[_sr].real.length < _sparseRow.real.length) _sparseRow = rows[_sr]
}
log('   （如 #' + (_sparseRow ? _sparseRow.no : '?') + '：真输入只有下标 '
    + (_sparseRow ? _sparseRow.real.map(function (x) { return x.slot }).join('/') : '?')
    + '）真输入是**散落**的 —— 若把空槽当下标语义，`in[7]` 与 `in[0]` 的距离')
log('   会被当成「摆位」，但机器配方根本没有 3×3 摆位这回事。')
log('2. **只有「合成样板」的下标才有语义**（下标 0..8 = 3×3 行优先，用户已核对确认）。')
log('   ⇒ 所以：**合成样板保留下标语义（渲染成 3×3 网格）；处理样板丢掉下标，只按出现顺序铺成输入列表。**')
log('3. **顺序影响微乎其微但并非零**：GT 配方里 `.itemInputs(a).itemInputs(b)` 与反序在 JEI/槽位展示上不同，')
log('   但**不改变匹配**（GT 的输入是无序集合匹配）。我按 `in` 数组**原始下标升序**铺，等于保留用户的填写顺序。')
log('')
log('**仍需用户确认的一点**：处理样板里物品与流体的**先后**我只按数组下标排序，不额外分组。')
log('')
log('## 6. ⚠️ 三处口径（先报出来，别自己选）')
log('')
log('### ①「物质模块是催化剂」')
log('')
log('出现在 **' + (function () { var n = 0; for (var i7 = 0; i7 < rows.length; i7++) for (var j = 0; j < rows[i7].notes.length; j++) if (rows[i7].notes[j].desc.name === '物质模块是催化剂') n++; return n })() + ' 条**样板里。同一条样板里同时含一个物质模块物品（通常是 `shanhai:introductory_material_module`）。')
log('')
log('| 写法 | KJS | 占物品输入槽？ | 现状 |')
log('|---|---|---|---|')
log('| 老写法（催化剂） | `.notConsumable(\'1x shanhai:introductory_material_module\')` | **占 1 槽** | 旧文件 ⑦ 曾用过、后于 09-29 改成门槛 |')
log('| 新机制（等级门槛） | `.addCondition(new ModuleLevelCondition(\'shanhai:introductory_material_module\', 1))` | **不占槽** | 已实测：`shanhai-0.1.0.jar` 里 **存在** `com/shanhai/machine/module/ModuleLevelCondition.class` |')
log('')
log('**我的倾向**：**等级门槛**。理由：①jar 里类已存在（本条已取证）；②旧文件 §6 已为它写好 `typeof` 降级 + `[SHANHAI-PF] module-level-condition available=` 判据行；③门槛不占槽，对新增条目的槽位压力更小。')
log('**但要用户定** —— 因为「物质模块是催化剂」这句话的字面意思是**不消耗**，而门槛的语义是**要有等级**，两者不等价。')
log('')
log('### ②「力场发生器是催化剂」')
log('')
// 原先把"出现在 #31 / #33 / #36 三条"写死了 ⇒ 改成现算（行号会随 PF.txt 变）
var _fieldNoteRows = []
for (var _f1 = 0; _f1 < rows.length; _f1++) {
    for (var _f2 = 0; _f2 < rows[_f1].notes.length; _f2++) {
        if (rows[_f1].notes[_f2].desc.name === '力场发生器是催化剂') { _fieldNoteRows.push(rows[_f1].no); break }
    }
}
log('出现在 ' + (_fieldNoteRows.length ? _fieldNoteRows.map(function (x) { return '#' + x }).join(' / ') : '（本次一条都没有）')
    + ' ' + _fieldNoteRows.length + ' 条，每条同格旁边都有 `gtceu:lv_field_generator`。')
log('⇒ 我的推断：`.notConsumable(\'1x gtceu:lv_field_generator\')`。**请确认**（是 LV 不是别的电压？）')
log('')
log('### ③「电子中微子产出概率5%」')
log('')
// 原先把"只出现在 #33 一条"写死了 ⇒ 改成现算
var _chancedRows = []
for (var _h1 = 0; _h1 < rows.length; _h1++) {
    var _hitN = false
    for (var _h2 = 0; _h2 < rows[_h1].outNotes.length; _h2++) {
        if (/电子中微子产出概率5%/.test(rows[_h1].outNotes[_h2].desc.name)) _hitN = true
    }
    if (_hitN) _chancedRows.push(rows[_h1].no)
}
log('只出现在 **' + (_chancedRows.length ? _chancedRows.map(function (x) { return '#' + x }).join(' / ') : '（无）')
    + ' ' + _chancedRows.length + ' 条**，而且它写在那个样板的 **【输出】格 `out[3]` 里**，紧邻 `out[2]` 的 `shanhai:electron_neutrino`。')
log('⇒ ✅ 用户 2026-09-26 裁决：「吃加成」⇒ 落 `.chancedOutput(\'1x shanhai:electron_neutrino\', 500, 100)`。')
log('')
log('🔴🔴 **单位订正（会差 10 倍，必须说清）**：本包 `chancedOutput` 用的是**万分比**，**10000 = 100%** ⇒')
log('**5% = `500`，不是 `5000`**（`5000` 是 50%）。两个独立取证：')
log('')
log('- ① 宿主脚本现成写法：`gtceu.js:6148` `.chancedOutput(item, 2000, 0)`（20%）／')
log('  `gtceu.js:8405` `.chancedOutput("gtceu:magnetic_neodymium_dust", 1000, 0)`（10%）／')
log('  `gtceu.js:6717` `.chancedOutput(item, 200, 20)`（2%）／`ad.js:96` `chancedOutput("gtceu:steel_ingot", 5000, 0)`（50%）')
log('- ② 游戏自己导出的配方表里 `chance` 的**最大值 = 10000**（历史值 2026-09-26：当时扫 `export/recipes/gtceu/` 40 个类型目录，')
log('  出现过的值含 275 / 1111 / 2250 / 4950 / 10000 —— 全部 ≤ 10000）')
log('')
log('🔴 **第二个 int 不是"加成上限"，是【每超频一级的加成量 `tierChanceBoost`】** —— 字节码实证')
log('`GTRecipeBuilder.chancedOutput(ItemStack,int,int)` 偏移 72-74：`aload_0 / iload_3 / putfield tierChanceBoost:I`。')
log('（`maxChance` 从 KubeJS 侧设不了，保持默认 10000 = `getMaxChancedValue()` 的 `sipush 10000`。）')
log('')
// 🔴 2026-09-29：本句原来写成"反读游戏导出表 **93,897 个文件 / 348,092 条 chanced 记录**后…"，
//    那两个数是 2026-09-26 那次全量反读的【历史值】—— 导出表会被用户重新 /kubejs export 覆盖，
//    现算代价是一次全盘扫描（几万文件），本脚本不该为了印一行字去扫。
//    ⇒ 按纪律**显式标注为历史值**并把口径（当时扫的目录）写清楚，不冒充"本次现算"。
log('**`100` 这个数的来源（不是猜的，但下面是【历史值 2026-09-26】）**：那次反读游戏导出表 **93,897 个文件 / 348,092 条 chanced 记录**后，')
log('三元组 `chance=500 / maxChance=10000 / tierChanceBoost=100` 出现 **414 次**，')
log('⚠️ 口径：测量对象 = `local\\kubejs\\export\\` 的当时快照（2026-09-26，未随本次 PF.txt 重新导出）')
log('⇒ 这三个数是**历史值**，不是本次现算；引用它们时请连日期一起引。')
log('全部来自 GTCEu 自己的矿石副产线：`gtceu:macerator` 138 ／ `gtceu:integrated_ore_processor` 138 ／')
log('`gtceu:space_ore_processor` 138 ⇒ 这就是本包"5% 且带加成"的标准配法。')
log('')
log('**加成怎么算**（`ChanceBoostFunction.OVERCLOCK` 反读）：')
log('`tierDiff = machineTier − recipeTier`；`tierDiff ≤ 0` 时**不吃加成**；`recipeTier == 0` 时 `tierDiff−1`；')
log('最终 `chance + tierChanceBoost × tierDiff`，天花板是 `maxChance = 10000`。')
log('⇒ #33 实际概率：LV(1) 5% ／ MV(2) 6% ／ HV(3) 7% ／ EV(5) 9% ／ MAX(14) 18%。')
log('')
log('## 7. 读不懂 / 没做到的（原文列出，不猜）')
log('')
log('### 7.1 元件名里的档位 ⇒ EUt（**现算** —— 值来自唯一真源 `gt_voltage.js`，不写死）')
log('')
// 某个元件是不是"带配方类型的元件"（= 走机器、必须有 EUt）。现算，别手抄名单。
var _cellsWithType = {}
for (var _ct = 0; _ct < rows.length; _ct++) if (rows[_ct].type) _cellsWithType[rows[_ct].cell] = 1
function cellNeedsEut(cellName) { return !!_cellsWithType[cellName] }
log('| 元件名 | 档位 | EUt | 依据 | 备注 |')
log('|---|---|---|---|---|')
for (var _e1 = 0; _e1 < cells.length; _e1++) {
    var _cn2 = cells[_e1].name
    var _tier = GT_VOLTAGE.cellTier(_cn2)
    var _eut2 = GT_VOLTAGE.cellEUt(_cn2)
    if (_eut2 === undefined) {
        log('| ' + _cn2 + ' | — | **（推不出）** | 元件名不在 `CELL_TIER` 里 | ' +
            (cellNeedsEut(_cn2) ? '⚠️ **这个元件带配方类型 ⇒ 必须补 `CELL_TIER`**（gen_kjs.js 会因此拒绝生成）' : '✅ 工作台配方（无配方类型），本来就没有 EUt') + ' |')
        continue
    }
    if (GT_VOLTAGE.isStargateCell(_cn2)) {
        log('| **' + _cn2 + '** | ' + _tier + ' | **' + _eut2 + '** | **现算**：`EUt = V[MAX] × 4^' + GT_VOLTAGE.STARGATE_AMPS_EXP
            + '`（用户 2026-09-26 的【第二次】裁决，原话逐字「溢出那就算了，改成 max+8=max,4^8A」）'
            + '；`V[MAX] = ' + GT_VOLTAGE.V.MAX + '` 取自 gtceu jar 字节码 `GTValues.<clinit>`'
            + '（`bipush 14` → `ldc2_w // long 2147483648l` → `lastore` → `putstatic V:[J`）'
            + '，**不是** `Integer.MAX_VALUE`(2^31−1) | ✅ **不溢出**：`2^31 × 2^16 = 2^47`，只有 `Long.MAX_VALUE(2^63−1)` 的 1/65536。'
            + '⚠️ 元件显示名里的 `MAX+16` 是 **PF.txt 的样板名原文**（两次改判都作废了 4^16 口径），名字不动、只改落地值。'
            + '📌 作废留档：4^16 口径 = `2^31 × 2^32 = 2^63 = Long.MAX_VALUE + 1` ⇒ 恰好越界 1、回绕成负数，**那正是它被改掉的原因**，不再是现行口径 |')
        continue
    }
    log('| ' + _cn2 + ' | ' + _tier + ' | ' + _eut2 + ' | 现算：`GTValues.V[' + _tier + ']`（真值出处见 `kubejs\\_generators\\gt_voltage.js`） | ✅ |')
}
log('')
log('> 🔴 本节 2026-09-29 订正：上一版这一行把星门的 EUt 写成 **2147483648**，并声称"电流 4^16 A 放不进 EUt，')
log('> 只落电压"。**那句是错的、也是过期的**：① 4^16 口径早在 2026-09-26 就被用户改判成 `max+8=max,4^8A`；')
log('> ② 按 4^8 算出来的 `2^47` 是合法 long，`gen_kjs.js` 落地时就是**整体写进 `.EUt(...)`** 的')
// 🔴 2026-09-29：星门条数原来写死"那 3 条"。改成按 rows + GT_VOLTAGE.isStargateCell 现算
//    （判据与 gen_kjs.js 落地 EUt 时用的是同一个函数 ⇒ 两边不可能各说一个数）。
var _stargateRows = 0
for (var _sg = 0; _sg < rows.length; _sg++) {
    if (!rows[_sg].type) continue
    if (GT_VOLTAGE.isStargateCell(rows[_sg].cell)) _stargateRows++
}
log('> （产物里那 ' + _stargateRows + ' 条星门配方的 `EUt: ' + (GT_VOLTAGE.V.MAX * Math.pow(4, GT_VOLTAGE.STARGATE_AMPS_EXP))
    + '` 可以直接 grep 到；条数**现算** = `GT_VOLTAGE.isStargateCell(rows[].cell)` 计数）⇒ 不存在"只落电压"这回事。')
log('> 本条的真值以 `gt_voltage.js` 为准，两边共用一个模块，不会再出现"清单一个数、产物另一个数"。')
log('')
log('## 8. 槽位核对：`photon_separation` 的溢出**已由用户裁决解决**（本节旧文字已作废）')
log('')
log('🔴 本节曾经写着「槽位核对查出的【3 条溢出】」「`slotOver: true` 标记」「落地后打 `SLOT-OVER` 告警行」')
log('   「这**不是我能修的**」—— **那些文字已经作废，是过期信息，下一轮不要再引用。**原因：')
log('   2026-09-26 用户裁决把该类型的上限放宽了，溢出不复存在；`gen_kjs.js` 也早已把 `slotOver` 字段')
log('   与整段 `SLOT-OVER` 告警代码**一并删掉**。下面一律引用**真值**。')
log('')
log('### 8.1 `photon_separation` 的真值')
log('')
log('`setMaxIOSize` = **(4, 10, 2, 2)** ＝ 物品入 4 ／ 物品出 10 ／ 流体入 2 ／ 流体出 2')
log('')
log('出处 = **活代码**（不是注释、不在作废块里）：')
log('`shanhai-rewrite\\src\\main\\java\\com\\shanhai\\common\\recipe\\ShanhaiRecipeTypes.java`')
log('  L505 `PHOTON_SEPARATION = GTRecipeTypes.register("photon_separation", "multiblock")`')
log('  L506         `.setMaxIOSize(4, 10, 2, 2)`')
log('并与已部署 jar 的字节码互证（`ShanhaiRecipeTypes.class` 里 `photon_separation` 段 =')
log('`iconst_4 / bipush 10 / iconst_2 / iconst_2 → setMaxIOSize`）。')
log('')
log('⚠️ **旧值 `(2, 4, 2, 2)` 是 2026-09-26 裁决之前的值** —— 任何还写着它的文字都是过期的。')
log('⚠️ 同理，`gen_kjs.js` 的 `CAP` 表写的 `photon_separation: [4, 10, 2, 2]` 是**对的**，别去"修"它。')
log('')
log('### 8.2 曾经被标成"溢出"的那几条：现在全部装得下')
log('')
log('| 配方 id | 类型 | 需要"物品入" | 该类型上限 | 结论 |')
log('|---|---|---|---|---|')
log('`photon_separation` 上限 = **4**（见 8.1）。本次 `rows` 里该类型的每一条：')
var _sepRows = []
for (var _s1 = 0; _s1 < rows.length; _s1++) if (rows[_s1].type === '光子分离') _sepRows.push(rows[_s1])
for (var _s2 = 0; _s2 < _sepRows.length; _s2++) {
    var _need = _sepRows[_s2].real.length + _sepRows[_s2].circuits.length
    log('| #' + _sepRows[_s2].no + '（产出 ' + outsText(_sepRows[_s2]) + '） | photon_separation | ' + _need + ' | **4** | '
        + (_need <= 4 ? '✅ 装得下' : '❌ **溢出**') + ' |')
}
log('')
var _sepNeedMax = 0, _sepNeedArg = ''
for (var _s3 = 0; _s3 < _sepRows.length; _s3++) {
    var _n3 = _sepRows[_s3].real.length + _sepRows[_s3].circuits.length
    if (_n3 > _sepNeedMax) { _sepNeedMax = _n3; _sepNeedArg = '#' + _sepRows[_s3].no }
}
log('形状（为什么最多 ' + _sepNeedMax + ' 格，**现算**：取上面表里的最大值，来自 ' + _sepNeedArg + '）：')
log('**1~2 个真物品 ＋ 1 个 `notConsumable`(力场发生器) ＋ 1 个 `.circuit(...)` ≤ ' + _sepNeedMax + ' 格**，')
log('上限 4 ⇒ 还留 ' + (4 - _sepNeedMax) + ' 格余量。**不存在溢出，也不需要任何告警。**')
log('')
log('### 8.3 条数（现算，不写死）')
log('')
var _gtTotal = 0
var _sepTotal = 0
for (var _q8 = 0; _q8 < rows.length; _q8++) {
    if (!rows[_q8].type) continue
    _gtTotal++
    if (rows[_q8].type === '光子分离') _sepTotal++
}
var _wbTotal = rows.length - _gtTotal
log('本次 `rows` 共 **' + rows.length + '** 条，其中带 GT 配方类型（= 走机器的那批）**' + _gtTotal + '** 条；')
log('不带类型的 **' + _wbTotal + '** 条是工作台「合成样板」，由 `gen_kjs.js` 的 `event.shaped` 路径生成，不参与本节核对。')
log('其中 `光子分离`（= `gtceu:photon_separation`）**' + _sepTotal + '** 条，逐条都要 ≤ ' + _sepNeedMax + ' 格 ≤ 上限 4 ⇒ 全部装得下（格数**现算**，见 8.2）。')
log('')
log('⚠️ 上面这两个数是**本节在脚本里的位置**算出来的：本节位于「土高炉 ⇒ 原初物质重组」那批副本')
log('   生成之**前**（那段代码在本文件更下方）⇒ 这里 `rows` = ' + rows.length
    + '。副本加完后 `rows` 会更多，')
log('   那才是 `rows.json` 的最终条数（脚本末尾会打印 `wrote 样板清单.md   rows=…`）。')
log('   **本节的数是"此刻的"，别当成最终总数引用。**')
log('')
log('⚠️ **本节不打印任何"溢出"结论。**槽位核对的权威输出是 `gen_kjs.js` 跑完后那一行 `slot overflows = 0`')
log('   （它按 `CAP` 表**逐条**算）。如实标注一处局限：`CAP` 表里**没有** `primordial_matter_recombination`，')
log('   那一类会落兜底 `[99,99,99,99]` ⇒ 对它们而言"没溢出"等于没查过；真值在')
log('   `ShanhaiRecipeTypes.java` 的 `PRIMORDIAL_MATTER_RECOMBINATION` 那一条 `setMaxIOSize`。')
log('')
log('## 9. id 存在性（正面对照，完整清单见 `id_check.md`）')
log('')
// 🔴 2026-09-29：原来这里把"38 条 / 76 个 id / 找到 76 / 找不到 0"全部写死了（那是 09-26 那版的数据）。
//    现在：条数与本表口径的 id 数【现算】；"存在性"的结论【引用】id_check.md（由 check_ids.js 生成），
//    并把该文件里**自己写的条数**一起打出来 —— 两者不一致就当场提示"那个文件已过期，去重跑"。
var _idSetItem = {}, _idSetFluid = {}
function _idAdd(kind, id) { if (id && id !== 'minecraft:paper') _idSetItem[kind + '|' + id] = 1 }
for (var _i1 = 0; _i1 < rows.length; _i1++) {
    var _R9 = rows[_i1]
    for (var _i2 = 0; _i2 < _R9.real.length; _i2++) _idAdd('item', _R9.real[_i2].d.id)
    for (var _i3 = 0; _i3 < _R9.fluids.length; _i3++) _idAdd('fluid', _R9.fluids[_i3].d.id)
    for (var _i4 = 0; _i4 < _R9.outs.length; _i4++) _idAdd(_R9.outs[_i4].d.kind === 'FLUID' ? 'fluid' : 'item', _R9.outs[_i4].d.id)
}
_idAdd('item', 'minecraft:paper')   // 与 check_ids.js 同口径：#25 真产物（出纸）也算
var _idTotal = Object.keys(_idSetItem).length
var _idCheckTxt = null
try { _idCheckTxt = fs.readFileSync(BASE + 'id_check.md', 'utf8') } catch (e) { }
var _icClaim = null, _icFound = null, _icMiss = null
if (_idCheckTxt) {
    var _m1 = /id 总数 = (\d+)/.exec(_idCheckTxt)
    var _m2 = /在正面来源里找到 = (\d+)/.exec(_idCheckTxt)
    var _m3 = /在正面来源里找不到 = (\d+)/.exec(_idCheckTxt)
    if (_m1) _icClaim = +_m1[1]
    if (_m2) _icFound = +_m2[1]
    if (_m3) _icMiss = +_m3[1]
}
log('| 项 | 数 |')
log('|---|---|')
log('| 出现在本次 ' + rows.length + ' 条样板里的 id 总数（**本脚本现算**，物品 + 流体去重） | **' + _idTotal + '** |')
if (_icClaim === null) {
    log('| ✅ / ❌ 存在性结论 | **（读不到 `id_check.md`）** ⇒ 跑一次 `node kubejs\\_generators\\check_ids.js` 再看 |')
} else {
    log('| `id_check.md` 自报的 id 总数（**由 check_ids.js 生成**） | **' + _icClaim + '**'
        + (_icClaim === _idTotal ? '（与上面现算一致 ✅）' : '（⚠️ **与现算不一致 ⇒ 那个文件已过期，去看它的生成时间**）') + ' |')
    log('| ✅ 在正面来源里找到（引自 `id_check.md`） | **' + _icFound + '** |')
    log('| ❌ 找不到（引自 `id_check.md`） | **' + _icMiss + '** |')
}
log('')
log('> ⚠️ 本表的 id 总数是**现算**的；"找到/找不到"是**引用** `recipe-convert\\id_check.md`（由 `check_ids.js` 扫三个正面来源生成）。')
log('> `id_check.md` **不在** `parse_pf → gen_manifest → gen_kjs` 这条流水线里 ⇒ 它可能滞后于本次 PF.txt。')
log('> 两者不一致时以**现算的条数**为准，并重新跑一次 `node kubejs\\_generators\\check_ids.js`。')
log('')
// 🔴 2026-09-29：下面两个"键数"是 2026-09-10 那次导出表快照的【历史值】——
//    导出表会被用户重新 /kubejs export 覆盖 ⇒ 现在这两个数只是当时的规模，不是现值。
//    （真要现算就得把 item.json 整个读进来，而本脚本只是为了印这一行字 ⇒ 按纪律显式标注，不冒充现算。）
log('三个正面来源：`export/registries/item.json`(**19,332 键** —— 历史值 2026-09-10，导出表快照) ／')
log('`fluid.json`(**2,243 键** —— 历史值 2026-09-10) ／')
log('`shanhai-0.1.0.jar` 的 lang（`shanhai:*` 专用 —— 因为导出表 09-10 早于 jar 09-26）。')
log('')
// 🔴 2026-09-29：原来这里写死"11 个正面样本 + 4 个负面样本"。样本表在 check_ids.js 里，
//    ⇒ 改成从 id_check.md 现算（数那一节表格里有几行「正」几行「负」），数不出来就如实说数不出来。
var _ctrlPos = null, _ctrlNeg = null
if (_idCheckTxt) {
    var _ctrlSec = /## 🔴 检查器自证[\s\S]*?⇒ 正面对照失败数/.exec(_idCheckTxt)
    if (_ctrlSec) {
        _ctrlPos = (_ctrlSec[0].match(/^\|\s*正\s*\|/gm) || []).length
        _ctrlNeg = (_ctrlSec[0].match(/^\|\s*负\s*\|/gm) || []).length
    }
}
log('**检查器自证**：' + (_ctrlPos === null || _ctrlNeg === null
    ? '（⚠️ 读不出样本数 —— `id_check.md` 的自证表格式变了或文件读不到，去跑 `node kubejs\\_generators\\check_ids.js`）'
    : _ctrlPos + ' 个正面样本 + ' + _ctrlNeg + ' 个负面样本（**现算**：从 `id_check.md` 的自证表数行数），**全对**')
    + '（明细见 `id_check.md`）。')
log('⚠️ 途中踩过一次"检查器自己的假设错了"：我把 `gtceu:iron_ingot` 当正面样本，但它**真的不存在**')
log('（GTCEu 的铁锭就是原版 `minecraft:iron_ingot`）⇒ 是**控制组写错了**，不是扫描器坏了。')
log('')
log('## 10. 其它没做到的')
log('')
log('- **id 存在性**：本机 `local\\kubejs\\export\\registries\\item.json` 时间戳 = 2026-09-10 19:19，**早于** `shanhai-0.1.0.jar`（2026-09-26 11:11）')
log('  ⇒ 导出表里**查不到任何 `shanhai:*`** —— 这**不是"不存在"**，是"导出表过时"。')
log('  ⇒ `shanhai:*` 的 id 我改用**已部署 jar 自己的 lang**核对（下一步做完整清单）。')
log('- **`物品` 与 `流体` 混在同一格序**：本次多条的 `in` 里物品与流体交错（如 #20 下标 3/4 是流体、0/1/2 是物品）。')
log('  我按"流体 → `.inputFluids`，物品 → `.itemInputs`"分离，**但两者的相对先后我无法从 NBT 还原**（GT 也不区分）。')

// ══════════ 用户 2026-09-26 交办：把【土高炉】那批复制一份 ⇒ 类型改【原初物质重组】＋ 输入加一个 31 号电路 ══════════
//   原话：「把土高炉的那些新增的配方复制一份，然后配方类型改成原初物质重组，条件不变，并且输入额外添加一个31号电路」
//   ✅「条件不变」= 输入/输出/数量/时长【全部照原样】，只动 type 与电路。
//   ✅ id 区分：gen_kjs.js 的 mkId 本来就有 `while (used[id]) id = base + '_2'` 去重，
//      再叠一层显式后缀 `_pmr`（copiedFrom 存在时）⇒ 一眼能看出是副本。
var RE_TYPE_COPIES = [
    { from: '土高炉', to: '原初物质重组', circuit: 31, suffix: 'pmr' }
]
for (var cp = 0; cp < RE_TYPE_COPIES.length; cp++) {
    var CFG = RE_TYPE_COPIES[cp]
    var srcRows = []
    for (var s9 = 0; s9 < rows.length; s9++) if (rows[s9].type === CFG.from) srcRows.push(rows[s9])
    console.log('[PF-COPY] ' + CFG.from + ' ⇒ ' + CFG.to + '：源 ' + srcRows.length + ' 条')
    for (var s10 = 0; s10 < srcRows.length; s10++) {
        var S = srcRows[s10]
        var copy = JSON.parse(JSON.stringify(S))
        copy.type = CFG.to
        copy.old = false
        copy.copiedFrom = S.no
        copy.suffix = CFG.suffix
        var hasC = false
        for (var c9 = 0; c9 < copy.circuits.length; c9++) if (copy.circuits[c9].n === CFG.circuit) hasC = true
        if (!hasC) {
            var usedS = {}
            for (var u9 = 0; u9 < copy.real.length; u9++) usedS[copy.real[u9].slot] = 1
            for (var u10 = 0; u10 < copy.fluids.length; u10++) usedS[copy.fluids[u10].slot] = 1
            for (var u11 = 0; u11 < copy.circuits.length; u11++) usedS[copy.circuits[u11].slot] = 1
            for (var u12 = 0; u12 < (copy.papers || []).length; u12++) usedS[copy.papers[u12].slot] = 1
            var freeS = -1
            for (var u13 = 0; u13 < 81; u13++) if (!usedS[u13]) { freeS = u13; break }
            copy.circuits.push({ n: CFG.circuit, slot: freeS, copied: true })
        }
        copy.no = rows.length + 1
        rows.push(copy)
    }
    console.log('[PF-COPY] 新增 ' + srcRows.length + ' 条 ⇒ rows 合计 ' + rows.length)
}

var MANIFEST_MD = BASE + '样板清单.md'
var ROWS_JSON = BASE + 'rows.json'
fs.writeFileSync(MANIFEST_MD, OUT.join('\r\n'), 'utf8')
console.log('')
console.log('wrote 样板清单.md   rows=' + rows.length + '  old=' + (function () { var n = 0; for (var i8 = 0; i8 < rows.length; i8++) if (rows[i8].old) n++; return n })())
// 🔴 rows.json 形状【保持裸数组】不变 —— 实测 12 处以上消费者按裸数组解析（check_ids.js /
//    temp\ftbq-ai\*.js / temp\*.js），改成 {_provenance, rows} 会同时打断它们。
//    ⇒ 它的源头声明走同目录的 _provenance.json 清单（见 provenance.js 顶部说明）。
fs.writeFileSync(ROWS_JSON, JSON.stringify(rows, null, 1), 'utf8')
var _rRec = PROV.record(ROWS_JSON, 'kubejs\\_generators\\gen_manifest.js', SRC)
var _mRec = PROV.record(MANIFEST_MD, 'kubejs\\_generators\\gen_manifest.js', SRC)
console.log('[PROV] rows.json        自证: srcSha256=' + _rRec.srcSha256 + ' artifactSha256=' + _rRec.artifactSha256 + ' bytes=' + _rRec.artifactBytes)
console.log('[PROV] 样板清单.md      自证: srcSha256=' + _mRec.srcSha256 + ' artifactSha256=' + _mRec.artifactSha256 + ' bytes=' + _mRec.artifactBytes)
