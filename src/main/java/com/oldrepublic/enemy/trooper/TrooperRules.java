package com.oldrepublic.enemy.trooper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.registry.M3Items;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * The trooper rules that need no world (spec 5.2, 7.1 to 7.5): squad composition, role stats,
 * incident and expiry checks, bark rate limits, the deflection answer, morale and death drops.
 * Every time is passed in, so tests can use an explicit game time.
 */
public final class TrooperRules {
	/** "Never happened" for last-bark and similar times. */
	public static final long NEVER = Long.MIN_VALUE;
	/** At most three fire sounds per squad per four ticks (spec 13.3). */
	public static final int FIRE_SOUNDS_PER_WINDOW = 3;
	public static final int FIRE_SOUND_WINDOW = 4;
	/** An officer's {health, armour}: a uniform, and invulnerable apart from {@code /kill} and the void. */
	private static final float[] OFFICER_STATS = {20, 0};

	/** A Republic GRENADIER and an Imperial MEDIC do not exist: they become RIFLEMAN (W4 plan 4.2). */
	public static TrooperRole normalise(Faction army, TrooperRole role) {
		if (army == Faction.REPUBLIC && role == TrooperRole.GRENADIER) {
			return TrooperRole.RIFLEMAN;
		}

		if (army != Faction.REPUBLIC && role == TrooperRole.MEDIC) {
			return TrooperRole.RIFLEMAN;
		}

		return role;
	}

	/**
	 * The patrol composition (spec 7.5): two riflemen, a medic (Republic) or grenadier (Imperial) and
	 * a sergeant, plus one rifleman when the nearest player is at least {@code extraRiflemanLevel}.
	 * The sergeant comes first: he leads.
	 */
	public static List<TrooperRole> defaultSquad(Faction army, int nearestPlayerLevel, TrooperBalance b) {
		List<TrooperRole> roles = new ArrayList<>(5);
		roles.add(TrooperRole.SERGEANT);
		roles.add(TrooperRole.RIFLEMAN);
		roles.add(TrooperRole.RIFLEMAN);
		roles.add(army == Faction.REPUBLIC ? TrooperRole.MEDIC : TrooperRole.GRENADIER);

		if (nearestPlayerLevel >= b.extraRiflemanLevel) {
			roles.add(TrooperRole.RIFLEMAN);
		}

		return roles;
	}

	/**
	 * {health, armour} for a role. Officers wear uniforms, not armour, and only {@code /kill} and the
	 * void can hurt them, so their numbers are fixed rather than balance keys.
	 */
	public static float[] stats(Faction army, TrooperRole role, TrooperBalance b) {
		boolean republic = army == Faction.REPUBLIC;
		float[] stats = switch (normalise(army, role)) {
			case OFFICER -> OFFICER_STATS;
			case SERGEANT -> republic ? b.republicSergeant : b.imperialSergeant;
			case MEDIC -> b.republicMedic;
			case GRENADIER -> b.imperialGrenadier;
			case RIFLEMAN -> republic ? b.republicRifleman : b.imperialRifleman;
		};

		// A hand-edited balance file may hold a short array: fall back per value.
		float health = stats != null && stats.length > 0 && stats[0] > 0 ? stats[0] : OFFICER_STATS[0];
		float armour = stats != null && stats.length > 1 ? Math.max(0, stats[1]) : 0;
		return new float[] {health, armour};
	}

	/** Whether the role carries a rifle (the others a pistol, the officer nothing). */
	public static boolean carriesRifle(TrooperRole role) {
		return role == TrooperRole.RIFLEMAN || role == TrooperRole.SERGEANT;
	}

	// ---------------------------------------------------------------- incidents and expiries

	/** The incident mode in force at {@code now}: {@code mode} until {@code until}, then NONE. */
	public static IncidentMode incidentAt(IncidentMode mode, long until, long now) {
		return mode != IncidentMode.NONE && now < until ? mode : IncidentMode.NONE;
	}

	/** Whether an incident makes the squad engaged with {@code player} from the start (ARMY_AND_OWNER and its owner). */
	public static boolean incidentIncludes(IncidentMode mode, long until, long now, @Nullable UUID owner, UUID player) {
		return incidentAt(mode, until, now) == IncidentMode.ARMY_AND_OWNER && player.equals(owner);
	}

