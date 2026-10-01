// priority: 1
// =============================================================================
// [client_scripts]shanhai_item_description.js
//   「特殊文字渲染」全量测试描述 —— 2026-09-27 用户点单第 8 条。
//
//   用户原话（逐字）：「新建client_scripts文件夹，在里面新建[client_scripts]shanhai_item_
//     description.js，并为测试物品添加描述（描述内容就是把所有jar中我们写的那种特殊的文字渲染都测试一遍）」
//
//   🔴 语法（来源：ShanhaiTextParser.java，全是读源码得来，不是猜）：
//         [前缀]&$[效果字符][样式名]-[正文]
//         · &$   = 标记起点；& 之前的文本原样绘制（叫 prefix）
//         · 效果字符（EFFECT_SYMBOLS = "@~#*%!^?+>`"）可以写在 $ 【之前】或【之后】
//         · 样式名 = ShanhaiTextPalette.STYLES 里那 16 个（大小写不敏感）
//         · - 之后 = 正文（色板染色只作用于这一段）
//   🔴 两条【退化】规则（用户 2026-09-27 裁决 ③）：
//         ① 样式名没移植 ⇒ 剥掉码 + 不染色（而不是回落成彩虹）
//         ② 效果字符没实现 ⇒ 丢掉那个效果、保留颜色
//   🔴 两条【不是我们的码 ⇒ 交回原版渲染】：
//         ① 正文里出现 § 与 &$ 混用   ② `-` 后为空串
//
//   17 个样式 / 11 个效果字符 / 4 条退化路径 —— 全部在下面各测一遍。
// =============================================================================

ItemEvents.tooltip(event => {
    // ────────────────────────────────────────────────────────────────
    // §1 全部 16 个样式（一条一行，测试物品挂在 test_item 上）
    // ────────────────────────────────────────────────────────────────
    event.add("shanhai:test_dynamic_text", [
        "§6§l═══ 特殊文字渲染 · 样式测试（16 个）═══",
        "&$body_golden-01 body_golden ｜ 正文慢速 · 11 色 · 200ms/格（横幅用）",
        "&$body_moss-02 body_moss ｜ 正文慢速 · 苔绿",
        "&$body_aurora-03 body_aurora ｜ 正文慢速 · 极光",
        "&$body_silver-04 body_silver ｜ 正文慢速 · 银",
        "&$ultimate-05 ultimate ｜ 标题样式 · ULTIMATE_RAINBOW",
        "&$golden-06 golden ｜ 标题样式 · 31 色 GOLDEN",
        "&$magic-07 magic ｜ 标题样式 · MAGIC",
        "&$water-08 water ｜ 标题样式 · WATER",
        "&$aurora-09 aurora ｜ 标题样式 · 25 色 AURORA",
        "&$neon-10 neon ｜ 标题样式 · NEON",
        "&$crimson-11 crimson ｜ 标题样式 · CRIMSON",
        "&$cosmic-12 cosmic ｜ 标题样式 · COSMIC（只在上游 GTL5.23 用过）",
        "&$electric-13 electric ｜ 标题样式 · ELECTRIC",
        "&$ultimateRainbow-14 ultimateRainbow ｜ 上游未注册 ⇒ 显式登记成 ULTIMATE_RAINBOW",
        "&$gray-15 gray ｜ 上游未注册 ⇒ 显式登记成 ULTIMATE_RAINBOW",
        "&$green-16 green ｜ 上游未注册 ⇒ 显式登记成 ULTIMATE_RAINBOW"
    ])

    // ────────────────────────────────────────────────────────────────
    // §2 全部 11 个效果字符（挂在 test_item 上，与样式测试分开看）
    // ────────────────────────────────────────────────────────────────
    event.add("shanhai:test_item", [
        "§6§l═══ 特殊文字渲染 · 效果字符测试（11 个）═══",
        "&$*ultimate-效果 [*] floatX ｜ ✅ 已实现：水平浮动",
        "&$?ultimate-效果 [?] glitch ｜ ✅ 已实现：位移 + 掺色（青 55FFFF / 红 FF5555）",
        "&$@ultimate-效果 [@] unsupported ｜ ⚠️ 未实现 ⇒ 应【丢弃效果、保留颜色】",
        "&$~ultimate-效果 [~] unsupported ｜ ⚠️ 未实现 ⇒ 丢效果保颜色",
        "&$#ultimate-效果 [#] unsupported ｜ ⚠️ 未实现 ⇒ 丢效果保颜色",
        "&$%ultimate-效果 [%] unsupported ｜ ⚠️ 未实现 ⇒ 丢效果保颜色",
        "&$!ultimate-效果 [!] unsupported ｜ ⚠️ 未实现 ⇒ 丢效果保颜色",
        "&$^ultimate-效果 [^] unsupported ｜ ⚠️ 未实现 ⇒ 丢效果保颜色",
        "&$+ultimate-效果 [+] unsupported ｜ ⚠️ 未实现 ⇒ 丢效果保颜色",
        "&$>ultimate-效果 [>] unsupported ｜ ⚠️ 未实现 ⇒ 丢效果保颜色",
        "&$`ultimate-效果 [反引号] unsupported ｜ ⚠️ 未实现 ⇒ 丢效果保颜色"
    ])

    // ────────────────────────────────────────────────────────────────
    // §3 效果字符写在 $ 【之前】（解析器两种都支持，这里验证前一种）
    // ────────────────────────────────────────────────────────────────
    event.add("shanhai:test_dynamic_text", [
        "§6§l═══ 效果字符位置测试（$ 前 / $ 后）═══",
        "&$*?golden-两个已实现效果叠一起（* floatX + ? glitch）",
        "&*$golden-效果写在 \$ 【之前】⇒ floatX",
        "&?$magic-效果写在 \$ 【之前】⇒ glitch",
        "&$golden-效果写在 \$ 【之后】⇒ 无效果（对照）"
    ])

    // ────────────────────────────────────────────────────────────────
    // §4 退化路径（4 条）—— 看它是否【优雅退化】而不是崩/画错
    // ────────────────────────────────────────────────────────────────
    event.add("shanhai:test_item", [
        "§6§l═══ 退化路径测试（6 条，全部【真触发】）═══",
        // 🔴 2026-09-27 修正：原来这一段的第 3 条【描述文字里提到 § 但正文本身不含 §】⇒ 触发不了
        //    「§ 与 &$ 混用 ⇒ 交回原版渲染」那条路径。下面 ③④ 是正文/前缀【真的】含 § 的版本。
        //    判据原文（ShanhaiTextParser.java L260）: if (prefix.indexOf(§) >= 0 || body.indexOf(§) >= 0) return NOT_OURS
        "&$nonexistent_style-① 样式名没移植 ⇒ 应剥掉码 + 不染色（白字；不许回落成彩虹）",
        "&$ultimateRainbow_typo-② 样式名拼错 ⇒ 同上（也是剥码 + 不染色）",
        "&$?body_golden-③ §c【正文】真的含 § ⇒ 应【交回原版】原样显示：本行应能看到 &$?body_golden- 这串码",
        "§a&$ultimate-④ 【前缀】真的含 §（§a），而【正文完全没有 §】 ⇒ 也应交回原版：本行应看到 §a 与 &$ultimate- 都在",
        "⑤ 空正文测试（本行右边什么都没有是对的） &$ultimate-",
        "普通文本 AT&T - 没有 &$ ⇒ 原样显示（不是我们的码）"
    ])

    // ────────────────────────────────────────────────────────────────
    // §5 正文为空 / 无 $ 的边界（都应判「不是我们的码」⇒ 原样显示）
    // ────────────────────────────────────────────────────────────────
    event.add("shanhai:test_dynamic_text", [
        "§6§l═══ 边界用例（3 条，前 2 条【是我们的码】会被染色，第 3 条原样显示）═══",
        "&$ultimate-正文非空（样式命中 ⇒ 应被染成彩虹）",
        "&$ultimate-  正文以两个空格开头（也应被染色，看宽度修正）",
        "&abcd - 有 & 没有 $ ⇒ 原样"
    ])
})

