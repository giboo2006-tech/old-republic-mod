package com.oldrepublic.quest.story;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.mission.Missions;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.Skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Rank 3 (spec 5.8): the Knighting at the Watch, the Naming, and the Independent's Pathfinder title.
 * The requirements are one pure rule ({@link ChapterThreeRules#requirements}) read here from the
 * synced attachments, so the Standing tab's checklist and the dialogue's fail reason always agree;
 * the trial is Chapter Three COMPLETE (the confrontation won). The ceremony pays once (flag
 * {@code c3.rank3_done} / {@code c3.pathfinder_done}); after a turning, the new path's Knighting or
 * Naming is held again without its skill points and XP (flag {@link #RANK3_PAID}, spec
 * MILESTONE_5.md 8.4).
 */
public final class RankThree {
	/** The Independent's dialogue at Courier's Fall. */
	public static final Identifier PATHFINDER_DIALOGUE = OldRepublicMod.id("pathfinder");
	/** The Pathfinder title (an identity choice, shown in the Standing tab's titles row). */
	public static final String TITLE_PATHFINDER = "title.pathfinder";
	/**
	 * Story flag the turning's rite puts in place of {@code c3.rank3_done} (spec MILESTONE_5.md 8.4):
	 * the rank 3 ceremony was paid on an earlier path, so holding it again gives no skill points and no XP.
	 */
	public static final String RANK3_PAID = "c3.rank3_paid";
	/** The rank 3 robes (W4's items, looked up by id). */
	private static final Identifier KNIGHT_ROBE = OldRepublicMod.id("jedi_knight_robe");
	private static final Identifier LORD_ROBE = OldRepublicMod.id("sith_lord_robe");
	/** How near the fall's pedestal the Pathfinder's ceremony must happen, in blocks. */
	private static final double PEDESTAL_REACH = 8;

	/**
	 * What the rank 3 checks read for {@code p}: the story and identity attachments (synced to the
	 * owner), the holocron count and the own-side contracts. {@code level} comes from the caller
	 * because the Force data is read differently on each side.
	 */
	public static ChapterThreeRules.Standing standing(Player p, int level) {
		StoryData data = Story.peek(p);
		Path path = data == null ? Path.NONE : data.storyPath();
		ChapterThreeStage stage = data == null ? ChapterThreeStage.NOT_STARTED : data.chapter3.stage();
		MentorRole role = MentorRole.of(path);
		HolocronKind own = path == Path.SITH ? HolocronKind.SITH : HolocronKind.JEDI;
		int bestRep = Integer.MIN_VALUE;

		// Only factions the player knows count, so a faction not met yet (0) never lifts the best (MILESTONE_5.md 7.3).
		for (Faction faction : Faction.values()) {
			if (Identity.knows(p, faction)) {
				bestRep = Math.max(bestRep, Identity.reputation(p, faction));
			}
		}

		return new ChapterThreeRules.Standing(path, stage, level, Identity.trust(p, role.id()), Identity.reputation(p, role.faction()),
				Identity.alignment(p), HolocronService.attunedCount(p, own), HolocronService.attunedCount(p, null), bestRep,
				Missions.ownSideContracts(p));
	}

	/** Every rank 3 (or Pathfinder) requirement with the player's values, for the Standing checklist. */
	public static List<ChapterThreeRules.Requirement> requirements(Player p, int level) {
		return ChapterThreeRules.requirements(standing(p, level), ChapterThreeService.balance());
	}

	/**
	 * The {@code rank3:ready} fail reason ({@code key|need}), or null when the player may take the
	 * ceremony now. A player who already took it is "done".
	 */
	public static @Nullable String blocker(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.hasFlag(ChapterThreeRules.RANK3_DONE) || data.hasFlag(ChapterThreeRules.PATHFINDER_DONE)) {
			return "message.oldrepublic.story.rank3.done";
		}

		return ChapterThreeRules.rank3Blocker(standing(p, Progression.get(p).level), ChapterThreeService.balance());
	}

	/** Whether the Calling should lead an Independent to Courier's Fall for the title. */
	static boolean pathfinderCalling(ServerPlayer p, StoryData data) {
		return data.storyPath() == Path.INDEPENDENT && data.chapter3.fall() != null && blocker(p) == null;
	}

	// ---------------------------------------------------------------- the ceremonies

	/**
	 * {@code rank3:ceremony}: the Knighting (Jedi) or the Naming (Sith) at the mentor, or the
	 * Pathfinder's title at the fall. Re-checks every requirement on the server and names the first
	 * unmet one.
	 */
	static void ceremony(ServerPlayer p, @Nullable Entity speaker) {
		Path path = Story.get(p).storyPath();
		String blocker = blocker(p);

		if (blocker != null) {
			sendReason(p, blocker);
			return;
		}

		switch (path) {
			case JEDI, SITH -> promote(p, MentorRole.of(path), speaker);
			case INDEPENDENT -> pathfinder(p);
			case NONE -> {
			}
		}
	}

	private static void promote(ServerPlayer p, MentorRole role, @Nullable Entity speaker) {
		StoryData data = Story.get(p);
		data.flags.add(ChapterThreeRules.RANK3_DONE);
		Story.commit(p, data);
		ChapterThreeBalance b = ChapterThreeService.balance();
		boolean jedi = role == MentorRole.JEDI;
		// Held again on a new path after a turning: no skill points and no XP the second time (8.4).
		boolean paid = data.hasFlag(RANK3_PAID);

		Identity.promote(p, 3);

		if (!paid) {
			Progression.grantSkillPoints(p, b.rank3Sp);
		}

		Progression.grantResonanceMilestone(p, "second_rank", b.rank3Resonance);

		if (!paid) {
			Progression.addXp(p, b.rank3Xp, "rank_three");
		}

		giveRobe(p, jedi ? KNIGHT_ROBE : LORD_ROBE);
		Skills.grant(p, jedi ? SkillIds.KNIGHTS_VIGIL : SkillIds.LORDS_DOMINION);
		Journal.unlockEntry(p, jedi ? JournalEntries.KNIGHT_OF_THE_WATCH : JournalEntries.NAMED_LORD);

		// The blade touch or the tablet breaking: around the player, the dir pointing from the mentor.
		Vec3 dir = speaker == null ? null : p.position().subtract(speaker.position()).horizontal();
		Vec3 facing = dir == null || dir.lengthSqr() < 1.0e-4 ? null : dir.normalize();
		EffectEvents.around(p.level(), QuestEffects.RANK_CEREMONY, p.position(), facing, p.getId(), 1f,
				jedi ? QuestEffects.RANK_KNIGHTING : QuestEffects.RANK_NAMING);
		StoryService.milestone(p, Story.get(p), QuestEffects.LESSON_RANK_THREE);
		StoryService.mentorSays(p, role, "rank3.after");
	}

	/** The Pathfinder's title (5.8): the rank does not change. */
	private static void pathfinder(ServerPlayer p) {
		StoryData data = Story.get(p);
		BlockPos fall = data.chapter3.fall();

		if (fall == null || !near(p, fall)) {
			sendReason(p, "message.oldrepublic.story.rank3.at_fall");
			return;
		}

		data.flags.add(ChapterThreeRules.PATHFINDER_DONE);
		Story.commit(p, data);
		ChapterThreeBalance b = ChapterThreeService.balance();

		Progression.grantSkillPoints(p, b.rank3Sp);
		Progression.grantResonanceMilestone(p, "second_rank", b.rank3Resonance);
		Progression.addXp(p, b.rank3Xp, "rank_three");
		Skills.grant(p, SkillIds.PATHFINDERS_POISE);
		Identity.recordChoice(p, TITLE_PATHFINDER);
		Journal.unlockEntry(p, JournalEntries.PATHFINDER);
		StoryService.milestone(p, Story.get(p), QuestEffects.LESSON_RANK_THREE);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.rank3.pathfinder", Component.translatable("title.oldrepublic.pathfinder")));
	}

	/** The robe goes to the inventory (or drops for the owner). A missing W4 item is logged, never fatal. */
	private static void giveRobe(ServerPlayer p, Identifier id) {
		// API-CHECK: Registry#getValue returns the default entry (air) for a missing id, so the stack is empty.
		ItemStack robe = new ItemStack(BuiltInRegistries.ITEM.getValue(id));

		if (robe.isEmpty()) {
			OldRepublicMod.LOGGER.warn("Rank 3 robe {} is not registered; {} gets no robe", id, p.getName().getString());
			return;
		}

		Rewards.give(p, robe);
	}

	// ---------------------------------------------------------------- the fall's pedestal

	/**
	 * Fabric {@code UseBlockCallback}: an Independent who meets the Pathfinder's requirements uses
	 * the Courier's Fall pedestal, Tessaly Arn's figure forms from the Compass and the
	 * {@code pathfinder} dialogue opens. Every other use passes to the pedestal.
	 */
	static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p) || p.isSpectator()) {
			return InteractionResult.PASS;
		}

		StoryData data = Story.get(p);
		BlockPos fall = data.chapter3.fall();

		if (fall == null || data.storyPath() != Path.INDEPENDENT || hit.getBlockPos().distSqr(fall) > 2 || blocker(p) != null) {
			return InteractionResult.PASS;
		}

		EffectEvents.around(p.level(), QuestEffects.RANK_CEREMONY, Vec3.atBottomCenterOf(fall).add(0, 1, 0), null, p.getId(), 1f,
				QuestEffects.RANK_PATHFINDER);
		Dialogues.open(p, PATHFINDER_DIALOGUE, null);
		return InteractionResult.SUCCESS;
	}

	private static boolean near(ServerPlayer p, BlockPos pos) {
		return p.level() == p.level().getServer().overworld() && p.position().distanceToSqr(Vec3.atCenterOf(pos)) <= PEDESTAL_REACH * PEDESTAL_REACH;
	}

	/** A fail reason ({@code key|args}) as a chat line. */
	private static void sendReason(ServerPlayer p, String reason) {
		String[] parts = reason.split("\\|");
		Object[] args = new Object[parts.length - 1];
		System.arraycopy(parts, 1, args, 0, args.length);
		p.sendSystemMessage(Component.translatable(parts[0], args));
	}

	private RankThree() {
	}
}
