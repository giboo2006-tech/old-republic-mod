package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.speed.FxKit.RANDOM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceM5Feature;
import com.oldrepublic.force.valor.ForceValor;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Valor visuals and sounds (MILESTONE_5.md 9.4, 15.1, 15.3): a warm gust lifts bright dust round the caster and each
 * ally in uneven puffs, and soft motes rise from each of them for a moment while it lasts. No glow, no ring.
 *
 * <ul>
 * <li><b>Cast</b> ({@code force_valor} 0 on the caster; intensity = seconds): the saber hand goes up and the other hand to
 * the chest ({@link PowerAnimations.Kind#VALOR}); {@code force.valor.cast}, a low swell; 20 {@code valor_dust} in three
 * uneven puffs.</li>
 * <li><b>An ally touched</b> (1, on the ally): the same puffs round the ally (the swell is the caster's alone).</li>
 * <li><b>End</b> (2, on any player whose valor ended, for any reason): {@code force.valor.end}, and the motes stop.</li>
 * </ul>
 * While it lasts, one mote per 10 ticks rises from each affected player.
 */
public final class ValorFx {
	/** Dust per player at the cast, at Standard (15.1). */
	private static final int GUST = 20;
	private static final int MOTE_INTERVAL = 10;
	private static final int CAST_POSE_TICKS = 26;
	/** Ticks past the effect's length before a lost end event is assumed. */
	private static final int WATCHDOG_SLACK = 20;
	/** Bright, warm dust. */
	private static final float R = 0.98f;
	private static final float G = 0.86f;
	private static final float B = 0.62f;
	/** How the gust's dust splits between its puffs: uneven on purpose. */
	private static final float[] PUFF_SHARE = {0.45f, 0.35f, 0.2f};

	/** Affected players: the watchdog end of their valor, in client ticks. */
	private static final Map<Integer, Long> LASTING = new HashMap<>();
	private static long ticks;

	/** {@code force_valor}: cast, an ally touched, or the end. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity player) {
		int id = payload.entityId();
		Vec3 feet = player != null ? player.position() : payload.pos();

		switch (payload.variant()) {
			case ForceValor.VARIANT_CAST -> {
				PowerAnimations.start(id, PowerAnimations.Kind.VALOR, CAST_POSE_TICKS, CAST_POSE_TICKS);
				FxKit.play(level, feet.add(0, 1, 0), ModSounds.FORCE_VALOR_CAST, 0.9f, FxKit.pitch(0.03f));
				gust(feet);
				LASTING.put(id, ticks + Math.round(payload.intensity() * 20f) + WATCHDOG_SLACK);
			}
			case ForceValor.VARIANT_ALLY -> {
				gust(feet);
				LASTING.put(id, ticks + Math.round(payload.intensity() * 20f) + WATCHDOG_SLACK);
			}
			case ForceValor.VARIANT_END -> {
				LASTING.remove(id);
				FxKit.play(level, feet.add(0, 1, 0), ModSounds.FORCE_VALOR_END, 0.6f, FxKit.pitch(0.04f));
			}
			default -> {
			}
		}
	}

	/** The warm gust: three uneven puffs of dust at the feet, each lifting and turning up and out. */
	private static void gust(Vec3 feet) {
		if (!Vfx.inRange(feet)) {
			return;
		}

		int count = Vfx.count(GUST);
		double base = RANDOM.nextDouble() * Math.PI * 2;
		// The gust turns one way for this cast.
		double turn = RANDOM.nextBoolean() ? 1 : -1;
		int spawned = 0;

		for (int puff = 0; puff < PUFF_SHARE.length; puff++) {
			int motes = puff == PUFF_SHARE.length - 1 ? count - spawned : Math.round(count * PUFF_SHARE[puff]);
			double centre = base + puff * 2.1 + FxKit.jitter1(0.5);

			for (int i = 0; i < motes; i++) {
				double angle = centre + FxKit.jitter1(0.45);
				double radius = 0.3 + RANDOM.nextDouble() * 0.4;
				Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
				Vec3 along = new Vec3(-out.z, 0, out.x).scale(turn);
				Vec3 at = feet.add(out.scale(radius)).add(0, 0.05 + RANDOM.nextDouble() * 0.6, 0);
				Vec3 velocity = out.scale(0.02 + RANDOM.nextDouble() * 0.03).add(along.scale(0.04 + RANDOM.nextDouble() * 0.03))
						.add(0, 0.05 + RANDOM.nextDouble() * 0.06, 0);
				mote(at, velocity, 0.05f + RANDOM.nextFloat() * 0.03f, 18 + RANDOM.nextInt(14));
			}

			spawned += motes;
		}
	}

	private static void mote(Vec3 at, Vec3 velocity, float size, int life) {
		ForceParticle p = FxKit.spawn(ForceM5Feature.VALOR_DUST, at, velocity);

		if (p != null) {
			// Light, so it keeps rising slowly as it slows.
			p.color(R, G, B).size(size, size * 0.6f).alpha(0.85f).life(life).drag(0.9f).fall(-0.08f, false);
		}
	}

	/** Whether {@code entityId}'s valor lasts, as far as this client knows. */
	public static boolean isLasting(int entityId) {
		Long until = LASTING.get(entityId);
		return until != null && ticks <= until;
	}

	public static void tick(ClientLevel level) {
		ticks++;

		if (LASTING.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Long>> it = LASTING.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Long> entry = it.next();
			Entity player = level.getEntity(entry.getKey());

			if (ticks > entry.getValue() || player == null || player.isRemoved() || !player.isAlive()) {
				it.remove();
				continue;
			}

			// Staggered by id, so a group's motes do not rise in step.
			if ((ticks + entry.getKey()) % MOTE_INTERVAL == 0 && Vfx.inRange(player.position()) && !player.isInvisible()) {
				for (int i = 0, n = Vfx.count(1); i < n; i++) {
					Vec3 at = player.position().add(FxKit.jitter1(player.getBbWidth() * 0.5), player.getBbHeight() * (0.2 + RANDOM.nextDouble() * 0.5),
							FxKit.jitter1(player.getBbWidth() * 0.5));
					mote(at, new Vec3(FxKit.jitter1(0.01), 0.02 + RANDOM.nextDouble() * 0.02, FxKit.jitter1(0.01)), 0.04f, 20 + RANDOM.nextInt(10));
				}
			}
		}
	}

	public static void clear() {
		LASTING.clear();
	}

	private ValorFx() {
	}
}
