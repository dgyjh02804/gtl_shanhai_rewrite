package com.shanhai.common.thread;

/**
 * 🔴 <b>并行预算的纯算术核（只 import {@code java.*}）</b> —— "本机并行上限 × 跨配方线程数"这
 * 三行算术的<b>唯一实现</b>，同时承担加载期自检与离线判据（本工程惯例：判据做成纯函数，
 * 既能在无头专服加载期真跑，也能单独 {@code javac} 驱动）。
 *
 * <h2>它为什么存在（本轮 2026-09-30 的起因）</h2>
 * 用户报的 bug（原话逐字）：<b>「又发现个bug，零点能反应堆不吃跨配方并行，那个发电量都没加」</b>。
 * 现象：同一台<b>原始真空零点能发生器</b>，线程槽放 30 个「世线残片·共鸣」把跨配方线程
 * 从 <b>1 提到 121</b>，而产能稳定在 <b>92.23E EU/t</b>，<b>一点没动</b>。
 *
 * <p>根因（详见交付报告 §1）：那台机器是<b>唯一</b>被 {@code PrimordialModuleRecipeLogic#shanhai$resolveRouting()}
 * 判定为 {@code getDefinition().isGenerator()} 的机器 ⇒ 它 {@code setUseMultipleRecipes(false)}，
 * <b>退回原生链</b>（引擎会把它的输出电算成深负值）。而原生链的并行实参取自
 * {@code ModuleRegistry#applyModuleRecipeModifier} 里的
 * {@code module.getRecipeLogicMaxParallel()}（= {@code max(1, getCurrentParallel())}）——
 * <b>那把表达式里根本没有跨配方线程</b>；引擎路径那一边（{@code PrimordialModuleRecipeLogic#calculateParallels()}）
 * 用的才是 {@link #totalParallelLimitFor(long, int)} = {@code 表值 × 线程数}。
 * <p>⇒ <b>同一台机器、同一种玩法，两条路对"跨配方线程"的态度相反</b>。本核把那份算术抽成一份，
 * 让原生链也走同一个表达式。
 *
 * <h2>why 值 = 1（线程槽空）时必须逐值不变</h2>
 * 用户那台机器在没放残片时显示的是 {@code 拥有跨配方线程: 1}。
 * {@link #saturatedMultiply(long, long)} 在 {@code b == 1} 时恒等于 {@code a}
 * ⇒ {@code totalParallelLimitFor(x, 1) == recipeLogicMaxParallelFor(x)} <b>对任意 x 成立</b>
 * ⇒ 空线程槽那条路与改动前<b>逐位相同</b>。这一条是本类自检的第一条断言
 * （也是"不影响现有玩法"的形式化证明）。
 *
 * <h2>🔴 一处必须知情的边界：原生链的 int 天花板</h2>
 * gtlcore 的 {@code ParallelLogicMixin#getMaxRecipeMultiplier} 第一句是
 * {@code ldc 2147483647}，末句是 {@code Ints.saturatedCast}
 * ⇒ <b>走 gtceu 那个 int 版 {@code ParallelLogic.applyParallel} 的路，并行永远不可能超过 2^31−1</b>。
 * 用户那台机器的物质模块槽里是「永恒物质模块」，它的表值 {@code 2147483647}
 * <b>恰好等于那个天花板</b> ⇒ 即使把线程数接进 int 版也<b>一个字都不会变</b>。
 * <p>⇒ 这就是为什么本核的乘数必须<b>允许越过 int</b>：超过 {@link #NATIVE_INT_CEILING} 时，
 * 本工程的 {@code PrimordialRecipeEffects#applyParallel(GTRecipe, MetaMachine, long)}
 * 会转交自建的 long 分支（{@code shanhai$applyParallelLong}），那条路才装得下。
 */
public final class ShanhaiParallelBudget {

    /**
     * 原生链（gtceu int 版 {@code ParallelLogic}）的<b>硬天花板</b>。
     *
     * <p>出处（字节码原文，取证文件 {@code temp\zpe-thread\javap\ParallelLogicMixin.txt}）：
     * <pre>
     *   ParallelLogicMixin.getMaxRecipeMultiplier(GTRecipe, IRecipeCapabilityHolder, int):
     *     0: ldc   #29   // int 2147483647     ← 起点就是它
     *     9: invokestatic IParallelLogic.getMaxParallel:(...)J
     *    12: invokestatic Math.min:(JJ)J        ← 与 IParallelLogic 的结果取小
     *    15: invokestatic Ints.saturatedCast:(J)I   ← 再窄化成 int
     * </pre>
     * ⇒ 该方法的返回值<b>上界就是 2147483647</b>，与传进去的 int 实参无关。
     */
    public static final long NATIVE_INT_CEILING = 2147483647L;

