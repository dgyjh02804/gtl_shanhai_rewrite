package com.shanhai.machine.engine;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 主机结构里唯一的「字符串查表」方块：空槽占位方块 {@code kubejs:steam_assembly_block}。
 *
 * <h2>为什么必须单独一个类、且必须判空</h2>
 * 结构的另外 11 个谓词全部用 GTCEu <b>类型安全常量</b>（{@code GTBlocks} / {@code GCyMBlocks}，
 * 见 {@link PrimordialOmegaEngineStructure}），零字符串查表。只有这个占位方块查不到 Java 常量 ——
 * 它由<b>宿主 KubeJS</b> 定义（{@code kubejs/startup_scripts/block.js}），规格 §2.3 / A17 明确
 * <b>不许迁入本 mod</b>，只能按字面 id 引用。
 *
 * <p>它缺失的后果是「16 个模块位永远没有合法方块 ⇒ 主机永不成型」，而 GTCEu 对
 * {@code Predicates.blocks((Block) null)} 既不会给你可读的报错、也不会打日志 ——
 * 这正是最难查的一类静默失效。因此本类<b>只允许响亮地失败</b>：
 * 拿不到就抛 {@link IllegalStateException}（带明确 id 与排查指引），绝不退化成 {@code air}。
 *
 * <h2>🔴 第二次真启动暴露的真 bug：{@code ForgeRegistries.BLOCKS.getValue()} 对「不存在的 id」
 * <b>不返回 null</b>，而是返回该注册表的默认值 {@code minecraft:air}</h2>
 *
 * <p>旧实现写的是 {@code return ForgeRegistries.BLOCKS.getValue(STEAM_ASSEMBLY_BLOCK);}，
 * 结果在 mod 构造期（那时 KubeJS 的方块<b>根本还没注册</b>）拿到了一个<b>非 null</b> 的
 * {@code Block} —— 于是走了「就位」分支、把 {@code Blocks.AIR} 缓存进了本类，
 * 并且<b>再也没有挂 CommonSetup 硬校验</b>。整个 fail-fast 设计被静默旁路：
 * J 位谓词会变成 {@code AIR ∪ 模块方块}，装了模块或空槽的结构都不成型，<b>不抛异常、不打日志</b>。
 *
 * <p>证据（Forge 47.4.16 反编译，路径见交付报告）：
 * <pre>
 *   // net/minecraftforge/registries/ForgeRegistry.java:170-177
 *   public V getValue(ResourceLocation key) {
 *       V ret = this.names.get(key);
 *       for (ResourceLocation var3 = this.aliases.get(key); ret == null &amp;&amp; var3 != null; var3 = this.aliases.get(var3))
 *           ret = this.names.get(var3);
 *       return ret == null ? this.defaultValue : ret;      // ← 缺失时返回 default，不是 null
 *   }
 *   // net/minecraftforge/registries/ForgeRegistry.java:365-374 —— 注册到 defaultKey 的那一项会成为 defaultValue
 *   // net/minecraftforge/registries/GameData.java:
 *   makeRegistry(Keys.BLOCKS, "air")…                              // ← 方块注册表的默认键是 minecraft:air
 *   → RegistryBuilder.setDefaultKey(new ResourceLocation(_default))
 * </pre>
 * 也就是说 {@code ForgeRegistries.BLOCKS.getValue(new ResourceLocation("kubejs","steam_assembly_block"))}
 * 在方块缺失时返回 {@code Blocks.AIR}。<b>判存在必须用 {@code containsKey}</b>，本类已改。
 *
 * <p>唯一「不当场抛」的情形是<b>注册期还没到</b>（宿主 KubeJS 的方块在注册期才入表）：
 * 那时由 {@code com.shanhai.machine.ShanhaiMachines#init()} 挂 CommonSetup 硬校验接管 ——
 * 仍然在 mod 加载期失败，而不是等玩家把结构搭完之后。
 */
public final class EngineBlocks {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 空槽占位方块（宿主 KubeJS）。规格 §2.3 / A17：**不迁入本 mod**，只按这个字面 id 引用。 */
    public static final ResourceLocation STEAM_ASSEMBLY_BLOCK =
            new ResourceLocation("kubejs", "steam_assembly_block");

    private static volatile Block steamAssemblyBlock;

    private EngineBlocks() {}

    /**
     * 字符串查表（全工程唯一查 {@code kubejs:steam_assembly_block} 的地方），<b>不抛异常</b>，
     * 查不到返回 {@code null}。
     *
     * <p>🔴 <b>必须用 {@code containsKey} 判存在</b>：{@code ForgeRegistries.BLOCKS} 的
     * {@code getValue()} 对缺失 id 返回默认值 {@code minecraft:air}（见类注释的 Forge 源码行号），
     * 只判 {@code != null} 会把「方块不存在」误判成「方块就位」，从而静默地拿 air 去建谓词。
     */
    @Nullable
    public static Block lookupSteamAssemblyBlock() {
        try {
            IForgeRegistry<Block> registry = ForgeRegistries.BLOCKS;
            if (registry == null || !registry.containsKey(STEAM_ASSEMBLY_BLOCK)) {
                return null;
            }
            Block block = registry.getValue(STEAM_ASSEMBLY_BLOCK);
            // 双保险：万一哪天默认键被换成别的方块，这里仍然不把「默认值」当成真方块。
            if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) {
                return null;
            }
            return block;
        } catch (Throwable t) {
            // 注册表实例都还没就绪：按「此刻查不到」处理，由调用方走延后校验
            LOGGER.debug("[SHANHAI-SPEC] 方块注册表此刻不可查（{}）：{}", STEAM_ASSEMBLY_BLOCK, t.toString());
            return null;
        }
    }

    /** 缓存已判空通过的空槽占位方块。 */
    public static void cacheSteamAssemblyBlock(Block block) {
        steamAssemblyBlock = block;
    }

    /** 空槽占位方块；拿不到就抛（结构谓词用，绝不返回 {@code null}）。 */
    public static Block steamAssemblyBlock() {
        Block block = steamAssemblyBlock;
        if (block != null) return block;
        block = lookupSteamAssemblyBlock();
        if (block == null) {
            throw new IllegalStateException("[SHANHAI] 缺少方块 " + STEAM_ASSEMBLY_BLOCK
                    + "：它是主机 16 个模块位的空槽占位方块（规格 §2.3），由宿主 KubeJS 的 "
                    + "kubejs/startup_scripts/block.js 提供，本 mod 不注册、也不允许注册同名方块（A17）。"
                    + "排查：宿主 kubejs 启动脚本是否报错（<实例>/logs/kubejs/startup.log）；"
                    + "若确实没有它，按规格 §8 R1 换占位方块并走 v1.2 变更流程。");
        }
        steamAssemblyBlock = block;
        return block;
    }
}
