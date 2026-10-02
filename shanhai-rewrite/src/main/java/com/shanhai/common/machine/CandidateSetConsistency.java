package com.shanhai.common.machine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 🔴 <b>「同一轮两次取候选，拿到的是不是同一个集合」的<b>运行期</b>断言</b>
 * （2026-10-02 第十轮 · 用户裁决：「既然识别出来了 ⇒ 就钉住它」）。
 *
 * <h2>要钉住的那个口子（逐字来源）</h2>
 * 上一轮交接单 §4.4 自述：
 * <blockquote>「未证实：上游 {@code lookupRecipeSet()} 在模块侧被截断之后，截断用的迭代顺序与
 * {@code shanhai$updatePowerParallelCap()} 那次调用是否逐次一致。同一轮里 {@code lookupRecipeSet()}
 * 会被调<b>两次</b>（算电上限一次、分配并行一次）。… 若两次返回的候选集不同，
 * <b>k最贵 可能没覆盖到真被摊到的那条</b> ⇒ 这是本轮唯一一条『理论上仍能超功率』的口子
 * （除已知地板档）。」</blockquote>
 *
 * <h2>为什么真的会不一样（机制上的理由，不是抽象担心）</h2>
 * 模块侧的候选集是<b>截断</b>出来的（{@code threads} 条），截断走的是上游那台
 * {@code RecipeIterator}；而两次调用之间夹着 {@code checkRecipe}（电压闸门、{@code matchRecipe}、
 * {@code checkConditions}）与别处的机器状态读取。任何一次"遍历顺序/谓词结果变了"，
 * 都会让第 2 次拿到的集合与第 1 次不同，而 {@code k最贵} 是用第 1 次算的。
 * 上游返回的是哈希集 ⇒ <b>顺序本身与配方优先级无关</b>（见模块侧 {@code lookupRecipeSet()} 的 javadoc），
 * 所以"哪一条被取到"这件事在这里尤其脆弱。
 *
 * <h2>为什么是<b>运行期</b>断言而不是加载期自检（用户点名要选对位置）</h2>
 * 「两次调用」这件事在加载期<b>造不出来</b>：加载期没有机器、没有候选集、没有那台迭代器。
 * ⇒ 只能在真有配方在跑的时候观察。代价是"没有崩溃就看不见" ⇒ 所以：
 * <ul>
 *   <li><b>一致</b>（正常）：把 {@link #checks()} 加 1，<b>不打告警</b>；</li>
 *   <li><b>不一致</b>：打<b>一行</b>带 {@link #PREFIX} 前缀的 WARN（可 grep），并按"不一致的形态"
 *       去重（同形只打一次，与工程既有几处探针同一条纪律），行里带上累计比对次数 / 不一致次数。</li>
 * </ul>
 *
 * <h2>🔴 2026-10-02 第十一轮：把计数器【暴露出来】（起因是队长读实机日志）</h2>
 * 上面那套设计有一个致命的可观测性缺口：<b>"一致时不打印"⇒ 日志里 {@link #PREFIX} 0 行</b>，
 * 于是<b>分不清</b>这两种情况 ——
 * <ul>
 *   <li>比较了很多次、每次都一致（<b>好事</b>）；</li>
 *   <li>一次都没比较过（探针没跑到 ⇒ <b>等于没验证</b>）。</li>
 * </ul>
 * 这正好踩在本工程的硬规矩上：<b>「0 必须能区分『没跑到』和『确实没有』」</b>。
 * ⇒ 补三个数（{@link #rounds()} / {@link #fetches()} / {@link #noComparisonRounds()}）
 * 与一条<b>低频</b>累计值心跳（{@link #ROSTER_INTERVAL_MILLIS} 最多一行，见 {@link #roster()}）：
 * <b>首次必打（哪怕计数全是 0）+ 之后每 {@value #ROSTER_INTERVAL_MILLIS} 毫秒最多一行</b>。
 * 判据：看到心跳里的计数就知道探针<b>真跑过 / 真比过</b>；"0 行告警"不再有多种解释。
 *
 * <h2>本类刻意<b>零 Minecraft 依赖</b></h2>
 * 只吃"配方 id 字符串"，不许 import 任何 MC / Forge / GTCEu 类型 ⇒ 整台探针
 * （含计数器与去重）可以在离线判据里被<b>正面 + 负面对照</b>各跑一遍
 * （见 {@code temp/autoparallel-fix9/judge/MultiCandidateJudge.java} 的 G 段）。
 * 本工程血规：「凡自己写的检查器，采信前必须先证明它自己是对的」——
 * 把探针做成离线可跑，就是为了让这条血规能落在它身上。
 *
 * <h2>线程模型</h2>
 * 观察窗与"第一次读到的集合"落在 {@link ThreadLocal} 上 ⇒ 别的线程（哪怕同时在跑同一台机器）
 * 不会被算进这一轮，<b>不会造出假的不一致</b>。
 * 计数器是全局的 {@link AtomicLong}（跨机器累加，供取证）。
 */
public final class CandidateSetConsistency {

    /** 可 grep 的前缀（用户点名的形状）。 */
    public static final String PREFIX = "[SHANHAI-WORST-COST]";

    /** 不一致时的告警出口（生产侧传 {@code ShanhaiMod.LOGGER::warn}；判据侧传一个收集器）。 */
    public interface Sink {
        void mismatch(String line);
    }

    /**
     * 🔴 <b>累计值心跳的出口</b>（2026-10-02 第十一轮新增）。
     *
     * <p>为什么不复用 {@link Sink}：两者<b>级别不同</b> —— 不一致是 {@code WARN}（= 要修的 bug），
     * 心跳是 {@code INFO}（= 正常取证）。合成一个接口就没法在出口处分级别，而
     * <b>"把心跳打成告警"比不打更糟</b>（会让下一次读日志的人以为出了事）。
     */
    public interface Heartbeat {
        void line(String text);
    }

    /** 一个观察窗（= 一次 {@code calculateParallels()} 调用）。 */
    private static final class Round {
        boolean active;
        int calls;
        String firstSignature;
        List<String> firstIds = List.of();
    }

    private static final ThreadLocal<Round> ROUND = ThreadLocal.withInitial(Round::new);

    /** 真正做过比较的次数（= 「第 2 次及以后」的调用次数）。 */
    private static final AtomicLong CHECKS = new AtomicLong();
    /** 其中判为不一致的次数。 */
    private static final AtomicLong MISMATCHES = new AtomicLong();

    // ═════════ 2026-10-02 第十一轮：把"跑没跑过"变成可解释的三个数 ═════════
    //
    // 🔴 起因（队长读实机日志发现）：`[SHANHAI-WORST-COST]` 在日志里【0 行】，
    //    而本探针的设计就是"一致时不打印" ⇒ 于是【分不清】"比过很多次都一致" 与 "一次都没比过"。
    //    ⇒ 补下面三个数 + 一条低频心跳，让 0 也能自我解释。

    /**
     * 观察窗开了多少次（= {@code calculateParallels()} 被调了多少次）。
     * <b>为 0 ⇒ 探针一次都没跑到</b>（本机没跑过原初机器 / 没挂上）。
     */
    private static final AtomicLong ROUNDS = new AtomicLong();

    /** 观察窗内【取到候选并喂给探针】的总次数（含每轮只取到一次的那些）。 */
    private static final AtomicLong FETCHES = new AtomicLong();

    /**
     * 其中<b>只取到 ≤1 次候选</b>的轮数 —— 这些轮<b>没有可比对的对象</b>。
     *
     * <p>它就是"为什么 {@link #checks()} 可能是 0"的那个解释：探针跑了、窗口也开了，
     * 但这一轮只取到一次候选（线程槽空 / 开关关着 / 只剩一条可跑配方）⇒ 第 2 次取候选根本不存在，
     * 无从比较。<b>它和"探针没跑到"是两件完全不同的事</b>，必须能分开。
     */
    private static final AtomicLong NO_COMPARISON_ROUNDS = new AtomicLong();

    /**
     * 🔴 <b>心跳行的最小间隔</b>：两次心跳之间至少隔这么久（= "低频"的那把尺子）。
     *
     * <p>5 分钟：配方逻辑一秒会开很多次观察窗，不设间隔就是刷屏（本工程吃过刷屏的亏）；
     * 而 5 分钟又足够短 —— 用户跑一次机器，短时间内就能拿到一行可贴的证据。
     */
    public static final long ROSTER_INTERVAL_MILLIS = 300_000L;

    /** "从没打过心跳"的哨兵值（用它把「首次必打」与「距上次不足间隔」区分开）。 */
    private static final long NEVER_ROSTERED = Long.MIN_VALUE;

    private static final AtomicLong LAST_ROSTER_MILLIS = new AtomicLong(NEVER_ROSTERED);

    /** 时钟（可注入 ⇒ 离线判据能<b>确定性地</b>证明"间隔内静默 / 超时必打"，不必真等 5 分钟）。 */
    private static volatile LongSupplier clock = System::currentTimeMillis;

    /** 已打过告警的"不一致形态"（防刷屏；超过上限后不再打，但计数继续）。 */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static final int MAX_LOGGED = 256;
    /** 告警行里逐条列出的 id 上限（其余折叠成"…"）。 */
    private static final int MAX_IDS_IN_LINE = 6;

    private CandidateSetConsistency() {}

    // ═════════════════════════════ 观察窗 ═════════════════════════════

    /** 进入一轮（在 {@code calculateParallels()} 的<b>最开头</b>调）。 */
    public static void beginRound() {
        ROUNDS.incrementAndGet();
        final Round r = ROUND.get();
        r.active = true;
        r.calls = 0;
        r.firstSignature = null;
        r.firstIds = List.of();
    }

    /** 离开一轮（在 {@code calculateParallels()} 的 {@code finally} 里调）。只更新计数，不打日志。 */
    public static void endRound() {
        endRound(null);
    }

    /**
     * 🔴 离开一轮，并<b>顺便决定要不要打一行累计值心跳</b>（第十一轮新增）。
     *
     * <p>心跳的落点为什么选在这里：这一轮刚刚结束，{@link #rounds()} 等计数是"完整一轮"的读数；
     * 而且本方法只有真正跑过机器才会被调到 ⇒ 心跳<b>天然只在有机器跑的时候出现</b>（不会开机就刷）。
     *
     * @param heartbeat 心跳出口；{@code null} = 只更新计数、不打日志（离线判据用）
     */
    public static void endRound(Heartbeat heartbeat) {
        final Round r = ROUND.get();
        // ⚠️ 必须在清零【之前】读 r.calls：它决定"这一轮有没有可比对的对象"。
        final boolean wasActive = r.active;
        final int calls = r.calls;
        if (wasActive && calls <= 1) {
            // 这一轮的候选只取到 ≤1 次 ⇒ 没有"第 2 次"可与之比对（checks 不动）。
            NO_COMPARISON_ROUNDS.incrementAndGet();
        }
        r.active = false;
        r.calls = 0;
        r.firstSignature = null;
        r.firstIds = List.of();
        if (heartbeat != null && claimRosterSlot(clock.getAsLong())) {
            heartbeat.line(roster());
        }
    }

    /** 当前线程是否在观察窗里（调用方据此跳过"造指纹"的开销 —— 窗口外一次字符串都不建）。 */
    public static boolean isRoundActive() {
        return ROUND.get().active;
    }

    /**
     * 🔴 把<b>刚刚取到的候选集</b>喂给探针。
     *
     * <p>第 1 次调用 = 记下指纹；第 2 次及以后 = 逐个比对。
     * 一致 ⇒ 只把 {@link #checks()} 加 1；不一致 ⇒ 再 {@link #mismatches()} 加 1 并打<b>一行</b>告警。
     *
     * @param side      是哪一侧（"模块" / "主机"），只用于告警行的可读性
     * @param machine   机器定义 id（{@code shanhai:xxx}），只用于告警行
     * @param recipeIds 本次候选集里每一条配方的 id（{@code GTRecipe#id} 的字符串形式）
     * @param sink      告警出口；为 {@code null} 时只计数不打日志（离线判据用）
     */
    public static void observe(String side, String machine, Collection<String> recipeIds, Sink sink) {
        final Round r = ROUND.get();
        if (!r.active) {
            // 观察窗之外（例如父类在别的时机自己取了一次候选）⇒ 不参与断言。
            return;
        }
        final List<String> ids = sortedIds(recipeIds);
        final String signature = signatureOfSorted(ids);
        r.calls++;
        FETCHES.incrementAndGet();
        if (r.calls == 1) {
            r.firstSignature = signature;
            r.firstIds = ids;
            return;
        }
        final long checks = CHECKS.incrementAndGet();
        if (signature.equals(r.firstSignature)) {
            return;                                  // ✅ 一致：只在计数里加 1，不打印
        }
        final long mismatches = MISMATCHES.incrementAndGet();
        final String shape = r.firstSignature + " => " + signature;
        if (sink == null || LOGGED.size() > MAX_LOGGED || !LOGGED.add(shape)) {
            return;                                  // 只计数（同形不重复刷屏）
        }
        sink.mismatch(PREFIX + " 🔴 同一轮两次取候选【不一致】！" + side + "「" + machine + "」："
                + "第 1 次 n=" + r.firstIds.size() + " " + show(r.firstIds)
                + " ≠ 第 " + r.calls + " 次 n=" + ids.size() + " " + show(ids)
                + "（本探针累计比对 " + checks + " 次 / 不一致 " + mismatches + " 次）"
                + " ⇒ 「k最贵」可能没覆盖到真被摊到的那条 ⇒ 这不是'记一笔'，是要修的 bug。");
    }

    // ═════════════════════════════ 读数 ═════════════════════════════

    /** 真正做过比较的次数（一致与不一致都算）。 */
    public static long checks() {
        return CHECKS.get();
    }

    /** 判为不一致的次数。 */
    public static long mismatches() {
        return MISMATCHES.get();
    }

    /** 观察窗开了多少次。为 0 ⇒ 探针一次都没跑到。 */
    public static long rounds() {
        return ROUNDS.get();
    }

    /** 观察窗内取到候选的总次数。 */
    public static long fetches() {
        return FETCHES.get();
    }

    /** 只取到 ≤1 次候选（= 没有可比对的对象）的轮数。 */
    public static long noComparisonRounds() {
        return NO_COMPARISON_ROUNDS.get();
    }

    /**
     * 🔴 <b>可 grep 的累计值取证行（心跳）</b>—— 本行的全部意义是让
     * <b>"0 行告警"变得可解释</b>：看到这三个数就知道探针真跑过、真比过，
     * 而不是"没跑到"和"确实没有"分不清。
     *
     * <p>由 {@link #endRound(Heartbeat)} 以 {@value #ROSTER_INTERVAL_MILLIS} 毫秒为间隔打出
     * （且<b>首次必打，哪怕计数全是 0</b>）。
     */
    public static String roster() {
        final long rounds = ROUNDS.get();
        final long fetches = FETCHES.get();
        final long compares = CHECKS.get();
        final long mismatches = MISMATCHES.get();
        final long noComparison = NO_COMPARISON_ROUNDS.get();
        return PREFIX + " 取证心跳（本行【不是告警】，每 " + (ROSTER_INTERVAL_MILLIS / 1000L) + " 秒最多一行）："
                + "观察窗 " + rounds + " 轮 / 取候选 " + fetches + " 次 / 真比较 " + compares + " 次 / 不一致 "
                + mismatches + " 次 / 没有第二次取候选的轮数 " + noComparison
                + "。判读："
                + "【真比较 >0 且不一致 0】= 比过的每一次两次取候选都同集合（好事）；"
                + "【真比较 0 且『没有第二次取候选的轮数』= 观察窗轮数】= 探针跑过、但每轮只取到一次候选，没有可比对的对象；"
                + "【观察窗 0 轮】= 探针一次都没跑到（这台机器没跑过原初机器 / 探针没挂上）。";
    }

    /** 注入时钟（<b>仅供离线判据</b>；传 {@code null} 恢复系统时钟）。 */
    public static void setClockForTest(LongSupplier supplier) {
        clock = (supplier == null) ? System::currentTimeMillis : supplier;
    }

    /**
     * 抢占"这一行心跳该不该打"的槽位（CAS ⇒ 多线程下也不会重复打）。
     *
     * <p>判据只有两条：<b>从没打过 ⇒ 打</b>（保证 0 也能出现在日志里）；
     * <b>距上次 ≥ {@link #ROSTER_INTERVAL_MILLIS} ⇒ 打</b>。其余一律静默。
     */
    private static boolean claimRosterSlot(long now) {
        while (true) {
            final long last = LAST_ROSTER_MILLIS.get();
            // ⚠️ NEVER_ROSTERED 是 Long.MIN_VALUE，直接做减法会溢出 ⇒ 必须先判哨兵值。
            if (last != NEVER_ROSTERED && now - last < ROSTER_INTERVAL_MILLIS) {
                return false;
            }
            if (LAST_ROSTER_MILLIS.compareAndSet(last, now)) {
                return true;
            }
        }
    }

    /** 清空计数器与去重表（<b>仅供离线判据</b>；生产代码不许调）。 */
    public static void resetForTest() {
        // ⚠️ 先清线程本地状态再清零计数 —— endRound() 自己会加计数，所以它必须排在清零【之前】。
        endRound();
        CHECKS.set(0L);
        MISMATCHES.set(0L);
        ROUNDS.set(0L);
        FETCHES.set(0L);
        NO_COMPARISON_ROUNDS.set(0L);
        LAST_ROSTER_MILLIS.set(NEVER_ROSTERED);
        LOGGED.clear();
    }

    // ═════════════════════════════ 指纹 ═════════════════════════════

    /**
     * 集合指纹（{@link ParallelPowerBudget#candidateSetSignature(Collection)} 的同一份口径）——
     * 去重 + 排序 + 用不可见分隔符连接 ⇒ <b>与遍历顺序无关</b>（集合语义）。
     */
    private static String signatureOfSorted(List<String> sorted) {
        return ParallelPowerBudget.candidateSetSignature(sorted);
    }

    private static List<String> sortedIds(Collection<String> recipeIds) {
        if (recipeIds == null || recipeIds.isEmpty()) {
            return List.of();
        }
        final TreeSet<String> sorted = new TreeSet<>();
        for (String id : recipeIds) {
            sorted.add(id == null ? "<null>" : id);
        }
        return new ArrayList<>(sorted);
    }

    private static String show(List<String> ids) {
        if (ids.isEmpty()) {
            return "[]";
        }
        final StringBuilder sb = new StringBuilder("[");
        final int shown = Math.min(ids.size(), MAX_IDS_IN_LINE);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        if (ids.size() > shown) {
            sb.append(",…共").append(ids.size()).append("条");
        }
        return sb.append(']').toString();
    }
}
