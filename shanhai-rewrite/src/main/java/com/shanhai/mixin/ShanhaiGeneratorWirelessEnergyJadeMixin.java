package com.shanhai.mixin;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.integration.jade.provider.RecipeLogicProvider;
import com.shanhai.ShanhaiMod;
import com.shanhai.common.machine.PrimordialOmegaEngineMachine;
import com.shanhai.machine.module.PrimordialGeneratorProduction;
import com.shanhai.machine.module.PrimordialModuleMachine;

import net.minecraft.nbt.CompoundTag;

import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigInteger;

/**
 * 山海重构 · 让<b>发电那台原初模块</b>在抬头（Jade）上显示出<b>每 tick 实际入池的 BigInteger 产出</b>
 * —— <b>照抄「宇宙之心」那条现成通道</b>，本类不写任何渲染代码。
 *
 * <h2>1. 用户原话（逐字）</h2>
 * <blockquote><b>「A1我希望它可以正常在jade显示，像宇宙之心一样」</b></blockquote>
 *
 * <h2>2. 🔴 「像宇宙之心一样」到底是谁提供的（已读上游原文，附行号）</h2>
 * 宇宙之心那行「产能 8.11e33 EU/t（879428478035376.6A MAX+16）」的来源是
 * <b>gtladditions 自己的一个 mixin</b>（不是 GTCEu，也不是本工程）：
 * <pre>
 * 类：com.gtladd.gtladditions.mixin.gtceu.recipe.RecipeLogicProviderMixin
 *     （版本 gtladditions-3.2.8Custom-fix1；源码在
 *      originals/analysis/decomp/limitsrc/com/gtladd/gtladditions/mixin/gtceu/recipe/RecipeLogicProviderMixin.java）
 *   · :38-50  {@code @Inject(HEAD)} 到 {@code RecipeLogicProvider.write(CompoundTag, RecipeLogic)}：
 *       if (capability.getLastRecipe() instanceof IWirelessGTRecipe recipe) {
 *           BigInteger v = recipe.getWirelessEuTickInputs();
 *           if (v != null && v.signum() != 0)
 *               data.putByteArray("wirelessTickInputs", v.toByteArray());   ← ★ 就是这个键
 *       }
 *   · :52-152 {@code @Overwrite} 掉 {@code addTooltip(...)}，其中 :93-125：
 *       } else if (capData.contains("wirelessTickInputs", 7)) {            ← 7 = TAG_Byte_Array
 *           BigInteger wirelessEut = new BigInteger(capData.getByteArray("wirelessTickInputs"));
 *           BigInteger abs = wirelessEut.abs();
 *           long longEu = NumberUtils.getLongValue(abs);
 *           int tier = longEu == Long.MAX_VALUE ? 30 : NumberUtils.getFakeVoltageTier(longEu);
 *           … CommonUtils.formatDouble(abs.doubleValue()) + " EU/t" + " (" + … VNF[tier] + ")" …
 *           if (wirelessEut.signum() &lt; 0) tooltip.add("gtceu.top.energy_consumption" …);
 *           else                            tooltip.add("gtceu.top.energy_production" …);   ← 「产能」那行
 *       }
 * </pre>
 * ⇒ <b>上游只认一个东西：Jade 服务端数据里的 {@code wirelessTickInputs}（byte[] = BigInteger.toByteArray）
 * 这个键；正数画成「产能」、负数画成「耗能」，数值与单位全部由上游自己算。</b>
 *
 * <h2>3. 🔴 我们的机器为什么显示不出来（根因，逐条对得上现象）</h2>
 * <pre>
 *   · GTCEu {@code RecipeLogicProvider.write}（_tmp-jade/…/RecipeLogicProvider.java :35-56 反编译原文）只写
 *       "Working" 与 "Recipe"{EUt, isInput} —— 而且
 *           long EUt = RecipeHelper.getInputEUt(recipe);
 *           if (EUt == 0L) { isInput = false; EUt = RecipeHelper.getOutputEUt(recipe); }
 *   · 发电那台配方自带的电【已被我们就地清零】（{@code PrimordialModuleMachine#shanhai$suppressRecipeElectricity}
 *     —— 电改从无线网走，见 §2.9）⇒ <b>EUt 恒为 0</b>
 *   · 上游 {@code addTooltip} 的判据是 {@code if (eut != 0L) … else if (capData.contains("wirelessTickInputs",7)) …}
 *     ⇒ EUt==0 ⇒ 跳过实数分支；而那个无线键<b>没人写</b>（我们的 lastRecipe 不是 {@code IWirelessGTRecipe}，
 *       上游 :44 那句 {@code instanceof} 判不中）⇒ <b>两行都不画 ⇒ 抬头上「产能」整行消失</b>
 *   （这正是用户看到的现象：机器自身那行「产能 … EU/t」不显示了，且并没有显示 0。）
 *
 *   · 另一行「无线电网输出终端」显示 9,223,372,036,854,775,807 是另一件事，根因也查清了：
 *       gtladditions {@code NetworkEnergyContainer.getEnergyStored()}（…/common/machine/trait/NetworkEnergyContainer.kt :127-137）
 *       = {@code NumberUtils.getLongValue(WirelessEnergyManager.getUserEU(uuid))} ⇒ <b>BigInteger 池被窄化成 long、
 *       超出即静默饱和到 Long.MAX_VALUE</b>；再由 GTCEu {@code ElectricContainerBlockProvider.addTooltip}
 *       （:39-54，{@code FormattingUtil.formatNumbers(stored)} 带千分位）画出来就是那一串。
 *     ⇒ 那行是「池子的 long 投影」，不是本行要修的东西（本行只补"每 tick 入池多少"）。
 * </pre>
 *
 * <h2>4. 本类怎么做（三句话）</h2>
 * <ol>
 *   <li>在<b>同一个方法</b> {@code RecipeLogicProvider.write(CompoundTag, RecipeLogic)} 的 RETURN 处注入
 *       （上游是 HEAD，两边互不干扰），<b>由本工程补写那一个键</b> {@code wirelessTickInputs}；</li>
 *   <li>取值 = <b>与入池逐值相同的那个算式</b> {@link PrimordialGeneratorProduction#perTick}
 *       （基础 EUt × 并行 p × N3 倍率；本类内<b>不出现任何自有算式</b>，三个因子按与
 *       {@code PrimordialModuleMachine#shanhai$depositGenerationToWirelessPool} <b>逐字相同</b>的方式取）；</li>
 *   <li>只对<b>发电那台</b>写（{@code instanceof PrimordialModuleMachine && getDefinition().isGenerator()}），
 *       其余 23 台与主机<b>一行都不写</b> ⇒ 行为与显示逐字不变。</li>
 * </ol>
 * 写进去之后，<b>渲染完全交给上游那份 @Overwrite</b>（第 2 节那段原文），
 * 于是发电那台得到的「产能 … EU/t（… A …）」与宇宙之心<b>同一条实现、逐字同款</b>。
 *
 * <h2>5. ⚠️ 为什么不需要新增 {@code @DescSynced} 字段（= 没有每 tick 的带宽代价）</h2>
 * Jade 的「服务端数据」本身就是一条<b>服务端 → 客户端</b>的 NBT 通道：
 * {@code CapabilityBlockProvider.appendServerData(CompoundTag, BlockAccessor)}（反编译原文 :53-72）
 * 在<b>玩家看向该方块时</b>由服务端现算，写进 {@code data.getCompound(uid).put("null", tag)} 再发给客户端；
 * 客户端 {@code appendTooltip} 读的就是同一份 tag。
 * ⇒ 本类只是往这份<b>已存在的</b> tag 里多塞一个 byte[]（满配 11 字节），
 * <b>不新增同步字段、不新增每 tick 的包</b>；本项目既有的 parallel / threads 两行走的是同一条通道
 * （{@code ShanhaiInfiniteThreadDisplayMixin}），代价同级。
 * <p>⚠️ 诚实边界：值是在<b>查询那一刻</b>现算的，不是"上次真的入池了多少"的记账 ——
 * 它与入池是同一条纯函数、同一组输入（配方不变时逐值相等），但若
 * {@code WirelessEnergyManager.addEUToGlobalEnergyMap} 那一侧拒收（池满/绑定失效），
 * 本行仍会显示"本 tick 试图入池多少"。真正的记账读数在 {@code [SHANHAI-GEN-LIMIT]} 那条日志里
 * （它同时打 {@code 池 before → after（本次接受=…）}），两者配合看即可。
 *
 * <h2>6. 与上游 {@code @Overwrite} 共存 / {@code require = 0} 的理由</h2>
 * 与 {@link ShanhaiInfiniteThreadDisplayMixin} 同一条纪律（那里已用 Mixin 0.8.5 字节码逐条核过）：
 * <b>真正决定顺序的是 pass</b>（MAIN → PREINJECT → INJECT），{@code @Overwrite} 在 MAIN pass 落地、
 * {@code @Inject} 在 INJECT pass 落地 ⇒ 本类的 RETURN 注入一定作用在覆盖后的方法体上；
 * 且上游只在 <b>HEAD</b> 写键、本类在 <b>RETURN</b> 写键 ⇒ 就算顺序反过来也只有"谁最后写"的差别，
 * 而两台机器（宇宙之心 vs 我们的发电模块）<b>不可能同时是同一个 {@code capability}</b> ⇒ 不会互相覆盖。
 * <p>{@code require = 0}：这是<b>纯显示</b>增强 —— GTCEu 换实现方式时只应该"少一行"，
 * <b>绝不允许把用户的客户端崩掉</b>。为了不静默失败，第一次真正写入时打一条 INFO
 * （字面量 {@code [SHANHAI-DISPLAY]}，与模块那两行同一个记号，一次 grep 全看得见）。
 * <p>⚠️ 与那条同样的边界：{@code require = 0} + 只有玩家看向方块才会触发
 * ⇒ <b>无头专服冒烟只能证明"没崩、注入没报错"，证明不了这一行会出现</b>，只能由用户进游戏看一眼。
 *
 * <h2>7. 构建期可机器验证的几点（不是"我觉得对"）</h2>
 * <pre>
 *   · 目标方法确实存在：javap -p -cp gtceu-1.20.1-1.4.4.jar
 *       com.gregtechceu.gtceu.integration.jade.provider.RecipeLogicProvider
 *     ⇒ {@code protected void write(net.minecraft.nbt.CompoundTag, RecipeLogic)}（与下面 method 字面量逐字一致）
 *   · 键名字面量与上游逐字一致：javap -c gtladditions 的 RecipeLogicProviderMixin.class
 *     ⇒ 常量池里同时出现 "wirelessTickInputs"（本类也用它）
 *   · 本类没有 refmap 条目也不影响：ForgeGradle 的 reobf 会重写<b>混入类自身的方法体</b> ——
 *     已验证的对照样本是本工程今天在用的 {@code ShanhaiInfiniteThreadDisplayMixin}
 *     （源码写 {@code data.putLong(...)}，成品 jar 里是 {@code CompoundTag.m_128356_}）
 *     ⇒ 本类的 {@code data.putByteArray(...)} 在成品里同样是 {@code m_128382_}。
 * </pre>
 */
