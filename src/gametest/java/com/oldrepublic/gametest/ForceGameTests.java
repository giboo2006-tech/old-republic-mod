package com.oldrepublic.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.ability.AbilityCosts;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.force.PushBalance;
import com.oldrepublic.force.PushBranch;
import com.oldrepublic.force.PushShape;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.force.heal.ForceHeal;
import com.oldrepublic.force.heal.HealBalance;
import com.oldrepublic.force.jump.ForceJump;
import com.oldrepublic.force.jump.JumpBalance;
import com.oldrepublic.force.jump.JumpShape;
import com.oldrepublic.force.lightning.ForceLightning;
import com.oldrepublic.force.lightning.LightningBalance;
import com.oldrepublic.force.lightning.LightningShape;
import com.oldrepublic.force.pull.ForcePull;
import com.oldrepublic.force.pull.PullBalance;
import com.oldrepublic.force.pull.PullShape;
import com.oldrepublic.force.pull.PullTargeting;
import com.oldrepublic.skill.SkillBalance;
import com.oldrepublic.skill.SkillIds;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Force power rules that can be checked without a live client: Push shapes, costs and cone
 * geometry (Milestone 1), and the Milestone 2 powers: Pull targeting, speed, brake and disarm; Jump
 * velocity, fall protection and Crushing Descent; Heal amounts, interruption and Shared Light
 * targets; Lightning damage, guard, chain targets, burn and cooldowns; alignment opposition; the
 * PvP rule. Casters are Fabric fake players (they never tick and are not in the player list).
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
}
