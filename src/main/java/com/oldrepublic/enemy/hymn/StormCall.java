package com.oldrepublic.enemy.hymn;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceReactions;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The called lightning strike and the shock (spec MILESTONE_5.md 12.1 to 12.3), shared by the
 * storm-callers, Ulvaine's rods, the First Cantor's basalt verse and the stormback's roar. The bolt
 * itself is vanilla's, made cosmetic ({@code setVisualOnly}): its flash is the only screen flash,
 * so the storm-flash setting dims it (15), and the damage is the mod's own.
 */
public final class StormCall {
	/** A strike reaches this far up and down from its patch. */
	private static final double REACH_UP = 3.0;
	private static final double REACH_DOWN = 1.5;

	/**
	 * A shock (the M3 Lightning rule): Slowness I for the diminished length of a {@code shock}
	 * control, and any wind-up broken. Returns the ticks applied (0 when diminished away).
	 */
	public static int shock(@Nullable Entity source, LivingEntity target, int ticks) {
		int duration = ControlEffects.diminished(target, ticks, ControlEffects.SHOCK);

		if (duration > 0) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 0), source);
			ForceReactions.interruptWindup(target, null, ForceReactions.CAUSE_SHOCK);
		}

		return duration;
	}

	/** A cosmetic bolt at {@code at}: vanilla's flash, thunder and bolt, no fire and no damage. */
	public static void bolt(ServerLevel level, Vec3 at) {
		// ServerLevel#tickThunder's own call (EntityTypes.LIGHTNING_BOLT, verified in the bytecode).
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);

		if (bolt == null) {
			return;
		}

		bolt.snapTo(at.x, at.y, at.z);
		bolt.setVisualOnly(true);
		level.addFreshEntity(bolt);
	}

	/**
	 * A strike on the patch at {@code at}: the cosmetic bolt, then {@code damage} of type
	 * {@code storm_call} and a {@code shock} of {@code shockTicks} to every living thing within
	 * {@code radius} that {@code hits} accepts. Creative and spectator players are never hit (by game
	 * mode: a test's mock player answers {@code isCreative()} true). Returns those struck.
	 */
	public static List<LivingEntity> strike(ServerLevel level, Vec3 at, double radius, float damage, int shockTicks, @Nullable Entity cause,
			Predicate<LivingEntity> hits) {
		bolt(level, at);
		return strikeUnderRoof(level, at, radius, damage, shockTicks, cause, hits);
	}

	/**
	 * {@link #strike} without vanilla's bolt, for a strike under a roof where a bolt from the sky would
	 * pass through the stone (the First Cantor's basalt marks): the caller's own effect event draws it.
	 */
	public static List<LivingEntity> strikeUnderRoof(ServerLevel level, Vec3 at, double radius, float damage, int shockTicks, @Nullable Entity cause,
			Predicate<LivingEntity> hits) {
		DamageSource source = level.damageSources().source(HymnDamage.STORM_CALL, null, cause);
		AABB box = new AABB(at.x - radius, at.y - REACH_DOWN, at.z - radius, at.x + radius, at.y + REACH_UP, at.z + radius);
		List<LivingEntity> struck = new ArrayList<>();

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !ignored(e) && hits.test(e))) {
			double dx = target.getX() - at.x;
			double dz = target.getZ() - at.z;

			if (dx * dx + dz * dz > radius * radius) {
				continue;
			}

			if (target.hurtServer(level, source, damage)) {
				shock(cause, target, shockTicks);
				struck.add(target);
			}
		}

		return struck;
	}

	/** Creative and spectator players, by game mode. */
	public static boolean ignored(LivingEntity entity) {
		return entity instanceof Player player && (player.gameMode() == GameType.CREATIVE || player.isSpectator());
	}

	private StormCall() {
	}
}
