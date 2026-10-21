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
import com.oldrepublic.client.force.energy.Spark;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.force.speed.PowerLoop;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.EnergyParticles;
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
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Force Rage visuals and sounds (MILESTONE_5.md 9.7, 15.1, 15.3).
 *
 * <ul>
 * <li><b>Start</b> ({@code force_rage} 0 on the caster; intensity = seconds): {@code force.rage.cast}, a growl in the
 * Force, for everyone near, and the rage erupts out of the body: a blast of heat (a flare, gentler with flashing effects
 * off), dark smoke thrown out round it, a spray of embers flung up, dust torn off the ground in its own colour and a
 * shell of bent air; the caster's own view jolts.</li>
 * <li>Seen by everyone, while it lasts: the eyes smoulder like embers, embers rise off the body, some caught in the heat and
 * carried up round it in a loose spiral, and stream off the lit blade; dark smoke curls up from the shoulders, now and then
 * a puff of it lit dull red from below; a heat haze shimmers round the caster (one {@code air_ripple} every 5 ticks), and
 * the hunched posture
 * ({@link #POSTURE}: shoulders up, saber arm low, an offset the humanoid model adds to the idle and walk pose, never over a
 * swing or another power's pose).</li>
 * <li>For the caster only: the {@code force.rage.loop} and a dark-red edge vignette, pulsing slowly (steady with flashing
 * effects off).</li>
 * <li><b>End</b> (1): {@code force.rage.end}; everything above stops (the loop and the vignette as soon as the effect
 * goes), with a last breath of smoke.</li>
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
	private static final int SMOKE_INTERVAL = 6;
	/** An ember carried up round the body this often. */
	private static final int SWIRL_INTERVAL = 2;
	/** The smouldering eyes: how far above the eye height the drawn eyes sit, and how far apart. */
	private static final float EYE_RAISE = 0.1f;
	private static final float EYE_APART = 0.12f;
	/** Ticks past the effect's length before a lost end event is assumed (other players). */
	private static final int WATCHDOG_SLACK = 20;
	/** {@link EnergyRender} owner kind of the rage's light. */
	private static final int OWNER_KIND = 7;
	/** Rage smoke: near black, a little red; and lit from below by the embers, a dull red. */
	private static final float[] SMOKE = {0.16f, 0.05f, 0.05f};
	private static final float[] LIT_SMOKE = {0.6f, 0.18f, 0.07f};
	/** The vignette: its colour, its strongest alpha at the very edge, its slow pulse. */
	private static final int VIGNETTE_RGB = 0x5A0A0C;
	private static final float VIGNETTE_ALPHA = 0.42f;
	private static final float PULSE_TICKS = 32f;
	private static final int VIGNETTE_STEP = 2;

	/** A raging player: when it started, the end (or the watchdog), whether it is ending, and its smouldering eyes. */
	private static final class Raging {
		final long start;
		long until;
		boolean ending;
		Flare @Nullable [] eyes;

		Raging(long start, long until) {
			this.start = start;
			this.until = until;
		}

		/** Lets the eyes die down. */
		void douse() {
			if (eyes != null) {
				for (Flare eye : eyes) {
					eye.release(6);
				}

				eyes = null;
			}
		}
	}

	private static final Map<Integer, Raging> RAGING = new HashMap<>();
	private static @Nullable PowerLoop loop;
	private static long ticks;

	/** {@code force_rage}: start or end on the caster. */
	public static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload payload, @Nullable Entity caster) {
		int id = payload.entityId();
		Vec3 at = caster != null ? EnergyKit.chest(caster) : payload.pos();

		switch (payload.variant()) {
			case ForceRage.VARIANT_START -> {
				RAGING.put(id, new Raging(ticks, ticks + Math.round(payload.intensity() * 20f) + WATCHDOG_SLACK));
				EnergyKit.play(level, at, ModSounds.FORCE_RAGE_CAST, 1f, EnergyKit.pitch(0.04f));

				if (mc.player != null && mc.player.getId() == id) {
					startLoop(mc.player);
					CameraShake.add(0.35f);
				}

				if (caster instanceof LivingEntity living && Vfx.inRange(living.position())) {
					erupt(level, living);
				}
			}
			case ForceRage.VARIANT_END -> {
				end(id);
				EnergyKit.play(level, at, ModSounds.FORCE_RAGE_END, 0.8f, EnergyKit.pitch(0.04f));

				if (caster != null && Vfx.inRange(caster.position()) && !EnergyKit.firstPerson(caster)) {
					for (int i = 0, n = Vfx.count(3); i < n; i++) {
						smoke(caster.position().add(EnergyKit.jitter1(0.3), caster.getBbHeight() * (0.5 + 0.4 * RANDOM.nextDouble()), EnergyKit.jitter1(0.3)),
								new Vec3(0, 0.02, 0), 0.18f, 26);
					}
				}
			}
			default -> {
			}
		}
	}

	/**
	 * The rage erupts out of the body: a blast of heat, dark smoke thrown out, embers flung up, dust torn off the ground and
	 * a shell of bent air.
	 */
	private static void erupt(ClientLevel level, LivingEntity caster) {
		long owner = EnergyRender.owner(OWNER_KIND, caster.getId());
		Vec3 feet = caster.position();
		float height = caster.getBbHeight();
		boolean own = EnergyKit.firstPerson(caster);

		if (!own) {
			EnergyRender.add(Flare.on(owner, Anchor.body(caster.getId(), 0.5f, 0.55f, 0.5f), Palette.EMBER_HOT).burst(9)
					.radius(Math.max(1f, height * 0.7f), 0.7f).strength(Vfx.flashing() ? 0.6f : 0.35f).core(0.05f).lift(0.4f));
		}

		for (int i = 0, n = Vfx.count(22); i < n; i++) {
			Vec3 out = EnergyKit.randomFlat();
			Vec3 at = feet.add(out.scale(caster.getBbWidth() * 0.4)).add(0, height * (0.3 + 0.55 * RANDOM.nextDouble()), 0);
			Vec3 velocity = out.scale(0.06 + 0.1 * RANDOM.nextDouble()).add(0, 0.08 + 0.12 * RANDOM.nextDouble(), 0);
			ember(owner, at, velocity, 16 + RANDOM.nextInt(14), 1.3f, -0.002f, 0.9f);
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			swirl(owner, caster, 0.1f + 0.5f * RANDOM.nextFloat());
		}

		for (int i = 0, n = Vfx.count(own ? 4 : 9); i < n; i++) {
			Vec3 out = EnergyKit.randomFlat();
			Vec3 at = feet.add(out.scale(caster.getBbWidth() * 0.5)).add(0, height * (0.2 + 0.5 * RANDOM.nextDouble()), 0);
			smoke(at, out.scale(0.07 + 0.06 * RANDOM.nextDouble()).add(0, 0.02, 0), 0.26f, 24);
		}

		BlockPos ground = EnergyKit.groundBelow(level, feet.add(0, 0.1, 0), 2);

		if (ground != null) {
			float[] rgb = EnergyKit.dustColor(level, ground);

			for (int i = 0, n = Vfx.count(8); i < n; i++) {
				Vec3 out = EnergyKit.randomFlat();
				Vec3 at = new Vec3(feet.x, ground.getY() + 1.05, feet.z).add(out.scale(caster.getBbWidth() * 0.5 + 0.1));
				ForceParticle p = EnergyKit.spawn(EnergyParticles.DUST, at, out.scale(0.1 + 0.08 * RANDOM.nextDouble()).add(0, 0.015, 0));

				if (p != null) {
					p.color(rgb[0], rgb[1], rgb[2]).size(0.2f, 0.6f).alpha(0.6f).life(18 + RANDOM.nextInt(10)).drag(0.86f);
				}
			}
		}

		ForceFxUtil.ripple(EnergyKit.chest(caster), Vec3.ZERO, 0.4f, 2.2f, 0.22f, 9);
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
				raging.douse();
				it.remove();
				continue;
			}

			boolean seen = !raging.ending && Vfx.inRange(caster.position()) && !caster.isInvisible();

			if (seen) {
				embers(mc, caster);
			}

			if (seen && !EnergyKit.firstPerson(caster)) {
				smoulder(raging, caster);
				seethe(caster);

				if ((ticks + entry.getKey()) % HAZE_INTERVAL == 0) {
					haze(caster);
				}
			} else {
				raging.douse();
			}
		}
	}

	/** One ember a tick, somewhere along the lit blade, drifting up and dying fast. */
	private static void embers(Minecraft mc, LivingEntity caster) {
		Vec3[] blade = blade(mc, caster);

		if (blade == null) {
			return;
		}

		for (int i = 0, n = Vfx.count(1); i < n; i++) {
			Vec3 at = blade[0].lerp(blade[1], 0.2 + RANDOM.nextDouble() * 0.8).add(EnergyKit.jitter(0.03));
			ember(EnergyRender.owner(OWNER_KIND, caster.getId()), at, new Vec3(EnergyKit.jitter1(0.012), 0.02 + RANDOM.nextDouble() * 0.025,
					EnergyKit.jitter1(0.012)), 8 + RANDOM.nextInt(8), 0.7f, -0.0015f, 0.96f);
		}
	}

	/**
	 * The body seethes: embers rise off it every tick, every third tick one is carried up round it, and now and then dark
	 * smoke curls up from the shoulders, every other puff lit dull red from below.
	 */
	private static void seethe(LivingEntity caster) {
		long owner = EnergyRender.owner(OWNER_KIND, caster.getId());
		double width = caster.getBbWidth();
		float height = caster.getBbHeight();
		long beat = ticks + caster.getId();

		for (int i = 0, n = Vfx.count(1); i < n; i++) {
			Vec3 at = caster.position().add(EnergyKit.jitter1(width * 0.55), height * (0.2 + 0.7 * RANDOM.nextDouble()), EnergyKit.jitter1(width * 0.55));
			ember(owner, at, new Vec3(EnergyKit.jitter1(0.01), 0.03 + 0.03 * RANDOM.nextDouble(), EnergyKit.jitter1(0.01)), 16 + RANDOM.nextInt(12), 1.25f,
					-0.0015f, 0.96f);
		}

		if (beat % SWIRL_INTERVAL == 0) {
			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				swirl(owner, caster, 0.05f + 0.45f * RANDOM.nextFloat());
			}
		}

		if (beat % SMOKE_INTERVAL == 0) {
			boolean lit = beat % (SMOKE_INTERVAL * 2) == 0;

			for (int i = 0, n = Vfx.count(1); i < n; i++) {
				Vec3 at = caster.position().add(EnergyKit.jitter1(width * 0.5), height * (0.72 + 0.2 * RANDOM.nextDouble()), EnergyKit.jitter1(width * 0.5));
				Vec3 drift = new Vec3(EnergyKit.jitter1(0.006), 0.018 + 0.01 * RANDOM.nextDouble(), EnergyKit.jitter1(0.006));

				if (lit) {
					litSmoke(at, drift, 0.15f, 24);
				} else {
					smoke(at, drift, 0.13f, 28);
				}
			}
		}
	}

	/**
	 * An ember caught in the heat: carried up round the body in a loose spiral from {@code from} (a share of its height),
	 * widening as it climbs, then let go to drift and die.
	 */
	private static void swirl(long owner, LivingEntity caster, float from) {
		float height = caster.getBbHeight();
		float radius = caster.getBbWidth() * 0.5f + 0.12f + 0.25f * RANDOM.nextFloat();
		float spin = (RANDOM.nextBoolean() ? 1f : -1f) * (0.14f + 0.08f * RANDOM.nextFloat());
		EnergyRender.add(Mote.at(owner, caster.position(), Vec3.ZERO, Palette.EMBER_HOT)
				.orbit(Anchor.body(caster.getId(), 0.5f, 0f, 0.5f), radius, RANDOM.nextFloat() * 6.2832f, height * from, spin,
						0.035f + 0.025f * RANDOM.nextFloat(), 0.006f)
				.life(18 + RANDOM.nextInt(10), 2f, 8f).size(1.7f, 0.9f).strength(1f).twinkle());
	}

	/** The eyes smoulder like embers while the rage lasts: lit the first tick, kept alight each tick after. */
	private static void smoulder(Raging raging, LivingEntity caster) {
		if (raging.eyes == null) {
			long owner = EnergyRender.owner(OWNER_KIND, caster.getId());
			raging.eyes = new Flare[2];

			for (int i = 0; i < 2; i++) {
				raging.eyes[i] = Flare.on(owner, eye(caster.getId(), i == 0 ? -1 : 1), Palette.EMBER_HOT).life(6, 4f, 6f).radius(0.075f)
						.strength(0.85f).core(0.012f).flicker(0.35f).lift(0.06f);
				EnergyRender.add(raging.eyes[i]);
			}
		}

		for (Flare eye : raging.eyes) {
			eye.keep(4);
		}
	}

	/** An eye of {@code entityId} ({@code side} -1 left, 1 right): on the face, a little either side of its middle. */
	private static Anchor eye(int entityId, int side) {
		return partial -> {
			if (!(Anchor.entity(entityId) instanceof LivingEntity entity)) {
				return null;
			}

			float yaw = Mth.rotLerp(partial, entity.yHeadRotO, entity.yHeadRot) * Mth.DEG_TO_RAD;
			Vec3 face = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
			Vec3 right = new Vec3(-face.z, 0, face.x);
			return entity.getEyePosition(partial).add(0, EYE_RAISE, 0).add(face.scale(entity.getBbWidth() * 0.42 + 0.03)).add(right.scale(EYE_APART * side));
		};
	}

	/** An ember: a glowing speck that rises (negative {@code gravity}) or falls, cooling from yellow-orange to a dull red. */
	private static void ember(long owner, Vec3 at, Vec3 velocity, int life, float size, float gravity, float drag) {
		EnergyRender.add(new Spark(owner, at, velocity, life, size * (0.8f + 0.4f * RANDOM.nextFloat()), gravity, drag, Palette.EMBER_HOT,
				Palette.EMBER_COOL));
	}

	/** A puff of dark smoke. */
	private static void smoke(Vec3 at, Vec3 velocity, float size, int life) {
		ForceParticle p = EnergyKit.spawn(EnergyParticles.SMOKE, at, velocity);

		if (p != null) {
			p.color(SMOKE[0], SMOKE[1], SMOKE[2]).size(size, size * 2.6f).alpha(0.5f).life(life + RANDOM.nextInt(10)).drag(0.92f);
		}
	}

	/** A puff of smoke lit dull red by the embers under it: it shows by night, when the dark smoke cannot. */
	private static void litSmoke(Vec3 at, Vec3 velocity, float size, int life) {
		ForceParticle p = EnergyKit.spawn(EnergyParticles.SMOKE, at, velocity);

		if (p != null) {
			p.color(LIT_SMOKE[0], LIT_SMOKE[1], LIT_SMOKE[2]).size(size, size * 2.6f).alpha(0.5f).life(life + RANDOM.nextInt(8)).drag(0.92f)
					.fullBright();
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
		for (int i = 0, n = Vfx.count(1); i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			Vec3 at = caster.position().add(Math.cos(angle) * caster.getBbWidth() * 0.6, caster.getBbHeight() * (0.3 + RANDOM.nextDouble() * 0.6),
					Math.sin(angle) * caster.getBbWidth() * 0.6);
			ForceFxUtil.ripple(at, new Vec3(0, 0.02 + RANDOM.nextDouble() * 0.015, 0), 0.22f, 0.45f, 0.12f, 14);
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
		for (Raging raging : RAGING.values()) {
			raging.douse();
		}

		RAGING.clear();

		if (loop != null) {
			loop.end();
			loop = null;
		}
	}

	private RageFx() {
	}
}
