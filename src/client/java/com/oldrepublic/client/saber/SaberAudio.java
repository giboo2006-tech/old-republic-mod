package com.oldrepublic.client.saber;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberHums;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Client-side saber audio that needs no packets: one positional hum per lit blade in each hand of
 * every nearby living entity (players and saber NPCs alike), so a dual-wielder plays two hums
 * (MILESTONE_5.md 10.5), and swing sounds detected from the vanilla swing state of each of them.
 * Each hum is its emitter's profile ({@link SaberHums#hum}; a training saber keeps the M1 hum) and
 * ends when its hand's saber changes to one with another profile, so the next tick starts the right
 * one. A thrown saber does not hum in the hand; the flying blade has its own loop
 * ({@link ThrownSaberFx}). A saber built around a singing crystal adds its hum layer
 * ({@link SaberSingingSound}) over its hum, on the same stop rules.
 *
 * <p>A swing counts as new when the swing starts or restarts (its clock goes back), so a
 * dual-wielder's alternating swings (MILESTONE_5.md 10.2), which follow each other faster than half
 * a swing, each get their trail; an off-hand swing's sound comes from the {@code offhand_strike}
 * effect ({@code saber.offhand_swing}) instead of the swing sound here.
 *
 * <p>A swing is the blade's hum carried past the ear (docs/AUDIO_BRIEF.md, "Lightsaber"), so the
 * swing sound plays at the pitch of the swinging blade's hum: its emitter's hum pitch, lifted with
 * the hum's motion level ({@link SaberHumSound#motion}), which also makes a faster swing louder.
 */
final class SaberAudio {
	private static final double HUM_RANGE = 32;
	/** The swing sound's volume, its lift at full motion (a fraction of it) and its random pitch spread. */
	private static final float SWING_VOLUME = 0.8f;
	private static final float SWING_VOLUME_SPAN = 0.25f;
	private static final float SWING_PITCH_SPREAD = 0.08f;
	/** Hums and singing layers by {@link #key(int, InteractionHand)}. */
	private static final Map<Long, SaberHumSound> HUMS = new HashMap<>();
	private static final Map<Long, SaberSingingSound> SINGING = new HashMap<>();
	/** The swing clock seen last tick, or -2 when not swinging. */
	private static final Map<Integer, Integer> WAS_SWINGING = new HashMap<>();

	/** The map key of {@code hand} of the entity {@code entityId}. */
	private static long key(int entityId, InteractionHand hand) {
		return (long) entityId * 2 + hand.ordinal();
	}

	/** True while the saber in {@code hand} is fully lit and its profile's hum is {@code hum}. */
	static boolean humming(LivingEntity entity, InteractionHand hand, SoundEvent hum) {
		ItemStack lit = litSaber(entity, hand);
		return lit != null && SaberHums.hum(lit) == hum;
	}

	/** True while the saber in {@code hand} is fully lit and built around a singing crystal. */
	static boolean singing(LivingEntity entity, InteractionHand hand) {
		ItemStack lit = litSaber(entity, hand);
		return lit != null && SaberComponents.isSinging(lit);
	}

	/** The lit, fully extended, un-thrown saber in {@code hand}, or null. */
	static @Nullable ItemStack litSaber(LivingEntity entity, InteractionHand hand) {
		ItemStack stack = entity.getItemInHand(hand);
		return SaberComponents.isUsable(stack) && SaberModelProperties.extension(stack, null) >= 1f ? stack : null;
	}

	/** Whether {@code entity}'s current swing is an off-hand swing with a lit off-hand saber (10.2). */
	static boolean offhandSwing(LivingEntity entity) {
		return entity.swingingArm == InteractionHand.OFF_HAND && SaberComponents.isUsable(entity.getOffhandItem());
	}

	/** The lit, fully extended, un-thrown saber in either hand, main hand first, or null. */
	static @Nullable ItemStack litSaber(LivingEntity entity) {
		ItemStack main = litSaber(entity, InteractionHand.MAIN_HAND);
		return main != null ? main : litSaber(entity, InteractionHand.OFF_HAND);
	}

