package com.oldrepublic.client.boss.kaast;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.kaast.Rattle;
import com.oldrepublic.client.enemy.droid.GlowShapes;
import com.oldrepublic.client.enemy.droid.SeekerRenderer;
import com.oldrepublic.client.fx.Vfx;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Renders Rattle ({@code kaast_droid}, spec MILESTONE_4.md 10.5, 10.7): the seeker's model in
 * Kaast's paint ({@code textures/entity/kaast/kaast_droid.png}), and while it scans a player its
 * scan line: a thin amber beam from the lens to the player's chest, brightest at the lens, and a
 * small point of light where it lands. Amber, so the rocket's red dot stays the only red light on
 * the field. The line wavers with flashing effects on and is steady with them off.
 */
public class RattleRenderer extends SeekerRenderer {
	private static final Identifier TEXTURE = OldRepublicMod.id("textures/entity/kaast/kaast_droid.png");
	private static final int BEAM_NEAR = 0xC0FFC060;
	private static final int BEAM_FAR = 0x40FFB040;
	private static final int SPOT = 0xB0FFD890;

	public RattleRenderer(EntityRendererProvider.Context context) {
		super(context, TEXTURE);
	}

	@Override
	public void extractRenderState(Mob mob, State state, float partialTick) {
		super.extractRenderState(mob, state, partialTick);
		state.scanTo = null;
		state.flashing = Vfx.flashing();

		if (!(mob instanceof Rattle rattle) || rattle.scanTarget() < 0) {
			return;
		}

		Entity target = rattle.level().getEntity(rattle.scanTarget());

		if (target == null) {
			return;
		}

		Vec3 origin = rattle.getPosition(partialTick);
		state.scanFrom = new Vec3(0, rattle.getBbHeight() * 0.5, 0);
		state.scanTo = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.55, 0).subtract(origin);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		Vec3 to = state.scanTo;

		if (to == null || state.isInvisible) {
			return;
		}

		Vec3 from = state.scanFrom;
		float waver = state.flashing ? 0.85f + 0.15f * Mth.sin(state.ageInTicks * 0.9f) : 1f;
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(),
				(pose, buffer) -> GlowShapes.beam(pose, buffer, from, to, 0.012f, GlowShapes.fade(BEAM_NEAR, waver), GlowShapes.fade(BEAM_FAR, waver)));
		// The geometry is drawn later from the pose captured here, so the spot gets its own translated pose.
		poseStack.pushPose();
		poseStack.translate(to.x, to.y, to.z);
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> GlowShapes.flare(pose, buffer, 0.05f, GlowShapes.fade(SPOT, waver)));
		poseStack.popPose();
	}
}
