package com.shanhai.common.log;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 🔴 <b>「正常不刷屏、异常照样看得见」的日志闸门（2026-10-01，用户点单「日志的问题」）。</b>
 *
 * <h2>它解决的是什么</h2>
 * 2026-10-01 用户实测日志：{@code latest.log} 共 <b>9431 行</b>，其中本工程六类对账行
 * <b>4311 行</b>是同一台机器（{@code pos=BlockPos{x=0, y=108, z=25}}，发电模块）打出来的。
 * 这些行的<b>本意是对账</b> —— 对账通过本来就<b>不需要</b>打；只有「不通过 / 状态没告知过」时才该打。
 *
 * <h2>本类的形态（三条硬约束，逐条对齐用户口径）</h2>
 * <ol>
 *   <li><b>正常 / 已经告知过的形态 ⇒ 不打。</b></li>
 *   <li><b>形态第一次出现 ⇒ 打一次</b>（这就是「状态变化时打一次 / 首次进入 long 档 ⇒ 打一行」）。</li>
 *   <li><b>异常 ⇒ 当场打，且级别 ≥ WARN</b>（本工程从不把 WARN / ERROR 降级成 INFO ——
 *       改动只发生在「INFO 的常态行」上，WARN / ERROR 的行只是<b>加了去重</b>，级别一个都没降）。</li>
 * </ol>
 *
 * <h2>🔴 为什么判定逻辑必须住在这个「纯 JDK」类里（而不是散在各调用点）</h2>
 * 本项目纪律「凡我自己写的检查脚本报出的结果，采信前必须先证明脚本自己是对的」。
 * 若把判定写在带 Minecraft 依赖的类里，离线的复现脚本只能<b>照抄一遍</b>判定 —— 那就变成
 * 「抄错的是脚本还是生产代码」无法分辨（2026-09-20 同族撞过 7 次，全是<b>检查器自己</b>的假设错了）。
 * ⇒ 本类<b>零 Minecraft / GT / Forge 依赖</b>，{@code javac --release 17} 可单独编译并跑
 * {@link #selfTest()}；<b>游戏里跑的是同一份字节码</b>，不存在第二份判定。
 *
 * <h2>🔴 为什么是「有界形态集合」而不是「只记住上一次」</h2>
 * 2026-10-01 离线回放实测：{@code realParallels} 探针的形态在真实日志里是
 * {@code p=320 / p=64 / p=9216} <b>三种值交替出现</b>（507 条）。
 * 「只记住上一次」的闸门在这种情况下会退化成「每 5 秒一行」（离线回放 <b>96 行</b>），
 * 而「同一形态只打一次」的闸门给出 <b>3 行</b>。
 * ⇒ 语义上正确的是后者：<b>一个形态<b>已经打出去过</b>，就等于已经如实告知过，不必再说。</b>
 *
 * <h2>并发</h2>
 * 真实日志里这些行同时来自 {@code Server thread} 与 {@code Render thread}
 * （{@code [0110月2026 19:54:43.275] [Render thread/INFO] ... [SHANHAI-DURATION-FLOOR] ...}）
 * ⇒ 闸门内部<b>全部 synchronized</b>，不引入新的竞争。
 */
public final class ShanhaiLogThrottle {

    private ShanhaiLogThrottle() {}

    // ═══════════════════════════════ 基本类型 ═══════════════════════════════

    /** 该打出去的级别；{@link #NONE} = 本次不打（静默）。 */
    public enum Level {
        /** 不打（这是本次改动的<b>主要产出</b>：正常 / 已告知过的形态 ⇒ 一行都不落盘）。 */
        NONE,
        /** 状态变化（首次进入 / 退出 long 档等）。 */
        INFO,
        /** 异常，但可以继续（对账不一致之外的：入池未落账、撞顶落在预期外……）。 */
        WARN,
        /** 异常，且是「值=1 的行为与今天分叉」这一档（与改动前的级别一致，<b>没有降级</b>）。 */
        ERROR
    }

    /** 一次判定的结果。 */
    public static final class Verdict {

        /** 该打的级别。 */
        public final Level level;
        /** 本次要在行末附上的「自上次落盘以来同形重复 N 次已静默」。 */
        public final long suppressed;
        /** true = 异常档（调用方据此选 INFO / WARN / ERROR）。 */
        public final boolean abnormal;
        /** 本次判定用的形态串（可 grep 的稳定标识；{@link Level#NONE} 时为 {@code null}）。 */
        public final String shape;

        Verdict(Level level, long suppressed, boolean abnormal, String shape) {
            this.level = level;
            this.suppressed = suppressed;
            this.abnormal = abnormal;
            this.shape = shape;
        }

        @Override
        public String toString() {
            return "Verdict{level=" + level + ", suppressed=" + suppressed + ", abnormal=" + abnormal
                    + ", shape=" + shape + '}';
        }
    }

    private static final Verdict SILENT = new Verdict(Level.NONE, 0L, false, null);

    /**
     * 异常档复述的最小间隔（毫秒）：同一个<b>已经打出去过</b>的异常形态，最多每 5 秒复述一次
     * ——「坏了」停不下来时也不会刷屏，但也不会只说过一次就沉底。
     */
    public static final long DEFAULT_MIN_INTERVAL_MILLIS = 5_000L;

    // ═══════════════════════════════ 状态闸门 ═══════════════════════════════

    /**
     * <b>状态闸门</b>：记住「哪些形态已经打出去过」。
     *
     * <pre>
     *   形态没打过       ⇒ 打（并记住它）                 ← 「状态变化时打一次」
     *   形态已经打过     ⇒ 静默（只累计 suppressed）      ← 「正常时不打」
     *   异常形态已打过   ⇒ 静默，但每 minInterval 复述一次 ← 「坏了别只喊一次」
     * </pre>
     *
     * <p>🔴 <b>输出量有界</b>：每个闸门为「正常（非异常）」行最多落
     * {@value #MAX_DISTINCT_SHAPES} 条 —— 形态再怎么变，正常行也不会刷屏。
     * 异常行（{@code urgent}）不受这个上限约束（<b>异常必须看得见</b>是更高优先级的硬约束）。
     */
    public static final class Gate {

        /** 每个闸门为「正常行」保留的形态上限（= 正常行落盘条数的硬上限）。 */
        public static final int MAX_DISTINCT_SHAPES = 64;

        private final Map<String, Long> seen = new LinkedHashMap<>();
        private String lastShape;
        private long lastLogMillis;
        private long lastTouchMillis;
        private long suppressed;

        /**
         * @param shape             本次的形态串
         * @param urgent            true = 异常档（不受「正常行条数上限」约束，且已告知过的形态会按间隔复述）
         * @param nowMillis         当前毫秒（由调用方给，便于离线复现时喂假时间）
         * @param minIntervalMillis 异常形态复述的最小间隔
         * @return true = 该打
         */
        public synchronized boolean changed(String shape, boolean urgent, long nowMillis, long minIntervalMillis) {
            if (shape == null) {
                suppressed++;
                return false;
            }
            final Long seenAt = seen.get(shape);
            if (seenAt != null) {
                if (urgent && nowMillis - seenAt >= minIntervalMillis) {
                    // 异常档：复述（同一件事别只喊一次就沉底）
                    seen.put(shape, nowMillis);
                    lastShape = shape;
                    lastLogMillis = nowMillis;
                    return true;
                }
                suppressed++;
                return false;
            }
            if (!urgent && seen.size() >= MAX_DISTINCT_SHAPES) {
                // 安全阀：正常行落盘条数上限。再新的正常形态只计数、不落盘（读数仍在下一条异常行里报出）。
                suppressed++;
                return false;
            }
            seen.put(shape, nowMillis);
            lastShape = shape;
            lastLogMillis = nowMillis;
            return true;
        }

        /** 形态一变就打（不过滤间隔）。 */
        public synchronized boolean changed(String shape, long nowMillis) {
            return changed(shape, true, nowMillis, 0L);
        }

        /** 只记「又发生了一次」，从来不表示「该打」（用于「正常入池」这类永不落盘的计数）。 */
        public synchronized void noteSuppressed() {
            suppressed++;
        }

        /**
         * <b>静默心跳</b>：记一次「我关心的那个状态还在」（不落盘、不改形态记忆，
         * 只更新 {@link #millisSinceTouch}）。「退出」这类<b>只能靠消失推断</b>的状态靠它判定。
         */
        public synchronized void touch(long nowMillis) {
            lastTouchMillis = nowMillis;
        }

        /** 距最近一次 {@link #touch} 过了多少毫秒；从没 touch 过 ⇒ {@link Long#MAX_VALUE}。 */
        public synchronized long millisSinceTouch(long nowMillis) {
            return lastTouchMillis == 0L ? Long.MAX_VALUE : nowMillis - lastTouchMillis;
        }

        /** 自上次 {@link #takeSuppressed()} 以来，被闸门挡下的次数。 */
        public synchronized long suppressed() {
            return suppressed;
        }

        /** 取走并清零被挡下的次数（写进下一条真正落盘的行里 ⇒ 「静默了多少」仍然看得见）。 */
        public synchronized long takeSuppressed() {
            final long n = suppressed;
            suppressed = 0L;
            return n;
        }

        /** 上一次真正落盘的形态（没打过则是 null）。 */
        public synchronized String lastShape() {
            return lastShape;
        }

        /** 是否打过以 {@code prefix} 开头的形态（「退出 long 档」那一行的前置条件靠它）。 */
        public synchronized boolean sawShapePrefix(String prefix) {
            if (lastShape != null && lastShape.startsWith(prefix)) {
                return true;
            }
            for (final String s : seen.keySet()) {
                if (s.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        /** 已经落盘过的形态种数。 */
        public synchronized int distinctShapes() {
            return seen.size();
        }

        public synchronized void reset() {
            seen.clear();
            lastShape = null;
            lastLogMillis = 0L;
            lastTouchMillis = 0L;
            suppressed = 0L;
        }
    }

    // ═══════════════════════════════ 判定用的常量 ═══════════════════════════════

    /** 引擎链的真实 int 天花板（{@code Integer.MAX_VALUE} = 2,147,483,647）。 */
    public static final long INT_CEILING = 2147483647L;

    /** 引擎新造的成品上 {@code realParallels} 的<b>已知形态</b>（保持配方的默认值 1）。 */
    public static final long EXPECTED_ENGINE_REAL_PARALLELS = 1L;

    /**
     * 🔴 <b>池审计「已知来源带」的绝对上限 = 1e19（用户 2026-10-01 拍板）。</b>
     *
     * <p>依据（用户实测日志全量 631 条池审计行）：差额<b>恒为负</b>（631/631，
     * {@code Δ总 < 我这台 Σ}），绝对值全部落在 <b>[6.27e17, 1.66e18]</b>
     * ⇒ 本上限 1e19 比实测上限（1.66e18）宽 <b>约一档（≈6 倍）</b>。
     *
     * <h2>🔴 2026-10-01 用户拍板改了什么（旧值 1e20）</h2>
     * <pre>
     *   差额 ≥ 0（正 或 零）        ⇒ 当场 WARN   ← 零也进异常档（旧实现把 0 当「已知」）
     *   差额 &lt; 0 且 |差额| ≤ 1e19  ⇒ 静默（一行都不打）  ← 实测 631/631 全在此档
     *   差额 &lt; 0 且 |差额| &gt; 1e19  ⇒ 当场 WARN   ← 远超实测区间
     * </pre>
     *
     * <p>⚠️ <b>诚实边界（写死）</b>：本工程只知道「实测落在 1e18 量级且恒为负」，
     * <b>没有</b>证明「负 = 正常 / 正 = 异常」。
     * ⇒ 原始代码注释写的是「差额 = 别的写者」，而实测方向是<b>反的</b>
     * （{@code Δ总} 比本机投递的<b>少</b>，不是「别的写者在加」）。
     * ⇒ ⇒ 🔴 <b>「哪个方向才是真异常」仍待用户确认</b>（他才知道"别的写者"是谁）。
     * 本实现的判据 = 用户 2026-10-01 的口径；若要反过来，只动本常量 + {@link #isPoolDiffKnown}。
     */
    public static final BigInteger POOL_DIFF_KNOWN_LIMIT = new BigInteger("10000000000000000000");

    // ═══════════════════════════════ 形态串构造 ═══════════════════════════════

    /** {@code BigInteger} 的十进制量级（{@code 0} ⇒ {@code 0}）。 */
    public static int decimalExponent(BigInteger abs) {
        return abs.signum() == 0 ? 0 : abs.toString().length() - 1;
    }

    /** 形态串：符号 + 十进制量级，例如 {@code "-|1e18"}、{@code "0"}。 */
    public static String shapeOf(BigInteger value) {
        if (value.signum() == 0) {
            return "0";
        }
        return (value.signum() < 0 ? "-|1e" : "+|1e") + decimalExponent(value.abs());
    }

    // ═══════════════════════════════ 各日志点的判定 ═══════════════════════════════

    /**
     * <b>① 模块对账（{@code [SHANHAI-MODULE-EQ] 真实对账}）。</b>
     *
     * <pre>
     *   一致   ⇒ 静默（NONE）                ← 对账通过本来就不需要打
     *   不一致 ⇒ 当场 ERROR（abnormal=true） ← 级别与改动前一致，没有降级
     * </pre>
     *
     * <p>⚠️ 用户口径写的是「升级到 WARN」，而改动前这一支本来就是 {@code ERROR}（比 WARN 更高）
     * ⇒ 按「不许把 WARN / ERROR 降级」的硬约束，<b>保留 ERROR</b>，不降成 WARN。
     */
    public static Verdict decideModuleEquality(Gate gate, String tag, long p, int d0,
                                               long engineDuration, long engineEut,
                                               long nativeDuration, long nativeEut,
                                               long nowMillis) {
        if (engineDuration == nativeDuration && engineEut == nativeEut) {
            gate.noteSuppressed();
            return SILENT;
        }
        // 形态取「粗粒度」（不一致 + p + d0）：数值细节进正文，避免「每次数值都不同 ⇒ 每次都新形态」的刷屏。
        final String shape = "不一致|" + tag + "|p=" + p + "|d0=" + d0;
        if (!gate.changed(shape, true, nowMillis, DEFAULT_MIN_INTERVAL_MILLIS)) {
            return SILENT;
        }
        return new Verdict(Level.ERROR, gate.takeSuppressed(), true, shape);
    }

    /**
     * <b>② realParallels 影子探针（{@code [SHANHAI-MODULE-EQ] realParallels 探针}）。</b>
     *
     * <pre>
     *   引擎成品 == 1（已知形态）    ⇒ 每个 (p) 只打一次 INFO（实测只有 64 / 320 / 9216 三种 ⇒ 3 行）
     *   引擎成品 != 1（预期外形态）  ⇒ 当场 WARN
     *   同一形态再次出现             ⇒ 静默
     * </pre>
     *
     * <p>⚠️ <b>与用户字面口径的差异（已在聊天与交接文档里报出）</b>：用户写的是「一致不打，不一致才打」。
     * 但<b>这一项的常态就是"不一致"</b> —— 实测 507/507 条全是 {@code 引擎成品 = 1} 而
     * {@code p ∈ {64, 320, 9216}}（探针自己的 javadoc 也写明「影子对账【不覆盖】此项」）。
     * 若照字面执行，507 条<b>一条都不会少</b>（刷屏照旧）。
     * ⇒ 本实现把「回归<b>已知形态</b>」定为静默条件，把「偏离已知形态」定为 WARN。
     */
    public static Verdict decideRealParallels(Gate gate, long p, long actual, long nowMillis) {
        final boolean unexpected = actual != EXPECTED_ENGINE_REAL_PARALLELS;
        final String shape = (unexpected ? "预期外|" : "已知|") + "p=" + p + "|成品=" + actual;
        if (!gate.changed(shape, unexpected, nowMillis, DEFAULT_MIN_INTERVAL_MILLIS)) {
            return SILENT;
        }
        return new Verdict(unexpected ? Level.WARN : Level.INFO, gate.takeSuppressed(), unexpected, shape);
    }

    /**
     * <b>③ 发电撞顶探针（{@code [SHANHAI-GEN-LIMIT] p=… 撞 Long.MAX_VALUE 顶=…}）。</b>
     *
     * <pre>
     *   撞顶 且 p &gt; 2,147,483,647（long 档内，用户已拍板接受）  ⇒ 每个 (p) 只打一次 INFO
     *   撞顶 且 p ≤ 2,147,483,647（**预期外**：没进 long 档却撞了墙）⇒ 当场 WARN
     *   未撞顶（撞顶已消失 = 状态变化）                          ⇒ 打一次 INFO
     *   同一形态再次出现（持续撞顶）                             ⇒ 静默
     * </pre>
     */
    public static Verdict decideEnergyWall(Gate gate, long parallel, long beforeEu, long afterEu, long nowMillis) {
        final boolean hitWall = afterEu == Long.MAX_VALUE;
        final boolean longScale = parallel > INT_CEILING;
        final boolean unexpected = hitWall && !longScale;
        final String shape = (hitWall ? "撞顶" : "未撞顶") + "|long档=" + longScale + "|p=" + parallel;
        if (!gate.changed(shape, unexpected, nowMillis, DEFAULT_MIN_INTERVAL_MILLIS)) {
            return SILENT;
        }
        return new Verdict(unexpected ? Level.WARN : Level.INFO, gate.takeSuppressed(), unexpected, shape);
    }

    /**
     * <b>④ 池审计（{@code [SHANHAI-GEN-LIMIT] 池审计}）。</b>
     *
     * <pre>
     *   差额 &lt; 0 且 |差额| ≤ 1e19（已知带 = **实测 631/631 全在这一档**）
     *        ⇒ **静默：一行都不打**（只累计静默次数，下一条 WARN 行会把它打出来）
     *   差额 ≥ 0（正 或 零），或 差额 &lt; 0 且 |差额| &gt; 1e19
     *        ⇒ 当场 WARN
     * </pre>
     *
     * <p>🔴 <b>口径来源 = 用户 2026-10-01 拍板</b>（本轮改动）：作者原先做的是「已知带只在形态
     * 第一次出现时打一次 INFO（共 2 行）」，用户拍板改成「已知带<b>静默</b>」并把上限从 1e20
     * 收到 <b>1e19</b>、把 <b>0 也划进异常档</b>。
     * <p>⇒ 改后这一行在本机实测形态下落盘 <b>0 行</b>（631 → 0），而不是作者离线回放的那 2 行。
     *
     * <p>⚠️ 「哪个方向才是真异常」<b>仍待用户确认</b> —— 见 {@link #POOL_DIFF_KNOWN_LIMIT}。
     */
    public static Verdict decidePoolAudit(Gate gate, BigInteger diff, long nowMillis) {
        if (isPoolDiffKnown(diff)) {
            // 实测 631/631 全在这一档 ⇒ 这一档就是"正常"⇒ 按用户口径一行都不打。
            // 「静默了多少」不会丢：下一次真正落盘的 WARN 行会把 takeSuppressed() 一起打出来。
            gate.noteSuppressed();
            return SILENT;
        }
        final String shape = "超出已知带|" + shapeOf(diff);
        if (!gate.changed(shape, true, nowMillis, DEFAULT_MIN_INTERVAL_MILLIS)) {
            return SILENT;
        }
        return new Verdict(Level.WARN, gate.takeSuppressed(), true, shape);
    }

    /**
     * 差额是否落在「已知来源带」：{@code diff < 0 且 |diff| ≤ }{@value #POOL_DIFF_KNOWN_LIMIT}。
     *
     * <p>🔴 用户 2026-10-01 口径：<b>必须严格为负</b>（{@code 0} 不算）—— {@code 差额 ≥ 0 ⇒ WARN}。
     */
    public static boolean isPoolDiffKnown(BigInteger diff) {
        return diff.signum() < 0 && diff.abs().compareTo(POOL_DIFF_KNOWN_LIMIT) <= 0;
    }

    /**
     * <b>⑤ 入池并排（{@code [SHANHAI-GEN-LIMIT] 入池并排}）。</b>
     *
     * <pre>
     *   Δ==Y 且 接受=true  ⇒ 静默（入池落账，正常）—— 实测 128/128 条全是这一档 ⇒ 改后 0 行
     *   其余               ⇒ 当场 WARN
     * </pre>
     */
    public static Verdict decideDeposit(Gate gate, boolean deltaEqualsDeposit, boolean accepted,
                                        boolean aboveLongMax, long nowMillis) {
        if (deltaEqualsDeposit && accepted) {
            gate.noteSuppressed();
            return SILENT;
        }
        final String shape = "异常|Δ==Y=" + deltaEqualsDeposit + "|接受=" + accepted + "|超Long.MAX=" + aboveLongMax;
        if (!gate.changed(shape, true, nowMillis, DEFAULT_MIN_INTERVAL_MILLIS)) {
            return SILENT;
        }
        return new Verdict(Level.WARN, gate.takeSuppressed(), true, shape);
    }

    /**
     * <b>⑥ 时长下限（{@code [SHANHAI-DURATION-FLOOR] 并行已进 long 档}）。</b>
     *
     * <pre>
     *   进了 long 档且真的抬了时长 ⇒ 「预算 / 抬前 / 抬后 / 下限」这个形态第一次出现时打一次 INFO
     *   同一形态再次出现（实测 1270 条全同形）⇒ 静默
     * </pre>
     *
     * <p>「退出 long 档」由 {@link #decideDurationFloorExit(Gate, long, long)} 负责。
     */
    public static Verdict decideDurationFloor(Gate gate, long parallelBudget, int fromDuration, int toDuration,
                                              int floor, int originalDuration, long nowMillis) {
        final String shape = "long|预算=" + parallelBudget + "|" + fromDuration + "->" + toDuration
                + "|下限=" + floor;
        if (!gate.changed(shape, false, nowMillis, DEFAULT_MIN_INTERVAL_MILLIS)) {
            return SILENT;
        }
        return new Verdict(Level.INFO, gate.takeSuppressed(), false, shape);
    }

    /**
     * <b>⑥-b 「退出 long 档」的一行（只能靠「long 档调用停止」推断 —— 见下面的判据）。</b>
     *
     * <h2>🔴 为什么必须靠「静默超时」而不是「看到一次非 long 调用」</h2>
     * 本方法所在的原生链helper {@code PrimordialRecipeEffects#applyLongScaleDurationFloor}
     * 是<b>无机器身份的静态工具</b>（实参只有 配方 / 原时长 / 预算）。
     * 而本工程 25 台模块里<b>大量档位的预算 ≤ 21 亿</b>（例：空槽模块 64）
     * ⇒ 「看到一次非 long 调用」根本<b>不表示</b>「刚才那台退出了 long 档」
     * （那只是另一台本来就不在 long 档的机器）。
     * 若照字面实现「非 long ⇒ 打一行退出」，实测会打出十几行<b>假退出</b>。
     *
     * <p>⇒ 判据改成：<b>「打过 long 档形态」＋「距最近一次 long 档调用已静默 ≥ {@code quietMillis}」</b>。
     * 发电机在跑时 long 档调用约 2 次/秒 ⇒ 静默超时<b>永远不可能</b>达成 ⇒ <b>不会打假退出</b>；
     * 只有 long 档真的停下来（例如用户把并行档改回 ≤ 21 亿）才会兑现。
     *
     * <h2>⚠️ 诚实边界（写死）</h2>
     * <ul>
     *   <li>延迟 = {@code quietMillis}（默认 30 秒）；<b>不是</b>「一变就报」。</li>
     *   <li>若 long 档停止后<b>整个方法都不再被调用</b>（例如机器被拆、世界卸载）⇒ 本行<b>不会</b>出现
     *       （没有调用就没有判据）。这是有意的：宁可不报，也不报假的。</li>
     *   <li>调用方必须<b>在 long 分支的每一次调用上</b>调 {@link Gate#touch}(now)，
     *       否则静默超时会立刻达成、又变回「假退出」。</li>
     * </ul>
     */
    public static Verdict decideDurationFloorExit(Gate gate, long parallelBudget, long quietMillis,
                                                  long nowMillis) {
        if (!gate.sawShapePrefix("long|")) {
            return SILENT;
        }
        if (gate.millisSinceTouch(nowMillis) < quietMillis) {
            // long 档调用还在继续 ⇒ 没有退出，一行都不许打。
            return SILENT;
        }
        final String shape = "退出long|预算=" + parallelBudget;
        if (!gate.changed(shape, true, nowMillis, DEFAULT_MIN_INTERVAL_MILLIS)) {
            return SILENT;
        }
        return new Verdict(Level.INFO, gate.takeSuppressed(), false, shape);
    }

    /** 「退出 long 档」的默认静默窗（毫秒）：长期档调用停这么久才算退出。 */
    public static final long DEFAULT_LONG_EXIT_QUIET_MILLIS = 30_000L;

    // ═══════════════════════════════ 自检（离线可跑） ═══════════════════════════════

    /**
     * 🔴 <b>自检 —— 本项目纪律「检查器先证明自己对」的落点。</b>
     *
     * <p>覆盖三段：①<b>正常 ⇒ 静默</b>；②<b>异常 ⇒ 当场打且级别 ≥ WARN</b>；③<b>复原 ⇒ 回到静默</b>。
     * 任一条不成立 ⇒ 抛 {@link IllegalStateException}
     * （宁可当场炸，也不许「看起来修好了、其实异常也被吞了」）。
     *
     * <p><b>为什么它是纯的</b>：不碰日志、不碰文件、不碰时钟（时间由参数喂）⇒
     * {@code javac --release 17 ShanhaiLogThrottle.java} 单独编译即可跑，
     * 游戏里跑的是<b>同一份字节码</b>。
     *
     * @return 给调用方写日志用的一整行
     */
    public static String selfTest() {
        int checks = 0;

        // ═══════════ ① 正常 / 已告知过的形态 ⇒ 静默 ═══════════
        {
            final Gate g = new Gate();
            // 撞顶探针：long 档内持续撞顶 —— 这正是用户实测那 1268 行的形态
            final Verdict first = decideEnergyWall(g, 9223372036854775L, 2147483648L, Long.MAX_VALUE, 1_000L);
            require(first.level == Level.INFO, "① 撞顶 首次进入 long 档应打一行 INFO，实为 " + first.level);
            require(!first.abnormal, "① 撞顶 首次进入 long 档不应算异常");
            require(first.suppressed == 0L, "① 首次打出的那一行，静默计数应为 0，实为 " + first.suppressed);
            long t = 1_000L;
            for (int i = 0; i < 1000; i++) {
                t += 10L;
                final Verdict v = decideEnergyWall(g, 9223372036854775L, 2147483648L, Long.MAX_VALUE, t);
                require(v.level == Level.NONE, "① 撞顶 持续同形应静默，第 " + i + " 次却给了 " + v.level);
            }
            require(g.suppressed() == 1000L, "① 撞顶 静默计数应为 1000，实为 " + g.suppressed());
            require(g.takeSuppressed() == 1000L, "① 撞顶 takeSuppressed 应返回 1000");
            require(g.suppressed() == 0L, "① 撞顶 takeSuppressed 之后应清零");
            require(g.distinctShapes() == 1, "① 撞顶 只应记住 1 个形态，实为 " + g.distinctShapes());
            checks += 8;

            // realParallels：实测三种 p 交替出现 ⇒ 每种一次、共 3 行（而不是 96 行）
            final Gate g0 = new Gate();
            int emitted = 0;
            for (int i = 0; i < 169; i++) {
                if (decideRealParallels(g0, 320L, 1L, 1_000L).level != Level.NONE) {
                    emitted++;
                }
                if (decideRealParallels(g0, 9216L, 1L, 1_000L).level != Level.NONE) {
                    emitted++;
                }
                if (decideRealParallels(g0, 64L, 1L, 1_000L).level != Level.NONE) {
                    emitted++;
                }
            }
            require(emitted == 3, "① realParallels 三种 p 交替 507 次应只落 3 行，实为 " + emitted);
            require(g0.suppressed() == 504L, "① realParallels 静默计数应为 504，实为 " + g0.suppressed());
            checks += 2;
        }

        // ═══════════ ② 异常 ⇒ 当场打，且级别 ≥ WARN ═══════════
        {
            // ② -a 对账不一致 ⇒ ERROR（与改动前同级，没有降级）
            final Gate g1 = new Gate();
            for (int i = 0; i < 500; i++) {
                require(decideModuleEquality(g1, "真实对账", 320L, 1, 1L, 1920L, 1L, 1920L, 1_000L).level == Level.NONE,
                        "② 对账一致应静默（第 " + i + " 次）");
            }
            // ⚠️ 不一致只比正常行晚 1 毫秒 ⇒ 证明「异常不受最小间隔约束，当场打」
            final Verdict bad = decideModuleEquality(g1, "真实对账", 320L, 1, 1L, 1920L, 1L, 1919L, 1_001L);
            require(bad.level == Level.ERROR, "② 对账不一致应当场 ERROR，实为 " + bad.level);
            require(bad.abnormal, "② 对账不一致应标记 abnormal");
            require(bad.suppressed == 500L, "② 不一致这一行应报出『此前已静默 500 次一致』，实为 " + bad.suppressed);
            checks += 504;

            // ② -b 撞顶落在预期外（未进 long 档却撞墙）⇒ WARN
            require(decideEnergyWall(new Gate(), 64L, 2147483648L, Long.MAX_VALUE, 1_000L).level == Level.WARN,
                    "② 未进 long 档（p=64）却撞顶应 WARN");

            // ② -c 入池 Δ != Y / 接受=false ⇒ WARN；Δ == Y 且接受 ⇒ 静默
            require(decideDeposit(new Gate(), false, true, true, 1_000L).level == Level.WARN,
                    "② 入池 Δ!=Y 应 WARN");
            require(decideDeposit(new Gate(), true, false, true, 1_000L).level == Level.WARN,
                    "② 入池 接受=false 应 WARN");
            require(decideDeposit(new Gate(), true, true, true, 1_000L).level == Level.NONE,
                    "② 入池 Δ==Y 且接受=true 应静默");
            // 异常即使紧跟在正常行后面也当场打
            final Gate g2 = new Gate();
            require(decideDeposit(g2, true, true, true, 1_000L).level == Level.NONE, "② 正常入池应静默");
            require(decideDeposit(g2, false, true, true, 1_001L).level == Level.WARN,
                    "② 异常档不受最小间隔约束（必须当场打）");
            checks += 6;

            // ② -d 池审计超出已知带 ⇒ WARN（用户 2026-10-01 拍板口径：差额 ≥ 0 或 |差额| > 1e19）
            require(decidePoolAudit(new Gate(), new BigInteger("5000000000000000000000"), 1_000L).level == Level.WARN,
                    "② 池差额 5e21（正数 ⇒ 不在已知带）应 WARN");
            require(decidePoolAudit(new Gate(), BigInteger.ONE, 1_000L).level == Level.WARN,
                    "② 池差额为正（实测 631/631 恒为负 ⇒ 方向与旧注释相反，按用户口径为正即异常）应 WARN");
            require(decidePoolAudit(new Gate(), new BigInteger("-100000000000000000000"), 1_000L).level == Level.WARN,
                    "② 池差额 -1e20（|差额| > 1e19 上限）应 WARN");
            // ② -e 影子探针的预期外形态 ⇒ WARN
            require(decideRealParallels(new Gate(), 320L, 7L, 1_000L).level == Level.WARN,
                    "② 引擎成品 ≠ 1（预期外形态）应当场 WARN");
            checks += 4;
        }

        // ═══════════ ③ 复原 ⇒ 回到静默（且不互相污染） ═══════════
        {
            // ③ -a 池审计：实测序列只有 1e18 / 1e17 两个形态，且**恒为负**
            //      ⇒ 按用户 2026-10-01 口径，两档都落在已知带 ⇒ **一行都不打（0 行）**
            final Gate g3 = new Gate();
            require(decidePoolAudit(g3, new BigInteger("-1660206966633859365"), 1_000L).level == Level.NONE,
                    "③ 差额 1e18 量级、负 ⇒ 在已知带内 ⇒ 必须静默（实测 631/631 全在这一档）");
            require(decidePoolAudit(g3, new BigInteger("-627189298506124649"), 1_000L).level == Level.NONE,
                    "③ 第二个形态（1e17 量级、负）同样必须静默");
            for (int i = 0; i < 600; i++) {
                require(decidePoolAudit(g3, new BigInteger("-737869762948381940"), 1_000L).level == Level.NONE,
                        "③ 同形态重复应静默，第 " + i + " 次却给了非静默");
            }
            require(g3.suppressed() == 602L, "③ 已知带静默计数应为 602（2 + 600），实为 " + g3.suppressed());
            final Verdict back = decidePoolAudit(g3, BigInteger.ZERO, 9_000L);
            require(back.level == Level.WARN, "③ 差额回到 0（完全守恒）按用户口径『差额 ≥ 0 ⇒ WARN』，实为 " + back.level);
            require(back.abnormal, "③ 差额 0 必须标 abnormal（用户口径把 0 划进异常档）");
            require(back.suppressed == 602L, "③ 这一行应报出『此前已静默 602 次』，实为 " + back.suppressed);
            require(g3.distinctShapes() == 1, "③ 池审计此时只应记住 1 个形态（那条 WARN），实为 " + g3.distinctShapes());
            checks += 607;

            // ③ -a2 已知带的边界（用户口径：上限 1e19「比实测上限宽一档」；0 与正数都进异常档）
            require(isPoolDiffKnown(new BigInteger("-9999999999999999999")), "③ |差额| = 1e19 − 1 应落在已知带内");
            require(isPoolDiffKnown(new BigInteger("-10000000000000000000")), "③ |差额| = 1e19（含边界，≤）应落在已知带内");
            require(!isPoolDiffKnown(new BigInteger("-10000000000000000001")), "③ |差额| = 1e19 + 1 应超出已知带");
            require(decidePoolAudit(new Gate(), new BigInteger("-10000000000000000001"), 1_000L).level == Level.WARN,
                    "③ |差额| 越过 1e19 一点 ⇒ 也当场 WARN");
            require(!isPoolDiffKnown(BigInteger.ZERO), "③ 差额 = 0 不属已知带（用户口径：差额 ≥ 0 ⇒ WARN）");
            require(decidePoolAudit(new Gate(), BigInteger.ZERO, 1_000L).level == Level.WARN, "③ 差额 0 应 WARN");
            require(decimalExponent(new BigInteger("10000000000000000000")) == 19, "③ 1e19 的量级应为 19");
            checks += 7;

            // ③ -b 时长下限：形态首次打一次、同形静默；「退出」靠「long 档调用静默超时」推断
            final Gate g5 = new Gate();
            require(decideDurationFloor(g5, Long.MAX_VALUE, 1, 10, 10, 1, 60_000L).level == Level.INFO,
                    "③ 时长下限 首次进入 long 档应打一次");
            for (int i = 0; i < 300; i++) {
                g5.touch(60_000L);
                require(decideDurationFloor(g5, Long.MAX_VALUE, 1, 10, 10, 1, 60_000L).level == Level.NONE,
                        "③ 时长下限 同形应静默，第 " + i + " 次却给了非静默");
            }
            // long 档调用还在继续（刚刚 touch 过）⇒ 非 long 档的调用【一行都不许打】（防假退出）
            require(decideDurationFloorExit(g5, 64L, DEFAULT_LONG_EXIT_QUIET_MILLIS, 61_000L).level == Level.NONE,
                    "③ long 档还在跑时，非 long 调用不许报『已退出』（否则就是假退出）");
            // long 档调用停止超过静默窗 ⇒ 兑现一行
            require(decideDurationFloorExit(g5, 64L, DEFAULT_LONG_EXIT_QUIET_MILLIS, 200_000L).level == Level.INFO,
                    "③ long 档调用静默超过 30 秒后，应打一行『已退出 long 档』");
            require(decideDurationFloorExit(new Gate(), 64L, DEFAULT_LONG_EXIT_QUIET_MILLIS, 200_000L).level
                            == Level.NONE,
                    "③ 从没进过 long 档时『退出』一行都不许打（否则恒等支会变成新噪声）");
            checks += 304;

            // ③ -c 形状工具
            require(shapeOf(BigInteger.ZERO).equals("0"), "③ shapeOf(0) 应为 0");
            require(shapeOf(new BigInteger("-737869762948381940")).equals("-|1e17"), "③ shapeOf 1e17 负档应稳定");
            require(decimalExponent(new BigInteger("100000000000000000000")) == 20, "③ 1e20 的量级应为 20");
            require(isPoolDiffKnown(new BigInteger("-1660206966633859365")), "③ 实测差额应落在已知带内");
            require(!isPoolDiffKnown(BigInteger.ONE), "③ 正差额不属已知带");
            checks += 5;

            // ③ -d 安全阀：正常行的形态种数有上限（不许「形态每次都不一样」把日志撑爆）
            final Gate g7 = new Gate();
            int normalEmitted = 0;
            for (int i = 0; i < 500; i++) {
                if (decideEnergyWall(g7, 1000L + i, 1L, 2L, 1_000L).level != Level.NONE) {
                    normalEmitted++;
                }
            }
            require(normalEmitted == Gate.MAX_DISTINCT_SHAPES,
                    "③ 安全阀：500 个互不相同的正常形态最多落 " + Gate.MAX_DISTINCT_SHAPES
                            + " 条，实为 " + normalEmitted);
            // 但异常行不受这个上限约束（异常必须看得见）
            final Gate g8 = new Gate();
            int abnormalEmitted = 0;
            for (int i = 0; i < 100; i++) {
                if (decideEnergyWall(g8, 10L + i, 1L, Long.MAX_VALUE, 1_000L + i * 10_000L).level == Level.WARN) {
                    abnormalEmitted++;
                }
            }
            require(abnormalEmitted == 100, "③ 异常行不受正常行上限约束，100 个不同异常应落 100 行，实为 "
                    + abnormalEmitted);
            checks += 2;
        }

        return "[SHANHAI-LOG-THROTTLE] 日志闸门自检通过（" + checks + " 条断言，纯 JDK、可离线单跑）："
                + "① 正常 / 已告知过的形态 ⇒ 静默（撞顶 1001 次落 1 行、对账一致 500 次落 0 行、"
                + "池审计【已知带】602 次落 0 行、realParallels 三种 p 交替 507 次落 3 行）；"
                + "② 异常 ⇒ 当场打且级别 ≥ WARN（对账不一致 = ERROR、未进 long 档却撞顶 = WARN、"
                + "入池 Δ!=Y / 接受=false = WARN、池差额 ≥ 0 或 |差额| > 1e19 = WARN、引擎成品≠1 = WARN），"
                + "并在行末报出『此前已静默 N 次』；"
                + "③ 边界与复原：池差额 -1e19（含边界）⇒ 静默、-1e19 再小一点 ⇒ WARN、差额 0 ⇒ WARN"
                + "（用户 2026-10-01 口径：差额 ≥ 0 即异常）；"
                + "正常行落盘条数有硬上限 " + Gate.MAX_DISTINCT_SHAPES + " 条/日志点，异常行不受该上限约束。";
    }

    private static void require(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException("[SHANHAI-LOG-THROTTLE] 自检失败：" + what
                    + " ⇒ 闸门本身是坏的，它在真实输入上说的『静默』没有任何信息量。");
        }
    }
}
