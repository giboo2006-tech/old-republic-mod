package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.ulvaine.UlvaineEffects;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.enemy.cartel.CartelEnforcer;
import com.oldrepublic.enemy.cartel.CartelEnforcers;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SpawnerGroups;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The counting-house and the factor (spec MILESTONE_5.md 6.3, 6.4): the counting-house mission that
 * starts with THE_FACTOR (its guards by the way taken: none after the kajidic sold Dask out, 6 with
 * the clan fighting beside the player, 4 after a Mind Trick, 8 after Fear or a forced door), Imrel
 * Dask spawned for its owner at {@code NPC_DASK} once it is cleared, Ulvaine's hologram on his desk,
 * and the choice of his fate, which moves the chapter on to ARMED (or BLOCKADE for an armed ship).
 */
final class CountingHouse {
	/** Dask's variant ({@code hub_host}, 6.4). */
	static final String DASK = "dask";
	/** The counting-house's guard posts, in order (3.2.1). */
	private static final List<SiteAnchors.Anchor> POSTS = List.of(SiteAnchors.Anchor.COUNTING_HOUSE_1, SiteAnchors.Anchor.COUNTING_HOUSE_2,
			SiteAnchors.Anchor.COUNTING_HOUSE_3, SiteAnchors.Anchor.COUNTING_HOUSE_4, SiteAnchors.Anchor.COUNTING_HOUSE_5,
			SiteAnchors.Anchor.COUNTING_HOUSE_6, SiteAnchors.Anchor.COUNTING_HOUSE_7, SiteAnchors.Anchor.COUNTING_HOUSE_8);
	/** Without the template's anchors, Dask's room is this far east of Saltgate's heart. */
	private static final int FALLBACK_OFFSET = 12;
	/** Paces the mission's automatic start while another mission is active. */
	private static final String START_TASK = "c5.counting_house.start";

	/**
	 * THE_FACTOR begins: Dask is in the counting-house. Its mission starts on the chapter's next slow
	 * tick when the player has no other (so the way's own mission, finishing now, is paid first).
	 */
	static void onTheFactor(ServerPlayer p) {
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c5.objective.the_factor"));
	}

	/** THE_FACTOR's slow tick: the mission's start, its site and fight, then Dask waiting upstairs. */
	static void tick(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (!data.hasFlag(ChapterFiveRules.COUNTING_HOUSE)) {
			if (ChapterFiveRules.COUNTING_HOUSE_RUN.equals(MissionService.activeId(p))) {
				drive(p);
			} else if (ChapterFiveService.due(p, START_TASK)) {
				startMission(p);
			}

			return;
		}

		if (data.chapter5.dask() == StoryData.Chapter5.DASK_NONE && ChapterFiveService.planet(p) == Planets.Planet.GHESSAR) {
			BlockPos desk = daskSpot(p.level());

			if (desk != null) {
				StoryNpcs.ensure(p, desk, DASK);
			}
		}
	}

	/** Starts the counting-house mission when nothing else is under way (retried while another mission is). */
	private static void startMission(ServerPlayer p) {
		if (MissionService.get(p).storyDone(ChapterFiveRules.COUNTING_HOUSE_RUN) || Story.get(p).hasFlag(ChapterFiveRules.COUNTING_HOUSE)) {
			return;
		}

		if (MissionService.activeId(p) != null || !MissionService.startStory(p, ChapterFiveRules.COUNTING_HOUSE_RUN, null)) {
			ChapterFiveService.retryLater(p, START_TASK, ChapterFiveService.balance().retryTicks);
		}
	}

