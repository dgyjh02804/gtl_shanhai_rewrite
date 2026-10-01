package com.shanhai.common.heat;

import java.util.Set;

/**
 * 山海重构 · <b>恒星热力槽</b>的判定核 —— <b>只 import {@code java.*}</b>，可以离线 {@code javac} 驱动。
 *
 * <h2>1. 它判定什么（用户原话逐字）</h2>
 * <blockquote>
 * 「给原初太虚宇宙锻炉，原初永恒熔炼炉，原初分子裂隙核心，它们放置世线残片的那个格子下面再加一个格子，
 *  用来放置线圈/恒星热力容器，分别给配方：合金冶炼炉，电力高炉，超维度熔炼，混沌炼金，星焰跃迁，
 *  恒星热能熔炼，深度扭曲化学仪提供温度/恒星热力容器等级，<b>都需要放满64个才能生效</b>，
 *  <b>若选择其他配方则无视这个格子</b>，并在 jade 显示（配方未执行成功原因）」
 * </blockquote>
 * 用户随后的澄清（逐字）：「<b>这些是原版配方的额外条件，类似我们的物质模块等级</b>」。
 *
 * <h2>2. 🔴 「原版配方的额外条件」在本工程里的确切形状（已实证，不是推断）</h2>
 * 那 7 个类型是 gtceu / gtlcore / gtladditions 注册的**原版类型**。它们的配方自带两个
 * <b>{@code data} NBT 整数字段</b>（从当前实例的配方导出逐条现读，见交付报告 §4 的计数表）：
 * <pre>
 *   {@code ebf_temp}  —— 线圈炉温门槛，单位 K。GTCEu 自己的 {@code GTRecipeBuilder#blastFurnaceTemp(int)}
 *                        字节码就是 {@code ldc "ebf_temp" → addData(String,int)}（javap 原文）。
 *   {@code SCTier}    —— 恒星热力容器等级门槛，取值 1/2/3。gtlcore 的
 *                        {@code GTLRecipeTypes#getSCTier(int)} 把它渲成
 *                        {@code gtceu.tier.base / .advanced / .ultimate}。
 * </pre>
 * ⇒ 本类**不发明**任何一个数值：门槛（{@code needTemp} / {@code needTier}）由调用方从
 * {@code recipe.data} 原样读出后传进来。
 *
 * <h2>3. 生效判据 = 该格 {@code count >= 64}（选 {@code >=} 而不是 {@code ==} 的理由）</h2>
 * 用户原话是「放满 64 个」。方块类物品的原版堆叠上限就是 64 ⇒ 在<b>今天的游戏里
 * {@code >=64} 与 {@code ==64} 逐值等价</b>（一格放不下第 65 个）。选 {@code >=} 的唯一理由是
 * <b>将来若有别的 mod 抬高堆叠上限</b>：那时 {@code ==64} 会变成"放满 65 个反而不生效"这种
 * 静默失效，而 {@code >=} 仍是玩家说的"放满"。
 * ⚠️ 若某一格堆叠上限被**调低**到 64 以下，{@code >=64} 就变成永远达不到 ——
 * 那时这条判据要改成按 {@code getMaxStackSize()} 现读。
 *
 * <h2>4. 「若选择其他配方则无视这个格子」怎么实现的</h2>
 * 两条独立的分流，都不需要特判"别的配方"：
 * <ol>
 *   <li>{@link #isGated(String)} —— 当前配方类型不在 {@link #GATED_TYPE_IDS} 里 ⇒ 连槽都不读；</li>
 *   <li>{@code needTemp <= 0 && needTier <= 0} —— 即便类型对，那条配方**自己没写**这两个键
 *       （实测：{@code distort} 有 1 条没有 {@code ebf_temp}；{@code stellar_forge} 只有
 *       {@code SCTier} 没有 {@code ebf_temp}）⇒ 同样放行。</li>
 * </ol>
 */
