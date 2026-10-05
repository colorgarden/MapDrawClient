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
 * <p><b>主要注入点</b>（照抄 xiushabei/Musangclient）：{@code Minecraft.renderFrame} 里
 * {@code RenderTarget.blitToScreen()} 之前 —— 此刻 GUI 已画完、画面还没送到屏幕，
 * 我们画的就在即将显示的内容上。</p>
 *
 * <p>其它注入点作为兜底（不同 MC 版本方法名/签名不同，{@code require = 0} 匹配不到只是不注入）。</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
	@Inject(method = "renderFrame(Z)V", at = @At(value = "INVOKE",
			target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V",
			shift = At.Shift.BEFORE), require = 0)
	private void mapdrawclient$beforeBlit(boolean tick, CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd();
	}

	@Inject(method = "renderFrame(Z)V", at = @At("HEAD"), require = 0)
	private void mapdrawclient$renderFrameHead(boolean tick, CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd();
	}

	@Inject(method = "runTick()V", at = @At("TAIL"), require = 0)
	private void mapdrawclient$onFrameEnd(CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd();
	}

	@Inject(method = "runTick(Z)V", at = @At("TAIL"), require = 0)
	private void mapdrawclient$onFrameEndLegacy(boolean renderLevel, CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd();
	}
}