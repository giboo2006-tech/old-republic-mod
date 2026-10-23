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
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorEntities;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.quest.story.CrossingStage;
import com.oldrepublic.quest.story.PathStage;
import com.oldrepublic.quest.story.RankThree;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryMentors;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.quest.story.Turnings;
import com.oldrepublic.quest.turning.RankFour;
import com.oldrepublic.quest.turning.TurningBalance;
import com.oldrepublic.quest.turning.TurningRules;
import com.oldrepublic.quest.turning.TurningService;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.vendor.hub.HubRole;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 client test steps for the Fall, the Return and rank 4 (W5 paths and ranks; spec MILESTONE_5.md 8, 19.2).
 * {@code M5ClientTest} runs them after the foes' steps, in the shared seeded world. Screenshots are
 * {@code oldrepublic_m5_<nnn>_<what>}, 155 to 164:
 * <ul>
 * <li>155 {@code fall_offer}: a Jedi in the Fall's band; the messenger (an acolyte) walks up and makes the offer
 * ({@code turn_fall_offer}), the camera turned to them; the offer is then accepted with the real number keys;</li>
 * <li>156 {@code last_lesson}: Saelen has yielded; the player uses her with a real right-click and the Last Lesson's
 * choice opens ({@code turn_last_lesson}); she is let go with the real key;</li>
 * <li>157 {@code kneel}: Sorn at a camp fire, used with a real right-click: the rite ({@code turn_kneel});</li>
 * <li>158 {@code rite}: the rite chosen with the real key: the Fall's cold wind across the camp and the turning's banner,
 * in third person;</li>
 * <li>159 {@code master}: a Jedi ready for rank 4 uses Saelen (her blade lit), reads the letter and accepts with the real
 * keys: the blade-light across the hands, the falling motes and the banner, in third person;</li>
 * <li>160 {@code darth}: a Sith ready for rank 4 uses Sorn and takes the second name of the writ: the writ burning, in
 * third person;</li>
 * <li>161 {@code wayfarer}: an Independent ready for the Wayfarer title uses the Compass's pedestal at Courier's Fall with
 * a real right-click and takes the mantle: Tessaly Arn's figure a step aside and the dust lifting on the road;</li>
 * <li>162 to 164 {@code robe_master}, {@code robe_darth}, {@code robe_wayfarer}: the three rank 4 pieces the ceremonies
 * gave, each worn with the rest of its set (hood or cap, trousers, boots), seen from the front.</li>
 * </ul>
 *
 * <p>Shortcuts, each logged: the identity, story and Force state is set directly (the Fall's offer is made at once instead
 * of after a day in the band; the task is skipped and the confrontation starts after the duel, which is W8's; the rank 4
 * requirements are met directly); the mentors are spawned ahead of the player with their AI off, and the mentor the
 * story sends is called off (the real arrival takes 1,200 ticks and pitches a camp); a lit campfire stands for the rite's
 * camp and is removed after; Courier's Fall is placed 40 blocks behind where the player stood (out of the next steps'
 * view) and left in the world, as the M3 steps leave theirs; hostile mobs near the player are removed and the player is
 * invulnerable for the steps, since a hit ends a dialogue; the inventory is emptied for the steps so the robes have room;
 * the robes are put on directly; when it is dark the time is set to noon. When a right-click opens nothing, the dialogue
 * is opened on the server (logged), so the remaining pictures are still taken. Every check that fails is collected and
 * the steps fail at the end, after everything is put back: the story, identity, Force and skill data, the inventory and
 * the armour, invulnerability, the camera, the position, and the people and blocks the steps placed (not the Fall).
 */
public final class TurningClientSteps {
	private static final String STEP = "Turning M5 client step";
	private static final String DIALOGUE_SCREEN = "DialogueScreen";
	private static final Identifier FALL_OFFER = OldRepublicMod.id("turn_fall_offer");
	private static final Identifier LAST_LESSON = OldRepublicMod.id("turn_last_lesson");
	private static final Identifier KNEEL = OldRepublicMod.id("turn_kneel");
	private static final Identifier RANK4_SAELEN = OldRepublicMod.id("rank4_saelen");
	private static final Identifier RANK4_SORN = OldRepublicMod.id("rank4_sorn");
	private static final Identifier RANK4_COMPASS = OldRepublicMod.id("rank4_compass");
	private static final Identifier COURIERS_FALL = OldRepublicMod.id("story/couriers_fall");
	/** A Jedi's alignment in the Fall's band (at or below -50, 8.2). */
	private static final int FALLEN = -60;
	/** How far past 50 a Jedi or a Sith ready for rank 4 leans (8.5). */
	private static final int LEAN = 60;
	/** Mastery points of mastery rank 3 ({@link Progression#masteryRank}). */
	private static final int MASTERED = 400;
	/** The name taken from Sorn's writ: the second of the first page ({@code confirm_2}). */
	private static final int DARTH_NAME = 2;
	/** Mentors stand this far ahead of the player, within reach of a right-click. */
	private static final double AHEAD = 2.5;
	/** The rite's camp fire: this far ahead of the player and this far to the right, behind Sorn's shoulder. */
	private static final double FIRE_AHEAD = 3.5;
	private static final double FIRE_SIDE = 1.5;
	/** Courier's Fall is placed this far behind where the player stood, sunk as the M3 steps sink it. */
	private static final double FALL_BEHIND = 40;
	private static final int FALL_BURIED = 2;
	/** Ticks the messenger gets to walk up and make the offer (it greets after 300 ticks wherever it is). */
	private static final int MESSENGER_TIMEOUT = 360;
	/** Ticks a right-click gets to open its dialogue before the server opens it. */
	private static final int CLICK_TIMEOUT = 40;
	/** Ticks to wait for a screen opened on the server. */
	private static final int SCREEN_TIMEOUT = 100;
	/** Ticks after a dialogue choice for the server's answer. */
	private static final int CHOICE_TICKS = 8;
	/** Tessaly Arn's figure stands aside for 90 ticks after the Wayfarer's ceremony, then folds ({@code TurningFx}). */
	private static final int FIGURE_TICKS = 90;
	/** Hostile mobs this close to the player are removed before each scene. */
	private static final double HOSTILE_RANGE = 48;
	/** Messengers are looked for this close to the player. */
	private static final double MESSENGER_RANGE = 64;
	/** The camera's pitch toward a person or a point is kept within this (degrees, down is positive). */
	private static final float AIM_PITCH_MIN = -30f;
	private static final float AIM_PITCH_MAX = 50f;
	/** The player looks up a little for the robes, so the camera in front of them looks down a little. */
	private static final float ROBE_PITCH = -12f;
	/** The equipment the steps change, put back at the end. */
	private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

