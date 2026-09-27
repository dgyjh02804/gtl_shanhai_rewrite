// Generate shanhai_pf_recipes.NEW.js  (old 8 kept verbatim + new 30 from PF.txt)
// Discipline: every spec is derived from rows.json (parsed from PF.txt), nothing hand-typed.
'use strict'
var fs = require('fs')
var BASE = 'C:\\Users\\david\\Desktop\\构建\\shanhai重构\\recipe-convert\\'
var rows = JSON.parse(fs.readFileSync(BASE + 'rows.json', 'utf8'))

// ---------------------------------------------------------------- IO caps (evidence: bytecode / source)
// 来源：ShanhaiRecipeTypes.java 的 .setMaxIOSize(...)  /  gtceu jar GTRecipeTypes.class 字节码
var CAP = {
    circuit_assembler: [6, 1, 1, 0],
    primitive_blast_furnace: [3, 3, 0, 0],
    spacetime_distortion: [9, 6, 6, 5],
    primordial_singularity_inversion: [12, 3, 6, 3],
    matter_flow_condensation: [4, 2, 2, 2],
    matter_module_casting: [17, 1, 4, 0],
    photon_siphon: [4, 2, 2, 2],
    // 🔴 2026-09-26 用户裁决（逐字）：「photon_separation 的 setMaxIOSize(2, 4, 2, 2) ⇒ (4, 10, 2, 2)」
    //    ⇒ 物品入 2 ⇒ 4（原本 3 条星门配方各要 3 格 ⇒ 旧值溢出）、物品出 4 ⇒ 10；**流体 2/2 不动**。
    //    ⚠️ 旧值 (2,4,2,2) 与另一处 (4,2,2,2) [photon_siphon] 只差前两位，别写串。
    //    新值出处：javap -c 成品 jar 的 ShanhaiRecipeTypes.init()，该调用点操作数
    //              = iconst_4 / bipush 10 / iconst_2 / iconst_2（2026-09-26 实测）。
    photon_separation: [4, 10, 2, 2],
    interstellar_matter_absorption: [2, 2, 2, 2],
    wl_board_circuit_assembly: [9, 3, 6, 4]
}
var TYPE_ID = {
    '电路组装机': 'circuit_assembler',
    '土高炉': 'primitive_blast_furnace',
    '量子化现实重构': 'spacetime_distortion',
    '原初奇点反演': 'primordial_singularity_inversion',
    '物质流凝结': 'matter_flow_condensation',
    '物质模块铸造': 'matter_module_casting',
    '光子虹吸': 'photon_siphon',
    '光子分离': 'photon_separation',
    // 🔴 2026-09-26 补：TYPE_ID 原本【漏了】这个键，导致 5 行（no=20/41/43/48/51）被误判成解析不出类型而跳过。
    //    GT id 由 SNAP-1 导出证实为 gtceu:primordial_matter_recombination。
    '原初物质重组': 'primordial_matter_recombination',
    '星际物质吸取': 'interstellar_matter_absorption',
    // ✅ 用户 2026-09-26 亲自确认：纸上「世线电路板组装」是【笔误】⇒ 按 lang 的
    //    `gtceu.wl_board_circuit_assembly`（世线板电路组装）落。原 ⚠️ 标记已按用户裁决移除。
    '世线电路板组装': 'wl_board_circuit_assembly'
}
var UNRESOLVED_TYPE = {}
// 用户 2026-09-26 **最新**裁决（逐字）：「溢出那就算了，改成 max+8=max,4^8A」
//   ⇒ 电压 = MAX 档，电流 = **4^8 A**。（本条覆盖早先那句「MAX+16=MAX，4^16A」的裁决。）
// 🔴 V[MAX] 从【已部署 gtceu jar 字节码】读出来的真值 = 2147483648（= 2^31），
//    不是 Integer.MAX_VALUE(2147483647)，也不是从注释抄的。
//    GTValues.<clinit>：`bipush 14` → `ldc2_w // long 2147483648l` → `lastore` → `putstatic V:[J`
//    （V 是 15 项 long[]，索引 0..14 = ULV..MAX；VN[14] = "MAX"）
// 🔴 算式：EUt = V[MAX] × 4^8 = 2^31 × 2^16 = **2^47 = 140737488355328**
//    只有 Long.MAX_VALUE(2^63−1) 的 1/65536 ⇒ **不溢出**，可以整体写进 `.EUt(long)`。
// ⚠️ 旧口径留档：4^16 ⇒ 2^31 × 2^32 = 2^63 = Long.MAX+1 ⇒ 越界 1 ⇒ 回绕成负数（详见输出文件头 §8⑥）。
// ⚠️ 「处理样板-星门(MAX+16)」这个键名是 **PF.txt 里 AE2 样板的显示名**，本次不改样板，故原样保留。
// 🔴 2026-09-26 补：用户新加了「处理样板MV」箱子，CELL_EUT 里【没有这个键】=> EUt(undefined)
//    => 运行期 8 条配方全报 `Can't find method GTRecipeJS.EUt(Undefined)`。
//    电压按 GT 电压表递进（ULV=8 / LV=32 / MV=128 / HV=512），与原有两个值完全吻合。
var CELL_EUT = { '处理样板ULV': 8, '处理样板LV': 32, '处理样板MV': 128, '处理样板-星门(MAX+16)': 140737488355328 }
var CELL_EUT_FLAG = { '处理样板-星门(MAX+16)': true }
var MAX_AMP_OVERFLOW = false

// ---------------------------------------------------------------- spec builder
// 🔴 用户 2026-09-26 裁定（甲）：解析不出配方类型的行【跳过并报告】，不再抛错中断整轮生成
var SKIPPED = []
function specFromRow(R) {
    var typeId = TYPE_ID[R.type]
    if (!typeId) { SKIPPED.push({ no: R.no, cell: R.cell, srcType: R.type, outs: (R.outs||[]).length }); return null }
    var s = { r: R, type: typeId, srcTypeName: R.type }

    // 输入分类
    var catModule = null, catField = null
    for (var i = 0; i < R.notes.length; i++) {
        var n = R.notes[i].desc.name
        if (n === '物质模块是催化剂') catModule = true
        if (n === '力场发生器是催化剂') catField = true
    }
    var items = [], mods = []
    for (var a = 0; a < R.real.length; a++) {
        var id = R.real[a].d.id, cnt = R.real[a].d.count || 1
        if (/material_module$/.test(id)) { mods.push({ id: id, cnt: cnt }); items.push({ id: id, cnt: cnt, isModule: true }) }
        else items.push({ id: id, cnt: cnt })
    }
    var itemInputs = [], notConsumable = [], moduleGate = null, moduleFallback = null, flags = []

    // 🔴 2026-09-26 用户裁定（原话逐字）：
    //    「突然想起一件事，有一个问题，我把不是山海的机器写了物质模块的要求，
    //      那里物质模块作为催化剂就真的只是催化剂，主要就是这个土高炉，先把土高炉的都该回去」
    //    ⇒ 规则：**山海自己的机器** ⇒ 物质模块 = **等级门槛**（准入判据，以催化剂形态出现）；
    //            **不是山海的机器** ⇒ 物质模块 = **真的只是催化剂** ⇒ **不设等级门槛**。
    //    ⇒ 土高炉 `gtceu:primitive_blast_furnace`（GTCEu 原生）那 14 条全部改成
    //      `notConsumable` 催化剂（**模块物品仍在输入里**，只是不再是门槛）。
    //    ⚠️ `photon_separation`（同为 GTCEu 原生，3 条：photon_2 / electron / photon_rainbow）
    //       **暂不动** —— 队长正在问用户，那 3 条是他设计的逐光链，门槛可能是故意的。
    //    ⚠️ `photon_siphon` **不是**这一类：它是**山海自己注册的**类型
    //       （ShanhaiRecipeTypes.java L282/L754 `GTRecipeTypes.register("photon_siphon", ...)`）
    //       ⇒ 它的门槛该保留。
    var NO_GATE_TYPES = { 'primitive_blast_furnace': 1 }

    for (var b = 0; b < items.length; b++) {
        var it = items[b]
        if (it.isModule && catModule) {
            if (NO_GATE_TYPES[typeId]) {
                // 非山海机器：模块当【真催化剂】挂 notConsumable，**不设等级门槛**
                notConsumable.push(it.cnt + 'x ' + it.id)
                continue
            }
            if (moduleGate === null) { moduleGate = it.cnt + 'x ' + it.id; moduleFallback = it.cnt + 'x ' + it.id }
            continue   // 从 itemInputs 里移走（不能两处都写）
        }
        if (it.id === 'gtceu:lv_field_generator' && catField) { notConsumable.push(it.cnt + 'x ' + it.id); continue }
        itemInputs.push(it.cnt + 'x ' + it.id)
    }
    if (catModule && moduleGate === null && !NO_GATE_TYPES[typeId]) {
        flags.push('纸写「物质模块是催化剂」，但该样板里【没有】任何物质模块物品 ⇒ 催化剂无从挂起，本条按"无催化剂"落。')
    }
    if (catField) {
        var hasF = false
        for (var c = 0; c < notConsumable.length; c++) if (/field_generator/.test(notConsumable[c])) hasF = true
        if (!hasF) flags.push('纸写「力场发生器是催化剂」，但该样板里【没有】力场发生器物品 ⇒ 催化剂无从挂起，本条按"无催化剂"落。')
    }

    var inFluids = [], outFluids = [], outItems = [], chanced = []
    for (var d = 0; d < R.fluids.length; d++) inFluids.push(R.fluids[d].d.id + ' ' + R.fluids[d].d.amount)
    for (var e = 0; e < R.outs.length; e++) {
        var o = R.outs[e].d
        if (o.kind === 'FLUID') { outFluids.push(o.id + ' ' + o.amount); continue }
        // #33：纸「电子中微子产出概率5%」写在 out[3]，紧邻 out[2] 的 shanhai:electron_neutrino
        var isChanced = false
        for (var f = 0; f < (R.outNotes || []).length; f++) {
            if (/电子中微子产出概率5%/.test(R.outNotes[f].desc.name) && o.id === 'shanhai:electron_neutrino') isChanced = true
        }
        if (isChanced) { chanced.push({ item: (o.count || 1) + 'x ' + o.id, chance: 500, tierChanceBoost: 100 }); flags.push('纸「电子中微子产出概率5%」⇒ ' + o.id + ' 改成 chancedOutput(500, 100)。'
            + '⚠️ 单位：本包 chance 是【万分比】，10000=100% ⇒ 5% = 500（不是 5000）。'
            + '⚠️ 第二个 int 不是"加成上限"，是【每超频一级的加成量 tierChanceBoost】'
            + '（字节码实证：GTRecipeBuilder.chancedOutput 把 iload_3 写进字段 tierChanceBoost）。'
            + '100 = GTCEu 自己"5% 档副产"的标准值（本包 414 条实际配方 chance=500/boost=100）。'
            + '用户 2026-09-26 裁决：吃加成 ⇒ 第二参不能是 0，本文件取 100。') ; continue }
        outItems.push((o.count || 1) + 'x ' + o.id)
    }

    var circuit = 0
    if (R.circuits.length) {
        circuit = R.circuits[0].n
        if (R.circuits.length > 1) flags.push('该样板有 ' + R.circuits.length + ' 张编程电路，只取了第一张 Configuration=' + circuit)
    }

    var dur = 0
    if (R.time) dur = parseInt(R.time, 10) * 20
    else flags.push('纸上【没有】耗时纸 ⇒ duration 无法确定。')

    var EUt = CELL_EUT[R.cell]
    // 🔴 2026-09-27 用户报的 bug（原话逐字）：「有个bug，土高炉那些配方应该是没有电力要求的，就和原版的炼钢一样」
    //    取证：原版 `gtceu:primitive_blast_furnace` 的配方 JSON【根本没有 EUt 字段】
    //      （样本 `export/recipes/gtceu/primitive_blast_furnace/steel_from_charcoal_block.json`
    //        = {"type":"gtceu:primitive_blast_furnace","duration":16,"inputs":{…}} —— 无 EUt；
    //        原版该类 18 条配方里 EUt 字段【一条都没有】）⇒ 表默认 0 ⇒ 就是"不用电"。
    //    🔴 判据刻意用【类型】而不是 id 列表 ⇒ 用户以后再加土高炉配方也自动对（硬编码 id 会漏）。
    //    ⚠️ 只改 `primitive_blast_furnace`；`primordial_matter_recombination`（_pmr 那批）是【山海的电机】，
    //       不动（用户在裁决中）。
    if (typeId === 'primitive_blast_furnace') {
        if (EUt !== 0) {
            flags.push('🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」\n'
                + '⇒ EUt 由元件默认的 ' + EUt + ' 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。')
        }
        EUt = 0
    }
    if (CELL_EUT_FLAG[R.cell]) flags.push('元件「处理样板-星门」：用户最新裁决（原话逐字）「溢出那就算了，改成 max+8=max,4^8A」\n'
        + '⇒ **MAX+8 = MAX 电压 + 4^8 安培**。\n'
        + '独立验算：V[MAX] = 2147483648（= 2^31，jar 字节码真值）；4^8 = 2^16 = 65536\n'
        + '⇒ EUt = 2^31 × 2^16 = **2^47 = 140737488355328**，只有 Long.MAX（2^63−1）的 1/65536 ⇒ **不溢出**。\n'
        + '⚠️ 上一版口径（4^16）算出 2^63 = Long.MAX+1 ⇒ 回绕成负数，那正是当时只写 V[MAX] 的原因；本版已解除。\n'
        + '完整算式、字节码取证与作废留档见文件头 §8。')
    if (UNRESOLVED_TYPE[R.type]) flags.push('配方类型中文名「' + R.type + '」在 lang 里【没有精确命中】，暂用近似候选 `gtceu:' + typeId + '`（lang 实为「世线板电路组装」），待用户裁决。')

    // 槽位核对（用字节码/源码里的 setMaxIOSize 实测值）
    var cap = CAP[typeId] || [99, 99, 99, 99]
    var slotIn = itemInputs.length + notConsumable.length + (circuit > 0 ? 1 : 0)
    var slotOut = outItems.length + chanced.length
    var over = (slotIn > cap[0]) || (slotOut > cap[1]) || (inFluids.length > cap[2]) || (outFluids.length > cap[3])

    return {
        row: R, type: typeId, srcTypeName: R.type, itemInputs: itemInputs, notConsumable: notConsumable,
        moduleGate: moduleGate, moduleFallback: moduleFallback, circuit: circuit,
        inputFluids: inFluids, itemOutputs: outItems, outputFluids: outFluids, chancedOutputs: chanced,
        duration: dur, EUt: EUt, flags: flags,
        slots: { itemIn: slotIn, itemOut: slotOut, fluidIn: inFluids.length, fluidOut: outFluids.length, cap: cap, over: over }
    }
}

