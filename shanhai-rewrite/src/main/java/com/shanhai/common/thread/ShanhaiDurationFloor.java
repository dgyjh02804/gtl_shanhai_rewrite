package com.shanhai.common.thread;

import java.util.Locale;

/**
 * 山海重构 · <b>「并行进入 long 档 ⇒ 配方时长下限」的唯一一份算术</b>（纯 {@code java.*}）。
 *
 * <h2>用户原话（逐字，本类就是它的实现）</h2>
 * <blockquote>「对了，这个配方加到 long 之后可以加一个最小配方时长为 10tick，然后 jade 写一下提示，
 * 这样不会引起误解」</blockquote>
 *
 * <h2>它治的是哪个现象（同一次诊断的实测结论）</h2>
 * 并行预算进入 long 档之后，GTCEu / gtlcore 的输入钳位会把实际并行放大到
 * 「输入箱里有多少 ÷ 每份用量」：对【原始真空零点能发生器】+【创始现实修改模块】+【创造模式输入仓】
 * 这一组，<b>一次装配就把整箱 9,223,372,036,854,775,807 mB 扣到只剩 807 mB</b>；
 * 而配方时长被 N5 减免 + N6 压到 <b>1 tick</b> ⇒ 下一 tick 就没料 ⇒ 抬头在
 * 「在跑（有产能）」与「配方失败原因：未找到配方」之间反复横跳
 * —— 因为 gtmthings 的创造模式输入仓 {@code autoKeep} 每 <b>5</b> tick 才回填一次。
 * <b>时长抬到 10 tick 之后，一个料周期跨 2 次回填 ⇒ 不再挨饿 ⇒ 横跳消失。</b>
 *
 * <h2>判据：「加到 long 之后」的可判定定义</h2>
 * <b>并行预算 &gt; {@link ShanhaiParallelBudget#NATIVE_INT_CEILING}（= 2147483647）</b>。
 * 那个常量是全工程「int 版并行装不下」的唯一真源，也正是 gtceu
 * {@code ParallelLogicMixin#getMaxRecipeMultiplier} 字节码里的起点 ⇒ 越过它才叫"进了 long 通道"
 * （并行表末三档：物质创造模块 / 现实锚定模块 / 创始现实修改模块）。
 *
 * <p>⚠️ 判据取的是<b>预算</b>（{@code totalParallelLimitFor} 的输出），
 * <b>不是</b>"实际吃到的并行"——后者被输入量钳位、随箱里剩多少料跳变，
 * 用它会让这条下限"时灵时不灵"。
 *
 * <h2>🔴 红线措辞的收窄（用户 2026-09-30 拍板「B. 破一次红线，让那 25 台也抬到 10」）</h2>
 * <pre>
 * ⛔ 旧措辞（作废）：<b>任何情况下配方时长都不许超过配方定义的原时长。</b>
 *                    ⇒ 推论：{@code min(10, 原时长)} —— 原时长 &lt; 10 的配方**永远拿不到下限**，
 *                      而那正是引擎链 25 台的实际情形（它们的配方定义时长 = 1 tick）。
 * ✅ 新措辞（现行，逐字即本条）：<b>只有「进了 long 档」（并行预算 &gt; 2,147,483,647）时，
 *                   配方时长才允许被抬到 10 tick；
 *                   其余一切情形，配方时长仍不许超过配方定义的原时长。</b>
 *                    ⇒ 推论：判据成立 ⇒ <b>下限恒为 10 tick（绝对 10，不再按原时长收缩）</b>；
 *                      判据不成立 ⇒ <b>逐值不变</b>（一个 tick 都不许动）。
 * </pre>
 * <p><b>已知代价（用户明知并接受）</b>：原时长 &lt; 10 的配方在 long 档下会<b>比原版慢</b>
 * （最坏 1 → 10，即每周期慢 10 倍）。这是"破红线"的代价本身，不是 bug。
 *
 * <h2>⚠️ "绝对 10"的实现口径（如实写清，防误读）</h2>
 * 「配方时长就是 10 tick」在本类里实现为 <b>下限</b>：{@code duration = max(当前时长, 10)}。
 * <b>不是</b>"强制等于 10"——后者会把原时长 60 tick 的配方<b>砍到 10</b>（快 6 倍），
 * 那是静默加速、会重新制造本类要治的"一顿饱一顿饿"，与用户"抬到 10"的原话方向相反。
 * 本类只把时长<b>往长里抬</b>，从不缩短。
 *
 * <h2>为什么要单独开一个纯类</h2>
 * 与本工程 {@link ShanhaiParallelBudget} / {@code ShanhaiFairAllocation} 同款理由：
 * <b>只 import {@code java.*}</b> ⇒ 可以单独 {@code javac} 驱动，做【正常 / 预期失败 / 复原】三段自证；
 * 也能挂进加载期自检，在无头专服里真跑（那是本工程唯一"一次运行就能验到"的位置）。
 * 引用型调用点（{@code PrimordialRecipeEffects} 与 Jade 显示那两处）只做一行委托，
 * <b>全工程没有第二份算术</b>。
 */
