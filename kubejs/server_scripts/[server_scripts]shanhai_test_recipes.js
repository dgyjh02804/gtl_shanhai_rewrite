// priority: 1
// =============================================================================
// [server_scripts]shanhai_test_recipes.js  --  占位测试配方（40 条真类型版 + 合并进来的 deconstruct_test 1 条）
//
// ✅ 已部署到 GTL山海9.10test\kubejs\server_scripts\（2026-09-27 改名）。
//
// 目的（用户 2026-09-23 交办原话，逐字）：
//   「给每一个新增的配方种类添加一个测试用配方，都是一个原石出一个钻石，
//     不然jei看不到」
//
// 2026-09-27 用户点单「40 条真类型全部挂上」⇒ 本脚本从 16 条扩到 **39 条**：
//   · 普通组（原石→钻石）：**36 条**（原来的 16 条 + 新恢复的 20 条）
//   · 只消耗组：**3 条**（物品输出槽 = 0，写不出"原石→钻石"，只能用只消耗形态）
//   · **1 条不写**：`proxy_execution`（setMaxIOSize(0,0,0,0)：物品输入=0 且 输出=0）
//     ⇒ 它**写不出任何配方**，JEI 里那个分类**永远是空的**。这不是脚本的毛病，是类型本身如此。
//   ⇒ 40 = 36 普通 + 3 只消耗 + 1 无配方
//
// 为什么"没配方 JEI 里就看不到"：JEI 的 GT 分类是
// `GTRecipeTypeCategory#registerRecipes` 遍历 `BuiltInRegistries.RECIPE_TYPE`、
// 对每个类型调 `RecipeManager.getRecipes(该类型)` 再 `addRecipes(...)` ⇒
// **该类型一条配方都没有时，那个分类就是空的**（分类标题在、点进去没东西）。
// 给它们各补一条占位配方，就是为了"点进去有东西"。
//
// =============================================================================
// 写法来源 —— 照抄宿主脚本里【现成的】形态，不是我自己发明：
//   temp\smoke-rig\server\kubejs\server_scripts\gtceu.js:908
//     const gtr = event.recipes.gtceu
//     gtr.alloy_smelter(...)
//         .itemInputs(...)
//         .itemOutputs(...)
//         .EUt(16)
//         .duration(80)
//   （实测 gtceu.js 里 gtr.<type> 这种写法有 1000+ 处，是整合包自己的标准形态。）
//
// ⚠️ KubeJS = Rhino 引擎，不是 Node。本脚本刻意只用最保守的写法：
//   · 全局/局部一律 var；不用模板字符串、不用 ?.、不用解构、不用 let/const。
//   · 不用 circuit（rhino 下 circuit 必须是数字，本脚本干脆不传）。
//   · 不用 notConsumable（那也占输入槽位）。
//   · 每条配方包在 try/catch 里 —— 万一某个类型 KubeJS 侧没暴露，只让这一条失败，
//     不会把整个脚本的配方一起带崩。
//
// ⚠️ 另外 3 条（只消耗组）的 id 与槽位事实（逐个从 ShanhaiRecipeTypes 的
//    setMaxIOSize 读出，不是我猜的）：
//      primordial_myriad_ascension_tier_1  setMaxIOSize(4, 0, 4, 0)   ← 物品输出槽 0
//      primordial_myriad_ascension_tier_2  setMaxIOSize(4, 0, 4, 0)   ← 物品输出槽 0
//      gravitational_wave_consumption      setMaxIOSize(1, 0, 1, 0)   ← 物品输出槽 0
//    ⇒ 这三条**不能**写 itemOutputs（物品输出槽不存在）。
// =============================================================================

