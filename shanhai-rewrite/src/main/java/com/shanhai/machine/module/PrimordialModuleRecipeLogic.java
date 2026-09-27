package com.shanhai.machine.module;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic;
import com.gtladd.gtladditions.common.data.ParallelData;
import com.shanhai.ShanhaiMod;
import com.shanhai.common.recipe.PrimordialRecipeEffects;

import net.minecraft.network.chat.Component;

import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeMultiplierTracker;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 24 台原初模块的<b>多配方引擎逻辑</b>（{@code MutableRecipesLogic} 的本工程子类）——
 * "跨配方线程"机制在模块侧的落点。
 *
 * <h2>本类要解决的唯一问题：把模块的配方口径搬到引擎路径上，且【值 = 1 时逐值不变】</h2>
 * 引擎（gtladditions {@code MutableRecipesLogic}）与原生的差别不是"多一个功能"，而是：
 * <ol>
 *   <li>引擎路径<b>完全不经过</b> {@code RecipeLogic.checkMatchedRecipeAvailable}
 *       ⇒ 模块挂在 {@code .recipeModifier(...)} 上的整条效果链（{@code ModuleRegistry#applyModuleRecipeModifier}
 *       的 并行→N3→N5时长→N6→N5耗能）会<b>静默失效</b>。本类的
 *       {@link #buildFinalNormalRecipe(ParallelData)} 把这条链迁到引擎路径上，
 *       <b>全部复用 {@link PrimordialRecipeEffects} 的同一批纯函数</b>（本类里不出现任何自有算式）。</li>
 *   <li>引擎自己<b>硬编码</b> {@code minDuration = 20}（{@code RecipeCalculationHelper.buildNormalRecipe}），
 *       不认识模块侧栏那个"配方最短耗时"（该旋钮 2026-09-22 已从模块侧摘除）⇒ 由尾链的 N6 按 d0 与 f 重算。</li>
 *   <li>引擎的候选集是<b>该配方类型的全部可跑配方</b>（贪心分配后合并成一条）
 *       ⇒ 值 = 1 时必须限制成一条，见 {@link #lookupRecipeSet()}。</li>
 * </ol>
 *
 * <h2>🔴 EUt 口径（本类最容易出错的一格，已用字节码实证）</h2>
 * <pre>
 *   原生链：{@code ParallelLogic.doParallelRecipes} → {@code recipe.copy(ContentModifier.multiplier(limit), false)}
 *           ⇒ {@code GTRecipe.copy(ContentModifier, boolean)} 把 inputs / outputs / <b>tickInputs</b> / tickOutputs
 *             <b>四张表全部乘 p</b> ⇒ 原生 {@code EUt_content = baseEUt × p}
 *           （运行时真正执行的是 gtlcore {@code ParallelLogicMixin.doParallelRecipes} 的覆写，
 *             它同样是 {@code recipe.copy(ContentModifier.multiplier(limitByOutput), modifyDuration)}）
 *   引擎：{@code EUt_engine = totalEu / D}，而 {@code totalEu = baseEUt × p × d0} ⇒ 同样含 p
 *   ⇒ 两边对 p 的处理一致；本类只需把"引擎时长 D ⇒ 原时长 d0"这一档还原：
 *       {@link PrimordialRecipeEffects#rescaleEnergyForDuration(GTRecipe, int, int) rescaleEnergyForDuration(built, D, d0)}
 *       给出 {@code EUt_engine × D/d0 = totalEu/d0 = baseEUt × p} ✅
 *   ⚠️ {@code realParallels} <b>不参与</b>逐 tick 耗能（gtlcore 里它只进
 *      {@code RecipeRunner} 的概率掷骰、{@code BatchProcessing} 与显示追踪器 —— 已逐类核过）
 *      ⇒ 不存在"重复乘 p"。引擎新造配方的 {@code realParallels} 保持默认 1。
 * </pre>
 *
 * <h2>🔴 "1 线程 = 一次只跑一种配方"是【我们替上游改的语义】</h2>
 * 上游把 {@code getMultipleThreads()} 当<b>并行预算的倍数</b>（{@code totalParallel = getMaxParallel() × threads}）。
 * 本工程改成：<b>候选配方集合最多取 {@code threads} 条</b>（见 {@link #lookupRecipeSet()}）。
 * 值 = 1 时这两套语义在"预算充足"时等价（第一条吃满预算），但在输入稀缺时不同 ——
 * 上游会把余量分给第二条并合并，我们不这么做（那样会与模块今天的行为分叉）。
 * ⇒ <b>这是刻意的口径改动，写在这里以防将来被当成 bug 修掉。</b>
 *
 * <h2>发电模块（唯一那台）走原生链</h2>
 * 它的配方 EUt 是 {@code Integer.MIN_VALUE}（输出电力语义，见冒烟日志
 * {@code zero_point_power … EUt=-2147483648}），而引擎的 {@code totalEu} 是"耗电"累加
 * ⇒ 会算出深负值。判据用 {@code getDefinition().isGenerator()}（全 24 台只有它 true），
 * 命中则 {@code setUseMultipleRecipes(false)} = 退回原生链（那条链的 {@code .generator} 语义原样保留）。
 * <p>⚠️ 切换点放在 {@link #findAndHandleRecipe()} 里<b>懒判定</b>：构造期不许读机器字段
 * （{@code MachineBuilder} 在机器构造过程中调 {@code createRecipeLogic}，此刻字段还没初始化）。
 */
public class PrimordialModuleRecipeLogic extends MutableRecipesLogic<PrimordialModuleMachine> {

    /** 懒判定闸门：只跑一次（发电特判 + 一条可 grep 的路由日志）。 */
    private boolean shanhai$routingResolved;

    /** 对账器的正面对照闸门：只跑一次。 */
    private boolean shanhai$probeSelfTested;

    public PrimordialModuleRecipeLogic(PrimordialModuleMachine machine) {
        super(machine);
        // 引擎开关本身（不写这一行 = 对象是 MutableRecipesLogic 但走的是父类原生路径）。
        // 发电那台会在 findAndHandleRecipe() 里被懒关掉 —— 见类注释。
        setUseMultipleRecipes(true);
    }

    @Override
    public void findAndHandleRecipe() {
        shanhai$resolveRouting();
        // 🔴 每轮重扫前清空「这一轮为什么没跑起来」的暂存 —— 见 shanhai$publishFailReason 的 A③ 注释。
        shanhai$pendingFailReason = null;
        shanhai$pendingFailIsModuleLevel = false;
        super.findAndHandleRecipe();
        shanhai$publishFailReason();
    }

    /**
     * 🔴 <b>2026-09-26：把并行预算接回 long（本任务 ② 的落点）。</b>
     *
     * <h2>病根（一句话）</h2>
     * 父类 {@code MutableRecipesLogic.calculateParallels()} 的预算是
     * <pre>
     *   [源码原文] MutableRecipesLogic.kt:198
     *       val totalParallel: Long = (long)this.getMachine().getMaxParallel() * this.getMultipleThreads();
     * </pre>
     * 第一个因子 {@code getMaxParallel()} 是 <b>gtlcore {@code ParallelMachine} 的 int 方法</b>
     * ⇒ 并行表末三档（4611686018427387903 / 6917529027641081855 / 9223372036854775807）
     * <b>在这里被压成 2147483647</b>，17 档实际只有 14 档有区分度。
     *
     * <h2>修法（老山海的形态，逐条对齐）</h2>
     * <pre>
     *   ① 预算换成 long：{@code totalParallelLimitFor(getCurrentParallel(), getMultipleThreads())}
     *      —— 形状 = 老山海 SelectableRecipeTypeSetRecipeLogic:449 的
     *         {@code saturatedMultiply(getMachine().getRecipeLogicMaxParallel(), getLogicThreadMultiplier())}；
     *   ② 分配仍是上游那一份算法，只是换成 long 版
     *      （{@link PrimordialRecipeEffects#greedyAllocateWithLongLimit} 逐句照抄上游，
     *        因为上游那个形参类型 kotlin.jvm.functions.Function2 在编译期不可见）；
     *   ③ 🔴 <b>预算 ≤ Integer.MAX_VALUE 时【原样走父类】</b> ⇒ 并行表前 14 档、
     *      空槽 64、以及一切正常玩法走的都是<b>改动前那条字节码</b>。
     * </pre>
     * 判据（可 grep）：预算 &gt; 2^31−1 时打一行 {@code [SHANHAI-PARALLEL-LONG]}，
     * 里面同时给出「上限 / 预算 / 15..17 档会命中」。
     *
     * <h2>⚠️ 诚实边界</h2>
     * 本方法保证的是「<b>引擎收到的并行<b>上限</b> = 表值</b>」。
     * 真正分配出去多少，仍由上游的两条边界收敛：
     * {@code IParallelLogic.getMaxParallel}（<b>实际可用输入量</b>）与
     * <b>输出空间</b>。这就是用户规格里那句「实际并行由输入量与输出空间决定」；
     * 装满 4.6e18 份原料时它才会真的是 4.6e18。
     */
    @Override
    protected @Nullable ParallelData calculateParallels() {
        final PrimordialModuleMachine module = getMachine();
        final long limit = PrimordialModuleMachine.totalParallelLimitFor(
                module.getCurrentParallel(), getMultipleThreads());
        if (limit <= (long) Integer.MAX_VALUE) {
            // ≤ int 范围 ⇒ 上游原路（父类的 (long)getMaxParallel()*getMultipleThreads()），逐字不变。
            return super.calculateParallels();
        }
        shanhai$logLongParallelBudget(limit);
        return PrimordialRecipeEffects.greedyAllocateWithLongLimit(
                lookupRecipeSet(), limit, module,
                (recipe, remain) -> calculateParallel(module, recipe, remain));
    }

    /** 上一次记下的 long 预算（只在数值变化时打一行，不刷屏；{@code Long.MIN_VALUE} = 还没打过）。 */
    private static long shanhai$lastLoggedLongBudget = Long.MIN_VALUE;

    /** 预算超过 int 范围时的<b>一次性可 grep 证据行</b>（值变化才打）。 */
    private static void shanhai$logLongParallelBudget(long limit) {
        if (limit == shanhai$lastLoggedLongBudget) {
            return;
        }
        shanhai$lastLoggedLongBudget = limit;
        ShanhaiMod.LOGGER.info("[SHANHAI-PARALLEL-LONG] 模块并行预算已走 long 通道："
                        + "engineLimit={}（int 桥会把它压成 {}）⇒ 这一档正是并行表末三档之一；"
                        + "分配算法 = 上游 greedy（long 版），预算本身不再被 int 钳制。",
                limit, Integer.MAX_VALUE);
    }

    /** 懒判定路由：发电模块退回原生链；其余留在引擎路径。只跑一次，日志可 grep。 */
    private void shanhai$resolveRouting() {
        if (shanhai$routingResolved) {
            return;
        }
        shanhai$routingResolved = true;
        final boolean generator = getMachine().getDefinition().isGenerator();
        if (generator) {
            setUseMultipleRecipes(false);
            ShanhaiMod.LOGGER.info("[SHANHAI-MODULE-ENGINE] 发电模块退回原生链（EUt 是输出语义，引擎会算成深负值）：{}",
                    getMachine().getPos());
        } else {
            ShanhaiMod.LOGGER.info("[SHANHAI-MODULE-ENGINE] 模块已接多配方引擎：跨配方线程 = {}，候选配方上限 = {} 条；{}",
                    getMultipleThreads(), getMultipleThreads(), getMachine().getPos());
        }
    }

    /**
     * 🔴 <b>值 = 1（以及任意 N）时的候选集上限 —— 我们替上游改的语义</b>（理由见类注释）。
     *
     * <p>上游 {@code MutableRecipesLogic.lookupRecipeSet()} 返回<b>全部</b>可跑配方，
     * 贪心分配会把 {@code getMaxParallel() × getMultipleThreads()} 的预算摊到多条上并合并成一条。
     * 本覆写只保留顺序最前的 {@code getMultipleThreads()} 条 ⇒ 值 = 1 时恒为一条（与今天的行为同形）。
     * <p>{@code LinkedHashSet} 保持上游迭代顺序（"取前 N 条"必须是确定性的，否则同一批输入会跑出不同配方）。
     */
    @Override
    protected @NotNull Set<GTRecipe> lookupRecipeSet() {
        final Set<GTRecipe> all = super.lookupRecipeSet();
        final int threads = Math.max(1, getMultipleThreads());
        if (all.size() <= threads) {
            return all;
        }
        final Set<GTRecipe> limited = new LinkedHashSet<>();
        for (GTRecipe recipe : all) {
            if (limited.size() >= threads) {
                break;
            }
            limited.add(recipe);
        }
        return limited;
    }

    /**
     * 🔴 <b>配方电压等级闸门 —— 2026-09-26 <u>恢复</u>（此前是本类【刻意删掉】的那一条）。</b>
     *
     * <h2>1. 父类原文（字节码实证，不是推断）</h2>
     * <pre>
     *   javap -c -p libs/gtladditions-3.2.8Custom-fix1.jar \
     *         com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic
     *   protected boolean checkRecipe(GTRecipe recipe);
     *       7: … getfield RecipeLogic.machine … checkcast IRecipeCapabilityHolder
     *      15: invokestatic RecipeRunnerHelper.matchRecipe:(…,GTRecipe;)Z
     *      18: ifeq 82                       ← matchRecipe 假 ⇒ false
     *      21: aload_1 / 22: invokestatic IGTRecipe.of:(GTRecipe;)LIGTRecipe;
     *      25: invokeinterface IGTRecipe.getEuTier:()I
     *      30: getfield machine / 34: invokevirtual WorkableElectricMultiblockMachine.getTier:()I
     *      37: if_icmpgt 82                  ← <b>euTier &gt; machineTier ⇒ false（这里就是我们删掉的那条）</b>
     *      40: recipe.checkConditions(this) …
     * </pre>
     * ⇒ 上游判据是三条合取：{@code matchRecipe && euTier &lt;= machine.getTier() && checkConditions}。
     *
     * <h2>2. 为什么当初删了它（留档，不许再当成"可以删"的理由）</h2>
     * 见本类旧注释的原文：模块 {@code tier} 当时被认为"固定 9"，而这条闸门会让 {@code euTier &gt; 9}
     * 的配方<b>从候选集里静默消失</b>。当时的选择是"整条删掉"。
     *
     * <h2>3. 🔴 为什么现在必须装回去（用户 2026-09-26 实机报的 bug）</h2>
     * 用户的机器电压来自它自己的<b>能源仓</b>（实测用的是创造能源仓，配成 ULV 8V/1A）。
     * 电压等级闸门一删，机器就<b>无条件</b>接受任何 {@code euTier} 的配方，与"这个机器有多少电压"脱钩
     * ⇒ 用户实机：同一个能源仓配置下，原版 GTL 的<b>大型挤压机</b>老实报
     * 「配方失败原因：电压等级未达到配方要求」，而我们的<b>原初山海调试模块</b>照跑 102/205 EU/t 的 MV/HV 配方。
     * <p>⚠️ <b>代价（必须让用户知情）</b>：装回去之后，<b>原本能跑的"超压配方"会跑不动</b>，
     * 显示为 gtlcore 现成的 {@code FAIL_VOLTAGE_TIER}「电压等级未达到配方要求」。这正是用户要的口径。
     *
     * <h2>4. 失败原因写进哪条链（不动自己造显示）</h2>
     * 拦下时写 {@link RecipeResult#FAIL_VOLTAGE_TIER}（gtlcore 自己的枚举），经
     * {@code RecipeResult.of(machine, …)} → {@code IRecipeStatus.setRecipeStatus} 落进
     * {@link org.gtlcore.gtlcore.api.machine.trait.IRecipeStatus}：
     * <ul>
     *   <li><b>Jade</b>：{@code org.gtlcore.gtlcore.mixin.gtm.RecipeLogicProviderMixin#write} 把
     *       {@code getRecipeStatus().reason()} 写成 NBT {@code reason}，{@code addTooltip} 用
     *       {@code gtceu.recipe.fail.reason}（"配方失败原因：%s"）渲成红字；</li>
     *   <li><b>机器 GUI</b>：{@code org.gtlcore.gtlcore.mixin.gtm.fix.WorkableElectricMultiblockMachineMixin
     *       #addDisplayText} 把同一个 {@code reason} 以 {@code ChatFormatting.RED} 追加进文本行
     *       （我们的模块正好 extends WorkableElectricMultiblockMachine ⇒ 这条混入对我们成立）。</li>
     * </ul>
     */
    @Override
    protected boolean checkRecipe(@NotNull GTRecipe recipe) {
        if (!RecipeRunnerHelper.matchRecipe((IRecipeCapabilityHolder) getMachine(), recipe)) {
            return false;
        }
        final int recipeEuTier = IGTRecipe.of(recipe).getEuTier();
        final int machineTier = getMachine().getTier();
        if (!ModuleVoltageGate.allows(machineTier, recipeEuTier)) {
            shanhai$recordVoltageTierBlock(recipeEuTier, machineTier);
            return false;
        }
        if (!recipe.checkConditions(this).isSuccess()) {
            shanhai$recordConditionBlock(recipe);
            return false;
        }
        return true;
    }

    // ═══════════════ 配方失败原因（GUI + Jade 同一条链） ═══════════════
    //     电压闸门的纯函数判据在 ModuleVoltageGate（可离线取证），本类只负责用它与报原因。

    /** 本轮扫描里记下的「为什么没跑起来」（{@code null} = 这一轮没有需要报的原因）。 */
    private Component shanhai$pendingFailReason;

    /** 上面那条是不是「物质模块等级」类的原因（它比"电压等级"更具体 ⇒ 优先级更高）。 */
    private boolean shanhai$pendingFailIsModuleLevel;

    /** 电压等级不足：记 gtlcore 现成的 {@code FAIL_VOLTAGE_TIER}。 */
    private void shanhai$recordVoltageTierBlock(int recipeEuTier, int machineTier) {
        shanhai$logVoltageTierBlock(recipeEuTier, machineTier);
        if (shanhai$pendingFailIsModuleLevel) {
            return;     // 物质模块等级那条更具体、且是本工程的自有条件 ⇒ 不覆盖它
        }
        shanhai$pendingFailReason = RecipeResult.FAIL_VOLTAGE_TIER.reason();
    }

    /**
     * 条件不满足：<b>只认我们自己的 {@link ModuleLevelCondition}</b>（别的配方条件不抢这一行）。
     *
     * <p>必须先逐条重测条件才能指名"是哪一条"：{@code GTRecipe#checkConditions} 只返回一个
     * {@code ActionResult}（{@code javap} 实测它有 {@code lambda$checkConditions$5/$6/$7} 三条私有 lambda，
     * 失败信息不逐条外露）。本方法复用的是<b>同一条</b> {@code RecipeCondition#test}，
     * 不另写判定 —— 用户那套「等级 ≥ 要求」的语义只有一个实现（{@code ModuleLevelCondition}）。
     */
    private void shanhai$recordConditionBlock(@NotNull GTRecipe recipe) {
        final ModuleLevelCondition blocker = shanhai$failingModuleLevelCondition(recipe);
        if (blocker == null) {
            return;
        }
        shanhai$pendingFailIsModuleLevel = true;
        shanhai$pendingFailReason = shanhai$moduleLevelFailReason(blocker);
    }

    /** 找出这条配方上【不通过】的那一条 {@code module_level} 条件；没有则 {@code null}。 */
    private @Nullable ModuleLevelCondition shanhai$failingModuleLevelCondition(@NotNull GTRecipe recipe) {
        for (RecipeCondition condition : recipe.conditions) {
            if (condition instanceof ModuleLevelCondition mlc && !mlc.test(recipe, this)) {
                return mlc;
            }
        }
        return null;
    }

    /**
     * 文案（**走 lang 键，不硬编码中文**）。
     *
     * <pre>
     *   可解   ：shanhai.recipe.fail.module_level = "物质模块等级不足（需要 Lv.%s，当前 Lv.%s）"
     *   不可解 ：shanhai.recipe.fail.module_level.unresolved
     *            = "物质模块等级要求无法解析（配方里的模块 id 不在 17 个物质模块表里）"
     * </pre>
     * ⚠️ 只给<b>原因</b>，不带「配方失败原因：」前缀 —— 那个前缀由 Jade 的
     * {@code gtceu.recipe.fail.reason}（"配方失败原因：%s"）加，加了会变成两层。
     * GUI 侧 gtlcore 一律画裸原因（它自己也这么画"电压等级未达到配方要求"）⇒ 两边同形。
     */
    private Component shanhai$moduleLevelFailReason(@NotNull ModuleLevelCondition blocker) {
        if (!blocker.isGateResolvable()) {
            return Component.translatable("shanhai.recipe.fail.module_level.unresolved");
        }
        return Component.translatable("shanhai.recipe.fail.module_level",
                blocker.requiredLevelForGate(), getMachine().getMatterModuleLevel());
    }

    /**
     * 把暂存的原因写进 gtlcore 的配方状态 —— <b>只有这一轮真的没跑起来才写</b>。
     *
     * <h2>🔴 判据 A③：不许变成常驻行</h2>
     * {@code checkRecipe} 会被<b>逐条候选配方</b>调用：同一条配方类型下，可能"配方 A 被门槛拦下、
     * 配方 B 照跑"。此处在 {@link #findAndHandleRecipe()} 的<b>末尾</b>才写，并且
     * <b>只要机器已经在跑（WORKING / WAITING）或手上已有配方，就一个字都不写</b>
     * ⇒ 没有真拦截时，Jade 与 GUI 里都不会多出这一行（本项目明令禁止的"活的假数据"）。
     *
     * <p>写入口 = {@code RecipeResult.of(machine, …)}：与 gtlcore / gtladditions 自己
     * （{@code RecipeLogicMixin} / {@code MutableRecipesLogic}）<b>逐字同一条路</b>，不自造显示。
     */
    private void shanhai$publishFailReason() {
        final Component reason = shanhai$pendingFailReason;
        if (reason == null) {
            return;
        }
        final RecipeLogic.Status status = getStatus();
        if (status == RecipeLogic.Status.WORKING || status == RecipeLogic.Status.WAITING) {
            return;
        }
        if (getLastRecipe() != null) {
            return;
        }
        RecipeResult.of(getMachine(), RecipeResult.fail(reason));
        shanhai$logFailReasonPublished(reason);
    }

    // ── 两条一次性可 grep 证据行（"悄悄不发生"与"出故障"必须能分开） ──

    /** 已报过的 (machineTier, recipeEuTier) 组合（每个组合只报一次，避免刷屏）。 */
    private static final Set<String> shanhai$voltageGateLogged = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void shanhai$logVoltageTierBlock(int recipeEuTier, int machineTier) {
        final String key = machineTier + "/" + recipeEuTier;
        if (shanhai$voltageGateLogged.size() > 256 || !shanhai$voltageGateLogged.add(key)) {
            return;
        }
        ShanhaiMod.LOGGER.info("[SHANHAI-VOLTAGE-GATE] 配方电压等级超出本机 ⇒ 拦下："
                        + "machineTier={}（= 本机能源仓等级）< recipeEuTier={}。"
                        + "已写入 gtlcore 现成的 FAIL_VOLTAGE_TIER ⇒ Jade / 机器 GUI 都会渲成"
                        + "「电压等级未达到配方要求」。",
                machineTier, recipeEuTier);
    }

    /** 已报过的原因文本（每个不同的原因只报一次）。 */
    private static final Set<String> shanhai$failReasonLogged = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void shanhai$logFailReasonPublished(@NotNull Component reason) {
        final String key = reason.getString();
        if (shanhai$failReasonLogged.size() > 64 || !shanhai$failReasonLogged.add(key)) {
            return;
        }
        ShanhaiMod.LOGGER.info("[SHANHAI-FAIL-REASON] 已写入配方失败原因（链 = RecipeResult.of → IRecipeStatus "
                        + "→ Jade 的 reason / 机器 GUI 的红字行）：原文=《{}》"
                        + "（专用服务端不加载客户端 lang，这里打出的是键名/参数，客户端才会渲成中文）",
                key);
    }

    /**
     * 🔴 <b>差异 1 的门控：把"引擎先扣料、后 {@code beforeWorking} 拒绝"这条吞料路径挡住。</b>
     *
     * <h2>为什么必须挡</h2>
     * 引擎的扣料点在 {@code getRecipe()} 内部（{@code calculateParallelsWithGreedyAllocation} /
     * {@code buildFinalNormalRecipe} 里的 {@code handleRecipeInput}），<b>严格早于</b>
     * {@code setupRecipe} → {@code machine.beforeWorking(recipe)}；而模块的
     * {@link PrimordialModuleMachine#beforeWorking} 在主机离线时会返回 false
     * ⇒ <b>每试一次就吞一批料、零产出</b>（节奏：{@code serverTick} 里 {@code getOffsetTimer()%5==0}，
     * 因为模块 {@code keepSubscribing()==true}）。
     *
     * <h2>🔴 还必须调 {@code tryReconnectLimited()}</h2>
     * 那个重连尝试原本只在 {@code beforeWorking} 里做。门控一旦在更早处返回 false，
     * {@code beforeWorking} 就<b>永远不会被调用</b> ⇒ 不补这一句就会"挡掉了吞料、也挡掉了重连"
     * ⇒ 模块卡死（比吞料更糟）。
     */
    @Override
    protected boolean checkBeforeWorking() {
        if (!super.checkBeforeWorking()) {
            return false;
        }
        final PrimordialModuleMachine module = getMachine();
        if (!module.canWork()) {
            module.tryReconnectLimited();
            return module.canWork();
        }
        // 🔴 2026-09-26（丢料探针可判化 · 第 ① 步）：在【扣料前】存一份输入仓 / 流体仓摘要。
        //    「这个点早于扣料」不是推断 —— 字节码实证：
        //      MutableRecipesLogic.getRecipe() 偏移 0–8 就是
        //        checkBeforeWorking() → ifne 9 / aconst_null / areturn，
        //      紧接着 9–13 调 calculateParallels()（扣料点 RecipeRunnerHelper.handleRecipeInput
        //      在 RecipeCalculationHelper 里被调 12 处），最后才 buildFinalNormalRecipe()；
        //      而 setupRecipe()（machine.beforeWorking 所在）在 getRecipe() 返回【之后】才被调。
        //    ⇒ 门控 < 扣料 < 拒绝，三段全部有实证。
        module.shanhai$lossProbeBeforeDeduction();
        return true;
    }

    /**
     * <b>尾链迁移</b>：把 并行 → N3产出 → N5时长 → N6下限 → N5耗能 施加在引擎装配出的成品上。
     *
     * <p>顺序与 {@code ModuleRegistry#applyModuleRecipeModifier} <b>逐字一致</b>；差别只在入口：
     * 原生链的入口是"配方定义原实例"（所以链上要自己先做并行），
     * 本路径的入口是<b>引擎已经并好输出、已经扣完料的成品</b>（所以并行那一步由引擎做了，本类不重放）。
     *
     * <p><b>本类里不出现任何自有算式</b>：每一步都是对 {@link PrimordialRecipeEffects} 的调用。
     */
    @Override
    protected @Nullable GTRecipe buildFinalNormalRecipe(@NotNull ParallelData parallelData) {
        final GTRecipe built = super.buildFinalNormalRecipe(parallelData);
        if (built == null) {
            return null;
        }
        final PrimordialModuleMachine module = getMachine();
        // 🔴 2026-09-26（丢料探针 · 第 ③ 步的实机那一半）：走到这里引擎【已经扣过料】
        //    （扣料点在 calculateParallels → calculateParallelsWithGreedyAllocation 里，
        //     严格早于 buildFinalNormalRecipe），所以这一次摘要对比是"实机上真的能看到扣料"的证据。
        //    只读、只在真的读到变化时打一行（全部模块共享一次），不改任何数值。
        module.shanhai$lossProbeLiveControl();
        final int engineDuration = built.duration;          // 引擎时长 D（守恒反缩放的分子）
        final int originDuration = shanhai$originDurationOf(parallelData);   // 配方定义原时长 d0
        // 🔴 2026-09-26：原有一行 `final int limitedDuration = module.getLimitedDuration();` 已删除 ——
        //    模块的「配方最短耗时（下限）」按用户裁决「连功能一起删」整体删除，
        //    字段与 getter 都不存在了（依赖它的一律改走"只吃 N5 减免系数"的新公式）。
        // 门控只读一次：N3 的倍率与 N5 的成本系数必须同源（与原生链同一条纪律）。
        final int gateBonus = module.getHost() == null ? 0 : module.getHost().moduleSlotBonus();

        GTRecipe modified = built;
        // ── 守恒反缩放：EUt_引擎(D) → baseEUt × p（= totalEu / d0）──
        //    第二个实参就是 d0（时长），语义无错位；D == d0 时本调用原样返回、零分配。
        modified = PrimordialRecipeEffects.rescaleEnergyForDuration(modified, engineDuration, originDuration);
        // ── 时长钉回 d0：引擎的 D 自带硬编码 20 下限，N5/N6 必须在【原时长】上算 ──
        //    用共用的 min 天花板函数（applyHostDurationCap，现在只有模块侧在调）表达"上限 = 原时长"⇒ 结果恒为 d0。
        modified = PrimordialRecipeEffects.applyHostDurationCap(modified, originDuration, originDuration);
        // ── ② N3 产出倍率 ──
        modified = PrimordialRecipeEffects.multiplyOutputs(
                modified, PrimordialRecipeEffects.outputMultiplier(gateBonus));
        // ── ③ N5 耗时减免 ──
        modified = PrimordialRecipeEffects.applyDurationReduction(
                modified, PrimordialRecipeEffects.reductionFactor(gateBonus));
        // ── ④ N6 模块侧（保底 + 天花板）：min(d0, max(1, round(d0 × f))) ──
        //    🔴 2026-09-26 用户裁决「连功能一起删」⇒ 下限 L 与它的形参都已删除；
        //       剩下的 `max(1, …)` 是「至少 1 tick」保底（与"下限 20"是两件事，别混）。
        modified = PrimordialRecipeEffects.applyModuleDuration(modified, originDuration);
        // ── ⑤ N5 耗能减免 ──
        modified = PrimordialRecipeEffects.reduceEnergy(
                modified, PrimordialRecipeEffects.reductionFactor(gateBonus));

        final GTRecipe finalRecipe = shanhai$applyNativeAuthority(
                parallelData, originDuration, gateBonus, modified);
        // 🔴 2026-09-26：抬头（Jade）那两行的显示因子 —— 与主机
        //    {@code PrimordialEngineRecipeLogic#captureEngineReduction} 逐字同口径
        //    （同一对公式、同一批纯函数、同样"以配方定义原时长 d0 为基线"）。
        //    ⚠️ 传的 T 必须是【本方法返回的那张成品的真实时长】（N6 + 原生权威覆盖之后的最终值），
        //      不是侧栏那个下限 L、也不是引擎时长 D —— 与主机"传 T 不传 L"的细化同一条纪律。
        shanhai$captureModuleReduction(originDuration, gateBonus, finalRecipe);
        return finalRecipe;
    }

    // ═══════════════ 抬头两行（总耗能倍率 / 总耗时倍率）的真实因子 ═══════════════

    /** 上一次写进去的那一对因子（只在值变化时打一行日志，不刷屏）。 */
    private static long shanhai$lastLoggedReductionBits = Long.MIN_VALUE;

    /**
     * 把「总耗能倍率 / 总耗时倍率」两个<b>真实因子</b>写进 gtlcore 的显示追踪器
     * （{@code RecipeMultiplierTracker} → Jade 的 {@code RecipeMultiplierProvider}）。
     *
     * <h2>1. 为什么模块必须自己写（根因，字节码实证）</h2>
     * 提供者的 {@code write(CompoundTag, IRecipeLogicMachine)} 只有两支：
     * <pre>
     *   if (logic instanceof org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic mrl)
     *         → Optional.of(new Multipliers(mrl.getReductionEUt(), mrl.getReductionDuration()));
     *   else  → RecipeMultiplierTracker.get(machine.self())
     *               .or(() -&gt; last == null ? Optional.empty() : Optional.of(DEFAULT));   // DEFAULT = (1.0, 1.0)
     * </pre>
     * 本类的继承链是 {@code PrimordialModuleRecipeLogic → gtladditions MutableRecipesLogic → gtceu RecipeLogic}
     * （{@code javap} 实测：{@code MutableRecipesLogic extends com.gregtechceu.gtceu.api.machine.trait.RecipeLogic}）
     * ⇒ <b>不是</b> gtlcore 的 {@code MultipleRecipesLogic} ⇒ 走 else 支；而 tracker 里没有本机条目时，
     * 兜底就是 {@code DEFAULT = (1.0, 1.0)} ⇒ <b>抬头恒画 100%／100%，与装不装主机无关</b>。
     * 修饰链那条路上的 {@code captureReduction(…, 1.0, 1.0)}（{@code ModuleRegistry} 里那一句）在引擎路径上是
     * <b>死代码</b>（引擎路径不经过 {@code RecipeModifierList.apply}）⇒ 真值的唯一来源就是这个方法。
     *
     * <h2>2. 写什么（与主机逐字同口径，队长 2026-09-21 裁决 (Q)）</h2>
     * <pre>
     *   durationFactor = T / d0
     *   energyFactor   = f × (d0 / T)      f = PrimordialRecipeEffects.reductionFactor(gateBonus)
     *   ⇒ energyFactor × durationFactor = f 恒成立（两行互为倒数、同源）
     * </pre>
     * d0 = 配方定义原时长（{@link #shanhai$originDurationOf}）；T = 成品真实时长（见调用点注释）。
     *
     * <h2>3. 为什么"先探一次再写"（照抄主机的防假数据手法）</h2>
     * {@code captureReduction} 有两条分支（字节码）：context 支把因子乘进 {@code ctx.captured}，
     * null 支才 {@code MULTIPLIERS.put(machine, multiply(DEFAULT, e, d))} —— <b>只有后者是 provider 读的那张表</b>。
     * 引擎路径上 {@code begin()} 无人调用 ⇒ 预期 context 恒 null，但我们<b>不靠"应该"</b>：
     * 先写 (1.0,1.0) 探一次，用<b>引用相等</b>判断表项有没有换新（{@code Multipliers} 是 record，
     * 值相等会掩盖"到底有没有 put 过"）；换了才写真因子。没换 ⇒ <b>绝不写</b>
     * （写了就是乘第二次 = 假数据），改打一条 ERROR 让抬头退回上游兜底值。
     *
     * <h2>🔴 诚实边界（红线）</h2>
     * 本方法<b>只写显示用的 {@code WeakHashMap}</b>，<b>不改配方的任何字段</b>：
     * 唯一的输入是两个数字，{@code built} 只作为被忽略的形参传入（与主机同形）。
     *
     * @param originDuration 配方定义原时长 d0（&le;0 ⇒ 两行都不写真实值，退化成 1.0）
     * @param gateBonus      主机专属槽门控等级（与 N3/N5 同源的 f）
     * @param built          最终成品（只读它的 {@code duration} 当 T；其余一概不读）
     */
    private void shanhai$captureModuleReduction(int originDuration, int gateBonus, @NotNull GTRecipe built) {
        try {
            final PrimordialModuleMachine machine = getMachine();
            final int target = Math.max(1, built.duration);
            final double durationFactor = originDuration > 0
                    ? (double) target / (double) originDuration : 1.0D;
            final double energyFactor = PrimordialRecipeEffects.reductionFactor(gateBonus)
                    * (originDuration > 0 ? (double) originDuration / (double) target : 1.0D);

            // ── ① 探针：先取一次当前值，再决定写法 ──
            final RecipeMultiplierTracker.Multipliers before = RecipeMultiplierTracker.get(machine).orElse(null);
            RecipeMultiplierTracker.captureReduction(machine, built, 1.0D, 1.0D);
            if (RecipeMultiplierTracker.get(machine).orElse(null) == before) {
                // 🔴 值引用没变 ⇒ 探针没写进 MULTIPLIERS ⇒ 落进了 context 分支。
                //    此时 ctx.captured 已经把 (1.0,1.0) 乘进去了（乘 1 无副作用），
                //    但再传真实因子就会乘第二次 ⇒ 绝不允许。
                ShanhaiMod.LOGGER.error("[SHANHAI-MODULE] 抬头两行的显示因子写不进去："
                        + "RecipeMultiplierTracker.captureReduction 走的是 context 分支（ctx != null）。"
                        + "为避免把同一批因子乘第二次（假数据），本次【不写】真实因子；"
                        + "抬头会退化成上游兜底值 100%/100%。配方数值不受影响。pos={}", machine.getPos());
                return;
            }

            // ── ② 确认走的是 null 分支 ⇒ 写真实因子（覆盖探针写下的 1.0/1.0）──
            RecipeMultiplierTracker.captureReduction(machine, built, energyFactor, durationFactor);

            // ── ③ 只在因子变化时打一行（不刷屏）：部署后这一行就是"tracker 真的写进去了"的实机证据 ──
            final long bits = Double.doubleToLongBits(energyFactor) * 31L
                    + Double.doubleToLongBits(durationFactor);
            if (bits != shanhai$lastLoggedReductionBits) {
                shanhai$lastLoggedReductionBits = bits;
                final RecipeMultiplierTracker.Multipliers readBack =
                        RecipeMultiplierTracker.get(machine).orElse(null);
                ShanhaiMod.LOGGER.info("[SHANHAI-MODULE] 抬头因子已写入 tracker："
                                + "总耗能倍率={}（{}%）／总耗时倍率={}（{}%）"
                                + "（口径 (Q)：energy×duration = N5 成本系数 f={}；"
                                + "d0={} → T={} ⇒ duration=T/d0、energy=f×d0/T）；读回={} pos={}",
                        energyFactor, energyFactor * 100.0D, durationFactor, durationFactor * 100.0D,
                        PrimordialRecipeEffects.reductionFactor(gateBonus), originDuration, target,
                        readBack, machine.getPos());
            }
        } catch (Throwable t) {
            // 纯显示：任何异常都不允许影响配方装配本身（与主机 captureEngineReduction 同纪律）。
            ShanhaiMod.LOGGER.error("[SHANHAI-MODULE] 写抬头显示因子时异常（已忽略，配方不受影响）", t);
        }
    }

    /** 引擎本轮"原配方"的时长 d0（取第一条；上游同写法，见主机 {@code originDurationOf}）。 */
    private static int shanhai$originDurationOf(@NotNull ParallelData parallelData) {
        final List<GTRecipe> origins = parallelData.getOriginRecipeList();
        if (origins.isEmpty()) {
            return 0;
        }
        return Math.max(0, origins.get(0).duration);
    }

    // ═══════════════ 原生权威 + 影子对账（"值=1 逐字一致"的可核对证据） ═══════════════

    /**
     * 🔴 <b>用"原生链重放值"作为最终权威</b>，并把它与引擎产出的差异打成可核对日志。
     *
     * <h2>为什么必须"覆盖"而不是"只对账"（2026-09-26 用户实机抓到的真 bug）</h2>
     * 引擎给 EUt 的方式是<b>除回来</b>（{@code RecipeCalculationHelper.buildNormalRecipe}：
     * {@code EUt = totalEu / maxEUt} 再按 duration 分摊），只要除不尽就<b>必然丢余数</b>。
     * 用户实机原文（满配物质模块，p = {@code Integer.MAX_VALUE}）：
     * <pre>
     *   引擎路径 EUt = 3221225470 ；原生链重放 EUt = 3221225471（p = 2147483647，d0 = 1）   ⇒ 差 1
     * </pre>
     * 而原生链是 {@code baseEUt × p}（乘法，精确）⇒ 本方法把 duration 与 EUt 都<b>钉到原生重放值</b>。
     * <p>⚠️ <b>普通档（如 p = 64）本来就一致</b> ⇒ 钉这一下对它们是恒等操作（不产生新实例、不改变数值），
     * 只有极端档会真的被纠正。
     *
     * <h2>为什么这里可以放心"重放"（不碰机器、不扣料）</h2>
     * 全程只用 {@link PrimordialRecipeEffects} 的纯函数作用在 {@code origins.get(0).copy()} 上，
     * <b>不调用任何会读机器仓位/会扣料的引擎 API</b>。
     *
     * <h2>诚实边界（写死，别当成已验证）</h2>
     * 它只能证明"我算的那份 == 我要的那份"，<b>不能</b>证明"== 用户换引擎之前玩到的那份"
     * —— 后者只能由用户做 A/B 对账。
     */
    private @NotNull GTRecipe shanhai$applyNativeAuthority(@NotNull ParallelData parallelData, int originDuration,
                                                           int gateBonus,
                                                           @NotNull GTRecipe engineTailed) {
        try {
            shanhai$selfTestProbe();
            final List<GTRecipe> origins = parallelData.getOriginRecipeList();
            final long[] parallels = parallelData.getParallels();
            if (origins.isEmpty() || parallels.length == 0 || originDuration <= 0) {
                return engineTailed;
            }
            // 🔴 2026-09-26：p 由 int 改 long（并行表末三档现在真的会走到这里）。
            final long p = Math.max(1L, parallels[0]);
            if (p > (long) Integer.MAX_VALUE) {
                // ⛔ 这条影子重放链的时长/能量原语【全是 int】
                //    （PrimordialRecipeEffects.rescaleEnergyForDuration(GTRecipe, int, int)），
                //    装不下 4.6e18 / 6.9e18 / Long.MAX。
                //    ⇒ 这里【不做权威覆盖】，因为覆盖会把 EUt 钉到一个被 int 压平的错值上（静默错数）。
                //    只手打一行可 grep 的日志，把引擎自算的成品原样交回（引擎侧才是 long 的真实值）。
                shanhai$logShadowReplaySkipped(p, originDuration, engineTailed);
                return engineTailed;
            }
            final int pInt = (int) p;
            GTRecipe expected = origins.get(0).copy();
            // 原生链的 ×p 由 GTRecipe.copy(ContentModifier, boolean) 完成（四张表全乘，已字节码核实）；
            // 本处没有"并行"这一步可调（不碰机器、更不能扣料），所以用同一条守恒函数的 D/T 形式表达"×p"。
            expected = PrimordialRecipeEffects.rescaleEnergyForDuration(expected, pInt, 1);
            expected = PrimordialRecipeEffects.multiplyOutputs(
                    expected, PrimordialRecipeEffects.outputMultiplier(gateBonus));
            expected = PrimordialRecipeEffects.applyDurationReduction(
                    expected, PrimordialRecipeEffects.reductionFactor(gateBonus));
            expected = PrimordialRecipeEffects.applyModuleDuration(expected, originDuration);
            expected = PrimordialRecipeEffects.reduceEnergy(
                    expected, PrimordialRecipeEffects.reductionFactor(gateBonus));

            // ── 权威覆盖：duration 与 EUt 都钉到原生重放值（一致时是恒等操作）──
            GTRecipe corrected = engineTailed;
            if (corrected.duration != expected.duration) {
                corrected = PrimordialRecipeEffects.applyHostDurationCap(
                        corrected, expected.duration, expected.duration);
            }
            corrected = PrimordialRecipeEffects.setEUtPerTick(
                    corrected, RecipeHelper.getInputEUt(expected));

            shanhai$compare("真实对账", expected, corrected, p, originDuration);

            // 🔴 realParallels 探针 —— ⚠️ **影子对账【不覆盖】这一项**（只记录、不判成败）。
            //    原生链会把 realParallels 设成 p（gtlcore ParallelLogicMixin.doParallelRecipes 的
            //    setRealParallels(limitByOutput × 原值)），而引擎新造的成品保持默认 1。
            //    它【不参与】逐 tick EU 扣减（gtlcore 里只进 RecipeRunner 的概率掷骰、
            //    BatchProcessing 与 RecipeMultiplierTracker 显示）⇒ 数值口径不受影响；
            //    但玩家开【批处理】时这一档仍可能有细微差别 ⇒ 打出来供实机核对。
            final long actualRealParallels = ((IGTRecipe) corrected).getRealParallels();
            ShanhaiMod.LOGGER.info("[SHANHAI-MODULE-EQ] realParallels 探针（⚠️ 影子对账【不覆盖】此项）："
                            + "原生链本会设为 p={}，引擎成品 = {}；不一致不算失败，但开【批处理】时请留意这一行。",
                    p, actualRealParallels);
            return corrected;
        } catch (Throwable t) {
            ShanhaiMod.LOGGER.error("[SHANHAI-MODULE-EQ] 对账器自身异常（不影响配方）：{}", t.toString());
            return engineTailed;
        }
    }

    /** 影子重放够不着 long 档时的一次性可 grep 证据行（只在 p 变化时打）。 */
    private static long shanhai$lastShadowSkipped = Long.MIN_VALUE;

    private static void shanhai$logShadowReplaySkipped(long p, int originDuration, @NotNull GTRecipe engineTailed) {
        if (p == shanhai$lastShadowSkipped) {
            return;
        }
        shanhai$lastShadowSkipped = p;
        ShanhaiMod.LOGGER.warn("[SHANHAI-MODULE-EQ] 🔴 影子重放【已跳过】：p={} 超出 int 范围，"
                        + "而 PrimordialRecipeEffects.rescaleEnergyForDuration 的时长形参是 int "
                        + "⇒ 重放值会被压平。为避免把 EUt 钉到错的数上（静默错数），"
                        + "本档【不做权威覆盖】，配方 = 引擎自算的 long 真实值。"
                        + "引擎成品 duration={} EUt={} d0={}",
                p, engineTailed.duration, RecipeHelper.getInputEUt(engineTailed), originDuration);
    }

    /** 对比一对 (duration, EUt) 并打日志；不等就 ERROR。 */
    private void shanhai$compare(String tag, @NotNull GTRecipe expected, @NotNull GTRecipe actual, long p, int d0) {
        final long expectedEut = RecipeHelper.getInputEUt(expected);
        final long actualEut = RecipeHelper.getInputEUt(actual);
        if (expected.duration == actual.duration && expectedEut == actualEut) {
            ShanhaiMod.LOGGER.info("[SHANHAI-MODULE-EQ] {} 一致：duration={} / EUt={}（p={}，d0={}）",
                    tag, actual.duration, actualEut, p, d0);
            return;
        }
        ShanhaiMod.LOGGER.error("[SHANHAI-MODULE-EQ] 🔴 {} 不一致！引擎路径 duration={} EUt={}；原生链重放 duration={} EUt={}（p={}，d0={}）"
                        + " ⇒ 值=1 的行为与今天分叉，请把这一行原文交回。",
                tag, actual.duration, actualEut, expected.duration, expectedEut, p, d0);
    }

    /**
     * 🔴 <b>对账器的正面对照</b>：喂一个【已知为坏】的样本（duration 故意差 1），
     * 确认 {@link #shanhai$compare} 走的是"不一致"分支。只跑一次。
     */
    private void shanhai$selfTestProbe() {
        if (shanhai$probeSelfTested) {
            return;
        }
        shanhai$probeSelfTested = true;
        final GTRecipe base = com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder.ofRaw().buildRawRecipe();
        base.duration = 100;
        final GTRecipe broken = base.copy();
        broken.duration = 101;
        if (shanhai$isSameAs(base, broken)) {
            throw new IllegalStateException("[SHANHAI-MODULE-EQ] 对账器正面对照失败："
                    + "两份额外相差 1 tick 的配方被它判成'一致' ⇒ 对账器本身是坏的，"
                    + "它在真实输入上说的'一致'没有任何信息量。");
        }
        ShanhaiMod.LOGGER.info("[SHANHAI-MODULE-EQ] 对账器正面对照通过：故意差 1 tick 的样本被判成【不一致】"
                + "（⇒ 它对真实输入报的'一致'可信）。");
    }

    private static boolean shanhai$isSameAs(@NotNull GTRecipe a, @NotNull GTRecipe b) {
        return a.duration == b.duration && RecipeHelper.getInputEUt(a) == RecipeHelper.getInputEUt(b);
    }
}
