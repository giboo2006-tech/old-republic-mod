package com.oldrepublic.gametest.visuals;

import java.util.List;
import java.util.function.Predicate;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.client.force.DrainFx;
import com.oldrepublic.client.force.FearFx;
import com.oldrepublic.client.force.MindTrickFx;
import com.oldrepublic.client.force.RageFx;
import com.oldrepublic.client.force.SenseReveal;
import com.oldrepublic.client.force.ValorFx;
import com.oldrepublic.client.skill.ForceScreen;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.force.ForceM5Feature;
import com.oldrepublic.force.ForceMobEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.drain.ForceDrain;
import com.oldrepublic.force.fear.ForceFear;
import com.oldrepublic.force.heal.ForceHeal;
import com.oldrepublic.force.heal.HealShape;
import com.oldrepublic.force.lightning.ForceLightning;
import com.oldrepublic.force.lightning.LightningShape;
import com.oldrepublic.force.mindtrick.MindTrick;
import com.oldrepublic.force.rage.ForceRage;
import com.oldrepublic.force.sense.ForceSense;
import com.oldrepublic.force.valor.ForceValor;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillService;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Close-up pictures of the energy and mind Force powers ({@code oldrepublic_look_force_energy_<nn>_<what>}): Force Lightning
 * charging, arcing into a husk by day and by night, from the side, at the struck body, in first person, chained and just
 * after the stop; Drain; Heal and its Serene Ward; Valor; Rage with a lit saber; Fear; Mind Trick; Sense; and Danger Sense
 * at the edge of the view. The powers are started with {@code /oldrepublic force use}, as their keys would; the targets
 * are husks and a crystal poacher standing still (no AI) with health for every blow.
 *
 * <p>Shortcuts, each logged: the player is made awakened and taught the powers, Serene Ward, Chain Lightning and Danger
 * Sense directly, with the Force screen open while the new nodes arrive (so no "Taught" banner covers the first-person
 * pictures, and no chat lines), and cooldowns are cleared before each cast. Seen from behind, the view is turned a little
 * off the target once a power has hold of it, so the target stands beside the caster instead of behind them. The Danger
 * Sense tell is an {@code npc_windup} sent from a husk behind the player, as the M3 step does. Everything is put back at
 * the end: the creatures, the walls, the player's Force and skill data, the held item, the effects, the difficulty.
 */
public final class ForceEnergyLook {
	/** Tag of every creature the steps spawn, so cleanup never touches anyone else's. */
	private static final String TAG = "or_look_energy";
	/** Health of the target creatures: enough for every arc and pulse. */
	private static final float TARGET_HEALTH = 400f;
	private static final int SEEN_TIMEOUT = 60;
	/** Seen from behind, how far (degrees) the view is turned left of a target, so it shows beside the caster. */
	private static final float ASIDE = 15f;
	/** Ticks of the windup tell sent for the Danger Sense picture. */
	private static final int WINDUP_TICKS = 12;
	/** The powers taught for the steps. */
	private static final List<String> TAUGHT = List.of(ForceLightning.ID, ForceHeal.ID, ForceSense.ID, ForceDrain.ID, ForceValor.ID, ForceRage.ID,
			ForceFear.ID, MindTrick.ID);
	/** The effects the powers put on the player, taken off between steps and at the end. */
	private static final List<Holder<MobEffect>> EFFECTS = List.of(ForceMobEffects.FEAR, ForceMobEffects.PACIFIED, ForceMobEffects.VALOR,
			ForceMobEffects.RAGE, ForceMobEffects.DRAINED);

	/** What the steps change on the player, put back at the end. */
	private record Before(ForceData force, SkillData skills, ItemStack mainHand, boolean peaceful) {
	}

