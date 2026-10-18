package com.oldrepublic.saber;

import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberComponents.SaberState;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Ignition and retraction of sabers, always decided on the server. */
public final class SaberToggle {
	public static SaberBalance balance() {
		return BalanceConfig.section("saber", SaberBalance.class, SaberBalance::new);
	}

	/**
	 * Handles the Toggle Lightsaber key: toggles the saber in the main hand, else the one in the off
	 * hand. Does nothing if the player holds no saber.
	 */
	public static void toggleHeld(ServerPlayer player) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);

			if (stack.getItem() instanceof LightsaberItem) {
				toggle(player, stack);
				return;
			}
		}
	}

	/** Flips the ignition of {@code stack}, which the player is holding. */
	public static void toggle(ServerPlayer player, ItemStack stack) {
		if (player.isSpectator() || !player.isAlive() || !(stack.getItem() instanceof LightsaberItem) || SaberComponents.isThrown(stack)) {
			return;
		}

		boolean ignite = !SaberComponents.state(stack).ignited();

		if (!ignite && player.isUsingItem() && player.getUseItem() == stack) {
			player.stopUsingItem();
		}

		setIgnited(stack, ignite, player.level().getGameTime());
		playToggleSound(player.level(), player, ignite);
	}

	/**
	 * Sets the ignition state and rewrites the attribute modifiers to match. {@code gameTime} is
	 * stored so clients can animate the blade from it.
	 */
	public static void setIgnited(ItemStack stack, boolean ignited, long gameTime) {
		SaberBalance balance = balance();
		stack.set(SaberComponents.SABER_STATE, new SaberState(ignited, gameTime));
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, SaberStats.of(stack, balance).modifiers(ignited, balance));
	}

	/**
	 * Rewrites the attribute modifiers of {@code stack} for its current parts (a saberstaff's blade
	 * count, M4) without touching the ignition state or its animation clock.
	 */
	public static void refreshModifiers(ItemStack stack, boolean ignited) {
		SaberBalance balance = balance();
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, SaberStats.of(stack, balance).modifiers(ignited, balance));
	}

	/** Server-side ignite/retract sound, heard by everyone nearby including the holder. */
	public static void playToggleSound(Level level, Entity entity, boolean ignite) {
		float pitch = 0.95f + level.getRandom().nextFloat() * 0.1f;
		level.playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
				ignite ? ModSounds.SABER_IGNITE : ModSounds.SABER_RETRACT, SoundSource.PLAYERS, 1f, pitch);
	}

	private SaberToggle() {
	}
}
