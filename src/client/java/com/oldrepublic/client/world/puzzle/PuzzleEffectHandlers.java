package com.oldrepublic.client.world.puzzle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;
import com.oldrepublic.world.puzzle.CollapsedRubbleBlock;
import com.oldrepublic.world.puzzle.CounterweightStoneBlock;
import com.oldrepublic.world.puzzle.PullAnchorBlock;
import com.oldrepublic.world.puzzle.PuzzleBlocks;
import com.oldrepublic.world.puzzle.PuzzleEffects;
import com.oldrepublic.world.puzzle.PuzzleSealBlock;
import com.oldrepublic.world.puzzle.PuzzleStates;
import com.oldrepublic.world.puzzle.PuzzleStates.Skin;
import com.oldrepublic.world.puzzle.PuzzleStates.Veil;
import com.oldrepublic.world.puzzle.StepStoneBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the puzzle kit's effect events (spec 13.3; ids and variants in
 * {@link PuzzleEffects}): dust, grit, chunks of the skin's stone, a little warm light, and the puzzle
 * sounds. Everything is built from dust, debris and motes; nothing is a ground circle, glyph, halo or
 * outline, and nothing flashes. The vent tell is a Danger Sense tell and spends
 * {@link Vfx#tellCount}, so no preset thins it; everything else spends {@link Vfx#count}.
 *
 * <p>Some looks run on for a few ticks after their event: the urn plume, the dust curtain of a
 * wrong urn, a counterweight's warning trickle, a straining anchor, a shuddering heap, a crumbling
 * seal face, pedestal motes, the vent tell and the vent puff. They live in a short list that
 * {@link #tick} draws each client tick. Each ends after its length, when its block reaches its next
 * state (the anchor released, the heap cleared, the counterweight risen), when the next event of the
 * same block replaces it, and on a level change or disconnect ({@link #reset}).
 */
public final class PuzzleEffectHandlers {
	/**
	 * Pitches of the urn notes by step: a minor tetrachord (tone, semitone, tone) around the
	 * recorded note, so the sequence the keeper plays is heard as a short rising scale.
	 */
	private static final float[] URN_NOTES = {0.8909f, 1f, 1.0595f, 1.1892f};
	/** Pedestal chime pitch by {@link PuzzleStates.PedestalKind}: Jedi as recorded, Sith a minor third down, Wayfarer a tone down. */
	private static final float[] PEDESTAL_PITCH = {1f, 0.8409f, 0.8909f};
	/** Mote tint by pedestal kind: warm gold, rust, brass (the holocron palette). */
	private static final DustParticleOptions[] PEDESTAL_TINT = {
			new DustParticleOptions(0xF2D27A, 0.5f),
			new DustParticleOptions(0xB0472E, 0.5f),
			new DustParticleOptions(0xC9A050, 0.5f)};
	/** Flakes of rust shed by a straining anchor ring. */
	private static final DustParticleOptions RUST = new DustParticleOptions(0x8A4B2A, 0.55f);
	/** The mortar courses of the stone skins, as face-local heights (the textures' brick rows). */
	private static final double[] SEAMS = {0.03, 0.5, 0.97};
	private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
	/** Largest group the client walks when an event names one (the server caps groups at {@code sites.veilGroupMax}). */
	private static final int MAX_GROUP = 64;
	private static final int MAX_LINGERING = 48;
	/** How far above a wrong urn the client looks for the ceiling the dust falls from. */
	private static final int CEILING_SEARCH = 8;

	private static final List<Lingering> LINGERING = new ArrayList<>();
	private static @Nullable Object lastLevel;

	/** Registers the handlers of every puzzle event except {@code seal_open} (shared with M1; see {@link #puzzleSealOpen}). */
	static void init() {
		ClientEffects.register(PuzzleEffects.VEIL_REVEAL, PuzzleEffectHandlers::veilReveal);
		ClientEffects.register(PuzzleEffects.VEIL_OPEN, PuzzleEffectHandlers::veilOpen);
		ClientEffects.register(PuzzleEffects.COUNTERWEIGHT, PuzzleEffectHandlers::counterweight);
		ClientEffects.register(PuzzleEffects.STEP_STONE, PuzzleEffectHandlers::stepStone);
		ClientEffects.register(PuzzleEffects.URN_ECHO, PuzzleEffectHandlers::urnEcho);
		ClientEffects.register(PuzzleEffects.URN_WRONG, PuzzleEffectHandlers::urnWrong);
		ClientEffects.register(PuzzleEffects.ANCHOR, PuzzleEffectHandlers::anchor);
		ClientEffects.register(PuzzleEffects.RUBBLE_CRUMBLE, PuzzleEffectHandlers::rubble);
		ClientEffects.register(PuzzleEffects.PUZZLE_SOLVED, PuzzleEffectHandlers::solved);
		ClientEffects.register(PuzzleEffects.PEDESTAL_READY, PuzzleEffectHandlers::pedestalReady);
		ClientEffects.register(PuzzleEffects.TOMB_VENT, PuzzleEffectHandlers::tombVent);
	}

	// ---- events ----

	/** Sense showed a veiled group: fine pale dust seeps from its mortar lines and drifts down. */
	private static void veilReveal(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		sound(level, p.pos(), ModSounds.PUZZLE_VEIL_REVEAL, 0.8f, vary(random, 1f));

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		// Before the state update arrives the group is still VEILED, after it REVEALED: both count.
		List<BlockPos> cells = group(level, BlockPos.containing(p.pos()), PuzzleBlocks.VEILED_STONE,
				s -> s.getValue(PuzzleStates.VEIL) != Veil.OPEN, size(p));

		for (int i = 0, n = Vfx.count(Math.min(36, 6 + cells.size() * 2)); i < n; i++) {
			BlockPos cell = cells.get(random.nextInt(cells.size()));
			Direction face = exposedFace(level, cell, random);

			if (face != null) {
				Vec3 v = normal(face).scale(0.006).add(0, -0.004, 0);
				spawn(level, WorldParticles.VEIL_DUST, seamPoint(cell, face, random, 0.03), v);
			}
		}
	}

	/** A revealed group opened: it crumbles inward in chunks of its stone, dust falls, a small puff blows out. */
	private static void veilOpen(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		List<BlockPos> cells = group(level, BlockPos.containing(p.pos()), PuzzleBlocks.VEILED_STONE,
				s -> s.getValue(PuzzleStates.VEIL) != Veil.VEILED, size(p));
		sound(level, p.pos(), ModSounds.PUZZLE_VEIL_CRUMBLE, Math.min(1f, 0.7f + cells.size() * 0.03f), vary(random, 1f));

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		ParticleOptions chunk = new BlockParticleOption(ParticleTypes.BLOCK, skinStone(p.variant()));
		Vec3 centre = centroid(cells);

		for (int i = 0, n = Vfx.count(Math.min(40, 6 + cells.size() * 3)); i < n; i++) {
			Vec3 at = inside(cells.get(random.nextInt(cells.size())), random);
			Vec3 v = centre.subtract(at).scale(0.06).add(0, 0.02, 0);
			spawn(level, chunk, at, v);
		}

		for (int i = 0, n = Vfx.count(Math.min(30, 4 + cells.size() * 2)); i < n; i++) {
			BlockPos cell = cells.get(random.nextInt(cells.size()));
			Vec3 at = new Vec3(cell.getX() + random.nextDouble(), cell.getY() + 0.8 + random.nextDouble() * 0.2, cell.getZ() + random.nextDouble());
			spawn(level, WorldParticles.VEIL_DUST, at, new Vec3(0, -0.01 - random.nextDouble() * 0.01, 0));
		}

		for (int i = 0, n = Vfx.count(Math.min(16, 3 + cells.size())); i < n; i++) {
			BlockPos cell = cells.get(random.nextInt(cells.size()));
			Direction face = exposedFace(level, cell, random);

			if (face != null) {
				Vec3 at = facePoint(cell, face, random.nextDouble(), random.nextDouble(), 0.05);
				spawn(level, WorldParticles.TOMB_DUST, at, normal(face).scale(0.04 + random.nextDouble() * 0.03).add(0, 0.005, 0));
			}
		}
	}

	/** Sink (dust squeezed from the seams, a grit puff), warn (a grit trickle until it rises) or rise (dust slides off). */
	private static void counterweight(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		BlockPos pos = BlockPos.containing(p.pos());

		switch (p.variant()) {
			case PuzzleEffects.COUNTERWEIGHT_SINK -> {
				sound(level, p.pos(), ModSounds.PUZZLE_COUNTERWEIGHT_SINK, 1f, vary(random, 1f));
				shake(client, p.pos(), 4, 0.08f);

				if (!Vfx.inRange(p.pos())) {
					return;
				}

				for (int i = 0, n = Vfx.count(14); i < n; i++) {
					Direction side = HORIZONTAL[random.nextInt(4)];
					Vec3 at = facePoint(pos, side, random.nextDouble(), 0.92 - random.nextDouble() * 0.25, 0.04);
					spawn(level, WorldParticles.TOMB_DUST, at, normal(side).scale(0.03 + random.nextDouble() * 0.02).add(0, 0.004, 0));
				}

				Vec3 top = Vec3.atBottomCenterOf(pos).add(0, 0.75, 0);

				for (int i = 0, n = Vfx.count(8); i < n; i++) {
					Vec3 v = new Vec3((random.nextDouble() - 0.5) * 0.16, 0.12 + random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.16);
					spawn(level, WorldParticles.VENT_GRIT, jitter(top, random, 0.35), v);
				}
			}
			case PuzzleEffects.COUNTERWEIGHT_WARN -> {
				sound(level, p.pos(), ModSounds.PUZZLE_COUNTERWEIGHT_WARN, 1f, vary(random, 1f));
				add(new Lingering(Kind.COUNTERWEIGHT_TRICKLE, pos, 60));
			}
			case PuzzleEffects.COUNTERWEIGHT_RISE -> {
				clear(Kind.COUNTERWEIGHT_TRICKLE, pos);
				sound(level, p.pos(), ModSounds.PUZZLE_COUNTERWEIGHT_RISE, 1f, vary(random, 1f));

				if (!Vfx.inRange(p.pos())) {
					return;
				}

				for (int i = 0, n = Vfx.count(14); i < n; i++) {
					Direction side = HORIZONTAL[random.nextInt(4)];
					Vec3 at = facePoint(pos, side, random.nextDouble(), 0.98, 0.03);
					spawn(level, WorldParticles.TOMB_DUST, at, normal(side).scale(0.03).add(0, -0.01, 0));
				}

				for (int i = 0, n = Vfx.count(4); i < n; i++) {
					Direction side = HORIZONTAL[random.nextInt(4)];
					spawn(level, WorldParticles.VENT_GRIT, facePoint(pos, side, random.nextDouble(), 1.0, 0.04), normal(side).scale(0.04));
				}
			}
			default -> {
			}
		}
	}

	/** A ledge slid out (0) or back (1): grit spills along its edge. */
	private static void stepStone(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		boolean extend = p.variant() == PuzzleEffects.STEP_EXTEND;
		sound(level, p.pos(), extend ? ModSounds.PUZZLE_STEP_EXTEND : ModSounds.PUZZLE_STEP_RETRACT, 0.9f, vary(random, 1f));

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		BlockPos pos = BlockPos.containing(p.pos());
		BlockState state = level.getBlockState(pos);
		Direction facing = state.getBlock() instanceof StepStoneBlock ? state.getValue(PuzzleStates.FACING) : Direction.NORTH;
		// Extended: the outer edge of the ledge; retracted: the foot of the plate against the wall.
		Direction edge = extend ? facing : facing.getOpposite();
		double out = extend ? 0.02 : -0.14;
		Vec3 drift = normal(facing).scale(extend ? 0.02 : 0.01);

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			Vec3 at = facePoint(pos, edge, random.nextDouble(), 0.5, out);
			spawn(level, WorldParticles.VENT_GRIT, at, drift.add(0, -0.02, 0));
		}

		for (int i = 0, n = Vfx.count(5); i < n; i++) {
			Vec3 at = facePoint(pos, edge, random.nextDouble(), 0.5 + random.nextDouble() * 0.1, out);
			spawn(level, WorldParticles.TOMB_DUST, at, new Vec3(drift.x * 0.5, -0.005, drift.z * 0.5));
		}
	}

	/** An urn sings its note of the sequence: a slow plume of fine dust from its mouth with warm light inside. */
	private static void urnEcho(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		BlockPos pos = BlockPos.containing(p.pos());
		Vec3 mouth = urnMouth(pos);
		// The note sets the pitch: no random variation, so the four steps read as a scale.
		sound(level, mouth, ModSounds.PUZZLE_URN_ECHO, 1f, URN_NOTES[Mth.clamp(p.variant(), 0, URN_NOTES.length - 1)]);

		if (!Vfx.inRange(mouth)) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			spawn(level, WorldParticles.VEIL_DUST, jitter(mouth, random, 0.1), new Vec3(0, 0.02 + random.nextDouble() * 0.015, 0));
		}

		for (int i = 0, n = Vfx.count(2); i < n; i++) {
			spawn(level, WorldParticles.LIGHT_MOTE, jitter(mouth.add(0, 0.1, 0), random, 0.08), new Vec3(0, 0.01, 0));
		}

		add(new Lingering(Kind.URN_PLUME, pos, 24));
	}

	/** The wrong urn: a 3-block curtain of dust and grit falls from the ceiling around it, and the room rumbles. */
	private static void urnWrong(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		BlockPos pos = BlockPos.containing(p.pos());
		sound(level, p.pos(), ModSounds.PUZZLE_URN_WRONG, 1f, vary(random, 1f));
		shake(client, p.pos(), Math.max(1, p.intensity()) + 2, 0.15f);

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		double ceiling = ceilingAbove(level, pos);

		for (int i = 0, n = Vfx.count(18); i < n; i++) {
			spawn(level, WorldParticles.TOMB_DUST, curtainPoint(pos, ceiling, random), new Vec3(0, -0.03 - random.nextDouble() * 0.03, 0));
		}

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			spawn(level, WorldParticles.VENT_GRIT, curtainPoint(pos, ceiling, random), new Vec3(0, -0.05, 0));
		}

		add(new Lingering(Kind.URN_CURTAIN, pos, List.of(), null, ceiling, 0, 16));
	}

	/** Strain (rust and dust shaken off the ring while it strains) or release (a clank and a grit puff). */
	private static void anchor(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		BlockPos pos = BlockPos.containing(p.pos());

		if (p.variant() == PuzzleEffects.ANCHOR_STRAIN) {
			sound(level, p.pos(), ModSounds.PUZZLE_ANCHOR_STRAIN, 1f, vary(random, 1f));

			if (Vfx.inRange(p.pos())) {
				for (int i = 0, n = Vfx.count(6); i < n; i++) {
					shedRust(level, pos, random);
				}

				add(new Lingering(Kind.ANCHOR_STRAIN, pos, 40));
			}

			return;
		}

		clear(Kind.ANCHOR_STRAIN, pos);
		sound(level, p.pos(), ModSounds.PUZZLE_ANCHOR_RELEASE, 1f, vary(random, 1f));

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			Direction side = exposedSide(level, pos, random);
			Vec3 at = facePoint(pos, side, 0.3 + random.nextDouble() * 0.4, 0.3 + random.nextDouble() * 0.4, 0.05);
			spawn(level, WorldParticles.VENT_GRIT, at, normal(side).scale(0.06 + random.nextDouble() * 0.05).add(0, 0.08 + random.nextDouble() * 0.06, 0));
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			Direction side = exposedSide(level, pos, random);
			Vec3 at = facePoint(pos, side, 0.3 + random.nextDouble() * 0.4, 0.3 + random.nextDouble() * 0.4, 0.08);
			spawn(level, WorldParticles.TOMB_DUST, at, normal(side).scale(0.03).add(0, 0.01, 0));
		}
	}

	/** Shudder while a Lift channel holds the heap (0), or its collapse: a dust burst and rolling pebbles (1). */
	private static void rubble(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		BlockPos pos = BlockPos.containing(p.pos());

		if (p.variant() == PuzzleEffects.RUBBLE_SHUDDER) {
			// The same recording, quiet and low: stone shifting under the Lift.
			sound(level, p.pos(), ModSounds.PUZZLE_RUBBLE_CRUMBLE, 0.45f, vary(random, 0.7f));

			if (Vfx.inRange(p.pos())) {
				for (int i = 0, n = Vfx.count(4); i < n; i++) {
					trickle(level, pos, random);
				}

				add(new Lingering(Kind.RUBBLE_SHUDDER, pos, Mth.clamp(Math.round(p.intensity()), 1, 60)));
			}

			return;
		}

		clear(Kind.RUBBLE_SHUDDER, pos);
		sound(level, p.pos(), ModSounds.PUZZLE_RUBBLE_CRUMBLE, 1f, vary(random, 1f));
		shake(client, p.pos(), 5, 0.12f);

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		Vec3 centre = Vec3.atCenterOf(pos);

		for (int i = 0, n = Vfx.count(24); i < n; i++) {
			Vec3 v = randomDirection(random).scale(0.05 + random.nextDouble() * 0.05);
			spawn(level, WorldParticles.TOMB_DUST, jitter(centre, random, 0.4), new Vec3(v.x, Math.abs(v.y) * 0.6 + 0.01, v.z));
		}

		for (int i = 0, n = Vfx.count(12); i < n; i++) {
			double angle = random.nextDouble() * Mth.TWO_PI;
			double speed = 0.1 + random.nextDouble() * 0.1;
			Vec3 at = Vec3.atBottomCenterOf(pos).add(0, 0.2 + random.nextDouble() * 0.5, 0);
			spawn(level, WorldParticles.VENT_GRIT, at, new Vec3(Math.cos(angle) * speed, 0.05 + random.nextDouble() * 0.07, Math.sin(angle) * speed));
		}

		ParticleOptions chunk = new BlockParticleOption(ParticleTypes.BLOCK, PuzzleBlocks.COLLAPSED_RUBBLE.defaultBlockState());

		for (int i = 0, n = Vfx.count(14); i < n; i++) {
			Vec3 at = inside(pos, random);
			spawn(level, chunk, at, new Vec3((random.nextDouble() - 0.5) * 0.1, 0.02, (random.nextDouble() - 0.5) * 0.1));
		}
	}

	/**
	 * {@code seal_open} variant {@link PuzzleEffects#SEAL_PUZZLE}: a puzzle seal opens with a dust
	 * burst from its seams, then its face crumbles away in chunks of its stone. Called from the M1
	 * {@code seal_open} handler, which keeps variant 0 (the resonant seal) as it was.
	 */
	public static void puzzleSealOpen(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		sound(level, p.pos(), ModSounds.PUZZLE_SEAL_OPEN, 1f, vary(random, 1f));

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		BlockPos pos = BlockPos.containing(p.pos());
		BlockState state = level.getBlockState(pos);
		Skin skin = state.getBlock() instanceof PuzzleSealBlock ? state.getValue(PuzzleStates.SKIN) : Skin.RUIN;
		List<BlockPos> cells = group(level, pos, PuzzleBlocks.PUZZLE_SEAL, s -> true, size(p));

		for (int i = 0, n = Vfx.count(Math.min(36, 8 + cells.size() * 4)); i < n; i++) {
			BlockPos cell = cells.get(random.nextInt(cells.size()));
			Direction face = exposedFace(level, cell, random);

			if (face != null) {
				Vec3 v = normal(face).scale(0.05 + random.nextDouble() * 0.05).add(0, 0.01, 0);
				spawn(level, WorldParticles.TOMB_DUST, seamPoint(cell, face, random, 0.04), v);
			}
		}

		for (int i = 0, n = Vfx.count(Math.min(10, 2 + cells.size())); i < n; i++) {
			BlockPos cell = cells.get(random.nextInt(cells.size()));
			Direction face = exposedFace(level, cell, random);

			if (face != null) {
				spawn(level, WorldParticles.VENT_GRIT, seamPoint(cell, face, random, 0.04), normal(face).scale(0.08).add(0, 0.04, 0));
			}
		}

		add(new Lingering(Kind.SEAL_CRUMBLE, pos, cells, skinStone(skin.ordinal()), 0, 4, 20));
	}

	/** A puzzle was solved: a soft resolving chord for this player alone. */
	private static void solved(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		float volume = 0.8f * ClientConfig.get().modSoundVolume;

		if (volume > 0.001f) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.PUZZLE_SOLVED, 1f, volume));
		}
	}

	/** A pedestal became ready: thin light along its seams, then slow motes drift up from the cradle. */
	private static void pedestalReady(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		int kind = Mth.clamp(p.variant(), 0, PEDESTAL_PITCH.length - 1);
		sound(level, p.pos(), ModSounds.PUZZLE_PEDESTAL_READY, 0.9f, PEDESTAL_PITCH[kind] * (0.98f + random.nextFloat() * 0.04f));

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		BlockPos pos = BlockPos.containing(p.pos());

		// The column's seams (the column is 8 px wide, so its faces are 0.25 from the centre).
		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			Direction side = HORIZONTAL[random.nextInt(4)];
			Vec3 at = Vec3.atBottomCenterOf(pos).add(normal(side).scale(0.27)).add(0, 0.2 + random.nextDouble() * 0.5, 0);
			spawn(level, WorldParticles.LIGHT_MOTE, at, new Vec3(0, 0.008, 0));
		}

		add(new Lingering(Kind.PEDESTAL_MOTES, pos, List.of(), null, kind, 0, 40));
	}

	/** Tell (grit dancing up through the grate with a thin wisp, for the tell's length) or the puff of miasma. */
	private static void tombVent(Minecraft client, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		RandomSource random = level.getRandom();
		BlockPos pos = BlockPos.containing(p.pos());

		if (p.variant() == PuzzleEffects.VENT_TELL) {
			sound(level, p.pos(), ModSounds.VENT_HISS, 0.9f, vary(random, 1f));

			if (Vfx.inRange(p.pos())) {
				for (int i = 0, n = Vfx.tellCount(4); i < n; i++) {
					danceGrit(level, pos, random);
				}

				add(new Lingering(Kind.VENT_TELL, pos, Mth.clamp(Math.round(p.intensity()), 1, 40)));
			}

			return;
		}

		clear(Kind.VENT_TELL, pos);
		sound(level, p.pos(), ModSounds.VENT_PUFF, 1f, vary(random, 1f));

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		// The cloud fills a column 1.5 wide and 2 high over the grate; the particles linger about 30 ticks.
		for (int i = 0, n = Vfx.count(14); i < n; i++) {
			Vec3 at = puffPoint(pos, random, 1.7);
			Vec3 v = new Vec3((random.nextDouble() - 0.5) * 0.02, 0.01 + random.nextDouble() * 0.01, (random.nextDouble() - 0.5) * 0.02);
			spawn(level, WorldParticles.MIASMA, at, v);
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			Vec3 at = new Vec3(pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.02, pos.getZ() + 0.2 + random.nextDouble() * 0.6);
			spawn(level, WorldParticles.VENT_GRIT, at, new Vec3((random.nextDouble() - 0.5) * 0.06, 0.22 + random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.06));
		}

		add(new Lingering(Kind.VENT_PUFF, pos, 8));
	}

	// ---- lingering looks ----

	/** What a lingering look draws each tick. */
	private enum Kind {
		URN_PLUME,
		URN_CURTAIN,
		COUNTERWEIGHT_TRICKLE,
		ANCHOR_STRAIN,
		RUBBLE_SHUDDER,
		SEAL_CRUMBLE,
		PEDESTAL_MOTES,
		VENT_TELL,
		VENT_PUFF
	}

	/**
	 * A look that keeps drawing for {@code duration} ticks after its event, starting after
	 * {@code delay}. {@code cells} is the group it covers (seals), {@code stone} the block whose
	 * chunks it sheds, {@code value} a per-kind number (the ceiling height of a curtain, the pedestal
	 * kind).
	 */
	private static final class Lingering {
		private final Kind kind;
		private final BlockPos pos;
		private final List<BlockPos> cells;
		private final @Nullable BlockState stone;
		private final double value;
		private final int delay;
		private final int duration;
		private int age;

		Lingering(Kind kind, BlockPos pos, List<BlockPos> cells, @Nullable BlockState stone, double value, int delay, int duration) {
			this.kind = kind;
			this.pos = pos.immutable();
			this.cells = cells;
			this.stone = stone;
			this.value = value;
			this.delay = delay;
			this.duration = duration;
		}

		Lingering(Kind kind, BlockPos pos, int duration) {
			this(kind, pos, List.of(), null, 0, 0, duration);
		}
	}

	/** Client tick: draws every lingering look and drops the finished ones. */
	static void tick(Minecraft client) {
		ClientLevel level = client.level;

		if (level != lastLevel) {
			LINGERING.clear();
			lastLevel = level;
		}

		if (level == null || LINGERING.isEmpty()) {
			return;
		}

		RandomSource random = level.getRandom();
		Iterator<Lingering> it = LINGERING.iterator();

		while (it.hasNext()) {
			Lingering look = it.next();
			look.age++;

			if (look.age > look.duration || ended(level, look)) {
				it.remove();
				continue;
			}

			if (look.age > look.delay && Vfx.inRange(Vec3.atCenterOf(look.pos))) {
				draw(level, look, random);
			}
		}
	}

	/** Disconnect: forget every lingering look. */
	static void reset() {
		LINGERING.clear();
		lastLevel = null;
	}

	/**
	 * Whether the block of a state-bound look has moved on. Effect payloads usually arrive before
	 * the block update of the same tick, so this only ends a look at its block's <em>next</em> state.
	 */
	private static boolean ended(ClientLevel level, Lingering look) {
		BlockState state = level.getBlockState(look.pos);

		return switch (look.kind) {
			case COUNTERWEIGHT_TRICKLE -> !(state.getBlock() instanceof CounterweightStoneBlock) || !state.getValue(PuzzleStates.SUNK);
			case ANCHOR_STRAIN -> !(state.getBlock() instanceof PullAnchorBlock) || state.getValue(PuzzleStates.STRAIN) == 2;
			case RUBBLE_SHUDDER -> !(state.getBlock() instanceof CollapsedRubbleBlock) || state.getValue(PuzzleStates.CLEARED);
			default -> false;
		};
	}

	private static void draw(ClientLevel level, Lingering look, RandomSource random) {
		BlockPos pos = look.pos;
		int age = look.age;

		switch (look.kind) {
			case URN_PLUME -> {
				Vec3 mouth = urnMouth(pos);

				for (int i = 0, n = Vfx.count(1); i < n; i++) {
					Vec3 at = mouth.add((random.nextDouble() - 0.5) * 0.14, 0, (random.nextDouble() - 0.5) * 0.14);
					spawn(level, WorldParticles.VEIL_DUST, at, new Vec3((random.nextDouble() - 0.5) * 0.01, 0.02 + random.nextDouble() * 0.015, (random.nextDouble() - 0.5) * 0.01));
				}

				if (age % 3 == 0) {
					double rise = 0.1 + age / (double) look.duration * 0.5;
					spawn(level, WorldParticles.LIGHT_MOTE, jitter(mouth.add(0, rise, 0), random, 0.08), new Vec3(0, 0.012, 0));
				}
			}
			case URN_CURTAIN -> {
				for (int i = 0, n = Vfx.count(2); i < n; i++) {
					spawn(level, WorldParticles.TOMB_DUST, curtainPoint(pos, look.value, random), new Vec3(0, -0.03 - random.nextDouble() * 0.03, 0));
				}

				if (age % 2 == 0) {
					spawn(level, WorldParticles.VENT_GRIT, curtainPoint(pos, look.value, random), new Vec3(0, -0.05, 0));
				}
			}
			case COUNTERWEIGHT_TRICKLE -> {
				if (age % 2 == 0) {
					Direction side = HORIZONTAL[random.nextInt(4)];
					spawn(level, WorldParticles.VENT_GRIT, facePoint(pos, side, random.nextDouble(), 0.98, -0.03), new Vec3(0, -0.02, 0));
				}

				if (age % 6 == 0) {
					Direction side = HORIZONTAL[random.nextInt(4)];
					spawn(level, WorldParticles.TOMB_DUST, facePoint(pos, side, random.nextDouble(), 0.98, -0.02), new Vec3(0, -0.006, 0));
				}
			}
			case ANCHOR_STRAIN -> {
				for (int i = 0, n = Vfx.count(1); i < n; i++) {
					shedRust(level, pos, random);
				}

				if (age % 3 == 0) {
					Direction side = exposedSide(level, pos, random);
					Vec3 at = facePoint(pos, side, 0.3 + random.nextDouble() * 0.4, 0.3 + random.nextDouble() * 0.4, 0.06);
					spawn(level, WorldParticles.TOMB_DUST, at, normal(side).scale(0.01).add(0, -0.005, 0));
				}
			}
			case RUBBLE_SHUDDER -> {
				for (int i = 0, n = Vfx.count(1); i < n; i++) {
					trickle(level, pos, random);
				}

				if (age % 3 == 0) {
					ParticleOptions chunk = new BlockParticleOption(ParticleTypes.BLOCK, PuzzleBlocks.COLLAPSED_RUBBLE.defaultBlockState());
					Direction side = exposedSide(level, pos, random);
					spawn(level, chunk, facePoint(pos, side, random.nextDouble(), 0.5 + random.nextDouble() * 0.5, 0.04), new Vec3(0, -0.02, 0));
				}

				if (age % 4 == 0) {
					spawn(level, WorldParticles.TOMB_DUST, jitter(Vec3.atCenterOf(pos), random, 0.6), new Vec3(0, -0.004, 0));
				}
			}
			case SEAL_CRUMBLE -> {
				if (look.stone == null || look.cells.isEmpty()) {
					return;
				}

				ParticleOptions chunk = new BlockParticleOption(ParticleTypes.BLOCK, look.stone);

				for (int i = 0, n = Vfx.count(Math.min(6, 1 + look.cells.size() / 3)); i < n; i++) {
					BlockPos cell = look.cells.get(random.nextInt(look.cells.size()));
					Direction face = exposedFace(level, cell, random);
					Vec3 at = face == null ? inside(cell, random) : facePoint(cell, face, random.nextDouble(), random.nextDouble(), 0.02);
					Vec3 out = face == null ? Vec3.ZERO : normal(face).scale(0.02);
					spawn(level, chunk, at, out.add(0, -0.02, 0));
				}

				if (age % 2 == 0) {
					BlockPos cell = look.cells.get(random.nextInt(look.cells.size()));
					spawn(level, WorldParticles.VEIL_DUST, inside(cell, random), new Vec3(0, -0.01, 0));
				}
			}
			case PEDESTAL_MOTES -> {
				if (age % 2 == 0) {
					Vec3 at = new Vec3(pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 15 / 16.0 + 0.05,
							pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5);
					spawn(level, WorldParticles.LIGHT_MOTE, at, new Vec3(0, 0.012 + random.nextDouble() * 0.01, 0));
				}

				if (age % 6 == 0) {
					int kind = Mth.clamp((int) look.value, 0, PEDESTAL_TINT.length - 1);
					Vec3 at = jitter(Vec3.atBottomCenterOf(pos).add(0, 1.0, 0), random, 0.2);
					spawn(level, PEDESTAL_TINT[kind], at, new Vec3(0, 0.01, 0));
				}
			}
			case VENT_TELL -> {
				for (int i = 0, n = Vfx.tellCount(2); i < n; i++) {
					danceGrit(level, pos, random);
				}

				if (age % 4 == 0) {
					for (int i = 0, n = Vfx.tellCount(1); i < n; i++) {
						Vec3 at = new Vec3(pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 1.05, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3);
						spawn(level, WorldParticles.MIASMA, at, new Vec3(0, 0.03, 0));
					}
				}
			}
			case VENT_PUFF -> {
				for (int i = 0, n = Vfx.count(2); i < n; i++) {
					Vec3 at = puffPoint(pos, random, 0.6);
					spawn(level, WorldParticles.MIASMA, at, new Vec3((random.nextDouble() - 0.5) * 0.02, 0.04 + random.nextDouble() * 0.05, (random.nextDouble() - 0.5) * 0.02));
				}
			}
		}
	}

	private static void add(Lingering look) {
		LINGERING.removeIf(other -> other.kind == look.kind && other.pos.equals(look.pos));

		if (LINGERING.size() >= MAX_LINGERING) {
			LINGERING.remove(0);
		}

		LINGERING.add(look);
	}

	private static void clear(Kind kind, BlockPos pos) {
		LINGERING.removeIf(look -> look.kind == kind && look.pos.equals(pos));
	}

	// ---- shapes of the looks ----

	/** A flake of rust (or a chip of the anchor's stone) shaken off one of the ring faces. */
	private static void shedRust(ClientLevel level, BlockPos pos, RandomSource random) {
		Direction side = exposedSide(level, pos, random);
		Vec3 at = facePoint(pos, side, 0.3 + random.nextDouble() * 0.4, 0.3 + random.nextDouble() * 0.4, 0.06);
		Vec3 v = normal(side).scale(0.015 + random.nextDouble() * 0.015).add((random.nextDouble() - 0.5) * 0.01, -0.01, (random.nextDouble() - 0.5) * 0.01);
		ParticleOptions flake = random.nextInt(3) == 0 ? new BlockParticleOption(ParticleTypes.BLOCK, PuzzleBlocks.PULL_ANCHOR.defaultBlockState()) : RUST;
		spawn(level, flake, at, v);
	}

	/** A grit chip sliding off the upper half of a shuddering heap. */
	private static void trickle(ClientLevel level, BlockPos pos, RandomSource random) {
		Direction side = exposedSide(level, pos, random);
		Vec3 at = facePoint(pos, side, random.nextDouble(), 0.55 + random.nextDouble() * 0.45, 0.04);
		spawn(level, WorldParticles.VENT_GRIT, at, normal(side).scale(0.02).add(0, -0.01, 0));
	}

	/** A grit chip tossed up through the vent grate. */
	private static void danceGrit(ClientLevel level, BlockPos pos, RandomSource random) {
		Vec3 at = new Vec3(pos.getX() + 0.15 + random.nextDouble() * 0.7, pos.getY() + 1.02, pos.getZ() + 0.15 + random.nextDouble() * 0.7);
		Vec3 v = new Vec3((random.nextDouble() - 0.5) * 0.04, 0.1 + random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.04);
		spawn(level, WorldParticles.VENT_GRIT, at, v);
	}

	/** A point in the miasma column over a vent: 1.5 wide, from the grate up to {@code height}. */
	private static Vec3 puffPoint(BlockPos pos, RandomSource random, double height) {
		return new Vec3(pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 1.5, pos.getY() + 1.0 + random.nextDouble() * height,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 1.5);
	}

	/** A point just under the ceiling, in the 3 × 3 block area centred on {@code pos}. */
	private static Vec3 curtainPoint(BlockPos pos, double ceiling, RandomSource random) {
		return new Vec3(pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 3, ceiling - 0.05 - random.nextDouble() * 0.2,
				pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 3);
	}

	/** The underside of the first non-air block above {@code pos}, or four blocks up when none is close. */
	private static double ceilingAbove(ClientLevel level, BlockPos pos) {
		for (int dy = 1; dy <= CEILING_SEARCH; dy++) {
			if (!level.getBlockState(pos.above(dy)).isAir()) {
				return pos.getY() + dy;
			}
		}

		return pos.getY() + 4;
	}

	/** The mouth of an echo urn (its shape is 14 px tall). */
	private static Vec3 urnMouth(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + 14 / 16.0 + 0.02, pos.getZ() + 0.5);
	}

	// ---- geometry ----

	/**
	 * The connected group of {@code start}: a 6-neighbour flood fill over {@code block} whose state
	 * passes {@code member}, at most {@code max} cells. Just {@code start} when it is not such a block
	 * (the state update has not arrived yet, or the chunk is not loaded), so the look still plays there.
	 */
	private static List<BlockPos> group(ClientLevel level, BlockPos start, Block block, Predicate<BlockState> member, int max) {
		Set<BlockPos> found = new LinkedHashSet<>();
		Set<BlockPos> seen = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start.immutable());

		while (!queue.isEmpty() && found.size() < max) {
			BlockPos next = queue.poll();

			if (!seen.add(next)) {
				continue;
			}

			BlockState state = level.getBlockState(next);

			if (!state.is(block) || !member.test(state)) {
				continue;
			}

			found.add(next);

			for (Direction direction : Direction.values()) {
				queue.add(next.relative(direction));
			}
		}

		return found.isEmpty() ? List.of(start.immutable()) : new ArrayList<>(found);
	}

	/** A random face of {@code pos} that opens onto air, or null when it is buried. */
	private static @Nullable Direction exposedFace(ClientLevel level, BlockPos pos, RandomSource random) {
		Direction[] all = Direction.values();
		int first = random.nextInt(all.length);

		for (int i = 0; i < all.length; i++) {
			Direction face = all[(first + i) % all.length];

			if (level.getBlockState(pos.relative(face)).isAir()) {
				return face;
			}
		}

		return null;
	}

	/** A random side face of {@code pos} that opens onto air; any side when none does. */
	private static Direction exposedSide(ClientLevel level, BlockPos pos, RandomSource random) {
		int first = random.nextInt(4);

		for (int i = 0; i < 4; i++) {
			Direction side = HORIZONTAL[(first + i) % 4];

			if (level.getBlockState(pos.relative(side)).isAir()) {
				return side;
			}
		}

		return HORIZONTAL[first];
	}

	/**
	 * A point on {@code face} of the cell {@code pos}: {@code u}, {@code v} are face-local (0 to 1;
	 * {@code v} is height on a side face), {@code out} is the offset along the face normal (negative
	 * is inside the cell).
	 */
	static Vec3 facePoint(BlockPos pos, Direction face, double u, double v, double out) {
		if (face.getStepX() != 0) {
			return new Vec3(pos.getX() + across(face.getStepX(), out), pos.getY() + v, pos.getZ() + u);
		}

		if (face.getStepY() != 0) {
			return new Vec3(pos.getX() + u, pos.getY() + across(face.getStepY(), out), pos.getZ() + v);
		}

		return new Vec3(pos.getX() + u, pos.getY() + v, pos.getZ() + across(face.getStepZ(), out));
	}

	/** The cell-local coordinate of a face plane on its axis, moved {@code out} along its normal. */
	private static double across(int step, double out) {
		return step > 0 ? 1 + out : -out;
	}

	/** A point on a mortar line of {@code face}: a bed joint (a course) or a staggered head joint. */
	private static Vec3 seamPoint(BlockPos pos, Direction face, RandomSource random, double out) {
		double u = random.nextDouble();
		double v = random.nextDouble();

		if (random.nextBoolean()) {
			v = SEAMS[random.nextInt(SEAMS.length)];
		} else {
			u = v < 0.5 ? 0.25 : 0.75;
		}

		return facePoint(pos, face, u, v, out);
	}

	private static Vec3 normal(Direction face) {
		return new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
	}

	private static Vec3 inside(BlockPos pos, RandomSource random) {
		return new Vec3(pos.getX() + 0.1 + random.nextDouble() * 0.8, pos.getY() + 0.1 + random.nextDouble() * 0.8, pos.getZ() + 0.1 + random.nextDouble() * 0.8);
	}

	private static Vec3 centroid(List<BlockPos> cells) {
		double x = 0;
		double y = 0;
		double z = 0;

		for (BlockPos cell : cells) {
			x += cell.getX() + 0.5;
			y += cell.getY() + 0.5;
			z += cell.getZ() + 0.5;
		}

		return new Vec3(x / cells.size(), y / cells.size(), z / cells.size());
	}

	private static Vec3 jitter(Vec3 pos, RandomSource random, double spread) {
		return pos.add((random.nextDouble() - 0.5) * 2 * spread, (random.nextDouble() - 0.5) * 2 * spread, (random.nextDouble() - 0.5) * 2 * spread);
	}

	private static Vec3 randomDirection(RandomSource random) {
		double y = random.nextDouble() * 2 - 1;
		double angle = random.nextDouble() * Mth.TWO_PI;
		double r = Math.sqrt(1 - y * y);
		return new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r);
	}

	/** The plain stone a skin is dressed as, for chunk particles. */
	private static BlockState skinStone(int skinOrdinal) {
		Skin[] skins = Skin.values();

		return switch (skins[Mth.clamp(skinOrdinal, 0, skins.length - 1)]) {
			case RUIN -> PuzzleBlocks.RUIN_STONE.defaultBlockState();
			case TOMB -> PuzzleBlocks.TOMB_STONE.defaultBlockState();
			case WRECK -> PuzzleBlocks.WRECK_PLATING.defaultBlockState();
		};
	}

	/** The group size an event carries in its intensity. */
	private static int size(EffectEventPayload p) {
		return Mth.clamp(Math.round(p.intensity()), 1, MAX_GROUP);
	}

	// ---- sound and shake ----

	private static float vary(RandomSource random, float pitch) {
		return pitch * (0.95f + random.nextFloat() * 0.1f);
	}

	private static void sound(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		float scaled = volume * ClientConfig.get().modSoundVolume;

		if (scaled > 0.001f) {
			level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, scaled, pitch, false);
		}
	}

	/** A small jolt for the local player within {@code radius} of {@code pos}, fading with distance. */
	private static void shake(Minecraft client, Vec3 pos, double radius, float strength) {
		if (client.player == null) {
			return;
		}

		double distance = client.player.position().distanceTo(pos);

		if (distance < radius) {
			CameraShake.add(strength * (float) (1 - distance / radius));
		}
	}

	private static void spawn(ClientLevel level, ParticleOptions type, Vec3 at, Vec3 v) {
		level.addParticle(type, at.x, at.y, at.z, v.x, v.y, v.z);
	}

	private PuzzleEffectHandlers() {
	}
}
