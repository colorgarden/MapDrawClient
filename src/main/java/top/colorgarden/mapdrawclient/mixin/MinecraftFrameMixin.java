package top.colorgarden.mapdrawclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

import top.colorgarden.mapdrawclient.ui.skia.SkiaFrameHook;

/**
 * 帧尾钩子：调用 {@link SkiaFrameHook}。
 *
 * <p>四个注入点并存（都 {@code require = 0}，命中哪个用哪个，日志里会打印来源）：</p>
 * <ul>
 *   <li>{@code blitToScreen} 之后（主）—— 假设 MC 是「blit 完再画 GUI」，我们必须画在 GUI 之后</li>
 *   <li>{@code blitToScreen} 之前 —— Musangclient 的位置（他们的 GUI 也是 Skija 画的，不需要担心被盖）</li>
 *   <li>{@code renderFrame} 尾 / {@code runTick} 尾 —— 兜底</li>
 * </ul>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
	@Inject(method = "renderFrame(Z)V", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V",
			shift = At.Shift.AFTER), require = 0)
	private void mapdrawclient$afterBlit(boolean tick, CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd("blitToScreen之后");
	}

	@Inject(method = "renderFrame(Z)V", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V",
			shift = At.Shift.BEFORE), require = 0)
	private void mapdrawclient$beforeBlit(boolean tick, CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd("blitToScreen之前");
	}

	@Inject(method = "renderFrame(Z)V", at = @At("TAIL"), require = 0)
	private void mapdrawclient$renderFrameTail(boolean tick, CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd("renderFrame尾");
	}

	@Inject(method = "runTick()V", at = @At("TAIL"), require = 0)
	private void mapdrawclient$onFrameEnd(CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd("runTick尾(无参)");
	}

	@Inject(method = "runTick(Z)V", at = @At("TAIL"), require = 0)
	private void mapdrawclient$onFrameEndLegacy(boolean renderLevel, CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd("runTick尾(布尔)");
	}
}