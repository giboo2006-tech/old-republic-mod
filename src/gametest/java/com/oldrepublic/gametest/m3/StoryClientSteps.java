package com.oldrepublic.gametest.m3;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.identity.StandingPanel;
import com.oldrepublic.client.quest.JournalScreen;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mission.Missions;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.ChapterThreeService;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.PathStage;
import com.oldrepublic.quest.story.RankThree;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3 client test steps for the story feature (Chapter Three, missions, reputation and
 * ranks). {@code ExplorationClientTest} runs them in the shared seeded exploration world (spec
 * 17.2). Screenshots are {@code oldrepublic_m3_<nn>_<what>} with {@code nn} from 20 to 29:
 *
 * <ul>
 * <li>20 {@code chapter3_journal}: a Jedi at LEADS with three of four leads, the journal's Chapter
 * Three page with the lead list ticked;</li>
 * <li>21 {@code board}: the Republic board (the {@code mission board} test aid, no terminal needed);</li>
 * <li>22 {@code missions_tab}: the journal's Missions tab with the {@code clear_camp} contract active;</li>
 * <li>23 to 25 {@code tracker_far|near|here}: the mission tracker with the player put about 210,
 * 100 and 28 blocks from the contract's camp (FAR above 160, NEAR 48 to 160, HERE below 48);</li>
 * <li>26 {@code officer_wary_amends}: a Republic officer's dialogue at Wary (Distrusted), showing
 * "Make amends";</li>
 * <li>27 {@code standing_tab}: the Standing panel's factions page with its four rows;</li>
 * <li>28 {@code rank3_banner}: the rank 3 banner over the Knighting's blade-light, in third person;</li>
 * <li>29 {@code pathfinder_fall}: Courier's Fall placed ahead of the player, Tessaly Arn's figure
 * rising from the Compass and the {@code pathfinder} dialogue.</li>
 * </ul>
 *
 * <p>Shortcuts, each logged: the story, identity and reputation state is set directly on the
 * server (no Chapter Three placements run, and the counter-hunt and extra-shrine flags are set so
 * nothing spawns or is placed behind the screenshots); the rank 3 and Pathfinder moments are the
 * ceremony's own effect events and dialogue, not a full rank 3 run (its requirements span the
 * whole milestone and are covered by {@code StoryM3GameTests}). Afterwards the contract is
 * abandoned, mission spawns and the officer are removed, the story and identity data are put back
 * and the player returns to where they stood. Courier's Fall and the contract's camp stay in the
 * world (logged).
 */
public final class StoryClientSteps {
	private static final List<String> LEADS = List.of("holo:jedi_morrane", "mission:r1_silent_patrol", ChapterThreeRules.LEAD_LEDGER);
	/** A contract with a runtime camp 300 to 600 blocks out (far placement, docs/MILESTONE_6.md 3.1), so the tracker has a target. */
	private static final String CONTRACT = "clear_camp";
	/** The camp is placed by the mission tick: polls of 20 ticks before the tracker shots go ahead without it. */
	private static final int TARGET_POLLS = 150;
	/** Distances from the target for the three tracker bands, and the spread allowed around each point. */
	private static final int FAR = 210;
	private static final int NEAR = 100;
	private static final int HERE = 28;
	private static final int SPREAD = 6;
	/** Ticks after a move before a tracker shot: chunks render and a few tracker hints arrive. */
	private static final int TRACKER_SETTLE = 70;
	/** Reputation for "Wary" (Distrusted, -100 to -499). */
	private static final int WARY_REP = -200;
	private static final Identifier OFFICER_REPUBLIC = OldRepublicMod.id("officer_republic");
	private static final Identifier COURIERS_FALL = OldRepublicMod.id("story/couriers_fall");
	/** Courier's Fall's lowest layers set into the ground, as the story places it. */
	private static final int FALL_BURIED = 2;
	/** Courier's Fall is placed this many blocks ahead of the player. */
	private static final int FALL_AHEAD = 32;
	/** Ticks to wait for a screen to open before going on without it (the miss is logged as an error). */
	private static final int SCREEN_TIMEOUT = 100;

