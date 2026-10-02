package com.shanhai.common.jei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * 山海重构 · <b>JEI「分类内排序」的【纯逻辑核】（第三版：档位支持「输入 ∧ 输出」）</b>。
 *
 * <h2>0. 这一版为什么又变了</h2>
 * <pre>
 *   第一版  只认「锭」，也只认"输入"                （IngotFirstOrdering，已删）
 *   第二版  「配方类型 → 输入形态档位表」           （研磨机＝锭＋宝石 ／ 提取机＝锭＋粉）
 *   第三版  ← 本版：档位从"只看输入"扩成"**输入 ∧ 输出**"，
 *          因为新需求是「**粉 → 锭**」「**原矿 → 锭**」—— 光有输入侧判不出"→ 锭"。
 * </pre>
 *
 * <h2>1. 现在的档位模型（{@link Tier}）</h2>
 * 一个档位 = 「输入必须含某几种形态之一」 ∧ 「输出必须是某种形态（可选）」：
 * <pre>
 *   gtceu:macerator   档0 = 输入含【锭】           档1 = 输入含【宝石】
 *   gtceu:extractor   档0 = 输入含【锭】           档1 = 输入含【粉】
 *   minecraft:furnace 档0 = 输入含【单质粉】且输出是【锭】   档1 = 输入含【原矿】且输出是【锭】
 * </pre>
 * 🔴 前两条是第二版的原样搬过来（{@code output == null} ⇒ <b>完全不看输出</b>）⇒
 * **研磨机／提取机的行为逐条不变**（离线自证里对这条有断言）。
 * 表里没列的类型 ⇒ <b>完全不排序</b>（原样返回）。
 *
 * <h2>2. 🔴 「粉」为什么必须分「粉」和「单质粉」两档</h2>
 * 用户口径（原话）：<i>「粉是指单质粉，例如铁粉是 {@code gtceu:iron_dust}
 * 而不是 {@code gtceu:pure_iron_dust}」</i>。
 * <p>导出快照的 <b>tags 全量扫描（9114 个标签文件）</b>实测，游戏里「粉」有 <b>五族</b>：
 * <pre>
 *   forge:dusts/*          810 个  → gtceu:iron_dust          ← 只要这一族
 *   forge:small_dusts/*    807 个  → gtceu:small_iron_dust    ← 小撮粉，不算
 *   forge:tiny_dusts/*     807 个  → gtceu:tiny_iron_dust     ← 小堆粉，不算
 *   forge:pure_dusts/*     138 个  → gtceu:pure_iron_dust     ← 用户点名【不算】
 *   forge:impure_dusts/*   138 个  → gtceu:impure_iron_dust   ← 不算
 * </pre>
 * 而第二版既有的 {@link InputClass#DUST} 判据是「<b>标签 path 里含 "dust"</b>」⇒
 * 它**会把上面五族全算成粉**。若直接拿它去判「粉 → 锭」，
 * 会多前移 <b>74 条</b> {@code pure/impure} 配方（实测数）⇒ 与用户口径不符。
 * ⇒ <b>不能改既有 {@code DUST}（那会动到提取机）</b>，而是<b>新增</b>
 * {@link InputClass#SIMPLE_DUST}，判据收紧到「**标签第一段正好是 {@code dusts}**」
 * ／「**id 去掉 {@code _dust} 后缀后不以四种修饰词开头**」。
 *
 * <h2>3. 🔴 「原矿」的判据（按"词"判，不按任意子串）</h2>
 * 用户口径（原话）：<i>「原矿指的是带 raw 的，例如 {@code minecraft:raw_iron}」</i>。
 * <p>落地成「{@code raw} 必须是<b>独立的下划线片段</b>」（{@code raw} / {@code raw_*} /
 * {@code *_raw} / {@code *_raw_*}）而不是 {@code p.contains("raw")}：<br>
 * 后者会把 {@code straw}／{@code drawstring} 这类<b>含 "raw" 三个字母但不是词</b>的 id 误判成原矿。
 * 实测这一条在真实数据上两端口径都成立：
 * <pre>
 *   标签  forge:raw_materials/iron   → 第一段 raw_materials 以 "raw_" 开头 ⇒ 原矿 ✓
 *   物品  minecraft:raw_iron / raw_gold / raw_copper ⇒ 以 "raw_" 开头 ⇒ 原矿 ✓
 *   物品  ad_astra:raw_calorite / raw_desh / raw_ostrum ⇒ 同上 ✓
 * </pre>
 * ⚠️ <b>已知的宽判据边界（如实标出）</b>：{@code laserio:logic_chip_raw} 按本判据<b>也算原矿</b>
 * （它以 {@code _raw} 结尾）。它没有出现在真实数据里被误伤 ——
 * 因为「粉/原矿 → 锭」还要求<b>输出是锭</b>，而它那条配方输出不是锭（见交付单）。
 *
 * <h2>4. 判据为什么一律是"并集"（id 形态 ∪ 标签）</h2>
 * 与第一版同源，两条实测依据：
 * <pre>
 *   forge/gems/diamond.json = ["minecraft:diamond"]   ← 钻石 id 里**没有** "gem"，只认 id 必漏
 *   forge/dusts/iron.json   = ["gtceu:iron_dust"]     ← 真实配方输入写的是 tag，只认 tag 会漏掉"没打标签但 id 里有形态名"的
 * </pre>
 * ⇒ 每个口径各自往下标 {@code out} 里加，<b>两个口径取并集</b>。
 *
 * <h2>5. 稳定性（比"排序"本身更重要）</h2>
 * {@link #stableByRank} 是**自己写的分桶**（不依赖 {@code List.sort}/{@code Stream.sorted} 的稳定性承诺）：
 * 按原顺序扫一遍把元素放进各自档位的桶，再<b>按档号升序</b>把桶首尾相接。
 * ⇒ ①同档内 = 原顺序（定义上稳定）；②幂等；③<b>只有一档时恒等短路</b>（=什么都不做）。
 *
 * <h2>6. 纯函数边界</h2>
 * 本文件**只 import {@code java.*}** ⇒ 可以离线 {@code javac}+{@code java} 跑自证
 * （正常／预期失败／复原／真实配方集），跑的就是线上那份字节码。
 */
