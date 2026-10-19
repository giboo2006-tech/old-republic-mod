package com.oldrepublic.gametest.m5;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.crafting.EngineeringBenchScreen;
import com.oldrepublic.client.saber.workbench.LightsaberWorkbenchScreen;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingBlocks;
import com.oldrepublic.crafting.CraftingData;
import com.oldrepublic.crafting.CraftingData.Progress;
import com.oldrepublic.crafting.CraftingRules;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.crafting.bench.BenchService;
import com.oldrepublic.crafting.bench.EngineeringBenchMenu;
import com.oldrepublic.crafting.network.BenchActionPayload;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.CrystalTuning;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.LightsaberItem;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberDual;
import com.oldrepublic.saber.SaberForm;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberPassives;
import com.oldrepublic.saber.SaberStances;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 client test steps for saber forms, dual-wielding, blade length, crystal tuning and the Legendary tier (W7).
 * {@code M5ClientTest} runs them in the shared seeded world (spec MILESTONE_5.md 19.2). Screenshots are
 * {@code oldrepublic_m5_<nnn>_<what>}, 175 to 184:
 * <ul>
 * <li>175 {@code makashi_stance}, 176 {@code djem_so_stance}: a lit blue saber (Guardian, Wrapped, Weighted) in each form's
 * stance, third person from the front;</li>
 * <li>177 {@code jar_kai_stance}: the blue saber and a lit green one (Duelist, Ridged, Balanced) in the off hand, Jar'Kai;</li>
 * <li>178 {@code dual_wield_first_person}: in Jar'Kai, the off hand's blow following the main hand's on a still Sith
 * trooper two blocks ahead, in first person;</li>
 * <li>179 {@code dual_wield_third_person}: the same pair of blows from behind the player;</li>
 * <li>180 {@code short_blade}, 181 {@code long_blade}: the blue saber with a short, then a long blade, Unformed, from the
 * front;</li>
 * <li>182 {@code workbench_tune_tab}: the Lightsaber Workbench's Tune tab, the player's saber on the bench, Keen chosen and
 * its price (two sunstone, 100 credits) carried, so the Tune button is lit; the tuning is then made;</li>
 * <li>183 {@code legendary_craft}: the Engineering Bench as a legendary craft (the Hask Pommel) finishes;</li>
 * <li>184 {@code legendary_banner}: the first Legendary's banner, the screen closed, looking at the bench.</li>
 * </ul>
 *
 * <p>The steps build a dark stage 40 blocks above wherever the player stands, in the player's own level, with a wall
 * behind the spot (north) so the blades show against it from the front, and stand the player on it facing south. The
 * saber is lit and put out with the real Toggle Lightsaber key (U), and the blows are real attack presses. Shortcuts,
 * each logged: the inventory is emptied for the steps; the three new forms' nodes are granted and each form is made the
 * active form at once, without the change's delay; difficulty is raised from Peaceful to Normal while the trooper stands
 * (Peaceful removes it); the trooper is spawned without AI and with 200 health; the blade length is set on the saber as
 * the workbench's length switch sets it; the Keen tuning's price is put in the inventory; the crafting data is set to
 * Artifice at the Hask Pommel's level with its schematic known and no Legendary made yet; and, when the craft's
 * first-craft XP would raise the player's level (whose banner would come first), the player's XP is moved to the start
 * of the next level that it cannot finish. The server confirms each step did what its picture claims. Everything is put
 * back at the end: the stage and both benches, the trooper, the inventory and selected slot, the crafting, Force and
 * skill data (the active form with them), the off hand's clock, the workbench's remembered tab, health, camera and
 * position; the banner is waited out, so the next steps' pictures are clean.
 */