// ---------------------------------------------------------------- split old / new
var newRows = [], oldRows = []
for (var i = 0; i < rows.length; i++) { if (rows[i].old) oldRows.push(rows[i]); else newRows.push(rows[i]) }
if (oldRows.length !== 8) throw new Error('old count != 8')
// ⚠️ 原为 if (newRows.length !== 30) throw ... —— 用户 2026-09-26 裁定：降级成【打印警告】
//    理由：用户会持续往 PF.txt 加样板，硬断言会把每次同步都拦死；但期望值仍要打印出来做对照
var EXPECTED_NEW = 30
if (newRows.length !== EXPECTED_NEW) {
  console.info('[PF] 条数变化: ' + EXPECTED_NEW + ' -> ' + newRows.length + '  (old=' + oldRows.length + ', 合计=' + (oldRows.length + newRows.length) + ')')
} else {
  console.info('[PF] 新增条数 = ' + newRows.length + ' (与期望一致)')
}

// ---------------------------------------------------------------- recipe ids
var used = { 'shanhai:pf/primordial_omega_engine': 1, 'shanhai:pf/photon': 1, 'shanhai:pf/first_light': 1 }
function mkId(spec) {
    var base = null
    for (var k = 0; k < spec.itemOutputs.length; k++) {
        var m = /^(\d+)x ([a-z0-9_]+):([a-z0-9_\/]+)$/.exec(spec.itemOutputs[k])
        if (m) { base = m[3]; break }
    }
    if (!base) base = spec.type + '_' + spec.row.no
    // 🔴 2026-09-27：pmr 副本（土的工炉⇒原初物质重组那批）加 _pmr 后缀，一眼能认出是副本；
    //   其余一律不加（用户口径：老配方 id 不动）。撞车时下面 while 再补 _2。
    var id = 'shanhai:pf/' + base + ((spec.row && spec.row.copiedFrom) ? '_pmr' : '')
    var n = 2
    while (used[id]) { id = 'shanhai:pf/' + base + '_' + n; n++ }
    used[id] = 1
    return id
}
var specs = []
for (var wi = 0; wi < newRows.length; wi++) { var sp = specFromRow(newRows[wi]); if (!sp) continue; sp.id = mkId(sp); specs.push(sp) }

// ─────────────────────────────────────────────────────────────────────────────
// 🔴 2026-09-27 用户逐字点单：「给铁锭变成锻铁锭的那两个配方都加上编程电路，
//    其中土高炉1号电路，原初物质重组改为30号电路」
//    🔴 判据刻意用【id】精确匹配 ⇒ 【绝不写成"类型级"规则】
//      （类型级会波及同族：土高炉另外 13 条、pmr 另外 13 条）
//      · 土高炉 14 条现在全是 circuit 0 ⇒ 改完只有 wrought_iron_ingot 是 1，其余 13 条仍必须 0
//      · pmr 14 条现在全是 circuit 31（来自 RE_TYPE_COPIES）⇒ 改完只有 _pmr 那条是 30，其余 13 条仍必须 31
//    ⚠️ 放在 mkId 之后 —— 因为判据用的是最终 id（_pmr 后缀由 mkId 按 copiedFrom 加）。
// ─────────────────────────────────────────────────────────────────────────────
var CIRCUIT_BY_ID = {
    'shanhai:pf/wrought_iron_ingot': 1,
    'shanhai:pf/wrought_iron_ingot_pmr': 30
}
var circuitPatchLog = []
for (var cp = 0; cp < specs.length; cp++) {
    var cid = specs[cp].id
    if (Object.prototype.hasOwnProperty.call(CIRCUIT_BY_ID, cid)) {
        circuitPatchLog.push(cid + ' circuit ' + specs[cp].circuit + ' -> ' + CIRCUIT_BY_ID[cid])
        specs[cp].circuit = CIRCUIT_BY_ID[cid]
    }
}
console.log('[PF-CIRCUIT] 按 id 覆写编程电路 ' + circuitPatchLog.length + ' 条： ' + circuitPatchLog.join(' | '))

// ---------------------------------------------------------------- emit KJS
var L = []
function w(s) { L.push(s === undefined ? '' : s) }