	/** What the steps change, put back at the end. */
	private record Snapshot(StoryData story, IdentityData identity, double x, double y, double z, float yRot, float xRot) {
		static Snapshot of(ServerPlayer p) {
			return new Snapshot(Story.get(p).copy(), Identity.get(p).copy(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
		}

		void restore(ServerPlayer p) {
			Story.commit(p, story);
			Identity.commit(p, identity);
		}

		String teleport() {
			return String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.1f %.1f", x, y, z, yRot, xRot);
		}
	}

	/** A bare screen hosting the Standing panel on one page, the way the Force screen hosts it. */
	private static final class StandingHost extends Screen {
		private final StandingPanel panel = new StandingPanel();

		StandingHost(String page) {
			super(Component.literal("Standing"));
			panel.open(page);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int w = Math.min(width - 16, 440);
			int h = Math.min(height - 16, 230);
			panel.render(graphics, font, (width - w) / 2, (height - h) / 2, w, h, partialTick);
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}

	/** Runs the story steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		Snapshot before = server.computeOnServer(s -> Snapshot.of(player(s)));

		try {
			setUp(context, server);
			journal(context);
			board(context, server);
			missionsTab(context, server);
			tracker(context, server);
			server.runCommand(before.teleport());
			context.waitTicks(40);
			officer(context, server);
			standing(context, server);
			rankThreeBanner(context, server);
			pathfinder(context, server);
		} finally {
			context.setScreen(() -> null);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			if (server.computeOnServer(s -> Missions.activeId(player(s)) != null)) {
				server.runCommand("oldrepublic mission abandon @p");
			}

			server.runCommand("kill @e[tag=oldrepublic.mission]");
			server.runOnServer(s -> before.restore(player(s)));
			server.runCommand(before.teleport());
			OldRepublicMod.LOGGER.info("Story client steps: state restored; Courier's Fall and the {} camp stay in the world", CONTRACT);
			context.waitTicks(20);
		}
	}

	/**
	 * A Jedi at rank 2 whose Chapter Two is complete, introduced, Neutral with the Republic, at
	 * Chapter Three LEADS with three leads. The counter-hunt (3 leads) and the extra Echo Shrine are
	 * marked as done, so nothing spawns or is placed while the steps run.
	 */
	private static void setUp(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			IdentityData identity = Identity.get(p);
			identity.path = Path.JEDI;
			identity.rank = 2;
			identity.choices.add(Identity.INTRODUCED);
			Identity.commit(p, identity);
			Identity.setReputation(p, Faction.REPUBLIC, 0);

			StoryData story = Story.get(p);
			story.path = Path.JEDI.id();
			story.pathStage = PathStage.COMPLETE.ordinal();
			story.flags.add(ChapterThreeRules.AMBUSH);
			story.flags.add(ChapterThreeRules.EXTRA_SHRINE_PREFIX + "jedi");
			Story.commit(p, story);
			ChapterThreeService.enterStage(p, ChapterThreeStage.LEADS);
			LEADS.forEach(lead -> ChapterThreeService.addLead(p, lead));
			OldRepublicMod.LOGGER.info("Story client step: set {} to a rank 2 Jedi at Chapter Three LEADS with leads {} (shortcut)",
					p.getName().getString(), Story.get(p).chapter3.leads());
		});
		context.waitTicks(20);
	}

