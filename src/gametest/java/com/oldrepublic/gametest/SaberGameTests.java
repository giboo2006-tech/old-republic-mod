package com.oldrepublic.gametest;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.EnergyRefunds;
import com.oldrepublic.boss.ArchiveDebris;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.combat.CombatEvents;
import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.combat.CombatHooks.DeflectResult;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.SithAcolyte;
import com.oldrepublic.enemy.construct.SentinelBalance;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.ComboTracker;
import com.oldrepublic.saber.LightsaberItem;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberAssembly.Status;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberForm;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberNodes;
import com.oldrepublic.saber.SaberPassives;
import com.oldrepublic.saber.SaberStats;
import com.oldrepublic.saber.SaberTechniques;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.duel.DuelState;
import com.oldrepublic.saber.duel.DuelistProfile;
import com.oldrepublic.saber.duel.DuelistProfiles;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.NpcGuard;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.duel.SparResult;
import com.oldrepublic.saber.duel.SparRules;
import com.oldrepublic.saber.throwing.SaberThrow;
import com.oldrepublic.saber.throwing.SaberThrowBalance;
import com.oldrepublic.saber.throwing.ThrownSaber;
import com.oldrepublic.skill.SkillBalance;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Saber rules: stat maths, the workbench's construct/disassemble round trip, and the guard's
 * handling of blaster bolts (with a fake server player holding an ignited saber). Milestone 2: combo
 * timing, heavy attack drain and parry immunity, lunge validation, form modifiers, the NPC guard and
 * parry rules, Saber Throw never duplicating the saber, and spar safety. Milestone 3 (W5): heavy
 * bolts and Archive debris against guards and parries, the parry window's order and cap, the two new
 * hilt parts, the singing crystal, the acolyte's deflections, the {@code DuelistProfile}
 * compatibility constructor and the Knight's and Lord's rank nodes (run through their rules with the
 * node test passed in, so the tests need no skill-tree data).
 */