w('// priority: 1')
w('// =============================================================================')
w('// [server_scripts]shanhai_recipes.js  —— 用户「游戏里编好的 AE 样板」落地为 KubeJS')
w('//')
w('// ✅ 2026-09-26 【终版 · FINAL】：本文件原先挂着的问题【三处全部落定】——')
w('//    ① 新类型「原初物质解构」= `gtceu:primordial_matter_deconstruction`（用户裁决「就用这个」）；')
w('//    ② `photon_separation` 的 setMaxIOSize 由 (2,4,2,2) **放宽到 (4,10,2,2)**（用户裁决：物品入 2→4、物品出 4→10、流体 2/2 不动）；')
w('//    ③ 星门 3 条的 EUt 按「**MAX+8** = MAX 电压 + 4^8 A」= **2^47 = 140737488355328**（用户裁决）。')
w('//    ⇒ 后果：**2026-09-26 放宽后不再溢出** —— 原先那 3 条 SLOT-OVER 溢出**已消除**，')
w('//       代际产物里**既没有 `slotOver` 字段、也没有 SLOT-OVER 告警代码**（整段已删）。')
w('//    ✅ 2026-09-27：【已部署】到 GTL山海9.10test\\kubejs\\server_scripts\\')
w('//    ⚠️ 「原初物质解构」这个配方类型是**本版 shanhai 模组新增**的（jar 侧已注册 + 有专属 .rtui），')
w('//       本文件没有也不需要有它的配方；它只是让 JEI/机器多出一个可用分类。')
w('//')
w('// 🔴 本次是【增量更新】（用户原话逐字：「这次添加配方就和上次一样啊，上次我还帮你理解了」')
w('//    ／「每个元件都有新增的配方」）：')
w('//    · 老那 8 条【原样保留、一个字不改】（它们的 spec 从上一版文件逐字搬过来）；')
w('//    · 新增 30 条【追加】进同一个文件。')
w('//    ⇒ 实测（2026-09-27 运行期）：工作台 18 条 + GT 机器 61 条 = 79 条。')
w('//')
w('// 源数据：C:\\Users\\david\\Desktop\\PF.txt')
w('//   SHA256 = 086D2F5E83DB2E390EF42E473EEC7FCE046269BAE691AF17FDC0CBAC83A730F2')
w('//   41,564 B / 单行 NBT / 一个 minecraft:chest')
w('//')
w('// =============================================================================')
w('// §0 检查器自证（先说清"我凭什么信自己没看漏"）')
w('// =============================================================================')
w('//  · `ae2:processing_pattern` 纯文本出现次数 = 38')
w('//  · 逐条解析成功 = 38     ⇒ 两边相等，无静默漏条')
w('//  · `in` 恒为 81 格 / `out` 恒为 27 格（AE2 定长数组，空槽是 `{}`）—— 79/79 条都对')
w('//  · 正面对照：解析出的 8 条旧配方与上一版文件头 §3 的记录【逐字一致】')
w('//    ⇒ 说明解析器看的是同一批东西（这是"解析器自己是对的"的证据，不是自说自话）')
w('//')
w('// =============================================================================')
w('// §1 元件对照（这次 vs 上次）—— 用户要的第一问')
w('// =============================================================================')
w('//   本次 Slot | 元件名（display.Name 逐字） | 本次条数 | 上次 | 差')
w('//   ----------|---------------------------|---------|------|----')
w('//   Slot 0    | 处理样板-星门(MAX+16)      |    3    | 无   | 全新元件')
w('//   Slot 1    | 合成样板                   |    5    | 17   | +12 ← 2026-09-27 改为从 PF.txt 全量生成')
w('//   Slot 2    | 处理样板ULV                |   19    | 1    | +18')
w('//   Slot 3    | 处理样板LV                 |   11    | 2    | +9')
w('//   ----------|---------------------------|---------|------|----')
w('//   合计      |                            |   38    | 8    | +30')
w('//')
w('//  ⚠️ 与用户口径的一处出入（如实报，不顺着说）：用户说「每个元件都有新增的配方」，')
w('//     ⚠️ 2026-09-27 更正：本句【已过期】。用户更新 PF.txt 后合成样板 = 17 条，')
w('//  ⚠️ 元件【槽位变了】：上次 Slot 0 = 处理样板ULV、Slot 2 = 处理样板LV；')
w('//     这次整体后移（ULV→Slot 2，LV→Slot 3），Slot 0 换成了新元件。')
w('//     判据：条数与内容吻合（ULV 里仍含旧 ①、LV 里仍含旧 ⑦⑧）⇒ 是同一个元件被挪了槽，不是新元件。')
w('//')
w('// =============================================================================')
w('// §2 中文名 → `gtceu:<id>` 映射（口径沿用上次：lang 里【唯一精确等于】那条）')
w('// =============================================================================')
w('//   纸上中文名     | gtceu id                            | 来源          | 条数')
w('//   ---------------|-------------------------------------|---------------|-----')
w('//   电路组装机     | gtceu:circuit_assembler             | gtceu zh_cn   |  1')
w('//   土高炉         | gtceu:primitive_blast_furnace       | gtceu zh_cn   | 14')
w('//   量子化现实重构 | gtceu:spacetime_distortion          | shanhai zh_cn |  3')
w('//   原初奇点反演   | gtceu:primordial_singularity_inversion | shanhai zh_cn | 3')
w('//   物质流凝结     | gtceu:matter_flow_condensation      | shanhai zh_cn |  2')
w('//   物质模块铸造   | gtceu:matter_module_casting         | shanhai zh_cn |  2')
w('//   光子虹吸       | gtceu:photon_siphon                 | shanhai zh_cn |  2')
w('//   光子分离       | gtceu:photon_separation             | shanhai zh_cn |  3')
w('//   星际物质吸取   | gtceu:interstellar_matter_absorption| shanhai zh_cn |  1')
w('//   ✅世线电路板组装| gtceu:wl_board_circuit_assembly       | shanhai zh_cn |  2')
w('//')
w('//  ✅ 已消除的唯一歧义：纸上写「世线电路板组装」，lang 里是「世线板电路组装」——')
w('//     用户 2026-09-26 亲自裁决：「我写错了」⇒ 按 lang 的 `gtceu:wl_board_circuit_assembly` 落，')
w('//     原先的 ⚠️ 待裁决标记已按用户裁决【移除】。')
w('//')
w('// =============================================================================')
w('// §3 配方类型 id 的存在性核实（正面对照，不是猜）')
w('// =============================================================================')
w('//  · 8 个 shanhai 类型：解 `mods\\shanhai-0.1.0.jar` 的')
w('//      `com/shanhai/common/recipe/ShanhaiRecipeTypes.class` 常量池，**逐个命中**：')
w('//      spacetime_distortion / primordial_singularity_inversion / matter_flow_condensation /')
w('//      matter_module_casting / photon_siphon / photon_separation /')
w('//      interstellar_matter_absorption / wl_board_circuit_assembly  ⇒ **8/8 在**。')
w('//  · gtceu:primitive_blast_furnace：游戏自己的导出表')
w('//      `local\\kubejs\\export\\recipes\\gtceu\\primitive_blast_furnace\\` = **18 个配方文件**')
w('//      ⇒ 这个配方类型在本包里**真实存在且有配方**。')
w('//  · gtceu:circuit_assembler：同目录下有 **90** 个配方文件。')
w('//  · ⚠️ 反例（防"假否定"）：`local\\kubejs\\export\\registries\\item.json` 时间戳 = 2026-09-10 19:19，')
w('//      **早于** shanhai-0.1.0.jar（2026-09-26 11:11）⇒ 那张导出表里 `gtceu:spacetime_distortion` ')
w('//      之类的目录**查不到**。这【不是"不存在"】，是"导出表过时"。')
w('//      ⇒ 所以 shanhai 类型一律改用【已部署 jar 的字节码/lang】取证，不看那张旧表。')
w('//')
w('// =============================================================================')
w('// §4 槽位核对（用 jar 里【真的】setMaxIOSize 值，不是估的）')
w('// =============================================================================')
w('//  shanhai 类型：ShanhaiRecipeTypes.java 的 .setMaxIOSize(物品入,物品出,流体入,流体出)：')
for (var q = 0; q < Object.keys(CAP).length; q++) {
    var tn = Object.keys(CAP)[q]
    w('//     ' + (tn + '                                  ').substring(0, 34) + ' = (' + CAP[tn].join(', ') + ')')
}
w('//  · gtceu:primitive_blast_furnace = (3, 3, 0, 0)')
w('//      —— 出处：gtceu jar `GTRecipeTypes.class` 字节码偏移 3028-3032：')
w('//         iconst_3 / iconst_3 / iconst_0 / iconst_0 → setMaxIOSize')
w('//      ⚠️ 流体入=0 流体出=0 ⇒ 土高炉配方【不许带流体】，本文件 14 条土高炉确实一条流体都没有 ✓')
w('//      ⚠️ 18 条现存 primitive_blast_furnace 配方的实测最大值是 (2,2,0,0)（没填满 3）')
w('//  · gtceu:circuit_assembler = (6, 1, 1, 0)（上一版已核，出处见上一版 §1）')
w('//')
w('//  🔴 三条占用规则（沿用上次口径，**没变**）：')
w('//     · `.notConsumable(...)`【占】1 个物品输入槽（和普通输入一样算）')
w('//     · `.circuit(N)`【占】1 个物品输入槽')
w('//     · 物质模块【等级门槛】不占槽（它是准入判据，不是输入物）')
w('//  ⇒ 每条新增配方都算过 slotIn / slotOut，逐条结果见 §7 的表。')
w('//')
w('//  🔴🔴 槽位核对【曾查出 3 条溢出】—— ✅ **本版已由用户裁决解决**：')
w('//')
w('//    PF.txt#  | 配方 id 后缀               | 类型               | 物品入 | 旧 cap | 新 cap')
w('//    ---------|---------------------------|--------------------|--------|--------|-------')
w('//    #31      | shanhai:pf/photon_2       | photon_separation  |   3    |  2 ❌  |  4 ✓')
w('//    #33      | shanhai:pf/electron       | photon_separation  |   3    |  2 ❌  |  4 ✓')
w('//    #36      | shanhai:pf/photon_rainbow | photon_separation  |   3    |  2 ❌  |  4 ✓')
w('//')
w('//    三条都是同一形状：1~2 个真物品 + 1 个 notConsumable(力场发生器) + 1 个 .circuit(1) = 3 格，')
w('//    而旧 photon_separation 的 setMaxIOSize 是 (2,4,2,2) ⇒ 物品入只有 2 格。')
w('//    🔴 用户 2026-09-26 裁决（逐字）：「photon_separation 的 setMaxIOSize(2, 4, 2, 2) ⇒ (4, 10, 2, 2)」')
w('//       ⇒ 物品入 2 ⇒ **4**（3 格装得下，留 1 格余量）、物品出 4 ⇒ **10**；**流体 2/2 不动**。')
w('//    ⚠️ 旧 cap (2,4,2,2) 是从【当时已部署的 jar】字节码读出来的真值：')
w('//       mods\\shanhai-0.1.0.jar!com/shanhai/common/recipe/ShanhaiRecipeTypes.class')
w('//       photon_separation 段：iconst_2 / iconst_4 / iconst_2 / iconst_2 → setMaxIOSize')
w('//    ✅ 新 cap (4,10,2,2) 是【本版 jar】的真值，出处 = javap -c 成品 jar 的 ShanhaiRecipeTypes.init()：')
w('//       该调用点操作数 = iconst_4 / bipush 10 / iconst_2 / iconst_2（2026-09-26 实测）。')
w('//    ⇒ 2026-09-26 放宽后不再溢出 ⇒ 这 3 条的 `slotOver` 标记与整段 SLOT-OVER 告警【已删除】。')
w('//    ⚠️ 附注：若把物质模块从"等级门槛"降级回"催化剂"（§7① 的另一条路），这三条会变成 4 格 ——')
w('//       正好用满 cap[0]=4，仍然装得下（旧 cap=2 时才是真的装不下）。')
w('//')
w('// =============================================================================')
w('// §5 KubeJS / Rhino 写法纪律（与上一版完全一致，刻意只用最保守的写法）')
w('// =============================================================================')
w('//  · 全局只用 var；不用 let/const、不用箭头函数、不用模板字符串、不用 ?.、不用解构')
w('//  · 不用 conditions（assembler 类配方设它会报错）')
w('//  · 编程电路用 .circuit(数字)——照宿主现成写法（gtceu.js:3301-3303 / ae2.js:361-362）')
w('//  · 「不消耗（催化剂）」用 .notConsumable(...)，照宿主 gtceu.js:935 / 3302 / 9955')
w('//  · 每条配方各自包 try/catch —— 一条失败只让一条失败，不连坐')
w('//  · 配方 id 全部显式给死（.id(...)），避免 /kubejs reload 时自动 id 变化导致旧配方残留')
w('//')
w('// =============================================================================')
w('// §6 🔴 改名纸 = 注记，不是配方输入（沿用上次口径）')
w('// =============================================================================')
w('//  ⇒ 本文件【任何地方都不出现 minecraft:paper】—— 除非它是**没改名的真产物**')
w('//    （本次确实有一条：#25「甘蔗 → 2x minecraft:paper」是真的出纸，那条保留）。')
w('//  · 纸有三种：')
w('//      ① 「配方类型：XXX」（也有裸写类型名的，如「光子虹吸」「电路组装机」）⇒ 决定用哪台机器')
w('//      ② 「Ns」⇒ 决定耗时（×20 = tick）')
w('//      ③ 备注（本次三种：物质模块是催化剂 / 力场发生器是催化剂 / 电子中微子产出概率5%）')
w('//  · 🔴 纸写在 in 里，也可能写在 **out** 里 —— #33 的「电子中微子产出概率5%」就在 out[3]。')
w('//    本文件把"带自定义名的纸"从 out 里剔除，只留真产物。')
w('//')
w('// =============================================================================')
w('// §7 🔴 三处口径（**先报出来，没自己选**）：')
w('// =============================================================================')
w('//  ①「物质模块是催化剂」—— 27 条样板里有这张纸。')
w('//     ✅ 用户 2026-09-26 裁决（原话逐字）：「以后物质模块是催化剂指的都是我们今天刚写好的机制」')
w('//     ⇒ 「物质模块是催化剂」= 今天刚做好的【等级门槛 ModuleLevelCondition】，不再是老写法。')
w('//       · 新机制（等级门槛，**唯一口径**）： .addCondition(new ModuleLevelCondition(\'shanhai:<模块>\', 1))  ← 不占槽')
w('//       · 老写法（催化剂）： .notConsumable(\'Nx shanhai:<模块>\')                                  ← 占 1 槽')
w('//     · 已取证：`mods\\shanhai-0.1.0.jar` 里 **存在** ')
w('//       `com/shanhai/machine/module/ModuleLevelCondition.class` ⇒ 上一版"待落地"的状态已结束。')
w('//     · 本文件的做法：**默认走等级门槛**；`SHANHAI_PF_MODULE_MODE` 一行可切。')
w('//       ⚠️ 降级通道【保留】（jar 没绑 / `typeof` 判不到类时自动退回催化剂，配方不会消失）。')
w('//     · ⚠️ 另有 3 条（PF.txt #1/#2/#3）有这张纸却【没有物质模块物品】⇒ 门槛无从挂起，')
w('//       本文件按"无门槛无催化剂"落，并在脚本里逐条注明。')
w('//  ②「力场发生器是催化剂」—— 3 条（#31/#33/#36），每条同格就有 `gtceu:lv_field_generator`。')
w('//       本文件按 `.notConsumable(\'1x gtceu:lv_field_generator\')` 落。**待确认电压档（LV？）**')
w('//  ③「电子中微子产出概率5%」—— 1 条（#33），且写在该样板的 **out[3]**，紧邻 out[2] 的')
w('//       `shanhai:electron_neutrino`。')
w('//     ✅ 用户 2026-09-26 裁决（原话逐字）：「吃加成」')
w('//     ⇒ 本文件落 `.chancedOutput(\'1x shanhai:electron_neutrino\', 500, 100)`')
w('//')
w('//     🔴 参数 1：**单位是万分比**（`getMaxChancedValue()` 反读 = `sipush 10000`）')
w('//        ⇒ **5% = 500**，不是 5000（5000 = 50%，会差 10 倍）。')
w('//        取证：宿主 gtceu.js:6148/6212/6250 (2000,0)=20% ／ :8405 (1000,0)=10% ／')
w('//              :6717 (200,20)=2% ／ ad.js:96 (5000,0)=50%；')
w('//              且游戏导出表里 chance 的最大值就是 10000。')
w('//')
w('//     🔴 参数 2：**它不是"加成上限"，是【每超频一级的加成量 tierChanceBoost】**。')
w('//        字节码实证 `GTRecipeBuilder.chancedOutput(ItemStack,int,int)`：')
w('//          67: aload_0 / 68: iload_2 / 69: putfield chance:I')
w('//          72: aload_0 / 73: iload_3 / 74: putfield tierChanceBoost:I     <-- 第三个参数进这里')
w('//        ⚠️ `maxChance` 从 KubeJS 侧【设不了】，它保持默认 10000。')
w('//')
w('//     🔴 **100 这个数是从哪来的（不是猜的）**：GTCEu 自己的"5% 档副产"标准值。')
w('//        反读游戏导出表 93,897 个文件、348,092 条 chanced 记录后，')
w('//        `chance=500 / maxChance=10000 / tierChanceBoost=100` 这个三元组出现 **414 次**，')
w('//        全部来自 GTCEu 的矿石副产线：`gtceu:macerator` 138 ／ `gtceu:integrated_ore_processor` 138 ／')
w('//        `gtceu:space_ore_processor` 138。⇒ 这就是本包"5% 且带加成"的标准配法。')
w('//        （同族还有 1400/850、200/20、50/5 —— 即 GTCEu 的 14% / 5% / 2% / 0.5% 副产阶梯。）')
w('//')
w('//     🔴 **加成到底怎么算**（`ChanceBoostFunction.OVERCLOCK` 反读，逐条对字节码）：')
w('//          int tierDiff = machineTier - recipeTier;')
w('//          if (tierDiff <= 0) return chance;          // 没超频 ⇒ 原始概率，吃不到加成')
w('//          if (recipeTier == 0) tierDiff = tierDiff - 1;')
w('//          return chance + tierChanceBoost * tierDiff;')
w('//        ⇒ #33 的 recipeTier = LV(1)，实际概率随机器超频：')
w('//            LV(1) → 5% ／ MV(2) → 6% ／ HV(3) → 7% ／ EV(5) → 9% ／ MAX(14) → 18%')
w('//          （公式里没有封顶，但 `maxChance` = 10000 = 100% 就是天花板）')
w('//')
w('// =============================================================================')
w('// §8 🔴 元件「处理样板-星门」的 EUt —— 算式 + 溢出判断')
w('//    （2026-09-26 第二次改判：MAX+16 ⇒ **MAX+8**）')
w('// =============================================================================')
w('//  ✅ 用户最新裁决（原话逐字）：「溢出那就算了，改成 max+8=max,4^8A」')
w('//     ⇒ 电压 = MAX 档，电流 = **4^8 A**。')
w('//     ⚠️ 本段【覆盖】上一版「MAX+16=MAX，4^16A」的裁决；上一版的算式与结论')
w('//        按本工程惯例【原样保留在 ⑥ 作留档】，但不再是当前口径。')
w('//')
w('//  ① V[MAX] 的真值 —— 【从字节码读的，不是从注释抄的】')
w('//     `libs\\gtceu-1.20.1-1.4.4.jar!com/gregtechceu/gtceu/api/GTValues.class`（javap -p -c）')
w('//     该 class 的 sha256 = 7A7275B8018D78876D2F1AB3D6B14644D7D5145EDBC16D35C3B9D99146D33375')
w('//     `<clinit>`：`bipush 14` → `ldc2_w // long 2147483648l` → `lastore` → `putstatic V:[J`')
w('//     V 是 **15 项 long[]**，索引 0..14 = ULV..MAX（索引 13 = 536870912）；同文件 VN[14] = `"MAX"`')
w('//     ⇒ **V[MAX] = 2147483648（= 2^31）**')
w('//     ⚠️ 注意：**不是** Integer.MAX_VALUE(2147483647)，也**不是** 1.7 时代注释里那种写法 ——')
w('//        这个数字如果照注释猜，整条算式的答案会差一位。')
w('//')
w('//  ② 4^8 的值')
w('//     4^8 = (2^2)^8 = **2^16 = 65536**')
w('//')
w('//  ③ 算式与结果（精确整数运算，不是浮点）')
w('//     EUt = V[MAX] × 4^8')
w('//         = 2147483648 × 65536')
w('//         = 2^31 × 2^16')
w('//         = **2^47 = 140737488355328**')
w('//')
w('//  ④ 🔴 溢出判断：**【不溢出】✓**（这是本次改判的全部目的）')
w('//        Long.MAX_VALUE = 2^63 − 1 = 9223372036854775807')
w('//        2^47 ≈ 1.41e14 ⇒ 只有 Long.MAX 的 **1/65536**（余量 65536 倍）')
w('//        ⇒ 2^47 是合法 long，**不存在回绕**。')
w('//        `.EUt` 的签名已核实：`GTRecipeBuilder EUt(long)` —— 是 long，不是 int，2^47 装得下。')
w('//')
w('//  ⑤ ⇒ 本文件的落地：')
w('//        **EUt = 140737488355328**（= V[MAX] × 4^8 = 2^47）')
w('//        —— "电压 × 电流"这一次可以**整体**进 EUt，不再需要像上一版那样拆开。')
w('//        🔴 事实提示（不是拦阻、也不是待裁决项）：2^47 EUt/t = **65536 倍 MAX 电压**。')
w('//           配方的 EUt 是"每 tick 电压需求"，GT 侧要能找到供得起这个数的能源仓它才跑得起来。')
w('//           这是数值/玩法层面的事；本条只负责"算式不溢出、写进去的值不违法"。')
w('//        ⚠️ 这三条配方所在的**元件名**仍写作「处理样板-星门(MAX+16)」——')
w('//           那是 AE2 样板自己的**显示名**（PF.txt 源数据原文），本次不要求改样板，')
w('//           故文件里保持原样；本段只改这 3 条配方的 EUt 值。')
w('//')
w('//  ⑥ 📌 留档：**已作废的上一版口径**（2026-09-26 早先裁决「MAX+16=MAX，4^16A」）')
w('//     4^16 = 2^32 = 4294967296')
w('//     EUt = 2^31 × 2^32 = 2^63 = 9223372036854775808 = Long.MAX_VALUE **+ 1**')
w('//     ⇒ **恰好越界 1** ⇒ Java long 二进制补码回绕成 Long.MIN_VALUE = −9223372036854775808（负数！）')
w('//     ⇒ 上一版因此只能把 EUt 写成 V[MAX] = 2147483648（只放电压部分），安培不放进 EUt。')
w('//     ⇒ 改成 4^8 后该约束消失，故本版把 2^47 整体写进 EUt。')
w('//')
w('//  · 「合成样板」的 5 条是工作台配方（event.shaped，有序），摆位 = in 下标 0..8 行优先 ——')
w('//      这是上次已由用户核对确认的口径（原话「这下对了」），本次**一个字没改**。')
w('//')
w('// =============================================================================')

