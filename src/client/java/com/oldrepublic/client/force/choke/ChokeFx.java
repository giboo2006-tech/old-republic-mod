package com.oldrepublic.client.force.choke;

import static com.oldrepublic.client.force.kinetic.KineticKit.RANDOM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.PowerAnimations;
import com.oldrepublic.client.force.kinetic.AirStreakParticle;
import com.oldrepublic.client.force.kinetic.DustCloudParticle;
import com.oldrepublic.client.force.kinetic.KineticKit;
import com.oldrepublic.client.force.kinetic.KineticNoise;
import com.oldrepublic.client.force.kinetic.PebbleParticle;
import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.client.force.speed.PowerLoop;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.choke.ForceChoke;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Choke visuals and sounds (spec MILESTONE_4.md 9.3, 13.1, 13.3). Shown only by dust, grit and bent air: no glow and
 * no tendrils.
 *
 * <ul>
 * <li><b>Start</b> ({@code force_choke} 0 on the caster, pos = the target's chest, intensity = the hold in seconds):
 * {@code force.choke.start} and the {@code force.choke.loop} creak on the caster, which stops on the end or crush event,
 * the watchdog, the caster's death or removal, a level change or a disconnect.</li>
 * <li><b>Held target</b> ({@code force_choke_target}, intensity 1): as it is hauled off its feet the ground under it gives
 * up dust and grit, a few pebbles rise and hang trembling under its feet, and dust keeps welling up from the ground below
 * it and spiralling up toward its feet; at its throat the air is drawn tight, short streaks of bent air and dark grit
 * closing in on the neck from all round.
 * Its hands go to its throat and its feet hang ({@link #THROAT}, read by the humanoid model).</li>
 * <li><b>Release</b> (target intensity 0; caster 1 or 2): the pebbles fall, dust bursts from the ground in uneven puffs as
 * it drops, and {@code force.choke.release}; a Crush first slams the air shut on the throat, with a heavier burst and
 * {@code force.choke.crush}.</li>
 * </ul>
 */
public final class ChokeFx {
	/**
	 * How far a choked creature's hands are at its throat (0 to 1), on any living render state; null
	 * when it is not choked. Set by {@code mixin.foes.LivingEntityRendererChokeMixin}, drawn by
	 * {@code mixin.foes.HumanoidModelChokeMixin}.
	 */
	public static final RenderStateDataKey<Float> THROAT = RenderStateDataKey.create(() -> "oldrepublic:choke_throat");
	/** The longest hold (60 ticks) plus slack, for a lost release event. */
	private static final int WATCHDOG = 60 + 30;
	/** Dust wells up from the ground every this many ticks. */
	private static final int GRIT_INTERVAL = 2;
	/** The air at the throat draws tight every this many ticks. */
	private static final int SQUEEZE_INTERVAL = 3;
	/** Ticks the hands take to reach the throat. */
	private static final float THROAT_IN = 5f;

	/** A target held now: when it started, the watchdog end, its counts and the pebbles hanging under it. */
	private static final class Held {
		final long start;
		final long until;
		final int dust;
		final int squeeze;
		final List<PebbleParticle> pebbles = new ArrayList<>();
		boolean torn;

		Held(long start, long until) {
			this.start = start;
			this.until = until;
			// Counted once for the hold: per dust interval and per squeeze interval.
			this.dust = Vfx.count(2);
			this.squeeze = Vfx.count(3);
		}

		void dropPebbles() {
			for (PebbleParticle pebble : pebbles) {
				pebble.drop(KineticKit.spread(0.02), -0.02, KineticKit.spread(0.02));
			}

			pebbles.clear();
		}
	}

	/** A caster choking now: the watchdog end and the loop. */
	private static final class Choking {
		final long until;
		@Nullable PowerLoop loop;

		Choking(long until) {
			this.until = until;
		}
	}

	private static final Map<Integer, Held> HELD = new HashMap<>();
	private static final Map<Integer, Choking> CHOKING = new HashMap<>();
	private static long ticks;

	/** {@code force_choke}: start, end or crush on the caster. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity caster) {
		Vec3 pos = payload.pos();

		switch (payload.variant()) {
			case ForceChoke.VARIANT_START -> {
				Vec3 at = caster != null ? FxKit.chest(caster) : pos;
				FxKit.play(level, at, ModSounds.FORCE_CHOKE_START, 0.8f, FxKit.pitch(0.04f));
				stopCaster(payload.entityId());
				int watchdog = Math.round(payload.intensity() * 20f) + 30;
				Choking choking = new Choking(ticks + watchdog);
				CHOKING.put(payload.entityId(), choking);

				if (caster != null) {
					int id = caster.getId();
					// The free arm reaching for the target until the end or crush event (or the same watchdog).
					PowerAnimations.start(id, PowerAnimations.Kind.CHOKE, -1, watchdog);
					choking.loop = PowerLoop.start(ModSounds.FORCE_CHOKE_LOOP, caster, 0.5f, 1f, () -> isChoking(id), () -> 0);
				}
			}
			case ForceChoke.VARIANT_END -> {
				stopCaster(payload.entityId());
				FxKit.play(level, pos, ModSounds.FORCE_CHOKE_RELEASE, 0.75f, FxKit.pitch(0.05f));
			}
			case ForceChoke.VARIANT_CRUSH -> {
				stopCaster(payload.entityId());
				FxKit.play(level, pos, ModSounds.FORCE_CHOKE_CRUSH, 1f, FxKit.pitch(0.05f));

				if (Vfx.inRange(pos)) {
					slamShut(pos);
				}
			}
			default -> {
			}
		}
	}

	/** {@code force_choke_target}: intensity 1 while held, 0 when dropped (variant 1 after a Crush). */
	public static void onTarget(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity target) {
		if (payload.intensity() > 0f) {
			Held old = HELD.remove(payload.entityId());

			if (old != null) {
				old.dropPebbles();
			}

			HELD.put(payload.entityId(), new Held(ticks, ticks + WATCHDOG));
			return;
		}

		Held held = HELD.remove(payload.entityId());

		if (held != null) {
			held.dropPebbles();
		}

		Vec3 feet = target != null ? target.position() : payload.pos().subtract(0, 1, 0);

		if (Vfx.inRange(feet)) {
			drop(level, feet, payload.variant() == 1);
		}
	}

	/** Whether {@code entityId} is choking someone, as far as this client knows (for the caster's pose). */
	public static boolean isChoking(int entityId) {
		Choking choking = CHOKING.get(entityId);
		return choking != null && ticks <= choking.until;
	}

	/** How far {@code entityId}'s hands are at its throat: 0 when not choked, rising to 1. */
	public static float throat(int entityId, float partialTick) {
		if (HELD.isEmpty()) {
			return 0f;
		}

		Held held = HELD.get(entityId);
		return held == null ? 0f : Mth.clamp((ticks - held.start + partialTick) / THROAT_IN, 0f, 1f);
	}

	public static void tick(ClientLevel level) {
		ticks++;
		CHOKING.entrySet().removeIf(e -> {
			boolean over = ticks > e.getValue().until;

			if (over && e.getValue().loop != null) {
				e.getValue().loop.end();
			}

			return over;
		});

		Iterator<Map.Entry<Integer, Held>> it = HELD.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Held> entry = it.next();
			Held held = entry.getValue();
			Entity target = level.getEntity(entry.getKey());

			if (ticks > held.until || target == null || target.isRemoved() || !target.isAlive()) {
				held.dropPebbles();
				it.remove();
				continue;
			}

			if (Vfx.inRange(target.position())) {
				held(level, target, held);
			}
		}
	}

	/** The ground giving up dust and grit under the target, the pebbles hanging under it, and the air tight at its neck. */
	private static void held(ClientLevel level, Entity target, Held held) {
		Vec3 feet = target.position();
		BlockPos ground = KineticKit.ground(level, feet.subtract(0, 0.3, 0), 3);
		float[] dust = KineticKit.dustColor(level, ground);
		double width = target.getBbWidth() * 0.7;

		if (!held.torn && ground != null) {
			held.torn = true;
			tearUp(level, target, held, ground, dust);
		}

		if (ticks % GRIT_INTERVAL == 0 && ground != null) {
			double top = ground.getY() + 1.02;
			// The dust turns one way under one creature, the other way under the next.
			float turn = (target.getId() & 1) == 0 ? 0.1f : -0.1f;
			Vec3 under = new Vec3(feet.x, Math.max(top + 0.3, feet.y - 0.1), feet.z);

			for (int i = 0; i < held.dust; i++) {
				Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
				double reach = width * 0.5 + RANDOM.nextDouble() * 0.6;
				Vec3 at = new Vec3(feet.x + out.x * reach, top + 0.05, feet.z + out.z * reach);
				// Welling up off the ground and spiralling up toward the hanging feet, then thinning.
				DustCloudParticle cloud = KineticKit.dust(at, Vec3.ZERO, KineticKit.airDust(dust), 0.2f + RANDOM.nextFloat() * 0.1f, 2.2f,
						0.45f + RANDOM.nextFloat() * 0.15f, 22 + RANDOM.nextInt(8));

				if (cloud != null) {
					cloud.swirl(under, turn, 0.05f).hold(0.35f);
				}

				// A grain of grit tossed up with it, falling back.
				KineticKit.pebble(at, new Vec3(KineticKit.spread(0.02), 0.1 + RANDOM.nextDouble() * 0.08, KineticKit.spread(0.02)), KineticKit.gritColor(dust),
						0.03f + RANDOM.nextFloat() * 0.025f, 14 + RANDOM.nextInt(6));
			}
		}

		if (ticks % SQUEEZE_INTERVAL == 0) {
			Vec3 neck = FxKit.neck(target);
			float[] grit = KineticKit.gritColor(dust);

			for (int i = 0; i < held.squeeze; i++) {
				// Closing in from all round, level with the throat or a little above and below.
				Vec3 in = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2).add(0, KineticKit.spread(0.35), 0).normalize();
				Vec3 from = neck.add(in.scale(0.5 + RANDOM.nextDouble() * 0.25));
				AirStreakParticle streak = KineticKit.streak(from, in.scale(-0.12 - RANDOM.nextDouble() * 0.04), 0.22f, 0.6f, 5);

				if (streak != null) {
					streak.stretch(1f).drag(0.9f);
				}

				// Dark grit drawn round the throat and in, tight.
				DustCloudParticle mote = KineticKit.dust(neck.add(in.scale(0.38 + RANDOM.nextDouble() * 0.15)), Vec3.ZERO, grit, 0.07f + RANDOM.nextFloat() * 0.05f,
						0.6f, 0.65f, 10 + RANDOM.nextInt(4));

				if (mote != null) {
					mote.swirl(neck, 0.35f, 0.16f).hold(0.5f);
				}
			}
		}
	}

	/** As the target leaves the ground: a burst of dust under it and a few pebbles that rise and hang under its feet. */
	private static void tearUp(ClientLevel level, Entity target, Held held, BlockPos ground, float[] dust) {
		Vec3 feet = target.position();
		double top = ground.getY() + 1.02;
		float[] grit = KineticKit.gritColor(dust);
		float seed = RANDOM.nextFloat() * 40f;
		int clouds = Vfx.count(7);
		int pebbles = Vfx.count(5);

		for (int i = 0; i < clouds; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;

			if (KineticNoise.at((float) Math.cos(angle) * 1.8f + seed, (float) Math.sin(angle) * 1.8f, seed) < 0.36f) {
				continue;
			}

			Vec3 out = KineticKit.flat(angle);
			Vec3 at = new Vec3(feet.x + out.x * 0.3, top + 0.05, feet.z + out.z * 0.3);
			DustCloudParticle cloud = KineticKit.dust(at, out.scale(0.04 + RANDOM.nextDouble() * 0.05).add(0, 0.03 + RANDOM.nextDouble() * 0.03, 0),
					KineticKit.airDust(dust), 0.22f + RANDOM.nextFloat() * 0.12f, 2.6f, 0.5f, 26 + RANDOM.nextInt(12));

			if (cloud != null) {
				cloud.hold(0.25f).drag(0.9f);
			}
		}

		for (int i = 0; i < pebbles; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			Vec3 at = new Vec3(feet.x + out.x * (0.15 + RANDOM.nextDouble() * 0.45), top + 0.04, feet.z + out.z * (0.15 + RANDOM.nextDouble() * 0.45));
			PebbleParticle pebble = KineticKit.pebble(at, Vec3.ZERO, grit, 0.05f + RANDOM.nextFloat() * 0.06f, WATCHDOG + 30);

			if (pebble != null) {
				pebble.hover(WATCHDOG, 0.15 + RANDOM.nextDouble() * 0.5);
				held.pebbles.add(pebble);
			}
		}
	}

	/** A Crush: the air slams shut on the throat, streaks rushing into it from all round. */
	private static void slamShut(Vec3 chest) {
		Vec3 neck = chest.add(0, 0.45, 0);

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			Vec3 in = KineticKit.jitter(1).normalize();
			Vec3 from = neck.add(in.scale(0.8 + RANDOM.nextDouble() * 0.3));
			AirStreakParticle streak = KineticKit.streak(from, in.scale(-0.22), 0.22f, 0.55f, 5);

			if (streak != null) {
				streak.stretch(0.6f).drag(0.8f);
			}
		}
	}

	/** The drop: dust bursting from the ground in uneven puffs at the feet, heavier after a Crush, and grit thrown. */
	private static void drop(ClientLevel level, Vec3 feet, boolean crushed) {
		BlockPos ground = KineticKit.ground(level, feet.subtract(0, 0.3, 0), 3);
		float[] dust = KineticKit.dustColor(level, ground);
		float[] haze = KineticKit.airDust(dust);
		double top = ground != null ? ground.getY() + 1.02 : feet.y;
		float seed = RANDOM.nextFloat() * 40f;
		int clouds = Vfx.count(crushed ? 14 : 9);
		int pebbles = Vfx.count(crushed ? 6 : 3);

		for (int i = 0; i < clouds; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;

			if (KineticNoise.at((float) Math.cos(angle) * 2f + seed, (float) Math.sin(angle) * 2f, seed) < 0.34f) {
				continue;
			}

			Vec3 out = KineticKit.flat(angle);
			double speed = 0.05 + RANDOM.nextDouble() * (crushed ? 0.16 : 0.1);
			Vec3 at = new Vec3(feet.x + out.x * (0.15 + RANDOM.nextDouble() * 0.3), top + 0.05 + RANDOM.nextDouble() * 0.15, feet.z + out.z * (0.15 + RANDOM.nextDouble() * 0.3));
			DustCloudParticle cloud = KineticKit.dust(at, out.scale(speed).add(0, 0.02 + RANDOM.nextDouble() * 0.04, 0), haze,
					0.24f + RANDOM.nextFloat() * (crushed ? 0.2f : 0.12f), 2.6f, 0.55f + RANDOM.nextFloat() * 0.12f, 26 + RANDOM.nextInt(14));

			if (cloud != null) {
				cloud.hold(0.2f).lift(-0.0015f).drag(0.88f);
			}
		}

		for (int i = 0; i < pebbles; i++) {
			Vec3 out = KineticKit.flat(RANDOM.nextDouble() * Math.PI * 2);
			KineticKit.pebble(new Vec3(feet.x, top + 0.05, feet.z), out.scale(0.06 + RANDOM.nextDouble() * 0.08).add(0, 0.12 + RANDOM.nextDouble() * 0.1, 0),
					KineticKit.gritColor(dust), 0.04f + RANDOM.nextFloat() * 0.04f, 22 + RANDOM.nextInt(8));
		}
	}

	private static void stopCaster(int casterId) {
		Choking choking = CHOKING.remove(casterId);
		PowerAnimations.stop(casterId, PowerAnimations.Kind.CHOKE);

		if (choking != null && choking.loop != null) {
			choking.loop.end();
		}
	}

	/** Forgets every hold and stops every loop (level change, disconnect). */
	public static void clear() {
		for (Choking choking : CHOKING.values()) {
			if (choking.loop != null) {
				choking.loop.end();
			}
		}

		for (Held held : HELD.values()) {
			held.dropPebbles();
		}

		CHOKING.clear();
		HELD.clear();
	}

	private ChokeFx() {
	}
}
