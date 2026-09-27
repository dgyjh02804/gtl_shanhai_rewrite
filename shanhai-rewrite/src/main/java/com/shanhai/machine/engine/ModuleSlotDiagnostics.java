package com.shanhai.machine.engine;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 主机侧 {@code [SHANHAI-SPEC]} 诊断日志（规格 §4.3，验收 A3/A4/A6 直接 grep 它）。
 *
 * <h2>字段顺序不可变</h2>
 * <pre>
 * [SHANHAI-SPEC] host_scan_begin count=16
 * [SHANHAI-SPEC] host_scan pos=(x,y,z) facing=UP slot=0 layer=0 px=x py=y pz=z
 * ... 共 16 行 ...
 * [SHANHAI-SPEC] module_connected module=(x,y,z) host=(x,y,z) slot=0
 * [SHANHAI-SPEC] module_disconnected module=(x,y,z) host=(x,y,z)
 * </pre>
 * {@code facing} 用枚举名（{@code UP}/{@code DOWN}/{@code NORTH}/…），不是
 * {@code getSerializedName()} 的小写形式。
 *
 * <p>只写服务端：{@code isClientSide()} 时直接返回，避免客户端刷日志。
 * 日志本身不参与任何判定逻辑，异常也不允许影响成型流程。
 */
public final class ModuleSlotDiagnostics {

    public static final String PREFIX = "[SHANHAI-SPEC]";

    /** 每层的槽位数（F-1：4 层 × 每层 4 方位 = 16）。 */
    private static final int SLOTS_PER_LAYER = 4;

    // ── 槽位三态（2026-09 加，用于把"日志里没有 slot=N"从推断变成实测） ──
    /** 该位置没有机器（方块不是机器控制器，或区块没加载）。 */
    public static final String STATE_NO_MACHINE = "no_machine";
    /** 有机器，但不是我们的模块（IModularMachineModule）。 */
    public static final String STATE_NOT_MODULE = "not_module";
    /** 是模块机器，但<b>自己没成型</b>（gtlcore 的 isValidModule 会因此拒收它）。 */
    public static final String STATE_NOT_FORMED = "not_formed";
    /** 已成型、已被主机连接（正常态）。 */
    public static final String STATE_OK = "ok";
    /** 已成型，但没进主机的模块集（本次扫描没收下）。 */
    public static final String STATE_FORMED_UNCONNECTED = "formed_unconnected";

    private static final Logger LOGGER = LogUtils.getLogger();

    private ModuleSlotDiagnostics() {}

    /**
     * 16 行 {@code host_scan}（前一行 {@code host_scan_begin}）。
     *
     * <p>2026-09 加：每行尾部多一个 {@code state=} 字段（三态），**不新增行数**；
     * 各态计数与"第几次成型"折进已有的 {@code host_scan_begin} 那一行（也不新增行数）。
     * 这样「一次成型 = 17 行」的口径不变，不会把日志刷成两倍。
     *
     * @param states      逐槽三态（长度可与 {@code positions} 不同，缺的按 {@code null} 处理）
     * @param reformIndex 本次是第几次成型（从 1 开始；诊断用，不参与任何判定）
     * @param kept        本次调和式重扫<b>保留</b>的模块数
     * @param dropped     本次调和式重扫<b>断开</b>的模块数（判据：应当只在模块真的失效时 &gt; 0）
     */
    public static void logHostScan(@Nullable Level level, BlockPos hostPos, Direction facing,
                                   BlockPos[] positions, @Nullable String[] states, int reformIndex,
                                   int kept, int dropped) {
        if (isClient(level) || positions == null) return;

        int noMachine = 0;
        int notModule = 0;
        int notFormed = 0;
        int okCount = 0;
        int formedUnconnected = 0;
        if (states != null) {
            for (String state : states) {
                if (STATE_NO_MACHINE.equals(state)) noMachine++;
                else if (STATE_NOT_MODULE.equals(state)) notModule++;
                else if (STATE_NOT_FORMED.equals(state)) notFormed++;
                else if (STATE_OK.equals(state)) okCount++;
                else if (STATE_FORMED_UNCONNECTED.equals(state)) formedUnconnected++;
            }
        }

        LOGGER.info("{} host_scan_begin count={} reform=#{} kept={} dropped={} no_machine={} not_module={} not_formed={} ok={} formed_unconnected={}",
                PREFIX, positions.length, reformIndex, kept, dropped,
                noMachine, notModule, notFormed, okCount, formedUnconnected);

        for (int slot = 0; slot < positions.length; slot++) {
            BlockPos pos = positions[slot];
            LOGGER.info("{} host_scan pos=({},{},{}) facing={} slot={} layer={} px={} py={} pz={} state={}",
                    PREFIX,
                    hostPos.getX(), hostPos.getY(), hostPos.getZ(),
                    facing.name(),
                    slot, slot / SLOTS_PER_LAYER,
                    pos.getX(), pos.getY(), pos.getZ(),
                    states != null && slot < states.length ? states[slot] : "unknown");
        }
    }

    public static void logModuleConnected(@Nullable Level level, BlockPos modulePos, BlockPos hostPos, int slot) {
        if (isClient(level) || modulePos == null) return;
        LOGGER.info("{} module_connected module=({},{},{}) host=({},{},{}) slot={}",
                PREFIX,
                modulePos.getX(), modulePos.getY(), modulePos.getZ(),
                hostPos.getX(), hostPos.getY(), hostPos.getZ(),
                slot);
    }

    public static void logModuleDisconnected(@Nullable Level level, @Nullable BlockPos modulePos, BlockPos hostPos) {
        if (isClient(level) || modulePos == null) return;
        LOGGER.info("{} module_disconnected module=({},{},{}) host=({},{},{})",
                PREFIX,
                modulePos.getX(), modulePos.getY(), modulePos.getZ(),
                hostPos.getX(), hostPos.getY(), hostPos.getZ());
    }

    private static boolean isClient(@Nullable Level level) {
        return level == null || level.isClientSide();
    }
}
