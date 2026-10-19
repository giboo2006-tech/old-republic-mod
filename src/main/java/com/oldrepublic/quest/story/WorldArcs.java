package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.construct.Sentinels;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.mission.MissionDefinition;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.planet.Heartstones;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillRules;
import com.oldrepublic.skill.Skills;
import com.oldrepublic.vendor.VendorItems;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The world arcs (spec MILESTONE_4.md 8.4): the arc missions' progress and pay, the two tomb and
 * stone trials (the Heartstone's cleansing and Zarvos's Sepulchre), Tessaly's teaching, the arc's
 * power under the slot rule (a granted power takes the first free slot; with none free a hint
 * shows and the step waits, 9), the expeditions and their second crystal, "Not now", and the end of
 * WORLD_ARC.
 *
 * <p>Arc missions are story missions whose objective this class drives
 * ({@code "driven": true} in their JSON): {@link MissionService#drive} points the tracker and counts
 * progress, and {@link MissionService#completeDriven} completes them, which pays through
 * {@link #payMission}. Trials live in memory per player: leaving the site, death, logout or a
 * dimension change ends a trial and its spawns, and it starts again on return.
 */
public final class WorldArcs {
	static final String TRIAL_TAG = "oldrepublic.c4_trial";
	/** A trial's spawns that are corrupted beasts carry this tag too (W6 gives them their dark-veined look). */
	public static final String CORRUPTED_TAG = "oldrepublic.corrupted";
	private static final Identifier HOLLOWHORN = OldRepublicMod.id("hollowhorn");
	private static final Identifier ASH_HOUND = OldRepublicMod.id("ash_hound");
	/** The trial is abandoned when the player goes this many rings away from it. */
	private static final double LEAVE_FACTOR = 3.0;
	/** Zarvos's echo seizes the player within this distance. */
	private static final double ECHO_REACH = 5.0;
	/** Tessaly teaches within this distance of the cache's centre. */
	private static final double CACHE_REACH = 5.0;
	/** How often the slot-full hint and the "Raise Barrier" hint repeat. */
	private static final int HINT_TICKS = 60;
	/** A worldgen waystone or tomb is searched for this far. */
	private static final int SEARCH_RADIUS = 768;
	/** How many rooms the Sepulchre's trial has. */
	private static final int ROOMS = 3;

	private enum Kind {
		HEARTSTONE,
		SEPULCHRE
	}

	/** One player's running trial (memory only). */
	private static final class Trial {
		final Kind kind;
		final ResourceKey<Level> dimension;
		final BlockPos site;
		final long start;
		final List<UUID> spawned = new ArrayList<>();
		int waves;
		boolean granted;
		boolean raisedSinceLastWave;
		int room;
		boolean roomSpawned;
		@Nullable UUID echo;
		long gripAt = -1;
		boolean gripDone;

		Trial(Kind kind, ResourceKey<Level> dimension, BlockPos site, long start) {
			this.kind = kind;
			this.dimension = dimension;
			this.site = site;
			this.start = start;
		}
	}

	private static final Map<UUID, Trial> TRIALS = new HashMap<>();
	/** Game time of the last hint per player. */
	private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

	// ---------------------------------------------------------------- the arc

	/** WORLD_ARC begins: a power already owned completes its step; an Independent's road starts at once. */
	static void onArcBegins(ServerPlayer p) {
		StoryData data = Story.get(p);
		Path path = data.storyPath();

		if (Skills.rank(p, ChapterFourRules.arcPower(path)) > 0) {
			setStep(p, ChapterFourRules.STEP_POWER);
		}

		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c4.objective.arc." + (path == Path.JEDI ? "jedi" : path == Path.SITH ? "sith" : "independent")
				+ ".first"));

		if (path == Path.INDEPENDENT || path == Path.NONE) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.compass_hums"));
			MissionService.startStory(p, ChapterFourRules.TESSALYS_ROAD, null);
		}

		checkArcDone(p);
	}

	/** Sets an arc step bit. */
	static void setStep(ServerPlayer p, int step) {
		StoryData data = Story.get(p);
		StoryData.Chapter4.Arc arc = data.chapter4.arc();

		if (arc.has(step)) {
			return;
		}

		data.chapter4 = data.chapter4.withArc(arc.withMissionFlags(arc.missionFlags() | step));
		Story.commit(p, data);
	}

	/** WORLD_ARC ends when the power is owned and the expedition is done or skipped; HUNTED counts the clues held. */
	static void checkArcDone(ServerPlayer p) {
		StoryData.Chapter4 c = Story.get(p).chapter4;

		if (c.stage() == ChapterFourStage.WORLD_ARC && ChapterFourRules.arcDone(c.arc())) {
			ChapterFourService.enterStage(p, ChapterFourStage.HUNTED);
		}
	}

	/** {@code c4:not_now}: the host hears that the expedition can wait (8.4). */
	static void notNow(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (!ChapterFourRules.mayDeclineExpedition(data.chapter4)) {
			return;
		}

		data.chapter4 = data.chapter4.withArc(data.chapter4.arc().withExpedition(ChapterFourRules.EXPEDITION_SKIPPED));
		Story.commit(p, data);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.not_now"));
		checkArcDone(p);
	}

	/** {@code c4:both_doors <greenwatch|proving>}: the Independent's favour for the other hub (8.4). */
	static void bothDoors(ServerPlayer p, @Nullable Entity speaker, String door) {
		String which = door.trim();

		if (!"greenwatch".equals(which) && !"proving".equals(which)) {
			return;
		}

		StoryData data = Story.get(p);

		if (data.hasFlag(ChapterFourRules.BOTH_DOORS_PREFIX + "greenwatch") || data.hasFlag(ChapterFourRules.BOTH_DOORS_PREFIX + "proving")) {
			if (!data.hasFlag(ChapterFourRules.BOTH_DOORS_PREFIX + which)) {
				return;
			}
		} else {
			data.flags.add(ChapterFourRules.BOTH_DOORS_PREFIX + which);
			Story.commit(p, data);
		}

		MissionService.startStory(p, ChapterFourRules.BOTH_DOORS, speaker);
	}

	/**
	 * Whether an Independent may take Both Doors at {@code door}'s host: the arc has begun, the
	 * mission is not done, and no other door was chosen (8.4).
	 */
	static boolean bothDoorsOpen(ServerPlayer p, String door) {
		StoryData data = Story.get(p);
		Path path = data.storyPath();

		if (path == Path.JEDI || path == Path.SITH || data.chapter4.stage().ordinal() < ChapterFourStage.WORLD_ARC.ordinal()
				|| (!"greenwatch".equals(door) && !"proving".equals(door)) || MissionService.get(p).storyDone(ChapterFourRules.BOTH_DOORS)) {
			return false;
		}

		String chosen = bothDoorsChoice(data);
		return chosen == null || chosen.equals(door);
	}

	/** Whether the Hollow's crystal waits for its hue (the {@code c4:crystal} condition). */
	static boolean crystalPending(StoryData data) {
		return data.hasFlag(ChapterFourRules.CRYSTAL_PENDING_PREFIX + "singing_hollow");
	}

	/** Whether the player can hand a relic to the host now (the {@code c4:relic} condition). */
	static boolean relicReady(ServerPlayer p) {
		String active = MissionService.activeId(p);
		boolean proving = ChapterFourRules.PROVING.equals(active)
				|| (ChapterFourRules.BOTH_DOORS.equals(active) && "proving".equals(bothDoorsChoice(Story.get(p))));
		return proving && carriesRelic(p);
	}

	/**
	 * Why the host's arc mission {@code id} cannot be offered (the {@code c4:arc} condition), or
	 * null: the arc has begun and the mission is the path's (Jedi: The Greenwatch and The Heartstone;
	 * Sith: The Proving and Zarvos's Sepulchre), in either order. The mission's own rules
	 * ({@code mission:available}) are checked beside it.
	 */
	static @Nullable String arcMissionBlocker(ServerPlayer p, String id, @Nullable Entity speaker) {
		StoryData data = Story.get(p);
		Path path = data.storyPath();
		boolean ours = switch (id) {
			case ChapterFourRules.GREENWATCH, ChapterFourRules.HEARTSTONE -> path == Path.JEDI;
			case ChapterFourRules.PROVING, ChapterFourRules.SEPULCHRE -> path == Path.SITH;
			default -> false;
		};

		return ours && data.chapter4.stage().ordinal() >= ChapterFourStage.WORLD_ARC.ordinal() ? null : "message.oldrepublic.story.blocked.not_now";
	}

	/** Which door an Independent took for Both Doors, or null. */
	static @Nullable String bothDoorsChoice(StoryData data) {
		if (data.hasFlag(ChapterFourRules.BOTH_DOORS_PREFIX + "greenwatch")) {
			return "greenwatch";
		}

		return data.hasFlag(ChapterFourRules.BOTH_DOORS_PREFIX + "proving") ? "proving" : null;
	}

	// ---------------------------------------------------------------- mission pay (8.2, 8.4)

	/**
	 * Pays a completed Chapter Four story mission (from {@code MissionService.complete}): XP, 150
	 * credits (100 for "A Part Off the Books"), the giver's standing, the arc power's trust and
	 * codex, the journal entry, and the arc's step.
	 */
	public static void payMission(ServerPlayer p, MissionDefinition def) {
		ChapterFourBalance b = ChapterFourService.balance();
		String id = def.id();
		String source = "mission." + id;

		if (ChapterFourRules.OFF_THE_BOOKS.equals(id)) {
			ChapterFourService.xp(p, b.offTheBooksXp);
			com.oldrepublic.economy.Credits.give(p, b.offTheBooksCredits);
			StoryData data = Story.get(p);
			data.flags.add(ChapterFourRules.MOTIVATOR_ROUTE_PREFIX + "smuggler");
			Story.commit(p, data);
			// The camp cleared, the freighter's motivator is in the poachers' cache (the mission is a `clear` of the M3 raider camp).
			Rewards.give(p, new ItemStack(M4Items.HYPERDRIVE_MOTIVATOR));
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.motivator_found"));
		} else {
			boolean power = ChapterFourRules.isPowerMission(id);
			ChapterFourService.xp(p, power ? b.arcPowerMissionXp : b.arcMissionXp);
			com.oldrepublic.economy.Credits.give(p, b.arcMissionCredits);
			Faction faction = missionFaction(Story.get(p), id);

			if (faction != null) {
				Identity.changeReputation(p, faction, power ? b.arcPowerRep : b.arcRep, source, true);
			}

			if (power) {
				Path path = Story.get(p).storyPath();

				if (path == Path.JEDI || path == Path.SITH) {
					StoryService.trust(p, MentorRole.of(path), b.arcPowerTrust, "quest.oldrepublic.c4.reason.arc_power");
				}

				Journal.unlockCodex(p, switch (id) {
					case ChapterFourRules.HEARTSTONE -> JournalEntries.CODEX_FORCE_BARRIER;
					case ChapterFourRules.SEPULCHRE -> JournalEntries.CODEX_FORCE_CHOKE;
					default -> JournalEntries.CODEX_FORCE_SPEED;
				});
			}

			setStep(p, ChapterFourRules.stepOf(id));
		}

		if (def.entry() != null) {
			Journal.unlockEntry(p, def.entry());
		}

		checkArcDone(p);
	}

	/** The faction a mission's standing goes to. */
	private static @Nullable Faction missionFaction(StoryData data, String id) {
		return switch (id) {
			case ChapterFourRules.GREENWATCH, ChapterFourRules.HEARTSTONE -> Faction.JEDI_ORDER;
			case ChapterFourRules.PROVING, ChapterFourRules.SEPULCHRE -> Faction.SITH_EMPIRE;
			case ChapterFourRules.TESSALYS_ROAD -> Faction.FRONTIER;
			case ChapterFourRules.BOTH_DOORS -> "proving".equals(bothDoorsChoice(data)) ? Faction.SITH_EMPIRE : Faction.JEDI_ORDER;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- the power (9, revision 2)

	/** Whether the player owns the node. */
	static boolean owns(ServerPlayer p, String node) {
		return Skills.rank(p, node) > 0;
	}

	/**
	 * Grants a story power under the slot rule: an owned power counts at once; a new one takes the
	 * first free slot; with none free a hint shows (every few seconds) and false is returned so the
	 * step waits and asks again.
	 *
	 * @return whether the player owns the power now
	 */
	public static boolean grantPower(ServerPlayer p, String node) {
		if (owns(p, node)) {
			return true;
		}

		SkillData skills = Skills.data(p);

		if (skills != null && SkillRules.firstFreeSlot(skills) < 0) {
			hint(p, Component.translatable("message.oldrepublic.story.c4.slot_full", Component.translatable("ability.oldrepublic." + node)));
			return false;
		}

		Skills.grant(p, node);

		if (!owns(p, node)) {
			OldRepublicMod.LOGGER.warn("Granting {} to {} did not take (is the node in the skill trees?)", node, p.getName().getString());
			return false;
		}

		StoryData data = Story.get(p);
		data.flags.add(ChapterFourRules.POWER_GRANTED);
		Story.commit(p, data);

		if (node.equals(ChapterFourRules.arcPower(data.storyPath()))) {
			setStep(p, ChapterFourRules.STEP_POWER);
		}

		return true;
	}

	private static void hint(ServerPlayer p, Component line) {
		long now = StoryService.now(p);
		Long last = LAST_HINT.get(p.getUUID());

		if (last == null || now - last >= HINT_TICKS) {
			LAST_HINT.put(p.getUUID(), now);
			p.sendOverlayMessage(line);
		}
	}

	// ---------------------------------------------------------------- driving the missions

	/** Slow checks from WORLD_ARC on: the expedition's site, the active arc mission's site, its trial's start, Tessaly's teaching, a waiting power. */
	static void slowTick(ServerPlayer p) {
		expeditionSite(p);
		String active = MissionService.activeId(p);

		if (active == null || !ChapterFourRules.isChapterMission(active)) {
			return;
		}

		StoryData data = Story.get(p);

		switch (active) {
			case ChapterFourRules.HEARTSTONE -> sharedSite(p, active, PlanetSites.Site.HEARTSTONE, Planets.Planet.SELVAREN, Kind.HEARTSTONE);
			case ChapterFourRules.SEPULCHRE -> sharedSite(p, active, PlanetSites.Site.SEPULCHRE, Planets.Planet.VARKHAST, Kind.SEPULCHRE);
			case ChapterFourRules.TESSALYS_ROAD -> tessalysRoad(p, data);
			case ChapterFourRules.GREENWATCH -> pointAt(p, active, SiteKind.WAYSTONE);
			case ChapterFourRules.PROVING -> pointAt(p, active, SiteKind.GREATER_TOMB);
			case ChapterFourRules.BOTH_DOORS -> pointAt(p, active, "proving".equals(bothDoorsChoice(data)) ? SiteKind.GREATER_TOMB : SiteKind.WAYSTONE);
			default -> {
			}
		}
	}

	/**
	 * The crystal expedition the player may take on {@code planet} now (8.4), or null: none before the
	 * arc's power is learned or once that expedition is done; during WORLD_ARC only the path's own (the
	 * Hollow for a Jedi, the rift for a Sith, either for an Independent), after it either one.
	 */
	static PlanetSites.@Nullable Site expeditionOn(StoryData data, Planets.Planet planet) {
		StoryData.Chapter4 c = data.chapter4;
		PlanetSites.Site site = switch (planet) {
			case SELVAREN -> PlanetSites.Site.SINGING_HOLLOW;
			case VARKHAST -> PlanetSites.Site.DUSKGLASS_RIFT;
			default -> null;
		};

		if (site == null || c.stage().ordinal() < ChapterFourStage.WORLD_ARC.ordinal() || !c.arc().has(ChapterFourRules.STEP_POWER)
				|| data.hasFlag(ChapterFourRules.EXPEDITION_PREFIX + site.name().toLowerCase(java.util.Locale.ROOT))) {
			return null;
		}

		Planets.Planet own = ChapterFourRules.pathWorld(data.storyPath());
		return c.stage() == ChapterFourStage.WORLD_ARC && own != null && own != planet ? null : site;
	}

	/**
	 * Places the expedition the player may take on the world they stand on (3.4: 300 to 450 blocks out
	 * from the hub). {@link PlanetSites#ensure} places it once they come within reach of its ring point
	 * and paces its own retries; the Calling points at the ring point until then (StoryGuidance).
	 */
	private static void expeditionSite(ServerPlayer p) {
		Planets.Planet planet = Planets.Planet.of(p.level().dimension());
		PlanetSites.Site site = planet == null ? null : expeditionOn(Story.get(p), planet);

		if (site != null && PlanetSites.find(p, site) == null) {
			PlanetSites.ensure(p, site, planet);
		}
	}

	/** Points the tracker at the nearest worldgen site of {@code kind} the player has not done yet, while the mission still needs one. */
	private static void pointAt(ServerPlayer p, String id, SiteKind kind) {
		if (MissionService.drivenSite(p) == null && ChapterFourService.due(p, "point:" + id) && Planets.isPlanet(p.level())) {
			ChapterFourService.retryLater(p, "point:" + id, ChapterFourService.balance().retryTicks / 4);
			MissionService.pointAtNearest(p, id, List.of(kind), SEARCH_RADIUS);
		}
	}

	/** A shared trial site (the Heartstone, the Sepulchre): placed when the step begins, then the trial starts in its ring. */
	private static void sharedSite(ServerPlayer p, String id, PlanetSites.Site site, Planets.Planet planet, Kind kind) {
		GlobalPos at = MissionService.drivenSite(p);

		if (at == null && ChapterFourService.due(p, "site:" + id)) {
			at = PlanetSites.ensure(p, site, planet);

			if (at == null) {
				ChapterFourService.retryLater(p, "site:" + id, ChapterFourService.balance().retryTicks / 4);
				return;
			}

			MissionService.drive(p, id, at, 0, 1);
		}

		if (at == null || TRIALS.containsKey(p.getUUID()) || !p.level().dimension().equals(at.dimension())
				|| p.position().distanceToSqr(Vec3.atCenterOf(at.pos())) > sq(ChapterFourService.balance().trialRing)) {
			return;
		}

		// The trial grants its power mid-fight, when slots cannot change (the combat lock), so with every
		// slot full it waits for one to be freed before it begins, with the slot rule's hint (9).
		String power = kind == Kind.HEARTSTONE ? "force_barrier" : "force_choke";
		SkillData skills = Skills.data(p);

		if (!owns(p, power) && skills != null && SkillRules.firstFreeSlot(skills) < 0) {
			hint(p, Component.translatable("message.oldrepublic.story.c4.slot_full", Component.translatable("ability.oldrepublic." + power)));
			return;
		}

		TRIALS.put(p.getUUID(), new Trial(kind, at.dimension(), at.pos(), StoryService.now(p)));
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.trial." + kind.name().toLowerCase(java.util.Locale.ROOT) + ".begin"));
	}

	/** Tessaly's Road: the cache placed on the Independent's world; at its centre Tessaly teaches Speed and its Wayfarer branch. */
	private static void tessalysRoad(ServerPlayer p, StoryData data) {
		GlobalPos at = MissionService.drivenSite(p);
		Planets.Planet planet = data.chapter4.arcPlanet();

		if (at == null && planet != null && ChapterFourService.due(p, "site:tessaly")) {
			at = PlanetSites.ensure(p, PlanetSites.Site.WAYFARER_CACHE, planet);

			if (at == null) {
				ChapterFourService.retryLater(p, "site:tessaly", ChapterFourService.balance().retryTicks / 4);
				return;
			}

			MissionService.drive(p, ChapterFourRules.TESSALYS_ROAD, at, 0, 1);
		}

		if (at != null && p.level().dimension().equals(at.dimension()) && p.position().distanceToSqr(Vec3.atCenterOf(at.pos())) <= CACHE_REACH * CACHE_REACH) {
			teachSpeed(p);
		}
	}

	/**
	 * Tessaly teaches Force Speed and {@code speed_wayfarer} (8.4). A player who bought
	 * {@code force_speed} first gets the points they spent back (once), because the grant itself
	 * never refunds. Completes the mission once both are owned.
	 */
	public static void teachSpeed(ServerPlayer p) {
		SkillData skills = Skills.data(p);
		Integer spent = skills == null ? null : skills.spent.get("force_speed");

		if (spent != null && spent > 0 && ChapterFourService.once(p, ChapterFourRules.SPEED_REFUND)) {
			Progression.grantSkillPoints(p, spent);
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.speed_refund", spent));
		}

		if (owns(p, "force_speed")) {
			// A bought node becomes a granted one; SkillService.grant never refunds, which is why the refund came first.
			Skills.grant(p, "force_speed");
			setStep(p, ChapterFourRules.STEP_POWER);
		} else if (!grantPower(p, "force_speed")) {
			return;
		}

		Skills.grant(p, "speed_wayfarer");
		ChapterFourService.mentorLine(p, "tessaly_teaches");
		MissionService.completeDriven(p, ChapterFourRules.TESSALYS_ROAD);
	}

	// ---------------------------------------------------------------- hooks

	/** A waystone was cleansed: The Greenwatch counts two, Both Doors (greenwatch) one. */
	static void onWaystoneCleansed(ServerPlayer p, BlockPos pos, boolean heartstone) {
		String active = MissionService.activeId(p);

		if (heartstone || active == null) {
			return;
		}

		boolean greenwatch = ChapterFourRules.GREENWATCH.equals(active);
		boolean door = ChapterFourRules.BOTH_DOORS.equals(active) && "greenwatch".equals(bothDoorsChoice(Story.get(p)));

		if (!greenwatch && !door) {
			return;
		}

		MissionService.markSurveyed(p, pos);
		int target = greenwatch ? 2 : 1;
		int progress = MissionService.drivenProgress(p) + 1;

		if (progress >= target) {
			MissionService.completeDriven(p, active);
		} else {
			MissionService.drive(p, active, null, progress, target);
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.c4.waystones", progress, target));
		}
	}

	/**
	 * The player entered a site piece: a greater tomb's crypt gives the relic for The Proving or
	 * Both Doors (proving), and a lost one again once a day while the mission waits for it.
	 */
	static void onSiteEntered(ServerPlayer p, SitePiece piece) {
		String active = MissionService.activeId(p);

		if (active == null || piece.kind() != SiteKind.GREATER_TOMB || !piece.template().getPath().contains("crypt")) {
			return;
		}

		boolean proving = ChapterFourRules.PROVING.equals(active)
				|| (ChapterFourRules.BOTH_DOORS.equals(active) && "proving".equals(bothDoorsChoice(Story.get(p))));

		if (!proving || carriesRelic(p)) {
			return;
		}

		long day = ChapterFourService.day(p);
		StoryData data = Story.get(p);

		if (MissionService.drivenProgress(p) > 0 && !ChapterFourRules.reissueDue(data, "relic", day)) {
			return;
		}

		data.counters.put(ChapterFourRules.REISSUE_PREFIX + "relic", (int) day);
		Story.commit(p, data);
		ChapterFourRepairs.dropAtFeet(p, new ItemStack(VendorItems.TOMB_RELIC));
		MissionService.driveReturn(p, active);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.relic"));
	}

	static boolean carriesRelic(ServerPlayer p) {
		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (stack.is(VendorItems.TOMB_RELIC)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * {@code c4:turn_in}: the relic handed to the host completes The Proving or Both Doors
	 * (proving).
	 */
	static void turnIn(ServerPlayer p) {
		String active = MissionService.activeId(p);
		boolean proving = ChapterFourRules.PROVING.equals(active)
				|| (ChapterFourRules.BOTH_DOORS.equals(active) && "proving".equals(bothDoorsChoice(Story.get(p))));

		if (!proving || !carriesRelic(p)) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.c4.no_relic"));
			return;
		}

		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (stack.is(VendorItems.TOMB_RELIC)) {
				stack.shrink(1);
				break;
			}
		}

		p.getInventory().setChanged();
		MissionService.completeDriven(p, active);
	}

	/** A power was used: Barrier raised during the Heartstone (8.4). */
	static void onAbilityUsed(ServerPlayer p, String abilityId) {
		Trial trial = TRIALS.get(p.getUUID());

		if (trial == null || trial.kind != Kind.HEARTSTONE || !"force_barrier".equals(abilityId)) {
			return;
		}

		ChapterFourBalance b = ChapterFourService.balance();
		long elapsed = StoryService.now(p) - trial.start;

		if (ChapterFourRules.barrierCounts(elapsed, b)) {
			trial.raisedSinceLastWave = true;

			if (ChapterFourRules.heartstoneDone(elapsed, trial.granted, true, b)) {
				finishTrial(p, trial);
			}
		}
	}

	/** A power landed: a Choke on Zarvos's echo breaks it and ends the Sepulchre (8.4). */
	static void onAbilityHit(ServerPlayer p, String abilityId, @Nullable Entity target) {
		Trial trial = TRIALS.get(p.getUUID());

		if (trial == null || trial.kind != Kind.SEPULCHRE || !trial.gripDone || target == null || !"force_choke".equals(abilityId)
				|| !target.getUUID().equals(trial.echo)) {
			return;
		}

		HubNpcs.breakEcho(target);
		finishTrial(p, trial);
	}

	// ---------------------------------------------------------------- the trials (every tick)

	static void tick(ServerPlayer p) {
		Trial trial = TRIALS.get(p.getUUID());

		if (trial == null) {
			return;
		}

		ChapterFourBalance b = ChapterFourService.balance();
		double leave = b.trialRing * LEAVE_FACTOR;

		String mission = trial.kind == Kind.HEARTSTONE ? ChapterFourRules.HEARTSTONE : ChapterFourRules.SEPULCHRE;

		if (!p.level().dimension().equals(trial.dimension) || p.position().distanceToSqr(Vec3.atCenterOf(trial.site)) > leave * leave
				|| !mission.equals(MissionService.activeId(p))) {
			endTrial(p, trial, true);
			return;
		}

		if (trial.kind == Kind.HEARTSTONE) {
			tickHeartstone(p, trial, b);
		} else {
			tickSepulchre(p, trial, b);
		}
	}

	/** The cleansing: three waves, Barrier granted at 45 s, and the end after 90 s once Barrier was raised since the last wave. */
	private static void tickHeartstone(ServerPlayer p, Trial trial, ChapterFourBalance b) {
		long elapsed = StoryService.now(p) - trial.start;
		int[] waves = b.heartstoneWaves;

		while (trial.waves < waves.length && elapsed >= waves[trial.waves]) {
			if (!ChapterFourService.peaceful(p)) {
				spawnHeartstoneWave(p, trial, b);
			}

			BlockPos stone = Heartstones.stone(p.level(), trial.site);

			if (stone != null) {
				Heartstones.wave(p.level(), stone, trial.waves + 1);
			}

			trial.waves++;
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.c4.trial.heartstone.wave", trial.waves, waves.length));
		}

		if (elapsed >= b.heartstoneGrantTicks && !trial.granted) {
			trial.granted = grantPower(p, "force_barrier");

			if (trial.granted) {
				Journal.unlockCodex(p, JournalEntries.CODEX_FORCE_BARRIER);
				StoryService.say(p, Component.translatable("npc.oldrepublic.ansa.name"), "npc.oldrepublic.ansa.c4.heartstone_teaches");
			}
		}

		if (trial.granted && !trial.raisedSinceLastWave && elapsed >= b.heartstoneGrantTicks) {
			hint(p, Component.translatable("message.oldrepublic.story.c4.raise_barrier", slotKey(p, "force_barrier")));
		}

		if (ChapterFourRules.heartstoneDone(elapsed, trial.granted, trial.raisedSinceLastWave, b)) {
			BlockPos stone = Heartstones.stone(p.level(), trial.site);

			if (stone != null) {
				Heartstones.complete(p, stone);
			}

			finishTrial(p, trial);
		}
	}

	/** The key that casts {@code ability} from its slot, for the hint (falls back to the first slot's key). */
	static Component slotKey(ServerPlayer p, String ability) {
		SkillData skills = Skills.data(p);
		int slot = skills == null ? 0 : Math.max(0, skills.slotOf(ability));
		return Component.keybind(slot == 0 ? "key.oldrepublic.use_force_power" : "key.oldrepublic.force_slot_" + (slot + 1));
	}

	private static void spawnHeartstoneWave(ServerPlayer p, Trial trial, ChapterFourBalance b) {
		ServerLevel level = p.level();

		for (int i = 0; i < b.heartstoneHollowhorns; i++) {
			spawnBeast(level, p, trial, HOLLOWHORN, ringSpot(level, trial.site, 9, i + trial.waves * 3));
		}

		for (int i = 0; i < b.heartstoneSentinels; i++) {
			spawnSentinel(level, p, trial, SentinelVariant.WATCHER, ringSpot(level, trial.site, 11, i * 5 + trial.waves));
		}
	}

	/** The Sepulchre: three rooms of sentinels and ash hounds, then Zarvos's echo, its grip, Choke granted, and the echo waits for a Choke. */
	private static void tickSepulchre(ServerPlayer p, Trial trial, ChapterFourBalance b) {
		ServerLevel level = p.level();

		if (trial.room < ROOMS) {
			if (!trial.roomSpawned) {
				trial.roomSpawned = true;

				if (!ChapterFourService.peaceful(p)) {
					spawnSentinel(level, p, trial, SentinelVariant.TOMB, ringSpot(level, trial.site, 7, trial.room * 2));
					spawnBeast(level, p, trial, ASH_HOUND, ringSpot(level, trial.site, 8, trial.room * 2 + 1));
					spawnBeast(level, p, trial, ASH_HOUND, ringSpot(level, trial.site, 8, trial.room * 2 + 4));
				}

				p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.c4.trial.sepulchre.room", trial.room + 1, ROOMS));
			} else if (aliveSpawns(level, trial) == 0) {
				trial.room++;
				trial.roomSpawned = false;
			}

			return;
		}

		Entity echo = trial.echo == null ? null : level.getEntity(trial.echo);

		if (echo == null) {
			echo = HubNpcs.spawnEcho(level, ringSpot(level, trial.site, 4, 0), p.getUUID());

			if (echo == null) {
				return;
			}

			echo.addTag(TRIAL_TAG);
			trial.echo = echo.getUUID();
			StoryService.say(p, Component.translatable("npc.oldrepublic.zarvos.name"), "npc.oldrepublic.zarvos.c4.rise");
		}

		long now = StoryService.now(p);

		if (trial.gripAt < 0) {
			if (p.distanceToSqr(echo) <= ECHO_REACH * ECHO_REACH) {
				trial.gripAt = now;
				Holds.hold(p, b.sepulchreGripTicks, "zarvos_grip", p.position().add(0, 1.0, 0), true);
				StoryService.say(p, Component.translatable("npc.oldrepublic.zarvos.name"), "npc.oldrepublic.zarvos.c4.take_it");
			}

			return;
		}

		if (!trial.gripDone && ChapterFourRules.gripEnds(now, trial.gripAt, Holds.isHeld(p), b)) {
			if (Holds.isHeld(p)) {
				Holds.release(p, "release");
			}

			if (grantPower(p, "force_choke")) {
				trial.gripDone = true;
				Journal.unlockCodex(p, JournalEntries.CODEX_FORCE_CHOKE);
				StoryService.say(p, Component.translatable("npc.oldrepublic.zarvos.name"), "npc.oldrepublic.zarvos.c4.then_take_it");
			}
		}

		if (trial.gripDone) {
			hint(p, Component.translatable("message.oldrepublic.story.c4.choke_echo", slotKey(p, "force_choke")));
		}
	}

	private static int aliveSpawns(ServerLevel level, Trial trial) {
		int alive = 0;

		for (UUID id : trial.spawned) {
			Entity entity = level.getEntity(id);

			if (entity != null && entity.isAlive()) {
				alive++;
			}
		}

		return alive;
	}

	/** A trial's beast (W6's hollowhorn or ash hound), owned and corrupted; skipped when the type is missing. */
	private static void spawnBeast(ServerLevel level, ServerPlayer p, Trial trial, Identifier type, BlockPos at) {
		Entity beast = BuiltInRegistries.ENTITY_TYPE.getOptional(type).<Entity>map(t -> t.create(level, EntitySpawnReason.EVENT)).orElse(null);

		if (beast == null) {
			return;
		}

		beast.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
		beast.addTag(TRIAL_TAG);
		beast.addTag(CORRUPTED_TAG);
		StoryTags.setOwner(beast, p.getUUID());

		if (beast instanceof Mob mob) {
			mob.setTarget(p);
		}

		if (level.addFreshEntity(beast)) {
			trial.spawned.add(beast.getUUID());
		}
	}

	private static void spawnSentinel(ServerLevel level, ServerPlayer p, Trial trial, SentinelVariant variant, BlockPos at) {
		LivingEntity sentinel = Sentinels.spawn(level, at, variant, true, p.getUUID());
		sentinel.addTag(TRIAL_TAG);
		trial.spawned.add(sentinel.getUUID());
		Sentinels.wakeNear(level, at, 2, p);
	}

	/** A spot on the ground on a ring around {@code centre}. */
	private static BlockPos ringSpot(ServerLevel level, BlockPos centre, int radius, int index) {
		double angle = index * (Math.PI * 2 / 7);
		int x = centre.getX() + (int) Math.round(Math.cos(angle) * radius);
		int z = centre.getZ() + (int) Math.round(Math.sin(angle) * radius);
		int y = level.hasChunkAt(x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : centre.getY();
		return new BlockPos(x, Math.min(y, centre.getY() + 6), z);
	}

	/** The trial is won: its spawns go and the mission completes. */
	private static void finishTrial(ServerPlayer p, Trial trial) {
		endTrial(p, trial, false);
		MissionService.completeDriven(p, trial.kind == Kind.HEARTSTONE ? ChapterFourRules.HEARTSTONE : ChapterFourRules.SEPULCHRE);
	}

	/** Ends a trial and removes its spawns; {@code abandoned} says so to the player. */
	private static void endTrial(ServerPlayer p, Trial trial, boolean abandoned) {
		TRIALS.remove(p.getUUID());
		ServerLevel level = p.level().getServer().getLevel(trial.dimension);

		if (level != null) {
			for (UUID id : trial.spawned) {
				Entity entity = level.getEntity(id);

				if (entity != null) {
					entity.discard();
				}
			}

			Entity echo = trial.echo == null ? null : level.getEntity(trial.echo);

			if (echo != null && abandoned) {
				echo.discard();
			}
		}

		if (Holds.isHeld(p) && trial.gripAt >= 0 && !trial.gripDone) {
			Holds.release(p, "reset");
		}

		if (abandoned) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.c4.trial.left"));
		}
	}

	// ---------------------------------------------------------------- expeditions and crafting

	/**
	 * An expedition was finished: 2,000 XP and 1 SP once per expedition, its entry, the second
	 * crystal (the Hollow lets the player choose blue or green; the rift's is red), and the arc's
	 * expedition step (a skipped one counts as done now).
	 */
	static void onExpeditionDone(ServerPlayer p, PlanetSites.Site site) {
		String name = site.name().toLowerCase(java.util.Locale.ROOT);

		if (site != PlanetSites.Site.SINGING_HOLLOW && site != PlanetSites.Site.DUSKGLASS_RIFT
				|| !ChapterFourService.once(p, ChapterFourRules.EXPEDITION_PREFIX + name)) {
			return;
		}

		ChapterFourBalance b = ChapterFourService.balance();
		ChapterFourService.xp(p, b.expeditionXp);
		Progression.grantSkillPoints(p, b.expeditionSp);
		String entry = ChapterFourRules.expeditionEntry(name);

		if (entry != null) {
			Journal.unlockEntry(p, entry);
		}

		if (site == PlanetSites.Site.DUSKGLASS_RIFT) {
			giveCrystal(p, BladeColor.RED);
		} else {
			StoryData data = Story.get(p);
			data.flags.add(ChapterFourRules.CRYSTAL_PENDING_PREFIX + name);
			Story.commit(p, data);
			com.oldrepublic.dialogue.Dialogues.open(p, OldRepublicMod.id("c4_crystal"), null);
		}

		StoryData data = Story.get(p);
		data.chapter4 = data.chapter4.withArc(data.chapter4.arc().withExpedition(ChapterFourRules.EXPEDITION_DONE));
		Story.commit(p, data);
		checkArcDone(p);
	}

	/** {@code c4:crystal <blue|green>}: the Hollow's crystal answers in the chosen hue (once). */
	static void chooseCrystal(ServerPlayer p, String hue) {
		String flag = ChapterFourRules.CRYSTAL_PENDING_PREFIX + "singing_hollow";
		StoryData data = Story.get(p);
		BladeColor color = "green".equals(hue.trim()) ? BladeColor.GREEN : "blue".equals(hue.trim()) ? BladeColor.BLUE : null;

		if (color == null || !data.flags.remove(flag)) {
			return;
		}

		Story.commit(p, data);
		giveCrystal(p, color);
	}

	private static void giveCrystal(ServerPlayer p, BladeColor color) {
		Rewards.give(p, SaberAssembly.crystalStack(new CrystalAttunement(p.getUUID(), p.getPlainTextName(), color.rgb())));
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.crystal"));
	}

	/** A craft: the first saberstaff is entry {@code two_blades}; a Masterwork shows its banner. */
	static void onCrafted(ServerPlayer p, Identifier schematic, int quality, boolean first) {
		String path = schematic.getPath();

		if (path.contains("saberstaff") || path.contains("lightsaber_staff")) {
			Journal.unlockEntry(p, JournalEntries.TWO_BLADES);
		}

		if (quality == Quality.MASTERWORK.ordinal()) {
			ChapterFourService.banner(p, QuestEffects.LESSON_MASTERWORK, Story.get(p).storyPath().ordinal());
		}
	}

	// ---------------------------------------------------------------- lifecycle

	static void forget(ServerPlayer p) {
		Trial trial = TRIALS.get(p.getUUID());

		if (trial != null) {
			endTrial(p, trial, false);
		}

		LAST_HINT.remove(p.getUUID());
	}

	static void clear() {
		TRIALS.clear();
		LAST_HINT.clear();
	}

	/** A trial spawn loaded from disk after a restart: no one tracks it. */
	static boolean isLeftover(Entity entity) {
		return entity.entityTags().contains(TRIAL_TAG);
	}

	/** Whether a trial is running for the player (tests). */
	static boolean inTrial(ServerPlayer p) {
		return TRIALS.containsKey(p.getUUID());
	}

	private static double sq(double v) {
		return v * v;
	}

	private WorldArcs() {
	}
}
