package com.oldrepublic.saber.throwing;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.ForceReactiveEntity;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.SaberInteractions;
import com.oldrepublic.saber.SaberStaffs;
import com.oldrepublic.saber.SaberToggle;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown lightsaber in flight (spec section 8.3). It is never an item: it carries a render copy
 * of the saber, flies out along the look until its range or a block, then homes back to the
 * thrower, hitting each living target once (at most {@code maxTargets}). A guard that faces it
 * blocks it and sends it straight back, and so does a {@link ForceReactiveEntity} that takes the
 * hit (spec 10.6). Not saved ({@code noSave}); {@link SaberThrow} clears the inventory stack's
 * {@code saber_thrown} component whenever it ends.
 */
public class ThrownSaber extends Entity {
	/** The saber as rendered (a copy without the thrown component). */
	private static final EntityDataAccessor<ItemStack> DATA_SABER = SynchedEntityData.defineId(ThrownSaber.class, EntityDataSerializers.ITEM_STACK);
	/** Entity id of the thrower, for the client's catch animation; -1 if unknown. */
	private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(ThrownSaber.class, EntityDataSerializers.INT);
	private static final double HIT_INFLATE = 0.35;

	/** Branch of the throw. */
	enum Shape {
		STANDARD,
		RICOCHET,
		RENDING
	}

	private @Nullable UUID throwId;
	private @Nullable UUID ownerId;
	private Vec3 origin = Vec3.ZERO;
	private Shape shape = Shape.STANDARD;
	private double range;
	private double returnSpeed;
	private float damage;
	private int maxTargets;
	/** How far the blade's sweep reaches beyond its box: wider for a saberstaff (spec MILESTONE_4.md 5.6). */
	private double hitInflate = HIT_INFLATE;
	private int maxTicks;
	private boolean returning;
	private int age;
	private int hits;
	private int hostileHits;
	private int bounces;
	private @Nullable LivingEntity bounceTarget;
	private final Set<UUID> struck = new HashSet<>();

	public ThrownSaber(EntityType<? extends ThrownSaber> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	/** A throw by {@code owner} of {@code saber}, flying along the look. Add it to the level afterwards. */
	static ThrownSaber create(ServerPlayer owner, ItemStack saber, UUID throwId, Shape shape, double speed, double range, float damage) {
		SaberThrowBalance b = SaberThrowBalance.get();
		ThrownSaber entity = new ThrownSaber(SaberThrow.THROWN_SABER, owner.level());
		ItemStack copy = saber.copy();
		copy.remove(SaberComponents.SABER_THROWN);
		entity.getEntityData().set(DATA_SABER, copy);
		entity.getEntityData().set(DATA_OWNER, owner.getId());
		entity.throwId = throwId;
		entity.ownerId = owner.getUUID();
		entity.shape = shape;
		entity.range = range;
		entity.returnSpeed = b.returnSpeed;
		entity.damage = damage;
		entity.maxTargets = b.maxTargets;
		entity.maxTicks = b.maxTicks;

		if (SaberStaffs.isStaff(saber)) {
			// The staff spins out on a wider, slower arc and cuts through more targets (5.6).
			SaberBalance sb = SaberToggle.balance();
			speed *= sb.staffThrowSpeedMult;
			entity.returnSpeed *= sb.staffThrowSpeedMult;
			entity.maxTargets = Math.max(b.maxTargets, sb.staffThrowTargets);
			entity.hitInflate = HIT_INFLATE + sb.staffThrowWidth;
		}

		Vec3 start = owner.getEyePosition().subtract(0, 0.3, 0).add(owner.getLookAngle().scale(0.6));
		entity.setPos(start);
		entity.origin = start;
		entity.setDeltaMovement(owner.getLookAngle().scale(speed));
		return entity;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_SABER, ItemStack.EMPTY);
		builder.define(DATA_OWNER, -1);
	}

