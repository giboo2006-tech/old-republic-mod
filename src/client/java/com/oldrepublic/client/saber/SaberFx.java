package com.oldrepublic.client.saber;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.force.energy.Anchor;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.saber.workbench.LightsaberWorkbenchScreen;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Client handlers for the saber effect events: sparks in the blade colour, the contact flash
 * (one tick, or a soft glow when flashing effects are off), sounds and camera kicks, and the
 * technique poses ({@link SaberAnimations}) of the combo, heavy attack, lunge, form change, Saber
 * Throw, saber NPC tells and NPC bolt deflections. The swing trails are the blades' own
 * ({@link BladeTrails}).
 * Clash and deflect sounds are limited to one per entity every {@value #SOUND_GAP} ticks.
 */
final class SaberFx {
	private static final int SOUND_GAP = 3;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<Integer, Long> LAST_SOUND = new HashMap<>();

	static void init() {
		ClientEffects.register(SaberEffects.CLASH, SaberFx::onClash);
		ClientEffects.register(SaberEffects.DEFLECT, SaberFx::onDeflect);
		ClientEffects.register(SaberEffects.PARRY, SaberFx::onParry);
		ClientEffects.register(SaberEffects.GUARD_BREAK, SaberFx::onGuardBreak);
		ClientEffects.register(SaberEffects.HIT, SaberFx::onHit);
		ClientEffects.register(SaberEffects.WORKBENCH_ASSEMBLE, SaberFx::onAssemble);
		ClientEffects.register(SaberEffects.COMBO, SaberFx::onCombo);
		ClientEffects.register(SaberEffects.HEAVY, SaberFx::onHeavy);
		ClientEffects.register(SaberEffects.LUNGE, SaberFx::onLunge);
		ClientEffects.register(SaberEffects.FORM, SaberFx::onForm);
		ClientEffects.register(SaberEffects.THROW, SaberFx::onThrow);
		ClientEffects.register(SaberEffects.THROW_CATCH, SaberFx::onCatch);
		ClientEffects.register(SaberEffects.NPC_GUARD_BREAK, SaberFx::onGuardBreak);
		ClientEffects.register(SaberEffects.NPC_WINDUP, SaberFx::onNpcWindup);
		ClientEffects.register(SaberEffects.SPAR, SaberFx::onSpar);
		ClientEffects.register(SaberEffects.NPC_DEFLECT, SaberFx::onNpcDeflect);
		ClientEffects.register(SaberEffects.SABER_STAFF, SaberFx::onStaff);
		ClientEffects.register(SaberEffects.LENS_FIT, SaberFx::onLensFit);
	}

	/**
	 * The saberstaff (spec MILESTONE_4.md 5.6, 13): the second blade igniting or retracting, or the
	 * whirl, a full turn of both blades with the heavy strike's pose, its own sound, two streaks of
	 * blade light circling the player at the whirl's range, and dust thrown out around the feet.
	 */
	private static void onStaff(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		switch (p.variant()) {
			case SaberEffects.STAFF_SECOND_BLADE -> {
				boolean ignite = p.intensity() > 0.5f;
				play(level, p.pos(), ignite ? ModSounds.SABER_STAFF_IGNITE : ModSounds.SABER_RETRACT, 0.9f, ignite ? 1f : 1.15f);
			}
			case SaberEffects.STAFF_WHIRL -> {
				SaberAnimations.start(p.entityId(), SaberAnimations.Kind.HEAVY_STRIKE, SaberAnimations.HEAVY_STRIKE_TICKS);
				play(level, p.pos(), ModSounds.SABER_STAFF_WHIRL, 1.1f, 0.95f + RANDOM.nextFloat() * 0.1f);
				shakeIfSelf(mc, entity, 0.25f);

				if (!Vfx.inRange(p.pos())) {
					return;
				}

				int color = entity instanceof LivingEntity living ? saberColor(living) : 0xFFFFFF;
				double radius = Math.max(1.0, p.intensity() * 0.8);
				double y = p.pos().y + (entity != null ? entity.getBbHeight() * 0.55 : 1.0);
				int count = Vfx.count(14);
				double start = RANDOM.nextDouble() * Math.PI * 2;

				// Two blades, half a turn apart, each sweeping most of a circle.
				for (int blade = 0; blade < 2; blade++) {
					for (int i = 0; i < count; i++) {
						double a = start + blade * Math.PI + (i / (double) count) * Math.PI * 1.6;
						double r = radius * (0.55 + 0.45 * RANDOM.nextDouble());
						Vec3 at = new Vec3(p.pos().x + Math.cos(a) * r, y + (RANDOM.nextDouble() - 0.5) * 0.25, p.pos().z + Math.sin(a) * r);
						Vec3 tangent = new Vec3(-Math.sin(a), 0, Math.cos(a)).scale(0.3);
						SaberSparks.streak(at, tangent, color, 6 + RANDOM.nextInt(3));
					}
				}

				dust(level, p.pos(), Vfx.count(10), radius * 1.2);
			}
			default -> {
			}
		}
	}

	/** A lens seats in the workbench (5.7): its click and the socket's glint, for the fitter only. */
	private static void onLensFit(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		play(level, p.pos(), ModSounds.WORKBENCH_LENS_FIT, 0.9f, 1f + p.variant() * 0.06f);
		LightsaberWorkbenchScreen.onLensFit(level.getGameTime(), p.variant());
	}

	/** A combo step: on the finisher, the overhead chop and its sound. */
	private static void onCombo(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		if (p.variant() == 3) {
			SaberAnimations.start(p.entityId(), SaberAnimations.Kind.FINISHER, SaberAnimations.FINISHER_TICKS);
			play(level, p.pos(), ModSounds.SABER_FINISHER, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);
		}
	}

	/** Heavy attack: the drawn-back wind-up with its rising sound, then the sweep, or a cancel. */
	private static void onHeavy(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		switch (p.variant()) {
			case SaberEffects.HEAVY_WINDUP -> {
				SaberAnimations.startWindup(p.entityId(), SaberAnimations.Kind.HEAVY_WINDUP, Math.round(p.intensity()));
				play(level, p.pos(), ModSounds.SABER_HEAVY_WINDUP, 1f, 1f);

				if (entity instanceof LivingEntity living) {
					glint(living, saberColor(living), 0.9);
				}
			}
			case SaberEffects.HEAVY_STRIKE -> {
				SaberAnimations.start(p.entityId(), SaberAnimations.Kind.HEAVY_STRIKE, SaberAnimations.HEAVY_STRIKE_TICKS);
				play(level, p.pos(), ModSounds.SABER_HEAVY_STRIKE, 1.1f, 0.95f + RANDOM.nextFloat() * 0.1f);
				shakeIfSelf(mc, entity, 0.2f);

				if (entity != null && Vfx.inRange(p.pos())) {
					dust(level, entity.position(), Vfx.count(8), 0.5);
				}
			}
			default -> SaberAnimations.stop(p.entityId(), SaberAnimations.Kind.HEAVY_WINDUP);
		}
	}

	/** Lunge: a dust kick and a streak along the dash, then sparks on a hit. */
	private static void onLunge(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		switch (p.variant()) {
			case SaberEffects.LUNGE_START -> {
				SaberAnimations.start(p.entityId(), SaberAnimations.Kind.LUNGE, SaberAnimations.LUNGE_TICKS);
				play(level, p.pos(), ModSounds.SABER_LUNGE, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);

				if (Vfx.inRange(p.pos())) {
					dust(level, p.pos(), Vfx.count(10), 0.4);
					int color = entity instanceof LivingEntity living ? saberColor(living) : 0xFFFFFF;

					for (int i = 0, n = Vfx.count(8); i < n; i++) {
						Vec3 at = p.pos().add(p.dir().scale(i * 0.6)).add(0, 0.9 + RANDOM.nextDouble() * 0.3, 0);
						SaberSparks.streak(at, p.dir().scale(0.35), color, 6 + RANDOM.nextInt(3));
					}
				}
			}
			case SaberEffects.LUNGE_HIT -> {
				SaberAnimations.stop(p.entityId(), SaberAnimations.Kind.LUNGE);

				if (Vfx.inRange(p.pos()) && entity instanceof LivingEntity living) {
					sparks(p.pos(), p.dir().scale(-1), 0.6, Vfx.count(10), saberColor(living));
				}
			}
			default -> {
				SaberAnimations.stop(p.entityId(), SaberAnimations.Kind.LUNGE);

				if (Vfx.inRange(p.pos())) {
					dust(level, p.pos(), Vfx.count(6), 0.3);
				}
			}
		}
	}

	/** A form change: the salute for the length of the change, and its chime. */
	private static void onForm(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		SaberAnimations.start(p.entityId(), SaberAnimations.Kind.SALUTE, Math.max(8, Math.round(p.intensity())));
		play(level, p.pos(), ModSounds.SABER_FORM_CHANGE, 0.9f, 1f);
	}

	/** Saber Throw: the release, or the scorch where the blade struck a block. */
	private static void onThrow(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		if (p.variant() == SaberEffects.THROW_START) {
			SaberAnimations.start(p.entityId(), SaberAnimations.Kind.THROW, SaberAnimations.THROW_TICKS);
			play(level, p.pos(), ModSounds.SABER_THROW, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);
			return;
		}

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		int color = entity instanceof LivingEntity living ? ThrownSaberFx.colorOf(living) : 0xFFFFFF;
		sparks(p.pos(), p.dir(), 0.7, Vfx.count(12), color);

		for (int i = 0, n = Vfx.count(5); i < n; i++) {
			Vec3 v = p.dir().scale(0.03).add(0, 0.02, 0);
			level.addParticle(ParticleTypes.SMOKE, p.pos().x, p.pos().y, p.pos().z, v.x, v.y, v.z);
		}

		play(level, p.pos(), ModSounds.SABER_HIT, 0.7f, 1.3f);
	}

	private static void onCatch(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		SaberAnimations.start(p.entityId(), SaberAnimations.Kind.CATCH, SaberAnimations.CATCH_TICKS);
		play(level, p.pos(), ModSounds.SABER_THROW_CATCH, 1f, 1f);
	}

	/**
	 * A saber NPC's attack tell: the blade drawn back, a glint at the tip and, on the first swing of
	 * a string, the wind-up sound. Never reduced by the particle presets.
	 */
	private static void onNpcWindup(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		SaberAnimations.start(p.entityId(), SaberAnimations.Kind.NPC_WINDUP, Math.max(1, Math.round(p.intensity())));

		if (entity instanceof LivingEntity living) {
			glint(living, saberColor(living), 0.85);
		}

		if (p.variant() == SaberEffects.WINDUP_FIRST) {
			play(level, p.pos(), ModSounds.SABER_NPC_WINDUP, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);
		}
	}

	/** Spar start and end stings. */
	private static void onSpar(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		play(level, p.pos(), p.variant() == SaberEffects.SPAR_BEGIN ? ModSounds.STORY_DUEL_BEGIN : ModSounds.STORY_DUEL_END, 1f, 1f);
	}

	/** The blade colour of {@code entity}'s lit saber, white if it has none. */
	private static int saberColor(LivingEntity entity) {
		ItemStack lit = SaberAudio.litSaber(entity);
		return lit == null ? 0xFFFFFF : SaberComponents.config(lit).color().rgb();
	}

	/**
	 * The attack-tell glint at the drawn-back blade tip, a star of light that opens and closes as it moves with the entity.
	 * Drawn as light ({@link SaberSparks#glint}), which the particle presets never thin out.
	 */
	private static void glint(LivingEntity entity, int color, double height) {
		float bodyYaw = entity.yBodyRot * Mth.DEG_TO_RAD;
		float side = entity.getMainArm() == HumanoidArm.LEFT ? -1f : 1f;
		Vec3 right = new Vec3(-Mth.cos(bodyYaw), 0, -Mth.sin(bodyYaw)).scale(side);
		Vec3 back = entity.getLookAngle().multiply(1, 0, 1).scale(-0.35);
		Vec3 tip = new Vec3(0, entity.getBbHeight() * height + 0.6, 0).add(right.scale(0.45)).add(back);
		SaberSparks.glint(Anchor.body(entity.getId(), 0.5f, 0f, 0.5f).offset(tip), color, 8, 0.3f);
	}

	/** A kick of dust around the feet at {@code pos}. */
	private static void dust(ClientLevel level, Vec3 pos, int count, double spread) {
		for (int i = 0; i < count; i++) {
			double dx = (RANDOM.nextDouble() - 0.5) * spread;
			double dz = (RANDOM.nextDouble() - 0.5) * spread;
			level.addParticle(ParticleTypes.POOF, pos.x + dx, pos.y + 0.1, pos.z + dz, dx * 0.1, 0.02, dz * 0.1);
		}
	}

	private static void onClash(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int color = BladeColor.byIndex(p.variant()).rgb();
		Vec3 normal = p.dir().scale(-1);

		if (Vfx.inRange(p.pos())) {
			sparks(p.pos(), normal, 0.35, Vfx.count(14), color);
			flash(p.pos(), color);
		}

		limitedSound(level, p, ModSounds.SABER_CLASH, 1f, 0.9f + RANDOM.nextFloat() * 0.2f);
		shakeIfSelf(mc, entity, 0.15f);
	}

	private static void onDeflect(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int color = BladeColor.byIndex(p.variant()).rgb();

		if (Vfx.inRange(p.pos())) {
			sparks(p.pos(), p.dir(), 0.5, Vfx.count(12), color);
			flash(p.pos(), color);
		}

		limitedSound(level, p, ModSounds.SABER_DEFLECT, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);
	}

	/**
	 * A guarding saber NPC sends a bolt back (spec 10.7, 13.3): a quick blade sweep (the swing, which
	 * leaves its trail), sparks and the contact flash at the blade, and the M1 deflect sound.
	 */
	private static void onNpcDeflect(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int color = BladeColor.byIndex(p.variant()).rgb();

		if (entity instanceof LivingEntity living && SaberAudio.litSaber(living) != null) {
			// Client-side swing animation only: on a client level it is not sent anywhere.
			living.swing(InteractionHand.MAIN_HAND);
		}

		if (Vfx.inRange(p.pos())) {
			sparks(p.pos(), p.dir(), 0.5, Vfx.count(12), color);
			flash(p.pos(), color);
		}

		limitedSound(level, p, ModSounds.SABER_DEFLECT, 1f, 0.95f + RANDOM.nextFloat() * 0.1f);
	}

	private static void onParry(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int color = BladeColor.byIndex(p.variant()).rgb();

		if (Vfx.inRange(p.pos())) {
			sparks(p.pos(), p.dir(), 0.6, Vfx.count(20), color);
			flash(p.pos(), color);
		}

		play(level, p.pos(), ModSounds.SABER_PARRY, 1f, 1f);
		shakeIfSelf(mc, entity, 0.1f);
	}

	private static void onGuardBreak(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int color = BladeColor.byIndex(p.variant()).rgb();
		Vec3 front = p.pos().add(p.dir().scale(0.6));

		if (Vfx.inRange(front)) {
			sparks(front, new Vec3(0, 1, 0), 1.0, Vfx.count(26), color);
			flash(front, color);
		}

		play(level, p.pos(), ModSounds.SABER_GUARD_BREAK, 1.1f, 1f);
		shakeIfSelf(mc, entity, 0.35f);
	}

	private static void onHit(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int color = BladeColor.byIndex(p.variant()).rgb();

		if (Vfx.inRange(p.pos())) {
			sparks(p.pos(), p.dir().scale(-1), 0.5, Vfx.count(8), color);
		}

		play(level, p.pos(), ModSounds.SABER_HIT, 0.9f, 0.95f + RANDOM.nextFloat() * 0.1f);
	}

	/** Assembly at the workbench: the assemble sting and a burst of crystal glints above the bench. */
	private static void onAssemble(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		play(level, p.pos(), ModSounds.WORKBENCH_ASSEMBLE, 1f, 1f);

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		ParticleOptions glint = crystalGlint();

		for (int i = 0, n = Vfx.count(16); i < n; i++) {
			Vec3 v = randomDirection().scale(0.04 + RANDOM.nextDouble() * 0.05).add(0, 0.03, 0);
			level.addParticle(glint, p.pos().x, p.pos().y, p.pos().z, v.x, v.y, v.z);
		}

		sparks(p.pos(), new Vec3(0, 1, 0), 0.9, Vfx.count(10), BladeColor.byIndex(p.variant()).rgb());
	}

	/** The world feature's {@code crystal_glint}, or the end rod sparkle if it is missing. */
	private static ParticleOptions crystalGlint() {
		return BuiltInRegistries.PARTICLE_TYPE.getValue(OldRepublicMod.id("crystal_glint")) instanceof ParticleOptions options
				? options
				: ParticleTypes.END_ROD;
	}

	/** Sparks in a cone of {@code spread} around {@code dir} ({@link SaberSparks}). */
	private static void sparks(Vec3 pos, Vec3 dir, double spread, int count, int color) {
		SaberSparks.sparks(pos, dir, spread, count, color, 0.25);
	}

	/** The contact flash, or a soft glow when flashing effects are disabled ({@link SaberSparks}). */
	private static void flash(Vec3 pos, int color) {
		SaberSparks.flash(pos, color);
	}

	private static Vec3 randomDirection() {
		return new Vec3(RANDOM.nextDouble() * 2 - 1, RANDOM.nextDouble() * 2 - 1, RANDOM.nextDouble() * 2 - 1);
	}

	private static void shakeIfSelf(Minecraft mc, @Nullable Entity entity, float intensity) {
		if (entity != null && entity == mc.player) {
			CameraShake.add(intensity);
		}
	}

	private static void limitedSound(ClientLevel level, EffectEventPayload p, SoundEvent sound, float volume, float pitch) {
		long now = level.getGameTime();
		Long last = LAST_SOUND.get(p.entityId());

		if (last != null && now - last < SOUND_GAP && now >= last) {
			return;
		}

		LAST_SOUND.put(p.entityId(), now);
		play(level, p.pos(), sound, volume, pitch);
	}

	private static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	/** Forgets rate-limit state (disconnect). */
	static void clear() {
		LAST_SOUND.clear();
	}

	private SaberFx() {
	}
}
