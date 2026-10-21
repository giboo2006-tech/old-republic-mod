package com.oldrepublic.client.force;

import java.util.Map;

import com.oldrepublic.client.ability.ClientAbilityInfo;
import com.oldrepublic.client.force.energy.DangerPrickle;
import com.oldrepublic.client.hud.DangerEdgeHud;
import com.oldrepublic.force.sense.ForceSense;
import com.oldrepublic.force.sense.SenseBalance;
import com.oldrepublic.force.sense.SenseRules;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.Skills;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Danger Sense ({@code sense_danger}, spec 10.1), entirely on the client: a listener on every effect
 * event. When the local player owns the node and an enemy attack tell ({@link DangerTells#isTell})
 * starts within 12 blocks outside the 100° front cone, it plays the directional prickle
 * {@code force.sense.danger} from the tell's side and shows it at that edge of the view: a dust tick
 * ({@link DangerEdgeHud}) with a pale prickling light and grit streaming in ({@link DangerPrickle}),
 * at most once per 20 ticks. Range, cone and repeat come from the server's {@code force_sense} info,
 * falling back to the balance defaults.
 */
final class DangerSense {
	/** The prickle sounds this far from the head, in the tell's direction. */
	private static final double SOUND_OFFSET = 3;
	private static final SenseBalance DEFAULTS = new SenseBalance();

	private static long lastPrickle = Long.MIN_VALUE;

	/** Every received effect event (registered with {@code ClientEffects.addListener}). */
	static void onEvent(EffectEventPayload payload) {
		if (!DangerTells.isTell(payload.effect(), payload.variant())) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;

		if (player == null || mc.level == null || !player.isAlive() || payload.entityId() == player.getId()
				|| !Skills.active(player, SkillIds.SENSE_DANGER)) {
			return;
		}

		Map<String, Float> info = ClientAbilityInfo.values(ForceSense.ID);
		double range = info.getOrDefault("danger_range", (float) DEFAULTS.dangerRange);
		float cone = info.getOrDefault("danger_cone", (float) DEFAULTS.dangerConeDegrees);
		int repeat = Math.round(info.getOrDefault("danger_repeat_s", DEFAULTS.dangerRepeatTicks / 20f) * 20f);
		long now = PowerAnimations.ticks();
		Vec3 eye = player.getEyePosition();
		Vec3 toTell = payload.pos().subtract(eye);

		// Before the first prickle there is nothing to wait for (now - MIN_VALUE would overflow to a negative gap).
		boolean waiting = lastPrickle != Long.MIN_VALUE && now - lastPrickle < repeat;

		if (waiting || toTell.lengthSqr() > range * range || SenseRules.inDangerCone(player.getViewVector(1f), toTell, cone)) {
			return;
		}

		lastPrickle = now;
		Vec3 flat = ForceFxUtil.horizontal(toTell);
		ForceFxUtil.play(mc.level, eye.add(flat.scale(SOUND_OFFSET)), ModSounds.FORCE_SENSE_DANGER, 0.8f, ForceFxUtil.pitchVariance(0.03f));
		// Yaw 0 faces +z and grows clockwise seen from above, so a positive difference is to the right.
		float tellYaw = (float) Math.toDegrees(Math.atan2(-flat.x, flat.z));
		float relative = Mth.wrapDegrees(tellYaw - player.getYRot());
		DangerEdgeHud.prickle(relative);
		DangerPrickle.prickle(relative);
	}

	static void clear() {
		lastPrickle = Long.MIN_VALUE;
		DangerPrickle.reset();
	}

	private DangerSense() {
	}
}
