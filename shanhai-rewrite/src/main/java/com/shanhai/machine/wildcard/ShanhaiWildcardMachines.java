package com.shanhai.machine.wildcard;

import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.mojang.logging.LogUtils;
import com.shanhai.registry.ShanhaiRegistration;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 超级通配符ME样板总成的注册入口。
 *
 * <h2>🔴 注册链必须与宿主同一条（不许自造第三种时机）</h2>
 * 宿主 {@code gtceu:me_wildcard_pattern_buffer} 的注册形态（反编译
 * {@code WildcardPatternCompatImpl.registerMachines()}，逐字）：
 * <pre>
 *   REGISTRATE.machine("me_wildcard_pattern_buffer", holder -&gt; new MEWildcardPatternBufferPartMachine(holder, IO.BOTH))
 *       .langValue("ME Wildcard Pattern Buffer").tier(9).rotationState(RotationState.ALL)
 *       .abilities(IMPORT_ITEMS, IMPORT_FLUIDS, EXPORT_ITEMS, EXPORT_FLUIDS)
 *       .overlayTieredHullRenderer("me_pattern_buffer")
 *       .tooltips(…).tooltipBuilder(GTLMachines.GTL_ADD).register();
 * </pre>
 * 本类<b>逐项照抄</b>，只换两处：REGISTRATE 用本工程的
 * （{@link ShanhaiRegistration#REGISTRATE}，命名空间 {@code shanhai}），
 * 工厂 lambda 换成 {@link SuperWildcardPatternBufferPartMachine}。
 *
 * <h2>🔴 overlay 模型为什么必须自带一份（2026-10-01 贴图缺失事故的根因）</h2>
 * <p>上一版这里的注释写的是「{@code overlayTieredHullRenderer} 把命名空间写死成 gtceu」——
 * <b>那是错的</b>，而且正是这次「新物品没有贴图」的根因。
 * <p>{@code javap -v} 读 {@code MachineBuilder} 的真实字节码
 * （BootstrapMethods 第 50 项 = {@code block/machine/part/\u0001}）：
 * <pre>
 *   lambda$overlayTieredHullRenderer$10(String name):
 *       new OverlayTieredMachineRenderer(this.tier,
 *             new ResourceLocation(this.registrate.getModid(), "block/machine/part/" + name))
 * </pre>
 * ⇒ 命名空间取自 <b>{@code registrate.getModid()}</b>，<b>不是</b> {@code GTCEu.id(...)}。
 * <ul>
 *   <li>宿主（gtlcore {@code WildcardPatternCompatImpl}）用的 {@code GTRegistration.REGISTRATE}
 *       是 {@code GTRegistrate.create("gtceu")}（javap 实测 {@code ldc "gtceu"}）
 *       ⇒ 它取的是 {@code gtceu:block/machine/part/me_pattern_buffer}，gtceu-1.20.1-1.4.4.jar 里<b>有</b>；</li>
 *   <li>本工程用 {@code GTRegistrate.create(ShanhaiMod.MOD_ID)}（= {@code shanhai}）
 *       ⇒ 会去取 {@code shanhai:block/machine/part/me_pattern_buffer}，
 *       而我们<b>没有</b>这个文件 ⇒ overlay 模型解析失败 ⇒ 正面那圈 ME 样板仓贴图不渲染
 *       （方块与物品走的是<b>同一条</b>渲染路径：
 *        {@code OverlayTieredMachineRenderer.renderMachine} 先画 tiered hull、再叠 {@code overlayModel}；
 *        物品侧 {@code MetaMachineItem implements IItemRendererProvider} ⇒
 *        {@code MachineRenderer.renderItem} 用同一个 {@code renderMachine} 现算）。</li>
 * </ul>
 * <p>⇒ 修法：在<b>本工程自己的命名空间</b>下补一份同名 overlay 模型
 * {@code src/main/resources/assets/shanhai/models/block/machine/part/me_pattern_buffer.json}，
 * 内容与 gtceu 那份逐字相同（{@code parent=gtceu:block/overlay/front}、
 * {@code overlay=gtceu:block/overlay/appeng/me_buffer_hatch}）
 * ⇒ 外观与原版「通配符ME样板总成」<b>同一套贴图</b>，且不新增任何 png。
 * <p>⚠️ 外观的「底板」永远是 {@code gtceu:block/machine/hull_machine} +
 * {@code gtceu:block/casings/voltage/<tier>/…}（{@code OverlayTieredMachineRenderer} 构造器里写死
 * {@code GTCEu.id(…)}）⇒ 这一部分本来就不受影响，缺的只是 overlay。
 *
 * <h2>🔴 注册时机</h2>
 * 由 {@code ShanhaiRegistry.onMachineRegister}（GTCEu 的 {@code MachineDefinition}
 * {@code GTCEuAPI.RegisterEvent}，即 {@code GTMachines.init()} 内部 post 的那一拍）调用。
 * 与模块、主机<b>同一拍</b>，不是新时机。
 */
public final class ShanhaiWildcardMachines {

    /**
     * 注册名（方块 / 物品 / BlockEntity 三合一）。
     *
     * <p>⇒ 物品 id 是 {@code shanhai:super_wildcard_pattern_buffer}，
     * 它就是需求③要写进宿主 KubeJS 那个「无限元件物品列表」的字符串。
     */
    public static final String SUPER_WILDCARD_PATTERN_BUFFER_ID = "super_wildcard_pattern_buffer";

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 幂等守卫：GTCEu 将来若多 post 一次事件，重复注册会抛「registry contains key … already」。 */
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /** 注册后非空。 */
    private static volatile MachineDefinition superWildcardPatternBuffer;

    private ShanhaiWildcardMachines() {}

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        superWildcardPatternBuffer = ShanhaiRegistration.REGISTRATE
                .machine(SUPER_WILDCARD_PATTERN_BUFFER_ID,
                        holder -> new SuperWildcardPatternBufferPartMachine(holder, IO.BOTH))
                .langValue("Super Wildcard ME Pattern Buffer")
                .tier(9)
                .rotationState(RotationState.ALL)
                .abilities(
                        PartAbility.IMPORT_ITEMS,
                        PartAbility.IMPORT_FLUIDS,
                        PartAbility.EXPORT_ITEMS,
                        PartAbility.EXPORT_FLUIDS)
                .overlayTieredHullRenderer("me_pattern_buffer")
                .tooltips(
                        Component.translatable("block.gtceu.pattern_buffer.desc.0"),
                        Component.translatable("shanhai.machine.super_wildcard_pattern_buffer.desc.0"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.3"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.5"),
                        Component.translatable("block.gtceu.pattern_buffer.desc.2"),
                        Component.translatable("gtceu.universal.enabled"))
                .register();
        LOGGER.info("[SHANHAI-WILDCARD] 已注册仓室：shanhai:{}（通配符样板槽 = {} 格；宿主 gtceu:me_wildcard_pattern_buffer = 1 格）",
                SUPER_WILDCARD_PATTERN_BUFFER_ID, SuperWildcardPatternBufferPartMachine.WILDCARD_SLOT_COUNT);
    }

    /** 注册后的定义句柄；未注册时为 {@code null}。 */
    public static MachineDefinition superWildcardPatternBuffer() {
        return superWildcardPatternBuffer;
    }
}
