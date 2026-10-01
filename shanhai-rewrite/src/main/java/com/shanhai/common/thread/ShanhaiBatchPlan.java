package com.shanhai.common.thread;

/**
 * 山海重构 · <b>「多配方聚合 ⇒ 批处理」的纯算术核 + 决策编排</b>。
 *
 * <h2>🔴 为什么单独一个类，而不是写在 {@code PrimordialRecipeEffects} 里</h2>
 * 与 {@link ShanhaiFairAllocation} / {@link ShanhaiConcurrencyTables} 同一条纪律：
 * <b>把要判定的那件事放到一个不依赖 Minecraft / GTCEu / Forge 的地方</b>。
 * 本轮要回答的问题是两句：
 * <ol>
 *   <li>多条配方同时跑时，批处理的 <b>N 取几</b>；</li>
 *   <li>每条 origin 该补扣多少额外原料，以及<b>「一条不够 ⇒ 整单放弃、一粒料都不扣」这条原子性</b>
 *       到底有没有被守住。</li>
 * </ol>
 * 这两件事<b>全部</b>落在本类的控制流与整数算术上；真正碰机器的那三件
 * （构造额外投料配方 / 预检 / 真扣）由调用方用 {@link Steps} 回呼注入
 * ⇒ 于是判据可以写成"喂一个假的库存与假的回呼，跑出数字来"，
 * 而不是"我推导应该对"。离线判据见 {@code temp/multi-batch/BatchPlanCheck.java}。
 *
 * <p>本类<b>刻意只 import {@code java.*}</b>（连 fastutil 都不用），
 * 保证 {@code javac ShanhaiBatchPlan.java} 一条命令就能编译。
 *
 * <h2>它治的病（上一轮 §3.4 的"安全选择"）</h2>
 * 修好跨配方并行之后，一份成品是 <b>N 条配方的聚合</b>；而原来的
 * {@code applyBatchProcessing} 只按<b>第一条</b> origin 补扣 {@code (N-1)×p_0} 份料，
 * 却把<b>聚合后的全部产出</b>乘 N ⇒ <b>少扣料 = 白送材料</b>（本工程红线）
 * ⇒ 上一轮的做法是"多配方下干脆不施加批处理"。
 * 本类给出的正确形态：<b>逐条 origin 各补各的 {@code (N-1)×p_i}</b>，
 * 且<b>全部预检通过之后才开始真扣</b>。
 *
 * <h2>🔴 N 取几 —— 三条上界的推导（不是拍脑袋）</h2>
 * <pre>
 *   d0* = max_i(第 i 条的配方时长)        ← 【最大】，理由见下
 *   N_time   = max(1, 时间窗 / d0*)
 *   N_input  = min_i( (该条当前剩余可跑份数 + p_i) / p_i )     ← 只对 p_i > 0 的条取
 *   N_output = 输出空间能装下的【聚合体份数】
 *   N        = min(N_time, N_input, N_output)
 * </pre>
 *
 * <h3>为什么时长取 <b>max</b>（本条是"别自己拍"那一问的答案）</h3>
 * <ol>
 *   <li><b>同口径</b>：聚合体的"时长基数"在本工程已经被定为 {@code d0* = max}
 *       （{@code PrimordialModuleRecipeLogic#shanhai$aggregateOriginDurationOf}，
 *        上一轮 §3.3 定的，N6 的 {@code min(d0*, …)} 天花板随之）。
 *       批处理的 N 必须按<b>这一份聚合体</b>的时长算 ⇒ 只能同口径。</li>
 *   <li><b>唯一能保住上游时间窗不变式的取值</b>：成品真实时长 {@code T ≤ d0*}
 *       （N5 只减不增、N6 是 {@code min(d0*, …)}）⇒ {@code T × N ≤ d0* × N ≤ 时间窗}
 *       ⇒ <b>整批的总时长永远不超过 {@code batchProcessingTimeLimitTicks}</b>。
 *       取第一条或取 min 都会让 N 偏大 ⇒ 总时长可能超出时间窗 ⇒ 违背 gtlcore 语义。</li>
 *   <li><b>唯一能保住上游"资格闸门"的取值</b>：上游要求 {@code duration ≤ 时间窗/2}
 *       才进批处理（{@code BatchProcessing.apply} 的 {@code Eligibility.TIME_WINDOW}）。
 *       用 max ⇒ 只要<b>有一条</b> origin 不该进批处理，整批就进不去（N &lt; 2）
 *       —— 这正是"聚合体 = 全部 origin 一起跑"该有的 AND 语义；
 *       取 first / min 会出现"拿一条短配方当代表、把一条本来不该批的长配方一起批了"。</li>
 *   <li><b>每条各算 N_i 不可行</b>：产出一整块由<b>单个</b> N 统一放大
 *       （{@code finalRecipe.copy(×N)}），时长也只有一个数
 *       ⇒ 各条用不同的 N_i 就与任何"连跑 N 次"的语义都对不上。</li>
 *   <li><b>取 first 没有依据</b>：第一条只是遍历顺序，与"这一批要做多久"无关。</li>
 * </ol>
 * 保守性：{@code d0* ≥ d0_first} ⇒ {@code N_max ≤ N_first}
 * —— 比"只认第一条"的旧口径<b>只会更小、不会更大</b>。
 *
 * <h3>为什么 N_input 要逐条取 min</h3>
 * {@code getMaxParallel(machine, origin_i, Long.MAX_VALUE)} 问的是"这条配方单独看，
 * 当前库存还能跑几份"；装配相已经按 {@code p_i} 扣过一份 ⇒ 本轮总共能跑 {@code 剩余 + p_i} 份
 * ⇒ 能"连跑"几轮 = {@code (剩余 + p_i) / p_i}。逐条取 min，是因为<b>每条都要被补到 N 轮</b>。
 *
 * <h3>为什么 N_output 用【聚合体】去问</h3>
 * 被 ×N 的就是聚合体那一块产出（它已经把全部 origin 的产出都含在里面了，
 * 见 {@code MutableRecipesLogic.buildFinalNormalRecipe} 的收集式）
 * ⇒ "这块聚合体还能装下几份"就是 N，而且它<b>天然把各条争同一个输出口的情况算进去了</b>。
 *
 * <h2>🔴 原子性契约（本类存在的第二个理由）</h2>
 * <pre>
 *   相 B：对每一条 extras[i] &gt; 0 的 origin 先【预检】（模拟扣料）
 *   相 C：只有在【相 B 全部通过】之后，才开始【真扣】
 * </pre>
 * ⇒ 任一条预检不过 ⇒ 返回 {@link Kind#REJECTED}，<b>相 C 一次都不会被调用</b>
 * （判据把这个写成断言：假回呼里的"真扣计数器"必须是 0）。
 *
 * <h2>🔴 降级通道（本工程血规矩：不许猜一个数）</h2>
 * 任何一处"算不出来"⇒ 返回 {@link Kind#REJECTED} + 一行 {@code reason}，
 * 由调用方打 WARN 并<b>原样交回未施加批处理的配方</b>。算不出来的情形：
 * 时长非正 / 输入量上限问不出来 / 输出空间问不出来 / 额外投料配方构造失败 /
 * 条数与份数数组对不上 / 全部候选份额都 ≤ 0 / 额外投料量溢出。
 * （最后一条<b>结构上到不了</b>：{@code N ≤ (剩余+p)/p ≤ Long.MAX_VALUE/p}
 *  ⇒ {@code (N-1)×p ≤ Long.MAX_VALUE - p}，见 {@link #run} 里的注释；
 *  保留它只是保险，不许当成"已覆盖的判据"。）
 */
