package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.ForceFxUtil.RANDOM;
import static com.oldrepublic.client.force.ForceFxUtil.chest;
import static com.oldrepublic.client.force.ForceFxUtil.jitterScalar;
import static com.oldrepublic.client.force.ForceFxUtil.play;
import static com.oldrepublic.client.force.ForceFxUtil.spawn;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForceParticles;
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
 * <p><b>Caster</b> ({@code sense_reveal}, the caster's own reveal): a one-second inhale in which a
 * few motes lift around the player and drift outward; then, nearest point first, a thin dotted
 * stream of {@code sense_stream} specks runs from just in front of the player to each point and
 * settles into a small cluster of slow-drifting {@code sense_mote}s at chest height. A creature's
 * cluster follows it for the reveal. Tints: hostile ember orange, wary amber, neutral grey-white,
 * allied pale blue, blocks pale gold. Each hidden point (any block point, or a creature out of line
 * of sight) gets a soft {@code force.sense.reveal} tick as its stream arrives, at most 6 per cast and
 * at least 3 ticks apart, placed in the point's direction. The 30% ambience dip belongs to the
 * ambience's owner (the world feature dips its beds on the caster's own gesture event). Holocron
 * direction ({@code force_sense} variant 1): three soft pulses of
 * kind-tinted motes travel along the direction, brighter when closer.
 *
 * <p><b>Everyone</b> ({@code force_sense} variant 0): the cast sound and the free-arm pose
 * {@code SENSE}; others also see a faint breath of dust leave the caster's head.
 *
 * <p>No outline, glow, ring or ground wave anywhere: only motes and dust. Budget: at most 60 live
 * particles at Standard for the inhale, streams and clusters together (8 inhale motes, at most 24
 * stream specks, at most 30 cluster motes; the 12 pulse motes come after the inhale has faded).
 */
final class SenseFx {
	/** Pose length (spec 10.1). */
	static final int GESTURE_TICKS = 10;
	/** The inhale: motes lift for this many ticks after the cast. */
	private static final int INHALE_TICKS = 16;
	private static final int INHALE_INTERVAL = 4;
	private static final int INHALE_MOTES = 2;
	/** Cast tick the first stream leaves the player. */
	private static final int STREAM_START = 6;
	/** Stream specks of one cast at Standard, over every point. */
	private static final int STREAM_BUDGET = 24;
	private static final int MAX_SPECKS_PER_POINT = 4;
	private static final int MIN_STREAM_LIFE = 8;
	private static final int MAX_STREAM_LIFE = 24;
	/** Clusters: one wave every 5 ticks of at most 5 motes living about 30 ticks (30 live at Standard). */
	private static final int CLUSTER_INTERVAL = 5;
	private static final int CLUSTER_WAVE_MAX = 5;
	private static final int CLUSTER_WAVE_MIN = 2;
	private static final int CLUSTER_MOTE_LIFE = 30;
	/** Reveal ticks: at most 6 per cast, 3 ticks apart, heard about 6 blocks away in the point's direction. */
	private static final int MAX_REVEAL_TICKS = 6;
	private static final int REVEAL_TICK_GAP = 3;
	private static final double REVEAL_SOUND_DISTANCE = 6;
	/** Holocron pulses: three, 10 ticks apart, after the inhale. */
	private static final int PULSES = 3;
	private static final int PULSE_FIRST = 20;
	private static final int PULSE_GAP = 10;
	private static final int PULSE_MOTES = 4;
	private static final int PULSE_LIFE = 22;
	private static final double PULSE_TRAVEL = 5.5;
	/** Others: the breath of dust from the caster's head. */
	private static final int BREATH_MOTES = 5;

	/** One revealed point: its stream, its cluster and whether it earns a reveal tick. */
	private static final class Mark {
		final SenseTargets.Point point;
		/** Cast tick its stream leaves the player. */
		final int streamStart;
		final int specks;
		int spawned;
		/** Cast tick the first speck reaches the point; the cluster starts then. -1 until it left. */
		int arrival = -1;
		boolean hidden;
		boolean ticked;
		/** Set once the creature is gone: its cluster ends. */
		boolean lost;

