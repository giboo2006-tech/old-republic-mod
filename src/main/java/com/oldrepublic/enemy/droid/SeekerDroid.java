package com.oldrepublic.enemy.droid;

import com.oldrepublic.force.choke.ChokeImmune;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * The seeker droid (spec MILESTONE_4.md 10.7): a small flying probe that only exists for the Chapter
 * Four opening, where W2's seeker scene flies it ({@code SeekerScene}). It has 4 health but is
 * invulnerable, never saved, never hostile and has no AI of its own. One left behind without its
 * scene's tag (a scene that ended badly) shuts down after a short while.
 */
public class SeekerDroid extends PathfinderMob implements ChokeImmune {
	/** W2's scene tag ({@code SeekerScene.SEEKER_TAG}). */
	private static final String SCENE_TAG = "oldrepublic.c4_seeker";
	/** Ticks a droid outside a scene lingers before it leaves. */
	private static final int STRAY_TICKS = 200;

	private int strayTicks;

	public SeekerDroid(EntityType<? extends SeekerDroid> type, Level level) {
		super(type, level);
		setInvulnerable(true);
		setNoGravity(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.MOVEMENT_SPEED, 0.25);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		strayTicks = entityTags().contains(SCENE_TAG) ? 0 : strayTicks + 1;

		if (strayTicks > STRAY_TICKS) {
			discard();
		}
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ModSounds.SEEKER_FLY;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return ModSounds.SEEKER_CHIRP;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}
}
