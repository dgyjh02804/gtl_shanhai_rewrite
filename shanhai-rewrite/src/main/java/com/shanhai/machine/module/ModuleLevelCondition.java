package com.shanhai.machine.module;

import com.google.gson.JsonObject;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.shanhai.ShanhaiMod;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 山海重构 · <b>物质模块等级配方条件</b>（老山海 {@code module_level} 的移植，<b>简化版</b>）。
 *
 * <h2>0. 出处</h2>
 * <pre>
 * 上游：originals/upstream\gtl_shanhai-dishanhai\src\main\java\com\dishanhai\gt_shanhai\api\ModuleLevelCondition.java
 *       （236 行，{@code extends com.gregtechceu.gtceu.api.recipe.RecipeCondition}）
 * 上游注册：GTDishanhaiGTAddon.java:34-38
 *       GTRegistries.RECIPE_CONDITIONS.unfreeze();
 *       GTRegistries.RECIPE_CONDITIONS.register("module_level", ModuleLevelCondition.TYPE);
 *       GTRegistries.RECIPE_CONDITIONS.freeze();
 * </pre>
 *
 * <h2>1. 🔴 判定规则 = <b>等级上界比较</b>（用户 2026-09-26 定案，<b>刻意不用老山海那套</b>）</h2>
 * <p><b>用户原话（逐字）</b>：
 * <blockquote>
 * 「对了，我们的配方全都是只会要求有 1 个物质模块，不然若一个低级配方需要 4 个低级模块，
 * 而我要升级的时候材料只能够一个高级模块，那甚至不能执行低级配方，这样就很不好，所以物质模块的要求是有就行」
 * <br>「其实不用老山海的解法，用我这个解法就好，我不希望游戏太复杂」
 * </blockquote>
 * ⇒ <b>本类的判定式只有一条</b>：
 * <pre>
 *   槽里的物质模块等级  &gt;=  要求等级        ⇒ 通过
 *   其中   要求等级   = {@code PrimordialModuleMachine.getModuleLevelById(moduleId)}（由物品 id 自己决定）
 *          槽里等级   = {@code module.getMatterModuleLevel()}（空槽 = 0）
 * </pre>
 * ⇒ <b>不看数量、不做等效换算、没有"高等级顶多个低等级"这一层</b>。
 * 配方的 N（{@code "1x …"} 里那个 1）<b>一律写 1</b>，且<b>不参与判定</b>（见 §3）。
 * <p>⚠️ <b>与上游的三处差别，逐条写明（不许含糊）</b>：
 * <ol>
 *   <li>上游有 {@code ModuleLevelEquivalence.calculateEquivalentCount}
 *       （等级差 0~3 每级翻倍、差 ≥4 = {@code Long.MAX_VALUE}）<b>—— 本工程【没有这个类】，刻意不移植</b>
 *       （用户原话：「不用老山海的解法」）。上游那个 {@code requiredLevel} 是<b>数量</b>，
 *       本工程的语义里<b>数量不参与判定</b>。</li>
 *   <li>上游 {@code test()} 只走<b>主机聚合分支</b>（16 模块位合并等效量）；本工程走<b>模块分支</b>。
 *       理由见 §4。</li>
 *   <li>上游 {@code getStyledName()} 用 {@code DShanhaiTextUtil} / {@code ShanhaiTextAPI}
 *       两个<b>本工程不存在</b>的类做服务端预解析；本工程改成直接返回物品的显示名 Component
 *       （2026-09-26 修复轮起 = {@code new ItemStack(item).getHoverName()}），理由见 {@link #displayName()}。</li>
 * </ol>
 *
 * <h2>2. 语义示例（用户点名的三条判据）</h2>
 * <pre>
 *   要求 = shanhai:introductory_material_module（该物品 = 1 级）⇒ 要求等级 = 1
 *     装 [material_deduction_module = 3 级]  ⇒ 3 ≥ 1 ⇒ ✅ 通过
 *     装 [introductory_material_module = 1 级] ⇒ 1 ≥ 1 ⇒ ✅ 通过
 *     空槽（等级 0）                            ⇒ 0 ≥ 1 ⇒ ❌ 不通过
 * </pre>
 *
 * <h2>3. ⚠️ 一个必须讲清楚的歧义（如实交代，别当成"肯定对"）</h2>
 * <p>「要求等级」有两个可能的读法：**(a) 由 {@code moduleId} 这个物品自己在等级表里的档位决定**；
 * **(b) 由构造器第二个实参 {@link #requiredLevel}（也就是 KJS 里 {@code "1x"} 的那个 1）决定**。
 * 本类取 **(a)**，理由两条：
 * <ol>
 *   <li>{@code shanhai_pf_recipes.js}（<b>已冻结、不许改</b>）的 §6 注释写死了：
 *       「这里的 N 是【数量】，而"等级"由那个物品 id 自己决定」；</li>
 *   <li>读法 (b) 下 {@code "4x <1 级模块>"} 会变成"要求等级 4"（用户明确讨厌的那种复杂度），
 *       而 {@code "1x <3 级模块>"} 又会丢掉"要求 3 级"这件事 ⇒ (b) 与"把模块等级作为配方要求"这个目标矛盾。</li>
 * </ol>
 * <p>⇒ 对<b>当前唯一的那条配方 ⑦</b>（{@code "1x shanhai:introductory_material_module"}）两种读法<b>给出同一个门槛 1</b>
 * ⇒ 用户那三条判据在两种读法下都成立。若将来要按读法 (b) 走，改一行即可（见 {@link #requiredLevelForGate()}）。
 *
 * <h2>4. ⛔ 【未移植】主机聚合分支（16 模块位合并等效量）</h2>
 * <p>上游 {@code hostEquivalentCountSatisfies(IModularMachineHost)}（原 :161-185）依赖
 * {@code org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost} /
 * {@code IModularMachineModule} 与 {@code PrimordialOmegaEngineMachine#getModuleSet()} /
 * {@code #getEquivalentModuleCountForLevel(int)} —— <b>本工程一个都没有，本轮不移植</b>。
 * <p>⇒ 后果，<b>明确写出来</b>：本条件在<b>非模块机</b>上（含主机、含任何别的机器）<b>一律判 false</b>，
 * 并打一条<b>只出现一次</b>的可 grep WARN（{@code [SHANHAI-MODULE-LEVEL]}）。
 * <b>刻意不选"静默放行"</b>：放行 = 门槛失效 —— 那正是本项目明令禁止的「悄悄不发生」型失败。
 * <p>实际影响 = 0：本条件目前只挂在 {@code shanhai:pf/photon} 一条配方上，而它属于 {@code photon_siphon} 类型，
 * 该类型只被模块（原初分歧发生器 / 原初山海调试模块）挂载（取证：{@code ModuleRegistry.RECIPE_DIVERGENCE_GENERATOR}）。
 *
 * <h2>5. {@link #REQUIREMENTS} 静态表：保留但不参与判定（如实交代）</h2>
 * <p>{@link #register} / {@link #clearRequirements} / {@link #getRequirements} 是上游"配方 ID → 条件列表"
 * 的内存表，供老山海那条模块判定链使用。<b>本工程没有那条链</b>（见 §4 与 {@link #test}）⇒ 这三方法目前
 * <b>没有任何调用者</b>。保留只为与上游逐字对照。<b>不要以为写了 {@code register(...)} 就会生效。</b>
 *
 * <h2>6. 🔴 2026-09-26 修复轮：门槛被算成 0（用户报「空槽也能跑」）＋ 提示行改成中文名</h2>
 *
 * <h3>6.1 现象（用户截图，逐条列原始字符串）</h3>
 * <pre>
 *   JEI 那一行 : 模块要求: 1x shanhai:introductory_material_module （等级 &gt;= 0）
 *   机器 GUI    : 已安装模块:（空槽） ＋ 当前机器模式: 光子虹吸 ＋ 运行正常
 * </pre>
 * 用户原话：「<b>jei显示是这样的，我希望它可以写中文，而且我发现它的限制并没有生效，我没放物质模块它能工作</b>」
 *
 * <h3>6.2 根因（判据 = 显示串反推，不是猜）</h3>
 * <ol>
 *   <li>{@link #getTooltips()} 的字节码只有三段：{@code "§b模块要求："} ＋ {@code getStyledName()} ＋
 *       {@code " §7（等级 ≥ " + N + "）"}（{@code javap -c} 可证，本类里<b>没有任何</b>"N× "前缀拼接）。</li>
 *   <li>⇒ 上一行里那个 {@code 1x } 前缀<b>只可能来自名字那一段</b>，而名字那一段在物品查不到时会
 *       <b>原样回退成 {@link #moduleId}</b>。</li>
 *   <li>⇒ 显示的 {@code 1x shanhai:introductory_material_module} 就是<b>构造时收到的 {@code moduleId} 原文</b>；
 *       它<b>不是 17 个模块 id 之一</b>（表里是 {@code shanhai:introductory_material_module}）⇒
 *       {@code getModuleLevelById} 走 {@code getOrDefault(..., 0)} ⇒ <b>门槛 = 0</b>。</li>
 *   <li>⇒ 门槛 0 的后果是<b>语义反转</b>：{@code 0 &gt;= 0} 为真 ⇒ <b>空槽反而通过</b>。
 *       这就是「没放物质模块它也能工作」的机制（门槛失效，而且是"越缺东西越放行"）。</li>
 * </ol>
 * <p>⚠️ 本条里"0 是怎么来的"和"那个 {@code 1x } 前缀是谁传进来的"<b>是两件事</b>：
 * 前者由 {@code getOrDefault} 的静默语义决定（本类的问题，已修）；
 * 后者来自脚本侧的 {@code "Nx &lt;物品id&gt;"} 写法（{@code shanhai_pf_recipes.js} §6，<b>本轮未改</b>）。
 * 本类的修法<b>不依赖</b>去指认后者——见 §6.3。
 *
 * <h3>6.3 修法一：把 {@code "Nx <id>"} 写法在 jar 侧也认下来（{@link #normalizeModuleId}）</h3>
 * <p>老山海的配方侧写法本来就是 {@code "Nx <物质模块物品id>"}（由它的引擎在 jar 侧切分），
 * 本工程的 KJS 也照抄了这个写法。既然脚本与条件之间<b>隔着一层写法</b>，就在条件里把这层写法认下来：
 * 剥掉数字 + {@code x/X} 前缀后再查表。⇒ 两种入参（{@code "1x id"} 与 {@code "id"}）<b>都得到 1</b>。
 *
 * <h3>6.4 修法二：查不到的 id <b>不再静默当成 0</b>（fail-closed ＋ 一次性 WARN）</h3>
 * <p>🔴 这是本轮最重要的语义变更，<b>刻意选的</b>：
 * <ul>
 *   <li>查不到时<b>放行</b>（旧行为：门槛 0 ⇒ 空槽也通过）= 门槛失效 = 本项目最忌讳的「悄悄不发生」；</li>
 *   <li>查不到时<b>拦下</b>（新行为：门槛 {@link #UNRESOLVABLE_GATE} ⇒ 任何模块等级都过不了）
 *       ＋ 一条可 grep 的 WARN 点名那个 id。</li>
 * </ul>
 * ⇒ 一个写错的 id 从此是「配方跑不起来 ＋ 日志点名」，而不是「配方偷偷不需要模块」。
 * <p>⚠️ 唯一例外：{@code moduleId} 为空串（= {@link #createTemplate()} / GTCEu 的 {@code createDefault()}
 * 造出来的模板实例，它<b>本来就没有配置</b>）⇒ 同样 fail-closed，但<b>不</b>打 WARN（那不是配置错误）。
 *
 * <h3>6.5 修法三：提示行改成中文名（用户要求）</h3>
 * <pre>
 *   改前: 模块要求: 1x shanhai:introductory_material_module（等级 &gt;= 0）
 *   改后: 模块要求: 1× 入门物质模块（等级 ≥ 1）
 * </pre>
 * <ul>
 *   <li>名字 = {@code new ItemStack(item).getHoverName()} —— 这就是"标准做法"：
 *       它内部是 {@code Component.translatable(item.getDescriptionId())}，
 *       即 <b>lang 键 {@code item.shanhai.introductory_material_module}</b>，
 *       客户端用 {@code assets/shanhai/lang/zh_cn.json} 渲成「入门物质模块」。</li>
 *   <li>数量位 = 构造器第二个实参（{@code "1x"} 里那个 1），渲染成 {@code 1× }。</li>
 *   <li>⚠️ <b>诚实边界</b>：专用服务端<b>不加载客户端 lang</b>，所以在冒烟日志里这行会打印出
 *       <b>lang 键本身</b>（{@code item.shanhai.introductory_material_module}）而不是中文。
 *       这不是没修好，而是"键 → 值"的解析发生在客户端；证明方式 = 键 + 反读 lang 表（见 §6.6）。</li>
 * </ul>
 *
 * <h3>6.6 本轮的机器可验判据</h3>
 * <pre>
 *   ① 门槛值   : requiredLevelForGate() == 1（对 "1x shanhai:introductory_material_module" 与
 *                "shanhai:introductory_material_module" 两种入参都成立）
 *   ② 空槽     : meetsLevelRequirement(0, 1) == false
 *   ③ 有模块   : meetsLevelRequirement(1, 1) == true ／ meetsLevelRequirement(3, 1) == true
 *   ④ 中文名   : 提示行里的名字 == lang 键 item.shanhai.introductory_material_module，
 *                且该键在 jar 的 zh_cn.json 里 == 「入门物质模块」（反读核对）
 *   ⑤ 走的哪条 : 日志 [SHANHAI-PF] module-level-condition available=true ⇒ 走等级门槛、催化剂形态已停用
 * </pre>
 */
public class ModuleLevelCondition extends RecipeCondition {

    public static final Codec<ModuleLevelCondition> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("module_id").forGetter(c -> c.moduleId),
                    Codec.INT.fieldOf("level").forGetter(c -> c.requiredLevel)
            ).apply(instance, ModuleLevelCondition::new)
    );

    public static final RecipeConditionType<ModuleLevelCondition> TYPE = new RecipeConditionType<>(
            () -> new ModuleLevelCondition("", 0),
            ModuleLevelCondition.CODEC
    );

    /** 模块物品 id（如 {@code shanhai:introductory_material_module}）。 */
    public final String moduleId;

    /**
     * KJS 写法 {@code "Nx <物品id>"} 里的 <b>N</b>。
     * <p>🔴 <b>本工程不拿它当门槛，也不拿它当需求数量</b> —— 见类注释 §1/§3。
     * 保留字段只为：① 与上游 JSON / 网络协议逐字同形（{@code level} 键）；② 显示（{@code ×N}）。
     */
    public final int requiredLevel;

    // ====== 静态注册表：绕过 KubeJS 序列化/反序列化问题（上游原样保留，本工程无调用者） ======
    /** 配方ID → 模块条件列表 */
    private static final java.util.Map<String, java.util.List<ModuleLevelCondition>> REQUIREMENTS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 配方注册时调用，将条件存入内存表（⚠️ 见类注释 §5：本工程当前无调用者、不参与判定）。 */
    public static void register(String recipeId, ModuleLevelCondition cond) {
        REQUIREMENTS.computeIfAbsent(recipeId, k -> new java.util.ArrayList<>()).add(cond);
    }

    /** 每次配方重载前清空上一轮条件，避免修改后的旧需求继续残留。 */
    public static void clearRequirements() {
        REQUIREMENTS.clear();
    }

    /** 运行时按完整配方 ID 精确查询，禁止相似配方之间串条件。 */
    public static java.util.List<ModuleLevelCondition> getRequirements(String recipeId) {
        if (recipeId == null || recipeId.isEmpty()) return null;
        return REQUIREMENTS.get(recipeId);
    }

    public ModuleLevelCondition(String moduleId, int level) {
        super(false);
        this.moduleId = moduleId;
        this.requiredLevel = level;
    }

    // ═══════════════════════════ 判定（用户 2026-09-26 定案的简化版） ═══════════════════════════

    /**
     * 本条件要的等级 —— <b>由 {@code moduleId} 这个物品在等级表里的档位决定</b>（类注释 §3 的读法 (a)）。
     * <p>{@code PrimordialModuleMachine.MODULE_LEVELS} 是<b>全工程唯一</b>的"物品 id → 等级"映射
     * （1..17；见 {@code PrimordialModuleMachine#getModuleLevelById} 的 javadoc），
     * 本类<b>不另建一套</b>。
     * <p>⚠️ 若将来要改成读法 (b)（用构造器第二个实参当门槛），把本方法的返回值换成 {@code requiredLevel} 即可 ——
     * <b>但那是语义变更，需先拿用户裁决</b>。
     *
     * <p>🔴 <b>2026-09-26 修复</b>（类注释 §6.2）：查表前先过 {@link #effectiveModuleId()}（剥掉
     * {@code "Nx "} 配方侧前缀）；<b>查不到时不再返回 0</b>，而是 {@link #UNRESOLVABLE_GATE} ＋ 一条
     * 点名 id 的 WARN。原先"查不到 → 0"让门槛<b>语义反转</b>（0 &gt;= 0 ⇒ 空槽通过），
     * 这就是用户看到的「没放物质模块它也能工作」。
     */
    public int requiredLevelForGate() {
        final String id = effectiveModuleId();
        if (id.isEmpty()) {
            // createTemplate() / GTCEu createDefault() 造出来的"未配置"模板：fail-closed，但不打 WARN。
            return UNRESOLVABLE_GATE;
        }
        final int level = PrimordialModuleMachine.getModuleLevelById(id);
        if (level <= 0) {
            shanhai$warnUnknownIdOnce(id);
            return UNRESOLVABLE_GATE;
        }
        return level;
    }

    /**
     * 🔴 查不到 id 时的门槛（<b>fail-closed</b>，见类注释 §6.4）。
     *
     * <p>取值 = {@link Integer#MAX_VALUE}：它<b>永远大于</b>任何槽里等级（槽里等级最大 17），
     * ⇒ {@link #meetsLevelRequirement} 恒 false ⇒ 配方被拦下（而不是被放行）。
     * <p>⚠️ 为什么不返回 0：0 = "无门槛"，那正是本轮要修的 bug —— 一个写错的 id 会<b>静默</b>
     * 让门槛消失。这里的取舍是<b>宁可跑不起来，也不许悄悄放行</b>。
     */
    public static final int UNRESOLVABLE_GATE = Integer.MAX_VALUE;

    /**
     * 把配方侧写法 {@code "Nx <物品id>"}（老山海 / 本工程 KJS 的写法）剥成<b>纯物品 id</b>。
     *
     * <pre>
     *   "1x shanhai:introductory_material_module" -&gt; "shanhai:introductory_material_module"
     *   "shanhai:introductory_material_module"    -&gt; 原样（没有前缀）
     *   "4x shanhai:basic_material_module"         -&gt; "shanhai:basic_material_module"
     *   "1X shanhai:basic_material_module"         -&gt; "shanhai:basic_material_module"（大写 X 也认）
     * </pre>
     * ⚠️ 只剥【数字开头 + x/X】这一种前缀，不做别的猜测：一个真实物品 id 不可能以数字开头
     * （{@code ResourceLocation} 的路径本来就不允许数字开头之外的怪写法，且本表 17 个 id 都不以数字开头）。
     *
     * @param raw 原样入参，可为 null
     * @return 纯 id；入参为 null 时返回空串（不是 null，调用方不必再判空）
     */
    public static String normalizeModuleId(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        int i = 0;
        while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
            i++;
        }
        if (i > 0 && i < s.length() && (s.charAt(i) == 'x' || s.charAt(i) == 'X')) {
            s = s.substring(i + 1).trim();
        }
        return s;
    }

    /** {@link #moduleId} 去掉配方侧数量前缀之后的<b>真物品 id</b>（本类所有查表都用它）。 */
    public String effectiveModuleId() {
        return normalizeModuleId(moduleId);
    }

    /** 门槛是否可解（false = id 写错了 / 没配置；此时门槛 = {@link #UNRESOLVABLE_GATE}）。 */
    public boolean isGateResolvable() {
        String id = effectiveModuleId();
        return !id.isEmpty() && PrimordialModuleMachine.getModuleLevelById(id) > 0;
    }

    /** 显示用门槛文本：可解 = 数字；不可解 = {@code ?（未识别的物质模块 id: …）}。 */
    private String gateText() {
        if (isGateResolvable()) {
            return String.valueOf(PrimordialModuleMachine.getModuleLevelById(effectiveModuleId()));
        }
        return "?（未识别的物质模块 id：" + (moduleId == null || moduleId.isEmpty() ? "未配置" : moduleId) + "）";
    }

    /**
     * 查不到 id 时只打一次的可 grep WARN（避免每 tick 刷屏）。
     * <p>它是「id 写错了」这件事在日志里的<b>唯一</b>痕迹 —— 没有它，这个失败和"门槛本来就是 0"长得一样。
     */
    private void shanhai$warnUnknownIdOnce(String normalizedId) {
        final String key = normalizedId;
        if (!shanhai$unknownIdWarned.add(key)) {
            return;
        }
        ShanhaiMod.LOGGER.warn("[SHANHAI-MODULE-LEVEL] 条件 `module_level` 的 module_id 不是 17 个物质模块之一 ⇒ "
                        + "门槛按【不可解】处理（= 任何等级都过不了，配方会被拦下，不会静默放行）。"
                        + "收到的原文 =《{}》，剥掉数量前缀后 =《{}》，"
                        + "要求等级字段（构造器第二个实参，只用于显示）= {}。"
                        + "正确写法：module_id 必须是 PrimordialModuleMachine.MODULE_LEVELS 里那 17 个 id 之一"
                        + "（例如 shanhai:introductory_material_module），可带 `Nx ` 前缀。",
                moduleId, key, requiredLevel);
    }

    /** 已经 WARN 过的 id 集合（每个 id 只报一次）。 */
    private static final java.util.Set<String> shanhai$unknownIdWarned =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 🔴 <b>纯函数形态的判定式（本类唯一的判据）</b> —— 拆出来是为了能被离线装置直接调用取证
     * （{@code _batchfix-verify\ModuleLevelGateProof.java}；{@code checkModuleLevel} 需要一台真机器，离线跑不了）。
     *
     * <pre>
     *   槽里等级 &gt;= 要求等级  ⇒ 通过
     *   空槽（槽里等级 = 0）⇒ ❌ 不通过（因为要求等级恒 ≥ 1，见 {@link #requiredLevelForGate()}）
     * </pre>
     * ⚠️ {@code installedLevel} 是<b>槽里那个模块自己的等级</b>，不是数量；数量<b>不参与</b>。
     */
    public static boolean meetsLevelRequirement(int installedLevel, int requiredLevel) {
        return installedLevel >= requiredLevel;
    }

    /**
     * 🔴 模块侧判定 —— <b>本工程唯一真正生效的那条分支</b>（见类注释 §1/§4）。
     *
     * <pre>
     *   槽空  ⇒ getMatterModuleLevel() = 0 ⇒ 0 &gt;= 要求等级(≥1) ⇒ false
     *   有模块 ⇒ 该模块自己的等级 &gt;= 要求等级 ⇒ true
     * </pre>
     */
    public boolean checkModuleLevel(@Nullable MetaMachine machine) {
        // ── 模块机器路径（本工程 PrimordialModuleRecipeLogic 会走到这里）：只看本机自己的物质模块槽 ──
        if (machine instanceof PrimordialModuleMachine module) {
            return meetsLevelRequirement(module.getMatterModuleLevel(), requiredLevelForGate());
        }

        // ── ⛔ 主机聚合分支【未移植】（见类注释 §4）────────────────────────────────
        //    上游 hostEquivalentCountSatisfies(...) 依赖 IModularMachineHost / IModularMachineModule /
        //    PrimordialOmegaEngineMachine#getModuleSet / #getEquivalentModuleCountForLevel ——
        //    本工程一个都没有。这里【不静默放行】，而是判 false + 一条只出现一次的 WARN。
        if (!shanhai$notPortedWarned) {
            shanhai$notPortedWarned = true;
            ShanhaiMod.LOGGER.warn("[SHANHAI-MODULE-LEVEL] 条件 `module_level`({}，门槛等级 {}) 被一台【非模块机】求值 ⇒ "
                            + "判 false。原因：老山海的主机聚合分支（16 模块位合并）本轮【未移植】"
                            + "（依赖 IModularMachineHost / getEquivalentModuleCountForLevel，本工程没有）。"
                            + "实测影响应为 0：该条件目前只挂在 shanhai:pf/photon 上，其类型 photon_siphon 只被模块挂载。"
                            + "若你看到本行的机器本应跑这条配方 ⇒ 那就是原因，需要先补主机聚合分支。machine={}",
                    moduleId, requiredLevelForGate(), machine == null ? "null" : machine.getClass().getName());
        }
        return false;
    }

    /** 非模块机路径被走到时只打一次的闸门（避免每 tick 刷屏）。 */
    private static boolean shanhai$notPortedWarned = false;

    /**
     * 条件求值入口（{@code GTRecipe#checkConditions} 会调到这里）。
     *
     * <p>🔴 <b>与上游刻意不同</b>：上游这里只走主机聚合分支，本工程走
     * {@link #checkModuleLevel}（模块分支 + 未移植时的响亮失败），理由见类注释 §4。
     *
     * <p>{@code recipeLogic} 为 {@code null} ⇒ 没有任何机器上下文可判（例如纯展示 / 校验调用），
     * 此时<b>不拦</b>（返回 true）：那不是"某台机器上的准入判定"，判 false 只会把配方标成非法。
     */
    @Override
    public boolean test(GTRecipe recipe, @Nullable RecipeLogic recipeLogic) {
        if (recipeLogic == null) {
            return true;
        }
        return checkModuleLevel(recipeLogic.getMachine());
    }

    @Override
    public RecipeConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public Component getTooltips() {
        return Component.literal("§b模块要求：").append(displayNameWithCount())
                .append(Component.literal(" §7（等级 ≥ " + gateText() + "）"));
    }

    public Component getFailTooltip() {
        return Component.literal("§c✗ 模块不足：").append(displayNameWithCount())
                .append(Component.literal(" §7（等级 ≥ " + gateText() + "）"));
    }

    public Component getPassTooltip() {
        return Component.literal("§a✓ 模块满足：").append(displayNameWithCount())
                .append(Component.literal(" §7（等级 ≥ " + gateText() + "）"));
    }

    /**
     * 提示行里的「数量 ＋ 名字」那一段：{@code 1× 入门物质模块}。
     *
     * <pre>
     *   改前（2026-09-26 之前）：1x shanhai:introductory_material_module     ← 裸 id，还是配方侧写法
     *   改后（本轮）            ：1× 入门物质模块                            ← 该物品的【显示名】
     * </pre>
     * <ul>
     *   <li>数量 = 构造器第二个实参（{@code "1x"} 里那个 1，{@link #requiredLevel}），只用于显示、不参与判定；
     *       为 0 时整段省略（用 {@code N× } 而不是 {@code Nx }）。</li>
     *   <li>名字 = {@link #displayName()}，即物品的显示名 Component（客户端按 lang 表渲成中文）。</li>
     * </ul>
     */
    private Component displayNameWithCount() {
        Component name = displayName();
        if (requiredLevel <= 0) {
            return name;
        }
        return Component.literal(requiredLevel + "× ").append(name);
    }

    /**
     * 模块名 —— <b>用该物品自己的显示名（标准做法）</b>。
     *
     * <p>实现 = {@code new ItemStack(item).getHoverName()}：它内部就是
     * {@code Component.translatable(item.getDescriptionId())}（{@code Item.getName(ItemStack)}），
     * 即 lang 键 {@code item.shanhai.introductory_material_module}，
     * <b>在客户端</b>由 {@code assets/shanhai/lang/zh_cn.json} 渲成「入门物质模块」。
     * <p>⚠️ 用 {@link ResourceLocation#tryParse} 而不是 {@code new ResourceLocation(...)}：
     * 后者对非法 id（例如带 {@code "1x "} 前缀的那种）会<b>抛异常</b>，只能靠 catch 兜住；
     * 前者返回 null，语义更清楚。查不到物品时回退成<b>剥过前缀的纯 id</b>（而不是整条配方侧写法），
     * 并在后面由 {@link #gateText()} 明确标出"未识别"。
     *
     * <h2>🔴 与上游的偏离（如实交代）</h2>
     * <p>上游这里解析 {@code &$style-} 前缀并用 {@code DShanhaiTextUtil.createStyled(text, style)} 预渲染；
     * <b>这两个类本工程都没有</b>（全工程 0 命中）。
     * <p>本工程对应的是<b>客户端绘制期解析</b>（{@code ShanhaiTextParser}，{@code &$…-} 文本码的解析器）。
     * 本轮<b>不再</b>取 {@code getHoverName().getString()}（那会把可翻译 Component 拍平成字符串，
     * 丢掉 lang 解析），而是**原样返回 Component**，让客户端在绘制期解析 —— 这既满足"要显示中文"，
     * 也保住了上游那条 {@code &$…-} 管线的位置。
     */
    private Component displayName() {
        final String id = effectiveModuleId();
        if (id.isEmpty()) {
            return Component.literal("§7（未配置模块）");
        }
        try {
            final ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                final Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item).getHoverName();
                }
            }
        } catch (Exception ignored) {
            // 注册表还没起来 / 物品不存在 ⇒ 回退到纯 id（下面那行），不影响任何判定。
        }
        return Component.literal(id);
    }

    /** 提示行里显示的物品 id（给离线取证装置与日志用；不含配方侧数量前缀）。 */
    public String displayNameIdForProbe() {
        return effectiveModuleId();
    }

    @Override
    public RecipeCondition createTemplate() {
        return new ModuleLevelCondition("", 0);
    }

    @Override
    public JsonObject serialize() {
        JsonObject json = super.serialize();
        json.addProperty("module_id", moduleId);
        json.addProperty("level", requiredLevel);
        return json;
    }

    /**
     * 反序列化。
     *
     * <p>🔴 2026-09-26 修复：接受<b>两种形状</b>，并且缺字段不再 NPE。
     * <pre>
     *   ① 扁平（GTCEu 自家条件的约定，也是本类 serialize() 写出来的形状）：
     *        { "module_id": "shanhai:introductory_material_module", "level": 1 }
     *   ② KubeJS {@code GTRecipeComponents$5.write()} 写出来的外层包装：
     *        { "type": "module_level", "data": { "module_id": …, "level": … } }
     * </pre>
     * 为什么必须认 ②：KubeJS 那个组件的 {@code write} 产出 ②，而它的 {@code read} 在拿到 JsonObject 时
     * 会把<b>这个外层对象原样</b>交给 {@code condition.deserialize(json)}
     * （取证：{@code javap -c com.gregtechceu.gtceu.integration.kjs.recipe.components.GTRecipeComponents$5}）。
     * ⇒ 旧实现里 {@code json.get("module_id")} 在 ② 上会取到 null 并 NPE（配方直接解析失败）。
     * <p>缺字段时：{@code module_id} 缺失 ⇒ 空串（= 未配置，门槛不可解、拦下并（对非空 id）打 WARN）；
     * {@code level} 缺失 ⇒ 0（只影响显示的数量位）。
     */
    @Override
    public ModuleLevelCondition deserialize(JsonObject json) {
        super.deserialize(json);
        JsonObject data = json;
        if (json.has("data") && json.get("data").isJsonObject()) {
            data = json.getAsJsonObject("data");
        }
        final var mid = data.get("module_id");
        final var lvl = data.get("level");
        return new ModuleLevelCondition(
                mid == null || mid.isJsonNull() ? "" : mid.getAsString(),
                lvl == null || lvl.isJsonNull() ? 0 : lvl.getAsInt()
        );
    }

    @Override
    public void toNetwork(FriendlyByteBuf buf) {
        super.toNetwork(buf);
        buf.writeUtf(moduleId);
        buf.writeVarInt(requiredLevel);
    }

    @Override
    public ModuleLevelCondition fromNetwork(FriendlyByteBuf buf) {
        super.fromNetwork(buf);
        return new ModuleLevelCondition(buf.readUtf(), buf.readVarInt());
    }
}
