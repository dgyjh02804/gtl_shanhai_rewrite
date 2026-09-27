package com.shanhai.machine.engine;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.data.GCyMBlocks;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.shanhai.common.compat.GtlAddCompat;
import com.shanhai.machine.ShanhaiMachines;

/**
 * 青铜神锻（原始终焉引擎）主机的结构图案。
 *
 * <h2>几何来源：复用 gtladditions 的 .bin（不重写、不用 MultiBlockStructure 单例）</h2>
 * 几何取自 {@link GtlAddCompat#hostPattern()} —— 它内部走
 * {@code StructureResourceLoader.loadFactoryPattern(..., LEFT, DOWN, BACK)}，每次返回
 * <b>全新</b>可安全改写的 {@link com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern}。
 * <b>严禁</b>改用 {@code MultiBlockStructure.getFORGE_OF_THE_ANTICHRIST()}：那是 Lazy 共享可变单例，
 * {@code where()} 原地改共享符号表，会与 gtladditions 自家 6 台机器互踩（详见 GtlAddCompat 类注释）。
 *
 * <h2>谓词映射（规格 §4.1 的表，逐条对应）</h2>
 * <pre>
 * A 外圈外壳    gtceu:steam_machine_casing      → GTBlocks.CASING_BRONZE_BRICKS  ＋ 全仓室能力
 * B 外圈装饰    gtceu:bronze_machine_casing     → GTBlocks.BRONZE_HULL
 * C 核心圈内层  gtceu:bronze_pipe_casing        → GTBlocks.CASING_BRONZE_PIPE
 * D 背面结构    gtceu:firebricks                → GTBlocks.CASING_PRIMITIVE_BRICKS
 * E 侧面        gtceu:industrial_steam_casing   → GCyMBlocks.CASING_INDUSTRIAL_STEAM
 * F 核心圈外圈  gtceu:coke_oven_bricks          → GTBlocks.CASING_COKE_BRICKS（不挂仓室）
 * G 内部填充    gtceu:bronze_pipe_casing        → GTBlocks.CASING_BRONZE_PIPE
 * H 内部填充    gtceu:firebricks                → GTBlocks.CASING_PRIMITIVE_BRICKS
 * I 内部填充    gtceu:bronze_pipe_casing        → GTBlocks.CASING_BRONZE_PIPE
 * J 16 模块位   kubejs:steam_assembly_block（空槽占位）∪ 模块控制器方块
 * K 内部填充    gtceu:bronze_brick_casing       → GTBlocks.BRONZE_BRICKS_HULL
 * ~ 控制器      本 mod 的 shanhai:primordial_omega_engine 方块
 * </pre>
 * <p>⛔ <b>【2026-09-22 作废 · 本轮 air 版原文照留，一字未改】</b>本表 J 行曾短暂写作：
 * <pre>
 * J 16 模块位   空气 ∪ kubejs:steam_assembly_block（旧空槽占位，保留兼容）∪ 模块控制器方块
 *               ↑ 🔴 2026-09-22 起允许空气（用户裁决「拆一个模块不该停全机」）；旧行原文：
 *                 「kubejs:steam_assembly_block（空槽占位）∪ 模块控制器方块」，作废留档。
 * </pre>
 * <b>作废原因</b>：用户 2026-09-22 裁决 A 后又撤回
 * （原话「<b>我后悔了，我返回之前那个替换或者拆除模块就停止全机的决定</b>」）
 * ⇒ 恢复「拆 / 换模块即停全机」的原行为。该放宽的代码侧落点见
 * {@link com.shanhai.machine.ShanhaiMachines#moduleSlotPredicate()}（那里也有完整留档）。
 * 🔴 <b>该改动从未部署、从未进游戏。</b>
 * ⚠ 两个反直觉映射（「按 id 字符串查表必错」的铁证）：
 * {@code steam_machine_casing → CASING_BRONZE_BRICKS}、{@code bronze_machine_casing → BRONZE_HULL}。
 *
 * <h2>为什么只映射 A–K，不做 L–S 的「超集防御」</h2>
 * {@code FactoryBlockPattern.build()} 只按格子取符号，{@code checkMissingPredicates()} 只对
 * 「图案里出现但没 where」的符号抛异常。多映射没用的字母本身无害，但把它们映射成
 * {@code any()} 会<b>静默接受错误方块</b>；而不映射则会在符号集真的变了时
 * <b>立刻抛 {@code IllegalStateException} 并列出缺哪个字母</b>。本项目一律选「响亮地失败」。
 * （主机 .bin 的符号集 A–K/~/空格 已在 4 个 gtladditions 变体上实测逐字节相同。）
 */
public final class PrimordialOmegaEngineStructure {

    private PrimordialOmegaEngineStructure() {}

    public static BlockPattern createPattern(MultiblockMachineDefinition definition) {
        // A：外圈外壳 —— 蒸汽机器外壳 + 全仓室挂载（仓室贴附点）
        TraceabilityPredicate aPred = Predicates.blocks(GTBlocks.CASING_BRONZE_BRICKS.get())
                .or(Predicates.abilities(PartAbility.IMPORT_ITEMS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.EXPORT_ITEMS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setPreviewCount(1))
                .or(Predicates.abilities(PartAbility.INPUT_ENERGY).setPreviewCount(1));

        // J：16 个模块位 = 空槽占位 ∪ 模块控制器（谓词本体在 ShanhaiMachines.moduleSlotPredicate()，
        //    那里对两个「字符串查表」方块逐条判空、缺了就抛，绝不放行成「谓词恒 false」）。
        //    ⛔ 【2026-09-22 作废 · 原文照留】本行曾短暂加注：
        //       「🔴 2026-09-22 起含 Predicates.air()：拆掉一台模块不再让整座主机失效。」
        //       作废原因：用户同日撤回「拆一个模块不该停全机」⇒ 谓词已恢复为不含 air。
        //    这一格放什么由谓词决定；这 16 格的坐标由 F-1 / GtlAddCompat.moduleSlots 决定，两者互不推导。
        TraceabilityPredicate jPred = ShanhaiMachines.moduleSlotPredicate();

        return GtlAddCompat.hostPattern()
                .where('A', aPred)                                                  // 外圈外壳 + 仓室
                .where('B', Predicates.blocks(GTBlocks.BRONZE_HULL.get()))          // 外圈装饰
                .where('C', Predicates.blocks(GTBlocks.CASING_BRONZE_PIPE.get()))   // 核心圈内层
                .where('D', Predicates.blocks(GTBlocks.CASING_PRIMITIVE_BRICKS.get())) // 背面结构
                .where('E', Predicates.blocks(GCyMBlocks.CASING_INDUSTRIAL_STEAM.get())) // 侧面
                .where('F', Predicates.blocks(GTBlocks.CASING_COKE_BRICKS.get()))   // 核心圈外圈（无仓室）
                .where('G', Predicates.blocks(GTBlocks.CASING_BRONZE_PIPE.get()))   // 内部填充
                .where('H', Predicates.blocks(GTBlocks.CASING_PRIMITIVE_BRICKS.get())) // 内部填充
                .where('I', Predicates.blocks(GTBlocks.CASING_BRONZE_PIPE.get()))   // 内部填充
                .where('J', jPred)                                                  // 16 个模块位
                .where('K', Predicates.blocks(GTBlocks.BRONZE_BRICKS_HULL.get()))   // 内部填充
                .where('~', Predicates.controller(Predicates.blocks(definition.getBlock())))
                .where(' ', Predicates.any())
                .build();
    }
}
