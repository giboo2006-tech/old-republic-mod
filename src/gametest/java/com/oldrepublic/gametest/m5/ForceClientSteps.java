package com.oldrepublic.gametest.m5;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.client.ability.AbilityInput;
import com.oldrepublic.client.force.DrainFx;
import com.oldrepublic.client.force.FearFx;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.client.force.MindTrickFx;
import com.oldrepublic.client.force.RageFx;
import com.oldrepublic.client.force.StasisFx;
import com.oldrepublic.client.force.ValorFx;
import com.oldrepublic.client.skill.ForceScreen;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.force.ForceM5Feature;
import com.oldrepublic.force.ForceMobEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.drain.ForceDrain;
import com.oldrepublic.force.fear.ForceFear;
import com.oldrepublic.force.fear.Frightened;
import com.oldrepublic.force.mindtrick.MindTrick;
import com.oldrepublic.force.mindtrick.Pacified;
import com.oldrepublic.force.rage.ForceRage;
import com.oldrepublic.force.stasis.ForceStasis;
import com.oldrepublic.force.stasis.StasisHolds;
import com.oldrepublic.force.valor.ForceValor;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.Skills;

import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 client test steps for the second power bar and the six powers (W6). {@code M5ClientTest} runs them in the
 * shared seeded world (spec MILESTONE_5.md 19.2). Screenshots are {@code oldrepublic_m5_<nnn>_<what>}, 165 to 173 (174 is
 * left free):
 * <ul>
 * <li>165 {@code second_bar_focus_held}: both bars filled (eight powers), Focus held, so the second bar is lit and the first
 * dimmed;</li>
 * <li>166 {@code skills_screen_eight_sockets}: the Force screen on the Exploration tree with Mind Trick selected, the
 * eight-socket strip under the tree;</li>
 * <li>167 {@code stasis_on_trooper}: a Sith trooper 6 blocks ahead held in Stasis, the rime settled on it, from behind the
 * caster;</li>
 * <li>168 {@code drain_stream}: Drain on a crystal poacher after its first pulse, the dark motes streaming to the caster's
 * hand;</li>
 * <li>169 {@code fear_victims}: three poachers in Fear's cone turning to run, shivering;</li>
 * <li>170 {@code mind_trick_calm}: a Republic trooper 5 blocks ahead under Mind Trick, calm, in first person;</li>
 * <li>171 {@code rage_embers}: the caster in Rage with a lit saber, seen from the front: the posture, the embers off the
 * blade and the heat haze;</li>
 * <li>172 {@code rage_vignette}: the same rage in first person, the dark-red edge of the view;</li>
 * <li>173 {@code valor_two_players}: Valor cast with a second player beside the caster, the warm gust round both, from the
 * front.</li>
 * </ul>
 *
 * <p>The steps build a stone stage 40 blocks above wherever the player stands, in the player's own level, with a rail two
 * blocks high round its edge, and stand the player on it facing south. Shortcuts, each logged: the player is made
 * awakened, raised to level 20 and rank 4 (both bars open) and taught the powers the shots use, which are put in the
 * eight slots directly; energy and cooldowns are refilled before each power; the powers are started on the server as
 * their key would ({@code onInput}); the foes are spawned with their AI off so they hold still (Fear's victims get it back
 * as the cast lands, so they can run); the second player is a mock player on a connection with no client behind it (as
 * {@code GameTestHelper#makeMockServerPlayerInLevel} makes them), logged out after its picture. Difficulty is raised from
 * Peaceful to Normal for the steps when it was Peaceful. The server confirms each step did what its screenshot claims,
 * and the client waits until it has seen each power's event. Everything is put back at the end: the foes, the second
 * player, the stage, the player's Force, skill, identity and story data, the held item, the powers' effects, health,
 * camera and position; the second player's join line in the chat is waited out, so the next steps' pictures are clean.
 */
public final class ForceClientSteps {
	/** Height of the stage above the player's starting position. */
	private static final int LIFT = 40;
	/** The stage's half-width, depth behind the spot and length ahead of it (south, +Z), and the air cleared above it. */
	private static final int HALF_WIDTH = 10;
	private static final int BACK = 6;
	private static final int FRONT = 16;
	private static final int HEADROOM = 10;
	/** Tag of everything the steps spawn, so cleanup never touches anyone else's entities. */
	private static final String TAG = "or_m5_powers";
	/** The second player's name. */
	private static final String ALLY_NAME = "Valor_Ally";
	/** The level and rank the player is raised to: every new power's level, and rank 4 opens the whole second bar (9.1). */
	private static final int LEVEL = 20;
	private static final int RANK = 4;
	/** The powers taught for the steps (Force Sense shows the Exploration tab of picture 166). */
	private static final List<String> TAUGHT = List.of(SkillIds.FORCE_PUSH, SkillIds.FORCE_SPEED, SkillIds.FORCE_SENSE, ForceStasis.ID, ForceValor.ID,
			ForceDrain.ID, ForceFear.ID, ForceRage.ID, MindTrick.ID);
	/** The two bars as the steps fill them: slots 1 to 4, then the second bar's 5 to 8. */
	private static final List<String> BARS = List.of(SkillIds.FORCE_PUSH, SkillIds.FORCE_SPEED, MindTrick.ID, ForceRage.ID, ForceStasis.ID,
			ForceValor.ID, ForceDrain.ID, ForceFear.ID);
	/** The effects the powers put on the player, taken off between steps and at the end. */
	private static final List<Holder<MobEffect>> EFFECTS = List.of(ForceMobEffects.STASIS, ForceMobEffects.FEAR, ForceMobEffects.PACIFIED,
			ForceMobEffects.VALOR, ForceMobEffects.RAGE, ForceMobEffects.DRAINED);
	/** How far ahead the held, drained and tricked foes stand. */
	private static final int AHEAD = 6;
	/** The ticks the client gets to see an entity or a power's event before the step fails. */
	private static final int SEEN_TIMEOUT = 60;
	/** A chat line fades 200 ticks after it arrives: the second player's join line is waited out this long. */
	private static final int CHAT_FADE_TICKS = 210;

	/** What the steps change on the player, put back at the end. */
	private record Before(Vec3 position, float yaw, float pitch, ForceData force, SkillData skills, IdentityData identity, StoryData story,
			ItemStack mainHand, boolean peaceful) {
	}

	/** Runs the powers steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		Before before = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return new Before(p.position(), p.getYRot(), p.getXRot(), Progression.get(p).copy(), SkillService.data(p).copy(), Identity.get(p).copy(),
					Story.get(p).copy(), p.getMainHandItem().copy(), p.level().getDifficulty() == Difficulty.PEACEFUL);
		});
		CameraType camera = context.computeOnClient(mc -> mc.options.getCameraType());
		BlockPos start = BlockPos.containing(before.position());
		Stage stage = new Stage(start.getX(), Math.min(start.getY() + LIFT, 300), start.getZ());
		// The second player while it is logged in, and the server tick it joined at (its join line fades from there).
		AtomicReference<ServerPlayer> ally = new AtomicReference<>();
		AtomicInteger joinedAt = new AtomicInteger(-1);

		if (before.peaceful()) {
			OldRepublicMod.LOGGER.info("Powers client step: shortcut, difficulty Normal for the steps (Peaceful removes the foes); put back afterwards");
			server.runCommand("difficulty normal");
		}

		server.runOnServer(s -> {
			ServerPlayer p = player(s);

			if (p.isPassenger()) {
				OldRepublicMod.LOGGER.warn("Powers client step: the player was still riding {}; dismounted for the steps", p.getVehicle());
				p.stopRiding();
			}

			stage.build(p.level());
			OldRepublicMod.LOGGER.info("Powers client step: shortcut, the player is awakened, raised to level {} and rank {} and taught {} (put back afterwards)",
					LEVEL, RANK, TAUGHT);
			ForceData data = Progression.get(p);

			if (!data.isAwakened()) {
				data.questStage = QuestStage.FOCUS.ordinal();
			}

			data.abilities.addAll(TAUGHT);
			data.level = Math.max(data.level, LEVEL);
			Progression.commit(p, data);
			Identity.setRank(p, Math.max(RANK, Identity.rank(p)));
			// Owns the taught powers' nodes and opens both bars.
			SkillService.refresh(p);
			OldRepublicMod.LOGGER.info("Powers client step: shortcut, the eight slots are filled directly: {}", BARS);
			SkillData skills = SkillService.data(p);

			for (int i = 0; i < BARS.size(); i++) {
				skills.slots.set(i, BARS.get(i));
			}

			SkillService.commit(p, skills);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			SkillData opened = SkillService.data(p);
			check(opened.openSlots == SkillData.BAR_SLOTS && opened.openSlots2 == SkillData.BAR_SLOTS,
					"Level " + LEVEL + " and rank " + RANK + " opened " + opened.openSlots + " and " + opened.openSlots2 + " slots, not both bars");
		});

		try {
			secondBar(context, server, stage);
			skillsScreen(context);
			stasis(context, server, stage);
			drain(context, server, stage);
			fear(context, server, stage);
			mindTrick(context, server, stage);
			rage(context, server, stage);
			valor(context, server, stage, ally, joinedAt);
		} finally {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				logOut(s, ally);
				clearPowers(p);
				stage.removeActors(p);
				stage.clear(p.level());
				Identity.commit(p, before.identity());
				Progression.commit(p, before.force());
				SkillService.commit(p, before.skills());
				SkillService.refresh(p);
				Story.commit(p, before.story());
				p.setItemInHand(InteractionHand.MAIN_HAND, before.mainHand());
				p.setHealth(p.getMaxHealth());
				p.teleportTo(before.position().x, before.position().y, before.position().z);
			});
			context.setScreen(() -> null);
			context.runOnClient(mc -> mc.options.setCameraType(camera));
			context.getInput().lookAt(before.yaw(), before.pitch());

			if (before.peaceful()) {
				server.runCommand("difficulty peaceful");
			}

			context.waitTicks(10);

			if (joinedAt.get() >= 0) {
				int since = server.computeOnServer(MinecraftServer::getTickCount) - joinedAt.get();

				if (since < CHAT_FADE_TICKS) {
					context.waitTicks(CHAT_FADE_TICKS - since);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the stage

	/** The stage: a smooth stone floor at {@code y - 1} round the spot {@code (x, y, z)}, air above, a rail two blocks high round its edge. */
	private record Stage(int x, int y, int z) {
		void build(ServerLevel level) {
			fill(level, true);
		}

		void clear(ServerLevel level) {
			fill(level, false);
		}

		private void fill(ServerLevel level, boolean build) {
			BlockState air = Blocks.AIR.defaultBlockState();
			BlockState floor = build ? Blocks.SMOOTH_STONE.defaultBlockState() : air;

			for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
				for (int dz = -BACK; dz <= FRONT; dz++) {
					boolean edge = Math.abs(dx) == HALF_WIDTH || dz == -BACK || dz == FRONT;
					level.setBlock(new BlockPos(x + dx, y - 1, z + dz), floor, Block.UPDATE_CLIENTS);

					for (int up = 0; up <= HEADROOM; up++) {
						level.setBlock(new BlockPos(x + dx, y + up, z + dz), edge && up < 2 ? floor : air, Block.UPDATE_CLIENTS);
					}
				}
			}
		}

		/** The block at the stage's floor level, {@code dx} east and {@code dz} south of the spot. */
		BlockPos at(int dx, int dz) {
			return new BlockPos(x + dx, y, z + dz);
		}

		AABB area() {
			return new AABB(x - HALF_WIDTH, y - 2, z - BACK, x + HALF_WIDTH + 1, y + HEADROOM + 1, z + FRONT + 1).inflate(16.0);
		}

		/** Removes what the steps spawned (their holds, fears and tricks end as the foes unload). */
		void removeActors(ServerPlayer p) {
			for (Entity entity : p.level().getEntitiesOfClass(Entity.class, area(), e -> e.entityTags().contains(TAG))) {
				entity.discard();
			}
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		// The test's own player joined first; the second player, while there is one, comes after it.
		return server.getPlayerList().getPlayers().getFirst();
	}

	/**
	 * Back to the stage's spot: the powers ended and their effects gone, the foes removed, health and energy full,
	 * cooldowns clear, the camera as asked, looking south and {@code pitch} down.
	 */
	private static void stand(ClientGameTestContext context, TestServerContext server, Stage stage, CameraType camera, float pitch) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			clearPowers(p);
			stage.removeActors(p);
			Vec3 at = Vec3.atBottomCenterOf(stage.at(0, 0));
			p.teleportTo(at.x, at.y, at.z);
			p.setHealth(p.getMaxHealth());
			refill(p);
		});
		context.runOnClient(mc -> mc.options.setCameraType(camera));
		context.getInput().lookAt(0f, pitch);
		context.waitTicks(10);
	}

	private static void refill(ServerPlayer p) {
		ForceData data = Progression.get(p);
		data.energy = data.maxEnergy;
		Progression.commit(p, data);

		for (String id : TAUGHT) {
			Progression.setCooldown(p, id, 0);
		}
	}

	/** Ends the player's drain and takes off every effect the powers put on them, with Valor's and Rage's modifiers. */
	private static void clearPowers(ServerPlayer p) {
		ForceM5Feature.forgetAll(p, false);

		for (Holder<MobEffect> effect : EFFECTS) {
			p.removeEffect(effect);
		}

		ForceValor.removeModifiers(p);
		ForceRage.removeModifiers(p);
		ForceValor.forget(p);
	}

	/** Presses a power's key on the server's side, as the key's packet would. */
	private static void use(ServerPlayer p, String id, AbilityAction action) {
		ForceAbility ability = Abilities.get(id);

		if (ability == null) {
			throw new AssertionError("Power " + id + " is not registered");
		}

		ability.onInput(p, action);
	}

	private static void check(boolean ok, String failure) {
		if (!ok) {
			throw new AssertionError(failure);
		}
	}

	/** The server's entity {@code id} near the player, as {@code type}. */
	private static <T extends Entity> T entity(MinecraftServer s, int id, Class<T> type) {
		Entity entity = player(s).level().getEntity(id);

		if (!type.isInstance(entity)) {
			throw new AssertionError("Entity " + id + " is gone, or is not a " + type.getSimpleName());
		}

		return type.cast(entity);
	}

	/** Whether the client has entity {@code id} (it is tracked, so its events reach the client). */
	private static boolean seen(Minecraft mc, int id) {
		return mc.level != null && mc.level.getEntity(id) != null;
	}

	/** Yaw and pitch from {@code eye} to {@code target}. */
	private static float[] angles(Vec3 eye, Vec3 target) {
		Vec3 d = target.subtract(eye);
		float yaw = (float) (Math.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90f;
		float pitch = (float) -(Math.atan2(d.y, d.horizontalDistance()) * (180.0 / Math.PI));
		return new float[] {yaw, pitch};
	}

	/** Turns the player's view to entity {@code id}'s chest (the server learns the turn on the next tick). */
	private static void aim(ClientGameTestContext context, TestServerContext server, int id) {
		float[] view = server.computeOnServer(s -> angles(player(s).getEyePosition(), ForcePowers.chest(entity(s, id, Entity.class))));
		context.getInput().lookAt(view[0], view[1]);
		context.waitTicks(3);
	}

	/** A foe of {@code type} at the middle of {@code at}, facing north (toward the spot), without AI, tagged and kept. */
	private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, BlockPos at) {
		T mob = type.create(level, EntitySpawnReason.EVENT);

		if (mob == null) {
			throw new AssertionError("A " + type.toShortString() + " could not be created at " + at);
		}

		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		mob.setNoAi(true);
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
		mob.setYHeadRot(180f);
		mob.setYBodyRot(180f);
		mob.addTag(TAG);
		mob.setPersistenceRequired();

		if (!level.addFreshEntity(mob)) {
			throw new AssertionError("A " + type.toShortString() + " could not be added at " + at);
		}

		return mob;
	}

	/** A lit saber attuned to {@code player}, red for Rage (the boss tests' recipe). */
	private static ItemStack litSaber(ServerPlayer player) {
		ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(player.getUUID(), player.getName().getString(), BladeColor.RED.rgb()), BladeColor.RED,
				Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
		SaberToggle.setIgnited(saber, true, player.level().getGameTime());
		return saber;
	}

	// ---------------------------------------------------------------- 165 and 166: the second bar

	/** 165: eight powers in the two bars; Focus held, so the second bar is lit and the first dimmed. */
	private static void secondBar(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, CameraType.FIRST_PERSON, 10f);
		// The client has the eight slots (the second bar's last slot open, Stasis in its first).
		context.waitFor(mc -> mc.player != null && Skills.isOpen(mc.player, SkillData.SLOT_COUNT - 1)
				&& ForceStasis.ID.equals(Skills.slot(mc.player, SkillData.BAR_SLOTS)), SEEN_TIMEOUT);
		context.getInput().holdKey(ForceKeys.FOCUS);

		try {
			context.waitFor(mc -> AbilityInput.focusHeld(), SEEN_TIMEOUT);
			// The HUD eases the second bar in.
			context.waitTicks(8);
			context.takeScreenshot("oldrepublic_m5_165_second_bar_focus_held");
		} finally {
			context.getInput().releaseKey(ForceKeys.FOCUS);
		}

		context.waitFor(mc -> !AbilityInput.focusHeld(), SEEN_TIMEOUT);
	}

	/** 166: the Force screen on the Exploration tree, Mind Trick selected, the eight sockets under the tree. */
	private static void skillsScreen(ClientGameTestContext context) {
		context.runOnClient(mc -> ForceScreen.openOn(mc, SkillIds.TREE_EXPLORATION, MindTrick.ID));
		context.waitForScreen(ForceScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m5_166_skills_screen_eight_sockets");
		context.setScreen(() -> null);
		context.waitTicks(4);
	}

	// ---------------------------------------------------------------- 167 to 170: the powers on foes

	/** 167: a still Sith trooper 6 blocks ahead held in Stasis; the rime has settled on it. From behind the caster. */
	private static void stasis(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, CameraType.THIRD_PERSON_BACK, 8f);
		int id = server.computeOnServer(s -> spawn(player(s).level(), EnemyEntities.SITH_TROOPER, stage.at(0, AHEAD)).getId());
		context.waitFor(mc -> seen(mc, id), SEEN_TIMEOUT);
		aim(context, server, id);
		server.runOnServer(s -> {
			use(player(s), ForceStasis.ID, AbilityAction.START);
			check(StasisHolds.isHeld(entity(s, id, Mob.class)), "Force Stasis did not hold the trooper: screenshot 167 would show a free trooper");
		});
		context.waitFor(mc -> StasisFx.isHeld(id), SEEN_TIMEOUT);
		// The rime drifts in and settles; the hold lasts 60 ticks.
		context.waitTicks(14);
		context.takeScreenshot("oldrepublic_m5_167_stasis_on_trooper");
		check(server.computeOnServer(s -> StasisHolds.isHeld(entity(s, id, Mob.class))), "The trooper was out of stasis by the time of screenshot 167");
	}

	/** 168: Drain on a still crystal poacher 6 blocks ahead, after its first pulse: the stream of motes to the hand. */
	private static void drain(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, CameraType.THIRD_PERSON_BACK, 8f);
		int caster = server.computeOnServer(s -> player(s).getId());
		int id = server.computeOnServer(s -> spawn(player(s).level(), EnemyEntities.CRYSTAL_POACHER, stage.at(1, AHEAD)).getId());
		context.waitFor(mc -> seen(mc, id), SEEN_TIMEOUT);
		aim(context, server, id);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			use(p, ForceDrain.ID, AbilityAction.START);
			check(ForceDrain.isDraining(p) && ForceDrain.targetOf(p) == entity(s, id, Mob.class),
					"Force Drain did not take the poacher: screenshot 168 would show no stream");
		});
		context.waitFor(mc -> DrainFx.isDraining(caster), SEEN_TIMEOUT);
		// A pulse lands every 10 ticks; after the first the stream runs from the poacher to the hand.
		context.waitTicks(16);
		context.takeScreenshot("oldrepublic_m5_168_drain_stream");
		boolean draining = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			boolean on = ForceDrain.isDraining(p);
			use(p, ForceDrain.ID, AbilityAction.RELEASE);
			return on;
		});
		check(draining, "The drain had ended by the time of screenshot 168");
	}

	/** 169: Fear cast at three poachers 6 and 7 blocks ahead; their AI back on, they turn to run, shivering. From behind. */
	private static void fear(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, CameraType.THIRD_PERSON_BACK, 8f);
		int[] ids = server.computeOnServer(s -> {
			ServerLevel level = player(s).level();
			return new int[] {spawn(level, EnemyEntities.CRYSTAL_POACHER, stage.at(-2, AHEAD)).getId(),
					spawn(level, EnemyEntities.CRYSTAL_POACHER, stage.at(0, AHEAD + 1)).getId(),
					spawn(level, EnemyEntities.CRYSTAL_POACHER, stage.at(2, AHEAD)).getId()};
		});
		context.waitFor(mc -> seen(mc, ids[0]) && seen(mc, ids[1]) && seen(mc, ids[2]), SEEN_TIMEOUT);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);

			for (int id : ids) {
				entity(s, id, Mob.class).setNoAi(false);
			}

			use(p, ForceFear.ID, AbilityAction.START);
			int afraid = 0;

			for (int id : ids) {
				afraid += Frightened.isAfraid(entity(s, id, Mob.class)) ? 1 : 0;
			}

			check(afraid == ids.length, "Force Fear frightened " + afraid + " of the three poachers in its cone: screenshot 169 would show the wrong thing");
		});
		context.waitFor(mc -> FearFx.shiver(ids[1], 0f) > 0f, SEEN_TIMEOUT);
		// They drop what they were doing, turn and start to run.
		context.waitTicks(12);
		context.takeScreenshot("oldrepublic_m5_169_fear_victims");
		check(server.computeOnServer(s -> Frightened.isAfraid(entity(s, ids[1], Mob.class))), "The poachers were no longer afraid at screenshot 169");
	}

	/** 170: a still Republic trooper 5 blocks ahead under Mind Trick: calm, in first person. */
	private static void mindTrick(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, CameraType.FIRST_PERSON, 8f);
		int id = server.computeOnServer(s -> spawn(player(s).level(), EnemyEntities.REPUBLIC_TROOPER, stage.at(0, AHEAD - 1)).getId());
		context.waitFor(mc -> seen(mc, id), SEEN_TIMEOUT);
		aim(context, server, id);
		server.runOnServer(s -> {
			use(player(s), MindTrick.ID, AbilityAction.START);
			check(Pacified.isPacified(entity(s, id, Mob.class)), "Mind Trick did not calm the trooper: screenshot 170 would show the wrong thing");
		});
		context.waitFor(mc -> MindTrickFx.calm(id, 0f) > 0f, SEEN_TIMEOUT);
		// The calm eases in.
		context.waitTicks(16);
		context.takeScreenshot("oldrepublic_m5_170_mind_trick_calm");
		check(server.computeOnServer(s -> Pacified.isPacified(entity(s, id, Mob.class))), "The trooper was no longer calm at screenshot 170");
	}

	// ---------------------------------------------------------------- 171 to 173: the powers on players

	/** 171 and 172: Rage with a lit saber in hand, from the front (embers, posture, haze), then in first person (the vignette). */
	private static void rage(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, CameraType.THIRD_PERSON_FRONT, 0f);
		OldRepublicMod.LOGGER.info("Powers client step: shortcut, a lit saber is put in the player's hand for Rage's embers (put back afterwards)");
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, litSaber(p));
		});
		// The blade lights.
		context.waitTicks(8);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			use(p, ForceRage.ID, AbilityAction.START);
			check(ForceRage.isRaging(p), "Force Rage did not start: screenshots 171 and 172 would show a calm player");
		});
		context.waitFor(mc -> mc.player != null && RageFx.isRaging(mc.player), SEEN_TIMEOUT);
		// The posture blends in and the embers start to rise off the blade.
		context.waitTicks(16);
		context.takeScreenshot("oldrepublic_m5_171_rage_embers");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(6);
		context.takeScreenshot("oldrepublic_m5_172_rage_vignette");
		boolean raging = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			boolean on = ForceRage.isRaging(p);
			clearPowers(p);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return on;
		});
		check(raging, "The rage had ended by the time of screenshots 171 and 172");
	}

	/** 173: a second player beside the caster (2 blocks east, 1 behind) as Valor is cast; the gust round both, from the front. */
	private static void valor(ClientGameTestContext context, TestServerContext server, Stage stage, AtomicReference<ServerPlayer> ally,
			AtomicInteger joinedAt) {
		stand(context, server, stage, CameraType.THIRD_PERSON_FRONT, 0f);
		OldRepublicMod.LOGGER.info("Powers client step: shortcut, the second player {} is a mock player with no client (logged out after picture 173)",
				ALLY_NAME);
		int[] ids = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerPlayer other = logIn(s, p.level(), stage.at(2, -1));
			ally.set(other);
			joinedAt.set(s.getTickCount());
			return new int[] {p.getId(), other.getId()};
		});
		context.waitFor(mc -> seen(mc, ids[1]), SEEN_TIMEOUT);
		context.waitTicks(4);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerPlayer other = ally.get();
			use(p, ForceValor.ID, AbilityAction.START);
			check(p.hasEffect(ForceMobEffects.VALOR) && other != null && other.hasEffect(ForceMobEffects.VALOR),
					"Valor did not reach both players: screenshot 173 would show one");
		});
		context.waitFor(mc -> ValorFx.isLasting(ids[0]) && ValorFx.isLasting(ids[1]), SEEN_TIMEOUT);
		// The gust's dust lifts and turns round both of them.
		context.waitTicks(6);
		context.takeScreenshot("oldrepublic_m5_173_valor_two_players");
		server.runOnServer(s -> logOut(s, ally));
	}

	/**
	 * Logs a second player in at {@code at}, facing south, as {@code GameTestHelper#makeMockServerPlayerInLevel} does: a
	 * connection whose only other end is an embedded channel, so whatever the server sends it goes nowhere.
	 */
	private static ServerPlayer logIn(MinecraftServer s, ServerLevel level, BlockPos at) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), ALLY_NAME), false);
		ServerPlayer other = new ServerPlayer(s, level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		// API-CHECK: io.netty.channel.embedded.EmbeddedChannel on the gametest compile classpath (GameTestHelper's own recipe uses it)
		new EmbeddedChannel(connection);
		s.getPlayerList().placeNewPlayer(connection, other, cookie);
		Vec3 feet = Vec3.atBottomCenterOf(at);
		other.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), 0f, 0f, false);
		other.setYHeadRot(0f);
		return other;
	}

	/** Logs the second player out, if one is logged in (the leave event ends its powers). */
	private static void logOut(MinecraftServer s, AtomicReference<ServerPlayer> ally) {
		ServerPlayer other = ally.getAndSet(null);

		if (other != null && !other.hasDisconnected()) {
			s.getPlayerList().remove(other);
		}
	}

	private ForceClientSteps() {
	}
}