		Mark(SenseTargets.Point point, int streamStart, int specks) {
			this.point = point;
			this.streamStart = streamStart;
			this.specks = specks;
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

		MARKS.clear();
		PULSES_DUE.clear();
		doneAge = -1;
		revealTicks = 0;
		lastRevealTick = Long.MIN_VALUE;
		clusterCursor = 0;

		List<SenseTargets.Point> points = payload.points();
		int n = points.size();

		for (int i = 0; i < n; i++) {
			// Nearest first; a large reveal spreads its stream starts over about a second.
			int specks = Vfx.count(Mth.clamp(STREAM_BUDGET / n, 1, MAX_SPECKS_PER_POINT));
			MARKS.add(new Mark(points.get(i), STREAM_START + (n > 16 ? i / 2 : i), specks));
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
		play(level, payload.pos(), ModSounds.FORCE_SENSE_CAST, own ? 0.8f : 0.55f, 1f);
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

	/** Others: a faint breath of pale dust leaves the caster's head and thins out. */
	private static void breath(LivingEntity caster) {
		Vec3 head = caster.getEyePosition();
		Vec3 look = caster.getViewVector(1f);

		for (int i = 0, budgeted = Vfx.count(BREATH_MOTES); i < budgeted; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle)).scale(0.012).add(look.scale(0.01));
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, head.add(jitterScalar(0.25), -0.1 + RANDOM.nextDouble() * 0.25, jitterScalar(0.25)),
					out.add(0, 0.008, 0));

			if (p != null) {
				p.color(0.82f, 0.8f, 0.74f).size(0.03f, 0.05f).alpha(0.3f).life(22 + RANDOM.nextInt(8)).drag(0.95f);
			}
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

	/** Advances the inhale, streams, clusters, reveal ticks and pulses. Called every client tick. */
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

		if (now < INHALE_TICKS && now % INHALE_INTERVAL == 0) {
			inhale(player);
		}

		for (Mark mark : MARKS) {
			streamTick(level, player, mark, now);
		}

		if (now % CLUSTER_INTERVAL == 0) {
			clusterWave(level, now);
		}

		pulses(level, player, now);
	}

	/** The inhale: motes rise around the player's waist and drift outward. */
	private static void inhale(LocalPlayer player) {
		Vec3 base = player.position();

		for (int i = 0, budgeted = Vfx.count(INHALE_MOTES); i < budgeted; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double r = 0.45 + RANDOM.nextDouble() * 0.35;
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 at = base.add(out.scale(r)).add(0, 0.5 + RANDOM.nextDouble() * 0.7, 0);
			ForceParticle p = spawn(ForceParticles.SENSE_MOTE, at, out.scale(0.025).add(0, 0.03, 0));

			if (p != null) {
				p.color(0.9f, 0.88f, 0.82f).alpha(0.55f).life(20 + RANDOM.nextInt(6));
			}
		}
	}

