// priority: 1
// =============================================================================
// [server_scripts]shanhai_recipes.js  —— 用户「游戏里编好的 AE 样板」落地为 KubeJS
//
// ✅ 2026-09-26 【终版 · FINAL】：本文件原先挂着的问题【三处全部落定】——
//    ① 新类型「原初物质解构」= `gtceu:primordial_matter_deconstruction`（用户裁决「就用这个」）；
//    ② `photon_separation` 的 setMaxIOSize 由 (2,4,2,2) **放宽到 (4,10,2,2)**（用户裁决：物品入 2→4、物品出 4→10、流体 2/2 不动）；
//    ③ 星门 3 条的 EUt 按「**MAX+8** = MAX 电压 + 4^8 A」= **2^47 = 140737488355328**（用户裁决）。
//    ⇒ 后果：**2026-09-26 放宽后不再溢出** —— 原先那 3 条 SLOT-OVER 溢出**已消除**，
//       代际产物里**既没有 `slotOver` 字段、也没有 SLOT-OVER 告警代码**（整段已删）。
//    ✅ 2026-09-27：【已部署】到 GTL山海9.10test\kubejs\server_scripts\
//    ⚠️ 「原初物质解构」这个配方类型是**本版 shanhai 模组新增**的（jar 侧已注册 + 有专属 .rtui），
//       本文件没有也不需要有它的配方；它只是让 JEI/机器多出一个可用分类。
//
// 🔴 本次是【增量更新】（用户原话逐字：「这次添加配方就和上次一样啊，上次我还帮你理解了」
//    ／「每个元件都有新增的配方」）：
//    · 老那 8 条【原样保留、一个字不改】（它们的 spec 从上一版文件逐字搬过来）；
//    · 新增 30 条【追加】进同一个文件。
//    ⇒ 实测（2026-09-27 运行期）：工作台 18 条 + GT 机器 61 条 = 79 条。
//
// 源数据：C:\Users\david\Desktop\PF.txt
//   SHA256 = 086D2F5E83DB2E390EF42E473EEC7FCE046269BAE691AF17FDC0CBAC83A730F2
//   41,564 B / 单行 NBT / 一个 minecraft:chest
//
// =============================================================================
// §0 检查器自证（先说清"我凭什么信自己没看漏"）
// =============================================================================
//  · `ae2:processing_pattern` 纯文本出现次数 = 38
//  · 逐条解析成功 = 38     ⇒ 两边相等，无静默漏条
//  · `in` 恒为 81 格 / `out` 恒为 27 格（AE2 定长数组，空槽是 `{}`）—— 79/79 条都对
//  · 正面对照：解析出的 8 条旧配方与上一版文件头 §3 的记录【逐字一致】
//    ⇒ 说明解析器看的是同一批东西（这是"解析器自己是对的"的证据，不是自说自话）
//
// =============================================================================
// §1 元件对照（这次 vs 上次）—— 用户要的第一问
// =============================================================================
//   本次 Slot | 元件名（display.Name 逐字） | 本次条数 | 上次 | 差
//   ----------|---------------------------|---------|------|----
//   Slot 0    | 处理样板-星门(MAX+16)      |    3    | 无   | 全新元件
//   Slot 1    | 合成样板                   |    5    | 17   | +12 ← 2026-09-27 改为从 PF.txt 全量生成
//   Slot 2    | 处理样板ULV                |   19    | 1    | +18
//   Slot 3    | 处理样板LV                 |   11    | 2    | +9
//   ----------|---------------------------|---------|------|----
//   合计      |                            |   38    | 8    | +30
//
//  ⚠️ 与用户口径的一处出入（如实报，不顺着说）：用户说「每个元件都有新增的配方」，
//     ⚠️ 2026-09-27 更正：本句【已过期】。用户更新 PF.txt 后合成样板 = 17 条，
//  ⚠️ 元件【槽位变了】：上次 Slot 0 = 处理样板ULV、Slot 2 = 处理样板LV；
//     这次整体后移（ULV→Slot 2，LV→Slot 3），Slot 0 换成了新元件。
//     判据：条数与内容吻合（ULV 里仍含旧 ①、LV 里仍含旧 ⑦⑧）⇒ 是同一个元件被挪了槽，不是新元件。
//
// =============================================================================
// §2 中文名 → `gtceu:<id>` 映射（口径沿用上次：lang 里【唯一精确等于】那条）
// =============================================================================
//   纸上中文名     | gtceu id                            | 来源          | 条数
//   ---------------|-------------------------------------|---------------|-----
//   电路组装机     | gtceu:circuit_assembler             | gtceu zh_cn   |  1
//   土高炉         | gtceu:primitive_blast_furnace       | gtceu zh_cn   | 14
//   量子化现实重构 | gtceu:spacetime_distortion          | shanhai zh_cn |  3
//   原初奇点反演   | gtceu:primordial_singularity_inversion | shanhai zh_cn | 3
//   物质流凝结     | gtceu:matter_flow_condensation      | shanhai zh_cn |  2
//   物质模块铸造   | gtceu:matter_module_casting         | shanhai zh_cn |  2
//   光子虹吸       | gtceu:photon_siphon                 | shanhai zh_cn |  2
//   光子分离       | gtceu:photon_separation             | shanhai zh_cn |  3
//   星际物质吸取   | gtceu:interstellar_matter_absorption| shanhai zh_cn |  1
//   ✅世线电路板组装| gtceu:wl_board_circuit_assembly       | shanhai zh_cn |  2
//
//  ✅ 已消除的唯一歧义：纸上写「世线电路板组装」，lang 里是「世线板电路组装」——
//     用户 2026-09-26 亲自裁决：「我写错了」⇒ 按 lang 的 `gtceu:wl_board_circuit_assembly` 落，
//     原先的 ⚠️ 待裁决标记已按用户裁决【移除】。
//
// =============================================================================
// §3 配方类型 id 的存在性核实（正面对照，不是猜）
// =============================================================================
//  · 8 个 shanhai 类型：解 `mods\shanhai-0.1.0.jar` 的
//      `com/shanhai/common/recipe/ShanhaiRecipeTypes.class` 常量池，**逐个命中**：
//      spacetime_distortion / primordial_singularity_inversion / matter_flow_condensation /
//      matter_module_casting / photon_siphon / photon_separation /
//      interstellar_matter_absorption / wl_board_circuit_assembly  ⇒ **8/8 在**。
//  · gtceu:primitive_blast_furnace：游戏自己的导出表
//      `local\kubejs\export\recipes\gtceu\primitive_blast_furnace\` = **18 个配方文件**
//      ⇒ 这个配方类型在本包里**真实存在且有配方**。
//  · gtceu:circuit_assembler：同目录下有 **90** 个配方文件。
//  · ⚠️ 反例（防"假否定"）：`local\kubejs\export\registries\item.json` 时间戳 = 2026-09-10 19:19，
//      **早于** shanhai-0.1.0.jar（2026-09-26 11:11）⇒ 那张导出表里 `gtceu:spacetime_distortion` 
//      之类的目录**查不到**。这【不是"不存在"】，是"导出表过时"。
//      ⇒ 所以 shanhai 类型一律改用【已部署 jar 的字节码/lang】取证，不看那张旧表。
//
// =============================================================================
// §4 槽位核对（用 jar 里【真的】setMaxIOSize 值，不是估的）
// =============================================================================
//  shanhai 类型：ShanhaiRecipeTypes.java 的 .setMaxIOSize(物品入,物品出,流体入,流体出)：
//     circuit_assembler                  = (6, 1, 1, 0)
//     primitive_blast_furnace            = (3, 3, 0, 0)
//     spacetime_distortion               = (9, 6, 6, 5)
//     primordial_singularity_inversion   = (12, 3, 6, 3)
//     matter_flow_condensation           = (4, 2, 2, 2)
//     matter_module_casting              = (17, 1, 4, 0)
//     photon_siphon                      = (4, 2, 2, 2)
//     photon_separation                  = (4, 10, 2, 2)
//     interstellar_matter_absorption     = (2, 2, 2, 2)
//     wl_board_circuit_assembly          = (9, 3, 6, 4)
//  · gtceu:primitive_blast_furnace = (3, 3, 0, 0)
//      —— 出处：gtceu jar `GTRecipeTypes.class` 字节码偏移 3028-3032：
//         iconst_3 / iconst_3 / iconst_0 / iconst_0 → setMaxIOSize
//      ⚠️ 流体入=0 流体出=0 ⇒ 土高炉配方【不许带流体】，本文件 14 条土高炉确实一条流体都没有 ✓
//      ⚠️ 18 条现存 primitive_blast_furnace 配方的实测最大值是 (2,2,0,0)（没填满 3）
//  · gtceu:circuit_assembler = (6, 1, 1, 0)（上一版已核，出处见上一版 §1）
//
//  🔴 三条占用规则（沿用上次口径，**没变**）：
//     · `.notConsumable(...)`【占】1 个物品输入槽（和普通输入一样算）
//     · `.circuit(N)`【占】1 个物品输入槽
//     · 物质模块【等级门槛】不占槽（它是准入判据，不是输入物）
//  ⇒ 每条新增配方都算过 slotIn / slotOut，逐条结果见 §7 的表。
//
//  🔴🔴 槽位核对【曾查出 3 条溢出】—— ✅ **本版已由用户裁决解决**：
//
//    PF.txt#  | 配方 id 后缀               | 类型               | 物品入 | 旧 cap | 新 cap
//    ---------|---------------------------|--------------------|--------|--------|-------
//    #31      | shanhai:pf/photon_2       | photon_separation  |   3    |  2 ❌  |  4 ✓
//    #33      | shanhai:pf/electron       | photon_separation  |   3    |  2 ❌  |  4 ✓
//    #36      | shanhai:pf/photon_rainbow | photon_separation  |   3    |  2 ❌  |  4 ✓
//
//    三条都是同一形状：1~2 个真物品 + 1 个 notConsumable(力场发生器) + 1 个 .circuit(1) = 3 格，
//    而旧 photon_separation 的 setMaxIOSize 是 (2,4,2,2) ⇒ 物品入只有 2 格。
//    🔴 用户 2026-09-26 裁决（逐字）：「photon_separation 的 setMaxIOSize(2, 4, 2, 2) ⇒ (4, 10, 2, 2)」
//       ⇒ 物品入 2 ⇒ **4**（3 格装得下，留 1 格余量）、物品出 4 ⇒ **10**；**流体 2/2 不动**。
//    ⚠️ 旧 cap (2,4,2,2) 是从【当时已部署的 jar】字节码读出来的真值：
//       mods\shanhai-0.1.0.jar!com/shanhai/common/recipe/ShanhaiRecipeTypes.class
//       photon_separation 段：iconst_2 / iconst_4 / iconst_2 / iconst_2 → setMaxIOSize
//    ✅ 新 cap (4,10,2,2) 是【本版 jar】的真值，出处 = javap -c 成品 jar 的 ShanhaiRecipeTypes.init()：
//       该调用点操作数 = iconst_4 / bipush 10 / iconst_2 / iconst_2（2026-09-26 实测）。
//    ⇒ 2026-09-26 放宽后不再溢出 ⇒ 这 3 条的 `slotOver` 标记与整段 SLOT-OVER 告警【已删除】。
//    ⚠️ 附注：若把物质模块从"等级门槛"降级回"催化剂"（§7① 的另一条路），这三条会变成 4 格 ——
//       正好用满 cap[0]=4，仍然装得下（旧 cap=2 时才是真的装不下）。
//
// =============================================================================
// §5 KubeJS / Rhino 写法纪律（与上一版完全一致，刻意只用最保守的写法）
// =============================================================================
//  · 全局只用 var；不用 let/const、不用箭头函数、不用模板字符串、不用 ?.、不用解构
//  · 不用 conditions（assembler 类配方设它会报错）
//  · 编程电路用 .circuit(数字)——照宿主现成写法（gtceu.js:3301-3303 / ae2.js:361-362）
//  · 「不消耗（催化剂）」用 .notConsumable(...)，照宿主 gtceu.js:935 / 3302 / 9955
//  · 每条配方各自包 try/catch —— 一条失败只让一条失败，不连坐
//  · 配方 id 全部显式给死（.id(...)），避免 /kubejs reload 时自动 id 变化导致旧配方残留
//
// =============================================================================
// §6 🔴 改名纸 = 注记，不是配方输入（沿用上次口径）
// =============================================================================
//  ⇒ 本文件【任何地方都不出现 minecraft:paper】—— 除非它是**没改名的真产物**
//    （本次确实有一条：#25「甘蔗 → 2x minecraft:paper」是真的出纸，那条保留）。
//  · 纸有三种：
//      ① 「配方类型：XXX」（也有裸写类型名的，如「光子虹吸」「电路组装机」）⇒ 决定用哪台机器
//      ② 「Ns」⇒ 决定耗时（×20 = tick）
//      ③ 备注（本次三种：物质模块是催化剂 / 力场发生器是催化剂 / 电子中微子产出概率5%）
//  · 🔴 纸写在 in 里，也可能写在 **out** 里 —— #33 的「电子中微子产出概率5%」就在 out[3]。
//    本文件把"带自定义名的纸"从 out 里剔除，只留真产物。
//
// =============================================================================
// §7 🔴 三处口径（**先报出来，没自己选**）：
// =============================================================================
//  ①「物质模块是催化剂」—— 27 条样板里有这张纸。
//     ✅ 用户 2026-09-26 裁决（原话逐字）：「以后物质模块是催化剂指的都是我们今天刚写好的机制」
//     ⇒ 「物质模块是催化剂」= 今天刚做好的【等级门槛 ModuleLevelCondition】，不再是老写法。
//       · 新机制（等级门槛，**唯一口径**）： .addCondition(new ModuleLevelCondition('shanhai:<模块>', 1))  ← 不占槽
//       · 老写法（催化剂）： .notConsumable('Nx shanhai:<模块>')                                  ← 占 1 槽
//     · 已取证：`mods\shanhai-0.1.0.jar` 里 **存在** 
//       `com/shanhai/machine/module/ModuleLevelCondition.class` ⇒ 上一版"待落地"的状态已结束。
//     · 本文件的做法：**默认走等级门槛**；`SHANHAI_PF_MODULE_MODE` 一行可切。
//       ⚠️ 降级通道【保留】（jar 没绑 / `typeof` 判不到类时自动退回催化剂，配方不会消失）。
//     · ⚠️ 另有 3 条（PF.txt #1/#2/#3）有这张纸却【没有物质模块物品】⇒ 门槛无从挂起，
//       本文件按"无门槛无催化剂"落，并在脚本里逐条注明。
//  ②「力场发生器是催化剂」—— 3 条（#31/#33/#36），每条同格就有 `gtceu:lv_field_generator`。
//       本文件按 `.notConsumable('1x gtceu:lv_field_generator')` 落。**待确认电压档（LV？）**
//  ③「电子中微子产出概率5%」—— 1 条（#33），且写在该样板的 **out[3]**，紧邻 out[2] 的
//       `shanhai:electron_neutrino`。
//     ✅ 用户 2026-09-26 裁决（原话逐字）：「吃加成」
//     ⇒ 本文件落 `.chancedOutput('1x shanhai:electron_neutrino', 500, 100)`
//
//     🔴 参数 1：**单位是万分比**（`getMaxChancedValue()` 反读 = `sipush 10000`）
//        ⇒ **5% = 500**，不是 5000（5000 = 50%，会差 10 倍）。
//        取证：宿主 gtceu.js:6148/6212/6250 (2000,0)=20% ／ :8405 (1000,0)=10% ／
//              :6717 (200,20)=2% ／ ad.js:96 (5000,0)=50%；
//              且游戏导出表里 chance 的最大值就是 10000。
//
//     🔴 参数 2：**它不是"加成上限"，是【每超频一级的加成量 tierChanceBoost】**。
//        字节码实证 `GTRecipeBuilder.chancedOutput(ItemStack,int,int)`：
//          67: aload_0 / 68: iload_2 / 69: putfield chance:I
//          72: aload_0 / 73: iload_3 / 74: putfield tierChanceBoost:I     <-- 第三个参数进这里
//        ⚠️ `maxChance` 从 KubeJS 侧【设不了】，它保持默认 10000。
//
//     🔴 **100 这个数是从哪来的（不是猜的）**：GTCEu 自己的"5% 档副产"标准值。
//        反读游戏导出表 93,897 个文件、348,092 条 chanced 记录后，
//        `chance=500 / maxChance=10000 / tierChanceBoost=100` 这个三元组出现 **414 次**，
//        全部来自 GTCEu 的矿石副产线：`gtceu:macerator` 138 ／ `gtceu:integrated_ore_processor` 138 ／
//        `gtceu:space_ore_processor` 138。⇒ 这就是本包"5% 且带加成"的标准配法。
//        （同族还有 1400/850、200/20、50/5 —— 即 GTCEu 的 14% / 5% / 2% / 0.5% 副产阶梯。）
//
//     🔴 **加成到底怎么算**（`ChanceBoostFunction.OVERCLOCK` 反读，逐条对字节码）：
//          int tierDiff = machineTier - recipeTier;
//          if (tierDiff <= 0) return chance;          // 没超频 ⇒ 原始概率，吃不到加成
//          if (recipeTier == 0) tierDiff = tierDiff - 1;
//          return chance + tierChanceBoost * tierDiff;
//        ⇒ #33 的 recipeTier = LV(1)，实际概率随机器超频：
//            LV(1) → 5% ／ MV(2) → 6% ／ HV(3) → 7% ／ EV(5) → 9% ／ MAX(14) → 18%
//          （公式里没有封顶，但 `maxChance` = 10000 = 100% 就是天花板）
//
// =============================================================================
// §8 🔴 元件「处理样板-星门」的 EUt —— 算式 + 溢出判断
//    （2026-09-26 第二次改判：MAX+16 ⇒ **MAX+8**）
// =============================================================================
//  ✅ 用户最新裁决（原话逐字）：「溢出那就算了，改成 max+8=max,4^8A」
//     ⇒ 电压 = MAX 档，电流 = **4^8 A**。
//     ⚠️ 本段【覆盖】上一版「MAX+16=MAX，4^16A」的裁决；上一版的算式与结论
//        按本工程惯例【原样保留在 ⑥ 作留档】，但不再是当前口径。
//
//  ① V[MAX] 的真值 —— 【从字节码读的，不是从注释抄的】
//     `libs\gtceu-1.20.1-1.4.4.jar!com/gregtechceu/gtceu/api/GTValues.class`（javap -p -c）
//     该 class 的 sha256 = 7A7275B8018D78876D2F1AB3D6B14644D7D5145EDBC16D35C3B9D99146D33375
//     `<clinit>`：`bipush 14` → `ldc2_w // long 2147483648l` → `lastore` → `putstatic V:[J`
//     V 是 **15 项 long[]**，索引 0..14 = ULV..MAX（索引 13 = 536870912）；同文件 VN[14] = `"MAX"`
//     ⇒ **V[MAX] = 2147483648（= 2^31）**
//     ⚠️ 注意：**不是** Integer.MAX_VALUE(2147483647)，也**不是** 1.7 时代注释里那种写法 ——
//        这个数字如果照注释猜，整条算式的答案会差一位。
//
//  ② 4^8 的值
//     4^8 = (2^2)^8 = **2^16 = 65536**
//
//  ③ 算式与结果（精确整数运算，不是浮点）
//     EUt = V[MAX] × 4^8
//         = 2147483648 × 65536
//         = 2^31 × 2^16
//         = **2^47 = 140737488355328**
//
//  ④ 🔴 溢出判断：**【不溢出】✓**（这是本次改判的全部目的）
//        Long.MAX_VALUE = 2^63 − 1 = 9223372036854775807
//        2^47 ≈ 1.41e14 ⇒ 只有 Long.MAX 的 **1/65536**（余量 65536 倍）
//        ⇒ 2^47 是合法 long，**不存在回绕**。
//        `.EUt` 的签名已核实：`GTRecipeBuilder EUt(long)` —— 是 long，不是 int，2^47 装得下。
//
//  ⑤ ⇒ 本文件的落地：
//        **EUt = 140737488355328**（= V[MAX] × 4^8 = 2^47）
//        —— "电压 × 电流"这一次可以**整体**进 EUt，不再需要像上一版那样拆开。
//        🔴 事实提示（不是拦阻、也不是待裁决项）：2^47 EUt/t = **65536 倍 MAX 电压**。
//           配方的 EUt 是"每 tick 电压需求"，GT 侧要能找到供得起这个数的能源仓它才跑得起来。
//           这是数值/玩法层面的事；本条只负责"算式不溢出、写进去的值不违法"。
//        ⚠️ 这三条配方所在的**元件名**仍写作「处理样板-星门(MAX+16)」——
//           那是 AE2 样板自己的**显示名**（PF.txt 源数据原文），本次不要求改样板，
//           故文件里保持原样；本段只改这 3 条配方的 EUt 值。
//
//  ⑥ 📌 留档：**已作废的上一版口径**（2026-09-26 早先裁决「MAX+16=MAX，4^16A」）
//     4^16 = 2^32 = 4294967296
//     EUt = 2^31 × 2^32 = 2^63 = 9223372036854775808 = Long.MAX_VALUE **+ 1**
//     ⇒ **恰好越界 1** ⇒ Java long 二进制补码回绕成 Long.MIN_VALUE = −9223372036854775808（负数！）
//     ⇒ 上一版因此只能把 EUt 写成 V[MAX] = 2147483648（只放电压部分），安培不放进 EUt。
//     ⇒ 改成 4^8 后该约束消失，故本版把 2^47 整体写进 EUt。
//
//  · 「合成样板」的 5 条是工作台配方（event.shaped，有序），摆位 = in 下标 0..8 行优先 ——
//      这是上次已由用户核对确认的口径（原话「这下对了」），本次**一个字没改**。
//
// =============================================================================
var SHANHAI_PF_TAG = '[SHANHAI-PF]'

