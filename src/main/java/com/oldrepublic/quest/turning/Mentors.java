package com.oldrepublic.quest.turning;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.vendor.hub.HubNpc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The turning's people as the player meets them (spec MILESTONE_5.md 8.3 to 8.5): using a mentor, a messenger or a
 * person of the detention post, the Compass at Courier's Fall, and the mentors who lost their blades.
 *
 * <ul>
 * <li>The old mentor who yielded reopens the confrontation's choice; the new mentor of a rite reopens the rite (through
 * {@code StoryMentors.setOtherPathTalk}); any other use of a mentor of the other path keeps the lead's cold line.</li>
 * <li>A player ready for rank 4 who uses their own mentor is offered the ceremony ({@code rank4_saelen},
 * {@code rank4_sorn}); a holder of rank 4 gets their robe again, once a day, when it is lost.</li>
 * <li>An Independent ready for the Wayfarer title uses the Compass's pedestal at Courier's Fall: Tessaly Arn's echo
 * speaks ({@code rank4_compass}).</li>
 * </ul>
 */
final class Mentors {
	/** How near the pedestal the Wayfarer's ceremony must happen, in blocks. */
	private static final double PEDESTAL_REACH = 8.0;
	/** The pedestal each player last opened the Compass's dialogue at (for the ceremony's effect). */
	private static final Map<UUID, Vec3> COMPASS = new HashMap<>();

	/** Fabric {@code UseEntityCallback}: the turning's uses of mentors and of its own people. */
	static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity) {
		if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer p) || p.isSpectator()) {
			return InteractionResult.PASS;
		}

		boolean handled = false;

		if (entity instanceof MentorEntity mentor) {
			handled = onMentorUsed(p, mentor);
		} else if (entity instanceof HubNpc npc) {
			handled = TurningOffers.onUsed(p, npc) || ReturnTask.onUsed(p, npc);
		}

		return handled ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** A mentor was used: the confrontation's choice, or rank 4 with the player's own mentor. Returns whether it was handled. */
	private static boolean onMentorUsed(ServerPlayer p, MentorEntity mentor) {
		if (!mentor.isOwnedBy(p) || mentor.isSparring() || mentor.isDeparting() || Spar.inSpar(p)) {
			return false;
		}

		if (Confrontations.choiceWaits(p, mentor)) {
			Confrontations.openChoice(p, mentor);
			return true;
		}

		StoryData data = Story.get(p);
		Path path = data.storyPath();

		if (mentor.role().path() != path || Identity.path(p) != path) {
			// The other path's mentor: the rite, through StoryMentors.setOtherPathTalk, or the cold line.
			return false;
		}

		if (RankFour.done(p)) {
			RankFour.reissue(p);
			return false;
		}

		if (RankFour.blocker(p) == null) {
			Dialogues.open(p, OldRepublicMod.id(mentor.role() == MentorRole.JEDI ? "rank4_saelen" : "rank4_sorn"), mentor);
			return true;
		}

		return false;
	}

	/** {@code StoryMentors.setOtherPathTalk}: the new mentor waiting for the rite opens it again. */
	static boolean otherPathTalk(ServerPlayer p, MentorEntity mentor) {
		if (Rites.waitsOn(p, mentor)) {
			Rites.open(p, mentor);
			return true;
		}

		return false;
	}

	/** {@code rank4:mentor}: the mentor's usual dialogue ("Something else"). */
	static void usualDialogue(ServerPlayer p, Entity speaker) {
		if (speaker instanceof MentorEntity mentor) {
			Dialogues.close(p);
			StoryMentors.onUsed(p, mentor);
		}
	}

	/**
	 * Fabric {@code UseBlockCallback}: an Independent at the Compass's pedestal at Courier's Fall, ready for the Wayfarer
	 * title, hears Tessaly Arn's echo; a Wayfarer who lost the mantle gets it again there, once a day.
	 */
	static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p) || p.isSpectator()) {
			return InteractionResult.PASS;
		}

		StoryData data = Story.get(p);
		BlockPos fall = data.chapter3.fall();

		if (fall == null || data.storyPath() != Path.INDEPENDENT || Identity.path(p) != Path.INDEPENDENT || hit.getBlockPos().distSqr(fall) > 2) {
			return InteractionResult.PASS;
		}

		if (RankFour.done(p)) {
			return RankFour.reissue(p) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}

		if (RankFour.blocker(p) != null) {
			return InteractionResult.PASS;
		}

		Vec3 top = Vec3.atBottomCenterOf(fall).add(0, 1, 0);
		COMPASS.put(p.getUUID(), top);
		EffectEvents.around(p.level(), QuestEffects.RANK_CEREMONY, top, null, p.getId(), 1f, QuestEffects.RANK_PATHFINDER);
		Dialogues.open(p, OldRepublicMod.id("rank4_compass"), null);
		return InteractionResult.SUCCESS;
	}

	/** {@code rank4:wayfarer}: the ceremony at the Compass (the player must still stand near its pedestal). */
	static void wayfarer(ServerPlayer p) {
		BlockPos fall = Story.get(p).chapter3.fall();

		if (fall == null || p.level() != p.level().getServer().overworld()
				|| p.position().distanceToSqr(Vec3.atCenterOf(fall)) > PEDESTAL_REACH * PEDESTAL_REACH) {
			p.sendSystemMessage(Component.translatable("turning.oldrepublic.rank4.at_compass"));
			return;
		}

		RankFour.wayfarer(p, COMPASS.getOrDefault(p.getUUID(), Vec3.atBottomCenterOf(fall).add(0, 1, 0)));
	}

	/**
	 * Slow tick: a mentor whose blade was taken in a turning (Saelen's crystal, Sorn's broken blade) stays without one,
	 * however often the mentor's own setup rebuilds it, until the rite they come to with a new blade.
	 */
	static void keepBladeless(ServerPlayer p) {
		StoryData data = Story.get(p);
		StoryData.Turning t = data.turning;
		boolean returnRite = t.stage() == TurningStage.RITE && t.kind() == TurningKind.RETURN;
		boolean fallRite = t.stage() == TurningStage.RITE && t.kind() == TurningKind.FALL;

		if (data.hasFlag(TurningRules.CRYSTAL_TAKEN) && !returnRite) {
			strip(p, MentorRole.JEDI);
		}

		if (data.hasFlag(TurningRules.BLADE_BROKEN) && !fallRite) {
			strip(p, MentorRole.SITH);
		}
	}

	private static void strip(ServerPlayer p, MentorRole role) {
		MentorEntity mentor = TurningService.mentor(p, role);

		if (mentor != null && !mentor.isSparring() && !mentor.getMainHandItem().isEmpty()) {
			mentor.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}

	static void forget(UUID player) {
		COMPASS.remove(player);
	}

	static void clear() {
		COMPASS.clear();
	}

	private Mentors() {
	}
}