public final class ShanhaiHeatGate {

    private ShanhaiHeatGate() {}

    /** 生效所需数量。见类注释 §3 对 {@code >=} 与 {@code ==} 的取舍说明。 */
    public static final int REQUIRED_COUNT = 64;

    /** 配方 {@code data} 里的线圈炉温门槛键（GTCEu 原文拼写）。 */
    public static final String KEY_EBF_TEMP = "ebf_temp";

    /** 配方 {@code data} 里的恒星热力容器等级门槛键（gtlcore 原文拼写）。 */
    public static final String KEY_SC_TIER = "SCTier";

    /**
     * 受本槽管控的 7 个配方类型 id —— <b>用户原话点名的 7 个</b>，逐个从<b>语言文件</b>反查得来
     * （不是猜的；反查表与原始输出见交付报告 §3）：
     * <pre>
     *   合金冶炼炉      → gtceu:alloy_blast_smelter                            （原初太虚宇宙锻炉）
     *   电力高炉        → gtceu:electric_blast_furnace                         （原初太虚宇宙锻炉）
     *   超维度熔炼      → gtceu:dimensionally_transcendent_plasma_forge       （原初永恒熔炼炉）
     *   混沌炼金        → gtceu:chaotic_alchemy                                （原初永恒熔炼炉）
     *   星焰跃迁        → gtceu:stellar_lgnition                              （原初永恒熔炼炉）
     *   恒星热能熔炼    → gtceu:stellar_forge                                 （原初永恒熔炼炉）
     *   深度化学扭曲仪  → gtceu:distort                                       （原初分子裂隙核心）
     * </pre>
     * 🔴 <b>用户把这 7 个里的最后一个写成了「深度（扭曲化学）仪」，语言文件里的真名是
     * 「深度（化学扭曲）仪」</b>（中间四个字对调）。已按真名实现，并已在交付报告里点名。
     */
    public static final Set<String> GATED_TYPE_IDS = Set.of(
            "gtceu:alloy_blast_smelter",
            "gtceu:electric_blast_furnace",
            "gtceu:dimensionally_transcendent_plasma_forge",
            "gtceu:chaotic_alchemy",
            "gtceu:stellar_lgnition",
            "gtceu:stellar_forge",
            "gtceu:distort");

    /** 这个配方类型受不受本槽管控。 */
    public static boolean isGated(String typeId) {
        return typeId != null && GATED_TYPE_IDS.contains(typeId);
    }

    /**
     * 哪几台机器**看得见**恒星热力槽 —— <b>就是用户原话点名的那三台</b>。
     *
     * <p>用户 2026-09-30 的选择题答案（逐字）：**「B. 只留那三台」**。
     *
     * <pre>
     *   shanhai:taixu_smelting_furnace                原初太虚宇宙锻炉（🔴 唯一没有 primordial_ 前缀的）
     *   shanhai:primordial_eternal_smelting_furnace   原初永恒熔炼炉
     *   shanhai:primordial_molecular_rift_core        原初分子裂隙核心
     * </pre>
     *
     * <h2>🔴 为什么必须配 {@link #verifyMachineIds(java.util.List)}（不是可选装饰）</h2>
     * 「按 id 白名单决定哪台机器有这一格」本身就是一条**会静默失效**的写法：
     * 只要有一台被改名 / 被删，结果是玩家**看不到那一格**，而日志里一个字都没有
     * —— 这正是本工程反复踩过的形态（"少一格"与"没少"在日志上长得一模一样）。
     * ⇒ 所以白名单一旦存在，就必须配一条**注册期的硬自检**：三个 id 必须逐个能在
     * 已注册的模块里找到，缺任何一个就**抛异常**。这样"漏了"是**报错**，不是**默默少一格**。
     * <p>它是纯函数（只吃一个 {@code List<String>}），所以可以离线驱动（见交付报告 §8 的自证）。
     */
    public static final Set<String> HEAT_SLOT_MACHINE_IDS = Set.of(
            "shanhai:taixu_smelting_furnace",
            "shanhai:primordial_eternal_smelting_furnace",
            "shanhai:primordial_molecular_rift_core");

