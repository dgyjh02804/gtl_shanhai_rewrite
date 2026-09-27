package com.shanhai.mixin;

import com.shanhai.machine.module.ModuleSetBlockWatch;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 山海重构 · <b>取证探针 {@code [SHANHAI-SETBLOCK]} 的注入骨架</b>：
 * 挂在 {@code LevelChunk#setBlockState} 的 {@code HEAD}。
 *
 * <h2>1. 🔴 目标方法是 {@code javap} 查证的，不是猜的</h2>
 * <pre>
 * javap -p -s -classpath &lt;forge-1.20.1-47.4.16_mapped_parchment_2023.09.03-1.20.1.jar&gt; \
 *       net.minecraft.world.level.chunk.LevelChunk
 *   public net.minecraft.world.level.block.state.BlockState
 *     setBlockState(net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState, boolean);
 *   descriptor: (Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)
 *               Lnet/minecraft/world/level/block/state/BlockState;
 * </pre>
 * <b>{@code LevelChunk} 上只有一个 {@code setBlockState}</b>（无重载 ⇒ 描述符抄错的空间都没有），
 * 且 {@code Level.setBlock} 体内<b>唯一</b>的方块落盘调用就是它
 * （完整调用链取证见 {@link ModuleSetBlockWatch} 类注释 §2）。
 *
 * <h2>2. 🔴🔴 本类体内<b>一个字都不许引用 Minecraft 成员</b>（2026-09-26 实测出来的硬约束）</h2>
 * 目标方法用<b>开发名</b>写、由 {@code build.gradle} 那套 mixin 注解处理器生成 refmap
 * 映射成运行期 SRG 名 —— 这个机制<b>只对 {@code @Inject} 选中的方法生效</b>。
 * 实测证据（本轮构建产物）：
 * <pre>
 *   build\mixinrefmap\shanhai.refmap.json
 *     "com/shanhai/mixin/ShanhaiSetBlockWatchMixin": {
 *        "setBlockState(...)L...;": "Lnet/minecraft/world/level/chunk/LevelChunk;m_6978_(...)L...;"   ← 有
 *     }
 *   ✗ 同一份 refmap 里【没有】任何 @Shadow 字段（level → f_62776_）、
 *     也没有任何 @Shadow 方法（getLevel → m_XXXXX_）的条目 ——
 *     而 build\mixinrefmap\obfuscation-tsrg-dev-to-srg.tsrg 里这两条映射【都在】
 *     （LevelChunk 块：`level f_62776_`），⇒ 是<b>处理器没写进去</b>，不是映射缺失。
 * </pre>
 * ⇒ 若在本类体内调 {@code getLevel()} / 读 {@code level} 字段，生产环境会拿<b>字面开发名</b>去
 * SRG 命名的 {@code LevelChunk} 上找 ⇒ 找不到 ⇒ 混入失败 / 崩。
 * <p><b>本工程已经跑通的先例</b>：{@code ShanhaiFontStyleMixin} 的 {@code javap -c} 里，
 * 它对本项目以外的一切只有 {@code checkcast} 到自己的目标类型，其余全部
 * {@code invokestatic com/shanhai/client/text/ShanhaiFontStyleRenderer.*} ——
 * <b>「混入类只当转发壳、所有 MC 调用下沉到普通类」</b>正是本工程既有的写法，本类照此办理。
 * <p>⇒ 本类只做三件事：<b>类型对得上的形参</b>、{@code (Object) this}、一次
 * {@link ModuleSetBlockWatch#onSetBlockState} 调用。{@code getLevel()} / {@code getBlockState()} /
 * {@code ServerLevel} 判定全部在 {@code ModuleSetBlockWatch}（<b>普通类，会被 reobf，SRG 名正常</b>）里做。
 * <p>（形参类型 {@code BlockPos} / {@code BlockState} 不需要映射：<b>类名不混淆</b>，只有成员名才需要。）
 *
 * <h2>3. 🔴 它是「零行为改动」的</h2>
 * <ul>
 *   <li>{@code @At("HEAD")}、<b>不</b>{@code cancellable}、<b>不</b>{@code @Overwrite}、
 *       <b>不</b>{@code @Redirect} ⇒ 不改返回值、不改控制流；</li>
 *   <li>回调体<b>只调一次</b> {@link ModuleSetBlockWatch#onSetBlockState}，而那里面全程
 *       {@code try/catch(Throwable)} 自吞 + WARN ⇒ <b>探针坏了也不会让方块写不进去</b>；</li>
 *   <li>这里再套一层 {@code try/catch} 作为<b>第二道保险</b>。</li>
 * </ul>
 *
 * <h2>4. 为什么 {@code require = 1}</h2>
 * 与 {@code ShanhaiSmokeMixin} 同一取舍：目标方法找不到时<b>加载期就炸</b>，
 * 而不是「探针静默不存在、日志上看起来像这几天没人写方块」。这是本次取证的<b>总判据</b>，
 * 静默失败会让整轮调查得出错误的否定结论 ⇒ 宁可炸得响。
 * （另外 {@code verifyArmed()} 会在 mod 构造期正着证明一次「挂上了」。）
 */
@Mixin(LevelChunk.class)
public abstract class ShanhaiSetBlockWatchMixin {

    /**
     * 方块写入的最内层入口。<b>只把现场原样转交给普通类，不解读、不干预。</b>
     *
     * @param pos      写入坐标
     * @param newState 即将写入的新方块状态（{@code HEAD} 时区块里还是旧状态，读得到 A）
     * @param isMoving 原版形参，本探针不使用（照抄以匹配描述符）
     * @param cir      原版返回值回调，本探针<b>不</b>触碰
     */
    @Inject(
            method = "setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)"
                    + "Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            require = 1)
    private void shanhai$watchSetBlockState(BlockPos pos, BlockState newState, boolean isMoving,
                                            CallbackInfoReturnable<BlockState> cir) {
        try {
            // (Object) this ⇒ 本类体内零 MC 成员引用（理由见类注释 §2）。
            // 世界（LevelChunk#getLevel）与旧方块（ChunkAccess#getBlockState）都在普通类里读。
            ModuleSetBlockWatch.onSetBlockState((Object) this, pos, newState);
        } catch (Throwable ignored) {
            // 第二道保险：真正的「自吞 + WARN」在 ModuleSetBlockWatch 内部（见类注释 §3）
        }
    }
}
