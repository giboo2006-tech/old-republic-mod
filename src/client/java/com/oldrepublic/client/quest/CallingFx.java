package com.oldrepublic.client.quest;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestParticles;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
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
 *
 * <p>Chapter Three's variants come through {@code Guidance} (spec 13.3); the event's entity id
 * carries the variant's data, never an entity:
 * <ul>
 * <li>4 HOLOCRON (data = holocron kind ordinal): pale motes along the direction tinted warm gold
 * (Jedi), rust (Sith) or brass (Wayfarer), with the {@code story.calling_holocron} swell.</li>
 * <li>5 CONTRACT: a thin drift of ash-grey motes at eye height and the Calling chime pitched down.</li>
 * <li>6 RELIQUARY: deep amber glints, more of them the closer the player is, and the
 * {@code story.calling_reliquary} hum, louder with closeness.</li>
 * <li>7 OUTPOST (data = the army's {@code Faction} ordinal): faint blue-white motes for the Republic,
 * crimson for the Empire, and the Calling chime.</li>
 * <li>8 FALL: ember streaks slanting down toward Courier's Fall and the {@code story.calling_fall}
 * crackle.</li>
 * </ul>
 */
public final class CallingFx {
	private static final int SHOW_TICKS = 55;
	private static final RandomSource RANDOM = RandomSource.create();

	private static Vec3 direction = Vec3.ZERO;
	private static float closeness;
	private static int remaining;
	private static boolean faint;
	private static int variant;
	/** The variant's data (holocron kind or army ordinal), from the event's entity id. */
	private static int data;

	private static final int JEDI_GOLD = 0xF0D48A;
	private static final int SITH_RUST = 0xB4502E;
	private static final int WAYFARER_BRASS = 0xC8A456;
	private static final int RELIQUARY_AMBER = 0xD9861A;
	private static final int REPUBLIC_WHITE = 0xD4E4FF;
	private static final int EMPIRE_CRIMSON = 0xB01E2C;
	private static final int OFFWORLD_PALE = 0xC8DCF0;
	/** {@code Faction.REPUBLIC.ordinal()}: the only army shown blue-white. */
	private static final int REPUBLIC_ORDINAL = 3;

	static void handle(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (payload.variant() == QuestEffects.CALLING_NONE || payload.dir().lengthSqr() < 1.0e-4) {
			faint = true;
			remaining = 0;
			return;
		}

		faint = false;
		variant = payload.variant();
		data = payload.entityId();
		direction = payload.dir().normalize();
		closeness = payload.intensity();
		remaining = SHOW_TICKS;

		if (client.player == null || variant == QuestEffects.CALLING_SMOKE) {
			return;
		}

		Vec3 look = client.player.getLookAngle().horizontal();
		double facing = look.lengthSqr() < 1.0e-4 ? 0 : Math.max(0, look.normalize().dot(direction.horizontal().normalize()));
		float volume = (float) (0.2 + 0.8 * facing * facing);

		switch (variant) {
			case QuestEffects.CALLING_VOICES -> {
				// Two low tones, a warm one and a colder one a fifth below.
				QuestSounds.ui(ModSounds.CRYSTAL_CALLING, 0.6f + 0.1f * closeness, volume * 0.6f);
				QuestSounds.ui(ModSounds.CRYSTAL_CALLING, 0.4f + 0.07f * closeness, volume * 0.5f);
			}
			case QuestEffects.CALLING_HOLOCRON -> QuestSounds.ui(ModSounds.STORY_CALLING_HOLOCRON, 0.95f + 0.1f * closeness, volume * 0.8f);
			case QuestEffects.CALLING_CONTRACT -> QuestSounds.ui(ModSounds.CRYSTAL_CALLING, 0.62f + 0.1f * closeness, volume * 0.7f);
			case QuestEffects.CALLING_RELIQUARY -> QuestSounds.ui(ModSounds.STORY_CALLING_RELIQUARY, 1f, volume * (0.25f + 0.75f * closeness));
			case QuestEffects.CALLING_FALL -> QuestSounds.ui(ModSounds.STORY_CALLING_FALL, 0.9f + 0.2f * closeness, volume * 0.8f);
			// The M1 chime pitched up: the call is from another world (spec MILESTONE_4.md 13.3).
			case QuestEffects.CALLING_OFFWORLD -> QuestSounds.ui(ModSounds.CRYSTAL_CALLING, 1.3f, volume * 0.6f);
			default -> QuestSounds.ui(ModSounds.CRYSTAL_CALLING, 0.9f + 0.2f * closeness, volume);
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
		int base = variant == QuestEffects.CALLING_RELIQUARY ? 2 + Math.round(2 * closeness) : 2;

		for (int i = 0; i < Vfx.count(base); i++) {
			float spread = (float) Math.toRadians((RANDOM.nextFloat() * 2f - 1f) * 15f);
			Vec3 dir = direction.yRot(spread);
			double distance = 4 + RANDOM.nextDouble() * 3;

			if (variant == QuestEffects.CALLING_FALL) {
				// Streaks start high and slant down toward the site.
				Vec3 pos = eye.add(dir.scale(distance - 1.5)).add(0, 1.5 + RANDOM.nextDouble() * 1.5, 0);
				Vec3 vel = dir.scale(0.07 + 0.03 * closeness).add(0, -0.06, 0);
				mc.level.addParticle(QuestParticles.FALLING_EMBER, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
				continue;
			}

			// The contract's ash keeps to eye height; the rest rise and fall a little.
			double rise = variant == QuestEffects.CALLING_CONTRACT ? RANDOM.nextDouble() * 0.6 - 0.3 : RANDOM.nextDouble() * 2 - 1;
			Vec3 pos = eye.add(dir.scale(distance)).add(0, rise, 0);
			Vec3 vel = dir.scale(0.035 + 0.02 * closeness).add(0, 0.004, 0);
			mc.level.addParticle(particle(i), pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
		}
	}

	private static ParticleOptions particle(int index) {
		return switch (variant) {
			case QuestEffects.CALLING_VOICES -> index % 2 == 0 ? QuestParticles.VOICE_WARM : QuestParticles.VOICE_DUST;
			case QuestEffects.CALLING_SMOKE, QuestEffects.CALLING_CONTRACT -> QuestParticles.ASH_MOTE;
			// Tinted dust glints along the direction, every other one a pale shimmer drifting with it.
			case QuestEffects.CALLING_HOLOCRON -> index % 2 == 0 ? new DustParticleOptions(holocronTint(), 0.6f) : QuestParticles.RESONANCE_SHIMMER;
			case QuestEffects.CALLING_RELIQUARY -> index % 3 == 2 ? QuestParticles.VOICE_WARM : new DustParticleOptions(RELIQUARY_AMBER, 0.8f);
			case QuestEffects.CALLING_OUTPOST -> new DustParticleOptions(data == REPUBLIC_ORDINAL ? REPUBLIC_WHITE : EMPIRE_CRIMSON, 0.55f);
			// Faint pale motes toward the ship or pad.
			case QuestEffects.CALLING_OFFWORLD -> new DustParticleOptions(OFFWORLD_PALE, 0.45f);
			default -> QuestParticles.RESONANCE_SHIMMER;
		};
	}

	/** Warm gold for a Jedi holocron, rust for a Sith one, brass for a Wayfarer one. */
	private static int holocronTint() {
		return switch (data) {
			case 1 -> SITH_RUST;
			case 2 -> WAYFARER_BRASS;
			default -> JEDI_GOLD;
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
		data = 0;
	}

	private CallingFx() {
	}
}
