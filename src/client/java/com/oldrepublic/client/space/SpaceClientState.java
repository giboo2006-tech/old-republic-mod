package com.oldrepublic.client.space;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.space.SpaceEffects;
import com.oldrepublic.space.network.ShipCombatStatusPayload;
import com.oldrepublic.space.network.SpaceEncounterPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * What the server last told this client about its fight in space (spec MILESTONE_5.md 5.10, 18.2):
 * the combat status of the seat it holds ({@code ship_combat_status}, every other tick while it
 * pilots or guns), its encounter or objective ({@code space_encounter}: the objective point and the
 * comm lines' keys) and the comm line on screen. A status the server stops sending goes stale
 * after a second, so a lost last packet never leaves the HUD up. Cleared on disconnect.
 */
final class SpaceClientState {
	/** A status older than this (client ticks in a level) is treated as the role's end. */
	private static final int STALE_TICKS = 20;
	/** How long a comm line stays up, fades included. */
	static final int COMM_TICKS = 100;
	/** The speakers' tag colours: a Hymn pilot, a pirate, a clan pilot, Varra, an ace. */
	private static final int[] SPEAKER_TINTS = {0xFFC8A0FF, 0xFFFFA860, 0xFFE8C46A, 0xFF8FE3C8, 0xFFFF7A66};

	/** A comm line: its speaker's tag, the line and the tick it arrived. */
	record Comm(Component tag, Component text, int tint, long start) {
	}

	private static ShipCombatStatusPayload status = ShipCombatStatusPayload.OFF;
	private static long statusTick = -STALE_TICKS - 1;
	private static SpaceEncounterPayload encounter = SpaceEncounterPayload.CLEARED;
	private static @Nullable Comm comm;
	/** Client ticks spent in a level, unpaused: the clock of the staleness and the comm lines. */
	private static long ticks;

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(ShipCombatStatusPayload.TYPE, (payload, context) -> {
			status = payload;
			statusTick = ticks;
		});
		ClientPlayNetworking.registerGlobalReceiver(SpaceEncounterPayload.TYPE, (payload, context) -> encounter = payload);
	}

	static void tick(Minecraft mc) {
		if (mc.level == null) {
			clear();
			return;
		}

		if (!mc.isPaused()) {
			ticks++;
		}
	}

	static long ticks() {
		return ticks;
	}

	/** The seat's status, or {@link ShipCombatStatusPayload#OFF} when it is stale or the role ended. */
	static ShipCombatStatusPayload status() {
		return ticks - statusTick > STALE_TICKS ? ShipCombatStatusPayload.OFF : status;
	}

	/** Whether the local player holds a seat with a combat HUD. */
	static boolean active() {
		return status().role() != ShipCombatStatusPayload.NONE;
	}

	static SpaceEncounterPayload encounter() {
		return encounter;
	}

	/** A {@code space_comm} event: line {@code index} of the encounter's comm list, spoken by {@code speaker}. */
	static void comm(int speaker, int index) {
		if (index < 0 || index >= encounter.comm().size()) {
			return;
		}

		int who = Math.clamp(speaker, 0, SPEAKER_TINTS.length - 1);
		Component name = who == SpaceEffects.SPEAKER_ACE && !encounter.ace().isEmpty()
				? Component.translatable(encounter.ace())
				: Component.translatable("space.oldrepublic.comm.speaker." + who);
		comm = new Comm(Component.translatable("space.oldrepublic.comm.format", name), Component.translatable(encounter.comm().get(index)),
				SPEAKER_TINTS[who], ticks);
	}

	/** The comm line on screen, or null. */
	static @Nullable Comm comm() {
		Comm line = comm;

		if (line != null && ticks - line.start() > COMM_TICKS) {
			comm = null;
			return null;
		}

		return line;
	}

	/** Disconnect or no level: everything forgotten. */
	static void clear() {
		status = ShipCombatStatusPayload.OFF;
		statusTick = -STALE_TICKS - 1;
		encounter = SpaceEncounterPayload.CLEARED;
		comm = null;
		ticks = 0;
	}

	private SpaceClientState() {
	}
}
