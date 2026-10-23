package com.oldrepublic.client.holocron;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.holocron.HolocronEffects;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.holocron.HolocronParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client look and sound of the holocron effect events (spec 4.4, 4.7, 4.8, 13.3). Everything is
 * built from seam light, dust, falling grit and the projected figure, never rings, glyphs or
 * flashes: with flashing effects off every flare becomes a steady glow. All particles go through
 * {@link Vfx}; the attunement moment with its figure stays within 120 particles at Standard.
 *
 * <ul>
 * <li>{@code holocron_attune}: the holocron rises out of its cradle ({@link RisingHolocron}), its
 * seams split with thin light and alcove dust drifts upward; or it sinks back.</li>
 * <li>{@code holocron_gatekeeper}: the figure forms from the seams ({@link GatekeeperFigure}) with
 * the hum ({@link HolocronHumSound}), or folds back.</li>
 * <li>{@code holocron_choice}: seal (the seams close with a dust exhale), break (the pale stone
 * splits, gold light escapes and gutters out, chips and gold dust fall), listen (motes flow into the
 * player's chest), or remember (the holocron settles into the player's hands).</li>
 * <li>{@code holocron_echo}: a faint gatekeeper silhouette, then a shard forms in the cradle.</li>
 * <li>{@code archive_choice}: return (the three dim, a dust exhale, the clasp), keep (their seams
 * warm and motes drift into the player), unmake (the seams flare, light drains out as dust, the
 * shells crack and slump).</li>
 * </ul>
 */
final class HolocronFx {
	private static final RandomSource RANDOM = RandomSource.create();
	/** Pale sandy alcove dust. */
	private static final int DUST = 0xB8AC94;
	/** Pale crystal light of a forming shard. */
	private static final int CRYSTAL = 0xE6F4FF;
	/** The three Archive holocrons sit along the plinth's top (x offsets from its centre). */
	private static final double[] ARCHIVE_SLOTS = {-0.55, 0, 0.55};

	/** A short-lived emitter: {@code step} runs once per client tick from {@code start} for {@code ticks}. */
	private record Task(long start, int ticks, Step step) {
	}

	@FunctionalInterface
	private interface Step {
		void run(ClientLevel level, int age);
	}

	private static final List<Task> TASKS = new ArrayList<>();

	static void init() {
		ClientEffects.register(HolocronEffects.ATTUNE, HolocronFx::attune);
		ClientEffects.register(HolocronEffects.GATEKEEPER, HolocronFx::gatekeeper);
		ClientEffects.register(HolocronEffects.CHOICE, HolocronFx::choice);
		ClientEffects.register(HolocronEffects.ECHO, HolocronFx::echo);
		ClientEffects.register(HolocronEffects.ARCHIVE, HolocronFx::archive);
	}

	// ---------------------------------------------------------------- attunement

	private static void attune(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 top = payload.pos();
		HolocronKind kind = kind(payload.variant());
		int tint = GatekeeperFigure.tint(kind);

		if (payload.intensity() <= 0f) {
			RisingHolocron.sink(top);
			settle(top, tint);
			return;
		}

		int riseTicks = Math.max(10, payload.entityId());
		RisingHolocron.rise(top, kind, riseTicks);
		play(level, top, ModSounds.HOLOCRON_RISE, 0.9f, 1f);

		// Alcove dust drifts upward the whole rise; at seven tenths the seams split with thin light.
		schedule(0, riseTicks, (lvl, age) -> {
			if (age % 2 == 0 && Vfx.inRange(top)) {
				Vec3 at = top.add(RANDOM.nextGaussian() * 0.6, -0.4 + RANDOM.nextDouble() * 0.5, RANDOM.nextGaussian() * 0.6);
				HolocronMoteParticle dust = mote(at, new Vec3(0, 0.012 + RANDOM.nextDouble() * 0.01, 0));

				if (dust != null) {
					dust.dust().tint(DUST).size(0.05f, 0.03f).alpha(0.5f).life(30 + RANDOM.nextInt(16));
				}
			}
		});
		schedule(riseTicks * 7 / 10, 1, (lvl, age) -> {
			Vec3 centre = RisingHolocron.heldCentre(top).subtract(0, 0.12, 0);
			play(lvl, centre, ModSounds.HOLOCRON_OPEN, 0.8f, 1f);
			seamSplit(centre, tint);
		});
	}

