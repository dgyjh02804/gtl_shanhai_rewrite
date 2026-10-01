package com.shanhai.common.machine;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import com.shanhai.item.ShanhaiItems;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 🔴 <b>「并行数」侧栏面板（2026-09-27 新增，用户实机提出）。</b>
 *
 * <h2>用户原话（逐字）</h2>
 * <blockquote>「在模块和主机的左下角再新增一个全新的按钮，他可以调节主机或者模块的并行数，
 * 作为一个输入框，可以让玩家输入数字，并且右边有一个一键调至最大的按钮」</blockquote>
 *
 * <h2>「左下角」是哪儿（不是猜的）</h2>
 * GTCEu 的机器 GUI 左侧那条竖排按钮条就是 {@code ConfiguratorPanel}：它的
 * {@code Tab} 的 y = {@code index * (tabSize + 2)}，而整条面板由
 * {@code FancyMachineUIWidget.setupFancyUI} 底对齐（{@code setSelfPosition(new Position(-26,
 * gui.getHeight() - panel.getSize().height - 4))}，字节码）
 * ⇒ <b>最后 attach 的那一个落在整条的最下面 = 左下角</b>。
 * 本工程的既有先例就是这么写的（{@code PrimordialOmegaEngineMachine#attachConfigurators} 里
 * 「始终渲染为工作状态」那一段的注释原文：「最后挂的这一个落在整条【最下面】＝用户指定的「左下角」」）。
 * ⇒ 本面板用 {@code panel.attachConfigurators(new ParallelOverrideConfigurator(this))} 挂在两侧列表的<b>末尾</b>。
 *
 * <h2>面板里有什么（照用户描述：左边输入框 + 右边「一键最大」）</h2>
 * <pre>
 *   y=3   标题
 *   y=16  实时读数：当前生效 / 自动值
 *   y=34  [ TextFieldWidget 86×16 ][ ButtonWidget 52×16 ] ← 右边这个就是「一键最大」
 *   y=58  脚注：输入 0 = 恢复自动 · 超过上限会被钳到上限
 * </pre>
 * <p>🔴 <b>2026-09-27 语义改正（用户实机提出）</b>：
 * <pre>
 *   用户原话（逐字）：「我说一键最大是到机器可以达到的并行数（也就是设置 0 时机器的并行数），
 *                     而且也不允许玩家输入超出机器可以达到最大并行数的数字」
 *   ⇒ 「一键最大」填 = {@code machine.getParallelOverrideCeiling()} = 【该机器此刻的自动值】
 *      （⛔ 上一版填的是 {@code PARALLEL_MAX = Long.MAX_VALUE}）
 *   ⇒ 输入框上钳同样到那个值（服务端 {@code clampOverrideToCeiling}，动态，见下）
 * </pre>
 * 实际弹出面板 = 面板尺寸 + (8, 28)（{@code ConfiguratorPanel$Tab} 构造器字节码：
 * {@code view.setSize(widget.width + border*2, widget.height + button.height + border)}，
 * border=4 / tabSize=24），本面板 150×78 ⇒ 弹出 158×106
 * （对照：{@code StarRenderConfigurator} 是 150×116 ⇒ 158×144，且它自己的注释记着"可能溢出屏幕"的已知风险
 * —— 本面板比它矮 38px，溢出风险只会更小）。
 *
 * <h2>🔴 为什么这里敢用文本输入，而 {@code StarRenderConfigurator} 当初不敢</h2>
 * 那份注释写的是「本工程编译期吃的 {@code libs/ldlib-forge-1.20.1-1.0.33.b.jar} 里<b>没有</b>
 * {@code IntInputWidget}」—— <b>前半句为真、结论已过时</b>（2026-09-27 逐条核实）：
 * <pre>
 *   libs/ldlib-…1.0.33.b.jar 的 gui/widget/ 里确实没有 IntInputWidget，
 *   但那不是唯一的来源 —— {@code com.gregtechceu.gtceu.api.gui.widget.IntInputWidget}
 *   就在 libs/gtceu-1.20.1-1.4.4.jar 里（public，可用），而且本工程侧栏第 5 个控件
 *   {@code gtladditions LimitedDurationConfigurator} 用的就是它。
 *   另外 LDLib 自己有 {@code com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget}（本类用的就是这个）。
 * </pre>
 * <b>本类选 {@code TextFieldWidget} 而不是 GTCEu 的 {@code IntInputWidget}</b>，理由是<b>值域</b>：
 * {@code IntInputWidget} 是 {@code Supplier&lt;Integer&gt;}，装不下本工程的 long 并行
 * （主机 {@code MAX_PARALLEL = Long.MAX_VALUE}；模块并行表最高档也是 {@code Long.MAX_VALUE}），
 * 而 {@code TextFieldWidget} 直接提供 {@code setNumbersOnly(long, long)}。
 *
 * <h2>🔴 输入的值怎么一路安全到达服务端（逐条字节码实证，不是推断）</h2>
 * <pre>
 *   ① 客户端打字 → {@code TextFieldWidget.onTextChanged(text)}
 *        → {@code textValidator.apply(text)}（= setNumbersOnly 那一份，见下）
 *        → 变了就 {@code writeClientAction(1, buf -> buf.writeUtf(v))}（C2S 包）；
 *   ② 服务端同 id widget 的 {@code handleClientAction(1, buf)}
 *        → {@code v = textValidator.apply(buf.readUtf())}   ← ★ 服务端【重跑一遍】校验器
 *        → {@code v = v.substring(0, min(len, maxStringLength))}
 *        → 变了才 {@code setCurrentString(v)} + {@code textResponder.accept(v)}   ← 我们写机器的入口
 *   ③ {@code setParallelOverride(...)} 里再钳一次（{@link ParallelOverrideMachine#clampOverride}）并
 *        {@code notifyBlockUpdate()} ⇒ {@code @DescSynced} 字段广播给所有观看者；
 *   ④ 回灌：{@code detectAndSendChanges()} 发现 {@code textSupplier.get() != currentString} 就
 *        {@code writeUpdateInfo(1, …)} 把服务端权威值推回客户端输入框。
 *      ⇒ <b>服务端钳过的值会自己显示到框里</b>，不会出现"框里是 999999、机器里是别的数"。
 * </pre>
 * {@code setNumbersOnly(min, max)} 的校验器语义（{@code lambda$setNumbersOnly$5} 字节码）：
 * <pre>
 *   null / 空串          → String.valueOf(min)         （⇒ 空框 = 自动，合法）
 *   解析失败（非数字）    → 返回 currentString          （⇒ 退回旧值，<b>绝不抛异常</b>）
 *   在 [min,max] 内       → 原样返回
 *   小于 min             → String.valueOf(min)
 *   大于 max             → String.valueOf(max)
 * </pre>
 * ⇒ 用户要求的「输入非法值（0 / 负数 / 超大 / 非数字）要有兜底」由<b>三层</b>共同保证：
 * 控件的校验器（客户端 + 服务端各一次）、{@link ParallelOverrideMachine#clampOverride}（写入口）、
 * 以及 {@link #parseOrDefault(String, long)} 的 {@code try/catch}（非数字永不上抛）。
 *
 * <h2>⚠️ 诚实边界</h2>
 * <ul>
 *   <li>本类<b>不保证</b>"填多少就跑多少"：两台机器的实际并行还要过输入量与输出空间两道闸
 *       （见 {@link ParallelOverrideMachine} 的 javadoc）。工具提示里写的就是"上限"，不写成"精确值"。</li>
 *   <li>{@code setActive} 之类的灰态是 build 期快照（LDLib 既有行为，见 {@code StarRenderConfigurator}
 *       的同款注释）；本面板只有一个按钮、没有需要变灰的状态，不受影响。</li>
 * </ul>
 */
public final class ParallelOverrideConfigurator implements IFancyConfigurator {

    /** 面板自身尺寸（弹出面板 = 本尺寸 + (8, 28)，见类注释）。 */
    private static final int PANEL_W = 150;
    private static final int PANEL_H = 78;

    /** 输入框与按钮的几何（同一行：左边输入框、右边「一键最大」）。 */
    private static final int ROW_Y = 34;
    private static final int FIELD_X = 4;
    private static final int FIELD_W = 86;
    private static final int ROW_H = 16;
    private static final int BUTTON_X = FIELD_X + FIELD_W + 4;   // = 94
    private static final int BUTTON_W = 52;                      // ⇒ 右边缘 146 < PANEL_W - 4

    /**
     * 标签图标：本工程自己的「寰宇并行核心」物品。
     *
     * <p>为什么不新做贴图：{@code IFancyUIProvider.getTabIcon()} / {@code IFancyConfigurator.getIcon()}
     * 只要求一个 {@code IGuiTexture}，而 {@code com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture}
     * 是本工程已在用的件（{@code PrimordialModuleMachine.ExtraMountPageProvider#getTabIcon} 就返回
     * {@code new ItemStackTexture(Items.HOPPER)}）⇒ 用物品图标既省一份资源、又与「并行」语义对得上。
     * <p>懒建一次并缓存：{@code ConfiguratorPanel$Tab.drawInBackground} <b>每帧</b>都会调 {@code getIcon()}
     * （字节码：{@code drawInBackground} 里 invokeinterface getIcon），每帧 new 一个纹理会白白造垃圾
     * —— 与 {@code StarRenderConfigurator} 里 {@code ICON_ACTIVE} 的处置同理。
     * <p>⚠️ {@code ItemStackTexture(Item...)} 的构造器<b>只把物品存进数组</b>（字节码核实：
     * 构造器里没有任何贴图/图集解析），真正的贴图在绘制时（客户端）才解析 ⇒ 即便本方法在某些路径上
     * 被服务端调到，也只是多分配一个 ItemStack，不会去碰客户端图集。
     */
    private static ItemStackTexture ICON;

    private final ParallelOverrideMachine machine;

    public ParallelOverrideConfigurator(ParallelOverrideMachine machine) {
        this.machine = machine;
    }

    @Override
    public Component getTitle() {
        return Component.literal("并行数");
    }

    @Override
    public IGuiTexture getIcon() {
        if (ICON == null) {
            // RegistryEntry#get()（ItemEntry<Item> 的超类）—— 只在 GUI 构建期取一次物品，
            // 那时注册早已完成。⚠️ 实测：ItemEntry 上【没有】asItem()（编译报"找不到符号"），
            // 所以这里必须走 get()，不能图省事用 TextFieldWidget 那套写法的同类 API。
            ICON = new ItemStackTexture(ShanhaiItems.UNIVERSAL_PARALLEL_CORE.get());
        }
        return ICON;
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(
                Component.literal("§b§l并行数"),
                Component.literal("§7面板里是一个输入框：填数字 = 该机器的并行上限"),
                Component.literal("§7右边「一键最大」= 填入这台机器§f此刻能达到§7的并行数"),
                Component.literal("§7填 §f0 §7= 恢复自动（跟随物质模块 / 机器自身）"),
                Component.literal("§8填得比能达到的数更大 ⇒ 自动钳到那个数"),
                Component.literal("§8实际跑多少仍由可用输入量与输出空间决定"));
    }

    @Override
    public Widget createConfigurator() {
        // 注意：本方法与 attachConfigurators 一样，在【服务端与客户端各跑一次】
        //（UIFactory.createUITemplate 的两个调用点：服务端直接建、客户端 readHolderFromSyncData 之后建）
        // ⇒ 这里只许建"两侧都安全"的件，不许碰 @OnlyIn(CLIENT) 的东西。
        WidgetGroup group = new WidgetGroup(0, 0, PANEL_W, PANEL_H);
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);

        group.addWidget(new LabelWidget(4, 3, "§b§l并行数上限（玩家可调）"));
        group.addWidget(new LabelWidget(4, 16, this::readoutText));

        // ── 输入框：值来自机器的覆盖字段；写入经 C2S 包到服务端后由 textResponder 落库 ──
        TextFieldWidget field = new TextFieldWidget(FIELD_X, ROW_Y, FIELD_W, ROW_H,
                () -> String.valueOf(machine.getParallelOverride()),
                text -> machine.setParallelOverride(
                        parseOrDefault(text, machine.getParallelOverride())));
        // 0 也允许：0 就是「自动」，所以下界是 0 而不是 1。
        field.setNumbersOnly(ParallelOverrideMachine.PARALLEL_AUTO, ParallelOverrideMachine.PARALLEL_MAX);
        // 19 位 = Long.MAX_VALUE 的十进制长度；再长也没有意义（服务端还会按 maxStringLength 截断）。
        field.setMaxStringLength(19);
        field.setBordered(true);
        // 🔴 2026-09-27：上面的 setNumbersOnly 只做【数值类型层】的边界（≥0 / ≤long 上限）。
        //    真正"不许超过这台机器能达到的并行数"那一条由服务端 setParallelOverride →
        //    clampOverrideToCeiling 完成 —— 它必须动态读天花板（模块的自动值每 3 tick 变），
        //    而 setNumbersOnly 的 (min,max) 是【建控件那一刻】的快照，装不下动态语义。
        //    钳过的权威值会经 @DescSynced + writeUpdateInfo(1, …) 回灌到框里（见类注释 ④）。
        field.setHoverTooltips(
                Component.literal("§7填入并行数上限（十进制整数）"),
                Component.literal("§7填 §f0 §7= 恢复自动（跟随物质模块 / 机器自身）"),
                Component.literal("§7超过这台机器能达到的数 ⇒ 自动钳到那个数"),
                Component.literal("§8非数字 / 空框会被退回上一个合法值，不会崩"));
        group.addWidget(field);

        // ── 「一键最大」：填这台机器【此刻能达到】的并行数（= 设 0 时机器能达到的那个数） ──
        ButtonWidget maxButton = new ButtonWidget(BUTTON_X, ROW_Y, BUTTON_W, ROW_H, GuiTextures.BUTTON,
                clickData -> machine.setParallelOverride(machine.getParallelOverrideCeiling()));
        maxButton.setHoverTooltips(
                Component.literal("§a一键最大"),
                Component.literal("§7把并行数填成这台机器此刻能达到的值：§f" + machine.getParallelOverrideCeiling()),
                Component.literal("§8= 填 0 时机器自己算出来的并行数"),
                Component.literal("§8实际仍受输入量与输出空间限制"));
        group.addWidget(maxButton);
        // 按钮上的文字：LDLib 的 ButtonWidget 没有 setText（javap 全量方法表里没有），
        // 本工程既有写法是"铺满按钮 + 上面盖一个 LabelWidget"（见 StarRenderConfigurator 第 4/6 行）。
        group.addWidget(new LabelWidget(BUTTON_X + 5, ROW_Y + 4, "§f一键最大"));

        // ⚠️ 这一行的渲染宽度要压在面板内（PANEL_W = 150，约 30 个全角/60 个半角字符上限）：
        //    「自动值」已经在上面那行实时读数里了，这里只说"超过上限会被钳到上限"，不重复数字。
        group.addWidget(new LabelWidget(4, 58, "§7输入 §f0 §7= 自动 · 超过上限会被钳到上限"));

        return group;
    }

    /**
     * 第 2 行的实时读数：覆盖值 / 生效值并排，玩家一眼能看出"我填的数到底生效没"。
     *
     * <p>🔴 2026-09-27：括号里那个"上限"显示的是 <b>{@code min(存下来的覆盖值, 此刻的天花板)}</b>，
     * 不是存下来的原值 —— 因为覆盖值是持久化的数、而天花板会变（模块的自动值每 3 tick 跟物质模块走）。
     * 若照抄原值，玩家拆掉物质模块后会看到「上限 4096」而机器只能跑 64，那是本工程红线点名的假数据。
     */
    private String readoutText() {
        final long override = machine.getParallelOverride();
        final long effective = machine.getEffectiveParallel();
        if (override <= ParallelOverrideMachine.PARALLEL_AUTO) {
            return "§7模式 §f自动§7 · 生效 §6" + formatParallel(effective);
        }
        final long shownCeiling = Math.min(override, machine.getParallelOverrideCeiling());
        return "§7模式 §e手动§7 · 生效 §6" + formatParallel(effective) + " §8（上限 " + shownCeiling + "）";
    }

    /**
     * 文本 → 覆盖值。<b>永不抛异常</b>。
     *
     * @param text     输入框送来的原文（可能是 null / 空串 / 非数字 —— 客户端与服务端那两道
     *                 {@code setNumbersOnly} 校验器理论上已经拦过，这里仍然兜底）
     * @param fallback 解析不出来时保留的原值（<b>不是</b> 0：报错的输入不该顺手把玩家的设置清掉）
     */
    static long parseOrDefault(String text, long fallback) {
        if (text == null) {
            return fallback;
        }
        final String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            // 空框 = 自动（与 setNumbersOnly 的 "空串 → String.valueOf(min)" 同口径，min = 0）。
            return ParallelOverrideMachine.PARALLEL_AUTO;
        }
        try {
            return ParallelOverrideMachine.clampOverride(Long.parseLong(trimmed));
        } catch (NumberFormatException e) {
            // 超长 / 带符号怪串 / 非数字 ⇒ 退回原值，绝不上抛（上抛会打断 GUI 的 client action 处理）。
            return fallback;
        }
    }

    /** 数值显示：自动档就是"自动"，其余按十进制原样（不做 K/M 缩写 —— 那是"看不清真值"的来源）。 */
    private static String formatParallel(long value) {
        if (value <= ParallelOverrideMachine.PARALLEL_AUTO) {
            return "自动";
        }
        return String.valueOf(value);
    }
}
