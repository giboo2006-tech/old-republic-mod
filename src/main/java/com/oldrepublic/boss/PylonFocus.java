package com.oldrepublic.boss;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.ForceReactiveEntity;
import com.oldrepublic.force.PushBranch;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.throwing.SaberThrow;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The hittable focus of a singing (or regrowing) resonance pylon crystal (spec 8.3): a small
 * non-living entity on top of the pylon block, attackable like the vanilla end crystal. It holds no
 * state of its own: its {@link ArenaSession} keeps the pylon's resonance, and every silencing source
 * reports to it.
 *
 * <table>
 * <caption>What each source takes off ({@code collector} balance)</caption>
 * <tr><td>a saber hit (melee with a usable saber)</td><td>{@code pylonSaber} 10</td></tr>
 * <tr><td>Push / Focused Blast</td><td>{@code pylonPush} 10 / {@code pylonFocusedBlast} 15</td></tr>
 * <tr><td>Saber Throw</td><td>{@code pylonThrow} 15</td></tr>
 * <tr><td>Lightning</td><td>{@code pylonLightningPerSecond} 10 per second</td></tr>
 * <tr><td>a parried sentinel lance</td><td>{@code pylonParriedLance} 15</td></tr>
 * <tr><td>Pull</td><td>rips the crystal out at once (within {@code pylonPullRange}, line of sight)</td></tr>
 * <tr><td>Lift</td><td>silences it after a {@code pylonLiftTicks} channel (same range and sight)</td></tr>
 * <tr><td>anything else a player hurts it with</td><td>its damage</td></tr>
 * </table>
 * Damage whose source is not a player (an unparried lance, fire, an explosion, his own debris) takes
 * nothing off.
 */
public class PylonFocus extends Entity implements ForceReactiveEntity {
	/** The focus sits this high above the pylon block's base (the crystal cluster on top). */
	public static final double CRYSTAL_HEIGHT = 0.85;
	/** A Lift channel that has not reached the focus for this many ticks has ended. */
	private static final int LIFT_GRACE_TICKS = 2;

	private @Nullable ArenaSession session;
	private int index = -1;
	private @Nullable UUID lifter;
	private int liftTicks;
	private long lastLiftTick;

	public PylonFocus(EntityType<? extends PylonFocus> type, Level level) {
		super(type, level);
	}

	/** A focus for pylon {@code index} of {@code session}, on the crystal at {@code pylon}; not yet added. */
	static PylonFocus create(ServerLevel level, ArenaSession session, int index, BlockPos pylon) {
		PylonFocus focus = new PylonFocus(BossEntities.PYLON_FOCUS, level);
		Vec3 at = Vec3.atBottomCenterOf(pylon).add(0, CRYSTAL_HEIGHT, 0);
		focus.snapTo(at.x, at.y, at.z, 0f, 0f);
		focus.session = session;
		focus.index = index;
		return focus;
	}

	/** The pylon this focus belongs to (its index in the session's pylon list), or -1. */
	public int index() {
		return index;
	}

	/** The crystal's centre, where currents leave and hits land. */
	public Vec3 crystalCentre() {
		return getBoundingBox().getCenter();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	public void tick() {
		super.tick();

		if (!(level() instanceof ServerLevel level)) {
			return;
		}

		if (session == null || !session.ownsFocus(this)) {
			discard();
			return;
		}

		if (lifter != null && level.getGameTime() - lastLiftTick > LIFT_GRACE_TICKS) {
			lifter = null;
			liftTicks = 0;
		}
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// Only players silence a pylon (8.3); a parried lance belongs to the parrier, an unparried one, fire or his own debris do nothing.
		if (session == null || isRemoved() || !(source.getEntity() instanceof ServerPlayer)) {
			return false;
		}

		float loss = loss(source, amount, CollectorBalance.get());

		if (loss <= 0f) {
			return false;
		}

		session.damagePylon(this, loss, source.getEntity());
		return true;
	}

	/** Resonance a hit takes off: a saber hit, a parried lance, a thrown saber, else its damage. */
	static float loss(DamageSource source, float amount, CollectorBalance b) {
		Entity direct = source.getDirectEntity();

		if (direct instanceof BlasterBolt bolt && bolt.heavy() && bolt.parried()) {
			return b.pylonParriedLance;
		}

		if (source.is(SaberThrow.DAMAGE)) {
			return b.pylonThrow;
		}

		if (source.getEntity() instanceof LivingEntity attacker && direct == attacker && SaberComponents.isUsable(attacker.getMainHandItem())) {
			return b.pylonSaber;
		}

		return amount;
	}

	/** Resonance a Push takes off: Focused Blast {@code pylonFocusedBlast}, any other push {@code pylonPush}. */
	static float pushLoss(PushBranch branch, CollectorBalance b) {
		return branch == PushBranch.FOCUSED_BLAST ? b.pylonFocusedBlast : b.pylonPush;
	}

	// ---------------------------------------------------------------- Force

	@Override
	public boolean onForcePush(ServerPlayer caster, Vec3 dir, float strength) {
		if (session == null || isRemoved()) {
			return false;
		}

		session.damagePylon(this, pushLoss(ForcePush.branch(caster), CollectorBalance.get()), caster);
		return true;
	}

	/** Pull rips the crystal out at once, within {@code pylonPullRange} and in sight. */
	@Override
	public boolean onForcePull(ServerPlayer caster, Vec3 dir, float strength) {
		if (session == null || isRemoved() || !inReach(caster)) {
			return false;
		}

		session.ripPylon(this, caster);
		return true;
	}

	/** Lift silences the crystal after a {@code pylonLiftTicks} channel, within reach and in sight. */
	@Override
	public boolean onForceLift(ServerPlayer caster) {
		if (session == null || isRemoved() || !inReach(caster)) {
			return false;
		}

		long now = level().getGameTime();

		if (!caster.getUUID().equals(lifter)) {
			lifter = caster.getUUID();
			liftTicks = 0;
		}

		lastLiftTick = now;

		if (++liftTicks >= CollectorBalance.get().pylonLiftTicks) {
			session.silencePylon(this, caster);
		}

		return true;
	}

	@Override
	public boolean onForceLightning(ServerPlayer caster, float perTick) {
		if (session == null || isRemoved()) {
			return false;
		}

		session.damagePylon(this, CollectorBalance.get().pylonLightningPerSecond / 20f, caster);
		return true;
	}

	@Override
	public boolean onSaberThrowHit(ServerPlayer caster, float damage) {
		if (session == null || isRemoved()) {
			return false;
		}

		session.damagePylon(this, CollectorBalance.get().pylonThrow, caster);
		return true;
	}

	/** Within {@code pylonPullRange} of the caster's eye, with no block between. */
	boolean inReach(ServerPlayer caster) {
		Vec3 eye = caster.getEyePosition();
		Vec3 centre = crystalCentre();
		float range = CollectorBalance.get().pylonPullRange;

		if (eye.distanceToSqr(centre) > range * range) {
			return false;
		}

		BlockHitResult hit = level().clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(eye) >= eye.distanceToSqr(centre) - 0.25;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}
}