	/** The seams open: thin light spills sideways out of the holocron's edges. */
	private static void seamSplit(Vec3 centre, int tint) {
		if (!Vfx.inRange(centre)) {
			return;
		}

		int n = Vfx.count(12);

		for (int i = 0; i < n; i++) {
			double angle = Mth.TWO_PI * i / n + RANDOM.nextDouble() * 0.3;
			Vec3 out = new Vec3(Math.cos(angle), (RANDOM.nextDouble() - 0.5) * 0.4, Math.sin(angle));
			HolocronMoteParticle light = mote(centre.add(out.scale(0.16)), out.scale(0.02));

			if (light != null) {
				light.tint(tint).size(0.04f, 0.012f).alpha(0.8f).drag(0.9f).life(12 + RANDOM.nextInt(6));
			}
		}
	}

	/** The holocron sinks back: a little grit settles off the cradle. */
	private static void settle(Vec3 top, int tint) {
		if (!Vfx.inRange(top)) {
			return;
		}

		for (int i = 0, spawned = Vfx.count(8); i < spawned; i++) {
			Vec3 at = top.add(RANDOM.nextGaussian() * 0.25, 0.2 + RANDOM.nextDouble() * 0.4, RANDOM.nextGaussian() * 0.25);
			HolocronMoteParticle grit = mote(at, new Vec3(0, -0.01, 0));

			if (grit != null) {
				grit.dust().tint(DUST).size(0.04f, 0.03f).alpha(0.45f).fall(0.04f).life(26 + RANDOM.nextInt(10));
			}
		}

		for (int i = 0, spawned = Vfx.count(4); i < spawned; i++) {
			HolocronMoteParticle light = mote(top.add(RANDOM.nextGaussian() * 0.12, 0.3, RANDOM.nextGaussian() * 0.12), new Vec3(0, -0.015, 0));

			if (light != null) {
				light.tint(tint).size(0.03f, 0.01f).alpha(0.5f).life(16);
			}
		}
	}

	private static void gatekeeper(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 top = payload.pos();
		BlockPos pedestal = BlockPos.containing(top.x, top.y - 0.5, top.z);
		Vec3 source = RisingHolocron.heldCentre(top);
		// The server sends the unit vector from the pedestal toward the player (zero when it has none).
		Vec3 toward = payload.dir().lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : payload.dir();

		if (payload.intensity() > 0f) {
			GatekeeperFigure.show(pedestal, toward, payload.variant());
			play(level, source, ModSounds.HOLOCRON_GATEKEEPER_FORM, 0.9f, 1f);
			HolocronHumSound.play(source);
		} else {
			GatekeeperFigure.fold(payload.variant());
			play(level, source, ModSounds.HOLOCRON_GATEKEEPER_FADE, 0.9f, 1f);
			HolocronHumSound.fadeOut();
		}
	}

	// ---------------------------------------------------------------- choices

	private static void choice(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity player) {
		Vec3 top = payload.pos();
		boolean atPedestal = payload.intensity() > 0f;
		Vec3 source = atPedestal ? RisingHolocron.heldCentre(top) : top;
		int choice = payload.variant() & HolocronEffects.CHOICE_MASK;
		HolocronKind kind = kind(payload.variant() >>> HolocronEffects.KIND_SHIFT);
		int tint = GatekeeperFigure.tint(kind);

		switch (choice) {
			case HolocronEffects.CHOICE_SEAL -> {
				RisingHolocron.taken(top, payload.entityId(), true);
				play(level, source, ModSounds.HOLOCRON_SEAL, 0.9f, 1f);
				exhale(source, tint);
			}
			case HolocronEffects.CHOICE_BREAK -> {
				RisingHolocron.shattered(top);
				play(level, source, ModSounds.HOLOCRON_BREAK, 1f, 1f);
				shatter(level, source, kind, tint);
			}
			case HolocronEffects.CHOICE_LISTEN -> {
				RisingHolocron.taken(top, payload.entityId(), false);
				play(level, source, ModSounds.HOLOCRON_LISTEN, 0.9f, 1f);
				flowInto(source, payload.entityId(), tint, 24, 2);
			}
			case HolocronEffects.CHOICE_OWN -> {
				RisingHolocron.taken(top, payload.entityId(), false);
				flowInto(source, payload.entityId(), tint, 10, 1);
			}
			default -> {
			}
		}
	}

