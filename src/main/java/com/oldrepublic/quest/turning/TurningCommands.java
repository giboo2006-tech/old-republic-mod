package com.oldrepublic.quest.turning;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /oldrepublic turning ...} operator commands (spec MILESTONE_5.md 8), each with a single player argument:
 * <ul>
 * <li>{@code status <player>}: the turning's record, the band, the refusals and rank 4's first unmet requirement</li>
 * <li>{@code offer <player>}: the offer now, whatever the band (a Jedi is offered the Fall, a Sith the Return)</li>
 * <li>{@code advance <player>}: finishes the step under way (accepts an offer, completes a task, wins a confrontation by
 * letting the mentor go, holds the rite)</li>
 * <li>{@code rank4 <player>}: the rank 4 checklist</li>
 * <li>{@code ceremony <player>}: holds rank 4's ceremony now if every requirement is met (a Darth takes the first name)</li>
 * <li>{@code reset <player>}: forgets a questline under way and the taken blades (the identity choices stay)</li>
 * </ul>
 */
final class TurningCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((context, root) -> root.then(literal("turning")
				.then(sub("status", TurningCommands::status))
				.then(sub("offer", TurningCommands::offer))
				.then(sub("advance", TurningCommands::advance))
				.then(sub("rank4", TurningCommands::rank4))
				.then(sub("ceremony", TurningCommands::ceremony))
				.then(sub("reset", TurningCommands::reset))));
	}

	private interface Run {
		int run(CommandContext<CommandSourceStack> ctx, ServerPlayer p);
	}

	private static LiteralArgumentBuilder<CommandSourceStack> sub(String name, Run run) {
		return literal(name).then(argument("player", EntityArgument.player()).executes(ctx -> run.run(ctx, player(ctx))));
	}

	private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return EntityArgument.getPlayer(ctx, "player");
	}

	private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
		StoryData.Turning t = Story.get(p).turning;
		long day = Clocks.day(p.level().getServer());
		String blocker = RankFour.blocker(p);
		String text = p.getName().getString() + ": " + t.kind().id() + " " + t.stage().id() + " task " + t.task() + ", band since day " + t.bandSince()
				+ ", refusals " + t.refusals() + ", re-offer day " + t.reofferDay() + ", turned day " + t.turnedDay() + " (today " + day + ")"
				+ ", confrontation retry at " + Confrontations.retryAt(p) + ", rank 4: " + (blocker == null ? "ready" : blocker);
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static int offer(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
		Path path = Identity.path(p);
		TurningKind kind = path == Path.JEDI ? TurningKind.FALL : path == Path.SITH ? TurningKind.RETURN : TurningKind.NONE;

		if (kind == TurningKind.NONE || !TurningOffers.offer(p, kind)) {
			ctx.getSource().sendFailure(Component.literal(p.getName().getString() + " cannot be offered a turning now"));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.literal("Offered the " + kind.id() + " to " + p.getName().getString()), true);
		return 1;
	}

	private static int advance(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
		StoryData.Turning t = Story.get(p).turning;

		switch (t.stage()) {
			case OFFERED -> TurningOffers.accept(p);
			case TASK -> {
				if (t.kind() == TurningKind.FALL) {
					StoryData data = Story.get(p);
					data.turning = data.turning.withTask(TurningRules.FALL_CARRYING);
					Story.commit(p, data);
					p.getInventory().add(new ItemStack(TurningItems.WATCH_HOLOCRON));
					FallTask.deliver(p);
				} else {
					ReturnTask.complete(p);
				}
			}
			case CONFRONTATION -> {
				if (t.task() != TurningRules.CONFRONT_YIELDED && t.task() != TurningRules.CONFRONT_TALKED) {
					StoryMentors.cancelComing(p);
					Confrontations.begin(p, null, true);
				}

				Confrontations.resolve(p, null, "release");
			}
			case RITE -> Rites.rite(p);
			default -> {
				ctx.getSource().sendFailure(Component.literal(p.getName().getString() + " has no turning under way"));
				return 0;
			}
		}

		StoryData.Turning after = Story.get(p).turning;
		ctx.getSource().sendSuccess(() -> Component.literal(p.getName().getString() + ": " + after.kind().id() + " " + after.stage().id()), true);
		return 1;
	}

	private static int rank4(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
		RankFourChecklistPayload list = RankFour.checklist(p);
		StringBuilder text = new StringBuilder(p.getName().getString()).append(": visible ").append(list.visible()).append(", done ").append(list.done());

		for (RankFourChecklistPayload.Entry entry : list.entries()) {
			text.append("\n  ").append(entry.met() ? "[x] " : "[ ] ").append(entry.id()).append(' ').append(entry.have()).append('/').append(entry.need());
		}

		ctx.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
		return 1;
	}

	private static int ceremony(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
		String blocker = RankFour.blocker(p);

		if (blocker != null) {
			ctx.getSource().sendFailure(Component.literal("Not ready: " + blocker));
			return 0;
		}

		if (Identity.path(p) == Path.INDEPENDENT) {
			RankFour.wayfarer(p, null);
		} else {
			RankFour.ceremony(p, null, 1);
		}

		ctx.getSource().sendSuccess(() -> Component.literal("Held rank 4's ceremony for " + p.getName().getString()), true);
		return 1;
	}

	private static int reset(CommandContext<CommandSourceStack> ctx, ServerPlayer p) {
		StoryMentors.cancelComing(p);
		TurningOffers.forget(p);
		ReturnTask.forget(p);
		Confrontations.forget(p.getUUID());
		WatchHolocronItem.removeAll(p);
		StoryData data = Story.get(p);
		data.turning = StoryData.Turning.NEW;
		data.flags.remove(TurningRules.CRYSTAL_TAKEN);
		data.flags.remove(TurningRules.BLADE_BROKEN);
		Story.commit(p, data);
		ctx.getSource().sendSuccess(() -> Component.literal("Reset the turning of " + p.getName().getString()), true);
		return 1;
	}

	private TurningCommands() {
	}
}
