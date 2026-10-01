package com.shanhai.machine.wildcard;

/**
 * GTCEu {@code CircuitFancyConfigurator} 那张「0~32 电路网格」的<b>纯逻辑核</b>：
 * 「按钮自坐标 → 电路号」。
 *
 * <h2>为什么单独拆一个类（与 {@link ShanhaiSlotCircuit} 同一条纪律）</h2>
 * 本类<b>不 import 任何 Minecraft / GTCEu / LDLib 类</b>，所以它可以被 {@code javac}
 * 单独编译、在裸 JVM 上直接跑断言 —— 游戏不能启动、maven 依赖拉不动的时候，
 * 这是唯一能<b>真正执行</b>的离线证据（而不是"读代码觉得对"）。
 *
 * <h2>坐标从哪来（数字全部是 javap 原文，不是估的）</h2>
 * {@code javap -p -c -cp libs\gtceu-1.20.1-1.4.4.jar \
 *   com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator}：
 * <pre>
 *   createConfigurator() 偏移 0–12   : WidgetGroup(0, 0, 174, 132)
 *   偏移 183–299（双重循环 row=0..2 × col=0..8）:
 *         iconst_5 ; bipush 18 ; iload col ; imul ; iadd          ⇒ x = 5 + col*18
 *         bipush 48 ; bipush 18 ; iload row ; imul ; iadd         ⇒ y = 48 + row*18
 *         电路号 idx 从 0 起逐条自增                                ⇒ 电路 = row*9 + col（0..26）
 *   偏移 302–396（单重循环 i=0..5）:
 *         iconst_5 ; bipush 18 ; iload i ; imul ; iadd            ⇒ x = 5 + i*18
 *         bipush 102                                              ⇒ y = 102
 *         circuit = i + 27                                        ⇒ 电路 = 27+i（27..32）
 *   偏移 144–176（{@code ConfigHolder.machines.ghostCircuit} 为真时那个「清除」键）:
 *         group.getSize().width ; bipush 18 ; isub ; iconst_2 ; idiv ⇒ x = (174-18)/2 = 78
 *         bipush 20                                                  ⇒ y = 20
 * </pre>
 * ⇒ 合计 <b>33 个</b>电路按钮（0..32）＋（条件性的）1 个「清除」键，清除键与那个编程电路槽
 * {@code SlotWidget(circuitSlot, 0, 78, 20)} <b>位置重合</b>。
 *
 * <h2>为什么按坐标认、而不按"第几个子控件"认</h2>
 * 因为 {@code ghostCircuit} 那个「清除」键是<b>条件性</b>插在 33 键<b>之前</b>的
 * （javap 偏移 132–182 在双重循环 183 之前）。按下标数 ⇒ 开关一翻就整体错一位；
 * 按坐标认则天然免疫。
 *
 * <h2>离线自证</h2>
 * {@code node tools\sync-check\check-circuit-buttons-outbound.mjs} 会用
 * <b>javap 那套正向公式</b>（独立来源）生成 33 个位置，喂给本类编译出来的真字节码，
 * 断言「33 个位置一一对应到 0..32、清除键对应 {@link ShanhaiSlotCircuit#NONE}、
 * 网格外的点一律 {@link #UNKNOWN}」。
 */
public final class ShanhaiCircuitGrid {

    /** 电路段整体尺寸（javap 偏移 0–12：{@code WidgetGroup(0, 0, 174, 132)}）。 */
    public static final int PANEL_WIDTH = 174;
    public static final int PANEL_HEIGHT = 132;

    /** 网格 9 列 × 3 行（javap：{@code bipush 8} / {@code iconst_2} 是上界，闭区间 ⇒ 9 / 3）。 */
    public static final int GRID_COLS = 9;
    public static final int GRID_ROWS = 3;

    /** 单格边长 ＝ 行距 ＝ 列距（javap 里三处都是 {@code bipush 18}）。 */
    public static final int STEP = 18;

    /** 网格原点（javap：{@code iconst_5} / {@code bipush 48}）。 */
    public static final int GRID_X0 = 5;
    public static final int GRID_Y0 = 48;

    /** 第二段（电路 27..32）那一行（javap：{@code bipush 102}）。 */
    public static final int TAIL_Y = 102;
    public static final int TAIL_COUNT = 6;

    /** 「清除」键位置（javap：{@code (width-18)/2 = 78} 与 {@code bipush 20}）。 */
    public static final int CLEAR_X = (PANEL_WIDTH - STEP) / 2;
    public static final int CLEAR_Y = 20;

    /** 认不出来（不是这张网格上的任何一键）。 */
    public static final int UNKNOWN = -1;

    private ShanhaiCircuitGrid() {}

    /**
     * 这张网格上一共有多少个「电路号」键（0..32 共 33 个）。
     *
     * <p>数值来自 javap：27（{@code row*9+col}，row 0..2 × col 0..8）＋ 6（第二段 i 0..5）。
     */
    public static final int CIRCUIT_BUTTON_COUNT = GRID_ROWS * GRID_COLS + TAIL_COUNT;

    /** 最大电路号（{@code 27 + 5}）。 */
    public static final int MAX_CIRCUIT = GRID_ROWS * GRID_COLS + TAIL_COUNT - 1;

    /**
     * 按钮自坐标 → 电路号。
     *
     * @param x 按钮相对电路段左上角的 x（{@code CircuitFancyConfigurator} 造出来的自坐标）
     * @param y 同上
     * @return {@code 0..32} 电路号；「清除」键 ⇒ {@link ShanhaiSlotCircuit#NONE}（= 0，与电路 0 同值，
     *         语义都是"不设置"，所以不需要额外区分）；网格外 ⇒ {@link #UNKNOWN}
     */
    public static int circuitAt(int x, int y) {
        if (x == CLEAR_X && y == CLEAR_Y) {
            return ShanhaiSlotCircuit.NONE;
        }
        if (y == TAIL_Y) {
            final int i = indexOf(x, GRID_X0);
            return i >= 0 && i < TAIL_COUNT ? GRID_ROWS * GRID_COLS + i : UNKNOWN;
        }
        if (y >= GRID_Y0) {
            final int col = indexOf(x, GRID_X0);
            final int row = indexOf(y, GRID_Y0);
            if (col >= 0 && col < GRID_COLS && row >= 0 && row < GRID_ROWS) {
                return row * GRID_COLS + col;
            }
        }
        return UNKNOWN;
    }

    /** {@code (v - origin)} 恰好在步长网格上 ⇒ 返回格号，否则 {@code -1}。 */
    private static int indexOf(int v, int origin) {
        final int offset = v - origin;
        return offset >= 0 && offset % STEP == 0 ? offset / STEP : -1;
    }
}