ServerEvents.recipes(function (event) {
    var gtr = event.recipes.gtceu

    // ───────────────────────── 普通组：原石 → 钻石（36 条） ─────────────────────────
    // 前 16 条 = 2026-09-23 就在跑的那批（顺序沿用原脚本，便于对账）
    // 后 20 条 = 2026-09-27 恢复的 24 条里，物品输入槽>=1 且 物品输出槽>=1 的那些
    var NORMAL_IDS = [
        // ── 原有的 16 条 ──
        'interstellar_matter_absorption',
        'matter_flow_condensation',
        'matter_forging',
        'matter_module_casting',
        'photon_separation',
        'primordial_biological_core',
        'primordial_causal_weaving',
        'primordial_energy_absorption',
        'primordial_matter_recombination',
        'primordial_power_generator',
        'primordial_singularity_inversion',
        'primordial_stellar_reaction',
        'taixu_smelting',
        'wl_board_circuit_assembly',
        'wl_board_wafer_etching',
        'worldline_oscillation_collection',
        // ── 2026-09-27 恢复的 20 条 ──
        'black_hole_compressor',
        'black_hole_event_horizon_blast',
        'black_hole_neutronium_compressor',
        'chaos_crafting',
        'coin_forge',
        'gravitational_wave_production',
        'high_dimensional_fragment_cutting',
        'kmyy',
        'matter_aggregation',
        'nebula_siphoning',
        'nine_industrial',
        'photon_siphon',
        'seventy_two_changes',
        'spacetime_distortion',
        'tianjie_navigation',
        'worldline_cutting',
        'worldline_matter_recurrence',
        'worldline_probability_cracking',
        'worldline_sampling',
        'zero_point_conversion'
    ]

    // ───────────────────────── 只消耗组（3 条） ─────────────────────────
    // 这三条物品输出槽 = 0 ⇒ 只能写"只消耗"，不能写 itemOutputs
    var CONSUME_ONLY_IDS = [
        'gravitational_wave_consumption',
        'primordial_myriad_ascension_tier_1',
        'primordial_myriad_ascension_tier_2'
    ]

    var ok = 0
    var bad = 0
    var errList = ''
    var i
    var tid

    for (i = 0; i < NORMAL_IDS.length; i++) {
        tid = NORMAL_IDS[i]
        try {
            gtr[tid]('shanhai:test/' + tid)
                .itemInputs('1x minecraft:cobblestone')
                .itemOutputs('1x minecraft:diamond')
                .duration(20)
                .EUt(30)
            ok = ok + 1
        } catch (e) {
            bad = bad + 1
            if (errList.length < 1500) {
                errList = errList + tid + ' => ' + e + ' | '
            }
        }
    }

    for (i = 0; i < CONSUME_ONLY_IDS.length; i++) {
        tid = CONSUME_ONLY_IDS[i]
        try {
            // 只消耗：给输入、**不给输出**（这些类型的物品输出槽 = 0）
            gtr[tid]('shanhai:test/' + tid)
                .itemInputs('1x minecraft:cobblestone')
                .duration(20)
                .EUt(30)
            ok = ok + 1
        } catch (e) {
            bad = bad + 1
            if (errList.length < 1500) {
                errList = errList + tid + '(consume-only) => ' + e + ' | '
            }
        }
    }

    // 预期：ok=39 failed=0 total=39
    console.info('[SHANHAI-TEST] 占位测试配方: ok=' + ok + ' failed=' + bad + ' total=' + (NORMAL_IDS.length + CONSUME_ONLY_IDS.length))
    if (bad > 0) {
        console.error('[SHANHAI-TEST] 失败清单: ' + errList)
    }

    // -------------------------------------------------------------------------
    // 上报 Java 侧统计（2026-09-24 新增，保持原样）
    // -------------------------------------------------------------------------
    // 🔴 为什么整段包在 try/catch 里：这是 server_scripts，KubeJS 的 ServerEvents.recipes
    //    回调里抛异常有可能连锁影响配方注册。Java 统计属于「附加功能」——
    //    它坏了绝不允许把上面那些配方带崩。任何一环失败只打一条 error，回调照常结束。
    //
    // ⚠️ 三步顺序不能换：
    //    ① reset()            —— 否则 /reload 会让同一条配方被累加两次
    //    ② addResult(true/false) —— Java 侧【唯一】的计数入口，按 ok/bad 还原条数
    //    ③ reportSummary(scope)  —— 只有走到这一步才会打那一行
    //    任何一步抛异常 => 那一行不会出现（响亮缺席），但绝不会打出一行残缺的数字。
    try {
        var Stats = Java.loadClass('com.shanhai.common.recipe.ShanhaiRecipeStats')
        Stats.reset()
        var n
        for (n = 0; n < ok; n++) {
            Stats.addResult(true)
        }
        for (n = 0; n < bad; n++) {
            Stats.addResult(false)
        }
        Stats.reportSummary('kjs_shanhai_test_recipes')
    } catch (err) {
        console.error('[SHANHAI-TEST] 上报 Java 侧配方统计失败（配方本身不受影响）: ' + err)
    }
})

// =============================================================================
// 本文件该放到哪（部署由队长做，本代理未碰任何游戏实例）
// =============================================================================
// 目标路径：<游戏实例>\kubejs\server_scripts\shanhai_test_recipes.js
//   · 沙盒验证位置（本工程自己的无头专服）：
//       _smoke-mixin\server\kubejs\server_scripts\shanhai_test_recipes.js
//   · 用户实例（**本代理没有碰**，队长另派）：
//       日常\versions\GTL山海9.10test\kubejs\server_scripts\shanhai_test_recipes.js
//
// 🔴 这是**原地替换**，不是新增：目标位置已有一个同名脚本（16 条版）。
//    若两个文件同时存在，`shanhai:test/<tid>` 这 16 个 id 会**重复注册**并报错。
//    ⇒ 必须替换（或先删旧的），不能两份并存。
// =============================================================================

// ═════════════════════════════════════════════════════════════════════════════════════════════
// 🔴 2026-09-27 用户点单第 5 条：shanhai_deconstruct_test.js 全文合并进本文件。
//    它的 id 规则 = shanhai:deconstruct_test/<type> ⇒ 与本文件既有 id 【不冲突】
//    （本文件用 shanhai:<type>_test_<n> 之类，没有 deconstruct_test/ 这一段）。
// ═════════════════════════════════════════════════════════════════════════════════════════════

