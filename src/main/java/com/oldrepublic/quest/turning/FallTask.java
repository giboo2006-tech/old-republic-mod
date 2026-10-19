package com.oldrepublic.quest.turning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.AlignmentShift;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.vendor.hub.HubNpc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * The Fall's task, "A Gift for the Empire" (spec MILESTONE_5.md 8.3): Keeper Ansa lends the Jedi the Watch holocron at
 * the Greenwatch on Selvaren ({@code turn_ansa}, action {@code turn:holocron}); the Jedi carries it to Overseer Kaddor at
 * the Proving on Varkhast ({@code turn_kaddor}, action {@code turn:deliver}). Struck down, or without the holocron, the
 * step starts again at Ansa.
 *
 * <p>While the task is under way Ansa's and Kaddor's Chapter Four dialogues open the turning's instead
 * ({@link #redirect}); their option "Something else" ({@code turn:other}) opens the one the player asked for.
 */
final class FallTask {
	/** The Chapter Four dialogue each player was redirected from, for {@code turn:other}. */
	private static final Map<UUID, String> ORIGINAL = new HashMap<>();
	/** Players whose next Ansa or Kaddor dialogue opens unchanged (set by {@code turn:other}). */
	private static final Set<UUID> BYPASS = new HashSet<>();
	private static final String SOURCE = "turn.fall.task";

	/** Whether the player's Fall is at its task. */
	static boolean active(ServerPlayer p) {
		StoryData.Turning t = Story.get(p).turning;
		return t.kind() == TurningKind.FALL && t.stage() == TurningStage.TASK;
	}

	/** Whether the player's Fall is carrying the holocron (the only time a Watch holocron may be held). */
	static boolean carrying(ServerPlayer p) {
		return active(p) && Story.get(p).turning.task() == TurningRules.FALL_CARRYING;
	}

	/** Slow tick: a holocron no longer carried (dropped, stored, lost) goes back to Ansa. */
	static void tick(ServerPlayer p) {
		if (carrying(p) && !WatchHolocronItem.carried(p)) {
			restart(p);
		}
	}

	/** The step starts again at Ansa: every copy of the holocron goes back to her. */
	static void restart(ServerPlayer p) {
		if (!active(p)) {
			return;
		}

		WatchHolocronItem.removeAll(p);
		StoryData data = Story.get(p);

		if (data.turning.task() != TurningRules.FALL_TO_ANSA) {
			data.turning = data.turning.withTask(TurningRules.FALL_TO_ANSA);
			Story.commit(p, data);
			TurningService.say(p, Component.translatable("npc.oldrepublic.ansa.name"), "turning.oldrepublic.fall.taken_back");
		}
	}

	// ---------------------------------------------------------------- Ansa and Kaddor

	/**
	 * The redirect of {@code c4_ansa}, {@code c4_ansa_meet}, {@code c4_kaddor} and {@code c4_kaddor_meet}: while the Fall
	 * is at its task, {@code turn_ansa} or {@code turn_kaddor}; else (or once after {@code turn:other}) no change.
	 */
	static @Nullable String redirect(ServerPlayer p, String id) {
		if (BYPASS.remove(p.getUUID()) || !active(p)) {
			return null;
		}

		ORIGINAL.put(p.getUUID(), id);
		return id.startsWith("c4_ansa") ? "turn_ansa" : "turn_kaddor";
	}

	/** {@code turn:other}: the Chapter Four dialogue the player was redirected from, unchanged. */
	static void other(ServerPlayer p, @Nullable Entity speaker) {
		String fallback = speaker instanceof HubNpc npc && "kaddor".equals(npc.variant()) ? "c4_kaddor" : "c4_ansa";
		String original = ORIGINAL.getOrDefault(p.getUUID(), fallback);
		BYPASS.add(p.getUUID());

		if (!Dialogues.open(p, OldRepublicMod.id(original), speaker)) {
			BYPASS.remove(p.getUUID());
		}
	}

	/** {@code turn:holocron}: Ansa lends the holocron (on Selvaren, with room in the inventory). */
	static void giveHolocron(ServerPlayer p) {
		if (!active(p) || Story.get(p).turning.task() != TurningRules.FALL_TO_ANSA) {
			return;
		}

		if (Planets.Planet.of(p.level().dimension()) != Planets.Planet.SELVAREN) {
			return;
		}

		ItemStack stack = new ItemStack(TurningItems.WATCH_HOLOCRON);
		StoryData data = Story.get(p);
		// The task is set first: the holocron's own check removes a copy held by anyone not carrying it.
		data.turning = data.turning.withTask(TurningRules.FALL_CARRYING);
		Story.commit(p, data);

		if (!p.getInventory().add(stack)) {
			data = Story.get(p);
			data.turning = data.turning.withTask(TurningRules.FALL_TO_ANSA);
			Story.commit(p, data);
			p.sendOverlayMessage(Component.translatable("turning.oldrepublic.fall.no_room"));
			return;
		}

		p.sendSystemMessage(Component.translatable("turning.oldrepublic.fall.holocron_given"));
	}

	/** The {@code turn:holocron} condition: the player carries the holocron for the Fall. */
	static @Nullable String holocronReason(ServerPlayer p) {
		return carrying(p) && WatchHolocronItem.carried(p) ? null : "turning.oldrepublic.fall.no_holocron";
	}

	/**
	 * {@code turn:deliver}: Kaddor takes the holocron. 1,500 XP, Sith Empire +100, Jedi Order -150, dark -5, entry
	 * {@code a_gift_for_the_empire}; Saelen will come (the confrontation).
	 */
	static void deliver(ServerPlayer p) {
		if (!carrying(p) || !WatchHolocronItem.carried(p)) {
			return;
		}

		TurningBalance b = TurningService.balance();
		WatchHolocronItem.removeAll(p);
		StoryData data = Story.get(p);
		data.turning = data.turning.withStage(TurningKind.FALL, TurningStage.CONFRONTATION).withTask(TurningRules.CONFRONT_WAITING);
		Story.commit(p, data);

		Progression.addXp(p, b.taskXp, "turning");
		Identity.changeReputation(p, Faction.SITH_EMPIRE, b.taskRepGain, SOURCE, true);
		Identity.changeReputation(p, Faction.JEDI_ORDER, b.taskRepLoss, SOURCE, true);
		Identity.shift(p, new AlignmentShift(SOURCE, b.fallDark, AlignmentShift.Kind.MAJOR, "turning.oldrepublic.reason.fall_task"));
		TurningService.entry(p, JournalEntries.A_GIFT_FOR_THE_EMPIRE);
		p.sendSystemMessage(Component.translatable("turning.oldrepublic.fall.delivered"));
		Confrontations.waitFor(p);
	}

	static void forget(UUID player) {
		ORIGINAL.remove(player);
		BYPASS.remove(player);
	}

	static void clear() {
		ORIGINAL.clear();
		BYPASS.clear();
	}

	private FallTask() {
	}
}
