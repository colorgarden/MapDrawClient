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
 * <p>可移植性：只注入 runTick，两个签名都写且 require=0；方法名在 1.20.6 ~ 26.2 稳定，
 * 签名不匹配也只是不注入，不会导致加载失败。只读不写，失败最多少画一帧画布。</p>
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameMixin {
	@Inject(method = "runTick()V", at = @At("TAIL"), require = 0)
	private void mapdrawclient$onFrameEnd(CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd();
	}

	@Inject(method = "renderFrame(Z)V", at = @At("TAIL"), require = 0)
	private void mapdrawclient(boolean renderLevel, CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd();
	}

	@Inject(method = "runTick(Z)V", at = @At("TAIL"), require = 0)
	private void mapdrawclient$onFrameEndLegacy(boolean renderLevel, CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd();
	}
}