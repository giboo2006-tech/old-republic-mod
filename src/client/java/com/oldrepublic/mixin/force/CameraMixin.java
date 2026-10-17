package com.oldrepublic.mixin.force;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.oldrepublic.client.fx.CameraShake;

import net.minecraft.client.Camera;

/**
 * Applies the shared {@link CameraShake} to the camera rotation for every feature. Injected right
 * after the camera is aligned with its entity, before the view matrix and culling frustum are
 * built from the rotation.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Shadow
	public abstract float yRot();

	@Shadow
	public abstract float xRot();

	@Inject(method = "alignWithEntity(F)V", at = @At("TAIL"))
	private void oldrepublic$applyShake(float partialTick, CallbackInfo ci) {
		float yaw = CameraShake.yaw(partialTick);
		float pitch = CameraShake.pitch(partialTick);

		if (yaw != 0f || pitch != 0f) {
			setRotation(yRot() + yaw, xRot() + pitch);
		}
	}
}
