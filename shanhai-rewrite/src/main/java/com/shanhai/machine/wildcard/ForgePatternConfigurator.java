package com.shanhai.machine.wildcard;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * 「神锻样板模式」按钮（需求④）—— 每个仓室右下左侧竖列里的那一个。
 *
 * <h2>一、它是上游哪一个的对应物（逐字对齐，不转述）</h2>
 * 上游 = {@code com.gtladd.gtladditions.api.machine.gui.FOAPatternConfigurator}
 * （源码见 {@code temp\wildcard\decomp6\...\FOAPatternConfigurator.kt}）。
 * 本类与它的<b>控件布局、控件类型、位置尺寸、lang key 全部逐字相同</b>：
 * <pre>
 *   WidgetGroup(0, 0, 118, 56)
 *   ├─ ToggleButtonWidget(6, 5, 20, 20, GuiTextures.BUTTON_POWER, isEnabled, setEnabled)
 *   │     .setTooltipText("gtladditions.machine.me_super_pattern_buffer.foa_mode")
 *   ├─ LabelWidget(32, 10, "gtladditions.machine.me_super_pattern_buffer.foa_mode")
 *   ├─ LabelWidget(6,  36, "gtladditions.machine.me_super_pattern_buffer.foa_multiplier")
 *   └─ IntInputWidget(58, 31, 54, 20, getMultiplier, setMultiplier).setMin(1).setMax(30)
 *   标题 getTitle()      = "gtladditions.machine.me_super_pattern_buffer.foa_config.title"     → 「神锻样板模式」
 *   提示 getTooltips()   = 标题 + "…foa_config.tooltip.0" + "…foa_config.tooltip.1"
 *                          → 与用户截图那两行**同一批 key**，所以文字逐字一致
 * </pre>
 *
 * <h2>二、🔴 为什么不能直接 {@code new FOAPatternConfigurator(this)}</h2>
 * 它的构造器签名是 {@code FOAPatternConfigurator(MESuperPatternBufferPartMachine)}
 * —— <b>只收 gtladditions 那一种机器</b>（javap 实测：
 * {@code public com.gtladd.gtladditions.api.machine.gui.FOAPatternConfigurator(com.gtladd.gtladditions.common.machine.multiblock.part.MESuperPatternBufferPartMachine)}）。
 * 本机继承的是 gtlcore 的 {@code MEPatternBufferPartMachineBase}，与它没有继承关系 ⇒ 编译期就传不进去 ✗。
 * <b>而它的实现里除了「读/写那台机器的两个字段 + 画上面那 4 个控件」之外没有任何别的东西</b>
 * （全文 84 行，见上面那个文件）⇒ 照抄 4 个控件 + 把读写接成本机的字段，就是**等价复用**。
 *
 * <h2>三、位置</h2>
 * GTCEu 的 {@code ConfiguratorPanel} 把挂上去的 configurator <b>按挂载顺序自上而下排在界面左侧</b>。
 * 上游在 {@code MESuperPatternBufferPartMachine.attachConfigurators} 里把 FOA
 * <b>放在最后一个</b> {@code attachConfigurators(...)} 调用 ⇒ 它落在整列的最下面 = 截图里的左下角。
 * 本机 {@code attachConfigurators} 同样把它放最后 ⇒ 同一个位置 ✓。
 *
 * <h2>四、功能（不许「点了没反应」）</h2>
 * 开关与乘数都写回机器，并且 {@code SuperWildcardPatternBufferPartMachine} 会<b>立刻重算样板</b>
 * （见该类的 {@code rebuildPatterns()}）：把已展开的通配符样板按神锻规则改写输入输出。
 * 改写本体在 {@link ShanhaiForgePatternMode}（反射复用 gtladditions 的同一份实现）。
 * 万一那份实现取不到，{@link #getTooltips()} 会<b>多出一行显式的「不可用」</b>，并写 ERROR 日志 ——
 * 那种情况下按钮依旧是活的（能切），但玩家能一眼看出它没生效。
 */
public class ForgePatternConfigurator implements IFancyConfigurator {

    /** 与上游完全相同的 lang key 前缀（复用它的文案，不另起一套）。 */
    private static final String LANG_TITLE = "gtladditions.machine.me_super_pattern_buffer.foa_config.title";
    private static final String LANG_TOOLTIP_0 = "gtladditions.machine.me_super_pattern_buffer.foa_config.tooltip.0";
    private static final String LANG_TOOLTIP_1 = "gtladditions.machine.me_super_pattern_buffer.foa_config.tooltip.1";
    private static final String LANG_MODE = "gtladditions.machine.me_super_pattern_buffer.foa_mode";
    private static final String LANG_MULTIPLIER = "gtladditions.machine.me_super_pattern_buffer.foa_multiplier";

    /** 上游图标 = 神锻机器本体（{@code MultiBlockMachine.INSTANCE.getFORGE_OF_THE_ANTICHRIST().asStack()}）的物品 id。 */
    private static final ResourceLocation FOA_ITEM_ID = new ResourceLocation("gtladditions", "forge_of_the_antichrist");

    /** 失败提示（本工程自加，上游没有）—— 只在反射复用失败时出现。 */
    private static final String LANG_UNAVAILABLE = "shanhai.machine.super_wildcard_pattern_buffer.foa_unavailable";

    private final SuperWildcardPatternBufferPartMachine machine;

    public ForgePatternConfigurator(SuperWildcardPatternBufferPartMachine machine) {
        this.machine = machine;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(LANG_TITLE);
    }

    @Override
    public IGuiTexture getIcon() {
        final ItemStack icon = foaIconStack();
        // 拿不到就退回通用的电源按钮贴图 —— 图标缺失不该把整个 configurator 弄崩。
        return icon.isEmpty() ? GuiTextures.BUTTON_POWER : new ItemStackTexture(icon);
    }

    private static ItemStack foaIconStack() {
        final var item = ForgeRegistries.ITEMS.getValue(FOA_ITEM_ID);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public List<Component> getTooltips() {
        final List<Component> tooltips = new ArrayList<>();
        tooltips.add(this.getTitle());
        tooltips.add(Component.translatable(LANG_TOOLTIP_0));
        tooltips.add(Component.translatable(LANG_TOOLTIP_1));
        if (!ShanhaiForgePatternMode.isAvailable()) {
            tooltips.add(Component.translatable(LANG_UNAVAILABLE));
        }
        return tooltips;
    }

    @Override
    public Widget createConfigurator() {
        final WidgetGroup group = new WidgetGroup(0, 0, 118, 56);
        group.addWidget(new ToggleButtonWidget(
                6, 5, 20, 20,
                GuiTextures.BUTTON_POWER,
                this.machine::isForgePatternModeEnabled,
                this.machine::setForgePatternModeEnabled)
                .setTooltipText(LANG_MODE));
        group.addWidget(new LabelWidget(32, 10, LANG_MODE));
        group.addWidget(new LabelWidget(6, 36, LANG_MULTIPLIER));
        final IntInputWidget multiplier = new IntInputWidget(
                58, 31, 54, 20,
                this.machine::getForgePatternMultiplier,
                this.machine::setForgePatternMultiplier);
        multiplier.setMin(ShanhaiForgePatternMode.MIN_MULTIPLIER);
        multiplier.setMax(ShanhaiForgePatternMode.MAX_MULTIPLIER);
        group.addWidget(multiplier);
        return group;
    }
}
