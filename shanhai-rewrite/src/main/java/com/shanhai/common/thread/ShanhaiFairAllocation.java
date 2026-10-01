package com.shanhai.common.thread;

/**
 * 山海重构 · <b>跨配方并行的「公平分配」纯算术核</b>。
 *
 * <h2>🔴 为什么单独一个类，而不是写在 {@code PrimordialRecipeEffects} 里</h2>
 * 和 {@link ShanhaiConcurrencyTables} 同一条纪律：<b>把要判定的那件事放到一个
 * 不依赖 Minecraft / GTCEu / Forge 的地方</b>。
 * 「跨配方并行到底会不会互相挤占」是这一轮唯一要回答的问题，
 * 而它<b>全部</b>落在这个几十行的整数算法上
 * ⇒ 只要能用一个 {@code javac + java}（不碰游戏、不碰任何 mod jar）把它驱动起来，
 * 判据就不再是"我推导应该对"，而是"跑出来的数字"。
 *
 * <p>本类<b>刻意只 import {@code java.*}</b>（连 fastutil 都不用，改用裸数组），
 * 保证 {@code javac ShanhaiFairAllocation.java} 一条命令就能编译，
 * 离线判据见 {@code temp/probes/fair-alloc/FairAllocCheck.java}。
 *
 * <h2>它治的病（用户 2026-09-29 实机原文）</h2>
 * <blockquote>
 *   「这两种的配方都可以正常执行，但是我开启跨配方种类并行时，它们并没有同时进行，
 *    而是一种挤占了另一种，而 gtladd 的机器就可以同时执行」
 * </blockquote>
 * 父类走的是<b>贪心</b>（{@code calculateParallelsWithGreedyAllocation}）：第一条候选拿
 * {@code p = min(输入量, 剩余预算)}，原料管够时它就是<b>整个预算</b>
 * ⇒ {@code remain == 0} ⇒ 循环在第二条之前 {@code break} ⇒ <b>后面的候选拿到 0</b>。
 * 本类实现的是上游<b>公平版</b>（{@code calculateParallelsWithFairAllocation} +
 * {@code getFinalParallelData} + {@code getParallelDataIndexArray}）的逐句等价形态。
 *
 * <h2>两相算法（上游原文，逐句）</h2>
 * <pre>
 *   相 1（保底）  share_i = min(demand_i, 总预算 / 候选条数)
 *                 ⚠️ 分母是【候选条数】（含 demand ≤ 0 的那几条），不是"能跑的那几条" —— 上游同形
 *                 预算 -= share_i；欠的那部分进 remainingWants
 *   相 2（水填充）while (预算 &gt; 0 且 还有人欠着) {
 *                     每人份 = 剩余预算 / 还欠着的人数;
 *                     if (每人份 &lt;= 0) break;
 *                     for (每个还欠着的人) { 补 min(还欠多少, 每人份); 累计本轮消耗; }
 *                     剩余预算 -= 本轮消耗;
 *                 }
 * </pre>
 *
 * <h2>三条不变式（离线判据断言的就是它们）</h2>
 * <ol>
 *   <li>{@code 0 <= shares[i] <= max(0, demands[i])}；</li>
 *   <li>{@code Σ shares[i] <= totalParallel}（不会分超预算）；</li>
 *   <li><b>不饿死</b>：{@code demands[i] > 0} 且 {@code totalParallel / n > 0}
 *       ⇒ {@code shares[i] > 0}。
 *       <p>这就是"挤占被治好"的形式化表述：贪心版违反它（后面的候选拿 0）。</li>
 * </ol>
 *
 * <h2>🔴 值 = 1 时恒等（本项目最硬的一条要求）</h2>
 * {@code n == 1} ⇒ {@code shares[0] = min(demand_0, 总预算)}，
 * 而贪心的第一条拿的也正是 {@code min(需求, remain = 总预算)} ⇒ <b>逐值相等</b>。
 */
public final class ShanhaiFairAllocation {

    private ShanhaiFairAllocation() {}

    /**
     * 公平分配（相 1 保底 + 相 2 水填充）。
     *
     * @param demands       每条候选的并行需求（{@code <= 0} = 这条跑不了；
     *                      <b>仍然占一个"分母名额"</b> —— 上游 {@code limit / recipes.size()} 就是这么算的）
     * @param totalParallel 并行预算
     * @return 与 {@code demands} <b>等长且下标一一对应</b>的份数数组；不可跑的候选恒为 0
     */
    public static long[] fairShares(long[] demands, long totalParallel) {
        final int count = demands == null ? 0 : demands.length;
        final long[] shares = new long[count];
        if (count == 0 || totalParallel <= 0L) {
            return shares;
        }
        // 欠额表（用裸数组代替上游的 LongArrayList / IntArrayList，好让本文件零依赖）
        final long[] wants = new long[count];
        final int[] indices = new int[count];
        int wantCount = 0;
        long remainingLimit = totalParallel;

        // ── 相 1 ──
        for (int i = 0; i < count; i++) {
            final long demand = demands[i];
            if (demand <= 0L) {
                continue;
            }
            final long share = Math.min(demand, totalParallel / (long) count);
            shares[i] = share;
            final long want = demand - share;
            if (want > 0L) {
                wants[wantCount] = want;
                indices[wantCount] = i;
                wantCount++;
            }
            remainingLimit -= share;
        }

        // ── 相 2：水填充 ──
        while (remainingLimit > 0L && wantCount > 0) {
            final long each = remainingLimit / (long) wantCount;
            if (each <= 0L) {
                break;
            }
            long consumed = 0L;
            int write = 0;
            for (int i = 0; i < wantCount; i++) {
                final int target = indices[i];
                final long want = wants[i];
                final long add = Math.min(want, each);
                shares[target] += add;
                consumed += add;
                final long left = want - add;
                if (left > 0L) {
                    wants[write] = left;
                    indices[write] = target;
                    write++;
                }
            }
            wantCount = write;
            remainingLimit -= consumed;
        }
        return shares;
    }