	/** Seal: the seams close and push out a soft ring-less exhale of dust, low and uneven. */
	private static void exhale(Vec3 source, int tint) {
		if (!Vfx.inRange(source)) {
			return;
		}

		int n = Vfx.count(14);

		for (int i = 0; i < n; i++) {
			double angle = RANDOM.nextDouble() * Mth.TWO_PI;
			double speed = 0.02 + RANDOM.nextDouble() * 0.025;
			Vec3 out = new Vec3(Math.cos(angle) * speed, (RANDOM.nextDouble() - 0.6) * 0.012, Math.sin(angle) * speed);
			HolocronMoteParticle dust = mote(source.add(out.scale(4)), out);

			if (dust != null) {
				dust.dust().tint(DUST).size(0.06f, 0.09f).alpha(0.45f).drag(0.9f).life(22 + RANDOM.nextInt(12));
			}
		}

		for (int i = 0, spawned = Vfx.count(5); i < spawned; i++) {
			HolocronMoteParticle light = mote(source.add(RANDOM.nextGaussian() * 0.08, 0, RANDOM.nextGaussian() * 0.08), Vec3.ZERO);

			if (light != null) {
				// The last seam light, drawn back in.
				light.tint(tint).size(0.05f, 0.005f).alpha(0.6f).life(10);
			}
		}
	}

