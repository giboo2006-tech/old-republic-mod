package com.oldrepublic.quest.story;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Pair;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.FactionM5Balance;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.mission.MissionData;
import com.oldrepublic.mission.MissionDefinition;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.mission.PlanetContracts;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.duel.SparResult;
import com.oldrepublic.saber.duel.SparRules;
import com.oldrepublic.space.SpaceMissions;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SpawnerGroups;
import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;

/**
 * Saltgate and the three ways to the factor (spec MILESTONE_5.md 6.3, 7): the first arrival (both
 * factions met, Clan Hask's first standing from Kaast's fate), the kajidic's two jobs and Vessba's
 * audience, the clan's matriarch hunt and duel of honour, the Force's way through the counting-house
 * door, Kaast's helmet, the clan hired for the blockade, and the pay of these story missions.
 *
 * <p>The three ways progress in parallel; the first one finished moves the chapter to THE_FACTOR and
 * the others stay open as optional faction work, each paid once ({@link ChapterFiveRules} flags).
 * The missions are story missions driven by this class: it sets their sites, spawns their people
 * and fights ({@link StoryNpcs}) and completes them.
 */
final class SaltgateWays {
	/** The debtor's variant ({@code hub_host}, 6.3). */
	static final String DEBTOR = "debtor";
	/** {@code c5.debt.<force|paid|ran>}: the debtor's three outcomes. */
	static final String DEBT_FORCE = ChapterFiveRules.DEBT_PREFIX + "force";
	static final String DEBT_PAID = ChapterFiveRules.DEBT_PREFIX + "paid";
	static final String DEBT_RAN = ChapterFiveRules.DEBT_PREFIX + "ran";
	/** The dunes, where the matriarch hunts (3.2). */
	private static final ResourceKey<Biome> DUNES = ResourceKey.create(Registries.BIOME, OldRepublicMod.id("ghessar_dunes"));
	/** How far from the chosen point the hunt looks for open dunes (blocks), and its steps. */
	private static final int DUNES_SEARCH = 96;
	private static final int DUNES_STEP = 16;
	/** The hunting ground's footprint for far placement (docs/MILESTONE_6.md 3.2): the square loaded before it counts as placed. */
	private static final int MATRIARCH_FOOTPRINT = 16;
	/** Set once the hunt's ground is settled (far placement placed or failed, or an old save's ground); until then guidance points at the far target. */
	static final String MATRIARCH_GROUND = "c5.matriarch_ground";
	/** The kajidic's patrol fighters in the quiet cargo run (W2's entity id, 7). */
	private static final Identifier HASK_FIGHTER = OldRepublicMod.id("hask_fighter");
	private static final String KAAST_HELMET = "kaast_helmet";
	/** W6's powers and their dialogue hooks (6.11). */
	private static final String KNOWS = "ability:knows";
	private static final String MIND_TRICK = "force_mind_trick";
	private static final String FEAR = "force_fear";
	private static final String MIND_TRICK_USE = "mind_trick:use";
	private static final String FEAR_USE = "fear:use";
	private static final String NOT_NOW = "message.oldrepublic.story.blocked.not_now";

	// ---------------------------------------------------------------- arriving (6.3, 7)

	/** The player touched down on Ghessar (W1's hook): the first arrival's standing, and CALLED moves on. */
	static void onArrived(ServerPlayer p) {
		arrive(p);
	}

	/** CALLED's slow check: a player already on Ghessar (an old save, a command, a missed hook) has arrived. */
	static void checkArrival(ServerPlayer p) {
		if (ChapterFiveService.planet(p) == Planets.Planet.GHESSAR) {
			arrive(p);
		}
	}

	/**
	 * The first arrival meets both factions (Varra is in the cantina too, 7.3) and sets Clan Hask's
	 * first standing from Kaast's fate, once; at CALLED the chapter moves to SALTGATE.
	 */
	private static void arrive(ServerPlayer p) {
		if (ChapterFiveService.once(p, ChapterFiveRules.ARRIVED)) {
			Identity.meetFaction(p, Faction.VESSBA_KAJIDIC);
			Identity.meetFaction(p, Faction.CLAN_HASK);
			int start = ChapterFiveRules.clanStart(ChapterFiveRules.kaast(Story.get(p)), FactionM5Balance.get());

			if (start != 0) {
				Identity.changeReputation(p, Faction.CLAN_HASK, start, "c5.clan_start", true);
			}
		}

		if (Story.get(p).chapter5.stage() == ChapterFiveStage.CALLED) {
			ChapterFiveService.enterStage(p, ChapterFiveStage.SALTGATE);
		}
	}

