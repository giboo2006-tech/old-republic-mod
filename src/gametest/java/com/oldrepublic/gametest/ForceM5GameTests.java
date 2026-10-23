package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.boss.BossEntities;
import com.oldrepublic.boss.kaast.KaastEntities;
import com.oldrepublic.combat.Allegiances;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.creature.CreatureEntities;
import com.oldrepublic.enemy.droid.DroidEntities;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceM4Feature;
import com.oldrepublic.force.ForceM5Feature;
import com.oldrepublic.force.ForceMobEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.choke.ChokeBalance;
import com.oldrepublic.force.choke.ForceChoke;
import com.oldrepublic.force.drain.DrainBalance;
import com.oldrepublic.force.drain.ForceDrain;
import com.oldrepublic.force.fear.FearBalance;
import com.oldrepublic.force.fear.ForceFear;
import com.oldrepublic.force.fear.Frightened;
import com.oldrepublic.force.heal.ForceHeal;
import com.oldrepublic.force.mindtrick.MindTrick;
import com.oldrepublic.force.mindtrick.MindTrickBalance;
import com.oldrepublic.force.mindtrick.Pacified;
import com.oldrepublic.force.mindtrick.PowerDialogues;
import com.oldrepublic.force.rage.ForceRage;
import com.oldrepublic.force.rage.RageBalance;
import com.oldrepublic.force.speed.ForceSpeed;
import com.oldrepublic.force.stasis.ForceStasis;
import com.oldrepublic.force.stasis.StasisBalance;
import com.oldrepublic.force.stasis.StasisHolds;
import com.oldrepublic.force.valor.ForceValor;
import com.oldrepublic.force.valor.ValorBalance;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.skill.SkillBalance;
import com.oldrepublic.skill.SkillContext;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillNode;
import com.oldrepublic.skill.SkillRules;
import com.oldrepublic.skill.SkillRules.LearnResult;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.SkillTreeSet;
import com.oldrepublic.skill.SkillTrees;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 Force powers (spec MILESTONE_5.md 9 and 19.1): the second power bar's open rule, the three control kinds
 * ({@code stasis}, {@code fear}, {@code pacify}) with their status effects, diminishing returns and boss rules, the six
 * powers (Stasis, Valor, Drain, Fear, Rage and Mind Trick) with their branches and mastery, Valor's and Rage's modifiers
 * kept in step with the effect, Mind Trick's dialogue hooks, the 9.9 tree nodes and Wayfarer's Grace.
 *
 * <p>Casters are survival fake players unless a rule needs the player list: they are never ticked (no regeneration, no
 * cooldown countdown), so energy and cooldowns stay exactly where a cast put them. Valor's allies, the regeneration check
 * and the per-tick modifier sync use listed mock players ({@link M3TestPlayers}), which are in the level and the player
 * list (and always creative, so their casts are free). Every test is in the {@code oldrepublic_test:arena_24x7x24}
 * fixture or needs none; whatever a test starts and spawns is undone when it ends, whichever step ends it
 * ({@link Cleanup}). Creatures of other workers' types (the force-resistant, fearless and strong-willed tags, the
 * starfighters) are only created, never added to the level, and only when their type is registered.
 */