	/** What the steps change on the player, put back at the end. */
	private record Before(StoryData story, IdentityData identity, ForceData force, SkillData skills, List<ItemStack> items,
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

			return new Before(Story.get(p).copy(), Identity.get(p).copy(), Progression.get(p).copy(), SkillService.data(p).copy(), items, worn,
					p.isInvulnerable(), p.position(), p.getYRot(), p.getXRot());
		}

		void restore(ServerPlayer p) {
			Story.commit(p, story.copy());
			Identity.commit(p, identity.copy());
			Progression.commit(p, force.copy());
			SkillService.commit(p, skills.copy());
			SkillService.refresh(p);
			Inventory inventory = p.getInventory();
			inventory.clearContent();

			for (int i = 0; i < items.size() && i < inventory.getContainerSize(); i++) {
				inventory.setItem(i, items.get(i).copy());
			}

			worn.forEach((slot, stack) -> p.setItemSlot(slot, stack.copy()));
			p.setInvulnerable(invulnerable);
		}

		/** The direction the player faced at the start, flat. */
		Vec3 facing() {
			double yaw = Math.toRadians(yRot);
			return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		}

		String teleport() {
			return String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.1f %.1f", position.x, position.y, position.z, yRot, xRot);
		}
	}

	/** The camera's yaw and pitch toward someone or something; {@code found} false when there was nothing to look at. */
	private record Aim(boolean found, float yaw, float pitch) {
		static final Aim NONE = new Aim(false, 0f, 0f);
	}

	/** The Compass's pedestal at Courier's Fall and a free spot before it. */
	private record Fall(BlockPos pedestal, BlockPos stand) {
	}

	/** A block the steps placed, with what stood there before. */
	private record Placed(ServerLevel level, BlockPos pos, BlockState before) {
	}

	/** What the steps spawned (touched on the server thread only), removed after each scene and at the end. */
	private static final List<Entity> SPAWNED = new ArrayList<>();
	/** Blocks the steps placed (server thread only), put back after their scene and at the end. */
	private static final List<Placed> PLACED = new ArrayList<>();
	/** Failed checks (test thread only), reported together once everything is put back. */
	private static final List<String> FAILURES = new ArrayList<>();

	/** Runs the turning's and rank 4's steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		FAILURES.clear();
		server.runOnServer(s -> {
			SPAWNED.clear();
			PLACED.clear();
		});
		Before before = server.computeOnServer(s -> Before.of(player(s)));
		CameraType camera = context.computeOnClient(mc -> mc.options.getCameraType());

		try {
			setUp(context, server);
			fallOffer(context, server, before);
			lastLesson(context, server);
			rite(context, server);
			master(context, server, before);
			darth(context, server, before);
			wayfarer(context, server, before);
			robes(context, server, before);
		} finally {
			context.setScreen(() -> null);
			context.runOnClient(mc -> mc.options.setCameraType(camera));
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Dialogues.close(p);
				StoryMentors.cancelComing(p);
				discardSpawned();
				removeMessengers(p);
				restoreBlocks();
				before.restore(p);
			});
			server.runCommand(before.teleport());
			context.waitTicks(20);
			OldRepublicMod.LOGGER.info("{}s: story, identity, Force and skill data, inventory and armour, invulnerability, camera and position put back; "
					+ "the people and blocks placed for the scenes removed (Courier's Fall stays)", STEP);
		}

		if (!FAILURES.isEmpty()) {
			throw new AssertionError(STEP + "s: " + FAILURES.size() + " check(s) failed: " + String.join("; ", FAILURES));
		}
	}

	/** Empties the inventory, makes the player invulnerable and clears the hostiles; noon when it is dark. */
	private static void setUp(ClientGameTestContext context, TestServerContext server) {
		boolean dark = server.computeOnServer(s -> {
			ServerPlayer p = player(s);

			if (p.level() != s.overworld()) {
				OldRepublicMod.LOGGER.error("{}: the player is not on the home world ({}); the offer's messenger and the Compass need it", STEP,
						p.level().dimension());
			}

			if (p.isPassenger()) {
				OldRepublicMod.LOGGER.warn("{}: the player was still riding {}; dismounted for the steps", STEP, p.getVehicle());
				p.stopRiding();
			}

			p.getInventory().clearContent();
			p.setInvulnerable(true);
			clearHostiles(p);
			OldRepublicMod.LOGGER.info("{}: the inventory is emptied and the player invulnerable for the steps, a hit ending a dialogue (shortcut; put back "
					+ "afterwards)", STEP);
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

	// ---------------------------------------------------------------- 155 the Fall's offer

	/** 155: the messenger comes to a Jedi in the band and makes the offer; accepted with the keys 2 then 1. */
	private static void fallOffer(ClientGameTestContext context, TestServerContext server, Before before) {
		boolean offered = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			become(p, before, Path.JEDI, 2, FALLEN);
			clearHostiles(p);
			boolean made = TurningService.offer(p, TurningKind.FALL);
			OldRepublicMod.LOGGER.info("{}: {} is a rank 2 Jedi at alignment {} with Chapters Two to Five complete; the Fall is offered now instead of "
					+ "after a day in the band (shortcut): {}", STEP, p.getName().getString(), FALLEN, made);
			return made;
		});
		check(offered ? null : "the Fall could not be offered to a Jedi at alignment " + FALLEN);

		if (!waitForScreen(context, DIALOGUE_SCREEN, MESSENGER_TIMEOUT)) {
			OldRepublicMod.LOGGER.warn("{}: no messenger made the offer within {} ticks; an acolyte is spawned ahead and the offer opened on the server "
					+ "(fallback)", STEP, MESSENGER_TIMEOUT);
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				HubNpc acolyte = spawnHubAhead(p, HubRole.ACOLYTE, "acolyte_0");

				if (acolyte == null || !Dialogues.open(p, FALL_OFFER, acolyte)) {
					OldRepublicMod.LOGGER.error("{}: the Fall's offer could not be opened", STEP);
				}
			});
			waitForScreen(context, DIALOGUE_SCREEN, SCREEN_TIMEOUT);
		}

		lookAt(context, server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Entity speaker = offerSpeaker(p);
			return speaker == null ? Aim.NONE : aim(p, speaker.getEyePosition());
		}));
		context.waitTicks(4);

		if (!at(context, server, FALL_OFFER, "offer")) {
			return;
		}

		reveal(context);
		shot(context, DIALOGUE_SCREEN, "oldrepublic_m5_155_fall_offer");
		// 2: "I will do it" (the confirmation), then 1: yes.
		choose(context, 2);

		if (at(context, server, FALL_OFFER, "accept_confirm")) {
			choose(context, 1);
			check(server.computeOnServer(s -> {
				StoryData.Turning t = Story.get(player(s)).turning;
				return t.kind() == TurningKind.FALL && t.stage() == TurningStage.TASK ? null
						: "accepting the offer with the keys 2 then 1 did not start the Fall's task: " + describe(t);
			}));
		}

		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	// ---------------------------------------------------------------- 156 the Last Lesson

	/** 156: Saelen has yielded; used with a right-click, the Last Lesson's choice opens; she is let go (key 1). */
	private static void lastLesson(ClientGameTestContext context, TestServerContext server) {
		UUID saelen = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			clearHostiles(p);
			setTurning(p, TurningKind.FALL, TurningStage.CONFRONTATION, TurningRules.CONFRONT_YIELDED);
			MentorEntity mentor = spawnMentor(p, MentorRole.JEDI, false);
			OldRepublicMod.LOGGER.info("{}: the Fall's task is skipped and Saelen's duel taken as yielded (shortcut: the duel is W8's MentorDuel); "
					+ "Saelen stands ahead", STEP);
			return mentor == null ? null : mentor.getUUID();
		});

		if (saelen == null) {
			fail("Saelen could not be spawned for the Last Lesson");
		} else if (use(context, server, saelen, LAST_LESSON) && at(context, server, LAST_LESSON, "yielded")) {
			reveal(context);
			shot(context, DIALOGUE_SCREEN, "oldrepublic_m5_156_last_lesson");
			// 1: let her go.
			choose(context, 1);
			check(server.computeOnServer(s -> {
				StoryData.Turning t = Story.get(player(s)).turning;
				return t.stage() == TurningStage.RITE ? null : "letting Saelen go with the key 1 did not lead to the rite: " + describe(t);
			}));
		}

		// Sorn sets out for the rite with a new camp; the next scene brings him instead.
		server.runOnServer(s -> StoryMentors.cancelComing(player(s)));
		context.setScreen(() -> null);
		server.runOnServer(s -> discardSpawned());
		context.waitTicks(5);
	}

	// ---------------------------------------------------------------- 157 and 158 the rite

	/** 157: Sorn at a camp fire, used with a right-click: the rite opens. 158: chosen (key 2), the rite's wind and banner. */
	private static void rite(ClientGameTestContext context, TestServerContext server) {
		UUID sorn = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			clearHostiles(p);
			StoryMentors.cancelComing(p);
			setTurning(p, TurningKind.FALL, TurningStage.RITE, TurningRules.RITE_HERE);
			placeFire(p);
			MentorEntity mentor = spawnMentor(p, MentorRole.SITH, false);
			OldRepublicMod.LOGGER.info("{}: Sorn stands ahead by a camp fire as if he had come for the rite (shortcut: his arrival takes 1,200 ticks "
					+ "and pitches a camp)", STEP);
			return mentor == null ? null : mentor.getUUID();
		});

		if (sorn == null) {
			fail("Sorn could not be spawned for the rite");
		} else if (use(context, server, sorn, KNEEL) && at(context, server, KNEEL, "kneel")) {
			reveal(context);
			shot(context, DIALOGUE_SCREEN, "oldrepublic_m5_157_kneel");
			// 2: kneel; the rite is held and the dialogue goes on to Sorn's last line, closed here for the picture.
			choose(context, 2);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.setScreen(() -> null);
			camera(context, CameraType.THIRD_PERSON_BACK);
			context.waitTicks(12);
			shot(context, null, "oldrepublic_m5_158_rite");
			check(server.computeOnServer(s -> {
				ServerPlayer p = player(s);
				StoryData.Turning t = Story.get(p).turning;
				boolean turned = Turnings.done(p, TurningKind.FALL) && Identity.path(p) == Path.SITH && t.stage() == TurningStage.COMPLETE;
				return turned ? null : "the rite did not turn the Jedi: path " + Identity.path(p).id() + ", " + describe(t);
			}));
			context.waitTicks(30);
			camera(context, CameraType.FIRST_PERSON);
		}

		context.setScreen(() -> null);
		server.runOnServer(s -> {
			discardSpawned();
			restoreBlocks();
		});
		context.waitTicks(5);
	}

	// ---------------------------------------------------------------- 159 to 161 the ceremonies

	/** 159: a Jedi ready for rank 4 uses Saelen: the letter (key 1), the Master's ceremony (key 1). */
	private static void master(ClientGameTestContext context, TestServerContext server, Before before) {
		UUID saelen = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			become(p, before, Path.JEDI, 3, LEAN);
			readyForRankFour(p, Path.JEDI);
			clearHostiles(p);
			MentorEntity mentor = spawnMentor(p, MentorRole.JEDI, true);
			OldRepublicMod.LOGGER.info("{}: {} is a rank 3 Jedi meeting every rank 4 requirement (shortcut); Saelen stands ahead, her blade lit", STEP,
					p.getName().getString());
			return mentor == null ? null : mentor.getUUID();
		});

		if (saelen == null) {
			fail("Saelen could not be spawned for the Master's ceremony");
		} else if (use(context, server, saelen, RANK4_SAELEN) && at(context, server, RANK4_SAELEN, "letter")) {
			// 1: read the letter; 1: accept.
			choose(context, 1);

			if (at(context, server, RANK4_SAELEN, "master")) {
				choose(context, 1);
				ceremonyShot(context, server, Path.JEDI, "oldrepublic_m5_159_master");
			}
		}

		endScene(context, server);
	}

	/** 160: a Sith ready for rank 4 uses Sorn: the names (key 1), the second name (key 2), taken (key 1). */
	private static void darth(ClientGameTestContext context, TestServerContext server, Before before) {
		UUID sorn = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			become(p, before, Path.SITH, 3, -LEAN);
			readyForRankFour(p, Path.SITH);
			clearHostiles(p);
			MentorEntity mentor = spawnMentor(p, MentorRole.SITH, false);
			OldRepublicMod.LOGGER.info("{}: {} is a rank 3 Sith meeting every rank 4 requirement (shortcut); Sorn stands ahead", STEP,
					p.getName().getString());
			return mentor == null ? null : mentor.getUUID();
		});

		if (sorn == null) {
			fail("Sorn could not be spawned for the Darth's ceremony");
		} else if (use(context, server, sorn, RANK4_SORN) && at(context, server, RANK4_SORN, "writ")) {
			choose(context, 1);

			if (at(context, server, RANK4_SORN, "names")) {
				choose(context, DARTH_NAME);

				if (at(context, server, RANK4_SORN, "confirm_" + DARTH_NAME)) {
					choose(context, 1);
					ceremonyShot(context, server, Path.SITH, "oldrepublic_m5_160_darth");
				}
			}
		}

		endScene(context, server);
	}

	/** The ceremony's picture from behind the player, then its checks. */
	private static void ceremonyShot(ClientGameTestContext context, TestServerContext server, Path path, String name) {
		camera(context, CameraType.THIRD_PERSON_BACK);
		context.waitTicks(6);
		shot(context, null, name);
		check(server.computeOnServer(s -> afterCeremony(player(s), path)));
		context.waitTicks(40);
		camera(context, CameraType.FIRST_PERSON);
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

	/**
	 * 161: an Independent ready for the Wayfarer title uses the Compass's pedestal at Courier's Fall (a real right-click):
	 * Tessaly Arn's echo, what now (key 1), the mantle (key 1); the figure steps aside and the road's dust lifts.
	 */
	private static void wayfarer(ClientGameTestContext context, TestServerContext server, Before before) {
		Fall fall = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			become(p, before, Path.INDEPENDENT, 0, 0);
			readyForRankFour(p, Path.INDEPENDENT);
			clearHostiles(p);
			Fall placed = placeFall(p, before);
			StoryData story = Story.get(p);
			story.chapter3 = ChapterThreeRules.withFall(story.chapter3, placed.pedestal());
			Story.commit(p, story);
			OldRepublicMod.LOGGER.info("{}: {} is an Independent with the Pathfinder's title meeting every Wayfarer requirement (shortcut), the fall's "
					+ "pedestal at {}", STEP, p.getName().getString(), placed.pedestal());
			return placed;
		});
		Vec3 top = Vec3.atBottomCenterOf(fall.pedestal()).add(0, 1, 0);
		BlockPos stand = fall.stand();
		server.runCommand(String.format(Locale.ROOT, "tp @p %.1f %d %.1f facing %.1f %.1f %.1f", stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5,
				top.x, top.y + 0.8, top.z));
		context.waitTicks(30);
		server.runOnServer(s -> clearHostiles(player(s)));
		context.getInput().lookAt(fall.pedestal());
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);

		if (!waitForScreen(context, DIALOGUE_SCREEN, CLICK_TIMEOUT)) {
			OldRepublicMod.LOGGER.warn("{}: the right-click on the Compass's pedestal at {} opened nothing; Tessaly Arn's figure and rank4_compass are "
					+ "opened on the server (fallback)", STEP, fall.pedestal());
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				EffectEvents.around(p.level(), QuestEffects.RANK_CEREMONY, top, null, p.getId(), 1f, QuestEffects.RANK_PATHFINDER);

				if (!Dialogues.open(p, RANK4_COMPASS, null)) {
					OldRepublicMod.LOGGER.error("{}: rank4_compass could not be opened", STEP);
				}
			});
			waitForScreen(context, DIALOGUE_SCREEN, SCREEN_TIMEOUT);
		}

		if (at(context, server, RANK4_COMPASS, "echo")) {
			lookAt(context, server.computeOnServer(s -> aim(player(s), top.add(0, 0.8, 0))));
			// 1: what now; 1: take the mantle.
			choose(context, 1);

			if (at(context, server, RANK4_COMPASS, "mantle")) {
				choose(context, 1);
				// The figure forms again a step aside 20 ticks after the ceremony, and the dust lifts along the road.
				context.waitTicks(24);
				shot(context, null, "oldrepublic_m5_161_wayfarer");
				check(server.computeOnServer(s -> afterCeremony(player(s), Path.INDEPENDENT)));
			}
		}

		context.setScreen(() -> null);
		// The figure folds back before the steps go on.
		context.waitTicks(FIGURE_TICKS);
		server.runCommand(before.teleport());
		context.waitTicks(20);
	}

	// ---------------------------------------------------------------- 162 to 164 the robes

	/** 162 to 164: each rank 4 piece the ceremonies gave, worn with the rest of its set, from the front. */
	private static void robes(ClientGameTestContext context, TestServerContext server, Before before) {
		server.runOnServer(s -> clearHostiles(player(s)));
		camera(context, CameraType.THIRD_PERSON_FRONT);
		context.getInput().lookAt(before.yRot(), ROBE_PITCH);
		robe(context, server, RankFour.MASTER_ROBE, ArmourSet.JEDI_ROBES, "oldrepublic_m5_162_robe_master");
		robe(context, server, RankFour.DARTH_ROBE, ArmourSet.SITH_ROBES, "oldrepublic_m5_163_robe_darth");
		robe(context, server, RankFour.WAYFARER_MANTLE, ArmourSet.SPACER, "oldrepublic_m5_164_robe_wayfarer");
		camera(context, CameraType.FIRST_PERSON);
	}

	private static void robe(ClientGameTestContext context, TestServerContext server, String robe, ArmourSet set, String name) {
		String problem = server.computeOnServer(s -> wear(player(s), robe, set));
		context.waitTicks(12);
		shot(context, null, name);
		check(problem);
	}

	/**
	 * Puts on the rank 4 piece {@code robe} the ceremony gave (a new one when it gave none) with the head, legs and feet of
	 * {@code set}. Returns what was wrong, or null.
	 */
	private static @Nullable String wear(ServerPlayer p, String robe, ArmourSet set) {
		Item item = item(robe);

		if (item == null) {
			return "the rank 4 piece " + robe + " is not registered (W7)";
		}

		ItemStack given = carried(p, item);
		p.setItemSlot(EquipmentSlot.HEAD, ArmourSets.piece(set, EquipmentSlot.HEAD));
		p.setItemSlot(EquipmentSlot.CHEST, given.isEmpty() ? new ItemStack(item) : given.copy());
		p.setItemSlot(EquipmentSlot.LEGS, ArmourSets.piece(set, EquipmentSlot.LEGS));
		p.setItemSlot(EquipmentSlot.FEET, ArmourSets.piece(set, EquipmentSlot.FEET));
		OldRepublicMod.LOGGER.info("{}: {} put on with the {} set's other pieces (shortcut)", STEP, robe, set);
		return given.isEmpty() ? "no ceremony gave the " + robe : null;
	}

	// ---------------------------------------------------------------- the player's state

	/**
	 * Puts the player's data back to the start, then makes them a {@code path} player at {@code rank} and
	 * {@code alignment}, introduced, awakened, with the Crossing decided and Chapters Two to Five complete (Chapter Four's
	 * placements marked done, its back pay settled), and no turning.
	 */
	private static void become(ServerPlayer p, Before before, Path path, int rank, int alignment) {
		IdentityData identity = before.identity().copy();
		identity.path = path;
		identity.rank = rank;
		identity.alignment = alignment;
		identity.choices.add(Identity.INTRODUCED);
		Identity.commit(p, identity);
		StoryData story = before.story().copy();
		story.crossingStage = CrossingStage.DECIDED.ordinal();
		story.path = path.id();

		if (path == Path.JEDI || path == Path.SITH) {
			story.pathStage = PathStage.COMPLETE.ordinal();
		}

		story.chapter3 = ChapterThreeRules.withStage(story.chapter3, ChapterThreeStage.COMPLETE);
		story.chapter4 = story.chapter4.withStage(ChapterFourStage.COMPLETE, 0L).withBackPay();
		story.chapter5 = story.chapter5.withStage(ChapterFiveStage.COMPLETE, -1L);
		story.flags.add(ChapterFourRules.HULK_PLACED);
		story.flags.add(ChapterFourRules.CHALLENGE_PLAYED);
		story.turning = StoryData.Turning.NEW;
		Story.commit(p, story);
		ForceData force = before.force().copy();
		force.questStage = Math.max(force.questStage, QuestStage.COMPLETE.ordinal());
		Progression.commit(p, force);
		SkillService.commit(p, before.skills().copy());
		SkillService.refresh(p);
	}

	/**
	 * Meets every rank 4 requirement of {@code path} (8.5): level 20, two powers at mastery rank 3, two deeds (five bounties,
	 * five contracts); a Jedi or a Sith with rank 3 done, trust 90 and the order at 800; an Independent with the
	 * Pathfinder's title and three known factions at Trusted.
	 */
	public static void readyForRankFour(ServerPlayer p, Path path) {
		TurningBalance b = TurningService.balance();
		boolean ordered = path == Path.JEDI || path == Path.SITH;
		IdentityData identity = Identity.get(p);

		if (ordered) {
			identity.trust.put(MentorRole.of(path).id(), b.rank4Trust);
		} else {
			identity.choices.add(RankThree.TITLE_PATHFINDER);
		}

		Identity.commit(p, identity);
		Faction own = TurningRules.factionOf(path);

		if (own != null) {
			Identity.setReputation(p, own, b.rank4Rep);
		} else {
			for (Faction faction : List.of(Faction.REPUBLIC, Faction.FRONTIER, Faction.JEDI_ORDER)) {
				Identity.setReputation(p, faction, b.wayfarerTier);
			}
		}

		ForceData force = Progression.get(p);
		force.level = Math.max(force.level, b.rank4Level);
		force.mastery.put(SkillIds.FORCE_PUSH, MASTERED);
		force.mastery.put(SkillIds.FORCE_SPEED, MASTERED);
		Progression.commit(p, force);
		StoryData story = Story.get(p);
		// Rank 3 done, so the rank 3 ceremony (the mentor's, or the Pathfinder's at the same pedestal) does not answer first.
		story.flags.add(ordered ? ChapterThreeRules.RANK3_DONE : ChapterThreeRules.PATHFINDER_DONE);
		story.counters.put(ChapterFive.BOUNTIES, b.deedBounties);
		story.counters.put(ChapterFive.CONTRACTS, b.deedContracts);
		Story.commit(p, story);
		SkillService.refresh(p);
	}

	private static void setTurning(ServerPlayer p, TurningKind kind, TurningStage stage, int task) {
		StoryData story = Story.get(p);
		story.turning = story.turning.withStage(kind, stage).withTask(task);
		Story.commit(p, story);
	}

	/** What a ceremony left out (null when it gave everything): the rank or the title, the power, the name, the robe. */
	public static @Nullable String afterCeremony(ServerPlayer p, Path path) {
		List<String> missing = new ArrayList<>();

		if (!RankFour.done(p)) {
			missing.add(TurningRules.RANK4_DONE);
		}

		if (path != Path.INDEPENDENT && Identity.rank(p) != 4) {
			missing.add("rank 4 (rank " + Identity.rank(p) + ")");
		}

		String power = path == Path.JEDI ? TurningRules.FORCE_VALOR : path == Path.SITH ? TurningRules.FORCE_RAGE : SkillIds.WAYFARERS_STRIDE;

		if (!SkillService.data(p).owns(power)) {
			missing.add(power);
		}

		if (path == Path.SITH && !Identity.hasChoice(p, TurningRules.darthChoice(DARTH_NAME))) {
			missing.add("the Darth name " + DARTH_NAME);
		}

		if (path == Path.INDEPENDENT && !Identity.hasChoice(p, SkillService.WAYFARER_TITLE)) {
			missing.add(SkillService.WAYFARER_TITLE);
		}

		String robe = path == Path.JEDI ? RankFour.MASTER_ROBE : path == Path.SITH ? RankFour.DARTH_ROBE : RankFour.WAYFARER_MANTLE;
		Item item = item(robe);

		if (item == null || carried(p, item).isEmpty()) {
			missing.add("the robe " + robe);
		}

		return missing.isEmpty() ? null : "the " + path.id() + " ceremony left out " + String.join(", ", missing) + " (" + why(p) + ")";
	}

	private static @Nullable Item item(String id) {
		return BuiltInRegistries.ITEM.getOptional(OldRepublicMod.id(id)).orElse(null);
	}

	/** The first stack of {@code item} in the inventory, or empty. */
	private static ItemStack carried(ServerPlayer p, Item item) {
		Inventory inventory = p.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(item)) {
				return inventory.getItem(i);
			}
		}

		return ItemStack.EMPTY;
	}

	// ---------------------------------------------------------------- people and places

	/**
	 * The player's own {@code role} mentor, {@link #AHEAD} blocks ahead and facing them, with its AI off so it holds still.
	 * The story points at it before it enters the world, so it is not taken for a stray copy.
	 */
	private static @Nullable MentorEntity spawnMentor(ServerPlayer p, MentorRole role, boolean bladeLit) {
		ServerLevel level = p.level();
		Vec3 feet = standAhead(level, p, AHEAD);
		MentorEntity mentor = MentorEntities.type(role).create(level, EntitySpawnReason.COMMAND);

		if (mentor == null) {
			OldRepublicMod.LOGGER.error("{}: the {} mentor could not be created", STEP, role.id());
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
		story.setMentor(role.id(), mentor.getUUID());
		Story.commit(p, story);
		level.addFreshEntity(mentor);
		mentor.setBladeLit(bladeLit);
		SPAWNED.add(mentor);
		OldRepublicMod.LOGGER.info("{}: the {} mentor spawned at {} with its AI off (shortcut; removed after the scene)", STEP, role.id(),
				BlockPos.containing(feet));
		return mentor;
	}

	/** A hub person of {@code role} three blocks ahead of the player, facing them; null when none could be spawned. */
	private static @Nullable HubNpc spawnHubAhead(ServerPlayer p, HubRole role, String variant) {
		ServerLevel level = p.level();
		Vec3 feet = standAhead(level, p, 3.0);
		HubNpc npc = HubNpcs.spawn(level, BlockPos.containing(feet), role, variant);

		if (npc == null) {
			OldRepublicMod.LOGGER.error("{}: the {} '{}' could not be spawned", STEP, role, variant);
			return null;
		}

		float yaw = yawToward(feet, p.position());
		npc.snapTo(feet.x, feet.y, feet.z, yaw, 0f);
		npc.setYHeadRot(yaw);
		npc.setYBodyRot(yaw);
		SPAWNED.add(npc);
		return npc;
	}

	/** Who makes the offer: the player's messenger, else the acolyte spawned in its place. */
	private static @Nullable Entity offerSpeaker(ServerPlayer p) {
		List<HubNpc> messengers = messengers(p);

		if (!messengers.isEmpty()) {
			return messengers.getFirst();
		}

		for (Entity entity : SPAWNED) {
			if (entity instanceof HubNpc && entity.isAlive()) {
				return entity;
			}
		}

		return null;
	}

	private static List<HubNpc> messengers(ServerPlayer p) {
		return p.level().getEntitiesOfClass(HubNpc.class, p.getBoundingBox().inflate(MESSENGER_RANGE),
				npc -> npc.isAlive() && npc.entityTags().contains(TurningService.MESSENGER_TAG) && StoryTags.isOwnedBy(npc, p.getUUID()));
	}

	/** Removes the player's messenger if it is still about (it walks off and leaves by itself once answered). */
	private static void removeMessengers(ServerPlayer p) {
		messengers(p).forEach(Entity::discard);
	}

	/** A lit campfire behind Sorn's shoulder for the rite's camp (logged; put back after the rite). */
	private static void placeFire(ServerPlayer p) {
		ServerLevel level = p.level();
		Vec3 look = flatLook(p);
		Vec3 right = new Vec3(-look.z, 0, look.x);
		BlockPos pos = standNear(level, p.position().add(look.scale(FIRE_AHEAD)).add(right.scale(FIRE_SIDE)), p.getBlockY());

		if (!level.getBlockState(pos).isAir()) {
			OldRepublicMod.LOGGER.info("{}: no room for the rite's camp fire at {}; the rite goes on without one", STEP, pos);
			return;
		}

		place(level, pos, Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
		OldRepublicMod.LOGGER.info("{}: a lit campfire placed at {} for the rite's camp (shortcut; removed after)", STEP, pos);
	}

	/**
	 * Places Courier's Fall {@link #FALL_BEHIND} blocks behind where the player stood at the start (as the M3 steps place
	 * it; it stays in the world). A stone stands in for the Compass's pedestal when the template cannot be placed.
	 */
	private static Fall placeFall(ServerPlayer p, Before before) {
		ServerLevel level = p.level();
		BlockPos centre = ground(level, before.position().subtract(before.facing().scale(FALL_BEHIND)));
		BlockPos pedestal;

		try {
			RuntimeSites.Placement placement = RuntimeSites.placeAt(level, COURIERS_FALL, centre, Rotation.NONE, FALL_BURIED, p.getUUID());
			BlockPos found = SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.PEDESTAL_WAYFARER);
			pedestal = found != null ? found : placement.origin();
			OldRepublicMod.LOGGER.info("{}: placed Courier's Fall at {} (pedestal {}) for the Wayfarer's ceremony; it stays in the world", STEP,
					placement.origin(), pedestal);
		} catch (IllegalArgumentException | IllegalStateException e) {
			pedestal = ground(level, p.position().add(flatLook(p).scale(4)));
			place(level, pedestal, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			OldRepublicMod.LOGGER.error("{}: Courier's Fall could not be placed at {}; a stone at {} stands in for the Compass's pedestal", STEP, centre,
					pedestal, e);
		}

		return new Fall(pedestal, standBefore(p, pedestal));
	}

	/**
	 * A free spot 4 (else 3, else 2) blocks from the pedestal on solid ground, on any side, from which the pedestal is in plain sight
	 * (the fall's hull hides it from some sides, and a click from there hits the hull); else the ground 4 blocks south.
	 */
	private static BlockPos standBefore(ServerPlayer p, BlockPos pedestal) {
		ServerLevel level = p.level();

		for (int reach : new int[] {4, 3, 2}) {
			for (int[] side : new int[][] {{0, 1}, {1, 0}, {0, -1}, {-1, 0}}) {
				BlockPos spot = pedestal.offset(side[0] * reach, 0, side[1] * reach);

				if (level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir() && !level.getBlockState(spot.below()).isAir()
						&& inSight(p, spot, pedestal)) {
					return spot;
				}
			}
		}

		OldRepublicMod.LOGGER.warn("{}: no spot near the pedestal at {} sees it; standing 4 blocks south", STEP, pedestal);
		return ground(level, Vec3.atCenterOf(pedestal.offset(0, 0, 4)));
	}

	/** Whether a player standing at {@code spot} sees the pedestal with nothing in between. */
	private static boolean inSight(ServerPlayer p, BlockPos spot, BlockPos pedestal) {
		Vec3 eye = Vec3.atBottomCenterOf(spot).add(0, p.getEyeHeight(), 0);
		BlockHitResult hit = p.level().clip(new ClipContext(eye, Vec3.atCenterOf(pedestal), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pedestal);
	}

	private static void place(ServerLevel level, BlockPos pos, BlockState state) {
		PLACED.add(new Placed(level, pos.immutable(), level.getBlockState(pos)));
		level.setBlock(pos, state, Block.UPDATE_ALL);
	}

	/** Puts back every block the steps placed, the last first. */
	private static void restoreBlocks() {
		for (int i = PLACED.size() - 1; i >= 0; i--) {
			Placed placed = PLACED.get(i);
			placed.level().setBlock(placed.pos(), placed.before(), Block.UPDATE_ALL);
		}

		PLACED.clear();
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

	/** Where someone can stand {@code distance} ahead of the player, near the player's height (else on the ground). */
	private static Vec3 standAhead(ServerLevel level, ServerPlayer p, double distance) {
		Vec3 at = p.position().add(flatLook(p).scale(distance));
		BlockPos feet = standNear(level, at, p.getBlockY());
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
	 * dialogue is opened on the server with the target as the speaker (logged). Returns whether a dialogue screen is open.
	 */
	private static boolean use(ClientGameTestContext context, TestServerContext server, UUID target, Identifier dialogue) {
		context.setScreen(() -> null);
		Aim aim = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Entity entity = p.level().getEntity(target);
			return entity == null ? Aim.NONE : aim(p, entity.getEyePosition());
		});

		if (!aim.found()) {
			fail("the speaker of " + dialogue.getPath() + " is gone");
			return false;
		}

		lookAt(context, aim);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);

		if (waitForScreen(context, DIALOGUE_SCREEN, CLICK_TIMEOUT)) {
			return true;
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
			return true;
		}

		fail(dialogue.getPath() + " did not open (" + server.computeOnServer(s -> why(player(s))) + ")");
		return false;
	}

	/** Whether {@code dialogue} is open at {@code node} on the server; else the failure is noted and the dialogue closed. */
	private static boolean at(ClientGameTestContext context, TestServerContext server, Identifier dialogue, String node) {
		String want = dialogue.getPath() + "/" + node;
		String where = server.computeOnServer(s -> {
			DialogueSession session = Dialogues.session(player(s));
			return session == null ? null : session.dialogue().getPath() + "/" + session.node();
		});

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

	/** The turning's record, the path, rank 4's first unmet requirement and what last hurt the player: why a step stalled. */
	private static String why(ServerPlayer p) {
		String blocker = RankFour.blocker(p);
		return "path " + Identity.path(p).id() + ", turning " + describe(Story.get(p).turning) + ", rank 4 " + (blocker == null ? "ready" : blocker) + ", "
				+ lastHurt(p);
	}

	private static String describe(StoryData.Turning t) {
		return t.kind().id() + " " + t.stage().id() + " task " + t.task();
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

	private TurningClientSteps() {
	}
}
