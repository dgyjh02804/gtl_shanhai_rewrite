package com.shanhai.machine.wildcard;

import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.misc.FluidTransferList;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import com.lowdragmc.lowdraglib.side.item.IItemTransfer;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.gtlcore.gtlcore.api.gui.MEPatternCatalystUIManager;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * <b>右侧那一块</b>（用户图1）：照上游 {@code MESuperPatternBufferPartMachine} 的做法，
 * 直接 {@code new MEPatternCatalystUIManager(...)} 挂在主界面同一个 {@code WidgetGroup} 上
 * （{@code waitToAdded}，x = 组宽 + 4），里面自带三段：
 * <ol>
 *   <li>「实际配方数量」（{@code gui.gtlcore.pattern_buffer_gui_1} ＋ 数字输入）；</li>
 *   <li>「物品催化剂槽」3×3（{@code gui.gtlcore.pattern_buffer_gui_2}）；</li>
 *   <li>「流体催化剂槽」3×3（{@code gui.gtlcore.pattern_buffer_gui_3}）。</li>
 * </ol>
 *
 * <h2>🔴 本类只多做一件事：在【流体催化剂槽】下面再接一段「电路设置」</h2>
 * 用户 2026-10-01 原话：「新增一个电路设置的框，如图2，图3 中这个按钮你就加载图1 这个面板里，
 * 就用这个决定当前格子的编程电路」。
 *
 * <p><b>那一段不是我们自己画的</b> —— 它是 GTCEu 自带的
 * {@link CircuitFancyConfigurator#createConfigurator()}，原样搬过来：
 * <pre>
 *   javap -p -c -cp libs\gtceu-1.20.1-1.4.4.jar \
 *         com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator
 *   createConfigurator() 偏移 0–12  : WidgetGroup(0, 0, 174, 132)
 *   偏移  16– 33                    : LabelWidget(9, 8, "Programmed Circuit Configuration")
 *   偏移  34–131                    : 编程电路槽 SlotWidget(circuitSlot, 0, (174-18)/2=78, 20)
 *   偏移 183–299                    : 双重循环 row = 0..2 × col = 0..8
 *                                     ⇒ 27 个按钮，电路 0..26，位置 (5 + col*18, 48 + row*18)
 *   偏移 302–396                    : 单重循环 i = 0..5
 *                                     ⇒ 6 个按钮，电路 27..32，位置 (5 + i*18, 102)
 *   ⇒ 合计 33 个按钮 = 电路 0..32，9 列铺开，按钮贴图 = SLOT ＋ ItemStackTexture(IntCircuitBehaviour.stack(n))
 *   ⚠️ ghostCircuit 为真时【另有一个】"清除"按钮，位置与那个槽【重合】= ((174-18)/2, 20) = (78, 20)，
 *      贴图 IGuiTexture.EMPTY（偏移 144–176）。
 * </pre>
 * 这正是用户图2 里那张「0~32 的按钮网格（9 列，33 个）」，所以<b>直接复用，不重画</b>。
 *
 * <h2>它怎么决定「当前格子」</h2>
 * {@code CircuitFancyConfigurator} 的入参是一个 {@link ItemStackTransfer}（装编程电路物品的槽）。
 * 我们给它一个 <b>1 格的代理容器</b>（{@link SlotCircuitTransfer}）：
 * <ul>
 *   <li>读 ⇒ 现取 {@code machine.shanhaiSlotCircuit(currentSlot)}，转成对应编号的编程电路物品；</li>
 *   <li>写 ⇒ 把物品上的编号交回 {@code machine.shanhaiApplySlotCircuit(currentSlot, n)}。</li>
 * </ul>
 * ⇒ 「当前格子」= 最后一次中键点的那一格（与「实际配方数量」同一套 per-slot 语义）。
 *
 * <h2>🔴🔴 2026-10-01 二次修复：33 键「点了没反应」的真正机制</h2>
 *
 * <h3>先说结论：先前那三条推论是【错的】，本文档把字节码摆出来</h3>
 * 用户实测「点 33 个按钮没反应」，实机日志里有这一行：
 * <pre>
 * [Render thread/INFO] [com.shanhai.machine.wildcard.SuperWildcardPatternBufferPartMachine/]:
 * [SHANHAI-WILDCARD] 设电路被拒（早期返回）：格=2 值=0 原因=这是客户端侧（isRemote）
 * </pre>
 * 先前据此推出「① 键发的请求只在客户端跑了一次 ⇒ ② 没有『发给服务端』那一步」。
 * <b>两条都与字节码不符</b>：
 * <ol>
 *   <li><b>{@code CircuitFancyConfigurator} 的按钮自己在客户端是【空操作】</b>。
 *       它的回调（{@code javap -p -c} 的 {@code lambda$createConfigurator$1/2} 原文）第一句就是：
 *       <pre>
 *       0: aload_2 ; 1: getfield ClickData.isRemote:Z ; 4: ifne 75      ← if (clickData.isRemote) return;
 *       7: circuitSlot.getStackInSlot(0) ; 19: isIntegratedCircuit(stack)
 *       …
 *       67: circuitSlot.onContentsChanged(0) ; 75: return
 *       </pre>
 *       而 {@code ClickData()} 无参构造器（偏移 107–109）是 {@code putfield isRemote = 1}、
 *       {@code readFromBuf}（偏移 22–23）传的是 {@code iconst_0 ⇒ false}
 *       ⇒ <b>客户端那次 isRemote=true ⇒ 第 4 条指令就 return，它在客户端【一个字节都不写】</b>。
 *       ⇒ 那行 {@code 值=0 原因=这是客户端侧（isRemote)} <b>不可能是这 33 个按钮点出来的</b>。</li>
 *   <li><b>「发给服务端」那一步在 {@code ButtonWidget} 内部，不是可选步骤</b>。
 *       {@code javap -p -c ButtonWidget.mouseClicked} 偏移 18–32：
 *       <pre>
 *       9: new ClickData
 *       18: aload_0 ; 19: iconst_1 ; 20: aload 6(clickData)
 *       27: invokedynamic #0:accept:(ClickData)Consumer
 *       32: invokevirtual Widget.writeClientAction:(ILjava/util/function/Consumer;)V     ← 🔴 发！
 *       35–48: onPressCallback.accept(clickData)                                        ← 本地也跑一次
 *       </pre>
 *       服务端 {@code handleClientAction}（偏移 6–33）走 {@code ClickData.readFromBuf}（isRemote=false）
 *       再跑同一个回调 ⇒ <b>「发一份 ＋ 本地也跑一份」本来就是 LDLib 内建的</b>，
 *       不需要目标对象做任何事。本工程既有实证：{@code StarRenderConfigurator} 类注释第 69–100 行
 *       记的就是这条链（那边的按钮能用）。</li>
 * </ol>
 *
 * <h3>那行客户端日志的真正来源（这一段是【机制推断】，证据强度见下）</h3>
 * {@code javap -p -c SlotWidget$WidgetSlotItemTransfer} 偏移 82–92：
 * <pre>
 * public void m_5852_(ItemStack stack) {          // = Slot.set
 *     this.itemHandler.setStackInSlot(this.index, stack);   ← 🔴 直插我们的 SlotCircuitTransfer
 *     this.m_6654_();                                       // onContentsChanged
 * }
 * </pre>
 * 而 {@code SlotWidget.createSlot(IItemTransfer,int)}（偏移 182–192）用的就是这个内部类，
 * {@code SlotWidget.updateSlot} 在 {@code !isClientSideWidget} 时把它登记进
 * {@code ModularUI.addNativeSlot} ⇒ <b>它进了原版容器</b>，容器做「客户端一份 / 服务端一份」
 * 的槽同步时会在【客户端】调 {@code set(EMPTY)} ⇒ 打到我们的代理槽 ⇒ 在 Render thread 上
 * 调 {@code shanhaiApplySlotCircuit(格, 0)} ⇒ 被 {@code isRemote()} 早退 ⇒ 打出那一行。
 * ⇒ <b>那行是「打开面板时的容器槽同步回声」，不是「按钮只跑了客户端」的证据。</b>
 * （⚠️ 本段是推断，不是实测；但它同时解释了日志里的 {@code 值=0} 与 {@code 格=2}
 * —— 值 0 正是 {@code ItemStack.EMPTY} 折出来的 {@code ShanhaiSlotCircuit.NONE}，
 * 格 2 正是用户中键选中的那一格，与「面板刚挂上、槽被同步一次」完全吻合。）
 *
 * <h3>本轮改法（两条都照用户/主 agent 的口径：「发一份给服务端 ＋ 本地也跑一份」）</h3>
 * 既然按钮自己会发，为什么还动它？因为它的发送走的是
 * {@code WidgetGroupUIAccess.writeClientAction} 那条【按控件下标逐级解析】的长链：
 * <pre>
 *   WidgetGroupUIAccess.writeClientAction 偏移 0–14:
 *       0: this$0 ; 4: iconst_1 ; 9: invokedynamic  → 调 WidgetGroup.writeClientAction(1, wrapped)
 *   lambda$writeClientAction$0:
 *       buf.writeInt(this$0.widgets.indexOf(widget))   ← 🔴 下标是【点击那一刻】现算的
 *       buf.writeInt(id) ; consumer.accept(buf)
 * </pre>
 * ⇒ 本机这条链是 <b>主界面组 → 本面板 → 电路段 → 按钮</b> 三级，而<b>电路段是本类在
 * 中键那一刻【现场 new / 现场 addWidget】的</b>（见 {@link #shanhaiAttachCircuitSection}）。
 * 任何一侧的这三级下标只要差一格，包就会被派发到别的控件上，而
 * {@code WidgetGroup.handleClientAction}（偏移 0–12：{@code if (id != 1) return;}）
 * <b>对不上就静默丢弃、一条日志都不留</b> —— 这正是「点了没反应」的形态。
 *
 * <p>本轮把「发」这一步<b>上提一级</b>，交给<b>本面板自己</b>发（它是主界面组的常驻子节点）：
 * <ul>
 *   <li>33 个按钮（＋ ghostCircuit 那个「清除」键）的点击回调被
 *       {@link ButtonWidget#setOnPressCallback} <b>重绑</b>（该 setter 由
 *       {@code javap -p ButtonWidget} 核实存在）；
 *   <li>点击时【客户端】只做一件事：{@link #shanhaiSendSlotCircuit} ⇒
 *       {@code this.writeClientAction(ACTION_SET_SLOT_CIRCUIT, …)}，
 *       路由链缩短到 <b>主界面组 → 本面板</b>（两级，且本面板不随电路段增删）；
 *   <li>【服务端】由本类覆写的 {@link #handleClientAction} 收到
 *       （动作号 {@value #ACTION_SET_SLOT_CIRCUIT}），才调
 *       {@code machine.shanhaiApplySlotCircuit(格, 值)} 落库；
 *   <li>⚠️ {@code handleClientAction} 里对不认识的 id <b>必须 {@code super}</b> 转发 ——
 *       {@code WidgetGroup.handleClientAction} 的 {@code id == 1} 那支就是子控件下标分发，
 *       不转发会把整个面板子树的动作全掐掉。
 * </ul>
 * <p>⚠️ {@code 值 = 0}（= {@link ShanhaiSlotCircuit#NONE}）照旧走
 * {@code shanhaiApplySlotCircuit} ⇒ {@code normalize(true, 0) = 0} ⇒
 * {@code applySlotCircuits()} 里 {@code Action.CLEAR} ⇒ {@code clearCircuitCache()}。
 * <b>既有修法一个字没动</b>。
 *
 * <h3>判据（两行日志把三种可能分开）</h3>
 * <pre>
 *   点击 → [Render thread] 「电路按钮点击（客户端）：格=N 电路=M ⇒ 已发往服务端」
 *   收到 → [Server thread] 「电路按钮请求【已到达服务端】：格=N 电路=M」   ← ✅ 这一行【必须】出现
 * </pre>
 * ① 只有第一行 ⇒ 发没成功（{@code Widget.writeClientAction} 的两道静默闸门之一：
 * {@code uiAccess == null} 或 {@code isClientSideWidget == true}，见该类字节码偏移 0–26）；
 * ② 两行都有但电路没变 ⇒ 是机器侧早退（会有机器自己的日志说明原因）；
 * ③ 第一行都没有 ⇒ 点击根本没送到按钮（可见性/命中框问题）。
 * <p>⚠️ <b>「日志里不该再出现『原因=这是客户端侧（isRemote）』」这一条判据在本机上【不成立】</b>：
 * 那句话来自上面第 2 节定位的容器槽同步回声（GTCEu 那段自带 {@code SlotWidget}），
 * 只要面板一挂出来就会打一次。请改用上面这【两行配对】做判据。
 *
 * <h2>🔴🔴🔴 2026-10-01 第三次修复：点击【根本到不了按钮】—— 被面板自带的「拖拽闸门」吃掉</h2>
 *
 * <h3>用户实测的关键现象（这一句把范围钉死了）</h3>
 * 用户原话：「我按那个按钮的时候好像就是在按一个<b>不能交互的贴图</b>，一点反应都没有」。
 * ⇒ 连<b>按下的视觉/音效反馈</b>都没有 ⇒ 事件压根没落到按钮上，<b>不是「回调没被调用」</b>。
 * （与日志吻合：接线那行在（两侧各一次），而 {@code 电路按钮点击（客户端）} 命中 <b>0</b> 条。）
 *
 * <h3>根因：{@code MEPatternCatalystUIManager} 自己覆写了 {@code mouseClicked}，命中就【直接吞掉】</h3>
 * <pre>
 * javap -p -c -cp shanhai-rewrite\libs\gtlcore-1.2.3.2.jar \
 *       org.gtlcore.gtlcore.api.gui.MEPatternCatalystUIManager
 *
 * public boolean mouseClicked(double, double, int);            // 偏移 0–77
 *    0: dconst_0  → putfield lastDeltaX ； dconst_0 → putfield lastDeltaY ； iconst_0 → putfield isDragging
 *   15: getPositionX/getPositionY/getSizeWidth/getSizeHeight
 *   34: invokevirtual isMouseover:(IIIIDD)Z
 *   37: ifeq 47                       ← 闸门【命中】时【不跳】
 *   40: iconst_1 → putfield isDragging  ← 🔴 把这次点击当成「拖面板的手柄」
 *   45: iconst_1 ; 46: ireturn          ← 🔴🔴 直接 true 返回：super.mouseClicked 一次都不调
 *   47: invokespecial WidgetGroup.mouseClicked:(DDI)Z   ← 只有【没命中】才正常派发给子控件
 *   55: ifne 67 ； 58: isMouseOverElement ； 64: ifeq 71 ； 67: iconst_1 ； 71: iconst_0 ； 72: ireturn
 * </pre>
 * 而那个 {@code isMouseover(int,int,int,int,double,double)}（偏移 0–241）的语义是：
 * <pre>
 *   点【在面板自己矩形内】 且 点【不在任何「孙级 WidgetGroup」里】 ⇒ true（= 闸门命中 ⇒ 吃掉）
 *   （偏移 49–237：先遍历本面板的 children（要求 instanceof WidgetGroup），
 *     再遍历那个组的 children（同样要求 instanceof WidgetGroup）；
 *     一旦点落在某个孙级组的矩形里 ⇒ 偏移 223 判 false 分支 ⇒ goto 237 ⇒ 返回 false）
 * </pre>
 *
 * <h3>为什么正好是「同面板里催化剂槽能点、电路按钮不能点」</h3>
 * 两条路只差<b>一层嵌套</b>：
 * <pre>
 * 催化剂槽（能用 ✅）：面板 → createInventoryContainer 的外层组 → 🔴内层组(8,12,cols*18,rows*18) → SlotWidget
 *                     （javap 偏移 45–58 new 外层组、81–96 new 内层组、196–203 把内层组 addWidget 进外层组）
 *                     ⇒ 命中的是【孙级 WidgetGroup】⇒ isMouseover = false ⇒ 走 super 正常派发
 * 电路按钮（点不着 ❌）：面板 → 电路段(那个 WidgetGroup) → 🔴33 个按钮【就是它的直接子节点】
 *                     （javap CircuitFancyConfigurator.createConfigurator 偏移 205–286 / 315–390
 *                       全部 invokevirtual WidgetGroup.addWidget —— 扁平一层，中间没有别的 WidgetGroup；
 *                       同法：偏移 144–182 的「清除」键也是直接 addWidget）
 *                     ⇒ 没有任何孙级 WidgetGroup 兜住 ⇒ isMouseover = true ⇒ 点击被面板吞掉
 * </pre>
 * ⇒ <b>这就是「同一个面板里，槽能点、按钮点不着」的唯一差别</b>；
 * 而且吞掉的那一下会把 {@code isDragging} 置真 ⇒ 按住挪一下鼠标，<b>整块右侧面板会跟着跑</b>
 * （{@code mouseDragged} 偏移 48–89 ⇒ {@code addSelfPosition}），这也是可现场复现的旁证。
 *
 * <h3>本轮排掉的另外三条嫌疑（都附了判据，别再回头查）</h3>
 * <ol>
 *   <li><b>回调被覆盖？不是。</b>{@code CircuitFancyConfigurator.createConfigurator} 里那个 consumer 只经
 *       {@code ButtonWidget."<init>"(IIIILIGuiTexture;Ljava/util/function/Consumer;)V} 写一次
 *       {@code onPressCallback}（偏移 9–12），而我们的重绑在 {@code createConfigurator()} <b>返回之后</b>
 *       ⇒ 我们是最后写入者。本轮另加<b>运行时回读探针</b>（反射读该字段比引用）来钉死这一点。</li>
 *   <li><b>{@code isClientSideWidget} 传染？与点击无关。</b>它只出现在
 *       {@code Widget.writeClientAction} 的闸门里（偏移 0–26：{@code uiAccess == null} 或
 *       {@code isClientSideWidget == true} ⇒ 不发包）——<b>它管的是「发不发」，不是「收不收」</b>。
 *       若是它，客户端回调仍会跑（会打出那行（客户端）），而实测是 0 条 ⇒ <b>排除</b>。</li>
 *   <li><b>{@code isActive == false} / 被兄弟控件盖住？都不是。</b>{@code Widget."<init>"} 偏移 90–111：
 *       {@code isVisible = true; isActive = true}（都是 iconst_1），我们从未设 false；
 *       {@code WidgetGroup.addWidget} 偏移 57–68 只传染 {@code isClientSideWidget}，<b>不传染</b> visible/active。
 *       而 {@code WidgetGroup.mouseClicked} 偏移 0–70 是<b>倒序</b>遍历（{@code size-1 → 0}），
 *       本段是本面板<b>最后</b>加入的子节点、本面板又是主界面组<b>最后</b>加入的子节点
 *       ⇒ 任何兄弟都不可能挡在它前面。⇒ <b>排除</b>。</li>
 * </ol>
 * ⚠️ 顺带记一条与体感有关的事实：这 33 个按钮走的是
 * {@code ButtonWidget(int,int,int,int,IGuiTexture,Consumer)}（偏移 41–61），
 * 那个构造器<b>不调 {@code initTemplate()}</b>、也没有 {@code setHoverTexture}
 * ⇒ 它们本来<b>就没有悬停高亮</b>，看上去更像「死贴图」。本轮按
 * {@link #SHANHAI_CIRCUIT_HOVER_CUE} 用上游自带的 {@code initTemplate()} 补一个 1px 白边悬停提示（可一键关掉）。
 *
 * <h3>本轮改法（一个字没动上游那个组件）</h3>
 * 本类覆写 {@link #mouseClicked}：<b>先把点击交给电路段</b>，段没接（返回 false）才走
 * {@code super}（面板原来的拖拽/派发语义原样保留）。布局、贴图、槽、33 键的落点全部不变。
 *
 * <h3>本轮四条探针（下次日志一眼三分）</h3>
 * <pre>
 *   ① 打开面板时：[SHANHAI-WILDCARD] 电路按钮回调自检：设了 N 个；回读仍是我们设的 M 个；已被别人覆盖 K 个
 *   ② 按钮被按下：[SHANHAI-WILDCARD] 电路按钮【被按下】：格=N 电路=M 侧=… 线程=…   ← 打在回调最开头，无早退
 *   ③ 服务端收到：[SHANHAI-WILDCARD] 电路按钮请求【已到达服务端】：格=N 电路=M
 *   ④ 打开面板时：[SHANHAI-WILDCARD] 电路按钮状态自检：…（类/可见/可交互/clientSide/被遮盖/会被闸门吃掉）
 * 判据：
 *   · ②③ 都没有            ⇒ 点击压根没到按钮（闸门/遮盖/不可见）
 *   · ②没有、④说「被闸门吃掉」 ⇒ 闸门吃掉了（= 本轮之前的状态）
 *   · ②有、「已发往服务端」也有、③没有 ⇒ 真的送不出去
 *   · ②③都有但电路没变      ⇒ 机器侧在拒（会另有「原因=…」一行）
 * </pre>
 * ⚠️ 点一次<b>可能看到 4 行</b>（不会更多）：按钮自带的 LDLib 通道
 * （{@code ButtonWidget.mouseClicked} 偏移 32 的 {@code writeClientAction(1, …)}）也会
 * 走 {@code 主界面组→本面板→电路段→按钮} 把同一次点击送到服务端并再跑一次回调
 * ⇒ 服务端会各收到「我们的 21322」与「按钮自带的 id=1」两份，第二次落库会被
 * {@code 值没变（当前就是 …）} 早退。<b>那是幂等回声，不是错误。</b>
 */
public class ShanhaiCatalystPanel extends MEPatternCatalystUIManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** GTCEu 那张电路网格面板的原始尺寸（上面 javap 偏移 0–12）。 */
    private static final int CIRCUIT_PANEL_WIDTH = 174;
    private static final int CIRCUIT_PANEL_HEIGHT = 132;

    /** 电路段与上面催化剂段之间的间隔。 */
    private static final int SECTION_GAP = 4;

    /**
     * 🔴 「点电路按钮」的客户端动作号（2026-10-01 本轮新增）。
     *
     * <p>取值 {@code 21322}：与 {@code ShanhaiCircuitSlotWidget} 里既有的
     * {@code ACTION_SET_CIRCUIT = 21320} / {@code ACTION_TOGGLE_CATALYST = 21321} 相邻但不相同。
     * ⚠️ 两侧共用本常量；且**绝不能取 1** —— {@code WidgetGroup.handleClientAction} 把
     * {@code id == 1} 当成「子控件下标分发」，取 1 会把我们的包体当成
     * （下标, 子id）去解析。
     */
    public static final int ACTION_SET_SLOT_CIRCUIT = 21322;

    /**
     * 给那 33 个按钮补一个「悬停高亮」（上游 {@code ButtonWidget.initTemplate()} 自带的 1px 白边）。
     *
     * <p>⚠️ 它们本来是<b>没有</b>悬停高亮的：{@code CircuitFancyConfigurator} 用的是
     * {@code ButtonWidget(int,int,int,int,IGuiTexture,Consumer)} 那个构造器，它不调 {@code initTemplate()}。
     * 用户实测原话是「好像在按一个不能交互的贴图」⇒ 补上这条高亮，让「按钮是活的」在视觉上直接可见。
     *
     * <p>想关掉：把这个常量改成 {@code false}（就一行，没有别的牵连）。
     */
    private static final boolean SHANHAI_CIRCUIT_HOVER_CUE = true;

    private final SuperWildcardPatternBufferPartMachine machine;

    /** 代理容器：读/写都落到「当前那一格」的 {@code slotCircuit} 上。 */
    private final ItemStackTransfer circuitSlot = new SlotCircuitTransfer();

    /** 当前被中键选中的通配符样板格（{@code -1} = 还没选过）。 */
    private int currentSlot = -1;

    /** 当前挂在下面的电路段（换格时整个换掉，避免残留旧回调）。 */
    private Widget circuitSection;

    /**
     * 「没有电路段」时面板的基准尺寸（= 上游 {@code show(...)} 末尾 {@code setSize} 算出来的那个）。
     *
     * <p>⚠️ 必须记下来：{@code setSize} 是<b>累加</b>的写法，而「同一格再按一次 = 收起、再按一次 = 展开」
     * 这条路上 {@code super.toggleFor} <b>不会再调 {@code show()}</b>
     * （{@code MEPatternCatalystUIManager.toggleFor} 偏移 0–40：格号相同就只翻 {@code setVisible/setActive} 后返回），
     * 于是上一轮挂的电路段还留在子节点里。不先撤干净就再挂一次 ⇒ 33 个按钮来两份、面板高度每次都涨。
     */
    private int baseWidth = -1;
    private int baseHeight = -1;

    public ShanhaiCatalystPanel(int x,
                                IItemTransfer[] itemTransfers,
                                FluidTransferList[] fluidTankTransfers,
                                byte[] cacheRecipeCount,
                                IntConsumer onCacheCountChange,
                                SuperWildcardPatternBufferPartMachine machine) {
        super(x, itemTransfers, fluidTankTransfers, cacheRecipeCount, onCacheCountChange);
        this.machine = machine;
    }

    /**
     * 中键的落点。照上游语义：同一格再按一次 = 收起；换一格 = 重开并换成那一格的内容。
     *
     * <p>⚠️ 必须在 {@code super.toggleFor} <b>之后</b>再决定要不要补电路段：
     * {@code show(...)} 内部第一句是 {@code clearAllWidgets()}，会把上一次挂的电路段一并清掉。
     *
     * <p>🔴 <b>本方法会在客户端和服务端各跑一次</b>（中键走
     * {@code ShanhaiCircuitSlotWidget#mouseClicked} 的 {@code writeClientAction}
     * ＋ {@code handleClientAction}）。两侧各持一份自己的界面状态，所以两侧都要把电路段挂出来 ——
     * 只在一侧挂 ⇒ 那一侧多出来的 {@code SlotWidget} 在原版容器里没有对应槽位。
     *
     * <p>⚠️ 本轮【没有动这个方法】。
     */
    @Override
    public void toggleFor(int index) {
        // 「换了格」⇔ super 这次一定会走 show()（toggleFor 偏移 0–5 判的就是 lastIndex != index，
        // 而 lastIndex 初值 -1 与本类 currentSlot 初值一致、且只在 show() 末尾更新 ⇒ 两者同步）。
        final boolean switchedSlot = (index != this.currentSlot);
        this.currentSlot = index;
        super.toggleFor(index);
        if (!this.isVisible()) {
            return;
        }
        if (switchedSlot || this.baseHeight < 0) {
            // super 刚跑完 show() ⇒ 此刻的尺寸就是「还没有电路段」的基准尺寸
            this.baseWidth = this.getSizeWidth();
            this.baseHeight = this.getSizeHeight();
        }
        this.shanhaiAttachCircuitSection();
    }

    /** 当前被选中的格号（{@code -1} = 没选过）。 */
    public int shanhaiCurrentSlot() {
        return this.currentSlot;
    }

    /**
     * 🔴🔴🔴 2026-10-01 第三次修复：<b>把点击【先】交给电路段</b>。
     *
     * <p>上一版「点了没反应」的根因就在这里：父类 {@code MEPatternCatalystUIManager} 覆写的
     * {@code mouseClicked}（javap 偏移 0–77）会先跑它自己的「拖拽闸门」
     * （{@code isMouseover(...)}）—— 只要点落在面板矩形内<b>且不在任何「孙级 WidgetGroup」里</b>，
     * 它就 {@code isDragging = true; return true}，<b>一次都不调 {@code super.mouseClicked}</b>
     * ⇒ 所有子控件都收不到这次点击。
     *
     * <p>而电路段是 {@code 面板 → 电路段 → 33 个按钮} 的<b>扁平</b>结构（按钮就是段的直接子节点），
     * 没有任何孙级 WidgetGroup ⇒ 闸门永远命中把点击吞掉。详见类注释。
     *
     * <p>⚠️ 这里只做一件事：段可见/可交互时，先问段要不要这次点击；段不要（返回 false）就原样走
     * {@code super}（父类那套「点到空白处 = 拖面板」的语义<b>一个字没改</b>）。
     * ⚠️ 与拖拽语义的差别（有意为之）：点在电路段上的那一下<b>不再</b>把 {@code isDragging} 置真
     * ⇒ 按住电路按钮挪鼠标不会再拖着整块面板跑 —— 这正是我们要的。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        final Widget section = this.circuitSection;
        if (section != null && section.isVisible() && section.isActive()
                && section.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------ 电路按钮 → 服务端（本轮新增）

    /**
     * 服务端收到「客户端点了第 N 格的第 M 号电路按钮」。
     *
     * <p>🔴 这就是本轮把「发」上提到本面板之后的新入口。动作号 {@value #ACTION_SET_SLOT_CIRCUIT}。
     *
     * <p>⚠️ 对不认识的 id <b>必须 {@code super.handleClientAction}</b>：
     * {@code WidgetGroup.handleClientAction}（javap 偏移 0–12）里 {@code id == 1} 那一支
     * 就是「读下标 → 转发给对应子控件」，是本面板下面三段催化剂控件的动作通道；
     * 不转发会把它们全掐掉。
     */
    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == ACTION_SET_SLOT_CIRCUIT) {
            final int slot = buffer.readInt();
            final int circuit = buffer.readInt();
            LOGGER.info("[SHANHAI-WILDCARD] 电路按钮请求【已到达服务端】：格={} 电路={} ⇒ 交给机器落库",
                    slot + 1, circuit);
            if (this.machine != null) {
                this.machine.shanhaiApplySlotCircuit(slot, circuit);
            }
            return;
        }
        super.handleClientAction(id, buffer);
    }

    /**
     * 客户端点击某个电路按钮。
     *
     * <p>🔴 只做两件事：①（客户端）显式发一份给服务端；②（万一回调在服务端跑）直接落库。
     * <b>客户端【不再】调机器的 {@code shanhaiApplySlotCircuit}</b> —— 那份一定会被
     * {@code isRemote()} 早退，只会往日志里灌一行「原因=这是客户端侧（isRemote）」，
     * 让人误以为「只在客户端跑了」。
     */
    private void shanhaiOnCircuitButton(int circuit) {
        final SuperWildcardPatternBufferPartMachine owner = this.machine;
        final int slot = this.currentSlot;
        if (owner == null || slot < 0) {
            LOGGER.info("[SHANHAI-WILDCARD] 电路按钮点击被忽略：{}",
                    owner == null ? "面板没绑机器" : "还没选中任何格子（先中键点一个样板格）");
            return;
        }
        if (owner.isRemote()) {
            this.shanhaiSendSlotCircuit(slot, circuit);
        } else {
            owner.shanhaiApplySlotCircuit(slot, circuit);
        }
    }

    /**
     * 把「第 {@code slot} 格 = 电路 {@code circuit}」发到服务端。
     *
     * <p>⚠️ 这里刻意用 {@code this}（本面板）而不是按钮自己发：
     * {@code WidgetGroupUIAccess.writeClientAction} 会把
     * {@code parent.widgets.indexOf(widget)} 写进包体（javap 原文），
     * 而本面板是主界面组的常驻子节点 —— 路由链只有两级，
     * 不像按钮那样要穿过【现场挂上/摘下的】电路段。
     */
    private void shanhaiSendSlotCircuit(int slot, int circuit) {
        LOGGER.info("[SHANHAI-WILDCARD] 电路按钮点击（客户端）：格={} 电路={} ⇒ 已发往服务端",
                slot + 1, circuit);
        this.writeClientAction(ACTION_SET_SLOT_CIRCUIT, buf -> {
            buf.writeInt(slot);
            buf.writeInt(circuit);
        });
    }

    /**
     * 把 GTCEu 那 33 个按钮（＋ ghostCircuit 的「清除」键）的点击回调重绑到本面板的通道上。
     *
     * <p>🔴 <b>不是重画那个组件</b>：布局、贴图、标签、编程电路槽全部还是
     * {@link CircuitFancyConfigurator#createConfigurator()} 原样造出来的，
     * 我们只用 {@code ButtonWidget} 的公开 setter {@code setOnPressCallback}
     * （{@code javap -p ButtonWidget} 核实存在）把回调换掉。
     *
     * <p>识别按键靠<b>坐标</b>而不是靠顺序 —— 顺序会被 ghostCircuit 那个「清除」键推歪，
     * 而坐标是类注释里那串 javap 偏移算出来的死数。
     *
     * <p>🔴 2026-10-01 第三次修复：重绑完立刻跑<b>两条自检探针</b>
     * （{@link #shanhaiProbeCallbackOwnership} / {@link #shanhaiProbeButtonLiveness}），
     * 让「打开面板」这一步就能把「回调是不是我们的」「按钮是不是活的」写进日志，
     * <b>不必再让用户点一次来采数据</b>。
     */
    private void shanhaiRebindCircuitButtons(WidgetGroup section) {
        final List<ButtonWidget> buttons = section.getWidgetsByType(ButtonWidget.class);
        final List<ButtonWidget> boundButtons = new ArrayList<>(buttons.size());
        final List<Consumer<ClickData>> installed = new ArrayList<>(buttons.size());
        for (final ButtonWidget button : buttons) {
            final int circuit = shanhaiCircuitOfButton(button);
            if (circuit < 0) {
                continue;
            }
            final Consumer<ClickData> callback = clickData -> {
                // 🔴 探针②：这一行必须打在【任何早退之前】——
                //    它就是「按钮到底有没有被按到」的唯一判据（②③ 都没有 = 压根没点着）。
                LOGGER.info("[SHANHAI-WILDCARD] 电路按钮【被按下】：格={} 电路={} 侧={} 线程={}",
                        ShanhaiCatalystPanel.this.currentSlot + 1, circuit,
                        ShanhaiCatalystPanel.this.shanhaiSideName(),
                        Thread.currentThread().getName());
                ShanhaiCatalystPanel.this.shanhaiOnCircuitButton(circuit);
            };
            button.setOnPressCallback(callback);
            if (SHANHAI_CIRCUIT_HOVER_CUE) {
                // 上游自带的悬停提示（1px 白边）。⚠️ 不调它的话这 33 个按钮【没有任何悬停高亮】，
                // 用户看上去就是「一张不能交互的贴图」。见常量注释。
                button.initTemplate();
            }
            boundButtons.add(button);
            installed.add(callback);
        }
        LOGGER.info("[SHANHAI-WILDCARD] 电路按钮已接线：共 {} 个（应为 {}；ghostCircuit 开启时另有 1 个清除键）",
                boundButtons.size(), ShanhaiCircuitGrid.CIRCUIT_BUTTON_COUNT);
        // 探针①：设完之后【回读】字段，看有没有被别人又设回去（覆盖）。
        this.shanhaiProbeCallbackOwnership(boundButtons, installed);
        // 探针④：这批按钮此刻的状态（类/可见/可交互/clientSide/被遮盖/会不会被面板闸门吃掉）。
        this.shanhaiProbeButtonLiveness(section);
    }

    /** 当前这一侧的名字（只用于日志：客户端/服务端）。 */
    private String shanhaiSideName() {
        final SuperWildcardPatternBufferPartMachine owner = this.machine;
        if (owner == null) {
            return "未知（面板没绑机器）";
        }
        return owner.isRemote() ? "客户端（本地面板）" : "服务端";
    }

    /**
     * <b>探针①</b>：回调设完<b>回读一次</b>，看还是不是我们设的那一个。
     *
     * <p>做法：{@code ButtonWidget.onPressCallback} 是 {@code protected} 字段（javap 原文一行：
     * {@code protected java.util.function.Consumer<ClickData> onPressCallback;}），
     * 本类不在同一包也不是它的子类 ⇒ 只能反射读。<b>只读不写</b>。
     *
     * <p>判据（下次日志直接三分）：{@code 仍是我们设的 = 设了几次} ⇒ 没被覆盖；
     * 出现「已被别人覆盖 N 个」⇒ 有后来者又把回调换掉了。
     * 读不到（反射失败）⇒ 打 WARN 并明确写「本行只说明探测失败，不代表回调被覆盖」，<b>不许当成「被覆盖」</b>。
     */
    private void shanhaiProbeCallbackOwnership(List<ButtonWidget> buttons, List<Consumer<ClickData>> installed) {
        int stillOurs = 0;
        int overwritten = 0;
        boolean readFailed = false;
        try {
            final Field field = ButtonWidget.class.getDeclaredField("onPressCallback");
            field.setAccessible(true);
            for (int i = 0; i < buttons.size(); i++) {
                if (field.get(buttons.get(i)) == installed.get(i)) {
                    stillOurs++;
                } else {
                    overwritten++;
                }
            }
        } catch (Throwable t) {
            readFailed = true;
            LOGGER.warn("[SHANHAI-WILDCARD] 电路按钮回调自检【读取失败】：{}"
                    + "（⇒ 本行只说明探测失败，【不代表】回调被覆盖）", t.toString());
        }
        LOGGER.info("[SHANHAI-WILDCARD] 电路按钮回调自检：设了 {} 个；回读仍是我们设的 {} 个；已被别人覆盖 {} 个{}",
                installed.size(), readFailed ? -1 : stillOurs, readFailed ? -1 : overwritten,
                readFailed ? "（-1 = 探测失败，见上一行 WARN）" : "");
    }

    /**
     * <b>探针④</b>：这一批按钮此刻的<b>状态</b>——一次把四条死因全报出来。
     *
     * <p>查的就是「按不按得到」的四件事：类是不是 {@code ButtonWidget}、可见吗、{@code isActive} 吗、
     * 是不是被标成纯客户端（{@code isClientSideWidget}）、有没有<b>后被加入的兄弟控件</b>盖住它们，
     * 以及<b>面板自带的「拖拽闸门」会不会把点击吃掉</b>（后者用本类复刻的
     * {@link #shanhaiPanelDragGateEats} 现场算一遍，等值于父类 {@code isMouseover} 的语义）。
     */
    private void shanhaiProbeButtonLiveness(WidgetGroup section) {
        final List<Widget> allChildren = section.getWidgetsByType(Widget.class);
        final List<ButtonWidget> buttons = section.getWidgetsByType(ButtonWidget.class);
        int visible = 0;
        int active = 0;
        int clientSide = 0;
        int covered = 0;
        int eatenByDragGate = 0;
        for (final ButtonWidget button : buttons) {
            if (button.isVisible()) {
                visible++;
            }
            if (button.isActive()) {
                active++;
            }
            if (button.isClientSideWidget()) {
                clientSide++;
            }
            if (shanhaiHasLaterSiblingCovering(section, button)) {
                covered++;
            }
            if (this.shanhaiPanelDragGateEats(button)) {
                eatenByDragGate++;
            }
        }
        LOGGER.info("[SHANHAI-WILDCARD] 电路按钮状态自检：段内控件 {} 个（ButtonWidget {} 个、非按钮 {} 个）；"
                        + "可见 {}；可交互(isActive) {}；被标为纯客户端 {}；被后来加入的兄弟遮盖 {}；"
                        + "会被面板自带「拖拽闸门」吃掉 {} ⇒ 本类已用 mouseClicked 抢先派发绕过该闸门",
                allChildren.size(), buttons.size(), allChildren.size() - buttons.size(),
                visible, active, clientSide, covered, eatenByDragGate);
        LOGGER.info("[SHANHAI-WILDCARD] 电路段状态：段 visible={} active={} clientSide={} 段坐标=({},{}) 段尺寸={}x{}；"
                        + "面板坐标=({},{}) 面板尺寸={}x{}",
                section.isVisible(), section.isActive(), section.isClientSideWidget(),
                section.getPositionX(), section.getPositionY(),
                section.getSizeWidth(), section.getSizeHeight(),
                this.getPositionX(), this.getPositionY(),
                this.getSizeWidth(), this.getSizeHeight());
    }

    /**
     * 本类对父类 {@code isMouseover(int x, int y, int w, int h, double mx, double my)} 的<b>逐条复刻</b>
     * （javap 偏移 0–241），用来<b>在打开面板时就预言</b>「这一下点击会不会被拖拽闸门吃掉」。
     *
     * <p>语义（父类原文）：
     * <pre>
     *   点不在面板矩形内                                   ⇒ false（闸门不触发）
     *   点在面板矩形内，且点在【任何孙级 WidgetGroup】矩形内 ⇒ false（闸门不触发，正常派发给子控件）
     *   点在面板矩形内，且没有任何孙级 WidgetGroup 兜住它   ⇒ true （闸门命中 ⇒ 吞掉 + isDragging=true）
     * </pre>
     * ⚠️ 这是<b>复刻</b>，不是调用 —— 它只用于日志预判；真正的行为已由 {@link #mouseClicked} 覆写接管。
     */
    private boolean shanhaiPanelDragGateEats(Widget target) {
        final int centerX = target.getPositionX() + target.getSizeWidth() / 2;
        final int centerY = target.getPositionY() + target.getSizeHeight() / 2;
        final int panelX = this.getPositionX();
        final int panelY = this.getPositionY();
        if (!(centerX >= panelX && centerY >= panelY
                && centerX < panelX + this.getSizeWidth() && centerY < panelY + this.getSizeHeight())) {
            return false;
        }
        for (final Widget child : this.getWidgetsByType(Widget.class)) {
            if (child.getParent() != this || !(child instanceof WidgetGroup group)) {
                continue;
            }
            for (final Widget inner : group.getWidgetsByType(Widget.class)) {
                if (inner.getParent() != group || !(inner instanceof WidgetGroup grand)) {
                    continue;
                }
                if (centerX >= grand.getPositionX() && centerY >= grand.getPositionY()
                        && centerX < grand.getPositionX() + grand.getSizeWidth()
                        && centerY < grand.getPositionY() + grand.getSizeHeight()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 同组里有没有<b>比它后加入</b>的兄弟控件盖住了它的中心点。
     *
     * <p>依据 {@code WidgetGroup.mouseClicked} 的<b>倒序</b>遍历（javap 偏移 0–70：{@code i = size-1 → 0}，
     * 命中即 return）—— 后加入的先被问，所以「压在它上面」的只会是下标更大的那个。
     */
    private static boolean shanhaiHasLaterSiblingCovering(WidgetGroup parent, Widget target) {
        final List<Widget> siblings = parent.getWidgetsByType(Widget.class);
        final int index = siblings.indexOf(target);
        if (index < 0) {
            return false;
        }
        final int centerX = target.getPositionX() + target.getSizeWidth() / 2;
        final int centerY = target.getPositionY() + target.getSizeHeight() / 2;
        for (int i = index + 1; i < siblings.size(); i++) {
            final Widget sibling = siblings.get(i);
            if (sibling.getParent() != parent || !sibling.isVisible() || !sibling.isActive()) {
                continue;
            }
            if (centerX >= sibling.getPositionX() && centerY >= sibling.getPositionY()
                    && centerX < sibling.getPositionX() + sibling.getSizeWidth()
                    && centerY < sibling.getPositionY() + sibling.getSizeHeight()) {
                return true;
            }
        }
        return false;
    }


    /**
     * 某个按钮属于哪一号电路；认不出来 ⇒ {@link ShanhaiCircuitGrid#UNKNOWN}。
     *
     * <p>坐标表与全部算术都在纯逻辑核 {@link ShanhaiCircuitGrid} 里（那一类不 import 任何
     * Minecraft / GTCEu / LDLib 类，所以能被 {@code javac} 单独编译、在裸 JVM 上跑断言）。
     * 本方法只负责从控件取自坐标。
     */
    private static int shanhaiCircuitOfButton(Widget button) {
        return ShanhaiCircuitGrid.circuitAt(button.getSelfPositionX(), button.getSelfPositionY());
    }

    // ------------------------------------------------------------ 电路段挂载

    /**
     * 把 GTCEu 的「电路设置」整段接到当前内容的<b>最下面</b>，并把面板撑高。
     *
     * <p>⚠️ {@code MEPatternCatalystUIManager#show(...)} 是 private，钩不到它的尺寸计算，
     * 所以只能在它算完之后再追加一段并 {@code setSize}。视觉上等价于「三段之后再来一段」。
     *
     * <p>🔴 每次都<b>先撤掉上一轮那段、再按基准尺寸重新挂</b>：
     * 否则「同一格收起后再展开」这条路上（{@code super.toggleFor} 不调 {@code show()}、
     * 上一段的子节点还在）会挂出<b>第二份</b> 33 个按钮，且面板高度每次都涨一截。
     *
     * <p>🔴 本轮新增：挂上之前先把那 33 个按钮的回调重绑（{@link #shanhaiRebindCircuitButtons}）。
     */
    private void shanhaiAttachCircuitSection() {
        this.shanhaiDetachCircuitSection();
        final Widget created = new CircuitFancyConfigurator(this.circuitSlot).createConfigurator();
        if (created instanceof WidgetGroup section) {
            this.shanhaiRebindCircuitButtons(section);
        } else {
            LOGGER.info("[SHANHAI-WILDCARD] 电路段不是 WidgetGroup（实际 {}）⇒ 跳过按钮接线；"
                    + "33 键仍走 GTCEu 自带通道", created == null ? "null" : created.getClass().getName());
        }
        this.circuitSection = created;
        this.circuitSection.setSelfPosition(0, this.baseHeight + SECTION_GAP);
        this.addWidget(this.circuitSection);
        this.setSize(Math.max(this.baseWidth, CIRCUIT_PANEL_WIDTH),
                this.baseHeight + SECTION_GAP + CIRCUIT_PANEL_HEIGHT);
    }

    /**
     * 撤掉当前那段电路设置（子节点 ＋ 尺寸都回到基底）。
     *
     * <p>⚠️ {@code removeWidget} 对「已经不在子节点里的对象」是安全的：
     * 换格那条路上 {@code show(...)} 里的 {@code clearAllWidgets()} 已经把上一段摘掉了，
     * 此时这里只是把我们手上那个引用丢掉。
     */
    private void shanhaiDetachCircuitSection() {
        if (this.circuitSection != null) {
            this.removeWidget(this.circuitSection);
            this.circuitSection = null;
        }
    }

    /**
     * 1 格代理容器 —— 让 GTCEu 那张电路网格直接把「当前格」的电路读出来 / 写回去。
     *
     * <p>⚠️ 不落任何影子状态：读完即走机器的 {@code slotCircuit}（{@code @DescSynced} 权威字段）。
     *
     * <p>🔴 <b>2026-10-01 二次修复后，这条 {@code setStackInSlot} 通道【已经不再是按钮那条路】</b>
     * （按钮的回调被重绑到 {@link #shanhaiSendSlotCircuit} 了）。仍然保留它，是因为
     * GTCEu 那段自带一个 {@code SlotWidget(circuitSlot, 0, 78, 20)} ——
     * 容器给那个槽做同步时会经 {@code WidgetSlotItemTransfer.m_5852_}（javap 偏移 82–92）
     * 打到这里来。那一路上机器必然在客户端侧 ⇒ 机器会打一行
     * 「原因=这是客户端侧（isRemote）」。<b>那是回声，不是错误</b>，详见类注释第 2 节。
     */
    private final class SlotCircuitTransfer extends ItemStackTransfer {

        SlotCircuitTransfer() {
            super(1);
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            final int circuit = ShanhaiCatalystPanel.this.machine
                    .shanhaiSlotCircuit(ShanhaiCatalystPanel.this.currentSlot);
            return circuit == ShanhaiSlotCircuit.NONE ? ItemStack.EMPTY : IntCircuitBehaviour.stack(circuit);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            final int circuit;
            if (stack.isEmpty() || !IntCircuitBehaviour.isIntegratedCircuit(stack)) {
                circuit = ShanhaiSlotCircuit.NONE;
            } else {
                circuit = IntCircuitBehaviour.getCircuitConfiguration(stack);
            }
            ShanhaiCatalystPanel.this.machine
                    .shanhaiApplySlotCircuit(ShanhaiCatalystPanel.this.currentSlot, circuit);
            this.onContentsChanged(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return IntCircuitBehaviour.isIntegratedCircuit(stack);
        }
    }
}