	/** One speck of a point's stream per tick while it has specks left; the reveal tick on arrival. */
	private static void streamTick(ClientLevel level, LocalPlayer player, Mark mark, int now) {
		if (now >= mark.streamStart && mark.spawned < mark.specks) {
			Vec3 to = where(level, mark);
			Vec3 eye = player.getEyePosition();
			Vec3 toward = to.subtract(eye);
			Vec3 flat = ForceFxUtil.horizontal(toward);
			Vec3 from = eye.add(flat.scale(0.6)).add(jitterScalar(0.05), -0.35 + jitterScalar(0.05), jitterScalar(0.05));
			double distance = to.distanceTo(from);
			int life = Mth.clamp((int) Math.round(distance * 1.2), MIN_STREAM_LIFE, MAX_STREAM_LIFE);
			ForceParticle p = spawn(ForceParticles.SENSE_STREAM, from, to.subtract(from).scale(1.0 / life));

			if (p != null) {
				float[] tint = tint(mark.point);
				p.color(Mth.lerp(0.4f, tint[0], 0.86f), Mth.lerp(0.4f, tint[1], 0.84f), Mth.lerp(0.4f, tint[2], 0.78f)).life(life).drag(1f);
			}

			if (mark.spawned == 0) {
				mark.arrival = now + life;
				mark.hidden = hidden(level, player, mark.point);
			}

			mark.spawned++;
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
		play(level, at, ModSounds.FORCE_SENSE_REVEAL, 0.5f, 0.94f + 0.04f * revealTicks);
	}

	/** One wave of cluster motes, round-robin over the points whose streams have arrived. */
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

			Vec3 drift = new Vec3(jitterScalar(0.004), 0.002 + RANDOM.nextDouble() * 0.003, jitterScalar(0.004));

			Entity moving = mark.point.entityId() >= 0 ? level.getEntity(mark.point.entityId()) : null;

			if (moving != null) {
				// Keep up with a walking creature for a moment; the drag lets the mote fall behind softly.
				drift = drift.add(moving.getDeltaMovement().multiply(0.8, 0, 0.8));
			}

			Vec3 at = centre.add(jitterScalar(0.35), jitterScalar(0.25), jitterScalar(0.35));
			ForceParticle p = spawn(ForceParticles.SENSE_MOTE, at, drift);

			if (p != null) {
				float[] tint = tint(mark.point);
				// The last wave fades out with the reveal instead of outliving it.
				p.color(tint[0], tint[1], tint[2]).life(Math.max(6, Math.min(CLUSTER_MOTE_LIFE + RANDOM.nextInt(6) - 3, remaining)));
			}
		}
	}

	/** Holocron pulses: kind-tinted motes travel along the direction, brighter when closer. */
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
			float[] tint = kindTint(pulse.kind());
			float alpha = 0.4f + 0.5f * pulse.closeness();

			if (pulse.index() == 0) {
				play(level, start.add(dir.scale(4)), ModSounds.STORY_CALLING_HOLOCRON, 0.25f + 0.35f * pulse.closeness(), 1f);
			}

			for (int m = 0, budgeted = Vfx.count(PULSE_MOTES); m < budgeted; m++) {
				Vec3 at = start.add(side.scale(jitterScalar(0.3))).add(dir.scale(RANDOM.nextDouble() * 0.8)).add(0, jitterScalar(0.15), 0);
				ForceParticle p = spawn(ForceParticles.SENSE_MOTE, at, dir.scale(PULSE_TRAVEL / PULSE_LIFE).add(0, 0.003, 0));

				if (p != null) {
					p.color(tint[0], tint[1], tint[2]).alpha(alpha).size(0.04f, 0.02f).life(PULSE_LIFE + RANDOM.nextInt(4)).drag(0.985f);
				}
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

		return chest(entity);
	}

	/** Every block point is hidden; a creature is when the player has no line of sight to it. */
	private static boolean hidden(ClientLevel level, LocalPlayer player, SenseTargets.Point point) {
		if (point.kind() != SenseTargets.Kind.CREATURE) {
			return true;
		}

		Entity entity = level.getEntity(point.entityId());
		return entity == null || !player.hasLineOfSight(entity);
	}

	/** Cluster tint: creatures by attitude, every block point pale gold. */
	static float[] tint(SenseTargets.Point point) {
		if (point.kind() != SenseTargets.Kind.CREATURE) {
			return new float[] {1f, 0.88f, 0.6f};
		}

		return attitudeTint(point.attitude());
	}

	static float[] attitudeTint(Attitude attitude) {
		return switch (attitude) {
			case HOSTILE -> new float[] {1f, 0.55f, 0.25f};
			case WARY -> new float[] {1f, 0.75f, 0.3f};
			case NEUTRAL -> new float[] {0.85f, 0.85f, 0.82f};
			case ALLIED -> new float[] {0.62f, 0.78f, 1f};
		};
	}

	/** Holocron pulse tint: warm gold, rust or brass. */
	private static float[] kindTint(HolocronKind kind) {
		return switch (kind) {
			case JEDI -> new float[] {1f, 0.85f, 0.5f};
			case SITH -> new float[] {0.85f, 0.42f, 0.28f};
			case WAYFARER -> new float[] {0.86f, 0.7f, 0.42f};
		};
	}

	/** Forgets the reveal's effects (level change, death, respawn, disconnect). */
	static void clear() {
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
