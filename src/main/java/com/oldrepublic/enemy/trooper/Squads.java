package com.oldrepublic.enemy.trooper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Squad lookups and the squad's short-lived shared state. A squad is the set of loaded troopers
 * that carry the same {@code Squad} UUID; nothing reaches into unloaded chunks. The shared state
 * (last bark, warnings, salutes, the standoff, the encounter's fall and flare, and the fire-sound
 * window) lives in memory on the server, keyed by squad UUID (or a lone trooper's own UUID), and is
 * forgotten when the server stops or the squad has been quiet for a long time. The fall and flare
 * are also forgotten when the encounter ends ({@link #encounterTick}).
 */
public final class Squads {
	/** How far around a member the rest of its squad is looked for. */
	public static final double SQUAD_RADIUS = 48;
	/** Shared state untouched this long is dropped. */
	private static final long FORGET_TICKS = 12000;

	/** In-memory state shared by one squad. Server only. */
	static final class State {
		/** The squad's last bark (one per {@code barkSquadTicks}). */
		long lastBark = TrooperRules.NEVER;
		/** Player to the game time of the last Wary warning. */
		final Map<UUID, Long> warned = new HashMap<>();
		/** Player to consecutive ticks spent within {@code waryRange} after a warning. */
		final Map<UUID, Integer> waryTicks = new HashMap<>();
		/** Player to the game time of the last salute or "move along". */
		final Map<UUID, Long> greeted = new HashMap<>();
		/** A member fell this encounter: the Imperial sergeant may call reinforcements. */
		boolean memberFell;
		/** The flare was fired this encounter. */
		boolean flareUsed;
		/** Game time a member last fought, fell or fired the flare: the encounter ends {@code encounterEndTicks} later. */
		long lastCombat = TrooperRules.NEVER;
		/** The cold-peace standoff with a rival patrol, and where the nearest rival stood at the last check. */
		ColdPeace.Standoff standoff = ColdPeace.Standoff.NONE;
		@Nullable Vec3 rival;
		/** The fire-sound window: its index ({@code gameTime / FIRE_SOUND_WINDOW}) and sounds used in it. */
		long fireWindow = TrooperRules.NEVER;
		int fireSounds;
		long touched;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	/** The key a trooper's shared state is kept under: its squad, or itself when it has none. */
	public static UUID key(TrooperEntity trooper) {
		UUID squad = trooper.squad();
		return squad != null ? squad : trooper.getUUID();
	}

	static State state(TrooperEntity trooper) {
		return state(key(trooper), trooper.level().getGameTime());
	}

	static State state(UUID key, long now) {
		State state = STATES.computeIfAbsent(key, k -> new State());
		state.touched = now;
		return state;
	}

	/**
	 * The loaded, living members of {@code squad} within {@code radius} of {@code around}, downed
	 * ones included. Empty when {@code squad} is null.
	 */
	public static List<TrooperEntity> members(ServerLevel level, @Nullable UUID squad, Vec3 around, double radius) {
		if (squad == null) {
			return List.of();
		}

		return level.getEntitiesOfClass(TrooperEntity.class, new AABB(around, around).inflate(radius),
				t -> t.isAlive() && squad.equals(t.squad()));
	}

	/** {@code trooper}'s squad around it (itself included), or just itself when it has none. */
	public static List<TrooperEntity> squadOf(TrooperEntity trooper) {
		if (trooper.squad() == null || !(trooper.level() instanceof ServerLevel level)) {
			return List.of(trooper);
		}

		List<TrooperEntity> members = members(level, trooper.squad(), trooper.position(), SQUAD_RADIUS);
		return members.isEmpty() ? List.of(trooper) : members;
	}

	/** Members that can fight: alive, not downed, not yielded, not an escort or officer. */
	public static List<TrooperEntity> fighters(TrooperEntity trooper) {
		List<TrooperEntity> fighters = new ArrayList<>();

		for (TrooperEntity member : squadOf(trooper)) {
			if (member.canFight()) {
				fighters.add(member);
			}
		}

		return fighters;
	}

	/**
	 * The squad's leader: its living sergeant, otherwise the standing member with the lowest UUID (so
	 * every member agrees without talking). The trooper itself when it has no squad.
	 */
	public static TrooperEntity leader(TrooperEntity trooper) {
		TrooperEntity best = null;

		for (TrooperEntity member : squadOf(trooper)) {
			if (member.isDowned() || member.isYielded()) {
				continue;
			}

			if (best == null || leaderOrder().compare(member, best) < 0) {
				best = member;
			}
		}

		return best != null ? best : trooper;
	}

	private static Comparator<TrooperEntity> leaderOrder() {
		return Comparator.comparing((TrooperEntity t) -> t.role() != TrooperRole.SERGEANT).thenComparing(LivingEntity::getUUID);
	}

	/** Whether the squad still has a living, standing sergeant nearby. */
	public static boolean sergeantStanding(TrooperEntity trooper) {
		for (TrooperEntity member : squadOf(trooper)) {
			if (member.role() == TrooperRole.SERGEANT && !member.isDowned()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Claims one of the squad's fire sounds for a shot fired now: true for the first
	 * {@link TrooperRules#FIRE_SOUNDS_PER_WINDOW} shots of each {@link TrooperRules#FIRE_SOUND_WINDOW}-tick
	 * window, then false (the shot is sent with intensity 0: flash only).
	 */
	public static boolean claimFireSound(TrooperEntity shooter) {
		State state = state(shooter);
		long window = shooter.level().getGameTime() / TrooperRules.FIRE_SOUND_WINDOW;

		if (state.fireWindow != window) {
			state.fireWindow = window;
			state.fireSounds = 0;
		}

		if (state.fireSounds >= TrooperRules.FIRE_SOUNDS_PER_WINDOW) {
			return false;
		}

		state.fireSounds++;
		return true;
	}

	/**
	 * Game time of the Wary warning {@code trooper}'s squad last gave {@code player}, or null when it
	 * has not warned them (the countdown to opening fire starts here).
	 */
	public static @Nullable Long warnedAt(TrooperEntity trooper, UUID player) {
		return state(trooper).warned.get(player);
	}

	/**
	 * A member of {@code trooper}'s squad fell in a fight: the Imperial sergeant may now call for
	 * reinforcements. The fall is part of the encounter, so it is forgotten with it.
	 */
	static void memberFell(TrooperEntity trooper) {
		State state = state(trooper);
		state.memberFell = true;
		state.lastCombat = trooper.level().getGameTime();
	}

	/** Whether any standing member of {@code trooper}'s squad has a target. */
	public static boolean fighting(TrooperEntity trooper) {
		for (TrooperEntity member : fighters(trooper)) {
			if (member.getTarget() != null) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Keeps the squad's encounter (spec 7.3: the flare is once per encounter), run by the leader's
	 * social tick: while a member fights the encounter goes on; once {@code encounterEndTicks} pass
	 * with no fight, fall or flare, it is over and the fallen member and the used flare are forgotten.
	 * Returns true on the check that ends the encounter.
	 */
	static boolean encounterTick(State state, boolean inCombat, long now, TrooperBalance b) {
		if (inCombat) {
			state.lastCombat = now;
			return false;
		}

		if (!TrooperRules.encounterOver(state.lastCombat, now, b.encounterEndTicks)) {
			return false;
		}

		state.memberFell = false;
		state.flareUsed = false;
		state.lastCombat = TrooperRules.NEVER;
		return true;
	}

	/** Game time {@code trooper}'s squad last fought, lost a member or fired its flare; {@link TrooperRules#NEVER} if not this encounter. */
	public static long lastCombat(TrooperEntity trooper) {
		return state(trooper).lastCombat;
	}

	/** Where {@code trooper}'s squad is in a standoff with a rival patrol (spec 7.5). */
	public static ColdPeace.Phase standoffPhase(TrooperEntity trooper) {
		return state(trooper).standoff.phase();
	}

	/** The rival trooper nearest the squad at the last standoff check, or null. */
	public static @Nullable Vec3 standoffRival(TrooperEntity trooper) {
		return state(trooper).rival;
	}

	/** The Imperial flare may go up: a member fell this encounter and the flare was not fired yet. */
	public static boolean flareReady(TrooperEntity trooper) {
		State state = state(trooper);
		return state.memberFell && !state.flareUsed;
	}

	/** The flare went up: once per encounter. */
	public static void markFlareUsed(TrooperEntity trooper) {
		State state = state(trooper);
		state.flareUsed = true;
		state.lastCombat = trooper.level().getGameTime();
	}

	/** Shares {@code target} with every member of the squad that has none, so the squad fights together. */
	public static void shareTarget(TrooperEntity trooper, LivingEntity target) {
		for (TrooperEntity member : squadOf(trooper)) {
			if (member != trooper && member.canFight() && member.getTarget() == null && member.wants(target)) {
				member.setTarget(target);
			}
		}
	}

	/** Forgets squads untouched for a long time. Called from the server tick now and then. */
	static void prune(long now) {
		STATES.values().removeIf(state -> now - state.touched > FORGET_TICKS);
	}

	/** Forgets everything (server stopped). */
	static void clear() {
		STATES.clear();
	}

	private Squads() {
	}
}
