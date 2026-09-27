package com.shanhai.machine.module;

import com.shanhai.ShanhaiMod;
import com.shanhai.common.recipe.PrimordialRecipeEffects;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * 🔴 <b>发电模块（全 24 台里唯一那台 {@code getDefinition().isGenerator()}）的产出算式 + 加载期自检</b>。
 *
 * <h2>1. 走的是哪一支（用户 2026-09-26 拍板）</h2>
 * 用户原话（逐字）：<b>「我宇宙之心就是这样搞得，都可以正常工作」</b>
 * ⇒ <b>电进无线网（gtmthings 全局能源池，大整数）</b>，而不是走 GT 的 long 电网（动力仓 / 线缆）。
 * 私货里的同款先例是 {@code PrimordialOmegaVoidInductionArmature.onWorking()}（原文 :220-237）：
 * <pre>
 *   WirelessEnergyManager.addEUToGlobalEnergyMap(targetUuid, getGeneratedEuPerTick(), this);
 * </pre>
 *
 * <h2>2. 🔴 那句引用（用户规则，<u>不是</u>本类发明的新数值）</h2>
 * <blockquote><b>发电机的可用能量 = 它自己算出来的产出（= 基础 EUt × 并行数 p）</b></blockquote>
 * 落到本工程，三个因子全部来自"机器自己"或项目既有纯函数：
 * <ol>
 *   <li><b>基础 EUt</b> = <b>该配方自己</b> {@code tickOutputs[eu]} 的值 —— 由调用方用
 *       {@code RecipeHelper.getOutputEUt(logic.getLastOriginRecipe())} 从
 *       <b>配方定义实例</b>上读（GTCEu 自己保留的 {@code lastOriginRecipe}，已用字节码核实
 *       {@code RecipeLogic.checkMatchedRecipeAvailable} 在 {@code setupRecipe} 之后写它，
 *       写的是<b>未经并行/未经过倍率的原配方</b>）。kubejs 那条
 *       {@code shanhai:zero_point_power_max_1a} 的 EUt 是 {@code -2147483648}（发电型 ⇒ 走
 *       {@code GTRecipeBuilder.EUt} 的 {@code outputEU(-EUt)}）⇒ 基础 EUt = {@code 2^31 = 2147483648}
 *       （MAX 档 1A）。<b>⚠️ 不许拿私货那台的常数 {@code 1048576 (2^20)} 来套</b> ——
 *       那是另一台机器的口径。</li>
 *   <li><b>并行数 p</b> = <b>该配方实际吃到的并行</b> {@code IGTRecipe.getRealParallels()}
 *       （gtlcore {@code ParallelLogicMixin.doParallelRecipes} 在 {@code copy(ContentModifier.multiplier(limitByOutput))}
 *       之后写 {@code setRealParallels(limitByOutput × 旧值)} —— 已读字节码）。
 *       用它而不是 {@code getMaxParallel()}：两者在"输入不够"时会分叉，用后者会<b>凭空多产电</b>。</li>
 *   <li><b>倍率（2026-09-25 用户改判）</b> = <b>÷ 主机耗能系数</b>
 *       {@link PrimordialRecipeEffects#reductionFactor(int)}（与模块尾链 N5 是<b>同一个纯函数</b>，
 *       地板 {@link #MIN_ENERGY_FACTOR} = 0.05 ⇒ 最多 ×20）。
 *       ⛔ <b>旧句（作废）</b>：原文写的是「倍率 = N3 产出倍率 {@code outputMultiplier(int)}」——
 *       用户 2026-09-25 原话：「<b>哦对了我忘记了它吃了*18倍的产出，那就不吃产出加成了，只吃这个加成，
 *       系数不是最小是0.05嘛，范围只有零点能发电机</b>」⇒ 发电不再吃 N3，改吃耗能系数的倒数。
 *       ⇒ <b>净效果</b>：{@code 基础 × p × 18} → {@code 基础 × p × 20}（系数 0.05 时），
 *       数值几乎不变，但<b>语义变成"跟着主机的减免走"</b>。</li>
 * </ol>
 *
 * <h2>3. 为什么必须用 BigInteger（这是整件事的钥匙）</h2>
 * 用户实测（<b>改前 ×18 口径</b>）：空槽 {@code 2.47T EU/t}（= 1152A）／满配 {@code 9.22E EU/t}（= 4294967296A）。
 * 逐位对得上：
 * <pre>
 *   空槽（改前）：p = 64，门控 17 ⇒ N3 = 18   ⇒ 2^31 × 64 × 18 = 2 473 901 162 496 = 2.4739e12  ✅（A = 1152 = 64×18）
 *   空槽（改后）：p = 64，门控 17 ⇒ ÷0.05    ⇒ 2^31 × 64 × 20 = 2 748 779 069 440 = 2.7488e12  ✅（A = 1280 = 64×20）
 *   满配：p 被钳到 Integer.MAX_VALUE ⇒ 2^31 × 2147483647 × 20 = 92 233 720 325 598 085 120 ≈ 9.2234e19
 *         ⇒ 超过 Long.MAX_VALUE = 9 223 372 036 854 775 807 ⇒ 撞墙
 *         ⇒ 墙 = {@code ContentModifier.apply(Number)} 对 Long 走 double 分支
 *           ＋ {@code EURecipeCapability.copyWithModifier} 收尾的 {@code Number.longValue()} 窄化
 *           （JLS：double 超出 long 范围时<b>静默饱和</b>到 Long.MAX_VALUE，不抛异常）
 *         ⇒ 显示 9.22E / A = 9.223372036854775807e18 ÷ 2^31 = 4294967296  ✅
 * </pre>
 * <b>⇒ 只要电还走 GT 的 long 电网，{@code >9.22e18 EU/t} 在物理上不存在</b>；
 * 只有 gtmthings 那个 <b>BigInteger</b> 池子装得下 8.301e19。
 *
 * <h2>4. 加载期自检（本类的 {@link #selfTest()}）</h2>
 * 本项目纪律「检查器先证明自己对」：自检里既有<b>正向对照</b>（用用户实测的空槽数当已知为真的样本），
 * 也有<b>负面对照</b>（证明 long 路径确实会饱和 = 墙是真的）。任一条不成立 ⇒ 加载期抛异常。
 * 调用点：{@link PrimordialModuleMachine#assertParallelTablesConsistent()}（由 {@code ModuleRegistry#init()} 触发）。
 */
public final class PrimordialGeneratorProduction {

    /**
     * 探针前缀（用户/队长用它在日志里一眼看修没修好）。
     *
     * <p>🔴 <b>沿用 `[SHANHAI-GEN-LIMIT]` 这一个记号</b>（队长 2026-09-26 口径："把已有探针扩成入池版"）：
     * 原来的同名行在 {@code PrimordialRecipeEffects#shanhai$probeGeneratorEnergyWall}（打那条 long 乘法窄化的
     * "撞顶"行），本类打的是<b>修好之后</b>的入池行 —— 两者同属一件事（这道墙 / 这道墙的解法），
     * 所以共用一个前缀 ⇒ 用户只需 grep 一个记号就能同时看到"墙"和"入池"。
     */
    public static final String TAG = "[SHANHAI-GEN-LIMIT]";

    // ───────────────────────── 自检用的已知样本（全部是用户实测 / 已核实的常数） ─────────────────────────

    /** 用户 2026-09-26 实测的<b>空槽</b>读数（EU/t）：2.47T。 */
    public static final long USER_MEASURED_EMPTY_SLOT_EUT = 2_473_901_162_496L;
    /** 空槽对应的并行（{@link PrimordialModuleMachine#DEFAULT_PARALLEL}）。 */
    public static final long USER_MEASURED_EMPTY_SLOT_PARALLEL = 64L;
    /** 用户实测时主机专属槽的门控等级（N3 倍率 = 1 + 17 = 18）。 */
    public static final int USER_MEASURED_GATE_BONUS = 17;
    /** 基础 EUt 的默认来源（配方自带）：MAX 档 1A = 2^31。 */
    public static final long MAX_TIER_ONE_AMP_EUT = 1L << 31;
    /**
     * 满配（p 被钳到 int 上限）时<b>改前（×18 口径）</b>的产出真值：2^31 × 2147483647 × 18。
     * <p>⚠️ 2026-09-25 起这不再是本类的产出 —— 保留它是因为自检要拿它做<b>负面对照</b>
     * （新算式必须【不再】等于它）。
     */
    public static final BigInteger USER_EXPECTED_FULL_EUT = new BigInteger("83010348293038276608");

    // ═════════ 2026-09-25 用户定案：发电不再吃 N3 产出倍率，改吃「÷ 主机耗能系数」 ═════════

    /** 系数地板（用户原话「系数不是最小是0.05嘛」）。低于它一律按它算 ⇒ 发电最多 ×20，不会除出无穷。 */
    public static final double MIN_ENERGY_FACTOR = 0.05D;
    /** 系数量化到的小数位（与界面同精度）；0.050000000000000044 ⇒ 0.0500 ⇒ 恰好 ×20。 */
    public static final int ENERGY_FACTOR_SCALE = 4;
    /** 新口径下【空槽】的正向对照字面量：2^31 × 64 ÷ 0.05 = 2^31 × 64 × 20。 */
    public static final BigInteger NEW_EXPECTED_EMPTY_SLOT_EUT = new BigInteger("2748779069440");
    /** 新口径下【满配】的真值：2^31 × 2147483647 × 20。 */
    public static final BigInteger NEW_EXPECTED_FULL_EUT = new BigInteger("92233720325598085120");

    private PrimordialGeneratorProduction() {}

    /**
     * 发电吃的那一档除数 = <b>主机耗能系数</b>（与模块 N5 同一个 {@code reductionFactor}）。
     *
     * <h2>🔴 2026-09-25 用户定案（原话逐字）</h2>
     * <blockquote>「哦对了我忘记了它吃了*18倍的产出，那就不吃产出加成了，只吃这个加成，
     * 系数不是最小是0.05嘛，范围只有零点能发电机」</blockquote>
     * ⇒ <b>去掉 {@code × N3 产出倍率}（= 18），改成 {@code ÷ 耗能系数}</b>；
     * 系数 0.05（最小）⇒ 发电 ×20；系数 1.0（无减免）⇒ 发电 ×1（与改前相同）。
     *
     * <h2>为什么在这里量化到 4 位小数（不是"美化"，是为了算对）</h2>
     * {@code reductionFactor(17) = 1 - 0.95} 在 double 里是 {@code 0.050000000000000044}（不是 0.05）。
     * 直接除以它，{@code 2^31×64} 那份会得 {@code 2748779069439.99…} ⇒ 取整成 19 倍（少 5%），
     * 而用户要的是 <b>×20</b>。故先把系数按【界面同精度 4 位小数】量化（→ 0.0500）
     * ⇒ 空槽恰好 {@code 2^31×64×20}、满配恰好 {@code 2^31×2147483647×20}，可写成字面量断言。
     * <p>⚠️ 诚实边界：量化带来的相对误差 ≤ ~5e-5（真实值 0.4181… 这类档位），
     * 与配方侧 N5 用的原始 double 至多差这个量级 —— 但显示的那 4 位小数与这里除的是<b>同一个数</b>。
     *
     * <h2>🔴 兜底的两档策略（2026-09-25 写死，别只读一半）</h2>
     * <ol>
     *   <li><b>域外</b>（{@code ≤0} / {@code >1} / NaN / ∞）⇒ <b>一律 1.0（不放大）</b>。
     *       理由：这些值说明上游公式坏了；此时若按地板 0.05 给 ×20，等于<b>用 bug 白送 20 倍电</b>，
     *       方向与本次纠偏（不许凭空多产）正好相反。⇒ 坏输入的安全侧是"不给加成"。</li>
     *   <li><b>域内但低于地板</b>（{@code 0 < f < 0.05}）⇒ 抬到 {@link #MIN_ENERGY_FACTOR} = 0.05
     *       （用户口径"系数最小是 0.05"）⇒ 最多 ×20，绝不除出无穷。</li>
     * </ol>
     * 今天 {@code reductionFactor} 的合法域是 {@code [0.05, 1.0]}（level ∈ [0,17]，level 17 恰好 = 0.05）
     * ⇒ 第 2 档是<b>为将来改系数公式准备的保险</b>，现在跑不到；第 1 档也跑不到。
     * <p>若队长要的是"字面口径"（0/负数也按 0.05 算），把 {@code safe} 那两行换成
     * {@code Math.max(MIN_ENERGY_FACTOR, rawFactor)} 即可（一行），但那就把"坏输入"解释成"最大加成"了。
     */
    public static BigDecimal divisorOf(double rawFactor) {
        final double safe = (Double.isFinite(rawFactor) && rawFactor > 0.0D && rawFactor <= 1.0D)
                ? rawFactor : 1.0D;                       // 域外（NaN/∞/≤0/>1）⇒ 不放大（保守）
        final double floored = Math.max(MIN_ENERGY_FACTOR, safe);   // 域内低于地板 ⇒ 抬到 0.05
        return new BigDecimal(Double.toString(floored)).setScale(ENERGY_FACTOR_SCALE, RoundingMode.HALF_UP);
    }

    /** 本档（门控等级）发电要除的那个系数。 */
    public static BigDecimal generationDivisor(int gateBonus) {
        return divisorOf(PrimordialRecipeEffects.reductionFactor(gateBonus));
    }

    /** 发电总倍率 = 1 / 系数（系数 0.05 ⇒ 20.0）。<b>只用于显示与自检</b>，不参与入池计算。 */
    public static double generationGain(int gateBonus) {
        return BigDecimal.ONE.divide(generationDivisor(gateBonus), 10, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 本台发电机<b>每 tick 的产出</b>（BigInteger，不会饱和）。
     *
     * <p>算式 = 基础 EUt × 并行数 p（用户规则）<b>÷ 主机耗能系数</b>（2026-09-25 用户定案；
     * 系数地板 0.05 ⇒ 最多 ×20）。语义与"配方那条路"逐值一致 —— 只把最后那一次 long 窄化换成 BigInteger。
     *
     * @param baseEut  该配方自己的基础产出（{@code RecipeHelper.getOutputEUt(原配方)}）
     * @param parallel 该配方每 tick 实际吃到的并行
     * @param gateBonus 主机专属槽门控等级（{@code host.moduleSlotBonus()}；&le;0 ⇒ 系数 1.0 ⇒ 不放大）
     * @return 每 tick 产出；任一因子非法（&le;0）时返回 {@link BigInteger#ZERO}（= 不产、也不误产）
     */
    public static BigInteger perTick(long baseEut, long parallel, int gateBonus) {
        if (baseEut <= 0L || parallel <= 0L) {
            return BigInteger.ZERO;
        }
        BigInteger value = BigInteger.valueOf(baseEut).multiply(BigInteger.valueOf(parallel));
        final BigDecimal divisor = generationDivisor(gateBonus);
        if (divisor.compareTo(BigDecimal.ONE) != 0) {
            // HALF_UP：量化后的系数是有限小数，但 value ÷ 0.4181 这类仍可能除不尽 ⇒ 取最近整数
            //（这里不"宁可少算"：除不尽只是系数精度造成的亚整数级差，四舍五入才是"÷ 这个系数"的本义）。
            value = new BigDecimal(value).divide(divisor, 0, RoundingMode.HALF_UP).toBigIntegerExact();
        }
        return value;
    }

    /**
     * 🔴 <b>加载期自检</b>（正向对照 + 负面对照 + 两条用户实测数）。
     *
     * @throws IllegalStateException 任一条判据不成立（本项目风格：加载期 fail-fast，不静默）
     */
    public static void selfTest() {
        // ── ① 正向对照（新口径 2026-09-25）：必须等于"÷0.05 = ×20"的字面量 2748779069440 ──
        final BigInteger empty = perTick(MAX_TIER_ONE_AMP_EUT, USER_MEASURED_EMPTY_SLOT_PARALLEL,
                USER_MEASURED_GATE_BONUS);
        assertEq(empty.toString(), NEW_EXPECTED_EMPTY_SLOT_EUT.toString(),
                "空槽产出（2^31 × 64 ÷ 0.05 = 2^31 × 64 × 20）必须等于 2748779069440");

        // ── ② 负面对照：新口径必须【不再】等于改前那条 ×18 的实测值（否则说明 N3 还在乘）──
        if (empty.toString().equals(Long.toString(USER_MEASURED_EMPTY_SLOT_EUT))) {
            throw new IllegalStateException(TAG + " 自检失败：新算式仍等于改前的 ×18 值 "
                    + USER_MEASURED_EMPTY_SLOT_EUT + " ⇒ N3 产出倍率没有被换成「÷耗能系数」。");
        }
        // 并证明"这次改动恰好就是 18→20 那一步"：新值 × 18 ÷ 20 必须逐值回到改前实测值
        final BigInteger backToOld = empty.multiply(BigInteger.valueOf(18L)).divide(BigInteger.valueOf(20L));
        assertEq(backToOld.toString(), Long.toString(USER_MEASURED_EMPTY_SLOT_EUT),
                "新值 ×18÷20 必须逐值回到改前实测值（= 证明只换了 18→20 这一步）");

        // ── ③ 地板与域外兜底（防 0/负/NaN ⇒ 除出无穷）──
        //    🔴 两档策略（见 divisorOf 的 javadoc）：域外 ⇒ 1.0（不放大）；域内低于地板 ⇒ 抬到 0.05。
        //    ⚠️ 断言一律用 stripTrailingZeros() 归一化：divisorOf 的 scale 恒为 4，
        //       直接比字符串会得到 "1.0000"/"0.0500" 这种带尾零的形态（本自检第一版就栽在这上面）。
        assertDivisor("reductionFactor(17)（double 噪声 0.050000000000000044）", 0.050000000000000044D, "0.05");
        assertDivisor("系数 0（域外）", 0.0D, "1");
        assertDivisor("负系数（域外）", -1.0D, "1");
        assertDivisor("NaN", Double.NaN, "1");
        assertDivisor("∞", Double.POSITIVE_INFINITY, "1");
        assertDivisor("系数 >1（域外）", 1.7D, "1");
        assertDivisor("系数 1.0（无减免）", 1.0D, "1");
        assertDivisor("域内但低于地板的 0.0123", 0.0123D, "0.05");
        if (divisorOf(0.0D).compareTo(BigDecimal.ZERO) <= 0 || divisorOf(-1.0D).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(TAG + " 自检失败：地板/兜底后的除数不为正 ⇒ 可能除出无穷/负电。");
        }
        // 无加成档：门控 0 ⇒ 系数 1.0 ⇒ ÷1 恒等（这条证明"不影响正常情况"）
        final BigInteger noBonus = perTick(MAX_TIER_ONE_AMP_EUT, 64L, 0);
        assertEq(noBonus.toString(),
                BigInteger.valueOf(MAX_TIER_ONE_AMP_EUT).multiply(BigInteger.valueOf(64L)).toString(),
                "门控 0（无减免）时产出必须 = 2^31 × 64（÷1 恒等）");

        // ── ④ 满配真值（新口径）：必须超过 Long.MAX_VALUE（这正是"撞顶"的根因） ──
        final BigInteger full = perTick(MAX_TIER_ONE_AMP_EUT, Integer.MAX_VALUE, USER_MEASURED_GATE_BONUS);
        assertEq(full.toString(), NEW_EXPECTED_FULL_EUT.toString(),
                "满配产出（2^31 × 2147483647 × 20）必须等于 92233720325598085120");
        if (full.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
            throw new IllegalStateException(TAG + " 自检失败：满配产出（" + full + "）没有超过 Long.MAX_VALUE"
                    + " ⇒ 那就不存在「撞顶」问题，本类的前提不成立 ⇒ 说明常数抄错了。");
        }

        // ── ⑤ 负面对照：证明"墙"是真的（long 路径必然把满配压到 Long.MAX_VALUE） ──
        //    与 {@code EURecipeCapability.copyWithModifier} 同形：double 乘法 → Number.longValue()。
        final double gain = generationGain(USER_MEASURED_GATE_BONUS);
        final double viaDouble = (double) MAX_TIER_ONE_AMP_EUT * (double) Integer.MAX_VALUE * gain;
        final long narrowed = (long) viaDouble;              // JLS：超出 long 范围 ⇒ 饱和到 Long.MAX_VALUE
        if (narrowed != Long.MAX_VALUE) {
            throw new IllegalStateException(TAG + " 自检的【负面对照】失败：double 窄化后得到 " + narrowed
                    + "，而没有饱和到 Long.MAX_VALUE ⇒ 检查器本身是坏的，"
                    + "它在真实输入上说的「BigInteger 能绕开墙」没有任何信息量。");
        }

        ShanhaiMod.LOGGER.info(TAG + " 自检通过（正向对照 + 负面对照）：空槽 2^31×64÷0.05 = {}"
                        + "（改前 ×18 口径 = {}；新值×18÷20 逐值回到它 ⇒ 只换了 18→20 这一步）；"
                        + "发电倍率 ÷{} = ×{}；满配 2^31×{}×20 = {} ＞ Long.MAX_VALUE={}"
                        + "（同一算式走 long 会被窄化压回 {}）"
                        + " ⇒ 用电网 long 装不下、只有无线网的 BigInteger 池装得下。",
                empty, USER_MEASURED_EMPTY_SLOT_EUT, generationDivisor(USER_MEASURED_GATE_BONUS),
                String.format(java.util.Locale.ROOT, "%.2f", gain), Integer.MAX_VALUE, full, Long.MAX_VALUE,
                narrowed);
    }

    private static void assertEq(String actual, String expected, String what) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(TAG + " 自检失败：" + what + "，实际 " + actual + "，应为 " + expected + "。");
        }
    }

    /** 断言某个原始系数被兜底成期望的除数（<b>归一化尾零</b>后比较，避免 scale 假失败）。 */
    private static void assertDivisor(String what, double rawFactor, String expectedPlain) {
        final String actual = divisorOf(rawFactor).stripTrailingZeros().toPlainString();
        if (!expectedPlain.equals(actual)) {
            throw new IllegalStateException(TAG + " 自检失败：兜底后的除数不对（" + what + "），实际 "
                    + actual + "，应为 " + expectedPlain + "。");
        }
    }
}