w('var SHANHAI_PF_TAG = \'[SHANHAI-PF]\'')
w('')
w('// 🔴 §7① 的切换点：\'gate\' = 物质模块等级门槛（默认）／ \'catalyst\' = 老写法（不消耗催化剂）')
w('var SHANHAI_PF_MODULE_MODE = \'gate\'')
w('')
w('// 老山海 module_level 条件类是否已由 jar 侧注册并绑定（见 §7①）。')
w('// ⚠️ 必须用 typeof 判：直接引用未绑定的标识符会 ReferenceError，而 typeof 不会抛。')
w('var SHANHAI_HAS_MODULE_LEVEL_CONDITION = (typeof ModuleLevelCondition !== \'undefined\')')
w('')
w('// 解析老山海写法 "Nx <物品id>" -> [物品id, 数量]（无 Nx 时数量默认 1）。')
w('// 逐句对齐 DShanhaiRecipeEngine.addOneCondition 的解析口径（照上一版搬）。')
w('var shanhaiParseModuleLevel = function (text) {')
w('    var s = String(text).trim()')
w('    var count = 1')
w('    var id = s')
w('    var xi = s.indexOf(\'x\')')
w('    var c0 = s.length > 0 ? s.charCodeAt(0) : 0')
w('    if (xi > 0 && c0 >= 48 && c0 <= 57) {')
w('        count = parseInt(s.substring(0, xi), 10)')
w('        if (isNaN(count) || count <= 0) { count = 1 }')
w('        id = s.substring(xi + 1).trim()')
w('    }')
w('    return [id, count]')
w('}')
w('')
w('// 该走等级门槛吗？（模式 = gate  且  jar 侧条件类已绑定）')
w('var shanhaiUseLevelGate = function (r) {')
w('    if (SHANHAI_PF_MODULE_MODE !== \'gate\') { return false }')
w('    if (!r.moduleLevelRequirement) { return false }')
w('    return SHANHAI_HAS_MODULE_LEVEL_CONDITION')
w('}')
w('')

