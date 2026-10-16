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
import com.oldrepublic.progression.Progression;
import com.oldrepublic.skill.NodeInfo;
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
 * power, or a power the player's alignment refuses ({@link AbilityCosts#refused}). Pressing another
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

	private static final Map<UUID, Held> HELD = new HashMap<>();

	/** Handles one slot key intent from the client. */
	public static void onInput(ServerPlayer player, int slot, AbilityAction action) {
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

		if (slot < 0 || slot >= Skills.slots(player)) {
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

		Held held = new Held(slot, abilityId);
		HELD.put(player.getUUID(), held);
		ability.onInput(player, AbilityAction.START);
		report(player, held, ability);
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

	/** The info payload: every ability's input style and info map, plus the node info sources. */
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
	}

	public static void clear() {
		HELD.clear();
	}

	private AbilityRuntime() {
	}
}