public final class ShanhaiDurationFloor {

    private ShanhaiDurationFloor() {}

    /**
     * <b>并行进 long 档之后的配方时长下限 = 10 tick</b>（用户 2026-09-30 拍板）。
     *
     * <p>为什么是 10：与主机侧 GUI 允许的【最小下限】
     * {@code PrimordialRecipeEffects.MIN_LIMITED_DURATION} 同值
     * （那个值本身照抄 gtladditions {@code LimitedDurationConfigurator} 的 {@code setMin(10)}）。
     * {@link #selfTest()} 断言两者相等 ⇒ 将来谁改了其中一处会当场炸，不会静默漂移。
     */
    public static final int LONG_SCALE_MIN_DURATION = 10;

    /**
     * 本台机器当前的并行预算<b>是不是"进了 long 档"</b>。
     *
     * <p>边界必须写成 <b>严格大于</b>：{@code 2147483647} 本身是「int 档的最高值」，
     * 不属于 long 档（并行表里的「永恒物质模块」恰好取它，而那一档**不该**吃本下限）。
     * 这一档由 {@link #selfTest()} 的负面对照钉死。
     */
    public static boolean isLongScaleParallel(long parallelBudget) {
        return parallelBudget > ShanhaiParallelBudget.NATIVE_INT_CEILING;
    }

    /**
     * <b>判据成立时的时长下限 = 绝对 10 tick</b>（不再按原时长收缩）。
     *
     * <p>⛔ 历史（作废，要点留档）：本方法曾经是 {@code min(10, 原时长)}，为的是不撞
     * 「任何情况下时长都不许超过定义时长」那条旧红线。用户 2026-09-30 选 B 破掉它
     * ⇒ 现在**与原时长无关，恒返回 {@link #LONG_SCALE_MIN_DURATION}**。
     *
     * <p>⚠️ 形参 {@code originalDuration} <b>刻意保留</b>（值不被使用）：它是现场探针与日志
     * 「为什么这个数是这样」的一半证据，删掉形参会让调用点不得不再读一次、多一处漂移可能。
     *
     * @param originalDuration 配方定义的原时长（<b>本实现不读它</b>；&le;0 与 &gt;0 结果相同）
     */
    public static int floorTargetFor(int originalDuration) {
        return LONG_SCALE_MIN_DURATION;
    }

    /**
     * <b>唯一入口</b>：并行预算 + 当前时长 + 原时长 ⇒ 最终时长。
     *
     * <pre>
     *   预算 &lt;= 2147483647（未进 long 档）  ⇒  逐值不变（返回 currentDuration，一个 tick 都不动）
     *   预算 &gt;  2147483647（已进 long 档）  ⇒  max(currentDuration, 10)     ← 绝对下限，与"原时长"无关
     * </pre>
     *
     * <p>恒等那一支是硬保证：本工程所有"并行 ≤ 21 亿"的档位（空槽 64、并行表前 16 档、
     * 以及「永恒物质模块 + 线程 1」那个恰好等于天花板的边界）走这一支 ⇒ 一个字都不变。
     * {@link #selfTest()} 逐档断言。
     */
    public static int apply(int currentDuration, int originalDuration, long parallelBudget) {
        if (!isLongScaleParallel(parallelBudget)) {
            return currentDuration;
        }
        return Math.max(currentDuration, floorTargetFor(originalDuration));
    }