    private ShanhaiParallelBudget() {}

    /**
     * <b>饱和 long 乘法</b>（全工程唯一一份；2026-09-30 从 {@code PrimordialModuleMachine} 原样搬来，
     * 那里现在只剩一行委托 —— 搬动<b>没有</b>改动任何数值或分支）。
     *
     * <p>老山海 guide 的自写红线（{@code special_index.md:113}）：
     * 「最大并行不能超过 {@code Long.MAX_VALUE}，否则会发生数值溢出」
     * ⇒ 并行预算这一路上的每个乘法<b>都必须饱和</b>，不许裸乘。
     * <p>语义：任一因子 ≤ 0 ⇒ 0（"没有预算"，引擎据此停机而<b>不是</b>得到负数）；
     * 否则溢出时返回 {@code Long.MAX_VALUE}（"无限"，正是老山海对最高档的表达）。
     */
    public static long saturatedMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        if (a > Long.MAX_VALUE / b) {
            return Long.MAX_VALUE;
        }
        return a * b;
    }

    /**
     * <b>本模块的并行上限（long 形态）</b> = {@code max(1, currentParallel)}。
     *
     * <p>与老山海逐字同源：{@code PrimordialOmegaEngineModuleBase.java:520}
     * {@code return Math.max(1L, getCurrentParallel());}。
     * 下限 1 的理由与老山海一致：0/负值会让引擎的贪心分配
     * {@code if (remain <= 0L) break;} 立刻跳出 ⇒ 机器【不动、不崩、日志无输出】。
     */
    public static long recipeLogicMaxParallelFor(long currentParallel) {
        return Math.max(1L, currentParallel);
    }

    /**
     * <b>并行预算</b> = {@link #recipeLogicMaxParallelFor(long)} × 跨配方线程数（饱和）。
     *
     * <p>形状与老山海的 {@code getTotalParallelLimit()}
     * （{@code saturatedMultiply(getMachine().getRecipeLogicMaxParallel(), getLogicThreadMultiplier())}）
     * 逐字同源，而不是 gtladditions 父类的 {@code (long) getMaxParallel() * getMultipleThreads()}
     * —— 后者的第一个因子是 int，正是被压平的那一处。
     *
     * @param threads 跨配方线程数；{@code <= 0} 按 <b>1</b> 算（"没有线程"= 不放大）
     */
    public static long totalParallelLimitFor(long currentParallel, int threads) {
        return saturatedMultiply(recipeLogicMaxParallelFor(currentParallel), Math.max(1, threads));
    }

    /** 用户实测档：物质模块槽里的「永恒物质模块」表值（{@code ShanhaiConcurrencyTables} 现读）。 */
    public static final long USER_ETERNAL_MODULE_PARALLEL = 2147483647L;
    /** 用户实测档：线程槽 30 个「世线残片·共鸣」（2 号残片 = 2^2 = 4/枚 ⇒ 1 + 4×30 = 121）。 */
    public static final int USER_SHARD_THREADS = 121;
    /** 用户实测档：121 线程下的预算字面量 = 2147483647 × 121（已核算）。 */
    public static final long USER_BUDGET_AT_121 = 259845521287L;

    /**
     * 🔴 <b>加载期自检（正向对照 + 负面对照 + 复原），由
     * {@code PrimordialModuleMachine#assertParallelTablesConsistent()} → {@code ModuleRegistry#init()}
     * 在注册期调用 ⇒ 无头专服里必跑、日志可 grep。</b>
     *
     * @return 一行可 grep 的读数（调用方直接 {@code LOGGER.info(...)}）
     * @throws IllegalStateException 任一条断言不成立（本项目风格：加载期 fail-fast，不静默）
     */
    public static String selfTest() {
        // ── ① 恒等（改动安全性的形式化证明）：线程 = 1 时与改动前的实参逐值相同 ──
        final long[] samples = {0L, 1L, 2L, 64L, 128L, 1048576L, 536870912L, 2147483647L,
                4611686018427387903L, 6917529027641081855L, Long.MAX_VALUE};
        int identityChecked = 0;
        for (int i = 0; i < samples.length; i++) {
            final long c = samples[i];
            final long before = recipeLogicMaxParallelFor(c);          // 改动前原生链的实参
            final long after = totalParallelLimitFor(c, 1);            // 改动后（线程槽空 = 1）
            if (before != after) {
                throw new IllegalStateException("[SHANHAI-PARALLEL-BUDGET] 自检失败：线程 = 1 时必须恒等，"
                        + "但 currentParallel=" + c + " 时 改动前=" + before + " / 改动后=" + after
                        + " ⇒ 「不影响现有玩法」这句话不成立。");
            }
            identityChecked++;
        }

        // ── ② 用户实测档：121 线程必须真的把预算顶过原生链的 int 天花板 ──
        final long userBefore = totalParallelLimitFor(USER_ETERNAL_MODULE_PARALLEL, 1);
        final long userAfter = totalParallelLimitFor(USER_ETERNAL_MODULE_PARALLEL, USER_SHARD_THREADS);
        assertEq(userBefore, 2147483647L, "1 线程时预算必须 = 永恒物质模块表值");
        assertEq(userAfter, USER_BUDGET_AT_121, "121 线程时预算必须 = 2147483647 × 121");
        if (userAfter <= NATIVE_INT_CEILING) {
            throw new IllegalStateException("[SHANHAI-PARALLEL-BUDGET] 自检失败：121 线程下预算 "
                    + userAfter + " 没有越过原生链天花板 " + NATIVE_INT_CEILING
                    + " ⇒ 用户那台机器加了残片也不会有任何变化（这正是他报的现象）。");
        }
        // 121 这个数本身自洽：1 + 2^2 × 30（世线残片·共鸣 = thread_shard_2）
        assertEq(1L + 4L * 30L, (long) USER_SHARD_THREADS, "121 必须 = 1 + 2^2 × 30（共鸣残片）");

        // ── ③ 饱和红线：Long.MAX 档 × 线程数不许回绕成负数 ──
        assertEq(totalParallelLimitFor(Long.MAX_VALUE, 121), Long.MAX_VALUE, "MAX 档 × 121 必须饱和到 MAX");
        assertEq(totalParallelLimitFor(4611686018427387903L, 3), Long.MAX_VALUE, "4.6e18 × 3 必须饱和到 MAX");
        assertEq(totalParallelLimitFor(100L, 0), 100L, "线程数 ≤ 0 按 1 算（不放大、也不清零）");
        assertEq(totalParallelLimitFor(100L, -5), 100L, "线程数为负同样按 1 算");

        // ── ④ 负面对照 A：不改的话（老实参）在 121 线程下永远是表值 ⇒ 与用户所见吻合 ──
        if (userBefore >= userAfter) {
            throw new IllegalStateException("[SHANHAI-PARALLEL-BUDGET] 自检的【负面对照】失败："
                    + "老实参在 121 线程下竟然不小于新实参 ⇒ 本核什么都没证明。");
        }

        // ── ⑤ 负面对照 B：防"把乘数写死" —— 1 线程那档必须【不】等于 121 线程那档 ──
        if (totalParallelLimitFor(USER_ETERNAL_MODULE_PARALLEL, 1) == USER_BUDGET_AT_121) {
            throw new IllegalStateException("[SHANHAI-PARALLEL-BUDGET] 自检的【负面对照】失败："
                    + "1 线程与 121 线程算出了同一个预算 ⇒ 乘数写死了。");
        }

        // ── ⑥ 复原（顺序无关 / 无状态）：同样的输入再算一次必须同值 ──
        if (totalParallelLimitFor(USER_ETERNAL_MODULE_PARALLEL, USER_SHARD_THREADS) != userAfter
                || totalParallelLimitFor(USER_ETERNAL_MODULE_PARALLEL, 1) != userBefore) {
            throw new IllegalStateException("[SHANHAI-PARALLEL-BUDGET] 自检失败：重复计算不同值（本核应当无状态）。");
        }

        return "[SHANHAI-PARALLEL-BUDGET] 并行预算纯算术核自检通过（恒等 " + identityChecked
                + " 档 + 用户实测档 + 饱和 " + 2 + " 条 + 负面对照 2 条 + 复原 1 条）："
                + "线程槽空（=1）时与原实参逐值相同；永恒物质模块表值 " + USER_ETERNAL_MODULE_PARALLEL
                + " 在 1 线程下预算 = " + userBefore + "（恰 = 原生链 int 天花板 " + NATIVE_INT_CEILING
                + "），在 " + USER_SHARD_THREADS + " 线程下预算 = " + userAfter
                + "（> 天花板，会转交 long 分支）；MAX 档 × 121 饱和到 " + Long.MAX_VALUE + "（不回绕）。";
    }

    private static void assertEq(long actual, long expected, String what) {
        if (actual != expected) {
            throw new IllegalStateException("[SHANHAI-PARALLEL-BUDGET] 自检失败：" + what
                    + "，实际 " + actual + "，应为 " + expected + "。");
        }
    }
}
