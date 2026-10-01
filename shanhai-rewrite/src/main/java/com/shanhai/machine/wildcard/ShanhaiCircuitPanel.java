package com.shanhai.machine.wildcard;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import net.minecraft.network.chat.Component;

/**
 * 「每块通配符样板各设一个电路」的中键弹框（中文题头 + 显示当前值）。
 *
 * <h2>一、它怎么被打开（🔴 2026-10-01 已改版）</h2>
 * 界面现在照超级样板总成那一套装配：样板格由 gtlcore 的
 * {@code PaginationUIManager} 生成，中键走它 per-slot 的
 * {@code createPaginationUI(IntConsumer)} 回调，回调里带着<b>被点中那一格的格号</b>调
 * {@link #shanhaiOpenFor(int, int)}。
 *
 * <p>⚠️ 这条链<b>完全不读槽里有没有物品</b> —— 上游 {@code AEPatternViewExtendSlotWidget}
 * 的中键分支在 {@code isEmpty()} 判定<b>之前</b>就 {@code return true}（字节码偏移 23–57 早于 76/91）。
 * 我们此前自建的那个样板格小部件多要了一个「槽非空」条件，那正是用户第 3 条
 * 「没有装通配符样板的格子都点不开」的根因；那个类已经删除。
 *
 * <p>关闭走「确定 / 清除 / 取消」三个按钮。
 *
 * <h2>二、🔴 为什么是「常驻但默认隐藏」，而不是动态往界面里塞</h2>
 * 面板在 {@code createUIWidget()} 里<b>无条件</b>装配一次，之后只切 {@code setVisible/setActive}；
 * LDLib 的 {@code WidgetGroup.mouseClicked} 会跳过 {@code !isVisible() || !isActive()} 的子节点，
 * 所以隐藏时的面板不会抢格子上的点击。
 *
 * <h2>三、控件与语义</h2>
 * <ul>
 *   <li>{@code IntInputWidget} 取值 <b>0–32</b>：{@code 0} = 不设置（清除），{@code 1–32} = 指定电路。
 *       上限 32 与上游 {@code IntCircuitBehaviour} 的合法区间一致。</li>
 *   <li>「确定」把当前值发给服务端；「清除」发 0；「取消」直接关。</li>
 *   <li>⚠️ 面板只是**客户端界面**：真正的写入在机器的
 *       {@code shanhaiApplySlotCircuit}（第一句 {@code isRemote()} 早退，只服务端生效）。</li>
 * </ul>
 *
 * <h2>四、🔴 2026-10-01 用户实测回报的三条 bug 与根因（全部有 javap 字节码证据）</h2>
 *
 * <h3>4.1 输入框「打不了字」—— 输入框被压成了 <b>8 px 宽</b>（根因）</h3>
 * 用户原话（逐字）：「有贴图了，但是我【无法在输入框中输入数字】，而且【文字都到框的外面了】」。
 * <p>旧代码是 {@code new IntInputWidget(6, 31, 42, 16, …)} —— <b>42 px 宽</b>。
 * 而 GTCEu 的 {@code NumberInputWidget.buildUI()}（javap -p -c 原文）是：
 * <pre>
 *   n  = Mth.clamp(getSize().width / 5, 15, 40)        → 42/5 = 8 → 夹到 15
 *   n2 = getSize().width - 2*n - 4                     → 42 - 30 - 4 = 8
 *   new ButtonWidget(0,        0, n,  20, "-" 按钮)
 *   new TextFieldWidget(n + 2, 0, n2, 20, …)           ← 真正的输入框只有 n2 = 8 px
 *   new ButtonWidget(n + n2 + 4, 0, n, 20, "+" 按钮)
 * </pre>
 * ⇒ <b>内部那个 {@code TextFieldWidget} 只有 8×20 px</b>（屏幕上就是面板 x=23…31 那一小条）。
 * 而 LDLib 的 {@code TextFieldWidget.mouseClicked} 原文是
 * <pre>
 *   0: … isMouseOverElement(DD)Z
 *  21: … setFocus(Z)V                    ← 焦点唯一来源就是"鼠标在不在这个矩形里"
 *  34: EditBox.setFocused(Z)V
 * </pre>
 * ⇒ <b>点不到那 8 px 就永远拿不到焦点，{@code EditBox} 一个字符都不吃</b>。
 * ⚠️ 用户看到的那两个 {@code -1} / {@code +1} <b>不是</b>独立按钮，正是这个 widget 自带的两个
 * {@code ButtonWidget}（文字 = "步长×点击"，普通 1 / Shift 8 / Ctrl 64 / CtrlShift 512；
 * 见 {@code IntInputWidget.getChangeValues()}）。所谓"一个黑框"就是它们中间那 8 px。
 *
 * <p><b>修法</b>：改用 {@code IntInputWidget} 的 {@code Position} 重载
 * （{@code new IntInputWidget(Position, Supplier, Consumer)}）——
 * 它内部固定 {@code new Size(100, 20)}（javap 原文），于是
 * {@code n = clamp(20,15,40) = 20}、{@code n2 = 100 - 40 - 4 = 56} ⇒ <b>输入框 56 px</b>。
 * 这正是原版「超级样板总成」那台机器的写法：
 * {@code org.gtlcore.gtlcore.api.gui.MEPatternCatalystUIManager.createCacheCountInputWidget}
 * 用的就是同一个重载 ⇒ 用户要的「照图3/图4 的形态」随之达成。
 *
 * <h3>4.2 提示行「文字都到框的外面了」—— {@code LabelWidget} 不换行也不裁剪</h3>
 * LDLib {@code LabelWidget.updateSize()} 原文把自身尺寸设成 {@code (Font.width(文本), 9)}，
 * {@code drawInBackground()} 只按 {@code \n} 分行、每行都从 {@code Position.x} 原样往右画 ——
 * <b>没有宽度上限、没有裁剪、没有自动缩放</b>（方法表里也没有 {@code setWidth}/{@code setTextAlign}）。
 * 旧的一行提示约 250 px，面板只有 150 px ⇒ 必然画到框外。
 * <p><b>修法</b>：把提示拆成两行（lang 值里写 {@code \n}），每行都在面板宽度以内；
 * 并把面板加宽到 154 px 给足余量。
 *
 * <h3>4.3 题头「第 <b>-</b> 格」—— {@code LabelWidget} 的文本供应器<b>永远不会被再调用</b></h3>
 * 这是本轮最隐蔽的一条。{@code LabelWidget.updateScreen()} 的 javap 原文：
 * <pre>
 *   updateScreen():
 *      0: Widget.updateScreen()
 *      4: getfield isClientSideWidget:Z
 *      8: ifeq 63                 ← 🔴 isClientSideWidget == false ⇒ 直接 return
 *     19: getfield textSupplier … invokeinterface Supplier.get()
 *     43: 与 lastTextValue 比较，不同才 putfield lastTextValue + updateSize()
 * </pre>
 * ⇒ <b>只有「客户端小部件」才会每帧重取 supplier</b>。而
 * <ul>
 *   <li>构造时（客户端，{@code isRemote()==true}）{@code lastTextValue} 被<b>快照一次</b> ——
 *       那一刻 {@code targetSlotIndex} 还是 {@code -1} ⇒ 快照下来的就是
 *       「样板电路设置：第 - 格／当前 —」；</li>
 *   <li>之后无论 {@link #shanhaiOpenFor} 怎么改 {@code targetSlotIndex}，
 *       因为 {@code isClientSideWidget == false}，{@code updateScreen()} 永远不再重取 ⇒
 *       <b>题头就永久停在那句快照上</b>。</li>
 * </ul>
 * （服务端那条推送路也堵死了：{@code WidgetGroup.detectAndSendChanges()} 只对 {@code isActive()} 的
 * 子节点递归，而面板的 {@code setActive(true)} 只发生在客户端 ⇒ 服务端恒 inactive ⇒ 不会覆盖。）
 * <p><b>修法</b>（两条一起上，互为兜底）：
 * <ol>
 *   <li>两个 {@code LabelWidget} 各调一次 {@code setClientSideWidget()}
 *       ⇒ {@code updateScreen()} 里那句 {@code textSupplier.get()} 从此每帧执行；</li>
 *   <li>题头的格号<b>不再读影子状态</b>，改由中键回调<b>当场传进来的格号</b>（{@code this.targetSlotIndex}）
 *       决定 —— 它与「面板可见」由同一对字段设置，结构上不可能再对不上。
 *       ⚠️ 2026-10-01 二次改版后，「值」也不再来自面板自己的局部字段：
 *       {@link #shanhaiOpenFor(int, int)} 的第二个入参由机器现取。</li>
 * </ol>
 *
 * <h3>4.4 ⚠️ 如实交代：哪些是证实的、哪些不是</h3>
 * <ul>
 *   <li><b>证实</b>（javap 字节码 + 算术）：42 → 输入框 8 px；{@code Position} 重载 → 100×20 → 56 px；
 *       {@code TextFieldWidget.mouseClicked} 的焦点判据就是矩形；{@code LabelWidget.updateScreen()}
 *       的 supplier 分支被 {@code isClientSideWidget} 挡住；{@code LabelWidget} 不换行不裁剪。</li>
 *   <li><b>未证实</b>：以上三条在<b>实机渲染</b>上的最终观感（本轮禁止启动游戏）。
 *       提示行两行的像素宽是<b>估算</b>（半角 6 px / 全角 9 px 的上界口径），没有实测
 *       {@code Font.width}；面板 154 px 的余量就是按这个上界留的。</li>
 * </ul>
 */
