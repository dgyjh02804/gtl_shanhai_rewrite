package com.shanhai.machine.wildcard;

import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.function.IntPredicate;

/**
 * 「实际配方数量」（上游 {@code cacheRecipeCount}）的<b>纯逻辑核</b>。
 *
 * <h2>一、它到底是个什么东西（上游语义，逐条有 javap 依据）</h2>
 * 上游 {@code MEPatternBufferPartMachine} 里这个数字是一个 <b>{@code byte[槽数]}</b>，
 * 含义是「<b>该槽的样板要攒够多少条不同配方，这一格才算「配方缓存就绪」</b>」。原始字节码
 * （{@code javap -p -c MEPatternBufferPartMachine$MEPatternTrait#setSlotCacheRecipe}）：
 * <pre>
 *   ObjectSet set = recipeMultipleCacheMap.computeIfAbsent(index, i -&gt; new ObjectArraySet());
 *   if (set.add(recipe)) {
 *       this$0.cacheRecipe[index] = set.size() &gt;= this$0.cacheRecipeCount[index];   // &lt;== 就是这一句
 *   }
 * </pre>
 * 而 {@code cacheRecipe[槽]} 是「就绪闸门」，读它的地方有两处（均为 {@code javap -c} 实证）：
 * <ul>
 *   <li>{@code MEPatternTrait#getCachedGTRecipe()}：只有
 *       {@code cacheRecipe[slot] && internalInventory[slot].isActive()} 的槽，其配方才进返回集
 *       ⇒ 经 {@code MEPatternRecipeHandlePart#getCachedGTRecipe()} 进
 *       {@code GTRecipeLookupMixin#find()} / {@code #getRecipeIterator()}
 *       ⇒ <b>就绪前这些配方根本不在机器的候选配方表里</b>；</li>
 *   <li>{@code MEPatternBufferPartMachineBase#getActiveAndUnCachedSlots()}
 *       （{@code filter(i -&gt; getInternalSlot(i).isActive() && !hasRecipeCacheInSlot(i))}）：
 *       就绪前该槽还在「待缓存」表里，会被反复拿物料去试配；就绪后才退出。</li>
 * </ul>
 * ⇒ 一句话：<b>它控制「这一格格子里攒到的配方，机器看不看得见」</b>，而「看得见」正是能不能被下单/执行的前提。
 *
 * <h2>二、作用域</h2>
 * <b>每块样板各一个</b>（上游是 {@code byte[maxPatternCount]}，下标 = 样板格号；
 * 中键面板也是按格弹出并只改当前那一格）。本机因此按<b>通配符样板槽</b>存一份，
 * 与「每格电路」{@link ShanhaiSlotCircuit} 是<b>两个互不相干的字段</b>，不共用任何存储。
 *
 * <h2>三、为什么单独拆一个类</h2>
 * 本类<b>不 import 任何 Minecraft / GTCEu / LDLib 类</b>（配方键是泛型 {@code T}，机器那边传
 * {@code GTRecipe}），所以它能被 javac 单独编译、在裸 JVM 上直接跑断言 ——
 * 这是本工程「先证明检查器自己是对的」那条规矩唯一能落地的形态（游戏不能启动，逻辑核必须能离线执行）。
 * 与 {@link ShanhaiSlotCircuit} 同款做法。
 *
 * <h2>四、⚠️ 与上游有意不同的两处（都写在这里，别当成照搬）</h2>
 * <ol>
 *   <li><b>阈值变化时本类立即重算</b>（{@link #resetOwner(int)} + 重新攒）。
 *       上游把「实际配方数量」的界面回调接到了 {@code onPatternChange(slot)}，
 *       而这个方法只在<b>样板本身换了</b>时才去清缓存
 *       ⇒ 上游「把数字改大」在样板没换的情况下<b>不会立刻生效</b>。
 *       本机改成「改了数字就把该样板名下的缓存与就绪状态全部作废、按新阈值重新攒」，
 *       否则用户改了数字看不到任何变化（这正是本轮要修的病）。</li>
 *   <li><b>阈值与「攒到的条数」的口径</b>：上游是 {@code 该槽自己的 set.size() >= cacheRecipeCount[槽]}。
 *       本机的阈值表按<b>通配符样板块</b>存（面板就是按块弹、玩家按块设），所以
 *       {@code cacheRecipeCount[槽]} 换成 {@code cacheRecipeCount[该展开样板所属的块]}，
 *       其余<b>逐字相同</b>（比的是<b>该展开样板自己</b>攒到的条数）。</li>
 * </ol>
 */
public final class ShanhaiPatternRecipeCache<T> {

    /**
     * 合法阈值下界。
     *
     * <p>取自上游 UI 的字节码常量：{@code MEPatternCatalystUIManager#createCacheCountInputWidget}
     * 里 {@code IntInputWidget.setMin(Integer.valueOf(1))}。
     */
    public static final int MIN_COUNT = 1;

