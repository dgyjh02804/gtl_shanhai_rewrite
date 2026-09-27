// priority: 10
// =============================================================================
// [startup_scripts]shanhai_deconstruct_recipes_elements.js  --  把 9 个特殊符号注册成【元素 + 材质】
//
// 🔴 位置：**startup_scripts**（不是 server_scripts）—— GTCEuStartupEvents 只在注册期有效。
// 🔴 状态：已落地 `kubejs/startup_scripts/[startup_scripts]shanhai_deconstruct_recipes_elements.js`（2026-09-27 改名，照实例命名）
//
// -----------------------------------------------------------------------------
// 用户原话（逐字）
// -----------------------------------------------------------------------------
//   「把它们注册成元素」
//   目的（队长转述，我照做）：「它们变成合法元素符号后，解析器就能从那些化合物公式里
//                             把它们切出来」
//
// -----------------------------------------------------------------------------
// 我核对出来的【符号到底几个】—— **9 个**
// -----------------------------------------------------------------------------
//   来源：`originals/matdump\evidence\parse_report.json` 的 `failures[].unknown[]`
//        （= GTCEu 自己的公式解析器【认不出来】而丢进 unknown 的那些 token）。
//   我按「非 ASCII、且不是下标数字」筛出符号类 token，扫**全量**（不用截断窗口）：
//
//     #  符号   码位       被哪些材质的公式用到（全量 1463 材质里）
//     1  Ж     U+0416   proto_halkonite_base / proto_halkonite
//     2  ⊕     U+2295   proto_halkonite_base / proto_halkonite
//     3  ☄     U+2604   proto_halkonite_base / proto_halkonite
//     4  ⚛     U+269B   proto_halkonite_base / proto_halkonite
//     5  〄     U+3004   phonon_medium / phonon_crystal_solution      ⚠️ 队长清单里【没有这个】
//     6  ⌘     U+2318   phonon_medium
//     7  ☯     U+262F   phonon_medium
//     8  ✟     U+271F   star_gate_crystal_slurry
//     9  ✵     U+2735   draconiumawakened / star_gate_crystal_slurry
//
//   🔴 队长给的清单是 **8 个**，**漏了 U+3004（〄）**；队长列的 8 个本身**全对、没有多的**。
//      用户说的"9 个符号" ⇒ **9 是对的**，本文件按 **9** 注册。
//   ⚠️ 顺带：`✟✵✟` 是**三个字符**（`✟` + `✵` + `✟`），不是两个符号；所以它只贡献 2 个符号。
//      剩下的 unknown token 都不是符号：ASCII 的 `* - t e + p n b a r A h _`、
//      以及下标 `₇ ₂ ₃ ₄`（U+2087/2082/2083/2084）—— 那些是化学式里的数字，按原样留着。
//
// -----------------------------------------------------------------------------
// API 取证（**这包装配里没有任何元素/材质注册先例，所以我去反编译了 GTCEu 自己的 KJS 接口**）
// -----------------------------------------------------------------------------
//   · `GTCEuStartupEvents.registry('gtceu:element', ...)` ✅ 存在
//       —— `GregTechKubeJSPlugin.init()` 里有 `GTRegistryInfo.ELEMENT.addType("basic", ...)`，
//          其 bootstrap 绑定的构造器 = `com/gregtechceu/gtceu/integration/kjs/builders/ElementBuilder`。
//   · `GTCEuStartupEvents.registry('gtceu:material', ...)` ✅ 存在
//       —— 同处 `GTRegistryInfo.MATERIAL.addType("basic", ...)`，
//          绑定的构造器 = `com/gregtechceu/gtceu/api/data/chemical/material/Material$Builder`
//          （**GTCEu 自己的 Java Builder**，不是 KJS 包里的类 —— 所以 KJS 里直接链它的方法）。
//   · ⚠️ 我第一遍**差点报错**：我用「jar 里有没有 `gtceu:material` 这个字符串」当判据，
//      得到 0 命中，就以为"没有这个注册表"。**那是假否定** —— 真正该看的是
//      `GTRegistryInfo` 的**字段**（ELEMENT / MATERIAL）而不是字符串字面量。
//      ⇒ 记一条：**"我们的产物里没有" ≠ "这个东西不存在"**。
//   · 🔴 `Material$Builder` **没有** `formula(...)` 方法（77 个 public 方法里 0 命中）。
//      化学式是**算出来的**：`Material.calculateChemicalFormula()` 由 components/element 推导；
//      要硬指定只能用**建成之后**的 `Material.setFormula(String)`。
//      ⇒ 所以我们不写 formula，改走 `.element(el)` ⇒ 推导出的公式自然就是那个符号本身 ✅。
//
// ⚠️ 断言一律用【十六进制码位】，**不打印符号本身**：专服控制台是 GBK，非 ASCII 会变 `?`，
//    看控制台会得出错误结论。本文件只打印 `U+XXXX`。
//
// ⚠️ KubeJS = Rhino：只用 var；无 let/const、无箭头、无模板串、无 ?.、无解构。
// =============================================================================

