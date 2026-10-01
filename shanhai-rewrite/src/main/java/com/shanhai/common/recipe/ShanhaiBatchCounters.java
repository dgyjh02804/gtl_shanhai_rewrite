package com.shanhai.common.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 山海重构 · <b>「配方统计」的纯算术核</b>（不 import 任何 Minecraft / Forge / GTCEu / 本工程其它类）。
 *
 * <h2>0. 为什么要单独开这个类（2026-10-01）</h2>
 * 用户 2026-10-01 对着横幅报了 <b>「物质解构: 1193」</b>，而那一批实际只有 <b>1154</b> 条。
 * 根因是<b>算术口径</b>而不是渲染：{@link ShanhaiRecipeStats} 里
 * <pre>
 *   TOTAL / SUCCESS / FAILED  —— 只有 reset() 会清（脚本自己决定调不调）
 *   BY_SCOPE                  —— 在 reportSummary(scope) 里存下"那一刻的 TOTAL"
 * </pre>
 * 而 <b>解构脚本是全工程唯一不调 {@code reset()} 的</b>（取证：
 * {@code kubejs\server_scripts\[server_scripts]shanhai_deconstruct_recipes.js} 全文没有
 * {@code ShanhaiStats.reset()}，另外两个脚本都有）⇒ 它上报时 {@code TOTAL} 里还留着别的脚本加过的数
 * ⇒ 「本批条数」被算成了「别人的 + 自己的」。
 *
 * <h2>1. 🔴 本类的口径（三条，逐条写死）</h2>
 * <ol>
 *   <li><b>累计三个数</b>（{@code total/success/failed}）＝「自上次 {@link #reset()} 以来累加了多少条」
 *       —— <b>与改动前逐字节同义</b>，日志行里那几个字段的口径因此没变；</li>
 *   <li><b>{@code lifetime}</b> ＝ 自开服以来累计上报条数，{@link #reset()} <b>不清它</b>（既有设计，用户明确要求保持）；</li>
 *   <li><b>本批条数</b>（{@link #closeBatch(String)} 的返回值）＝ <b>本次上报时的累计 − 上次上报（或上次清零）时的累计</b>，
 *       并<b>钳到 ≥ 0</b>。⇒ 不调 {@code reset()} 的那一批也能算出自己的真实条数。</li>
 * </ol>
 *
 * <h3>1.1 为什么"差值"这条路成立（三种脚本组合都推过）</h3>
 * <pre>
 *   A 不 reset：TOTAL 0 → N₁ ；上报 ⇒ 本批 = N₁ − 0 = N₁ ✓ ；记住 LAST = N₁
 *   B 先 reset：LAST 被 reset 置 0 ；TOTAL → N₂ ；上报 ⇒ 本批 = N₂ − 0 = N₂ ✓ ；LAST = N₂
 *   C 不 reset 且排在 B 之后：TOTAL 从 N₂ → N₂+N₃ ；上报 ⇒ 本批 = (N₂+N₃) − N₂ = N₃ ✓
 * </pre>
 * ⇒ <b>「reset 的那几批」与「不 reset 的那一批」混在任何顺序里都对</b>。
 * ⚠️ <b>前置假设（如实写明）</b>：每个脚本<b>只上报一次</b>（现状如此：三个脚本各一次
 * {@code reportSummary}）。同一个脚本连续上报两次时，"本批"会退化成"自上次上报以来的增量" —— 那也是同一件事。
 *
 * <h2>2. 🔴 为什么不做"方向甲"（让 KJS 显式传本批条数）</h2>
 * 方向甲必然要么改 KubeJS 脚本（每批开头补一次 {@code reset()}）、要么加一个
 * {@code reportSummaryBatch(scope, count)} 让脚本多传一个参数。
 * <b>两条都要求改 {@code kubejs\**}</b> —— 而那一片在本轮任务书里是<b>别的线的地盘（明令不许碰）</b>。
 * ⇒ 本类选了<b>纯 Java 侧自洽</b>的那条路（方向乙），它不需要任何脚本改动，也不会与别的线冲突。
 * 另：本工程铁律「凡会被 KubeJS 调用的 Java 方法一律<b>不留重载</b>」（Rhino 在重载上会
 * {@code InternalError} 静默毁掉整批配方）⇒ 本类<b>一个重载都没有</b>，
 * {@code ShanhaiRecipeStats} 的对外方法名/签名也<b>一个都没改</b>。
 *
 * <h2>3. 纯类的意义</h2>
 * 本类<b>只 import {@code java.util.*} / {@code java.util.concurrent.*}</b> ⇒ 可以离线
 * {@code javac} + {@code java} 跑三段自证（正常／预期失败／复原），
 * 而且自证用的是<b>本类的一个独立实例</b>（{@link #selfTest()}），<b>不会污染线上计数器</b>。
 */
public final class ShanhaiBatchCounters {

    /** 线上唯一实例（KJS 侧的一切调用最终都落到它身上）。 */
    private static final ShanhaiBatchCounters INSTANCE = new ShanhaiBatchCounters();

    /** 线上实例。 */
    public static ShanhaiBatchCounters get() {
        return INSTANCE;
    }

    // ────────────────────────────── 状态（全部是实例字段 ⇒ 可离线造干净实例） ──────────────────────────────

    private final AtomicLong total = new AtomicLong();
    private final AtomicLong success = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    /** 自开服以来的累计上报条数。<b>{@link #reset()} 刻意不清它。</b> */
    private final AtomicLong lifetime = new AtomicLong();

    /** 上一次上报（或上一次清零）时的累计值 —— 「本批 = 现在 − 它」的那一半。 */
    private final AtomicLong lastTotal = new AtomicLong();
    private final AtomicLong lastSuccess = new AtomicLong();
    private final AtomicLong lastFailed = new AtomicLong();

    /** scope → {本批条数, 本批成功, 本批失败}。每次 {@link #closeBatch(String)} 覆盖写。 */
    private final Map<String, long[]> byScope = new ConcurrentHashMap<>();

    // ────────────────────────────── 写入 ──────────────────────────────

    /**
     * 清零「当前这一批」的累计，并<b>把"上次上报时的累计"也一起归零</b>。
     *
     * <p>后半句是本轮修复的关键：脚本先 reset 再累加时，{@code total} 从 0 起算，
     * 若 {@code lastTotal} 还留着上一批的值，差值就会变成负数 ⇒ 钳成 0 ⇒ 那一批显示 0 条。
     * 归零之后，「reset 的批」与「不 reset 的批」共用同一个差值公式。
     */
    public void reset() {
        total.set(0L);
        success.set(0L);
        failed.set(0L);
        lastTotal.set(0L);
        lastSuccess.set(0L);
        lastFailed.set(0L);
        // 🔴 刻意【不清】lifetime —— 它跨批次累计，是"总数行顺序无关"的基础（既有设计）。
    }

    /** 上报<b>一条</b>配方的结果。全工程唯一的计数入口（单一数据源）。 */
    public void addResult(boolean ok) {
        total.incrementAndGet();
        lifetime.incrementAndGet();
        if (ok) {
            success.incrementAndGet();
        } else {
            failed.incrementAndGet();
        }
    }

    // ────────────────────────────── 读取 ──────────────────────────────

    public long total() {
        return total.get();
    }

    public long success() {
        return success.get();
    }

    public long failed() {
        return failed.get();
    }

    public long lifetime() {
        return lifetime.get();
    }

    /**
     * <b>收一批</b>：算出这一批自己的真实条数，留档，并把"上次上报值"推进到当前。
     *
     * @param scope 这一批的 scope（null/空 ⇒ {@code "unknown"}，与日志行同一口径）
     * @return {@code {本批条数, 本批成功, 本批失败}}（三个都钳到 ≥ 0）
     */
    public long[] closeBatch(String scope) {
        final String key = (scope == null || scope.isEmpty()) ? "unknown" : scope;
        final long t = total.get();
        final long s = success.get();
        final long f = failed.get();
        final long batch = Math.max(0L, t - lastTotal.get());
        final long batchOk = Math.max(0L, s - lastSuccess.get());
        final long batchBad = Math.max(0L, f - lastFailed.get());
        lastTotal.set(t);
        lastSuccess.set(s);
        lastFailed.set(f);
        final long[] row = new long[] {batch, batchOk, batchBad};
        byScope.put(key, row);
        return row;
    }

    /** 已留档的 scope 快照（顺序 = 字典序，保证两次运行一致、便于比对）。 */
    public List<String> scopes() {
        final List<String> out = new ArrayList<>(byScope.keySet());
        out.sort(String::compareTo);
        return out;
    }

    /** 该 scope 上一次 {@link #closeBatch(String)} 留档的三个数；没留过档 ⇒ {@code null}。 */
    public long[] batchOf(String scope) {
        return byScope.get(scope);
    }

    // ────────────────────────────── 加载期自检 ──────────────────────────────

    /**
     * <b>自检（在独立实例上跑，不碰线上计数器）</b>。
     *
     * <p>用例集合刻意包含<b>用户这一轮报的那个缺陷场景</b>：
     * 「先跑一批 39（reset 过）⇒ 再跑一批 1154（<b>不 reset</b>）⇒ 上报后者」
     * ⇒ 期望后者 = <b>1154</b>，而改动前的实现会给出 <b>1193</b>。
     *
     * @return 一行可 grep 的结论（<b>本类自己不写日志</b> —— 保持"纯类"这条边界）
     * @throws IllegalStateException 任一条不成立（加载期响亮失败，而不是静默算错）
     */
    public static String selfTest() {
        // ── 用独立实例，绝不污染线上 ──
        final ShanhaiBatchCounters c = new ShanhaiBatchCounters();

        // 用例①：一批 39 条，正常 reset 流程
        c.reset();
        for (int i = 0; i < 39; i++) {
            c.addResult(true);
        }
        long[] a = c.closeBatch("test");
        check(a[0] == 39L, "用例①本批条数", 39L, a[0]);
        check(a[1] == 39L, "用例①本批成功", 39L, a[1]);
        check(a[2] == 0L, "用例①本批失败", 0L, a[2]);

        // 用例②：再一批 1154 条，同样 reset
        c.reset();
        for (int i = 0; i < 1154; i++) {
            c.addResult(true);
        }
        long[] b = c.closeBatch("pf");
        check(b[0] == 1154L, "用例②本批条数", 1154L, b[0]);

        // 用例③：🔴 缺陷场景 —— **不 reset** 的一批 1154 条（排在别人之后）
        //   改动前：BY_SCOPE 存的是 TOTAL = 39 + 1154 = 1193 ✗
        //   改动后：差值 = 1193 − 39 = 1154 ✓
        for (int i = 0; i < 1154; i++) {
            c.addResult(true);
        }
        long[] d = c.closeBatch("shanhai_deconstruct");
        check(d[0] == 1154L, "用例③不 reset 的那批", 1154L, d[0]);

        // 用例④：lifetime 仍是累加（39 + 1154 + 1154 = 2347），reset 不清它
        check(c.lifetime() == 2347L, "用例④终身累计", 2347L, c.lifetime());

        // 用例⑤：失败数也按批算（先 3 失败，再 reset 后 2 失败 ⇒ 第二批只该报 2）
        c.reset();
        c.addResult(false);
        c.addResult(false);
        c.addResult(false);
        long[] e1 = c.closeBatch("f1");
        check(e1[2] == 3L, "用例⑤第一批失败", 3L, e1[2]);
        c.reset();
        c.addResult(false);
        c.addResult(false);
        long[] e2 = c.closeBatch("f2");
        check(e2[2] == 2L, "用例⑤第二批失败（不该把上一批的算进来）", 2L, e2[2]);

        // 用例⑥：负面对照 —— 没留过档的 scope 必须是 null（不许编一个 0 出来）
        if (c.batchOf("从来没上报过的 scope") != null) {
            throw new IllegalStateException("[SHANHAI-SPEC] 自检失败：未上报过的 scope 竟然有留档");
        }

        // 用例⑦：scope 为 null/空 ⇒ 落成 "unknown"（与日志行同口径）
        final long[] u = c.closeBatch(null);
        if (c.batchOf("unknown") == null || c.batchOf("unknown") != u) {
            throw new IllegalStateException("[SHANHAI-SPEC] 自检失败：null scope 没有落成 unknown");
        }

        // 用例⑧：reset() 必须把「上次上报值」也归零（否则 reset 批会算出负数并被钳成 0）
        final ShanhaiBatchCounters z = new ShanhaiBatchCounters();
        for (int i = 0; i < 100; i++) {
            z.addResult(true);
        }
        z.closeBatch("x");
        z.reset();
        for (int i = 0; i < 7; i++) {
            z.addResult(true);
        }
        final long[] zy = z.closeBatch("y");
        check(zy[0] == 7L, "用例⑧reset 之后那一批（不许被上批污染）", 7L, zy[0]);

        return "[SHANHAI-SPEC] batch_counters_selftest ok cases=8"
                + "（①39 ②1154 ③不 reset 的那批=1154 ④lifetime=2347 ⑤按批失败 ⑥未上报=null ⑦null→unknown ⑧reset 后 7）";
    }

    private static void check(boolean cond, String what, long expected, long actual) {
        if (!cond) {
            throw new IllegalStateException("[SHANHAI-SPEC] 自检失败（" + what + "）：应为 "
                    + expected + "，实为 " + actual);
        }
    }

    /** 给日志用的一行 scope 摘要（形如 {@code test=39 / pf=1154}），便于人读与 grep。 */
    public String summary() {
        final StringBuilder sb = new StringBuilder();
        for (String s : scopes()) {
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            final long[] v = byScope.get(s);
            sb.append(s).append('=').append(v == null ? "?" : String.valueOf(v[0]));
        }
        return sb.length() == 0 ? "(还没上报过任何批次)" : sb.toString();
    }
}