	/** Break: the pale stone splits along its seams; gold light escapes and gutters out; chips and gold dust fall. */
	private static void shatter(ClientLevel level, Vec3 source, HolocronKind kind, int tint) {
		if (!Vfx.inRange(source)) {
			return;
		}

		BlockState stone = chips(kind);

		for (int i = 0, spawned = Vfx.count(14); i < spawned; i++) {
			Vec3 v = new Vec3(RANDOM.nextGaussian() * 0.08, 0.05 + RANDOM.nextDouble() * 0.12, RANDOM.nextGaussian() * 0.08);
			level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, stone), source.x, source.y, source.z, v.x, v.y, v.z);
		}

		int escape = Vfx.count(12);

		for (int i = 0; i < escape; i++) {
			double angle = Mth.TWO_PI * i / escape;
			Vec3 out = new Vec3(Math.cos(angle) * 0.05, 0.01 + RANDOM.nextDouble() * 0.03, Math.sin(angle) * 0.05);
			HolocronMoteParticle light = mote(source, out);

			if (light != null) {
				// Gutters out quickly; with flashing off it is a dimmer, slower glow, never a flash.
				boolean flashing = Vfx.flashing();
				light.tint(tint).size(flashing ? 0.09f : 0.06f, 0.01f).alpha(flashing ? 0.85f : 0.5f).drag(0.88f).life(flashing ? 10 : 18);
			}
		}

		schedule(4, 16, (lvl, age) -> {
			if (age % 2 == 0) {
				Vec3 at = source.add(RANDOM.nextGaussian() * 0.2, RANDOM.nextDouble() * 0.2, RANDOM.nextGaussian() * 0.2);
				HolocronMoteParticle dust = mote(at, new Vec3(0, -0.02, 0));

				if (dust != null) {
					dust.dust().tint(tint).size(0.035f, 0.02f).alpha(0.6f).fall(0.06f).life(26);
				}
			}
		});
	}

	/** Motes stream from {@code source} into the player's chest for {@code ticks}, {@code perTick} at a time. */
	private static void flowInto(Vec3 source, int playerId, int tint, int ticks, int perTick) {
		schedule(0, ticks, (lvl, age) -> {
			Entity player = lvl.getEntity(playerId);

			if (player == null || !Vfx.inRange(source)) {
				return;
			}

			Vec3 chest = player.position().add(0, player.getBbHeight() * 0.62, 0);

			for (int i = 0, spawned = Math.min(perTick, Vfx.count(perTick)); i < spawned; i++) {
				Vec3 from = source.add(RANDOM.nextGaussian() * 0.06, RANDOM.nextGaussian() * 0.06, RANDOM.nextGaussian() * 0.06);
				Vec3 path = chest.subtract(from);
				int life = Mth.clamp((int) (path.length() / 0.12), 6, 40);
				HolocronMoteParticle mote = mote(from, path.scale(1.0 / life));

				if (mote != null) {
					mote.tint(tint).size(0.045f, 0.02f).alpha(0.7f).life(life);
				}
			}
		});
	}

	// ---------------------------------------------------------------- echoes

	private static void echo(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity player) {
		Vec3 top = payload.pos();
		BlockPos pedestal = BlockPos.containing(top.x, top.y - 0.5, top.z);
		Vec3 toward = player == null ? new Vec3(0, 0, 1) : player.position().subtract(top);
		GatekeeperFigure.glimpse(pedestal, toward, payload.variant());
		play(level, top, ModSounds.HOLOCRON_ECHO, 0.9f, 1f);

		// After the silhouette, crystal light gathers in the cradle and a shard forms there.
		Vec3 cradle = top.subtract(0, 0.02, 0);
		schedule(18, 16, (lvl, age) -> {
			if (!Vfx.inRange(cradle)) {
				return;
			}

			double angle = RANDOM.nextDouble() * Mth.TWO_PI;
			double radius = 0.4 + RANDOM.nextDouble() * 0.25;
			Vec3 from = cradle.add(Math.cos(angle) * radius, 0.15 + RANDOM.nextDouble() * 0.3, Math.sin(angle) * radius);
			HolocronMoteParticle glint = mote(from, cradle.subtract(from).scale(1.0 / 14));

			if (glint != null) {
				glint.tint(CRYSTAL).size(0.03f, 0.05f).alpha(0.75f).life(14);
			}
		});
	}

	// ---------------------------------------------------------------- the Archive

	private static void archive(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity player) {
		Vec3 top = payload.pos();
		Vec3[] holocrons = new Vec3[ARCHIVE_SLOTS.length];

		for (int i = 0; i < holocrons.length; i++) {
			holocrons[i] = top.add(ARCHIVE_SLOTS[i], 0.18, 0);
		}

		HolocronKind[] kinds = {HolocronKind.JEDI, HolocronKind.SITH, HolocronKind.WAYFARER};

		switch (payload.variant()) {
			case HolocronEffects.ARCHIVE_RETURN -> {
				play(level, top, ModSounds.HOLOCRON_ARCHIVE_RETURN, 1f, 1f);

				for (int i = 0; i < holocrons.length; i++) {
					dim(holocrons[i], GatekeeperFigure.tint(kinds[i]));
				}

				exhale(top.add(0, 0.1, 0), GatekeeperFigure.tint(HolocronKind.WAYFARER));
			}
			case HolocronEffects.ARCHIVE_KEEP -> {
				play(level, top, ModSounds.HOLOCRON_ARCHIVE_KEEP, 1f, 1f);

				for (int i = 0; i < holocrons.length; i++) {
					warm(holocrons[i], GatekeeperFigure.tint(kinds[i]));
					flowInto(holocrons[i], payload.entityId(), GatekeeperFigure.tint(kinds[i]), 22, 1);
				}
			}
			case HolocronEffects.ARCHIVE_UNMAKE -> {
				play(level, top, ModSounds.HOLOCRON_UNMAKE, 1f, 1f);

				for (int i = 0; i < holocrons.length; i++) {
					unmake(level, holocrons[i], kinds[i], i * 3);
				}
			}
			default -> {
			}
		}
	}

	/** Return: the seam light sinks and goes out. */
	private static void dim(Vec3 holocron, int tint) {
		if (!Vfx.inRange(holocron)) {
			return;
		}

		for (int i = 0, spawned = Vfx.count(5); i < spawned; i++) {
			HolocronMoteParticle light = mote(holocron.add(RANDOM.nextGaussian() * 0.08, 0.05, RANDOM.nextGaussian() * 0.08), new Vec3(0, -0.008, 0));

			if (light != null) {
				light.tint(tint).size(0.04f, 0.004f).alpha(0.55f).life(24);
			}
		}
	}

	/** Keep: the seams warm with a slow glow. */
	private static void warm(Vec3 holocron, int tint) {
		if (!Vfx.inRange(holocron)) {
			return;
		}

		for (int i = 0, spawned = Vfx.count(5); i < spawned; i++) {
			Vec3 at = holocron.add(RANDOM.nextGaussian() * 0.1, RANDOM.nextDouble() * 0.12, RANDOM.nextGaussian() * 0.1);
			HolocronMoteParticle glow = mote(at, new Vec3(0, 0.006, 0));

			if (glow != null) {
				glow.tint(tint).size(0.07f, 0.05f).alpha(0.5f).life(30 + RANDOM.nextInt(10));
			}
		}
	}

	/** Unmake: the seams flare (a steady glow with flashing off), light drains out as dust, the shell cracks. */
	private static void unmake(ClientLevel level, Vec3 holocron, HolocronKind kind, int delay) {
		int tint = GatekeeperFigure.tint(kind);
		boolean flashing = Vfx.flashing();

		schedule(delay, 1, (lvl, age) -> {
			if (!Vfx.inRange(holocron)) {
				return;
			}

			for (int i = 0, spawned = Vfx.count(6); i < spawned; i++) {
				Vec3 at = holocron.add(RANDOM.nextGaussian() * 0.06, RANDOM.nextDouble() * 0.1, RANDOM.nextGaussian() * 0.06);
				HolocronMoteParticle flare = mote(at, Vec3.ZERO);

				if (flare != null) {
					flare.tint(tint).size(flashing ? 0.14f : 0.09f, 0.02f).alpha(flashing ? 0.9f : 0.55f).life(flashing ? 8 : 20);
				}
			}
		});
		schedule(delay + 8, 20, (lvl, age) -> {
			if (age % 2 == 0 && Vfx.inRange(holocron)) {
				HolocronMoteParticle drain = mote(holocron.add(RANDOM.nextGaussian() * 0.1, 0.05, RANDOM.nextGaussian() * 0.1),
						new Vec3(RANDOM.nextGaussian() * 0.004, -0.012, RANDOM.nextGaussian() * 0.004));

				if (drain != null) {
					drain.dust().tint(DUST).size(0.04f, 0.06f).alpha(0.5f).fall(0.03f).life(30);
				}
			}
		});
		schedule(delay + 22, 1, (lvl, age) -> {
			if (!Vfx.inRange(holocron)) {
				return;
			}

			BlockState shell = chips(kind);

			for (int i = 0, spawned = Vfx.count(6); i < spawned; i++) {
				lvl.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, shell), holocron.x, holocron.y, holocron.z,
						RANDOM.nextGaussian() * 0.04, 0.02, RANDOM.nextGaussian() * 0.04);
			}
		});
	}

	// ---------------------------------------------------------------- shared

	/** Seam light while a holocron hovers (a mote every few ticks), and the scheduled emitters. */
	static void tick(ClientLevel level) {
		long now = HolocronClient.ticks();

		if (now % 3 == 0) {
			RisingHolocron.forEachHovering((centre, kind) -> {
				if (Vfx.inRange(centre)) {
					Vec3 at = centre.add(RANDOM.nextGaussian() * 0.12, RANDOM.nextGaussian() * 0.12, RANDOM.nextGaussian() * 0.12);
					HolocronMoteParticle light = mote(at, new Vec3(0, 0.006, 0));

					if (light != null) {
						light.tint(GatekeeperFigure.tint(kind)).size(0.03f, 0.01f).alpha(0.55f).life(18);
					}
				}
			});
		}

		Iterator<Task> it = TASKS.iterator();
		List<Task> running = new ArrayList<>();

		while (it.hasNext()) {
			Task task = it.next();
			int age = (int) (now - task.start());

			if (age >= task.ticks()) {
				it.remove();
			} else if (age >= 0) {
				running.add(task);
			}
		}

		// Steps may schedule more tasks, so they run outside the iteration.
		for (Task task : running) {
			task.step().run(level, (int) (now - task.start()));
		}
	}

	static void clear() {
		TASKS.clear();
	}

	private static void schedule(int delay, int ticks, Step step) {
		TASKS.add(new Task(HolocronClient.ticks() + Math.max(0, delay), Math.max(1, ticks), step));
	}

	/** Spawns a {@code holocron_mote}, or returns null when the engine refused it. */
	static @Nullable HolocronMoteParticle mote(Vec3 pos, Vec3 velocity) {
		return spawn(HolocronParticles.HOLOCRON_MOTE, pos, velocity) instanceof HolocronMoteParticle mote ? mote : null;
	}

	/** Spawns a {@code projection_band}, or returns null when the engine refused it. */
	static @Nullable ProjectionBandParticle band(Vec3 pos, Vec3 velocity) {
		return spawn(HolocronParticles.PROJECTION_BAND, pos, velocity) instanceof ProjectionBandParticle band ? band : null;
	}

	private static @Nullable Particle spawn(SimpleParticleType type, Vec3 pos, Vec3 velocity) {
		return Minecraft.getInstance().particleEngine.createParticle(type, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
	}

	private static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume * ClientConfig.get().modSoundVolume,
				pitch * (0.96f + RANDOM.nextFloat() * 0.08f), false);
	}

	private static HolocronKind kind(int ordinal) {
		HolocronKind[] kinds = HolocronKind.values();
		return kinds[Mth.clamp(ordinal, 0, kinds.length - 1)];
	}

	/** Shell chips of a holocron kind: pale stone, black glass, or brass. */
	private static BlockState chips(HolocronKind kind) {
		return switch (kind) {
			case JEDI -> Blocks.CALCITE.defaultBlockState();
			case SITH -> Blocks.BLACKSTONE.defaultBlockState();
			case WAYFARER -> Blocks.GOLD_BLOCK.defaultBlockState();
		};
	}

	private HolocronFx() {
	}
}
