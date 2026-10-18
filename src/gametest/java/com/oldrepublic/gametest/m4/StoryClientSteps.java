package com.oldrepublic.gametest.m4;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.quest.JournalScreen;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.Path;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.PathStage;
import com.oldrepublic.quest.story.SeekerScene;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.vendor.Vendors;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.vendor.hub.HubRole;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 4 client test steps for Chapter Four, hubs and vendors (W2). {@code M4ClientTest} runs
 * them last, in the shared seeded world (spec MILESTONE_4.md 17.2). Screenshots are
 * {@code oldrepublic_m4_<nn>_<what>} with {@code nn} from 20 to 29:
 *
 * <ul>
 * <li>20 {@code seeker_scan}: the seeker droid hovering ahead and a little above the player, its red
 * scan fan sweeping them (the scene started by {@code /oldrepublic c4 seeker});</li>
 * <li>21 {@code chapter4_journal}: a Jedi at WORLD_ARC on Selvaren with the first step done, the
 * journal's Chapter Four page with the arc's checklist;</li>
 * <li>22 {@code ansa_dialogue}: Ansa Veyl (a hub host) three blocks ahead and her first meeting
 * with a Jedi ({@code c4_ansa_meet});</li>
 * <li>23 {@code kaddor_dialogue}: Overseer Kaddor and his first meeting with a Sith
 * ({@code c4_kaddor_meet});</li>
 * <li>24 {@code vendor_locked}: the Watch Quartermaster's screen for a Jedi at Neutral with the
 * Jedi Order, so the Trusted rows show locked;</li>
 * <li>25 {@code report_banner}: the "Chapter Four complete" banner as the report sends it, in third
 * person.</li>
 * </ul>
 *
 * <p>Shortcuts, each logged: the story and identity state is set directly on the server (back pay
 * marked settled so no credits are handed out, the hulk marked placed so none is placed), and the
 * player is made awakened with Chapter One complete, as a Chapter Four player is; the Chapter Four
 * entries such a player has written by WORLD_ARC are flagged directly (no toasts); the hosts and the
 * quartermaster are spawned for their shots and removed after, with the camera turned to face
 * them; hostile mobs near the player are removed and the player is invulnerable for the steps,
 * because a hit ends a dialogue (a skeleton's arrows once closed Kaddor's before his shot); the
 * report banner is the report's own effect event, not a full Chapter Four run (covered by
 * {@code StoryM4GameTests}). A screen shot whose screen is not open when it is taken fails the
 * steps. Afterwards the story, identity and Force data are put back and the player returns to where
 * they stood.
 */
public final class StoryClientSteps {
	private static final Identifier ANSA_MEET = OldRepublicMod.id("c4_ansa_meet");
	private static final Identifier KADDOR_MEET = OldRepublicMod.id("c4_kaddor_meet");
	private static final Identifier WATCH_QUARTERMASTER = OldRepublicMod.id("watch_quartermaster");
	/** Ticks from the scene's start to the shot: the droid comes down over 50 ticks, then scans for 40. */
	private static final int SEEKER_SHOT = 68;
	/** Polls of 10 ticks for the seeker scene to end before the steps go on. */
	private static final int SEEKER_END_POLLS = 12;
	/** The camera's pitch for the seeker shot (looking up at the hover point, 1.5 above the eyes and 3 ahead). */
	private static final float SEEKER_PITCH = -18f;
	/** People are spawned this many blocks ahead of the player. */
	private static final int AHEAD = 3;
	/** Ticks to wait for a screen to open before going on without it (the miss is logged as an error). */
	private static final int SCREEN_TIMEOUT = 100;
	/** Hostile mobs this close to the player are removed before the people shots. */
	private static final double HOSTILE_RANGE = 48;
	/** The camera's pitch toward a spawned person is kept within this (degrees, down is positive). */
	private static final float AIM_PITCH_MIN = -20f;
	private static final float AIM_PITCH_MAX = 50f;

	/** What the steps change, put back at the end. */
	private record Snapshot(StoryData story, IdentityData identity, ForceData force, boolean invulnerable, double x, double y, double z, float yRot,
			float xRot) {
		static Snapshot of(ServerPlayer p) {
			return new Snapshot(Story.get(p).copy(), Identity.get(p).copy(), Progression.get(p).copy(), p.isInvulnerable(), p.getX(), p.getY(), p.getZ(),
					p.getYRot(), p.getXRot());
		}

		void restore(ServerPlayer p) {
			Story.commit(p, story);
			Identity.commit(p, identity);
			Progression.commit(p, force);
			p.setInvulnerable(invulnerable);
		}

		String teleport() {
			return String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.1f %.1f", x, y, z, yRot, xRot);
		}
	}