@Mixin(value = RecipeLogicProvider.class, priority = 500, remap = false)
public class ShanhaiGeneratorWirelessEnergyJadeMixin {

    /**
     * 🔴 <b>与 gtladditions 逐字一致的那个 Jade 数据键</b>（证据见类注释第 2 节原文 :47 / :93）。
     *
     * <p>⚠️ 这是<b>跨模组的字符串契约</b>：上游 {@code @Overwrite} 只认这个键 + TAG_Byte_Array(7)。
     * 一旦 gtladditions 改键名，本行会静默消失（不崩）—— 所以值一并写在第 6 节那种一次性 INFO 里，
     * 出事时 grep {@code [SHANHAI-DISPLAY]} 就能看到"我们确实写了这个键"。
     */
    @Unique
    private static final String SHANHAI$WIRELESS_KEY = "wirelessTickInputs";

    /** 只打一次，避免抬头上每次刷新都刷屏（与 {@link ShanhaiInfiniteThreadDisplayMixin} 同一个理由）。 */
    @Unique
    private static boolean shanhai$generatorJadeAnnounced = false;

    /**
     * 往 Jade 的<b>服务端数据</b>里补写无线网键（<b>只写显示用 NBT，不参与任何运算</b>）。
     *
     * @param data       本次查询的服务端数据 tag（{@code CapabilityBlockProvider#appendServerData} 里新建的那个，
     *                   同一实例随后被 put 进 {@code capData} 并发给客户端）
     * @param capability 本次查询到的 {@code RecipeLogic}（{@code GTCapabilityHelper.getRecipeLogic} 返回的机器特性）
     */
    @Inject(
            method = "write(Lnet/minecraft/nbt/CompoundTag;Lcom/gregtechceu/gtceu/api/machine/trait/RecipeLogic;)V",
            at = @At("RETURN"),
            remap = false,
            require = 0)
    private void shanhai$generatorWirelessTick(CompoundTag data, RecipeLogic capability, CallbackInfo ci) {
        try {
            if (data == null || capability == null) {
                return;
            }
            // ① 只认发电那台（全 24 台里唯一 isGenerator()）—— 其余 23 台与主机连键都不写。
            if (!(capability.machine instanceof PrimordialModuleMachine module)) {
                return;
            }
            if (!module.getDefinition().isGenerator()) {
                return;
            }
            // ② 上游只在 Working 时才画那一行，这里同判据：不工作时写了也没人看，
            //    而且此刻"每 tick 入池多少"本身没有意义（不产电）。
            if (!capability.isWorking()) {
                return;
            }
            final BigInteger production = shanhai$perTickProduction(capability, module);
            if (production.signum() <= 0) {
                return;
            }
            // ③ 就是这一个键 —— 上游 @Overwrite 的 addTooltip 会把它画成「产能 … EU/t（… A …）」。
            data.putByteArray(SHANHAI$WIRELESS_KEY, production.toByteArray());

            if (!shanhai$generatorJadeAnnounced) {
                shanhai$generatorJadeAnnounced = true;
                ShanhaiMod.LOGGER.info("[SHANHAI-DISPLAY] 发电模块已往 Jade 写无线网产出键 {}={}"
                                + "（照宇宙之心同一条通道，渲染交给 gtladditions；此后不再重复打）pos={}",
                        SHANHAI$WIRELESS_KEY, production, module.getPos());
            }
        } catch (Throwable ignored) {
            // 纯显示路径：任何异常都不允许影响游戏（与 ShanhaiInfiniteThreadDisplayMixin 同纪律）。
        }
    }