public final class ShanhaiBatchPlan {

    private ShanhaiBatchPlan() {}

    /** 决策的四种归宿。 */
    public enum Kind {
        /** 施加批处理（相 B、相 C 全部成功）。 */
        APPLIED,
        /** 正常跳过（N ≤ 1）—— 不是错误，是"这一档本来就不该批"。 */
        SKIPPED,
        /** 算不出来 / 预检不过 ⇒ 降级：不施加、<b>不扣任何料</b>。 */
        REJECTED,
        /** 预检已全部通过、真扣却失败（理论上到不了）⇒ 不施加，调用方打 ERROR。 */
        DEDUCT_FAILED
    }

    /**
     * 逐条 origin 的"输入量上限"问询（返回该条配方单独看当前还能跑几份）。
     * <b>返回 &lt; 0 = 问不出来</b>（⇒ 降级，不许猜）。
     */
    @FunctionalInterface
    public interface InputProbe {
        long remainingParallels(int index);
    }

    /** 输出空间问询：{@code wanted} 是"我们最多想要几份聚合体"，返回实际装得下几份；&lt; 0 = 问不出来。 */
    @FunctionalInterface
    public interface OutputProbe {
        long copies(int wanted);
    }

    /**
     * 真正碰机器的三件事。核心只负责<b>何时</b>调它们，不关心它们怎么实现。
     * <p>用 {@code Object} 当载荷类型，是为了让本文件零依赖（真正传进去的是 {@code GTRecipe}）。
     */
    public interface Steps {

