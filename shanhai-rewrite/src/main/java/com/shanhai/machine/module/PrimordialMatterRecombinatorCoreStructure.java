package com.shanhai.machine.module;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.data.GCyMBlocks;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.shanhai.common.compat.GtlAddCompat;

import net.minecraft.world.level.block.Block;

/**
 * 原初物质重组核心的<b>本体结构</b>。模块不是「无结构的挂件」——
 * 它有自己的控制器 + 一台独立的小多方块（{@code forge_of_the_antichrist_module.bin}，293 字节），
 * 结构不成立就永远不会 {@code isFormed()}，而 gtlcore 的默认 {@code isValidModule} 要求
 * {@code module.isFormed()} ⇒ <b>结构是回连的前置条件</b>。
 *
 * <h2>几何来源（不重写、不用 MultiBlockStructure 单例）</h2>
 * {@link GtlAddCompat#modulePattern()} —— 内部走 {@code StructureResourceLoader.loadFactoryPattern}
 * 的 <b>0 参重载</b>（模块那份 .bin 的轴约定是默认 {@code LEFT/UP/FRONT}，
 * <b>与主机的 3 参 {@code LEFT/DOWN/BACK} 不同，两份禁止互相套用</b>），
 * 每次返回全新实例，不会与 gtladditions 自家的符号表互踩。
 *
 * <h2>谓词映射（规格 §5.2，照抄上游 {@code PrimordialMatterRecombinatorCoreStructure} 的字母表）</h2>
 * <pre>
 * A C E F G H I J K L M N O P Q S → gtceu:bronze_machine_casing（GTBlocks.BRONZE_HULL）
 * B                              → 同上 ＋ 物品/流体 输入输出仓 ＋ INPUT_ENERGY ＋ OUTPUT_ENERGY
 * D                              → gtceu:industrial_steam_casing（GCyMBlocks.CASING_INDUSTRIAL_STEAM）
 * ~                              → 本模块控制器
 * </pre>
 *
 * <h2>两处<b>有意</b>的偏离，务必知情（不是抄漏）</h2>
 * <ol>
 *   <li><b>映射字母表取 A–S 超集，而不是只映射 .bin 里实际出现的 6 个</b>（上游同样如此）：
 *       未在图案中出现的字母映射起来零成本，但<b>图案里出现却没 where 的符号会让
 *       {@code build()} 抛异常</b>；未来 gtladditions 换 .bin 引入新字母时，超集映射不会炸。</li>
 *   <li><b>B 上额外挂 {@code INPUT_ENERGY}</b>（上游没有这一项）。本模块挂的是 GTCEu <b>原版电机器
 *       配方类型</b>，没有能源仓就一格电都进不来 ⇒ 验收 A5「模块能跑配方」永远不可能通过。
 *       这是阶段 1 闭环的必要条件，且是<b>纯增量</b>（不减少任何上游允许的方块）。</li>
 *   <li><b>B 上再挂 {@code OUTPUT_ENERGY}</b>（2026-09-24 追加，本轮任务 B）。发电模块
 *       （{@code ShanhaiRecipeTypes.PRIMORDIAL_POWER_GENERATOR}，{@code setEUIO(IO.OUT)}）
 *       的电<b>只能</b>经 {@code PartAbility.OUTPUT_ENERGY} 仓（dynamo）送出去：
 *       {@code WorkableElectricMultiblockMachine#getEnergyContainer()} 只从<b>部件（仓）</b>
 *       收集 {@code IEnergyContainer}，一个仓都没有时容器列表为空。
 *       GTCEu 原生发电多方块的唯一范本是 {@code GTMachines.LARGE_COMBUSTION_ENGINE}
 *       （{@code .generator(true)} ＋ {@code .where('D', Predicates.ability(PartAbility.OUTPUT_ENERGY, …))}）。
 *       加之前，玩家往 {@code B} 位放能源输出仓会被谓词拒收 ⇒ 图案判定失败 ⇒ tooltip 显示
 *       {@code gtceu.top.invalid_structure}（「结构不完整」）。<b>纯增量</b>：只增加允许的方块，
 *       不减少任何原有允许项。</li>
 * </ol>
 *
 * <h2>⚠️ 诚实边界：本类被 <b>24 台模块共用</b>（{@code ModuleRegistry.SPECS} 全部指向本方法）</h2>
 * 上面两条「纯增量」结论<b>是从 {@code TraceabilityPredicate.or(...)} 的并集语义推出的</b>
 * （加一项不会让原本匹配的方块变成不匹配），
 * <b>但「已建成的 23 台结构加这一项后不会被重判失效」我【没有实测过】</b>
 * —— 严格说这是 [推断]，实机判据是：下一次进游戏时已建成的模块是否仍然显示已成型。
 */
public final class PrimordialMatterRecombinatorCoreStructure {

    private PrimordialMatterRecombinatorCoreStructure() {}

    public static BlockPattern createPattern(MultiblockMachineDefinition definition) {
        final Block bronzeCasing = GTBlocks.BRONZE_HULL.get();                 // gtceu:bronze_machine_casing
        final Block industrialSteam = GCyMBlocks.CASING_INDUSTRIAL_STEAM.get(); // gtceu:industrial_steam_casing

        TraceabilityPredicate shell = Predicates.blocks(bronzeCasing);
        TraceabilityPredicate shellWithHatches = shell
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(1))
                // ↓ 阶段 1 追加：原版电配方类型必须有能源仓（见类注释「有意偏离 2」）
                .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1))
                // ↓ 2026-09-24 追加（任务 B）：发电模块必须有【能源输出仓】(dynamo)，
                //   见类注释「有意偏离 3」—— 没有它就是「结构不完整」且一格电都送不出去。
                .or(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setPreviewCount(1));

        return GtlAddCompat.modulePattern()
                .where('A', shell)
                .where('B', shellWithHatches)
                .where('C', shell)
                .where('D', Predicates.blocks(industrialSteam))
                .where('E', shell)
                .where('F', shell)
                .where('G', shell)
                .where('H', shell)
                .where('I', shell)
                .where('J', shell)
                .where('K', shell)
                .where('L', shell)
                .where('M', shell)
                .where('N', shell)
                .where('O', shell)
                .where('P', shell)
                .where('Q', shell)
                .where('S', shell)
                .where('~', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .build();
    }
}