	/**
	 * Merges a new incident into the current one: the stronger mode wins, and the later expiry.
	 *
	 * @return {mode ordinal, until}
	 */
	public static long[] mergeIncident(IncidentMode mode, long until, IncidentMode added, long addedUntil, long now) {
		IncidentMode current = incidentAt(mode, until, now);

		if (current == IncidentMode.NONE) {
			return new long[] {added.ordinal(), addedUntil};
		}

		IncidentMode stronger = added.ordinal() > current.ordinal() ? added : current;
		return new long[] {stronger.ordinal(), Math.max(until, addedUntil)};
	}

	/** The expiry of something that lasts {@code ticks} from {@code now}, never overflowing. */
	public static long expiry(long now, int ticks) {
		return ticks <= 0 ? now : (now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks);
	}

	// ---------------------------------------------------------------- barks

	/** One bark per trooper per {@code barkTrooperTicks} and one per squad per {@code barkSquadTicks}. */
	public static boolean barkAllowed(long now, long lastTrooper, long lastSquad, TrooperBalance b) {
		return elapsed(now, lastTrooper) >= b.barkTrooperTicks && elapsed(now, lastSquad) >= b.barkSquadTicks;
	}

	private static long elapsed(long now, long since) {
		return since == NEVER ? Long.MAX_VALUE : now - since;
	}

	// ---------------------------------------------------------------- deflection, morale, fire sounds

	/**
	 * Whether a rifleman answers deflection now: at least {@code deflectCount} of its bolts were
	 * deflected within the last {@code deflectWindowTicks} (the times include the one just now).
	 */
	public static boolean answersDeflection(List<Long> deflectedAt, long now, TrooperBalance b) {
		int recent = 0;

		for (long at : deflectedAt) {
			if (now - at < b.deflectWindowTicks) {
				recent++;
			}
		}

		return recent >= b.deflectCount;
	}

	/**
	 * A squad's encounter is over (spec 7.3): it last fought, lost a member or fired its flare at
	 * {@code lastCombat} ({@link #NEVER} when it has not), and {@code quietTicks} have passed since.
	 */
	public static boolean encounterOver(long lastCombat, long now, int quietTicks) {
		return lastCombat != NEVER && now - lastCombat >= quietTicks;
	}

	/** The squad breaks (spec 7.5) once no sergeant lives and at most half of it is still standing. */
	public static boolean moraleBreaks(boolean sergeantAlive, int standing, int squadSize) {
		return !sergeantAlive && squadSize > 1 && standing * 2 <= squadSize;
	}

	/** Ticks between two Republic bursts, or two Imperial shots, shortened while rallied. */
	public static int shotCooldown(int base, boolean rallied, TrooperBalance b) {
		return rallied ? Math.max(1, Math.round(base * (1f - b.rallyFireRate))) : base;
	}

	/** A hit of at least {@code interruptDamage} breaks the medic's channel; other actions ignore damage. */
	public static boolean hitInterrupts(TrooperAction action, float damage, TrooperBalance b) {
		return action == TrooperAction.HEALING && damage >= b.interruptDamage;
	}

	/** The tells and channels a Force interrupt (stagger, Push, Pull, Disarming Pull, shock) cancels. */
	public static boolean interruptible(TrooperAction action) {
		return switch (action) {
			case CHARGING, HEALING, RALLY_TELL, FLARE_TELL, GRENADE_TELL -> true;
			default -> false;
		};
	}

	// ---------------------------------------------------------------- drops

	/**
	 * The trooper's own drops (spec 7.1, 9.3): one durasteel plate at {@code durasteelChance}, and only
	 * when a player killed it. Never armour, never commendations.
	 */
	public static List<ItemStack> deathDrops(RandomSource random, boolean byPlayer, TrooperBalance b) {
		if (!byPlayer || random.nextFloat() >= b.durasteelChance) {
			return List.of();
		}

		return List.of(new ItemStack(M3Items.DURASTEEL_PLATE));
	}

	private TrooperRules() {
	}
}
