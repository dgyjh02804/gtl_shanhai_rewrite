package com.shanhai.item;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.shanhai.fluid.ShanhaiFluids;
import com.shanhai.machine.ShanhaiMachines;
import com.shanhai.machine.module.ModuleRegistry;
import com.shanhai.machine.wildcard.ShanhaiWildcardMachines;
import com.shanhai.registry.ShanhaiRegistration;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

/**
 * 本 mod 自己的创造模式物品栏（{@code shanhai}）—— 18 个物品 + B1a 的 131 个私货物品 + 主机控制器的归属。
 *
 * <h2>🔴 为什么必须有它（不是"好看而已"）</h2>
 * <b>JEI 的物品表是从创造模式物品栏建的。</b>取证（反编译 JEI 15.49.0.188 的
 * {@code mezz.jei.library.plugins.vanilla.ingredients.ItemStackListFactory#create}）：
 * <pre>
 *   for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
 *       if (tab.getType() != CreativeModeTab.Type.CATEGORY) continue;
 *       tab.buildContents(displayParameters);
 *       ... addFromTab(tab.getDisplayItems()) / addFromTab(tab.getSearchTabDisplayItems()) ...
 *   }
 *   if (showHidden) { addItemsFromRegistries(...); }   // ← 只有开了"显示隐藏物品"才会兜底扫注册表
 * </pre>
 * 也就是说：<b>不在任何 CATEGORY 物品栏里的物品，JEI 搜不到</b>
 * （除非用户在 JEI 配置里打开了 showHiddenIngredients，默认关闭）。
 * 本 mod 之前 18 个物品全部没有归属栏 ⇒ 创造栏看不见、JEI 也搜不到。
 *
 * <h2>写法出处（照抄 GTCEu 自己的 GTCreativeModeTabs，不发明 API）</h2>
 * 反编译 {@code com.gregtechceu.gtceu.common.data.GTCreativeModeTabs:52}：
 * <pre>
 *   GTRegistration.REGISTRATE
 *       .defaultCreativeTab("material_fluid", builder -&gt; builder
 *           .displayItems(new RegistrateDisplayItemsGenerator("material_fluid", GTRegistration.REGISTRATE))
 *           .icon(() -&gt; GTItems.FLUID_CELL.asStack())
 *           .title(GTRegistration.REGISTRATE.addLang("itemGroup", GTCEu.id("material_fluid"), "...")))
 *       .register();
 * </pre>
 * 本类沿用同一入口 {@code GTRegistrate.defaultCreativeTab(String, Consumer&lt;CreativeModeTab.Builder&gt;)}
 * （继承自 {@code AbstractRegistrate}，javap 已确认签名；GTRegistrate 另覆写了
 * {@code protected createCreativeModeTab(P, String, Consumer)}，用来做 tab 的注册表绑定）。
 * <p><b>与 GTCEu 的差别只有一处、且是故意的</b>：GTCEu 用
 * {@code RegistrateDisplayItemsGenerator} 依赖 {@code GTRegistrate.isInCreativeTab()}
 * （内建 {@code TAB_LOOKUP}），本 mod 的物品是自己列的，直接<b>显式列举</b>更确定 ——
 * 不依赖"注册时 currentTab 恰好是这个栏"这条隐式时序。
 *
 * <h2>lang</h2>
 * 标题走 {@code Component.translatable("itemGroup.shanhai.shanhai")}，中文名在
 * {@code assets/shanhai/lang/zh_cn.json}（与 18 个物品名的存放位置一致）。
 */
public final class ShanhaiCreativeModeTabs {

    /** 物品栏标题的语言键；条目在 {@code assets/shanhai/lang/zh_cn.json}。 */
    public static final String SHANHAI_TAB_LANG_KEY = "itemGroup.shanhai.shanhai";

