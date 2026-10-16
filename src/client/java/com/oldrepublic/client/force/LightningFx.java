package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.ForceFxUtil.RANDOM;
import static com.oldrepublic.client.force.ForceFxUtil.chest;
import static com.oldrepublic.client.force.ForceFxUtil.handPos;
import static com.oldrepublic.client.force.ForceFxUtil.jitter;
import static com.oldrepublic.client.force.ForceFxUtil.jitterScalar;
import static com.oldrepublic.client.force.ForceFxUtil.pitchVariance;
import static com.oldrepublic.client.force.ForceFxUtil.play;
import static com.oldrepublic.client.force.ForceFxUtil.spawn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.force.particle.LightningSegmentParticle;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Lightning visuals and sounds (spec 8.5). Each caster's channel lives here between the
 * charge event and the stop event: crawling sparks and tiny arcs at the hand while it charges,
 * then jittered, forking arcs rebuilt every 2 ticks from the hand to each target (or the struck
 * block), contact sparks and the impact sizzle on damage ticks, and a clean stop that removes every
 * arc at once and leaves only a few smoke wisps at the hand.
 *
 * <p>Watchdogs: arcs are hidden when no damage-tick refresh has arrived for {@link #REFRESH_TICKS}
 * (they come back with the next one), and the whole channel is forgotten after
 * {@link #LOST_TICKS} without one, or when the caster dies, is removed or the level changes.
 */
final class LightningFx {
	/** Spec 8.5: no refresh for this many ticks hides the arcs. */
	private static final int REFRESH_TICKS = 6;
	/** No refresh for this long: the stop event was lost; forget the channel. */
	private static final int LOST_TICKS = 40;
	/** Charge (8 ticks) plus slack before a charge that never turned into arcs is dropped. */
	private static final int CHARGE_WATCHDOG = 8 + 12;
	/** Longest channel (charge 8 + arcs 80) plus slack, for the pose watchdog. */
	static final int CHANNEL_WATCHDOG = 8 + 80 + 20;
	private static final int REBUILD_INTERVAL = 2;
	private static final float CASTER_SHAKE = 0.03f;
	private static final double RANGE = 9.0;
	private static final int IMPACT_SOUND_WINDOW = 10;

	private static final class Channel {
		final int casterId;
		int rank;
		boolean arcs;
		final long started;
		long lastRefresh;
		final int[] targets = {-1, -1, -1};
		/** Where the arcs strike when there is no target (the last damage tick's block). */
		@Nullable Vec3 contact;
		/** The last two impact sounds (rate limit: at most 2 per 10 ticks). */
		long impactA = -100;
		long impactB = -100;
		long lastBlockSound = -100;
		/** Live arc segments, removed at once when the channel stops. */
		final List<Particle> live = new ArrayList<>();

		Channel(int casterId, int rank, long now) {
			this.casterId = casterId;
			this.rank = rank;
			this.started = now;
			this.lastRefresh = now;
		}
	}

	private static final Map<Integer, Channel> CHANNELS = new HashMap<>();

	/** {@code force_lightning}: charge, arcs or stop. Entity = caster, intensity = mastery rank. */
	static void onPhase(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity caster)) {
			return;
		}

		int phase = ForceEffects.lightningPhase(payload.variant());
		int rank = Math.max(0, Math.round(payload.intensity()));
		long now = PowerAnimations.ticks();

		switch (phase) {
			case ForceEffects.LIGHTNING_CHARGE -> {
				play(level, chest(caster), ModSounds.FORCE_LIGHTNING_CHARGE, 0.8f, pitchVariance(0.04f));
				startChannel(caster, rank, caster == mc.player);
			}
			case ForceEffects.LIGHTNING_ARCS -> {
				Channel channel = CHANNELS.get(caster.getId());

				if (channel == null) {
					// Joined mid-cast or the charge event was missed.
					startChannel(caster, rank, false);
					channel = CHANNELS.get(caster.getId());
				}

				channel.arcs = true;
				channel.rank = rank;
				channel.lastRefresh = now;
				PowerAnimations.loop(caster, PowerAnimations.Kind.LIGHTNING, ModSounds.FORCE_LIGHTNING_LOOP, 0.7f, 1f);
			}
			case ForceEffects.LIGHTNING_STOP -> stop(level, caster);
			default -> {
			}
		}
	}

	/** Starts the pose and the channel; for the own player ({@code own}) it confirms the prediction. */
	private static void startChannel(LivingEntity caster, int rank, boolean own) {
		Channel existing = CHANNELS.get(caster.getId());

		if (own && existing != null && !existing.arcs) {
			existing.rank = rank;
			PowerAnimations.confirm(caster.getId(), PowerAnimations.Kind.LIGHTNING);
			return;
		}

		if (existing != null) {
			kill(existing);
		}

		CHANNELS.put(caster.getId(), new Channel(caster.getId(), rank, PowerAnimations.ticks()));

		if (!PowerAnimations.isActive(caster.getId(), PowerAnimations.Kind.LIGHTNING)) {
			PowerAnimations.start(caster.getId(), PowerAnimations.Kind.LIGHTNING, -1, CHANNEL_WATCHDOG);
		}
	}

	/** Own prediction at the key press: the charge pose and sparks before the server answers. */
	static void predict(LivingEntity caster) {
		if (CHANNELS.containsKey(caster.getId())) {
			return;
		}

		CHANNELS.put(caster.getId(), new Channel(caster.getId(), 0, PowerAnimations.ticks()));
		PowerAnimations.startPredicted(caster.getId(), PowerAnimations.Kind.LIGHTNING, -1, CHANNEL_WATCHDOG);
	}

	/** {@code force_lightning_target}: entity = target, variant = (caster, slot), intensity 1 on, 0 off. */
	static void onTarget(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		Channel channel = CHANNELS.get(ForceEffects.casterOf(payload.variant()));
		int slot = ForceEffects.valueOf(payload.variant());

		if (channel == null || slot >= channel.targets.length) {
			return;
		}

		if (payload.intensity() > 0.5f) {
			channel.targets[slot] = payload.entityId();
		} else if (channel.targets[slot] == payload.entityId()) {
			channel.targets[slot] = -1;
		}
	}

	/** {@code force_lightning_hit}: a damage tick. Entity = caster, pos = contact, intensity = hits, variant = blocked mask. */
	static void onHit(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity caster)) {
			return;
		}

		Channel channel = CHANNELS.get(caster.getId());
		long now = PowerAnimations.ticks();

		if (channel == null) {
			return;
		}

		channel.lastRefresh = now;
		channel.arcs = true;
		int hits = Math.round(payload.intensity());
		channel.contact = hits == 0 ? payload.pos() : null;

		if (now - channel.impactB >= IMPACT_SOUND_WINDOW) {
			play(level, payload.pos(), ModSounds.FORCE_LIGHTNING_IMPACT, 0.7f, pitchVariance(0.08f));
			channel.impactB = channel.impactA;
			channel.impactA = now;
		}

		int blocked = payload.variant();

		for (int slot = 0; slot < channel.targets.length; slot++) {
			Entity target = channel.targets[slot] >= 0 ? level.getEntity(channel.targets[slot]) : null;

			if (target == null) {
				continue;
			}

			Vec3 at = chest(target);
			boolean guarded = (blocked >> slot & 1) != 0;

			if (guarded && now - channel.lastBlockSound >= IMPACT_SOUND_WINDOW) {
				play(level, at, ModSounds.SABER_LIGHTNING_BLOCK, 0.8f, pitchVariance(0.05f));
				channel.lastBlockSound = now;
			}

			if (Vfx.inRange(at)) {
				sparks(at, Vfx.count(guarded ? 8 : 5), guarded);
			}
		}

		if (hits == 0 && Vfx.inRange(payload.pos())) {
			sparks(payload.pos(), Vfx.count(4), false);
		}
	}

	/** Charge sparks and arc rebuilds. Called every client tick. */
	static void tick(Minecraft mc, ClientLevel level) {
		if (CHANNELS.isEmpty()) {
			return;
		}

		long now = PowerAnimations.ticks();
		Iterator<Channel> it = CHANNELS.values().iterator();

		while (it.hasNext()) {
			Channel channel = it.next();
			channel.live.removeIf(p -> !p.isAlive());
			Entity entity = level.getEntity(channel.casterId);
			boolean lost = channel.arcs ? now - channel.lastRefresh > LOST_TICKS : now - channel.started > CHARGE_WATCHDOG;

			if (!(entity instanceof LivingEntity caster) || caster.isRemoved() || !caster.isAlive() || lost
					|| !PowerAnimations.isActive(channel.casterId, PowerAnimations.Kind.LIGHTNING)) {
				kill(channel);
				PowerAnimations.stop(channel.casterId, PowerAnimations.Kind.LIGHTNING);
				it.remove();
				continue;
			}

			if (!channel.arcs) {
				chargeTick(mc, caster, now - channel.started);
				continue;
			}

			boolean fresh = now - channel.lastRefresh <= REFRESH_TICKS;

			if (!fresh) {
				// Refresh overdue: arcs off until the next damage tick arrives.
				kill(channel);
				continue;
			}

			if (caster == mc.player) {
				CameraShake.add(CASTER_SHAKE);
			}

			if ((now - channel.started) % REBUILD_INTERVAL == 0 && Vfx.inRange(caster.position())) {
				rebuild(mc, level, channel, caster);
			}
		}
	}

	/** Charge: sparks jump between the fingertips, with two or three tiny arcs around the hand. */
	private static void chargeTick(Minecraft mc, LivingEntity caster, long age) {
		if (!Vfx.inRange(caster.position())) {
			return;
		}

		Vec3 hand = origin(mc, caster, ForceFxUtil.freeArmIsLeft(caster));

		for (int i = 0; i < Vfx.count(2); i++) {
			ForceParticle p = spawn(ForceParticles.LIGHTNING_SPARK, hand.add(jitter(0.1)), jitter(0.03));

			if (p != null) {
				p.color(0.92f, 0.92f, 1f).life(3 + RANDOM.nextInt(3));
			}
		}

		if (age % REBUILD_INTERVAL == 0) {
			Channel channel = CHANNELS.get(caster.getId());
			int arcs = 2 + RANDOM.nextInt(2);

			for (int i = 0; i < arcs && channel != null; i++) {
				Vec3 from = hand.add(jitter(0.12));
				Vec3 to = from.add(jitter(1).normalize().scale(0.3));
				arc(channel, from, to, 3, 0, 0.05, alpha() * 0.8f);
			}
		}
	}

	/** Rebuilds every arc of a channel from scratch (the jitter is re-randomised each time). */
	private static void rebuild(Minecraft mc, ClientLevel level, Channel channel, LivingEntity caster) {
		ClientConfig.Preset preset = ClientConfig.get().preset;
		int mainArcs = switch (preset) {
			case REDUCED -> 1;
			case CINEMATIC -> 3;
			default -> 2;
		};
		int forks = preset == ClientConfig.Preset.REDUCED ? 0 : 1 + channel.rank;
		boolean both = !PowerAnimations.holdsSaber(caster);
		Entity primary = channel.targets[0] >= 0 ? level.getEntity(channel.targets[0]) : null;
		Vec3 end = primary != null ? chest(primary) : channel.contact != null ? channel.contact : rayEnd(caster);
		float alpha = alpha();

		for (int i = 0; i < mainArcs; i++) {
			boolean left = both ? i % 2 == 0 : ForceFxUtil.freeArmIsLeft(caster);
			Vec3 from = origin(mc, caster, left);
			Vec3 to = end.add(jitter(0.15));
			arc(channel, from, to, 0, forks, 0.3, alpha);
		}

		if (primary == null) {
			return;
		}

		// Chain Lightning: arcs leap from the primary target to the others.
		for (int slot = 1; slot < channel.targets.length; slot++) {
			Entity next = channel.targets[slot] >= 0 ? level.getEntity(channel.targets[slot]) : null;

			if (next != null) {
				arc(channel, chest(primary), chest(next).add(jitter(0.1)), 0, Math.max(0, forks / 2), 0.25, alpha * 0.9f);
			}
		}
	}

	/**
	 * One jittered polyline arc from {@code from} to {@code to}: {@code segments} pieces (0 = 8 to
	 * 14 by length) displaced sideways by up to {@code amplitude} (scaled by length, largest in the
	 * middle), plus {@code forks} short side branches.
	 */
	private static void arc(Channel channel, Vec3 from, Vec3 to, int segments, int forks, double amplitude, float alpha) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();

		if (length < 0.05) {
			return;
		}

		int n = segments > 0 ? segments : Math.max(8, Math.min(14, 8 + (int) (length / 1.5)));
		Vec3 dir = delta.scale(1 / length);
		Vec3 u = dir.cross(new Vec3(0, 1, 0));
		u = u.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : u.normalize();
		Vec3 v = u.cross(dir).normalize();
		double amp = Math.min(amplitude, length * 0.08) + 0.02;
		List<Vec3> points = new ArrayList<>(n + 1);
		points.add(from);

		for (int i = 1; i < n; i++) {
			double t = (double) i / n;
			double envelope = Math.sin(Math.PI * t);
			Vec3 offset = u.scale(jitterScalar(amp) * envelope).add(v.scale(jitterScalar(amp) * envelope));
			points.add(from.add(delta.scale(t)).add(offset));
		}

		points.add(to);

		for (int i = 0; i < n; i++) {
			segment(channel, points.get(i), points.get(i + 1), alpha);
		}

		for (int f = 0; f < Vfx.count(forks) && forks > 0; f++) {
			Vec3 start = points.get(1 + RANDOM.nextInt(Math.max(1, n - 1)));
			Vec3 side = dir.add(u.scale(jitterScalar(0.9))).add(v.scale(jitterScalar(0.9))).normalize();
			double forkLength = 0.5 + RANDOM.nextDouble();
			Vec3 prev = start;
			int pieces = 3 + RANDOM.nextInt(3);

			for (int k = 1; k <= pieces; k++) {
				Vec3 next = start.add(side.scale(forkLength * k / pieces)).add(jitter(0.06));
				segment(channel, prev, next, alpha * 0.75f);
				prev = next;
			}
		}
	}

	private static void segment(Channel channel, Vec3 a, Vec3 b, float alpha) {
		Vec3 mid = a.add(b).scale(0.5);
		ForceParticle particle = spawn(ForceParticles.LIGHTNING_SEGMENT, mid, Vec3.ZERO);

		if (particle instanceof LightningSegmentParticle seg) {
			seg.span(a, b).alpha(alpha).life(2 + RANDOM.nextInt(2));
			channel.live.add(seg);
		}
	}

	/** Flicker: 0.7 to 1.0 per refresh, or a steady 0.85 with flashing effects off. */
	private static float alpha() {
		return Vfx.flashing() ? 0.7f + RANDOM.nextFloat() * 0.3f : 0.85f;
	}

	private static void sparks(Vec3 at, int count, boolean guarded) {
		for (int i = 0; i < count; i++) {
			ForceParticle p = spawn(ForceParticles.LIGHTNING_SPARK, at.add(jitter(0.25)), jitter(guarded ? 0.18 : 0.1));

			if (p != null) {
				p.fall(guarded ? 0.4f : 0.15f, false);
			}
		}
	}

	/** Where the arcs leave the caster: the hand; for the own first-person view, just below the view. */
	private static Vec3 origin(Minecraft mc, LivingEntity caster, boolean left) {
		if (caster == mc.player && mc.options.getCameraType().isFirstPerson()) {
			Vec3 look = caster.getViewVector(1f);
			Vec3 side = look.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
			return caster.getEyePosition().add(look.scale(0.7)).add(side.scale(left ? -0.28 : 0.28)).add(0, -0.25, 0);
		}

		return handPos(caster, left, 1f);
	}

	private static Vec3 rayEnd(LivingEntity caster) {
		return caster.pick(RANGE, 1f, false).getLocation();
	}

	/** Stop: every arc goes at once; a few faint dark smoke wisps rise from the hand for 10 ticks. */
	private static void stop(ClientLevel level, LivingEntity caster) {
		Channel channel = CHANNELS.remove(caster.getId());
		PowerAnimations.stop(caster.getId(), PowerAnimations.Kind.LIGHTNING);

		if (channel == null) {
			return;
		}

		kill(channel);
		Vec3 hand = handPos(caster, ForceFxUtil.freeArmIsLeft(caster), 1f);

		if (channel.arcs) {
			play(level, hand, ModSounds.FORCE_LIGHTNING_RELEASE, 0.8f, pitchVariance(0.05f));
		}

		if (!Vfx.inRange(hand)) {
			return;
		}

		for (int i = 0; i < 2 + RANDOM.nextInt(2); i++) {
			ForceParticle p = spawn(ForceParticles.GATHER_DUST, hand.add(jitter(0.06)), new Vec3(jitterScalar(0.005), 0.02, jitterScalar(0.005)));

			if (p != null) {
				p.color(0.22f, 0.21f, 0.24f).size(0.06f, 0.14f).alpha(0.35f).life(10);
			}
		}
	}

	private static void kill(Channel channel) {
		for (Particle particle : channel.live) {
			particle.remove();
		}

		channel.live.clear();
	}

	static void clear() {
		for (Channel channel : CHANNELS.values()) {
			kill(channel);
		}

		CHANNELS.clear();
	}

	private LightningFx() {
	}
}
