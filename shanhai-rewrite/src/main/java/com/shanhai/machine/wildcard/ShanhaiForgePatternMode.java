package com.shanhai.machine.wildcard;

import appeng.api.crafting.IPatternDetails;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * 「神锻样板模式」（需求④）的逻辑入口 —— 把一条已经展开好的 AE 样板按
 * <b>神锻配方倍增规则</b>改写输入输出。
 *
 * <h2>一、宿主里的那个按钮到底是什么（只读侦察结果，逐字）</h2>
 * <pre>
 * 机器 ： com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine   （"超级样板总成"）
 * 按钮 ： com.gtladd.gtladditions.api.machine.gui.FOAPatternConfigurator implements IFancyConfigurator
 *        构造器 = FOAPatternConfigurator(MESuperPatternBufferPartMachine)          ← 只收那一种机器 ✗
 *        挂载点 = MESuperPatternBufferPartMachine.attachConfigurators(...) 里 **最后** 一个
 *                 configuratorPanel.attachConfigurators(new IFancyConfigurator[]{ new FOAPatternConfigurator(this) })
 *                 ⇒ 落在左侧竖列的**最下面** = 用户截图里的左下角 ✓
 *        它读写的三个状态 = isFOAModeEnabled() / setFOAModeEnabled(boolean) /
 *                          getFOAPatternOutputMultiplier() / setFOAPatternOutputMultiplier(int)
 *        UI = WidgetGroup(0,0,118,56)：ToggleButtonWidget(6,5,20,20,BUTTON_POWER) + LabelWidget(32,10)
 *             + LabelWidget(6,36) + IntInputWidget(58,31,54,20).setMin(1).setMax(30)
 *        文案 = 「gtladditions.machine.me_super_pattern_buffer.foa_config.title」= 神锻样板模式
 *               「…foa_config.tooltip.0/1」= 用户贴的那两行（本类直接复用同一批 lang key）
 * 真正的改写 ： com.gtladd.gtladditions.integration.ae2.MEBufferPatternHelperExtensions
 *              ← Kotlin 文件级函数类，字节码实测（javap -p）：
 *                public  static IPatternDetails processForgeOfTheAntichristPattern(MEBufferPatternHelper, ItemStack, Consumer&lt;Integer&gt;, Level, boolean, int)
 *                private static IPatternDetails rewriteForgeOfTheAntichristPattern(IPatternDetails, Level, int)   ← 🔴 我们要的就是它
 *                调用点 = gtladditions 的 MEPatternBufferPartMachineMixin.@Overwrite getRealPattern(...)
 *                         （本工程 temp\wildcard\decomp3\...\MEPatternBufferPartMachineMixin.java:41-50 原文）
 * </pre>
 *
 * <h2>二、🔴 为什么不能「直接调用」，以及本类怎么处理</h2>
 * 两个入口，各有一个拦路虎：
 * <table border="1">
 *   <tr><th>入口</th><th>可见性</th><th>为什么用不了</th></tr>
 *   <tr>
 *     <td>{@code rewriteForgeOfTheAntichristPattern}</td>
 *     <td><b>private static</b> ✗</td>
 *     <td>语义<b>正好</b>是我们要的（吃 IPatternDetails、吐改写后的 IPatternDetails），但编译期不可见。</td>
 *   </tr>
 *   <tr>
 *     <td>{@code processForgeOfTheAntichristPattern}</td>
 *     <td>public static ✓</td>
 *     <td>它的第一个参数是 {@code MEBufferPatternHelper}、第二个是 {@code ItemStack}，
 *         内部先做 {@code helper.processPatternWithCircuit(stack, …)} ——
 *         <b>它认的是「AE2 译码后的处理样板物品」</b>；
 *         而本机的样板是通配符样板**按材料现场展开**出来的 {@code IPatternDetails}，
 *         <b>压根没有那块 ItemStack</b> ⇒ 塞不进去 ✗</td>
 *   </tr>
 * </table>
 * ⇒ 本类采用<b>第三条路：反射调用那个 private 方法</b>。理由：
 * <ol>
 *   <li>它是**同一份上游实现**（不是照着反编译重写一遍）⇒ 与超级样板总成的行为逐位一致，
 *       不存在"我理解的倍增规则和上游不一样"这种静默分歧；</li>
 *   <li>反编译那段的输入倍增分支被 vineflower 打散成 {@code thisCollection$iv}（不可读），
 *       照抄反而更容易抄错；</li>
 *   <li>它不碰任何实例状态（纯静态函数：入参 → 出参）。</li>
 * </ol>
 *
 * <h2>三、🔴 失败必须出声（本工程红线）</h2>
 * 反射可能因为 gtladditions 改私名而失效。本类<b>绝不允许</b>那种情况下「按钮点了没反应」：
 * <ul>
 *   <li>解析失败 ⇒ 立刻 {@code LOGGER.error} 打出<b>目标类名 + 方法名 + 异常</b>（一条，带堆栈）；</li>
 *   <li>{@link #isAvailable()} 转为 false ⇒ {@code ForgePatternConfigurator} 会在 tooltip 上
 *       <b>直接挂一行「神锻模式不可用（见日志）」</b>，并在按钮上给出提示，玩家一眼能看出它坏了。</li>
 * </ul>
 */
public final class ShanhaiForgePatternMode {

    /** 与上游 {@code FOAPatternConfigurator} 的 {@code IntInputWidget.setMin(1).setMax(30)} 一致。 */
    public static final int MIN_MULTIPLIER = 1;

    /** 同上，{@code setMax(30)}（上游内部还会再 {@code coerceIn(1, 30)} 一次，这里只用于 UI）。 */
    public static final int MAX_MULTIPLIER = 30;

    /** 与上游 {@code MESuperPatternBufferPartMachine.foaPatternOutputMultiplier} 的默认值一致。 */
    public static final int DEFAULT_MULTIPLIER = 15;

    private static final String TARGET_CLASS =
            "com.gtladd.gtladditions.integration.ae2.MEBufferPatternHelperExtensions";

    private static final String TARGET_METHOD = "rewriteForgeOfTheAntichristPattern";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 惰性解析一次；解析失败置 {@link #failed} 并只报一次错。 */
    private static Method rewriteMethod;
    private static boolean resolved;
    private static boolean failed;

    private ShanhaiForgePatternMode() {}

    /** 上游实现是否可用（false ⇒ 按钮会显式提示"不可用"，不是静默死键）。 */
    public static synchronized boolean isAvailable() {
        resolve();
        return !failed;
    }

    /**
     * 按神锻模式改写一条样板。
     *
     * <p>语义与上游 {@code MESuperPatternBufferPartMachine} + {@code MEPatternBufferPartMachineMixin} 的组合一致：
     * <pre>
     *   if (!superBuffer.isFOAModeEnabled()) return 原样板;
     *   else return processForgeOfTheAntichristPattern(…, multiplier);
     * </pre>
     * 其中上游内部对 {@code multiplier} 还会 {@code coerceIn(1, 30)}，且 {@code &lt;= 1} 时原样返回 ——
     * 这些都在被反射的那份实现里，本类<b>不重复实现</b>。
     *
     * <p>任何失败都<b>退回原样板</b>（= 效果等同关掉神锻模式），并留 ERROR 日志。
     */
    public static IPatternDetails rewrite(IPatternDetails pattern, Level level, boolean enabled, int multiplier) {
        if (!enabled || pattern == null || level == null) {
            return pattern;
        }
        resolve();
        if (failed || rewriteMethod == null) {
            return pattern;
        }
        try {
            final Object result = rewriteMethod.invoke(null, pattern, level, multiplier);
            return result instanceof IPatternDetails details ? details : pattern;
        } catch (Throwable t) {
            LOGGER.error("[SHANHAI-WILDCARD] 神锻样板模式改写失败（本次退回原样板；机器其余功能不受影响）：", t);
            return pattern;
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            final Class<?> target = Class.forName(TARGET_CLASS);
            final Method method = target.getDeclaredMethod(TARGET_METHOD, IPatternDetails.class, Level.class, int.class);
            method.setAccessible(true);
            rewriteMethod = method;
            LOGGER.info("[SHANHAI-WILDCARD] 神锻样板模式：已复用上游实现 {}.{}(IPatternDetails, Level, int)（private static，经反射调用）",
                    TARGET_CLASS, TARGET_METHOD);
        } catch (Throwable t) {
            failed = true;
            LOGGER.error("[SHANHAI-WILDCARD] 神锻样板模式不可用：无法解析 {}.{}(IPatternDetails, Level, int)。"
                            + "⇒ 按钮仍会显示，但 tooltip 会标注「不可用」，开关本身不会改变任何样板。"
                            + "（这【不是】静默失效：本条 ERROR 就是它的证据。）",
                    TARGET_CLASS, TARGET_METHOD, t);
        }
    }
}
