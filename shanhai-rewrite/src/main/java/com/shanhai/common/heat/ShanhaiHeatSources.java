package com.shanhai.common.heat;

import com.gregtechceu.gtceu.common.block.CoilBlock;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 山海重构 · <b>恒星热力槽</b>「槽里那件东西是什么」的**唯一**读表处。
 *
 * <h2>🔴 为什么不写死一张表</h2>
 * 「哪些方块算线圈」这件事在本整合包里有<b>三个来源</b>，写死任何一份都会漏：
 * <ol>
 *   <li>GTCEu 自带的 8 个 —— {@code GTBlocks.createCoilBlock(ICoilType)} 造出
 *       {@code com.gregtechceu.gtceu.common.block.CoilBlock}（{@code GTBlocks} 字节码里
 *       {@code createCoilBlock} 恰好 8 个调用点：cupronickel / kanthal / nichrome / rtm_alloy /
 *       hssg / naquadah / trinium / tritanium）；</li>
 *   <li><b>KubeJS 注册的</b> —— 本实例 {@code kubejs/startup_scripts/block.js:3-28,163} 用
 *       {@code event.create("xxx_coil_block", "gtceu:coil")} 又注册了 9 个
 *       （abyssalalloy 12600 / titansteel 14400 / adamantine 16200 / naquadriatictaranium 18900 /
 *       starmetal 21600 / infinity 36000 / hypogen 62000 / eternity 96000 / uruium 273）；
 *       {@code CoilBlockBuilder.createObject()} 造出来的同样是 {@link CoilBlock}
 *       （{@code javap} 实测该类 {@code extends BlockBuilder} 且暴露
 *       {@code temperature/level/energyDiscount/tier} 四个字段 ⇒ 就是给 GTCEu 那套 coil 用的）。</li>
 * </ol>
 * ⇒ <b>判据一律是「它是不是 {@link CoilBlock}」</b>，温度从 {@link CoilBlock#coilType}
 * （公开字段，{@code javap -p} 实证）现读 —— <b>表里一个数字都不抄</b>。
 *
 * <h2>⚠️ 哪些"看起来像线圈"的东西<b>不是</b>加热线圈（照着名字猜会踩的坑）</h2>
 * <ul>
 *   <li>{@code gtceu:superconducting_coil} / {@code gtceu:fusion_coil} —— 是
 *       {@code FusionCasingBlock}，不是 {@code CoilBlock}（{@code GTBlocks} 字段类型实证）；
 *       它们用在聚变结构的 {@code c} 格上，不提供炉温。</li>
 *   <li>{@code gtlcore:advanced_fusion_coil} / {@code fusion_coil_mk2} /
 *       {@code improved_superconductor_coil} / {@code compressed_fusion_coil} /
 *       {@code advanced_compressed_fusion_coil} / {@code compressed_fusion_coil_mk2_prototype} /
 *       {@code compressed_fusion_coil_mk2} / {@code qft_coil} —— 全是
 *       {@code com.gregtechceu.gtceu.api.block.ActiveBlock}，<b>不是</b> {@code CoilBlock}
 *       （{@code GTLBlocks} 字段类型实证）⇒ 本槽不收它们。</li>
 * </ul>
 * 这两条是**如实报**：名字里带 coil 但机制上不提供炉温的方块，收进来只会让玩家以为生效了却不生效。
 *
 * <h2>恒星热力容器</h2>
 * 只有 gtlcore 那三个（{@code GTLBlocks} 字段实证存在，中文名取自
 * {@code assets/gtlcore/lang/zh_cn.json}）。等级口径来自 {@code GTLRecipeTypes#getSCTier(int)}
 * 的字节码：{@code 1 → gtceu.tier.base}、{@code 2 → gtceu.tier.advanced}、{@code 3 → gtceu.tier.ultimate}。
 * <p>⚠️ 这里用<b>注册 id 字符串</b>比对，<b>不 import gtlcore 的类</b> —— 与
 * {@code ModuleRegistry} 里 GTLAdd 走 {@code GtlAddCompat} 是同一个纪律：少一个硬编译依赖，
 * 那个 mod 换版本时这里不会连带炸。代价是 id 改名会静默失效，所以下面三条常量就是唯一真源。
 */
public final class ShanhaiHeatSources {

    private ShanhaiHeatSources() {}

    /** 基础恒星热力容器（{@code GTLBlocks.STELLAR_CONTAINMENT_CASING}）。 */
    public static final String SC_BASIC_ID = "gtlcore:stellar_containment_casing";
    /** 高级恒星热力容器（{@code GTLBlocks.ADVANCED_STELLAR_CONTAINMENT_CASING}）。 */
    public static final String SC_ADVANCED_ID = "gtlcore:advanced_stellar_containment_casing";
    /** 终极恒星热力容器（{@code GTLBlocks.ULTIMATE_STELLAR_CONTAINMENT_CASING}）。 */
    public static final String SC_ULTIMATE_ID = "gtlcore:ultimate_stellar_containment_casing";

    /** 容器等级上限（= 上面三个）。给 tooltip 与自证用，避免两处各写一个 3。 */
    public static final int MAX_CONTAINMENT_TIER = 3;

    /** 槽里那件东西提供的热力来源。不可变。 */
    public static final class Source {

        /** 什么都没有。 */
        public static final Source NONE = new Source(false, 0, false, 0);

        /** 是不是加热线圈。 */
        public final boolean coil;
        /** 线圈额定炉温（K）；不是线圈时 0。 */
        public final int coilTemperature;
        /** 是不是恒星热力容器。 */
        public final boolean containment;
        /** 容器等级 1/2/3；不是容器时 0。 */
        public final int containmentTier;

        Source(boolean coil, int coilTemperature, boolean containment, int containmentTier) {
            this.coil = coil;
            this.coilTemperature = coilTemperature;
            this.containment = containment;
            this.containmentTier = containmentTier;
        }

        /** 既不是线圈也不是容器。 */
        public boolean isEmpty() {
            return !coil && !containment;
        }

        /** 一行读数（tooltip 与证据行共用，避免两处口径漂移）。 */
        public String describe() {
            if (coil) {
                return "线圈·炉温 " + coilTemperature + "K";
            }
            if (containment) {
                return "恒星热力容器·等级 " + containmentTier;
            }
            return "（不是线圈也不是恒星热力容器）";
        }
    }

    /**
     * 读一件物品提供什么。<b>纯读，不改 stack，不碰世界</b>。
     *
     * <p>客户端也会调（GUI tooltip 里的活值）⇒ 只做注册表查询与字段读取，不做任何服务端动作。
     */
    @NotNull
    public static Source of(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Source.NONE;
        }
        final Block block = blockOf(stack);
        if (block == null) {
            return Source.NONE;
        }
        if (block instanceof CoilBlock coilBlock) {
            // coilType 是 CoilBlock 的公开字段（javap -p 实证：public ICoilType coilType;）。
            // 理论上 1.4.4 里恒非 null（构造器强塞），但这里仍然判空 —— 判空的代价是 0，
            // 不判空的代价是"某个第三方线圈让整台机器的 GUI 崩掉"。
            if (coilBlock.coilType != null) {
                return new Source(true, coilBlock.coilType.getCoilTemperature(), false, 0);
            }
            return Source.NONE;
        }
        final int tier = containmentTierOf(block);
        return tier > 0 ? new Source(false, 0, true, tier) : Source.NONE;
    }

    /**
     * 槽位过滤器：这件物品能不能放进恒星热力槽。
     *
     * <p>空槽恒放行 —— 与 {@code PrimordialModuleMachine#isMatterModuleStack} 同一条理由：
     * 否则玩家没法把放进错槽的东西取出来。
     */
    public static boolean isAccepted(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }
        final Block block = blockOf(stack);
        if (block == null) {
            return false;
        }
        return block instanceof CoilBlock || containmentTierOf(block) > 0;
    }

    /** 物品对应的方块；不是方块物品返回 null。 */
    @Nullable
    private static Block blockOf(@NotNull ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        }
        return null;
    }

    /** 按注册 id 判恒星热力容器等级；不是容器返回 0。 */
    public static int containmentTierOf(@Nullable Block block) {
        if (block == null) {
            return 0;
        }
        final ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        if (key == null) {
            return 0;
        }
        final String id = key.toString();
        if (SC_BASIC_ID.equals(id)) {
            return 1;
        }
        if (SC_ADVANCED_ID.equals(id)) {
            return 2;
        }
        if (SC_ULTIMATE_ID.equals(id)) {
            return 3;
        }
        return 0;
    }
}
