package com.oldrepublic.client.planet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.VerseVision;
import com.oldrepublic.client.world.FadingLoop;
import com.oldrepublic.client.world.site.SiteClient;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.planet.PlanetBalance;
import com.oldrepublic.planet.PlanetParticles;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.planet.block.PlanetBlockAmbience;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;
import com.oldrepublic.world.block.CrystalFormationBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Client ambience of the planets (spec MILESTONE_4.md 3.1 to 3.3, 13.1, 13.4; MILESTONE_5.md 3.2,
 * 3.3, 15.5):
 * <ul>
 * <li><b>Biome beds</b>: one non-positional {@link FadingLoop} per planet biome
 * ({@code ambient.selvaren.*}, {@code ambient.varkhast.*}, {@code ambient.ghessar.*},
 * {@code ambient.tyrvann.*}), crossfaded over four seconds as the player crosses biomes, ducked
 * under cover, under a site's own bed and while a verse vision plays; released on death, stopped
 * with the level on a dimension change and killed on disconnect. Biome JSON ambient loops are not
 * used because their 26.2 fields are unverified (this emitter is the documented fallback).</li>
 * <li><b>Ashfall</b>: grey flakes drifting on a slowly turning wind around the camera on Varkhast,
 * 40 visible at Standard in the ashfall biome, twice that during the planet's storm (the level's
 * rain flag), and a sparse fall over the other Varkhast biomes during the storm only. Flakes spawn
 * only under open sky and go through the {@link Vfx} budget.</li>
 * <li><b>Corrupted stones</b>: the {@code waystone.corrupt} loop and the dark-vein motes (6 per
 * second within 8 blocks, pulsing every 2 seconds) of each corrupted waystone or Heartstone the
 * server says is corrupted for this player. Each refresh keeps it alive for the event's intensity in
 * seconds, so leaving 16 blocks (no more refreshes), the local player's own cleanse, death,
 * disconnect and a level change all stop it.</li>
 * <li><b>Block display ticks</b> ({@link PlanetBlockAmbience.Listener}):
 * duskglass slivers and dull red sparks, aurelite glints, the clean waystone's rare hum and pale
 * mote (more often at night), the Heartstone's slow dark mist while a corrupt refresh names it,
 * sunstone's heat shimmer (by day), static crawling over stormglass, and the verse stones'
 * motes and hum ({@link VerseStoneClient}).</li>
 * <li><b>Timed effects</b> started by the planet effect events (the cleanse draining away, a
 * Heartstone wave, the Heartstone's exhale, the Hollow answering, the rift crystal bleeding).</li>
 * </ul>
 * Beds, ashfall and block ambience follow {@code ClientConfig.planetAmbience}; the effect-driven
 * loops and particles always play, since they show which stones are corrupted.
 */
final class PlanetAmbience implements PlanetBlockAmbience.Listener {
	static final PlanetAmbience INSTANCE = new PlanetAmbience();

	private static final int BED_FADE_TICKS = 80;
	private static final float BED_VOLUME = 0.55f;
	/** The biome bed's volume factor under cover (caves, the tombs) and under a site's bed. */
	private static final float BED_DUCK_COVERED = 0.35f;
	private static final float BED_DUCK_SITE = 0.4f;
	/** The biome bed's volume factor while a verse vision plays (MILESTONE_5.md 6.6, 15.3). */
	private static final float BED_DUCK_VISION = 0.3f;
	/** Ashfall flakes started per tick at Standard: 40 alive at a mean life of 120 ticks. */
	private static final float ASH_PER_TICK = 40f / 120f;
	private static final int ASH_REACH = 18;
	/** Dark-vein motes show within this many blocks of a corrupted stone (13.1). */
	private static final double VEIN_RANGE = 8;
	/** A corrupt loop is released at once beyond this distance, without waiting for its keep-alive. */
	private static final double CORRUPT_DROP_RANGE = 20;
	/** The vein pulse: one pulse of {@link #PULSE_ON} ticks every {@link #PULSE_PERIOD}. */
	private static final int PULSE_PERIOD = 40;
	private static final int PULSE_ON = 12;
	private static final int VEIN_COLOR = 0x2A1220;
	private static final int MIST_COLOR = 0x1E1418;
	private static final int BLEED_COLOR = 0x8C1414;
	private static final Direction[] HORIZONTAL = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

	private final Map<BlockPos, Corrupt> corrupt = new HashMap<>();
	private final List<Timed> timed = new ArrayList<>();
	private @Nullable FadingLoop bed;
	private @Nullable SoundEvent bedSound;
	private float ashCarry;
	/** The rift's whisper while a bleed is held, stopped when the hold breaks. */
	private @Nullable SimpleSoundInstance whisper;
	private long ticks;
	private @Nullable Object lastLevel;

	/** A stone the server says is corrupted for this player: its loop and when it lapses. */
	private static final class Corrupt {
		final Vec3 centre;
		final FadingLoop loop;
		long expires;

		Corrupt(Vec3 centre, FadingLoop loop, long expires) {
			this.centre = centre;
			this.loop = loop;
			this.expires = expires;
		}
	}

	/**
	 * An effect that keeps spawning for a while. {@code owner} is the entity id of the player it is
	 * for (-1: none); {@code data} the effect's strength (the wave, the bleed's hold in ticks);
	 * {@code points} the crystal faces of the Hollow answering.
	 */
	record Timed(Kind kind, Vec3 pos, int owner, int duration, float data, List<Vec3> points, int[] age) {
		enum Kind { CLEANSE_DRAIN, HEART_WAVE, HEART_EXHALE, ANSWER, BLEED }
	}

	private PlanetAmbience() {
	}

	// ---- server-driven state ----

	/** A corrupt refresh for the stone at {@code centre}: (re)starts its loop and keeps it for {@code seconds}. */
	void corrupt(ClientLevel level, Vec3 centre, float seconds) {
		BlockPos key = BlockPos.containing(centre);
		Corrupt entry = corrupt.get(key);
		long expires = ticks + Math.max(20, Math.round(seconds * 20));

		if (entry != null && !entry.loop.isStopped() && !entry.loop.isReleased()) {
			entry.expires = expires;
			return;
		}

		corrupt.put(key, new Corrupt(centre, FadingLoop.play(ModSounds.WAYSTONE_CORRUPT, centre, 0.9f, 20), expires));
	}

	/** The local player cleansed the stone at {@code centre}: its loop fades and the veins stop. */
	void cleansed(Vec3 centre) {
		Iterator<Corrupt> it = corrupt.values().iterator();

		while (it.hasNext()) {
			Corrupt entry = it.next();

			if (entry.centre.distanceToSqr(centre) < 2.25) {
				entry.loop.release();
				it.remove();
			}
		}
	}

	/** Whether a corrupt refresh names a stone within {@code radius} of {@code pos}. */
	boolean corruptNear(Vec3 pos, double radius) {
		for (Corrupt entry : corrupt.values()) {
			if (entry.centre.distanceToSqr(pos) <= radius * radius) {
				return true;
			}
		}

		return false;
	}

	void addTimed(Timed.Kind kind, Vec3 pos, int owner, int duration, float data, List<Vec3> points) {
		clearTimed(kind, pos);
		timed.add(new Timed(kind, pos, owner, duration, data, points, new int[1]));
	}

	void clearTimed(Timed.Kind kind, Vec3 pos) {
		timed.removeIf(t -> t.kind() == kind && t.pos().distanceToSqr(pos) < 2.25);
	}

	/** Ends every effect of {@code kind} for the player with entity id {@code owner}. */
	void clearOwned(Timed.Kind kind, int owner) {
		timed.removeIf(t -> t.kind() == kind && t.owner() == owner);
	}

	// ---- client tick ----

	void tick(Minecraft client) {
		ClientLevel level = client.level;

		if (level != lastLevel) {
			reset();
			lastLevel = level;
		}

		LocalPlayer player = client.player;

		if (level == null || player == null) {
			return;
		}

		ticks++;
		boolean alive = !player.isDeadOrDying();
		tickCorrupt(level, player, alive);
		tickTimed(level);
		Planets.Planet planet = Planets.Planet.of(level.dimension());
		boolean onPlanet = planet != null && planet != Planets.Planet.HOME;
		boolean ambience = onPlanet && alive && ClientConfig.get().planetAmbience;
		updateBed(level, player, ambience);

		if (ambience && planet == Planets.Planet.VARKHAST) {
			ashfall(client, level);
		}
	}

	/** Disconnect or level change: every loop stops at once and all state is forgotten. */
	void reset() {
		for (Corrupt entry : corrupt.values()) {
			entry.loop.kill();
		}

		corrupt.clear();
		timed.clear();

		if (bed != null) {
			bed.kill();
		}

		bed = null;
		bedSound = null;
		ashCarry = 0;
		stopWhisper();
	}

	/** Plays the rift's whisper at {@code pos}, cutting off the one before it. */
	void whisper(ClientLevel level, Vec3 pos, float volume) {
		stopWhisper();
		float scaled = volume * ClientConfig.get().modSoundVolume;

		if (scaled > 0.001f) {
			whisper = new SimpleSoundInstance(ModSounds.RIFT_WHISPER, SoundSource.AMBIENT, scaled, 0.95f + level.getRandom().nextFloat() * 0.1f,
					level.getRandom(), pos.x, pos.y, pos.z);
			Minecraft.getInstance().getSoundManager().play(whisper);
		}
	}

	/** The hold broke (or the level went): the whisper stops at once. */
	void stopWhisper() {
		if (whisper != null) {
			Minecraft.getInstance().getSoundManager().stop(whisper);
			whisper = null;
		}
	}

	private void tickCorrupt(ClientLevel level, LocalPlayer player, boolean alive) {
		Iterator<Corrupt> it = corrupt.values().iterator();
		RandomSource random = level.getRandom();

		while (it.hasNext()) {
			Corrupt entry = it.next();
			double distance = player.position().distanceTo(entry.centre);

			if (!alive || entry.expires <= ticks || entry.loop.isStopped() || distance > CORRUPT_DROP_RANGE) {
				entry.loop.release();
				it.remove();
				continue;
			}

			if (distance <= VEIN_RANGE && ticks % PULSE_PERIOD < PULSE_ON) {
				repeat(random, 1, () -> veinMote(level, entry.centre, random));
			}
		}
	}

	/** A dark mote seeping from an open face of the stone's column (the stone, the course above and the base). */
	private static void veinMote(ClientLevel level, Vec3 centre, RandomSource random) {
		BlockPos cell = BlockPos.containing(centre).above(random.nextInt(4) - 1);
		Direction face = HORIZONTAL[random.nextInt(4)];

		if (level.getBlockState(cell).isAir() || !level.getBlockState(cell.relative(face)).isAir()) {
			return;
		}

		Vec3 out = new Vec3(face.getStepX(), 0, face.getStepZ());
		Vec3 side = new Vec3(-out.z, 0, out.x);
		Vec3 at = Vec3.atCenterOf(cell).add(out.scale(0.53)).add(side.scale((random.nextDouble() - 0.5) * 0.8))
				.add(0, (random.nextDouble() - 0.5) * 0.8, 0);

		if (Vfx.inRange(at)) {
			Vec3 v = out.scale(0.006 + random.nextDouble() * 0.006).add(0, 0.004, 0);
			level.addParticle(new DustParticleOptions(VEIN_COLOR, 0.7f + random.nextFloat() * 0.4f), at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	// ---- biome beds ----

	private void updateBed(ClientLevel level, LocalPlayer player, boolean ambience) {
		SoundEvent wanted = ambience && ticks % 20 == 0 ? bedOf(level.getBiome(player.blockPosition())) : bedSound;

		if (!ambience) {
			wanted = null;
		}

		if (bed != null && (bed.isStopped() || bedSound != wanted)) {
			bed.release();
			bed = null;
			bedSound = null;
		}

		if (wanted != null && bed == null) {
			bed = FadingLoop.play(wanted, null, BED_VOLUME, BED_FADE_TICKS);
			bedSound = wanted;
		}

		if (bed != null && ticks % 10 == 0) {
			int x = player.getBlockX();
			int z = player.getBlockZ();
			boolean covered = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) > player.getBlockY() + 3;
			bed.duck(VerseVision.dipping() ? BED_DUCK_VISION : SiteClient.siteBed() ? BED_DUCK_SITE : covered ? BED_DUCK_COVERED : 1f);
		}

		if (bed != null) {
			bed.dip(SiteClient.senseDip());
		}
	}

	private static @Nullable SoundEvent bedOf(Holder<Biome> biome) {
		if (biome.is(PlanetWorldgen.SELVAREN_HIGHWOOD)) {
			return ModSounds.AMBIENT_SELVAREN_HIGHWOOD;
		} else if (biome.is(PlanetWorldgen.SELVAREN_MEADOW)) {
			return ModSounds.AMBIENT_SELVAREN_MEADOW;
		} else if (biome.is(PlanetWorldgen.SELVAREN_TARN)) {
			return ModSounds.AMBIENT_SELVAREN_TARN;
		} else if (biome.is(PlanetWorldgen.VARKHAST_REDROCK)) {
			return ModSounds.AMBIENT_VARKHAST_REDROCK;
		} else if (biome.is(PlanetWorldgen.VARKHAST_ASHFALL)) {
			return ModSounds.AMBIENT_VARKHAST_ASHFALL;
		} else if (biome.is(PlanetWorldgen.VARKHAST_TOMB_VALLEY)) {
			return ModSounds.AMBIENT_VARKHAST_TOMB_VALLEY;
		} else if (biome.is(PlanetWorldgen.GHESSAR_DUNES)) {
			return ModSounds.AMBIENT_GHESSAR_DUNES;
		} else if (biome.is(PlanetWorldgen.GHESSAR_SALTFLAT)) {
			return ModSounds.AMBIENT_GHESSAR_SALTFLAT;
		} else if (biome.is(PlanetWorldgen.GHESSAR_CANYONS)) {
			return ModSounds.AMBIENT_GHESSAR_CANYONS;
		} else if (biome.is(PlanetWorldgen.TYRVANN_STORMWOOD)) {
			return ModSounds.AMBIENT_TYRVANN_STORMWOOD;
		} else if (biome.is(PlanetWorldgen.TYRVANN_MIRE)) {
			return ModSounds.AMBIENT_TYRVANN_MIRE;
		} else if (biome.is(PlanetWorldgen.TYRVANN_BASALT)) {
			return ModSounds.AMBIENT_TYRVANN_BASALT;
		}

		return null;
	}

	// ---- ashfall ----

	/**
	 * Starts this tick's flakes: {@link #ASH_PER_TICK} at Standard around a camera in the ashfall
	 * biome, doubled in the storm, half that elsewhere (the flakes then only land over nearby
	 * ashfall, or a quarter of them anywhere on Varkhast during the storm); scaled by
	 * {@code planets.ashfallDensity}, the preset and the {@link Vfx} budget.
	 */
	private void ashfall(Minecraft client, ClientLevel level) {
		Vec3 camera = client.gameRenderer.mainCamera().position();
		boolean storm = level.isRaining();
		boolean inAshfall = level.getBiome(BlockPos.containing(camera)).is(PlanetWorldgen.VARKHAST_ASHFALL);
		ashCarry += ASH_PER_TICK * (storm ? 2f : 1f) * (inAshfall ? 1f : 0.5f) * (float) PlanetBalance.get().ashfallDensity;
		int base = (int) ashCarry;

		if (base <= 0) {
			return;
		}

		ashCarry -= base;
		RandomSource random = level.getRandom();
		// The wind turns slowly over five minutes and gusts a little each minute.
		double windAngle = (level.getGameTime() % 6000) * (Mth.TWO_PI / 6000.0);
		double windSpeed = 0.025 + (storm ? 0.02 : 0) + 0.01 * Mth.sin((level.getGameTime() % 1200) * Mth.TWO_PI / 1200f);
		Vec3 wind = new Vec3(Math.cos(windAngle) * windSpeed, 0, Math.sin(windAngle) * windSpeed);
		repeat(random, base, () -> flake(level, camera, wind, storm, random));
	}

	/** One flake under open sky near the camera: always over ashfall, over the rest of Varkhast only in the storm, sparsely. */
	private static void flake(ClientLevel level, Vec3 camera, Vec3 wind, boolean storm, RandomSource random) {
		double angle = random.nextDouble() * Mth.TWO_PI;
		double r = 1.5 + random.nextDouble() * ASH_REACH;
		// Upwind of the camera more often, so the drift carries flakes past the player.
		Vec3 at = camera.add(Math.cos(angle) * r, 2 + random.nextDouble() * 12, Math.sin(angle) * r).subtract(wind.scale(40 * random.nextDouble()));

		if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z)) > at.y) {
			return;
		}

		if (!level.getBiome(BlockPos.containing(at)).is(PlanetWorldgen.VARKHAST_ASHFALL) && (!storm || random.nextInt(4) != 0)) {
			return;
		}

		level.addParticle(PlanetParticles.ASH_FLAKE, at.x, at.y, at.z, wind.x, -0.025 - random.nextDouble() * 0.02, wind.z);
	}

	// ---- display ticks ----

	@Override
	public void duskglass(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!ClientConfig.get().planetAmbience || random.nextInt(6) != 0) {
			return;
		}

		Direction facing = state.getValue(CrystalFormationBlock.FACING);
		Vec3 out = CrystalFormationBlock.facingVector(facing);
		Vec3 tip = Vec3.atCenterOf(pos).subtract(out.scale(0.5)).add(out.scale(0.3 + random.nextDouble() * 0.3));

		if (!Vfx.inRange(tip)) {
			return;
		}

		if (random.nextInt(3) == 0) {
			// A sliver works loose and falls.
			level.addParticle(PlanetParticles.DUSKGLASS_SHARD, tip.x, tip.y, tip.z, (random.nextDouble() - 0.5) * 0.02, 0, (random.nextDouble() - 0.5) * 0.02);
		} else {
			// The dull red light deep in the glass catches for a moment.
			Vec3 at = tip.add((random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.3);
			level.addParticle(new DustParticleOptions(BLEED_COLOR, 0.5f), at.x, at.y, at.z, 0, 0, 0);
		}
	}

	@Override
	public void aurelite(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!ClientConfig.get().planetAmbience || random.nextInt(5) != 0) {
			return;
		}

		Direction face = Direction.values()[random.nextInt(6)];

		if (!level.getBlockState(pos.relative(face)).isAir()) {
			return;
		}

		Vec3 out = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
		Vec3 at = Vec3.atCenterOf(pos).add(out.scale(0.52)).add(onFace(face, random, 0.8));

		if (Vfx.inRange(at)) {
			level.addParticle(PlanetParticles.AURELITE_GLINT, at.x, at.y, at.z, 0, 0, 0);
		}
	}

	/** The clean stone's pale mote, and now and then its hum: low by day, more often at night. */
	@Override
	public void waystone(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!ClientConfig.get().planetAmbience) {
			return;
		}

		Vec3 centre = Vec3.atCenterOf(pos);

		if (random.nextInt(3) == 0 && Vfx.inRange(centre)) {
			Direction face = HORIZONTAL[random.nextInt(4)];
			Vec3 at = centre.add(face.getStepX() * 0.55, (random.nextDouble() - 0.5) * 1.6 + 0.5, face.getStepZ() * 0.55).add(onFace(face, random, 0.7));
			level.addParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, 0, 0.008 + random.nextDouble() * 0.006, 0);
		}

		boolean night = level.isDarkOutside();

		if (random.nextInt(night ? 40 : 90) == 0) {
			float volume = (night ? 0.55f : 0.35f) * ClientConfig.get().modSoundVolume;

			if (volume > 0.001f) {
				level.playLocalSound(centre.x, centre.y, centre.z, ModSounds.WAYSTONE_HUM, SoundSource.AMBIENT, volume, 0.92f + random.nextFloat() * 0.12f, false);
			}
		}
	}

	/** Slow dark mist at the Heartstone's base while a corrupt refresh names it. */
	@Override
	public void heartstone(BlockState state, Level level, BlockPos pos, RandomSource random) {
		Vec3 centre = Vec3.atCenterOf(pos);

		if (!corruptNear(centre, 1.5) || random.nextInt(2) != 0 || !Vfx.inRange(centre)) {
			return;
		}

		mistAtBase(level, centre, random, 1.2, 2.6, 0.006);
	}

	/** Heat shimmer off a sunstone seam open to the air above it, now and then, by day. */
	@Override
	public void sunstone(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!ClientConfig.get().planetAmbience || level.isDarkOutside() || random.nextInt(8) != 0 || !level.getBlockState(pos.above()).isAir()) {
			return;
		}

		Vec3 at = Vec3.atCenterOf(pos).add((random.nextDouble() - 0.5) * 0.7, 0.6, (random.nextDouble() - 0.5) * 0.7);

		if (!Vfx.inRange(at)) {
			return;
		}

		Particle particle = Minecraft.getInstance().particleEngine.createParticle(ForceParticles.AIR_RIPPLE, at.x, at.y, at.z, 0, 0.015, 0);

		if (particle instanceof ForceParticle shimmer) {
			shimmer.size(0.25f, 0.6f).alpha(0.08f).life(24);
		}
	}

	/** Static crawling over a stormglass cluster now and then (steady motes with flashing effects off). */
	@Override
	public void stormglass(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!ClientConfig.get().planetAmbience || random.nextInt(6) != 0) {
			return;
		}

		Direction facing = state.getValue(CrystalFormationBlock.FACING);
		Vec3 out = CrystalFormationBlock.facingVector(facing);
		Vec3 at = Vec3.atCenterOf(pos).subtract(out.scale(0.5)).add(out.scale(0.2 + random.nextDouble() * 0.5))
				.add((random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4);

		if (Vfx.inRange(at)) {
			level.addParticle(Vfx.flashing() ? ForceParticles.LIGHTNING_SPARK : WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, 0, 0.004, 0);
		}
	}

	/** A verse stone's client tick: its motes and its hum ({@link VerseStoneClient}). */
	@Override
	public void verseStone(BlockState state, Level level, BlockPos pos) {
		VerseStoneClient.INSTANCE.stone(state, level, pos);
	}

	/**
	 * One puff of dark mist on the ground {@code inner} to {@code outer} blocks from the stone at
	 * {@code centre}, rising at {@code rise}: dust and smoke where the ground is, nothing where it is not.
	 */
	static void mistAtBase(Level level, Vec3 centre, RandomSource random, double inner, double outer, double rise) {
		double angle = random.nextDouble() * Mth.TWO_PI;
		mistAt(level, centre, angle, inner + random.nextDouble() * (outer - inner), random, rise);
	}

	static void mistAt(Level level, Vec3 centre, double angle, double r, RandomSource random, double rise) {
		double x = centre.x + Math.cos(angle) * r;
		double z = centre.z + Math.sin(angle) * r;
		BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(centre.y), Mth.floor(z));

		for (int i = 0; i < 5 && level.getBlockState(cell).isAir(); i++) {
			cell.move(Direction.DOWN);
		}

		if (level.getBlockState(cell).isAir() || !level.getBlockState(cell.above()).isAir()) {
			return;
		}

		double y = cell.getY() + 1.05;

		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0, rise * 0.6, 0);
		} else {
			level.addParticle(new DustParticleOptions(MIST_COLOR, 1.6f + random.nextFloat() * 1.2f), x, y + random.nextDouble() * 0.3, z, 0, rise, 0);
		}
	}

	// ---- timed effects ----

	private void tickTimed(ClientLevel level) {
		RandomSource random = level.getRandom();
		Iterator<Timed> it = timed.iterator();

		while (it.hasNext()) {
			Timed effect = it.next();
			int age = ++effect.age()[0];

			if (age > effect.duration()) {
				it.remove();
				continue;
			}

			if (!Vfx.inRange(effect.pos())) {
				continue;
			}

			float t = age / (float) effect.duration();

			switch (effect.kind()) {
				case CLEANSE_DRAIN -> cleanseDrain(level, effect, age, random);
				case HEART_WAVE -> heartWave(level, effect, t, random);
				case HEART_EXHALE -> heartExhale(level, effect, t, random);
				case ANSWER -> answer(level, effect, age, random);
				case BLEED -> bleed(level, effect, age, random);
			}
		}
	}

	/**
	 * The veins drain: dark dust slides down the stone's faces into the ground for 20 ticks; at tick
	 * 16 the stone's own hum returns with a few pale motes rising.
	 */
	private static void cleanseDrain(ClientLevel level, Timed effect, int age, RandomSource random) {
		Vec3 centre = effect.pos();

		if (age <= 20) {
			repeat(random, 2, () -> {
				Direction face = HORIZONTAL[random.nextInt(4)];
				Vec3 at = centre.add(face.getStepX() * 0.56, 2.2 - age * 0.1 - random.nextDouble() * 0.8, face.getStepZ() * 0.56)
						.add(onFace(face, random, 0.8));
				level.addAlwaysVisibleParticle(new DustParticleOptions(VEIN_COLOR, 0.9f), at.x, at.y, at.z, face.getStepX() * 0.01, -0.05, face.getStepZ() * 0.01);
			});
		}

		if (age == 16) {
			float volume = 0.8f * ClientConfig.get().modSoundVolume;

			if (volume > 0.001f) {
				level.playLocalSound(centre.x, centre.y, centre.z, ModSounds.WAYSTONE_HUM, SoundSource.BLOCKS, volume, 1f, false);
			}

			for (int i = 0, n = Vfx.count(10); i < n; i++) {
				Vec3 at = centre.add((random.nextDouble() - 0.5) * 1.4, -0.8 + random.nextDouble() * 3, (random.nextDouble() - 0.5) * 1.4);
				level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, 0, 0.015 + random.nextDouble() * 0.01, 0);
			}
		}
	}

	/**
	 * A wave wells up: dark mist from a few vents around the stone's base (fixed per wave, so it
	 * comes in uneven patches with bare ground between them), densest early, stronger each wave.
	 */
	private static void heartWave(ClientLevel level, Timed effect, float t, RandomSource random) {
		int wave = Mth.clamp(Math.round(effect.data()), 1, 3);
		RandomSource vents = RandomSource.create(BlockPos.containing(effect.pos()).asLong() * 31 + wave);
		int ventCount = 3 + wave;
		float strength = 1f - t * 0.7f;
		repeat(random, Math.max(1, Math.round((1 + wave) * strength)), () -> {
			int vent = random.nextInt(ventCount);
			double angle = 0;
			double r = 0;

			for (int i = 0; i <= vent; i++) {
				angle = vents.nextDouble() * Mth.TWO_PI;
				r = 1.6 + vents.nextDouble() * 2.4;
			}

			mistAt(level, effect.pos(), angle + (random.nextDouble() - 0.5) * 0.35, r + (random.nextDouble() - 0.5) * 0.8, random, 0.02 + wave * 0.006);
		});
	}

	/** The cleansed Heartstone breathes out: pale motes leave its faces and drift up and away. */
	private static void heartExhale(ClientLevel level, Timed effect, float t, RandomSource random) {
		float breath = Mth.sin(t * Mth.PI);

		if (random.nextFloat() > 0.3f + breath) {
			return;
		}

		repeat(random, 1, () -> {
			Direction face = HORIZONTAL[random.nextInt(4)];
			Vec3 at = effect.pos().add(face.getStepX() * 1.6, -1.5 + random.nextDouble() * 4, face.getStepZ() * 1.6).add(onFace(face, random, 2.5));
			Vec3 v = new Vec3(face.getStepX() * 0.012, 0.012 + random.nextDouble() * 0.01, face.getStepZ() * 0.012);
			level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, v.x, v.y, v.z);
		});
	}

	/** The Hollow's crystals answer together: glints run over the clusters found around the heart. */
	private static void answer(ClientLevel level, Timed effect, int age, RandomSource random) {
		if (effect.points().isEmpty() || age % 2 != 0) {
			return;
		}

		Vec3 at = effect.points().get(random.nextInt(effect.points().size())).add((random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.5) * 0.4,
				(random.nextDouble() - 0.5) * 0.4);

		if (Vfx.flashing()) {
			level.addAlwaysVisibleParticle(PlanetParticles.AURELITE_GLINT, at.x, at.y, at.z, 0, 0, 0);
		} else {
			level.addAlwaysVisibleParticle(WorldParticles.CRYSTAL_MOTE, at.x, at.y, at.z, 0, 0.01, 0);
		}
	}

	/**
	 * The rift crystal darkens to red while it is held: red seeps from it and drips, black slivers
	 * are drawn in from the walls, and the whispers come again every two and a half seconds.
	 */
	private void bleed(ClientLevel level, Timed effect, int age, RandomSource random) {
		Vec3 pos = effect.pos();
		float t = Mth.clamp(age / Math.max(1f, effect.data()), 0f, 1f);

		if (age % 50 == 0) {
			whisper(level, pos, 0.5f + t * 0.5f);
		}

		repeat(random, 1 + Math.round(t * 2), () -> {
			Vec3 at = pos.add((random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 0.5);
			int color = lerpColor(0x3A1A24, BLEED_COLOR, t);
			level.addAlwaysVisibleParticle(new DustParticleOptions(color, 0.6f + t * 0.6f), at.x, at.y, at.z, 0, -0.02, 0);
		});

		if (age % 3 == 0) {
			repeat(random, 1, () -> {
				Vec3 from = pos.add(randomDirection(random).scale(2.5 + random.nextDouble() * 1.5));
				Vec3 v = pos.subtract(from).scale(0.05);
				level.addAlwaysVisibleParticle(PlanetParticles.DUSKGLASS_SHARD, from.x, from.y, from.z, v.x, v.y + 0.12, v.z);
			});
		}
	}

	// ---- helpers ----

	/**
	 * Runs {@code sample} for a Standard-preset count of {@code base}, scaled by the preset and the
	 * {@link Vfx} budget. Below one sample per call (Reduced), samples are skipped by chance.
	 */
	static void repeat(RandomSource random, int base, Runnable sample) {
		float wanted = base * ClientConfig.get().preset.particleMultiplier;

		if (wanted < 1f && random.nextFloat() >= wanted) {
			return;
		}

		for (int i = 0, n = Vfx.count(base); i < n; i++) {
			sample.run();
		}
	}

	/** A random offset within {@code spread} across the face {@code face} (zero along its normal). */
	static Vec3 onFace(Direction face, RandomSource random, double spread) {
		double a = (random.nextDouble() - 0.5) * spread;
		double b = (random.nextDouble() - 0.5) * spread;
		return switch (face.getAxis()) {
			case X -> new Vec3(0, a, b);
			case Y -> new Vec3(a, 0, b);
			case Z -> new Vec3(a, b, 0);
		};
	}

	static Vec3 randomDirection(RandomSource random) {
		double y = random.nextDouble() * 2 - 1;
		double angle = random.nextDouble() * Mth.TWO_PI;
		double r = Math.sqrt(1 - y * y);
		return new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r);
	}

	private static int lerpColor(int from, int to, float t) {
		int r = Math.round(Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
		return r << 16 | g << 8 | b;
	}
}