// =============================================================================
// §6 【2026-09-28 新增】世线残片 ×8 + 物质模块 ×17 的描述
//
//   用户原话（逐字）：
//     「还有，把提供的跨配方并行（线程）数写在世线残片的描述中，
//       并且把提供的并行数补充在各等级物质模块的描述中」
//
//   🔴 单一真源原则（本机血账：同一个数写两份必然漂移）：
//     这里【一个数字都不手写】—— 全部 `Java.loadClass` 到 Java 侧那张权威表
//     `com.shanhai.common.thread.ShanhaiConcurrencyTables` 现读：
//       · 机器算线程用的是同一张表（`finalThreads`）
//       · 所以"描述里写的" 与 "机器真正算的" 是同一份数据，不可能对不上
//     那个类是【纯 java.util】的（连 ShanhaiMod 都不引用）⇒ 加载它不会触发任何游戏注册表。
//
//   ⚠️ 失败可见性：client_scripts 只在【客户端】跑，无头专服冒烟证明不了这一段。
//     若 loadClass 失败（例如将来有人改动类名/包名），下面会打一条 console.error，
//     在 `<实例>\logs\kubejs\client.log` 里可 grep「[SHANHAI-DESC]」——
//     "没扫到" 与 "没发生" 必须能分开。届时物品会【少掉描述】而不是显示错数字（宁可缺，不可假）。
// =============================================================================

var SHANHAI_CONC = null
var SHANHAI_CONC_ERR = ""
try {
    SHANHAI_CONC = Java.loadClass("com.shanhai.common.thread.ShanhaiConcurrencyTables")
} catch (e) {
    SHANHAI_CONC = null
    SHANHAI_CONC_ERR = "" + e
}

/** Java 侧返回的是「多行用一个 \n 拼起来的单个字符串」，这里拆成 KubeJS 要的数组。 */
function shanhaiLines(s) {
    return ("" + s).split("\n")
}

ItemEvents.tooltip(event => {
    if (SHANHAI_CONC == null) {
        console.error("[SHANHAI-DESC] 无法加载 com.shanhai.common.thread.ShanhaiConcurrencyTables ⇒ "
            + "世线残片与物质模块的描述【不会显示】。原因：" + SHANHAI_CONC_ERR)
        return
    }

    var shardN = 0
    var moduleN = 0

    // ① 8 种世线残片：写明各自提供的跨配方并行（线程）
    for (var i = 0; i < SHANHAI_CONC.shardCount(); i++) {
        event.add(SHANHAI_CONC.shardIdAt(i), shanhaiLines(SHANHAI_CONC.shardDescription(i)))
        shardN = shardN + 1
    }

    // ② 17 个物质模块：补上"提供的并行数"（两档并行表都写，避免与机器对不上）
    for (var j = 0; j < SHANHAI_CONC.moduleCount(); j++) {
        event.add(SHANHAI_CONC.moduleIdAt(j), shanhaiLines(SHANHAI_CONC.moduleDescription(j)))
        moduleN = moduleN + 1
    }

    // 单向证据行：证明这段真的跑过（而不是"没扫到"）
    console.info("[SHANHAI-DESC] 已挂载描述：世线残片 " + shardN + " 种 + 物质模块 " + moduleN + " 种"
        + "（数字全部现读自 Java 的 ShanhaiConcurrencyTables，KJS 侧无手写数字表）")
})
