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
 *   <tr><td>{@code #}</td><td>outline <b>→ 粗体</b></td><td>描边 <b>→ 加粗</b></td><td><b>上游自己就是死旗标</b>（只写不读）</td><td><b>✅ 做了</b>（2026-09-30 改造成「粗体开关」，见 §2.2）</td></tr>
 * </table>
 * <b>为什么只做 3 个</b>：用户裁决「只移植目前我们要用到的」。实测我们的三类文本里
 * <b>只出现了 {@code ?} 与 {@code *}</b>（横幅），物品名<b>一个效果字符都没有</b>
 * （取证：{@code _tools\extract_style_inventory.ps1} 的 {@code FLAGS (distinct=0)}）；
 * {@code #} 是 2026-09-30 用户点单「两块世线板要同时有流动色和粗体」时新启用的（见 §2.2）。<br>
 * <b>没做的怎么退化</b>：见 §3 —— <b>剥离该字符、保留色板颜色</b>，限流打一行 WARN。
 *
 * <h3>2.2 🔴 加粗开关 {@code #}（2026-09-30 新增）</h3>
 * <pre>
 *   写法：  &amp;$#&lt;样式名&gt;-&lt;正文&gt;        例：{@code &$#golden-世线终焉板}
 *   位置：  跟 {@code ?} / {@code *} 一样，写在 {@code $} 之后、样式名之前
 *           （写在 {@code $} 之前也认，因为检测扫的是整个 codePart）
 *   作用域：只让【正文】加粗（{@code -} 之后那一段），与色板染色的作用域完全一致；prefix 不加粗
 * </pre>
 * <b>为什么选 {@code #}</b>（四个候选的取舍，理由逐条）：
 * <ol>
 *   <li><b>{@code #} 是上游自己都没实现的死旗标</b>（{@code isEffectSymbol} 收它，但
 *       {@code parseFlags} 只写进一个从不被读的 {@code outline} 字段）。⇒ 把它改成「粗体」
 *       <b>不可能与上游任何既有文本的观感冲突</b> —— 上游那套渲染里它本来就是个 no-op。
 *       对照组 {@code !}（bounce）：上游是<b>真的有实现</b>的，复用它会在语义上撞车。</li>
 *   <li><b>零新语法概念</b>：{@code #} 本来就在 {@link #EFFECT_SYMBOLS} 里，解析器本来就在
 *       「样式名开头的效果字符」那圈里扫描并剥离它 ⇒ 只需把「扫到了 ⇒ 丢掉」改成「扫到了 ⇒ 置位」，
 *       语法结构一个字不用动（不像 {@code &$bold_xxx-} 那样要新造一个前缀约定）。</li>
 *   <li><b>不占新字符位</b>：11 个效果字符里还有 8 个空着，但用户明确要求「现有的 96 条名字逐字节不变」
 *       ⇒ 新增一个语法符号反而要求所有既有文本重新审一遍有没有撞上。{@code #} 已经过了这一关：
 *       取证 = 全文 {@code zh_cn.json} 里 {@code #} 出现 <b>0 次</b>，6 行横幅的 5 个
 *       {@code PREFIX_*} 常量里也没有。</li>
 *   <li><b>能一眼看出是「修饰」不是「颜色」</b>：它跟在 {@code $} 后、样式名前，与
 *       {@code &$?crimson-} 同一个位置 ⇒ 已有的「效果字符必须写在样式名之前」这条经验直接沿用。</li>
 * </ol>
 * <b>加粗在 MC 里是怎么生效的</b>（全部 {@code javap -c} 实证，见 {@code handoff\outbound\物品名美化-收尾.md}）：
 * <pre>
 *   Font$StringRenderOutput.accept : 60 invokevirtual Style.isBold()Z          ⇒ istore 7
 *                                  : 284 iload 7 → 286 GlyphInfo.getAdvance(Z)F
 *   GlyphInfo.getAdvance(Z)        : getAdvance() + (Z ? getBoldOffset() : 0)，getBoldOffset() 默认返回 1.0f
 *   Font 里 StringSplitter 的 WidthProvider（lambda$new$0）: 17 Style.isBold()Z → 20 GlyphInfo.getAdvance(Z)F
 * </pre>
 * ⇒ <b>「样式带 bold」= 每个字形多推进 1.0 px</b>，而且 <b>宽度测量与绘制走的是同一个 {@code getAdvance(Z)}</b>
 * ⇒ 不需要我们自己造第二条加粗路径，把 {@code Style.withBold(true)} 挂上去就行。
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
 *   <tr>
 *     <td><b>{@code §} 与 {@code &$} 混在同一行</b>（§5.3）</td>
 *     <td>整条判<b>「不是我们的码」</b>（{@code ours=false}），原样交回原版渲染 —— <b>码会显示在名字里</b>，
 *         但这是刻意的：交回原版至少能正确显示 {@code §} 语义，自己硬吃反而更难预测</td>
 *   </tr>
 * </table>
 * ⚠️ <b>加粗与颜色共进退</b>：{@code #} 是 codePart 的一部分，样式名不认识时整段码被剥掉
 * ⇒ <b>退化 A 的那条路【不带加粗】</b>（白字、不加粗）。这是刻意的：退化 A 的语义是
 * 「这段码我们不认识 ⇒ 整段当垃圾扔掉」，让其中一半（加粗）活下来会造出一个
 * 「既不是我们的渲染、也不是原版渲染」的第三态。用户裁决原话是「样式名拼错 ⇒ 白字」，
 * 本条**逐字节保持了 {@code #} 出现之前的同一条路径**（{@code drawDegraded} 一个字没改）。
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
 *   <li><b>{@code #} 写在样式名【后面】不算加粗</b>（{@code &$golden#-名}）。
 *       与 {@code ?} / {@code *} 同一条规矩：剥离只发生在样式名开头 ⇒ {@code theme} 变成
 *       {@code "golden#"}，查表查不到 ⇒ <b>退化 A（白字、不加粗）</b>。
 *       <b>这是刻意的、和既有行为一致</b>，不是新坑（{@code &$ultimate?-帝山海} 早在 2026-09-30
 *       上一轮就被实测为「退化 A」，见 {@code handoff\outbound\物品名美化-落地.md} §3.4）。</li>
 *   <li><b>加粗只作用于正文</b>，{@code prefix}（{@code &} 之前那一段）照旧用调用方给的颜色、不加粗。
 *       我们的 96 条物品名与 6 行横幅<b>没有一条带 prefix</b>，所以这条边界目前不生效。</li>
 * </ol>
 */
public final class ShanhaiTextParser {

    private ShanhaiTextParser() {}

    /** 11 个效果字符 —— 照抄上游 {@code isEffectSymbol:196-204}（顺序也照抄）。 */
    private static final String EFFECT_SYMBOLS = "@~#*%!^?+>`";

    /**
     * <b>加粗开关字符</b> —— 2026-09-30 新启用（见类注释 §2.2）。
     *
     * <p>它<b>本来就是</b> {@link #EFFECT_SYMBOLS} 的成员（上游的 {@code outline} 死旗标），
     * 所以样式名开头的剥离循环<b>一个字都不用改</b>就能把 {@code &$#golden-} 里的 {@code #} 剥掉。
     */
    public static final char BOLD_SYMBOL = '#';

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
     * @param bold       是否带 {@code #}（<b>加粗</b>，2026-09-30 新增）。⚠️ 只在
     *                   {@code palette != null} 时可能为 true（退化 A 不带加粗，见类注释 §3）
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
            boolean bold,
            String styleName,
            String unsupported) {

        /**
         * 有没有「会改变像素位置」的效果 —— 有就得走逐字绘制，没有可以一次画完整行。
         *
         * <p>🔴 <b>{@code bold} 不算 motion</b>（刻意的）：加粗改的是字形的<b>推进量</b>与描摹宽度，
         * 不改「这个字画在哪个坐标」，所以它<b>不需要</b>逐字路径
         * （逐字路径每字一次 {@code drawInBatch}，比单次绘制贵一个 {@code Font$StringRenderOutput}）。
         * 证据：{@code StringRenderOutput.accept} 里 {@code isBold} 只被喂给
         * {@code GlyphInfo.getAdvance(Z)}，绘制坐标 {@code x}/{@code y} 一个字节都没被它改。
         */
        public boolean hasMotion() {
            return glitch || floatX;
        }
    }

    /** 「不是我们的码」的单一实例（缓存里的否定结果也用它，省对象）。 */
    private static final Parsed NOT_OURS = new Parsed(
            false, "", "", "", null, false, false, false, false, "", "");

    // ================================================================== 前缀码剥离

    /**
     * <b>剥掉一段文本里的 {@code &$<样式名>-} 前缀码，只留正文</b>。
     *
     * <h2>🔴 为什么需要它（2026-09-30 用户实机报的 bug）</h2>
     * 用户截图：「量子化现实重构」配方页底部那行显示成
     * <pre>
     *   模块要求：1× &$electric-创始现实修改模块（等级 ≥ 17）      ← &$electric- 原样露出来了
     * </pre>
     * 根因（用<b>真的本类</b>离线跑出来的，不是推断）：那一行是
     * {@code §b模块要求：} ＋ {@code 1× } ＋ 物品显示名 ＋ {@code §7（等级 ≥ 17）} 拼起来的，
     * ⇒ <b>同一行里同时出现 U+00A7 与 {@code &$}</b> ⇒ 命中 {@code parse} 里那条
     * 「{@code §} 与 {@code &$} 混用 ⇒ 交回原版」的规则（见类注释 §5.3）
     * ⇒ {@code ours=false} ⇒ 原版渲染把 {@code &$electric-} 当普通字符画出来。
     * <p>⇒ <b>调用方（拼显示文本的那几处）只要把"名字"这一段先剥码</b>，那一行就不再有 {@code &$}，
     * 于是"交回原版"变成一个**正确**的结局：{@code §b}/{@code §7} 照常生效、也不再露码。
     *
     * <h2>🔴🔴 2026-10-01 更正（理由写错了，修法是对的；本方法的**必要性**反而更强了）</h2>
     * 上面那条"根因 = 命中 {@code parse} 的 §/&amp;$ 混用规则"<b>是错的</b>：
     * 那一行文本由 {@code GTRecipeWidget} 用 {@code condition.getTooltips().getString()} 塞进
     * LDLib 的 {@code LabelWidget(int,int,String)}，而它走
     * {@code Font.drawInBatch(String,…)} —— <b>一条完全不经过本类 {@code parse} 的渲染路径</b>
     * （四段 {@code javap} 原文见 {@code com.shanhai.machine.module.ModuleLevelCondition} 类注释 §7）。
     * ⇒ 那次（以及 2026-10-01 那次 {@code &$ultimateRainbow-} 露码）的<b>同一条真根因</b>是：
     * <b>这条路径上任何 {@code &$…-} 都会被原样画出来，与有没有 {@code §} 无关。</b>
     * <p>⇒ 修法（先剥码再做字面量）<b>依然正确</b>，因为"零 {@code &}"就是那条路径的唯一安全形态；
     * 只是别再指望 {@code parse} 帮你在那条路径上把关 —— <b>它不会被调用</b>。
     *
     * <h2>🔴 它<b>不</b>改 {@code parse} 的规则</h2>
     * 那条"混用交回原版"的全局规则<b>一个字都没动</b>（它保护的是本类无法在不验证的情况下
     * 做的下标换算）。本方法是一个**独立的、给调用方用的**工具函数。
     *
     * <h2>行为</h2>
     * <ul>
     *   <li>以 {@code &$} 开头且后面有 {@code -} ⇒ 返回 {@code -} 之后的部分（含效果字符一起丢）；</li>
     *   <li>其它一切情况（没有 {@code &$} / 没有 {@code -} / {@code -} 后为空 / {@code null}）
     *       ⇒ <b>原样返回</b>，绝不返回 {@code null}、绝不返回空串（除非本来就是空串）。</li>
     *   <li>{@code §} 一个都不动（那是原版的合法色码，剥了反而丢颜色）。</li>
     * </ul>
     */
    public static String stripStyleCode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw == null ? "" : raw;
        }
        if (!raw.startsWith("&$")) {
            return raw;
        }
        final int dash = raw.indexOf('-');
        if (dash < 0 || dash + 1 >= raw.length()) {
            return raw;   // 形状不是 `&$…-正文` ⇒ 不动它（交给原版，与 parse 的判据一致）
        }
        return raw.substring(dash + 1);
    }

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
        // 加粗：与上面两个同一套判据（扫整个 codePart ⇒ `$` 前后都认），见类注释 §2.2。
        boolean bold = codePart.indexOf(BOLD_SYMBOL) >= 0;
        StringBuilder unsupported = new StringBuilder();
        for (int i = 0; i < EFFECT_SYMBOLS.length(); i++) {
            char sym = EFFECT_SYMBOLS.charAt(i);
            if (sym == '?' || sym == '*' || sym == BOLD_SYMBOL) {
                continue; // 这三个我们做了
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
            // 🔴 这里 bold 恒传 false：样式名不认识 ⇒ 整段码（含 `#`）一起被剥掉，见类注释 §3 的加粗说明。
            warnUnsupported("style", theme);
            return new Parsed(true, cleanText, prefix, body, null, false,
                    false, false, false, theme, unsupported.toString());
        }

        if (unsupported.length() > 0) {
            // ---- 退化 B：没实现的效果字符 ⇒ 丢掉那个效果，保留颜色（用户裁决 ③ 的第二条） ----
            warnUnsupported("effect", unsupported.toString());
        }

        return new Parsed(true, cleanText, prefix, body, style.rgb(), style.isBody(),
                glitch, floatX, bold, theme, unsupported.toString());
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

    // ================================================================== 加粗的宽度补偿

    /**
     * 加粗让一段文字<b>多推进</b>多少像素 —— 供
     * {@code ShanhaiFontStyleRenderer#widthOf} 修正宽度用（纯函数，只依赖 {@code java.lang.String}）。
     *
     * <p>🔴 <b>为什么必须补</b>：{@code widthOf} 量的是 {@code self.width(parsed.cleanText())}，
     * 而 {@code Font.width(String)} 内部走的是 {@code Style.EMPTY}（证据：{@code javap -c}
     * {@code StringSplitter.stringWidth(String)} 转调 {@code stringWidth(String, Style.EMPTY)}）
     * ⇒ <b>它量出来的宽度【不含加粗】</b>，而真正画出去的是含 {@code bold} 的 FCS。
     * 不补的话这两块板在居中显示（tooltip / JEI）时每字会偏 1 px。
     *
     * <p><b>数值从哪来</b>（{@code javap -c} 原文，不是推断）：
     * <pre>
     *   com.mojang.blaze3d.font.GlyphInfo.getAdvance(boolean):
     *      0: aload_0
     *      1: invokeinterface getAdvance:()F
     *      6: iload_1
     *      7: ifeq  19
     *     10: invokeinterface getBoldOffset:()F
     *     19: fconst_0
     *     20: fadd
     *     21: freturn
     *   com.mojang.blaze3d.font.GlyphInfo.getBoldOffset():
     *      0: fconst_1
     *      1: freturn                                        → 默认就是 1.0f
     * </pre>
     * ⇒ 每个字形恰好多 <b>1.0 px</b>。
     *
     * <p><b>为什么是「码点数」而不是「字符数/字节数」</b>：加粗是按<b>字形</b>（glyph）算的，
     * 而 {@code Font} 是按<b>码点</b>取字形的 ⇒ 代理对（emoji 等）只算 1 个字形的钱。
     *
     * <p>⚠️ <b>已知边界（如实列出）</b>：
     * <ol>
     *   <li>假设被量的这段文字<b>没有换行符</b>。有 {@code \n} 时 {@code Font.width(String)}
     *       仍是整串累加，而本方法也整串数码点 ⇒ 两者其实仍同口径；但一旦原版改成「按行取最大」，
     *       这条就不成立。我们的 96 条物品名与 6 行横幅<b>一个换行都没有</b>（离线自证里有这条断言）。</li>
     *   <li>假设没有资源包覆写 {@code GlyphInfo.getBoldOffset()}。原版 {@code TrueTypeGlyphProvider}
     *       及其内部类<b>没有</b>覆写它（取证：{@code javap -p} 的该 class 方法表里无 {@code getBoldOffset}）。</li>
     * </ol>
     */
    public static int boldAdvance(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return text.codePointCount(0, text.length());
    }
}
