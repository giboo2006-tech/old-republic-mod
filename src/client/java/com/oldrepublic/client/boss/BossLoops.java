package com.oldrepublic.client.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.OrrenVasch;
import com.oldrepublic.boss.block.PylonCrystal;
import com.oldrepublic.boss.block.ResonancePylonBlock;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The fight's two hum loops (spec 8.5, 13.3, 16): {@code boss.collector.ward_hum} on Orren Vasch while
 * his Resonance Ward holds, and {@code pylon.sing} on each singing pylon (at most {@value #MAX_PYLON_LOOPS}
 * at once). Both are positional ({@code LINEAR} attenuation) and stop on every path:
 *
 * <ul>
 * <li>the ward hum: Unsung or the last pylon silenced ({@code boss_ward} level 0), the yield (he
 * kneels; the drone takes over), leaving phase 2 (the second transition), his removal or death, the
 * {@code boss_phase} yield, reset, victory and music-stop events, and a watchdog: no ward event for
 * {@value #WATCHDOG_TICKS} ticks while no known pylon still sings;</li>
 * <li>a pylon's song: its crystal no longer {@code SINGING} in the synced block state (silenced, ripped,
 * regrowing, shattered, reset; this is also the watchdog for a lost event), the silence and shatter
 * events;</li>
 * <li>both: the local player's death, leaving the area (farther than {@value #AREA_RANGE} blocks, or the
 * participant's {@code boss_phase} music stop, after which loops stay off until the next phase sync),
 * a level or dimension change, and disconnect.</li>
 * </ul>
 * Coming back (within range again, or the phase sync after a music stop) restarts both: the hum from
 * the ward level, and a song for every known pylon whose synced crystal still sings.
 */
final class BossLoops {
	/** {@code pylon.sing} loops at most (13.3). */
	static final int MAX_PYLON_LOOPS = 4;
	/** Beyond this distance from the player a loop stops (leaving the area). */
	private static final double AREA_RANGE = 48.0;
	/** Ticks without a ward event, while no known pylon sings, before the ward hum's watchdog ends it. */
	private static final int WATCHDOG_TICKS = 200;
	private static final float WARD_VOLUME = 0.55f;
	private static final float SING_VOLUME = 0.6f;

	/** The ward level (pylons singing, 0-4) last sent for each Vasch, by entity id. */
	private static final Map<Integer, Integer> WARD = new HashMap<>();
	/** The client tick the ward level of each Vasch was last sent. */
	private static final Map<Integer, Long> WARD_SEEN = new HashMap<>();
	private static final Map<Integer, WardHum> HUMS = new HashMap<>();
	private static final Map<BlockPos, PylonSing> SINGS = new HashMap<>();
	/** Every pylon a {@code pylon_state} event has named in this level (the currents and the watchdogs read their block state). */
	private static final Set<BlockPos> PYLONS = new HashSet<>();
	/** The participant left the hall ({@code boss_phase} 6): no loops until the next phase sync. */
	private static boolean away;
	private static @Nullable ClientLevel lastLevel;
	private static long ticks;

	static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(BossLoops::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	// ---------------------------------------------------------------- events (from BossFx)

	/** {@code boss_ward} variant 0: {@code level} pylons sing (0 drops the ward). */
	static void ward(int vaschId, int level) {
		WARD.put(vaschId, Math.max(0, level));
		WARD_SEEN.put(vaschId, ticks);

		if (level <= 0) {
			stopHum(vaschId);
		}
	}

	/** Unsung, the yield, or he is gone: the ward hum stops now. */
	static void wardDown(int vaschId) {
		WARD.put(vaschId, 0);
		WARD_SEEN.put(vaschId, ticks);
		stopHum(vaschId);
	}

	/** The ward level last sent for {@code vaschId}, 0 when none. */
	static int wardLevel(int vaschId) {
		return away ? 0 : WARD.getOrDefault(vaschId, 0);
	}

	/** A pylon was named by an event: remember it for the currents and the watchdogs. */
	static void notePylon(BlockPos pylon) {
		PYLONS.add(pylon.immutable());
	}

	/** {@code pylon_state} sing or re-sung: starts its song if there is room and the player is near. */
	static void pylonSings(Minecraft mc, BlockPos pylon) {
		notePylon(pylon);
		PylonSing playing = SINGS.get(pylon);

		if (playing != null && !playing.isStopped() || away || SINGS.size() >= MAX_PYLON_LOOPS || mc.player == null) {
			return;
		}

		if (mc.player.position().distanceToSqr(Vec3.atCenterOf(pylon)) > AREA_RANGE * AREA_RANGE) {
			return;
		}

		PylonSing sing = new PylonSing(pylon);
		SINGS.put(pylon.immutable(), sing);
		mc.getSoundManager().play(sing);
	}

	/** {@code pylon_state} silenced or shattered: its song stops now. */
	static void pylonQuiet(BlockPos pylon) {
		PylonSing sing = SINGS.remove(pylon);

		if (sing != null) {
			sing.end();
		}
	}

	/** {@code boss_phase} for this participant: a phase (re)sync lets loops play; yield, reset, victory and music stop end them. */
	static void phase(int variant) {
		if (variant <= 2) {
			away = false;
			return;
		}

		stopAll();
		// Yield: his ward is over (the second transition already ended it); reset and victory: the fight is.
		WARD.replaceAll((id, level) -> 0);
		away = variant == 6;
	}

	/** Whether {@code pylon}'s crystal sings in the synced block state. */
	static boolean singing(ClientLevel level, BlockPos pylon) {
		BlockState state = level.getBlockState(pylon);
		return state.getBlock() instanceof ResonancePylonBlock && state.getValue(ResonancePylonBlock.CRYSTAL) == PylonCrystal.SINGING;
	}

	/** The known pylons whose crystal sings, within {@code range} of {@code near}. */
	static List<BlockPos> singingPylons(ClientLevel level, Vec3 near, double range) {
		List<BlockPos> singing = new ArrayList<>();

		for (BlockPos pylon : PYLONS) {
			if (Vec3.atCenterOf(pylon).distanceToSqr(near) <= range * range && singing(level, pylon)) {
				singing.add(pylon);
			}
		}

		return singing;
	}

	/** Every known pylon within {@code range} of {@code near}. */
	static List<BlockPos> pylonsNear(Vec3 near, double range) {
		List<BlockPos> found = new ArrayList<>();

		for (BlockPos pylon : PYLONS) {
			if (Vec3.atCenterOf(pylon).distanceToSqr(near) <= range * range) {
				found.add(pylon);
			}
		}

		return found;
	}

	// ---------------------------------------------------------------- tick and stops

	private static void tick(Minecraft mc) {
		ticks++;
		ClientLevel level = mc.level;

		if (level != lastLevel) {
			// Join, dimension change or leaving the world.
			lastLevel = level;
			reset();
		}

		if (level == null || mc.player == null) {
			return;
		}

		if (!mc.player.isAlive()) {
			stopAll();
			return;
		}

		SINGS.values().removeIf(PylonSing::isStopped);
		HUMS.values().removeIf(WardHum::isStopped);

		if (!away) {
			// Back in range, or back in the hall after a music stop: the crystals that still sing are heard again.
			for (BlockPos pylon : singingPylons(level, mc.player.position(), AREA_RANGE)) {
				pylonSings(mc, pylon);
			}
		}

		for (Iterator<Map.Entry<Integer, Integer>> it = WARD.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Integer> entry = it.next();
			int id = entry.getKey();
			Entity entity = level.getEntity(id);

			if (!(entity instanceof OrrenVasch vasch) || vasch.isRemoved() || !vasch.isAlive()) {
				stopHum(id);
				it.remove();
				WARD_SEEN.remove(id);
				continue;
			}

			// The watchdog: a lost "ward down" event while no known pylon still sings near him.
			if (entry.getValue() > 0 && ticks - WARD_SEEN.getOrDefault(id, ticks) > WATCHDOG_TICKS
					&& singingPylons(level, vasch.position(), 32.0).isEmpty()) {
				entry.setValue(0);
			}

			if (humAllowed(mc, vasch) && entry.getValue() > 0) {
				WardHum hum = HUMS.get(id);

				if (hum == null || hum.isStopped()) {
					hum = new WardHum(vasch);
					HUMS.put(id, hum);
					mc.getSoundManager().play(hum);
				}
			} else {
				stopHum(id);
			}
		}
	}

	/** The ward hum may play: phase 2, standing, near the player, and the player still in the hall. */
	private static boolean humAllowed(Minecraft mc, OrrenVasch vasch) {
		return !away && vasch.phase() == 2 && !vasch.isKneeling() && mc.player != null
				&& mc.player.distanceToSqr(vasch) <= AREA_RANGE * AREA_RANGE;
	}

	private static void stopHum(int vaschId) {
		WardHum hum = HUMS.remove(vaschId);

		if (hum != null) {
			hum.end();
		}
	}

	/** Stops every loop now (the ward levels and the known pylons are kept). */
	static void stopAll() {
		HUMS.values().forEach(WardHum::end);
		HUMS.clear();
		SINGS.values().forEach(PylonSing::end);
		SINGS.clear();
	}

	/** Stops every loop and forgets every level-bound record (level change, disconnect). */
	private static void reset() {
		stopAll();
		WARD.clear();
		WARD_SEEN.clear();
		PYLONS.clear();
		away = false;
	}

	// ---------------------------------------------------------------- the sound instances

	/** The ward's low hum, following Vasch; louder with more pylons singing. */
	private static final class WardHum extends AbstractTickableSoundInstance {
		private final OrrenVasch vasch;

		WardHum(OrrenVasch vasch) {
			super(ModSounds.BOSS_COLLECTOR_WARD_HUM, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
			this.vasch = vasch;
			this.looping = true;
			this.delay = 0;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.volume = volumeFor(wardLevel(vasch.getId()));
			follow();
		}

		@Override
		public void tick() {
			Minecraft mc = Minecraft.getInstance();
			int level = wardLevel(vasch.getId());

			if (vasch.isRemoved() || !vasch.isAlive() || vasch.level() != mc.level || level <= 0 || !humAllowed(mc, vasch)) {
				stop();
				return;
			}

			this.volume = volumeFor(level);
			follow();
		}

		void end() {
			stop();
		}

		private void follow() {
			this.x = vasch.getX();
			this.y = vasch.getY() + vasch.getBbHeight() * 0.6;
			this.z = vasch.getZ();
		}

		private static float volumeFor(int level) {
			return WARD_VOLUME * (0.6f + 0.1f * Math.min(4, level)) * ClientConfig.get().modSoundVolume;
		}
	}

	/** One singing pylon's crystal song, at the crystal; ends with its {@code SINGING} block state. */
	private static final class PylonSing extends AbstractTickableSoundInstance {
		private final BlockPos pylon;

		PylonSing(BlockPos pylon) {
			super(ModSounds.PYLON_SING, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
			this.pylon = pylon.immutable();
			this.looping = true;
			this.delay = 0;
			this.attenuation = SoundInstance.Attenuation.LINEAR;
			this.volume = SING_VOLUME * ClientConfig.get().modSoundVolume;
			// A little pitch spread so four pylons never beat in unison.
			this.pitch = 0.94f + (Math.floorMod(pylon.hashCode(), 7)) * 0.02f;
			this.x = pylon.getX() + 0.5;
			this.y = pylon.getY() + 1.3;
			this.z = pylon.getZ() + 0.5;
		}

		@Override
		public void tick() {
			Minecraft mc = Minecraft.getInstance();

			if (mc.level == null || mc.player == null || away || !singing(mc.level, pylon)
					|| mc.player.position().distanceToSqr(this.x, this.y, this.z) > AREA_RANGE * AREA_RANGE) {
				stop();
				return;
			}

			this.volume = SING_VOLUME * ClientConfig.get().modSoundVolume;
		}

		void end() {
			stop();
		}
	}

	private BossLoops() {
	}
}