public class SaberGameTests {
	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-4;
	}

	@GameTest
	public void statsMatchSpec(GameTestHelper helper) {
		SaberBalance b = new SaberBalance();
		SaberStats guardian = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, b);
		helper.assertTrue(near(guardian.damage(), 8.0) && near(guardian.attackSpeed(), 1.35) && near(guardian.reachBonus(), 0.25),
				Component.literal("Guardian/Wrapped/Weighted: 8 damage, 1.35 speed, +0.25 reach"));
		helper.assertTrue(near(guardian.maxStamina(), 120.0) && near(guardian.parryTicks(), 6.0),
				Component.literal("Wrapped grip: 120 stamina; parry window 6 ticks"));

		SaberStats duelist = SaberStats.of(Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, b);
		helper.assertTrue(near(duelist.damage(), 6.5) && near(duelist.attackSpeed(), 1.85) && near(duelist.reachBonus(), 0.0),
				Component.literal("Duelist/Ridged/Balanced: 6.5 damage, 1.85 speed, no reach"));
		helper.assertTrue(near(duelist.maxStamina(), 100.0) && near(duelist.parryTicks(), 6.6),
				Component.literal("Balanced pommel: parry window x1.1"));
		helper.succeed();
	}

	@GameTest
	public void constructThenDisassembleReturnsSameCrystal(GameTestHelper helper) {
		UUID owner = UUID.randomUUID();
		ItemStack crystal = SaberAssembly.crystalStack(new CrystalAttunement(owner, "Tester", 0x36E05A));
		SimpleContainer bench = new SimpleContainer(SaberAssembly.SIZE);
		bench.setItem(SaberAssembly.CRYSTAL, crystal.copy());
		bench.setItem(SaberAssembly.EMITTER, new ItemStack(SaberItems.EMITTER_DUELIST));
		bench.setItem(SaberAssembly.GRIP, new ItemStack(SaberItems.GRIP_WRAPPED));
		bench.setItem(SaberAssembly.POMMEL, new ItemStack(SaberItems.POMMEL_BALANCED));

		helper.assertTrue(SaberAssembly.status(bench, UUID.randomUUID()) == Status.NOT_YOUR_CRYSTAL, Component.literal("a stranger's crystal must be refused"));
		helper.assertTrue(SaberAssembly.status(bench, owner) == Status.READY, Component.literal("bench should be ready for the owner"));

		ItemStack saber = SaberAssembly.assemble(bench, owner, BladeColor.VIOLET, new SaberBalance());
		helper.assertTrue(!saber.isEmpty() && bench.getItem(SaberAssembly.CRYSTAL).isEmpty() && bench.getItem(SaberAssembly.POMMEL).isEmpty(),
				Component.literal("construct must consume the four inputs"));
		helper.assertTrue(SaberComponents.config(saber).color() == BladeColor.VIOLET && SaberComponents.config(saber).emitter() == Emitter.DUELIST,
				Component.literal("saber must carry the chosen colour and parts"));
		helper.assertTrue(SaberAssembly.disassemble(bench, UUID.randomUUID()) == null, Component.literal("only the owner may disassemble"));

		helper.assertTrue(SaberAssembly.disassemble(bench, owner) != null, Component.literal("owner disassembly must succeed"));
		helper.assertTrue(bench.getItem(SaberAssembly.SABER).isEmpty(), Component.literal("saber must leave the bench"));
		helper.assertTrue(ItemStack.isSameItemSameComponents(bench.getItem(SaberAssembly.CRYSTAL), crystal),
				Component.literal("disassembly must return the exact same crystal"));
		helper.assertTrue(bench.getItem(SaberAssembly.EMITTER).getItem() == SaberItems.EMITTER_DUELIST && bench.getItem(SaberAssembly.GRIP).getItem() == SaberItems.GRIP_WRAPPED
				&& bench.getItem(SaberAssembly.POMMEL).getItem() == SaberItems.POMMEL_BALANCED, Component.literal("disassembly must return the same parts"));
		helper.succeed();
	}

	@GameTest
	public void slotRulesFollowOwnership(GameTestHelper helper) {
		UUID owner = UUID.randomUUID();
		ItemStack crystal = SaberAssembly.crystalStack(new CrystalAttunement(owner, "Tester", 0x2F6BFF));
		helper.assertTrue(SaberAssembly.mayPlace(SaberAssembly.CRYSTAL, crystal, owner), Component.literal("owner may place their crystal"));
		helper.assertTrue(!SaberAssembly.mayPlace(SaberAssembly.CRYSTAL, crystal, UUID.randomUUID()), Component.literal("others may not"));
		helper.assertTrue(!SaberAssembly.mayPlace(SaberAssembly.CRYSTAL, new ItemStack(SaberItems.KYBER_CRYSTAL), owner), Component.literal("unattuned crystals are refused"));
		helper.assertTrue(!SaberAssembly.mayPlace(SaberAssembly.GRIP, new ItemStack(SaberItems.EMITTER_GUARDIAN), owner), Component.literal("parts only fit their own slot"));
		helper.succeed();
	}

	/**
	 * A fake server player that has held its guard for a fixed number of ticks. Fake players never
	 * tick, so the use duration is pinned here instead: 0 is inside the parry window, 20 is past it.
	 */
	private static final class GuardingPlayer extends FakePlayer {
		private final int ticksUsing;
		private final boolean vulnerable;

		GuardingPlayer(ServerLevel level, int ticksUsing, boolean vulnerable) {
			super(level, new GameProfile(UUID.randomUUID(), "guard_test"));
			this.ticksUsing = ticksUsing;
			this.vulnerable = vulnerable;
		}

		@Override
		public int getTicksUsingItem() {
			return isUsingItem() ? ticksUsing : 0;
		}

		/** Fake players ignore all damage; melee tests need the damage pipeline to run. */
		@Override
		public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
			return !vulnerable && super.isInvulnerableTo(level, source);
		}
	}

	/** A player at the test's centre, facing +z, guarding with an ignited Guardian/Wrapped/Weighted saber. */
	private static GuardingPlayer guardingPlayer(GameTestHelper helper, int ticksUsing) {
		return guardingPlayer(helper, ticksUsing, false);
	}

	private static GuardingPlayer guardingPlayer(GameTestHelper helper, int ticksUsing, boolean vulnerable) {
		return guardingPlayer(helper, ticksUsing, vulnerable, Emitter.GUARDIAN, Pommel.WEIGHTED);
	}

	/** The same, with a Wrapped saber built from {@code emitter} and {@code pommel}. */
	private static GuardingPlayer guardingPlayer(GameTestHelper helper, int ticksUsing, boolean vulnerable, Emitter emitter, Pommel pommel) {
		ServerLevel level = helper.getLevel();
		GuardingPlayer player = new GuardingPlayer(level, ticksUsing, vulnerable);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 4))));
		player.setYRot(0f);
		player.setXRot(0f);

		ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(player.getUUID(), "Tester", BladeColor.BLUE.rgb()), BladeColor.BLUE,
				emitter, Grip.WRAPPED, pommel, SaberToggle.balance());
		SaberToggle.setIgnited(saber, true, level.getGameTime());
		player.setItemInHand(InteractionHand.MAIN_HAND, saber);
		player.startUsingItem(InteractionHand.MAIN_HAND);
		helper.assertTrue(player.isUsingItem() && !SaberGuard.guardingSaber(player).isEmpty(), Component.literal("the fake player must be guarding"));
		return player;
	}

	/**
	 * A bolt arriving at the player's chest from {@code yawOffset} degrees off its facing and
	 * {@code elevation} degrees above the horizon (negative pitch is up), at poacher bolt speed.
	 */
	private static BlasterBolt incomingBolt(ServerLevel level, GuardingPlayer player, float yawOffset, float elevation) {
		Vec3 fromDir = Vec3.directionFromRotation(-elevation, player.getYRot() + yawOffset);
		Vec3 chest = player.position().add(0, 1.2, 0);
		BlasterBolt bolt = BlasterBolt.create(level, null, chest.add(fromDir.scale(0.4)), 5f, 60);
		bolt.setDeltaMovement(fromDir.reverse().scale(1.8));
		return bolt;
	}

	@GameTest
	public void guardDeflectsFrontalBolts(GameTestHelper helper) {
		GuardingPlayer player = guardingPlayer(helper, 20);

		try {
			SaberBalance b = SaberToggle.balance();
			float half = b.guardArcDegrees / 2f;
			Vec3 facing = Vec3.directionFromRotation(0f, player.getYRot());
			float[][] fronts = { { 0f, 0f }, { 25f, 0f }, { -25f, 0f }, { half - 3f, 0f }, { -(half - 3f), 0f }, { 0f, 35f }, { 20f, 60f }, { 0f, -20f } };
			int deflected = 0;

			for (float[] from : fronts) {
				BlasterBolt bolt = incomingBolt(helper.getLevel(), player, from[0], from[1]);
				DeflectResult result = CombatHooks.onProjectileHit(player, bolt);
				String where = "a bolt from " + from[0] + " deg off the facing, " + from[1] + " deg up";
				helper.assertTrue(result == DeflectResult.DEFLECTED, Component.literal(where + " must be deflected (no damage), got " + result));
				helper.assertTrue(bolt.isDeflected() && bolt.getOwner() == player, Component.literal(where + " must now belong to the defender"));
				Vec3 out = bolt.getDeltaMovement();
				helper.assertTrue(out.horizontal().normalize().dot(facing) > 0.5 && out.length() > 1.0,
						Component.literal(where + " must leave forward at speed, went " + out));
				helper.assertTrue(bolt.position().subtract(player.position()).horizontal().dot(facing) > 0.2,
						Component.literal(where + " must leave from in front of the defender, not from inside it"));
				deflected++;
			}

			SaberGuard.GuardSnapshot guard = SaberGuard.snapshot(player);
			float expected = SaberStats.of(SaberComponents.config(player.getMainHandItem()), b).maxStamina() - deflected * b.boltDrain;
			helper.assertTrue(guard != null && Math.abs(guard.stamina() - expected) < 1.0e-3,
					Component.literal("each deflection must cost " + b.boltDrain + " stamina: expected " + expected + ", got " + (guard == null ? "none" : guard.stamina())));

			for (float yaw : new float[] { half + 15f, -(half + 15f), 90f, 180f }) {
				BlasterBolt bolt = incomingBolt(helper.getLevel(), player, yaw, 0f);
				helper.assertTrue(CombatHooks.onProjectileHit(player, bolt) == DeflectResult.NONE && !bolt.isDeflected(),
						Component.literal("a bolt from " + yaw + " deg off the facing is outside the guard and must hit"));
			}

			helper.succeed();
		} finally {
			SaberGuard.forget(player);
		}
	}

	@GameTest
	public void freshGuardParriesBoltsBack(GameTestHelper helper) {
		GuardingPlayer player = guardingPlayer(helper, 0);

		try {
			BlasterBolt bolt = incomingBolt(helper.getLevel(), player, 15f, 10f);
			Vec3 incoming = bolt.getDeltaMovement();
			DeflectResult result = CombatHooks.onProjectileHit(player, bolt);
			helper.assertTrue(result == DeflectResult.PARRIED, Component.literal("a bolt inside the parry window must be parried, got " + result));
			// No shooter: a parried bolt goes straight back the way it came.
			helper.assertTrue(bolt.getDeltaMovement().normalize().dot(incoming.normalize().reverse()) > 0.99,
					Component.literal("a parried bolt must fly straight back"));
			helper.succeed();
		} finally {
			SaberGuard.forget(player);
		}
	}

	// ---------------------------------------------------------------- Milestone 2

	/** A fake player at {@code pos} (relative), facing +z, that takes damage and holds a lit Duelist/Ridged/Balanced saber. */
	private static FakePlayer saberPlayer(GameTestHelper helper, BlockPos pos, String name) {
		ServerLevel level = helper.getLevel();
		FakePlayer player = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), name));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(pos)));
		player.setYRot(0f);
		player.setXRot(0f);
		player.setYHeadRot(0f);
		ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(player.getUUID(), name, BladeColor.GREEN.rgb()), BladeColor.GREEN,
				Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, SaberToggle.balance());
		SaberToggle.setIgnited(saber, true, level.getGameTime());
		player.setItemInHand(InteractionHand.MAIN_HAND, saber);
		return player;
	}

	/** A Sith Acolyte at {@code pos} (relative) facing -z, its AI off so the guard state stays as set. */
	private static SithAcolyte acolyte(GameTestHelper helper, BlockPos pos) {
		SithAcolyte acolyte = helper.spawn(EnemyEntities.SITH_ACOLYTE, pos);
		acolyte.setNoAi(true);
		acolyte.setYRot(180f);
		acolyte.setYBodyRot(180f);
		acolyte.setYHeadRot(180f);
		return acolyte;
	}

	private static int lightsabers(Player player) {
		int count = 0;

		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).getItem() instanceof LightsaberItem) {
				count += player.getInventory().getItem(slot).getCount();
			}
		}

		return count;
	}

	@GameTest
	public void comboStepTimingAndReset(GameTestHelper helper) {
		SaberBalance b = new SaberBalance();
		int w = b.comboWindowTicks;
		float min = b.comboMinStrength;
		helper.assertTrue(ComboTracker.nextStep(0, 0, 1f, 100, w, min) == 1, Component.literal("a fresh full swing is step 1"));
		helper.assertTrue(ComboTracker.nextStep(1, 100, 1f, 100 + w, w, min) == 2, Component.literal("a full swing at the end of the window continues to step 2"));
		helper.assertTrue(ComboTracker.nextStep(2, 110, 1f, 118, w, min) == ComboTracker.FINISHER, Component.literal("the third hit is the finisher"));
		helper.assertTrue(ComboTracker.nextStep(ComboTracker.FINISHER, 118, 1f, 120, w, min) == 1, Component.literal("after the finisher a new string starts"));
		helper.assertTrue(ComboTracker.nextStep(1, 100, 1f, 101 + w, w, min) == 1, Component.literal("a swing after the window starts over"));
		helper.assertTrue(ComboTracker.nextStep(2, 100, min - 0.05f, 105, w, min) == 0, Component.literal("a weak swing does not count and resets the string"));
		helper.assertTrue(SaberForm.ATARU.modifiers(b).comboWindow() == b.ataruComboWindow && SaberForm.UNFORMED.modifiers(b).comboWindow() == w,
				Component.literal("Ataru widens the combo window"));
		helper.succeed();
	}

	@GameTest
	public void heavyCannotBeParriedAndDrainsTriple(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		ServerLevel level = helper.getLevel();
		SaberCombat.Strike heavy = SaberCombat.Strike.heavy(b);
		helper.assertTrue(heavy.unparryable() && near(heavy.guardDrainMult(), b.heavyGuardDrainMult) && !SaberCombat.Strike.LIGHT.unparryable(),
				Component.literal("the heavy strike is unparryable and drains x" + b.heavyGuardDrainMult));
		helper.assertTrue(near(b.heavyDamageMult, 1.75) && b.heavyMaxTargets == 3 && near(b.heavyArcDegrees, 90),
				Component.literal("heavy: x1.75 damage, up to 3 targets in a 90 degree arc"));

		// Both defenders are inside the parry window (0 ticks of guard).
		GuardingPlayer light = guardingPlayer(helper, 0, true);
		GuardingPlayer struck = guardingPlayer(helper, 0, true);
		FakePlayer attacker = saberPlayer(helper, new BlockPos(4, 1, 6), "heavy_attacker");

		try {
			float amount = 2f;
			DamageSource hit = level.damageSources().playerAttack(attacker);
			float before = SaberGuard.stamina(light);
			float parried = SaberCombat.modifyIncomingDamage(light, level, hit, amount, SaberCombat.Strike.LIGHT);
			helper.assertTrue(parried == 0f && near(SaberGuard.stamina(light), before),
					Component.literal("a light strike inside the parry window is parried: no damage, no stamina spent"));

			before = SaberGuard.stamina(struck);
			float through = SaberCombat.modifyIncomingDamage(struck, level, hit, amount, heavy);
			float drain = (b.meleeDrainBase + b.meleeDrainPerDamage * amount) * b.heavyGuardDrainMult;
			helper.assertTrue(near(SaberGuard.stamina(struck), Math.max(0f, before - drain)),
					Component.literal("the heavy is not parried and drains " + drain + "; stamina " + before + " -> " + SaberGuard.stamina(struck)));
			helper.assertTrue(near(through, amount * (1f - b.meleeReduction)),
					Component.literal("the heavy's damage goes through the guard, reduced to " + amount * (1f - b.meleeReduction) + ", got " + through));
			helper.succeed();
		} finally {
			SaberGuard.forget(light);
			SaberGuard.forget(struck);
		}
	}

	@GameTest
	public void lungeNeedsRangeAndLineOfSight(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		FakePlayer player = saberPlayer(helper, new BlockPos(1, 1, 0), "lunge_test");
		float range = SaberForm.UNFORMED.modifiers(b).lungeRange() + 0.5f;
		LivingEntity near = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 5));
		LivingEntity far = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 10));
		LivingEntity hidden = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(5, 1, 5));

		for (int y = 1; y <= 3; y++) {
			for (int z = 3; z <= 6; z++) {
				helper.setBlock(new BlockPos(4, y, z), Blocks.STONE);
			}
		}

		helper.assertTrue(SaberTechniques.validLungeTarget(player, near, range), Component.literal("a target 5 blocks away in the open is valid"));
		helper.assertTrue(!SaberTechniques.validLungeTarget(player, far, range), Component.literal("a target 10 blocks away is out of range"));
		helper.assertTrue(!SaberTechniques.validLungeTarget(player, hidden, 20f), Component.literal("a target behind a wall is invalid"));
		helper.assertTrue(!SaberTechniques.validLungeTarget(player, player, range), Component.literal("the player is never its own target"));
		helper.assertTrue(near(b.lungeMinRange, 3.5) && near(b.lungeMaxRange, 7) && SaberForm.SORESU.modifiers(b).lungeAllowed() == false
				&& near(SaberForm.ATARU.modifiers(b).lungeRange(), b.ataruLungeRange),
				Component.literal("lunge 3.5 to 7 blocks; Soresu cannot lunge; Ataru reaches " + b.ataruLungeRange));
		helper.succeed();
	}

	@GameTest
	public void formModifiersApplyToStats(GameTestHelper helper) {
		SaberBalance b = new SaberBalance();
		SaberStats base = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, b);
		SaberStats soresu = base.withForm(SaberForm.SORESU.modifiers(b), 0, 0f, b);
		SaberStats ataru = base.withForm(SaberForm.ATARU.modifiers(b), 0, 0f, b);
		SaberStats steady = base.withForm(SaberForm.UNFORMED.modifiers(b), 2, 1f, b);
		helper.assertTrue(near(soresu.parryTicks(), base.parryTicks() * b.soresuParryMult) && near(soresu.maxStamina(), base.maxStamina()),
				Component.literal("Soresu: parry window x" + b.soresuParryMult));
		helper.assertTrue(near(ataru.maxStamina(), base.maxStamina() * b.ataruStaminaMult)
				&& near(ataru.parryTicks(), Math.max(0f, base.parryTicks() + b.ataruParryTicks)),
				Component.literal("Ataru: stamina x" + b.ataruStaminaMult + ", parry window " + b.ataruParryTicks + " ticks"));
		helper.assertTrue(near(steady.maxStamina(), base.maxStamina() * (1f + 2 * b.steadyGuardPerRank)) && near(steady.parryTicks(), base.parryTicks() + 1f),
				Component.literal("steady_guard rank 2 adds " + 2 * b.steadyGuardPerRank * 100 + "% stamina; extra parry ticks add on"));
		helper.assertTrue(near(soresu.damage(), base.damage()) && near(SaberForm.SORESU.modifiers(b).guardArc(), b.soresuGuardArc),
				Component.literal("forms leave the saber's damage alone (Soresu's x" + b.soresuDamageMult + " is applied per hit); Soresu widens the guard arc"));
		helper.succeed();
	}

	@GameTest
	public void npcGuardBlocksDrainsAndBreaks(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		ServerLevel level = helper.getLevel();
		SithAcolyte acolyte = acolyte(helper, new BlockPos(4, 1, 6));
		FakePlayer attacker = saberPlayer(helper, new BlockPos(4, 1, 4), "npc_guard_test");
		DuelState state = NpcGuard.state(acolyte);
		helper.assertTrue(state != null, Component.literal("the acolyte's guard is registered by its combat goal"));

		long now = level.getGameTime();
		acolyte.setGuardState(GuardState.GUARDING);
		DamageSource hit = level.damageSources().playerAttack(attacker);
		float amount = 4f;
		float drain = (b.npcDrainBase + b.npcDrainPerDamage * amount) * b.heavyGuardDrainMult;
		float before = state.stamina(now);
		helper.assertTrue(drain < before, Component.literal("test setup: one heavy must not break a full guard (" + drain + " of " + before + ")"));

		float blocked = NpcGuard.onHit(acolyte, state, level, hit, amount, SaberCombat.Strike.heavy(b));
		helper.assertTrue(near(blocked, amount * b.npcBlockDamageMult) && near(state.stamina(now), before - drain),
				Component.literal("a guarded heavy is blocked to x" + b.npcBlockDamageMult + " and drains " + drain + "; got " + blocked));

		float broken = NpcGuard.onHit(acolyte, state, level, hit, amount, SaberCombat.Strike.heavy(b));
		helper.assertTrue(near(broken, amount) && state.isBroken(now) && acolyte.guardState() == GuardState.BROKEN,
				Component.literal("the next heavy breaks the guard and lands in full"));
		float punished = NpcGuard.onHit(acolyte, state, level, hit, amount, SaberCombat.Strike.LIGHT);
		helper.assertTrue(near(punished, amount * b.npcBrokenDamageMult), Component.literal("a broken guard takes x" + b.npcBrokenDamageMult));
		helper.assertTrue(!state.isBroken(now + b.npcBrokenTicks + 1), Component.literal("the break lasts " + b.npcBrokenTicks + " ticks"));

		// From behind the guard does nothing.
		state.refill();
		acolyte.setGuardState(GuardState.GUARDING);
		FakePlayer behind = saberPlayer(helper, new BlockPos(4, 1, 8), "npc_guard_behind");
		helper.assertTrue(near(NpcGuard.onHit(acolyte, state, level, level.damageSources().playerAttack(behind), amount, SaberCombat.Strike.LIGHT), amount),
				Component.literal("a hit from behind is not guarded"));
		helper.succeed();
	}

	@GameTest
	public void npcParriesOnlyLightAttacksAboveSeventyPercent(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		ServerLevel level = helper.getLevel();
		SithAcolyte acolyte = acolyte(helper, new BlockPos(4, 1, 6));
		FakePlayer attacker = saberPlayer(helper, new BlockPos(4, 1, 4), "npc_parry_test");
		DuelState state = NpcGuard.state(acolyte);
		DamageSource hit = level.damageSources().playerAttack(attacker);
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));

		for (int i = 0; i < 40; i++) {
			state.refill();
			acolyte.setGuardState(GuardState.READY);
			helper.assertTrue(NpcGuard.onHit(acolyte, state, level, hit, 2f, SaberCombat.Strike.heavy(b)) >= 0f, Component.literal("a heavy is never parried"));
			helper.assertTrue(NpcGuard.onHit(acolyte, state, level, level.damageSources().mobAttack((Mob) zombie), 2f, SaberCombat.Strike.NPC_LIGHT) >= 0f,
					Component.literal("only a player's strikes are parried"));
		}

		state.refill();
		NpcGuard.drain(acolyte, state.maxStamina() * 0.4f);

		for (int i = 0; i < 40; i++) {
			acolyte.setGuardState(GuardState.READY);
			helper.assertTrue(NpcGuard.onHit(acolyte, state, level, hit, 2f, SaberCombat.Strike.LIGHT) >= 0f,
					Component.literal("below " + b.npcParryMinStamina * 100 + "% stamina nothing is parried"));
		}

		int parries = 0;

		for (int i = 0; i < 60; i++) {
			state.refill();
			acolyte.setGuardState(GuardState.READY);

			if (NpcGuard.onHit(acolyte, state, level, hit, 2f, SaberCombat.Strike.LIGHT) < 0f) {
				parries++;
			}
		}

		helper.assertTrue(parries > 0 && parries < 60, Component.literal("at full stamina light strikes are parried by chance, got " + parries + " of 60"));
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void thrownSaberNeverDuplicates(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FakePlayer player = saberPlayer(helper, new BlockPos(2, 1, 2), "throw_test");
		ForceData force = Progression.get(player);
		force.questStage = QuestStage.COMPLETE.ordinal();
		force.abilities.add(SaberThrow.ID);
		force.energy = 100f;
		Progression.commit(player, force);
		AABB around = player.getBoundingBox().inflate(32);

		helper.assertTrue(lightsabers(player) == 1, Component.literal("test setup: one saber"));
		helper.assertTrue(SaberThrow.throwSaber(player), Component.literal("the throw must start"));
		ItemStack held = player.getMainHandItem();
		helper.assertTrue(lightsabers(player) == 1 && SaberComponents.isThrown(held) && !SaberComponents.isUsable(held),
				Component.literal("the saber never leaves the inventory; it is marked thrown and unusable"));
		helper.assertTrue(level.getEntitiesOfClass(ThrownSaber.class, around).size() == 1 && level.getEntitiesOfClass(ItemEntity.class, around).isEmpty(),
				Component.literal("one flying saber entity and no item entity"));

		// Death, logout and dimension change all recall.
		SaberThrow.recall(player);
		helper.assertTrue(!SaberComponents.isThrown(player.getMainHandItem()) && lightsabers(player) == 1,
				Component.literal("a recall clears the component and keeps exactly one saber"));
		helper.assertTrue(level.getEntitiesOfClass(ThrownSaber.class, around, e -> !e.isRemoved()).isEmpty(), Component.literal("the flying saber is gone"));

		// A dropped stack is never "in flight".
		ItemStack dropped = player.getMainHandItem().copy();
		dropped.set(SaberComponents.SABER_THROWN, UUID.randomUUID());
		ItemEntity item = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), dropped);
		level.addFreshEntity(item);
		helper.assertTrue(!SaberComponents.isThrown(item.getItem()), Component.literal("an item entity loads without the thrown component"));
		item.discard();

		// An orphaned component (its throw is gone) clears after the timeout.
		ItemStack orphan = player.getMainHandItem();
		orphan.set(SaberComponents.SABER_THROWN, UUID.randomUUID());
		SaberThrow.checkOrphan(orphan, level);
		helper.assertTrue(SaberComponents.isThrown(orphan), Component.literal("an orphan is not cleared at once"));
		helper.runAfterDelay(SaberThrowBalance.get().orphanClearTicks + 1, () -> {
			SaberThrow.checkOrphan(orphan, level);
			helper.assertTrue(!SaberComponents.isThrown(orphan) && lightsabers(player) == 1,
					Component.literal("the orphan clears after " + SaberThrowBalance.get().orphanClearTicks + " ticks"));
			helper.succeed();
		});
	}

	@GameTest
	public void sparNeverKillsEitherSide(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SithAcolyte partner = acolyte(helper, new BlockPos(4, 1, 6));
		partner.setMode(SithAcolyte.Mode.RIVAL);
		GuardingPlayer player = guardingPlayer(helper, 20, true);
		player.stopUsingItem();
		List<SparResult> results = new ArrayList<>();
		SparRules rules = new SparRules(SparRules.Goal.YIELD, 0, 0.3f, 0.5f);

		try {
			helper.assertTrue(Spar.start(player, partner, rules, results::add), Component.literal("the spar must start"));
			DamageSource fromPartner = level.damageSources().mobAttack(partner);
			float scaled = SaberCombat.modifyIncomingDamage(player, level, fromPartner, 4f);
			helper.assertTrue(near(scaled, 2f), Component.literal("the partner's hits are scaled by the rules (x0.5), got " + scaled));

			float lethal = SaberCombat.modifyIncomingDamage(player, level, fromPartner, 1000f);
			helper.assertTrue(lethal == 0f && results.equals(List.of(SparResult.LOST)) && !Spar.inSpar(player),
					Component.literal("a hit that would drop the player below 20% is cancelled and the spar is lost"));
			helper.assertTrue(near(player.getHealth(), player.getMaxHealth()), Component.literal("the player is unharmed"));

			results.clear();
			helper.assertTrue(Spar.start(player, partner, rules, results::add), Component.literal("a second spar must start"));
			float before = partner.getHealth();
			// The partner's parry is a random roll; keep it from catching this blow.
			ForceStatus.suppressGuard(partner, 20);
			SaberCombat.strike(partner, level, level.damageSources().playerAttack(player), 1000f, SaberCombat.Strike.LIGHT);
			helper.assertTrue(partner.isAlive() && near(partner.getHealth(), before) && results.equals(List.of(SparResult.WON)),
					Component.literal("a winning blow is cancelled: the partner never dies and the player wins"));

			results.clear();
			helper.assertTrue(Spar.start(player, partner, rules, results::add), Component.literal("a third spar must start"));
			LivingEntity outsider = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
			SaberCombat.modifyIncomingDamage(player, level, level.damageSources().mobAttack((Mob) outsider), 1f);
			helper.assertTrue(results.equals(List.of(SparResult.INTERRUPTED)), Component.literal("a third party's hit interrupts the spar"));
			helper.succeed();
		} finally {
			Spar.endWithoutResult(player);
			SaberGuard.forget(player);
		}
	}

	// ---------------------------------------------------------------- Milestone 3 (W5)

	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	/** Players whose parries the test listener counts in {@link #PARRIES}; each test removes its own entries. */
	private static final String TAG_PARRY_LOG = "oldrepublic_test.parry_log";
	private static final Map<UUID, Integer> PARRIES = new HashMap<>();
	private static boolean parryLogRegistered;
	/** Seed of the guard's and the acolyte's own random before deflections, so the spreads are reproducible. */
	private static final long DEFLECT_SEED = 0x5A8E12L;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	/**
	 * Registers the parry counter once (the event has no unregister); it answers only for players
	 * tagged {@link #TAG_PARRY_LOG}, so it changes nothing else.
	 */
	private static void registerParryLog() {
		if (parryLogRegistered) {
			return;
		}

		parryLogRegistered = true;
		CombatEvents.PARRY.register((defender, attacker, projectile) -> {
			if (defender.entityTags().contains(TAG_PARRY_LOG)) {
				PARRIES.merge(defender.getUUID(), 1, Integer::sum);
			}
		});
	}

	private static int parries(Player player) {
		return PARRIES.getOrDefault(player.getUUID(), 0);
	}

	/**
	 * A bolt fired by {@code shooter} at {@code target}'s chest, arriving 0.4 blocks out: a heavy
	 * {@code SENTINEL} lance at lance speed, or an ordinary 5-damage bolt at poacher speed.
	 */
	private static BlasterBolt boltFrom(ServerLevel level, LivingEntity shooter, LivingEntity target, boolean heavy) {
		SentinelBalance sb = SentinelBalance.get();
		Vec3 chest = target.position().add(0, target.getBbHeight() * 0.65, 0);
		Vec3 from = shooter.getEyePosition().subtract(chest).normalize();
		Vec3 at = chest.add(from.scale(0.4));
		BlasterBolt bolt = heavy ? BlasterBolt.create(level, shooter, at, sb.lanceDamage, 60, BlasterBolt.Style.SENTINEL, true)
				: BlasterBolt.create(level, shooter, at, 5f, 60);
		bolt.setDeltaMovement(from.reverse().scale(heavy ? sb.lanceSpeed : 1.8));
		return bolt;
	}

	private static double degreesBetween(Vec3 a, Vec3 b) {
		return Math.toDegrees(Math.acos(Mth.clamp(a.normalize().dot(b.normalize()), -1.0, 1.0)));
	}

	/** The yaw and pitch (degrees) of a direction, as {@code Vec3.directionFromRotation} takes them. */
	private static float yawOf(Vec3 dir) {
		return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
	}

	private static float pitchOf(Vec3 dir) {
		return (float) Math.toDegrees(Math.asin(Mth.clamp(-dir.y / dir.length(), -1.0, 1.0)));
	}

	/**
	 * Spec 10.7: a heavy bolt (the sentinel lance) against a late guard is blocked for
	 * {@code heavyBoltGuardDrain} and never redirected; inside the parry window it is returned
	 * straight at the shooter for {@code lanceReturnDamage}, marked parried, firing PARRY exactly once
	 * (and {@code watchers_insight} refunds once, capped). Archive debris is only ever blocked, for its
	 * own guard drain, even inside the parry window: never moved, discarded, redirected or counted as
	 * a parry. A guarding acolyte blocks a heavy bolt for {@code sentinel.lanceGuardDrain} and lets
	 * debris through.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void heavyBoltAndDebrisAgainstGuardAndParry(GameTestHelper helper) {
		registerParryLog();
		ServerLevel level = helper.getLevel();
		SaberBalance b = SaberToggle.balance();
		SentinelBalance sb = SentinelBalance.get();
		SaberBalance defaults = new SaberBalance();
		helper.assertTrue(near(defaults.heavyBoltGuardDrain, 35) && near(defaults.nonDeflectableGuardDrain, 30) && near(new SentinelBalance().lanceReturnDamage, 10),
				msg("spec 14: a heavy bolt drains 35, a non-deflectable projectile 30; a returned lance hits for 10"));
		float drainMult = SaberForm.UNFORMED.modifiers(b).drainMult();
		GuardingPlayer late = guardingPlayer(helper, 20);
		GuardingPlayer fresh = guardingPlayer(helper, 0);
		late.addTag(TAG_PARRY_LOG);
		fresh.addTag(TAG_PARRY_LOG);
		Mob shooter = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 9));

		try {
			// A heavy bolt against a late guard: blocked and drained, never sent back, no parry.
			BlasterBolt blocked = boltFrom(level, shooter, late, true);
			float before = SaberGuard.stamina(late);
			DeflectResult result = CombatHooks.onProjectileHit(late, blocked);
			helper.assertTrue(result == DeflectResult.BLOCKED, msg("a late guard blocks a heavy bolt, got " + result));
			helper.assertTrue(near(SaberGuard.stamina(late), Math.max(0f, before - b.heavyBoltGuardDrain * drainMult)),
					msg("blocking it drains " + b.heavyBoltGuardDrain + ": " + before + " -> " + SaberGuard.stamina(late)));
			helper.assertTrue(!blocked.isDeflected() && !blocked.parried() && blocked.getOwner() == shooter && near(blocked.damage(), sb.lanceDamage),
					msg("a blocked heavy bolt is never redirected"));
			helper.assertTrue(parries(late) == 0, msg("a block is no parry"));

			// The same inside the parry window: straight back at the shooter.
			BlasterBolt returned = boltFrom(level, shooter, fresh, true);
			before = SaberGuard.stamina(fresh);
			result = CombatHooks.onProjectileHit(fresh, returned);
			helper.assertTrue(result == DeflectResult.PARRIED, msg("a fresh guard parries a heavy bolt, got " + result));
			helper.assertTrue(returned.parried() && returned.isDeflected() && returned.getOwner() == fresh, msg("the returned bolt is marked parried and belongs to the defender"));
			helper.assertTrue(degreesBetween(returned.getDeltaMovement(), shooter.getEyePosition().subtract(returned.position())) < 1.0,
					msg("outside a boss arena it flies straight at the shooter"));
			helper.assertTrue(near(returned.damage(), sb.lanceReturnDamage), msg("it hits for " + sb.lanceReturnDamage + ", got " + returned.damage()));
			helper.assertTrue(near(SaberGuard.stamina(fresh), before), msg("a parry costs no stamina"));
			helper.assertTrue(parries(fresh) == 1, msg("the parry fires CombatEvents.PARRY exactly once, got " + parries(fresh)));

			// watchers_insight: a parry restores watchersInsightFe, capped per parry, once per parry moment.
			ForceData force = Progression.get(fresh);
			force.energy = 0f;
			Progression.commit(fresh, force);
			float expected = Math.min(Math.min(b.watchersInsightFe, Progression.balance().parryRefundCap), Progression.get(fresh).maxEnergy);
			float restored = SaberNodes.insightRefund(fresh, p -> true, b);
			helper.assertTrue(restored > 0f && near(restored, expected), msg("watchers_insight restores " + expected + " FE, got " + restored));
			helper.assertTrue(SaberNodes.insightRefund(fresh, p -> true, b) == 0f, msg("one refund per parry moment"));
			helper.assertTrue(SaberNodes.insightRefund(late, p -> false, b) == 0f, msg("nothing without the node"));
			// The shared per-parry cap (9.1): with 5 FE already paid for this tick's parry (Calm of the Watch), insight gives only what is left.
			ForceData lateForce = Progression.get(late);
			lateForce.energy = 0f;
			Progression.commit(late, lateForce);
			float cap = Progression.balance().parryRefundCap;
			float calm = EnergyRefunds.forParry(late, level.getGameTime(), 5f);
			float rest = SaberNodes.insightRefund(late, p -> true, b);
			helper.assertTrue(near(calm, Math.min(5f, cap)) && near(rest, Math.min(b.watchersInsightFe, Math.max(0f, cap - calm))),
					msg("after " + calm + " FE for the same parry, insight restores " + Math.min(b.watchersInsightFe, cap - calm) + ", got " + rest));

			// Archive debris inside the parry window: only blocked.
			ArchiveDebris debris = ArchiveDebris.create(level, shooter, fresh.position().add(0, 1.2, 0.6), 8f);
			debris.setDeltaMovement(0, 0, -1.1);
			Vec3 at = debris.position();
			Vec3 moving = debris.getDeltaMovement();
			before = SaberGuard.stamina(fresh);
			result = CombatHooks.onProjectileHit(fresh, debris);
			helper.assertTrue(result == DeflectResult.BLOCKED, msg("debris inside the parry window is still only blocked, got " + result));
			helper.assertTrue(near(SaberGuard.stamina(fresh), Math.max(0f, before - debris.guardDrain() * drainMult)),
					msg("blocking debris drains its own guard drain " + debris.guardDrain()));
			helper.assertTrue(debris.position().equals(at) && debris.getDeltaMovement().equals(moving) && !debris.isRemoved() && debris.getOwner() == shooter,
					msg("the guard never moves, redirects or discards debris (the chunk breaks itself)"));
			helper.assertTrue(parries(fresh) == 1, msg("blocking debris is never a parry"));

			ArchiveDebris lateDebris = ArchiveDebris.create(level, shooter, late.position().add(0, 1.2, 0.6), 8f);
			lateDebris.setDeltaMovement(0, 0, -1.1);
			helper.assertTrue(CombatHooks.onProjectileHit(late, lateDebris) == DeflectResult.BLOCKED, msg("a late guard blocks debris too"));

			// Against a guarding acolyte.
			SithAcolyte acolyte = acolyte(helper, new BlockPos(8, 1, 6));
			Mob gunner = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(8, 1, 2));
			DuelState state = NpcGuard.state(acolyte);
			helper.assertTrue(state != null, msg("the acolyte's guard is registered"));
			state.refill();
			acolyte.setGuardState(GuardState.GUARDING);
			long now = level.getGameTime();
			float npcBefore = state.stamina(now);
			BlasterBolt atAcolyte = boltFrom(level, gunner, acolyte, true);
			result = CombatHooks.onProjectileHit(acolyte, atAcolyte);
			helper.assertTrue(result == DeflectResult.BLOCKED && !atAcolyte.isDeflected(), msg("a guarding acolyte blocks a heavy bolt and never deflects it, got " + result));
			helper.assertTrue(near(state.stamina(now), Math.max(0f, npcBefore - sb.lanceGuardDrain)),
					msg("it drains the acolyte's guard by " + sb.lanceGuardDrain + ": " + npcBefore + " -> " + state.stamina(now)));
			state.refill();
			acolyte.setGuardState(GuardState.GUARDING);
			ArchiveDebris atNpc = ArchiveDebris.create(level, gunner, acolyte.position().add(0, 1.2, -0.6), 8f);
			atNpc.setDeltaMovement(0, 0, 1.1);
			helper.assertTrue(CombatHooks.onProjectileHit(acolyte, atNpc) == DeflectResult.NONE, msg("an NPC's guard never stops debris"));
			helper.succeed();
		} finally {
			SaberGuard.forget(late);
			SaberGuard.forget(fresh);
			SaberNodes.forget(late);
			SaberNodes.forget(fresh);
			EnergyRefunds.forget(late);
			EnergyRefunds.forget(fresh);
			PARRIES.remove(late.getUUID());
			PARRIES.remove(fresh.getUUID());
		}
	}

	/**
	 * Spec 10.4 stacking: base 6, plus the flat bonuses ({@code centered_mind}, {@code archivists_ward},
	 * {@code pommel_archivist}), then the form multiplier, then the cap of 11.
	 */
	@GameTest(maxTicks = 20)
	public void parryWindowOrderAndCap11(GameTestHelper helper) {
		SaberBalance b = new SaberBalance();
		float centered = new SkillBalance().centeredParryTicksPerRank;
		float ward = b.archivistsWardTicks;
		helper.assertTrue(near(b.parryWindowTicks, 6) && near(b.parryWindowCap, 11) && near(b.archivistParryTicks, 2) && near(ward, 2)
				&& near(b.soresuParryMult, 1.5) && near(b.watchersInsightFe, 3), msg("spec 10.4: base 6, pommel_archivist +2, archivists_ward +2, Soresu x1.5, cap 11; watchers_insight 3 FE"));

		// The rule itself: (hilt + flat) x form multiplier + form ticks, never below 0, then the cap.
		helper.assertTrue(near(SaberStats.parryWindow(8f, 3f, 1.5f, 0f, 0f), 16.5f), msg("flat bonuses come before the multiplier: (8 + 3) x 1.5 = 16.5, not 8 x 1.5 + 3"));
		helper.assertTrue(near(SaberStats.parryWindow(8f, 3f, 1.5f, 0f, 11f), 11f), msg("then the cap: 11"));
		helper.assertTrue(near(SaberStats.parryWindow(6f, 0f, 1f, -2f, 11f), 4f) && near(SaberStats.parryWindow(1f, 0f, 1f, -2f, 11f), 0f),
				msg("a form's own ticks come after the multiplier; the window never goes below 0"));

		SaberStats archivist = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.ARCHIVIST, b);
		SaberStats weighted = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, b);
		SaberForm.Modifiers unformed = SaberForm.UNFORMED.modifiers(b);
		SaberForm.Modifiers soresu = SaberForm.SORESU.modifiers(b);
		SaberForm.Modifiers ataru = SaberForm.ATARU.modifiers(b);
		helper.assertTrue(near(archivist.parryTicks(), 8f) && near(weighted.parryTicks(), 6f), msg("the Archivist pommel's +2 is part of the hilt's window"));
		helper.assertTrue(near(archivist.withForm(unformed, 0, centered + ward, b).parryTicks(), 11f),
				msg("6 + 2 (pommel) + 1 (centered_mind) + 2 (archivists_ward) = 11, exactly the cap"));
		helper.assertTrue(near(archivist.withForm(soresu, 0, centered + ward, b).parryTicks(), 11f), msg("x1.5 in Soresu would be 16.5: capped at 11"));
		helper.assertTrue(near(weighted.withForm(soresu, 0, 0f, b).parryTicks(), 9f), msg("Soresu alone: 6 x 1.5 = 9, under the cap"));
		helper.assertTrue(near(archivist.withForm(ataru, 0, ward, b).parryTicks(), Math.max(0f, 8f + ward + b.ataruParryTicks)),
				msg("Ataru's own ticks come after the flat bonuses"));

		SaberBalance uncapped = new SaberBalance();
		uncapped.parryWindowCap = 0f;
		SaberStats archivistUncapped = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.ARCHIVIST, uncapped);
		helper.assertTrue(near(archivistUncapped.withForm(SaberForm.SORESU.modifiers(uncapped), 0, ward, uncapped).parryTicks(), 15f),
				msg("without the cap: (6 + 2 + 2) x 1.5 = 15, not 8 x 1.5 + 2 = 14 or 6 x 1.5 + 4 = 13"));
		SaberStats balanced = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.BALANCED, b);
		helper.assertTrue(near(balanced.withForm(unformed, 0, ward, b).parryTicks(), 6f * b.balancedParryMultiplier + ward),
				msg("the Balanced pommel's x1.1 stays a hilt multiplier"));

		// The server path for a player who owns no nodes: the hilt and the form only.
		SaberBalance live = SaberToggle.balance();
		FakePlayer player = saberPlayer(helper, new BlockPos(2, 1, 2), "parry_window");
		ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(player.getUUID(), "parry_window", BladeColor.GREEN.rgb()), BladeColor.GREEN,
				Emitter.GUARDIAN, Grip.WRAPPED, Pommel.ARCHIVIST, live);
		float server = SaberPassives.serverStats(player, saber).parryTicks();
		float hiltOnly = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.ARCHIVIST, live).withForm(SaberForm.UNFORMED.modifiers(live), 0, 0f, live).parryTicks();
		helper.assertTrue(near(server, hiltOnly), msg("with no nodes the server window is the hilt's: " + hiltOnly + ", got " + server));
		helper.assertTrue(SaberNodes.wardTicks(player, live) == 0f, msg("archivists_ward adds nothing until it is learned"));
		helper.succeed();
	}

	/**
	 * Spec 8.8 and 8.9: {@code pommel_archivist} adds 2 parry ticks and -0.05 attack speed, nothing
	 * else; {@code emitter_reliquary} changes no stat but aims unparried deflections within 12 degrees
	 * of the crosshair and adds 15% to parried bolts. Both are hilt-part items the workbench takes.
	 */
	@GameTest(maxTicks = 20)
	public void hiltPartStats(GameTestHelper helper) {
		SaberBalance b = new SaberBalance();
		helper.assertTrue(near(b.archivistParryTicks, 2) && near(b.archivistAttackSpeed, -0.05) && near(b.reliquaryDeflectDegrees, 12) && near(b.reliquaryParryBonus, 0.15),
				msg("spec 14: archivist +2 ticks and -0.05 speed; reliquary 12 degrees and +15%"));
		SaberStats balanced = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.BALANCED, b);
		SaberStats archivist = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.ARCHIVIST, b);
		helper.assertTrue(near(archivist.parryTicks(), b.parryWindowTicks + b.archivistParryTicks) && near(archivist.attackSpeed(), balanced.attackSpeed() + b.archivistAttackSpeed),
				msg("pommel_archivist: parry window +2 ticks, attack speed -0.05"));
		helper.assertTrue(near(archivist.damage(), balanced.damage()) && near(archivist.reachBonus(), balanced.reachBonus()) && near(archivist.maxStamina(), balanced.maxStamina()),
				msg("and nothing else"));
		SaberStats reliquary = SaberStats.of(Emitter.RELIQUARY, Grip.WRAPPED, Pommel.BALANCED, b);
		helper.assertTrue(near(reliquary.damage(), b.baseDamage) && near(reliquary.attackSpeed(), b.baseAttackSpeed) && near(reliquary.reachBonus(), 0)
				&& near(reliquary.parryTicks(), balanced.parryTicks()) && near(reliquary.maxStamina(), balanced.maxStamina()),
				msg("emitter_reliquary changes no stat on the hilt"));

		// The items and the bench.
		helper.assertTrue(SaberItems.partItem(Pommel.ARCHIVIST) == SaberItems.POMMEL_ARCHIVIST && SaberItems.partItem(Emitter.RELIQUARY) == SaberItems.EMITTER_RELIQUARY
				&& Pommel.ARCHIVIST.itemId().equals("pommel_archivist") && Emitter.RELIQUARY.itemId().equals("emitter_reliquary"), msg("the two parts are hilt-part items"));
		UUID owner = UUID.randomUUID();
		helper.assertTrue(SaberAssembly.mayPlace(SaberAssembly.POMMEL, new ItemStack(SaberItems.POMMEL_ARCHIVIST), owner)
				&& SaberAssembly.mayPlace(SaberAssembly.EMITTER, new ItemStack(SaberItems.EMITTER_RELIQUARY), owner)
				&& !SaberAssembly.mayPlace(SaberAssembly.EMITTER, new ItemStack(SaberItems.POMMEL_ARCHIVIST), owner), msg("each fits its own slot only"));
		SimpleContainer bench = new SimpleContainer(SaberAssembly.SIZE);
		bench.setItem(SaberAssembly.CRYSTAL, SaberAssembly.crystalStack(new CrystalAttunement(owner, "Tester", 0x2F6BFF)));
		bench.setItem(SaberAssembly.EMITTER, new ItemStack(SaberItems.EMITTER_RELIQUARY));
		bench.setItem(SaberAssembly.GRIP, new ItemStack(SaberItems.GRIP_WRAPPED));
		bench.setItem(SaberAssembly.POMMEL, new ItemStack(SaberItems.POMMEL_ARCHIVIST));
		ItemStack built = SaberAssembly.assemble(bench, owner, BladeColor.BLUE, b);
		helper.assertTrue(SaberComponents.config(built).emitter() == Emitter.RELIQUARY && SaberComponents.config(built).pommel() == Pommel.ARCHIVIST,
				msg("the workbench builds a saber with both"));
		helper.assertTrue(SaberAssembly.disassemble(bench, owner) != null && bench.getItem(SaberAssembly.EMITTER).getItem() == SaberItems.EMITTER_RELIQUARY
				&& bench.getItem(SaberAssembly.POMMEL).getItem() == SaberItems.POMMEL_ARCHIVIST, msg("and gives them back"));

		// In a fight: parried bolts +15%, aimed deflections within 12 degrees.
		ServerLevel level = helper.getLevel();
		SaberBalance live = SaberToggle.balance();
		GuardingPlayer plain = guardingPlayer(helper, 0);
		GuardingPlayer relic = guardingPlayer(helper, 0, false, Emitter.RELIQUARY, Pommel.WEIGHTED);
		GuardingPlayer aimed = guardingPlayer(helper, 20, false, Emitter.RELIQUARY, Pommel.WEIGHTED);
		GuardingPlayer wild = guardingPlayer(helper, 20);

		try {
			BlasterBolt plainBolt = incomingBolt(level, plain, 10f, 0f);
			float base = plainBolt.damage();
			helper.assertTrue(CombatHooks.onProjectileHit(plain, plainBolt) == DeflectResult.PARRIED && near(plainBolt.damage(), base), msg("a Guardian parry returns the bolt at its own damage"));
			BlasterBolt relicBolt = incomingBolt(level, relic, 10f, 0f);
			helper.assertTrue(CombatHooks.onProjectileHit(relic, relicBolt) == DeflectResult.PARRIED && near(relicBolt.damage(), base * (1f + live.reliquaryParryBonus)),
					msg("a Reliquary parry adds " + live.reliquaryParryBonus * 100 + "%: " + base * (1f + live.reliquaryParryBonus) + ", got " + relicBolt.damage()));

			aimed.getRandom().setSeed(DEFLECT_SEED); // API-CHECK: RandomSource#setSeed(long) (not in the probe dumps)
			wild.getRandom().setSeed(DEFLECT_SEED);
			boolean wide = false;

			for (int i = 0; i < 12; i++) {
				float from = (i % 5 - 2) * 10f;
				SaberGuard.refill(aimed);
				BlasterBolt bolt = incomingBolt(level, aimed, from, 0f);
				helper.assertTrue(CombatHooks.onProjectileHit(aimed, bolt) == DeflectResult.DEFLECTED, msg("a late Reliquary guard deflects"));
				Vec3 out = bolt.getDeltaMovement();
				float yawOff = Math.abs(Mth.wrapDegrees(yawOf(out) - aimed.getYRot()));
				float pitchOff = Math.abs(pitchOf(out) - aimed.getXRot());
				helper.assertTrue(yawOff <= live.reliquaryDeflectDegrees + 0.01f && pitchOff <= live.reliquaryDeflectDegrees + 0.01f,
						msg("a Reliquary deflection stays within " + live.reliquaryDeflectDegrees + " degrees of the crosshair, got " + yawOff + " / " + pitchOff));

				SaberGuard.refill(wild);
				BlasterBolt loose = incomingBolt(level, wild, from, 0f);
				helper.assertTrue(CombatHooks.onProjectileHit(wild, loose) == DeflectResult.DEFLECTED, msg("a late Guardian guard deflects"));
				Vec3 looseOut = loose.getDeltaMovement();
				wide |= Math.abs(Mth.wrapDegrees(yawOf(looseOut) - wild.getYRot())) > live.reliquaryDeflectDegrees
						|| Math.abs(pitchOf(looseOut) - wild.getXRot()) > live.reliquaryDeflectDegrees;
			}

			if (live.deflectArcDegrees > live.reliquaryDeflectDegrees * 2f) {
				helper.assertTrue(wide, msg("without the emitter deflections scatter over the " + live.deflectArcDegrees + " degree arc"));
			}

			helper.succeed();
		} finally {
			SaberGuard.forget(plain);
			SaberGuard.forget(relic);
			SaberGuard.forget(aimed);
			SaberGuard.forget(wild);
		}
	}

	/**
	 * Spec 8.8: the singing crystal fits any bench while unattuned, attunes to its builder with the
	 * natural hue AMBER, and the saber carries the singing flag (saved with the stack); disassembly
	 * gives the attuned singing crystal back. The hum layer {@code saber.hum.singing} is a sound
	 * event of its own; the client plays it over the normal hum (see the client steps).
	 */
	@GameTest(maxTicks = 20)
	public void amberCrystalHumLayer(GameTestHelper helper) {
		SaberBalance b = new SaberBalance();
		UUID builder = UUID.randomUUID();
		UUID stranger = UUID.randomUUID();
		ItemStack singing = new ItemStack(M3Items.SINGING_CRYSTAL);
		helper.assertTrue(SaberAssembly.isSingingCrystal(singing) && SaberAssembly.mayPlace(SaberAssembly.CRYSTAL, singing, builder)
				&& SaberAssembly.mayPlace(SaberAssembly.CRYSTAL, singing, stranger), msg("an unattuned singing crystal fits anyone's bench"));

		SimpleContainer bench = new SimpleContainer(SaberAssembly.SIZE);
		bench.setItem(SaberAssembly.CRYSTAL, singing.copy());
		bench.setItem(SaberAssembly.EMITTER, new ItemStack(SaberItems.EMITTER_DUELIST));
		bench.setItem(SaberAssembly.GRIP, new ItemStack(SaberItems.GRIP_WRAPPED));
		bench.setItem(SaberAssembly.POMMEL, new ItemStack(SaberItems.POMMEL_BALANCED));
		helper.assertTrue(SaberAssembly.status(bench, builder) == Status.READY, msg("the bench is ready with a singing crystal"));
		ItemStack saber = SaberAssembly.assemble(bench, builder, "Builder", BladeColor.AMBER, b);
		SaberConfig config = SaberComponents.config(saber);
		helper.assertTrue(SaberComponents.isSinging(saber), msg("the saber carries the singing flag"));
		helper.assertTrue(config.crystal().owner().equals(builder) && config.crystal().ownerName().equals("Builder") && config.crystal().color() == BladeColor.AMBER.rgb()
				&& config.color() == BladeColor.AMBER, msg("the crystal attunes to its builder with its natural amber hue"));

		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = ItemStack.CODEC.encodeStart(ops, saber).getOrThrow();
		helper.assertTrue(SaberComponents.isSinging(ItemStack.CODEC.parse(ops, saved).getOrThrow()), msg("the singing flag is saved with the saber"));

		helper.assertTrue(SaberAssembly.disassemble(bench, builder) != null, msg("its builder takes it apart"));
		ItemStack back = bench.getItem(SaberAssembly.CRYSTAL);
		helper.assertTrue(back.getItem() == M3Items.SINGING_CRYSTAL && config.crystal().equals(back.get(ModComponents.CRYSTAL_ATTUNEMENT)),
				msg("disassembly gives the singing crystal back, attuned to its builder"));
		helper.assertTrue(SaberAssembly.mayPlace(SaberAssembly.CRYSTAL, back, builder) && !SaberAssembly.mayPlace(SaberAssembly.CRYSTAL, back, stranger),
				msg("once attuned it is its builder's alone"));
		ItemStack again = SaberAssembly.assemble(bench, builder, "Builder", BladeColor.BLUE, b);
		helper.assertTrue(SaberComponents.isSinging(again) && SaberComponents.config(again).color() == BladeColor.BLUE && SaberComponents.config(again).crystal().equals(config.crystal()),
				msg("rebuilt in another colour it still sings, with the same attunement"));

		ItemStack kyber = SaberItems.buildSaber(new CrystalAttunement(builder, "Builder", BladeColor.GREEN.rgb()), BladeColor.GREEN, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, b);
		ItemStack stray = new ItemStack(Items.STICK);
		stray.set(SaberComponents.SABER_SINGING, true);
		helper.assertTrue(!SaberComponents.isSinging(kyber) && !SaberComponents.isSinging(stray), msg("a kyber saber does not sing; the flag means nothing off a saber"));

		helper.assertTrue(ModSounds.SABER_HUM_SINGING.location().equals(OldRepublicMod.id("saber.hum.singing"))
				&& BuiltInRegistries.SOUND_EVENT.containsKey(ModSounds.SABER_HUM_SINGING.location()), msg("the hum layer saber.hum.singing is a registered sound event"));
		helper.succeed();
	}

	/**
	 * Spec 7.8 and 10.7: only the acolyte profile deflects. A guarding acolyte sends a bolt from a
	 * shooter within 70 degrees of its facing back at the shooter's chest within 15 degrees; farther
	 * off, while not guarding, or in rival mode the bolt goes through.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void acolyteDeflects(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		SaberBalance defaults = new SaberBalance();
		helper.assertTrue(near(defaults.npcDeflectConeDegrees, 70) && near(defaults.npcDeflectSpreadDegrees, 15), msg("spec 14: a 70 degree cone, 15 degrees of spread"));
		DuelistProfile profile = DuelistProfiles.get(DuelistProfiles.ACOLYTE);
		helper.assertTrue(profile.deflects() && profile.heavyDamage() == 0f && profile.parryMinStamina() == 0f, msg("the acolyte deflects; nothing else about it changes"));
		helper.assertTrue(!DuelistProfiles.get(DuelistProfiles.RIVAL).deflects() && !DuelistProfiles.get(DuelistProfiles.JEDI_MASTER).deflects()
				&& !DuelistProfiles.get(DuelistProfiles.TRAINING).deflects(), msg("no other named profile deflects"));

		ServerLevel level = helper.getLevel();
		SithAcolyte acolyte = acolyte(helper, new BlockPos(4, 1, 6));
		Mob front = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 2));
		// 63 degrees off the acolyte's facing: inside the cone (70 either side); 90 degrees off: outside it.
		Mob wide = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(8, 1, 4));
		Mob side = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(8, 1, 6));
		DuelState state = NpcGuard.state(acolyte);
		helper.assertTrue(state != null, msg("the acolyte's guard is registered"));
		acolyte.getRandom().setSeed(DEFLECT_SEED); // API-CHECK: RandomSource#setSeed(long) (not in the probe dumps)

		for (int i = 0; i < 8; i++) {
			state.refill();
			acolyte.setGuardState(GuardState.GUARDING);
			BlasterBolt bolt = boltFrom(level, front, acolyte, false);
			DeflectResult result = CombatHooks.onProjectileHit(acolyte, bolt);
			helper.assertTrue(result == DeflectResult.DEFLECTED, msg("a guarding acolyte deflects a bolt from in front, got " + result));
			helper.assertTrue(bolt.isDeflected() && !bolt.parried() && bolt.getOwner() == acolyte, msg("the bolt is now the acolyte's"));
			double off = degreesBetween(bolt.getDeltaMovement(), front.getBoundingBox().getCenter().subtract(bolt.position()));
			helper.assertTrue(off <= b.npcDeflectSpreadDegrees + 0.5, msg("sent back at the shooter's chest within " + b.npcDeflectSpreadDegrees + " degrees, got " + off));
		}

		state.refill();
		acolyte.setGuardState(GuardState.GUARDING);
		helper.assertTrue(CombatHooks.onProjectileHit(acolyte, boltFrom(level, wide, acolyte, false)) == DeflectResult.DEFLECTED,
				msg("a shooter 63 degrees off its facing is inside the cone"));
		state.refill();
		acolyte.setGuardState(GuardState.GUARDING);
		helper.assertTrue(CombatHooks.onProjectileHit(acolyte, boltFrom(level, side, acolyte, false)) == DeflectResult.NONE, msg("a shooter outside the cone is not answered"));
		acolyte.setGuardState(GuardState.READY);
		helper.assertTrue(CombatHooks.onProjectileHit(acolyte, boltFrom(level, front, acolyte, false)) == DeflectResult.NONE, msg("only while it guards"));
		acolyte.setGuardState(GuardState.GUARDING);
		acolyte.setMode(SithAcolyte.Mode.RIVAL);
		helper.assertTrue(CombatHooks.onProjectileHit(acolyte, boltFrom(level, front, acolyte, false)) == DeflectResult.NONE, msg("the rival profile does not deflect"));
		helper.succeed();
	}

	/**
	 * The M3 {@code DuelistProfile} keeps the M2 constructor (deflects false, heavyDamage 0,
	 * parryMinStamina 0) and the frozen component order; parryMinStamina above 0 replaces the balance
	 * threshold, and an NPC's heavy swing is unparryable and lands through a fresh guard.
	 */
	@GameTest(maxTicks = 20)
	public void duelistProfileCompatibilityConstructor(GameTestHelper helper) {
		DuelistProfile m2 = new DuelistProfile("m2_test", 5f, 80f, 0.3f, 2, 3, 12, 8, 14, true, 0.25f);
		helper.assertTrue(!m2.deflects() && m2.heavyDamage() == 0f && m2.parryMinStamina() == 0f && !m2.hasHeavySwing(),
				msg("the M2 field list: no deflection, no heavy swing, the balance parry threshold"));
		helper.assertTrue(m2.equals(new DuelistProfile("m2_test", 5f, 80f, 0.3f, 2, 3, 12, 8, 14, true, 0.25f, false, 0f, 0f)), msg("it equals the full form with false, 0, 0"));
		helper.assertTrue(m2.id().equals("m2_test") && m2.damage() == 5f && m2.guardStamina() == 80f && m2.parryChance() == 0.3f && m2.stringMin() == 2 && m2.stringMax() == 3
				&& m2.windupTicks() == 12 && m2.gapTicks() == 8 && m2.recoveryTicks() == 14 && m2.shove() && m2.yieldFraction() == 0.25f, msg("every M2 field keeps its value"));
		List<String> order = Arrays.stream(DuelistProfile.class.getRecordComponents()).map(RecordComponent::getName).toList();
		helper.assertTrue(order.equals(List.of("id", "damage", "guardStamina", "parryChance", "stringMin", "stringMax", "windupTicks", "gapTicks", "recoveryTicks",
				"shove", "yieldFraction", "deflects", "heavyDamage", "parryMinStamina")), msg("the frozen component order, got " + order));

		SaberBalance b = SaberToggle.balance();
		DuelistProfile strict = new DuelistProfile("strict_test", 6f, 140f, 0.3f, 2, 3, 10, 8, 16, false, 0f, true, 11f, 0.7f);
		helper.assertTrue(strict.hasHeavySwing() && near(NpcGuard.parryMinStamina(strict, b), 0.7f) && near(NpcGuard.parryMinStamina(m2, b), b.npcParryMinStamina),
				msg("parryMinStamina above 0 replaces saber.npcParryMinStamina"));
		for (String id : List.of(DuelistProfiles.RIVAL, DuelistProfiles.JEDI_MASTER, DuelistProfiles.TRAINING)) {
			DuelistProfile p = DuelistProfiles.get(id);
			helper.assertTrue(!p.deflects() && p.heavyDamage() == 0f && p.parryMinStamina() == 0f, msg(id + " keeps its M2 behaviour"));
		}

		SaberCombat.Strike heavy = SaberCombat.Strike.npcHeavy(b.heavyGuardDrainMult);
		helper.assertTrue(heavy.unparryable() && near(heavy.guardDrainMult(), b.heavyGuardDrainMult), msg("an NPC's heavy swing is unparryable and drains x" + b.heavyGuardDrainMult));
		ServerLevel level = helper.getLevel();
		SithAcolyte attacker = acolyte(helper, new BlockPos(4, 1, 6));
		GuardingPlayer struck = guardingPlayer(helper, 0, true);

		try {
			float before = SaberGuard.stamina(struck);
			float through = SaberCombat.modifyIncomingDamage(struck, level, level.damageSources().mobAttack(attacker), 4f, heavy);
			float drain = (b.meleeDrainBase + b.meleeDrainPerDamage * 4f) * b.heavyGuardDrainMult;
			helper.assertTrue(through > 0f && near(SaberGuard.stamina(struck), Math.max(0f, before - drain)),
					msg("inside the parry window the NPC heavy is not parried: it drains " + drain + " and lands, got " + through));
			helper.succeed();
		} finally {
			SaberGuard.forget(struck);
		}
	}

	/**
	 * Spec 10.4 rank nodes, through their rules with the node test passed in: {@code knights_vigil}
	 * (allies within 6 of a guarding Knight take 10% less; out of range, not guarding, without the node,
	 * hostile creatures, a PvP opponent and the Knight itself get nothing; Knights do not stack) and
	 * {@code lords_dominion} (after the Lord's heavy hit, hostile non-boss creatures within 8 deal 10%
	 * less for 100 ticks, through the registered damage modifier).
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void knightsVigilAndLordsDominion(GameTestHelper helper) {
		SaberBalance defaults = new SaberBalance();
		helper.assertTrue(near(defaults.knightsVigilRange, 6) && near(defaults.knightsVigilReduction, 0.1) && near(defaults.lordsDominionRange, 8)
				&& near(defaults.lordsDominionReduction, 0.1) && defaults.lordsDominionTicks == 100, msg("spec 14: vigil 6 / 10%, dominion 8 / 10% / 100 ticks"));
		SaberBalance b = SaberToggle.balance();
		ServerLevel level = helper.getLevel();
		FakePlayer knight = saberPlayer(helper, new BlockPos(4, 1, 4), "vigil_knight");
		FakePlayer second = saberPlayer(helper, new BlockPos(5, 1, 4), "vigil_second");
		FakePlayer ally = saberPlayer(helper, new BlockPos(4, 1, 8), "vigil_ally");
		FakePlayer distant = saberPlayer(helper, new BlockPos(4, 1, 11), "vigil_distant");
		Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 6));
		Predicate<Player> guarding = p -> !SaberGuard.guardingSaber(p).isEmpty();
		List<Player> knights = List.of(knight);
		float vigil = 1f - b.knightsVigilReduction;

		helper.assertTrue(near(SaberNodes.vigilMultiplier(ally, knights, p -> true, guarding, b), 1f), msg("no vigil while the Knight is not guarding"));
		knight.startUsingItem(InteractionHand.MAIN_HAND);
		helper.assertTrue(guarding.test(knight), msg("test setup: the Knight guards with a lit saber"));
		helper.assertTrue(near(SaberNodes.vigilMultiplier(ally, knights, p -> true, guarding, b), vigil), msg("an ally 4 blocks from the guarding Knight takes x" + vigil));
		helper.assertTrue(near(SaberNodes.vigilMultiplier(distant, knights, p -> true, guarding, b), 1f), msg("7 blocks away is out of range"));
		helper.assertTrue(near(SaberNodes.vigilMultiplier(ally, knights, p -> false, guarding, b), 1f), msg("nothing without the node"));
		helper.assertTrue(near(SaberNodes.vigilMultiplier(zombie, knights, p -> true, guarding, b), 1f), msg("a hostile creature is no ally"));
		helper.assertTrue(near(SaberNodes.vigilMultiplier(knight, knights, p -> true, guarding, b), 1f), msg("the Knight does not cover itself"));
		second.startUsingItem(InteractionHand.MAIN_HAND);
		helper.assertTrue(near(SaberNodes.vigilMultiplier(ally, List.of(knight, second), p -> true, guarding, b), vigil), msg("two Knights do not stack"));
		// A player fighting the Knight (one of them hurt the other) is no ally, wherever PvP lets the Knight harm them.
		second.setLastHurtByMob(knight);
		helper.assertTrue(!knight.canHarmPlayer(second) || near(SaberNodes.vigilMultiplier(second, knights, p -> true, guarding, b), 1f),
				msg("a PvP opponent of the guarding Knight is not covered"));
		knight.stopUsingItem();
		second.stopUsingItem();

		FakePlayer lord = saberPlayer(helper, new BlockPos(12, 1, 4), "dominion_lord");
		Mob close = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(12, 1, 9));
		Mob far = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(20, 1, 20));
		Mob villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(14, 1, 4));
		Mob victim = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(20, 1, 2));

		try {
			long now = level.getGameTime();
			helper.assertTrue(SaberNodes.applyDominion(lord, p -> false, b) == 0 && !SaberNodes.isDominated(close, now), msg("nothing without the node"));
			int count = SaberNodes.applyDominion(lord, p -> true, b);
			helper.assertTrue(count >= 1 && SaberNodes.isDominated(close, now), msg("a zombie 5 blocks from the Lord falls under the dominion"));
			helper.assertTrue(!SaberNodes.isDominated(far, now) && !SaberNodes.isDominated(villager, now), msg("never one out of range or one that is not hostile"));
			helper.assertTrue(near(SaberNodes.dominionMultiplier(close, now, b), 1f - b.lordsDominionReduction) && near(SaberNodes.dominionMultiplier(far, now, b), 1f),
					msg("a dominated creature deals x" + (1f - b.lordsDominionReduction)));
			float dominated = DamageModifiers.apply(victim, level.damageSources().mobAttack(close), 10f);
			float free = DamageModifiers.apply(victim, level.damageSources().mobAttack(far), 10f);
			helper.assertTrue(near(dominated, free * (1f - b.lordsDominionReduction)), msg("through the damage modifiers: " + dominated + " against " + free));
			helper.assertTrue(near(SaberNodes.dominionMultiplier(close, now + b.lordsDominionTicks - 1, b), 1f - b.lordsDominionReduction), msg("it lasts " + b.lordsDominionTicks + " ticks"));
			helper.assertTrue(near(SaberNodes.dominionMultiplier(close, now + b.lordsDominionTicks, b), 1f) && !SaberNodes.isDominated(close, now), msg("then it lifts"));
			helper.succeed();
		} finally {
			for (Mob mob : List.of(zombie, close, far, villager, victim)) {
				SaberNodes.clearDominion(mob);
			}
		}
	}
}