var SHANHAI_SYMBOLS = [
    { id: 'zhe',     sym: '\u0416', hex: 'U+0416', note: 'proto_halkonite(_base)' },
    { id: 'oplus',   sym: '\u2295', hex: 'U+2295', note: 'proto_halkonite(_base)' },
    { id: 'comet',   sym: '\u2604', hex: 'U+2604', note: 'proto_halkonite(_base)' },
    { id: 'atom',    sym: '\u269B', hex: 'U+269B', note: 'proto_halkonite(_base)' },
    { id: 'maru',    sym: '\u3004', hex: 'U+3004', note: 'phonon_medium / phonon_crystal_solution' },
    { id: 'cmd',     sym: '\u2318', hex: 'U+2318', note: 'phonon_medium' },
    { id: 'taiji',   sym: '\u262F', hex: 'U+262F', note: 'phonon_medium' },
    { id: 'cross',   sym: '\u271F', hex: 'U+271F', note: 'star_gate_crystal_slurry' },
    { id: 'penta',   sym: '\u2735', hex: 'U+2735', note: 'draconiumawakened / star_gate_crystal_slurry' }
]

// 元素注册结果（id -> hex），供下面 material 阶段核对
var SHANHAI_EL_OK = {}
var SHANHAI_EL_BAD = {}

// 把字符串逐字符转成 `U+XXXX`（大写、补足 4 位）—— 专服控制台是 GBK，**不许把符号本身
// 打进日志**（会变 `?`），所以一切断言都用码位十六进制。
function shanhaiHexOf(s) {
    if (s === null || s === undefined) { return 'null' }
    var out = ''
    for (var i = 0; i < s.length; i++) {
        var h = s.charCodeAt(i).toString(16).toUpperCase()
        while (h.length < 4) { h = '0' + h }
        out = out + 'U+' + h
    }
    return out
}

// ─────────────────────────────────────────────────────────────────────────────
// ① 元素
// ─────────────────────────────────────────────────────────────────────────────
GTCEuStartupEvents.registry('gtceu:element', function (event) {
    for (var i = 0; i < SHANHAI_SYMBOLS.length; i++) {
        var S = SHANHAI_SYMBOLS[i]
        try {
            // ElementBuilder 的构造器把第 1 个参数当 protons（bytecode: arg[0] instanceof Number -> intValue）
            // 🔴 ElementBuilder 的构造器要 **6 个位置参数**（bytecode 实证，不是猜的）：
            //      args[0]=protons(Number) args[1]=neutrons(Number，无空值保护，必填)
            //      args[2]=halfLifeSeconds(Number，必填) args[3]=decayTo(String，可为 null)
            //      args[4]=symbol(String，可为 null ⇒ 默认 "") args[5]=isIsotope(Boolean，必填)
            //    ⚠️ 我第一版只传了 1 个参数 ⇒ args[1] 越界 ⇒
            //       `ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1`（9/9 全失败）。
            //    ⚠️ 构造器还会把 name 强制设成 ResourceLocation 的 path（如 `zhe`）——这是**显示名**，
            //       不影响公式解析（解析用的是 symbol）。
            //    🔴 **不要再赋值 b.name / b.symbol 等字段**：Rhino 报
            //       `InternalError: Java class "ElementBuilder" has no public instance field or
            //        method named "name"`（实测踩到，9/9 全失败）。虽然 javap 显示这些是
            //       public transient 字段，但 Rhino 的成员查找**解析不到**它们。
            //       ⇒ **构造器的 6 个参数已经把 protons/neutrons/halfLifeSeconds/decayTo/name/
            //          symbol/isIsotope 全部设好了**，一个字段都不用再碰。
            event.create(S.id, 0, 0, 0, null, S.sym, false)
            SHANHAI_EL_OK[S.id] = S.hex
        } catch (e) {
            SHANHAI_EL_BAD[S.id] = '' + e
        }
    }
    var nOk = 0
    for (var k in SHANHAI_EL_OK) { nOk = nOk + 1 }
    var nBad = 0
    for (var k2 in SHANHAI_EL_BAD) { nBad = nBad + 1 }
    console.info('[SHANHAI-SYMEL] element registry => ok=' + nOk + ' failed=' + nBad
        + ' total=' + SHANHAI_SYMBOLS.length)
    for (var k3 in SHANHAI_EL_BAD) {
        // 🔴 用 info 不用 error：KubeJS 会把 **startup 脚本里的 console.error** 计进
        //    "startup script errors" ⇒ 触发 `There were KubeJS startup script syntax errors!`
        //    ⇒ 连带 `COMPLETE` 阶段抛 LoadingFailedException、**整个服务器起不来**（实测踩到）。
        console.info('[SHANHAI-SYMEL] element FAILED id=' + k3 + ' err=' + SHANHAI_EL_BAD[k3])
    }
})

