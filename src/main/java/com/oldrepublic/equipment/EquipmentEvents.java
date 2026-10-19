package com.oldrepublic.equipment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.equipment.mod.ModEffects;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.registry.ModComponents;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Reacts to worn armour changing (spec section 9.2), through Fabric's
 * {@code ServerEntityEvents.EQUIPMENT_CHANGE} (there is no inventory-change event): recomputes the
 * player's max energy, keeps Iron Discipline's knockback modifier and the M4 plating and terrain
 * modifiers in step, reports each era piece put on to the quest hooks, and sends
 * {@code armour_set} once when a piece completes a set. When several
 * pieces land in the same tick (a command, a mission handing out a set), the event fires once per
 * slot after every slot is filled, so the flourish is sent at most once per player per tick.
 */
public final class EquipmentEvents {
	/**
	 * Changes seen in a player's first ticks are the worn armour being applied after a join or
	 * respawn, not an equip, so they never send the set flourish.
	 */
	private static final int SETTLE_TICKS = 5;
	/** Player to the game time of the last {@code armour_set} sent to them. */
	private static final Map<UUID, Long> LAST_SET = new HashMap<>();

	static void init() {
		ServerEntityEvents.EQUIPMENT_CHANGE.register(EquipmentEvents::onChange);
		ServerPlayerEvents.LEAVE.register(player -> LAST_SET.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_SET.clear());
	}

	private static void onChange(LivingEntity entity, EquipmentSlot slot, ItemStack previous, ItemStack current) {
		if (!isArmourSlot(slot)) {
			return;
		}

		EquipmentTraits before = previous.get(ModComponents.EQUIPMENT_TRAITS);
		EquipmentTraits after = current.get(ModComponents.EQUIPMENT_TRAITS);

		if (before == null && after == null) {
			return;
		}

		SetBonusRules.Worn worn = ArmourSets.worn(entity);
		updateKnockback(entity, worn);
		updateGear(entity, worn);

		if (!(entity instanceof ServerPlayer player)) {
			return;
		}

		Progression.refreshModifiers(player);

		if (after == null) {
			return;
		}

		QuestHooks.onArmourEquipped(player, after.set());

		// This piece completed the set: 4/4 now, and the piece it replaced was not of the same set.
		boolean completed = worn.full(after.set()) && (before == null || before.set() != after.set());

		if (completed && player.tickCount > SETTLE_TICKS && firstSetThisTick(player)) {
			EffectEvents.toPlayer(player, EquipmentEffects.ARMOUR_SET, player.position(), null, player.getId(), 1f, after.set().ordinal());
		}
	}

	/**
	 * Adds or removes the transient Iron Discipline knockback modifier
	 * ({@link ArmourAttributes#SET_KNOCKBACK}) to match what {@code entity} wears.
	 */
	public static void updateKnockback(LivingEntity entity, SetBonusRules.Worn worn) {
		AttributeInstance knockback = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);

		if (knockback == null) {
			return;
		}

		float bonus = SetBonusRules.setKnockback(worn, EquipmentBalance.get());

		if (bonus > 0f) {
			knockback.addOrUpdateTransientModifier(new AttributeModifier(ArmourAttributes.SET_KNOCKBACK, bonus, AttributeModifier.Operation.ADD_VALUE));
		} else {
			knockback.removeModifier(ArmourAttributes.SET_KNOCKBACK);
		}
	}

	/**
	 * Keeps the transient Milestone 4 modifiers in step with what {@code entity} wears: the flat
	 * armour of its {@code mod_plating}s ({@link ArmourAttributes#MOD_PLATING}) and
	 * {@code mod_clan_iron_plating}s ({@link ArmourAttributes#MOD_CLAN_IRON}, MILESTONE_5.md 11.2) and
	 * Light on Your Feet's movement efficiency ({@link ArmourAttributes#SET_TERRAIN}), which keeps soul
	 * sand and honey from slowing the wearer.
	 */
	public static void updateGear(LivingEntity entity, SetBonusRules.Worn worn) {
		transientModifier(entity.getAttribute(Attributes.ARMOR), ArmourAttributes.MOD_PLATING,
				ModEffects.wornArmour(entity, EquipmentM4Balance.mods()));
		transientModifier(entity.getAttribute(Attributes.ARMOR), ArmourAttributes.MOD_CLAN_IRON,
				ModEffects.wornClanIronArmour(entity, CraftingBalance.get()));
		transientModifier(entity.getAttribute(Attributes.MOVEMENT_EFFICIENCY), ArmourAttributes.SET_TERRAIN,
				SetBonusRules.terrainEfficiency(worn, EquipmentM4Balance.get()));
	}

	/** Adds, updates or (at zero) removes one transient {@code ADD_VALUE} modifier. */
	private static void transientModifier(@Nullable AttributeInstance attribute, Identifier id, double amount) {
		if (attribute == null) {
			return;
		}

		if (amount != 0.0) {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		} else {
			attribute.removeModifier(id);
		}
	}

	/** Claims this tick's {@code armour_set} for {@code player}: true only for the first claim of the tick. */
	private static boolean firstSetThisTick(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long last = LAST_SET.put(player.getUUID(), now);
		return last == null || last != now;
	}

	private static boolean isArmourSlot(EquipmentSlot slot) {
		for (EquipmentSlot armour : ArmourSets.SLOTS) {
			if (armour == slot) {
				return true;
			}
		}

		return false;
	}

	private EquipmentEvents() {
	}
}