    /** 这台机器该不该有恒星热力槽。用户答案 = B（只留那三台）。 */
    public static boolean hasHeatSlot(String machineId) {
        return machineId != null && HEAT_SLOT_MACHINE_IDS.contains(machineId);
    }

    /**
     * <b>白名单自检</b>：三个 id 必须逐个都能在已注册的模块 id 列表里找到。
     *
     * @param registeredMachineIds 已注册的模块 id（形如 {@code "shanhai:" + SPECS.path()}）
     * @return <b>通过返回 {@code null}</b>；不通过返回<b>完整的中文错误文本</b>（调用方直接抛出去）
     */
    public static String verifyMachineIds(java.util.List<String> registeredMachineIds) {
        if (registeredMachineIds == null || registeredMachineIds.isEmpty()) {
            return "[SHANHAI-HEATSLOT] 恒星热力槽白名单自检失败：已注册模块 id 列表为空 / 为 null。"
                    + " 白名单 = " + HEAT_SLOT_MACHINE_IDS
                    + " ⇒ 多半是 ModuleRegistry.SPECS 没被读到；此时【任何一台机器都不会有热力槽】。";
        }
        final StringBuilder missing = new StringBuilder();
        int miss = 0;
        for (String wanted : HEAT_SLOT_MACHINE_IDS) {
            if (!registeredMachineIds.contains(wanted)) {
                miss++;
                if (missing.length() > 0) {
                    missing.append(" / ");
                }
                missing.append(wanted);
            }
        }
        if (miss == 0) {
            return null;
        }
        return "[SHANHAI-HEATSLOT] 恒星热力槽白名单里有 " + miss + " / " + HEAT_SLOT_MACHINE_IDS.size()
                + " 个 id 【不在已注册的模块里】：" + missing
                + " ⇒ 白名单 = " + HEAT_SLOT_MACHINE_IDS
                + "；已注册 " + registeredMachineIds.size() + " 台 = " + registeredMachineIds
                + " ⇒ 要么白名单写错了 id（后果 = 玩家【看不到那一格】，而日志里本来一个字都不会有），"
                + "要么那台机器被改名 / 删掉了。两种都不许静默通过。";
    }

    /**
     * 拒绝原因。<b>顺序即优先级</b>（先空的、再没放满的、再放错东西的、最后才是不够的）——
     * 这样玩家看到的永远是"当前最该先修的那一条"，而不是一个笼统的"条件不满足"。
     */
    public enum Deny {
        /** 放行。 */
        NONE,
        /** 放行：这条配方自己没写温度/容器等级门槛 ⇒ 本槽与它无关。 */
        NO_REQUIREMENT,
        /** 槽是空的。 */
        SLOT_EMPTY,
        /** 放了，但没放满 64 个（例：63/64）。 */
        SLOT_NOT_FULL,
        /** 放满了，但放的是恒星热力容器，而这条配方要的是**线圈炉温**。 */
        NEED_COIL,
        /** 放满了，但放的是线圈，而这条配方要的是**恒星热力容器等级**。 */
        NEED_CONTAINMENT,
        /** 放满了线圈，但线圈温度不够。 */
        COIL_TEMP,
        /** 放满了恒星热力容器，但容器等级不够。 */
        SC_TIER,
    }

    /**
     * 一次判定的结果。不可变。<b>所有数字都原样带出来</b>，好让上层把
     * 「需要 10800K，当前 3600K」这种**具体**文案直接渲出来（而不是「条件不满足」）。
     */
    public static final class Outcome {