public final class SaberClientSteps {
	/** Height of the stage above the player's starting position. */
	private static final int LIFT = 40;
	/** The stage's half-width, depth behind the spot and length ahead of it (south, +Z), and the air cleared above it. */
	private static final int HALF_WIDTH = 7;
	private static final int BACK = 7;
	private static final int FRONT = 8;
	private static final int HEADROOM = 7;
	/** The dark wall along the stage's north edge, behind the player as the front camera sees them. */
	private static final int WALL_HEIGHT = 5;
	/** Tag of everything the steps spawn, so cleanup never touches anyone else's entities. */
	private static final String TAG = "or_m5_sabers";
	private static final Function<Options, KeyMapping> ATTACK = o -> o.keyAttack;
	/** The forms the steps grant and show. */
	private static final SaberForm[] FORMS = {SaberForm.MAKASHI, SaberForm.DJEM_SO, SaberForm.JAR_KAI};
	/** The still trooper the blades strike: this many blocks ahead (south) of the spot, with this much health. */
	private static final int TARGET_AHEAD = 2;
	private static final float TARGET_HEALTH = 200f;
	/** The benches, once the saber pictures are taken: two blocks ahead, one each side. */
	private static final int BENCH_AHEAD = 2;
	private static final int BENCH_DX = 2;
	/** The legendary craft of picture 183: the Hask Pommel (four clan iron and a hymnstone shard). */
	private static final Identifier HASK = OldRepublicMod.id("pommel_hask_legendary");
	/** Ticks the client gets to see a change before the step fails; the off hand's swing shows at once. */
	private static final int SEEN_TIMEOUT = 60;
	private static final int SWING_TIMEOUT = 5;
	/** Ticks for a blade to grow out after the toggle, and for a stance to settle, before a picture. */
	private static final int IGNITE_TICKS = 20;
	private static final int STANCE_TICKS = 12;
	/** The banner shows for 70 ticks and fades: waited out after picture 184. */
	private static final int BANNER_CLEAR_TICKS = 80;

	/** What the steps change on the player, put back at the end. */
	private record Before(Vec3 position, float yaw, float pitch, List<ItemStack> inventory, int selected, CraftingData crafting, ForceData force,
			SkillData skills, boolean peaceful) {
	}

	/** Runs the saber steps. */
	public static void run(ClientGameTestContext context, TestServerContext server) {
		Before before = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Inventory inventory = p.getInventory();
			List<ItemStack> items = new ArrayList<>();

			for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
				items.add(inventory.getItem(slot).copy());
			}

