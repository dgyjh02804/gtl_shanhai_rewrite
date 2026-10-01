package com.shanhai.client.compat;

import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;

import org.gtlcore.gtlcore.client.gui.MEStorageConfiguratorTabLayout;

/**
 * 🔴 <b>把机器的侧栏按钮列从「一条长竖列」改成「4 行一列的网格」—— 只对指定的那一个
 * {@code ConfiguratorPanel} 生效（2026 本轮新增，用户实机提出）。</b>
 *
 * <h2>用户原话（逐字）</h2>
 * <blockquote>「还有一件事，主机的左下角列表太长了，可以改成像超级样板总成这样的」</blockquote>
 * ⇒ 他要的就是<b>超级样板总成（{@code gtladditions:me_super_pattern_buffer}）</b>那种紧凑布局：
 * 按钮不再是一条竖列，而是<b>两列 × 4 行</b>的网格。
 *
 * <h2>🔴 关键发现：那套布局【不用我们做】，gtlcore 已经做完了</h2>
 * 反编译 + 字节码扫描（{@code temp/recon-2026/sidebar/}）逐条核实，证据链如下：
 * <pre>
 * ① 布局算法本体（gtlcore，客户端类，<b>无 @OnlyIn</b>，public static）：
 *      org.gtlcore.gtlcore.client.gui.MEStorageConfiguratorTabLayout
 *        public static final int TABS_PER_COLUMN = 4;                 ← 【4 行】
 *        public static void setEnabled(ConfiguratorPanel, boolean)
 *        public static void arrange(ConfiguratorPanel)
 *        public static Position positionFor(ConfiguratorPanel, int)
 *        positionFor 原文（逐行照抄）：
 *          int stride        = panel.getTabSize() + 2;                 // 26
 *          int column        = tabIndex / 4;
 *          int row           = tabIndex % 4;
 *          int tabsInColumn  = Math.min(4, tabCount - column * 4);
 *          int columnHeight  = tabsInColumn * stride - 2;
 *          int y             = panel.getSize().height - columnHeight + row * stride;
 *          return new Position(-column * stride, y);                   ← 第 2 列往【左】溢出 26px
 *        ⇒ <b>每 4 个一行、第 5 个起另起一列并对齐到底部</b> —— 与用户截图上看到的
 *          「两列 × 4 行」逐字吻合。
 * ② 谁把它打开（gtlcore 的 {@code FancyMachineUIWidgetMixin}，@HEAD of setupFancyUI）：
 *      MEStorageConfiguratorTabLayout.setEnabled(this.configuratorPanel,
 *          page instanceof MEHatchPartMachine || page instanceof MEPatternBufferPartMachineBase);
 *    ⇒ <b>超级样板总成命中</b>：gtladditions 的
 *      {@code MESuperPatternBufferPartMachine extends MEPatternBufferPartMachine
 *       extends MEPatternBufferPartMachineBase}（三种类逐级 findByClass 核对过）。
 *    ⇒ <b>而我们的主机【不在】那两个类型里 ⇒ 它自己是【关】的</b>，这就是主机仍是一条长竖列的原因。
 * ③ 谁负责在每次挂 tab 之后重新摆位（gtlcore 的 {@code ConfiguratorPanelMixin}）：
 *      @Inject(method = "attachConfigurators", at = @At("TAIL"))  → arrange(panel)
 *      @Redirect(method = {"collapseTab","expandTab"},
 *                target = "ConfiguratorPanel$Tab;collapseTo(II)V")
 *        ⇒ 开着的 panel 用 positionFor(...) 摆位，其余原样（0, i*(tabSize+2)）
 *    ⇒ <b>只要把开关打开，摆位/展开/收起三条路全部自动走网格分支</b>，我们一行布局代码都不用写。
 * ④ 溢出的那一列怎么还能被点到（gtlcore 的 {@code MEStorageConfiguratorTabBoundsMixin}）：
 *      往 {@code Widget.isMouseOverElement} / {@code getHoverElement} 的 RETURN 上打补丁，
 *      对<b>开着的</b> panel 把"落在溢出 tab 上"也算作命中。
 *    ⇒ 这一条解释了为什么第 2 列跑到 panel 自己的包围盒【外面】（x 为负）却仍然可点。
 * </pre>
 *
 * <h2>🔴 为什么这样写，而不是自己写一个 mixin（三条路的取舍）</h2>
 * <pre>
 *   ① 自己写 mixin 改 ConfiguratorPanel 的竖列布局 —— <b>不需要了</b>：gtlcore 已经改完，
 *      而且改的正是我们要的那一种（同一套算法 = 观感与超级样板总成【完全一致】）。
 *   ② 自己加一个「折叠」按钮 —— 要新写一个 {@code IFancyConfiguratorButton}，还只能收起
 *      【别人的】tab（{@code ConfiguratorPanel} 没有"隐藏某个 tab"的 API，只有
 *      attach 与 clear()），折叠态又要自己开同步位；成本明显更高、观感还只是"像"。
 *   ③ 把我们自己的几个面板并成一个 tab —— 只能从 7 个压到 3~4 个，
 *      而且会改变用户已经熟悉的交互（每个面板一个图标），收益小于代价。
 *   ④ ✅ <b>本方案</b>：调 gtlcore 的公开静态入口把开关打开。
 *      成本 = 1 个 import + 1 行调用；风险 = 只依赖 gtlcore 的 public static 方法
 *      （不是 mixin target、不是反射、不是私有字段），而本工程<b>本来就硬依赖 gtlcore</b>
 *      （主机实现了 {@code IModularMachineHost} 等一批 gtlcore 接口）。
 * </pre>
 *
 * <h2>⚠️ 为什么单独开一个类，而不是在主机类里直接调</h2>
 * 本工程既有纪律（见 {@code GtlAddClientCompat} 的类注释）：<b>把"谁在碰第三方客户端类"收成一句 grep</b>。
 * {@code MEStorageConfiguratorTabLayout} 位于 {@code gtlcore.client.gui} 包，本次用 {@code javap -v}
 * 核实它<b>自己没有 {@code @OnlyIn}</b>（同一次核实：{@code ConfiguratorPanel} 的
 * <b>类级也没有</b> {@code @OnlyIn}，只有 {@code drawWidgetsBackground}/{@code drawWidgetsForeground}/
 * {@code mouseClicked}/{@code drawInBackground} 四个<b>方法</b>有）。⇒ 收进网关类是为了可检索性。
 *
 * <h2>🔴 本类【刻意不】加 {@code @OnlyIn(Dist.CLIENT)}（这是一个已避开的坑）</h2>
 * 第一版写了 {@code @OnlyIn(Dist.CLIENT)}，复核时<b>撤掉</b>了，理由是
 * {@code IFancyUIMachine#attachConfigurators} 在<b>服务端也会被跑一次</b>
 * （本工程既有留档：{@code ParallelOverrideConfigurator} 的类注释里
 * 「本方法与 attachConfigurators 一样，在【服务端与客户端各跑一次】」）——
 * 而 FML 的 dist cleaner 会把 {@code @OnlyIn(CLIENT)} 的类在专用服务端上拦下
 * ⇒ 那会<b>凭空造出一个新的服务端崩溃点</b>。
 * 本类的内容只有两件事：往 gtlcore 的 {@code WeakHashMap} 集合里放一个 key、
 * 以及逐个 tab 调 {@code setSelfPosition}（两者都是 LDLib 的 common 侧 API，
 * 服务端跑一遍完全无害）⇒ <b>保持 dist 中立才是正确的</b>。
 * <p>⚠️ 诚实标注：本工程当前只有<b>集成服务器</b>（单人/局域网）这一种实际运行场景，
 * 上述"专用服务端"风险本轮<b>没有实测</b>；这里是按 jar/注解的静态事实做的防御性选择。
 *
 * <h2>⚠️ 诚实边界（本轮未做、也做不了的验证）</h2>
 * <ul>
 *   <li><b>红线上不许启动 Minecraft 客户端</b> ⇒ 「实机打开主机 GUI，按钮确实是 2 列，
 *       且不超出屏幕」这条<b>本轮没有验证</b>，只能由用户进游戏看。</li>
 *   <li>几何是<b>算得出来的</b>：7 个 tab ⇒ {@code tabs.size()*26-2 = 180} 的面板高度，
 *       第 1 列 4 格（{@code columnHeight = 102}，y = 78/104/130/156），
 *       第 2 列 3 格（{@code columnHeight = 76}，y = 104/130/156，x = -26）
 *       ⇒ <b>最高点从"距面板底 180px"降到"距面板底 102px"</b>，
 *       即整条最高处<b>下移 78px</b>——这正是"太长了"的量化解法。</li>
 *   <li>⚠️ 本类<b>只作用于我们指定的那一个 panel</b>（gtlcore 那边是 WeakHashMap 按 panel 实例记账），
 *       不改变其它机器（含模块侧）的布局。模块侧当前只有 1~2 个 tab，本来就不挤，<b>有意不动它</b>。</li>
 * </ul>
 */
public final class ConfiguratorTabGridCompat {

    private ConfiguratorTabGridCompat() {}

    /**
     * 把该面板的侧栏按钮切成「4 行一列」的网格（= 超级样板总成那种紧凑布局）。
     *
     * <p>🔴 <b>必须在 {@code attachConfigurators} 里、【第一次】{@code panel.attachConfigurators(...)}
     * 之前调用。</b>理由：gtlcore 的 {@code ConfiguratorPanelMixin} 是在
     * {@code attachConfigurators} 的 <b>TAIL</b> 上触发 {@code arrange(panel)} 的
     * ⇒ 打开开关之后每一次挂 tab 都会自动重排；<b>开关之前挂上去的 tab 要等下一次挂载才被重排</b>。
     * 放在最前面最省事，也避免"最后一个 tab 位置是错的"这种只在实机才看得见的偏差。
     *
     * <p>{@code arrange(panel)} 是<b>幂等</b>的（只是 {@code setSelfPosition}），多调一次没有副作用。
     */
    public static void enableTabGrid(ConfiguratorPanel panel) {
        MEStorageConfiguratorTabLayout.setEnabled(panel, true);
        MEStorageConfiguratorTabLayout.arrange(panel);
    }
}
