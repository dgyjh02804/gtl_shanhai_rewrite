package com.shanhai.integration.jei;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.jei.IngredientIO;
import com.lowdragmc.lowdraglib.utils.CycleItemStackHandler;
import com.shanhai.ShanhaiMod;
import com.shanhai.common.text.ShanhaiTextParser;
import com.shanhai.machine.module.ModuleLevelCondition;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 山海重构 · <b>JEI 配方页「物质模块催化剂展示槽」</b>（纯展示层）。
 *
 * <h2>0. 它解决什么（用户原始需求逐字）</h2>
 * <blockquote>
 * 「我希望 jei 右键物质模块（显示用途），可以看到物质模块作为我们特殊的催化剂的配方也可以出现」
 * </blockquote>
 *
 * <h2>1. 为什么需要它（现状，已由前一轮方案研究实测）</h2>
 * <p>物质模块当"催化剂"在本工程有两种形态：
 * <ul>
 *   <li><b>形态乙 · 真催化剂</b>（14 条，全在 {@code gtceu:primitive_blast_furnace} 上）：
 *       KJS 侧写的是 {@code .notConsumable(模块)} ⇒ 模块<b>在 {@code recipe.getInputs()} 里</b>
 *       ⇒ JEI 天然就能按"用途"查到它。</li>
 *   <li><b>形态甲 · 等级门槛</b>（41 条，落在这 8 个山海自有类型上）：
 *       KJS 侧写的是 {@code .addCondition(new ModuleLevelCondition(...))} ⇒ 模块<b>不在
 *       {@code recipe.getInputs()} 里</b>，只在 {@code recipe.conditions} 里
 *       ⇒ <b>JEI 的「用途」查不到它</b>（JEI 的 {@code U} 键按 ingredient 反查，完全不看 conditions）。</li>
 * </ul>
 * <p>🔴 形态甲<b>不能</b>改成形态乙：模块机的模块槽是 {@code IO.NONE}（{@code PrimordialModuleMachine}
 * 里 {@code new NotifiableItemStackHandler(this, 1, IO.NONE, IO.BOTH)}），而
 * {@code RecipeRunner} 只查 {@code IO.IN} / {@code IO.BOTH} 两行 ⇒ 改了会匹配不上。
 * <b>门槛是模块机上唯一可行的形态，所以本类一个字都不动配方数据。</b>
 *
 * <h2>2. 机制（为什么"插一个 CATALYST 槽"就能被「用途」查到）</h2>
 * <p>以下 5 个环节的字节码证据在
 * {@code handoff\outbound\jei-物质模块催化剂-方案.md} §1.7；本类只依赖它们：
 * <ol>
 *   <li>{@code GTRecipeWidget} 构造的最后一步调 {@code GTRecipeTypeUI.appendJEIUI(recipe, this)}；</li>
 *   <li>{@code appendJEIUI} 本体就是 {@code uiBuilder.accept(recipe, group)}；</li>
 *   <li>{@code ModularUIRecipeCategory.setRecipe} 遍历
 *       {@code modularUI.getFlatWidgetCollection()}（整棵扁平的 widget 树），
 *       把每个 {@code IRecipeIngredientSlot} 按 {@code getIngredientIO()} 映射成 JEI 的槽角色 ——
 *       {@code IngredientIO.CATALYST} → {@code RecipeIngredientRole.CATALYST}；</li>
 *   <li>JEI 为<b>每一种</b> {@code RecipeIngredientRole} 都建了索引表
 *       （{@code RecipeManagerInternal} 构造器里的 {@code RecipeIngredientRole.values()} 循环）；</li>
 *   <li>JEI 的<b>「用途」键同时查 {@code INPUT} 与 {@code CATALYST} 两种角色</b>
 *       （{@code FocusInputHandler.handleUserInput} 里的 {@code List.of(INPUT, CATALYST)}）。</li>
 * </ol>
 * ⇒ 只要往配方页的 widget 树里塞一个 {@code IngredientIO.CATALYST} 的 {@code SlotWidget}，
 * 槽里那个模块就会被「用途」查到。而且语义上它<b>真的是</b>"催化剂"，不是 hack。
 *
 * <h2>3. 🔴 本类只改展示层（红线）</h2>
 * <p>不碰 {@code GTRecipe.inputs} / 不碰 {@code GTRecipe.conditions} / 不碰 {@code RecipeRunner} /
 * 不碰任何配方生成流程。这个槽只活在 {@code GTRecipeWidget} 这棵<b>展示用</b> widget 树里，
 * {@code RecipeRunner} 从头到尾看不到它 ⇒ <b>「门槛不占输入槽」这个卖点完整保留</b>。
 *
 * <h2>4. 🔴 回调必须自己 try/catch（{@code safeCallPlugin} 不保护展示槽）</h2>
 * <p>JEI 的 {@code PluginManager.safeCallPlugin} 只保护<b>插件</b>抛出的异常；
 * {@code appendJEIUI} 是 GTCEu 在构造 {@code GTRecipeWidget} 时调的，<b>不经过那层保护</b>。
 * 回调抛异常会让这个配方页构造失败。⇒ 本类的入口 {@link #append} 整体 try/catch，
 * 最坏情况是「少一个槽」，绝不是「配方页崩」。
 *
 * <h2>5. 什么时候【不加】槽（要求 4 / 要求 5）</h2>
 * <p>🔴 <b>2026-10-01 扩了"挂载范围"，但本条过滤一个字都没改</b>：
 * 挂载范围从"手工白名单 9 个类型"（当天那个"漏挂 worldline_cutting"的 bug 就在那里）
 * 改成 <b>「全部山海配方类型」</b>（真源 = {@code ShanhaiRecipeTypes} 注册期自动登记的列表，
 * 见 {@code installModuleCatalystSlotUi()}），而"哪些配方页真的长槽"<b>仍然只由下面这条过滤决定</b>
 * ⇒ <b>"挂载"≠"显示"：多挂不会多出槽</b>（逐类离线实测读数见
 * {@code handoff\outbound\展示槽-全类型挂载.md} §3）。
 * <p>{@link #firstGateModule} 返回 null 时一个槽都不加。null 的三条来源：
 * <ol>
 *   <li><b>这条配方没有 {@code ModuleLevelCondition}</b> ⇒ 什么都不加。
 *       <b>不给任何配方凭空加槽</b>（例如同类型里那些不带门槛的配方，以及全部非山海类型）。</li>
 *   <li>{@code module_id} 剥掉 {@code "Nx "} 前缀后是空串
 *       （= {@code createTemplate()} 造出来的未配置模板）⇒ 不加。</li>
 *   <li>那个 id 解不出已注册的非 AIR 物品 ⇒ 不加（否则会画一个空气槽）。</li>
 * </ol>
 * <p>🔴 <b>关于"降级通道 {@code moduleLevelFallbackCatalyst}"的兜底选择 —— 明确选【不加槽】，理由两条：</b>
 * <ol>
 *   <li><b>那个字段在 jar 侧根本读不到</b>：{@code moduleLevelFallbackCatalyst} 只活在
 *       {@code kubejs\server_scripts\[server_scripts]shanhai_recipes.js} 的数据表里，
 *       <b>从不写进配方的 JSON / 网络包</b>（发射代码是 {@code if (!useLevelGate && r.moduleLevelFallbackCatalyst)
 *       b = b.notConsumable(...)}；门槛可用时这一支根本不执行）。⇒ "用降级通道那个模块兜底"
 *       在 Java 侧<b>不可实现</b>，不是"不想做"。</li>
 *   <li><b>真的降级了就不需要本类</b>：门槛不可用时那 41 条会退回
 *       {@code .notConsumable(模块)}（形态乙）⇒ 模块<b>重新变成 INPUT ingredient</b>
 *       ⇒ JEI 的「用途」<b>本来就能查到它</b>。此时再插一个 CATALYST 槽只会让同一个模块
 *       在同一个配方页上出现两次（一次输入、一次催化剂），纯粹是视觉噪声。</li>
 * </ol>
 *
 * <h2>6. 槽位坐标怎么定（为什么是"右下角空白处"）</h2>
 * <p>{@code appendJEIUI} 收到的是 <b>整个 {@code GTRecipeWidget}</b>（它自己就是一个
 * {@code WidgetGroup}），而它构造时尺寸 = {@code GTRecipeTypeUI.getJEISize()} =
 * {@code (max(模板宽,150), 模板高 + 5 + (maxTooltips + dataInfos) * 10)}。
 * <p>山海全部 8 个门槛类型都是 {@code .setMaxTooltips(4)} 且没有 dataInfo
 * ⇒ 预留的 40 px 那个文本区里，EU 行占 20、1 条条件行占 10
 * （条件行的 y = 模板高 + 5 + (EUt>0 ? 20 : 0) + 10*k）。**本竖切类型 6 条门槛配方的 EUt 全部 &gt; 0**
 * ⇒ 文本区只空出约 1 px ⇒ <b>文本区里没有位置可放 18×18 的槽</b>。
 * <p>而 {@code GTRecipeWidget.addButtons()} 把 GTCEu 自己的 15×15「ID」按钮固定放在
 * {@code (getSize().width - xOffset - 18, getSize().height - 30)} —— 说明<b>右下角这一格
 * 在模板网格之外</b>（{@code height - 模板高 = 45 ≥ 30}）。本类就把展示槽放在那格<b>左边一格</b>
 * （相隔 2 px，见 {@link #RIGHT_MARGIN}），保证：
 * <ul>
 *   <li>一定在 group 尺寸之内（不会因为越界而不画）；</li>
 *   <li>一定不在配方槽网格上（{@code y ≥ 模板底 + 15}）；</li>
 *   <li>与 GTCEu 自己的按钮不重叠。</li>
 * </ul>
 * <p>⚠️ 版本相关的兜底：万一 {@code maxTooltips} 很小导致 {@code height - 30} 落进模板内，
 * 本类会退到"模板底 + 1"并<b>把 group 撑高到刚好装得下</b>（{@code appendJEIUI} 发生在
 * {@code GTRecipeWrapper} 的 {@code super(new GTRecipeWidget(...))} 求值期间，
 * 早于 {@code ModularWrapper} 读 {@code getSize()} 建 {@code ModularUI} ⇒ 撑高是生效的）。
 *
 * <h2>7. ⚠️ 本类【未做实机验证】的地方（诚实边界，别当成已知为真）</h2>
 * <ul>
 *   <li>JEI 里这个槽的<b>实际观感</b>（位置是否满意、深色槽底与 JEI 背景是否协调）—— 没跑过游戏。</li>
 *   <li>JEI 是否会对超出原配方背景图范围的绘制做裁剪 —— 没跑过游戏。若被裁剪，槽仍然<b>会被索引</b>
 *       （{@code setRecipe} 遍历 widget 树时没有边界检查），只是可能看不到图。</li>
 *   <li>配方页反复打开时 layout 缓存的一致性 —— 没跑过游戏。</li>
 *   <li>EMI / REI / 罗盘配方预览是否也长出这个槽（{@code GTRecipeWidget} 被那几家复用）——
 *       本实例只装 JEI，影响面未核。</li>
 * </ul>
 */
public final class ModuleCatalystSlotUI {

    /** 展示槽边长（与配方页其它物品槽一致）。 */
    private static final int SLOT_SIZE = 18;

    /**
     * 距右边缘的留白。
     * <p>GTCEu 自己的 15×15「ID」按钮在 {@code getSize().width - xOffset - 18}
     * ⇒ 留白 38 时本槽右边缘落在 {@code width - xOffset - 20}，与按钮左边缘相隔 2 px。
     */
    private static final int RIGHT_MARGIN = 38;

    /**
     * 距底边缘的留白。
     * <p>与 GTCEu 自己那个按钮同一条横线（{@code getSize().height - 30}）——
     * 这一格在模板网格之外是 GTCEu 自己用行为证明过的。
     */
    private static final int BOTTOM_MARGIN = 30;

    /** 一次性日志的上限（避免反复开 JEI 把日志刷爆）。 */
    private static final int LOG_CAP = 128;

    /** 已经打过"少一个槽"WARN 的异常类型集合（每类只报一次）。 */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    /** 已经打过"已挂上催化剂展示槽"INFO 的配方 id（每个配方只报一次）。 */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private ModuleCatalystSlotUI() {}

    // ═══════════════════════════════ 挂在配方类型上的回调 ═══════════════════════════════

    /**
     * 把本类的展示槽回调挂到某个配方类型上。
     *
     * <p>⚠️ 只允许在配方类型<b>已注册之后</b>调用（{@code ShanhaiRecipeTypes.init()} 末尾即是）。
     * <p>⚠️ 这是<b>整类型一个</b>回调，不是每条配方一个：回调里自己按 {@code recipe.conditions} 过滤。
     * <p>⚠️ <b>不要指望链式叠加</b>：{@code GTRecipeTypeUI.uiBuilder} 是 {@code protected} 的
     * 单个字段，本类读不到旧值 ⇒ 调本方法会<b>覆盖</b>该类型原有的 uiBuilder。
     *
     * <p>🔴 <b>2026-10-01 重新取证（本方法的作用范围已扩到"全部山海配方类型"，
     * 所以"会不会覆盖别人的"这句话必须重新证明，不能沿用旧结论）</b>：
     * <ul>
     *   <li>{@code GTRecipeTypeUI.setUiBuilder} 的字节码（{@code javap -p -c}）就是
     *       {@code aload_0 / aload_1 / putfield uiBuilder / return} —— <b>无条件赋值，没有 getter，
     *       没有链式叠加，也不存在"先读旧值再合并"的可能</b>（所以"谁最后写谁赢"）。</li>
     *   <li>检索范围：<b>124 个 jar</b>（{@code shanhai-rewrite\libs\*.jar} 全部 ＋ 测试实例
     *       {@code GTL山海9.10test\mods\*.jar} 全部 ＋ Forge {@code _mapped_official_} 用户 dev jar），
     *       逐 class 扫常量池里的 {@code uiBuilder} / {@code setUiBuilder} 名字。</li>
     *   <li>命中并逐个 {@code javap -p -c} 复核后，<b>真正调 {@code setUiBuilder} 的只有 4 个上游类</b>：
     *       GTCEu {@code GTRecipeTypes}（create_mixer 等）/ GTCEu {@code GCyMRecipeTypes}
     *       （alloy_blast）/ gtlcore {@code GTLRecipeTypes} / gtladditions {@code GTLAddRecipesTypes}
     *       ＋ GTCEu 的 KJS 构建器 {@code GTRecipeTypeBuilder}（它把 JS 侧设的值转交过去）。
     *       <b>全部是"在自己那条 register 链里、紧接着 putstatic 自己那个类型字段"的就地调用</b>
     *       （字节码上下文可见：{@code setUiBuilder} 后紧跟 {@code putstatic Xxx_RECIPES}）——
     *       <b>没有任何一个类遍历已注册的配方类型去批量设 uiBuilder</b>。</li>
     *   <li>KubeJS 侧：全工程（本仓库 ＋ 实例 {@code kubejs\}）扫 {@code uiBuilder} = <b>0 命中</b>，
     *       ⇒ 没有脚本会给我们这些类型设 uiBuilder。</li>
     *   <li>⇒ <b>结论：不会覆盖别人的东西。</b>山海自有类型都是本工程自己 {@code register} 出来的
     *       新对象（id 不重复），上游那 5 个写入点只会写它们自己的类型
     *       ⇒ 我们的类型上没有任何"别人的 uiBuilder"可被覆盖（本方法也是"谁最后写谁赢"里的最后那个）。
     *       ⚠️ 边界（未证实）：若将来某个 addon 写出"遍历全部已注册类型设 uiBuilder"这种代码，
     *       本条结论即失效 —— 判据 = 重跑上面那次全 jar 常量池扫描，看有没有新的命中类。</li>
     * </ul>
     *
     * @param type 目标配方类型；为 null 时静默跳过（注册失败时不要在这里二次崩）
     * @return 是否成功挂上
     */
    public static boolean install(@Nullable GTRecipeType type) {
        if (type == null) {
            return false;
        }
        try {
            type.getRecipeUI().setUiBuilder(ModuleCatalystSlotUI::append);
            return true;
        } catch (Throwable t) {
            // 挂不上 = 该类型照旧（没有展示槽），绝不能把初始化流程带崩。
            warnOnce("install:" + t.getClass().getName(),
                    "[SHANHAI-JEI] 给配方类型 {} 挂「模块催化剂展示槽」失败 ⇒ 该类型照旧（配方页没有展示槽）。"
                            + "配方数据一个字都没动，功能不受影响。",
                    String.valueOf(type.registryName), t);
            return false;
        }
    }

    // ═══════════════════════════════ 展示层回调本体 ═══════════════════════════════

    /**
     * {@code GTRecipeTypeUI#appendJEIUI} 的回调入口 —— <b>整体 try/catch</b>（类注释 §4）。
     *
     * <p>⚠️ 本方法会在<b>客户端</b>构造每个配方页时被调用（含 JEI 配方页、机器 GUI 内的配方预览）。
     * 它不是热路径（JEI 有 layout 缓存），但绝不能抛。
     */
    public static void append(@Nullable GTRecipe recipe, @Nullable WidgetGroup group) {
        try {
            appendChecked(recipe, group);
        } catch (Throwable t) {
            // 最坏情况 = 少一个槽，不是配方页崩。
            warnOnce("append:" + t.getClass().getName(),
                    "[SHANHAI-JEI] 给配方 {} 插「模块催化剂展示槽」时抛异常 ⇒ 本条配方页少这一个槽（其余照常渲染）。"
                            + "这不是配方数据的问题，配方匹配与执行完全不受影响。",
                    recipe == null ? "null" : String.valueOf(recipe.id), t);
        }
    }

    private static void appendChecked(@Nullable GTRecipe recipe, @Nullable WidgetGroup group) {
        if (recipe == null || group == null) {
            return;
        }
        // 要求 4 / 要求 5：没有门槛条件、或模块读不到 ⇒ 一个槽都不加。
        final ItemStack module = firstGateModule(recipe);
        if (module == null || module.isEmpty()) {
            return;
        }

        final Component tooltip = buildTooltip(module);

        final SlotWidget slot = new SlotWidget();
        slot.initTemplate();
        slot.setHandlerSlot(
                new CycleItemStackHandler(Collections.singletonList(Collections.singletonList(module))), 0);
        // 🔴 关键：CATALYST，不是 INPUT —— 语义上真的是催化剂，而且 JEI 的「用途」键会查它。
        slot.setIngredientIO(IngredientIO.CATALYST);
        slot.setCanTakeItems(false);
        slot.setCanPutItems(false);
        slot.setBackgroundTexture(GuiTextures.SLOT);
        // 提示行：组件在这里就构造好，渲染期只做 list.add ⇒ 渲染路径上不可能抛。
        slot.setOnAddedTooltips((sw, list) -> {
            if (list != null) {
                list.add(tooltip);
            }
        });

        placeSlot(recipe, group, slot);
        group.addWidget(slot);

        logOnce(recipe, module);
    }

    /**
     * 找这条配方<b>实际要求的那一个模块</b>（从 {@code recipe.conditions} 读，<b>不硬编码任何模块</b>）。
     *
     * <p>取第一个可解的 {@link ModuleLevelCondition}；取不到返回 {@code null}（调用方据此不加槽）。
     * <p>⚠️ 用 {@link ModuleLevelCondition#effectiveModuleId()}（会剥掉 KJS 侧的 {@code "Nx "} 前缀），
     * <b>不要</b>直接用 {@code moduleId} 字段 —— 配方侧写的是
     * {@code '1x shanhai:introductory_material_module'}，裸用会让 {@code ResourceLocation} 解析失败。
     */
    @Nullable
    static ItemStack firstGateModule(GTRecipe recipe) {
        if (recipe.conditions == null || recipe.conditions.isEmpty()) {
            return null;
        }
        for (RecipeCondition condition : recipe.conditions) {
            if (!(condition instanceof ModuleLevelCondition)) {
                continue;
            }
            final String id = ((ModuleLevelCondition) condition).effectiveModuleId();
            if (id == null || id.isEmpty()) {
                continue;
            }
            final ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) {
                continue;
            }
            final Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null || item == Items.AIR) {
                continue;
            }
            return new ItemStack(item);
        }
        return null;
    }

    private static Component buildTooltip(ItemStack module) {
        // 「催化剂」是给玩家看的语义说明：它不占输入槽、不消耗，只是准入门槛。
        // 🔴 2026-09-30：名字先剥 `&$…-` 前缀码 —— 本行与我们自己的 `§b`/`§7` 同处一行，
        //    不剥就命中 ShanhaiTextParser 的「§ 与 &$ 混用 ⇒ 交回原版」规则 ⇒ 美化码原样露出来
        //    （同族 bug 的现场与离线读数见交付报告 §14；用户点名的是 ModuleLevelCondition 那一行）。
        return Component.literal("§b催化剂：")
                .append(Component.literal(ShanhaiTextParser.stripStyleCode(module.getHoverName().getString())))
                .append(Component.literal(" §7（不占输入槽·不消耗）"));
    }

    /**
     * 把槽放到右下角空白处（坐标怎么来的见类注释 §6）。
     *
     * <p>坐标全部在这个 {@code WidgetGroup} 自己的局部坐标系里。
     */
    private static void placeSlot(GTRecipe recipe, WidgetGroup group, SlotWidget slot) {
        final int groupW = group.getSize().width;
        final int groupH = group.getSize().height;

        // GTRecipeWidget 自己的 self position 就是 xOffset（见其 ctor 的第 1 个实参），
        // 而它自己的子 widget（条件文本、ID 按钮）都把 x 减掉了 xOffset ⇒ 这里照样减，保持同一口径。
        final int xOffset = computeXOffset(recipe);

        final int x = Math.max(1, groupW - xOffset - RIGHT_MARGIN);
        int y = groupH - BOTTOM_MARGIN;

        // 兜底 1：若那一行落进了配方槽模板里（maxTooltips 很小的情况），退到模板底 + 1。
        final int templateBottom = firstChildBottom(group);
        if (templateBottom > 0 && y < templateBottom + 1) {
            y = templateBottom + 1;
        }
        // 兜底 2：若还不装得下，把 group 撑高到刚好装得下（见类注释 §6 的时序说明）。
        final int need = y + SLOT_SIZE + 1;
        if (need > groupH) {
            group.setSize(groupW, need);
        }
        slot.setSelfPosition(x, y);
    }

    /**
     * {@code GTRecipeWidget.getXOffset} 的<b>同口径复算</b>
     * （那边是 {@code private static}，本类读不到，只能照公式重算一遍）。
     *
     * <pre>
     *   xOffset = (originalWidth == jeiWidth) ? 0 : (jeiWidth - originalWidth) / 2
     * </pre>
     * ⚠️ 公式来自 {@code javap -p -c com.gregtechceu.gtceu.integration.GTRecipeWidget} 的
     * {@code getXOffset} 方法（偏移 0–54），不是猜的。
     */
    private static int computeXOffset(GTRecipe recipe) {
        try {
            if (recipe.getType() == null) {
                return 0;
            }
            final GTRecipeTypeUI ui = recipe.getType().getRecipeUI();
            if (ui == null) {
                return 0;
            }
            final int jeiW = ui.getJEISize().width;
            final int origW = ui.getOriginalWidth();
            return jeiW == origW ? 0 : (jeiW - origW) / 2;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** 第一个子 widget（= {@code createUITemplate} 造出来的配方模板组）的底边 y。 */
    private static int firstChildBottom(WidgetGroup group) {
        if (group.widgets == null || group.widgets.isEmpty()) {
            return 0;
        }
        final Widget first = group.widgets.get(0);
        if (first == null) {
            return 0;
        }
        return first.getPositionY() + first.getSizeHeight();
    }

    // ═══════════════════════════════ 日志（可 grep 的取证痕迹） ═══════════════════════════════

    /**
     * 每个配方只报一次 {@code [SHANHAI-JEI]} INFO。
     *
     * <p>用途 = <b>给"没跑游戏也能判"留一条机器可读的痕迹</b>：客户端 {@code logs\latest.log} 里
     * 出现几条 {@code recipe=… module=…}，就说明有几条配方真的挂上了展示槽。
     * 上限 {@link #LOG_CAP} 条，防刷屏。
     */
    private static void logOnce(GTRecipe recipe, ItemStack module) {
        final String key = String.valueOf(recipe.id) + " "
                + String.valueOf(ForgeRegistries.ITEMS.getKey(module.getItem()));
        if (LOGGED.size() >= LOG_CAP || !LOGGED.add(key)) {
            return;
        }
        ShanhaiMod.LOGGER.info("[SHANHAI-JEI] 催化剂展示槽已挂上（配方页）：recipe={} module={} （槽角色=CATALYST，不占输入槽、不消耗）",
                recipe.id, ForgeRegistries.ITEMS.getKey(module.getItem()));
    }

    /** 每类失败只报一次的 WARN（避免反复开 JEI 刷屏）。 */
    private static void warnOnce(String key, String format, Object arg, Throwable t) {
        if (!WARNED.add(key)) {
            return;
        }
        ShanhaiMod.LOGGER.warn(format, arg, t);
    }
}
