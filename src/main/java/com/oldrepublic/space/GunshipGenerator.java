package com.oldrepublic.space;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.network.EffectEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The blockade gunship's shield generator (spec MILESTONE_5.md 5.5): a separate small target fixed
 * to the gunship's spine, which places it every tick. While it stands the gunship takes no damage
 * and keeps launching interceptors; destroying it first is the battle's lesson (16.2). Only ship
 * weapons hurt it, and not during the Peaceful pass. A generator whose gunship is gone removes
 * itself.
 */
public class GunshipGenerator extends Mob {
	/** Ticks a generator waits for a missing gunship before it removes itself. */
	private static final int ORPHAN_TICKS = 40;

	private @Nullable UUID gunship;
	private int orphanTicks;

	public GunshipGenerator(EntityType<? extends GunshipGenerator> type, Level level) {
		super(type, level);
		setNoGravity(true);
		this.noPhysics = true;
		this.xpReward = 0;
		this.lookControl = new Starfighter.FixedLook(this);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 80.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	/**
	 * Fixes the generator to {@code owner} with {@code hull} health, or {@code health} of it when that
	 * is positive (a generator coming back with its chunk). Called before it is added.
	 */
	void attach(BlockadeGunship owner, float hull, float health) {
		gunship = owner.getUUID();
		AttributeInstance max = getAttribute(Attributes.MAX_HEALTH);

		if (max != null) {
			max.setBaseValue(Math.max(1f, hull));
		}

		setHealth(health > 0f ? Math.min(health, getMaxHealth()) : getMaxHealth());
	}

	/** The gunship it shields, if it is loaded and alive. Server side. */
	public @Nullable BlockadeGunship gunship(ServerLevel level) {
		return gunship != null && level.getEntity(gunship) instanceof BlockadeGunship g && g.isAlive() ? g : null;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if (gunship(level) != null) {
			orphanTicks = 0;
		} else if (++orphanTicks > ORPHAN_TICKS) {
			discard();
		}
	}

	/** The gunship moves it. */
	@Override
	public void travel(Vec3 input) {
	}

	/** Ship weapons only, with no hurt cooldown; during the Peaceful pass its gunship's shield covers it too. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}

		if (!source.is(SpaceEntities.SHIP_WEAPON) || isDeadOrDying()) {
			return false;
		}

		Entity direct = source.getDirectEntity();
		Vec3 at = direct != null ? direct.position() : getBoundingBox().getCenter();
		Vec3 normal = at.subtract(getBoundingBox().getCenter());
		Vec3 dir = normal.lengthSqr() > 1.0e-6 ? normal.normalize() : null;
		BlockadeGunship owner = gunship(level);

		if (owner != null && owner.passive()) {
			EffectEvents.around(level, SpaceEffects.SHIP_HIT, at, dir, getId(), amount, SpaceEffects.HIT_SHIELD);
			return false;
		}

		EffectEvents.around(level, SpaceEffects.SHIP_HIT, at, dir, getId(), amount, SpaceEffects.HIT_HULL);
		boolean hurt = super.hurtServer(level, source, amount);
		this.invulnerableTime = 0;
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		boolean first = !this.dead;
		super.die(source);

		if (first && level() instanceof ServerLevel level) {
			EffectEvents.around(level, SpaceEffects.SHIP_DESTROYED, getBoundingBox().getCenter(), null, getId(), 1f, SpaceEffects.DESTROYED_GENERATOR);
			BlockadeGunship owner = gunship(level);

			if (owner != null) {
				owner.onGeneratorDestroyed(level);
			}

			SpaceRewards.onDestroyed(level, this, SpaceRules.Kind.GENERATOR, source);
		}
	}

	/** Gone at once: the blast is the client's ({@code ship_destroyed}). */
	@Override
	protected void tickDeath() {
		if (!level().isClientSide() && !isRemoved()) {
			discard();
		}
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return null;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return null;
	}
}
