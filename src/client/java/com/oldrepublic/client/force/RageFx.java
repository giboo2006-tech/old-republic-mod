package com.oldrepublic.client.force;

import static com.oldrepublic.client.force.speed.FxKit.RANDOM;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.enemy.EnemyParticleProviders;
import com.oldrepublic.client.force.speed.FxKit;
import com.oldrepublic.client.force.speed.PowerLoop;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.EnemyParticles;
import com.oldrepublic.force.ForceMobEffects;
import com.oldrepublic.force.rage.ForceRage;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberComponents;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Rage visuals and sounds (MILESTONE_5.md 9.7, 15.1, 15.3).
 *
 * <ul>
 * <li>Seen by everyone, while it lasts: embers trailing the lit blade (one {@code flare_ember} a tick), a heat haze round
 * the caster (one {@code air_ripple} every 5 ticks), and the hunched posture ({@link #POSTURE}: shoulders up, saber arm
 * low, an offset the humanoid model adds to the idle and walk pose, never over a swing or another power's pose).</li>
 * <li>For the caster only: the {@code force.rage.loop} and a dark-red edge vignette, pulsing slowly (steady with flashing
 * effects off).</li>
 * <li><b>Start</b> ({@code force_rage} 0 on the caster; intensity = seconds): {@code force.rage.cast}, a growl in the
 * Force, for everyone near. <b>End</b> (1): {@code force.rage.end}; everything above stops (the loop and the vignette as
 * soon as the effect goes).</li>
 * </ul>
 * The caster's own client follows its {@code rage} effect, so a relog keeps the loop, the vignette and the posture; other
 * clients follow the events, with a watchdog.
 */
public final class RageFx {
	/** The rage posture on a player's render state: how far into it (0 to 1) and which arm holds the saber. */
	public record Posture(float amount, boolean saberLeft) {
	}

	/** Set by {@code mixin.force.AvatarRendererMixin}, applied by {@code mixin.force.HumanoidModelMixin}; null when calm. */
	public static final RenderStateDataKey<Posture> POSTURE = RenderStateDataKey.create(() -> "oldrepublic:rage_posture");
	/** Ticks the posture and the vignette take to come and go. */
	private static final float BLEND = 6f;
	private static final int HAZE_INTERVAL = 5;
	/** Ticks past the effect's length before a lost end event is assumed (other players). */
	private static final int WATCHDOG_SLACK = 20;
	/** Ember colour: a dull hot red-orange. */
	private static final int EMBER = 0xE0582A;
	/** The vignette: its colour, its strongest alpha at the very edge, its slow pulse. */
	private static final int VIGNETTE_RGB = 0x5A0A0C;
	private static final float VIGNETTE_ALPHA = 0.42f;
	private static final float PULSE_TICKS = 32f;
	private static final int VIGNETTE_STEP = 2;

	/** A raging player: when it started, the end (or the watchdog), and whether it is ending. */
	private static final class Raging {
		final long start;
		long until;
		boolean ending;

		Raging(long start, long until) {
			this.start = start;
			this.until = until;
		}
	}

	private static final Map<Integer, Raging> RAGING = new HashMap<>();
	private static @Nullable PowerLoop loop;
	private static long ticks;

	/** {@code force_rage}: start or end on the caster. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity caster) {
		int id = payload.entityId();
		Vec3 at = caster != null ? FxKit.chest(caster) : payload.pos();

		switch (payload.variant()) {
			case ForceRage.VARIANT_START -> {
				RAGING.put(id, new Raging(ticks, ticks + Math.round(payload.intensity() * 20f) + WATCHDOG_SLACK));
				FxKit.play(level, at, ModSounds.FORCE_RAGE_CAST, 1f, FxKit.pitch(0.04f));

				if (mc.player != null && mc.player.getId() == id) {
					startLoop(mc.player);
				}
			}
			case ForceRage.VARIANT_END -> {
				end(id);
				FxKit.play(level, at, ModSounds.FORCE_RAGE_END, 0.8f, FxKit.pitch(0.04f));
			}
			default -> {
			}
		}
	}

	private static void end(int id) {
		Raging raging = RAGING.get(id);

		if (raging != null && !raging.ending) {
			raging.ending = true;
			raging.until = Math.min(raging.until, ticks + (long) BLEND);
		}
	}

	/** The loop, for the caster only: it stops by itself when the effect goes, on death, a level change or a disconnect. */
	private static void startLoop(LocalPlayer player) {
		if (loop != null && !loop.isStopped()) {
			return;
		}

		loop = PowerLoop.start(ModSounds.FORCE_RAGE_LOOP, player, 0.4f, 1f, () -> isRaging(player), () -> 0);
	}

	/** Whether {@code entity} rages now: its own effect for the local player, the events for anyone else. */
	public static boolean isRaging(Entity entity) {
		Minecraft mc = Minecraft.getInstance();

		if (entity == mc.player) {
			return mc.player.hasEffect(ForceMobEffects.RAGE);
		}

		Raging raging = RAGING.get(entity.getId());
		return raging != null && !raging.ending && ticks <= raging.until;
	}

	/** How far into the rage {@code entityId} is (0 to 1), easing in and out. */
	private static float amount(int entityId, float partialTick) {
		if (RAGING.isEmpty()) {
			return 0f;
		}

		Raging raging = RAGING.get(entityId);

		if (raging == null) {
			return 0f;
		}

		float in = Mth.clamp((ticks - raging.start + partialTick) / BLEND, 0f, 1f);
		float out = Mth.clamp((raging.until - ticks - partialTick) / BLEND, 0f, 1f);
		return in * out;
	}

	/** The posture to render for {@code entity}, or null when it is calm. */
	public static @Nullable Posture posture(LivingEntity entity, float partialTick) {
		float amount = amount(entity.getId(), partialTick);
		return amount > 0f ? new Posture(amount, saberArmIsLeft(entity)) : null;
	}

	/** The arm with a lit saber (the main hand's first), else the main arm. */
	private static boolean saberArmIsLeft(LivingEntity entity) {
		boolean mainLeft = entity.getMainArm() == HumanoidArm.LEFT;
		boolean offOnly = !SaberComponents.isUsable(entity.getMainHandItem()) && SaberComponents.isUsable(entity.getOffhandItem());
		return offOnly != mainLeft;
	}

	public static void tick(Minecraft mc, ClientLevel level) {
		ticks++;
		LocalPlayer self = mc.player;

		if (self != null) {
			// -1 is an endless effect (a command): it lasts at least another second.
			int remaining = ForceMobEffects.remaining(self, ForceMobEffects.RAGE);
			int left = remaining < 0 ? 20 : remaining;
			Raging own = RAGING.get(self.getId());

			if (remaining != 0 && self.isAlive()) {
				if (own == null || own.ending) {
					own = new Raging(ticks, ticks + left);
					RAGING.put(self.getId(), own);
				}

				own.until = ticks + left;
				startLoop(self);
			} else if (own != null) {
				end(self.getId());
			}
		}

		if (RAGING.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<Integer, Raging>> it = RAGING.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Raging> entry = it.next();
			Raging raging = entry.getValue();

			if (ticks > raging.until || !(level.getEntity(entry.getKey()) instanceof LivingEntity caster) || caster.isRemoved() || !caster.isAlive()) {
				it.remove();
				continue;
			}

			if (!raging.ending && Vfx.inRange(caster.position()) && !caster.isInvisible()) {
				embers(mc, caster);

				if ((ticks + entry.getKey()) % HAZE_INTERVAL == 0) {
					haze(caster);
				}
			}
		}
	}

	/** One ember a tick, somewhere along the lit blade, drifting up and dying fast. */
	private static void embers(Minecraft mc, LivingEntity caster) {
		Vec3[] blade = blade(mc, caster);

		if (blade == null) {
			return;
		}

		for (int i = 0; i < Vfx.count(1); i++) {
			Vec3 at = blade[0].lerp(blade[1], 0.2 + RANDOM.nextDouble() * 0.8).add(FxKit.jitter(0.03));
			EnemyParticleProviders.EnemyParticle ember = EnemyParticleProviders.spawn(EnemyParticles.FLARE_EMBER, at,
					new Vec3(FxKit.jitter1(0.01), 0.015 + RANDOM.nextDouble() * 0.02, FxKit.jitter1(0.01)));

			if (ember != null) {
				ember.tint(EMBER).size(0.025f + RANDOM.nextFloat() * 0.015f).life(8 + RANDOM.nextInt(8)).gravity(-0.01f).bright();
			}
		}
	}

	/**
	 * Base and tip of {@code caster}'s lit blade, roughly: low and forward in third person (the rage posture), low at the
	 * side of the view in first person; null without a lit saber in hand.
	 */
	private static Vec3 @Nullable [] blade(Minecraft mc, LivingEntity caster) {
		boolean main = SaberComponents.isUsable(caster.getMainHandItem());

		if (!main && !SaberComponents.isUsable(caster.getOffhandItem())) {
			return null;
		}

		boolean left = (caster.getMainArm() == HumanoidArm.LEFT) == main;
		double side = left ? -1 : 1;

		if (caster == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
			Vec3 look = caster.getLookAngle();
			Vec3 right = look.cross(new Vec3(0, 1, 0));
			right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
			Vec3 up = right.cross(look).normalize();
			Vec3 base = caster.getEyePosition().add(look.scale(0.55)).add(right.scale(0.3 * side)).add(up.scale(-0.3));
			return new Vec3[] {base, base.add(look.scale(0.45).add(up.scale(0.75)).normalize().scale(0.7))};
		}

		float yaw = caster.yBodyRot * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(side);
		Vec3 hand = caster.position().add(0, caster.getBbHeight() * 0.42, 0).add(right.scale(0.36)).add(forward.scale(0.2));
		return new Vec3[] {hand, hand.add(forward.scale(0.85).add(0, 0.5, 0).normalize())};
	}

	/** The heat haze: a small shell of bent air rising off the body. */
	private static void haze(LivingEntity caster) {
		for (int i = 0; i < Vfx.count(1); i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 at = caster.position().add(Math.cos(angle) * caster.getBbWidth() * 0.6, caster.getBbHeight() * (0.3 + RANDOM.nextDouble() * 0.6),
					Math.sin(angle) * caster.getBbWidth() * 0.6);
			FxKit.ripple(at, new Vec3(0, 0.02 + RANDOM.nextDouble() * 0.015, 0), 0.22f, 0.45f, 0.12f, 14);
		}
	}

	/**
	 * The caster's dark-red edge vignette (a HUD element after the vanilla overlays): darkest at the screen's edges, fading
	 * in a fifth of the way in. It pulses slowly; with flashing effects off it is steady.
	 */
	public static void extractVignette(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || RAGING.isEmpty() || mc.gui.hud.isHidden()) {
			return;
		}

		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
		float amount = amount(mc.player.getId(), partialTick);

		if (amount <= 0.01f) {
			return;
		}

		float pulse = Vfx.flashing() ? 0.82f + 0.18f * Mth.sin((ticks + partialTick) * Mth.TWO_PI / PULSE_TICKS) : 0.9f;
		float edge = VIGNETTE_ALPHA * amount * pulse;
		int width = g.guiWidth();
		int height = g.guiHeight();
		int band = Math.max(8, Math.min(width, height) / 5);

		for (int i = 0; i < band; i += VIGNETTE_STEP) {
			float fall = 1f - (float) i / band;
			int alpha = Mth.clamp(Math.round(edge * fall * fall * 255f), 0, 255);

			if (alpha == 0) {
				continue;
			}

			int color = alpha << 24 | VIGNETTE_RGB;
			g.fill(0, i, width, i + VIGNETTE_STEP, color);
			g.fill(0, height - i - VIGNETTE_STEP, width, height - i, color);
			g.fill(i, 0, i + VIGNETTE_STEP, height, color);
			g.fill(width - i - VIGNETTE_STEP, 0, width - i, height, color);
		}
	}

	/** Forgets every rage and stops the loop (level change, disconnect). */
	public static void clear() {
		RAGING.clear();

		if (loop != null) {
			loop.end();
			loop = null;
		}
	}

	private RageFx() {
	}
}
