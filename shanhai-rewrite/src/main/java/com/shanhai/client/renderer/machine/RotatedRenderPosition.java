package com.shanhai.client.renderer.machine;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * 「把基准朝向下的偏移量旋转到目标朝向」的<b>本地实现</b>。
 *
 * <h2>为什么要本地化（不引 gtladditions）</h2>
 * 上游这里调的是 {@code com.gtladd.gtladditions.utils.CommonUtils.INSTANCE.getRotatedRenderPosition(...)}。
 * 我把它反编译读了全文：**它是一段 20 行的纯数学**，输入只有两个 {@link Direction} 和三个 double，
 * <b>不读任何 gtladditions 状态、不碰注册表、不依赖客户端</b>。
 * 所以这里逐行照抄成静态方法 —— 这是<b>精确等价</b>（不是"近似实现"），
 * 从而把 gtladditions 的客户端依赖收缩到只剩"环坐标表"与"星体渲染管线"两处真需要的地方
 * （都在 {@code com.shanhai.client.compat.GtlAddClientCompat} 里）。
 *
 * <h2>语义（反编译原文逐句对应）</h2>
 * <pre>
 *   if (!(baseFacing.getAxis() == Axis.Y || targetFacing.getAxis() != Axis.Y))
 *       throw new IllegalArgumentException("Facing must be horizontal (NORTH, SOUTH, EAST, WEST)");
 *   double y = 0.5 + offsetY;
 *   if (baseFacing == targetFacing) return new Vec3(0.5 + offsetX, y, 0.5 + offsetZ);
 *   int baseIndex = getHorizontalIndex(baseFacing);     // 实证顺序：EAST=0, SOUTH=1, WEST=2, NORTH=3
 *   int targetIndex = getHorizontalIndex(targetFacing); // （见下方 getHorizontalIndex 的取证）
 *   int rotationSteps = (targetIndex - baseIndex + 4) % 4;
 *   case 0 -> (0.5 + offsetX, y, 0.5 + offsetZ)
 *   case 1 -> (0.5 - offsetZ, y, 0.5 + offsetX)
 *   case 2 -> (0.5 - offsetX, y, 0.5 - offsetZ)
 *   case 3 -> (0.5 + offsetZ, y, 0.5 - offsetX)
 * </pre>
 * 注意那个 {@code 0.5} 常量：它是<b>方块中心偏移</b>，不是"归一化坐标"，
 * 少加/多加会让球心与轨道环整体错半格（上游两处调用都靠它对齐）。
 */
public final class RotatedRenderPosition {

    private RotatedRenderPosition() {}

    /**
     * @param baseFacing   偏移量所依据的基准朝向（上游用 {@code Direction.EAST} 或 {@code Direction.WEST}）
     * @param targetFacing 目标朝向（主机的 {@code getFrontFacing()}）
     * @param offsetX      基准朝向下的 X 偏移（格）
     * @param offsetY      基准朝向下的 Y 偏移（格；竖直不受旋转影响）
     * @param offsetZ      基准朝向下的 Z 偏移（格）
     * @return 旋转到目标朝向后、相对<b>控制器方块位置</b>的渲染偏移（含 +0.5 方块中心偏移）
     */
    public static Vec3 rotate(Direction baseFacing, Direction targetFacing,
                              double offsetX, double offsetY, double offsetZ) {
        // 与上游同一道前置校验：两个朝向必须都是水平朝向（否则旋转步数无意义）
        if (!(baseFacing.getAxis() == Direction.Axis.Y || targetFacing.getAxis() != Direction.Axis.Y)) {
            throw new IllegalArgumentException("Facing must be horizontal (NORTH, SOUTH, EAST, WEST)");
        }

        double y = 0.5 + offsetY;
        if (baseFacing == targetFacing) {
            return new Vec3(0.5 + offsetX, y, 0.5 + offsetZ);
        }

        int rotationSteps = (getHorizontalIndex(targetFacing) - getHorizontalIndex(baseFacing) + 4) % 4;
        return switch (rotationSteps) {
            case 0 -> new Vec3(0.5 + offsetX, y, 0.5 + offsetZ);
            case 1 -> new Vec3(0.5 - offsetZ, y, 0.5 + offsetX);
            case 2 -> new Vec3(0.5 - offsetX, y, 0.5 - offsetZ);
            case 3 -> new Vec3(0.5 + offsetZ, y, 0.5 - offsetX);
            default -> throw new IncompatibleClassChangeError();
        };
    }

    /**
     * 水平朝向 → 旋转序号。
     *
     * <p>🔴 <b>这个顺序是反编译读出来的，不是推的。</b>上游 Kotlin 写的是一个占位符查找
     * （{@code WhenMappings.$EnumSwitchMapping$0[facing.ordinal()]}），我解了那张表
     * （{@code javap} 找到 {@code com/gtladd/gtladditions/utils/CommonUtils$WhenMappings.class}，CFR 反编译原文）：
     * <pre>
     *   nArray[Direction.EAST.ordinal()]    = 1;
     *   nArray[Direction.SOUTH.ordinal()]   = 2;
     *   nArray[Direction.WEST.ordinal()]    = 3;
     *   nArray[Direction.NORTH.ordinal()]   = 4;
     * </pre>
     * 而 {@code getHorizontalIndex} 的主体是 {@code case 1 -> 0; case 2 -> 1; case 3 -> 2; case 4 -> 3; default -> 0;}
     * ⇒ <b>EAST=0、SOUTH=1、WEST=2、NORTH=3</b>，竖直朝向落到 {@code default -> 0}。
     *
     * <p>⚠️ 我第一版按"MC 枚举直觉"写成了 {@code NORTH=0, EAST=1, SOUTH=2, WEST=3} —— <b>那是错的</b>，
     * 会让除基准朝向以外的三个朝向整体错 90°，且<b>不报错</b>（球心和轨道环会歪/错位）。
     * 所以这里保留反编译出来的真实顺序，并留下取证出处。
     */
    private static int getHorizontalIndex(Direction facing) {
        return switch (facing) {
            case EAST -> 0;
            case SOUTH -> 1;
            case WEST -> 2;
            case NORTH -> 3;
            default -> 0;   // DOWN / UP → 上游走 default 分支
        };
    }
}
