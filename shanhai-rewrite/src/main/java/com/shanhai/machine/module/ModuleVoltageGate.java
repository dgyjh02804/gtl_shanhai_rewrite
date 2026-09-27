package com.shanhai.machine.module;

/**
 * 山海重构 · <b>配方电压等级闸门的纯函数落点</b>（本工程唯一的电压准入判据）。
 *
 * <h2>1. 为什么单独开这个类（而不是写成一个私有方法）</h2>
 * 判据要能被<b>离线装置直接调用取证</b>：真正的调用点
 * {@code PrimordialModuleRecipeLogic#checkRecipe} 需要一台真机器（要 {@code GTRecipe}、
 * 要 {@code IRecipeCapabilityHolder}、要 GT 注册表），裸 JVM 里跑不起来。
 * 而本类<b>不引用任何 Minecraft / GT 类型</b>（连 import 都没有）⇒ 可以被一个只带 classpath 的
 * {@code main} 直接加载并逐值核对（装置：{@code _voltage-gate-verify\VoltageGateProof.java}）。
 * <p>⚠️ <b>不许把本类改成一个会读机器的实现</b> —— 那样这个"可离线取证"的性质就没了，
 * 而它正是本项目「检查器先自证」纪律所依赖的东西。
 *
 * <h2>2. 判据出处（字节码，不是推断）</h2>
 * 与 gtladditions 父类 {@code MutableRecipesLogic#checkRecipe} <b>逐字同义</b>：
 * <pre>
 *   javap -c -p libs/gtladditions-3.2.8Custom-fix1.jar \
 *         com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic
 *   protected boolean checkRecipe(GTRecipe);
 *       21: invokestatic  IGTRecipe.of:(GTRecipe;)LIGTRecipe;
 *       25: invokeinterface IGTRecipe.getEuTier:()I
 *       30: getfield      machine
 *       34: invokevirtual WorkableElectricMultiblockMachine.getTier:()I
 *       37: if_icmpgt      82      ← euTier &gt; machineTier ⇒ 整条返回 false
 * </pre>
 * 另有一条同源的闸门在 gtlcore 侧（{@code GTRecipeMixin#matchTickRecipe}）：
 * {@code this.getEuTier() > GTUtil.getFloorTierByVoltage(machine.getMaxVoltage()) ⇒ FAIL_VOLTAGE_TIER}
 * —— 口径一致（都是"配方等级不得高于机器等级"），只是取机器等级的方式不同
 * （{@code getTier()} 字段 vs 现算 {@code getMaxVoltage()}）。
 *
 * <h2>3. 语义</h2>
 * <pre>
 *   machineTier = 0（ULV，8 EU/t） ／ recipeEuTier = 1（LV，32 EU/t）⇒ false（拦下）
 *   machineTier = 1（LV）          ／ recipeEuTier = 1               ⇒ true （放行）
 *   machineTier = 9（UHV）         ／ recipeEuTier = 0..9            ⇒ true （放行）
 * </pre>
 */
public final class ModuleVoltageGate {

    private ModuleVoltageGate() {}

    /**
     * 这台机器够不够电压跑这条配方。
     *
     * @param machineTier 机器电压等级 —— 真值来自
     *                    {@code WorkableElectricMultiblockMachine#getTier()}，而它在
     *                    {@code onStructureFormed()} 里被写成
     *                    {@code GTUtil.getFloorTierByVoltage(getMaxVoltage())}，
     *                    也就是<b>这台机器自己能源仓的电压等级</b>。
     * @param recipeEuTier 配方电压等级（{@code IGTRecipe#getEuTier()}）
     * @return {@code true} = 够电压，放行；{@code false} = 电压等级不足，拦下
     */
    public static boolean allows(int machineTier, int recipeEuTier) {
        return recipeEuTier <= machineTier;
    }
}