// ---- shaped (5, verbatim from previous version) ----
w('// -----------------------------------------------------------------------------')
w('// 老 ①..⑧ 里的 ②③④⑤⑥：工作台配方（有序 shaped）')
w('// 🔴 与上一版【逐字相同】，本次未改动（用户口径：老 8 条保留不动）')
w('// 摆位 = PF.txt 该样板 in 数组下标 0..8 按【行优先】')
w('// -----------------------------------------------------------------------------')
w('var shanhaiPfShaped = [')
var SHAPED = [
    { id: 'shanhai:pf_crafting/primordial_divergence_generator', out: 'shanhai:primordial_divergence_generator', pattern: ['ABA', 'BCB', 'ABA'], keys: { A: 'gtceu:primitive_void_ore', B: 'gtceu:ulv_fragment_world_collection_machine', C: 'shanhai:primordial_engine_core' } },
    { id: 'shanhai:pf_crafting/wl_board_ulv', out: 'shanhai:wl_board_ulv', pattern: ['ABA', 'BCB', 'ABA'], keys: { A: 'gtceu:pulsating_alloy_ingot', B: 'gtceu:certus_quartz_gem', C: 'kubejs:ulv_universal_circuit' } },
    { id: 'shanhai:pf_crafting/primordial_engine_core', out: 'shanhai:primordial_engine_core', pattern: ['PRP', 'MTB', 'AGA'], keys: { P: 'gtceu:spacetime_tiny_fluid_pipe', R: 'gtlcore:primitive_fluid_regulator', M: 'thetornproductionline:celestial_secret_deducing_module_ulv', T: 'gtlcore:treasures_crystal', B: 'shanhai:wl_board_ulv', A: 'gtlcore:primitive_robot_arm', G: 'ae2:fluix_glass_cable' } },
    { id: 'shanhai:pf_crafting/introductory_material_module', out: 'shanhai:introductory_material_module', pattern: ['CRD', 'BTB', 'DPC'], keys: { C: 'gtlcore:mining_crystal', R: 'kubejs:reactor_core', D: 'minecraft:diamond', B: 'shanhai:wl_board_ulv', T: 'gtlcore:treasures_crystal', P: 'gtceu:spacetime_tiny_fluid_pipe' } },
    { id: 'shanhai:pf_crafting/primordial_matter_caster', out: 'shanhai:primordial_matter_caster', pattern: ['LSL', 'SKS', 'LSL'], keys: { L: 'thetornproductionline:celestial_secret_deducing_module_lv', S: 'ae2:molecular_assembler', K: 'shanhai:primordial_engine_core' } },
    // 🔴 2026-09-27 用户裁定「补回去」：工业蒸汽机械方块的工作台配方。
    //    背景：用户报「JEI 里没显示」⇒ 查明它在 09-10 的 export 快照里存在（id = minecraft:kjs/gtceu_industrial_steam_casing，
    //    即 KubeJS 给【无显式 id 配方】自动生成的形态），但【产生它的代码在现存的任何文件里都搜不到】
    //    （kubejs 全目录 / 全部 90 个 mod jar 的 data/**/recipes / kubejs\data / 存档 datapacks 全 0 命中；
    //     GTL原版 09-24 快照也没有 ⇒ 不是原版自带）。⇒ 问用户后他选「补回去」。
    //    ✅ 形状与 key 全部【照抄 09-10 快照原文】，未重新设计：
    //       pattern ["AAA","BCA","DEF"]，A=bronze_plate B=#forge:tools/hammers C=bronze_frame
    //       D=bronze_rotor E=bronze_gear F=bronze_rotor
    //    ✅ tag 已核实有效：export\tags\minecraft\item\forge\tools\hammers.json = 26 项（含 gtceu:bronze_hammer）
    //    ⚠️ KJS 里 tag 必须写前缀 '#' ⇒ '#forge:tools/hammers'。
    { id: 'kubejs:industrial_steam_casing', out: 'gtceu:industrial_steam_casing', pattern: ['AAA', 'BCA', 'DEF'], keys: { A: 'gtceu:bronze_plate', B: '#forge:tools/hammers', C: 'gtceu:bronze_frame', D: 'gtceu:bronze_rotor', E: 'gtceu:bronze_gear', F: 'gtceu:bronze_rotor' } }
]
// ══════════ 从 rows.json 的合成样板【全量生成】工作台配方（2026-09-27 用户点单）══════════
//   起因：用户问「你是不是没更新工作台的配方」—— 他问对了。原来 SHAPED 是手写的 5 条，
//   【完全不读 PF.txt】，所以 PF.txt 里 17 条合成样板只发了 5 条，新增的
//   shanhai:worldline_cracking_hub 一条都没进游戏。
//   ✅ 判据：cell === '合成样板' 且 type 为空（没有 GT 配方类型 = 纯工作台合成）且非 old。
//   ✅ 形状：rows.json 的 real[].slot 是 0..8 的【行优先格位】（parse_pf 保留了格位），
//      按 slot 还原 3×3；空格 = 空格 ' '。输出是 1 格 ⇒ 用 event.shaped（与老 5 条同口径）。
//   ✅ 查重：与硬编码那 5 条按【产出物品 id】比对，重复的不再生成。
//   ✅ 老 5 条【保留不动】（用户口径"老 8 条保留不动"），新的【追加】在后面。
var SHAPED_DUP = {}
for (var q0 = 0; q0 < SHAPED.length; q0++) SHAPED_DUP[SHAPED[q0].out] = 1
var SHAPED_SKIP = []
var SHAPED_KEYS_LETTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
for (var q1 = 0; q1 < rows.length; q1++) {
    var RQ = rows[q1]
    if (RQ.cell !== '合成样板') continue
    if (RQ.type) continue
    if (RQ.old) continue
    var OQ = RQ.outs || []
    if (OQ.length !== 1) { SHAPED_SKIP.push('no=' + RQ.no + ' 产出不是 1 种'); continue }
    var od = OQ[0].d || OQ[0]
    if (!od.id) { SHAPED_SKIP.push('no=' + RQ.no + ' 产出无 id'); continue }
    if (SHAPED_DUP[od.id]) { SHAPED_SKIP.push('no=' + RQ.no + ' ' + od.id + ' 与硬编码重复'); continue }
    var g9 = [' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ']
    var filled = 0
    for (var q2 = 0; q2 < RQ.real.length; q2++) {
        var itq = RQ.real[q2]
        var dq = itq.d || itq
        var sl = itq.slot
        if (sl === undefined || sl === null || sl < 0 || sl > 8) continue
        if (!dq.id) continue
        g9[sl] = dq.id
        filled = filled + 1
    }
    if (filled === 0) { SHAPED_SKIP.push('no=' + RQ.no + ' ' + od.id + ' 9 格全空'); continue }
    var keysQ = {}
    var mapQ = {}
    var liQ = 0
    var patQ = ['', '', '']
    for (var r9 = 0; r9 < 3; r9++) {
        for (var c9 = 0; c9 < 3; c9++) {
            var v9 = g9[r9 * 3 + c9]
            if (v9 === ' ') { patQ[r9] = patQ[r9] + ' '; continue }
            if (!mapQ[v9]) { if (liQ >= 26) { mapQ[v9] = '?'; } else { mapQ[v9] = SHAPED_KEYS_LETTERS.charAt(liQ); liQ = liQ + 1 } }
            if (mapQ[v9] === '?') { patQ[r9] = patQ[r9] + ' '; continue }
            keysQ[mapQ[v9]] = v9
            patQ[r9] = patQ[r9] + mapQ[v9]
        }
    }
    var outQ = ((od.count || 1) > 1 ? (od.count + 'x ') : '') + od.id
    SHAPED.push({ id: 'shanhai:pf_crafting/' + od.id.split(':')[1], out: outQ, pattern: patQ, keys: keysQ })
    SHAPED_DUP[od.id] = 1
}
console.log('[PF-SHAPED] 硬编码 ' + 5 + ' 条 + 由 PF.txt 生成 ' + (SHAPED.length - 5) + ' 条 ⇒ 合计 ' + SHAPED.length + ' 条')
if (SHAPED_SKIP.length) for (var q3 = 0; q3 < SHAPED_SKIP.length; q3++) console.log('[PF-SHAPED] 跳过 ' + SHAPED_SKIP[q3])