public final class RecipeIngredientOrdering {

    /**
     * 材料形态。
     *
     * <p>⚠️ 名字沿用第二版（{@code InputClass}），但<b>现在输入侧和输出侧共用它</b>
     * （{@link Tier} 的 {@code output} 也是这个类型）。
     * <b>故意不改名</b>：改名要动 3 个文件的十几个调用点，而本轮不允许构建 ⇒ 无法编译期兜底，
     * 万一漏一处就是"交给下一环一个编译不过的树"。宁可名字略欠贴切。
     *
     * <p><b>枚举常量的声明顺序有意义</b>：{@link #primary(Set)} 取"声明顺序里最靠前的那个"，
     * 而单值 API（{@link #classifyIdPath}/{@link #classifyTag}）走的就是 {@code primary} ⇒
     * 新增的 {@code SIMPLE_DUST}/{@code RAW_ORE} <b>排在既有三个之后</b>，
     * 保证「id 里含 dust」这类既有调用点拿到的仍然是 {@link #DUST}，<b>第二版行为不被改动</b>。
     */
    public enum InputClass {
        INGOT("锭"),
        GEM("宝石"),
        DUST("粉"),
        /** <b>单质粉</b>：{@code gtceu:iron_dust}（{@code gtceu:pure_iron_dust} <b>不算</b>）。 */
        SIMPLE_DUST("单质粉"),
        /** <b>原矿</b>：带 {@code raw} 的，例如 {@code minecraft:raw_iron}。 */
        RAW_ORE("原矿");

        private final String label;

        InputClass(String label) {
            this.label = label;
        }

        /** 日志里用的中文名（与 {@link #tierLabel} 共用，保证日志和档位表不会各说各话）。 */
        public String label() {
            return label;
        }
    }

    /** 形态名（id 里的后缀 / 标签里的关键词）。 */
    private static final String KEY_INGOT = "ingot";
    private static final String KEY_GEM = "gem";
    private static final String KEY_DUST = "dust";

    /** 「原矿」的关键词（按**词**匹配，见类注释 §3）。 */
    private static final String KEY_RAW = "raw";

