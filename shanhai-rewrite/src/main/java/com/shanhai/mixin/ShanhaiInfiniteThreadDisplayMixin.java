package com.shanhai.mixin;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.integration.jade.provider.ParallelProvider;
import com.shanhai.ShanhaiMod;
import com.shanhai.common.machine.PrimordialOmegaEngineMachine;
import com.shanhai.machine.module.PrimordialModuleMachine;

import net.minecraft.nbt.CompoundTag;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import snownee.jade.api.BlockAccessor;

/**
 * 山海重构 · 让抬头（Jade）显示「并行」与「跨配方线程」两行 —— <b>主机 + 24 台原初模块</b>。
 *
 * <p>← 2026-09-25（任务 A）起新增模块那一半，见本文件末尾的
 * {@link #shanhai$moduleDisplay(CompoundTag, MetaMachineBlockEntity)}。
 *
 * <h2>它做什么（主机那一路）</h2>
 * 在 {@code ParallelProvider.appendServerData(...)} 的 RETURN 处，如果本次查询的方块是<b>本工程的主机</b>
 * 且已成型，就往 Jade 的服务端数据 {@link CompoundTag} 里写
 * {@code parallel = -114514L} 与 {@code threads = -114514L}（两个哨兵）。
 * <b>不改任何机器行为，只改两个用于显示的 NBT 键。</b>
 *
 * <h2>🔴 2026-09-21 二次改判（T3）：为什么现在<b>必须</b>也写 {@code parallel}</h2>
 * <b>旧版本类只写 {@code threads}</b>，理由是当时「并行 = 无限」那个哨兵由
 * {@code PrimordialOmegaEngineMachine.getMaxParallel()} 返回的负数承担
 * （gtladditions 的 {@code ParallelProviderMixin} 会把
 * {@code ((ParallelMachine) machine).getMaxParallel()} 原样写进 {@code parallel} 键）。
 * <b>那个设计已经作废，因为它会静默停机</b>：
 * <pre>
 *   换多配方引擎后，{@code getMaxParallel()} 被真的乘进并行数：
 *       MutableRecipesLogic.calculateParallels():  totalParallel = (long) getMaxParallel() * getMultipleThreads()
 *   ⇒ -114514 × 1073741824 ≈ -1.23e14（负数、无饱和）
 *   ⇒ 贪心分配开头 if (remain &lt;= 0L) break; ⇒ 返回 null ⇒ getRecipe() 恒为 null
 *   ⇒ 机器【不动、不崩、日志无任何输出】——最坏的静默失败形态。
 * </pre>
 * ⇒ 现在 {@code getMaxParallel()} 返回正数（{@code MAX_PARALLEL} = 2^30），
 * 「无限」<b>只由本类写进显示用的 NBT</b>：<b>显示归显示、运算归运算，两者不再共用同一个数。</b>
 * <p>⚠️ 这一条也意味着：<b>若本 mixin 失效（{@code require = 0} 在目标方法缺失时不报错），
 * 症状是"抬头那两行变成很大的数字（1073741824）"而不是机器坏掉</b> —— 数值行为<b>不受影响</b>。
 * 这是刻意的取舍：显示可以退化，机器不可以。
 *
 * <h2>为什么是 -114514L（而不是自己画彩虹）</h2>
 * 这个哨兵是 <b>gtladditions 自己约定的「无限」标记</b>，证据（[源码原文]，gtladditions 3.2.8）：
 * <pre>
 * com.gtladd.gtladditions.mixin.gtceu.integration.ParallelProviderMixin:42
 *     private static final long INFINITY_FEATURE = -114514L;
 * 同文件 appendTooltip(...)：
 *     if (data.contains("parallel")) {
 *         long parallel = data.getLong("parallel");
 *         if (parallel &gt; 0L) …深紫数字…
 *         else if (parallel == -114514L)
 *             tooltip.add(Component.translatable("gtceu.multiblock.parallel",
 *                     CommonUtils.createLanguageRainbowComponent(
 *                             Component.translatable("gtladditions.multiblock.forge_of_the_antichrist.parallel"))));
 *     }
 *     if (data.contains("threads")) {
 *         int threads = data.getInt("threads");
 *         if (threads &gt; 0) …金色数字…
 *         else if (threads == -114514L)
 *             tooltip.add(Component.translatable("gtladditions.multiblock.threads",
 *                     CommonUtils.createLanguageRainbowComponent(
 *                             Component.translatable("gtladditions.multiblock.forge_of_the_antichrist.parallel"))));
 *     }
 * </pre>
 * 而 {@code gtladditions.multiblock.threads} = {@code 拥有%s个跨配方线程}、
 * {@code gtladditions.multiblock.forge_of_the_antichrist.parallel} = {@code 无限}
 * （[源码原文] {@code assets/gtladditions/lang/zh_cn.json}）。
 * <b>⇒ 我们只要写这两个哨兵，gtladditions 自己的渲染就会产出与「伪神（FOTC）」逐字一致的彩虹「无限」两行</b>，
 * 不需要本工程再抄一份渐变文字实现（少一份会漂移的重复实现）。
 * <p>注意 {@code threads} 那一侧上游是按 {@code int} 读的（{@code data.getInt}），
 * 而哨兵按 {@code long} 写；{@code -114514} 在 int 范围内，{@code getInt} 读回来仍是 {@code -114514}
 * ⇒ 判等成立（上游对 {@code ForgeOfTheAntichrist} 也是这么写的）。
 *
 * <h2>与 gtladditions 的 @Overwrite 共存（这是本 mixin 唯一的脆弱点，明写在此）</h2>
 * gtladditions 的 {@code ParallelProviderMixin} 用 {@code @Overwrite} <b>整体替换</b>了
 * {@code appendServerData} 与 {@code appendTooltip}。本类的 {@code @Inject(RETURN)}
 * <b>落在被覆盖后的方法体上</b>并覆盖它写下的值。
 *
 * <h3>⛔ 2026-09-26：上面那句"为什么成立"的<b>旧依据已作废</b>（原文照留）</h3>
 * <pre>
 *   ⛔ 旧原文（作废）：
 *      Mixin 的规则是 priority 数值小的后应用（默认 1000）⇒ 本类写 priority = 500，于是 …
 *      依据：旧私货 {@code ParallelProviderOverrideMixin}（同样的 priority = 500 + @Inject(RETURN)）
 *      在本环境已跑通过。
 * </pre>
 * 🔴 <b>作废原因（2026-09-26 用 Mixin 0.8.5 的字节码逐条核实）</b>：
 * <pre>
 *   · {@code MixinInfo.compareTo}（mixin-0.8.5.jar 字节码 :27-36）是 {@code this.priority - other.priority}
 *     ⇒ <b>升序</b>；{@code MixinProcessor} 把同一目标类的 mixin 收进 {@code TreeSet}
 *     （字节码 :481-498）交给 {@code TargetClassContext}，{@code MixinApplicatorStandard.apply}
 *     按该 {@code SortedSet} 的顺序处理 ⇒ priority <b>小</b>者<b>先</b>处理。
 *   · 真正决定成败的不是 priority，而是 <b>pass 顺序</b>：
 *     {@code MixinApplicatorStandard$ApplicatorPass} 的枚举顺序 = <b>MAIN(0) → PREINJECT(1) → INJECT(2)</b>
 *     （字节码 {@code <clinit>}）⇒ {@code @Overwrite} 在 MAIN pass 落地、{@code @Inject} 在 INJECT pass 落地
 *     ⇒ <b>@Inject 一定作用在 @Overwrite 之后的函数体上，与 priority 谁大谁小无关</b>。
 * </pre>
 * ⇒ <b>结论不变（我们的 @Inject 会覆盖上游写入的值），但依据从"priority 小者后应用"换成"pass 顺序"。</b>
 * {@code priority = 500} 保留无害（它在本场景里不起作用），但<b>不要再把它当作理由</b>。
 * <p>且上游对 {@code MutableRecipesLogic} 那一支的原文是
 * {@code if (mutableRecipesLogic.isMultipleRecipeMode() && mutableRecipesLogic.getMultipleThreads() > 1)
 *      compoundTag.putLong("threads", mutableRecipesLogic.getMultipleThreads());}
 * —— 我们的覆盖正落在它<b>之后</b>。
 *
 * <h2>为什么 {@code require = 0}</h2>
 * 这是一条<b>纯显示</b>的增强：目标方法找不到时（例如 gtladditions 换了实现方式）
 * 只应该「少一行」，<b>绝不允许把用户的客户端崩掉</b>。
 * 为了不让它变成「静默失败」，第一次真正写入时会打一条 INFO 日志
 * （字面量 {@code [SHANHAI-DISPLAY]}），谁都能在 {@code latest.log} 里 grep 到。
 * <p>⚠️ 注意：{@code require = 0} 意味着<b>专服冒烟证明不了这一行会出现</b> —— 它只能在用户进游戏后验证。
 */
