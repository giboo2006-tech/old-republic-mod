package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.Poachers;
import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.mission.MissionFeature;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.Skills;
import com.oldrepublic.world.EchoCairns;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * Chapter Three's openings (spec 6.2): the Jedi "Listen" and Sith "The Hunt" lessons with their
 * cairns near the camp, and the Independent's Courier's Fall with its scavengers, the Compass and
 * the three caches. Touching all three hidden stones (or caches) pays LISTENING and points the
 * Force at the first echo.
 */
final class ChapterThreeOpenings {
	static final Identifier COURIERS_FALL = OldRepublicMod.id("story/couriers_fall");
	static final ResourceKey<LootTable> WAYFARER_CACHE = ResourceKey.create(Registries.LOOT_TABLE, OldRepublicMod.id("gameplay/wayfarer_cache"));
	/** The fall's template is 15 x 11: its footprint must be ground, its clearance free of built blocks. */
	private static final int FALL_FOOTPRINT = 15;
	private static final int FALL_CLEARANCE = 19;
	/** The fall's crater floor and hull keel sit in the ground ({@code gen_structures_story_m3.py}). */
	static final int FALL_BURIED = 2;
	/** Each scavenger anchor gets one poacher. */
	private static final List<SiteAnchors.Anchor> SCAVENGERS = List.of(SiteAnchors.Anchor.SCAVENGER_1, SiteAnchors.Anchor.SCAVENGER_2);

	// ---------------------------------------------------------------- Jedi and Sith

	/**
	 * "I will listen." / "Show me the hunt.": Force Sense for free, the hood and robe of the path's
	 * set, the entry, and the cairns (LISTENING). Once.
	 */
	static void acceptLesson(ServerPlayer p) {
		StoryData data = Story.get(p);
		Path path = data.storyPath();

		if (!ChapterThreeService.mentorPath(path) || data.chapter3.stage() != ChapterThreeStage.SUMMONS || !data.flags.add(ChapterThreeRules.LESSON_ACCEPTED)) {
			return;
		}

		Story.commit(p, data);
		MentorRole role = MentorRole.of(path);
		ArmourSet set = path == Path.JEDI ? ArmourSet.JEDI_ROBES : ArmourSet.SITH_ROBES;
		Skills.grant(p, SkillIds.FORCE_SENSE);
		give(p, ArmourSets.piece(set, EquipmentSlot.HEAD));
		give(p, ArmourSets.piece(set, EquipmentSlot.CHEST));
		Journal.unlockEntry(p, path == Path.JEDI ? JournalEntries.LISTEN : JournalEntries.THE_HUNT);
		Journal.unlockCodex(p, JournalEntries.CODEX_FORCE_SENSE);
		StoryService.mentorSays(p, role, "c3.lesson.task");
		ChapterThreeService.enterStage(p, ChapterThreeStage.LISTENING);
	}

	private static void give(ServerPlayer p, ItemStack stack) {
		if (stack.isEmpty()) {
			OldRepublicMod.LOGGER.warn("The Chapter Three lesson gift for {} is missing (armour not registered)", p.getName().getString());
			return;
		}

		Rewards.give(p, stack);
	}

	// ---------------------------------------------------------------- LISTENING

	/**
	 * Places the player's three hidden stones: cairns within {@code cairnRadius} of the camp (Jedi,
	 * Sith) or caches {@code cacheMin} to {@code cacheMax} from the fall (Independent). Nothing placed
	 * retries every {@code retryTicks}; after {@code forwardFallbackAfter} failures the centre moves to
	 * the M2 forward fallback, 12 blocks ahead of the player.
	 */
	static void placeHidden(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.chapter3.stage() != ChapterThreeStage.LISTENING || !data.chapter3.cairns().isEmpty()) {
			return;
		}

		ChapterThreeBalance b = ChapterThreeService.balance();

		if (!StoryService.inOverworld(p)) {
			ChapterThreeService.retryLater(p, "cairns", b.retryTicks);
			return;
		}

		boolean caches = data.storyPath() == Path.INDEPENDENT;
		BlockPos centre = caches ? data.chapter3.fall() : data.camp;
		int failures = data.counter(ChapterThreeRules.PLACE_FAILURES);

		if (centre == null || failures >= b.forwardFallbackAfter) {
			centre = BlockPos.containing(forwardSpot(p));
		}

		List<BlockPos> placed = caches
				? EchoCairns.place(p, centre, b.cacheMin, b.cacheMax, b.cairnSpacing, b.cairnCount, true)
				: EchoCairns.place(p, centre, 0, b.cairnRadius, b.cairnSpacing, b.cairnCount, false);
		StoryData fresh = Story.get(p);