public class ShanhaiCircuitPanel extends WidgetGroup {

    private static final String LANG_TITLE = "shanhai.machine.super_wildcard_pattern_buffer.circuit.title";
    private static final String LANG_HINT = "shanhai.machine.super_wildcard_pattern_buffer.circuit.hint";
    private static final String LANG_CONFIRM = "shanhai.machine.super_wildcard_pattern_buffer.circuit.confirm";
    private static final String LANG_CLEAR = "shanhai.machine.super_wildcard_pattern_buffer.circuit.clear";
    private static final String LANG_CANCEL = "shanhai.machine.super_wildcard_pattern_buffer.circuit.cancel";

    /**
     * 面板尺寸。
     *
     * <p>🔴 2026-10-01：由 150×52 改成 <b>154×84</b>，三个理由（见类注释 §4）：
     * <ol>
     *   <li>输入行走 {@code Position} 重载 ⇒ 固定 100×20，原来那行只留了 16 px 高，装不下；</li>
     *   <li>提示行拆成两行 ⇒ 要往下让 9 px；</li>
     *   <li>「确定/清除/取消」要挪到第三行，否则会被 100 px 宽的输入行挤掉。</li>
     * </ol>
     * 154 的来源与**当前**坐标口径（🔴 2026-10-01 紧急修复后更新）：
     * 主界面组是 {@code (0, 0, uiWidth, uiHeight)}，装配处是
     * {@code SuperWildcardPatternBufferPartMachine#createUIWidget}，本面板挂在 {@code (4, uiHeight + 4)}。
     * <ul>
     *   <li>{@code uiWidth = max(每行槽数*18+16, 106)}，每行槽数 = {@code WILDCARD_SLOT_COUNT}(10) ⇒ <b>196</b>；
     *       4 + 154 = 158 ≤ 196，横向装得下。⚠️ 改 {@code uiWidth} 时必须保证 ≥ 4 + {@link #PANEL_WIDTH}。</li>
     *   <li>{@code uiHeight = 每页行数*18+28 = 1*18+28 = 46} ⇒ 本面板从 y=50 画到 134，
     *       **本来就画在组的下面**（浮层形态），与上一版（uiHeight=136、面板 140..224）同一种画法。</li>
     * </ul>
     * ⚠️ <b>不要</b>改上面这两处坐标口径，改面板尺寸前先回读那一行装配代码。
     */
    public static final int PANEL_WIDTH = 154;
    public static final int PANEL_HEIGHT = 84;