    /**
     * 粉的四种"非单质"修饰词 —— 实测全部形态（tags 全量扫描）：
     * {@code small_dusts} / {@code tiny_dusts} / {@code pure_dusts} / {@code impure_dusts}。
     * 带这四个之一的粉<b>不算单质粉</b>。
     */
    private static final String[] DUST_MODIFIERS = {"small_", "tiny_", "pure_", "impure_"};

    /** 只认这两个命名空间的标签（Forge 的通用标签 + 跨模组通用的 c 标签）。 */
    private static final String NS_FORGE = "forge";
    private static final String NS_C = "c";

    /** 档位：输入必须含 {@code inputs} 里的一种，且（若 {@code output != null}）输出必须是 {@code output}。 */
    public static final class Tier {

        private final InputClass[] inputs;
        private final InputClass output;

        private Tier(InputClass[] inputs, InputClass output) {
            this.inputs = inputs;
            this.output = output;
        }

        /** 只看输入（= 第二版的行为，{@code output} 为 {@code null}）。 */
        public static Tier inputsAny(InputClass... inputs) {
            return new Tier(inputs, null);
        }

        /** 输入含 {@code inputs} 之一 <b>且</b> 输出是 {@code requiredOutput}。 */
        public static Tier inputsAnyTo(InputClass requiredOutput, InputClass... inputs) {
            return new Tier(inputs, requiredOutput);
        }

        public InputClass[] inputs() {
            return inputs;
        }

        /** {@code null} = 这一档不判输出（第二版的两条口径就是它）。 */
        public InputClass output() {
            return output;
        }

        /** 这一档要不要看输出（{@code false} ⇒ 调用方可以**完全不算**输出，省一遍标签展开）。 */
        public boolean needsOutput() {
            return output != null;
        }

        /** 一条配方（已问出输入形态集 ∧ 输出形态集）命中本档吗。 */
        public boolean matches(Set<InputClass> presentInputs, Set<InputClass> presentOutputs) {
            if (presentInputs == null || inputs == null) {
                return false;
            }
            boolean inputHit = false;
            for (int i = 0; i < inputs.length; i++) {
                if (presentInputs.contains(inputs[i])) {
                    inputHit = true;
                    break;
                }
            }
            if (!inputHit) {
                return false;
            }
            if (output == null) {
                return true;
            }
            return presentOutputs != null && presentOutputs.contains(output);
        }

        /** 日志用的档位名：{@code 锭} ／ {@code 单质粉→锭}。 */
        public String label() {
            if (inputs == null || inputs.length == 0) {
                return "?";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < inputs.length; i++) {
                if (i > 0) {
                    sb.append('/');
                }
                sb.append(inputs[i].label());
            }
            if (output != null) {
                sb.append('→').append(output.label());
            }
            return sb.toString();
        }
    }

    /**
     * 🔴 <b>「配方类型 → 档位表」</b>：数组顺序 = 优先级（下标 0 最前）。
     * 表里没有的类型 = <b>这个分类不排序</b>。
     */
    private static final Map<String, Tier[]> TIER_TABLE;

    static {
        Map<String, Tier[]> table = new LinkedHashMap<>();
        // 研磨机：锭 优先，其次 宝石（用户口径；第二版原样）
        table.put("gtceu:macerator", new Tier[]{Tier.inputsAny(InputClass.INGOT), Tier.inputsAny(InputClass.GEM)});
        // 提取机：锭 优先，其次 粉（用户口径："锭和粉都要提前"；第二版原样）
        table.put("gtceu:extractor", new Tier[]{Tier.inputsAny(InputClass.INGOT), Tier.inputsAny(InputClass.DUST)});
        // 🔴 原版熔炉「烧炼」：粉 → 锭 排最前，原矿 → 锭 紧跟其后（本轮新增的用户需求）
        table.put("minecraft:furnace", new Tier[]{
                Tier.inputsAnyTo(InputClass.INGOT, InputClass.SIMPLE_DUST),
                Tier.inputsAnyTo(InputClass.INGOT, InputClass.RAW_ORE)
        });
        TIER_TABLE = Collections.unmodifiableMap(table);
    }

    private RecipeIngredientOrdering() {
    }

