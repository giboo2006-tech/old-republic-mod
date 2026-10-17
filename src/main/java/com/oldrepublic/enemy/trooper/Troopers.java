package com.oldrepublic.enemy.trooper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.identity.Faction;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Republic and Imperial troopers for other features (spec section 7): squads, officers, downed
 * troopers and escorts, and the questions other features ask about a trooper. {@code army} is
 * {@link Faction#REPUBLIC} or {@link Faction#SITH_EMPIRE}; any other faction is treated as the
 * Republic. Signatures are frozen (spec section 15.1); the additions below them are W4's.
 */
public final class Troopers {
	/** Checks other features register to say a construct is dormant (not fought on sight). */
	private static final List<Predicate<LivingEntity>> DORMANT = new CopyOnWriteArrayList<>();
	/** Checks other features register to say a construct is bound (always fought on sight). */
	private static final List<Predicate<LivingEntity>> BOUND = new CopyOnWriteArrayList<>();
	/** Spacing of a freshly spawned squad around its spawn point. */
	private static final double SPAWN_RING = 1.5;
	/** Distance between the prisoner and each escort. */
	private static final double ESCORT_OFFSET = 1.5;

	/**
	 * Spawns a squad with one trooper per role around {@code at}, under a fresh squad UUID, owned by
	 * {@code owner} when not null and carrying {@code incident} (which never expires on its own). Roles
	 * are normalised for the army; the first Imperial rifleman is the flanker. Persistent.
	 */
	public static List<LivingEntity> spawnSquad(ServerLevel l, BlockPos at, Faction army, List<TrooperRole> roles, @Nullable UUID owner, IncidentMode incident) {
		UUID squad = UUID.randomUUID();
		List<LivingEntity> spawned = new ArrayList<>(roles.size());
		boolean flankerChosen = false;

		for (int i = 0; i < roles.size(); i++) {
			TrooperRole role = TrooperRules.normalise(armyOf(army), roles.get(i));
			TrooperEntity trooper = create(l, army, role, ringPosition(at, i, roles.size()), EntitySpawnReason.EVENT);

			if (trooper == null) {
				continue;
			}

			trooper.setSquad(squad, roles.size());
			trooper.setOwner(owner);
			trooper.setIncident(incident, Long.MAX_VALUE);

			if (!flankerChosen && !trooper.isRepublic() && role == TrooperRole.RIFLEMAN) {
				trooper.setFlanker(true);
				flankerChosen = true;
			}

			trooper.setPersistenceRequired();
			placeClear(l, trooper, at);

			if (l.addFreshEntity(trooper)) {
				spawned.add(trooper);
			}
		}

		return spawned;
	}

	/**
	 * Spawns an outpost officer: invulnerable apart from {@code /kill} and the void, unarmed, opening
	 * {@code officer_<republic|imperial>} on use. The name key is
	 * {@code npc.oldrepublic.officer.<republic|imperial>.<nameSeed>} (seed taken modulo 6). Persistent.
	 */
	public static LivingEntity spawnOfficer(ServerLevel l, BlockPos at, Faction army, int nameSeed) {
		TrooperEntity officer = create(l, army, TrooperRole.OFFICER, Vec3.atBottomCenterOf(at), EntitySpawnReason.EVENT);

		if (officer == null) {
			throw new IllegalStateException("Trooper entity type refused to create an officer");
		}

		officer.setNameSeed(nameSeed);
		officer.setPersistenceRequired();
		l.addFreshEntity(officer);
		return officer;
	}

	/**
	 * Spawns a downed trooper (a rifleman) that {@code owner} can tend (5.4): no AI, never targeted,
	 * hurt only by {@code /kill} and the void, at a quarter of its health. It never bleeds out until
	 * {@link #bleedOut} gives it a time. Persistent.
	 */
	public static LivingEntity spawnDowned(ServerLevel l, BlockPos at, Faction army, UUID owner) {
		TrooperEntity trooper = create(l, army, TrooperRole.RIFLEMAN, Vec3.atBottomCenterOf(at), EntitySpawnReason.EVENT);

		if (trooper == null) {
			throw new IllegalStateException("Trooper entity type refused to create a downed trooper");
		}

		trooper.setPersistenceRequired();
		trooper.setDowned(owner);
		trooper.setHealth(trooper.getMaxHealth() * 0.25f);
		l.addFreshEntity(trooper);
		return trooper;
	}

	/**
	 * Spawns two unsaved riflemen without combat goals who flank {@code prisoner} and walk it from
	 * {@code at} to {@code exit}. The three of them (a player prisoner excepted) are removed together
	 * once they are out of every player's sight, on the way or at the exit, or after
	 * {@code escortDespawnTicks} at the latest. A trooper prisoner is bound (hands behind its back)
	 * for the walk; any other mob prisoner is walked by its own navigation.
	 */
	public static List<LivingEntity> spawnEscort(ServerLevel l, BlockPos at, Faction army, LivingEntity prisoner, BlockPos exit) {
		Vec3 start = Vec3.atBottomCenterOf(at);
		Vec3 toExit = Vec3.atBottomCenterOf(exit).subtract(start).multiply(1, 0, 1);
		Vec3 side = toExit.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(-toExit.z, 0, toExit.x).normalize();
		List<LivingEntity> escorts = new ArrayList<>(2);

		for (int sign : new int[] {1, -1}) {
			TrooperEntity escort = create(l, army, TrooperRole.RIFLEMAN, start.add(side.scale(ESCORT_OFFSET * sign)), EntitySpawnReason.EVENT);

			if (escort == null) {
				continue;
			}

			escort.setEscort(prisoner, exit.immutable(), sign);
			placeClear(l, escort, at);

			if (l.addFreshEntity(escort)) {
				escorts.add(escort);
			}
		}

		if (prisoner instanceof TrooperEntity trooper) {
			trooper.setBound(true);
		}

		return escorts;
	}

	/** Whether the entity is a Republic or Imperial trooper. */
	public static boolean isTrooper(Entity e) {
		return e instanceof TrooperEntity;
	}

	/** The trooper's role; {@link TrooperRole#RIFLEMAN} for anything else. */
	public static TrooperRole role(Entity e) {
		return e instanceof TrooperEntity trooper ? trooper.role() : TrooperRole.RIFLEMAN;
	}

	/** The trooper's army, or null when it is not a trooper. */
	public static @Nullable Faction faction(Entity e) {
		return e instanceof TrooperEntity trooper ? trooper.army() : null;
	}

	/** Whether the entity is a downed trooper waiting to be tended. */
	public static boolean isDowned(Entity e) {
		return e instanceof TrooperEntity trooper && trooper.isDowned();
	}

	/**
	 * One kneel tick of tending ({@code instant} false) or a Force Heal revive ({@code instant} true);
	 * true once the trooper stands. Kneel ticks count once per game tick however many features drive
	 * them, so the W4 sneak-use kneel and a mission's own driver never double up; {@code tendTicks}
	 * of them stand it up. Standing fires {@code QuestHooks.onTrooperTended} and it walks home.
	 */
	public static boolean tend(ServerPlayer p, Entity downed, boolean instant) {
		return downed instanceof TrooperEntity trooper && trooper.tend(p, instant);
	}

	// ---------------------------------------------------------------- W4 additions

	/** The patrol composition of spec 7.5 for {@code army}, with the extra rifleman at {@code extraRiflemanLevel}. */
	public static List<TrooperRole> defaultSquad(Faction army, int nearestPlayerLevel) {
		return TrooperRules.defaultSquad(armyOf(army), nearestPlayerLevel, TrooperBalance.get());
	}

	/**
	 * Makes {@code squad} a natural patrol: never saved (the patrol spawner despawns it as a whole),
	 * and its leader walks {@code route} in order while the others follow.
	 */
	public static void startPatrol(List<LivingEntity> squad, List<BlockPos> route) {
		TrooperEntity leader = null;

		for (LivingEntity member : squad) {
			if (member instanceof TrooperEntity trooper) {
				trooper.setPatrol(true);
				trooper.setRoute(null);

				if (leader == null || trooper.role() == TrooperRole.SERGEANT && leader.role() != TrooperRole.SERGEANT) {
					leader = trooper;
				}
			}
		}

		if (leader != null) {
			leader.setRoute(route);
		}
	}

	public static boolean isPatrol(Entity e) {
		return e instanceof TrooperEntity trooper && trooper.isPatrol();
	}

	public static @Nullable UUID squadId(Entity e) {
		return e instanceof TrooperEntity trooper ? trooper.squad() : null;
	}

	/**
	 * Binds a trooper to its bounty terminal: slot 0 is the officer, 1 to 4 the garrison. Saved as
	 * {@code Terminal}; the trooper stays within {@code leash} of it, and a trooper whose post lists
	 * someone else in its slot discards itself when it loads ({@link TrooperPost}).
	 */
	public static void bindTerminal(Entity trooper, BlockPos terminal, int slot, int leash) {
		if (trooper instanceof TrooperEntity t) {
			t.bindTerminal(terminal, slot, leash);
		}
	}

	public static @Nullable BlockPos terminal(Entity e) {
		return e instanceof TrooperEntity trooper ? trooper.terminal() : null;
	}

	/** The trooper's terminal slot, or -1 when it is not bound. */
	public static int terminalSlot(Entity e) {
		return e instanceof TrooperEntity trooper ? trooper.terminalSlot() : -1;
	}

	/** The downed trooper is discarded at {@code gameTime} unless tended first (on its tick or when it loads). */
	public static void bleedOut(Entity downed, long gameTime) {
		if (downed instanceof TrooperEntity trooper) {
			trooper.setBleedAt(gameTime);
		}
	}

	/**
	 * Makes a sergeant yield at {@code healthFraction} of its health into {@code dialogue} (I2: 0.20 and
	 * {@code yield_border}; contracts: 0.25 and {@code yield_intercept}). Without this call only the
	 * sergeant of an {@code ARMY_AND_OWNER} squad yields, at {@code interceptYield}.
	 */
	public static void setYield(Entity sergeant, float healthFraction, Identifier dialogue) {
		if (sergeant instanceof TrooperEntity trooper) {
			trooper.setYield(healthFraction, dialogue);
		}
	}

	/**
	 * Lets the construct feature say when one of its constructs is dormant: troopers fight
	 * ANCIENT and ARCHIVE constructs on sight only while they are awake. Call during mod init.
	 */
	public static void registerDormancyCheck(Predicate<LivingEntity> isDormant) {
		DORMANT.add(isDormant);
	}

	/**
	 * Lets the construct feature say which constructs are bound (spec 5.2, 7.7: re-bound to guard a
	 * trail, always hostile): troopers attack those on sight, dormant or not. Call during mod init.
	 */
	public static void registerBoundCheck(Predicate<LivingEntity> isBound) {
		BOUND.add(isBound);
	}

	/**
	 * Whether troopers attack an ANCIENT or ARCHIVE construct on sight (spec 5.2): a bound one
	 * always, any other only while it is {@link #awake}.
	 */
	public static boolean fightsOnSight(LivingEntity construct) {
		for (Predicate<LivingEntity> bound : BOUND) {
			if (bound.test(construct)) {
				return true;
			}
		}

		return awake(construct);
	}

	/**
	 * Whether a construct is awake: no registered check says it is dormant, and it is visibly active
	 * (it has a target or is aggressive).
	 */
	public static boolean awake(LivingEntity construct) {
		for (Predicate<LivingEntity> dormant : DORMANT) {
			if (dormant.test(construct)) {
				return false;
			}
		}

		return !(construct instanceof Mob mob) || mob.getTarget() != null || mob.isAggressive();
	}

	// ---------------------------------------------------------------- helpers

	/** Republic or Sith Empire: the two armies with troopers. */
	static Faction armyOf(Faction army) {
		return army == Faction.SITH_EMPIRE ? Faction.SITH_EMPIRE : Faction.REPUBLIC;
	}

	/** A trooper of {@code army} and {@code role} at {@code pos}, set up but not yet added to the level. */
	static @Nullable TrooperEntity create(ServerLevel l, Faction army, TrooperRole role, Vec3 pos, EntitySpawnReason reason) {
		EntityType<TrooperEntity> type = armyOf(army) == Faction.SITH_EMPIRE ? EnemyEntities.SITH_TROOPER : EnemyEntities.REPUBLIC_TROOPER;
		TrooperEntity trooper = type.create(l, reason);

		if (trooper == null) {
			return null;
		}

		trooper.snapTo(pos.x, pos.y, pos.z, l.getRandom().nextFloat() * 360f, 0f);
		trooper.setRole(role);
		trooper.finalizeSpawn(l, l.getCurrentDifficultyAt(BlockPos.containing(pos)), reason, null);
		trooper.setUp();
		return trooper;
	}

	private static Vec3 ringPosition(BlockPos at, int index, int count) {
		Vec3 centre = Vec3.atBottomCenterOf(at);

		if (index == 0 || count <= 1) {
			return centre;
		}

		double angle = 2 * Math.PI * (index - 1) / Math.max(1, count - 1);
		return centre.add(Math.cos(angle) * SPAWN_RING, 0, Math.sin(angle) * SPAWN_RING);
	}

	/** Moves an entity that would spawn inside a wall back onto the spawn point. */
	private static void placeClear(ServerLevel l, Entity entity, BlockPos at) {
		if (!l.noCollision(entity, entity.getBoundingBox())) {
			Vec3 centre = Vec3.atBottomCenterOf(at);
			entity.snapTo(centre.x, centre.y, centre.z, entity.getYRot(), 0f);
		}
	}

	private Troopers() {
	}
}
