package com.oldrepublic.crafting;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.crafting.bench.BenchResult;
import com.oldrepublic.crafting.bench.BenchService;
import com.oldrepublic.crafting.bench.EngineeringBenchMenu;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.economy.Credits;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The trainers' dialogue conditions and actions (spec MILESTONE_4.md 8.10), registered by W5:
 * <ul>
 * <li>condition {@code discipline:has} (a discipline id, or {@code any});</li>
 * <li>condition {@code discipline:level} ({@code "<id> <n>"}: at least level n);</li>
 * <li>action {@code discipline:unlearn <id>}: drops the discipline for {@code unlearnCost} credits,
 * refused while a craft is running;</li>
 * <li>action {@code trainer:commission [<id>]}: opens the commission menu for that discipline (any
 * discipline without an argument).</li>
 * </ul>
 */
final class CraftingDialogue {
	private static final String REASON = "crafting.oldrepublic.condition.";

	static void init() {
		DialogueConditions.register("discipline:has", CraftingDialogue::has);
		DialogueConditions.register("discipline:level", CraftingDialogue::level);
		DialogueActions.registerArg("discipline:unlearn", (p, speaker, arg) -> unlearn(p, arg));
		DialogueActions.register("trainer:commission", (p, speaker) -> EngineeringBenchMenu.openCommission(p, speaker, null));
		DialogueActions.registerArg("trainer:commission", (p, speaker, arg) -> EngineeringBenchMenu.openCommission(p, speaker, Discipline.byId(arg.trim())));
	}

	private static @Nullable String has(ServerPlayer p, @Nullable Entity speaker, String arg) {
		String id = arg.trim();

		if ("any".equals(id)) {
			return CraftingAttachments.get(p).disciplines().isEmpty() ? REASON + "any" : null;
		}

		Discipline d = Discipline.byId(id);
		return d != null && Crafting.has(p, d) ? null : REASON + "has|@discipline.oldrepublic." + id;
	}

	private static @Nullable String level(ServerPlayer p, @Nullable Entity speaker, String arg) {
		String[] parts = arg.trim().split("[\\s,:]+");
		Discipline d = parts.length > 0 ? Discipline.byId(parts[0]) : null;
		int needed;

		try {
			needed = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
		} catch (NumberFormatException e) {
			needed = 1;
		}

		if (d == null) {
			return REASON + "any";
		}

		return Crafting.level(p, d) >= needed ? null : REASON + "level|@discipline.oldrepublic." + d.id() + "|" + needed;
	}

	/** {@code discipline:unlearn}: the trainer's reset (7.2). */
	static boolean unlearn(ServerPlayer p, String arg) {
		Discipline d = Discipline.byId(arg.trim());

		if (d == null || !Crafting.has(p, d)) {
			return false;
		}

		if (BenchService.isCrafting(p)) {
			p.sendSystemMessage(Component.translatable("crafting.oldrepublic.unlearn.busy").withStyle(ChatFormatting.RED));
			return false;
		}

		int cost = CraftingBalance.get().unlearnCost;

		if (!Credits.take(p, cost, true)) {
			p.sendSystemMessage(Component.translatable("crafting.oldrepublic.unlearn.credits", cost).withStyle(ChatFormatting.RED));
			return false;
		}

		BenchService.cancel(p, BenchResult.CANCELLED);
		Crafting.unlearn(p, d);
		p.sendSystemMessage(Component.translatable("crafting.oldrepublic.unlearn.done", Crafting.name(d), cost).withStyle(ChatFormatting.GOLD));
		return true;
	}

	private CraftingDialogue() {
	}
}