@Mixin(value = ParallelProvider.class, priority = 500, remap = false)
public class ShanhaiInfiniteThreadDisplayMixin {

    /**
     * gtladditions 约定的「无限」哨兵。
     *
     * <p>🔴 取值<b>直接引用</b> {@link PrimordialOmegaEngineMachine#DISPLAY_INFINITE_PARALLEL}
     * 而不是在本类里再写一遍字面量 —— 全工程只留<b>一处</b>定义，两处各自硬编码必然漂移。
     * 那个常量已经是 {@code public static final int}（编译期常量），
     * 引用它不会给本 mixin 增加任何运行期类加载负担。
     */
    @Unique
    private static final long SHANHAI$INFINITE = PrimordialOmegaEngineMachine.DISPLAY_INFINITE_PARALLEL;

    /** 只打一次，避免 Jade 每次刷新都刷屏。 */
    @Unique
    private static boolean shanhai$displayAnnounced = false;

    /**
     * @param data     Jade 的服务端数据 tag（会被发送给客户端用于画 tooltip）
     * @param accessor 本次查询的方块访问器
     */
    @Inject(
            method = "appendServerData(Lnet/minecraft/nbt/CompoundTag;Lsnownee/jade/api/BlockAccessor;)V",
            at = @At("RETURN"),
            remap = false,
            require = 0)
    private void shanhai$infiniteThreads(CompoundTag data, BlockAccessor accessor, CallbackInfo ci) {
        try {
            if (data == null || accessor == null) {
                return;
            }
            if (!(accessor.getBlockEntity() instanceof MetaMachineBlockEntity mbe)) {
                return;
            }
            // 🔴 2026-09-25（任务 A）：模块那一支**先**走。这是**纯新增的两行** ——
            //    下面的主机分支一字未动；模块在这里写完就 return，不会落到主机分支
            //    （PrimordialModuleMachine 不是 PrimordialOmegaEngineMachine）。
            shanhai$moduleDisplay(data, mbe);
            if (!(mbe.getMetaMachine() instanceof PrimordialOmegaEngineMachine machine)) {
                return;
            }
            // 只对成型的主机显示；未成型时 gtladditions 本来也不会写 parallel/threads。
            if (!machine.isFormed()) {
                return;
            }

            // ⛔⛔ 【2026-09-22 作废，原文留档】哨兵 → 真实数值（用户裁决「显示真实的数值」）
            //   ⛔ 旧原文（作废）：
            //       // 🔴 两个键一起写：并行与线程都显示「无限」。
            //       //    它们【只进 Jade 的 NBT】，不参与任何运算（数值侧见 …#getMaxParallel）。
            //       data.putLong("parallel", SHANHAI$INFINITE);
            //       data.putLong("threads", SHANHAI$INFINITE);
            //   ⛔ 作废原因：**用户 2026-09-22 裁决「让我们的主机的并行、线程都按照伪神的来填写，
            //      然后显示真实的数值」** ⇒ 不再显示彩虹「无限」，改为**真数**
            //      （预期 parallel = 2147483647、threads = 128）。
            //      ⇒ 同时解掉了本工程「**活的界面上不许放假数据**」那条红线
            //      （数值侧已改成 MAX_VALUE / 128，仍显示"无限"就是活的假数据）。
            //   ✅ 上游渲染判据（已读原文）：`ParallelProviderMixin.appendTooltip` 里
            //      `parallel > 0L` → 深紫数字、`threads > 0` → 金色数字
            //      ⇒ **写正数就会画成真数**，不需要本工程另画一份。
            final int parallel = machine.getMaxParallel();
            int threads = 0;
            if (machine.getRecipeLogic() instanceof com.shanhai.common.machine.PrimordialEngineRecipeLogic logic) {
                threads = logic.getMultipleThreads();
            }
            data.putLong("parallel", parallel);
            data.putLong("threads", threads);

            if (!shanhai$displayAnnounced) {
                shanhai$displayAnnounced = true;
                ShanhaiMod.LOGGER.info("[SHANHAI-DISPLAY] 已写入 Jade 真值：parallel={} threads={}"
                        + "（用户 2026-09-22 裁决：显示真实数值；哨兵 {} 已作废，不再写）",
                        parallel, threads, SHANHAI$INFINITE);
            }
        } catch (Throwable ignored) {
            // 纯显示路径：任何异常都不允许影响游戏
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    //  2026-09-25（任务 A）：把同样两行也画到【24 台原初模块】的抬头上
    // ═══════════════════════════════════════════════════════════════════════════════════════

    /**
     * 模块专用的抬头上报（<b>只写 Jade 的服务端 NBT，不参与任何运算</b>）。
     *
     * <h2>用户原话（逐字）</h2>
     * 「还有一件事，可以让模块图中这个地方（图1）显示并行和跨配方线程（以后我会增加），<b>就像主机一样</b>」
     *
     * <h2>「就像主机一样」= 走同一条通道</h2>
     * 主机那两行的实现就是本 mixin 的上面那一段：往 GTCEu {@code ParallelProvider} 的
     * {@code parallel} / {@code threads} 两个键里写值，再由 gtladditions 的
     * {@code ParallelProviderMixin#appendTooltip} 画成
     * 「同时处理至多%d个配方」（深紫）与「拥有%s个跨配方线程」（金色）。
     * ⇒ 模块只要写<b>同样两个键</b>，就会得到<b>与主机逐字同款的两行</b>，
     * 不需要本工程再实现一套渲染。
     *
     * <h2>🔴 为什么原来模块那两行是空的（已读上游原文，附行号）</h2>
     * gtladditions {@code ParallelProviderMixin.appendServerData}（
     * {@code originals/analysis/decomp/limitsrc/com/gtladd/gtladditions/mixin/gtceu/integration/ParallelProviderMixin.java}
     * :85-141）对「成型的 {@code WorkableMultiblockMachine}」按类型分支：
     * {@code GTLAddWorkableElectricMultipleRecipesMachine} → 写；
     * {@code MultipleRecipesLogic} → 写；{@code MutableRecipesLogic} → 写；
     * {@code ParallelMachine} → 写；{@code ConversationMachine} / {@code BasicOreProcessorMachine} → 写；
     * <b>其余一律走 :138-141 的兜底分支 —— 只有并行仓（{@code getParallelHatch()}）才写值</b>。
     * 而本工程的模块：不是那几类机器、配方逻辑是普通 {@code RecipeLogic}、
     * <b>而且刻意不实现 {@code ParallelMachine}</b>（理由见
     * {@code PrimordialModuleMachine.LimitedDurationAdapter} 的注释），
     * 也没有并行仓 ⇒ <b>兜底分支什么都不写 ⇒ 抬头上原本一行都没有</b>。
     *
     * <h2>取值（不许编）</h2>
     * <ul>
     *   <li>{@code parallel} = {@link PrimordialModuleMachine#getJadeParallel()}：
     *       <b>每台自己的真实并行槽值</b>（2026-09-25 路线 ① 之后 24 台都有并行槽了）——
     *       空槽 = {@code PrimordialModuleMachine.DEFAULT_PARALLEL} = 64，放入物质模块按该台自己的表提升。
     *       🔴 <b>2026-09-26：不再钳到 {@code Integer.MAX_VALUE}</b>，直接写真 long
     *       （4.6e18 / 6.9e18 / 9.2e18 会原样画出来），只在 ≥ {@code Long.MAX_VALUE / 2} 时
     *       按老山海口径写「无限」哨兵 —— 判定取
     *       {@link PrimordialModuleMachine#isInfiniteParallel(long)}（<b>三处显示共用的唯一一处阈值</b>）。</li>
     *   <li>{@code threads} = {@link PrimordialModuleMachine#getCrossRecipeThreads()}：
     *       <b>恒为 1</b>。用户 2026-09-25 原话「现在模块是没有跨配方线程，后续我会加，
     *       你现在可以写跨配方线程数为 1」⇒ 这是<b>用户指定的显示值</b>，
     *       上游会把它画成「拥有1个跨配方线程」（与主机那句「拥有128个跨配方线程」同款）。</li>
     * </ul>
     *
     * <h2>为什么要在这里 return 而不是让主机分支去判</h2>
     * 两个类互不继承（都只是 {@code WorkableElectricMultiblockMachine} 的子类），
     * 所以写成"先判模块、命中即返回"最不容易碰坏已经跑通的主机路径。
     *
     * <p>⚠️ <b>可验证性边界（诚实声明）</b>：本方法属于 {@code require = 0} 的纯显示增强，
     * 而且只有客户端在看方块时才会被 Jade 触发 ⇒ <b>无头专服冒烟测试证明不了它生效</b>，
     * 只能由用户进游戏看一眼（验收清单见交付报告）。为了不"静默失败"，
     * 第一次真正写入时会打一条 INFO（字面量 {@code [SHANHAI-DISPLAY]}，可 grep）。
     */
    private static void shanhai$moduleDisplay(CompoundTag data, MetaMachineBlockEntity mbe) {
        if (!(mbe.getMetaMachine() instanceof PrimordialModuleMachine module)) {
            return;
        }
        // 与主机同一判据：只在成型时写（未成型时上面那条兜底分支本来也不写）。
        if (!module.isFormed()) {
            return;
        }
        final long parallel = module.getJadeParallel();
        final int threads = module.getCrossRecipeThreads();
        // 🔴 2026-09-26（任务书 ④）：老山海口径 ——
        //   并行值 <  Long.MAX_VALUE / 2  ⇒ 写真 long（上游 `%d` 键会原样画出真实位数，不再是 21 亿）
        //   并行值 >= Long.MAX_VALUE / 2  ⇒ 写 gtladditions 的「无限」哨兵（画成彩虹「无限」）
        //   阈值取自 PrimordialModuleMachine#isInfiniteParallel(long)：**唯一一处定义**，
        //   与机器 GUI tooltip（formatParallel）用的是同一个判据，不会漂移。
        final boolean infinite = PrimordialModuleMachine.isInfiniteParallel(parallel);
        data.putLong("parallel", infinite ? SHANHAI$INFINITE : parallel);
        data.putLong("threads", threads);
        if (!shanhai$moduleAnnounced) {
            shanhai$moduleAnnounced = true;
            ShanhaiMod.LOGGER.info("[SHANHAI-DISPLAY] 已写入模块 Jade 真值：parallel={}（写入 {}；{}）threads={}"
                            + "（用户 2026-09-25：模块暂无跨配方线程，显示值写 1；并行按模块自身真实值；"
                            + "2026-09-26：不再被压成 21 亿）",
                    parallel, infinite ? "「无限」哨兵" : String.valueOf(parallel),
                    infinite ? "≥ Long.MAX/2，按老山海口径画无限" : "小于 Long.MAX/2，画真 long", threads);
        }
    }

    /** 只打一次，避免 Jade 每次刷新都刷屏（与主机那条同一个理由）。 */
    @Unique
    private static boolean shanhai$moduleAnnounced = false;
}
