package com.oldrepublic.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueLoader;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorEntities;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.story.CaptainFate;
import com.oldrepublic.quest.story.CaptivesFate;
import com.oldrepublic.quest.story.CrossingStage;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.quest.story.PathStage;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.quest.story.StoryRules;
import com.oldrepublic.quest.story.StoryStructures;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Chapter Two: stage and choice rules, late joins, the lesson grant, Smoke on the Ridge
 * completion, the trial matrix, sessions, meeting sites on built test ground, the story templates,
 * the six dialogues, and one mentor per owner.
 */
public class StoryGameTests {
	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static ServerPlayer player(GameTestHelper helper) {
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "story_test"));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
		return player;
	}

	private static void run(ServerPlayer player, String action) {
		DialogueActions.Action a = DialogueActions.get(action);

		if (a == null) {
			throw new IllegalStateException("dialogue action " + action + " is not registered");
		}

		a.run(player, null);
	}

	/** A player standing at the Crossing, Chapter One behind them. */
	private static ServerPlayer atCrossing(GameTestHelper helper) {
		ServerPlayer player = player(helper);
		StoryData data = Story.get(player);
		data.crossingStage = CrossingStage.MEETING.ordinal();
		Story.commit(player, data);
		return player;
	}

	// ---------------------------------------------------------------- stages and the choice

	@GameTest
	public void stagesOnlyMoveForward(GameTestHelper helper) {
		StoryData data = new StoryData();
		helper.assertTrue(StoryRules.advanceCrossing(data, CrossingStage.WAITING, CrossingStage.NOT_STARTED), msg("NOT_STARTED -> WAITING"));
		helper.assertTrue(!StoryRules.advanceCrossing(data, CrossingStage.MEETING, CrossingStage.VOICES), msg("MEETING needs VOICES first"));
		helper.assertTrue(StoryRules.advanceCrossing(data, CrossingStage.VOICES, CrossingStage.WAITING), msg("WAITING -> VOICES"));
		helper.assertTrue(!StoryRules.advanceCrossing(data, CrossingStage.WAITING, CrossingStage.NOT_STARTED), msg("never back to WAITING"));
		helper.assertTrue(!StoryRules.advanceCrossing(data, CrossingStage.VOICES, CrossingStage.WAITING), msg("a repeat changes nothing"));

		data.pathStage = PathStage.LESSON_TWO.ordinal();
		helper.assertTrue(!StoryRules.advancePath(data, PathStage.LESSON_TWO, PathStage.LESSON_ONE), msg("same stage is no advance"));
		helper.assertTrue(!StoryRules.advancePath(data, PathStage.LESSON_ONE, PathStage.NONE), msg("never backwards"));
		helper.assertTrue(StoryRules.advancePath(data, PathStage.RECKONING, PathStage.LESSON_TWO), msg("LESSON_TWO -> RECKONING"));
		helper.assertTrue(!StoryRules.advancePath(data, PathStage.TRIAL, PathStage.DEBRIEF), msg("TRIAL needs the debrief first"));
		helper.succeed();
	}

	@GameTest
	public void choiceAppliesOnce(GameTestHelper helper) {
		StoryData data = new StoryData();
		helper.assertTrue(!StoryRules.choose(data, Path.JEDI, 0), msg("no choice before the meeting"));
		data.crossingStage = CrossingStage.MEETING.ordinal();
		helper.assertTrue(StoryRules.choose(data, Path.SITH, 10), msg("the first choice applies"));
		helper.assertTrue(data.stage() == PathStage.LESSON_ONE && data.storyPath() == Path.SITH, msg("Sith starts at lesson one"));
		helper.assertTrue(!StoryRules.choose(data, Path.JEDI, 20) && data.storyPath() == Path.SITH, msg("a second choice is ignored"));

		// Through the dialogue actions: join the Jedi, then a forged Sith join changes nothing.
		ServerPlayer player = atCrossing(helper);
		run(player, "story:join_jedi");
		helper.assertTrue(Story.get(player).storyPath() == Path.JEDI, msg("join_jedi must set the story path"));
		helper.assertTrue(Identity.path(player) == Path.JEDI && Identity.rank(player) == 1, msg("identity path Jedi, rank 1"));
		helper.assertTrue(Identity.trust(player, StoryRules.JEDI) == 20, msg("trust starts at 20, is " + Identity.trust(player, StoryRules.JEDI)));
		long xp = Progression.get(player).xp;
		run(player, "story:join_sith");
		run(player, "story:decline");
		helper.assertTrue(Story.get(player).storyPath() == Path.JEDI && Identity.path(player) == Path.JEDI, msg("the choice is final"));
		helper.assertTrue(Progression.get(player).xp == xp, msg("a repeated choice gives no second reward"));
		helper.succeed();
	}

	@GameTest
	public void declineMakesAnIndependent(GameTestHelper helper) {
		ServerPlayer player = atCrossing(helper);
		run(player, "story:decline");
		StoryData data = Story.get(player);
		helper.assertTrue(data.storyPath() == Path.INDEPENDENT && data.stage() == PathStage.NONE, msg("decline -> Independent at NONE"));
		helper.assertTrue(data.crossing() == CrossingStage.DECIDED, msg("the Crossing is decided"));
		helper.assertTrue(Identity.path(player) == Path.INDEPENDENT, msg("identity path Independent"));
		helper.assertTrue(Skills.rank(player, SkillIds.UNBOUND) > 0, msg("unbound must be granted"));
		helper.succeed();
	}

	@GameTest
	public void lateJoinGates(GameTestHelper helper) {
		StoryData data = new StoryData();
		data.crossingStage = CrossingStage.DECIDED.ordinal();
		data.path = Path.INDEPENDENT.id();

		helper.assertTrue(StoryRules.joinBlocker(data, Path.JEDI, 0, 0) == null, msg("a neutral Independent may join the Jedi"));
		helper.assertTrue("message.oldrepublic.story.blocked.jedi_refuses".equals(StoryRules.joinBlocker(data, Path.JEDI, -50, 0)),
				msg("the Jedi refuses at -50"));
		helper.assertTrue(StoryRules.joinBlocker(data, Path.JEDI, -49, 0) == null, msg("-49 is still accepted"));
		helper.assertTrue("message.oldrepublic.story.blocked.sith_refuses".equals(StoryRules.joinBlocker(data, Path.SITH, 50, 0)),
				msg("the Sith refuses at +50"));
		helper.assertTrue("message.oldrepublic.story.blocked.reputation".equals(StoryRules.joinBlocker(data, Path.SITH, 0, -100)),
				msg("Distrusted (rep -100) is refused"));
		helper.assertTrue(StoryRules.joinBlocker(data, Path.SITH, 0, -99) == null, msg("rep -99 is accepted"));

		helper.assertTrue(StoryRules.lateJoin(data, Path.SITH), msg("the late join applies"));
		helper.assertTrue(data.storyPath() == Path.SITH && data.stage() == PathStage.LESSON_ONE, msg("late join starts at lesson one"));
		helper.assertTrue(!StoryRules.lateJoin(data, Path.JEDI), msg("only one late join"));
		helper.assertTrue(StoryRules.joinBlocker(data, Path.JEDI, 0, 0) != null, msg("no join after the late join"));

		StoryData pathPlayer = new StoryData();
		pathPlayer.crossingStage = CrossingStage.DECIDED.ordinal();
		pathPlayer.path = Path.JEDI.id();
		helper.assertTrue("message.oldrepublic.story.blocked.chosen".equals(StoryRules.joinBlocker(pathPlayer, Path.SITH, -60, 0)),
				msg("a Jedi cannot switch to the Sith"));
		helper.succeed();
	}

	@GameTest
	public void lessonOneGrantsPullForFree(GameTestHelper helper) {
		ServerPlayer player = atCrossing(helper);
		run(player, "story:join_jedi");
		int points = Progression.get(player).skillPoints;
		run(player, "story:lesson_one");
		helper.assertTrue(Story.get(player).hasFlag(StoryRules.LESSON_ONE_BEGUN), msg("lesson one must begin"));
		helper.assertTrue(Skills.rank(player, "force_pull") > 0, msg("force_pull must be granted"));
		helper.assertTrue(Progression.get(player).skillPoints == points, msg("the lesson grant costs no skill points"));
		run(player, "story:lesson_one");
		helper.assertTrue(Progression.get(player).skillPoints == points, msg("asking again changes nothing"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- Smoke on the Ridge

	@GameTest
	public void reckoningCompletion(GameTestHelper helper) {
		StoryData data = new StoryData();
		data.counters.put(StoryRules.CREW_TOTAL, 5);
		data.counters.put(StoryRules.CREW_DOWN, 4);
		helper.assertTrue(!StoryRules.crewDown(data), msg("one crew member still stands"));
		data.counters.put(StoryRules.CREW_DOWN, 5);
		helper.assertTrue(StoryRules.crewDown(data), msg("all five down"));

		helper.assertTrue(!StoryRules.captivesAbandoned(data, true, 200, 128), msg("not abandoned while the Captain is undecided"));
		data.captain = CaptainFate.YIELDED.ordinal();
		helper.assertTrue(!StoryRules.captivesAbandoned(data, true, 128, 128), msg("128 blocks is not yet away"));
		helper.assertTrue(StoryRules.captivesAbandoned(data, true, 128.5, 128), msg("beyond 128 blocks the captives are left"));
		helper.assertTrue(!StoryRules.captivesAbandoned(data, false, 300, 128), msg("never while crew stands"));

		helper.assertTrue(!StoryRules.reckoningComplete(data, true), msg("a yielded Captain is not resolved"));
		data.captain = CaptainFate.SPARED.ordinal();
		helper.assertTrue(!StoryRules.reckoningComplete(data, true), msg("captives still open"));
		data.captives = CaptivesFate.ABANDONED.ordinal();
		helper.assertTrue(!StoryRules.captivesAbandoned(data, true, 300, 128), msg("a settled group is not abandoned twice"));
		helper.assertTrue(StoryRules.reckoningComplete(data, true), msg("crew down, Captain spared, captives left: complete"));
		helper.assertTrue(!StoryRules.reckoningComplete(data, false), msg("never with crew standing"));

		helper.assertTrue(StoryRules.afterReckoning(Path.INDEPENDENT) == PathStage.COMPLETE, msg("Independents finish the chapter"));
		helper.assertTrue(StoryRules.afterReckoning(Path.JEDI) == PathStage.DEBRIEF, msg("path players report back"));
		helper.assertTrue(StoryRules.afterLessonTwo(data) == PathStage.RECKONING, msg("lesson two leads to the mission"));
		data.flags.add(StoryRules.RECKONING_DONE);
		helper.assertTrue(StoryRules.afterLessonTwo(data) == PathStage.DEBRIEF, msg("a late joiner never repeats the mission"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- trial and sessions

	@GameTest
	public void trialRequirementsMatrix(GameTestHelper helper) {
		StoryRules.TrialRequirements req = new StoryRules.TrialRequirements(6, 60, 100);
		String p = "message.oldrepublic.story.blocked.";
		helper.assertTrue(StoryRules.trialBlocker(Path.JEDI, req, 6, 60, 100, 0, 0, 0) == null, msg("all met: open"));
		helper.assertTrue((p + "level").equals(StoryRules.trialBlocker(Path.JEDI, req, 5, 60, 100, 0, 0, 0)), msg("level 5"));
		helper.assertTrue((p + "trial_trust").equals(StoryRules.trialBlocker(Path.JEDI, req, 6, 59, 100, 0, 0, 0)), msg("trust 59"));
		helper.assertTrue((p + "rep_jedi").equals(StoryRules.trialBlocker(Path.JEDI, req, 6, 60, 99, 0, 0, 0)), msg("Jedi rep 99"));
		helper.assertTrue((p + "rep_sith").equals(StoryRules.trialBlocker(Path.SITH, req, 6, 60, 99, 0, 0, 0)), msg("Sith rep 99"));
		helper.assertTrue((p + "dark_clouds").equals(StoryRules.trialBlocker(Path.JEDI, req, 6, 60, 100, -1, 0, 0)), msg("Jedi at -1"));
		helper.assertTrue((p + "too_soft").equals(StoryRules.trialBlocker(Path.SITH, req, 6, 60, 100, 1, 0, 0)), msg("Sith at +1"));
		helper.assertTrue(StoryRules.trialBlocker(Path.SITH, req, 6, 60, 100, 0, 0, 0) == null, msg("Sith at 0 is allowed"));
		helper.assertTrue((p + "retry").equals(StoryRules.trialBlocker(Path.JEDI, req, 6, 60, 100, 0, 100, 700)), msg("retry wait"));
		helper.assertTrue(StoryRules.trialBlocker(Path.JEDI, req, 6, 60, 100, 0, 700, 700) == null, msg("retry over"));

		helper.assertTrue((p + "dark_clouds").equals(StoryRules.lessonBlocker(Path.JEDI, 50, 30, -15)), msg("no lessons in the opposed band"));
		helper.assertTrue(StoryRules.lessonBlocker(Path.JEDI, 50, 30, -14) == null, msg("-14 still learns"));
		helper.assertTrue((p + "trust").equals(StoryRules.lessonBlocker(Path.SITH, 29, 30, 0)), msg("lesson two needs trust 30"));
		helper.succeed();
	}

	@GameTest
	public void sessionsKeepThePathOpen(GameTestHelper helper) {
		long day = 24000L;
		StoryData data = new StoryData();
		data.path = Path.JEDI.id();
		data.pathStage = PathStage.DEBRIEF.ordinal();
		int needed = StoryRules.trustNeeded(data.stage(), 30, 60);
		helper.assertTrue(needed == 60, msg("the debrief leads to the trial's 60"));
		helper.assertTrue(StoryRules.sessionAvailable(data, 3 * day, 45, needed), msg("short of trust: a session is open"));
		helper.assertTrue(!StoryRules.sessionAvailable(data, 3 * day, 60, needed), msg("enough trust: no session before the end"));
		data.sessionDay = 3;
		data.pathStage = PathStage.COMPLETE.ordinal();
		helper.assertTrue(!StoryRules.sessionAvailable(data, 3 * day + 100, 80, 0), msg("once per day"));
		helper.assertTrue(StoryRules.sessionAvailable(data, 4 * day, 80, 0), msg("the next day it is open again"));

		helper.assertTrue(StoryRules.atoneAvailable(data, -1, 4 * day), msg("Atone is open while alignment blocks the trial"));
		helper.assertTrue(!StoryRules.atoneAvailable(data, 0, 4 * day), msg("no Atone without need"));
		data.atoneDay = 4;
		helper.assertTrue(!StoryRules.atoneAvailable(data, -20, 4 * day), msg("Atone once per day"));

		StoryData independent = new StoryData();
		independent.path = Path.INDEPENDENT.id();
		helper.assertTrue(!StoryRules.sessionAvailable(independent, day, 0, 60), msg("Independents keep the solo meditation"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- sites and templates

	private static final MeetingSites.Rules SMALL = new MeetingSites.Rules(5, 7, 2, pos -> false, List.of(), 0);

	/** An 8 x 8 grass floor at y = 1, open to the sky. */
	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);

				// Up to y = 8: the test enclosure's barrier ceiling would otherwise be the heightmap top.
				for (int y = 2; y <= 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	private static MeetingSites.Candidate evaluate(GameTestHelper helper, MeetingSites.Rules rules) {
		BlockPos centre = helper.absolutePos(new BlockPos(3, 1, 3));
		return MeetingSites.evaluate(helper.getLevel(), centre.getX(), centre.getZ(), rules);
	}

	/** Heights and ground blocks of the 7 x 7 area (relative to the test), for failure messages. */
	private static String describe(GameTestHelper helper) {
		StringBuilder out = new StringBuilder("relative stand/ground:");
		int base = helper.absolutePos(BlockPos.ZERO).getY();

		for (int z = 0; z <= 6; z++) {
			for (int x = 0; x <= 6; x++) {
				BlockPos abs = helper.absolutePos(new BlockPos(x, 0, z));
				int stand = helper.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, abs.getX(), abs.getZ());
				String ground = helper.getLevel().getBlockState(new BlockPos(abs.getX(), stand - 1, abs.getZ())).getBlock().getDescriptionId();
				out.append(' ').append(x).append(',').append(z).append('=').append(stand - base).append(ground.substring(ground.lastIndexOf('.') + 1));
			}
		}

		for (int y = -3; y <= 8; y++) {
			for (int z = 0; z <= 6; z++) {
				for (int x = 0; x <= 6; x++) {
					if (helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).is(MeetingSites.BUILT_BLOCKS)) {
						out.append(" built@").append(x).append(',').append(y).append(',').append(z);
					}
				}
			}
		}

		return out.toString();
	}

	@GameTest
	public void meetingSiteOnFlatGrass(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.SHORT_GRASS);
		MeetingSites.Candidate site = evaluate(helper, SMALL);
		helper.assertTrue(site != null, msg("flat grass with a tuft on it must pass; " + describe(helper)));
		helper.assertTrue(site.stand().getY() == helper.absolutePos(new BlockPos(0, 2, 0)).getY(), msg("the stand height is on top of the grass"));
		helper.succeed();
	}

	@GameTest
	public void meetingSiteRejectsSlope(GameTestHelper helper) {
		floor(helper);

		for (int y = 2; y <= 4; y++) {
			helper.setBlock(new BlockPos(1, y, 1), Blocks.DIRT);
		}

		helper.assertTrue(evaluate(helper, SMALL) == null, msg("a 3-block rise is too steep"));
		helper.setBlock(new BlockPos(1, 4, 1), Blocks.AIR);
		helper.assertTrue(evaluate(helper, SMALL) != null, msg("a 2-block rise is allowed; " + describe(helper)));
		helper.succeed();
	}

	@GameTest
	public void meetingSiteRejectsWater(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(new BlockPos(3, 1, 4), Blocks.WATER);
		helper.assertTrue(evaluate(helper, SMALL) == null, msg("water in the footprint must fail"));
		helper.succeed();
	}

	@GameTest
	public void meetingSiteRejectsBuiltBlocks(GameTestHelper helper) {
		floor(helper);
		// Outside the 5 x 5 footprint but inside the 7 x 7 clearance.
		helper.setBlock(new BlockPos(6, 2, 3), Blocks.OAK_PLANKS);
		helper.assertTrue(evaluate(helper, SMALL) == null, msg("planks near the site must fail"));
		helper.succeed();
	}

	@GameTest
	public void meetingSiteRespectsForbiddenAndOthers(GameTestHelper helper) {
		floor(helper);
		BlockPos centre = helper.absolutePos(new BlockPos(3, 2, 3));
		MeetingSites.Rules protectedRules = new MeetingSites.Rules(5, 7, 2, pos -> Math.abs(pos.getX() - centre.getX()) + Math.abs(pos.getZ() - centre.getZ()) < 4, List.of(), 0);
		helper.assertTrue(evaluate(helper, protectedRules) == null, msg("a site in spawn protection must fail"));
		MeetingSites.Rules crowded = new MeetingSites.Rules(5, 7, 2, pos -> false, List.of(centre.offset(10, 0, 0)), 32);
		helper.assertTrue(evaluate(helper, crowded) == null, msg("a site near another player's site must fail"));
		helper.succeed();
	}

	@GameTest
	public void storyTemplatesLoad(GameTestHelper helper) {
		checkTemplate(helper, StoryStructures.JEDI_CAMP, new Vec3i(7, 5, 7));
		checkTemplate(helper, StoryStructures.SITH_CAMP, new Vec3i(7, 5, 7));
		checkTemplate(helper, StoryStructures.RAIDER_CAMP, new Vec3i(13, 6, 13));
		helper.succeed();
	}

	private static void checkTemplate(GameTestHelper helper, Identifier id, Vec3i size) {
		Optional<StructureTemplate> template = helper.getLevel().getStructureManager().get(id);
		helper.assertTrue(template.isPresent(), msg("missing structure template " + id));
		helper.assertTrue(template.get().getSize().equals(size), msg(id + " has size " + template.get().getSize() + ", expected " + size));
	}

	@GameTest
	public void placeStructuresSwitch(GameTestHelper helper) {
		floor(helper);
		BlockPos stand = helper.absolutePos(new BlockPos(3, 2, 3));
		helper.assertTrue(!StoryStructures.placeCamp(helper.getLevel(), MentorRole.JEDI, stand, false), msg("switch off: nothing placed"));

		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.assertTrue(helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.GRASS_BLOCK) && helper.getBlockState(new BlockPos(x, 2, z)).isAir(),
						msg("switch off must leave " + x + ", " + z + " untouched"));
			}
		}

		helper.assertTrue(StoryStructures.placeCamp(helper.getLevel(), MentorRole.JEDI, stand, true), msg("switch on: the camp is placed"));
		// Template origin is (0, 1, 0) here; its lantern sits at local (5, 3, 5).
		helper.assertTrue(helper.getBlockState(new BlockPos(5, 4, 5)).is(Blocks.LANTERN), msg("the Jedi camp's lantern must be in place"));
		helper.assertTrue(helper.getBlockState(new BlockPos(3, 2, 3)).isAir(), msg("the mentor's spot stays free"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- dialogues, mentors, data

	@GameTest
	public void storyDialoguesLoad(GameTestHelper helper) {
		for (String id : new String[] {"crossing_jedi", "crossing_sith", "mentor_jedi", "mentor_sith", "brenn", "independent"}) {
			helper.assertTrue(DialogueLoader.get(OldRepublicMod.id(id)) != null, msg("dialogue " + id + " must load"));
		}

		for (String action : new String[] {"story:join_jedi", "story:join_sith", "story:decline", "story:lesson_one", "story:lesson_two",
				"story:debrief", "story:trial", "story:session", "story:brenn_spare", "story:brenn_kill", "story:summon", "skills:unburden"}) {
			helper.assertTrue(DialogueActions.get(action) != null, msg("dialogue action " + action + " must be registered"));
		}

		helper.succeed();
	}

	@GameTest
	public void oneMentorPerOwner(GameTestHelper helper) {
		MentorEntity first = MentorEntities.type(MentorRole.JEDI).create(helper.getLevel(), EntitySpawnReason.COMMAND);
		MentorEntity copy = MentorEntities.type(MentorRole.JEDI).create(helper.getLevel(), EntitySpawnReason.COMMAND);
		helper.assertTrue(first != null && copy != null, msg("mentors must be creatable"));
		StoryData data = new StoryData();
		helper.assertTrue(!StoryMentors.isWantedCopy(first, data), msg("with no mentor remembered, any copy is unwanted"));
		data.setMentor(StoryRules.JEDI, first.getUUID());
		helper.assertTrue(StoryMentors.isWantedCopy(first, data), msg("the remembered mentor stays"));
		helper.assertTrue(!StoryMentors.isWantedCopy(copy, data), msg("a second copy is removed"));
		first.discard();
		copy.discard();
		helper.succeed();
	}

	@GameTest
	public void storyDataRoundTrip(GameTestHelper helper) {
		StoryData data = new StoryData();
		data.crossingStage = CrossingStage.DECIDED.ordinal();
		data.path = Path.SITH.id();
		data.pathStage = PathStage.TRIAL.ordinal();
		data.camp = new BlockPos(10, 64, -20);
		data.rival = UUID.randomUUID();
		data.captain = CaptainFate.EXECUTED.ordinal();
		data.captives = CaptivesFate.EXPLOITED.ordinal();
		data.flags.add(StoryRules.TRIAL_WON);
		data.counters.put(StoryRules.CREW_TOTAL, 5);

		JsonElement json = StoryData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		StoryData back = StoryData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.crossing() == CrossingStage.DECIDED && back.storyPath() == Path.SITH && back.stage() == PathStage.TRIAL, msg("stages"));
		helper.assertTrue(data.camp.equals(back.camp) && data.rival.equals(back.rival), msg("places and NPCs"));
		helper.assertTrue(back.captainFate() == CaptainFate.EXECUTED && back.captivesFate() == CaptivesFate.EXPLOITED, msg("fates"));
		helper.assertTrue(back.hasFlag(StoryRules.TRIAL_WON) && back.counter(StoryRules.CREW_TOTAL) == 5, msg("flags and counters"));
		helper.succeed();
	}
}
