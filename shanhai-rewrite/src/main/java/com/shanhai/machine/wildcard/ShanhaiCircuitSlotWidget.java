package com.shanhai.machine.wildcard;

import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.IntConsumer;

/**
 * 主页面上的**样板格**控件：一个普通的 {@link SlotWidget}（贴图 = GTCEu 的
 * {@code SLOT} ＋ {@code PATTERN_OVERLAY}，与宿主原版那台单格样板格逐字相同），
 * 外加两件原版 {@code SlotWidget} 没有的事：
 *
 * <ol>
 *   <li><b>中键</b> ⇒ 交给机器装好的 {@code IntConsumer}（= 右侧催化剂块的
 *       {@code MEPatternCatalystUIManager#toggleFor(格号)}），并顺带把下方嵌入式
 *       「样板电路设置」块切到这一格；</li>
 *   <li>老的「中键 ⇒ 弹框 ⇒ writeClientAction 提交电路」那条通道
 *       （{@link #shanhaiSendCircuit(int)} / {@link #handleClientAction(int, FriendlyByteBuf)}）
 *       仍然保留 —— 当前界面上没人调它，但它是 {@link ShanhaiCircuitPanel} 的服务端出口，
 *       删掉会让那块面板彻底失能。</li>
 * </ol>
 *
 * <h2>🔴 为什么中键分支【不】判「槽里有没有东西」</h2>
 * 2026-10-01 用户第 3 条的原话是「没有装通配符样板的格子都点不开」。根因就是本类
 * 旧版在 {@code mouseClicked} 里多要了一个 {@code !transfer.getStackInSlot(...).isEmpty()} 条件。
 * 上游 {@code org.gtlcore.gtlcore…AEPatternViewExtendSlotWidget} 的中键分支
 * （javap -c 偏移 23–57）在 {@code isEmpty()} 判定（偏移 76/91）**之前**就 {@code return true}。
 * ⇒ 本类照上游：只要点在格子上、且是右键序号的 2（= 中键），一律吃掉并回调，不判内容。
 *
 * <h2>它与宿主原版单格那行的对应关系</h2>
 * 宿主原版（{@code MEWildcardPatternBufferPartMachine#createUIWidget} 偏移 88–136）是：
 * {@code new SlotWidget(wildcardPatternSlot, 0, 70, 25).setChangeListener(...).setBackground(SLOT, PATTERN_OVERLAY)}。
 * 本类就是那一行加了中键回调，并且在 5×2 的循环里被 new 十次。
 *
 * <h2>🔴 2026-10-01 实机验收发现的第 4 件事：中键必须<b>同时</b>发到服务端（本轮已修）</h2>
 *
 * <h3>症状（用户回报的第 8/9/10 条）</h3>
 * <ol>
 *   <li>右侧「电路设置」那 33 个按钮<b>点了没反应</b>；</li>
 *   <li>「物品催化剂槽」是<b>幽灵</b> —— 放进去的物品只在客户端看得见，
 *       拿起来放回物品栏 ⇒ 真物品被弄没了；直接 esc ⇒ 它又回到物品栏
 *       （⇒ 服务端<b>从没收到过</b>它）；</li>
 *   <li>「流体催化剂槽」<b>放不进液体</b>。</li>
 * </ol>
 *
 * <h3>根因（三条同一个病）</h3>
 * 实机日志里那行 {@code [SHANHAI-WILDCARD] 收到催化剂槽变化} 命中 <b>0 条</b> ⇒ 服务端一次都没收到；
 * 而「催化剂槽回调已接线：…共 100 条」那行<b>在</b> ⇒ 服务端的容器与回调<b>本身都是好的</b>。
 * 机制（LDLib 侧，字节码实证）：
 * <pre>
 *   ModularUIContainer extends AbstractContainerMenu        ← 是真正的原版容器
 *   ⇒ 槽位「客户端一份 / 服务端一份」各自登记（SlotWidget.createSlot ＋ ModularUIContainer.addSlot）
 *   MEPatternCatalystUIManager.show(格号, …)                ← 那一整块面板控件的【唯一】建造点
 *   show(...) 只由 toggleFor(格号) 调用，而 toggleFor 由中键触发
 *   ⇒ 中键没送到服务端 ⇒ 服务端那份容器里根本没有这些槽
 *      ⇒ 客户端多出来的槽在服务端无对应 Slot
 *      ⇒ 放置只改客户端（幽灵）／流体放不进／电路按钮点了没人接
 * </pre>
 *
 * <h3>上游那台是怎么做的（判据锚）</h3>
 * {@code org.gtlcore.gtlcore.integration.ae2.widget.AEPatternViewExtendSlotWidget}
 * 的中键分支（{@code javap -p -c} 原文；上游 {@code MEPatternBufferPartMachine.createUIWidget}
 * 与 {@code MESuperPatternBufferPartMachine.createUIWidget} 装的都是同一条
 * {@code setOnMiddleClick(() -> catalystUIManager.toggleFor(格号))}）：
 * <pre>
 *   mouseClicked 偏移 23–57:
 *       iload 5 ; iconst_2 ; if_icmpne 58        ← button == 2（中键）
 *       bipush 10 ; invokedynamic …              ← 动作号 10
 *       invokevirtual Widget.writeClientAction:(ILjava/util/function/Consumer;)V   ← 🔴 发给服务端
 *       invokeinterface Runnable.run:()V                                            ← 本地也跑一次
 *       iconst_1 ; ireturn
 *   handleClientAction 偏移 147–162:
 *       iload_1 ; bipush 10 ; if_icmpne 28
 *       invokeinterface Runnable.run:()V          ← 🔴 服务端再跑一次同一回调
 * </pre>
 * ⇒ <b>「发一份 ＋ 本地也跑一份」是上游的固定写法，不是可选项</b>。
 *
 * <h3>修法</h3>
 * 本类的中键分支照抄那条写法：先 {@code writeClientAction(ACTION_TOGGLE_CATALYST, 格号)}，
 * 再本地跑一次；服务端 {@link #handleClientAction} 里对同一动作号再跑一次。
 * ⚠️ 动作号必须与老的 {@link #ACTION_SET_CIRCUIT} 区分开，且两侧共用同一个常量。
 * ⚠️ 光在这里发还不够：接线的另一端（{@code catalystUIManager::toggleFor}）必须已经装好 ——
 * 判据脚本里那条「判据 3」就是在钉这件事。
 * ⇒ 判据：{@code node tools\sync-check\check-catalyst-panel-sync.mjs} 必须全绿。
 */