	/**
	 * The mission's site is Dask's room; on arrival there the guards stand at their posts (and the
	 * clan's warriors beside the player); it is done when they are down. Peaceful, or no guards (the
	 * kajidic's way): done on arrival.
	 */
	private static void drive(ServerPlayer p) {
		ServerLevel level = p.level();
		BlockPos desk = Planets.Planet.of(level.dimension()) == Planets.Planet.GHESSAR ? daskSpot(level) : null;
		GlobalPos site = desk == null ? null : GlobalPos.of(level.dimension(), desk);

		if (site == null) {
			return;
		}

		if (!site.equals(MissionService.drivenSite(p)) && !StoryNpcs.fighting(p, ChapterFiveRules.COUNTING_HOUSE_RUN)) {
			MissionService.drive(p, ChapterFiveRules.COUNTING_HOUSE_RUN, site, 0, 1);
		}

		if (StoryNpcs.fighting(p, ChapterFiveRules.COUNTING_HOUSE_RUN)) {
			if (StoryNpcs.leftFight(p)) {
				StoryNpcs.endFight(p);
				return;
			}

			int left = StoryNpcs.foesLeft(p, ChapterFiveRules.COUNTING_HOUSE_RUN);
			int total = StoryNpcs.foesAtStart(p, ChapterFiveRules.COUNTING_HOUSE_RUN);
			MissionService.drive(p, ChapterFiveRules.COUNTING_HOUSE_RUN, site, total - left, Math.max(1, total));

			if (left == 0) {
				StoryNpcs.endFight(p);
				MissionService.completeDriven(p, ChapterFiveRules.COUNTING_HOUSE_RUN);
			}

			return;
		}

		if (!StoryNpcs.near(p, site.dimension(), desk, ChapterFiveService.balance().arriveRange) || !level.hasChunkAt(desk)) {
			return;
		}

		int guards = ChapterFiveRules.guards(Story.get(p), ChapterFiveService.balance());

		if (ChapterFiveRules.clearsOnArrival(guards, ChapterFiveService.peaceful(p))) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.counting_house.open"));
			MissionService.completeDriven(p, ChapterFiveRules.COUNTING_HOUSE_RUN);
			return;
		}

		List<LivingEntity> foes = spawnGuards(p, level, desk, guards);

		if (foes.isEmpty()) {
			MissionService.completeDriven(p, ChapterFiveRules.COUNTING_HOUSE_RUN);
			return;
		}

