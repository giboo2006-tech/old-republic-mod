package com.oldrepublic.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.PoacherCaptain;
import com.oldrepublic.enemy.PoacherLoot;
import com.oldrepublic.enemy.SithAcolyte;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.ForceTags;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.duel.DuelState;
import com.oldrepublic.saber.duel.NpcGuard;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 2 enemies: the Sith Acolyte's Force Shove is cancelled by a Push during its wind-up
 * (the push clash), the Poacher Captain yields at 25% of her health instead of dying, and a lost
 * poacher emitter can drop again.
 */
public class EnemyGameTests {
	private static FakePlayer player(GameTestHelper helper, BlockPos pos, String name) {
		ServerLevel level = helper.getLevel();
		FakePlayer player = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), name));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(pos)));
		player.setYRot(0f);
		return player;
	}

	@GameTest
	public void pushDuringShoveWindupClashes(GameTestHelper helper) {
		SithAcolyte acolyte = helper.spawn(EnemyEntities.SITH_ACOLYTE, new BlockPos(4, 1, 6));
		// No AI: the wind-up stays open until the push arrives.
		acolyte.setNoAi(true);
		FakePlayer caster = player(helper, new BlockPos(4, 1, 2), "shove_test");
		long now = helper.getLevel().getGameTime();

		helper.assertTrue(acolyte.is(ForceTags.PUSH_RESISTANT), Component.literal("the acolyte is push resistant"));
		helper.assertTrue(!acolyte.onPushed(), Component.literal("a push outside the wind-up is no clash"));
		helper.assertTrue(acolyte.startShove() && acolyte.isShoving(), Component.literal("the shove wind-up must start"));

		ForceReactions.onPushHit(caster, acolyte);
		DuelState state = NpcGuard.state(acolyte);
		helper.assertTrue(!acolyte.isShoving(), Component.literal("a Push during the wind-up cancels the shove"));
		helper.assertTrue(state != null && state.isStaggered(now), Component.literal("the clash staggers the acolyte"));
		helper.succeed();
	}

	@GameTest
	public void captainYieldsAtAQuarterHealth(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PoacherCaptain captain = helper.spawn(EnemyEntities.POACHER_CAPTAIN, new BlockPos(4, 1, 6));
		captain.setNoAi(true);
		FakePlayer attacker = player(helper, new BlockPos(4, 1, 4), "captain_test");
		float max = captain.getMaxHealth();
		float threshold = max * captain.yieldHealthFraction();

		helper.assertTrue(Math.abs(captain.yieldHealthFraction() - 0.25f) < 1.0e-4 && captain.canYield(),
				Component.literal("the Captain yields at 25%"));
		captain.hurtServer(level, level.damageSources().playerAttack(attacker), max * 0.5f);
		helper.assertTrue(!captain.isYielded() && captain.getHealth() < max && captain.getHealth() > threshold,
				Component.literal("a hit that leaves her above 25% is taken normally (armour may soften it)"));

		captain.hurtServer(level, level.damageSources().playerAttack(attacker), max);
		helper.assertTrue(captain.isAlive() && captain.isYielded(), Component.literal("a lethal hit makes her yield instead of dying"));
		helper.assertTrue(Math.abs(captain.getHealth() - threshold) < 1.0e-3,
				Component.literal("her health stops at 25% (" + threshold + "), got " + captain.getHealth()));
		helper.assertTrue(captain.getTarget() == null, Component.literal("a yielded Captain drops her target"));
		helper.succeed();
	}

	@GameTest
	public void lostEmitterCanDropAgain(GameTestHelper helper) {
		FakePlayer player = player(helper, new BlockPos(2, 1, 2), "emitter_test");
		ForceData data = Progression.get(player);
		data.flags.add(PoacherLoot.EMITTER_FLAG);
		player.getInventory().add(new ItemStack(SaberItems.EMITTER_DUELIST));
		helper.assertTrue(!PoacherLoot.reconsiderEmitterFlag(player, data) && data.hasFlag(PoacherLoot.EMITTER_FLAG),
				Component.literal("an emitter in the inventory keeps the flag"));

		player.getInventory().clearContent();
		helper.assertTrue(PoacherLoot.reconsiderEmitterFlag(player, data) && !data.hasFlag(PoacherLoot.EMITTER_FLAG),
				Component.literal("a lost emitter clears the flag so another can drop"));
		helper.succeed();
	}

}
