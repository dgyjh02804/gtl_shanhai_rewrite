package com.shanhai.client.event;

import com.shanhai.ShanhaiMod;
import com.shanhai.client.compat.GtlAddClientCompat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>A 方案</b>的客户端钩子：<b>区块（重新）下发到客户端后，把台账里属于该区块的环格子重新断言为 AIR</b>。
 *
 * <h2>1. 只补哪一个洞（不要指望它管别的）</h2>
 * 只补「<b>区块在客户端卸载、之后又被服务端重新下发</b>」这一个洞：
 * 环隐藏是<b>纯客户端</b>写（服务端从未 hide 过），所以区块一旦在客户端被卸载再下发，
 * 客户端那一格的方块会回到<b>服务端真值 = 真环方块</b>；而此时 {@code hideRingsAt} 会因为
 * "台账非空且扫全了"而 O(1) 短路 ⇒ <b>永远不会再 hide 一遍</b> ⇒ 真环 + VBO 环双重显示。
 * 本钩子就是在"区块刚到达"这个时刻把这类格子再写成 AIR。
 *
 * <h2>2. 它<b>不</b>覆盖的路径（如实写明，别把它当万能药）</h2>
 * <ul>
 *   <li>{@code [推断]} <b>服务端方块更新包绕过它</b>：{@code ClientboundBlockUpdatePacket} /
 *       {@code ClientboundSectionBlocksUpdatePacket}（玩家挖放、机器改方块、结构变更走的都是这条）
 *       直接改客户端区块数据，<b>不触发 {@code ChunkEvent.Load}</b> ⇒ 本钩子不会被调到。
 *       这类"单格回灌"目前靠 {@code hideRingsAt} 的 INCOMPLETE 重扫路径兜（见网关注释）。</li>
 *   <li>宇宙模式（{@code PrimordialSphereStyle.UNIVERSE}）与 {@code PrimordialSphereAnchor}
 *       —— 本钩子与它们<b>无关</b>，也<b>不许</b>因为本钩子而动它们。</li>
 *   <li>诊断（B 方案 {@code ring_drift}）保持<b>只读</b>；补写只在这里发生，两处都写会互相掩盖故障。</li>
 * </ul>
 *
 * <h2>3. 为什么用 {@code @Mod.EventBusSubscriber} 而不是自己 {@code register()}</h2>
 * 工程里此前<b>没有</b>任何客户端 Forge 总线订阅者（{@code modEventBus} 那条链只用于机器注册），
 * 所以这里采用 Forge 原生的自动订阅：{@code value = Dist.CLIENT} 让 Forge <b>只在客户端</b>注册它
 * （专用服务端下这个类不会被注册、方法不会被调到），{@code bus = FORGE} 才是 {@code ChunkEvent} 所在的
 * {@code MinecraftForge.EVENT_BUS}。<b>不新建任何 mixin 基础设施</b>。
 *
 * <h2>4. 时机为什么可信（不是我推测的，是另一路字节码取证的结论）</h2>
 * 生产客户端 {@code ClientChunkCache#replaceWithPacketData} 的指令顺序为：
 * <pre>
 *   96-103 : LevelChunk.setBlockState / 方块数据写入（分支①）
 *   106-114: Storage.replace(...) 入缓存
 *   120-127: 方块数据写入（分支②，复用旧 chunk）
 *   130-136: ClientLevel.onChunkLoaded(ChunkPos)
 *   139-157: MinecraftForge.EVENT_BUS.post(new ChunkEvent$Load(chunk, false))
 *   158-160: areturn
 * </pre>
 * ⇒ 事件在<b>方块数据之后、return 之前</b>触发 ⇒ 处理器里 {@code level.getBlockState(pos)} 读得到
 * 刚下发的真方块；上游 mixin 打在 {@code @At("RETURN")}，中间没有任何会再写方块的代码 ⇒ 语义等价。
 */
@Mod.EventBusSubscriber(modid = ShanhaiMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientChunkLoadHandler {

    private ClientChunkLoadHandler() {}

    /**
     * 区块到达钩子。
     *
     * <p>🔴 <b>第一行必须是边判，一个字都不许挪到它前面</b>：客户端与服务端派发的是<b>同一个</b>
     * {@code ChunkEvent.Load}（单机 = 集成服务器，同一进程里两套逻辑都会 post 这个事件）。
     * 不判边 ⇒ 本处理器会把 AIR 写进<b>服务端</b>的 {@code LevelChunk}
     * ⇒ 服务端数据结构被改 ⇒ <b>落盘 ⇒ 毁用户存档</b>。
     * 这一行是整个 A 方案里唯一一处"漏了就是事故"的代码，因为它下面是<b>真的写方块</b>。
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // 边判（红线）：理由见上。getLevel() 返回 LevelAccessor，其父链 LevelReader#isClientSide 存在
        // （javap 取证：LevelAccessor extends CommonLevelAccessor extends LevelReader，LevelReader 声明 isClientSide）。
        if (!event.getLevel().isClientSide()) {
            return;
        }
        // 收窄成 Level（网关签名要 Level；LevelAccessor 不是 Level）。走到这里已确定是客户端。
        if (!(event.getLevel() instanceof ClientLevel clientLevel)) {
            return;
        }
        ChunkPos chunkPos = event.getChunk().getPos();
        GtlAddClientCompat.reassertHiddenAt(clientLevel, chunkPos.x, chunkPos.z);
    }
}