        /** 构造第 {@code index} 条 origin 的额外投料配方（{@code extra} 份）；{@code null} = 构造失败。 */
        Object build(int index, long extra);

        /** 预检（模拟扣料）。{@code false} = 料不够 ⇒ 整单放弃。 */
        boolean precheck(int index, Object payload);

        /** 真扣。只有全部预检通过之后才会被调用。 */
        boolean deduct(int index, Object payload);
    }

    /** 决策结果（不可变，供调用方打证据行）。 */
    public static final class Outcome {

        public final Kind kind;
        /** 施加时的 N（= 连跑几次）；未施加时是"本该是几"（0 或 1）或 0。 */
        public final long cycles;
        /** 逐条 {@code (N-1)×p_i}；未施加时<b>全 0</b>（因为一粒料都没扣）。 */
        public final long[] extras;
        public final long nTime;
        public final long nInput;
        public final long nOutput;
        /** 聚合时长基数 = max(各条时长)。 */
        public final long d0Max;
        /** 被拒 / 失败的原因（一行中文）；成功时为空串。 */
        public final String reason;
        /** 预检或真扣失败的下标；-1 = 无。 */
        public final int failedIndex;
        /** 份数 {@code ≤ 0} 的候选条数（这些条补 0 份料，不算"算不出来"）。 */
        public final int zeroParallelCount;

        private Outcome(Kind kind, long cycles, long[] extras, long nTime, long nInput, long nOutput,
                        long d0Max, String reason, int failedIndex, int zeroParallelCount) {
            this.kind = kind;
            this.cycles = cycles;
            this.extras = extras;
            this.nTime = nTime;
            this.nInput = nInput;
            this.nOutput = nOutput;
            this.d0Max = d0Max;
            this.reason = reason;
            this.failedIndex = failedIndex;
            this.zeroParallelCount = zeroParallelCount;
        }

        public boolean applied() {
            return kind == Kind.APPLIED;
        }