    /** 这个配方类型有没有档位表；没有 ⇒ 返回 {@code null}（= 不排序）。 */
    public static Tier[] tiersFor(String recipeTypeUid) {
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
     * 这个分类的档位表里<b>有没有任何一档要看输出</b>。
     * 调用方据此决定要不要去算输出形态（第二版的两个分类 = {@code false} ⇒ 一个字节的额外开销都没有）。
     */
    public static boolean needsOutput(String recipeTypeUid) {
        Tier[] tiers = tiersFor(recipeTypeUid);
        if (tiers == null) {
            return false;
        }
        for (int i = 0; i < tiers.length; i++) {
            if (tiers[i].needsOutput()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 给一条配方定档：返回<b>第一个</b>命中的档位下标；一个都没命中 ⇒ {@code tiers.length}（= "其它"档）。
     * 表里没有这个类型 ⇒ 返回 0（调用方本就不该走到这里）。
     *
     * @param presentOutputs 只有 {@link #needsOutput} 为 {@code true} 时必须给；其它情况允许 {@code null}
     */
    public static int rank(String recipeTypeUid, Set<InputClass> presentInputs, Set<InputClass> presentOutputs) {
        Tier[] tiers = tiersFor(recipeTypeUid);
        if (tiers == null || tiers.length == 0) {
            return 0;
        }
        for (int i = 0; i < tiers.length; i++) {
            if (tiers[i].matches(presentInputs, presentOutputs)) {
                return i;
            }
        }
        return tiers.length;
    }

    /** 纯判据①：<b>物品 id 的 path</b> 属于哪一种形态（都不属于 ⇒ {@code null}）。 */
    public static InputClass classifyIdPath(String path) {
        Set<InputClass> out = classifyIdPathInto(path, null);
        return primary(out);
    }

    /**
     * 纯判据①（多值版）：把 id path 命中的<b>所有</b>形态加进 {@code out}。
     *
     * <p>{@code iron_dust} 会同时得到 {@link InputClass#DUST} 与 {@link InputClass#SIMPLE_DUST}
     * —— 这是刻意的：既有口径（提取机）要 DUST，新口径（烧炼）要 SIMPLE_DUST，
     * <b>两个都在集合里，各自都能命中</b>，不靠"谁压谁"。
     *
     * @return 传入的那个 {@code out}（方便串写）；{@code out == null} 时返回新建的集合
     */
    public static Set<InputClass> classifyIdPathInto(String path, Set<InputClass> out) {
        Set<InputClass> acc = (out == null) ? EnumSet.noneOf(InputClass.class) : out;
        if (path == null || path.isEmpty()) {
            return acc;
        }
        String p = path.toLowerCase(Locale.ROOT);
        if (p.equals(KEY_INGOT) || p.endsWith("_" + KEY_INGOT)) {
            acc.add(InputClass.INGOT);
        }
        if (p.equals(KEY_GEM) || p.endsWith("_" + KEY_GEM)) {
            acc.add(InputClass.GEM);
        }
        if (p.equals(KEY_DUST) || p.endsWith("_" + KEY_DUST)) {
            // 既有口径：名字里带 dust 就算"粉"（提取机用它）—— 保持第二版原样
            acc.add(InputClass.DUST);
            // 新增口径：只有"没有修饰词"的才是【单质粉】
            if (!hasDustModifier(p)) {
                acc.add(InputClass.SIMPLE_DUST);
            }
        }
        if (hasRawToken(p)) {
            acc.add(InputClass.RAW_ORE);
        }
        return acc;
    }

    /** 纯判据②：<b>标签（命名空间 + path）</b> 属于哪一种形态（都不属于 ⇒ {@code null}）。 */
    public static InputClass classifyTag(String namespace, String path) {
        Set<InputClass> out = classifyTagInto(namespace, path, null);
        return primary(out);
    }

    /** 纯判据②（多值版）：把标签命中的<b>所有</b>形态加进 {@code out}。语义同 {@link #classifyIdPathInto}。 */
    public static Set<InputClass> classifyTagInto(String namespace, String path, Set<InputClass> out) {
        Set<InputClass> acc = (out == null) ? EnumSet.noneOf(InputClass.class) : out;
        if (path == null || path.isEmpty()) {
            return acc;
        }
        String ns = (namespace == null || namespace.isEmpty()) ? "minecraft" : namespace;
        if (!NS_FORGE.equals(ns) && !NS_C.equals(ns)) {
            return acc;
        }
        String p = path.toLowerCase(Locale.ROOT);
        if (p.contains(KEY_INGOT)) {
            acc.add(InputClass.INGOT);
        }
        if (p.contains(KEY_GEM)) {
            acc.add(InputClass.GEM);
        }
        if (p.contains(KEY_DUST)) {
            // 既有口径：标签 path 里含 "dust" 就算粉（提取机用它）—— 保持第二版原样
            acc.add(InputClass.DUST);
            // 新增口径：只有"第一段正好是 dusts"的才是【单质粉】纯标签
            if (isSimpleDustTagPath(p)) {
                acc.add(InputClass.SIMPLE_DUST);
            }
        }
        if (hasRawToken(p)) {
            acc.add(InputClass.RAW_ORE);
        }
        return acc;
    }

    /**
     * 🔴 <b>「原矿」的核心判据</b>：{@code raw} 必须是<b>独立的下划线片段</b>。
     *
     * <p>为什么不直接 {@code p.contains("raw")}：那会把 {@code straw}／{@code drawstring}
     * 这种"含 raw 三个字母但不是一个词"的 id 也判成原矿。
     * 用户口径是「带 raw 的」，落地到 id/tag 上最贴切且不误伤的就是"词"这一级。
     */
    public static boolean hasRawToken(String lowerPath) {
        if (lowerPath == null || lowerPath.isEmpty()) {
            return false;
        }
        return lowerPath.equals(KEY_RAW)
                || lowerPath.startsWith(KEY_RAW + "_")
                || lowerPath.endsWith("_" + KEY_RAW)
                || lowerPath.contains("_" + KEY_RAW + "_");
    }

    /**
     * 🔴 <b>「单质粉」的 id 判据</b>：id 以 {@code _dust} 结尾，<b>且</b>去掉后缀后不以四种修饰词开头。
     * <pre>
     *   iron_dust        → true   （单质粉 ✓）
     *   pure_iron_dust   → false  （用户点名不算）
     *   impure_iron_dust → false
     *   small_iron_dust  → false
     *   tiny_iron_dust   → false
     * </pre>
     */
    public static boolean isSimpleDustIdPath(String lowerPath) {
        if (lowerPath == null || !lowerPath.endsWith("_" + KEY_DUST)) {
            return false;
        }
        return !hasDustModifier(lowerPath);
    }

    /**
     * 🔴 <b>「单质粉」的标签判据</b>：标签 path 的<b>第一段正好是 {@code dusts}</b>。
     * <pre>
     *   dusts/iron        → true   （单质粉 ✓）
     *   dusts             → true   （聚合标签 #forge:dusts，本身就是"所有单质粉"）
     *   pure_dusts/iron   → false  （用户点名不算）
     *   impure_dusts/iron → false
     *   small_dusts/iron  → false
     *   tiny_dusts/iron   → false
     * </pre>
     * 依据：导出快照实测这五族是<b>平级</b>的标签族（{@code dusts} / {@code small_dusts} /
     * {@code tiny_dusts} / {@code pure_dusts} / {@code impure_dusts}），
     * 所以"第一段是不是正好 {@code dusts}"就能把它们干净地分开。
     */
    public static boolean isSimpleDustTagPath(String lowerPath) {
        if (lowerPath == null || lowerPath.isEmpty()) {
            return false;
        }
        int slash = lowerPath.indexOf('/');
        String head = (slash < 0) ? lowerPath : lowerPath.substring(0, slash);
        return head.equals("dusts");
    }

    /** 去掉 {@code _dust} 后是否以四种修饰词开头（{@code small_/tiny_/pure_/impure_}）。 */
    private static boolean hasDustModifier(String lowerPath) {
        for (int i = 0; i < DUST_MODIFIERS.length; i++) {
            if (lowerPath.startsWith(DUST_MODIFIERS[i])) {
                return true;
            }
        }
        return false;
    }

    /** 单值 API 的"主形态" = <b>枚举声明顺序</b>里最靠前的那个（都不在 ⇒ {@code null}）。 */
    private static InputClass primary(Set<InputClass> classes) {
        if (classes == null || classes.isEmpty()) {
            return null;
        }
        InputClass[] order = InputClass.values();
        for (int i = 0; i < order.length; i++) {
            if (classes.contains(order[i])) {
                return order[i];
            }
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