    /**
     * <b>对照实现：今天线上跑的那一份（贪心）</b>—— 只给离线判据当负面对照用。
     *
     * <p>逐句照抄上游 {@code calculateParallelsWithGreedyAllocation} 的循环骨架
     * （含那个 {@code if (remain <= 0) break;}）。<b>生产代码里没有任何地方调它</b>，
     * 它存在的唯一目的 = 让判据能"用同一批输入把新旧两版并排打出来"，
     * 免得"修好了"只是一句空话。
     */
    public static long[] greedyShares(long[] demands, long totalParallel) {
        final int count = demands == null ? 0 : demands.length;
        final long[] shares = new long[count];
        long remain = totalParallel;
        for (int i = 0; i < count; i++) {
            if (remain <= 0L) {
                break;              // ← 上游原文：额度吃完就把后面的候选整个丢掉
            }
            final long demand = demands[i];
            if (demand <= 0L) {
                continue;
            }
            final long p = Math.min(demand, remain);
            shares[i] = p;
            remain -= p;
        }
        return shares;
    }

    /**
     * <b>加载期自检</b>：把三条不变式 + 与贪心的对照关系一次性断言掉。
     *
     * <p>由 {@code PrimordialModuleMachine} 的加载期自检链调用（与
     * {@link ShanhaiConcurrencyTables#selfTest()} 同一个入口，不新增任何加载钩子）。
     * 任一不成立 ⇒ <b>加载期当场抛异常</b>（本项目反复记录过的最坏失败形态是"静默不对"）。
     *
     * @return 给调用方写日志用的一整行（本类自己不写日志，见类注释）
     */
    public static String selfTest() {
        // ① 正面对照：用户实测那一档（预算 2048×9 = 18432，两条候选都管够）
        assertFair(new long[] { 18432L, 18432L }, 18432L, new long[] { 9216L, 9216L }, "双配方均分（用户实测档）");
        // ② 负面对照：同一批输入走贪心 ⇒ 第二条必须被饿死（证明判据能区分好坏）
        assertShares(greedyShares(new long[] { 18432L, 18432L }, 18432L), 18432L, new long[] { 18432L, 0L },
                "负面对照：贪心把第二条饿死");
        // ③ 恒等：单条候选时公平 == 贪心，逐值相等
        assertFair(new long[] { 18432L }, 18432L, new long[] { 18432L }, "单条候选恒等");
        assertShares(greedyShares(new long[] { 18432L }, 18432L), 18432L, new long[] { 18432L }, "单条候选（贪心侧）");
        // ④ 需求小于保底份额时不多拿
        assertFair(new long[] { 100L, 10L }, 18432L, new long[] { 100L, 10L }, "需求不足时按需取");
        // ⑤ 相 2 水填充：第一条吃不完自己的份额，余量补给还在要的人
        assertFair(new long[] { 100L, 10L }, 50L, new long[] { 40L, 10L }, "水填充补余量");
        // ⑥ 分母口径：demand ≤ 0 的候选仍占名额（上游 limit / recipes.size() 的语义）
        assertFair(new long[] { 0L, 12L, 12L }, 36L, new long[] { 0L, 12L, 12L }, "不可跑的候选占分母名额");
        // ⑦ 预算为 0 / 空输入
        assertFair(new long[] { 5L, 5L }, 0L, new long[] { 0L, 0L }, "预算为 0");
        return "[SHANHAI-FAIR-ALLOC] 公平分配自检通过（正面对照 6 条 + 负面对照 1 条）："
                + "相 1 = min(需求, 预算/条数)、相 2 = 水填充；不饿死、Σp ≤ 预算、单条时与贪心逐值相等。";
    }

    private static void assertFair(long[] demands, long total, long[] expected, String what) {
        assertShares(fairShares(demands, total), total, expected, what);
    }

    private static void assertShares(long[] got, long total, long[] expected, String what) {
        if (got.length != expected.length) {
            throw new IllegalStateException("[SHANHAI-FAIR-ALLOC] 自检失败（" + what + "）：长度 "
                    + got.length + " ≠ " + expected.length);
        }
        long sum = 0L;
        for (int i = 0; i < got.length; i++) {
            if (got[i] != expected[i]) {
                throw new IllegalStateException("[SHANHAI-FAIR-ALLOC] 自检失败（" + what + "）：下标 " + i
                        + " 实得 " + got[i] + "，应为 " + expected[i]);
            }
            sum += got[i];
        }
        if (sum > total) {
            throw new IllegalStateException("[SHANHAI-FAIR-ALLOC] 自检失败（" + what
                    + "）：Σp = " + sum + " 超过预算 " + total);
        }
    }
}