	/** SALTGATE begins: the three ways. */
	static void onSaltgate(ServerPlayer p) {
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c5.objective.saltgate"));
	}

	// ---------------------------------------------------------------- the slow tick

	/** The active Chapter Five mission's places, people and fights at Saltgate and around it. */
	static void tick(ServerPlayer p) {
		String active = MissionService.activeId(p);
		String fight = StoryNpcs.fightId(p);

		if (fight != null && !fight.equals(active)) {
			// No Chapter Five fight outlives its mission (an abandoned hunt, a debt dropped mid-fight).
			StoryNpcs.endFight(p);
		}

		if (ChapterFiveRules.DEBT.equals(active)) {
			tickDebt(p);
		} else if (ChapterFiveRules.MATRIARCH_HUNT.equals(active)) {
			tickMatriarch(p);
		} else if (ChapterFiveRules.HONOUR_DUEL.equals(active)) {
			tickHonour(p);
		}
	}

	// ---------------------------------------------------------------- the debt (6.3)

	/** The debt in the canyons: the camp's site, the debtor waiting at {@code NPC_DEBTOR}, and the salvagers' fight after force. */
	private static void tickDebt(ServerPlayer p) {
		MissionData.Active active = MissionService.get(p).active;

		if (active == null || active.stage == MissionData.Stage.RETURN) {
			return;
		}

		GlobalPos camp = camp(p);

		if (camp == null) {
			return;
		}

		if (!camp.equals(MissionService.drivenSite(p))) {
			MissionService.drive(p, ChapterFiveRules.DEBT, camp, 0, 1);
		}

		ServerLevel level = p.level();

		if (!level.dimension().equals(camp.dimension())) {
			return;
		}

		if (StoryNpcs.fighting(p, ChapterFiveRules.DEBT)) {
			if (StoryNpcs.leftFight(p)) {
				// Left mid-fight: the salvagers regroup and wait for the player's return.
				StoryNpcs.endFight(p);
				return;
			}

			int left = StoryNpcs.foesLeft(p, ChapterFiveRules.DEBT);
			int total = StoryNpcs.foesAtStart(p, ChapterFiveRules.DEBT);
			MissionService.drive(p, ChapterFiveRules.DEBT, camp, total - left, Math.max(1, total));

			if (left == 0) {
				StoryNpcs.endFight(p);
				boxTaken(p);
			}

			return;
		}

		SitePiece piece = StoryNpcs.piece(level, camp.pos(), SiteKind.DEBTOR_CAMP);

		if (Story.get(p).hasFlag(DEBT_FORCE)) {
			if (StoryNpcs.near(p, camp.dimension(), camp.pos(), ChapterFiveService.balance().arriveRange)) {
				fightSalvagers(p, piece, camp.pos());
			}

			return;
		}

		// At his anchor once the camp stands; before that (or without the anchor) at the camp's middle.
		BlockPos debtor = piece == null ? null : SiteAnchors.find(piece, SiteAnchors.Anchor.NPC_DEBTOR);
		StoryNpcs.ensure(p, debtor != null ? debtor : StoryNpcs.ground(level, camp.pos()), DEBTOR);
	}

	/** The owner's debtor camp: placed when they come near (3.2, W3's site), its ring point before; null while Saltgate is not placed. */
	private static @Nullable GlobalPos camp(ServerPlayer p) {
		GlobalPos placed = ChapterFiveService.planet(p) == Planets.Planet.GHESSAR
				? PlanetSites.ensure(p, PlanetSites.Site.DEBTOR_CAMP, Planets.Planet.GHESSAR)
				: PlanetSites.find(p, PlanetSites.Site.DEBTOR_CAMP);
		return placed != null ? placed : PlanetSites.ringPoint(p.level().getServer(), p.getUUID(), PlanetSites.Site.DEBTOR_CAMP, Planets.Planet.GHESSAR);
	}

