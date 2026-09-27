package com.shanhai.machine.module;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.mojang.logging.LogUtils;
import com.shanhai.ShanhaiMod;
import com.shanhai.common.machine.PrimordialOmegaEngineMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>取证探针 {@code [SHANHAI-SETBLOCK]}</b>（2026-09-26）——
 * 抓「<b>谁</b>在<b>哪一行代码</b>把模块位那个坐标<b>从什么方块写成了什么方块</b>」。
 *
 * <h2>1. 为什么必须有它（{@code [SHANHAI-SLOT-WATCH]} 不够）</h2>
 * 既有的 {@link ModuleSlotWatch} 只记录「<b>机器被移除</b>」这个<b>结果</b>，
 * 而用户看到的现象是「<b>退出 → 重进存档后，模块位变成了 {@code kubejs:steam_assembly_block}</b>」——
 * 那是<b>一次方块写入</b>。把探针挂在「机器被移除的回调」上，等于<b>站在下游等</b>：
 * 写入路径根本不是玩家破坏时，那条回调可能压根不响，日志上表现为
 * 「什么都没发生」——<b>和「没有人写方块」长得一模一样</b>（本工程反复踩的那类洞）。
 * <p>⇒ 本探针<b>上移到方块写入的真正入口</b>，直接记<b>调用者栈</b>：那才是「谁写的」。
 *
 * <h2>2. 🔴 入口是查证过的，不是猜的</h2>
 * {@code javap -p -c net.minecraft.world.level.Level}（forge 47.4.16 开发名 jar）里
 * {@code Level.setBlock} 只有<b>一处</b>方块落盘调用：
 * <pre>
 *   124: invokevirtual LevelChunk.setBlockState:(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;
 * </pre>
 * 而 {@code LevelChunk.setBlockState} 体内唯一继续往下写的地方是
 * <pre>
 *    73: invokevirtual LevelChunkSection.setBlockState:(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;
 * </pre>
 * ⇒ <b>{@code LevelChunk#setBlockState(BlockPos, BlockState, boolean)} 就是服务端活世界里所有方块写入的唯一漏斗</b>，
 * 且它<b>手上就有坐标</b>（{@code LevelChunkSection} 那一层已经被摊平成 {@code x/y/z} 索引，反推坐标要额外开销）。
 * 混入代码见 {@code com.shanhai.mixin.ShanhaiSetBlockWatchMixin}。
 *
 * <h2>3. 🔴 范围：只对【16 个模块位 + 主机自身】打点（这是硬要求，否则日志会被淹掉）</h2>
 * 一条写入要被记录，必须命中下面<b>任意一条</b>（{@code 判据} 字段会如实写出命中的是哪一条）：
 * <ol>
 *   <li><b>旧方块是我方方块</b> —— 该坐标上此刻坐着的还是 {@code shanhai:*} 的方块
 *       （16 个模块位上的模块控制器方块、或主机自身）。在 {@code HEAD} 处读取，
 *       读到的正是<b>要被覆盖掉的旧值</b>，这也是本探针「A → B」里 A 的来源。</li>
 *   <li><b>新方块是我方方块</b> —— 有人往某处写了我方方块（放模块 / 结构重建写回模块）。</li>
 *   <li><b>坐标已定格</b> —— 该坐标<b>在本会话内曾经</b>是我方主机或它的 16 个模块位
 *       （由 {@link #registerHost} 登记，见 §4）。
 *       <b>这一条是本次任务的关键</b>：模块位在「被换成 {@code kubejs:steam_assembly_block}」
 *       之后<b>已经不再是我方方块</b>，只靠第 1、2 条抓不到<b>随后的每一次</b>写入；
 *       定格表让这些坐标<b>整局都被盯着</b>。</li>
 * </ol>
 * <b>为什么允许第 1、2 条（比我方方块判据略宽于"16 槽位"）</b>：维护一张「全世界我方方块坐标」的反索引
 * 需要一个世界级扫描，成本与收益不成比例；而<b>我方方块在用户存档里就只有那 24 台模块 + 主机</b>，
 * 所以「命中我方方块」在效果上<b>等价于</b>只盯那几个坐标，<b>不会</b>把「挖石头 / 放泥土」记进来。
 *
 * <h2>4. 🔴 定格表怎么填的（不是猜的，是一个已有回调）</h2>
 * {@code PrimordialOmegaEngineMachine#onLoad()} 是<b>区块重新加载后</b>机器被反序列化时必被调用的
 * GTCEu 生命周期回调（它就是为「存档重进」这一类场景存在的）。
 * 在那里调一次 {@link #registerHost}，把<b>主机坐标 + {@code getModuleScanPositions()} 的 16 个坐标</b>
 * 全部定格。⇒ 用户「退出 → 重进存档」之后，那个写入发生在<b>定格之后</b>，必被记录。
 *
 * <h2>5. 🔴 零行为改动（本工程血账）</h2>
 * <ul>
 *   <li>注入点 {@code @At("HEAD")}，<b>不取消、不修改返回值、不 {@code @Overwrite}、不 {@code @Redirect}</b>；</li>
 *   <li>公有入口 {@link #onSetBlockState} 全程 {@code try/catch(Throwable)} ⇒
 *       <b>打日志的异常自吞 + 打一条 WARN</b>，绝不让「探针打日志失败」变成「方块写不进去」；</li>
 *   <li>只读：{@code ChunkAccess#getBlockState} / {@code MetaMachine#getMachine} /
 *       {@code ForgeRegistries.BLOCKS#getKey} + 一个本地 {@code Long} 集合，不碰任何方块、槽位、NBT。</li>
 * </ul>
 *
 * <h2>6. 🔴 "探针没挂上" 和 "没有人写方块" 必须在日志上长得不一样</h2>
 * {@link #verifyArmed()} 在 mod 构造期用反射看 {@code LevelChunk} 上有没有我们混入的方法，
 * 并<b>当场打一行</b>：挂上了打 INFO，没挂上打 <b>ERROR 并在正文里写明
 * 「本会话内一次都不会响，不要据此判断没有人写方块」</b>。
 */
public final class ModuleSetBlockWatch {

    /** 与工程既有前缀同族（{@code [SHANHAI-LOSS]} / {@code [SHANHAI-SLOT-WATCH]} / {@code [SHANHAI-SPEC]}）。 */
    public static final String PREFIX = "[SHANHAI-SETBLOCK]";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 调用者栈打印的帧数上限（要求 5–8 帧：够看到"谁"，又不至于把日志撑爆）。 */
    private static final int STACK_FRAMES = 8;

    /**
     * 定格表的上限。正常世界里最多是「主机 + 16 槽位 × 主机数」量级（几十个）；
     * 上限只是<b>防止某条病态路径把内存吃光</b>，到顶后<b>只停止新增</b>、不影响任何既有判定。
     */
    private static final int MAX_LATCHED = 4096;

    /** 「本会话内曾是我方主机 / 模块位」的坐标（压缩成 {@code BlockPos#asLong}）。 */
    private static final Set<Long> LATCHED = ConcurrentHashMap.newKeySet();

    /** 由 {@link #verifyArmed()} 填写；只用于日志与人工判读，不参与任何判定。 */
    private static volatile boolean armed = false;

    private ModuleSetBlockWatch() {}

    // ═════════════════════════════ ① 注入点（唯一的写入口） ═════════════════════════════

    /**
     * {@code LevelChunk#setBlockState} 的 {@code HEAD} 回调（由 {@code ShanhaiSetBlockWatchMixin} 调用）。
     *
     * <p>🔴 <b>本方法必须永不抛出</b>：它跑在方块写入的最内层，抛出去就是「为了查 bug 制造 bug」。
     * 任何异常都在这里被吞掉，并且<b>打一条 WARN</b>（不许静默吞 —— 静默吞等于没有自吞的证据）。
     *
     * <p>🔴 <b>为什么 {@code chunk} 是 {@code Object} 而不是 {@code LevelChunk}</b>：
     * 混入类体内<b>不许引用 Minecraft 成员</b>（本工程 mixin 注解处理器只给 {@code @Inject} 目标写 refmap，
     * 见 {@code ShanhaiSetBlockWatchMixin} 类注释 §2 的实测证据）⇒ 混入侧只传 {@code (Object) this}，
     * {@code getLevel()} / {@code getBlockState()} 这些 MC 调用全部下沉到本类（<b>普通类，正常 reobf</b>）。
     *
     * @param chunk    区块本身（混入侧的 {@code this}）；不是 {@code LevelChunk} 就直接返回
     * @param pos      写入坐标
     * @param newState 即将写入的新方块 B
     */
    public static void onSetBlockState(@Nullable Object chunk, @Nullable BlockPos pos, @Nullable BlockState newState) {
        try {
            if (!(chunk instanceof LevelChunk levelChunk)) {
                return;
            }
            shanhai$handle(levelChunk.getLevel(), levelChunk, pos, newState);
        } catch (Throwable t) {
            warn("写入点记录异常（已忽略，本次方块写入不受影响）", t);
        }
    }

    private static void shanhai$handle(@Nullable Level level, @Nullable ChunkAccess chunk,
                                       @Nullable BlockPos pos, @Nullable BlockState newState) {
        if (level == null || chunk == null || pos == null || newState == null) {
            return;
        }
        // 🔴 仅服务端：单机的服务端逻辑跑在集成服务器上，判 ServerLevel 而不是判 Dist（理由同 ModuleSlotWatch §3）。
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        final BlockState oldState = chunk.getBlockState(pos);
        // 同一个实例 ⇒ 原版 setBlockState 会在开头直接 return（什么都没写）⇒ 记它只会制造噪音。
        if (oldState == newState) {
            return;
        }
        final BlockPos p = pos.immutable();
        final String reason = shanhai$reason(p, oldState, newState);
        if (reason == null) {
            return; // 不在范围内：一行都不打（挖石头 / 放泥土 / 世界生成都到不了这里）
        }

        final long key = p.asLong();
        LATCHED.add(key); // 命中即定格：此后该坐标上任何写入都会被记录

        final String aBlock = shanhai$blockId(oldState);
        final String aMachine = shanhai$machineAt(server, p);
        final String bBlock = shanhai$blockId(newState);

        LOGGER.info("{} tick={} 世界={} pos=({},{},{}) 范围={} 判据={} A={}{} → B={} 线程={} 栈={}",
                PREFIX,
                server.getGameTime(),
                server.dimension().location(),
                p.getX(), p.getY(), p.getZ(),
                shanhai$scopeOf(server, p),
                reason,
                aBlock, aMachine,
                bBlock,
                Thread.currentThread().getName(),
                shanhai$stack());
    }

    // ═════════════════════════════ ② 范围判定 ═════════════════════════════

    /**
     * @return 命中范围时返回<b>人类可读的判据</b>（会写进日志的 {@code 判据=} 字段）；不在范围内返回 {@code null}。
     */
    @Nullable
    private static String shanhai$reason(@Nullable BlockPos pos, @Nullable BlockState oldState,
                                         @Nullable BlockState newState) {
        if (shanhai$isOurs(oldState)) {
            return "旧方块是我方方块（该坐标此刻正被我方方块占用 ⇒ 属于 16 个模块位或主机自身）";
        }
        if (shanhai$isOurs(newState)) {
            return "新方块是我方方块";
        }
        if (pos != null && LATCHED.contains(pos.asLong())) {
            return "坐标已定格（本会话内曾是我方主机 / 模块位）";
        }
        return null;
    }

    /** 方块是否属于本 mod（{@code shanhai:*}）。注册表还没就绪时一律按「不是」处理，绝不抛。 */
    private static boolean shanhai$isOurs(@Nullable BlockState state) {
        if (state == null) {
            return false;
        }
        final Block block = state.getBlock();
        if (block == null) {
            return false;
        }
        final ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return id != null && ShanhaiMod.MOD_ID.equals(id.getNamespace());
    }

    // ═════════════════════════════ ③ 坐标定格（主机 + 16 个模块位） ═════════════════════════════

    /**
     * 把一台主机的坐标 + 它的 16 个模块位坐标登记进定格表。
     *
     * <p>调用点<b>只有一个</b>：{@code PrimordialOmegaEngineMachine#onLoad()}
     * —— 区块重载（= 用户重进存档）时机器被反序列化，那是「重进之后发生的写入」能被抓到的前提。
     *
     * <p>同样<b>永不抛出</b>：{@code onLoad()} 是机器加载链上的一环，探针不许在那里制造任何风险。
     */
    public static void registerHost(@Nullable PrimordialOmegaEngineMachine host) {
        try {
            if (host == null || host.isRemote()) {
                return;
            }
            final Level level = host.getLevel();
            if (!(level instanceof ServerLevel server)) {
                return;
            }
            final BlockPos hostPos = host.getPos();
            if (hostPos == null) {
                return;
            }
            final int before = LATCHED.size();
            latch(hostPos.asLong());
            int slots = 0;
            final BlockPos[] positions = host.getModuleScanPositions();
            if (positions != null) {
                for (BlockPos slot : positions) {
                    if (slot != null) {
                        latch(slot.asLong());
                        slots++;
                    }
                }
            }
            LOGGER.info("{} 已定格主机坐标 + {} 个模块位（世界={} host=({},{},{})），新增定格 {} 个坐标，"
                            + "目前共 {} 个 —— 此后这些坐标上的任何方块写入都会被记录",
                    PREFIX, slots, server.dimension().location(),
                    hostPos.getX(), hostPos.getY(), hostPos.getZ(),
                    LATCHED.size() - before, LATCHED.size());
        } catch (Throwable t) {
            warn("登记主机坐标失败（已忽略，不影响机器加载）", t);
        }
    }

    private static void latch(long key) {
        if (LATCHED.size() < MAX_LATCHED) {
            LATCHED.add(key);
        }
    }

    // ═════════════════════════════ ④ 「挂上了没有」的自证 ═════════════════════════════

    /**
     * 世界加载完成（{@code ServerStartedEvent}）后调用：确认混入真的落在了 {@code LevelChunk} 上，
     * 并<b>把结论打进日志</b>。
     *
     * <h3>🔴 为什么自检必须等到【世界加载之后】，不能放在 mod 构造期</h3>
     * 第一版把它放在 {@code ShanhaiMod} 构造器里，<b>冒烟实测是假阴性</b>：
     * 那一拍 {@code LevelChunk} 还没被 Mixin 处理过，反射看到的还是未混入的类
     * ⇒ 日志上打出一条「探针未挂上」的 ERROR，<b>而同一轮里注入其实成功了</b>
     * （{@code require = 1}：真失败就加载期直接崩，服务器根本到不了 {@code Done (}）。
     * ⇒ 自检挪到世界加载之后（那时 {@code LevelChunk} 一定已经加载并混入过）。
     *
     * <p>存在意义：{@code Mixin} 的注入若因映射 / refmap 问题失败，探针会<b>一次都不响</b>，
     * 而日志上「没有 {@code [SHANHAI-SETBLOCK]} 行」<b>同时</b>也是「这几天确实没有人写方块」的样子。
     * 这两种结论必须可区分 ⇒ 这里正着证明一次「挂上了」，那条 ERROR 才是判「没挂上」的依据。
     */
    public static void onServerStarted(@Nullable ServerStartedEvent event) {
        try {
            verifyArmed();
        } catch (Throwable t) {
            warn("挂载自检本身异常（不影响游戏）", t);
        }
    }

    private static void verifyArmed() {
        String found = null;
        int mixinLike = 0;
        // 🔴 只读反射，不改任何东西；DIST 判据不用（单机也要在集成服务器里自证）。
        // 🔴 必须用 contains("shanhai$") 而不是 startsWith("shanhai$")：Mixin 会把混入方法**改名**成
        //    `handler$<随机串>$<原名>`。第一版用了 startsWith ⇒ 世界加载后仍然报「找不到」，
        //    而同一轮日志里明确有写入行、栈里就有
        //    `LevelChunk.handler$zgh000$shanhai$watchSetBlockState(LevelChunk.java:1404)`
        //    ⇒ 那是【自检自己的假阴性】，不是探针没挂上（已实测）。
        for (Method m : LevelChunk.class.getDeclaredMethods()) {
            final String n = m.getName();
            if (n.contains("shanhai$")) {
                found = n;
                break;
            }
            if (n.contains("$")) {
                mixinLike++;
            }
        }
        final int total = LevelChunk.class.getDeclaredMethods().length;
        if (found != null) {
            armed = true;
            LOGGER.info("{} 取证探针已挂上 net.minecraft.world.level.chunk.LevelChunk"
                            + "#setBlockState(BlockPos, BlockState, boolean) 的 HEAD（混入方法 = {}；该类的声明方法数 = {}）；"
                            + "范围 = 16 个模块位 + 主机自身（附加：命中我方方块的坐标、以及本会话内定格过的坐标）；"
                            + "仅服务端生效；每条写入一行，grep 前缀 {} 即可",
                    PREFIX, found, total, PREFIX);
        } else {
            armed = false;
            LOGGER.error("{} 【挂载自检未通过】在世界加载之后的 LevelChunk 上找不到含 shanhai$ 的混入方法"
                            + "（该类声明方法数 = {}，其中含 $ 的 = {}）"
                            + "⇒ 请把本条当作【待确认】而不是【探针一定没工作】，并用下面这条独立证据交叉核对："
                            + "本次日志里有没有任何一行 {} 行 —— 只要有一行，就说明混入确实在跑。"
                            + "🔴 反过来：若真的一行都没有，也【不能】直接判「没有人写方块」，"
                            + "先确认 jar 内 shanhai.refmap.json 是否含 LevelChunk#setBlockState、"
                            + "启动日志里有没有 Mixin / refmap 相关报错。",
                    PREFIX, total, mixinLike, PREFIX);
        }
    }

    /** {@link #verifyArmed()} 的结论（给日志判读用；不参与任何业务判定）。 */
    public static boolean armed() {
        return armed;
    }

    // ═════════════════════════════ ⑤ 现场描述 ═════════════════════════════

    /** 范围的人类可读描述：优先给出「主机 / 模块位 slot=N」，其次「定格坐标」。 */
    private static String shanhai$scopeOf(Level level, BlockPos pos) {
        try {
            final MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine instanceof PrimordialOmegaEngineMachine host) {
                return "主机自身 host=(" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + ") facing=" + host.getFrontFacing().name();
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
        } catch (Throwable ignored) {
            // 范围描述取不到不影响这一行日志的取证价值（坐标 / A / B / 栈都还在）
        }
        return "定格坐标 (x,y,z)=" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + "（本会话内曾是我方主机 / 模块位；此刻该坐标上已取不到我方机器）";
    }

    /** 该坐标上此刻的机器（{@code HEAD} 处读到的仍是<b>旧的</b>那台）—— 拿不到就返回空串。 */
    private static String shanhai$machineAt(Level level, BlockPos pos) {
        try {
            final MetaMachine machine = MetaMachine.getMachine(level, pos);
            if (machine == null) {
                return "";
            }
            final ResourceLocation key = machine.getDefinition().getId();
            return "（machine=" + (key == null ? "unknown" : key.toString()) + "）";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String shanhai$blockId(@Nullable BlockState state) {
        if (state == null) {
            return "null（读不到状态）";
        }
        try {
            final ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            return key == null ? "unknown（未注册的方块）" : key.toString();
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    /**
     * 取<b>调用者栈</b>（前 {@value #STACK_FRAMES} 帧）—— 「谁写的」就在这里。
     *
     * <p>跳过三类帧，让这 8 个名额<b>全部留给真正的调用者</b>：
     * <ol>
     *   <li>JDK 的 {@code Thread.getStackTrace} 自身；</li>
     *   <li>探针自己的帧（{@code ModuleSetBlockWatch}）；</li>
     *   <li>混入方法 + 目标方法自身（{@code LevelChunk#setBlockState}）——
     *       ⚠️ <b>混入方法在运行期会被 Mixin 改名成 {@code handler$<随机串>$<原名>}</b>
     *       （实测栈里就是 {@code LevelChunk.handler$zgh000$shanhai$watchSetBlockState}），
     *       所以按 <b>{@code contains("shanhai$")}</b> 过滤，<b>不能</b>按 {@code startsWith} 过滤。</li>
     * </ol>
     * ⇒ 栈的第 1 帧就是真正的调用者（实测为 {@code Level.setBlock} 那一层）。
     */
    private static String shanhai$stack() {
        final StringBuilder sb = new StringBuilder(720);
        try {
            final StackTraceElement[] frames = Thread.currentThread().getStackTrace();
            int shown = 0;
            for (StackTraceElement f : frames) {
                if (f == null) {
                    continue;
                }
                final String cn = f.getClassName();
                final String mn = f.getMethodName();
                if (cn == null || mn == null || cn.startsWith("java.lang.Thread")) {
                    continue;
                }
                if (cn.startsWith("com.shanhai.machine.module.ModuleSetBlockWatch")) {
                    continue;
                }
                if (mn.contains("shanhai$")) {
                    continue;
                }
                if (cn.equals("net.minecraft.world.level.chunk.LevelChunk")
                        && (mn.equals("setBlockState") || mn.equals("m_6978_"))) {
                    continue;
                }
                sb.append(++shown).append(')').append(cn).append('.').append(mn)
                        .append('(').append(f.getFileName()).append(':').append(f.getLineNumber()).append(") ");
                if (shown >= STACK_FRAMES) {
                    break;
                }
            }
        } catch (Throwable t) {
            return "取栈失败(" + t + ")";
        }
        return sb.length() == 0 ? "(空：栈里只有探针自己的帧)" : sb.toString();
    }

    /** 自吞 + WARN：探针的所有异常都走这里，且<b>本身也不许抛</b>。 */
    private static void warn(String what, Throwable t) {
        try {
            LOGGER.warn("{} {}：{}", PREFIX, what, t.toString());
        } catch (Throwable ignored) {
            // 连日志都打不出去时，也绝不允许影响方块写入
        }
    }
}