	public static void run(ClientGameTestContext context, TestServerContext server) {
		Before before = server.computeOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			return new Before(Progression.get(p).copy(), SkillService.data(p).copy(), p.getMainHandItem().copy(),
					p.level().getDifficulty() == Difficulty.PEACEFUL);
		});

		if (before.peaceful()) {
			log("difficulty Normal for the steps (Peaceful removes the husks); put back afterwards");
			server.runCommand("difficulty normal");
		}

		teach(context, server);

		try {
			lightning(context, server);
			drain(context, server);
			heal(context, server);
			valor(context, server);
			rage(context, server);
			fear(context, server);
			mindTrick(context, server);
			sense(context, server);
			dangerSense(context, server);
		} finally {
			Visuals.release(context, server);
			server.runOnServer(s -> {
				ServerPlayer p = Visuals.player(s);
				clearPowers(p);
				removeCreatures(p);
				Progression.commit(p, before.force());
				SkillService.commit(p, before.skills());
				SkillService.refresh(p);
				p.setItemInHand(InteractionHand.MAIN_HAND, before.mainHand());
				p.setHealth(p.getMaxHealth());
			});
			server.runCommand("fill -6 -60 6 6 -58 8 minecraft:air");
			server.runCommand("effect clear @a");
			Visuals.time(server, false);

			if (before.peaceful()) {
				server.runCommand("difficulty peaceful");
			}

			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(10);
		}
	}

	// ---------------------------------------------------------------- set-up

	/**
	 * Awakens the player and teaches the powers and nodes the pictures need, without the chat lines a grant prints. The
	 * Force screen is open while the new nodes reach the client, which takes them in without a "Taught" banner.
	 */
	private static void teach(ClientGameTestContext context, TestServerContext server) {
		log("the player is made awakened and taught " + TAUGHT + ", " + HealShape.WARD.nodeId() + ", " + LightningShape.CHAIN.nodeId() + " and "
				+ SkillIds.SENSE_DANGER + " directly, with the Force screen open so no banner shows (put back afterwards)");
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			ForceData data = Progression.get(p);

			if (!data.isAwakened()) {
				data.questStage = QuestStage.FOCUS.ordinal();
				Progression.commit(p, data);
			}

			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		});
		context.waitTicks(10);
		context.runOnClient(mc -> ForceScreen.open(mc, false, null));
		await(context, "the Force screen", mc -> mc.gui.screen() instanceof ForceScreen);
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			ForceData data = Progression.get(p);
			data.abilities.addAll(TAUGHT);
			data.abilities.add(HealShape.WARD.nodeId());
			data.abilities.add(LightningShape.CHAIN.nodeId());
			Progression.commit(p, data);
			SkillService.refresh(p);
			SkillData skills = SkillService.data(p);
			skills.nodes.put(SkillIds.SENSE_DANGER, 1);
			skills.granted.add(SkillIds.SENSE_DANGER);
			SkillService.commit(p, skills);
		});
		context.waitTicks(20);
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	/** Back to the spot with the camera as asked, the powers ended, their creatures gone and health full; the HUD hidden unless {@code hud}. */
	private static void stand(ClientGameTestContext context, TestServerContext server, CameraType camera, float pitch, boolean hud) {
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			clearPowers(p);
			removeCreatures(p);
			p.setHealth(p.getMaxHealth());
		});
		Visuals.stand(context, server, camera, pitch);

		if (!hud) {
			Visuals.hud(context, false);
		}
	}

	/** Clears every taught power's cooldown. */
	private static void refill(TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);

			for (String id : TAUGHT) {
				Progression.setCooldown(p, id, 0);
			}
		});
	}

	/** Ends the player's drain and takes off every effect the powers put on them, with Valor's and Rage's modifiers. */
	private static void clearPowers(ServerPlayer p) {
		for (String id : TAUGHT) {
			ForceAbility ability = Abilities.get(id);

			if (ability != null && ability.isActive(p)) {
				ability.cancel(p, true);
			}
		}

		ForceM5Feature.forgetAll(p, false);

		for (Holder<MobEffect> effect : EFFECTS) {
			p.removeEffect(effect);
		}

		ForceValor.removeModifiers(p);
		ForceRage.removeModifiers(p);
		ForceValor.forget(p);
	}

	private static void use(TestServerContext server, String id, String action) {
		server.runCommand("execute as @p run oldrepublic force use " + id + " " + action);
	}

	/** A still creature of {@code type} {@code dx} east and {@code dz} south of the spot, facing the player, tagged, sturdy. */
	private static int spawn(TestServerContext server, EntityType<? extends Mob> type, int dx, int dz, boolean equip) {
		return server.computeOnServer(s -> {
			ServerLevel level = Visuals.player(s).level();
			BlockPos at = BlockPos.containing(Visuals.SPOT).offset(dx, 0, dz);
			Mob mob = type.create(level, EntitySpawnReason.EVENT);

			if (mob == null) {
				throw new AssertionError("A " + type.toShortString() + " could not be created at " + at);
			}

			if (equip) {
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			}

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
				throw new AssertionError("A " + type.toShortString() + " could not be added at " + at);
			}

			return mob.getId();
		});
	}

	private static void removeCreatures(ServerPlayer p) {
		AABB area = new AABB(Visuals.SPOT, Visuals.SPOT).inflate(24);

		for (Entity entity : p.level().getEntitiesOfClass(Entity.class, area, e -> e.entityTags().contains(TAG))) {
			entity.discard();
		}
	}

	/** Turns the player's view to entity {@code id}'s chest. */
	private static void aim(ClientGameTestContext context, TestServerContext server, int id) {
		float[] view = server.computeOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			Entity target = p.level().getEntity(id);

			if (target == null) {
				throw new AssertionError("Entity " + id + " is gone");
			}

			return angles(p.getEyePosition(), ForcePowers.chest(target));
		});
		context.getInput().lookAt(view[0], view[1]);
		context.waitTicks(3);
	}

	/** Turns the player's view {@link #ASIDE} degrees left of entity {@code id}'s chest, so seen from behind it stands to the right. */
	private static void aside(ClientGameTestContext context, TestServerContext server, int id) {
		float[] view = server.computeOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			Entity target = p.level().getEntity(id);

			if (target == null) {
				throw new AssertionError("Entity " + id + " is gone");
			}

			return angles(p.getEyePosition(), ForcePowers.chest(target));
		});
		context.getInput().lookAt(view[0] - ASIDE, view[1] + 2f);
		context.waitTicks(3);
	}

	/** A free camera at {@code eye} looking at {@code target}. */
	private static void look(ClientGameTestContext context, TestServerContext server, Vec3 eye, Vec3 target) {
		float[] view = angles(eye, target);
		Visuals.camera(context, server, eye, view[0], view[1]);
	}

	/** Yaw and pitch from {@code eye} to {@code target}. */
	private static float[] angles(Vec3 eye, Vec3 target) {
		Vec3 d = target.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()));
		return new float[] {yaw, pitch};
	}

	/** A point {@code dx} east, {@code dy} up and {@code dz} south of the spot. */
	private static Vec3 at(double dx, double dy, double dz) {
		return Visuals.SPOT.add(dx, dy, dz);
	}

	// ---------------------------------------------------------------- Force Lightning

	/**
	 * 01 to 09: the charge at the hands (from the front, by night); arcs into a husk ahead and to the right, from behind by
	 * night and by day; from the side; at the struck body; in first person by night and by day; Chain Lightning into three
	 * husks; and the frame after the stop. Chain Lightning is known throughout; with one husk it has nothing to leap to.
	 */
	private static void lightning(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, true);
		stand(context, server, CameraType.THIRD_PERSON_FRONT, 4f, false);
		int husk = spawn(server, EntityTypes.HUSK, -2, 6, false);
		context.waitTicks(10);
		aim(context, server, husk);
		refill(server);
		use(server, ForceLightning.ID, "start");
		context.waitTicks(5);
		shot(context, "01_lightning_charge_night");
		context.waitTicks(10);
		use(server, ForceLightning.ID, "release");
		context.waitTicks(10);

		// From behind, the husk to the right (inside the 20 degree cone), by night and then by day.
		stand(context, server, CameraType.THIRD_PERSON_BACK, 8f, false);
		husk = spawn(server, EntityTypes.HUSK, -2, 6, false);
		context.waitTicks(10);
		aside(context, server, husk);
		refill(server);
		use(server, ForceLightning.ID, "start");
		context.waitTicks(20);
		shot(context, "02_lightning_back_night");
		Visuals.time(server, false);
		context.waitTicks(6);
		shot(context, "03_lightning_back_day");
		Visuals.time(server, true);

		// The same channel from the side (east, looking west) and at the struck body.
		look(context, server, at(6.5, 1.4, 3.4), at(-0.6, 1.1, 3.4));
		context.waitTicks(3);
		shot(context, "04_lightning_side_night");
		look(context, server, at(0.6, 1.7, 3.6), at(-2.0, 1.1, 6.0));
		context.waitTicks(3);
		shot(context, "05_lightning_struck_night");
		Visuals.release(context, server);
		use(server, ForceLightning.ID, "release");
		context.waitTicks(10);

		// First person, by night and by day.
		stand(context, server, CameraType.FIRST_PERSON, 4f, true);
		husk = spawn(server, EntityTypes.HUSK, -1, 5, false);
		context.waitTicks(10);
		aim(context, server, husk);
		refill(server);
		use(server, ForceLightning.ID, "start");
		context.waitTicks(20);
		shot(context, "06_lightning_first_person_night");
		Visuals.time(server, false);
		context.waitTicks(6);
		shot(context, "07_lightning_first_person_day");
		use(server, ForceLightning.ID, "release");
		Visuals.time(server, true);
		context.waitTicks(10);

		// Chain Lightning: three husks, the arcs leaping from the first to the others (within 4 blocks of it, outside the aim
		// cone so the first stays the one nearest the aim); then the frame after the stop.
		stand(context, server, CameraType.THIRD_PERSON_BACK, 8f, false);
		husk = spawn(server, EntityTypes.HUSK, -2, 6, false);
		spawn(server, EntityTypes.HUSK, -5, 6, false);
		spawn(server, EntityTypes.HUSK, -4, 3, false);
		context.waitTicks(10);
		aside(context, server, husk);
		refill(server);
		use(server, ForceLightning.ID, "start");
		context.waitTicks(22);
		shot(context, "08_lightning_chain_night");
		use(server, ForceLightning.ID, "release");
		context.waitTicks(3);
		shot(context, "09_lightning_after_stop_night");
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- Drain

	/**
	 * 10 to 12: Drain on a husk ahead and to the right after its first pulse: from behind by day (the view turned off the husk
	 * once the drain holds it), from the side by night, first person.
	 */
	private static void drain(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		stand(context, server, CameraType.THIRD_PERSON_BACK, 8f, false);
		int caster = server.computeOnServer(s -> Visuals.player(s).getId());
		int husk = spawn(server, EntityTypes.HUSK, -2, 6, false);
		context.waitTicks(10);
		aim(context, server, husk);
		refill(server);
		use(server, ForceDrain.ID, "start");
		await(context, "the drain", mc -> DrainFx.isDraining(caster));
		aside(context, server, husk);
		context.waitTicks(11);
		shot(context, "10_drain_back_day");
		Visuals.time(server, true);
		look(context, server, at(5.5, 1.5, 3.0), at(-0.8, 1.1, 3.0));
		context.waitTicks(4);
		shot(context, "11_drain_side_night");
		Visuals.release(context, server);
		use(server, ForceDrain.ID, "release");
		context.waitTicks(10);

		stand(context, server, CameraType.FIRST_PERSON, 4f, true);
		husk = spawn(server, EntityTypes.HUSK, -1, 4, false);
		context.waitTicks(10);
		aim(context, server, husk);
		refill(server);
		use(server, ForceDrain.ID, "start");
		await(context, "the drain", mc -> DrainFx.isDraining(caster));
		context.waitTicks(14);
		shot(context, "12_drain_first_person_night");
		use(server, ForceDrain.ID, "release");
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- Heal

	/** 13 to 15: Heal from the front: the motes drawn in, the channel by night, and Serene Ward's glints after it. */
	private static void heal(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		stand(context, server, CameraType.THIRD_PERSON_FRONT, 6f, false);
		refill(server);
		use(server, ForceHeal.ID, "start");
		context.waitTicks(5);
		shot(context, "13_heal_gather_day");
		Visuals.time(server, true);
		context.waitTicks(20);
		shot(context, "14_heal_channel_night");
		// The channel runs out (66 ticks with its start-up) and the ward settles on the caster.
		context.waitTicks(48);
		shot(context, "15_heal_ward_night");
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- Valor

	/** 16 and 17: Valor cast from the front, by day and by night. */
	private static void valor(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		stand(context, server, CameraType.THIRD_PERSON_FRONT, 6f, false);
		int caster = server.computeOnServer(s -> Visuals.player(s).getId());
		refill(server);
		use(server, ForceValor.ID, "start");
		await(context, "valor", mc -> ValorFx.isLasting(caster));
		context.waitTicks(5);
		shot(context, "16_valor_cast_day");
		context.waitTicks(10);

		Visuals.time(server, true);
		stand(context, server, CameraType.THIRD_PERSON_FRONT, 6f, false);
		refill(server);
		use(server, ForceValor.ID, "start");
		await(context, "valor", mc -> ValorFx.isLasting(caster));
		context.waitTicks(8);
		shot(context, "17_valor_cast_night");
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- Rage

	/** 18 to 20: Rage with a lit red saber: the cast and the lasting rage from the front by night, the vignette in first person. */
	private static void rage(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, true);
		stand(context, server, CameraType.THIRD_PERSON_FRONT, 4f, false);
		log("a lit saber is put in the player's hand for Rage's embers (put back afterwards)");
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(p.getUUID(), p.getPlainTextName(), BladeColor.RED.rgb()), BladeColor.RED,
					Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
			SaberToggle.setIgnited(saber, true, p.level().getGameTime() - 100);
			p.setItemInHand(InteractionHand.MAIN_HAND, saber);
		});
		context.waitTicks(10);
		refill(server);
		use(server, ForceRage.ID, "start");
		await(context, "the rage", mc -> mc.player != null && RageFx.isRaging(mc.player));
		context.waitTicks(3);
		shot(context, "18_rage_cast_night");
		context.waitTicks(20);
		shot(context, "19_rage_lasting_night");
		Visuals.time(server, false);
		Visuals.release(context, server);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(8);
		shot(context, "20_rage_first_person_day");
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			clearPowers(p);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		});
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- Fear

	/** 21 and 22: Fear down a cone at three husks: the cast from behind, then the victims close up, by night. */
	private static void fear(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, true);
		stand(context, server, CameraType.THIRD_PERSON_BACK, 8f, false);
		int[] ids = {spawn(server, EntityTypes.HUSK, -2, 5, false), spawn(server, EntityTypes.HUSK, 2, 7, false),
				spawn(server, EntityTypes.HUSK, -3, 8, false)};
		context.waitTicks(10);
		context.getInput().lookAt(0f, 6f);
		context.waitTicks(3);
		refill(server);
		use(server, ForceFear.ID, "start");
		await(context, "the fear", mc -> FearFx.shiver(ids[1], 0f) > 0f);
		context.waitTicks(3);
		shot(context, "21_fear_cast_night");
		context.waitTicks(14);
		look(context, server, at(1.5, 1.7, 2.4), at(-1.0, 0.9, 6.5));
		context.waitTicks(4);
		shot(context, "22_fear_victims_night");
		Visuals.release(context, server);
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- Mind Trick

	/** 23 and 24: Mind Trick on a crystal poacher: the cast from behind, then the calm poacher close up, by day. */
	private static void mindTrick(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		stand(context, server, CameraType.THIRD_PERSON_BACK, 6f, false);
		int poacher = spawn(server, EnemyEntities.CRYSTAL_POACHER, -2, 5, true);
		context.waitTicks(10);
		aim(context, server, poacher);
		refill(server);
		use(server, MindTrick.ID, "start");
		await(context, "the calm", mc -> MindTrickFx.calm(poacher, 0f) > 0f);
		aside(context, server, poacher);
		shot(context, "23_mind_trick_cast_day");
		context.waitTicks(14);
		look(context, server, at(-0.2, 1.8, 3.3), at(-1.5, 1.4, 5.5));
		context.waitTicks(4);
		shot(context, "24_mind_trick_calm_day");
		Visuals.release(context, server);
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- Sense

	/**
	 * 25 to 27: Sense with a husk, a pig and a villager hidden behind a wall ahead: the streams from behind by day, the
	 * clusters by night, and the same in first person.
	 */
	private static void sense(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		stand(context, server, CameraType.THIRD_PERSON_BACK, 8f, false);
		server.runCommand("fill -5 -60 7 -1 -58 7 minecraft:mossy_stone_bricks");
		spawn(server, EntityTypes.HUSK, 3, 6, false);
		spawn(server, EntityTypes.PIG, -5, 4, false);
		spawn(server, EntityTypes.VILLAGER, -3, 9, false);
		context.waitTicks(15);
		refill(server);
		use(server, ForceSense.ID, "start");
		await(context, "the reveal", mc -> !SenseReveal.points().isEmpty());
		context.waitTicks(12);
		shot(context, "25_sense_streams_day");
		Visuals.time(server, true);
		context.waitTicks(20);
		shot(context, "26_sense_clusters_night");
		Visuals.release(context, server);
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(8);
		shot(context, "27_sense_first_person_night");
		server.runOnServer(s -> removeCreatures(Visuals.player(s)));
		server.runCommand("fill -5 -60 7 -1 -58 7 minecraft:air");
		context.waitTicks(10);
	}

	// ---------------------------------------------------------------- Danger Sense

	/** 28: Danger Sense in first person by day: a husk behind the player to the right starts an attack tell. */
	private static void dangerSense(ClientGameTestContext context, TestServerContext server) {
		Visuals.time(server, false);
		stand(context, server, CameraType.FIRST_PERSON, 0f, true);
		int husk = spawn(server, EntityTypes.HUSK, -3, -3, false);
		context.waitTicks(15);
		log("the Danger Sense tell is an npc_windup sent from the husk behind the player");
		server.runOnServer(s -> {
			ServerPlayer p = Visuals.player(s);
			Entity tell = p.level().getEntity(husk);

			if (tell == null) {
				throw new AssertionError("the Danger Sense husk is missing");
			}

			Vec3 toPlayer = p.getEyePosition().subtract(tell.getEyePosition()).normalize();
			EffectEvents.toPlayer(p, SaberEffects.NPC_WINDUP, tell.getEyePosition(), toPlayer, tell.getId(), WINDUP_TICKS, 0);
		});
		context.waitTicks(3);
		shot(context, "28_danger_sense_day");
		context.waitTicks(20);
	}

	/**
	 * Waits until the client has seen {@code what} ({@code seen}), for up to {@link #SEEN_TIMEOUT} ticks; logs instead of
	 * failing, so one power that did not take hold never costs the others their pictures.
	 */
	private static void await(ClientGameTestContext context, String what, Predicate<Minecraft> seen) {
		for (int i = 0; i < SEEN_TIMEOUT; i++) {
			if (context.computeOnClient(seen::test)) {
				return;
			}

			context.waitTick();
		}

		OldRepublicMod.LOGGER.warn("Force energy look: {} was not seen within {} ticks", what, SEEN_TIMEOUT);
	}

	private static void shot(ClientGameTestContext context, String what) {
		context.takeScreenshot("oldrepublic_look_force_energy_" + what);
	}

	private static void log(String shortcut) {
		OldRepublicMod.LOGGER.info("Force energy look: shortcut, {}", shortcut);
	}

	private ForceEnergyLook() {
	}
}