		List<LivingEntity> allies = ChapterFiveRules.clanAllies(Story.get(p))
				// In Dask's room upstairs (the column's top is the counting-house roof since docs/MILESTONE_6.md 5.7).
				? SpawnerGroups.spawn(level, desk.offset(-2, 0, 0), "hask_warriors", ChapterFiveService.balance().clanAllies, false, p.getUUID())
				: List.of();
		StoryNpcs.startFight(p, ChapterFiveRules.COUNTING_HOUSE_RUN, desk, foes, allies);
		p.sendSystemMessage(Component.translatable(allies.isEmpty() ? "message.oldrepublic.story.c5.counting_house.alarm"
				: "message.oldrepublic.story.c5.counting_house.clan"));
	}

	/**
	 * The guards at the counting-house posts, a captain leading when there are three or more (W8's
	 * enforcer kits); posts the template lacks fall back to a ring around Dask's room.
	 */
	private static List<LivingEntity> spawnGuards(ServerPlayer p, ServerLevel level, BlockPos desk, int count) {
		SitePiece hub = StoryNpcs.hub(level, Planets.Planet.GHESSAR);
		List<CartelEnforcer.Variant> kits = CartelEnforcers.squad(count);
		List<LivingEntity> out = new ArrayList<>(kits.size());

		for (int i = 0; i < kits.size(); i++) {
			BlockPos post = hub == null ? null : StructureLocator.anchor(hub, POSTS.get(i % POSTS.size()));
			BlockPos at = post != null ? post : StoryNpcs.ground(level, ring(desk, i, kits.size()));
			CartelEnforcer guard = CartelEnforcers.spawn(level, at, kits.get(i), p.getUUID());

			if (guard != null) {
				guard.setTarget(p);
				out.add(guard);
			}
		}

		return out;
	}

	/** The {@code i}th of {@code n} spots on a ring 6 blocks around {@code centre}. */
	private static BlockPos ring(BlockPos centre, int i, int n) {
		double angle = Math.PI * 2 * i / Math.max(1, n);
		return centre.offset((int) Math.round(Math.cos(angle) * 6), 0, (int) Math.round(Math.sin(angle) * 6));
	}

	/** Dask's room ({@code NPC_DASK}) in Saltgate, or near its heart without the anchor; null while Saltgate is not placed. */
	static @Nullable BlockPos daskSpot(ServerLevel level) {
		return Planets.Planet.of(level.dimension()) == Planets.Planet.GHESSAR
				? StoryNpcs.hubAnchor(level, Planets.Planet.GHESSAR, SiteAnchors.Anchor.NPC_DASK, FALLBACK_OFFSET)
				: null;
	}

	/** The counting-house is cleared: 1,500 XP and 150 credits, once; Dask waits upstairs (6.3). */
	static void pay(ServerPlayer p) {
		if (!ChapterFiveService.once(p, ChapterFiveRules.COUNTING_HOUSE)) {
			return;
		}

		ChapterFiveBalance b = ChapterFiveService.balance();
		ChapterFiveService.xp(p, b.countingHouseXp);
		Credits.give(p, b.countingHouseCredits);
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c5.objective.dask"));
	}

	// ---------------------------------------------------------------- the factor (6.4)

	/**
	 * {@code c5:hologram}: Dask plays Ulvaine's last message on his desk projector (W8 draws her
	 * figure, 15.3): entry {@code the_buyers_face}, codex {@code darth_ulvaine}.
	 */
	static void hologram(ServerPlayer p, @Nullable Entity speaker) {
		StoryData data = Story.get(p);

		if (data.chapter5.stage() != ChapterFiveStage.THE_FACTOR || !data.hasFlag(ChapterFiveRules.COUNTING_HOUSE)) {
			return;
		}

		Entity at = speaker != null ? speaker : p;
		EffectEvents.around(p.level(), UlvaineEffects.HOLOGRAM, at.position().add(0, 1.1, 0), null, at.getId(),
				ChapterFiveService.balance().hologramTicks, UlvaineEffects.HOLOGRAM_START);
		Journal.unlockEntry(p, JournalEntries.THE_BUYERS_FACE);
		Journal.unlockCodex(p, JournalEntries.CODEX_DARTH_ULVAINE);
	}

	/**
	 * {@code c5:dask <release|clan|end>}, Dask's fate (6.4): let him go (kajidic +40, light +3), hand
	 * him to the clan (clan +80, kajidic -60; only after meeting Varra) or end him (kajidic -100,
	 * clan -20, dark -5, entry {@code dask_ended}). Every choice: 300 XP, entry {@code the_factor},
	 * flag {@code c5.dask.<choice>}; he leaves, and the chapter moves to ARMED (BLOCKADE for a ship
	 * that already has cannons).
	 */
	static void choose(ServerPlayer p, @Nullable Entity speaker, String id) {
		int choice = ChapterFiveRules.daskChoice(id);
		StoryData data = Story.get(p);

		if (!ChapterFiveRules.daskChoiceOpen(data, choice)) {
			return;
		}

		data.chapter5 = data.chapter5.withDask(choice);
		data.flags.add(ChapterFiveRules.DASK_PREFIX + ChapterFiveRules.daskId(choice));
		Story.commit(p, data);
		ChapterFiveBalance b = ChapterFiveService.balance();
		String source = ChapterFiveRules.DASK_PREFIX + ChapterFiveRules.daskId(choice);

		switch (choice) {
			case StoryData.Chapter5.DASK_RELEASE -> {
				Identity.changeReputation(p, Faction.VESSBA_KAJIDIC, b.releaseRep, source, false);
				StoryService.shift(p, source, b.releaseLight, "quest.oldrepublic.c5.reason.release");
			}
			case StoryData.Chapter5.DASK_CLAN -> {
				Identity.changeReputation(p, Faction.CLAN_HASK, b.clanRep, source, false);
				Identity.changeReputation(p, Faction.VESSBA_KAJIDIC, b.clanKajidicRep, source, false);
			}
			case StoryData.Chapter5.DASK_END -> {
				Identity.changeReputation(p, Faction.VESSBA_KAJIDIC, b.endKajidicRep, source, false);
				Identity.changeReputation(p, Faction.CLAN_HASK, b.endClanRep, source, false);
				StoryService.shift(p, source, b.endDark, "quest.oldrepublic.c5.reason.end_dask");
				Journal.unlockEntry(p, JournalEntries.DASK_ENDED);
			}
			default -> {
			}
		}

		ChapterFiveService.xp(p, b.daskXp);
		Journal.unlockEntry(p, JournalEntries.THE_FACTOR);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.dask." + ChapterFiveRules.daskId(choice)));

		if (speaker instanceof HubNpc dask && p.getUUID().equals(dask.storyOwner())) {
			dask.discard();
		}

		boolean armed = ShipRegistry.of(p.level().getServer(), p.getUUID()).map(Ships::hasWeapons).orElse(false);
		ChapterFiveService.enterStage(p, ChapterFiveRules.afterFactor(armed));
	}

	private CountingHouse() {
	}
}
