package com.oldrepublic.quest.story;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /oldrepublic story ...} (operators): inspect Chapter Two, reset it, and jump to any part of
 * it for testing. Every shortcut is logged. Resetting does not touch alignment, reputation, path or
 * rank: the identity commands set those.
 */
final class StoryCommands {
	private static final double RESET_RADIUS = 96;

	static void register() {
		OldRepublicCommands.addSubcommand((context, root) -> root.then(story()));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> story() {
		return literal("story")
				.executes(ctx -> status(ctx, ctx.getSource().getPlayerOrException()))
				.then(literal("status").then(argument("player", EntityArgument.player())
						.executes(ctx -> status(ctx, EntityArgument.getPlayer(ctx, "player")))))
				.then(literal("reset").then(argument("player", EntityArgument.player()).executes(StoryCommands::reset)))
				.then(literal("dream").then(argument("player", EntityArgument.player()).executes(StoryCommands::dream)))
				.then(literal("meet").then(argument("player", EntityArgument.player()).executes(StoryCommands::meet)))
				.then(literal("choose").then(argument("player", EntityArgument.player())
						.then(argument("path", StringArgumentType.word()).executes(StoryCommands::choose))))
				.then(literal("stage").then(argument("player", EntityArgument.player())
						.then(argument("stage", StringArgumentType.word()).executes(StoryCommands::stage))))
				.then(literal("lesson").then(argument("player", EntityArgument.player())
						.then(literal("complete").executes(StoryCommands::completeLesson))))
				.then(literal("reckoning").then(argument("player", EntityArgument.player())
						.then(literal("start").executes(ctx -> reckoning(ctx, "start")))
						.then(literal("here").executes(ctx -> reckoning(ctx, "here")))
						.then(literal("complete").executes(ctx -> reckoning(ctx, "complete")))))
				.then(literal("trial").then(argument("player", EntityArgument.player())
						.then(literal("start").executes(ctx -> trial(ctx, false)))
						.then(literal("win").executes(ctx -> trial(ctx, true)))))
				.then(literal("summon").then(argument("player", EntityArgument.player()).executes(StoryCommands::summon)));
	}

	private static int status(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
		StoryData d = Story.get(player);
		String text = String.format(Locale.ROOT,
				"%s: crossing %s, path %s at %s, meeting %s, camp %s, mentors jedi=%s sith=%s rival=%s, encounter %s at %s "
						+ "(captain %s, captives %s, crew %d/%d), flags %s, counters %s, summon %s",
				player.getName().getString(), d.crossing(), d.storyPath(), d.stage(), pos(d.meeting), pos(d.camp), d.jediMentor, d.sithMentor,
				d.rival, d.encounterState(), pos(d.encounterPos), d.captainFate(), d.captivesFate(), d.counter(StoryRules.CREW_DOWN),
				d.counter(StoryRules.CREW_TOTAL), d.flags, d.counters, d.summonAt < 0 ? "none" : d.summonRole + "@" + d.summonAt);
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
		return 1;
	}

	private static String pos(@Nullable BlockPos pos) {
		return pos == null ? "-" : pos.toShortString();
	}

	/** Clears Chapter Two and removes the player's loaded story entities (mentors, Brenn, encounter). */
	private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		StoryData d = Story.get(player);
		ServerLevel overworld = player.level().getServer().overworld();
		int removed = 0;

		for (BlockPos centre : new BlockPos[] {d.meeting, d.camp, d.encounterPos, player.blockPosition()}) {
			if (centre != null) {
				removed += removeOwned(overworld, player.getUUID(), centre);
			}
		}

		StoryService.forget(player);
		// forget keeps a pending mentor call (it survives respawns), so a reset drops it itself.
		StoryMentors.cancelComing(player);
		Story.commit(player, new StoryData());
		log(ctx, "reset Chapter Two of " + player.getName().getString() + " (" + removed + " entities removed)");
		return 1;
	}

