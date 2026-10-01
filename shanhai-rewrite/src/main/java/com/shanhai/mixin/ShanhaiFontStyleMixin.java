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
 * <h2>2. 🔴 注入点 = 两条「漏斗」，不是每个重载都注（2026-10-01 重写）</h2>
 * <b>上一版这一节只讲了 FCS 一条家族，并且写下了「String 那条路本工程不需要」的结论</b>——
 * 那句话被实机证伪（用户图1：{@code &$ultimateRainbow-模块要求：…} 原样画在配方页上）。
 * 现按 {@code javap -c} 的全量调用图重写（原文见 {@code handoff\outbound\模块要求-露码修正.md} §11）。
 *
 * <p><b>{@code Font} 的文字绘制是两个互不相干的家族</b>（同一份字节码在开发 jar 与 SRG 运行时 jar 里都核过）：
 * <pre>
 *   【Component 家族】 drawInBatch(Component,…)          :23 → drawInBatch(FormattedCharSequence,…) :18
 *                                                             ↑ m_272191_（本类注入点①）
 *   【String 家族】    drawInBatch(String,…II)I          :22 → drawInBatch(String,…IIZ)I         :20
 *                        ↑ m_271703_（10 参，只转调）              ↑ m_272078_（本类注入点②）
 *   ⇒ 两个家族的 {@code drawInternal} 都是 private，外人进不来：
 *     drawInternal(String,…)=m_271880_ / drawInternal(FormattedCharSequence,…)=m_272085_
 * </pre>
 * ⇒ <b>每个家族只需要注「漏斗」那一个</b>，多注一个是白多一个失败面：
 * <ul>
 *   <li>① {@link #shanhai$styleDrawInBatch}（FCS）= 所有 {@code Component} 文本的收口；</li>
 *   <li>② {@link #shanhai$styleDrawInBatchString}（String <b>11 参</b>那个）= 所有 String 文本的收口。</li>
 * </ul>
 * 🔴 <b>为什么必须是 11 参那个、而不是 10 参那个</b>（这是本轮最容易踩空的一步）：
 * <pre>
 *   · 10 参 m_271703_ 的方法体只有一条转调：:22 invokevirtual m_272078_() ⇒ 注 11 参照样罩住 10 参的调用者；
 *   · 而 LDLib 实际走的那条路【不是】10 参：
 *       LabelWidget.drawInBackground :108  invokevirtual GuiGraphics.m_280056_:(Font;String;IIIZ)I
 *       m_280056_                    :12   invokevirtual drawString:(Font;String;FFIZ)I
 *       drawString(Font,String,float,float,int,boolean)
 *                                    :40   invokevirtual Font.m_272078_:(String;…IIZ)I   ← 🔴 直调 11 参
 *   ⇒ 只注 10 参 = <b>那条要修的路径一条都罩不住</b>（「以为接管了、其实没有」的第二种形态）。
 * </pre>
 *
 * <h2>2.5 🔴 这个注入点罩得住 / 罩不住什么（照实测写，别默认）</h2>
 * <b>罩得住</b>（全部经字节码链核实）：
 * <ol>
 *   <li>{@code GuiGraphics.drawString(Font, String, float,float,int,boolean)} 及其 int 坐标重载
 *       {@code m_280056_} —— LDLib {@code LabelWidget} 的 String 分支走这条；</li>
 *   <li>{@code Font.drawInBatch(String, 10 参)} 的直接调用者 —— 实测本 classpath 里
 *       AE2 / FTB-Library / KubeJS / Polylib / SophisticatedCore|Storage 都在直接调 {@code m_271703_}；</li>
 *   <li>直接调 11 参 {@code m_272078_} 的调用者（实测：Polylib {@code GuiRender}）；</li>
 *   <li>（既有注入点）所有 {@code Component} 文本：聊天 / 物品名 / tooltip / JEI，
 *       以及 LDLib {@code LabelWidget} 的 <b>Component 分支</b>（{@code m_280614_} → {@code m_280649_} → FCS 漏斗）。</li>
 * </ol>
 * 🔴 <b>罩不住（已知第三条路，<u>本轮不动</u>）</b>：{@code Font.drawInBatch8xOutline(FormattedCharSequence,…)}
 * = {@code m_168645_} —— 它<b>自己 new 一个 {@code Font$StringRenderOutput}</b> 逐偏移画描边，
 * <b>不经过任何一个 {@code drawInBatch}</b>（反向扫描 {@code javap -c} 全类无命中）。
 * 实测调用者只有两个：原版 {@code SignRenderer}（告示牌文字）与 Jade
 * {@code impl/ui/ProgressStyle}（{@code glowText} 打开时把入参 Component 描边画出来）。
 * ⇒ 这是一条<b>改前就存在</b>的缺口，与本次改动无关；本轮如实记下，不在这里修（要修得再补一个注入点）。
 *
 * <h2>3. 🔴 两个注入点都用 {@code require = 1}（故意的）</h2>
 * 注入点找不到时<b>启动就崩</b>，而不是静默放过。用户 2026-10-01 拍板的原话是
 * 「启动就崩其实是最好修的，要是莫名其妙崩了才难修」——
 * 因为「崩」有栈可查、一条命令就能回滚，「静默失效」则<b>分不清是没注入还是算错了</b>。
 * <p>它<b>不是</b>赌运气：两条风险各自可以离线判死（详见本类对应的交接文档 §11.2）：
 * <ol>
 *   <li><b>匹配数</b>：描述符在目标类里的匹配数 == 1（{@code javap -p -s} 全表精确计数，
 *       开发 jar 与 SRG jar 各一遍）⇒ Mixin 的匹配是静态的 ⇒ 不会「找不到」；</li>
 *   <li><b>refmap</b>：反查喂给注解处理器的 TSRG，确认该 dev 描述符<b>有 SRG 映射</b>
 *       （{@code drawInBatch(String,…,IIZ)} → {@code m_272078_}）⇒ 生产 jar 里名字也翻得对。</li>
 * </ol>
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
 * <p>⚠️ 那行日志的 {@code methods=…} 字段<b>逐字对应本类所有 {@code @Inject}</b>
 * （{@link ShanhaiFontStyleRenderer#HOOK_METHODS}）⇒ 加了注入点就必须同步改它，否则日志会撒谎。
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

    /**
     * 接管 {@code Font.drawInBatch(String, …, boolean)}（<b>11 参那个</b>）——
     * <b>所有「裸字符串」文字渲染的收口</b>（= 第二条家族）。
     *
     * <p>🔴 <b>为什么是 11 参而不是 10 参</b>：见类注释 §2 —— 10 参只转调它，
     * 而 {@code GuiGraphics.drawString(Font, String, float, float, int, boolean)} 是<b>直调 11 参</b>的
     * （SRG 运行时原文 {@code :40 invokevirtual Font.m_272078_}），LDLib {@code LabelWidget} 正走这条。
     *
     * <p>描述符逐字符出处（{@code javap -p -s} 原文，开发 jar 与 SRG jar 各一份）：
     * <pre>
     *   descriptor: (Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I
     *   末位那个 Z = bidirectional（10 参那个转发时传 false ⇒ 本注入点统一收口）
     *   SRG 名 = m_272078_（反查 srg_to_parchment_2023.09.03-1.20.1.tsrg 得到，已核）
     * </pre>
     *
     * <p>{@code at = HEAD} + {@code cancellable} ⇒ 我们先整行画完，然后取消原版渲染
     * （与 FCS 那条同一个做法，照上游 {@code WobbleFontMixin.java:637-638}）。
     * <p>⚠️ 我们自己发起的 String 绘制（{@code drawDegraded} 的退化成字、{@code drawStyled} 的 prefix）
     * 会再次进到这里 —— 由 {@code ShanhaiFontStyleRenderer} 的重入标记立刻放行（那两处必须由原版画，
     * 因为 {@code §} 只在 String 路径上被原版解析）。
     */
    @Inject(
            method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void shanhai$styleDrawInBatchString(String text, float x, float y, int color,
                                                boolean shadow, Matrix4f matrix, MultiBufferSource buffer,
                                                Font.DisplayMode mode, int packedLight, int packedOverlay,
                                                boolean bidirectional,
                                                CallbackInfoReturnable<Integer> cir) {
        Integer handled = ShanhaiFontStyleRenderer.renderString((Font) (Object) this, text, x, y, color,
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