	/** The salvagers stand and fight for the box (the {@code scavengers} group at the camp's {@code SPAWNER}); on Peaceful they scatter. */
	private static void fightSalvagers(ServerPlayer p, @Nullable SitePiece piece, BlockPos camp) {
		if (ChapterFiveService.peaceful(p)) {
			boxTaken(p);
			return;
		}

		ServerLevel level = p.level();
		BlockPos spawner = piece == null ? null : SiteAnchors.find(piece, SiteAnchors.Anchor.SPAWNER);
		BlockPos at = spawner != null ? spawner : StoryNpcs.ground(level, camp);
		List<LivingEntity> crew = SpawnerGroups.spawn(level, at, SpawnerGroups.SCAVENGERS, ChapterFiveService.balance().salvagers, false, p.getUUID());

		if (crew.isEmpty()) {
			boxTaken(p);
			return;
		}

		for (LivingEntity member : crew) {
			if (member instanceof Mob mob) {
				mob.setTarget(p);
			}
		}

		StoryNpcs.startFight(p, ChapterFiveRules.DEBT, at, crew, List.of());
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.debt.fight"));
	}

	/** The strongbox is the player's: back to Teel. */
	private static void boxTaken(ServerPlayer p) {
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.debt.box"));
		MissionService.driveReturn(p, ChapterFiveRules.DEBT);
	}

	/**
	 * {@code c5:debt <force|pay|run>}, the debtor's choice (6.3): take the box by force (his salvagers
	 * fight), pay his debt from the player's own credits (200), or let him run and claim the box was
	 * empty (Teel believes it only with a Mind Trick or a bribe). The debtor leaves either way.
	 */
	static void debtChoice(ServerPlayer p, @Nullable Entity speaker, String how) {
		MissionData.Active active = MissionService.get(p).active;

		if (active == null || !ChapterFiveRules.DEBT.equals(active.id) || active.stage == MissionData.Stage.RETURN) {
			return;
		}

		StoryData data = Story.get(p);

		if (data.hasFlag(DEBT_FORCE) || data.hasFlag(DEBT_PAID) || data.hasFlag(DEBT_RAN)) {
			return;
		}

		ChapterFiveBalance b = ChapterFiveService.balance();
		String flag;

		switch (how) {
			case "force" -> flag = DEBT_FORCE;
			case "pay" -> {
				if (!Credits.take(p, b.debtCredits, true)) {
					p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.blocked.credits", b.debtCredits));
					return;
				}

				flag = DEBT_PAID;
			}
			case "run" -> flag = DEBT_RAN;
			default -> {
				return;
			}
		}

		data.flags.add(flag);
		Story.commit(p, data);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.debt." + how));

		if (speaker instanceof HubNpc debtor && p.getUUID().equals(debtor.storyOwner())) {
			debtor.discard();
		}

		if (flag.equals(DEBT_FORCE)) {
			GlobalPos camp = camp(p);
			ServerLevel level = p.level();

			if (camp != null && level.dimension().equals(camp.dimension())) {
				fightSalvagers(p, StoryNpcs.piece(level, camp.pos(), SiteKind.DEBTOR_CAMP), camp.pos());
			}
		} else {
			boxTaken(p);
		}
	}

	/**
	 * {@code c5:debt_claim <box|trick|bribe|back>}, at Teel (6.3): the strongbox handed over (paid or
	 * taken), or the empty-box story told with a Mind Trick (W6's {@code mind_trick:use}) or 150
	 * credits; {@code back} drops the job, to be taken again (the debtor is at his camp again).
	 */
	static void debtClaim(ServerPlayer p, @Nullable Entity speaker, String how) {
		MissionData.Active active = MissionService.get(p).active;

		if (active == null || !ChapterFiveRules.DEBT.equals(active.id) || active.stage != MissionData.Stage.RETURN) {
			return;
		}

		StoryData data = Story.get(p);
		boolean ran = data.hasFlag(DEBT_RAN);

		switch (how) {
			case "box" -> {
				if (ran) {
					return;
				}
			}
			case "trick" -> {
				if (!ran || !usePower(p, speaker, MIND_TRICK, MIND_TRICK_USE)) {
					return;
				}
			}
			case "bribe" -> {
				int bribe = ChapterFiveService.balance().teelBribe;

				if (!ran) {
					return;
				}

				if (!Credits.take(p, bribe, true)) {
					p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.blocked.credits", bribe));
					return;
				}
			}
			case "back" -> {
				if (!ran) {
					return;
				}

				data.flags.remove(DEBT_RAN);
				Story.commit(p, data);
				MissionService.abandon(p);
				return;
			}
			default -> {
				return;
			}
		}

		MissionService.completeDriven(p, ChapterFiveRules.DEBT);
	}

