package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.energy.EnergyKit.RANDOM;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.energy.Anchor;
import com.oldrepublic.client.force.energy.EnergyKit;
import com.oldrepublic.client.force.energy.EnergyRender;
import com.oldrepublic.client.force.energy.Glint;
import com.oldrepublic.client.force.energy.Mote;
import com.oldrepublic.client.force.energy.Palette;
import com.oldrepublic.client.force.energy.Tendril;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.network.SenseRevealPayload;
import com.oldrepublic.force.sense.SenseTargets;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Sense visuals and sounds (spec 10.1, 13.1, 13.3).
 *
 * <p><b>Caster</b> ({@code sense_reveal}, the caster's own reveal): a breath. For a moment motes of pale light are drawn in
 * to the player from all round; then they are let go and drift out again, low and unevenly, every way. Then, nearest point
 * first, a thread of light runs out from just in front of the player to each point (beads of light running along it), and
 * where it arrives a small cluster of slow, twinkling motes gathers and stays for the reveal, following a creature as it
 * moves; a sensed block glints now and then. The threads fade a second or two after they arrive. Tints: hostile ember
 * orange, wary amber, neutral grey-white, allied pale blue, blocks pale gold. Each hidden point (any block point, or a
 * creature out of line of sight) gets a soft {@code force.sense.reveal} tick as its thread arrives, at most 6 per cast and at
 * least 3 ticks apart, placed in the point's direction. The 30% ambience dip belongs to the ambience's owner (the world
 * feature dips its beds on the caster's own gesture event). Holocron direction ({@code force_sense} variant 1): three soft
 * pulses of kind-tinted motes fly off along the direction, brighter when closer.
 *
 * <p><b>Everyone</b> ({@code force_sense} variant 0): the cast sound and the free-arm pose {@code SENSE}; others also see a
 * faint breath of pale motes leave the caster's head.
 *
 * <p>No outline, ring or ground wave anywhere: threads and motes of light only. Budget: threads to the 12 nearest points
 * at Standard; cluster motes in waves of at most 5 every 5 ticks, each living about 30 ticks.
 */
final class SenseFx {
	/** Pose length (spec 10.1). */
	static final int GESTURE_TICKS = 10;
	/** The breath: motes are drawn in until this tick, then let go. */
	private static final int INHALE_TICKS = 6;
	private static final int INHALE_MOTES = 10;
	private static final int EXHALE_MOTES = 14;
	/** Cast tick the first thread leaves the player. */
	private static final int STREAM_START = 6;
	/** Threads of one cast at Standard (the nearest points). */
	private static final int THREADS = 12;
	private static final int MIN_STREAM_LIFE = 8;
	private static final int MAX_STREAM_LIFE = 24;
	/** Ticks a thread holds after it arrives, then fades over {@link #THREAD_FADE}. */
	private static final int THREAD_HOLD = 34;
	private static final int THREAD_FADE = 14;
	/** Clusters: one wave every 5 ticks of at most 5 motes living about 30 ticks (30 live at Standard). */
	private static final int CLUSTER_INTERVAL = 5;
	private static final int CLUSTER_WAVE_MAX = 5;
	private static final int CLUSTER_WAVE_MIN = 2;
	private static final int CLUSTER_MOTE_LIFE = 30;
	/** Reveal ticks: at most 6 per cast, 3 ticks apart, heard about 6 blocks away in the point's direction. */
	private static final int MAX_REVEAL_TICKS = 6;
	private static final int REVEAL_TICK_GAP = 3;
	private static final double REVEAL_SOUND_DISTANCE = 6;
	/** Holocron pulses: three, 10 ticks apart, after the breath. */
	private static final int PULSES = 3;
	private static final int PULSE_FIRST = 20;
	private static final int PULSE_GAP = 10;
	private static final int PULSE_MOTES = 4;
	private static final int PULSE_LIFE = 22;
	private static final double PULSE_TRAVEL = 5.5;
	/** Others: the breath of light from the caster's head. */
	private static final int BREATH_MOTES = 5;
	/** {@link EnergyRender} owner kind of a reveal's light (the local player's own). */
	private static final int OWNER_KIND = 11;
	/** The breath's light: pale, a little warm. */
	private static final Palette BREATH = Palette.light(0xE8E2D2);
	private static final Palette BLOCKS = Palette.light(0xFFDC96);
	private static final Palette HOSTILE = Palette.light(0xFF8C40);
	private static final Palette WARY = Palette.light(0xFFC04C);
	private static final Palette NEUTRAL = Palette.light(0xD8D8D0);
	private static final Palette ALLIED = Palette.light(0x9EC6FF);
	private static final Palette JEDI = Palette.light(0xFFD880);
	private static final Palette SITH = Palette.light(0xD86A48);
	private static final Palette WAYFARER = Palette.light(0xDCB46C);

	/** One revealed point: its thread, its cluster and whether it earns a reveal tick. */
	private static final class Mark {
		final SenseTargets.Point point;
		/** Cast tick its thread leaves the player. */
		final int streamStart;
		final boolean threaded;
		/** Cast tick the thread reaches the point; the cluster starts then. -1 until it left. */
		int arrival = -1;
		@Nullable Tendril thread;
		boolean hidden;
		boolean ticked;
		/** Set once the creature is gone: its cluster ends. */
		boolean lost;

		Mark(SenseTargets.Point point, int streamStart, boolean threaded) {
			this.point = point;
			this.streamStart = streamStart;
			this.threaded = threaded;
		}
	}

	/** One holocron direction pulse due at a cast tick. */
	private record Pulse(int at, int index, Vec3 dir, HolocronKind kind, float closeness) {
	}

	private static final List<Mark> MARKS = new ArrayList<>();
	private static final List<Pulse> PULSES_DUE = new ArrayList<>();
	/** Cast tick this reveal was last advanced to, so each tick's work runs once. */
	private static long doneAge = -1;
	private static int revealTicks;
	private static long lastRevealTick = Long.MIN_VALUE;
	private static int clusterCursor;

	/** {@code sense_reveal}: the local player's own cast revealed these points. */
	static void onReveal(Minecraft mc, SenseRevealPayload payload) {
		LocalPlayer player = mc.player;

		if (player == null || mc.level == null) {
			return;
		}

		SenseReveal.set(payload);
		gesturePose(player);
		clear();

		List<SenseTargets.Point> points = payload.points();
		int n = points.size();
		int threads = Vfx.count(THREADS);

		for (int i = 0; i < n; i++) {
			// Nearest first; a large reveal spreads its thread starts over about a second.
			MARKS.add(new Mark(points.get(i), STREAM_START + (n > 16 ? i / 2 : i), i < threads));
		}
	}

	/** {@code force_sense}: variant 0 the gesture (everyone tracking), variant 1 the holocron direction (caster). */
	static void onSense(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		switch (payload.variant()) {
			case ForceEffects.SENSE_GESTURE -> gesture(mc, level, payload, entity);
			case ForceEffects.SENSE_HOLOCRON -> holocron(payload);
			default -> {
			}
		}
	}

	private static void gesture(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity caster)) {
			return;
		}

		boolean own = caster == mc.player;
		EnergyKit.play(level, payload.pos(), ModSounds.FORCE_SENSE_CAST, own ? 0.8f : 0.55f, 1f);
		gesturePose(caster);

		if (!own && Vfx.inRange(payload.pos())) {
			breath(caster);
		}
	}

	/** Starts the free-arm SENSE pose unless it already runs (the reveal and the gesture both ask). */
	private static void gesturePose(LivingEntity caster) {
		if (!PowerAnimations.isActive(caster.getId(), PowerAnimations.Kind.SENSE)) {
			PowerAnimations.start(caster.getId(), PowerAnimations.Kind.SENSE, GESTURE_TICKS, 0);
		}
	}

	/** Others: a faint breath of pale light leaves the caster's head and thins out. */
	private static void breath(LivingEntity caster) {
		Vec3 head = caster.getEyePosition();
		Vec3 look = caster.getViewVector(1f);

		for (int i = 0, budgeted = Vfx.count(BREATH_MOTES); i < budgeted; i++) {
			Vec3 out = EnergyKit.randomFlat().scale(0.02).add(look.scale(0.012));
			EnergyRender.add(Mote.at(0L, head.add(EnergyKit.jitter1(0.25), -0.1 + RANDOM.nextDouble() * 0.25, EnergyKit.jitter1(0.25)),
					out.add(0, 0.01, 0), BREATH).life(22 + RANDOM.nextInt(8), 4f, 12f).size(0.5f, 0.3f).strength(0.5f).drift(0f, 0.97f).twinkle());
		}
	}

	/** Variant 1: queues three pulses toward the sensed holocron. Entity id = kind ordinal, intensity = closeness. */
	private static void holocron(EffectEventPayload payload) {
		if (payload.dir().horizontalDistanceSqr() < 1.0e-4) {
			// Standing on the site's column: there is no direction to show.
			return;
		}

		Vec3 dir = ForceFxUtil.horizontal(payload.dir());
		HolocronKind[] kinds = HolocronKind.values();
		HolocronKind kind = payload.entityId() >= 0 && payload.entityId() < kinds.length ? kinds[payload.entityId()] : HolocronKind.JEDI;
		float closeness = Mth.clamp(payload.intensity(), 0f, 1f);
		// Due relative to the current reveal; with no reveal showing (it arrives first), count from now.
		int base = (int) Math.max(0, SenseReveal.age());
		int first = Math.max(base + 2, PULSE_FIRST);

		for (int i = 0; i < PULSES; i++) {
			PULSES_DUE.add(new Pulse(first + i * PULSE_GAP, i, dir, kind, closeness));
		}
	}

	/** Advances the breath, threads, clusters, reveal ticks and pulses. Called every client tick. */
	static void tick(Minecraft mc, ClientLevel level) {
		LocalPlayer player = mc.player;
		long age = SenseReveal.age();

		if (player == null || age < 0) {
			if (!MARKS.isEmpty() || !PULSES_DUE.isEmpty()) {
				clear();
			}

			return;
		}

		if (age <= doneAge) {
			return;
		}

		doneAge = age;
		int now = (int) age;

		if (now == 0) {
			inhale(player);
		} else if (now == INHALE_TICKS) {
			exhale(player);
		}

		for (Mark mark : MARKS) {
			streamTick(level, player, mark, now);
		}

		if (now % CLUSTER_INTERVAL == 0) {
			clusterWave(level, now);
		}

		pulses(level, player, now);
	}

	/** The breath in: motes of pale light are drawn in to the player from all round. */
	private static void inhale(LocalPlayer player) {
		Vec3 base = player.position();
		Anchor chest = Anchor.body(player.getId(), 0.5f, 0.55f, 0.5f);

		for (int i = 0, budgeted = Vfx.count(INHALE_MOTES); i < budgeted; i++) {
			Vec3 out = EnergyKit.randomFlat();
			Vec3 at = base.add(out.scale(1.3 + RANDOM.nextDouble() * 0.6)).add(0, 0.3 + RANDOM.nextDouble() * 1.4, 0);
			EnergyRender.add(Mote.at(owner(), at, out.scale(-0.04), BREATH).life(INHALE_TICKS + 4, 2f, 3f).size(0.7f, 0.4f).strength(0.65f)
					.seek(chest, 0.2f, 0.25f, RANDOM.nextBoolean() ? 0.4f : -0.4f, 0.3f));
		}
	}

	/** The breath out: the motes are let go and drift out again, low and unevenly, every way. */
	private static void exhale(LocalPlayer player) {
		Vec3 base = player.position();

		for (int i = 0, budgeted = Vfx.count(EXHALE_MOTES); i < budgeted; i++) {
			Vec3 out = EnergyKit.randomFlat();
			Vec3 at = base.add(out.scale(0.3 + 0.3 * RANDOM.nextDouble())).add(0, 0.4 + RANDOM.nextDouble() * 1.1, 0);
			EnergyRender.add(Mote.at(owner(), at, out.scale(0.07 + 0.09 * RANDOM.nextDouble()).add(0, 0.01 * RANDOM.nextDouble(), 0), BREATH)
					.life(20 + RANDOM.nextInt(12), 1f, 12f).size(0.65f, 0.35f).strength(0.6f).drift(-0.0003f, 0.94f).wander(0.003f).twinkle());
		}
	}

	/** A point's thread leaves the player at its start tick and grows to the point; the reveal tick on arrival. */
	private static void streamTick(ClientLevel level, LocalPlayer player, Mark mark, int now) {
		if (now == mark.streamStart) {
			Vec3 to = where(level, mark);
			Vec3 from = front(player);
			int travel = Mth.clamp((int) Math.round(to.distanceTo(from) * 1.2), MIN_STREAM_LIFE, MAX_STREAM_LIFE);
			mark.arrival = now + travel;
			mark.hidden = hidden(level, player, mark.point);

			if (mark.threaded && Vfx.inRange(to)) {
				Anchor start = partial -> Anchor.entity(player.getId()) instanceof LivingEntity self ? front(self, partial) : null;
				mark.thread = Tendril.between(owner(), start, at(mark), palette(mark.point)).life(travel + THREAD_HOLD, 2f, THREAD_FADE, travel)
						.width(0.55f, 0.4f).strength(0.75f).writhe(0.06f, 1.2f, 0.03f).beads(1.5f, 0.05f, 1.5f);
				EnergyRender.add(mark.thread);
			}
		}

		if (mark.thread != null && mark.lost) {
			mark.thread.release(6);
			mark.thread = null;
		}

		if (mark.arrival >= 0 && now == mark.arrival && !mark.lost) {
			// It arrives: a first handful of the cluster and, on a block, a glint.
			Vec3 centre = where(level, mark);

			if (Vfx.inRange(centre)) {
				for (int i = 0, n = Vfx.count(3); i < n; i++) {
					clusterMote(level, mark, centre, (int) SenseReveal.remaining());
				}

				if (mark.point.kind() != SenseTargets.Kind.CREATURE) {
					EnergyRender.add(new Glint(owner(), Anchor.fixed(centre), palette(mark.point), 12, 0.28f, 1f));
				}
			}
		}

		if (mark.hidden && !mark.ticked && mark.arrival >= 0 && now >= mark.arrival) {
			revealTick(level, player, mark);
		}
	}

	/** {@code force.sense.reveal} for a hidden point: at most 6 a cast, 3 ticks apart, in its direction. */
	private static void revealTick(ClientLevel level, LocalPlayer player, Mark mark) {
		long clock = PowerAnimations.ticks();

		if (revealTicks >= MAX_REVEAL_TICKS) {
			mark.ticked = true;
			return;
		}

		if (clock - lastRevealTick < REVEAL_TICK_GAP) {
			return;
		}

		mark.ticked = true;
		revealTicks++;
		lastRevealTick = clock;
		Vec3 eye = player.getEyePosition();
		Vec3 offset = where(level, mark).subtract(eye);
		double distance = offset.length();
		Vec3 at = distance < 1.0e-3 ? eye : eye.add(offset.scale(Math.min(distance, REVEAL_SOUND_DISTANCE) / distance));
		EnergyKit.play(level, at, ModSounds.FORCE_SENSE_REVEAL, 0.5f, 0.94f + 0.04f * revealTicks);
	}

	/** One wave of cluster motes, round-robin over the points whose threads have arrived; a block point glints now and then. */
	private static void clusterWave(ClientLevel level, int now) {
		List<Mark> arrived = new ArrayList<>();

		for (Mark mark : MARKS) {
			if (mark.arrival >= 0 && now >= mark.arrival && !mark.lost) {
				arrived.add(mark);
			}
		}

		if (arrived.isEmpty()) {
			return;
		}

		int wave = Vfx.count(Mth.clamp(arrived.size(), CLUSTER_WAVE_MIN, CLUSTER_WAVE_MAX));
		int remaining = (int) SenseReveal.remaining();

		for (int i = 0; i < wave; i++) {
			Mark mark = arrived.get(clusterCursor++ % arrived.size());
			Vec3 centre = where(level, mark);

			if (mark.lost || !Vfx.inRange(centre)) {
				continue;
			}

			clusterMote(level, mark, centre, remaining);

			if (mark.point.kind() != SenseTargets.Kind.CREATURE && RANDOM.nextFloat() < 0.3f && remaining > 14) {
				EnergyRender.add(new Glint(owner(), Anchor.fixed(centre.add(EnergyKit.jitter(0.25))), palette(mark.point), 10 + RANDOM.nextInt(5),
						0.16f + 0.08f * RANDOM.nextFloat(), 0.9f));
			}
		}
	}

	/** One slow, twinkling mote of a point's cluster; a creature's keeps up with it as it walks. */
	private static void clusterMote(ClientLevel level, Mark mark, Vec3 centre, int remaining) {
		Vec3 drift = new Vec3(EnergyKit.jitter1(0.004), 0.002 + RANDOM.nextDouble() * 0.003, EnergyKit.jitter1(0.004));
		Entity moving = mark.point.entityId() >= 0 ? level.getEntity(mark.point.entityId()) : null;
		float size = 0.9f + 0.4f * RANDOM.nextFloat();
		// The last wave fades out with the reveal instead of outliving it.
		int life = Math.max(6, Math.min(CLUSTER_MOTE_LIFE + RANDOM.nextInt(6) - 3, remaining));
		Mote mote = Mote.at(owner(), centre.add(EnergyKit.jitter1(0.35), EnergyKit.jitter1(0.25), EnergyKit.jitter1(0.35)), drift, palette(mark.point))
				.life(life, 5f, 10f).size(size, size * 0.7f).strength(0.85f).wander(0.002f).drift(0f, 0.92f).twinkle();

		if (moving != null) {
			// Keeps with a walking creature: it circles slowly where the creature is.
			float width = moving.getBbWidth();
			mote = Mote.at(owner(), centre, Vec3.ZERO, palette(mark.point))
					.orbit(Anchor.body(moving.getId(), 0.5f, 0.6f, 0.5f), width * 0.35f + 0.25f * RANDOM.nextFloat(), RANDOM.nextFloat() * 6.2832f,
							-0.3f + 0.6f * RANDOM.nextFloat(), (RANDOM.nextBoolean() ? 1f : -1f) * (0.02f + 0.03f * RANDOM.nextFloat()), 0.004f, 0f)
					.life(life, 5f, 10f).size(size, size * 0.7f).strength(0.85f).twinkle();
		}

		EnergyRender.add(mote);
	}

	/** Holocron pulses: kind-tinted motes fly off along the direction, brighter when closer. */
	private static void pulses(ClientLevel level, LocalPlayer player, int now) {
		if (PULSES_DUE.isEmpty()) {
			return;
		}

		for (int i = 0; i < PULSES_DUE.size(); i++) {
			Pulse pulse = PULSES_DUE.get(i);

			if (pulse.at() != now) {
				continue;
			}

			Vec3 dir = pulse.dir();
			Vec3 side = new Vec3(-dir.z, 0, dir.x);
			Vec3 start = player.getEyePosition().add(dir.scale(1.4)).add(0, -0.25, 0);
			Palette palette = switch (pulse.kind()) {
				case JEDI -> JEDI;
				case SITH -> SITH;
				case WAYFARER -> WAYFARER;
			};
			float strength = 0.45f + 0.5f * pulse.closeness();

			if (pulse.index() == 0) {
				EnergyKit.play(level, start.add(dir.scale(4)), ModSounds.STORY_CALLING_HOLOCRON, 0.25f + 0.35f * pulse.closeness(), 1f);
			}

			for (int m = 0, budgeted = Vfx.count(PULSE_MOTES); m < budgeted; m++) {
				Vec3 at = start.add(side.scale(EnergyKit.jitter1(0.3))).add(dir.scale(RANDOM.nextDouble() * 0.8)).add(0, EnergyKit.jitter1(0.15), 0);
				EnergyRender.add(Mote.at(owner(), at, dir.scale(PULSE_TRAVEL / PULSE_LIFE).add(0, 0.003, 0), palette)
						.life(PULSE_LIFE + RANDOM.nextInt(4), 3f, 8f).size(0.8f, 0.45f).strength(strength).drift(0f, 0.985f));
			}
		}

		PULSES_DUE.removeIf(pulse -> pulse.at() <= now);
	}

	/** Where a point is drawn now: a loaded creature's chest, else the position the server sent. */
	private static Vec3 where(ClientLevel level, Mark mark) {
		int id = mark.point.entityId();

		if (id < 0) {
			return mark.point.pos();
		}

		Entity entity = level.getEntity(id);

		if (entity == null || entity.isRemoved() || !entity.isAlive()) {
			// Out of tracking range or dead: the cluster stays where it was sensed, or ends if it died.
			if (entity != null) {
				mark.lost = true;
			}

			return mark.point.pos();
		}

		return EnergyKit.chest(entity);
	}

	/** Where a point's thread ends, found again every frame: a creature's chest while it is there, else the sensed spot. */
	private static Anchor at(Mark mark) {
		int id = mark.point.entityId();
		Vec3 fixed = mark.point.pos();

		if (id < 0) {
			return Anchor.fixed(fixed);
		}

		return partial -> {
			Entity entity = Anchor.entity(id);
			return entity == null || !entity.isAlive() ? fixed : EnergyKit.chest(entity, partial);
		};
	}

	/** Just in front of the player, a little below the eyes, toward where it looks: where the threads leave. */
	private static Vec3 front(LivingEntity player) {
		return front(player, 1f);
	}

	private static Vec3 front(LivingEntity player, float partial) {
		Vec3 look = ForceFxUtil.horizontal(player.getViewVector(partial));
		return player.getEyePosition(partial).add(look.scale(0.6)).add(0, -0.45, 0);
	}

	/** Every block point is hidden; a creature is when the player has no line of sight to it. */
	private static boolean hidden(ClientLevel level, LocalPlayer player, SenseTargets.Point point) {
		if (point.kind() != SenseTargets.Kind.CREATURE) {
			return true;
		}

		Entity entity = level.getEntity(point.entityId());
		return entity == null || !player.hasLineOfSight(entity);
	}

	/** A point's light: creatures by attitude, every block point pale gold. */
	private static Palette palette(SenseTargets.Point point) {
		if (point.kind() != SenseTargets.Kind.CREATURE) {
			return BLOCKS;
		}

		return switch (point.attitude()) {
			case HOSTILE -> HOSTILE;
			case WARY -> WARY;
			case NEUTRAL -> NEUTRAL;
			case ALLIED -> ALLIED;
		};
	}

	/** The owner of the local player's reveal light. */
	private static long owner() {
		Minecraft mc = Minecraft.getInstance();
		return EnergyRender.owner(OWNER_KIND, mc.player != null ? mc.player.getId() : 0);
	}

	/** Forgets the reveal's effects (level change, death, respawn, disconnect). */
	static void clear() {
		EnergyRender.kill(owner());
		MARKS.clear();
		PULSES_DUE.clear();
		doneAge = -1;
		revealTicks = 0;
		lastRevealTick = Long.MIN_VALUE;
		clusterCursor = 0;
	}

	private SenseFx() {
	}
}
