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
import com.oldrepublic.saber.duel.NpcGuard;
import com.oldrepublic.saber.duel.Spar;

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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * A player's saber guard and saber stamina. Melee blocking and parries ({@link #onMelee}, called
 * from {@link SaberCombat}'s damage pipeline), projectile deflection ({@link #onProjectile},
 * installed as the {@code CombatHooks} projectile guard) and the stamina pool that guarding, heavy
 * attacks and lunges share (spec section 9.1). Forms and Lightsaber passives change the numbers
 * through {@link SaberPassives}. All server side.
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

	/** Saber stamina, synced to its owner only. Not persistent: it refills on login. */
	public static final AttachmentType<GuardSnapshot> GUARD = AttachmentRegistry.create(
			OldRepublicMod.id("saber_guard"),
			builder -> builder.syncWith(GuardSnapshot.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Server-side stamina pool per player. */
	private static final class Pool {
		float stamina;
		float max;
		long lastDrainTick = Long.MIN_VALUE / 2;
		long brokenUntil;
		@Nullable GuardSnapshot sent;

		Pool(float max) {
			this.max = max;
			this.stamina = max;
		}
	}

	private static final Map<UUID, Pool> RUNTIME = new HashMap<>();

	/** The ignited saber {@code entity} is guarding with, or {@link ItemStack#EMPTY}. */
	public static ItemStack guardingSaber(LivingEntity entity) {
		if (!entity.isUsingItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = entity.getUseItem();
		return SaberComponents.isUsable(stack) ? stack : ItemStack.EMPTY;
	}

	/** Whether {@code player} is guarding and {@code from} lies inside the guard arc of the active form. */
	public static boolean isGuardingAgainst(Player player, Vec3 from) {
		if (guardingSaber(player).isEmpty()) {
			return false;
		}

		return inFront(player, from, SaberPassives.form(player).modifiers(SaberToggle.balance()).guardArc());
	}

	/**
	 * Applies the guard to a hit that is not a deflectable bolt. Returns the damage that should go
	 * through, or -1 when the hit was parried (the pipeline then cancels it).
	 *
	 * @param strike the kind of strike, or null for an ordinary hit (mob melee, arrows, explosions)
	 */
	static float onMelee(ServerPlayer player, ServerLevel level, DamageSource source, float amount, SaberCombat.@Nullable Strike strike) {
		ItemStack saber = guardingSaber(player);
		Vec3 from = source.getSourcePosition();

		if (saber.isEmpty() || from == null || source.is(DamageTypeTags.BYPASSES_SHIELD) || player.invulnerableTime > 10
				|| source.getDirectEntity() instanceof DeflectableProjectile) {
			return amount;
		}

		SaberBalance b = SaberToggle.balance();
		SaberForm.Modifiers form = SaberPassives.form(player).modifiers(b);

		if (!inFront(player, from, form.guardArc())) {
			return amount;
		}

		SaberStats stats = SaberPassives.serverStats(player, saber);
		Pool pool = pool(player, stats.maxStamina());
		long now = level.getGameTime();
		Entity direct = source.getDirectEntity();
		Vec3 contact = contactPoint(player, direct != null ? direct.getBoundingBox().getCenter() : from);
		int colour = SaberComponents.config(saber).color().ordinal();
		boolean parryable = strike == null || !strike.unparryable();

		if (parryable && player.getTicksUsingItem() < stats.parryTicks()) {
			if (source.getEntity() instanceof LivingEntity attacker && attacker != player) {
				stagger(player, attacker, b);
				onParried(player, attacker, contact, strike);
			}

			SaberCombat.onParry(player, b);
			EffectEvents.toTracking(player, SaberEffects.PARRY, contact, player.getLookAngle(), player.getId(), 1f, colour);
			return -1f;
		}

		float drainMult = form.drainMult() * (strike == null ? 1f : strike.guardDrainMult());
		float drain = (b.meleeDrainBase + b.meleeDrainPerDamage * amount) * drainMult;

		if (drain(player, pool, drain, now)) {
			guardBreak(player, saber, pool, b, now, colour);
			return amount;
		}

		EffectEvents.toTracking(player, SaberEffects.CLASH, contact, player.getLookAngle(), player.getId(), drain, colour);
		return amount * (1f - b.meleeReduction);
	}

	/** The rest of a melee parry: quest progress, spar goals and the attacker's own reaction. */
	private static void onParried(ServerPlayer player, LivingEntity attacker, Vec3 contact, SaberCombat.@Nullable Strike strike) {
		QuestHooks.onSaberTechnique(player, "parry", attacker, true);
		Spar.onParry(player, attacker);
		SaberCombat.Strike parried = strike != null ? strike : SaberCombat.Strike.NPC_LIGHT;

		if (attacker instanceof Mob mob && NpcGuard.state(mob) != null) {
			NpcGuard.onParriedByPlayer(mob, contact);
		}

		if (attacker instanceof ParryReactive reactive) {
			reactive.onParried(player, parried);
		}
	}

	/** The {@code CombatHooks} projectile guard: blocks, deflects or parries blaster bolts. */
	public static DeflectResult onProjectile(LivingEntity target, Projectile projectile) {
		if (!(target instanceof ServerPlayer player) || !(projectile instanceof DeflectableProjectile bolt)) {
			return DeflectResult.NONE;
		}

		ItemStack saber = guardingSaber(player);
		Vec3 velocity = bolt.deflectableVelocity();
		SaberBalance b = SaberToggle.balance();
		SaberForm.Modifiers form = SaberPassives.form(player).modifiers(b);

		// The arc is judged from the bolt's direction of travel: it came from -velocity. The hit
		// location is not used for this because vanilla moves the projectile onto its hit point
		// before handling the hit, which may be anywhere on (or inside) the defender's box.
		if (saber.isEmpty() || !comesFromFront(player, velocity, form.guardArc())) {
			return DeflectResult.NONE;
		}

		SaberStats stats = SaberPassives.serverStats(player, saber);
		Pool pool = pool(player, stats.maxStamina());
		long now = player.level().getGameTime();
		double speed = Math.max(0.5, velocity.length());
		// The blade meets the bolt in front of the chest, on the side it came from. The bolt leaves
		// from there, so a deflected or returned bolt never starts inside the defender or at its feet.
		Vec3 contact = boltContactPoint(player, velocity);
		projectile.setPos(contact);
		int colour = SaberComponents.config(saber).color().ordinal();
		// Read before redirect(), which makes the defender the bolt's owner.
		Entity shooter = bolt.deflectableShooter();

		if (player.getTicksUsingItem() < stats.parryTicks()) {
			Vec3 dir = shooter != null && shooter.isAlive() && shooter != player
					? shooter.getEyePosition().subtract(contact).normalize()
					: velocity.scale(-1).normalize();
			bolt.redirect(dir.scale(speed), player, true);
			QuestHooks.onBoltDeflected(player, true, shooter);
			SaberCombat.onParry(player, b);
			EffectEvents.toTracking(player, SaberEffects.PARRY, contact, dir, player.getId(), 1f, colour);
			return DeflectResult.PARRIED;
		}

		if (drain(player, pool, b.boltDrain * form.drainMult(), now)) {
			guardBreak(player, saber, pool, b, now, colour);
			projectile.discard();
			return DeflectResult.BLOCKED;
		}

		Vec3 dir = deflectDirection(player, form, b);
		bolt.redirect(dir.scale(speed), player, false);
		QuestHooks.onBoltDeflected(player, false, shooter);
		EffectEvents.toTracking(player, SaberEffects.DEFLECT, contact, dir, player.getId(), 1f, colour);
		return DeflectResult.DEFLECTED;
	}

	/**
	 * Where an unparried bolt goes: within the form's aim (Soresu) or {@code deflection_control}'s
	 * cone around the crosshair, whichever is tighter, otherwise a random arc.
	 */
	private static Vec3 deflectDirection(ServerPlayer player, SaberForm.Modifiers form, SaberBalance b) {
		float aim = form.deflectAim();

		if (SaberPassives.has(player, SaberPassives.DEFLECTION_CONTROL)) {
			aim = aim < 0f ? b.deflectionControlDegrees : Math.min(aim, b.deflectionControlDegrees);
		}

		float pitchSpread = aim >= 0f ? aim : b.deflectArcDegrees * 0.5f;
		float yawSpread = aim >= 0f ? aim : b.deflectArcDegrees;
		float pitch = player.getXRot() + (player.getRandom().nextFloat() * 2f - 1f) * pitchSpread;
		float yaw = player.getYRot() + (player.getRandom().nextFloat() * 2f - 1f) * yawSpread;
		return Vec3.directionFromRotation(Mth.clamp(pitch, -80f, 80f), yaw);
	}

	/** Regeneration and syncing, once per server tick for every online player. */
	public static void tick(ServerPlayer player) {
		ItemStack held = heldSaber(player);
		Pool pool = RUNTIME.get(player.getUUID());

		if (pool == null && held.isEmpty()) {
			return;
		}

		SaberBalance b = SaberToggle.balance();
		SaberForm.Modifiers form = SaberPassives.form(player).modifiers(b);

		if (!held.isEmpty()) {
			float max = SaberPassives.serverStats(player, held).maxStamina();
			pool = pool(player, max);
			pool.max = max;
		}

		long now = player.level().getGameTime();
		pool.stamina = Math.min(pool.stamina, pool.max);
		boolean broken = now < pool.brokenUntil;

		if (!broken && pool.stamina < pool.max && guardingSaber(player).isEmpty() && now - pool.lastDrainTick >= b.guardRegenDelayTicks) {
			pool.stamina = Math.min(pool.max, pool.stamina + b.guardRegenPerSecond * form.regenMult() / 20f);
		}

		GuardSnapshot sent = pool.sent;
		boolean due = sent == null || sent.broken() != broken || sent.max() != pool.max
				|| (sent.stamina() != pool.stamina && (pool.stamina >= pool.max || now % 5 == 0));

		if (due) {
			sync(player, pool, broken);
		}
	}

	/** Current stamina, or the maximum of the held saber if no pool exists yet. 0 with no saber. */
	public static float stamina(ServerPlayer player) {
		Pool pool = RUNTIME.get(player.getUUID());

		if (pool != null) {
			return pool.stamina;
		}

		ItemStack held = heldSaber(player);
		return held.isEmpty() ? 0f : SaberPassives.serverStats(player, held).maxStamina();
	}

	/** Whether the guard is broken right now. */
	public static boolean isBroken(ServerPlayer player) {
		Pool pool = RUNTIME.get(player.getUUID());
		return pool != null && player.level().getGameTime() < pool.brokenUntil;
	}

	/**
	 * Spends stamina for a technique (heavy attack, lunge). Fails, spending nothing, while the guard
	 * is broken or the pool holds less than {@code amount}. Creative players always succeed.
	 */
	public static boolean trySpend(ServerPlayer player, float amount) {
		ItemStack held = heldSaber(player);

		if (held.isEmpty()) {
			return false;
		}

		Pool pool = pool(player, SaberPassives.serverStats(player, held).maxStamina());
		long now = player.level().getGameTime();

		if (now < pool.brokenUntil || (!player.isCreative() && pool.stamina + 1.0e-3f < amount)) {
			return false;
		}

		if (!player.isCreative()) {
			drain(player, pool, amount, now);
		}

		return true;
	}

	/**
	 * Drains stamina from outside the melee path (a Force Push or Lightning on a guarding player).
	 * Returns true when this broke the guard.
	 */
	public static boolean drainGuard(ServerPlayer player, float amount) {
		ItemStack saber = guardingSaber(player);

		if (saber.isEmpty()) {
			return false;
		}

		SaberBalance b = SaberToggle.balance();
		Pool pool = pool(player, SaberPassives.serverStats(player, saber).maxStamina());
		long now = player.level().getGameTime();
		float scaled = amount * SaberPassives.form(player).modifiers(b).drainMult();

		if (drain(player, pool, scaled, now)) {
			guardBreak(player, saber, pool, b, now, SaberComponents.config(saber).color().ordinal());
			return true;
		}

		return false;
	}

	/** Gives back stamina (Serene Ward). */
	public static void restore(ServerPlayer player, float amount) {
		Pool pool = RUNTIME.get(player.getUUID());

		if (pool != null && amount > 0f) {
			pool.stamina = Math.min(pool.max, pool.stamina + amount);
			sync(player, pool, player.level().getGameTime() < pool.brokenUntil);
		}
	}

	/** Forgets a player's guard (logout, respawn). */
	public static void forget(Player player) {
		RUNTIME.remove(player.getUUID());
	}

	/** Current stamina and max, for the {@code /oldrepublic saber stamina} command. */
	public static @Nullable GuardSnapshot snapshot(ServerPlayer player) {
		Pool pool = RUNTIME.get(player.getUUID());
		return pool == null ? null : new GuardSnapshot(pool.stamina, pool.max, player.level().getGameTime() < pool.brokenUntil);
	}

	/** Refills the guard (command and tests). */
	public static void refill(ServerPlayer player) {
		Pool pool = RUNTIME.get(player.getUUID());

		if (pool != null) {
			pool.stamina = pool.max;
			pool.brokenUntil = 0;
			sync(player, pool, false);
		}
	}

	/** Sets the stamina to {@code amount} (command and tests). */
	public static void set(ServerPlayer player, float amount) {
		ItemStack held = heldSaber(player);
		Pool pool = held.isEmpty() ? RUNTIME.get(player.getUUID()) : pool(player, SaberPassives.serverStats(player, held).maxStamina());

		if (pool != null) {
			pool.stamina = Mth.clamp(amount, 0f, pool.max);
			sync(player, pool, player.level().getGameTime() < pool.brokenUntil);
		}
	}

	private static Pool pool(ServerPlayer player, float max) {
		return RUNTIME.computeIfAbsent(player.getUUID(), id -> new Pool(max));
	}

	private static ItemStack heldSaber(Player player) {
		ItemStack main = player.getMainHandItem();
		return main.getItem() instanceof LightsaberItem ? main
				: player.getOffhandItem().getItem() instanceof LightsaberItem ? player.getOffhandItem() : ItemStack.EMPTY;
	}

	/** Spends stamina; returns true if this emptied the pool. */
	private static boolean drain(ServerPlayer player, Pool pool, float amount, long now) {
		pool.stamina = Math.max(0f, pool.stamina - amount);
		pool.lastDrainTick = now;
		sync(player, pool, now < pool.brokenUntil);
		return pool.stamina <= 0f;
	}

	private static void guardBreak(ServerPlayer player, ItemStack saber, Pool pool, SaberBalance b, long now, int colour) {
		player.stopUsingItem();
		player.getCooldowns().addCooldown(saber, b.guardBreakTicks);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, b.guardBreakTicks, 1));
		pool.stamina = 0f;
		pool.brokenUntil = now + b.guardBreakTicks;
		pool.lastDrainTick = now;
		sync(player, pool, true);
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

	private static void sync(ServerPlayer player, Pool pool, boolean broken) {
		GuardSnapshot snapshot = new GuardSnapshot(pool.stamina, pool.max, broken);
		pool.sent = snapshot;
		player.setAttached(GUARD, snapshot);
	}

	/** Whether {@code from} lies within {@code arcDegrees} around the entity's horizontal facing. */
	public static boolean inFront(LivingEntity entity, Vec3 from, float arcDegrees) {
		Vec3 to = from.subtract(entity.position()).horizontal();

		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}

		Vec3 facing = Vec3.directionFromRotation(0f, entity.getYRot());
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
	public static Vec3 contactPoint(LivingEntity defender, Vec3 attacker) {
		Vec3 blade = defender.getEyePosition().add(defender.getLookAngle().scale(0.9)).subtract(0, 0.3, 0);
		return blade.lerp(attacker, 0.35);
	}

	static void init() {
	}

	private SaberGuard() {
	}
}