// 🔴 §7① 的切换点：'gate' = 物质模块等级门槛（默认）／ 'catalyst' = 老写法（不消耗催化剂）
var SHANHAI_PF_MODULE_MODE = 'gate'

// 老山海 module_level 条件类是否已由 jar 侧注册并绑定（见 §7①）。
// ⚠️ 必须用 typeof 判：直接引用未绑定的标识符会 ReferenceError，而 typeof 不会抛。
var SHANHAI_HAS_MODULE_LEVEL_CONDITION = (typeof ModuleLevelCondition !== 'undefined')

// 解析老山海写法 "Nx <物品id>" -> [物品id, 数量]（无 Nx 时数量默认 1）。
// 逐句对齐 DShanhaiRecipeEngine.addOneCondition 的解析口径（照上一版搬）。
var shanhaiParseModuleLevel = function (text) {
    var s = String(text).trim()
    var count = 1
    var id = s
    var xi = s.indexOf('x')
    var c0 = s.length > 0 ? s.charCodeAt(0) : 0
    if (xi > 0 && c0 >= 48 && c0 <= 57) {
        count = parseInt(s.substring(0, xi), 10)
        if (isNaN(count) || count <= 0) { count = 1 }
        id = s.substring(xi + 1).trim()
    }
    return [id, count]
}

// 该走等级门槛吗？（模式 = gate  且  jar 侧条件类已绑定）
var shanhaiUseLevelGate = function (r) {
    if (SHANHAI_PF_MODULE_MODE !== 'gate') { return false }
    if (!r.moduleLevelRequirement) { return false }
    return SHANHAI_HAS_MODULE_LEVEL_CONDITION
}

