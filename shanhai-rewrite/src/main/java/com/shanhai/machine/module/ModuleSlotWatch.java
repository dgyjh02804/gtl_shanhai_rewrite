package com.shanhai.machine.module;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.mojang.logging.LogUtils;
import com.shanhai.common.machine.PrimordialOmegaEngineMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * <b>取证探针 {@code [SHANHAI-SLOT-WATCH]}</b>（2026-09-26）——
 * 抓「<b>谁</b>把模块位那个坐标<b>从什么方块改成了什么方块</b>」。
 *
 * <h2>1. 它回答的问题（现在没有日志能回答）</h2>
 * 此前只知道"某个模块位坐标上的方块变了"，<b>日志里没有任何一行能指出是谁干的</b>。
 * 本探针把一次方块移除打成一条可 grep 的行：
 * <pre>
 *   [SHANHAI-SLOT-WATCH] removed 谁=名字(uuid=…) tick=… pos=(x,y,z) 范围=… A=旧方块 → B=新方块 模块被移除掉落 N 件（分槽明细）
 * </pre>
 *
 * <h2>2. 🔴 范围：只认【16 个模块位 + 主机自身】</h2>
 * 判定靠<b>该坐标上的机器</b>（{@code MetaMachine.getMachine}），不猜、不扫全图：
 * <ul>
 *   <li>机器是 {@link PrimordialOmegaEngineMachine} ⇒ <b>主机自身</b>；</li>
 *   <li>机器是 {@link PrimordialModuleMachine} ⇒ <b>模块位</b>，能连上主机时顺带给出<b>槽位号</b>
 *       （下标落在 {@code host.getModuleScanPositions()} 的哪一格）；</li>
 *   <li>其余坐标 <b>一行都不打</b>（挖石头、挖机器外壳都不会污染日志）。</li>
 * </ul>
 * <b>诚实边界</b>：<b>不含</b>"那一格已经是占位方块 / 空气"的情形 —— 那时坐标上已经没有机器，
 * 拿不到它属于哪台主机，反查需要一个主机的反索引，本探针刻意不做（见 §5）。
 *
 * <h2>3. 🔴 为什么用【显式注册】而不是 {@code @Mod.EventBusSubscriber}</h2>
 * 写法本身照 {@code ClientChunkLoadHandler} 的 Forge 原生路子；<b>但注册点挪到了
 * {@code ShanhaiMod} 的构造器里显式 {@code MinecraftForge.EVENT_BUS.addListener(...)}</b>。理由有两条：
 * <ol>
 *   <li><b>注册这件事必须留下证据</b>。{@code @Mod.EventBusSubscriber} 注册成功时<b>一行日志都没有</b>
 *       ⇒「探针到底挂上了没有」在日志上<b>不可判</b>（正是本工程反复踩的那类洞），
 *       而显式注册可以在同一条链上打一行 {@code [SHANHAI-SLOT-WATCH] 取证探针已挂上 FORGE 事件总线}。
 *       那一行的<b>前置条件</b>（{@code ShanhaiMod} 构造器跑到了末尾）在同一份启动日志里还有
 *       {@code [SHANHAI] shanhai 已加载（阶段 1）}作旁证。</li>
 *   <li><b>不用 {@code Dist} 决定"仅服务端"</b>。用户玩的是<b>单机</b>，单机的服务端逻辑跑在集成服务器上、
 *       {@code Dist} 仍是 {@code CLIENT} ⇒ 若按 {@code Dist.DEDICATED_SERVER} 注册，本探针
 *       <b>在单机里一次都不会生效</b>（静默失效）。「仅服务端」这条纪律因此改由
 *       <b>运行期</b>的 {@code ServerLevel} 判定执行（见 {@link #onBlockBreak}）。</li>
 * </ol>
 *
 * <h2>4. 🔴 为什么 A→B 的 B 在 {@code onMachineRemoved()} 里读，而不是在 BreakEvent 里读</h2>
 * {@code BlockEvent.BreakEvent} 在<b>方块被移除之前</b>触发（Forge 在
 * {@code ServerPlayerGameMode#destroyBlock} 里先 post 事件、不取消才 {@code removeBlock}）
 * ⇒ 那一刻读 {@code getBlockState(pos)} 读到的还是 <b>A</b>，拿不到 B。
 * <p>而 {@code PrimordialModuleMachine#onMachineRemoved()} 是<b>方块真的被换掉</b>那一步的回调，
 * 并且此刻区块里<b>新状态已经写好</b>（字节码实证：{@code LevelChunk.setBlockState} 先在偏移 64–76
 * 把新状态写进 {@code LevelChunkSection}，之后才在偏移 <b>328</b> 调
 * {@code BlockState.onRemove(level, pos, newState, moved)} ⇒ {@code MetaMachineBlock.m_6810_}
 * ⇒ {@code IMachineLife.onMachineRemoved()}）⇒ 在那里读 {@code getBlockState(pos)} 才是 <b>B</b>。
 * <p>⚠️ 若日后 MC/Forge 改了这个顺序，B 会读成 A —— 那种情况下本探针会打印
 * {@code B=A（与 A 相同 ⇒ 读取时刻新状态尚未写入）}，<b>不会静默给一个假的 B</b>。
 *
 * <h2>5. 🔴 零行为改动</h2>
 * 本类<b>只读</b>：{@code MetaMachine.getMachine} 与 {@code Level#getBlockState}，
 * 外加读一次玩家名 / uuid / tick。不改方块、不取消事件、不掉落、不动任何槽 ——
 * 因此<b>不设"可关闭"开关</b>（没有行为可关）。
 *
 * <h2>6. 注册点</h2>
 * {@code ShanhaiMod} 构造器（{@code MinecraftForge.EVENT_BUS.addListener(ModuleSlotWatch::onBlockBreak)}）
 * —— 理由见 §3。本类<b>不带</b> {@code @Mod.EventBusSubscriber}（带了就会重复注册、每条日志打两遍）。
 */
public final class ModuleSlotWatch {

    /** 与工程既有前缀风格一致（{@code [SHANHAI-SPEC]} / {@code [SHANHAI-LOSS]} 同族）。 */
    public static final String PREFIX = "[SHANHAI-SLOT-WATCH]";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 一次「玩家破坏 ⇒ 方块被移除」的现场记录。
     *
     * <p>🔴 <b>为什么可以用一个静态单槽</b>：{@code BreakEvent} 与随后的
     * {@code onMachineRemoved()} 在<b>同一次调用栈</b>里（{@code destroyBlock} → {@code removeBlock}），
     * 而服务端方块操作在<b>主线程串行</b> ⇒ 中间不可能插进另一次破坏。
     * 仍然按 {@code pos} + {@code tick} 双重核对，对不上就当"没有 BreakEvent"（见 §5 诚实边界）。
     */
    private static final class Pending {
        final long tick;
        final @NotNull BlockPos pos;
        final @NotNull String who;
        final @NotNull String uuid;
        final @NotNull String scope;
        final @NotNull String aBlock;

        Pending(long tick, @NotNull BlockPos pos, @NotNull String who, @NotNull String uuid,
                @NotNull String scope, @NotNull String aBlock) {
            this.tick = tick;
            this.pos = pos;
            this.who = who;
            this.uuid = uuid;
            this.scope = scope;
            this.aBlock = aBlock;
        }
    }

    @Nullable
    private static Pending pending;

    private ModuleSlotWatch() {}

    // ═════════════════════════════ ① 破坏点 ═════════════════════════════

    /**
     * 玩家破坏方块时的记录点。<b>只服务端</b>（单机也算服务端逻辑 —— 见类注释 §3）。
     *
     * <p>本方法<b>不打 A→B 那一行</b>：B 要等方块真的被换掉才读得到（见类注释 §4）。
     * 它只做两件事：① 记下"谁 / 什么 tick / 哪个坐标 / 旧方块 A / 范围"；
     * ② 打一条 <b>break 行</b>作为<b>兜底</b>——万一 {@code onMachineRemoved()} 没被调到
     * （例如方块不是被移除、而是被直接替换），这一行仍然留下"谁碰过这个坐标"。
     *
     * <p>⚠️ 本方法由 {@code ShanhaiMod} 构造器里的
     * {@code MinecraftForge.EVENT_BUS.addListener(ModuleSlotWatch::onBlockBreak)} 显式挂上
     * ⇒ <b>不带</b> {@code @SubscribeEvent}（那是给自动订阅用的，两套一起用会重复注册）。
     */
    public static void onBlockBreak(@NotNull BlockEvent.BreakEvent event) {
        // 🔴 探针绝不许把异常抛回 Forge 事件链（那会把"探针坏了"变成"玩家挖不了方块"）。
        try {
            shanhai$handleBreak(event);
        } catch (Throwable t) {
            LOGGER.error("{} 破坏点记录异常（已忽略，挖方块行为不受影响）：{}", PREFIX, t.toString());
        }
    }

    private static void shanhai$handleBreak(@NotNull BlockEvent.BreakEvent event) {
        // 🔴 边判放最前：取消掉的破坏不会改方块，记它只会污染日志。
        if (event.isCanceled()) {
            return;
        }
        // 🔴 仅服务端：用真实类型判，不用 Dist（理由见类注释 §3）。
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        final BlockPos pos = event.getPos().immutable();
        final String scope = shanhai$scopeOf(level, pos);
        if (scope == null) {
            return; // 不在【16 个模块位 + 主机自身】范围内 ⇒ 一行都不打
        }

        final Player player = event.getPlayer();
        final String who = player == null ? "未知（无玩家引用）" : player.getName().getString();
        final String uuid = player == null ? "-" : player.getUUID().toString();
        final long tick = level.getGameTime();
        final String aBlock = shanhai$describe(level, pos, event.getState());

        pending = new Pending(tick, pos, who, uuid, scope, aBlock);
        LOGGER.info("{} break 谁={}(uuid={}) tick={} pos=({},{},{}) 范围={} A={}"
                        + " ⇒ B 由紧随的 removed 行实测（见 ModuleSlotWatch 类注释 §4）",
                PREFIX, who, uuid, tick, pos.getX(), pos.getY(), pos.getZ(), scope, aBlock);
    }

    // ═════════════════════════════ ② 模块被移除点 ═════════════════════════════

    /**
     * 模块方块真的被移除时调用（{@code PrimordialModuleMachine#onMachineRemoved()}）。
     *
     * <p>调用时机在<b>槽清空之后</b>（掉落件数由调用方在清空前数好传进来）——
     * 这样 B 才是"这个坐标现在是什么"的<b>实测值</b>。
     *
     * @param level       模块所在世界（客户端 / null 直接返回，绝不写日志）
     * @param pos         模块坐标
     * @param droppedItems 本次随方块一起掉的物品总件数
     * @param breakdown   分槽明细（人类可读，探针自己不算数）
     */
    public static void onModuleRemoved(@Nullable Level level, @NotNull BlockPos pos, int droppedItems,
                                       @NotNull String breakdown) {
        // 🔴 同理：本方法在 onMachineRemoved 里、掉物品【之后】被调；抛异常会污染方块移除链。
        try {
            shanhai$handleRemoved(level, pos, droppedItems, breakdown);
        } catch (Throwable t) {
            LOGGER.error("{} 移除点记录异常（已忽略，掉落行为不受影响）：{}", PREFIX, t.toString());
        }
    }

    private static void shanhai$handleRemoved(@Nullable Level level, @NotNull BlockPos pos, int droppedItems,
                                              @NotNull String breakdown) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        final Pending p = pending;
        final boolean matched = p != null && p.pos.equals(pos) && p.tick == serverLevel.getGameTime();
        if (p != null && p.pos.equals(pos)) {
            pending = null; // 用完即弃；下一个坐标的事件必须自带自己的 BreakEvent
        }

        final String bBlock = shanhai$describe(serverLevel, pos, serverLevel.getBlockState(pos));
        final String bNote = matched && bBlock.equals(p.aBlock)
                ? "（⚠️ B 与 A 相同 ⇒ 读取时刻新状态尚未写入，B 不可采信）"
                : "";
        final String droppedText = droppedItems < 0
                ? "计数失败（见上一行 ERROR；掉落本身照常）"
                : droppedItems + " 件";
        // 没有 BreakEvent 的路径（爆炸 / 结构变更 / 指令 / 别的 mod 写方块）也照样记录，
        // 但要如实标明"不知道是谁"——不许把"没记录"写成"没人动过"。
        LOGGER.info("{} removed 谁={}(uuid={}) tick={} pos=({},{},{}) 范围={} A={} → B={}（实测 level.getBlockState，"
                        + "onMachineRemoved 时）{} 模块被移除掉落 {}（{}）",
                PREFIX,
                matched ? p.who : "非玩家破坏路径（本 tick 内没有对应的 BreakEvent）",
                matched ? p.uuid : "-",
                matched ? p.tick : serverLevel.getGameTime(),
                pos.getX(), pos.getY(), pos.getZ(),
                matched ? p.scope : "模块位（未能反查主机）",
                matched ? p.aBlock : "未记录（无 BreakEvent）",
                bBlock, bNote, droppedText, breakdown);
    }

    // ═════════════════════════════ 工具 ═════════════════════════════

    /**
     * 判定坐标是否落在【16 个模块位 + 主机自身】范围内；是则返回人类可读的范围描述，否则 {@code null}。
     *
     * <p>槽位号的取法：{@code host.getModuleScanPositions()} 里 {@code pos} 的下标
     * （与 {@code [SHANHAI-SPEC] host_scan … slot=N} 是同一套编号）。
     */
    @Nullable
    private static String shanhai$scopeOf(@NotNull Level level, @NotNull BlockPos pos) {
        final MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (machine instanceof PrimordialOmegaEngineMachine host) {
            return "主机自身 host=(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ") facing="
                    + host.getFrontFacing().name();
        }
        if (machine instanceof PrimordialModuleMachine module) {
            final PrimordialOmegaEngineMachine host = module.getHost();
            if (host == null) {
                return "模块位（未连接主机，槽位号取不到）";
            }
            final BlockPos[] slots = host.getModuleScanPositions();
            int slot = -1;
            for (int i = 0; i < slots.length; i++) {
                if (pos.equals(slots[i])) {
                    slot = i;
                    break;
                }
            }
            final BlockPos hp = host.getPos();
            return "模块位 slot=" + (slot >= 0 ? String.valueOf(slot) : "?（不在该主机的槽位表里）")
                    + " host=(" + hp.getX() + "," + hp.getY() + "," + hp.getZ() + ")";
        }
        return null;
    }

    /** 「方块 id（+ 我方机器 id，若该坐标上是机器）」的人类可读描述。 */
    @NotNull
    private static String shanhai$describe(@NotNull Level level, @NotNull BlockPos pos,
                                           @Nullable BlockState state) {
        final ResourceLocation blockKey = state == null ? null : ForgeRegistries.BLOCKS.getKey(state.getBlock());
        final String blockId = blockKey == null ? "unknown" : blockKey.toString();
        final MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (machine == null) {
            return blockId;
        }
        final ResourceLocation machineKey = machine.getDefinition().getId();
        return blockId + "（machine=" + machineKey + "）";
    }
}
