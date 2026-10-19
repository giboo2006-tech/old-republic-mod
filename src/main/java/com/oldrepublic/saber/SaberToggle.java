package com.oldrepublic.saber;

import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.saber.SaberComponents.SaberState;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Ignition and retraction of sabers, always decided on the server. */
public final class SaberToggle {
	public static SaberBalance balance() {
		return BalanceConfig.section("saber", SaberBalance.class, SaberBalance::new);
	}

	/**
	 * Handles the Toggle Lightsaber key: toggles the saber in the main hand, else the one in the off
	 * hand. A player with a saber in each hand ignites or retracts both together (MILESTONE_5.md
	 * 10.2): both light when either is dark, else both go out. Does nothing if the player holds no saber.
	 */
	public static void toggleHeld(ServerPlayer player) {
		if (SaberDual.holdsTwo(player)) {
			ItemStack main = player.getMainHandItem();
			ItemStack off = player.getOffhandItem();
			boolean ignite = !SaberComponents.isIgnited(main) || !SaberComponents.isIgnited(off);

			for (ItemStack stack : new ItemStack[] {main, off}) {
				if (SaberComponents.isIgnited(stack) != ignite) {
					toggle(player, stack);
				}
			}

			return;
		}

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
		playToggleSound(player.level(), player, ignite, stack);
	}

	/**
	 * Sets the ignition state and rewrites the attribute modifiers and the reach component to match.
	 * {@code gameTime} is stored so clients can animate the blade from it.
	 */
	public static void setIgnited(ItemStack stack, boolean ignited, long gameTime) {
		stack.set(SaberComponents.SABER_STATE, new SaberState(ignited, gameTime));
		SaberStats.apply(stack, ignited, balance());
	}

	/**
	 * Rewrites the attribute modifiers and the reach component of {@code stack} for its current parts
	 * (a saberstaff's blade count, M4; a new length or tuning, M5) without touching the ignition state
	 * or its animation clock.
	 */
	public static void refreshModifiers(ItemStack stack, boolean ignited) {
		SaberStats.apply(stack, ignited, balance());
	}

	/**
	 * Server-side ignite/retract sound, heard by everyone nearby including the holder: the profile of
	 * the saber {@code entity} carries (a dropped saber's own stack, else the main hand's saber, else
	 * the off hand's; MILESTONE_5.md 10.5).
	 */
	public static void playToggleSound(Level level, Entity entity, boolean ignite) {
		playToggleSound(level, entity, ignite, carried(entity));
	}

	/** The ignite or retract sound of {@code saber}'s emitter ({@link SaberHums}), heard by everyone nearby. */
	public static void playToggleSound(Level level, Entity entity, boolean ignite, ItemStack saber) {
		float pitch = 0.95f + level.getRandom().nextFloat() * 0.1f;
		level.playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
				ignite ? SaberHums.ignite(saber) : SaberHums.retract(saber), SoundSource.PLAYERS, 1f, pitch);
	}

	/** The saber an entity's toggle sound belongs to, or empty (the M1 sounds). */
	private static ItemStack carried(Entity entity) {
		if (entity instanceof ItemEntity item) {
			return item.getItem();
		}

		if (entity instanceof LivingEntity living) {
			return living.getMainHandItem().getItem() instanceof LightsaberItem ? living.getMainHandItem() : living.getOffhandItem();
		}

		return ItemStack.EMPTY;
	}

	private SaberToggle() {
	}
}