        /** 是否放行。 */
        public final boolean allowed;
        /** 拒绝原因（放行时是 {@link Deny#NONE} 或 {@link Deny#NO_REQUIREMENT}）。 */
        public final Deny deny;
        /** 配方要的炉温（K）；0 = 这条配方不要炉温。 */
        public final int needTemp;
        /** 槽实际提供的炉温（K）；0 = 没提供。 */
        public final int haveTemp;
        /** 配方要的容器等级；0 = 这条配方不要容器等级。 */
        public final int needTier;
        /** 槽实际提供的容器等级；0 = 没提供。 */
        public final int haveTier;
        /** 槽里的实际数量。 */
        public final int count;

        Outcome(boolean allowed, Deny deny, int needTemp, int haveTemp,
                int needTier, int haveTier, int count) {
            this.allowed = allowed;
            this.deny = deny;
            this.needTemp = needTemp;
            this.haveTemp = haveTemp;
            this.needTier = needTier;
            this.haveTier = haveTier;
            this.count = count;
        }

        /** 一行可 grep 的读数（证据行用它）。 */
        public String describe() {
            return "count=" + count + "/" + REQUIRED_COUNT
                    + " haveTemp=" + haveTemp + "K needTemp=" + needTemp + "K"
                    + " haveTier=" + haveTier + " needTier=" + needTier
                    + " ⇒ " + (allowed ? "放行" : "拦下（" + deny + "）");
        }
    }

    /**
     * 判定。
     *
     * @param count           槽里的数量（同一格内累加；空槽 = 0）
     * @param hasCoil         槽里那件东西**是不是加热线圈**（{@code CoilBlock}）
     * @param coilTemp        线圈的额定炉温（K）；不是线圈时传 0
     * @param hasContainment  槽里那件东西**是不是恒星热力容器**
     * @param containmentTier 容器的等级（基础 1 / 高级 2 / 终极 3）；不是容器时传 0
     * @param needTemp        配方要的炉温（K）；0 = 不要
     * @param needTier        配方要的容器等级；0 = 不要
     */
    public static Outcome evaluate(int count,
                                   boolean hasCoil, int coilTemp,
                                   boolean hasContainment, int containmentTier,
                                   int needTemp, int needTier) {
        // ① 这条配方自己没写门槛 ⇒ 本槽与它无关（「若选择其他配方则无视这个格子」的第二条分流）。
        if (needTemp <= 0 && needTier <= 0) {
            return new Outcome(true, Deny.NO_REQUIREMENT, 0, 0, 0, 0, count);
        }
        // ② 空槽 —— 比"没放满"更具体，先说它。
        if (count <= 0) {
            return new Outcome(false, Deny.SLOT_EMPTY, needTemp, 0, needTier, 0, count);
        }
        // ③ 放了，但没放满。
        if (count < REQUIRED_COUNT) {
            return new Outcome(false, Deny.SLOT_NOT_FULL, needTemp, 0, needTier, 0, count);
        }
        // ④ 放满了，但放错了类别（用户举的负例：要炉温却放了容器 / 反过来）。
        if (needTemp > 0 && !hasCoil) {
            return new Outcome(false, Deny.NEED_COIL, needTemp, 0, needTier, 0, count);
        }
        if (needTier > 0 && !hasContainment) {
            return new Outcome(false, Deny.NEED_CONTAINMENT, needTemp, coilTemp, needTier, 0, count);
        }
        // ⑤ 类别对、数量够，就看值够不够。
        if (needTemp > 0 && coilTemp < needTemp) {
            return new Outcome(false, Deny.COIL_TEMP, needTemp, coilTemp, needTier, containmentTier, count);
        }
        if (needTier > 0 && containmentTier < needTier) {
            return new Outcome(false, Deny.SC_TIER, needTemp, coilTemp, needTier, containmentTier, count);
        }
        return new Outcome(true, Deny.NONE, needTemp, coilTemp, needTier, containmentTier, count);
    }
}
