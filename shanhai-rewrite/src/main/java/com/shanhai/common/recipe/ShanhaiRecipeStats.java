package com.shanhai.common.recipe;

import com.shanhai.ShanhaiMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 山海重构 · <b>「私货配方统计」横幅</b>（原版同名功能的自研重制版）。
 *
 * <h2>0. 这个类是干什么的</h2>
 * 复刻老版私货那条「配方统计横幅」，但<b>全部由本工程自研</b>，<b>不复用老版任何类</b>：
 * <ul>
 *   <li><b>配方加载完成</b> → 往日志打<b>一行机器可判的统计</b>（见 §2 的固定格式）；</li>
 *   <li><b>玩家登录后 160 tick</b> → 给该玩家发多行聊天横幅（见 §3）。</li>
 * </ul>
 *
 * <h2>1. 为什么是「Java 侧累加器」而不是「KJS 直接报数」</h2>
 * 老版的计数链是：<b>KJS 逐条 {@code recordRecipe(type, ok, id, msg)} 上报</b>
 * → Java {@code AtomicLong} 累加（{@code RECIPE_TOTAL/SUCCESS/DISABLED/FAILED}）→ 横幅取 {@code .get()}。
 * 本类照这条链重做，理由不是「像老版」，而是<b>它本来就更对</b>：
 * <ol>
 *   <li><b>登录横幅晚于配方加载</b>：横幅在玩家登录后 160 tick 才发，那时 KJS 的 recipes 回调早就结束了
 *       ⇒ 数字必须有<b>一个活在 Java 侧的载体</b>，否则横幅无从取值。</li>
 *   <li><b>单一数据源</b>：数只在一处（{@link #addResult(boolean)}）累加、只在一处（{@link #reportSummary(String)}）
 *       读出。工程里已有的教训是「两处都写会互相掩盖故障」，所以本类<b>不接受</b>「KJS 直接传总数」
 *       那种第二条写入通道。</li>
 * </ol>
 *
 * <h2>2. 🔴 数字口径声明（机器判定行的字段本身就是口径）</h2>
 * 配方加载完成打的那一行，格式<b>固定</b>：
 * <pre>
 * [SHANHAI-SPEC] recipe_stats scope=&lt;scope&gt; total=&lt;n&gt; success=&lt;n&gt; failed=&lt;n&gt;
 * </pre>
 * 口径三条，逐条写死：
 * <ol>
 *   <li><b>谁数的</b>：<b>本类的 {@link AtomicLong} 累加器</b>（Java 侧）。数据来源是 <b>KJS 逐条上报</b>
 *       —— 即 {@code shanhai_test_recipes.js} 在自己的循环里对每条配方调一次 {@link #addResult(boolean)}。
 *       <b>不是</b> KubeJS 的 {@code Added N recipes}。</li>
 *   <li><b>数的范围</b>：<b>只有调了 {@link #addResult(boolean)} 的那些配方</b>。当前唯一调用方是
 *       {@code kubejs/server_scripts/shanhai_test_recipes.js}（⚠️ 2026-09-27 更正：原文写「16 条占位测试配方」，
 *       实测是 <b>39 条</b> —— 见运行日志 {@code scope=kjs_shanhai_test_recipes total=39}）；
 *       另有 {@code shanhai_pf_recipes.js} 于 2026-09-27 接入，用<b>独立 scope {@code shanhai_pf}</b>
 *       上报（它先于测试脚本执行，并在自己那批之前调 {@link #reset()}，因此两批数字互不污染）；
 *       宿主脚本（{@code gtceu.js} 等 15 个）<b>不上报</b>，因此<b>不在</b>这个数字里。
 *       ⇒ 这个数字<b>不等于</b>整合包的配方总数，读的时候必须带着 {@code scope=} 一起读。</li>
 *   <li><b>成功/失败判据</b>：{@code success} = 「本脚本调用 GTCEu recipe builder 没有抛异常」的条数，
 *       {@code failed} = 抛了异常的条数。<b>它不等于</b>「RecipeManager 最终接受了的条数」——
 *       后者要看 KubeJS 自己后面打的那行 {@code Added N recipes ..., with M failed recipes}（那行覆盖全部脚本）。
 *       ⚠️ 本行日志出现得比 {@code Added N recipes} 那行<b>早</b>（KJS 先收集、后应用），这是老版也有的时序。</li>
 * </ol>
 * <p>
 * ⚠️ <b>没有 {@code skipped} 字段</b>：老版的 {@code disabled}/{@code errors} 对应它自己的
 * 「配方加载已禁用」开关（见 §5 留档常量 {@code 配方加载已禁用，已跳过}）。<b>重制版目前没有任何
 * 「禁用/跳过配方」的概念</b> —— 工程里搜不到对应的注册类、配置项或 KJS 入口。把它写成一个恒为 0
 * 的字段就是<b>印一个假数字</b>（本工程红线：宁可缺，不可假），所以本类<b>只数 total/success/failed 三个</b>，
 * 等真有「跳过」通道时再加第四个字段（那时字段集合会变，解析方要同步改 —— 这是有意的、可见的变更）。
 *
 * <h2>3. 横幅为什么用 {@code TickEvent} 自己数 tick，而不是 {@code server.scheduleInTicks(160, …)}</h2>
 * <b>本工程没有 {@code scheduleInTicks} 可用</b>，这是查证过的，不是推测：
 * <ul>
 *   <li>老版那句 {@code event.server.scheduleInTicks(160, …)} 里的 {@code event} 是 <b>KubeJS 的事件</b>，
 *       {@code scheduleInTicks} 来自 <b>KubeJS</b> 的 {@code dev.latvian.mods.kubejs.core.MinecraftServerKJS}
 *       （取证：{@code jar tf kubejs-forge-2001.6.5-build.26.jar | grep MinecraftServerKJS} 命中）。
 *       ⇒ 它是 KJS 侧接口 mixin，<b>不是 Forge/原版 API</b>。</li>
 *   <li>原版 {@code MinecraftServer} 上<b>没有</b> {@code scheduleInTicks}
 *       （取证：{@code javap -p net.minecraft.server.MinecraftServer} 的方法表里无此方法）。</li>
 *   <li>用 {@code server.tell(new TickTask(server.getTickCount() + 160, task))} 造延迟<b>也是错的</b>：
 *       {@code MinecraftServer.shouldRun(TickTask)} 的字节码是
 *       {@code return task.getTick() + 3 < this.tickCount || this.haveTime();}
 *       ⇒ 未来 160 tick 的任务因为 {@code haveTime()} 为真而<b>立刻</b>被 poll 执行，{@code tick} 字段
 *       不是延迟机制。同理 {@code server.execute(...)} 会被 {@code wrapRunnable} 包成
 *       {@code tickCount} 当刻的任务，也不延迟。</li>
 * </ul>
 * ⇒ 结论：在 FORGE 总线上订阅 {@link TickEvent.ServerTickEvent}（{@code Phase.END}）**自己数 160 tick**。
 * 这是<b>行为可验证</b>的写法：延迟长度就是数出来的 tick 数，没有依赖任何需要猜语义的内部机制。
 * 空表时第一行就 return，稳态开销为 0。
 *
 * <h2>4. 🔴 老版横幅原文（留档，不是本类要输出的内容）</h2>
 * <b>老版由 {@code com.dishanhai.gt_shanhai.api.DShanhaiRecipeEngine} 打出</b>（Java 侧）。
 * 下面是<b>用户 2026-09 截图里的逐字原文</b>（本类的<b>版式依据</b>）：
 * <pre>
 * ======= 山海 私货配方统计 =======
 * [OK]配方库 加载完成!
 * ✅ 成功加载: 560 个配方
 * ⚠️ 已被禁用配方数量: 7
 * 😊 配方库检测无报错 祝领航员航行无阻!
 * 🔷 当前神人私货版本:v2.7.4fix(日志系统版本2.7.3)
 * 🔷 当前gt_shanhai模组版本:v1.0.0
 * 🔷 当前API总控系统版本为2.9.1
 * 欢迎来到GTL寰宇联合重工巨企
 * 此成功信息回执由JAVA侧: DShanhaiRecipeEngine 生成
 * 老大我们这样熬夜写私货心脏真的不会自己先休息吗
 * ==============================
 * </pre>
 * <p>
 * 被用户点名「已经没有了的功能描述可以不写」⇒ <b>本类刻意不输出的 3 条</b>：
 * <ol>
 *   <li>{@code 🔷 当前API总控系统版本为2.9.1}：重制版<b>没有任何 API 版本类/常量</b>；老版里它也只是
 *       KJS 侧的一个字面量，不驱动任何逻辑。</li>
 *   <li>{@code (日志系统版本2.7.3)} 那个括号：全工程搜「日志系统」无任何注册/类/配置，只是版本串里的文字。</li>
 *   <li>{@code 此成功信息回执由JAVA侧: DShanhaiRecipeEngine 生成} <b>的类名</b>：重制版是自研，
 *       不该署老版的类名 ⇒ 改成 {@link #RECEIPT_CLASS_NAME}（= 本类名）。这一行本身<b>保留</b>，只换署名。</li>
 * </ol>
 *
 * <h4>4.1 ⚠️ 顺带纠一处「计划外的偏差」，是查出来的，不是我想改</h4>
 * <p>
 * 🔴 <b>【本节原文保留，但它已被证伪/作废，不要再照着做】</b>（本工程规矩：作废项保留原文，不删）：
 * </p>
 * <blockquote>
 * 本类<b>不使用</b>老版横幅的样式前缀 {@code &$?body_golden-} / {@code &$*body_moss-} /
 * {@code &$body_aurora-} / {@code &$body_silver-}：那套前缀由 <b>{@code DShanhaiTextUtil}</b> 解析，
 * 重制版<b>没有这个类</b>，照抄只会把 {@code &$body_moss-} 原样显示出来。
 * </blockquote>
 * <p>
 * 🔴 <b>【作废原因 · 2026 本轮】</b>上面那句「照抄只会原样显示」<b>只在"没有客户端 mixin"这个前提下成立</b>，
 * 而那个前提<b>已经不存在了</b>：本轮新增了客户端 {@code Font} 混入
 * （{@code com.shanhai.mixin.ShanhaiFontStyleMixin} + {@code com.shanhai.client.text.ShanhaiFontStyleRenderer}，
 * 走 {@code shanhai.mixin.json} 的 {@code client} 数组），它接管了
 * {@code Font.drawInBatch(FormattedCharSequence,…)} 这个<b>所有文字渲染的唯一收口</b>。
 * ⇒ 现在文案是「服务端发裸字面量、客户端逐帧算色」，<b>与原版的机制完全一致</b>。
 * ⇒ 用户裁决：「横幅 6 行加回 {@code &$…-} 前缀」。
 * </p>
 * <p>
 * ⚠️ <b>仍未变的边界</b>：客户端<b>没有</b>本 mod 的时候（或混入没生效的时候），
 * 这 6 行会显示成带 {@code &$…-} 的裸码。本 mod 双端同 jar，正常玩法下客户端必然带着它
 * （方案 md §7 风险 4 已经记过这一条）。
 * </p>
 * <p>
 * <b>留档时发现一处与任务书转录不一致，如实记下</b>（取证命令见 §6）：把
 * {@code originals/analysis/gtsh_refs/x/com/dishanhai/gt_shanhai/api/DShanhaiRecipeEngine.class} 的字符串常量
 * 用 {@code javap -J-Dfile.encoding=UTF-8 -c -p} 取出来，实际是：
 * <pre>
 * &amp;$?body_golden-============= 山海私货配方统计 =============     &lt;- 两侧各 13 个 '='
 * &amp;$*body_moss-[OK] 配方库加载完成!
 * §a😋 配方库检测无报错 祝领航员航行无阻!                          &lt;- 是 😋 不是 😊
 * &amp;$body_aurora-欢迎来到GTL寰宇联合重工巨企
 * &amp;$body_moss-此成功信息回执由JAVA侧: DShanhaiRecipeEngine 生成
 * &amp;$body_silver-老大我们这样熬夜写私货心脏真的不会自己先休息吗
 * &amp;$?body_golden-===========================================     &lt;- 43 个 '='
 * </pre>
 * 两个「来源」对不上：用户截图里是 {@code ==========} 与 {@code 😊}，而 {@code originals/analysis/gtsh_refs}
 * 里那份是 13/43 个 {@code =} 与 {@code 😋}；且那份里<b>根本没有</b> {@code ✅ 成功加载} /
 * {@code ⚠️ 已被禁用配方数量} / {@code 🔷 当前…版本} 这几行（它们来自另一处/另一版本）。
 * <b>本类按任务书给出的用户截图原文输出</b>（分隔线 {@link #BAR_TOP} / {@link #BAR_BOTTOM} 提成常量，
 * 要改一个 token 就行），并把差异记在这里 —— 因为横幅只能人眼看，机器验不了，宁可留一处可查的分歧。
 * </p>
 * <p>
 * ⚠️ 本轮<b>只加前缀、没改正文</b>：{@link #BAR_TOP}/{@link #BAR_BOTTOM} 的 {@code '='} 个数与
 * 「山海 私货配方统计」中间那个空格<b>保持用户截图版</b>（7/30 个 {@code '='}），
 * <b>没有</b>换成上面那份 13/43 个。理由：用户本轮要的是「加回前缀」，换分隔线长度是另一件事，
 * 且这两个值在本类里本来就有单独的留档分歧。要切过去只需改这两个常量。
 * </p>
 *
 * <h2>5. 触发时机（照原版）</h2>
 * <ul>
 *   <li><b>日志行</b>：由 KJS 在 {@code ServerEvents.recipes} 回调<b>末尾</b>调
 *       {@link #reportSummary(String)} 触发（老版同款：配方加载完成那一刻只往日志打一行）。</li>
 *   <li><b>横幅</b>：{@link PlayerEvent.PlayerLoggedInEvent} → 延迟 {@link #BANNER_DELAY_TICKS} tick
 *       （= 160，<b>照原版</b>）→ {@link #sendBannerTo(ServerPlayer)}。
 *       ⇒ 🔴 <b>无头专服永远看不到横幅</b>（没有玩家登录），横幅只能由用户进游戏验。</li>
 * </ul>
 * 关于「用户说的是『加载配方的时候』」：原版给玩家看的那条<b>确实是登录后 160 tick</b>，
 * 而「配方加载完成」那一刻原版只打日志 ⇒ <b>两处都做</b>：加载完成打日志、玩家登录打横幅。
 *
 * <h2>6. 本类依赖的 API 逐条取证（都不是推测）</h2>
 * <pre>
 * javap -cp &lt;forge-1.20.1-47.4.16_mapped_parchment&gt; -p net.minecraft.server.MinecraftServer
 *   → 有 getTickCount()/tell()，**没有** scheduleInTicks
 * javap ... -c -p net.minecraft.server.MinecraftServer   （shouldRun）
 *   → getTick()+3 &lt; tickCount || haveTime()
 * javap ... -p 'net.minecraftforge.event.entity.player.PlayerEvent$PlayerLoggedInEvent'
 *   → 只有 getEntity()（**没有** getServer()）
 * javap ... -p 'net.minecraftforge.event.TickEvent$ServerTickEvent'
 *   → getServer()、父类 public final Phase phase、Phase.END 存在
 * javap ... -p net.minecraft.server.level.ServerPlayer        → sendSystemMessage(Component)
 * javap ... -p net.minecraft.server.players.PlayerList        → getPlayer(UUID)
 * javap ... -p net.minecraft.world.entity.Entity              → getServer()
 * javap -v -p 'net.minecraftforge.fml.common.Mod$EventBusSubscriber'（javafmllanguage jar）
 *   → value() 的 AnnotationDefault = {Dist.CLIENT, Dist.DEDICATED_SERVER}  ⇒ 不写 value 即两侧都注册
 * javap -cp &lt;fmlcore jar&gt; -p net.minecraftforge.fml.ModList   → get()/getModContainerById(String)
 * javap -cp &lt;forgespi jar&gt; -p net.minecraftforge.forgespi.language.IModInfo
 *   → getVersion() 返回 org.apache.maven.artifact.versioning.ArtifactVersion
 * </pre>
 */
@Mod.EventBusSubscriber(modid = ShanhaiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShanhaiRecipeStats {

    /** 本类所有日志行的前缀，沿用本工程既有前缀（{@code ModuleSlotDiagnostics.PREFIX} 同款）。 */
    public static final String LOG_PREFIX = "[SHANHAI-SPEC]";

    /** 机器判定行的固定键名。grep 这一个词就能定位到统计行。 */
    public static final String STATS_LOG_KEY = "recipe_stats";

    /**
     * 本 scope 的上报方：{@code kubejs/server_scripts/shanhai_test_recipes.js}。
     * ⚠️ 2026-09-27 更正：原文写「16 条占位测试配方」，<b>实测是 39 条</b>
     * （运行日志 {@code scope=kjs_shanhai_test_recipes total=39 success=39 failed=0}）。
     * <p>另有 {@code shanhai_pf_recipes.js} 于 2026-09-27 接入，用<b>另一个 scope</b>
     * （见 {@code scope=shanhai_pf}），两者数字互不污染。
     *
     * <p>这个字符串会被打进日志行的 {@code scope=} 字段，所以它<b>就是口径的一半</b>——
     * 看到 {@code scope=kjs_shanhai_test_recipes} 就该知道：数字只覆盖那一个脚本报上来的配方。
     */
    public static final String SCOPE_KJS_TEST = "kjs_shanhai_test_recipes";

    /** 横幅里「回执由谁生成」的署名。<b>是本类自己的名字，不是老版的 {@code DShanhaiRecipeEngine}。</b> */
    public static final String RECEIPT_CLASS_NAME = "ShanhaiRecipeStats";

    /** 玩家登录后等多少 tick 再发横幅 —— <b>160，照原版</b>（8 秒）。 */
    public static final int BANNER_DELAY_TICKS = 160;

    /**
     * 横幅首行。老版是 {@code &$?body_golden-============= 山海私货配方统计 =============}（13/13 个 '='）。
     *
     * <p>🔴 本轮 <b>样式前缀 {@code &$?body_golden-} 已加回</b>（见 {@link #PREFIX_GLITCH_GOLDEN} 与类注释 §4.1）。
     * 分隔线数量仍按用户截图（7 个 '='）—— 本轮只加前缀、不改正文。
     */
    public static final String BAR_TOP = "======= 山海 私货配方统计 =======";

    /** 横幅末行（用户截图：30 个 '='；老版常量里那份是 43 个 '='，见类注释 §4.1）。 */
    public static final String BAR_BOTTOM = "==============================";

    // ------------------------------------------------------------------ &$…- 样式前缀

    /**
     * 横幅 6 行的样式前缀 —— <b>照原版原文，一个字都没改</b>。
     *
     * <p>语法 {@code &<效果字符>$<样式名>-<正文>}；由客户端
     * {@code com.shanhai.mixin.ShanhaiFontStyleMixin} 在 {@code Font.drawInBatch} 里解析并逐帧算色
     * ⇒ 服务端只管发<b>裸字面量</b>（这里发的就是它），<b>原版也是这么干的</b>
     * （取证：{@code DShanhaiRecipeEngine.java:408-448} 全是裸 {@code Component.literal}）。
     *
     * <table border="1">
     *   <tr><th>常量</th><th>前缀</th><th>效果</th><th>用在哪一行</th></tr>
     *   <tr><td>{@link #PREFIX_GLITCH_GOLDEN}</td><td>{@code &$?body_golden-}</td>
     *       <td>{@code ?}=glitch（偶发掺青/红 + 亚像素位移）＋ 金色板 200ms/格</td>
     *       <td>首行、末行</td></tr>
     *   <tr><td>{@link #PREFIX_FLOATX_MOSS}</td><td>{@code &$*body_moss-}</td>
     *       <td>{@code *}=floatX（左右 ±2px 波浪）＋ 苔藓色板</td><td>{@code [OK]配方库 加载完成!}</td></tr>
     *   <tr><td>{@link #PREFIX_AURORA}</td><td>{@code &$body_aurora-}</td>
     *       <td>极光色板</td><td>{@code 欢迎来到GTL寰宇联合重工巨企}</td></tr>
     *   <tr><td>{@link #PREFIX_MOSS}</td><td>{@code &$body_moss-}</td>
     *       <td>苔藓色板</td><td>回执行</td></tr>
     *   <tr><td>{@link #PREFIX_SILVER}</td><td>{@code &$body_silver-}</td>
     *       <td>银白色板（白↔灰往返）</td><td>末句</td></tr>
     * </table>
     * <p>
     * 🔴 <b>{@code ?} 与 {@code *} 是本工程实现了的<b>全部</b>效果字符</b>
     * （清单与「没做的那 9 个怎么退化」见 {@code ShanhaiTextParser} 类注释 §2/§3）；
     * 其余 9 行正文（{@code ✅ 成功加载} / {@code 😊 配方库检测无报错} / {@code 🔷 当前版本} 等）
     * <b>刻意不加前缀</b>——理由见 {@link #bannerLines()} 的注释。
     */
    public static final String PREFIX_GLITCH_GOLDEN = "&$?body_golden-";

    /** {@code &$*body_moss-}：苔藓色板 + floatX 左右波浪。 */
    public static final String PREFIX_FLOATX_MOSS = "&$*body_moss-";

    /** {@code &$body_aurora-}：极光色板。 */
    public static final String PREFIX_AURORA = "&$body_aurora-";

    /** {@code &$body_moss-}：苔藓色板。 */
    public static final String PREFIX_MOSS = "&$body_moss-";

    /** {@code &$body_silver-}：银白色板。 */
    public static final String PREFIX_SILVER = "&$body_silver-";

    /** 版本取不到时的显示串。<b>宁可显示「不可用」，也不显示一个编出来的版本号。</b> */
    public static final String VERSION_UNAVAILABLE = "(不可用)";

    // ------------------------------------------------------------------ 累加器

    private static final AtomicLong TOTAL = new AtomicLong();
    private static final AtomicLong SUCCESS = new AtomicLong();
    private static final AtomicLong FAILED = new AtomicLong();

    /**
     * 🔴 2026-09-27 新增：<b>终身累加器，{@link #reset()} 不清它</b>。
     *
     * <p>为什么必须单独有一个：{@code TOTAL/SUCCESS/FAILED} 是「当前这一批」的口径，
     * 谁要报自己那批就先 {@link #reset()}。而 {@code shanhai_deconstruct.js} /
     * {@code shanhai_pf_recipes.js} / {@code shanhai_test_recipes.js} 三个脚本各自 reset，
     * <b>后一个会把前一个清掉</b> ⇒ 用那三个数<b>算不出</b>「山海相关全部配方」的总和。
     *
     * <p>⇒ 本计数器只在 {@link #addResult(boolean)} 里加、<b>永不清零</b>
     * ⇒ 任何位置、任何顺序调 {@link #reportSummary(String)} 都拿到<b>同一份</b>
     * 「自开服以来累计上报了多少条」。这就是「顺序无关」的实现方式：
     * <b>不靠调用时机，靠一个不被清的累加器</b>。
     *
     * <p>⚠️ 口径提醒：它数的仍是「调过 {@link #addResult(boolean)} 的条数」，
     * 仍然<b>不等于</b>整合包配方总数（宿主脚本不上报）。
     */
    private static final AtomicLong LIFETIME = new AtomicLong();

    /**
     * 🔴 2026-09-27 新增：<b>按 scope 分批留档</b> —— scope → {total, success, failed}。
     *
     * <p>为什么需要它：{@code TOTAL/SUCCESS/FAILED} 是<b>全局</b>累加器，谁后报就显示谁 ——
     * 实测用户看到的横幅「✅ 成功加载: 39 个配方」显示的是<b>最后一次 reportSummary 时的 TOTAL</b>
     * （脚本顺序是 解构 → PF → 测试 ⇒ 显示测试的 39），<b>既不是 78 也不是 1441</b>，
     * 读不出「每一批各多少条」。
     *
     * <p>实现取巧之处：<b>每批在 reportSummary 之前都先 {@link #reset()} 过</b>
     * ⇒ 那一刻的 {@code TOTAL} <b>正好就是该批的条数</b> ⇒ 在 {@link #reportSummary(String)} 里
     * 顺手存一份即可，<b>不需要给 addResult 加 scope 参数、也不需要改任何 KJS 调用点</b>。
     */
    private static final Map<String, long[]> BY_SCOPE = new ConcurrentHashMap<>();

    /** scope → 横幅上的中文名。查不到就用 scope 原文（<b>绝不编一个名字</b>）。 */
    private static final Map<String, String> SCOPE_LABELS = Map.of(
            SCOPE_KJS_TEST, "测试配方",
            "shanhai_pf", "shanhai_pf",
            "shanhai_deconstruct", "物质解构");

    // ------------------------------------------------------------------ 待发横幅

    /**
     * 玩家 UUID → 还差多少 tick 发横幅。
     *
     * <p>用 UUID 而不是直接存 {@link ServerPlayer} 引用：玩家可能在 160 tick 内掉线，
     * 到点时再从 {@code PlayerList} 查一次，查不到就静默跳过，<b>不会留下一个已经被丢弃的实体强引用</b>。
     */
    private static final Map<UUID, Integer> PENDING_BANNER = new ConcurrentHashMap<>();

    private ShanhaiRecipeStats() {}

    // ================================================================== 累加器 API

    /**
     * 计数清零。由 KJS 在每次配方加载<b>开始上报之前</b>调一次
     * —— 否则 {@code /reload} 会让同一条配方被累加两次。
     *
     * <p>注意：本方法和 {@link #addResult(boolean)} {@link #reportSummary(String)} 由 KJS 包在
     * <b>同一个 try/catch</b> 里连续调用；任何一步抛异常 ⇒ {@link #reportSummary(String)} 不会被调到
     * ⇒ <b>不会打出那一行</b>。这是刻意的：宁可让机器判定的那一行<b>缺席（响亮失败）</b>，
     * 也不许打出一行<b>残缺但看起来正常</b>的数字。
     */
    public static void reset() {
        TOTAL.set(0L);
        SUCCESS.set(0L);
        FAILED.set(0L);
        // 🔴 刻意【不清】LIFETIME —— 它跨批次累计，是「顺序无关」的基础。
        //    清了它就等于回到「后一个脚本把前一个清掉」的老问题。
    }

    /**
     * 上报<b>一条</b>配方的结果。这是全工程<b>唯一</b>的计数入口（单一数据源）。
     *
     * <p>参数刻意是 {@code boolean} 而不是字符串状态：Rhino 侧只有 `true`/`false` 两种字面量，
     * <b>不存在拼错状态字符串导致静默记错</b>的可能。也刻意<b>不重载</b>这个方法 ——
     * Rhino 的重载解析在有多个同签名候选时是踩坑高发区。
     *
     * @param ok 该配方成功进了 GTCEu recipe builder（true）还是抛了异常（false）
     */
    public static void addResult(boolean ok) {
        TOTAL.incrementAndGet();
        LIFETIME.incrementAndGet();   // 🔴 终身累加，reset() 不清它
        if (ok) {
            SUCCESS.incrementAndGet();
        } else {
            FAILED.incrementAndGet();
        }
    }

    /** 🔴 终身累计上报条数（{@link #reset()} 不清）。顺序无关，任何脚本位置读都得到同一个数。 */
    public static long lifetime() {
        return LIFETIME.get();
    }

    public static long total() {
        return TOTAL.get();
    }

    public static long success() {
        return SUCCESS.get();
    }

    public static long failed() {
        return FAILED.get();
    }

    // ================================================================== 配方加载完成：日志行

    /**
     * 打出<b>机器判定行</b>并返回它。由 KJS 在 {@code ServerEvents.recipes} 回调末尾调用。
     *
     * <p>格式固定（字段集合有意封闭，口径见类注释 §2）：
     * <pre>
     * [SHANHAI-SPEC] recipe_stats scope=&lt;scope&gt; total=&lt;n&gt; success=&lt;n&gt; failed=&lt;n&gt;
     * </pre>
     * <p>实现细节：用 {@link com.shanhai.ShanhaiMod#LOGGER} 而不是另起一个
     * {@code LogUtils.getLogger()} —— 类注释里写明过，各打各的 logger 会让 grep 漏掉。
     * 且这行<b>是纯字符串、不含 {@code {}} 占位符</b>，所以走 {@code info(String)} 时不会被 SLF4J
     * 的占位符替换逻辑动到任何字符。
     *
     * @param scope 这一批数字覆盖的范围（见 {@link #SCOPE_KJS_TEST}）。null/空串会被换成 {@code unknown}，
     *              以免打出一行 {@code scope=} 后面空着的、看起来像解析失败的日志。
     * @return 打出去的那一行原文（便于单测与人工核对）
     */
    public static String reportSummary(String scope) {
        String safeScope = (scope == null || scope.isEmpty()) ? "unknown" : scope;
        long t = TOTAL.get();
        long s = SUCCESS.get();
        long f = FAILED.get();
        // 🔴 先留档再打印：此刻 TOTAL 正好是【这一批】的条数（该批开头 reset 过）
        //    ⇒ 横幅据此一行一批地显示，不需要任何 KJS 侧改动。
        BY_SCOPE.put(safeScope, new long[]{t, s, f});
        String line = LOG_PREFIX + " " + STATS_LOG_KEY
                + " scope=" + safeScope
                + " total=" + t
                + " success=" + s
                + " failed=" + f
                + " lifetime=" + LIFETIME.get();
        ShanhaiMod.LOGGER.info(line);
        return line;
    }

    // ================================================================== 玩家登录：横幅

    /**
     * 玩家登录 → 排一次 160 tick 后的横幅。
     *
     * <p>边判说明：不需要 {@code isClientSide} 之类的判断 —— {@code PlayerLoggedInEvent} 只在<b>逻辑服务端</b>
     * 派发，且这里只读不写、只往聊天框发字，不碰任何存档数据。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        PENDING_BANNER.put(player.getUUID(), BANNER_DELAY_TICKS);
    }

    /**
     * 服务端 tick 计数 → 到点发横幅。
     *
     * <p>{@code Phase.END} 才计：一个 tick 只数一次（START/END 各来一次会让 160 tick 变成 80 tick）。
     * 第一行 {@code isEmpty()} 短路 ⇒ 稳态（没有待发横幅）时这个处理器只是一次 map 判空。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (PENDING_BANNER.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, Integer>> it = PENDING_BANNER.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining > 0) {
                entry.setValue(remaining);
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                sendBannerTo(player);
            }
        }
    }

    /**
     * 横幅内容（10 行，逐行发，每行一个 {@link Component}）。
     *
     * <p>⚠️ <b>「失败」那行与「无报错」那行互斥</b>：{@code failed > 0} 只显示失败行，
     * {@code failed == 0} 才显示「配方库检测无报错 祝领航员航行无阻!」。
     * 老版是两行都显示（它的「禁用 7」不算失败），这里按任务书给的版式做互斥。
     *
     * <p>内容一律用 {@link Component#literal(String)} 直出<b>字面量</b>，不走 lang 键
     * ⇒ <b>本轮不需要动 {@code assets/shanhai/lang/zh_cn.json}（改前=改后=58 键）</b>。
     *
     * <h4>🔴 哪些行带 {@code &$…-} 前缀、哪些不带 —— 这是照原版的分工，不是省事</h4>
     * <b>带前缀的 6 行</b>（每行都照原版原文）：首行 / {@code [OK]配方库 加载完成!} /
     * {@code 欢迎来到GTL寰宇联合重工巨企} / 回执行 / 末句 / 末行。
     * <p>
     * <b>不带前缀的 3 类行</b>（{@code ✅ 成功加载}、{@code 😊 配方库检测无报错}、{@code 🔷 当前重制版版本}，
     * 以及互斥的 {@code ⚠️ 失败}）—— 理由有三条，按分量排序：
     * <ol>
     *   <li><b>原版这几行本来就没有 {@code &$} 前缀</b>。老版对应的三行是
     *       {@code §a📦 成功加载: §eN§a 个配方} / {@code §a😋 配方库检测无报错…} /
     *       {@code §a💽 当前神人私货版本:v…}（取证：方案 md §3.4 的逐行解码表，
     *       第 3/4/5 行的「解析出的色板」一栏是空的）⇒ <b>原版是有意让"只有 6 行在流动"</b>
     *       （方案 md §3.4 原文：「注意：不是每行都有特效，只有 5 行有」）。
     *       给它们加渐变会把这个层次感做平。</li>
     *   <li><b>{@code ⚠️ 失败} 行<b>不该</b>是喜庆的流动色</b>：一条报错被染成流光溢彩会传递错误信号。
     *       本工程口径是「宁可缺，不可假」——它保持素色就是"这条是特殊状态"的可视信号。</li>
     *   <li><b>本轮任务范围就是「加回前缀」</b>：给这 3 行挑新样式属于<b>新增设计</b>，
     *       而用户给的选项里明确包含「或保持无前缀 ✅」。真要给它们挑样式，改 3 个字符串即可。</li>
     * </ol>
     */
    public static List<String> bannerLines() {
        long failed = FAILED.get();
        long lifetime = LIFETIME.get();

        List<String> lines = new ArrayList<>(16);
        lines.add(PREFIX_GLITCH_GOLDEN + BAR_TOP);
        lines.add(PREFIX_FLOATX_MOSS + "[OK]配方库 加载完成!");
        // 🔴 2026-09-27 改：原来这里只有一行「✅ 成功加载: <TOTAL> 个配方」，而 TOTAL 是【全局】累加器
        //    ⇒ 谁后报就显示谁（实测显示测试的 39）⇒ 读不出"每批各多少条"，用户当场就问了。
        //    现在改成【一行一批】，数据来自 BY_SCOPE（每次 reportSummary 时留档）。
        //    排序：批条数多的在前；同数按 scope 名字典序（保证两次运行顺序一致，便于比对）。
        List<String> scopes = new ArrayList<>(BY_SCOPE.keySet());
        scopes.sort((a, b) -> {
            long ta = BY_SCOPE.get(a)[0];
            long tb = BY_SCOPE.get(b)[0];
            return ta != tb ? Long.compare(tb, ta) : a.compareTo(b);
        });
        for (String sc : scopes) {
            long[] v = BY_SCOPE.get(sc);
            String label = SCOPE_LABELS.getOrDefault(sc, sc);
            lines.add("✅ " + label + ": " + v[0] + " 个配方"
                    + (v[2] > 0 ? ("（失败 " + v[2] + " 个）") : ""));
        }
        if (scopes.isEmpty()) {
            // 一批都没上报过（例如 BY_SCOPE 为空）⇒ 说清楚，不编数字、也不显示一个像模像样的 0
            lines.add("✅ 暂无可统计的配方批次");
        }
        // 🔴 山海相关【全部】配方的汇总（解构 + PF + 测试 + 未来任何上报方）。
        //    取 LIFETIME（不被 reset 清零）⇒ 横幅在玩家登录后 160 tick 才发，
        //    此时各脚本都上报完了 ⇒ 这个数就是用户要的「shanhai 相关所有配方」。
        //    ⚠️ 运行期读，代码里没有任何写死的数字。
        lines.add("🔷 shanhai 相关配方总数: " + lifetime);
        if (failed > 0) {
            lines.add("⚠️ 失败: " + failed + " 个");
        } else {
            lines.add("😊 配方库检测无报错 祝领航员航行无阻!");
        }
        lines.add("🔷 当前重制版版本: " + modVersion());
        lines.add(PREFIX_AURORA + "欢迎来到GTL寰宇联合重工巨企");
        lines.add(PREFIX_MOSS + "此成功信息回执由JAVA侧: " + RECEIPT_CLASS_NAME + " 生成");
        lines.add(PREFIX_SILVER + "老大我们这样熬夜写私货心脏真的不会自己先休息吗");
        lines.add(PREFIX_GLITCH_GOLDEN + BAR_BOTTOM);
        return lines;
    }

    /** 给玩家逐行发横幅。{@code null} 玩家静默跳过（调用方本来就会判，这里再加一道）。 */
    public static void sendBannerTo(ServerPlayer player) {
        if (player == null) {
            return;
        }
        for (String line : bannerLines()) {
            player.sendSystemMessage(Component.literal(line));
        }
    }

    /**
     * 取本 mod 的运行期版本（横幅 {@code 🔷 当前重制版版本: …} 那一行）。
     *
     * <p>取不到就返回 {@link #VERSION_UNAVAILABLE}，<b>绝不回落到写死的版本号</b> ——
     * 活界面上印假数字是本工程的血账之一。
     *
     * <p>老版那一行是 {@code v2.7.4fix}，带 {@code v} 前缀，所以这里也补一个（版本串本身若已带 v/V 就不重复补）。
     */
    public static String modVersion() {
        String raw;
        try {
            raw = ModList.get()
                    .getModContainerById(ShanhaiMod.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse(VERSION_UNAVAILABLE);
        } catch (Throwable ignored) {
            raw = VERSION_UNAVAILABLE;
        }
        if (raw.isEmpty()) {
            return VERSION_UNAVAILABLE;
        }
        char first = raw.charAt(0);
        return (first == 'v' || first == 'V') ? raw : ("v" + raw);
    }
}
