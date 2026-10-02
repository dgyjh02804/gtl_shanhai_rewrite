package com.shanhai.client.jei;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;
import com.shanhai.ShanhaiMod;
import com.shanhai.common.jei.RecipeIngredientOrdering;
import com.shanhai.common.jei.RecipeIngredientOrdering.InputClass;
import com.shanhai.common.jei.RecipeIngredientOrdering.Tier;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 山海重构 · <b>JEI「分类内排序」的客户端侧收口</b>（判据与档位表在纯核
 * {@link RecipeIngredientOrdering}，本类只负责"从一条 JEI 配方问出它含哪些材料形态"）。
 *
 * <h2>1. 各分类的口径（**故意不一样**）</h2>
 * <pre>
 *   研磨机     gtceu:macerator   ⇒ 输入含【锭】排第 1 档、含【宝石】排第 2 档，其余不动
 *   提取机     gtceu:extractor   ⇒ 输入含【锭】排第 1 档、含【粉】排第 2 档，其余不动
 *   原版熔炉烧炼 minecraft:furnace ⇒ 【单质粉 → 锭】排第 1 档、【原矿 → 锭】排第 2 档，其余不动
 * </pre>
 * 档位表是**数据**（{@code RecipeIngredientOrdering.TIER_TABLE}）⇒
 * 研磨机里"是粉的"**不会**被提前，提取机里"是宝石的"**也不会**被提前 ——
 * 这正是用户口径的要求（别做成一套通用规则）。
 * <p>第三行是本轮新增：它是表里<b>第一个"要看输出"的分类</b>（{@link Tier#needsOutput()}）。
 *
 * <h2>2. 「含哪种形态」怎么问出来（本轮从"只问输入"扩成"输入 ＋ 输出"）</h2>
 * <b>两条互不相干的配方族都要认</b>：
 * <ol>
 *   <li><b>GT 配方</b>（{@link GTRecipeWrapper}）：输入取 {@code gtRecipe.inputs.get(ItemRecipeCapability.CAP)}、
 *       输出取 {@code gtRecipe.outputs.get(ItemRecipeCapability.CAP)}；</li>
 *   <li>🔴 <b>原版烹饪配方</b>（{@link AbstractCookingRecipe}）—— 本版新增。
 *       JEI 的「烧炼」分类元素类型实测就是 {@code net.minecraft.world.item.crafting.SmeltingRecipe}
 *       （{@code javap mezz.jei.library.plugins.vanilla.cooking.FurnaceSmeltingCategory} ⇒
 *       {@code extends AbstractCookingCategory<SmeltingRecipe>}），
 *       <b>不是</b> {@code GTRecipeWrapper} ⇒ 第二版在这里问出来的永远是空集。</li>
 * </ol>
 * 原版侧的两个取值点（都在 MC 1.20.1 反编译源码里核对过）：
 * <pre>
 *   AbstractCookingRecipe#getIngredients()            → NonNullList&lt;Ingredient&gt;，里面只有那 1 个输入
 *   AbstractCookingRecipe#getResultItem(RegistryAccess) → ItemStack；
 *        🔴 反编译原文是 `return this.f_43730_;` —— **完全忽略入参** ⇒ 传 {@code RegistryAccess.EMPTY} 安全
 * </pre>
 *
 * <h2>3. 每个 {@link Content}/{@link Ingredient} 展开成 {@link ItemStack} 后，两个口径取并集</h2>
 * <ul>
 *   <li>id 口径：{@code BuiltInRegistries.ITEM.getKey(item).getPath()} → {@code classifyIdPathInto}；</li>
 *   <li>标签口径：{@code item.builtInRegistryHolder().tags()} → {@code classifyTagInto}。</li>
 * </ul>
 * 依据见纯核注释：{@code forge:gems/diamond} 的成员是 {@code minecraft:diamond}（id 里没有 "gem"），
 * 而真实配方的输入写的是 <b>tag</b>（{@code forge:dusts/iron}）⇒ 两条都不能少。
 *
 * <h2>4. 🔴 省一遍开销：不看输出的分类**根本不去算输出</h2>
 * {@link #order} 先问 {@link RecipeIngredientOrdering#needsOutput(String)}；
 * 研磨机／提取机是 {@code false} ⇒ 连 {@code outputs} 都不碰（那两个分类一个字节的额外开销都没有）。
 *
 * <h2>5. 一次性日志</h2>
 * 每个目标分类**每局一行** {@code [SHANHAI-JEIORDER]}：总条数、各档命中数、
 * 重排前后各前 5 条的配方 id。三种失败靠它分开：
 * <b>没这行 = 注入没跑</b>；<b>命中数全 0 = 判据没命中</b>；<b>命中数 &gt; 0 而界面没变 = 显示的不是这份列表</b>。
 */
public final class ShanhaiJeiRecipeOrdering {

    /** 已打过日志的分类（进程内一次性）。 */
    private static final Map<String, Boolean> LOGGED = new ConcurrentHashMap<>();

    private static volatile boolean failureLogged = false;

    private ShanhaiJeiRecipeOrdering() {
    }

    /** 这个配方分类在档位表里吗（不在 = 我们不排序）。 */
    public static boolean isTargetCategory(ResourceLocation uid) {
        if (uid == null) {
            return false;
        }
        return RecipeIngredientOrdering.tiersFor(uid.toString()) != null;
    }

    /**
     * 收口：把 JEI 交出来的那份配方列表按该分类的档位表重排（档内保持原顺序）。
     *
     * <p>判据对每个元素只算一次（{@link IdentityHashMap} 记忆化）—— 一页几百条配方，
     * 每条都要展开标签，重复计算没有意义。
     */
    public static <T> List<T> order(List<T> recipes, String recipeTypeUid) {
        if (recipes == null || recipes.isEmpty()) {
            return recipes;
        }
        Tier[] tiers = RecipeIngredientOrdering.tiersFor(recipeTypeUid);
        if (tiers == null) {
            return recipes;
        }
        // 🔴 只有表里真有一档要看输出时，才去算输出形态（第二版的两个分类 ⇒ 这里的开销恒为 0）
        boolean needOutput = RecipeIngredientOrdering.needsOutput(recipeTypeUid);
        IdentityHashMap<T, Integer> ranks = new IdentityHashMap<>(recipes.size() * 2);
        int[] tierHits = new int[tiers.length];
        for (int i = 0; i < recipes.size(); i++) {
            T element = recipes.get(i);
            if (ranks.containsKey(element)) {
                continue;
            }
            Set<InputClass> inputs = inputsOf(element);
            Set<InputClass> outputs = needOutput ? outputsOf(element) : null;
            ranks.put(element, RecipeIngredientOrdering.rank(recipeTypeUid, inputs, outputs));
            for (int t = 0; t < tiers.length; t++) {
                if (tiers[t].matches(inputs, outputs)) {
                    tierHits[t]++;
                }
            }
        }
        List<T> ordered = RecipeIngredientOrdering.stableByRank(recipes, e -> {
            Integer r = ranks.get(e);
            return r == null ? 0 : r;
        });
        logOnce(recipeTypeUid, recipes, ordered, tiers, tierHits);
        return ordered;
    }

    /**
     * 一条配方<b>输入</b>里出现的全部材料形态（并集）。
     * 既不是 GT 配方、也不是原版烹饪配方 ⇒ 空集 ⇒ 永远落在"其它"档。
     */
    public static Set<InputClass> inputsOf(Object recipe) {
        if (recipe instanceof GTRecipeWrapper wrapper) {
            GTRecipe gtRecipe = wrapper.recipe;
            if (gtRecipe == null) {
                return EnumSet.noneOf(InputClass.class);
            }
            return classesOfContents(gtRecipe.inputs);
        }
        if (recipe instanceof AbstractCookingRecipe cooking) {
            // 原版烹饪（熔炉/烟熏/高炉/营火）：输入是那 1 个 Ingredient
            Set<InputClass> out = EnumSet.noneOf(InputClass.class);
            NonNullList<Ingredient> ingredients = cooking.getIngredients();
            if (ingredients != null) {
                for (int i = 0; i < ingredients.size(); i++) {
                    collectClasses(ingredients.get(i), out);
                }
            }
            return out;
        }
        return EnumSet.noneOf(InputClass.class);
    }

    /**
     * 一条配方<b>输出</b>里出现的全部材料形态（并集）。语义与 {@link #inputsOf} 对称。
     *
     * <p>只在档位表里真有"要看输出"的那一档时才会被调用（见 {@link #order}）。
     */
    public static Set<InputClass> outputsOf(Object recipe) {
        if (recipe instanceof GTRecipeWrapper wrapper) {
            GTRecipe gtRecipe = wrapper.recipe;
            if (gtRecipe == null) {
                return EnumSet.noneOf(InputClass.class);
            }
            return classesOfContents(gtRecipe.outputs);
        }
        if (recipe instanceof AbstractCookingRecipe cooking) {
            Set<InputClass> out = EnumSet.noneOf(InputClass.class);
            // 反编译实测：AbstractCookingRecipe#getResultItem 忽略入参（直接 return 结果字段）
            collectClasses(cooking.getResultItem(RegistryAccess.EMPTY), out);
            return out;
        }
        return EnumSet.noneOf(InputClass.class);
    }

    /**
     * GT 的 {@code inputs} 与 {@code outputs} 是**同一张类型**
     * （{@code Map<RecipeCapability<?>, List<Content>>}，javap 实测）⇒ 同一段取法用两次，只是把表换一张。
     */
    private static Set<InputClass> classesOfContents(Map<RecipeCapability<?>, List<Content>> contentsByCapability) {
        Set<InputClass> out = EnumSet.noneOf(InputClass.class);
        if (contentsByCapability == null) {
            return out;
        }
        List<Content> contents = contentsByCapability.get(ItemRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) {
            return out;
        }
        for (int i = 0; i < contents.size(); i++) {
            Content content = contents.get(i);
            if (content != null) {
                collectClasses(content.getContent(), out);
            }
        }
        return out;
    }

    /** {@code Content.getContent()} 的运行时形态：{@link Ingredient}（含 SizedIngredient）或 {@link ItemStack}。 */
    private static void collectClasses(Object content, Set<InputClass> out) {
        if (content instanceof Ingredient ingredient) {
            ItemStack[] stacks = ingredient.getItems();
            if (stacks != null) {
                for (int i = 0; i < stacks.length; i++) {
                    collectClasses(stacks[i], out);
                }
            }
            return;
        }
        if (content instanceof ItemStack stack) {
            collectClasses(stack, out);
        }
    }

    /** 单个物品栈：id 口径 与 标签口径 取并集（两边各自往 {@code out} 里加，不互相覆盖）。 */
    private static void collectClasses(ItemStack stack, Set<InputClass> out) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null) {
            RecipeIngredientOrdering.classifyIdPathInto(id.getPath(), out);
        }
        for (TagKey<Item> tag : item.builtInRegistryHolder().tags().toList()) {
            ResourceLocation tagId = tag.location();
            RecipeIngredientOrdering.classifyTagInto(tagId.getNamespace(), tagId.getPath(), out);
        }
    }

    /** 每个分类每局一行：总条数 / 各档命中数 / 重排前后前 5 个配方 id。 */
    private static <T> void logOnce(String recipeTypeUid, List<T> before, List<T> after,
                                    Tier[] tiers, int[] tierHits) {
        try {
            if (recipeTypeUid == null || LOGGED.putIfAbsent(recipeTypeUid, Boolean.TRUE) != null) {
                return;
            }
            StringBuilder hits = new StringBuilder();
            for (int i = 0; i < tiers.length; i++) {
                if (i > 0) {
                    hits.append('、');
                }
                hits.append(tiers[i].label()).append(' ').append(tierHits[i]).append(" 条");
            }
            ShanhaiMod.LOGGER.info(
                    "[SHANHAI-JEIORDER] JEI 分类内排序已生效：{} 本批 {} 条配方；命中档位[{}]（档内保持原顺序）；重排前前 5 条={} ；重排后前 5 条={}",
                    recipeTypeUid, before.size(), hits, head(before), head(after));
        } catch (Throwable ignored) {
            // 日志不能成为失败点
        }
    }

    /** 前 5 条的 id（或类名），只为了日志里能对上屏幕。 */
    private static <T> String head(List<T> list) {
        int n = Math.min(5, list.size());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(describe(list.get(i)));
        }
        if (list.size() > n) {
            sb.append(", ...");
        }
        return sb.append(']').toString();
    }

    /** GT 配方直接给注册 id（{@code GTRecipe.id} 是 public 字段）；别的包装类型退化成类名。 */
    private static String describe(Object recipe) {
        if (recipe instanceof GTRecipeWrapper wrapper) {
            GTRecipe gtRecipe = wrapper.recipe;
            if (gtRecipe != null && gtRecipe.id != null) {
                return gtRecipe.id.toString();
            }
        }
        if (recipe instanceof AbstractCookingRecipe cooking) {
            // 原版烹饪：getRegistryName 由 JEI 提供，这里没有 JEI 的 category ⇒ 退回配方自身的 id
            ResourceLocation id = cooking.getId();
            if (id != null) {
                return id.toString();
            }
        }
        if (recipe == null) {
            return "null";
        }
        return recipe.getClass().getSimpleName();
    }

    /** 排序出错时的兜底：打一行原始异常，调用方随后按原顺序返回。 */
    public static void logFailureOnce(Throwable throwable) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        try {
            ShanhaiMod.LOGGER.error("[SHANHAI-JEIORDER] 排序失败，已降级为原顺序（JEI 不受影响）", throwable);
        } catch (Throwable ignored) {
            // 日志不能成为失败点
        }
    }
}