	private static int removeOwned(ServerLevel level, UUID owner, BlockPos centre) {
		int count = 0;

		for (Entity entity : level.getEntities((Entity) null, new AABB(centre).inflate(RESET_RADIUS),
				e -> StoryTags.isOwnedBy(e, owner) || (e instanceof MentorEntity mentor && owner.equals(mentor.owner())))) {
			entity.discard();
			count++;
		}

		return count;
	}

	/** Starts the Two Voices dream now (Chapter Two's wait is skipped). */
	private static int dream(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		StoryData d = Story.get(player);

		if (d.crossing().ordinal() > CrossingStage.WAITING.ordinal()) {
			ctx.getSource().sendFailure(Component.literal("The Two Voices dream is already behind this player"));
			return 0;
		}

		d.crossingStage = CrossingStage.WAITING.ordinal();
		d.waitSince = StoryService.now(player);
		Story.commit(player, d);
		TwoVoices.start(player);
		log(ctx, "started the Two Voices dream for " + player.getName().getString());
		return 1;
	}

	/** Moves the story to the meeting (choosing the site if needed) and the player next to it. */
	private static int meet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

		if (!StoryService.inOverworld(player)) {
			ctx.getSource().sendFailure(Component.literal("The Crossing happens in the overworld"));
			return 0;
		}

		if (!toMeeting(player)) {
			ctx.getSource().sendFailure(Component.literal("The Crossing is already decided"));
			return 0;
		}