// =============================================================================
// shanhai_deconstruct_test.js  --  「原初物质解构」的【占位测试配方】（1 条）
//
// 目的（用户 2026-09-26 交办原话，逐字）：
//   「等一下，如果是空分类，我是在jei看不到的，我现在还没启动，你补一个测试配方」
// ⇒ 新类型 primordial_matter_deconstruction 目前**一条配方都没有**。
//   JEI 的 GT 分类 = GTRecipeTypeCategory#registerRecipes 遍历 RECIPE_TYPE 再按类型取配方，
//   **该类型零配方时那个分类就是空的**（标题在、点进去没东西）。
//   ⇒ 本条的【唯一目的】= 让 JEI 里那个分类"有东西可看"，不是最终玩法配方。
//   ⇒ 真正的"按化学式拆解"是将来的事，本条刻意做到最简。
//
// 🔴 为什么单开一个文件（那三个一个都不动）：
//   · shanhai_test_recipes.js      (sha256 A042DCFB…) = 【保护文件】，本次一字节不许碰；
//   · shanhai_pf_recipes.js        (sha256 D82343F0…) = 用户自己写的，本代理不许碰；
//   · shanhai_zero_point_power.js  (sha256 B4F14358…) = 保护文件。
//   ⇒ 本文件是【新增】，目标位置原本【不存在同名文件】⇒ 无覆盖风险。
//
// 🔴 配方 id 为什么不是 `shanhai:test/primordial_matter_deconstruction`：
//   那正是 shanhai_test_recipes.js 的命名法（`shanhai:test/<type>`）。若将来把第 41 条
//   补进那个文件的 NORMAL_IDS，两边会【重复注册同一个 id】并报错。
//   ⇒ 本条改用 `shanhai:deconstruct_test/<type>`，与该文件现有 39 个 id 全不冲突。
//
// 写法来源 —— 照抄 shanhai_test_recipes.js:114-118 那条【已经在跑的】现成形态：
//     gtr[tid]('shanhai:test/' + tid)
//         .itemInputs('1x minecraft:cobblestone')
//         .itemOutputs('1x minecraft:diamond')
//         .duration(20)
//         .EUt(30)
//   ⇒ 只把 type 换成本类型、输出换成 gtceu:carbon_dust（用户建议）。
//
// 类型侧事实（**无头专服运行期实测**，不是从注释抄的）：
//   primordial_matter_deconstruction  → setMaxIOSize(1, 20, 1, 10)
//   物品入 1 / 物品出 20 / 流体入 1 / 流体出 10；本条只用 1 入 1 出，完全在限额内。
//   （原始实测行：[SHANHAI-RTUI-PROBE] TARGET_NEW_TYPE …
//     maxInputs{Item=1,Fluid=1} maxOutputs{Item=20,Fluid=10}）
//
// 物品存在性 —— **带正面对照的核过**（不是猜的）：
//   测试实例 local/kubejs/export/registries/item.json（19,332 条）里
//     minecraft:cobblestone  FOUND
//     gtceu:carbon_dust      FOUND
//   对照：gtceu:iron_dust FOUND ／ gtceu:zzz_bogus_xyz absent
//   ⇒ 探针本身可信（3/3 正面 + 1 负面全过），所以上面两个 FOUND 才算数。
//
// ⚠️ KubeJS = Rhino 引擎，不是 Node。刻意只用最保守的写法：
//    全部 var；无 let/const、无箭头函数、无模板字符串、无 ?.、无解构。
//    整条包在 try/catch 里 —— 本类型万一在 KubeJS 侧没暴露，只让这一条失败，不连坐别的脚本。
// =============================================================================

ServerEvents.recipes(function (event) {
    var gtr = event.recipes.gtceu
    var TYPE = 'primordial_matter_deconstruction'
    var RID = 'shanhai:deconstruct_test/' + TYPE

    var ok = 0
    var bad = 0
    var errText = ''

    try {
        gtr[TYPE](RID)
            .itemInputs('1x minecraft:cobblestone')
            .itemOutputs('1x gtceu:carbon_dust')
            .duration(20)
            .EUt(30)
        ok = 1
    } catch (e) {
        bad = 1
        errText = '' + e
    }

    // 🔴 判据行：这一行打出 ok=1 且 failed=0 ⇒ JEI 里那个分类就不是空的了
    console.info('[SHANHAI-DECON-TEST] type=' + TYPE + ' id=' + RID
        + ' in=1x minecraft:cobblestone out=1x gtceu:carbon_dust duration=20t EUt=30'
        + ' => ok=' + ok + ' failed=' + bad)
    if (bad > 0) {
        console.error('[SHANHAI-DECON-TEST] 失败: ' + errText)
    }
})
