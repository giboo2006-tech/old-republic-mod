package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.energy.EnergyKit.RANDOM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.energy.Anchor;
import com.oldrepublic.client.force.energy.EnergyKit;
import com.oldrepublic.client.force.energy.EnergyRender;
import com.oldrepublic.client.force.energy.Flare;
import com.oldrepublic.client.force.energy.Mote;
import com.oldrepublic.client.force.energy.Palette;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.EnergyParticles;
import com.oldrepublic.force.valor.ForceValor;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Valor visuals and sounds (MILESTONE_5.md 9.4, 15.1, 15.3): a warm updraft rises round the caster and each ally. Dust
 * is torn off the ground under them in the ground's own colour and swirled up, streaks of warm light climb round the body
 * in a loose spiral, and the chest fills with a breath of warm light that fades; while it lasts, sparks of the same light
 * rise off them now and then. No ring, dome or column.
 *
 * <ul>
 * <li><b>Cast</b> ({@code force_valor} 0 on the caster; intensity = seconds): the saber hand goes up and the other hand to
 * the chest ({@link PowerAnimations.Kind#VALOR}); {@code force.valor.cast}, a low swell; the updraft.</li>
 * <li><b>An ally touched</b> (1, on the ally): the same updraft round the ally, a little smaller (the swell is the
 * caster's alone).</li>
 * <li><b>End</b> (2, on any player whose valor ended, for any reason): {@code force.valor.end}, and the sparks stop.</li>
 * </ul>
 */
public final class ValorFx {
	/** Dust puffs at the cast, at Standard (15.1). */
	private static final int DUST = 10;
	/** Climbing streaks of light at the cast. */
	private static final int STREAKS = 9;
	/** Motes rising after them. */
	private static final int MOTES = 8;
	private static final int SPARK_INTERVAL = 6;
	private static final int CAST_POSE_TICKS = 26;
	/** Ticks past the effect's length before a lost end event is assumed. */
	private static final int WATCHDOG_SLACK = 20;
	/** {@link EnergyRender} owner kind of valor's light. */
	private static final int OWNER_KIND = 6;

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
				EnergyKit.play(level, feet.add(0, 1, 0), ModSounds.FORCE_VALOR_CAST, 0.9f, EnergyKit.pitch(0.03f));
				updraft(level, player, feet, 1f);
				LASTING.put(id, ticks + Math.round(payload.intensity() * 20f) + WATCHDOG_SLACK);
			}
			case ForceValor.VARIANT_ALLY -> {
				updraft(level, player, feet, 0.75f);
				LASTING.put(id, ticks + Math.round(payload.intensity() * 20f) + WATCHDOG_SLACK);
			}
			case ForceValor.VARIANT_END -> {
				LASTING.remove(id);
				EnergyKit.play(level, feet.add(0, 1, 0), ModSounds.FORCE_VALOR_END, 0.6f, EnergyKit.pitch(0.04f));
			}
			default -> {
			}
		}
	}

	/**
	 * The updraft at {@code feet} ({@code scale} 1 for the caster): dust torn off the ground and swirled up and out, streaks
	 * of light climbing round the body, motes rising after them, and a breath of warm light at the chest.
	 */
	private static void updraft(ClientLevel level, @Nullable Entity body, Vec3 feet, float scale) {
		if (!Vfx.inRange(feet)) {
			return;
		}

		long owner = EnergyRender.owner(OWNER_KIND, body != null ? body.getId() : 0);
		float height = body != null ? body.getBbHeight() : 1.8f;
		float width = body != null ? body.getBbWidth() : 0.6f;
		// The updraft turns one way for this cast.
		float turn = RANDOM.nextBoolean() ? 1f : -1f;
		Anchor axis = body != null ? Anchor.body(body.getId(), 0.5f, 0f, 0.5f) : Anchor.fixed(feet);
		BlockPos ground = EnergyKit.groundBelow(level, feet.add(0, 0.1, 0), 2);
		float[] rgb = EnergyKit.dustColor(level, ground);
		double floor = ground != null ? ground.getY() + 1.02 : feet.y;

		for (int i = 0, n = Vfx.count(Math.round(DUST * scale)); i < n; i++) {
			Vec3 out = EnergyKit.randomFlat();
			Vec3 along = new Vec3(-out.z, 0, out.x).scale(turn);
			Vec3 at = new Vec3(feet.x, floor, feet.z).add(out.scale(width * 0.5 + 0.1 + 0.3 * RANDOM.nextDouble()));
			Vec3 velocity = out.scale(0.05 + 0.05 * RANDOM.nextDouble()).add(along.scale(0.05 + 0.04 * RANDOM.nextDouble()))
					.add(0, 0.03 + 0.04 * RANDOM.nextDouble(), 0);
			ForceParticle p = EnergyKit.spawn(EnergyParticles.DUST, at, velocity);

			if (p != null) {
				p.color(rgb[0], rgb[1], rgb[2]).size(0.18f * scale, 0.55f * scale).alpha(0.6f).life(20 + RANDOM.nextInt(12)).drag(0.9f).fall(-0.03f, false);
			}
		}

		for (int i = 0, n = Vfx.count(Math.round(STREAKS * scale)); i < n; i++) {
			// Fast, so each leaves a long streak as it climbs round the body.
			float angle = RANDOM.nextFloat() * 6.2832f;
			float radius = width * 0.5f + 0.2f + 0.25f * RANDOM.nextFloat();
			EnergyRender.add(Mote.at(owner, feet, Vec3.ZERO, Palette.WARM).orbit(axis, radius, angle, 0.05f + 0.3f * RANDOM.nextFloat(),
					turn * (0.3f + 0.12f * RANDOM.nextFloat()), height * (0.07f + 0.03f * RANDOM.nextFloat()), 0.012f)
					.life(14 + RANDOM.nextInt(6), 1f, 6f).size(0.55f * scale, 0.3f).strength(0.9f));
		}

		for (int i = 0, n = Vfx.count(Math.round(MOTES * scale)); i < n; i++) {
			Vec3 at = feet.add(EnergyKit.jitter1(width * 0.6), height * (0.1 + 0.6 * RANDOM.nextDouble()), EnergyKit.jitter1(width * 0.6));
			EnergyRender.add(Mote.at(owner, at, EnergyKit.randomFlat().scale(0.02).add(0, 0.06 + 0.04 * RANDOM.nextDouble(), 0), Palette.WARM)
					.life(26 + RANDOM.nextInt(14), 3f, 12f).size(0.5f * scale, 0.25f).strength(0.85f).drift(-0.001f, 0.95f).wander(0.004f).twinkle());
		}

		if (body != null) {
			EnergyRender.add(Flare.on(owner, Anchor.body(body.getId(), 0.5f, 0.6f, 0.5f), Palette.WARM).life(16, 3f, 12f)
					.radius(Math.max(0.8f, height * 0.5f) * scale, 1.2f * scale).strength(0.3f).lift(0.35f));
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

			// Staggered by id, so a group's sparks do not rise in step.
			if ((ticks + entry.getKey()) % SPARK_INTERVAL == 0 && Vfx.inRange(player.position()) && !player.isInvisible()
					&& !EnergyKit.firstPerson(player)) {
				for (int i = 0, n = Vfx.count(1); i < n; i++) {
					Vec3 at = player.position().add(EnergyKit.jitter1(player.getBbWidth() * 0.5), player.getBbHeight() * (0.15 + RANDOM.nextDouble() * 0.6),
							EnergyKit.jitter1(player.getBbWidth() * 0.5));
					EnergyRender.add(Mote.at(EnergyRender.owner(OWNER_KIND, player.getId()), at, new Vec3(EnergyKit.jitter1(0.006),
							0.035 + RANDOM.nextDouble() * 0.02, EnergyKit.jitter1(0.006)), Palette.WARM).life(22 + RANDOM.nextInt(10), 3f, 10f)
							.size(0.45f, 0.25f).strength(0.8f).drift(-0.0005f, 0.97f).wander(0.003f).twinkle());
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