	// ---------------------------------------------------------------- the quiet cargo (6.3)

	/** W2 ended the cargo run: a clean run completes the job; a scan or a lost crate drops it, to be taken again from Teel. */
	static void onCargoDone(ServerPlayer p, boolean success) {
		if (!ChapterFiveRules.CARGO.equals(MissionService.activeId(p))) {
			return;
		}

		if (success) {
			MissionService.completeDriven(p, ChapterFiveRules.CARGO);
		} else {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.cargo_failed"));
			MissionService.abandon(p);
		}
	}

	/**
	 * A fighter of the quiet cargo's patrol shot down (W2's {@link SpaceMissions#missionOf}): the patrol is
	 * Clan Hask's, so each costs clan standing, counted as a hostile kill inside the daily cap (5.9, 7).
	 * A Clan Hask fighter is checked by its type too, so a pirate in the same orbit costs nothing.
	 */
	static void onShipKill(ServerPlayer shooter, Entity fighter) {
		if (ChapterFiveRules.CARGO.equals(SpaceMissions.missionOf(fighter)) && HASK_FIGHTER.equals(BuiltInRegistries.ENTITY_TYPE.getKey(fighter.getType()))) {
			FactionStanding.onKill(shooter, Faction.CLAN_HASK, true);
		}
	}

	// ---------------------------------------------------------------- the matriarch (6.3)

	/** A hunting ground, and whether it is settled (else the far target guidance points at while it loads). */
	private record Ground(GlobalPos pos, boolean settled) {
	}

	/**
	 * The matriarch hunt: a ground in the dunes {@code matriarchRange} (300 to 500) blocks from
	 * Saltgate, placed far away through {@link FarSites} (docs/MILESTONE_6.md 3.2); her brood on
	 * arrival, done when it falls. While the far request loads, the mission points at its target.
	 */
	private static void tickMatriarch(ServerPlayer p) {
		GlobalPos ground = MissionService.drivenSite(p);

		if (ground == null || !Story.get(p).hasFlag(MATRIARCH_GROUND)) {
			Ground next = matriarchGround(p, ground);

			if (next == null) {
				return;
			}

			if (!next.pos().equals(ground)) {
				MissionService.drive(p, ChapterFiveRules.MATRIARCH_HUNT, next.pos(), 0, 1);
			}

			if (!next.settled()) {
				return;
			}

			StoryData data = Story.get(p);
			boolean settledNow = data.flags.add(MATRIARCH_GROUND);

			if (settledNow) {
				Story.commit(p, data);
			}

			if (settledNow || ground == null) {
				// The way to the ground, once it is settled (and again when the hunt is taken anew).
				p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.hunting_ground", FarSites.describe(p.blockPosition(), next.pos().pos())));
			}

			ground = next.pos();
		}

		if (StoryNpcs.fighting(p, ChapterFiveRules.MATRIARCH_HUNT)) {
			if (StoryNpcs.leftFight(p)) {
				StoryNpcs.endFight(p);
				return;
			}

			int left = StoryNpcs.foesLeft(p, ChapterFiveRules.MATRIARCH_HUNT);
			int total = StoryNpcs.foesAtStart(p, ChapterFiveRules.MATRIARCH_HUNT);
			MissionService.drive(p, ChapterFiveRules.MATRIARCH_HUNT, ground, total - left, Math.max(1, total));

			if (left == 0) {
				StoryNpcs.endFight(p);
				MissionService.completeDriven(p, ChapterFiveRules.MATRIARCH_HUNT);
			}

			return;
		}

		ServerLevel level = p.level();

		if (!StoryNpcs.near(p, ground.dimension(), ground.pos(), ChapterFiveService.balance().arriveRange) || !level.hasChunkAt(ground.pos())) {
			return;
		}

		if (ChapterFiveRules.clearsOnArrival(ChapterFiveService.balance().matriarchBrood, ChapterFiveService.peaceful(p))) {
			// Peaceful: a clear mission completes on arrival (6.9, the M3 rule).
			MissionService.completeDriven(p, ChapterFiveRules.MATRIARCH_HUNT);
			return;
		}

		BlockPos at = StoryNpcs.ground(level, ground.pos());
		List<LivingEntity> brood = SpawnerGroups.spawn(level, at, "scour_brood", ChapterFiveService.balance().matriarchBrood, true, p.getUUID());

		if (brood.isEmpty()) {
			OldRepublicMod.LOGGER.warn("The matriarch hunt of {} found no sand scours to spawn; it completes", p.getName().getString());
			MissionService.completeDriven(p, ChapterFiveRules.MATRIARCH_HUNT);
			return;
		}

		StoryNpcs.startFight(p, ChapterFiveRules.MATRIARCH_HUNT, at, brood, List.of());
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.matriarch"));
	}

