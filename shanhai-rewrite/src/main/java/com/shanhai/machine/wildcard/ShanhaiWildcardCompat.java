package com.shanhai.machine.wildcard;

import appeng.api.crafting.IPatternDetails;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 「通配符样板」的<b>唯一</b>解析入口 —— 把 {@code wildcard_pattern} 这个外部 mod 的两件事
 * （「这块物品算不算通配符样板」＋「把它展开成哪些 AE 样板」）收在一处。
 *
 * <h2>🔴 为什么要有这一层，而不是直接 import</h2>
 * gtlcore 自己的 {@code org.gtlcore.gtlcore.integration.wildcard.WildcardPatternCompatImpl}
 * 里确实有这两个函数，但它们是 <b>包私有（package-private）</b>：
 * <pre>
 *   static boolean isWildcardPattern(ItemStack stack)         ← 无 public
 *   static List&lt;IPatternDetails&gt; expandPatterns(ItemStack, Level)  ← 无 public
 * </pre>
 * （取证：vineflower 反编译 gtlcore-1.2.3.2，见 {@code temp\wildcard\decomp\...\WildcardPatternCompatImpl.java:50/54}。）
 * 本 mod 在 {@code com.shanhai.*} 包下，**拿不到**这两个方法。
 *
 * <p>⇒ 本类用<b>公开 API</b>等价复刻它们的语义（两处都取自同一批公开符号）：
 * <ul>
 *   <li>{@code isWildcardPattern} ← {@code stack.is(WildcardItems.WILDCARD_PATTERN.get())}
 *       （gtlcore 原文 {@code stack.m_150930_(WildcardItems.WILDCARD_PATTERN.m_5456_())}，逐字同源）</li>
 *   <li>{@code expandPatterns} ← {@code WildcardPatternLogic.decodePatterns(stack, level)}
 *       （gtlcore 原文同样是这一句，见 {@code WildcardPatternCompatImpl.java:57}）</li>
 * </ul>
 *
 * <h2>🔴 为什么不直接 import {@code WildcardItems}</h2>
 * 那两个类是**别的 mod 的类**。若整体 import，则「没装 wildcard_pattern」时加载本类就会
 * {@code NoClassDefFoundError} —— 而本 mod 的仓室**在没装它时也必须能注册**（否则会出现
 * 「物品在 JEI 里消失且日志无异常」这种本工程最忌讳的静默失败）。
 * 因此外部类引用全部关在 {@link Holder} 里：<b>Holder 只在第一次真正问「这块算不算通配符样板」时才初始化</b>，
 * 缺 mod 时 {@link Holder#WILDCARD_PATTERN} 为 {@code null}、{@code isWildcardPattern} 恒 false，
 * 机器照常存在、槽位照常拒绝一切物品，并在日志里留一条 WARN。
 */
public final class ShanhaiWildcardCompat {

    /** 提供通配符样板的 mod id（与 gtlcore 的 {@code WildcardPatternCompat.MOD_ID} 逐字一致）。 */
    public static final String MOD_ID = "wildcard_pattern";

    private static final Logger LOGGER = LogUtils.getLogger();

    private ShanhaiWildcardCompat() {}

    /** 这块物品是不是「通配符样板」。缺 mod ⇒ 恒 false（不抛）。 */
    public static boolean isWildcardPattern(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        final Item pattern = Holder.WILDCARD_PATTERN;
        return pattern != null && stack.is(pattern);
    }

    /**
     * 把一块通配符样板展开成它覆盖的全部 AE 样板。
     *
     * <p>语义与 gtlcore 的 {@code WildcardPatternCompatImpl.expandPatterns} 逐字一致：
     * 不是通配符样板、或客户端侧拿不到 level ⇒ 返回<b>空表</b>（不是 null）。
     *
     * <p>⚠️ 展开过程会遍历 GTCEu 已注册的全部材料并按过滤器取舍，单次代价不低 ——
     * 本类不缓存，调用方（{@code SuperWildcardPatternBufferPartMachine.refreshPatterns}）
     * 只在「样板变化 / 载入存档」时调它。
     */
    public static List<IPatternDetails> expandPatterns(ItemStack stack, Level level) {
        if (!isWildcardPattern(stack) || level == null) {
            return List.of();
        }
        try {
            final List<IPatternDetails> result = new ArrayList<>();
            final Stream<IPatternDetails> stream = Holder.decode(stack, level);
            stream.forEach(result::add);
            return result;
        } catch (Throwable t) {
            // 展开失败 = 这块样板暂时不出样板，但机器**不能**因此崩掉：槽里那块物品仍在，
            // 下一次 onWildcardPatternChange 会再试。失败必须出声，不许静默吞。
            LOGGER.error("[SHANHAI-WILDCARD] 通配符样板展开失败（该槽本次不出样板，物品仍在槽内）：", t);
            return List.of();
        }
    }

    /**
     * 外部 mod 类引用的隔离舱。<b>只有真正要用的时候才会被类初始化器加载。</b>
     *
     * <p>判据（可 grep 日志）：装了 wildcard_pattern ⇒
     * {@code [SHANHAI-WILDCARD] 通配符样板来源已解析：<item>}；
     * 没装 ⇒ {@code [SHANHAI-WILDCARD] 未检测到 wildcard_pattern …}（WARN，一条）。
     */
    private static final class Holder {

        /** 通配符样板的 {@code Item}；缺 mod 或解析失败时为 {@code null}。 */
        static final Item WILDCARD_PATTERN;

        static {
            Item resolved = null;
            try {
                final ModList modList = ModList.get();
                if (modList != null && modList.isLoaded(MOD_ID)) {
                    // 这一句会触发 WildcardItems.<clinit>。gtlcore 走的是同一条路
                    // （WildcardPatternCompatImpl.isWildcardPattern），因此这不是新引入的时机风险。
                    resolved = org.leodreamer.wildcard_pattern.WildcardItems.WILDCARD_PATTERN.get();
                    LOGGER.info("[SHANHAI-WILDCARD] 通配符样板来源已解析：{}", resolved);
                } else {
                    LOGGER.warn("[SHANHAI-WILDCARD] 未检测到 mod {}：超级通配符ME样板总成仍会注册，"
                            + "但它的样板槽不接受任何物品（isWildcardPattern 恒 false）。", MOD_ID);
                }
            } catch (Throwable t) {
                LOGGER.error("[SHANHAI-WILDCARD] 解析 mod {} 的通配符样板物品失败：槽位将拒绝一切物品。", MOD_ID, t);
            }
            WILDCARD_PATTERN = resolved;
        }

        static Stream<IPatternDetails> decode(ItemStack stack, Level level) {
            return org.leodreamer.wildcard_pattern.wildcard.WildcardPatternLogic.decodePatterns(stack, level);
        }

        private Holder() {}
    }
}