    /** 输入行给 {@code IntInputWidget} 的宽度 —— 100 是那个 {@code Position} 重载写死的值（javap 原文）。 */
    private static final int INPUT_ROW_WIDTH = 100;

    /**
     * 本面板归属的机器（服务端入口）。
     *
     * <p>🔴 2026-10-01 改版：面板<b>不再</b>通过那个自建的样板格小部件回传值
     * （{@code ShanhaiCircuitSlotWidget} 那条「中键 → 弹框 → writeClientAction」链路已整条作废），
     * 改成中键时由 {@code PaginationUIManager} 的 per-slot 回调把<b>格号</b>直接交给面板，
     * 「确定」再带格号走机器自己的 {@code shanhaiApplySlotCircuit}。
     */
    private SuperWildcardPatternBufferPartMachine machine;

    /** 当前正在编辑的样板格号（0 起）；{@code -1} = 面板没开。 */
    private int targetSlotIndex = -1;

    /** 编辑中的值（0 = 不设置）。{@link IntInputWidget} 读写它。 */
    private int editValue = ShanhaiSlotCircuit.NONE;

    /** 题头那两个 label 各留一份引用（诊断/未来的动态改文本用；构造完就不再变）。 */
    private final LabelWidget titleLabel;
    private final LabelWidget hintLabel;