for (var s1 = 0; s1 < SHAPED.length; s1++) {
    var S1 = SHAPED[s1]
    w('    {')
    w('        id: \'' + S1.id + '\',')
    w('        out: \'' + S1.out + '\',')
    w('        pattern: [\'' + S1.pattern.join('\', \'') + '\'],')
    w('        keys: {')
    var kk = Object.keys(S1.keys)
    for (var s2 = 0; s2 < kk.length; s2++) w('            ' + kk[s2] + ': \'' + S1.keys[kk[s2]] + '\'' + (s2 < kk.length - 1 ? ',' : ''))
    w('        }')
    w('    }' + (s1 < SHAPED.length - 1 ? ',' : ''))
}
w(']')
w('')

// ---- GT specs ----
w('// -----------------------------------------------------------------------------')
w('// 老 ①⑦⑧（3 条，与上一版【逐字相同】，本次未改动）+ 新增 30 条')
w('// 每条上面第一行注释 = 溯源：PF.txt 里的序号 / 元件 / 纸')
w('// -----------------------------------------------------------------------------')
w('var shanhaiPfGt = [')
var FIRST = true
function emit(spec, isOld, rawText) {
    if (!FIRST) w(',')
    FIRST = false
    w('    ' + rawText[0])
    for (var i = 1; i < rawText.length; i++) w('    ' + rawText[i])
}

// 老 3 条：逐字搬上一版（内容见 [server_scripts]shanhai_recipes.js 的对应段）
var OLDSPEC = [
    {
        lines: [
            '// ===== 老 ①（上一版逐字，未改）：处理样板ULV / 电路组装机 / 10s =====',
            '{'
        ],
        raw: null
    }
]
// 手工拼老 3 条（照抄上一版文本）
function emitOldGt() {
    var o1 = [
        '{',
        '    id: \'shanhai:pf/primordial_omega_engine\',',
        '    type: \'circuit_assembler\',',
        '    // 老 ① 的 6 项 = circuit_assembler 的物品输入上限 setMaxIOSize(6,1,1,0)，正好用满',
        '    notConsumable: [],',
        '    circuit: 0,',
        '    itemInputs: [',
        '        \'4x gtceu:dimensionally_transcendent_steam_oven\',',
        '        \'4x gtceu:dimensionally_transcendent_dirt_forge\',',
        '        \'1x shanhai:primordial_engine_core\',',
        '        \'16x thetornproductionline:celestial_secret_deducing_module_ulv\',',
        '        \'64x kubejs:precision_steam_mechanism\',',
        '        \'16x gtceu:primitive_void_ore\'',
        '    ],',
        '    inputFluids: [\'gtceu:glue 16000\'],',
        '    itemOutputs: [\'1x shanhai:primordial_omega_engine\'],',
        '    outputFluids: [],',
        '    chancedOutputs: [],',
        '    duration: 200,',
        '    EUt: 8',
        '}'
    ]
    var o2 = [
        '{',
        '    id: \'shanhai:pf/photon\',',
        '    type: \'photon_siphon\',',
        '    // PF.txt 原文该格：programmed_circuit + tag:{Configuration:2}',
        '    circuit: 2,',
        '    // 🔴 用户 2026-09-28 原话：「光子虹吸的配方里面主世界碎片和物质模块都是不消耗的（作为催化剂）」',
        '    //     ⇒ 主世界碎片保留不消耗；物质模块于 2026-09-29 改成"等级 >= 1"的配方门槛（见 §7①）',
        '    notConsumable: [\'1x gtlcore:world_fragments_overworld\'],',
        '    moduleLevelRequirement: \'1x shanhai:introductory_material_module\',',
        '    // ⚠️ 降级用：条件类不可用时退回改动前的催化剂形态（宁可比原来差，也不能让配方消失）',
        '    moduleLevelFallbackCatalyst: \'1x shanhai:basic_material_module\',',
        '    itemInputs: [],',
        '    inputFluids: [],',
        '    itemOutputs: [\'16x shanhai:photon\'],',
        '    outputFluids: [\'shanhai:zero_point_energy 32000\', \'shanhai:light 16000\'],',
        '    chancedOutputs: [],',
        '    duration: 1200,',
        '    EUt: 32',
        '}'
    ]
    var o3 = [
        '{',
        '    id: \'shanhai:pf/first_light\',',
        '    type: \'photon_siphon\',',
        '    // PF.txt 原文该格：programmed_circuit + tag:{Configuration:1}',
        '    circuit: 1,',
        '    notConsumable: [\'1x gtlcore:world_fragments_overworld\'],',
        '    itemInputs: [],',
        '    inputFluids: [],',
        '    itemOutputs: [\'32x shanhai:first_light\', \'4x shanhai:photon\'],',
        '    outputFluids: [\'shanhai:light 4000\'],',
        '    chancedOutputs: [],',
        '    duration: 1200,',
        '    EUt: 32',
        '}'
    ]
    var blocks = [
        { note: ['// ===== 老 ①（上一版逐字，未改）：处理样板ULV / 电路组装机 / 10s ====='], body: o1 },
        { note: ['// ===== 老 ⑦（上一版逐字，未改）：处理样板LV / 光子虹吸 / 60s ====='], body: o2 },
        { note: ['// ===== 老 ⑧（上一版逐字，未改）：处理样板LV / 光子虹吸 / 60s ====='], body: o3 }
    ]
    for (var b = 0; b < blocks.length; b++) {
        if (b > 0) w(',')
        for (var n = 0; n < blocks[b].note.length; n++) w('    ' + blocks[b].note[n])
        for (var l = 0; l < blocks[b].body.length; l++) w('    ' + blocks[b].body[l])
    }
}
emitOldGt()

// 新 30 条
for (var x = 0; x < specs.length; x++) {
    var s = specs[x]
    var R = s.row
    w(',')
    w('    // ▶ PF.txt 第 ' + R.no + ' 条（本次新增）｜元件「' + R.cell + '」｜纸：类型「' + s.srcTypeName + '」'
        + (R.time ? '／耗时「' + R.time + '」' : '') + '｜输出 ' + s.itemOutputs.join(' + '))
    if (s.flags.length) for (var f2 = 0; f2 < s.flags.length; f2++) {
        // 一条 flag 可以是多行（用 \n 分隔）：首行带 🔴，续行用对齐缩进，与原手写注释同形。
        var flines = String(s.flags[f2]).split(String.fromCharCode(10))
        for (var f3 = 0; f3 < flines.length; f3++) w((f3 === 0 ? '    // 🔴 ' : '    //    ') + flines[f3])
    }
    // 🔴 2026-09-26：槽位溢出机制已【整段移除】（见文件头 §4）。
    //    历史上这里会按 s.slots.over 打 4 行"请用户裁决"注释并写 slotOver: true；
    //    photon_separation 放宽到 (4,10,2,2) 后没有任何配方溢出 ⇒ 该分支与那个字段一起删掉。
    //    ⚠️ 生成器【仍然】在下面算 s.slots.over 并在控制台报告（构建期自检，不进产物）。
    w('    {')
    w('        id: \'' + s.id + '\',')
    w('        type: \'' + s.type + '\',')
    if (s.circuit > 0) w('        circuit: ' + s.circuit + ',')
    else w('        circuit: 0,')
    w('        notConsumable: [' + s.notConsumable.map(function (v) { return '\'' + v + '\'' }).join(', ') + '],')
    if (s.moduleGate) {
        w('        moduleLevelRequirement: \'' + s.moduleGate + '\',')
        w('        moduleLevelFallbackCatalyst: \'' + s.moduleFallback + '\',')
    }
    w('        itemInputs: [' + s.itemInputs.map(function (v) { return '\'' + v + '\'' }).join(', ') + '],')
    w('        inputFluids: [' + s.inputFluids.map(function (v) { return '\'' + v + '\'' }).join(', ') + '],')
    w('        itemOutputs: [' + s.itemOutputs.map(function (v) { return '\'' + v + '\'' }).join(', ') + '],')
    w('        outputFluids: [' + s.outputFluids.map(function (v) { return '\'' + v + '\'' }).join(', ') + '],')
    w('        chancedOutputs: [' + s.chancedOutputs.map(function (c) { return '{ item: \'' + c.item + '\', chance: ' + c.chance + ', tierChanceBoost: ' + c.tierChanceBoost + ' }' }).join(', ') + '],')
    w('        duration: ' + s.duration + ',')
    w('        EUt: ' + s.EUt)
    w('    }')
}
w(']')
w('')