	/** 20: the Chapter Three page: the objective with 3/4 leads, the three leads ticked and one open. */
	private static void journal(ClientGameTestContext context) {
		context.runOnClient(mc -> JournalScreen.openOnTab("journal"));
		waitForScreen(context, "JournalScreen");
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m3_20_chapter3_journal");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	/** 21: the Republic board's two offer cards, through the {@code mission board} test aid. */
	private static void board(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("oldrepublic mission board @p republic");
		waitForScreen(context, "BoardScreen");
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m3_21_board");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	/** 22: the Missions tab with the contract just accepted. */
	private static void missionsTab(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("oldrepublic mission start @p " + CONTRACT);
		context.waitTicks(20);
		context.runOnClient(mc -> JournalScreen.openOnTab("missions"));
		waitForScreen(context, "JournalScreen");
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m3_22_missions_tab");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	/**
	 * 23 to 25: waits for the contract's camp to be placed, then puts the player on the line from
	 * the camp through where they stand, at each band's distance, facing the camp.
	 */
	private static void tracker(ClientGameTestContext context, TestServerContext server) {
		Vec3 target = null;

		for (int i = 0; i < TARGET_POLLS && target == null; i++) {
			context.waitTicks(20);
			target = server.computeOnServer(s -> Missions.target(player(s)));
		}

		if (target == null) {
			OldRepublicMod.LOGGER.error("Story client step: the {} camp was not placed; the tracker shots show the mission without a direction", CONTRACT);
		}

		Vec3 from = server.computeOnServer(s -> player(s).position());
		trackerShot(context, server, target, from, FAR, "oldrepublic_m3_23_tracker_far");
		trackerShot(context, server, target, from, NEAR, "oldrepublic_m3_24_tracker_near");
		trackerShot(context, server, target, from, HERE, "oldrepublic_m3_25_tracker_here");
	}

	private static void trackerShot(ClientGameTestContext context, TestServerContext server, @Nullable Vec3 target, Vec3 from, int distance, String name) {
		if (target != null) {
			double dx = from.x - target.x;
			double dz = from.z - target.z;
			double length = Math.sqrt(dx * dx + dz * dz);
			double ux = length < 1 ? 0 : dx / length;
			double uz = length < 1 ? 1 : dz / length;
			double x = target.x + ux * distance;
			double z = target.z + uz * distance;
			// spreadplayers stands the player on the top block (never in a liquid) within SPREAD of the point.
			server.runCommand(String.format(Locale.ROOT, "spreadplayers %.1f %.1f 0 %d false @p", x, z, SPREAD));
			server.runCommand(String.format(Locale.ROOT, "execute as @p at @s run tp @s ~ ~ ~ facing %.1f %.1f %.1f", target.x, target.y, target.z));
		}

		context.waitTicks(TRACKER_SETTLE);

		if (target != null) {
			double away = server.computeOnServer(s -> {
				Vec3 at = player(s).position();
				return Math.sqrt(Math.pow(at.x - target.x, 2) + Math.pow(at.z - target.z, 2));
			});
			OldRepublicMod.LOGGER.info("Story client step: {} taken {} blocks from the camp (asked {})", name, Math.round(away), distance);
		}

		context.takeScreenshot(name);
	}

	/**
	 * 26: a Republic officer spawned three blocks ahead, Republic standing at Wary, and the officer's
	 * dialogue with its line typed out, so the "Make amends" option shows.
	 */
	private static void officer(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("execute as @p at @s run tp @s ~ ~ ~ ~ 0");
		context.waitTicks(5);
		int officerId = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Identity.setReputation(p, Faction.REPUBLIC, WARY_REP);
			ServerLevel level = p.level();
			BlockPos at = ground(level, p.position().add(flatLook(p).scale(3)));
			LivingEntity officer = null;

			try {
				officer = Troopers.spawnOfficer(level, at, Faction.REPUBLIC, 0);
				Vec3 feet = Vec3.atBottomCenterOf(at);
				float yaw = yawToward(feet, p.position());
				officer.snapTo(feet.x, feet.y, feet.z, yaw, 0f);
				officer.setYHeadRot(yaw);
			} catch (RuntimeException e) {
				OldRepublicMod.LOGGER.error("Story client step: no Republic officer could be spawned; the dialogue opens without a speaker", e);
			}

			if (!Dialogues.open(p, OFFICER_REPUBLIC, officer)) {
				OldRepublicMod.LOGGER.error("Story client step: the dialogue {} did not open", OFFICER_REPUBLIC);
			}

			return officer == null ? -1 : officer.getId();
		});

		if (waitForScreen(context, "DialogueScreen")) {
			context.waitTicks(4);
			// Skips the typing so the options show.
			context.getInput().pressKey(InputConstants.KEY_SPACE);
			context.waitTicks(6);
		}

		context.takeScreenshot("oldrepublic_m3_26_officer_wary_amends");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		context.setScreen(() -> null);
		server.runOnServer(s -> {
			Entity officer = officerId < 0 ? null : player(s).level().getEntity(officerId);

			if (officer != null) {
				officer.discard();
			}
		});
		context.waitTicks(5);
	}

	/** 27: the factions page: the Jedi Order Trusted, the Sith Empire Distrusted, the Frontier Neutral, the Republic Wary. */
	private static void standing(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Identity.setReputation(p, Faction.JEDI_ORDER, 150);
			Identity.setReputation(p, Faction.SITH_EMPIRE, -260);
			Identity.setReputation(p, Faction.FRONTIER, 60);
			Identity.setReputation(p, Faction.REPUBLIC, WARY_REP);
		});
		context.waitTicks(10);
		context.setScreen(() -> new StandingHost("factions"));
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m3_27_standing_tab");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	/**
	 * 28: the rank 3 banner (Knight of the Watch) as the ceremony sends it, over the Knighting's
	 * blade-light on the player's shoulders, in third person. The shot waits for the second touch.
	 */
	private static void rankThreeBanner(ClientGameTestContext context, TestServerContext server) {
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		server.runCommand("execute as @p at @s run tp @s ~ ~ ~ ~ 12");
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			EffectEvents.around(p.level(), QuestEffects.RANK_CEREMONY, p.position(), null, p.getId(), 1f, QuestEffects.RANK_KNIGHTING);
			EffectEvents.toPlayer(p, QuestEffects.LESSON_COMPLETE, p.position(), null, p.getId(), Path.JEDI.ordinal(), QuestEffects.LESSON_RANK_THREE);
			OldRepublicMod.LOGGER.info("Story client step: sent the Knighting's effects to {} (shortcut, no promotion)", p.getName().getString());
		});
		context.waitTicks(28);
		context.takeScreenshot("oldrepublic_m3_28_rank3_banner");
		context.waitTicks(40);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	/**
	 * 29: Courier's Fall placed ahead of the player, who becomes an Independent standing before its
	 * pedestal; the Pathfinder's moment plays (Tessaly Arn's figure from the Compass) and the
	 * {@code pathfinder} dialogue opens, as using the pedestal does.
	 */
	private static void pathfinder(ClientGameTestContext context, TestServerContext server) {
		BlockPos[] spots = server.computeOnServer(s -> placeFall(player(s)));

		if (spots == null) {
			context.takeScreenshot("oldrepublic_m3_29_pathfinder_fall");
			return;
		}

		BlockPos pedestal = spots[0];
		BlockPos stand = spots[1];
		Vec3 top = Vec3.atBottomCenterOf(pedestal).add(0, 1, 0);
		server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f facing %.1f %.1f %.1f", stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5,
				top.x, top.y + 0.8, top.z));
		context.waitTicks(30);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			EffectEvents.around(p.level(), QuestEffects.RANK_CEREMONY, top, null, p.getId(), 1f, QuestEffects.RANK_PATHFINDER);
			Dialogues.open(p, RankThree.PATHFINDER_DIALOGUE, null);
		});

		if (waitForScreen(context, "DialogueScreen")) {
			context.waitTicks(36);
			context.getInput().pressKey(InputConstants.KEY_SPACE);
			context.waitTicks(6);
		}

		context.takeScreenshot("oldrepublic_m3_29_pathfinder_fall");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		context.setScreen(() -> null);
		// The figure folds once the dialogue is closed and it has stood its minimum.
		context.waitTicks(70);
	}

	/**
	 * Places Courier's Fall {@link #FALL_AHEAD} blocks ahead of the player and makes the player an
	 * Independent whose chapter is complete with that fall recorded.
	 *
	 * @return the pedestal and a free spot before it, or null when the template could not be placed
	 */
	private static BlockPos @Nullable [] placeFall(ServerPlayer p) {
		ServerLevel level = p.level();
		BlockPos centre = ground(level, p.position().add(flatLook(p).scale(FALL_AHEAD)));
		RuntimeSites.Placement placement;

		try {
			placement = RuntimeSites.placeAt(level, COURIERS_FALL, centre, Rotation.NONE, FALL_BURIED, p.getUUID());
		} catch (IllegalArgumentException | IllegalStateException e) {
			OldRepublicMod.LOGGER.error("Story client step: Courier's Fall could not be placed at {}", centre, e);
			return null;
		}

		BlockPos found = SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.PEDESTAL_WAYFARER);
		BlockPos pedestal = found != null ? found : placement.origin();
		OldRepublicMod.LOGGER.info("Story client step: placed Courier's Fall at {} (pedestal {}) for the Pathfinder shot", placement.origin(), pedestal);

		IdentityData identity = Identity.get(p);
		identity.path = Path.INDEPENDENT;
		identity.rank = 0;
		Identity.commit(p, identity);
		StoryData story = Story.get(p);
		story.path = Path.INDEPENDENT.id();
		story.chapter3 = ChapterThreeRules.withFall(ChapterThreeRules.withStage(story.chapter3, ChapterThreeStage.COMPLETE), pedestal);
		Story.commit(p, story);
		return new BlockPos[] {pedestal, standBefore(level, pedestal)};
	}

	/** A free spot 4 (else 3) blocks from the pedestal on solid ground, on any side; else the ground 4 blocks south. */
	private static BlockPos standBefore(ServerLevel level, BlockPos pedestal) {
		for (int reach : new int[] {4, 3}) {
			for (int[] side : new int[][] {{0, 1}, {1, 0}, {0, -1}, {-1, 0}}) {
				BlockPos spot = pedestal.offset(side[0] * reach, 0, side[1] * reach);

				if (level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir() && !level.getBlockState(spot.below()).isAir()) {
					return spot;
				}
			}
		}

		return ground(level, Vec3.atCenterOf(pedestal.offset(0, 0, 4)));
	}

	/** The first free block above the ground at a column. */
	private static BlockPos ground(ServerLevel level, Vec3 at) {
		int x = (int) Math.floor(at.x);
		int z = (int) Math.floor(at.z);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** The player's look flattened to the ground plane (south when looking straight up or down). */
	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	/** The yaw (degrees, Minecraft convention) that looks from {@code from} toward {@code to}. */
	private static float yawToward(Vec3 from, Vec3 to) {
		return (float) (Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x)) - 90.0);
	}

	/**
	 * Waits up to {@link #SCREEN_TIMEOUT} ticks for a screen with this simple class name. A miss is
	 * logged as an error and the step goes on, so the remaining screenshots of every feature are
	 * still taken.
	 */
	private static boolean waitForScreen(ClientGameTestContext context, String simpleName) {
		for (int waited = 0; waited < SCREEN_TIMEOUT; waited += 2) {
			if (context.computeOnClient(mc -> screenIs(mc, simpleName))) {
				return true;
			}

			context.waitTicks(2);
		}

		OldRepublicMod.LOGGER.error("Story client step: {} did not open within {} ticks", simpleName, SCREEN_TIMEOUT);
		return false;
	}

	private static boolean screenIs(Minecraft mc, String simpleName) {
		Screen screen = mc.gui.screen();
		return screen != null && screen.getClass().getSimpleName().equals(simpleName);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private StoryClientSteps() {
	}
}
