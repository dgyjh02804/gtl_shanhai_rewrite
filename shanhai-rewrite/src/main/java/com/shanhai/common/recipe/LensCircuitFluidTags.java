package com.shanhai.common.recipe;

import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

/**
 * 【标签流体】的 Java 侧构造器 —— 给 KubeJS 用。
 *
 * <h2>⚠️ 名字是历史遗留，它本身与"透镜/再见"无关</h2>
 * 本类只是「按标签造一个 GT 流体原料」这一个通用动作，谁都能用。
 * 现在有两个使用者：
 * <ol>
 *   <li>2026-09-30 起：{@code [server_scripts]shanhai_lens_goodbye.js}（原初激光蚀刻 / 原初蜂群铸造）；</li>
 *   <li>2026-10-01 起：{@code [server_scripts]shanhai_primordial_forming.js}（原初物质定型）——
 *       那 2457 条里有 <b>1106 条</b>的流体槽写的是标签（{@code #forge:water} 之类），
 *       只有 7 条写具体流体。</li>
 * </ol>
 * （要不要改名是另一件事：改名要同时动 Java 与实例侧脚本，本轮不动，先把用途记在这里。）
 *
 * <h2>为什么需要它</h2>
 * 「原初激光蚀刻」的 25 条 gtladditions 配方，流体槽写的是<b>标签</b>
 * （{@code #forge:photoresist} / {@code #forge:euv_photoresist} / {@code #forge:gamma_rays_photoresist}
 * / {@code #forge:cosmic} / {@code #forge:spacetime} / {@code #forge:primordialmatter}），
 * 而不是具体流体。
 *
 * <p>KubeJS 侧<b>没有</b>可用的字符串写法：{@code GTRecipeComponents$FluidIngredientJS.of(Object)}
 * 的字节码分支只有三条——
 * <pre>
 *   偏移 14-34   instanceof FluidIngredient  ⇒ 直接包一层          ← 本类走这条
 *   偏移 35-58   instanceof JsonElement      ⇒ FluidIngredient.fromJson(...)
 *   偏移 108+    否则 → ListJS.of / FluidStackJS.of(String)       ⇒ 只认 'id amount'，不认 '#tag'
 * </pre>
 *
 * <h2>🔴 这条是被冒烟实测逼出来的</h2>
 * 2026-09-30 第一次（也是唯一一次）无头专服冒烟里，KubeJS 侧走"自己拼 JSON 字符串 +
 * {@code JsonParser.parseString}"这条路<b>当场炸了</b>，原文：
 * <pre>
 *   shanhai_lens_goodbye.js#54: Error in 'ServerEvents.recipes':
 *   Error: SHTAGF(forge:euv_photoresist,75) 失败:
 *   JavaException: com.google.gson.JsonSyntaxException: expected value to be either object or array.
 * </pre>
 * 而且它是在<b>数组字面量求值阶段</b>炸的 ⇒ 整个 {@code ServerEvents.recipes} 回调中止，
 * <b>282 条配方一条都没建出来</b>。⇒ 现在改成两条硬措施：
 * <ol>
 *   <li>标签流体改由本类（纯 Java，无 JSON、无 Rhino 编组）构造；</li>
 *   <li>KubeJS 产物里的表改成<b>纯数据</b>，任何构造调用都挪进逐条 try/catch ⇒
 *       单条坏掉只会让那一条计入 {@code failed}，不再连坐整个文件。</li>
 * </ol>
 *
 * <h2>🔴 本工程通用坑：凡是要被 KubeJS 调用的 Java 方法，一律不留重载</h2>
 * 2026-09-30 实测事故（同一轮、同一批文件）：{@code PrimordialLensCircuitMap} 上留了
 * {@code circuitFor(String,String)} 与 {@code circuitFor(String,String...)} 两个重载，
 * KubeJS(Rhino) 判不出该调哪个 ⇒ 抛
 * {@code InternalError: The choice of Java method ... matching JavaScript argument types
 * (string,string) is ambiguous} ⇒ 整个 {@code ServerEvents.recipes} 里 311 条配方一条都没建出来。
 * <p>⇒ 本类**只有一个** {@code tagFluid(String, long)}，故意不做任何重载。
 */
public final class LensCircuitFluidTags {

    private LensCircuitFluidTags() {}

    /**
     * 造一个【标签】流体原料。
     *
     * @param tag    不带前导 {@code #} 的标签 id，例 {@code "forge:photoresist"}
     * @param amount 单位 mB
     * @return GTCEu 自己的 {@link FluidIngredient}（会被 KubeJS 的
     *         {@code FluidIngredientJS.of} 的 {@code instanceof FluidIngredient} 分支接住）
     */
    public static FluidIngredient tagFluid(String tag, long amount) {
        if (tag == null || tag.isEmpty()) {
            throw new IllegalArgumentException("[SHANHAI-L2C] tagFluid 收到空标签");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("[SHANHAI-L2C] tagFluid 收到非正数量：" + amount);
        }
        return FluidIngredient.of(TagKey.create(Registries.FLUID, new ResourceLocation(tag)), amount);
    }
}