	static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			clear();
			return;
		}

		HUMS.values().removeIf(sound -> sound.isStopped());
		SINGING.values().removeIf(sound -> sound.isStopped());

		AABB area = mc.player.getBoundingBox().inflate(HUM_RANGE);

		for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !e.isSpectator())) {
			boolean near = entity.distanceToSqr(mc.player) <= HUM_RANGE * HUM_RANGE;

			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack inHand = near ? litSaber(entity, hand) : null;
				long key = key(entity.getId(), hand);

				if (inHand != null && !HUMS.containsKey(key)) {
					SaberHumSound hum = new SaberHumSound(entity, hand, SaberHums.hum(inHand));
					HUMS.put(key, hum);
					mc.getSoundManager().play(hum);
				}

				if (inHand != null && SaberComponents.isSinging(inHand) && !SINGING.containsKey(key)) {
					SaberSingingSound layer = new SaberSingingSound(entity, hand);
					SINGING.put(key, layer);
					mc.getSoundManager().play(layer);
				}
			}

			ItemStack lit = near ? litSaber(entity) : null;

			int clock = entity.swinging ? entity.swingTime : -2;
			Integer before = WAS_SWINGING.put(entity.getId(), clock);
			boolean started = entity.swinging && (before == null || before == -2 || clock < before);

			if (lit != null && started) {
				if (!offhandSwing(entity)) {
					float motion = humMotion(entity);
					float spread = 1f + (entity.getRandom().nextFloat() - 0.5f) * SWING_PITCH_SPREAD;
					float pitch = emitterPitch(lit) * (1f + SaberHumSound.PITCH_SPAN * motion) * spread;
					float volume = Math.min(1f, SWING_VOLUME * (1f + SWING_VOLUME_SPAN * motion));
					SoundSource source = entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
					mc.level.playLocalSound(entity.getX(), entity.getY() + 1.2, entity.getZ(), ModSounds.SABER_SWING, source,
							volume * ClientConfig.get().modSoundVolume, pitch, false);
				}
			}
		}

		for (Iterator<Integer> it = WAS_SWINGING.keySet().iterator(); it.hasNext();) {
			if (mc.level.getEntity(it.next()) == null) {
				it.remove();
			}
		}
	}

	/** The motion level of the hum of {@code entity}'s lit saber, main hand first, or 0 while neither hums. */
	private static float humMotion(LivingEntity entity) {
		SaberHumSound hum = HUMS.get(key(entity.getId(), InteractionHand.MAIN_HAND));

		if (hum == null) {
			hum = HUMS.get(key(entity.getId(), InteractionHand.OFF_HAND));
		}

		return hum != null ? hum.motion() : 0f;
	}

	/**
	 * The swing sound's pitch for {@code lit}'s emitter: its hum's fundamental over the M1 hum's 92 Hz
	 * (FG, FD, FR and FC in tools/assets/sounds/saber_m5.py), so a swing carries the blade's own hum.
	 */
	private static float emitterPitch(ItemStack lit) {
		Emitter emitter = SaberHums.profile(lit);

		if (emitter == null) {
			return 1f;
		}

		return switch (emitter) {
			case GUARDIAN -> 76f / 92f;
			case DUELIST -> 112f / 92f;
			case RELIQUARY -> 86f / 92f;
			case CANTOR -> 70f / 92f;
			// An emitter appended later swings at the M1 pitch until it has a hum of its own.
			default -> 1f;
		};
	}

	/** Stops every hum (disconnect, level change). */
	static void clear() {
		HUMS.values().forEach(SaberHumSound::end);
		HUMS.clear();
		SINGING.values().forEach(SaberSingingSound::end);
		SINGING.clear();
		WAS_SWINGING.clear();
	}

	private SaberAudio() {
	}
}