    /**
     * 本 mod 唯一的创造模式物品栏。静态初始化即完成 Registrate 入列。
     * <p>⚠️ 本栏的 {@code displayItems} 生成器在<b>构建章节目录时</b>才被调用
     * （客户端打开创造栏 / JEI 启动 / 服务端建 {@code CreativeModeTab} 内容），
     * 所以 lambda 里引用 {@link ShanhaiItems}、{@link ShanhaiMachines} 都是安全的
     * —— 那时两类早已初始化完毕。
     */
    public static final RegistryEntry<CreativeModeTab> SHANHAI = ShanhaiRegistration.REGISTRATE
            .defaultCreativeTab("shanhai", builder -> builder
                    .title(Component.translatable(SHANHAI_TAB_LANG_KEY))
                    // 图标用"原初引擎核心"：它是本 mod 最有辨识度的一个。
                    // 用 Supplier 形式（CreativeModeTab.Builder.icon），取栈时机晚于注册时机。
                    .icon(() -> ShanhaiItems.PRIMORDIAL_ENGINE_CORE.asStack())
                    .displayItems(ShanhaiCreativeModeTabs::fill))
            .register();

    /**
     * 触发本类静态初始化（进而注册物品栏）。必须由
     * {@code com.shanhai.registry.ShanhaiRegistry#init()} 调用，且顺序上先于
     * {@code ShanhaiItems.init()}（虽然本类不依赖那个顺序，但先建栏后放东西更直观）。
     */
    public static void init() {
        // 静态字段初始化即注册；本方法只为「显式触达类」而存在。
    }

