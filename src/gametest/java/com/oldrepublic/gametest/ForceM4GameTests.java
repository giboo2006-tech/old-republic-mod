package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.droid.DroidEntities;
import com.oldrepublic.enemy.mandalorian.HunterDamage;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForceM4Feature;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.barrier.BarrierBalance;
import com.oldrepublic.force.barrier.ForceBarrier;
import com.oldrepublic.force.choke.ChokeBalance;
import com.oldrepublic.force.choke.ChokedTargets;
import com.oldrepublic.force.choke.ForceChoke;
import com.oldrepublic.force.lift.ForceLift;
import com.oldrepublic.force.speed.ForceSpeed;
import com.oldrepublic.force.speed.SpeedBalance;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;
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

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 4 Force powers (spec MILESTONE_4.md 9 and 17.1): Force Speed (cost, duration, the
 * cancels, the Burst dash, Wayfarer's turned shots and safe falls), Force Barrier (start, channel
 * and bolt costs, heavy bolts, the release pulse, Shelter, Return, the break), Force Choke
 * (breathing targets, diminishing returns, PvP, the once-a-day alignment shift, Crush, Dread,
 * Cruel Focus, the reaction hook bosses answer), the Lift path nodes, the 9.5 tree additions and
 * the mastery numbers.
 *
 * <p>Casters are survival fake players: they are never ticked (no regeneration, no cooldown
 * countdown) and projectiles fly through them, so energy and cooldowns stay exactly where a cast
 * put them. Every test is in the {@code oldrepublic_test:arena_24x7x24} fixture; whatever a test
 * starts (runs, channels, holds) and spawns is undone when it ends, whichever step ends it
 * ({@link Cleanup}). The one test hook ({@link #registerHooks}) answers only for the creatures a
 * test marks.
 */
public class ForceM4GameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";

	/** Creatures the test choke hook refuses (a boss's answer, 9.3). */
	private static final Set<UUID> NO_CHOKE = new HashSet<>();
	private static boolean hooksRegistered;

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

		/** Ends the player's Speed run, Barrier and Choke; logs a listed player out. */
		<T extends ServerPlayer> T player(T player) {
			undo.add(() -> {
				ForceM4Feature.forgetAll(player, false);
				M3TestPlayers.remove(player);
			});
			return player;
		}

		void then(Runnable action) {
			undo.add(action);
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

	private static void registerHooks() {
		if (hooksRegistered) {
			return;
		}

		hooksRegistered = true;
		ForceReactions.register(new ForceReactions.Handler() {
			@Override
			public int chokeTicks(LivingEntity target, int requested) {
				return NO_CHOKE.contains(target.getUUID()) ? 0 : requested;
			}
		});
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-3;
	}

	/**
	 * A survival fake player standing at {@code relative}, facing +z: awakened, knowing
	 * {@code abilities} (powers and upgrade nodes alike, as the grant command does), energy full.
	 */
	private static FakePlayer caster(GameTestHelper helper, Cleanup cleanup, BlockPos relative, String... abilities) {
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "force_m4_test"));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(relative)));
		face(player, 0f, 0f);
		ForceData data = Progression.get(player);
		data.questStage = QuestStage.FOCUS.ordinal();
		data.abilities.addAll(List.of(abilities));
		data.energy = data.maxEnergy;
		Progression.commit(player, data);
		helper.assertTrue(!player.isCreative(), "the fake caster must be in survival");
		return cleanup.player(player);
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

	private static void refill(ServerPlayer player) {
		ForceData data = Progression.get(player);
		data.energy = data.maxEnergy;
		Progression.commit(player, data);
	}

	private static int cooldown(ServerPlayer player, String id) {
		return Progression.get(player).cooldown(id);
	}

	private static boolean hasModifier(LivingEntity entity, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = entity.getAttribute(attribute);
		return instance != null && instance.hasModifier(id);
	}

	/** Movement speed over its base: 1.4 while Force Speed's +40% is the only modifier. */
	private static double speedRatio(LivingEntity entity) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		return speed == null ? 0.0 : speed.getValue() / speed.getBaseValue();
	}

	/** A NoFreeWill crystal poacher: hostile, breathing, not push-resistant, never burns. */
	private static Mob poacher(GameTestHelper helper, Cleanup cleanup, BlockPos relative) {
		return cleanup.entity(helper.spawnWithNoFreeWill(EnemyEntities.CRYSTAL_POACHER, relative));
	}

	/** A bolt four blocks in front of {@code target}'s chest flying back at it, {@code speed} blocks a tick. */
	private static BlasterBolt boltAt(ServerLevel level, @Nullable Entity shooter, ServerPlayer target, boolean heavy, double speed, int lifetime) {
		BlasterBolt bolt = BlasterBolt.create(level, shooter, ForcePowers.chest(target).add(0, 0, 4), 4f, lifetime, BlasterBolt.Style.POACHER, heavy);
		bolt.setDeltaMovement(new Vec3(0, 0, -speed));
		level.addFreshEntity(bolt);
		return bolt;
	}

	// ---------------------------------------------------------------- Force Speed (9.1)

	@GameTest(structure = ARENA, maxTicks = 200)
	public void speedDurationCostAndCancels(GameTestHelper helper) {
		SpeedBalance b = new SpeedBalance();
		helper.assertTrue(near(ForceSpeed.cost(b, 0, false), 20) && near(ForceSpeed.cost(b, 0, true), 25) && near(ForceSpeed.cost(b, 3, false), 14),
				"Force Speed costs 20 FE, 25 with The Long Road, 14 at mastery rank 3");
		helper.assertTrue(ForceSpeed.duration(b, 0, false) == 160 && ForceSpeed.duration(b, 0, true) == 300 && ForceSpeed.duration(b, 3, false) == 220,
				"a run lasts 160 ticks, 300 with The Long Road, 220 at mastery rank 3");

		Cleanup cleanup = new Cleanup();
		ForceAbility speed = power(helper, ForceSpeed.ID);
		FakePlayer runner = caster(helper, cleanup, new BlockPos(3, 1, 3), ForceSpeed.ID);
		FakePlayer timed = caster(helper, cleanup, new BlockPos(20, 1, 3), ForceSpeed.ID);

		guarded(cleanup, () -> {
			float full = energy(runner);
			float mult = ForcePowers.costMultiplier(runner, ForceSpeed.ID);
			speed.onInput(runner, AbilityAction.START);
			helper.assertTrue(ForceSpeed.isRunning(runner) && near(energy(runner), full - b.cost * mult),
					"a cast starts a run for 20 FE, energy " + energy(runner));
			helper.assertTrue(hasModifier(runner, Attributes.MOVEMENT_SPEED, ForceSpeed.SPEED_MODIFIER)
					&& hasModifier(runner, Attributes.STEP_HEIGHT, ForceSpeed.STEP_MODIFIER), "the run adds the speed and step modifiers");
			helper.assertTrue(near(speedRatio(runner), 1.0 + b.speedPct / 100.0), "the run is +40% speed, ratio " + speedRatio(runner));

			float before = energy(runner);
			speed.onInput(runner, AbilityAction.START);
			helper.assertTrue(!ForceSpeed.isRunning(runner) && !hasModifier(runner, Attributes.MOVEMENT_SPEED, ForceSpeed.SPEED_MODIFIER)
					&& !hasModifier(runner, Attributes.STEP_HEIGHT, ForceSpeed.STEP_MODIFIER), "a second cast ends the run and drops its modifiers");
			helper.assertTrue(near(energy(runner), before) && cooldown(runner, ForceSpeed.ID) == ForcePowers.cooldown(runner, ForceSpeed.ID, b.cooldownTicks),
					"ending early refunds nothing and starts the 600-tick cooldown");
			speed.onInput(runner, AbilityAction.START);
			helper.assertTrue(!ForceSpeed.isRunning(runner) && near(energy(runner), before), "no run starts during the cooldown");

			Progression.setCooldown(runner, ForceSpeed.ID, 0);
			speed.onInput(runner, AbilityAction.START);
			helper.assertTrue(ForceSpeed.isRunning(runner), "off cooldown a run starts again");
			runner.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 0));

			speed.onInput(timed, AbilityAction.START);
			helper.assertTrue(ForceSpeed.isRunning(timed), "the timed run starts");
		});
		step(helper, cleanup, 3, () -> helper.assertTrue(!ForceSpeed.isRunning(runner)
				&& !hasModifier(runner, Attributes.MOVEMENT_SPEED, ForceSpeed.SPEED_MODIFIER), "slowness (a stagger or a shock) ends the run"));
		step(helper, cleanup, b.durationTicks - 5, () -> helper.assertTrue(ForceSpeed.isRunning(timed), "the run is still up just before 160 ticks"));
		last(helper, cleanup, b.durationTicks + 5, () -> {
			helper.assertTrue(!ForceSpeed.isRunning(timed) && !hasModifier(timed, Attributes.MOVEMENT_SPEED, ForceSpeed.SPEED_MODIFIER),
					"the run ends after 160 ticks and its modifiers go");
			helper.assertTrue(cooldown(timed, ForceSpeed.ID) == ForcePowers.cooldown(timed, ForceSpeed.ID, b.cooldownTicks), "the cooldown starts when the run ends");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void speedBurstDash(GameTestHelper helper) {
		SpeedBalance b = new SpeedBalance();
		helper.assertTrue(near(ForceSpeed.speedBonus(b, true, 0), 1.0) && near(ForceSpeed.speedBonus(b, true, b.burstTicks - 1), 1.0)
				&& near(ForceSpeed.speedBonus(b, true, b.burstTicks), 0.4) && near(ForceSpeed.speedBonus(b, false, 0), 0.4),
				"Burst runs at +100% for its first 20 ticks, then +40% like a plain run");

		Cleanup cleanup = new Cleanup();
		ForceAbility speed = power(helper, ForceSpeed.ID);
		helper.setBlock(new BlockPos(8, 1, 6), Blocks.STONE);
		helper.setBlock(new BlockPos(8, 2, 6), Blocks.STONE);
		FakePlayer walled = caster(helper, cleanup, new BlockPos(8, 1, 3), ForceSpeed.ID, ForceSpeed.BURST);
		FakePlayer open = caster(helper, cleanup, new BlockPos(16, 1, 3), ForceSpeed.ID, ForceSpeed.BURST);

		guarded(cleanup, () -> {
			Vec3 south = new Vec3(0, 0, 1);
			double free = ForceSpeed.dashDistance(open, south, b);
			double blocked = ForceSpeed.dashDistance(walled, south, b);
			helper.assertTrue(near(free, b.burstDistance), "in the open the dash goes the full 6 blocks, went " + free);
			helper.assertTrue(Math.abs(blocked - 2.0) < 0.05, "a wall 2.5 blocks ahead stops the dash half a block short, went " + blocked);

			speed.onInput(open, AbilityAction.START);
			helper.assertTrue(ForceSpeed.isRunning(open) && near(speedRatio(open), 1.0 + b.burstSpeedPct / 100.0),
					"a Burst run starts at +100% speed, ratio " + speedRatio(open));
		});
		last(helper, cleanup, b.burstTicks + 3, () -> helper.assertTrue(ForceSpeed.isRunning(open) && near(speedRatio(open), 1.0 + b.speedPct / 100.0),
				"after its first second the Burst run settles to +40%, ratio " + speedRatio(open)));
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void wayfarerMissChanceNeverThroughWalls(GameTestHelper helper) {
		SpeedBalance b = new SpeedBalance();
		Vec3 chest = new Vec3(0.5, 2.0, 10.5);
		Vec3 v = new Vec3(0, 0, 1);
		Vec3 from = chest.subtract(0, 0, 8);
		RandomSource random = RandomSource.create(4242L);
		int bent = 0;
		double widest = 0.0;

		for (int i = 0; i < 1000; i++) {
			Vec3 path = ForceSpeed.wayfarerPath(from, v, chest, b, random.nextFloat());
			helper.assertTrue(path != null, "a shot on course for the chest always rolls");

			if (!path.equals(v)) {
				bent++;
				widest = Math.max(widest, Math.toDegrees(Math.acos(Mth.clamp(path.normalize().dot(v), -1.0, 1.0))));
				helper.assertTrue(near(path.length(), v.length()), "a turned shot keeps its speed");
			}
		}

		helper.assertTrue(bent >= 200 && bent <= 300, "about a quarter of the shots are turned aside, " + bent + " of 1000");
		helper.assertTrue(widest > 0.0 && widest <= b.wayfarerMaxDegrees + 1.0e-3, "a turn is at most 1.5 degrees, widest " + widest);
		helper.assertTrue(!v.equals(ForceSpeed.wayfarerPath(from, v, chest, b, 0.1f)) && v.equals(ForceSpeed.wayfarerPath(from, v, chest, b, 0.3f)),
				"a roll under 0.25 turns the shot, one above keeps it");
		helper.assertTrue(ForceSpeed.wayfarerPath(from.add(2, 0, 0), v, chest, b, 0.1f) == null, "a shot that would miss anyway does not roll");
		helper.assertTrue(ForceSpeed.wayfarerPath(chest.add(0, 0, 3), v, chest, b, 0.1f) == null, "a shot flying away does not roll");
		helper.assertTrue(ForceSpeed.wayfarerPath(chest.subtract(0, 0, 12), v, chest, b, 0.1f) == null, "a shot more than 10 ticks out does not roll yet");

		// A wall just right of a bolt's path: a turn into it is refused, a turn away is allowed.
		for (int z = 8; z <= 14; z++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(21, y, z), Blocks.STONE);
			}
		}

		ServerLevel level = helper.getLevel();
		BlasterBolt probe = BlasterBolt.create(level, null, helper.absoluteVec(new Vec3(20.9, 2.5, 6.5)), 4f, 20);
		double sin = Math.sin(Math.toRadians(b.wayfarerMaxDegrees));
		double cos = Math.cos(Math.toRadians(b.wayfarerMaxDegrees));
		helper.assertTrue(!ForceSpeed.clearAsBefore(level, probe, v, new Vec3(sin, 0, cos)), "a turn that would put the shot into a wall is refused");
		helper.assertTrue(ForceSpeed.clearAsBefore(level, probe, v, new Vec3(-sin, 0, cos)), "a turn into open air is allowed");

		Cleanup cleanup = new Cleanup();
		ForceAbility speed = power(helper, ForceSpeed.ID);
		FakePlayer wayfarer = caster(helper, cleanup, new BlockPos(3, 1, 3), ForceSpeed.ID, ForceSpeed.WAYFARER);
		FakePlayer plain = caster(helper, cleanup, new BlockPos(16, 1, 3), ForceSpeed.ID);

		try {
			speed.onInput(wayfarer, AbilityAction.START);
			speed.onInput(plain, AbilityAction.START);
			helper.assertTrue(ForceSpeed.isWayfaring(wayfarer) && !ForceSpeed.isWayfaring(plain), "only the Wayfarer runner is wayfaring");
			DamageSource fall = level.damageSources().fall();
			wayfarer.fallDistance = 5;
			helper.assertTrue(!ForceSpeed.allowDamage(wayfarer, fall, 3f), "a Wayfarer runner takes nothing from a 5-block fall");
			wayfarer.fallDistance = 10;
			helper.assertTrue(ForceSpeed.allowDamage(wayfarer, fall, 3f), "a fall of 8 blocks or more still hurts");
			plain.fallDistance = 5;
			helper.assertTrue(ForceSpeed.allowDamage(plain, fall, 3f), "a plain runner falls as usual");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	// ---------------------------------------------------------------- Force Barrier (9.2)

	@GameTest(structure = ARENA, maxTicks = 40)
	public void barrierStopsBoltsAndCostsFe(GameTestHelper helper) {
		BarrierBalance b = new BarrierBalance();
		helper.assertTrue(near(ForceBarrier.boltCost(b, false), 3) && near(b.startCost, 15) && near(ForceBarrier.costPerSecond(b, 0), 6),
				"Barrier: 15 FE to raise, 6 FE/s, 3 FE a bolt");
		helper.assertTrue(ForceBarrier.entry(new Vec3(0, 0, 4), new Vec3(0, 0, -1), Vec3.ZERO, 2) == null
				&& near(ForceBarrier.entry(new Vec3(0, 0, 2.5), new Vec3(0, 0, -1), Vec3.ZERO, 2).z, 2.0)
				&& ForceBarrier.entry(new Vec3(0, 0, 1), new Vec3(0, 0, 1), Vec3.ZERO, 2) == null,
				"a projectile is caught on the step that enters the sphere, never on its way out");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ForceAbility barrier = power(helper, ForceBarrier.ID);
		FakePlayer guarded = caster(helper, cleanup, new BlockPos(2, 1, 2), ForceBarrier.ID);
		FakePlayer control = caster(helper, cleanup, new BlockPos(14, 1, 2), ForceBarrier.ID);
		FakePlayer open = caster(helper, cleanup, new BlockPos(20, 1, 2));
		float full = energy(control);
		float mult = ForcePowers.costMultiplier(control, ForceBarrier.ID);
		List<BlasterBolt> bolts = new ArrayList<>();

		guarded(cleanup, () -> {
			barrier.onInput(guarded, AbilityAction.START);
			barrier.onInput(control, AbilityAction.START);
			helper.assertTrue(ForceBarrier.isChannelling(guarded) && ForceBarrier.isChannelling(control), "both barriers are up");
			helper.assertTrue(near(energy(control), full - b.startCost * mult), "raising the barrier costs 15 FE, energy " + energy(control));
			bolts.add(cleanup.entity(boltAt(level, null, guarded, false, 0.5, 14)));
			bolts.add(cleanup.entity(boltAt(level, null, open, false, 0.5, 14)));
		});
		last(helper, cleanup, 8, () -> {
			helper.assertTrue(bolts.get(0).isRemoved(), "the bolt stops at the barrier");
			helper.assertTrue(!bolts.get(1).isRemoved(), "the bolt at the player without a barrier flies on");
			helper.assertTrue(near(energy(control) - energy(guarded), b.boltCost * mult), "a stopped bolt costs 3 FE, paid " + (energy(control) - energy(guarded)));
			helper.assertTrue(ForceBarrier.isChannelling(guarded), "stopping a bolt does not drop the barrier");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void barrierHeavyCostsMore(GameTestHelper helper) {
		BarrierBalance b = new BarrierBalance();
		helper.assertTrue(near(ForceBarrier.boltCost(b, true), 10) && ForceBarrier.boltCost(b, true) > ForceBarrier.boltCost(b, false),
				"a heavy bolt costs 10 FE, more than a plain one");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ForceAbility barrier = power(helper, ForceBarrier.ID);
		FakePlayer light = caster(helper, cleanup, new BlockPos(2, 1, 2), ForceBarrier.ID);
		FakePlayer heavy = caster(helper, cleanup, new BlockPos(8, 1, 2), ForceBarrier.ID);
		FakePlayer control = caster(helper, cleanup, new BlockPos(14, 1, 2), ForceBarrier.ID);
		float mult = ForcePowers.costMultiplier(control, ForceBarrier.ID);
		List<BlasterBolt> bolts = new ArrayList<>();

		guarded(cleanup, () -> {
			barrier.onInput(light, AbilityAction.START);
			barrier.onInput(heavy, AbilityAction.START);
			barrier.onInput(control, AbilityAction.START);
			bolts.add(cleanup.entity(boltAt(level, null, light, false, 0.5, 14)));
			bolts.add(cleanup.entity(boltAt(level, null, heavy, true, 0.5, 14)));
		});
		last(helper, cleanup, 8, () -> {
			helper.assertTrue(bolts.get(0).isRemoved() && bolts.get(1).isRemoved(), "both bolts stop at the barriers");
			helper.assertTrue(near(energy(control) - energy(heavy), b.heavyBoltCost * mult),
					"a stopped heavy bolt costs 10 FE, paid " + (energy(control) - energy(heavy)));
			helper.assertTrue(energy(heavy) < energy(light), "the heavy bolt cost more than the plain one");
			helper.assertTrue(ForceBarrier.isChannelling(heavy), "a heavy bolt under the break threshold does not drop the barrier");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 80)
	public void barrierPulseAfter40Ticks(GameTestHelper helper) {
		BarrierBalance b = new BarrierBalance();
		helper.assertTrue(!ForceBarrier.pulses(b, 39) && ForceBarrier.pulses(b, 40), "the release pulse needs 40 ticks of holding");

		Cleanup cleanup = new Cleanup();
		ForceAbility barrier = power(helper, ForceBarrier.ID);
		FakePlayer held = caster(helper, cleanup, new BlockPos(3, 1, 3), ForceBarrier.ID);
		FakePlayer quick = caster(helper, cleanup, new BlockPos(20, 1, 3), ForceBarrier.ID);
		Mob heldFoe = poacher(helper, cleanup, new BlockPos(3, 1, 5));
		Mob quickFoe = poacher(helper, cleanup, new BlockPos(20, 1, 5));

		guarded(cleanup, () -> {
			barrier.onInput(held, AbilityAction.START);
			barrier.onInput(quick, AbilityAction.START);
			helper.assertTrue(ForceBarrier.isChannelling(held) && ForceBarrier.isChannelling(quick), "both barriers are up");
		});
		step(helper, cleanup, 20, () -> {
			barrier.onInput(quick, AbilityAction.RELEASE);
			helper.assertTrue(!ForceBarrier.isChannelling(quick), "a release ends the barrier");
			helper.assertTrue(!quickFoe.hasEffect(MobEffects.SLOWNESS), "a release after 1 s sends no pulse");
			helper.assertTrue(cooldown(quick, ForceBarrier.ID) == ForcePowers.cooldown(quick, ForceBarrier.ID, b.cooldownTicks), "the cooldown starts on release");
		});
		last(helper, cleanup, 45, () -> {
			helper.assertTrue(ForceBarrier.isChannelling(held), "the barrier is still held at 45 ticks");
			barrier.onInput(held, AbilityAction.RELEASE);
			helper.assertTrue(!ForceBarrier.isChannelling(held), "the release ends it");
			helper.assertTrue(heldFoe.hasEffect(MobEffects.SLOWNESS) && heldFoe.getDeltaMovement().z > 0.1,
					"a release after 2 s pushes the enemy within 3 blocks away and staggers it, motion " + heldFoe.getDeltaMovement());
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void barrierShelterProtectsAllies(GameTestHelper helper) {
		BarrierBalance b = new BarrierBalance();
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ForceAbility barrier = power(helper, ForceBarrier.ID);
		FakePlayer sheltering = caster(helper, cleanup, new BlockPos(3, 1, 3), ForceBarrier.ID, ForceBarrier.SHELTER);
		FakePlayer plain = caster(helper, cleanup, new BlockPos(20, 1, 3), ForceBarrier.ID);
		Mob inside = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 1, 5)));
		Mob outside = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 1, 10)));
		Mob nearPlain = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(20, 1, 5)));
		Mob foeInside = poacher(helper, cleanup, new BlockPos(1, 1, 4));
		Mob attacker = poacher(helper, cleanup, new BlockPos(12, 1, 20));

		try {
			DamageSource bite = level.damageSources().mobAttack(attacker);
			DamageSource blast = level.damageSources().explosion(attacker, attacker);
			DamageSource flame = level.damageSources().source(HunterDamage.WRIST_FLAMER);
			Vec3 center = ForcePowers.chest(sheltering);
			helper.assertTrue(near(ForceBarrier.damageMultiplier(b, blast, center, b.radius), b.blastMult), "a blast from outside is cut to 30%");
			helper.assertTrue(near(ForceBarrier.damageMultiplier(b, flame, center, b.radius), 0.5), "the wrist flamer is halved");
			helper.assertTrue(near(ForceBarrier.damageMultiplier(b, bite, center, b.radius), 0.5), "melee is halved");
			helper.assertTrue(near(ForceBarrier.damageMultiplier(b, level.damageSources().magic(), center, b.radius), 1.0), "magic is not stopped");

			barrier.onInput(sheltering, AbilityAction.START);
			barrier.onInput(plain, AbilityAction.START);
			helper.assertTrue(near(ForceBarrier.modifyDamage(inside, bite, 4f), 2), "Shelter halves melee on a harmless creature inside it");
			helper.assertTrue(near(ForceBarrier.modifyDamage(outside, bite, 4f), 4), "a creature outside the 3.5-block shelter is not guarded");
			helper.assertTrue(near(ForceBarrier.modifyDamage(foeInside, bite, 4f), 4), "an enemy inside the shelter is not guarded");
			helper.assertTrue(near(ForceBarrier.modifyDamage(sheltering, bite, 4f), 2), "the caster is guarded");
			helper.assertTrue(near(ForceBarrier.modifyDamage(plain, bite, 4f), 2), "a plain barrier guards its caster");
			helper.assertTrue(near(ForceBarrier.modifyDamage(nearPlain, bite, 4f), 4), "a plain barrier guards nobody else");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	@GameTest(structure = ARENA, maxTicks = 60)
	public void barrierReturnSendsBoltsBack(GameTestHelper helper) {
		BarrierBalance b = new BarrierBalance();
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ForceAbility barrier = power(helper, ForceBarrier.ID);
		FakePlayer returner = caster(helper, cleanup, new BlockPos(3, 1, 3), ForceBarrier.ID, ForceBarrier.RETURN);
		FakePlayer heavyReturner = caster(helper, cleanup, new BlockPos(20, 1, 3), ForceBarrier.ID, ForceBarrier.RETURN);
		Mob shooter = poacher(helper, cleanup, new BlockPos(3, 1, 9));
		Mob heavyShooter = poacher(helper, cleanup, new BlockPos(20, 1, 9));
		List<BlasterBolt> bolts = new ArrayList<>();
		List<String> seen = new ArrayList<>();

		guarded(cleanup, () -> {
			barrier.onInput(returner, AbilityAction.START);
			barrier.onInput(heavyReturner, AbilityAction.START);
			bolts.add(cleanup.entity(boltAt(level, shooter, returner, false, 0.5, 30)));
			bolts.add(cleanup.entity(boltAt(level, heavyShooter, heavyReturner, true, 0.5, 30)));
		});
		helper.onEachTick(() -> {
			BlasterBolt bolt = bolts.isEmpty() ? null : bolts.get(0);

			if (seen.isEmpty() && bolt != null && bolt.isDeflected() && !bolt.isRemoved()) {
				Vec3 toShooter = ForcePowers.chest(shooter).subtract(bolt.position()).normalize();
				double aim = bolt.getDeltaMovement().normalize().dot(toShooter);
				seen.add((bolt.getOwner() == returner) + ":" + (aim > 0.95) + ":" + near(bolt.damage(), 4f * b.returnDamageMult));
			}
		});
		last(helper, cleanup, 12, () -> {
			helper.assertTrue(!seen.isEmpty(), "Return sends the bolt back");
			helper.assertTrue(seen.get(0).equals("true:true:true"),
					"the returned bolt is the caster's, flies at its shooter and deals 60% (owner:aim:damage = " + seen.get(0) + ")");
			BlasterBolt heavyBolt = bolts.get(1);
			helper.assertTrue(heavyBolt.isRemoved() && !heavyBolt.isDeflected(), "a heavy bolt is stopped, never returned");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void barrierBreaksOnBigHit(GameTestHelper helper) {
		BarrierBalance b = new BarrierBalance();
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ForceAbility barrier = power(helper, ForceBarrier.ID);
		FakePlayer caster = caster(helper, cleanup, new BlockPos(3, 1, 3), ForceBarrier.ID);

		try {
			// The two hooks a hit runs through (the damage modifier, then Fabric's AFTER_DAMAGE), called in order.
			barrier.onInput(caster, AbilityAction.START);
			DamageSource magic = level.damageSources().magic();
			helper.assertTrue(near(ForceBarrier.modifyDamage(caster, magic, 4f), 4), "magic passes the barrier untouched");
			ForceBarrier.onDamaged(caster);
			helper.assertTrue(ForceBarrier.isChannelling(caster) && !caster.hasEffect(MobEffects.SLOWNESS), "a 4-point hit does not break the barrier");
			ForceBarrier.modifyDamage(caster, magic, b.breakDamage + 2f);
			ForceBarrier.onDamaged(caster);
			helper.assertTrue(!ForceBarrier.isChannelling(caster), "a hit of 10 or more breaks the barrier");
			helper.assertTrue(caster.hasEffect(MobEffects.SLOWNESS), "the broken barrier staggers its caster");
			helper.assertTrue(cooldown(caster, ForceBarrier.ID) > 0, "a broken barrier cools down");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	// ---------------------------------------------------------------- Force Choke (9.3)

	@GameTest(structure = ARENA, maxTicks = 20)
	public void chokeTargetsBreathingOnly(GameTestHelper helper) {
		ChokeBalance b = new ChokeBalance();
		helper.assertTrue(near(b.startCost, 25) && near(b.channelCost, 6) && b.maxTicks == 60 && near(b.range, 12) && near(b.damage, 2) && b.damageInterval == 10,
				"Choke: 25 FE, 6 FE/s, up to 3 s, range 12, 2 damage every 10 ticks");

		Cleanup cleanup = new Cleanup();
		ForceAbility choke = power(helper, ForceChoke.ID);
		Mob husk = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.HUSK, new BlockPos(3, 1, 8)));
		Mob target = poacher(helper, cleanup, new BlockPos(20, 1, 8));
		Mob villager = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(8, 1, 20)));
		Mob pig = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(10, 1, 20)));
		LivingEntity stand = cleanup.entity(helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(14, 1, 20)));
		Mob droid = cleanup.entity(helper.spawnWithNoFreeWill(DroidEntities.SECURITY_DROID, new BlockPos(17, 1, 20)));
		FakePlayer onHusk = caster(helper, cleanup, new BlockPos(3, 1, 2), ForceChoke.ID);
		FakePlayer onPoacher = caster(helper, cleanup, new BlockPos(20, 1, 2), ForceChoke.ID);

		try {
			helper.assertTrue(ForceChoke.breathes(target) && ForceChoke.breathes(villager) && ForceChoke.breathes(pig), "poachers, villagers and pigs breathe");
			helper.assertTrue(!ForceChoke.breathes(husk) && !ForceChoke.breathes(stand) && !ForceChoke.breathes(droid),
					"the undead, armour stands and droids do not");

			aim(onHusk, ForcePowers.chest(husk));
			float before = energy(onHusk);
			choke.onInput(onHusk, AbilityAction.START);
			helper.assertTrue(!ForceChoke.isChoking(onHusk) && near(energy(onHusk), before) && cooldown(onHusk, ForceChoke.ID) == 0,
					"Choke on a husk is refused: no hold, no energy, no cooldown");

			aim(onPoacher, ForcePowers.chest(target));
			float full = energy(onPoacher);
			choke.onInput(onPoacher, AbilityAction.START);
			helper.assertTrue(ForceChoke.isChoking(onPoacher) && ChokedTargets.isChoked(target) && ForceChoke.chokerOf(target) == onPoacher,
					"Choke holds a breathing enemy");
			helper.assertTrue(near(full - energy(onPoacher), b.startCost * ForcePowers.costMultiplier(onPoacher, ForceChoke.ID)), "the hold costs 25 FE to start");
			choke.onInput(onPoacher, AbilityAction.RELEASE);
			helper.assertTrue(!ForceChoke.isChoking(onPoacher) && !ChokedTargets.isChoked(target) && target.hasEffect(MobEffects.SLOWNESS),
					"a release lets go and staggers");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void chokeHoldUsesDiminishing(GameTestHelper helper) {
		Cleanup cleanup = new Cleanup();
		ForceAbility choke = power(helper, ForceChoke.ID);
		Mob acolyte = cleanup.entity(helper.spawnWithNoFreeWill(EnemyEntities.SITH_ACOLYTE, new BlockPos(3, 1, 8)));
		Mob fresh = cleanup.entity(helper.spawnWithNoFreeWill(EnemyEntities.SITH_ACOLYTE, new BlockPos(12, 1, 20)));
		Mob target = poacher(helper, cleanup, new BlockPos(20, 1, 8));
		FakePlayer onAcolyte = caster(helper, cleanup, new BlockPos(3, 1, 2), ForceChoke.ID);
		FakePlayer onPoacher = caster(helper, cleanup, new BlockPos(20, 1, 2), ForceChoke.ID);

		try {
			helper.assertTrue(ForcePowers.isResistant(acolyte) && !ForcePowers.isResistant(target), "the acolyte is #push_resistant, the poacher is not");
			int first = ControlEffects.diminished(fresh, 60, ControlEffects.HOLD);
			int second = ControlEffects.diminished(fresh, 60, ControlEffects.HOLD);
			int third = ControlEffects.diminished(fresh, 60, ControlEffects.HOLD);
			helper.assertTrue(first == ControlEffects.duration(fresh, 60, ControlEffects.HOLD) && second == ControlEffects.duration(fresh, 30, ControlEffects.HOLD)
					&& third == 0, "holds on a resistant target diminish: full, half, none (" + first + ", " + second + ", " + third + ")");

			aim(onAcolyte, ForcePowers.chest(acolyte));
			choke.onInput(onAcolyte, AbilityAction.START);
			helper.assertTrue(ForceChoke.isChoking(onAcolyte), "the first choke holds the acolyte");
			choke.onInput(onAcolyte, AbilityAction.RELEASE);
			Progression.setCooldown(onAcolyte, ForceChoke.ID, 0);
			refill(onAcolyte);
			float before = energy(onAcolyte);
			choke.onInput(onAcolyte, AbilityAction.START);
			helper.assertTrue(!ForceChoke.isChoking(onAcolyte) && near(energy(onAcolyte), before),
					"after a hold and its release stagger the acolyte's window is spent: the next choke is refused for free");

			aim(onPoacher, ForcePowers.chest(target));
			choke.onInput(onPoacher, AbilityAction.START);
			choke.onInput(onPoacher, AbilityAction.RELEASE);
			Progression.setCooldown(onPoacher, ForceChoke.ID, 0);
			refill(onPoacher);
			choke.onInput(onPoacher, AbilityAction.START);
			helper.assertTrue(ForceChoke.isChoking(onPoacher), "a target that is not resistant can be choked again");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void chokeOnPlayersOnlyWithPvp(GameTestHelper helper) {
		helper.assertTrue(!new ChokeBalance().pvpEffects, "Choke on players is off by default");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ForceAbility choke = power(helper, ForceChoke.ID);
		ServerPlayer victim = cleanup.player(M3TestPlayers.listed(helper, new BlockPos(12, 1, 8)));
		victim.setGameMode(GameType.CREATIVE);
		FakePlayer caster = caster(helper, cleanup, new BlockPos(12, 1, 2), ForceChoke.ID);
		ChokeBalance live = ForceChoke.balance();
		boolean saved = live.pvpEffects;

		try {
			aim(caster, ForcePowers.chest(victim));
			live.pvpEffects = false;

			try {
				choke.onInput(caster, AbilityAction.START);
			} finally {
				live.pvpEffects = saved;
			}

			helper.assertTrue(!ForceChoke.isChoking(caster) && !Holds.isHeld(victim), "without pvpEffects no player is choked");

			if (level.isPvpAllowed()) {
				// A listed mock player's game mode is always creative, so it can never be choked; the PvP rule is checked on a
				// survival fake player, which is not in the level for the aim to find.
				FakePlayer survivor = caster(helper, cleanup, new BlockPos(12, 1, 8));
				helper.assertTrue(!ForcePowers.canAffect(caster, survivor, false) && ForcePowers.canAffect(caster, survivor, true),
						"with pvpEffects on a PvP server a survival player can be choked");
				helper.assertTrue(!ForcePowers.canAffect(caster, victim, true), "never a creative player");
			}

			helper.succeed();
		} finally {
			live.pvpEffects = saved;
			cleanup.run();
		}
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void chokeShiftOnNonHostileOncePerDay(GameTestHelper helper) {
		ChokeBalance b = new ChokeBalance();
		helper.assertTrue(ForceChoke.shifts(false, false, false) && !ForceChoke.shifts(true, false, false) && ForceChoke.shifts(true, true, false)
				&& !ForceChoke.shifts(false, false, true), "a choke on the non-hostile or the yielded shifts alignment; on enemies and animals it does not");

		Cleanup cleanup = new Cleanup();
		ForceAbility choke = power(helper, ForceChoke.ID);
		Mob villager = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(12, 1, 8)));
		FakePlayer caster = caster(helper, cleanup, new BlockPos(12, 1, 2), ForceChoke.ID);

		try {
			aim(caster, ForcePowers.chest(villager));
			int before = Identity.get(caster).alignment;
			choke.onInput(caster, AbilityAction.START);
			int after = Identity.get(caster).alignment;
			helper.assertTrue(ForceChoke.isChoking(caster), "the villager is held");
			helper.assertTrue(after < before && after >= before + b.nonHostileShift, "choking a villager is a minor dark shift: " + before + " to " + after);

			choke.onInput(caster, AbilityAction.RELEASE);
			Progression.setCooldown(caster, ForceChoke.ID, 0);
			refill(caster);
			choke.onInput(caster, AbilityAction.START);
			helper.assertTrue(ForceChoke.isChoking(caster) && Identity.get(caster).alignment == after, "the same villager the same day shifts nothing more");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	@GameTest(structure = ARENA, maxTicks = 100)
	public void crushAndDreadBranches(GameTestHelper helper) {
		ChokeBalance b = new ChokeBalance();
		helper.assertTrue(near(ForceChoke.releaseDamage(b, true, 40), 6) && near(ForceChoke.releaseDamage(b, true, 39), 0)
				&& near(ForceChoke.releaseDamage(b, false, 60), 0), "Crush adds 6 damage when a hold of 2 s or more ends");

		Cleanup cleanup = new Cleanup();
		ForceAbility choke = power(helper, ForceChoke.ID);
		FakePlayer crusher = caster(helper, cleanup, new BlockPos(2, 1, 2), ForceChoke.ID, ForceChoke.CRUSH);
		FakePlayer plain = caster(helper, cleanup, new BlockPos(7, 1, 2), ForceChoke.ID);
		FakePlayer dreader = caster(helper, cleanup, new BlockPos(20, 1, 2), ForceChoke.ID, ForceChoke.DREAD);
		Mob crushed = poacher(helper, cleanup, new BlockPos(2, 1, 7));
		Mob held = poacher(helper, cleanup, new BlockPos(7, 1, 7));
		Mob dreaded = poacher(helper, cleanup, new BlockPos(20, 1, 8));
		Mob nearby = poacher(helper, cleanup, new BlockPos(22, 1, 8));
		Mob distant = poacher(helper, cleanup, new BlockPos(12, 1, 8));
		Mob bait = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(16, 1, 20)));

		guarded(cleanup, () -> {
			nearby.setTarget(bait);
			distant.setTarget(bait);
			aim(crusher, ForcePowers.chest(crushed));
			aim(plain, ForcePowers.chest(held));
			aim(dreader, ForcePowers.chest(dreaded));
			choke.onInput(crusher, AbilityAction.START);
			choke.onInput(plain, AbilityAction.START);
			choke.onInput(dreader, AbilityAction.START);
			helper.assertTrue(ForceChoke.chokerOf(crushed) == crusher && ForceChoke.chokerOf(held) == plain && ForceChoke.chokerOf(dreaded) == dreader,
					"each caster holds their own poacher");
		});
		step(helper, cleanup, 5, () -> {
			helper.assertTrue(nearby.getTarget() == null, "Dread: an enemy within 6 blocks of the choked one drops its target");
			helper.assertTrue(distant.getTarget() == bait, "an enemy 8 blocks away keeps its target");
			choke.onInput(dreader, AbilityAction.RELEASE);
		});
		last(helper, cleanup, b.maxTicks + 6, () -> {
			helper.assertTrue(!ForceChoke.isChoking(crusher) && !ForceChoke.isChoking(plain), "both holds end on their own after 3 s");
			float extra = held.getHealth() - crushed.getHealth();
			helper.assertTrue(Math.abs(extra - b.crushDamage) < 0.3f, "Crush deals 6 more when the full hold ends (less armour), dealt " + extra);
		});
	}

	@GameTest(structure = ARENA, maxTicks = 80)
	public void cruelFocusWithin40TicksOfRelease(GameTestHelper helper) {
		ChokeBalance b = new ChokeBalance();
		helper.assertTrue(ForceChoke.cruelFocusApplies(b, 0) && ForceChoke.cruelFocusApplies(b, 40) && !ForceChoke.cruelFocusApplies(b, 41)
				&& !ForceChoke.cruelFocusApplies(b, -1), "Cruel Focus lasts 40 ticks after the release");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ForceAbility choke = power(helper, ForceChoke.ID);
		FakePlayer cruel = caster(helper, cleanup, new BlockPos(3, 1, 2), ForceChoke.ID, ForceChoke.CRUEL_FOCUS);
		FakePlayer bystander = caster(helper, cleanup, new BlockPos(12, 1, 2), ForceChoke.ID, ForceChoke.CRUEL_FOCUS);
		FakePlayer plain = caster(helper, cleanup, new BlockPos(20, 1, 2), ForceChoke.ID);
		Mob victim = poacher(helper, cleanup, new BlockPos(3, 1, 8));
		Mob other = poacher(helper, cleanup, new BlockPos(20, 1, 8));

		guarded(cleanup, () -> {
			aim(cruel, ForcePowers.chest(victim));
			aim(plain, ForcePowers.chest(other));
			choke.onInput(cruel, AbilityAction.START);
			choke.onInput(plain, AbilityAction.START);
			helper.assertTrue(ForceChoke.isChoking(cruel) && ForceChoke.isChoking(plain), "both chokes hold");
			choke.onInput(cruel, AbilityAction.RELEASE);
			choke.onInput(plain, AbilityAction.RELEASE);

			float sharp = 10f * (1f + b.cruelFocusPct / 100f);
			helper.assertTrue(near(ForceChoke.modifyDamage(victim, ForceDamage.source(level, ForceDamage.LIGHTNING, cruel), 10f), sharp),
					"the caster's Lightning on the target they just released deals +20%");
			helper.assertTrue(near(ForceChoke.modifyDamage(victim, ForceDamage.source(level, ForceDamage.LIGHTNING, bystander), 10f), 10),
					"another caster's Lightning is not sharpened");
			helper.assertTrue(near(ForceChoke.modifyDamage(other, ForceDamage.source(level, ForceDamage.LIGHTNING, plain), 10f), 10),
					"without the node nothing is sharpened");
			helper.assertTrue(near(ForceChoke.modifyDamage(victim, level.damageSources().playerAttack(cruel), 10f), 10), "only Lightning is sharpened");
		});
		last(helper, cleanup, b.cruelFocusWindow + 5, () -> helper.assertTrue(
				near(ForceChoke.modifyDamage(victim, ForceDamage.source(level, ForceDamage.LIGHTNING, cruel), 10f), 10),
				"after 40 ticks the window has shut"));
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void chokeTicksHookRespected(GameTestHelper helper) {
		registerHooks();
		Cleanup cleanup = new Cleanup();
		ForceAbility choke = power(helper, ForceChoke.ID);
		Mob marked = poacher(helper, cleanup, new BlockPos(3, 1, 8));
		Mob unmarked = poacher(helper, cleanup, new BlockPos(20, 1, 8));
		FakePlayer refused = caster(helper, cleanup, new BlockPos(3, 1, 2), ForceChoke.ID);
		FakePlayer allowed = caster(helper, cleanup, new BlockPos(20, 1, 2), ForceChoke.ID);
		NO_CHOKE.add(marked.getUUID());
		cleanup.then(() -> NO_CHOKE.remove(marked.getUUID()));

		try {
			helper.assertTrue(ForceReactions.chokeTicks(marked, 60) == 0 && ForceReactions.chokeTicks(unmarked, 60) == 60,
					"the hook refuses the marked poacher only");
			aim(refused, ForcePowers.chest(marked));
			float before = energy(refused);
			choke.onInput(refused, AbilityAction.START);
			helper.assertTrue(!ForceChoke.isChoking(refused) && near(energy(refused), before) && cooldown(refused, ForceChoke.ID) == 0,
					"a hold a handler refuses (0 ticks) costs nothing and starts no cooldown");
			aim(allowed, ForcePowers.chest(unmarked));
			choke.onInput(allowed, AbilityAction.START);
			helper.assertTrue(ForceChoke.isChoking(allowed), "the unmarked poacher is held");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	// ---------------------------------------------------------------- Lift for Jedi and Sith (9.4), tree additions (9.5)

	@GameTest(structure = ARENA, maxTicks = 20)
	public void liftNodesInJediAndSithTreesOnly(GameTestHelper helper) {
		SkillTreeSet trees = SkillTrees.server();
		SkillNode jedi = trees.node(ForceM4Feature.LIFT_JEDI);
		SkillNode sith = trees.node(ForceM4Feature.LIFT_SITH);
		helper.assertTrue(jedi != null && SkillIds.TREE_JEDI.equals(jedi.tree()) && jedi.path() == Path.JEDI && ForceLift.ID.equals(jedi.ability())
				&& jedi.requires().stream().anyMatch(r -> SkillIds.FORCE_PUSH.equals(r.node())), "lift_learned_jedi: Jedi tree, Jedi path, teaches Lift, needs Push");
		helper.assertTrue(sith != null && SkillIds.TREE_SITH.equals(sith.tree()) && sith.path() == Path.SITH && ForceLift.ID.equals(sith.ability())
				&& sith.requires().stream().anyMatch(r -> SkillIds.FORCE_PUSH.equals(r.node())), "lift_learned_sith: Sith tree, Sith path, teaches Lift, needs Push");
		SkillNode granted = trees.node(ForceLift.ID);
		helper.assertTrue(granted != null && granted.grantedOnly() && SkillIds.TREE_EXPLORATION.equals(granted.tree()), "Lift itself stays granted in Exploration");

		Set<String> liftTrees = new HashSet<>();

		for (SkillNode node : trees.nodes()) {
			if (ForceLift.ID.equals(node.id()) || ForceLift.ID.equals(node.ability())) {
				liftTrees.add(node.tree());
			}
		}

		helper.assertTrue(liftTrees.equals(Set.of(SkillIds.TREE_EXPLORATION, SkillIds.TREE_JEDI, SkillIds.TREE_SITH)),
				"Lift is taught in the Exploration, Jedi and Sith trees only, found " + liftTrees);

		Cleanup cleanup = new Cleanup();
		FakePlayer learner = caster(helper, cleanup, new BlockPos(3, 1, 3));
		FakePlayer explorer = caster(helper, cleanup, new BlockPos(20, 1, 3));

		try {
			SkillData learned = SkillService.data(learner);
			learned.nodes.put(ForceM4Feature.LIFT_JEDI, 1);
			learned.nodes.put(ForceLift.ID, 1);
			learned.granted.add(ForceLift.ID);
			SkillService.commit(learner, learned);
			helper.assertTrue(ForceM4Feature.tickLiftNodes(learner), "with the path node owned, the taught Lift turns into a bought node");
			helper.assertTrue(!ForceM4Feature.tickLiftNodes(learner), "only once");
			helper.assertTrue(SkillService.data(learner).owns(ForceLift.ID) && !SkillService.data(learner).isGranted(ForceLift.ID),
					"Lift is still owned, no longer granted, so a respec takes it with the path node");

			SkillData found = SkillService.data(explorer);
			found.nodes.put(ForceLift.ID, 1);
			found.granted.add(ForceLift.ID);
			SkillService.commit(explorer, found);
			helper.assertTrue(!ForceM4Feature.tickLiftNodes(explorer) && SkillService.data(explorer).isGranted(ForceLift.ID),
					"Lift found in the world stays granted");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	@GameTest(maxTicks = 20)
	public void newNodesVisibleAndPurchasable(GameTestHelper helper) {
		SkillTreeSet trees = SkillTrees.server();
		int penalty = new SkillBalance().switchPenalty;
		SkillData none = new SkillData();
		SkillData speed = owning(ForceSpeed.ID);
		SkillData barrier = owning(ForceBarrier.ID);
		SkillData choke = owning(ForceChoke.ID);
		SkillData push = owning(SkillIds.FORCE_PUSH);

		expect(helper, trees, none, 12, Path.NONE, ForceSpeed.ID, penalty, LearnResult.OK);
		expect(helper, trees, none, 11, Path.NONE, ForceSpeed.ID, penalty, LearnResult.LEVEL_TOO_LOW);
		expect(helper, trees, speed, 13, Path.NONE, ForceSpeed.BURST, penalty, LearnResult.OK);
		expect(helper, trees, speed, 13, Path.NONE, ForceSpeed.LONG_ROAD, penalty, LearnResult.OK);
		expect(helper, trees, speed, 12, Path.NONE, ForceSpeed.BURST, penalty, LearnResult.LEVEL_TOO_LOW);
		expect(helper, trees, none, 13, Path.NONE, ForceSpeed.LONG_ROAD, penalty, LearnResult.MISSING_REQUIREMENT);
		expect(helper, trees, speed, 20, Path.INDEPENDENT, ForceSpeed.WAYFARER, penalty, LearnResult.GRANTED_ONLY);

		expect(helper, trees, none, 20, Path.JEDI, ForceBarrier.ID, penalty, LearnResult.GRANTED_ONLY);
		expect(helper, trees, barrier, 13, Path.JEDI, ForceBarrier.SHELTER, penalty, LearnResult.OK);
		expect(helper, trees, barrier, 13, Path.JEDI, ForceBarrier.RETURN, penalty, LearnResult.OK);
		expect(helper, trees, barrier, 14, Path.JEDI, ForceBarrier.GUARDIANS_PATIENCE, penalty, LearnResult.OK);
		expect(helper, trees, barrier, 13, Path.JEDI, ForceBarrier.GUARDIANS_PATIENCE, penalty, LearnResult.LEVEL_TOO_LOW);

		expect(helper, trees, none, 20, Path.SITH, ForceChoke.ID, penalty, LearnResult.GRANTED_ONLY);
		expect(helper, trees, choke, 13, Path.SITH, ForceChoke.CRUSH, penalty, LearnResult.OK);
		expect(helper, trees, choke, 13, Path.SITH, ForceChoke.DREAD, penalty, LearnResult.OK);
		expect(helper, trees, choke, 14, Path.SITH, ForceChoke.CRUEL_FOCUS, penalty, LearnResult.OK);

		expect(helper, trees, push, 13, Path.JEDI, ForceM4Feature.LIFT_JEDI, penalty, LearnResult.OK);
		expect(helper, trees, push, 13, Path.SITH, ForceM4Feature.LIFT_JEDI, penalty, LearnResult.WRONG_PATH);
		expect(helper, trees, push, 12, Path.JEDI, ForceM4Feature.LIFT_JEDI, penalty, LearnResult.LEVEL_TOO_LOW);
		expect(helper, trees, push, 13, Path.SITH, ForceM4Feature.LIFT_SITH, penalty, LearnResult.OK);
		expect(helper, trees, none, 20, Path.INDEPENDENT, ForceLift.ID, penalty, LearnResult.GRANTED_ONLY);

		expect(helper, trees, none, 12, Path.INDEPENDENT, "staff_whirl", penalty, LearnResult.OK);
		expect(helper, trees, none, 13, Path.INDEPENDENT, "lens_mastery", penalty, LearnResult.OK);
		expect(helper, trees, none, 12, Path.INDEPENDENT, "lens_mastery", penalty, LearnResult.LEVEL_TOO_LOW);

		SkillData dread = owning(ForceChoke.ID);
		dread.nodes.put(ForceChoke.DREAD, 1);
		SkillRules.LearnCheck swap = SkillRules.check(trees, dread, context(20, Path.SITH), ForceChoke.CRUSH, penalty);
		helper.assertTrue(swap.ok() && swap.isSwitch() && swap.cost() == 1 + penalty, "Crush and Dread are one choice: owning Dread, Crush is a switch");
		helper.succeed();
	}

	private static SkillData owning(String nodeId) {
		SkillData data = new SkillData();
		data.nodes.put(nodeId, 1);
		return data;
	}

	private static SkillContext context(int level, Path path) {
		Set<String> all = Set.of(SkillIds.TREE_UNIVERSAL, SkillIds.TREE_SABER, SkillIds.TREE_JEDI, SkillIds.TREE_SITH, SkillIds.TREE_EXPLORATION);
		return new SkillContext(true, level, 10, 0, path, all, false, false);
	}

	private static void expect(GameTestHelper helper, SkillTreeSet trees, SkillData data, int level, Path path, String nodeId, int penalty, LearnResult expected) {
		LearnResult got = SkillRules.check(trees, data, context(level, path), nodeId, penalty).result();
		helper.assertTrue(got == expected, nodeId + " at level " + level + " (" + path + "): expected " + expected + ", got " + got);
	}

	// ---------------------------------------------------------------- mastery (9.1 to 9.3)

	@GameTest(structure = ARENA, maxTicks = 20)
	public void masteryThresholds(GameTestHelper helper) {
		helper.assertTrue(Progression.masteryRank(49) == 0 && Progression.masteryRank(50) == 1 && Progression.masteryRank(149) == 1
				&& Progression.masteryRank(150) == 2 && Progression.masteryRank(399) == 2 && Progression.masteryRank(400) == 3,
				"mastery ranks at 50, 150 and 400 points");
		SpeedBalance speedB = new SpeedBalance();
		BarrierBalance barrierB = new BarrierBalance();
		ChokeBalance chokeB = new ChokeBalance();
		helper.assertTrue(near(ForceSpeed.cost(speedB, 1, false), 18) && ForceSpeed.duration(speedB, 2, false) == 200,
				"Speed: -2 FE and +1 s per rank");
		helper.assertTrue(ForceSpeed.masteryFor(199, speedB) == 0 && ForceSpeed.masteryFor(399, speedB) == 1 && ForceSpeed.masteryFor(400, speedB) == 2,
				"Speed earns a mastery point every 200 blocks run");
		helper.assertTrue(near(ForceBarrier.costPerSecond(barrierB, 0), 6) && near(ForceBarrier.costPerSecond(barrierB, 3), 3)
				&& ForceBarrier.maxTicks(barrierB, 0) == 120 && ForceBarrier.maxTicks(barrierB, 3) == 150, "Barrier: -1 FE/s and +0.5 s per rank");
		helper.assertTrue(near(ForceChoke.startCost(chokeB, 0), 25) && near(ForceChoke.startCost(chokeB, 3), 19)
				&& near(ForceChoke.range(chokeB, 0), 12) && near(ForceChoke.range(chokeB, 3), 15), "Choke: -2 FE and +1 block per rank");

		Cleanup cleanup = new Cleanup();
		ForceAbility speed = power(helper, ForceSpeed.ID);
		FakePlayer master = caster(helper, cleanup, new BlockPos(3, 1, 3), ForceSpeed.ID);

		try {
			Progression.addMastery(master, ForceSpeed.ID, 400);
			helper.assertTrue(ForcePowers.masteryRank(master, ForceSpeed.ID) == 3, "400 points is rank 3");
			float before = energy(master);
			speed.onInput(master, AbilityAction.START);
			helper.assertTrue(ForceSpeed.isRunning(master) && near(before - energy(master), 14f * ForcePowers.costMultiplier(master, ForceSpeed.ID)),
					"a rank 3 run costs 14 FE, paid " + (before - energy(master)));
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}
}
