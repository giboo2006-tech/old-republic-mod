package com.oldrepublic.saber;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.BossArenas;
import com.oldrepublic.combat.CombatHooks.DeflectResult;
import com.oldrepublic.combat.DeflectableProjectile;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.construct.SentinelBalance;
import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.equipment.blaster.Blasters;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.saber.duel.DuelState;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.NpcGuard;
import com.oldrepublic.saber.duel.SaberDuelist;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A player's saber guard and saber stamina. Melee blocking and parries ({@link #onMelee}, called
 * from {@link SaberCombat}'s damage pipeline), projectile deflection ({@link #onProjectile},
 * installed as the {@code CombatHooks} projectile guard) and the stamina pool that guarding, heavy
 * attacks and lunges share (spec section 9.1). Forms and Lightsaber passives change the numbers
 * through {@link SaberPassives}. Milestone 7: the guarding hilt's own guard drain and clan iron
 * ({@link HiltTraits#guardDrainMultiplier}) scale every drain, and its boss set acts on parries, blocks
 * and regeneration ({@link HiltSetEffects}: Curator's Eye, Vigil). All server side.
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
		ItemStack saber = guardingSaber(player);

		if (saber.isEmpty()) {
			return false;
		}

		SaberBalance b = SaberToggle.balance();
		return inFront(player, from, SaberStaffs.guardArc(SaberPassives.form(player).modifiers(b).guardArc(), saber, b));
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

		if (!inFront(player, from, SaberStaffs.guardArc(form.guardArc(), saber, b))) {
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

				// Djem So (MILESTONE_5.md 10.1): the parried attacker is staggered as well, its wind-up broken.
				if (form.traits().parryStaggerTicks() > 0 && attacker.isAlive()) {
					ControlEffects.stagger(player, attacker, form.traits().parryStaggerTicks(), 3);
				}

				onParried(player, attacker, contact, strike);
				// Curator's Eye (MILESTONE_7.md 7.2): a full Collector hilt marks the parried attacker.
				HiltSetEffects.onParry(player, saber, attacker, b);
			}

			SaberCombat.onParry(player, b);
			EffectEvents.toTracking(player, SaberEffects.PARRY, contact, player.getLookAngle(), player.getId(), 1f, colour);
			return -1f;
		}

		// Milestone 7 (6.3, 3.3): the Warden emitter and clan iron drain the guard less.
		float drainMult = form.drainMult() * (strike == null ? 1f : strike.guardDrainMult()) * SaberStaffs.drainMult(saber, b)
				* HiltTraits.guardDrainMultiplier(saber, stats, b);
		float drain = (b.meleeDrainBase + b.meleeDrainPerDamage * amount) * drainMult;

		if (drain(player, pool, drain, now)) {
			guardBreak(player, saber, pool, b, now, colour);
			return amount;
		}

		EffectEvents.toTracking(player, SaberEffects.CLASH, contact, player.getLookAngle(), player.getId(), drain, colour);
		// Vigil (MILESTONE_7.md 7.2): a full Watchman hilt slows the attacker whose blow it blocked.
		HiltSetEffects.onBlock(player, saber, source, b);
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

	/**
	 * The {@code CombatHooks} projectile guard: blocks, deflects or parries blaster bolts for
	 * guarding players, and lets guarding saber NPCs whose profile {@code deflects} send bolts back
	 * (spec 10.7).
	 * <ul>
	 * <li>A projectile that is not {@link DeflectableProjectile#saberDeflectable() deflectable}
	 * (Archive debris) is only blocked by a player's guard: it drains {@link GuardDrainSource} or
	 * {@code nonDeflectableGuardDrain} and returns {@code BLOCKED}. The guard never moves, discards
	 * or redirects it and never counts a parry; the projectile breaks itself.</li>
	 * <li>A heavy bolt (the sentinel lance) is blocked for {@code heavyBoltGuardDrain} (the bolt
	 * discards itself) unless parried: a parry returns it straight at the shooter, or toward the
	 * crosshair within {@code reliquaryDeflectDegrees} inside a boss arena, hitting for
	 * {@code sentinel.lanceReturnDamage} (+{@code reliquaryParryBonus} with the Reliquary emitter),
	 * marked {@code parried()}.</li>
	 * <li>An ordinary bolt is parried (returned to the shooter; +{@code reliquaryParryBonus} damage
	 * with the Reliquary emitter), blocked when the drain breaks the guard, or deflected (the
	 * Reliquary emitter aims within {@code reliquaryDeflectDegrees} of the crosshair).</li>
	 * </ul>
	 * Every parry fires {@code CombatEvents.PARRY} once, through {@code QuestHooks.onBoltDeflected}.
	 */
	public static DeflectResult onProjectile(LivingEntity target, Projectile projectile) {
		if (!(projectile instanceof DeflectableProjectile bolt)) {
			return DeflectResult.NONE;
		}

		if (target instanceof ServerPlayer player) {
			return onPlayerProjectile(player, projectile, bolt);
		}

		if (target instanceof Mob mob) {
			return onNpcProjectile(mob, projectile, bolt);
		}

		return DeflectResult.NONE;
	}

	private static DeflectResult onPlayerProjectile(ServerPlayer player, Projectile projectile, DeflectableProjectile bolt) {
		ItemStack saber = guardingSaber(player);
		Vec3 velocity = bolt.deflectableVelocity();
		SaberBalance b = SaberToggle.balance();
		SaberForm.Modifiers form = SaberPassives.form(player).modifiers(b);

		// The arc is judged from the bolt's direction of travel: it came from -velocity. The hit
		// location is not used for this because vanilla moves the projectile onto its hit point
		// before handling the hit, which may be anywhere on (or inside) the defender's box.
		if (saber.isEmpty() || !comesFromFront(player, velocity, SaberStaffs.guardArc(form.guardArc(), saber, b))) {
			return DeflectResult.NONE;
		}

		SaberStats stats = SaberPassives.serverStats(player, saber);
		Pool pool = pool(player, stats.maxStamina());
		long now = player.level().getGameTime();
		double speed = Math.max(0.5, velocity.length());
		// Milestone 7 (6.3, 3.3): the Warden emitter and clan iron drain the guard less.
		float hiltDrain = HiltTraits.guardDrainMultiplier(saber, stats, b);
		Vec3 contact = boltContactPoint(player, velocity);
		int colour = SaberComponents.config(saber).color().ordinal();
		boolean reliquary = SaberComponents.config(saber).emitter() == HiltParts.Emitter.RELIQUARY;

		if (!bolt.saberDeflectable()) {
			// Blocked only. The projectile stays where it is and breaks itself on BLOCKED.
			float drain = projectile instanceof GuardDrainSource source ? source.guardDrain() : b.nonDeflectableGuardDrain;
			block(player, saber, pool, drain * form.drainMult() * SaberStaffs.drainMult(saber, b) * hiltDrain, contact, colour, now, b);
			return DeflectResult.BLOCKED;
		}

		// The blade meets the bolt in front of the chest, on the side it came from. The bolt leaves
		// from there, so a deflected or returned bolt never starts inside the defender or at its feet.
		projectile.setPos(contact);
		// Read before redirect(), which makes the defender the bolt's owner.
		Entity shooter = bolt.deflectableShooter();
		boolean parry = player.getTicksUsingItem() < stats.parryTicks();
		BlasterBolt blaster = projectile instanceof BlasterBolt bb ? bb : null;

		if (blaster != null && blaster.heavy()) {
			if (!parry) {
				// A heavy bolt is never deflected: the guard takes it and the bolt is spent (Shoulder to Shoulder eases it, 9.1; Makashi drains faster, M5 10.1).
				float drain = projectile instanceof GuardDrainSource source ? source.guardDrain() : b.heavyBoltGuardDrain;
				block(player, saber, pool, drain * form.drainMult() * form.traits().boltDrainMult() * SaberStaffs.drainMult(saber, b)
						* ArmourSets.boltGuardDrainMultiplier(player) * hiltDrain, contact, colour, now, b);
				return DeflectResult.BLOCKED;
			}

			Vec3 dir = BossArenas.isInArena(player) ? crosshairAim(player, contact, b.reliquaryDeflectDegrees)
					: returnDirection(player, shooter, contact, velocity);
			float mult = SentinelBalance.get().lanceReturnDamage / Math.max(0.01f, blaster.damage());
			blaster.redirect(dir.scale(speed), player, true, reliquary ? mult * (1f + b.reliquaryParryBonus) : mult);
			parried(player, saber, shooter, contact, dir, colour, b);
			return DeflectResult.PARRIED;
		}

		if (parry) {
			Vec3 dir = returnDirection(player, shooter, contact, velocity);

			if (blaster != null) {
				blaster.redirect(dir.scale(speed), player, true, reliquary ? 1f + b.reliquaryParryBonus : 1f);
			} else {
				bolt.redirect(dir.scale(speed), player, true);
			}

			parried(player, saber, shooter, contact, dir, colour, b);
			return DeflectResult.PARRIED;
		}

		// Shoulder to Shoulder (9.1): the full Republic trooper set drains the guard less per bolt; Makashi more (M5 10.1).
		// A scatter pellet drains its share of one bolt's, so a whole blast drains as one bolt (MILESTONE_7.md 9.1).
		if (drain(player, pool, b.boltDrain * form.drainMult() * form.traits().boltDrainMult() * SaberStaffs.drainMult(saber, b)
				* ArmourSets.boltGuardDrainMultiplier(player) * hiltDrain * Blasters.guardDrainShare(projectile), now)) {
			guardBreak(player, saber, pool, b, now, colour);
			projectile.discard();
			return DeflectResult.BLOCKED;
		}

		// Djem So (M5 10.1) sends a share of its deflections straight back at the shooter.
		boolean back = form.traits().boltReturnChance() > 0f && player.getRandom().nextFloat() < form.traits().boltReturnChance();
		Vec3 dir = back ? returnDirection(player, shooter, contact, velocity) : deflectDirection(player, form, b, reliquary);
		bolt.redirect(dir.scale(speed), player, false);
		QuestHooks.onBoltDeflected(player, false, shooter);
		EffectEvents.toTracking(player, SaberEffects.DEFLECT, contact, dir, player.getId(), 1f, colour);
		return DeflectResult.DEFLECTED;
	}

	/** A parried bolt: the quest hook (which fires {@code CombatEvents.PARRY} once), riposte, Curator's Eye (MILESTONE_7.md 7.2), effect. */
	private static void parried(ServerPlayer player, ItemStack saber, @Nullable Entity shooter, Vec3 contact, Vec3 dir, int colour, SaberBalance b) {
		QuestHooks.onBoltDeflected(player, true, shooter);
		SaberCombat.onParry(player, b);
		HiltSetEffects.onParry(player, saber, shooter, b);
		EffectEvents.toTracking(player, SaberEffects.PARRY, contact, dir, player.getId(), 1f, colour);
	}

	/** A guard absorbs a projectile it cannot send back: the drain, then a clash or a guard break. */
	private static void block(ServerPlayer player, ItemStack saber, Pool pool, float drain, Vec3 contact, int colour, long now, SaberBalance b) {
		if (drain(player, pool, drain, now)) {
			guardBreak(player, saber, pool, b, now, colour);
		} else {
			EffectEvents.toTracking(player, SaberEffects.CLASH, contact, player.getLookAngle(), player.getId(), drain, colour);
		}
	}

	/** Straight back at the shooter's eyes, or back along the bolt's path when there is no shooter. */
	private static Vec3 returnDirection(ServerPlayer player, @Nullable Entity shooter, Vec3 contact, Vec3 velocity) {
		return shooter != null && shooter.isAlive() && shooter != player
				? shooter.getEyePosition().subtract(contact).normalize()
				: velocity.scale(-1).normalize();
	}

	/**
	 * Toward the crosshair (inside a boss arena, so a parried lance can be sent into a pylon focus):
	 * at the centre of the visible entity nearest the crosshair within {@code degrees}, else straight
	 * along the look direction. Never more than {@code degrees} off the crosshair.
	 */
	static Vec3 crosshairAim(ServerPlayer player, Vec3 contact, float degrees) {
		Vec3 look = player.getLookAngle().normalize();
		Vec3 eye = player.getEyePosition();
		double cos = Math.cos(Math.toRadians(Math.max(0f, degrees)));
		double reach = 48.0;
		Entity best = null;
		double bestDot = cos;

		for (Entity entity : player.level().getEntities(player, player.getBoundingBox().expandTowards(look.scale(reach)).inflate(4.0),
				e -> e.isAlive() && !e.isSpectator() && !(e instanceof Projectile) && !(e instanceof ItemEntity))) {
			Vec3 to = entity.getBoundingBox().getCenter().subtract(eye);
			double length = to.length();

			if (length < 1.0e-3 || length > reach) {
				continue;
			}

			double dot = to.scale(1.0 / length).dot(look);

			if (dot > bestDot && player.level().clip(new ClipContext(eye, entity.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
				bestDot = dot;
				best = entity;
			}
		}

		if (best == null) {
			return look;
		}

		Vec3 dir = best.getBoundingBox().getCenter().subtract(contact);
		return dir.lengthSqr() < 1.0e-6 ? look : dir.normalize();
	}

	/**
	 * Where an unparried bolt goes: within the form's aim (Soresu), {@code deflection_control}'s cone
	 * or the Reliquary emitter's {@code reliquaryDeflectDegrees} around the crosshair, whichever is
	 * tightest, otherwise a random arc.
	 */
	private static Vec3 deflectDirection(ServerPlayer player, SaberForm.Modifiers form, SaberBalance b, boolean reliquary) {
		float aim = form.deflectAim();

		if (SaberPassives.has(player, SaberPassives.DEFLECTION_CONTROL)) {
			aim = aim < 0f ? b.deflectionControlDegrees : Math.min(aim, b.deflectionControlDegrees);
		}

		if (reliquary) {
			aim = aim < 0f ? b.reliquaryDeflectDegrees : Math.min(aim, b.reliquaryDeflectDegrees);
		}

		float pitchSpread = aim >= 0f ? aim : b.deflectArcDegrees * 0.5f;
		float yawSpread = aim >= 0f ? aim : b.deflectArcDegrees;
		float pitch = player.getXRot() + (player.getRandom().nextFloat() * 2f - 1f) * pitchSpread;
		float yaw = player.getYRot() + (player.getRandom().nextFloat() * 2f - 1f) * yawSpread;
		return Vec3.directionFromRotation(Mth.clamp(pitch, -80f, 80f), yaw);
	}

	/**
	 * A bolt at a saber NPC (spec 10.7): a registered {@link SaberDuelist} whose profile
	 * {@code deflects}, {@code GUARDING}, facing the shooter within its {@code deflectConeDegrees} (either side),
	 * sends an ordinary bolt back at the shooter's chest within {@code deflectSpreadDegrees}
	 * ({@code npc_deflect}); a heavy bolt drains {@code sentinel.lanceGuardDrain} and is spent.
	 * Anything else, including every projectile that is not saber-deflectable, goes through.
	 */
	private static DeflectResult onNpcProjectile(Mob mob, Projectile projectile, DeflectableProjectile bolt) {
		DuelState state = NpcGuard.state(mob);

		if (!(mob instanceof SaberDuelist duelist) || state == null || !state.profile().deflects() || !bolt.saberDeflectable()
				|| duelist.guardState() != GuardState.GUARDING || ForceStatus.isGuardSuppressed(mob)) {
			return DeflectResult.NONE;
		}

		Vec3 velocity = bolt.deflectableVelocity();
		Entity shooter = bolt.deflectableShooter();
		Vec3 from = shooter != null && shooter != mob ? shooter.position() : mob.position().subtract(velocity.scale(8.0));

		// "Faces the shooter within 70°": the cone is that many degrees either side of its facing.
		if (!inFront(mob, from, duelist.deflectConeDegrees() * 2f)) {
			return DeflectResult.NONE;
		}

		Vec3 contact = boltContactPoint(mob, velocity);
		int colour = SaberComponents.config(duelist.duelSaber()).color().ordinal();

		if (projectile instanceof BlasterBolt blaster && blaster.heavy()) {
			NpcGuard.drain(mob, SentinelBalance.get().lanceGuardDrain);
			EffectEvents.toTracking(mob, SaberEffects.CLASH, contact, mob.getLookAngle(), mob.getId(), 1f, colour);
			return DeflectResult.BLOCKED;
		}

		projectile.setPos(contact);
		Vec3 aim = shooter != null && shooter.isAlive() && shooter != mob
				? shooter.getBoundingBox().getCenter().subtract(contact)
				: velocity.scale(-1);
		Vec3 dir = spread(aim.lengthSqr() < 1.0e-6 ? mob.getLookAngle() : aim.normalize(), duelist.deflectSpreadDegrees(), mob.getRandom());
		bolt.redirect(dir.scale(Math.max(0.5, velocity.length())), mob, false);
		EffectEvents.toTracking(mob, SaberEffects.NPC_DEFLECT, contact, dir, mob.getId(), 1f, colour);
		return DeflectResult.DEFLECTED;
	}

	/** {@code dir} turned by a random angle of at most {@code degrees}, evenly over the cone's disc. */
	static Vec3 spread(Vec3 dir, float degrees, RandomSource random) {
		if (degrees <= 0f) {
			return dir;
		}

		float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
		float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(-dir.y, -1.0, 1.0)));
		double radius = degrees * Math.sqrt(random.nextDouble());
		double angle = random.nextDouble() * Math.PI * 2.0;
		return Vec3.directionFromRotation(Mth.clamp(pitch + (float) (radius * Math.sin(angle)), -89f, 89f), yaw + (float) (radius * Math.cos(angle)));
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
			// Vigil (MILESTONE_7.md 7.2): a held full Watchman hilt's guard comes back faster.
			pool.stamina = Math.min(pool.max, pool.stamina + b.guardRegenPerSecond * form.regenMult() * HiltSetEffects.guardRegenMultiplier(held, b) / 20f);
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
		SaberStats stats = SaberPassives.serverStats(player, saber);
		Pool pool = pool(player, stats.maxStamina());
		long now = player.level().getGameTime();
		// Milestone 7 (6.3, 3.3): the Warden emitter and clan iron drain the guard less here too.
		float scaled = amount * SaberPassives.form(player).modifiers(b).drainMult() * SaberStaffs.drainMult(saber, b) * HiltTraits.guardDrainMultiplier(saber, stats, b);

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
	private static Vec3 boltContactPoint(LivingEntity defender, Vec3 velocity) {
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
