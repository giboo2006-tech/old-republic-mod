package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.ArenaLayout;
import com.oldrepublic.boss.ArenaSession;
import com.oldrepublic.boss.BossArenas;
import com.oldrepublic.boss.CollectorCombatGoal;
import com.oldrepublic.boss.OrrenVasch;
import com.oldrepublic.boss.PylonFocus;
import com.oldrepublic.boss.block.PylonCrystal;
import com.oldrepublic.boss.kaast.CampLayout;
import com.oldrepublic.boss.kaast.CampMine;
import com.oldrepublic.boss.kaast.KaastBalance;
import com.oldrepublic.boss.kaast.KaastFight;
import com.oldrepublic.boss.kaast.KaastSession;
import com.oldrepublic.boss.kaast.TorvinKaast;
import com.oldrepublic.boss.ulvaine.DarthUlvaine;
import com.oldrepublic.boss.ulvaine.HeartLayout;
import com.oldrepublic.boss.ulvaine.UlvaineBalance;
import com.oldrepublic.boss.ulvaine.UlvaineFight;
import com.oldrepublic.boss.ulvaine.UlvaineSession;
import com.oldrepublic.client.crafting.EngineeringBenchScreen;
import com.oldrepublic.client.dialogue.DialogueScreen;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.client.skill.ForceScreen;
import com.oldrepublic.client.travel.ShipClientState;
import com.oldrepublic.client.travel.ShipScreen;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingBlocks;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.bench.EngineeringBenchMenu;
import com.oldrepublic.dialogue.DialogueDefinition;
import com.oldrepublic.dialogue.DialogueLoader;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.dialogue.network.DialogueNodePayload;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.CrystalPoacher;
import com.oldrepublic.enemy.PoacherLoot;
import com.oldrepublic.enemy.construct.RuinSentinel;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.force.ForceBlockInteractions;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.PushShape;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.force.barrier.ForceBarrier;
import com.oldrepublic.gametest.m5.FlightOrbitTrip;
import com.oldrepublic.gametest.m5.TurningClientSteps;
import com.oldrepublic.holocron.HolocronBalance;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.FrontierSettler;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.identity.Yieldable;
import com.oldrepublic.identity.Yielding;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mission.MissionData;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.mission.Missions;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Expeditions;
import com.oldrepublic.planet.HymnalDoors;
import com.oldrepublic.planet.PlanetBalance;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.planet.Waystones;
import com.oldrepublic.planet.block.VerseStoneBlockEntity;
import com.oldrepublic.planet.block.WaystoneBlockEntity;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.Guidance;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.QuestItems;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.quest.story.CaptainFate;
import com.oldrepublic.quest.story.CaptivesFate;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveRules;
import com.oldrepublic.quest.story.ChapterFiveService;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.quest.story.ChapterFour;
import com.oldrepublic.quest.story.ChapterFourRepairs;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterFourService;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.ChapterThreeService;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.CrossingStage;
import com.oldrepublic.quest.story.EncounterState;
import com.oldrepublic.quest.story.KaastDuel;
import com.oldrepublic.quest.story.KaastOutcome;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.quest.story.PathStage;
import com.oldrepublic.quest.story.RankThree;
import com.oldrepublic.quest.story.SeekerScene;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryRules;
import com.oldrepublic.quest.story.StoryService;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.quest.story.TheHunt;
import com.oldrepublic.quest.story.WorldArcs;
import com.oldrepublic.quest.turning.RankFour;
import com.oldrepublic.quest.turning.RankFourChecklistPayload;
import com.oldrepublic.quest.turning.TurningRules;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberPassives;
import com.oldrepublic.saber.duel.DuelState;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.NpcGuard;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillRules;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.SkillTrees;
import com.oldrepublic.skill.Skills;
import com.oldrepublic.space.BlockadeGunship;
import com.oldrepublic.space.GunshipGenerator;
import com.oldrepublic.space.ShipWeapons;
import com.oldrepublic.space.SpaceData;
import com.oldrepublic.space.SpaceEncounters;
import com.oldrepublic.space.SpaceMissions;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.NavData;
import com.oldrepublic.travel.ShipActions;
import com.oldrepublic.travel.ShipBalance;
import com.oldrepublic.travel.ShipMenu;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.travel.network.ShipStatusPayload;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.vendor.hub.HubRole;
import com.oldrepublic.world.CaveLocator;
import com.oldrepublic.world.WorldBlocks;
import com.oldrepublic.world.block.ResonantSealBlock;
import com.oldrepublic.world.outpost.Outposts;
import com.oldrepublic.world.puzzle.PuzzleBlocks;
import com.oldrepublic.world.puzzle.PuzzleStates;
import com.oldrepublic.world.site.EchoCairnBlock;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;
import com.oldrepublic.world.site.WorldSites;
import com.oldrepublic.world.trial.CrystalGrant;
import com.oldrepublic.world.trial.ShadowEchoEntity;
import com.oldrepublic.world.trial.TrialManager;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Plays Milestone 1 the way a player would, in a survival world with normal terrain and a fixed
 * seed: sleep to trigger the Awakening, crouch with the journal to meditate, push hostile mobs,
 * follow the Calling to a generated crystal cave, fight the poacher camp, open the seal, win the
 * Trial of Reflection, loot the hidden chamber, craft the parts and the workbench, build the
 * lightsaber and guard against blaster fire. Inputs are real key presses, mouse clicks and menu
 * clicks; quest state is only read, never set.
 *
 * <p>Chapter Two follows in the same world (spec 16.3): sleep for the Two Voices dream, follow the
 * voices with the journal, talk to the Watchwoman and join the Jedi with the number keys (after a
 * creed answer that moves alignment), her camp, Force Pull in lesson one, a node learned on the
 * Force screen and cast with its key (Force Jump), the parry spar of lesson two, Smoke on the
 * Ridge (heavy attack, the crew, the Captain's yield and Spare, freeing the captives), the
 * debrief, the Trial of the Blade and Force Heal. Every Chapter Two step logs a FAIL and lets the
 * run go on; story commands used to get past a failed step are logged as shortcuts.
 *
 * <p>Chapter Three and then Chapter Four (spec 17.3, the Jedi run) follow in the same world. Chapter
 * Four repairs and flies the ship with clicks on its screen, does the world arc on Selvaren, the
 * Singing Hollow, the sabotage, both ambushes and the Kaast duel, spares him and reports. Each of its
 * steps checks the story first and skips what is done, and nothing it does escapes, so the summary
 * still prints; durasteel, long teleports, the two-day wait and the XP top-up are logged shortcuts.
 *
 * <p>Chapter Five (spec MILESTONE_5.md 19.3, the Jedi run) follows in the same world: the call
 * after a sleep, the ship flown by hand (the Helm key, the climb into orbit, the jump clicked on the
 * Nav tab, the dive and the landing on a hub's pad; the autopilot only as a logged fallback),
 * Saltgate's jobs the kajidic's way (the debt paid, the quiet cargo flown to its buoy), the
 * audience, the counting-house and Dask let go, the free cannons, the blockade fought with them,
 * the three verse stones, Ulvaine's three phases and her spare, the report, and the Master ceremony
 * after a logged shortcut to the rank 4 requirements. Its steps, like Chapter Four's, never escape.
 *
 * <p>Every check is recorded as PASS, FAIL or STUCK instead of failing at once, and every shortcut
 * (teleporting over long distances, time of day, vanilla items, keep-alive effects, removing stray
 * mobs) is logged. The summary is printed with every line prefixed {@code PLAYTHROUGH:}; the test
 * fails at the end only if a step got stuck, so all screenshots are still taken and uploaded.
 */
public class PlaythroughTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("oldrepublic-playthrough");
	private static final String SEED = "echoes-of-the-old-republic";

	private static final Function<Options, KeyMapping> FORWARD = o -> o.keyUp;
	private static final Function<Options, KeyMapping> JUMP = o -> o.keyJump;
	private static final Function<Options, KeyMapping> SNEAK = o -> o.keyShift;
	private static final Function<Options, KeyMapping> SPRINT = o -> o.keySprint;
	private static final Function<Options, KeyMapping> USE = o -> o.keyUse;
	private static final Function<Options, KeyMapping> ATTACK = o -> o.keyAttack;
	private static final Function<Options, KeyMapping> INVENTORY = o -> o.keyInventory;

	/** Hold time of a practice push: past the minimum charge, short enough to afford two in a row. */
	private static final int PUSH_HOLD_TICKS = 12;
	/** Health below which the bot gets keep-alive effects (logged as a shortcut). */
	private static final float RESCUE_HEALTH = 8f;
	/** How close a phase 2 Watcher comes before the bot turns to fight it. */
	private static final double WATCHER_RANGE = 10.0;
	/** How far from its crystal the bot stands to Pull it (the cast reaches a little further). */
	private static final double PULL_REACH = 10.5;
	/** How far from a downed trooper's middle the bot's eye may be to tend it (entity reach is 3). */
	private static final double TEND_REACH = 2.6;
	/** How far from the R2 crate's centre the bot's eye may be to open it (block reach is 4.5). */
	private static final double CRATE_REACH = 4.0;
	private static final double CAIRN_REACH = 4.0;
	/** Lesson one pulls a husk only from this close (the Pull reaches 12). */
	private static final double HUSK_PULL_DISTANCE = 10.0;
	/** A pylon crystal's tip above its centre (as {@code PylonFocus#inReach} checks it). */
	private static final double CRYSTAL_TIP = 0.3;
	/** Eye-to-aim-point distance the bot closes to before mining (survival block reach is 4.5). */
	private static final double MINE_REACH = 3.5;

	private enum Result {
		PASS, FAIL, STUCK, INFO
	}

	private record Entry(String step, Result result, String details) {
	}

	/** Thrown when the playthrough cannot continue; the summary is still printed. */
	private static final class Stuck extends RuntimeException {
		Stuck(String message) {
			super(message);
		}
	}

	/** Thrown after the player died and respawned; the current step is retried once. */
	private static final class Died extends RuntimeException {
		Died(String message) {
			super(message);
		}
	}

	/** Client view of the player for one tick. */
	private record Me(Vec3 pos, Vec3 eye, boolean collided, boolean onGround, float strength, int picked, float health, boolean dead) {
	}

	/** A blaster bolt seen near the player. */
	private record Bolt(UUID id, boolean deflected, boolean towardPoacher) {
	}

	/** Server view of a fight target. */
	private record Target(boolean alive, Vec3 pos, double height, int id) {
	}

	/**
	 * The heart piece of a generated cave, located from its meditation stone and resonant seal. Maps
	 * template-local coordinates of {@code crystal_cave/heart} (see tools/assets/gen_structures.py)
	 * to world positions, whatever the piece's rotation: local +z points from the seal to the stone.
	 */
	private record Heart(BlockPos stone, int dx, int dz, boolean locatorHitStone, int sealBlocks) {
		BlockPos block(int x, int y, int z) {
			int ox = x - 22;
			int oy = y - 1;
			int oz = z - 38;
			return stone.offset(ox * dz + oz * dx, oy, -ox * dx + oz * dz);
		}

		Vec3 feet(int x, int y, int z) {
			return Vec3.atBottomCenterOf(block(x, y, z));
		}

		/** A point offset from the centre of a local block, offsets given in local axes. */
		Vec3 point(int x, int y, int z, double ox, double oy, double oz) {
			Vec3 center = Vec3.atCenterOf(block(x, y, z));
			return center.add(ox * dz + oz * dx, oy, -ox * dx + oz * dz);
		}

		/** Yaw that faces local +z (from the cave entrance towards the trial chamber). */
		float yawInward() {
			return (float) (-Mth.atan2(dx, dz) * Mth.RAD_TO_DEG);
		}
	}

	/** Game messages the client received (chat and action bar), for logging refusals. */
	private static final List<String> MESSAGES = Collections.synchronizedList(new ArrayList<>());
	private static boolean listening;
	/** Damage the player took while {@link #recordHits} was on, described with the hit's direction (server thread writes). */
	private static final List<String> HITS = Collections.synchronizedList(new ArrayList<>());
	private static volatile boolean recordHits;
	/** Poacher deaths and emitter item entities appearing and going away (server thread writes). */
	private static final List<String> LOOT_EVENTS = Collections.synchronizedList(new ArrayList<>());
	/** Emitter item entities seen loading, by UUID, so their removal can be logged even after pickup empties the stack. */
	private static final Set<UUID> EMITTER_ITEMS = Collections.synchronizedSet(new HashSet<>());

	private final List<Entry> entries = new ArrayList<>();
	private final Map<String, Integer> shortcuts = new LinkedHashMap<>();
	private final List<BlockPos> knownCaves = new ArrayList<>();
	private ClientGameTestContext context;
	private TestSingleplayerContext singleplayer;
	private TestServerContext server;
	private int screenshots;
	private boolean stuck;
	private boolean forwardHeld;
	private int lastRescueTick = -1000;
	/** The Collector arena last seen, to come back to after a death drops the fight. */
	private @Nullable ArenaLayout lastArena;
	private int ticks;
	private float damageTaken;
	private @Nullable Heart heart;
	private @Nullable Vec3 lastCamp;
	/** Why the last {@link #walkTo} gave up (drop ahead, stalled, timeout). */
	private String walkStop = "";

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;

		if (!listening) {
			listening = true;
			// Records what the player is told, e.g. vanilla's bed refusals on the action bar.
			ClientReceiveMessageEvents.GAME.register((message, overlay) -> MESSAGES.add((overlay ? "[action bar] " : "[chat] ") + message.getString()));
			// Records where the hits the guard let through came from.
			ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
				if (recordHits && entity instanceof ServerPlayer player) {
					HITS.add(describeHit(player, source, taken));
				}
			});
			// Records where poachers die and what happens to the emitter they drop (picked up, burnt, despawned, unloaded).
			ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
				if (entity instanceof CrystalPoacher) {
					LOOT_EVENTS.add("poacher died at " + BlockPos.containing(entity.position()).toShortString() + " by " + source.getMsgId()
							+ " (direct " + (source.getDirectEntity() == null ? "none" : source.getDirectEntity().getClass().getSimpleName()) + ")");
				}
			});
			ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
				if (entity instanceof ItemEntity item && isEmitter(item.getItem()) && EMITTER_ITEMS.add(entity.getUUID())) {
					LOOT_EVENTS.add("emitter item appeared at " + BlockPos.containing(entity.position()).toShortString());
				}
			});
			ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
				if (entity instanceof ItemEntity && EMITTER_ITEMS.remove(entity.getUUID())) {
					// API-CHECK: Entity#getRemovalReason (probe api/enemy.txt); DISCARDED covers pickup and despawn, KILLED fire/lava/void/cactus.
					LOOT_EVENTS.add("emitter item gone at " + BlockPos.containing(entity.position()).toShortString() + " (" + entity.getRemovalReason() + ")");
				}
			});
		}

		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(creator -> {
					creator.setSeed(SEED);
					creator.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
				})
				.create()) {
			this.singleplayer = world;
			this.server = world.getServer();

			try {
				play();
			} catch (Stuck e) {
				LOG.info("PLAYTHROUGH-LOG: stopped: {}", e.getMessage());
			} catch (Throwable t) {
				note("unexpected error", Result.STUCK, t.getClass().getSimpleName() + ": " + t.getMessage());
				LOG.error("PLAYTHROUGH-LOG: unexpected error", t);
				shot("error");
			} finally {
				releaseAll();
			}
		}

		printSummary();

		if (stuck) {
			throw new AssertionError("Playthrough got stuck; see the PLAYTHROUGH: summary in the log");
		}
	}

	private void play() {
		step("01 start", this::start);
		step("03 awakening", this::awakening);
		step("04 meditation", this::meditation);
		step("05 practice", this::practice);
		step("06 calling", this::calling);
		step("07 crafting", this::craftParts);
		step("08 travel", this::travelToCave);
		step("09 camp fight", () -> fightCamp("camp 1", false));
		step("10 seal", this::enterHeart);
		step("11 trial", this::trial);
		step("12 crystal", this::crystal);
		step("13 hidden chamber", this::hiddenChamber);
		step("14 emitter", this::obtainEmitter);
		step("15 workbench", this::buildSaber);
		step("16 saber", this::saberCombat);
		step("17 finish", this::finish);

		// Chapter Two (spec 16.3): the same world goes on, on the Jedi path.
		chapterTwo("19 two voices", this::twoVoices);
		chapterTwo("20 follow the voices", this::followVoices);
		chapterTwo("21 crossing", this::crossingChoice);
		chapterTwo("22 mentor camp", this::mentorCamp);
		chapterTwo("23 lesson one", this::lessonOne);
		chapterTwo("24 skill screen", this::learnNode);
		chapterTwo("25 force jump", this::forceJump);
		chapterTwo("26 lesson two", this::lessonTwo);
		chapterTwo("27 smoke on the ridge", this::travelToRaiders);
		chapterTwo("28 raider fight", this::raiderFight);
		chapterTwo("29 captain", this::captainYield);
		chapterTwo("30 captives", this::freeCaptives);
		chapterTwo("31 reckoning complete", this::reckoningComplete);
		chapterTwo("32 debrief and trial", this::debriefAndTrial);
		chapterTwo("33 force heal", this::forceHeal);
		chapterTwo("34 chapter two state", this::chapterTwoState);

		// Chapter Three (spec 17.3): the same world goes on, on the Jedi path. The Sith and Independent runs
		// of 17.3 are not played here (one story path per world; they need their own worlds).
		chapterThree("35 the ones that sing", this::onesThatSing);
		chapterThree("36 the lesson", this::listeningLesson);
		chapterThree("37 cairns", this::cairns);
		chapterThree("38 first echo", this::firstEcho);
		chapterThree("39 ruin stair", this::ruinStair);
		chapterThree("40 listening hall", this::listeningHall);
		chapterThree("41 pilgrim niche", this::pilgrimNiche);
		chapterThree("42 republic outpost", this::republicOutpost);
		chapterThree("43 silent patrol", this::silentPatrol);
		chapterThree("44 credits from the core", this::creditsFromTheCore);
		chapterThree("45 leads and level", this::leadsAndLevel);
		chapterThree("46 the reliquary", this::reliquary);
		chapterThree("47 collector phase 1", this::collectorPhaseOne);
		chapterThree("48 collector phase 2", this::collectorPhaseTwo);
		chapterThree("49 collector phase 3", this::collectorPhaseThree);
		chapterThree("50 yield and spare", this::yieldAndSpare);
		chapterThree("51 the hoard", this::hoard);
		chapterThree("52 report", this::reportToMentor);
		chapterThree("53 chapter three state", this::chapterThreeState);
		chapterThree("54 rank three", this::rankThreeRequirements);
		chapterThree("55 knighting", this::knighting);

		// Chapter Four (spec 17.3 items 1 to 7): the Jedi run goes on in the same world. Nothing a Chapter Four step
		// does escapes (chapterFour), so the summary always prints. The Sith run of 17.3 is not played: it needs its
		// own world (one story path per world), and the Jedi run has not been proven on CI yet. The Independent run
		// (Tessaly's Road and Speed) is item 9 of the cut list.
		chapterFour("56 marked and the seeker", this::markedAndSeeker);
		chapterFour("57 briefing", this::briefing);
		chapterFour("58 bench and artifice", this::benchAndArtifice);
		chapterFour("59 ship parts", this::shipParts);
		chapterFour("60 motivator", this::motivator);
		chapterFour("61 nav computer", this::navComputer);
		chapterFour("62 hulk repairs", this::hulkRepairs);
		chapterFour("63 fuel", this::fuelAndLiftOff);
		chapterFour("64 first flight", this::firstFlight);
		chapterFour("65 ansa", this::meetAnsa);
		chapterFour("66 greenwatch", this::greenwatch);
		chapterFour("67 heartstone", this::heartstone);
		chapterFour("68 singing hollow", this::singingHollow);
		chapterFour("69 sabotage", this::sabotage);
		chapterFour("70 ship repair", this::shipRepair);
		chapterFour("71 ambush one", this::ambushOne);
		chapterFour("72 ambush two", this::ambushTwo);
		chapterFour("73 challenge", this::challenge);
		chapterFour("74 hunting camp", this::huntingCamp);
		chapterFour("75 kaast phase 1", () -> kaastPhase("75 kaast phase 1", 1));
		chapterFour("76 kaast phase 2", () -> kaastPhase("76 kaast phase 2", 2));
		chapterFour("77 kaast phase 3", () -> kaastPhase("77 kaast phase 3", 3));
		chapterFour("78 spare", this::spareKaast);
		chapterFour("79 report", this::reportChapterFour);
		chapterFour("80 chapter four state", this::chapterFourState);

		// Chapter Five (spec MILESTONE_5.md 19.3): the Jedi run goes on in the same world, its flights by hand (FlightOrbitTrip's
		// pilot) with the autopilot as the logged fallback. Nothing a Chapter Five step does escapes (chapterFive). The Sith run
		// of 19.3 is not played: one story path per world (DECISIONS.md).
		chapterFive("81 the call", this::theCall);
		chapterFive("82 to saltgate", this::toSaltgate);
		chapterFive("83 a debt in the canyons", this::debtJob);
		chapterFive("84 the quiet cargo", this::cargoJob);
		chapterFive("85 the audience", this::audience);
		chapterFive("86 the counting-house", this::countingHouse);
		chapterFive("87 let dask go", this::letDaskGo);
		chapterFive("88 free cannons", this::freeCannons);
		chapterFive("89 the blockade", this::blockade);
		chapterFive("90 the holdfast", this::holdfastLanding);
		chapterFive("91 verse stone 1", () -> verseStone("91 verse stone 1", 1));
		chapterFive("92 verse stone 2", () -> verseStone("92 verse stone 2", 2));
		chapterFive("93 verse stone 3", () -> verseStone("93 verse stone 3", 3));
		chapterFive("94 duel level", this::duelLevel);
		chapterFive("95 the hymnal", this::hymnal);
		chapterFive("96 ulvaine phase 1", () -> ulvainePhase("96 ulvaine phase 1", 1));
		chapterFive("97 ulvaine phase 2", () -> ulvainePhase("97 ulvaine phase 2", 2));
		chapterFive("98 ulvaine phase 3", () -> ulvainePhase("98 ulvaine phase 3", 3));
		chapterFive("99 spare", this::spareUlvaine);
		chapterFive("100 report", this::reportChapterFive);
		chapterFive("101 chapter five state", this::chapterFiveState);
		chapterFive("102 rank four", this::rankFourRequirements);
		chapterFive("103 master ceremony", this::masterCeremony);
	}

	/** Runs a step; if the player dies during it, the step is retried once from the respawn point. */
	private void step(String name, Runnable body) {
		for (int attempt = 1; ; attempt++) {
			try {
				logWhere(name, attempt == 1 ? "start" : "retry " + attempt);
				body.run();

				if (context.computeOnClient(mc -> mc.player.isDeadOrDying())) {
					// Died somewhere the fight and walk loops do not watch (e.g. while waiting).
					died(name);
				}

				logWhere(name, "end");
				return;
			} catch (Died e) {
				if (attempt >= 2) {
					stuck(name, "died again on the retry: " + e.getMessage());
				}

				note(name, Result.INFO, "retrying the step after dying");
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Steps
	// ---------------------------------------------------------------------------------------------

	private void start() {
		waitForChunks("spawn");
		server.runCommand("weather clear");
		// API-CHECK: LevelReader#getBiome + Holder#unwrapKey (standard, not in the probe dumps).
		String where = onServer(p -> p.blockPosition().toShortString() + " in "
				+ p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
		ForceData data = data();
		boolean survival = onServer(p -> !p.isCreative() && !p.isSpectator());
		check("01 world", survival && stage(data) == QuestStage.STIRRING && !data.hasAbility("force_push"),
				"seed '" + SEED + "', spawn " + where + ", survival=" + survival + ", stage=" + stage(data));
		shot("spawn");
		moveToStartSpot();

		shortcut("gave starting kit a survivor would have: white bed, 16 bread, iron sword, iron pickaxe, iron armour (equipped)");
		server.runCommand("give @a minecraft:iron_sword");
		server.runCommand("give @a minecraft:white_bed");
		server.runCommand("give @a minecraft:bread 16");
		server.runCommand("give @a minecraft:iron_pickaxe");
		server.runCommand("item replace entity @a armor.head with minecraft:iron_helmet");
		server.runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate");
		server.runCommand("item replace entity @a armor.legs with minecraft:iron_leggings");
		server.runCommand("item replace entity @a armor.feet with minecraft:iron_boots");
		shortcut("keepInventory on, so a death (logged as FAIL) does not end the run");
		server.runCommand("gamerule keep_inventory true");
		shortcut("saturation effect for the whole run (the bot does not eat)");
		server.runCommand("effect give @a minecraft:saturation infinite 0 true");
		context.waitTicks(5);

		// Before the Awakening the Force key must do nothing.
		float energyBefore = data().energy;
		context.getInput().holdKeyFor(InputConstants.KEY_R, 20);
		context.waitTicks(5);
		ForceData after = data();
		check("02 no powers before the Awakening", !after.hasAbility("force_push") && after.cooldown("force_push") == 0
				&& after.energy == energyBefore && stage(after) == QuestStage.STIRRING,
				"held R for 20 ticks: cooldown=" + after.cooldown("force_push") + ", energy " + energyBefore + " -> " + after.energy);
	}

	/** Sleeps in a bed at night, which should trigger the Awakening dream on waking. */
	private void awakening() {
		String step = "03 awakening (sleep)";
		shortcut("time set to night so the bed can be used");
		server.runCommand("time set 13000");
		context.waitTicks(5);
		clearInterference(step);
		Item bedItem = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("white_bed"));
		boolean awakened = false;

		for (int attempt = 1; attempt <= 3 && !awakened; attempt++) {
			if (attempt > 1) {
				// Another spot: a flat place further along, with a fresh bed.
				Vec3 spot = onServer(p -> findFlatSpot(p.level(), p.blockPosition().getX() + 12, p.blockPosition().getZ(), 40));

				if (spot != null) {
					shortcut("teleported to another flat spot for the bed after a failed sleep");
					teleport(spot, 0f);
				}

				if (count(bedItem) == 0) {
					shortcut("gave another white bed for the next sleep attempt");
					server.runCommand("give @a minecraft:white_bed");
					context.waitTicks(3);
				}

				shortcut("time set to night again for sleep attempt " + attempt);
				server.runCommand("time set 13000");
				context.waitTicks(3);
			}

			// API-CHECK: BlockTags.BEDS and BlockState#is(TagKey) (standard, not in the probe dumps).
			BlockPos bed = placeBlock(bedItem, state -> state.is(BlockTags.BEDS), true);

			if (bed == null) {
				note(step, Result.FAIL, "attempt " + attempt + ": could not place the bed by right-clicking the ground");
				continue;
			}

			note(step, Result.INFO, "attempt " + attempt + ": bed placed by right-clicking the ground at " + bed.toShortString());
			boolean asleep = sleepIn(step, attempt, bed);

			if (!asleep) {
				continue;
			}

			context.waitTicks(40);
			shot("sleeping");
			boolean woke = waitServer(300, p -> !p.isSleeping());
			context.waitTicks(30);
			shot("dream");
			awakened = waitServer(260, p -> stage(Progression.get(p)) != QuestStage.STIRRING);
			note(step, awakened ? Result.INFO : Result.FAIL, "attempt " + attempt + ": woke=" + woke + ", awakened=" + awakened);
		}

		ForceData data = data();
		boolean journal = count(QuestItems.WANDERERS_JOURNAL) == 1;

		if (!awakened) {
			stuck(step, "sleeping did not trigger the Awakening (stage " + stage(data) + "); the time trigger needs "
					+ Progression.balance().awakeningTicks + " ticks alive, too long to wait");
		}

		context.waitTicks(20);
		shot("awakened");
		check(step, stage(data) == QuestStage.FOCUS && journal && data.hasFlag("resonance:awakening")
				&& data.hasFlag(JournalEntries.entryFlag(JournalEntries.THE_DREAM)),
				"stage=" + stage(data) + ", journal=" + journal + ", resonance=" + data.resonance + ", energy=" + data.energy + "/" + data.maxEnergy);
	}

	/**
	 * Clears monsters that would block resting, steps next to the bed, aims at the middle of the
	 * bed's foot and right-clicks it (twice if needed). Logs vanilla's refusal message and the
	 * server-side conditions when the player does not fall asleep.
	 */
	private boolean sleepIn(String step, int attempt, BlockPos bed) {
		int removed = onServer(p -> {
			int n = 0;

			for (Entity m : p.level().getEntities((Entity) null, new AABB(bed).inflate(10), x -> x instanceof Monster && x.isAlive() && !(x instanceof CrystalPoacher))) {
				m.discard();
				n++;
			}

			return n;
		});

		if (removed > 0) {
			shortcut("removed " + removed + " monster(s) within 10 blocks of the bed (vanilla refuses sleep with monsters nearby)");
		}

		// Stand next to the bed, on the side of the player.
		Vec3 me = context.computeOnClient(mc -> mc.player.position());
		Vec3 foot = Vec3.atBottomCenterOf(bed);
		Vec3 away = new Vec3(me.x - foot.x, 0, me.z - foot.z);
		Vec3 beside = away.lengthSqr() < 1.0E-4 ? foot.add(1.2, 0, 0) : foot.add(away.normalize().scale(1.3));
		walkTo(beside, 0.4, 100, false, null);
		boolean asleep = false;

		for (int click = 1; click <= 2 && !asleep; click++) {
			int mark = MESSAGES.size();
			// The bed is 9/16 of a block tall: aim at the middle of its body, not above it.
			aimAt(new Vec3(bed.getX() + 0.5, bed.getY() + 0.3, bed.getZ() + 0.5));
			context.waitTick();
			String looking = lookingAt();
			context.getInput().pressKey(USE);
			asleep = waitServer(40, ServerPlayer::isSleeping);

			if (!asleep) {
				List<String> said;

				synchronized (MESSAGES) {
					said = new ArrayList<>(MESSAGES.subList(Math.min(mark, MESSAGES.size()), MESSAGES.size()));
				}

				String conditions = onServer(p -> {
					ServerLevel level = p.level();
					Vec3 c = Vec3.atBottomCenterOf(bed);
					long monsters = level.getEntities((Entity) null, new AABB(c.x - 8, c.y - 5, c.z - 8, c.x + 8, c.y + 5, c.z + 8),
							x -> x instanceof Monster m && m.isPreventingPlayerRest(level, p)).size();
					BlockState state = level.getBlockState(bed);
					return "bed block " + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + ", player " + p.blockPosition().toShortString()
							+ String.format(Locale.ROOT, " (%.1f blocks from the bed)", p.position().distanceTo(c)) + ", bright outside=" + level.isBrightOutside()
							+ ", clock time " + level.getOverworldClockTime() % 24000 + ", monsters preventing rest " + monsters
							+ ", above the bed " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(bed.above()).getBlock()).getPath();
				});
				note(step, Result.FAIL, "attempt " + attempt + " click " + click + ": not sleeping; crosshair on " + looking + "; messages " + said + "; " + conditions);
			} else {
				note(step, Result.INFO, "attempt " + attempt + " click " + click + ": sleeping (crosshair on " + looking + ")");
			}
		}

		return asleep;
	}

	/**
	 * The centre of the nearest flat, dry 5x5 patch (equal surface height, solid non-fluid ground,
	 * no leaves) to (ox, oz) within {@code radius}, searched in a fixed order so a seed always gives
	 * the same spot. Server thread only.
	 */
	private static @Nullable Vec3 findFlatSpot(ServerLevel level, int ox, int oz, int radius) {
		for (int r = 0; r <= radius; r += 2) {
			for (int dx = -r; dx <= r; dx += 2) {
				for (int dz = -r; dz <= r; dz += 2) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}

					int cx = ox + dx;
					int cz = oz + dz;
					level.getChunk(cx >> 4, cz >> 4, ChunkStatus.FULL, true);
					int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
					boolean flat = true;

					for (int x = cx - 2; x <= cx + 2 && flat; x++) {
						for (int z = cz - 2; z <= cz + 2 && flat; z++) {
							level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
							BlockPos ground = new BlockPos(x, h - 1, z);
							BlockState state = level.getBlockState(ground);
							flat = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) == h
									&& level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) == h
									&& !state.getCollisionShape(level, ground).isEmpty() && state.getFluidState().isEmpty() && !state.is(Blocks.POWDER_SNOW)
									&& open(level, ground.above()) && open(level, ground.above(2));
						}
					}

					if (flat) {
						return new Vec3(cx + 0.5, h, cz + 0.5);
					}
				}
			}
		}

		return null;
	}

	/** No collision and no fluid (air, grass, a thin snow layer). */
	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty() && !state.is(Blocks.POWDER_SNOW);
	}

	/** Teleports to a fixed flat spot near the world origin, so every run starts in the same place. */
	private void moveToStartSpot() {
		Vec3 spot = onServer(p -> findFlatSpot(p.level(), 0, 0, 64));

		if (spot == null) {
			note("01 start", Result.INFO, "no flat 5x5 spot within 64 blocks of 0, 0; staying at the spawn point");
			return;
		}

		shortcut("teleported from the (randomised) spawn point to a fixed flat spot near 0, 0 so every run starts in the same place");
		teleport(spot, 0f);
		String biome = onServer(p -> p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
		note("01 start", Result.INFO, "start spot " + BlockPos.containing(spot).toShortString() + " in " + biome);
	}

	/** Crouches still with the journal in hand until the meditation teaches Force Push. */
	private void meditation() {
		String step = "04 meditation";
		clearInterference(step);

		if (!select(QuestItems.WANDERERS_JOURNAL)) {
			stuck(step, "could not put the journal in hand");
		}

		context.getInput().holdKey(SNEAK);
		context.waitTicks(80);
		shot("meditation");
		boolean learned = waitServer(260, p -> Progression.get(p).hasAbility("force_push"));
		context.getInput().releaseKey(SNEAK);
		ForceData data = data();
		check(step, learned && stage(data) == QuestStage.PRACTICE,
				"crouched still holding the journal: push learned=" + learned + ", stage=" + stage(data) + ", xp=" + data.xp + ", level=" + data.level);

		if (!learned) {
			stuck(step, "meditation never completed");
		}
	}

	/** Pushes hostile mobs until the practice counter completes. */
	private void practice() {
		String step = "05 practice";
		clearInterference(step);
		shortcut("summoned 3 slowed husks as push targets instead of waiting for night spawns");
		List<UUID> targets = new ArrayList<>(summonTargets(4));
		context.waitTicks(10);
		int casts = 0;

		while (casts < 8 && stage(data()) == QuestStage.PRACTICE) {
			if (casts == 4) {
				// Terrain may have put the first targets out of reach or sight: try the other side.
				shortcut("summoned 3 more husks on the other side after 4 casts without finishing the practice");
				targets.addAll(summonTargets(-4));
				context.waitTicks(10);
			}

			waitServer(300, p -> {
				ForceData d = Progression.get(p);
				return d.cooldown("force_push") == 0 && d.energy >= 25f;
			});
			Target target = nearest(targets);

			if (target == null) {
				break;
			}

			aimAt(target.pos().add(0, target.height() * 0.6, 0));
			context.waitTick();
			context.getInput().holdKey(InputConstants.KEY_R);
			context.waitTicks(PUSH_HOLD_TICKS);

			if (casts == 0) {
				shot("push_charge");
			}

			// Which husks the server would count as targets right now, and where everything is.
			String targeting = onServer(p -> {
				float charge = PushShape.chargeFromHold(ForcePush.balance(), PUSH_HOLD_TICKS);
				PushShape shape = ForcePush.shapeFor(p, charge);
				Set<UUID> hit = new HashSet<>();

				for (PushTargeting.Target t : PushTargeting.find(p, shape, ForcePush.balance())) {
					hit.add(t.entity().getUUID());
				}

				List<String> marks = new ArrayList<>();

				for (int i = 0; i < targets.size(); i++) {
					marks.add("#" + (i + 1) + (hit.contains(targets.get(i)) ? " yes" : " no"));
				}

				return String.format(Locale.ROOT, "charge %.2f, knockback %.2f, range %.1f, half angle %.0f, player pitch %.1f, targeted %s", charge,
						shape.knockback(), shape.range(), shape.halfAngle(), p.getXRot(), marks);
			});
			Map<UUID, Vec3> before = huskPositions(targets);
			context.getInput().releaseKey(InputConstants.KEY_R);
			Map<UUID, List<Motion>> motion = new LinkedHashMap<>();

			for (int i = 0; i <= 16; i++) {
				for (Map.Entry<UUID, Motion> m : huskMotion(targets).entrySet()) {
					motion.computeIfAbsent(m.getKey(), k -> new ArrayList<>()).add(m.getValue());
				}

				context.waitTick();

				if (i == 4 && casts == 0) {
					shot("push_release");
				}
			}

			note(step, Result.INFO, "cast " + (casts + 1) + " at release: " + targeting);

			for (int i = 0; i < targets.size(); i++) {
				List<Motion> samples = motion.get(targets.get(i));
				Vec3 start = before.get(targets.get(i));

				if (samples != null && start != null) {
					note(step, Result.INFO, "cast " + (casts + 1) + " husk #" + (i + 1) + ": " + describeMotion(start, samples));
				}
			}

			casts++;
			ForceData d = data();
			note(step, Result.INFO, "cast " + casts + ": practice " + d.counter("push_practice") + "/5, energy " + Math.round(d.energy)
					+ ", mastery " + d.mastery("force_push"));
			context.waitTicks(9);
		}

		ForceData data = data();
		check(step, stage(data) == QuestStage.CALLING && data.hasFlag("resonance:first_push"),
				casts + " real R casts, stage=" + stage(data) + ", practice=" + data.counter("push_practice") + ", mastery=" + data.mastery("force_push"));

		select(Items.IRON_SWORD);
		int killed = 0;

		for (UUID id : targets) {
			if (fight(id, 500, false, step)) {
				killed++;
			}
		}

		int left = onServer(p -> {
			int n = 0;

			for (UUID id : targets) {
				Entity e = p.level().getEntity(id);

				if (e != null && e.isAlive()) {
					e.discard();
					n++;
				}
			}

			return n;
		});

		if (left > 0) {
			shortcut("removed " + left + " practice husk(s) the bot could not finish off");
		}

		note(step, Result.INFO, "killed " + killed + "/" + targets.size() + " husks with the sword");

		if (stage(data) != QuestStage.CALLING) {
			stuck(step, "practice did not complete");
		}
	}

	/** Holds the journal and checks the Calling has a cave to point at. */
	private void calling() {
		String step = "06 calling";

		if (!select(QuestItems.WANDERERS_JOURNAL)) {
			note(step, Result.FAIL, "journal not found in the inventory");
		}

		server.runCommand("execute as @a run oldrepublic locatecave");
		BlockPos cave = null;

		for (int i = 0; i < 40 && cave == null; i++) {
			cave = onServer(CaveLocator::findNearestCave);
			context.waitTick();
		}

		if (cave == null) {
			stuck(step, "CaveLocator found no crystal cave within its search radius");
		}

		BlockPos found = cave;
		Vec3 me = context.computeOnClient(mc -> mc.player.position());
		double distance = Vec3.atCenterOf(found).distanceTo(me);
		aimAt(Vec3.atCenterOf(found).add(0, me.y - found.getY(), 0));
		context.waitTicks(60);
		shot("calling");
		note(step, Result.PASS, "Calling points at " + found.toShortString() + ", " + Math.round(distance) + " blocks away (direction only is sent to the client)");
	}

	/** Crafts the grip, the pommel and the workbench at a crafting table, by clicking the grid. */
	private void craftParts() {
		String step = "07 crafting";
		clearInterference(step);
		shortcut("gave vanilla crafting materials (2 crafting tables, iron, amethyst, smooth stone, leather, string, iron block, nuggets)");
		server.runCommand("give @a minecraft:crafting_table 2");
		server.runCommand("give @a minecraft:iron_ingot 4");
		server.runCommand("give @a minecraft:amethyst_shard 2");
		server.runCommand("give @a minecraft:smooth_stone 3");
		server.runCommand("give @a minecraft:leather 2");
		server.runCommand("give @a minecraft:string 2");
		server.runCommand("give @a minecraft:iron_block 1");
		server.runCommand("give @a minecraft:iron_nugget 4");
		context.waitTicks(5);

		BlockPos table = placeBlock(Items.CRAFTING_TABLE, state -> state.is(Blocks.CRAFTING_TABLE), false);

		if (table == null) {
			shortcut("crafting table placed with /setblock (no free spot for a real placement)");
			BlockPos feet = onServer(p -> p.blockPosition());
			table = feet.offset(0, 0, 2);
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:crafting_table", table.getX(), table.getY(), table.getZ()));
			context.waitTicks(3);
		}

		if (!openBlock(table, mc -> mc.player.containerMenu instanceof CraftingMenu)) {
			note(step, Result.FAIL, "crafting table screen did not open");
			return;
		}

		boolean grip = craft("grip_wrapped", new String[] { " L ", "SIS", " L " },
				Map.of('L', Items.LEATHER, 'S', Items.STRING, 'I', Items.IRON_INGOT), SaberItems.GRIP_WRAPPED);
		boolean pommel = craft("pommel_weighted", new String[] { " N ", "NBN" },
				Map.of('N', Items.IRON_NUGGET, 'B', Items.IRON_BLOCK), SaberItems.POMMEL_WEIGHTED);
		shot("crafting");
		boolean bench = craft("lightsaber_workbench", new String[] { "III", "ACA", "SSS" },
				Map.of('I', Items.IRON_INGOT, 'A', Items.AMETHYST_SHARD, 'C', Items.CRAFTING_TABLE, 'S', Items.SMOOTH_STONE), SaberItems.WORKBENCH.asItem());
		closeScreen();
		check(step, grip && pommel && bench, "grip_wrapped=" + grip + ", pommel_weighted=" + pommel + ", lightsaber_workbench=" + bench);
	}

	/** Travels to the cave the Calling points at and walks into the poacher camp. */
	private void travelToCave() {
		String step = "08 travel to the cave";
		BlockPos cave = onServer(CaveLocator::findNearestCaveNow);

		if (cave == null) {
			stuck(step, "no cave found");
		}

		if (knownCaves.isEmpty()) {
			knownCaves.add(cave);
		}

		boolean campFound = approachCamp(step, cave);

		if (campFound && lastCamp != null && !onServer(CaveLocator::isInsideCave)) {
			walkTo(lastCamp, 2, 400, true, () -> onServer(CaveLocator::isInsideCave));
		}

		waitServer(30, p -> stage(Progression.get(p)) == QuestStage.TRIAL);
		ForceData data = data();
		boolean inside = onServer(CaveLocator::isInsideCave);
		shot("camp");
		check(step, campFound && stage(data) == QuestStage.TRIAL,
				"camp poachers found=" + campFound + ", inside cave bounds=" + inside + ", stage=" + stage(data)
						+ ", journal voices_in_the_rock=" + data.hasFlag(JournalEntries.entryFlag(JournalEntries.VOICES_IN_THE_ROCK)));
	}

	/**
	 * Teleports near a cave (its start or, when the locator refined it, its meditation stone), finds
	 * the poachers, logs how the camp sits on the terrain and walks up to it, avoiding drops.
	 */
	private boolean approachCamp(String step, BlockPos cave) {
		CaveInfo info = caveInfo(cave);
		Vec3 yard = info != null && info.camp() != null ? campYard(info.camp()) : null;
		note(step, Result.INFO, "cave structure at " + cave.toShortString() + ": " + (info == null ? "NOT found"
				: box(info.structure()) + ", camp " + box(info.camp()) + ", descent " + box(info.descent()) + ", heart " + box(info.heart())));
		int x = yard != null ? Mth.floor(yard.x) : cave.getX();
		int z = (yard != null ? Mth.floor(yard.z) : cave.getZ()) + 40;
		double distance = onServer(p -> Math.sqrt(p.blockPosition().distSqr(new BlockPos(x, p.blockPosition().getY(), z))));
		int y = surfaceY(x, z);
		boolean isStone = onServer(p -> p.level().getBlockState(cave).is(WorldBlocks.MEDITATION_STONE));
		shortcut("teleported " + Math.round(distance) + " blocks to 40 blocks south of the " + (yard != null ? "camp piece" : "cave position") + " of the cave at "
				+ cave.toShortString());
		teleport(new Vec3(x + 0.5, y, z + 0.5), 180f);
		shortcut("time set to day on arrival");
		server.runCommand("time set 1000");
		clearInterference(step);

		if (yard == null) {
			// No camp piece found: fall back to looking for the poachers.
			List<UUID> poachers = List.of();

			for (int i = 0; i < 10 && poachers.isEmpty(); i++) {
				poachers = poachersNear(96);
				context.waitTicks(10);
			}

			if (poachers.isEmpty()) {
				note(step, Result.FAIL, "no camp piece and no crystal poachers within 96 blocks of " + cave.toShortString());
				return false;
			}

			yard = nearest(poachers).pos();
			note(step, Result.FAIL, "no camp piece found in the structure; heading for the nearest poacher at " + BlockPos.containing(yard).toShortString());
		}

		lastCamp = yard;
		logCampGround(step, yard, cave);
		logPoachers(step, info);
		boolean reached = walkTo(yard, 3, 900, true, () -> onServer(CaveLocator::isInsideCave));

		if (!reached) {
			Vec3 spot = safeSpotNear(yard);
			shortcut("walk to the camp stopped (" + walkStop + "); teleported to the surface next to the camp yard");
			note(step, Result.INFO, "walk to the camp stopped: " + walkStop + "; teleporting to " + BlockPos.containing(spot).toShortString());
			teleport(spot, 180f);
		}

		if (!waitServer(40, CaveLocator::isInsideCave) && info != null && info.camp() != null) {
			// Still not inside: step into the yard itself (the camp floor), then log what pieceAt says.
			Vec3 inside = yard;
			shortcut("teleported onto the camp yard because the player was not inside the camp piece");
			teleport(inside, 180f);
			waitServer(40, CaveLocator::isInsideCave);
		}

		return true;
	}

	/** Logs every poacher within 96 blocks and where it stands relative to the camp piece. */
	private void logPoachers(String step, @Nullable CaveInfo info) {
		List<UUID> poachers = poachersNear(96);
		List<String> parts = new ArrayList<>();

		for (UUID id : poachers) {
			Target t = target(id);
			parts.add(BlockPos.containing(t.pos()).toShortString() + " " + relativeToCamp(t.pos(), info));
		}

		note(step, Result.INFO, poachers.size() + " poacher(s) within 96 blocks: " + parts);
	}

	/** Where a position is relative to the camp piece box: inside, or the offset outside it. */
	private static String relativeToCamp(Vec3 pos, @Nullable CaveInfo info) {
		if (info == null || info.camp() == null) {
			return "(no camp piece)";
		}

		BoundingBox c = info.camp();
		BlockPos b = BlockPos.containing(pos);

		if (c.isInside(b)) {
			return "(inside the camp piece)";
		}

		int dx = b.getX() < c.minX() ? b.getX() - c.minX() : b.getX() > c.maxX() ? b.getX() - c.maxX() : 0;
		int dy = b.getY() < c.minY() ? b.getY() - c.minY() : b.getY() > c.maxY() ? b.getY() - c.maxY() : 0;
		int dz = b.getZ() < c.minZ() ? b.getZ() - c.minZ() : b.getZ() > c.maxZ() ? b.getZ() - c.maxZ() : 0;
		String other = info.pieceContaining(b);
		return "(outside the camp piece by dx " + dx + ", dy " + dy + ", dz " + dz + (other != null ? ", inside the " + other + " piece" : "") + ")";
	}

	/**
	 * Logs how the camp piece sits on the terrain: its yard height against the ground around it, and
	 * how many foundation columns hang over air (the camp can overhang on steep slopes).
	 */
	private void logCampGround(String step, Vec3 poacherPos, BlockPos cave) {
		String info = onServer(p -> {
			ServerLevel level = p.level();
			BlockPos at = BlockPos.containing(poacherPos);
			// API-CHECK: StructureManager#getStructureWithPieceAt, StructureStart#getPieces and the
			// BoundingBox minX()/maxX()/minY()/minZ()/maxZ() accessors (standard, used by CaveLocator).
			StructureStart start = caveStartAt(level, cave);

			if (start == null) {
				return "no crystal cave structure found at " + cave.toShortString();
			}

			for (StructurePiece piece : start.getPieces()) {
				if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single
						&& CaveLocator.CAMP_TEMPLATE.equals(single.getTemplateLocation())) {
					BoundingBox box = piece.getBoundingBox();
					int yard = box.minY() + 5;
					int low = Integer.MAX_VALUE;
					int high = Integer.MIN_VALUE;
					int[] xs = { box.minX() - 3, (box.minX() + box.maxX()) / 2, box.maxX() + 3 };
					int[] zs = { box.minZ() - 3, (box.minZ() + box.maxZ()) / 2, box.maxZ() + 3 };

					for (int sx : xs) {
						for (int sz : zs) {
							if (sx == xs[1] && sz == zs[1]) {
								continue;
							}

							level.getChunk(sx >> 4, sz >> 4, ChunkStatus.FULL, true);
							int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
							low = Math.min(low, h);
							high = Math.max(high, h);
						}
					}

					int columns = 0;
					int hanging = 0;

					for (int cx = box.minX(); cx <= box.maxX(); cx += 3) {
						for (int cz = box.minZ(); cz <= box.maxZ(); cz += 3) {
							BlockPos bottom = new BlockPos(cx, box.minY(), cz);
							BlockPos below = bottom.below();

							if (level.getBlockState(bottom).getCollisionShape(level, bottom).isEmpty()) {
								continue; // the shaft down to the descent, open on purpose
							}

							columns++;

							if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
								hanging++;
							}
						}
					}

					return "camp yard at y " + yard + " (piece y " + box.minY() + ".." + box.maxY() + "), surrounding ground y " + low + ".." + high
							+ ", foundation columns over air " + hanging + "/" + columns + (hanging > 0 ? " -> camp OVERHANGS" : "")
							+ (high - low > 12 ? " -> steep terrain" : "");
				}
			}

			return "no camp piece found in the structure at " + at.toShortString();
		});
		note(step, Result.INFO, "camp ground: " + info);
	}

	/**
	 * Fights the poachers around the player with the sword (or ignited saber) and picks up what
	 * they drop. With {@code untilEmitter} it stops as soon as an emitter is in the inventory.
	 */
	private int fightCamp(String label, boolean untilEmitter) {
		String step = "09 poacher fight (" + label + ")";
		select(Items.IRON_SWORD);
		float before = damageTaken;
		int killsBefore = data().counter("poacher_kills");
		int kills = 0;
		int fights = 0;
		Set<UUID> failed = new HashSet<>();
		CaveInfo info = knownCaves.isEmpty() ? null : caveInfo(knownCaves.get(knownCaves.size() - 1));

		if (lastCamp != null && context.computeOnClient(mc -> mc.player.position()).distanceTo(lastCamp) > 48) {
			// Back from a respawn: return to the camp.
			Vec3 spot = safeSpotNear(lastCamp);
			shortcut("teleported back to the camp after respawning (" + label + ")");
			teleport(spot, 180f);
			clearInterference(step);
		}

		while (fights < 6 && !(untilEmitter && hasEmitter())) {
			List<UUID> poachers = poachersNear(48).stream().filter(x -> !failed.contains(x)).toList();

			if (poachers.isEmpty()) {
				break;
			}

			UUID id = poachers.get(0);
			fights++;
			Target start = target(id);

			if (fight(id, 900, false, step)) {
				kills++;
				context.waitTicks(15);

				if (kills == 1) {
					shot("poacher_down");
				}

				collectDrops(step, 12);
				recoverEmitter(step);
			} else {
				failed.add(id);
				Target t = target(id);
				Vec3 me = context.computeOnClient(mc -> mc.player.position());
				note(step, Result.FAIL, "could not finish poacher " + fights + " within 900 ticks: it started at " + BlockPos.containing(start.pos()).toShortString()
						+ " " + relativeToCamp(start.pos(), info) + ", now " + (t.alive() ? BlockPos.containing(t.pos()).toShortString() + " " + relativeToCamp(t.pos(), info)
								+ String.format(Locale.ROOT, ", %.1f blocks from the player (height difference %.1f)", t.pos().distanceTo(me), t.pos().y - me.y)
								: "dead") + "; skipping it");
			}
		}

		ForceData data = data();
		String loot = "parts in inventory: " + partsSummary() + ", got_emitter flag=" + data.hasFlag(PoacherLoot.EMITTER_FLAG);
		check(step, kills > 0, kills + " poacher(s) killed, poacher_kills " + killsBefore + " -> " + data.counter("poacher_kills")
				+ ", damage taken " + Math.round(damageTaken - before) + ", " + loot
				+ ", journal cutters=" + data.hasFlag(JournalEntries.entryFlag(JournalEntries.CUTTERS)));
		return kills;
	}

	/** Goes down to the heart, walks the cavern and opens the resonant seal. */
	private void enterHeart() {
		String step = "10 seal";
		BlockPos cave = knownCaves.get(0);

		if (lastCamp != null && context.computeOnClient(mc -> mc.player.position()).distanceTo(lastCamp) > 160) {
			// Back from a respawn: return to the first camp so the locator refines to its heart.
			Vec3 camp = lastCamp;
			Vec3 spot = safeSpotNear(camp);
			shortcut("teleported back to the camp after respawning");
			teleport(spot, 180f);
		}

		heart = findHeart();

		if (heart == null || heart.sealBlocks() == 0 || (heart.dx() == 0 && heart.dz() == 0)) {
			note(step, Result.INFO, "heart lookup: " + heartDiagnostics());
			stuck(step, "could not locate the heart piece (meditation stone or seal) near " + cave.toShortString()
					+ (heart == null ? "" : ", stone " + heart.stone().toShortString() + ", seal blocks " + heart.sealBlocks()));
		}

		Heart h = heart;
		note("10 locator", h.locatorHitStone() ? Result.PASS : Result.FAIL,
				"CaveLocator heart position " + (h.locatorHitStone() ? "is" : "is NOT") + " the meditation stone; stone at " + h.stone().toShortString()
						+ ", seal blocks " + h.sealBlocks() + ", inward axis (" + h.dx() + ", " + h.dz() + ")");

		Vec3 entrance = h.feet(22, 1, 5);
		shortcut("teleported down the camp shaft to the heart entrance tunnel (ladders and the stepped tunnel are not bot-navigable)");
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f 0", entrance.x, entrance.y, entrance.z, h.yawInward()));
		waitForChunks(step);
		clearInterference(step);
		context.waitTicks(20);
		shot("cavern");

		boolean walked = walkPath(step, h.feet(22, 1, 14), h.feet(22, 1, 22), h.feet(22, 1, 27));
		BlockPos seal = h.block(22, 2, 29);
		Vec3 sealFace = h.point(22, 2, 29, 0, 0, -0.5);
		double reach = context.computeOnClient(mc -> mc.player.getEyePosition()).distanceTo(sealFace);

		if (reach > 4.0) {
			shortcut("teleported in front of the seal: the cavern walk ended out of reach of it");
			note(step, Result.INFO, String.format(Locale.ROOT, "seal out of reach (%.1f blocks) after the walk; teleporting to the doorway", reach));
			teleport(h.feet(22, 1, 27), h.yawInward());
		}

		String before = onServer(p -> {
			ForceData d = Progression.get(p);
			return "stage " + stage(d) + ", awakened=" + d.isAwakened() + ", push=" + d.hasAbility("force_push") + ", seal refusal="
					+ ResonantSealBlock.refusalKey(p);
		});
		aimAt(sealFace);
		context.waitTick();
		String looking = lookingAt();
		note(step, Result.INFO, "before the click: " + before + "; crosshair on " + looking);
		context.getInput().pressKey(USE);
		boolean isSeal = onServer(p -> p.level().getBlockState(seal).is(WorldBlocks.RESONANT_SEAL));
		boolean open = false;

		for (int t = 0; t < 20 && !open; t++) {
			open = sealOpen(seal);
			context.waitTick();
		}

		shot("seal");

		if (!open) {
			String refusal = onServer(ResonantSealBlock::refusalKey);
			// The refusal is shown to the player as an overlay message with this translation key.
			String shown = refusal == null ? "none (the seal accepts this player)"
					: refusal + " = \"" + context.computeOnClient(mc -> net.minecraft.network.chat.Component.translatable(refusal).getString()) + "\"";
			note(step, Result.INFO, "the seal stayed closed; refusal message: " + shown + "; crosshair was on " + looking);
		}

		check(step, open, "walked the cavern=" + walked + ", seal block where expected=" + isSeal + ", right-clicked the seal: open=" + open);

		if (!open) {
			stuck(step, "the resonant seal did not open");
		}
	}

	/** Starts the trial at the meditation stone and fights the Shadow Echo, retrying on failure. */
	private void trial() {
		String step = "11 trial";
		Heart h = heart;
		float before = damageTaken;
		int attempts = 0;
		boolean won = false;

		while (attempts < 3 && !won) {
			attempts++;
			returnToHeart(step);
			boolean started = onServer(TrialManager::isInTrial);

			if (!started) {
				enterChamber(step);
				int mark = MESSAGES.size();
				// The stone is 10/16 tall: aim into its body (y + 0.35), not at its top edge.
				aimAt(h.point(22, 1, 38, 0, -0.15, 0));
				context.waitTick();
				String looking = lookingAt();
				double distance = context.computeOnClient(mc -> mc.player.getEyePosition()).distanceTo(Vec3.atCenterOf(h.stone()));
				context.getInput().pressKey(USE);
				started = waitServer(20, TrialManager::isInTrial);

				if (!started) {
					String refusal = onServer(p -> TrialManager.requirementRefusal(Progression.get(p), p.isCreative()));
					List<String> said;

					synchronized (MESSAGES) {
						said = new ArrayList<>(MESSAGES.subList(Math.min(mark, MESSAGES.size()), MESSAGES.size()));
					}

					String held = onServer(p -> BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).getPath() + (p.isShiftKeyDown() ? ", sneaking" : ""));
					note(step, Result.FAIL, "attempt " + attempts + ": using the stone did not start the trial (requirement refusal " + refusal + "); crosshair on "
							+ looking + String.format(Locale.ROOT, ", eye %.2f blocks from the stone centre", distance) + ", holding " + held + ", messages " + said);
					continue;
				}

				note(step, Result.INFO, "attempt " + attempts + ": trial started (crosshair on " + looking + String.format(Locale.ROOT, ", %.2f blocks)", distance));
			}

			context.waitTicks(20);
			shot("trial_forming");
			UUID echo = null;

			for (int i = 0; i < 80 && echo == null; i++) {
				echo = onServer(p -> {
					for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(16), x -> x instanceof ShadowEchoEntity && x.isAlive())) {
						if (p.getUUID().equals(((ShadowEchoEntity) e).ownerId())) {
							return e.getUUID();
						}
					}

					return null;
				});
				context.waitTick();
			}

			if (echo == null) {
				note(step, Result.FAIL, "attempt " + attempts + ": no Shadow Echo formed");
				continue;
			}

			context.waitTicks(5);
			shot("shadow_echo");
			select(Items.IRON_SWORD);
			boolean killed = fight(echo, 1600, true, step);
			context.waitTicks(10);
			won = data().hasFlag(CrystalGrant.TRIAL_COMPLETE);
			boolean inTrial = onServer(TrialManager::isInTrial);
			note(step, won ? Result.INFO : Result.FAIL, "attempt " + attempts + ": echo killed=" + killed + ", trial complete=" + won + ", still in trial=" + inTrial);
		}

		ForceData data = data();
		check(step, won && stage(data) == QuestStage.TRIAL && data.hasFlag(JournalEntries.entryFlag(JournalEntries.REFLECTION)),
				attempts + " attempt(s), damage taken " + Math.round(damageTaken - before) + ", stage=" + stage(data) + ", resonance=" + data.resonance);

		if (!won) {
			stuck(step, "could not win the Trial of Reflection");
		}
	}

	/** Walks from the cavern side through the seal (opening it if it closed) to the meditation stone. */
	private void enterChamber(String step) {
		Heart h = heart;
		Vec3 me = context.computeOnClient(mc -> mc.player.position());

		if (me.distanceTo(Vec3.atBottomCenterOf(h.stone())) < 6) {
			walkTo(h.feet(22, 1, 35), 0.6, 120, false, null);
			return;
		}

		walkTo(h.feet(22, 1, 27), 0.6, 300, false, null);
		BlockPos seal = h.block(22, 2, 29);

		if (!sealOpen(seal)) {
			aimAt(h.point(22, 2, 29, 0, 0, -0.5));
			context.waitTick();
			context.getInput().pressKey(USE);
			context.waitTicks(5);
		}

		walkPath(step, h.feet(22, 1, 31), h.feet(22, 1, 35));
	}

	/** Waits for the crystal to form on the stone and picks it up by walking to it. */
	private void crystal() {
		String step = "12 crystal";
		Heart h = heart;
		BlockPos stone = h.stone();

		if (returnToHeart(step)) {
			enterChamber(step);
		}

		boolean formed = waitServer(120, p -> !p.level().getEntities((Entity) null, new AABB(stone).inflate(2),
				e -> e instanceof ItemEntity item && item.getItem().is(SaberItems.KYBER_CRYSTAL)).isEmpty());
		context.waitTicks(10);
		shot("crystal_formed");
		boolean obtained = false;
		int[][] sides = { { 0, -1 }, { -1, 0 }, { 1, 0 }, { 0, 1 } };

		for (int[] side : sides) {
			walkTo(h.feet(22 + side[0], 1, 38 + side[1]), 0.5, 160, false, null);
			obtained = waitServer(50, p -> stage(Progression.get(p)) == QuestStage.CONSTRUCTION);

			if (obtained) {
				break;
			}
		}

		ForceData data = data();
		int owned = onServer(p -> {
			int n = 0;

			for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
				ItemStack stack = p.getInventory().getItem(slot);
				CrystalAttunement attunement = stack.get(ModComponents.CRYSTAL_ATTUNEMENT);

				if (stack.is(SaberItems.KYBER_CRYSTAL) && attunement != null && attunement.owner().equals(p.getUUID())) {
					n++;
				}
			}

			return n;
		});
		check(step, formed && obtained && owned == 1, "formed on the stone=" + formed + ", picked up=" + obtained + ", attuned crystals carried=" + owned
				+ ", stage=" + stage(data));

		if (owned == 0) {
			stuck(step, "no attuned crystal in the inventory");
		}
	}

	/** Leaves the chamber, braves the unstable passage, mines the veined wall and loots the chest. */
	private void hiddenChamber() {
		String step = "13 hidden chamber";
		Heart h = heart;
		float before = damageTaken;

		if (returnToHeart(step)) {
			// Respawned and back at the entrance tunnel: walk the cavern up to the side passage.
			walkPath(step, h.feet(22, 1, 14), h.feet(22, 1, 22));
		} else {
			walkTo(h.feet(22, 1, 31), 0.6, 200, false, null);
			BlockPos seal = h.block(22, 2, 29);

			if (!sealOpen(seal)) {
				aimAt(h.point(22, 2, 29, 0, 0, 0.5));
				context.waitTick();
				context.getInput().pressKey(USE);
				context.waitTicks(5);
			}
		}

		boolean walked = walkPath(step, h.feet(22, 1, 26), h.feet(26, 1, 18), h.feet(31, 1, 18), h.feet(38, 1, 18), h.feet(38, 1, 9));
		// Is the hidden chamber really hidden? Search a walkable route to the chest without mining.
		String wall = onServer(p -> {
			List<String> names = new ArrayList<>();

			for (int y = 1; y <= 3; y++) {
				for (int x = 37; x <= 39; x++) {
					BlockPos b = h.block(x, y, 7);
					names.add(BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(b).getBlock()).getPath());
				}
			}

			return String.join(" ", names);
		});
		BlockPos chestFront = h.block(38, 1, 3);
		BlockPos from = onServer(p -> p.blockPosition());
		int route = onServer(p -> walkableSteps(p.level(), from, chestFront, BoundingBox.fromCorners(from, chestFront).inflatedBy(24), 60000));
		note(step, route < 0 ? Result.INFO : Result.FAIL, "veined wall (local x 37..39, y 1..3) before mining: " + wall + "; walkable route from "
				+ from.toShortString() + " to the chest without mining: " + (route < 0 ? "none (the chamber is hidden)" : route + " steps (the chamber is NOT hidden)"));
		Vec3 eye = context.computeOnClient(mc -> mc.player.getEyePosition());
		note(step, Result.INFO, String.format(Locale.ROOT, "mining from %s, eye %.2f blocks from the upper wall block", from.toShortString(),
				eye.distanceTo(Vec3.atCenterOf(h.block(38, 2, 7)))));
		select(Items.IRON_PICKAXE);
		boolean upper = mine(step, h.block(38, 2, 7), h.point(38, 2, 7, 0, 0, 0.45));
		boolean lower = mine(step, h.block(38, 1, 7), h.point(38, 1, 7, 0, 0, 0.45));
		shot("hidden_wall");
		walkPath(step, h.feet(38, 1, 6), h.feet(38, 1, 4));
		BlockPos chest = h.block(38, 1, 2);
		boolean isChest = onServer(p -> p.level().getBlockState(chest).is(Blocks.CHEST));
		List<String> loot = new ArrayList<>();

		if (isChest && openBlock(chest, mc -> mc.player.containerMenu != mc.player.inventoryMenu && mc.player.containerMenu.slots.size() == 63)) {
			context.waitTicks(5);
			shot("hidden_chest");
			loot.addAll(context.computeOnClient(mc -> {
				List<String> names = new ArrayList<>();
				AbstractContainerMenu menu = mc.player.containerMenu;

				for (int slot = 0; slot < 27; slot++) {
					ItemStack stack = menu.slots.get(slot).getItem();

					if (!stack.isEmpty()) {
						names.add(stack.getCount() + "x " + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
						mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
					}
				}

				return names;
			}));
			context.waitTicks(5);
			closeScreen();
		}

		check(step, walked && upper && lower && isChest, "walked=" + walked + ", mined veined wall=" + (upper && lower) + ", chest found=" + isChest
				+ ", loot " + loot + ", damage taken (unstable clusters) " + Math.round(damageTaken - before));
	}

	/** Makes sure an emitter is in the inventory, visiting further poacher camps if needed. */
	private void obtainEmitter() {
		String step = "14 emitter";

		for (int visit = 2; visit <= 3 && !hasEmitter(); visit++) {
			BlockPos other = findOtherCave();

			if (other == null) {
				note(step, Result.FAIL, "no further crystal cave found to hunt poachers in");
				break;
			}

			knownCaves.add(other);
			note(step, Result.INFO, "no emitter yet after " + data().counter("poacher_kills") + " poacher kill(s); heading to cave " + visit + " at " + other.toShortString());

			if (approachCamp(step, other)) {
				fightCamp("camp " + visit, true);
			}
		}

		check(step, hasEmitter(), "poacher_kills=" + data().counter("poacher_kills") + ", parts: " + partsSummary());

		if (!hasEmitter()) {
			stuck(step, "no emitter obtained, the saber cannot be built");
		}
	}

	/** Places the workbench, shift-clicks the parts in, constructs the saber and takes it out. */
	private void buildSaber() {
		String step = "15 workbench";
		boolean threatened = !poachersNear(40).isEmpty();
		BlockPos feet = onServer(p -> p.blockPosition());
		boolean underground = feet.getY() < surfaceY(feet.getX(), feet.getZ()) - 4;

		if (threatened || underground) {
			Vec3 from = context.computeOnClient(mc -> mc.player.position());
			int fx = Mth.floor(from.x);
			int fz = Mth.floor(from.z);
			// Dry, solid, flat ground (never water) with no poachers around it.
			Vec3 spot = onServer(p -> {
				int[][] offsets = { { 80, 0 }, { -80, 0 }, { 0, 80 }, { 0, -80 }, { 120, 120 }, { -120, -120 } };

				for (int[] o : offsets) {
					Vec3 found = findFlatSpot(p.level(), fx + o[0], fz + o[1], 48);

					if (found != null && p.level().getEntities((Entity) null, new AABB(BlockPos.containing(found)).inflate(40),
							e -> e instanceof CrystalPoacher && e.isAlive()).isEmpty()) {
						return found;
					}
				}

				return null;
			});

			if (spot == null) {
				note(step, Result.FAIL, "no dry flat spot without poachers found within reach; assembling here");
			} else {
				shortcut("teleported to a dry flat spot " + Math.round(spot.distanceTo(from)) + " blocks away to assemble the saber in peace ("
						+ (threatened ? "poachers nearby" : "underground") + ")");
				teleport(spot, 0f);
				note(step, Result.INFO, "assembly spot " + BlockPos.containing(spot).toShortString());
			}
		}

		clearInterference(step);
		BlockPos bench = placeBlock(SaberItems.WORKBENCH.asItem(), state -> state.is(SaberItems.WORKBENCH), false);

		if (bench == null) {
			BlockPos at = onServer(p -> p.blockPosition());
			note(step, Result.FAIL, "could not place the workbench at " + at.toShortString() + " (standing on "
					+ onServer(p -> BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(at.below()).getBlock()).getPath()) + ", workbenches in the inventory "
					+ count(SaberItems.WORKBENCH.asItem()) + "); trying another spot");
			Vec3 spot = onServer(p -> findFlatSpot(p.level(), at.getX() + 16, at.getZ(), 48));

			if (spot != null) {
				shortcut("teleported to another flat spot to place the workbench");
				teleport(spot, 0f);
				bench = placeBlock(SaberItems.WORKBENCH.asItem(), state -> state.is(SaberItems.WORKBENCH), false);
			}
		}

		if (bench == null) {
			stuck(step, "could not place the crafted workbench");
		}

		if (!openBlock(bench, mc -> mc.player.containerMenu instanceof LightsaberWorkbenchMenu)) {
			stuck(step, "the workbench screen did not open");
		}

		List<Item> parts = new ArrayList<>(List.of(SaberItems.KYBER_CRYSTAL, SaberItems.GRIP_WRAPPED, SaberItems.POMMEL_WEIGHTED));
		parts.add(count(SaberItems.EMITTER_GUARDIAN) > 0 ? SaberItems.EMITTER_GUARDIAN : SaberItems.EMITTER_DUELIST);

		for (Item part : parts) {
			context.runOnClient(mc -> {
				AbstractContainerMenu menu = mc.player.containerMenu;

				for (int slot = SaberAssembly.SIZE; slot < menu.slots.size(); slot++) {
					if (menu.slots.get(slot).getItem().is(part)) {
						mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
						return;
					}
				}
			});
			context.waitTicks(3);
		}

		boolean ready = waitClient(40, mc -> mc.player.containerMenu instanceof LightsaberWorkbenchMenu menu && menu.status() == SaberAssembly.Status.READY);
		String preview = context.computeOnClient(mc -> mc.player.containerMenu instanceof LightsaberWorkbenchMenu menu
				? "status " + menu.status() + ", damage " + menu.data(LightsaberWorkbenchMenu.DATA_DAMAGE_X100) / 100.0
						+ ", speed " + menu.data(LightsaberWorkbenchMenu.DATA_SPEED_X100) / 100.0
						+ ", stamina " + menu.data(LightsaberWorkbenchMenu.DATA_STAMINA)
						+ ", parry " + menu.data(LightsaberWorkbenchMenu.DATA_PARRY_X10) / 10.0
				: "screen closed");
		shot("workbench_ready");
		note(step, ready ? Result.INFO : Result.FAIL, "parts shift-clicked in: " + preview);

		if (ready) {
			boolean clicked = context.tryClickScreenButton("container.oldrepublic.lightsaber_workbench.construct");
			note(step, clicked ? Result.INFO : Result.FAIL, "clicked the Construct button=" + clicked);
		}

		boolean built = waitClient(40, mc -> mc.player.containerMenu.slots.size() > SaberAssembly.SABER
				&& mc.player.containerMenu.slots.get(SaberAssembly.SABER).getItem().is(SaberItems.LIGHTSABER));
		shot("workbench_built");

		if (built) {
			context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, SaberAssembly.SABER, 0, ContainerInput.QUICK_MOVE, mc.player));
			context.waitTicks(5);
		}

		closeScreen();
		context.waitTicks(10);
		ForceData data = data();
		boolean ownsSaber = onServer(p -> {
			for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
				ItemStack stack = p.getInventory().getItem(slot);

				if (stack.is(SaberItems.LIGHTSABER) && SaberAssembly.ownsSaber(stack, p.getUUID())) {
					return true;
				}
			}

			return false;
		});
		check(step, built && ownsSaber && stage(data) == QuestStage.COMPLETE && data.hasFlag("resonance:first_saber"),
				"constructed=" + built + ", own saber in inventory=" + ownsSaber + ", stage=" + stage(data) + ", skill points=" + data.skillPoints
						+ ", level=" + data.level + ", resonance=" + data.resonance);

		if (!ownsSaber) {
			stuck(step, "no lightsaber in the inventory");
		}
	}

	/** Ignites the saber with G, guards against a poacher's blaster fire, then fights with it. */
	private void saberCombat() {
		String step = "16 saber";

		if (!select(SaberItems.LIGHTSABER)) {
			stuck(step, "could not take the saber in hand");
		}

		context.getInput().pressKey(InputConstants.KEY_U);
		boolean ignited = waitServer(10, p -> SaberComponents.isIgnited(p.getMainHandItem()));
		context.waitTicks(10);
		shot("saber_ignited");
		check(step + " ignite", ignited, "pressed G: ignited=" + ignited);

		if (!findLivePoachers(step)) {
			note(step + " guard", Result.FAIL, "no live poachers left to test the guard against");
			return;
		}

		guardTest(step + " guard");

		float before = damageTaken;
		int killsBefore = data().counter("poacher_kills");
		int kills = 0;

		for (int i = 0; i < 4; i++) {
			List<UUID> poachers = poachersNear(48);

			if (poachers.isEmpty()) {
				break;
			}

			if (fight(poachers.get(0), 900, false, step)) {
				kills++;
			}
		}

		check(step + " fight", kills > 0, kills + " poacher(s) cut down with the saber, poacher_kills " + killsBefore + " -> " + data().counter("poacher_kills")
				+ ", damage taken " + Math.round(damageTaken - before));
	}

	/**
	 * Holds the guard against a camp, turning to face whichever poacher shows its shot tell (as a
	 * player would; the guard covers a 100 degree front arc), then tries timed parries, recording
	 * what happens to the bolts and where every hit that got through came from.
	 */
	private void guardTest(String step) {
		List<UUID> poachers = poachersNear(64);

		if (poachers.isEmpty()) {
			note(step, Result.FAIL, "the poachers are gone");
			return;
		}

		UUID id = poachers.get(0);
		Target target = target(id);
		walkTo(target.pos(), 10, 300, true, null);

		Set<UUID> seen = new HashSet<>();
		Set<UUID> deflected = new HashSet<>();
		Set<UUID> returned = new HashSet<>();
		float healthBefore = damageTaken;
		float minStamina = Float.MAX_VALUE;
		boolean broke = false;

		UUID threat = id;
		int threatTicks = 0;
		HITS.clear();
		recordHits = true;
		context.getInput().holdKey(USE);

		for (int t = 0; t < 240; t++) {
			Target now = target(id);

			if (!now.alive()) {
				break;
			}

			trackBolts(seen, deflected, returned);
			SaberGuard.GuardSnapshot guard = onServer(SaberGuard::snapshot);

			if (guard != null) {
				minStamina = Math.min(minStamina, guard.stamina());
				broke |= guard.broken();
			}

			// Face the poacher about to fire, as a player would, and keep facing it while its bolt flies.
			UUID charging = chargingPoacher();

			if (charging != null) {
				threat = charging;
				threatTicks = 20;
			} else if (--threatTicks <= 0) {
				threat = id;
			}

			Target facing = target(threat);
			Target aim = facing.alive() ? facing : now;
			aimAt(aim.pos().add(0, aim.height() * 0.8, 0));
			tickMe(step);

			if (t == 60) {
				shot("saber_guard");
			}
		}

		context.getInput().releaseKey(USE);
		recordHits = false;
		List<String> hits;

		synchronized (HITS) {
			hits = new ArrayList<>(HITS);
		}

		String held = "held guard 240 ticks: bolts seen " + seen.size() + " (within 32 blocks, misses included), deflected " + deflected.size()
				+ " (of which aimed back at a poacher " + returned.size() + "), damage taken " + Math.round(damageTaken - healthBefore) + ", min stamina "
				+ (minStamina == Float.MAX_VALUE ? "n/a" : Math.round(minStamina)) + ", guard broke=" + broke + ", hits " + hits;
		check(step, deflected.size() > 0, held);

		// Timed parries: raise the guard just as a bolt is about to arrive.
		int attempts = 0;
		int parriesBefore = returned.size();
		seen.clear();

		for (int t = 0; t < 400 && attempts < 4; t++) {
			Target now = target(id);

			if (!now.alive()) {
				break;
			}

			boolean charging = onServer(p -> p.level().getEntity(id) instanceof CrystalPoacher poacher && poacher.isCharging());
			aimAt(now.pos().add(0, now.height() * 0.8, 0));

			if (charging) {
				attempts++;
				context.waitTicks(8);
				context.getInput().holdKey(USE);

				for (int i = 0; i < 16; i++) {
					trackBolts(seen, deflected, returned);
					tickMe(step);
				}

				context.getInput().releaseKey(USE);
				context.waitTicks(10);
			} else {
				tickMe(step);
			}
		}

		ForceData data = data();
		note(step + " parry", returned.size() > parriesBefore ? Result.PASS : Result.INFO, attempts + " timed guard raise(s), bolts sent straight back "
				+ (returned.size() - parriesBefore) + " (estimated from bolt direction), journal returned_fire="
				+ data.hasFlag(JournalEntries.entryFlag(JournalEntries.RETURNED_FIRE)));
	}

	private void finish() {
		String step = "17 journal and force screens";
		clearInterference(step);
		context.getInput().pressKey(InputConstants.KEY_J);
		boolean journal = waitClient(40, mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("JournalScreen"));
		context.waitTicks(10);
		shot("journal");
		closeScreen();
		context.getInput().pressKey(InputConstants.KEY_K);
		boolean force = waitClient(40, mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("ForceScreen"));
		context.waitTicks(10);
		shot("force_screen");
		closeScreen();

		ForceData data = data();
		List<String> missing = new ArrayList<>();

		for (String entry : JournalEntries.ENTRIES) {
			if (!data.hasFlag(JournalEntries.entryFlag(entry))) {
				missing.add(entry);
			}
		}

		check(step, journal && force, "J opened journal=" + journal + ", K opened force screen=" + force);
		note("18 final state", stage(data) == QuestStage.COMPLETE ? Result.PASS : Result.FAIL, "stage=" + stage(data) + ", level " + data.level + ", xp " + data.xp
				+ ", resonance " + data.resonance + ", skill points " + data.skillPoints + ", push mastery " + data.mastery("force_push")
				+ ", journal entries missing " + missing + ", total damage taken " + Math.round(damageTaken));
		server.runCommand("execute as @a run oldrepublic progress");
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Two steps (Milestone 2, spec 16.3)
	// ---------------------------------------------------------------------------------------------

	/** Chapter Two journal entries the Jedi run should unlock. */
	private static final List<String> CHAPTER_TWO_ENTRIES = List.of(JournalEntries.TWO_VOICES, JournalEntries.THE_CROSSING, JournalEntries.THE_WATCHWOMAN,
			JournalEntries.THE_REACH, JournalEntries.THE_STILL_BLADE, JournalEntries.SMOKE_ON_THE_RIDGE, JournalEntries.A_LIFE_SPARED, JournalEntries.CUT_LOOSE,
			JournalEntries.THE_LEDGER, JournalEntries.THE_WATCHWOMAN_LISTENS, JournalEntries.PADAWAN_OF_THE_WATCH);

	/** Non-natural blocks in the camp footprint before the camp was placed, or -1 when not counted. */
	private int campBaseline = -1;

	/**
	 * Runs one Chapter Two step through {@link #step} (one retry after a death). A step that gets
	 * stuck or throws is recorded and the next step still runs: each step checks the story state
	 * first and skips what is already done.
	 */
	private void chapterTwo(String name, Runnable body) {
		try {
			step(name, body);
		} catch (Stuck e) {
			note(name, Result.INFO, "Chapter Two goes on after: " + e.getMessage());
		} catch (RuntimeException e) {
			note(name, Result.FAIL, "error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			LOG.error("PLAYTHROUGH-LOG: Chapter Two step failed", e);
			shot("error");
		} finally {
			releaseAll();
			closeScreen();
		}
	}

	/** After Chapter One: sleeps through a night, which makes the Two Voices dream due. */
	private void twoVoices() {
		String step = "19 two voices";
		boolean waiting = waitServer(60, p -> Story.get(p).crossing() != CrossingStage.NOT_STARTED);
		StoryData start = story();

		if (start.crossing().ordinal() >= CrossingStage.VOICES.ordinal()) {
			note(step, Result.INFO, "the dream is already behind the player: " + storyLine());
			return;
		}

		note(step, waiting ? Result.INFO : Result.FAIL, "after Chapter One: " + storyLine() + ", quest stage " + stage(data()));
		quietPlace(step);
		clearStrays(step);
		boolean voices = false;

		for (int attempt = 1; attempt <= 2 && !voices; attempt++) {
			shortcut("gave a white bed and set the time to night for the Two Voices sleep (the first bed stayed where Chapter One began)");
			server.runCommand("give @a minecraft:white_bed");
			server.runCommand("time set 13000");
			context.waitTicks(5);
			BlockPos bed = placeBlock(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("white_bed")), state -> state.is(BlockTags.BEDS), true);

			if (bed == null) {
				note(step, Result.FAIL, "attempt " + attempt + ": could not place the bed by right-clicking the ground");
				Vec3 spot = onServer(p -> findFlatSpot(p.level(), p.blockPosition().getX() + 12, p.blockPosition().getZ(), 40));

				if (spot != null) {
					shortcut("teleported to another flat spot for the Two Voices bed");
					teleport(spot, 0f);
				}

				continue;
			}

			if (!sleepIn(step, attempt, bed)) {
				continue;
			}

			context.waitTicks(40);
			shot("two_voices_sleep");
			boolean woke = waitServer(300, p -> !p.isSleeping());
			boolean due = waitServer(40, p -> Story.get(p).hasFlag(StoryRules.VOICES_DUE) || Story.get(p).crossing() != CrossingStage.WAITING);
			context.waitTicks(40);
			shot("two_voices_dream");
			voices = waitServer(400, p -> Story.get(p).crossing().ordinal() >= CrossingStage.VOICES.ordinal());
			note(step, voices ? Result.INFO : Result.FAIL, "attempt " + attempt + ": woke=" + woke + ", dream due after waking=" + due + ", now " + storyLine());
		}

		if (!voices) {
			shortcut("started the Two Voices dream with /oldrepublic story dream (sleeping did not bring it)");
			server.runCommand("oldrepublic story dream @p");
			voices = waitServer(400, p -> Story.get(p).crossing().ordinal() >= CrossingStage.VOICES.ordinal());
		}

		StoryData d = story();
		check(step, voices && d.meeting != null && data().hasFlag(JournalEntries.entryFlag(JournalEntries.TWO_VOICES)), storyLine() + ", meeting point "
				+ shortPos(d.meeting) + ", journal two_voices=" + data().hasFlag(JournalEntries.entryFlag(JournalEntries.TWO_VOICES)));
	}

	/** Holds the journal and walks toward the meeting point the voices pull to. */
	private void followVoices() {
		String step = "20 follow the voices";
		StoryData d = story();

		if (d.crossing().ordinal() < CrossingStage.VOICES.ordinal()) {
			note(step, Result.FAIL, "the voices never began: " + storyLine());
			shortcut("moved to the Crossing with /oldrepublic story meet (the voices never began)");
			server.runCommand("oldrepublic story meet @p");
			waitForChunks(step);
			return;
		}

		if (d.crossing() != CrossingStage.VOICES || d.meeting == null) {
			note(step, Result.INFO, "nothing to follow: " + storyLine());
			return;
		}

		Vec3 meeting = Vec3.atBottomCenterOf(d.meeting);

		if (!select(QuestItems.WANDERERS_JOURNAL)) {
			note(step, Result.FAIL, "journal not found in the inventory");
		}

		Vec3 from = here();
		double distance = flat(meeting, from);
		aimAt(meeting.add(0, from.y - meeting.y + 1.5, 0));
		context.waitTicks(40);
		shot("voices_calling");
		boolean arrived = walkTo(meeting, 10, 1800, true, () -> story().crossing() != CrossingStage.VOICES);
		arrived = waitServer(30, p -> Story.get(p).crossing() != CrossingStage.VOICES);

		if (!arrived) {
			Vec3 now = here();
			Vec3 spot = safeSpotNear(meeting.add(away(meeting, now).scale(12)));
			shortcut("teleported the rest of the way to the meeting point after the walk stopped (" + step + ")");
			note(step, Result.INFO, "walk stopped " + Math.round(flat(meeting, now)) + " blocks from the meeting point: " + walkStop);
			teleport(spot, yawToward(spot, meeting));
			arrived = waitServer(60, p -> Story.get(p).crossing() != CrossingStage.VOICES);
		}

		UUID jedi = mentorId("jedi");
		UUID sith = mentorId("sith");
		boolean both = jedi != null && sith != null && target(jedi).alive() && target(sith).alive();
		Vec3 at = here();
		aimAt(meeting.add(0, at.y - meeting.y + 1.2, 0));
		context.waitTicks(30);
		shot("crossing_mentors");
		check(step, arrived && story().crossing() == CrossingStage.MEETING && both, Math.round(distance) + " blocks to the meeting point at "
				+ d.meeting.toShortString() + ", walked there=" + (walkStop.isEmpty() || arrived) + ", " + storyLine() + ", both mentors present=" + both
				+ ", journal the_crossing=" + data().hasFlag(JournalEntries.entryFlag(JournalEntries.THE_CROSSING)));
	}

	/**
	 * Talks to the Watchwoman: asks about her creed and answers with mercy (an alignment shift),
	 * then joins the Jedi with the number keys and confirms.
	 */
	private void crossingChoice() {
		String step = "21 crossing";
		StoryData d = story();

		if (d.crossing() == CrossingStage.DECIDED) {
			note(step, Result.INFO, "the Crossing is already decided: " + storyLine());
			return;
		}

		if (d.crossing() != CrossingStage.MEETING) {
			shortcut("moved to the Crossing with /oldrepublic story meet (" + step + ")");
			server.runCommand("oldrepublic story meet @p");
			waitForChunks(step);
			waitServer(40, p -> Story.get(p).crossing() == CrossingStage.MEETING);
			d = story();
		}

		waitServer(120, p -> Story.get(p).jediMentor != null && p.level().getEntity(Story.get(p).jediMentor) instanceof MentorEntity);
		UUID jedi = mentorId("jedi");
		UUID sith = mentorId("sith");
		BlockPos meeting = story().meeting;

		if (jedi == null || !target(jedi).alive() || meeting == null) {
			note(step, Result.FAIL, "the Watchwoman is not at the Crossing: " + storyLine() + ", mentor id " + jedi);
			shortcut("chose the Jedi with /oldrepublic story choose (no mentor to talk to)");
			server.runCommand("oldrepublic story choose @p jedi");
			return;
		}

		// Between the two of them first, then up to her.
		walkTo(Vec3.atBottomCenterOf(meeting), 1.5, 300, false, null);
		campBaseline = campBlocks(meeting);
		int alignmentBefore = alignment();
		String before = standing();
		boolean creed = talkTo(step, jedi);

		if (creed) {
			context.waitTicks(10);
			shot("crossing_dialogue");
			creed = reply(step, "dialogue.oldrepublic.crossing_jedi.opt.creed") && reply(step, "dialogue.oldrepublic.crossing_jedi.opt.creed_mercy");
		}

		context.waitTicks(10);
		int alignmentAfter = alignment();
		check(step + " creed answer", creed && alignmentAfter > alignmentBefore, "asked about her creed and answered with mercy: alignment " + alignmentBefore
				+ " -> " + alignmentAfter + ", trust jedi " + onServer(p -> Identity.trust(p, "jedi")));

		if (creed) {
			shot("crossing_creed_answer");
		}

		boolean joined = creed && reply(step, "dialogue.oldrepublic.common.opt.back") && joinJedi(step);

		if (!joined && story().crossing() != CrossingStage.DECIDED) {
			// Start again from her greeting.
			closeScreen();
			joined = talkTo(step, jedi) && joinJedi(step);
		}

		boolean decided = waitServer(40, p -> Story.get(p).crossing() == CrossingStage.DECIDED);

		if (!decided) {
			shortcut("chose the Jedi with /oldrepublic story choose (the dialogue choice did not go through)");
			server.runCommand("oldrepublic story choose @p jedi");
			decided = waitServer(40, p -> Story.get(p).crossing() == CrossingStage.DECIDED);
		}

		closeScreen();
		context.waitTicks(20);
		boolean departing = sith == null || onServer(p -> !(p.level().getEntity(sith) instanceof MentorEntity m) || m.isDeparting() || !m.isAlive());
		shot("crossing_chosen");
		StoryData after = story();
		ForceData f = data();
		check(step, joined && decided && after.storyPath() == com.oldrepublic.identity.Path.JEDI && after.stage() == PathStage.LESSON_ONE,
				"joined with number keys=" + joined + ", " + storyLine() + "; before " + before + "; now " + standing() + ", Overseer leaving=" + departing
						+ ", xp " + f.xp + " (level " + f.level + "), resonance crossing=" + f.hasFlag("resonance:crossing") + ", journal the_watchwoman="
						+ f.hasFlag(JournalEntries.entryFlag(JournalEntries.THE_WATCHWOMAN)));
	}

	/** From her greeting: "I will learn from you", then Yes on the confirmation. */
	private boolean joinJedi(String step) {
		if (!reply(step, "dialogue.oldrepublic.crossing_jedi.opt.join")) {
			return false;
		}

		context.waitTicks(10);
		shot("crossing_confirm");
		return reply(step, "dialogue.oldrepublic.common.opt.yes");
	}

	/** Steps off the meeting point so the Watchwoman's camp is built there, and looks at it. */
	private void mentorCamp() {
		String step = "22 mentor camp";
		StoryData d = story();
		BlockPos camp = d.camp;

		if (camp == null) {
			note(step, Result.FAIL, "no camp: " + storyLine());
			return;
		}

		Vec3 centre = Vec3.atBottomCenterOf(camp);
		Vec3 out = centre.add(away(centre, here()).scale(10));
		walkTo(out, 1, 200, false, () -> !story().hasFlag(StoryRules.CAMP_PENDING));
		boolean placed = waitServer(60, p -> !Story.get(p).hasFlag(StoryRules.CAMP_PENDING));

		if (!placed) {
			Vec3 spot = safeSpotNear(centre.add(away(centre, here()).scale(12)));
			shortcut("teleported 12 blocks off the camp ground so the camp is placed (" + step + ")");
			teleport(spot, yawToward(spot, centre));
			placed = waitServer(60, p -> !Story.get(p).hasFlag(StoryRules.CAMP_PENDING));
		}

		int built = campBlocks(camp);
		boolean structures = onServer(p -> StoryService.balance().placeStructures);
		Vec3 me = here();
		aimAt(centre.add(0, me.y - centre.y + 0.5, 0));
		context.waitTicks(30);
		shot("jedi_camp");
		UUID jedi = story().jediMentor;
		String mentor = jedi == null ? "no mentor" : String.format(Locale.ROOT, "mentor %.1f blocks from the camp", target(jedi).alive()
				? flat(target(jedi).pos(), centre) : -1.0);
		check(step, placed && (!structures || built > Math.max(0, campBaseline)), "camp at " + camp.toShortString() + ", placed=" + placed
				+ ", non-natural blocks in the footprint " + campBaseline + " -> " + built + " (structures " + (structures ? "on" : "off") + "), " + mentor);
	}

	/** Lesson one, "The Reach": she teaches Force Pull; five husks are pulled in with its key. */
	private void lessonOne() {
		String step = "23 lesson one";
		StoryData d = story();
		UUID jedi = d.jediMentor;

		if (d.stage() != PathStage.LESSON_ONE || jedi == null) {
			note(step, d.stage().ordinal() > PathStage.LESSON_ONE.ordinal() ? Result.INFO : Result.FAIL, "not on lesson one: " + storyLine());
			return;
		}

		if (!d.hasFlag(StoryRules.LESSON_ONE_BEGUN)) {
			talkAndChoose(step, jedi, "lesson_one_dialogue", "dialogue.oldrepublic.mentor_jedi.opt.teach");
		}

		boolean begun = waitServer(20, p -> Story.get(p).hasFlag(StoryRules.LESSON_ONE_BEGUN));
		int slot = slotOf("force_pull");
		check(step + " teach", begun && slot >= 0, "\"Teach me\" chosen: lesson begun=" + begun + ", force_pull in slot " + (slot + 1));

		if (slot < 0) {
			shortcutLesson(step);
			return;
		}

		igniteSaber();
		clearStrays(step);
		Vec3 me = here();
		Vec3 out = away(Vec3.atBottomCenterOf(d.camp == null ? BlockPos.containing(me) : d.camp), me);
		Vec3 side = new Vec3(-out.z, 0, out.x);
		shortcut("summoned 5 slowed husks 9 blocks away as Force Pull targets (lesson one)");
		List<UUID> husks = new ArrayList<>(summonHusks(me.add(out.scale(9)), side, 5));
		note(step, Result.INFO, husks.size() + " husk(s) summoned, pulling with the slot " + (slot + 1) + " key");
		int casts = 0;
		int approaches = 0;

		while (casts < 10 && story().stage() == PathStage.LESSON_ONE) {
			waitServer(300, p -> Progression.get(p).cooldown("force_pull") == 0 && Progression.get(p).energy >= 16f);
			UUID pick = null;
			Target target = null;
			Vec3 now = here();

			for (UUID id : husks) {
				Target t = target(id);

				if (t.alive() && flat(t.pos(), now) > 4 && (target == null || t.pos().distanceTo(now) < target.pos().distanceTo(now))) {
					pick = id;
					target = t;
				}
			}

			if (pick == null) {
				if (casts >= 6) {
					break;
				}

				shortcut("summoned 3 more slowed husks as Force Pull targets (lesson one)");
				husks.addAll(summonHusks(now.add(out.scale(9)), side, 3));
				continue;
			}

			Vec3 aim = target.pos().add(0, target.height() * 0.6, 0);

			if ((target.pos().distanceTo(now) > HUSK_PULL_DISTANCE || !sightTo(aim)) && approaches < 6) {
				// Out of the Pull's reach or behind the ground: close in on it first (a cast would fizzle).
				approaches++;

				if (!walkTo(target.pos(), 5.0, 200, false, null)) {
					note(step, Result.INFO, "walk towards a husk " + Math.round(target.pos().distanceTo(now)) + " blocks away stopped: " + walkStop);
				}

				continue;
			}

			int before = story().counter(StoryRules.PULL_TARGETS);
			double startDistance = target.pos().distanceTo(now);
			aimAt(aim);
			context.waitTick();
			// A real tap lasts a tick or two (pressKey presses and releases within one frame).
			holdSlot(slot);
			context.waitTicks(2);
			releaseSlot(slot);

			for (int i = 0; i < 20; i++) {
				keepAlive(step);
				tickMe(step);

				if (casts == 0 && i == 6) {
					shot("force_pull");
				}
			}

			casts++;
			Target end = target(pick);
			note(step, Result.INFO, String.format(Locale.ROOT, "cast %d: husk %.1f -> %s blocks away, lesson progress %d -> %d, energy %d", casts, startDistance,
					end.alive() ? String.format(Locale.ROOT, "%.1f", end.pos().distanceTo(here())) : "gone", before, story().counter(StoryRules.PULL_TARGETS),
					Math.round(data().energy)));
			fight(pick, 300, false, step);
		}

		int left = discardAll(husks);

		if (left > 0) {
			shortcut("removed " + left + " lesson husk(s) left over (" + step + ")");
		}

		boolean done = waitServer(20, p -> Story.get(p).stage() != PathStage.LESSON_ONE);
		check(step, done, casts + " pull(s) with the slot key, " + storyLine() + ", pulls counted " + story().counter(StoryRules.PULL_TARGETS) + ", " + standing()
				+ ", pull mastery " + data().mastery("force_pull") + ", journal the_reach=" + data().hasFlag(JournalEntries.entryFlag(JournalEntries.THE_REACH)));

		if (!done) {
			shortcutLesson(step);
		}
	}

	/** Opens the Force screen with K and learns a node (Force Jump) with skill points. */
	private void learnNode() {
		String step = "24 skill screen";
		String node = "force_jump";

		if (onServer(p -> SkillService.data(p).owns(node))) {
			note(step, Result.INFO, "Force Jump is owned already; learning Deep Reserves instead");
		}

		String chosen = onServer(p -> SkillService.data(p).owns(node)) ? SkillIds.DEEP_RESERVES : node;
		SkillRules.LearnCheck check = learnCheck(chosen);
		ForceData f = data();
		note(step, Result.INFO, "before: level " + f.level + ", skill points " + f.skillPoints + ", " + chosen + " check " + check.result() + " (cost " + check.cost() + ")");

		if (check.result() == SkillRules.LearnResult.LEVEL_TOO_LOW && check.node() != null) {
			int level = check.node().levelFor(check.nextRank());
			shortcut("set the level to " + level + " with /oldrepublic setlevel so " + chosen + " can be learned (" + step + ")");
			server.runCommand("execute as @p run oldrepublic setlevel " + level);
			context.waitTicks(3);
			check = learnCheck(chosen);
		}

		if (check.result() == SkillRules.LearnResult.NOT_ENOUGH_POINTS) {
			int missing = Math.max(1, check.cost() - data().skillPoints);
			shortcut("granted " + missing + " skill point(s) with /oldrepublic grantskillpoints so " + chosen + " can be learned (" + step + ")");
			server.runCommand("execute as @p run oldrepublic grantskillpoints " + missing);
			context.waitTicks(3);
			check = learnCheck(chosen);
		}

		if (!check.ok()) {
			note(step, Result.FAIL, chosen + " cannot be learned: " + check.result());
			return;
		}

		int points = data().skillPoints;
		closeScreen();
		context.getInput().pressKey(ForceKeys.FORCE_SCREEN);
		boolean open = waitClient(40, mc -> mc.gui.screen() instanceof ForceScreen);
		context.waitTicks(10);
		shot("force_screen_chapter_two");
		shortcut("selected the node in the open Force screen with ForceScreen.openOn (the bot does not locate node sockets on screen)");
		context.runOnClient(mc -> ForceScreen.openOn(mc, SkillIds.TREE_UNIVERSAL, chosen));
		context.waitTicks(10);
		shot("force_screen_node");
		boolean clicked = clickButton("screen.oldrepublic.skills.learn") || clickButton("screen.oldrepublic.skills.switch");
		boolean learned = waitServer(40, p -> SkillService.data(p).owns(chosen));
		context.waitTicks(10);
		shot("force_screen_learned");
		closeScreen();
		int after = data().skillPoints;
		int slot = slotOf(chosen);
		check(step, open && clicked && learned && after < points, "K opened the Force screen=" + open + ", Learn button pressed=" + clicked + ", " + chosen
				+ " owned=" + learned + ", skill points " + points + " -> " + after + (slot >= 0 ? ", in slot " + (slot + 1) : ""));
	}

	/** Casts Force Jump with its slot key: hold to gather, release to leap. */
	private void forceJump() {
		String step = "25 force jump";
		int slot = slotOf("force_jump");

		if (slot < 0) {
			note(step, Result.FAIL, "Force Jump is in no slot: " + onServer(p -> SkillService.data(p).slots.toString()));
			return;
		}

		closeScreen();
		clearStrays(step);
		waitServer(300, p -> Progression.get(p).cooldown("force_jump") == 0 && Progression.get(p).energy >= 24f);
		BlockPos camp = story().camp;

		if (!waitClient(40, mc -> mc.player.onGround() && !mc.player.isInWater()) && camp != null) {
			// The lesson can end with the bot in a pond or a ravine near the camp, where the leap never gathers.
			Vec3 spot = safeSpotNear(Vec3.atBottomCenterOf(camp));
			shortcut("moved from " + BlockPos.containing(here()).toShortString() + " (not on dry ground) to " + BlockPos.containing(spot).toShortString()
					+ " beside the mentor camp for the Force Jump");
			teleport(spot, 0f);
			waitClient(40, mc -> mc.player.onGround());
		}

		Vec3 start = here();

		if (camp != null && flat(Vec3.atBottomCenterOf(camp), start) > 4) {
			// Leap toward the camp: known flat ground.
			context.getInput().lookAt(yawToward(start, Vec3.atBottomCenterOf(camp)), 0f);
		} else {
			context.getInput().lookAt(context.computeOnClient(mc -> mc.player.getYRot()), 0f);
		}

		double top = start.y;
		double furthest = 0;

		try {
			holdSlot(slot);
			context.waitTicks(12);
			shot("force_jump_gather");
			context.waitTicks(6);
		} finally {
			releaseSlot(slot);
		}

		for (int t = 0; t < 50; t++) {
			Vec3 now = here();
			top = Math.max(top, now.y);
			furthest = Math.max(furthest, flat(now, start));

			if (t == 6) {
				shot("force_jump_air");
			}

			tickMe(step);
		}

		int cooldown = data().cooldown("force_jump");
		check(step, top - start.y > 2.0 && cooldown > 0, String.format(Locale.ROOT, "held slot %d 18 ticks and released: rose %.1f blocks, moved %.1f blocks, "
				+ "cooldown %d, health %.1f, energy %d", slot + 1, top - start.y, furthest, cooldown, context.computeOnClient(mc -> mc.player.getHealth()),
				Math.round(data().energy)));
	}

	/** Lesson two, "The Still Blade": a training spar; the guard is raised just before each telegraphed strike. */
	private void lessonTwo() {
		String step = "26 lesson two";
		StoryData d = story();
		UUID jedi = d.jediMentor;

		if (d.stage() != PathStage.LESSON_TWO || jedi == null) {
			note(step, d.stage().ordinal() > PathStage.LESSON_TWO.ordinal() ? Result.INFO : Result.FAIL, "not on lesson two: " + storyLine());
			return;
		}

		String blocker = onServer(p -> Dialogues.failReason(Map.of("story:lesson_two", ""), p, null));
		note(step, Result.INFO, "before: " + standing() + "; \"I am ready\" " + (blocker == null ? "available" : "blocked: " + blocker));
		boolean asked = talkAndChoose(step, jedi, "lesson_two_dialogue", "dialogue.oldrepublic.mentor_jedi.opt.ready") == 1;
		boolean sparring = asked && waitServer(40, p -> Spar.inSpar(p));

		if (!sparring) {
			note(step, Result.FAIL, "the training spar did not start (reply made=" + asked + ", condition " + blocker + ")");
			shortcutLesson(step);
			return;
		}

		igniteSaber();
		int windup = onServer(p -> p.level().getEntity(jedi) instanceof MentorEntity m ? m.profile().windupTicks() : 14);
		float parryTicks = onServer(p -> SaberPassives.serverStats(p, p.getMainHandItem()).parryTicks());
		int baseLead = Math.max(1, Math.round(parryTicks / 2f));
		int[] leads = { 0, -1, 1, -2, 2 };
		int leadIndex = 0;
		int attempts = 0;
		int parried = 0;
		GuardState previous = GuardState.READY;
		note(step, Result.INFO, "spar started: strike wind-up " + windup + " ticks, parry window " + parryTicks + " ticks");

		for (int t = 0; t < 1400 && attempts < 12 && onServer(p -> Spar.inSpar(p)); t++) {
			Target master = target(jedi);

			if (!master.alive()) {
				break;
			}

			faceOver(master);
			GuardState now = guardOf(jedi);

			if (now == GuardState.ATTACKING && previous != GuardState.ATTACKING) {
				attempts++;
				int lead = baseLead + leads[leadIndex % leads.length];
				int before = onServer(p -> Spar.progress(p));

				for (int i = 0; i < Math.max(0, windup - lead - 1); i++) {
					faceOver(target(jedi));
					tickMe(step);
				}

				context.getInput().holdKey(USE);

				try {
					for (int i = 0; i < 16; i++) {
						faceOver(target(jedi));
						keepAlive(step);
						tickMe(step);

						if (attempts == 1 && i == 3) {
							shot("lesson_two_parry");
						}
					}
				} finally {
					context.getInput().releaseKey(USE);
				}

				boolean still = onServer(p -> Spar.inSpar(p));
				int after = still ? onServer(p -> Spar.progress(p)) : before + 1;

				if (after > before) {
					parried++;
				} else {
					leadIndex++;
				}

				note(step, Result.INFO, "strike " + attempts + ": guard raised " + lead + " tick(s) before the swing, parries " + before + " -> " + after
						+ (still ? "" : " (spar over)"));
				previous = still ? guardOf(jedi) : GuardState.READY;
				continue;
			}

			previous = now;
			keepAlive(step);
			tickMe(step);
		}

		boolean done = waitServer(60, p -> Story.get(p).stage() != PathStage.LESSON_TWO);
		check(step, done, attempts + " strike(s) met with a timed guard, " + parried + " parried, " + storyLine() + ", soresu owned="
				+ onServer(p -> SkillService.data(p).owns("form_soresu")) + ", " + standing() + ", journal the_still_blade="
				+ data().hasFlag(JournalEntries.entryFlag(JournalEntries.THE_STILL_BLADE)));

		if (!done) {
			endSpar(step);
			shortcutLesson(step);
		}
	}

	/** Follows the drifting ash with the journal to the raider camp of Smoke on the Ridge. */
	private void travelToRaiders() {
		String step = "27 smoke on the ridge";
		StoryData d = story();

		if (d.stage().ordinal() < PathStage.RECKONING.ordinal()) {
			note(step, Result.FAIL, "Smoke on the Ridge has not begun: " + storyLine());
			return;
		}

		if (d.encounterPos == null) {
			waitServer(40, p -> Story.get(p).encounterPos != null);
		}

		if (story().encounterPos == null) {
			shortcut("started Smoke on the Ridge with /oldrepublic story reckoning start (no raider camp site was chosen)");
			server.runCommand("oldrepublic story reckoning @p start");
			context.waitTicks(5);
		}

		BlockPos site = story().encounterPos;

		if (site == null || story().encounterState() == EncounterState.DONE) {
			note(step, site == null ? Result.FAIL : Result.INFO, "no raider camp to travel to: " + storyLine());
			return;
		}

		select(QuestItems.WANDERERS_JOURNAL);
		Vec3 camp = Vec3.atBottomCenterOf(site);
		Vec3 from = here();
		note(step, Result.INFO, "raider camp at " + site.toShortString() + ", " + Math.round(flat(camp, from)) + " blocks away"
				+ (d.camp == null ? "" : ", " + Math.round(flat(camp, Vec3.atBottomCenterOf(d.camp))) + " blocks from the mentor camp"));
		aimAt(camp.add(0, from.y - camp.y + 1.5, 0));
		context.waitTicks(40);
		shot("smoke_calling");
		boolean near = walkTo(camp, 36, 1800, true, () -> story().encounterState() != EncounterState.PLACED);

		if (!near && story().encounterState() == EncounterState.PLACED) {
			Vec3 now = here();
			Vec3 spot = safeSpotNear(camp.add(away(camp, now).scale(34)));
			shortcut("teleported toward the raider camp after the walk stopped (" + step + ")");
			note(step, Result.INFO, "walk stopped " + Math.round(flat(camp, now)) + " blocks from the camp: " + walkStop);
			teleport(spot, yawToward(spot, camp));
		}

		boolean spawned = waitServer(100, p -> Story.get(p).encounterState() != EncounterState.PLACED);

		if (!spawned) {
			walkTo(camp, 20, 400, true, () -> story().encounterState() != EncounterState.PLACED);
			spawned = waitServer(60, p -> Story.get(p).encounterState() != EncounterState.PLACED);
		}

		Vec3 at = here();
		aimAt(camp.add(0, at.y - camp.y + 1.0, 0));
		context.waitTicks(40);
		shot("raider_camp");
		StoryData now = story();
		check(step, spawned && now.captainId != null && !crewNear(site).isEmpty(), Math.round(flat(camp, here())) + " blocks from the camp, " + storyLine()
				+ ", crew " + crewNear(site).size() + ", captives " + captivesNear(site).size() + ", captain " + (now.captainId != null) + ", journal smoke_on_the_ridge="
				+ data().hasFlag(JournalEntries.entryFlag(JournalEntries.SMOKE_ON_THE_RIDGE)));
	}

	/** Fights the crew with the lit saber, opening with a heavy attack (Sneak + Attack). */
	private void raiderFight() {
		String step = "28 raider fight";
		StoryData d = story();
		BlockPos site = d.encounterPos;

		if (site == null || d.encounterState() != EncounterState.SPAWNED) {
			note(step, Result.FAIL, "no live raider camp: " + storyLine());
			return;
		}

		returnTo(step, Vec3.atBottomCenterOf(site), 40);
		igniteSaber();
		float before = damageTaken;
		List<UUID> crew = crewNear(site);
		int total = crew.size();

		if (!crew.isEmpty()) {
			heavyAttack(step, crew.get(0));
		}

		note(step, Result.INFO, "lunge (Sprint + Attack) not tried: saber_lunge owned=" + onServer(p -> SkillService.data(p).owns("saber_lunge")));
		int kills = 0;
		Set<UUID> failed = new HashSet<>();

		for (int fights = 0; fights < total + 3; fights++) {
			List<UUID> left = crewNear(site).stream().filter(x -> !failed.contains(x)).toList();

			if (left.isEmpty()) {
				break;
			}

			UUID id = left.get(0);
			String kind = onServer(p -> p.level().getEntity(id) == null ? "?" : BuiltInRegistries.ENTITY_TYPE.getKey(p.level().getEntity(id).getType()).getPath());

			if (fight(id, 900, false, step)) {
				kills++;

				if (kills == 1) {
					shot("raider_down");
				}
			} else {
				failed.add(id);
				note(step, Result.INFO, "could not finish a " + kind + " within 900 ticks");
			}
		}

		List<UUID> rest = crewNear(site);

		if (!rest.isEmpty()) {
			shortcut("killed " + rest.size() + " raider(s) the bot could not finish (" + step + ")");
			onServer(p -> {
				for (UUID id : rest) {
					if (p.level().getEntity(id) instanceof LivingEntity living && living.isAlive()) {
						living.kill(p.level());
					}
				}

				return null;
			});
		}

		boolean down = waitServer(200, p -> StoryRules.crewDown(Story.get(p)));
		StoryData after = story();
		check(step, kills > 0 && down, kills + "/" + total + " crew cut down with the saber, crew down=" + down + " (" + after.counter(StoryRules.CREW_DOWN) + "/"
				+ after.counter(StoryRules.CREW_TOTAL) + "), damage taken " + Math.round(damageTaken - before));
	}

	/** Brings Captain Hessa Dray to yield, then chooses Spare in her dialogue. */
	private void captainYield() {
		String step = "29 captain";
		StoryData d = story();
		UUID captain = d.captainId;

		if (d.captainFate().resolved() || captain == null) {
			note(step, captain == null ? Result.FAIL : Result.INFO, "nothing to decide: " + storyLine());
			return;
		}

		if (d.encounterPos != null) {
			returnTo(step, Vec3.atBottomCenterOf(d.encounterPos), 40);
		}

		igniteSaber();
		float before = damageTaken;

		if (!yielded(captain) && target(captain).alive()) {
			fight(captain, 1200, false, step, () -> yielded(captain));
		}

		if (!target(captain).alive()) {
			check(step, false, "the Captain died instead of yielding: " + storyLine());
			return;
		}

		if (!yielded(captain)) {
			shortcut("made the Captain yield with Yielding.forceYield (the fight did not bring her to 25%)");
			onServer(p -> p.level().getEntity(captain) instanceof LivingEntity living && Yielding.forceYield(living));
		}

		context.waitTicks(10);
		shot("captain_yield");
		int alignmentBefore = alignment();
		int replies = talkAndChoose(step, captain, "captain_dialogue", "dialogue.oldrepublic.yield.opt.spare", "dialogue.oldrepublic.common.opt.yes");
		boolean spared = waitServer(40, p -> Story.get(p).captainFate() == CaptainFate.SPARED);
		context.waitTicks(20);
		shot("captain_spared");
		check(step, spared && alignment() > alignmentBefore && count(QuestItems.POACHERS_LEDGER) > 0, "fought to her yield (damage taken "
				+ Math.round(damageTaken - before) + "), replies made " + replies + "/2, " + storyLine() + ", alignment " + alignmentBefore + " -> " + alignment()
				+ ", ledger carried=" + count(QuestItems.POACHERS_LEDGER) + ", journal a_life_spared=" + data().hasFlag(JournalEntries.entryFlag(JournalEntries.A_LIFE_SPARED)));
	}

	/** Frees the captives through one captive's dialogue: "Cut them loose", Yes. */
	private void freeCaptives() {
		String step = "30 captives";
		StoryData d = story();
		BlockPos site = d.encounterPos;

		if (site == null || d.captivesFate() != CaptivesFate.NONE) {
			note(step, site == null ? Result.FAIL : Result.INFO, "nothing to decide: " + storyLine());
			return;
		}

		List<UUID> captives = captivesNear(site);

		if (captives.isEmpty()) {
			note(step, Result.FAIL, "no bound captives within 64 blocks of the camp");
			return;
		}

		int alignmentBefore = alignment();
		int frontierBefore = onServer(p -> Identity.reputation(p, Faction.FRONTIER));
		int replies = talkAndChoose(step, captives.get(0), "captives_dialogue", "dialogue.oldrepublic.captives.opt.free", "dialogue.oldrepublic.common.opt.yes");
		boolean freed = waitServer(40, p -> Story.get(p).captivesFate() == CaptivesFate.FREED);
		context.waitTicks(30);
		shot("captives_freed");
		int frontier = onServer(p -> Identity.reputation(p, Faction.FRONTIER));
		check(step, freed && frontier > frontierBefore, "replies made " + replies + "/2, " + storyLine() + ", alignment " + alignmentBefore + " -> " + alignment()
				+ ", Frontier reputation " + frontierBefore + " -> " + frontier + ", journal cut_loose=" + data().hasFlag(JournalEntries.entryFlag(JournalEntries.CUT_LOOSE)));
	}

	/** Smoke on the Ridge completes once the crew, the Captain and the captives are settled. */
	private void reckoningComplete() {
		String step = "31 reckoning complete";
		int levelBefore = data().level;
		boolean done = waitServer(200, p -> Story.get(p).encounterState() == EncounterState.DONE);
		StoryData d = story();

		if (!done) {
			note(step, Result.FAIL, "not complete after 200 ticks: " + storyLine() + ", crew down " + StoryRules.crewDown(d));
			shortcut("completed Smoke on the Ridge with /oldrepublic story reckoning complete (open fates settled by the command)");
			server.runCommand("oldrepublic story reckoning @p complete");
			context.waitTicks(5);
			return;
		}

		waitServer(40, p -> Story.get(p).hasFlag(StoryRules.LEDGER_READ));
		ForceData f = data();
		check(step, story().stage() == PathStage.DEBRIEF && f.hasFlag("resonance:reckoning"), storyLine() + ", level " + levelBefore + " -> " + f.level
				+ ", skill points " + f.skillPoints + ", resonance reckoning=" + f.hasFlag("resonance:reckoning") + ", journal the_ledger="
				+ f.hasFlag(JournalEntries.entryFlag(JournalEntries.THE_LEDGER)));
	}

	/** Reports back to the Watchwoman, then takes the Trial of the Blade (a spar she yields at 35%). */
	private void debriefAndTrial() {
		String step = "32 debrief and trial";
		StoryData d = story();
		UUID jedi = d.jediMentor;

		if (jedi == null || d.camp == null) {
			note(step, Result.FAIL, "no mentor or camp: " + storyLine());
			return;
		}

		Vec3 camp = Vec3.atBottomCenterOf(d.camp);

		if (flat(camp, here()) > 16) {
			select(QuestItems.WANDERERS_JOURNAL);
			boolean back = walkTo(camp, 6, 1800, true, null);

			if (!back) {
				Vec3 spot = safeSpotNear(camp.add(away(camp, here()).scale(8)));
				shortcut("teleported back to the mentor camp after the walk stopped (" + step + ")");
				note(step, Result.INFO, "walk back stopped " + Math.round(flat(camp, here())) + " blocks from the camp: " + walkStop);
				teleport(spot, yawToward(spot, camp));
			}
		}

		if (d.stage() == PathStage.DEBRIEF) {
			String before = standing();
			talkAndChoose(step, jedi, "debrief_dialogue", "dialogue.oldrepublic.mentor_jedi.opt.report");
			boolean heard = waitServer(40, p -> Story.get(p).stage() != PathStage.DEBRIEF);
			check(step + " debrief", heard, "reported: " + storyLine() + "; before " + before + "; after " + standing() + ", journal the_watchwoman_listens="
					+ data().hasFlag(JournalEntries.entryFlag(JournalEntries.THE_WATCHWOMAN_LISTENS)));

			if (!heard) {
				shortcut("set the path stage to trial with /oldrepublic story stage (the debrief did not go through)");
				server.runCommand("oldrepublic story stage @p trial");
				context.waitTicks(3);
			}
		}

		boolean won = story().hasFlag(StoryRules.TRIAL_WON);

		if (!won && story().stage() == PathStage.TRIAL) {
			String blocker = onServer(p -> Dialogues.failReason(Map.of("story:trial", ""), p, null));
			note(step, Result.INFO, "trial requirements: level " + data().level + ", " + standing() + "; " + (blocker == null ? "met" : "blocked: " + blocker));

			if ("message.oldrepublic.story.blocked.level".equals(blocker)) {
				// The run skips the hunting a player would do between lessons; make up the levels
				// so the spar itself is still fought with real inputs.
				int level = onServer(p -> StoryService.balance().trialMinLevel);
				shortcut("set the level to " + level + " with /oldrepublic setlevel so the Trial of the Blade can be asked for (" + step + ")");
				server.runCommand("execute as @p run oldrepublic setlevel " + level);
				context.waitTicks(3);
				blocker = onServer(p -> Dialogues.failReason(Map.of("story:trial", ""), p, null));
				note(step, Result.INFO, "trial requirements now: level " + data().level + "; " + (blocker == null ? "met" : "blocked: " + blocker));
			}

			if (blocker == null && talkAndChoose(step, jedi, "trial_dialogue", "dialogue.oldrepublic.mentor_jedi.opt.trial") == 1
					&& waitServer(40, p -> Spar.inSpar(p))) {
				igniteSaber();
				float before = damageTaken;
				context.waitTicks(10);
				shot("trial_of_the_blade");
				fight(jedi, 1600, false, step, () -> !onServer(p -> Spar.inSpar(p)));
				won = waitServer(40, p -> Story.get(p).hasFlag(StoryRules.TRIAL_WON));
				note(step, won ? Result.INFO : Result.FAIL, "trial spar: won=" + won + ", damage taken " + Math.round(damageTaken - before) + ", " + storyLine());
				endSpar(step);
			}
		}

		if (!won && story().stage().ordinal() >= PathStage.TRIAL.ordinal()) {
			shortcut("passed the Trial of the Blade with /oldrepublic story trial win (the spar was not won or could not start)");
			server.runCommand("oldrepublic story trial @p win");
			context.waitTicks(10);
		}

		context.waitTicks(20);
		shot("padawan");
		int heal = slotOf("force_heal");
		check(step, onServer(p -> Identity.rank(p)) >= 2 && heal == 3 && alignment() > 0 && story().stage() == PathStage.COMPLETE,
				"won with real inputs=" + won + ", " + storyLine() + ", " + standing() + ", force_heal in slot " + (heal + 1) + ", journal padawan_of_the_watch="
						+ data().hasFlag(JournalEntries.entryFlag(JournalEntries.PADAWAN_OF_THE_WATCH)));
	}

	/** Casts Force Heal (a channel) with its slot key while hurt. */
	private void forceHeal() {
		String step = "33 force heal";
		int slot = slotOf("force_heal");

		if (slot < 0) {
			note(step, Result.FAIL, "Force Heal is in no slot: " + onServer(p -> SkillService.data(p).slots.toString()));
			return;
		}

		closeScreen();
		endSpar(step);
		waitServer(300, p -> Progression.get(p).cooldown("force_heal") == 0 && Progression.get(p).energy >= 30f);
		float max = context.computeOnClient(mc -> mc.player.getMaxHealth());

		if (context.computeOnClient(mc -> mc.player.getHealth()) > max - 6) {
			shortcut("took 8 damage with /damage so Force Heal has something to heal");
			server.runCommand("damage @p 8 minecraft:generic");
			context.waitTicks(25);
		}

		float before = context.computeOnClient(mc -> mc.player.getHealth());
		float energyBefore = data().energy;

		try {
			holdSlot(slot);

			for (int t = 0; t < 45; t++) {
				tickMe(step);

				if (t == 20) {
					shot("force_heal");
				}
			}
		} finally {
			releaseSlot(slot);
		}

		context.waitTicks(5);
		float after = context.computeOnClient(mc -> mc.player.getHealth());
		ForceData f = data();
		check(step, after > before && f.cooldown("force_heal") > 0, String.format(Locale.ROOT, "held slot %d for 45 ticks: health %.1f -> %.1f of %.1f "
				+ "(the saturation effect also regenerates), energy %d -> %d, cooldown %d", slot + 1, before, after, max, Math.round(energyBefore),
				Math.round(f.energy), f.cooldown("force_heal")));
	}

	/** Opens the journal on the Chapter Two pages and records the final story state. */
	private void chapterTwoState() {
		String step = "34 chapter two state";
		closeScreen();
		context.getInput().pressKey(InputConstants.KEY_J);
		boolean journal = waitClient(40, mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("JournalScreen"));
		context.waitTicks(10);
		shot("journal_chapter_two");
		closeScreen();
		ForceData f = data();
		List<String> missing = new ArrayList<>();

		for (String entry : CHAPTER_TWO_ENTRIES) {
			if (!f.hasFlag(JournalEntries.entryFlag(entry))) {
				missing.add(entry);
			}
		}

		StoryData d = story();
		note(step, d.stage() == PathStage.COMPLETE && missing.isEmpty() ? Result.PASS : Result.FAIL, "J opened the journal=" + journal + ", " + storyLine() + ", "
				+ standing() + ", level " + f.level + ", skill points " + f.skillPoints + ", slots " + onServer(p -> SkillService.data(p).slots.toString())
				+ ", Chapter Two journal entries missing " + missing + ", total damage taken " + Math.round(damageTaken));
		server.runCommand("oldrepublic story status @p");
		server.runCommand("oldrepublic skills list @p");
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Three steps (Milestone 3, spec 17.3): the Jedi run
	// ---------------------------------------------------------------------------------------------

	/** Ticks each phase of the Collector fight may take before it is moved on with /oldrepublic boss phase (spec 17.3). */
	private static final int PHASE_TICKS = 2400;
	private static final String R1 = "r1_silent_patrol";
	private static final String R2 = "r2_credits_from_the_core";
	private static final String HOLOCRON_CONTINUE = "dialogue.oldrepublic.holocron_common.opt.continue";
	private static final String HOLOCRON_OWN = "dialogue.oldrepublic.holocron_common.opt.own";

	/** The courtyard (start piece) of the Watchers' Rest the first echo is taken from, once found or placed. */
	private @Nullable SitePiece ruin;
	/** The forceload square kept around a Watchers' Rest the run placed; removed once the ruin is left. */
	private @Nullable String ruinForceload;
	/** The bounty terminal of the Republic outpost the story missions are taken at. */
	private @Nullable BlockPos outpost;

	/** Server view of the Collector fight for one tick (stage ENDED when the player has no arena). */
	private record BossView(ArenaSession.Stage stage, int phase, boolean alive, @Nullable UUID vasch, int id, Vec3 pos, double height, float health,
			float maxHealth, CollectorCombatGoal.Move move, boolean hurlTell, boolean unsung, int singing, boolean held, boolean talking) {
		/** Whether the fight is past {@code phase}: a later phase, the yield, or over. */
		boolean past(int phase) {
			return this.phase > phase || stage == ArenaSession.Stage.YIELDED || stage == ArenaSession.Stage.RESOLVING || stage == ArenaSession.Stage.ENDED;
		}

		/** Whether the fight is on but below {@code phase} (it began again after a reset). */
		boolean isFightingBelow(int phase) {
			return (stage == ArenaSession.Stage.FIGHTING || stage == ArenaSession.Stage.TRANSITION) && this.phase < phase;
		}
	}

	/** One resonance pylon of the arena: its focus entity (null when there is none) and its crystal. */
	private record Pylon(int index, @Nullable UUID focus, int focusId, Vec3 crystal, PylonCrystal state) {
	}

	/**
	 * Runs one Chapter Three step through {@link #step} (one retry after a death). As in Chapter Two,
	 * a step that gets stuck or throws is recorded and the next step still runs: each step checks the
	 * story state first and skips what is already done. Story commands used to get past a failed or
	 * unreliable part are logged as shortcuts.
	 */
	private void chapterThree(String name, Runnable body) {
		try {
			step(name, body);
		} catch (Stuck e) {
			note(name, Result.INFO, "Chapter Three goes on after: " + e.getMessage());
		} catch (RuntimeException e) {
			note(name, Result.FAIL, "error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			LOG.error("PLAYTHROUGH-LOG: Chapter Three step failed", e);
			shot("error");
		} finally {
			releaseAll();
			closeScreen();
		}
	}

	/**
	 * After Chapter Two the chapter waits ({@code chapter_three.waitTicks}); a sleep of at least
	 * {@code minSleepTicks} makes the dream of the Ones That Sing due instead, and the summons
	 * follows once it has played.
	 */
	private void onesThatSing() {
		String step = "35 the ones that sing";
		boolean waiting = waitServer(100, p -> Story.get(p).chapter3.stage() != ChapterThreeStage.NOT_STARTED);
		ChapterThreeStage stage = c3();

		if (stage.ordinal() >= ChapterThreeStage.SUMMONS.ordinal()) {
			note(step, Result.INFO, "the dream is already behind the player: " + c3Line());
			return;
		}

		int[] balance = onServer(p -> new int[] { ChapterThreeService.balance().waitTicks, ChapterThreeService.balance().minSleepTicks });
		note(step, waiting ? Result.INFO : Result.FAIL, "after Chapter Two: " + c3Line() + "; the dream comes after " + balance[0]
				+ " ticks of waiting or a sleep of at least " + balance[1] + " ticks");

		if (stage == ChapterThreeStage.NOT_STARTED) {
			c3Stage(step, ChapterThreeStage.WAITING, "Chapter Three did not start waiting after Chapter Two");
		}

		quietPlace(step);
		clearStrays(step);
		boolean summons = false;

		for (int attempt = 1; attempt <= 2 && !summons; attempt++) {
			shortcut("gave a white bed and set the time to night for the Ones That Sing sleep (the " + balance[0] + "-tick wait is not waited out)");
			server.runCommand("give @a minecraft:white_bed");
			server.runCommand("time set 13000");
			context.waitTicks(5);
			BlockPos bed = placeBlock(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("white_bed")), state -> state.is(BlockTags.BEDS), true);

			if (bed == null) {
				note(step, Result.FAIL, "attempt " + attempt + ": could not place the bed by right-clicking the ground");
				Vec3 spot = onServer(p -> findFlatSpot(p.level(), p.blockPosition().getX() + 12, p.blockPosition().getZ(), 40));

				if (spot != null) {
					shortcut("teleported to another flat spot for the Ones That Sing bed");
					teleport(spot, 0f);
				}

				continue;
			}

			if (!sleepIn(step, attempt, bed)) {
				continue;
			}

			context.waitTicks(40);
			shot("ones_that_sing_sleep");
			boolean woke = waitServer(300, p -> !p.isSleeping());
			boolean due = waitServer(40, p -> Story.get(p).hasFlag(ChapterThreeRules.DREAM_DUE) || Story.get(p).chapter3.stage() != ChapterThreeStage.WAITING);
			context.waitTicks(40);
			shot("ones_that_sing_dream");
			summons = waitServer(400, p -> Story.get(p).chapter3.stage().ordinal() >= ChapterThreeStage.SUMMONS.ordinal());
			note(step, summons ? Result.INFO : Result.FAIL, "attempt " + attempt + ": woke=" + woke + ", dream due after waking=" + due + ", now " + c3Line());
		}

		if (!summons) {
			summons = c3Stage(step, ChapterThreeStage.SUMMONS, "sleeping did not bring the dream");
		}

		check(step, summons && data().hasFlag(JournalEntries.entryFlag(JournalEntries.THE_ONES_THAT_SING)), c3Line() + ", journal the_ones_that_sing="
				+ data().hasFlag(JournalEntries.entryFlag(JournalEntries.THE_ONES_THAT_SING)));
	}

	/** Back at the camp: asks Saelen how to listen and accepts her lesson; she teaches Force Sense. */
	private void listeningLesson() {
		String step = "36 the lesson";
		StoryData d = story();
		ChapterThreeStage stage = d.chapter3.stage();
		UUID jedi = d.jediMentor;

		if (stage.ordinal() > ChapterThreeStage.SUMMONS.ordinal()) {
			note(step, Result.INFO, "the lesson is already behind the player: " + c3Line());
			return;
		}

		if (stage != ChapterThreeStage.SUMMONS || jedi == null) {
			note(step, Result.FAIL, "not at the summons: " + c3Line() + ", mentor " + jedi);
			return;
		}

		backToCamp(step);
		waitServer(100, p -> p.level().getEntity(jedi) instanceof MentorEntity);
		String before = standing();
		int replies = talkAndChoose(step, jedi, "c3_summons_dialogue", "dialogue.oldrepublic.mentor_jedi.opt.c3_how", "dialogue.oldrepublic.mentor_jedi.opt.c3_accept");
		boolean listening = waitServer(40, p -> Story.get(p).chapter3.stage().ordinal() >= ChapterThreeStage.LISTENING.ordinal());

		if (!listening) {
			c3Stage(step, ChapterThreeStage.LISTENING, "the lesson was not accepted in the dialogue");
		}

		boolean sense = onServer(p -> SkillService.data(p).owns(SkillIds.FORCE_SENSE));

		if (!sense) {
			shortcut("granted Force Sense with /oldrepublic force grant (" + step + ")");
			server.runCommand("oldrepublic force grant " + SkillIds.FORCE_SENSE + " @p");
			context.waitTicks(3);
		}

		context.waitTicks(10);
		shot("c3_lesson");
		check(step, replies == 2 && listening && sense && story().hasFlag(ChapterThreeRules.LESSON_ACCEPTED), "replies made " + replies + "/2, "
				+ c3Line() + ", force_sense owned=" + sense + ", lesson accepted=" + story().hasFlag(ChapterThreeRules.LESSON_ACCEPTED) + "; before "
				+ before + "; now " + standing());
	}

	/** "What the Force hides": casts Force Sense with H near the camp and touches the three revealed cairns. */
	private void cairns() {
		String step = "37 cairns";
		ChapterThreeStage stage = c3();

		if (stage.ordinal() > ChapterThreeStage.LISTENING.ordinal()) {
			note(step, Result.INFO, "the cairns are already behind the player: " + c3Line());
			return;
		}

		if (stage != ChapterThreeStage.LISTENING) {
			note(step, Result.FAIL, "not listening: " + c3Line());
			return;
		}

		boolean placed = waitServer(200, p -> !Story.get(p).chapter3.cairns().isEmpty());
		List<BlockPos> cairns = story().chapter3.cairns();

		if (!placed || cairns.isEmpty()) {
			note(step, Result.FAIL, "no cairns were placed around the camp: " + c3Line());
			c3Stage(step, ChapterThreeStage.FIRST_ECHO, "no cairns to touch");
			return;
		}

		backToCamp(step);
		closeScreen();
		boolean sensed = sense(step);
		long revealed = cairns.stream().filter(this::cairnRevealed).count();
		context.waitTicks(10);
		shot("cairns_sensed");
		note(step, Result.INFO, cairns.size() + " cairn(s) at " + cairns.stream().map(BlockPos::toShortString).toList() + ", " + revealed + " revealed by the first Sense");
		int touched = 0;

		for (int i = 0; i < cairns.size(); i++) {
			int bit = 1 << i;
			BlockPos cairn = cairns.get(i);

			if ((story().chapter3.cairnsTouched() & bit) != 0 || !c3AtLeast(ChapterThreeStage.LISTENING) || c3AtLeast(ChapterThreeStage.FIRST_ECHO)) {
				continue;
			}

			Vec3 at = Vec3.atBottomCenterOf(cairn);

			if (!walkTo(at, 2.0, 600, false, null)) {
				Vec3 spot = safeSpotNear(at);
				shortcut("teleported next to a cairn after the walk stopped (" + step + ")");
				note(step, Result.INFO, "walk to the cairn at " + cairn.toShortString() + " stopped: " + walkStop);
				teleport(spot, yawToward(spot, at));
			}

			Vec3 aim = new Vec3(cairn.getX() + 0.5, cairn.getY() + 0.25, cairn.getZ() + 0.5);

			// A cairn in a hollow can be hidden by the ground in front of it: step to where it is in sight.
			if (!sightTo(aim) || me().eye().distanceTo(aim) > CAIRN_REACH) {
				Vec3 spot = sightSpot(aim, 1, 3, CAIRN_REACH, cairn.getY());

				if (spot != null && !walkTo(spot, 0.4, 100, false, null)) {
					shortcut("stepped beside a cairn the walk could not reach (" + step + ")");
					teleport(spot, yawToward(spot, aim));
				}
			}

			if (!cairnRevealed(cairn)) {
				sense(step);
			}

			boolean done = false;

			for (int attempt = 1; attempt <= 2 && !done; attempt++) {
				boolean on = useBlock(cairn, aim);
				done = waitServer(20, p -> (Story.get(p).chapter3.cairnsTouched() & bit) != 0
						|| Story.get(p).chapter3.stage() != ChapterThreeStage.LISTENING);
				closeScreen();

				if (!done) {
					note(step, Result.INFO, "cairn " + (i + 1) + " attempt " + attempt + ": not touched (crosshair on it=" + on + ", " + lookingAt() + ", revealed="
							+ cairnRevealed(cairn) + ")");
					walkTo(at, 1.2, 60, false, null);
				}
			}

			if (done) {
				touched++;

				if (touched == 1) {
					shot("cairn_touched");
				}
			}
		}

		boolean echo = waitServer(40, p -> Story.get(p).chapter3.stage().ordinal() >= ChapterThreeStage.FIRST_ECHO.ordinal());
		check(step, sensed && echo && touched == cairns.size(), "H pressed (Sense cast=" + sensed + "), " + touched + "/" + cairns.size()
				+ " cairn(s) touched by right-click, " + c3Line() + ", journal what_the_force_hides="
				+ data().hasFlag(JournalEntries.entryFlag(JournalEntries.WHAT_THE_FORCE_HIDES)));

		if (!echo) {
			c3Stage(step, ChapterThreeStage.FIRST_ECHO, "the cairns did not move the chapter on");
		}
	}

	/**
	 * The first echo: the nearest Watchers' Rest within {@code firstEchoRadius} whose pedestal has not
	 * given a holocron, found with the mod's structure locator; when there is none, one is placed on a
	 * flat spot near the player (logged). Walks to its courtyard and fights Vasch's diggers there.
	 */
	private void firstEcho() {
		String step = "38 first echo";
		ChapterThreeStage stage = c3();

		if (stage.ordinal() >= ChapterThreeStage.RELIQUARY.ordinal()) {
			note(step, Result.INFO, "the first echo and the leads are already behind the player: " + c3Line());
			return;
		}

		if (stage.ordinal() < ChapterThreeStage.FIRST_ECHO.ordinal()) {
			note(step, Result.FAIL, "the first echo has not begun: " + c3Line());
			c3Stage(step, ChapterThreeStage.FIRST_ECHO, "the chapter was not at the first echo");
		}

		int radius = onServer(p -> ChapterThreeService.balance().firstEchoRadius);
		SitePiece[] found = new SitePiece[1];
		boolean[] answered = new boolean[1];
		onServer(p -> {
			StructureLocator.find(p, SiteKind.JEDI_RUIN, radius, HolocronService.usedSites(p), piece -> {
				found[0] = piece;
				answered[0] = true;
			});
			return null;
		});
		boolean searched = waitServer(600, p -> answered[0]);
		SitePiece start = onServer(p -> found[0]);
		note(step, Result.INFO, "structure locator, Watchers' Rest within " + radius + " blocks: " + (start == null ? "none (answered=" + searched + ")"
				: start.template().getPath() + " " + box(start.box()) + ", " + Math.round(flat(Vec3.atCenterOf(start.origin()), here())) + " blocks away"));

		if (start == null) {
			start = placeRuin(step);
		}

		if (start == null) {
			note(step, Result.FAIL, "no Watchers' Rest to go to: " + c3Line());
			return;
		}

		ruin = start;
		Vec3 stand = Vec3.atBottomCenterOf(SiteAnchors.toWorld(start, new BlockPos(15, 4, 15)));
		select(QuestItems.WANDERERS_JOURNAL);
		travel(step, stand, 12, "the Watchers' Rest");
		waitForChunks(step);
		SitePiece site = start;
		List<SitePiece> pieces = onServer(p -> StructureLocator.sitePieces(site));
		SitePiece court = pieceOf(pieces, "jedi_ruin/courtyard");

		if (court != null) {
			ruin = court;
		}

		Vec3 at = here();
		aimAt(stand.add(0, at.y - stand.y + 1.5, 0));
		context.waitTicks(20);
		shot("watchers_rest");
		int diggers = fightPoachers(step, 48);
		check(step, flat(stand, here()) <= 24 && court != null, "at the Watchers' Rest (" + pieces.size() + " pieces: "
				+ pieces.stream().map(x -> x.template().getPath()).toList() + ", rotation " + start.rotation() + "), " + Math.round(flat(stand, here()))
				+ " blocks from the courtyard, " + diggers + " digger(s) cut down, " + c3Line());
	}

	/**
	 * Places a Watchers' Rest about 64 blocks from the player with {@code /oldrepublic site place}
	 * (logged), after force-loading its area. Returns its courtyard piece, or null.
	 */
	private @Nullable SitePiece placeRuin(String step) {
		Vec3 spot = onServer(p -> findFlatSpot(p.level(), p.blockPosition().getX() + 64, p.blockPosition().getZ(), 48));

		if (spot == null) {
			note(step, Result.FAIL, "no flat spot near the player for a Watchers' Rest");
			return null;
		}

		BlockPos at = BlockPos.containing(spot);
		// A structure's pieces reach out from its start chunk's corner (as in WorldClientSteps).
		int cx = (at.getX() >> 4) << 4;
		int cz = (at.getZ() >> 4) << 4;
		int reach = 48;
		String square = String.format(Locale.ROOT, "%d %d %d %d", cx - reach, cz - reach, cx + reach, cz + reach);
		shortcut("placed a Watchers' Rest with /oldrepublic site place jedi_ruin about 64 blocks from the player (none within the first-echo radius)");
		server.runCommand("forceload add " + square);
		ruinForceload = square;
		boolean loaded = waitServer(400, p -> {
			for (int x = (cx - reach) >> 4; x <= (cx + reach) >> 4; x++) {
				for (int z = (cz - reach) >> 4; z <= (cz + reach) >> 4; z++) {
					if (!p.level().hasChunk(x, z)) {
						return false;
					}
				}
			}

			return true;
		});
		server.runCommand(String.format(Locale.ROOT, "oldrepublic site place jedi_ruin %d %d %d", at.getX(), at.getY(), at.getZ()));
		context.waitTicks(5);
		BlockPos near = new BlockPos(cx, at.getY(), cz);
		SitePiece court = onServer(p -> {
			for (SitePiece piece : StructureLocator.piecesNear(p.level(), near, 32)) {
				if (piece.kind() == SiteKind.JEDI_RUIN && piece.template().getPath().equals("jedi_ruin/courtyard")) {
					return piece;
				}
			}

			return null;
		});
		note(step, court == null ? Result.FAIL : Result.INFO, "site place jedi_ruin at " + at.toShortString() + " (area loaded=" + loaded + "): courtyard "
				+ (court == null ? "not found" : box(court.box()) + ", rotation " + court.rotation()));
		return court;
	}

	/**
	 * The Watchers' Rest stair: Force Sense (H) shows the veiled doorway under the half dome, Force
	 * Push opens it, and the bot walks down the switchback stair into the Listening Hall.
	 */
	private void ruinStair() {
		String step = "39 ruin stair";
		SitePiece court = ruin;

		if (court == null) {
			note(step, Result.FAIL, "no Watchers' Rest was found or placed");
			return;
		}

		if (c3AtLeast(ChapterThreeStage.RELIQUARY) || onServer(p -> HolocronService.attunedCount(p, HolocronKind.JEDI)) > 0) {
			note(step, Result.INFO, "the Listening Hall is not needed any more: " + c3Line());
			return;
		}

		List<SitePiece> pieces = onServer(p -> StructureLocator.sitePieces(court));
		SitePiece hall = pieceOf(pieces, "jedi_ruin/listening_hall");
		BoundingBox bounds = around(pieces, 2);
		fightPoachers(step, 48);
		Vec3 stand = Vec3.atBottomCenterOf(SiteAnchors.toWorld(court, new BlockPos(15, 4, 15)));
		BlockPos mouth = SiteAnchors.toWorld(court, new BlockPos(15, 5, 18));

		if (!walkTo(stand, 0.5, 400, false, null)) {
			shortcut("teleported in front of the veiled stair mouth after the walk stopped (" + step + ")");
			note(step, Result.INFO, "walk to the stair mouth stopped: " + walkStop);
			teleport(stand, yawToward(stand, Vec3.atCenterOf(mouth)));
		}

		String before = veil(mouth);
		boolean open = "OPEN".equals(before);
		boolean sensed = false;
		boolean pushed = false;
		boolean struck = false;

		if (!open) {
			sensed = sense(step);
			boolean revealed = waitServer(10, p -> !"VEILED".equals(veilAt(p, mouth)));
			aimAt(Vec3.atCenterOf(mouth));
			context.waitTick();
			shot("stair_mouth_sensed");
			pushed = castPush(step);
			open = waitServer(20, p -> "OPEN".equals(veilAt(p, mouth)));

			if (!open && revealed) {
				// A lit-saber hit opens a revealed group too.
				igniteSaber();
				aimAt(Vec3.atCenterOf(mouth));
				context.waitTick();
				context.getInput().pressKey(ATTACK);
				struck = true;
				open = waitServer(10, p -> "OPEN".equals(veilAt(p, mouth)));
			}
		}

		context.waitTicks(10);
		shot("stair_mouth_open");
		BlockPos entry = hall == null ? null : SiteAnchors.toWorld(hall, new BlockPos(7, 3, 20));
		boolean walked = open && entry != null && walkRoute(step, entry, bounds);

		if (!walked && entry != null) {
			shortcut("teleported down the stair into the Listening Hall (" + (open ? "the walk down stopped" : "the stair mouth stayed shut") + "; " + step + ")");
			teleport(Vec3.atBottomCenterOf(entry), 0f);
		}

		check(step, open && walked, "veil " + before + " -> " + veil(mouth) + " (H cast=" + sensed + ", Push cast=" + pushed + ", saber hit=" + struck
				+ "), walked down the stair=" + walked + ", hall " + (hall == null ? "not found" : box(hall.box())));
	}

	/**
	 * The Listening Hall: Push sinks the three counterweights (east, south, west), which raise the
	 * step stones; the bot climbs them to the gallery, fights the Watchers that wake and attunes the
	 * Jedi pedestal's holocron (Continue to the choice, then Own).
	 */
	private void listeningHall() {
		String step = "40 listening hall";
		SitePiece court = ruin;
		int jediBefore = onServer(p -> HolocronService.attunedCount(p, HolocronKind.JEDI));

		if (jediBefore > 0) {
			note(step, Result.INFO, "a Jedi holocron is attuned already: " + c3Line());
			return;
		}

		List<SitePiece> pieces = court == null ? List.of() : onServer(p -> StructureLocator.sitePieces(court));
		SitePiece hall = pieceOf(pieces, "jedi_ruin/listening_hall");
		boolean attuned = false;
		boolean climbed = false;
		int sunk = 0;

		if (hall == null) {
			note(step, Result.FAIL, "no Listening Hall: " + pieces.stream().map(x -> x.template().getPath()).toList());
		} else {
			BoundingBox bounds = around(pieces, 2);

			if (!hall.box().isInside(BlockPos.containing(here()))) {
				Vec3 entry = Vec3.atBottomCenterOf(SiteAnchors.toWorld(hall, new BlockPos(7, 3, 20)));
				shortcut("teleported into the Listening Hall (" + step + ")");
				teleport(entry, 0f);
			}

			// Counterweight cell and the floor cell three blocks in front of it, template-local.
			int[][] weights = { { 22, 3, 13, 19, 3, 13 }, { 14, 3, 22, 14, 3, 19 }, { 2, 3, 13, 5, 3, 13 } };
			String[] names = { "east", "south", "west" };

			for (int i = 0; i < weights.length; i++) {
				int[] w = weights[i];
				BlockPos weight = SiteAnchors.toWorld(hall, new BlockPos(w[0], w[1], w[2]));
				BlockPos front = SiteAnchors.toWorld(hall, new BlockPos(w[3], w[4], w[5]));

				if (!walkRoute(step, front, bounds)) {
					walkTo(Vec3.atBottomCenterOf(front), 0.5, 200, false, null);
				}

				aimAt(Vec3.atCenterOf(weight));
				context.waitTick();
				String aim = lookingAt();
				boolean cast = castPush(step);
				boolean down = waitServer(10, p -> sunkAt(p, weight));

				if (down) {
					sunk++;
				}

				note(step, Result.INFO, names[i] + " counterweight at " + weight.toShortString() + ": crosshair on " + aim + ", Push cast=" + cast + ", sunk=" + down);
			}

			// The counterweights raise the step stones through repeaters: wait until all six stand out as ledges.
			List<BlockPos> steps = new ArrayList<>();

			for (int j = 1; j <= 6; j++) {
				steps.add(SiteAnchors.toWorld(hall, new BlockPos(5 + j, 2 + j, 5)));
			}

			boolean extended = waitServer(100, p -> steps.stream().allMatch(at -> stepOut(p, at)));
			note(step, Result.INFO, "step stones out as ledges: " + onServer(p -> steps.stream().filter(at -> stepOut(p, at)).count()) + "/6 (waited until all="
					+ extended + ")");
			shot("counterweights");
			BlockPos gallery = SiteAnchors.toWorld(hall, new BlockPos(17, 10, 6));
			// The ledges are climbed one jump at a time from the floor cell before the first, then the gallery edge.
			BlockPos foot = SiteAnchors.toWorld(hall, new BlockPos(5, 3, 5));
			List<BlockPos> ledges = new ArrayList<>(steps.stream().map(BlockPos::above).toList());
			ledges.add(SiteAnchors.toWorld(hall, new BlockPos(12, 10, 5)));
			String climb = walkRoute(step, foot, bounds) ? climbLedges(foot, ledges) : "the floor before the first step stone was not reached";
			note(step, Result.INFO, "step stone climb: " + (climb == null ? "on the gallery edge" : climb));
			climbed = climb == null && walkRoute(step, gallery, bounds);

			if (!climbed) {
				shortcut("teleported onto the Listening Hall gallery (the step stones were not climbed; " + step + ")");
				teleport(Vec3.atBottomCenterOf(gallery), 0f);
			}

			// The Watchers by the gallery wake when a player comes near (unless they bow to the Jedi).
			context.waitTicks(40);
			fightSentinels(step, gallery, 16);
			BlockPos pedestal = SiteAnchors.find(hall, SiteAnchors.Anchor.PEDESTAL_JEDI);
			attuned = attune(step, pedestal != null ? pedestal : SiteAnchors.toWorld(hall, new BlockPos(17, 10, 5)), "holocron_jedi");
		}

		if (!attuned) {
			shortcut("attuned jedi_morrane with /oldrepublic holocron grant (the pedestal was not used; " + step + ")");
			server.runCommand("oldrepublic holocron grant @p jedi_morrane");
			context.waitTicks(5);
		}

		boolean leads = waitServer(60, p -> Story.get(p).chapter3.stage().ordinal() >= ChapterThreeStage.LEADS.ordinal());

		if (!leads) {
			leads = c3Stage(step, ChapterThreeStage.LEADS, "the attunement did not end the first echo");
		}

		check(step, attuned && climbed && leads, sunk + "/3 counterweights sunk by Push, climbed to the gallery=" + climbed + ", attuned at the pedestal="
				+ attuned + ", jedi_morrane=" + onServer(p -> HolocronService.hasAttuned(p, "jedi_morrane")) + ", Jedi holocrons "
				+ onServer(p -> HolocronService.attunedCount(p, HolocronKind.JEDI)) + ", " + c3Line());
	}

	/**
	 * The pilgrim's niche behind the hall's east wall: Sense and Push open the veiled panel, and the
	 * Wayfarer pedestal there gives the second holocron.
	 */
	private void pilgrimNiche() {
		String step = "41 pilgrim niche";
		SitePiece court = ruin;
		int before = onServer(p -> HolocronService.attunedCount(p, null));

		if (before >= 2) {
			note(step, Result.INFO, before + " holocrons are attuned already");
			removeRuinForceload();
			return;
		}

		List<SitePiece> pieces = court == null ? List.of() : onServer(p -> StructureLocator.sitePieces(court));
		SitePiece hall = pieceOf(pieces, "jedi_ruin/listening_hall");
		SitePiece niche = pieceOf(pieces, "jedi_ruin/pilgrim_niche");

		if (hall == null || niche == null) {
			note(step, Result.FAIL, "no pilgrim's niche: " + pieces.stream().map(x -> x.template().getPath()).toList());
			removeRuinForceload();
			return;
		}

		BoundingBox bounds = around(pieces, 2);
		BlockPos panel = SiteAnchors.toWorld(hall, new BlockPos(22, 3, 18));
		BlockPos front = SiteAnchors.toWorld(hall, new BlockPos(19, 3, 18));

		if (!walkRoute(step, front, bounds) && !walkTo(Vec3.atBottomCenterOf(front), 0.5, 200, false, null)) {
			shortcut("teleported in front of the veiled panel to the pilgrim's niche (" + step + ")");
			teleport(Vec3.atBottomCenterOf(front), 0f);
		}

		boolean open = "OPEN".equals(veil(panel));
		boolean sensed = false;
		boolean pushed = false;

		if (!open) {
			sensed = sense(step);
			aimAt(Vec3.atCenterOf(panel).add(0, 0.5, 0));
			context.waitTick();
			pushed = castPush(step);
			open = waitServer(20, p -> "OPEN".equals(veilAt(p, panel)));

			if (!open) {
				igniteSaber();
				aimAt(Vec3.atCenterOf(panel).add(0, 0.5, 0));
				context.waitTick();
				context.getInput().pressKey(ATTACK);
				open = waitServer(10, p -> "OPEN".equals(veilAt(p, panel)));
			}
		}

		shot("pilgrim_niche_open");
		BlockPos stand = SiteAnchors.toWorld(niche, new BlockPos(3, 1, 3));
		boolean walked = open && walkRoute(step, stand, bounds);

		if (!walked) {
			shortcut("teleported into the pilgrim's niche (" + (open ? "the walk stopped" : "the panel stayed shut") + "; " + step + ")");
			teleport(Vec3.atBottomCenterOf(stand), 0f);
		}

		BlockPos pedestal = SiteAnchors.find(niche, SiteAnchors.Anchor.PEDESTAL_WAYFARER);
		boolean attuned = attune(step, pedestal != null ? pedestal : SiteAnchors.toWorld(niche, new BlockPos(4, 1, 3)), "holocron_wayfarer");
		int after = onServer(p -> HolocronService.attunedCount(p, null));
		check(step, open && walked && attuned, "panel open=" + open + " (H cast=" + sensed + ", Push cast=" + pushed + "), walked in=" + walked
				+ ", attuned=" + attuned + ", holocrons " + before + " -> " + after + ", " + c3Line());
		removeRuinForceload();
	}

	private void removeRuinForceload() {
		if (ruinForceload != null) {
			server.runCommand("forceload remove " + ruinForceload);
			ruinForceload = null;
		}
	}

	/** Finds the nearest Republic outpost (one is placed on the summons; asked for again when missing) and waits for its officer. */
	private void republicOutpost() {
		String step = "42 republic outpost";
		removeRuinForceload();

		if (onServer(p -> Missions.storyDone(p, R1) && Missions.storyDone(p, R2))) {
			note(step, Result.INFO, "both Republic story missions are done");
			return;
		}

		BlockPos terminal = republicTerminal(400);

		if (terminal == null) {
			shortcut("asked for outposts with /oldrepublic outpost ensure (no Republic outpost found within 1024 blocks)");
			server.runCommand("oldrepublic outpost ensure @p");
			terminal = republicTerminal(600);
		}

		if (terminal == null) {
			note(step, Result.FAIL, "no Republic outpost within 1024 blocks");
			return;
		}

		BlockPos at = terminal;
		outpost = at;
		note(step, Result.INFO, "Republic bounty terminal at " + at.toShortString() + ", " + Math.round(flat(Vec3.atBottomCenterOf(at), here())) + " blocks away");
		travel(step, Vec3.atBottomCenterOf(at), 10, "the Republic outpost");
		boolean officer = waitServer(600, p -> officerOf(p, at) != null);
		Vec3 me = here();
		aimAt(Vec3.atCenterOf(at).add(0, me.y - at.getY() + 1.0, 0));
		context.waitTicks(20);
		shot("republic_outpost");
		check(step, officer, "at the outpost (" + Math.round(flat(Vec3.atBottomCenterOf(at), here())) + " blocks from the terminal), officer present=" + officer
				+ ", Republic " + onServer(p -> Identity.reputation(p, Faction.REPUBLIC)) + " (" + onServer(p -> Identity.tier(p, Faction.REPUBLIC)) + ")");
	}

	/** Polls {@link Outposts#nearestTerminal} (which starts a locator search when nothing is cached) for up to {@code timeout} ticks. */
	private @Nullable BlockPos republicTerminal(int timeout) {
		for (int t = 0; t < timeout; t += 10) {
			BlockPos terminal = onServer(p -> Outposts.nearestTerminal(p, Faction.REPUBLIC, 1024));

			if (terminal != null) {
				return terminal;
			}

			context.waitTicks(10);
		}

		return null;
	}

	/**
	 * R1 "The Silent Patrol": taken from the officer, the bound sentinels cut down, then each downed
	 * trooper tended (Sneak + right-click, staying crouched beside them).
	 */
	private void silentPatrol() {
		String step = "43 silent patrol";

		if (onServer(p -> Missions.storyDone(p, R1))) {
			note(step, Result.INFO, R1 + " is done already");
			return;
		}

		int repBefore = onServer(p -> Identity.reputation(p, Faction.REPUBLIC));

		if (!takeMission(step, R1, "dialogue.oldrepublic.officer_republic.opt.r1", "dialogue.oldrepublic.officer_republic.opt.r1_accept")) {
			note(step, Result.FAIL, "could not take " + R1);
			return;
		}

		boolean there = reachMissionSite(step, "the Silent Patrol site");
		igniteSaber();
		float hurt = damageTaken;
		int kills = fightMissionRole(step, R1, "enemy");
		boolean decide = waitServer(100, p -> MissionService.get(p).active == null || MissionService.get(p).active.stage != MissionData.Stage.ACTIVE);
		shot("silent_patrol_fight");
		List<UUID> downed = missionEntities(R1, "downed");
		int tended = 0;

		for (UUID id : downed) {
			if (tendDowned(step, id)) {
				tended++;

				if (tended == 1) {
					shot("silent_patrol_tend");
				}
			}
		}

		boolean done = waitServer(100, p -> Missions.storyDone(p, R1));

		if (!done) {
			shortcut("completed " + R1 + " with /oldrepublic mission complete tend (" + step + ")");
			server.runCommand("oldrepublic mission complete @p tend");
			waitServer(40, p -> Missions.storyDone(p, R1));
		}

		int repAfter = onServer(p -> Identity.reputation(p, Faction.REPUBLIC));
		check(step, there && kills > 0 && decide && !downed.isEmpty() && tended == downed.size() && done, "site reached=" + there + ", " + kills
				+ " sentinel(s) cut down, fight over=" + decide + ", " + tended + "/" + downed.size() + " downed trooper(s) tended, completed by play=" + done
				+ ", Republic " + repBefore + " -> " + repAfter + ", damage taken " + Math.round(damageTaken - hurt) + ", " + c3Line());
	}

	/**
	 * R2 "Credits from the Core": taken from the officer, the crew fought, the chief brought to yield
	 * and spared, the crate opened for the archive record, and the record returned at the officer.
	 */
	private void creditsFromTheCore() {
		String step = "44 credits from the core";

		if (onServer(p -> Missions.storyDone(p, R2))) {
			note(step, Result.INFO, R2 + " is done already");
			return;
		}

		int repBefore = onServer(p -> Identity.reputation(p, Faction.REPUBLIC));

		if (!takeMission(step, R2, "dialogue.oldrepublic.officer_republic.opt.r2", "dialogue.oldrepublic.officer_republic.opt.r2_accept")) {
			note(step, Result.FAIL, "could not take " + R2);
			return;
		}

		boolean there = reachMissionSite(step, "the smugglers' cache");
		igniteSaber();
		float hurt = damageTaken;
		int kills = fightMissionRole(step, R2, "enemy");
		List<UUID> chiefs = missionEntities(R2, "chief");

		if (chiefs.isEmpty()) {
			// Hessa Dray, spared in Chapter Two, runs the cache: she only comes out when the player nears her post.
			// A mission without a camp (none could be stamped) has its chief on a small ring around the site.
			BlockPos post = onServer(p -> {
				MissionData.Active active = MissionService.get(p).active;
				SitePiece piece = active == null || active.site == null ? null : StructureLocator.runtimePieceAt(p.level(), active.site, SiteKind.MISSION_SITE);
				BlockPos anchor = piece == null ? null : SiteAnchors.find(piece, SiteAnchors.Anchor.CHIEF);
				return anchor != null || active == null ? anchor : active.site;
			});

			if (post != null) {
				walkTo(Vec3.atBottomCenterOf(post), 2.5, 300, false, () -> !missionEntities(R2, "chief").isEmpty());
				waitServer(60, p -> MissionService.get(p).active == null || MissionService.get(p).active.has(MissionData.FLAG_CHIEF_DONE));
				chiefs = missionEntities(R2, "chief");
				note(step, Result.INFO, "walked to the chief's post at " + post.toShortString() + ": " + chiefs.size() + " chief(s) out");

				if (chiefs.isEmpty()) {
					Vec3 postFeet = Vec3.atBottomCenterOf(post);
					note(step, Result.INFO, onServer(p -> {
						MissionData.Active active = MissionService.get(p).active;
						List<String> near = p.level().getEntitiesOfClass(LivingEntity.class, new AABB(post).inflate(24), e -> e.hasCustomName()).stream()
								.map(e -> e.getName().getString() + " at " + e.blockPosition().toShortString() + (e.isAlive() ? "" : " (dead)")).toList();
						return String.format(Locale.ROOT, "no chief at the post: the player %.1f blocks from it, chief done=%s, captain fate %s, named near the post %s",
								p.position().distanceTo(postFeet), active != null && active.has(MissionData.FLAG_CHIEF_DONE), Story.get(p).captainFate(), near);
					}));
				}
			}
		}

		boolean spared = false;

		if (!chiefs.isEmpty()) {
			UUID chief = chiefs.get(0);

			if (!yielded(chief) && target(chief).alive()) {
				fight(chief, 1200, false, step, () -> yielded(chief));
			}

			if (target(chief).alive() && yielded(chief)) {
				context.waitTicks(10);
				shot("r2_chief_yield");
				spared = talkAndChoose(step, chief, null, "dialogue.oldrepublic.yield.opt.spare", "dialogue.oldrepublic.common.opt.yes") == 2;
			}
		}

		boolean chiefDone = waitServer(60, p -> MissionService.get(p).active == null || MissionService.get(p).active.has(MissionData.FLAG_CHIEF_DONE));

		if (!chiefDone) {
			List<UUID> rest = missionEntities(R2, "chief");
			shortcut("killed the R2 chief the bot could not bring to yield (" + step + ")");
			onServer(p -> {
				for (UUID id : rest) {
					if (p.level().getEntity(id) instanceof LivingEntity living && living.isAlive()) {
						living.kill(p.level());
					}
				}

				return null;
			});
			chiefDone = waitServer(60, p -> MissionService.get(p).active == null || MissionService.get(p).active.has(MissionData.FLAG_CHIEF_DONE));
		}

		BlockPos crate = onServer(p -> {
			MissionData.Active active = MissionService.get(p).active;
			SitePiece piece = active == null || active.site == null ? null : StructureLocator.runtimePieceAt(p.level(), active.site, SiteKind.MISSION_SITE);
			return piece == null ? null : SiteAnchors.find(piece, SiteAnchors.Anchor.CRATE);
		});
		// A spared Hessa Dray (the Chapter Two captain) hands the record over herself.
		boolean fromChief = waitServer(20, p -> MissionService.get(p).active == null || MissionService.get(p).active.stage == MissionData.Stage.RETURN);
		boolean issued = fromChief;

		if (fromChief) {
			note(step, Result.INFO, "the record was handed over at the chief, mission stage " + missionStage());
		} else if (crate != null) {
			walkTo(Vec3.atBottomCenterOf(crate), 2.0, 300, false, null);

			if (!sightTo(Vec3.atCenterOf(crate)) || me().eye().distanceTo(Vec3.atCenterOf(crate)) > CRATE_REACH) {
				Vec3 spot = sightSpot(Vec3.atCenterOf(crate), 1, 3, CRATE_REACH, crate.getY());

				if (spot != null && !walkTo(spot, 0.4, 100, false, null)) {
					shortcut("stepped beside the R2 crate the walk could not reach (" + step + ")");
					teleport(spot, yawToward(spot, Vec3.atCenterOf(crate)));
				}
			}

			for (int attempt = 1; attempt <= 2 && !issued; attempt++) {
				boolean on = useBlock(crate, Vec3.atCenterOf(crate));
				issued = waitServer(40, p -> MissionService.get(p).active == null || MissionService.get(p).active.stage == MissionData.Stage.RETURN);
				closeScreen();

				if (!issued) {
					note(step, Result.INFO, "crate attempt " + attempt + ": crosshair on it=" + on + " (" + lookingAt() + "), mission stage " + missionStage());
					walkTo(Vec3.atBottomCenterOf(crate), 1.5, 60, false, null);
				}
			}
		} else {
			// Without a camp there is no crate: the record is handed over near the site once the chief is dealt with.
			BlockPos site = onServer(p -> MissionService.get(p).active == null ? null : MissionService.get(p).active.site);

			if (site != null) {
				walkTo(Vec3.atBottomCenterOf(site), 2.0, 300, false, null);
				issued = waitServer(60, p -> MissionService.get(p).active == null || MissionService.get(p).active.stage == MissionData.Stage.RETURN);
			}

			note(step, issued ? Result.INFO : Result.FAIL, "no camp at the mission site " + (site == null ? "-" : site.toShortString())
					+ " (the mission runs around a bare spot): record handed over there=" + issued + ", mission stage " + missionStage());
		}

		shot("r2_record");
		boolean returned = false;
		BlockPos terminal = outpost;

		if (issued && terminal != null) {
			travel(step, Vec3.atBottomCenterOf(terminal), 10, "the Republic officer");
			waitServer(600, p -> officerOf(p, terminal) != null);
			UUID officer = officerNear(terminal);

			if (officer != null) {
				returned = talkAndChoose(step, officer, "r2_turn_in", "dialogue.oldrepublic.officer_republic.opt.work", "dialogue.oldrepublic.officer_republic.opt.r2_turn_in",
						"dialogue.oldrepublic.officer_republic.opt.r2_return") == 3;
			}
		}

		boolean done = waitServer(60, p -> Missions.storyDone(p, R2));

		if (!done) {
			shortcut("completed " + R2 + " with /oldrepublic mission complete return (" + step + ")");
			server.runCommand("oldrepublic mission complete @p return");
			waitServer(40, p -> Missions.storyDone(p, R2));
		}

		int repAfter = onServer(p -> Identity.reputation(p, Faction.REPUBLIC));
		check(step, there && chiefDone && spared && issued && returned && done, "site reached=" + there + ", " + kills + " crew cut down, chief spared at her yield="
				+ spared + ", chief done=" + chiefDone + ", record in hand=" + issued + " (" + (fromChief ? "from the chief" : crate != null ? "from the crate" : "at the bare site") + "), returned at the officer=" + returned + ", completed by play=" + done
				+ ", Republic " + repBefore + " -> " + repAfter + ", damage taken " + Math.round(damageTaken - hurt) + ", " + c3Line());
	}

	/**
	 * Fights an ambush if one came, then makes up what play left short: the leads (logged
	 * {@code c3 lead add}) and level 9 (logged {@code addxp}). The Reliquary opens and is placed.
	 */
	private void leadsAndLevel() {
		String step = "45 leads and level";
		ChapterThreeStage stage = c3();

		if (stage.ordinal() >= ChapterThreeStage.RELIQUARY.ordinal()) {
			note(step, Result.INFO, "the Reliquary is already open: " + c3Line());
			return;
		}

		if (stage != ChapterThreeStage.LEADS) {
			note(step, Result.FAIL, "not following leads: " + c3Line());
			c3Stage(step, ChapterThreeStage.LEADS, "the chapter was not at the leads");
		}

		int ambushers = fightPoachers(step, 40);
		int[] need = onServer(p -> new int[] { ChapterThreeService.balance().leadsNeeded, ChapterThreeService.balance().reliquaryMinLevel });
		Set<String> leads = story().chapter3.leads();
		int levelBefore = data().level;
		note(step, Result.INFO, "from play: leads " + leads + " (" + leads.size() + "/" + need[0] + "), level " + levelBefore + "/" + need[1]
				+ (ambushers > 0 ? ", " + ambushers + " ambusher(s) cut down" : ""));

		if (leads.size() < need[0]) {
			List<String> extra = new ArrayList<>(List.of(ChapterThreeRules.LEAD_LEDGER, ChapterThreeRules.LEAD_CONTRACT,
					ChapterThreeRules.LEAD_MISSION_PREFIX + "clear_camp", ChapterThreeRules.LEAD_MISSION_PREFIX + "secure_site"));
			extra.removeAll(leads);

			for (int i = 0; i < need[0] - leads.size() && i < extra.size(); i++) {
				shortcut("added the lead " + extra.get(i) + " with /oldrepublic c3 lead add (" + leads.size() + " of " + need[0] + " leads from play)");
				server.runCommand("oldrepublic c3 lead add @p " + extra.get(i));
			}

			context.waitTicks(3);
		}

		if (data().level < need[1]) {
			long xp = onServer(p -> {
				ForceData f = Progression.get(p);
				long total = -f.xp;

				for (int level = f.level; level < need[1]; level++) {
					total += Progression.xpToNext(level);
				}

				return Math.max(1L, total);
			});
			shortcut("added " + xp + " XP with /oldrepublic addxp to reach level " + need[1] + " for the Reliquary (level " + levelBefore + " from play)");
			server.runCommand("execute as @p run oldrepublic addxp " + xp);
			waitServer(20, p -> Progression.get(p).level >= need[1]);
		}

		boolean open = waitServer(100, p -> Story.get(p).chapter3.stage().ordinal() >= ChapterThreeStage.RELIQUARY.ordinal());

		if (!open) {
			open = c3Stage(step, ChapterThreeStage.RELIQUARY, "leads and level are met but the chapter did not move on");
		}

		boolean placed = waitServer(200, p -> Story.get(p).chapter3.reliquary() != null);

		if (!placed) {
			placed = placeReliquary(step);
		}

		check(step, open && placed && data().level >= need[1], c3Line() + ", level " + levelBefore + " -> " + data().level + ", journal vasch="
				+ story().chapter3.leads().size() + " leads");
	}

	/**
	 * Places the Reliquary by command (logged) when the story has not: from where the player stands,
	 * then, when no spot passes there, from the mentor camp. Returns whether it is placed.
	 */
	private boolean placeReliquary(String step) {
		shortcut("placed the Reliquary with /oldrepublic c3 reliquary (" + step + ")");
		server.runCommand("oldrepublic c3 reliquary @p");
		boolean placed = waitServer(100, p -> Story.get(p).chapter3.reliquary() != null);

		if (!placed) {
			note(step, Result.INFO, "no Reliquary spot passed around " + BlockPos.containing(here()).toShortString() + "; trying again from the mentor camp");
			backToCamp(step);
			shortcut("placed the Reliquary with /oldrepublic c3 reliquary from the mentor camp (" + step + ")");
			server.runCommand("oldrepublic c3 reliquary @p");
			placed = waitServer(100, p -> Story.get(p).chapter3.reliquary() != null);
		}

		return placed;
	}

	/**
	 * The Reliquary: walks to it on the surface and down the spiral descent (a logged teleport to the
	 * antechamber when the way cannot be walked), crosses the threshold, and draws in the meeting.
	 */
	private void reliquary() {
		String step = "46 the reliquary";

		if (c3AtLeast(ChapterThreeStage.AFTERMATH)) {
			note(step, Result.INFO, "the Collector is already behind the player: " + c3Line());
			return;
		}

		if (!c3AtLeast(ChapterThreeStage.RELIQUARY) || story().chapter3.reliquary() == null) {
			note(step, Result.FAIL, "no Reliquary: " + c3Line());
			placeReliquary(step);
		}

		BlockPos heartPos = story().chapter3.reliquary();

		if (heartPos == null) {
			note(step, Result.FAIL, "the Reliquary could not be placed: " + c3Line());
			return;
		}

		BossView before = bossView();

		if (before.stage() != ArenaSession.Stage.WAITING && before.stage() != ArenaSession.Stage.ENDED) {
			note(step, Result.INFO, "the Collector is already met: stage " + before.stage() + ", phase " + before.phase());
			return;
		}

		Vec3 surface = new Vec3(heartPos.getX() + 0.5, surfaceY(heartPos.getX(), heartPos.getZ()), heartPos.getZ() + 0.5);
		note(step, Result.INFO, "Reliquary heart at " + heartPos.toShortString() + ", " + Math.round(flat(surface, here())) + " blocks away");
		select(QuestItems.WANDERERS_JOURNAL);
		travel(step, surface, 16, "the Reliquary");
		boolean session = waitServer(100, p -> BossArenas.sessionOwnedBy(p) != null);

		if (!session) {
			shortcut("opened the Collector's arena session with BossArenas.ensureSession (" + step + ")");
			session = onServer(p -> BossArenas.ensureSession(p) != null);
		}

		ArenaLayout layout = arena();

		if (layout == null) {
			note(step, Result.FAIL, "no arena session at the Reliquary heart (session=" + session + ")");
			return;
		}

		boolean walked = false;
		BlockPos top = layout.descentTop();

		if (top != null) {
			Vec3 topFeet = Vec3.atBottomCenterOf(top);
			BoundingBox r = layout.reliquary();
			BlockPos from = BlockPos.containing(here());
			BoundingBox bounds = new BoundingBox(Math.min(Math.min(r.minX(), top.getX()), from.getX()) - 3, r.minY() - 1,
					Math.min(Math.min(r.minZ(), top.getZ()), from.getZ()) - 3, Math.max(Math.max(r.maxX(), top.getX()), from.getX()) + 3,
					Math.max(Math.max(r.maxY(), top.getY()), from.getY()) + 3, Math.max(Math.max(r.maxZ(), top.getZ()), from.getZ()) + 3);

			if (!walkTo(topFeet, 1.0, 600, false, null)) {
				note(step, Result.INFO, "walk to the top of the spiral descent at " + top.toShortString() + " stopped: " + walkStop);

				if (!walkRoute(step, top, bounds)) {
					// The surface walk's closing teleport can land on a ruin wall with no way down: start the descent from its top.
					shortcut("teleported to the top of the spiral descent (no walkable way there from " + BlockPos.containing(here()).toShortString() + "; "
							+ step + ")");
					teleport(topFeet, yawToward(topFeet, Vec3.atBottomCenterOf(layout.antechamberFloor())));
				}
			}

			walked = flat(topFeet, here()) < 2.5 && walkRoute(step, layout.antechamberFloor(), bounds);
		}

		Vec3 floor = Vec3.atBottomCenterOf(layout.antechamberFloor());

		if (!walked) {
			shortcut("teleported down to the Reliquary antechamber (" + (top == null ? "no descent anchor" : "the spiral descent was not walked") + "; " + step + ")");
			teleport(floor, yawToward(floor, layout.daisFeet()));
		}

		context.waitTicks(20);
		shot("reliquary_antechamber");
		walkTo(layout.daisFeet(), 4, 300, false, () -> sessionAt() != null || bossView().stage() != ArenaSession.Stage.WAITING);
		boolean meeting = waitServer(100, p -> {
			String at = sessionAt(p);
			return at != null && at.startsWith("c3_collector_meet/");
		});
		boolean drew = false;

		if (meeting) {
			context.waitTicks(10);
			shot("collector_meet");
			drew = reply(step, "dialogue.oldrepublic.c3_collector_meet.opt.draw");
		}

		boolean fighting = waitServer(80, p -> BossArenas.sessionOwnedBy(p) != null && BossArenas.sessionOwnedBy(p).isFighting());

		if (!fighting) {
			closeScreen();
			shortcut("started the Collector fight with /oldrepublic boss start (" + step + ")");
			server.runCommand("oldrepublic boss start @p");
			fighting = waitServer(80, p -> BossArenas.sessionOwnedBy(p) != null && BossArenas.sessionOwnedBy(p).isFighting());
		}

		boolean confrontation = waitServer(100, p -> Story.get(p).chapter3.stage().ordinal() >= ChapterThreeStage.CONFRONTATION.ordinal());

		if (!confrontation) {
			c3Stage(step, ChapterThreeStage.CONFRONTATION, "entering the hall did not move the chapter on");
		}

		check(step, walked && meeting && drew && fighting && confrontation, "walked down=" + walked + ", meeting opened on the threshold=" + meeting
				+ ", \"draw\" chosen=" + drew + ", fight on=" + fighting + ", " + c3Line());
	}

	/** Phase 1: heavy attacks (Sneak + Attack), and Force Pull at him during the Hurl tell (it rips the chunk away). */
	private void collectorPhaseOne() {
		String step = "47 collector phase 1";

		if (!bossReady(step, 1)) {
			return;
		}

		int pullSlot = slotOf("force_pull");
		BossView start = bossView();
		float hurt = damageTaken;
		int heavy = 0;
		int tells = 0;
		int pulls = 0;
		boolean inTell = false;
		long until = gameTime() + PHASE_TICKS;

		try {
			for (int i = 0; i < PHASE_TICKS * 2 && gameTime() < until; i++) {
				BossView b = bossView();

				if (b.past(1)) {
					break;
				}

				if (b.talking()) {
					closeScreen();
					tickMe(step);
					continue;
				}

				keepAlive(step);

				if (me().dead()) {
					died(step);
				}

				if (!b.alive() || b.stage() != ArenaSession.Stage.FIGHTING) {
					setForward(false, false);
					tickMe(step);
					continue;
				}

				if (b.hurlTell() && !inTell) {
					inTell = true;
					tells++;
					setForward(false, false);

					if (pullAt(b.pos().add(0, b.height() * 0.6, 0), pullSlot)) {
						pulls++;

						if (pulls == 1) {
							context.waitTicks(3);
							shot("collector_hurl_pull");
						}

						continue;
					}
				} else if (!b.hurlTell()) {
					inTell = false;
				}

				heavy += meleeTick(step, b);
			}
		} finally {
			setForward(false, false);
		}

		BossView end = bossView();
		endPhase(step, 1, String.format(Locale.ROOT, "%d heavy attack(s), %d Hurl tell(s), %d Pull(s) in a tell (force_pull slot %d), Collector health %.0f -> %.0f of %.0f, "
				+ "damage taken %d", heavy, tells, pulls, pullSlot + 1, start.health(), end.health(), end.maxHealth(), Math.round(damageTaken - hurt)));
	}

	/**
	 * Phase 2: the pylons ward him. Pull rips each singing crystal: the dais pylons from the dais, the
	 * gallery pylons from the floor when in sight, else with a Force Jump toward them and a Pull at
	 * the top of the leap. While he is Unsung, heavy attacks.
	 */
	private void collectorPhaseTwo() {
		String step = "48 collector phase 2";

		if (!bossReady(step, 2)) {
			return;
		}

		ArenaLayout layout = arena();
		Vec3 dais = layout == null ? here() : layout.daisFeet();
		int pullSlot = slotOf("force_pull");
		int jumpSlot = slotOf("force_jump");
		BossView start = bossView();
		float hurt = damageTaken;
		int pulls = 0;
		int jumps = 0;
		int heavy = 0;
		int unsung = 0;
		int saberFallbacks = 0;
		int watchers = 0;
		int watchersDown = 0;
		int spots = 0;
		int blocked = 0;
		int missed = 0;
		// Standing cells, by pylon, where a Pull found nothing or the crystal was not in sight after all: the next spot is another one.
		Map<Integer, Set<BlockPos>> avoid = new LinkedHashMap<>();
		boolean wasUnsung = false;
		int lastSinging = start.singing();
		long started = gameTime();
		long lastProgress = started;
		long until = started + PHASE_TICKS;

		try {
			for (int i = 0; i < PHASE_TICKS * 2 && gameTime() < until; i++) {
				BossView b = bossView();

				if (b.past(2)) {
					break;
				}

				if (b.talking()) {
					closeScreen();
					tickMe(step);
					continue;
				}

				keepAlive(step);

				if (me().dead()) {
					died(step);
				}

				if (!b.alive() || b.stage() != ArenaSession.Stage.FIGHTING) {
					setForward(false, false);
					tickMe(step);
					continue;
				}

				UUID watcher = watcherNear(WATCHER_RANGE);

				if (watcher != null) {
					// The bound Watchers (8.3) come down from the gallery for the player: cut them down first.
					setForward(false, false);
					watchers++;

					if (fight(watcher, 600, false, step)) {
						watchersDown++;
					}

					lastProgress = gameTime();
					continue;
				}

				if (b.singing() < lastSinging || (b.unsung() && !wasUnsung)) {
					lastProgress = gameTime();
					// A crystal silenced or the ward down is progress: keep at least 30 s to follow it up, up to half again the phase budget.
					until = Math.min(started + PHASE_TICKS * 3 / 2, Math.max(until, lastProgress + 600));
				}

				lastSinging = b.singing();

				if (b.unsung()) {
					if (!wasUnsung) {
						unsung++;

						if (unsung == 1) {
							shot("collector_unsung");
						}
					}

					wasUnsung = true;
					heavy += meleeTick(step, b);
					continue;
				}

				wasUnsung = false;
				Pylon pylon = nearestSinging();

				if (pylon == null) {
					heavy += meleeTick(step, b);
					continue;
				}

				Me me = me();
				boolean gallery = pylon.crystal().y - dais.y > 3.0;
				double reach = me.eye().distanceTo(pylon.crystal());
				Set<BlockPos> badSpots = avoid.computeIfAbsent(pylon.index(), k -> new HashSet<>());

				// Only from the ground: mid-jump the eye can clear an edge that hides the crystal again by the press.
				Vec3 aimPoint = reach <= 11.0 && me.onGround() && !badSpots.contains(BlockPos.containing(me.pos())) ? pullPoint(pylon.crystal()) : null;

				if (aimPoint != null && pullReady() && pullSlot >= 0) {
					BlockPos from = BlockPos.containing(me.pos());
					String outcome = pullCrystal(step, pylon, pullSlot);
					boolean landed = outcome.startsWith("landed");
					pulls++;

					for (int k = 0; k < 6; k++) {
						tickMe(step);
					}

					note(step, Result.INFO, String.format(Locale.ROOT, "pulled at pylon P%d from %.1f blocks at %s: %s; crystal now %s", pylon.index() + 1, reach,
							from.toShortString(), outcome, pylonState(pylon.index())));

					if (!landed) {
						// The line from here is too fine (a grazing one over an edge): the next try is from another spot.
						missed++;
						badSpots.add(from);
					}

					if (pulls == 1) {
						shot("collector_pylon_pull");
					}

					continue;
				}

				if (gameTime() - lastProgress > 600 && pylon.focus() != null) {
					// No crystal silenced for 30 s: cut it with the saber (fight teleports next to it, logged, when it cannot be reached).
					saberFallbacks++;
					UUID focus = pylon.focus();
					int index = pylon.index();
					note(step, Result.INFO, "no pylon silenced for 600 ticks; attacking pylon P" + (index + 1) + "'s crystal with the saber");
					fight(focus, 300, false, step, () -> pylonState(index) != PylonCrystal.SINGING);
					lastProgress = gameTime();
					continue;
				}

				// Not in sight from here. Under a gallery its edge hides the crystal; P1 and P2 stand on the wings, three blocks above the
				// hall floor, whose edge hides them from the floor near the dais. Stand where the crystal is in sight and in reach (on the
				// bot's own level when there is such a spot), as a player steps back to see it, rather than walking into the dais front
				// under him.
				Vec3 spot = pullSpot(pylon.crystal(), badSpots);

				if (spot != null) {
					if (flat(me.pos(), spot) > 0.8) {
						spots++;

						if (!walkTo(spot, 0.5, 200, false, null)) {
							shortcut("teleported to a spot in sight of a pylon crystal after the walk stopped (" + step + ")");
							teleport(spot, yawToward(spot, pylon.crystal()));
						}
					} else if (blocked < 8 && pullReady() && pullPoint(pylon.crystal()) == null) {
						// Pull is ready but the walk stopped short of the spot and an edge is in the way: step onto the spot itself
						// (a few times a phase; after that the saber fallback takes over).
						blocked++;

						if (!walkTo(spot, 0.15, 60, false, null) || pullPoint(pylon.crystal()) == null) {
							shortcut("placed on the spot in sight of a pylon crystal (" + step + ")");
							teleport(spot, yawToward(spot, pylon.crystal()));
							tickMe(step);
						}

						if (pullPoint(pylon.crystal()) == null) {
							badSpots.add(BlockPos.containing(spot));
							note(step, Result.INFO, String.format(Locale.ROOT, "pylon P%d's crystal is not in sight from the spot at %s (eye %.2f, %.1f blocks away)",
									pylon.index() + 1, BlockPos.containing(spot).toShortString(), me().eye().y, me().eye().distanceTo(pylon.crystal())));
						}
					} else {
						// There already: wait for Pull to be ready (or to land from a jump).
						setForward(false, false);
						tickMe(step);
					}

					continue;
				}

				if (gallery && jumpSlot >= 0 && me.onGround() && flat(me.pos(), pylon.crystal()) <= 7.0 && jumpReady() && pullReady()) {
					setForward(false, false);
					jumps++;

					if (jumpAndPull(step, pylon, jumpSlot, pullSlot)) {
						pulls++;
					}

					continue;
				}

				Vec3 goal = gallery ? new Vec3(pylon.crystal().x, me.pos().y, pylon.crystal().z) : dais;
				stepToward(step, goal, gallery ? 4.0 : 2.0);
			}
		} finally {
			setForward(false, false);
		}

		BossView end = bossView();
		endPhase(step, 2, String.format(Locale.ROOT, "%d ticks, %d pull(s) at crystals (slot %d, %d that did not land), %d move(s) to a spot in sight of a crystal (%d onto "
				+ "the spot itself), %d Force Jump(s) (slot %d), %d saber fallback(s), %d Watcher fight(s) (%d cut down), Unsung %d time(s), %d heavy attack(s), "
				+ "Collector health %.0f -> %.0f, damage taken %d", gameTime() - started, pulls, pullSlot + 1, missed, spots, blocked, jumps, jumpSlot + 1, saberFallbacks,
				watchers, watchersDown, unsung, heavy, start.health(), end.health(), Math.round(damageTaken - hurt)));
	}

	/**
	 * Phase 3: for each Surge (an uninterruptible 30-tick gather) the bot sprints behind the nearest
	 * broken column so it breaks his line; held in the Grip, it casts Push, which breaks the hold.
	 * Otherwise heavy attacks.
	 */
	private void collectorPhaseThree() {
		String step = "49 collector phase 3";

		if (!bossReady(step, 3)) {
			return;
		}

		ArenaLayout layout = arena();
		List<BlockPos> columns = layout == null ? List.of() : layout.columns();
		int pushSlot = slotOf(ForcePush.ABILITY_ID);
		BossView start = bossView();
		float hurt = damageTaken;
		int heavy = 0;
		int surges = 0;
		int sheltered = 0;
		int grips = 0;
		int broken = 0;
		boolean inSurge = false;
		boolean wasHeld = false;
		long until = gameTime() + PHASE_TICKS;

		try {
			for (int i = 0; i < PHASE_TICKS * 2 && gameTime() < until; i++) {
				BossView b = bossView();

				if (b.past(3)) {
					break;
				}

				if (b.talking()) {
					closeScreen();
					tickMe(step);
					continue;
				}

				keepAlive(step);

				if (me().dead()) {
					died(step);
				}

				if (!b.alive() || b.stage() != ArenaSession.Stage.FIGHTING) {
					setForward(false, false);
					tickMe(step);
					continue;
				}

				if (b.held()) {
					setForward(false, false);

					if (!wasHeld) {
						grips++;
						wasHeld = true;

						if (grips == 1) {
							shot("collector_grip");
						}
					}

					if (pushSlot >= 0 && pushReady()) {
						aimAt(b.pos().add(0, b.height() * 0.6, 0));
						pushKey(pushSlot);

						if (waitServer(10, p -> !Holds.isHeld(p))) {
							broken++;
						}
					} else {
						tickMe(step);
					}

					continue;
				}

				wasHeld = false;

				if (b.move() == CollectorCombatGoal.Move.SURGE) {
					if (!inSurge) {
						inSurge = true;
						surges++;

						if (takeCover(step, b, columns)) {
							sheltered++;
						}
					} else {
						setForward(false, false);
						tickMe(step);
					}

					continue;
				}

				inSurge = false;
				UUID watcher = watcherNear(WATCHER_RANGE);

				if (watcher != null) {
					setForward(false, false);
					fight(watcher, 400, false, step, () -> bossView().move() == CollectorCombatGoal.Move.SURGE || bossView().held());
					continue;
				}

				heavy += meleeTick(step, b);
			}
		} finally {
			setForward(false, false);
		}

		BossView end = bossView();
		endPhase(step, 3, String.format(Locale.ROOT, "%d Surge(s), %d with a column breaking his line, %d Grip(s), %d broken by Push (slot %d), %d heavy attack(s), "
				+ "Collector health %.0f -> %.0f, damage taken %d", surges, sheltered, grips, broken, pushSlot + 1, heavy, start.health(), end.health(),
				Math.round(damageTaken - hurt)));
	}

	/** At 10% he yields; his dialogue opens and the bot chooses Spare (the Jedi answer). */
	private void yieldAndSpare() {
		String step = "50 yield and spare";

		if (c3AtLeast(ChapterThreeStage.AFTERMATH)) {
			note(step, Result.INFO, "the Collector is already resolved: " + c3Line());
			return;
		}

		boolean yielded = waitServer(100, p -> {
			ArenaSession s = BossArenas.sessionOwnedBy(p);
			return s != null && (s.stage() == ArenaSession.Stage.YIELDED || s.stage() == ArenaSession.Stage.RESOLVING || s.stage() == ArenaSession.Stage.ENDED);
		});

		if (!yielded) {
			BossView b = bossView();
			note(step, Result.FAIL, "he has not yielded: stage " + b.stage() + ", phase " + b.phase() + String.format(Locale.ROOT, ", health %.0f", b.health()));
			shortcut("brought the Collector to his yield with /oldrepublic boss phase 4 (" + step + ")");
			server.runCommand("oldrepublic boss phase @p 4");
			yielded = waitServer(100, p -> BossArenas.sessionOwnedBy(p) != null && BossArenas.sessionOwnedBy(p).stage() == ArenaSession.Stage.YIELDED);
		}

		boolean asked = waitServer(100, p -> {
			String at = sessionAt(p);
			return at != null && at.startsWith("c3_collector_yield/");
		});

		if (!asked && bossView().stage() == ArenaSession.Stage.YIELDED && bossView().vasch() != null) {
			// The dialogue opens as he yields, often while the phase 3 loop still closes screens: right-click him, as a player would.
			note(step, Result.INFO, "no yield dialogue open after the yield; right-clicking him");
			asked = talkTo(step, bossView().vasch()) && sessionAt() != null && sessionAt().startsWith("c3_collector_yield/");
		}

		if (!asked && bossView().stage() == ArenaSession.Stage.YIELDED) {
			shortcut("opened the Collector's yield dialogue with Dialogues.open (right-clicking him did not open it; " + step + ")");
			// No speaker: a far speaker would end the dialogue at once (ArenaSession passes him only when near).
			onServer(p -> Dialogues.open(p, OrrenVasch.YIELD_DIALOGUE, null));
			asked = waitServer(20, p -> {
				String at = sessionAt(p);
				return at != null && at.startsWith("c3_collector_yield/");
			});
		}

		int alignmentBefore = alignment();
		boolean spared = false;

		if (asked) {
			context.waitTicks(10);
			shot("collector_yield");
			spared = reply(step, "dialogue.oldrepublic.c3_collector_yield.opt.spare_jedi");
		}

		boolean after = waitServer(600, p -> Story.get(p).chapter3.stage().ordinal() >= ChapterThreeStage.AFTERMATH.ordinal());

		if (!after && bossView().stage() == ArenaSession.Stage.YIELDED) {
			shortcut("resolved the yielded Collector as spared with BossArenas.resolve (" + step + ")");
			onServer(p -> {
				BossArenas.resolve(p, BossOutcome.SPARED);
				return null;
			});
			after = waitServer(600, p -> Story.get(p).chapter3.stage().ordinal() >= ChapterThreeStage.AFTERMATH.ordinal());
		}

		if (!after) {
			c3Stage(step, ChapterThreeStage.AFTERMATH, "the Spare did not move the chapter on");
		}

		context.waitTicks(20);
		shot("collector_spared");
		check(step, yielded && spared && after, "yielded=" + yielded + ", yield dialogue=" + asked + ", Spare chosen=" + spared + ", " + c3Line() + ", alignment "
				+ alignmentBefore + " -> " + alignment());
	}

	/** The vault opens after the scene: the hoard plinth's dialogue, "Return it to the Order", Yes. */
	private void hoard() {
		String step = "51 the hoard";
		StoryData d = story();

		if (d.hasFlag(ChapterThreeRules.HOARD_DECIDED)) {
			note(step, Result.INFO, "the hoard is already decided: " + c3Line());
			return;
		}

		if (d.chapter3.stage() != ChapterThreeStage.AFTERMATH) {
			note(step, Result.FAIL, "not in the aftermath: " + c3Line());
			return;
		}

		ArenaLayout layout = arena();
		BlockPos plinth = layout == null ? null : layout.plinth();

		if (layout == null || plinth == null) {
			note(step, Result.FAIL, "no hoard plinth (arena " + (layout != null) + ")");
			return;
		}

		boolean vault = waitServer(600, p -> BossArenas.isVaultOpen(p.level(), plinth));
		BlockPos stand = onServer(p -> standBeside(p.level(), plinth));
		boolean walked = stand != null && walkRoute(step, stand, layout.reliquary().inflatedBy(2));

		if (!walked) {
			Vec3 spot = stand != null ? Vec3.atBottomCenterOf(stand) : Vec3.atBottomCenterOf(plinth.above());
			shortcut("teleported beside the hoard plinth (" + step + ")");
			teleport(spot, yawToward(spot, Vec3.atCenterOf(plinth)));
		}

		boolean on = useBlock(plinth, Vec3.atCenterOf(plinth));
		boolean open = waitServer(40, p -> {
			String at = sessionAt(p);
			return at != null && at.startsWith("archive_hoard/");
		});

		if (!open) {
			closeScreen();
			shortcut("opened the hoard dialogue with HolocronService.openArchive (the right-click did not; " + step + ")");
			onServer(p -> {
				HolocronService.openArchive(p, plinth);
				return null;
			});
			open = waitServer(20, p -> {
				String at = sessionAt(p);
				return at != null && at.startsWith("archive_hoard/");
			});
		}

		boolean chose = false;

		if (open) {
			context.waitTicks(10);
			shot("archive_hoard");
			chose = reply(step, "dialogue.oldrepublic.archive_hoard.opt.return_jedi") && reply(step, "dialogue.oldrepublic.common.opt.yes");
		}

		boolean decided = waitServer(40, p -> Story.get(p).hasFlag(ChapterThreeRules.HOARD_DECIDED));
		check(step, vault && on && open && chose && decided, "vault open=" + vault + ", walked to the plinth=" + walked + ", crosshair on it=" + on
				+ ", dialogue open=" + open + ", Return chosen=" + chose + ", decided=" + decided + ", " + c3Line());
	}

	/** Reports to Saelen at the camp: the chapter completes. */
	private void reportToMentor() {
		String step = "52 report";
		StoryData d = story();
		UUID jedi = d.jediMentor;

		if (d.hasFlag(ChapterThreeRules.REPORT_DONE) || d.chapter3.stage() == ChapterThreeStage.COMPLETE) {
			note(step, Result.INFO, "already reported: " + c3Line());
			return;
		}

		if (jedi == null || d.chapter3.stage() != ChapterThreeStage.AFTERMATH) {
			note(step, Result.FAIL, "nothing to report: " + c3Line() + ", mentor " + jedi);
			return;
		}

		backToCamp(step);
		waitServer(100, p -> p.level().getEntity(jedi) instanceof MentorEntity);
		String before = standing();
		int replies = talkAndChoose(step, jedi, "c3_report_dialogue", "dialogue.oldrepublic.mentor_jedi.opt.c3_report");
		boolean complete = waitServer(60, p -> Story.get(p).chapter3.stage() == ChapterThreeStage.COMPLETE);
		check(step, replies == 1 && complete, "reported: replies made " + replies + "/1, " + c3Line() + "; before " + before + "; now " + standing());

		if (!complete) {
			c3Stage(step, ChapterThreeStage.COMPLETE, "the report did not go through");
		}
	}

	/** Records the Chapter Three state: complete, Republic Trusted or better, Force Sense, two holocrons. */
	private void chapterThreeState() {
		String step = "53 chapter three state";
		closeScreen();
		context.getInput().pressKey(InputConstants.KEY_J);
		boolean journal = waitClient(40, mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("JournalScreen"));
		context.waitTicks(10);
		shot("journal_chapter_three");
		closeScreen();
		StoryData d = story();
		ReputationTier tier = onServer(p -> Identity.tier(p, Faction.REPUBLIC));
		boolean sense = onServer(p -> SkillService.data(p).owns(SkillIds.FORCE_SENSE));
		int holocrons = onServer(p -> HolocronService.attunedCount(p, null));
		List<String> attuned = onServer(p -> JournalEntries.HOLOCRON_IDS.stream().filter(id -> HolocronService.hasAttuned(p, id)).toList());
		ForceData f = data();
		check(step, d.chapter3.stage() == ChapterThreeStage.COMPLETE && tier.ordinal() >= ReputationTier.TRUSTED.ordinal() && sense && holocrons >= 2,
				"J opened the journal=" + journal + ", " + c3Line() + ", Republic " + onServer(p -> Identity.reputation(p, Faction.REPUBLIC)) + " (" + tier
						+ "), force_sense owned=" + sense + ", holocrons " + holocrons + " " + attuned + ", " + standing() + ", level " + f.level
						+ ", total damage taken " + Math.round(damageTaken));
		server.runCommand("oldrepublic c3 status @p");
		server.runCommand("oldrepublic holocron status @p");
	}

	/** Rank 3's requirements: whatever play left short is set with logged commands (level, trust, reputation, alignment, holocrons, contracts). */
	private void rankThreeRequirements() {
		String step = "54 rank three";

		if (story().hasFlag(ChapterThreeRules.RANK3_DONE)) {
			note(step, Result.INFO, "already knighted");
			return;
		}

		List<ChapterThreeRules.Requirement> before = onServer(p -> RankThree.requirements(p, Progression.get(p).level));
		note(step, Result.INFO, "from play: " + describe(before));

		for (ChapterThreeRules.Requirement r : before) {
			if (r.met()) {
				continue;
			}

			String why = r.id() + " " + r.have() + "/" + r.need() + " from play";

			switch (r.id()) {
				case "trial" -> c3Stage(step, ChapterThreeStage.COMPLETE, "the trial requirement (" + why + ")");
				case "level" -> {
					shortcut("set the level to " + r.need() + " with /oldrepublic setlevel for rank 3 (" + why + ")");
					server.runCommand("execute as @p run oldrepublic setlevel " + r.need());
				}
				case "trust" -> {
					shortcut("set Saelen's trust to " + r.need() + " with /oldrepublic identity trust for rank 3 (" + why + ")");
					server.runCommand("oldrepublic identity trust @p jedi set " + r.need());
				}
				case "reputation" -> {
					shortcut("set the Jedi Order reputation to " + r.need() + " with /oldrepublic identity reputation for rank 3 (" + why + ")");
					server.runCommand("oldrepublic identity reputation @p " + Faction.JEDI_ORDER.id() + " set " + r.need());
				}
				case "alignment" -> {
					shortcut("set the alignment to " + r.need() + " with /oldrepublic identity alignment for rank 3 (" + why + ")");
					server.runCommand("oldrepublic identity alignment @p set " + r.need());
				}
				case "holocrons" -> {
					for (String id : JournalEntries.HOLOCRON_IDS) {
						if (onServer(p -> HolocronService.attunedCount(p, HolocronKind.JEDI)) >= r.need()) {
							break;
						}

						if (id.startsWith("jedi_") && !onServer(p -> HolocronService.hasAttuned(p, id))) {
							shortcut("attuned " + id + " with /oldrepublic holocron grant for rank 3 (" + why + ")");
							server.runCommand("oldrepublic holocron grant @p " + id);
							context.waitTicks(3);
						}
					}
				}
				case "contracts" -> {
					if (activeMission() != null) {
						shortcut("abandoned the active mission with /oldrepublic mission abandon to run contracts for rank 3");
						server.runCommand("oldrepublic mission abandon @p");
						context.waitTicks(3);
					}

					for (int i = 0; i < r.need() + 1 && onServer(p -> Missions.ownSideContracts(p)) < r.need(); i++) {
						shortcut("completed a patrol_intercept contract (Republic, own side) with /oldrepublic mission start and complete for rank 3 (" + why + ")");
						server.runCommand("oldrepublic mission start @p patrol_intercept");
						context.waitTicks(3);
						server.runCommand("oldrepublic mission complete @p");
						context.waitTicks(3);
					}
				}
				default -> note(step, Result.INFO, "no shortcut for the requirement " + why);
			}

			context.waitTicks(3);
		}

		String blocker = onServer(p -> RankThree.blocker(p));
		List<ChapterThreeRules.Requirement> after = onServer(p -> RankThree.requirements(p, Progression.get(p).level));
		check(step, blocker == null, "now " + describe(after) + (blocker == null ? ", the Knighting is open" : ", blocked: " + blocker));
	}

	/** The Knighting: Saelen's Chapter Three topics, "I am ready", then kneel. */
	private void knighting() {
		String step = "55 knighting";
		StoryData d = story();
		UUID jedi = d.jediMentor;

		if (d.hasFlag(ChapterThreeRules.RANK3_DONE)) {
			note(step, Result.INFO, "already knighted: rank " + onServer(p -> Identity.rank(p)));
			return;
		}

		if (jedi == null) {
			note(step, Result.FAIL, "no mentor: " + c3Line());
			return;
		}

		backToCamp(step);
		waitServer(100, p -> p.level().getEntity(jedi) instanceof MentorEntity);
		String blocker = onServer(p -> RankThree.blocker(p));
		int rankBefore = onServer(p -> Identity.rank(p));
		String before = standing();
		int replies = talkAndChoose(step, jedi, "knighting_dialogue", "dialogue.oldrepublic.mentor_jedi.opt.c3", "dialogue.oldrepublic.mentor_jedi.opt.c3_ready",
				"dialogue.oldrepublic.mentor_jedi.opt.c3_kneel");
		boolean knighted = waitServer(60, p -> Story.get(p).hasFlag(ChapterThreeRules.RANK3_DONE));
		context.waitTicks(30);
		shot("knighted");
		int rankAfter = onServer(p -> Identity.rank(p));
		check(step, replies == 3 && knighted && rankAfter >= 3, "replies made " + replies + "/3 (blocker before: " + blocker + "), knighted=" + knighted + ", rank "
				+ rankBefore + " -> " + rankAfter + "; before " + before + "; now " + standing());
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Four (spec MILESTONE_4.md 17.3, items 1 to 7: the Jedi run)
	// ---------------------------------------------------------------------------------------------

	/** Ticks each phase of the Kaast duel may take before it is moved on with /oldrepublic kaast phase (spec 17.3). */
	private static final int KAAST_PHASE_TICKS = 2400;
	/** Chapter Four walks to places at most this far; further, or on another world, is a logged teleport (spec 17.3). */
	private static final double C4_WALK_LIMIT = 128.0;
	private static final String DLG = "dialogue.oldrepublic.";
	private static final String SHIP_LAUNCH = "screen.oldrepublic.ship.nav.launch";
	private static final String SHIP_FUEL = "screen.oldrepublic.ship.fuel_button";
	private static final String SHIP_FIT = "screen.oldrepublic.ship.socket.fit";
	private static final String BENCH_CRAFT = "screen.oldrepublic.bench.craft";
	private static final String BENCH_CHOOSE = "screen.oldrepublic.bench.choose";
	private static final String BENCH_CONFIRM = "screen.oldrepublic.bench.confirm";
	/** The tag of the Heartstone trial's spawns ({@code WorldArcs.TRIAL_TAG}, package-private there). */
	private static final String C4_TRIAL_TAG = "oldrepublic.c4_trial";

	/** The Engineering Bench the run placed for Chapter Four, once placed. */
	private @Nullable BlockPos c4Bench;
	/** Kyber crystals carried before the Singing Hollow, so the second crystal shows; -1 before it is counted. */
	private int crystalsBefore = -1;
	/** The Chapter Four step running now (for helpers that log a shortcut without being handed the step). */
	private String c4Step = "";

	/** Server view of the player's ship for one tick (box and body id only while its body is loaded). */
	private record ShipView(ShipState state, int hull, int fuel, int tank, boolean flying, ResourceKey<Level> dimension, Vec3 pos, @Nullable AABB box,
			int bodyId, boolean aboard, @Nullable Destination to) {
	}

	/** Server view of the Kaast duel for one tick ({@code session} false when the player has no fight running). */
	private record KaastView(boolean session, int phase, boolean transitioning, boolean yielded, boolean over, @Nullable UUID id, int netId, Vec3 pos,
			double height, int nest, boolean windingUp, boolean staggered, @Nullable Vec3 mine, float health, float maxHealth) {
	}

	/** The top-left corner of an open container screen, in GUI coordinates. */
	private record Origin(int x, int y) {
	}

	/**
	 * Runs one Chapter Four step with one retry after a death, like {@link #step}. Unlike the earlier
	 * chapters nothing a step does escapes: a step that gets stuck, throws or fails a link is recorded
	 * as a FAIL and the next step still runs (each checks the story state first and skips what is
	 * done), so the summary always prints and a Chapter Four failure does not fail the run.
	 */
	private void chapterFour(String name, Runnable body) {
		boolean wasStuck = stuck;
		c4Step = name;

		try {
			for (int attempt = 1; ; attempt++) {
				try {
					note(name, Result.INFO, "where (" + (attempt == 1 ? "start" : "retry " + attempt) + "): " + c4Where());
					body.run();

					if (context.computeOnClient(mc -> mc.player != null && mc.player.isDeadOrDying())) {
						died(name);
					}

					note(name, Result.INFO, "where (end): " + c4Where());
					return;
				} catch (Died e) {
					if (attempt >= 2) {
						note(name, Result.FAIL, "died again on the retry: " + e.getMessage());
						return;
					}

					note(name, Result.INFO, "retrying the step after dying");
				}
			}
		} catch (Stuck e) {
			note(name, Result.FAIL, "Chapter Four goes on after: " + e.getMessage());
		} catch (RuntimeException | LinkageError | AssertionError e) {
			note(name, Result.FAIL, "error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			LOG.error("PLAYTHROUGH-LOG: Chapter Four step failed", e);
			tryShot("error");
		} finally {
			stuck = wasStuck;

			try {
				releaseAll();
				closeScreen();
			} catch (RuntimeException | AssertionError e) {
				LOG.warn("PLAYTHROUGH-LOG: could not tidy up after a Chapter Four step", e);
			}
		}
	}

	/**
	 * 17.3 item 1: Chapter Three done marks the player (MARKED); a sleep makes the seeker due, and it
	 * plays at the next safe moment on the home world; its end is SEEKER and the entry A Price.
	 */
	private void markedAndSeeker() {
		String step = "56 marked and the seeker";
		boolean marked = waitServer(100, p -> Story.get(p).chapter4.stage() != ChapterFourStage.NOT_STARTED);

		if (c4AtLeast(ChapterFourStage.SEEKER)) {
			note(step, Result.INFO, "the seeker is already behind the player: " + c4Line());
			return;
		}

		int[] balance = onServer(p -> new int[] { ChapterFourService.balance().waitTicks, ChapterFourService.balance().minSleepTicks });
		note(step, marked ? Result.INFO : Result.FAIL, "after Chapter Three: " + c4Line() + "; the seeker comes after " + balance[0]
				+ " ticks of waiting or a sleep of at least " + balance[1] + " ticks");

		if (!marked) {
			c4Stage(step, ChapterFourStage.MARKED, "Chapter Four did not begin after Chapter Three");
		}

		homeWorld(step);
		backToCamp(step);
		clearStrays(step);
		boolean filmed = false;

		for (int attempt = 1; attempt <= 2 && !c4AtLeast(ChapterFourStage.SEEKER); attempt++) {
			if (!onServer(p -> Story.get(p).hasFlag(ChapterFourRules.SEEKER_DUE))) {
				shortcut("gave a white bed and set the time to night for the sleep that makes the seeker due (the " + balance[0] + "-tick wait is not waited out)");
				server.runCommand("give @a minecraft:white_bed");
				server.runCommand("time set 13000");
				context.waitTicks(5);
				BlockPos bed = placeBlock(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("white_bed")), state -> state.is(BlockTags.BEDS), true);

				if (bed == null) {
					note(step, Result.FAIL, "attempt " + attempt + ": could not place the bed by right-clicking the ground");
				} else if (sleepIn(step, attempt, bed)) {
					context.waitTicks(40);
					tryShot("c4_marked_sleep");
					waitServer(300, p -> !p.isSleeping());
				}
			}

			boolean due = waitServer(40, p -> Story.get(p).hasFlag(ChapterFourRules.SEEKER_DUE) || Story.get(p).chapter4.stage() != ChapterFourStage.MARKED);
			note(step, due ? Result.INFO : Result.FAIL, "attempt " + attempt + ": seeker due after the sleep=" + due + ", " + c4Line());

			// The scene waits for a safe moment: on the ground, not hurt lately, no hostile within 24 blocks.
			for (int t = 0; t < 40 && !c4AtLeast(ChapterFourStage.SEEKER); t++) {
				if (t % 5 == 0) {
					clearStrays(step);
				}

				waitServer(20, p -> SeekerScene.playing(p) || Story.get(p).chapter4.stage() != ChapterFourStage.MARKED);

				if (!filmed && onServer(p -> SeekerScene.playing(p))) {
					context.waitTicks(30);
					tryShot("c4_seeker");
					filmed = true;
				}
			}
		}

		boolean seeker = c4AtLeast(ChapterFourStage.SEEKER);

		if (!seeker) {
			shortcut("played the seeker scene with /oldrepublic c4 seeker (it did not play after the sleep; " + step + ")");
			server.runCommand("oldrepublic c4 seeker @p");
			seeker = waitServer(400, p -> Story.get(p).chapter4.stage().ordinal() >= ChapterFourStage.SEEKER.ordinal());
		}

		if (!seeker) {
			seeker = c4Stage(step, ChapterFourStage.SEEKER, "the seeker scene did not end in SEEKER");
		}

		boolean entry = data().hasFlag(JournalEntries.entryFlag(JournalEntries.A_PRICE));
		check(step, seeker && filmed && entry, "seeker seen on screen=" + filmed + ", " + c4Line() + ", journal a_price=" + entry);
	}

	/** 17.3 item 1: Saelen's briefing (droid, what now, wake): REPAIRS, the bench item, the hulk near the camp and Greenwatch unlocked. */
	private void briefing() {
		String step = "57 briefing";
		StoryData d = story();
		UUID jedi = d.jediMentor;

		if (d.hasFlag(ChapterFourRules.BRIEFED) || d.chapter4.stage().ordinal() >= ChapterFourStage.REPAIRS.ordinal()) {
			note(step, Result.INFO, "already briefed: " + c4Line());
		} else if (jedi == null || d.chapter4.stage() != ChapterFourStage.SEEKER) {
			note(step, Result.FAIL, "no briefing to have: mentor " + jedi + ", " + c4Line());

			if (d.chapter4.stage() == ChapterFourStage.SEEKER) {
				c4Stage(step, ChapterFourStage.REPAIRS, "there is no mentor to brief the player");
			}
		} else {
			waitServer(400, p -> !SeekerScene.playing(p));
			homeWorld(step);
			backToCamp(step);
			waitServer(100, p -> p.level().getEntity(jedi) instanceof MentorEntity);
			int replies = talkAndChoose(step, jedi, "c4_briefing", DLG + "c4_hub_saelen.opt.droid", DLG + "c4_hub_saelen.opt.what_now",
					DLG + "c4_hub_saelen.opt.wake");
			boolean repairs = waitServer(60, p -> Story.get(p).chapter4.stage().ordinal() >= ChapterFourStage.REPAIRS.ordinal());
			note(step, replies == 3 && repairs ? Result.INFO : Result.FAIL, "briefing: replies made " + replies + "/3, " + c4Line());

			if (!repairs) {
				c4Stage(step, ChapterFourStage.REPAIRS, "the briefing did not go through");
			}
		}

		boolean hulk = waitServer(200, p -> ShipRegistry.of(p.level().getServer(), p.getUUID()).isPresent());

		if (!hulk) {
			homeWorld(step);
			shortcut("placed the hulk with /oldrepublic ship hulk (REPAIRS placed none; " + step + ")");
			server.runCommand("oldrepublic ship hulk @p");
			hulk = waitServer(100, p -> ShipRegistry.of(p.level().getServer(), p.getUUID()).isPresent());
		}

		if (hulk && standAtShip(step)) {
			ShipView s = ship();

			if (s != null && s.box() != null) {
				aimAt(s.box().getCenter());
				context.waitTicks(10);
				tryShot("c4_hulk");
			}
		}

		boolean bench = count(benchItem()) > 0 || c4Bench != null;
		boolean greenwatch = onServer(p -> TravelService.isUnlocked(p, Destination.GREENWATCH));
		check(step, c4AtLeast(ChapterFourStage.REPAIRS) && hulk && bench && greenwatch, c4Line() + ", " + shipLine() + ", bench held=" + bench
				+ ", Greenwatch unlocked=" + greenwatch);
	}

	/** 17.3 item 1: the bench placed by the camp and Artifice chosen on its Disciplines tab (card, Choose, Confirm). */
	private void benchAndArtifice() {
		String step = "58 bench and artifice";
		boolean placed = ensureBench(step);

		if (onServer(p -> Crafting.has(p, Discipline.ARTIFICE))) {
			check(step, placed, "Artifice is already chosen; bench at " + shortPos(c4Bench));
			return;
		}

		boolean chosen = false;

		if (placed && openBench(step)) {
			benchTab(EngineeringBenchMenu.TAB_DISCIPLINES);
			Origin o = benchOrigin();

			if (o == null) {
				note(step, Result.INFO, "the bench screen shows none of its bottom buttons");
			} else {
				// The discipline cards stand in a column on the left (EngineeringBenchScreen CARD_X, cardY).
				clickAt(o.x() + 8 + 60, o.y() + 35 + Discipline.ARTIFICE.ordinal() * 17 + 8);
				context.waitTicks(5);
				tryShot("c4_bench_disciplines");

				if (clickButtonMouse(BENCH_CHOOSE)) {
					context.waitTicks(5);
					boolean confirm = clickButtonMouse(BENCH_CONFIRM);
					chosen = waitServer(40, p -> Crafting.has(p, Discipline.ARTIFICE));
					note(step, chosen ? Result.INFO : Result.FAIL, "clicked the Artifice card, Choose and Confirm (confirm shown=" + confirm + "): chosen=" + chosen);
				} else {
					note(step, Result.INFO, "Choose is not enabled after clicking the Artifice card: " + describeButtons(BENCH_CHOOSE));
				}
			}
		}

		closeScreen();

		if (!chosen) {
			shortcut("chose Artifice with /oldrepublic crafting choose (the Disciplines tab did not choose it; " + step + ")");
			server.runCommand("oldrepublic crafting choose @p artifice");
			waitServer(20, p -> Crafting.has(p, Discipline.ARTIFICE));
		}

		check(step, placed && chosen, "bench placed by right-click at " + shortPos(c4Bench) + ", Artifice chosen on the bench=" + chosen + ", has Artifice="
				+ onServer(p -> Crafting.has(p, Discipline.ARTIFICE)));
	}

	/**
	 * 17.3 item 2: the parts. The plates for the hull socket and the parts come from a logged /give
	 * (no story drop gives durasteel), as do the vanilla ingredients; the power core, the nav computer
	 * and three batches of hyperfuel cells (for the later jumps) are crafted at the bench with clicks.
	 */
	private void shipParts() {
		String step = "59 ship parts";
		ShipView s = ship();

		if (s == null) {
			note(step, Result.FAIL, "no ship: " + c4Line());
			return;
		}

		int done = sockets();
		boolean needCore = (done & 1 << ShipRules.SOCKET_CORE_INDEX) == 0 && count(M4Items.POWER_CORE) == 0;
		boolean needNav = (done & 1 << ShipRules.SOCKET_NAV_INDEX) == 0 && count(M4Items.NAV_COMPUTER) == 0;
		int[] hull = onServer(p -> {
			ShipRecord r = ShipRegistry.of(p.level().getServer(), p.getUUID()).orElse(null);
			return r == null ? new int[] { 0, 0 } : new int[] { ShipRules.plates(r), ShipRules.platesNeeded(ShipBalance.get()) };
		});
		int hullPlates = (done & 1 << ShipRules.SOCKET_HULL) != 0 ? 0 : Math.max(0, hull[1] - hull[0]);
		int fuelBatches = 3;
		giveMissing(step, M3Items.DURASTEEL_PLATE, hullPlates + (needCore ? 4 : 0) + (needNav ? 2 : 0) + fuelBatches,
				"durasteel plates for the hull socket (" + hullPlates + "), the power core, the nav computer and the fuel cells");
		giveMissing(step, Items.REDSTONE, (needCore ? 4 : 0) + (needNav ? 4 : 0) + 4 * fuelBatches, "redstone for the bench's ship parts");
		giveMissing(step, Items.GLOWSTONE_DUST, fuelBatches, "glowstone dust for the hyperfuel cells");

		if (needCore) {
			giveMissing(step, Items.COPPER_INGOT, 2, "copper for the power core");
			giveMissing(step, Items.GOLD_INGOT, 1, "gold for the power core");
		}

		if (needNav) {
			giveMissing(step, Items.GLASS_PANE, 1, "a glass pane for the nav computer");
			giveMissing(step, Items.EMERALD, 1, "an emerald for the nav computer");
		}

		boolean bench = ensureBench(step);
		int core = needCore && bench ? craftAtBench(step, "power_core", M4Items.POWER_CORE, 1, true) : 0;
		int nav = needNav && bench ? craftAtBench(step, "nav_computer", M4Items.NAV_COMPUTER, 1, false) : 0;
		int fuel = bench ? craftAtBench(step, "hyperfuel_cell", M4Items.HYPERFUEL_CELL, fuelBatches, false) : 0;
		closeScreen();

		if (needCore && core == 0) {
			giveMissing(step, M4Items.POWER_CORE, 1, "the power core (the bench did not craft it)");
		}

		if (needNav && nav == 0) {
			giveMissing(step, M4Items.NAV_COMPUTER, 1, "a blank nav computer (the bench did not craft it)");
		}

		check(step, (!needCore || core == 1) && (!needNav || nav == 1) && fuel == fuelBatches, "crafted at the bench: power core " + core + "/" + (needCore ? 1 : 0)
				+ ", nav computer " + nav + "/" + (needNav ? 1 : 0) + ", hyperfuel batches " + fuel + "/" + fuelBatches + "; carried plates "
				+ count(M3Items.DURASTEEL_PLATE) + ", cells " + count(M4Items.HYPERFUEL_CELL));
	}

	/** 17.3 item 2: the motivator, salvaged with Force Sense at a Wreck Field's heart on the home world. */
	private void motivator() {
		String step = "60 motivator";

		if ((sockets() & 1 << ShipRules.SOCKET_MOTIVATOR_INDEX) != 0 || count(M4Items.HYPERDRIVE_MOTIVATOR) > 0) {
			note(step, Result.INFO, "the motivator is already in hand or fitted: " + shipLine());
			return;
		}

		if (c4() != ChapterFourStage.REPAIRS) {
			note(step, Result.FAIL, "the salvage only answers in REPAIRS: " + c4Line());
		}

		homeWorld(step);
		SitePiece wreck = findSite(step, SiteKind.WRECK_FIELD, 2048, null);
		BlockPos heartPos = wreck == null ? null : onServer(p -> StructureLocator.anchor(wreck, SiteAnchors.Anchor.HEART));
		boolean sensed = false;

		if (heartPos == null) {
			note(step, Result.FAIL, "no Wreck Field heart found within 2048 blocks" + (wreck == null ? "" : " (piece " + wreck.template().getPath() + ")"));
		} else {
			Vec3 centre = Vec3.atCenterOf(heartPos);
			goTo(step, Level.OVERWORLD, Vec3.atBottomCenterOf(heartPos), 4, "the Wreck Field");
			int range = onServer(p -> ChapterFourService.balance().salvageRange);

			// The salvage counts from the heart in three dimensions (it can sit in a crater).
			if (me().eye().distanceTo(centre) > range - 1) {
				Vec3 spot = standWithin(heartPos, range - 1.5);

				if (spot != null) {
					walkTo(spot, 0.5, 200, false, null);

					if (me().eye().distanceTo(centre) > range - 1) {
						shortcut("stepped into Sense range of the Wreck Field heart with a short teleport (" + step + ")");
						teleport(spot, yawToward(spot, centre));
					}
				}
			}

			aimAt(centre);
			context.waitTicks(5);
			tryShot("c4_wreck_field");
			int before = count(M4Items.HYPERDRIVE_MOTIVATOR);
			sense(step);
			sensed = waitServer(60, p -> countOf(p, M4Items.HYPERDRIVE_MOTIVATOR) > before);

			if (!sensed) {
				collectDrops(step, 6);
				sensed = count(M4Items.HYPERDRIVE_MOTIVATOR) > before;
			}

			note(step, sensed ? Result.INFO : Result.FAIL, String.format(Locale.ROOT, "Sense %.1f blocks from the heart at %s: motivator=%s", me().eye().distanceTo(centre),
					heartPos.toShortString(), sensed));
		}

		if (count(M4Items.HYPERDRIVE_MOTIVATOR) == 0) {
			shortcut("salvaged the motivator with ChapterFourRepairs.salvage (Sense at the Wreck Field gave none; " + step + ")");
			onServer(p -> ChapterFourRepairs.salvage(p));
			context.waitTicks(10);
			collectDrops(step, 4);
		}

		if (count(M4Items.HYPERDRIVE_MOTIVATOR) == 0) {
			giveMissing(step, M4Items.HYPERDRIVE_MOTIVATOR, 1, "the motivator (no salvage gave one)");
		}

		check(step, sensed, "motivator from Sense at the Wreck Field=" + sensed + ", carried " + count(M4Items.HYPERDRIVE_MOTIVATOR) + ", " + c4Line());
	}

	/** 17.3 item 2: the blank nav computer used on Saelen takes the Jedi path's coordinates. */
	private void navComputer() {
		String step = "61 nav computer";

		if ((sockets() & 1 << ShipRules.SOCKET_NAV_INDEX) != 0 || hasLoadedNav()) {
			note(step, Result.INFO, "a loaded nav computer is already in hand or fitted");
			return;
		}

		giveMissing(step, M4Items.NAV_COMPUTER, 1, "a blank nav computer (none was crafted)");
		UUID jedi = story().jediMentor;
		boolean loaded = false;

		if (jedi != null) {
			homeWorld(step);
			backToCamp(step);
			waitServer(100, p -> p.level().getEntity(jedi) instanceof MentorEntity);
			loaded = useItemOnEntity(step, jedi, M4Items.NAV_COMPUTER, this::hasLoadedNav);
		}

		if (!loaded) {
			shortcut("loaded the nav computer with NavData.loadForPath (using it on Saelen did not; " + step + ")");
			onServer(p -> NavData.loadForPath(p));
		}

		check(step, loaded, "used the nav computer on Saelen: loaded=" + loaded + ", carried a loaded one=" + hasLoadedNav());
	}

	/** 17.3 item 2: the four parts fitted on the Repairs tab of the hulk's screen (one Fit click a socket). */
	private void hulkRepairs() {
		String step = "62 hulk repairs";
		ShipView s = ship();

		if (s == null) {
			note(step, Result.FAIL, "no ship: " + c4Line());
			return;
		}

		if (s.state() != ShipState.HULK) {
			note(step, Result.INFO, "the hulk is already repaired: " + shipLine());
			return;
		}

		homeWorld(step);
		int fitted = 0;

		if (openShipScreen(step) && shipTab("repairs")) {
			context.waitTicks(10);
			tryShot("c4_hulk_repairs");

			for (int socket = 0; socket < ShipRules.SOCKETS; socket++) {
				int bit = 1 << socket;

				if ((sockets() & bit) != 0) {
					continue;
				}

				Origin o = shipOrigin();

				if (o == null) {
					break;
				}

				// The Fit buttons stand on the right of each socket row (ShipScreen SOCKET_Y, SOCKET_STEP).
				int gx = o.x() + 214 + 23;
				int gy = o.y() + 42 + socket * 32 + 3 + 7;

				if (!buttonAt(SHIP_FIT, gx, gy)) {
					note(step, Result.INFO, "socket " + socket + ": no enabled Fit button (" + describeButtons(SHIP_FIT) + ")");
					continue;
				}

				clickAt(gx, gy);

				if (waitServer(20, p -> (socketMask(p) & bit) != 0)) {
					fitted++;
				} else {
					note(step, Result.INFO, "socket " + socket + ": the Fit click did not fit it");
				}
			}

			context.waitTicks(10);
			tryShot("c4_hulk_fitted");
		}

		closeScreen();

		for (int socket = 0; socket < ShipRules.SOCKETS; socket++) {
			int index = socket;

			if ((sockets() & 1 << index) == 0) {
				shortcut("fitted ship socket " + index + " with ShipActions.fitSocket (the Fit click did not; " + step + ")");
				onServer(p -> ShipActions.fitSocket(p, index));
			}
		}

		if (ship() != null && ship().state() == ShipState.HULK) {
			shortcut("repaired the hulk with /oldrepublic ship repair (the sockets did not stand it up; " + step + ")");
			server.runCommand("oldrepublic ship repair @p");
			waitServer(20, p -> { ShipView v = shipOf(p); return v != null && v.state() != ShipState.HULK; });
		}

		ShipView after = ship();
		check(step, fitted == ShipRules.SOCKETS && after != null && after.state() != ShipState.HULK, fitted + "/" + ShipRules.SOCKETS
				+ " sockets fitted with Fit clicks, " + shipLine());
	}

	/** 17.3 item 2: the two cells from the hold (shift-clicked out on the Hold tab) into the tank with the Status tab's Fuel button. */
	private void fuelAndLiftOff() {
		String step = "63 fuel";

		if (c4AtLeast(ChapterFourStage.FIRST_FLIGHT)) {
			note(step, Result.INFO, "the ship is already flying the story: " + c4Line());
			return;
		}

		ShipView s = ship();

		if (s == null || s.state() == ShipState.HULK) {
			note(step, Result.FAIL, "no repaired ship to fuel: " + shipLine());
			return;
		}

		boolean fuelled = s.fuel() > 0;

		if (!fuelled && openShipScreen(step)) {
			int taken = takeCellsFromHold(step);
			note(step, Result.INFO, "shift-clicked " + taken + " hyperfuel cell(s) out of the hold");
			fuelled = refuelOnScreen(step);
			context.waitTicks(5);
			tryShot("c4_fuelled");
		}

		closeScreen();

		if (!fuelled) {
			shortcut("fuelled the ship with /oldrepublic ship fuel (the Fuel button did not; " + step + ")");
			server.runCommand("oldrepublic ship fuel @p");
			waitServer(20, p -> { ShipView v = shipOf(p); return v != null && v.fuel() > 0; });
		}

		boolean flight = waitServer(60, p -> Story.get(p).chapter4.stage().ordinal() >= ChapterFourStage.FIRST_FLIGHT.ordinal());

		if (!flight) {
			flight = c4Stage(step, ChapterFourStage.FIRST_FLIGHT, "the fuelled ship did not move the story on");
		}

		check(step, fuelled && flight, "fuelled with the Fuel button=" + fuelled + ", " + shipLine() + ", " + c4Line());
	}

	/** 17.3 item 3: the first jump, to Selvaren, with real clicks on the Nav tab (row, Launch); landing begins the world arc. */
	private void firstFlight() {
		String step = "64 first flight";

		if (c4AtLeast(ChapterFourStage.WORLD_ARC)) {
			note(step, Result.INFO, "already on the world arc: " + c4Line());
			return;
		}

		boolean flew = flyTo(step, Destination.GREENWATCH, true);
		boolean arc = waitServer(100, p -> Story.get(p).chapter4.stage().ordinal() >= ChapterFourStage.WORLD_ARC.ordinal());
		context.waitTicks(20);
		tryShot("c4_selvaren");

		if (!arc) {
			arc = c4Stage(step, ChapterFourStage.WORLD_ARC, "landing on Selvaren did not begin the world arc");
		}

		check(step, flew && arc, "flew to Greenwatch=" + flew + ", " + c4Line() + ", journal selvaren=" + data().hasFlag(JournalEntries.entryFlag(JournalEntries.SELVAREN)));
	}

	/** 17.3 item 3: Ansa at the Greenwatch Enclave: the Jedi greeting, then her work (The Greenwatch starts). */
	private void meetAnsa() {
		String step = "65 ansa";
		int greenwatch = ChapterFourRules.stepOf(ChapterFourRules.GREENWATCH);

		if (arcHas(greenwatch) || ChapterFourRules.GREENWATCH.equals(activeMission())) {
			note(step, Result.INFO, "The Greenwatch is already taken: " + c4Line());
			return;
		}

		onSelvaren(step);
		UUID ansa = hubHost();
		int replies = 0;

		if (ansa == null) {
			note(step, Result.FAIL, "no hub host (Ansa) within 96 blocks of Selvaren's hub");
		} else {
			goTo(step, Planets.SELVAREN, target(ansa).pos(), 6, "Ansa");

			if (talkTo(step, ansa)) {
				context.waitTicks(10);
				tryShot("c4_ansa");
				String at = sessionAt();

				if (at != null && at.startsWith("c4_ansa_meet/")) {
					for (String reply : new String[] { "c4_ansa_meet.opt.regards", "c4_ansa_meet.opt.help", "c4_ansa_meet.opt.go" }) {
						if (!reply(step, DLG + reply)) {
							break;
						}

						replies++;
					}
				} else if (at != null && at.startsWith("c4_ansa/")) {
					replies += reply(step, DLG + "c4_ansa.opt.greenwatch") && reply(step, DLG + "c4_ansa.opt.greenwatch_go") ? 3 : 0;
				} else {
					note(step, Result.INFO, "Ansa opened " + at);
				}
			}

			if (sessionAt() != null) {
				closeScreen();
			}
		}

		boolean active = waitServer(40, p -> ChapterFourRules.GREENWATCH.equals(Missions.activeId(p)));

		if (!active && !arcHas(greenwatch)) {
			shortcut("started The Greenwatch with /oldrepublic mission start (Ansa's dialogue did not; " + step + ")");
			server.runCommand("oldrepublic mission start @p " + ChapterFourRules.GREENWATCH);
			context.waitTicks(5);
		}

		check(step, replies == 3 && active, "Ansa's replies made " + replies + "/3, mission " + activeMission() + ", " + c4Line());
	}

	/** 17.3 item 4: The Greenwatch: two corrupted waystones cleansed with Force Sense beside them. */
	private void greenwatch() {
		String step = "66 greenwatch";
		int greenwatch = ChapterFourRules.stepOf(ChapterFourRules.GREENWATCH);

		if (arcHas(greenwatch)) {
			note(step, Result.INFO, "The Greenwatch is done: " + c4Line());
			return;
		}

		onSelvaren(step);

		if (!ChapterFourRules.GREENWATCH.equals(activeMission())) {
			shortcut("started The Greenwatch with /oldrepublic mission start (it was not active; " + step + ")");
			server.runCommand("oldrepublic mission start @p " + ChapterFourRules.GREENWATCH);
			context.waitTicks(5);
		}

		UUID me = onServer(p -> p.getUUID());
		Set<BlockPos> tried = Collections.synchronizedSet(new HashSet<>());
		int cleansed = 0;

		for (int round = 1; round <= 5 && cleansed < 2 && !arcHas(greenwatch) && ChapterFourRules.GREENWATCH.equals(activeMission()); round++) {
			// The tracker can point at a clean stone (MissionService.java:995 skips only surveyed sites), so the bot looks for a corrupted one itself.
			SitePiece piece = findSite(step, SiteKind.WAYSTONE, 768, found -> {
				BlockPos stone = StructureLocator.anchor(found, SiteAnchors.Anchor.STONE);
				return stone == null || tried.contains(stone) || !Waystones.corruptedAt(stone);
			});
			BlockPos stone = piece == null ? null : onServer(p -> StructureLocator.anchor(piece, SiteAnchors.Anchor.STONE));

			if (stone == null) {
				note(step, Result.FAIL, "round " + round + ": no corrupted waystone found within 768 blocks");
				break;
			}

			tried.add(stone);
			goTo(step, Planets.SELVAREN, Vec3.atBottomCenterOf(stone), 4, "a corrupted waystone");
			boolean corrupt = waitServer(100, p -> p.level().getBlockEntity(stone) instanceof WaystoneBlockEntity w && !w.pending() && w.corrupted());

			if (!corrupt) {
				note(step, Result.INFO, "round " + round + ": the waystone at " + stone.toShortString() + " is not corrupted after all");
				continue;
			}

			Vec3 centre = Vec3.atCenterOf(stone);

			if (me().eye().distanceTo(centre) > 6.5) {
				walkTo(Vec3.atBottomCenterOf(stone), 3.0, 200, false, () -> me().eye().distanceTo(centre) <= 6.5);
			}

			aimAt(centre);
			context.waitTicks(5);

			if (cleansed == 0) {
				tryShot("c4_waystone_corrupted");
			}

			sense(step);
			boolean clean = waitServer(20, p -> p.level().getBlockEntity(stone) instanceof WaystoneBlockEntity w && w.cleansedFor(me));
			note(step, clean ? Result.INFO : Result.FAIL, String.format(Locale.ROOT, "round %d: Sense %.1f blocks from the waystone at %s: cleansed=%s", round,
					me().eye().distanceTo(centre), stone.toShortString(), clean));

			if (clean) {
				cleansed++;
			}
		}

		boolean done = waitServer(100, p -> Story.get(p).chapter4.arc().has(greenwatch));

		if (!done && ChapterFourRules.GREENWATCH.equals(activeMission())) {
			shortcut("completed The Greenwatch with /oldrepublic mission complete (" + cleansed + " of 2 waystones cleansed in play; " + step + ")");
			server.runCommand("oldrepublic mission complete @p");
			context.waitTicks(10);
		}

		check(step, done && cleansed >= 2, cleansed + " corrupted waystone(s) cleansed with Sense, Greenwatch step done=" + done + ", " + c4Line());
	}

	/**
	 * 17.3 item 4: The Heartstone: Ansa's mission, then the cleansing in the stone's ring. Barrier is
	 * granted 45 s in (a free slot is made first) and raised at once, and again after the third wave.
	 */
	private void heartstone() {
		String step = "67 heartstone";
		int heartstone = ChapterFourRules.stepOf(ChapterFourRules.HEARTSTONE);

		if (arcHas(heartstone) && barrierOwned()) {
			note(step, Result.INFO, "The Heartstone is done: " + c4Line());
			return;
		}

		onSelvaren(step);

		if (!ChapterFourRules.HEARTSTONE.equals(activeMission())) {
			UUID ansa = hubHost();
			boolean asked = false;

			if (ansa != null) {
				goTo(step, Planets.SELVAREN, target(ansa).pos(), 6, "Ansa");

				if (talkTo(step, ansa)) {
					String at = sessionAt();

					if (at != null && at.startsWith("c4_ansa/")) {
						asked = reply(step, DLG + "c4_ansa.opt.heartstone") && reply(step, DLG + "c4_ansa.opt.heartstone_go");
					} else {
						note(step, Result.INFO, "Ansa opened " + at);
					}
				}

				if (sessionAt() != null) {
					closeScreen();
				}
			}

			if (!waitServer(40, p -> ChapterFourRules.HEARTSTONE.equals(Missions.activeId(p)))) {
				shortcut("started The Heartstone with /oldrepublic mission start (Ansa's dialogue did not; " + step + ")");
				server.runCommand("oldrepublic mission start @p " + ChapterFourRules.HEARTSTONE);
				context.waitTicks(5);
			}

			note(step, Result.INFO, "asked Ansa for The Heartstone=" + asked + ", mission " + activeMission());
		}

		// At the hub, away from the waystones' fights: the trial waits for a free slot before it begins.
		freeSlotForBarrier(step);
		GlobalPos site = heartstoneSite(step);

		if (site == null) {
			note(step, Result.FAIL, "the Heartstone was not placed: " + c4Line());
		} else {
			Vec3 centre = Vec3.atBottomCenterOf(site.pos());
			goTo(step, site.dimension(), centre, 5, "the Heartstone");
			heartstoneTrial(step, centre);
		}

		if (!barrierOwned()) {
			freeSlotForBarrier(step);
			shortcut("granted Force Barrier with WorldArcs.grantPower (the trial did not; " + step + ")");
			onServer(p -> WorldArcs.grantPower(p, ForceBarrier.ID));
		}

		boolean done = waitServer(60, p -> Story.get(p).chapter4.arc().has(heartstone));

		if (!done && ChapterFourRules.HEARTSTONE.equals(activeMission())) {
			shortcut("completed The Heartstone with /oldrepublic mission complete (the cleansing did not end; " + step + ")");
			server.runCommand("oldrepublic mission complete @p");
			context.waitTicks(10);
		}

		check(step, done && barrierOwned() && arcHas(ChapterFourRules.STEP_POWER), "Heartstone done by the trial=" + done + ", force_barrier owned="
				+ barrierOwned() + " in slot " + (slotOf(ForceBarrier.ID) + 1) + ", " + c4Line());
	}

	/** The Heartstone's cleansing from the ring's centre: the waves fought near the stone, Barrier raised when granted and after the third wave. */
	private void heartstoneTrial(String step, Vec3 centre) {
		int heartstone = ChapterFourRules.stepOf(ChapterFourRules.HEARTSTONE);
		long start = gameTime();
		long end = start + 3600;
		long lastRaise = -10_000;
		int raises = 0;
		long granted = -1;
		int fights = 0;
		long lastTick = -1;
		igniteSaber();

		while (gameTime() < end && !arcHas(heartstone) && ChapterFourRules.HEARTSTONE.equals(activeMission())) {
			// Every pass lets at least one tick go by (a fight that stops at once must not spin the loop).
			if (gameTime() == lastTick) {
				tickMe(step);
			}

			lastTick = gameTime();
			Me me = me();

			if (me.dead()) {
				died(step);
			}

			keepAlive(step);
			long now = gameTime();

			if (granted < 0 && barrierOwned()) {
				granted = now;
				note(step, Result.INFO, "Force Barrier granted " + (now - start) + " ticks into the cleansing, slot " + (slotOf(ForceBarrier.ID) + 1));
			}

			// Raised once when granted, then again once the third wave is in (1,200 ticks after the start; its 400-tick cooldown allows it).
			boolean raiseDue = granted >= 0 && now - lastRaise > 420 && (raises == 0 || now - start >= 1300);

			if (raiseDue) {
				boolean raised = raiseBarrier(step);
				lastRaise = gameTime();
				raises += raised ? 1 : 0;

				if (raised && raises <= 2) {
					tryShot("c4_barrier_" + raises);
				}

				continue;
			}

			UUID foe = trialFoe(centre, 14);

			if (foe != null) {
				fights++;
				fight(foe, 100, false, step, () -> flat(here(), centre) > 11);
				continue;
			}

			if (flat(here(), centre) > 6) {
				walkTo(centre, 4, 100, false, null);
			} else {
				tickMe(step);
			}
		}

		note(step, Result.INFO, "cleansing ran " + (gameTime() - start) + " ticks: Barrier raised " + raises + " time(s), " + fights
				+ " fight(s) with the waves, mission " + activeMission());
	}

	/** The Hollow's counterweights ({@code WEIGHT_1..6}), by the clusters' index in the humming order. */
	private static final SiteAnchors.Anchor[] HOLLOW_WEIGHTS = { SiteAnchors.Anchor.WEIGHT_1, SiteAnchors.Anchor.WEIGHT_2, SiteAnchors.Anchor.WEIGHT_3,
			SiteAnchors.Anchor.WEIGHT_4, SiteAnchors.Anchor.WEIGHT_5, SiteAnchors.Anchor.WEIGHT_6 };
	/**
	 * Template-local floor cells of the grotto three blocks in front of each counterweight (toward the
	 * middle), with a clear line from the eye to the hanging stone (read from
	 * {@code expedition/singing_hollow_grotto.nbt}).
	 */
	private static final int[][] HOLLOW_FRONTS = { { 20, 2, 13 }, { 19, 2, 15 }, { 11, 2, 15 }, { 10, 2, 13 }, { 11, 2, 11 }, { 19, 2, 11 } };

	/**
	 * 17.3 item 4: the Singing Hollow with real Sense and Push presses. Sense in the grotto makes the
	 * three humming clusters chime in order; the counterweights under them are pushed in that order
	 * from in front of each; the seal opens, and a right-click on the crystal at the bottom ends the
	 * expedition; the crystal's hue (blue) is chosen in the dialogue that opens.
	 */
	private void singingHollow() {
		String step = "68 singing hollow";
		String doneFlag = ChapterFourRules.EXPEDITION_PREFIX + "singing_hollow";

		if (crystalsBefore < 0) {
			crystalsBefore = count(SaberItems.KYBER_CRYSTAL);
		}

		if (onServer(p -> Story.get(p).hasFlag(doneFlag))) {
			boolean chose = takeCrystal(step);
			note(step, Result.INFO, "the Singing Hollow is done already (hue chosen now=" + chose + "): " + c4Line());
			return;
		}

		if (!onSelvaren(step)) {
			note(step, Result.FAIL, "not on Selvaren: " + c4Where());
			return;
		}

		GlobalPos hollow = onServer(p -> PlanetSites.find(p, PlanetSites.Site.SINGING_HOLLOW));

		if (hollow == null) {
			// Placed as the player nears its ring point (WorldArcs); the Calling points there meanwhile.
			GlobalPos ring = onServer(p -> PlanetSites.ringPoint(p.level().getServer(), p.getUUID(), PlanetSites.Site.SINGING_HOLLOW, Planets.Planet.SELVAREN));

			if (ring != null) {
				check(step, callingPointsAt(ring), "the Calling points at the Singing Hollow's ring point " + ring.pos().toShortString() + " before it is placed: " + callingLine());
				goTo(step, ring.dimension(), surfaceIn(ring.dimension(), ring.pos()), 24, "the Singing Hollow's ring point");
				waitServer(200, p -> PlanetSites.find(p, PlanetSites.Site.SINGING_HOLLOW) != null);
				hollow = onServer(p -> PlanetSites.find(p, PlanetSites.Site.SINGING_HOLLOW));
			}

			if (hollow == null) {
				note(step, Result.FAIL, "the Singing Hollow was not placed with the player at its ring point " + (ring == null ? "(none)" : ring.pos().toShortString()));

				for (int attempt = 1; attempt <= 3 && hollow == null; attempt++) {
					shortcut("placed the Singing Hollow with /oldrepublic planet site (attempt " + attempt + "; nothing in play places it; " + step + ")");
					server.runCommand("oldrepublic planet site @p singing_hollow");
					waitServer(60, p -> PlanetSites.find(p, PlanetSites.Site.SINGING_HOLLOW) != null);
					hollow = onServer(p -> PlanetSites.find(p, PlanetSites.Site.SINGING_HOLLOW));
				}
			}
		}

		if (hollow == null) {
			note(step, Result.FAIL, "the Singing Hollow could not be placed: " + c4Line());
			return;
		}

		goTo(step, hollow.dimension(), Vec3.atBottomCenterOf(hollow.pos()), 8, "the Singing Hollow");
		context.waitTicks(10);
		tryShot("c4_hollow_surface");
		SitePiece grotto = grotto();

		if (grotto == null) {
			note(step, Result.FAIL, "the Singing Hollow has no grotto for the player: " + c4Line());
			return;
		}

		BlockPos heart = SiteAnchors.find(grotto, SiteAnchors.Anchor.HEART);
		Vec3 heartFeet = Vec3.atBottomCenterOf(heart != null ? heart : SiteAnchors.toWorld(grotto, new BlockPos(15, 2, 10)));
		Vec3 middle = Vec3.atCenterOf(SiteAnchors.toWorld(grotto, new BlockPos(15, 2, 13)));

		if (!grotto.box().isInside(BlockPos.containing(here()))) {
			shortcut("teleported down into the Singing Hollow's grotto (the bot does not climb down its ladder shaft; " + step + ")");
			teleportIn(hollow.dimension(), heartFeet, yawToward(heartFeet, middle));
		}

		boolean solved = false;
		boolean sensed = false;
		int pushed = 0;
		BlockPos hubCell = SiteAnchors.toWorld(grotto, new BlockPos(15, 2, 11));
		BlockPos seal = SiteAnchors.toWorld(grotto, new BlockPos(15, 2, 25));
		int[] order = onServer(p -> Expeditions.order(p.getUUID()));

		for (int round = 1; round <= 2 && !solved; round++) {
			if (round > 1) {
				// A wrong stone starts the order again; the pushed stones rise after their 600 ticks.
				List<BlockPos> weights = new ArrayList<>();

				for (SiteAnchors.Anchor anchor : HOLLOW_WEIGHTS) {
					BlockPos weight = SiteAnchors.find(grotto, anchor);

					if (weight != null) {
						weights.add(weight);
					}
				}

				boolean risen = waitServer(700, p -> weights.stream().noneMatch(w -> sunkAt(p, w)));
				note(step, Result.INFO, "round " + round + ": the counterweights are up again=" + risen);
			}

			walkTo(Vec3.atBottomCenterOf(hubCell), 0.6, 200, false, null);
			aimAt(middle);
			context.waitTicks(5);
			sensed |= sense(step);
			// The three clusters chime 20 ticks apart, each a higher tone.
			context.waitTicks(70);

			if (round == 1) {
				tryShot("c4_hollow_sense");
			}

			StringBuilder heard = new StringBuilder();

			for (int idx : order) {
				heard.append(heard.length() == 0 ? "" : ", ").append("WEIGHT_").append(idx + 1);
			}

			note(step, Result.INFO, "round " + round + ": Sense in the grotto=" + sensed + "; the order the clusters chime (seeded by the player, Expeditions.order): " + heard);

			for (int idx : order) {
				BlockPos weight = SiteAnchors.find(grotto, HOLLOW_WEIGHTS[idx]);

				if (weight == null) {
					note(step, Result.FAIL, "the grotto has no " + HOLLOW_WEIGHTS[idx]);
					break;
				}

				int[] f = HOLLOW_FRONTS[idx];
				BlockPos front = SiteAnchors.toWorld(grotto, new BlockPos(f[0], f[1], f[2]));
				walkPath(step, Vec3.atBottomCenterOf(hubCell), Vec3.atBottomCenterOf(front));
				String aim = "";
				String ray = "";
				boolean cast = false;
				boolean down = false;

				// A Push acts on the first block on the server's eye ray at the release; a player whose
				// Push missed pushes again, the second time aiming a little lower, under the chime.
				for (int attempt = 1; attempt <= 2 && !down; attempt++) {
					waitClient(10, mc -> mc.player.getDeltaMovement().horizontalDistanceSqr() < 1.0e-4);
					aimAt(Vec3.atCenterOf(weight).add(0, attempt == 1 ? 0 : -0.25, 0));
					context.waitTick();
					aim = lookingAt();
					ray = onServer(p -> {
						BlockHitResult hit = ForceBlockInteractions.findOnAim(p, 16);
						return hit == null ? "nothing reactive" : p.level().getBlockState(hit.getBlockPos()).getBlock().getName().getString() + " at " + hit.getBlockPos().toShortString();
					});
					cast = castPush(step);
					down = waitServer(10, p -> sunkAt(p, weight));
				}

				pushed += down ? 1 : 0;
				note(step, Result.INFO, "round " + round + ": " + HOLLOW_WEIGHTS[idx] + " at " + weight.toShortString() + ": crosshair on " + aim + ", server ray on " + ray
						+ ", Push cast=" + cast + ", sunk=" + down);
			}

			solved = waitServer(60, p -> passable(p.level(), seal));
		}

		tryShot("c4_hollow_open");
		boolean took = false;

		if (solved) {
			walkPath(step, Vec3.atBottomCenterOf(hubCell), Vec3.atBottomCenterOf(SiteAnchors.toWorld(grotto, new BlockPos(12, 2, 13))),
					Vec3.atBottomCenterOf(SiteAnchors.toWorld(grotto, new BlockPos(12, 2, 19))), Vec3.atBottomCenterOf(SiteAnchors.toWorld(grotto, new BlockPos(15, 2, 20))),
					Vec3.atBottomCenterOf(SiteAnchors.toWorld(grotto, new BlockPos(15, 2, 24))), Vec3.atBottomCenterOf(SiteAnchors.toWorld(grotto, new BlockPos(15, 2, 26))));
			BlockPos relic = SiteAnchors.find(grotto, SiteAnchors.Anchor.RELIC);

			for (int attempt = 1; relic != null && attempt <= 3 && !took; attempt++) {
				if (here().distanceTo(Vec3.atCenterOf(relic)) > 3.0) {
					walkTo(Vec3.atBottomCenterOf(relic), 2.0, 120, false, null);
				}

				double away = here().distanceTo(Vec3.atCenterOf(relic));
				// A standing cluster is under half a block tall, so a ray at the block's centre passes over it.
				boolean on = useBlock(relic, Vec3.atBottomCenterOf(relic).add(0, 0.2, 0));
				String aim = lookingAt();
				took = waitServer(40, p -> Story.get(p).hasFlag(doneFlag));
				note(step, took ? Result.INFO : Result.FAIL, "attempt " + attempt + ": right-clicked the crystal at the bottom " + relic.toShortString() + " from "
						+ Math.round(away * 10) / 10.0 + " blocks (crosshair on it=" + on + ", on " + aim + "): expedition done=" + took);
			}
		}

		if (!onServer(p -> Story.get(p).hasFlag(doneFlag))) {
			shortcut("ended the Singing Hollow with PlanetSites.markDone and QuestHooks.onExpeditionDone (the puzzle did not; " + step + ")");
			onServer(p -> {
				PlanetSites.markDone(p.level().getServer(), p.getUUID(), PlanetSites.Site.SINGING_HOLLOW);
				QuestHooks.onExpeditionDone(p, PlanetSites.Site.SINGING_HOLLOW);
				return null;
			});
			waitServer(20, p -> Story.get(p).hasFlag(doneFlag));
		}

		context.waitTicks(10);
		tryShot("c4_hollow_crystal");
		boolean chose = takeCrystal(step);
		int crystals = count(SaberItems.KYBER_CRYSTAL);
		check(step, sensed && pushed >= 3 && solved && took && chose && crystals > crystalsBefore, "Sense=" + sensed + ", counterweights sunk by Push " + pushed
				+ ", seal open=" + solved + ", crystal taken=" + took + ", blue chosen=" + chose + ", kyber crystals " + crystalsBefore + " -> " + crystals + ", " + c4Line());
	}

	/** 17.3 item 5: the flight home; the ship's landing at home blows the charge in its strut (hull 10, DISABLED, clue 1). */
	private void sabotage() {
		String step = "69 sabotage";

		if (onServer(p -> Story.get(p).chapter4.hunt().sabotageDone())) {
			note(step, Result.INFO, "the sabotage is behind the player: " + c4Line() + ", " + shipLine());
			return;
		}

		boolean flew = flyTo(step, Destination.HOME, true);
		boolean blown = waitServer(200, p -> Story.get(p).chapter4.hunt().sabotageDone());

		if (blown) {
			if (standAtShip(step)) {
				ShipView s = ship();

				if (s != null && s.box() != null) {
					aimAt(s.box().getCenter());
				}
			}

			context.waitTicks(10);
			tryShot("c4_sabotage");
		} else {
			shortcut("blew the landing strut with /oldrepublic c4 sabotage (the landing at home did not; " + step + ")");
			server.runCommand("oldrepublic c4 sabotage @p");
			waitServer(40, p -> Story.get(p).chapter4.hunt().sabotageDone());
		}

		boolean clue = waitServer(40, p -> hasClueOf(p, 1));
		check(step, flew && blown && clue, "flew home=" + flew + ", blown on landing=" + blown + ", clue 1=" + clue + ", " + shipLine());
	}

	/** 17.3 item 5: the hull welded back over the jump minimum with durasteel plates in hand (a right-click on the ship each). */
	private void shipRepair() {
		String step = "70 ship repair";
		ShipView s = ship();

		if (s == null) {
			note(step, Result.FAIL, "no ship: " + c4Line());
			return;
		}

		if (s.hull() >= 40 && s.state() != ShipState.DISABLED) {
			note(step, Result.INFO, "the ship needs no repair: " + shipLine());
			return;
		}

		int before = s.hull();
		int welded = weld(step, 60);
		ShipView after = ship();
		boolean ready = after != null && after.hull() >= 20 && after.state() != ShipState.DISABLED;
		check(step, welded >= 10 && ready, "hull " + before + " -> " + (after == null ? "?" : after.hull()) + " (" + welded + " of it welded with plates in hand), "
				+ shipLine());
	}

	/** 17.3 item 5: back to Selvaren and out on open ground; the first ambush comes and is fought until a hunter yields (clue 2). */
	private void ambushOne() {
		String step = "71 ambush one";

		if (hasClue(2)) {
			note(step, Result.INFO, "the first ambush is behind the player: " + c4Line());
			return;
		}

		boolean flew = flyTo(step, Destination.GREENWATCH, false);

		if (!onServer(p -> p.level().dimension().equals(Planets.SELVAREN))) {
			onSelvaren(step);
		}

		clearAmbushRetry(step);
		boolean open = goToOpenGround(step, Planets.SELVAREN);
		settleAfterOpenGround(step);
		boolean came = waitServer(900, p -> TheHunt.ambushRunning(p) || hasClueOf(p, 2));

		if (!came) {
			shortcut("started the first ambush with TheHunt.ambush (none came on open ground in 900 ticks; " + step + ")");
			onServer(p -> {
				TheHunt.ambush(p, 1, false);
				return null;
			});
			waitServer(100, p -> TheHunt.ambushRunning(p) || hasClueOf(p, 2));
		}

		int fights = fightAmbush(step, 2);
		boolean clue = waitServer(60, p -> hasClueOf(p, 2));
		answerOpenDialogue(step);

		if (!clue) {
			shortcut("recorded clue 2 with /oldrepublic c4 clue (the first ambush did not give it; " + step + ")");
			server.runCommand("oldrepublic c4 clue @p 2");
			waitServer(20, p -> hasClueOf(p, 2));
			answerOpenDialogue(step);
		}

		check(step, came && clue, "flew to Greenwatch=" + flew + ", on open ground=" + open + ", the ambush came by itself=" + came + ", " + fights
				+ " fight(s), clue 2 from the ambush=" + clue + ", " + c4Line());
	}

	/** 17.3 item 5: home again for the second ambush two days later (a logged time skip): two hunters and a droid (clue 3). */
	private void ambushTwo() {
		String step = "72 ambush two";

		if (hasClue(3)) {
			note(step, Result.INFO, "the second ambush is behind the player: " + c4Line());
			return;
		}

		boolean flew = flyTo(step, Destination.HOME, false);
		homeWorld(step);
		boolean today = skipDaysForAmbushTwo(step);
		clearAmbushRetry(step);
		boolean open = goToOpenGround(step, Level.OVERWORLD);
		settleAfterOpenGround(step);
		boolean came = today && waitServer(900, p -> TheHunt.ambushRunning(p) || hasClueOf(p, 3));

		if (!came) {
			shortcut("started the second ambush with TheHunt.ambush (" + (today ? "none came on open ground in 900 ticks" : "it cannot come by itself on this world's first days")
					+ "; " + step + ")");
			onServer(p -> {
				TheHunt.ambush(p, 2, false);
				return null;
			});
			waitServer(100, p -> TheHunt.ambushRunning(p) || hasClueOf(p, 3));
		}

		int fights = fightAmbush(step, 3);
		boolean clue = waitServer(60, p -> hasClueOf(p, 3));
		answerOpenDialogue(step);

		if (!clue) {
			shortcut("recorded clue 3 with /oldrepublic c4 clue (the second ambush did not give it; " + step + ")");
			server.runCommand("oldrepublic c4 clue @p 3");
			waitServer(20, p -> hasClueOf(p, 3));
			answerOpenDialogue(step);
		}

		// On the world's first days the second ambush cannot come by itself (see skipDaysForAmbushTwo): only the fight and the clue are checked.
		check(step, (came || !today) && clue, "flew home=" + flew + ", on open ground=" + open + ", the ambush came by itself=" + came + (today ? "" : " (not possible today)")
				+ ", " + fights + " fight(s), clue 3 from the ambush=" + clue + ", " + c4Line());
	}

	/** 17.3 item 5: three clues and level 14 (a logged XP top-up): HUNTED passes to CHALLENGED; Kaast's challenge plays and is answered Come. */
	private void challenge() {
		String step = "73 challenge";

		// CHALLENGED without the camp unlocked: the challenge came but was not answered (a death closed it before a retry).
		if (c4AtLeast(ChapterFourStage.CHALLENGED) && (c4AtLeast(ChapterFourStage.DUEL) || onServer(p -> TravelService.isUnlocked(p, Destination.HUNTING_CAMP)))) {
			note(step, Result.INFO, "the challenge is behind the player: " + c4Line());
			return;
		}

		if (c4() == ChapterFourStage.WORLD_ARC && !waitServer(100, p -> Story.get(p).chapter4.stage().ordinal() >= ChapterFourStage.HUNTED.ordinal())) {
			c4Stage(step, ChapterFourStage.HUNTED, "the world arc did not end after the Heartstone and the Hollow");
		}

		for (int clue = 1; clue <= ChapterFour.MAX_CLUE; clue++) {
			int c = clue;

			if (!hasClue(c)) {
				shortcut("recorded clue " + c + " with /oldrepublic c4 clue (the hunt did not give it; " + step + ")");
				server.runCommand("oldrepublic c4 clue @p " + c);
				waitServer(20, p -> hasClueOf(p, c));
				answerOpenDialogue(step);
			}
		}

		// A hit closes a dialogue, and the last ambush's flames or stragglers may still reach the player.
		settleBeforeDialogue(step);
		int duelLevel = onServer(p -> ChapterFourService.balance().duelLevel);
		int levelBefore = data().level;

		if (levelBefore < duelLevel) {
			long xp = onServer(p -> {
				ForceData f = Progression.get(p);
				long total = -f.xp;

				for (int level = f.level; level < duelLevel; level++) {
					total += Progression.xpToNext(level);
				}

				return Math.max(1L, total);
			});
			shortcut("added " + xp + " XP with /oldrepublic addxp to reach level " + duelLevel + " for Kaast's challenge (level " + levelBefore + " from play)");
			server.runCommand("execute as @p run oldrepublic addxp " + xp);
			waitServer(20, p -> Progression.get(p).level >= duelLevel);
		}

		boolean challenged = waitServer(200, p -> Story.get(p).chapter4.stage().ordinal() >= ChapterFourStage.CHALLENGED.ordinal());

		if (!challenged) {
			challenged = c4Stage(step, ChapterFourStage.CHALLENGED, "three clues and level " + duelLevel + " did not bring the challenge");
		}

		boolean shown = waitServer(100, p -> "c4_kaast_challenge/challenge".equals(sessionAt(p)));
		boolean answered = false;

		if (!shown && challenged) {
			// It came and closed before the bot saw it (a death, then this retry): the marker beacon replays it.
			replayChallenge(step);
			shown = "c4_kaast_challenge/challenge".equals(sessionAt());
		}

		if (shown) {
			context.waitTicks(20);
			tryShot("c4_challenge");

			if (sessionAt() == null) {
				replayChallenge(step);
			}

			if (sessionAt() != null) {
				answered = reply(step, DLG + "c4_kaast_challenge.opt.come");
			}
		}

		if (sessionAt() != null) {
			answerOpenDialogue(step);
		}

		boolean unlocked = onServer(p -> TravelService.isUnlocked(p, Destination.HUNTING_CAMP));
		check(step, challenged && shown && answered && unlocked, "level " + levelBefore + " -> " + data().level + ", challenge shown=" + shown + ", answered Come="
				+ answered + ", hunting camp unlocked=" + unlocked + ", " + c4Line());
	}

	/** Waits until the player is out of combat and not burning, so a dialogue that opens next is not closed by a hit. */
	private void settleBeforeDialogue(String step) {
		int lock = onServer(p -> SkillService.balance().combatLockTicks);

		if (!waitServer(lock + 200, p -> !Skills.inCombat(p) && !p.isOnFire())) {
			note(step, Result.INFO, "still in combat or burning after " + (lock + 200) + " ticks; going on");
		}
	}

	/** Removes vanilla hostiles near the player (the mod's own foes, Kaast among them, are kept). */
	private void clearNightMobs(String step) {
		int removed = onServer(p -> {
			int n = 0;

			for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(24),
					x -> x instanceof Enemy && x.isAlive() && "minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(x.getType()).getNamespace()))) {
				e.discard();
				n++;
			}

			return n;
		});

		if (removed > 0) {
			shortcut("removed " + removed + " vanilla hostile mob(s) that kept the player in combat (" + step + ")");
		}
	}

	/** The challenge closed before it was answered (a hit closes a dialogue): the marker beacon replays it, used as a player would. */
	private void replayChallenge(String step) {
		GlobalPos beacon = onServer(p -> Story.get(p).chapter4.sites().get(ChapterFourRules.SITE_BEACON));

		if (beacon == null || !beacon.dimension().equals(onServer(p -> p.level().dimension()))) {
			note(step, Result.INFO, "the challenge closed and there is no marker beacon here to replay it: " + c4Line());
			return;
		}

		settleBeforeDialogue(step);
		Vec3 feet = Vec3.atBottomCenterOf(beacon.pos());

		if (flat(feet, here()) > 16) {
			// After a death the player is back at the respawn point.
			goTo(step, beacon.dimension(), feet, 2.0, "the marker beacon");
		} else if (flat(feet, here()) > 2.5) {
			walkTo(feet, 2.0, 200, false, null);
		}

		boolean used = useBlock(beacon.pos(), Vec3.atCenterOf(beacon.pos()));
		boolean open = waitServer(60, p -> "c4_kaast_challenge/challenge".equals(sessionAt(p)));
		note(step, Result.INFO, "the challenge closed before it was answered; used the marker beacon at " + beacon.pos().toShortString() + " (on it=" + used
				+ "): replayed=" + open);
		context.waitTicks(10);
	}

	/** 17.3 item 6: the flight to the hunting camp and into its ring through the gate: DUEL, and the fight begins. */
	private void huntingCamp() {
		String step = "74 hunting camp";

		if (c4AtLeast(ChapterFourStage.AFTERMATH)) {
			note(step, Result.INFO, "the duel is behind the player: " + c4Line());
			return;
		}

		if (c4() == ChapterFourStage.DUEL && kaast().session()) {
			note(step, Result.INFO, "the duel is running already: " + c4Line());
			return;
		}

		GlobalPos camp = campPos();

		if (camp == null) {
			waitServer(100, p -> Story.get(p).chapter4.duel().camp() != null);
			camp = campPos();
		}

		if (camp == null) {
			// Placed as the player nears its ring point on the path world (PlanetSites.ensure); the Calling points there meanwhile.
			onSelvaren(step);
			GlobalPos ring = onServer(p -> {
				Planets.Planet world = ChapterFourRules.pathWorld(Story.get(p).storyPath());
				return PlanetSites.ringPoint(p.level().getServer(), p.getUUID(), PlanetSites.Site.HUNTING_CAMP, world != null ? world : Planets.Planet.SELVAREN);
			});

			if (ring != null) {
				check(step, callingPointsAt(ring), "the Calling points at the hunting camp's ring point " + ring.pos().toShortString() + " before it is placed: " + callingLine());
				goTo(step, ring.dimension(), surfaceIn(ring.dimension(), ring.pos()), 24, "the hunting camp's ring point");
				waitServer(200, p -> Story.get(p).chapter4.duel().camp() != null);
				GlobalPos placed = campPos();
				double arena = onServer(p -> KaastBalance.get().arenaRadius);

				if (placed != null && flat(Vec3.atBottomCenterOf(placed.pos()), here()) < arena + 8) {
					// Placed around the player (a clearing by the ring point): Kaast opens fire before the bot is ready.
					Vec3 heart = Vec3.atBottomCenterOf(placed.pos());
					Vec3 spot = safeSpotIn(placed.dimension(), heart.add(away(heart, here()).scale(arena + 24)));
					shortcut("stepped out of the hunting camp's ring, which was placed around the player (" + step + ")");
					teleportIn(placed.dimension(), spot, yawToward(spot, heart));
				}
			}

			if (campPos() == null) {
				int range = onServer(p -> PlanetBalance.get().siteEnsureRange);
				note(step, Result.FAIL, "the hunting camp was not placed with the player within " + range + " blocks of its ring point "
						+ (ring == null ? "(none)" : ring.pos().toShortString()) + ": " + c4Line());
			}

			for (int attempt = 1; attempt <= 3 && campPos() == null; attempt++) {
				shortcut("placed the hunting camp with /oldrepublic c4 camp (attempt " + attempt + "; " + step + ")");
				server.runCommand("oldrepublic c4 camp @p");
				waitServer(60, p -> Story.get(p).chapter4.duel().camp() != null);
			}

			camp = campPos();
		}

		if (camp == null) {
			note(step, Result.FAIL, "no hunting camp: " + c4Line());
			return;
		}

		boolean flew = flyTo(step, Destination.HUNTING_CAMP, true);
		context.waitTicks(10);
		tryShot("c4_camp_landing");
		boolean inside = enterKaastRing(step);
		boolean duel = waitServer(100, p -> Story.get(p).chapter4.stage().ordinal() >= ChapterFourStage.DUEL.ordinal());

		if (!duel) {
			duel = c4Stage(step, ChapterFourStage.DUEL, "entering the camp's ring did not start the duel");
		}

		boolean fight = waitServer(200, p -> KaastFight.session(p.getUUID()) != null);
		KaastView k = kaast();

		if (k.id() != null) {
			aimAt(k.pos().add(0, k.height() * 0.7, 0));
			context.waitTicks(10);
		}

		tryShot("c4_kaast");
		check(step, flew && inside && duel && fight, "flew to the camp=" + flew + ", through the gate into the ring=" + inside + ", " + c4Line() + ", Kaast phase "
				+ k.phase() + " health " + Math.round(k.health()) + "/" + Math.round(k.maxHealth()));
	}

	/** The nearest live mine of the player's Kaast fight within {@code radius} of the player, or null. */
	private @Nullable Vec3 liveMineNear(double radius) {
		return onServer(p -> {
			KaastSession s = KaastFight.session(p.getUUID());

			if (s == null) {
				return null;
			}

			Vec3 best = null;
			double bestDistance = radius;

			for (CampMine m : s.mines()) {
				if (!m.isRemoved() && m.isLive() && m.distanceTo(p) <= bestDistance) {
					bestDistance = m.distanceTo(p);
					best = m.position();
				}
			}

			return best;
		});
	}

	/**
	 * 17.3 item 6: one phase of the Kaast fight, played until it ends or {@link #KAAST_PHASE_TICKS}
	 * pass: phase 1 guards (deflects) his bolts and Pushes him off his nest; phase 2 Pushes the live
	 * mines off; phase 3 parries the third hit of his combo (the guard raised just before it lands).
	 * Between those it fights with the saber. A phase that times out is moved on with the logged
	 * /oldrepublic kaast phase (c4 yield after phase 3) and recorded as PASS WITH SHORTCUT.
	 */
	private void kaastPhase(String step, int n) {
		if (c4AtLeast(ChapterFourStage.AFTERMATH) || onServer(p -> Story.get(p).chapter4.duel().boss()) != 0) {
			note(step, Result.INFO, "Kaast has yielded already: " + c4Line());
			return;
		}

		if (!kaast().session()) {
			enterKaastRing(step);
		}

		KaastView k = kaast();

		if (!k.session()) {
			note(step, Result.FAIL, "no Kaast fight is running: " + c4Line());
			return;
		}

		if (k.phase() > n) {
			note(step, Result.INFO, "the fight is past phase " + n + " already (phase " + k.phase() + ")");
			return;
		}

		if (k.phase() < n) {
			shortcut("moved the Kaast fight to phase " + n + " with /oldrepublic kaast phase (the fight was in phase " + k.phase() + "; " + step + ")");
			server.runCommand("oldrepublic kaast phase @p " + n);
			waitServer(200, p -> {
				KaastView v = kaastOf(p);
				return !v.session() || v.phase() >= n && !v.transitioning();
			});
		}

		GlobalPos camp = campPos();
		Vec3 heart = camp == null ? null : Vec3.atBottomCenterOf(camp.pos());
		double radius = onServer(p -> KaastBalance.get().arenaRadius);
		int windup = onServer(p -> KaastBalance.get().comboWindup);
		long start = gameTime();
		long end = start + KAAST_PHASE_TICKS;
		float healthStart = kaast().health();
		int pushes = 0;
		int offNest = 0;
		int minePushes = 0;
		int guards = 0;
		int parries = 0;
		int fights = 0;
		int stalls = 0;
		int ringStalls = 0;
		boolean filmed = false;
		long lastTick = -1;
		igniteSaber();

		while (gameTime() < end) {
			// Every pass of the loop lets at least one tick go by (the game only runs while the test waits).
			if (gameTime() == lastTick) {
				tickMe(step);
			}

			lastTick = gameTime();
			Me me = me();

			if (me.dead()) {
				died(step);
			}

			keepAlive(step);
			KaastView v = kaast();

			if (!v.session() || v.yielded() || v.over() || v.phase() > n) {
				break;
			}

			if (sessionAt() != null) {
				answerOpenDialogue(step);
				continue;
			}

			if (v.transitioning() || v.id() == null) {
				setForward(false, false);
				tickMe(step);
				continue;
			}

			if (!filmed && gameTime() - start > 100) {
				aimAt(v.pos().add(0, v.height() * 0.7, 0));
				context.waitTick();
				tryShot("c4_kaast_phase_" + n);
				filmed = true;
			}

			// Out of the ring for too long leaves the fight (it resets): back toward the middle.
			if (heart != null && flat(me.pos(), heart) > radius - 3) {
				if (!walkTo(heart, radius - 8, 100, true, null) && ++ringStalls >= 2) {
					ringStalls = 0;
					Vec3 spot = safeSpotNear(heart.add(away(heart, here()).scale(6)));
					shortcut("teleported back into Kaast's ring after the walk to its middle stopped (" + step + ")");
					note(step, Result.INFO, "the walk back into the ring stopped: " + walkStop + "; teleporting to " + BlockPos.containing(spot).toShortString());
					teleport(spot, yawToward(spot, v.pos()));
				}

				continue;
			}

			Vec3 chest = v.pos().add(0, v.height() * 0.5, 0);

			if (n == 1 && v.nest() >= 0) {
				double reach = me.eye().distanceTo(chest);

				if (reach <= 5.6 && pushReady()) {
					setForward(false, false);
					aimAt(chest);
					context.waitTick();
					boolean cast = castPush(step);
					pushes++;
					boolean off = waitServer(20, p -> kaastOf(p).nest() < 0);
					offNest += off ? 1 : 0;

					if (pushes <= 4 || off) {
						note(step, Result.INFO, String.format(Locale.ROOT, "Push %d at Kaast on nest %d from %.1f blocks: cast=%s, off the nest=%s", pushes, v.nest(), reach, cast, off));
					}

					if (off && offNest == 1) {
						context.waitTicks(5);
						tryShot("c4_kaast_off_nest");
					}

					continue;
				}

				if (reach <= 5.6) {
					// Under his nest with Push recharging: the guard deflects his bolts back at him.
					guardFor(step, 20);
					guards++;
					continue;
				}

				Vec3 below = new Vec3(v.pos().x, me.pos().y, v.pos().z);

				if (!walkTo(below, 1.5, 80, true, () -> {
					KaastView w = kaast();
					return w.nest() < 0 || me().eye().distanceTo(w.pos().add(0, w.height() * 0.5, 0)) <= 5.4;
				}) && ++stalls >= 4) {
					stalls = 0;
					Vec3 spot = safeSpotNear(below);
					shortcut("teleported to the foot of Kaast's nest after the walk there stopped (" + step + ")");
					note(step, Result.INFO, "the walk to Kaast's nest stopped: " + walkStop + "; teleporting to " + BlockPos.containing(spot).toShortString());
					teleport(spot, yawToward(spot, v.pos()));
				}

				continue;
			}

			if (n == 2 && pushReady()) {
				// A live mine by Kaast first (it goes off beside him), else any live mine in reach.
				Vec3 mine = v.mine() != null && me.eye().distanceTo(v.mine()) <= 5.5 ? v.mine() : liveMineNear(5.5);

				if (mine != null) {
					setForward(false, false);
					aimAt(mine.add(0, 0.2, 0));
					context.waitTick();
					float before = v.health();
					boolean cast = castPush(step);
					minePushes++;
					boolean hurt = waitServer(20, p -> kaastOf(p).health() < before);

					if (minePushes <= 4) {
						note(step, Result.INFO, String.format(Locale.ROOT, "Push %d at a live mine %.1f blocks away (%.1f from Kaast): cast=%s, Kaast hurt=%s", minePushes,
								me.eye().distanceTo(mine), mine.distanceTo(v.pos()), cast, hurt));
					}

					if (minePushes == 1) {
						tryShot("c4_kaast_mine");
					}

					continue;
				}
			}

			if (n == 3 && v.windingUp()) {
				// The third hit lands comboWindup ticks after the wind-up shows; the guard goes up a few ticks before it (the parry window).
				setForward(false, false);

				for (int i = 0; i < Math.max(0, windup - 5); i++) {
					tickMe(step);
				}

				guardFor(step, 16);
				guards++;
				boolean parried = kaast().staggered();
				parries += parried ? 1 : 0;

				if (parries == 1 && parried) {
					tryShot("c4_kaast_parry");
				}

				continue;
			}

			UUID id = v.id();
			fights++;
			fight(id, 60, false, step, () -> {
				KaastView w = kaast();
				return !w.session() || w.yielded() || w.phase() != n || w.transitioning() || n == 1 && w.nest() >= 0 || n == 3 && w.windingUp()
						|| n == 2 && pushReady() && liveMineNear(5.5) != null || sessionAt() != null;
			});
		}

		releaseAll();
		KaastView after = kaast();
		boolean yielded = after.yielded() || onServer(p -> Story.get(p).chapter4.duel().boss()) >= 1;
		boolean advanced = yielded || after.session() && after.phase() > n;
		String done = String.format(Locale.ROOT, "%d ticks, Kaast health %d -> %d of %d, %d Push(es) on a nest (%d off it), %d Push(es) at mines, %d guard(s), %d parry(ies), %d melee bout(s)",
				gameTime() - start, Math.round(healthStart), Math.round(after.health()), Math.round(after.maxHealth()), pushes, offNest, minePushes, guards, parries, fights);

		if (advanced) {
			check(step, n != 1 || offNest > 0, "phase " + n + " won in play: " + done + (n == 1 && offNest == 0 ? " (never Pushed off a nest)" : ""));
			return;
		}

		if (!after.session()) {
			note(step, Result.FAIL, "the fight ended without a yield: " + done + ", " + c4Line());
			return;
		}

		if (n < 3) {
			shortcut("moved the Kaast fight to phase " + (n + 1) + " with /oldrepublic kaast phase after the " + KAAST_PHASE_TICKS + "-tick timeout (" + step + ")");
			server.runCommand("oldrepublic kaast phase @p " + (n + 1));
			waitServer(100, p -> kaastOf(p).phase() > n);
		} else {
			shortcut("made Kaast yield with /oldrepublic c4 yield after the " + KAAST_PHASE_TICKS + "-tick timeout of phase 3 (" + step + ")");
			server.runCommand("oldrepublic c4 yield @p");
			waitServer(100, p -> Story.get(p).chapter4.duel().boss() >= 1);
		}

		note(step, Result.PASS, "PASS WITH SHORTCUT: phase " + n + " timed out after " + KAAST_PHASE_TICKS + " ticks and was moved on with "
				+ (n < 3 ? "/oldrepublic kaast phase " + (n + 1) : "/oldrepublic c4 yield") + ": " + done);
	}

	/** 17.3 item 7: Kaast yields and kneels; his talk is answered Spare (the chapter moves to AFTERMATH). */
	private void spareKaast() {
		String step = "78 spare";
		String spareFlag = ChapterFourRules.CHOICE_PREFIX + KaastOutcome.SPARED.id();

		if (c4AtLeast(ChapterFourStage.AFTERMATH)) {
			boolean spared = onServer(p -> Story.get(p).hasFlag(spareFlag));
			note(step, spared ? Result.INFO : Result.FAIL, "the duel is over: " + c4Line() + ", spared=" + spared);
			return;
		}

		boolean yielded = waitServer(200, p -> Story.get(p).chapter4.duel().boss() >= 1);

		if (!yielded) {
			shortcut("made Kaast yield with /oldrepublic c4 yield (he had not yielded; " + step + ")");
			server.runCommand("oldrepublic c4 yield @p");
			yielded = waitServer(40, p -> Story.get(p).chapter4.duel().boss() >= 1);
		}

		boolean shown = waitServer(100, p -> "c4_kaast_yield/yield".equals(sessionAt(p)));

		if (!shown) {
			// The knelt Kaast answers his owner's right-click with the same talk.
			KaastView k = kaast();

			if (k.id() != null) {
				talkTo(step, k.id());
			}

			shown = waitServer(40, p -> "c4_kaast_yield/yield".equals(sessionAt(p)));
		}

		boolean chose = false;

		if (shown) {
			context.waitTicks(20);
			tryShot("c4_kaast_yield");

			if (sessionAt() == null) {
				// A hit closes a dialogue (the fight's last flames, a leaving hunter, the night's mobs at
				// the camp): once it settles, the knelt Kaast answers his owner's right-click with the same talk.
				settleBeforeDialogue(step);

				if (onServer(p -> Skills.inCombat(p))) {
					clearNightMobs(step);
					settleBeforeDialogue(step);
				}

				KaastView k = kaast();

				if (k.id() != null) {
					talkTo(step, k.id());
				}

				boolean reopened = waitServer(40, p -> "c4_kaast_yield/yield".equals(sessionAt(p)));

				if (!reopened) {
					shortcut("reopened Kaast's yield with /oldrepublic c4 yield (a hit closed it and the right-click did not reopen it; " + step + ")");
					server.runCommand("oldrepublic c4 yield @p");
					reopened = waitServer(40, p -> "c4_kaast_yield/yield".equals(sessionAt(p)));
				}

				note(step, Result.INFO, "the yield closed before it was answered; reopened=" + reopened);
			}

			if (sessionAt() != null) {
				chose = reply(step, DLG + "c4_kaast_yield.opt.spare");
			}
		}

		boolean aftermath = waitServer(100, p -> Story.get(p).chapter4.stage().ordinal() >= ChapterFourStage.AFTERMATH.ordinal());

		if (!aftermath) {
			if (sessionAt() != null) {
				closeScreen();
			}

			shortcut("resolved the duel as Spare with KaastFight.resolve and KaastDuel.applyChoice (the yield's Spare did not; " + step + ")");
			aftermath = onServer(p -> {
				StoryData d = Story.get(p);
				// 2 is KaastDuel.BOSS_RESOLVED (package-private there).
				d.chapter4 = d.chapter4.withDuel(d.chapter4.duel().withBoss(2, KaastOutcome.SPARED.ordinal()));
				Story.commit(p, d);
				KaastFight.resolve(p, KaastOutcome.SPARED);
				KaastDuel.applyChoice(p, KaastOutcome.SPARED);
				return ChapterFourService.enterStage(p, ChapterFourStage.AFTERMATH);
			});
		}

		context.waitTicks(40);
		tryShot("c4_kaast_spared");
		boolean spared = onServer(p -> Story.get(p).hasFlag(spareFlag));
		check(step, yielded && shown && chose && aftermath && spared, "yielded=" + yielded + ", his talk shown=" + shown + ", Spare chosen=" + chose + ", spare recorded="
				+ spared + ", " + c4Line());
	}

	/** 17.3 item 7: home, and the report to Saelen (the spare reply): COMPLETE. */
	private void reportChapterFour() {
		String step = "79 report";

		if (c4AtLeast(ChapterFourStage.COMPLETE)) {
			note(step, Result.INFO, "the report is made: " + c4Line());
			return;
		}

		if (!c4AtLeast(ChapterFourStage.AFTERMATH)) {
			c4Stage(step, ChapterFourStage.AFTERMATH, "the duel did not end");
		}

		boolean flew = flyTo(step, Destination.HOME, false);
		homeWorld(step);
		backToCamp(step);
		UUID jedi = story().jediMentor;
		int replies = 0;

		if (jedi == null) {
			note(step, Result.FAIL, "no Jedi mentor to report to: " + c4Line());
		} else {
			waitServer(100, p -> p.level().getEntity(jedi) instanceof MentorEntity);
			replies = talkAndChoose(step, jedi, "c4_report", DLG + "c4_hub_saelen.opt.report_spare");
		}

		boolean complete = waitServer(100, p -> Story.get(p).chapter4.stage() == ChapterFourStage.COMPLETE);

		if (!complete) {
			shortcut("made the report with /oldrepublic c4 report (Saelen's report did not; " + step + ")");
			server.runCommand("oldrepublic c4 report @p");
			complete = waitServer(40, p -> Story.get(p).chapter4.stage() == ChapterFourStage.COMPLETE);
		}

		check(step, replies == 1 && complete, "flew home=" + flew + ", reported the spare to Saelen=" + (replies == 1) + ", " + c4Line());
	}

	/** 17.3 item 7: the end state: COMPLETE, every destination unlocked, force_barrier owned, the Hollow's second crystal; the journal shown. */
	private void chapterFourState() {
		String step = "80 chapter four state";
		closeScreen();
		context.getInput().pressKey(InputConstants.KEY_J);
		boolean journal = waitClient(40, mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("JournalScreen"));

		if (journal) {
			context.waitTicks(10);
			tryShot("c4_journal");
		}

		closeScreen();
		ChapterFourStage stage = c4();
		List<String> locked = onServer(p -> List.of(Destination.values()).stream().filter(d -> d.milestone() <= 4 && !TravelService.isUnlocked(p, d)).map(Destination::id).toList());
		boolean barrier = barrierOwned();
		boolean hollowCrystal = onServer(p -> Story.get(p).hasFlag(ChapterFourRules.EXPEDITION_PREFIX + "singing_hollow")
				&& !Story.get(p).hasFlag(ChapterFourRules.CRYSTAL_PENDING_PREFIX + "singing_hollow"));
		int crystals = count(SaberItems.KYBER_CRYSTAL);
		boolean second = hollowCrystal && crystals > Math.max(0, crystalsBefore);
		boolean entry = data().hasFlag(JournalEntries.entryFlag(JournalEntries.REPORT_C4));
		server.runCommand("oldrepublic c4 status @p");
		check(step, stage == ChapterFourStage.COMPLETE && locked.isEmpty() && barrier && second, "chapter four " + stage + ", locked destinations " + locked
				+ ", force_barrier owned=" + barrier + ", the Hollow's crystal chosen=" + hollowCrystal + ", kyber crystals " + Math.max(0, crystalsBefore) + " -> "
				+ crystals + ", journal report_c4=" + entry + " (journal shown=" + journal + ")");
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Four helpers
	// ---------------------------------------------------------------------------------------------

	private ChapterFourStage c4() {
		return onServer(p -> Story.get(p).chapter4.stage());
	}

	private boolean c4AtLeast(ChapterFourStage stage) {
		return onServer(p -> Story.get(p).chapter4.stage().ordinal() >= stage.ordinal());
	}

	private String c4Line() {
		return onServer(p -> {
			StoryData.Chapter4 c = Story.get(p).chapter4;
			GlobalPos camp = c.duel().camp();
			return "chapter four " + c.stage() + ", arc " + c.arc().planet() + " steps " + c.arc().missionFlags() + " expedition " + c.arc().expedition() + ", clues "
					+ Integer.bitCount(c.hunt().clues()) + "/" + ChapterFour.MAX_CLUE + ", sabotage " + c.hunt().sabotageDone() + ", ambushes " + c.hunt().ambushes()
					+ " (first on day " + c.hunt().ambushDay() + "), camp " + (camp == null ? "-" : camp.dimension().identifier().getPath() + " " + camp.pos().toShortString())
					+ ", boss " + c.duel().boss() + " outcome " + c.duel().outcome() + ", level " + Progression.get(p).level + ", mission " + Missions.activeId(p);
		});
	}

	private String c4Where() {
		return onServer(p -> p.level().dimension().identifier().getPath() + " " + p.blockPosition().toShortString() + (Ships.isAboard(p) ? " (aboard)" : "")
				+ ", chapter four " + Story.get(p).chapter4.stage()) + "; " + shipLine();
	}

	private String shipLine() {
		ShipView s = ship();
		return s == null ? "no ship" : "ship " + s.state() + " hull " + s.hull() + " fuel " + s.fuel() + "/" + s.tank() + " at " + s.dimension().identifier().getPath()
				+ " " + BlockPos.containing(s.pos()).toShortString() + (s.box() == null ? " (body not loaded)" : "") + (s.to() == null ? "" : ", flying to " + s.to().id());
	}

	/** Moves Chapter Four forward with /oldrepublic c4 stage (a logged shortcut) and waits for it. */
	private boolean c4Stage(String step, ChapterFourStage stage, String why) {
		String id = ChapterFourRules.stageId(stage);
		shortcut("moved Chapter Four to " + id + " with /oldrepublic c4 stage (" + why + "; " + step + ")");
		server.runCommand("oldrepublic c4 stage @p " + id);
		return waitServer(40, p -> Story.get(p).chapter4.stage().ordinal() >= stage.ordinal());
	}

	private boolean hasClue(int clue) {
		return onServer(p -> hasClueOf(p, clue));
	}

	private static boolean hasClueOf(ServerPlayer p, int clue) {
		return (Story.get(p).chapter4.hunt().clues() & 1 << (clue - 1)) != 0;
	}

	private boolean arcHas(int mask) {
		return onServer(p -> Story.get(p).chapter4.arc().has(mask));
	}

	/** A screenshot that never fails the step. */
	private void tryShot(String name) {
		try {
			shot(name);
		} catch (RuntimeException | AssertionError e) {
			LOG.warn("PLAYTHROUGH-LOG: screenshot {} failed", name, e);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Four: getting about (teleports in any world, walks, the home world and Selvaren)
	// ---------------------------------------------------------------------------------------------

	/** Teleports the player into {@code dim} at {@code pos} (a logged shortcut at the call site) and waits for the world and its chunks. */
	private void teleportIn(ResourceKey<Level> dim, Vec3 pos, float yaw) {
		server.runCommand(String.format(Locale.ROOT, "execute in %s run tp @a %.2f %.2f %.2f %.1f 0", dim.identifier(), pos.x, pos.y, pos.z, yaw));
		waitServer(60, p -> p.level().dimension().equals(dim));
		context.waitTicks(2);
		waitForChunks("teleport");
	}

	/**
	 * As {@link #safeSpotNear} in any world: a standing spot on the surface 2 to 10 blocks from
	 * {@code target} on solid ground within 3 blocks of its height, else any such surface spot,
	 * else the target itself.
	 */
	private Vec3 safeSpotIn(ResourceKey<Level> dim, Vec3 target) {
		int tx = Mth.floor(target.x);
		int ty = Mth.floor(target.y);
		int tz = Mth.floor(target.z);
		Vec3 found = onServer(p -> {
			ServerLevel level = p.level().getServer().getLevel(dim);

			if (level == null) {
				return null;
			}

			int[][] dirs = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 }, { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 } };
			Vec3 any = null;

			for (int r = 2; r <= 10; r++) {
				for (int[] d : dirs) {
					int x = tx + d[0] * r;
					int z = tz + d[1] * r;
					level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					BlockPos ground = new BlockPos(x, y - 1, z);
					BlockState state = level.getBlockState(ground);
					BlockPos feet = new BlockPos(x, y, z);
					boolean solid = !state.getCollisionShape(level, ground).isEmpty() && state.getFluidState().isEmpty();

					if (!solid || !passable(level, feet) || !passable(level, feet.above())) {
						continue;
					}

					Vec3 spot = new Vec3(x + 0.5, y, z + 0.5);

					if (Math.abs(y - ty) <= 3) {
						return spot;
					}

					if (any == null) {
						any = spot;
					}
				}
			}

			return any;
		});
		return found != null ? found : target;
	}

	/** The surface (first free block above the ground) at {@code at}'s column in {@code dim}; loads the chunk first. */
	private Vec3 surfaceIn(ResourceKey<Level> dim, BlockPos at) {
		return onServer(p -> {
			ServerLevel level = p.level().getServer().getLevel(dim);

			if (level == null) {
				return Vec3.atBottomCenterOf(at);
			}

			level.getChunk(at.getX() >> 4, at.getZ() >> 4, ChunkStatus.FULL, true);
			return new Vec3(at.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ() + 0.5);
		});
	}

	/**
	 * Gets within {@code near} of {@code target} in {@code dim}: in another world or further than
	 * {@link #C4_WALK_LIMIT} it is a logged teleport near the target, then a walk; a walk that stops
	 * ends in a logged teleport. Returns whether it walked the whole way.
	 */
	private boolean goTo(String step, ResourceKey<Level> dim, Vec3 target, double near, String what) {
		boolean same = onServer(p -> p.level().dimension().equals(dim));
		double distance = same ? flat(target, here()) : Double.MAX_VALUE;

		if (same && distance <= near) {
			return true;
		}

		boolean walked = true;

		if (!same || distance > C4_WALK_LIMIT) {
			Vec3 toward = same ? away(target, here()) : new Vec3(0, 0, 1);
			Vec3 spot = safeSpotIn(dim, target.add(toward.scale(Math.max(4, Math.min(24, near + 6)))));
			shortcut("teleported to " + what + (same ? " (" + Math.round(distance) + " blocks away" : " (in " + dim.identifier().getPath()) + "; " + step + ")");
			teleportIn(dim, spot, yawToward(spot, target));
			walked = false;

			if (flat(target, here()) <= near) {
				return false;
			}
		}

		if (walkTo(target, near, 1200, true, null)) {
			return walked;
		}

		Vec3 now = here();
		note(step, Result.INFO, "walk to " + what + " stopped " + Math.round(flat(target, now)) + " blocks away: " + walkStop);
		Vec3 spot = safeSpotIn(dim, target.add(away(target, now).scale(Math.max(2, near - 2))));
		shortcut("teleported the rest of the way to " + what + " after the walk stopped (" + step + ")");
		teleportIn(dim, spot, yawToward(spot, target));
		return false;
	}

	/** Back on the home world beside the mentor camp (a logged teleport) when the player is in another world. */
	private void homeWorld(String step) {
		if (onServer(p -> p.level().dimension().equals(Level.OVERWORLD))) {
			return;
		}

		BlockPos camp = story().camp;
		ShipView s = ship();
		Vec3 target = camp != null ? Vec3.atBottomCenterOf(camp)
				: s != null && s.dimension().equals(Level.OVERWORLD) ? s.pos() : surfaceIn(Level.OVERWORLD, BlockPos.ZERO);
		Vec3 spot = safeSpotIn(Level.OVERWORLD, target.add(8, 0, 0));
		shortcut("teleported back to the home world (" + step + ")");
		teleportIn(Level.OVERWORLD, spot, yawToward(spot, target));
	}

	/**
	 * On Selvaren: by a flight to Greenwatch when the ship can make one, else (logged) a teleport
	 * beside the hub. Returns whether the player is there.
	 */
	private boolean onSelvaren(String step) {
		if (onServer(p -> p.level().dimension().equals(Planets.SELVAREN))) {
			return true;
		}

		ShipView s = ship();

		if (s != null && s.state() != ShipState.HULK && c4AtLeast(ChapterFourStage.FIRST_FLIGHT) && onServer(p -> TravelService.isUnlocked(p, Destination.GREENWATCH))) {
			flyTo(step, Destination.GREENWATCH, false);

			if (onServer(p -> p.level().dimension().equals(Planets.SELVAREN))) {
				return true;
			}
		}

		BlockPos hub = onServer(p -> PlanetHubs.hub(p.level().getServer(), Planets.Planet.SELVAREN));

		if (hub == null) {
			onServer(p -> PlanetHubs.ensure(p.level().getServer(), Planets.Planet.SELVAREN));
			waitServer(400, p -> PlanetHubs.hub(p.level().getServer(), Planets.Planet.SELVAREN) != null);
			hub = onServer(p -> PlanetHubs.hub(p.level().getServer(), Planets.Planet.SELVAREN));
		}

		if (hub == null) {
			note(step, Result.FAIL, "Selvaren's hub is not placed: " + c4Line());
			return false;
		}

		Vec3 centre = Vec3.atBottomCenterOf(hub);
		Vec3 spot = safeSpotIn(Planets.SELVAREN, centre.add(0, 0, 12));
		shortcut("teleported to Selvaren's hub (no flight took the player there; " + step + ")");
		teleportIn(Planets.SELVAREN, spot, yawToward(spot, centre));
		return onServer(p -> p.level().dimension().equals(Planets.SELVAREN));
	}

	/** Ansa, the Greenwatch Enclave's host, when the player is on Selvaren (walking to the hub when it is far). */
	private @Nullable UUID hubHost() {
		BlockPos hub = onServer(p -> p.level().dimension().equals(Planets.SELVAREN) ? PlanetHubs.hub(p.level().getServer(), Planets.Planet.SELVAREN) : null);

		if (hub == null) {
			return null;
		}

		if (flat(Vec3.atBottomCenterOf(hub), here()) > 48) {
			goTo(c4Step, Planets.SELVAREN, surfaceIn(Planets.SELVAREN, hub), 24, "Selvaren's hub");
		}

		waitServer(60, p -> hostNear(p, hub) != null);
		return onServer(p -> hostNear(p, hub));
	}

	private static @Nullable UUID hostNear(ServerPlayer p, BlockPos hub) {
		return p.level().getEntities((Entity) null, new AABB(hub).inflate(96), e -> e instanceof HubNpc npc && e.isAlive() && npc.role() == HubRole.HOST).stream()
				.min(Comparator.comparingDouble(e -> e.distanceToSqr(Vec3.atCenterOf(hub)))).map(Entity::getUUID).orElse(null);
	}

	/**
	 * A standing spot whose eye is within {@code range} of {@code pos}'s centre (feet and head free,
	 * solid dry ground), the one nearest the player; null when there is none.
	 */
	private @Nullable Vec3 standWithin(BlockPos pos, double range) {
		Vec3 me = here();
		Vec3 centre = Vec3.atCenterOf(pos);
		int r = (int) Math.ceil(range);
		return onServer(p -> {
			ServerLevel level = p.level();
			Vec3 best = null;
			double bestDistance = Double.MAX_VALUE;

			for (BlockPos at : BlockPos.betweenClosed(pos.offset(-r, -r, -r), pos.offset(r, r, r))) {
				if (!passable(level, at) || !passable(level, at.above()) || passable(level, at.below()) || !level.getFluidState(at).isEmpty()
						|| !level.getFluidState(at.below()).isEmpty()) {
					continue;
				}

				Vec3 feet = Vec3.atBottomCenterOf(at);

				if (feet.add(0, p.getEyeHeight(), 0).distanceTo(centre) > range) {
					continue;
				}

				double distance = feet.distanceToSqr(me);

				if (distance < bestDistance) {
					best = feet;
					bestDistance = distance;
				}
			}

			return best;
		});
	}

	/** Finds the nearest site of {@code kind} with the structure locator (as Chapter Three does). */
	private @Nullable SitePiece findSite(String step, SiteKind kind, int radius, @Nullable Predicate<SitePiece> exclude) {
		SitePiece[] found = new SitePiece[1];
		boolean[] answered = new boolean[1];
		onServer(p -> {
			StructureLocator.find(p, kind, radius, exclude, piece -> {
				found[0] = piece;
				answered[0] = true;
			});
			return null;
		});
		boolean searched = waitServer(600, p -> answered[0]);
		SitePiece piece = onServer(p -> found[0]);
		note(step, Result.INFO, "structure locator, " + kind + " within " + radius + " blocks: " + (piece == null ? "none (answered=" + searched + ")"
				: piece.template().getPath() + " at " + piece.origin().toShortString() + ", " + Math.round(flat(Vec3.atCenterOf(piece.origin()), here())) + " blocks away"));
		return piece;
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Four: items, the bench and screens
	// ---------------------------------------------------------------------------------------------

	private static int countOf(ServerPlayer p, Item item) {
		int n = 0;

		for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
			ItemStack stack = p.getInventory().getItem(slot);

			if (stack.is(item)) {
				n += stack.getCount();
			}
		}

		return n;
	}

	/** Tops {@code item} up to {@code n} with /give (a logged shortcut). */
	private void giveMissing(String step, Item item, int n, String what) {
		int have = count(item);

		if (have >= n) {
			return;
		}

		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		shortcut("gave " + (n - have) + " " + id + " with /give: " + what + " (" + step + ")");
		server.runCommand("give @a " + id + " " + (n - have));
		waitServer(20, p -> countOf(p, item) >= n);
	}

	private boolean hasLoadedNav() {
		return onServer(p -> {
			for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
				if (NavData.isLoaded(p.getInventory().getItem(slot))) {
					return true;
				}
			}

			return false;
		});
	}

	/** Puts nothing in the main hand: an empty hotbar slot, or hotbar slot 9 emptied into the inventory first. */
	private void emptyHand() {
		int slot = onServer(p -> {
			for (int i = 0; i < 9; i++) {
				if (p.getInventory().getItem(i).isEmpty()) {
					return i;
				}
			}

			return -1;
		});

		if (slot < 0) {
			int free = onServer(p -> {
				for (int i = 9; i < 36; i++) {
					if (p.getInventory().getItem(i).isEmpty()) {
						return i;
					}
				}

				return -1;
			});

			if (free < 0) {
				return;
			}

			context.getInput().pressKey(INVENTORY);
			waitClient(20, mc -> mc.gui.screen() != null);
			context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, free, 8, ContainerInput.SWAP, mc.player));
			context.waitTicks(3);
			closeScreen();
			slot = 8;
		}

		int hotbar = slot;
		context.getInput().pressKey(o -> o.keyHotbarSlots[hotbar]);
		waitServer(10, p -> p.getMainHandItem().isEmpty());
	}

	/** Uses {@code item} on the entity {@code id} (walk up, item in hand, crosshair on it, right-click) until {@code done}; three tries. */
	private boolean useItemOnEntity(String step, UUID id, Item item, BooleanSupplier done) {
		for (int attempt = 1; attempt <= 3; attempt++) {
			Target t = target(id);

			if (!t.alive()) {
				note(step, Result.FAIL, "the one to use " + BuiltInRegistries.ITEM.getKey(item).getPath() + " on is gone");
				return false;
			}

			Vec3 spot = talkSpot(id);

			if (spot != null) {
				walkTo(spot, 0.4, 300, false, null);
			} else {
				walkTo(t.pos(), 2.5, 300, false, null);
			}

			if (!select(item)) {
				note(step, Result.FAIL, "no " + BuiltInRegistries.ITEM.getKey(item).getPath() + " to use");
				return false;
			}

			Target now = target(id);
			aimAt(now.pos().add(0, now.height() * 0.6, 0));
			context.waitTicks(3);
			boolean on = me().picked() == now.id();

			if (!on) {
				// Something stands between (a tent pole, a crate): a spot that sees the target.
				Vec3 clear = sightSpot(now.pos().add(0, now.height() * 0.6, 0), 1, 3, 4.0, now.pos().y);

				if (clear != null && !walkTo(clear, 0.4, 100, false, null)) {
					shortcut("stepped to where the crosshair reaches the one to use " + BuiltInRegistries.ITEM.getKey(item).getPath() + " on (" + step + ")");
					teleport(clear, yawToward(clear, now.pos()));
				}

				now = target(id);
				aimAt(now.pos().add(0, now.height() * 0.6, 0));
				context.waitTicks(3);
				on = me().picked() == now.id();
			}

			context.getInput().pressKey(USE);
			boolean ok = false;

			for (int t2 = 0; t2 < 20 && !ok; t2++) {
				context.waitTick();
				ok = done.getAsBoolean();
			}

			note(step, Result.INFO, "attempt " + attempt + ": right-clicked with " + BuiltInRegistries.ITEM.getKey(item).getPath() + " (crosshair on the target=" + on + "): "
					+ (ok ? "done" : "nothing yet"));

			if (sessionAt() != null || context.computeOnClient(mc -> mc.gui.screen() != null)) {
				closeScreen();
			}

			if (ok) {
				return true;
			}
		}

		return false;
	}

	/** Moves the mouse to GUI point ({@code gx}, {@code gy}) of the open screen and left-clicks. */
	private void clickAt(double gx, double gy) {
		double[] at = context.computeOnClient(mc -> {
			Screen screen = mc.gui.screen();
			return screen == null ? null
					: new double[] { gx * mc.getWindow().getScreenWidth() / (double) screen.width, gy * mc.getWindow().getScreenHeight() / (double) screen.height };
		});

		if (at == null) {
			return;
		}

		context.getInput().setCursorPos(at[0], at[1]);
		context.waitTick();
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	/** GUI centre of the shown, enabled button labelled with lang key {@code key}, or null. */
	private double @Nullable [] buttonCentre(String key) {
		return context.computeOnClient(mc -> {
			if (mc.gui.screen() == null) {
				return null;
			}

			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof Button button && button.visible && button.active && button.getMessage().getContents() instanceof TranslatableContents text
						&& text.getKey().equals(key)) {
					return new double[] { button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0 };
				}
			}

			return null;
		});
	}

	/** Clicks the shown, enabled button labelled {@code key} with the mouse. Returns whether there was one. */
	private boolean clickButtonMouse(String key) {
		double[] centre = buttonCentre(key);

		if (centre == null) {
			return false;
		}

		clickAt(centre[0], centre[1]);
		return true;
	}

	/** Whether a shown, enabled button labelled {@code key} covers GUI point ({@code gx}, {@code gy}). */
	private boolean buttonAt(String key, int gx, int gy) {
		return context.computeOnClient(mc -> {
			if (mc.gui.screen() == null) {
				return false;
			}

			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof Button button && button.visible && button.active && button.getMessage().getContents() instanceof TranslatableContents text
						&& text.getKey().equals(key) && gx >= button.getX() && gx < button.getX() + button.getWidth() && gy >= button.getY()
						&& gy < button.getY() + button.getHeight()) {
					return true;
				}
			}

			return false;
		});
	}

	/** The buttons labelled {@code key} on the open screen with their place and state (logs). */
	private String describeButtons(String key) {
		return context.computeOnClient(mc -> {
			if (mc.gui.screen() == null) {
				return "no screen";
			}

			List<String> found = new ArrayList<>();

			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key)) {
					found.add(button.getX() + "," + button.getY() + " " + button.getWidth() + "x" + button.getHeight() + (button.visible ? "" : " hidden")
							+ (button.active ? "" : " disabled"));
				}
			}

			return mc.gui.screen().getClass().getSimpleName() + " " + found;
		});
	}

	private Item benchItem() {
		return CraftingBlocks.ENGINEERING_BENCH.asItem();
	}

	/** The Chapter Four bench: the one placed before, one within 8 blocks, or the bench item placed by a right-click on the ground. */
	private boolean ensureBench(String step) {
		homeWorld(step);
		BlockPos known = c4Bench;

		if (known != null && onServer(p -> p.level().dimension().equals(Level.OVERWORLD) && p.level().getBlockState(known).is(CraftingBlocks.ENGINEERING_BENCH))) {
			return true;
		}

		Set<BlockPos> near = matchingBlocksNear(state -> state.is(CraftingBlocks.ENGINEERING_BENCH), 8);

		if (!near.isEmpty()) {
			c4Bench = near.iterator().next();
			return true;
		}

		giveMissing(step, benchItem(), 1, "an Engineering Bench (the briefing's one is not in the inventory)");
		BlockPos placed = placeBlock(benchItem(), state -> state.is(CraftingBlocks.ENGINEERING_BENCH), false);
		note(step, placed != null ? Result.INFO : Result.FAIL, "placed the Engineering Bench by right-clicking the ground: " + shortPos(placed));
		c4Bench = placed;
		return placed != null;
	}

	/** Opens the bench with a right-click from beside it (a logged menu open when the click does not). */
	private boolean openBench(String step) {
		BlockPos bench = c4Bench;

		if (bench == null) {
			return false;
		}

		if (context.computeOnClient(mc -> mc.gui.screen() instanceof EngineeringBenchScreen)) {
			return true;
		}

		closeScreen();
		BlockPos stand = onServer(p -> standBeside(p.level(), bench));

		if (stand != null && flat(Vec3.atBottomCenterOf(stand), here()) > 1.0) {
			walkTo(Vec3.atBottomCenterOf(stand), 0.5, 300, false, null);
		} else if (stand == null && flat(Vec3.atCenterOf(bench), here()) > 3.0) {
			walkTo(Vec3.atCenterOf(bench), 2.5, 300, false, null);
		}

		emptyHand();
		boolean opened = openBlock(bench, mc -> mc.gui.screen() instanceof EngineeringBenchScreen);

		if (!opened) {
			shortcut("opened the bench's screen through its menu (a right-click did not; " + step + ")");
			onServer(p -> p.openMenu(p.level().getBlockState(bench).getMenuProvider(p.level(), bench)));
			opened = waitClient(40, mc -> mc.gui.screen() instanceof EngineeringBenchScreen);
		}

		context.waitTicks(5);
		return opened;
	}

	/** Switches the open bench's tab as a click on its tab strip does. */
	private void benchTab(int tab) {
		context.runOnClient(mc -> {
			if (mc.player.containerMenu instanceof EngineeringBenchMenu menu && menu.clickMenuButton(mc.player, tab)) {
				mc.gameMode.handleInventoryButtonClick(menu.containerId, tab);
			}
		});
		context.waitTicks(5);
	}

	private @Nullable Origin benchOrigin() {
		return context.computeOnClient(mc -> mc.gui.screen() instanceof EngineeringBenchScreen screen ? new Origin((screen.width - 272) / 2, (screen.height - 204) / 2)
				: null);
	}

	/**
	 * Crafts {@code times} batches of {@code schematic} at the bench: the schematic selected on the
	 * Craft tab, Craft clicked, then the output shift-clicked out. Returns the batches made.
	 */
	private int craftAtBench(String step, String schematic, Item output, int times, boolean shotFirst) {
		if (!openBench(step)) {
			note(step, Result.FAIL, "the bench did not open for " + schematic);
			return 0;
		}

		benchTab(EngineeringBenchMenu.TAB_CRAFT);
		Identifier id = OldRepublicMod.id(schematic);
		int made = 0;

		for (int i = 0; i < times; i++) {
			int before = count(output);
			context.runOnClient(mc -> {
				if (mc.gui.screen() instanceof EngineeringBenchScreen screen) {
					screen.selectForTest(id);
				}
			});
			context.waitTicks(5);

			if (shotFirst && i == 0) {
				tryShot("c4_bench_" + schematic);
			}

			if (!clickButtonMouse(BENCH_CRAFT)) {
				note(step, Result.INFO, schematic + " batch " + (i + 1) + ": Craft is not enabled (" + describeButtons(BENCH_CRAFT) + ")");
				break;
			}

			boolean ready = waitClient(400, mc -> mc.player.containerMenu instanceof EngineeringBenchMenu menu
					&& !menu.getSlot(EngineeringBenchMenu.OUTPUT).getItem().isEmpty()) || count(output) > before;

			if (!ready && clickButtonMouse(BENCH_CRAFT)) {
				note(step, Result.INFO, schematic + " batch " + (i + 1) + ": no output after a Craft click; clicked Craft again");
				ready = waitClient(400, mc -> mc.player.containerMenu instanceof EngineeringBenchMenu menu
						&& !menu.getSlot(EngineeringBenchMenu.OUTPUT).getItem().isEmpty()) || count(output) > before;
			}

			if (ready) {
				context.runOnClient(mc -> {
					if (mc.player.containerMenu instanceof EngineeringBenchMenu menu) {
						mc.gameMode.handleContainerInput(menu.containerId, EngineeringBenchMenu.OUTPUT, 0, ContainerInput.QUICK_MOVE, mc.player);
					}
				});
				context.waitTicks(4);
			}

			boolean got = waitServer(10, p -> countOf(p, output) > before);
			note(step, got ? Result.INFO : Result.FAIL, schematic + " batch " + (i + 1) + "/" + times + ": selected, Craft clicked, output shift-clicked out=" + got);

			if (!got) {
				break;
			}

			made++;
		}

		return made;
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Four: the ship
	// ---------------------------------------------------------------------------------------------

	private @Nullable ShipView ship() {
		return onServer(p -> shipOf(p));
	}

	private static @Nullable ShipView shipOf(ServerPlayer p) {
		ShipRecord r = ShipRegistry.of(p.level().getServer(), p.getUUID()).orElse(null);

		if (r == null) {
			return null;
		}

		Entity body = Ships.body(p.level().getServer(), p.getUUID());
		Vec3 pos = body != null ? body.position() : Vec3.atBottomCenterOf(r.pos().pos());
		ResourceKey<Level> dimension = body != null ? body.level().dimension() : r.pos().dimension();
		return new ShipView(r.state(), r.hull(), r.fuel(), ShipRules.tankSize(r, ShipBalance.get()), r.state().flying() || r.flight().isPresent(), dimension, pos,
				body == null ? null : body.getBoundingBox(), body == null ? -1 : body.getId(), Ships.isAboard(p), r.flight().map(ShipRecord.Flight::to).orElse(null));
	}

	/** The fitted hulk sockets, a bit each (bit {@code n} for socket {@code n}). */
	private int sockets() {
		return onServer(p -> socketMask(p));
	}

	private static int socketMask(ServerPlayer p) {
		ShipRecord r = ShipRegistry.of(p.level().getServer(), p.getUUID()).orElse(null);
		int mask = 0;

		for (int socket = 0; r != null && socket < ShipRules.SOCKETS; socket++) {
			if (ShipRules.socketDone(r, socket, ShipBalance.get())) {
				mask |= 1 << socket;
			}
		}

		return mask;
	}

	private static boolean launchedTo(ServerPlayer p, Destination to) {
		ShipRecord r = ShipRegistry.of(p.level().getServer(), p.getUUID()).orElse(null);
		return r != null && (r.flight().map(f -> f.to() == to).orElse(false) || r.state().flying());
	}

	private static int flightPhase(ServerPlayer p) {
		return ShipRegistry.of(p.level().getServer(), p.getUUID()).flatMap(ShipRecord::flight).map(ShipRecord.Flight::phase).orElse(-1);
	}

	/** The world a flight to {@code to} lands in (the camp's for the hunting camp), or null. */
	private @Nullable ResourceKey<Level> destDim(Destination to) {
		Planets.Planet planet = to.planet();

		if (planet != null) {
			return planet.level();
		}

		return onServer(p -> {
			GlobalPos camp = PlanetSites.find(p, PlanetSites.Site.HUNTING_CAMP);
			return camp == null ? null : camp.dimension();
		});
	}

	/**
	 * A standing spot 5 or 6 blocks from the ship's middle (within reach of its 7-wide body) with a
	 * clear line from the eye to the hull, the one nearest {@code from}; null when there is none.
	 */
	private static @Nullable Vec3 shipStand(ServerPlayer p, Vec3 ship, @Nullable AABB box, Vec3 from) {
		ServerLevel level = p.level();
		Vec3 aim = ship.add(0, 1.5, 0);
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;

		for (int k = 0; k < 16; k++) {
			double angle = k * Math.PI / 8;

			for (double r : new double[] { 5.0, 6.0 }) {
				int x = Mth.floor(ship.x + Math.cos(angle) * r);
				int z = Mth.floor(ship.z + Math.sin(angle) * r);

				for (int dy = 1; dy >= -2; dy--) {
					BlockPos feet = new BlockPos(x, Mth.floor(ship.y) + dy, z);

					if (!passable(level, feet) || !passable(level, feet.above()) || passable(level, feet.below()) || !level.getFluidState(feet.below()).isEmpty()) {
						continue;
					}

					Vec3 spot = Vec3.atBottomCenterOf(feet);
					BlockHitResult hit = level.clip(new ClipContext(spot.add(0, 1.62, 0), aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
					boolean clear = hit.getType() == HitResult.Type.MISS || box != null && box.inflate(0.5).contains(hit.getLocation());
					double distance = spot.distanceToSqr(from);

					if (clear && distance < bestDistance) {
						best = spot;
						bestDistance = distance;
					}

					break;
				}
			}
		}

		return best;
	}

	/**
	 * Beside the ship, on foot: waits out a flight, gets off if aboard, then a walk (or, in another
	 * world or further than {@link #C4_WALK_LIMIT}, a logged teleport) to a spot within reach of it.
	 */
	private boolean standAtShip(String step) {
		waitServer(1500, p -> {
			ShipView v = shipOf(p);
			return v == null || !v.flying();
		});
		ShipView s = ship();

		if (s == null) {
			note(step, Result.FAIL, "no ship to go to: " + c4Line());
			return false;
		}

		if (s.aboard()) {
			dismount(step);
		}

		ResourceKey<Level> dim = s.dimension();
		boolean same = onServer(p -> p.level().dimension().equals(dim));
		double distance = same ? flat(s.pos(), here()) : Double.MAX_VALUE;

		if (!same || distance > C4_WALK_LIMIT) {
			Vec3 toward = same ? away(s.pos(), here()) : new Vec3(0, 0, 1);
			Vec3 spot = safeSpotIn(dim, s.pos().add(toward.scale(9)));
			shortcut("teleported to the ship (" + (same ? Math.round(distance) + " blocks away" : "in " + dim.identifier().getPath()) + "; " + step + ")");
			teleportIn(dim, spot, yawToward(spot, s.pos()));
		}

		waitServer(100, p -> {
			ShipView v = shipOf(p);
			return v != null && v.box() != null;
		});
		ShipView loaded = ship();

		if (loaded == null || loaded.box() == null) {
			note(step, Result.INFO, "the ship's body did not load: " + shipLine());
			return false;
		}

		Vec3 me = here();
		Vec3 stand = onServer(p -> shipStand(p, loaded.pos(), loaded.box(), me));

		if (stand == null) {
			note(step, Result.INFO, "no free spot 5 to 6 blocks beside the ship; staying " + Math.round(flat(loaded.pos(), here())) + " blocks from it");
			return flat(loaded.pos(), here()) <= 8;
		}

		if (flat(stand, here()) > 0.6 && !walkTo(stand, 0.5, 600, false, null)) {
			note(step, Result.INFO, "walk to the ship stopped: " + walkStop);
			shortcut("stepped beside the ship with a short teleport after the walk stopped (" + step + ")");
			teleportIn(dim, stand, yawToward(stand, loaded.pos()));
		}

		return true;
	}

	/** Steps from beside the ship (5 to 6 blocks from its middle) to about a block and a half from the hull's side. */
	private void stepUpToHull() {
		ShipView v = ship();

		if (v == null || v.box() == null) {
			return;
		}

		Vec3 me = here();
		Vec3 face = nearFace(v.box(), me);
		Vec3 out = new Vec3(me.x - face.x, 0, me.z - face.z);

		if (out.horizontalDistance() > 2.0) {
			Vec3 spot = face.add(out.normalize().scale(1.5));
			walkTo(new Vec3(spot.x, me.y, spot.z), 0.6, 100, false, null);
		}
	}

	/** The point of {@code box} nearest {@code from} on the ground plan, a little inside it, at {@code from}'s eye height clamped into the box. */
	private static Vec3 nearFace(AABB box, Vec3 from) {
		double x = Mth.clamp(from.x, box.minX + 0.3, box.maxX - 0.3);
		double z = Mth.clamp(from.z, box.minZ + 0.3, box.maxZ - 0.3);
		double y = Mth.clamp(from.y + 1.62, box.minY + 0.3, box.maxY - 0.3);
		return new Vec3(x, y, z);
	}

	/** Off the ship with Sneak (a logged stopRiding when that does not work). */
	private void dismount(String step) {
		if (!onServer(p -> Ships.isAboard(p))) {
			return;
		}

		closeScreen();
		context.getInput().holdKey(SNEAK);
		context.waitTicks(3);
		context.getInput().releaseKey(SNEAK);

		if (!waitServer(20, p -> !Ships.isAboard(p))) {
			shortcut("left the ship with stopRiding (Sneak did not; " + step + ")");
			onServer(p -> {
				p.stopRiding();
				return null;
			});
			waitServer(20, p -> !Ships.isAboard(p));
		}
	}

	/** Aboard with an empty hand and a right-click on the hull (a logged startRiding when that does not work). */
	private boolean boardShip(String step) {
		if (onServer(p -> Ships.isAboard(p))) {
			return true;
		}

		boolean used = false;

		if (standAtShip(step)) {
			emptyHand();

			for (int i = 0; i < 4 && !used; i++) {
				ShipView s = ship();

				if (s == null) {
					break;
				}

				aimAt(s.pos().add(0, 1.5, 0));
				context.waitTicks(3);
				context.getInput().pressKey(USE);
				used = waitServer(10, p -> Ships.isAboard(p));

				if (!used && context.computeOnClient(mc -> mc.gui.screen() != null)) {
					closeScreen();
				}
			}
		}

		boolean aboard = used;

		if (!aboard) {
			shortcut("boarded the ship with startRiding (a right-click beside it did not; " + step + ")");
			onServer(p -> {
				Entity body = Ships.body(p.level().getServer(), p.getUUID());
				return body != null && p.startRiding(body, true, false);
			});
			aboard = waitServer(20, p -> Ships.isAboard(p));
		}

		note(step, Result.INFO, "boarded with a right-click=" + used + ", aboard=" + aboard);
		return aboard;
	}

	/** From the pilot's seat: an empty hand, a look ahead and a right-click open the ship's screen. */
	private boolean useAboard() {
		emptyHand();
		float yaw = context.computeOnClient(mc -> mc.player.getYRot());
		context.getInput().lookAt(yaw, 10f);
		context.waitTicks(3);
		context.getInput().pressKey(USE);
		return waitClient(40, mc -> mc.gui.screen() instanceof ShipScreen);
	}

	/** The ship's screen: a right-click from the seat, or on the hull from beside it (a logged menu open when neither works). */
	private boolean openShipScreen(String step) {
		if (context.computeOnClient(mc -> mc.gui.screen() instanceof ShipScreen)) {
			return true;
		}

		closeScreen();
		boolean opened = false;

		if (onServer(p -> Ships.isAboard(p))) {
			opened = useAboard();
		} else if (standAtShip(step)) {
			emptyHand();

			for (int i = 0; i < 4 && !opened; i++) {
				ShipView s = ship();

				if (s == null) {
					break;
				}

				aimAt(s.pos().add(0, 1.5, 0));
				context.waitTicks(3);
				context.getInput().pressKey(USE);
				opened = waitClient(10, mc -> mc.gui.screen() instanceof ShipScreen);

				if (!opened && onServer(p -> Ships.isAboard(p))) {
					// A repaired ship is boarded by the first right-click; the screen opens from the seat.
					opened = useAboard();
				}
			}
		}

		if (!opened) {
			shortcut("opened the ship's screen with ShipMenu.open (a right-click did not; " + step + ")");
			onServer(p -> {
				ShipRecord r = ShipRegistry.of(p.level().getServer(), p.getUUID()).orElse(null);

				if (r != null) {
					ShipMenu.open(p, r);
				}

				return null;
			});
			opened = waitClient(40, mc -> mc.gui.screen() instanceof ShipScreen);
		}

		if (opened) {
			waitClient(40, mc -> ShipClientState.status() != null);
			context.waitTicks(5);
		}

		return opened;
	}

	/** Opens a tab of the ship's screen as a click on it would ({@link ShipScreen#openTab}, the M4 client test's seam). */
	private boolean shipTab(String id) {
		boolean open = waitClient(40, mc -> mc.gui.screen() instanceof ShipScreen screen && screen.openTab(id, false));
		context.waitTicks(8);
		return open;
	}

	private @Nullable Origin shipOrigin() {
		return context.computeOnClient(mc -> mc.gui.screen() instanceof ShipScreen screen ? new Origin((screen.width - 272) / 2, (screen.height - 204) / 2) : null);
	}

	/** The row of {@code to} in the Nav tab's list (0-based), or -1. */
	private int rowOf(Destination to) {
		waitClient(40, mc -> ShipClientState.status() != null);
		return context.computeOnClient(mc -> {
			ShipStatusPayload status = ShipClientState.status();

			if (status == null) {
				return -1;
			}

			for (int i = 0; i < status.destinations().size(); i++) {
				if (status.destinations().get(i).destination() == to.ordinal()) {
					return i;
				}
			}

			return -1;
		});
	}

	/** On the open ship screen's Hold tab, shift-clicks every hold slot holding a hyperfuel cell. Returns the cells taken. */
	private int takeCellsFromHold(String step) {
		if (!shipTab("hold")) {
			note(step, Result.INFO, "the ship's screen shows no Hold tab");
			return 0;
		}

		int before = count(M4Items.HYPERFUEL_CELL);
		int clicks = context.computeOnClient(mc -> {
			if (!(mc.player.containerMenu instanceof ShipMenu menu)) {
				return 0;
			}

			int n = 0;

			for (int slot = 0; slot < ShipMenu.HOLD_SLOTS && slot < menu.slots.size(); slot++) {
				if (menu.slots.get(slot).getItem().is(M4Items.HYPERFUEL_CELL)) {
					mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
					n++;
				}
			}

			return n;
		});
		context.waitTicks(5);
		int taken = Math.max(0, count(M4Items.HYPERFUEL_CELL) - before);
		note(step, Result.INFO, "shift-clicked " + clicks + " hold slot(s) holding cells: " + taken + " cell(s) taken");
		return taken;
	}

	/** The Status tab's Fuel button, clicked with the mouse. Returns whether the tank rose. */
	private boolean refuelOnScreen(String step) {
		ShipView before = ship();

		if (before == null || count(M4Items.HYPERFUEL_CELL) == 0) {
			note(step, Result.INFO, "no hyperfuel cells to fuel with (" + shipLine() + ")");
			return false;
		}

		shipTab("status");
		boolean clicked = clickButtonMouse(SHIP_FUEL);
		boolean fuelled = waitServer(20, p -> {
			ShipView v = shipOf(p);
			return v != null && v.fuel() > before.fuel();
		});
		ShipView after = ship();
		note(step, fuelled ? Result.INFO : Result.FAIL, "clicked Fuel on the Status tab (button there=" + clicked + "): fuel " + before.fuel() + " -> "
				+ (after == null ? "?" : after.fuel()) + (clicked ? "" : " (" + describeButtons(SHIP_FUEL) + ")"));
		return fuelled;
	}

	/**
	 * Hull welded back to {@code target} by right-clicking the ship with durasteel plates in hand
	 * (plates from a logged /give; the hull set with a logged command if the welding falls short).
	 * Returns the hull gained by welding.
	 */
	private int weld(String step, int target) {
		ShipView s = ship();

		if (s == null || s.state() == ShipState.HULK || s.hull() >= target) {
			return 0;
		}

		int start = s.hull();
		int plates = (target - start + 4) / 5;
		giveMissing(step, M3Items.DURASTEEL_PLATE, plates, "durasteel plates to weld the hull back to " + target);
		dismount(step);

		if (standAtShip(step) && select(M3Items.DURASTEEL_PLATE)) {
			stepUpToHull();

			for (int i = 0; i < plates * 3; i++) {
				ShipView v = ship();

				if (v == null || v.hull() >= target) {
					break;
				}

				if (!onServer(p -> p.getMainHandItem().is(M3Items.DURASTEEL_PLATE)) && !select(M3Items.DURASTEEL_PLATE)) {
					break;
				}

				// The nearest face of the hull's box: its centre is out of a hand's reach (3 blocks) from beside a 7-block hull.
				aimAt(v.box() != null ? nearFace(v.box(), here()) : v.pos().add(0, 1.5, 0));
				context.waitTicks(2);
				String aim = lookingAt();
				context.getInput().pressKey(USE);
				context.waitTicks(12);

				if (i == 0) {
					tryShot("c4_weld");
					ShipView w = ship();
					note(step, Result.INFO, "first weld: crosshair on " + aim + ", " + Math.round(here().distanceTo(v.pos()) * 10) / 10.0 + " blocks from the ship's origin, hull "
							+ v.hull() + " -> " + (w == null ? "?" : w.hull()) + ", state " + (w == null ? "?" : w.state()));
				}
			}
		}

		if (context.computeOnClient(mc -> mc.gui.screen() != null)) {
			closeScreen();
		}

		ShipView after = ship();
		int hull = after == null ? start : after.hull();
		note(step, Result.INFO, "welded with plates in hand: hull " + start + " -> " + hull + " (target " + target + ")");

		if (hull < 20 || after != null && after.state() == ShipState.DISABLED) {
			shortcut("set the hull to " + target + " with /oldrepublic ship hull (welding did not lift it over the jump minimum; " + step + ")");
			server.runCommand("oldrepublic ship hull @p " + target);
			waitServer(20, p -> {
				ShipView v = shipOf(p);
				return v != null && v.hull() >= target;
			});
		}

		return Math.max(0, hull - start);
	}

	/**
	 * Flies the ship to {@code to} the way a player does: aboard with a right-click, the screen from
	 * the seat, the destination's row and Launch clicked with the mouse on the Nav tab (fuel from the
	 * Status tab, hull welded first when needed). A refused or failed launch falls back to a logged
	 * /oldrepublic ship travel. With {@code film} the jump is photographed. Returns whether the
	 * clicked launch landed the player in {@code to}'s world.
	 */
	private boolean flyTo(String step, Destination to, boolean film) {
		waitServer(1500, p -> {
			ShipView v = shipOf(p);
			return v == null || !v.flying();
		});
		ShipView s = ship();

		if (s == null || s.state() == ShipState.HULK) {
			note(step, Result.FAIL, "no repaired ship to fly to " + to.id() + ": " + shipLine());
			return false;
		}

		ResourceKey<Level> dim = destDim(to);
		TravelService.Refusal refusal = onServer(p -> TravelService.canLaunch(p, to));

		if (refusal == TravelService.Refusal.HERE) {
			note(step, Result.INFO, "the ship already stands at " + to.id() + ": " + shipLine());
			standAtShip(step);
			return dim != null && onServer(p -> p.level().dimension().equals(dim));
		}

		if (refusal == TravelService.Refusal.LOCKED || refusal == TravelService.Refusal.UNAVAILABLE || dim == null) {
			note(step, Result.FAIL, to.id() + " cannot be flown to: " + refusal + ", " + shipLine());
			return false;
		}

		if (s.state() == ShipState.DISABLED || s.hull() < 25) {
			weld(step, 40);
		}

		ShipView now = ship();

		if (now != null && now.fuel() < 1) {
			giveMissing(step, M4Items.HYPERFUEL_CELL, 2, "hyperfuel cells for the jump to " + to.id());
		}

		boardShip(step);
		boolean clicked = false;
		boolean launched = false;

		if (openShipScreen(step)) {
			now = ship();

			if (now != null && now.fuel() < 1 && !refuelOnScreen(step)) {
				shortcut("fuelled the ship with /oldrepublic ship fuel (the Fuel button did not; " + step + ")");
				server.runCommand("oldrepublic ship fuel @p");
				waitServer(20, p -> {
					ShipView v = shipOf(p);
					return v != null && v.fuel() > 0;
				});
			}

			for (int attempt = 1; attempt <= 2 && !launched; attempt++) {
				if (!shipTab("nav")) {
					note(step, Result.INFO, "the ship's screen shows no Nav tab");
					break;
				}

				int row = rowOf(to);
				Origin o = shipOrigin();

				if (row < 0 || o == null) {
					note(step, Result.INFO, "no Nav row for " + to.id() + " (row " + row + ")");
					break;
				}

				// The destination rows of the list view (ShipScreen ROW_X, ROW_Y, ROW_STEP, 248 x 22 each).
				clickAt(o.x() + 136, o.y() + 42 + row * 24 + 11);
				context.waitTicks(3);

				if (film && attempt == 1) {
					tryShot("c4_nav_" + to.id());
				}

				clicked = clickButtonMouse(SHIP_LAUNCH);
				launched = clicked && waitServer(40, p -> launchedTo(p, to));

				if (!launched) {
					TravelService.Refusal why = onServer(p -> TravelService.canLaunch(p, to));
					note(step, Result.INFO, "attempt " + attempt + ": Launch " + (clicked ? "clicked" : "not enabled (" + describeButtons(SHIP_LAUNCH) + ")") + ", refusal " + why);

					if (why == TravelService.Refusal.COMBAT) {
						closeScreen();
						clearStrays(step);
						context.waitTicks(100);
						openShipScreen(step);
					}
				}
			}
		}

		if (context.computeOnClient(mc -> mc.gui.screen() != null)) {
			closeScreen();
		}

		if (!launched) {
			if (!onServer(p -> Ships.isAboard(p))) {
				boardShip(step);
			}

			shortcut("flew to " + to.id() + " with /oldrepublic ship travel (the Nav tab's Launch did not start the flight; " + step + ")");
			server.runCommand("oldrepublic ship travel @p " + to.id());
			waitServer(40, p -> launchedTo(p, to));
		}

		if (film && waitServer(600, p -> flightPhase(p) == ShipRules.PHASE_HYPERSPACE)) {
			context.waitTicks(30);
			tryShot("c4_hyperspace_" + to.id());
		}

		boolean landed = waitServer(1500, p -> {
			ShipView v = shipOf(p);
			return v != null && !v.flying();
		});

		if (!waitServer(60, p -> !Ships.isAboard(p))) {
			dismount(step);
		}

		boolean there = onServer(p -> p.level().dimension().equals(dim));
		waitForChunks(step);
		note(step, landed && there ? Result.INFO : Result.FAIL, "flight to " + to.id() + ": Launch clicked=" + clicked + ", launched by the click=" + launched + ", landed="
				+ landed + ", in " + dim.identifier().getPath() + "=" + there + ", " + shipLine());
		return launched && landed && there;
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Four: the world arc
	// ---------------------------------------------------------------------------------------------

	private boolean barrierOwned() {
		return onServer(p -> SkillService.data(p).owns(ForceBarrier.ID));
	}

	/**
	 * Barrier is granted into the first free slot, or not at all: when every open slot holds a power,
	 * the last one that is not Push is cleared (a logged shortcut for dragging it off on the Force screen).
	 */
	private void freeSlotForBarrier(String step) {
		if (barrierOwned() || onServer(p -> SkillRules.firstFreeSlot(SkillService.data(p)) >= 0)) {
			return;
		}

		int slot = onServer(p -> {
			SkillData d = SkillService.data(p);

			for (int i = Math.min(d.openSlots, SkillData.SLOT_COUNT) - 1; i >= 0; i--) {
				if (!d.slot(i).isEmpty() && !ForcePush.ABILITY_ID.equals(d.slot(i))) {
					return i;
				}
			}

			return -1;
		});

		if (slot < 0) {
			note(step, Result.FAIL, "no slot to free for Force Barrier: " + onServer(p -> SkillService.data(p).slots.toString()));
			return;
		}

		String was = onServer(p -> SkillService.data(p).slot(slot));
		// Slots do not change in combat (as on the Force screen), and the last step's fights may have just ended.
		int lock = onServer(p -> SkillService.balance().combatLockTicks);

		if (!waitServer(lock + 200, p -> !Skills.inCombat(p))) {
			note(step, Result.INFO, "still in combat after " + (lock + 200) + " ticks; trying to free the slot anyway");
		}
		shortcut("cleared Force slot " + (slot + 1) + " (" + was + ") with SkillService.assignSlot so Barrier has a free slot to be granted into (" + step + ")");
		boolean cleared = onServer(p -> SkillService.assignSlot(p, slot, ""));
		note(step, cleared ? Result.INFO : Result.FAIL, "freed slot " + (slot + 1) + " (was " + was + ") for Force Barrier: " + cleared);
	}

	/** Raises Force Barrier: its slot key held for 50 ticks once it is ready. Returns whether it went up. */
	private boolean raiseBarrier(String step) {
		int slot = slotOf(ForceBarrier.ID);

		if (slot < 0 || slot >= SkillData.SLOT_COUNT) {
			note(step, Result.FAIL, "Force Barrier is in no slot: " + onServer(p -> SkillService.data(p).slots.toString()));
			return false;
		}

		waitServer(200, p -> Progression.get(p).cooldown(ForceBarrier.ID) == 0 && Progression.get(p).energy >= 20f);
		boolean raised = false;

		try {
			holdSlot(slot);

			for (int t = 0; t < 50; t++) {
				keepAlive(step);
				tickMe(step);
				raised |= onServer(p -> ForceBarrier.isChannelling(p));
			}
		} finally {
			releaseSlot(slot);
		}

		note(step, raised ? Result.INFO : Result.FAIL, "held slot " + (slot + 1) + " for Force Barrier: raised=" + raised);
		return raised;
	}

	/** The nearest live spawn of the Heartstone's cleansing within {@code radius} of {@code centre}. */
	private @Nullable UUID trialFoe(Vec3 centre, double radius) {
		return onServer(p -> p.level().getEntities((Entity) null, new AABB(centre, centre).inflate(radius), e -> e instanceof Mob && e.isAlive()
				&& e.entityTags().contains(C4_TRIAL_TAG)).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::getUUID).orElse(null));
	}

	/** The Heartstone's place: placed by The Heartstone near the hub (a logged /oldrepublic planet site when it is not). */
	private @Nullable GlobalPos heartstoneSite(String step) {
		GlobalPos at = onServer(p -> PlanetSites.find(p, PlanetSites.Site.HEARTSTONE));

		if (at == null) {
			hubHost();
			waitServer(400, p -> PlanetSites.find(p, PlanetSites.Site.HEARTSTONE) != null);
			at = onServer(p -> PlanetSites.find(p, PlanetSites.Site.HEARTSTONE));
		}

		if (at == null) {
			shortcut("placed the Heartstone with /oldrepublic planet site (The Heartstone did not place it; " + step + ")");
			server.runCommand("oldrepublic planet site @p heartstone");
			waitServer(100, p -> PlanetSites.find(p, PlanetSites.Site.HEARTSTONE) != null);
			at = onServer(p -> PlanetSites.find(p, PlanetSites.Site.HEARTSTONE));
		}

		return at;
	}

	/** The player's own Singing Hollow grotto (the expedition's underground piece) in this world, or null. */
	private @Nullable SitePiece grotto() {
		return onServer(p -> {
			for (WorldSites.SiteRecord record : WorldSites.all(p.level().getServer())) {
				if (PlanetSites.HOLLOW_GROTTO.template().equals(record.template()) && p.getUUID().equals(record.owner())
						&& record.dimension().equals(p.level().dimension())) {
					return RuntimeSites.pieceOf(p.level(), record);
				}
			}

			return null;
		});
	}

	/** The Hollow's crystal: its hue chosen (blue) in the dialogue that opens when the expedition is done. Returns whether it was chosen there. */
	private boolean takeCrystal(String step) {
		String pending = ChapterFourRules.CRYSTAL_PENDING_PREFIX + "singing_hollow";

		if (!onServer(p -> Story.get(p).hasFlag(pending))) {
			return false;
		}

		boolean open = waitServer(40, p -> "c4_crystal/crystal".equals(sessionAt(p)));

		if (!open) {
			closeScreen();
			shortcut("opened the Hollow crystal's hue choice with Dialogues.open (it was not showing; " + step + ")");
			onServer(p -> Dialogues.open(p, OldRepublicMod.id("c4_crystal"), null));
			open = waitServer(40, p -> "c4_crystal/crystal".equals(sessionAt(p)));
		}

		boolean chose = open && reply(step, DLG + "c4_crystal.opt.blue");
		waitServer(20, p -> !Story.get(p).hasFlag(pending));

		if (sessionAt() != null) {
			closeScreen();
		}

		return chose;
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Four: the hunt
	// ---------------------------------------------------------------------------------------------

	/** Whether the player stands where an ambush may come (TheHunt's open-ground rule): open sky, on foot, {@code min} from the hub and every site. */
	private static boolean openGround(ServerPlayer p, int min) {
		ServerLevel level = p.level();
		Planets.Planet world = Planets.Planet.of(level.dimension());

		if (world == null || !level.canSeeSky(p.blockPosition()) || p.isPassenger()) {
			return false;
		}

		BlockPos hub = world == Planets.Planet.HOME ? null : PlanetHubs.hub(level.getServer(), world);
		return (hub == null || p.position().distanceToSqr(Vec3.atCenterOf(hub)) >= (double) min * min)
				&& StructureLocator.piecesNear(level, p.blockPosition(), min).isEmpty();
	}

	/**
	 * Out on open ground in {@code dim} for an ambush: rings 140 to 220 blocks out from the hub (on
	 * Selvaren) or the player (at home), first spot with sky and no site within reach; up to four tries.
	 */
	private boolean goToOpenGround(String step, ResourceKey<Level> dim) {
		int min = onServer(p -> ChapterFourService.balance().ambushMinDistance);

		if (onServer(p -> p.level().dimension().equals(dim) && openGround(p, min))) {
			return true;
		}

		Vec3 me = onServer(p -> p.level().dimension().equals(dim)) ? here() : null;
		List<Vec3> spots = onServer(p -> {
			ServerLevel level = p.level().getServer().getLevel(dim);
			List<Vec3> found = new ArrayList<>();

			if (level == null) {
				return found;
			}

			Planets.Planet world = Planets.Planet.of(dim);
			BlockPos hub = world == null || world == Planets.Planet.HOME ? null : PlanetHubs.hub(level.getServer(), world);
			Vec3 origin = hub != null ? Vec3.atBottomCenterOf(hub) : me != null ? me : Vec3.ZERO;

			for (int ring : new int[] { min + 44, min + 84, min + 124 }) {
				for (int k = 0; k < 8 && found.size() < 4; k++) {
					double angle = k * Math.PI / 4;
					int x = Mth.floor(origin.x + Math.cos(angle) * ring);
					int z = Mth.floor(origin.z + Math.sin(angle) * ring);
					level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					BlockPos feet = new BlockPos(x, y, z);
					BlockState ground = level.getBlockState(feet.below());

					if (!ground.getCollisionShape(level, feet.below()).isEmpty() && ground.getFluidState().isEmpty() && passable(level, feet)
							&& passable(level, feet.above()) && level.canSeeSky(feet) && StructureLocator.piecesNear(level, feet, min + 8).isEmpty()) {
						found.add(Vec3.atBottomCenterOf(feet));
					}
				}
			}

			return found;
		});

		for (Vec3 spot : spots) {
			goTo(step, dim, spot, 3, "open ground for the ambush");

			if (onServer(p -> openGround(p, min))) {
				note(step, Result.INFO, "on open ground at " + BlockPos.containing(here()).toShortString() + " in " + dim.identifier().getPath());
				return true;
			}
		}

		note(step, Result.FAIL, "found no open ground for the ambush in " + dim.identifier().getPath() + " (" + spots.size() + " candidate(s))");
		return false;
	}

	/** A fled ambush waits a day ({@code c4.ambush_retry_day}); that wait is cleared (a logged shortcut). */
	private void clearAmbushRetry(String step) {
		boolean cleared = onServer(p -> {
			StoryData d = Story.get(p);

			if (d.counter(ChapterFourRules.AMBUSH_RETRY_DAY) <= Clocks.day(p.level().getServer())) {
				return false;
			}

			d.counters.remove(ChapterFourRules.AMBUSH_RETRY_DAY);
			Story.commit(p, d);
			return true;
		});

		if (cleared) {
			shortcut("cleared the day a fled ambush waits before it comes again (" + step + ")");
		}
	}

	/**
	 * The time skip of 17.3: the second ambush comes {@code ambushGapDays} after the first, and game
	 * days follow the overworld's game time (which /time does not move), so the first ambush's day is
	 * moved back by the gap instead (a logged shortcut). Before day {@code gap} no first-ambush day
	 * fits (a day below 0 means "none yet"), so nothing is moved and false is returned: the second
	 * ambush cannot come by itself today.
	 */
	private boolean skipDaysForAmbushTwo(String step) {
		int gap = onServer(p -> ChapterFourService.balance().ambushGapDays);
		long today = onServer(p -> Clocks.day(p.level().getServer()));

		if (today < gap) {
			note(step, Result.INFO, "the world is on day " + today + " and the second ambush comes " + gap + " days after the first, so it cannot come by itself yet");
			return false;
		}

		String moved = onServer(p -> {
			StoryData d = Story.get(p);
			StoryData.Chapter4.Hunt h = d.chapter4.hunt();
			long day = Clocks.day(p.level().getServer());

			if (h.ambushDay() >= 0 && day - h.ambushDay() >= gap) {
				return null;
			}

			d.chapter4 = d.chapter4.withHunt(h.withAmbush(day - gap, Math.max(1, h.ambushes())));

			if (d.counter(ChapterFourRules.AMBUSH_RETRY_DAY) > day) {
				d.counters.remove(ChapterFourRules.AMBUSH_RETRY_DAY);
			}

			Story.commit(p, d);
			return "first ambush day " + h.ambushDay() + " -> " + (day - gap) + " (today is day " + day + ")";
		});

		if (moved != null) {
			shortcut("skipped the " + gap + " days before the second ambush by moving the first ambush's day back (" + step + ")");
			note(step, Result.INFO, "time skip: " + moved);
		}

		return true;
	}

	/**
	 * The search for open ground can stop on a spot that passes for a moment and then move on, past
	 * the ambush's leave range, so an ambush that dropped in there counts as fled and waits a day:
	 * after the slow tick has seen the move, that wait is cleared again.
	 */
	private void settleAfterOpenGround(String step) {
		context.waitTicks(30);
		clearAmbushRetry(step);
	}

	/** The nearest live mob within {@code radius} that the story set on this player (hunters, the droid), not yielded. */
	private @Nullable UUID ambusher(double radius) {
		return onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(radius), e -> e instanceof Mob && e.isAlive()
				&& p.getUUID().equals(StoryTags.owner(e)) && !(e instanceof MentorEntity) && !(e instanceof HubNpc) && !(e instanceof FrontierSettler)
				&& !(e instanceof Yieldable y && y.isYielded())).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::getUUID).orElse(null));
	}

	/**
	 * Fights a running ambush until clue {@code clue} arrives (a hunter yields, the droid falls) or
	 * 2,400 ticks pass, answering the talks that open on the way. Returns the fights fought.
	 */
	private int fightAmbush(String step, int clue) {
		long end = gameTime() + 2400;
		int fights = 0;
		long lastTick = -1;
		igniteSaber();

		while (gameTime() < end && !hasClue(clue)) {
			// Every pass lets at least one tick go by (a fight that stops at once must not spin the loop).
			if (gameTime() == lastTick) {
				tickMe(step);
			}

			lastTick = gameTime();

			if (me().dead()) {
				died(step);
			}

			keepAlive(step);

			if (sessionAt() != null) {
				answerOpenDialogue(step);
				continue;
			}

			UUID foe = ambusher(48);

			if (foe == null) {
				if (!onServer(p -> TheHunt.ambushRunning(p))) {
					break;
				}

				tickMe(step);
				continue;
			}

			if (fights == 0) {
				aimAt(target(foe).pos().add(0, 1, 0));
				context.waitTicks(2);
				tryShot("c4_ambush_" + (clue - 1));
			}

			fights++;
			igniteSaber();
			fight(foe, 400, true, step, () -> yielded(foe) || sessionAt() != null || hasClue(clue));
		}

		answerOpenDialogue(step);
		return fights;
	}

	/**
	 * Answers whatever Chapter Four or Five talk is open the way the Jedi run does (Who sent you, Go;
	 * Come; blue; Spare; Saelen's call: go on, me, go; Ulvaine: Spare); anything else is closed.
	 */
	private void answerOpenDialogue(String step) {
		for (int i = 0; i < 4; i++) {
			String at = sessionAt();

			if (at == null) {
				return;
			}

			String reply = switch (at) {
				case "c4_hunter_yield/yield" -> "c4_hunter_yield.opt.who";
				case "c4_hunter_yield/kaast" -> "c4_hunter_yield.opt.go";
				case "c4_kaast_challenge/challenge" -> "c4_kaast_challenge.opt.come";
				case "c4_crystal/crystal" -> "c4_crystal.opt.blue";
				case "c4_kaast_yield/yield" -> "c4_kaast_yield.opt.spare";
				case "c5_call_saelen/kaast" -> "c5_call_saelen.opt.go_on";
				case "c5_call_saelen/call" -> "c5_call_saelen.opt.me";
				case "c5_call_saelen/call2", "c5_call_saelen/why" -> "c5_call_saelen.opt.go";
				case "c5_ulvaine_yield/yield" -> "c5_ulvaine_yield.opt.spare";
				default -> null;
			};

			if (reply == null || !reply(step, DLG + reply)) {
				note(step, Result.INFO, "closed the dialogue " + at);
				closeScreen();

				if (sessionAt() != null) {
					onServer(p -> {
						Dialogues.close(p);
						return null;
					});
				}

				return;
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Four: Torvin Kaast
	// ---------------------------------------------------------------------------------------------

	private KaastView kaast() {
		return onServer(p -> kaastOf(p));
	}

	private static KaastView kaastOf(ServerPlayer p) {
		KaastSession s = KaastFight.session(p.getUUID());

		if (s == null) {
			return new KaastView(false, 0, false, false, false, null, -1, Vec3.ZERO, 0, -1, false, false, null, 0f, 0f);
		}

		TorvinKaast k = s.kaast();
		Vec3 mine = null;

		if (k != null) {
			double best = 3.5;

			for (CampMine m : s.mines()) {
				if (!m.isRemoved() && m.isLive() && m.distanceTo(k) <= best) {
					best = m.distanceTo(k);
					mine = m.position();
				}
			}
		}

		return new KaastView(true, s.phase(), s.isTransitioning(), s.isYielded(), s.isOver(), k == null ? null : k.getUUID(), k == null ? -1 : k.getId(),
				k == null ? Vec3.ZERO : k.position(), k == null ? 0 : k.getBbHeight(), k == null ? -1 : k.nest(), k != null && k.isWindingUp(),
				k != null && k.isStaggered(), mine, k == null ? 0f : k.getHealth(), k == null ? 0f : k.getMaxHealth());
	}

	/** Whether the Calling points at this place now (within two blocks, in its dimension). */
	private boolean callingPointsAt(GlobalPos at) {
		return onServer(p -> {
			Guidance.Request calling = Guidance.current(p);

			if (calling == null) {
				return false;
			}

			ResourceKey<Level> dimension = calling.dimension() != null ? calling.dimension() : p.level().dimension();
			return dimension.equals(at.dimension()) && calling.target().distanceTo(Vec3.atCenterOf(at.pos())) < 2;
		});
	}

	/** The Calling's request now, for notes. */
	private String callingLine() {
		return onServer(p -> {
			Guidance.Request calling = Guidance.current(p);
			return calling == null ? "no Calling" : "Calling " + calling.source() + " at " + BlockPos.containing(calling.target()).toShortString()
					+ (calling.dimension() == null ? "" : " in " + calling.dimension().identifier());
		});
	}

	private @Nullable GlobalPos campPos() {
		return onServer(p -> Story.get(p).chapter4.duel().camp());
	}

	/**
	 * Into the camp's ring on foot: to the camp (a logged teleport when far), round to its gate, and
	 * through it to six blocks inside; a logged teleport inside when the walk does not get there.
	 * Returns whether the fight is running.
	 */
	private boolean enterKaastRing(String step) {
		GlobalPos camp = campPos();

		if (camp == null) {
			return false;
		}

		Vec3 heart = Vec3.atBottomCenterOf(camp.pos());
		goTo(step, camp.dimension(), heart, 40, "the hunting camp");
		double radius = onServer(p -> KaastBalance.get().arenaRadius);
		List<BlockPos> gates = onServer(p -> p.level().dimension().equals(camp.dimension()) ? CampLayout.of(p.level(), camp.pos(), radius).gates() : List.<BlockPos>of());
		igniteSaber();

		if (!gates.isEmpty()) {
			int bottom = gates.stream().mapToInt(BlockPos::getY).min().orElse(camp.pos().getY());
			List<BlockPos> sill = gates.stream().filter(g -> g.getY() == bottom).toList();
			Vec3 gate = new Vec3(sill.stream().mapToDouble(g -> g.getX() + 0.5).average().orElse(heart.x), bottom,
					sill.stream().mapToDouble(g -> g.getZ() + 0.5).average().orElse(heart.z));
			Vec3 dir = away(heart, gate);
			walkTo(gate.add(dir.scale(2.5)), 0.8, 600, true, null);
			walkTo(gate, 0.6, 200, false, null);
			walkTo(heart.add(dir.scale(6)), 1.0, 300, false, null);
		} else {
			walkTo(heart, 6, 600, true, null);
		}

		boolean fight = waitServer(200, p -> KaastFight.session(p.getUUID()) != null);
		double inside = flat(heart, here());

		// Not started, or started with the bot stopped in the gate at the ring's edge (the fight loop keeps it 3 blocks inside).
		if (!fight && inside > 10 || inside > radius - 3) {
			Vec3 spot = safeSpotIn(camp.dimension(), heart.add(away(heart, here()).scale(6)));
			note(step, Result.INFO, "the walk through the gate stopped " + Math.round(inside) + " blocks from the pit: " + walkStop);
			shortcut("teleported into the hunting camp's ring (the walk through the gate did not get there; " + step + ")");
			teleportIn(camp.dimension(), spot, yawToward(spot, heart));
			fight = waitServer(200, p -> KaastFight.session(p.getUUID()) != null);
		}

		note(step, Result.INFO, "into the camp's ring through its gate (" + gates.size() + " gate cells): fight running=" + fight + ", " + c4Line());
		return fight;
	}

	/** Holds the guard (use, saber lit) for {@code duration} ticks, facing Kaast over his head. */
	private void guardFor(String step, int duration) {
		setForward(false, false);
		context.getInput().holdKey(USE);

		try {
			for (int i = 0; i < duration; i++) {
				KaastView k = kaast();

				if (k.id() != null) {
					aimAt(k.pos().add(0, k.height() + 2.5, 0));
				}

				keepAlive(step);
				tickMe(step);
			}
		} finally {
			context.getInput().releaseKey(USE);
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Five (spec MILESTONE_5.md 19.3: the Jedi run)
	// ---------------------------------------------------------------------------------------------

	/** Ticks each phase of the Ulvaine fight may take before it is moved on with /oldrepublic ulvaine phase (spec 19.3). */
	private static final int ULVAINE_PHASE_TICKS = 2400;
	/** Ticks the blockade is fought with the cannons before the logged /oldrepublic space blockade win (spec 19.3, the M3 rule). */
	private static final int BLOCKADE_TICKS = 2400;
	/** The pilot breaks off the blockade at this hull: a disabled ship loses the battle. */
	private static final int BLOCKADE_MIN_HULL = 25;
	/** Ticks the cruise to the cargo's buoy may take. */
	private static final int CARGO_TICKS = 4000;
	/** Ticks a verse stone's channel may take: its 100, with room for a guard that comes back and a broken channel. */
	private static final int VERSE_CHANNEL_TICKS = 400;
	/** The pilot keeps this far from the gunship's mark: outside its turrets' reach (64), inside the bolts' (96). */
	private static final double GUNSHIP_NEAR = 68;
	private static final double GUNSHIP_FAR = 84;
	/** And this high over it. */
	private static final double GUNSHIP_RISE = 14;

	/** The pilot flying the ship by hand, from the Helm key until the ship rests again. */
	private @Nullable FlightOrbitTrip flight;
	/** How many of the pilot's shortcuts are logged as the playthrough's already. */
	private int flightShortcuts;
	/** The Chapter Five step running now. */
	private String c5Step = "";

	/** Server view of a verse stone for one tick ({@code present} false while its block entity is not loaded); {@code guards} are the live ones. */
	private record StoneView(boolean present, boolean awake, boolean guardsSpawned, List<UUID> guards, @Nullable UUID channeller, int progress,
			boolean forceCut) {
	}

	/** Server view of the Ulvaine fight for one tick ({@code session} false when the player has none running). */
	private record UlvaineView(boolean session, int phase, boolean transitioning, boolean forming, boolean yielded, boolean over, @Nullable UUID id, Vec3 pos,
			double height, float health, double maxHealth, DarthUlvaine.Action action, GuardState guard, boolean staggered, List<UUID> chanters,
			List<UUID> resonators, boolean pulse, boolean rod, @Nullable HeartLayout layout) {
	}

	/** The Heart's door in the Hymnal's halls: its plating, the middle of its sill (feet height), its inner side (a unit step toward the Heart) and the Heart. */
	private record HeartDoor(List<BlockPos> cells, Vec3 sill, Vec3 inner, HeartLayout heart) {
	}

	/**
	 * Runs one Chapter Five step as {@link #chapterFour} does: one retry after a death, and nothing
	 * the step does escapes (a stuck step, an error or a failed link is a FAIL and the next step runs;
	 * each checks the story first and skips what is done). The pilot's keys are let go after it; the
	 * ship stays where it is, so the next step can fly on.
	 */
	private void chapterFive(String name, Runnable body) {
		boolean wasStuck = stuck;
		c5Step = name;

		try {
			for (int attempt = 1; ; attempt++) {
				try {
					note(name, Result.INFO, "where (" + (attempt == 1 ? "start" : "retry " + attempt) + "): " + c5Where());
					body.run();

					if (context.computeOnClient(mc -> mc.player != null && mc.player.isDeadOrDying())) {
						died(name);
					}

					note(name, Result.INFO, "where (end): " + c5Where());
					return;
				} catch (Died e) {
					if (attempt >= 2) {
						note(name, Result.FAIL, "died again on the retry: " + e.getMessage());
						return;
					}

					note(name, Result.INFO, "retrying the step after dying");
				}
			}
		} catch (Stuck e) {
			note(name, Result.FAIL, "Chapter Five goes on after: " + e.getMessage());
		} catch (RuntimeException | LinkageError | AssertionError e) {
			note(name, Result.FAIL, "error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
			LOG.error("PLAYTHROUGH-LOG: Chapter Five step failed", e);
			tryShot("error");
		} finally {
			stuck = wasStuck;

			try {
				pauseFlight();
				releaseAll();
				closeScreen();
			} catch (RuntimeException | AssertionError e) {
				LOG.warn("PLAYTHROUGH-LOG: could not tidy up after a Chapter Five step", e);
			}
		}
	}

	/**
	 * 19.3 item 1: Chapter Four done puts Chapter Five in WAITING; the wait is not waited out: a sleep
	 * (the bed and the night logged) makes the call due, and it plays at the next safe moment on the
	 * home world. Saelen's call is answered (go on, me, go): CALLED, the helm and Saltgate unlocked.
	 */
	private void theCall() {
		String step = "81 the call";
		boolean waiting = waitServer(100, p -> Story.get(p).chapter5.stage() != ChapterFiveStage.NOT_STARTED);

		if (c5AtLeast(ChapterFiveStage.CALLED)) {
			note(step, Result.INFO, "the call is behind the player: " + c5Line());
			return;
		}

		int[] balance = onServer(p -> new int[] { ChapterFiveService.balance().waitTicks, ChapterFiveService.balance().minSleepTicks });
		note(step, waiting ? Result.INFO : Result.FAIL, "after Chapter Four: " + c5Line() + "; the call comes after " + balance[0]
				+ " ticks of waiting or a sleep of at least " + balance[1] + " ticks");

		if (!waiting) {
			c5Stage(step, ChapterFiveStage.WAITING, "Chapter Five did not begin after Chapter Four");
		}

		endFlight();
		homeWorld(step);
		dismount(step);
		backToCamp(step);
		clearStrays(step);

		for (int attempt = 1; attempt <= 2 && !c5AtLeast(ChapterFiveStage.CALLED); attempt++) {
			if (!c5Flag(ChapterFiveRules.CALL_DUE)) {
				shortcut("gave a white bed and set the time to night for the sleep that makes the call due (the " + balance[0] + "-tick wait is not waited out)");
				server.runCommand("give @a minecraft:white_bed");
				server.runCommand("time set 13000");
				context.waitTicks(5);
				BlockPos bed = placeBlock(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("white_bed")), state -> state.is(BlockTags.BEDS), true);

				if (bed == null) {
					note(step, Result.FAIL, "attempt " + attempt + ": could not place the bed by right-clicking the ground");
				} else if (sleepIn(step, attempt, bed)) {
					context.waitTicks(40);
					tryShot("c5_waiting_sleep");
					waitServer(300, p -> !p.isSleeping());
				}
			}

			boolean due = waitServer(40, p -> Story.get(p).hasFlag(ChapterFiveRules.CALL_DUE) || ChapterFive.atLeast(p, ChapterFiveStage.CALLED));
			note(step, due ? Result.INFO : Result.FAIL, "attempt " + attempt + ": call due after the sleep=" + due + ", " + c5Line());

			// The call waits for a safe moment: the home world, off the ship, no talk open, on the ground, not hurt lately, no hostile near.
			for (int t = 0; t < 40 && !c5AtLeast(ChapterFiveStage.CALLED); t++) {
				if (t % 5 == 0) {
					clearStrays(step);
				}

				waitServer(20, p -> ChapterFive.atLeast(p, ChapterFiveStage.CALLED));
			}
		}

		boolean called = c5AtLeast(ChapterFiveStage.CALLED);

		if (!called) {
			shortcut("played the call with /oldrepublic c5 call (it did not play after the sleep; " + step + ")");
			server.runCommand("oldrepublic c5 call @p");
			called = waitServer(100, p -> ChapterFive.atLeast(p, ChapterFiveStage.CALLED));
		}

		if (!called) {
			called = c5Stage(step, ChapterFiveStage.CALLED, "the call did not move the chapter to CALLED");
		}

		boolean shown = waitServer(100, p -> callOpen(p));
		int answered = 0;

		if (shown) {
			context.waitTicks(20);
			tryShot("c5_call");
			answered = answerCall(step);
		}

		boolean helm = onServer(p -> TravelAttachments.get(p).helm());
		boolean saltgate = onServer(p -> TravelService.isUnlocked(p, Destination.SALTGATE));
		boolean entry = data().hasFlag(JournalEntries.entryFlag(JournalEntries.THE_FACTOR_ASKS));
		check(step, called && shown && answered >= 2 && helm && saltgate && entry, "call shown=" + shown + ", replies " + answered + ", helm unlocked=" + helm
				+ ", saltgate unlocked=" + saltgate + ", journal the_factor_asks=" + entry + ", " + c5Line());
	}

	/** Whether Saelen's call is open. Server thread. */
	private static boolean callOpen(ServerPlayer p) {
		String at = sessionAt(p);
		return at != null && at.startsWith("c5_call_saelen/");
	}

	/** Answers Saelen's call the way the Jedi run does (go on, me, go). Returns the replies made. */
	private int answerCall(String step) {
		int made = 0;

		for (int i = 0; i < 4 && onServer(p -> callOpen(p)); i++) {
			String reply = switch (Objects.requireNonNull(sessionAt())) {
				case "c5_call_saelen/kaast" -> "c5_call_saelen.opt.go_on";
				case "c5_call_saelen/call" -> "c5_call_saelen.opt.me";
				default -> "c5_call_saelen.opt.go";
			};

			if (!reply(step, DLG + reply)) {
				break;
			}

			made++;
		}

		if (sessionAt() != null) {
			closeScreen();
		}

		return made;
	}

	/**
	 * 19.3 item 2: from the home pad by hand: aboard, the Helm key, the climb into the home orbit, the
	 * jump to Ghessar's orbit clicked on the Nav tab, the dive and the landing on Saltgate's pad (the
	 * autopilot only as a logged fallback). Ghessar moves the chapter to SALTGATE.
	 */
	private void toSaltgate() {
		String step = "82 to saltgate";

		if (c5AtLeast(ChapterFiveStage.SALTGATE) && onPlanet(Planets.Planet.GHESSAR)) {
			note(step, Result.INFO, "already at Saltgate: " + c5Line());
			return;
		}

		if (flight == null || !flight.piloting()) {
			homeWorld(step);
			readyShip(step, 2);
		}

		boolean orbit = false;
		boolean jumped = false;

		if (helm(step)) {
			FlightOrbitTrip trip = Objects.requireNonNull(flight);
			ResourceKey<Level> world = trip.world();

			if (Planets.orbit(Planets.Planet.GHESSAR).equals(world) || Planets.Planet.GHESSAR.level().equals(world)) {
				note(step, Result.INFO, "the ship is flown over Ghessar already (a retry): " + trip.describe());
				orbit = true;
				jumped = true;
			} else {
				orbit = fly(step, "the climb into the home orbit", () -> trip.toOrbit(Planets.Planet.HOME));

				if (orbit) {
					tryShot("c5_orbit_home");
					jumped = fly(step, "the jump to Ghessar's orbit", () -> trip.jumpTo(Destination.SALTGATE));
				}

				if (jumped) {
					tryShot("c5_orbit_ghessar");
				}
			}
		}

		boolean byHand = landBy(step, Destination.SALTGATE);
		context.waitTicks(20);
		tryShot("c5_saltgate_landed");
		boolean saltgate = waitServer(200, p -> ChapterFive.atLeast(p, ChapterFiveStage.SALTGATE));

		if (!saltgate) {
			saltgate = c5Stage(step, ChapterFiveStage.SALTGATE, "landing on Ghessar did not move the chapter to SALTGATE");
		}

		boolean ghessar = onPlanet(Planets.Planet.GHESSAR);
		List<String> missing = onServer(p -> List.of(ChapterFiveRules.HELM, ChapterFiveRules.ORBIT, ChapterFiveRules.JUMP).stream()
				.filter(f -> !Story.get(p).hasFlag(f)).toList());
		check(step, orbit && jumped && byHand && saltgate && ghessar && missing.isEmpty(), "climbed into the home orbit by hand=" + orbit
				+ ", jumped to Ghessar's orbit on the Nav tab=" + jumped + ", landed at Saltgate by hand=" + byHand + ", on Ghessar=" + ghessar
				+ ", flags missing " + missing + ", " + c5Line());
	}

	/**
	 * 19.3 item 3, the kajidic's first job: Teel's debt in the canyons, paid (no fight): his jobs and
	 * the debt taken, to the debtor's camp (a walk, or a logged teleport beyond 128 blocks), his plea
	 * answered Pay (credits topped up when the run is short, logged), the strongbox handed to Teel.
	 */
	private void debtJob() {
		String step = "83 a debt in the canyons";

		if (c5Flag(ChapterFiveRules.JOB_DEBT) || c5AtLeast(ChapterFiveStage.THE_FACTOR)) {
			note(step, Result.INFO, "the debt is settled: " + c5Line());
			return;
		}

		boolean taken = ChapterFiveRules.DEBT.equals(activeMission());
		int replies = 0;

		if (!taken) {
			UUID teel = findHubPerson(step, "teel", Destination.SALTGATE);

			if (teel != null) {
				replies += talkAndChoose(step, teel, "c5_teel_jobs", DLG + "c5_majordomo.opt.jobs", DLG + "c5_majordomo.opt.debt", DLG + "c5_majordomo.opt.accept_debt");
			}

			taken = waitServer(40, p -> ChapterFiveRules.DEBT.equals(Missions.activeId(p)));
		}

		if (!taken) {
			shortcut("started the debt with /oldrepublic mission start (Teel's jobs did not; " + step + ")");
			server.runCommand("oldrepublic mission start @p " + ChapterFiveRules.DEBT);
			taken = waitServer(40, p -> ChapterFiveRules.DEBT.equals(Missions.activeId(p)));
		}

		boolean paid = onServer(p -> missionStageOf(p)) == MissionData.Stage.RETURN;
		UUID debtor = null;

		if (taken && !paid) {
			GlobalPos camp = reachSite(step, PlanetSites.Site.DEBTOR_CAMP, Planets.Planet.GHESSAR, "the debtor's camp");

			if (camp != null) {
				goTo(step, camp.dimension(), surfaceIn(camp.dimension(), camp.pos()), 16, "the debtor's camp");
				waitServer(200, p -> hubPersonOf(p, "debtor") != null);
				debtor = onServer(p -> hubPersonOf(p, "debtor"));
			}

			if (debtor == null) {
				note(step, Result.FAIL, "the debtor is not at his camp: " + c5Line());
			} else {
				int need = onServer(p -> ChapterFiveService.balance().debtCredits);
				int have = onServer(p -> Credits.count(p, false));

				if (have < need) {
					shortcut("gave " + (need - have) + " credits with Credits.give for the debtor's " + need + " (the run carried " + have + "; " + step + ")");
					onServer(p -> {
						Credits.give(p, need - have);
						return null;
					});
				}

				replies += talkAndChoose(step, debtor, "c5_debtor", DLG + "c5_debtor.opt.pay");
				paid = waitServer(60, p -> missionStageOf(p) == MissionData.Stage.RETURN);
			}
		}

		boolean handed = false;

		if (paid) {
			UUID teel = findHubPerson(step, "teel", Destination.SALTGATE);

			if (teel != null) {
				replies += talkAndChoose(step, teel, null, DLG + "c5_majordomo.opt.hand_box");
			}

			handed = waitServer(60, p -> Story.get(p).hasFlag(ChapterFiveRules.JOB_DEBT));
		}

		check(step, taken && paid && handed, "debt taken=" + taken + ", the debtor paid (no fight)=" + paid + ", the strongbox handed to Teel=" + handed + ", replies "
				+ replies + ", " + c5Line());
	}

	/**
	 * 19.3 item 3, the second job: the quiet cargo from Teel, flown by hand: the Helm key, the climb
	 * into Ghessar's orbit and the cruise to the buoy, past a patrol whose scan fails the run. A failed
	 * run is taken and flown once more, then (logged) /oldrepublic c5 cargo success. Down to Saltgate's
	 * pad by hand after each run.
	 */
	private void cargoJob() {
		String step = "84 the quiet cargo";

		if (c5Flag(ChapterFiveRules.JOB_CARGO) || c5AtLeast(ChapterFiveStage.THE_FACTOR)) {
			note(step, Result.INFO, "the cargo is delivered: " + c5Line());
			return;
		}

		boolean reached = false;
		int scans = 0;
		int flown = 0;

		for (int attempt = 1; attempt <= 2 && !c5Flag(ChapterFiveRules.JOB_CARGO); attempt++) {
			if (!takeCargo(step, attempt == 1 ? "c5_teel_cargo" : null)) {
				break;
			}

			readyShip(step, 1);
			boolean up = helm(step);
			FlightOrbitTrip trip = flight;
			up = up && trip != null && fly(step, "the climb into Ghessar's orbit", () -> trip.toOrbit(Planets.Planet.GHESSAR));

			if (up) {
				flown++;
				waitServer(200, p -> buoyOf(p) != null);
				Vec3 buoy = onServer(p -> buoyOf(p));
				note(step, Result.INFO, "attempt " + attempt + ": in Ghessar's orbit, the buoy " + (buoy == null ? "not set" : "at " + BlockPos.containing(buoy).toShortString())
						+ "; " + trip.describe());
				boolean there = flyUntil(step, "the cruise to the buoy", () -> trip.cruise("the cargo run", () -> onServer(PlaythroughTest::buoyOf), 8, true,
						() -> !onServer(p -> SpaceMissions.isRunning(p, ChapterFiveRules.CARGO)), CARGO_TICKS));

				if (there) {
					tryShot("c5_cargo_buoy");
				}

				boolean over = waitServer(200, p -> !SpaceMissions.isRunning(p, ChapterFiveRules.CARGO));
				Boolean result = onServer(p -> SpaceMissions.lastResult(p.getUUID(), ChapterFiveRules.CARGO));
				boolean job = waitServer(40, p -> Story.get(p).hasFlag(ChapterFiveRules.JOB_CARGO));
				reached |= there && job;
				scans += Boolean.FALSE.equals(result) ? 1 : 0;
				note(step, Result.INFO, "attempt " + attempt + ": at the buoy by hand=" + there + ", the run over=" + over + " (result " + result + "), the job done=" + job
						+ ", " + c5Line());
			}

			landBy(step, Destination.SALTGATE);

			if (!up) {
				break;
			}
		}

		boolean job = c5Flag(ChapterFiveRules.JOB_CARGO);

		if (!job) {
			if (!ChapterFiveRules.CARGO.equals(activeMission())) {
				takeCargo(step, null);
			}

			shortcut("ended the quiet cargo with /oldrepublic c5 cargo success (" + scans + " scan failure(s) in " + flown + " run(s) flown; " + step + ")");
			server.runCommand("oldrepublic c5 cargo @p success");
			job = waitServer(60, p -> Story.get(p).hasFlag(ChapterFiveRules.JOB_CARGO));
		}

		if (reached) {
			check(step, job, "the buoy reached by hand in " + flown + " run(s), the cargo delivered: " + c5Line());
		} else if (job && scans > 0) {
			note(step, Result.PASS, "PASS WITH SHORTCUT: the cargo run was flown " + flown + " time(s), " + scans + " scan failure(s), and ended with /oldrepublic c5 cargo success: "
					+ c5Line());
		} else {
			note(step, Result.FAIL, "the cargo run was flown " + flown + " time(s) (" + scans + " scan failure(s)) without reaching the buoy; the job done=" + job + ": " + c5Line());
		}
	}

	/** The quiet cargo from Teel (jobs, cargo, accept), else (logged) /oldrepublic mission start. Returns whether it is the active mission. */
	private boolean takeCargo(String step, @Nullable String shotName) {
		if (ChapterFiveRules.CARGO.equals(activeMission())) {
			return true;
		}

		UUID teel = findHubPerson(step, "teel", Destination.SALTGATE);

		if (teel != null) {
			talkAndChoose(step, teel, shotName, DLG + "c5_majordomo.opt.jobs", DLG + "c5_majordomo.opt.cargo", DLG + "c5_majordomo.opt.accept_cargo");
		}

		if (waitServer(40, p -> ChapterFiveRules.CARGO.equals(Missions.activeId(p)))) {
			return true;
		}

		shortcut("started the quiet cargo with /oldrepublic mission start (Teel's jobs did not; " + step + ")");
		server.runCommand("oldrepublic mission start @p " + ChapterFiveRules.CARGO);
		return waitServer(40, p -> ChapterFiveRules.CARGO.equals(Missions.activeId(p)));
	}

	/** The cargo run's buoy (its point in Ghessar's orbit), or null before it is set. Server thread. */
	private static @Nullable Vec3 buoyOf(ServerPlayer p) {
		return SpaceData.get(p).run(ChapterFiveRules.CARGO).flatMap(SpaceData.Run::point).orElse(null);
	}

	/**
	 * 19.3 item 3: both jobs done, Teel grants the audience (audience, lead) and Vessba takes the
	 * kajidic's way (the factor, the deal): THE_FACTOR.
	 */
	private void audience() {
		String step = "85 the audience";

		if (c5AtLeast(ChapterFiveStage.THE_FACTOR)) {
			note(step, Result.INFO, "the way is chosen: " + c5Line());
			return;
		}

		int replies = 0;
		UUID teel = findHubPerson(step, "teel", Destination.SALTGATE);

		if (teel != null) {
			replies += talkAndChoose(step, teel, "c5_audience", DLG + "c5_majordomo.opt.audience", DLG + "c5_majordomo.opt.lead");
		}

		UUID vessba = findHubPerson(step, HubNpcs.VESSBA, Destination.SALTGATE);

		if (vessba != null) {
			replies += talkAndChoose(step, vessba, "c5_vessba", DLG + "c5_vessba.opt.factor", DLG + "c5_vessba.opt.deal");
		}

		boolean factor = waitServer(100, p -> ChapterFive.atLeast(p, ChapterFiveStage.THE_FACTOR));

		if (!factor) {
			shortcut("completed the kajidic's way with /oldrepublic c5 way kajidic (the audience did not; " + step + ")");
			server.runCommand("oldrepublic c5 way @p kajidic");
			factor = waitServer(100, p -> ChapterFive.atLeast(p, ChapterFiveStage.THE_FACTOR));
		}

		ChapterFive.Way way = onServer(p -> ChapterFive.way(p));
		check(step, replies == 4 && factor && way == ChapterFive.Way.KAJIDIC, "replies " + replies + "/4 (Teel's audience, Vessba's deal), way " + way + ", "
				+ c5Line());
	}

	/**
	 * 19.3 item 3: the counting-house (its mission starts with THE_FACTOR once no other runs): to its
	 * desk in Saltgate; the kajidic's way leaves it no guards, so arriving clears it.
	 */
	private void countingHouse() {
		String step = "86 the counting-house";

		if (c5Flag(ChapterFiveRules.COUNTING_HOUSE) || c5AtLeast(ChapterFiveStage.ARMED)) {
			note(step, Result.INFO, "the counting-house is cleared: " + c5Line());
			return;
		}

		if (!onPlanet(Planets.Planet.GHESSAR)) {
			arrive(step, Destination.SALTGATE);
		}

		boolean started = waitServer(400, p -> ChapterFiveRules.COUNTING_HOUSE_RUN.equals(Missions.activeId(p)) || Story.get(p).hasFlag(ChapterFiveRules.COUNTING_HOUSE));
		waitServer(200, p -> MissionService.drivenSite(p) != null || Story.get(p).hasFlag(ChapterFiveRules.COUNTING_HOUSE));
		GlobalPos desk = onServer(p -> MissionService.drivenSite(p));
		int guards = onServer(p -> ChapterFiveRules.guards(Story.get(p), ChapterFiveService.balance()));

		if (desk != null) {
			goTo(step, desk.dimension(), Vec3.atBottomCenterOf(desk.pos()), 16, "the counting-house desk");
		}

		boolean cleared = waitServer(200, p -> Story.get(p).hasFlag(ChapterFiveRules.COUNTING_HOUSE));
		context.waitTicks(10);
		tryShot("c5_counting_house");
		check(step, started && cleared && guards == 0, "the mission started=" + started + ", the desk " + (desk == null ? "not set" : desk.pos().toShortString())
				+ ", guards for the way " + guards + ", cleared on arrival=" + cleared + ", " + c5Line());
	}

	/**
	 * 19.3 item 3: Dask at the counting-house desk: the buyer, what he wants, the hologram (Ulvaine's
	 * message, twice listened to), and Let him go: ARMED, the holdfast unlocked.
	 */
	private void letDaskGo() {
		String step = "87 let dask go";

		if (c5AtLeast(ChapterFiveStage.ARMED)) {
			note(step, Result.INFO, "Dask's fate is chosen: " + c5Line());
			return;
		}

		waitServer(200, p -> hubPersonOf(p, "dask") != null);
		UUID dask = onServer(p -> hubPersonOf(p, "dask"));
		int replies = 0;

		if (dask != null && talkTo(step, dask)) {
			context.waitTicks(10);
			tryShot("c5_dask");

			for (String option : new String[] { "buyer", "want", "show", "listen", "listen", "release" }) {
				if (sessionAt() == null || !reply(step, DLG + "c5_dask.opt." + option)) {
					break;
				}

				replies++;

				if (option.equals("show")) {
					context.waitTicks(20);
					tryShot("c5_dask_hologram");
				}
			}

			if (sessionAt() != null) {
				closeScreen();
			}
		} else {
			note(step, Result.FAIL, "Dask " + (dask == null ? "is not at the counting-house" : "would not talk") + ": " + c5Line());
		}

		boolean armed = waitServer(100, p -> ChapterFive.atLeast(p, ChapterFiveStage.ARMED));

		if (!armed && c5() == ChapterFiveStage.THE_FACTOR) {
			shortcut("let Dask go with /oldrepublic c5 dask release (his talk did not; " + step + ")");
			server.runCommand("oldrepublic c5 dask @p release");
			armed = waitServer(100, p -> ChapterFive.atLeast(p, ChapterFiveStage.ARMED));
		}

		if (!armed) {
			c5Stage(step, ChapterFiveStage.ARMED, "Dask's fate did not move the chapter on");
		}

		int fate = onServer(p -> Story.get(p).chapter5.dask());
		boolean holdfast = onServer(p -> TravelService.isUnlocked(p, Destination.HOLDFAST));
		check(step, replies == 6 && fate == StoryData.Chapter5.DASK_RELEASE && holdfast, "replies " + replies + "/6, Dask's fate " + fate + " (let go is "
				+ StoryData.Chapter5.DASK_RELEASE + "), holdfast unlocked=" + holdfast + ", " + c5Line());
	}

	/** 19.3 item 4: the free cannons from Saltgate's shipwright (his first reply in ARMED): BLOCKADE, the ship armed. */
	private void freeCannons() {
		String step = "88 free cannons";

		if (c5AtLeast(ChapterFiveStage.BLOCKADE)) {
			note(step, Result.INFO, "the ship is armed: " + c5Line());
			return;
		}

		int replies = 0;
		UUID wright = findHubPerson(step, "saltgate_shipwright", Destination.SALTGATE);

		if (wright != null) {
			replies = talkAndChoose(step, wright, "c5_shipwright", DLG + "vendor_saltgate_shipwright.opt.free_cannons");
		}

		boolean blockade = waitServer(100, p -> ChapterFive.atLeast(p, ChapterFiveStage.BLOCKADE));

		if (!blockade) {
			shortcut("fitted the free cannons with Ships.update and moved the chapter to BLOCKADE (the shipwright's reply did not; " + step + ")");
			blockade = onServer(p -> {
				Ships.update(p.level().getServer(), p.getUUID(), r -> r.withUpgrades(r.upgrades() | ShipRecord.UPGRADE_CANNONS));
				return ChapterFiveService.enterStage(p, ChapterFiveStage.BLOCKADE);
			});
		}

		boolean paid = c5Flag(ChapterFiveRules.CANNONS_PAID);
		boolean cannons = onServer(p -> ShipRegistry.of(p.level().getServer(), p.getUUID()).map(Ships::hasWeapons).orElse(false));
		check(step, replies == 1 && paid && blockade && cannons, "the free cannons chosen=" + (replies == 1) + ", given once=" + paid + ", cannons fitted=" + cannons + ", "
				+ c5Line());
	}

	/**
	 * 19.3 item 4: to Tyrvann's orbit by hand (the Helm key, the climb, the jump clicked): the blockade,
	 * fought with the cannons (the shield generator, then the gunship) for {@link #BLOCKADE_TICKS} ticks,
	 * then (logged) /oldrepublic space blockade win: PASS WITH SHORTCUT, the M3 rule.
	 */
	private void blockade() {
		String step = "89 the blockade";

		if (c5AtLeast(ChapterFiveStage.TYRVANN)) {
			note(step, Result.INFO, "the blockade is broken: " + c5Line());
			return;
		}

		if (flight == null || !flight.piloting()) {
			readyShip(step, 2);
		}

		boolean jumped = false;
		boolean fought = false;
		boolean won = false;

		if (helm(step)) {
			FlightOrbitTrip trip = Objects.requireNonNull(flight);
			ResourceKey<Level> world = trip.world();
			Planets.Planet system = world == null ? null : Planets.orbitOf(world);
			Planets.Planet from = system != null ? system : world == null ? null : Planets.Planet.of(world);

			if (system == Planets.Planet.TYRVANN) {
				jumped = true;
			} else if (from == null) {
				note(step, Result.FAIL, "the ship is in no planet's world or orbit: " + trip.describe());
			} else {
				jumped = fly(step, "the climb into " + from.id() + "'s orbit", () -> trip.toOrbit(from))
						&& fly(step, "the jump to Tyrvann's orbit", () -> trip.jumpTo(Destination.HOLDFAST));
			}

			if (jumped) {
				boolean running = waitServer(200, p -> SpaceEncounters.blockadeRunning(p) || ChapterFive.atLeast(p, ChapterFiveStage.TYRVANN));
				context.waitTicks(20);
				tryShot("c5_blockade");
				note(step, Result.INFO, "in Tyrvann's orbit: the blockade running=" + running + ", " + blockadeLine() + "; " + trip.describe());
				int[] marks = { 0 };
				fought = true;
				won = flyUntil(step, "the blockade", () -> trip.fight("the blockade", () -> {
					if (++marks[0] == 200) {
						tryShot("c5_blockade_fight");
					}

					return onServer(PlaythroughTest::blockadeMarkOf);
				}, () -> c5AtLeast(ChapterFiveStage.TYRVANN), BLOCKADE_MIN_HULL, BLOCKADE_TICKS));
				note(step, Result.INFO, "the cannons " + (won ? "broke" : "did not break") + " the blockade: " + blockadeLine() + "; " + trip.describe());
			}
		}

		if (!won && !c5AtLeast(ChapterFiveStage.TYRVANN)) {
			shortcut("won the blockade with /oldrepublic space blockade win (" + (fought ? "after " + BLOCKADE_TICKS + " ticks of cannon fire" : "it was not fought") + "; "
					+ step + ")");
			server.runCommand("oldrepublic space blockade win @p");
			waitServer(100, p -> ChapterFive.atLeast(p, ChapterFiveStage.TYRVANN));
		}

		boolean tyrvann = c5AtLeast(ChapterFiveStage.TYRVANN) || c5Stage(step, ChapterFiveStage.TYRVANN, "the blockade did not end in TYRVANN");

		if (won) {
			check(step, tyrvann, "the blockade broken with the cannons by hand: " + c5Line());
		} else if (fought && tyrvann) {
			note(step, Result.PASS, "PASS WITH SHORTCUT: the blockade was fought with the cannons and ended with /oldrepublic space blockade win: " + c5Line());
		} else {
			note(step, Result.FAIL, "the blockade was not fought (in Tyrvann's orbit by hand=" + jumped + "), TYRVANN=" + tyrvann + ": " + c5Line());
		}
	}

	/**
	 * The blockade's mark for the pilot's cannons: the gunship's shield generator while it stands, else
	 * the gunship's middle; null while no gunship of the player's blockade is in their world. Server thread.
	 */
	private static FlightOrbitTrip.@Nullable Mark blockadeMarkOf(ServerPlayer p) {
		SpaceEncounters.Snapshot s = SpaceEncounters.snapshot(p.getUUID());
		Entity e = s == null || s.gunship() == null ? null : p.level().getEntity(s.gunship());

		if (!(e instanceof BlockadeGunship gunship) || !gunship.isAlive()) {
			return null;
		}

		Vec3 velocity = ShipWeapons.velocityOf(gunship);
		GunshipGenerator generator = gunship.generator(p.level());

		if (generator != null && generator.isAlive()) {
			return new FlightOrbitTrip.Mark(generator.getBoundingBox().getCenter(), velocity, Math.max(1.0, generator.getBbWidth() / 2), GUNSHIP_NEAR, GUNSHIP_FAR,
					GUNSHIP_RISE);
		}

		return new FlightOrbitTrip.Mark(gunship.toWorld(0, BlockadeGunship.HEIGHT / 2, 0), velocity, BlockadeGunship.HALF_WIDTH, GUNSHIP_NEAR, GUNSHIP_FAR, GUNSHIP_RISE);
	}

	/** The player's blockade now, for notes. */
	private String blockadeLine() {
		return onServer(p -> {
			SpaceEncounters.Snapshot s = SpaceEncounters.snapshot(p.getUUID());

			if (s == null) {
				return "no blockade running";
			}

			Entity e = s.gunship() == null ? null : p.level().getEntity(s.gunship());
			String gunship = e instanceof BlockadeGunship g ? String.format(Locale.ROOT, "gunship %.0f/%.0f%s", g.getHealth(), g.getMaxHealth(), g.shielded() ? " (shielded)" : "")
					: "gunship " + (s.gunship() == null ? "none" : "not in this world");
			return s.type() + " wave " + s.waveIndex() + " (" + s.wave().size() + " left), " + gunship + ", destroyed " + s.destroyed() + "/" + s.spawned() + ", finished "
					+ s.finished();
		});
	}

	/** 19.3 item 5: down into Tyrvann's sky and onto the holdfast's pad by hand: TYRVANN moves to VERSES. */
	private void holdfastLanding() {
		String step = "90 the holdfast";

		if (c5AtLeast(ChapterFiveStage.VERSES) && onPlanet(Planets.Planet.TYRVANN)) {
			note(step, Result.INFO, "already on Tyrvann: " + c5Line());
			return;
		}

		boolean byHand = landBy(step, Destination.HOLDFAST);
		context.waitTicks(20);
		tryShot("c5_holdfast_landed");
		boolean verses = waitServer(200, p -> ChapterFive.atLeast(p, ChapterFiveStage.VERSES));

		if (!verses) {
			verses = c5Stage(step, ChapterFiveStage.VERSES, "landing on Tyrvann did not move the chapter to VERSES");
		}

		boolean tyrvann = onPlanet(Planets.Planet.TYRVANN);
		check(step, byHand && verses && tyrvann, "landed at the holdfast by hand=" + byHand + ", on Tyrvann=" + tyrvann + ", " + c5Line());
	}

	/**
	 * 19.3 item 5: verse stone {@code index}: to its site (a walk, or a logged teleport beyond 128
	 * blocks), its guards fought with the saber and Push, then the channel: the use key held on the
	 * stone with no guard alive, and a Push into it to cut the channel short.
	 */
	private void verseStone(String step, int index) {
		int bit = ChapterFiveRules.verseBit(index);

		if ((onServer(p -> Story.get(p).chapter5.verses()) & bit) != 0 || c5AtLeast(ChapterFiveStage.THE_HYMNAL)) {
			note(step, Result.INFO, "verse stone " + index + " is silent: " + c5Line());
			return;
		}

		if (!c5AtLeast(ChapterFiveStage.VERSES)) {
			c5Stage(step, ChapterFiveStage.VERSES, "the verse stones wake in VERSES");
		}

		if (!onPlanet(Planets.Planet.TYRVANN)) {
			arrive(step, Destination.HOLDFAST);
		}

		PlanetSites.Site site = index == 1 ? PlanetSites.Site.VERSE_STONE_1 : index == 2 ? PlanetSites.Site.VERSE_STONE_2 : PlanetSites.Site.VERSE_STONE_3;
		GlobalPos at = reachSite(step, site, Planets.Planet.TYRVANN, "verse stone " + index);
		BlockPos stone = at == null ? null : onServer(p -> p.level().dimension().equals(at.dimension()) ? verseStoneAt(p, at.pos()) : null);
		int bouts = 0;
		boolean channelled = false;
		boolean pushed = false;
		boolean cut = false;

		if (stone == null) {
			note(step, Result.FAIL, "verse stone " + index + " is not at its site: " + c5Line());
		} else {
			goTo(step, at.dimension(), Vec3.atBottomCenterOf(stone), 10, "verse stone " + index);
			igniteSaber();
			waitServer(100, p -> stoneOf(p, stone).guardsSpawned());
			StoneView before = onServer(p -> stoneOf(p, stone));
			note(step, Result.INFO, "at verse stone " + index + " (" + stone.toShortString() + "): awake=" + before.awake() + ", guards spawned=" + before.guardsSpawned()
					+ ", " + before.guards().size() + " alive");
			bouts += fightGuards(step, stone, index);
			// A Push ready for the channel (the fight may have spent it).
			waitServer(300, p -> Progression.get(p).cooldown(ForcePush.ABILITY_ID) == 0 && Progression.get(p).energy >= 25f);
			emptyHand();
			Vec3 centre = Vec3.atCenterOf(stone);
			Vec3 spot = standWithin(stone, 3.0);

			if (spot != null && flat(spot, here()) > 0.6) {
				walkTo(spot, 0.4, 200, false, null);
			}

			aimAt(centre);
			context.waitTicks(2);
			context.getInput().holdKey(USE);
			int refights = 0;
			int pushTries = 0;

			try {
				for (int t = 0; t < VERSE_CHANNEL_TICKS; t++) {
					StoneView v = onServer(p -> stoneOf(p, stone));

					if (!v.present() || !v.awake()) {
						break;
					}

					keepAlive(step);

					if (!v.guards().isEmpty()) {
						// Guards that keep coming back (or cannot be reached) end the channel; the logged command silences the stone.
						if (++refights > 2) {
							note(step, Result.INFO, "verse stone " + index + ": " + v.guards().size() + " guard(s) alive after " + bouts + " bouts; the channel is given up");
							break;
						}

						context.getInput().releaseKey(USE);
						bouts += fightGuards(step, stone, index);
						emptyHand();

						if (spot != null) {
							walkTo(spot, 0.4, 200, false, null);
						}

						aimAt(centre);
						context.waitTicks(2);
						context.getInput().holdKey(USE);
						continue;
					}

					channelled |= v.channeller() != null;

					if (v.channeller() != null && !v.forceCut() && !pushed && pushTries < 2 && pushReady()) {
						pushTries++;
						aimAt(centre);
						context.waitTick();
						pushed = castPush(step);
						// The cut is spent (or the stone silenced) within a few ticks of a Push that lands on it.
						cut |= pushed && waitServer(10, p -> {
							StoneView w = stoneOf(p, stone);
							return w.forceCut() || !w.awake();
						});
						tryShot("c5_verse_" + index + "_channel");
						continue;
					}

					aimAt(centre);
					tickMe(step);
				}
			} finally {
				context.getInput().releaseKey(USE);
			}

			context.waitTicks(10);
			tryShot("c5_verse_" + index + "_silenced");
		}

		boolean byHand = waitServer(40, p -> (Story.get(p).chapter5.verses() & bit) != 0);

		if (!byHand) {
			shortcut("silenced verse stone " + index + " with /oldrepublic c5 verse (the channel did not; " + step + ")");
			server.runCommand("oldrepublic c5 verse @p " + index);
			waitServer(40, p -> (Story.get(p).chapter5.verses() & bit) != 0);
		}

		check(step, byHand && channelled && cut, "guard bouts " + bouts + ", channelled with the use key=" + channelled + ", a Push into the channel=" + pushed
				+ " (it cut the channel=" + cut + "), silenced by the channel=" + byHand + ", " + c5Line());
	}

	/** The verse stone of the verse stone site at {@code site} (its STONE anchor), or null. Server thread. */
	private static @Nullable BlockPos verseStoneAt(ServerPlayer p, BlockPos site) {
		SitePiece piece = StructureLocator.runtimePieceAt(p.level(), site, SiteKind.VERSE_STONE);
		return piece == null ? null : SiteAnchors.find(piece, SiteAnchors.Anchor.STONE);
	}

	private static StoneView stoneOf(ServerPlayer p, BlockPos pos) {
		if (!(p.level().getBlockEntity(pos) instanceof VerseStoneBlockEntity stone)) {
			return new StoneView(false, false, false, List.of(), null, 0, false);
		}

		List<UUID> live = stone.guards().stream().filter(id -> p.level().getEntity(id) instanceof LivingEntity e && e.isAlive()).toList();
		return new StoneView(true, stone.awake(), stone.guardsSpawned(), live, stone.channeller(), stone.progress(), stone.forceCut());
	}

	/** Fights the stone's live guards with the lit saber and Push, nearest first, at most 12 bouts. Returns the bouts fought. */
	private int fightGuards(String step, BlockPos stone, int index) {
		int bouts = 0;

		while (bouts < 12) {
			UUID id = onServer(p -> stoneOf(p, stone).guards().stream().map(g -> p.level().getEntity(g)).filter(Objects::nonNull)
					.min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::getUUID).orElse(null));

			if (id == null) {
				break;
			}

			igniteSaber();

			if (bouts == 0) {
				Target t = target(id);
				aimAt(t.pos().add(0, t.height() * 0.6, 0));
				context.waitTick();
				tryShot("c5_verse_" + index + "_guards");
			}

			bouts++;
			fight(id, 600, true, step);
		}

		return bouts;
	}

	/**
	 * 19.3 item 5: the duel level for the Hymnal: an XP top-up to it when the run is short (logged);
	 * three stones silenced and the level open THE_HYMNAL.
	 */
	private void duelLevel() {
		String step = "94 duel level";

		if (c5AtLeast(ChapterFiveStage.THE_HYMNAL)) {
			note(step, Result.INFO, "the Hymnal is open: " + c5Line());
			return;
		}

		int duelLevel = onServer(p -> ChapterFiveService.balance().duelLevel);
		int levelBefore = data().level;

		if (levelBefore < duelLevel) {
			long xp = onServer(p -> {
				ForceData f = Progression.get(p);
				long total = -f.xp;

				for (int level = f.level; level < duelLevel; level++) {
					total += Progression.xpToNext(level);
				}

				return Math.max(1L, total);
			});
			shortcut("added " + xp + " XP with /oldrepublic addxp to reach level " + duelLevel + " for the Hymnal (level " + levelBefore + " from play)");
			server.runCommand("execute as @p run oldrepublic addxp " + xp);
			waitServer(20, p -> Progression.get(p).level >= duelLevel);
		}

		boolean hymnal = waitServer(100, p -> ChapterFive.atLeast(p, ChapterFiveStage.THE_HYMNAL));

		if (!hymnal) {
			hymnal = c5Stage(step, ChapterFiveStage.THE_HYMNAL, "three silenced stones and level " + duelLevel + " did not open the Hymnal");
		}

		boolean entry = data().hasFlag(JournalEntries.entryFlag(JournalEntries.THE_HYMNAL));
		check(step, hymnal, "level " + levelBefore + " -> " + data().level + ", journal the_hymnal=" + entry + ", " + c5Line());
	}

	/**
	 * 19.3 item 6: to the Hymnal (its arch a walk, or a logged teleport beyond 128 blocks) and into the
	 * Heart through its door, opened with the use key: the fight begins.
	 */
	private void hymnal() {
		String step = "95 the hymnal";

		if (c5AtLeast(ChapterFiveStage.AFTERMATH) || onServer(p -> Story.get(p).chapter5.boss()) != 0 || ulvaine().session()) {
			note(step, Result.INFO, "the fight is under way or over: " + c5Line());
			return;
		}

		if (!onPlanet(Planets.Planet.TYRVANN)) {
			arrive(step, Destination.HOLDFAST);
		}

		GlobalPos arch = reachSite(step, PlanetSites.Site.HYMNAL, Planets.Planet.TYRVANN, "the Hymnal");

		if (arch != null) {
			goTo(step, arch.dimension(), Vec3.atBottomCenterOf(arch.pos()), 8, "the Hymnal's arch");
			context.waitTicks(10);
			tryShot("c5_hymnal_arch");
		}

		boolean fight = intoTheHeart(step, true);
		check(step, fight, "into the Heart: the fight running=" + fight + ", " + ulvaineLine() + ", " + c5Line());
	}

	/**
	 * Into the Heart of the Hymnal: down to its door (a logged teleport: the halls' way down is not
	 * walked), the guards before it fought, the door opened with the use key on its plating (a logged
	 * open when that does not), and a walk through it toward the Heart's middle. Falls back on a logged
	 * teleport inside, then on /oldrepublic ulvaine start. With {@code film} the door and Ulvaine are
	 * photographed. Returns whether a fight is running.
	 */
	private boolean intoTheHeart(String step, boolean film) {
		if (!onPlanet(Planets.Planet.TYRVANN)) {
			arrive(step, Destination.HOLDFAST);
		}

		if (onServer(p -> inHeartOf(p))) {
			return true;
		}

		HeartDoor door = onServer(PlaythroughTest::heartDoorOf);
		boolean open = false;

		if (door == null) {
			note(step, Result.FAIL, "the Hymnal's halls (and the Heart's door) are not in this world: " + c5Line());
		} else {
			Vec3 outside = door.sill().subtract(door.inner().scale(3));

			if (here().distanceTo(outside) > 4) {
				Vec3 spot = standWithin(BlockPos.containing(outside.add(0, 0.5, 0)), 3.0);
				shortcut("teleported down to the Heart's door (the Hymnal's halls are not walked; " + step + ")");
				teleport(spot != null ? spot : outside, yawToward(outside, door.sill()));
			}

			igniteSaber();
			int fought = fightNear(step, 12, 600, e -> !(e instanceof DarthUlvaine) && e.position().subtract(door.sill()).dot(door.inner()) < 0);
			emptyHand();
			walkTo(door.sill().subtract(door.inner().scale(2)), 0.6, 100, false, null);
			Vec3 eyeLevel = door.sill().add(0, 1.5, 0);
			BlockPos cell = door.cells().stream().min(Comparator.comparingDouble(c -> Vec3.atCenterOf(c).distanceToSqr(eyeLevel))).orElseThrow();

			for (int i = 0; i < 3 && !open; i++) {
				aimAt(Vec3.atCenterOf(cell).subtract(door.inner().scale(0.45)));
				context.waitTicks(2);
				context.getInput().pressKey(USE);
				open = waitServer(20, p -> doorOpen(p, door));
			}

			boolean used = open;

			if (!open) {
				shortcut("opened the Heart's door with HymnalDoors.open (the use key on its plating did not; " + step + ")");
				open = onServer(p -> {
					HymnalDoors.Found found = HymnalDoors.doorAt(p.level(), cell);
					return found != null && HymnalDoors.open(p.level(), found) || doorOpen(p, door);
				});
			}

			if (film) {
				context.waitTicks(10);
				tryShot("c5_heart_door_open");
			}

			walkTo(door.sill().add(door.inner().scale(4)), 0.8, 200, false, null);
			walkTo(door.heart().centre(), 6, 300, true, () -> ulvaine().session());
			note(step, Result.INFO, "the Heart's door at " + BlockPos.containing(door.sill()).toShortString() + ": " + fought + " foe(s) fought before it, opened with the use key="
					+ used + ", open=" + open);
		}

		boolean fight = waitServer(100, p -> UlvaineFight.session(p.getUUID()) != null);

		if (!fight && door != null) {
			Vec3 inside = door.heart().centre().subtract(door.inner().scale(6));
			Vec3 spot = standWithin(BlockPos.containing(inside.add(0, 0.5, 0)), 3.0);
			shortcut("teleported into the Heart of the Hymnal (the walk through its door did not begin the fight; " + step + ")");
			teleport(spot != null ? spot : inside, yawToward(inside, door.heart().centre()));
			fight = waitServer(100, p -> UlvaineFight.session(p.getUUID()) != null);
		}

		if (!fight) {
			shortcut("began the Ulvaine fight with /oldrepublic ulvaine start (entering the Heart did not; " + step + ")");
			server.runCommand("oldrepublic ulvaine start @p");
			fight = waitServer(100, p -> UlvaineFight.session(p.getUUID()) != null);
		}

		if (fight && film) {
			context.waitTicks(40);
			UlvaineView u = ulvaine();

			if (u.id() != null) {
				aimAt(u.pos().add(0, u.height() * 0.7, 0));
				context.waitTick();
			}

			tryShot("c5_ulvaine");
		}

		return fight;
	}

	/** The Hymnal's halls piece (the Heart and its door) in the player's world, or null. Server thread. */
	private static @Nullable SitePiece hymnalHallsOf(ServerPlayer p) {
		for (WorldSites.SiteRecord r : WorldSites.all(p.level().getServer())) {
			if (r.kind() == SiteKind.HYMNAL && r.dimension().equals(p.level().dimension()) && r.template().equals(PlanetSites.HYMNAL_HALLS.template())) {
				SitePiece piece = RuntimeSites.pieceOf(p.level(), r);

				if (piece != null) {
					return piece;
				}
			}
		}

		return null;
	}

	/** The Heart's door of the halls in the player's world, or null. Server thread. */
	private static @Nullable HeartDoor heartDoorOf(ServerPlayer p) {
		SitePiece halls = hymnalHallsOf(p);
		List<BlockPos> cells = halls == null ? List.of() : SiteAnchors.all(halls, SiteAnchors.Anchor.HEART_DOOR);

		if (halls == null || cells.isEmpty()) {
			return null;
		}

		int bottom = cells.stream().mapToInt(BlockPos::getY).min().orElseThrow();
		List<BlockPos> sill = cells.stream().filter(c -> c.getY() == bottom).toList();
		Vec3 middle = new Vec3(sill.stream().mapToDouble(c -> c.getX() + 0.5).average().orElseThrow(), bottom,
				sill.stream().mapToDouble(c -> c.getZ() + 0.5).average().orElseThrow());
		// The Heart lies south of its door in the halls' own frame (HymnalDoors.Door.HEART).
		Direction inner = halls.rotation().rotate(Direction.SOUTH);
		return new HeartDoor(cells, middle, new Vec3(inner.getStepX(), 0, inner.getStepZ()), HeartLayout.of(halls));
	}

	private static boolean doorOpen(ServerPlayer p, HeartDoor door) {
		return door.cells().stream().allMatch(c -> p.level().getBlockState(c).isAir());
	}

	/** Whether the player stands in the Heart of their running Ulvaine fight. Server thread. */
	private static boolean inHeartOf(ServerPlayer p) {
		UlvaineSession s = UlvaineFight.session(p.getUUID());
		return s != null && s.level() == p.level() && s.layout().inside(p.position());
	}

	/**
	 * Fights the hostiles within {@code radius} that {@code keep} accepts with the lit saber and Push,
	 * nearest first, for at most {@code timeout} ticks each and 12 bouts. Returns the bouts fought.
	 */
	private int fightNear(String step, double radius, int timeout, Predicate<Entity> keep) {
		int bouts = 0;

		while (bouts < 12) {
			UUID id = onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(radius), e -> e instanceof Enemy && e.isAlive() && keep.test(e))
					.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::getUUID).orElse(null));

			if (id == null) {
				break;
			}

			bouts++;
			fight(id, timeout, true, step);
		}

		return bouts;
	}

	/**
	 * 19.3 item 6, one of Ulvaine's three phases with real inputs: the saber and the parry (the guard
	 * raised as a string's swing comes), the pylon chanters fought and a rod's patch left in phase 2,
	 * the pulses jumped and the resonators broken in phase 3. Each phase may take
	 * {@link #ULVAINE_PHASE_TICKS} ticks before it is moved on (logged) with /oldrepublic ulvaine phase,
	 * or with /oldrepublic ulvaine yield after the third: PASS WITH SHORTCUT, the M3 rule.
	 */
	private void ulvainePhase(String step, int n) {
		if (c5AtLeast(ChapterFiveStage.AFTERMATH) || onServer(p -> Story.get(p).chapter5.boss()) != 0) {
			note(step, Result.INFO, "Ulvaine has yielded already: " + c5Line());
			return;
		}

		if (!onServer(p -> inHeartOf(p))) {
			intoTheHeart(step, false);
		}

		UlvaineView u = ulvaine();

		if (!u.session()) {
			note(step, Result.FAIL, "no Ulvaine fight is running: " + c5Line());
			return;
		}

		if (u.phase() > n) {
			note(step, Result.INFO, "the fight is past phase " + n + " already (phase " + u.phase() + ")");
			return;
		}

		if (u.phase() < n) {
			shortcut("moved the Ulvaine fight to phase " + n + " with /oldrepublic ulvaine phase (the fight was in phase " + u.phase() + "; " + step + ")");
			server.runCommand("oldrepublic ulvaine phase @p " + n);
			waitServer(200, p -> {
				UlvaineView v = ulvaineOf(p);
				return !v.session() || v.phase() >= n && !v.transitioning() && !v.forming();
			});
		}

		HeartLayout layout = ulvaine().layout();
		int windup = onServer(p -> UlvaineBalance.get().windupTicks);
		double pulseSpeed = onServer(p -> (double) UlvaineBalance.get().pulseRadius / Math.max(1, UlvaineBalance.get().pulseTicks));
		long start = gameTime();
		long end = start + ULVAINE_PHASE_TICKS;
		float healthStart = ulvaine().health();
		int guards = 0;
		int parries = 0;
		int fights = 0;
		int chanterBouts = 0;
		int resonatorBouts = 0;
		int rods = 0;
		int pulses = 0;
		int jumps = 0;
		int stalls = 0;
		long pulseStart = -1;
		Vec3 pulseOrigin = Vec3.ZERO;
		boolean jumpedPulse = false;
		boolean rodSeen = false;
		boolean filmed = false;
		long lastTick = -1;
		igniteSaber();

		while (gameTime() < end) {
			// Every pass of the loop lets at least one tick go by (the game only runs while the test waits).
			if (gameTime() == lastTick) {
				tickMe(step);
			}

			lastTick = gameTime();
			Me me = me();

			if (me.dead()) {
				died(step);
			}

			keepAlive(step);
			UlvaineView v = ulvaine();

			if (!v.session() || v.yielded() || v.over() || v.phase() > n) {
				break;
			}

			String talk = sessionAt();

			if (talk != null) {
				if (talk.startsWith("c5_ulvaine_yield/")) {
					break;
				}

				answerOpenDialogue(step);
				continue;
			}

			if (v.transitioning() || v.forming() || v.id() == null) {
				setForward(false, false);
				tickMe(step);
				continue;
			}

			if (!filmed && gameTime() - start > 100) {
				aimAt(v.pos().add(0, v.height() * 0.7, 0));
				context.waitTick();
				tryShot("c5_ulvaine_phase_" + n);
				filmed = true;
			}

			// Out of the Heart for too long leaves the fight: back toward its middle.
			if (layout != null && (!layout.inside(me.pos()) || flat(me.pos(), layout.centre()) > layout.radius() - 2)) {
				Vec3 heart = layout.centre();

				if (!walkTo(heart, layout.radius() - 6, 100, true, null) && ++stalls >= 2) {
					stalls = 0;
					Vec3 spot = standWithin(BlockPos.containing(heart.add(away(heart, here()).scale(5)).add(0, 0.5, 0)), 3.0);
					shortcut("teleported back into the Heart after the walk to its middle stopped (" + step + ")");
					note(step, Result.INFO, "the walk back into the Heart stopped: " + walkStop);
					teleport(spot != null ? spot : heart, yawToward(here(), v.pos()));
				}

				continue;
			}

			// A Hymn pulse rolls out at pulseSpeed blocks a tick from where she stood: in the air when its front comes, it passes.
			if (v.pulse()) {
				if (pulseStart < 0) {
					pulseStart = gameTime();
					pulseOrigin = v.pos();
					jumpedPulse = false;
					pulses++;
				}

				double distance = flat(me.pos(), pulseOrigin);

				if (!jumpedPulse && me.onGround() && pulseSpeed * (gameTime() - pulseStart + 4) >= distance) {
					setForward(false, false);
					jump(step);
					jumpedPulse = true;
					jumps++;

					if (jumps == 1) {
						tryShot("c5_ulvaine_pulse");
					}

					continue;
				}
			} else {
				pulseStart = -1;
			}

			// A rod's strike falls where its target stood when the call began: out of the patch, across her line.
			if (v.rod()) {
				if (!rodSeen) {
					rodSeen = true;
					rods++;
					Vec3 patch = me.pos();
					Vec3 out = away(v.pos(), patch);
					Vec3 across = new Vec3(-out.z, 0, out.x).scale(5);
					Vec3 to = layout != null && flat(patch.add(across), layout.centre()) > layout.radius() - 3 ? patch.subtract(across) : patch.add(across);
					walkTo(to, 1.0, 25, true, null);
					continue;
				}
			} else {
				rodSeen = false;
			}

			// Her string: the guard goes up a few ticks before its first swing lands (the parry window), then holds through the string.
			if (stringOn(v, me)) {
				setForward(false, false);

				for (int i = 0; i < Math.max(0, windup - 5); i++) {
					tickMe(step);
				}

				boolean parried = guardAgainstUlvaine(step, 16);
				guards++;
				parries += parried ? 1 : 0;

				if (parried && parries == 1) {
					tryShot("c5_ulvaine_parry");
				}

				continue;
			}

			boolean watchPulse = pulseStart < 0 || !jumpedPulse;
			boolean watchRod = !rodSeen;

			if (n == 2 && !v.chanters().isEmpty()) {
				UUID chanter = nearestId(v.chanters());

				if (chanter != null) {
					chanterBouts++;
					fight(chanter, 100, true, step, () -> phaseInterrupt(n, watchPulse, watchRod));
					continue;
				}
			}

			if (n == 3 && !v.resonators().isEmpty()) {
				UUID resonator = nearestId(v.resonators());

				if (resonator != null) {
					resonatorBouts++;
					fight(resonator, 100, false, step, () -> phaseInterrupt(n, watchPulse, watchRod));
					continue;
				}
			}

			fights++;
			fight(v.id(), 60, false, step, () -> phaseInterrupt(n, watchPulse, watchRod));
		}

		releaseAll();
		UlvaineView after = ulvaine();
		boolean yielded = after.yielded() || onServer(p -> Story.get(p).chapter5.boss()) >= ChapterFiveRules.BOSS_YIELDED;
		boolean advanced = yielded || after.session() && after.phase() > n;
		String done = String.format(Locale.ROOT, "%d ticks, Ulvaine health %d -> %d of %d, %d guard(s) at her strings (%d parried), %d melee bout(s), %d chanter bout(s),"
				+ " %d rod patch(es) left, %d pulse(s) (%d jumped), %d resonator bout(s)", gameTime() - start, Math.round(healthStart), Math.round(after.health()),
				Math.round(after.maxHealth()), guards, parries, fights, chanterBouts, rods, pulses, jumps, resonatorBouts);

		if (advanced) {
			check(step, true, "phase " + n + " won in play: " + done);
			return;
		}

		if (!after.session()) {
			note(step, Result.FAIL, "the fight ended without a yield: " + done + ", " + c5Line());
			return;
		}

		if (n < 3) {
			shortcut("moved the Ulvaine fight to phase " + (n + 1) + " with /oldrepublic ulvaine phase after the " + ULVAINE_PHASE_TICKS + "-tick timeout (" + step + ")");
			server.runCommand("oldrepublic ulvaine phase @p " + (n + 1));
			waitServer(100, p -> ulvaineOf(p).phase() > n);
		} else {
			shortcut("made Ulvaine yield with /oldrepublic ulvaine yield after the " + ULVAINE_PHASE_TICKS + "-tick timeout of phase 3 (" + step + ")");
			server.runCommand("oldrepublic ulvaine yield @p");
			waitServer(100, p -> Story.get(p).chapter5.boss() >= ChapterFiveRules.BOSS_YIELDED);
		}

		note(step, Result.PASS, "PASS WITH SHORTCUT: phase " + n + " timed out after " + ULVAINE_PHASE_TICKS + " ticks and was moved on with "
				+ (n < 3 ? "/oldrepublic ulvaine phase " + (n + 1) : "/oldrepublic ulvaine yield") + ": " + done);
	}

	/** Whether Ulvaine swings a string at the player now: her guard on the attack with no Force move, her middle within reach. */
	private static boolean stringOn(UlvaineView v, Me me) {
		return v.guard() == GuardState.ATTACKING && v.action() == DarthUlvaine.Action.NONE && me.eye().distanceTo(v.pos().add(0, v.height() * 0.5, 0)) <= 4.5;
	}

	/**
	 * Whether a melee bout of phase {@code n} should stop: the fight moved on, a talk opened, a string
	 * comes, or (when watched) a pulse rolls or a rod charges.
	 */
	private boolean phaseInterrupt(int n, boolean watchPulse, boolean watchRod) {
		UlvaineView w = ulvaine();
		return !w.session() || w.yielded() || w.over() || w.phase() != n || w.transitioning() || w.forming() || stringOn(w, me())
				|| n == 3 && watchPulse && w.pulse() || n == 2 && watchRod && w.rod() || sessionAt() != null;
	}

	/** Holds the guard (use, saber lit) for {@code duration} ticks, facing Ulvaine over her head. Returns whether she was staggered meanwhile (a parry). */
	private boolean guardAgainstUlvaine(String step, int duration) {
		boolean staggered = false;
		setForward(false, false);
		context.getInput().holdKey(USE);

		try {
			for (int i = 0; i < duration; i++) {
				UlvaineView u = ulvaine();
				staggered |= u.staggered();

				if (u.id() != null) {
					aimAt(u.pos().add(0, u.height() + 2.5, 0));
				}

				keepAlive(step);
				tickMe(step);
			}
		} finally {
			context.getInput().releaseKey(USE);
		}

		return staggered || ulvaine().staggered();
	}

	/** The live one of {@code ids} nearest the player, or null. */
	private @Nullable UUID nearestId(List<UUID> ids) {
		Vec3 me = here();
		UUID best = null;
		double bestDistance = Double.MAX_VALUE;

		for (UUID id : ids) {
			Target t = target(id);

			if (t.alive() && t.pos().distanceToSqr(me) < bestDistance) {
				best = id;
				bestDistance = t.pos().distanceToSqr(me);
			}
		}

		return best;
	}

	private UlvaineView ulvaine() {
		return onServer(p -> ulvaineOf(p));
	}

	private static UlvaineView ulvaineOf(ServerPlayer p) {
		UlvaineSession s = UlvaineFight.session(p.getUUID());

		if (s == null) {
			return new UlvaineView(false, 0, false, false, false, false, null, Vec3.ZERO, 0, 0f, 0, DarthUlvaine.Action.NONE, GuardState.READY, false, List.of(), List.of(),
					false, false, null);
		}

		DarthUlvaine u = s.ulvaine();
		DuelState duel = u == null ? null : NpcGuard.state(u);
		return new UlvaineView(true, s.phase(), s.isTransitioning(), s.isForming(), s.isYielded(), s.isOver(), u == null ? null : u.getUUID(),
				u == null ? Vec3.ZERO : u.position(), u == null ? 0 : u.getBbHeight(), u == null ? 0f : u.getHealth(), s.maxHealth(),
				u == null ? DarthUlvaine.Action.NONE : u.action(), u == null ? GuardState.READY : u.guardState(), duel != null && duel.isStaggered(p.level().getGameTime()),
				s.chanters().stream().map(Entity::getUUID).toList(), s.resonators().stream().map(Entity::getUUID).toList(), s.pulseActive(), s.rodCharging(), s.layout());
	}

	/** The Ulvaine fight now, for notes. */
	private String ulvaineLine() {
		UlvaineView u = ulvaine();
		return !u.session() ? "no Ulvaine fight" : String.format(Locale.ROOT, "Ulvaine phase %d%s%s%s, health %.0f/%.0f, %s %s, %d chanter(s), %d resonator(s)%s%s",
				u.phase(), u.transitioning() ? " (changing)" : "", u.yielded() ? " (yielded)" : "", u.over() ? " (over)" : "", u.health(), u.maxHealth(), u.action(),
				u.guard(), u.chanters().size(), u.resonators().size(), u.pulse() ? ", a pulse rolling" : "", u.rod() ? ", a rod charging" : "");
	}

	/** 19.3 item 6: Ulvaine yields and kneels; her talk is answered Spare (the chapter moves to AFTERMATH). */
	private void spareUlvaine() {
		String step = "99 spare";
		String spareFlag = ChapterFiveRules.CHOICE_PREFIX + "spare";

		if (c5AtLeast(ChapterFiveStage.AFTERMATH)) {
			boolean spared = c5Flag(spareFlag);
			note(step, spared ? Result.INFO : Result.FAIL, "the fight is over: " + c5Line() + ", spared=" + spared);
			return;
		}

		boolean yielded = waitServer(200, p -> Story.get(p).chapter5.boss() >= ChapterFiveRules.BOSS_YIELDED);

		if (!yielded) {
			shortcut("made Ulvaine yield with /oldrepublic c5 yield (she had not yielded; " + step + ")");
			server.runCommand("oldrepublic c5 yield @p");
			yielded = waitServer(40, p -> Story.get(p).chapter5.boss() >= ChapterFiveRules.BOSS_YIELDED);
		}

		boolean shown = waitServer(100, p -> "c5_ulvaine_yield/yield".equals(sessionAt(p)));

		if (!shown) {
			// A hit closes a dialogue: once the fight settles, the knelt Ulvaine answers her owner's right-click with the same talk.
			settleBeforeDialogue(step);
			UlvaineView u = ulvaine();

			if (u.id() != null) {
				talkTo(step, u.id());
			}

			shown = waitServer(40, p -> "c5_ulvaine_yield/yield".equals(sessionAt(p)));
		}

		if (!shown) {
			shortcut("reopened Ulvaine's yield with /oldrepublic c5 yield (her talk was not open; " + step + ")");
			server.runCommand("oldrepublic c5 yield @p");
			shown = waitServer(40, p -> "c5_ulvaine_yield/yield".equals(sessionAt(p)));
		}

		boolean chose = false;

		if (shown) {
			context.waitTicks(20);
			tryShot("c5_ulvaine_yield");

			if (sessionAt() != null) {
				chose = reply(step, DLG + "c5_ulvaine_yield.opt.spare");
			}
		}

		boolean aftermath = waitServer(100, p -> ChapterFive.atLeast(p, ChapterFiveStage.AFTERMATH));

		if (!aftermath) {
			if (sessionAt() != null) {
				closeScreen();
			}

			shortcut("chose Spare with /oldrepublic c5 choose spare (the yield's Spare did not; " + step + ")");
			server.runCommand("oldrepublic c5 choose @p spare");
			aftermath = waitServer(100, p -> ChapterFive.atLeast(p, ChapterFiveStage.AFTERMATH));
		}

		context.waitTicks(40);
		tryShot("c5_ulvaine_spared");
		boolean spared = c5Flag(spareFlag);
		check(step, yielded && shown && chose && aftermath && spared, "yielded=" + yielded + ", her talk shown=" + shown + ", Spare chosen=" + chose + ", spare recorded="
				+ spared + ", " + c5Line());
	}

	/** 19.3 item 6: home, and the report to Saelen (the spare reply): COMPLETE. */
	private void reportChapterFive() {
		String step = "100 report";

		if (c5AtLeast(ChapterFiveStage.COMPLETE)) {
			note(step, Result.INFO, "the report is made: " + c5Line());
			return;
		}

		if (!c5AtLeast(ChapterFiveStage.AFTERMATH)) {
			c5Stage(step, ChapterFiveStage.AFTERMATH, "the Ulvaine fight did not end");
		}

		endFlight();
		boolean flew = flyTo(step, Destination.HOME, false);
		homeWorld(step);
		backToCamp(step);
		UUID jedi = story().jediMentor;
		int replies = 0;

		if (jedi == null) {
			note(step, Result.FAIL, "no Jedi mentor to report to: " + c5Line());
		} else {
			waitServer(100, p -> p.level().getEntity(jedi) instanceof MentorEntity);
			replies = talkAndChoose(step, jedi, "c5_report", DLG + "c5_hub_saelen.opt.report_spare");
		}

		boolean complete = waitServer(100, p -> Story.get(p).chapter5.stage() == ChapterFiveStage.COMPLETE);

		if (!complete) {
			shortcut("made the report with /oldrepublic c5 report (Saelen's report did not; " + step + ")");
			server.runCommand("oldrepublic c5 report @p");
			complete = waitServer(40, p -> Story.get(p).chapter5.stage() == ChapterFiveStage.COMPLETE);
		}

		check(step, replies == 1 && complete, "flew home=" + flew + ", reported the spare to Saelen=" + (replies == 1) + ", " + c5Line());
	}

	/** 19.3 item 6: the end state: COMPLETE, the report's entry and the rank 4 checklist shown (the Master's); the journal shown. */
	private void chapterFiveState() {
		String step = "101 chapter five state";
		closeScreen();
		context.getInput().pressKey(InputConstants.KEY_J);
		boolean journal = waitClient(40, mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("JournalScreen"));

		if (journal) {
			context.waitTicks(10);
			tryShot("c5_journal");
		}

		closeScreen();
		ChapterFiveStage stage = c5();
		boolean entry = data().hasFlag(JournalEntries.entryFlag(JournalEntries.REPORT_C5));
		RankFourChecklistPayload list = onServer(p -> RankFour.checklist(p));
		List<String> rows = list.entries().stream().map(e -> e.id() + (e.met() ? " met" : " " + e.have() + "/" + e.need())).toList();
		server.runCommand("oldrepublic c5 status @p");
		check(step, stage == ChapterFiveStage.COMPLETE && entry && list.visible() && list.kind() == RankFourChecklistPayload.MASTER, "chapter five " + stage
				+ ", journal report_c5=" + entry + " (journal shown=" + journal + "), the rank 4 checklist shown=" + list.visible() + " for kind " + list.kind() + " (the Master's is "
				+ RankFourChecklistPayload.MASTER + "): " + String.join(", ", rows));
	}

	/** 19.3 item 7: the rank 4 requirements met by a logged shortcut (the level, two powers mastered, the mentor's trust, the Order's standing, the deeds). */
	private void rankFourRequirements() {
		String step = "102 rank four";

		if (onServer(p -> RankFour.done(p))) {
			note(step, Result.INFO, "rank 4 is done: " + standing());
			return;
		}

		String before = onServer(p -> RankFour.blocker(p));
		shortcut("met the rank 4 requirements with TurningClientSteps.readyForRankFour: the level, Push and Speed mastered, the mentor's trust, the Order's standing,"
				+ " five bounties and five contracts (" + step + ")");
		onServer(p -> {
			TurningClientSteps.readyForRankFour(p, com.oldrepublic.identity.Path.JEDI);
			return null;
		});
		context.waitTicks(5);
		String after = onServer(p -> RankFour.blocker(p));
		check(step, after == null, "rank 4 blocker before: " + before + ", after: " + after + "; " + standing());
	}

	/** 19.3 item 7: the Master ceremony at Saelen with real dialogue clicks (the letter read, accepted): rank 4, force_valor, slots 7 and 8, the robe. */
	private void masterCeremony() {
		String step = "103 master ceremony";
		homeWorld(step);
		backToCamp(step);
		UUID jedi = story().jediMentor;
		int rankBefore = onServer(p -> Identity.rank(p));
		int replies = 0;

		if (jedi == null) {
			note(step, Result.FAIL, "no Jedi mentor for the ceremony: " + standing());
		} else if (!onServer(p -> RankFour.done(p))) {
			waitServer(100, p -> p.level().getEntity(jedi) instanceof MentorEntity);
			settleBeforeDialogue(step);
			replies = talkAndChoose(step, jedi, "c5_master", DLG + "rank4_saelen.opt.read", DLG + "rank4_saelen.opt.accept");
		}

		boolean done = waitServer(100, p -> RankFour.done(p));
		context.waitTicks(30);
		tryShot("c5_master");
		int rank = onServer(p -> Identity.rank(p));
		boolean valor = onServer(p -> SkillService.data(p).owns(TurningRules.FORCE_VALOR));
		boolean slots = onServer(p -> Skills.isOpen(p, 6) && Skills.isOpen(p, 7));
		String missing = onServer(p -> TurningClientSteps.afterCeremony(p, com.oldrepublic.identity.Path.JEDI));
		check(step, replies == 2 && done && rank == 4 && valor && slots && missing == null, "replies " + replies + "/2, rank " + rankBefore + " -> " + rank
				+ ", force_valor owned=" + valor + ", slots 7 and 8 open=" + slots + ", " + (missing == null ? "the robe given" : missing) + "; " + standing());
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Five helpers
	// ---------------------------------------------------------------------------------------------

	private ChapterFiveStage c5() {
		return onServer(p -> Story.get(p).chapter5.stage());
	}

	private boolean c5AtLeast(ChapterFiveStage stage) {
		return onServer(p -> ChapterFive.atLeast(p, stage));
	}

	private boolean c5Flag(String flag) {
		return onServer(p -> Story.get(p).hasFlag(flag));
	}

	private boolean onPlanet(Planets.Planet planet) {
		return onServer(p -> p.level().dimension().equals(planet.level()));
	}

	private String c5Line() {
		return onServer(p -> {
			StoryData d = Story.get(p);
			StoryData.Chapter5 c = d.chapter5;
			MissionData.Active active = MissionService.get(p).active;
			return "chapter five " + c.stage() + ", way " + c.chosenWay() + ", dask " + c.dask() + ", blockade failures " + c.blockadeFailures() + ", verses "
					+ ChapterFiveRules.versesSilenced(c.verses()) + "/3, boss " + c.boss() + " (" + c.ulvaineOutcome() + "), flags "
					+ d.flags.stream().filter(f -> f.startsWith("c5.")).sorted().toList() + ", level " + Progression.get(p).level + ", credits " + Credits.count(p, false)
					+ ", mission " + (active == null ? "none" : active.id + " " + active.stage);
		});
	}

	private String c5Where() {
		return onServer(p -> p.level().dimension().identifier().getPath() + " " + p.blockPosition().toShortString() + (Ships.isAboard(p) ? " (aboard)" : "")
				+ ", chapter five " + Story.get(p).chapter5.stage()) + "; " + shipLine() + (flight != null && flight.piloting() ? ", at the helm" : "");
	}

	/** Moves Chapter Five forward with /oldrepublic c5 stage (a logged shortcut) and waits for it. */
	private boolean c5Stage(String step, ChapterFiveStage stage, String why) {
		shortcut("moved Chapter Five to " + stage.id() + " with /oldrepublic c5 stage (" + why + "; " + step + ")");
		server.runCommand("oldrepublic c5 stage @p " + stage.id());
		return waitServer(40, p -> ChapterFive.atLeast(p, stage));
	}

	private static MissionData.@Nullable Stage missionStageOf(ServerPlayer p) {
		MissionData.Active active = MissionService.get(p).active;
		return active == null ? null : active.stage;
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Five: the ship by hand (FlightOrbitTrip's pilot) and the hubs
	// ---------------------------------------------------------------------------------------------

	/** One leg of the flight by hand; a leg that cannot go on is a FAIL with a shot, and the step falls back. Returns whether it flew. */
	private boolean fly(String step, String what, Runnable leg) {
		return flyUntil(step, what, () -> {
			leg.run();
			return true;
		});
	}

	/** As {@link #fly} for a leg that ends true or false (a cruise, a fight). */
	private boolean flyUntil(String step, String what, BooleanSupplier leg) {
		try {
			return leg.getAsBoolean();
		} catch (AssertionError | RuntimeException e) {
			note(step, Result.FAIL, what + " by hand: " + e.getMessage());
			LOG.warn("PLAYTHROUGH-LOG: {}: {} failed", step, what, e);
			pauseFlight();
			tryShot("c5_flight_failed");
			return false;
		} finally {
			pilotShortcuts();
		}
	}

	/**
	 * At the ship's helm by hand: aboard with a right-click ({@link #boardShip}), then a real press of
	 * the Helm key through the pilot. Returns whether the player flies the ship.
	 */
	private boolean helm(String step) {
		if (flight != null && flight.piloting()) {
			return true;
		}

		endFlight();
		// The helm is refused in combat, like a launch.
		settleBeforeDialogue(step);

		if (!boardShip(step)) {
			note(step, Result.FAIL, "could not board the ship to take its helm: " + shipLine());
			return false;
		}

		FlightOrbitTrip trip = FlightOrbitTrip.pilot(context, server, onServer(p -> p.getUUID()));
		flight = trip;
		flightShortcuts = 0;

		if (trip.piloting()) {
			return true;
		}

		boolean took = fly(step, "taking the helm", trip::takeHelm);

		if (took) {
			note(step, Result.INFO, "the Helm key handed the ship over: " + trip.describe());
		}

		return took;
	}

	/** Logs the pilot's new shortcuts as the playthrough's own. */
	private void pilotShortcuts() {
		FlightOrbitTrip trip = flight;

		if (trip == null) {
			return;
		}

		List<String> taken = trip.shortcuts();

		for (int i = flightShortcuts; i < taken.size(); i++) {
			shortcut("the pilot: " + taken.get(i));
		}

		flightShortcuts = taken.size();
	}

	/** Lets go of the pilot's keys and any ship screen; the ship stays where it is and the pilot with it. */
	private void pauseFlight() {
		FlightOrbitTrip trip = flight;

		if (trip != null) {
			try {
				trip.release();
			} catch (RuntimeException | AssertionError e) {
				LOG.warn("PLAYTHROUGH-LOG: could not let go of the pilot's keys", e);
			}
		}
	}

	/** Done with the pilot: its shortcuts logged, its keys let go. */
	private void endFlight() {
		if (flight != null) {
			pilotShortcuts();
			pauseFlight();
			flight = null;
		}
	}

	/**
	 * The ship ready for a flight of {@code cells} jumps: welded back over half its hull, fuelled with
	 * the Status tab's Fuel button (cells from a logged /give). Not while it is flown.
	 */
	private void readyShip(String step, int cells) {
		waitServer(1500, p -> {
			ShipView v = shipOf(p);
			return v == null || !v.flying();
		});
		ShipView s = ship();

		if (s == null || s.state() == ShipState.HULK) {
			note(step, Result.FAIL, "no ship to fly: " + shipLine());
			return;
		}

		if (s.state() == ShipState.DISABLED || s.hull() < 50) {
			weld(step, 80);
		}

		ShipView now = ship();

		if (now != null && now.fuel() < cells) {
			giveMissing(step, M4Items.HYPERFUEL_CELL, cells - now.fuel(), "hyperfuel cells for " + cells + " jump(s)");
			boolean fuelled = openShipScreen(step) && refuelOnScreen(step);
			closeScreen();

			if (!fuelled) {
				shortcut("fuelled the ship with /oldrepublic ship fuel (the Fuel button did not; " + step + ")");
				server.runCommand("oldrepublic ship fuel @p");
				waitServer(20, p -> {
					ShipView v = shipOf(p);
					return v != null && v.fuel() >= cells;
				});
			}
		}
	}

	/**
	 * Down to {@code to}'s hub by hand: from its orbit across to the point above the hub and the dive,
	 * or from its sky, then the landing on its pad with the Helm key ({@link FlightOrbitTrip#toSky},
	 * {@link FlightOrbitTrip#landAt}), and off the ship. Anything else (no pilot, a leg that failed)
	 * falls back on {@link #arrive}. Returns whether the landing was made by hand.
	 */
	private boolean landBy(String step, Destination to) {
		Planets.Planet planet = Objects.requireNonNull(to.planet());
		FlightOrbitTrip trip = flight;
		boolean byHand = false;

		if (trip != null && trip.piloting()) {
			ResourceKey<Level> world = trip.world();

			if (Planets.orbit(planet).equals(world)) {
				byHand = fly(step, "the dive into " + planet.id() + "'s sky", () -> trip.toSky(planet)) && fly(step, "the landing at " + to.id(), () -> trip.landAt(planet));
			} else if (planet.level().equals(world)) {
				byHand = fly(step, "the landing at " + to.id(), () -> trip.landAt(planet));
			}
		}

		if (!byHand) {
			arrive(step, to);
			return false;
		}

		endFlight();
		dismount(step);
		waitForChunks(step);
		return true;
	}

	/**
	 * On {@code to}'s planet, landed, when the flight by hand did not get there: the autopilot (the
	 * Nav tab's Land at the helm, or the ship screen's Launch on the ground; logged as the fallback),
	 * else a logged teleport beside the hub. Returns whether the player is on that planet.
	 */
	private boolean arrive(String step, Destination to) {
		Planets.Planet planet = Objects.requireNonNull(to.planet());
		FlightOrbitTrip trip = flight;

		if (trip != null && trip.piloting()) {
			fly(step, "the autopilot to " + to.id(), () -> trip.autopilot(to));
		}

		endFlight();

		if (!onPlanet(planet)) {
			shortcut("flew to " + to.id() + " on the autopilot (the ship screen's Launch), the fallback for the flight by hand (" + step + ")");
			flyTo(step, to, false);
		}

		dismount(step);

		if (!onPlanet(planet)) {
			atHub(step, planet);
		}

		waitForChunks(step);
		return onPlanet(planet);
	}

	/** Beside {@code planet}'s hub by a logged teleport (no flight took the player there). */
	private void atHub(String step, Planets.Planet planet) {
		BlockPos hub = onServer(p -> PlanetHubs.hub(p.level().getServer(), planet));

		if (hub == null) {
			onServer(p -> PlanetHubs.ensure(p.level().getServer(), planet));
			waitServer(400, p -> PlanetHubs.hub(p.level().getServer(), planet) != null);
			hub = onServer(p -> PlanetHubs.hub(p.level().getServer(), planet));
		}

		if (hub == null) {
			note(step, Result.FAIL, planet.id() + "'s hub is not placed: " + c5Line());
			return;
		}

		Vec3 centre = Vec3.atBottomCenterOf(hub);
		Vec3 spot = safeSpotIn(planet.level(), centre.add(0, 0, 12));
		shortcut("teleported to " + planet.id() + "'s hub (no flight took the player there; " + step + ")");
		teleportIn(planet.level(), spot, yawToward(spot, centre));
	}

	/** The hub person of {@code variant} within 160 blocks who answers this player (a story person only its owner), the nearest. Server thread. */
	private static @Nullable UUID hubPersonOf(ServerPlayer p, String variant) {
		return p.level().getEntities((Entity) null, p.getBoundingBox().inflate(160), e -> e instanceof HubNpc npc && e.isAlive() && variant.equals(npc.variant())
				&& (npc.storyOwner() == null || p.getUUID().equals(npc.storyOwner()))).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p)))
				.map(Entity::getUUID).orElse(null);
	}

	/**
	 * The hub person of {@code variant} at {@code to}'s hub: on its planet first ({@link #arrive}), to
	 * the hub when it is far (a walk, or a logged teleport), then a wait for them. Null (a FAIL) when
	 * they are not there.
	 */
	private @Nullable UUID findHubPerson(String step, String variant, Destination to) {
		Planets.Planet planet = Objects.requireNonNull(to.planet());

		if (!onPlanet(planet)) {
			arrive(step, to);
		}

		BlockPos hub = onServer(p -> PlanetHubs.hub(p.level().getServer(), planet));

		if (hub != null && flat(Vec3.atBottomCenterOf(hub), here()) > 48) {
			goTo(step, planet.level(), surfaceIn(planet.level(), hub), 24, planet.id() + "'s hub");
		}

		waitServer(100, p -> hubPersonOf(p, variant) != null);
		UUID id = onServer(p -> hubPersonOf(p, variant));

		if (id == null) {
			note(step, Result.FAIL, "no " + variant + " near " + planet.id() + "'s hub: " + c5Line());
		}

		return id;
	}

	/**
	 * Near {@code site} on {@code planet}: when it is not placed, to its ring point (a walk, or a
	 * logged teleport beyond {@link #C4_WALK_LIMIT}) and a wait for it to be placed, then (logged) up
	 * to three /oldrepublic planet site. Returns where it stands, or null.
	 */
	private @Nullable GlobalPos reachSite(String step, PlanetSites.Site site, Planets.Planet planet, String what) {
		GlobalPos at = onServer(p -> PlanetSites.find(p, site));

		if (at == null) {
			GlobalPos ring = onServer(p -> PlanetSites.ringPoint(p.level().getServer(), p.getUUID(), site, planet));

			if (ring != null) {
				goTo(step, ring.dimension(), surfaceIn(ring.dimension(), ring.pos()), 24, what + "'s ring point");
				waitServer(400, p -> PlanetSites.find(p, site) != null);
				at = onServer(p -> PlanetSites.find(p, site));
			}
		}

		String id = site.name().toLowerCase(Locale.ROOT);

		for (int i = 0; i < 3 && at == null; i++) {
			shortcut("placed " + what + " with /oldrepublic planet site (it was not placed when the player came near; " + step + ")");
			server.runCommand("oldrepublic planet site @p " + id);
			waitServer(100, p -> PlanetSites.find(p, site) != null);
			at = onServer(p -> PlanetSites.find(p, site));
		}

		note(step, at == null ? Result.FAIL : Result.INFO, what + (at == null ? " is not placed: " + c5Line()
				: " stands at " + at.dimension().identifier().getPath() + " " + at.pos().toShortString()));
		return at;
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Three helpers
	// ---------------------------------------------------------------------------------------------

	private ChapterThreeStage c3() {
		return onServer(p -> Story.get(p).chapter3.stage());
	}

	private boolean c3AtLeast(ChapterThreeStage stage) {
		return onServer(p -> Story.get(p).chapter3.stage().ordinal() >= stage.ordinal());
	}

	private String c3Line() {
		return onServer(p -> {
			StoryData.Chapter3 c = Story.get(p).chapter3;
			return "chapter three " + c.stage() + ", leads " + c.leads() + ", cairns touched " + Integer.bitCount(c.cairnsTouched()) + "/" + c.cairns().size()
					+ ", reliquary " + shortPos(c.reliquary()) + ", boss " + c.boss() + " (outcome " + c.outcome() + ")";
		});
	}

	/** Moves Chapter Three forward with /oldrepublic c3 stage (a logged shortcut) and waits for it. */
	private boolean c3Stage(String step, ChapterThreeStage stage, String why) {
		String name = stage.name().toLowerCase(Locale.ROOT);
		shortcut("moved Chapter Three to " + name + " with /oldrepublic c3 stage (" + why + "; " + step + ")");
		server.runCommand("oldrepublic c3 stage @p " + name);
		return waitServer(40, p -> Story.get(p).chapter3.stage().ordinal() >= stage.ordinal());
	}

	private static String describe(List<ChapterThreeRules.Requirement> requirements) {
		return requirements.stream().map(r -> r.id() + " " + r.have() + "/" + r.need() + (r.met() ? "" : " (unmet)")).toList().toString();
	}

	private long gameTime() {
		return onServer(p -> p.level().getGameTime());
	}

	/** Casts Force Sense with its key (H) once its cooldown is over. Returns whether it went through (its cooldown started). */
	private boolean sense(String step) {
		waitServer(260, p -> Progression.get(p).cooldown(SkillIds.FORCE_SENSE) == 0 && Progression.get(p).energy >= 15f);
		context.getInput().pressKey(ForceKeys.FORCE_SENSE);
		boolean cast = waitServer(10, p -> Progression.get(p).cooldown(SkillIds.FORCE_SENSE) > 0);
		note(step, cast ? Result.INFO : Result.FAIL, "pressed H for Force Sense: " + (cast ? "cast" : "no cast (energy " + Math.round(data().energy) + ")"));
		return cast;
	}

	/** Charges and releases Force Push with its slot key at whatever the crosshair is on, once it is ready. Returns whether it was cast. */
	private boolean castPush(String step) {
		int slot = slotOf(ForcePush.ABILITY_ID);

		if (slot < 0) {
			note(step, Result.FAIL, "Force Push is in no slot: " + onServer(p -> SkillService.data(p).slots.toString()));
			return false;
		}

		// Three Pushes in a row (the counterweights) drain the energy: wait for it to come back.
		if (!waitServer(600, p -> Progression.get(p).cooldown(ForcePush.ABILITY_ID) == 0 && Progression.get(p).energy >= 25f)) {
			note(step, Result.INFO, "Push still not ready after 600 ticks: cooldown " + onServer(p -> Progression.get(p).cooldown(ForcePush.ABILITY_ID)) + ", energy "
					+ onServer(p -> Math.round(Progression.get(p).energy)));
		}

		pushKey(slot);
		return waitServer(10, p -> Progression.get(p).cooldown(ForcePush.ABILITY_ID) > 0);
	}

	/** Holds the Push slot key past the minimum charge and releases it. */
	private void pushKey(int slot) {

		try {
			holdSlot(slot);
			context.waitTicks(PUSH_HOLD_TICKS);
		} finally {
			releaseSlot(slot);
		}
	}

	/** Aims at {@code point} and taps the Force Pull slot key, if Pull is ready. Returns whether it was tapped. */
	private boolean pullAt(Vec3 point, int slot) {
		if (slot < 0 || !pullReady()) {
			return false;
		}

		aimAt(point);
		context.waitTick();
		// A real tap lasts a tick or two (pressKey presses and releases within one frame).
		holdSlot(slot);
		context.waitTicks(2);
		releaseSlot(slot);
		return true;
	}

	private boolean pullReady() {
		return onServer(p -> Progression.get(p).cooldown("force_pull") == 0 && Progression.get(p).energy >= 16f);
	}

	private boolean jumpReady() {
		return onServer(p -> Progression.get(p).cooldown("force_jump") == 0 && Progression.get(p).energy >= 24f);
	}

	/** Whether no solid block lies between two points (a server ray); a block right at {@code to} (a pylon under its crystal) does not count. */
	private boolean clearLine(Vec3 from, Vec3 to) {
		return onServer(p -> {
			BlockHitResult hit = p.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
			return hit.getType() == HitResult.Type.MISS || Vec3.atCenterOf(hit.getBlockPos()).distanceTo(to) <= 1.0;
		});
	}

	private boolean sightTo(Vec3 point) {
		return clearLine(me().eye(), point);
	}

	/** Back at the mentor camp: walks when it is near, else (or when the walk stops) a logged teleport beside it. */
	private void backToCamp(String step) {
		BlockPos camp = story().camp;

		if (camp == null) {
			note(step, Result.FAIL, "no mentor camp: " + storyLine());
			return;
		}

		Vec3 centre = Vec3.atBottomCenterOf(camp);
		double distance = flat(centre, here());

		if (distance <= 16) {
			return;
		}

		if (distance <= 160) {
			select(QuestItems.WANDERERS_JOURNAL);

			if (walkTo(centre, 6, 1800, true, null)) {
				return;
			}

			note(step, Result.INFO, "walk back to the camp stopped " + Math.round(flat(centre, here())) + " blocks away: " + walkStop);
		}

		Vec3 spot = safeSpotNear(centre.add(away(centre, here()).scale(8)));
		shortcut("teleported back to the mentor camp (" + Math.round(distance) + " blocks away; " + step + ")");
		teleport(spot, yawToward(spot, centre));
	}

	/**
	 * Walks (sprinting) to within {@code near} of {@code target}. More than 200 blocks away it first
	 * teleports to 48 blocks short, and a walk that stops ends with a teleport the rest of the way
	 * (both logged). Returns whether it walked all the way.
	 */
	private boolean travel(String step, Vec3 target, double near, String what) {
		double distance = flat(target, here());

		if (distance <= near) {
			return true;
		}

		if (distance > 200) {
			Vec3 spot = safeSpotNear(target.add(away(target, here()).scale(48)));
			shortcut("teleported to 48 blocks short of " + what + " (" + Math.round(distance) + " blocks away; " + step + ")");
			teleport(spot, yawToward(spot, target));
		}

		leaveSite(step);
		boolean walked = walkTo(target, near, 1800, true, null);

		if (!walked) {
			Vec3 now = here();
			note(step, Result.INFO, "walk to " + what + " stopped " + Math.round(flat(target, now)) + " blocks away: " + walkStop);
			Vec3 spot = safeSpotNear(target.add(away(target, now).scale(Math.max(2, near - 2))));
			shortcut("teleported the rest of the way to " + what + " after the walk stopped (" + step + ")");
			teleport(spot, yawToward(spot, target));
		}

		return walked && distance <= 200;
	}

	/**
	 * Walks a way through a site to the standing cell {@code goal}: a path over standing cells inside
	 * {@code bounds} (a block up, or up to three down, as {@link #walkableSteps}), walked corner to
	 * corner. A leg that stops is planned again once from where the bot is. Returns whether it arrived.
	 */
	private boolean walkRoute(String step, BlockPos goal, BoundingBox bounds) {
		for (int plan = 1; plan <= 2; plan++) {
			Vec3 start = here();
			BlockPos from = BlockPos.containing(start.x, start.y + 0.5, start.z);
			List<BlockPos> path = onServer(p -> route(p.level(), from, goal, bounds, 60000));

			if (path == null) {
				note(step, Result.INFO, "no walkable way from " + from.toShortString() + " to " + goal.toShortString() + " inside " + box(bounds) + ": "
						+ onServer(p -> noRoute(p.level(), from, goal, bounds, 60000)));
				return false;
			}

			if (walkCells(step, from, path, goal.toShortString() + " (plan " + plan + ")")) {
				return true;
			}
		}

		return false;
	}

	/** Walks a planned way corner to corner. Returns whether every leg arrived (the leg that stopped is logged). */
	private boolean walkCells(String step, BlockPos from, List<BlockPos> path, String what) {
		List<BlockPos> corners = new ArrayList<>();
		// Whether each leg climbs anywhere: only those may jump (a jump against a wall would leave the way).
		List<Boolean> climbs = new ArrayList<>();
		BlockPos previous = from;
		int dx = 0;
		int dz = 0;
		boolean up = false;

		for (int i = 0; i < path.size(); i++) {
			BlockPos cell = path.get(i);
			int sx = Integer.signum(cell.getX() - previous.getX());
			int sz = Integer.signum(cell.getZ() - previous.getZ());

			if (i > 0 && (sx != dx || sz != dz)) {
				corners.add(previous);
				climbs.add(up);
				up = false;
			}

			up |= cell.getY() > previous.getY();
			dx = sx;
			dz = sz;
			previous = cell;
		}

		corners.add(previous);
		climbs.add(up);
		BlockPos last = from;

		for (int k = 0; k < corners.size(); k++) {
			BlockPos corner = corners.get(k);
			int cells = Math.abs(corner.getX() - last.getX()) + Math.abs(corner.getZ() - last.getZ());

			if (!walkTo(Vec3.atBottomCenterOf(corner), 0.35, 60 + 15 * cells, false, null, climbs.get(k))) {
				note(step, Result.INFO, "way to " + what + " (" + path.size() + " cells): the leg to " + corner.toShortString() + " stopped at "
						+ BlockPos.containing(here()).toShortString() + ": " + walkStop);
				return false;
			}

			last = corner;
		}

		note(step, Result.INFO, "walked a way of " + path.size() + " cells (" + corners.size() + " legs) to " + what);
		return true;
	}

	/**
	 * Inside a site piece (an outpost's walls), walks the shortest way out of its box first, so the
	 * straight walk to a far target does not start against a wall. Returns whether the bot is out.
	 */
	private boolean leaveSite(String step) {
		BlockPos from = BlockPos.containing(here().add(0, 0.5, 0));
		BoundingBox box = onServer(p -> {
			SitePiece piece = StructureLocator.pieceAt(p.level(), from);
			return piece == null ? null : piece.box();
		});

		if (box == null || !box.isInside(from)) {
			return true;
		}

		BoundingBox bounds = box.inflatedBy(4);
		List<BlockPos> path = onServer(p -> {
			Map<BlockPos, BlockPos> parent = new java.util.HashMap<>();
			BlockPos end = search(p.level(), from, at -> !box.inflatedBy(1).isInside(at), bounds, 20000, parent);
			return end == null ? null : pathTo(from, end, parent);
		});

		if (path == null) {
			note(step, Result.INFO, "no walkable way out of the site at " + box(box));
			return false;
		}

		return walkCells(step, from, path, "the way out of the site at " + box(box));
	}

	/**
	 * Standing cells from {@code from} to {@code to} (feet cells, {@code from} left out) inside
	 * {@code bounds}, by the moves of {@link #walkableSteps}; null when there is no way within
	 * {@code limit} cells. Server thread only.
	 */
	private static @Nullable List<BlockPos> route(ServerLevel level, BlockPos from, BlockPos to, BoundingBox bounds, int limit) {
		Map<BlockPos, BlockPos> parent = new java.util.HashMap<>();
		BlockPos end = search(level, from, at -> at.getX() == to.getX() && at.getZ() == to.getZ() && Math.abs(at.getY() - to.getY()) <= 1, bounds, limit, parent);
		return end == null ? null : pathTo(from, end, parent);
	}

	/** The cells from {@code from} (left out) to {@code end} along the search's {@code parent} links. */
	private static List<BlockPos> pathTo(BlockPos from, BlockPos end, Map<BlockPos, BlockPos> parent) {
		List<BlockPos> path = new ArrayList<>();

		for (BlockPos at = end; !at.equals(from); at = parent.get(at)) {
			path.add(at);
		}

		Collections.reverse(path);
		return path;
	}

	/**
	 * Why {@link #route} found no way, for the log: how many cells the search reached, the reached
	 * cell nearest the goal and what stands around it (block, and "-" for no collision), from one
	 * above its feet down to three below, in each direction. Server thread only.
	 */
	private static String noRoute(ServerLevel level, BlockPos from, BlockPos to, BoundingBox bounds, int limit) {
		Map<BlockPos, BlockPos> parent = new java.util.HashMap<>();
		search(level, from, at -> at.getX() == to.getX() && at.getZ() == to.getZ() && Math.abs(at.getY() - to.getY()) <= 1, bounds, limit, parent);
		BlockPos near = parent.keySet().stream().min(Comparator.comparingDouble(at -> at.distSqr(to))).orElse(from);
		StringBuilder out = new StringBuilder(parent.size() + " cell(s) reached; nearest the goal " + near.toShortString() + " (feet " + cell(level, near)
				+ ", below " + cell(level, near.below()) + ")");
		String[] names = { "east", "west", "south", "north" };
		int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

		for (int i = 0; i < dirs.length; i++) {
			out.append("; ").append(names[i]).append(':');

			for (int dy = 2; dy >= -3; dy--) {
				out.append(' ').append(dy).append('=').append(cell(level, near.offset(dirs[i][0], dy, dirs[i][1])));
			}
		}

		return out.toString();
	}

	private static String cell(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + (passable(level, pos) ? "-" : "");
	}

	/** The breadth-first search of {@link #route}: fills {@code parent} and returns the goal cell reached, or null. */
	private static @Nullable BlockPos search(ServerLevel level, BlockPos from, Predicate<BlockPos> goal, BoundingBox bounds, int limit,
			Map<BlockPos, BlockPos> parent) {
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		queue.add(from);
		parent.put(from, from);

		while (!queue.isEmpty() && parent.size() < limit) {
			BlockPos cur = queue.poll();

			if (goal.test(cur)) {
				return cur;
			}

			for (int[] dir : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
				for (int dy = 1; dy >= -3; dy--) {
					BlockPos next = cur.offset(dir[0], dy, dir[1]);

					if (!bounds.isInside(next) || parent.containsKey(next)) {
						continue;
					}

					boolean headroom = dy <= 0 || passable(level, cur.above(2));

					if (headroom && passable(level, next) && passable(level, next.above()) && !passable(level, next.below())) {
						parent.put(next, cur);
						queue.add(next);
						break;
					}

					if (!passable(level, next)) {
						break;
					}
				}
			}
		}

		return null;
	}

	/** The box around every piece, grown by {@code margin}. */
	private static BoundingBox around(List<SitePiece> pieces, int margin) {
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;

		for (SitePiece piece : pieces) {
			BoundingBox b = piece.box();
			minX = Math.min(minX, b.minX());
			minY = Math.min(minY, b.minY());
			minZ = Math.min(minZ, b.minZ());
			maxX = Math.max(maxX, b.maxX());
			maxY = Math.max(maxY, b.maxY());
			maxZ = Math.max(maxZ, b.maxZ());
		}

		if (pieces.isEmpty()) {
			return new BoundingBox(0, 0, 0, 0, 0, 0);
		}

		return new BoundingBox(minX - margin, minY - margin, minZ - margin, maxX + margin, maxY + margin, maxZ + margin);
	}

	/** The piece of {@code pieces} whose template path is {@code path} (e.g. {@code jedi_ruin/listening_hall}), or null. */
	private static @Nullable SitePiece pieceOf(List<SitePiece> pieces, String path) {
		for (SitePiece piece : pieces) {
			if (piece.template().getPath().equals(path)) {
				return piece;
			}
		}

		return null;
	}

	/** The veil of the veiled stone at {@code pos} (VEILED, REVEALED, OPEN), or NONE when it is not one. */
	private String veil(BlockPos pos) {
		return onServer(p -> veilAt(p, pos));
	}

	private static String veilAt(ServerPlayer p, BlockPos pos) {
		BlockState state = p.level().getBlockState(pos);
		return state.is(PuzzleBlocks.VEILED_STONE) ? state.getValue(PuzzleStates.VEIL).name() : "NONE";
	}

	private static boolean stepOut(ServerPlayer p, BlockPos pos) {
		BlockState state = p.level().getBlockState(pos);
		return state.is(PuzzleBlocks.STEP_STONE) && state.getValue(PuzzleStates.EXTENDED);
	}

	private static boolean sunkAt(ServerPlayer p, BlockPos pos) {
		BlockState state = p.level().getBlockState(pos);
		return state.is(PuzzleBlocks.COUNTERWEIGHT_STONE) && state.getValue(PuzzleStates.SUNK);
	}

	private boolean cairnRevealed(BlockPos pos) {
		return onServer(p -> {
			BlockState state = p.level().getBlockState(pos);
			return state.getBlock() instanceof EchoCairnBlock && EchoCairnBlock.revealed(state);
		});
	}

	/** A standing cell beside {@code pos} (same height or one lower), or null. Server thread only. */
	private static @Nullable BlockPos standBeside(ServerLevel level, BlockPos pos) {
		for (int dy = 0; dy >= -1; dy--) {
			for (int[] d : new int[][] { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } }) {
				BlockPos at = pos.offset(d[0], dy, d[1]);

				if (passable(level, at) && passable(level, at.above()) && !passable(level, at.below())) {
					return at;
				}
			}
		}

		return null;
	}

	/**
	 * Right-clicks the block at {@code pos} with the journal in hand, aiming at {@code aim}; grass or
	 * flowers that catch the crosshair are knocked away first. Returns whether the crosshair was on it.
	 */
	private boolean useBlock(BlockPos pos, Vec3 aim) {
		select(QuestItems.WANDERERS_JOURNAL);

		for (int i = 0; i < 4; i++) {
			aimAt(aim);
			context.waitTick();
			BlockPos hit = crosshairBlock();

			if (hit == null || hit.equals(pos) || !onServer(p -> p.level().getBlockState(hit).canBeReplaced())) {
				break;
			}

			context.getInput().pressKey(ATTACK);
			context.waitTicks(4);
		}

		boolean on = pos.equals(crosshairBlock());
		context.getInput().pressKey(USE);
		return on;
	}

	/**
	 * Uses a holocron pedestal (right-click with the journal in hand), then listens to the holocron
	 * that rises: Continue until the choice, then Own. Returns whether a new holocron was attuned.
	 */
	private boolean attune(String step, BlockPos pedestal, String shotName) {
		int before = onServer(p -> HolocronService.attunedCount(p, null));
		Vec3 feet = Vec3.atBottomCenterOf(pedestal);

		if (flat(feet, here()) > 2.5) {
			walkTo(feet, 1.8, 120, false, null);
		}

		boolean open = false;

		for (int attempt = 1; attempt <= 3 && !open; attempt++) {
			int mark = MESSAGES.size();
			Vec3 stood = here();
			boolean on = useBlock(pedestal, Vec3.atCenterOf(pedestal));

			if (!on) {
				// The journal opens on a miss: close it again.
				closeScreen();
			}

			open = waitServer(100, p -> {
				String at = sessionAt(p);
				return at != null && at.startsWith("holocron_");
			});

			if (!open) {
				List<String> said;

				synchronized (MESSAGES) {
					said = new ArrayList<>(MESSAGES.subList(Math.min(mark, MESSAGES.size()), MESSAGES.size()));
				}

				note(step, Result.INFO, "pedestal at " + pedestal.toShortString() + " attempt " + attempt + ": no holocron dialogue (crosshair on it=" + on + ", "
						+ lookingAt() + "); messages " + said);
				fightSentinels(step, pedestal, 16);
				fightHostiles(step, pedestal);

				if (here().distanceTo(stood) > 1.5 && !walkTo(stood, 0.4, 300, false, null)) {
					// The fight led off the gallery or down a drop: back to where the pedestal was in reach.
					shortcut("teleported back to the holocron pedestal after a fight (" + step + ")");
					teleport(stood, yawToward(stood, Vec3.atCenterOf(pedestal)));
				}
			}
		}

		if (!open) {
			return false;
		}

		context.waitTicks(10);
		shot(shotName);

		for (int i = 0; i < 8 && sessionAt() != null; i++) {
			if (dialogueRow(HOLOCRON_OWN) > 0) {
				reply(step, HOLOCRON_OWN);
				break;
			}

			if (!reply(step, HOLOCRON_CONTINUE)) {
				break;
			}
		}

		if (sessionAt() != null) {
			closeScreen();
		}

		boolean attuned = waitServer(40, p -> HolocronService.attunedCount(p, null) > before);
		note(step, attuned ? Result.INFO : Result.FAIL, "holocron at " + pedestal.toShortString() + ": attuned=" + attuned + ", holocrons "
				+ onServer(p -> JournalEntries.HOLOCRON_IDS.stream().filter(id -> HolocronService.hasAttuned(p, id)).toList()));
		return attuned;
	}

	/** Fights the awake ruin sentinels within {@code radius} of {@code centre}; any left are killed (logged). Returns how many fell to the saber. */
	private int fightSentinels(String step, BlockPos centre, int radius) {
		List<UUID> awake = onServer(p -> p.level().getEntities((Entity) null, new AABB(centre).inflate(radius),
				e -> e instanceof RuinSentinel sentinel && sentinel.isAlive() && !sentinel.isDormant()).stream().map(Entity::getUUID).toList());

		if (awake.isEmpty()) {
			return 0;
		}

		igniteSaber();
		int kills = 0;

		for (UUID id : awake) {
			if (fight(id, 900, false, step)) {
				kills++;
			}
		}

		List<UUID> left = awake.stream().filter(id -> target(id).alive()).toList();

		if (!left.isEmpty()) {
			shortcut("killed " + left.size() + " ruin sentinel(s) the bot could not finish (" + step + ")");
			onServer(p -> {
				for (UUID id : left) {
					if (p.level().getEntity(id) instanceof LivingEntity living && living.isAlive()) {
						living.kill(p.level());
					}
				}

				return null;
			});
		}

		note(step, Result.INFO, awake.size() + " awake sentinel(s): " + kills + " cut down with the saber");
		return kills;
	}

	/**
	 * Fights what keeps a holocron pedestal from answering ("Something still stirs here"): the
	 * pedestal's own rule, an awake creature within {@code holocron.pedestalClearRange} of its top that
	 * counts as hostile to the player or was provoked by them. What the bot cannot finish is killed
	 * (logged). Returns how many fell to the saber.
	 */
	private int fightHostiles(String step, BlockPos pedestal) {
		int range = HolocronBalance.get().pedestalClearRange;
		Vec3 top = Vec3.atCenterOf(pedestal.above());
		List<UUID> hostile = onServer(p -> p.level().getEntitiesOfClass(LivingEntity.class, new AABB(pedestal).inflate(range),
				e -> e != p && e.isAlive() && !(e instanceof Mob mob && mob.isNoAi()) && e.distanceToSqr(top) <= (double) range * range
						&& (Attitudes.countsAsHostile(p, e) || Attitudes.provokedBy(e, p))).stream().map(Entity::getUUID).toList());

		if (hostile.isEmpty()) {
			return 0;
		}

		String what = onServer(p -> hostile.stream().map(id -> p.level().getEntity(id)).filter(Objects::nonNull)
				.map(e -> BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath() + " at " + e.blockPosition().toShortString()).toList().toString());
		igniteSaber();
		int kills = 0;

		for (UUID id : hostile) {
			if (fight(id, 600, false, step)) {
				kills++;
			}
		}

		List<UUID> left = hostile.stream().filter(id -> target(id).alive()).toList();

		if (!left.isEmpty()) {
			shortcut("killed " + left.size() + " hostile creature(s) near the pedestal the bot could not finish (" + step + ")");
			onServer(p -> {
				for (UUID id : left) {
					if (p.level().getEntity(id) instanceof LivingEntity living && living.isAlive()) {
						living.kill(p.level());
					}
				}

				return null;
			});
		}

		note(step, Result.INFO, "hostile near the pedestal " + what + ": " + kills + " cut down with the saber");
		return kills;
	}

	/** Fights the live crystal poachers within {@code radius} (diggers, an ambush), nearest first. Returns how many fell. */
	private int fightPoachers(String step, double radius) {
		List<UUID> poachers = poachersNear(radius);

		if (poachers.isEmpty()) {
			return 0;
		}

		igniteSaber();
		int kills = 0;

		for (UUID id : poachers) {
			if (fight(id, 900, false, step)) {
				kills++;
			}
		}

		note(step, Result.INFO, poachers.size() + " poacher(s) within " + Math.round(radius) + " blocks: " + kills + " cut down");
		return kills;
	}

	/** A Republic officer within 48 blocks of the terminal, or null. Server thread only. */
	private static @Nullable Entity officerOf(ServerPlayer p, BlockPos terminal) {
		Vec3 at = Vec3.atCenterOf(terminal);
		return p.level().getEntities((Entity) null, new AABB(terminal).inflate(48), e -> e.isAlive() && Troopers.isTrooper(e)
				&& Troopers.role(e) == TrooperRole.OFFICER && Troopers.faction(e) == Faction.REPUBLIC).stream()
				.min(Comparator.comparingDouble(e -> e.position().distanceToSqr(at))).orElse(null);
	}

	private @Nullable UUID officerNear(BlockPos terminal) {
		return onServer(p -> {
			Entity officer = officerOf(p, terminal);
			return officer == null ? null : officer.getUUID();
		});
	}

	private @Nullable String activeMission() {
		return onServer(p -> Missions.activeId(p));
	}

	private MissionData.@Nullable Stage missionStage() {
		return onServer(p -> {
			MissionData.Active active = MissionService.get(p).active;
			return active == null ? null : active.stage;
		});
	}

	/** This player's live entities of mission {@code missionId} with {@code role} (enemy, chief, downed) within 96 blocks, nearest first. */
	private List<UUID> missionEntities(String missionId, String role) {
		return onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(96), e -> e.isAlive()
				&& e.entityTags().contains("oldrepublic.mission." + missionId) && e.entityTags().contains("oldrepublic.mission_role." + role)
				&& StoryTags.isOwnedBy(e, p.getUUID())).stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::getUUID).toList());
	}

	/**
	 * Takes a story mission from the outpost officer: "Is there work?", the mission, accept. Falls
	 * back to /oldrepublic mission start (logged). Returns whether the mission is active.
	 */
	private boolean takeMission(String step, String missionId, String option, String accept) {
		String active = activeMission();

		if (missionId.equals(active)) {
			return true;
		}

		if (active != null) {
			shortcut("abandoned the mission " + active + " with /oldrepublic mission abandon to take " + missionId + " (" + step + ")");
			server.runCommand("oldrepublic mission abandon @p");
			context.waitTicks(3);
		}

		BlockPos terminal = outpost;
		int replies = 0;

		if (terminal != null) {
			travel(step, Vec3.atBottomCenterOf(terminal), 10, "the Republic outpost");
			waitServer(600, p -> officerOf(p, terminal) != null);
			UUID officer = officerNear(terminal);

			if (officer != null) {
				replies = talkAndChoose(step, officer, missionId + "_briefing", "dialogue.oldrepublic.officer_republic.opt.work", option, accept);
			}
		}

		boolean taken = waitServer(40, p -> missionId.equals(Missions.activeId(p)));
		note(step, taken ? Result.INFO : Result.FAIL, "officer at " + shortPos(terminal) + ": replies made " + replies + "/3, active mission " + activeMission());

		if (!taken) {
			shortcut("started " + missionId + " with /oldrepublic mission start (" + step + ")");
			server.runCommand("oldrepublic mission start @p " + missionId);
			taken = waitServer(40, p -> missionId.equals(Missions.activeId(p)));
		}

		return taken;
	}

	/** Waits for the mission's site, travels there and waits for its people to appear (the stage moves past TRAVEL). */
	private boolean reachMissionSite(String step, String what) {
		boolean placed = waitServer(600, p -> Missions.target(p) != null);
		Vec3 site = onServer(p -> Missions.target(p));

		if (!placed || site == null) {
			note(step, Result.FAIL, "no site for " + what + " after 600 ticks: stage " + missionStage());
			return false;
		}

		note(step, Result.INFO, what + " at " + BlockPos.containing(site).toShortString() + ", " + Math.round(flat(site, here())) + " blocks away");
		select(QuestItems.WANDERERS_JOURNAL);
		travel(step, site, 16, what);
		boolean active = waitServer(200, p -> MissionService.get(p).active == null || (MissionService.get(p).active.stage != MissionData.Stage.TRAVEL
				&& MissionService.get(p).active.stage != MissionData.Stage.PLACING));

		if (!active) {
			walkTo(site, 6, 400, false, () -> missionStage() != MissionData.Stage.TRAVEL);
			active = waitServer(100, p -> MissionService.get(p).active == null || MissionService.get(p).active.stage != MissionData.Stage.TRAVEL);
		}

		Vec3 at = here();
		aimAt(site.add(0, at.y - site.y + 1.0, 0));
		context.waitTicks(20);
		shot("mission_site");
		return active;
	}

	/** Fights this mission's live entities of {@code role} with the saber, nearest first; any left are killed (logged). Returns how many fell to the saber. */
	private int fightMissionRole(String step, String missionId, String role) {
		int kills = 0;
		Set<UUID> failed = new HashSet<>();

		for (int fights = 0; fights < 12; fights++) {
			List<UUID> left = missionEntities(missionId, role).stream().filter(x -> !failed.contains(x)).toList();

			if (left.isEmpty()) {
				break;
			}

			UUID id = left.get(0);

			if (fight(id, 900, false, step)) {
				kills++;
			} else {
				failed.add(id);
			}
		}

		List<UUID> rest = missionEntities(missionId, role);

		if (!rest.isEmpty()) {
			shortcut("killed " + rest.size() + " mission " + role + "(s) the bot could not finish (" + step + ")");
			onServer(p -> {
				for (UUID id : rest) {
					if (p.level().getEntity(id) instanceof LivingEntity living && living.isAlive()) {
						living.kill(p.level());
					}
				}

				return null;
			});
		}

		return kills;
	}

	/** Kneels at a downed trooper: Sneak held, right-click on it, then stays crouched beside it until it stands (W4 tending). */
	private boolean tendDowned(String step, UUID id) {
		Target t = target(id);

		if (!t.alive()) {
			return false;
		}

		select(QuestItems.WANDERERS_JOURNAL);
		walkTo(t.pos(), 1.5, 300, false, null);
		Vec3 body = t.pos().add(0, t.height() * 0.5, 0);

		if (!sightTo(body)) {
			// A fence or bars between: kneel on the side of the trooper that can see it.
			Vec3 spot = sightSpot(body, 1, 2, TEND_REACH, t.pos().y);

			if (spot != null && !walkTo(spot, 0.4, 100, false, null)) {
				shortcut("stepped beside a downed trooper the walk could not reach (" + step + ")");
				teleport(spot, yawToward(spot, body));
			}
		}

		int netId = t.id();
		boolean stood = false;
		context.getInput().holdKey(SNEAK);

		try {
			for (int attempt = 1; attempt <= 4 && !stood; attempt++) {
				Target now = target(id);

				if (!now.alive()) {
					break;
				}

				context.waitTick();
				// Alternate the middle and the upper part of the body: a low wall can hide the middle of a trooper lying behind it.
				double up = attempt % 2 == 0 ? 0.35 : 0.0;
				Vec3 aim = context.computeOnClient(mc -> {
					Entity e = mc.level == null ? null : mc.level.getEntity(netId);
					return e == null ? null : e.getBoundingBox().getCenter().add(0, e.getBbHeight() * up, 0);
				});
				aimAt(aim != null ? aim : now.pos().add(0, now.height() * 0.5, 0));
				context.waitTick();

				if (me().picked() != now.id()) {
					note(step, Result.INFO, "tend attempt " + attempt + ": the crosshair is not on the downed trooper (" + lookingAt() + ")");
					// Crouched, the eye is lower: a panel the standing view cleared can hide a trooper lying behind it. From the second
					// miss, kneel where the crouched eye sees it.
					Vec3 nowBody = now.pos().add(0, now.height() * 0.5, 0);
					Vec3 spot = attempt >= 2 ? sightSpot(nowBody, 1, 3, TEND_REACH, now.pos().y) : null;

					if (spot == null) {
						walkTo(now.pos(), 1.0, 40, false, null);
					} else if (!walkTo(spot, 0.4, 100, false, null)) {
						shortcut("stepped beside a downed trooper the walk could not reach (" + step + ")");
						teleport(spot, yawToward(spot, nowBody));
					}

					continue;
				}

				context.getInput().pressKey(USE);
				stood = waitServer(120, p -> {
					Entity e = p.level().getEntity(id);
					return e == null || !Troopers.isDowned(e);
				});
			}
		} finally {
			context.getInput().releaseKey(SNEAK);
		}

		note(step, stood ? Result.INFO : Result.FAIL, "downed trooper " + (stood ? "stood up after tending" : "still down after 4 tries"));
		return stood;
	}

	private BossView bossView() {
		return onServer(p -> {
			ArenaSession s = BossArenas.sessionOwnedBy(p);
			OrrenVasch v = s == null ? null : s.vasch();
			CollectorCombatGoal c = v == null ? null : v.combat();
			boolean talking = Dialogues.session(p) != null;

			if (s == null || v == null) {
				return new BossView(s == null ? ArenaSession.Stage.ENDED : s.stage(), s == null ? 0 : s.phase(), false, null, -1, Vec3.ZERO, 0, 0f, 0f,
						CollectorCombatGoal.Move.NONE, false, s != null && s.isUnsung(), s == null ? 0 : s.singingCount(), Holds.isHeld(p), talking);
			}

			return new BossView(s.stage(), s.phase(), v.isAlive(), v.getUUID(), v.getId(), v.position(), v.getBbHeight(), v.getHealth(), v.getMaxHealth(),
					c == null ? CollectorCombatGoal.Move.NONE : c.move(), c != null && c.inTell(CollectorCombatGoal.Move.HURL), s.isUnsung(), s.singingCount(),
					Holds.isHeld(p), talking);
		});
	}

	private @Nullable ArenaLayout arena() {
		ArenaLayout layout = onServer(p -> {
			ArenaSession s = BossArenas.sessionOwnedBy(p);
			return s == null ? null : s.layout();
		});

		if (layout != null) {
			lastArena = layout;
		}

		return layout;
	}

	private List<Pylon> pylons() {
		return onServer(p -> {
			ArenaSession s = BossArenas.sessionOwnedBy(p);
			List<Pylon> out = new ArrayList<>();

			if (s == null) {
				return out;
			}

			for (int i = 0; i < s.layout().pylons().size(); i++) {
				PylonFocus focus = s.pylonFocus(i);
				Vec3 crystal = focus != null ? focus.crystalCentre() : Vec3.atBottomCenterOf(s.layout().pylons().get(i)).add(0, PylonFocus.CRYSTAL_HEIGHT, 0);
				out.add(new Pylon(i, focus == null ? null : focus.getUUID(), focus == null ? -1 : focus.getId(), crystal, s.pylonCrystal(i)));
			}

			return out;
		});
	}

	private PylonCrystal pylonState(int index) {
		return onServer(p -> {
			ArenaSession s = BossArenas.sessionOwnedBy(p);
			return s == null || index >= s.layout().pylons().size() ? PylonCrystal.EMPTY : s.pylonCrystal(index);
		});
	}

	/** The nearest awake phase 2 Watcher of the player's Collector fight within {@code range} blocks, or null. */
	private @Nullable UUID watcherNear(double range) {
		return onServer(p -> {
			ArenaSession s = BossArenas.sessionOwnedBy(p);
			return s == null ? null : s.sentinels().stream().filter(w -> w.isAlive() && !w.isDormant() && w.distanceTo(p) <= range)
					.min(Comparator.comparingDouble(w -> w.distanceToSqr(p))).map(Entity::getUUID).orElse(null);
		});
	}

	/**
	 * A standing cell 3 to 10 blocks around a pylon crystal, within 3 blocks of the bot's level, whose
	 * eye sees the crystal (its centre or its tip, by the server's rule) within {@link #PULL_REACH};
	 * null when there is none. Cells in {@code avoid} are left out. The pick is the nearest, counting a
	 * cell more than a block above or below the bot (a climb, not a walk) as 20 blocks further and one
	 * that sees only the tip (a grazing line over an edge) as 10 blocks further.
	 */
	private @Nullable Vec3 pullSpot(Vec3 crystal, Set<BlockPos> avoid) {
		Vec3 me = here();
		int level = Mth.floor(me.y + 0.01);
		return onServer(p -> {
			ServerLevel world = p.level();
			Vec3 best = null;
			double bestScore = Double.MAX_VALUE;

			for (int r = 3; r <= 10; r++) {
				for (int i = 0; i < 16; i++) {
					double angle = i * Math.PI / 8;
					int x = Mth.floor(crystal.x + Math.cos(angle) * r);
					int z = Mth.floor(crystal.z + Math.sin(angle) * r);

					for (int dy = -3; dy <= 3; dy++) {
						BlockPos feet = new BlockPos(x, level + dy, z);

						if (avoid.contains(feet) || !passable(world, feet) || !passable(world, feet.above()) || passable(world, feet.below())) {
							continue;
						}

						Vec3 stand = Vec3.atBottomCenterOf(feet);
						Vec3 eye = stand.add(0, p.getEyeHeight(), 0);

						if (eye.distanceTo(crystal) > PULL_REACH) {
							continue;
						}

						boolean centre = reaches(p, eye, crystal);

						if (!centre && !reaches(p, eye, crystal.add(0, CRYSTAL_TIP, 0))) {
							continue;
						}

						double score = stand.distanceToSqr(me) + (Math.abs(dy) > 1 ? 400 : 0) + (centre ? 0 : 100);

						if (score < bestScore) {
							bestScore = score;
							best = stand;
						}
					}
				}
			}

			return best;
		});
	}

	/**
	 * Pulls at a pylon crystal the way a player lines the shot up: stops, lets the bot come to rest on
	 * the ground, aims from where its eye is then and checks on the client that the look ray runs into
	 * the crystal before any block (what the server's Pull looks for when the key goes down), then taps
	 * the Pull key. The server takes the aim at the press, so a jump or a knock between aiming and
	 * pressing turns a fine line over a wing's or a gallery's edge onto the stone ("Nothing within reach
	 * to pull", no cost, no cooldown). Returns how it went; "landed" first when the Pull's cooldown began.
	 */
	private String pullCrystal(String step, Pylon pylon, int slot) {
		setForward(false, false);
		boolean still = false;

		for (int t = 0; t < 12 && !still; t++) {
			still = context.computeOnClient(mc -> mc.player.onGround() && mc.player.getDeltaMovement().horizontalDistanceSqr() < 0.0025);

			if (!still) {
				tickMe(step);
			}
		}

		boolean onIt = false;

		for (int i = 0; i < 3 && !onIt; i++) {
			Vec3 point = pullPoint(pylon.crystal());

			if (point == null) {
				return "not pressed: the crystal went out of sight once the bot stood still";
			}

			aimAt(point);
			context.waitTick();
			onIt = lookHitsFocus(pylon.focusId());
		}

		if (!onIt) {
			return "not pressed: the look ray does not reach the crystal before a block" + (still ? "" : " (the bot did not come to rest)");
		}


		try {
			holdSlot(slot);
			context.waitTicks(2);
		} finally {
			releaseSlot(slot);
		}

		// The reach lasts windupTicks (3) after the press before the Pull lands and its cooldown begins.
		boolean landed = waitServer(10, p -> Progression.get(p).cooldown("force_pull") > 0);
		return landed ? "landed" : "pressed, found nothing on the aim";
	}

	/**
	 * Whether the bot's look ray, from its eye on the client, runs into pylon focus {@code focusId}'s
	 * box (grown by 0.1, as {@code ForceReactiveEntity#findOnAim} has it) before the first block.
	 */
	private boolean lookHitsFocus(int focusId) {
		return context.computeOnClient(mc -> {
			Entity focus = focusId < 0 || mc.level == null ? null : mc.level.getEntity(focusId);

			if (focus == null) {
				return false;
			}

			Vec3 eye = mc.player.getEyePosition();
			Vec3 end = eye.add(mc.player.getLookAngle().scale(12.0));
			BlockHitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
			Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
			return focus.getBoundingBox().inflate(0.1).clip(eye, stop).isPresent();
		});
	}

	/**
	 * Where to aim a Pull at a pylon crystal from the bot's eye: its centre, else its tip (from below
	 * the pylon's edge can hide the centre), by the server's rule; null when neither is in sight.
	 */
	private @Nullable Vec3 pullPoint(Vec3 centre) {
		Vec3 eye = me().eye();
		Vec3 tip = centre.add(0, CRYSTAL_TIP, 0);
		return onServer(p -> reaches(p, eye, centre) ? centre : reaches(p, eye, tip) ? tip : null);
	}

	/** {@code PylonFocus#inReach}'s line rule: no block between, a hit at the point itself aside. */
	private static boolean reaches(ServerPlayer p, Vec3 eye, Vec3 point) {
		BlockHitResult hit = p.level().clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(eye) >= eye.distanceToSqr(point) - 0.25;
	}

	/**
	 * A standing spot {@code minRadius} to {@code maxRadius} blocks around {@code target}, within 3
	 * blocks of {@code height}, whose eye sees {@code target} past every block and is within
	 * {@code reach} of it; the one nearest the bot, or null when there is none.
	 */
	private @Nullable Vec3 sightSpot(Vec3 target, int minRadius, int maxRadius, double reach, double height) {
		return sightSpot(target, minRadius, maxRadius, reach, height, false);
	}

	/** As above; with {@code crystal}, "in sight" is a pylon crystal's (its centre or its tip, by the server's rule). */
	private @Nullable Vec3 sightSpot(Vec3 target, int minRadius, int maxRadius, double reach, double height, boolean crystal) {
		Vec3 me = here();
		Vec3 aim = target;
		return onServer(p -> {
			ServerLevel level = p.level();
			Vec3 best = null;
			double bestDistance = Double.MAX_VALUE;

			for (int r = minRadius; r <= maxRadius; r++) {
				for (int i = 0; i < 16; i++) {
					double angle = i * Math.PI / 8;
					int x = Mth.floor(aim.x + Math.cos(angle) * r);
					int z = Mth.floor(aim.z + Math.sin(angle) * r);

					for (int dy = -3; dy <= 3; dy++) {
						BlockPos feet = new BlockPos(x, Mth.floor(height) + dy, z);

						if (!passable(level, feet) || !passable(level, feet.above()) || passable(level, feet.below())) {
							continue;
						}

						Vec3 stand = Vec3.atBottomCenterOf(feet);
						Vec3 eye = stand.add(0, p.getEyeHeight(), 0);

						if (eye.distanceTo(aim) > reach) {
							continue;
						}

						if (crystal) {
							if (!reaches(p, eye, aim) && !reaches(p, eye, aim.add(0, CRYSTAL_TIP, 0))) {
								continue;
							}
						} else {
							BlockHitResult hit = level.clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));

							if (hit.getType() != HitResult.Type.MISS && Vec3.atCenterOf(hit.getBlockPos()).distanceTo(aim) > 1.0) {
								continue;
							}
						}

						double distance = stand.distanceToSqr(me);

						if (distance < bestDistance) {
							bestDistance = distance;
							best = stand;
						}
					}
				}
			}

			return best;
		});
	}

	private @Nullable Pylon nearestSinging() {
		Vec3 me = here();
		return pylons().stream().filter(x -> x.state() == PylonCrystal.SINGING).min(Comparator.comparingDouble(x -> x.crystal().distanceToSqr(me))).orElse(null);
	}

	/**
	 * One tick of the Collector fight up close: closes in and, when the attack is charged and the
	 * crosshair is on him, swings a heavy attack (Sneak held, then Attack). Returns 1 for a swing.
	 */
	private int meleeTick(String step, BossView b) {
		Me me = me();

		if (me.dead()) {
			died(step);
		}

		aimAt(b.pos().add(0, b.height() * 0.55, 0));
		double distance = flat(me.pos(), b.pos());
		setForward(distance > 2.2, distance > 3.5);

		if (me.picked() == b.id() && me.strength() >= 0.95f) {
			setForward(false, false);
			context.getInput().holdKey(SNEAK);

			try {
				// The heavy attack reads Sneak on the client when Attack is pressed: let it take hold first.
				tickMe(step);
				context.getInput().pressKey(ATTACK);

				for (int i = 0; i < 5; i++) {
					BossView now = bossView();

					if (now.alive()) {
						aimAt(now.pos().add(0, now.height() * 0.55, 0));
					}

					tickMe(step);
				}
			} finally {
				context.getInput().releaseKey(SNEAK);
			}

			return 1;
		}

		if (me.collided() && me.onGround()) {
			jump(step);
		} else {
			tickMe(step);
		}

		return 0;
	}

	/** One tick of walking toward {@code goal} (horizontal), stopping {@code stopAt} short of it. */
	private void stepToward(String step, Vec3 goal, double stopAt) {
		Me me = me();

		if (me.dead()) {
			died(step);
		}

		double distance = flat(me.pos(), goal);
		aimFlat(me.eye(), goal);
		setForward(distance > stopAt, distance > 4);

		if (distance > stopAt && me.collided() && me.onGround()) {
			jump(step);
		} else {
			tickMe(step);
		}
	}

	/**
	 * Makes sure the Collector fight is on and has reached {@code phase}: back into the hall after a
	 * death (logged), a fight that never started started (logged), and a fight that reset resumed at
	 * the phase (logged). Returns false (noted) when the phase is already behind or there is no fight.
	 */
	private boolean bossReady(String step, int phase) {
		BossView b = bossView();
		boolean returned = false;

		if (b.stage() == ArenaSession.Stage.ENDED && b.vasch() == null && lastArena != null && !onServer(BossArenas::isResolved)) {
			// A death sends the owner out of the Reliquary, which drops the fight; the heart makes a new one when they are back.
			Vec3 floor = Vec3.atBottomCenterOf(lastArena.antechamberFloor());
			shortcut("teleported back into the Reliquary antechamber to fight the Collector again after the fight was dropped (" + step + ")");
			teleport(floor, yawToward(floor, lastArena.daisFeet()));
			boolean again = waitServer(200, p -> BossArenas.sessionOwnedBy(p) != null);
			b = bossView();
			returned = true;
			note(step, Result.INFO, "the fight was dropped (no Collector session for the player); back in the antechamber: new session=" + again + ", stage "
					+ b.stage() + ", phase " + b.phase());
		}

		if (b.past(phase)) {
			note(step, Result.INFO, "phase " + phase + " is already behind: stage " + b.stage() + ", phase " + b.phase());
			return false;
		}

		ArenaLayout layout = arena();

		if (layout == null) {
			note(step, Result.FAIL, "no Collector arena for the player: " + c3Line());
			return false;
		}

		if (!layout.isInHall(here())) {
			Vec3 floor = Vec3.atBottomCenterOf(layout.antechamberFloor());

			if (!returned) {
				shortcut("teleported back into the Reliquary antechamber (" + step + ")");
				teleport(floor, yawToward(floor, layout.daisFeet()));
			}

			walkTo(layout.daisFeet(), 5, 200, false, () -> bossView().stage() != ArenaSession.Stage.WAITING);
		}

		b = bossView();

		if (b.stage() == ArenaSession.Stage.WAITING || b.stage() == ArenaSession.Stage.MEETING) {
			closeScreen();
			shortcut("started the Collector fight with /oldrepublic boss start (" + step + ")");
			server.runCommand("oldrepublic boss start @p");
			waitServer(80, p -> BossArenas.sessionOwnedBy(p) != null && BossArenas.sessionOwnedBy(p).isFighting());
		}

		boolean reached = waitServer(200, p -> {
			ArenaSession s = BossArenas.sessionOwnedBy(p);
			return s == null || s.phase() > phase || (s.phase() == phase && s.stage() == ArenaSession.Stage.FIGHTING) || !s.isFighting();
		});
		b = bossView();

		if (b.isFightingBelow(phase)) {
			// The fight reset (a death) and began again below this phase.
			shortcut("resumed the Collector fight at phase " + phase + " with /oldrepublic boss phase after it began again (" + step + ")");
			server.runCommand("oldrepublic boss phase @p " + phase);
			waitServer(100, p -> BossArenas.sessionOwnedBy(p) != null && BossArenas.sessionOwnedBy(p).phase() >= phase);
			b = bossView();
		}

		if (b.past(phase)) {
			note(step, Result.INFO, "phase " + phase + " is already behind: stage " + b.stage() + ", phase " + b.phase());
			return false;
		}

		if (b.stage() != ArenaSession.Stage.FIGHTING || b.phase() != phase) {
			note(step, Result.FAIL, "phase " + phase + " did not begin (waited=" + reached + "): stage " + b.stage() + ", phase " + b.phase());
			return false;
		}

		igniteSaber();
		note(step, Result.INFO, String.format(Locale.ROOT, "phase %d begins: Collector health %.0f of %.0f, %d pylon(s) singing, player health %.1f", phase, b.health(),
				b.maxHealth(), b.singing(), me().health()));
		shot("collector_phase_" + phase);
		return true;
	}

	/**
	 * After a phase loop: PASS when the fight moved past {@code phase} with real inputs. On a timeout
	 * a FAIL, and /oldrepublic boss phase (logged) moves the fight on so the yield and the hoard
	 * still run.
	 */
	private void endPhase(String step, int phase, String details) {
		BossView b = bossView();
		boolean past = b.past(phase);
		check(step, past, (past ? "" : "phase " + phase + " not won in time (" + PHASE_TICKS + " ticks; phase 2 up to half again after progress): ") + details + ", now stage " + b.stage() + ", phase "
				+ b.phase());

		if (!past) {
			int next = phase + 1;
			shortcut("moved the Collector fight on to " + (next >= 4 ? "his yield" : "phase " + next) + " with /oldrepublic boss phase " + next + " (" + step
					+ " timed out)");
			server.runCommand("oldrepublic boss phase @p " + next);
			waitServer(100, p -> {
				ArenaSession s = BossArenas.sessionOwnedBy(p);
				return s == null || s.phase() > phase || !s.isFighting();
			});
		}
	}

	/**
	 * Leaps with Force Jump toward a gallery pylon (hold its slot key to gather, release) and taps
	 * Pull at its crystal on the way up once in reach and in sight. Returns whether Pull was cast.
	 */
	private boolean jumpAndPull(String step, Pylon pylon, int jumpSlot, int pullSlot) {
		Vec3 start = here();
		context.getInput().lookAt(yawToward(start, pylon.crystal()), 0f);

		try {
			holdSlot(jumpSlot);
			context.waitTicks(18);
		} finally {
			releaseSlot(jumpSlot);
		}

		boolean pulled = false;
		double top = start.y;

		for (int t = 0; t < 40; t++) {
			Me me = me();
			top = Math.max(top, me.pos().y);

			Vec3 point = !pulled && t >= 3 && me.eye().distanceTo(pylon.crystal()) <= 11.0 ? pullPoint(pylon.crystal()) : null;

			if (point != null && pullAt(point, pullSlot)) {
				pulled = true;
				continue;
			}

			if (t > 8 && me.onGround()) {
				break;
			}

			tickMe(step);
		}

		note(step, Result.INFO, String.format(Locale.ROOT, "Force Jump toward pylon P%d: rose %.1f blocks, pulled in the air=%s, crystal now %s", pylon.index() + 1,
				top - start.y, pulled, pylonState(pylon.index())));
		return pulled;
	}

	/**
	 * For a Surge: sprints to the spot behind the broken column nearest the bot (away from the
	 * Collector), so the column breaks his line; with no columns, out of the Surge's reach. Returns
	 * whether the line to him is broken there.
	 */
	private boolean takeCover(String step, BossView b, List<BlockPos> columns) {
		Vec3 me = here();
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockPos column : columns) {
			// Each column is 2x2 from its min corner cell: its axis runs through the corner shared by the four cells.
			Vec3 centre = new Vec3(column.getX() + 1.0, column.getY(), column.getZ() + 1.0);
			Vec3 spot = centre.add(away(b.pos(), centre).scale(2.2));
			double distance = flat(spot, me);

			if (distance < bestDistance) {
				best = spot;
				bestDistance = distance;
			}
		}

		Vec3 goal = best != null ? best : me.add(away(b.pos(), me).scale(9));
		walkTo(goal, 0.5, 30, true, null);
		boolean covered = !clearLine(me().eye(), b.pos().add(0, b.height() * 0.6, 0));
		note(step, Result.INFO, String.format(Locale.ROOT, "Surge: ran %.1f blocks to %s, line to him broken=%s", flat(goal, me),
				best != null ? "cover behind a column" : "away from him", covered));

		if (covered) {
			shot("collector_surge_cover");
		}

		return covered;
	}

	// ---------------------------------------------------------------------------------------------
	// Chapter Two helpers
	// ---------------------------------------------------------------------------------------------

	/**
	 * Walks up to an NPC, puts the crosshair on it and right-clicks with the journal in hand (a
	 * right-click with the lit saber would raise the guard). Returns whether a dialogue opened.
	 *
	 * <p>Mentors often stand on raised ground in tall grass. Like a player, the bot picks a spot
	 * about two blocks away with a clear view of their face (not the foot of a ledge, where the
	 * ledge's edge catches the crosshair), aims at eye and then chest height, re-aiming every try,
	 * and knocks away grass or flowers that catch the crosshair.
	 */
	private boolean talkTo(String step, UUID id) {
		if (sessionAt() != null || context.computeOnClient(mc -> mc.gui.screen() != null)) {
			closeScreen();
			context.waitTicks(3);
		}

		select(QuestItems.WANDERERS_JOURNAL);

		for (int attempt = 1; attempt <= 3; attempt++) {
			Target t = target(id);

			if (!t.alive()) {
				note(step, Result.FAIL, "the one to talk to is gone");
				return false;
			}

			Vec3 spot = talkSpot(id);

			if (spot != null) {
				walkTo(spot, 0.4, 300, false, null);
			} else {
				walkTo(t.pos(), 2.5, 300, false, null);
			}

			// Tents, fences and wagons can leave the walk short of the spot; the talk itself stays real.
			double away = context.computeOnClient(mc -> mc.player.position().distanceTo(t.pos()));

			if (spot != null && away > 3.5) {
				shortcut(String.format(Locale.ROOT, "%s: the walk stopped %.1f blocks from them; teleported to the talking spot", step, away));
				Vec3 face = t.pos().subtract(spot);
				teleport(spot, (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90f);
				context.waitTicks(5);
			}

			boolean onIt = false;
			List<String> cleared = new ArrayList<>();

			for (int i = 0; i < 8 && !onIt; i++) {
				Target now = target(id);

				if (!now.alive()) {
					break;
				}

				// Aim where the client draws them (its position can lag the server's), at the
				// middle of their box: kneeling captives and seated mentors are shorter than standing.
				int netId = now.id();
				double frac = i % 2 == 0 ? 0.5 : 0.75;
				Vec3 aim = context.computeOnClient(mc -> {
					Entity e = mc.level == null ? null : mc.level.getEntity(netId);
					return e == null ? null : e.getBoundingBox().getCenter().add(0, e.getBbHeight() * (frac - 0.5), 0);
				});
				aimAt(aim != null ? aim : now.pos().add(0, now.height() * frac, 0));
				context.waitTick();
				onIt = me().picked() == now.id();

				if (onIt) {
					break;
				}

				BlockPos hit = crosshairBlock();

				if (hit != null && onServer(p -> {
					BlockState state = p.level().getBlockState(hit);
					return state.canBeReplaced() && state.getFluidState().isEmpty();
				})) {
					// Grass or a flower in the line of sight catches the crosshair: knock it away.
					cleared.add(blockName(hit));
					context.getInput().pressKey(ATTACK);
					context.waitTicks(4);
				} else if (i == 3) {
					// Something solid (the edge of their ledge) is in the way: step right up to them,
					// climbing onto their ground when it is one block higher.
					walkTo(now.pos(), 1.0, 60, false, null);
				} else if (i == 5) {
					// Still blocked from up close: back off to another spot with a view, if any.
					Vec3 other = talkSpot(id);

					if (other != null) {
						walkTo(other, 0.4, 120, false, null);
					}
				}
			}

			if (!onIt) {
				Target last = target(id);
				double dist = context.computeOnClient(mc -> mc.player.position().distanceTo(last.pos()));
				note(step, Result.INFO, "talk attempt " + attempt + ": the crosshair is not on them (" + lookingAt() + String.format(Locale.ROOT,
						"; they are at %.1f, %.1f, %.1f, %.1f blocks away, %.1f tall", last.pos().x, last.pos().y, last.pos().z, dist, last.height()) + ")"
						+ (spot == null ? ", no standing spot with a clear view found" : String.format(Locale.ROOT, ", stood at %.1f, %.1f, %.1f", spot.x, spot.y, spot.z))
						+ (cleared.isEmpty() ? "" : ", knocked away " + cleared));
				continue;
			}

			int mark = MESSAGES.size();
			context.getInput().pressKey(USE);
			boolean open = waitClient(40, mc -> mc.gui.screen() instanceof DialogueScreen) && sessionAt() != null;

			if (open) {
				note(step, Result.INFO, "right-clicked: dialogue " + sessionAt());
				return true;
			}

			List<String> said;

			synchronized (MESSAGES) {
				said = new ArrayList<>(MESSAGES.subList(Math.min(mark, MESSAGES.size()), MESSAGES.size()));
			}

			note(step, Result.INFO, "talk attempt " + attempt + ": right-click opened no dialogue; messages " + said);
			closeScreen();
		}

		note(step, Result.FAIL, "could not open a dialogue by right-clicking");
		return false;
	}

	/**
	 * A standing spot 2 to 2.5 blocks from the entity, on ground within a block of its feet, from
	 * which a player's eye sees its eyes past every solid block (grass does not count; the bot knocks
	 * it away). The one nearest the player, or null. Two to 2.5 blocks keeps the entity within the
	 * 3-block interaction reach while looking past the edge of the ground it stands on.
	 */
	private @Nullable Vec3 talkSpot(UUID id) {
		Vec3 me = here();
		return onServer(p -> {
			ServerLevel level = p.level();
			Entity e = level.getEntity(id);

			if (e == null) {
				return null;
			}

			Vec3 eyes = e.getEyePosition();
			Vec3 best = null;
			double bestDistance = Double.MAX_VALUE;

			for (double r : new double[] { 2.0, 2.5 }) {
				for (int k = 0; k < 16; k++) {
					double angle = k * Math.PI / 8;
					int x = Mth.floor(e.getX() + Math.cos(angle) * r);
					int z = Mth.floor(e.getZ() + Math.sin(angle) * r);

					for (int y = Mth.floor(e.getY()) + 1; y >= Mth.floor(e.getY()) - 1; y--) {
						BlockPos feet = new BlockPos(x, y, z);
						BlockState ground = level.getBlockState(feet.below());
						boolean standable = !ground.getCollisionShape(level, feet.below()).isEmpty() && ground.getFluidState().isEmpty()
								&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet).getFluidState().isEmpty()
								&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();

						if (!standable) {
							continue;
						}

						Vec3 spot = Vec3.atBottomCenterOf(feet);
						Vec3 eye = spot.add(0, p.getEyeHeight(), 0);
						boolean clear = level.clip(new ClipContext(eye, eyes, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
						double distance = spot.distanceToSqr(me);

						if (clear && distance < bestDistance) {
							best = spot;
							bestDistance = distance;
						}

						break;
					}
				}
			}

			return best;
		});
	}

	/**
	 * Picks a reply in the open dialogue the way a player does: Space shows the whole line (the
	 * replies only appear once it has typed out), then the reply's number key. The number is
	 * where the reply sits among the replies the screen lists (hidden ones left out).
	 */
	private boolean reply(String step, String textKey) {
		String at = sessionAt();

		if (at == null) {
			note(step, Result.FAIL, "wanted to answer " + textKey + " but no dialogue is open");
			return false;
		}

		waitClient(40, mc -> mc.gui.screen() instanceof DialogueScreen);
		int row = dialogueRow(textKey);

		if (row <= 0) {
			note(step, Result.FAIL, "reply " + textKey + " at " + at + " is " + (row < 0 ? "shown disabled" : "not offered"));
			return false;
		}

		context.getInput().pressKey(InputConstants.KEY_SPACE);
		context.waitTicks(2);
		context.getInput().pressKey(InputConstants.KEY_1 + row - 1);
		boolean moved = waitServer(40, p -> !at.equals(sessionAt(p)));
		note(step, moved ? Result.INFO : Result.FAIL, "pressed " + row + " for " + textKey + " at " + at + " -> " + (moved ? sessionAt() : "no change"));
		context.waitTicks(3);
		return moved;
	}

	/** Talks to {@code id} and picks {@code replies} in order. Returns how many were made; closes the dialogue if it stays open. */
	private int talkAndChoose(String step, UUID id, @Nullable String shotName, String... replies) {
		if (!talkTo(step, id)) {
			return 0;
		}

		if (shotName != null) {
			context.waitTicks(10);
			shot(shotName);
		}

		if (replies.length > 0 && sessionAt() == null) {
			// Any hit closes a dialogue: let the fight settle and talk again.
			note(step, Result.INFO, "the dialogue closed before the first reply; talking again once the fight settles");
			settleBeforeDialogue(step);

			if (!talkTo(step, id)) {
				return 0;
			}
		}

		int made = 0;

		for (String reply : replies) {
			if (!reply(step, reply)) {
				break;
			}

			made++;
		}

		if (sessionAt() != null) {
			closeScreen();
		}

		return made;
	}

	/**
	 * 1-based number of the reply with {@code textKey} among the replies the open dialogue node
	 * shows (as the server sends them: options hidden when unmet are left out); negative when it
	 * is shown disabled, 0 when it is not there.
	 */
	private int dialogueRow(String textKey) {
		return onServer(p -> {
			DialogueSession session = Dialogues.session(p);
			DialogueDefinition definition = session == null ? null : DialogueLoader.get(session.dialogue());
			DialogueDefinition.Node node = definition == null ? null : definition.nodes().get(session.node());

			if (node == null) {
				return 0;
			}

			int row = 0;

			for (DialogueDefinition.Option option : node.options()) {
				boolean met = Dialogues.failReason(option.conditions(), p, null) == null;

				if (!met && option.hiddenIfUnmet()) {
					continue;
				}

				row++;

				if (row > DialogueNodePayload.MAX_OPTIONS) {
					return 0;
				}

				if (option.text().equals(textKey)) {
					return met ? row : -row;
				}
			}

			return 0;
		});
	}

	/** "dialogue/node" of the player's open dialogue session, or null. Server thread. */
	private static @Nullable String sessionAt(ServerPlayer p) {
		DialogueSession session = Dialogues.session(p);
		return session == null ? null : session.dialogue().getPath() + "/" + session.node();
	}

	private @Nullable String sessionAt() {
		return onServer(p -> sessionAt(p));
	}

	/** Presses an enabled button whose label is the lang key {@code key} (with any arguments). */
	private boolean clickButton(String key) {
		return context.computeOnClient(mc -> {
			if (mc.gui.screen() == null) {
				return false;
			}

			for (GuiEventListener child : mc.gui.screen().children()) {
				if (child instanceof Button button && button.active && button.getMessage().getContents() instanceof TranslatableContents text
						&& text.getKey().equals(key)) {
					button.onPress(new KeyEvent(257, 0, 0)); // Enter, as a keyboard press would
					return true;
				}
			}

			return false;
		});
	}

	/** Moves to a quiet flat spot when poachers are near or the player is underground (a logged shortcut). */
	private void quietPlace(String step) {
		boolean threatened = !poachersNear(64).isEmpty();
		BlockPos feet = onServer(p -> p.blockPosition());
		boolean underground = feet.getY() < surfaceY(feet.getX(), feet.getZ()) - 4;

		if (!threatened && !underground) {
			return;
		}

		int fx = feet.getX();
		int fz = feet.getZ();
		Vec3 spot = onServer(p -> {
			int[][] offsets = { { 120, 0 }, { -120, 0 }, { 0, 120 }, { 0, -120 }, { 160, 160 }, { -160, -160 } };

			for (int[] o : offsets) {
				Vec3 found = findFlatSpot(p.level(), fx + o[0], fz + o[1], 48);

				if (found != null && p.level().getEntities((Entity) null, new AABB(BlockPos.containing(found)).inflate(64),
						e -> e instanceof CrystalPoacher && e.isAlive()).isEmpty()) {
					return found;
				}
			}

			return null;
		});

		if (spot == null) {
			note(step, Result.FAIL, "no quiet flat spot found away from the poachers; staying at " + feet.toShortString());
			return;
		}

		shortcut("teleported " + Math.round(flat(spot, Vec3.atBottomCenterOf(feet))) + " blocks to a quiet flat spot for Chapter Two ("
				+ (threatened ? "poachers nearby" : "underground") + ")");
		teleport(spot, 0f);
	}

	/** Back near {@code centre} after a respawn (a logged teleport when further than {@code range}). */
	private void returnTo(String step, Vec3 centre, double range) {
		if (flat(centre, here()) > range) {
			Vec3 spot = safeSpotNear(centre.add(away(centre, here()).scale(12)));
			shortcut("returned to the raider camp after respawning (" + step + ")");
			teleport(spot, yawToward(spot, centre));
		}
	}

	/** Removes stray vanilla hostiles near the player; story NPCs and poachers are kept. */
	private void clearStrays(String step) {
		int removed = onServer(p -> {
			int n = 0;

			for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(24),
					x -> x instanceof Enemy && x.isAlive() && StoryTags.owner(x) == null && !(x instanceof CrystalPoacher))) {
				e.discard();
				n++;
			}

			return n;
		});

		if (removed > 0) {
			shortcut("removed stray hostile mobs that wandered in (" + step + ")");
		}
	}

	/** Swings a heavy attack (Sneak + Attack with the lit saber) at {@code id} once it is under the crosshair. */
	private void heavyAttack(String step, UUID id) {
		try {
			for (int t = 0; t < 300; t++) {
				Target target = target(id);

				if (!target.alive()) {
					return;
				}

				Me me = me();

				if (me.dead()) {
					died(step);
				}

				keepAlive(step);
				aimAt(target.pos().add(0, target.height() * 0.5, 0));
				double distance = flat(me.pos(), target.pos());
				setForward(distance > 2.2, distance > 3.5);

				if (me.picked() == target.id() && me.strength() >= 0.95f) {
					setForward(false, false);
					float before = healthOf(id);
					context.getInput().holdKey(SNEAK);

					try {
						context.getInput().pressKey(ATTACK);

						for (int i = 0; i < 16; i++) {
							Target now = target(id);

							if (now.alive()) {
								aimAt(now.pos().add(0, now.height() * 0.5, 0));
							}

							tickMe(step);

							if (i == 6) {
								shot("heavy_attack");
							}
						}
					} finally {
						context.getInput().releaseKey(SNEAK);
					}

					note(step, Result.INFO, String.format(Locale.ROOT, "heavy attack (Sneak + Attack): target health %.1f -> %.1f", before, healthOf(id)));
					return;
				}

				if (me.collided() && me.onGround()) {
					jump(step);
				} else {
					tickMe(step);
				}
			}

			note(step, Result.INFO, "no heavy attack: the target never came under the crosshair");
		} finally {
			setForward(false, false);
		}
	}

	/** Takes the saber in hand and lights it with G if it is not lit. */
	private boolean igniteSaber() {
		if (!select(SaberItems.LIGHTSABER)) {
			return false;
		}

		if (!onServer(p -> SaberComponents.isIgnited(p.getMainHandItem()))) {
			context.getInput().pressKey(InputConstants.KEY_U);
			waitServer(10, p -> SaberComponents.isIgnited(p.getMainHandItem()));
		}

		return onServer(p -> SaberComponents.isIgnited(p.getMainHandItem()));
	}

	/**
	 * Faces the spar partner while looking over her head, so the crosshair is not on her: a
	 * right-click on a mentor talks instead of raising the guard.
	 */
	private void faceOver(Target target) {
		if (target.alive()) {
			aimAt(target.pos().add(0, target.height() + 2.5, 0));
		}
	}

	/** Ends a spar still running (a logged shortcut). */
	private void endSpar(String step) {
		boolean ended = onServer(p -> {
			Mob partner = Spar.partner(p);

			if (partner == null) {
				return false;
			}

			Spar.endWithoutResult(partner);
			return true;
		});

		if (ended) {
			shortcut("ended a spar that was still running (" + step + ")");
		}
	}

	private void shortcutLesson(String step) {
		shortcut("completed the lesson with /oldrepublic story lesson complete (" + step + ")");
		server.runCommand("oldrepublic story lesson @p complete");
		context.waitTicks(5);
	}

	private SkillRules.LearnCheck learnCheck(String node) {
		return onServer(p -> SkillRules.check(SkillTrees.server(), SkillService.data(p), Skills.context(p), node, SkillService.balance().switchPenalty));
	}

	/** Slot index (0-based) holding {@code ability}, or -1. */
	private int slotOf(String ability) {
		return onServer(p -> SkillService.data(p).slotOf(ability));
	}

	/**
	 * Holds a power slot's key: R, Z, V or B, with Focus held first for the second bar's slots 5 to 8
	 * (MILESTONE_5.md 9.1: the bar is chosen when the slot key goes down).
	 */
	private void holdSlot(int slot) {
		if (slot >= SkillData.BAR_SLOTS) {
			context.getInput().holdKey(ForceKeys.FOCUS);
			context.waitTick();
		}

		context.getInput().holdKey(ForceKeys.SLOTS.get(slot % SkillData.BAR_SLOTS));
	}

	/** Releases what {@link #holdSlot} held. */
	private void releaseSlot(int slot) {
		context.getInput().releaseKey(ForceKeys.SLOTS.get(slot % SkillData.BAR_SLOTS));

		if (slot >= SkillData.BAR_SLOTS) {
			context.getInput().releaseKey(ForceKeys.FOCUS);
		}
	}

	private GuardState guardOf(UUID id) {
		return onServer(p -> p.level().getEntity(id) instanceof MentorEntity mentor ? mentor.guardState() : GuardState.READY);
	}

	private boolean yielded(UUID id) {
		return onServer(p -> p.level().getEntity(id) instanceof Yieldable yieldable && yieldable.isYielded());
	}

	private float healthOf(UUID id) {
		return onServer(p -> p.level().getEntity(id) instanceof LivingEntity living ? living.getHealth() : 0f);
	}

	/** Summons {@code n} slowed husks in a row across {@code side} around {@code centre} and returns their ids. */
	private List<UUID> summonHusks(Vec3 centre, Vec3 side, int n) {
		Set<UUID> before = new HashSet<>(entitiesOfType("husk", centre, 24));

		for (int i = 0; i < n; i++) {
			double offset = (i - (n - 1) / 2.0) * 2.0;
			int x = Mth.floor(centre.x + side.x * offset);
			int z = Mth.floor(centre.z + side.z * offset);
			int y = surfaceY(x, z);
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {PersistenceRequired:1b,"
					+ "active_effects:[{id:\"minecraft:slowness\",amplifier:3,duration:-1,show_particles:0b}]}", x, y, z));
		}

		context.waitTicks(2);
		return entitiesOfType("husk", centre, 24).stream().filter(id -> !before.contains(id)).toList();
	}

	private List<UUID> entitiesOfType(String path, Vec3 centre, double radius) {
		return onServer(p -> p.level().getEntities((Entity) null, new AABB(BlockPos.containing(centre)).inflate(radius),
				e -> e.isAlive() && path.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())).stream().map(Entity::getUUID).toList());
	}

	/** Discards the live entities among {@code ids}; returns how many there were. */
	private int discardAll(List<UUID> ids) {
		return onServer(p -> {
			int n = 0;

			for (UUID id : ids) {
				Entity e = p.level().getEntity(id);

				if (e != null && e.isAlive()) {
					e.discard();
					n++;
				}
			}

			return n;
		});
	}

	/** This player's live raider crew around the camp, nearest first. */
	private List<UUID> crewNear(BlockPos site) {
		return onServer(p -> p.level().getEntities((Entity) null, new AABB(site).inflate(64), e -> e.isAlive() && StoryTags.isCrew(e)
				&& StoryTags.isOwnedBy(e, p.getUUID())).stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::getUUID).toList());
	}

	/** This player's bound captives around the camp, nearest first. */
	private List<UUID> captivesNear(BlockPos site) {
		return onServer(p -> p.level().getEntities((Entity) null, new AABB(site).inflate(64), e -> e instanceof FrontierSettler settler && settler.isAlive()
				&& settler.state() == FrontierSettler.State.CAPTIVE && p.getUUID().equals(settler.owner())).stream()
				.sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::getUUID).toList());
	}

	/** Non-natural blocks in the 9x9 area around a camp anchor, from a block below to four above. */
	private int campBlocks(BlockPos camp) {
		return onServer(p -> {
			int n = 0;

			for (BlockPos pos : BlockPos.betweenClosed(camp.offset(-4, -1, -4), camp.offset(4, 4, 4))) {
				BlockState state = p.level().getBlockState(pos);

				if (!state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty() && !state.is(MeetingSites.STORY_GROUND)
						&& !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)) {
					n++;
				}
			}

			return n;
		});
	}

	private @Nullable UUID mentorId(String role) {
		return onServer(p -> Story.get(p).mentor(role));
	}

	private StoryData story() {
		return onServer(p -> Story.get(p).copy());
	}

	private int alignment() {
		return onServer(p -> Identity.alignment(p));
	}

	private String storyLine() {
		return onServer(p -> {
			StoryData d = Story.get(p);
			return "crossing " + d.crossing() + ", path " + d.storyPath() + " at " + d.stage() + ", encounter " + d.encounterState() + " (captain "
					+ d.captainFate() + ", captives " + d.captivesFate() + ")";
		});
	}

	private String standing() {
		return onServer(p -> "alignment " + Identity.alignment(p) + ", Jedi Order " + Identity.reputation(p, Faction.JEDI_ORDER) + ", Sith Empire "
				+ Identity.reputation(p, Faction.SITH_EMPIRE) + ", Frontier " + Identity.reputation(p, Faction.FRONTIER) + ", trust jedi "
				+ Identity.trust(p, "jedi") + ", path " + Identity.path(p) + ", rank " + Identity.rank(p));
	}

	private Vec3 here() {
		return context.computeOnClient(mc -> mc.player.position());
	}

	private static double flat(Vec3 a, Vec3 b) {
		return a.subtract(b).horizontalDistance();
	}

	/** Horizontal unit vector from {@code from} toward {@code to} (south when they coincide). */
	private static Vec3 away(Vec3 from, Vec3 to) {
		Vec3 v = new Vec3(to.x - from.x, 0, to.z - from.z);
		return v.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : v.normalize();
	}

	private static float yawToward(Vec3 from, Vec3 to) {
		return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90f;
	}

	private static String shortPos(@Nullable BlockPos pos) {
		return pos == null ? "-" : pos.toShortString();
	}

	// ---------------------------------------------------------------------------------------------
	// Movement and combat
	// ---------------------------------------------------------------------------------------------

	/**
	 * Walks towards {@code target} (horizontal distance) with forward held, jumping when blocked.
	 * Stops at {@code tolerance}, when {@code done} becomes true, on timeout or when stuck.
	 */
	private boolean walkTo(Vec3 target, double tolerance, int timeout, boolean sprint, @Nullable BooleanSupplier done) {
		return walkTo(target, tolerance, timeout, sprint, done, true);
	}

	/**
	 * As above; with {@code jumps} false the walk never jumps (a planned leg that only goes level or
	 * down, where a jump against a wall would leave the way).
	 */
	private boolean walkTo(Vec3 target, double tolerance, int timeout, boolean sprint, @Nullable BooleanSupplier done, boolean jumps) {
		Vec3 lastCheck = null;
		int stalls = 0;
		walkStop = "timeout after " + timeout + " ticks";

		try {
			for (int t = 0; t < timeout; t++) {
				Me me = me();

				if (me.dead()) {
					died("walk");
				}

				double distance = me.pos().subtract(target).horizontalDistance();

				if (distance <= tolerance || (done != null && t % 10 == 0 && done.getAsBoolean())) {
					walkStop = "";
					return true;
				}

				if (me.onGround()) {
					String drop = dropAhead(me.pos(), target);

					if (drop != null) {
						walkStop = drop + " at " + BlockPos.containing(me.pos()).toShortString() + ", " + Math.round(distance) + " blocks from the target";
						return false;
					}
				}

				if (t % 40 == 0) {
					if (lastCheck != null && me.pos().distanceTo(lastCheck) < 0.5) {
						stalls++;

						if (stalls >= 3) {
							walkStop = "stalled at " + BlockPos.containing(me.pos()).toShortString() + ", " + Math.round(distance) + " blocks from the target, ahead: "
									+ describeAhead(me.pos(), target);
							return false;
						}
					} else {
						stalls = 0;
					}

					lastCheck = me.pos();
				}

				aimFlat(me.eye(), target);
				setForward(true, sprint);
				keepAlive("walk");

				if (jumps && me.collided() && me.onGround()) {
					jump("walk");
				} else {
					tickMe("walk");
				}
			}

			return false;
		} finally {
			setForward(false, false);
		}
	}

	/**
	 * Climbs a run of ledges from {@code foot}, one standing cell of {@code stands} at a time: holds
	 * forward towards the cell's centre and jumps while below it, until standing on it. Returns null
	 * on the last one, else which ledge stopped the climb and the last ticks' positions.
	 */
	private @Nullable String climbLedges(BlockPos foot, List<BlockPos> stands) {
		walkTo(Vec3.atBottomCenterOf(foot), 0.2, 100, false, null);

		try {
			for (int i = 0; i < stands.size(); i++) {
				BlockPos stand = stands.get(i);
				Vec3 goal = Vec3.atBottomCenterOf(stand);
				List<String> trace = new ArrayList<>();
				boolean on = false;

				for (int t = 0; t < 80 && !on; t++) {
					Me me = me();

					if (me.dead()) {
						died("climb");
					}

					boolean up = me.pos().y > stand.getY() - 0.05;
					on = me.onGround() && up && me.pos().subtract(goal).horizontalDistance() < 0.4;

					if (on) {
						break;
					}

					if (t >= 56 && t % 4 == 0) {
						trace.add(String.format(Locale.ROOT, "%.2f %.2f %.2f ground=%s collided=%s", me.pos().x, me.pos().y, me.pos().z, me.onGround(), me.collided()));
					}

					aimFlat(me.eye(), goal);
					setForward(true, false);

					if (me.onGround() && !up) {
						jump("climb");
					} else {
						tickMe("climb");
					}
				}

				if (!on) {
					return "ledge " + (i + 1) + "/" + stands.size() + " (stand " + stand.toShortString() + ") not reached; last ticks: " + String.join("; ", trace);
				}
			}

			return null;
		} finally {
			setForward(false, false);
		}
	}

	/**
	 * Looks 0.8 and 1.6 blocks ahead towards {@code target} for a drop: no solid ground (or water)
	 * to stand on from one block above the feet down to three below (the block under that stand is
	 * four below), or lava. Returns a description or null.
	 */
	private @Nullable String dropAhead(Vec3 pos, Vec3 target) {
		Vec3 flat = new Vec3(target.x - pos.x, 0, target.z - pos.z);

		if (flat.lengthSqr() < 1.0E-4) {
			return null;
		}

		Vec3 dir = flat.normalize();
		// Probing past the target would see the drop beyond a route corner (the gallery edge) the walk turns at.
		double reach = flat.length() + 0.4;
		return context.computeOnClient(mc -> {
			for (double ahead : new double[] { 0.8, 1.6 }) {
				if (ahead > reach) {
					break;
				}

				Vec3 probe = pos.add(dir.scale(ahead));
				int x = Mth.floor(probe.x);
				int z = Mth.floor(probe.z);
				int feet = Mth.floor(pos.y + 0.01);
				boolean ground = false;

				// From the edge of a tread, 1.6 blocks ahead on a stair going down one per block is the tread three lower: its block is four below the feet.
				for (int y = feet + 1; y >= feet - 4 && !ground; y--) {
					BlockPos at = new BlockPos(x, y, z);
					BlockState state = mc.level.getBlockState(at);

					// API-CHECK: BlockState#getFluidState().isEmpty() (as in PushFx).
					if (state.is(Blocks.LAVA)) {
						return "lava ahead at " + at.toShortString();
					}

					ground = !state.getCollisionShape(mc.level, at).isEmpty() || !state.getFluidState().isEmpty();
				}

				if (!ground) {
					return "drop of more than 3 blocks ahead at " + x + ", " + z;
				}
			}

			return null;
		});
	}

	/**
	 * A standing spot on the surface 2 to 6 blocks from {@code target} whose ground is solid (not
	 * fluid) and within 3 blocks of the target's height; the target itself if there is none.
	 */
	private Vec3 safeSpotNear(Vec3 target) {
		int tx = Mth.floor(target.x);
		int tz = Mth.floor(target.z);
		int ty = Mth.floor(target.y);
		int[][] dirs = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 }, { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 } };

		for (int r = 2; r <= 6; r++) {
			for (int[] d : dirs) {
				int x = tx + d[0] * r;
				int z = tz + d[1] * r;
				Vec3 spot = onServer(p -> {
					ServerLevel level = p.level();
					level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					BlockPos ground = new BlockPos(x, y - 1, z);
					BlockState state = level.getBlockState(ground);
					boolean solid = !state.getCollisionShape(level, ground).isEmpty() && state.getFluidState().isEmpty();
					boolean free = level.getBlockState(new BlockPos(x, y, z)).getCollisionShape(level, new BlockPos(x, y, z)).isEmpty()
							&& level.getBlockState(new BlockPos(x, y + 1, z)).getCollisionShape(level, new BlockPos(x, y + 1, z)).isEmpty();
					return solid && free && Math.abs(y - ty) <= 3 ? new Vec3(x + 0.5, y, z + 0.5) : null;
				});

				if (spot != null) {
					return spot;
				}
			}
		}

		return target;
	}

	/**
	 * Teleports the player within the world they are in (a logged shortcut at the call site) and waits
	 * for the chunks. Server commands run in the overworld, so in any other world the teleport names it.
	 */
	private void teleport(Vec3 pos, float yaw) {
		ResourceKey<Level> dim = onServer(p -> p.level().dimension());
		String in = dim.equals(Level.OVERWORLD) ? "" : "execute in " + dim.identifier() + " run ";
		server.runCommand(in + String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f 0", pos.x, pos.y, pos.z, yaw));
		context.waitTicks(2);
		waitForChunks("teleport");
	}

	/**
	 * Handles a death: logs it as a FAIL with its cause, presses Respawn on the death screen and
	 * throws {@link Died} so the step is retried from the respawn point.
	 */
	private void died(String step) {
		releaseAll();
		String cause = onServer(p -> {
			try {
				// API-CHECK: LivingEntity#getCombatTracker + CombatTracker#getDeathMessage (standard).
				return p.getCombatTracker().getDeathMessage().getString();
			} catch (RuntimeException e) {
				return "unknown cause (" + e.getClass().getSimpleName() + ")";
			}
		});
		Vec3 at = context.computeOnClient(mc -> mc.player.position());
		note(step, Result.FAIL, "the player died at " + BlockPos.containing(at).toShortString() + ": " + cause);
		context.waitTicks(20);
		shot("death");
		boolean alive = false;

		for (int t = 0; t < 200 && !alive; t++) {
			if (t % 10 == 0) {
				// Real click on the death screen's Respawn button (enabled after a short delay).
				context.tryClickScreenButton("deathScreen.respawn");
			}

			context.waitTick();
			alive = context.computeOnClient(mc -> mc.player != null && mc.player.isAlive() && mc.gui.screen() == null);
		}

		if (!alive) {
			stuck(step, "died (" + cause + ") and could not respawn through the death screen");
		}

		waitForChunks(step);
		lastRescueTick = -1000;
		Vec3 spawn = context.computeOnClient(mc -> mc.player.position());
		boolean kept = count(Items.IRON_SWORD) > 0;
		note(step, Result.INFO, "respawned at " + BlockPos.containing(spawn).toShortString() + " with the inventory " + (kept ? "kept" : "LOST"));
		throw new Died(step + ": " + cause);
	}

	/** Back near the heart after a respawn: teleports to the cavern entrance tunnel if far away. */
	private boolean returnToHeart(String step) {
		Heart h = heart;
		Vec3 me = context.computeOnClient(mc -> mc.player.position());

		if (h != null && me.distanceTo(Vec3.atBottomCenterOf(h.stone())) > 40) {
			Vec3 entrance = h.feet(22, 1, 5);
			shortcut("returned to the cave after respawning (" + step + ")");
			teleport(entrance, h.yawInward());
			clearInterference(step);
			return true;
		}

		return false;
	}

	/** The blocks just ahead towards {@code target} at feet, head and above-head height (diagnostics). */
	private String describeAhead(Vec3 pos, Vec3 target) {
		Vec3 flat = new Vec3(target.x - pos.x, 0, target.z - pos.z);
		Vec3 probe = flat.lengthSqr() < 1.0E-4 ? pos : pos.add(flat.normalize().scale(0.8));
		return context.computeOnClient(mc -> {
			List<String> names = new ArrayList<>();

			for (int dy = -1; dy <= 2; dy++) {
				BlockPos at = BlockPos.containing(probe.x, pos.y + 0.01 + dy, probe.z);
				names.add("y" + (dy >= 0 ? "+" : "") + dy + " " + BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(at).getBlock()).getPath());
			}

			return String.join(", ", names);
		});
	}

	/** A block with a partial collision shape (like a crystal cluster) right ahead at feet or head height, or null. */
	private @Nullable BlockPos partialObstacleAhead(Vec3 pos, Vec3 target) {
		Vec3 flat = new Vec3(target.x - pos.x, 0, target.z - pos.z);

		if (flat.lengthSqr() < 1.0E-4) {
			return null;
		}

		Vec3 dir = flat.normalize();
		return context.computeOnClient(mc -> {
			for (double ahead : new double[] { 0.6, 1.2 }) {
				Vec3 probe = pos.add(dir.scale(ahead));

				for (int dy = 0; dy <= 1; dy++) {
					BlockPos at = BlockPos.containing(probe.x, pos.y + 0.01 + dy, probe.z);
					BlockState state = mc.level.getBlockState(at);

					if (!state.getCollisionShape(mc.level, at).isEmpty() && !state.isCollisionShapeFullBlock(mc.level, at) && !state.is(Blocks.CHEST)) {
						return at;
					}
				}
			}

			return null;
		});
	}

	/**
	 * Walks a list of waypoints; returns false if any leg did not arrive. A leg that stalls on a
	 * partial block in the way (a crystal cluster on the cavern floor) mines it, as a player would,
	 * and tries the leg once more.
	 */
	private boolean walkPath(String step, Vec3... points) {
		boolean all = true;

		for (Vec3 point : points) {
			boolean arrived = walkTo(point, 0.6, 300, false, null);

			if (!arrived) {
				Vec3 at = context.computeOnClient(mc -> mc.player.position());
				note(step, Result.INFO, "walk leg to " + BlockPos.containing(point).toShortString() + " stopped at " + BlockPos.containing(at).toShortString()
						+ ": " + walkStop);
				BlockPos obstacle = partialObstacleAhead(at, point);

				if (obstacle != null) {
					String name = onServer(p -> BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(obstacle).getBlock()).getPath());
					select(Items.IRON_PICKAXE);
					boolean mined = mine(obstacle, Vec3.atCenterOf(obstacle));
					note(step, Result.INFO, "mined the " + name + " in the way at " + obstacle.toShortString() + ": " + mined);
					arrived = walkTo(point, 0.6, 300, false, null);

					if (!arrived) {
						note(step, Result.INFO, "walk leg to " + BlockPos.containing(point).toShortString() + " still stopped: " + walkStop);
					}
				}
			}

			all &= arrived;
		}

		return all;
	}

	/** What the crosshair is on (client hit result), for logs. */
	private String lookingAt() {
		return context.computeOnClient(mc -> {
			HitResult hit = mc.hitResult;

			if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
				return "block " + BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(block.getBlockPos()).getBlock()).getPath() + " at "
						+ block.getBlockPos().toShortString();
			}

			if (hit instanceof EntityHitResult entity && hit.getType() == HitResult.Type.ENTITY) {
				return "entity " + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getEntity().getType()).getPath();
			}

			return "nothing in reach";
		});
	}

	/**
	 * Melee fight: chase (sprinting, so a kiting ranged enemy cannot simply walk away), aim at the
	 * middle of the target's body and swing whenever the attack is fully charged and the crosshair
	 * is on the target, which also means it is within reach. With {@code usePush} also charges a
	 * Force Push when close and affordable. A target that stays out of reach for 10 s across a
	 * height difference or while the bot walks into something (a ledge it cannot climb, a tree) is
	 * reached with a logged teleport.
	 *
	 * @return whether the target died
	 */
	private boolean fight(UUID id, int timeout, boolean usePush, String step) {
		return fight(id, timeout, usePush, step, null);
	}

	/**
	 * {@link #fight(UUID, int, boolean, String)} that also ends as soon as {@code stop} holds,
	 * checked before every swing (a yielding Captain, a spar that ended).
	 *
	 * @return whether the target died or {@code stop} came true
	 */
	private boolean fight(UUID id, int timeout, boolean usePush, String step, @Nullable BooleanSupplier stop) {
		int lastPush = -1000;
		int blocked = 0;
		int outOfReach = 0;
		int swings = 0;

		try {
			for (int t = 0; t < timeout; t++) {
				Target target = target(id);

				if (!target.alive() || (stop != null && stop.getAsBoolean())) {
					return true;
				}

				Me me = me();

				if (me.dead()) {
					died(step);
				}

				keepAlive(step);
				aimAt(target.pos().add(0, target.height() * 0.5, 0));
				double distance = me.pos().subtract(target.pos()).horizontalDistance();
				double rise = target.pos().y - me.pos().y;
				String drop = distance > 1.8 && me.onGround() ? dropAhead(me.pos(), target.pos()) : null;
				setForward(distance > 1.8 && drop == null, distance > 2.5 && drop == null);
				outOfReach = me.picked() == target.id() ? 0 : outOfReach + 1;

				if (outOfReach >= 200 && (Math.abs(rise) >= 1.5 || me.collided())) {
					// Out of reach for 10 s with a ledge in between, or walking into something: the
					// bot cannot climb two blocks or path around obstacles.
					outOfReach = 0;
					Vec3 spot = safeSpotNear(target.pos());
					shortcut("teleported next to a fight target the bot could not reach (" + step + ")");
					note(step, Result.INFO, String.format(Locale.ROOT, "fight target out of reach for 10 s, %.1f blocks away and %.1f blocks up (bot blocked=%s);"
							+ " teleporting to %s", distance, rise, me.collided(), BlockPos.containing(spot).toShortString()));
					teleport(spot, 0f);
					continue;
				}

				if (drop != null && ++blocked >= 60) {
					// The target stays across a drop: hop over to safe ground next to it.
					blocked = 0;
					Vec3 spot = safeSpotNear(target.pos());
					shortcut("teleported across a drop towards a fight target (" + step + ")");
					note(step, Result.INFO, "fight target behind a drop (" + drop + "); teleporting to " + BlockPos.containing(spot).toShortString());
					teleport(spot, 0f);
					continue;
				} else if (drop == null) {
					blocked = 0;
				}

				if (usePush && t - lastPush > 100 && distance < 5 && pushReady()) {
					lastPush = t;
					setForward(false, false);
					context.getInput().holdKeyFor(InputConstants.KEY_R, PUSH_HOLD_TICKS);
					continue;
				}

				if (me.strength() >= 0.95f && me.picked() == target.id()) {
					swings++;
					context.getInput().pressKey(ATTACK);
				} else if (me.collided() && me.onGround()) {
					jump(step);
				} else {
					tickMe(step);
				}
			}

			boolean dead = !target(id).alive() || (stop != null && stop.getAsBoolean());

			if (!dead) {
				note(step, Result.INFO, "fight timed out after " + timeout + " ticks with " + swings + " charged swing(s) on the target");
			}

			return dead;
		} finally {
			setForward(false, false);
		}
	}

	/** Holds the attack key on a block until it breaks. */
	private boolean mine(BlockPos pos, Vec3 face) {
		return mine(null, pos, face);
	}

	/**
	 * Holds the attack key on a block until it breaks; with a step, logs what happened. Walks into
	 * reach first and re-aims every tick: a knock (an unstable cluster's discharge, a hit) moves
	 * the bot while it mines, and with a fixed view the crosshair can drift off the block or out of
	 * reach, which silently restarts or stops the mining.
	 */
	private boolean mine(@Nullable String step, BlockPos pos, Vec3 face) {
		String was = blockName(pos);

		if (onServer(p -> p.level().getBlockState(pos).isAir())) {
			if (step != null) {
				note(step, Result.INFO, "mine " + pos.toShortString() + ": already air (" + was + ")");
			}

			return true;
		}

		approachToMine(pos, face);
		aimAt(face);
		context.waitTick();
		String looking = lookingAt();
		double reachAtStart = eyeDistance(face);
		String tool = onServer(p -> BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).getPath());
		float damageBefore = damageTaken;
		context.getInput().holdKey(ATTACK);
		boolean broken = false;
		int held = 0;
		int onTarget = 0;
		int reapproached = 0;

		try {
			for (; held < 160 && !broken; held++) {
				aimAt(face);
				tickMe("mine");

				if (pos.equals(crosshairBlock())) {
					onTarget++;
				} else if (held % 20 == 19 && reapproached < 3) {
					// Knocked away or out of reach: step back in, then keep mining.
					reapproached++;
					context.getInput().releaseKey(ATTACK);
					approachToMine(pos, face);
					aimAt(face);
					context.waitTick();
					context.getInput().holdKey(ATTACK);
				}

				broken = onServer(p -> p.level().getBlockState(pos).isAir());
			}
		} finally {
			context.getInput().releaseKey(ATTACK);
		}

		if (step != null) {
			note(step, Result.INFO, String.format(Locale.ROOT, "mine %s (%s): crosshair on %s, eye %.2f blocks from the aim point, tool %s, held attack %d ticks"
					+ " (crosshair on the block %d of them, re-approached %d times, damage taken meanwhile %d), broken=%s, now %s", pos.toShortString(), was,
					looking, reachAtStart, tool, held, onTarget, reapproached, Math.round(damageTaken - damageBefore), broken, blockName(pos)));
		}

		return broken;
	}

	/** Walks towards a block to mine until the aim point is comfortably within block reach. */
	private void approachToMine(BlockPos pos, Vec3 face) {
		if (eyeDistance(face) > MINE_REACH) {
			walkTo(Vec3.atBottomCenterOf(pos), 1.2, 80, false, () -> eyeDistance(face) <= MINE_REACH);
		}
	}

	private double eyeDistance(Vec3 point) {
		return context.computeOnClient(mc -> mc.player.getEyePosition().distanceTo(point));
	}

	/** The block under the crosshair (client hit result), or null. */
	private @Nullable BlockPos crosshairBlock() {
		return context.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null);
	}

	private String blockName(BlockPos pos) {
		return onServer(p -> BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(pos).getBlock()).getPath());
	}

	/**
	 * Breadth-first search over standing spots (two free blocks over solid ground, steps of up to
	 * one block up or three down) from {@code from} to {@code to}, inside {@code bounds}. Returns
	 * the path length, or -1 when there is none. Server thread only.
	 */
	private static int walkableSteps(ServerLevel level, BlockPos from, BlockPos to, BoundingBox bounds, int limit) {
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		Map<BlockPos, Integer> seen = new java.util.HashMap<>();
		queue.add(from);
		seen.put(from, 0);

		while (!queue.isEmpty() && seen.size() < limit) {
			BlockPos cur = queue.poll();
			int d = seen.get(cur);

			if (cur.getX() == to.getX() && cur.getZ() == to.getZ() && Math.abs(cur.getY() - to.getY()) <= 1) {
				return d;
			}

			for (int[] dir : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
				for (int dy = 1; dy >= -3; dy--) {
					BlockPos next = cur.offset(dir[0], dy, dir[1]);

					if (!bounds.isInside(next) || seen.containsKey(next)) {
						continue;
					}

					boolean headroom = dy <= 0 || passable(level, cur.above(2));

					if (headroom && passable(level, next) && passable(level, next.above()) && !passable(level, next.below())) {
						seen.put(next, d + 1);
						queue.add(next);
						break;
					}

					if (!passable(level, next)) {
						break;
					}
				}
			}
		}

		return -1;
	}

	private static boolean passable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && !state.is(Blocks.LAVA);
	}

	private void setForward(boolean forward, boolean sprint) {
		if (forward != forwardHeld) {
			if (forward) {
				context.getInput().holdKey(FORWARD);
			} else {
				context.getInput().releaseKey(FORWARD);
				context.getInput().releaseKey(SPRINT);
			}

			forwardHeld = forward;
		}

		if (forward) {
			if (sprint) {
				context.getInput().holdKey(SPRINT);
			} else {
				context.getInput().releaseKey(SPRINT);
			}
		}
	}

	private void releaseAll() {
		try {
			setForward(false, false);
			context.getInput().releaseKey(SNEAK);
			context.getInput().releaseKey(USE);
			context.getInput().releaseKey(ATTACK);
			context.getInput().releaseKey(InputConstants.KEY_R);
		} catch (RuntimeException e) {
			LOG.warn("PLAYTHROUGH-LOG: could not release keys", e);
		}
	}

	/** One tick, tracking damage taken from the client's view of the player's health. */
	/**
	 * One jump: the jump key held over a tick. ({@code pressKey} presses and releases before the
	 * tick, which only counts a click; the jump reads whether the key is down.)
	 */
	private void jump(String step) {
		context.getInput().holdKey(JUMP);
		tickMe(step);
		context.getInput().releaseKey(JUMP);
	}

	private void tickMe(String step) {
		float before = context.computeOnClient(mc -> mc.player.getHealth());
		context.waitTick();
		ticks++;
		float after = context.computeOnClient(mc -> mc.player.getHealth());

		if (after < before) {
			damageTaken += before - after;
		}
	}

	/**
	 * Keep-alive for fights a bot cannot dodge: resistance and an instant heal when health is low, at
	 * most every 200 ticks, and at once whenever health falls below half the rescue line (a crowded
	 * phase, such as the Collector with Watchers, can kill inside the 200 ticks).
	 */
	private void keepAlive(String step) {
		float health = context.computeOnClient(mc -> mc.player.getHealth());

		if (health < RESCUE_HEALTH && (ticks - lastRescueTick > 200 || health < RESCUE_HEALTH / 2f)) {
			lastRescueTick = ticks;
			shortcut("keep-alive effects (resistance + instant health) at low health during " + step);
			server.runCommand("effect give @a minecraft:resistance 20 2 true");
			server.runCommand("effect give @a minecraft:instant_health 1 1 true");
		}
	}

	private Me me() {
		return context.computeOnClient(mc -> new Me(mc.player.position(), mc.player.getEyePosition(), mc.player.horizontalCollision, mc.player.onGround(),
				mc.player.getAttackStrengthScale(0f), mc.crosshairPickEntity == null ? -1 : mc.crosshairPickEntity.getId(), mc.player.getHealth(),
				mc.player.isDeadOrDying()));
	}

	private void aimAt(Vec3 point) {
		Vec3 eye = context.computeOnClient(mc -> mc.player.getEyePosition());
		double dx = point.x - eye.x;
		double dy = point.y - eye.y;
		double dz = point.z - eye.z;
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		context.getInput().lookAt(yaw, pitch);
	}

	private void aimFlat(Vec3 eye, Vec3 point) {
		float yaw = (float) (Mth.atan2(point.z - eye.z, point.x - eye.x) * Mth.RAD_TO_DEG) - 90f;
		context.getInput().lookAt(yaw, 15f);
	}

	private Target target(UUID id) {
		return onServer(p -> {
			Entity e = p.level().getEntity(id);
			return e == null || !e.isAlive() ? new Target(false, Vec3.ZERO, 0, -1) : new Target(true, e.position(), e.getBbHeight(), e.getId());
		});
	}

	private @Nullable Target nearest(List<UUID> ids) {
		Vec3 me = context.computeOnClient(mc -> mc.player.position());
		return ids.stream().map(this::target).filter(Target::alive).min(Comparator.comparingDouble(t -> t.pos().distanceToSqr(me))).orElse(null);
	}

	private boolean pushReady() {
		return onServer(p -> {
			ForceData d = Progression.get(p);
			return d.cooldown("force_push") == 0 && d.energy >= 25f;
		});
	}

	/** Live crystal poachers within {@code radius} of the player, nearest first. */
	/** The nearest poacher within 32 blocks that is showing its shot tell, or null. */
	private @Nullable UUID chargingPoacher() {
		return onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(32), e -> e instanceof CrystalPoacher poacher && poacher.isAlive()
				&& poacher.isCharging()).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::getUUID).orElse(null));
	}

	private List<UUID> poachersNear(double radius) {
		return onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(radius), e -> e instanceof CrystalPoacher && e.isAlive())
				.stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::getUUID).toList());
	}

	/** Walks over the item drops near the player. */
	private void collectDrops(String step, double radius) {
		Set<UUID> unreachable = new HashSet<>();

		for (int i = 0; i < 8; i++) {
			List<ItemEntity> items = onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(radius), e -> e instanceof ItemEntity && e.isAlive()
					&& !unreachable.contains(e.getUUID())).stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(e -> (ItemEntity) e).toList());

			if (items.isEmpty()) {
				return;
			}

			// One drop the bot cannot walk to (down the camp shaft, over a ledge) no longer ends the pickup for the rest.
			if (!walkTo(items.get(0).position(), 0.4, 160, false, null)) {
				unreachable.add(items.get(0).getUUID());
				continue;
			}

			context.waitTicks(12);
		}
	}

	/**
	 * Logs the poacher deaths and emitter drops since the last call and, when the poacher loot has
	 * spent its one emitter (the {@code got_emitter} flag) but the emitter is not in the inventory,
	 * goes and fetches it: a drop can land where {@link #collectDrops} cannot walk (down the camp
	 * shaft, over a ledge, further than its radius). A lost emitter is never dropped again, so
	 * without this the run is stuck however many camps it clears.
	 */
	private void recoverEmitter(String step) {
		List<String> events;

		synchronized (LOOT_EVENTS) {
			events = new ArrayList<>(LOOT_EVENTS);
			LOOT_EVENTS.clear();
		}

		if (!events.isEmpty()) {
			note(step, Result.INFO, "loot events: " + String.join("; ", events));
		}

		if (hasEmitter() || !data().hasFlag(PoacherLoot.EMITTER_FLAG)) {
			return;
		}

		for (int attempt = 0; attempt < 3 && !hasEmitter(); attempt++) {
			Vec3 me = context.computeOnClient(mc -> mc.player.position());
			Vec3 item = onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(64), e -> e instanceof ItemEntity i && i.isAlive() && isEmitter(i.getItem()))
					.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::position).orElse(null));

			if (item == null) {
				note(step, Result.FAIL, "got_emitter is set (a poacher dropped the emitter) but there is no emitter in the inventory or on the ground within 64 blocks"
						+ " of " + BlockPos.containing(me).toShortString() + "; the drop was lost and poachers never drop another");
				return;
			}

			String where = BlockPos.containing(item).toShortString() + String.format(Locale.ROOT, ", %.1f blocks away, %.1f blocks %s", item.distanceTo(me),
					Math.abs(item.y - me.y), item.y < me.y ? "down" : "up");
			note(step, Result.INFO, "the emitter dropped but was not picked up: it lies at " + where);

			if (attempt == 0 && walkTo(item, 0.4, 200, false, this::hasEmitter)) {
				context.waitTicks(12);
				continue;
			}

			if (!hasEmitter()) {
				shortcut("teleported onto a dropped emitter the bot could not walk to (" + step + ")");
				teleport(item.add(0, 0.1, 0), 0f);
				context.waitTicks(20);
			}
		}

		note(step, hasEmitter() ? Result.INFO : Result.FAIL, "emitter recovery: emitters in inventory " + (count(SaberItems.EMITTER_GUARDIAN) + count(SaberItems.EMITTER_DUELIST)));
	}

	private static boolean isEmitter(ItemStack stack) {
		return stack.is(SaberItems.EMITTER_GUARDIAN) || stack.is(SaberItems.EMITTER_DUELIST);
	}

	/** Records the blaster bolts near the player: seen, deflected, and deflected straight back at a poacher. */
	/**
	 * One hit for the guard log: damage type, damage taken, how far off the player's facing it came
	 * from (a bolt by its direction of travel, anything else by its source position) and whether
	 * the guard was up. The documented guard arc is 100 degrees, so up to 50 off should be blocked.
	 */
	private static String describeHit(ServerPlayer player, DamageSource source, float taken) {
		Vec3 from = source.getDirectEntity() instanceof BlasterBolt bolt ? bolt.getDeltaMovement().reverse()
				: source.getSourcePosition() == null ? null : source.getSourcePosition().subtract(player.position());
		String angle = "?";

		if (from != null && from.horizontal().lengthSqr() > 1.0e-6) {
			Vec3 facing = Vec3.directionFromRotation(0f, player.getYRot());
			double cos = Mth.clamp(facing.dot(from.horizontal().normalize()), -1.0, 1.0);
			angle = Math.round(Math.toDegrees(Math.acos(cos))) + " deg off facing";
		}

		return String.format(Locale.ROOT, "%s %.1f from %s, guard up=%s", source.getMsgId(), taken, angle, !SaberGuard.guardingSaber(player).isEmpty());
	}

	private void trackBolts(Set<UUID> seen, Set<UUID> deflected, Set<UUID> returned) {
		List<Bolt> bolts = onServer(p -> {
			List<Bolt> out = new ArrayList<>();

			for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(32), x -> x instanceof BlasterBolt)) {
				BlasterBolt bolt = (BlasterBolt) e;
				boolean towardPoacher = false;

				if (bolt.isDeflected()) {
					Vec3 velocity = bolt.getDeltaMovement();

					for (Entity poacher : p.level().getEntities((Entity) null, bolt.getBoundingBox().inflate(24), x -> x instanceof CrystalPoacher && x.isAlive())) {
						Vec3 to = poacher.getEyePosition().subtract(bolt.position());

						if (velocity.lengthSqr() > 1.0e-4 && to.lengthSqr() > 1.0e-4 && velocity.normalize().dot(to.normalize()) > 0.97) {
							towardPoacher = true;
						}
					}
				}

				out.add(new Bolt(bolt.getUUID(), bolt.isDeflected(), towardPoacher));
			}

			return out;
		});

		for (Bolt bolt : bolts) {
			seen.add(bolt.id());

			if (bolt.deflected() && deflected.add(bolt.id()) && bolt.towardPoacher()) {
				returned.add(bolt.id());
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// World, items and screens
	// ---------------------------------------------------------------------------------------------

	/** Summons three slowed husks {@code dz} blocks south (or north) of the player and returns their ids. */
	private List<UUID> summonTargets(int dz) {
		BlockPos feet = onServer(p -> p.blockPosition());
		Set<UUID> before = new HashSet<>(husksNear());

		for (int i = -1; i <= 1; i++) {
			int x = feet.getX() + i;
			int z = feet.getZ() + dz;
			int y = surfaceY(x, z);
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {PersistenceRequired:1b,Rotation:[180f,0f],"
					+ "active_effects:[{id:\"minecraft:slowness\",amplifier:3,duration:-1,show_particles:0b}]}", x, y, z));
		}

		context.waitTicks(2);
		return husksNear().stream().filter(id -> !before.contains(id)).toList();
	}

	/** Server-side motion of one entity in one game tick. */
	private record Motion(long time, Vec3 pos, Vec3 velocity, boolean onGround, boolean collided) {
	}

	private Map<UUID, Motion> huskMotion(List<UUID> ids) {
		return onServer(p -> {
			Map<UUID, Motion> result = new LinkedHashMap<>();
			long time = p.level().getGameTime();

			for (UUID id : ids) {
				Entity e = p.level().getEntity(id);

				if (e != null && e.isAlive()) {
					result.put(id, new Motion(time, e.position(), e.getDeltaMovement(), e.onGround(), e.horizontalCollision));
				}
			}

			return result;
		});
	}

	/**
	 * Finds the impulse tick (first sample whose horizontal speed exceeds 0.3 blocks/tick) and
	 * reports velocity and displacement from the pre-release position at +0, +1, +2, +4 and +10.
	 */
	private static String describeMotion(Vec3 start, List<Motion> samples) {
		int k = -1;

		for (int i = 0; i < samples.size() && k < 0; i++) {
			if (samples.get(i).velocity().horizontalDistance() > 0.3) {
				k = i;
			}
		}

		if (k < 0) {
			double max = samples.stream().mapToDouble(m -> m.velocity().horizontalDistance()).max().orElse(0);
			Motion last = samples.get(samples.size() - 1);
			return String.format(Locale.ROOT, "no impulse seen (max horizontal speed %.2f), moved %.2f in %d ticks", max,
					last.pos().subtract(start).horizontalDistance(), samples.size());
		}

		List<String> parts = new ArrayList<>();

		for (int off : new int[] { 0, 1, 2, 4, 10 }) {
			int i = k + off;

			if (i < samples.size()) {
				Motion m = samples.get(i);
				parts.add(String.format(Locale.ROOT, "+%d: v(%.2f, %.2f, %.2f) moved %.2f%s%s", off, m.velocity().x, m.velocity().y, m.velocity().z,
						m.pos().subtract(start).horizontalDistance(), m.onGround() ? " ground" : " air", m.collided() ? " COLLIDED" : ""));
			}
		}

		Motion last = samples.get(samples.size() - 1);
		return "impulse seen " + k + " tick(s) after release (game tick " + samples.get(k).time() + "): " + String.join("; ", parts)
				+ String.format(Locale.ROOT, "; final %.2f", last.pos().subtract(start).horizontalDistance());
	}

	/** Server positions of the live entities among {@code ids}. */
	private Map<UUID, Vec3> huskPositions(List<UUID> ids) {
		return onServer(p -> {
			Map<UUID, Vec3> positions = new LinkedHashMap<>();

			for (UUID id : ids) {
				Entity e = p.level().getEntity(id);

				if (e != null && e.isAlive()) {
					positions.put(id, e.position());
				}
			}

			return positions;
		});
	}

	private static String huskDisplacement(Map<UUID, Vec3> before, Map<UUID, Vec3> after) {
		List<String> parts = new ArrayList<>();
		int i = 0;

		for (Map.Entry<UUID, Vec3> entry : before.entrySet()) {
			i++;
			Vec3 now = after.get(entry.getKey());
			parts.add("#" + i + " " + (now == null ? "gone" : String.format(Locale.ROOT, "%.2f", now.subtract(entry.getValue()).horizontalDistance())));
		}

		return parts.isEmpty() ? "(no husks)" : String.join(", ", parts);
	}

	private List<UUID> husksNear() {
		return onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(12),
				e -> e.isAlive() && "husk".equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())).stream().map(Entity::getUUID).toList());
	}

	/**
	 * Places a block item the way a player does: picks it in the hotbar, looks at the top of a free
	 * ground block two blocks away and right-clicks. Returns the placed position or null.
	 */
	private @Nullable BlockPos placeBlock(Item item, Predicate<BlockState> placed, boolean needsLength) {
		if (!select(item)) {
			return null;
		}

		BlockPos ground = onServer(p -> {
			ServerLevel level = p.level();
			BlockPos feet = p.blockPosition();
			int[][] dirs = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };

			for (int distance = 2; distance <= 3; distance++) {
				for (int[] d : dirs) {
					for (int dy = 1; dy >= -2; dy--) {
						BlockPos g = feet.offset(d[0] * distance, dy - 1, d[1] * distance);
						BlockPos above = g.above();
						// API-CHECK: Level#getFluidState + FluidState#isEmpty (standard, not in the probe dumps).
						boolean solid = !level.getBlockState(g).getCollisionShape(level, g).isEmpty() && level.getFluidState(g).isEmpty();
						boolean free = level.getBlockState(above).getCollisionShape(level, above).isEmpty()
								&& level.getBlockState(above.above()).getCollisionShape(level, above.above()).isEmpty()
								&& level.getFluidState(above).isEmpty();
						BlockPos head = above.offset(d[0], 0, d[1]);
						boolean headFree = !needsLength || (level.getBlockState(head).getCollisionShape(level, head).isEmpty()
								&& !level.getBlockState(head.below()).getCollisionShape(level, head.below()).isEmpty());

						if (solid && free && headFree) {
							return g;
						}

						if (solid) {
							break;
						}
					}
				}
			}

			return null;
		});

		if (ground == null) {
			return null;
		}

		Vec3 top = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
		aimAt(top);
		context.waitTick();

		// Grass and flowers in the line of sight catch the click, and a right-click on a replaceable
		// plant places the block in the plant's spot (possibly the upper half of tall grass, in the
		// air). Clear them away first, as a player would.
		for (int i = 0; i < 4; i++) {
			BlockPos hit = crosshairBlock();

			if (hit == null || hit.equals(ground) || !onServer(p -> p.level().getBlockState(hit).canBeReplaced())) {
				break;
			}

			context.getInput().pressKey(ATTACK);
			context.waitTicks(6);
			aimAt(top);
			context.waitTick();
		}

		Set<BlockPos> before = matchingBlocksNear(placed, 6);
		context.getInput().pressKey(USE);
		BlockPos at = ground.above();

		if (waitServer(10, p -> placed.test(p.level().getBlockState(at)))) {
			return at;
		}

		// Placed somewhere else after all (the click caught something else): use it where it is.
		Set<BlockPos> after = matchingBlocksNear(placed, 6);
		after.removeAll(before);
		return after.isEmpty() ? null : after.iterator().next();
	}

	/** Positions within {@code radius} of the player whose block matches {@code test}. */
	private Set<BlockPos> matchingBlocksNear(Predicate<BlockState> test, int radius) {
		return onServer(p -> {
			Set<BlockPos> found = new HashSet<>();

			for (BlockPos pos : BlockPos.betweenClosed(p.blockPosition().offset(-radius, -radius, -radius), p.blockPosition().offset(radius, radius, radius))) {
				if (test.test(p.level().getBlockState(pos))) {
					found.add(pos.immutable());
				}
			}

			return found;
		});
	}

	/** Right-clicks a block and waits until {@code opened} holds on the client. */
	private boolean openBlock(BlockPos pos, Predicate<Minecraft> opened) {
		aimAt(Vec3.atCenterOf(pos).add(0, 0.45, 0));
		context.waitTick();
		context.getInput().pressKey(USE);
		return waitClient(40, opened);
	}

	/**
	 * Crafts one item at the open crafting table by clicking: picks up each ingredient stack, right
	 * clicks one into the grid cell, puts the rest back, then shift-clicks the result.
	 */
	private boolean craft(String name, String[] pattern, Map<Character, Item> key, Item result) {
		int before = count(result);
		boolean placedAll = context.computeOnClient(mc -> {
			if (!(mc.player.containerMenu instanceof CraftingMenu menu)) {
				return false;
			}

			for (int row = 0; row < pattern.length; row++) {
				for (int col = 0; col < pattern[row].length(); col++) {
					Item ingredient = key.get(pattern[row].charAt(col));

					if (ingredient == null) {
						continue;
					}

					int source = -1;

					for (int slot = 10; slot < menu.slots.size(); slot++) {
						if (menu.slots.get(slot).getItem().is(ingredient)) {
							source = slot;
							break;
						}
					}

					if (source < 0) {
						return false;
					}

					int cell = 1 + row * 3 + col;
					mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.PICKUP, mc.player);
					mc.gameMode.handleContainerInput(menu.containerId, cell, 1, ContainerInput.PICKUP, mc.player);
					mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.PICKUP, mc.player);
				}
			}

			return true;
		});
		context.waitTicks(4);

		if (placedAll) {
			context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 0, ContainerInput.QUICK_MOVE, mc.player));
			context.waitTicks(4);
		}

		boolean crafted = count(result) > before;
		note("07 crafting", crafted ? Result.INFO : Result.FAIL, name + ": ingredients placed=" + placedAll + ", crafted=" + crafted);
		return crafted;
	}

	/** Closes the open screen with Escape, as a player would (so the server closes the menu too). */
	private void closeScreen() {
		if (context.computeOnClient(mc -> mc.gui.screen() != null)) {
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(3);
		}

		if (context.computeOnClient(mc -> mc.gui.screen() != null)) {
			context.setScreen(() -> null);
		}
	}

	/**
	 * Puts {@code item} in the main hand: a hotbar number key if it is in the hotbar, else a swap
	 * into hotbar slot 9 through the inventory screen first.
	 */
	private boolean select(Item item) {
		int slot = onServer(p -> {
			for (int i = 0; i < 36; i++) {
				if (p.getInventory().getItem(i).is(item)) {
					return i;
				}
			}

			return -1;
		});

		if (slot < 0) {
			return false;
		}

		if (slot >= 9) {
			context.getInput().pressKey(INVENTORY);
			waitClient(20, mc -> mc.gui.screen() != null);
			int from = slot;
			context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, from, 8, ContainerInput.SWAP, mc.player));
			context.waitTicks(3);
			closeScreen();
			slot = 8;
		}

		int hotbar = slot;
		context.getInput().pressKey(o -> o.keyHotbarSlots[hotbar]);
		return waitServer(10, p -> p.getMainHandItem().is(item));
	}

	/** Removes stray vanilla hostiles near the player so they do not interfere with a scripted step. */
	private void clearInterference(String step) {
		int removed = onServer(p -> {
			int n = 0;

			for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(24),
					x -> x instanceof Enemy && x.isAlive() && !(x instanceof CrystalPoacher) && !(x instanceof ShadowEchoEntity))) {
				e.discard();
				n++;
			}

			return n;
		});

		if (removed > 0) {
			shortcut("removed stray hostile mobs that wandered in (" + step + ")");
		}
	}

	/** Searches for a crystal cave far from the ones already visited. */
	private @Nullable BlockPos findOtherCave() {
		BlockPos origin = knownCaves.get(knownCaves.size() - 1);
		int[][] offsets = { { 700, 0 }, { -700, 0 }, { 0, 700 }, { 0, -700 }, { 700, 700 }, { -700, -700 } };

		for (int[] offset : offsets) {
			BlockPos found = onServer(p -> p.level().findNearestMapStructure(CaveLocator.CRYSTAL_CAVES, origin.offset(offset[0], 0, offset[1]), 6, false));

			if (found != null && knownCaves.stream().allMatch(known -> known.distSqr(found) > 200 * 200)) {
				return found;
			}
		}

		return null;
	}

	/** Goes to a camp that still has live poachers: the last one visited, else a new cave. */
	private boolean findLivePoachers(String step) {
		if (lastCamp != null) {
			Vec3 camp = lastCamp;
			int x = Mth.floor(camp.x);
			int z = Mth.floor(camp.z) + 24;
			int y = surfaceY(x, z);
			shortcut("teleported back towards the last poacher camp for the saber test");
			server.runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 180 0", x, y, z));
			waitForChunks(step);
			context.waitTicks(20);

			if (!poachersNear(64).isEmpty()) {
				return true;
			}
		}

		for (int tries = 0; tries < 2; tries++) {
			BlockPos other = findOtherCave();

			if (other == null) {
				return false;
			}

			knownCaves.add(other);

			if (approachCamp(step, other) && !poachersNear(48).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/** Locates the heart piece from the locator result, the meditation stone and the seal blocks. */
	private @Nullable Heart findHeart() {
		return onServer(p -> {
			BlockPos located = CaveLocator.findNearestCaveNow(p);

			if (located == null) {
				return null;
			}

			ServerLevel level = p.level();
			BlockPos stone = located;
			boolean hit = level.getBlockState(located).is(WorldBlocks.MEDITATION_STONE);

			if (!hit) {
				BlockPos found = TrialManager.findStone(level, located, 48);

				if (found == null) {
					return null;
				}

				stone = found;
			}

			long sx = 0;
			long sz = 0;
			int n = 0;

			for (BlockPos pos : BlockPos.betweenClosed(stone.offset(-12, -1, -12), stone.offset(12, 4, 12))) {
				if (level.getBlockState(pos).is(WorldBlocks.RESONANT_SEAL)) {
					sx += pos.getX();
					sz += pos.getZ();
					n++;
				}
			}

			if (n == 0) {
				return new Heart(stone, 0, 0, hit, 0);
			}

			double vx = stone.getX() - (double) sx / n;
			double vz = stone.getZ() - (double) sz / n;
			int dx = Math.abs(vx) > Math.abs(vz) ? (int) Math.signum(vx) : 0;
			int dz = Math.abs(vz) >= Math.abs(vx) ? (int) Math.signum(vz) : 0;
			return new Heart(stone, dx, dz, hit, n);
		});
	}

	/**
	 * What the heart lookup saw, for a stuck report: the locator's answer and the block there, then
	 * every piece of that cave with its placement, and for the heart piece the mod blocks and cave
	 * air standing in its box (none means its template was never placed).
	 */
	private String heartDiagnostics() {
		return onServer(p -> {
			ServerLevel level = p.level();
			BlockPos located = CaveLocator.findNearestCaveNow(p);

			if (located == null) {
				return "the locator found no cave from " + p.blockPosition().toShortString();
			}

			StringBuilder out = new StringBuilder("locator answer " + located.toShortString() + " holds "
					+ BuiltInRegistries.BLOCK.getKey(level.getBlockState(located).getBlock()).getPath());
			StructureStart start = caveStartAt(level, located);

			if (start == null) {
				return out.append(", no cave structure there").toString();
			}

			for (StructurePiece piece : start.getPieces()) {
				Identifier template = templateOf(piece);
				out.append("; piece ").append(template == null ? piece.getClass().getSimpleName() : template.getPath()).append(' ').append(box(piece.getBoundingBox()));

				if (piece instanceof PoolElementStructurePiece pool) {
					out.append(" at ").append(pool.getPosition().toShortString()).append(' ').append(pool.getRotation());
				}

				if (CaveLocator.HEART_TEMPLATE.equals(template)) {
					Map<String, Integer> counts = new LinkedHashMap<>();
					BoundingBox b = piece.getBoundingBox();

					for (BlockPos pos : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
						Identifier id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());

						if (!"minecraft".equals(id.getNamespace()) || "cave_air".equals(id.getPath())) {
							counts.merge(id.getPath(), 1, Integer::sum);
						}
					}

					out.append(" holds ").append(counts.isEmpty() ? "no mod blocks or cave air" : counts.toString());
				}
			}

			return out.toString();
		});
	}

	/** Piece bounds of one crystal cave structure, read on the server. */
	private record CaveInfo(BoundingBox structure, @Nullable BoundingBox camp, @Nullable BoundingBox descent, @Nullable BoundingBox heart, int pieces) {
		@Nullable String pieceContaining(BlockPos pos) {
			if (camp != null && camp.isInside(pos)) {
				return "camp";
			}

			if (descent != null && descent.isInside(pos)) {
				return "descent";
			}

			return heart != null && heart.isInside(pos) ? "heart" : null;
		}
	}

	/**
	 * The crystal cave structure with a piece at {@code pos}, or else one whose start is in the
	 * chunk of {@code pos} (the locator's start position). Server thread only.
	 */
	private static @Nullable StructureStart caveStartAt(ServerLevel level, BlockPos pos) {
		level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, CaveLocator.CRYSTAL_CAVES);

		if (start != null && start.isValid()) {
			return start;
		}

		ChunkAccess chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true);

		for (StructureStart candidate : chunk.getAllStarts().values()) {
			if (candidate != null && candidate.isValid() && candidate.getPieces().stream().anyMatch(piece -> CaveLocator.CAMP_TEMPLATE.equals(templateOf(piece)))) {
				return candidate;
			}
		}

		return null;
	}

	private static @Nullable Identifier templateOf(StructurePiece piece) {
		return piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single ? single.getTemplateLocation() : null;
	}

	/** Bounds of the cave structure around {@code pos} (see {@link #caveStartAt}), or null. */
	private @Nullable CaveInfo caveInfo(BlockPos pos) {
		return onServer(p -> {
			StructureStart start = caveStartAt(p.level(), pos);

			if (start == null) {
				return null;
			}

			BoundingBox camp = null;
			BoundingBox descent = null;
			BoundingBox heartBox = null;

			for (StructurePiece piece : start.getPieces()) {
				Identifier template = templateOf(piece);

				if (CaveLocator.CAMP_TEMPLATE.equals(template)) {
					camp = piece.getBoundingBox();
				} else if (CaveLocator.DESCENT_TEMPLATE.equals(template)) {
					descent = piece.getBoundingBox();
				} else if (CaveLocator.HEART_TEMPLATE.equals(template)) {
					heartBox = piece.getBoundingBox();
				}
			}

			return new CaveInfo(start.getBoundingBox(), camp, descent, heartBox, start.getPieces().size());
		});
	}

	private static String box(@Nullable BoundingBox b) {
		return b == null ? "none" : "[" + b.minX() + " " + b.minY() + " " + b.minZ() + " .. " + b.maxX() + " " + b.maxY() + " " + b.maxZ() + "]";
	}

	/** Centre of the camp yard (one block above the camp floor, see gen_structures.py). */
	private static Vec3 campYard(BoundingBox camp) {
		return new Vec3((camp.minX() + camp.maxX() + 1) / 2.0, camp.minY() + 5, (camp.minZ() + camp.maxZ() + 1) / 2.0);
	}

	/**
	 * Logs where the player is: quest stage, {@code CaveLocator.pieceAt} at the player's block, and
	 * the bounds of the nearest crystal cave structure with which piece box (checked directly)
	 * contains the player, so a disagreement with pieceAt shows up.
	 */
	private void logWhere(String step, String when) {
		try {
			BlockPos ref = heart != null ? heart.stone() : knownCaves.isEmpty() ? null : knownCaves.get(0);
			BlockPos cave = ref != null ? ref
					: onServer(p -> p.level().findNearestMapStructure(CaveLocator.CRYSTAL_CAVES, p.blockPosition(), 6, false));
			CaveInfo info = cave == null ? null : caveInfo(cave);
			String where = onServer(p -> {
				BlockPos pos = p.blockPosition();
				Identifier piece = CaveLocator.pieceAt(p.level(), pos);
				return "stage " + stage(Progression.get(p)) + ", player " + pos.toShortString() + ", pieceAt=" + (piece == null ? "none" : piece.getPath())
						+ ", isInsideCave=" + CaveLocator.isInsideCave(p);
			});
			BlockPos me = onServer(p -> p.blockPosition());
			String structure = info == null ? "no cave structure found" + (cave == null ? "" : " at " + cave.toShortString())
					: "cave " + box(info.structure()) + " (" + info.pieces() + " pieces): camp " + box(info.camp()) + ", descent " + box(info.descent())
							+ ", heart " + box(info.heart()) + ", piece box containing the player=" + info.pieceContaining(me);
			note(step, Result.INFO, "where (" + when + "): " + where + "; " + structure);
		} catch (RuntimeException e) {
			note(step, Result.INFO, "where (" + when + "): could not inspect: " + e);
		}
	}

	/** First free block above the surface at x, z; loads (and generates) the chunk first. */
	private int surfaceY(int x, int z) {
		return onServer(p -> {
			// Level#getHeight answers the minimum height for chunks that are not loaded.
			p.level().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
			return p.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		});
	}

	/** Whether the resonant seal block at {@code pos} is open (false if it is not a seal at all). */
	private boolean sealOpen(BlockPos pos) {
		return onServer(p -> {
			BlockState state = p.level().getBlockState(pos);
			return state.is(WorldBlocks.RESONANT_SEAL) && state.getValue(ResonantSealBlock.OPEN);
		});
	}

	private void waitForChunks(String step) {
		if (context.computeOnClient(mc -> mc.player == null || mc.player.isDeadOrDying())) {
			note(step, Result.INFO, "not waiting for chunks: the player is dead");
			return;
		}

		try {
			singleplayer.getConnection().waitForChunksRender(true, 2400);
		} catch (AssertionError e) {
			note(step, Result.INFO, "chunks did not finish rendering within 2400 ticks: " + e.getMessage());
		}
	}

	private int count(Item item) {
		return onServer(p -> {
			int n = 0;

			for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
				ItemStack stack = p.getInventory().getItem(slot);

				if (stack.is(item)) {
					n += stack.getCount();
				}
			}

			return n;
		});
	}

	private boolean hasEmitter() {
		return count(SaberItems.EMITTER_GUARDIAN) + count(SaberItems.EMITTER_DUELIST) > 0;
	}

	private String partsSummary() {
		return "emitters " + (count(SaberItems.EMITTER_GUARDIAN) + count(SaberItems.EMITTER_DUELIST))
				+ ", grips " + (count(SaberItems.GRIP_WRAPPED) + count(SaberItems.GRIP_RIDGED))
				+ ", pommels " + (count(SaberItems.POMMEL_WEIGHTED) + count(SaberItems.POMMEL_BALANCED))
				+ ", iron nuggets " + count(Items.IRON_NUGGET);
	}

	private ForceData data() {
		return onServer(p -> Progression.get(p).copy());
	}

	private static QuestStage stage(ForceData data) {
		return QuestStage.of(data.questStage);
	}

	private <T> T onServer(Function<ServerPlayer, T> function) {
		return server.computeOnServer(s -> function.apply(s.getPlayerList().getPlayers().getFirst()));
	}

	/** Polls a server-side condition once per tick for up to {@code timeout} ticks. */
	private boolean waitServer(int timeout, Predicate<ServerPlayer> condition) {
		for (int t = 0; t < timeout; t++) {
			if (onServer(condition::test)) {
				return true;
			}

			tickMe("wait");
		}

		return onServer(condition::test);
	}

	/** Polls a client-side condition once per tick for up to {@code timeout} ticks. */
	private boolean waitClient(int timeout, Predicate<Minecraft> condition) {
		for (int t = 0; t < timeout; t++) {
			if (context.computeOnClient(condition::test)) {
				return true;
			}

			context.waitTick();
		}

		return context.computeOnClient(condition::test);
	}

	// ---------------------------------------------------------------------------------------------
	// Recording
	// ---------------------------------------------------------------------------------------------

	private void shot(String step) {
		context.takeScreenshot(String.format(Locale.ROOT, "play_%02d_%s", ++screenshots, step));
	}

	private void check(String step, boolean ok, String details) {
		note(step, ok ? Result.PASS : Result.FAIL, details);
	}

	private void note(String step, Result result, String details) {
		entries.add(new Entry(step, result, details));
		LOG.info("PLAYTHROUGH-LOG: [{}] {}: {}", result, step, details);
	}

	private void shortcut(String what) {
		if (shortcuts.merge(what, 1, Integer::sum) == 1) {
			LOG.info("PLAYTHROUGH-LOG: SHORTCUT: {}", what);
		}
	}

	private void stuck(String step, String details) {
		stuck = true;
		note(step, Result.STUCK, details);
		logWhere(step, "stuck");
		shot("stuck");
		throw new Stuck(step + ": " + details);
	}

	private void printSummary() {
		long pass = entries.stream().filter(e -> e.result() == Result.PASS).count();
		long fail = entries.stream().filter(e -> e.result() == Result.FAIL).count();
		LOG.info("PLAYTHROUGH: ============ Chapter One to Five playthrough ============");
		LOG.info("PLAYTHROUGH: seed '{}', {} pass, {} fail, stuck={}", SEED, pass, fail, stuck);

		for (Entry entry : entries) {
			if (entry.result() != Result.INFO) {
				LOG.info("PLAYTHROUGH: {} | {} | {}", entry.result(), entry.step(), entry.details());
			}
		}

		LOG.info("PLAYTHROUGH: ---- details ----");

		for (Entry entry : entries) {
			if (entry.result() == Result.INFO) {
				LOG.info("PLAYTHROUGH: INFO | {} | {}", entry.step(), entry.details());
			}
		}

		LOG.info("PLAYTHROUGH: ---- shortcuts ----");

		for (Map.Entry<String, Integer> shortcut : shortcuts.entrySet()) {
			LOG.info("PLAYTHROUGH: SHORTCUT: {}{}", shortcut.getKey(), shortcut.getValue() > 1 ? " (x" + shortcut.getValue() + ")" : "");
		}

		LOG.info("PLAYTHROUGH: ==================================================================");
	}
}