public class ShanhaiCircuitSlotWidget extends SlotWidget {

    /**
     * 老的「提交本格电路」客户端动作号。
     *
     * <p>⚠️ 取值 {@code 21320} 是从改动前的字节码里读回来的原值（{@code sipush 21320}），
     * <b>不要随手改</b>：它只在本类内部收发，改了就与旧存档里可能还在飞的包对不上。
     */
    public static final int ACTION_SET_CIRCUIT = 21320;

    /**
     * 🔴 「中键切换右侧那一块」的客户端动作号（2026-10-01 本轮新增）。
     *
     * <p>存在的理由只有一个：让服务端也跑一次 {@code toggleFor}。
     * 右侧那一整块面板的控件（催化剂槽 3×3 / 流体槽 3×3 / 电路设置 33 键）是
     * {@code MEPatternCatalystUIManager.show(...)} 里<b>当场 new</b> 出来的，
     * 而 LDLib 的槽位是「客户端一份 / 服务端一份」各自登记的
     * （{@code ModularUIContainer extends AbstractContainerMenu}）——
     * 只在客户端跑 {@code toggleFor} ⇒ 服务端那份容器里根本没有这些槽
     * ⇒ 放进去的东西只活在客户端（幽灵物品）。详见类注释。
     *
     * <p>⚠️ 取值与 {@link #ACTION_SET_CIRCUIT}（21320）不同、且两侧共用同一常量；
     * 本动作只送一个「格号」，不做任何状态搬运。
     */
    public static final int ACTION_TOGGLE_CATALYST = 21321;

