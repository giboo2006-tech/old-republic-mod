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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side saber audio that needs no packets: one positional hum per nearby player holding a
 * lit blade, and swing sounds detected from the vanilla swing state of each player.
 */
final class SaberAudio {
	private static final double HUM_RANGE = 32;
	private static final Map<Integer, SaberHumSound> HUMS = new HashMap<>();
	private static final Map<Integer, Boolean> WAS_SWINGING = new HashMap<>();

	/** True while {@code player} holds a saber whose blade is fully out. */
	static boolean humming(Player player) {
		return litSaber(player) != null;
	}

	/** The lit, fully extended saber in either hand, main hand first, or null. */
	static @Nullable ItemStack litSaber(Player player) {
		for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
			if (SaberComponents.isIgnited(stack) && SaberModelProperties.extension(stack, null) >= 1f) {
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

		for (Player player : mc.level.players()) {
			boolean near = player.distanceToSqr(mc.player) <= HUM_RANGE * HUM_RANGE;
			ItemStack lit = near ? litSaber(player) : null;

			if (lit != null && !HUMS.containsKey(player.getId())) {
				SaberHumSound hum = new SaberHumSound(player);
				HUMS.put(player.getId(), hum);
				mc.getSoundManager().play(hum);
			}

			boolean swinging = player.swinging;
			Boolean before = WAS_SWINGING.put(player.getId(), swinging);

			if (lit != null && swinging && (before == null || !before)) {
				float pitch = 0.92f + player.getRandom().nextFloat() * 0.16f;
				mc.level.playLocalSound(player.getX(), player.getY() + 1.2, player.getZ(), ModSounds.SABER_SWING, SoundSource.PLAYERS,
						0.8f * ClientConfig.get().modSoundVolume, pitch, false);
				SwingTrailRenderer.onSwing(player);
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
		WAS_SWINGING.clear();
	}

	private SaberAudio() {
	}
}
