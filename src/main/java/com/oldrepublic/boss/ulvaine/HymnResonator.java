package com.oldrepublic.boss.ulvaine;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Aligned;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * A resonator of Ulvaine's Crescendo (spec MILESTONE_5.md 12.2): a crystal spire that stands at one
 * of the Heart's {@code RESONATOR_1..4} spots while the Crescendo runs, with {@code resonatorHealth}
 * health and a steady inner light. It never moves or turns, and only the fight's participants can
 * break it; its session spawns it, relights it and clears it. Never saved.
 */
public class HymnResonator extends Mob implements Aligned {
	/** A stray copy without a session leaves after this long. */
	private static final int STRAY_TICKS = 20;

	private @Nullable UlvaineSession session;
	/** Which of the four spots it stands on. */
	private int spot;

	public HymnResonator(EntityType<? extends HymnResonator> type, Level level) {
		super(type, level);
		this.xpReward = 0;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, new UlvaineBalance().resonatorHealth)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	/** Joins it to the fight that lit it, standing on spot {@code spot} (server side; never saved). */
	void bind(UlvaineSession session, int spot) {
		this.session = session;
		this.spot = spot;
	}

	public int spot() {
		return spot;
	}

	@Override
	public Allegiance allegiance() {
		return Allegiance.HYMN;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);

		if ((session == null || !session.ownsResonator(this)) && (session != null || tickCount > STRAY_TICKS)) {
			discard();
		}
	}

	/** Only the participants' blows (and {@code /kill}) reach it. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}

		return session != null && session.acceptsResonatorDamage(source) && super.hurtServer(level, source, amount);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);

		if (session != null && !level().isClientSide()) {
			session.onResonatorBroken(this);
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	/** Its fight owns its lifetime. */
	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	/** A struck spire rings. */
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.BOSS_ULVAINE_RESONATOR;
	}

	/** The break is the session's ring and shards. */
	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return null;
	}
}
