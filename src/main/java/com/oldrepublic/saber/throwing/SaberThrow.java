package com.oldrepublic.saber.throwing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.InputStyle;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.SaberForms;
import com.oldrepublic.saber.SaberPassives;
import com.oldrepublic.saber.SaberTechniques;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Saber Throw ({@code saber_throw}, spec section 8.3): a tap ability on the ability framework.
 * The inventory stack never leaves the inventory; while a {@link ThrownSaber} flies, the stack
 * carries the throw's id in {@code saber_thrown}. Every way a throw can end (catch, timeout, the
 * thrower dying, logging out, changing dimension, a server stop) clears that component on the
 * stack with the same id, and a stack whose throw no longer exists is cleared from
 * {@code inventoryTick} after {@code orphanClearTicks}. No item is ever created, so nothing can be
 * duplicated. The cooldown starts at the catch.
 */
public final class SaberThrow implements ForceAbility {
	public static final String ID = "saber_throw";

	public static final ResourceKey<EntityType<?>> THROWN_SABER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("thrown_saber"));
	public static final EntityType<ThrownSaber> THROWN_SABER = Registry.register(BuiltInRegistries.ENTITY_TYPE, THROWN_SABER_KEY,
			EntityType.Builder.<ThrownSaber>of(ThrownSaber::new, MobCategory.MISC)
					.sized(0.6f, 0.6f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.alwaysUpdateVelocity(true)
					.noSave()
					.fireImmune()
					.build(THROWN_SABER_KEY));

	/** {@code data/oldrepublic/damage_type/thrown_saber.json}. */
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, OldRepublicMod.id("thrown_saber"));

	/** Throws in flight by throw id. */
	private static final Map<UUID, ThrownSaber> ACTIVE = new HashMap<>();
	/** Throw ids seen on a stack with no matching entity, with the game time first seen. */
	private static final Map<UUID, Long> ORPHANS = new HashMap<>();

	@Override
	public String id() {
		return ID;
	}

	@Override
	public InputStyle inputStyle() {
		return InputStyle.TAP;
	}

	@Override
	public void onInput(ServerPlayer p, AbilityAction action) {
		if (action == AbilityAction.START) {
			throwSaber(p);
		}
	}

	/** A throw in flight is not a charge or channel: nothing to cancel. */
	@Override
	public void cancel(ServerPlayer p, boolean feedback) {
	}

	@Override
	public boolean isActive(ServerPlayer p) {
		return false;
	}

	/**
	 * Keys: {@code cost}, {@code cooldown_s}, {@code range}, {@code speed}, {@code damage_pct},
	 * {@code max_targets}, {@code block_drain}, {@code ricochet_range}, {@code ricochet_bounces},
	 * {@code ricochet_falloff_pct}, {@code rending_range}, {@code rending_speed},
	 * {@code rending_damage_pct}, {@code rending_stagger_s}, {@code mastery_range_pct}.
	 */
	@Override
	public Map<String, Float> info() {
		SaberThrowBalance b = SaberThrowBalance.get();
		Map<String, Float> info = new LinkedHashMap<>();
		info.put("cost", b.cost);
		info.put("cooldown_s", b.cooldownTicks / 20f);
		info.put("range", b.range);
		info.put("speed", b.speed);
		info.put("damage_pct", b.damageMult * 100f);
		info.put("max_targets", (float) b.maxTargets);
		info.put("block_drain", b.blockDrainPlayer);
		info.put("ricochet_range", b.ricochetRange);
		info.put("ricochet_bounces", (float) b.ricochetBounces);
		info.put("ricochet_falloff_pct", b.ricochetFalloff * 100f);
		info.put("rending_range", b.rendingRange);
		info.put("rending_speed", b.rendingSpeed);
		info.put("rending_damage_pct", b.damageMult * b.rendingDamageMult * 100f);
		info.put("rending_stagger_s", b.rendingStaggerTicks / 20f);
		info.put("mastery_range_pct", b.masteryRangePerRank * 100f);
		return info;
	}

	/** Throws the lit saber in the main hand. Returns false (with feedback) when refused. */
	public static boolean throwSaber(ServerPlayer player) {
		if (!ForcePowers.mayStart(player, ID)) {
			return false;
		}

		ItemStack saber = player.getMainHandItem();

		if (!SaberComponents.isUsable(saber) || SaberTechniques.isBusy(player) || SaberForms.isSwitching(player)) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.saber.throw_no_saber"));
			return false;
		}

		SaberThrowBalance b = SaberThrowBalance.get();

		if (!Progression.trySpendEnergy(player, b.cost * ForcePowers.costMultiplier(player, ID))) {
			return false;
		}

		ThrownSaber.Shape shape = ForcePowers.hasNode(player, Progression.get(player), SaberPassives.THROW_RICOCHET) ? ThrownSaber.Shape.RICOCHET
				: ForcePowers.hasNode(player, Progression.get(player), SaberPassives.THROW_RENDING) ? ThrownSaber.Shape.RENDING
				: ThrownSaber.Shape.STANDARD;
		boolean rending = shape == ThrownSaber.Shape.RENDING;
		float rangeMult = 1f + b.masteryRangePerRank * ForcePowers.masteryRank(player, ID);
		double range = (rending ? b.rendingRange : b.range) * rangeMult;
		double speed = rending ? b.rendingSpeed : b.speed;
		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * b.damageMult * (rending ? b.rendingDamageMult : 1f);

		UUID throwId = UUID.randomUUID();
		ThrownSaber entity = ThrownSaber.create(player, saber, throwId, shape, speed, range, damage);
		player.stopUsingItem();
		saber.set(SaberComponents.SABER_THROWN, throwId);
		ACTIVE.put(throwId, entity);
		player.level().addFreshEntity(entity);
		player.swing(InteractionHand.MAIN_HAND, true);
		EffectEvents.toTracking(player, SaberEffects.THROW, entity.position(), player.getLookAngle(), player.getId(), 1f, SaberEffects.THROW_START);
		return true;
	}

	/**
	 * Ends a throw: removes the entity and clears the stack's component. {@code caught} means the
	 * saber reached the hand (catch effect, mastery, quest progress); either way the cooldown starts
	 * if the thrower is still around.
	 */
	static void end(ThrownSaber entity, @Nullable ServerPlayer owner, boolean caught) {
		UUID throwId = entity.throwId();

		if (throwId != null) {
			ACTIVE.remove(throwId);
		}

		entity.discard();

		if (owner == null) {
			return;
		}

		if (throwId != null) {
			clearComponent(owner, throwId);
		}

		Progression.setCooldown(owner, ID, ForcePowers.cooldown(owner, ID, SaberThrowBalance.get().cooldownTicks));

		if (caught) {
			EffectEvents.toTracking(owner, SaberEffects.THROW_CATCH, owner.getEyePosition(), owner.getLookAngle(), owner.getId(), 1f, 0);
			ForcePowers.addMastery(owner, ID, entity.hostileHits());
			QuestHooks.onAbilityUsed(owner, ID, entity.hostileHits());
		}
	}

	/** Ends every throw of {@code player} and clears all their thrown components (death, logout, dimension change). */
	public static void recall(ServerPlayer player) {
		for (ThrownSaber entity : new ArrayList<>(ACTIVE.values())) {
			if (player.getUUID().equals(entity.ownerId())) {
				end(entity, player, false);
			}
		}

		clearAll(player);
	}

	/** Server stop: every throw ends. */
	public static void recallAll(MinecraftServer server) {
		for (ThrownSaber entity : new ArrayList<>(ACTIVE.values())) {
			UUID owner = entity.ownerId();
			end(entity, owner == null ? null : server.getPlayerList().getPlayer(owner), false);
		}

		ACTIVE.clear();
		ORPHANS.clear();
	}

	/** Whether a throw with this id is in flight. */
	public static boolean isInFlight(UUID throwId) {
		ThrownSaber entity = ACTIVE.get(throwId);
		return entity != null && !entity.isRemoved();
	}

	/**
	 * From {@code LightsaberItem#inventoryTick}: clears the component of a stack whose throw no
	 * longer exists once it has been seen that way for {@code orphanClearTicks}.
	 */
	public static void checkOrphan(ItemStack stack, ServerLevel level) {
		UUID throwId = stack.get(SaberComponents.SABER_THROWN);

		if (throwId == null) {
			return;
		}

		if (isInFlight(throwId)) {
			ORPHANS.remove(throwId);
			return;
		}

		long now = level.getGameTime();
		long since = ORPHANS.computeIfAbsent(throwId, id -> now);

		if (now - since >= SaberThrowBalance.get().orphanClearTicks) {
			stack.remove(SaberComponents.SABER_THROWN);
			ORPHANS.remove(throwId);
		}
	}

	/** Removes the component from the stack carrying {@code throwId}, wherever it is in the player's inventory. */
	public static boolean clearComponent(Player player, UUID throwId) {
		ORPHANS.remove(throwId);
		boolean found = false;

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);

			if (throwId.equals(stack.get(SaberComponents.SABER_THROWN))) {
				stack.remove(SaberComponents.SABER_THROWN);
				found = true;
			}
		}

		ItemStack carried = player.containerMenu.getCarried();

		if (throwId.equals(carried.get(SaberComponents.SABER_THROWN))) {
			carried.remove(SaberComponents.SABER_THROWN);
			found = true;
		}

		return found;
	}

	/** Removes every thrown component from the player's inventory. */
	public static void clearAll(Player player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			clear(player.getInventory().getItem(slot));
		}

		clear(player.containerMenu.getCarried());
	}

	private static void clear(ItemStack stack) {
		if (stack.has(SaberComponents.SABER_THROWN)) {
			stack.remove(SaberComponents.SABER_THROWN);
		}
	}

	/** Registers the ability. */
	public static void init() {
		Abilities.register(new SaberThrow());
	}
}