    /**
     * 合法阈值上界。
     *
     * <p>同处字节码：{@code setMax(Integer.valueOf(127))} —— 127 正是 {@code byte} 的正半区上界，
     * 说明上游把这个数当 {@code byte} 用（字段类型也是 {@code byte[]}）。
     */
    public static final int MAX_COUNT = 127;

    /**
     * 出厂默认阈值。
     *
     * <p>上游构造器字节码：{@code newarray byte} → {@code Arrays.fill(cacheRecipeCount, (byte) 1)}。
     * ⇒ 默认 1 ⇒ 「攒到 1 条就算就绪」，也就是本机改动之前的行为。
     */
    public static final int DEFAULT_COUNT = 1;

    /** 全局展开样板索引 → 已缓存的不同配方集合（上游 {@code recipeMultipleCacheMap} 的同一形状）。 */
    private final Int2ReferenceMap<ObjectSet<T>> cached = new Int2ReferenceOpenHashMap<>();

    /** 全局展开样板索引 → 是否已就绪（下标与 {@link #ownerBySlot} 同长）。 */
    private boolean[] ready = new boolean[0];

    /**
     * 全局展开样板索引 → 它出自哪一块通配符样板。
     *
     * <p>⚠️ <b>本类是持有引用、不是拷贝</b>：机器那份表在样板展开时会重建，
     * 重建之后必须重新 {@link #bind(int[], byte[])}（见机器里的 {@code buildEffectivePatterns()}）。
     */
    private int[] ownerBySlot = new int[0];

    /**
     * 通配符样板槽 → 阈值（玩家在中键面板设的那个数）。
     *
     * <p>🔴 <b>这必须是机器上那个 {@code @Persisted @DescSynced byte[]} 本身</b>，
     * 不能是拷贝：界面控件（{@code MEPatternCatalystUIManager}）是<b>直接往这个数组里写</b>的
     * （{@code lambda$show$1} 里 {@code cacheRecipeCount[idx] = v.byteValue()}），
     * 中间再隔一层拷贝就会出现「界面改了、逻辑读不到」。
     */
    private byte[] countByOwner = new byte[0];

    /** 绑定下标表与阈值数组（可反复调用；每次都会按新长度重建就绪位图并清空已缓存集合）。 */
    public void bind(int[] ownerBySlot, byte[] countByOwner) {
        this.ownerBySlot = ownerBySlot != null ? ownerBySlot : new int[0];
        this.countByOwner = countByOwner != null ? countByOwner : new byte[0];
        this.ready = new boolean[this.ownerBySlot.length];
        this.cached.clear();
    }

    /** 全部作废（样板表被重建时调用）。 */
    public void clear() {
        for (int i = 0; i < this.ready.length; i++) {
            this.ready[i] = false;
        }
        this.cached.clear();
    }

    /** 把界面/存档里的原始值夹到合法区间（{@link #MIN_COUNT}–{@link #MAX_COUNT}）。 */
    public static int normalizeCount(int raw) {
        if (raw < MIN_COUNT) {
            return MIN_COUNT;
        }
        return Math.min(raw, MAX_COUNT);
    }

    /** 该通配符样板当前设的「实际配方数量」（读的是机器那个同步数组，不是拷贝）。 */
    public int countOf(int owner) {
        if (owner < 0 || owner >= this.countByOwner.length) {
            return DEFAULT_COUNT;
        }
        return this.countByOwner[owner];
    }

