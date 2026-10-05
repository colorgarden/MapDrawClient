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
 * <p>主注入点（照抄 xiushabei/Musangclient）：{@code Minecraft.renderFrame} 里
 * {@code RenderTarget.blitToScreen()} 之前。其它注入点作为兜底，{@code require = 0}。</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
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