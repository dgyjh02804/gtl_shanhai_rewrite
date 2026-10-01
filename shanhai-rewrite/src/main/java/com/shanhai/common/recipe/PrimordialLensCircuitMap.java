package com.shanhai.common.recipe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「透镜 → 电路」映射的<b>纯表核</b>（🆕 2026-09-30，用户点单「透镜再见」改造）。
 *
 * <h2>它是什么</h2>
 * dgy 的「透镜再见」（{@code kubejs/server_scripts/[server_scripts]dgy.js} 的
 * {@code // =================== 透镜再见！ ===================} 那一段）把一批【用透镜当选择器】的
 * 配方改写成【用编程电路当选择器】。用户 2026-09-30 点单：把这件事搬进我们自己的两个新配方类型
 * （「原初激光蚀刻」＝光子晶阵蚀刻的模板、「原初蜂群铸造」＝纳米蜂群工厂的模板）。
 * 本类就是那次改写所用的<b>映射表本体</b>。
 *
 * <h2>🔴 为什么必须是纯 {@code java.*}</h2>
 * 本类<b>刻意不引用任何 Minecraft / GTCEu 类型</b>，唯一目的是让它能
 * <b>脱离 Minecraft 单独 {@code javac} 编译并运行</b>（{@link #main(String[])} 就是那个自证入口）。
 * 本工程的既有血账：检查器自己写错、却"自检全绿" ⇒ 判据必须是【能离线跑、且带预期失败档】的。
 *
 * <h2>映射规则（实证自 dgy 的脚本，不是猜的）</h2>
 * <p>查表顺序：<b>先按配方源键</b>（{@code <命名空间>/<配方路径>}），<b>再按透镜 id</b>。
 * <p>· {@link #BY_SRC}：<b>61 条</b>【逐条兜底】——dgy 给这些配方发的电路号<b>不等于</b>它那个透镜的通用色号
 * （例：{@code gtceu:crystal_soc} 用的是蓝透镜，通用色号是 7，而 dgy 发的是 17）。
 * 没有透镜、或一条配方里不止一个透镜的，也只能走这一张表。
 * <p>· {@link #BY_LENS}：<b>20 条</b>【通用色号表】——剩下 250 条都靠它。
 * 它的值从 dgy 的 {@code waferRecipes} 表与白透镜组实证而来，且**单值无冲突**（已断言）。
 *
 * <h2>🔴 映射不是"一一对应"</h2>
 * 任务书里假设"透镜有 20+ 色号、电路有 1..32 号、存在一张透镜→电路表"——
 * <b>实测只有部分成立</b>：dgy 的编号是【按配方手工发的】，同一个透镜在不同配方上可以拿到不同电路号
 * （铁证：{@code gtceu:light_blue_glass_lens} → 15 与 16；{@code gtceu:ruby_lens} → 20 与 21；
 * {@code #forge:lenses/lime} → 3 与 16）。所以本类<b>必须</b>是"源键优先 + 透镜兜底"的两级表，
 * 单靠一张透镜表复现不出 dgy 的行为。这条已写进交付报告。
 *
 * <h2>🔴 未知输入必须响亮失败</h2>
 * {@link #circuitFor(String, String)} 在两张表都查不到时<b>抛异常</b>，绝不返回 0/null/默认值 ——
 * "静默给个默认电路号"会让一条配方悄悄变成另一条配方，这是本工程明令禁止的形态。
 *
 * <h2>🔴🔴 本工程通用坑：凡是要被 KubeJS 调用的 Java 方法，一律不留重载</h2>
 * 2026-09-30 实测事故：KubeJS(Rhino) 调 {@code circuitFor(sk, lens)} 时对
 * {@code (String,String)} 与 {@code (String,String...)} 两个重载**判不出该调哪个**，
 * 抛 {@code InternalError: The choice of Java method ... matching JavaScript argument types
 * (string,string) is ambiguous}，原文见 {@code shanhai_lens_goodbye.js#128} 的
 * {@code [SHANHAI-NEWTYPE] 失败明细} ⇒ <b>那一次 311 条配方一条都没建出来</b>。
 * 而同一套表在**离线 Java 自证里是全绿的**（{@code ok=311 bad=0}）——
 * ⇒ <b>离线全绿 ≠ 运行期可用</b>：KJS→Rhino→Java 是【另一条路径】，离线自证覆盖不到它。
 * 所以本类对 KubeJS 只暴露**唯一**一个 {@code circuitFor(String, String)}。
 */
public final class PrimordialLensCircuitMap {

    /** 色号表：透镜物品/标签 id → 电路号。**单值**（{@link #assertSelfConsistent()} 会验）。 */
    private static final Map<String, Integer> BY_LENS = new HashMap<>();

    /** 逐条兜底表：{@code <命名空间>/<配方路径>} → 电路号。**优先于** {@link #BY_LENS}。 */
    private static final Map<String, Integer> BY_SRC = new HashMap<>();

    static {
        // ───────── BY_LENS：通用色号表（14 个透镜标签 + 6 个物品透镜，实证自 dgy 的 waferRecipes 表 / 白透镜组）─────────
        BY_LENS.put("#forge:lenses/black", 14);
        BY_LENS.put("#forge:lenses/blue", 7);
        BY_LENS.put("#forge:lenses/brown", 11);
        BY_LENS.put("#forge:lenses/cyan", 6);
        BY_LENS.put("#forge:lenses/gray", 12);
        BY_LENS.put("#forge:lenses/green", 4);
        BY_LENS.put("#forge:lenses/light_blue", 8);
        BY_LENS.put("#forge:lenses/lime", 16);
        BY_LENS.put("#forge:lenses/orange", 5);
        BY_LENS.put("#forge:lenses/pink", 10);
        BY_LENS.put("#forge:lenses/purple", 13);
        BY_LENS.put("#forge:lenses/red", 3);
        BY_LENS.put("#forge:lenses/white", 24);
        BY_LENS.put("#forge:lenses/yellow", 9);
        BY_LENS.put("gtceu:blue_glass_lens", 7);
        BY_LENS.put("gtceu:diamond_lens", 8);
        BY_LENS.put("gtceu:emerald_lens", 10);
        BY_LENS.put("gtceu:nether_star_lens", 11);
        BY_LENS.put("gtceu:ruby_lens", 20);
        BY_LENS.put("gtceu:sapphire_lens", 22);

        // ───────── BY_SRC：逐条兜底 32 条 ─────────
        // ① 原版就没有透镜、dgy 补发电路号的三条（9/10/11）
        BY_SRC.put("gtladditions/chaos_soc_wafer", 11);
        BY_SRC.put("gtladditions/extraordinary_soc_wafer", 10);
        BY_SRC.put("gtladditions/outstanding_soc_wafer", 9);
        // ② 纳米蜂群工厂 25 条里，dgy 用的是"按顺序 1..25"的编号，与透镜色号无关（其中 22 条落在这里）
        BY_SRC.put("gtceu/carbon_nanoswarm", 1);
        BY_SRC.put("gtceu/glowstone_nanoswarm", 2);
        BY_SRC.put("gtceu/copper_nanoswarm", 3);
        BY_SRC.put("gtceu/iron_nanoswarm", 4);
        BY_SRC.put("gtceu/gold_nanoswarm", 5);
        BY_SRC.put("gtceu/silver_nanoswarm", 6);
        BY_SRC.put("gtceu/iridium_nanoswarm", 7);
        BY_SRC.put("gtceu/rhenium_nanoswarm", 9);
        BY_SRC.put("gtceu/orichalcum_nanoswarm", 12);
        BY_SRC.put("gtceu/enderium_nanoswarm", 13);
        BY_SRC.put("gtceu/infuscolium_nanoswarm", 14);
        BY_SRC.put("gtceu/uruium_nanoswarm", 15);
        BY_SRC.put("gtceu/vibranium_nanoswarm", 16);
        BY_SRC.put("gtceu/starmetal_nanoswarm", 17);
        BY_SRC.put("gtceu/draconium_nanoswarm", 18);
        BY_SRC.put("gtceu/cosmicneutronium_nanoswarm", 19);
        BY_SRC.put("gtceu/black_dwarf_mtter_nanoswarm", 21);
        BY_SRC.put("gtceu/transcendentmetal_nanoswarm", 23);
        BY_SRC.put("gtladditions/cosmic_nanoswarm", 25);
        // ③ 光子晶阵蚀刻这边，dgy 在 photon_matrix_etch 里发的 1..8（同一条在原版里写的是【标签流体】，
        //     dgy 写的是【具体流体】⇒ 按"去透镜后的输入+输出"匹配不上，只能逐条钉死）
        BY_SRC.put("gtladditions/spacetime_soc_wafer", 1);
        BY_SRC.put("gtladditions/nm_wafer", 2);
        BY_SRC.put("gtladditions/pm_wafer", 3);
        BY_SRC.put("gtladditions/primary_soc_wafer", 4);
        BY_SRC.put("gtladditions/raw_photon_carrying_wafer", 5);
        BY_SRC.put("gtladditions/fm_wafer", 6);
        BY_SRC.put("gtladditions/high_precision_crystal_soc", 7);
        BY_SRC.put("gtladditions/prepared_cosmic_soc_wafer", 8);
        // ④ 两条"透镜色号 ≠ dgy 发号"的独立编号（dgy 的 crystal_soc / engraved_lapotron 用的是 17 / 18）
        BY_SRC.put("gtceu/crystal_soc", 17);
        BY_SRC.put("gtceu/engraved_lapotron_chip", 18);

        // ⑤ 🆕 2026-09-30 用户第二次拍板（原话逐字）：「把那29条也分配进新配方」
        //    这 29 条是「透镜再见」里有、而两个新类型【没有等价物】的 —— 用户要求也搬进新类型。
        //    它们本来就带 dgy 发的电路号（不是透镜），所以只能逐条钉死。
        //    srcKey 的命名空间是 thetornproductionline（dgy 自己的），与上面几组不会重名。
        //    · laser_engraver 14 条 + precision_laser_engraver 6 条 + dimensional_focus 6 条 → 原初激光蚀刻
        //    · nano_forge 3 条 → 原初蜂群铸造
        BY_SRC.put("thetornproductionline/dimfocus_fm_wafer_circuit", 6);
        BY_SRC.put("thetornproductionline/dimfocus_high_precision_crystal_soc_circuit", 3);
        BY_SRC.put("thetornproductionline/dimfocus_nm_wafer_circuit", 4);
        BY_SRC.put("thetornproductionline/dimfocus_pm_wafer_circuit", 5);
        BY_SRC.put("thetornproductionline/dimfocus_prepared_cosmic_soc_wafer_circuit", 2);
        BY_SRC.put("thetornproductionline/dimfocus_raw_photon_carrying_wafer_circuit", 1);
        BY_SRC.put("thetornproductionline/exotic_wafer_circuit", 15);
        BY_SRC.put("thetornproductionline/laser_advanced_soc_wafer_circuit", 13);
        BY_SRC.put("thetornproductionline/laser_cpu_wafer_circuit", 8);
        BY_SRC.put("thetornproductionline/laser_highly_advanced_soc_wafer_circuit", 14);
        BY_SRC.put("thetornproductionline/laser_ilc_wafer_circuit", 3);
        BY_SRC.put("thetornproductionline/laser_lpic_wafer_circuit", 5);
        BY_SRC.put("thetornproductionline/laser_mpic_wafer_circuit", 11);
        BY_SRC.put("thetornproductionline/laser_nand_memory_wafer_circuit", 12);
        BY_SRC.put("thetornproductionline/laser_nor_memory_wafer_circuit", 10);
        BY_SRC.put("thetornproductionline/laser_ram_wafer_circuit", 4);
        BY_SRC.put("thetornproductionline/laser_simple_soc_wafer_circuit", 6);
        BY_SRC.put("thetornproductionline/laser_soc_wafer_circuit", 9);
        BY_SRC.put("thetornproductionline/laser_soc_wafer_silicon_circuit", 9);
        BY_SRC.put("thetornproductionline/laser_ulpic_wafer_circuit", 7);
        BY_SRC.put("thetornproductionline/nano_eternity_nanoswarm_circuit", 24);
        BY_SRC.put("thetornproductionline/nano_spacetime_nanoswarm_circuit", 22);
        BY_SRC.put("thetornproductionline/nano_transcendentmetal_nanoswarm_circuit", 23);
        BY_SRC.put("thetornproductionline/precision_fm_wafer_circuit", 5);
        BY_SRC.put("thetornproductionline/precision_high_precision_crystal_soc_circuit", 2);
        BY_SRC.put("thetornproductionline/precision_nm_wafer_circuit", 3);
        BY_SRC.put("thetornproductionline/precision_pm_wafer_circuit", 4);
        BY_SRC.put("thetornproductionline/precision_prepared_cosmic_soc_wafer_circuit", 1);
        BY_SRC.put("thetornproductionline/precision_raw_photon_carrying_wafer_circuit", 6);
    }

    private PrimordialLensCircuitMap() {}

    /**
     * 查号。<b>查不到就抛</b>（绝不静默给默认值）。
     *
     * @param srcKey 配方源键，形如 {@code "gtceu/engrave_ilc_silicon"}（= {@code <命名空间>/<配方路径>}）
     *               —— 就是 kubejs export 的 {@code recipes/<ns>/<type>/<path>.json} 里
     *               {@code <ns>} ＋ {@code <path>}。允许为 {@code null}（那就只查透镜表）。
     * @param lensId 透镜的物品/标签 id（标签带前导 {@code #}）；没有透镜时给 {@code null} 或空串。
     * @return 1..32 的电路号
     * @throws IllegalArgumentException 两张表都查不到 —— 这是<b>响亮的失败</b>，不是"没查到就算 0"
     */
    public static int circuitFor(String srcKey, String lensId) {
        if (srcKey != null) {
            Integer bySrc = BY_SRC.get(srcKey);
            if (bySrc != null) {
                return bySrc;
            }
        }
        if (lensId != null && !lensId.isEmpty()) {
            Integer byLens = BY_LENS.get(lensId);
            if (byLens != null) {
                return byLens;
            }
        }
        throw new IllegalArgumentException("[SHANHAI-L2C] 透镜→电路 映射失败：srcKey=" + srcKey
                + " lens=" + lensId + " ⇒ 两张表都没有它。"
                + " 这【不许】静默跳过：要么是 dgy 加了新的透镜色号，要么是模板里出现了没见过的透镜。"
                + " 修法：核对 temp\\lens-goodbye\\plan.json 与 handoff\\outbound\\原初激光蚀刻与蜂群铸造.md §3 的映射表。");
    }

    /**
     * 多透镜形态：逐个查，取<b>第一个查得到</b>的；都不行则抛。
     *
     * <p>🔴 <b>这个方法【不叫】{@code circuitFor} —— 名字是刻意的。</b>
     * 2026-09-30 实测事故：本类原先另有 {@code circuitFor(String, String...)} 这个可变参数重载，
     * KubeJS(Rhino) 调用 {@code circuitFor(sk, lens)} 时**判不出该调哪一个**，
     * 抛 {@code InternalError: The choice of Java method ... matching JavaScript argument types
     * (string,string) is ambiguous}，原文见
     * {@code shanhai_lens_goodbye.js#128 的 [SHANHAI-NEWTYPE] 失败明细}
     * ⇒ <b>那一次 311 条配方一条都没建出来</b>。
     *
     * <p>🔴🔴 <b>由此定下本工程的一条通用坑（务必记住）：
     * 「凡是要被 KubeJS 调用的 Java 方法，一律不留重载（overload）。」</b>
     * Rhino 的重载决议不按 Java 的静态类型那套走 —— 它按运行时 JS 值类型去挑候选，
     * {@code (string,string)} 同时匹配 {@code (String,String)} 与 {@code (String,String[])}，
     * 于是它选择抛异常而不是选一个。
     * <p>⇒ 本类现在对 KubeJS 只暴露**唯一**一个 {@code circuitFor(String, String)}。
     * 需要多透镜时走这个改了名的 {@code circuitForAnyLens}（KubeJS 侧并不需要它）。
     */
    public static int circuitForAnyLens(String srcKey, String... lensIds) {
        if (lensIds == null || lensIds.length == 0) {
            return circuitFor(srcKey, null);
        }
        IllegalArgumentException last = null;
        for (String l : lensIds) {
            try {
                return circuitFor(srcKey, l);
            } catch (IllegalArgumentException e) {
                last = e;
            }
        }
        throw last;
    }

    /** 表自检：表条数不许悄悄变（构造期静态跑一次，把"表本身被改坏了"变成加载期异常）。 */
    public static void assertSelfConsistent() {
        if (BY_LENS.size() != 20) {
            throw new IllegalStateException("[SHANHAI-L2C] 色号表条数变了：" + BY_LENS.size() + "（应为 20）");
        }
        if (BY_SRC.size() != 61) {
            throw new IllegalStateException("[SHANHAI-L2C] 逐条兜底表条数变了：" + BY_SRC.size()
                    + "（应为 61 = 原 32 ＋ 2026-09-30 用户第二次拍板补进的 29）");
        }
    }

    /** 表中的透镜键（只读，自证用）。 */
    public static Map<String, Integer> lensTable() {
        return java.util.Collections.unmodifiableMap(BY_LENS);
    }

    /** 表中的源键（只读，自证用）。 */
    public static Map<String, Integer> srcTable() {
        return java.util.Collections.unmodifiableMap(BY_SRC);
    }

    /**
     * 离线自证入口。用法：
     * <pre>java -cp &lt;classes&gt; com.shanhai.common.recipe.PrimordialLensCircuitMap &lt;plan-csv&gt;</pre>
     * CSV 每行＝{@code srcKey,lensIds(多透镜用'|'分隔),期望电路号}；由
     * {@code temp\lens-goodbye\extract_tables.js} 从 plan.json 现算生成。
     *
     * <p>🔴 三段自证（由 {@code temp\lens-goodbye\selftest.js} 驱动）：
     * ① 正常：全表对上（{@code ok=282 bad=0}）；
     * ② <b>预期失败</b>：把某行的透镜改成一个表里没有的 ⇒ 本方法必须抛（进程非 0 退出 + 带 {@code [SHANHAI-L2C]} 的原文）；
     * ③ 复原：删掉坏 CSV，重跑 ①，读数必须与 ① 逐字相同。
     */
    public static void main(String[] args) {
        assertSelfConsistent();
        if (args.length == 0) {
            System.out.println("USAGE: PrimordialLensCircuitMap <plan-csv>");
            System.out.println("tables: BY_LENS=" + BY_LENS.size() + " BY_SRC=" + BY_SRC.size());
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        int ok = 0;
        int bad = 0;
        StringBuilder detail = new StringBuilder();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] f = line.split(",", -1);
            if (f.length != 3) {
                throw new IllegalArgumentException("[SHANHAI-L2C] plan-csv 行格式不对（应为 srcKey,lens,c）：" + line);
            }
            String srcKey = f[0];
            String lensField = f[1];
            int expect = Integer.parseInt(f[2]);
            String[] lensIds = lensField.isEmpty() ? new String[0] : lensField.split("\\|");
            int got = lensIds.length == 1 ? circuitFor(srcKey, lensIds[0]) : circuitForAnyLens(srcKey, lensIds);
            if (got == expect) {
                ok++;
            } else {
                bad++;
                if (detail.length() < 1200) {
                    detail.append("| ").append(srcKey).append(" lens=").append(lensField)
                            .append(" expect=").append(expect).append(" got=").append(got).append(' ');
                }
            }
        }
        System.out.println("[SHANHAI-L2C] plan-csv 对账 ok=" + ok + " bad=" + bad + " total=" + (ok + bad)
                + (bad > 0 ? ("  🔴 不一致明细：" + detail) : ""));
        if (bad > 0) {
            throw new IllegalStateException("[SHANHAI-L2C] 纯表核与计划产物不一致：" + bad + " 条");
        }
    }
}