		log(ctx, "moved " + player.getName().getString() + " to the Crossing at " + pos(Story.get(player).meeting));
		return 1;
	}

	private static boolean toMeeting(ServerPlayer player) {
		StoryData d = Story.get(player);

		if (d.crossing() == CrossingStage.DECIDED) {
			return false;
		}

		if (d.crossing().ordinal() < CrossingStage.VOICES.ordinal()) {
			d.crossingStage = CrossingStage.VOICES.ordinal();
			Story.commit(player, d);
			Crossing.chooseMeeting(player, d);
			d = Story.get(player);
		}

		BlockPos meeting = d.meeting;

		if (meeting != null && d.crossing() == CrossingStage.VOICES) {
			Vec3 at = StoryMentors.ground(player.level(), meeting.getX() + 0.5, meeting.getZ() + 8.5);
			player.teleportTo(at.x, at.y, at.z);
			Crossing.arrive(player, d);
		}

		return true;
	}

	/** Makes the Crossing choice without the dialogue. */
	private static int choose(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String path = StringArgumentType.getString(ctx, "path").toLowerCase(Locale.ROOT);

		if (Story.get(player).crossing() != CrossingStage.DECIDED && StoryService.inOverworld(player)) {
			toMeeting(player);
		}

		switch (path) {
			case "jedi" -> Crossing.join(player, MentorRole.JEDI);
			case "sith" -> Crossing.join(player, MentorRole.SITH);
			case "independent", "neither" -> Crossing.decline(player);
			default -> {
				ctx.getSource().sendFailure(Component.literal("Unknown path; one of jedi, sith, independent"));
				return 0;
			}
		}

		log(ctx, "chose " + path + " for " + player.getName().getString() + " (now " + Story.get(player).storyPath() + ")");
		return 1;
	}

	/** Sets the path stage directly (no rewards). */
	private static int stage(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		String name = StringArgumentType.getString(ctx, "stage").toUpperCase(Locale.ROOT);
		PathStage stage;

		try {
			stage = PathStage.valueOf(name);
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal("Unknown stage; one of "
					+ Arrays.stream(PathStage.values()).map(s -> s.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "))));
			return 0;
		}

		StoryData d = Story.get(player);
		d.pathStage = stage.ordinal();
		Story.commit(player, d);
		log(ctx, "set the path stage of " + player.getName().getString() + " to " + stage);
		return 1;
	}

	/** Completes the lesson the player is on, with its rewards. */
	private static int completeLesson(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");

		switch (Story.get(player).stage()) {
			case LESSON_ONE -> Lessons.completeLessonOne(player);
			case LESSON_TWO -> Lessons.completeLessonTwo(player);
			default -> {
				ctx.getSource().sendFailure(Component.literal("No lesson in progress"));
				return 0;
			}
		}

		log(ctx, "completed a lesson for " + player.getName().getString());
		return 1;
	}

	/**
	 * {@code start}: the mission begins now (site search from the camp or player); {@code here}: the
	 * raider camp is placed 24 blocks in front of the player; {@code complete}: open fates are settled
	 * (the Captain fled, the captives left) and the mission completes.
	 */
	private static int reckoning(CommandContext<CommandSourceStack> ctx, String what) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		StoryData d = Story.get(player);

		switch (what) {
			case "start" -> {
				if (d.stage().ordinal() < PathStage.RECKONING.ordinal()) {
					d.pathStage = PathStage.RECKONING.ordinal();
					Story.commit(player, d);
				}

				Reckoning.begin(player, d);
			}
			case "here" -> {
				Vec3 ahead = player.position().add(player.getLookAngle().horizontal().normalize().scale(24));
				Vec3 ground = StoryMentors.ground(player.level(), ahead.x, ahead.z);
				d.pathStage = Math.max(d.pathStage, PathStage.RECKONING.ordinal());
				d.encounterPos = BlockPos.containing(ground);
				d.encounterState = EncounterState.PLACED.ordinal();
				d.captain = CaptainFate.NONE.ordinal();
				d.captives = CaptivesFate.NONE.ordinal();
				Story.commit(player, d);
				StoryStructures.placeRaiderCamp(player.level(), d.encounterPos, StoryService.balance().placeStructures);
			}
			default -> {
				if (!d.captainFate().resolved()) {
					d.captain = CaptainFate.FLED.ordinal();
				}

				if (d.captivesFate() == CaptivesFate.NONE) {
					d.captives = CaptivesFate.ABANDONED.ordinal();
				}

				d.encounterState = EncounterState.SPAWNED.ordinal();
				Story.commit(player, d);
				Reckoning.complete(player, d);
			}
		}

		log(ctx, "Smoke on the Ridge " + what + " for " + player.getName().getString() + " at " + pos(Story.get(player).encounterPos));
		return 1;
	}

	/** {@code start}: the trial spar (requirements are still checked); {@code win}: the trial is passed. */
	private static int trial(CommandContext<CommandSourceStack> ctx, boolean win) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		StoryData d = Story.get(player);

		if (StoryRules.mentorOf(d.storyPath()) == null) {
			ctx.getSource().sendFailure(Component.literal("Only Jedi and Sith path players take a rank trial"));
			return 0;
		}

		if (d.stage().ordinal() < PathStage.TRIAL.ordinal()) {
			d.pathStage = PathStage.TRIAL.ordinal();
			Story.commit(player, d);
		}

		if (win) {
			Trials.passTrial(player);
		} else {
			Trials.startTrial(player, null);
		}

		log(ctx, (win ? "passed" : "started") + " the trial for " + player.getName().getString() + " (rank " + Identity.rank(player) + ")");
		return 1;
	}

	/** The path mentor arrives near the player on the next tick. */
	private static int summon(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		StoryData d = Story.get(player);
		String mentorId = StoryRules.mentorOf(d.storyPath());

		if (mentorId == null || !StoryService.inOverworld(player)) {
			ctx.getSource().sendFailure(Component.literal("Needs a Jedi or Sith path player in the overworld"));
			return 0;
		}

		d.summonRole = mentorId;
		d.summonAt = StoryService.now(player);
		Story.commit(player, d);
		log(ctx, "summoned the " + mentorId + " mentor to " + player.getName().getString());
		return 1;
	}

	private static void log(CommandContext<CommandSourceStack> ctx, String what) {
		OldRepublicMod.LOGGER.info("[story command] {} {}", ctx.getSource().getTextName(), what);
		ctx.getSource().sendSuccess(() -> Component.literal("Story: " + what), true);
	}

	private StoryCommands() {
	}
}