public class ForceM5GameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	/** The six Milestone 5 powers. */
	private static final List<String> POWERS = List.of(ForceStasis.ID, ForceValor.ID, ForceDrain.ID, ForceFear.ID, ForceRage.ID, MindTrick.ID);
	/** Lang keys of the {@code ability:knows} failures (the condition appends the power's name). */
	private static final String REQ_KNOWS = "ability.oldrepublic.req.knows";
	private static final String REQ_READY = "ability.oldrepublic.req.ready";
	private static final String REQ_ENERGY = "ability.oldrepublic.req.energy";

	// ---------------------------------------------------------------- helpers

	/** What a test started and spawned, undone once, by whichever step ends the test. */
	private static final class Cleanup {
		private final List<Runnable> undo = new ArrayList<>();
		private boolean done;

		<T extends Entity> T entity(T entity) {
			undo.add(() -> {
				if (!entity.isRemoved()) {
					entity.discard();
				}
			});
			return entity;
		}

		/** Ends the player's drain, choke and the rest, forgets Steadfast and Heal's combat record; logs a listed player out. */
		<T extends ServerPlayer> T player(T player) {
			undo.add(() -> {
				ForceM5Feature.forgetAll(player, false);
				ForceM4Feature.forgetAll(player, false);
				ForceValor.forget(player);
				ForceHeal.forget(player);
				M3TestPlayers.remove(player);
			});
			return player;
		}

		void run() {
			if (done) {
				return;
			}

			done = true;

			for (int i = undo.size() - 1; i >= 0; i--) {
				undo.get(i).run();
			}
		}
	}

	/** Runs {@code body} now; a failure cleans up before it is rethrown. */
	private static void guarded(Cleanup cleanup, Runnable body) {
		try {
			body.run();
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/** Runs {@code body} {@code delay} ticks after the start; a failure cleans up. */
	private static void step(GameTestHelper helper, Cleanup cleanup, long delay, Runnable body) {
		helper.runAfterDelay(delay, () -> guarded(cleanup, body));
	}

	/** The last step: runs {@code body} {@code delay} ticks after the start and succeeds; cleans up either way. */
	private static void last(GameTestHelper helper, Cleanup cleanup, long delay, Runnable body) {
		helper.runAfterDelay(delay, () -> {
			try {
				body.run();
				helper.succeed();
			} finally {
				cleanup.run();
			}
		});
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-3;
	}

	/**
	 * A survival fake player standing at {@code relative}, facing +z: awakened, knowing {@code abilities} (powers and
	 * branch nodes alike, as the grant command does), energy full.
	 */
	private static FakePlayer caster(GameTestHelper helper, Cleanup cleanup, BlockPos relative, String... abilities) {
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "force_m5_test"));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(relative)));
		face(player, 0f, 0f);
		awaken(player, abilities);
		helper.assertTrue(!player.isCreative(), "the fake caster must be in survival");
		return cleanup.player(player);
	}

	/** A listed mock player at {@code relative} (in the level and the player list; creative), awakened, knowing {@code abilities}. */
	private static ServerPlayer listed(GameTestHelper helper, Cleanup cleanup, BlockPos relative, String... abilities) {
		ServerPlayer player = cleanup.player(M3TestPlayers.listed(helper, relative));
		awaken(player, abilities);
		return player;
	}

	private static void awaken(ServerPlayer player, String... abilities) {
		ForceData data = Progression.get(player);
		data.questStage = QuestStage.FOCUS.ordinal();
		data.abilities.addAll(List.of(abilities));
		data.energy = data.maxEnergy;
		Progression.commit(player, data);
	}

	private static void face(ServerPlayer player, float yaw, float pitch) {
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setYHeadRot(yaw);
	}

	/** Turns {@code player}'s head and body toward {@code target} (powers aim along the view). */
	private static void aim(ServerPlayer player, Vec3 target) {
		Vec3 d = target.subtract(player.getEyePosition());
		float yaw = (float) (Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90f;
		float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * (180.0 / Math.PI));
		face(player, yaw, pitch);
	}

	private static ForceAbility power(GameTestHelper helper, String id) {
		ForceAbility ability = Abilities.get(id);
		helper.assertTrue(ability != null, "power " + id + " must be registered");
		return ability;
	}

	private static float energy(ServerPlayer player) {
		return Progression.get(player).energy;
	}

	private static void setEnergy(ServerPlayer player, float energy) {
		ForceData data = Progression.get(player);
		data.energy = energy;
		Progression.commit(player, data);
	}

	private static int cooldown(ServerPlayer player, String id) {
		return Progression.get(player).cooldown(id);
	}

	/** {@code id}'s cost and cooldown for {@code player} after its multipliers. */
	private static float cost(ServerPlayer player, String id, float base) {
		return base * ForcePowers.costMultiplier(player, id);
	}

	/** The amount of modifier {@code id} on {@code attribute}, or NaN when it is not there. */
	private static double modifier(LivingEntity entity, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = entity.getAttribute(attribute);
		AttributeModifier m = instance == null ? null : instance.getModifier(id);
		return m == null ? Double.NaN : m.amount();
	}

	private static boolean hasModifier(LivingEntity entity, Holder<Attribute> attribute, Identifier id) {
		return !Double.isNaN(modifier(entity, attribute, id));
	}

	private static int remaining(LivingEntity entity, Holder<MobEffect> effect) {
		return ForceMobEffects.remaining(entity, effect);
	}

	/** A NoFreeWill creature of {@code type} (no goals; its AI still runs what a power adds). */
	private static <T extends Mob> T still(GameTestHelper helper, Cleanup cleanup, EntityType<T> type, BlockPos relative) {
		return cleanup.entity(helper.spawnWithNoFreeWill(type, relative));
	}

	/** A NoFreeWill crystal poacher: hostile, breathing, mind-trickable, not push-resistant. */
	private static Mob poacher(GameTestHelper helper, Cleanup cleanup, BlockPos relative) {
		return still(helper, cleanup, EnemyEntities.CRYSTAL_POACHER, relative);
	}

	/** Hits {@code target} for {@code amount} (armour applies) with a fresh hurt timer, so every hit lands. */
	private static void hit(GameTestHelper helper, LivingEntity target, float amount) {
		target.invulnerableTime = 0;
		target.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), amount);
	}

	/**
	 * The registered types of {@code tag} (other workers' creatures among them), each created but not added to the level:
	 * only the living ones. Empty while the tag has no registered members yet.
	 */
	private static List<LivingEntity> created(ServerLevel level, TagKey<EntityType<?>> tag) {
		List<LivingEntity> out = new ArrayList<>();

		for (Holder<EntityType<?>> holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(tag)) {
			Entity entity = holder.value().create(level, EntitySpawnReason.EVENT);

			if (entity instanceof LivingEntity living) {
				out.add(living);
			}
		}

		return out;
	}

	/** The creature of the registered type {@code path} ({@code oldrepublic:<path>}), created but not added; empty when it is not registered. */
	private static Optional<LivingEntity> created(ServerLevel level, String path) {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(OldRepublicMod.id(path)).map(type -> type.create(level, EntitySpawnReason.EVENT))
				.filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast);
	}

	/** A lit saber attuned to {@code player} (the boss tests' recipe). */
	private static ItemStack litSaber(ServerPlayer player) {
		ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(player.getUUID(), "Tester", BladeColor.BLUE.rgb()), BladeColor.BLUE, Emitter.GUARDIAN,
				Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
		SaberToggle.setIgnited(saber, true, player.level().getGameTime());
		return saber;
	}

	private static SkillContext context(int level, Path path) {
		Set<String> all = Set.of(SkillIds.TREE_UNIVERSAL, SkillIds.TREE_SABER, SkillIds.TREE_JEDI, SkillIds.TREE_SITH, SkillIds.TREE_EXPLORATION);
		return new SkillContext(true, level, 10, 0, path, all, false, false);
	}

	private static SkillData owning(String... nodeIds) {
		SkillData data = new SkillData();

		for (String id : nodeIds) {
			data.nodes.put(id, 1);
		}

		return data;
	}

	private static void expect(GameTestHelper helper, SkillTreeSet trees, SkillData data, int level, Path path, String nodeId, int penalty, LearnResult expected) {
		LearnResult got = SkillRules.check(trees, data, context(level, path), nodeId, penalty).result();
		helper.assertTrue(got == expected, nodeId + " at level " + level + " (" + path + "): expected " + expected + ", got " + got);
	}

	/** Sets the player's level and refreshes the derived skill state (open slots, stat modifiers). */
	private static void setLevel(ServerPlayer player, int level) {
		ForceData data = Progression.get(player);
		data.level = level;
		Progression.commit(player, data);
		SkillService.refresh(player);
	}

	// ---------------------------------------------------------------- the second bar (9.1)

	@GameTest(maxTicks = 20)
	public void secondBarSlotsOpenAtLevelAndRank(GameTestHelper helper) {
		SkillBalance b = new SkillBalance();
		helper.assertTrue(b.secondBarLevel == 15, "the second bar opens at level 15");
		helper.assertTrue(SkillRules.openSlots2(14, false, b) == 0 && SkillRules.openSlots2(15, false, b) == 2 && SkillRules.openSlots2(20, false, b) == 2
				&& SkillRules.openSlots2(1, true, b) == 4 && SkillRules.openSlots2(20, true, b) == 4,
				"second bar: none below 15, two from 15, all four at rank 4 or as a Wayfarer");
		helper.assertTrue(SkillRules.openSlots(false, b) == 3 && SkillRules.openSlots(true, b) == 4, "the first bar keeps the M3 rule and never opens the second");

		Cleanup cleanup = new Cleanup();
		// Eight slotted powers for eight slots: three on the first bar at level 14, two more at 15, the last three at rank 4.
		String[] known = {SkillIds.FORCE_PUSH, ForceStasis.ID, ForceValor.ID, ForceDrain.ID, ForceFear.ID, ForceRage.ID, MindTrick.ID, SkillIds.FORCE_SPEED};
		FakePlayer climber = caster(helper, cleanup, new BlockPos(1, 1, 1), known);
		FakePlayer wayfarer = caster(helper, cleanup, new BlockPos(3, 1, 1), SkillIds.FORCE_PUSH);

		try {
			setLevel(climber, 14);
			SkillData data = SkillService.data(climber);
			helper.assertTrue(data.openSlots == 3 && data.openSlots2 == 0, "level 14: three first-bar slots, no second bar, got " + data.openSlots + "/" + data.openSlots2);
			helper.assertTrue(Skills.isOpen(climber, 2) && !Skills.isOpen(climber, 3) && !Skills.isOpen(climber, 4), "slots 1 to 3 open, 4 and 5 shut");

			setLevel(climber, 15);
			data = SkillService.data(climber);
			helper.assertTrue(data.openSlots2 == 2 && Skills.isOpen(climber, 4) && Skills.isOpen(climber, 5) && !Skills.isOpen(climber, 6)
					&& !Skills.isOpen(climber, 7), "level 15 opens slots 5 and 6 only");
			helper.assertTrue(!data.slot(4).isEmpty() && !data.slot(5).isEmpty(), "the newly opened slots are filled with known powers");

			Identity.setRank(climber, 4);
			SkillService.refresh(climber);
			data = SkillService.data(climber);
			helper.assertTrue(data.openSlots == 4 && data.openSlots2 == 4 && Skills.isOpen(climber, 7), "rank 4 opens all eight slots");
			String inSlot8 = data.slot(7);
			helper.assertTrue(!inSlot8.isEmpty() && !data.slot(3).isEmpty() && !data.slot(6).isEmpty(), "with eight powers known every open slot is filled");

			Identity.setRank(climber, 2);
			SkillService.refresh(climber);
			data = SkillService.data(climber);
			helper.assertTrue(data.openSlots == 4 && data.openSlots2 == 2 && data.slots.get(7).isEmpty() && data.slotOf(inSlot8) < 0,
					"losing rank 4 shuts slots 7 and 8 and unslots their powers");
			helper.assertTrue(Skills.slot(climber, 7).isEmpty(), "a shut slot reads empty");

			setLevel(wayfarer, 1);
			Identity.recordChoice(wayfarer, SkillService.WAYFARER_TITLE);
			SkillService.refresh(wayfarer);
			helper.assertTrue(SkillService.data(wayfarer).openSlots2 == 4 && SkillService.rankFour(wayfarer), "the Wayfarer title opens all four, at any level");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	// ---------------------------------------------------------------- Force Stasis (9.2, 9.3)

	@GameTest(structure = ARENA, maxTicks = 100)
	public void stasisHoldsAndBreaks(GameTestHelper helper) {
		StasisBalance b = new StasisBalance();
		helper.assertTrue(near(b.cost, 30) && b.cooldownTicks == 400 && near(b.range, 16) && b.durationTicks == 60 && near(b.breakDamage, 8)
				&& b.breakDelayTicks == 20, "Stasis: 30 FE, 400 ticks, 16 blocks, 60 ticks, broken by 8 in one hit or 20 ticks after the first");
		helper.assertTrue(StasisHolds.breaksAtOnce(8f, b) && !StasisHolds.breaksAtOnce(7.9f, b), "8 damage breaks it at once, 7.9 does not");

		Cleanup cleanup = new Cleanup();
		ForceAbility stasis = power(helper, ForceStasis.ID);
		FakePlayer breaker = caster(helper, cleanup, new BlockPos(3, 1, 2), ForceStasis.ID);
		FakePlayer timer = caster(helper, cleanup, new BlockPos(8, 1, 2), ForceStasis.ID);
		FakePlayer patient = caster(helper, cleanup, new BlockPos(14, 1, 2), ForceStasis.ID);
		FakePlayer locker = caster(helper, cleanup, new BlockPos(20, 1, 2), ForceStasis.ID, ForceStasis.LOCK);
		FakePlayer fielder = caster(helper, cleanup, new BlockPos(3, 1, 10), ForceStasis.ID, ForceStasis.FIELD);
		Mob broken = poacher(helper, cleanup, new BlockPos(3, 1, 7));
		Mob timed = poacher(helper, cleanup, new BlockPos(8, 1, 7));
		// In the air: a creature that does not fly still falls while it is held.
		Mob falling = poacher(helper, cleanup, new BlockPos(14, 3, 7));
		Mob locked = poacher(helper, cleanup, new BlockPos(20, 1, 7));
		Mob centre = poacher(helper, cleanup, new BlockPos(3, 1, 15));
		Mob beside = poacher(helper, cleanup, new BlockPos(1, 1, 17));
		Mob other = poacher(helper, cleanup, new BlockPos(6, 1, 15));
		Mob far = poacher(helper, cleanup, new BlockPos(3, 1, 21));
		int floor = helper.absolutePos(BlockPos.ZERO).getY() + 1;

		guarded(cleanup, () -> {
			aim(breaker, ForcePowers.chest(broken));
			float full = energy(breaker);
			stasis.onInput(breaker, AbilityAction.START);
			helper.assertTrue(StasisHolds.isFrozen(broken) && StasisHolds.isHeld(broken) && remaining(broken, ForceMobEffects.STASIS) == b.durationTicks,
					"a tap holds the creature under the crosshair for 60 ticks, " + remaining(broken, ForceMobEffects.STASIS));
			helper.assertTrue(near(full - energy(breaker), cost(breaker, ForceStasis.ID, b.cost))
					&& cooldown(breaker, ForceStasis.ID) == ForcePowers.cooldown(breaker, ForceStasis.ID, b.cooldownTicks), "the cast costs 30 FE and starts the 400-tick cooldown");
			helper.assertTrue(Progression.get(breaker).mastery(ForceStasis.ID) == 1, "one mastery point per creature held");
			hit(helper, broken, 9.5f);
			helper.assertTrue(!StasisHolds.isFrozen(broken) && !StasisHolds.isHeld(broken), "one hit of 8 or more ends it at once");

			for (FakePlayer c : List.of(timer, patient, locker)) {
				Mob target = c == timer ? timed : c == patient ? falling : locked;
				aim(c, ForcePowers.chest(target));
				stasis.onInput(c, AbilityAction.START);
				helper.assertTrue(StasisHolds.isFrozen(target), "each caster holds their own poacher");
			}

			helper.assertTrue(remaining(locked, ForceMobEffects.STASIS) == b.lockTicks, "Stasis Lock holds for 100 ticks");
			hit(helper, timed, 2f);
			hit(helper, locked, 9.5f);
			hit(helper, locked, 9.5f);
			helper.assertTrue(StasisHolds.isFrozen(timed), "a small hit does not end it at once");
			helper.assertTrue(StasisHolds.isFrozen(locked), "Stasis Lock ignores its first two hits, even heavy ones");

			aim(fielder, ForcePowers.chest(centre));
			stasis.onInput(fielder, AbilityAction.START);
			helper.assertTrue(remaining(centre, ForceMobEffects.STASIS) == b.fieldTicks && remaining(beside, ForceMobEffects.STASIS) == b.fieldTicks
					&& remaining(other, ForceMobEffects.STASIS) == b.fieldTicks, "Stasis Field holds the target and every hostile within 4 blocks for 40 ticks");
			helper.assertTrue(!StasisHolds.isFrozen(far), "a hostile 6 blocks from the target is not held");
			helper.assertTrue(Progression.get(fielder).mastery(ForceStasis.ID) == 3, "three creatures held, three mastery points");
		});
		step(helper, cleanup, 15, () -> {
			helper.assertTrue(StasisHolds.isFrozen(timed), "15 ticks after the first hit the hold is still on");
			helper.assertTrue(StasisHolds.isFrozen(falling) && falling.getY() < floor + 0.5,
					"gravity still applies: the poacher held in the air has fallen to the floor (y " + falling.getY() + ")");
		});
		step(helper, cleanup, 30, () -> {
			helper.assertTrue(!StasisHolds.isFrozen(timed) && !StasisHolds.isHeld(timed), "the hold ends 20 ticks after the first hit");
			helper.assertTrue(StasisHolds.isFrozen(locked), "the two ignored hits started no count");
		});
		step(helper, cleanup, 50, () -> helper.assertTrue(!StasisHolds.isFrozen(centre) && !StasisHolds.isFrozen(beside),
				"the field's holds end after 40 ticks"));
		last(helper, cleanup, 70, () -> helper.assertTrue(!StasisHolds.isFrozen(falling) && !StasisHolds.isHeld(falling),
				"an untouched hold ends after its 60 ticks"));
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void stasisOnBossesQuarter(GameTestHelper helper) {
		StasisBalance b = new StasisBalance();
		helper.assertTrue(near(b.bossMultiplier, 0.25) && near(b.resistantMultiplier, 0.5), "a boss takes 25%, a force-resistant creature 50%");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		Mob ordinary = poacher(helper, cleanup, new BlockPos(3, 1, 3));
		// Created, never added: the rules read only the type and the boss's own modifiers.
		LivingEntity kaast = KaastEntities.TORVIN_KAAST.create(level, EntitySpawnReason.EVENT);
		LivingEntity collector = BossEntities.ORREN_VASCH.create(level, EntitySpawnReason.EVENT);

		try {
			helper.assertTrue(kaast != null && collector != null && kaast.is(ConventionalEntityTypeTags.BOSSES) && collector.is(ConventionalEntityTypeTags.BOSSES),
					"Kaast and the Collector are in #c:bosses");
			helper.assertTrue(ControlEffects.duration(ordinary, 60, ControlEffects.STASIS) == 60
					&& near(ForceM5Feature.durationMultiplier(ordinary, ControlEffects.STASIS), 1), "an ordinary creature gets the full 60 ticks");

			for (LivingEntity boss : List.of(kaast, collector)) {
				String name = boss.getType().toShortString();
				int stagger = ControlEffects.duration(boss, 60, ControlEffects.STAGGER);
				int held = ControlEffects.duration(boss, 60, ControlEffects.STASIS);
				helper.assertTrue(near(ForceM5Feature.durationMultiplier(boss, ControlEffects.STASIS), b.bossMultiplier),
						name + ": the stasis rule gives a boss 25%");
				helper.assertTrue(Math.abs(held - stagger * b.bossMultiplier) <= 1 && held < stagger,
						name + ": 25% multiplied with the boss's own rule (stagger " + stagger + ", stasis " + held + ")");
				helper.assertTrue(ControlEffects.duration(boss, 80, ControlEffects.FEAR) == 0 && ControlEffects.duration(boss, 120, ControlEffects.PACIFY) == 0,
						name + ": bosses are immune to fear and pacify");
				helper.assertTrue(Frightened.isImmune(boss) && !MindTrick.trickable(boss), name + ": no fear, no trick");
			}

			int kaastHeld = ControlEffects.duration(kaast, 60, ControlEffects.STASIS);
			helper.assertTrue(kaastHeld <= 10, "Kaast (x0.6 of his own) takes about 15%: " + kaastHeld + " of 60 ticks");
			helper.assertTrue(Frightened.apply(kaast, null, 80, null, false) == 0 && Pacified.apply((Mob) kaast, null, 120, false) == 0,
					"the fear and pacify runtimes refuse a boss outright");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void diminishingReturnsOnResistantTargets(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		FakePlayer caster = caster(helper, cleanup, new BlockPos(12, 1, 1), ForceStasis.ID);
		Mob held = still(helper, cleanup, EnemyEntities.SITH_ACOLYTE, new BlockPos(3, 1, 8));
		Mob feared = still(helper, cleanup, EnemyEntities.SITH_ACOLYTE, new BlockPos(12, 1, 8));
		Mob mixed = still(helper, cleanup, EnemyEntities.SITH_ACOLYTE, new BlockPos(20, 1, 8));
		Mob ordinary = poacher(helper, cleanup, new BlockPos(3, 1, 20));

		try {
			helper.assertTrue(ForcePowers.isResistant(held) && !ForcePowers.isResistant(ordinary), "acolytes are #push_resistant, poachers are not");
			int s1 = StasisHolds.apply(held, caster, 60, 0);
			int s2 = StasisHolds.apply(held, caster, 60, 0);
			int s3 = StasisHolds.apply(held, caster, 60, 0);
			helper.assertTrue(s1 == 60 && s2 == 30 && s3 == 0, "stasis on a resistant target: full, half, nothing (" + s1 + ", " + s2 + ", " + s3 + ")");

			int f1 = ForceFear.apply(feared, caster, 80);
			int f2 = ForceFear.apply(feared, caster, 80);
			int f3 = ForceFear.apply(feared, caster, 80);
			helper.assertTrue(f1 == 80 && f2 == 40 && f3 == 0, "fear on a resistant target: full, half, nothing (" + f1 + ", " + f2 + ", " + f3 + ")");

			int m1 = StasisHolds.apply(mixed, caster, 60, 0);
			int m2 = ForceFear.apply(mixed, caster, 80);
			int m3 = StasisHolds.apply(mixed, caster, 60, 0);
			helper.assertTrue(m1 == 60 && m2 == 40 && m3 == 0, "every control kind shares the window: stasis, then fear at half, then nothing ("
					+ m1 + ", " + m2 + ", " + m3 + ")");

			int o1 = StasisHolds.apply(ordinary, caster, 60, 0);
			int o2 = StasisHolds.apply(ordinary, caster, 60, 0);
			int o3 = StasisHolds.apply(ordinary, caster, 60, 0);
			helper.assertTrue(o1 == 60 && o2 == 60 && o3 == 60, "an ordinary target always gets the full length");

			StasisBalance b = ForceStasis.balance();
			List<LivingEntity> resistant = created(level, ForceStasis.FORCE_RESISTANT);

			for (LivingEntity creature : resistant) {
				float expected = creature.is(ConventionalEntityTypeTags.BOSSES) ? b.resistantMultiplier * b.bossMultiplier : b.resistantMultiplier;
				helper.assertTrue(near(ForceStasis.durationMultiplier(creature, b), expected),
						creature.getType().toShortString() + " is #oldrepublic:force_resistant: stasis at half");
			}

			if (resistant.isEmpty()) {
				OldRepublicMod.LOGGER.info("diminishingReturnsOnResistantTargets: no #oldrepublic:force_resistant creature is registered yet (W8's Hymnsworn)");
			}

			helper.succeed();
		} finally {
			for (Mob mob : List.of(held, mixed, ordinary)) {
				StasisHolds.release(mob);
			}

			cleanup.run();
		}
	}

	// ---------------------------------------------------------------- Force Fear and the shared fear (9.2, 9.6)

	@GameTest(structure = ARENA, maxTicks = 60)
	public void fearFleesAndChokeDreadMigrated(GameTestHelper helper) {
		FearBalance b = new FearBalance();
		ChokeBalance choke = new ChokeBalance();
		helper.assertTrue(near(b.cost, 30) && b.cooldownTicks == 600 && near(b.range, 10) && near(b.coneDegrees, 60) && b.durationTicks == 80,
				"Fear: 30 FE, 600 ticks, a 60-degree cone of 10 blocks, 80 ticks");
		helper.assertTrue(b.fleeHorizontal == 16 && b.fleeVertical == 7 && near(b.fleeSpeed, 1.3) && b.slownessAmplifier == 1,
				"the flee: 16 and 7 blocks, x1.3; Slowness II where there is no goal");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ForceAbility fear = power(helper, ForceFear.ID);
		ForceAbility chokePower = power(helper, ForceChoke.ID);
		FakePlayer caster = caster(helper, cleanup, new BlockPos(12, 1, 2), ForceFear.ID);
		FakePlayer choker = caster(helper, cleanup, new BlockPos(3, 1, 2), ForceChoke.ID, ForceChoke.DREAD);
		FakePlayer victim = caster(helper, cleanup, new BlockPos(20, 1, 20));
		Mob left = poacher(helper, cleanup, new BlockPos(11, 1, 7));
		Mob right = poacher(helper, cleanup, new BlockPos(13, 1, 8));
		Mob behind = poacher(helper, cleanup, new BlockPos(12, 1, 0));
		Mob villager = still(helper, cleanup, EntityTypes.VILLAGER, new BlockPos(14, 1, 5));
		Mob droid = still(helper, cleanup, DroidEntities.SECURITY_DROID, new BlockPos(10, 1, 9));
		Mob bait = still(helper, cleanup, EntityTypes.PIG, new BlockPos(20, 1, 14));
		Mob choked = poacher(helper, cleanup, new BlockPos(3, 1, 8));
		Mob dreaded = poacher(helper, cleanup, new BlockPos(3, 1, 12));
		Mob unshaken = poacher(helper, cleanup, new BlockPos(3, 1, 15));
		double[] start = new double[2];

		guarded(cleanup, () -> {
			left.setTarget(bait);
			start[0] = left.distanceTo(caster);
			start[1] = right.distanceTo(caster);
			float full = energy(caster);
			fear.onInput(caster, AbilityAction.START);
			helper.assertTrue(Frightened.isAfraid(left) && Frightened.isAfraid(right) && remaining(left, ForceMobEffects.FEAR) == b.durationTicks,
					"every hostile in the cone is afraid for 80 ticks");
			helper.assertTrue(left.getTarget() == null, "a frightened creature drops its target");
			helper.assertTrue(!Frightened.isAfraid(behind) && !Frightened.isAfraid(villager), "nothing behind the caster, and no villager");
			helper.assertTrue(!Frightened.isAfraid(droid) && Frightened.isImmune(droid), "droids are immune");
			helper.assertTrue(near(full - energy(caster), cost(caster, ForceFear.ID, b.cost))
					&& cooldown(caster, ForceFear.ID) == ForcePowers.cooldown(caster, ForceFear.ID, b.cooldownTicks), "30 FE and the 600-tick cooldown");
			helper.assertTrue(Progression.get(caster).mastery(ForceFear.ID) == 2, "a mastery point per enemy frightened");

			aim(choker, ForcePowers.chest(choked));
			chokePower.onInput(choker, AbilityAction.START);
			helper.assertTrue(ForceChoke.isChoking(choker), "the choke holds the poacher");
			helper.assertTrue(Frightened.isAfraid(dreaded) && remaining(dreaded, ForceMobEffects.FEAR) == choke.dreadTicks,
					"Choke's Dread is the shared fear now: the oldrepublic:fear effect for its 40 ticks");
			helper.assertTrue(!Frightened.isAfraid(unshaken) && !Frightened.isAfraid(choked), "7 blocks from the choked poacher, no dread; the choked one is held, not afraid");
			chokePower.onInput(choker, AbilityAction.RELEASE);

			int onPlayer = ForceFear.apply(victim, caster, 40);
			MobEffectInstance slow = victim.getEffect(MobEffects.SLOWNESS);
			helper.assertTrue(onPlayer == 40 && Frightened.isAfraid(victim) && slow != null && slow.getAmplifier() == b.slownessAmplifier && !victim.isSprinting(),
					"the shared fear on a player: the effect, Slowness II, no sprinting");
			helper.assertTrue(ForceFear.apply(droid, null, 40) == 0, "the shared fear refuses droids too");

			for (LivingEntity fearless : created(level, ForceFear.FEARLESS)) {
				helper.assertTrue(Frightened.isImmune(fearless), fearless.getType().toShortString() + " is #oldrepublic:fearless");
			}
		});
		step(helper, cleanup, 40, () -> {
			helper.assertTrue(left.distanceTo(caster) > start[0] + 1.0 && right.distanceTo(caster) > start[1] + 1.0,
					String.format("the frightened run from the caster: %.1f to %.1f and %.1f to %.1f", start[0], left.distanceTo(caster), start[1],
							right.distanceTo(caster)));
			helper.assertTrue(!Frightened.isAfraid(dreaded), "Dread's 40 ticks are over");
		});
		last(helper, cleanup, 45, () -> helper.assertTrue(Frightened.isAfraid(left) && left.getTarget() == null, "still afraid at 45 ticks, still no target"));
	}

	// ---------------------------------------------------------------- Mind Trick and pacify (9.2, 9.8)

	@GameTest(structure = ARENA, maxTicks = 40)
	public void pacifyStopsTargetingUntilHit(GameTestHelper helper) {
		MindTrickBalance b = new MindTrickBalance();
		helper.assertTrue(near(b.cost, 25) && b.cooldownTicks == 600 && near(b.range, 8) && b.durationTicks == 120,
				"Mind Trick: 25 FE, 600 ticks, 8 blocks, 120 ticks");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ForceAbility trick = power(helper, MindTrick.ID);
		FakePlayer caster = caster(helper, cleanup, new BlockPos(12, 1, 2), MindTrick.ID);
		FakePlayer confuser = caster(helper, cleanup, new BlockPos(3, 1, 2), MindTrick.ID, MindTrick.CONFUSE);
		FakePlayer refused = caster(helper, cleanup, new BlockPos(20, 1, 2), MindTrick.ID);
		Mob trooper = still(helper, cleanup, EnemyEntities.REPUBLIC_TROOPER, new BlockPos(12, 1, 7));
		Mob turned = still(helper, cleanup, EnemyEntities.REPUBLIC_TROOPER, new BlockPos(3, 1, 7));
		Mob comrade = still(helper, cleanup, EnemyEntities.REPUBLIC_TROOPER, new BlockPos(3, 1, 10));
		Mob bystander = still(helper, cleanup, EnemyEntities.REPUBLIC_TROOPER, new BlockPos(6, 1, 12));
		Mob closed = still(helper, cleanup, EntityTypes.ZOMBIE, new BlockPos(20, 1, 7));
		Mob bait = still(helper, cleanup, EntityTypes.PIG, new BlockPos(20, 1, 20));

		guarded(cleanup, () -> {
			trooper.setTarget(bait);
			aim(caster, ForcePowers.chest(trooper));
			float full = energy(caster);
			trick.onInput(caster, AbilityAction.START);
			helper.assertTrue(Pacified.isPacified(trooper) && remaining(trooper, ForceMobEffects.PACIFIED) == b.durationTicks && trooper.getTarget() == null,
					"a weak mind is pacified for 120 ticks and drops its target");
			helper.assertTrue(near(full - energy(caster), cost(caster, MindTrick.ID, b.cost))
					&& cooldown(caster, MindTrick.ID) == ForcePowers.cooldown(caster, MindTrick.ID, b.cooldownTicks), "25 FE and the 600-tick cooldown");
			trooper.setTarget(bait);
			helper.assertTrue(trooper.getTarget() == null, "while pacified it cannot take a new target");

			aim(confuser, ForcePowers.chest(turned));
			trick.onInput(confuser, AbilityAction.START);
			helper.assertTrue(Pacified.isConfused(turned) && turned.getTarget() == comrade, "Confusion turns the trooper on its nearest ally");
			helper.assertTrue(!Allegiances.friendly(turned, comrade) && Allegiances.friendly(comrade, bystander),
					"the same-side rule spares neither the confused nor its victim; others are still friends");

			aim(refused, ForcePowers.chest(closed));
			float before = energy(refused);
			trick.onInput(refused, AbilityAction.START);
			helper.assertTrue(!closed.hasEffect(ForceMobEffects.PACIFIED) && near(energy(refused), before) && cooldown(refused, MindTrick.ID) == 0,
					"a mind that is not weak refuses it: nothing spent, no cooldown");
		});
		step(helper, cleanup, 10, () -> {
			helper.assertTrue(Pacified.isPacified(trooper) && trooper.getTarget() == null, "ten ticks on it is still calm");
			hit(helper, trooper, 1f);
			helper.assertTrue(Pacified.isPacified(trooper), "damage from no player leaves the calm");
			trooper.invulnerableTime = 0;
			trooper.hurtServer(level, level.damageSources().playerAttack(caster), 1f);
			helper.assertTrue(!Pacified.isPacified(trooper) && !trooper.hasEffect(ForceMobEffects.PACIFIED), "a player's hit ends it");
			trooper.setTarget(bait);
			helper.assertTrue(trooper.getTarget() == bait, "once the calm is broken it may fight again");
		});
		last(helper, cleanup, 12, () -> helper.assertTrue(Pacified.isConfused(turned), "Confusion lasts beyond the first second"));
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void mindTrickTagsAndDialogueAction(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		List<Mob> weak = List.of(still(helper, cleanup, EnemyEntities.REPUBLIC_TROOPER, new BlockPos(2, 1, 20)),
				still(helper, cleanup, EnemyEntities.SITH_TROOPER, new BlockPos(5, 1, 20)), poacher(helper, cleanup, new BlockPos(8, 1, 20)),
				still(helper, cleanup, EnemyEntities.POACHER_CAPTAIN, new BlockPos(11, 1, 20)));
		List<Mob> strong = List.of(still(helper, cleanup, EnemyEntities.SITH_ACOLYTE, new BlockPos(14, 1, 20)),
				still(helper, cleanup, DroidEntities.SECURITY_DROID, new BlockPos(17, 1, 20)), still(helper, cleanup, EntityTypes.ZOMBIE, new BlockPos(20, 1, 20)),
				still(helper, cleanup, CreatureEntities.HOLLOWHORN, new BlockPos(20, 1, 9)), still(helper, cleanup, EntityTypes.VILLAGER, new BlockPos(14, 1, 3)));
		FakePlayer novice = caster(helper, cleanup, new BlockPos(2, 1, 2));
		FakePlayer trickster = caster(helper, cleanup, new BlockPos(4, 1, 2), MindTrick.ID);
		FakePlayer tired = caster(helper, cleanup, new BlockPos(6, 1, 2), MindTrick.ID);
		FakePlayer frightener = caster(helper, cleanup, new BlockPos(8, 1, 2), ForceFear.ID);

		try {
			for (Mob mob : weak) {
				helper.assertTrue(MindTrick.trickable(mob), mob.getType().toShortString() + " is a weak mind");
			}

			for (Mob mob : strong) {
				helper.assertTrue(!MindTrick.trickable(mob), mob.getType().toShortString() + " is not mind-trickable");
			}

			created(level, "cartel_enforcer").ifPresent(e -> helper.assertTrue(MindTrick.trickable(e), "the cartel's enforcers are weak minds"));

			for (String path : List.of("hask_warrior", "vessba")) {
				created(level, path).ifPresent(e -> helper.assertTrue(e.is(MindTrick.STRONG_WILLED) && !MindTrick.trickable(e), path + " is strong-willed"));
			}

			DialogueConditions.Condition knows = DialogueConditions.get(PowerDialogues.KNOWS);
			DialogueActions.Action use = DialogueActions.get(PowerDialogues.MIND_TRICK_USE);
			DialogueActions.Action fearUse = DialogueActions.get(PowerDialogues.FEAR_USE);
			helper.assertTrue(knows != null && use != null && fearUse != null, "ability:knows, mind_trick:use and fear:use are registered");

			String unknown = knows.failReason(novice, null, MindTrick.ID);
			helper.assertTrue(unknown != null && unknown.startsWith(REQ_KNOWS), "without the power the option is refused, naming it: " + unknown);
			helper.assertTrue(knows.failReason(trickster, null, MindTrick.ID) == null, "known, ready and affordable: met");
			setEnergy(tired, 5f);
			String broke = knows.failReason(tired, null, MindTrick.ID);
			helper.assertTrue(broke != null && broke.startsWith(REQ_ENERGY), "too little energy: refused with the cost, " + broke);

			Mob speaker = weak.getFirst();
			float full = energy(trickster);
			use.run(trickster, speaker);
			MindTrickBalance mb = MindTrick.balance();
			helper.assertTrue(near(full - energy(trickster), cost(trickster, MindTrick.ID, mb.cost))
					&& cooldown(trickster, MindTrick.ID) == ForcePowers.cooldown(trickster, MindTrick.ID, mb.cooldownTicks),
					"mind_trick:use spends the cost and starts the cooldown as a cast would");
			helper.assertTrue(Progression.get(trickster).mastery(MindTrick.ID) == 1, "a dialogue use counts for mastery");
			helper.assertTrue(!speaker.hasEffect(ForceMobEffects.PACIFIED), "the dialogue use pacifies no one (the conversation moves on instead)");
			String cooling = knows.failReason(trickster, null, MindTrick.ID);
			helper.assertTrue(cooling != null && cooling.startsWith(REQ_READY), "on cooldown the option is refused: " + cooling);

			float before = energy(frightener);
			fearUse.run(frightener, null);
			FearBalance fb = ForceFear.balance();
			helper.assertTrue(near(before - energy(frightener), cost(frightener, ForceFear.ID, fb.cost))
					&& cooldown(frightener, ForceFear.ID) == ForcePowers.cooldown(frightener, ForceFear.ID, fb.cooldownTicks), "fear:use spends Fear's cost and cooldown");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	// ---------------------------------------------------------------- Force Valor (9.4)

	@GameTest(structure = ARENA, maxTicks = 20, padding = 16)
	public void valorTouchesAllies(GameTestHelper helper) {
		ValorBalance b = new ValorBalance();
		helper.assertTrue(near(b.cost, 35) && b.cooldownTicks == 900 && b.durationTicks == 200 && near(b.radius, 8) && near(b.attackDamagePct, 20)
				&& near(b.armor, 4) && near(b.knockbackResistance, 0.5) && near(b.sharedRadius, 16) && near(b.sharedHeal, 4) && near(b.steadfastDamagePct, 30),
				"Valor: 35 FE, 900 ticks, 200 ticks within 8 blocks: +20% damage, +4 armour, +0.5 knockback resistance");

		Cleanup cleanup = new Cleanup();
		ForceAbility valor = power(helper, ForceValor.ID);
		ServerPlayer master = listed(helper, cleanup, new BlockPos(12, 1, 3), ForceValor.ID);
		ServerPlayer ally = listed(helper, cleanup, new BlockPos(12, 1, 8));
		ServerPlayer far = listed(helper, cleanup, new BlockPos(20, 1, 10));
		ServerPlayer rival = listed(helper, cleanup, new BlockPos(8, 1, 5));

		try {
			ForceHeal.onDamaged(rival, master, 1f);
			valor.onInput(master, AbilityAction.START);
			helper.assertTrue(remaining(master, ForceMobEffects.VALOR) == b.durationTicks && remaining(ally, ForceMobEffects.VALOR) == b.durationTicks,
					"the caster and an ally 5 blocks away have Valor for 200 ticks");
			helper.assertTrue(!far.hasEffect(ForceMobEffects.VALOR), "an ally 11 blocks away is out of reach");
			helper.assertTrue(!rival.hasEffect(ForceMobEffects.VALOR), "a player the caster is fighting is no ally");

			for (ServerPlayer p : List.of(master, ally)) {
				helper.assertTrue(near(modifier(p, Attributes.ATTACK_DAMAGE, ForceValor.DAMAGE_ID), 0.2) && near(modifier(p, Attributes.ARMOR, ForceValor.ARMOR_ID), 4)
						&& near(modifier(p, Attributes.KNOCKBACK_RESISTANCE, ForceValor.KNOCKBACK_ID), 0.5), "+20% damage, +4 armour, +0.5 knockback resistance");
			}

			helper.assertTrue(cooldown(master, ForceValor.ID) == ForcePowers.cooldown(master, ForceValor.ID, b.cooldownTicks), "the 900-tick cooldown");
			helper.assertTrue(Progression.get(master).mastery(ForceValor.ID) == 1, "a cast that touches an ally earns a mastery point");

			// Rallying Light: 16 blocks, and each ally regains 4 health.
			Progression.setCooldown(master, ForceValor.ID, 0);
			awaken(master, ForceValor.SHARED);
			far.setHealth(10f);
			valor.onInput(master, AbilityAction.START);
			helper.assertTrue(far.hasEffect(ForceMobEffects.VALOR) && near(far.getHealth(), 10f + b.sharedHeal), "Rallying Light reaches 11 blocks and heals 4");

			// Steadfast: the caster alone, +30%, and no stagger.
			Progression.setCooldown(master, ForceValor.ID, 0);
			ForceData data = Progression.get(master);
			data.abilities.remove(ForceValor.SHARED);
			data.abilities.add(ForceValor.STEADFAST);
			Progression.commit(master, data);
			ally.removeEffect(ForceMobEffects.VALOR);
			valor.onInput(master, AbilityAction.START);
			helper.assertTrue(!ally.hasEffect(ForceMobEffects.VALOR), "Steadfast touches no ally");
			helper.assertTrue(ForceValor.isSteadfast(master) && near(modifier(master, Attributes.ATTACK_DAMAGE, ForceValor.DAMAGE_ID), 0.3),
					"Steadfast gives the caster +30% attack damage");
			helper.assertTrue(ControlEffects.duration(master, 40, ControlEffects.STAGGER) == 0 && ControlEffects.duration(ally, 40, ControlEffects.STAGGER) == 40,
					"a Steadfast caster cannot be staggered; others can");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	// ---------------------------------------------------------------- Force Drain (9.5)

	@GameTest(structure = ARENA, maxTicks = 100)
	public void drainPulsesHealAndRefund(GameTestHelper helper) {
		DrainBalance b = new DrainBalance();
		helper.assertTrue(near(b.startCost, 10) && near(b.pulseCost, 2) && b.pulseTicks == 10 && b.maxPulses == 6 && b.cooldownTicks == 300 && near(b.range, 10)
				&& near(b.pulseDamage, 2) && near(b.pulseHeal, 1.5) && near(b.pulseEnergy, 2), "Drain: 10 FE, then 2 a pulse; 6 pulses of 2 damage, 1.5 health and 2 FE back");

		Cleanup cleanup = new Cleanup();
		ForceAbility drain = power(helper, ForceDrain.ID);
		FakePlayer plain = caster(helper, cleanup, new BlockPos(3, 1, 2), ForceDrain.ID);
		FakePlayer life = caster(helper, cleanup, new BlockPos(12, 1, 2), ForceDrain.ID, ForceDrain.LIFE);
		FakePlayer essence = caster(helper, cleanup, new BlockPos(20, 1, 2), ForceDrain.ID, ForceDrain.ESSENCE);
		FakePlayer lost = caster(helper, cleanup, new BlockPos(8, 1, 2), ForceDrain.ID);
		Mob plainTarget = poacher(helper, cleanup, new BlockPos(3, 1, 8));
		Mob lifeTarget = poacher(helper, cleanup, new BlockPos(12, 1, 8));
		Mob essenceTarget = poacher(helper, cleanup, new BlockPos(20, 1, 8));
		Mob lostTarget = poacher(helper, cleanup, new BlockPos(8, 1, 8));
		FakePlayer[] casters = {plain, life, essence, lost};
		Mob[] targets = {plainTarget, lifeTarget, essenceTarget, lostTarget};
		float[] health = new float[4];
		Vec3[] stood = new Vec3[4];

		guarded(cleanup, () -> {
			for (int i = 0; i < casters.length; i++) {
				casters[i].setHealth(10f);
				setEnergy(casters[i], 30f);
				health[i] = targets[i].getHealth();
				stood[i] = targets[i].position();
				aim(casters[i], ForcePowers.chest(targets[i]));
				drain.onInput(casters[i], AbilityAction.START);
				helper.assertTrue(ForceDrain.isDraining(casters[i]) && ForceDrain.targetOf(casters[i]) == targets[i], "each caster drains their own poacher");
			}

			helper.assertTrue(near(energy(plain), 30f - cost(plain, ForceDrain.ID, b.startCost)), "the channel costs 10 FE to start");
			helper.assertTrue(plainTarget.hasEffect(ForceMobEffects.DRAINED), "the target is drained");
			MobEffectInstance slow = plainTarget.getEffect(MobEffects.SLOWNESS);
			helper.assertTrue(slow != null && slow.getAmplifier() == 0 && !slow.isVisible(), "and slowed (Slowness I, hidden) while it lasts");
		});
		step(helper, cleanup, 3, () -> lostTarget.teleportTo(lostTarget.getX(), lostTarget.getY(), lostTarget.getZ() + 15.0));
		step(helper, cleanup, 6, () -> helper.assertTrue(!ForceDrain.isDraining(lost)
				&& cooldown(lost, ForceDrain.ID) == ForcePowers.cooldown(lost, ForceDrain.ID, b.cooldownTicks), "a target out of range or sight ends it, and the cooldown starts"));
		step(helper, cleanup, 15, () -> {
			float dealt = health[0] - plainTarget.getHealth();
			helper.assertTrue(dealt > 0f && dealt <= b.pulseDamage + 1.0e-3f, "the first pulse has landed: 2 damage less armour, dealt " + dealt);
			helper.assertTrue(near(plain.getHealth(), 10f + b.pulseHeal), "the pulse heals the caster 1.5: " + plain.getHealth());
			helper.assertTrue(near(energy(plain), 30f - cost(plain, ForceDrain.ID, b.startCost) - cost(plain, ForceDrain.ID, b.pulseCost) + b.pulseEnergy),
					"a pulse costs 2 FE and gives 2 back: " + energy(plain));
			helper.assertTrue(near(life.getHealth(), 10f + b.pulseHeal * b.lifeHealMultiplier), "Drain Life heals twice as much: " + life.getHealth());
			float lifeDealt = health[1] - lifeTarget.getHealth();
			helper.assertTrue(lifeDealt > 0f && lifeDealt <= b.pulseDamage * b.lifeDamageMultiplier + 1.0e-3f, "and deals three quarters: " + lifeDealt);
			helper.assertTrue(near(energy(essence), 30f - cost(essence, ForceDrain.ID, b.startCost) - cost(essence, ForceDrain.ID, b.pulseCost) + b.pulseEnergy + b.essenceEnergy),
					"Drain Essence gives 4 FE more a pulse: " + energy(essence));
		});
		// Five pulses in, before the sixth: an early end (a target shoved out of reach) shows here, not as a short heal.
		step(helper, cleanup, b.pulseTicks * (b.maxPulses - 1) + 5, () -> {
			for (int i = 0; i < 3; i++) {
				Vec3 at = targets[i].position();
				double moved = Math.hypot(at.x - stood[i].x, at.z - stood[i].z);
				helper.assertTrue(ForceDrain.isDraining(casters[i]) && ForceDrain.targetOf(casters[i]) == targets[i], "five pulses in, every channel still holds its target");
				helper.assertTrue(moved < 0.25, "a pulse draws, it does not shove: the target stays where it stood, moved " + moved);
			}

			helper.assertTrue(near(plain.getHealth(), 10f + (b.maxPulses - 1) * b.pulseHeal), "five pulses heal 7.5: " + plain.getHealth());
		});
		last(helper, cleanup, b.pulseTicks * b.maxPulses + 10, () -> {
			helper.assertTrue(!ForceDrain.isDraining(plain) && !plainTarget.hasEffect(ForceMobEffects.DRAINED), "the channel ends after its sixth pulse and lets go");
			helper.assertTrue(near(plain.getHealth(), 10f + b.maxPulses * b.pulseHeal), "six pulses heal 9: " + plain.getHealth());
			helper.assertTrue(cooldown(plain, ForceDrain.ID) == ForcePowers.cooldown(plain, ForceDrain.ID, b.cooldownTicks), "the 300-tick cooldown runs from the end");
			helper.assertTrue(Progression.get(plain).mastery(ForceDrain.ID) == b.maxPulses, "a mastery point per pulse that lands");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void drainRefusedOnDroidsAndFighters(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ForceAbility drain = power(helper, ForceDrain.ID);
		FakePlayer caster = caster(helper, cleanup, new BlockPos(3, 1, 2), ForceDrain.ID);
		Mob droid = still(helper, cleanup, DroidEntities.SECURITY_DROID, new BlockPos(3, 1, 7));
		Mob poacher = poacher(helper, cleanup, new BlockPos(20, 1, 7));
		LivingEntity stand = cleanup.entity(helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(12, 1, 7)));

		try {
			helper.assertTrue(!ForceDrain.hasLife(droid) && !ForceDrain.hasLife(stand) && ForceDrain.hasLife(poacher), "droids and armour stands have no life to drain");
			aim(caster, ForcePowers.chest(droid));
			float before = energy(caster);
			drain.onInput(caster, AbilityAction.START);
			helper.assertTrue(!ForceDrain.isDraining(caster) && near(energy(caster), before) && cooldown(caster, ForceDrain.ID) == 0,
					"Drain on a droid is refused: no channel, nothing spent, no cooldown");

			List<LivingEntity> fighters = created(level, ForceDrain.SHIP_TARGETS);

			for (LivingEntity fighter : fighters) {
				helper.assertTrue(!ForceDrain.hasLife(fighter), fighter.getType().toShortString() + " (#oldrepublic:ship_targets) has no life to drain");
			}

			if (fighters.isEmpty()) {
				OldRepublicMod.LOGGER.info("drainRefusedOnDroidsAndFighters: #oldrepublic:ship_targets has no living member yet (W2's tag)");
			}

			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	// ---------------------------------------------------------------- Force Rage (9.7)

	@GameTest(structure = ARENA, maxTicks = 100)
	public void rageModifiersAndNoRegeneration(GameTestHelper helper) {
		RageBalance b = new RageBalance();
		helper.assertTrue(near(b.cost, 30) && b.cooldownTicks == 900 && b.durationTicks == 200 && near(b.meleeDamagePct, 30) && near(b.attackSpeedPct, 15)
				&& near(b.moveSpeedPct, 10) && near(b.damageTakenPct, 10) && near(b.coldDamagePct, 20) && near(b.unboundHeal, 1),
				"Rage: 30 FE, 900 ticks, 200 ticks of +30% melee, +15% attack speed, +10% speed, +10% damage taken");
		helper.assertTrue(near(ForceRage.damageFraction(b, false), 0.3) && near(ForceRage.damageFraction(b, true), 0.2), "Cold Fury: +20% instead of +30%");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ForceAbility rage = power(helper, ForceRage.ID);
		FakePlayer fury = caster(helper, cleanup, new BlockPos(3, 1, 2), ForceRage.ID);
		FakePlayer cold = caster(helper, cleanup, new BlockPos(6, 1, 2), ForceRage.ID, ForceRage.COLD);
		FakePlayer thirst = caster(helper, cleanup, new BlockPos(9, 1, 2), ForceRage.ID, ForceRage.UNBOUND);
		ServerPlayer raging = listed(helper, cleanup, new BlockPos(3, 1, 20), ForceRage.ID);
		ServerPlayer calm = listed(helper, cleanup, new BlockPos(20, 1, 20), ForceRage.ID);
		Mob dummy = poacher(helper, cleanup, new BlockPos(9, 1, 5));
		float[] start = new float[2];

		guarded(cleanup, () -> {
			float full = energy(fury);
			rage.onInput(fury, AbilityAction.START);
			helper.assertTrue(remaining(fury, ForceMobEffects.RAGE) == b.durationTicks && ForceRage.isRaging(fury), "Rage lasts 200 ticks");
			helper.assertTrue(near(full - energy(fury), cost(fury, ForceRage.ID, b.cost))
					&& cooldown(fury, ForceRage.ID) == ForcePowers.cooldown(fury, ForceRage.ID, b.cooldownTicks), "30 FE and the 900-tick cooldown");
			helper.assertTrue(near(modifier(fury, Attributes.ATTACK_DAMAGE, ForceRage.DAMAGE_ID), 0.3)
					&& near(modifier(fury, Attributes.ATTACK_SPEED, ForceRage.ATTACK_SPEED_ID), 0.15)
					&& near(modifier(fury, Attributes.MOVEMENT_SPEED, ForceRage.MOVE_SPEED_ID), 0.1), "+30% damage, +15% attack speed, +10% speed");
			helper.assertTrue(Progression.get(fury).counter(Progression.MOD_REGEN_PCT) == -100, "no Force Energy regeneration while it lasts");
			helper.assertTrue(near(ForceRage.modifyDamage(fury, level.damageSources().generic(), 10f), 10f * (1f + b.damageTakenPct / 100f)),
					"the price: 10% more damage taken");

			rage.onInput(cold, AbilityAction.START);
			helper.assertTrue(near(modifier(cold, Attributes.ATTACK_DAMAGE, ForceRage.DAMAGE_ID), 0.2) && near(ForceRage.modifyDamage(cold, level.damageSources().generic(), 10f), 10f),
					"Cold Fury: +20% and no damage-taken penalty");

			thirst.setItemInHand(InteractionHand.MAIN_HAND, litSaber(thirst));
			rage.onInput(thirst, AbilityAction.START);
			thirst.setHealth(10f);
			ForceRage.onDamaged(dummy, level.damageSources().playerAttack(thirst), 4f);
			helper.assertTrue(near(thirst.getHealth(), 10f + b.unboundHeal), "Bloodthirst: a saber hit heals 1, health " + thirst.getHealth());
			helper.assertTrue(Progression.get(thirst).mastery(ForceRage.ID) == 1, "a hit landed during Rage earns a mastery point");

			rage.onInput(raging, AbilityAction.START);
			helper.assertTrue(ForceRage.isRaging(raging), "the listed player rages");

			for (ServerPlayer p : List.of(raging, calm)) {
				float low = Progression.get(p).maxEnergy - 20f;
				setEnergy(p, low);
			}

			start[0] = energy(raging);
			start[1] = energy(calm);
		});
		last(helper, cleanup, 70, () -> {
			helper.assertTrue(energy(calm) > start[1] + 1f, "a player without Rage regenerates: " + start[1] + " to " + energy(calm));
			helper.assertTrue(near(energy(raging), start[0]), "a raging player regenerates nothing: " + start[0] + " to " + energy(raging));
		});
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void valorAndRageModifiersFollowTheEffect(GameTestHelper helper) {
		Cleanup cleanup = new Cleanup();
		ForceAbility valor = power(helper, ForceValor.ID);
		ForceAbility rage = power(helper, ForceRage.ID);
		ServerPlayer master = listed(helper, cleanup, new BlockPos(3, 1, 3), ForceValor.ID);
		ServerPlayer darth = listed(helper, cleanup, new BlockPos(20, 1, 20), ForceRage.ID);
		ServerPlayer brief = listed(helper, cleanup, new BlockPos(20, 1, 3));

		guarded(cleanup, () -> {
			valor.onInput(master, AbilityAction.START);
			rage.onInput(darth, AbilityAction.START);
			helper.assertTrue(hasModifier(master, Attributes.ARMOR, ForceValor.ARMOR_ID) && hasModifier(darth, Attributes.ATTACK_SPEED, ForceRage.ATTACK_SPEED_ID),
					"the casts add their modifiers");
			ForceMobEffects.apply(brief, ForceMobEffects.VALOR, 5, brief);
		});

		// A listed mock player has no live connection, so nothing runs its full player tick and its effects would never count
		// down: tick it by hand (a level that also ticked it would only make the 5 ticks run out sooner).
		for (int t = 1; t <= 8; t++) {
			step(helper, cleanup, t, brief::doTick);
		}

		step(helper, cleanup, 2, () -> {
			helper.assertTrue(hasModifier(brief, Attributes.ATTACK_DAMAGE, ForceValor.DAMAGE_ID), "the effect without its modifiers (a login) gets them back");
			// Milk or /effect clear.
			master.removeEffect(ForceMobEffects.VALOR);
			darth.removeEffect(ForceMobEffects.RAGE);
		});
		step(helper, cleanup, 5, () -> {
			helper.assertTrue(!hasModifier(master, Attributes.ATTACK_DAMAGE, ForceValor.DAMAGE_ID) && !hasModifier(master, Attributes.ARMOR, ForceValor.ARMOR_ID)
					&& !hasModifier(master, Attributes.KNOCKBACK_RESISTANCE, ForceValor.KNOCKBACK_ID), "Valor's modifiers go when its effect is removed");
			helper.assertTrue(!hasModifier(darth, Attributes.ATTACK_DAMAGE, ForceRage.DAMAGE_ID) && !hasModifier(darth, Attributes.ATTACK_SPEED, ForceRage.ATTACK_SPEED_ID)
					&& !hasModifier(darth, Attributes.MOVEMENT_SPEED, ForceRage.MOVE_SPEED_ID), "Rage's modifiers go when its effect is removed");
			helper.assertTrue(Progression.get(darth).counter(Progression.MOD_REGEN_PCT) != -100, "and the regeneration comes back");
			ForceMobEffects.apply(master, ForceMobEffects.VALOR, 100, master);
			ForceMobEffects.apply(darth, ForceMobEffects.RAGE, 100, darth);
		});
		step(helper, cleanup, 10, () -> {
			helper.assertTrue(near(modifier(master, Attributes.ATTACK_DAMAGE, ForceValor.DAMAGE_ID), 0.2), "a player who comes back with Valor gets its modifiers");
			helper.assertTrue(near(modifier(darth, Attributes.ATTACK_DAMAGE, ForceRage.DAMAGE_ID), 0.3)
					&& Progression.get(darth).counter(Progression.MOD_REGEN_PCT) == -100, "and with Rage, its modifiers and no regeneration");
			helper.assertTrue(!brief.hasEffect(ForceMobEffects.VALOR) && !hasModifier(brief, Attributes.ATTACK_DAMAGE, ForceValor.DAMAGE_ID),
					"when the effect runs out its modifiers go");
		});
		last(helper, cleanup, 12, () -> {
		});
	}

	// ---------------------------------------------------------------- branches, mastery and the trees (9.3 to 9.9)

	@GameTest(maxTicks = 20)
	public void branchesExclusive(GameTestHelper helper) {
		SkillTreeSet trees = SkillTrees.server();
		int penalty = new SkillBalance().switchPenalty;
		String[][] groups = {
				{"stasis_shape", ForceStasis.ID, ForceStasis.FIELD, ForceStasis.LOCK},
				{"valor_shape", ForceValor.ID, ForceValor.SHARED, ForceValor.STEADFAST},
				{"drain_shape", ForceDrain.ID, ForceDrain.LIFE, ForceDrain.ESSENCE},
				{"fear_shape", ForceFear.ID, ForceFear.TERROR, ForceFear.NIGHTMARE},
				{"rage_shape", ForceRage.ID, ForceRage.UNBOUND, ForceRage.COLD},
				{"mind_trick_shape", MindTrick.ID, MindTrick.CONFUSE, MindTrick.MASS}};

		for (String[] g : groups) {
			List<String> members = trees.group(g[0]).stream().map(SkillNode::id).sorted().toList();
			helper.assertTrue(members.equals(List.of(g[2], g[3]).stream().sorted().toList()), g[0] + " holds exactly " + g[2] + " and " + g[3] + ", found " + members);

			for (int i = 2; i <= 3; i++) {
				String owned = g[i];
				String other = g[i == 2 ? 3 : 2];
				SkillRules.LearnCheck swap = SkillRules.check(trees, owning(g[1], owned), context(20, Path.INDEPENDENT), other, penalty);
				helper.assertTrue(swap.ok() && swap.isSwitch() && swap.cost() == 1 + penalty && swap.replaces() != null && owned.equals(swap.replaces().id()),
						owned + " and " + other + " are one choice: owning " + owned + ", " + other + " is a switch for " + (1 + penalty) + " points");
			}
		}

		Cleanup cleanup = new Cleanup();
		FakePlayer learner = caster(helper, cleanup, new BlockPos(1, 1, 1));

		try {
			Identity.setPath(learner, Path.JEDI, true);
			ForceData data = Progression.get(learner);
			data.level = 20;
			data.skillPoints = 10;
			Progression.commit(learner, data);
			Skills.grant(learner, ForceStasis.ID);
			int switchPenalty = SkillService.balance().switchPenalty;
			helper.assertTrue(SkillService.learn(learner, ForceStasis.FIELD) == LearnResult.OK, "Stasis Field is bought");
			helper.assertTrue(SkillService.learn(learner, ForceStasis.LOCK) == LearnResult.OK, "switching to Stasis Lock is allowed out of combat");
			SkillData skills = SkillService.data(learner);
			helper.assertTrue(skills.owns(ForceStasis.LOCK) && !skills.owns(ForceStasis.FIELD), "the switch removes Stasis Field");
			helper.assertTrue(Progression.get(learner).skillPoints == 10 - 1 - (1 + switchPenalty), "one point, then one plus the switch penalty: "
					+ Progression.get(learner).skillPoints + " left");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void masteryThresholds(GameTestHelper helper) {
		helper.assertTrue(Progression.masteryRank(49) == 0 && Progression.masteryRank(50) == 1 && Progression.masteryRank(150) == 2
				&& Progression.masteryRank(400) == 3, "mastery ranks at 50, 150 and 400 points");
		StasisBalance stasis = new StasisBalance();
		ValorBalance valor = new ValorBalance();
		DrainBalance drain = new DrainBalance();
		FearBalance fear = new FearBalance();
		RageBalance rage = new RageBalance();
		MindTrickBalance trick = new MindTrickBalance();
		helper.assertTrue(ForceStasis.masteryTicks(stasis, 0) == 0 && ForceStasis.masteryTicks(stasis, 1) == 5 && ForceStasis.masteryTicks(stasis, 2) == 10
				&& ForceStasis.masteryTicks(stasis, 3) == 20, "Stasis: +5, +10, +20 ticks");
		helper.assertTrue(ForceStasis.ticks(stasis, false, false, 3) == 80 && ForceStasis.ticks(stasis, true, false, 3) == 60
				&& ForceStasis.ticks(stasis, false, true, 3) == 120, "Stasis at rank 3: 80, the field 60, the lock 120");
		helper.assertTrue(ForceValor.ticks(valor, 0) == 200 && ForceValor.ticks(valor, 3) == 260, "Valor: +20 ticks a rank");
		helper.assertTrue(near(ForceDrain.pulseDamage(drain, false, 0), 2) && near(ForceDrain.pulseDamage(drain, false, 3), 2.75)
				&& near(ForceDrain.pulseDamage(drain, true, 3), 2.75 * 0.75), "Drain: +0.25 damage a pulse a rank (Drain Life's x0.75 after)");
		helper.assertTrue(near(ForceDrain.pulseHeal(drain, true), 3) && near(ForceDrain.pulseEnergy(drain, true), 6), "Drain Life heals 3, Drain Essence gives 6 FE");
		helper.assertTrue(ForceFear.ticks(fear, false, false, 3) == 110 && ForceFear.ticks(fear, true, false, 3) == 90 && ForceFear.ticks(fear, false, true, 3) == 190,
				"Fear: +10 ticks a rank (the cone 110, Terror 90, Nightmare 190 at rank 3)");
		helper.assertTrue(ForceRage.ticks(rage, 3) == 260, "Rage: +20 ticks a rank");
		helper.assertTrue(MindTrick.ticks(trick, false, false, 0) == 120 && MindTrick.ticks(trick, false, false, 3) == 180 && MindTrick.ticks(trick, true, false, 0) == 80
				&& MindTrick.ticks(trick, false, true, 0) == 80, "Mind Trick: 120, Confusion and Many Minds 80, +20 a rank");

		Cleanup cleanup = new Cleanup();
		ForceAbility power = power(helper, ForceStasis.ID);
		FakePlayer master = caster(helper, cleanup, new BlockPos(3, 1, 2), ForceStasis.ID);
		Mob target = poacher(helper, cleanup, new BlockPos(3, 1, 8));

		try {
			Progression.addMastery(master, ForceStasis.ID, 400);
			helper.assertTrue(ForcePowers.masteryRank(master, ForceStasis.ID) == 3, "400 points is rank 3");
			aim(master, ForcePowers.chest(target));
			power.onInput(master, AbilityAction.START);
			helper.assertTrue(remaining(target, ForceMobEffects.STASIS) == 80, "a rank 3 Stasis holds for 80 ticks, held " + remaining(target, ForceMobEffects.STASIS));
			helper.assertTrue(Progression.get(master).mastery(ForceStasis.ID) == 401, "and earns its point");
			helper.succeed();
		} finally {
			StasisHolds.release(target);
			cleanup.run();
		}
	}

	@GameTest(maxTicks = 20)
	public void newNodesVisibleAndPurchasable(GameTestHelper helper) {
		SkillTreeSet trees = SkillTrees.server();
		int penalty = new SkillBalance().switchPenalty;
		Map<String, String> treeOf = Map.ofEntries(Map.entry(ForceStasis.ID, SkillIds.TREE_JEDI), Map.entry(ForceStasis.FIELD, SkillIds.TREE_JEDI),
				Map.entry(ForceStasis.LOCK, SkillIds.TREE_JEDI), Map.entry(ForceValor.ID, SkillIds.TREE_JEDI), Map.entry(ForceValor.SHARED, SkillIds.TREE_JEDI),
				Map.entry(ForceValor.STEADFAST, SkillIds.TREE_JEDI), Map.entry(ForceDrain.ID, SkillIds.TREE_SITH), Map.entry(ForceDrain.LIFE, SkillIds.TREE_SITH),
				Map.entry(ForceDrain.ESSENCE, SkillIds.TREE_SITH), Map.entry(ForceFear.ID, SkillIds.TREE_SITH), Map.entry(ForceFear.TERROR, SkillIds.TREE_SITH),
				Map.entry(ForceFear.NIGHTMARE, SkillIds.TREE_SITH), Map.entry(ForceRage.ID, SkillIds.TREE_SITH), Map.entry(ForceRage.UNBOUND, SkillIds.TREE_SITH),
				Map.entry(ForceRage.COLD, SkillIds.TREE_SITH), Map.entry(MindTrick.ID, SkillIds.TREE_EXPLORATION), Map.entry(MindTrick.CONFUSE, SkillIds.TREE_EXPLORATION),
				Map.entry(MindTrick.MASS, SkillIds.TREE_EXPLORATION), Map.entry(SkillIds.WAYFARERS_STRIDE, SkillIds.TREE_EXPLORATION),
				Map.entry("form_makashi", SkillIds.TREE_SABER), Map.entry("form_djem_so", SkillIds.TREE_SABER), Map.entry("form_jar_kai", SkillIds.TREE_SABER));
		helper.assertTrue(trees.errors().isEmpty(), "the trees load without errors: " + trees.errors());

		for (Map.Entry<String, String> entry : treeOf.entrySet()) {
			SkillNode node = trees.node(entry.getKey());
			helper.assertTrue(node != null && entry.getValue().equals(node.tree()), entry.getKey() + " is in the " + entry.getValue() + " tree");
		}

		for (String id : POWERS) {
			SkillNode node = trees.node(id);
			helper.assertTrue(node != null && node.isAbility() && Abilities.get(id) != null && node == trees.abilityNode(id), id + " is an ability node of a registered power");
		}

		helper.assertTrue(trees.node(ForceStasis.ID).taughtBy().equals(List.of(Path.JEDI)) && trees.node(ForceDrain.ID).taughtBy().equals(List.of(Path.SITH))
				&& trees.node(ForceFear.ID).taughtBy().equals(List.of(Path.SITH)) && trees.node(MindTrick.ID).taughtBy().isEmpty(),
				"Stasis is taught by the Jedi mentor, Drain and Fear by the Sith; Mind Trick has no teacher");
		Set<String> before = SkillRules.visibleTrees(trees, true, true, false, false);
		Set<String> after = SkillRules.visibleTrees(trees, true, true, true, true);
		helper.assertTrue(!before.contains(SkillIds.TREE_JEDI) && !before.contains(SkillIds.TREE_SITH) && !before.contains(SkillIds.TREE_EXPLORATION),
				"the Jedi and Sith tabs wait for the Crossing, Exploration for Force Sense");
		helper.assertTrue(after.containsAll(Set.of(SkillIds.TREE_JEDI, SkillIds.TREE_SITH, SkillIds.TREE_EXPLORATION, SkillIds.TREE_SABER)), "then every new node shows");

		SkillData none = new SkillData();
		expect(helper, trees, none, 15, Path.JEDI, ForceStasis.ID, penalty, LearnResult.OK);
		expect(helper, trees, none, 14, Path.JEDI, ForceStasis.ID, penalty, LearnResult.LEVEL_TOO_LOW);
		expect(helper, trees, owning(ForceStasis.ID), 16, Path.JEDI, ForceStasis.FIELD, penalty, LearnResult.OK);
		expect(helper, trees, owning(ForceStasis.ID), 15, Path.JEDI, ForceStasis.LOCK, penalty, LearnResult.LEVEL_TOO_LOW);
		expect(helper, trees, none, 16, Path.JEDI, ForceStasis.LOCK, penalty, LearnResult.MISSING_REQUIREMENT);
		expect(helper, trees, none, 20, Path.JEDI, ForceValor.ID, penalty, LearnResult.GRANTED_ONLY);
		expect(helper, trees, owning(ForceValor.ID), 20, Path.JEDI, ForceValor.SHARED, penalty, LearnResult.OK);
		expect(helper, trees, owning(ForceValor.ID), 19, Path.JEDI, ForceValor.STEADFAST, penalty, LearnResult.LEVEL_TOO_LOW);

		expect(helper, trees, none, 15, Path.SITH, ForceDrain.ID, penalty, LearnResult.OK);
		expect(helper, trees, none, 14, Path.SITH, ForceDrain.ID, penalty, LearnResult.LEVEL_TOO_LOW);
		expect(helper, trees, owning(ForceDrain.ID), 16, Path.SITH, ForceDrain.LIFE, penalty, LearnResult.OK);
		expect(helper, trees, none, 16, Path.SITH, ForceFear.ID, penalty, LearnResult.OK);
		expect(helper, trees, none, 15, Path.SITH, ForceFear.ID, penalty, LearnResult.LEVEL_TOO_LOW);
		expect(helper, trees, owning(ForceFear.ID), 17, Path.SITH, ForceFear.TERROR, penalty, LearnResult.OK);
		expect(helper, trees, owning(ForceFear.ID), 16, Path.SITH, ForceFear.NIGHTMARE, penalty, LearnResult.LEVEL_TOO_LOW);
		expect(helper, trees, none, 20, Path.SITH, ForceRage.ID, penalty, LearnResult.GRANTED_ONLY);
		expect(helper, trees, owning(ForceRage.ID), 20, Path.SITH, ForceRage.COLD, penalty, LearnResult.OK);

		for (Path path : List.of(Path.JEDI, Path.SITH, Path.INDEPENDENT)) {
			expect(helper, trees, none, 14, path, MindTrick.ID, penalty, LearnResult.OK);
		}

		expect(helper, trees, none, 13, Path.INDEPENDENT, MindTrick.ID, penalty, LearnResult.LEVEL_TOO_LOW);
		expect(helper, trees, owning(MindTrick.ID), 15, Path.INDEPENDENT, MindTrick.MASS, penalty, LearnResult.OK);
		expect(helper, trees, none, 20, Path.INDEPENDENT, SkillIds.WAYFARERS_STRIDE, penalty, LearnResult.GRANTED_ONLY);

		expect(helper, trees, none, 15, Path.JEDI, "form_makashi", penalty, LearnResult.OK);
		expect(helper, trees, none, 14, Path.JEDI, "form_makashi", penalty, LearnResult.LEVEL_TOO_LOW);
		expect(helper, trees, none, 16, Path.SITH, "form_djem_so", penalty, LearnResult.OK);
		expect(helper, trees, none, 17, Path.INDEPENDENT, "form_jar_kai", penalty, LearnResult.OK);
		expect(helper, trees, none, 16, Path.INDEPENDENT, "form_jar_kai", penalty, LearnResult.LEVEL_TOO_LOW);

		Cleanup cleanup = new Cleanup();
		FakePlayer master = caster(helper, cleanup, new BlockPos(1, 1, 1));
		FakePlayer darth = caster(helper, cleanup, new BlockPos(3, 1, 1));

		try {
			Skills.grant(master, ForceValor.ID);
			Skills.grant(darth, ForceRage.ID);
			helper.assertTrue(ForcePowers.knows(master, Progression.get(master), ForceValor.ID) && ForcePowers.knows(darth, Progression.get(darth), ForceRage.ID),
					"the ceremonies' grants teach Valor and Rage");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	@GameTest(maxTicks = 20)
	public void wayfarersStrideBonuses(GameTestHelper helper) {
		SkillBalance b = new SkillBalance();
		helper.assertTrue(b.wayfarerEnergyPct == 10 && b.wayfarerSpeedCostPct == 20, "Wayfarer's Grace: +10% maximum Force Energy, Force Speed 20% cheaper");

		Cleanup cleanup = new Cleanup();
		FakePlayer wayfarer = caster(helper, cleanup, new BlockPos(1, 1, 1), ForceSpeed.ID);
		FakePlayer control = caster(helper, cleanup, new BlockPos(3, 1, 1), ForceSpeed.ID);
		FakePlayer jedi = caster(helper, cleanup, new BlockPos(5, 1, 1), ForceSpeed.ID);

		try {
			Identity.setPath(wayfarer, Path.INDEPENDENT, true);
			Identity.setPath(control, Path.INDEPENDENT, true);
			Identity.setPath(jedi, Path.JEDI, true);

			for (FakePlayer p : List.of(wayfarer, control, jedi)) {
				setLevel(p, 18);
			}

			float plainMax = Progression.get(control).maxEnergy;
			float plainCost = ForcePowers.costMultiplier(control, ForceSpeed.ID);
			float jediCost = ForcePowers.costMultiplier(jedi, ForceSpeed.ID);
			Skills.grant(wayfarer, SkillIds.WAYFARERS_STRIDE);
			Skills.grant(jedi, SkillIds.WAYFARERS_STRIDE);
			SkillService.refresh(wayfarer);
			SkillService.refresh(jedi);

			helper.assertTrue(Skills.active(wayfarer, SkillIds.WAYFARERS_STRIDE), "an Independent's Wayfarer's Grace is awake");
			helper.assertTrue(Progression.get(wayfarer).counter(Progression.MOD_MAX_ENERGY_PCT) == b.wayfarerEnergyPct
					&& Math.abs(Progression.get(wayfarer).maxEnergy - plainMax * 1.1f) < 0.05f,
					"+10% maximum Force Energy: " + plainMax + " to " + Progression.get(wayfarer).maxEnergy);
			helper.assertTrue(near(ForcePowers.costMultiplier(wayfarer, ForceSpeed.ID), plainCost * 0.8f), "Force Speed costs 20% less");
			helper.assertTrue(near(ForcePowers.costMultiplier(wayfarer, ForceStasis.ID), ForcePowers.costMultiplier(control, ForceStasis.ID)),
					"and no other power is cheaper");

			helper.assertTrue(Skills.dormant(jedi, SkillIds.WAYFARERS_STRIDE) && Progression.get(jedi).counter(Progression.MOD_MAX_ENERGY_PCT) == 0
					&& near(ForcePowers.costMultiplier(jedi, ForceSpeed.ID), jediCost), "on another path the passive sleeps");

			Identity.recordChoice(wayfarer, SkillService.WAYFARER_TITLE);
			SkillService.refresh(wayfarer);
			helper.assertTrue(SkillService.data(wayfarer).openSlots2 == 4, "the Wayfarer title opens the whole second bar");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}
}
