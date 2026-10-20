package com.oldrepublic.client.world.site;

import java.util.PriorityQueue;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.VerseVision;
import com.oldrepublic.client.world.FadingLoop;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;
import com.oldrepublic.world.puzzle.PuzzleBlocks;
import com.oldrepublic.world.site.SiteBlocks;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePresencePayload;
import com.oldrepublic.world.site.SiteStates;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The client ambience of the M3 sites (spec 3.7), driven by {@code site_presence}:
 * <ul>
 * <li>One non-positional bed per site ({@code ambient.jedi_ruin}, {@code ambient.sith_tomb},
 * {@code ambient.wreck}, {@code ambient.outpost}, {@code ambient.reliquary}) through
 * {@link FadingLoop}: it fades in on entry and fades out when the player leaves the site or dies,
 * stops at once with the level on a dimension change, and is killed on disconnect.</li>
 * <li>Distant one-shots: {@code ambient.ruin_chime} every 15 to 40 s, {@code ambient.tomb_whisper}
 * every 8 to 20 s, {@code ambient.wreck_creak} every 10 to 30 s.</li>
 * <li>Particles through the {@link Vfx} budget, never more than {@link #MAX_LIVE} alive at once per
 * site: {@code light_mote} drifting in sun shafts and {@code leaf_fall} under the canopy of the
 * courtyard, ceiling dust and motes in the lamp-lit air of the halls, {@code tomb_dust} sifting from
 * the tomb's ceiling cracks, smoke wisps off scorched plating, {@code crystal_glint} in the
 * Reliquary. Lumen moss, conduits, vents and pedestals have their own block ambience (the puzzle
 * kit's display ticks).</li>
 * <li>The comms mast lamp of a Republic Forward Post blinks slowly (a red flare every 2 s around
 * the sea lantern); with flashing effects off the lamp stays steady (the lantern alone).</li>
 * <li>Every bed dips by {@link SiteClient#senseDip()} after the local player's Force Sense, and
 * ducks while a verse vision plays.</li>
 * </ul>
 * Echo shrines use the bed of their tradition: the Jedi and Wayfarer shrines the ruin's, the Sith
 * shrine the tomb's. Crystal caves keep the M1 {@code ambient.cave} bed of {@code WorldAmbience};
 * Courier's Fall and mission sites have no bed of their own.
 *
 * <p>Milestone 4 (spec MILESTONE_4.md 13.4): the Greenwatch Enclave plays {@code ambient.greenwatch}
 * with the ruin's sun shafts and leaves, the Proving {@code ambient.proving}; both stop when
 * {@code ClientConfig.planetAmbience} is off. Greater tombs and Zarvos's Sepulchre use the tomb's
 * scenes, the Singing Hollow's grotto the ruin's halls and the duskglass rift the tomb's depths.
 *
 * <p>Milestone 5 (spec MILESTONE_5.md 3.2, 3.3, 15.5): Saltgate plays {@code ambient.saltgate}, the
 * holdfast {@code ambient.holdfast} and the Hymnal {@code ambient.hymnal} with its ceiling dust (all
 * three follow {@code ClientConfig.planetAmbience}); a crashed freighter is a wreck and the detention
 * post an Imperial outpost.
 */
final class SiteAmbience {
	static final SiteAmbience INSTANCE = new SiteAmbience();

	/** Most particles of the site ambience alive at once (spec 3.7: 40 per site). */
	static final int MAX_LIVE = 40;
	private static final int BED_FADE_TICKS = 60;
	/** A bed's volume factor while a verse vision plays (MILESTONE_5.md 6.6, 15.3). */
	private static final float BED_DUCK_VISION = 0.3f;
	/** Horizontal reach of the particle sampling around the player. */
	private static final int REACH = 10;
	/** Republic mast lamp: one slow blink per period, lit for {@link #BLINK_ON} ticks. */
	private static final int BLINK_PERIOD = 40;
	private static final int BLINK_ON = 8;
	private static final int MAST_LAMP_COLOR = 0xFF3B2E;
	/** The terminal scan on entering a Republic post: half-size of the box and x-slices per tick. */
	private static final int SCAN_RADIUS = 24;
	private static final int SCAN_HEIGHT = 8;
	private static final int SCAN_SLICES_PER_TICK = 6;
	private static final Direction[] HORIZONTAL = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

	/** The site beds: the five of M3, and the planet hubs and the Hymnal (which follow {@code ClientConfig.planetAmbience}). */
	enum Bed {
		JEDI_RUIN(ModSounds.AMBIENT_JEDI_RUIN, 0.55f, false),
		SITH_TOMB(ModSounds.AMBIENT_SITH_TOMB, 0.6f, false),
		WRECK(ModSounds.AMBIENT_WRECK, 0.55f, false),
		OUTPOST(ModSounds.AMBIENT_OUTPOST, 0.4f, false),
		RELIQUARY(ModSounds.AMBIENT_RELIQUARY, 0.6f, false),
		GREENWATCH(ModSounds.AMBIENT_GREENWATCH, 0.5f, true),
		PROVING(ModSounds.AMBIENT_PROVING, 0.5f, true),
		SALTGATE(ModSounds.AMBIENT_SALTGATE, 0.5f, true),
		HOLDFAST(ModSounds.AMBIENT_HOLDFAST, 0.5f, true),
		HYMNAL(ModSounds.AMBIENT_HYMNAL, 0.6f, true);

		final SoundEvent sound;
		final float volume;
		final boolean planet;

		Bed(SoundEvent sound, float volume, boolean planet) {
			this.sound = sound;
			this.volume = volume;
			this.planet = planet;
		}
	}

	/** The distant one-shots, with their interval in ticks and how far from the player they sound. */
	enum OneShot {
		NONE(null, 0, 0, 0f, 0, 0),
		CHIME(ModSounds.AMBIENT_RUIN_CHIME, 300, 800, 0.5f, 10, 18),
		WHISPER(ModSounds.AMBIENT_TOMB_WHISPER, 160, 400, 0.45f, 4, 9),
		CREAK(ModSounds.AMBIENT_WRECK_CREAK, 200, 600, 0.6f, 6, 14);

		final @Nullable SoundEvent sound;
		final int minTicks;
		final int maxTicks;
		final float volume;
		final int minDistance;
		final int maxDistance;

		OneShot(@Nullable SoundEvent sound, int minTicks, int maxTicks, float volume, int minDistance, int maxDistance) {
			this.sound = sound;
			this.minTicks = minTicks;
			this.maxTicks = maxTicks;
			this.volume = volume;
			this.minDistance = minDistance;
			this.maxDistance = maxDistance;
		}

		int nextDelay(RandomSource random) {
			return minTicks + random.nextInt(maxTicks - minTicks + 1);
		}
	}

	/** What the player stands in: decides the bed, the one-shot and the particles. */
	enum Scene {
		NONE(null, OneShot.NONE),
		/** {@code jedi_ruin/courtyard}: sun shafts and falling leaves. */
		RUIN_SURFACE(Bed.JEDI_RUIN, OneShot.CHIME),
		/** The ruin's stair, hall, niche and cache, and the Jedi and Wayfarer shrines: lamp-lit dust. */
		RUIN_UNDERGROUND(Bed.JEDI_RUIN, OneShot.CHIME),
		/** {@code sith_tomb/gate}, path and dig camp: the bed and whispers only. */
		TOMB_SURFACE(Bed.SITH_TOMB, OneShot.WHISPER),
		/** The processional, burial chamber, niche, rubble room and the Sith shrine: tomb dust. */
		TOMB_UNDERGROUND(Bed.SITH_TOMB, OneShot.WHISPER),
		WRECK(Bed.WRECK, OneShot.CREAK),
		REPUBLIC_OUTPOST(Bed.OUTPOST, OneShot.NONE),
		IMPERIAL_OUTPOST(Bed.OUTPOST, OneShot.NONE),
		RELIQUARY(Bed.RELIQUARY, OneShot.NONE),
		/** {@code hub/greenwatch}: the enclave's bed, sun shafts and leaves in the clearing. */
		GREENWATCH(Bed.GREENWATCH, OneShot.NONE),
		/** {@code hub/proving}: the academy's bed (Varkhast's ashfall is {@code PlanetAmbience}'s). */
		PROVING(Bed.PROVING, OneShot.NONE),
		/** {@code hub/saltgate}: the port's bed (a sandstorm's sand is {@code client.planet.Sandstorm}'s). */
		SALTGATE(Bed.SALTGATE, OneShot.NONE),
		/** {@code hub/holdfast}: the clan camp's bed under the storm. */
		HOLDFAST(Bed.HOLDFAST, OneShot.NONE),
		/** The Hymnal's halls: its bed and dust sifting from the ceiling. */
		HYMNAL(Bed.HYMNAL, OneShot.NONE);

		final @Nullable Bed bed;
		final OneShot oneShot;

		Scene(@Nullable Bed bed, OneShot oneShot) {
			this.bed = bed;
			this.oneShot = oneShot;
		}

		/** The scene of a presence payload. */
		static Scene of(@Nullable SiteKind kind, String template) {
			if (kind == null) {
				return NONE;
			}

			String path = templatePath(template);
			return switch (kind) {
				case JEDI_RUIN -> path.equals("jedi_ruin/courtyard") ? RUIN_SURFACE : RUIN_UNDERGROUND;
				case SITH_TOMB -> path.equals("sith_tomb/gate") || path.equals("sith_tomb/path") || path.equals("sith_tomb/dig_camp")
						? TOMB_SURFACE
						: TOMB_UNDERGROUND;
				case WRECK_FIELD -> WRECK;
				case REPUBLIC_OUTPOST -> REPUBLIC_OUTPOST;
				case IMPERIAL_OUTPOST -> IMPERIAL_OUTPOST;
				case ECHO_SHRINE -> path.equals("site/echo_shrine_sith") ? TOMB_UNDERGROUND : RUIN_UNDERGROUND;
				case RELIQUARY -> RELIQUARY;
				// Milestone 4: the hubs have beds of their own; the greater tombs and Zarvos's Sepulchre are
				// tombs; the Hollow's grotto sings like a ruin and the rift whispers like a tomb.
				case PLANET_HUB -> switch (path) {
					case "hub/greenwatch" -> GREENWATCH;
					case "hub/proving" -> PROVING;
					case "hub/saltgate" -> SALTGATE;
					case "hub/holdfast" -> HOLDFAST;
					default -> NONE;
				};
				case GREATER_TOMB -> path.equals("greater_tomb/gate") ? TOMB_SURFACE : TOMB_UNDERGROUND;
				case SEPULCHRE -> TOMB_UNDERGROUND;
				case EXPEDITION -> path.equals("expedition/singing_hollow_grotto") ? RUIN_UNDERGROUND
						: path.equals("expedition/duskglass_rift_rift") ? TOMB_UNDERGROUND : NONE;
				// Milestone 5: the Hymnal has its own bed; a crashed freighter is a wreck, the detention post an
				// Imperial outpost.
				case HYMNAL -> HYMNAL;
				case CRASHED_FREIGHTER -> WRECK;
				case DETENTION_POST -> IMPERIAL_OUTPOST;
				// Crystal caves keep the M1 bed; Courier's Fall, mission sites, waystones, the Heartstone, the
				// caches, the camp, the cradle, the M5 shrines, posts, graves, camps and verse stones, and
				// kinds appended later have none.
				default -> NONE;
			};
		}

		private static String templatePath(String template) {
			String prefix = OldRepublicMod.MOD_ID + ":";
			return template.startsWith(prefix) ? template.substring(prefix.length()) : template;
		}
	}

	private SitePresencePayload presence = SitePresencePayload.NONE;
	private @Nullable Object presenceLevel;
	private @Nullable Object lastLevel;
	private Scene scene = Scene.NONE;
	private @Nullable SiteKind particleSite;
	private @Nullable FadingLoop bed;
	private @Nullable Bed bedKind;
	private int oneShotDelay;
	private long ticks;
	/** Expiry ticks of the particles this ambience spawned that may still be alive. */
	private final PriorityQueue<Long> live = new PriorityQueue<>();

	// Republic mast lamp
	private @Nullable BlockPos scanCenter;
	private int scanSlice;
	private @Nullable BlockPos scanBest;
	private double scanBestDistance;
	private @Nullable BlockPos mastLamp;

	private SiteAmbience() {
	}

	// ---- server-driven state ----

	/** A {@code site_presence} payload arrived (client thread). */
	void presence(Minecraft client, SitePresencePayload payload) {
		syncLevel(client);
		presence = payload;
		presenceLevel = client.level;
		Scene next = Scene.of(payload.siteKind(), payload.template());

		if (next == scene) {
			return;
		}

		scene = next;
		SiteKind site = payload.siteKind();

		if (site != particleSite) {
			// A new site starts its own particle allowance.
			particleSite = site;
			live.clear();
		}

		RandomSource random = client.level != null ? client.level.getRandom() : RandomSource.create();
		oneShotDelay = scene.oneShot == OneShot.NONE ? 0 : scene.oneShot.nextDelay(random) / 2;
		resetMast();

		if (scene == Scene.REPUBLIC_OUTPOST && client.player != null) {
			scanCenter = client.player.blockPosition();
		}
	}

	/** Whether a bed is playing (not fading out). */
	boolean hasBed() {
		return bed != null && !bed.isReleased() && !bed.isStopped();
	}

	/** The presence the server last sent for the current level ({@link SitePresencePayload#NONE} if none). */
	SitePresencePayload presence() {
		return presence;
	}

	// ---- client tick ----

	void tick(Minecraft client) {
		syncLevel(client);
		ClientLevel level = client.level;
		LocalPlayer player = client.player;

		if (level == null || player == null) {
			return;
		}

		ticks++;
		boolean alive = !player.isDeadOrDying();
		Bed wanted = alive ? scene.bed : null;
		updateBed(wanted != null && wanted.planet && !ClientConfig.get().planetAmbience ? null : wanted);

		if (!alive || scene == Scene.NONE) {
			return;
		}

		while (!live.isEmpty() && live.peek() <= ticks) {
			live.poll();
		}

		RandomSource random = level.getRandom();
		oneShot(level, player, random);
		particles(level, player, random);

		if (scene == Scene.REPUBLIC_OUTPOST) {
			scanForMast(level, player);
			blinkMast(level, random);
		}
	}

	/** Disconnect: every loop stops at once and all state is forgotten. */
	void reset() {
		if (bed != null) {
			bed.kill();
		}

		bed = null;
		bedKind = null;
		presence = SitePresencePayload.NONE;
		presenceLevel = null;
		lastLevel = null;
		scene = Scene.NONE;
		particleSite = null;
		live.clear();
		resetMast();
	}

	/**
	 * A level change (dimension change, respawn into another level, joining a world) drops the
	 * presence of the old level; the loop stops itself with its level and the server resends the
	 * presence for the new one.
	 */
	private void syncLevel(Minecraft client) {
		if (client.level == lastLevel) {
			return;
		}

		lastLevel = client.level;

		if (presenceLevel != client.level) {
			presence = SitePresencePayload.NONE;
			presenceLevel = null;
			scene = Scene.NONE;
			particleSite = null;
		}

		if (bed != null) {
			bed.kill();
			bed = null;
			bedKind = null;
		}

		live.clear();
		resetMast();
	}

	private void updateBed(@Nullable Bed wanted) {
		if (bed != null && (bed.isStopped() || bedKind != wanted)) {
			bed.release();
			bed = null;
			bedKind = null;
		}

		if (wanted != null && bed == null) {
			bed = FadingLoop.play(wanted.sound, null, wanted.volume, BED_FADE_TICKS);
			bedKind = wanted;
		}

		if (bed != null) {
			bed.dip(SiteClient.senseDip());
			bed.duck(VerseVision.dipping() ? BED_DUCK_VISION : 1f);
		}
	}

	// ---- one-shots ----

	private void oneShot(ClientLevel level, LocalPlayer player, RandomSource random) {
		OneShot shot = scene.oneShot;

		if (shot.sound == null || --oneShotDelay > 0) {
			return;
		}

		oneShotDelay = shot.nextDelay(random);
		float volume = shot.volume * ClientConfig.get().modSoundVolume * SiteClient.senseDip();

		if (volume <= 0.001f) {
			return;
		}

		double angle = random.nextDouble() * Mth.TWO_PI;
		double distance = shot.minDistance + random.nextDouble() * (shot.maxDistance - shot.minDistance);
		Vec3 eye = player.getEyePosition();
		Vec3 at = eye.add(Math.cos(angle) * distance, -1 + random.nextDouble() * 4, Math.sin(angle) * distance);
		level.playLocalSound(at.x, at.y, at.z, shot.sound, SoundSource.AMBIENT, volume, 0.9f + random.nextFloat() * 0.2f, false);
	}

	// ---- particles ----

	private void particles(ClientLevel level, LocalPlayer player, RandomSource random) {
		switch (scene) {
			case RUIN_SURFACE, GREENWATCH -> {
				if (ticks % 4 == 0) {
					repeat(random, 2, () -> sunShaftMote(level, player, random));
				}

				if (ticks % 7 == 0) {
					repeat(random, 1, () -> fallingLeaf(level, player, random));
				}
			}
			case RUIN_UNDERGROUND -> {
				if (ticks % 5 == 0) {
					repeat(random, 2, () -> hallDust(level, player, random));
				}
			}
			case TOMB_UNDERGROUND, HYMNAL -> {
				if (ticks % 4 == 0) {
					repeat(random, 2, () -> tombDust(level, player, random));
				}
			}
			case WRECK -> {
				if (ticks % 6 == 0) {
					repeat(random, 1, () -> smokeWisp(level, player, random));
				}
			}
			case RELIQUARY -> {
				if (ticks % 4 == 0) {
					repeat(random, 2, () -> reliquaryGlint(level, player, random));
				}
			}
			default -> {
			}
		}
	}

	/**
	 * Runs {@code sample} for a Standard-preset count of {@code base}, scaled by the preset and the
	 * {@link Vfx} budget. Below one sample per call (Reduced), samples are skipped by chance.
	 */
	private static void repeat(RandomSource random, int base, Runnable sample) {
		float wanted = base * ClientConfig.get().preset.particleMultiplier;

		if (wanted < 1f && random.nextFloat() >= wanted) {
			return;
		}

		for (int i = 0, n = Vfx.count(base); i < n; i++) {
			sample.run();
		}
	}

	/** Spawns one ambience particle unless the site already has {@link #MAX_LIVE} alive or it is out of range. */
	private boolean spawn(ClientLevel level, ParticleOptions particle, Vec3 at, Vec3 velocity, int maxLifetime) {
		if (live.size() >= MAX_LIVE || !Vfx.inRange(at)) {
			return false;
		}

		level.addParticle(particle, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
		live.add(ticks + maxLifetime);
		return true;
	}

	/** A warm mote drifting in a sunlit column that has cover close beside it (light falling past an edge). */
	private void sunShaftMote(ClientLevel level, LocalPlayer player, RandomSource random) {
		int x = player.getBlockX() + random.nextInt(REACH * 2 + 1) - REACH;
		int z = player.getBlockZ() + random.nextInt(REACH * 2 + 1) - REACH;
		int y = player.getBlockY() + random.nextInt(6);

		if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > y || !coveredNear(level, x, y, z)) {
			return;
		}

		BlockPos pos = new BlockPos(x, y, z);

		// Daylight only: at night the raw sky light is darkened well below this.
		if (!level.getBlockState(pos).isAir() || level.getMaxLocalRawBrightness(pos) < 12) {
			return;
		}

		Vec3 at = new Vec3(x + random.nextDouble(), y + random.nextDouble(), z + random.nextDouble());
		Vec3 v = new Vec3((random.nextDouble() - 0.5) * 0.006, -0.002 - random.nextDouble() * 0.004, (random.nextDouble() - 0.5) * 0.006);
		spawn(level, WorldParticles.LIGHT_MOTE, at, v, 110);
	}

	/** Whether a column two blocks away on any side is covered well above {@code y} (wall, apse, canopy). */
	private static boolean coveredNear(ClientLevel level, int x, int y, int z) {
		for (Direction side : HORIZONTAL) {
			int nx = x + side.getStepX() * 2;
			int nz = z + side.getStepZ() * 2;

			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, nx, nz) > y + 2) {
				return true;
			}
		}

		return false;
	}

	/** A leaf dropping from the underside of a canopy near the player. */
	private void fallingLeaf(ClientLevel level, LocalPlayer player, RandomSource random) {
		int x = player.getBlockX() + random.nextInt(REACH * 2 + 1) - REACH;
		int z = player.getBlockZ() + random.nextInt(REACH * 2 + 1) - REACH;
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;

		if (top < player.getBlockY() - 2 || top > player.getBlockY() + 16) {
			return;
		}

		// Walk down through the canopy to its underside.
		BlockPos pos = new BlockPos(x, top, z);

		for (int i = 0; i < 4 && level.getBlockState(pos.below()).is(BlockTags.LEAVES); i++) {
			pos = pos.below();
		}

		if (!level.getBlockState(pos).is(BlockTags.LEAVES) || !level.getBlockState(pos.below()).isAir()) {
			return;
		}

		Vec3 at = new Vec3(x + random.nextDouble(), pos.getY() - 0.1, z + random.nextDouble());
		spawn(level, WorldParticles.LEAF_FALL, at, new Vec3(0, -0.01, 0), 140);
	}

	/** Ceiling dust in the lamp-lit air of the ruin's underground rooms, with a few motes in the light. */
	private void hallDust(ClientLevel level, LocalPlayer player, RandomSource random) {
		BlockPos ceiling = ceilingAbove(level, player, random, 8);

		if (ceiling == null || level.getMaxLocalRawBrightness(ceiling.below()) < 8) {
			return;
		}

		BlockState state = level.getBlockState(ceiling);
		Vec3 at = new Vec3(ceiling.getX() + 0.15 + random.nextDouble() * 0.7, ceiling.getY() - 0.05, ceiling.getZ() + 0.15 + random.nextDouble() * 0.7);

		if (random.nextInt(3) == 0) {
			Vec3 mote = at.add(0, -1 - random.nextDouble() * 2, 0);

			if (level.getBlockState(BlockPos.containing(mote)).isAir()) {
				spawn(level, WorldParticles.LIGHT_MOTE, mote, new Vec3(0, -0.002, 0), 110);
			}

			return;
		}

		spawn(level, new BlockParticleOption(ParticleTypes.FALLING_DUST, state), at, Vec3.ZERO, 80);
	}

	/** Tomb dust sifting down from the ceiling, mostly from cracked stones. */
	private void tombDust(ClientLevel level, LocalPlayer player, RandomSource random) {
		BlockPos ceiling = ceilingAbove(level, player, random, 8);

		if (ceiling == null) {
			return;
		}

		BlockState state = level.getBlockState(ceiling);
		boolean crack = state.is(PuzzleBlocks.TOMB_STONE_CRACKED) || state.is(PuzzleBlocks.RUIN_STONE_CRACKED);

		if (!crack && random.nextInt(3) != 0) {
			return;
		}

		Vec3 at = new Vec3(ceiling.getX() + 0.2 + random.nextDouble() * 0.6, ceiling.getY() - 0.05, ceiling.getZ() + 0.2 + random.nextDouble() * 0.6);
		Vec3 v = new Vec3((random.nextDouble() - 0.5) * 0.004, -0.01 - random.nextDouble() * 0.01, (random.nextDouble() - 0.5) * 0.004);
		spawn(level, WorldParticles.TOMB_DUST, at, v, 75);
	}

	/**
	 * The ceiling over a random cell within {@code reach} blocks of the player: the first block up to
	 * 10 above the cell over the player's feet, with air beneath it; null under open sky, under
	 * leaves or fluid, or when the cell itself is not air.
	 */
	private static @Nullable BlockPos ceilingAbove(ClientLevel level, LocalPlayer player, RandomSource random, int reach) {
		int x = player.getBlockX() + random.nextInt(reach * 2 + 1) - reach;
		int z = player.getBlockZ() + random.nextInt(reach * 2 + 1) - reach;
		int start = player.getBlockY() + 1;

		if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) <= start) {
			return null;
		}

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, start, z);

		if (!level.getBlockState(pos).isAir()) {
			return null;
		}

		for (int rise = 1; rise <= 10; rise++) {
			pos.setY(start + rise);
			BlockState state = level.getBlockState(pos);

			if (!state.isAir()) {
				return state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES) ? pos.immutable() : null;
			}
		}

		return null;
	}

	/** A thin wisp of smoke rising off scorched plating open to the air. */
	private void smokeWisp(ClientLevel level, LocalPlayer player, RandomSource random) {
		for (int attempt = 0; attempt < 4; attempt++) {
			int x = player.getBlockX() + random.nextInt(REACH * 2 + 1) - REACH;
			int z = player.getBlockZ() + random.nextInt(REACH * 2 + 1) - REACH;
			int y = player.getBlockY() - 3 + random.nextInt(12);
			BlockPos pos = new BlockPos(x, y, z);

			if (!level.getBlockState(pos).is(PuzzleBlocks.WRECK_PLATING_SCORCHED) || !level.getBlockState(pos.above()).isAir()) {
				continue;
			}

			Vec3 at = new Vec3(x + 0.2 + random.nextDouble() * 0.6, y + 1.02, z + 0.2 + random.nextDouble() * 0.6);
			spawn(level, ParticleTypes.SMOKE, at, new Vec3((random.nextDouble() - 0.5) * 0.004, 0.015 + random.nextDouble() * 0.01,
					(random.nextDouble() - 0.5) * 0.004), 40);
			return;
		}
	}

	/** A crystal glint on a block face open to the air near the player. */
	private void reliquaryGlint(ClientLevel level, LocalPlayer player, RandomSource random) {
		for (int attempt = 0; attempt < 4; attempt++) {
			BlockPos pos = player.blockPosition().offset(random.nextInt(17) - 8, random.nextInt(9) - 2, random.nextInt(17) - 8);

			if (level.getBlockState(pos).isAir()) {
				continue;
			}

			Direction face = Direction.values()[random.nextInt(6)];

			if (!level.getBlockState(pos.relative(face)).isAir()) {
				continue;
			}

			Vec3 out = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
			Vec3 at = Vec3.atCenterOf(pos).add(out.scale(0.53)).add(
					face.getAxis() == Direction.Axis.X ? 0 : (random.nextDouble() - 0.5) * 0.8,
					face.getAxis() == Direction.Axis.Y ? 0 : (random.nextDouble() - 0.5) * 0.8,
					face.getAxis() == Direction.Axis.Z ? 0 : (random.nextDouble() - 0.5) * 0.8);
			spawn(level, WorldParticles.CRYSTAL_GLINT, at, Vec3.ZERO, 18);
			return;
		}
	}

	// ---- Republic mast lamp ----

	private void resetMast() {
		scanCenter = null;
		scanSlice = 0;
		scanBest = null;
		scanBestDistance = Double.MAX_VALUE;
		mastLamp = null;
	}

	/**
	 * Finds the post's bounty terminal a few x-slices per tick after entry; the lamp is the sea
	 * lantern on the mast, 10 up and 4 behind the terminal's screen (the frozen layout of
	 * {@code outpost/republic_post}). A post placed without its structure has no lamp.
	 */
	private void scanForMast(ClientLevel level, LocalPlayer player) {
		if (scanCenter == null) {
			return;
		}

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int end = Math.min(SCAN_RADIUS * 2 + 1, scanSlice + SCAN_SLICES_PER_TICK);

		for (; scanSlice < end; scanSlice++) {
			int x = scanCenter.getX() - SCAN_RADIUS + scanSlice;

			for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
				for (int dy = -SCAN_HEIGHT; dy <= SCAN_HEIGHT; dy++) {
					pos.set(x, scanCenter.getY() + dy, scanCenter.getZ() + dz);
					BlockState state = level.getBlockState(pos);

					if (state.is(SiteBlocks.BOUNTY_TERMINAL) && state.getValue(SiteStates.FACTION) == SiteStates.Army.REPUBLIC) {
						double distance = pos.distSqr(scanCenter);

						if (distance < scanBestDistance) {
							scanBestDistance = distance;
							scanBest = pos.immutable();
						}
					}
				}
			}
		}

		if (scanSlice < SCAN_RADIUS * 2 + 1) {
			return;
		}

		BlockState terminal = scanBest != null ? level.getBlockState(scanBest) : null;

		if (terminal != null && terminal.is(SiteBlocks.BOUNTY_TERMINAL)) {
			Direction screen = terminal.getValue(SiteStates.FACING);
			BlockPos lamp = scanBest.above(10).relative(screen.getOpposite(), 4);
			mastLamp = level.getBlockState(lamp).is(Blocks.SEA_LANTERN) ? lamp : null;
		}

		scanCenter = null;
	}

	/** One slow red flare around the lamp every {@link #BLINK_PERIOD} ticks; none with flashing effects off. */
	private void blinkMast(ClientLevel level, RandomSource random) {
		if (mastLamp == null || !Vfx.flashing()) {
			return;
		}

		int phase = (int) (ticks % BLINK_PERIOD);

		if (phase >= BLINK_ON || phase % 2 != 0) {
			return;
		}

		if (!level.getBlockState(mastLamp).is(Blocks.SEA_LANTERN)) {
			mastLamp = null;
			return;
		}

		Vec3 center = Vec3.atCenterOf(mastLamp);
		DustParticleOptions glow = new DustParticleOptions(MAST_LAMP_COLOR, 1.4f);

		for (Direction side : HORIZONTAL) {
			Vec3 at = center.add(side.getStepX() * 0.62, (random.nextDouble() - 0.5) * 0.4, side.getStepZ() * 0.62);
			spawn(level, glow, at, Vec3.ZERO, 40);
		}
	}
}
