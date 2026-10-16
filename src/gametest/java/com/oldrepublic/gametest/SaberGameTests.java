package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.combat.CombatHooks.DeflectResult;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.SithAcolyte;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
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
import com.oldrepublic.saber.SaberForm;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberStats;
import com.oldrepublic.saber.SaberTechniques;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.duel.DuelState;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.NpcGuard;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.duel.SparResult;
import com.oldrepublic.saber.duel.SparRules;
import com.oldrepublic.saber.throwing.SaberThrow;
import com.oldrepublic.saber.throwing.SaberThrowBalance;
import com.oldrepublic.saber.throwing.ThrownSaber;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Saber rules: stat maths, the workbench's construct/disassemble round trip, and the guard's
 * handling of blaster bolts (with a fake server player holding an ignited saber). Milestone 2: combo
 * timing, heavy attack drain and parry immunity, lunge validation, form modifiers, the NPC guard and
 * parry rules, Saber Throw never duplicating the saber, and spar safety.
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
		ServerLevel level = helper.getLevel();
		GuardingPlayer player = new GuardingPlayer(level, ticksUsing, vulnerable);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 4))));
		player.setYRot(0f);
		player.setXRot(0f);

		ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(player.getUUID(), "Tester", BladeColor.BLUE.rgb()), BladeColor.BLUE,
				Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
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

}