    /**
     * <b>本 tick 的产出</b> —— 三个因子的取法与
     * {@code PrimordialModuleMachine#shanhai$depositGenerationToWirelessPool}（原文 :1382-1389）
     * <b>逐字相同</b>，算式复用同一个纯函数（{@link PrimordialGeneratorProduction#perTick}）
     * ⇒ 这一行与 {@code [SHANHAI-GEN-LIMIT]} 日志里的"每 tick"必然同值，不会各算各的。
     *
     * @return 每 tick 产出；取不到配方时返回 {@link BigInteger#ZERO}
     */
    @Unique
    private static BigInteger shanhai$perTickProduction(RecipeLogic capability, PrimordialModuleMachine module) {
        final GTRecipe last = capability.getLastRecipe();
        if (last == null) {
            return BigInteger.ZERO;
        }
        // 基础 EUt 取【配方定义实例】（origin）上的 tickOutputs —— 与入池那边同一个来源：
        // 拿 last（已被 ×p×倍率、而且配方自带电已被清零）读到的是 0，不是"配方自己的产出"。
        final GTRecipe origin = capability.getLastOriginRecipe();
        final long baseEut = RecipeHelper.getOutputEUt(origin != null ? origin : last);
        // 并行数 = 该配方【每 tick】实际吃到的并行 —— 不用 getMaxParallel，
        // 两者在"输入不够"时会分叉，用后者会凭空显示多出来的电（与入池那边同一条理由）。
        // 🔴 2026-09-25 修正：IGTRecipe.getRealParallels() 是【整批总份数】（gtlcore BatchProcessing.scaleRecipe
        //    里它 = 原值 × batchSize，且 duration 同时 × batchSize）⇒ 必须再除以 getBatchSize() 才是【每 tick】。
        //    非批处理时 batchSize = 1 ⇒ 除 1 恒等 ⇒ 正常情况显示不变；批处理下这条正是"Jade 不再 ×100"的判据。
        //    除不尽时的取向 = 向下取整（见 PrimordialModuleMachine#shanhai$depositGenerationToWirelessPool 的同条注）。
        final long batchSize = Math.max(1L, ((IGTRecipe) last).getBatchSize());
        final long parallel = Math.max(1L, ((IGTRecipe) last).getRealParallels() / batchSize);
        // 倍率源 = 主机的专属槽门控等级（N3），与配方修饰链同源。
        final PrimordialOmegaEngineMachine host = module.getHost();
        final int gateBonus = host == null ? 0 : host.moduleSlotBonus();
        return PrimordialGeneratorProduction.perTick(baseEut, parallel, gateBonus);
    }
}