// ---- registration ----
w('// -----------------------------------------------------------------------------')
w('// 注册：工作台配方（老 ②③④⑤⑥，5 条）')
w('// -----------------------------------------------------------------------------')
// ---- 🔴 运行期反查：那两条老配方【到底还在不在】(用户 2026-09-27 要求可判定的证据) ----
//    放在 ServerEvents.loaded ⇒ 配方表已完全定型，能真读到结果。
w('// -----------------------------------------------------------------------------')
w('// -----------------------------------------------------------------------------')
w('// 🔴 运行期探针：把【真实 id】打出来 —— 不再猜')
w('//   教训：上一版用 recipeManager.byKey(id) 反查，报 present=false，而用户 JEI 里【配方还在】，')
w('//   两者不可能同时对 ⇒ 【byKey 查不到 GTCEu 的配方】⇒ 那条判据【不可靠，已废】。')
w('//   本版改成【遍历配方表】getRecipes().toArray()，按 getId() 里的关键词找 ⇒ 自洽、不依赖索引。')
w('//   ⚠️ 刻意【不用 getResultItem()】—— 字节码实证它恒返回 ItemStack.EMPTY。')
w('//   判读：water_lava=[none] ⇒ 那类配方确实不在表里；打出具体 id ⇒ 那就是【真实 id】。')
w('//   同时报 totalRecipes 做 sanity：若为 0 ⇒ 是遍历入口不对，不是配方不在。')
w('// -----------------------------------------------------------------------------')
w('ServerEvents.loaded(function (event) {')
w("    // 🔴 迟到删除：ServerEvents.recipes 期间删不掉（实测：那时 mod 的配方还没进表），")
w("    //    改到 loaded 时直接改配方表。判据 = 紧随其后的探针（同一行日志体系）。")
w("    // 🔴 用【子串】而不是精确 id —— 实测同一个\"水+岩浆→黑曜石+蒸汽\"存在【两条】老配方：")
w("    //      cxhmz:chemical_reactor/water_lava_to_steam 与 cxhmz:large_chemical_reactor/water_lava_to_steam")
w("    //    （探针实测：删掉第一条后第二条约仍在 ⇒ 这就是用户说\"配方还在\"的原因）")
w("    var KEY = ['water_lava_to_steam', 'fire_charge_ch']")
w("    // ⚠️ 绝不删自己新写的（shanhai: 前缀）")
w("    var dropped = 0")
w("    var keep = []")
w("    var lerr = ''")
w("    try {")
w("        var all0 = event.server.recipeManager.getRecipes().toArray()")
w("        for (var k0 = 0; k0 < all0.length; k0++) {")
w("            var r0 = '?'")
w("            try { r0 = String(all0[k0].getId()) } catch (e0) { r0 = '?' }")
w("            var hit = false")
w("            for (var k1 = 0; k1 < KEY.length; k1++) if (r0.indexOf(KEY[k1]) >= 0) hit = true")
w("            if (r0.indexOf('shanhai:') === 0) hit = false")
w("            if (hit) { dropped = dropped + 1; continue }")
w("            keep.push(all0[k0])")
w("        }")
w("        if (dropped > 0) event.server.recipeManager.replaceRecipes(keep)")
w("    } catch (e1) { lerr = ' (' + e1 + ')' }")
w("    console.info(SHANHAI_PF_TAG + ' remove-late dropped=' + dropped + ' kept=' + keep.length + lerr)")
w("")
w('    var arr = []')
w('    var why = \'\'')
w('    try { arr = event.server.recipeManager.getRecipes().toArray() } catch (e) { why = \' (\' + e + \')\' }')
w('    var total = arr.length')
w('    var wl = \'none\'')
w('    var fc = \'none\'')
w('    var ncx = 0')
w('    var cxList = \'\'')
w('    for (var i = 0; i < total; i++) {')
w('        var rid = \'?\'')
w('        try { rid = String(arr[i].getId()) } catch (e2) { rid = \'?\' }')
w('        if (rid.indexOf(\'water_lava\') >= 0) wl = rid')
w('        if (rid.indexOf(\'fire_charge\') >= 0) { if (fc === \'none\') fc = rid; else if (fc.indexOf(rid) < 0) fc = fc + \'|\' + rid }')
w('        if (rid.indexOf(\'cxhmz\') === 0 || rid.indexOf(\'cxbp\') === 0) { ncx = ncx + 1; if (cxList.length < 200) cxList = cxList + \' \' + rid }')
w('    }')
w('    console.info(SHANHAI_PF_TAG + \' probe totalRecipes=\' + total + \' water_lava=[\' + wl + \'] fire_charge=[\' + fc + \'] cx-ns-count=\' + ncx + why)')
w('    if (ncx > 0) console.info(SHANHAI_PF_TAG + \' probe cx-ids\' + cxList)')
w('})')
w('')
w('')
w('ServerEvents.recipes(function (event) {')
w('    var ok = 0')
w('    // 🔴 2026-09-27 接进聊天栏横幅（scope=shanhai_pf）')
w('    //    ⚠️ 变量名必须【不叫 Stats】—— Rhino 里 `Stats` 会回落到原版 net.minecraft.stats.Stats，')
w('    //       实测报错：Java class "net.minecraft.stats.Stats" has no … "reportSummary"。')
w('    //    ⚠️ 声明必须在【每个 ServerEvents.recipes 回调内部各来一次】—— var 是函数作用域，跨回调不共享。')
w('    var ShanhaiStats = null')
w('    try { ShanhaiStats = Java.loadClass(\'com.shanhai.common.recipe.ShanhaiRecipeStats\') } catch (eS) { ShanhaiStats = null }')
w('    if (ShanhaiStats) ShanhaiStats.reset()   // 本批自己清一次（Java 侧是全局静态累加器）')
w('    var bad = 0')
w('    var errList = \'\'')
w('    var i')
w('')
w('    for (i = 0; i < shanhaiPfShaped.length; i++) {')
w('        var r = shanhaiPfShaped[i]')
w('        try {')
w('            event.shaped(r.out, r.pattern, r.keys).id(r.id)')
w('            ok = ok + 1')
w('            if (ShanhaiStats) ShanhaiStats.addResult(true)')
w('        } catch (e) {')
w('            bad = bad + 1')
w('            if (ShanhaiStats) ShanhaiStats.addResult(false)')
w('            if (errList.length < 1200) { errList = errList + r.id + \' => \' + e + \' | \' }')
w('        }')
w('    }')
w('')
w('    console.info(SHANHAI_PF_TAG + \' crafting(shaped) ok=\' + ok + \' failed=\' + bad')
w('        + \' total=\' + shanhaiPfShaped.length)')
w('    if (bad > 0) {')
w('        console.error(SHANHAI_PF_TAG + \' crafting FAILED list: \' + errList)')
w('    }')
w('})')
w('')
w('// -----------------------------------------------------------------------------')
w('// 注册：GT 机器配方（老 3 条 + 新增 30 条 = 33 条）')
w('// -----------------------------------------------------------------------------')
// ---- 🔴 移除被移植替代的老配方（用户 2026-09-26 在 PF.txt 的纸上点名）----
// 🟢 第二版：第一版只记了「调用成功」，用户实测【没删掉】。
//    本版把 event.remove 的【返回值（删了几条）】打出来 —— 这是决定性的运行期判据：
//      · 返回 0  ⇒ 谓词没匹配上（写错 / 配方根本不在表里）
//      · 返回 >0 而配方仍在 ⇒ 是【执行顺序】问题（别人后加）
//    并同时按【显式 id】再删一次做交叉验证。
w('// -----------------------------------------------------------------------------')
w('// 🔴 移除【被移植替代的老配方】')
w('//   用户 2026-09-26 逐字原话：「我在PF.txt中纸写了，移植的配方是原本产线撕裂或者dgy的配方，')
w('//     由于要结合山海，所以我更新了其中的一些配方，但是老配方还在文件里面，因此需要删除」')
w('//   他写在样板里的纸面原文：「注意，此配方为产线撕裂/dgy移植，添加此配方之后需要移除原本的配方」')
w('//   ① minecraft:obsidian —— gtceu:chemical_reactor：水 2147483647mB + 岩浆 1024000mB ⇒ 蒸汽 + 黑曜石×1024')
w('//   ② minecraft:fire_charge —— gtceu:large_chemical_reactor：火药 + 碳粉 + 烈焰粉 ⇒ 火焰弹×3')
w('//   ⚠️ 用户明确【不删】：gtceu:mixer 产出 fire_charge、以及原版合成台那两条。')
w('//   🔴🔴 2026-09-27 探针实证（遍历 55,947 条配方表得到的真实 id）：')
w('//      真实格式 = <命名空间>:<配方类型路径>/<路径>  —— 不是 <ns>:<路径>！')
w('//        真 id = cxhmz:chemical_reactor/water_lava_to_steam')
w('//        真 id = cxbp:large_chemical_reactor/fire_charge_ch')
w('//      我从导出路径猜的 cxhmz:water_lava_to_steam 【少了一整段类型路径】⇒ 永远不匹配 ✗')
w('//      ⚠️ 正则 { id: /...$/ } 本轮实测【也没生效】⇒ 只能用【精确 id】。')
w('//      ⚠️ 探针同时确认【不该删的两条在表里】：gtceu:mixer/fire_charge 与 minecraft:fire_charge（原版合成台）。')
w('//   🔴 2026-09-27 字节码实证：GTRecipe.getResultItem() 恒返回 ItemStack.EMPTY（javap -c 只有 getstatic ItemStack.f_41583_; areturn），')
w('//      而 KubeJS 的 OutputFilter.test() 只有一句 RecipeKJS.hasOutput(match) ⇒ 【{output:...} 对 GT 配方永远不可能匹配】！')
w('//      ⇒ 所以第一版的 {type,output} 谓词【注定无效】（这也是"删不掉"的机械根因）。')
w('//      ⇒ 正确写法是【按 id 删】：GTRecipe implements 原版 Recipe<Container>（javap 类声明），有 id 字段与 getId()。')
w('//      ⇒ 这里用【正则 id】而不是硬猜命名空间（导出路径是 added_recipes/cxhmz/chemical_reactor/water_lava_to_steam.json，')
w('//        命名空间那一段我无法从路径 100% 反推）。')
w('//      （老配方来自 mod jar（ns=cxhmz/cxbp），不是 KJS 写的 —— 但这不影响 event.remove：')
w('//        它删的是配方表里的条目，不区分来源。真正要防的是删完之后又被后加的脚本加回来。）')
w('//   ✅ 幂等：重复执行时返回值变 0，不报错。')
w('// -----------------------------------------------------------------------------')
w('ServerEvents.recipes(function (event) {')
w('    // 🔴 2026-09-27 收尾：这里原本有 4 条 event.remove —— 【已删除】，因为实测【全部无效】。')
w('    //    ① {type,output} 两条：字节码证明 GTRecipe.getResultItem() 恒返回 ItemStack.EMPTY，')
w('    //       而 KubeJS 的 OutputFilter.test() 只有一句 RecipeKJS.hasOutput(match)')
w('    //       ⇒ 【{output:...} 对 GT 配方永远不可能匹配】。')
w('    //    ② {id} 精确 / {id:/正则/} 两条：时机太早 —— ServerEvents.recipes 期间 mod 的配方')
w('    //       还没进配方表（实测：此刻移除后，18 秒后的探针仍能看到它）。')
w('    //    ✅ 真正生效的删除已挪到本文件末尾的 ServerEvents.loaded 里（直接改配方表）。')
w('    //    ✅ 那处的判据是自洽的：remove-late 与紧随其后的 probe 用同一套遍历。')
w('    console.info(SHANHAI_PF_TAG + \' remove-old 已停用（本块 4 条实测无效，见下方 ServerEvents.loaded）\')')
w('})')
w('')
w('')
w('ServerEvents.recipes(function (event) {')
w('    var gtr = event.recipes.gtceu')
w('    var ok = 0')
w('    // 🔴 同上：本回调内【重新声明】一次（var 不跨回调共享）')
w('    var ShanhaiStats = null')
w('    try { ShanhaiStats = Java.loadClass(\'com.shanhai.common.recipe.ShanhaiRecipeStats\') } catch (eS) { ShanhaiStats = null }')
w('    var bad = 0')
w('    var errList = \'\'')
w('    var i')
w('    var j')
w('')
w('    for (i = 0; i < shanhaiPfGt.length; i++) {')
w('        var r = shanhaiPfGt[i]')
w('        try {')
w('            // ⚠️ 类型 id 直接当方法名用：gtr[\'photon_siphon\'](...) —— 等价于 gtr.photon_siphon(...)')
w('            var b = gtr[r.type](r.id)')
w('')
w('            var useLevelGate = shanhaiUseLevelGate(r)')
w('')
w('            // 🔴 顺序照宿主脚本：先 .notConsumable(...)，再 .circuit(...)')
w('            //    先例：gtceu.js:3302-3303 / gtceu.js:9955-9957 / gtceu.js:935')
w('            for (j = 0; j < r.notConsumable.length; j++) {')
w('                b = b.notConsumable(r.notConsumable[j])')
w('            }')
w('            // 🔴 物质模块：门槛可用 ⇒ 不写催化剂；否则退回催化剂形态（配方不会消失）')
w('            if (!useLevelGate && r.moduleLevelFallbackCatalyst) {')
w('                b = b.notConsumable(r.moduleLevelFallbackCatalyst)')
w('            }')
w('')
w('            // 🔴 编程电路：.circuit(数字)，参数必须是数字（Rhino 下传字符串会报错）')
w('            if (r.circuit > 0) {')
w('                b = b.circuit(r.circuit)')
w('            }')
w('')
w('            // 🔴 物质模块等级门槛（= 老山海 module_level 配方条件）')
w('            //    写法照宿主现成先例：gtceu.js:8489 .addCondition(new GravityCondition(true))')
w('            if (useLevelGate) {')
w('                var mlp = shanhaiParseModuleLevel(r.moduleLevelRequirement)')
w('                b = b.addCondition(new ModuleLevelCondition(mlp[0], mlp[1]))')
w('            }')
w('')
w('            for (j = 0; j < r.itemInputs.length; j++) {')
w('                b = b.itemInputs(r.itemInputs[j])')
w('            }')
w('            for (j = 0; j < r.inputFluids.length; j++) {')
w('                b = b.inputFluids(r.inputFluids[j])')
w('            }')
w('            for (j = 0; j < r.itemOutputs.length; j++) {')
w('                b = b.itemOutputs(r.itemOutputs[j])')
w('            }')
w('            for (j = 0; j < r.outputFluids.length; j++) {')
w('                b = b.outputFluids(r.outputFluids[j])')
w('            }')
w('//            // 🔴 概率产出（本次新增能力）：#33 的「电子中微子产出概率5%」')
w('            // ⚠️ 第二个 int 是【每超频一级的加成量 tierChanceBoost】，不是"上限"：')
w('            //    字节码实证 GTRecipeBuilder.chancedOutput(ItemStack,int,int)：')
w('            //      67: aload_0 / 68: iload_2 / 69: putfield chance:I')
w('            //      72: aload_0 / 73: iload_3 / 74: putfield tierChanceBoost:I   <-- 第三个参数')
w('            if (r.chancedOutputs) {')
w('                for (j = 0; j < r.chancedOutputs.length; j++) {')
w('                    var co = r.chancedOutputs[j]')
w('                    b = b.chancedOutput(co.item, co.chance, co.tierChanceBoost)')
w('                }')
w('            }')
w('')
w('            b.duration(r.duration).EUt(r.EUt)')
w('            ok = ok + 1')
w('            if (ShanhaiStats) ShanhaiStats.addResult(true)')
w('        } catch (e) {')
w('            bad = bad + 1')
w('            if (ShanhaiStats) ShanhaiStats.addResult(false)')
w('            if (errList.length < 1200) {')
w('                errList = errList + r.id + \' [\' + r.type + \'] => \' + e + \' | \'')
w('            }')
w('        }')
w('    }')
w('')
w('    console.info(SHANHAI_PF_TAG + \' gt_machine ok=\' + ok + \' failed=\' + bad')
w('        + \' total=\' + shanhaiPfGt.length)')
w('    console.info(SHANHAI_PF_TAG + \' module-mode=\' + SHANHAI_PF_MODULE_MODE')
w('        + \' module-level-condition available=\' + SHANHAI_HAS_MODULE_LEVEL_CONDITION)')
w('    if (bad > 0) {')
w('')
w('        console.error(SHANHAI_PF_TAG + \' gt_machine FAILED list: \' + errList)')
w('    }')
w('    // 🔴 打机器可判的那一行：本批 PF 配方的 total/success/failed')
w('    var hasShanhaiStats = ShanhaiStats')
w('    if (hasShanhaiStats) {')
w('        try { ShanhaiStats.reportSummary(\'shanhai_pf\') } catch (eR) { console.error(SHANHAI_PF_TAG + \' reportSummary FAILED: \' + eR) }')
w('    } else {')
w('        console.error(SHANHAI_PF_TAG + \' ShanhaiRecipeStats 类不可用 ⇒ 本批不上报 \')')
w('    }')
w('')
w('    // 逐条回执：证明 builder 收下了什么（方便和 PF.txt 对账）')
w('    for (i = 0; i < shanhaiPfGt.length; i++) {')
w('        var s = shanhaiPfGt[i]')
w('        var gateOn = shanhaiUseLevelGate(s)')
w('        console.info(SHANHAI_PF_TAG + \' spec id=\' + s.id')
w('            + \' type=\' + s.type')
w('            + \' circuit=\' + s.circuit')
w('            + \' in=\' + s.itemInputs.length + \'item\'')
w('            + \' nc=\' + s.notConsumable.length + \'cat\'')
w('            + \' slots=\' + (s.itemInputs.length + s.notConsumable.length + (s.circuit > 0 ? 1 : 0)')
w('                + (!gateOn && s.moduleLevelFallbackCatalyst ? 1 : 0))')
w('            + \' gate=\' + (gateOn ? s.moduleLevelRequirement : \'-\')')
w('            + \' fin=\' + s.inputFluids.length')
w('            + \' out=\' + s.itemOutputs.length + \'item\'')
w('            + \' fout=\' + s.outputFluids.length')
w('            + \' chanced=\' + (s.chancedOutputs ? s.chancedOutputs.length : 0)')
w('            + \' EUt=\' + s.EUt')
w('            + \' duration=\' + s.duration + \'t\')')
w('    }')
w('')
w('    // 🔴 2026-09-26：槽位溢出自检【已移除】—— photon_separation 放宽到 (4, 10, 2, 2) 之后')
w('    //    没有任何配方超出上限（详见文件头 §4）。原先这里会扫 shanhaiPfGt[i].slotOver')
w('    //    并打 [SHANHAI-PF] SLOT-OVER 告警；那个字段与整段告警代码一并删掉了。')
w('    //    ⚠️ 若将来又出现"配方要的槽位 > 该类型 setMaxIOSize"的情形，需要【重新引入】这类检查，')
w('    //       不要以为本文件还带着它。')
w('})')

