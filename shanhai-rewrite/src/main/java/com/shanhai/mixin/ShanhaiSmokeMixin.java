package com.shanhai.mixin;

import com.shanhai.ShanhaiMod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 山海重构 · mixin 基建的【冒烟 mixin】。
 *
 * <h2>它只做一件事</h2>
 * 在 {@link ShanhaiMod} 的构造器返回处打一行日志。不改任何行为、不注入任何第三方类。
 *
 * <h2>为什么摘「我们自己的类」</h2>
 * <ul>
 *   <li><b>优先级最高</b>：目标 {@code com.shanhai.ShanhaiMod} 是本 mod 自己的类，<b>不经任何混淆映射</b>
 *       ⇒ {@code remap = false} 成立，<b>不需要 refmap</b>。</li>
 *   <li><b>冒烟必须在 common 侧</b>：这个 mixin 列在 {@code shanhai.mixin.json} 的 {@code mixins} 数组里
 *       （<b>不是</b> {@code client} 数组）。{@code client} 数组的 mixin 专用服务端不会加载，
 *       那样专服冒烟就永远看不到这行日志、也就证不了任何事。</li>
 *   <li><b>必须无害</b>：注入点 {@code @At("RETURN")} <b>不取消、不修改</b>返回值（{@code <init>} 返回 void），
 *       不 {@code @Overwrite}、不 {@code @Redirect}、不 {@code @Shadow}。</li>
 * </ul>
 *
 * <h2>为什么日志里写死字符串而不是取常量</h2>
 * {@code "[SHANHAI-SMOKE] mixin loaded"} 这个字面量在<b>整个工程里只出现在本文件</b>，
 * 因此专服日志里出现它，就唯一地证明「mixin 配置被加载 + 本 mixin 被应用 + 注入点真的执行了」，
 * 而不是「mod 加载了」。这是本次冒烟唯一的判据。
 */
@Mixin(value = ShanhaiMod.class, remap = false)
public class ShanhaiSmokeMixin {

    /**
     * 在 {@code ShanhaiMod.<init>()} 的 RETURN 处打印冒烟标记。
     *
     * <p>{@code require = 1} 是刻意的（默认值也是 1）：注入点找不到目标时<b>让加载期直接失败</b>，
     * 而不是静默放过 —— 冒烟的目的就是「要炸就炸得响」。
     */
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void shanhai$smokeMixinLoaded(CallbackInfo ci) {
        ShanhaiMod.LOGGER.info("[SHANHAI-SMOKE] mixin loaded");
    }
}
