package com.oldrepublic.gametest.m5;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.identity.StandingPanel;
import com.oldrepublic.client.quest.JournalScreen;
import com.oldrepublic.client.quest.turning.RankFourClient;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.FactionM5Balance;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.IdentityRules;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorEntities;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.planet.HubSpawners;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveRules;
import com.oldrepublic.quest.story.ChapterFiveService;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.CrossingStage;
import com.oldrepublic.quest.story.KaastOutcome;
import com.oldrepublic.quest.story.PathStage;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.quest.turning.RankFour;
import com.oldrepublic.quest.turning.RankFourChecklistPayload;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.TravelAttachments;
import com.oldrepublic.travel.TravelData;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.vendor.hub.HubRole;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 client test steps for Chapter Five, "The Long Hymn", and its two factions (W4 story and factions; spec
 * MILESTONE_5.md 6, 7, 8.5, 13.2, 19.2). {@code M5ClientTest} runs them last, after the turning's steps, in the shared
 * seeded world. One Jedi plays the chapter through, scene by scene, mostly with the real keys and the real hooks. The
 * screenshots are {@code oldrepublic_m5_<nnn>_<what>}, 140 to 153:
 * <ul>
 * <li>140 {@code journal_c5}: the landing hook for Saltgate has met both factions and moved the chapter to SALTGATE; the
 * journal's Chapter Five page with the objective and the three ways (the kajidic's at one job of two) and the helm
 * lesson's jump still open;</li>
 * <li>141 {@code varra}: Varra Hask, used with a real right-click, greets a Jedi who spared Kaast ({@code c5_varra},
 * {@code greet_spare}); the use marks her met;</li>
 * <li>142 {@code vessba}: Vessba the Hutt's own body, seen whole, no screen;</li>
 * <li>143 {@code vessba_audience}: both jobs done, Vessba grants her audience ({@code c5_vessba}, {@code audience}); the
 * deal is then struck with the real keys (1, 1): the kajidic's way is done and the chapter moves to THE_FACTOR;</li>
 * <li>144 {@code dask}: Dask, the Buyer's factor, used with a real right-click ({@code c5_dask}, {@code greet});</li>
 * <li>145 {@code hologram}: "Show me" (keys 1, 1, 1): Ulvaine's message plays on his desk projector (W8 draws her) under
 * the line {@code holo_1}; he is then let go with the real keys, and the chapter moves to ARMED (BLOCKADE for a ship
 * that already has cannons);</li>
 * <li>146 to 148 {@code verse_vision_1} to {@code _3}: the three verse stones silenced through the hook W3's block
 * calls; each vision (the stave, the choir, the quiet city) photographed with its second line showing;</li>
 * <li>149 {@code ulvaine_choice}: Ulvaine yields through the hook W8's fight calls and the choice opens
 * ({@code c5_ulvaine_yield}); she is spared with the real key 1: W8 plays the resolve and the chapter moves to
 * AFTERMATH;</li>
 * <li>150 {@code standing_factions}: the Standing panel's factions page with its six rows, the Vessba kajidic and Clan
 * Hask under the four of Milestone 4;</li>
 * <li>151 {@code report}: Saelen, used with a real right-click, takes the report ({@code c5_hub_saelen},
 * {@code report});</li>
 * <li>152 {@code banner}: the report made with the real key 1: the "Chapter Five complete" banner, in third person;</li>
 * <li>153 {@code standing_rank4}: the Standing panel's path page with the rank 4 checklist ("Toward Master") as the
 * server sent it, the story's requirement now met.</li>
 * </ul>
 *
 * <p>Shortcuts, each logged: the player is made a rank 3 Jedi with Chapters Two to Four complete (Kaast spared, his camp
 * far away) and Chapter Five at CALLED with the helm lesson's first two steps done, directly; the helm and both
 * Milestone 5 destinations are given up front so no unlock notice covers a picture; Saltgate's landing is the hook W1
 * fires at a touchdown, called directly (no flight to Ghessar); the debt and the cargo run are marked done (the cargo
 * run is a space objective, W2's) and the counting-house is marked cleared before the audience, so the factor's mission
 * does not start behind the scenes; the people stand ahead of the player with their AI off, spawned for the scene and
 * removed after it; the blockade and the landing on Tyrvann (W1's and W2's flights) are skipped by putting the chapter
 * at VERSES, the Hymnal's door and the fight (W3's and W8's) by putting it at THE_HYMNAL; the player's level is set so
 * no level-up banner covers a picture (16 for the stones, 17 for the Hymnal); hostile mobs near the player are removed
 * and the player is invulnerable for the steps, since a hit ends a dialogue; the inventory is emptied for the steps;
 * when it is dark the time is set to noon. When a right-click opens nothing, the dialogue is opened on the server
 * (logged) so the remaining pictures are still taken; when a scene could not leave the state the next one needs, the
 * next one sets it directly (logged as a fallback). Every check that fails is collected and the steps fail at the end,
 * after everything is put back: the story, identity, Force and travel data, the inventory and the armour,
 * invulnerability, the camera, the position, and the people the steps spawned.
 */
public final class StoryClientSteps {
	private static final String STEP = "Story M5 client step";
	private static final String DIALOGUE_SCREEN = "DialogueScreen";
	private static final String JOURNAL_SCREEN = "JournalScreen";
	private static final String STANDING_SCREEN = "StandingHost";
	private static final Identifier C5_VARRA = OldRepublicMod.id("c5_varra");
	private static final Identifier C5_VESSBA = OldRepublicMod.id("c5_vessba");
	private static final Identifier C5_DASK = OldRepublicMod.id("c5_dask");
	private static final Identifier C5_ULVAINE_YIELD = OldRepublicMod.id("c5_ulvaine_yield");
	private static final Identifier C5_HUB_SAELEN = OldRepublicMod.id("c5_hub_saelen");
	/** Darth Ulvaine's boss id, as W8's fight reports it. */
	private static final String ULVAINE = "ulvaine";
	/** Chapter Four's camp, far from the steps, so nothing of Chapter Four is placed near the player. */
	private static final GlobalPos FAR_CAMP = GlobalPos.of(Level.OVERWORLD, new BlockPos(29_000_000, 80, 29_000_000));
	/** Kaast's duel resolved ({@code KaastDuel.BOSS_RESOLVED}), so his fate reads as chosen. */
	private static final int KAAST_RESOLVED = 2;
	/** A rank 3 Jedi (Knight of the Watch), so the Standing panel's next rank is rank 4 (8.5). */
	private static final int RANK = 3;
	private static final int ALIGNMENT = 60;
	private static final int TRUST = 60;
	/**
	 * Level 16 for Saltgate and the stones: their XP (the landing, Dask, three stones) stays under level 17, so the
	 * chapter waits at VERSES (6.6) and no level-up banner covers a picture.
	 */
	private static final int VERSE_LEVEL = 16;
	/** Level 17, the duel's level, for the Hymnal: Ulvaine's 5,000 XP and the report's 1,000 stay under level 18. */
	private static final int HYMNAL_LEVEL = 17;
	/** People stand this far ahead of the player, within reach of a right-click; Vessba, being larger, a little further. */
	private static final double AHEAD = 2.5;
	private static final double VESSBA_AHEAD = 3.6;
	/** Ticks after the landing for the planet's banner and the reputation note to pass before the journal opens. */
	private static final int ARRIVAL_SETTLE = 90;
	/** A vision runs 120 ticks; its second line is fully shown from about tick 54 to 68 ({@code VerseVision}). */
	private static final int VISION_SHOT = 58;
	private static final int VISION_END = 70;
	/** Ticks for Ulvaine's figure to form over Dask's desk. */
	private static final int HOLOGRAM_FORM = 16;
	/** Ticks after the report before the banner's picture (the M4 report banner waited 28 in all). */
	private static final int BANNER_TICKS = 20;
	/** The rank 4 checklist is looked at every 40 ticks on the server ({@code RankFour}); wait up to three looks. */
	private static final int CHECKLIST_TIMEOUT = 130;
	/** Ticks a right-click gets to open its dialogue before the server opens it. */
	private static final int CLICK_TIMEOUT = 40;
	/** Ticks to wait for a screen opened on the server. */
	private static final int SCREEN_TIMEOUT = 100;
	/** Ticks after a dialogue choice for the server's answer. */
	private static final int CHOICE_TICKS = 8;
	/** Ticks a lagging server gets to answer a choice before the dialogue counts as stuck. */
	private static final int ANSWER_TIMEOUT = 60;
	/** Where a person may stand, in degrees from the player's look: ahead first, then further round. */
	private static final int[] AROUND = {0, 30, -30, 60, -60, 90, -90, 135, -135, 180};
	/** Hostile mobs this close to the player are removed before each scene. */
	private static final double HOSTILE_RANGE = 48;
	/** The camera's pitch toward a person is kept within this (degrees, down is positive). */
	private static final float AIM_PITCH_MIN = -30f;
	private static final float AIM_PITCH_MAX = 50f;
	/** The look for the visions: ahead, a little above the horizon. */
	private static final float VISION_PITCH = -6f;
	/** The Standing panel's box in the host screen, as the Force screen hosts it. */
	private static final int HOST_W = 440;
	private static final int HOST_H = 250;
	/** The equipment the steps change, put back at the end. */
	private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
	private static final String[] VISION_SHOTS = {"oldrepublic_m5_146_verse_vision_1", "oldrepublic_m5_147_verse_vision_2",
			"oldrepublic_m5_148_verse_vision_3"};

	/** What the steps change on the player, put back at the end. */
	private record Before(StoryData story, IdentityData identity, ForceData force, TravelData travel, List<ItemStack> items,
			Map<EquipmentSlot, ItemStack> worn, boolean invulnerable, Vec3 position, float yRot, float xRot) {
		static Before of(ServerPlayer p) {
			Inventory inventory = p.getInventory();
			List<ItemStack> items = new ArrayList<>();

			for (int i = 0; i < inventory.getContainerSize(); i++) {
				items.add(inventory.getItem(i).copy());
			}

			Map<EquipmentSlot, ItemStack> worn = new EnumMap<>(EquipmentSlot.class);

			for (EquipmentSlot slot : WORN) {
				worn.put(slot, p.getItemBySlot(slot).copy());
			}

			return new Before(Story.get(p).copy(), Identity.get(p).copy(), Progression.get(p).copy(), TravelAttachments.get(p), items, worn,
					p.isInvulnerable(), p.position(), p.getYRot(), p.getXRot());
		}

		void restore(ServerPlayer p) {
			Story.commit(p, story.copy());
			Identity.commit(p, identity.copy());
			Progression.commit(p, force.copy());
			SkillService.refresh(p);
			TravelAttachments.set(p, travel);
			Inventory inventory = p.getInventory();
			inventory.clearContent();

			for (int i = 0; i < items.size() && i < inventory.getContainerSize(); i++) {
				inventory.setItem(i, items.get(i).copy());
			}

			worn.forEach((slot, stack) -> p.setItemSlot(slot, stack.copy()));
			p.setInvulnerable(invulnerable);
		}

		String teleport() {
			return String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.1f %.1f", position.x, position.y, position.z, yRot, xRot);
		}
	}

	/** The camera's yaw and pitch toward someone; {@code found} false when there was nothing to look at. */
	private record Aim(boolean found, float yaw, float pitch) {
		static final Aim NONE = new Aim(false, 0f, 0f);
	}

	/** How a dialogue came to be open: the real right-click, the server's fallback, or not at all. */
	private enum Opened {
		CLICK, SERVER, NONE
	}

	/** A bare screen hosting the Standing panel on one page, the way the Force screen hosts it (as the M3 steps do). */
	private static final class StandingHost extends Screen {
		private final StandingPanel panel = new StandingPanel();

		StandingHost(String page) {
			super(Component.literal("Standing"));
			panel.open(page);
		}

		@Override
		public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			int w = Math.min(width - 16, HOST_W);
			int h = Math.min(height - 16, HOST_H);
			panel.render(graphics, font, (width - w) / 2, (height - h) / 2, w, h, partialTick);
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		}

		@Override
		public boolean isPauseScreen() {
			return false;
		}
	}

	/** What the steps spawned (touched on the server thread only), removed after each scene and at the end. */
	private static final List<Entity> SPAWNED = new ArrayList<>();
	/** Failed checks (test thread only), reported together once everything is put back. */
	private static final List<String> FAILURES = new ArrayList<>();

	/** Runs Chapter Five's steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		FAILURES.clear();
		server.runOnServer(s -> SPAWNED.clear());
		Before before = server.computeOnServer(s -> Before.of(player(s)));
		CameraType camera = context.computeOnClient(mc -> mc.options.getCameraType());

		try {
			setUp(context, server, before);
			arrival(context, server);
			varra(context, server);
			vessba(context, server);
			dask(context, server);
			verses(context, server, before);
			ulvaine(context, server);
			factions(context);
			report(context, server);
			rankFour(context, server);
		} finally {
			context.setScreen(() -> null);
			context.runOnClient(mc -> mc.options.setCameraType(camera));
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Dialogues.close(p);
				StoryMentors.cancelComing(p);
				discardSpawned();
				before.restore(p);
			});
			server.runCommand(before.teleport());
			context.waitTicks(20);
			OldRepublicMod.LOGGER.info("{}s: story, identity, Force and travel data, inventory and armour, invulnerability, camera and position put "
					+ "back; the people spawned for the scenes removed", STEP);
		}

		if (!FAILURES.isEmpty()) {
			throw new AssertionError(STEP + "s: " + FAILURES.size() + " check(s) failed: " + String.join("; ", FAILURES));
		}
	}

	/** Makes the player a rank 3 Jedi at Chapter Five CALLED, empties the inventory, makes them invulnerable; noon when dark. */
	private static void setUp(ClientGameTestContext context, TestServerContext server, Before before) {
		boolean dark = server.computeOnServer(s -> {
			ServerPlayer p = player(s);

			if (p.isPassenger()) {
				OldRepublicMod.LOGGER.warn("{}: the player was still riding {}; dismounted for the steps", STEP, p.getVehicle());
				p.stopRiding();
			}

			p.getInventory().clearContent();
			p.setInvulnerable(true);
			clearHostiles(p);
			become(p, before);
			OldRepublicMod.LOGGER.info("{}: {} is a rank {} Jedi with Chapters Two to Four complete (Kaast spared) and Chapter Five at CALLED, the "
					+ "helm lesson's first two steps done, level {}; the helm, Saltgate and the holdfast are given up front; the inventory is "
					+ "emptied and the player invulnerable, a hit ending a dialogue (shortcuts; put back afterwards)", STEP, p.getName().getString(), RANK,
					VERSE_LEVEL);
			return p.level().isDarkOutside();
		});

		if (dark) {
			OldRepublicMod.LOGGER.info("{}: it is dark; the time is set to noon for the pictures (shortcut; the story clock reads game time)", STEP);
			server.runCommand("time set noon");
		}

		context.setScreen(() -> null);
		camera(context, CameraType.FIRST_PERSON);
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- 140 the landing and the journal

	/** 140: the landing hook for Saltgate meets both factions and moves the chapter to SALTGATE; the journal's page. */
	private static void arrival(ClientGameTestContext context, TestServerContext server) {
		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			clearHostiles(p);
			int clan = Identity.reputation(p, Faction.CLAN_HASK);
			QuestHooks.onPlanetArrived(p, Planets.Planet.GHESSAR, true);
			OldRepublicMod.LOGGER.info("{}: Saltgate's landing hook called as W1 fires it at a touchdown (shortcut: no flight to Ghessar)", STEP);
			String problem = afterArrival(p, clan);
			StoryData story = Story.get(p);
			story.flags.add(ChapterFiveRules.JOB_DEBT);
			Story.commit(p, story);
			OldRepublicMod.LOGGER.info("{}: the debt job is marked done, so the kajidic's way stands at one job of two (shortcut)", STEP);
			return problem;
		}));
		context.waitTicks(ARRIVAL_SETTLE);
		context.runOnClient(mc -> JournalScreen.openOnTab("journal"));

		if (waitForScreen(context, JOURNAL_SCREEN, SCREEN_TIMEOUT)) {
			context.waitTicks(10);
		}

		shot(context, JOURNAL_SCREEN, "oldrepublic_m5_140_journal_c5");
		check(context.computeOnClient(mc -> {
			StoryData seen = mc.player == null ? null : Story.peek(mc.player);
			return seen != null && seen.chapter5.stage() == ChapterFiveStage.SALTGATE ? null
					: "the client's story data did not reach SALTGATE for the journal (" + (seen == null ? "none" : describe(seen)) + ")";
		}));
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	/**
	 * What the landing left out (null when it did everything): SALTGATE, both factions met, the clan's start from Kaast
	 * spared on top of {@code clanBefore}, the entry.
	 */
	private static @Nullable String afterArrival(ServerPlayer p, int clanBefore) {
		List<String> missing = new ArrayList<>();
		StoryData story = Story.get(p);

		if (story.chapter5.stage() != ChapterFiveStage.SALTGATE) {
			missing.add("SALTGATE (" + describe(story) + ")");
		}

		if (!Identity.knows(p, Faction.VESSBA_KAJIDIC) || !Identity.knows(p, Faction.CLAN_HASK)) {
			missing.add("both factions met");
		}

		int start = Identity.reputation(p, Faction.CLAN_HASK) - clanBefore;

		if (start != FactionM5Balance.get().clanStartSpared) {
			missing.add("Clan Hask +" + FactionM5Balance.get().clanStartSpared + " for Kaast spared (" + start + ")");
		}

		if (!entry(p, JournalEntries.SALTGATE)) {
			missing.add("the entry " + JournalEntries.SALTGATE);
		}

		return missing.isEmpty() ? null : "Saltgate's landing left out " + String.join(", ", missing);
	}

	// ---------------------------------------------------------------- 141 Varra

	/** 141: Varra Hask, used with a right-click, greets a Jedi who spared Kaast; the use marks her met. */
	private static void varra(ClientGameTestContext context, TestServerContext server) {
		UUID varra = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			clearHostiles(p);
			HubNpc npc = spawnHubAhead(p, "varra", AHEAD);
			return npc == null ? null : npc.getUUID();
		});

		if (varra == null) {
			fail("Varra could not be spawned");
		} else {
			Opened opened = use(context, server, varra, C5_VARRA);

			if (opened != Opened.NONE && at(context, server, C5_VARRA, "greet_spare")) {
				reveal(context);
				shot(context, DIALOGUE_SCREEN, "oldrepublic_m5_141_varra");
			}

			boolean met = server.computeOnServer(s -> Story.get(player(s)).hasFlag(ChapterFiveRules.MET_VARRA));

			if (opened == Opened.CLICK && !met) {
				fail("using Varra at SALTGATE did not mark her met (" + ChapterFiveRules.MET_VARRA + ")");
			} else if (!met) {
				OldRepublicMod.LOGGER.warn("{}: Varra was not used with a right-click, so she is not marked met; Dask's clan choice stays hidden", STEP);
			}
		}

		endScene(context, server);
	}

	// ---------------------------------------------------------------- 142 and 143 Vessba

	/** 142: Vessba seen whole. 143: her audience, then the deal (keys 1, 1): the kajidic's way and THE_FACTOR. */
	private static void vessba(ClientGameTestContext context, TestServerContext server) {
		UUID vessba = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			clearHostiles(p);
			StoryData story = Story.get(p);
			story.flags.add(ChapterFiveRules.JOB_CARGO);
			story.flags.add(ChapterFiveRules.COUNTING_HOUSE);
			Story.commit(p, story);
			OldRepublicMod.LOGGER.info("{}: the cargo run (a space objective, W2's) is marked done, so Vessba grants her audience; the counting-house is "
					+ "marked cleared ahead, so the factor's mission does not start behind the scenes (shortcuts)", STEP);
			HubNpc npc = spawnVessba(p);
			return npc == null ? null : npc.getUUID();
		});

		if (vessba == null) {
			fail("Vessba could not be spawned (her own entity type, the hub spawner's role 'vessba')");
			endScene(context, server);
			return;
		}

		lookAt(context, server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Entity her = p.level().getEntity(vessba);
			return her == null ? Aim.NONE : aim(p, her.position().add(0, her.getBbHeight() * 0.5, 0));
		}));
		context.waitTicks(10);
		shot(context, null, "oldrepublic_m5_142_vessba");

		if (use(context, server, vessba, C5_VESSBA) != Opened.NONE && at(context, server, C5_VESSBA, "audience")) {
			reveal(context);
			shot(context, DIALOGUE_SCREEN, "oldrepublic_m5_143_vessba_audience");

			int kajidic = server.computeOnServer(s -> Identity.reputation(player(s), Faction.VESSBA_KAJIDIC));

			// 1: "the factor"; 1: the deal (c5:way kajidic).
			if (next(context, server, 1, C5_VESSBA, "sell")) {
				chooseLast(context, server, 1);
				check(server.computeOnServer(s -> afterAudience(player(s), kajidic)));
			}
		}

		endScene(context, server);
	}

	/** What the audience left out (null when it did everything): the kajidic's way, its entry, THE_FACTOR, the reputation over {@code repBefore}. */
	private static @Nullable String afterAudience(ServerPlayer p, int repBefore) {
		List<String> missing = new ArrayList<>();
		StoryData story = Story.get(p);

		if (!story.hasFlag(ChapterFiveRules.AUDIENCE) || !story.chapter5.hasWay(ChapterFive.Way.KAJIDIC)) {
			missing.add("the kajidic's way");
		}

		if (!entry(p, JournalEntries.THE_KAJIDIC_WAY)) {
			missing.add("the entry " + JournalEntries.THE_KAJIDIC_WAY);
		}

		if (story.chapter5.stage() != ChapterFiveStage.THE_FACTOR) {
			missing.add("THE_FACTOR (" + describe(story) + ")");
		}

		int gained = Identity.reputation(p, Faction.VESSBA_KAJIDIC) - repBefore;
		int audienceRep = ChapterFiveService.balance().audienceRep;

		if (gained != audienceRep) {
			missing.add("the kajidic +" + audienceRep + " (" + gained + ")");
		}

		return missing.isEmpty() ? null : "the deal with Vessba (keys 1, 1) left out " + String.join(", ", missing);
	}

	// ---------------------------------------------------------------- 144 and 145 Dask and the hologram

	/**
	 * 144: Dask, used with a right-click. 145: "Show me" (keys 1, 1, 1): Ulvaine's message on his projector; then he is
	 * let go (keys 1, 1, 1) and the chapter moves on to ARMED (or BLOCKADE for a ship with cannons).
	 */
	private static void dask(ClientGameTestContext context, TestServerContext server) {
		UUID dask = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			clearHostiles(p);
			StoryData story = Story.get(p);

			if (story.chapter5.stage() != ChapterFiveStage.THE_FACTOR) {
				story.chapter5 = story.chapter5.withWay(ChapterFive.Way.KAJIDIC).withStage(ChapterFiveStage.THE_FACTOR, 0L);
				OldRepublicMod.LOGGER.warn("{}: the audience did not reach THE_FACTOR; the chapter is put there directly for Dask (fallback)", STEP);
			}

			story.flags.add(ChapterFiveRules.COUNTING_HOUSE);
			Story.commit(p, story);
			HubNpc npc = spawnHubAhead(p, "dask", AHEAD);
			OldRepublicMod.LOGGER.info("{}: the counting-house is cleared (shortcut: its fight is on Ghessar); Dask stands ahead as at his desk", STEP);
			return npc == null ? null : npc.getUUID();
		});

		if (dask == null) {
			fail("Dask could not be spawned");
			endScene(context, server);
			return;
		}

		if (use(context, server, dask, C5_DASK) == Opened.NONE || !at(context, server, C5_DASK, "greet")) {
			endScene(context, server);
			return;
		}

		reveal(context);
		shot(context, DIALOGUE_SCREEN, "oldrepublic_m5_144_dask");

		// 1: the buyer; 1: what she wants; 1: "show me" (c5:hologram).
		if (next(context, server, 1, C5_DASK, "buyer") && next(context, server, 1, C5_DASK, "want") && next(context, server, 1, C5_DASK, "holo_1")) {
			context.waitTicks(HOLOGRAM_FORM);
			reveal(context);
			shot(context, DIALOGUE_SCREEN, "oldrepublic_m5_145_hologram");
			check(server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				return entry(p, JournalEntries.THE_BUYERS_FACE) && codex(p, JournalEntries.CODEX_DARTH_ULVAINE) ? null
						: "the hologram (c5:hologram) did not open the entry " + JournalEntries.THE_BUYERS_FACE + " and the codex "
								+ JournalEntries.CODEX_DARTH_ULVAINE;
			}));

			// 1: listen; 1: listen; 1: let him go (c5:dask release).
			if (next(context, server, 1, C5_DASK, "holo_2") && next(context, server, 1, C5_DASK, "coords")) {
				chooseLast(context, server, 1);
				check(server.computeOnServer(s -> afterDask(player(s))));
			}
		}

		endScene(context, server);
	}

	/** What letting Dask go left out (null when it did everything): the choice, the entry, ARMED or BLOCKADE. */
	private static @Nullable String afterDask(ServerPlayer p) {
		List<String> missing = new ArrayList<>();
		StoryData story = Story.get(p);

		if (story.chapter5.dask() != StoryData.Chapter5.DASK_RELEASE) {
			missing.add("the release");
		}

		if (!entry(p, JournalEntries.THE_FACTOR)) {
			missing.add("the entry " + JournalEntries.THE_FACTOR);
		}

		ChapterFiveStage stage = story.chapter5.stage();

		if (stage != ChapterFiveStage.ARMED && stage != ChapterFiveStage.BLOCKADE) {
			missing.add("ARMED or BLOCKADE (" + describe(story) + ")");
		}

		return missing.isEmpty() ? null : "letting Dask go (keys 1, 1, 1) left out " + String.join(", ", missing);
	}

	// ---------------------------------------------------------------- 146 to 148 the verse visions

	/** 146 to 148: the three verse stones silenced through the hook W3's block calls; each vision with its second line. */
	private static void verses(ClientGameTestContext context, TestServerContext server, Before before) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			clearHostiles(p);
			StoryData story = Story.get(p);
			story.chapter5 = story.chapter5.withStage(ChapterFiveStage.VERSES, 0L);
			Story.commit(p, story);
			level(p, VERSE_LEVEL);
			OldRepublicMod.LOGGER.info("{}: the blockade and the landing on Tyrvann are W1's and W2's flights: the chapter is put at VERSES directly, "
					+ "level {} so the stones' XP raises no level (shortcuts)", STEP, VERSE_LEVEL);
		});
		context.setScreen(() -> null);
		camera(context, CameraType.FIRST_PERSON);
		context.getInput().lookAt(before.yRot(), VISION_PITCH);
		context.waitTicks(5);

		for (int stone = 1; stone <= VISION_SHOTS.length; stone++) {
			int index = stone;
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				QuestHooks.onVerseSilenced(p, index);
				OldRepublicMod.LOGGER.info("{}: verse stone {} silenced through the hook W3's block calls (shortcut: the stones stand on Tyrvann)", STEP,
						index);
			});
			context.waitTicks(VISION_SHOT);
			shot(context, null, VISION_SHOTS[stone - 1]);
		}

		check(server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			StoryData story = Story.get(p);
			boolean all = ChapterFiveRules.versesSilenced(story.chapter5.verses()) == 3 && entry(p, JournalEntries.VERSE_ONE)
					&& entry(p, JournalEntries.VERSE_TWO) && entry(p, JournalEntries.VERSE_THREE);
			return all && story.chapter5.stage() == ChapterFiveStage.VERSES ? null
					: "three stones silenced at level " + VERSE_LEVEL + " should give their three entries and wait at VERSES (" + describe(story) + ")";
		}));
		context.waitTicks(VISION_END);
	}

	// ---------------------------------------------------------------- 149 the choice over Ulvaine

	/** 149: Ulvaine yields through the hook W8's fight calls; the choice opens; spared with the real key 1. */
	private static void ulvaine(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			clearHostiles(p);
			StoryData story = Story.get(p);
			story.chapter5 = story.chapter5.withStage(ChapterFiveStage.THE_HYMNAL, 0L).withBoss(0, -1);
			Story.commit(p, story);
			level(p, HYMNAL_LEVEL);
			OldRepublicMod.LOGGER.info("{}: the Hymnal's door and Darth Ulvaine's fight are W3's and W8's: the chapter is put at THE_HYMNAL, level {}, "
					+ "and her yield hook called directly (shortcuts)", STEP, HYMNAL_LEVEL);
			QuestHooks.onBossYielded(p, ULVAINE);
		});

		if (!waitForScreen(context, DIALOGUE_SCREEN, SCREEN_TIMEOUT)) {
			fail("Ulvaine's yield did not open the choice (" + server.computeOnServer(s -> why(player(s))) + ")");
		} else if (at(context, server, C5_ULVAINE_YIELD, "yield")) {
			reveal(context);
			shot(context, DIALOGUE_SCREEN, "oldrepublic_m5_149_ulvaine_choice");
			// 1: spare her (ulvaine:resolve spare).
			chooseLast(context, server, 1);
			check(server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				StoryData story = Story.get(p);
				boolean spared = story.chapter5.stage() == ChapterFiveStage.AFTERMATH && story.chapter5.ulvaineOutcome() == BossOutcome.SPARED
						&& story.hasFlag(ChapterFiveRules.CHOICE_PREFIX + "spare") && entry(p, JournalEntries.ULVAINE_SPARED);
				return spared ? null : "sparing Ulvaine with the key 1 did not lead to AFTERMATH with her spared (" + describe(story) + ")";
			}));
		}

		endScene(context, server);
	}

	// ---------------------------------------------------------------- 150 the Standing panel's factions

	/** 150: the Standing panel's factions page: the four rows of Milestone 4, then the two factions met at Saltgate. */
	private static void factions(ClientGameTestContext context) {
		context.setScreen(() -> new StandingHost("factions"));
		waitForScreen(context, STANDING_SCREEN, SCREEN_TIMEOUT);
		context.waitTicks(12);
		shot(context, STANDING_SCREEN, "oldrepublic_m5_150_standing_factions");
		check(context.computeOnClient(mc -> {
			IdentityData view = mc.player == null ? null : Identity.view(mc.player);
			List<Faction> rows = view == null ? List.of() : IdentityRules.standingRows(view);
			return rows.contains(Faction.VESSBA_KAJIDIC) && rows.contains(Faction.CLAN_HASK) ? null
					: "the Standing panel's rows lack the two factions met at Saltgate: " + rows;
		}));
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	// ---------------------------------------------------------------- 151 and 152 the report and the banner

	/** 151: Saelen, used with a right-click, takes the report. 152: made with the key 1: the banner, in third person. */
	private static void report(ClientGameTestContext context, TestServerContext server) {
		UUID saelen = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			clearHostiles(p);
			StoryData story = Story.get(p);

			if (!ChapterFiveRules.reportReady(story)) {
				story.chapter5 = story.chapter5.withStage(ChapterFiveStage.AFTERMATH, 0L).withBoss(ChapterFiveRules.BOSS_RESOLVED,
						BossOutcome.SPARED.ordinal());
				story.flags.add(ChapterFiveRules.CHOICE_PREFIX + "spare");
				Story.commit(p, story);
				OldRepublicMod.LOGGER.warn("{}: the choice did not reach AFTERMATH; the chapter is put there directly, Ulvaine spared (fallback)", STEP);
			}

			MentorEntity mentor = spawnSaelen(p);
			OldRepublicMod.LOGGER.info("{}: Saelen stands ahead with her AI off as at the Greenwatch (shortcut)", STEP);
			return mentor == null ? null : mentor.getUUID();
		});

		if (saelen == null) {
			fail("Saelen could not be spawned for the report");
		} else if (use(context, server, saelen, C5_HUB_SAELEN) != Opened.NONE && at(context, server, C5_HUB_SAELEN, "report")) {
			reveal(context);
			shot(context, DIALOGUE_SCREEN, "oldrepublic_m5_151_report");
			// 1: "She is spared" (c5:report).
			chooseLast(context, server, 1);
			context.setScreen(() -> null);
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.waitTicks(BANNER_TICKS);
			shot(context, null, "oldrepublic_m5_152_banner");
			check(server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				StoryData story = Story.get(p);
				return story.chapter5.stage() == ChapterFiveStage.COMPLETE && story.hasFlag(ChapterFiveRules.REPORTED) && entry(p, JournalEntries.REPORT_C5)
						? null
						: "the report (key 1) did not complete the chapter with its entry (" + describe(story) + ")";
			}));
			context.waitTicks(40);
			camera(context, CameraType.FIRST_PERSON);
		}

		endScene(context, server);
	}

	// ---------------------------------------------------------------- 153 the rank 4 checklist

	/** 153: the checklist as the server sends it after the report (the story met), on the Standing panel's path page. */
	private static void rankFour(ClientGameTestContext context, TestServerContext server) {
		boolean arrived = false;

		for (int waited = 0; waited <= CHECKLIST_TIMEOUT; waited += 5) {
			if (context.computeOnClient(mc -> storyMet(RankFourClient.checklist()))) {
				arrived = true;
				break;
			}

			context.waitTicks(5);
		}

		if (!arrived) {
			fail("the rank 4 checklist did not reach the client with the story met within " + CHECKLIST_TIMEOUT + " ticks (the server's: "
					+ server.computeOnServer(s -> describe(RankFour.checklist(player(s)))) + "; the client's: "
					+ context.computeOnClient(mc -> describe(RankFourClient.checklist())) + ")");
		}

		context.setScreen(() -> new StandingHost("path"));
		waitForScreen(context, STANDING_SCREEN, SCREEN_TIMEOUT);
		context.waitTicks(12);
		shot(context, STANDING_SCREEN, "oldrepublic_m5_153_standing_rank4");
		check(context.computeOnClient(mc -> {
			RankFourChecklistPayload list = RankFourClient.checklist();
			return list.visible() && list.kind() == RankFourChecklistPayload.MASTER && !list.done() ? null
					: "a rank 3 Jedi after the report should see the Master's checklist, not attained (" + describe(list) + ")";
		}));
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	private static boolean storyMet(RankFourChecklistPayload list) {
		return list.visible() && list.entries().stream().anyMatch(e -> "story".equals(e.id()) && e.met());
	}

	private static String describe(RankFourChecklistPayload list) {
		if (!list.visible()) {
			return "hidden";
		}

		List<String> entries = new ArrayList<>();

		for (RankFourChecklistPayload.Entry e : list.entries()) {
			entries.add(e.id() + (e.met() ? " met" : " " + e.have() + "/" + e.need()));
		}

		return "kind " + list.kind() + (list.done() ? ", done" : "") + ": " + String.join(", ", entries);
	}

	// ---------------------------------------------------------------- the player's state

	/**
	 * Puts the player's data back to the start, then makes them a rank 3 Jedi, introduced, with the Crossing decided,
	 * Chapters Two to Four complete (Kaast spared, his camp far away, the back pay settled), Chapter Five at CALLED with
	 * the helm and the orbit done (the jump still to make), its first two entries written, level 16, the helm and both
	 * Milestone 5 destinations given, and no turning.
	 */
	private static void become(ServerPlayer p, Before before) {
		IdentityData identity = before.identity().copy();
		identity.path = Path.JEDI;
		identity.rank = RANK;
		identity.alignment = ALIGNMENT;
		identity.choices.add(Identity.INTRODUCED);
		identity.trust.put(MentorRole.JEDI.id(), TRUST);
		Identity.commit(p, identity);
		StoryData story = before.story().copy();
		story.crossingStage = CrossingStage.DECIDED.ordinal();
		story.path = Path.JEDI.id();
		story.pathStage = PathStage.COMPLETE.ordinal();
		story.chapter3 = ChapterThreeRules.withStage(story.chapter3, ChapterThreeStage.COMPLETE);
		StoryData.Chapter4.Duel duel = StoryData.Chapter4.Duel.NONE.withCamp(FAR_CAMP).withBoss(KAAST_RESOLVED, KaastOutcome.SPARED.ordinal());
		story.chapter4 = story.chapter4.withStage(ChapterFourStage.COMPLETE, 0L).withBackPay().withDuel(duel);
		story.flags.add(ChapterFourRules.HULK_PLACED);
		story.flags.add(ChapterFourRules.CHALLENGE_PLAYED);
		story.chapter5 = StoryData.Chapter5.NEW.withStage(ChapterFiveStage.CALLED, 0L);
		story.flags.add(ChapterFiveRules.HELM);
		story.flags.add(ChapterFiveRules.ORBIT);
		story.turning = StoryData.Turning.NEW;
		Story.commit(p, story);
		ForceData force = before.force().copy();
		force.questStage = Math.max(force.questStage, QuestStage.COMPLETE.ordinal());
		force.level = VERSE_LEVEL;
		force.xp = 0;
		force.flags.add(JournalEntries.entryFlag(JournalEntries.THE_FACTOR_ASKS));
		force.flags.add(JournalEntries.entryFlag(JournalEntries.THE_HELM));
		Progression.commit(p, force);
		TravelAttachments.set(p, before.travel().withHelm(true).withUnlocked(Destination.SALTGATE, true).withUnlocked(Destination.HOLDFAST, true));
	}

	/** Sets the level with no XP into it. */
	private static void level(ServerPlayer p, int level) {
		ForceData force = Progression.get(p);
		force.level = level;
		force.xp = 0;
		Progression.commit(p, force);
	}

	private static boolean entry(ServerPlayer p, String id) {
		return Progression.get(p).flags.contains(JournalEntries.entryFlag(id));
	}

	private static boolean codex(ServerPlayer p, String id) {
		return Progression.get(p).flags.contains(JournalEntries.codexFlag(id));
	}

	// ---------------------------------------------------------------- people

	/** A hub host of {@code variant} {@code distance} blocks ahead of the player, facing them, AI off; null when none could be spawned. */
	private static @Nullable HubNpc spawnHubAhead(ServerPlayer p, String variant, double distance) {
		ServerLevel level = p.level();
		Vec3 feet = standAhead(level, p, distance);
		HubNpc npc = HubNpcs.spawn(level, BlockPos.containing(feet), HubRole.HOST, variant);

		if (npc == null) {
			OldRepublicMod.LOGGER.error("{}: the host '{}' could not be spawned", STEP, variant);
			return null;
		}

		hold(npc, feet, p);
		OldRepublicMod.LOGGER.info("{}: '{}' spawned at {} with its AI off (shortcut; removed after the scene)", STEP, variant, BlockPos.containing(feet));
		return npc;
	}

	/** Vessba, in her own body, through the hub spawner's {@code vessba} role, ahead of the player; null when she could not be spawned. */
	private static @Nullable HubNpc spawnVessba(ServerPlayer p) {
		ServerLevel level = p.level();
		Vec3 feet = standAhead(level, p, VESSBA_AHEAD);
		Entity entity = HubSpawners.spawn(level, BlockPos.containing(feet), Planets.Planet.GHESSAR, HubNpcs.VESSBA);

		if (!(entity instanceof HubNpc vessba)) {
			if (entity != null) {
				entity.discard();
			}

			OldRepublicMod.LOGGER.error("{}: the hub spawner's role 'vessba' made {}", STEP, entity);
			return null;
		}

		hold(vessba, feet, p);
		OldRepublicMod.LOGGER.info("{}: Vessba spawned at {} with her AI off (shortcut; she lies on her dais at Saltgate)", STEP, BlockPos.containing(feet));
		return vessba;
	}

	/** Puts a spawned person at {@code feet}, facing the player, with its AI off so it holds still. */
	private static void hold(HubNpc npc, Vec3 feet, ServerPlayer p) {
		float yaw = yawToward(feet, p.position());
		npc.snapTo(feet.x, feet.y, feet.z, yaw, 0f);
		npc.setYHeadRot(yaw);
		npc.setYBodyRot(yaw);
		npc.setNoAi(true);
		SPAWNED.add(npc);
	}

	/**
	 * The player's own Saelen, {@link #AHEAD} blocks ahead and facing them, with her AI off. The story points at her before
	 * she enters the world, so she is not taken for a stray copy.
	 */
	private static @Nullable MentorEntity spawnSaelen(ServerPlayer p) {
		ServerLevel level = p.level();
		Vec3 feet = standAhead(level, p, AHEAD);
		MentorEntity mentor = MentorEntities.type(MentorRole.JEDI).create(level, EntitySpawnReason.COMMAND);

		if (mentor == null) {
			OldRepublicMod.LOGGER.error("{}: Saelen could not be created", STEP);
			return null;
		}

		float yaw = yawToward(feet, p.position());
		mentor.snapTo(feet.x, feet.y, feet.z, yaw, 0f);
		mentor.setYHeadRot(yaw);
		mentor.setYBodyRot(yaw);
		mentor.setOwner(p.getUUID());
		StoryTags.setOwner(mentor, p.getUUID());
		mentor.setAnchor(BlockPos.containing(feet), 1);
		mentor.setPersistenceRequired();
		mentor.setNoAi(true);
		StoryData story = Story.get(p);
		story.setMentor(MentorRole.JEDI.id(), mentor.getUUID());
		Story.commit(p, story);
		level.addFreshEntity(mentor);
		SPAWNED.add(mentor);
		return mentor;
	}

	private static void discardSpawned() {
		for (Entity entity : SPAWNED) {
			if (!entity.isRemoved()) {
				entity.discard();
			}
		}

		SPAWNED.clear();
	}

	/** Removes hostile mobs near the player (logged): a hit ends a dialogue. */
	private static void clearHostiles(ServerPlayer p) {
		List<String> removed = new ArrayList<>();

		for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(HOSTILE_RANGE), x -> x instanceof Enemy && x.isAlive())) {
			removed.add(e.getName().getString());
			e.discard();
		}

		if (!removed.isEmpty()) {
			OldRepublicMod.LOGGER.info("{}: removed {} hostile mob(s) near {} so no hit ends a dialogue (shortcut): {}", STEP, removed.size(),
					p.getName().getString(), removed);
		}
	}

	/**
	 * Where someone can stand {@code distance} from the player within a block of the player's height, with nothing solid
	 * between the player's eyes and theirs: ahead when it can be, else the first of {@link #AROUND} that can. On a slope the
	 * ground ahead can lie several blocks up, out of the right-click's reach and the dialogue's choice range (6 blocks), so
	 * nobody answers there. With no such spot, ahead, near the player's height or else on the ground, as before.
	 */
	private static Vec3 standAhead(ServerLevel level, ServerPlayer p, double distance) {
		Vec3 look = flatLook(p);

		for (int degrees : AROUND) {
			Vec3 at = p.position().add(look.yRot((float) Math.toRadians(degrees)).scale(distance));
			BlockPos feet = standNear(level, at, p.getBlockY());
			Vec3 eyes = new Vec3(at.x, feet.getY() + p.getEyeHeight(), at.z);

			if (Math.abs(feet.getY() - p.getBlockY()) <= 1
					&& level.clip(new ClipContext(p.getEyePosition(), eyes, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS) {
				return new Vec3(at.x, feet.getY(), at.z);
			}
		}

		Vec3 at = p.position().add(look.scale(distance));
		BlockPos feet = standNear(level, at, p.getBlockY());
		OldRepublicMod.LOGGER.warn("{}: no spot {} blocks from {} within a block of their height and in plain view; the ground ahead at {}", STEP, distance,
				p.getName().getString(), feet);
		return new Vec3(at.x, feet.getY(), at.z);
	}

	/** The first free spot on solid ground in the column of {@code at}, near height {@code y}; else the ground there. */
	private static BlockPos standNear(ServerLevel level, Vec3 at, int y) {
		int x = (int) Math.floor(at.x);
		int z = (int) Math.floor(at.z);

		for (int dy : new int[] {0, 1, -1, 2, -2, -3}) {
			BlockPos feet = new BlockPos(x, y + dy, z);
			BlockPos below = feet.below();

			if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && open(level, feet) && open(level, feet.above())) {
				return feet;
			}
		}

		return ground(level, at);
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).blocksMotion() && level.getFluidState(pos).isEmpty();
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

	/** The camera's yaw and pitch from the player's eyes to {@code target} (the pitch kept to a sensible range). */
	private static Aim aim(ServerPlayer p, Vec3 target) {
		Vec3 eye = p.getEyePosition();
		double flat = Math.sqrt((target.x - eye.x) * (target.x - eye.x) + (target.z - eye.z) * (target.z - eye.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, Math.max(flat, 0.5)));
		return new Aim(true, yawToward(eye, target), Math.clamp(pitch, AIM_PITCH_MIN, AIM_PITCH_MAX));
	}

	// ---------------------------------------------------------------- input, dialogue and pictures

	private static void lookAt(ClientGameTestContext context, Aim aim) {
		if (aim.found()) {
			context.getInput().lookAt(aim.yaw(), aim.pitch());
			context.waitTicks(2);
		}
	}

	private static void camera(ClientGameTestContext context, CameraType type) {
		context.runOnClient(mc -> mc.options.setCameraType(type));
	}

	/**
	 * Turns to {@code target} and right-clicks it, then waits for {@code dialogue}'s screen; when nothing opens, the
	 * dialogue is opened on the server with the target as the speaker (logged). Returns how the dialogue came to be open.
	 */
	private static Opened use(ClientGameTestContext context, TestServerContext server, UUID target, Identifier dialogue) {
		context.setScreen(() -> null);
		Aim aim = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Entity entity = p.level().getEntity(target);
			return entity == null ? Aim.NONE : aim(p, entity.getEyePosition());
		});

		if (!aim.found()) {
			fail("the speaker of " + dialogue.getPath() + " is gone");
			return Opened.NONE;
		}

		lookAt(context, aim);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);

		if (waitForScreen(context, DIALOGUE_SCREEN, CLICK_TIMEOUT)) {
			return Opened.CLICK;
		}

		OldRepublicMod.LOGGER.warn("{}: the right-click opened nothing; {} is opened on the server (fallback)", STEP, dialogue);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Entity entity = p.level().getEntity(target);

			if (entity == null || !Dialogues.open(p, dialogue, entity)) {
				OldRepublicMod.LOGGER.error("{}: {} could not be opened", STEP, dialogue);
			}
		});

		if (waitForScreen(context, DIALOGUE_SCREEN, SCREEN_TIMEOUT)) {
			return Opened.SERVER;
		}

		fail(dialogue.getPath() + " did not open (" + server.computeOnServer(s -> why(player(s))) + ")");
		return Opened.NONE;
	}

	/** Whether {@code dialogue} is open at {@code node} on the server; else the failure is noted and the dialogue closed. */
	private static boolean at(ClientGameTestContext context, TestServerContext server, Identifier dialogue, String node) {
		String want = dialogue.getPath() + "/" + node;
		String where = sessionAt(server);

		if (want.equals(where)) {
			return true;
		}

		fail("expected the dialogue at " + want + ", found " + (where == null ? "none open" : where) + " (" + server.computeOnServer(s -> why(player(s)))
				+ ")");
		context.setScreen(() -> null);
		server.runOnServer(s -> Dialogues.close(player(s)));
		context.waitTicks(5);
		return false;
	}

	/** "dialogue/node" of the player's open dialogue on the server, or null. */
	private static @Nullable String sessionAt(TestServerContext server) {
		return server.computeOnServer(s -> {
			DialogueSession session = Dialogues.session(player(s));
			return session == null ? null : session.dialogue().getPath() + "/" + session.node();
		});
	}

	/** Picks option {@code number} with its key and checks that {@code dialogue} went on to {@code node}, once the server has answered. */
	private static boolean next(ClientGameTestContext context, TestServerContext server, int number, Identifier dialogue, String node) {
		choose(context, number);
		String want = dialogue.getPath() + "/" + node;

		for (int waited = CHOICE_TICKS; waited < ANSWER_TIMEOUT && !want.equals(sessionAt(server)); waited += 2) {
			context.waitTicks(2);
		}

		return at(context, server, dialogue, node);
	}

	/** Picks option {@code number}, one that closes the dialogue with its action, and waits for the server to have run it. */
	private static void chooseLast(ClientGameTestContext context, TestServerContext server, int number) {
		choose(context, number);

		for (int waited = CHOICE_TICKS; waited < ANSWER_TIMEOUT && sessionAt(server) != null; waited += 2) {
			context.waitTicks(2);
		}
	}

	/** Skips the line's typing so the options show. */
	private static void reveal(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.KEY_SPACE);
		context.waitTicks(8);
	}

	/** Reveals the line, picks option {@code number} (1 to 6) with its number key, and waits for the server's answer. */
	private static void choose(ClientGameTestContext context, int number) {
		context.getInput().pressKey(InputConstants.KEY_SPACE);
		context.waitTicks(2);
		context.getInput().pressKey(InputConstants.KEY_1 + number - 1);
		context.waitTicks(CHOICE_TICKS);
	}

	/** Closes what is open and removes the scene's people. */
	private static void endScene(ClientGameTestContext context, TestServerContext server) {
		context.setScreen(() -> null);
		server.runOnServer(s -> {
			Dialogues.close(player(s));
			discardSpawned();
		});
		context.waitTicks(5);
	}

	/** Takes the screenshot; with {@code screen}, notes a failure when that screen was not open for it (the shot is kept). */
	private static void shot(ClientGameTestContext context, @Nullable String screen, String name) {
		boolean open = screen == null || context.computeOnClient(mc -> screenIs(mc, screen));
		context.takeScreenshot(name);

		if (!open) {
			fail(screen + " was not open for the shot " + name);
		}
	}

	/** Waits up to {@code timeout} ticks for a screen with this simple class name. */
	private static boolean waitForScreen(ClientGameTestContext context, String simpleName, int timeout) {
		for (int waited = 0; waited < timeout; waited += 2) {
			if (context.computeOnClient(mc -> screenIs(mc, simpleName))) {
				return true;
			}

			context.waitTicks(2);
		}

		return context.computeOnClient(mc -> screenIs(mc, simpleName));
	}

	private static boolean screenIs(Minecraft mc, String simpleName) {
		Screen screen = mc.gui.screen();
		return screen != null && screen.getClass().getSimpleName().equals(simpleName);
	}

	// ---------------------------------------------------------------- failures

	private static void check(@Nullable String problem) {
		if (problem != null) {
			fail(problem);
		}
	}

	private static void fail(String problem) {
		OldRepublicMod.LOGGER.error("{}: {}", STEP, problem);
		FAILURES.add(problem);
	}

	/** Chapter Five's record and what last hurt the player: why a step stalled. */
	private static String why(ServerPlayer p) {
		return "chapter five " + describe(Story.get(p)) + ", " + lastHurt(p);
	}

	private static String describe(StoryData data) {
		StoryData.Chapter5 c = data.chapter5;
		return String.format(Locale.ROOT, "%s, ways %d, dask %d, verses %d, boss %d, ulvaine %d", c.stage().id(), c.ways(), c.dask(), c.verses(), c.boss(),
				c.ulvaine());
	}

	/** What last hurt the player, and how many hostiles are near: why a dialogue may have closed. */
	private static String lastHurt(ServerPlayer p) {
		DamageSource source = p.getLastDamageSource();
		Entity attacker = source == null ? null : source.getEntity();
		String hurt = source == null ? "not hurt lately" : "last hurt by " + source.getMsgId() + (attacker == null ? "" : " from " + attacker.getName().getString());
		int hostiles = p.level().getEntities((Entity) null, p.getBoundingBox().inflate(HOSTILE_RANGE), x -> x instanceof Enemy && x.isAlive()).size();
		return hurt + ", " + hostiles + " hostile(s) within " + (int) HOSTILE_RANGE + " blocks";
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private StoryClientSteps() {
	}
}
