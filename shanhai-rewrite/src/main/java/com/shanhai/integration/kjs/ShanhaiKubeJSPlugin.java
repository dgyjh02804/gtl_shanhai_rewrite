package com.shanhai.integration.kjs;

import com.shanhai.ShanhaiMod;
import com.shanhai.machine.module.ModuleLevelCondition;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.util.ClassFilter;

/**
 * 山海重构的 KubeJS 集成插件 —— 把 {@link ModuleLevelCondition} 暴露给脚本。
 *
 * <h2>1. 为什么需要这个类（KubeJS 脚本无法自己注册 RecipeCondition）</h2>
 * <p>取证（jar 字节码扫描，不是推断）：{@code kubejs-forge-2001.6.5-build.26.jar} 里
 * <b>没有任何</b> {@code recipe/condition} 注册类 ⇒ 脚本侧<b>没有</b>注册 {@code RecipeConditionType} 的手段。
 * 而条件类型必须进 {@code GTRegistries.RECIPE_CONDITIONS}（{@code GTRecipeSchema.CONDITIONS} 那个
 * RecipeKey 要靠它做 id ↔ 类型的往返），所以<b>只能由 jar 侧注册 + 绑定</b>。
 *
 * <h2>2. 🔴 发现机制 = jar 根目录下的 {@code kubejs.plugins.txt}（实测，不是推断）</h2>
 * <p>本机三个 jar 都在自己根目录放了同名文件，内容是<b>一行一个插件 FQCN</b>：
 * <pre>
 *   kubejs-forge-2001.6.5-build.26.jar : dev.latvian.mods.kubejs.forge.BuiltinKubeJSForgePlugin
 *                                        （另有 {@code ...GameStagesIntegration gamestages} 这类"FQCN 空格 参"的行）
 *   gtceu-1.20.1-1.4.4.jar             : com.gregtechceu.gtceu.integration.kjs.GregTechKubeJSPlugin
 *   gtlcore-1.2.3.2.jar                : org.gtlcore.gtlcore.integration.kjs.GTLKubejsPlugin
 *   ldlib-forge-1.20.1-1.0.33.b.jar    : com.lowdragmc.lowdraglib.kjs.LDLibKubeJSPlugin
 * </pre>
 * <p><b>运行期证据</b>（{@code _batchfix-verify\smoke_latest-r4.log}，KubeJS 自己打的）：
 * <pre>
 *   [KubeJS/]: Looking for KubeJS plugins...
 *   [KubeJS/]: Found plugin source kubejs / gtlcore / gtceu / ldlib / meinfinitycell / ...
 *   [KubeJS/]: Plugin ...KubeJSPlugin does not load on server side, skipping
 *   [KubeJS/]: Failed to load plugin ... from source avaritia: java.lang.ClassNotFoundException: ...
 * </pre>
 * ⇒ <b>每个 {@code kubejs.plugins.txt} 都对应一条 {@code Found plugin source <modid>}</b>，
 * 且加载失败会当场报 ERROR 并点名 FQCN。⇒ 落地后有<b>可 grep 的判据</b>：
 * 日志里必须出现 {@code Found plugin source shanhai}，且<b>不得</b>出现指向本类 FQCN 的
 * {@code Failed to load plugin}。
 * <p>本类走的就是这条：{@code src/main/resources/kubejs.plugins.txt} = {@code com.shanhai.integration.kjs.ShanhaiKubeJSPlugin}。
 * <p>⚠️ 与 {@code @Mod}/{@code @KubeJSPlugin} 注解无关：KubeJS 6 <b>不扫注解</b>，只读这个清单文件
 * （三个自带插件的 jar 里都没有对应的 {@code META-INF/services} 条目，取证见上）。
 *
 * <h2>3. 两个注册点分别解决什么</h2>
 * <ul>
 *   <li>{@link #registerBindings} —— <b>本轮唯一必需的</b>：{@code event.add("ModuleLevelCondition", …)}
 *       把类对象放进 server 脚本的全局作用域，脚本里才写得出
 *       {@code new ModuleLevelCondition('shanhai:introductory_material_module', 1)}。
 *       脚本侧的探测写法是 {@code typeof ModuleLevelCondition !== 'undefined'}
 *       （{@code shanhai_pf_recipes.js:224}）—— 未绑定时该表达式为 false，脚本自动退回旧形态，不会抛 ReferenceError。</li>
 *   <li>{@link #registerClasses} —— 照 {@code GTLKubejsPlugin} / {@code GregTechKubeJSPlugin} 的形状写的
 *       {@code filter.allow(...)}，让脚本可以用 {@code Java.loadClass("com.shanhai.machine.module.ModuleLevelCondition")}。
 *       <p>⚠️ <b>诚实边界</b>：实测 {@code ClassFilter.isAllowed0} 的字节码是「只查 denyStrong / allowStrong / denyWeak，
 *       查完就 {@code return 1}」⇒ 本整合包里它<b>本就是"默认放行、按黑名单拒绝"</b>
 *       （旁证：{@code smoke_latest-r4.log} 里 {@code shanhai_test_recipes.js#164: Loaded Java class
 *       'com.shanhai.common.recipe.ShanhaiRecipeStats'} —— 那时本工程<b>还没有</b>任何 KubeJS 插件）。
 *       ⇒ 这几行 {@code allow} 对本项目<b>很可能是冗余的</b>；保留是因为它与上游/同族插件写法一致、且无副作用。
 *       <b>不要把它当成"起作用了"的证据。</b></li>
 * </ul>
 */
public class ShanhaiKubeJSPlugin extends KubeJSPlugin {

    @Override
    public void registerClasses(ScriptType type, ClassFilter filter) {
        super.registerClasses(type, filter);
        filter.allow(ModuleLevelCondition.class);
    }

    @Override
    public void registerBindings(BindingsEvent event) {
        super.registerBindings(event);
        event.add("ModuleLevelCondition", ModuleLevelCondition.class);
        // 一次性自证：插件被加载时打一行，避免"没加载"与"加载了但没人用"在日志上长得一样。
        ShanhaiMod.LOGGER.info("[SHANHAI-KJS] ShanhaiKubeJSPlugin 已加载：已绑定全局类 "
                + "ModuleLevelCondition（构造器 (String 物质模块物品id, int 数量)）；"
                + "配方条件类型 module_level 的注册在 ShanhaiRegistry#onRecipeConditionRegister。");
    }
}
