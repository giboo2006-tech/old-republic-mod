package com.oldrepublic.gametest;

import com.mojang.serialization.JsonOps;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public class ProgressionGameTests {
	@GameTest
	public void xpCurveIsIncreasing(GameTestHelper helper) {
		long previous = 0;

		for (int level = 1; level < 100; level++) {
			long next = Progression.xpToNext(level);

			if (next <= previous) {
				helper.fail(Component.literal("xp_to_next must increase, but level " + level + " needs " + next + " after " + previous));
			}

			previous = next;
		}

		if (Progression.xpToNext(100) != 0) {
			helper.fail(Component.literal("Level 100 is the cap and should need no more xp"));
		}

		helper.succeed();
	}

	@GameTest
	public void forceDataRoundTrips(GameTestHelper helper) {
		ForceData data = new ForceData();
		data.level = 7;
		data.xp = 123;
		data.resonance = 3100;
		data.skillPoints = 2;
		data.energy = 41.5f;
		data.abilities.add("force_push");
		data.pushBranch = "focused_blast";
		data.mastery.put("force_push", 77);
		data.questStage = 3;
		data.flags.add("resonance:awakening");
		data.counters.put("poacher_kills", 4);
		data.cooldowns.put("force_push", 30);

		var json = ForceData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		ForceData back = ForceData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

		helper.assertTrue(back.level == 7 && back.xp == 123 && back.resonance == 3100 && back.skillPoints == 2, Component.literal("stats lost: " + json));
		helper.assertTrue(Math.abs(back.energy - 41.5f) < 1e-4, Component.literal("energy lost"));
		helper.assertTrue(back.hasAbility("force_push") && "focused_blast".equals(back.pushBranch), Component.literal("abilities lost"));
		helper.assertTrue(back.mastery("force_push") == 77 && back.counter("poacher_kills") == 4 && back.cooldown("force_push") == 30, Component.literal("maps lost"));
		helper.assertTrue(back.questStage == 3 && back.hasFlag("resonance:awakening"), Component.literal("quest lost"));
		helper.succeed();
	}
}
