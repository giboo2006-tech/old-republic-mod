package com.oldrepublic.saber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.CombatHooks.DeflectResult;
import com.oldrepublic.combat.DeflectableProjectile;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Saber guard: melee blocking and parries ({@link #modifyIncomingDamage}, called from
 * {@code LivingEntityGuardMixin}), projectile deflection ({@link #onProjectile}, installed as the
 * {@code CombatHooks} projectile guard) and guard stamina. All server side.
 */
public final class SaberGuard {
	/** What the client needs for the stamina bar. */
	public record GuardSnapshot(float stamina, float max, boolean broken) {
		public static final StreamCodec<RegistryFriendlyByteBuf, GuardSnapshot> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, GuardSnapshot::stamina,
				ByteBufCodecs.FLOAT, GuardSnapshot::max,
				ByteBufCodecs.BOOL, GuardSnapshot::broken,
				GuardSnapshot::new);
	}

	/** Guard stamina, synced to its owner only. Not persistent: it refills on login. */
	public static final AttachmentType<GuardSnapshot> GUARD = AttachmentRegistry.create(
			OldRepublicMod.id("saber_guard"),
			builder -> builder.syncWith(GuardSnapshot.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Server-side guard state per player. */
	private static final class GuardState {
		float stamina;
		float max;
		long lastDrainTick = Long.MIN_VALUE / 2;
		long brokenUntil;
		@Nullable GuardSnapshot sent;

		GuardState(float max) {
			this.max = max;
			this.stamina = max;
		}
	}

	private static final Map<UUID, GuardState> RUNTIME = new HashMap<>();
	/** Players whose current hit was parried, with the game time, so ALLOW_DAMAGE cancels it. */
	private static final Map<UUID, Long> CANCEL = new HashMap<>();

	/** The ignited saber {@code player} is guarding with, or {@link ItemStack#EMPTY}. */
	public static ItemStack guardingSaber(LivingEntity entity) {
		if (!entity.isUsingItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = entity.getUseItem();
		return SaberComponents.isIgnited(stack) ? stack : ItemStack.EMPTY;
	}

	/**
	 * Applies the guard to a melee (or other non-bolt) hit. Returns the damage that should go
	 * through. A parry returns 0 and marks the hit to be cancelled entirely by ALLOW_DAMAGE.
	 */
	public static float modifyIncomingDamage(LivingEntity entity, ServerLevel level, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || amount <= 0) {
			return amount;
		}

		ItemStack saber = guardingSaber(player);
		Vec3 from = source.getSourcePosition();

		if (saber.isEmpty() || from == null || source.is(DamageTypeTags.BYPASSES_SHIELD) || player.invulnerableTime > 10
				|| source.getDirectEntity() instanceof DeflectableProjectile) {
			return amount;
		}

		SaberBalance b = SaberToggle.balance();

		if (!inFront(player, from, b.guardArcDegrees)) {
			return amount;
		}

		SaberComponents.SaberConfig config = SaberComponents.config(saber);
		SaberStats stats = SaberStats.of(config, b);
		GuardState rt = runtime(player, stats.maxStamina());
		long now = level.getGameTime();
		Entity direct = source.getDirectEntity();
		Vec3 contact = contactPoint(player, direct != null ? direct.getBoundingBox().getCenter() : from);
		int colour = config.color().ordinal();

		if (player.getTicksUsingItem() < stats.parryTicks()) {
			if (source.getEntity() instanceof LivingEntity attacker && attacker != player) {
				stagger(player, attacker, b);
			}

			EffectEvents.toTracking(player, SaberEffects.PARRY, contact, player.getLookAngle(), player.getId(), 1f, colour);
			CANCEL.put(player.getUUID(), now);
			return 0f;
		}

		float drain = b.meleeDrainBase + b.meleeDrainPerDamage * amount;

		if (drain(player, rt, drain, now)) {
			guardBreak(player, saber, rt, b, now, colour);
			return amount;
		}

		EffectEvents.toTracking(player, SaberEffects.CLASH, contact, player.getLookAngle(), player.getId(), drain, colour);
		return amount * (1f - b.meleeReduction);
	}

	/** True once for a hit that {@link #modifyIncomingDamage} parried this tick. */
	public static boolean consumeCancel(LivingEntity entity) {
		Long tick = CANCEL.remove(entity.getUUID());
		return tick != null && tick == entity.level().getGameTime();
	}

	/** The {@code CombatHooks} projectile guard: blocks, deflects or parries blaster bolts. */
	public static DeflectResult onProjectile(LivingEntity target, Projectile projectile) {
		if (!(target instanceof ServerPlayer player) || !(projectile instanceof DeflectableProjectile bolt)) {
			return DeflectResult.NONE;
		}

		ItemStack saber = guardingSaber(player);
		Vec3 velocity = bolt.deflectableVelocity();
		SaberBalance b = SaberToggle.balance();

		// The arc is judged from the bolt's direction of travel: it came from -velocity. The hit
		// location is not used for this because vanilla moves the projectile onto its hit point
		// before handling the hit, which may be anywhere on (or inside) the defender's box.
		if (saber.isEmpty() || !comesFromFront(player, velocity, b.guardArcDegrees)) {
			return DeflectResult.NONE;
		}

		SaberComponents.SaberConfig config = SaberComponents.config(saber);
		SaberStats stats = SaberStats.of(config, b);
		GuardState rt = runtime(player, stats.maxStamina());
		long now = player.level().getGameTime();
		double speed = Math.max(0.5, velocity.length());
		// The blade meets the bolt in front of the chest, on the side it came from. The bolt leaves
		// from there, so a deflected or returned bolt never starts inside the defender or at its feet.
		Vec3 contact = boltContactPoint(player, velocity);
		projectile.setPos(contact);
		int colour = config.color().ordinal();

		if (player.getTicksUsingItem() < stats.parryTicks()) {
			Entity shooter = bolt.deflectableShooter();
			Vec3 dir = shooter != null && shooter.isAlive() && shooter != player
					? shooter.getEyePosition().subtract(contact).normalize()
					: velocity.scale(-1).normalize();
			bolt.redirect(dir.scale(speed), player, true);
			QuestHooks.onBoltDeflected(player, true);
			EffectEvents.toTracking(player, SaberEffects.PARRY, contact, dir, player.getId(), 1f, colour);
			return DeflectResult.PARRIED;
		}

		if (drain(player, rt, b.boltDrain, now)) {
			guardBreak(player, saber, rt, b, now, colour);
			projectile.discard();
			return DeflectResult.BLOCKED;
		}

		float arc = b.deflectArcDegrees;
		float pitch = player.getXRot() + (player.getRandom().nextFloat() - 0.5f) * arc;
		float yaw = player.getYRot() + (player.getRandom().nextFloat() * 2f - 1f) * arc;
		Vec3 dir = Vec3.directionFromRotation(Mth.clamp(pitch, -80f, 80f), yaw);
		bolt.redirect(dir.scale(speed), player, false);
		QuestHooks.onBoltDeflected(player, false);
		EffectEvents.toTracking(player, SaberEffects.DEFLECT, contact, dir, player.getId(), 1f, colour);
		return DeflectResult.DEFLECTED;
	}

	/** Regeneration and syncing, once per server tick for every online player. */
	public static void tick(ServerPlayer player) {
		ItemStack held = heldSaber(player);
		GuardState rt = RUNTIME.get(player.getUUID());

		if (rt == null) {
			if (held.isEmpty()) {
				return;
			}

			rt = runtime(player, SaberStats.of(SaberComponents.config(held), SaberToggle.balance()).maxStamina());
		}

		SaberBalance b = SaberToggle.balance();
		long now = player.level().getGameTime();

		if (!held.isEmpty()) {
			rt.max = SaberStats.of(SaberComponents.config(held), b).maxStamina();
		}

		rt.stamina = Math.min(rt.stamina, rt.max);
		boolean broken = now < rt.brokenUntil;

		if (!broken && rt.stamina < rt.max && guardingSaber(player).isEmpty() && now - rt.lastDrainTick >= b.guardRegenDelayTicks) {
			rt.stamina = Math.min(rt.max, rt.stamina + b.guardRegenPerSecond / 20f);
		}

		GuardSnapshot sent = rt.sent;
		boolean due = sent == null || sent.broken() != broken || sent.max() != rt.max
				|| (sent.stamina() != rt.stamina && (rt.stamina >= rt.max || now % 5 == 0));

		if (due) {
			sync(player, rt, broken);
		}
	}

	/** Forgets a player's guard (logout, respawn). */
	public static void forget(Player player) {
		RUNTIME.remove(player.getUUID());
		CANCEL.remove(player.getUUID());
	}

	/** Current stamina and max, for the {@code /oldrepublic saber stamina} command. */
	public static @Nullable GuardSnapshot snapshot(ServerPlayer player) {
		GuardState rt = RUNTIME.get(player.getUUID());
		return rt == null ? null : new GuardSnapshot(rt.stamina, rt.max, player.level().getGameTime() < rt.brokenUntil);
	}

	/** Refills the guard (command and tests). */
	public static void refill(ServerPlayer player) {
		GuardState rt = RUNTIME.get(player.getUUID());

		if (rt != null) {
			rt.stamina = rt.max;
			rt.brokenUntil = 0;
			sync(player, rt, false);
		}
	}

	private static GuardState runtime(ServerPlayer player, float max) {
		return RUNTIME.computeIfAbsent(player.getUUID(), id -> new GuardState(max));
	}

	private static ItemStack heldSaber(Player player) {
		ItemStack main = player.getMainHandItem();
		return main.getItem() instanceof LightsaberItem ? main
				: player.getOffhandItem().getItem() instanceof LightsaberItem ? player.getOffhandItem() : ItemStack.EMPTY;
	}

	/** Spends stamina; returns true if this broke the guard. */
	private static boolean drain(ServerPlayer player, GuardState rt, float amount, long now) {
		rt.stamina = Math.max(0f, rt.stamina - amount);
		rt.lastDrainTick = now;
		sync(player, rt, false);
		return rt.stamina <= 0f;
	}

	private static void guardBreak(ServerPlayer player, ItemStack saber, GuardState rt, SaberBalance b, long now, int colour) {
		player.stopUsingItem();
		player.getCooldowns().addCooldown(saber, b.guardBreakTicks);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, b.guardBreakTicks, 1));
		rt.stamina = 0f;
		rt.brokenUntil = now + b.guardBreakTicks;
		rt.lastDrainTick = now;
		sync(player, rt, true);
		Vec3 chest = player.position().add(0, player.getBbHeight() * 0.6, 0);
		EffectEvents.toTracking(player, SaberEffects.GUARD_BREAK, chest, player.getLookAngle(), player.getId(), 1f, colour);
	}

	/** Parry punishment: pushed back, slowed and weakened; players also lose their swing charge. */
	private static void stagger(ServerPlayer defender, LivingEntity attacker, SaberBalance b) {
		Vec3 away = attacker.position().subtract(defender.position()).horizontal();
		Vec3 impulse = (away.lengthSqr() < 1.0E-4 ? defender.getLookAngle().horizontal() : away).normalize().scale(b.parryKnockback).add(0, 0.15, 0);
		attacker.setDeltaMovement(attacker.getDeltaMovement().scale(0.3).add(impulse));
		attacker.hurtMarked = true;
		attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, b.staggerTicks, 3));
		attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, b.staggerTicks, 1));

		if (attacker instanceof Player attackingPlayer) {
			attackingPlayer.resetAttackStrengthTicker();
		}
	}

	private static void sync(ServerPlayer player, GuardState rt, boolean broken) {
		GuardSnapshot snapshot = new GuardSnapshot(rt.stamina, rt.max, broken);
		rt.sent = snapshot;
		player.setAttached(GUARD, snapshot);
	}

	/** Whether {@code from} lies within the guard arc around the player's horizontal facing. */
	private static boolean inFront(Player player, Vec3 from, float arcDegrees) {
		Vec3 to = from.subtract(player.position()).horizontal();

		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}

		Vec3 facing = Vec3.directionFromRotation(0f, player.getYRot());
		return facing.dot(to.normalize()) >= Math.cos(Math.toRadians(arcDegrees / 2f));
	}

	/**
	 * Whether a projectile travelling along {@code velocity} comes from within the guard arc
	 * around the player's horizontal facing. A purely vertical approach counts as in front.
	 */
	private static boolean comesFromFront(Player player, Vec3 velocity, float arcDegrees) {
		Vec3 from = velocity.reverse().horizontal();

		if (from.lengthSqr() < 1.0E-6) {
			return true;
		}

		Vec3 facing = Vec3.directionFromRotation(0f, player.getYRot());
		return facing.dot(from.normalize()) >= Math.cos(Math.toRadians(arcDegrees / 2f));
	}

	/** Where the blade meets an incoming bolt: chest height, a little out toward where it came from. */
	private static Vec3 boltContactPoint(Player defender, Vec3 velocity) {
		Vec3 from = velocity.reverse().horizontal();
		Vec3 out = from.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0f, defender.getYRot()) : from.normalize();
		return defender.position().add(0, defender.getBbHeight() * 0.65, 0).add(out.scale(defender.getBbWidth() * 0.5 + 0.35));
	}

	/** Where blade meets weapon: a point in front of the defender, pulled toward the attacker. */
	private static Vec3 contactPoint(Player defender, Vec3 attacker) {
		Vec3 blade = defender.getEyePosition().add(defender.getLookAngle().scale(0.9)).subtract(0, 0.3, 0);
		return blade.lerp(attacker, 0.35);
	}

	static void init() {
	}

	private SaberGuard() {
	}
}