        /** 供证据行/判据打印：{@code [a, b, c]}。 */
        public String extrasText() {
            final StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < extras.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(extras[i]);
            }
            return sb.append(']').toString();
        }
    }

    /**
     * 主入口：决定 N、逐条算额外料、按"全部预检通过才真扣"的顺序执行。
     *
     * @param windowTicks    gtlcore 的 {@code batchProcessingTimeLimitTicks}（≤0 按 1 处理）
     * @param durations      逐条 origin 的<b>配方定义原时长</b> d_i（tick）
     * @param parallels      逐条 origin 本轮分配到的份数 p_i（与 {@code durations} 一一对应）
     * @param inputProbe     逐条的输入量上限问询（可被调用 0 次，见下）
     * @param outputProbe    输出空间问询（可被调用 0 次，见下）
     * @param steps          碰机器的三件事
     * @return 见 {@link Outcome}
     *
     * <p>⚠️ {@code N_time ≤ 1} 时<b>一次 probe 都不会调</b>（与"配方太长 ⇒ 零查询、零改动"同一条纪律）。
     */
    public static Outcome run(long windowTicks, long[] durations, long[] parallels,
                              InputProbe inputProbe, OutputProbe outputProbe, Steps steps) {
        final int count = durations == null ? 0 : durations.length;
        if (count == 0 || parallels == null || parallels.length != count) {
            return reject(new long[count], "候选条数与份数数组对不上（条数 " + count + "，份数 "
                    + (parallels == null ? "null" : String.valueOf(parallels.length)) + "）", 0L, 0L, 0L, 0L, 0, -1);
        }
        if (inputProbe == null || outputProbe == null || steps == null) {
            return reject(new long[count], "回呼缺失（inputProbe / outputProbe / steps 不能为 null）",
                    0L, 0L, 0L, 0L, 0, -1);
        }

        // ── ① 聚合时长基数 d0* = max(各条时长) —— 见类注释的三条推导 ──
        long d0Max = 0L;
        for (int i = 0; i < count; i++) {
            final long d = durations[i];
            if (d <= 0L) {
                return reject(new long[count], "第 " + i + " 条 origin 的时长非正（" + d + " tick）⇒ 算不出 N",
                        0L, 0L, 0L, 0L, 0, -1);
            }
            d0Max = Math.max(d0Max, d);
        }

        final long window = Math.max(1L, windowTicks);
        final long nTime = Math.max(1L, window / d0Max);
        if (nTime <= 1L) {
            // 配方的时长已超过时间窗的一半 ⇒ 上游也不会批它 ⇒ 零查询、零改动。
            return skip(1L, new long[count], nTime, Long.MAX_VALUE, Long.MAX_VALUE, d0Max, count,
                    "时间窗 " + window + " tick ÷ 聚合时长 " + d0Max + " tick = " + nTime + " ≤ 1");
        }

        // ── ② 输入量上界：逐条取 min（每条都要被补到 N 轮）──
        long nInput = Long.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            final long p = parallels[i];
            if (p <= 0L) {
                continue;                       // 这条本轮没跑 ⇒ 不需要补料，也不参与取 min
            }
            final long remaining = inputProbe.remainingParallels(i);
            if (remaining < 0L) {
                return reject(new long[count], "第 " + i + " 条 origin 的输入量上限问不出来（" + remaining + "）⇒ 算不出 N",
                        nTime, -1L, -1L, d0Max, count, -1);
            }
            nInput = Math.min(nInput, saturatingAdd(remaining, p) / p);
        }
        if (nInput == Long.MAX_VALUE) {
            return reject(new long[count], "全部候选的份数都 ≤ 0 ⇒ 没有可补的料（算不出该补多少）",
                    nTime, -1L, -1L, d0Max, count, -1);
        }

        // ── ③ 输出空间上界：拿【聚合体】去问（N 就是我们最多想要几份）──
        final long hint = Math.min(Math.min(nTime, nInput), (long) Integer.MAX_VALUE);
        final long nOutput = outputProbe.copies((int) hint);
        if (nOutput < 0L) {
            return reject(new long[count], "输出空间份数问不出来（" + nOutput + "）⇒ 算不出 N",
                    nTime, nInput, -1L, d0Max, count, -1);
        }

        // ── ④ N = 三条上界取 min，并夹到 int（下游 preciseMultiplier / duration 都是 int 量级）──
        long cycles = Math.min(Math.min(nTime, nInput), nOutput);
        if (cycles > (long) Integer.MAX_VALUE) {
            cycles = Integer.MAX_VALUE;         // 饱和夹紧：只会让 N 更小，不会过头
        }
        int zeroParallel = 0;
        for (int i = 0; i < count; i++) {
            if (parallels[i] <= 0L) {
                zeroParallel++;
            }
        }
        if (cycles <= 1L) {
            return skip(cycles, new long[count], nTime, nInput, nOutput, d0Max, zeroParallel,
                    "N = min(时间窗 " + nTime + "，输入量 " + nInput + "，输出空间 " + nOutput + ") = " + cycles);
        }

        // ── ⑤ 逐条算额外料 (N-1)×p_i ──
        final long[] extras = new long[count];
        final long per = cycles - 1L;
        for (int i = 0; i < count; i++) {
            final long p = parallels[i];
            if (p <= 0L) {
                extras[i] = 0L;
                continue;
            }
            // ⚠️ 结构上到不了：N ≤ saturatingAdd(剩余,p)/p ≤ Long.MAX_VALUE/p
            //    ⇒ (N-1)×p ≤ Long.MAX_VALUE - p。保留此闸门只为"以后有人改了上界"。
            if (p > Long.MAX_VALUE / per) {
                return reject(new long[count], "第 " + i + " 条 origin 的额外投料量溢出（p=" + p
                        + " ×(N-1)=" + per + " 超出 long）", nTime, nInput, nOutput, d0Max, zeroParallel, -1);
            }
            extras[i] = p * per;
        }

        // ── 相 B：先把每条的额外投料配方都造出来，再全部预检 ──
        final Object[] payloads = new Object[count];
        for (int i = 0; i < count; i++) {
            if (extras[i] <= 0L) {
                continue;
            }
            final Object built = steps.build(i, extras[i]);
            if (built == null) {
                return reject(extras, "第 " + i + " 条 origin 的额外投料配方构造失败 ⇒ 算不出该补多少",
                        nTime, nInput, nOutput, d0Max, zeroParallel, i);
            }
            payloads[i] = built;
        }
        for (int i = 0; i < count; i++) {
            if (extras[i] <= 0L) {
                continue;
            }
            if (!steps.precheck(i, payloads[i])) {
                // 🔴 到这里为止【一次真扣都没有发生过】—— 这就是"全部预检通过才扣"。
                return reject(extras, "第 " + i + " 条 origin 的额外投料【预检不过】⇒ 整单放弃（不扣任何料）",
                        nTime, nInput, nOutput, d0Max, zeroParallel, i);
            }
        }

        // ── 相 C：全部预检通过 ⇒ 才开始真扣 ──
        for (int i = 0; i < count; i++) {
            if (extras[i] <= 0L) {
                continue;
            }
            if (!steps.deduct(i, payloads[i])) {
                return new Outcome(Kind.DEDUCT_FAILED, 0L, new long[count], nTime, nInput, nOutput, d0Max,
                        "第 " + i + " 条 origin 的额外投料【真扣失败】（预检已全部通过 ⇒ 理论上到不了这里）",
                        i, zeroParallel);
            }
        }
        return new Outcome(Kind.APPLIED, cycles, extras, nTime, nInput, nOutput, d0Max, "", -1, zeroParallel);
    }

    // ═══════════════════════════ 内部小工具 ═══════════════════════════

    private static Outcome skip(long cycles, long[] extras, long nTime, long nInput, long nOutput,
                               long d0Max, int zeroParallel, String why) {
        return new Outcome(Kind.SKIPPED, cycles, extras, nTime, nInput, nOutput, d0Max, why, -1, zeroParallel);
    }

    private static Outcome reject(long[] extras, String reason, long nTime, long nInput, long nOutput,
                                  long d0Max, int zeroParallel, int failedIndex) {
        // 🔴 被拒时 extras 一律清零：调用方要能直接拿它当"该扣多少"的证据行，
        //    "被拒 ⇒ 一个数都不许露出来"。
        return new Outcome(Kind.REJECTED, 0L, new long[extras.length], nTime, nInput, nOutput, d0Max,
                reason, failedIndex, zeroParallel);
    }

    /** 饱和加（本工程红线：并行/份数算术一律饱和，不许回绕成负数）。 */
    public static long saturatingAdd(long a, long b) {
        if (a < 0L || b < 0L) {
            return Long.MAX_VALUE;
        }
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }

    /** 饱和乘（同上）。 */
    public static long saturatingMultiply(long a, long b) {
        if (a <= 0L || b <= 0L) {
            return 0L;
        }
        return a > Long.MAX_VALUE / b ? Long.MAX_VALUE : a * b;
    }

    /**
     * 时间窗份数（= 上游 {@code BatchProcessing.getBatchSize} 第一段）。
     * 单独暴露出来，是为了让判据能直接核对"d0* 取 max 会让 N 更小"这件事。
     */
    public static long timeWindowCycles(long windowTicks, long d0) {
        if (d0 <= 0L) {
            return 1L;
        }
        return Math.max(1L, Math.max(1L, windowTicks) / d0);
    }

    // ═══════════════════════ 加载期自检（正面对照 + 负面对照） ═══════════════════════

    /** 假回呼：数一数 {@code precheck} / {@code deduct} 各被调了几次，用作"原子性"的现场读数。 */
    private static final class CountingSteps implements Steps {

        int failPrecheckAt = -1;
        int precheckCalls;
        int deductCalls;
        long deductedTotal;
        final long[] parallelSeen;

        CountingSteps(int count) {
            this.parallelSeen = new long[count];
        }

        @Override
        public Object build(int index, long extra) {
            return "extra#" + index + "=" + extra;
        }

        @Override
        public boolean precheck(int index, Object payload) {
            precheckCalls++;
            return index != failPrecheckAt;
        }

        @Override
        public boolean deduct(int index, Object payload) {
            deductCalls++;
            if (payload instanceof String s) {
                parallelSeen[index] = Long.parseLong(s.substring(s.indexOf('=') + 1));
                deductedTotal += parallelSeen[index];
            }
            return true;
        }
    }

    /**
     * <b>加载期自检</b>：把"N 的取值口径"与"原子性"一次性断言掉。
     *
     * <p>由 {@code PrimordialModuleMachine#assertParallelTablesConsistent()} 调用
     * （本工程既有的"注册期必跑 + 日志可 grep"入口，不新增任何加载钩子）。
     * 任一不成立 ⇒ <b>加载期当场抛异常</b>
     * （本工程反复记录过的最坏失败形态是"静默不对"）。
     *
     * <p>判据三段（对应任务书要求的"正常 / 应当被拒绝 / 复原"）：
     * <ol>
     *   <li><b>正面对照</b>：用户实测档（时间窗 12000、两条时长 120、p=[9216,9216]、料管够、
     *       输出空间管够）⇒ 必须 APPLIED，N=100，extras 逐条 = 99×9216；</li>
     *   <li><b>负面对照 A（应当被拒绝）</b>：把第 2 条的预检改成不过
     *       ⇒ 必须 REJECTED，且<b>真扣调用次数 = 0</b>（这是本类存在的全部意义）；</li>
     *   <li><b>负面对照 B（算不出）</b>：时长非正 / 输入量问不出来 / 输出空间问不出来
     *       ⇒ 一律 REJECTED 且真扣 0 次；</li>
     *   <li><b>不变式</b>：任何 APPLIED 的样本都满足 {@code d0* × N ≤ 时间窗}
     *       （= 整批总时长不会超出时间窗）；并给出"取 min 而不是 max 会破坏它"的对照。</li>
     * </ol>
     *
     * @return 给调用方写日志用的一整行（本类自己不写日志）
     */
    public static String selfTest() {
        final long window = 12000L;

        // ── ① 正面对照：用户实测档（两条同长配方，料管够）──
        final CountingSteps ok = new CountingSteps(2);
        final Outcome applied = run(window, new long[] { 120L, 120L }, new long[] { 9216L, 9216L },
                i -> 1_000_000L, w -> (long) w, ok);
        expect(applied.kind == Kind.APPLIED, "正面对照应为 APPLIED，实为 " + applied.kind + "（" + applied.reason + "）");
        expect(applied.cycles == 100L, "正面对照 N 应为 100，实为 " + applied.cycles);
        expect(applied.extras.length == 2 && applied.extras[0] == 99L * 9216L && applied.extras[1] == 99L * 9216L,
                "正面对照逐条额外料应为 [912384, 912384]，实为 " + applied.extrasText());
        expect(ok.deductCalls == 2, "正面对照真扣应发生 2 次，实为 " + ok.deductCalls);
        expect(ok.deductedTotal == 2L * 99L * 9216L, "正面对照真扣总量应为 1824768，实为 " + ok.deductedTotal);

        // ── ② 负面对照 A：第 2 条料不够（预检不过）⇒ 整单放弃、一次真扣都不许发生 ──
        final CountingSteps bad = new CountingSteps(2);
        bad.failPrecheckAt = 1;
        final Outcome rejected = run(window, new long[] { 120L, 120L }, new long[] { 9216L, 9216L },
                i -> 1_000_000L, w -> (long) w, bad);
        expect(rejected.kind == Kind.REJECTED,
                "负面对照 A 应为 REJECTED，实为 " + rejected.kind + "（" + rejected.reason + "）");
        expect(rejected.failedIndex == 1, "负面对照 A 的失败下标应为 1，实为 " + rejected.failedIndex);
        expect(bad.precheckCalls >= 1, "负面对照 A 至少应预检过，实为 " + bad.precheckCalls);
        expect(bad.deductCalls == 0, "🔴 负面对照 A 违反了原子性：真扣发生了 " + bad.deductCalls + " 次");
        expect(bad.deductedTotal == 0L, "🔴 负面对照 A 扣掉了料：" + bad.deductedTotal);
        expect(allZero(rejected.extras), "负面对照 A 被拒时 extras 必须清零，实为 " + rejected.extrasText());

        // ── ③ 负面对照 B：算不出来 ⇒ 一律 REJECTED + 真扣 0 次 ──
        final CountingSteps c1 = new CountingSteps(2);
        expect(run(window, new long[] { 120L, 0L }, new long[] { 9216L, 9216L }, i -> 1_000_000L, w -> (long) w, c1)
                        .kind == Kind.REJECTED, "时长非正应被拒");
        final CountingSteps c2 = new CountingSteps(2);
        expect(run(window, new long[] { 120L, 120L }, new long[] { 9216L, 9216L }, i -> i == 1 ? -1L : 1_000_000L,
                        w -> (long) w, c2).kind == Kind.REJECTED, "输入量上限问不出来应被拒");
        final CountingSteps c3 = new CountingSteps(2);
        expect(run(window, new long[] { 120L, 120L }, new long[] { 9216L, 9216L }, i -> 1_000_000L,
                        w -> -1L, c3).kind == Kind.REJECTED, "输出空间问不出来应被拒");
        final CountingSteps c4 = new CountingSteps(2);
        expect(run(window, new long[] { 120L, 120L }, new long[] { 0L, 0L }, i -> 1_000_000L, w -> (long) w, c4)
                        .kind == Kind.REJECTED, "全部份额 ≤ 0 应被拒");
        expect(c1.deductCalls + c2.deductCalls + c3.deductCalls + c4.deductCalls == 0,
                "🔴 负面对照 B 里发生了真扣");

        // ── ④ 零查询：配方太长（N_time ≤ 1）⇒ 一次 probe 都不许调 ──
        final int[] probes = { 0 };
        final CountingSteps c5 = new CountingSteps(2);
        final Outcome tooLong = run(100L, new long[] { 60L, 60L }, new long[] { 9216L, 9216L },
                i -> { probes[0]++; return 1_000_000L; }, w -> { probes[0]++; return (long) w; }, c5);
        expect(tooLong.kind == Kind.SKIPPED, "时间窗 ÷ 时长 ≤ 1 应跳过，实为 " + tooLong.kind);
        expect(probes[0] == 0, "🔴 跳过档不应碰机器，实际调了 " + probes[0] + " 次 probe");
        expect(c5.deductCalls == 0, "跳过档不应真扣");

        // ── ⑤ 输出空间钳制：输出只装得下 7 份 ⇒ N=7（即使时间窗给 100）──
        final CountingSteps c6 = new CountingSteps(2);
        final Outcome outLimited = run(window, new long[] { 120L, 120L }, new long[] { 9216L, 9216L },
                i -> 1_000_000L, w -> 7L, c6);
        expect(outLimited.kind == Kind.APPLIED && outLimited.cycles == 7L,
                "输出空间钳制应为 N=7，实为 " + outLimited.cycles + "/" + outLimited.kind);
        expect(outLimited.extras[0] == 6L * 9216L, "输出空间钳制下逐条额外料应为 6×9216，实为 " + outLimited.extrasText());

        // ── ⑥ 输入量钳制：只有第 2 条吃紧 ⇒ N 由它决定，逐条一起降到同一个 N ──
        final CountingSteps c7 = new CountingSteps(2);
        final Outcome inLimited = run(window, new long[] { 120L, 120L }, new long[] { 1000L, 1000L },
                i -> i == 0 ? 1_000_000L : 4_000L, w -> (long) w, c7);
        // (4000 + 1000) / 1000 = 5
        expect(inLimited.kind == Kind.APPLIED && inLimited.cycles == 5L,
                "输入量钳制应为 N=5，实为 " + inLimited.cycles + "/" + inLimited.kind);
        expect(inLimited.extras[0] == inLimited.extras[1],
                "输入量钳制下两条的额外料应同为 4×1000，实为 " + inLimited.extrasText());

        // ── ⑦ 不变式：任何 APPLIED 档都满足 d0* × N ≤ 时间窗（= 整批不会超出时间窗）──
        final long[][] durations = { { 120L, 120L }, { 5L, 300L }, { 300L, 5L }, { 1L, 1L, 1L }, { 240L, 240L } };
        for (long[] ds : durations) {
            final long[] ps = new long[ds.length];
            java.util.Arrays.fill(ps, 512L);
            final CountingSteps cs = new CountingSteps(ds.length);
            final Outcome o = run(window, ds, ps, i -> 1_000_000L, w -> (long) w, cs);
            if (o.kind == Kind.APPLIED) {
                long d0Max = 0L;
                for (long d : ds) {
                    d0Max = Math.max(d0Max, d);
                }
                expect(d0Max * o.cycles <= window,
                        "🔴 不变式被破坏：d0*(" + d0Max + ")×N(" + o.cycles + ") > 时间窗(" + window + ")");
            }
        }
        // 负面对照：同一批时长如果按【最短那条】算 N，不变式当场被破坏 ⇒ 证明"取 max"不是可有可无。
        final long[] minDurations = { 5L, 300L };
        long minD = Long.MAX_VALUE;
        long maxD = 0L;
        for (long d : minDurations) {
            minD = Math.min(minD, d);
            maxD = Math.max(maxD, d);
        }
        final long nByMin = timeWindowCycles(window, minD);
        expect(maxD * nByMin > window,
                "负面对照失效：按最短时长算 N(" + nByMin + ") 竟然没超出时间窗 —— 判据本身没在测东西");

        return "[SHANHAI-BATCH-PLAN] 多配方批处理自检通过（正面对照 2 条 + 负面对照 5 类）："
                + "N = min(时间窗÷max(各条时长), 逐条输入量, 聚合体输出空间)；"
                + "逐条补扣 (N-1)×p_i；【任一预检不过 ⇒ 真扣 0 次】(已断言)；"
                + "配方太长时零查询零改动。";
    }

    private static boolean allZero(long[] a) {
        for (long v : a) {
            if (v != 0L) {
                return false;
            }
        }
        return true;
    }

    private static void expect(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException("[SHANHAI-BATCH-PLAN] 自检失败：" + what);
        }
    }
}
