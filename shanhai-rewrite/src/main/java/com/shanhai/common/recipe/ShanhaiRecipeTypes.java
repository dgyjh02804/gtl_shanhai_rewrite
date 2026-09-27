package com.shanhai.common.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.lowdragmc.lowdraglib.gui.texture.ProgressTexture;
import com.shanhai.ShanhaiMod;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本 mod【唯一】的配方类型注册点。
 *
 * <h2>这个类注册什么</h2>
 * 照旧私货 {@code DShanhaiRecipeTypes.java}（492 行）<b>逐条</b>抄回它自注册的全部类型：
 * <ul>
 *   <li><b>40 个「真类型」</b>（其中 3 条是 2026-09-22 先做的）；</li>
 *   <li><b>36 个「显示类型」</b> {@code gtceu:nine_industrial_mode_0} … {@code _35}
 *       —— 原版源码注释原文：「36 水浒传模式显示类型 — 仅用于 Jade 机器模式展示，翻译名在 zh_cn.json」。</li>
 * </ul>
 * 合计 <b>76 条</b>。
 *
 * <h2>🔴🔴 订正（2026-09-26，用户点单"40 条"）—— <b>这条是最新的，与下面 2026-09-23 那条相反，以本条为准</b></h2>
 * 用户 2026-09-26 交办：为了让「原初山海调试模块」在 JEI 里展示<b>全部山海自有配方类型</b>，
 * 点单 **"40 条真类型全部挂上"** ⇒ <b>下一条（2026-09-23 裁剪到 16 条）已被本条覆盖</b>：
 * <ul>
 *   <li>那 24 条真类型<b>已按 ⛔作废块原文逐字恢复注册</b>（声明区 + {@code init()} 末尾 24 段链）；</li>
 *   <li>{@link #REAL_TYPE_COUNT} <b>16 → 40</b>，{@link #countMissingReal()} 的数组同步补 24 项；</li>
 *   <li><b>36 条 GTNH 显示类型仍然不恢复</b>（用户 2026-09-22 原话「那个 GTNH 是我重制版不会添加的」）；</li>
 *   <li>下一条 2026-09-23 的<b>旧句原样保留</b>（本工程惯例），但<b>不再是当前口径</b>。</li>
 * </ul>
 *
 * <h2>🔴 订正（2026-09-23，用户裁决裁剪后）——上面那两句是【旧句，原样保留】</h2>
 * <p>⚠️ <b>本段已被上一段（2026-09-26）覆盖，仅作历史留档。</b>
 * 用户 2026-09-23 裁决：<b>只保留被 {@code ModuleRegistry} 那 24 台模块实际引用的类型</b>，
 * 其余全删。于是：
 * <ul>
 *   <li><b>36 个显示类型整组删除</b>（{@code nine_industrial_mode_0..35}）——
 *       理由：它们来自 <b>GTNH（{@code GTnotleisure}）</b>，用户 2026-09-22 明说
 *       「那个 GTNH 是我重制版不会添加的」；⇒ {@code NINE_INDUSTRIAL_MODES} 数组、
 *       它的 for 循环、{@code DISPLAY_TYPE_COUNT}、{@code countMissingDisplay()} <b>一并删除</b>，
 *       <b>不留"恒为 0 的空壳"</b>（幽灵概念会让后人以为"这东西本来就该有"）。</li>
 *   <li><b>24 个真类型删除</b>（40 − 16），因为它们<b>没有被任何模块引用</b>
 *       （取证：全 {@code src} 100 个文件扫描，除本文件自己的声明/fail-fast/日志外只有
 *       {@code ShanhaiRegistry} 的报错串提到过其中几个，{@code ModuleRegistry} 一次都没出现）。</li>
 * </ul>
 * ⇒ <b>最终只注册 16 个真类型</b>；{@code REAL_TYPE_COUNT = 16}；
 * 日志行相应变成「真类型 16 / 16」（<b>不再有"显示类型"那一段</b>）。
 * <p>⚠️ 被删的 24 个真类型的<b>原文（字段声明 + register 链）逐字保留在文件末尾的作废块里</b>，
 * 将来做新机器时要重新加回来 —— 见下面 &lt;h2&gt;作废块&lt;/h2&gt;。此前本工程<b>一条自定义类型都没有</b>（取证：全 {@code src} 扫
 * {@code GTRecipeTypes.register|RecipeTypes.register|new GTRecipeType} = <b>0 命中</b>；
 * 108 处 {@code RecipeType} 全是引用 GTCEu 原版类型或注释）。
 *
 * <h2>🔴 为什么必须挂 {@code GTRecipeType} 的泛型监听器，而不能直接调 {@code init()}</h2>
 * {@code GTRecipeTypes.register(name, category, proxyRecipes...)} 的字节码（{@code javap -p -c}）：
 * <pre>
 *    0: new           #29   // class com/gregtechceu/gtceu/api/recipe/GTRecipeType
 *    5: invokestatic  #137  // GTCEu.id:(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;
 *   10: invokespecial #140  // GTRecipeType."&lt;init&gt;":(ResourceLocation;String;[RecipeType;)V
 *   14: getstatic     #146  // 🔴 BuiltInRegistries.f_256990_   （RecipeType 注册表）
 *   22: invokestatic  #155  // GTRegistries.register:(Registry;ResourceLocation;Object;)Object
 *   26: getstatic     #158  // 🔴 BuiltInRegistries.f_256769_   （RecipeSerializer 注册表）
 *   40: invokestatic  #155  // GTRegistries.register:(…)
 *   44: getstatic     #165  // 🔴 GTRegistries.RECIPE_TYPES
 *   52: invokevirtual #168  // GTRegistry$RL.register:(Object;Object;)Object
 * </pre>
 * ⇒ 它<b>写三个注册表</b>，全是「冻结就抛」的那种。所以只能在 GTCEu 自己开的那道窗口里调。
 *
 * <h2>窗口在哪：GTCEu 主动 post 了 {@code RegisterEvent&lt;…, GTRecipeType&gt;}（与机器同款机制）</h2>
 * {@code com.gregtechceu.gtceu.common.data.GTRecipeTypes#init()} 的字节码（同一次 {@code javap -p -c}）：
 * <pre>
 *  107: invokestatic  #294  // ModLoader.get:()Lnet/minecraftforge/fml/ModLoader;
 *  110: new           #17   // class com/gregtechceu/gtceu/api/GTCEuAPI$RegisterEvent
 *  114: getstatic     #165  // GTRegistries.RECIPE_TYPES
 *  117: ldc           #29   // 🔴 class com/gregtechceu/gtceu/api/recipe/GTRecipeType   ← 泛型实参
 *  119: invokespecial #297  // GTCEuAPI$RegisterEvent."&lt;init&gt;":(GTRegistry;Class;)V
 *  122: invokevirtual #301  // ModLoader.postEvent:(Event;)V     ← 🔴 这就是给 addon 的窗口
 *  125: getstatic     #165  // GTRegistries.RECIPE_TYPES
 *  128: invokevirtual #304  // GTRegistry$RL.freeze:()V          ← 🔴 窗口在 freeze 之前
 * </pre>
 * ⇒ <b>{@code addGenericListener(GTRecipeType.class, …)} 必被命中</b>（filter 是
 * {@code getGenericType() == GTRecipeType.class} 身份比较，GTCEu 传的就是这个 class 字面量），
 * 且<b>此刻 {@code RECIPE_TYPES} 还没 freeze</b>（freeze 在第 128 条，postEvent 之后）。
 * <p>⚠️ 我第一遍读这段字节码时把方法截断了，看到尾部一堆序列化器注册就以为「没有 postEvent」——
 * 实际上 <b>{@code postEvent} 在偏移 122，后面还有 90 条指令</b>。
 * 按本项目「检查器自己必须先被证明是对的」那条记在这里：<b>截断窗口会造出假否定。</b>
 *
 * <h2>🔴 只注册、不挂机器</h2>
 * 本类只负责「把类型定义出来」。旧私货 48 台机器里只挂了其中一部分（另有 3 条全源码树零引用：
 *  {@code matter_aggregation} / {@code worldline_cutting} / {@code high_dimensional_fragment_cutting}）。
 * <b>谁挂什么在 {@code ModuleRegistry} / {@code ShanhaiMachines}，不在本文件。</b>
 *
 * <h2>命名（用户 2026-09-22 亲定，不许改）</h2>
 * <ul>
 *   <li>{@code gtceu:matter_aggregation} —— <b>原初物质凝集</b></li>
 *   <li>{@code gtceu:worldline_cutting} —— <b>原初世线切割</b></li>
 *   <li>{@code gtceu:high_dimensional_fragment_cutting} —— <b>高维碎片裁切</b></li>
 * </ul>
 * 其余类型的中文名一律取<b>原版 lang 原文</b>（不是我们翻译的），写在
 * {@code assets/shanhai/lang/zh_cn.json}，键 = <b>{@code gtceu.<id>}</b>。
 * 键格式不是推的，是<b>GTCEu 自己的语言文件里的真值</b>：解出
 * {@code libs/gtceu-1.20.1-1.4.4.jar!assets/gtceu/lang/zh_cn.json} 后逐字命中
 * {@code "gtceu.macerator":"研磨机"} / {@code "gtceu.assembler":"组装机"} /
 * {@code "gtceu.electric_blast_furnace":"电力高炉"}；而 {@code "gtceu.recipe_type.macerator"} <b>不存在</b>。
 *
 * <h2>🔴 那 3 条"搬运键"（队长 2026-09-22 裁决）——<b>不是新译名，别误会</b></h2>
 * {@code worldline_probability_cracking} / {@code worldline_matter_recurrence} / {@code worldline_sampling}
 * 在原版 lang 里<b>只有</b> {@code gtceu.recipe_type.<id>}（没有 {@code gtceu.<id>}）。而 GTCEu 的
 * JEI 分类标题只读 {@code gtceu.<id>}（{@code GTRecipeTypeCategory#getTitle()} =
 * {@code ResourceLocation.toLanguageKey()}）⇒ 这 3 条在 JEI 里会显示裸键。
 * ⇒ 我们<b>把 {@code gtceu.recipe_type.<id>} 那条的值原样搬运</b>到 {@code gtceu.<id>}（**同值，不是新译名**），
 * 且<b>原版那条 {@code gtceu.recipe_type.<id>} 保留不删</b>（留档/兼容）。
 * <p>🔴 值分别是：<b>概率裂解</b> / <b>物质复现</b> / <b>世线采样</b> —— 三个都来自原版 lang 原文。
 * <p>另：36 条显示类型 {@code nine_industrial_mode_0..35} 原版<b>同样只有</b>
 * {@code gtceu.recipe_type.<id>}（{@code gtceu.<id>} 实测 36/36 全空），故照实际有的键写；
 * 这 2 条无中文名（lang 未收录）<b>我们没有加、也没有自译</b>：
 * {@code wl_board_circuit_assembly} / {@code wl_board_wafer_etching}。
 * <p>🔴 <b>2026-09-26 订正（上面这句旧话 <u>与事实不符</u>，旧句原样保留在上面）</b>：
 * 实测<b>我们工程的 {@code zh_cn.json} 里这两个键 <u>都有</u></b> ——
 * {@code "gtceu.wl_board_circuit_assembly":"世线板电路组装"}、
 * {@code "gtceu.wl_board_wafer_etching":"世线晶圆蚀刻"}（见该文件 61-62 行附近）。
 * 而<b>上游旧私货的 lang 里这两个键都没有</b> ⇒ 即这两个中文名是<b>本工程自译的</b>，
 * 不是"原版 lang 原文"。⇒ 两个后果，都<b>不</b>由本文件擅自处理：
 * <ol>
 *   <li>它们<b>在 JEI 里不会显示裸键</b>（有名字）；</li>
 *   <li>但"本文件所有中文名一律取原版 lang 原文、不自译"这条口径</li>在此 2 条上<b>不成立</b> ——
 *       要不要保留这两个自译名、要不要改这条口径，<b>需用户裁决</b>（本工程规矩：改口径要先拿到裁决）。</li>
 * </ol>
 *
 * <h2>🔴 与原版的【唯一偏离】——已由用户裁决「接受」，⛔ 不要"顺手补上"</h2>
 * <p>🔴 <b>唯一偏离 = 不抄 gtladditions 的 {@code GTLAddSoundEntries}（用户裁决）。</b>
 * 即：原版 37 条链尾的 {@code .setSound(GTLAddSoundEntries.INSTANCE.getFORGE_OF_THE_ANTICHRIST())}
 * 本类<b>不写</b>；而 3 条 {@code .setSound(GTSoundEntries.ARC)}
 * （{@code primordial_myriad_ascension_tier_1} / {@code _tier_2} / {@code primordial_stellar_reaction}）
 * <b>已于 2026-09-22 按队长裁决恢复抄写</b>（见实现里那三处 `setSound(GTSoundEntries.ARC)`）。
 * <p>不抄 gtladditions 那个，理由两条（都<b>只适用于 gtladditions 的 {@code GTLAddSoundEntries}</b>）：
 * <ol>
 *   <li><b>它是 gtladditions 的【非 API】符号</b>：{@code javap} 出来的 FQN 是
 *       {@code com.gtladd.gtladditions.common.modify.GTLAddSoundEntries}（{@code common.modify}，
 *       不是 {@code api}）—— 而本工程有一条明写的隔离墙：
 *       「{@code common} 侧唯一引用 gtladditions 非 API 的类是 {@code GtlAddCompat}」。</li>
 *   <li><b>它是一次 Registrate 注册，不是纯读字段</b>：{@code GTLAddSoundEntries#register(String,int)} 字节码
 *       {@code 0: GTLAddRegistration.Companion.getREGISTRATE() → 13: GTLAddRegistration.sound(ResourceLocation)
 *       → 17: SoundEntryBuilder.attenuationDistance(int) → 20: SoundEntryBuilder.build():SoundEntry}
 *       ⇒ 取这个字段会<b>触发 {@code <clinit>} 并当场 build 一个声音条目</b>。
 *       它写的是哪张注册表、那一刻是否已冻结，<b>没有任何证据</b> ——
 *       而本工程已经因「在错误的时机碰冻结注册表」炸过一次（见 {@code ShanhaiRegistry} 类注释）。</li>
 * </ol>
 * <p>⚠️ <b>上一条理由【不适用于】{@code GTSoundEntries.ARC}</b>：它是 GTCEu 自己的类（不是第三方非 API），
 * GTCEu 自家机器到处在用它 ⇒ 不存在"碰冻结注册表"的问题。所以这 3 条恢复抄写（队长 2026-09-22 裁决）。
 * <p>⚠️ 还有一处<b>已作废的旧口径</b>（留档，别重蹈）：本类 2026-09-22 一度把"省略 setSound"
 * 扩张到 {@code GTSoundEntries.ARC}，理由是"它同样会触发静态初始化 = 时机未验证"。
 * 队长裁定那是<b>过度保守</b>（理由只对 gtladditions 成立），已回退。
 * <p>⚠️ <b>除这一处外，其余全部链式调用、{@code category} 与 {@code setMaxIOSize} 实参逐字照抄原版。</b>
 *
 * <h2>🔴 裁决留档（2026-09-22，用户拍板；<b>照抄这句话，别把它改写成"我们的选择"</b>）</h2>
 * <pre>
 * ⚠️ 已知偏离（用户 2026-09-22 裁决：接受）：未抄 .setSound(GTLAddSoundEntries…)。理由两条（见上）。
 *    若将来要补：那 37 条链尾各加一行 + 让 GtlAddCompat 出薄封装。
 *    （3 条 GTSoundEntries.ARC 不算偏离 —— 已按裁决恢复抄写。）
 * ⚠️ en_us.json（用户 2026-09-22 裁决：不补）：英文环境下这些类型会显示裸键 gtceu.&lt;id&gt;。
 * </pre>
 * <b>写在这里的目的（原话）</b>：<b>防止将来有人看到"和原版不一致"就顺手补上</b> ——
 * 那正是<b>会崩的那一处</b>（理由第 2 条：取那个字段会触发一次时机未验证的 Registrate 注册）。
 * <p>🔴 <b>两条都不是"本工程的取舍"，是用户裁决；要改回去必须先拿到新的用户裁决，不许"顺手"。</b>
 */
public final class ShanhaiRecipeTypes {

    // ═══════════════════ 40 个真类型（字段顺序照原版 DShanhaiRecipeTypes.java:15-54）═══════════════════

    /** 原初发电协议 —— 原始真空零点能发生器。 */
    public static GTRecipeType PRIMORDIAL_POWER_GENERATOR;
    /** 原初恒星反应 —— 原初宇宙反应炉。 */
    public static GTRecipeType PRIMORDIAL_STELLAR_REACTION;
    /** 原初生物演化协议 —— 原初生物核心。 */
    public static GTRecipeType PRIMORDIAL_BIOLOGICAL_CORE;
    /** 原初物质重组 —— 原初物质重组核心。 */
    public static GTRecipeType PRIMORDIAL_MATTER_RECOMBINATION;
    /** 原初因果编织 —— 原初因果编织矩阵。 */
    public static GTRecipeType PRIMORDIAL_CAUSAL_WEAVING;
    /** 原初奇点反演 —— 原初奇点反演核心。 */
    public static GTRecipeType PRIMORDIAL_SINGULARITY_INVERSION;
    /** 太虚熔炼 —— 原初太虚宇宙锻炉。 */
    public static GTRecipeType TAIXU_SMELTING;
    /** 世线震荡收集 —— 原初分歧发生器。 */
    public static GTRecipeType WORLDLINE_OSCILLATION_COLLECTION;
    /** 星际物质吸取 —— 原初分歧发生器。 */
    public static GTRecipeType INTERSTELLAR_MATTER_ABSORPTION;
    /** 物质流凝结 —— 原初物质铸造机。 */
    public static GTRecipeType MATTER_FLOW_CONDENSATION;
    /** 原初能量吸取 —— 原初分歧发生器。 */
    public static GTRecipeType PRIMORDIAL_ENERGY_ABSORPTION;
    /** 光子分离 —— 原初物质铸造机。 */
    public static GTRecipeType PHOTON_SEPARATION;
    /** 物质模块铸造（{@code setHasResearchSlot(true)}；4 条带专属 .rtui 的类型之一）—— 原初物质铸造机。
     *  <p>🔴 2026-09-25 用户裁决：{@code setMaxIOSize} = <b>(17, 1, 4, 0)</b>（原 (15,6,6,6)），
     *  四个数 = 其专属模板 {@code assets/gtceu/ui/recipe_type/matter_module_casting.rtui}
     *  真正画出的槽位数，也等于现存 25 条该类型配方的实测最大值。详见 {@link #init()} 里那一大段注释。 */
    public static GTRecipeType MATTER_MODULE_CASTING;
    /** 物质锻造 —— 原初物质铸造机。 */
    public static GTRecipeType MATTER_FORGING;
    /** 原初物质解构 —— 原初混沌蜉蝣解构结晶炉（{@code primordial_chaotic_ephemeral_deconstruction_crystallization_furnace}）。
     *  <p>🔴 2026-09-26 用户点单（原话逐字）：「给原初混沌蜉蝣解构结晶炉添加一个新的配方种类，
     *  名字叫原初物质解构，1流体输入，1物品输入，同时，需要预留10流体输出和20物品输出」
     *  ⇒ {@code setMaxIOSize(1, 103, 1, 16)}（物品入 1／物品出 20／流体入 1／流体出 16）。
     *  ⚠️ 流体出 10 ⇒ **16**：2026-09-26 用户裁决「放宽上限到 16」（原话），
     *     起因是星门水晶浆液那条产线要吐 16 种流体。
     *  <p>四个数 = 专属模板 {@code assets/gtceu/ui/recipe_type/primordial_matter_deconstruction.rtui}
     *  （**190×114**；由 {@code black_hole_event_horizon_blast.rtui} 裁剪改写而来）真正画出的槽位数
     *  —— 见 {@link #init()} 里那段注释的取证。 */
    public static GTRecipeType PRIMORDIAL_MATTER_DECONSTRUCTION;
    /** 无中文名（lang 未收录，带专属 .rtui）—— 原初装配线模块 ＋ 永恒格雷工坊额外模块。
     *  <p>🔴 2026-09-26 订正：旧句"无中文名（lang 未收录）"<b>与事实不符</b>（原文保留于上行）——
     *  我们 {@code zh_cn.json} 里<b>有</b> {@code gtceu.wl_board_circuit_assembly = 世线板电路组装}；
     *  只是上游旧私货 lang 没有该键 ⇒ 它是<b>本工程自译</b>。详见类注释同名订正块。 */
    public static GTRecipeType WL_BOARD_CIRCUIT_ASSEMBLY;
    /** 无中文名（lang 未收录，带专属 .rtui）—— 原初装配线模块 ＋ 原初世线蚀刻核心。
     *  <p>🔴 2026-09-26 订正：同上 —— 我们 {@code zh_cn.json} 里有
     *  {@code gtceu.wl_board_wafer_etching = 世线晶圆蚀刻}，是<b>本工程自译</b>，不是"无中文名"。 */
    public static GTRecipeType WL_BOARD_WAFER_ETCHING;

    // ═══════════════════════════════════════════════════════════════════════════════════════════════
    // 🔴 2026-09-26 恢复：下面 24 条真类型（**用户点单："40 条"**）
    //
    // 由来：2026-09-23 用户裁决「裁剪配方类型注册」，把没被 24 台模块引用的 24 条真类型与
    // 36 条显示类型一起删掉（原文逐字留在本文件末尾的 ⛔作废块里，见 &lt;h2&gt;作废块&lt;/h2&gt;）。
    // 2026-09-26 用户点单 **"40 条真类型全部挂上"**（因为「原初山海调试模块」要在 JEI 里
    // 展示**全部山海自有配方类型**）⇒ 本块把这 24 条**按作废块原文逐字恢复**。
    //
    // 恢复口径（三条，全部照旧、不得改）：
    //   ① 链式调用 / category / setMaxIOSize 实参**逐字照抄**作废块原文；
    //   ② 24 段里凡原版链尾有 {@code .setSound(...)} 的，**照作废块原文**处理 ——
    //      即：3 条 {@code GTSoundEntries.ARC}（tier_1 / tier_2）保留（作废块原文里就带着），
    //      而 gtladditions 的 {@code GTLAddSoundEntries} 那 37 处**仍然不抄**（用户 2026-09-22 裁决）；
    //   ③ **36 条显示类型不恢复**（来自 GTNH，用户 2026-09-22 原话「那个 GTNH 是我重制版不会添加的」）。
    //
    // ⚠️ 本块 24 条目前**没有任何机器挂它们**（实测：ModuleRegistry 里对这 24 个字段的引用数 = 0）
    //   ⇒ 它们唯一的运行/展示载体是 {@code shanhai:primordial_debug_module}（第 25 台模块）。
    //
    // ⚠️ 注册顺序说明（如实交代）：24 段链**集中放在 init() 末尾**（fail-fast 之前），
    //   不再插回原版行号对应的"原位置"。理由：每条 register 互相独立，顺序不影响语义；
    //   集中放可把 24 处散点插入压成一处，降低"整段替换丢定义"那类事故的概率。
    //   每段仍保留 {@code // :NNN-NNN} 的**原版行号标记**，便于逐行回去对账。
    // ═══════════════════════════════════════════════════════════════════════════════════════════════

    /** 代理执行占位类型（{@code setMaxTooltips(1)}，无 slotOverlay）—— 代理执行机器。 */
    public static GTRecipeType PROXY_EXECUTION;

    /** 原初铸币工厂 —— 原版有该机器，<b>本工程已删除该机器</b>；类型照原版保留注册。 */
    public static GTRecipeType COIN_FORGE;

    /** 大明科技聚合类型 —— 大明工业机器。 */
    public static GTRecipeType NINE_INDUSTRIAL;

    /** 事件视界爆破（4 条带专属 .rtui 的类型之一）—— 黑洞收容机器。 */
    public static GTRecipeType BLACK_HOLE_EVENT_HORIZON_BLAST;

    /** 黑洞中子态素压缩 —— 黑洞收容机器。 */
    public static GTRecipeType BLACK_HOLE_NEUTRONIUM_COMPRESSOR;

    /** 黑洞引力压缩 —— 黑洞收容机器。 */
    public static GTRecipeType BLACK_HOLE_COMPRESSOR;

    /** 高维碎片裁切（用户拟名）—— <b>原版零挂载</b>。 */
    public static GTRecipeType HIGH_DIMENSIONAL_FRAGMENT_CUTTING;

    /** 原初世线切割（用户拟名）—— <b>原版零挂载</b>。 */
    public static GTRecipeType WORLDLINE_CUTTING;

    /** 世线采样 —— 世线裂解枢纽。 */
    public static GTRecipeType WORLDLINE_SAMPLING;

    /** 世线物质重现 —— 世线裂解枢纽。 */
    public static GTRecipeType WORLDLINE_MATTER_RECURRENCE;

    /** 概率裂解 —— 世线裂解枢纽。 */
    public static GTRecipeType WORLDLINE_PROBABILITY_CRACKING;

    /** 光子虹吸（{@code "single"}，5 条 slotOverlay）—— 世线裂解枢纽 ＋ 零点光子转换器。 */
    public static GTRecipeType PHOTON_SIPHON;

    /** 零点转换（{@code "single"}）—— 世线裂解枢纽 ＋ 零点光子转换器。 */
    public static GTRecipeType ZERO_POINT_CONVERSION;

    /** 原初物质凝集（用户拟名；原版 lang 无此键）—— <b>原版零挂载</b>。 */
    public static GTRecipeType MATTER_AGGREGATION;

    /** 引力波广域广播 —— 引力波天线发射器。 */
    public static GTRecipeType GRAVITATIONAL_WAVE_CONSUMPTION;

    /** 宇宙修改·天界领航 —— 天界领航塔（⚠️此处原注释写"注册在它自己类里"，**实测不准确**：
     *  它在 {@code DShanhaiRecipeTypes.java:205} 注册；旧句原样保留，2026-09-26 订正）。 */
    public static GTRecipeType TIANJIE_NAVIGATION;

    /** 多维星穹零点聚合 —— 天界星云零点虹吸枢纽。 */
    public static GTRecipeType NEBULA_SIPHONING;

    /** 混沌合成（lang 原文值含 §k 混淆格式码）—— 引力波天线发射器 ＋ 创世之眼模块。 */
    public static GTRecipeType CHAOS_CRAFTING;

    /** 七十二变 —— 引力波天线发射器。 */
    public static GTRecipeType SEVENTY_TWO_CHANGES;

    /** 引力波宏观干涉 —— 引力波天线发射器。 */
    public static GTRecipeType GRAVITATIONAL_WAVE_PRODUCTION;

    /** 一级原初万象晋升 —— 原初万象衍生核心（我们未做该模块）。 */
    public static GTRecipeType PRIMORDIAL_MYRIAD_ASCENSION_TIER_1;

    /** 二级原初万象晋升 —— 原初万象衍生核心（我们未做该模块）。 */
    public static GTRecipeType PRIMORDIAL_MYRIAD_ASCENSION_TIER_2;

    /** 苦命鸳鸯 —— 终焉创始现实修改矩阵。 */
    public static GTRecipeType KU_MING_YUAN_YANG;

    /** 量子化现实重构 —— 终焉创始现实修改矩阵。 */
    public static GTRecipeType SPACETIME_DISTORTION;


    /**
     * 真类型条数（不含 36 条显示类型）。fail-fast 用。
     *
     * <p>🔴 2026-09-26：<b>16 → 40</b>（用户点单"40 条"）；<b>同日再 40 → 41</b>
     * （用户点单新增「原初物质解构」{@code primordial_matter_deconstruction}）。这个数字同时被
     * {@link #countMissingReal()} 与 {@code ShanhaiRegistry#verifyRecipeTypesRegistered} 使用，
     * <b>改类型数量必须同步改这里与那个数组</b>，否则 fail-fast 只查一部分、其余静默缺失。
     */
    public static final int REAL_TYPE_COUNT = 41;

    /**
     * 幂等闸门。与 {@code ShanhaiMachines.INITIALIZED} / {@code ModuleRegistry} 同款写法。
     *
     * <p>🔴 这里<b>不是</b>「可有可无的防御」：{@code GTRecipeTypes.register} 写的是
     * {@code ForgeRegistries.RECIPE_TYPES} 那类注册表，<b>同一个 id 注册两次会抛（键重复）</b>。
     * 而 GTCEu 的 {@code RegisterEvent} 在特定情况下可能被反复派发 ⇒ 必须自锁。
     */
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private ShanhaiRecipeTypes() {}

    /**
     * 全部 76 条的注册。<b>只允许</b>从 {@code GTCEuAPI.RegisterEvent<ResourceLocation, GTRecipeType>}
     * 的监听器里调（见类注释的窗口证据）；重复调用安全。
     *
     * <p>逐条对应原版行号已写在每组注释里；<b>唯一系统性偏离 = 去掉链尾的 {@code setSound}</b>（见类注释）。
     */
    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            ShanhaiMod.LOGGER.info("[SHANHAI-SPEC] ShanhaiRecipeTypes.init() 重复调用，已跳过（幂等）");
            return;
        }

        // ═══════════ 链式调用逐字照抄 DShanhaiRecipeTypes.java:236-490（唯一偏离：去掉链尾 setSound，见类注释）═══════════

        // DShanhaiRecipeTypes.java:60-69


        // :82-91
        PRIMORDIAL_POWER_GENERATOR = GTRecipeTypes.register("primordial_power_generator", "multiblock")
                .setMaxIOSize(2, 2, 2, 2)
                .setEUIO(IO.OUT)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);



        // :111-117 —— 原版无 slotOverlay；链尾 setSound(ARC) 在 setOffsetVoltageText 之前（照抄原版顺序）
        PRIMORDIAL_STELLAR_REACTION = GTRecipeTypes.register("primordial_stellar_reaction", "multiblock")
                .setMaxIOSize(5, 3, 5, 3)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_FUSION, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSound(GTSoundEntries.ARC)
                .setOffsetVoltageText(true);

        // :119-128
        PRIMORDIAL_BIOLOGICAL_CORE = GTRecipeTypes.register("primordial_biological_core", "multiblock")
                .setMaxIOSize(6, 3, 3, 3)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :130-139
        PRIMORDIAL_MATTER_RECOMBINATION = GTRecipeTypes.register("primordial_matter_recombination", "multiblock")
                .setMaxIOSize(12, 3, 6, 3)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :141-150
        PRIMORDIAL_CAUSAL_WEAVING = GTRecipeTypes.register("primordial_causal_weaving", "multiblock")
                .setMaxIOSize(12, 3, 6, 3)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :152-161
        PRIMORDIAL_SINGULARITY_INVERSION = GTRecipeTypes.register("primordial_singularity_inversion", "multiblock")
                .setMaxIOSize(12, 3, 6, 3)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);







        // :225-234 —— 原版 overlay 顺序是 DUST,FLUID,DUST,FLUID（与其他条不同，照抄）
        TAIXU_SMELTING = GTRecipeTypes.register("taixu_smelting", "multiblock")
                .setMaxIOSize(2, 2, 1, 1)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT);




        // :268-277
        WORLDLINE_OSCILLATION_COLLECTION = GTRecipeTypes.register("worldline_oscillation_collection", "multiblock")
                .setMaxIOSize(2, 2, 2, 2)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :279-288
        INTERSTELLAR_MATTER_ABSORPTION = GTRecipeTypes.register("interstellar_matter_absorption", "multiblock")
                .setMaxIOSize(2, 2, 2, 2)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :290-298 —— 3 条 slotOverlay
        MATTER_FLOW_CONDENSATION = GTRecipeTypes.register("matter_flow_condensation", "multiblock")
                .setMaxIOSize(4, 2, 2, 2)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :300-309
        PRIMORDIAL_ENERGY_ABSORPTION = GTRecipeTypes.register("primordial_energy_absorption", "multiblock")
                .setMaxIOSize(1, 2, 2, 2)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :311-320
        // 🔴 2026-09-26 用户裁决（原话逐字）：「photon_separation 的 setMaxIOSize(2, 4, 2, 2) ⇒ (4, 10, 2, 2)」
        //    —— 物品入 2 ⇒ 4、物品出 4 ⇒ 10，**流体那 2/2 不动**。
        //    起因：3 条星门配方（shanhai:pf/photon_2、shanhai:pf/electron、shanhai:pf/photon_rainbow）
        //    的物品输入要 3 格（1~2 个真物品 + notConsumable(力场发生器) + .circuit(1)），旧上限只有 2 ⇒ 溢出。
        //    放宽到 4 后这 3 条装得下（最坏 4 格，留 1 格余量）；物品出放宽到 10 是同一句裁决里的配套。
        //    ⚠️ 改的是【注册期上限】，本次【没有】改任何配方；四条实参位置 = (物品入, 物品出, 流体入, 流体出)。
        PHOTON_SEPARATION = GTRecipeTypes.register("photon_separation", "multiblock")
                .setMaxIOSize(4, 10, 2, 2)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :322-331 —— 有 setHasResearchSlot(true)；3 条 slotOverlay
        //
        // 🔴 2026-09-25 用户裁决改 IO。用户原话逐字：「可以都补一下，然后物质模块铸造就这样改，
        //    我们不需要那么多的流体输入和任意的流体输出」
        //    ⇒ setMaxIOSize 由 (15,6,6,6) 改成 (17,1,4,0)。
        //    四个数 = 该类型的专属界面模板真正画出来的槽位数
        //    （assets/gtceu/ui/recipe_type/matter_module_casting.rtui，154×80）：
        //        item_in  = 17  （id item_in_0 … item_in_16；其中 item_in_16 落在 x=130 那一格）
        //        item_out = 1   （模板里只有 item_out_0）
        //        fluid_in = 4   （fluid_in_0 … fluid_in_3，占 x=93 一整列）
        //        fluid_out= 0   （模板里【没有任何】fluid_out_* widget）
        //    数法不是眼看，是 GTCEu 自己的匹配规则：GTRecipeTypeUI 用正则
        //    "^<cap>_<io>_[0-9]+$" 找槽位，而 RecipeCapability.slotName(IO) = "%s_%s"
        //    （javap -c 实证）⇒ 只有前缀 item_in_/item_out_/fluid_in_/fluid_out_ 的 widget 算数。
        //    ⚠️ item_in_16 的 id 前缀是 item_in（不是 item_out），所以它在 UI 里被当成
        //       第 17 个【物品输入】槽，不是第二个输出槽。
        //    ⇒ 这四格同时就是【现存 25 条 matter_module_casting 配方的实测最大值】：
        //      local\kubejs\export\{recipes,added_recipes}\dishanhai\matter_module_casting\
        //      （各 25 条，两处一致）实测 max(itemIn)=17 / max(itemOut)=1 / max(fluidIn)=4 / max(fluidOut)=0
        //      ⇒ 改成 (17,1,4,0) 后 25 条**全部装得下，一条都不受影响**（离线逐条清点过）。
        //    📌 顺带：旧的 (15,6,6,6) 反而装不下 matter_module_casting_create_mk（17 个物品输入）
        //      —— 这是旧口径下就存在的既有问题，本次一并修好。
        //      （原版私货 DShanhaiRecipeTypes 里也是 (15,6,6,6)，javap 实证 ⇒ 不是我方引入。）
        MATTER_MODULE_CASTING = GTRecipeTypes.register("matter_module_casting", "multiblock")
                .setMaxIOSize(17, 1, 4, 0)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setHasResearchSlot(true)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :333-341 —— 3 条 slotOverlay
        // 🔴 2026-09-26 用户点单（原话逐字）：「把物质铸造这一种类配方（输入，输出）修改为组装机这一种类」
        //    ⇒ 口径 = 把 setMaxIOSize 换成【组装机 gtceu:assembler】那一套：(4,2,2,2) → (9,1,1,0)。
        //    四个数的来源（javap 实证，本整合包【没有】改过它）：
        //      gtceu jar `GTRecipeTypes.<clinit>`：`ldc "assembler"` → `register(...)` 之后紧接着
        //      `bipush 9 / iconst_1 / iconst_1 / iconst_0 / invokevirtual setMaxIOSize:(IIII)`
        //      （偏移 268 → 285）；包内"既引用 ASSEMBLER_RECIPES 又调用 setMaxIOSize"的类只有
        //      gtlcore `RecipeModify` 一个，而它的 `init()` 对 ASSEMBLER_RECIPES 只调 `onRecipeBuild`
        //      （两次），**没有** setMaxIOSize ⇒ 组装机的有效规格就是原版那四个数。
        //    ⚠️ 用户同一句话里的「物质铸造」= 这条 matter_forging；**物质模块铸造**
        //      (matter_module_casting, 上面那段) 不在这条指令范围内，本次一个字都没动。
        //    ⚠️ 超限核对（交付报告里另有逐条清单）：现存 13 条 matter_forging 配方实测
        //      max(itemIn)=4 / max(itemOut)=1 / max(fluidIn)=2 / max(fluidOut)=1
        //      ⇒ 新规格下 **9 条超出流体限额**（6 条 fluidIn=2 > 1、4 条 fluidOut=1 > 0）。
        //      这是本改动的**已知代价**，已按要求上报，未擅自改配方、也未擅自放宽规格。
        MATTER_FORGING = GTRecipeTypes.register("matter_forging", "multiblock")
                .setMaxIOSize(9, 1, 1, 0)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // ───── 2026-09-26 新增：原初物质解构（用户点单，第 41 条真类型）─────
        //
        // 🔴 写法逐字对齐【同一批】的 photon_separation（上方 :311-320）与 matter_forging（上方 :333-341）：
        //    同 category "multiblock"、同 setEUIO(IO.IN)、同 setMaxTooltips(4)、
        //    同 PROGRESS_BAR_ARROW + LEFT_TO_RIGHT、同 4 条 slotOverlay（FLUID-in / DUST-in / FLUID-out / DUST-out）、
        //    链尾同样**没有** setSound（用户 2026-09-22 裁决：gtladditions 的 setSound 不抄）。
        //
        // 🔴 setMaxIOSize(1, 103, 1, 16) 的四个数【不是估的】，是专属模板真正画出的槽位数。
        //    模板 = assets/gtceu/ui/recipe_type/primordial_matter_deconstruction.rtui（**190×114**）。
        //    ⚠️ 2026-09-26 第二次改（用户裁决「放宽上限到 16」）：fluid-out **10 ⇒ 16**。
        //       起因：`star_gate_crystal_slurry` 那条产线的输入有 **16 种流体**，10 格装不下。
        //       ⇒ .rtui 同步把 fluid_out 从 10 槽加到 16 槽（**8 列 × 2 行**），
        //          root.size 190×96 ⇒ **190×114**。两处必须同步，否则 JEI 画不满/画多余。
        //    取证方式两条（互相独立、结论一致）：
        //      ① 该文件由 black_hole_event_horizon_blast.rtui（75,413 B、root.size 226×256）
        //         裁剪改写而来 —— 范本里 item_out 的网格是 12 列 × 10 行、步长严格 18，
        //         我们保留 x=5..167 的 10 列 × y=37,55 的 2 行 = 20 格（item_out_0..19），
        //         流体出取 8 列 × y=73,91 的 2 行 = 16 格（fluid_out_0..15），
        //         另加 item_in_0 与【补造的】fluid_in_0 各 1 格。
        //      ② 用 NBT 解析器回读新文件，逐槽数 id 并对绝对坐标做断言（生成器自证，见交付报告）。
        //    ⚠️ 与 matter_module_casting 那次（改 setMaxIOSize 去贴合模板）同口径：
        //       模板画几个槽，上限就写几个。
        PRIMORDIAL_MATTER_DECONSTRUCTION = GTRecipeTypes.register("primordial_matter_deconstruction", "multiblock")
                .setMaxIOSize(1, 103, 1, 16)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :343-351 —— PROGRESS_BAR_CIRCUIT + CIRCUIT_OVERLAY
        WL_BOARD_CIRCUIT_ASSEMBLY = GTRecipeTypes.register("wl_board_circuit_assembly", "multiblock")
                .setMaxIOSize(9, 3, 6, 4)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.CIRCUIT_OVERLAY)
                .setSlotOverlay(true, false, false, GuiTextures.CIRCUIT_OVERLAY);

        // :353-361 —— 3 条 overlay：FLUID,DUST,CIRCUIT（注意不是全 CIRCUIT）
        WL_BOARD_WAFER_ETCHING = GTRecipeTypes.register("wl_board_wafer_etching", "multiblock")
                .setMaxIOSize(6, 3, 4, 3)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_CIRCUIT, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, false, GuiTextures.CIRCUIT_OVERLAY);

        // ========== 世线裂解枢纽 ==========



        // ========== 原初世线切割核心 ==========






        // ========== 原初铸币工厂（原版有该机器，本工程已删；类型照原版保留）==========


        // ═══════════════════════════════════════════════════════════════════════════════════════
        // 🔴 2026-09-26 恢复的 24 段 register 链（用户点单"40 条"）
        //    原文 = 本文件 ⛔作废块（"【作废 · register 链原文】"）逐字，去掉行首注释符。
        //    每段保留 {@code // :NNN-NNN} 的【旧私货 DShanhaiRecipeTypes.java 原行号】便于对账。
        //    ⚠️ 集中放在这里（fail-fast 之前），不再插回原位置；理由见字段区那段长注释。
        // ═══════════════════════════════════════════════════════════════════════════════════════

        // :485-490 —— 无 slotOverlay，setMaxTooltips(1)
        PROXY_EXECUTION = GTRecipeTypes.register("proxy_execution", "multiblock")
                .setMaxIOSize(0, 0, 0, 0)
                .setEUIO(IO.IN)
                .setMaxTooltips(1)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT);

        // :474-483
        COIN_FORGE = GTRecipeTypes.register("coin_forge", "multiblock")
                .setMaxIOSize(9, 6, 6, 3)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :453-462
        NINE_INDUSTRIAL = GTRecipeTypes.register("nine_industrial", "multiblock")
                .setMaxIOSize(24, 24, 12, 12)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :442-451 —— PROGRESS_BAR_FUSION
        BLACK_HOLE_EVENT_HORIZON_BLAST = GTRecipeTypes.register("black_hole_event_horizon_blast", "multiblock")
                .setMaxIOSize(3, 9, 3, 6)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_FUSION, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :431-440 —— PROGRESS_BAR_COMPRESS
        BLACK_HOLE_NEUTRONIUM_COMPRESSOR = GTRecipeTypes.register("black_hole_neutronium_compressor", "multiblock")
                .setMaxIOSize(9, 6, 6, 5)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_COMPRESS, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :420-429 —— PROGRESS_BAR_COMPRESS
        BLACK_HOLE_COMPRESSOR = GTRecipeTypes.register("black_hole_compressor", "multiblock")
                .setMaxIOSize(9, 6, 6, 5)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_COMPRESS, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :409-418
        HIGH_DIMENSIONAL_FRAGMENT_CUTTING = GTRecipeTypes.register("high_dimensional_fragment_cutting", "multiblock")
                .setMaxIOSize(4, 9, 2, 4)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :398-407
        WORLDLINE_CUTTING = GTRecipeTypes.register("worldline_cutting", "multiblock")
                .setMaxIOSize(6, 6, 4, 4)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :386-395
        WORLDLINE_SAMPLING = GTRecipeTypes.register("worldline_sampling", "multiblock")
                .setMaxIOSize(3, 12, 3, 6)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :375-384
        WORLDLINE_MATTER_RECURRENCE = GTRecipeTypes.register("worldline_matter_recurrence", "multiblock")
                .setMaxIOSize(9, 6, 6, 3)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :364-373
        WORLDLINE_PROBABILITY_CRACKING = GTRecipeTypes.register("worldline_probability_cracking", "multiblock")
                .setMaxIOSize(6, 9, 4, 4)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :256-266 —— category "single"；全 40 条里唯一 5 条 slotOverlay（原版原文第 261/262 行是两条相同的 DUST，照抄不合并）
        PHOTON_SIPHON = GTRecipeTypes.register("photon_siphon", "single")
                .setMaxIOSize(4, 2, 2, 2)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT);

        // :245-254 —— category "single"
        ZERO_POINT_CONVERSION = GTRecipeTypes.register("zero_point_conversion", "single")
                .setMaxIOSize(2, 2, 2, 2)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT);

        // :236-243 —— category "single"（用户 2026-09-22 亲定中文名：原初物质凝集）
        MATTER_AGGREGATION = GTRecipeTypes.register("matter_aggregation", "single")
                .setMaxIOSize(2, 2, 0, 0)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :216-223
        GRAVITATIONAL_WAVE_CONSUMPTION = GTRecipeTypes.register("gravitational_wave_consumption", "multiblock")
                .setMaxIOSize(1, 0, 1, 0)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY);

        // :205-214
        TIANJIE_NAVIGATION = GTRecipeTypes.register("tianjie_navigation", "multiblock")
                .setMaxIOSize(6, 3, 6, 3)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :194-203
        NEBULA_SIPHONING = GTRecipeTypes.register("nebula_siphoning", "multiblock")
                .setMaxIOSize(6, 3, 6, 3)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :183-192 —— 全 40 条里唯一的 EUIO = IO.BOTH
        CHAOS_CRAFTING = GTRecipeTypes.register("chaos_crafting", "multiblock")
                .setMaxIOSize(24, 24, 12, 12)
                .setEUIO(IO.BOTH)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :174-181 —— category 是裸字符串 "single"
        SEVENTY_TWO_CHANGES = GTRecipeTypes.register("seventy_two_changes", "single")
                .setMaxIOSize(1, 1, 0, 0)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :163-172
        GRAVITATIONAL_WAVE_PRODUCTION = GTRecipeTypes.register("gravitational_wave_production", "multiblock")
                .setMaxIOSize(2, 2, 2, 2)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :102-109 —— 链尾 setSound(GTSoundEntries.ARC)：GTCEu 自己的 API，按队长 2026-09-22 裁决【保留抄写】
        PRIMORDIAL_MYRIAD_ASCENSION_TIER_1 = GTRecipeTypes.register("primordial_myriad_ascension_tier_1", "multiblock")
                .setMaxIOSize(4, 0, 4, 0)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSound(GTSoundEntries.ARC);

        // :93-100
        PRIMORDIAL_MYRIAD_ASCENSION_TIER_2 = GTRecipeTypes.register("primordial_myriad_ascension_tier_2", "multiblock")
                .setMaxIOSize(4, 0, 4, 0)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSound(GTSoundEntries.ARC);

        // :71-80
        KU_MING_YUAN_YANG = GTRecipeTypes.register("kmyy", "multiblock")
                .setMaxIOSize(2, 1, 0, 0)
                .setEUIO(IO.OUT)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // :60-69（作废块原文漏了行号标记，2026-09-26 按上游补上）
        SPACETIME_DISTORTION = GTRecipeTypes.register("spacetime_distortion", "multiblock")
                .setMaxIOSize(9, 6, 6, 5)
                .setEUIO(IO.IN)
                .setMaxTooltips(4)
                .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
                .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
                .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
                .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);

        // ───────── fail-fast（就地）：任何一条静默没生效，就在这里响亮地失败 ─────────
        int realMissing = countMissingReal();
        if (realMissing > 0) {
            throw new IllegalStateException("[SHANHAI] 配方类型注册不完整：真类型缺 " + realMissing + " / "
                    + REAL_TYPE_COUNT + "。抽查三句柄＝primordial_matter_recombination="
                    + PRIMORDIAL_MATTER_RECOMBINATION
                    + " / primordial_stellar_reaction=" + PRIMORDIAL_STELLAR_REACTION
                    + " / taixu_smelting=" + TAIXU_SMELTING);
        }

        ShanhaiMod.LOGGER.info("[SHANHAI-SPEC] 配方类型已注册：真类型 {} / {}"
                        // 🔴 2026-09-26 订正：这句原写「2026-09-23 裁剪后仅保留被 24 台模块引用的类型」——
                        //    用户 2026-09-26 点单"40 条"已把那 24 条恢复 ⇒ 旧话术不再是事实，故改写。
                        //    ⚠️ 那个「51」不是手写数字，是下面两个 {} 之外的常量；见 REAL_TYPE_COUNT=40 / 显示类型 0。
                        + "（2026-09-26 用户点单恢复：40 条真类型全部注册；"
                        + "逐条照抄原版 DShanhaiRecipeTypes.java；只注册、不挂机器；"
                        + "36 条 GTNH 显示类型仍不恢复 —— 用户 2026-09-22「那个 GTNH 是我重制版不会添加的」；"
                        + "链尾 gtladditions 的 setSound 按用户 2026-09-22 裁决省略）",
                REAL_TYPE_COUNT - realMissing, REAL_TYPE_COUNT);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // ⛔ 作废块（2026-09-23，用户裁决「裁剪配方类型注册」）
    //    🔴 2026-09-26 状态更新：**本块内容已被【恢复为活代码】**（用户点单"40 条"）——
    //       24 个字段声明在文件上方「2026-09-26 恢复」那一段，24 段 register 链在 init() 末尾。
    //       本块**原样保留作历史留档**（本工程惯例：改判时旧文不删）。
    //       ⇒ 读代码请以活代码为准；本块只是"当时为什么删、原文长什么样"的凭证。
    //    🔴 仍然有效、且**不许顺手改回来**的一条：**36 条显示类型不恢复**（GTNH 来源）。
    //    以下是被删掉的 **24 个真类型** 与 **36 个显示类型** 的【原文】，逐字保留，
    //    仅每行前置 "// " 使其成为注释（不能包在 /* */ 里：原文含 javadoc，注释不可嵌套）。
    //    将来做新机器时要重新加回来。
    //
    //    🔴 随之作废的【三个用户拟名】——名字的由来必须留档（否则将来没人知道是谁起的）：
    //        · gtceu:matter_aggregation              = 原初物质凝集   （用户 2026-09-22 拟名）
    //        · gtceu:worldline_cutting               = 原初世线切割   （用户 2026-09-22 拟名）
    //        · gtceu:high_dimensional_fragment_cutting = 高维碎片裁切 （用户 2026-09-22 拟名）
    //      这三个名字【不是】原版 lang 原文（原版 lang 里根本没有这三个键），是用户亲定的；
    //      类型本次被删 ⇒ 三个名字随之作废，但由来按规定留档在此。
    //      用户若反悔，重新加回类型 + 重新加回 lang 键即可（键值见 git 历史/交付报告）。
    //
    //    另：36 个显示类型 { nine_industrial_mode_0 .. _35 } 来自 GTNH（GTnotleisure），
    //        用户 2026-09-22 原话「那个 GTNH 是我重制版不会添加的」⇒ 整组删除，
    //        数组 NINE_INDUSTRIAL_MODES / for 循环 / DISPLAY_TYPE_COUNT / countMissingDisplay()
    //        一并删除，【不留恒为 0 的空壳】。
    //    ═══════════════════════════════════════════════════════════════════════════════════════
    // 【作废 · 字段声明原文】
    //     /** 代理执行占位类型（{@code setMaxTooltips(1)}，无 slotOverlay）—— 代理执行机器。 */
    //     public static GTRecipeType PROXY_EXECUTION;
    // 
    //     /** 原初铸币工厂 —— 原版有该机器，<b>本工程已删除该机器</b>；类型照原版保留注册。 */
    //     public static GTRecipeType COIN_FORGE;
    // 
    //     /** 大明科技聚合类型 —— 大明工业机器。 */
    //     public static GTRecipeType NINE_INDUSTRIAL;
    // 
    //     /** 事件视界爆破（4 条带专属 .rtui 的类型之一）—— 黑洞收容机器。 */
    //     public static GTRecipeType BLACK_HOLE_EVENT_HORIZON_BLAST;
    // 
    //     /** 黑洞中子态素压缩 —— 黑洞收容机器。 */
    //     public static GTRecipeType BLACK_HOLE_NEUTRONIUM_COMPRESSOR;
    // 
    //     /** 黑洞引力压缩 —— 黑洞收容机器。 */
    //     public static GTRecipeType BLACK_HOLE_COMPRESSOR;
    // 
    //     /** 高维碎片裁切（用户拟名）—— <b>原版零挂载</b>。 */
    //     public static GTRecipeType HIGH_DIMENSIONAL_FRAGMENT_CUTTING;
    // 
    //     /** 原初世线切割（用户拟名）—— <b>原版零挂载</b>。 */
    //     public static GTRecipeType WORLDLINE_CUTTING;
    // 
    //     /** 世线采样 —— 世线裂解枢纽。 */
    //     public static GTRecipeType WORLDLINE_SAMPLING;
    // 
    //     /** 世线物质重现 —— 世线裂解枢纽。 */
    //     public static GTRecipeType WORLDLINE_MATTER_RECURRENCE;
    // 
    //     /** 概率裂解 —— 世线裂解枢纽。 */
    //     public static GTRecipeType WORLDLINE_PROBABILITY_CRACKING;
    // 
    //     /** 光子虹吸（{@code "single"}，5 条 slotOverlay）—— 世线裂解枢纽 ＋ 零点光子转换器。 */
    //     public static GTRecipeType PHOTON_SIPHON;
    // 
    //     /** 零点转换（{@code "single"}）—— 世线裂解枢纽 ＋ 零点光子转换器。 */
    //     public static GTRecipeType ZERO_POINT_CONVERSION;
    // 
    //     /** 原初物质凝集（用户拟名；原版 lang 无此键）—— <b>原版零挂载</b>。 */
    //     public static GTRecipeType MATTER_AGGREGATION;
    // 
    //     /** 引力波广域广播 —— 引力波天线发射器。 */
    //     public static GTRecipeType GRAVITATIONAL_WAVE_CONSUMPTION;
    // 
    //     /** 宇宙修改·天界领航 —— 天界领航塔（注册在它自己类里）。 */
    //     public static GTRecipeType TIANJIE_NAVIGATION;
    // 
    //     /** 多维星穹零点聚合 —— 天界星云零点虹吸枢纽。 */
    //     public static GTRecipeType NEBULA_SIPHONING;
    // 
    //     /** 混沌合成（lang 原文值含 §k 混淆格式码）—— 引力波天线发射器 ＋ 创世之眼模块。 */
    //     public static GTRecipeType CHAOS_CRAFTING;
    // 
    //     /** 七十二变 —— 引力波天线发射器。 */
    //     public static GTRecipeType SEVENTY_TWO_CHANGES;
    // 
    //     /** 引力波宏观干涉 —— 引力波天线发射器。 */
    //     public static GTRecipeType GRAVITATIONAL_WAVE_PRODUCTION;
    // 
    //     /** 一级原初万象晋升 —— 原初万象衍生核心（我们未做该模块）。 */
    //     public static GTRecipeType PRIMORDIAL_MYRIAD_ASCENSION_TIER_1;
    // 
    //     /** 二级原初万象晋升 —— 原初万象衍生核心（我们未做该模块）。 */
    //     public static GTRecipeType PRIMORDIAL_MYRIAD_ASCENSION_TIER_2;
    // 
    //     /** 苦命鸳鸯 —— 终焉创始现实修改矩阵。 */
    //     public static GTRecipeType KU_MING_YUAN_YANG;
    // 
    //     /** 量子化现实重构 —— 终焉创始现实修改矩阵。 */
    //     public static GTRecipeType SPACETIME_DISTORTION;
    // 

    // 【作废 · register 链原文】（原 init() 里逐条照抄 DShanhaiRecipeTypes.java 的那 24 段）
    //         // :485-490 —— 无 slotOverlay，setMaxTooltips(1)
    //         PROXY_EXECUTION = GTRecipeTypes.register("proxy_execution", "multiblock")
    //                 .setMaxIOSize(0, 0, 0, 0)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(1)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT);
    // 
    //         // :474-483
    //         COIN_FORGE = GTRecipeTypes.register("coin_forge", "multiblock")
    //                 .setMaxIOSize(9, 6, 6, 3)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :453-462
    //         NINE_INDUSTRIAL = GTRecipeTypes.register("nine_industrial", "multiblock")
    //                 .setMaxIOSize(24, 24, 12, 12)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :442-451 —— PROGRESS_BAR_FUSION
    //         BLACK_HOLE_EVENT_HORIZON_BLAST = GTRecipeTypes.register("black_hole_event_horizon_blast", "multiblock")
    //                 .setMaxIOSize(3, 9, 3, 6)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_FUSION, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :431-440 —— PROGRESS_BAR_COMPRESS
    //         BLACK_HOLE_NEUTRONIUM_COMPRESSOR = GTRecipeTypes.register("black_hole_neutronium_compressor", "multiblock")
    //                 .setMaxIOSize(9, 6, 6, 5)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_COMPRESS, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :420-429 —— PROGRESS_BAR_COMPRESS
    //         BLACK_HOLE_COMPRESSOR = GTRecipeTypes.register("black_hole_compressor", "multiblock")
    //                 .setMaxIOSize(9, 6, 6, 5)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_COMPRESS, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :409-418
    //         HIGH_DIMENSIONAL_FRAGMENT_CUTTING = GTRecipeTypes.register("high_dimensional_fragment_cutting", "multiblock")
    //                 .setMaxIOSize(4, 9, 2, 4)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :398-407
    //         WORLDLINE_CUTTING = GTRecipeTypes.register("worldline_cutting", "multiblock")
    //                 .setMaxIOSize(6, 6, 4, 4)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :386-395
    //         WORLDLINE_SAMPLING = GTRecipeTypes.register("worldline_sampling", "multiblock")
    //                 .setMaxIOSize(3, 12, 3, 6)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :375-384
    //         WORLDLINE_MATTER_RECURRENCE = GTRecipeTypes.register("worldline_matter_recurrence", "multiblock")
    //                 .setMaxIOSize(9, 6, 6, 3)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :364-373
    //         WORLDLINE_PROBABILITY_CRACKING = GTRecipeTypes.register("worldline_probability_cracking", "multiblock")
    //                 .setMaxIOSize(6, 9, 4, 4)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :256-266 —— category "single"；全 40 条里唯一 5 条 slotOverlay（原版原文第 261/262 行是两条相同的 DUST，照抄不合并）
    //         PHOTON_SIPHON = GTRecipeTypes.register("photon_siphon", "single")
    //                 .setMaxIOSize(4, 2, 2, 2)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT);
    // 
    //         // :245-254 —— category "single"
    //         ZERO_POINT_CONVERSION = GTRecipeTypes.register("zero_point_conversion", "single")
    //                 .setMaxIOSize(2, 2, 2, 2)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT);
    // 
    //         // :236-243 —— category "single"（用户 2026-09-22 亲定中文名：原初物质凝集）
    //         MATTER_AGGREGATION = GTRecipeTypes.register("matter_aggregation", "single")
    //                 .setMaxIOSize(2, 2, 0, 0)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :216-223
    //         GRAVITATIONAL_WAVE_CONSUMPTION = GTRecipeTypes.register("gravitational_wave_consumption", "multiblock")
    //                 .setMaxIOSize(1, 0, 1, 0)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :205-214
    //         TIANJIE_NAVIGATION = GTRecipeTypes.register("tianjie_navigation", "multiblock")
    //                 .setMaxIOSize(6, 3, 6, 3)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :194-203
    //         NEBULA_SIPHONING = GTRecipeTypes.register("nebula_siphoning", "multiblock")
    //                 .setMaxIOSize(6, 3, 6, 3)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :183-192 —— 全 40 条里唯一的 EUIO = IO.BOTH
    //         CHAOS_CRAFTING = GTRecipeTypes.register("chaos_crafting", "multiblock")
    //                 .setMaxIOSize(24, 24, 12, 12)
    //                 .setEUIO(IO.BOTH)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :174-181 —— category 是裸字符串 "single"
    //         SEVENTY_TWO_CHANGES = GTRecipeTypes.register("seventy_two_changes", "single")
    //                 .setMaxIOSize(1, 1, 0, 0)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :163-172
    //         GRAVITATIONAL_WAVE_PRODUCTION = GTRecipeTypes.register("gravitational_wave_production", "multiblock")
    //                 .setMaxIOSize(2, 2, 2, 2)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         // :102-109 —— 同上
    //         PRIMORDIAL_MYRIAD_ASCENSION_TIER_1 = GTRecipeTypes.register("primordial_myriad_ascension_tier_1", "multiblock")
    //                 .setMaxIOSize(4, 0, 4, 0)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSound(GTSoundEntries.ARC);
    // 
    //         // :93-100 —— 链尾 setSound(GTSoundEntries.ARC)：GTCEu 自己的 API，按队长 2026-09-22 裁决【恢复抄写】
    //         PRIMORDIAL_MYRIAD_ASCENSION_TIER_2 = GTRecipeTypes.register("primordial_myriad_ascension_tier_2", "multiblock")
    //                 .setMaxIOSize(4, 0, 4, 0)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSound(GTSoundEntries.ARC);
    // 
    //         // :71-80
    //         KU_MING_YUAN_YANG = GTRecipeTypes.register("kmyy", "multiblock")
    //                 .setMaxIOSize(2, 1, 0, 0)
    //                 .setEUIO(IO.OUT)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 
    //         SPACETIME_DISTORTION = GTRecipeTypes.register("spacetime_distortion", "multiblock")
    //                 .setMaxIOSize(9, 6, 6, 5)
    //                 .setEUIO(IO.IN)
    //                 .setMaxTooltips(4)
    //                 .setProgressBar(GuiTextures.PROGRESS_BAR_ARROW, ProgressTexture.FillDirection.LEFT_TO_RIGHT)
    //                 .setSlotOverlay(false, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(false, false, false, GuiTextures.DUST_OVERLAY)
    //                 .setSlotOverlay(true, false, true, GuiTextures.FLUID_SLOT)
    //                 .setSlotOverlay(true, false, false, GuiTextures.DUST_OVERLAY);
    // 

    /** CommonSetup 兜底用：{@link #REAL_TYPE_COUNT} 条是否全部拿到句柄。 */
    public static boolean allRegistered() {
        return countMissingReal() == 0;
    }

    /**
     * {@link #REAL_TYPE_COUNT} 条真类型里拿到 null 的条数。
     *
     * <p>🔴 <b>2026-09-26：16 → 40 → 41。</b>这个数组<b>必须</b>与
     * {@link #REAL_TYPE_COUNT} 的条数一致 —— 少写一条的后果是
     * {@code allRegistered()} 对它<b>静默不查</b>（"没报错"与"查过了且没问题"在日志上长得一模一样），
     * 本工程为此付过账。改类型数量时两处必须同步。
     */
    private static int countMissingReal() {
        GTRecipeType[] all = {
                PRIMORDIAL_MATTER_RECOMBINATION, PRIMORDIAL_STELLAR_REACTION, PRIMORDIAL_POWER_GENERATOR,
                PRIMORDIAL_BIOLOGICAL_CORE, PRIMORDIAL_CAUSAL_WEAVING, PRIMORDIAL_SINGULARITY_INVERSION,
                WORLDLINE_OSCILLATION_COLLECTION, INTERSTELLAR_MATTER_ABSORPTION, PRIMORDIAL_ENERGY_ABSORPTION,
                MATTER_FLOW_CONDENSATION, PHOTON_SEPARATION, MATTER_MODULE_CASTING,
                MATTER_FORGING, WL_BOARD_CIRCUIT_ASSEMBLY, WL_BOARD_WAFER_ETCHING, TAIXU_SMELTING,
                // ───── 2026-09-26 恢复的 24 条（用户点单"40 条"）─────
                PROXY_EXECUTION, COIN_FORGE, NINE_INDUSTRIAL,
                BLACK_HOLE_EVENT_HORIZON_BLAST, BLACK_HOLE_NEUTRONIUM_COMPRESSOR, BLACK_HOLE_COMPRESSOR,
                HIGH_DIMENSIONAL_FRAGMENT_CUTTING, WORLDLINE_CUTTING, WORLDLINE_SAMPLING,
                WORLDLINE_MATTER_RECURRENCE, WORLDLINE_PROBABILITY_CRACKING,
                PHOTON_SIPHON, ZERO_POINT_CONVERSION, MATTER_AGGREGATION,
                GRAVITATIONAL_WAVE_CONSUMPTION, TIANJIE_NAVIGATION, NEBULA_SIPHONING,
                CHAOS_CRAFTING, SEVENTY_TWO_CHANGES, GRAVITATIONAL_WAVE_PRODUCTION,
                PRIMORDIAL_MYRIAD_ASCENSION_TIER_1, PRIMORDIAL_MYRIAD_ASCENSION_TIER_2,
                KU_MING_YUAN_YANG, SPACETIME_DISTORTION,
                // ───── 2026-09-26 新增的第 41 条（用户点单「原初物质解构」）─────
                PRIMORDIAL_MATTER_DECONSTRUCTION};
        int missing = 0;
        for (GTRecipeType t : all) {
            if (t == null) {
                missing++;
            }
        }
        return missing;
    }
}
