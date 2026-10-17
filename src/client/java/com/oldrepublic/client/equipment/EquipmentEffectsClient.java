package com.oldrepublic.client.equipment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.enemy.EnemyParticleProviders;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.enemy.EnemyParticles;
import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.equipment.EquipmentEffects;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The client side of the {@code armour_set} effect (spec 13.3): a set was just completed, sent to
 * the wearer only. It plays {@code armour.set_bonus} (pitched per set), shows the
 * {@link SetBonusToast}, and runs a short flourish at the shoulders that follows the wearer:
 * <ul>
 * <li>trooper sets: the shoulder clasps catch the light (a soft glow in the army colour and two
 * steel glints per clasp), then glints run down the chest straps toward the belt over six ticks, as
 * the plates settle;</li>
 * <li>robes: a soft cloth puff at each shoulder as the robe settles, then a few loose fibres drift
 * down from the hood's edge over six ticks.</li>
 * </ul>
 * In first person the flourish is moved a little forward so the wearer sees it at the bottom of the
 * view. With flashing effects off the clasp glow is a slow steady one. About 12 particles in all,
 * through {@link Vfx#count}.
 */
public final class EquipmentEffectsClient {
	/** Ticks the settle phase runs for; a step every {@link #STEP} ticks. */
	private static final int SETTLE_TICKS = 6;
	private static final int STEP = 2;
	/** Shoulder height below the eyes, and the half-width between the shoulders. */
	private static final double SHOULDER_DROP = 0.32;
	private static final double SHOULDER_HALF = 0.34;
	/** How far down the chest the strap glints and fibres travel. */
	private static final double SETTLE_DROP = 0.55;

	private static final class Flourish {
		final int entityId;
		final ArmourSet set;
		int age;

		Flourish(int entityId, ArmourSet set) {
			this.entityId = entityId;
			this.set = set;
		}
	}

	private static final List<Flourish> ACTIVE = new ArrayList<>();
	private static final RandomSource RANDOM = RandomSource.create();

	static void init() {
		ClientEffects.register(EquipmentEffects.ARMOUR_SET, EquipmentEffectsClient::onArmourSet);
		ClientTickEvents.END_CLIENT_TICK.register(EquipmentEffectsClient::tick);
	}

	private static void onArmourSet(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		ArmourSet set = ArmourSet.byIndex(payload.variant());
		QuestSounds.ui(ModSounds.ARMOUR_SET_BONUS, pitch(set), 0.9f);
		SetBonusToast.show(set);

		if (entity instanceof LivingEntity wearer && Vfx.inRange(wearer.position())) {
			burst(client, wearer, set);
			ACTIVE.add(new Flourish(wearer.getId(), set));
		}
	}

	/** Trooper plates sound a little lower for the Empire; Sith robes deeper than Jedi ones. */
	static float pitch(ArmourSet set) {
		return switch (set) {
			case REPUBLIC_TROOPER -> 1.0f;
			case SITH_TROOPER -> 0.9f;
			case JEDI_ROBES -> 1.06f;
			case SITH_ROBES -> 0.88f;
		};
	}

	private static boolean trooper(ArmourSet set) {
		return set == ArmourSet.REPUBLIC_TROOPER || set == ArmourSet.SITH_TROOPER;
	}

	/** The army colour of the clasp glow. */
	private static int claspColour(ArmourSet set) {
		return set == ArmourSet.REPUBLIC_TROOPER ? 0x9CCBFF : 0xF0606A;
	}

	/** Polished steel on Republic plates, a warmer blackened steel on Imperial ones. */
	private static int glintColour(ArmourSet set) {
		return set == ArmourSet.REPUBLIC_TROOPER ? 0xEEF4FF : 0xFFE2D6;
	}

	/** Undyed cream wool for the Jedi, dark crimson for the Sith. */
	private static int clothColour(ArmourSet set) {
		return set == ArmourSet.JEDI_ROBES ? 0xD8C8A0 : 0x5A1822;
	}

	/** Loose fibres catch a little light, so they are a shade brighter than the cloth. */
	private static int fibreColour(ArmourSet set) {
		return set == ArmourSet.JEDI_ROBES ? 0xEADDBC : 0x9A2E3C;
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;

		if (level == null) {
			ACTIVE.clear();
			return;
		}

		if (client.isPaused()) {
			return;
		}

		for (Iterator<Flourish> it = ACTIVE.iterator(); it.hasNext();) {
			Flourish flourish = it.next();
			flourish.age++;

			if (!(level.getEntity(flourish.entityId) instanceof LivingEntity wearer) || wearer.isRemoved() || flourish.age > SETTLE_TICKS) {
				it.remove();
				continue;
			}

			if (flourish.age % STEP == 0) {
				settle(client, wearer, flourish.set, flourish.age / (float) SETTLE_TICKS);
			}
		}
	}

	// ---------------------------------------------------------------- the first beat

	/** The moment the set completes: clasps catch the light, or the robe settles on the shoulders. */
	private static void burst(Minecraft client, LivingEntity wearer, ArmourSet set) {
		for (int side = -1; side <= 1; side += 2) {
			Vec3 shoulder = shoulder(client, wearer, side);
			Vec3 out = right(wearer).scale(side * 0.02);

			if (trooper(set)) {
				// A soft glow in the army colour where the pauldron clasps.
				EnemyParticleProviders.EnemyParticle glow = EnemyParticleProviders.spawn(EnemyParticles.FLARE_EMBER, shoulder, Vec3.ZERO);

				if (glow != null) {
					glow.tint(claspColour(set)).size(0.1f).gravity(0f).bright();

					if (Vfx.flashing()) {
						glow.life(6).alpha(0.7f);
					} else {
						glow.life(14).alpha(0.35f);
					}
				}

				for (int i = 0, n = Vfx.count(2); i < n; i++) {
					Vec3 at = shoulder.add(jitter(0.06), jitter(0.05), jitter(0.06));
					Vec3 velocity = out.add(jitter(0.01), 0.012 + RANDOM.nextDouble() * 0.01, jitter(0.01));
					spawn(EnemyParticles.FLARE_EMBER, at, velocity, glintColour(set), 0.035f, 7 + RANDOM.nextInt(4), 0f);
				}
			} else {
				DustParticleOptions puff = new DustParticleOptions(clothColour(set), 0.7f);

				for (int i = 0, n = Vfx.count(2); i < n; i++) {
					Vec3 at = shoulder.add(jitter(0.1), jitter(0.06), jitter(0.1));
					Vec3 velocity = out.add(0, -0.01, 0);
					wearer.level().addParticle(puff, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the settle

	/**
	 * One step of the settle, {@code progress} 0 to 1: strap glints run from each shoulder toward
	 * the belt buckle (trooper sets), or fibres drift loose from the hood's edge (robes).
	 */
	private static void settle(Minecraft client, LivingEntity wearer, ArmourSet set, float progress) {
		// One per shoulder, left first; a thin budget keeps only the left one.
		for (int i = 0, n = Vfx.count(2); i < n; i++) {
			int side = i % 2 == 0 ? -1 : 1;
			Vec3 shoulder = shoulder(client, wearer, side);

			if (trooper(set)) {
				// The straps cross the chest: inward and down from each shoulder.
				Vec3 inward = right(wearer).scale(-side * SHOULDER_HALF * 0.8 * progress);
				Vec3 at = shoulder.add(inward).add(0, -SETTLE_DROP * progress, 0).add(forward(wearer).scale(0.18));
				spawn(EnemyParticles.FLARE_EMBER, at, new Vec3(0, -0.004, 0), glintColour(set), 0.03f, 6 + RANDOM.nextInt(3), 0f);
			} else {
				Vec3 at = shoulder.add(jitter(0.12), 0.08 + jitter(0.04), jitter(0.12));
				Vec3 drift = new Vec3(jitter(0.015), -0.01, jitter(0.015));
				spawn(EnemyParticles.FLARE_EMBER, at, drift, fibreColour(set), 0.022f, 22 + RANDOM.nextInt(10), 0.012f);
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	private static void spawn(SimpleParticleType type, Vec3 pos, Vec3 velocity, int tint, float size, int life, float gravity) {
		EnemyParticleProviders.EnemyParticle p = EnemyParticleProviders.spawn(type, pos, velocity);

		if (p != null) {
			p.tint(tint).size(size).life(life).gravity(gravity);
		}
	}

	/**
	 * The point on the wearer's shoulder on {@code side} (-1 left, 1 right). For the local player in
	 * first person it is moved forward into the bottom of the view.
	 */
	private static Vec3 shoulder(Minecraft client, LivingEntity wearer, int side) {
		Vec3 base = wearer.getEyePosition().add(0, -SHOULDER_DROP, 0).add(right(wearer).scale(side * SHOULDER_HALF));

		if (wearer == client.getCameraEntity() && client.options.getCameraType().isFirstPerson()) {
			return base.add(forward(wearer).scale(0.45)).add(0, -0.08, 0);
		}

		return base;
	}

	/** The wearer's right, from the body yaw. */
	private static Vec3 right(LivingEntity wearer) {
		float yaw = wearer.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
	}

	private static Vec3 forward(LivingEntity wearer) {
		float yaw = wearer.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
	}

	private static double jitter(double spread) {
		return (RANDOM.nextDouble() - 0.5) * 2 * spread;
	}

	private EquipmentEffectsClient() {
	}
}
