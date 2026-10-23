package com.oldrepublic.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.drops.BossDropRules;
import com.oldrepublic.boss.drops.BossDrops;
import com.oldrepublic.boss.drops.BossDropsBalance;
import com.oldrepublic.boss.drops.BossDropsData;
import com.oldrepublic.boss.drops.EliteDrops;
import com.oldrepublic.boss.drops.EliteDrops.Elite;
import com.oldrepublic.boss.raid.RaidRewards;
import com.oldrepublic.boss.ulvaine.UlvaineFight;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.quest.story.ChapterFiveRules;
import com.oldrepublic.quest.story.ChapterFiveService;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterFourService;
import com.oldrepublic.quest.story.ChapterThreeService;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.registry.M7Items;
import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.vendor.VendorStock;
import com.oldrepublic.vendor.VendorStocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Milestone 7's drops (spec MILESTONE_7.md 6.4, 7.3 to 7.5, 9.1, 14): each new boss drop once per guard, the pity
 * counters, the record kept on death and in the save, the reward hooks paying the drops, the elites' rolls, the chests'
 * steel parts, the echo cache and the vendors' new lines. Today's reward tests are untouched: the drops only add items.
 * Days and weeks are arguments; no clock is touched.
 */
public class BossDropsGameTests {
	/** Rolls per elite kind: enough that a rate sits well inside its window. */
	private static final int ELITE_ROLLS = 2000;
	/** Rolls per chest table. */
	private static final int CHEST_ROLLS = 400;
	/** How far an observed chance may sit from the table's (about four standard deviations at 400 rolls). */
	private static final double CHEST_SLACK = 0.1;
	/** Spreads the roll seeds: a loot table's first draw is nearly the same for consecutive seeds. */
	private static final long SEED_MIX = 0x9E3779B97F4A7C15L;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	// ---------------------------------------------------------------- the rules (7.3, 7.4)