			return new Before(p.position(), p.getYRot(), p.getXRot(), items, inventory.getSelectedSlot(), CraftingAttachments.get(p),
					Progression.get(p).copy(), SkillService.data(p).copy(), p.level().getDifficulty() == Difficulty.PEACEFUL);
		});
		CameraType camera = context.computeOnClient(mc -> mc.options.getCameraType());
		BlockPos start = BlockPos.containing(before.position());
		Stage stage = new Stage(start.getX(), Math.min(start.getY() + LIFT, 300), start.getZ());

		if (before.peaceful()) {
			OldRepublicMod.LOGGER.info("Saber client step: shortcut, difficulty Normal for the steps (Peaceful removes the trooper); put back afterwards");
			server.runCommand("difficulty normal");
		}

		server.runOnServer(s -> {
			ServerPlayer p = player(s);

			if (p.isPassenger()) {
				OldRepublicMod.LOGGER.warn("Saber client step: the player was still riding {}; dismounted for the steps", p.getVehicle());
				p.stopRiding();
			}

			p.closeContainer();
			stage.build(p.level());
			OldRepublicMod.LOGGER.info("Saber client step: shortcut, the inventory is emptied for the steps and the Makashi, Djem So and Jar'Kai nodes "
					+ "are granted (both put back afterwards)");
			p.getInventory().clearContent();

			for (SaberForm form : FORMS) {
				Skills.grant(p, form.nodeId());
			}
		});

		try {
			stances(context, server, stage);
			dualWield(context, server, stage);
			lengths(context, server, stage);
			tuneTab(context, server, stage);
			legendaryCraft(context, server, stage);
		} finally {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				p.closeContainer();
				stage.clear(p.level());
				stage.removeActors(p);
				Inventory inventory = p.getInventory();
				inventory.clearContent();

				for (int slot = 0; slot < before.inventory().size() && slot < inventory.getContainerSize(); slot++) {
					inventory.setItem(slot, before.inventory().get(slot));
				}

				inventory.setSelectedSlot(before.selected());
				CraftingAttachments.set(p, before.crafting());
				Progression.commit(p, before.force());
				SkillService.commit(p, before.skills());
				SkillService.refresh(p);
				SaberDual.resetClock(p);
				p.setHealth(p.getMaxHealth());
				p.teleportTo(before.position().x, before.position().y, before.position().z);
			});
			context.setScreen(() -> null);
			context.runOnClient(mc -> {
				LightsaberWorkbenchScreen.resetTabForTest();
				mc.options.setCameraType(camera);
			});
			context.getInput().lookAt(before.yaw(), before.pitch());

			if (before.peaceful()) {
				server.runCommand("difficulty peaceful");
			}

			context.waitTicks(10);
		}
	}

	// ---------------------------------------------------------------- the stage

	/**
	 * The stage: a polished deepslate floor at {@code y - 1} round the spot {@code (x, y, z)}, air above, and a deepslate
	 * tile wall along its north edge.
	 */
	private record Stage(int x, int y, int z) {
		void build(ServerLevel level) {
			fill(level, true);
		}

		void clear(ServerLevel level) {
			fill(level, false);
		}

		private void fill(ServerLevel level, boolean build) {
			BlockState air = Blocks.AIR.defaultBlockState();
			BlockState floor = build ? Blocks.POLISHED_DEEPSLATE.defaultBlockState() : air;
			BlockState wall = build ? Blocks.DEEPSLATE_TILES.defaultBlockState() : air;

			for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
				for (int dz = -BACK; dz <= FRONT; dz++) {
					level.setBlock(new BlockPos(x + dx, y - 1, z + dz), floor, Block.UPDATE_CLIENTS);

					for (int up = 0; up <= HEADROOM; up++) {
						level.setBlock(new BlockPos(x + dx, y + up, z + dz), dz == -BACK && up < WALL_HEIGHT ? wall : air, Block.UPDATE_CLIENTS);
					}
				}
			}
		}

		/** The block at the stage's floor level, {@code dx} east and {@code dz} south of the spot. */
		BlockPos at(int dx, int dz) {
			return new BlockPos(x + dx, y, z + dz);
		}

		AABB area() {
			return new AABB(x - HALF_WIDTH, y - 2, z - BACK, x + HALF_WIDTH + 1, y + HEADROOM + 1, z + FRONT + 1).inflate(8.0);
		}

		/** Removes what the steps spawned, and any item a bench dropped. */
		void removeActors(ServerPlayer p) {
			for (Entity entity : p.level().getEntitiesOfClass(Entity.class, area(), e -> e.entityTags().contains(TAG) || e instanceof ItemEntity)) {
				entity.discard();
			}
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Back to the stage's spot: the trooper gone, health full, the camera as asked, looking south and {@code pitch} down. */
	private static void stand(ClientGameTestContext context, TestServerContext server, Stage stage, CameraType camera, float pitch) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			stage.removeActors(p);
			Vec3 at = Vec3.atBottomCenterOf(stage.at(0, 0));
			p.teleportTo(at.x, at.y, at.z);
			p.setHealth(p.getMaxHealth());
		});
		context.runOnClient(mc -> mc.options.setCameraType(camera));
		context.getInput().lookAt(0f, pitch);
		context.waitTicks(10);
	}

	private static void check(boolean ok, String failure) {
		if (!ok) {
			throw new AssertionError(failure);
		}
	}

	/** A retracted single saber of these parts around a crystal attuned to {@code p}. */
	private static ItemStack saber(ServerPlayer p, BladeColor color, Emitter emitter, Grip grip, Pommel pommel) {
		return SaberItems.buildSaber(new CrystalAttunement(p.getUUID(), p.getPlainTextName(), color.rgb()), color, emitter, grip, pommel,
				SaberToggle.balance());
	}

	/**
	 * Presses the Toggle Lightsaber key (U) and waits until the main-hand blade is {@code lit} on the server and the client,
	 * then for the blade to grow out (or in).
	 */
	private static void toggle(ClientGameTestContext context, TestServerContext server, boolean lit) {
		context.getInput().pressKey(InputConstants.KEY_U);
		server.waitFor(s -> SaberComponents.isIgnited(player(s).getMainHandItem()) == lit, SEEN_TIMEOUT);
		context.waitFor(mc -> mc.player != null && SaberComponents.isIgnited(mc.player.getMainHandItem()) == lit, SEEN_TIMEOUT);
		context.waitTicks(IGNITE_TICKS);
	}

	/** Makes {@code form} the player's active form at once (a logged shortcut for the timed change). */
	private static void setForm(TestServerContext server, SaberForm form) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			OldRepublicMod.LOGGER.info("Saber client step: shortcut, {} made the active form at once (no change delay)", form.id());
			Skills.setActiveForm(p, form.nodeId());
			check(Skills.activeForm(p).equals(form.nodeId()), "The active form is '" + Skills.activeForm(p) + "', not " + form.id());
		});
	}

	/** Sets {@code form} and waits until the client shows its stance and the pose has settled; the server must show it too. */
	private static void form(ClientGameTestContext context, TestServerContext server, SaberForm form) {
		setForm(server, form);
		context.waitFor(mc -> mc.player != null && SaberStances.stance(mc.player) == form, SEEN_TIMEOUT);
		context.waitTicks(STANCE_TICKS);
		check(server.computeOnServer(s -> SaberStances.stance(player(s)) == form), "The server does not show the " + form.id() + " stance for its picture");
	}

	/** Whether the client has entity {@code id}. */
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
		float[] view = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			Entity entity = p.level().getEntity(id);

			if (entity == null) {
				throw new AssertionError("Entity " + id + " is gone");
			}

			return angles(p.getEyePosition(), ForcePowers.chest(entity));
		});
		context.getInput().lookAt(view[0], view[1]);
		context.waitTicks(3);
	}

	/** A still Sith trooper at the middle of {@code at}, facing the spot (north), with health for every blow, tagged and kept. */
	private static Mob target(ServerLevel level, BlockPos at) {
		Mob mob = EnemyEntities.SITH_TROOPER.create(level, EntitySpawnReason.EVENT);

		if (mob == null) {
			throw new AssertionError("A Sith trooper could not be created at " + at);
		}

		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		mob.setNoAi(true);
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);

		if (health != null) {
			health.setBaseValue(TARGET_HEALTH);
		}

		mob.setHealth(TARGET_HEALTH);
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0f);
		mob.setYHeadRot(180f);
		mob.setYBodyRot(180f);
		mob.addTag(TAG);
		mob.setPersistenceRequired();

		if (!level.addFreshEntity(mob)) {
			throw new AssertionError("A Sith trooper could not be added at " + at);
		}

		return mob;
	}

	// ---------------------------------------------------------------- 175 to 177: the stances

	/** 175 to 177: the lit saber in each new form's stance, third person from the front; Jar'Kai with a second lit saber. */
	private static void stances(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, CameraType.THIRD_PERSON_FRONT, 0f);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.MAIN_HAND, saber(p, BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED));
		});
		context.waitFor(mc -> mc.player != null && mc.player.getMainHandItem().getItem() instanceof LightsaberItem, SEEN_TIMEOUT);
		toggle(context, server, true);

		form(context, server, SaberForm.MAKASHI);
		context.takeScreenshot("oldrepublic_m5_175_makashi_stance");
		form(context, server, SaberForm.DJEM_SO);
		context.takeScreenshot("oldrepublic_m5_176_djem_so_stance");

		// Jar'Kai: a second saber, dark, in the off hand; the toggle key lights it beside the lit main blade.
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.setItemInHand(InteractionHand.OFF_HAND, saber(p, BladeColor.GREEN, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED));
		});
		context.waitFor(mc -> mc.player != null && mc.player.getOffhandItem().getItem() instanceof LightsaberItem, SEEN_TIMEOUT);
		setForm(server, SaberForm.JAR_KAI);
		context.getInput().pressKey(InputConstants.KEY_U);
		server.waitFor(s -> SaberDual.isDualWielding(player(s)), SEEN_TIMEOUT);
		context.waitFor(mc -> mc.player != null && SaberStances.stance(mc.player) == SaberForm.JAR_KAI, SEEN_TIMEOUT);
		context.waitTicks(IGNITE_TICKS);
		context.takeScreenshot("oldrepublic_m5_177_jar_kai_stance");
		check(server.computeOnServer(s -> SaberPassives.form(player(s)) == SaberForm.JAR_KAI && SaberStances.stance(player(s)) == SaberForm.JAR_KAI),
				"Jar'Kai with two lit blades neither acts nor shows at picture 177");
	}

	// ---------------------------------------------------------------- 178 and 179: dual-wielding

	/** 178 and 179: in Jar'Kai, the main hand's blow on a still trooper and the off hand's after it, in first then third person. */
	private static void dualWield(ClientGameTestContext context, TestServerContext server, Stage stage) {
		blows(context, server, stage, CameraType.FIRST_PERSON, "oldrepublic_m5_178_dual_wield_first_person");
		blows(context, server, stage, CameraType.THIRD_PERSON_BACK, "oldrepublic_m5_179_dual_wield_third_person");
	}

	/**
	 * A fresh trooper ahead, both hands ready, then two attack presses: the first is the main hand's blow, and with the
	 * main hand recovering the second goes to the off hand (10.2). The picture is taken while both swings are on.
	 */
	private static void blows(ClientGameTestContext context, TestServerContext server, Stage stage, CameraType camera, String name) {
		stand(context, server, stage, camera, 0f);
		int id = server.computeOnServer(s -> target(player(s).level(), stage.at(0, TARGET_AHEAD)).getId());
		context.waitFor(mc -> seen(mc, id), SEEN_TIMEOUT);
		aim(context, server, id);
		check(server.computeOnServer(s -> SaberDual.isDualWielding(player(s)) && SaberPassives.form(player(s)) == SaberForm.JAR_KAI),
				"Two lit blades in Jar'Kai are not in hand for " + name);
		// Both hands ready: the main hand's charge full and the off hand's clock run out.
		context.waitFor(mc -> mc.player != null && mc.player.getAttackStrengthScale(0.5f) >= 1f, SEEN_TIMEOUT);
		server.waitFor(s -> SaberDual.offhandReady(player(s)), SEEN_TIMEOUT);
		context.getInput().pressKey(ATTACK);
		context.getInput().pressKey(ATTACK);
		context.waitFor(mc -> mc.player != null && mc.player.swinging && mc.player.swingingArm == InteractionHand.OFF_HAND, SWING_TIMEOUT);
		context.waitTicks(1);
		context.takeScreenshot(name);
		check(server.computeOnServer(s -> !SaberDual.offhandReady(player(s))),
				"The server started no off-hand recovery: the off hand's blow of " + name + " never reached it");
	}

	// ---------------------------------------------------------------- 180 and 181: blade length

	/** 180 and 181: a short, then a long blade, Unformed, third person from the front. */
	private static void lengths(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, CameraType.THIRD_PERSON_FRONT, 0f);
		server.runOnServer(s -> player(s).setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
		setForm(server, SaberForm.UNFORMED);
		blade(context, server, SaberConfig.LENGTH_SHORT, "oldrepublic_m5_180_short_blade");
		blade(context, server, SaberConfig.LENGTH_LONG, "oldrepublic_m5_181_long_blade");
	}

	/** A dark saber with blade {@code length} in hand, lit with the toggle key for its picture, then put out. */
	private static void blade(ClientGameTestContext context, TestServerContext server, int length, String name) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			OldRepublicMod.LOGGER.info("Saber client step: shortcut, blade length {} set on the saber as the workbench's length switch sets it", length);
			ItemStack saber = saber(p, BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED);
			saber.set(SaberComponents.SABER_CONFIG, SaberComponents.config(saber).withLength(length));
			SaberToggle.refreshModifiers(saber, false);
			p.setItemInHand(InteractionHand.MAIN_HAND, saber);
		});
		context.waitFor(mc -> mc.player != null && mc.player.getMainHandItem().getItem() instanceof LightsaberItem
				&& SaberComponents.config(mc.player.getMainHandItem()).length() == length && !SaberComponents.isIgnited(mc.player.getMainHandItem()),
				SEEN_TIMEOUT);
		toggle(context, server, true);
		context.takeScreenshot(name);
		check(server.computeOnServer(s -> {
			ItemStack held = player(s).getMainHandItem();
			return SaberComponents.isIgnited(held) && SaberComponents.config(held).length() == length;
		}), "The saber in hand is not lit with blade length " + length + " at " + name);
		toggle(context, server, false);
	}

	// ---------------------------------------------------------------- 182: the Tune tab

	/** 182: the Lightsaber Workbench's Tune tab, Keen chosen for the saber on the bench with its price carried; then the tuning. */
	private static void tuneTab(ClientGameTestContext context, TestServerContext server, Stage stage) {
		stand(context, server, stage, CameraType.FIRST_PERSON, 20f);
		BlockPos pos = stage.at(-BENCH_DX, BENCH_AHEAD);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.getInventory().clearContent();
			p.level().setBlock(pos, SaberItems.WORKBENCH.defaultBlockState(), Block.UPDATE_ALL);
			SaberBalance b = SaberToggle.balance();
			OldRepublicMod.LOGGER.info("Saber client step: shortcut, the Keen tuning's price ({} and {} credits) is put in the inventory",
					CrystalTuning.KEEN.materialStack(b), b.tuneCredits);
			p.getInventory().add(CrystalTuning.KEEN.materialStack(b));
			Credits.give(p, b.tuneCredits);
		});
		open(context, server, pos, LightsaberWorkbenchScreen.class);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			LightsaberWorkbenchMenu menu = workbench(p);
			menu.getSlot(SaberAssembly.SABER).set(saber(p, BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED));
			menu.broadcastChanges();
		});
		context.waitFor(mc -> mc.player != null && mc.player.containerMenu instanceof LightsaberWorkbenchMenu menu
				&& menu.getSlot(SaberAssembly.SABER).getItem().getItem() instanceof LightsaberItem, SEEN_TIMEOUT);
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof LightsaberWorkbenchScreen screen) {
				screen.showTabForTest(CrystalTuning.KEEN);
			}
		});
		context.waitTicks(15);
		context.takeScreenshot("oldrepublic_m5_182_workbench_tune_tab");
		boolean pressed = context.computeOnClient(mc -> mc.gui.screen() instanceof LightsaberWorkbenchScreen screen && screen.pressTuneForTest());
		check(pressed, "The Tune button was not lit with Keen chosen and its price carried: picture 182 shows a refusal");
		server.waitFor(s -> player(s).containerMenu instanceof LightsaberWorkbenchMenu menu
				&& CrystalTuning.of(SaberComponents.config(menu.getSlot(SaberAssembly.SABER).getItem()).crystal()) == CrystalTuning.KEEN, SEEN_TIMEOUT);
		// The tuned socket's glint, then the bench emptied so closing it returns nothing.
		context.waitTicks(20);
		server.runOnServer(s -> {
			LightsaberWorkbenchMenu menu = workbench(player(s));

			for (int slot = 0; slot < SaberAssembly.SIZE; slot++) {
				menu.getSlot(slot).set(ItemStack.EMPTY);
			}

			menu.broadcastChanges();
		});
		context.waitTicks(2);
		context.setScreen(() -> null);
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- 183 and 184: a legendary craft

	/** 183 and 184: the Hask Pommel's legendary craft at the Engineering Bench as it finishes, then the first Legendary's banner. */
	private static void legendaryCraft(ClientGameTestContext context, TestServerContext server, Stage stage) {
		Schematic hask = Schematics.get(HASK);

		if (hask == null) {
			throw new AssertionError("The " + HASK + " schematic is not loaded; the legendary craft cannot run");
		}

		stand(context, server, stage, CameraType.FIRST_PERSON, 20f);
		BlockPos pos = stage.at(BENCH_DX, BENCH_AHEAD);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			p.level().setBlock(pos, CraftingBlocks.ENGINEERING_BENCH.defaultBlockState(), Block.UPDATE_ALL);
			CraftingBalance b = CraftingBalance.get();
			int level = Math.max(1, hask.level());
			OldRepublicMod.LOGGER.info("Saber client step: shortcut, the crafting data is Artifice {} with {} known and no Legendary made yet (put back afterwards)",
					level, HASK);
			CraftingAttachments.set(p, CraftingData.EMPTY.withProgress(new Progress(Discipline.ARTIFICE, level, levelXp(b, level), 0)).withKnown(HASK)
					.withChosenAt(0L));
			check(Crafting.craftRefusal(CraftingAttachments.get(p), hask) == null && Crafting.noLegendaryYet(CraftingAttachments.get(p)),
					"The Hask Pommel cannot be crafted as a first Legendary with the steps' crafting data");
			// A level-up banner from the first-craft XP would come before the legendary one.
			ForceData data = Progression.get(p);
			int max = Progression.balance().maxLevel;
			long craftXp = CraftingRules.firstCraftXp(hask.level(), b);

			if (data.level < max && data.xp + craftXp >= Progression.xpToNext(data.level)) {
				data.xp = 0;

				while (data.level < max && Progression.xpToNext(data.level) <= craftXp) {
					data.level++;
				}

				OldRepublicMod.LOGGER.info("Saber client step: shortcut, the player's XP is set to the start of level {} so the craft's {} XP raises no "
						+ "level-up banner ahead of the legendary one (put back afterwards)", data.level, craftXp);
				Progression.commit(p, data);
			}
		});
		open(context, server, pos, EngineeringBenchScreen.class);
		onBench(server, (p, menu) -> {
			menu.clickMenuButton(p, EngineeringBenchMenu.TAB_CRAFT);
			menu.getSlot(EngineeringBenchMenu.TRAY_START).set(new ItemStack(M5Items.CLAN_IRON, 4));
			menu.getSlot(EngineeringBenchMenu.TRAY_START + 1).set(new ItemStack(M5Items.HYMNSTONE_SHARD, 1));
		});
		context.waitTicks(5);
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof EngineeringBenchScreen screen) {
				screen.selectForTest(HASK);
			}
		});
		context.waitTicks(10);
		onBench(server, (p, menu) -> BenchService.handle(p, new BenchActionPayload(EngineeringBenchMenu.TAB_CRAFT, HASK.toString(), BenchActionPayload.CRAFT)));
		server.waitFor(s -> player(s).containerMenu instanceof EngineeringBenchMenu menu && menu.data(EngineeringBenchMenu.DATA_TOTAL) == 0
				&& !menu.getSlot(EngineeringBenchMenu.OUTPUT).getItem().isEmpty(), hask.craftTicks() + 60);
		context.waitTicks(4);
		context.takeScreenshot("oldrepublic_m5_183_legendary_craft");
		String wrong = server.computeOnServer(s -> {
			ItemStack output = player(s).containerMenu instanceof EngineeringBenchMenu menu ? menu.getSlot(EngineeringBenchMenu.OUTPUT).getItem() : ItemStack.EMPTY;
			return output.is(SaberItems.POMMEL_HASK) && Crafting.quality(output) == Quality.LEGENDARY ? "" : output + " of quality " + Crafting.quality(output);
		});
		check(wrong.isEmpty(), "The legendary craft gave " + wrong + ", not a Legendary Hask Pommel: picture 183 shows the wrong thing");
		check(server.computeOnServer(s -> !Crafting.noLegendaryYet(CraftingAttachments.get(player(s)))),
				"The craft was not recorded as the player's first Legendary, so no banner was sent for picture 184");

		// Empty every slot the step filled, so closing returns nothing; then the banner over the world, looking at the bench.
		onBench(server, (p, menu) -> {
			for (int slot = EngineeringBenchMenu.TRAY_START; slot < EngineeringBenchMenu.INV_START; slot++) {
				menu.getSlot(slot).set(ItemStack.EMPTY);
			}
		});
		context.waitTicks(2);
		context.setScreen(() -> null);
		context.getInput().lookAt(pos);
		context.waitTicks(8);
		context.takeScreenshot("oldrepublic_m5_184_legendary_banner");
		context.waitTicks(BANNER_CLEAR_TICKS);
	}

	// ---------------------------------------------------------------- menus

	/** Opens the block menu at {@code pos} for the player (as a right click does) and waits for its screen. */
	private static void open(ClientGameTestContext context, TestServerContext server, BlockPos pos, Class<?> screen) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);

			if (provider == null) {
				throw new AssertionError("No menu at " + pos + ": " + level.getBlockState(pos));
			}

			p.openMenu(provider);
		});
		context.waitFor(mc -> screen.isInstance(mc.gui.screen()), SEEN_TIMEOUT);
		context.waitTicks(5);
	}

	private static LightsaberWorkbenchMenu workbench(ServerPlayer p) {
		if (!(p.containerMenu instanceof LightsaberWorkbenchMenu menu)) {
			throw new AssertionError("The Lightsaber Workbench menu is not open on the server");
		}

		return menu;
	}

	private interface BenchAction {
		void run(ServerPlayer p, EngineeringBenchMenu menu);
	}

	/** Runs {@code action} on the server with the player's open bench menu, then syncs it. */
	private static void onBench(TestServerContext server, BenchAction action) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);

			if (!(p.containerMenu instanceof EngineeringBenchMenu menu)) {
				throw new AssertionError("The Engineering Bench menu is not open on the server");
			}

			action.run(p, menu);
			menu.broadcastChanges();
		});
	}

	private static int levelXp(CraftingBalance b, int level) {
		return b.levelXp.length == 0 ? 0 : b.levelXp[Math.max(0, Math.min(b.levelXp.length - 1, level - 1))];
	}

	private SaberClientSteps() {
	}
}
