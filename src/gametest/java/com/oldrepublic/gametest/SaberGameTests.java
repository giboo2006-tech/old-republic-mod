package com.oldrepublic.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.combat.CombatHooks.DeflectResult;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberAssembly.Status;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberStats;
import com.oldrepublic.saber.SaberToggle;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Saber rules: stat maths, the workbench's construct/disassemble round trip, and the guard's
 * handling of blaster bolts (with a fake server player holding an ignited saber).
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

		GuardingPlayer(ServerLevel level, int ticksUsing) {
			super(level, new GameProfile(UUID.randomUUID(), "guard_test"));
			this.ticksUsing = ticksUsing;
		}

		@Override
		public int getTicksUsingItem() {
			return isUsingItem() ? ticksUsing : 0;
		}
	}

	/** A player at the test's centre, facing +z, guarding with an ignited Guardian/Wrapped/Weighted saber. */
	private static GuardingPlayer guardingPlayer(GameTestHelper helper, int ticksUsing) {
		ServerLevel level = helper.getLevel();
		GuardingPlayer player = new GuardingPlayer(level, ticksUsing);
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
}
