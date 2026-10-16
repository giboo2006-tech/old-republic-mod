package com.oldrepublic.client.quest;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestParticles;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The Calling, seen and heard only by the called player: faint shimmers appear 4 to 7 blocks away in
 * the direction of the nearest crystal cave and drift toward it, and a soft tonal ping sounds,
 * louder the more directly the player faces the cave.
 *
 * <p>Chapter Two reuses it. Variant 2 (the two voices, toward the Crossing): pale warm glints and
 * cold drifting dust together, with a soft low two-tone murmur instead of the ping. Variant 3 (Smoke
 * on the Ridge): ash motes drifting toward the raider camp, silent (its smoke is the guide).
 */
public final class CallingFx {
	private static final int SHOW_TICKS = 55;
	private static final RandomSource RANDOM = RandomSource.create();

	private static Vec3 direction = Vec3.ZERO;
	private static float closeness;
	private static int remaining;
	private static boolean faint;
	private static int variant;

	static void handle(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (payload.variant() == QuestEffects.CALLING_NONE || payload.dir().lengthSqr() < 1.0e-4) {
			faint = true;
			remaining = 0;
			return;
		}

		faint = false;
		variant = payload.variant();
		direction = payload.dir().normalize();
		closeness = payload.intensity();
		remaining = SHOW_TICKS;

		if (client.player == null || variant == QuestEffects.CALLING_SMOKE) {
			return;
		}

		Vec3 look = client.player.getLookAngle().horizontal();
		double facing = look.lengthSqr() < 1.0e-4 ? 0 : Math.max(0, look.normalize().dot(direction.horizontal().normalize()));
		float volume = (float) (0.2 + 0.8 * facing * facing);

		if (variant == QuestEffects.CALLING_VOICES) {
			// Two low tones, a warm one and a colder one a fifth below.
			QuestSounds.ui(ModSounds.CRYSTAL_CALLING, 0.6f + 0.1f * closeness, volume * 0.6f);
			QuestSounds.ui(ModSounds.CRYSTAL_CALLING, 0.4f + 0.07f * closeness, volume * 0.5f);
		} else {
			QuestSounds.ui(ModSounds.CRYSTAL_CALLING, 0.9f + 0.2f * closeness, volume);
		}
	}

	static void tick(Minecraft mc) {
		if (remaining <= 0 || mc.player == null || mc.level == null) {
			return;
		}

		remaining--;

		if (remaining % 4 != 0) {
			return;
		}

		Vec3 eye = mc.player.getEyePosition();

		for (int i = 0; i < Vfx.count(2); i++) {
			float spread = (float) Math.toRadians((RANDOM.nextFloat() * 2f - 1f) * 15f);
			Vec3 dir = direction.yRot(spread);
			double distance = 4 + RANDOM.nextDouble() * 3;
			Vec3 pos = eye.add(dir.scale(distance)).add(0, RANDOM.nextDouble() * 2 - 1, 0);
			Vec3 vel = dir.scale(0.035 + 0.02 * closeness).add(0, 0.004, 0);
			mc.level.addParticle(particle(i), pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
		}
	}

	private static SimpleParticleType particle(int index) {
		return switch (variant) {
			case QuestEffects.CALLING_VOICES -> index % 2 == 0 ? QuestParticles.VOICE_WARM : QuestParticles.VOICE_DUST;
			case QuestEffects.CALLING_SMOKE -> QuestParticles.ASH_MOTE;
			default -> QuestParticles.RESONANCE_SHIMMER;
		};
	}

	/** Whether the last Calling ping found no cave in range (shown in the journal). */
	public static boolean isFaint() {
		return faint;
	}

	static void reset() {
		remaining = 0;
		faint = false;
		variant = 0;
	}

	private CallingFx() {
	}
}
