package com.shanhai.common.text;

import com.shanhai.ShanhaiMod;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 山海重构 · <b>{@code &$…-} 文本码的解析器</b>（客户端 Font 混入的唯一入口）。
 *
 * <h2>0. 出处</h2>
 * <pre>
 * originals/upstream\gtl_shanhai-dishanhai\src\main\java\com\dishanhai\gt_shanhai\api\TextFormatParser.java
 *   parseFormatting       : 59-89    ← 语法结构（& / $ / - 三个位置关系）
 *   parseFlags            : 147-177  ← 效果字符位 & 样式名提取
 *   isEffectSymbol        : 196-204  ← 11 个效果字符的全表
 *   calcWaveX             : 239-241  ← floatX 的数学式
 *   calcGlitchX / Y       : 255-265  ← glitch 的像素位移
 *   applyGlitchColor      : 294-299  ← glitch 的偶发掺色
 *   mixColor              : 301-314
 * </pre>
 * 一律<b>逐行照抄</b>，只删掉我们用不到的分支（见 §3）。
 *
 * <h2>1. 语法（照抄 {@code parseFormatting}）</h2>
 * <pre>
 *   [前缀] &lt;效果字符…&gt; $ &lt;样式名&gt; - &lt;正文&gt;
 *   ↑ prefix          ↑ 效果字符可以写在 $ 之前，也可以写在 $ 之后（两种都支持）
 * </pre>
 * 规则逐条（对应上游 63–88 行）：
 * <ol>
 *   <li>从<b>第一个</b> {@code &} 开始；{@code &} 之前的文本是 {@code prefix}（原样保留、原样绘制）；</li>
 *   <li>{@code &} 之后到<b>第一个 {@code -}</b> 之间的整段 = {@code codePart}；</li>
 *   <li>{@code codePart} 里 {@code $} <b>之后</b>、剥掉紧邻的效果字符，剩下的就是<b>样式名</b>；</li>
 *   <li>{@code -} 之后 = 正文；{@code cleanText = prefix + 正文}（<b>宽度按它算</b>）。</li>
 * </ol>
 *
 * <h2>2. 🔴 效果字符全表（11 个 + {@code $} 分隔符）—— 逐个交代「做没做」</h2>
 * <table border="1">
 *   <tr><th>字符</th><th>旗标</th><th>语义</th><th>上游公式</th><th>本工程</th></tr>
 *   <tr><td>{@code ~}</td><td>floatY</td><td>上下波浪</td><td>{@code sin(i*0.5 + t*0.002) * 3.0}</td><td><b>没做</b></td></tr>
 *   <tr><td>{@code *}</td><td>floatX</td><td>左右波浪</td><td>{@code cos(i*0.7 + t*0.0015) * 2.0}</td><td><b>✅ 做了</b>（横幅第 2 行用）</td></tr>
 *   <tr><td>{@code %}</td><td>shake</td><td>抖动</td><td>{@code sin(t*0.02 + seed*0.001) * (seed%100) * 0.01}</td><td><b>没做</b></td></tr>
 *   <tr><td>{@code @}</td><td>circle</td><td>圆周</td><td>{@code angle=t*0.003+i*0.5 → (cos*3, sin*3)}</td><td><b>没做</b></td></tr>
 *   <tr><td>{@code !}</td><td>bounce</td><td>弹跳</td><td>{@code |sin(t*0.005 + i*1.2)| * 4.0}</td><td><b>没做</b></td></tr>
 *   <tr><td>{@code ^}</td><td>scan</td><td>扫光</td><td>亮带位置 {@code (t/45)%period - 4}，{@code dist&lt;=3} 混白 {@code *0.75}</td><td><b>没做</b></td></tr>
 *   <tr><td>{@code ?}</td><td>glitch</td><td>闪烁 + 位移</td><td>见 §2.1</td><td><b>✅ 做了</b>（横幅首尾行用）</td></tr>
 *   <tr><td>{@code +}</td><td>breathe</td><td>呼吸明暗</td><td>{@code sin(t*0.003 + i*0.18) → 混白 (w+1)*0.22}</td><td><b>没做</b></td></tr>
 *   <tr><td>{@code &gt;}</td><td>chase</td><td>环形追光</td><td>{@code pos=(t/70)%len}，{@code dist&lt;=2} 混白 {@code *0.9}</td><td><b>没做</b></td></tr>
 *   <tr><td>{@code `}</td><td>obfuscated</td><td>混淆</td><td>{@code Style.withObfuscated(true)}</td><td><b>没做</b></td></tr>
 *   <tr><td>{@code #}</td><td>outline</td><td>描边</td><td><b>上游自己就是死旗标</b>（只写不读）</td><td><b>没做</b>（上游也没做）</td></tr>
 * </table>
 * <b>为什么只做 2 个</b>：用户裁决「只移植目前我们要用到的」。实测我们的三类文本里
 * <b>只出现了 {@code ?} 与 {@code *}</b>（横幅），34 条物品名<b>一个效果字符都没有</b>
 * （取证：{@code _tools\extract_style_inventory.ps1} 的 {@code FLAGS (distinct=0)}）。<br>
 * <b>没做的怎么退化</b>：见 §3 —— <b>剥离该字符、保留色板颜色</b>，限流打一行 WARN。
 *
 * <h3>2.1 {@code ?} glitch 的三段（照抄上游）</h3>
 * <pre>
 * 位移  calcGlitchX(i,t) = (t/55 + i*17) % 7 &gt; 1 ? 0 : ((t/55 + i*17) % 3 - 1) * 1.5
 * 位移  calcGlitchY(i,t) = (t/65 + i*23) % 9 &gt; 1 ? 0 : ((t/65 + i*23) % 3 - 1) * 1.0
 * 掺色  applyGlitchColor: (t/60 + i*31) % 11 == 0 → 混 0x55FFFF 65%；% 13 == 0 → 混 0xFF5555 55%
 * </pre>
 * ⚠️ 注意：上游的 {@code ?} <b>既掺色也位移</b>。只做掺色不做位移会少一半观感，
 * 所以 {@code ?} 走「逐字绘制」那条路径（见 {@code ShanhaiFontStyleRenderer}）。
 *
 * <h2>3. 🔴 退化规则（用户裁决 ③：最坏是「没特效」，绝不是「显示乱码」）</h2>
 * <table border="1">
 *   <tr><th>遇到什么</th><th>怎么做</th></tr>
 *   <tr>
 *     <td><b>没移植的样式名</b>（{@code ShanhaiTextPalette.get} 返回 null）</td>
 *     <td><b>剥掉整段码</b>（{@code &…-} 全部不画），按<b>普通文字、无颜色</b>绘制；限流 WARN 记下名字</td>
 *   </tr>
 *   <tr>
 *     <td><b>没实现的效果字符</b>（上表里「没做」的那些）</td>
 *     <td><b>剥掉那个字符本身</b>（它本来就不会被画出来），<b>保留色板颜色</b>，只丢掉那个效果；限流 WARN</td>
 *   </tr>
 * </table>
 * 第二条与第一条不同（不当成致命退化）的理由：色板是<b>已经移植好</b>的，
 * 丢的是动效不是颜色 ⇒ 把颜色一起丢掉是<b>无谓的损失</b>；而样式名不在表里时我们<b>根本不知道配什么色</b>，
 * 只能不染。两条都满足用户那句「最坏是没特效，不是显示乱码」。
 *
 * <p>⚠️ <b>WARN 的限流</b>照本工程既有做法（{@code GtlAddClientCompat.HIDE_LOG_MIN_GAP_TICKS}）：
 * <b>每 5000 ms 最多一行</b>，并带上被折叠掉的行数（只限流不报数 = 静默，本工程的血账之一）。
 *
 * <h2>4. 缓存（热路径要求）</h2>
 * 解析是<b>纯函数</b>（只看输入字符串）⇒ 可以用「原始串 → 解析结果」的 LRU 表缓存。
 * 表大小 {@value #CACHE_MAX}，超出按访问顺序淘汰最旧的。
 * 缓存也存<b>否定结果</b>（{@code ours=false}）：聊天栏里含 {@code &} 的普通文本<b>不该每帧重解析一遍</b>。
 * 表本身用 {@code synchronizedMap} 包一层：渲染主要在客户端主线程，但本类<b>不假设</b>只有那一个线程。
 *
 * <h2>5. 已知边界（如实列出，别当成 bug 以外的解释）</h2>
 * <ol>
 *   <li><b>只处理第一个 {@code &}</b>（照抄上游）。一条文本里写两个 {@code &$…-} 码时，
 *       第二个会<b>原样显示</b>。我们的 34 条物品名与 6 行横幅<b>每行只有一个码</b>，所以不构成问题。</li>
 *   <li><b>不实现上游的 {@code parseCompactFormatting}</b>（没有 {@code -} 的那种写法 {@code &$ultimate旋转彩虹}）。
 *       我们的文本<b>一律带 {@code -}</b>；没有 {@code -} 时本类直接判「不是我们的码」⇒ 交回原版渲染，
 *       <b>不会</b>误伤普通文本里的 {@code &}。</li>
 *   <li><b>没有 § 混合支持</b>：上游能在同一行里同时处理 {@code §a} 与 {@code &$…-}
 *       （{@code WobbleFontMixin.java:263-297}）。我们<b>不做这一层</b>：
 *       若正文里同时出现 {@code §} 与 {@code &$}，本类判「不是我们的码」，交回原版渲染。
 *       理由：我们的 34+6 行文本<b>不存在这种混合</b>（横幅里带 § 的哪几行本来也没有 {@code &$}），
 *       而那一层的下标换算（去 § 后重建 per-char style 数组）是我<b>无法在本机验证</b>的复杂度 —— 不写没把握的热路径代码。</li>
 *   <li><b>{@code -} 后是空串</b>时（{@code &$ultimate-}）判「不是我们的码」。
 *       与上游不同（上游会把整条原文当正文绘制 ⇒ <b>反而会把码画出来</b>），我们选更保守的那个。</li>
 * </ol>
 */
public final class ShanhaiTextParser {

    private ShanhaiTextParser() {}

    /** 11 个效果字符 —— 照抄上游 {@code isEffectSymbol:196-204}（顺序也照抄）。 */
    private static final String EFFECT_SYMBOLS = "@~#*%!^?+>`";

    /** glitch 掺色用的两个目标色（照抄上游 {@code applyGlitchColor}）。 */
    private static final int GLITCH_CYAN = 0x55FFFF;
    private static final int GLITCH_RED = 0xFF5555;

    /** 缓存条数上限。<b>只在构造解析表时用到一次</b>，不是每帧判据；给个够放的数就行。 */
    public static final int CACHE_MAX = 256;

    private static final Map<String, Parsed> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Parsed> eldest) {
                    return size() > CACHE_MAX;
                }
            });

    // ------------------------------------------------------------------ 解析结果

    /**
     * 一条 {@code &$…-} 文本的解析结果。<b>不可变</b>（会被放进缓存、被多个线程读）。
     *
     * @param ours       {@code false} = 这条文本<b>不是我们的码</b>，调用方<b>必须原样交回原版渲染</b>
     * @param cleanText  {@code prefix + body}，即「该画出来的字」；<b>宽度修正也用它</b>
     * @param prefix     {@code &} 之前的原文（原样保留，原样绘制，用的还是调用方传进来的 color）
     * @param body       {@code -} 之后的正文（色板染色只作用于这一段）
     * @param palette    色板；{@code null} = <b>退化模式</b>（没移植的样式名）⇒ 无颜色
     * @param isBody     {@code true} = 正文慢速样式（200 ms/格）
     * @param glitch     是否带 {@code ?}
     * @param floatX     是否带 {@code *}
     * @param styleName  解析出的样式名（诊断用；可能是没移植的名字）
     * @param unsupported 解析时遇到但<b>没实现</b>的效果字符（诊断用，可为空串）
     */
    public record Parsed(
            boolean ours,
            String cleanText,
            String prefix,
            String body,
            int[] palette,
            boolean isBody,
            boolean glitch,
            boolean floatX,
            String styleName,
            String unsupported) {

        /** 有没有「会改变像素位置」的效果 —— 有就得走逐字绘制，没有可以一次画完整行。 */
        public boolean hasMotion() {
            return glitch || floatX;
        }
    }

    /** 「不是我们的码」的单一实例（缓存里的否定结果也用它，省对象）。 */
    private static final Parsed NOT_OURS = new Parsed(
            false, "", "", "", null, false, false, false, "", "");

    // ================================================================== 探针

    /**
     * <b>零分配</b>的「这条文本可能含我们的码吗」探针。
     *
     * <p>判据就是「有没有 {@code &}」—— 与上游 {@code containsSpecialFormatting} 同款。
     * <b>绝不</b>在这里做子串/正则：{@code Font.drawInBatch} 是每帧每段文字都走的收口，
     * 这里多分配一次，整个游戏都要付钱。
     *
     * <p>调用方自己在字符回调里做判断，本方法只给常量。
     */
    public static char probeChar() {
        return '&';
    }

    // ================================================================== 解析

    /** 便捷判定：这段文本里有没有 {@code &}（不分配）。 */
    public static boolean containsCode(String raw) {
        return raw != null && !raw.isEmpty() && raw.indexOf('&') >= 0;
    }

    /**
     * 解析一条文本。<b>带缓存</b>，同一个串第二次进来不再解析。
     *
     * @param raw {@code null}/空串会直接返回 {@link #NOT_OURS}
     */
    public static Parsed parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return NOT_OURS;
        }
        Parsed cached = CACHE.get(raw);
        if (cached != null) {
            return cached;
        }
        Parsed fresh = parseUncached(raw);
        CACHE.put(raw, fresh);
        return fresh;
    }

    private static Parsed parseUncached(String raw) {
        int ampIdx = raw.indexOf('&');
        if (ampIdx < 0) {
            return NOT_OURS;
        }
        int cursor = ampIdx + 1;
        int dashIdx = raw.indexOf('-', cursor);
        if (dashIdx < cursor) {
            // 没有 `-`：不是 `&$…-` 这个形状。不学上游的 compact 分支（见类注释 §5.2）。
            return NOT_OURS;
        }
        String codePart = raw.substring(cursor, dashIdx);
        int dollarIdx = codePart.indexOf('$');
        if (dollarIdx < 0) {
            // `&` 后面一段里没有 `$` ⇒ 这就是一条普通含 `&` 的文本（例如 "AT&T - 说明"）。
            return NOT_OURS;
        }

        // ---- 效果字符（照抄 parseFlags:148-159：在 codePart 的全段里找，$ 前后都认） ----
        boolean glitch = codePart.indexOf('?') >= 0;
        boolean floatX = codePart.indexOf('*') >= 0;
        StringBuilder unsupported = new StringBuilder();
        for (int i = 0; i < EFFECT_SYMBOLS.length(); i++) {
            char sym = EFFECT_SYMBOLS.charAt(i);
            if (sym == '?' || sym == '*') {
                continue; // 这两个我们做了
            }
            if (codePart.indexOf(sym) >= 0) {
                unsupported.append(sym);
            }
        }

        // ---- 样式名（照抄 parseFlags:161-174：$ 之后，剥掉紧邻的效果字符） ----
        String theme = codePart.substring(dollarIdx + 1);
        int strip = 0;
        while (strip < theme.length() && EFFECT_SYMBOLS.indexOf(theme.charAt(strip)) >= 0) {
            strip++;
        }
        theme = theme.substring(strip);
        if (theme.isEmpty()) {
            return NOT_OURS;
        }

        String body = raw.substring(dashIdx + 1);
        if (body.isEmpty()) {
            // 见类注释 §5.4：上游会把整条原文当正文（= 把码画出来），我们判「不是我们的码」。
            return NOT_OURS;
        }
        String prefix = raw.substring(0, ampIdx);
        String cleanText = prefix + body;

        if (prefix.indexOf('\u00A7') >= 0 || body.indexOf('\u00A7') >= 0) {
            // 见类注释 §5.3：§ 与 &$ 混在同一行时不做，交回原版（避免我无法验证的下标换算）。
            warnUnsupported("mixed_section_sign", theme);
            return NOT_OURS;
        }

        ShanhaiTextPalette.Style style = ShanhaiTextPalette.get(theme);
        if (style == null) {
            // ---- 退化 A：没移植的样式名 ⇒ 剥码 + 不染色（用户裁决 ③） ----
            warnUnsupported("style", theme);
            return new Parsed(true, cleanText, prefix, body, null, false,
                    false, false, theme, unsupported.toString());
        }

        if (unsupported.length() > 0) {
            // ---- 退化 B：没实现的效果字符 ⇒ 丢掉那个效果，保留颜色（用户裁决 ③ 的第二条） ----
            warnUnsupported("effect", unsupported.toString());
        }

        return new Parsed(true, cleanText, prefix, body, style.rgb(), style.isBody(),
                glitch, floatX, theme, unsupported.toString());
    }

    // ================================================================== 限流 WARN

    /** 两条 WARN 之间至少隔多少毫秒 —— 照本工程既有做法（{@code HIDE_LOG_MIN_GAP_TICKS} 的精神）。 */
    private static final long WARN_MIN_GAP_MS = 5000L;

    private static final AtomicLong LAST_WARN_MS = new AtomicLong(Long.MIN_VALUE / 2);
    private static final AtomicLong WARN_SUPPRESSED = new AtomicLong();

    /**
     * 限流告警：每 {@value #WARN_MIN_GAP_MS} ms 最多一行，并带上被折叠掉的行数。
     *
     * <p>🔴 <b>只限流不报数 = 静默</b>（本工程的血账）。所以被折叠的那些会累加到 {@code suppressed=} 字段里，
     * 下一次真正打出来的那一行会把它清掉，读日志的人就知道「这中间还发生过 N 次」。
     */
    private static void warnUnsupported(String kind, String name) {
        long now = System.currentTimeMillis();
        long last = LAST_WARN_MS.get();
        if (now - last < WARN_MIN_GAP_MS) {
            WARN_SUPPRESSED.incrementAndGet();
            return;
        }
        if (!LAST_WARN_MS.compareAndSet(last, now)) {
            WARN_SUPPRESSED.incrementAndGet();
            return;
        }
        long suppressed = WARN_SUPPRESSED.getAndSet(0L);
        ShanhaiMod.LOGGER.warn(
                "[SHANHAI-SPEC] font_style_unsupported kind={} name={} suppressed={}",
                kind, name, suppressed);
    }

    // ================================================================== 效果数学（照抄上游）

    /**
     * {@code *} floatX 的水平位移 —— 照抄 {@code TextFormatParser.calcWaveX:239-241}。
     *
     * <pre>cos(charIndex * 0.7 + time * 0.0015) * 2.0</pre>
     */
    public static float waveX(int charIndex, long time) {
        return (float) (Math.cos(charIndex * 0.7 + time * 0.0015) * 2.0);
    }

    /** {@code ?} glitch 的水平位移 —— 照抄 {@code calcGlitchX:255-259}。 */
    public static float glitchX(int charIndex, long time) {
        long phase = (time / 55L) + charIndex * 17L;
        if ((phase % 7L) > 1L) {
            return 0.0f;
        }
        return ((phase % 3L) - 1L) * 1.5f;
    }

    /** {@code ?} glitch 的垂直位移 —— 照抄 {@code calcGlitchY:261-265}。 */
    public static float glitchY(int charIndex, long time) {
        long phase = (time / 65L) + charIndex * 23L;
        if ((phase % 9L) > 1L) {
            return 0.0f;
        }
        return ((phase % 3L) - 1L) * 1.0f;
    }

    /**
     * {@code ?} glitch 的偶发掺色 —— 照抄 {@code applyGlitchColor:294-299}。
     *
     * <pre>
     * phase = (time/60) + charIndex*31
     * phase % 11 == 0  ⇒ 向 0x55FFFF 混 65%   （偏青）
     * phase % 13 == 0  ⇒ 向 0xFF5555 混 55%   （偏红）
     * </pre>
     */
    public static int applyGlitchColor(int color, int charIndex, long time) {
        long phase = (time / 60L) + charIndex * 31L;
        if ((phase % 11L) == 0L) {
            return mixColor(color, GLITCH_CYAN, 0.65f);
        }
        if ((phase % 13L) == 0L) {
            return mixColor(color, GLITCH_RED, 0.55f);
        }
        return color;
    }

    /** 线性混色 —— 照抄 {@code TextFormatParser.mixColor:301-314}（含 {@code amount} 的两个夹取）。 */
    public static int mixColor(int base, int target, float amount) {
        if (amount <= 0.0f) {
            return base;
        }
        if (amount > 1.0f) {
            amount = 1.0f;
        }
        int br = (base >> 16) & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = base & 0xFF;
        int tr = (target >> 16) & 0xFF;
        int tg = (target >> 8) & 0xFF;
        int tb = target & 0xFF;
        int r = (int) (br + (tr - br) * amount);
        int g = (int) (bg + (tg - bg) * amount);
        int b = (int) (bb + (tb - bb) * amount);
        return (r << 16) | (g << 8) | b;
    }
}