	/** The rendered saber. */
	public ItemStack saber() {
		return this.entityData.get(DATA_SABER);
	}

	/** Entity id of the thrower, or -1. */
	public int ownerEntityId() {
		return this.entityData.get(DATA_OWNER);
	}

	@Nullable UUID throwId() {
		return throwId;
	}

	@Nullable UUID ownerId() {
		return ownerId;
	}

	int hostileHits() {
		return hostileHits;
	}

	public boolean isReturning() {
		return returning;
	}

	@Override
	public void tick() {
		super.tick();

		if (level() instanceof ServerLevel level && !isRemoved()) {
			serverTick(level);
		}
	}

	private void serverTick(ServerLevel level) {
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);

		if (owner == null || !owner.isAlive() || owner.level() != level || throwId == null) {
			SaberThrow.end(this, owner, false);
			return;
		}

		if (++age >= maxTicks) {
			SaberThrow.end(this, owner, true);
			return;
		}

		Vec3 pos = position();
		Vec3 motion;

		if (!returning) {
			motion = outwardMotion();
			BlockHitResult block = level.clip(new ClipContext(pos, pos.add(motion), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));

			if (block.getType() != HitResult.Type.MISS) {
				setPos(block.getLocation());
				Vec3 normal = new Vec3(block.getDirection().getStepX(), block.getDirection().getStepY(), block.getDirection().getStepZ());
				EffectEvents.around(level, SaberEffects.THROW, block.getLocation(), normal, owner.getId(), 1f, SaberEffects.THROW_SCORCH);
				startReturn();
				return;
			}

			hitAlong(level, owner, pos, pos.add(motion));
			setPos(pos.add(motion));

			if (position().distanceTo(origin) >= range) {
				startReturn();
			}
		} else {
			Vec3 hand = owner.getEyePosition().subtract(0, 0.35, 0);
			Vec3 to = hand.subtract(pos);
			double distance = to.length();

			if (distance <= returnSpeed + 0.6) {
				SaberThrow.end(this, owner, true);
				return;
			}

			motion = to.scale(returnSpeed / distance);
			hitAlong(level, owner, pos, pos.add(motion));
			setPos(pos.add(motion));
		}

