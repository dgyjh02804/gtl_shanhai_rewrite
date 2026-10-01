package com.shanhai.machine.module;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.shanhai.common.text.ShanhaiTextParser;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

/**
 * 原初物质重组核心 —— 挂在「原始终焉引擎」16 个模块位上的第一个原初模块（阶段 1 唯一模块）。
 *
 * <h2>官方 guide 描述（阶段 1 对齐项）</h2>
 * <ul>
 *   <li>「一台挂在原初引擎模块位上的基础重组核心」「属于原初引擎模块，不是独立主机」；</li>
 *   <li>「机器本体会显示已安装模块、并行上限和线程倍率」；</li>
 *   <li>「一台机有一个<b>独立的并行物质槽</b>」「<b>空槽默认并行是 64</b>，可以先顶着开工」；</li>
 *   <li>「没有合适模块时，默认并行仍然可用」—— 即<b>不准因空槽拒绝工作</b>。</li>
 * </ul>
 *
 * <h2>并行槽（规格 §5.2）—— 🔴 2026-09-25 已整体上移到基类</h2>
 * 机器内 <b>1 个</b>物品槽（{@link PrimordialModuleMachine#matterModuleSlot}），只收 17 个物质模块物品；
 * <ul>
 *   <li>每 <b>3 tick</b> 重扫一次（上游实现即 {@code getOffsetTimer() % 3 == 0}），成形时先扫一次；</li>
 *   <li>空槽 / 未识别物品 = {@link PrimordialModuleMachine#DEFAULT_PARALLEL} = 64；</li>
 *   <li>堆叠 ≥16 翻倍：{@code base × (1 + count/16)}，溢出饱和到 {@code Long.MAX_VALUE}。</li>
 * </ul>
 * 🔴 <b>表确实是逐台不同的</b>（新版订正：<b>按【值】只有两张</b> ——
 * 表#1 = {@link ParallelTable#ENHANCED}（3 台），表#2/#3 = {@link ParallelTable#STANDARD}（20 台，
 * 上游 {@code PrimordialMatterCaster} 那张只是插入顺序不同、值完全一样）。
 * 上面那段"至少三张互不相同的表"是按<b>源码书写顺序</b>数的，值上是两张 —— 逐台上游核对见交付报告。
 * <p>⚠️ 本类<b>不再自带表副本</b>（两份一定漂移）；用哪张表由下面那一处覆写声明。
 *
 * <h2>配方（规格 §6.2 方案 A：挂 GTCEu 原版类型）</h2>
 * 本阶段<b>不</b>注册 {@code gtceu:primordial_matter_recombination}（那 103 条老配方全部依赖 KubeJS 与
 * {@code module_level} 自定义条件，本阶段不做 KubeJS ⇒ 注册了也没有配方可跑）。改挂 GTCEu 原版现成类型，
 * 宿主环境自带配方，零数据成本即可演示闭环（见 {@link ModuleRegistry#RECIPE_TYPES_MATTER_RECOMBINATOR_CORE}）。
 */
public class PrimordialMatterRecombinatorCore extends PrimordialModuleMachine {

    /**
     * <b>本模块用表#1（{@link ParallelTable#ENHANCED}）</b>。
     *
     * <h2>为什么本类只剩这一处覆写（2026-09-25 路线 ① 的后果）</h2>
     * 并行槽（{@code DEFAULT_PARALLEL=64} + 17 项表 + 3 tick 重扫 + {@code currentParallel}
     * + {@code parallelCap()} + {@code applyStackMultiplier()}）<b>整体上移到了基类</b>
     * {@link PrimordialModuleMachine}，因为上游 23/24 台本来就有自己那张表，
     * 而本工程此前只把它接在了本类一台身上（用户实机指出的那个缺口）。
     * <p>本类现在只需回答一个问题：<b>我用哪张表</b>。
     * 上游出处：{@code originals/upstream/…/module/core/PrimordialMatterRecombinatorCore.java:86-105}
     * （表#1 的值逐字搬到了基类的 {@code PARALLEL_TABLE_ENHANCED}，没有第二份副本）。
     * <p>上游同表的另两台：原初奇点反演核心、原初因果编织矩阵（{@code ModuleRegistry} 里逐条声明）。
     */
    @Override
    protected ParallelTable parallelTable() {
        return ParallelTable.ENHANCED;
    }