	@GameTest(maxTicks = 20)
	public void pityMakesTheNthMissSure(GameTestHelper helper) {
		BossDropsBalance b = new BossDropsBalance();
		helper.assertTrue(b.collectorGripChance == 0.25f && b.collectorGripPity == 4 && b.watchmanPartChance == 0.25f && b.watchmanPartPity == 3
				&& b.penitentPartChance == 0.25f && b.penitentPartPity == 3 && b.choirPartChance == 0.30f && b.choirPartPity == 2 && b.choirIngots == 2,
				msg("the bosses' chances and pities are 7.3's"));
		helper.assertTrue(Arrays.equals(b.championClanIron, new int[] {1, 2}) && Arrays.equals(b.captainSunstone, new int[] {1, 3})
				&& b.captainKnurledChance == 0.15f && b.callerHymnstoneChance == 0.15f && b.callerDomedChance == 0.10f
				&& Arrays.equals(b.matriarchDuskglass, new int[] {2, 4}) && Arrays.equals(b.bullAurelite, new int[] {2, 4}) && b.trooperVanguardChance == 0.08f,
				msg("the elites' numbers are 7.4's"));

		helper.assertTrue(BossDropRules.due(0, 0.1f, 0.25f, 4) && !BossDropRules.due(0, 0.25f, 0.25f, 4) && !BossDropRules.due(2, 0.99f, 0.25f, 4)
				&& BossDropRules.due(3, 0.99f, 0.25f, 4), msg("a 25% roll, and surely the fourth clear in a row without one"));
		helper.assertTrue(!BossDropRules.due(0, 0.99f, 0.30f, 2) && BossDropRules.due(1, 0.99f, 0.30f, 2), msg("a pity of 2: the second week without one is sure"));
		helper.assertTrue(!BossDropRules.due(50, 0.99f, 0.25f, 0), msg("no pity never forces a drop"));

		List<HiltParts.Part> watchman = List.of(Grip.WATCHMAN, Pommel.WATCHMAN);
		helper.assertTrue(BossDropRules.lacking(Set.of(), watchman).equals(watchman)
				&& BossDropRules.lacking(Set.of(Grip.WATCHMAN.itemId()), watchman).equals(List.of(Pommel.WATCHMAN))
				&& BossDropRules.lacking(Set.of(Grip.WATCHMAN.itemId(), Pommel.WATCHMAN.itemId()), watchman).isEmpty(),
				msg("the part the player lacks is the one the drops have not given"));

		int[] range = {2, 4};
		helper.assertTrue(BossDropRules.count(0.0, range) == 2 && BossDropRules.count(0.5, range) == 3 && BossDropRules.count(0.99, range) == 4
				&& BossDropRules.count(1.0, range) == 4 && BossDropRules.count(0.7, new int[] {1, 1}) == 1, msg("a count range is uniform and inclusive"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void recordSurvivesSaveAndDeath(GameTestHelper helper) {
		BossDropsData data = BossDropsData.EMPTY.withMisses(BossDrops.WATCHMAN, 2).withGiven(Emitter.WATCHMAN).withGiven(Grip.CHOIR)
				.withRolled(BossDrops.Repeat.CHOIR_WEEK.id(), 12L).withRolled(BossDrops.Repeat.COLLECTOR_ECHO.id(), 40L);
		JsonElement json = BossDropsData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		BossDropsData loaded = BossDropsData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(loaded.equals(data) && loaded.misses(BossDrops.WATCHMAN) == 2 && loaded.gave(Emitter.WATCHMAN) && loaded.gave(Grip.CHOIR)
				&& !loaded.gave(Pommel.WATCHMAN) && loaded.rolledOn(BossDrops.Repeat.CHOIR_WEEK.id(), 12L)
				&& !loaded.rolledOn(BossDrops.Repeat.CHOIR_WEEK.id(), 13L), msg("the pity, the parts given and the last occasions ride in the save"));
		helper.assertTrue(BossDropsData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow().equals(BossDropsData.EMPTY),
				msg("a save without the record reads as nothing given"));
		helper.assertTrue(BossDropsData.BOSS_DROPS.copyOnDeath(), msg("the record is copied on death"));
		helper.assertTrue(BossDropsData.EMPTY.withMisses(BossDrops.CHOIR, -3).misses(BossDrops.CHOIR) == 0, msg("a pity counter is never negative"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- each drop once per guard (7.3)

	@GameTest(maxTicks = 20)
	public void setEmittersComeOnce(GameTestHelper helper) {
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			helper.assertTrue(BossDrops.watchmanEmitter(p) && parts(p, Emitter.WATCHMAN, HiltMaterial.AURELITE) == 1,
					msg("Kaast's first clear: the Watchman emitter, in aurelite"));
			helper.assertTrue(BossDrops.penitentEmitter(p) && parts(p, Emitter.PENITENT, HiltMaterial.CLAN_IRON) == 1,
					msg("Ulvaine's fight: the Penitent emitter, in clan iron"));
			helper.assertTrue(BossDrops.choirEmitter(p) && parts(p, Emitter.CHOIR, HiltMaterial.HYMNSTEEL) == 1,
					msg("the Long Hymn's first clear: the Choir emitter, in hymnsteel"));
			helper.assertTrue(!BossDrops.watchmanEmitter(p) && !BossDrops.penitentEmitter(p) && !BossDrops.choirEmitter(p)
					&& parts(p, Emitter.WATCHMAN, null) == 1 && parts(p, Emitter.PENITENT, null) == 1 && parts(p, Emitter.CHOIR, null) == 1,
					msg("each emitter comes once ever"));
			BossDropsData data = BossDropsData.get(p);
			helper.assertTrue(data.gave(Emitter.WATCHMAN) && data.gave(Emitter.PENITENT) && data.gave(Emitter.CHOIR) && data.pity().isEmpty(),
					msg("each is recorded as given, and no pity counts for them"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void collectorGripRollsOncePerDayWithItsPity(GameTestHelper helper) {
		BossDropsBalance b = BossDropsBalance.get();
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			int run = 0;
			int longest = 0;
			int grips = 0;

			for (long day = 1; day <= 40; day++) {
				int before = parts(p, Grip.RELIQUARY, null);
				int missesBefore = BossDropsData.get(p).misses(BossDrops.COLLECTOR);
				helper.assertTrue(BossDrops.collectorEcho(p, day), msg("day " + day + ": the first rewarded echo rolls"));
				boolean dropped = parts(p, Grip.RELIQUARY, null) == before + 1;
				int misses = BossDropsData.get(p).misses(BossDrops.COLLECTOR);
				helper.assertTrue(dropped ? misses == 0 : misses == missesBefore + 1 && parts(p, Grip.RELIQUARY, null) == before,
						msg("day " + day + ": a grip starts the pity over, a miss counts one (" + missesBefore + " -> " + misses + ")"));
				helper.assertFalse(BossDrops.collectorEcho(p, day), msg("day " + day + ": a second echo the same day rolls nothing"));
				helper.assertTrue(parts(p, Grip.RELIQUARY, null) == before + (dropped ? 1 : 0) && BossDropsData.get(p).misses(BossDrops.COLLECTOR) == misses,
						msg("day " + day + ": and changes nothing"));
				run = dropped ? 0 : run + 1;
				longest = Math.max(longest, run);
				grips += dropped ? 1 : 0;
			}

			helper.assertTrue(longest < b.collectorGripPity, msg("never " + b.collectorGripPity + " echoes in a row without the grip (longest " + longest + ")"));
			helper.assertTrue(grips >= 40 / Math.max(1, b.collectorGripPity) && parts(p, Grip.RELIQUARY, HiltMaterial.STARFALL) == grips,
					msg("the grips come in starfall iron, again and again (" + grips + " in 40 days)"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void lackedPartsComeThenStop(GameTestHelper helper) {
		BossDropsBalance b = BossDropsBalance.get();
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			// Kaast's rematch: made sure by the pity, the two parts the player lacks, then nothing.
			force(p, BossDrops.WATCHMAN, b.watchmanPartPity);
			helper.assertTrue(BossDrops.watchmanRematch(p, 1) && BossDropsData.get(p).misses(BossDrops.WATCHMAN) == 0,
					msg("a rematch after " + (b.watchmanPartPity - 1) + " without a part is sure, and the pity starts over"));
			force(p, BossDrops.WATCHMAN, b.watchmanPartPity);
			helper.assertTrue(BossDrops.watchmanRematch(p, 2) && parts(p, Grip.WATCHMAN, HiltMaterial.AURELITE) == 1
					&& parts(p, Pommel.WATCHMAN, HiltMaterial.AURELITE) == 1, msg("a grip and a pommel, in aurelite: never one the player has"));
			force(p, BossDrops.WATCHMAN, b.watchmanPartPity);
			helper.assertTrue(!BossDrops.watchmanRematch(p, 3) && !BossDropsData.get(p).rolledOn(BossDrops.Repeat.WATCHMAN_REMATCH.id(), 3)
					&& parts(p, Grip.WATCHMAN, null) == 1 && parts(p, Pommel.WATCHMAN, null) == 1, msg("with both, the rematch has nothing left to roll"));

			// Ulvaine: her echo and a helper's roll keep a day each and share one pity.
			force(p, BossDrops.PENITENT, b.penitentPartPity);
			helper.assertTrue(BossDrops.penitentEcho(p, 5) && BossDropsData.get(p).misses(BossDrops.PENITENT) == 0
					&& parts(p, Grip.PENITENT, HiltMaterial.CLAN_IRON) + parts(p, Pommel.PENITENT, HiltMaterial.CLAN_IRON) == 1,
					msg("her echo's sure roll gives a grip or a pommel, in clan iron"));
			helper.assertFalse(BossDrops.penitentEcho(p, 5), msg("her echo rolls once a day"));
			force(p, BossDrops.PENITENT, b.penitentPartPity);
			helper.assertTrue(BossDrops.penitentHelper(p, 5) && parts(p, Grip.PENITENT, HiltMaterial.CLAN_IRON) == 1
					&& parts(p, Pommel.PENITENT, HiltMaterial.CLAN_IRON) == 1, msg("a helper's roll the same day is its own, and gives the other part"));
			helper.assertTrue(!BossDrops.penitentHelper(p, 5) && !BossDrops.penitentEcho(p, 6), msg("then once a day each, and nothing once both are given"));

			// The Long Hymn: a grip and a pommel the player lacks, then hymnsteel ingots instead.
			force(p, BossDrops.CHOIR, b.choirPartPity);
			helper.assertTrue(BossDrops.choirWeek(p, 12) && parts(p, Grip.CHOIR, HiltMaterial.HYMNSTEEL) + parts(p, Pommel.CHOIR, HiltMaterial.HYMNSTEEL) == 1,
					msg("the week's sure roll gives a Choir grip or pommel, in hymnsteel"));
			force(p, BossDrops.CHOIR, b.choirPartPity);
			helper.assertTrue(BossDrops.choirWeek(p, 13) && parts(p, Grip.CHOIR, HiltMaterial.HYMNSTEEL) == 1
					&& parts(p, Pommel.CHOIR, HiltMaterial.HYMNSTEEL) == 1, msg("then the other"));
			int ingots = count(p, M7Items.HYMNSTEEL_INGOT);
			force(p, BossDrops.CHOIR, b.choirPartPity);
			helper.assertTrue(BossDrops.choirWeek(p, 14) && count(p, M7Items.HYMNSTEEL_INGOT) == ingots + b.choirIngots
					&& parts(p, Grip.CHOIR, null) == 1 && parts(p, Pommel.CHOIR, null) == 1, msg("with the set, " + b.choirIngots + " hymnsteel ingots instead"));
			helper.assertFalse(BossDrops.choirWeek(p, 14), msg("once a week"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the reward hooks pay them (7.3)

	@GameTest(maxTicks = 40)
	public void rewardHooksPayTheDrops(GameTestHelper helper) {
		ServerPlayer owner = M3TestPlayers.listed(helper, new BlockPos(1, 1, 1));
		ServerPlayer friend = M3TestPlayers.listed(helper, new BlockPos(3, 1, 1));
		ServerPlayer veteran = M3TestPlayers.listed(helper, new BlockPos(5, 1, 1));

		try {
			// The Collector: a rewarded echo (CollectorRewards.echo).
			ChapterThreeService.onBossDefeated(owner, "collector", BossOutcome.ECHO, owner.getUUID(), false);
			helper.assertTrue(rolled(owner, BossDrops.Repeat.COLLECTOR_ECHO), msg("a rewarded Collector echo rolls for the Reliquary grip"));

			// Kaast: the first clear, whatever the choice, then the owner's daily rematch.
			ChapterFourService.onBossDefeated(owner, "kaast", BossOutcome.SPARED, owner.getUUID(), true);
			helper.assertTrue(Story.get(owner).hasFlag(ChapterFourRules.KAAST_PAID) && parts(owner, Emitter.WATCHMAN, HiltMaterial.AURELITE) == 1,
					msg("Kaast's first clear gives the Watchman emitter"));
			ChapterFourService.onBossDefeated(owner, "kaast", BossOutcome.ECHO, owner.getUUID(), false);
			helper.assertTrue(rolled(owner, BossDrops.Repeat.WATCHMAN_REMATCH) && parts(owner, Emitter.WATCHMAN, null) == 1,
					msg("the rematch rolls for the grip or pommel, and gives no second emitter"));

			// Ulvaine: the fight's pay, a helper's day, and her echo.
			ChapterFiveService.onBossDefeated(owner, "ulvaine", BossOutcome.SPARED, owner.getUUID(), true);
			helper.assertTrue(Story.get(owner).hasFlag(ChapterFiveRules.ULVAINE_PAID) && parts(owner, Emitter.PENITENT, HiltMaterial.CLAN_IRON) == 1,
					msg("Ulvaine's fight pay gives the Penitent emitter"));
			ChapterFiveService.onBossDefeated(friend, "ulvaine", BossOutcome.SPARED, owner.getUUID(), false);
			helper.assertTrue(rolled(friend, BossDrops.Repeat.PENITENT_HELPER) && parts(friend, Emitter.PENITENT, null) == 0,
					msg("a helper's day rolls for the grip or pommel, never the emitter"));
			UlvaineFight.payEcho(friend, 100);
			helper.assertTrue(BossDropsData.get(friend).rolledOn(BossDrops.Repeat.PENITENT_ECHO.id(), 100) && parts(friend, Emitter.PENITENT, null) == 0,
					msg("her echo rolls for its day; without a paid fight no emitter"));

			// A fight paid before the drops existed: her echo gives the emitter it missed.
			StoryData data = Story.get(veteran);
			data.flags.add(ChapterFiveRules.ULVAINE_PAID);
			Story.commit(veteran, data);
			UlvaineFight.payEcho(veteran, 100);
			helper.assertTrue(parts(veteran, Emitter.PENITENT, HiltMaterial.CLAN_IRON) == 1 && BossDropsData.get(veteran).rolledOn(BossDrops.Repeat.PENITENT_ECHO.id(), 100),
					msg("an older save's paid fight gets its emitter at the next echo"));

			// The Long Hymn: the week's rewarded clear; a second clear that week is a helper's and drops nothing.
			RaidRewards.pay(friend, 12);
			helper.assertTrue(parts(friend, Emitter.CHOIR, HiltMaterial.HYMNSTEEL) == 1 && BossDropsData.get(friend).rolledOn(BossDrops.Repeat.CHOIR_WEEK.id(), 12),
					msg("the first clear gives the Choir emitter and rolls the week"));
			BossDropsData before = BossDropsData.get(friend);
			RaidRewards.pay(friend, 12);
			helper.assertTrue(BossDropsData.get(friend).equals(before) && parts(friend, Emitter.CHOIR, null) == 1, msg("a second clear the same week drops nothing more"));
		} finally {
			M3TestPlayers.remove(owner);
			M3TestPlayers.remove(friend);
			M3TestPlayers.remove(veteran);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the elites (7.4)

	@GameTest(maxTicks = 20)
	public void elitesDropTheirMaterials(GameTestHelper helper) {
		BossDropsBalance b = new BossDropsBalance();

		Map<Item, int[]> champion = sample(Elite.HASK_CHAMPION, b);
		helper.assertTrue(champion.size() == 1 && always(champion.get(M5Items.CLAN_IRON), 1, 2), msg("Clan Hask's champion: 1 to 2 clan iron, every kill"));

		Map<Item, int[]> captain = sample(Elite.CARTEL_CAPTAIN, b);
		helper.assertTrue(captain.size() == 2 && always(captain.get(M5Items.SUNSTONE), 1, 3) && near(captain.get(SaberItems.GRIP_KNURLED), b.captainKnurledChance),
				msg("the cartel captain: 1 to 3 sunstone, the Knurled grip " + rate(captain.get(SaberItems.GRIP_KNURLED))));

		Map<Item, int[]> caller = sample(Elite.HYMNSWORN_CALLER, b);
		helper.assertTrue(caller.size() == 2 && near(caller.get(M5Items.HYMNSTONE_SHARD), b.callerHymnstoneChance)
				&& near(caller.get(SaberItems.POMMEL_DOMED), b.callerDomedChance), msg("the Hymnsworn caller: a hymnstone shard "
						+ rate(caller.get(M5Items.HYMNSTONE_SHARD)) + ", the Domed pommel " + rate(caller.get(SaberItems.POMMEL_DOMED))));

		Map<Item, int[]> trooper = sample(Elite.TROOPER_CAPTAIN, b);
		helper.assertTrue(trooper.size() == 1 && near(trooper.get(SaberItems.EMITTER_VANGUARD), b.trooperVanguardChance),
				msg("a trooper captain: the Vanguard emitter " + rate(trooper.get(SaberItems.EMITTER_VANGUARD))));

		Map<Item, int[]> matriarch = sample(Elite.SCOUR_MATRIARCH, b);
		Map<Item, int[]> bull = sample(Elite.HOLLOWHORN_BULL, b);
		helper.assertTrue(matriarch.size() == 1 && always(matriarch.get(M4Items.DUSKGLASS_SHARD), 2, 4) && bull.size() == 1
				&& always(bull.get(M4Items.AURELITE_SHARD), 2, 4), msg("the matriarch and the bull: 2 to 4 duskglass and aurelite"));

		for (Map<Item, int[]> drops : List.of(captain, caller, trooper)) {
			for (int[] seen : drops.values()) {
				helper.assertTrue(seen[3] == 0, msg("the elites' parts are plain steel"));
			}
		}

		helper.assertTrue(Elite.SCOUR_MATRIARCH.beast() && Elite.HOLLOWHORN_BULL.beast() && !Elite.HASK_CHAMPION.beast() && !Elite.TROOPER_CAPTAIN.beast(),
				msg("only the beasts drop without a hostile fight"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the chests, the captain and the vendors (6.4, 9.1)

	@GameTest(maxTicks = 20)
	public void chestsHoldTheDesigns(GameTestHelper helper) {
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			chance(helper, p, "chests/jedi_ruin", SaberItems.EMITTER_WARDEN, 0.35);
			chance(helper, p, "chests/wilds/watch_hermitage", SaberItems.EMITTER_WARDEN, 0.35);
			chance(helper, p, "chests/wilds/smuggler_cove", SaberItems.GRIP_KNURLED, 0.35);
			chance(helper, p, "chests/sith_tomb", SaberItems.GRIP_FLUTED, 0.30);
			chance(helper, p, "chests/greater_tomb", SaberItems.GRIP_FLUTED, 0.30);
			chance(helper, p, "chests/wilds/poacher_dig", SaberItems.POMMEL_RINGED, 0.30);
			chance(helper, p, "chests/crystal_cave_hidden", SaberItems.POMMEL_DOMED, 0.35);
			// The echo cache: its emitter as today, and the new starfall and durasteel.
			chance(helper, p, "gameplay/echo_cache", SaberItems.EMITTER_RELIQUARY, 0.25);
			chance(helper, p, "gameplay/echo_cache", M7Items.STARFALL_INGOT, 0.40);
			chance(helper, p, "gameplay/echo_cache", M3Items.DURASTEEL_PLATE, 0.40);

			ResourceKey<LootTable> cache = table("gameplay/echo_cache");
			int least = Integer.MAX_VALUE;
			int most = 0;

			for (int i = 0; i < CHEST_ROLLS; i++) {
				for (ItemStack stack : Rewards.roll(p, cache, (51_000L + i) * SEED_MIX)) {
					if (stack.is(M7Items.STARFALL_INGOT)) {
						least = Math.min(least, stack.getCount());
						most = Math.max(most, stack.getCount());
					} else if (stack.is(M3Items.DURASTEEL_PLATE)) {
						helper.assertTrue(stack.getCount() == 1, msg("the echo cache's durasteel is one plate"));
					}
				}
			}

			helper.assertTrue(least == 1 && most == 2, msg("the echo cache's starfall is 1 to 2 ingots, got " + least + " to " + most));
		} finally {
			M3TestPlayers.remove(p);
		}

		JsonObject captain = resource("/data/oldrepublic/loot_table/entities/poacher_captain.json");
		helper.assertTrue(captain != null && playerKillPool(captain, "oldrepublic:pommel_ringed", 0.1),
				msg("the poacher captain drops the Ringed pommel 10% on a player's kill"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void vendorsSellTheNewLines(GameTestHelper helper) {
		int trusted = ReputationTier.TRUSTED.ordinal();

		for (String id : List.of("requisition_republic", "requisition_imperial")) {
			VendorStock stock = VendorStocks.get(id);
			helper.assertTrue(sells(stock, SaberItems.EMITTER_VANGUARD, 260, 0) && sells(stock, SaberItems.GRIP_BANDED, 180, 0),
					msg(id + " sells the Vanguard emitter (260) and the Banded grip (180)"));
			helper.assertTrue(sells(stock, M7Items.BLASTER_CARBINE, 420, trusted), msg(id + " sells the blaster carbine at Trusted (420)"));
		}

		helper.assertTrue(sells(VendorStocks.get("hask_armourer"), M7Items.VIBRO_AXE, 700, 0), msg("the Hask armourer sells the vibro-axe (700)"));
		VendorStock fence = VendorStocks.get("cartel_fence");
		helper.assertTrue(sells(fence, M7Items.FORCE_PIKE, 900, 0) && sells(fence, M7Items.SCATTER_BLASTER, 650, 0),
				msg("the cartel fence sells the force pike (900) and the scatter blaster (650)"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- helpers

	/** Makes {@code counter}'s next occasion sure: the pity's last miss before it. */
	private static void force(ServerPlayer p, String counter, int pity) {
		BossDropsData.set(p, BossDropsData.get(p).withMisses(counter, pity - 1));
	}

	/** Whether the drop has rolled on some occasion. */
	private static boolean rolled(ServerPlayer p, BossDrops.Repeat drop) {
		return BossDropsData.get(p).rolled().containsKey(drop.id());
	}

	/** How many of {@code part} the player carries, of {@code material} (any, when null). */
	private static int parts(ServerPlayer p, HiltParts.Part part, @Nullable HiltMaterial material) {
		int n = 0;

		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (stack.is(SaberItems.partItem(part)) && (material == null || SaberComponents.material(stack) == material)) {
				n += stack.getCount();
			}
		}

		return n;
	}

	private static int count(ServerPlayer p, Item item) {
		int n = 0;

		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}

		return n;
	}

	/**
	 * {@link #ELITE_ROLLS} of {@code elite}'s drops, by item: {rolls holding it, least, most, stacks with a material
	 * component}.
	 */
	private static Map<Item, int[]> sample(Elite elite, BossDropsBalance b) {
		RandomSource random = RandomSource.create(73_421L + elite.ordinal());
		Map<Item, int[]> seen = new HashMap<>();

		for (int i = 0; i < ELITE_ROLLS; i++) {
			for (ItemStack stack : EliteDrops.roll(elite, random, b)) {
				int[] s = seen.computeIfAbsent(stack.getItem(), item -> new int[] {0, Integer.MAX_VALUE, 0, 0});
				s[0]++;
				s[1] = Math.min(s[1], stack.getCount());
				s[2] = Math.max(s[2], stack.getCount());
				s[3] += stack.has(SaberComponents.HILT_MATERIAL) ? 1 : 0;
			}
		}

		return seen;
	}

	/** Whether an item came on every roll, from {@code min} to {@code max} of it, both seen. */
	private static boolean always(int @Nullable [] seen, int min, int max) {
		return seen != null && seen[0] == ELITE_ROLLS && seen[1] == min && seen[2] == max;
	}

	/** Whether an item came on about {@code chance} of the rolls (within a third of it). */
	private static boolean near(int @Nullable [] seen, float chance) {
		return seen != null && Math.abs(seen[0] / (double) ELITE_ROLLS - chance) < chance / 3.0;
	}

	private static String rate(int @Nullable [] seen) {
		return seen == null ? "never" : String.format("%.3f", seen[0] / (double) ELITE_ROLLS);
	}

	private static ResourceKey<LootTable> table(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, OldRepublicMod.id(path));
	}

	/** Rolls {@code path} {@link #CHEST_ROLLS} times: {@code item} comes in about {@code chance} of them, a plain steel part. */
	private static void chance(GameTestHelper helper, ServerPlayer p, String path, Item item, double chance) {
		ResourceKey<LootTable> key = table(path);
		int hits = 0;
		boolean plain = true;

		for (int i = 0; i < CHEST_ROLLS; i++) {
			boolean hit = false;

			for (ItemStack stack : Rewards.roll(p, key, (91_000L + i) * SEED_MIX)) {
				if (stack.is(item)) {
					hit = true;
					plain &= !stack.has(SaberComponents.HILT_MATERIAL);
				}
			}

			hits += hit ? 1 : 0;
		}

		double rate = hits / (double) CHEST_ROLLS;
		helper.assertTrue(Math.abs(rate - chance) < CHEST_SLACK && plain,
				msg(path + " holds " + BuiltInRegistries.ITEM.getKey(item) + " about " + chance + " of the time, got " + rate));
	}

	/** Whether a pool of {@code table} drops {@code item} on a player's kill at {@code chance}. */
	private static boolean playerKillPool(JsonObject table, String item, double chance) {
		for (JsonElement element : table.getAsJsonArray("pools")) {
			JsonObject pool = element.getAsJsonObject();
			boolean byPlayer = false;
			double rolled = -1;

			if (pool.has("conditions")) {
				for (JsonElement c : pool.getAsJsonArray("conditions")) {
					String kind = c.getAsJsonObject().get("condition").getAsString();
					byPlayer |= kind.equals("minecraft:killed_by_player");
					rolled = kind.equals("minecraft:random_chance") ? c.getAsJsonObject().get("chance").getAsDouble() : rolled;
				}
			}

			for (JsonElement e : pool.getAsJsonArray("entries")) {
				JsonObject entry = e.getAsJsonObject();

				if (entry.has("name") && entry.get("name").getAsString().equals(item) && byPlayer && Math.abs(rolled - chance) < 1.0e-6) {
					return true;
				}
			}
		}

		return false;
	}

	/** A JSON file of the mod's resources, or null when it cannot be read. */
	private static @Nullable JsonObject resource(String path) {
		try (InputStream in = BossDropsGameTests.class.getResourceAsStream(path)) {
			return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** Whether {@code stock} sells {@code item} at {@code price} from reputation tier ordinal {@code tier} (0: ungated). */
	private static boolean sells(@Nullable VendorStock stock, Item item, int price, int tier) {
		return stock != null && stock.entries().stream().anyMatch(e -> e.item().is(item) && e.price() == price && e.tier() == tier);
	}
}
