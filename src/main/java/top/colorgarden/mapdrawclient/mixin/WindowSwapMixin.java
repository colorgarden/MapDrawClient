package top.colorgarden.mapdrawclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import top.colorgarden.mapdrawclient.ui.skia.SkiaFrameHook;

/**
 * 在「缓冲交换之前」执行绘制（Skija 直画帧缓冲唯一可靠的位置）。
 *
 * <p>Musangclient 用 MinHook 挂 wglSwapBuffers；这里等价地注入
 * {@code com.mojang.blaze3d.platform.Window.swapBuffers()} 的 HEAD。</p>
 *
 * <p>可移植性：该方法在 1.20.6 ~ 26.2 同名；{@code require = 0}，匹配不到只是不注入。</p>
 */
@Mixin(com.mojang.blaze3d.platform.Window.class)
public abstract class WindowSwapMixin {
	@Inject(method = "swapBuffers()V", at = @At("HEAD"), require = 0)
	private void mapdrawclient$beforeSwap(CallbackInfo ci) {
		SkiaFrameHook.onFrameEnd("swapBuffers之前");
	}
}