    /**
     * 17 个物质模块（等级 1..17，顺序 = {@link ShanhaiItems} 的字段声明顺序）
     * + 原初引擎核心 + 主机控制器。
     *
     * <p><b>为什么主机也要放进来</b>：主机物品 {@code shanhai:primordial_omega_engine}
     * 由 {@code MultiblockMachineBuilder} 注册，它的物品同样没有别的归属栏
     * ⇒ 不放进来，JEI 里就<b>搜不到主机</b>（同一条 JEI 机制）。
     * 它用 {@code != null} 兜底：注册失败时宁可少一格，也绝不让生成器抛异常
     * —— JEI 对抛异常的栏是<b>整栏丢弃</b>（{@code ItemStackListFactory} 里的
     * {@code catch (RuntimeException e) { continue; }}），那会连带 18 个物品一起搜不到。
     */
    private static void fill(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        // ─────────────────────────────────────────────────────────────
        // 段A · 老山海【KubeJS 栏】的顺序 = 山海的物品注册.js 的脚本注册序
        //   （KubeJSCreativeTabs 只注册一个 kubejs:tab，displayItems 迭代 RegistryInfo.ITEM，
        //     即物品创建顺序 ⇒ 老山海 KubeJS 栏的显示序 = 脚本注册序）
        // ─────────────────────────────────────────────────────────────
        output.accept(ShanhaiItems.DISHANHAI.asStack());
        output.accept(ShanhaiItems.COSMIC_PROBE_MK.asStack());
        output.accept(ShanhaiItems.GOD_FORGE_MOD.asStack());
        output.accept(ShanhaiItems.GATE_AND_BRIDG.asStack());
        output.accept(ShanhaiItems.BRIDGE_AND_GATE.asStack());
        output.accept(ShanhaiItems.BIG_TEAR.asStack());
        output.accept(ShanhaiItems.CSJ.asStack());
        output.accept(ShanhaiItems.TIME_REVERSAL_PROTOCOL.asStack());
        output.accept(ShanhaiItems.FOOD.asStack());
        output.accept(ShanhaiItems.PIGGY.asStack());
        output.accept(ShanhaiItems.FISHBIG_SHARDS.asStack());
        output.accept(ShanhaiItems.COLLAPSE_TEAR.asStack());
        output.accept(ShanhaiItems.HALO_END.asStack());
        output.accept(ShanhaiItems.TAIXU_DUST.asStack());
        output.accept(ShanhaiItems.TAIXU_CRYSTAL_CORE.asStack());
        // 🟢 2026-09-26 新增（世线晶核）—— 必须放进创造栏，否则 JEI 里搜不到
        output.accept(ShanhaiItems.WORLDLINE_CRYSTAL_CORE.asStack());
        output.accept(ShanhaiItems.TAIXU_LIQUID_DROPLET.asStack());
        output.accept(ShanhaiItems.IDEAL_ASHES.asStack());
        output.accept(ShanhaiItems.BEYOND_TAIXU_THREAD.asStack());
        output.accept(ShanhaiItems.FINALITY_CERTIFICATE.asStack());
        output.accept(ShanhaiItems.MATTER_SINGULARITY.asStack());

        // ─── 17 个物质模块：老脚本把它们插在私货物品中间（L970–L1135），等级 1..17 连号 ───
        // 🔴 2026-10-01（用户点单「给物质流安装物质模块的顺序排序」）：
        //    这 17 行**改成由 {@link #matterRows()} 单表生成**，与下面那 14 个物质流桶**共用同一张表** ⇒
        //    「物质流按物质模块顺序排」由构造保证，不可能分叉。**顺序本身一个都没动**（逐行与旧版相同）。
        for (MatterRow row : matterRows()) {
            output.accept(row.module().asStack());
        }
        output.accept(ShanhaiItems.PRIMORDIAL_DIVERGENCE_HEART.asStack());
        output.accept(ShanhaiItems.PRIMORDIAL_ENGINE_CORE.asStack());

        // ─── 世线残片 1..7（L1171–L1232）───
        output.accept(ShanhaiItems.THREAD_SHARD_1.asStack());
        output.accept(ShanhaiItems.THREAD_SHARD_2.asStack());
        output.accept(ShanhaiItems.THREAD_SHARD_3.asStack());
        output.accept(ShanhaiItems.THREAD_SHARD_4.asStack());
        output.accept(ShanhaiItems.THREAD_SHARD_5.asStack());
        output.accept(ShanhaiItems.THREAD_SHARD_6.asStack());
        output.accept(ShanhaiItems.THREAD_SHARD_7.asStack());
        output.accept(ShanhaiItems.UNIVERSAL_PARALLEL_CORE.asStack());
        output.accept(ShanhaiItems.JUDGMENT_LIMITER.asStack());
        output.accept(ShanhaiItems.PROLOGUE_OF_THE_END.asStack());
        output.accept(ShanhaiItems.UNIVERSAL_PARALLEL_OVERDRIVER.asStack());

        // ─── 世线电路板 16 档（L1288–L1408）：老脚本原序，不是"按等级重排" ───
        output.accept(ShanhaiItems.WL_BOARD_ULV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_LV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_MV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_HV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_EV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_IV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_LUV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_ZPM.asStack());
        output.accept(ShanhaiItems.WL_BOARD_UV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_UHV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_UEV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_UIV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_UXV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_OPV.asStack());
        output.accept(ShanhaiItems.WL_BOARD_MAX.asStack());
        output.accept(ShanhaiItems.WL_BOARD_ETERNAL.asStack());
        output.accept(ShanhaiItems.GRAVITATIONAL_MEDIUM.asStack());
        output.accept(ShanhaiItems.GRAVITATIONAL_ANTENNA.asStack());
        output.accept(ShanhaiItems.GRAVITATIONAL_VIBRATION_STRING.asStack());
        output.accept(ShanhaiItems.ARTIFICIAL_NEUTRON_STAR.asStack());
        output.accept(ShanhaiItems.STRONG_INTERACTION_DROPLET.asStack());

        // ─── 世线蚀刻矩阵 4 档：老脚本用 helper regWEM 注册（L1490–L1493）───
        output.accept(ShanhaiItems.WEM_1.asStack());
        output.accept(ShanhaiItems.WEM_2.asStack());
        output.accept(ShanhaiItems.WEM_3.asStack());
        output.accept(ShanhaiItems.WEM_4.asStack());
        output.accept(ShanhaiItems.HXSP.asStack());
        output.accept(ShanhaiItems.CSHX.asStack());
        output.accept(ShanhaiItems.ZWF.asStack());
        output.accept(ShanhaiItems.SOC.asStack());
        output.accept(ShanhaiItems.PLATINUM_GOD_PROOF.asStack());
        output.accept(ShanhaiItems.DARK_ENERGY_MULTIPLIER.asStack());
        output.accept(ShanhaiItems.BLUE_ALIEN.asStack());
        output.accept(ShanhaiItems.LONG_ZUI.asStack());
        output.accept(ShanhaiItems.KU_MING_YUAN_YANG.asStack());
        output.accept(ShanhaiItems.GRAVITATIONAL_LENS.asStack());
        output.accept(ShanhaiItems.ANNIHILATION_CORE.asStack());
        output.accept(ShanhaiItems.PRIMORDIAL_WORLDLINE_SEED.asStack());
        output.accept(ShanhaiItems.PRIMORDIAL_PARALLEL_PARTICLE.asStack());
        output.accept(ShanhaiItems.DIMENSIONAL_WORLDLINE_FRAGMENT.asStack());
        output.accept(ShanhaiItems.WORLDLINE_RESIDUAL_FRAGMENT.asStack());
        output.accept(ShanhaiItems.WORLDLINE_DIVERGENT_CORE.asStack());
        output.accept(ShanhaiItems.WORLDLINE_BOUNDLESS_SINGULARITY.asStack());
        output.accept(ShanhaiItems.WORLDLINE_IMAGINARY_STRING.asStack());
        output.accept(ShanhaiItems.WORLDLINE_GENESIS_EMBRYO.asStack());
        output.accept(ShanhaiItems.TEST_DYNAMIC_TEXT.asStack());
        output.accept(ShanhaiItems.WANXIANG_CORE.asStack());
        output.accept(ShanhaiItems.GENESIS_SHARD.asStack());
        output.accept(ShanhaiItems.NOVA_CATALYST.asStack());
        output.accept(ShanhaiItems.REALITY_CORE.asStack());
        output.accept(ShanhaiItems.COSMIC_DUST.asStack());
        output.accept(ShanhaiItems.DIMENSIONAL_MATRIX.asStack());
        output.accept(ShanhaiItems.DIMENSIONAL_FRAME.asStack());
        output.accept(ShanhaiItems.SINGULARITY_RING.asStack());
        output.accept(ShanhaiItems.FIRST_LIGHT.asStack());
        output.accept(ShanhaiItems.NAVIGATE_PRISM.asStack());
        output.accept(ShanhaiItems.LIGHT_VOYAGE.asStack());
        output.accept(ShanhaiItems.STAR_SPARK.asStack());
        output.accept(ShanhaiItems.BLUE_SON.asStack());
        output.accept(ShanhaiItems.PHOTON.asStack());
        output.accept(ShanhaiItems.CENTRAL_FINITE_CURVE.asStack());
        output.accept(ShanhaiItems.TEST_ITEM.asStack());

        // ─── 黑洞/夸克释放催化剂族（L1945–L2027）───
        output.accept(ShanhaiItems.BHD_HYPER_SEED.asStack());
        output.accept(ShanhaiItems.BHD_COLLAPSER.asStack());
        output.accept(ShanhaiItems.HYPERDIMENSIONAL_CALIBRATION_MATRIX.asStack());
        output.accept(ShanhaiItems.CASING_EMPTY_QUARK_EMISSION_CATALYST.asStack());
        output.accept(ShanhaiItems.UP_QUARK_EMISSION_CATALYST.asStack());
        output.accept(ShanhaiItems.DOWN_QUARK_EMISSION_CATALYST.asStack());
        output.accept(ShanhaiItems.STRANGE_QUARK_EMISSION_CATALYST.asStack());
        output.accept(ShanhaiItems.CHARM_QUARK_EMISSION_CATALYST.asStack());
        output.accept(ShanhaiItems.BOTTOM_QUARK_EMISSION_CATALYST.asStack());
        output.accept(ShanhaiItems.TOP_QUARK_EMISSION_CATALYST.asStack());
        output.accept(ShanhaiItems.MISALIGNED_QUARK_EMISSION_CATALYST.asStack());

        // ─── 离子 / 夸克 / 粒子族（L2049–L2294）───
        output.accept(ShanhaiItems.HYDROGEN_ION.asStack());
        output.accept(ShanhaiItems.HELIUM_ION.asStack());
        output.accept(ShanhaiItems.GRAVITON.asStack());
        output.accept(ShanhaiItems.UP_QUARK.asStack());
        output.accept(ShanhaiItems.DOWN_QUARK.asStack());
        output.accept(ShanhaiItems.CHARM_QUARK.asStack());
        output.accept(ShanhaiItems.STRANGE_QUARK.asStack());
        output.accept(ShanhaiItems.BOTTOM_QUARK.asStack());
        output.accept(ShanhaiItems.TOP_QUARK.asStack());
        output.accept(ShanhaiItems.ELECTRON.asStack());
        output.accept(ShanhaiItems.ELECTRON_NEUTRINO.asStack());
        output.accept(ShanhaiItems.MUON.asStack());
        output.accept(ShanhaiItems.MUON_NEUTRINO.asStack());
        output.accept(ShanhaiItems.TAU.asStack());
        output.accept(ShanhaiItems.TAU_NEUTRINO.asStack());
        output.accept(ShanhaiItems.GLUON.asStack());
        output.accept(ShanhaiItems.PHOTON_RAINBOW.asStack());
        output.accept(ShanhaiItems.Z_BOSON.asStack());
        output.accept(ShanhaiItems.W_BOSON.asStack());
        output.accept(ShanhaiItems.HIGGS_BOSON.asStack());
        output.accept(ShanhaiItems.PROTON.asStack());
        output.accept(ShanhaiItems.NEUTRON.asStack());
        output.accept(ShanhaiItems.LAMBDA_PARTICLE.asStack());
        output.accept(ShanhaiItems.OMEGA_PARTICLE.asStack());
        output.accept(ShanhaiItems.PION.asStack());
        output.accept(ShanhaiItems.ETA_MESON.asStack());
        output.accept(ShanhaiItems.UNKNOWN_PARTICLE.asStack());

        // ─── 25 个桶 ───
        //     ⛔ 旧序（作废，留档）：… matter_fluid_entry, foundation, basic, virtual, transmutation,
        //        darkstar, advanced, transition, zero, **ascension, transcend, peak**, eternal, ultimate …
        //     🔴 2026-10-01：14 个**物质流桶**改为按【物质模块的顺序】排（用户原话「给物质流安装物质模块的
        //        顺序排序（jei里面的顺序，还有他们对应的桶）」）⇒ 从 {@link #matterRows()} 同一张表生成。
        //        实测差异只有 3 个：旧序里 `ascension(升维) → transcend(超限) → peak(巅峰)`，
        //        而物质模块顺序是 `巅峰(apex) → 升维 → 超限` ⇒ 现在**巅峰物质流桶**提前到升维之前。
        //     其余 11 个非物质流桶**保持原有相对顺序**（只把物质流那一段整体挪到原位）。
        acceptBucket(output, "zero_point_energy");
        acceptBucket(output, "light");
        acceptBucket(output, "liquid_ending");
        for (MatterRow row : matterRows()) {
            if (row.fluidId() != null) {
                acceptBucket(output, row.fluidId());
            }
        }
        acceptBucket(output, "primal_chaos");
        acceptBucket(output, "dimensional_fabric");
        acceptBucket(output, "causal_essence");
        acceptBucket(output, "stabilized_eternity");
        acceptBucket(output, "chaos_fluid");
        acceptBucket(output, "wl_catalyst");
        acceptBucket(output, "universal_coolant");
        acceptBucket(output, "spacetime");

        // ─────────────────────────────────────────────────────────────
        // 段B · 老山海【模组栏「山海的神人私货」】的顺序 = Registrate 注册序
        //   （老栏 DShanhaiCreativeModeTabs 遍历 REGISTRATE.getAll(Registries.ITEM)
        //     + isInCreativeTab 过滤 ⇒ 显示序 = 注册插入序）
        // ─────────────────────────────────────────────────────────────
        output.accept(ShanhaiItems.SPACETIME_WAVE_MATRIX.asStack());
        MultiblockMachineDefinition engine = ShanhaiMachines.primordialOmegaEngine();
        if (engine != null) {
            output.accept(engine.asStack());
        }
        // 超级通配符ME样板总成（2026-10-01 用户点单）—— 仓室，与主机/模块一样只能靠自己这台栏位
        // 才看得见（GTCEu 的 MachineBuilder 不会自动塞进任何创造栏；缺了就是"物品在 JEI 里也搜不到"）。
        MachineDefinition superWildcardBuffer = ShanhaiWildcardMachines.superWildcardPatternBuffer();
        if (superWildcardBuffer != null) {
            output.accept(superWildcardBuffer.asStack());
        }
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_VOID_INDUCTION_ARMATURE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_BIOLOGICAL_CORE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_CHAOTIC_EPHEMERAL_DECONSTRUCTION_CRYSTALLIZATION_FURNACE);
        output.accept(ShanhaiItems.WORLD_LINE_STRIPPING_OSCILLATION_GENERATOR.asStack());
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_MATTER_RECOMBINATOR_CORE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_CAUSAL_WEAVING_MATRIX);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_SINGULARITY_INVERSION_CORE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_WORLD_FRAGMENTS_COLLECTOR);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_ANTI_ENTROPY_CONDENSATION_CORE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_DIVERGENCE_GENERATOR);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_MATTER_CASTER);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_ASSEMBLY_LINE_MODULE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_CRITICAL_PROCESSING_MODULE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_MULTIDIMENSIONAL_IMPLOSION_CORE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_SUPERCRITICAL_MATTER_GENERATION_CORE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_COSMIC_REACTOR);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_MOLECULAR_RIFT_CORE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_TIANQIONG_ASSEMBLY_CORE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_ETERNAL_SMELTING_FURNACE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_WORLDLINE_TRAVERSAL_MATRIX);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_QUANTUM_DISTORTION_MATRIX);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_SHAOGUANG_AGGREGATION_CORE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_WEIYANG_RECONSTRUCTION_MODULE);
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_ENGRAVING_MODULE);
        output.accept(ShanhaiItems.BIG_TAG_FILTER_STOCK_BUS.asStack());
        output.accept(ShanhaiItems.MAINTENANCE_HATCH.asStack());
        output.accept(ShanhaiItems.NEBULA_SIPHON.asStack());
        acceptIfPresent(output, ModuleRegistry.TAIXU_SMELTING_FURNACE);
        output.accept(ShanhaiItems.WORLDLINE_CRACKING_HUB.asStack());
        output.accept(ShanhaiItems.ZERO_PHOTON_CONDENSER.asStack());
        output.accept(ShanhaiItems.BLACK_HOLE_CONTAINMENT.asStack());
        output.accept(ShanhaiItems.TIANJIE_NAVIGATION_TOWER.asStack());
        output.accept(ShanhaiItems.ULV_ZERO_POINT_CONVERSION.asStack());
        output.accept(ShanhaiItems.LV_ZERO_POINT_CONVERSION.asStack());
        output.accept(ShanhaiItems.MV_ZERO_POINT_CONVERSION.asStack());
        output.accept(ShanhaiItems.ULV_PHOTON_SIPHON.asStack());
        output.accept(ShanhaiItems.LV_PHOTON_SIPHON.asStack());
        output.accept(ShanhaiItems.MV_PHOTON_SIPHON.asStack());

        // ─── 末尾追加段：老栏在遍历之后用 output.accept 追加的 Forge RegistryObject 物品 ───
        output.accept(ShanhaiItems.GRAVITON_SHARD.asStack());
        output.accept(ShanhaiItems.GUIDE_BOOK.asStack());

        // ─── 老山海没有这台机器 ⇒ 置尾（不改动老顺序）───
        acceptIfPresent(output, ModuleRegistry.PRIMORDIAL_DEBUG_MODULE);
    }


    /**
     * 🔴 「物质模块 → 它对应的物质流」的<b>唯一真源</b>（2026-10-01 新增，用户点单）。
     *
     * <h2>1. 这张表的顺序 = 【物质模块在 JEI 里的显示顺序】= 等级 1..17</h2>
     * 「物质模块的顺序」的权威来源是<b>本表</b>，而本表的顺序就是 {@code fill()} 里那 17 行的原顺序
     * （老脚本 L970–L1135 的注册序，未改一个位置）。<b>两条独立旁证</b>证明它就是等级序：
     * <ol>
     *   <li>{@code ShanhaiConcurrencyTables.standardParallelTable()} 的并行上限值，
     *       按本表顺序<b>严格单调递增</b>（128 → 256 → … → {@code Long.MAX_VALUE}）
     *       ⇒ 「等级」与「本表顺序」同序；</li>
     *   <li>用户原话把「jei 里面的顺序」直接当成模块顺序 ⇒ 他看到的就是创造栏这一列，
     *       而 {@code ShanhaiCreativeModeTabs} 的类注释已实证：<b>JEI 物品表是从创造栏 displayItems 建的</b>。</li>
     * </ol>
     *
     * <h2>2. 🔴 为什么模块与流体必须共用这一张表</h2>
     * 用户要的是「物质流按物质模块的顺序排」。若模块那份顺序与流体那份顺序各写一遍，
     * 将来加一台模块就<b>必然</b>出现两份顺序分叉（本工程血账：「同一个数被两处各写一份，必然漂移」）
     * ⇒ 本方法返回的<b>同一张表</b>同时喂给 ①模块那一段 ②物质流桶那一段，顺序由构造保证。
     *
     * <h2>3. 配对判据</h2>
     * 中文名字面呼应（入门↔入门、基础↔基础、推演↔推演、虚像↔虚像、嬗变↔嬗变、暗星↔暗星、
     * 重组↔重组、虚数跃迁↔虚数物质跃迁重塑、归零↔归零、巅峰↔巅峰、升维↔升维、超限↔超限、
     * 永恒↔永恒、创造↔物质创造）：<b>14 对 14，无歧义、无一对多</b>。
     * 17 台模块里另有 3 台<b>没有对应流体</b>（混沌 / 现实锚点 / 创始现实修改）⇒ {@code fluidId = null}，
     * <b>不编造</b>一条不存在的流体。
     *
     * <h2>4. 为什么是「方法」而不是「静态字段」</h2>
     * 静态字段会在 {@code ShanhaiCreativeModeTabs} 的类初始化期就去触碰 {@code ShanhaiItems} 的静态字段，
     * 从而把物品注册的时机提前到「创造栏注册之前」—— <b>那是一次没人要求过的初始化顺序变更</b>。
     * 写成方法 ⇒ 只在 {@code fill()} 被调用时（那时物品早已注册完）取表，<b>零时序影响</b>。
     */
    private static MatterRow[] matterRows() {
        return new MatterRow[] {
                new MatterRow(ShanhaiItems.INTRODUCTORY_MATERIAL_MODULE, "matter_fluid_entry"),
                new MatterRow(ShanhaiItems.BASIC_MATERIAL_MODULE, "matter_fluid_foundation"),
                new MatterRow(ShanhaiItems.MATERIAL_DEDUCTION_MODULE, "matter_fluid_basic"),
                new MatterRow(ShanhaiItems.VIRTUAL_IMAGE_MATERIAL_MODULE, "matter_fluid_virtual"),
                new MatterRow(ShanhaiItems.TRANSFORMATION_MATERIAL_MODULE, "matter_fluid_transmutation"),
                new MatterRow(ShanhaiItems.DARK_STAR_MATERIAL_MODULE, "matter_fluid_darkstar"),
                new MatterRow(ShanhaiItems.MATERIAL_RECOMBINATION_MODULE, "matter_fluid_advanced"),
                new MatterRow(ShanhaiItems.IMAGINARY_MATERIAL_TRANSITION_REMOLDING_MODULE, "matter_fluid_transition"),
                new MatterRow(ShanhaiItems.ZEROING_MATERIAL_MODULE, "matter_fluid_zero"),
                new MatterRow(ShanhaiItems.APEX_MATERIAL_MODULE, "matter_fluid_peak"),
                new MatterRow(ShanhaiItems.DIMENSIONAL_ASCENSION_MATERIAL_MODULE, "matter_fluid_ascension"),
                new MatterRow(ShanhaiItems.TRANSFINITE_MATERIAL_MODULE, "matter_fluid_transcend"),
                new MatterRow(ShanhaiItems.CHAOS_MATERIAL_MODULE, null),
                new MatterRow(ShanhaiItems.ETERNAL_MATERIAL_MODULE, "matter_fluid_eternal"),
                new MatterRow(ShanhaiItems.MATERIAL_CREATION_MODULE, "matter_fluid_ultimate"),
                new MatterRow(ShanhaiItems.REALITY_ANCHOR_MODULE, null),
                new MatterRow(ShanhaiItems.GENESIS_REALITY_MODIFICATION_MODULE, null),
        };
    }

    /** 一行：一台物质模块 ＋ 它对应的物质流 id（{@code null} = 该模块没有对应流体）。 */
    private record MatterRow(ItemEntry<Item> module, String fluidId) {}

    /**
     * 把一个桶放进创造栏。桶句柄是懒解析的（{@link ShanhaiFluids#bucketStack(String)}），
     * 解析不到就<b>保持少一格</b>，而不是把 {@code ItemStack.EMPTY} 塞进去
     * —— 与 {@link #acceptIfPresent} 同一条纪律（宁可少一格，不让生成器抛异常）。
     */
    private static void acceptBucket(CreativeModeTab.Output output, String fluidId) {
        net.minecraft.world.item.ItemStack stack = ShanhaiFluids.bucketStack(fluidId);
        if (!stack.isEmpty()) {
            output.accept(stack);
        }
    }

    /** 机器句柄非 null 才入列（注册失败时保持"少一格"，而不是让整栏崩掉）。 */
    private static void acceptIfPresent(CreativeModeTab.Output output, MultiblockMachineDefinition definition) {
        if (definition != null) {
            output.accept(definition.asStack());
        }
    }

    private ShanhaiCreativeModeTabs() {}
}