	/**
	 * The hunt's ground: a ground point {@code chapter_five.matriarchRange} from Saltgate asked of
	 * {@link FarSites} (unsettled, at its target, while it loads; null while it is still choosing),
	 * then moved to the nearest dunes when the biome source has some near (no chunk is loaded to find
	 * them). When far placement fails, the old seeded spot in the same band. An old save's ground
	 * ({@code current}, never asked of far placement) stays where it is. Null while Saltgate is not
	 * placed.
	 */
	private static @Nullable Ground matriarchGround(ServerPlayer p, @Nullable GlobalPos current) {
		ServerLevel ghessar = Planets.level(p.level().getServer(), Planets.Planet.GHESSAR);
		BlockPos hub = ghessar == null ? null : PlanetHubs.hub(p.level().getServer(), Planets.Planet.GHESSAR);

		if (hub == null) {
			return null;
		}

		String key = StorySites.matriarchKey(p.getUUID());

		// API-CHECK: a key never requested polls as FAILED with detail "unknown" (the FarSites contract); an old save's ground is kept then.
		if (current != null && "unknown".equals(FarSites.poll(ghessar, key).detail())) {
			return new Ground(current, true);
		}

		int[] range = ChapterFiveService.balance().matriarchRange;
		FarSites.Status status = FarSites.request(ghessar, StorySites.point(key, p.getUUID(), hub, range, MATRIARCH_FOOTPRINT));

		if (StorySites.pending(status)) {
			BlockPos pointer = status.pointer();
			return pointer == null ? null : new Ground(GlobalPos.of(ghessar.dimension(), pointer), false);
		}

		BlockPos target = status.placed() ? status.target() : null;
		BlockPos point = target != null ? target : seededGround(p, hub, range);
		Pair<BlockPos, Holder<Biome>> dunes = ghessar.findClosestBiome3d(biome -> biome.is(DUNES), point, DUNES_SEARCH, DUNES_STEP, DUNES_STEP);
		BlockPos found = dunes != null ? dunes.getFirst() : point;
		return new Ground(GlobalPos.of(ghessar.dimension(), new BlockPos(found.getX(), point.getY(), found.getZ())), true);
	}

	/** The near fallback: a direction seeded per player, at a seeded distance in {@code range} from Saltgate's {@code hub}. */
	private static BlockPos seededGround(ServerPlayer p, BlockPos hub, int[] range) {
		int min = range.length > 0 ? range[0] : 300;
		int max = range.length > 1 ? Math.max(min, range[1]) : min;
		RandomSource random = RandomSource.create(Rewards.seed(p, "c5.matriarch", 0));
		double angle = random.nextDouble() * Math.PI * 2;
		int distance = min + random.nextInt(max - min + 1);
		return new BlockPos(hub.getX() + (int) Math.round(Math.cos(angle) * distance), hub.getY(), hub.getZ() + (int) Math.round(Math.sin(angle) * distance));
	}

	// ---------------------------------------------------------------- the duel of honour (6.3)

