package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.energy.EnergyKit.RANDOM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.energy.Anchor;
import com.oldrepublic.client.force.energy.EnergyKit;
import com.oldrepublic.client.force.energy.EnergyRender;
import com.oldrepublic.client.force.energy.Flare;
import com.oldrepublic.client.force.energy.Mote;
import com.oldrepublic.client.force.energy.Palette;
import com.oldrepublic.client.force.energy.Tendril;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.EnergyParticles;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.drain.ForceDrain;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Drain visuals and sounds (MILESTONE_5.md 9.5, 15.1, 15.3): life torn out of the target and drawn into the caster's
 * hand. Strands of crimson light grow out of the target's body and writhe across to the clawed hand, beads of light running
 * down them; crimson motes fly the same way on curving paths, dark vapour is pulled off the body after them, and the hand
 * that drinks it all glows. Nothing on the ground, no dome or ring.
 *
 * <ul>
 * <li><b>Start</b> ({@code force_drain} 0 on the caster; the target's id packed in the variant; intensity = the longest
 * channel in seconds): the free hand reaches out, fingers curled, and draws slowly back
 * ({@link PowerAnimations.Kind#DRAIN}); the {@code force.drain.loop} plays on the caster; the strands grow out of the body
 * toward the hand.</li>
 * <li><b>Pulse</b> (1): the strands surge, thicker and brighter, a gout of motes and vapour tears out of the body, and the
 * hand flares.</li>
 * <li><b>End</b> (2): the pose and the loop stop and {@code force.drain.end} sounds; the strands let go and fade, the
 * hand's glow dies away in a breath of dark vapour. The caster's death or removal, a level change, a disconnect or the
 * watchdog end it too.</li>
 * </ul>
 */
public final class DrainFx {
	/** Ticks past the longest channel before a lost end event is assumed. */
	private static final int WATCHDOG_SLACK = 30;
	/** Strands at Standard. */
	private static final int STRANDS = 3;
	/** {@link EnergyRender} owner kind of a drain's light. */
	private static final int OWNER_KIND = 2;
	/** How far the hand reaches while it drinks (0 drawn in, 1 thrust out). */
	private static final float REACH = 0.85f;
	/** How fast a mote flies to the hand, blocks a tick. */
	private static final float MOTE_SPEED = 0.28f;
	/** Vapour drawn off the body: a dark, bloody red. */
	private static final float[] VAPOUR = {0.22f, 0.03f, 0.05f};

	/** A channel seen by this client: the caster's target, where it was last told to be, the watchdog end and its light. */
	private static final class Stream {
		final int targetId;
		final long owner;
		final boolean left;
		Vec3 lastTarget;
		final long until;
		final List<Tendril> strands = new ArrayList<>();
		@Nullable Flare glow;

		Stream(int casterId, int targetId, boolean left, Vec3 lastTarget, long until) {
			this.targetId = targetId;
			this.owner = EnergyRender.owner(OWNER_KIND, casterId);
			this.left = left;
			this.lastTarget = lastTarget;
			this.until = until;
		}
	}

	private static final Map<Integer, Stream> STREAMS = new HashMap<>();
	private static long ticks;

	/** {@code force_drain}: start, pulse or end on the caster. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity caster) {
		int id = payload.entityId();
		int targetId = ForceEffects.casterOf(payload.variant());

		switch (ForceEffects.valueOf(payload.variant())) {
			case ForceDrain.VARIANT_START -> {
				int watchdog = Math.round(payload.intensity() * 20f) + WATCHDOG_SLACK;
				Stream old = STREAMS.remove(id);

				if (old != null) {
					EnergyRender.kill(old.owner);
				}

				boolean left = caster instanceof LivingEntity living && EnergyKit.freeArmIsLeft(living);
				Stream stream = new Stream(id, targetId, left, payload.pos(), ticks + watchdog);
				STREAMS.put(id, stream);
				PowerAnimations.start(id, PowerAnimations.Kind.DRAIN, -1, watchdog);

				if (caster instanceof LivingEntity living) {
					// Tied to the pose: it stops with it, on every end path.
					PowerAnimations.loop(living, PowerAnimations.Kind.DRAIN, ModSounds.FORCE_DRAIN_LOOP, 0.55f, 1f);

					if (Vfx.inRange(payload.pos()) || Vfx.inRange(living.position())) {
						begin(level, stream, living);
					}
				}
			}
			case ForceDrain.VARIANT_PULSE -> {
				Stream stream = STREAMS.get(id);

				if (stream != null) {
					stream.lastTarget = payload.pos();

					if (caster instanceof LivingEntity living && (Vfx.inRange(payload.pos()) || Vfx.inRange(living.position()))) {
						pulse(level, stream, living);
					}
				}
			}
			case ForceDrain.VARIANT_END -> {
				Stream stream = STREAMS.remove(id);
				PowerAnimations.stop(id, PowerAnimations.Kind.DRAIN);
				Vec3 at = caster != null ? EnergyKit.chest(caster) : payload.pos();
				EnergyKit.play(level, at, ModSounds.FORCE_DRAIN_END, 0.75f, EnergyKit.pitch(0.04f));

				if (stream != null) {
					letGo(stream, caster);
				}
			}
			default -> {
			}
		}
	}

	/** Whether {@code casterId} is draining, as far as this client knows. */
	public static boolean isDraining(int casterId) {
		Stream stream = STREAMS.get(casterId);
		return stream != null && ticks <= stream.until;
	}

	/** The strands grow out of the body toward the hand, and the hand starts to glow. */
	private static void begin(ClientLevel level, Stream stream, LivingEntity caster) {
		Anchor hand = Anchor.hand(caster.getId(), stream.left, REACH);

		for (int i = 0, n = Vfx.count(STRANDS); i < n; i++) {
			// Each strand from its own place on the body: chest, shoulders, throat.
			Anchor from = Anchor.body(stream.targetId, 0.3f + 0.4f * RANDOM.nextFloat(), 0.5f + 0.35f * RANDOM.nextFloat(),
					0.3f + 0.4f * RANDOM.nextFloat());
			Tendril strand = Tendril.between(stream.owner, from, hand, Palette.DRAIN).life(12, 2f, 6f, 5f + 2f * i)
					.width(1f - 0.15f * i, 0.45f).strength(0.9f - 0.12f * i).writhe(0.16f + 0.06f * RANDOM.nextFloat(), 1.1f + 0.5f * RANDOM.nextFloat(),
							0.05f + 0.03f * RANDOM.nextFloat())
					.beads(2.2f + RANDOM.nextFloat(), 0.045f + 0.02f * RANDOM.nextFloat(), 1.3f);
			stream.strands.add(strand);
			EnergyRender.add(strand);
		}

		stream.glow = Flare.on(stream.owner, hand, Palette.DRAIN).life(12, 4f, 6f).radius(0.34f).strength(0.5f).core(0.035f).flicker(0.25f);
		EnergyRender.add(stream.glow);
	}

	/** A pulse: the strands surge, a gout of motes and vapour tears out of the body, the hand flares. */
	private static void pulse(ClientLevel level, Stream stream, LivingEntity caster) {
		for (Tendril strand : stream.strands) {
			strand.surge(1.1f);
		}

		Anchor hand = Anchor.hand(caster.getId(), stream.left, REACH);
		EnergyRender.add(Flare.on(stream.owner, hand, Palette.DRAIN).burst(6).radius(0.6f, 0.3f).strength(Vfx.flashing() ? 0.75f : 0.45f).core(0.05f));
		Entity target = level.getEntity(stream.targetId);

		if (target == null || !target.isAlive()) {
			return;
		}

		EnergyRender.add(Flare.on(stream.owner, Anchor.body(stream.targetId, 0.5f, 0.62f, 0.5f), Palette.DRAIN).burst(5)
				.radius(Math.max(0.9f, target.getBbHeight() * 0.6f), 0.5f).strength(Vfx.flashing() ? 0.4f : 0.25f).lift(0.4f));

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			mote(stream, caster, target, 1.3f);
		}

		for (int i = 0, n = Vfx.count(3); i < n; i++) {
			vapour(caster, target, 0.2f);
		}
	}

	/** The end: the strands let go and fade, the glow in the hand dies in a breath of dark vapour. */
	private static void letGo(Stream stream, @Nullable Entity caster) {
		for (Tendril strand : stream.strands) {
			strand.release(6);
		}

		if (stream.glow != null) {
			stream.glow.release(8);
		}

		if (caster instanceof LivingEntity living && Vfx.inRange(living.position())) {
			Vec3 hand = EnergyKit.hand(living, stream.left, REACH, 1f);

			for (int i = 0, n = Vfx.count(3); i < n; i++) {
				puff(hand.add(EnergyKit.jitter(0.08)), new Vec3(EnergyKit.jitter1(0.01), 0.015 + 0.01 * RANDOM.nextDouble(), EnergyKit.jitter1(0.01)),
						0.1f, 22);
			}
		}
	}

	public static void tick(ClientLevel level) {
		ticks++;

		if (STREAMS.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Stream>> it = STREAMS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Stream> entry = it.next();
			Stream stream = entry.getValue();

			if (ticks > stream.until || !(level.getEntity(entry.getKey()) instanceof LivingEntity caster) || caster.isRemoved() || !caster.isAlive()) {
				PowerAnimations.stop(entry.getKey(), PowerAnimations.Kind.DRAIN);
				EnergyRender.kill(stream.owner);
				it.remove();
				continue;
			}

			Entity target = level.getEntity(stream.targetId);
			Vec3 from = target != null && target.isAlive() ? EnergyKit.chest(target) : stream.lastTarget;

			// Held while the channel lasts; let go when the target is gone.
			if (target != null && target.isAlive()) {
				for (Tendril strand : stream.strands) {
					strand.keep(6);
				}
			}

			if (stream.glow != null) {
				stream.glow.keep(6);
			}

			if (target != null && target.isAlive() && (Vfx.inRange(from) || Vfx.inRange(caster.position()))) {
				flow(stream, caster, target);
			}
		}
	}

	/** One tick of the stream: a mote or two leave the body for the hand, and dark vapour is pulled off after them. */
	private static void flow(Stream stream, LivingEntity caster, Entity target) {
		for (int i = 0, n = Vfx.count(1 + (ticks % 2 == 0 ? 1 : 0)); i < n; i++) {
			mote(stream, caster, target, 1f);
		}

		if (ticks % 3 == 0) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				vapour(caster, target, 0.12f);
			}
		}

		// Now and then a wisp of dark vapour curls off the drinking hand.
		if (ticks % 7 == 0 && !EnergyKit.firstPerson(caster)) {
			Vec3 hand = EnergyKit.hand(caster, stream.left, REACH, 1f);
			puff(hand.add(EnergyKit.jitter(0.06)), new Vec3(0, 0.012, 0), 0.07f, 18);
		}
	}

	/** A crimson mote leaves a random place on the body and flies to the hand on a curving path. */
	private static void mote(Stream stream, LivingEntity caster, Entity target, float size) {
		double width = target.getBbWidth();
		Vec3 at = target.position().add(EnergyKit.jitter1(width * 0.45), target.getBbHeight() * (0.3 + 0.6 * RANDOM.nextDouble()),
				EnergyKit.jitter1(width * 0.45));
		Vec3 burst = EnergyKit.randomUnit().scale(0.04 + 0.04 * RANDOM.nextDouble());
		Vec3 hand = EnergyKit.hand(caster, stream.left, REACH, 1f);
		int life = (int) Math.ceil(at.distanceTo(hand) / MOTE_SPEED) + 14;
		float swirl = (RANDOM.nextBoolean() ? 1f : -1f) * (0.2f + 0.4f * RANDOM.nextFloat());
		EnergyRender.add(Mote.at(stream.owner, at, burst, Palette.DRAIN).life(life, 2f, 3f).size(size * (0.55f + 0.3f * RANDOM.nextFloat()), size * 0.3f)
				.strength(0.95f).seek(Anchor.hand(caster.getId(), stream.left, REACH), MOTE_SPEED * (0.85f + 0.3f * RANDOM.nextFloat()),
						0.16f + 0.1f * RANDOM.nextFloat(), swirl, 0.14f));
	}

	/** Dark vapour pulled off the body toward the caster, thinning as it goes. */
	private static void vapour(LivingEntity caster, Entity target, float speed) {
		Vec3 at = target.position().add(EnergyKit.jitter1(target.getBbWidth() * 0.4), target.getBbHeight() * (0.35 + 0.5 * RANDOM.nextDouble()),
				EnergyKit.jitter1(target.getBbWidth() * 0.4));
		Vec3 toward = EnergyKit.chest(caster).subtract(at);
		toward = toward.lengthSqr() < 1.0e-4 ? Vec3.ZERO : toward.normalize();
		puff(at, toward.scale(speed * (0.5 + 0.5 * RANDOM.nextDouble())).add(0, 0.01, 0), 0.16f + 0.08f * RANDOM.nextFloat(), 16);
	}

	private static void puff(Vec3 at, Vec3 velocity, float size, int life) {
		ForceParticle p = EnergyKit.spawn(EnergyParticles.MIST, at, velocity);

		if (p != null) {
			p.color(VAPOUR[0], VAPOUR[1], VAPOUR[2]).size(size, size * 2.4f).alpha(0.5f).life(life + RANDOM.nextInt(6)).drag(0.88f);
		}
	}

	/** Forgets every stream (level change, disconnect); the poses and loops end with {@link PowerAnimations#clear}. */
	public static void clear() {
		for (Stream stream : STREAMS.values()) {
			EnergyRender.kill(stream.owner);
		}

		STREAMS.clear();
	}

	private DrainFx() {
	}
}
