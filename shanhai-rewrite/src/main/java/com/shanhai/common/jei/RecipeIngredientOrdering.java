package com.shanhai.common.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * 山海重构 · <b>JEI「分类内排序」的【纯逻辑核】（第二版：按配方类型分档）</b>。
 *
 * <h2>0. 这一版为什么变了</h2>
 * 第一版只认「锭」（{@code IngotFirstOrdering}，已删除）。用户抽样测试后给了**两条不同的口径**：
 * <pre>
 *   研磨机（gtceu:macerator） ⇒ 输入含【锭】或【宝石】的排最前
 *   提取机（gtceu:extractor） ⇒ 输入含【锭】或【粉】的排最前
 * </pre>
 * 🔴 **两条口径不一样**，所以这里**不做一套通用规则**，而是做成一张
 * <b>「配方类型 → 档位表」</b>（{@link #TIER_TABLE}）：
 * * 表里没列的类型 ⇒ <b>完全不排序</b>（原样返回）；
 * * 表里列了 ⇒ 按表里的顺序分档，**档号小的在前**，同档内保持原顺序。
 * 以后再加类型（或给某个类型换口径）**只改这张表**，判据与排序都不用动。
 *
 * <h2>1. 🔴 判据为什么又是"并集"（id 形态 ＋ 标签）</h2>
 * 与第一版同源，三条**实测**依据（导出快照 {@code export\tags\} 与 {@code export\recipes\}）：
 * <pre>
 *   forge/ingots      里有 gtceu:iron_ingot 出现 **0** 次、minecraft:iron_ingot 1 次
 *   forge/gems/diamond.json   = ["minecraft:diamond","minecraft:diamond"]   ← 钻石的 id 里**没有** "gem"
 *   forge/gems/ruby.json      = ["gtceu:ruby_gem"]                          ← GT 自己的宝石才有 _gem
 *   forge/dusts/iron.json     = ["gtceu:iron_dust"]                         ← 粉是 "_dust"
 *   真实配方 macerate_diamond.json 的输入 = tag:forge:gems/diamond
 *   真实配方 macerate_emerald.json 的输入 = tag:forge:gems/emerald
 * </pre>
 * ⇒ **只认 id 会漏掉"钻石/绿宝石"这种原版物品；只认标签会漏掉"id 里有形态名但没打标签"的物品** ⇒
 * 判据 = {@link #classifyIdPath(String)} ∪ {@link #classifyTag(String, String)}。
 *
 * <h2>2. 稳定性（比"排序"本身更重要）</h2>
 * {@link #stableByRank} 是**自己写的分桶**（不依赖 {@code List.sort}/{@code Stream.sorted} 的稳定性承诺）：
 * 按原顺序扫一遍把元素放进各自档位的桶，再<b>按档号升序</b>把桶首尾相接。
 * ⇒ ①同档内 = 原顺序（定义上稳定）；②幂等；③<b>只有一档时恒等短路</b>（=什么都不做）。
 *
 * <h2>3. 纯函数边界</h2>
 * 本文件**只 import {@code java.*}** ⇒ 可以离线 {@code javac}+{@code java} 跑三段自证
 * （正常／预期失败／复原），跑的就是线上那份字节码。
 */
public final class RecipeIngredientOrdering {

    /** 输入的"材料形态"——只区分我们真正要用的三种，其余一律 null（= 不参与分档）。 */
    public enum InputClass {
        INGOT, GEM, DUST
    }

    /** 形态名（id 里的后缀 / 标签里的关键词）。 */
    private static final String KEY_INGOT = "ingot";
    private static final String KEY_GEM = "gem";
    private static final String KEY_DUST = "dust";

    /** 只认这两个命名空间的标签（Forge 的通用标签 + 跨模组通用的 c 标签）。 */
    private static final String NS_FORGE = "forge";
    private static final String NS_C = "c";

    /**
     * 🔴 <b>「配方类型 → 档位表」</b>：数组顺序 = 优先级（下标 0 最前）。
     * 表里没有的类型 = <b>这个分类不排序</b>。
     */
    private static final Map<String, InputClass[]> TIER_TABLE;

    static {
        Map<String, InputClass[]> table = new LinkedHashMap<>();
        // 研磨机：锭 优先，其次 宝石（用户口径）
        table.put("gtceu:macerator", new InputClass[]{InputClass.INGOT, InputClass.GEM});
        // 提取机：锭 优先，其次 粉（用户口径："锭和粉都要提前"）
        table.put("gtceu:extractor", new InputClass[]{InputClass.INGOT, InputClass.DUST});
        TIER_TABLE = Collections.unmodifiableMap(table);
    }

    private RecipeIngredientOrdering() {
    }

    /** 这个配方类型有没有档位表；没有 ⇒ 返回 {@code null}（= 不排序）。 */
    public static InputClass[] tiersFor(String recipeTypeUid) {
        if (recipeTypeUid == null) {
            return null;
        }
        return TIER_TABLE.get(recipeTypeUid);
    }

    /** 表里登记过的全部配方类型（给日志/自证用）。 */
    public static Set<String> targetRecipeTypes() {
        return TIER_TABLE.keySet();
    }

    /**
     * 给一条配方定档：返回命中档位里**最靠前**的那个下标；一个都没命中 ⇒ {@code tiers.length}（= "其它"档）。
     * 表里没有这个类型 ⇒ 返回 0（调用方本就不该走到这里）。
     */
    public static int rank(String recipeTypeUid, Set<InputClass> present) {
        InputClass[] tiers = tiersFor(recipeTypeUid);
        if (tiers == null || tiers.length == 0) {
            return 0;
        }
        for (int i = 0; i < tiers.length; i++) {
            if (present != null && present.contains(tiers[i])) {
                return i;
            }
        }
        return tiers.length;
    }

    /** 纯判据①：<b>物品 id 的 path</b> 属于哪一种形态（都不属于 ⇒ {@code null}）。 */
    public static InputClass classifyIdPath(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String p = path.toLowerCase(Locale.ROOT);
        if (p.equals(KEY_INGOT) || p.endsWith("_" + KEY_INGOT)) {
            return InputClass.INGOT;
        }
        if (p.equals(KEY_GEM) || p.endsWith("_" + KEY_GEM)) {
            return InputClass.GEM;
        }
        if (p.equals(KEY_DUST) || p.endsWith("_" + KEY_DUST)) {
            return InputClass.DUST;
        }
        return null;
    }

    /** 纯判据②：<b>标签（命名空间 + path）</b> 属于哪一种形态（都不属于 ⇒ {@code null}）。 */
    public static InputClass classifyTag(String namespace, String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String ns = (namespace == null || namespace.isEmpty()) ? "minecraft" : namespace;
        if (!NS_FORGE.equals(ns) && !NS_C.equals(ns)) {
            return null;
        }
        String p = path.toLowerCase(Locale.ROOT);
        if (p.contains(KEY_INGOT)) {
            return InputClass.INGOT;
        }
        if (p.contains(KEY_GEM)) {
            return InputClass.GEM;
        }
        if (p.contains(KEY_DUST)) {
            return InputClass.DUST;
        }
        return null;
    }

    /**
     * <b>稳定 N 档分区</b>：档号小的在前，<b>同档内保持原相对顺序</b>。
     *
     * <p>保证（离线自证逐条覆盖）：
     * <ol>
     *   <li>不改输入，返回新列表；</li>
     *   <li>是同一多重集（元素一个不多一个不少）；</li>
     *   <li>档号单调不减（第 i 个元素的档号 ≤ 第 i+1 个的）；</li>
     *   <li>同档内元素的原相对顺序严格保持；</li>
     *   <li>幂等（对输出再跑一次，逐元素相同）；</li>
     *   <li>只有一档时 <b>恒等短路</b>（输出 = 输入顺序）。</li>
     * </ol>
     */
    public static <T> List<T> stableByRank(List<T> input, ToIntFunction<T> rankOf) {
        if (input == null) {
            return Collections.emptyList();
        }
        if (input.isEmpty()) {
            return new ArrayList<>(0);
        }
        if (rankOf == null) {
            return new ArrayList<>(input);
        }
        int size = input.size();
        TreeMap<Integer, List<T>> buckets = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            T element = input.get(i);
            int rank = rankOf.applyAsInt(element);
            buckets.computeIfAbsent(rank, k -> new ArrayList<>()).add(element);
        }
        // 恒等短路：全在同一档 ⇒ 原样返回（绝不打乱）
        if (buckets.size() <= 1) {
            return new ArrayList<>(input);
        }
        List<T> out = new ArrayList<>(size);
        for (List<T> bucket : buckets.values()) {
            out.addAll(bucket);
        }
        return out;
    }

    /** 两档便捷写法：命中的整体前置（= {@link #stableByRank} 的 0/1 两档版），第一版的口径就是它。 */
    public static <T> List<T> stableFront(List<T> input, Predicate<T> isFront) {
        if (isFront == null) {
            return stableByRank(input, e -> 0);
        }
        return stableByRank(input, e -> isFront.test(e) ? 0 : 1);
    }
}
