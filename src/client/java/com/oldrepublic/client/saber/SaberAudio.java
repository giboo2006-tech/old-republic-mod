package com.oldrepublic.client.saber;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberComponents;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Client-side saber audio that needs no packets: one positional hum per nearby living entity
 * (players and saber NPCs alike) holding a lit blade, and swing sounds detected from the vanilla
 * swing state of each of them. A thrown saber does not hum in the hand; the flying blade has its
 * own loop ({@link ThrownSaberFx}). A saber built around a singing crystal adds its hum layer
 * ({@link SaberSingingSound}) over the normal hum, on the same stop rules.
 */
final class SaberAudio {
	private static final double HUM_RANGE = 32;
	private static final Map<Integer, SaberHumSound> HUMS = new HashMap<>();
	private static final Map<Integer, SaberSingingSound> SINGING = new HashMap<>();
	private static final Map<Integer, Boolean> WAS_SWINGING = new HashMap<>();

	/** True while {@code entity} holds a saber whose blade is fully out. */
	static boolean humming(LivingEntity entity) {
		return litSaber(entity) != null;
	}

	/** True while {@code entity} holds a fully lit saber built around a singing crystal. */
	static boolean singing(LivingEntity entity) {
		ItemStack lit = litSaber(entity);
		return lit != null && SaberComponents.isSinging(lit);
	}

	/** The lit, fully extended, un-thrown saber in either hand, main hand first, or null. */
	static @Nullable ItemStack litSaber(LivingEntity entity) {
		for (ItemStack stack : new ItemStack[] {entity.getMainHandItem(), entity.getOffhandItem()}) {
			if (SaberComponents.isUsable(stack) && SaberModelProperties.extension(stack, null) >= 1f) {
				return stack;
			}
		}

		return null;
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
			ItemStack lit = entity.distanceToSqr(mc.player) <= HUM_RANGE * HUM_RANGE ? litSaber(entity) : null;

			if (lit != null && !HUMS.containsKey(entity.getId())) {
				SaberHumSound hum = new SaberHumSound(entity);
				HUMS.put(entity.getId(), hum);
				mc.getSoundManager().play(hum);
			}

			if (lit != null && SaberComponents.isSinging(lit) && !SINGING.containsKey(entity.getId())) {
				SaberSingingSound layer = new SaberSingingSound(entity);
				SINGING.put(entity.getId(), layer);
				mc.getSoundManager().play(layer);
			}

			boolean swinging = entity.swinging;
			Boolean before = WAS_SWINGING.put(entity.getId(), swinging);

			if (lit != null && swinging && (before == null || !before)) {
				float pitch = 0.92f + entity.getRandom().nextFloat() * 0.16f;
				SoundSource source = entity instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
				mc.level.playLocalSound(entity.getX(), entity.getY() + 1.2, entity.getZ(), ModSounds.SABER_SWING, source,
						0.8f * ClientConfig.get().modSoundVolume, pitch, false);
				SwingTrailRenderer.onSwing(entity);
			}
		}

		for (Iterator<Integer> it = WAS_SWINGING.keySet().iterator(); it.hasNext();) {
			if (mc.level.getEntity(it.next()) == null) {
				it.remove();
			}
		}
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