    private final SuperWildcardPatternBufferPartMachine machine;

    private final ItemStackTransfer transfer;

    private final int slotIndex;

    /** 中键回调（右侧催化剂块的开关 ＋ 下方电路块的选格）。装配时由机器装上。 */
    private IntConsumer onMiddleClick;

    public ShanhaiCircuitSlotWidget(ItemStackTransfer transfer, int slotIndex, int x, int y,
                                    SuperWildcardPatternBufferPartMachine machine) {
        super(transfer, slotIndex, x, y);
        this.transfer = transfer;
        this.slotIndex = slotIndex;
        this.machine = machine;
    }

    /** 机器在装配界面时装上中键回调。 */
    public void shanhaiSetMiddleClickHandler(IntConsumer handler) {
        this.onMiddleClick = handler;
    }

    public int shanhaiSlotIndex() {
        return this.slotIndex;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // button == 2 是中键（0 左 / 1 右 / 2 中）。
        if (button == 2 && this.isMouseOverElement(mouseX, mouseY)) {
            final IntConsumer handler = this.onMiddleClick;
            if (handler != null) {
                // 🔴 先发给服务端，再本地跑 —— 两侧都必须跑 toggleFor，否则面板只在客户端存在。
                //    这就是上游 AEPatternViewExtendSlotWidget.mouseClicked 偏移 23–57 的顺序
                //    （偏移 16–22 先判 gui != null ⇒ 才 writeClientAction ⇒ 再本地 run）。
                //    ⚠️ 这两句【就写在这里】，不要再折进辅助方法：判据脚本
                //    （tools\sync-check\check-catalyst-panel-sync.mjs 的「判据 2」）要求
                //    writeClientAction 出现在 mouseClicked 方法体里 —— 与上游形态逐字对齐时才最不容易走样。
                if (this.getGui() != null) {
                    this.writeClientAction(ACTION_TOGGLE_CATALYST, buf -> buf.writeInt(this.slotIndex));
                }
                handler.accept(this.slotIndex);
            }
            // 与上游同款：中键被本控件吃掉，不再往下传（也不触发 SlotWidget 自己的搬运逻辑）。
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 把「本格 = 值」送到服务端（老的弹框通道；当前界面上没有调用点）。 */
    public void shanhaiSendCircuit(int value) {
        final int normalized = ShanhaiSlotCircuit.normalize(true, value);
        this.writeClientAction(ACTION_SET_CIRCUIT, buf -> {
            buf.writeInt(this.slotIndex);
            buf.writeInt(normalized);
        });
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        if (id == ACTION_SET_CIRCUIT) {
            final int index = buffer.readInt();
            final int value = buffer.readInt();
            this.machine.shanhaiApplySlotCircuit(index, value);
            return;
        }
        // 🔴 本轮新增：服务端也跑一次中键回调。
        //    没有这一支 ⇒ 服务端永远不知道面板开过 ⇒ 右侧那一整块在服务端等于不存在
        //    （用户第 8/9/10 条那三个症状）。
        //    ⚠️ 服务端与客户端各自跑一次是【对的】：toggleFor 是纯粹的开关/换格语义，
        //       两侧各持一份自己的显示状态，不需要互相同步第二遍。
        if (id == ACTION_TOGGLE_CATALYST) {
            final int index = buffer.readInt();
            final IntConsumer handler = this.onMiddleClick;
            if (handler != null) {
                handler.accept(index);
            }
            return;
        }
        super.handleClientAction(id, buffer);
    }
}
