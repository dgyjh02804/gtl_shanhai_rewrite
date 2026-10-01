package com.shanhai.client.jei;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.integration.jei.recipe.GTRecipeWrapper;
import com.shanhai.ShanhaiMod;
import com.shanhai.common.jei.RecipeIngredientOrdering;
import com.shanhai.common.jei.RecipeIngredientOrdering.InputClass;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 *   研磨机 gtceu:macerator ⇒ 是【锭】的排第 1 档、是【宝石】的排第 2 档，其余不动
 *   提取机 gtceu:extractor ⇒ 是【锭】的排第 1 档、是【粉】的排第 2 档，其余不动
 * </pre>
 * 档位表是**数据**（{@code RecipeIngredientOrdering.TIER_TABLE}）⇒
 * 研磨机里"是粉的"**不会**被提前，提取机里"是宝石的"**也不会**被提前 ——
 * 这正是用户口径的要求（别做成一套通用规则）。
 *
 * <h2>2. 「含哪种形态」怎么问出来</h2>
 * 对一条 {@link GTRecipeWrapper}：取 {@code recipe.inputs.get(ItemRecipeCapability.CAP)}
 * → 每个 {@link Content} 的 {@code getContent()}（{@link Ingredient}（GT 的 {@code SizedIngredient} 是子类）或 {@link ItemStack}）
 * → 展开成 {@link ItemStack} → 对每个栈：
 * <ul>
 *   <li>id 口径：{@code BuiltInRegistries.ITEM.getKey(item).getPath()} → {@code classifyIdPath}；</li>
 *   <li>标签口径：{@code item.builtInRegistryHolder().tags()} → {@code classifyTag}。</li>
 * </ul>
 * 两个口径的结果**取并集**（依据见纯核的注释：`forge:gems/diamond` 的成员是 `minecraft:diamond`，
 * id 里根本没有 "gem"；而 `gtceu:ruby_gem` 有 `_gem` 但也要能靠 id 认出来）。
 *
 * <h2>3. 一次性日志</h2>
 * 每个目标分类**每局一行** {@code [SHANHAI-JEIORDER]}：总条数、各形态命中数、
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
        InputClass[] tiers = RecipeIngredientOrdering.tiersFor(recipeTypeUid);
        if (tiers == null) {
            return recipes;
        }
        IdentityHashMap<T, Integer> ranks = new IdentityHashMap<>(recipes.size() * 2);
        int[] tierHits = new int[tiers.length];
        for (int i = 0; i < recipes.size(); i++) {
            T element = recipes.get(i);
            if (ranks.containsKey(element)) {
                continue;
            }
            Set<InputClass> present = classesOf(element);
            ranks.put(element, RecipeIngredientOrdering.rank(recipeTypeUid, present));
            for (int t = 0; t < tiers.length; t++) {
                if (present.contains(tiers[t])) {
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

    /** 一条配方输入里出现的全部材料形态（并集；不是 GT 配方 ⇒ 空集 ⇒ 永远落在"其它"档）。 */
    public static Set<InputClass> classesOf(Object recipe) {
        if (!(recipe instanceof GTRecipeWrapper wrapper)) {
            return EnumSet.noneOf(InputClass.class);
        }
        GTRecipe gtRecipe = wrapper.recipe;
        if (gtRecipe == null || gtRecipe.inputs == null) {
            return EnumSet.noneOf(InputClass.class);
        }
        List<Content> contents = gtRecipe.inputs.get(ItemRecipeCapability.CAP);
        if (contents == null || contents.isEmpty()) {
            return EnumSet.noneOf(InputClass.class);
        }
        Set<InputClass> out = EnumSet.noneOf(InputClass.class);
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

    /** 单个物品栈：id 口径 与 标签口径 取并集。 */
    private static void collectClasses(ItemStack stack, Set<InputClass> out) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null) {
            InputClass byId = RecipeIngredientOrdering.classifyIdPath(id.getPath());
            if (byId != null) {
                out.add(byId);
            }
        }
        for (TagKey<Item> tag : item.builtInRegistryHolder().tags().toList()) {
            ResourceLocation tagId = tag.location();
            InputClass byTag = RecipeIngredientOrdering.classifyTag(tagId.getNamespace(), tagId.getPath());
            if (byTag != null) {
                out.add(byTag);
            }
        }
    }

    /** 每个分类每局一行：总条数 / 各档命中数 / 重排前后前 5 个配方 id。 */
    private static <T> void logOnce(String recipeTypeUid, List<T> before, List<T> after,
                                    InputClass[] tiers, int[] tierHits) {
        try {
            if (recipeTypeUid == null || LOGGED.putIfAbsent(recipeTypeUid, Boolean.TRUE) != null) {
                return;
            }
            StringBuilder hits = new StringBuilder();
            for (int i = 0; i < tiers.length; i++) {
                if (i > 0) {
                    hits.append("、");
                }
                hits.append(tierName(tiers[i])).append(' ').append(tierHits[i]).append(" 条");
            }
            ShanhaiMod.LOGGER.info(
                    "[SHANHAI-JEIORDER] JEI 分类内排序已生效：{} 本批 {} 条配方；命中档位[{}]（档内保持原顺序）；重排前前 5 条={} ；重排后前 5 条={}",
                    recipeTypeUid, before.size(), hits, head(before), head(after));
        } catch (Throwable ignored) {
            // 日志不能成为失败点
        }
    }

    private static String tierName(InputClass inputClass) {
        switch (inputClass) {
            case INGOT:
                return "锭";
            case GEM:
                return "宝石";
            case DUST:
                return "粉";
            default:
                return String.valueOf(inputClass);
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
