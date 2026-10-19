package com.oldrepublic.ability;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.network.AbilityInfoPayload;
import com.oldrepublic.ability.network.AbilityInputPayload;
import com.oldrepublic.ability.network.AbilityStatePayload;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.skill.NodeInfo;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillNode;
import com.oldrepublic.skill.SkillTrees;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the ability slots: routes {@link AbilityInputPayload}s from the slot keys to the
 * {@link ForceAbility} in that slot, keeps only one power charging or channelling at a time, cancels
 * powers on death, disconnect, dimension change and slot changes, and tells the owner when a power
 * starts or stops being active ({@link AbilityStatePayload}).
 *
 * <p>Rules: a START is refused (with a reason) for a locked or empty slot, an unknown or unlearned
 * power, a power the player's alignment refuses ({@link AbilityCosts#refused}) or a power another
 * feature forbids right now ({@link AbilityLocks#refusal}). Pressing another
 * slot while a power is active cancels that power (no cost below its minimum charge) and the press
 * is otherwise ignored. RELEASE and CANCEL only reach the power whose key started it.
 */
public final class AbilityRuntime {
	/** The slot key a player is holding (or last pressed) and what the owner was told. */
	private static final class Held {
		final int slot;
		final String abilityId;
		boolean keyDown = true;
		boolean reportedActive;

		Held(int slot, String abilityId) {
			this.slot = slot;
			this.abilityId = abilityId;
		}
	}

	/** Info source id of the per-player cost multipliers in the {@code ability_info} payload. */
	public static final String COST_MODIFIERS = "cost_modifiers";

	private static final Map<UUID, Held> HELD = new HashMap<>();
	/** The cost multipliers each player was last sent. */
	private static final Map<UUID, Map<String, Float>> LAST_COSTS = new HashMap<>();

	/** Handles one slot key intent from the client, or the Force Sense key ({@link AbilityInputPayload#SENSE_SLOT}). */
	public static void onInput(ServerPlayer player, int slot, AbilityAction action) {
		if (slot == AbilityInputPayload.SENSE_SLOT) {
			onSenseInput(player, action);
			return;
		}

		switch (action) {
			case START -> start(player, slot);
			case RELEASE, CANCEL -> {
				Held held = HELD.get(player.getUUID());

				if (held == null || held.slot != slot) {
					return;
				}

				held.keyDown = false;
				ForceAbility ability = Abilities.get(held.abilityId);

				if (ability != null) {
					ability.onInput(player, action);
				}

				report(player, held, ability);
			}
		}
	}

	private static void start(ServerPlayer player, int slot) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}

		if (isBusy(player)) {
			// Another power is charging or channelling: the press cancels it and does nothing else.
			cancelAll(player, true);
			return;
		}

		// A tap power's key may still be down; the new press replaces it.
		HELD.remove(player.getUUID());

		if (!Progression.get(player).isAwakened()) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.not_awakened"));
			return;
		}

		if (!Skills.isOpen(player, slot)) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.skill.slot.locked", slot + 1));
			return;
		}

		String abilityId = Skills.slot(player, slot);

		if (abilityId.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.skill.slot.empty", slot + 1));
			return;
		}

		ForceAbility ability = Abilities.get(abilityId);

		if (ability == null) {
			OldRepublicMod.LOGGER.warn("Slot {} of {} holds unregistered ability {}", slot + 1, player.getName().getString(), abilityId);
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.force.not_learned"));
			return;
		}

		if (!knows(player, abilityId)) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.force.not_learned"));
			return;
		}

		if (AbilityCosts.refused(player, abilityId)) {
			return;
		}

		Component locked = AbilityLocks.refusal(player, abilityId);

		if (locked != null) {
			player.sendOverlayMessage(locked);
			return;
		}

		Held held = new Held(slot, abilityId);
		HELD.put(player.getUUID(), held);
		ability.onInput(player, AbilityAction.START);
		report(player, held, ability);
	}

	/**
	 * The Force Sense key (spec 10.1 and 10.5): a press casts {@code force_sense}, which never takes
	 * a slot. Unlike a slot key it does not cancel an active power; Sense itself refuses while one is
	 * charging or channelling. Releases are ignored (a tap).
	 */
	private static void onSenseInput(ServerPlayer player, AbilityAction action) {
		if (action != AbilityAction.START) {
			return;
		}

		ForceAbility sense = Abilities.get(SkillIds.FORCE_SENSE);

		if (sense != null) {
			sense.onInput(player, AbilityAction.START);
		}
	}

	/** True when the player owns the ability's node (or, before the trees load, has the ability). */
	private static boolean knows(ServerPlayer player, String abilityId) {
		SkillNode node = SkillTrees.server().abilityNode(abilityId);
		return node != null ? Skills.rank(player, node.id()) > 0 : Progression.get(player).hasAbility(abilityId);
	}

	/** Sends a state change to the owner and forgets the entry once the key is up and the power idle. */
	private static void report(ServerPlayer player, Held held, ForceAbility ability) {
		boolean active = ability != null && ability.isActive(player);

		if (active != held.reportedActive) {
			held.reportedActive = active;
			ServerPlayNetworking.send(player, new AbilityStatePayload(held.slot, held.abilityId, active));
		}

		if (!active && !held.keyDown) {
			HELD.remove(player.getUUID(), held);
		}
	}

	/** True while any ability of the player is charging or channelling. */
	public static boolean isBusy(ServerPlayer player) {
		for (ForceAbility ability : Abilities.all()) {
			if (ability.isActive(player)) {
				return true;
			}
		}

		return false;
	}

	/** Cancels every active ability of the player. {@code feedback} plays the cancel effects. */
	public static void cancelAll(ServerPlayer player, boolean feedback) {
		for (ForceAbility ability : Abilities.all()) {
			if (ability.isActive(player)) {
				ability.cancel(player, feedback);
			}
		}

		Held held = HELD.remove(player.getUUID());

		if (held != null && held.reportedActive && feedback) {
			ServerPlayNetworking.send(player, new AbilityStatePayload(held.slot, held.abilityId, false));
		}
	}

	/** Cancels one ability if it is active (its slot changed). */
	public static void cancel(ServerPlayer player, String abilityId) {
		ForceAbility ability = Abilities.get(abilityId);

		if (ability != null && ability.isActive(player)) {
			ability.cancel(player, true);
		}

		Held held = HELD.get(player.getUUID());

		if (held != null && held.abilityId.equals(abilityId)) {
			HELD.remove(player.getUUID());

			if (held.reportedActive) {
				ServerPlayNetworking.send(player, new AbilityStatePayload(held.slot, held.abilityId, false));
			}
		}
	}

	/**
	 * The info payload for {@code player}: every ability's input style and info map, the node info
	 * sources, and the player's own {@link #COST_MODIFIERS} entry.
	 */
	public static AbilityInfoPayload infoPayload(ServerPlayer player) {
		Map<String, Float> costs = costModifiers(player);
		LAST_COSTS.put(player.getUUID(), costs);
		Map<String, AbilityInfoPayload.Entry> entries = new LinkedHashMap<>(infoPayload().entries());
		entries.put(COST_MODIFIERS, new AbilityInfoPayload.Entry(-1, costs));
		return new AbilityInfoPayload(entries);
	}

	/**
	 * Per ability, the cost multiplier other features apply for {@code player} right now
	 * ({@code ForceModifiers}: armour traits, carried holocrons, {@code watchers_insight};
	 * {@code ForceReactions}: forms). The HUD and the Force screen multiply their cost preview by it.
	 * Rounded to thousandths so float noise never triggers a resend.
	 */
	public static Map<String, Float> costModifiers(ServerPlayer player) {
		Map<String, Float> costs = new LinkedHashMap<>();

		for (ForceAbility ability : Abilities.all()) {
			float mult = ForceModifiers.cost(player, ability.id()) * ForceReactions.costMultiplier(player, ability.id());
			costs.put(ability.id(), Math.round(mult * 1000f) / 1000f);
		}

		return costs;
	}

	/**
	 * Re-sends the info payload to {@code player} when their {@link #costModifiers} changed since the
	 * last send (called every 20 ticks by the skills feature).
	 */
	public static void refreshCostModifiers(ServerPlayer player) {
		if (!costModifiers(player).equals(LAST_COSTS.get(player.getUUID()))) {
			ServerPlayNetworking.send(player, infoPayload(player));
		}
	}

	/** The info payload shared by every player: abilities and node info sources. */
	public static AbilityInfoPayload infoPayload() {
		Map<String, AbilityInfoPayload.Entry> entries = new LinkedHashMap<>();

		for (ForceAbility ability : Abilities.all()) {
			try {
				entries.put(ability.id(), new AbilityInfoPayload.Entry(ability.inputStyle().ordinal(), Map.copyOf(ability.info())));
			} catch (RuntimeException e) {
				OldRepublicMod.LOGGER.error("Ability {} failed to report its info", ability.id(), e);
			}
		}

		NodeInfo.snapshot().forEach((id, values) -> entries.putIfAbsent(id, new AbilityInfoPayload.Entry(-1, values)));
		return new AbilityInfoPayload(entries);
	}

	/** Watches active abilities so the owner hears when a charge or channel ends on the server's side. */
	public static void tick(MinecraftServer server) {
		if (HELD.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, Held>> it = HELD.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<UUID, Held> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player == null) {
				it.remove();
				continue;
			}

			Held held = entry.getValue();
			ForceAbility ability = Abilities.get(held.abilityId);
			boolean active = ability != null && ability.isActive(player);

			if (active != held.reportedActive) {
				held.reportedActive = active;
				ServerPlayNetworking.send(player, new AbilityStatePayload(held.slot, held.abilityId, active));
			}

			if (!active && !held.keyDown) {
				it.remove();
			}
		}
	}

	/** Forgets a player (disconnect) without feedback. */
	public static void forget(ServerPlayer player) {
		cancelAll(player, false);
		LAST_COSTS.remove(player.getUUID());
	}

	public static void clear() {
		HELD.clear();
		LAST_COSTS.clear();
	}

	private AbilityRuntime() {
	}
}
