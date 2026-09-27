package com.shanhai.machine.module;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

/**
 * 纯标准原初模块：**类体只有构造函数**，行为全部继承 {@link PrimordialModuleMachine}。
 *
 * <p>为什么可以这么空：上游 25 个模块里「纯标准」的那一批（原初宇宙反应炉、原初分子裂隙核心、
 * 原初超临界物质生成核心、原初天琼组装核心、原初永恒熔炼炉、原初世线穿刺矩阵、原初量子扭曲矩阵、
 * 原初韶光聚合核心、原初未央重构模块 …）在参照实现里也是 10–31 行的类，业务逻辑只有"自己的并行表 +
 * 显示文案"，其余全部继承自它们的模块基类。本阶段我们不实现"每模块一张并行表"（那是数值平衡内容），
 * 并行由主机的模块槽决定 ⇒ 这 <b>23 个</b>（2026-09 补齐后的数量）模块的 Java 侧就是**纯数据**
 * （{@link ModuleRegistry} 里的一行）。
 *
 * <p>模块槽 / 线程倍率槽 / 额外挂载页 / 与主机的连接与断开回调，全部来自
 * {@link PrimordialModuleMachine} 基类，因此这 23 个模块在这几处的行为与物质重组核心一致。
 *
 * <p><b>中文名</b>：每台的中文名已在 {@code assets/shanhai/lang/zh_cn.json} 里以
 * {@code block.shanhai.<注册路径>} 给出（2026-09 补齐，译名与上游 {@code gt_shanhai} 的
 * {@code lang/zh_cn.json} 逐字一致）；英文名走注册时的 {@code .langValue()}。
 *
 * <p><b>将来会承接（本阶段不实现、不加字段）</b>：模块侧将来会承接 N5 的耗能/耗时减免
 * （成本系数 {@code f = 1 − 0.95^(17/等级)}，即 {@code 1 − 减免比例}，只作用于模块）
 * 与 N3 的产出倍率（{@code 1 + 物质模块等级}，主机 + 其所有模块）。
 *
 * <p><b>没有做</b>：各自的并行表（见上）；各自的外观资产（借用 GTCEu 现成 bronze 资产）。
 */
public class StandardPrimordialModule extends PrimordialModuleMachine {

    public StandardPrimordialModule(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }
}