    /** 该通配符样板名下的全部展开样板索引（由 {@link #ownerBySlot} 反查）。 */
    private int[] slotsOfOwner(int owner) {
        int n = 0;
        for (int i = 0; i < this.ownerBySlot.length; i++) {
            if (this.ownerBySlot[i] == owner) {
                n++;
            }
        }
        final int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < this.ownerBySlot.length; i++) {
            if (this.ownerBySlot[i] == owner) {
                out[k++] = i;
            }
        }
        return out;
    }

    /** 该通配符样板名下累计缓存到的不同配方条数（跨它展开出来的全部样板求和）。 */
    public int cachedCountOfOwner(int owner) {
        int total = 0;
        for (int slot : this.slotsOfOwner(owner)) {
            final ObjectSet<T> set = this.cached.get(slot);
            if (set != null) {
                total += set.size();
            }
        }
        return total;
    }

    /**
     * 记一条「这条配方在该槽上被缓存住了」。
     *
     * <p>与上游 {@code setSlotCacheRecipe} 同构：<b>只有真的新加进去一条</b>（{@code add} 返回 true）
     * 才重算就绪位；重复添加不改变任何东西。
     *
     * @return 该槽的就绪状态是否发生变化
     */
    public boolean add(int slotIndex, T recipe) {
        if (recipe == null || slotIndex < 0 || slotIndex >= this.ownerBySlot.length) {
            return false;
        }
        final ObjectSet<T> set = this.cached.computeIfAbsent(slotIndex, k -> new ObjectOpenHashSet<>());
        if (!set.add(recipe)) {
            return false;
        }
        final boolean wasReady = this.ready[slotIndex];
        final int owner = this.ownerBySlot[slotIndex];
        // 🔴 上游原式（javap -c MEPatternBufferPartMachine$MEPatternTrait#setSlotCacheRecipe）：
        //        cacheRecipe[index] = set.size() >= cacheRecipeCount[index];
        //    本机唯一的差别是阈值表按「通配符样板槽」存（因为面板就是按块弹的、玩家按块设），
        //    所以这里把 cacheRecipeCount[index] 换成 cacheRecipeCount[该展开样板所属的块]。
        //    ⚠️ 比的是**这一槽自己**攒到的条数，不是该块名下所有槽的合计 —— 合计口径会让
        //       「同块里第一条展开样板一就绪，其余的就从『待缓存』表里掉出去、再也不会去攒配方」，
        //       那是个静默的功能倒退。这条是 2026-10-01 离线三段自证当场抓出来的。
        final boolean nowReady = owner >= 0 && owner < this.countByOwner.length
                && set.size() >= this.countOf(owner);
        this.ready[slotIndex] = nowReady;
        return wasReady != nowReady;
    }

    /** 该展开样板是否已就绪（= 它的配方可以被机器看见）。 */
    public boolean isReady(int slotIndex) {
        return slotIndex >= 0 && slotIndex < this.ready.length && this.ready[slotIndex];
    }

    /**
     * 该通配符样板名下<b>有没有任何一条</b>展开样板已就绪。
     *
     * <p>给界面那句「已缓存配方」提示用的「块」级结论（机器把它导到
     * {@code @DescSynced boolean[] cacheRecipeReady} 上，因为提示是在客户端渲染的）。
     */
    public boolean isOwnerReady(int owner) {
        for (int slot : this.slotsOfOwner(owner)) {
            if (this.isReady(slot)) {
                return true;
            }
        }
        return false;
    }

    /** 该槽已缓存的不同配方条数。 */
    public int cachedCount(int slotIndex) {
        final ObjectSet<T> set = this.cached.get(slotIndex);
        return set != null ? set.size() : 0;
    }

    /**
     * 该槽已缓存的配方（只读视图；返回的是活集合，调用方不得改）。
     *
     * <p>⚠️ <b>不按就绪过滤</b> —— 这是「记账」用的。要拿「机器看得见的」请用
     * {@link #visibleRecipes(IntPredicate)}。
     */
    public ObjectSet<T> cachedRecipes(int slotIndex) {
        final ObjectSet<T> set = this.cached.get(slotIndex);
        return set != null ? set : new ObjectOpenHashSet<>();
    }

    /** 已占用的全局展开样板下标（按升序；用于遍历「谁有缓存」）。 */
    public int[] cachedSlots() {
        final int[] keys = this.cached.keySet().toIntArray();
        java.util.Arrays.sort(keys);
        return keys;
    }

    /**
     * 「机器看得见的配方」= 已就绪 且 该槽 active 的槽里的全部配方。
     *
     * <p>🔴 这就是上游 {@code getCachedGTRecipe()} 那一段的逐句对应：
     * <pre>
     *   for (entry : recipeMultipleCacheMap) {
     *       if (set.isEmpty()) { it.remove(); }
     *       else if (cacheRecipe[slot] &amp;&amp; internalInventory[slot].isActive()) { recipes.addAll(set); }
     *   }
     * </pre>
     * ⇒ <b>就绪前，这些配方一条都进不来</b>，机器自然也就下不了这个单。
     */
    public ObjectSet<T> visibleRecipes(IntPredicate slotActive) {
        final ObjectSet<T> out = new ObjectOpenHashSet<>();
        for (int slot : cachedSlots()) {
            final ObjectSet<T> set = this.cached.get(slot);
            if (set == null || set.isEmpty()) {
                this.cached.remove(slot);
            } else if (this.isReady(slot) && slotActive.test(slot)) {
                out.addAll(set);
            }
        }
        return out;
    }

    /**
     * 作废某一块通配符样板名下的全部配方缓存与就绪状态。
     *
     * <p>对应上游 {@code removeSlotFromGTRecipeCache(slot)} 的
     * 「{@code cacheRecipe[slot] = false;} {@code recipeMultipleCacheMap.remove(slot);}」两句；
     * 上游第三句 {@code removeSlotFromMap.accept(slot)}（把「配方→槽」的映射从
     * {@code MEPatternRecipeHandlePart.recipes2SlotsMap} 里摘掉）由机器那边补，
     * 本类不持有那份映射。
     */
    public void resetOwner(int owner) {
        for (int slot : this.slotsOfOwner(owner)) {
            this.cached.remove(slot);
            if (slot >= 0 && slot < this.ready.length) {
                this.ready[slot] = false;
            }
        }
    }

    /** 阈值变了之后要作废的展开样板下标（供机器把它们从 {@code recipes2SlotsMap} 里摘掉）。 */
    public int[] slotsOf(int owner) {
        return this.slotsOfOwner(owner);
    }
}