	/** The champion waits beside Varra in Saltgate's cantina while the duel is owed. */
	private static void tickHonour(ServerPlayer p) {
		ServerLevel level = p.level();

		if (ChapterFiveService.planet(p) != Planets.Planet.GHESSAR) {
			return;
		}

		BlockPos varra = StoryNpcs.hubAnchor(level, Planets.Planet.GHESSAR, SiteAnchors.Anchor.NPC_VARRA, 6);

		if (varra == null) {
			return;
		}

		// Beside Varra on the cantina floor (the column's top is the cantina roof since docs/MILESTONE_6.md 5.7).
		BlockPos spot = varra.offset(2, 0, 1);
		GlobalPos site = GlobalPos.of(level.dimension(), spot);

		if (!site.equals(MissionService.drivenSite(p))) {
			MissionService.drive(p, ChapterFiveRules.HONOUR_DUEL, site, 0, 1);
		}

		StoryNpcs.ensure(p, spot, HubNpcs.CHAMPION);
	}

	/**
	 * The player used Clan Hask's champion: while the duel of honour is theirs to fight, the bout
	 * begins (the M4 spar rules with a real blade, yield at 20%); while a Tyrvann spar contract waits
	 * for its bout, she is its partner (the hub spar's rules, 7.2); otherwise she only nods.
	 */
	static void championUsed(ServerPlayer p, HubNpc champion) {
		if (!ChapterFiveRules.HONOUR_DUEL.equals(MissionService.activeId(p))) {
			if (PlanetContracts.sparWaiting(p)) {
				if (HubNpcs.contractSpar(p, champion)) {
					p.sendSystemMessage(Component.translatable("message.oldrepublic.story.says", champion.getDisplayName(),
							Component.translatable("npc.oldrepublic.hask_champion.c5.contract")));
				}

				return;
			}

			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.says", champion.getDisplayName(),
					Component.translatable("npc.oldrepublic.hask_champion.c5.idle")));
			return;
		}

		ChapterFiveBalance b = ChapterFiveService.balance();
		UUID id = p.getUUID();
		SparRules rules = new SparRules(SparRules.Goal.YIELD, 0, b.honourYield, b.honourDamage);
		boolean started = Spar.start(p, champion, rules, result -> onHonourResult(p.level().getServer().getPlayerList().getPlayer(id), result));

		if (started) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.says", champion.getDisplayName(),
					Component.translatable("npc.oldrepublic.hask_champion.c5.begin")));
			champion.level().playSound(null, champion.getX(), champion.getY() + 1, champion.getZ(), ModSounds.HUB_APPRENTICE_SPAR, SoundSource.NEUTRAL, 0.8f, 0.9f);
		}
	}

	private static void onHonourResult(@Nullable ServerPlayer p, SparResult result) {
		if (p == null || !ChapterFiveRules.HONOUR_DUEL.equals(MissionService.activeId(p))) {
			return;
		}

		if (result == SparResult.WON) {
			MissionService.completeDriven(p, ChapterFiveRules.HONOUR_DUEL);
		} else {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.honour_again"));
		}
	}

	// ---------------------------------------------------------------- the ways

	/**
	 * A way is complete (6.3): its bit, its entry, and for the first one the chapter moves to
	 * THE_FACTOR (the counting-house mission starts there). Later ways are optional and change nothing else.
	 */
	static void completeWay(ServerPlayer p, ChapterFive.Way way) {
		StoryData data = Story.get(p);

		if (data.chapter5.hasWay(way)) {
			return;
		}

		boolean first = data.chapter5.chosenWay() == null;
		data.chapter5 = data.chapter5.withWay(way);
		Story.commit(p, data);
		Journal.unlockEntry(p, switch (way) {
			case KAJIDIC -> JournalEntries.THE_KAJIDIC_WAY;
			case CLAN -> JournalEntries.THE_CLANS_WAY;
			default -> JournalEntries.THE_FORCES_WAY;
		});
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.way." + way.id()));

		if (first && data.chapter5.stage() == ChapterFiveStage.SALTGATE) {
			ChapterFiveService.enterStage(p, ChapterFiveStage.THE_FACTOR);
		}
	}

	/**
	 * {@code c5:way <kajidic|clan|force>}: the kajidic's way is Vessba's audience, given once both jobs
	 * are done (the debt alone without free flight): kajidic +80 (6.3). The clan's way completes with
	 * the matriarch and the Force's at the door; asking for them here only completes a way already earned.
	 */
	static void wayAction(ServerPlayer p, String id) {
		ChapterFive.Way way = ChapterFive.Way.byId(id.trim());
		StoryData data = Story.get(p);

		if (way == ChapterFive.Way.KAJIDIC) {
			if (!ChapterFiveRules.audienceReady(data, ChapterFiveService.flight()) || !ChapterFiveService.once(p, ChapterFiveRules.AUDIENCE)) {
				return;
			}

			Identity.changeReputation(p, Faction.VESSBA_KAJIDIC, ChapterFiveService.balance().audienceRep, "c5.audience", false);
			completeWay(p, way);
		} else if (way == ChapterFive.Way.CLAN && data.hasFlag(ChapterFiveRules.MATRIARCH)) {
			completeWay(p, way);
		} else if (way == ChapterFive.Way.FORCE && data.flags.stream().anyMatch(f -> f.startsWith(ChapterFiveRules.FORCE_PREFIX))) {
			completeWay(p, way);
		}
	}

	/**
	 * {@code c5:doorman <trick|fear|push>}, the Force's way at the counting-house door (6.3): a Mind
	 * Trick on the doorman or Fear on the door guards (W6's actions spend the power as if cast), or for
	 * a player with neither power a Force Push through the door. 600 XP; kajidic -25 after a Mind
	 * Trick, -50 otherwise. Only while the door is the way in (SALTGATE, no way done yet).
	 */
	static void doorman(ServerPlayer p, @Nullable Entity speaker, String how) {
		if (!ChapterFiveRules.doorOpen(Story.get(p))) {
			p.sendOverlayMessage(Component.translatable(NOT_NOW));
			return;
		}

		ChapterFiveBalance b = ChapterFiveService.balance();
		int rep;

		switch (how) {
			case ChapterFiveRules.TRICK -> {
				if (!usePower(p, speaker, MIND_TRICK, MIND_TRICK_USE)) {
					return;
				}

				Journal.unlockCodex(p, JournalEntries.CODEX_CARTEL_ENFORCERS);
				rep = b.mindTrickWayRep;
			}
			case ChapterFiveRules.FEAR -> {
				if (!usePower(p, speaker, FEAR, FEAR_USE)) {
					return;
				}

				rep = b.forceWayRep;
			}
			case ChapterFiveRules.PUSH -> {
				if (knowsDoorPower(p, speaker)) {
					return;
				}

				Entity at = speaker != null ? speaker : p;
				p.level().playSound(null, at.getX(), at.getY() + 1, at.getZ(), ModSounds.FORCE_PUSH_SLAM, SoundSource.PLAYERS, 1f, 0.9f);
				rep = b.forceWayRep;
			}
			default -> {
				return;
			}
		}

		ChapterFiveService.once(p, ChapterFiveRules.FORCE_PREFIX + how);
		ChapterFiveService.xp(p, b.forceWayXp);
		Identity.changeReputation(p, Faction.VESSBA_KAJIDIC, rep, "c5.force_way", false);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.door." + how));
		completeWay(p, ChapterFive.Way.FORCE);
	}

	/** Whether the player can use Mind Trick or Fear at the door now ({@code c5:no_power} is its negation). */
	static boolean knowsDoorPower(ServerPlayer p, @Nullable Entity speaker) {
		return canUse(p, speaker, MIND_TRICK) || canUse(p, speaker, FEAR);
	}

	private static boolean canUse(ServerPlayer p, @Nullable Entity speaker, String ability) {
		DialogueConditions.Condition knows = DialogueConditions.get(KNOWS);
		return knows != null && knows.failReason(p, speaker, ability) == null;
	}

	/** Spends a power through W6's dialogue action when the player can use it now; false when they cannot. */
	private static boolean usePower(ServerPlayer p, @Nullable Entity speaker, String ability, String action) {
		if (!canUse(p, speaker, ability)) {
			return false;
		}

		DialogueActions.Action use = DialogueActions.get(action);

		if (use != null) {
			use.run(p, speaker);
		}

		return true;
	}

	// ---------------------------------------------------------------- Varra's asks (6.5, 7)

	/** {@code c5:helmet}: Kaast's helmet given back to Varra (she keeps it): clan +100, once (7). */
	static void returnHelmet(ServerPlayer p) {
		if (!ChapterFiveRules.helmetAsked(Story.get(p)) || !takeOne(p, OldRepublicMod.id(KAAST_HELMET))) {
			return;
		}

		ChapterFiveService.once(p, ChapterFiveRules.HELMET_RETURNED);
		Identity.changeReputation(p, Faction.CLAN_HASK, FactionM5Balance.get().helmetReturnRep, "c5.helmet", true);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.helmet"));
	}

	/** Whether the player carries Kaast's helmet (the {@code c5:helmet} condition). */
	static boolean carriesHelmet(ServerPlayer p) {
		Identifier id = OldRepublicMod.id(KAAST_HELMET);

		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
				return true;
			}
		}

		return false;
	}

	private static boolean takeOne(ServerPlayer p, Identifier id) {
		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
				stack.shrink(1);
				return true;
			}
		}

		return false;
	}

	/** {@code c5:hire_clan}: after failed blockades, Varra flies in with the clan for 1,000 credits of fuel and ammunition (6.5). */
	static void hireClan(ServerPlayer p) {
		StoryData data = Story.get(p);
		ChapterFiveBalance b = ChapterFiveService.balance();

		if (data.hasFlag(ChapterFive.CLAN_HIRED) || !ChapterFiveRules.clanHelpOffered(data, ChapterFive.clanStands(p), b)) {
			return;
		}

		if (!Credits.take(p, b.clanHelpCredits, true)) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.blocked.credits", b.clanHelpCredits));
			return;
		}

		ChapterFiveService.once(p, ChapterFive.CLAN_HIRED);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.clan_hired"));
	}

	// ---------------------------------------------------------------- starting and paying the missions

	/**
	 * {@code c5:mission <id>}: takes a Chapter Five story mission from its giver (the same checks as
	 * {@code mission:start}). A new debt forgets the last attempt's choice.
	 */
	static void startMission(ServerPlayer p, @Nullable Entity giver, String id) {
		if (ChapterFiveRules.DEBT.equals(id)) {
			StoryData data = Story.get(p);

			if (data.flags.removeIf(f -> f.startsWith(ChapterFiveRules.DEBT_PREFIX))) {
				Story.commit(p, data);
			}
		}

		MissionService.startStory(p, id, giver);
	}

	/** Pays a finished Saltgate mission (the jobs, the hunt and the duel), once each by its flag; then the way it belongs to moves. */
	static void pay(ServerPlayer p, MissionDefinition def) {
		ChapterFiveBalance b = ChapterFiveService.balance();

		switch (def.id()) {
			case ChapterFiveRules.DEBT -> payJob(p, ChapterFiveRules.JOB_DEBT, b);
			case ChapterFiveRules.CARGO -> payJob(p, ChapterFiveRules.JOB_CARGO, b);
			case ChapterFiveRules.MATRIARCH_HUNT -> {
				if (ChapterFiveService.once(p, ChapterFiveRules.MATRIARCH)) {
					ChapterFiveService.xp(p, b.matriarchXp);
					Credits.give(p, b.matriarchCredits);
					Identity.changeReputation(p, Faction.CLAN_HASK, b.matriarchRep, "c5.matriarch", false);
				}

				completeWay(p, ChapterFive.Way.CLAN);
			}
			case ChapterFiveRules.HONOUR_DUEL -> {
				if (ChapterFiveService.once(p, ChapterFiveRules.HONOUR)) {
					ChapterFiveService.xp(p, b.honourXp);
					Identity.changeReputation(p, Faction.CLAN_HASK, b.honourRep, "c5.honour", false);
					p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.honour"));
				}

				ServerLevel level = p.level();
				StoryNpcs.remove(level, p.getUUID(), HubNpcs.CHAMPION, p.blockPosition());
			}
			default -> {
			}
		}
	}

	/** One kajidic job: 1,200 XP, 150 credits, kajidic +60, once (6.3). */
	private static void payJob(ServerPlayer p, String flag, ChapterFiveBalance b) {
		if (!ChapterFiveService.once(p, flag)) {
			return;
		}

		ChapterFiveService.xp(p, b.jobXp);
		Credits.give(p, b.jobCredits);
		Identity.changeReputation(p, Faction.VESSBA_KAJIDIC, b.jobRep, flag, false);

		if (ChapterFiveRules.audienceReady(Story.get(p), ChapterFiveService.flight())) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.audience_ready"));
		}
	}

	private SaltgateWays() {
	}
}