// -----------------------------------------------------------------------------
// 老 ①..⑧ 里的 ②③④⑤⑥：工作台配方（有序 shaped）
// 🔴 与上一版【逐字相同】，本次未改动（用户口径：老 8 条保留不动）
// 摆位 = PF.txt 该样板 in 数组下标 0..8 按【行优先】
// -----------------------------------------------------------------------------
var shanhaiPfShaped = [
    {
        id: 'shanhai:pf_crafting/primordial_divergence_generator',
        out: 'shanhai:primordial_divergence_generator',
        pattern: ['ABA', 'BCB', 'ABA'],
        keys: {
            A: 'gtceu:primitive_void_ore',
            B: 'gtceu:ulv_fragment_world_collection_machine',
            C: 'shanhai:primordial_engine_core'
        }
    },
    {
        id: 'shanhai:pf_crafting/wl_board_ulv',
        out: 'shanhai:wl_board_ulv',
        pattern: ['ABA', 'BCB', 'ABA'],
        keys: {
            A: 'gtceu:pulsating_alloy_ingot',
            B: 'gtceu:certus_quartz_gem',
            C: 'kubejs:ulv_universal_circuit'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_engine_core',
        out: 'shanhai:primordial_engine_core',
        pattern: ['PRP', 'MTB', 'AGA'],
        keys: {
            P: 'gtceu:spacetime_tiny_fluid_pipe',
            R: 'gtlcore:primitive_fluid_regulator',
            M: 'thetornproductionline:celestial_secret_deducing_module_ulv',
            T: 'gtlcore:treasures_crystal',
            B: 'shanhai:wl_board_ulv',
            A: 'gtlcore:primitive_robot_arm',
            G: 'ae2:fluix_glass_cable'
        }
    },
    {
        id: 'shanhai:pf_crafting/introductory_material_module',
        out: 'shanhai:introductory_material_module',
        pattern: ['CRD', 'BTB', 'DPC'],
        keys: {
            C: 'gtlcore:mining_crystal',
            R: 'kubejs:reactor_core',
            D: 'minecraft:diamond',
            B: 'shanhai:wl_board_ulv',
            T: 'gtlcore:treasures_crystal',
            P: 'gtceu:spacetime_tiny_fluid_pipe'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_matter_caster',
        out: 'shanhai:primordial_matter_caster',
        pattern: ['LSL', 'SKS', 'LSL'],
        keys: {
            L: 'thetornproductionline:celestial_secret_deducing_module_lv',
            S: 'ae2:molecular_assembler',
            K: 'shanhai:primordial_engine_core'
        }
    },
    {
        id: 'kubejs:industrial_steam_casing',
        out: 'gtceu:industrial_steam_casing',
        pattern: ['AAA', 'BCA', 'DEF'],
        keys: {
            A: 'gtceu:bronze_plate',
            B: '#forge:tools/hammers',
            C: 'gtceu:bronze_frame',
            D: 'gtceu:bronze_rotor',
            E: 'gtceu:bronze_gear',
            F: 'gtceu:bronze_rotor'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_causal_weaving_matrix',
        out: 'shanhai:primordial_causal_weaving_matrix',
        pattern: ['ABA', 'CDC', 'EFE'],
        keys: {
            A: 'thetornproductionline:celestial_secret_deducing_module_lv',
            B: 'shanhai:electron',
            C: 'kubejs:time_twister_wireless',
            D: 'shanhai:primordial_engine_core',
            E: 'shanhai:wl_board_lv',
            F: 'shanhai:electron_neutrino'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_world_fragments_collector',
        out: 'shanhai:primordial_world_fragments_collector',
        pattern: ['ABA', 'CDE', 'AFA'],
        keys: {
            A: 'gtceu:ulv_fragment_world_collection_machine',
            B: 'gtceu:steel_drill_head',
            C: 'shanhai:wl_board_lv',
            D: 'shanhai:primordial_engine_core',
            E: 'thetornproductionline:celestial_secret_deducing_module_lv',
            F: 'gtceu:primitive_void_ore'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_chaotic_ephemeral_deconstruction_crystallization_furnace',
        out: 'shanhai:primordial_chaotic_ephemeral_deconstruction_crystallization_furnace',
        pattern: ['ABC', 'DED', 'FGH'],
        keys: {
            A: 'gtceu:lv_electrolyzer',
            B: 'gtceu:lv_centrifuge',
            C: 'gtceu:lv_sifter',
            D: 'shanhai:wl_board_lv',
            E: 'shanhai:primordial_engine_core',
            F: 'gtceu:lv_ore_washer',
            G: 'gtceu:lv_macerator',
            H: 'gtceu:lv_electromagnetic_separator'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_void_induction_armature',
        out: 'shanhai:primordial_void_induction_armature',
        pattern: ['ABA', 'CDC', 'AEA'],
        keys: {
            A: 'gtceu:generator_array',
            B: 'shanhai:wl_board_lv',
            C: 'gtmthings:lv_wireless_energy_receive_cover',
            D: 'shanhai:primordial_engine_core',
            E: 'shanhai:electron_neutrino'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_molecular_rift_core',
        out: 'shanhai:primordial_molecular_rift_core',
        pattern: ['ABC', 'DED', 'FGH'],
        keys: {
            A: 'gtceu:lv_chemical_reactor',
            B: 'gtceu:lv_fluid_regulator',
            C: 'gtceu:lv_distillery',
            D: 'shanhai:wl_board_lv',
            E: 'shanhai:primordial_engine_core',
            F: 'gtceu:lv_fermenter',
            G: 'gtceu:lv_electric_pump',
            H: 'gtceu:lv_fluid_heater'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_singularity_inversion_core',
        out: 'shanhai:primordial_singularity_inversion_core',
        pattern: ['ABA', 'CDC', 'ABA'],
        keys: {
            A: 'gtlcore:treasures_crystal',
            B: 'gtceu:lv_field_generator',
            C: 'thetornproductionline:celestial_secret_deducing_module_lv',
            D: 'shanhai:primordial_engine_core'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_assembly_line_module',
        out: 'shanhai:primordial_assembly_line_module',
        pattern: ['ABA', 'CDC', 'EBE'],
        keys: {
            A: 'gtceu:lv_assembler',
            B: 'shanhai:wl_board_ulv',
            C: 'thetornproductionline:celestial_secret_deducing_module_ulv',
            D: 'shanhai:primordial_engine_core',
            E: 'gtceu:lv_circuit_assembler'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_engraving_module',
        out: 'shanhai:primordial_engraving_module',
        pattern: ['ABA', 'CDC', 'ABA'],
        keys: {
            A: 'gtceu:lv_laser_engraver',
            B: 'gtceu:glass_lens',
            C: 'shanhai:wl_board_lv',
            D: 'shanhai:primordial_engine_core'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_critical_processing_module',
        out: 'shanhai:primordial_critical_processing_module',
        pattern: ['ABC', 'DED', 'FGH'],
        keys: {
            A: 'gtceu:lv_extruder',
            B: 'gtceu:lv_compressor',
            C: 'gtceu:lv_bender',
            D: 'shanhai:wl_board_lv',
            E: 'shanhai:primordial_engine_core',
            F: 'gtceu:lv_wiremill',
            G: 'gtceu:lv_forge_hammer',
            H: 'gtceu:lv_forming_press'
        }
    },
    {
        id: 'shanhai:pf_crafting/taixu_smelting_furnace',
        out: 'shanhai:taixu_smelting_furnace',
        pattern: ['ABA', 'CDC', 'EBE'],
        keys: {
            A: 'gtceu:electric_blast_furnace',
            B: 'gtceu:lv_alloy_smelter',
            C: 'shanhai:wl_board_lv',
            D: 'shanhai:primordial_engine_core',
            E: 'gtceu:lv_electric_furnace'
        }
    },
    {
        id: 'shanhai:pf_crafting/primordial_matter_recombinator_core',
        out: 'shanhai:primordial_matter_recombinator_core',
        pattern: ['ABC', 'DEF', 'GHI'],
        keys: {
            A: 'gtceu:lv_emitter',
            B: 'gtceu:lv_robot_arm',
            C: 'gtceu:lv_sensor',
            D: 'shanhai:wl_board_ulv',
            E: 'shanhai:primordial_engine_core',
            F: 'thetornproductionline:celestial_secret_deducing_module_ulv',
            G: 'shanhai:first_light',
            H: 'gtceu:maintenance_hatch',
            I: 'shanhai:photon_rainbow'
        }
    },
    {
        id: 'shanhai:pf_crafting/worldline_cracking_hub',
        out: 'shanhai:worldline_cracking_hub',
        pattern: ['ABA', 'CDE', 'FGH'],
        keys: {
            A: 'gtceu:mv_field_generator',
            B: 'shanhai:unknown_particle',
            C: 'shanhai:wl_board_mv',
            D: 'shanhai:primordial_engine_core',
            E: 'thetornproductionline:celestial_secret_deducing_module_mv',
            F: 'shanhai:up_quark',
            G: 'shanhai:basic_material_module',
            H: 'shanhai:down_quark'
        }
    }
]

// -----------------------------------------------------------------------------
// 老 ①⑦⑧（3 条，与上一版【逐字相同】，本次未改动）+ 新增 30 条
// 每条上面第一行注释 = 溯源：PF.txt 里的序号 / 元件 / 纸
// -----------------------------------------------------------------------------
var shanhaiPfGt = [
    // ===== 老 ①（上一版逐字，未改）：处理样板ULV / 电路组装机 / 10s =====
    {
        id: 'shanhai:pf/primordial_omega_engine',
        type: 'circuit_assembler',
        // 老 ① 的 6 项 = circuit_assembler 的物品输入上限 setMaxIOSize(6,1,1,0)，正好用满
        notConsumable: [],
        circuit: 0,
        itemInputs: [
            '4x gtceu:dimensionally_transcendent_steam_oven',
            '4x gtceu:dimensionally_transcendent_dirt_forge',
            '1x shanhai:primordial_engine_core',
            '16x thetornproductionline:celestial_secret_deducing_module_ulv',
            '64x kubejs:precision_steam_mechanism',
            '16x gtceu:primitive_void_ore'
        ],
        inputFluids: ['gtceu:glue 16000'],
        itemOutputs: ['1x shanhai:primordial_omega_engine'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 200,
        EUt: 8
    }
,
    // ===== 老 ⑦（上一版逐字，未改）：处理样板LV / 光子虹吸 / 60s =====
    {
        id: 'shanhai:pf/photon',
        type: 'photon_siphon',
        // PF.txt 原文该格：programmed_circuit + tag:{Configuration:2}
        circuit: 2,
        // 🔴 用户 2026-09-28 原话：「光子虹吸的配方里面主世界碎片和物质模块都是不消耗的（作为催化剂）」
        //     ⇒ 主世界碎片保留不消耗；物质模块于 2026-09-29 改成"等级 >= 1"的配方门槛（见 §7①）
        notConsumable: ['1x gtlcore:world_fragments_overworld'],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        // ⚠️ 降级用：条件类不可用时退回改动前的催化剂形态（宁可比原来差，也不能让配方消失）
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: [],
        inputFluids: [],
        itemOutputs: ['16x shanhai:photon'],
        outputFluids: ['shanhai:zero_point_energy 32000', 'shanhai:light 16000'],
        chancedOutputs: [],
        duration: 1200,
        EUt: 32
    }
,
    // ===== 老 ⑧（上一版逐字，未改）：处理样板LV / 光子虹吸 / 60s =====
    {
        id: 'shanhai:pf/first_light',
        type: 'photon_siphon',
        // PF.txt 原文该格：programmed_circuit + tag:{Configuration:1}
        circuit: 1,
        notConsumable: ['1x gtlcore:world_fragments_overworld'],
        itemInputs: [],
        inputFluids: [],
        itemOutputs: ['32x shanhai:first_light', '4x shanhai:photon'],
        outputFluids: ['shanhai:light 4000'],
        chancedOutputs: [],
        duration: 1200,
        EUt: 32
    }
,
    // ▶ PF.txt 第 1 条（本次新增）｜元件「处理样板-星门(MAX+16)」｜纸：类型「量子化现实重构」／耗时「3s」｜输出 1x shanhai:test_item + 1x shanhai:test_dynamic_text + 1x shanhai:zwf
    // 🔴 纸写「物质模块是催化剂」，但该样板里【没有】任何物质模块物品 ⇒ 催化剂无从挂起，本条按"无催化剂"落。
    // 🔴 元件「处理样板-星门」：用户最新裁决（原话逐字）「溢出那就算了，改成 max+8=max,4^8A」
    //    ⇒ **MAX+8 = MAX 电压 + 4^8 安培**。
    //    独立验算：V[MAX] = 2147483648（= 2^31，jar 字节码真值）；4^8 = 2^16 = 65536
    //    ⇒ EUt = 2^31 × 2^16 = **2^47 = 140737488355328**，只有 Long.MAX（2^63−1）的 1/65536 ⇒ **不溢出**。
    //    ⚠️ 上一版口径（4^16）算出 2^63 = Long.MAX+1 ⇒ 回绕成负数，那正是当时只写 V[MAX] 的原因；本版已解除。
    //    完整算式、字节码取证与作废留档见文件头 §8。
    {
        id: 'shanhai:pf/test_item',
        type: 'spacetime_distortion',
        circuit: 30,
        notConsumable: [],
        itemInputs: ['1x minecraft:cobblestone', '1x shanhai:genesis_reality_modification_module'],
        inputFluids: [],
        itemOutputs: ['1x shanhai:test_item', '1x shanhai:test_dynamic_text', '1x shanhai:zwf'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 140737488355328
    }
,
    // ▶ PF.txt 第 2 条（本次新增）｜元件「处理样板-星门(MAX+16)」｜纸：类型「量子化现实重构」／耗时「3s」｜输出 1x gtceu:creative_chest
    // 🔴 纸写「物质模块是催化剂」，但该样板里【没有】任何物质模块物品 ⇒ 催化剂无从挂起，本条按"无催化剂"落。
    // 🔴 元件「处理样板-星门」：用户最新裁决（原话逐字）「溢出那就算了，改成 max+8=max,4^8A」
    //    ⇒ **MAX+8 = MAX 电压 + 4^8 安培**。
    //    独立验算：V[MAX] = 2147483648（= 2^31，jar 字节码真值）；4^8 = 2^16 = 65536
    //    ⇒ EUt = 2^31 × 2^16 = **2^47 = 140737488355328**，只有 Long.MAX（2^63−1）的 1/65536 ⇒ **不溢出**。
    //    ⚠️ 上一版口径（4^16）算出 2^63 = Long.MAX+1 ⇒ 回绕成负数，那正是当时只写 V[MAX] 的原因；本版已解除。
    //    完整算式、字节码取证与作废留档见文件头 §8。
    {
        id: 'shanhai:pf/creative_chest',
        type: 'spacetime_distortion',
        circuit: 32,
        notConsumable: [],
        itemInputs: ['1x minecraft:cobblestone', '1x shanhai:genesis_reality_modification_module'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:creative_chest'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 140737488355328
    }
,
    // ▶ PF.txt 第 3 条（本次新增）｜元件「处理样板-星门(MAX+16)」｜纸：类型「量子化现实重构」／耗时「3s」｜输出 1x shanhai:primordial_debug_module
    // 🔴 纸写「物质模块是催化剂」，但该样板里【没有】任何物质模块物品 ⇒ 催化剂无从挂起，本条按"无催化剂"落。
    // 🔴 元件「处理样板-星门」：用户最新裁决（原话逐字）「溢出那就算了，改成 max+8=max,4^8A」
    //    ⇒ **MAX+8 = MAX 电压 + 4^8 安培**。
    //    独立验算：V[MAX] = 2147483648（= 2^31，jar 字节码真值）；4^8 = 2^16 = 65536
    //    ⇒ EUt = 2^31 × 2^16 = **2^47 = 140737488355328**，只有 Long.MAX（2^63−1）的 1/65536 ⇒ **不溢出**。
    //    ⚠️ 上一版口径（4^16）算出 2^63 = Long.MAX+1 ⇒ 回绕成负数，那正是当时只写 V[MAX] 的原因；本版已解除。
    //    完整算式、字节码取证与作废留档见文件头 §8。
    {
        id: 'shanhai:pf/primordial_debug_module',
        type: 'spacetime_distortion',
        circuit: 31,
        notConsumable: [],
        itemInputs: ['1x minecraft:cobblestone', '1x shanhai:genesis_reality_modification_module'],
        inputFluids: [],
        itemOutputs: ['1x shanhai:primordial_debug_module'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 140737488355328
    }
,
    // ▶ PF.txt 第 21 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 3x minecraft:fire_charge
    {
        id: 'shanhai:pf/fire_charge',
        type: 'primordial_matter_recombination',
        circuit: 13,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:gunpowder', '1x gtceu:carbon_dust', '1x minecraft:blaze_powder'],
        inputFluids: [],
        itemOutputs: ['3x minecraft:fire_charge'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 22 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 1x gtceu:annealed_copper_ingot
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/annealed_copper_ingot',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:copper_ingot'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:annealed_copper_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 23 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 1x gtceu:conductive_alloy_ingot
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/conductive_alloy_ingot',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x gtceu:pulsating_alloy_ingot', '1x minecraft:redstone'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:conductive_alloy_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 24 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 1x gtceu:compressed_fireclay
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/compressed_fireclay',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:clay_ball', '1x minecraft:brick'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:compressed_fireclay'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 26 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 2x minecraft:paper
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/paper',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:sugar_cane'],
        inputFluids: [],
        itemOutputs: ['2x minecraft:paper'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 27 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 4x gtceu:tin_alloy_ingot
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/tin_alloy_ingot',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:iron_ingot', '1x gtceu:tin_ingot'],
        inputFluids: [],
        itemOutputs: ['4x gtceu:tin_alloy_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 28 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 2x gtceu:red_alloy_ingot
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/red_alloy_ingot',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:copper_ingot', '2x minecraft:redstone'],
        inputFluids: [],
        itemOutputs: ['2x gtceu:red_alloy_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 29 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 4x gtceu:bronze_ingot
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/bronze_ingot',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:copper_ingot', '1x gtceu:tin_ingot'],
        inputFluids: [],
        itemOutputs: ['4x gtceu:bronze_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 30 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 3x gtceu:invar_ingot
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/invar_ingot',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:iron_ingot', '1x gtceu:nickel_ingot'],
        inputFluids: [],
        itemOutputs: ['3x gtceu:invar_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 31 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初奇点反演」／耗时「3s」｜输出 8x thetornproductionline:celestial_secret_deducing_module_ulv
    {
        id: 'shanhai:pf/celestial_secret_deducing_module_ulv',
        type: 'primordial_singularity_inversion',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x kubejs:ulv_universal_circuit', '1x shanhai:electron_neutrino'],
        inputFluids: [],
        itemOutputs: ['8x thetornproductionline:celestial_secret_deducing_module_ulv'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 32 条（本次新增）｜元件「处理样板ULV」｜纸：类型「物质流凝结」／耗时「15s」｜输出 
    {
        id: 'shanhai:pf/matter_flow_condensation_32',
        type: 'matter_flow_condensation',
        circuit: 0,
        notConsumable: [],
        itemInputs: ['1x gtceu:bronze_ingot', '1x gtceu:steel_ingot', '1x gtceu:red_alloy_ingot'],
        inputFluids: ['gtceu:steam 1000', 'gtceu:glue 10'],
        itemOutputs: [],
        outputFluids: ['shanhai:matter_fluid_entry 1000'],
        chancedOutputs: [],
        duration: 300,
        EUt: 8
    }
,
    // ▶ PF.txt 第 33 条（本次新增）｜元件「处理样板ULV」｜纸：类型「物质模块铸造」／耗时「60s」｜输出 1x shanhai:introductory_material_module
    {
        id: 'shanhai:pf/introductory_material_module',
        type: 'matter_module_casting',
        circuit: 0,
        notConsumable: [],
        itemInputs: ['4x shanhai:first_light', '1x shanhai:photon_rainbow', '1x shanhai:electron', '2x shanhai:wl_board_ulv'],
        inputFluids: ['shanhai:matter_fluid_entry 1000'],
        itemOutputs: ['1x shanhai:introductory_material_module'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 1200,
        EUt: 8
    }
,
    // ▶ PF.txt 第 34 条（本次新增）｜元件「处理样板ULV」｜纸：类型「世线电路板组装」／耗时「3s」｜输出 8x shanhai:wl_board_ulv
    {
        id: 'shanhai:pf/wl_board_ulv',
        type: 'wl_board_circuit_assembly',
        circuit: 0,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x kubejs:ulv_universal_circuit', '1x shanhai:photon_rainbow'],
        inputFluids: ['shanhai:matter_fluid_entry 1000'],
        itemOutputs: ['8x shanhai:wl_board_ulv'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 35 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 2x gtceu:steel_ingot
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/steel_ingot',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:iron_ingot', '1x minecraft:coal'],
        inputFluids: [],
        itemOutputs: ['2x gtceu:steel_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 36 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 2x gtceu:cupronickel_ingot
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/cupronickel_ingot',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:copper_ingot', '1x gtceu:nickel_ingot'],
        inputFluids: [],
        itemOutputs: ['2x gtceu:cupronickel_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 37 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 1x gtceu:pulsating_alloy_ingot
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/pulsating_alloy_ingot',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:iron_ingot', '1x minecraft:gunpowder'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:pulsating_alloy_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 38 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 2x gtceu:wrought_iron_ingot
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/wrought_iron_ingot',
        type: 'primitive_blast_furnace',
        circuit: 1,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:iron_ingot'],
        inputFluids: [],
        itemOutputs: ['2x gtceu:wrought_iron_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 39 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 4x gtceu:brass_ingot
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/brass_ingot',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x gtceu:zinc_ingot', '1x minecraft:copper_ingot'],
        inputFluids: [],
        itemOutputs: ['4x gtceu:brass_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 40 条（本次新增）｜元件「处理样板ULV」｜纸：类型「土高炉」／耗时「3s」｜输出 1x gtceu:glass_tube
    // 🔴 🔴 土高炉配方：用户 2026-09-27 裁决「应该是没有电力要求的，就和原版的炼钢一样」
    //    ⇒ EUt 由元件默认的 8 改为 **0**（原版 primitive_blast_furnace 的 JSON 里根本没有 EUt 字段）。
    {
        id: 'shanhai:pf/glass_tube',
        type: 'primitive_blast_furnace',
        circuit: 0,
        notConsumable: ['1x shanhai:introductory_material_module'],
        itemInputs: ['1x minecraft:glass'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:glass_tube'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 0
    }
,
    // ▶ PF.txt 第 42 条（本次新增）｜元件「处理样板LV」｜纸：类型「原初物质重组」／耗时「60s」｜输出 1024x minecraft:obsidian
    {
        id: 'shanhai:pf/obsidian',
        type: 'primordial_matter_recombination',
        circuit: 10,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:basic_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: [],
        inputFluids: ['minecraft:water 2147483647', 'minecraft:lava 1024000'],
        itemOutputs: ['1024x minecraft:obsidian'],
        outputFluids: ['gtceu:steam 2147483647'],
        chancedOutputs: [],
        duration: 1200,
        EUt: 32
    }
,
    // ▶ PF.txt 第 43 条（本次新增）｜元件「处理样板LV」｜纸：类型「世线电路板组装」／耗时「60s」｜输出 1x shanhai:wl_board_lv
    {
        id: 'shanhai:pf/wl_board_lv',
        type: 'wl_board_circuit_assembly',
        circuit: 1,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x kubejs:lv_universal_circuit', '4x shanhai:electron', '4x shanhai:first_light'],
        inputFluids: ['shanhai:zero_point_energy 1000', 'shanhai:matter_fluid_foundation 2000'],
        itemOutputs: ['1x shanhai:wl_board_lv'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 1200,
        EUt: 32
    }
,
    // ▶ PF.txt 第 44 条（本次新增）｜元件「处理样板LV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 1x gtceu:exquisite_emerald_gem
    {
        id: 'shanhai:pf/exquisite_emerald_gem',
        type: 'primordial_matter_recombination',
        circuit: 15,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:basic_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: ['3x minecraft:emerald'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:exquisite_emerald_gem'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 32
    }
,
    // ▶ PF.txt 第 45 条（本次新增）｜元件「处理样板LV」｜纸：类型「光子分离」／耗时「3s」｜输出 8x shanhai:photon_rainbow + 1x shanhai:unknown_particle
    {
        id: 'shanhai:pf/photon_rainbow',
        type: 'photon_separation',
        circuit: 1,
        notConsumable: ['1x gtceu:lv_field_generator'],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['4x shanhai:photon'],
        inputFluids: ['shanhai:light 4000'],
        itemOutputs: ['8x shanhai:photon_rainbow', '1x shanhai:unknown_particle'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 32
    }
,
    // ▶ PF.txt 第 46 条（本次新增）｜元件「处理样板LV」｜纸：类型「原初奇点反演」／耗时「3s」｜输出 1x thetornproductionline:celestial_secret_deducing_module_lv
    {
        id: 'shanhai:pf/celestial_secret_deducing_module_lv',
        type: 'primordial_singularity_inversion',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x kubejs:lv_universal_circuit', '1x shanhai:electron_neutrino', '4x gtceu:double_steel_plate'],
        inputFluids: [],
        itemOutputs: ['1x thetornproductionline:celestial_secret_deducing_module_lv'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 32
    }
,
    // ▶ PF.txt 第 47 条（本次新增）｜元件「处理样板LV」｜纸：类型「光子分离」／耗时「3s」｜输出 2x shanhai:photon + 1x shanhai:photon_rainbow + 1x shanhai:unknown_particle
    {
        id: 'shanhai:pf/photon_2',
        type: 'photon_separation',
        circuit: 1,
        notConsumable: ['1x gtceu:lv_field_generator'],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['16x shanhai:first_light'],
        inputFluids: [],
        itemOutputs: ['2x shanhai:photon', '1x shanhai:photon_rainbow', '1x shanhai:unknown_particle'],
        outputFluids: ['shanhai:light 2000'],
        chancedOutputs: [],
        duration: 60,
        EUt: 32
    }
,
    // ▶ PF.txt 第 48 条（本次新增）｜元件「处理样板LV」｜纸：类型「星际物质吸取」／耗时「30s」｜输出 1x gtlcore:treasures_crystal + 8x gtlcore:mining_crystal
    {
        id: 'shanhai:pf/treasures_crystal',
        type: 'interstellar_matter_absorption',
        circuit: 1,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:basic_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: [],
        inputFluids: [],
        itemOutputs: ['1x gtlcore:treasures_crystal', '8x gtlcore:mining_crystal'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 600,
        EUt: 32
    }
,
    // ▶ PF.txt 第 49 条（本次新增）｜元件「处理样板LV」｜纸：类型「原初物质重组」／耗时「10s」｜输出 
    {
        id: 'shanhai:pf/primordial_matter_recombination_49',
        type: 'primordial_matter_recombination',
        circuit: 12,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:basic_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: ['2x gtceu:carbon_dust'],
        inputFluids: ['gtceu:hydrogen 4000'],
        itemOutputs: [],
        outputFluids: ['gtceu:polyethylene 1000'],
        chancedOutputs: [],
        duration: 200,
        EUt: 32
    }
,
    // ▶ PF.txt 第 50 条（本次新增）｜元件「处理样板LV」｜纸：类型「原初奇点反演」／耗时「3s」｜输出 64x kubejs:ulv_universal_circuit
    {
        id: 'shanhai:pf/ulv_universal_circuit',
        type: 'primordial_singularity_inversion',
        circuit: 32,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x thetornproductionline:celestial_secret_deducing_module_lv', '1x shanhai:wl_board_lv'],
        inputFluids: [],
        itemOutputs: ['64x kubejs:ulv_universal_circuit'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 32
    }
,
    // ▶ PF.txt 第 51 条（本次新增）｜元件「处理样板LV」｜纸：类型「光子分离」／耗时「3s」｜输出 1x shanhai:electron
    // 🔴 纸「电子中微子产出概率5%」⇒ shanhai:electron_neutrino 改成 chancedOutput(500, 100)。⚠️ 单位：本包 chance 是【万分比】，10000=100% ⇒ 5% = 500（不是 5000）。⚠️ 第二个 int 不是"加成上限"，是【每超频一级的加成量 tierChanceBoost】（字节码实证：GTRecipeBuilder.chancedOutput 把 iload_3 写进字段 tierChanceBoost）。100 = GTCEu 自己"5% 档副产"的标准值（本包 414 条实际配方 chance=500/boost=100）。用户 2026-09-26 裁决：吃加成 ⇒ 第二参不能是 0，本文件取 100。
    {
        id: 'shanhai:pf/electron',
        type: 'photon_separation',
        circuit: 1,
        notConsumable: ['1x gtceu:lv_field_generator'],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x shanhai:photon_rainbow'],
        inputFluids: [],
        itemOutputs: ['1x shanhai:electron'],
        outputFluids: ['shanhai:zero_point_energy 1000'],
        chancedOutputs: [{ item: '1x shanhai:electron_neutrino', chance: 500, tierChanceBoost: 100 }],
        duration: 60,
        EUt: 32
    }
,
    // ▶ PF.txt 第 52 条（本次新增）｜元件「处理样板LV」｜纸：类型「原初物质重组」／耗时「10s」｜输出 
    {
        id: 'shanhai:pf/primordial_matter_recombination_52',
        type: 'primordial_matter_recombination',
        circuit: 11,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:basic_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: ['5x gtceu:carbon_dust'],
        inputFluids: ['gtceu:hydrogen 8000'],
        itemOutputs: [],
        outputFluids: ['gtceu:rubber 1000'],
        chancedOutputs: [],
        duration: 200,
        EUt: 32
    }
,
    // ▶ PF.txt 第 53 条（本次新增）｜元件「处理样板LV」｜纸：类型「物质模块铸造」／耗时「60s」｜输出 1x shanhai:basic_material_module
    {
        id: 'shanhai:pf/basic_material_module',
        type: 'matter_module_casting',
        circuit: 0,
        notConsumable: [],
        itemInputs: ['2x shanhai:wl_board_lv', '2x thetornproductionline:celestial_secret_deducing_module_lv', '1x shanhai:introductory_material_module', '16x shanhai:electron', '4x shanhai:photon_rainbow', '128x shanhai:first_light', '1x shanhai:electron_neutrino'],
        inputFluids: ['shanhai:matter_fluid_foundation 2000', 'shanhai:matter_fluid_entry 4000'],
        itemOutputs: ['1x shanhai:basic_material_module'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 1200,
        EUt: 32
    }
,
    // ▶ PF.txt 第 54 条（本次新增）｜元件「处理样板LV」｜纸：类型「物质流凝结」／耗时「15s」｜输出 
    {
        id: 'shanhai:pf/matter_flow_condensation_54',
        type: 'matter_flow_condensation',
        circuit: 0,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x gtceu:conductive_alloy_ingot', '1x gtceu:annealed_copper_ingot', '1x gtceu:cupronickel_ingot'],
        inputFluids: ['gtceu:tin_alloy 100', 'gtceu:ender_pearl 100'],
        itemOutputs: [],
        outputFluids: ['shanhai:matter_fluid_foundation 1000'],
        chancedOutputs: [],
        duration: 300,
        EUt: 32
    }
,
    // ▶ PF.txt 第 56 条（本次新增）｜元件「处理样板MV」｜纸：类型「原初奇点反演」／耗时「3s」｜输出 1x thetornproductionline:celestial_secret_deducing_module_mv
    // 🔴 纸写「物质模块是催化剂」，但该样板里【没有】任何物质模块物品 ⇒ 催化剂无从挂起，本条按"无催化剂"落。
    {
        id: 'shanhai:pf/celestial_secret_deducing_module_mv',
        type: 'primordial_singularity_inversion',
        circuit: 31,
        notConsumable: [],
        itemInputs: ['1x kubejs:mv_universal_circuit', '1x shanhai:material_deduction_module', '2x shanhai:electron_neutrino', '4x gtceu:double_aluminium_plate'],
        inputFluids: [],
        itemOutputs: ['1x thetornproductionline:celestial_secret_deducing_module_mv'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 128
    }
,
    // ▶ PF.txt 第 57 条（本次新增）｜元件「处理样板MV」｜纸：类型「星际物质吸取」／耗时「3s」｜输出 1x shanhai:cosmic_dust
    {
        id: 'shanhai:pf/cosmic_dust',
        type: 'interstellar_matter_absorption',
        circuit: 2,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:basic_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: [],
        inputFluids: [],
        itemOutputs: ['1x shanhai:cosmic_dust'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 128
    }
,
    // ▶ PF.txt 第 58 条（本次新增）｜元件「处理样板MV」｜纸：类型「世线电路板组装」／耗时「60s」｜输出 1x shanhai:wl_board_mv
    {
        id: 'shanhai:pf/wl_board_mv',
        type: 'wl_board_circuit_assembly',
        circuit: 1,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:basic_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: ['1x kubejs:mv_universal_circuit', '4x shanhai:electron', '1x shanhai:up_quark', '1x shanhai:down_quark'],
        inputFluids: ['shanhai:zero_point_energy 4000', 'shanhai:matter_fluid_basic 2000'],
        itemOutputs: ['1x shanhai:wl_board_mv'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 1200,
        EUt: 128
    }
,
    // ▶ PF.txt 第 59 条（本次新增）｜元件「处理样板MV」｜纸：类型「光子分离」／耗时「3s」｜输出 1x shanhai:down_quark + 1x shanhai:casing_empty_quark_emission_catalyst
    {
        id: 'shanhai:pf/down_quark',
        type: 'photon_separation',
        circuit: 2,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:basic_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: ['2x shanhai:photon_rainbow', '1x shanhai:down_quark_emission_catalyst'],
        inputFluids: [],
        itemOutputs: ['1x shanhai:down_quark', '1x shanhai:casing_empty_quark_emission_catalyst'],
        outputFluids: ['shanhai:zero_point_energy 2000'],
        chancedOutputs: [],
        duration: 60,
        EUt: 128
    }
,
    // ▶ PF.txt 第 60 条（本次新增）｜元件「处理样板MV」｜纸：类型「原初奇点反演」／耗时「3s」｜输出 1x shanhai:down_quark_emission_catalyst
    {
        id: 'shanhai:pf/down_quark_emission_catalyst',
        type: 'primordial_singularity_inversion',
        circuit: 8,
        notConsumable: [],
        itemInputs: ['1x shanhai:casing_empty_quark_emission_catalyst', '1x gtceu:mixed_plant', '1x gtceu:separated_plant'],
        inputFluids: [],
        itemOutputs: ['1x shanhai:down_quark_emission_catalyst'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 128
    }
,
    // ▶ PF.txt 第 61 条（本次新增）｜元件「处理样板MV」｜纸：类型「光子分离」／耗时「3s」｜输出 1x shanhai:up_quark + 1x shanhai:casing_empty_quark_emission_catalyst
    {
        id: 'shanhai:pf/up_quark',
        type: 'photon_separation',
        circuit: 2,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:basic_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: ['2x shanhai:photon_rainbow', '1x shanhai:up_quark_emission_catalyst'],
        inputFluids: [],
        itemOutputs: ['1x shanhai:up_quark', '1x shanhai:casing_empty_quark_emission_catalyst'],
        outputFluids: ['shanhai:zero_point_energy 2000'],
        chancedOutputs: [],
        duration: 60,
        EUt: 128
    }
,
    // ▶ PF.txt 第 62 条（本次新增）｜元件「处理样板MV」｜纸：类型「原初奇点反演」／耗时「30s」｜输出 1x shanhai:casing_empty_quark_emission_catalyst
    {
        id: 'shanhai:pf/casing_empty_quark_emission_catalyst',
        type: 'primordial_singularity_inversion',
        circuit: 9,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:basic_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: ['1x gtceu:lv_sodium_battery', '16x shanhai:electron', '4x gtceu:mv_field_generator', '1x thetornproductionline:celestial_secret_deducing_module_hv'],
        inputFluids: ['shanhai:matter_fluid_basic 16000', 'shanhai:zero_point_energy 4000'],
        itemOutputs: ['1x shanhai:casing_empty_quark_emission_catalyst'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 600,
        EUt: 128
    }
,
    // ▶ PF.txt 第 63 条（本次新增）｜元件「处理样板MV」｜纸：类型「物质流凝结」／耗时「15s」｜输出 
    {
        id: 'shanhai:pf/matter_flow_condensation_63',
        type: 'matter_flow_condensation',
        circuit: 0,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:basic_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:basic_material_module',
        itemInputs: ['4x shanhai:cosmic_dust', '1x gtceu:stainless_steel_ingot', '1x gtceu:silicon_ingot'],
        inputFluids: ['gtceu:polyethylene 200', 'gtceu:copper 1000'],
        itemOutputs: [],
        outputFluids: ['shanhai:matter_fluid_basic 1000'],
        chancedOutputs: [],
        duration: 300,
        EUt: 128
    }
,
    // ▶ PF.txt 第 64 条（本次新增）｜元件「处理样板MV」｜纸：类型「原初奇点反演」／耗时「3s」｜输出 1x shanhai:up_quark_emission_catalyst
    {
        id: 'shanhai:pf/up_quark_emission_catalyst',
        type: 'primordial_singularity_inversion',
        circuit: 8,
        notConsumable: [],
        itemInputs: ['1x shanhai:casing_empty_quark_emission_catalyst', '1x gtceu:processing_plant', '1x gtceu:assemble_plant'],
        inputFluids: [],
        itemOutputs: ['1x shanhai:up_quark_emission_catalyst'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 128
    }
,
    // ▶ PF.txt 第 65 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 1x gtceu:annealed_copper_ingot
    {
        id: 'shanhai:pf/annealed_copper_ingot_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:copper_ingot'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:annealed_copper_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 66 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 1x gtceu:conductive_alloy_ingot
    {
        id: 'shanhai:pf/conductive_alloy_ingot_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x gtceu:pulsating_alloy_ingot', '1x minecraft:redstone'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:conductive_alloy_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 67 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 1x gtceu:compressed_fireclay
    {
        id: 'shanhai:pf/compressed_fireclay_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:clay_ball', '1x minecraft:brick'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:compressed_fireclay'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 68 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 2x minecraft:paper
    {
        id: 'shanhai:pf/paper_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:sugar_cane'],
        inputFluids: [],
        itemOutputs: ['2x minecraft:paper'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 69 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 4x gtceu:tin_alloy_ingot
    {
        id: 'shanhai:pf/tin_alloy_ingot_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:iron_ingot', '1x gtceu:tin_ingot'],
        inputFluids: [],
        itemOutputs: ['4x gtceu:tin_alloy_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 70 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 2x gtceu:red_alloy_ingot
    {
        id: 'shanhai:pf/red_alloy_ingot_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:copper_ingot', '2x minecraft:redstone'],
        inputFluids: [],
        itemOutputs: ['2x gtceu:red_alloy_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 71 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 4x gtceu:bronze_ingot
    {
        id: 'shanhai:pf/bronze_ingot_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:copper_ingot', '1x gtceu:tin_ingot'],
        inputFluids: [],
        itemOutputs: ['4x gtceu:bronze_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 72 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 3x gtceu:invar_ingot
    {
        id: 'shanhai:pf/invar_ingot_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:iron_ingot', '1x gtceu:nickel_ingot'],
        inputFluids: [],
        itemOutputs: ['3x gtceu:invar_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 73 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 2x gtceu:steel_ingot
    {
        id: 'shanhai:pf/steel_ingot_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:iron_ingot', '1x minecraft:coal'],
        inputFluids: [],
        itemOutputs: ['2x gtceu:steel_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 74 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 2x gtceu:cupronickel_ingot
    {
        id: 'shanhai:pf/cupronickel_ingot_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:copper_ingot', '1x gtceu:nickel_ingot'],
        inputFluids: [],
        itemOutputs: ['2x gtceu:cupronickel_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 75 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 1x gtceu:pulsating_alloy_ingot
    {
        id: 'shanhai:pf/pulsating_alloy_ingot_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:iron_ingot', '1x minecraft:gunpowder'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:pulsating_alloy_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 76 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 2x gtceu:wrought_iron_ingot
    {
        id: 'shanhai:pf/wrought_iron_ingot_pmr',
        type: 'primordial_matter_recombination',
        circuit: 30,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:iron_ingot'],
        inputFluids: [],
        itemOutputs: ['2x gtceu:wrought_iron_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 77 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 4x gtceu:brass_ingot
    {
        id: 'shanhai:pf/brass_ingot_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x gtceu:zinc_ingot', '1x minecraft:copper_ingot'],
        inputFluids: [],
        itemOutputs: ['4x gtceu:brass_ingot'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
,
    // ▶ PF.txt 第 78 条（本次新增）｜元件「处理样板ULV」｜纸：类型「原初物质重组」／耗时「3s」｜输出 1x gtceu:glass_tube
    {
        id: 'shanhai:pf/glass_tube_pmr',
        type: 'primordial_matter_recombination',
        circuit: 31,
        notConsumable: [],
        moduleLevelRequirement: '1x shanhai:introductory_material_module',
        moduleLevelFallbackCatalyst: '1x shanhai:introductory_material_module',
        itemInputs: ['1x minecraft:glass'],
        inputFluids: [],
        itemOutputs: ['1x gtceu:glass_tube'],
        outputFluids: [],
        chancedOutputs: [],
        duration: 60,
        EUt: 8
    }
]

// -----------------------------------------------------------------------------
// 注册：工作台配方（老 ②③④⑤⑥，5 条）
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// 🔴 运行期探针：把【真实 id】打出来 —— 不再猜
//   教训：上一版用 recipeManager.byKey(id) 反查，报 present=false，而用户 JEI 里【配方还在】，
//   两者不可能同时对 ⇒ 【byKey 查不到 GTCEu 的配方】⇒ 那条判据【不可靠，已废】。
//   本版改成【遍历配方表】getRecipes().toArray()，按 getId() 里的关键词找 ⇒ 自洽、不依赖索引。
//   ⚠️ 刻意【不用 getResultItem()】—— 字节码实证它恒返回 ItemStack.EMPTY。
//   判读：water_lava=[none] ⇒ 那类配方确实不在表里；打出具体 id ⇒ 那就是【真实 id】。
//   同时报 totalRecipes 做 sanity：若为 0 ⇒ 是遍历入口不对，不是配方不在。
// -----------------------------------------------------------------------------
ServerEvents.loaded(function (event) {
    // 🔴 迟到删除：ServerEvents.recipes 期间删不掉（实测：那时 mod 的配方还没进表），
    //    改到 loaded 时直接改配方表。判据 = 紧随其后的探针（同一行日志体系）。
    // 🔴 用【子串】而不是精确 id —— 实测同一个"水+岩浆→黑曜石+蒸汽"存在【两条】老配方：
    //      cxhmz:chemical_reactor/water_lava_to_steam 与 cxhmz:large_chemical_reactor/water_lava_to_steam
    //    （探针实测：删掉第一条后第二条约仍在 ⇒ 这就是用户说"配方还在"的原因）
    var KEY = ['water_lava_to_steam', 'fire_charge_ch']
    // ⚠️ 绝不删自己新写的（shanhai: 前缀）
    var dropped = 0
    var keep = []
    var lerr = ''
    try {
        var all0 = event.server.recipeManager.getRecipes().toArray()
        for (var k0 = 0; k0 < all0.length; k0++) {
            var r0 = '?'
            try { r0 = String(all0[k0].getId()) } catch (e0) { r0 = '?' }
            var hit = false
            for (var k1 = 0; k1 < KEY.length; k1++) if (r0.indexOf(KEY[k1]) >= 0) hit = true
            if (r0.indexOf('shanhai:') === 0) hit = false
            if (hit) { dropped = dropped + 1; continue }
            keep.push(all0[k0])
        }
        if (dropped > 0) event.server.recipeManager.replaceRecipes(keep)
    } catch (e1) { lerr = ' (' + e1 + ')' }
    console.info(SHANHAI_PF_TAG + ' remove-late dropped=' + dropped + ' kept=' + keep.length + lerr)

    var arr = []
    var why = ''
    try { arr = event.server.recipeManager.getRecipes().toArray() } catch (e) { why = ' (' + e + ')' }
    var total = arr.length
    var wl = 'none'
    var fc = 'none'
    var ncx = 0
    var cxList = ''
    for (var i = 0; i < total; i++) {
        var rid = '?'
        try { rid = String(arr[i].getId()) } catch (e2) { rid = '?' }
        if (rid.indexOf('water_lava') >= 0) wl = rid
        if (rid.indexOf('fire_charge') >= 0) { if (fc === 'none') fc = rid; else if (fc.indexOf(rid) < 0) fc = fc + '|' + rid }
        if (rid.indexOf('cxhmz') === 0 || rid.indexOf('cxbp') === 0) { ncx = ncx + 1; if (cxList.length < 200) cxList = cxList + ' ' + rid }
    }
    console.info(SHANHAI_PF_TAG + ' probe totalRecipes=' + total + ' water_lava=[' + wl + '] fire_charge=[' + fc + '] cx-ns-count=' + ncx + why)
    if (ncx > 0) console.info(SHANHAI_PF_TAG + ' probe cx-ids' + cxList)
})


ServerEvents.recipes(function (event) {
    var ok = 0
    // 🔴 2026-09-27 接进聊天栏横幅（scope=shanhai_pf）
    //    ⚠️ 变量名必须【不叫 Stats】—— Rhino 里 `Stats` 会回落到原版 net.minecraft.stats.Stats，
    //       实测报错：Java class "net.minecraft.stats.Stats" has no … "reportSummary"。
    //    ⚠️ 声明必须在【每个 ServerEvents.recipes 回调内部各来一次】—— var 是函数作用域，跨回调不共享。
    var ShanhaiStats = null
    try { ShanhaiStats = Java.loadClass('com.shanhai.common.recipe.ShanhaiRecipeStats') } catch (eS) { ShanhaiStats = null }
    if (ShanhaiStats) ShanhaiStats.reset()   // 本批自己清一次（Java 侧是全局静态累加器）
    var bad = 0
    var errList = ''
    var i

    for (i = 0; i < shanhaiPfShaped.length; i++) {
        var r = shanhaiPfShaped[i]
        try {
            event.shaped(r.out, r.pattern, r.keys).id(r.id)
            ok = ok + 1
            if (ShanhaiStats) ShanhaiStats.addResult(true)
        } catch (e) {
            bad = bad + 1
            if (ShanhaiStats) ShanhaiStats.addResult(false)
            if (errList.length < 1200) { errList = errList + r.id + ' => ' + e + ' | ' }
        }
    }

    console.info(SHANHAI_PF_TAG + ' crafting(shaped) ok=' + ok + ' failed=' + bad
        + ' total=' + shanhaiPfShaped.length)
    if (bad > 0) {
        console.error(SHANHAI_PF_TAG + ' crafting FAILED list: ' + errList)
    }
})

// -----------------------------------------------------------------------------
// 注册：GT 机器配方（老 3 条 + 新增 30 条 = 33 条）
// -----------------------------------------------------------------------------
// -----------------------------------------------------------------------------
// 🔴 移除【被移植替代的老配方】
//   用户 2026-09-26 逐字原话：「我在PF.txt中纸写了，移植的配方是原本产线撕裂或者dgy的配方，
//     由于要结合山海，所以我更新了其中的一些配方，但是老配方还在文件里面，因此需要删除」
//   他写在样板里的纸面原文：「注意，此配方为产线撕裂/dgy移植，添加此配方之后需要移除原本的配方」
//   ① minecraft:obsidian —— gtceu:chemical_reactor：水 2147483647mB + 岩浆 1024000mB ⇒ 蒸汽 + 黑曜石×1024
//   ② minecraft:fire_charge —— gtceu:large_chemical_reactor：火药 + 碳粉 + 烈焰粉 ⇒ 火焰弹×3
//   ⚠️ 用户明确【不删】：gtceu:mixer 产出 fire_charge、以及原版合成台那两条。
//   🔴🔴 2026-09-27 探针实证（遍历 55,947 条配方表得到的真实 id）：
//      真实格式 = <命名空间>:<配方类型路径>/<路径>  —— 不是 <ns>:<路径>！
//        真 id = cxhmz:chemical_reactor/water_lava_to_steam
//        真 id = cxbp:large_chemical_reactor/fire_charge_ch
//      我从导出路径猜的 cxhmz:water_lava_to_steam 【少了一整段类型路径】⇒ 永远不匹配 ✗
//      ⚠️ 正则 { id: /...$/ } 本轮实测【也没生效】⇒ 只能用【精确 id】。
//      ⚠️ 探针同时确认【不该删的两条在表里】：gtceu:mixer/fire_charge 与 minecraft:fire_charge（原版合成台）。
//   🔴 2026-09-27 字节码实证：GTRecipe.getResultItem() 恒返回 ItemStack.EMPTY（javap -c 只有 getstatic ItemStack.f_41583_; areturn），
//      而 KubeJS 的 OutputFilter.test() 只有一句 RecipeKJS.hasOutput(match) ⇒ 【{output:...} 对 GT 配方永远不可能匹配】！
//      ⇒ 所以第一版的 {type,output} 谓词【注定无效】（这也是"删不掉"的机械根因）。
//      ⇒ 正确写法是【按 id 删】：GTRecipe implements 原版 Recipe<Container>（javap 类声明），有 id 字段与 getId()。
//      ⇒ 这里用【正则 id】而不是硬猜命名空间（导出路径是 added_recipes/cxhmz/chemical_reactor/water_lava_to_steam.json，
//        命名空间那一段我无法从路径 100% 反推）。
//      （老配方来自 mod jar（ns=cxhmz/cxbp），不是 KJS 写的 —— 但这不影响 event.remove：
//        它删的是配方表里的条目，不区分来源。真正要防的是删完之后又被后加的脚本加回来。）
//   ✅ 幂等：重复执行时返回值变 0，不报错。
// -----------------------------------------------------------------------------
ServerEvents.recipes(function (event) {
    // 🔴 2026-09-27 收尾：这里原本有 4 条 event.remove —— 【已删除】，因为实测【全部无效】。
    //    ① {type,output} 两条：字节码证明 GTRecipe.getResultItem() 恒返回 ItemStack.EMPTY，
    //       而 KubeJS 的 OutputFilter.test() 只有一句 RecipeKJS.hasOutput(match)
    //       ⇒ 【{output:...} 对 GT 配方永远不可能匹配】。
    //    ② {id} 精确 / {id:/正则/} 两条：时机太早 —— ServerEvents.recipes 期间 mod 的配方
    //       还没进配方表（实测：此刻移除后，18 秒后的探针仍能看到它）。
    //    ✅ 真正生效的删除已挪到本文件末尾的 ServerEvents.loaded 里（直接改配方表）。
    //    ✅ 那处的判据是自洽的：remove-late 与紧随其后的 probe 用同一套遍历。
    console.info(SHANHAI_PF_TAG + ' remove-old 已停用（本块 4 条实测无效，见下方 ServerEvents.loaded）')
})


ServerEvents.recipes(function (event) {
    var gtr = event.recipes.gtceu
    var ok = 0
    // 🔴 同上：本回调内【重新声明】一次（var 不跨回调共享）
    var ShanhaiStats = null
    try { ShanhaiStats = Java.loadClass('com.shanhai.common.recipe.ShanhaiRecipeStats') } catch (eS) { ShanhaiStats = null }
    var bad = 0
    var errList = ''
    var i
    var j

    for (i = 0; i < shanhaiPfGt.length; i++) {
        var r = shanhaiPfGt[i]
        try {
            // ⚠️ 类型 id 直接当方法名用：gtr['photon_siphon'](...) —— 等价于 gtr.photon_siphon(...)
            var b = gtr[r.type](r.id)

            var useLevelGate = shanhaiUseLevelGate(r)

            // 🔴 顺序照宿主脚本：先 .notConsumable(...)，再 .circuit(...)
            //    先例：gtceu.js:3302-3303 / gtceu.js:9955-9957 / gtceu.js:935
            for (j = 0; j < r.notConsumable.length; j++) {
                b = b.notConsumable(r.notConsumable[j])
            }
            // 🔴 物质模块：门槛可用 ⇒ 不写催化剂；否则退回催化剂形态（配方不会消失）
            if (!useLevelGate && r.moduleLevelFallbackCatalyst) {
                b = b.notConsumable(r.moduleLevelFallbackCatalyst)
            }

            // 🔴 编程电路：.circuit(数字)，参数必须是数字（Rhino 下传字符串会报错）
            if (r.circuit > 0) {
                b = b.circuit(r.circuit)
            }

            // 🔴 物质模块等级门槛（= 老山海 module_level 配方条件）
            //    写法照宿主现成先例：gtceu.js:8489 .addCondition(new GravityCondition(true))
            if (useLevelGate) {
                var mlp = shanhaiParseModuleLevel(r.moduleLevelRequirement)
                b = b.addCondition(new ModuleLevelCondition(mlp[0], mlp[1]))
            }

            for (j = 0; j < r.itemInputs.length; j++) {
                b = b.itemInputs(r.itemInputs[j])
            }
            for (j = 0; j < r.inputFluids.length; j++) {
                b = b.inputFluids(r.inputFluids[j])
            }
            for (j = 0; j < r.itemOutputs.length; j++) {
                b = b.itemOutputs(r.itemOutputs[j])
            }
            for (j = 0; j < r.outputFluids.length; j++) {
                b = b.outputFluids(r.outputFluids[j])
            }
//            // 🔴 概率产出（本次新增能力）：#33 的「电子中微子产出概率5%」
            // ⚠️ 第二个 int 是【每超频一级的加成量 tierChanceBoost】，不是"上限"：
            //    字节码实证 GTRecipeBuilder.chancedOutput(ItemStack,int,int)：
            //      67: aload_0 / 68: iload_2 / 69: putfield chance:I
            //      72: aload_0 / 73: iload_3 / 74: putfield tierChanceBoost:I   <-- 第三个参数
            if (r.chancedOutputs) {
                for (j = 0; j < r.chancedOutputs.length; j++) {
                    var co = r.chancedOutputs[j]
                    b = b.chancedOutput(co.item, co.chance, co.tierChanceBoost)
                }
            }

            b.duration(r.duration).EUt(r.EUt)
            ok = ok + 1
            if (ShanhaiStats) ShanhaiStats.addResult(true)
        } catch (e) {
            bad = bad + 1
            if (ShanhaiStats) ShanhaiStats.addResult(false)
            if (errList.length < 1200) {
                errList = errList + r.id + ' [' + r.type + '] => ' + e + ' | '
            }
        }
    }

    console.info(SHANHAI_PF_TAG + ' gt_machine ok=' + ok + ' failed=' + bad
        + ' total=' + shanhaiPfGt.length)
    console.info(SHANHAI_PF_TAG + ' module-mode=' + SHANHAI_PF_MODULE_MODE
        + ' module-level-condition available=' + SHANHAI_HAS_MODULE_LEVEL_CONDITION)
    if (bad > 0) {

        console.error(SHANHAI_PF_TAG + ' gt_machine FAILED list: ' + errList)
    }
    // 🔴 打机器可判的那一行：本批 PF 配方的 total/success/failed
    var hasShanhaiStats = ShanhaiStats
    if (hasShanhaiStats) {
        try { ShanhaiStats.reportSummary('shanhai_pf') } catch (eR) { console.error(SHANHAI_PF_TAG + ' reportSummary FAILED: ' + eR) }
    } else {
        console.error(SHANHAI_PF_TAG + ' ShanhaiRecipeStats 类不可用 ⇒ 本批不上报 ')
    }

    // 逐条回执：证明 builder 收下了什么（方便和 PF.txt 对账）
    for (i = 0; i < shanhaiPfGt.length; i++) {
        var s = shanhaiPfGt[i]
        var gateOn = shanhaiUseLevelGate(s)
        console.info(SHANHAI_PF_TAG + ' spec id=' + s.id
            + ' type=' + s.type
            + ' circuit=' + s.circuit
            + ' in=' + s.itemInputs.length + 'item'
            + ' nc=' + s.notConsumable.length + 'cat'
            + ' slots=' + (s.itemInputs.length + s.notConsumable.length + (s.circuit > 0 ? 1 : 0)
                + (!gateOn && s.moduleLevelFallbackCatalyst ? 1 : 0))
            + ' gate=' + (gateOn ? s.moduleLevelRequirement : '-')
            + ' fin=' + s.inputFluids.length
            + ' out=' + s.itemOutputs.length + 'item'
            + ' fout=' + s.outputFluids.length
            + ' chanced=' + (s.chancedOutputs ? s.chancedOutputs.length : 0)
            + ' EUt=' + s.EUt
            + ' duration=' + s.duration + 't')
    }

    // 🔴 2026-09-26：槽位溢出自检【已移除】—— photon_separation 放宽到 (4, 10, 2, 2) 之后
    //    没有任何配方超出上限（详见文件头 §4）。原先这里会扫 shanhaiPfGt[i].slotOver
    //    并打 [SHANHAI-PF] SLOT-OVER 告警；那个字段与整段告警代码一并删掉了。
    //    ⚠️ 若将来又出现"配方要的槽位 > 该类型 setMaxIOSize"的情形，需要【重新引入】这类检查，
    //       不要以为本文件还带着它。
})

// ═══ 手写区 开始（生成器不会动这一段）═══
// ─── 手写区内容来源（2026-09-27 用户点单第 4 条）─────────────────────────
//   1) 原 shanhai_zero_point_power.js 全文（原始真空零点能发生器的发电配方）—— 已并进本文件手写区
//   2) 以后你直接加在这里的任意 KJS 代码 —— 重跑生成器不会被覆盖

// =============================================================================
// shanhai_zero_point_power.js  --  原始真空零点能发生器的【发电配方】
//
// 用户交办原话（逐字，2026-09-24）：
//   「给原始真空零点能发生器添加一个发电配方，每输入 1000mb 真空零点能
//     可以输出 max 1a×1s 的电量」
//
// 🔴 本文件是【独立新增】，不修改任何既有脚本（尤其没动 shanhai_test_recipes.js）。
// =============================================================================
// §1 「原始真空零点能发生器」到底是哪个 id —— 认定过程（全部是读取，不是猜）
// =============================================================================
//   ① 中文名反查：shanhai-rewrite\src\main\resources\assets\shanhai\lang\zh_cn.json:28
//        "block.shanhai.primordial_void_induction_armature": "原始真空零点能发生器"
//      ⇒ 逐字命中，就是它。
//      ⚠️ 排除项：lv/mv/ulv_zero_point_conversion 的中文名是「LV/MV/ULV零点转换器」
//         （zh_cn.json:201-203），【不是】「原始真空零点能发生器」⇒ 与本次交办无关。
//   ② 它在不在我们注册的 24 台模块里：
//        ModuleRegistry.java:641-648
//          new ModuleSpec("PRIMORDIAL_VOID_INDUCTION_ARMATURE",
//                         "primordial_void_induction_armature", ..., 
//                         () -> RECIPE_VOID_INDUCTION_ARMATURE, false)
//      ⇒ 在。工厂是 StandardPrimordialModule，结构图案与其余 23 台同源。
//   ③ 🔴 它挂的配方类型（决定本脚本用哪个 gtr.<type>）：
//        ModuleRegistry.java:341-344
//          /** 原始真空零点能发生器 —— 上游 :277（全 24 台里最短的一张，只有 1 条）。 */
//          public static final GTRecipeType[] RECIPE_VOID_INDUCTION_ARMATURE = {
//                  ShanhaiRecipeTypes.PRIMORDIAL_POWER_GENERATOR,
//          };
//      ⇒ 唯一一个类型 = primordial_power_generator（原初发电协议）。
//      ⇒ 与本任务书给的候选一致，且【只有一个】，没有任何歧义。
//   ④ 该类型的槽位 / EUIO（ShanhaiRecipeTypes.java:217-225）：
//          GTRecipeTypes.register("primordial_power_generator", "multiblock")
//              .setMaxIOSize(2, 2, 2, 2)     // 物品入2 物品出2 流体入2 流体出2
//              .setEUIO(IO.OUT)              // 🔴 发电型 ⇒ 产出 EU
//      ⇒ 本配方只用 1 个流体输入槽，远在 2 个槽位之内。
// =============================================================================
// §2 输入流体 id —— 真空零点能
// =============================================================================
//   ShanhaiFluids.java:162-164
//     /** 流体 `zero_point_energy`（真空零点能）· ... 桶 `shanhai:zero_point_energy_bucket` */
//     public static final FluidEntry<ForgeFlowingFluid.Flowing> ZERO_POINT_ENERGY =
//             fluid("zero_point_energy", "zero_point_energy", "zero_point_energy_flow", "真空零点能");
//   zh_cn.json:308-310  "fluid.shanhai.zero_point_energy": "真空零点能"（fluid / block / fluid_type 三条）
//   ⇒ 流体的注册 id = **shanhai:zero_point_energy**，中文名逐字 = 真空零点能。
// =============================================================================
// §3 MAX 档电压 = 2147483648 = 2^31 —— 查证过程（不许猜，逐条给证据）
// =============================================================================
//   ① MAX 是第几档：
//        javap -p -v -cp gtceu-1.20.1-1.4.4.jar com.gregtechceu.gtceu.api.GTValues
//          → 按 ConstantValue 属性逐个读出：
//            ULV=0 LV=1 MV=2 HV=3 EV=4 IV=5 LuV=6 ZPM=7 UV=8 UHV=9 UEV=10 UIV=11 UXV=12 OpV=13
//            **MAX=14**   MAX_TRUE=30
//   ② 第 14 档的电压（同一次 javap 的 <clinit> 逐指令）：
//            V 是 long[15]（bipush 15 / newarray long），下标 0..14 依次 lastore：
//              0:8  1:32  2:128  3:512  4:2048  5:8192  6:32768  7:131072  8:524288
//              9:2097152  10:8388608  11:33554432  12:134217728  13:536870912
//              **14: 2147483648l（ldc2_w #270）**   ← = 2^31，逐指令原文
//        ⇒ **GTValues.V[GTValues.MAX] = 2147483648**（1 安培）。这就是「MAX 1A」的电压。
//   ③ 🔴 「输出」⇒ EUt 必须写【负数】（发电型）：
//        GTRecipeBuilder.EUt(long) 的字节码（com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder）：
//            lload_1 / lconst_0 / lcmp / ifle  →  if (eu > 0) inputEU(eu)
//            ...                              →  else if (eu < 0) outputEU(**lneg** eu)
//        🔴 注意那条 **lneg**（long 取负）：-(-2147483648L) = 2147483648L，
//           **在 long 里完全合法、不会溢出**（int 才会溢出）。
//           ⇒ 2147483648 是"1A 输出"能取的**最大值上界**，正好卡在安全线上。
//           宿主先例：gtceu.js:14215-14219 的 plasma_generator 就是 .EUt(-GTValues.V[GTValues.EV])。
//   ④ 「1s」= **20 tick**（GTCEu 的 duration 单位是 tick，20 tick = 1 秒；
//        见宿主 gtceu.js 里随处可见的 .duration(20)）。
//        ⇒ EUt = -2147483648，duration = 20
//          ⇒ 一个配方周期共输出 2147483648 × 20 = 42949672960 EU，
//            即"1000mB 真空零点能 ⇒ MAX 档 1 安培持续 1 秒的电量"。
//   ⚠️ 本脚本【不】直接写 GTValues.V[GTValues.MAX] 取数（虽然宿主能用它），
//      而是在下面 **运行时把字面量与 GTValues 交叉核对一遍**：两边不一致就在日志里响亮报错。
//      理由：本工程的红线是「检查器先证明自己对」——让日志本身证明这个常数没抄错。
// =============================================================================
// §4 KubeJS / Rhino 写法纪律（照 shanhai_test_recipes.js 的保守写法）
// =============================================================================
//   · 全局一律 var；不用 let/const / 模板串 / ?. / 解构 / 箭头函数
//   · 配方写在 ServerEvents.recipes 回调内的 myRecipes 数组里
//   · 只有 1 条配方也照样包 try/catch —— 一条失败不连坐
//   · 不传 circuit（Rhino 下必须是数字，且它占 1 个物品输入槽，本配方用不到）
//   · 描述/剧情文本【不在本文件】—— 本文件只有配方（本工程约定：配方与描述分文件，
//     避免一个语法错把另一个连坐清空）
// ❗ 本文件只在 _smoke-mixin 沙盒里跑；部署由队长另行安排。
// =============================================================================

var myRecipes = [
    {
        id: 'shanhai:zero_point_power_max_1a',
        type: 'primordial_power_generator',
        fluid: 'shanhai:zero_point_energy',
        mb: 1000,
        durationTicks: 20,
        // 负数 = 发电（= -GTValues.V[GTValues.MAX]，运行期会在 loaded 回调里交叉核对）
        euPerTick: -2147483648
    }
]

// -----------------------------------------------------------------------------
// ① 注册配方
// -----------------------------------------------------------------------------
ServerEvents.recipes(function (event) {
    var gtr = event.recipes.gtceu

    var ok = 0
    var bad = 0
    var errList = ''
    var i

    for (i = 0; i < myRecipes.length; i++) {
        var r = myRecipes[i]
        try {
            gtr[r.type](r.id)
                .inputFluids(r.fluid + ' ' + r.mb)
                .duration(r.durationTicks)
                .EUt(r.euPerTick)
            ok = ok + 1
        } catch (e) {
            bad = bad + 1
            if (errList.length < 1000) {
                errList = errList + r.id + ' => ' + e + ' | '
            }
        }
    }

    // 🔴 [SHANHAI-SPEC] 回执 —— 证明【builder 接受了这条配方】。
    //    ⚠️ 这一条只证明"GTCEu recipe builder 没抛异常"，【不】等于"RecipeManager 收下了"；
    //       后者由下面的 loaded 回调（真去配方表里回读）与 KubeJS 的 `Added N recipes` 一起证。
    console.info('[SHANHAI-SPEC] zero_point_power register ok=' + ok + ' failed=' + bad
        + ' total=' + myRecipes.length)
    console.info('[SHANHAI-SPEC] zero_point_power spec'
        + ' machine=shanhai:primordial_void_induction_armature'
        + ' gtr_type=' + myRecipes[0].type
        + ' recipe_id=' + myRecipes[0].id
        + ' input=' + myRecipes[0].fluid + ' ' + myRecipes[0].mb + 'mB'
        + ' EUt=' + myRecipes[0].euPerTick
        + ' duration=' + myRecipes[0].durationTicks + 't')
    if (bad > 0) {
        console.error('[SHANHAI-SPEC] zero_point_power FAILED list: ' + errList)
    }

    // 🔴 交叉核对：我把 2147483648 写成字面量，这里现场问 GTValues 要真值。
    //    GTValues 是 KubeJS 直接可用的全局（宿主 misc.js:79 / gtceu.js:14219 都在用）。
    //    两边不一致 ⇒ 打 error（响亮），不静默。
    try {
        var maxTier = GTValues.MAX
        var vMax = GTValues.V[maxTier]
        var want = -myRecipes[0].euPerTick
        if (vMax == want) {
            console.info('[SHANHAI-SPEC] zero_point_power gtvalues_check MATCH'
                + ' GTValues.MAX=' + maxTier + ' GTValues.V[MAX]=' + vMax
                + ' literal_abs=' + want)
        } else {
            console.error('[SHANHAI-SPEC] zero_point_power gtvalues_check MISMATCH'
                + ' GTValues.MAX=' + maxTier + ' GTValues.V[MAX]=' + vMax
                + ' literal_abs=' + want)
        }
    } catch (e3) {
        console.error('[SHANHAI-SPEC] zero_point_power gtvalues_check THREW: ' + e3)
    }
})

// -----------------------------------------------------------------------------
// ② 配方加载完成后【真去配方表里回读】—— 证明这条配方真的注册进去了
// -----------------------------------------------------------------------------
//  用的是 GTCEu 自己的 GTRecipeType.getProxyRecipes()（Map<RecipeType,List<GTRecipe>>）
//  + GTRecipe 的【public 字段】（duration / tickOutputs）与【public 内部类字段】
//    （Content.content）。
//  🔴 刻意只用【模组类】的 public 字段，一行方法调用都不发（除了集合的 iterator/get/size）——
//     专用服务端跑的是 SRG 名，脚本里一旦写原版/官方方法名（getRecipeManager / toString 之类）
//     就可能直接 NoSuchMethod。public 字段不受重映射影响。
//  🔴 判据不靠"猜 id 字符串"，而是靠【内容】：本类型的配方里，
//     tickOutputs 的 EU == 2147483648 且 duration == 20 的那条，只可能是我们这条。
//  全部包在 try/catch 里：回读失败只打一行 error，绝不影响 ① 的配方注册。
ServerEvents.loaded(function (event) {
    var targetEu = -myRecipes[0].euPerTick          // 2147483648
    var targetDuration = myRecipes[0].durationTicks // 20
    try {
        var Types = Java.loadClass('com.shanhai.common.recipe.ShanhaiRecipeTypes')
        var rt = Types.PRIMORDIAL_POWER_GENERATOR
        var proxies = rt.getProxyRecipes()

        var total = 0
        var hitCount = 0
        var idList = ''
        var hitDetail = ''
        var bucketCount = proxies.size()

        var it = proxies.values().iterator()
        while (it.hasNext()) {
            var list = it.next()
            var k
            for (k = 0; k < list.size(); k++) {
                var rec = list.get(k)
                total = total + 1

                // 配方 id（单独 try：万一 ResourceLocation.toString 在 SRG 下不可调用，
                // 也只丢这一小段，不影响下面的 EU/duration 判据）
                try {
                    idList = idList + '[' + ('' + rec.id) + ']'
                } catch (eId) {
                    idList = idList + '[id_unprintable:' + eId + ']'
                }

                // 读这条配方【每 tick 产出】的 EU（EUio=OUT ⇒ 产出值在 tickOutputs 里）
                var eu = -1
                var outs = rec.tickOutputs
                var oit = outs.entrySet().iterator()
                while (oit.hasNext()) {
                    var en = oit.next()
                    var lst = en.getValue()
                    var j
                    for (j = 0; j < lst.size(); j++) {
                        var v = 1 * lst.get(j).content
                        if (v > eu) {
                            eu = v
                        }
                    }
                }

                if (eu == targetEu) {
                    hitCount = hitCount + 1
                    hitDetail = hitDetail + ' {duration=' + rec.duration
                        + ' tickOutputEU=' + eu
                        + ' duration_ok=' + (rec.duration == targetDuration) + '}'
                }
            }
        }

        console.info('[SHANHAI-SPEC] zero_point_power readback total_in_type=' + total
            + ' buckets=' + bucketCount
            + ' eu_match_count=' + hitCount
            + ' target_eu=' + targetEu
            + ' target_duration=' + targetDuration
            + hitDetail)
        console.info('[SHANHAI-SPEC] zero_point_power readback ids_in_type=' + idList)
    } catch (e) {
        console.error('[SHANHAI-SPEC] zero_point_power readback THREW: ' + e)
    }
})
// ═══ 手写区 结束 ═══
