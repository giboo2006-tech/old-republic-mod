package com.oldrepublic.gametest;

import java.util.UUID;

import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberAssembly.Status;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberStats;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Saber rules that need no live player: stat maths and the workbench's construct/disassemble round trip. */
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
}
