package com.oldrepublic.space;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.economy.Credits;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.progression.ProgressionBalance;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.M4Items;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What a destroyed ship target pays (spec MILESTONE_5.md 5.5): kill XP to the shooter with the M1
 * anti-grind fatigue (interceptor 60, raider 80, Hask fighter 70, gunship 600) and credits straight
 * into their purse (raider 15, others 10), then the story's {@code onShipKill} and the encounter's
 * progress. The shooter is the player whose bolt or missile struck: ship weapons name the firing
 * ship as the damage's cause, so the M1 kill reward (which pays a player cause) never pays twice.
 * Nothing drops in space: what does not fit the shooter's pockets goes into their ship's hold.
 */
public final class SpaceRewards {
	/** The player credited with a ship weapon's hit: the shooter its bolt or missile carries, or null (an enemy's shot, a command). */
	public static @Nullable UUID shooterOf(DamageSource source) {
		Entity direct = source.getDirectEntity();

		if (direct instanceof ShipBolt bolt) {
			return bolt.shooter();
		}

		return direct instanceof ShipMissile missile ? missile.shooter() : null;
	}

	/**
	 * {@code target} (of {@code kind}) was destroyed by {@code source}: the shooter's XP and credits,
	 * {@code QuestHooks.onShipKill} (not for the generator, which is part of the gunship), then its
	 * encounter's progress.
	 */
	public static void onDestroyed(ServerLevel level, LivingEntity target, SpaceRules.Kind kind, DamageSource source) {
		UUID shooterId = shooterOf(source);
		ServerPlayer shooter = shooterId == null ? null : level.getServer().getPlayerList().getPlayer(shooterId);

		if (shooter != null && kind != SpaceRules.Kind.GENERATOR) {
			SpaceBalance b = SpaceBalance.get();
			killXp(shooter, target, SpaceRules.killXp(kind, b));
			credits(shooter, SpaceRules.killCredits(kind, b));
			QuestHooks.onShipKill(shooter, target, SpaceEncounters.ownerOf(target));
		}

		SpaceEncounters.onDestroyed(level, target, kind, shooter);
	}

	/** Kill XP with the M1 fatigue for the target's type (the rule {@code Progression}'s own kill reward follows): only after the awakening. */
	static void killXp(ServerPlayer p, LivingEntity target, int xp) {
		ForceData data = Progression.get(p);

		if (xp <= 0 || !data.isAwakened()) {
			return;
		}

		ProgressionBalance b = Progression.balance();
		String typeId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();
		float fatigue = data.killFatigue.getOrDefault(typeId, 0f);
		float multiplier = Math.max(b.fatigueFloor, (float) Math.pow(b.fatigueFactor, fatigue));
		data.killFatigue.put(typeId, fatigue + 1f);
		Progression.addXp(p, Math.max(1, Math.round(xp * multiplier)), "ship_kill");
	}

	/** A bonus without fatigue (a whole patrol destroyed, 5.6): only after the awakening, as kill XP. */
	static void bonusXp(ServerPlayer p, int xp) {
		if (xp > 0 && Progression.get(p).isAwakened()) {
			Progression.addXp(p, xp, "space_patrol");
		}
	}

	/** Credits as bundles and chits, through {@link #give}. */
	static void credits(ServerPlayer p, int amount) {
		if (amount <= 0) {
			return;
		}

		int bundles = amount / Credits.BUNDLE;
		int chits = amount % Credits.BUNDLE;

		if (bundles > 0) {
			give(p, new ItemStack(M4Items.CREDIT_BUNDLE, bundles));
		}

		if (chits > 0) {
			give(p, new ItemStack(M4Items.CREDIT_CHIT, chits));
		}
	}

	/**
	 * {@code stack} to {@code p} in space: the inventory first, then their own ship's hold, and only
	 * then at their feet (for them only), which in an orbit means lost to the void.
	 */
	static void give(ServerPlayer p, ItemStack stack) {
		ItemStack rest = stack.copy();
		// Inventory#add moves what fits and shrinks the stack to the remainder.
		p.getInventory().add(rest);

		if (!rest.isEmpty()) {
			rest = ShipHolds.stow(p.level().getServer(), p.getUUID(), rest);
		}

		if (!rest.isEmpty()) {
			Rewards.give(p, rest);
		}
	}

	private SpaceRewards() {
	}
}
