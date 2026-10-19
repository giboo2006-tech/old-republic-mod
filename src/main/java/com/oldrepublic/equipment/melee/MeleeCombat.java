package com.oldrepublic.equipment.melee;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.CombatEvents;
import com.oldrepublic.combat.WeaponActions;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.equipment.EquipmentM4Balance;
import com.oldrepublic.equipment.GearEffects;
import com.oldrepublic.equipment.blaster.PlayerBolts;
import com.oldrepublic.equipment.mod.ModEffects;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.mixin.equipment.LivingEntityAttackStrengthAccessor;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberToggle;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The server side of the vibroweapons and the electrostaff (spec MILESTONE_4.md 5.2 and 5.3):
 * <ul>
 * <li>the guard: a raised vibroweapon blocks part of every melee hit from the front, paid from a
 * stamina pool with the M2 saber guard's numbers ({@code saber} section), synced to its owner for the
 * HUD ({@link #GUARD}); an empty pool breaks the guard; no bolt is ever blocked and nothing is
 * parried. A guarded saber blow notches a vibroweapon ({@code saberNotch} durability); the
 * electrostaff's emitters hold the blade and lose nothing;</li>
 * <li>the vibroblade's flurry: every third consecutive hit within {@code flurryWindow} ticks deals
 * {@code flurryBonus} more and leaves the attack cooldown charged;</li>
 * <li>the vibrosword's cleave: a fully charged hit also strikes up to {@code cleaveTargets} more
 * enemies within {@code cleaveRange} in front, at {@code cleaveMultiplier} of the swing;</li>
 * <li>the electrostaff: a sweep on every full swing; its charge (Weapon Action, {@code staffChargeTicks})
 * makes the next hit within {@code staffChargeWindow} shock ({@link ControlEffects#SHOCK}) and arc to
 * one more enemy; droids take ×{@code droidShockMultiplier} from shocks;</li>
 * <li>a saber parry against a vibroweapon notches it, and the {@code mod_shock_edge} chance to shock.</li>
 * </ul>
 * Every piece of state belongs to the weapon in hand and is dropped when it leaves the hand, on
 * death, logout and a change of level.
 */
public final class MeleeCombat {
	/** The reach modifier of the sword and staff ({@code ENTITY_INTERACTION_RANGE}, main hand). */
	public static final Identifier REACH_ID = OldRepublicMod.id("melee.reach");
	/** The lead's {@code oldrepublic:droids} entity tag. */
	public static final TagKey<EntityType<?>> DROIDS = TagKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("droids"));
	/** A restored attack ticker: far past any weapon's cooldown. */
	private static final int CHARGED_TICKER = 100;
	/** Arc of the cleave in front of the wielder, degrees. */
	private static final float CLEAVE_ARC = 120f;
	/** Knockback of a sweep, as vanilla's. */
	private static final double SWEEP_KNOCKBACK = 0.4;

	/** What the HUD needs of the vibroweapon guard. */
	public record GuardView(float stamina, float max, boolean broken) {
		public static final StreamCodec<ByteBuf, GuardView> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, GuardView::stamina,
				ByteBufCodecs.FLOAT, GuardView::max,
				ByteBufCodecs.BOOL, GuardView::broken,
				GuardView::new);
	}

	/** The vibroweapon guard stamina, synced to its owner only. Not persistent: it refills on login. */
	public static final AttachmentType<GuardView> GUARD = AttachmentRegistry.create(
			OldRepublicMod.id("vibro_guard"),
			builder -> builder.syncWith(GuardView.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** One player's melee state. */
	private static final class State {
		@Nullable ItemStack weapon;
		float stamina;
		float max;
		long lastDrain = Long.MIN_VALUE / 2;
		long brokenUntil;
		@Nullable GuardView sent;
		int chain;
		long lastHit = Long.MIN_VALUE / 2;
		/** The swing in progress (from the attack callback): target, tick, strength, flurry. */
		int swingTarget = -1;
		long swingTick = -1;
		float swingStrength;
		boolean swingFlurry;
		/** Electrostaff: charge start, or -1; the tick the charge loaded, or -1. */
		long chargeStart = -1;
		long chargedAt = -1;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** Players whose attack cooldown is restored at the end of this tick (a flurry hit). */
	private static final Set<UUID> RESTORE = new HashSet<>();

	/** Registers the Weapon Action handler and the attack, parry, tick and cleanup listeners. */
	public static void init() {
		WeaponActions.register(stack -> stack.getItem() instanceof ElectrostaffItem, MeleeCombat::onAction);
		AttackEntityCallback.EVENT.register(MeleeCombat::onAttack);
		CombatEvents.PARRY.register((defender, attacker, projectile) -> {
			if (!projectile && attacker != null) {
				onParried(attacker);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(MeleeCombat::tick);
		ServerPlayerEvents.LEAVE.register(MeleeCombat::forget);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> forget(oldPlayer));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> forget(player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STATES.clear();
			RESTORE.clear();
		});
	}

	// ---------------------------------------------------------------- attacks

	/**
	 * Fabric's attack callback, at the head of {@code Player#attack} before the attack strength
	 * resets: records the swing's strength and advances the flurry chain.
	 */
	private static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator() || !(target instanceof LivingEntity)
				|| !(player.getMainHandItem().getItem() instanceof VibroWeaponItem item)) {
			return InteractionResult.PASS;
		}

		swing(serverPlayer, item.kind(), target.getId(), player.getAttackStrengthScale(0.5f), level.getGameTime());
		return InteractionResult.PASS;
	}

	/** Records a swing at {@code targetId} with {@code strength}, advancing the flurry chain (tests call it too). */
	public static void swing(ServerPlayer player, VibroWeaponItem.Kind kind, int targetId, float strength, long now) {
		MeleeBalance b = MeleeBalance.get();
		State state = state(player);
		state.chain = MeleeRules.nextChain(state.chain, state.lastHit, now, b);
		state.lastHit = now;
		state.swingTarget = targetId;
		state.swingTick = now;
		state.swingStrength = strength;
		state.swingFlurry = kind == VibroWeaponItem.Kind.BLADE && MeleeRules.isFlurry(state.chain, b);
	}

	/**
	 * Damage added to the swing at {@code target} recorded this tick: the flurry bonus, and the extra
	 * a charged staff's shock does to a droid (the item's damage bonus; tests read it too).
	 */
	public static float attackBonus(ServerPlayer player, Entity target, VibroWeaponItem.Kind kind) {
		State state = STATES.get(player.getUUID());

		if (state == null || state.swingTarget != target.getId() || state.swingTick != player.level().getGameTime()) {
			return 0f;
		}

		MeleeBalance b = MeleeBalance.get();
		float bonus = state.swingFlurry ? b.flurryBonus : 0f;

		if (kind == VibroWeaponItem.Kind.STAFF && charged(state, player.level().getGameTime(), b) && isDroid(target)) {
			float swing = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
			bonus += MeleeRules.shockDamage(swing, true, b) - swing;
		}

		return bonus;
	}

	/**
	 * After a hit landed with a vibroweapon: flurry, cleave, the staff's sweep and shock, the shock
	 * edge, and the Sunstone Edge's fire (MILESTONE_5.md 11.2).
	 */
	static void onHit(ServerPlayer player, ItemStack weapon, VibroWeaponItem.Kind kind, LivingEntity target) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		MeleeBalance b = MeleeBalance.get();
		State state = state(player);
		boolean thisSwing = state.swingTarget == target.getId() && state.swingTick == now;
		boolean full = thisSwing && MeleeRules.fullyCharged(state.swingStrength, b);
		int burn = ModEffects.sunstoneFireTicks(weapon, CraftingBalance.get());

		if (burn > 0 && target.isAlive()) {
			target.igniteForTicks(burn);
		}

		if (thisSwing && state.swingFlurry) {
			state.swingFlurry = false;
			RESTORE.add(player.getUUID());
			EffectEvents.around(level, GearEffects.VIBRO_HIT, target.getEyePosition().add(0, -0.3, 0), player.getLookAngle(), player.getId(), 1f, GearEffects.VIBRO_FLURRY);
		}

		if (kind == VibroWeaponItem.Kind.SWORD && full) {
			cleave(player, level, target, b);
		}

		if (kind == VibroWeaponItem.Kind.STAFF) {
			if (full && player.onGround() && !player.isSprinting()) {
				sweep(player, level, target, b);
			}

			if (charged(state, now, b)) {
				state.chargedAt = -1;
				shock(player, target, b);
				arc(player, level, target, b);
				return;
			}
		}

		float chance = ModEffects.shockChance(weapon, EquipmentM4Balance.mods());

		if (chance > 0f && player.getRandom().nextFloat() < chance) {
			shock(player, target, EquipmentM4Balance.mods().shockTicks);
		}
	}

	/** The extra cleave hits: the nearest enemies in front of the wielder, never allies or bystanders. */
	public static int cleave(ServerPlayer player, ServerLevel level, LivingEntity struck, MeleeBalance b) {
		float damage = MeleeRules.cleaveDamage((float) player.getAttributeValue(Attributes.ATTACK_DAMAGE), b);
		double range = b.cleaveRange;
		AABB box = player.getBoundingBox().inflate(range + 1.0);
		List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e != struck && e.isAlive()
				&& e.distanceTo(player) <= range + e.getBbWidth() / 2 && SaberGuard.inFront(player, e.position(), CLEAVE_ARC) && hittable(player, e));
		targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		int hit = 0;

		for (LivingEntity target : targets) {
			if (hit >= b.cleaveTargets) {
				break;
			}

			if (target.hurtServer(level, player.damageSources().playerAttack(player), damage)) {
				hit++;
			}
		}

		if (hit > 0) {
			EffectEvents.around(level, GearEffects.VIBRO_HIT, player.getEyePosition().add(player.getLookAngle().scale(1.2)).add(0, -0.4, 0),
					player.getLookAngle(), player.getId(), hit, GearEffects.VIBRO_CLEAVE);
		}

		return hit;
	}

	/** The staff's sweep: the vanilla pattern, enemies beside the struck one take a light hit and a shove. */
	private static void sweep(ServerPlayer player, ServerLevel level, LivingEntity struck, MeleeBalance b) {
		AABB box = struck.getBoundingBox().inflate(1.0, 0.25, 1.0);
		double reach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) + 0.5;
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e != struck && e.isAlive()
				&& player.distanceToSqr(e) < reach * reach && hittable(player, e));

		for (LivingEntity e : near) {
			Vec3 away = e.position().subtract(player.position()).horizontal();

			double strength = SWEEP_KNOCKBACK * (1.0 - Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0));

			if (away.lengthSqr() > 1.0e-4 && strength > 0.0) {
				Vec3 impulse = away.normalize().scale(strength);
				e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(impulse.x, Math.min(0.4, strength), impulse.z));
				e.hurtMarked = true;
			}

			e.hurtServer(level, player.damageSources().playerAttack(player), b.staffSweepDamage);
		}

		EffectEvents.toTracking(player, GearEffects.ELECTROSTAFF, struck.getEyePosition().add(0, -0.4, 0), player.getLookAngle(), player.getId(), 1f,
				GearEffects.STAFF_SWEEP);
	}

	/** Shocks {@code target} for the staff's shock length. */
	private static void shock(ServerPlayer player, LivingEntity target, MeleeBalance b) {
		shock(player, target, b.staffShockTicks);
	}

	/**
	 * A shock by {@code player}: slowed and any wind-up broken ({@link ControlEffects#SHOCK}, the
	 * Lightning shock's rule), with the electrostaff's crackle. Also the stormglass lens's finisher
	 * shock (MILESTONE_5.md 11.2).
	 */
	public static void shock(ServerPlayer player, LivingEntity target, int ticks) {
		int duration = ControlEffects.diminished(target, ticks, ControlEffects.SHOCK);

		if (duration > 0) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, duration, 0), player);
			ForceReactions.interruptWindup(target, player, ForceReactions.CAUSE_SHOCK);
		}

		EffectEvents.toTracking(player, GearEffects.ELECTROSTAFF, target.getEyePosition().add(0, -0.3, 0), null, target.getId(), 1f, GearEffects.STAFF_SHOCK);
	}

	/** The charge arcs to the nearest other enemy within {@code staffArcRange} of the struck one. */
	private static void arc(ServerPlayer player, ServerLevel level, LivingEntity struck, MeleeBalance b) {
		AABB box = struck.getBoundingBox().inflate(b.staffArcRange);
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e != struck && e.isAlive()
				&& e.distanceTo(struck) <= b.staffArcRange && hittable(player, e));
		near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(struck)));

		if (near.isEmpty()) {
			return;
		}

		LivingEntity next = near.get(0);
		next.hurtServer(level, player.damageSources().indirectMagic(player, player), MeleeRules.shockDamage(b.staffArcDamage, isDroid(next), b));
		shock(player, next, b);
	}

	/** Whether an area hit of {@code player}'s may land on {@code e}: never allies, pets or shielded bystanders. */
	private static boolean hittable(ServerPlayer player, LivingEntity e) {
		return e.isAttackable() && !PlayerBolts.passesThrough(player, e) && !Attitudes.shieldedFromArea(player, e);
	}

	/** Whether {@code entity} is a droid: the {@code droids} tag, or a droid entity id. */
	public static boolean isDroid(Entity entity) {
		return entity.is(DROIDS) || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().contains("droid");
	}

	/** A saber parried a melee blow: a vibroblade or vibrosword notches. */
	private static void onParried(LivingEntity attacker) {
		ItemStack weapon = attacker.getMainHandItem();

		if (!(weapon.getItem() instanceof VibroWeaponItem item) || item.kind() == VibroWeaponItem.Kind.STAFF
				|| !(attacker.level() instanceof ServerLevel level)) {
			return;
		}

		weapon.hurtAndBreak(MeleeBalance.get().saberNotch, attacker, InteractionHand.MAIN_HAND);
		EffectEvents.around(level, GearEffects.VIBRO_HIT, attacker.getEyePosition().add(attacker.getLookAngle().scale(0.6)).add(0, -0.3, 0),
				attacker.getLookAngle(), attacker.getId(), 1f, GearEffects.VIBRO_NOTCH);
	}

	// ---------------------------------------------------------------- the guard

	/**
	 * The guard's share of a hit on {@code victim}: the fraction of {@code amount} that goes through
	 * (1 when no guard stops it). Drains the guard stamina, breaks an emptied guard, and notches a
	 * vibroweapon that stops a saber. Called once per hit from the equipment damage modifier.
	 */
	public static float guardMultiplier(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof ServerPlayer player) || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof VibroWeaponItem item)
				|| !(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker || !isMeleeBlow(source)) {
			return 1f;
		}

		SaberBalance sb = SaberToggle.balance();
		long now = player.level().getGameTime();
		State state = state(player);

		if (now < state.brokenUntil || !SaberGuard.inFront(player, attacker.position(), sb.guardArcDegrees)) {
			return 1f;
		}

		MeleeBalance b = MeleeBalance.get();
		ItemStack weapon = player.getUseItem();
		ServerLevel level = player.level();
		Vec3 contact = SaberGuard.contactPoint(player, attacker.position());
		boolean saber = SaberComponents.isUsable(attacker.getMainHandItem());
		syncMax(state, sb);
		state.stamina = Math.max(0f, state.stamina - MeleeRules.guardDrain(amount, sb.meleeDrainBase, sb.meleeDrainPerDamage));
		state.lastDrain = now;

		if (saber && item.kind() == VibroWeaponItem.Kind.STAFF) {
			EffectEvents.toTracking(player, GearEffects.ELECTROSTAFF, contact, attacker.getLookAngle(), player.getId(), 1f, GearEffects.STAFF_BLOCK_SABER);
		} else if (saber) {
			weapon.hurtAndBreak(b.saberNotch, player, InteractionHand.MAIN_HAND);
			EffectEvents.around(level, GearEffects.VIBRO_HIT, contact, attacker.getLookAngle(), player.getId(), 1f, GearEffects.VIBRO_NOTCH);
		} else {
			EffectEvents.around(level, GearEffects.VIBRO_HIT, contact, attacker.getLookAngle(), player.getId(),
					state.max <= 0f ? 0f : state.stamina / state.max, GearEffects.VIBRO_GUARD);
		}

		if (state.stamina <= 0f) {
			breakGuard(player, weapon, state, sb, now);
		}

		sync(player, state, now);
		return MeleeRules.guardThrough(item.kind(), b);
	}

	/** A hand-to-hand blow (a player's or a mob's attack), not an explosion or a power that names its caster. */
	private static boolean isMeleeBlow(DamageSource source) {
		return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
	}

	private static void breakGuard(ServerPlayer player, ItemStack weapon, State state, SaberBalance sb, long now) {
		player.stopUsingItem();
		player.getCooldowns().addCooldown(weapon, sb.guardBreakTicks);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, sb.guardBreakTicks, 1));
		state.stamina = 0f;
		state.brokenUntil = now + sb.guardBreakTicks;
		EffectEvents.around(player.level(), GearEffects.VIBRO_HIT, player.getEyePosition().add(player.getLookAngle().scale(0.5)).add(0, -0.35, 0),
				player.getLookAngle(), player.getId(), 0f, GearEffects.VIBRO_NOTCH);
	}

	/** Guard stamina of {@code player} now (tests and the HUD's view). */
	public static float stamina(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state == null ? SaberToggle.balance().guardStamina : state.stamina;
	}

	// ---------------------------------------------------------------- the electrostaff charge

	/** Weapon Action with the electrostaff: a press starts the charge; it loads by itself. */
	static void onAction(ServerPlayer player, ItemStack held, boolean pressed) {
		if (!pressed) {
			return;
		}

		State state = state(player);
		long now = player.level().getGameTime();
		MeleeBalance b = MeleeBalance.get();

		if (state.chargeStart >= 0 || charged(state, now, b)) {
			return;
		}

		state.chargeStart = now;
		EffectEvents.toTracking(player, GearEffects.ELECTROSTAFF, player.getEyePosition(), player.getLookAngle(), player.getId(), 1f, GearEffects.STAFF_CHARGE);
	}

	private static boolean charged(State state, long now, MeleeBalance b) {
		return MeleeRules.staffChargeLive(state.chargedAt, now, b);
	}

	/** Whether {@code player}'s electrostaff is charged now (tests and commands). */
	public static boolean staffCharged(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state != null && charged(state, player.level().getGameTime(), MeleeBalance.get());
	}

	/** Test hook: the staff's charge loaded {@code ticksAgo} ticks ago. */
	public static void chargedSince(ServerPlayer player, long ticksAgo) {
		State state = state(player);
		state.chargeStart = -1;
		state.chargedAt = player.level().getGameTime() - ticksAgo;
	}

	// ---------------------------------------------------------------- the tick

	private static void tick(MinecraftServer server) {
		for (UUID id : RESTORE) {
			if (server.getPlayerList().getPlayer(id) instanceof ServerPlayer player) {
				((LivingEntityAttackStrengthAccessor) player).oldrepublic$setAttackStrengthTicker(CHARGED_TICKER);
			}
		}

		RESTORE.clear();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	private static void tickPlayer(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		ItemStack main = player.getMainHandItem();
		boolean holding = main.getItem() instanceof VibroWeaponItem;

		if (state == null) {
			if (!holding) {
				return;
			}

			state = state(player);
		}

		long now = player.level().getGameTime();
		MeleeBalance b = MeleeBalance.get();
		SaberBalance sb = SaberToggle.balance();

		if (state.weapon != main) {
			// Another weapon in hand: a staff charge is lost.
			endCharge(player, state);
			state.weapon = holding ? main : null;
		}

		if (state.chargeStart >= 0 && MeleeRules.staffCharged(state.chargeStart, now, b)) {
			state.chargeStart = -1;
			state.chargedAt = now;
			EffectEvents.toTracking(player, GearEffects.ELECTROSTAFF, player.getEyePosition(), player.getLookAngle(), player.getId(), 1f, GearEffects.STAFF_CHARGED);
		} else if (state.chargedAt >= 0 && !charged(state, now, b)) {
			endCharge(player, state);
		}

		syncMax(state, sb);
		boolean guarding = player.isUsingItem() && player.getUseItem().getItem() instanceof VibroWeaponItem;

		if (now >= state.brokenUntil && state.stamina < state.max && !guarding && now - state.lastDrain >= sb.guardRegenDelayTicks) {
			state.stamina = Math.min(state.max, state.stamina + sb.guardRegenPerSecond / 20f);
		}

		sync(player, state, now);

		if (!holding && state.stamina >= state.max && now >= state.brokenUntil && state.chargedAt < 0 && state.chargeStart < 0) {
			STATES.remove(player.getUUID());
			player.removeAttached(GUARD);
		}
	}

	/** Ends a staff charge that did not shock: the crackle stops. */
	private static void endCharge(ServerPlayer player, State state) {
		if (state.chargeStart >= 0 || state.chargedAt >= 0) {
			state.chargeStart = -1;
			state.chargedAt = -1;
			EffectEvents.toTracking(player, GearEffects.ELECTROSTAFF, player.getEyePosition(), player.getLookAngle(), player.getId(), 0f, GearEffects.STAFF_SPENT);
		}
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> {
			State state = new State();
			state.max = SaberToggle.balance().guardStamina;
			state.stamina = state.max;
			return state;
		});
	}

	private static void syncMax(State state, SaberBalance sb) {
		state.max = sb.guardStamina;
		state.stamina = Math.min(state.stamina, state.max);
	}

	/** Syncs the guard view when it changed: at once when broken or full, else every 5 ticks. */
	private static void sync(ServerPlayer player, State state, long now) {
		boolean broken = now < state.brokenUntil;
		GuardView sent = state.sent;
		boolean due = sent == null || sent.broken() != broken || sent.max() != state.max
				|| (sent.stamina() != state.stamina && (state.stamina >= state.max || state.stamina <= 0f || now % 5 == 0));

		if (due) {
			GuardView view = new GuardView(state.stamina, state.max, broken);
			state.sent = view;
			player.setAttached(GUARD, view);
		}
	}

	private static void forget(ServerPlayer player) {
		STATES.remove(player.getUUID());
		RESTORE.remove(player.getUUID());
		player.removeAttached(GUARD);
	}

	private MeleeCombat() {
	}
}