    public ShanhaiCircuitPanel(int x, int y) {
        super(x, y, PANEL_WIDTH, PANEL_HEIGHT);
        this.setBackground(GuiTextures.BACKGROUND);

        this.titleLabel = new LabelWidget(6, 5, this::titleText);
        this.hintLabel = new LabelWidget(6, 17, this::hintText);
        // 🔴 2026-10-01（类注释 §4.3）：这两句是本轮修「第 - 格」的关键。
        //    LabelWidget.updateScreen() 里那句 textSupplier.get() 只在 isClientSideWidget 为真时执行；
        //    不设它 ⇒ supplier 一辈子只在构造时被调一次 ⇒ 题头永久停在"第 - 格／当前 —"。
        this.titleLabel.setClientSideWidget();
        this.hintLabel.setClientSideWidget();
        this.addWidget(this.titleLabel);
        this.addWidget(this.hintLabel);

        // 🔴 用 Position 重载（内部 Size(100,20)），**不要**用 (x,y,w,h) 重载：
        //    后者在 w=42 时会把内部输入框压成 8 px（类注释 §4.1 的算术）。
        final IntInputWidget input =
                new IntInputWidget(new Position(6, 38), this::readEditValue, this::writeEditValue);
        input.setMin(ShanhaiSlotCircuit.NONE);
        input.setMax(ShanhaiSlotCircuit.MAX);
        this.addWidget(input);

        this.addWidget(new ButtonWidget(6, 64, 44, 16, buttonTexture(LANG_CONFIRM), data -> confirm()));
        this.addWidget(new ButtonWidget(54, 64, 44, 16, buttonTexture(LANG_CLEAR), data -> clear()));
        this.addWidget(new ButtonWidget(102, 64, 46, 16, buttonTexture(LANG_CANCEL), data -> closePanel()));

        this.setVisible(false);
        this.setActive(false);
    }

    /** 输入行实际占的宽度（给离线自证装置读；= {@link #INPUT_ROW_WIDTH}）。 */
    public static int inputRowWidthForProbe() {
        return INPUT_ROW_WIDTH;
    }

    /** 由机器在装配界面时调用一次（面板需要它才能把「确定」送到服务端）。 */
    public void shanhaiBindMachine(SuperWildcardPatternBufferPartMachine owner) {
        this.machine = owner;
    }

