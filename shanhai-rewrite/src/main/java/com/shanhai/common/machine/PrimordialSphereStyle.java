package com.shanhai.common.machine;

/**
 * 原始终焉引擎主机中心球体的渲染风格。
 *
 * <p>🔴 <b>{@code ordinal()} 即 NBT 持久化值 / {@code @DescSynced} 同步值</b>：
 * <b>禁止重排、禁止在中间插入新项</b>。要加新形态只能追加到末尾（末尾追加时老存档仍然正确）。
 * 这一条与 {@code com.shanhai.config.ShanhaiConfig.ConfigValues.SphereStyleOverride} 是<b>两个不同的枚举、
 * 故意不合并</b>：后者多一个 {@code FOLLOW_MACHINE}（"听机器的"）语义 —— 它不是机器状态，是<b>观看者偏好</b>，
 * 且它不持久化进存档。
 *
 * <p>放在 common 侧（不是 client）：主机类 {@code PrimordialOmegaEngineMachine} 要按它持久化/同步，
 * 而主机类跑在两侧；本枚举本身<b>不引用任何 client-only 符号</b>。
 */
public enum PrimordialSphereStyle {

    /** 宇宙渲染器：鸿蒙微型宇宙（恒星 + 三颗行星轨道 + 外层星空壳），完全自包含。 */
    UNIVERSE,

    /** 中子星渲染：复用伪神之锻炉的星体渲染管线（gtladditions 的延迟批次）。 */
    NEUTRON_STAR;

    private static final PrimordialSphereStyle[] VALUES = values();

    /** 越界/非法一律退回 {@link #UNIVERSE}（老存档里读出一个怪值也不该崩，也不该静默变成 null）。 */
    public static PrimordialSphereStyle byIndex(int index) {
        return (index >= 0 && index < VALUES.length) ? VALUES[index] : UNIVERSE;
    }
}