		if (placed.isEmpty()) {
			fresh.counters.merge(ChapterThreeRules.PLACE_FAILURES, 1, Integer::sum);
			Story.commit(p, fresh);
			ChapterThreeService.retryLater(p, "cairns", b.retryTicks);
			OldRepublicMod.LOGGER.warn("Could not place Chapter Three {} for {} around {}; retrying in {} ticks", caches ? "caches" : "cairns",
					p.getName().getString(), centre, b.retryTicks);
			return;
		}

		fresh.chapter3 = ChapterThreeRules.withCairns(fresh.chapter3, placed);
		fresh.counters.remove(ChapterThreeRules.PLACE_FAILURES);
		Story.commit(p, fresh);
		ChapterThreeService.clearRetry(p, "cairns");
		p.sendSystemMessage(Component.translatable(caches ? "message.oldrepublic.story.c3.caches_placed" : "message.oldrepublic.story.c3.cairns_placed"));
	}

	/**
	 * The player touched their cairn or cache {@code index}: the stone settles into dust, one line is
	 * heard (a cache also pays its loot), and the third pays LISTENING.
	 */
	static void onTouched(ServerPlayer p, BlockPos pos, int index, boolean cache) {
		StoryData data = Story.get(p);
		StoryData.Chapter3 c = data.chapter3;
		Path path = data.storyPath();

		if (c.stage() != ChapterThreeStage.LISTENING || cache != (path == Path.INDEPENDENT)) {
			return;
		}

		int mask = ChapterThreeRules.touch(c.cairnsTouched(), index, c.cairns().size());

		if (mask == c.cairnsTouched()) {
			return;
		}

		data.chapter3 = ChapterThreeRules.withTouched(c, mask);
		Story.commit(p, data);
		int touched = ChapterThreeRules.touched(mask);
		int needed = c.cairns().size();
		Vec3 at = Vec3.atCenterOf(pos);
		Vec3 toEyes = p.getEyePosition().subtract(at);
		Vec3 dir = toEyes.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : toEyes.normalize();
		EffectEvents.around(p.level(), QuestEffects.CAIRN_TOUCH, at, dir, p.getId(), 1f, cache ? QuestEffects.CAIRN_CACHE : QuestEffects.CAIRN_STONE);

		if (cache) {
			Rewards.giveLoot(p, WAYFARER_CACHE, Rewards.seed(p, "wayfarer_cache", index));
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.cache." + Math.min(touched, 3)));
		} else {
			StoryService.mentorSays(p, MentorRole.of(path), "c3.cairn." + Math.min(touched, 3));
		}

		p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.c3.listening", touched, needed));

		if (touched >= needed) {
			completeListening(p);
		}
	}

	/** All three found: XP, trust and standing (Frontier for an Independent), the entry, then the first echo. */
	private static void completeListening(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (!data.flags.add(ChapterThreeRules.LISTENING_PAID)) {
			return;
		}

		Story.commit(p, data);
		ChapterThreeBalance b = ChapterThreeService.balance();
		Path path = data.storyPath();

		if (path == Path.INDEPENDENT) {
			Progression.addXp(p, b.cacheXp, "chapter_three");
			Identity.changeReputation(p, Faction.FRONTIER, b.lessonRep, "c3.listening", true);
		} else {
			MentorRole role = MentorRole.of(path);
			Progression.addXp(p, b.lessonXp, "chapter_three");
			StoryService.trust(p, role, b.lessonTrust, "quest.oldrepublic.c3.reason.listening");
			Identity.changeReputation(p, role.faction(), b.lessonRep, "c3.listening", true);
		}

		Journal.unlockEntry(p, JournalEntries.WHAT_THE_FORCE_HIDES);
		ChapterThreeService.enterStage(p, ChapterThreeStage.FIRST_ECHO);
	}

	// ---------------------------------------------------------------- Courier's Fall

	/**
	 * Places Courier's Fall for an Independent on {@code fallRings} (then {@code fallFallback}) from
	 * the player; after {@code forwardFallbackAfter} failures, 12 blocks ahead. A failure retries
	 * every {@code retryTicks}. The fall is always built from blocks: the Compass's pedestal is in it.
	 */
	static void placeFall(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.chapter3.fall() != null || data.storyPath() != Path.INDEPENDENT) {
			return;
		}

		ChapterThreeBalance b = ChapterThreeService.balance();

		if (!StoryService.inOverworld(p)) {
			ChapterThreeService.retryLater(p, "fall", b.retryTicks);
			return;
		}

		ServerLevel level = p.level();
		StoryBalance sb = StoryService.balance();
		List<BlockPos> avoid = Crossing.otherSites(p);
		Crossing.addSites(avoid, data);
		MeetingSites.Rules rules = new MeetingSites.Rules(FALL_FOOTPRINT, FALL_CLEARANCE, sb.maxSlope, Crossing.forbidden(level), avoid, sb.siteMinSeparation);
		int failures = data.counter(ChapterThreeRules.PLACE_FAILURES);
		RuntimeSites.Placement placement = failures >= b.forwardFallbackAfter
				? forwardFallback(p)
				: RuntimeSites.placeSurface(level, COURIERS_FALL, p.position(), p.getLookAngle(), b.fallRings, b.fallFallback, rules, FALL_BURIED, p.getUUID());
		StoryData fresh = Story.get(p);

		if (placement == null) {
			fresh.counters.merge(ChapterThreeRules.PLACE_FAILURES, 1, Integer::sum);
			Story.commit(p, fresh);
			ChapterThreeService.retryLater(p, "fall", b.retryTicks);
			OldRepublicMod.LOGGER.warn("Could not place Courier's Fall for {} near {}; retrying in {} ticks", p.getName().getString(), p.blockPosition(),
					b.retryTicks);
			return;
		}

		BlockPos pedestal = SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.PEDESTAL_WAYFARER);
		fresh.chapter3 = ChapterThreeRules.withFall(fresh.chapter3, pedestal != null ? pedestal : placement.origin());
		fresh.counters.remove(ChapterThreeRules.PLACE_FAILURES);
		Story.commit(p, fresh);
		ChapterThreeService.clearRetry(p, "fall");
	}

	/** The M2 forward fallback: the fall centred 12 blocks ahead of the player, its cargo bay facing them. */
	private static RuntimeSites.@Nullable Placement forwardFallback(ServerPlayer p) {
		ServerLevel level = p.level();
		Vec3 spot = forwardSpot(p);
		BlockPos stand = BlockPos.containing(spot);
		double dx = p.getX() - spot.x;
		double dz = p.getZ() - spot.z;
		Direction toPlayer = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		Rotation rotation = RuntimeSites.facing(toPlayer);

		try {
			return RuntimeSites.placeAt(level, COURIERS_FALL, stand, rotation, FALL_BURIED, p.getUUID());
		} catch (IllegalArgumentException | IllegalStateException e) {
			OldRepublicMod.LOGGER.warn("Courier's Fall forward fallback failed for {}: {}", p.getName().getString(), e.getMessage());
			return null;
		}
	}

	/** The ground {@code fallbackForward} blocks ahead of the player (horizontally). */
	static Vec3 forwardSpot(ServerPlayer p) {
		Vec3 flat = p.getLookAngle().horizontal();
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 ahead = p.position().add(dir.scale(StoryService.balance().fallbackForward));
		return StoryMentors.ground(p.level(), ahead.x, ahead.z);
	}

	/**
	 * Slow tick: the two scavengers appear when the owner first comes within
	 * {@code missions.siteSpawnDistance} of the fall (the runtime-site rule, 3.6).
	 */
	static void tickFall(ServerPlayer p) {
		StoryData data = Story.get(p);
		BlockPos fall = data.chapter3.fall();
		double reach = MissionFeature.balance().siteSpawnDistance;

		if (fall == null || data.chapter3.stage() != ChapterThreeStage.SUMMONS || data.hasFlag(ChapterThreeRules.FALL_CREW) || !StoryService.inOverworld(p)
				|| p.blockPosition().distSqr(fall) > reach * reach) {
			return;
		}

		ServerLevel level = p.level();
		SitePiece piece = StructureLocator.pieceAt(level, fall);
		List<BlockPos> spots = new ArrayList<>();

		for (SiteAnchors.Anchor anchor : SCAVENGERS) {
			BlockPos spot = piece == null ? null : StructureLocator.anchor(piece, anchor);
			spots.add(spot != null ? spot : fall.offset(spots.isEmpty() ? 3 : -3, 0, 2));
		}

		data.flags.add(ChapterThreeRules.FALL_CREW);
		Story.commit(p, data);

		for (BlockPos spot : spots) {
			Poachers.spawnCrew(level, spot, 1, p.getUUID());
		}

		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.fall_crew"));
	}

	/**
	 * The Compass was attuned at the fall: Tessaly Arn's lesson, Vasch's sealed note from the
	 * strongbox, and the three caches (LISTENING).
	 */
	static void onCompassAttuned(ServerPlayer p) {
		Skills.grant(p, SkillIds.FORCE_SENSE);
		Journal.unlockEntry(p, JournalEntries.COURIERS_FALL);
		ChapterThreeService.addJournalNote(p, JournalEntries.COURIERS_FALL, "quest.oldrepublic.c3.note.vasch_invitation");
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.vasch_note"));
		ChapterThreeService.enterStage(p, ChapterThreeStage.LISTENING);
	}

	private ChapterThreeOpenings() {
	}
}