    public PrimordialMatterRecombinatorCore(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    // ⛔ 【2026-09-25 路线 ① · 原文本类被上移到基类的代码，逐字留档，勿在别处再抄一份】
    //
    //   搬走的东西（现在都在 PrimordialModuleMachine）：
    //     · public  static final long DEFAULT_PARALLEL = 64L;            → 基类同名常量
    //     · public  static final int  STACK_STEP      = 16;              → 基类同名常量
    //     · private static final Map<String, Long> PARALLEL_BY_MODULE_ID  → 基类 PARALLEL_TABLE_ENHANCED
    //     · private long currentParallel = DEFAULT_PARALLEL;             → 基类同名字段
    //     · private TickableSubscription matterSlotScanSubs;             → 基类同名字段
    //     · onModuleFormed()/onModuleInvalidated() 里的订阅与退订          → 基类的
    //                                                                    startMatterSlotScan()/stopMatterSlotScan()
    //                                                                    （改挂在 onStructureFormed/onStructureInvalid
    //                                                                      /onPartUnload 上，子类覆写钩子不再可能漏掉它）
    //     · private void scanMatterSlot()                                → 基类同名方法（多一个 switch 选表）
    //     · public  static long applyStackMultiplier(long, int)          → 基类同名方法（数值边界一字未改）
    //     · public  long getCurrentParallel()                            → 基类同名方法
    //     · public  int  parallelCap()                                   → 基类同名方法
    //     · public  long getDisplayParallel() { return getCurrentParallel(); }  → 基类同名方法
    //
    //   ⛔ 为什么不再保留本类那一份：**两份一定漂移**，而漂移的表现是"核心和别的模块数值不一样"
    //      且不报任何错。留档的目的只是让后来的人看得出这里搬过什么、搬去了哪。
    //
    //   ⛔ 被删掉的一个公开诊断方法（全工程零调用点，故未搬）：
    //        public static Long parallelOfModuleId(@Nullable String id) { return PARALLEL_BY_MODULE_ID.get(id); }
    // ═══════════════════════════════════════════════════════════════════════════════════════

    // ------------------------------------------------------------- 显示

    /**
     * 本模块自己的补充显示行。
     *
     * <p>⛔ <b>2026-09-25 任务 A：原来那一行 {@code 并行上限: §6…} 已上移到基类</b>
     * （见 {@link PrimordialModuleMachine#addParallelDisplayText}）—— 目的是让另 23 台
     * 也画得出并行/线程，且全工程只有一处实现。<b>文案与判据逐字保留</b>
     * （{@code formatParallel} 里那句 {@code >= Long.MAX_VALUE / 2 ⇒ 无限} 就是从这里搬过去的），
     * 所以本模块 GUI 的内容没有变化，只是行尾多了「 · 跨配方线程数: 1」。
     * <p>本条【不重复】画并行上限：重复画会在同一屏出现两个同名数字。
     */
    @Override
    protected void addModuleDisplayText(@NotNull java.util.List<Component> textList) {
        textList.add(Component.literal("物质模块等级: §bLv." + getMatterModuleLevel() + "§7 / 17"));
        ItemStack threadStack = getThreadBoostStack();
        textList.add(Component.literal("线程倍率槽: §7"
                + (threadStack.isEmpty() ? "（空）"
                        // 🔴 2026-09-30：名字先剥 `&$…-` 前缀码，理由同 PrimordialModuleMachine 的
                        //    线程槽 tooltip（世线残片名全带这个码，而本行含我们自己的 §7/§8）。
                        : ShanhaiTextParser.stripStyleCode(threadStack.getHoverName().getString())
                                + " §8[阶段 1 未生效]")));
    }
}
