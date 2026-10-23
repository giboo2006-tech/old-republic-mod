package com.oldrepublic.client.space;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.space.BlockadeGunship;
import com.oldrepublic.space.HymnInterceptor;
import com.oldrepublic.space.Starfighter;
import com.oldrepublic.space.network.ShipCombatStatusPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Space's loops and when they stop (spec MILESTONE_5.md 15.4):
 *
 * <ul>
 * <li>the fighters' engines ({@code space.fighter.engine}, the Hymn interceptor's singing
 * {@code space.hymn_interceptor.engine}) and the gunship's {@code space.gunship.hum}: bound to their
 * entity, started for the nearest few within earshot, gone with the entity, out of earshot or with
 * the level;</li>
 * <li>the lock tone ({@code space.missile.lock_tone}): from the lock's start, rising with its
 * progress, steady and higher once locked, until it is lost or fired;</li>
 * <li>the missile warning ({@code space.missile.warning}): from the first missile locking on or
 * flying at the ship to the last one's end;</li>
 * <li>the scan ({@code space.scan}): while a patrol scans the ship, until it breaks or completes.</li>
 * </ul>
 * The cockpit tones start and stop on their events; each also has a watchdog on the seat's status
 * (a lost event, the role's end), and every loop stops on a dimension change and on disconnect.
 */
final class SpaceLoops {
	/** Fighter engines heard at once, nearest first, and how far they carry. */
	private static final int MAX_ENGINES = 6;
	private static final double ENGINE_RANGE = 56;
	private static final double HUM_RANGE = 96;
	private static final int SCAN_INTERVAL = 10;
	/** Ticks a tone outlives its last event while the status has not caught up (it lags a tick or two). */
	private static final int GRACE_TICKS = 10;
	private static final float LOCK_VOLUME = 0.55f;

	private static final Map<Integer, SpaceLoop> ENGINES = new HashMap<>();
	private static @Nullable SpaceLoop hum;
	private static @Nullable SpaceLoop lockTone;
	private static @Nullable SpaceLoop warning;
	private static @Nullable SpaceLoop scan;
	private static boolean locked;
	private static long lockSince;
	private static boolean incoming;
	private static long incomingSince;
	private static boolean scanning;
	private static long scanSince;

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;

		if (level == null || player == null) {
			clear();
			return;
		}

		if (SpaceClientState.ticks() % SCAN_INTERVAL == 0) {
			engines(level, player);
		}

		ENGINES.values().removeIf(SpaceLoop::finished);

		if (lockTone != null && !lockTone.finished()) {
			lockTone.setTarget(LOCK_VOLUME, locked ? 1.6f : 0.8f + 0.6f * SpaceClientState.status().lockProgress());
		}
	}

	// ---------------------------------------------------------------- engines

	private static void engines(ClientLevel level, LocalPlayer player) {
		Vec3 at = player.position();
		AABB box = new AABB(at, at).inflate(HUM_RANGE);

		if (hum == null || hum.finished()) {
			hum = null;

			for (BlockadeGunship gunship : level.getEntitiesOfClass(BlockadeGunship.class, box, Mob::isAlive)) {
				hum = SpaceLoop.play(ModSounds.SPACE_GUNSHIP_HUM, SoundSource.HOSTILE, gunship, 5f, 1f, 30, near(gunship, player, HUM_RANGE + 16));
				break;
			}
		}

		List<Starfighter> heard = level.getEntitiesOfClass(Starfighter.class, box.deflate(HUM_RANGE - ENGINE_RANGE),
				e -> e.isAlive() && !ENGINES.containsKey(e.getId()) && e.distanceToSqr(at) < ENGINE_RANGE * ENGINE_RANGE);
		heard.sort(Comparator.comparingDouble(e -> e.distanceToSqr(at)));

		for (Starfighter fighter : heard) {
			if (ENGINES.size() >= MAX_ENGINES) {
				return;
			}

			// Each fighter sings a little apart from its wingmen.
			float pitch = 0.94f + Math.floorMod(fighter.getId(), 7) * 0.02f;
			BooleanSupplier keep = near(fighter, player, ENGINE_RANGE + 16);
			ENGINES.put(fighter.getId(), fighter instanceof HymnInterceptor
					? SpaceLoop.play(ModSounds.SPACE_HYMN_INTERCEPTOR_ENGINE, SoundSource.HOSTILE, fighter, 3f, pitch, 12, keep)
					: SpaceLoop.play(ModSounds.SPACE_FIGHTER_ENGINE, SoundSource.HOSTILE, fighter, 2.5f, pitch, 12, keep));
		}
	}

	/** Holds while {@code mob} lives within {@code range} of {@code player}. */
	private static BooleanSupplier near(Mob mob, LocalPlayer player, double range) {
		return () -> mob.isAlive() && !player.isRemoved() && mob.distanceToSqr(player) < range * range;
	}

	// ---------------------------------------------------------------- the cockpit tones (from SpaceFx)

	/** {@code missile_lock} locking: the rising tone starts. */
	static void lockStarted() {
		locked = false;
		lockSince = SpaceClientState.ticks();

		if (lockTone == null || lockTone.finished()) {
			lockTone = SpaceLoop.play(ModSounds.SPACE_MISSILE_LOCK_TONE, SoundSource.PLAYERS, null, LOCK_VOLUME, 0.8f, 3,
					() -> SpaceClientState.status().lockTarget() >= 0 || SpaceClientState.ticks() - lockSince < GRACE_TICKS);
		}
	}

	/** {@code missile_lock} locked: the tone holds steady, higher. */
	static void lockHeld() {
		lockStarted();
		locked = true;
	}

	/** {@code missile_lock} lost, or the missile fired. */
	static void lockEnded() {
		locked = false;

		if (lockTone != null) {
			lockTone.release();
			lockTone = null;
		}
	}

	/** Whether the lock is held (the HUD's ring turns red). */
	static boolean locked() {
		return locked && lockTone != null && !lockTone.finished();
	}

	/** {@code missile_lock} incoming: the warning starts. */
	static void incomingStarted() {
		incoming = true;
		incomingSince = SpaceClientState.ticks();

		if (warning == null || warning.finished()) {
			warning = SpaceLoop.play(ModSounds.SPACE_MISSILE_WARNING, SoundSource.PLAYERS, null, 0.7f, 1f, 2, SpaceLoops::incoming);
		}
	}

	/** {@code missile_lock} incoming end: the warning stops. */
	static void incomingEnded() {
		incoming = false;

		if (warning != null) {
			warning.release();
			warning = null;
		}
	}

	/** Whether an incoming missile is announced: its event came, and the status agrees (or has not had time to). */
	static boolean incoming() {
		ShipCombatStatusPayload status = SpaceClientState.status();
		return incoming && (status.incoming() || SpaceClientState.ticks() - incomingSince < GRACE_TICKS);
	}

	/** {@code space_scan} scanning: the scan loop starts. */
	static void scanStarted() {
		scanning = true;
		scanSince = SpaceClientState.ticks();

		if (scan == null || scan.finished()) {
			scan = SpaceLoop.play(ModSounds.SPACE_SCAN, SoundSource.PLAYERS, null, 0.5f, 1f, 6,
					() -> scanning && (SpaceClientState.status().scan() >= 0f || SpaceClientState.ticks() - scanSince < GRACE_TICKS));
		}
	}

	/** {@code space_scan} broken or complete: the scan loop stops. */
	static void scanEnded() {
		scanning = false;

		if (scan != null) {
			scan.release();
			scan = null;
		}
	}

	/** Every loop stops now: no level, or a disconnect. */
	static void clear() {
		ENGINES.values().forEach(SpaceLoop::release);
		ENGINES.clear();

		if (hum != null) {
			hum.release();
			hum = null;
		}

		lockEnded();
		incomingEnded();
		scanEnded();
	}

	private SpaceLoops() {
	}
}
