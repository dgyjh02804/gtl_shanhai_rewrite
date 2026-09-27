package com.shanhai.common.text;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 山海重构 · <b>{@code &$…-} 文本样式的「色板表 + 算色公式」</b>（客户端 mixin 与服务端共用的数据层）。
 *
 * <h2>0. 出处（🔴 移植来源，逐值照抄，一个字节都没改）</h2>
 * <pre>
 * 色板常量 ： originals/upstream\gtl_shanhai-dishanhai\src\main\java\com\dishanhai\gt_shanhai\api\DShanhaiTextUtil.java
 *             第 31–247 行（RAINBOW_RGB / GOLDEN_RGB / … / BODY_MOSS_RGB 共 37 个 static final int[]）
 * 样式注册表： 同目录 DShanhaiStyleRegistry.java  第 38–127 行（reg / regVariant 两段）
 * 算色公式 ： DShanhaiTextUtil.java 第 621–639 行（getDynamicColor）+ 第 498–510 行（calcInterval / getEffectiveInterval）
 * </pre>
 * 本类<b>只搬我们实际会渲染到的那一部分</b>（用户裁决原话：「fontmix 我选只移植目前我们要用到的，直接移植到 jar 里面」）。
 * 完整清单与出处见 §2；<b>没搬的样式不会被猜色</b> —— 见 {@link ShanhaiTextParser} 的退化规则。
 *
 * <h2>1. 为什么色板放在 {@code common} 而不是 {@code client}</h2>
 * 这三个文件（本类 / {@link ShanhaiTextParser} / 混入）在客户端跑，但本类与解析器
 * <b>没有任何 {@code net.minecraft.client.*} 依赖</b>（取证：上游 {@code DShanhaiTextUtil.java:1-7} 的
 * 全部 import 就是 {@code ChatFormatting/Component/MutableComponent/Style/TextColor}）。
 * 放在 {@code common} 是刻意的：<b>专服加载时也要能解析这个类</b>，否则
 * {@code shanhai.mixin.json} 的 {@code client} 数组一旦被服务端误读就会 NoClassDefFound。
 *
 * <h2>2. 🔴 本类实际注册的样式（= 我们真正会渲染到的全部，共 16 条 / 13 份色板）</h2>
 *
 * <h4>2.1 来源 A · 横幅（本轮 E-2 会写进 {@code ShanhaiRecipeStats}）</h4>
 * <ul>
 *   <li>{@code body_golden}（{@code &$?body_golden-}，带 {@code ?} glitch）</li>
 *   <li>{@code body_moss}（{@code &$*body_moss-}，带 {@code *} floatX）</li>
 *   <li>{@code body_aurora}</li>
 *   <li>{@code body_silver}</li>
 * </ul>
 *
 * <h4>2.2 来源 B · 我们自己的 131 条物品名（{@code assets/shanhai/lang/zh_cn.json}，34 处 {@code &$…-}）</h4>
 * <pre>
 * ultimate            x10      crimson         x4       magic           x4
 * golden              x3       ultimateRainbow x3       aurora          x2
 * electric            x2       water           x2       body_silver     x1
 * gray                x1       green           x1       neon            x1
 * </pre>
 * （取证：{@code _tools\extract_style_inventory.ps1} 的原始输出，见任务报告）
 *
 * <h4>2.3 来源 C · 旁证（上游 {@code GTL5.23\kubejs\startup_scripts\山海的物品注册.js}，79 处）</h4>
 * 与来源 B 的差异<b>只有一个</b>：多一个 {@code cosmic}(x6)。它不在我们的 lang 里，
 * 但只值 14 个 int、且是「上游确实在用」的名字，所以一起带上（漏一个 = 那个样式退化成无颜色）。
 *
 * <h4>2.4 🔴 {@code ultimateRainbow} / {@code gray} / {@code green} —— 为什么它们也在这张表里</h4>
 * 这三个名字<b>在上游的 {@code DShanhaiStyleRegistry} 里根本没有注册</b>（实测：静态块第 40–102 行全表无此三键），
 * 而 {@code DShanhaiStyleRegistry.getRGB:201-204} 是：
 * <pre>
 * return def != null ? def.rgb() : DShanhaiTextUtil.ULTIMATE_RAINBOW_RGB;
 * </pre>
 * ⇒ <b>原版对这三个名字的实际显示效果 = {@code ULTIMATE_RAINBOW_RGB} 彩虹</b>。
 * 所以本类把它们<b>显式登记成 {@code ULTIMATE_RAINBOW_RGB}</b>，而不是留给「未注册 ⇒ 退化」那条路：
 * <ul>
 *   <li>它们不是「没移植的样式」，是「上游本来就这么渲染的样式」⇒ 逐值照抄 = 显式登记；</li>
 *   <li>这 5 条物品名（{@code ultimateRainbow}×3 / {@code gray}×1 / {@code green}×1）在用户眼里
 *       <b>和原版一模一样</b>；若走退化就变成 5 条白字，那是可见的回退。</li>
 * </ul>
 * {@code ultimateRainbow} 在上游还额外有 {@code _distort} / {@code _wobble} 两个变体键
 * （{@code regVariant} 拼的是 {@code base_anim}，所以裸的 {@code ultimateRainbow} 反而不存在）——
 * 那两条是另外的名字，我们用不到，不搬。
 *
 * <h2>3. 算色公式（逐行照抄 {@code getDynamicColor}，只有「时间」从隐式变显式）</h2>
 * <pre>
 * interval = isBody ? 200 : calcInterval(max(textLen,1))
 * cycle    = interval * palette.length
 * masked   = now % (cycle &gt; 0 ? cycle : 1)
 * rawPhase = masked / interval          ← 连续相位（整数部分 = 走到第几格，小数部分 = 格内插值比）
 * intPhase = (int) rawPhase
 * frac     = rawPhase - intPhase
 * idx1     = (intPhase + dir*charIndex) mod len      （dir = +1，上游 setDirection 默认值）
 * idx2     = (idx1 + 1) mod len
 * color    = 线性插值(palette[idx1], palette[idx2], frac)   ← 逐字错位 + 相邻色插值 + 时间流动，三者缺一不可
 * </pre>
 * <b>三项都在</b>：{@code intPhase}（时间流动）、{@code charIndex}（逐字错位）、{@code frac}（相邻色插值）。
 *
 * <h2>4. 时间源</h2>
 * 上游 {@code getTime()} 是 {@code sharedTimestamp &gt;= 0 ? sharedTimestamp : System.currentTimeMillis()}
 * （{@code DShanhaiTextUtil.java:259-261}），{@code sharedTimestamp} 只在「多行同步」时被 lock。
 * 我们不做多行同步（横幅每一行自己流动就够，原版也是各算各的），
 * ⇒ 本类<b>把 {@code now} 作为显式参数</b>，调用方传 {@link System#currentTimeMillis()}。
 * 做成显式参数而不是内部读时钟，是为了让「同一次绘制里所有字符共用同一个 now」成为<b>类型上可见的约定</b>
 * （否则行内每个字符各读一次时钟，相位会自己抖）。
 */
public final class ShanhaiTextPalette {

    private ShanhaiTextPalette() {}

    // ================================================================== 正文（慢速）步长

    /**
     * 正文样式的固定步长（ms/格）—— 照抄 {@code DShanhaiTextUtil.BODY_INTERVAL = 200}。
     *
     * <p>即 {@code body_golden}（11 色）一个完整循环 = 200 × 11 = <b>2200 ms</b>。
     */
    public static final long BODY_INTERVAL = 200L;

    /** 上游「被 setSpeed() 改过」的哨兵值，默认 80。我们从不改速度 ⇒ 恒等于 80。 */
    private static final long COLOR_INTERVAL_DEFAULT = 80L;

    /**
     * 自适应间隔 —— 照抄 {@code DShanhaiTextUtil.calcInterval:498-506}。
     *
     * <p>文本越长流动越快（短文本要慢，否则看不出颜色过渡）。
     */
    public static long calcInterval(int textLen) {
        if (textLen <= 5) {
            return 80L;
        }
        if (textLen <= 8) {
            return 60L;
        }
        if (textLen <= 10) {
            return 50L;
        }
        return 65L;
    }

    /** 照抄 {@code DShanhaiTextUtil.getEffectiveInterval:507-510}。 */
    public static long effectiveInterval(boolean isBody, int textLen) {
        if (COLOR_INTERVAL_DEFAULT != 80L) {
            return COLOR_INTERVAL_DEFAULT;
        }
        return isBody ? BODY_INTERVAL : calcInterval(Math.max(textLen, 1));
    }

    // ================================================================== 色板常量（逐值照抄）

    /** 照抄 {@code DShanhaiTextUtil.java:99-110} {@code ULTIMATE_RAINBOW_RGB}（40 色）。 */
    static final int[] ULTIMATE_RAINBOW_RGB = {
            0xFF4444, 0xFF5B2D, 0xFF7117, 0xFF8800,
            0xFF9900, 0xFFAA00, 0xFFBB00, 0xFFCC17,
            0xFFDD2D, 0xFFEE44, 0xE3F444, 0xC6F944,
            0xAAFF44, 0x88FF44, 0x66FF44, 0x44FF44,
            0x44FF66, 0x44FF88, 0x44FFAA, 0x44F4C6,
            0x44E8E3, 0x44DDFF, 0x44C1FF, 0x44A4FF,
            0x4488FF, 0x5571FF, 0x665BFF, 0x7744FF,
            0x8E44FF, 0xA444FF, 0xBB44FF, 0xD244FF,
            0xE844FF, 0xFF44FF, 0xFF4FE8, 0xFF5BD2,
            0xFF66BB, 0xFF71AA, 0xFF7D99, 0xFF8888
    };

    /** 照抄 {@code DShanhaiTextUtil.java:39-45} {@code GOLDEN_RGB}（31 色）。 */
    static final int[] GOLDEN_RGB = {
            0x995500, 0xAA6600, 0xBB7700, 0xCC8800, 0xDD9300, 0xEE9F00, 0xFFAA00,
            0xFFBB17, 0xFFCC2D, 0xFFDD44, 0xFFE85B, 0xFFF471, 0xFFFF88,
            0xFFFF9F, 0xFFFFB5, 0xFFFFCC, 0xFFFFB5, 0xFFFF9F, 0xFFFF88,
            0xFFF471, 0xFFE85B, 0xFFDD44, 0xFFCC2D, 0xFFBB17, 0xFFAA00,
            0xEE9F00, 0xDD9300, 0xCC8800, 0xBB7700, 0xAA6600, 0x995500
    };

    /** 照抄 {@code DShanhaiTextUtil.java:64-70} {@code MAGIC_RGB}（31 色）。 */
    static final int[] MAGIC_RGB = {
            0x550088, 0x600099, 0x6C00AA, 0x7700BB, 0x8217C6, 0x8E2DD2, 0x9944DD,
            0xA44FE3, 0xB05BE8, 0xBB66EE, 0xC671F4, 0xD27DF9, 0xDD88FF,
            0xE888FF, 0xF488FF, 0xFF88FF, 0xF488FF, 0xE888FF, 0xDD88FF,
            0xD27DF9, 0xC671F4, 0xBB66EE, 0xB05BE8, 0xA44FE3, 0x9944DD,
            0x8E2DD2, 0x8217C6, 0x7700BB, 0x6C00AA, 0x600099, 0x550088
    };

    /** 照抄 {@code DShanhaiTextUtil.java:56-62} {@code WATER_RGB}（30 色）。 */
    static final int[] WATER_RGB = {
            0x004488, 0x004F93, 0x005B9F, 0x0066AA, 0x1177BB, 0x2288CC, 0x3399DD,
            0x44AAE8, 0x55BBF4, 0x66CCFF, 0x7DD7FF, 0x93E3FF, 0xAAEEFF,
            0xC6F4FF, 0xE3F9FF, 0xFFFFFF, 0xE3F9FF, 0xC6F4FF, 0xAAEEFF,
            0x93E3FF, 0x7DD7FF, 0x66CCFF, 0x55BBF4, 0x44AAE8, 0x3399DD,
            0x2288CC, 0x1177BB, 0x0066AA, 0x004F93, 0x004488
    };

    /** 照抄 {@code DShanhaiTextUtil.java:121-125} {@code AURORA_RGB}（25 色）。 */
    static final int[] AURORA_RGB = {
            0x33FF44, 0x33EE55, 0x33DD66, 0x33CC77, 0x33BB88, 0x33AA99, 0x3399BB, 0x3388CC, 0x4477DD,
            0x5566EE, 0x7755FF, 0x9944FF, 0xBB33FF, 0x9944FF, 0x7755FF, 0x5566EE, 0x4477DD, 0x3388CC,
            0x3399BB, 0x33AA99, 0x33BB88, 0x33CC77, 0x33DD66, 0x33EE55, 0x33FF44
    };

    /** 照抄 {@code DShanhaiTextUtil.java:132-136} {@code NEON_RGB}（21 色）。 */
    static final int[] NEON_RGB = {
            0xFF33FF, 0xFF55CC, 0xFF7799, 0xFFAA66, 0xFFCC44, 0xAAFF33, 0x77FF44, 0x44FF77, 0x33FFBB,
            0x33FFEE, 0xFFFFFF, 0x33FFEE, 0x33FFBB, 0x44FF77, 0x77FF44, 0xAAFF33, 0xFFCC44, 0xFFAA66,
            0xFF7799, 0xFF55CC, 0xFF33FF
    };

    /** 照抄 {@code DShanhaiTextUtil.java:127-130} {@code CRIMSON_RGB}（17 色）。 */
    static final int[] CRIMSON_RGB = {
            0x991111, 0xAA1111, 0xBB1111, 0xCC1111, 0xDD2222, 0xEE3333, 0xFF4444, 0xFF5555, 0xFF6666,
            0xFF5555, 0xFF4444, 0xEE3333, 0xDD2222, 0xCC1111, 0xBB1111, 0xAA1111, 0x991111
    };

    /** 照抄 {@code DShanhaiTextUtil.java:143-146} {@code COSMIC_RGB}（14 色）。 */
    static final int[] COSMIC_RGB = {
            0x553388, 0x6644AA, 0x7755CC, 0x6688EE, 0x44AAFF, 0x88CCFF, 0xDDF4FF,
            0xFFFFFF, 0xE8D8FF, 0xCCAAFF, 0xAA77FF, 0x8866DD, 0x6644AA, 0x553388
    };

    /** 照抄 {@code DShanhaiTextUtil.java:79-82} {@code ELECTRIC_RGB}（13 色）。 */
    static final int[] ELECTRIC_RGB = {
            0xFFDD00, 0xC1DD55, 0x82DDAA, 0x44DDFF, 0x82E8FF, 0xC1F4FF, 0xFFFFFF,
            0xC1F4FF, 0x82E8FF, 0x44DDFF, 0x82DDAA, 0xC1DD55, 0xFFDD00
    };

    /** 照抄 {@code DShanhaiTextUtil.java:175-178} {@code BODY_GOLDEN_RGB}（11 色）。 */
    static final int[] BODY_GOLDEN_RGB = {
            0x887744, 0x998855, 0xAA9966, 0xBBAA77, 0xCCBB88, 0xDDCC99,
            0xCCBB88, 0xBBAA77, 0xAA9966, 0x998855, 0x887744
    };

    /** 照抄 {@code DShanhaiTextUtil.java:244-247} {@code BODY_MOSS_RGB}（9 色）。 */
    static final int[] BODY_MOSS_RGB = {
            0x809870, 0x88A078, 0x90A880, 0x98B088, 0xA0B890,
            0x98B088, 0x90A880, 0x88A078, 0x809870
    };

    /** 照抄 {@code DShanhaiTextUtil.java:208-211} {@code BODY_AURORA_RGB}（9 色）。 */
    static final int[] BODY_AURORA_RGB = {
            0x557766, 0x668877, 0x779988, 0x88AA99, 0x99BBAA,
            0x88AA99, 0x779988, 0x668877, 0x557766
    };

    /** 照抄 {@code DShanhaiTextUtil.java:199-203} {@code BODY_SILVER_RGB}（19 色）。 */
    static final int[] BODY_SILVER_RGB = {
            0xFFFFFF, 0xF0F0F0, 0xE0E0E0, 0xD0D0D0, 0xC0C0C0, 0xB0B0B0,
            0xA0A0A0, 0x909090, 0x808080, 0x707070, 0x808080, 0x909090,
            0xA0A0A0, 0xB0B0B0, 0xC0C0C0, 0xD0D0D0, 0xE0E0E0, 0xF0F0F0, 0xFFFFFF
    };

    // ================================================================== 样式表

    /**
     * 一条样式定义。
     *
     * <p>上游的 {@code StyleDef} 还有 {@code anim} / {@code modSource} / {@code description} 三个字段：
     * <ul>
     *   <li>{@code anim} 只在 {@code DShanhaiTextUtil} 那几个 {@code perCharGradientRGB*} 变体里用，
     *       <b>混入这条渲染路径完全不读它</b>（取证：{@code WobbleFontMixin.java:596-614} 只取
     *       {@code getRGB()} 与 {@code isBody()}）⇒ 我们不搬；</li>
     *   <li>{@code modSource} / {@code description} 是给 KubeJS 枚举用的注释字段，不驱动渲染 ⇒ 不搬。</li>
     * </ul>
     *
     * <p>⚠️ {@code rgb} 数组一律是<b>共享常量</b>，本类从不修改它，调用方也<b>不许</b>改。
     */
    public record Style(int[] rgb, boolean isBody) {}

    /** 样式名 → 定义。名字已 lowercase（照上游 {@code get()} 的做法）。静态块填满后只读。 */
    private static final Map<String, Style> STYLES = new HashMap<>();

    static {
        // ---- 来源 A：横幅（正文慢速，200ms/格） ----
        reg("body_golden", BODY_GOLDEN_RGB, true);
        reg("body_moss", BODY_MOSS_RGB, true);
        reg("body_aurora", BODY_AURORA_RGB, true);
        reg("body_silver", BODY_SILVER_RGB, true);

        // ---- 来源 B/C：物品名的标题样式 ----
        reg("ultimate", ULTIMATE_RAINBOW_RGB, false);
        reg("golden", GOLDEN_RGB, false);
        reg("magic", MAGIC_RGB, false);
        reg("water", WATER_RGB, false);
        reg("aurora", AURORA_RGB, false);
        reg("neon", NEON_RGB, false);
        reg("crimson", CRIMSON_RGB, false);
        reg("cosmic", COSMIC_RGB, false);
        reg("electric", ELECTRIC_RGB, false);

        // ---- 上游未注册、但 getRGB 会回落成 ULTIMATE_RAINBOW_RGB 的三个名字（见类注释 §2.4） ----
        reg("ultimateRainbow", ULTIMATE_RAINBOW_RGB, false);
        reg("gray", ULTIMATE_RAINBOW_RGB, false);
        reg("green", ULTIMATE_RAINBOW_RGB, false);
    }

    private static void reg(String name, int[] rgb, boolean isBody) {
        STYLES.put(name.toLowerCase(Locale.ROOT), new Style(rgb, isBody));
    }

    /**
     * 查样式。{@code null} = 这个名字<b>不在我们移植的清单里</b>。
     *
     * <p>🔴 调用方<b>必须</b>把 {@code null} 当成「退化」处理
     * （{@link ShanhaiTextParser} 的 {@code UNSUPPORTED_STYLE} 分支），
     * <b>不许</b>学上游的 {@code getRGB} 回落到彩虹 —— 那是把没搬的样式伪装成搬了。
     */
    public static Style get(String name) {
        if (name == null) {
            return null;
        }
        return STYLES.get(name.toLowerCase(Locale.ROOT));
    }

    /** 已注册样式数（用于 {@code [SHANHAI-SPEC] font_style_mixin hooked …} 那行日志）。 */
    public static int styleCount() {
        return STYLES.size();
    }

    /** 「已注册的样式名」快照，按名字排序。<b>只给日志/诊断用</b>，不参与渲染。 */
    public static String[] styleNames() {
        String[] names = STYLES.keySet().toArray(new String[0]);
        java.util.Arrays.sort(names);
        return names;
    }

    // ================================================================== 算色

    /**
     * 把「连续相位」拆成整数格 + 格内插值比 —— 照抄 {@code getDynamicColor} 第 622–628 行。
     *
     * @param now        时间源（ms）。同一次绘制里<b>所有字符必须共用同一个值</b>。
     * @param interval   步长（ms/格），由 {@link #effectiveInterval} 给出
     * @param paletteLen 色板长度
     */
    public static long maskedPhase(long now, long interval, int paletteLen) {
        long cycle = interval * paletteLen;
        return now % (cycle > 0 ? cycle : 1);
    }

    /** 格内插值比 {@code frac}（照抄 {@code getDynamicColor} 第 626–628 行）。 */
    public static double fracOf(long masked, long interval) {
        double rawPhase = (double) masked / interval;
        return rawPhase - (int) rawPhase;
    }

    /** 整数相位 {@code intPhase}（照抄 {@code getDynamicColor} 第 627 行）。 */
    public static int intPhaseOf(long masked, long interval) {
        return (int) ((double) masked / interval);
    }

    /**
     * <b>逐字算色的唯一实现</b> —— 照抄 {@code DShanhaiTextUtil.getDynamicColor:621-639}。
     *
     * <p>与上游逐行等价，只把「时间」从内部 {@code getTime()} 改成显式传入的
     * {@code intPhase}/{@code frac}（同一行的所有字符共用，避免行内相位抖动）。
     *
     * @param palette   色板
     * @param slot      该字符的「错位槽」= charIndex（对应上游的 {@code charIndex}）
     * @param intPhase  整数相位
     * @param frac      格内插值比
     * @return {@code 0xRRGGBB}（<b>无 alpha</b>，与上游一致）
     */
    public static int colorForSlot(int[] palette, int slot, int intPhase, double frac) {
        int len = palette.length;
        // idx1 = (intPhase + dir * charIndex) % len，dir = +1（上游 setDirection 默认值，我们从不改）
        int idx1 = (intPhase + slot) % len;
        if (idx1 < 0) {
            idx1 += len;
        }
        int idx2 = (idx1 + 1) % len;
        int c1 = palette[idx1];
        int c2 = palette[idx2];
        int r = (int) (((c1 >> 16) & 0xFF) * (1 - frac) + ((c2 >> 16) & 0xFF) * frac);
        int g = (int) (((c1 >> 8) & 0xFF) * (1 - frac) + ((c2 >> 8) & 0xFF) * frac);
        int b = (int) ((c1 & 0xFF) * (1 - frac) + (c2 & 0xFF) * frac);
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    /**
     * 便捷重载：自己按 {@code now} 算相位再取色。
     *
     * <p>⚠️ <b>逐字渲染请勿在字符循环里调用它</b>（每个字符都会重算一遍相位，且语义上就不再是「同一帧」）。
     * 正确的用法是：循环外算一次 {@code masked} → {@link #intPhaseOf}/{@link #fracOf} → 循环里调
     * {@link #colorForSlot}。本重载只给单字符场景（如诊断）用。
     */
    public static int dynamicColor(int[] palette, int charIndex, int totalLen, boolean isBody, long now) {
        long interval = effectiveInterval(isBody, totalLen);
        long masked = maskedPhase(now, interval, palette.length);
        return colorForSlot(palette, charIndex, intPhaseOf(masked, interval), fracOf(masked, interval));
    }
}