// ─────────────────────────────────────────────────────────────────────────────
// ② 材质（每个符号一个；**必须有粉或流体才能当配方输出**）
// ─────────────────────────────────────────────────────────────────────────────
GTCEuStartupEvents.registry('gtceu:material', function (event) {
    var ok = 0
    var bad = 0
    var errs = ''
    for (var i = 0; i < SHANHAI_SYMBOLS.length; i++) {
        var S = SHANHAI_SYMBOLS[i]
        try {
            var mb = event.create(S.id)
            // 尽量挂上刚注册的元素 ⇒ 推导出的化学式就是这个符号本身
            try {
                var el = GTElements.get(S.id)
                if (el !== null && el !== undefined) { mb = mb.element(el) }
            } catch (e1) {
                // 元素不在（或取不到）也继续，材质本身仍要有粉/流体，否则不能当输出
            }
            mb = mb.color(0xFFFFFF)
            mb = mb.dust()      // ← "要有粉"
            mb = mb.fluid()     // ← "或流体"
            ok = ok + 1
        } catch (e) {
            bad = bad + 1
            if (errs.length < 1200) { errs = errs + S.id + '(' + S.hex + ') => ' + e + ' | ' }
        }
    }
    console.info('[SHANHAI-SYMEL] material registry => ok=' + ok + ' failed=' + bad
        + ' total=' + SHANHAI_SYMBOLS.length)
    // 同上：startup 脚本里不用 console.error
    if (bad > 0) { console.info('[SHANHAI-SYMEL] material FAILED: ' + errs) }

    // ─────────────────────────────────────────────────────────────────────────
    // ②b 显示名 = 符号（用户 2026-09-26 批准「可以改」）
    //    🔴 **只改【显示名 name】** —— 绝不动 `symbol` 字段、绝不动任何 id。
    //       （公式解析全靠 symbol；id 改了会让已注册的东西全废。）
    //    ⚠️ 为什么放在这里：`ElementBuilder` 的构造器把 name **强制设成 ResourceLocation
    //       的 path**（如 `zhe`），而 Rhino **拒绝**写 builder 的字段（实测报
    //       `has no public instance field or method named "name"`）。
    //       但 `Element` 上有 **public setter `public void name(String)`** ⇒ 只能在
    //       【元素已注册之后】把 Element 取回来再改。此刻 `GTElements.get()` 已可用
    //       （上面 ② 的 `.element(el)` 正是这么拿的）。
    //    ✅ 自证方式：把 name() / symbol() **逐字符读回成十六进制码位**，与期望值
    //       （如 Ж = U+0416）**逐字符比对** —— 不接受"没报错"当证据。
    //    🔴 注意：本块自己那次读回（下一行 readback）在【注册期】跑，那时元素还没被
    //       postEvent 填好 ⇒ 它读到空串是正常的、**不代表失败**。真正的判据是
    //       **注册完成之后**（server_scripts）再读一次 —— 见交付报告里的 A/B 实测。
    // ─────────────────────────────────────────────────────────────────────────
    var nSetOK = 0
    var nSetBad = 0
    var nHexMatch = 0
    var nHexMismatch = 0
    var rb = ''
    for (var n = 0; n < SHANHAI_SYMBOLS.length; n++) {
        var NS = SHANHAI_SYMBOLS[n]
        try {
            var ne = GTElements.get(NS.id)
            if (ne === null || ne === undefined) {
                nSetBad = nSetBad + 1
                rb = rb + NS.id + '=MISSING;'
                continue
            }
            ne.name(NS.sym)                    // ← 就是这一行
            nSetOK = nSetOK + 1
            var hName = shanhaiHexOf(ne.name())
            var hSym = shanhaiHexOf(ne.symbol())
            if (hName === NS.hex && hSym === NS.hex) { nHexMatch = nHexMatch + 1 }
            else { nHexMismatch = nHexMismatch + 1 }
            rb = rb + NS.id + '=' + hName + '/' + hSym + ';'
        } catch (e2) {
            nSetBad = nSetBad + 1
            rb = rb + NS.id + '=ERR(' + e2 + ');'
        }
    }
    console.info('[SHANHAI-SYMEL] name-set => setok=' + nSetOK + ' setbad=' + nSetBad
        + ' hexmatch=' + nHexMatch + ' hexmismatch=' + nHexMismatch
        + ' expect=' + SHANHAI_SYMBOLS.length)
    console.info('[SHANHAI-SYMEL] readback name/symbol as HEX => ' + rb)
})

// ─────────────────────────────────────────────────────────────────────────────
// ③ 判据（注册期跑不到这里也没关系，两条 info 行才是判据）
//    ⚠️ 期望：element ok=9 failed=0 ／ material ok=9 failed=0
//    ⚠️ 每个符号的输出 id（拿去写解构配方用）：
//        物品 = gtceu:<id>_dust     （如 gtceu:zhe_dust）
//        流体 = gtceu:<id>          （如 gtceu:zhe）
// =============================================================================