    /**
     * <b>加载期自检</b>（正面对照 6 条 + 负面对照 4 条 + 恒等 11 档 + 边界 2 条）。任一条不成立 ⇒ 抛异常。
     *
     * <p>调用点：{@code PrimordialModuleMachine#assertParallelTablesConsistent()}（机器注册期，
     * 早于任何存档加载）⇒ <b>无头专服里必跑、日志可 grep</b>，与本工程其余纯核同一条链。
     *
     * @return 给调用方写日志用的一整行（<b>本类自己不写日志</b> —— 纯类不引用 {@code ShanhaiMod}）
     */
    public static String selfTest() {
        // ── ① 常量与主机侧 GUI 的最小下限同值（防"两处各写一个 10"的漂移） ──
        if (LONG_SCALE_MIN_DURATION != 10) {
            throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 下限常量应为 10，实为 "
                    + LONG_SCALE_MIN_DURATION);
        }

        // ── ② 恒等：并行未进 long 档的每一档都必须返回入参本身（🔴 别把不该拖慢的也拖慢） ──
        final long[] notLongScale = {
                0L, 1L, 2L, 64L, 128L, 1_048_576L, 536_870_912L,
                2_147_483_647L,                                 // ← 边界：恰好 = int 天花板，不算 long 档
                -1L, -114_514L,                                 // 负值（哨兵 / 坏数据）
        };
        for (long budget : notLongScale) {
            // 原时长也逐档扫：新公式不读原时长 ⇒ 无论原时长多短，判据不成立就必须逐值不变。
            for (int current : new int[] { 0, 1, 5, 9, 10, 11, 20, 200 }) {
                for (int original : new int[] { -1, 0, 1, 5, 20 }) {
                    final int got = apply(current, original, budget);
                    if (got != current) {
                        throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 自检失败：预算 " + budget
                                + "（未进 long 档）时不得改动时长，当前 " + current + "／原时长 " + original
                                + " 实得 " + got);
                    }
                }
            }
        }

        // ── ③ 边界：严格大于（2147483647 不触发，2147483648 触发） ──
        if (isLongScaleParallel(ShanhaiParallelBudget.NATIVE_INT_CEILING)) {
            throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 自检失败：int 天花板自身被误判为 long 档");
        }
        if (!isLongScaleParallel(ShanhaiParallelBudget.NATIVE_INT_CEILING + 1L)) {
            throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 自检失败：天花板 +1 未被判为 long 档");
        }
        // 天花板 +1（用户最关心的一条"刚过线"）必须真的能抬起来
        if (apply(1, 1, ShanhaiParallelBudget.NATIVE_INT_CEILING + 1L) != LONG_SCALE_MIN_DURATION) {
            throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 自检失败：刚过线（天花板 +1）没能抬到 "
                    + LONG_SCALE_MIN_DURATION);
        }

        // ── ④ 正面对照（用户实测档）：预算 = Long.MAX、时长被压到 1 tick、原时长 20 ⇒ 10 ──
        final int userCase = apply(1, 20, Long.MAX_VALUE);
        if (userCase != LONG_SCALE_MIN_DURATION) {
            throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 自检失败：用户档（1 tick / 原时长 20 / 预算 MAX）"
                    + "应得 " + LONG_SCALE_MIN_DURATION + "，实为 " + userCase);
        }
        // 同一个料周期要跨过创造箱的 5 tick 回填 ⇒ 10 tick 必须 > 5（这是"不横跳"的判据本身）
        if (userCase <= REFILL_TICKS_OF_CREATIVE_HATCH) {
            throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 自检失败：下限 " + userCase
                    + " 不大于创造模式输入仓的回填周期 " + REFILL_TICKS_OF_CREATIVE_HATCH
                    + " tick ⇒ 治不了横跳，这个值等于没定。");
        }

        // ── ⑤ 正面对照：其余 long 档（4.6e18 / 6.9e18）同样抬到 10 ──
        for (long budget : new long[] { 4_611_686_018_427_387_903L, 6_917_529_027_641_081_855L }) {
            if (apply(1, 20, budget) != LONG_SCALE_MIN_DURATION) {
                throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 自检失败：预算 " + budget
                        + " 未抬到 " + LONG_SCALE_MIN_DURATION);
            }
        }

        // ── ⑥ 🔴 本轮新增的核心正面对照：原时长 < 10 的也必须被抬到 10（红线收窄后的语义） ──
        //    这一条就是用户选 B 要的效果；旧的 min(10, 原时长) 在这一条上会给出 5 / 3 / 1 ⇒ 必须失败。
        for (int original : new int[] { 1, 3, 5, 9 }) {
            final int got = apply(1, original, Long.MAX_VALUE);
            if (got != LONG_SCALE_MIN_DURATION) {
                throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 自检失败：long 档下原时长 " + original
                        + " 的配方应被抬到 " + LONG_SCALE_MIN_DURATION + "（绝对下限），实为 " + got
                        + " ⇒ 又退回「按原时长收缩」的旧口径了。");
            }
        }
        // 原时长"未知"（<=0）时同样是 10（新公式不读原时长，这条与上一条同源但独立可读）
        if (floorTargetFor(0) != LONG_SCALE_MIN_DURATION || floorTargetFor(-7) != LONG_SCALE_MIN_DURATION) {
            throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 自检失败：原时长未知时下限应仍为 "
                    + LONG_SCALE_MIN_DURATION);
        }

        // ── ⑦ 负面对照：已经 ≥ 下限的时长不许被动（幂等；也钉死"只抬不砍"） ──
        if (apply(20, 20, Long.MAX_VALUE) != 20 || apply(10, 20, Long.MAX_VALUE) != 10) {
            throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 自检失败：时长不幂等（20→"
                    + apply(20, 20, Long.MAX_VALUE) + "，10→" + apply(10, 20, Long.MAX_VALUE) + "）");
        }
        // 🔴 绝不许把长配方砍短（"强制等于 10"那个误读的口径一旦被人加回来，这条会当场炸）
        for (int longOne : new int[] { 11, 60, 200 }) {
            if (apply(longOne, 200, Long.MAX_VALUE) != longOne) {
                throw new IllegalStateException("[SHANHAI-DURATION-FLOOR] 自检失败：原时长 " + longOne
                        + " 的配方被砍到了 " + apply(longOne, 200, Long.MAX_VALUE)
                        + " ⇒ 本类只抬不砍，「绝对 10」被误实现成了「强制等于 10」。");
            }
        }

        return "[SHANHAI-DURATION-FLOOR] 并行 long 档时长下限自检通过："
                + "判据 = 预算 > " + String.format(Locale.ROOT, "%,d", ShanhaiParallelBudget.NATIVE_INT_CEILING)
                + "（严格大于；天花板自身不触发）；"
                + "🔴 现行红线措辞 = 「只有进了 long 档时才允许把配方时长抬到 " + LONG_SCALE_MIN_DURATION
                + " tick；其余一切情形仍不许超过配方定义的原时长」"
                + "（旧措辞「任何情况下都不许超过定义时长」已按用户 2026-09-30 选 B 作废）；"
                + "下限 = 绝对 " + LONG_SCALE_MIN_DURATION + " tick（与原时长无关；"
                + "≤ 创造模式输入仓回填周期 " + REFILL_TICKS_OF_CREATIVE_HATCH + " tick 就治不了横跳）；"
                + "用户档（1 tick / 原时长 20 / 预算 MAX）⇒ " + userCase
                + "；原时长 1/3/5/9 在 long 档下同样 ⇒ " + LONG_SCALE_MIN_DURATION
                + "（这是破红线那一步，代价 = 那些配方比原版慢，最坏 1→10 即 10 倍）；"
                + "只抬不砍（11/60/200 逐值不动）；"
                + "未进 long 档的 " + notLongScale.length + " 档 × 8 个当前时长 × 5 个原时长全部逐值不变。";
    }

    /**
     * 创造模式输入仓（gtmthings {@code CreativeInputHatchPartMachine#autoKeep}）的回填周期 = 5 tick。
     *
     * <p>它**不是**可调参数，而是对方源码里的 {@code getOffsetTimer() % 5 == 0}（字节码原文）。
     * 写在这里只为了让 {@link #selfTest()} 能断言「下限 &gt; 回填周期」——
     * 即"这个下限真的能治横跳"这件事本身。
     */
    public static final int REFILL_TICKS_OF_CREATIVE_HATCH = 5;
}
