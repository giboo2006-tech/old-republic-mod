package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.AbilityCosts;
import com.oldrepublic.ability.AbilityRuntime;
import com.oldrepublic.ability.EnergyRefunds;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.ability.network.AbilityInputPayload;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceAct;
import com.oldrepublic.force.ForceBlockInteractions;
import com.oldrepublic.force.ForceDispatch;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.ForceReactiveEntity;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.force.PushBalance;
import com.oldrepublic.force.PushBranch;
import com.oldrepublic.force.PushShape;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.force.heal.ForceHeal;
import com.oldrepublic.force.heal.HealBalance;
import com.oldrepublic.force.heal.HealRevive;
import com.oldrepublic.force.jump.ForceJump;
import com.oldrepublic.force.jump.JumpBalance;
import com.oldrepublic.force.jump.JumpShape;
import com.oldrepublic.force.lift.ForceLift;
import com.oldrepublic.force.lift.LiftBalance;
import com.oldrepublic.force.lift.LiftRules;
import com.oldrepublic.force.lift.LiftedTargets;
import com.oldrepublic.force.lightning.ForceLightning;
import com.oldrepublic.force.lightning.LightningBalance;
import com.oldrepublic.force.lightning.LightningShape;
import com.oldrepublic.force.network.SenseRevealPayload;
import com.oldrepublic.force.pull.ForcePull;
import com.oldrepublic.force.pull.PullBalance;
import com.oldrepublic.force.pull.PullShape;
import com.oldrepublic.force.pull.PullTargeting;
import com.oldrepublic.force.sense.ForceSense;
import com.oldrepublic.force.sense.SenseBalance;
import com.oldrepublic.force.sense.SenseLocator;
import com.oldrepublic.force.sense.SenseRules;
import com.oldrepublic.force.sense.SenseSink;
import com.oldrepublic.force.sense.SenseTargets;
import com.oldrepublic.holocron.HolocronBalance;
import com.oldrepublic.holocron.HolocronCatalog;
import com.oldrepublic.holocron.HolocronData;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.throwing.SaberThrow;
import com.oldrepublic.skill.SkillAttributes;
import com.oldrepublic.skill.SkillBalance;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillIds;
import com.oldrepublic.skill.SkillNodeEffects;
import com.oldrepublic.skill.SkillPassives;
import com.oldrepublic.skill.SkillRules;
import com.oldrepublic.skill.SkillService;
import com.oldrepublic.skill.Skills;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Force power rules that can be checked without a live client: Push shapes, costs and cone
 * geometry (Milestone 1), and the Milestone 2 powers: Pull targeting, speed, brake and disarm; Jump
 * velocity, fall protection and Crushing Descent; Heal amounts, interruption and Shared Light
 * targets; Lightning damage, guard, chain targets, burn and cooldowns; alignment opposition; the
 * PvP rule. Casters are Fabric fake players (they never tick and are not in the player list).
 *
 * <p>Milestone 3 (spec 17.1): Force Sense (cost, cooldown, the 32-point cap, the slot-8 key, the
 * providers, the cold cast and the follow-up pulse), Telekinetic Lift (heights, refusals, the hold
 * kind), the 10.6 dispatch order of Push, Pull and Lift, Lightning and Saber Throw on a reactive
 * entity, Force Heal reviving downed troopers, and the granted node effects. These follow the
 * Milestone 3 test rules: the {@code oldrepublic_test:arena_24x7x24} fixture, listed mock players
 * removed at the end, and test-only hooks registered once ({@link #registerM3Hooks}) that answer
 * only for the creatures and casters a test marks in its UUID sets. A listed mock player counts as
 * creative (energy is never spent), so the checks that energy is or is not spent use a survival
 * fake player instead.
 */
public class ForceGameTests {
	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 1.0e-3f;
	}

	@GameTest
	public void baseShapeMatchesSpec(GameTestHelper helper) {
		PushBalance b = new PushBalance();
		PushShape weak = PushShape.of(b, PushBranch.NONE, 0, 0f);
		PushShape full = PushShape.of(b, PushBranch.NONE, 0, 1f);
		helper.assertTrue(near(weak.halfAngle() * 2, 70f) && near(weak.range(), 6f), Component.literal("base cone must be 70 degrees, 6 blocks"));
		helper.assertTrue(near(weak.knockback(), 1.1f) && near(full.knockback(), 1.8f), Component.literal("knockback must scale 1.1 to 1.8"));
		helper.assertTrue(near(weak.cost(), 18f) && near(full.cost(), 28f), Component.literal("cost must scale 18 to 28"));
		helper.assertTrue(weak.cooldownTicks() == 70, Component.literal("cooldown must be 3.5 s"));
		helper.succeed();
	}

	@GameTest
	public void branchShapesMatchSpec(GameTestHelper helper) {
		PushBalance b = new PushBalance();
		PushShape repulse = PushShape.of(b, PushBranch.REPULSE_WAVE, 0, 1f);
		PushShape focused = PushShape.of(b, PushBranch.FOCUSED_BLAST, 0, 1f);
		PushShape pulse = PushShape.of(b, PushBranch.DEFLECTING_PULSE, 0, 1f);
		helper.assertTrue(near(repulse.halfAngle() * 2, 130f) && near(repulse.range(), 7f) && near(repulse.knockback(), 1.8f * 0.8f),
				Component.literal("Repulse Wave: 130 degrees, range 7, knockback x0.8"));
		helper.assertTrue(near(focused.halfAngle() * 2, 25f) && near(focused.range(), 12f) && near(focused.knockback(), 1.8f * 1.8f),
				Component.literal("Focused Blast: 25 degrees, range 12, knockback x1.8"));
		helper.assertTrue(pulse.spherical() && near(pulse.range(), 4f) && near(pulse.knockback(), 1.8f * 0.6f) && pulse.cooldownTicks() == 42,
				Component.literal("Deflecting Pulse: 360 degrees, radius 4, knockback x0.6, cooldown x0.6"));
		helper.succeed();
	}

	@GameTest
	public void masteryScalesRangeAndCostOnly(GameTestHelper helper) {
		PushBalance b = new PushBalance();
		PushShape rank0 = PushShape.of(b, PushBranch.FOCUSED_BLAST, 0, 0.5f);
		PushShape rank3 = PushShape.of(b, PushBranch.FOCUSED_BLAST, 3, 0.5f);
		helper.assertTrue(rank3.range() > rank0.range() && rank3.cost() < rank0.cost(), Component.literal("mastery should raise range and lower cost"));
		helper.assertTrue(near(rank3.halfAngle(), rank0.halfAngle()) && near(rank3.knockback(), rank0.knockback()), Component.literal("mastery must not change the branch"));
		helper.succeed();
	}

	@GameTest
	public void chargeNeedsMinimumHold(GameTestHelper helper) {
		PushBalance b = new PushBalance();
		helper.assertTrue(PushShape.chargeFromHold(b, 4) < 0f, Component.literal("below 0.25 s must cancel"));
		helper.assertTrue(near(PushShape.chargeFromHold(b, 5), 0f), Component.literal("0.25 s is the weakest push"));
		helper.assertTrue(near(PushShape.chargeFromHold(b, 20), 1f) && near(PushShape.chargeFromHold(b, 80), 1f), Component.literal("1 s and longer is full"));
		helper.succeed();
	}

	@GameTest
	public void coneGeometry(GameTestHelper helper) {
		Vec3 eye = Vec3.ZERO;
		Vec3 look = new Vec3(0, 0, 1);
		helper.assertTrue(PushTargeting.inShape(eye, look, new Vec3(0, 0, 5), 6, 35, false), Component.literal("straight ahead is inside"));
		helper.assertTrue(PushTargeting.inShape(eye, look, new Vec3(2, 0, 4), 6, 35, false), Component.literal("27 degrees off is inside a 70 degree cone"));
		helper.assertTrue(!PushTargeting.inShape(eye, look, new Vec3(4, 0, 4), 6, 35, false), Component.literal("45 degrees off is outside"));
		helper.assertTrue(!PushTargeting.inShape(eye, look, new Vec3(0, 0, 7), 6, 35, false), Component.literal("beyond range is outside"));
		helper.assertTrue(PushTargeting.inShape(eye, look, new Vec3(0, 0, -3), 4, 180, true), Component.literal("the pulse reaches behind"));
		helper.succeed();
	}

	@GameTest
	public void releaseVariantRoundTrips(GameTestHelper helper) {
		PushShape shape = PushShape.of(new PushBalance(), PushBranch.FOCUSED_BLAST, 2, 1f);
		int variant = ForceEffects.packRelease(PushBranch.FOCUSED_BLAST, shape);
		helper.assertTrue(ForceEffects.releaseBranch(variant) == PushBranch.FOCUSED_BLAST, Component.literal("branch lost"));
		helper.assertTrue(Math.abs(ForceEffects.releaseRange(variant) - shape.range()) <= 0.125f, Component.literal("range lost"));
		helper.assertTrue(Math.abs(ForceEffects.releaseHalfAngle(variant) - shape.halfAngle()) <= 0.5f, Component.literal("angle lost"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- Milestone 2

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-3;
	}

	/** A fake player standing at {@code pos} (relative), looking along +z. */
	private static FakePlayer caster(GameTestHelper helper, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		FakePlayer player = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "force_test"));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(pos)));
		player.setYRot(0f);
		player.setXRot(0f);
		player.setYHeadRot(0f);
		return player;
	}

	private static LivingEntity zombie(GameTestHelper helper, BlockPos pos) {
		return helper.spawn(EntityTypes.ZOMBIE, pos);
	}

	@GameTest
	public void pullSpeedFormula(GameTestHelper helper) {
		PullBalance b = new PullBalance();
		Vec3 near = ForcePull.velocity(b, Vec3.ZERO, new Vec3(0, 0, 10), 1f);
		helper.assertTrue(near(near.z, -1.45) && near(near.x, 0) && near(near.y, 0.25),
				Component.literal("10 blocks away: 0.35 + 0.11 x 10 = 1.45 toward the caster, lift 0.25; got " + near));
		Vec3 far = ForcePull.velocity(b, Vec3.ZERO, new Vec3(20, 0, 0), 1f);
		helper.assertTrue(near(far.x, -1.6), Component.literal("speed is capped at 1.6, got " + far));
		Vec3 below = ForcePull.velocity(b, Vec3.ZERO, new Vec3(0, -4, 5), 1f);
		helper.assertTrue(near(below.y, 0.25 + 0.03 * 4), Component.literal("a target 4 blocks below gets 0.03 lift per block, got " + below.y));
		Vec3 disarm = ForcePull.velocity(b, Vec3.ZERO, new Vec3(0, 0, 10), PullShape.DISARM.speedMult(b));
		helper.assertTrue(near(disarm.z, -1.45 * 0.6), Component.literal("Disarming Pull is x0.6"));
		helper.assertTrue(near(PullShape.REEL.speedMult(b), 1.2f), Component.literal("Reeling Pull is x1.2"));

		LivingEntity target = zombie(helper, new BlockPos(2, 1, 2));
		float open = ForcePull.multiplier(b, target, PullShape.NONE, false);
		float guarded = ForcePull.multiplier(b, target, PullShape.NONE, true);
		helper.assertTrue(near(guarded, open * 0.5f), Component.literal("a guarding target facing the caster is pulled at x0.5"));
		helper.succeed();
	}

	@GameTest
	public void pullBrakesNearTheCaster(GameTestHelper helper) {
		Vec3 braked = ForcePull.braked(new PullBalance(), new Vec3(1, 0.2, -1));
		helper.assertTrue(near(braked.x, 0.3) && near(braked.z, -0.3) && near(braked.y, 0.2),
				Component.literal("within 2.5 blocks the horizontal speed is x0.3 (the fall is untouched), got " + braked));
		helper.succeed();
	}

	@GameTest
	public void pullTargetingAimAssistAndLineOfSight(GameTestHelper helper) {
		PullBalance b = new PullBalance();
		FakePlayer player = caster(helper, new BlockPos(1, 1, 0));
		ServerLevel level = helper.getLevel();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		// Beside the crosshair ray (about 6 degrees off): the aim assist still finds it.
		LivingEntity assisted = zombie(helper, new BlockPos(1, 1, 7));
		assisted.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 7))).add(0.6, 0, 0));
		LivingEntity found = PullTargeting.find(level, player, eye, look, b.range, b.aimAssistHalfAngle, e -> ForcePowers.canAffect(player, e, b.pvpEffects));
		helper.assertTrue(found == assisted, Component.literal("a target within the 8 degree aim assist must be found"));

		// Far off the axis (about 40 degrees): not a target.
		assisted.discard();
		LivingEntity wide = zombie(helper, new BlockPos(6, 1, 6));
		found = PullTargeting.find(level, player, eye, look, b.range, b.aimAssistHalfAngle, e -> ForcePowers.canAffect(player, e, b.pvpEffects));
		helper.assertTrue(found == null, Component.literal("a target 40 degrees off the crosshair must not be pulled"));
		wide.discard();

		// Straight ahead but behind a wall: no line of sight.
		LivingEntity hidden = zombie(helper, new BlockPos(1, 1, 6));

		for (int x = 0; x < 4; x++) {
			for (int y = 1; y < 5; y++) {
				helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
			}
		}

		found = PullTargeting.find(level, player, eye, look, b.range, b.aimAssistHalfAngle, e -> ForcePowers.canAffect(player, e, b.pvpEffects));
		helper.assertTrue(found == null, Component.literal("a target behind a wall must not be pulled"));
		hidden.discard();
		helper.succeed();
	}

	@GameTest
	public void disarmingPullDisarms(GameTestHelper helper) {
		FakePlayer player = caster(helper, new BlockPos(1, 1, 0));
		LivingEntity target = zombie(helper, new BlockPos(1, 1, 6));
		ForcePull.pull(player, target, PullShape.DISARM);
		helper.assertTrue(ForceStatus.isDisarmed(target), Component.literal("Disarming Pull must leave the target disarmed (no firing)"));
		helper.assertTrue(target.getDeltaMovement().z < 0, Component.literal("the target must be pulled toward the caster"));
		ForcePull.forget(player);
		helper.succeed();
	}

	@GameTest
	public void jumpVelocityAndFallProtection(GameTestHelper helper) {
		JumpBalance b = new JumpBalance();
		Vec3 ahead = new Vec3(0, 0, 1);
		Vec3 weak = ForceJump.launchVelocity(b, 0f, ahead, JumpShape.NONE, 0);
		Vec3 full = ForceJump.launchVelocity(b, 1f, ahead, JumpShape.NONE, 0);
		helper.assertTrue(near(weak.y, 0.75) && near(weak.z, 0.45), Component.literal("minimum charge: 0.75 up, 0.45 ahead, got " + weak));
		helper.assertTrue(near(full.y, 1.15) && near(full.z, 0.9), Component.literal("full charge: 1.15 up, 0.9 ahead, got " + full));
		Vec3 soar = ForceJump.launchVelocity(b, 1f, ahead, JumpShape.SOAR, 0);
		helper.assertTrue(near(soar.y, 1.15 * 1.1) && near(soar.z, 0.9 * 1.35), Component.literal("Soaring Leap: vertical x1.1, horizontal x1.35"));
		helper.assertTrue(ForceJump.charge(b, 3) < 0f && near(ForceJump.charge(b, 4), 0f) && near(ForceJump.charge(b, 16), 1f),
				Component.literal("the gather needs 4 ticks and is full at 16"));
		helper.assertTrue(near(ForceJump.fallAllowance(b, 5.0), 9.0), Component.literal("fall protection covers the apex height + 4 blocks"));
		helper.assertTrue(near(ForceJump.baseCost(b, 0f, 0), 14f) && near(ForceJump.baseCost(b, 1f, 0), 24f), Component.literal("cost 14 to 24 by charge"));
		helper.succeed();
	}

	@GameTest
	public void crushingDescentDamage(GameTestHelper helper) {
		JumpBalance b = new JumpBalance();
		helper.assertTrue(near(ForceJump.crushDamage(b, 4.0), 5f), Component.literal("3 + 0.5 x 4 blocks = 5"));
		helper.assertTrue(near(ForceJump.crushDamage(b, 20.0), 8f), Component.literal("Crushing Descent damage is capped at 8"));
		helper.assertTrue(near(b.crushMinFall, 3f) && near(b.crushRadius, 3f), Component.literal("needs 3 blocks of fall, radius 3"));
		helper.succeed();
	}

	@GameTest
	public void healAmountsAndInterruption(GameTestHelper helper) {
		HealBalance b = new HealBalance();
		helper.assertTrue(near(ForceHeal.healAmount(b, 0), 1f) && near(ForceHeal.healAmount(b, 2), 1.2f),
				Component.literal("1 HP per heal tick, +10% per mastery rank"));
		helper.assertTrue(ForceHeal.interrupts(b, 4f) && !ForceHeal.interrupts(b, 3.9f), Component.literal("a hit of 4 or more interrupts"));
		helper.assertTrue(b.intervalTicks == 5 && b.maxChannelTicks == 60 && b.startupTicks == 6, Component.literal("heals every 5 ticks for up to 60 after a 6-tick start"));
		helper.succeed();
	}

	@GameTest
	public void sharedLightExcludesPlayersInCombat(GameTestHelper helper) {
		HealBalance b = new HealBalance();
		FakePlayer healer = caster(helper, new BlockPos(1, 1, 1));
		FakePlayer friend = caster(helper, new BlockPos(3, 1, 1));
		LivingEntity villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(5, 1, 1));
		LivingEntity hostile = zombie(helper, new BlockPos(5, 1, 5));

		try {
			helper.assertTrue(ForceHeal.isShareTarget(healer, friend, b), Component.literal("a player you are not fighting shares the light"));
			helper.assertTrue(ForceHeal.isShareTarget(healer, villager, b), Component.literal("villagers share the light"));
			helper.assertTrue(!ForceHeal.isShareTarget(healer, hostile, b), Component.literal("monsters do not"));
			ForceHeal.onDamaged(friend, healer, 1f);
			helper.assertTrue(!ForceHeal.isShareTarget(healer, friend, b), Component.literal("a player you damaged in the last 200 ticks does not"));
			helper.assertTrue(ForceHeal.inCombat(friend, healer, b.combatExcludeTicks), Component.literal("combat is recorded both ways"));
		} finally {
			ForceHeal.forget(healer);
			ForceHeal.forget(friend);
		}

		helper.succeed();
	}

	@GameTest
	public void lightningDamageAndGuard(GameTestHelper helper) {
		LightningBalance b = new LightningBalance();
		helper.assertTrue(near(ForceLightning.damage(b, 0, LightningShape.NONE, false, 0, false, false), 1.5f), Component.literal("1.5 per damage tick"));
		helper.assertTrue(near(ForceLightning.damage(b, 0, LightningShape.NONE, false, 0, false, true), 0.3f), Component.literal("a saber guard takes x0.2"));
		helper.assertTrue(near(ForceLightning.damage(b, 3, LightningShape.NONE, false, 0, false, false), 1.5f * 1.24f), Component.literal("+8% per mastery rank"));
		helper.assertTrue(near(ForceLightning.damage(b, 0, LightningShape.CHAIN, true, 0, false, false), 0.9f), Component.literal("chained arcs deal 60%"));
		helper.assertTrue(near(ForceLightning.damage(b, 0, LightningShape.CONSUMING, false, 40, false, false), 1.5f * 1.3f),
				Component.literal("Consuming ramps +15% per second of contact"));
		helper.assertTrue(near(ForceLightning.damage(b, 0, LightningShape.CONSUMING, false, 400, false, false), 1.5f * 1.6f),
				Component.literal("the Consuming ramp stops at +60%"));
		helper.assertTrue(near(ForceLightning.damage(b, 0, LightningShape.NONE, false, 0, true, false), 0.9f), Component.literal("push-resistant targets take x0.6"));
		helper.assertTrue(b.guardDrainPerTick == 3f && b.damageIntervalTicks == 5, Component.literal("guards lose 3 stamina per damage tick (every 5 ticks)"));
		helper.assertTrue(b.burnAfterTicks == 60 && near(b.burnDamage, 0.5f), Component.literal("the caster burns for 0.5 after 60 ticks"));
		helper.assertTrue(ForceLightning.cooldownTicks(b, false) == 160 && ForceLightning.cooldownTicks(b, true) == 200,
				Component.literal("cooldown 160, or 200 after a full channel"));
		helper.succeed();
	}

	@GameTest
	public void chainLightningPicksNearbyTargets(GameTestHelper helper) {
		LightningBalance b = new LightningBalance();
		FakePlayer player = caster(helper, new BlockPos(3, 1, 0));
		LivingEntity primary = zombie(helper, new BlockPos(3, 1, 5));
		LivingEntity second = zombie(helper, new BlockPos(5, 1, 6));
		LivingEntity third = zombie(helper, new BlockPos(1, 1, 7));
		LivingEntity outside = zombie(helper, new BlockPos(7, 1, 0));

		List<LivingEntity> single = ForceLightning.pickTargets(player, LightningShape.NONE, b);
		helper.assertTrue(single.size() == 1 && single.get(0) == primary, Component.literal("the arcs pick the target nearest the aim axis"));
		List<LivingEntity> chain = ForceLightning.pickTargets(player, LightningShape.CHAIN, b);
		helper.assertTrue(chain.size() == 3 && chain.get(0) == primary && chain.contains(second) && chain.contains(third),
				Component.literal("Chain Lightning jumps to up to 2 more targets within 4 blocks of the first"));
		helper.assertTrue(!chain.contains(outside), Component.literal("a creature far from the first target is not chained"));
		helper.succeed();
	}

	@GameTest
	public void sidePowersFollowAlignment(GameTestHelper helper) {
		SkillBalance b = new SkillBalance();
		helper.assertTrue(AbilityCosts.opposition(SkillIds.TREE_SITH, 0) == AbilityCosts.Opposition.NORMAL, Component.literal("Lightning is normal at 0"));
		helper.assertTrue(AbilityCosts.opposition(SkillIds.TREE_SITH, 30) == AbilityCosts.Opposition.OPPOSED, Component.literal("Lightning is opposed at +30"));
		helper.assertTrue(near(AbilityCosts.costMultiplier(false, SkillIds.TREE_SITH, 30, b), 1.5f), Component.literal("opposed costs x1.5"));
		helper.assertTrue(AbilityCosts.opposition(SkillIds.TREE_SITH, 60) == AbilityCosts.Opposition.REFUSED, Component.literal("Lightning is refused at +60"));
		helper.assertTrue(AbilityCosts.opposition(SkillIds.TREE_JEDI, -30) == AbilityCosts.Opposition.OPPOSED
				&& AbilityCosts.opposition(SkillIds.TREE_JEDI, -60) == AbilityCosts.Opposition.REFUSED, Component.literal("Heal mirrors it on the dark side"));

		FakePlayer player = caster(helper, new BlockPos(1, 1, 1));
		String lightningTree = AbilityCosts.treeOf(player, ForceLightning.ID);
		String healTree = AbilityCosts.treeOf(player, ForceHeal.ID);
		helper.assertTrue(lightningTree == null || SkillIds.TREE_SITH.equals(lightningTree), Component.literal("Force Lightning belongs to the Sith tree"));
		helper.assertTrue(healTree == null || SkillIds.TREE_JEDI.equals(healTree), Component.literal("Force Heal belongs to the Jedi tree"));
		helper.succeed();
	}

	@GameTest
	public void pvpFlagsOffSparePlayers(GameTestHelper helper) {
		FakePlayer player = caster(helper, new BlockPos(1, 1, 1));
		FakePlayer other = caster(helper, new BlockPos(3, 1, 1));
		LivingEntity monster = zombie(helper, new BlockPos(5, 1, 1));
		helper.assertTrue(!ForcePowers.canAffect(player, other, false), Component.literal("with pvpEffects off, powers never affect players"));
		helper.assertTrue(ForcePowers.canAffect(player, monster, false), Component.literal("creatures are always affected"));
		helper.assertTrue(!ForcePowers.canAffect(player, player, true), Component.literal("never the caster"));
		helper.assertTrue(!new PullBalance().pvpEffects && !new JumpBalance().pvpEffects && !new LightningBalance().pvpEffects,
				Component.literal("every pvpEffects flag defaults to false"));
		helper.succeed();
	}

	@GameTest
	public void effectVariantsRoundTrip(GameTestHelper helper) {
		int caster = 123456;
		int packed = ForceEffects.packCaster(caster, 2);
		helper.assertTrue(ForceEffects.casterOf(packed) == caster && ForceEffects.valueOf(packed) == 2, Component.literal("caster and slot survive packing"));
		int phase = ForceEffects.packLightning(ForceEffects.LIGHTNING_ARCS, LightningShape.CHAIN.ordinal());
		helper.assertTrue(ForceEffects.lightningPhase(phase) == ForceEffects.LIGHTNING_ARCS && ForceEffects.lightningShape(phase) == LightningShape.CHAIN.ordinal(),
				Component.literal("lightning phase and shape survive packing"));
		helper.assertTrue(ForceEffects.surface(Blocks.SAND.defaultBlockState()) == ForceEffects.SURFACE_SAND
				&& ForceEffects.surface(Blocks.STONE.defaultBlockState()) == ForceEffects.SURFACE_STONE
				&& ForceEffects.surface(Blocks.SNOW_BLOCK.defaultBlockState()) == ForceEffects.SURFACE_SNOW
				&& ForceEffects.surface(Blocks.GRASS_BLOCK.defaultBlockState()) == ForceEffects.SURFACE_SOFT,
				Component.literal("landing surfaces group sand, stone, snow and soft ground"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- Milestone 3: shared fixtures

	private static final String ARENA = "oldrepublic_test:arena_24x7x24";

	/** Casters whose Sense casts the test provider logs, as {@code "blockRange/creatureRange"}. */
	private static final Map<UUID, List<String>> SENSE_CALLS = new HashMap<>();
	/** Casters the test provider floods with {@link #FLOOD_POINTS} block points beside their eyes. */
	private static final Set<UUID> SENSE_FLOOD = new HashSet<>();
	private static final int FLOOD_POINTS = 40;
	/** Creatures the test reaction handler refuses to lift (it answers a height of 0). */
	private static final Set<UUID> NO_LIFT = new HashSet<>();
	/** Creatures whose control durations the test modifier halves, with every kind it was asked about. */
	private static final Map<UUID, List<String>> HALVED = new HashMap<>();
	/** Casters whose acts the stand-in entities and the stand-in block (lodestone) take, logged in order. */
	private static final Map<UUID, List<String>> ACT_LOG = new HashMap<>();
	private static boolean m3HooksRegistered;

	/**
	 * The inert Milestone 3 test hooks, registered once (the global registries have no unregister):
	 * a Sense provider, a reaction handler capping the lift height, a control duration modifier and
	 * the handler of the stand-in block {@code minecraft:lodestone} (never {@code minecraft:target},
	 * the contracts' stand-in). Each answers only for a UUID the running test put in its set.
	 */
	private static void registerM3Hooks() {
		if (m3HooksRegistered) {
			return;
		}

		m3HooksRegistered = true;
		SenseTargets.register((caster, blockRange, creatureRange, out) -> {
			List<String> calls = SENSE_CALLS.get(caster.getUUID());

			if (calls != null) {
				calls.add(blockRange + "/" + creatureRange);
			}

			if (SENSE_FLOOD.contains(caster.getUUID())) {
				Vec3 eye = caster.getEyePosition();

				for (int i = 1; i <= FLOOD_POINTS; i++) {
					out.accept(new SenseTargets.Point(SenseTargets.Kind.BLOCK, eye.add(0, 0, i * 0.01), -1, Attitude.NEUTRAL));
				}
			}
		});
		ForceReactions.register(new ForceReactions.Handler() {
			@Override
			public float liftHeight(LivingEntity target, float requested) {
				return NO_LIFT.contains(target.getUUID()) ? 0f : requested;
			}
		});
		ControlEffects.registerDurationModifier((target, kind) -> {
			List<String> kinds = HALVED.get(target.getUUID());

			if (kinds == null) {
				return 1f;
			}

			kinds.add(kind);
			return 0.5f;
		});
		ForceBlockInteractions.register(Blocks.LODESTONE, ctx -> {
			List<String> log = ACT_LOG.get(ctx.caster().getUUID());

			if (log == null) {
				return false;
			}

			log.add("block:" + ctx.act());
			return true;
		});
	}

	/**
	 * A non-living Force-reactive stand-in (spec 10.6): an item that floats where it is put and takes
	 * every act of a caster the test marked in {@link #ACT_LOG}, logging it there in order. For any
	 * other caster it answers nothing, so to them it is a plain item.
	 */
	private static final class ReactiveItemStandIn extends ItemEntity implements ForceReactiveEntity {
		float lightningPerTick;
		float throwDamage;

		ReactiveItemStandIn(ServerLevel level, Vec3 feet) {
			super(level, feet.x, feet.y, feet.z, new ItemStack(Items.AMETHYST_SHARD), 0, 0, 0);
			setNoGravity(true);
			setNeverPickUp();
		}

		@Override
		public boolean onForcePush(ServerPlayer caster, Vec3 dir, float strength) {
			return take(caster, "push");
		}

		@Override
		public boolean onForcePull(ServerPlayer caster, Vec3 dir, float strength) {
			return take(caster, "pull");
		}

		@Override
		public boolean onForceLift(ServerPlayer caster) {
			return take(caster, "lift");
		}

		@Override
		public boolean onForceLightning(ServerPlayer caster, float perTick) {
			boolean taken = take(caster, "lightning");

			if (taken) {
				lightningPerTick = perTick;
			}

			return taken;
		}

		@Override
		public boolean onSaberThrowHit(ServerPlayer caster, float damage) {
			boolean taken = take(caster, "throw");

			if (taken) {
				throwDamage = damage;
			}

			return taken;
		}

		private boolean take(ServerPlayer caster, String act) {
			List<String> log = ACT_LOG.get(caster.getUUID());

			if (log == null) {
				return false;
			}

			log.add(act);
			return true;
		}
	}

	/** The world's reactive entity finder, and a block finder that answers only for the stand-in block (lodestone) on the eye ray. */
	private static final ForceDispatch.Finders STAND_IN_FINDERS = new ForceDispatch.Finders() {
		@Override
		public @Nullable Entity reactiveEntity(ServerPlayer caster, double range) {
			return ForceReactiveEntity.findOnAim(caster, range);
		}

		@Override
		public @Nullable BlockHitResult reactiveBlock(ServerPlayer caster, double range) {
			ServerLevel level = caster.level();
			Vec3 eye = caster.getEyePosition();
			BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(caster.getLookAngle().scale(range)), ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, caster));
			return hit.getType() == HitResult.Type.BLOCK && level.getBlockState(hit.getBlockPos()).is(Blocks.LODESTONE) ? hit : null;
		}
	};

	/** Finders that find nothing reactive, so only living targets answer. */
	private static final ForceDispatch.Finders NO_REACTIVES = new ForceDispatch.Finders() {
		@Override
		public @Nullable Entity reactiveEntity(ServerPlayer caster, double range) {
			return null;
		}

		@Override
		public @Nullable BlockHitResult reactiveBlock(ServerPlayer caster, double range) {
			return null;
		}
	};

	/** What a Force Sense cast sent, recorded instead of going to the network. */
	private static final class RecordingSink implements SenseSink {
		/** A holocron direction: never a position (spec 4.3). */
		record Pulse(Vec3 dir, float closeness, HolocronKind kind) {
		}

		final List<SenseRevealPayload> reveals = new ArrayList<>();
		final List<Integer> gestures = new ArrayList<>();
		final List<Pulse> holocrons = new ArrayList<>();
		final List<Pulse> followUps = new ArrayList<>();

		@Override
		public void reveal(ServerPlayer caster, SenseRevealPayload payload) {
			reveals.add(payload);
		}

		@Override
		public void gesture(ServerPlayer caster, int points) {
			gestures.add(points);
		}

		@Override
		public void holocron(ServerPlayer caster, Vec3 dir, float closeness, HolocronKind kind) {
			holocrons.add(new Pulse(dir, closeness, kind));
		}

		@Override
		public void followUp(ServerPlayer caster, Vec3 dir, float closeness, HolocronKind kind) {
			followUps.add(new Pulse(dir, closeness, kind));
		}

		void clear() {
			reveals.clear();
			gestures.clear();
			holocrons.clear();
			followUps.clear();
		}
	}

	/** A structure locator stand-in: cached sites the test sets, and searches that answer when the test says so (never the server's queue). */
	private static final class FakeLocator implements SenseLocator {
		record Search(SiteKind kind, int radius, Consumer<@Nullable SitePiece> callback) {
		}

		final Map<SiteKind, SitePiece> cachedSites = new EnumMap<>(SiteKind.class);
		/** Every search asked for, in order. */
		final List<Search> searches = new ArrayList<>();
		private final List<Search> pending = new ArrayList<>();

		@Override
		public @Nullable SitePiece cached(ServerPlayer p, SiteKind kind) {
			return cachedSites.get(kind);
		}

		@Override
		public void find(ServerPlayer p, SiteKind kind, int radius, Predicate<SitePiece> exclude, Consumer<@Nullable SitePiece> callback) {
			Search search = new Search(kind, radius, callback);
			searches.add(search);
			pending.add(search);
		}

		/** Answers the pending searches of {@code kind} with {@code site}. */
		void answer(SiteKind kind, @Nullable SitePiece site) {
			Iterator<Search> it = pending.iterator();
			List<Search> due = new ArrayList<>();

			while (it.hasNext()) {
				Search search = it.next();

				if (search.kind() == kind) {
					it.remove();
					due.add(search);
				}
			}

			due.forEach(search -> search.callback().accept(site));
		}

		/** Answers every pending search with "nothing found". */
		void answerRest() {
			List<Search> due = List.copyOf(pending);
			pending.clear();
			due.forEach(search -> search.callback().accept(null));
		}

		void clear() {
			cachedSites.clear();
			searches.clear();
			pending.clear();
		}
	}

	/**
	 * A listed player at {@code relative} facing +z (south, head included: powers aim along the
	 * head's view), awakened (Chapter One's FOCUS stage), knowing {@code abilities}, energy full.
	 */
	private static ServerPlayer m3Caster(GameTestHelper helper, BlockPos relative, String... abilities) {
		ServerPlayer player = M3TestPlayers.listed(helper, relative);
		player.setYRot(0f);
		player.setXRot(0f);
		player.setYHeadRot(0f);
		ForceData data = Progression.get(player);
		data.questStage = QuestStage.FOCUS.ordinal();
		data.abilities.addAll(List.of(abilities));
		data.energy = data.maxEnergy;
		Progression.commit(player, data);
		return player;
	}

	/**
	 * A survival caster for the energy checks (a listed mock player counts as creative, so its energy
	 * is never spent): a fake player facing +z, awakened, knowing {@code abilities}, energy full. It
	 * never ticks, so its cooldowns stay where a cast put them.
	 */
	private static FakePlayer survivalCaster(GameTestHelper helper, BlockPos relative, String... abilities) {
		FakePlayer player = caster(helper, relative);
		ForceData data = Progression.get(player);
		data.questStage = QuestStage.FOCUS.ordinal();
		data.abilities.addAll(List.of(abilities));
		data.energy = data.maxEnergy;
		Progression.commit(player, data);
		helper.assertTrue(!player.isCreative(), "the survival caster must not be creative");
		return player;
	}

	private static ForceAbility ability(GameTestHelper helper, String id) {
		ForceAbility ability = Abilities.get(id);
		helper.assertTrue(ability != null, "ability " + id + " must be registered");
		return ability;
	}

	/** A zombie in a leather helmet (it never burns in daylight). {@code free} keeps its AI: a lifted creature only rises with it. */
	private static Mob helmetedZombie(GameTestHelper helper, BlockPos relative, boolean free) {
		Mob zombie = free ? helper.spawn(EntityTypes.ZOMBIE, relative) : helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, relative);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		return zombie;
	}

	/** A stand-in floating {@code ahead} blocks along {@code caster}'s aim, its centre {@code below} blocks under the eye line. */
	private static ReactiveItemStandIn standIn(ServerLevel level, ServerPlayer caster, double ahead, double below, List<Entity> made) {
		Vec3 center = caster.getEyePosition().add(caster.getLookAngle().scale(ahead)).subtract(0, below, 0);
		ReactiveItemStandIn standIn = new ReactiveItemStandIn(level, center);
		standIn.setPos(center.x, center.y - standIn.getBbHeight() / 2.0, center.z);
		level.addFreshEntity(standIn);
		made.add(standIn);
		return standIn;
	}

	/** Whether {@code entity} carries Lift's transient gravity modifier. */
	private static boolean gravityHeld(LivingEntity entity) {
		AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
		return gravity != null && gravity.hasModifier(LiftedTargets.GRAVITY_MODIFIER);
	}

	/** A site piece {@code dx}, {@code dz} blocks from the player's block (its centre on that column). */
	private static SitePiece site(ServerPlayer player, SiteKind kind, int dx, int dz) {
		BlockPos at = player.blockPosition().offset(dx, 0, dz);
		BoundingBox box = new BoundingBox(at.getX() - 2, at.getY(), at.getZ() - 2, at.getX() + 2, at.getY() + 4, at.getZ() + 2);
		return new SitePiece(kind, OldRepublicMod.id("sense_test_site"), box, Rotation.NONE, at, SitePiece.runtimeKey(player.level().dimension(), at));
	}

	private static Vec3 siteCenter(SitePiece site) {
		return Vec3.atCenterOf(site.box().getCenter());
	}

	/** Marks every world holocron attuned for {@code player}: Sense then has no site left to search for. */
	private static void attuneEveryWorldHolocron(ServerPlayer player) {
		HolocronData data = HolocronService.data(player);

		for (HolocronKind kind : HolocronKind.values()) {
			for (HolocronCatalog.Entry entry : HolocronCatalog.world(kind)) {
				data.attuned.add(entry.id());
			}
		}

		HolocronService.commit(player, data);
	}

	private static float energy(ServerPlayer player) {
		return Progression.get(player).energy;
	}

	private static void setEnergy(ServerPlayer player, float energy) {
		ForceData data = Progression.get(player);
		data.energy = energy;
		Progression.commit(player, data);
	}

	/** Sets the player's alignment directly (no shift caps, no log). */
	private static void align(ServerPlayer player, int alignment) {
		IdentityData data = Identity.get(player);
		data.alignment = alignment;
		Identity.commit(player, data);
	}

	/** Logs the listed players out and removes what the test spawned. */
	private static void cleanUpM3(List<? extends Entity> made, ServerPlayer... players) {
		for (Entity entity : made) {
			if (!entity.isRemoved()) {
				entity.discard();
			}
		}

		for (ServerPlayer player : players) {
			ForceSense.forget(player);
			M3TestPlayers.remove(player);
		}
	}

	// ---------------------------------------------------------------- Milestone 3: Force Sense

	@GameTest(structure = ARENA, maxTicks = 20)
	public void senseCostCooldownCapSlot8(GameTestHelper helper) {
		registerM3Hooks();
		SenseBalance b = new SenseBalance();
		helper.assertTrue(near(b.cost, 15f) && b.cooldownTicks == 240 && b.durationTicks == 160 && b.maxPoints == 32,
				"Force Sense costs 15 FE, cools down 240 ticks, reveals for 160 ticks, at most 32 points");
		helper.assertTrue(b.creatureRange == 24 && b.blockRange == 16 && b.holocronRadius == 256 && b.seekerRadius == 512,
				"creatures within 24 blocks, hidden blocks within 16, holocron sites within 256 (512 with Seeker's Sight)");
		helper.assertTrue(near(SenseRules.cost(b, 0), 15f) && near(SenseRules.cost(b, 3), 12f) && SenseRules.creatureRange(b, 2) == 32,
				"each mastery rank takes 1 FE off and reaches 4 blocks further");
		helper.assertTrue(SenseRules.cooldown(240, false, 0.3f) == 240 && SenseRules.cooldown(240, true, 0.3f) == 168,
				"Archivist's Road cuts the cooldown by 30%: 240 -> 168");
		helper.assertTrue(AbilityInputPayload.SENSE_SLOT == 8 && AbilityInputPayload.SENSE_SLOT >= SkillData.SLOT_COUNT,
				"the Sense key travels as slot 8, outside the 4 ability slots");

		List<SenseTargets.Point> many = new ArrayList<>();

		for (int i = 0; i < 40; i++) {
			many.add(new SenseTargets.Point(SenseTargets.Kind.BLOCK, new Vec3(0, 0, i), -1, Attitude.NEUTRAL));
		}

		helper.assertTrue(new SenseRevealPayload(many, b.durationTicks).points().size() == SenseRevealPayload.MAX_POINTS,
				"the reveal payload carries at most 32 points");

		ServerLevel level = helper.getLevel();
		ForceAbility lift = ability(helper, ForceLift.ID);
		FakePlayer payer = survivalCaster(helper, new BlockPos(2, 1, 2), ForceSense.ID);
		ServerPlayer player = m3Caster(helper, new BlockPos(9, 1, 2), ForceSense.ID, ForceLift.ID);
		Mob held = helmetedZombie(helper, new BlockPos(9, 1, 6), false);
		FakeLocator locator = new FakeLocator();
		RecordingSink sink = new RecordingSink();
		SENSE_FLOOD.add(player.getUUID());

		try {
			// Cost and cooldown, on a survival caster whose energy is really spent.
			float cost = ForceSense.cost(payer);
			helper.assertTrue(near(cost, 15f), "a fresh caster pays 15 FE, got " + cost);
			float before = energy(payer);
			helper.assertTrue(ForceSense.cast(payer, locator, sink, level.getGameTime()) != null, "the cast goes through (awakened, known, energy full)");
			helper.assertTrue(near(energy(payer), before - cost), "the cast spends its cost: " + before + " -> " + energy(payer));
			helper.assertTrue(Progression.get(payer).cooldown(ForceSense.ID) == 240, "and starts the 240-tick cooldown, got " + Progression.get(payer).cooldown(ForceSense.ID));
			float after = energy(payer);
			helper.assertTrue(ForceSense.cast(payer, locator, sink, level.getGameTime()) == null && near(energy(payer), after),
					"a cast on cooldown is refused and costs nothing");

			// The cap: the flood provider offers 40 extra points beside the eyes; the reveal keeps the 32 nearest.
			sink.clear();
			ForceSense.Cast flood = ForceSense.cast(player, locator, sink, level.getGameTime());
			helper.assertTrue(flood != null && flood.points().size() == 32, "a cast keeps at most 32 points, got " + (flood == null ? "a refusal" : flood.points().size()));
			helper.assertTrue(sink.reveals.size() == 1 && sink.reveals.get(0).points().size() == 32 && sink.gestures.equals(List.of(32)),
					"one reveal of 32 points and one gesture");

			// Slot 8: the real key path (network sink, world locator). With every world holocron attuned
			// there is no site to search for, so the cast never reaches the server's locator queue.
			attuneEveryWorldHolocron(player);
			helper.assertTrue(HolocronService.senseSearchKinds(player).isEmpty(), "with every world holocron attuned Sense searches for nothing");
			Progression.setCooldown(player, ForceSense.ID, 0);
			AbilityRuntime.onInput(player, AbilityInputPayload.SENSE_SLOT, AbilityAction.START);
			helper.assertTrue(Progression.get(player).cooldown(ForceSense.ID) == 240, "the Sense key (slot 8) casts Force Sense, cooldown "
					+ Progression.get(player).cooldown(ForceSense.ID));

			// Never in a slot.
			helper.assertTrue(SkillRules.slotless(ForceSense.ID) && !SkillRules.slotless(ForceLift.ID), "Force Sense is slotless, Lift is not");
			helper.assertTrue(!SkillService.assignSlot(player, 0, ForceSense.ID), "Force Sense cannot be put in a slot");

			for (int i = 0; i < SkillData.SLOT_COUNT; i++) {
				helper.assertTrue(!ForceSense.ID.equals(Skills.slot(player, i)), "slot " + (i + 1) + " must not hold Force Sense");
			}

			// Not while another power channels.
			helper.assertTrue(ForceLift.liftTarget(player, NO_REACTIVES) == ForceDispatch.Step.LIVING && lift.isActive(player), "a Lift channel holds the zombie");
			Progression.setCooldown(player, ForceSense.ID, 0);
			sink.clear();
			helper.assertTrue(ForceSense.cast(player, locator, sink, level.getGameTime()) == null && sink.reveals.isEmpty(),
					"Force Sense is refused while Lift channels");
			helper.succeed();
		} finally {
			lift.cancel(player, false);
			SENSE_FLOOD.remove(player.getUUID());
			ForceSense.forget(payer);
			cleanUpM3(List.of(held), player);
		}
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void senseProvidersCalledNoBlockScan(GameTestHelper helper) {
		registerM3Hooks();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = m3Caster(helper, new BlockPos(2, 1, 2), ForceSense.ID);
		ServerPlayer other = m3Caster(helper, new BlockPos(4, 1, 2));
		Mob seen = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 8));
		Mob villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 6));
		Mob hidden = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(8, 1, 14));
		Mob far = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(21, 1, 21));
		List<Entity> made = List.of(seen, villager, hidden, far);

		// A wall between the caster and the hidden zombie, and vanilla blocks a block scan would find.
		for (int x = 6; x <= 9; x++) {
			for (int y = 1; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, 10), Blocks.STONE);
			}
		}

		helper.setBlock(new BlockPos(3, 1, 4), Blocks.CHEST);
		helper.setBlock(new BlockPos(1, 1, 5), Blocks.BARREL);
		helper.setBlock(new BlockPos(0, 1, 7), Blocks.CRACKED_STONE_BRICKS);
		List<String> calls = new ArrayList<>();
		SENSE_CALLS.put(player.getUUID(), calls);
		RecordingSink sink = new RecordingSink();

		try {
			ForceSense.Cast cast = ForceSense.cast(player, new FakeLocator(), sink, level.getGameTime());
			helper.assertTrue(cast != null, "the cast goes through");
			helper.assertValueEqual(calls, List.of("16/24"), "every provider is asked once per cast, with block range 16 and creature range 24");

			Map<Integer, SenseTargets.Point> creatures = new HashMap<>();
			AABB bounds = helper.getBounds();

			for (SenseTargets.Point point : cast.points()) {
				if (point.kind() == SenseTargets.Kind.CREATURE) {
					creatures.put(point.entityId(), point);
				} else {
					helper.assertTrue(!bounds.contains(point.pos()), "vanilla blocks are never revealed (providers use POI and entity lookups, never a block scan): "
							+ point.kind() + " at " + point.pos());
				}
			}

			SenseTargets.Point zombiePoint = creatures.get(seen.getId());
			SenseTargets.Point villagerPoint = creatures.get(villager.getId());
			SenseTargets.Point hiddenPoint = creatures.get(hidden.getId());
			helper.assertTrue(zombiePoint != null && zombiePoint.attitude() == Attitude.HOSTILE && zombiePoint.pos().distanceTo(ForcePowers.chest(seen)) < 1.0e-3,
					"the zombie is revealed at its chest, tinted hostile");
			helper.assertTrue(villagerPoint != null && villagerPoint.attitude() == Attitude.NEUTRAL, "the villager is revealed, tinted neutral");
			helper.assertTrue(hiddenPoint != null && hiddenPoint.attitude() == Attitude.HOSTILE, "the zombie behind the wall is revealed too");
			helper.assertTrue(!creatures.containsKey(far.getId()), "a creature 26 blocks away is beyond the 24-block creature range");
			helper.assertTrue(!creatures.containsKey(other.getId()), "other players are not revealed while pvpEffects is off");

			double last = -1;
			Vec3 eye = player.getEyePosition();

			for (SenseTargets.Point point : cast.points()) {
				double distance = point.pos().distanceTo(eye);
				helper.assertTrue(distance >= last - 1.0e-6, "points are sorted nearest first");
				last = distance;
			}

			helper.assertTrue(!player.hasLineOfSight(hidden) && player.hasLineOfSight(seen), "the wall hides one zombie and not the other");
			helper.assertTrue(cast.hidden() >= 1 && Progression.get(player).mastery(ForceSense.ID) == 1,
					"a cast that reveals something hidden counts it and earns one mastery point (hidden " + cast.hidden() + ")");
			helper.assertTrue(sink.reveals.size() == 1 && sink.reveals.get(0).points().equals(cast.points()) && sink.reveals.get(0).durationTicks() == 160,
					"the caster gets one 160-tick reveal of exactly these points");
			helper.assertTrue(sink.gestures.equals(List.of(cast.points().size())), "the watchers get one gesture with the point count");
			helper.succeed();
		} finally {
			SENSE_CALLS.remove(player.getUUID());
			cleanUpM3(made, player, other);
		}
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void senseColdCastSendsRevealWithoutDirection(GameTestHelper helper) {
		registerM3Hooks();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = m3Caster(helper, new BlockPos(2, 1, 2), ForceSense.ID);
		Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 8));
		FakeLocator locator = new FakeLocator();
		RecordingSink sink = new RecordingSink();

		try {
			List<SiteKind> kinds = HolocronService.senseSearchKinds(player);
			helper.assertTrue(!kinds.isEmpty(), "a fresh player still has holocron sites to look for");

			// Cold: nothing cached. The reveal goes out at once; there is no direction to give.
			ForceSense.Cast cold = ForceSense.cast(player, locator, sink, level.getGameTime());
			helper.assertTrue(cold != null && cold.cachedHolocron() == null, "a cold cast goes through without a cached site");
			helper.assertTrue(sink.reveals.size() == 1 && sink.reveals.get(0).points().equals(cold.points()) && sink.reveals.get(0).durationTicks() == 160,
					"the reveal still goes to the caster at once");
			helper.assertTrue(sink.gestures.equals(List.of(cold.points().size())), "and the gesture to the watchers");
			helper.assertTrue(sink.holocrons.isEmpty() && sink.followUps.isEmpty(), "no holocron direction at the cast (force_sense variant 1 is not sent)");
			helper.assertValueEqual(locator.searches.stream().map(FakeLocator.Search::kind).toList(), kinds,
					"one fresh search per site kind still worth a visit, in senseSearchKinds order");
			helper.assertTrue(locator.searches.stream().allMatch(search -> search.radius() == 256), "every search reaches 256 blocks");
			locator.answerRest();
			helper.assertTrue(sink.followUps.isEmpty(), "searches that find nothing send no follow-up");

			// The contrast: a cached site goes out with the cast, as a direction and a closeness only.
			Progression.setCooldown(player, ForceSense.ID, 0);
			sink.clear();
			locator.clear();
			SitePiece ruin = site(player, SiteKind.JEDI_RUIN, 60, -80);
			locator.cachedSites.put(SiteKind.JEDI_RUIN, ruin);
			ForceSense.Cast warm = ForceSense.cast(player, locator, sink, level.getGameTime());
			helper.assertTrue(warm != null && warm.cachedHolocron() == ruin && sink.holocrons.size() == 1, "a cached site goes out with the cast");
			RecordingSink.Pulse pulse = sink.holocrons.get(0);
			Vec3 expected = SenseRules.horizontalDirection(player.position(), siteCenter(ruin));
			helper.assertTrue(near(pulse.dir().length(), 1.0) && pulse.dir().y == 0 && pulse.dir().distanceTo(expected) < 1.0e-4,
					"the direction is a flat unit vector toward the site, got " + pulse.dir());
			helper.assertTrue(near(pulse.closeness(), SenseRules.closeness(100, 256)), "closeness is 1 - 100/256 at 100 blocks, got " + pulse.closeness());
			helper.assertTrue(pulse.kind() == HolocronService.senseKind(player, ruin), "tinted by the holocron the site would give");
			helper.succeed();
		} finally {
			cleanUpM3(List.of(zombie), player);
		}
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void senseFollowUpPulseWithin40Ticks(GameTestHelper helper) {
		registerM3Hooks();
		helper.assertTrue(new HolocronBalance().senseFollowUpTicks == 40, "the follow-up window defaults to 40 ticks");
		helper.assertTrue(SenseRules.followUpDue(0, 40, 40) && !SenseRules.followUpDue(0, 41, 40) && !SenseRules.followUpDue(10, 9, 40),
				"an answer earns the pulse up to 40 ticks after the cast, inclusive");

		ServerLevel level = helper.getLevel();
		int window = HolocronBalance.get().senseFollowUpTicks;
		ServerPlayer player = m3Caster(helper, new BlockPos(2, 1, 2), ForceSense.ID);
		FakeLocator locator = new FakeLocator();
		RecordingSink sink = new RecordingSink();
		SitePiece wreck = site(player, SiteKind.WRECK_FIELD, 120, 0);
		SitePiece ruin = site(player, SiteKind.JEDI_RUIN, 0, 90);
		Vec3 east = new Vec3(1, 0, 0);

		try {
			helper.assertTrue(HolocronService.senseSearchKinds(player).containsAll(List.of(SiteKind.WRECK_FIELD, SiteKind.JEDI_RUIN)),
					"a fresh player searches wreck fields and Jedi ruins");

			// In time: every search answers at once, one with the wreck: one pulse toward it.
			helper.assertTrue(ForceSense.cast(player, locator, sink, level.getGameTime()) != null, "the first cast goes through");
			locator.answer(SiteKind.WRECK_FIELD, wreck);
			locator.answerRest();
			helper.assertTrue(sink.followUps.size() == 1, "an answer within the window sends one follow-up pulse, got " + sink.followUps.size());
			RecordingSink.Pulse pulse = sink.followUps.get(0);
			helper.assertTrue(pulse.dir().distanceTo(east) < 1.0e-4 && near(pulse.closeness(), SenseRules.closeness(120, 256))
					&& pulse.kind() == HolocronService.senseKind(player, wreck), "the pulse points at the wreck 120 blocks east, got " + pulse);

			// Too late: the answers come 41 ticks after the cast.
			Progression.setCooldown(player, ForceSense.ID, 0);
			sink.clear();
			locator.clear();
			ForceSense.cast(player, locator, sink, level.getGameTime() - (window + 1));
			locator.answer(SiteKind.WRECK_FIELD, wreck);
			locator.answerRest();
			helper.assertTrue(sink.followUps.isEmpty(), "an answer " + (window + 1) + " ticks after the cast sends nothing");

			// Already given: the cast pointed at the cached ruin; a search finding the same ruin adds nothing.
			Progression.setCooldown(player, ForceSense.ID, 0);
			sink.clear();
			locator.clear();
			locator.cachedSites.put(SiteKind.JEDI_RUIN, ruin);
			ForceSense.cast(player, locator, sink, level.getGameTime());
			locator.answer(SiteKind.JEDI_RUIN, ruin);
			locator.answerRest();
			helper.assertTrue(sink.holocrons.size() == 1 && sink.followUps.isEmpty(), "the follow-up never repeats the direction the cast already gave");

			// The deadline: the window closes at the end of this tick with only the wreck's search answered.
			Progression.setCooldown(player, ForceSense.ID, 0);
			sink.clear();
			locator.clear();
			ForceSense.cast(player, locator, sink, level.getGameTime() - window);
			locator.answer(SiteKind.WRECK_FIELD, wreck);
			helper.assertTrue(sink.followUps.isEmpty(), "while other searches are pending nothing is sent yet");
		} catch (RuntimeException | Error e) {
			cleanUpM3(List.of(), player);
			throw e;
		}

		helper.runAfterDelay(3, () -> {
			try {
				helper.assertTrue(sink.followUps.size() == 1 && sink.followUps.get(0).dir().distanceTo(east) < 1.0e-4,
						"at the deadline the best answer so far (the wreck) is sent, got " + sink.followUps);
				locator.answer(SiteKind.JEDI_RUIN, ruin);
				locator.answerRest();
				helper.assertTrue(sink.followUps.size() == 1, "answers after the deadline change nothing");
				helper.succeed();
			} finally {
				cleanUpM3(List.of(), player);
			}
		});
	}

	// ---------------------------------------------------------------- Milestone 3: Telekinetic Lift

	@GameTest(structure = ARENA, maxTicks = 140)
	public void liftHeightDurationResistantHeight(GameTestHelper helper) {
		registerM3Hooks();
		LiftBalance b = new LiftBalance();
		SkillBalance s = new SkillBalance();
		helper.assertTrue(near(LiftRules.requested(false, false, b), 2.5f) && near(LiftRules.requested(true, false, b), 1.2f)
				&& near(LiftRules.requested(false, true, b), 1.2f), "Lift raises 2.5 blocks, push-resistant targets and bosses 1.2");
		helper.assertTrue(near(LiftRules.height(false, false, 1f, b), 1f) && near(LiftRules.height(false, false, 9f, b), 2.5f),
				"a reaction handler can lower the height, never raise it");
		helper.assertTrue(LiftRules.holdTicks(false, b) == 60 && LiftRules.holdTicks(true, b) == 100, "the hold is 60 ticks, 100 with Suspend");
		helper.assertTrue(LiftRules.liftable(1.95f, b) && !LiftRules.liftable(2.7f, b), "creatures up to 2.4 blocks tall can be lifted");
		helper.assertTrue(near(LiftRules.cost(20f, 0, false, b, s), 20f) && near(LiftRules.cost(20f, 2, true, b, s), 20f * 0.9f * 0.9f),
				"mastery takes 5% per rank and Archivist's Road 10% off the cost");
		helper.assertTrue(near(LiftRules.range(0, b), 14f) && near(LiftRules.range(3, b), 17f), "range 14, +1 per mastery rank");

		ForceAbility lift = ability(helper, ForceLift.ID);
		ServerPlayer lifter = m3Caster(helper, new BlockPos(2, 1, 1), ForceLift.ID);
		ServerPlayer second = m3Caster(helper, new BlockPos(9, 1, 1), ForceLift.ID);
		Mob zombie = helmetedZombie(helper, new BlockPos(2, 1, 5), true);
		Mob acolyte = helper.spawn(EnemyEntities.SITH_ACOLYTE, new BlockPos(9, 1, 5));
		List<Entity> made = List.of(zombie, acolyte);
		double zombieFloor = zombie.getY();
		double acolyteFloor = acolyte.getY();
		float zombieHealth = zombie.getHealth();

		try {
			helper.assertTrue(!ForcePowers.isResistant(zombie) && ForcePowers.isResistant(acolyte), "the acolyte is #push_resistant, the zombie is not");
			helper.assertTrue(ForceLift.liftTarget(lifter, NO_REACTIVES) == ForceDispatch.Step.LIVING
					&& ForceLift.liftTarget(second, NO_REACTIVES) == ForceDispatch.Step.LIVING, "both lifts reach their living target");
			helper.assertTrue(LiftedTargets.isLifted(zombie) && LiftedTargets.isLifted(acolyte) && gravityHeld(zombie) && gravityHeld(acolyte),
					"both are held: lifted, with the transient gravity modifier " + LiftedTargets.GRAVITY_MODIFIER);
		} catch (RuntimeException | Error e) {
			cleanUpM3(made, lifter, second);
			throw e;
		}

		helper.runAfterDelay(25, () -> {
			try {
				double zombieRise = zombie.getY() - zombieFloor;
				double acolyteRise = acolyte.getY() - acolyteFloor;
				helper.assertTrue(Math.abs(zombieRise - b.height) < 0.3, "a zombie rises 2.5 blocks, rose " + zombieRise);
				helper.assertTrue(Math.abs(acolyteRise - b.resistantHeight) < 0.3, "a push-resistant acolyte rises 1.2 blocks, rose " + acolyteRise);
				helper.assertTrue(lift.isActive(lifter) && lift.isActive(second) && gravityHeld(zombie) && gravityHeld(acolyte),
						"both are still held 25 ticks into the 60-tick hold");
			} catch (RuntimeException | Error e) {
				lift.cancel(lifter, false);
				lift.cancel(second, false);
				cleanUpM3(made, lifter, second);
				throw e;
			}
		});
		helper.runAfterDelay(100, () -> {
			try {
				helper.assertTrue(!lift.isActive(lifter) && !lift.isActive(second), "the channels end when the 60-tick hold runs out");
				helper.assertTrue(!LiftedTargets.isLifted(zombie) && !LiftedTargets.isLifted(acolyte) && !gravityHeld(zombie) && !gravityHeld(acolyte),
						"both are let go and their gravity comes back");
				float slam = zombieHealth - zombie.getHealth();
				helper.assertTrue(slam > 4f && slam <= b.slamDamage + 1.0e-3f, "the zombie is slammed down for 6 (less its armour), took " + slam);
				helper.assertTrue(Math.abs(zombie.getY() - zombieFloor) < 0.1, "the zombie is back on the floor, at " + (zombie.getY() - zombieFloor));
				helper.succeed();
			} finally {
				lift.cancel(lifter, false);
				lift.cancel(second, false);
				cleanUpM3(made, lifter, second);
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void liftHeightZeroRefuses(GameTestHelper helper) {
		registerM3Hooks();
		LiftBalance b = new LiftBalance();
		helper.assertTrue(LiftRules.height(false, false, 0f, b) == 0f && LiftRules.height(false, false, -1f, b) == 0f && LiftRules.height(true, true, 0f, b) == 0f,
				"a handler answer of 0 (or less) refuses the lift");

		ForceAbility lift = ability(helper, ForceLift.ID);
		ServerPlayer lifter = m3Caster(helper, new BlockPos(2, 1, 1), ForceLift.ID);
		ServerPlayer golemLifter = m3Caster(helper, new BlockPos(9, 1, 1), ForceLift.ID);
		FakePlayer payer = survivalCaster(helper, new BlockPos(14, 1, 1), ForceLift.ID);
		Mob refuser = helmetedZombie(helper, new BlockPos(2, 1, 5), false);
		Mob golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new BlockPos(9, 1, 5));
		Mob payerTarget = helmetedZombie(helper, new BlockPos(14, 1, 5), false);
		List<Entity> made = List.of(refuser, golem, payerTarget);
		NO_LIFT.add(refuser.getUUID());
		NO_LIFT.add(payerTarget.getUUID());

		try {
			helper.assertTrue(ForceReactions.liftHeight(refuser, b.height) == 0f, "the test handler answers 0 for the marked zombie");
			helper.assertTrue(ForceLift.liftTarget(lifter, NO_REACTIVES) == ForceDispatch.Step.NONE && !LiftedTargets.isLifted(refuser)
					&& !gravityHeld(refuser) && !lift.isActive(lifter), "a height of 0 refuses: nothing is lifted and no channel starts");
			helper.assertTrue(!LiftRules.liftable(golem.getBbHeight(), b) && ForceLift.liftTarget(golemLifter, NO_REACTIVES) == ForceDispatch.Step.NONE
					&& !LiftedTargets.isLifted(golem) && !lift.isActive(golemLifter), "an iron golem (2.7 blocks) is too heavy to lift");

			float before = energy(payer);
			lift.onInput(payer, AbilityAction.START);
			helper.assertTrue(!lift.isActive(payer) && near(energy(payer), before) && Progression.get(payer).cooldown(ForceLift.ID) == 0,
					"a refused START spends nothing and starts no cooldown (energy " + before + " -> " + energy(payer) + ")");

			NO_LIFT.remove(refuser.getUUID());
			helper.assertTrue(near(ForceReactions.liftHeight(refuser, b.height), b.height), "the handler is inert for anything the test did not mark");
			helper.succeed();
		} finally {
			NO_LIFT.remove(refuser.getUUID());
			NO_LIFT.remove(payerTarget.getUUID());
			lift.cancel(lifter, false);
			lift.cancel(golemLifter, false);
			lift.cancel(payer, false);
			cleanUpM3(made, lifter, golemLifter);
		}
	}

	@GameTest(structure = ARENA, maxTicks = 80)
	public void liftHoldUsesHoldKind(GameTestHelper helper) {
		registerM3Hooks();
		helper.assertTrue("force_lift".equals(ForceLift.HOLD_SOURCE) && "hold".equals(ControlEffects.HOLD), "Lift holds as source force_lift, kind hold");

		ForceAbility lift = ability(helper, ForceLift.ID);
		ServerPlayer lifter = m3Caster(helper, new BlockPos(2, 1, 1), ForceLift.ID);
		Mob zombie = helmetedZombie(helper, new BlockPos(2, 1, 5), true);
		List<Entity> made = List.of(zombie);
		List<String> kinds = new ArrayList<>();
		HALVED.put(zombie.getUUID(), kinds);

		try {
			helper.assertTrue(ForceLift.liftTarget(lifter, NO_REACTIVES) == ForceDispatch.Step.LIVING && LiftedTargets.isLifted(zombie), "the zombie is lifted");
			helper.assertValueEqual(List.copyOf(kinds), List.of(ControlEffects.HOLD), "the hold length is asked of the duration modifiers once, as kind \"hold\"");
		} catch (RuntimeException | Error e) {
			HALVED.remove(zombie.getUUID());
			lift.cancel(lifter, false);
			cleanUpM3(made, lifter);
			throw e;
		}

		helper.runAfterDelay(25, () -> {
			try {
				helper.assertTrue(lift.isActive(lifter) && LiftedTargets.isLifted(zombie), "25 ticks into the halved 30-tick hold the zombie is still held");
			} catch (RuntimeException | Error e) {
				HALVED.remove(zombie.getUUID());
				lift.cancel(lifter, false);
				cleanUpM3(made, lifter);
				throw e;
			}
		});
		helper.runAfterDelay(40, () -> {
			try {
				helper.assertTrue(!lift.isActive(lifter) && !LiftedTargets.isLifted(zombie) && !gravityHeld(zombie),
						"the hold modifier's x0.5 ended the hold at 30 ticks instead of 60");
				helper.succeed();
			} finally {
				HALVED.remove(zombie.getUUID());
				lift.cancel(lifter, false);
				cleanUpM3(made, lifter);
			}
		});
	}

	// ---------------------------------------------------------------- Milestone 3: the 10.6 dispatch order

	@GameTest(structure = ARENA, maxTicks = 20)
	public void dispatchOrderPushPullLift(GameTestHelper helper) {
		registerM3Hooks();
		ServerLevel level = helper.getLevel();
		ForceAbility lift = ability(helper, ForceLift.ID);
		ServerPlayer pusher = m3Caster(helper, new BlockPos(2, 1, 1), ForcePush.ABILITY_ID);
		ServerPlayer caster = m3Caster(helper, new BlockPos(9, 1, 1), ForceLift.ID, ForcePull.ID);
		List<String> pushLog = new ArrayList<>();
		List<String> log = new ArrayList<>();
		ACT_LOG.put(pusher.getUUID(), pushLog);
		ACT_LOG.put(caster.getUUID(), log);
		List<Entity> made = new ArrayList<>();
		BlockPos block = new BlockPos(9, 2, 11);

		try {
			// Push: the cone first (a reactive entity in it takes the push itself), then the reactive block on the aim.
			ReactiveItemStandIn pushed = standIn(level, pusher, 2.0, 0.0, made);
			Mob knocked = helmetedZombie(helper, new BlockPos(2, 1, 4), false);
			made.add(knocked);
			helper.setBlock(new BlockPos(2, 2, 6), Blocks.LODESTONE);
			int hostile = ForcePush.pushTargets(pusher, 1f, STAND_IN_FINDERS);
			helper.assertValueEqual(List.copyOf(pushLog), List.of("push", "block:" + ForceAct.PUSH),
					"Push: the reactive entity in the cone answers, then the reactive block on the aim");
			helper.assertTrue(pushed.getDeltaMovement().lengthSqr() < 1.0e-6, "a reactive entity that took the push is not knocked like an item");
			helper.assertTrue(hostile == 1 && knocked.getDeltaMovement().z > 0.1, "the zombie in the cone is still knocked back");

			// Lift: reactive entity, then living, then block, then nothing.
			ReactiveItemStandIn reactive = standIn(level, caster, 4.0, 0.0, made);
			Mob target = helmetedZombie(helper, new BlockPos(9, 1, 8), false);
			made.add(target);
			helper.setBlock(block, Blocks.LODESTONE);
			helper.assertTrue(ForceLift.liftTarget(caster, STAND_IN_FINDERS) == ForceDispatch.Step.ENTITY && log.equals(List.of("lift")),
					"Lift reaches the reactive entity on the aim first, log " + log);
			lift.cancel(caster, false);
			reactive.discard();
			helper.assertTrue(ForceLift.liftTarget(caster, STAND_IN_FINDERS) == ForceDispatch.Step.LIVING && LiftedTargets.isLifted(target),
					"then the living target");
			lift.cancel(caster, false);
			helper.assertTrue(!LiftedTargets.isLifted(target) && !gravityHeld(target), "a cancelled lift lets go");
			target.discard();
			helper.assertTrue(ForceLift.liftTarget(caster, STAND_IN_FINDERS) == ForceDispatch.Step.BLOCK && log.equals(List.of("lift", "block:" + ForceAct.LIFT)),
					"then the reactive block, log " + log);
			lift.cancel(caster, false);
			helper.setBlock(block, Blocks.AIR);
			helper.assertTrue(ForceLift.liftTarget(caster, STAND_IN_FINDERS) == ForceDispatch.Step.NONE && !lift.isActive(caster), "then nothing");

			// Pull: reactive entity, then living, then block, then loose items, then nothing.
			log.clear();
			reactive = standIn(level, caster, 4.0, 0.0, made);
			target = helmetedZombie(helper, new BlockPos(9, 1, 8), false);
			made.add(target);
			helper.setBlock(block, Blocks.LODESTONE);
			helper.assertTrue(ForcePull.pullTarget(caster, PullShape.NONE, STAND_IN_FINDERS) == ForceDispatch.Step.ENTITY && log.equals(List.of("pull")),
					"Pull reaches the reactive entity on the aim first, log " + log);
			reactive.discard();
			helper.assertTrue(ForcePull.pullTarget(caster, PullShape.NONE, STAND_IN_FINDERS) == ForceDispatch.Step.LIVING && target.getDeltaMovement().z < 0,
					"then the living target, pulled toward the caster");
			target.discard();
			helper.assertTrue(ForcePull.pullTarget(caster, PullShape.NONE, STAND_IN_FINDERS) == ForceDispatch.Step.BLOCK
					&& log.equals(List.of("pull", "block:" + ForceAct.PULL)), "then the reactive block, log " + log);
			helper.setBlock(block, Blocks.AIR);
			Vec3 end = PullTargeting.rayEnd(level, caster, caster.getEyePosition(), caster.getLookAngle(), ForcePull.range(ForcePull.balance(), 0));
			ItemEntity loose = new ItemEntity(level, end.x, end.y, end.z, new ItemStack(Items.IRON_INGOT), 0, 0, 0);
			loose.setNoGravity(true);
			loose.setNeverPickUp();
			level.addFreshEntity(loose);
			made.add(loose);
			helper.assertTrue(ForcePull.pullTarget(caster, PullShape.NONE, STAND_IN_FINDERS) == ForceDispatch.Step.ITEMS, "then loose items where the ray ends");
			loose.discard();
			helper.assertTrue(ForcePull.pullTarget(caster, PullShape.NONE, STAND_IN_FINDERS) == ForceDispatch.Step.NONE, "then nothing");
			helper.assertValueEqual(List.copyOf(log), List.of("pull", "block:" + ForceAct.PULL), "nothing else answered the pulls");
			helper.succeed();
		} finally {
			ACT_LOG.remove(pusher.getUUID());
			ACT_LOG.remove(caster.getUUID());
			lift.cancel(caster, false);
			ForcePull.forget(caster);
			cleanUpM3(made, pusher, caster);
		}
	}

	@GameTest(structure = ARENA, maxTicks = 60)
	public void lightningAndThrowReachReactiveEntities(GameTestHelper helper) {
		registerM3Hooks();
		ServerLevel level = helper.getLevel();
		ForceAbility lightning = ability(helper, ForceLightning.ID);
		ServerPlayer shocker = m3Caster(helper, new BlockPos(2, 1, 1), ForceLightning.ID);
		ServerPlayer thrower = m3Caster(helper, new BlockPos(9, 1, 1), SaberThrow.ID);
		List<String> shockLog = new ArrayList<>();
		List<String> throwLog = new ArrayList<>();
		ACT_LOG.put(shocker.getUUID(), shockLog);
		ACT_LOG.put(thrower.getUUID(), throwLog);
		List<Entity> made = new ArrayList<>();
		ReactiveItemStandIn rod = standIn(level, shocker, 3.0, 0.0, made);
		Mob behind = helmetedZombie(helper, new BlockPos(2, 1, 7), false);
		made.add(behind);
		// The thrown blade flies from 0.3 below the eyes.
		ReactiveItemStandIn mark = standIn(level, thrower, 3.0, 0.3, made);
		ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(thrower.getUUID(), "Thrower", BladeColor.GREEN.rgb()), BladeColor.GREEN,
				Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, SaberToggle.balance());
		SaberToggle.setIgnited(saber, true, level.getGameTime());
		thrower.setItemInHand(InteractionHand.MAIN_HAND, saber);

		try {
			lightning.onInput(shocker, AbilityAction.START);
			helper.assertTrue(lightning.isActive(shocker), "the Lightning channel starts (awakened, known)");
			helper.assertTrue(SaberThrow.throwSaber(thrower), "the saber throw starts (lit saber, known, awakened)");
		} catch (RuntimeException | Error e) {
			ACT_LOG.remove(shocker.getUUID());
			ACT_LOG.remove(thrower.getUUID());
			lightning.cancel(shocker, false);
			SaberThrow.recall(thrower);
			cleanUpM3(made, shocker, thrower);
			throw e;
		}

		helper.runAfterDelay(20, () -> {
			try {
				helper.assertTrue(shockLog.contains("lightning") && rod.lightningPerTick > 0f,
						"after the 8-tick charge the arcs feed the reactive entity on the aim ray (" + shockLog.size() + " ticks, " + rod.lightningPerTick + " per tick)");
				helper.assertTrue(near(behind.getHealth(), behind.getMaxHealth()), "while the stand-in takes the arcs nothing living behind it is struck");
				helper.assertTrue(throwLog.contains("throw") && mark.throwDamage > 0f, "the thrown saber hits the non-living reactive entity on its path ("
						+ mark.throwDamage + ")");
				helper.succeed();
			} finally {
				ACT_LOG.remove(shocker.getUUID());
				ACT_LOG.remove(thrower.getUUID());
				lightning.onInput(shocker, AbilityAction.RELEASE);
				lightning.cancel(shocker, false);
				SaberThrow.recall(thrower);
				cleanUpM3(made, shocker, thrower);
			}
		});
	}

	// ---------------------------------------------------------------- Milestone 3: Force Heal and the granted nodes

	@GameTest(structure = ARENA, maxTicks = 20)
	public void healRevivesDownedThroughTend(GameTestHelper helper) {
		helper.assertTrue(near(HealRevive.RANGE, 4.0), "Force Heal reaches downed troopers within 4 blocks (missions.healTendRange)");
		ServerPlayer healer = m3Caster(helper, new BlockPos(2, 1, 2), ForceHeal.ID);
		Mob beside = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(4, 1, 2));
		Mob edge = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(2, 1, 5));
		Mob far = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(2, 1, 8));
		Mob standing = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(1, 1, 3));
		List<Entity> made = List.of(beside, edge, far, standing);
		Set<UUID> downed = new HashSet<>(List.of(beside.getUUID(), edge.getUUID(), far.getUUID()));
		List<String> tended = new ArrayList<>();
		HealRevive.Downed access = new HealRevive.Downed() {
			@Override
			public boolean isDowned(Entity e) {
				return downed.contains(e.getUUID());
			}

			@Override
			public boolean tend(ServerPlayer p, Entity e, boolean instant) {
				tended.add(e.getUUID() + (instant ? " instant" : " slow") + (p == healer ? "" : " by someone else"));
				downed.remove(e.getUUID());
				return true;
			}
		};

		try {
			int stood = HealRevive.revive(healer, HealRevive.RANGE, access);
			helper.assertTrue(stood == 2, "the two downed within 4 blocks stand, got " + stood);
			helper.assertTrue(tended.size() == 2 && tended.contains(beside.getUUID() + " instant") && tended.contains(edge.getUUID() + " instant"),
					"each is tended once, instantly, by the healer: " + tended);
			helper.assertTrue(downed.contains(far.getUUID()) && tended.stream().noneMatch(entry -> entry.startsWith(standing.getUUID().toString())),
					"one 6 blocks away stays down, and one who is not down is never tended");
			helper.assertTrue(HealRevive.revive(healer, HealRevive.RANGE, access) == 0, "once standing they are not tended again");
			helper.assertTrue(!HealRevive.Downed.TROOPERS.isDowned(beside), "a villager is never a downed trooper");
			helper.succeed();
		} finally {
			cleanUpM3(made, healer);
		}
	}

	@GameTest(structure = ARENA, maxTicks = 80)
	public void grantedNodeEffects(GameTestHelper helper) {
		registerM3Hooks();
		SkillBalance sb = new SkillBalance();
		helper.assertTrue(SkillNodeEffects.tombHungerDue(100, 160, 60) && !SkillNodeEffects.tombHungerDue(100, 161, 60),
				"tomb_hunger counts a kill up to 60 ticks after the power hit");
		helper.assertTrue(near(EnergyRefunds.fit(0f, 6f, 12f), 6f) && near(EnergyRefunds.fit(10f, 6f, 12f), 2f) && near(EnergyRefunds.fit(12f, 6f, 12f), 0f),
				"refunds for one kill share the 12 FE cap");
		helper.assertTrue(SkillNodeEffects.scavengerDrop(true, 0.1f, 0f, sb) == M3Items.DURASTEEL_PLATE && SkillNodeEffects.scavengerDrop(true, 0.35f, 0f, sb) == null
				&& SkillNodeEffects.scavengerDrop(false, 0.1f, 0.3f, sb) == M3Items.LUMEN_THREAD && SkillNodeEffects.scavengerDrop(false, 0.1f, 0.6f, sb) == M3Items.GRAVE_SILK,
				"scavenger: 35%, a durasteel plate from troopers, lumen thread or grave silk from sentinels");
		SkillAttributes.Values capped = SkillAttributes.values(2, 0, true, false, sb);
		SkillAttributes.Values fighting = SkillAttributes.values(1, 2, true, true, sb);
		helper.assertTrue(near(capped.fallReduction(), 0.6) && near(capped.speed(), 0.1) && near(fighting.fallReduction(), 0.45)
				&& near(fighting.health(), 4.0) && near(fighting.speed(), 0.0),
				"fall reductions add up to the 60% cap; Hardy adds 2 / 4 health; Pathfinder's speed is off in combat");

		ServerLevel level = helper.getLevel();
		ForceAbility heal = ability(helper, ForceHeal.ID);
		ServerPlayer jedi = m3Caster(helper, new BlockPos(2, 1, 2), ForceHeal.ID);
		ServerPlayer plain = m3Caster(helper, new BlockPos(9, 1, 2), ForceHeal.ID);
		ServerPlayer sith = m3Caster(helper, new BlockPos(14, 1, 2));
		ServerPlayer walker = m3Caster(helper, new BlockPos(21, 1, 2));
		Mob hunted = helmetedZombie(helper, new BlockPos(14, 1, 5), false);
		Mob unmarked = helmetedZombie(helper, new BlockPos(16, 1, 4), false);
		Mob hale = helmetedZombie(helper, new BlockPos(16, 1, 7), false);
		Mob crackled = helmetedZombie(helper, new BlockPos(14, 1, 8), false);
		Mob unslowed = helmetedZombie(helper, new BlockPos(9, 1, 8), false);
		List<Entity> made = List.of(hunted, unmarked, hale, crackled, unslowed);

		try {
			align(jedi, 20);
			align(plain, 20);
			align(sith, -20);
			Skills.grant(jedi, SkillIds.WATCHERS_INSIGHT);
			Skills.grant(jedi, SkillIds.MENDING_BREATH);
			Skills.grant(sith, SkillIds.TOMB_HUNGER);
			Skills.grant(sith, SkillIds.CRACKLING_GRASP);
			Skills.grant(sith, SkillIds.ARCHIVISTS_HUNGER);
			Skills.grant(walker, SkillIds.TRAILWISE);

			// watchers_insight: Force Heal costs 10% less, only Heal, only while active.
			Float listed = AbilityRuntime.costModifiers(jedi).get(ForceHeal.ID);
			helper.assertTrue(near(ForceModifiers.cost(jedi, ForceHeal.ID), 0.9f) && listed != null && near(listed, 0.9f),
					"watchers_insight: Force Heal costs x0.9, and the HUD's cost_modifiers says so");
			helper.assertTrue(near(ForceModifiers.cost(jedi, ForcePush.ABILITY_ID), 1f) && near(ForceModifiers.cost(plain, ForceHeal.ID), 1f),
					"only Force Heal, only with the node");
			align(jedi, 0);
			helper.assertTrue(Skills.dormant(jedi, SkillIds.WATCHERS_INSIGHT) && near(ForceModifiers.cost(jedi, ForceHeal.ID), 1f),
					"below +15 alignment the node is dormant and does nothing");
			align(jedi, 20);

			// tomb_hunger: a kill soon after a power hit restores 6 FE.
			setEnergy(sith, 10f);
			QuestHooks.onAbilityHit(sith, ForcePush.ABILITY_ID, hunted);
			hunted.hurtServer(level, level.damageSources().playerAttack(sith), 1000f);
			helper.assertTrue(!hunted.isAlive() && near(energy(sith), 10f + sb.tombHungerFe), "a kill after a power hit restores 6 FE, energy " + energy(sith));
			unmarked.hurtServer(level, level.damageSources().playerAttack(sith), 1000f);
			helper.assertTrue(!unmarked.isAlive() && near(energy(sith), 10f + sb.tombHungerFe), "a kill without a power hit restores nothing, energy " + energy(sith));

			// crackling_grasp: what Lightning struck is slowed 30% for 40 ticks.
			helper.assertTrue(SkillNodeEffects.crackle(sith, List.<LivingEntity>of(crackled)) == 1 && slowedByCrackle(crackled) && SkillNodeEffects.crackling(crackled),
					"crackling_grasp slows what the arcs struck");
			helper.assertTrue(near(crackled.getAttributeValue(Attributes.MOVEMENT_SPEED), crackled.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * (1 - sb.cracklingSlow)),
					"by 30%");
			helper.assertTrue(SkillNodeEffects.crackle(plain, List.<LivingEntity>of(unslowed)) == 0 && !slowedByCrackle(unslowed), "without the node nothing is slowed");

			// archivists_hunger: +15% against targets above 75% health.
			helper.assertTrue(near(SkillNodeEffects.archivistsHunger(sith, hale), 1.15f) && near(SkillNodeEffects.archivistsHunger(plain, hale), 1f),
					"archivists_hunger: Push and Lightning deal 15% more to a hale target");
			hale.setHealth(hale.getMaxHealth() * 0.5f);
			helper.assertTrue(near(SkillNodeEffects.archivistsHunger(sith, hale), 1f), "and nothing extra at half health");

			// trailwise rank 1: 20% less fall damage, 15% less sprint hunger.
			SkillAttributes.refresh(walker);
			AttributeInstance fall = walker.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER);
			helper.assertTrue(fall != null && fall.hasModifier(SkillAttributes.FALL_MODIFIER) && near(fall.getValue(), 0.8),
					"trailwise: fall damage x0.8, got " + (fall == null ? "no attribute" : fall.getValue()));
			walker.setSprinting(true);
			helper.assertTrue(near(SkillPassives.sprintExhaustion(walker, 1f), 0.85f), "trailwise: sprint hunger x0.85");
			walker.setSprinting(false);
			helper.assertTrue(near(SkillPassives.sprintExhaustion(walker, 1f), 1f), "only while sprinting");

			// mending_breath: a Heal channel's heal ticks cleanse slowness, weakness and poison.
			for (ServerPlayer player : List.of(jedi, plain)) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 0));
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
				player.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
				player.setHealth(10f);
				heal.onInput(player, AbilityAction.START);
				helper.assertTrue(heal.isActive(player), "the Force Heal channel starts (awakened, known, standing still)");
			}
		} catch (RuntimeException | Error e) {
			heal.cancel(jedi, false);
			heal.cancel(plain, false);
			cleanUpM3(made, jedi, plain, sith, walker);
			throw e;
		}

		helper.runAfterDelay(20, () -> {
			try {
				helper.assertTrue(!jedi.hasEffect(MobEffects.SLOWNESS) && !jedi.hasEffect(MobEffects.WEAKNESS) && !jedi.hasEffect(MobEffects.POISON),
						"mending_breath: the heal ticks cleansed slowness, weakness and poison");
				helper.assertTrue(plain.hasEffect(MobEffects.SLOWNESS) && plain.hasEffect(MobEffects.WEAKNESS) && plain.hasEffect(MobEffects.POISON),
						"without the node the same channel cleanses nothing");
			} catch (RuntimeException | Error e) {
				heal.cancel(jedi, false);
				heal.cancel(plain, false);
				cleanUpM3(made, jedi, plain, sith, walker);
				throw e;
			}

			heal.onInput(jedi, AbilityAction.RELEASE);
			heal.onInput(plain, AbilityAction.RELEASE);
		});
		helper.runAfterDelay(50, () -> {
			try {
				helper.assertTrue(!slowedByCrackle(crackled) && !SkillNodeEffects.crackling(crackled), "the crackling slow ends after 40 ticks");
				helper.succeed();
			} finally {
				heal.cancel(jedi, false);
				heal.cancel(plain, false);
				cleanUpM3(made, jedi, plain, sith, walker);
			}
		});
	}

	private static boolean slowedByCrackle(LivingEntity entity) {
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		return speed != null && speed.hasModifier(SkillNodeEffects.CRACKLING_MODIFIER);
	}
}
