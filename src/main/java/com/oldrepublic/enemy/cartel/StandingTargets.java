package com.oldrepublic.enemy.cartel;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.enemy.trooper.TrooperAttitudeRule;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.melee.ElectrostaffItem;
import com.oldrepublic.equipment.melee.VibroWeaponItem;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.saber.SaberComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * How a {@link FactionFoe} picks its fights (spec MILESTONE_5.md 7, 12.1): Hostile players on sight
 * (a hub guard never starts one beside a pad bay or the medbay), anyone it engaged or who provoked it;
 * a Distrusted (Wary) player within {@code waryRange} is warned, and fought if they draw a weapon
 * there after the warning. Read on the server, every {@link #INTERVAL} ticks.
 */
public final class StandingTargets {
	/** Ticks between target checks. */
	public static final int INTERVAL = 10;
	/** A warned player has this long to put the weapon away before the foe engages. */
	private static final int WARN_GRACE = 20;

	/** The faction's numbers the checks read. */
	public record Settings(double followRange, double waryRange, int warnRepeatTicks, int engageTicks, int provokeTicks, double safeRadius,
			SoundEvent bark, String warnKey) {
	}

	/** Keeps a good target, drops a stale one, picks a new one, and warns Wary players. Server side. */
	public static void update(Mob mob, FactionFoe foe, ServerLevel level, Settings s) {
		update(mob, foe, level, level.players(), s);
	}

	/**
	 * {@link #update} with {@code players} as the players about (the level's). Public for the tests, whose survival
	 * players are fake players, not in the level's list.
	 */
	public static void update(Mob mob, FactionFoe foe, ServerLevel level, List<? extends Player> players, Settings s) {
		long now = level.getGameTime();
		foe.standing().prune(now, s.warnRepeatTicks() * 4);
		LivingEntity current = mob.getTarget();

		if (current instanceof Player player) {
			if (player.isAlive() && !ignored(player) && player.level() == level && player.distanceToSqr(mob) <= s.followRange() * s.followRange() * 2.25
					&& wantsToFight(mob, foe, player, now, s)) {
				return;
			}

			mob.setTarget(null);
		} else if (current != null && current.isAlive()) {
			return;
		} else if (current != null) {
			mob.setTarget(null);
		}

		Player best = null;
		double bestDistance = s.followRange() * s.followRange();

		for (Player player : players) {
			if (!player.isAlive() || ignored(player)) {
				continue;
			}

			double distance = player.distanceToSqr(mob);

			if (distance > s.followRange() * s.followRange()) {
				continue;
			}

			if (!wantsToFight(mob, foe, player, now, s) && wary(mob, foe, player, now, distance, s)) {
				foe.standing().engage(player.getUUID(), now + s.engageTicks());
			}

			if (distance < bestDistance && wantsToFight(mob, foe, player, now, s) && (foe.standing().engaged(player.getUUID(), now)
					|| foe.standing().provoked(player.getUUID(), now) || mob.getSensing().hasLineOfSight(player))) {
				best = player;
				bestDistance = distance;
			}
		}

		if (best != null) {
			mob.setTarget(best);
		}
	}

	/**
	 * Whether {@code mob} fights {@code p} now: Hostile (not beside a hub's pad bay or medbay for a hub
	 * guard), engaged or provoked. Never in Peaceful.
	 */
	public static boolean wantsToFight(Mob mob, FactionFoe foe, Player p, long now, Settings s) {
		if (TrooperAttitudeRule.peaceful(mob)) {
			return false;
		}

		if (foe.standing().engaged(p.getUUID(), now) || foe.standing().provoked(p.getUUID(), now)) {
			return true;
		}

		return StandingRule.attitude(mob, foe, p) == Attitude.HOSTILE && !(foe.isHubGuard() && atSafeSpot(mob, p, s.safeRadius()));
	}

	/**
	 * The Wary rule for one player: warned once per {@code warnRepeatTicks} when within
	 * {@code waryRange}; true (engage) when they hold a drawn weapon there after a warning.
	 */
	private static boolean wary(Mob mob, FactionFoe foe, Player p, long now, double distanceSqr, Settings s) {
		if (distanceSqr > s.waryRange() * s.waryRange() || StandingRule.attitude(mob, foe, p) != Attitude.WARY) {
			return false;
		}

		StandingState state = foe.standing();
		boolean warnedBefore = state.warnedWithin(p.getUUID(), now, s.warnRepeatTicks()) && !state.warnedWithin(p.getUUID(), now, WARN_GRACE);

		if (state.warnDue(p.getUUID(), now, s.warnRepeatTicks())) {
			state.warned(p.getUUID(), now);
			mob.playSound(s.bark(), 1f, 0.95f + mob.getRandom().nextFloat() * 0.1f);
			p.sendOverlayMessage(Component.translatable(s.warnKey(), mob.getDisplayName()));
		}

		return warnedBefore && weaponDrawn(p);
	}

	/** A lit lightsaber in either hand, or a blaster, vibroblade or electrostaff in the main hand. */
	public static boolean weaponDrawn(Player p) {
		if (SaberComponents.isIgnited(p.getMainHandItem()) || SaberComponents.isIgnited(p.getOffhandItem())) {
			return true;
		}

		Item held = p.getMainHandItem().getItem();
		return held instanceof BlasterItem || held instanceof VibroWeaponItem || held instanceof ElectrostaffItem;
	}

	/** Beside one of the hub's pad bays or its medbay (the planet of {@code mob}'s level). */
	private static boolean atSafeSpot(Mob mob, Player p, double radius) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return false;
		}

		Planets.Planet planet = Planets.Planet.of(level.dimension());

		if (planet == null) {
			return false;
		}

		List<BlockPos> bays = PlanetHubs.padBays(level.getServer(), planet);
		BlockPos medbay = PlanetHubs.medbay(level.getServer(), planet);
		double r = radius * radius;

		for (BlockPos bay : bays) {
			if (p.distanceToSqr(Vec3.atBottomCenterOf(bay)) <= r) {
				return true;
			}
		}

		return medbay != null && p.distanceToSqr(Vec3.atBottomCenterOf(medbay)) <= r;
	}

	/**
	 * {@code mob} attacks {@code target}: a fight it starts with a player who is not Hostile is an
	 * engagement (it counts as hostile, 7).
	 */
	public static void onAttack(Mob mob, FactionFoe foe, LivingEntity target, int engageTicks) {
		if (target instanceof Player player && StandingRule.attitude(mob, foe, player) != Attitude.HOSTILE
				&& !foe.standing().provoked(player.getUUID(), mob.level().getGameTime())) {
			foe.standing().engage(player.getUUID(), mob.level().getGameTime() + engageTicks);
		}
	}

	/**
	 * {@code mob} was struck by {@code attacker}: a player who was not already its foe provoked it; it
	 * turns on them. Allied foes forgive nothing either: a blow is a blow.
	 */
	public static void onHurtBy(Mob mob, FactionFoe foe, @Nullable LivingEntity attacker, Settings s) {
		if (!(attacker instanceof Player player) || ignored(player) || TrooperAttitudeRule.peaceful(mob)) {
			return;
		}

		long now = mob.level().getGameTime();
		StandingState state = foe.standing();

		if (state.engaged(player.getUUID(), now)) {
			state.engage(player.getUUID(), now + s.engageTicks());
		} else if (StandingRule.attitude(mob, foe, player) != Attitude.HOSTILE) {
			state.provoke(player.getUUID(), now + s.provokeTicks());
		}

		mob.setTarget(player);
	}

	/** Copies {@code from}'s fight with {@code p} onto {@code to} (the captain's call). */
	public static void share(FactionFoe from, FactionFoe to, Player p, long now, Settings s) {
		if (from.standing().engaged(p.getUUID(), now)) {
			to.standing().engage(p.getUUID(), now + s.engageTicks());
		}

		if (from.standing().provoked(p.getUUID(), now)) {
			to.standing().provoke(p.getUUID(), now + s.provokeTicks());
		}
	}

	/** Creative and spectator players, by game mode (a test's mock player answers {@code isCreative()} true). */
	public static boolean ignored(LivingEntity entity) {
		return entity instanceof Player player && (player.gameMode() == GameType.CREATIVE || player.isSpectator());
	}

	private StandingTargets() {
	}
}