		setDeltaMovement(motion);
	}

	/** Straight out, or toward the next ricochet target. */
	private Vec3 outwardMotion() {
		Vec3 motion = getDeltaMovement();

		if (bounceTarget != null && bounceTarget.isAlive()) {
			Vec3 to = bounceTarget.getBoundingBox().getCenter().subtract(position());

			if (to.lengthSqr() > 1.0e-4) {
				return to.normalize().scale(Math.max(0.5, motion.length()));
			}
		}

		return motion;
	}

	private void startReturn() {
		returning = true;
		bounceTarget = null;
	}

	/** Hits the reactive and living entities the blade passes through between {@code from} and {@code to}. */
	private void hitAlong(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to) {
		if (hits >= maxTargets) {
			return;
		}

		AABB sweep = getBoundingBox().expandTowards(to.subtract(from)).inflate(hitInflate);

		if (touchReactive(level, owner, from, to, sweep)) {
			return;
		}

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, sweep,
				e -> e != owner && e.isAlive() && !e.isSpectator() && !struck.contains(e.getUUID()))) {
			AABB box = target.getBoundingBox().inflate(hitInflate);

			if (!box.contains(from) && box.clip(from, to).isEmpty()) {
				continue;
			}

			struck.add(target.getUUID());

			if (SaberInteractions.isGuardingAgainst(target, from)) {
				blocked(level, target);
				return;
			}

			strike(level, owner, target);

			if (hits >= maxTargets) {
				startReturn();
				return;
			}

			if (shape == Shape.RICOCHET) {
				ricochet(level, owner, target);
				return;
			}
		}
	}

	/**
	 * Offers the hit to the first non-living {@link ForceReactiveEntity} the blade passes through.
	 * Returns true when one took it: that counts as a hit and the saber turns back.
	 */
	private boolean touchReactive(ServerLevel level, ServerPlayer owner, Vec3 from, Vec3 to, AABB sweep) {
		for (Entity entity : level.getEntities(this, sweep, e -> e instanceof ForceReactiveEntity && !(e instanceof LivingEntity)
				&& e.isAlive() && !e.isSpectator() && !struck.contains(e.getUUID()))) {
			AABB box = entity.getBoundingBox().inflate(hitInflate);

			if (!box.contains(from) && box.clip(from, to).isEmpty()) {
				continue;
			}

			struck.add(entity.getUUID());

			if (((ForceReactiveEntity) entity).onSaberThrowHit(owner, hitDamage())) {
				hits++;
				startReturn();
				return true;
			}
		}

		return false;
	}

	/** Damage of the next hit: the throw's damage after the ricochet falloff. */
	private float hitDamage() {
		return damage * Math.max(0f, 1f - SaberThrowBalance.get().ricochetFalloff * bounces);
	}

	private void blocked(ServerLevel level, LivingEntity target) {
		SaberThrowBalance b = SaberThrowBalance.get();
		SaberInteractions.drainGuard(target, target instanceof Player ? b.blockDrainPlayer : b.blockDrainNpc);
		Vec3 contact = target.getBoundingBox().getCenter().lerp(position(), 0.5);
		int colour = SaberComponents.config(saber()).color().ordinal();
		EffectEvents.toTracking(target, SaberEffects.CLASH, contact, getDeltaMovement().normalize().reverse(), target.getId(), 1f, colour);
		startReturn();
	}

	private void strike(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		SaberThrowBalance b = SaberThrowBalance.get();
		float amount = hitDamage();
		// Judged before the hit lands, so a bystander the hit provokes earns no credit.
		boolean hostile = ForcePowers.isHostile(owner, target);
		DamageSource source = level.damageSources().source(SaberThrow.DAMAGE, this, owner);
		boolean hurt = SaberCombat.strike(target, level, source, amount, SaberCombat.Strike.thrown());
		hits++;

		if (!hurt) {
			return;
		}

		Vec3 hit = target.getBoundingBox().getCenter().lerp(position(), 0.4);
		int colour = SaberComponents.config(saber()).color().ordinal();
		EffectEvents.toTracking(target, SaberEffects.HIT, hit, getDeltaMovement().normalize(), target.getId(), amount, colour);
		QuestHooks.onAbilityHit(owner, SaberThrow.ID, target);

		if (hostile) {
			hostileHits++;
		}

		// Entity#is(TagKey) checks the c:bosses convention tag (EntityType has no is(TagKey) in 26.2).
		if (shape == Shape.RENDING && !target.is(ConventionalEntityTypeTags.BOSSES)) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, b.rendingStaggerTicks, 3));
			ForceReactions.interruptWindup(target, owner, ForceReactions.CAUSE_SABER_THROW);
		}
	}

	/** Turns toward the nearest other hostile within {@code ricochetRange}, or comes back. */
	private void ricochet(ServerLevel level, ServerPlayer owner, LivingEntity from) {
		SaberThrowBalance b = SaberThrowBalance.get();

		if (bounces >= b.ricochetBounces) {
			startReturn();
			return;
		}

		LivingEntity next = null;
		double best = b.ricochetRange * b.ricochetRange;

		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(b.ricochetRange),
				e -> e != owner && e.isAlive() && !struck.contains(e.getUUID()) && ForcePowers.isHostile(owner, e))) {
			double d = candidate.distanceToSqr(from);

			if (d < best && from.hasLineOfSight(candidate)) {
				best = d;
				next = candidate;
			}
		}

		if (next == null) {
			startReturn();
		} else {
			bounces++;
			bounceTarget = next;
		}
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		// Never saved (noSave): a throw does not survive a reload.
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		// Never saved (noSave).
	}
}