	/** A person spawned for a shot (id -1 when none could be), and the camera's yaw and pitch that face them. */
	private record Aim(int id, float yaw, float pitch) {
	}

	/** Entity ids spawned by the steps (on the server), discarded at the end. */
	private static final List<Integer> SPAWNED = new ArrayList<>();

	/** Runs the story steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		Snapshot before = server.computeOnServer(s -> Snapshot.of(player(s)));
		SPAWNED.clear();

		try {
			setUp(context, server, Path.JEDI);
			seeker(context, server);
			journal(context, server);
			ansa(context, server);
			kaddor(context, server);
			vendor(context, server);
			reportBanner(context, server);
		} finally {
			context.setScreen(() -> null);
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				SeekerScene.forget(p);
				discardSpawned(s);
				before.restore(p);
			});
			server.runCommand(before.teleport());
			OldRepublicMod.LOGGER.info("Story M4 client steps: story, identity and Force data restored (journal entries and awakening put back)");
			context.waitTicks(20);
		}
	}

	/**
	 * A rank 2 {@code path} whose Chapter Three is complete, introduced, Neutral with the Order (or
	 * the Empire), with Chapter Four not begun, back pay settled and the hulk marked placed.
	 */
	private static void setUp(ClientGameTestContext context, TestServerContext server, Path path) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			setPath(p, path);
			StoryData story = Story.get(p);
			story.pathStage = PathStage.COMPLETE.ordinal();
			story.chapter3 = ChapterThreeRules.withStage(story.chapter3, ChapterThreeStage.COMPLETE);
			story.chapter4 = StoryData.Chapter4.NEW.withBackPay();
			story.flags.add(ChapterFourRules.HULK_PLACED);
			Story.commit(p, story);
			ForceData force = Progression.get(p);
			force.questStage = Math.max(force.questStage, QuestStage.COMPLETE.ordinal());
			Progression.commit(p, force);
			p.setInvulnerable(true);
			OldRepublicMod.LOGGER.info("Story M4 client step: set {} to an awakened rank 2 {} with Chapters One to Three complete, back pay settled, "
					+ "hulk marked placed; invulnerable for the steps (shortcut)", p.getName().getString(), path.id());
			clearHostiles(p);
		});
		context.waitTicks(10);
	}

	/** Sets identity and story path, rank 2, introduced, and Neutral standing with the path's order. */
	private static void setPath(ServerPlayer p, Path path) {
		IdentityData identity = Identity.get(p);
		identity.path = path;
		identity.rank = 2;
		identity.choices.add(Identity.INTRODUCED);
		Identity.commit(p, identity);
		Identity.setReputation(p, Faction.JEDI_ORDER, 0);
		Identity.setReputation(p, Faction.SITH_EMPIRE, 0);
		StoryData story = Story.get(p);
		story.path = path.id();
		Story.commit(p, story);
	}

	/** 20: the seeker comes down ahead of the player and sweeps them with its scan fan. */
	private static void seeker(ClientGameTestContext context, TestServerContext server) {
		server.runCommand(String.format(Locale.ROOT, "execute as @p at @s run tp @s ~ ~ ~ ~ %.1f", SEEKER_PITCH));
		context.waitTicks(5);
		server.runCommand("oldrepublic c4 seeker @p");
		context.waitTicks(SEEKER_SHOT);
		context.takeScreenshot("oldrepublic_m4_20_seeker_scan");

		for (int i = 0; i < SEEKER_END_POLLS && server.computeOnServer(s -> SeekerScene.playing(player(s))); i++) {
			context.waitTicks(10);
		}

		ChapterFourStage stage = server.computeOnServer(s -> Story.get(player(s)).chapter4.stage());
		OldRepublicMod.LOGGER.info("Story M4 client step: after the seeker scene Chapter Four is at {}", stage);
		server.runCommand("execute as @p at @s run tp @s ~ ~ ~ ~ 0");
		context.waitTicks(5);
	}

	/** 21: the Chapter Four page at WORLD_ARC on Selvaren, the first step ticked, the others open. */
	private static void journal(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			StoryData story = Story.get(p);
			story.chapter4 = story.chapter4.withStage(ChapterFourStage.WORLD_ARC, p.level().getGameTime())
					.withArc(new StoryData.Chapter4.Arc(Planets.Planet.SELVAREN.id(), ChapterFourRules.STEP_FIRST, 0));
			story.flags.add(ChapterFourRules.LIFT_OFF);
			Story.commit(p, story);
			// The entries a Jedi has written by now: the briefing, the lift-off, the landing and the
			// first step (the seeker scene wrote A Price itself).
			ForceData force = Progression.get(p);

			for (String entry : List.of(ChapterFourRules.briefingEntry(Path.JEDI), JournalEntries.THE_SHIP_FLIES, JournalEntries.SELVAREN,
					JournalEntries.THE_GREENWATCH)) {
				force.flags.add(JournalEntries.entryFlag(entry));
			}

			Progression.commit(p, force);
			OldRepublicMod.LOGGER.info("Story M4 client step: put {} at WORLD_ARC on Selvaren with its first step done, its Chapter Four entries flagged (shortcut)",
					p.getName().getString());
		});
		context.waitTicks(10);
		context.runOnClient(mc -> JournalScreen.openOnTab("journal"));
		waitForScreen(context, "JournalScreen");
		context.waitTicks(10);
		shot(context, server, "JournalScreen", "oldrepublic_m4_21_chapter4_journal");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	/** 22: Ansa Veyl, spawned ahead, and her first meeting with a Jedi. */
	private static void ansa(ClientGameTestContext context, TestServerContext server) {
		dialogueShot(context, server, "ansa", ANSA_MEET, "oldrepublic_m4_22_ansa_dialogue");
	}

	/** 23: Overseer Kaddor and his first meeting with a Sith. */
	private static void kaddor(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> setPath(player(s), Path.SITH));
		context.waitTicks(5);
		dialogueShot(context, server, "kaddor", KADDOR_MEET, "oldrepublic_m4_23_kaddor_dialogue");
		server.runOnServer(s -> setPath(player(s), Path.JEDI));
		context.waitTicks(5);
	}

	/**
	 * A hub host of {@code variant} spawned ahead, facing the player, the camera turned to them, and
	 * {@code dialogue} opened with its line typed out.
	 */
	private static void dialogueShot(ClientGameTestContext context, TestServerContext server, String variant, Identifier dialogue, String shot) {
		Aim aim = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			clearHostiles(p);
			HubNpc host = spawnAhead(p, HubRole.HOST, variant);
			return host == null ? new Aim(-1, 0f, 0f) : aim(p, host);
		});

		if (aim.id() >= 0) {
			// The host may stand lower than the player: the ground ahead is the heightmap's.
			context.getInput().lookAt(aim.yaw(), aim.pitch());
			context.waitTicks(2);
		}

		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Entity host = aim.id() < 0 ? null : p.level().getEntity(aim.id());

			if (host == null || !Dialogues.open(p, dialogue, host)) {
				OldRepublicMod.LOGGER.error("Story M4 client step: the dialogue {} did not open", dialogue);
			}
		});

		if (waitForScreen(context, "DialogueScreen")) {
			context.waitTicks(4);
			// Skips the typing so the options show.
			context.getInput().pressKey(InputConstants.KEY_SPACE);
			context.waitTicks(8);
		}

		shot(context, server, "DialogueScreen", shot);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(5);
		context.setScreen(() -> null);
		server.runOnServer(StoryClientSteps::discardSpawned);
		context.waitTicks(5);
	}

	/** 24: the Watch Quartermaster for a Jedi at Neutral with the Order: the Trusted rows are locked. */
	private static void vendor(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			clearHostiles(p);
			Identity.setReputation(p, Faction.JEDI_ORDER, 0);
			Entity quartermaster = spawnAhead(p, HubRole.VENDOR, "watch_quartermaster");

			if (quartermaster == null) {
				OldRepublicMod.LOGGER.error("Story M4 client step: no quartermaster could be spawned; the vendor screen is opened by the player");
			}

			Vendors.open(p, quartermaster != null ? quartermaster : p, WATCH_QUARTERMASTER);
		});
		waitForScreen(context, "VendorScreen");
		context.waitTicks(10);
		shot(context, server, "VendorScreen", "oldrepublic_m4_24_vendor_locked");
		context.setScreen(() -> null);
		server.runOnServer(StoryClientSteps::discardSpawned);
		context.waitTicks(5);
	}

	/** 25: the report's banner ("Chapter Four complete"), as {@code c4:report} sends it, in third person. */
	private static void reportBanner(ClientGameTestContext context, TestServerContext server) {
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		server.runCommand("execute as @p at @s run tp @s ~ ~ ~ ~ 12");
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			EffectEvents.toPlayer(p, QuestEffects.LESSON_COMPLETE, p.position(), null, p.getId(), Path.JEDI.ordinal(), QuestEffects.LESSON_CHAPTER_FOUR);
			OldRepublicMod.LOGGER.info("Story M4 client step: sent the report's banner to {} (shortcut, no report made)", p.getName().getString());
		});
		context.waitTicks(28);
		context.takeScreenshot("oldrepublic_m4_25_report_banner");
		context.waitTicks(40);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
	}

	/** A hub person {@link #AHEAD} blocks ahead of the player on the ground, facing them; null when it could not be spawned. */
	private static @Nullable HubNpc spawnAhead(ServerPlayer p, HubRole role, String variant) {
		ServerLevel level = p.level();
		BlockPos at = ground(level, p.position().add(flatLook(p).scale(AHEAD)));
		HubNpc npc = HubNpcs.spawn(level, at, role, variant);

		if (npc == null) {
			OldRepublicMod.LOGGER.error("Story M4 client step: the {} '{}' could not be spawned at {}", role, variant, at);
			return null;
		}

		Vec3 feet = Vec3.atBottomCenterOf(at);
		float yaw = yawToward(feet, p.position());
		npc.snapTo(feet.x, feet.y, feet.z, yaw, 0f);
		npc.setYHeadRot(yaw);
		npc.setYBodyRot(yaw);
		SPAWNED.add(npc.getId());
		OldRepublicMod.LOGGER.info("Story M4 client step: spawned the {} '{}' at {} for its shot", role, variant, at);
		return npc;
	}

	/** The camera's yaw and pitch from the player's eyes to {@code npc}'s (pitch kept to a sensible range). */
	private static Aim aim(ServerPlayer p, HubNpc npc) {
		Vec3 eye = p.getEyePosition();
		Vec3 target = npc.getEyePosition();
		double flat = Math.sqrt((target.x - eye.x) * (target.x - eye.x) + (target.z - eye.z) * (target.z - eye.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, Math.max(flat, 0.5)));
		return new Aim(npc.getId(), yawToward(eye, target), Math.clamp(pitch, AIM_PITCH_MIN, AIM_PITCH_MAX));
	}

	/** Removes hostile mobs near the player (logged): a hit ends a dialogue. */
	private static void clearHostiles(ServerPlayer p) {
		List<String> removed = new ArrayList<>();

		for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(HOSTILE_RANGE), x -> x instanceof Enemy && x.isAlive())) {
			removed.add(e.getName().getString());
			e.discard();
		}

		if (!removed.isEmpty()) {
			OldRepublicMod.LOGGER.info("Story M4 client step: removed {} hostile mob(s) near {} so no hit ends a screen (shortcut): {}", removed.size(),
					p.getName().getString(), removed);
		}
	}

	/**
	 * Takes the screenshot, then fails the steps if {@code screen} was not open when it was taken (the
	 * shot is still saved, so it can be looked at); the log says what last hurt the player.
	 */
	private static void shot(ClientGameTestContext context, TestServerContext server, String screen, String name) {
		boolean open = context.computeOnClient(mc -> screenIs(mc, screen));
		context.takeScreenshot(name);

		if (!open) {
			String why = server.computeOnServer(s -> lastHurt(player(s)));
			OldRepublicMod.LOGGER.error("Story M4 client step: {} was not open for the shot {} ({})", screen, name, why);
			throw new AssertionError("Story M4 client step: " + screen + " was not open for the shot " + name + " (" + why + ")");
		}
	}

	/** What last hurt the player, and how many hostiles are near: why a screen may have closed. */
	private static String lastHurt(ServerPlayer p) {
		DamageSource source = p.getLastDamageSource();
		Entity attacker = source == null ? null : source.getEntity();
		String hurt = source == null ? "not hurt lately" : "last hurt by " + source.getMsgId() + (attacker == null ? "" : " from " + attacker.getName().getString());
		int hostiles = p.level().getEntities((Entity) null, p.getBoundingBox().inflate(HOSTILE_RANGE), x -> x instanceof Enemy && x.isAlive()).size();
		return hurt + ", " + hostiles + " hostile(s) within " + (int) HOSTILE_RANGE + " blocks";
	}

	/** Discards what the steps spawned, in the player's level. */
	private static void discardSpawned(MinecraftServer server) {
		ServerLevel level = player(server).level();

		for (int id : SPAWNED) {
			Entity entity = level.getEntity(id);

			if (entity != null) {
				entity.discard();
			}
		}

		SPAWNED.clear();
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
	 * logged as an error and the step goes on, so the remaining screenshots are still taken.
	 */
	private static boolean waitForScreen(ClientGameTestContext context, String simpleName) {
		for (int waited = 0; waited < SCREEN_TIMEOUT; waited += 2) {
			if (context.computeOnClient(mc -> screenIs(mc, simpleName))) {
				return true;
			}

			context.waitTicks(2);
		}

		OldRepublicMod.LOGGER.error("Story M4 client step: {} did not open within {} ticks", simpleName, SCREEN_TIMEOUT);
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