    /**
     * 打开面板并把当前值读进来。由中键回调调用（客户端）。
     *
     * <p>🔴 两个入参都是<b>调用那一刻现取</b>的：格号来自被点中的样板格，
     * 当前值来自机器的 {@code @DescSynced} 数组。**不再**有任何影子状态 ——
     * 「面板开着」与「题头显示的格号/值」由同一对字段决定，结构上不可能串格（用户第 2 条）。
     */
    public void shanhaiOpenFor(int slotIndex, int currentCircuit) {
        this.targetSlotIndex = slotIndex;
        this.editValue = currentCircuit;
        this.setVisible(true);
        this.setActive(true);
    }

    /** 关掉面板并忘掉目标（避免误触）。 */
    public void closePanel() {
        this.targetSlotIndex = -1;
        this.setVisible(false);
        this.setActive(false);
    }

    // ------------------------------------------------------------------ 按钮

    private void confirm() {
        final int slotIndex = this.targetSlotIndex;
        final int value = this.editValue;
        closePanel();
        this.send(slotIndex, value);
    }

    private void clear() {
        final int slotIndex = this.targetSlotIndex;
        closePanel();
        this.send(slotIndex, ShanhaiSlotCircuit.NONE);
    }

    /**
     * 把「第 N 格 = 值」交给机器。
     *
     * <p>服务端权威：机器自己是 {@code @Persisted @DescSynced} 的持有者，值由它落到
     * {@code SlotCacheManager.setCircuitCache}。⚠️ 客户端那一侧也会调用本方法（LDLib 的
     * 按钮回调两侧都跑），但机器里的实现第一句就是 {@code isRemote()} 早退，所以不会双写。
     */
    private void send(int slotIndex, int value) {
        final SuperWildcardPatternBufferPartMachine owner = this.machine;
        if (owner == null || slotIndex < 0) {
            return;
        }
        owner.shanhaiApplySlotCircuit(slotIndex, value);
    }

    // ------------------------------------------------------------------ 文本与取值

    /**
     * 题头：{@code 样板电路设置：第 N 格／当前 X}。
     *
     * <p>🔴 2026-10-01：格号与当前值都<b>现取</b>（{@code targetSlotIndex} ＋ 机器的同步数组），
     * 面板没开时显示 {@code —}。
     */
    private String titleText() {
        final int slot = this.targetSlotIndex;
        final String slotText = slot < 0 ? ShanhaiSlotCircuit.NONE_TEXT : Integer.toString(slot + 1);
        return Component.translatable(LANG_TITLE, slotText,
                ShanhaiSlotCircuit.toDisplayText(this.editValue)).getString();
    }

    /**
     * 提示行。
     *
     * <p>🔴 2026-10-01：lang 值现在带一个 {@code \n}（两行）。原因见类注释 §4.2：
     * {@code LabelWidget} 既不换行也不裁剪，单行 250 px 的文案在 150 px 面板里必然画到框外。
     * ⚠️ 本方法的返回值会被 {@code updateScreen()} 每帧取一次，<b>不要</b>在这里做重活。
     */
    private String hintText() {
        return Component.translatable(LANG_HINT,
                ShanhaiSlotCircuit.MIN, ShanhaiSlotCircuit.MAX, ShanhaiSlotCircuit.NONE).getString();
    }

    private Integer readEditValue() {
        return this.editValue;
    }

    private void writeEditValue(Integer value) {
        this.editValue = ShanhaiSlotCircuit.normalize(value != null, value == null ? ShanhaiSlotCircuit.NONE : value);
    }

    /**
     * 按钮贴图 = 一块写死文字的 {@link TextTexture}。
     *
     * <p>⚠️ 传进去的是**已经取过 .getString() 的字面量** —— 与上一版 GUI 里那几行
     * {@code LabelWidget(…, () -> Component.translatable(…).getString())} 同一写法，
     * 保证不会去查一个不存在的 lang key。
     */
    private static TextTexture buttonTexture(String langKey) {
        return new TextTexture(Component.translatable(langKey).getString());
    }
}
