package com.shanhai.mixin;

import com.shanhai.client.text.ShanhaiFontStyleRenderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 山海重构 · <b>客户端 {@code Font} 混入</b>：让 {@code &$…-} 文本按「随时间流动的色板渐变」渲染。
 *
 * <h2>0. 出处与定位</h2>
 * 对照实现：{@code originals/upstream\...\mixin\WobbleFontMixin.java}（646 行）。
 * 本类只保留它的「注入骨架」，渲染逻辑搬到
 * {@link ShanhaiFontStyleRenderer}（普通类）—— <b>理由见该类注释 §1</b>。
 *
 * <h2>1. 🔴 为什么是「无 {@code value}/无 {@code remap} 参数」的 {@code @Mixin(Font.class)}</h2>
 * <b>上游写的是</b>：
 * <pre>
 * &#64;Mixin(value = Font.class, remap = false)
 * &#64;Inject(method = "m_272191_", at = &#64;At("HEAD"), remap = false, require = 0, cancellable = true)
 * </pre>
 * 而本工程 {@code gradle.properties} 是 <b>parchment 2023.09.03</b>
 * （与上游同一套 mappings，已核实一致）⇒ <b>开发期</b> {@code Font} 上的方法名是
 * {@code drawInBatch}，<b>运行期（生产 jar）</b>才是 {@code m_272191_}。
 * 照抄上游 ⇒ 开发环境里那个名字匹配不上任何方法，{@code require = 0} 又让它<b>静默跳过</b>
 * ⇒ <b>「开发期永远看不到效果、只在生产 jar 生效」</b>，调试代价极高。
 * <p>
 * ⇒ <b>本工程的做法</b>（= 方案 md §5.1 的结论）：
 * <pre>
 * &#64;Mixin(Font.class)                       // remap 用默认值 true
 * &#64;Inject(method = "drawInBatch(&lt;开发描述符&gt;)I", require = 1)
 * </pre>
 * 由 mixin 注解处理器生成 refmap，把<b>开发名</b>在运行期映射成 SRG 名。
 * <p>
 * <b>双向 {@code javap} 实测</b>（本轮复跑，见任务报告 ②）：
 * <pre>
 * forge-1.20.1-47.4.16-srg.jar                     …_mapped_parchment_2023.09.03-1.20.1.jar
 *   int m_272191_(FormattedCharSequence, float,float,int,boolean,Matrix4f,MultiBufferSource,Font$DisplayMode,int,int)
 *                                                  int drawInBatch(FormattedCharSequence, …)   ← 同一个方法
 *   int m_272077_(Component, …)                    int drawInBatch(Component, …)
 *   int m_92724_(FormattedCharSequence)            int width(FormattedCharSequence)
 *   int m_92852_(FormattedText)                    int width(FormattedText)
 *   int m_92895_(String)                           int width(String)
 * </pre>
 * 🔴 <b>描述符必须逐字符正确</b>：本类第一版把 {@code (Lnet/minecraft/util/FormattedCharSequence;FFIZ…)}
 * 里的 <b>{@code Z}（{@code boolean shadow}）漏了</b>，注解处理器当场报
 * <pre>
 * Cannot find target method "drawInBatch(…FFI…)" for @Inject.method=… in net.minecraft.client.gui.Font
 * Unable to locate obfuscation mapping for @Inject target drawInBatch
 * </pre>
 * 这不是「处理器不灵」，是<b>我自己抄错了一个字符</b>。⇒ 描述符的权威来源只有
 * {@code javap -p -cp &lt;parchment jar&gt; net.minecraft.client.gui.Font} 的原文。
 * <p>
 * ⚠️ 另一个实测坑：ForgeGradle 的 TSRG 是 <b>{@code srg → 开发名}</b> 方向
 * （第一列为 {@code m_272191_}），而处理器要的是<b>反方向</b>。
 * 原样喂进去会打出 {@code Unable to locate obfuscation mapping for @Inject target width}——
 * 见 {@code build.gradle} 的 {@code mixinReverseTsrg} 任务（列对调后再喂）。
 *
 * <h2>2. 为什么只注入 {@code drawInBatch(FormattedCharSequence,…)} 一个重载</h2>
 * 因为 <b>{@code Component} 那个重载会转调它</b>，这是字节码级的实证，不是推断
 * （{@code javap -c net.minecraft.client.gui.Font}，{@code drawInBatch(Component,…)} 的方法体只有一条
 * {@code invokeinterface Component.getVisualOrderText()} + 一条
 * {@code invokevirtual drawInBatch:(Lnet/minecraft/util/FormattedCharSequence;…)I}）：
 * <pre>
 * 0: aload_0 / aload_1
 * 2: invokeinterface Component.getVisualOrderText:()Lnet/minecraft/util/FormattedCharSequence;
 *   … 参数原样透传 …
 * 23: invokevirtual drawInBatch:(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;…)I
 * </pre>
 * ⇒ 加上 {@code Component} 那份注入<b>不会多覆盖任何东西</b>，只会让 refmap 多一条、
 * 让「注入点找得到/找不到」这件事多一个失败面。
 * <p>
 * ⚠️ <b>但 {@code Font.drawInBatch(String,…)} 是另一条独立路径</b>
 * （它转调 {@code drawInBatch(String,…,boolean)}，<b>不经过</b> FCS 重载）⇒ 用
 * {@code GuiGraphics.drawString(font, "裸字符串", …)} 画出来的 {@code &$…-} <b>不会</b>有特效。
 * 本工程所有 {@code &$…-} 文本都是 {@code Component}（lang 条目 / {@code Component.literal}），
 * 所以这条路不需要 —— 如实记在这里，不当成「没做到」。
 *
 * <h2>3. 🔴 {@code require = 1}（故意的）</h2>
 * 注入点找不到时<b>启动就崩</b>，而不是静默放过。
 * 对应地，{@link ShanhaiFontStyleRenderer} 里所有算色/绘制逻辑都包在 {@code try/catch} 里
 * ⇒ 两条一起构成：<b>注入失败 = 响亮地崩；渲染算错 = 安静地不生效</b>。
 *
 * <h2>4. 🔴 那行日志</h2>
 * 本类不自己打日志，只调 {@link ShanhaiFontStyleRenderer} 的入口 ——
 * 那一行 {@code [SHANHAI-SPEC] font_style_mixin hooked …} 在<b>任何一个注入点第一次被调用</b>时打出（CAS 保证只打一次）。
 * ⇒ 它是「注入成功」与「算错了」的唯一分界线（见 {@code ShanhaiFontStyleRenderer#logHookedOnce} 的注释）。
 * <p>
 * ⚠️ <b>无头专用服务端不会加载本混入</b>（它在 {@code shanhai.mixin.json} 的 {@code client} 数组里）
 * ⇒ 那一行<b>只会在客户端日志里出现</b>，专服冒烟证明不了它。
 *
 * <h2>5. 为什么三个 {@code width} 重载都要注入</h2>
 * {@code javap -c} 实测：{@code width(String)} / {@code width(FormattedText)} /
 * {@code width(FormattedCharSequence)} <b>各自独立转调 {@code StringSplitter.stringWidth(…)}</b>，
 * <b>互不经过对方</b> ⇒ 少注一个，就有一类调用方（例如 {@code Font.width(Component)} 走
 * {@code FormattedText} 那条）算到「胖宽度」，居中的文字会偏。
 */
@Mixin(Font.class)
public class ShanhaiFontStyleMixin {

    /**
     * 接管 {@code Font.drawInBatch(FormattedCharSequence, …)} —— <b>所有文字渲染的唯一收口</b>。
     *
     * <p>覆盖范围（因此「横幅」与「35 条带前缀的物品名」会<b>一起</b>活过来）：
     * 聊天栏 / 物品名 / tooltip / JEI / {@code Component} 那一路全都会落到这里。
     *
     * <p>{@code at = HEAD} + {@code cancellable} ⇒ 我们先整行画完，然后取消原版渲染
     * （照上游 {@code WobbleFontMixin.java:637-638}）。
     */
    @Inject(
            method = "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void shanhai$styleDrawInBatch(FormattedCharSequence text, float x, float y, int color,
                                          boolean shadow, Matrix4f matrix, MultiBufferSource buffer,
                                          Font.DisplayMode mode, int packedLight, int packedOverlay,
                                          CallbackInfoReturnable<Integer> cir) {
        Integer handled = ShanhaiFontStyleRenderer.renderFcs((Font) (Object) this, text, x, y, color,
                shadow, matrix, buffer, mode, packedLight, packedOverlay);
        if (handled != null) {
            cir.setReturnValue(handled);
            cir.cancel();
        }
    }

    /** 宽度修正 · {@code width(FormattedCharSequence)}。 */
    @Inject(
            method = "width(Lnet/minecraft/util/FormattedCharSequence;)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void shanhai$styleWidthFcs(FormattedCharSequence text, CallbackInfoReturnable<Integer> cir) {
        Integer handled = ShanhaiFontStyleRenderer.widthFcs((Font) (Object) this, text);
        if (handled != null) {
            cir.setReturnValue(handled);
            cir.cancel();
        }
    }

    /** 宽度修正 · {@code width(FormattedText)} —— {@code Font.width(Component)} 走的就是这一条。 */
    @Inject(
            method = "width(Lnet/minecraft/network/chat/FormattedText;)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void shanhai$styleWidthFormattedText(FormattedText text, CallbackInfoReturnable<Integer> cir) {
        Integer handled = ShanhaiFontStyleRenderer.widthFormattedText((Font) (Object) this, text);
        if (handled != null) {
            cir.setReturnValue(handled);
            cir.cancel();
        }
    }

    /** 宽度修正 · {@code width(String)}。 */
    @Inject(
            method = "width(Ljava/lang/String;)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void shanhai$styleWidthString(String text, CallbackInfoReturnable<Integer> cir) {
        Integer handled = ShanhaiFontStyleRenderer.widthString((Font) (Object) this, text);
        if (handled != null) {
            cir.setReturnValue(handled);
            cir.cancel();
        }
    }
}
