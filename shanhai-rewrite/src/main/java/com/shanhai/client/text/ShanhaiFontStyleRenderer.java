package com.shanhai.client.text;

import com.shanhai.ShanhaiMod;
import com.shanhai.common.text.ShanhaiTextPalette;
import com.shanhai.common.text.ShanhaiTextParser;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.joml.Matrix4f;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 山海重构 · <b>把 {@code &$…-} 文本画成「随时间流动的色板渐变」的渲染器</b>。
 *
 * <h2>0. 出处</h2>
 * <pre>
 * originals/upstream\gtl_shanhai-dishanhai\src\main\java\com\dishanhai\gt_shanhai\mixin\WobbleFontMixin.java
 *   240-645  onDrawInBatchFCS  ← 本类整体结构照它
 *   34-112   onWidth*         ← 宽度修正（上游用三个 @Inject 分别打三个 width 重载）
 *   442-483  「无位移效果 → 单次绘制」分支
 *   485-638  「有位移效果 → 逐字绘制」分支
 *   628-633  Style + 逐字 drawInBatch + currentX = adv - xOff
 * </pre>
 * 🔴 <b>本类故意与上游不同的三处</b>（每处都写在下面对应位置）：
 * <ol>
 *   <li><b>@Mixin 的注解在 {@link com.shanhai.mixin.ShanhaiFontStyleMixin}，用的是开发名 + refmap</b>，
 *       不是上游的 {@code remap=false}+SRG 名（那条路「只在生产生效、开发期静默失效」）；</li>
 *   <li><b>先探针再分配</b>：上游一进门就 {@code text.accept} 收集成 StringBuilder + ArrayList&lt;Style&gt;
 *       （{@code WobbleFontMixin.java:253-259}）⇒ <b>每段文字都分配</b>。
 *       本类先用<b>零分配</b>探针问「有没有 {@code &}」，没有就直接返回（99%+ 的调用走这条）；</li>
 *   <li><b>不做 § 混合下标换算</b>（见 {@link ShanhaiTextParser} 类注释 §5.3）。</li>
 * </ol>
 *
 * <h2>1. 🔴 为什么渲染代码在本类（普通类）而不是在 mixin 类里</h2>
 * mixin 类<b>不参与 {@code reobfJar} 重混淆</b>（它的成员引用要靠 refmap 在运行期映射），
 * 而本工程<b>现有两个 mixin 都打自己的类</b>、<b>从来没有走过 refmap 路径</b>。
 * 把 {@code Font.drawInBatch(...)} / {@code Font.width(...)} 这些调用放在<b>普通类</b>里，
 * 它们就走<b>本工程已经验证过的老路</b>（{@code reobfJar} 把 dev 名改成 SRG 名，判据就是 jar 里的
 * {@code m_\\d+_} 计数 &gt; 0）；mixin 类里只留 {@code @Inject} 的<b>方法名字符串</b>这一件事需要 refmap。
 * ⇒ <b>把「新基建」的风险面压到最小</b>：refmap 只需要支持「一个方法名映射」。
 *
 * <h2>2. 两条绘制路径（照上游分支条件）</h2>
 * <table border="1">
 *   <tr><th>条件</th><th>路径</th><th>逐字循环里有没有 {@code new}</th></tr>
 *   <tr>
 *     <td>有<b>位移</b>效果（{@code ?} glitch / {@code *} floatX）</td>
 *     <td><b>逐字绘制</b>：每个字符一次 {@code drawInBatch}，带 x/y 偏移</td>
 *     <td><b>没有</b>（复用 acceptor + {@code Style.EMPTY} + 颜色走 {@code color} 参数）</td>
 *   </tr>
 *   <tr>
 *     <td>只有颜色（{@code body_aurora} / {@code body_moss} / {@code body_silver} / 全部物品名）</td>
 *     <td><b>单次绘制</b>：构造一个「每字一个 Style」的 FCS，一次 {@code drawInBatch} 画完整行</td>
 *     <td><b>没有</b>（{@code Style[]} 在循环<b>外</b>按色板长度预计算，循环里只做数组下标）</td>
 *   </tr>
 * </table>
 * <b>为什么单次绘制不做成「逐字 + 颜色参数」</b>（那样能统一成一条路径）：
 * {@code Font.drawInternal} 每次调用都会 {@code new Font$StringRenderOutput(...)}
 * （取证：{@code javap -c net.minecraft.client.gui.Font} 的 {@code drawInternal(FormattedCharSequence…)}
 * 第 0 条指令就是 {@code new Font$StringRenderOutput}）⇒ 逐字调用 = <b>每字多一个 Font 内部对象</b>，
 * 比「每字两个 Style/TextColor」更贵。所以照上游分两条路。
 *
 * <h2>3. 缓存与分配（逐项交代）</h2>
 * <ol>
 *   <li><b>identity 记忆（最快）</b>：{@link FcsMemo} 记住「上一帧那条 {@code FormattedCharSequence} 实例
 *       解析成了什么」。聊天栏/物品名每帧重画的是<b>同一个实例</b>时，这一步<b>连字符串都不用重建</b>。</li>
 *   <li><b>字符串 LRU</b>：{@link ShanhaiTextParser#parse(String)} 内部 256 条，
 *       存<b>肯定结果与否定结果</b>（含 {@code &} 的普通文本每帧不该重解析）。</li>
 *   <li><b>零分配探针</b>：{@link AmpProbe} 是一个<b>复用</b>的 {@link FormattedCharSink}，
 *       找到 {@code &} 就 {@code return false} 提前中止；<b>不捕获任何变量 ⇒ 不产生 lambda 对象</b>。</li>
 *   <li><b>复用缓冲</b>：{@link Collector}（StringBuilder）/ {@link StyleScratch}（{@code Style[]}）/
 *       {@link CharSeq} / {@link LineSeq} 全部 {@code ThreadLocal} 复用，稳态不再分配。</li>
 *   <li><b>每帧仍会分配的两处</b>（如实列出）：
 *       <ul>
 *         <li>有 {@code &} 的那几行每帧一次 {@code StringBuilder.toString()}（= 1 个 String）——
 *             它是缓存表的 key，去不掉；</li>
 *         <li>单次绘制路径按<b>色板槽位数</b>（≤ 色板长度，最多 40）重建 {@code Style} + {@code TextColor}。
 *             <b>这一步在逐字循环外</b>，且与正文长度无关（长行不会线性变贵）。</li>
 *       </ul>
 *       ⇒ 无 {@code &} 的文本（游戏里 99%+ 的绘制调用）<b>一条分配都没有</b>。</li>
 * </ol>
 *
 * <h2>4. 线程与并发</h2>
 * {@link Font#drawInBatch} 主要跑在客户端主线程（渲染线程），但本类<b>不假设</b>只有那一个线程：
 * 所有可复用状态都是 {@link ThreadLocal}（各自一份，互不干扰），
 * 唯一共享的是 {@link ShanhaiTextParser} 里那个 {@code synchronizedMap} 缓存（值不可变）。
 *
 * <h2>5. 失败模式（本项目铁律：两种失败必须长得不一样）</h2>
 * <ul>
 *   <li><b>注入失败</b> ⇒ 混入的 {@code require = 1} 让游戏<b>启动即崩</b>（响亮）；</li>
 *   <li><b>算色/绘制抛异常</b> ⇒ 本类 {@code catch (Throwable)} 后<b>安静地不生效</b>
 *       （这次没特效，<b>绝不崩</b>），并打一行 {@code font_style_error} 便于事后定位；</li>
 *   <li><b>注入成功但没生效</b> ⇒ 客户端日志里<b>没有</b>
 *       {@code [SHANHAI-SPEC] font_style_mixin hooked …} 这行 ⇒ 一眼区分「没注入」和「算错了」。</li>
 * </ul>
 */
public final class ShanhaiFontStyleRenderer {

    private ShanhaiFontStyleRenderer() {}

    /**
     * 已经注入成功的方法清单 —— <b>写死是刻意的</b>：它是「编译期事实」的快照，
     * 与 {@link com.shanhai.mixin.ShanhaiFontStyleMixin} 上的 {@code @Inject} 一一对应。
     * 改了混入就必须改这里（否则那行日志会撒谎）。
     */
    public static final String HOOK_METHODS =
            "{drawInBatch:FormattedCharSequence, width:FormattedCharSequence, width:FormattedText, width:String}";

    /** 收集字符串时最多接受多少个码点，超了就当「不是我们的码」。防止病态长文本把热路径拖垮。 */
    private static final int MAX_CODEPOINTS = 1024;

    private static final AtomicBoolean HOOK_LOGGED = new AtomicBoolean(false);

    // ================================================================== 诊断

    /**
     * 打一行 {@code [SHANHAI-SPEC] font_style_mixin hooked …} —— <b>全工程唯一</b>的「注入成功」判据。
     *
     * <p>🔴 <b>这行是"注入成功"和"算错了"的唯一分界线</b>：屏幕上「没有特效」有两种原因 ——
     * 混入没生效、或者算色算错了。两者肉眼完全一样（本项目血账：两种失败长得一样 ⇒ 那次运行信息量为 0）。
     * 有了这行：<b>日志里有它 = 注入好了（问题在算色）；日志里没它 = 注入没生效</b>。
     *
     * <p>⚠️ 它在<b>第一次真正被调用</b>时才打（不是类加载时）：这样它额外证明了
     * 「注入点真的被执行了」，而不只是「配置被读到了」。
     * ⚠️ <b>专用服务端永远不会打这行</b>：本混入列在 {@code shanhai.mixin.json} 的 {@code client} 数组里，
     * 无头专服不加载 client 混入 ⇒ 这行<b>只能在客户端日志里找</b>。
     */
    private static void logHookedOnce() {
        if (HOOK_LOGGED.compareAndSet(false, true)) {
            ShanhaiMod.LOGGER.info("[SHANHAI-SPEC] font_style_mixin hooked methods={} paletteStyles={}",
                    HOOK_METHODS, ShanhaiTextPalette.styleCount());
        }
    }

    /** 异常只在前两次打全，之后限流（5 秒一行 + suppressed 计数）—— 渲染线程不许被日志拖死。 */
    private static final long ERR_MIN_GAP_MS = 5000L;
    private static final java.util.concurrent.atomic.AtomicLong LAST_ERR_MS =
            new java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE / 2);
    private static final java.util.concurrent.atomic.AtomicLong ERR_SUPPRESSED =
            new java.util.concurrent.atomic.AtomicLong();

    private static void logError(String where, Throwable t) {
        long now = System.currentTimeMillis();
        long last = LAST_ERR_MS.get();
        if (now - last < ERR_MIN_GAP_MS || !LAST_ERR_MS.compareAndSet(last, now)) {
            ERR_SUPPRESSED.incrementAndGet();
            return;
        }
        long suppressed = ERR_SUPPRESSED.getAndSet(0L);
        // 🔴 刻意用 warn + 只带异常类名与 message：渲染线程上打整条堆栈会让卡顿变成雪崩。
        ShanhaiMod.LOGGER.warn("[SHANHAI-SPEC] font_style_error where={} ex={} msg={} suppressed={}",
                where, t.getClass().getName(), String.valueOf(t.getMessage()), suppressed);
    }

    // ================================================================== 可复用状态（ThreadLocal）

    /** 渲染重入标记：我们自己发起的逐字 {@code drawInBatch} 会再次进来，必须立刻放行，不能递归。 */
    private static final ThreadLocal<boolean[]> IN_RENDER = ThreadLocal.withInitial(() -> new boolean[1]);

    /** 宽度重入标记（{@code width(cleanText)} 会再次进来，同理）。 */
    private static final ThreadLocal<boolean[]> IN_WIDTH = ThreadLocal.withInitial(() -> new boolean[1]);

    /** 「这次调用已经画出去东西了吗」—— 异常兜底时决定「取消原渲染」还是「放回原渲染」。 */
    private static final ThreadLocal<boolean[]> STARTED = ThreadLocal.withInitial(() -> new boolean[1]);

    /** 零分配 {@code &} 探针（复用实例，不捕获变量 ⇒ 不是 lambda 对象）。 */
    private static final ThreadLocal<AmpProbe> PROBE = ThreadLocal.withInitial(AmpProbe::new);

    /** 零分配字符串收集器。 */
    private static final ThreadLocal<Collector> COLLECTOR = ThreadLocal.withInitial(Collector::new);

    /** 「上一帧那条 FCS → 解析结果」的记忆。 */
    private static final ThreadLocal<FcsMemo> MEMO = ThreadLocal.withInitial(FcsMemo::new);

    /** 单次绘制路径的 {@code Style[]} 暂存（容量按色板长度增长，稳态不再分配）。 */
    private static final ThreadLocal<StyleScratch> STYLES = ThreadLocal.withInitial(StyleScratch::new);

    /** 逐字绘制路径的复用 acceptor。 */
    private static final ThreadLocal<CharSeq> CHAR_SEQ = ThreadLocal.withInitial(CharSeq::new);

    /** 单次绘制路径的复用 FCS。 */
    private static final ThreadLocal<LineSeq> LINE_SEQ = ThreadLocal.withInitial(LineSeq::new);

    // ================================================================== 入口：绘制

    /**
     * {@code Font.drawInBatch(FormattedCharSequence, …)} 的接管实现。
     *
     * @return {@code null} = <b>我们不管这条</b>（调用方必须原样交回原版渲染）；
     *         非 null = 已画完，值为 x 方向推进量
     */
    public static Integer renderFcs(Font self,
                                    FormattedCharSequence text,
                                    float x, float y, int color, boolean shadow,
                                    Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode,
                                    int packedLight, int packedOverlay) {
        logHookedOnce();
        if (text == null || self == null) {
            return null;
        }
        boolean[] inRender = IN_RENDER.get();
        if (inRender[0]) {
            return null;
        }
        // 异常兜底的判据必须在【整个 try 之外】清零：否则异常若发生在本方法前段，
        // STARTED 里留着的是上一次调用（可能已经画过字）的陈旧值 ⇒ 会误判成"已经画了"。
        final boolean[] started = STARTED.get();
        started[0] = false;
        try {
            // ---- 第 1 步：零分配探针（99%+ 的调用在这里就返回了） ----
            if (!probeHasAmp(text)) {
                return null;
            }

            // ---- 第 2 步：拿解析结果（先按实例记忆，再按字符串缓存） ----
            ShanhaiTextParser.Parsed parsed = lookup(text);
            if (parsed == null || !parsed.ours()) {
                return null;
            }

            // ---- 第 3 步：画 ----
            inRender[0] = true;
            try {
                if (parsed.palette() == null) {
                    return drawDegraded(self, parsed, x, y, color, shadow, matrix, buffer, mode,
                            packedLight, packedOverlay, started);
                }
                return drawStyled(self, parsed, x, y, color, shadow, matrix, buffer, mode,
                        packedLight, packedOverlay, started);
            } finally {
                inRender[0] = false;
            }
        } catch (Throwable t) {
            logError("drawInBatch", t);
            // 🔴 「安静地不生效」：已经画出去一半就别让原版再画一遍（会重影）；
            //    一个字都还没画就放回去让原版整行照常画（会是白字，但至少不消失、不重影）。
            return STARTED.get()[0] ? Integer.valueOf(Math.round(x)) : null;
        }
    }

    // ================================================================== 入口：宽度

    /**
     * {@code Font.width(FormattedCharSequence)} 的修正实现。
     *
     * @return {@code null} = 不管；非 null = 该用的宽度
     */
    public static Integer widthFcs(Font self, FormattedCharSequence text) {
        logHookedOnce();
        if (text == null || self == null) {
            return null;
        }
        if (IN_WIDTH.get()[0]) {
            return null;
        }
        if (!probeHasAmp(text)) {
            return null;
        }
        return widthOf(self, extract(text));
    }

    /** {@code Font.width(FormattedText)} 的修正实现。 */
    public static Integer widthFormattedText(Font self, net.minecraft.network.chat.FormattedText text) {
        logHookedOnce();
        if (text == null || self == null) {
            return null;
        }
        if (IN_WIDTH.get()[0]) {
            return null;
        }
        String raw;
        try {
            raw = text.getString();
        } catch (Throwable t) {
            logError("width.getString", t);
            return null;
        }
        if (!ShanhaiTextParser.containsCode(raw)) {
            return null;
        }
        return widthOf(self, raw);
    }

    /** {@code Font.width(String)} 的修正实现。 */
    public static Integer widthString(Font self, String raw) {
        logHookedOnce();
        if (raw == null || raw.isEmpty() || self == null) {
            return null;
        }
        if (IN_WIDTH.get()[0]) {
            return null;
        }
        if (!ShanhaiTextParser.containsCode(raw)) {
            return null;
        }
        return widthOf(self, raw);
    }

    /**
     * 宽度修正本体。
     *
     * <p>为什么要它：{@code &$?body_golden-} 是 <b>16 个「可见字符」</b>，
     * 不修正的话聊天栏居中 / tooltip 居中 / {@code drawCenteredString} 都会把这一行算「胖」，
     * 文字会明显缩进或错位（方案 md §7.2 验收第 6 条）。
     * 修法照上游：算出 {@code cleanText} 的真实宽度顶回去。
     */
    private static Integer widthOf(Font self, String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            ShanhaiTextParser.Parsed parsed = ShanhaiTextParser.parse(raw);
            if (!parsed.ours()) {
                return null;
            }
            boolean[] inWidth = IN_WIDTH.get();
            inWidth[0] = true;
            try {
                int w = self.width(parsed.cleanText());
                return w > 0 ? Integer.valueOf(w) : null;
            } finally {
                inWidth[0] = false;
            }
        } catch (Throwable t) {
            logError("width", t);
            return null;
        }
    }

    // ================================================================== 两条绘制路径

    /**
     * <b>退化绘制</b>：样式名没移植（{@code palette == null}）⇒ 剥掉码、按普通文字画，<b>不染任何颜色</b>。
     *
     * <p>用 {@code Font.drawInBatch(String, …)} 一次画完 —— 它是<b>另一个重载</b>
     * （取证：{@code javap} 显示它转调 {@code drawInBatch(String,…,boolean)}，<b>不经过 FCS 那个重载</b>），
     * 所以<b>不会</b>再次进入本混入。
     */
    private static int drawDegraded(Font self, ShanhaiTextParser.Parsed parsed,
                                    float x, float y, int color, boolean shadow,
                                    Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode,
                                    int packedLight, int packedOverlay, boolean[] started) {
        started[0] = true;
        return self.drawInBatch(parsed.cleanText(), x, y, color, shadow, matrix, buffer, mode,
                packedLight, packedOverlay);
    }

    /**
     * <b>正文绘制</b>：{@code prefix} 用调用方给的颜色原样画，{@code body} 用色板逐字流动色画。
     *
     * <p>⚠️ 相位（{@code intPhase} / {@code frac}）在<b>循环外算一次</b>，
     * 行内所有字符共用 —— 这是「同一帧」的定义；每个字符各读一次时钟会让相位自己抖。
     */
    private static int drawStyled(Font self, ShanhaiTextParser.Parsed parsed,
                                  float x, float y, int color, boolean shadow,
                                  Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode,
                                  int packedLight, int packedOverlay, boolean[] started) {
        final String body = parsed.body();
        final int[] palette = parsed.palette();
        final int len = palette.length;
        final int bodyLen = body.length();
        final long now = System.currentTimeMillis();

        // 相位：三项之一「时间流动」。照抄 getDynamicColor 第 622-628 行。
        final long interval = ShanhaiTextPalette.effectiveInterval(parsed.isBody(), bodyLen);
        final long masked = ShanhaiTextPalette.maskedPhase(now, interval, len);
        final int intPhase = ShanhaiTextPalette.intPhaseOf(masked, interval);
        final double frac = ShanhaiTextPalette.fracOf(masked, interval);

        // & 之前的文字（我们的 34+6 行里这一般是空的，但机制照上游保留）
        float curX = x;
        if (!parsed.prefix().isEmpty()) {
            curX = self.drawInBatch(parsed.prefix(), curX, y, color, shadow, matrix, buffer, mode,
                    packedLight, packedOverlay);
            started[0] = true;
        }

        if (parsed.hasMotion()) {
            return drawPerChar(self, parsed, body, palette, intPhase, frac, now, curX, y, color, shadow,
                    matrix, buffer, mode, packedLight, packedOverlay, started);
        }
        return drawWholeLine(self, body, palette, len, bodyLen, intPhase, frac, curX, y, color, shadow,
                matrix, buffer, mode, packedLight, packedOverlay, started);
    }

    /**
     * <b>逐字绘制</b>（有位移效果时）。
     *
     * <p>逐字循环里<b>一个 {@code new} 都没有</b>：
     * 复用 {@link CharSeq} 当 FCS、样式恒为 {@code Style.EMPTY}、颜色走 {@code drawInBatch} 的
     * {@code color} 参数 —— 取证：{@code Font$StringRenderOutput.accept} 的字节码里
     * {@code TextColor textcolor = style.getColor(); int j = textcolor != null ? textcolor.getValue() : color;}
     * ⇒ <b>样式没带色时就用参数色</b>，所以「每字一个 Style」不是必需的。
     *
     * <p>{@code currentX = adv - xOff} 照抄上游 {@code WobbleFontMixin.java:633}：
     * {@code drawInBatch} 的返回值已经把 xOff 算进去了，减掉才是「这个字真正的推进量」。
     */
    private static int drawPerChar(Font self, ShanhaiTextParser.Parsed parsed,
                                   String body, int[] palette, int intPhase, double frac, long now,
                                   float curX, float y, int color, boolean shadow,
                                   Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode,
                                   int packedLight, int packedOverlay, boolean[] started) {
        final CharSeq seq = CHAR_SEQ.get();
        final int bodyLen = body.length();
        int index = 0;
        for (int i = 0; i < bodyLen; ) {
            final int cp = body.codePointAt(i);

            int cc = ShanhaiTextPalette.colorForSlot(palette, index, intPhase, frac);

            float xOff = 0.0f;
            float yOff = 0.0f;
            if (parsed.floatX()) {
                xOff += ShanhaiTextParser.waveX(index, now);
            }
            if (parsed.glitch()) {
                xOff += ShanhaiTextParser.glitchX(index, now);
                yOff += ShanhaiTextParser.glitchY(index, now);
                cc = ShanhaiTextParser.applyGlitchColor(cc, index, now);
            }

            seq.prepare(cp);
            float adv = self.drawInBatch(seq, curX + xOff, y + yOff, cc, shadow,
                    matrix, buffer, mode, packedLight, packedOverlay);
            curX = adv - xOff;
            started[0] = true;

            index++;
            i += Character.charCount(cp);
        }
        return Math.round(curX);
    }

    /**
     * <b>单次绘制</b>（只有颜色、没有位移效果时）。这是 34 条物品名 + 横幅 4 行的路径。
     *
     * <p>🔴「逐字循环零分配」在这里是这样做到的：因为
     * {@code idx1 = (intPhase + charIndex) % len}，所以<b>整行的颜色只有 {@code len} 种可能</b>
     * （{@code len} = 色板长度，最多 40），且与正文长度无关
     * ⇒ 在循环<b>外</b>按「槽位」预计算 {@code Style[]}，循环里只做 {@code styles[k % m]} 下标。
     */
    private static int drawWholeLine(Font self, String body, int[] palette, int len, int bodyLen,
                                     int intPhase, double frac,
                                     float curX, float y, int color, boolean shadow,
                                     Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode,
                                     int packedLight, int packedOverlay, boolean[] started) {
        if (bodyLen == 0) {
            return Math.round(curX);
        }
        // 需要的槽位数 = min(正文长度, 色板长度)：正文更长时颜色按色板回绕，更短时用前 bodyLen 个槽。
        final int slots = Math.min(bodyLen, len);
        final Style[] scratch = STYLES.get().ensure(slots);
        for (int k = 0; k < slots; k++) {
            int rgb = ShanhaiTextPalette.colorForSlot(palette, k, intPhase, frac);
            scratch[k] = Style.EMPTY.withColor(TextColor.fromRgb(rgb));
        }

        final LineSeq seq = LINE_SEQ.get();
        seq.prepare(body, scratch, slots);
        started[0] = true;
        return self.drawInBatch(seq, curX, y, color, shadow, matrix, buffer, mode, packedLight, packedOverlay);
    }

    // ================================================================== 提取 / 记忆 / 探针

    /** 零分配探针：这段 FCS 里有没有 {@code &}？找到就提前中止。 */
    private static boolean probeHasAmp(FormattedCharSequence text) {
        AmpProbe probe = PROBE.get();
        probe.hit = false;
        text.accept(probe);
        return probe.hit;
    }

    /**
     * 按「实例」记忆解析结果；实例对不上时退回按字符串缓存。
     *
     * <p>为什么值得：聊天栏横幅每帧重画的往往是<b>同一个 {@code FormattedCharSequence} 实例</b>
     * ⇒ 命中时连 {@code StringBuilder} 都不用碰，<b>零分配</b>。
     * 实例相同就一定内容相同（{@code FormattedCharSequence} 是纯函数式的 accept 回调），所以这个记忆是安全的。
     */
    private static ShanhaiTextParser.Parsed lookup(FormattedCharSequence text) {
        FcsMemo memo = MEMO.get();
        if (memo.seq == text) {
            return memo.parsed;
        }
        String raw = extract(text);
        if (raw == null) {
            // 超长文本：记住「不管」，免得每帧再来一遍
            memo.seq = text;
            memo.parsed = null;
            return null;
        }
        ShanhaiTextParser.Parsed parsed = ShanhaiTextParser.parse(raw);
        memo.seq = text;
        memo.parsed = parsed;
        return parsed;
    }

    /** 把 FCS 收集成字符串。用复用的 {@link Collector}，只在这里产生 1 个 String。 */
    private static String extract(FormattedCharSequence text) {
        Collector collector = COLLECTOR.get();
        collector.reset();
        text.accept(collector);
        if (collector.overflow) {
            return null;
        }
        return collector.sb.toString();
    }

    // ================================================================== 复用件

    /** 复用探针：不捕获外部变量 ⇒ 不是 lambda 对象，可以长期持有。 */
    private static final class AmpProbe implements FormattedCharSink {
        boolean hit;

        @Override
        public boolean accept(int index, Style style, int codepoint) {
            if (codepoint == '&') {
                hit = true;
                return false; // 提前中止，后面的字不用再走
            }
            return true;
        }
    }

    /** 复用收集器。{@code appendCodePoint} 不分配；只有最后的 {@code toString()} 会。 */
    private static final class Collector implements FormattedCharSink {
        final StringBuilder sb = new StringBuilder(128);
        boolean overflow;

        void reset() {
            sb.setLength(0);
            overflow = false;
        }

        @Override
        public boolean accept(int index, Style style, int codepoint) {
            if (sb.length() >= MAX_CODEPOINTS) {
                overflow = true;
                return false;
            }
            sb.appendCodePoint(codepoint);
            return true;
        }
    }

    /** 「上一帧那条 FCS → 解析结果」的记忆。只存 1 条，不增长。 */
    private static final class FcsMemo {
        FormattedCharSequence seq;
        ShanhaiTextParser.Parsed parsed;
    }

    /** {@code Style[]} 暂存，按需增长到色板长度。 */
    private static final class StyleScratch {
        Style[] array = new Style[16];

        Style[] ensure(int n) {
            if (array.length < n) {
                array = new Style[Math.max(n, array.length * 2)];
            }
            return array;
        }
    }

    /**
     * 逐字绘制用的复用 FCS。
     *
     * <p>为什么复用是安全的：{@code Font.drawInBatch} → {@code drawInternal} →
     * {@code renderText} 会在<b>同一个调用栈里</b>把 {@code accept(sink)} 走完并 {@code finish()}
     * （取证：{@code javap -c} 的 {@code renderText(FormattedCharSequence…)} 里
     * {@code invokeinterface FormattedCharSequence.accept} 后面紧跟 {@code StringRenderOutput.finish}），
     * <b>不会把这个 FCS 存下来等下次用</b>。
     */
    private static final class CharSeq implements FormattedCharSequence {
        private int codepoint;

        void prepare(int codepoint) {
            this.codepoint = codepoint;
        }

        @Override
        public boolean accept(FormattedCharSink sink) {
            return sink.accept(0, Style.EMPTY, codepoint);
        }
    }

    /** 单次绘制用的复用 FCS：按码点遍历 {@code text}，第 k 个字用 {@code styles[k % slots]}。 */
    private static final class LineSeq implements FormattedCharSequence {
        private String text = "";
        private Style[] styles;
        private int slots = 1;

        void prepare(String text, Style[] styles, int slots) {
            this.text = text;
            this.styles = styles;
            this.slots = Math.max(slots, 1);
        }

        @Override
        public boolean accept(FormattedCharSink sink) {
            int index = 0;
            int n = text.length();
            for (int i = 0; i < n; ) {
                int cp = text.codePointAt(i);
                if (!sink.accept(index, styles[index % slots], cp)) {
                    return false;
                }
                index++;
                i += Character.charCount(cp);
            }
            return true;
        }
    }
}