// 🔴 2026-09-27 用户点单第 1 条：产物改名 [server_scripts]shanhai_recipes.js（带 [server_scripts] 前缀，照实例命名）。
//    产物 = 【生成部分】+【手写区】：手写区在文件末尾，生成器原样保留。
//    ⚠️ 仍然【不落地到用户实例】—— 交付给队长，由队长决定什么时候放进 kubejs\。
var OUT_DIR = 'C:\\Users\\david\\Desktop\\构建\\shanhai重构\\kubejs\\server_scripts\\'
// 🔴 2026-09-27 用户点单第 3/4 条：产物 = 【生成部分】+【手写区】（zero_point_power 并进来后住这里）。
const M_BEGIN = "// ═══ 手写区 开始（生成器不会动这一段）═══"
const M_END   = "// ═══ 手写区 结束 ═══"
const TARGET = OUT_DIR + '[server_scripts]shanhai_recipes.js'
let HANDS = ['global.SHANHAI_RECIPES_HAND = {}']
let handNote = '(首次生成：写入默认空手写区)'
if (fs.existsSync(TARGET)) {
    const prev = fs.readFileSync(TARGET, 'utf8').split(/\r?\n/)
    const a = prev.findIndex(l => l.indexOf(M_BEGIN) >= 0)
    const b = prev.findIndex(l => l.indexOf(M_END) >= 0)
    if (a >= 0 && b > a) { HANDS = prev.slice(a + 1, b); handNote = '(保留自旧产物：' + HANDS.length + ' 行)' }
    else { handNote = '(旧产物无手写区标记 => 写入默认；原文件已备份到 .no-handreg-bak)'; fs.writeFileSync(TARGET + '.no-handreg-bak', prev.join('\r\n'), 'utf8') }
}
fs.writeFileSync(TARGET, L.join('\r\n') + '\r\n\r\n' + M_BEGIN + '\r\n' + HANDS.join('\r\n') + '\r\n' + M_END + '\r\n', 'utf8')
console.log('  手写区 ' + handNote)
// 报告被跳过的行
if (SKIPPED.length) {
  console.info('[PF] 🔴 因解析不出配方类型而【跳过】的行 = ' + SKIPPED.length + ' 条:')
  for (var sk = 0; sk < SKIPPED.length; sk++) console.info('      no=' + SKIPPED[sk].no + '  cell=' + SKIPPED[sk].cell + '  srcType=' + SKIPPED[sk].srcType + '  产物数=' + SKIPPED[sk].outs)
} else { console.info('[PF] 没有跳过任何行') }
console.log('written -> ' + TARGET)

// ---------------------------------------------------------------- report
console.log('new specs = ' + specs.length)
var over = []
for (var o = 0; o < specs.length; o++) if (specs[o].slots.over) over.push(specs[o])
console.log('slot overflows = ' + over.length)
for (var o2 = 0; o2 < over.length; o2++) {
    var sp2 = over[o2]
    console.log('  #' + sp2.row.no + ' ' + sp2.type + ' used=(' + sp2.slots.itemIn + ',' + sp2.slots.itemOut + ',' + sp2.slots.fluidIn + ',' + sp2.slots.fluidOut + ') cap=(' + sp2.slots.cap.join(',') + ')')
}
console.log('--- spec summary (id / type / slots used vs cap) ---')
for (var o3 = 0; o3 < specs.length; o3++) {
    var sp3 = specs[o3]
    console.log('  #' + String(sp3.row.no).padStart(2) + ' ' + sp3.id + '  ' + sp3.type
        + '  slots(' + sp3.slots.itemIn + '/' + sp3.slots.cap[0] + ',' + sp3.slots.itemOut + '/' + sp3.slots.cap[1]
        + ',' + sp3.slots.fluidIn + '/' + sp3.slots.cap[2] + ',' + sp3.slots.fluidOut + '/' + sp3.slots.cap[3] + ')'
        + (sp3.flags.length ? '   FLAGS=' + sp3.flags.length : ''))
}
fs.writeFileSync(BASE + 'specs.json', JSON.stringify(specs, null, 1), 'utf8')
