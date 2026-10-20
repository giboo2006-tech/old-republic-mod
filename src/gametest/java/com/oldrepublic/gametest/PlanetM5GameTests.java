package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntPredicate;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.force.ForceAct;
import com.oldrepublic.force.ForceBlockContext;
import com.oldrepublic.force.ForceBlockInteractions;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.planet.Hymnal;
import com.oldrepublic.planet.HymnalDoors;
import com.oldrepublic.planet.PlanetBalance;
import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.planet.PlanetM5Feature;
import com.oldrepublic.planet.PlanetSiteData;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.PlanetWeather;
import com.oldrepublic.planet.PlanetWeatherPayload;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.planet.RidgeLightning;
import com.oldrepublic.planet.VerseStones;
import com.oldrepublic.planet.block.HoldfastGateBlock;
import com.oldrepublic.planet.block.VerseStoneBlock;
import com.oldrepublic.planet.block.VerseStoneBlockEntity;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.StructureLocator;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 planet tests (spec MILESTONE_5.md 19.1, {@code PlanetM5GameTests}, W3): Ghessar's and
 * Tyrvann's weather rules and the sandstorm cycle, ridge lightning and stormglass, the verse stones'
 * channel, Force cut, party rule, slowing hum and silence hook, the Hymnal's once-per-server
 * placement, depth and door rules, the holdfast gate, peat, the M5 templates' sidecars and the new
 * structures' tags.
 *
 * <p>Ghessar and Tyrvann do not exist on the game-test server, so every test uses pure rules,
 * detached data or a fixture in the overworld arena. Each test declares {@code maxTicks} (at most
 * 400); randomness is seeded; listed players and spawned entities are removed when the test ends,
 * pass or fail; no test writes {@code WorldSites} or {@code PlanetSiteData}, and none places an M5
 * template.
 */
public class PlanetM5GameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";

	/** The M5 templates W3 owns, with their sizes and the anchors each must name. */
	private record M5Template(String path, Vec3i size, List<String> anchors) {
	}

	private static final List<String> PAD_ANCHORS = List.of("HEART", "PAD_BAY_1", "PAD_BAY_2", "PAD_BAY_3", "PAD_BAY_4", "PAD_TERMINAL",
			"CONTRACT_TERMINAL", "MEDBAY");

	private static final List<M5Template> TEMPLATES = List.of(
			new M5Template("hub/saltgate", new Vec3i(63, 24, 63), concat(PAD_ANCHORS, List.of("HOST", "CANTINA", "VENDOR_FENCE", "VENDOR_SHIPWRIGHT",
					"VENDOR_SPACER", "NPC_VARRA", "NPC_DASK", "NPC_VESSBA", "NPC_TEEL", "COUNTING_HOUSE_DOOR", "COUNTING_HOUSE_1", "COUNTING_HOUSE_8",
					"ENFORCER_1", "ENFORCER_6"))),
			new M5Template("hub/holdfast", new Vec3i(57, 26, 57), concat(PAD_ANCHORS, List.of("NPC_DORRUN", "NPC_VARRA", "VENDOR_ARMOURER",
					"VENDOR_SPACER", "WARRIOR_1", "WARRIOR_6", "HOLDFAST_GATE"))),
			new M5Template("site/verse_stone", new Vec3i(15, 10, 15), List.of("STONE", "HEART", "SPAWNER")),
			new M5Template("site/hymnal_arch", new Vec3i(15, 31, 15), List.of("STAIR_MOUTH")),
			new M5Template("site/hymnal", new Vec3i(37, 28, 79), List.of("HEART", "THRESHOLD", "LECTERN", "DAIS", "GUARD", "SQUAD_1", "SQUAD_3",
					"SENTINEL_1", "SENTINEL_2", "PYLON_1", "PYLON_2", "ROD_1", "ROD_4", "RESONATOR_1", "RESONATOR_4", "HEART_DOOR", "UNDERCROFT_DOOR",
					"CHOIR_1", "CHOIR_4", "SILENCE_1", "SILENCE_3", "RING_1", "RING_3", "KEYSTONE", "CANTOR", "UNDERCROFT_MIN", "UNDERCROFT_MAX")),
			new M5Template("site/debtor_camp", new Vec3i(23, 10, 23), List.of("HEART", "NPC_DEBTOR", "SPAWNER", "STRONGBOX")),
			new M5Template("site/detention_post", new Vec3i(31, 16, 31), List.of("HEART", "WARDEN", "GUARD_1", "GUARD_4", "PRISONER_1", "PRISONER_3")),
			new M5Template("planet/crashed_freighter", new Vec3i(33, 14, 17), List.of("HEART", "CHEST_1", "CRATE", "CREW_1", "CREW_4", "SURFACE_GUARD")),
			new M5Template("planet/kajidic_post", new Vec3i(21, 14, 21), List.of("HEART", "CHEST_1", "SQUAD", "SURFACE_GUARD")),
			new M5Template("planet/hymn_shrine", new Vec3i(17, 10, 17), List.of("HEART", "CHEST_1", "SQUAD", "SURFACE_GUARD")),
			new M5Template("planet/mire_grave", new Vec3i(13, 9, 13), List.of("HEART", "CHEST_1", "SURFACE_GUARD")));

	/** The four M5 worldgen structures and the tags their sites are found by. */
	private static final Map<String, TagKey<Structure>> STRUCTURE_TAGS = Map.of("crashed_freighter", StructureLocator.CRASHED_FREIGHTERS, "kajidic_post",
			StructureLocator.KAJIDIC_POSTS, "hymn_shrine", StructureLocator.HYMN_SHRINES, "mire_grave", StructureLocator.MIRE_GRAVES);

	// ---------------------------------------------------------------- weather

	/** Ghessar's rain and thunder are held at 0; the shared weather still rules the overworld and the M4 planets (3.5). */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void ghessarHoldsRainAtZero(GameTestHelper helper) {
		Float ghessar = PlanetWeather.heldLevel(Planets.GHESSAR);
		helper.assertTrue(ghessar != null && ghessar == 0f, msg("Ghessar's rain and thunder are held at 0, got " + ghessar));

		for (var dimension : List.of(Level.OVERWORLD, Planets.SELVAREN, Planets.VARKHAST)) {
			helper.assertTrue(PlanetWeather.heldLevel(dimension) == null, msg(dimension.identifier() + " follows the shared weather"));
		}

		helper.assertTrue(PlanetWeather.kind(helper.getLevel()) == PlanetWeather.Kind.CLEAR, msg("no sandstorm blows outside Ghessar"));
		helper.succeed();
	}

	/** Tyrvann's rain and thunder are held at 1 and its lightning never spawns a skeleton-horse trap (3.5). */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void tyrvannStormAlwaysOn(GameTestHelper helper) {
		Float tyrvann = PlanetWeather.heldLevel(Planets.TYRVANN);
		helper.assertTrue(tyrvann != null && tyrvann == 1f, msg("Tyrvann's rain and thunder are held at 1, got " + tyrvann));
		helper.assertTrue(PlanetWeather.noHorseTraps(Planets.TYRVANN), msg("no skeleton-horse traps on Tyrvann"));
		helper.assertFalse(PlanetWeather.noHorseTraps(Level.OVERWORLD) || PlanetWeather.noHorseTraps(Planets.GHESSAR),
				msg("the traps keep their vanilla rule elsewhere"));
		helper.succeed();
	}

	/**
	 * The sandstorm cycle is seeded by the world seed: a clear spell of {@code sandstormIntervalDays},
	 * then a storm of {@code sandstormTicks}, the same every time for the same seed and tick; it steps
	 * through spells it fell behind on, and with {@code sandstorms} off a storm ends now and no new
	 * one comes.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void sandstormCycleSeeded(GameTestHelper helper) {
		PlanetBalance balance = new PlanetBalance();
		long seed = 0x5A1F_6A7EL;
		long now = 1000L;
		PlanetWeather.State first = PlanetWeather.first(seed, now, balance);
		helper.assertTrue(first.kind() == PlanetWeather.Kind.CLEAR, msg("the first spell is clear"));
		long clear = first.untilTick() - now;
		helper.assertTrue(clear >= 3L * PlanetWeather.DAY_TICKS && clear <= 5L * PlanetWeather.DAY_TICKS, msg("a clear spell of 3 to 5 days, got " + clear));
		helper.assertTrue(first.equals(PlanetWeather.first(seed, now, balance)), msg("the same seed and tick give the same spell"));

		PlanetWeather.State storm = PlanetWeather.next(seed, first, balance);
		long length = storm.untilTick() - first.untilTick();
		helper.assertTrue(storm.kind() == PlanetWeather.Kind.SANDSTORM && length >= 3000 && length <= 6000,
				msg("a storm of 3000 to 6000 ticks follows, got " + storm));
		helper.assertTrue(storm.storming(first.untilTick()) && !storm.storming(storm.untilTick()), msg("the storm blows until its end tick"));
		helper.assertTrue(storm.equals(PlanetWeather.next(seed, first, balance)), msg("the storm is seeded too"));

		PlanetWeather.State after = PlanetWeather.next(seed, storm, balance);
		helper.assertTrue(after.kind() == PlanetWeather.Kind.CLEAR && after.untilTick() > storm.untilTick(), msg("a clear spell follows the storm"));
		helper.assertTrue(PlanetWeather.advance(seed, first, storm.untilTick() + 1, balance).equals(after),
				msg("advancing past two spells lands in the third"));

		boolean differs = false;

		for (long other = 1; other <= 16 && !differs; other++) {
			differs = !PlanetWeather.first(seed + other, now, balance).equals(first);
		}

		helper.assertTrue(differs, msg("another seed gives another cycle"));

		balance.sandstorms = false;
		long midStorm = first.untilTick() + 100;
		PlanetWeather.State calmed = PlanetWeather.advance(seed, storm, midStorm, balance);
		helper.assertTrue(!calmed.storming(midStorm), msg("with sandstorms off the storm ends now"));
		helper.assertTrue(PlanetWeather.next(seed, first, balance).kind() == PlanetWeather.Kind.CLEAR, msg("with sandstorms off no storm comes"));
		helper.succeed();
	}

	/** The saved state survives its codec and the payload; an unknown kind reads as clear. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void sandstormStateCodecRoundTrip(GameTestHelper helper) {
		for (PlanetWeather.State state : List.of(PlanetWeather.State.NONE, new PlanetWeather.State(PlanetWeather.Kind.SANDSTORM, 123_456_789L),
				new PlanetWeather.State(PlanetWeather.Kind.CLEAR, 98_000L))) {
			Tag nbt = PlanetWeather.State.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
			PlanetWeather.State loaded = PlanetWeather.State.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
			helper.assertTrue(loaded.equals(state), msg(state + " survives the codec, got " + loaded));
			helper.assertTrue(new PlanetWeatherPayload(state).state().equals(state), msg(state + " survives the payload"));
		}

		CompoundTag unknown = new CompoundTag();
		unknown.putInt("kind", 7);
		unknown.putLong("until", 5L);
		PlanetWeather.State read = PlanetWeather.State.CODEC.parse(NbtOps.INSTANCE, unknown).getOrThrow();
		helper.assertTrue(read.kind() == PlanetWeather.Kind.CLEAR && read.untilTick() == 5L, msg("an unknown kind reads as clear, got " + read));
		helper.succeed();
	}

	// ---------------------------------------------------------------- ridge lightning and stormglass

	/**
	 * A ridge strike lands {@code ridgeDistance} blocks from the player, on a stormback within
	 * {@code ridgeStormbackRange} instead, and never within {@code ridgeHubExclusion} of the holdfast.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void ridgeLightningDistanceSeekingAndHubExclusion(GameTestHelper helper) {
		PlanetBalance balance = new PlanetBalance();
		RandomSource random = RandomSource.create(7L);
		BlockPos farHub = new BlockPos(2000, 90, 2000);
		Vec3 player = new Vec3(10.5, 120, -4.5);

		for (int i = 0; i < 64; i++) {
			Vec3 point = RidgeLightning.target(player, null, farHub, random, balance);
			helper.assertTrue(point != null, msg("a strike far from the holdfast always lands"));
			double distance = Math.hypot(point.x - player.x, point.z - player.z);
			helper.assertTrue(distance >= 4 - 1.0E-6 && distance <= 10 + 1.0E-6 && point.y == player.y, msg("a strike 4 to 10 blocks away, got " + distance));
		}

		Vec3 nearStormback = player.add(12, 0, 12);
		helper.assertTrue(nearStormback.equals(RidgeLightning.target(player, nearStormback, farHub, random, balance)),
				msg("a stormback 17 blocks away draws the bolt"));

		Vec3 farStormback = player.add(30, 0, 0);
		Vec3 ignored = RidgeLightning.target(player, farStormback, farHub, random, balance);
		helper.assertTrue(ignored != null && !ignored.equals(farStormback) && Math.hypot(ignored.x - player.x, ignored.z - player.z) <= 10 + 1.0E-6,
				msg("a stormback 30 blocks away is too far to draw it"));

		BlockPos nearHub = BlockPos.containing(player).offset(20, -30, 0);

		for (int i = 0; i < 16; i++) {
			helper.assertTrue(RidgeLightning.target(player, null, nearHub, random, balance) == null, msg("no strike within 48 blocks of the holdfast"));
		}

		helper.assertTrue(RidgeLightning.target(player, nearStormback, nearHub, random, balance) == null,
				msg("not even a stormback draws a bolt into the holdfast's ring"));
		helper.succeed();
	}

	/**
	 * Real lightning on exposed basalt grows a stormglass cluster when the roll is under
	 * {@code stormglassChance}; never on other stone, under cover, or on a failed roll.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void stormglassFormsWhereLightningStrikesBasalt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		double chance = new PlanetBalance().stormglassChance;
		BlockPos basalt = new BlockPos(5, 1, 5);
		BlockPos smooth = new BlockPos(8, 1, 5);
		BlockPos stone = new BlockPos(11, 1, 5);
		BlockPos covered = new BlockPos(14, 1, 5);
		helper.setBlock(basalt, Blocks.BASALT.defaultBlockState());
		helper.setBlock(smooth, Blocks.SMOOTH_BASALT.defaultBlockState());
		helper.setBlock(stone, Blocks.STONE.defaultBlockState());
		helper.setBlock(covered, Blocks.POLISHED_BASALT.defaultBlockState());
		helper.setBlock(covered.above(), Blocks.STONE.defaultBlockState());

		helper.assertFalse(RidgeLightning.formStormglass(level, helper.absolutePos(basalt), (float) chance + 0.01f, chance), msg("a failed roll grows nothing"));
		helper.assertTrue(level.getBlockState(helper.absolutePos(basalt.above())).isAir(), msg("nothing grew on the failed roll"));
		helper.assertTrue(RidgeLightning.formStormglass(level, helper.absolutePos(basalt), 0f, chance), msg("a strike on basalt grows a cluster"));
		helper.assertTrue(level.getBlockState(helper.absolutePos(basalt.above())).is(PlanetBlocks.STORMGLASS_CLUSTER), msg("the cluster stands on the basalt"));
		helper.assertTrue(RidgeLightning.formStormglass(level, helper.absolutePos(smooth), 0f, chance), msg("smooth basalt grows one too"));
		helper.assertFalse(RidgeLightning.formStormglass(level, helper.absolutePos(stone), 0f, chance), msg("stone is not basalt"));
		helper.assertFalse(RidgeLightning.formStormglass(level, helper.absolutePos(covered), 0f, chance), msg("covered basalt grows nothing"));
		helper.assertFalse(RidgeLightning.formStormglass(level, helper.absolutePos(basalt), 0f, chance), msg("a grown cluster is not grown again"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- verse stones

	/**
	 * The owner holds the use key on an unguarded stone: the channel starts, breaks when the key is
	 * released or the owner walks 6 blocks away, and a full channel of {@code silenceTicks} silences
	 * the stone (dark, no channel). On Peaceful a use silences it at once.
	 */
	@GameTest(structure = ARENA, maxTicks = 400)
	public void verseStoneChannelAndInterrupt(GameTestHelper helper) {
		Run run = new Run(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = run.listed(new BlockPos(12, 1, 9));
		BlockPos pos = helper.absolutePos(new BlockPos(12, 1, 12));
		VerseStoneBlockEntity stone = verseStone(helper, new BlockPos(12, 1, 12), owner.getUUID());

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			run.now(() -> {
				VerseStones.use(level, pos, owner);
				helper.assertFalse(stone.awake(), msg("on Peaceful a use silences the stone at once"));
			});
			return;
		}

		int total = Math.max(1, PlanetBalance.get().silenceTicks);
		run.step(t -> {
			use(level, pos, owner, t);
			helper.assertTrue(owner.getUUID().equals(stone.channeller()), msg("the owner's use starts the channel"));
			return t >= 16;
		}).step(t -> {
			// The key is released: the channel lapses after its grace.
			return t > VerseStones.RELEASE_GRACE + 2;
		}).step(t -> {
			helper.assertTrue(stone.channeller() == null && stone.awake(), msg("a released channel breaks; the stone still sings"));
			use(level, pos, owner, 0);
			helper.assertTrue(owner.getUUID().equals(stone.channeller()), msg("a new use starts a new channel"));
			Vec3 away = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 1, 2)));
			owner.snapTo(away.x, away.y, away.z);
			return true;
		}).step(t -> {
			use(level, pos, owner, t);
			return t >= 2;
		}).step(t -> {
			helper.assertTrue(stone.channeller() == null && stone.awake(), msg("walking 6 blocks away breaks the channel"));
			Vec3 back = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 1, 9)));
			owner.snapTo(back.x, back.y, back.z);
			return true;
		}).step(t -> {
			use(level, pos, owner, t);
			helper.assertTrue(t <= total + 40, msg("the channel did not silence the stone in " + (total + 40) + " ticks"));
			return !stone.awake();
		}).step(t -> {
			helper.assertFalse(level.getBlockState(pos).getValue(VerseStoneBlock.AWAKE), msg("a silenced stone goes dark"));
			helper.assertTrue(stone.channeller() == null, msg("the silence ends the channel"));
			return true;
		}).start();
	}

	/** A Push at the stone during a channel cuts {@code silenceForceCut} ticks once; again, or with no channel, it does nothing. */
	@GameTest(structure = ARENA, maxTicks = 120)
	public void verseStoneForceCutOnce(GameTestHelper helper) {
		Run run = new Run(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = run.listed(new BlockPos(12, 1, 9));
		BlockPos pos = helper.absolutePos(new BlockPos(12, 1, 12));
		VerseStoneBlockEntity stone = verseStone(helper, new BlockPos(12, 1, 12), owner.getUUID());
		BlockPos idlePos = helper.absolutePos(new BlockPos(6, 1, 12));
		verseStone(helper, new BlockPos(6, 1, 12), owner.getUUID());

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			run.now(() -> helper.assertFalse(push(owner, idlePos), msg("no channel, no cut")));
			return;
		}

		int cut = Math.max(0, PlanetBalance.get().silenceForceCut);
		int[] before = new int[1];
		run.step(t -> {
			use(level, pos, owner, t);
			return t >= 8;
		}).step(t -> {
			helper.assertFalse(push(owner, idlePos), msg("a Push at a stone with no channel does nothing"));
			before[0] = stone.progress();
			helper.assertTrue(push(owner, pos), msg("the first Push during the channel answers"));
			helper.assertTrue(stone.forceCut() && stone.progress() >= before[0] + cut, msg("the channel jumps " + cut + " ticks, from " + before[0] + " to "
					+ stone.progress()));
			int after = stone.progress();
			helper.assertFalse(push(owner, pos), msg("a second Push in the same channel does nothing"));
			helper.assertTrue(stone.progress() == after, msg("the second Push cuts nothing"));
			return true;
		}).start();
	}

	/**
	 * The party rule: the owner may channel; anyone else only while the owner is in the same world
	 * within 32 blocks; a stone without an owner sings for no one.
	 */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void verseStoneOwnerAndPartyOnly(GameTestHelper helper) {
		Vec3 stoneAt = new Vec3(0.5, 64.5, 0.5);
		UUID owner = new UUID(0x7E55L, 1L);
		UUID other = new UUID(0x7E55L, 2L);
		helper.assertTrue(VerseStones.mayChannel(owner, owner, null, stoneAt), msg("the owner may always channel"));
		helper.assertFalse(VerseStones.mayChannel(owner, other, null, stoneAt), msg("a helper may not while the owner is away"));
		helper.assertTrue(VerseStones.mayChannel(owner, other, stoneAt.add(20, 0, 20), stoneAt), msg("a helper may with the owner 28 blocks off"));
		helper.assertFalse(VerseStones.mayChannel(owner, other, stoneAt.add(30, 0, 30), stoneAt), msg("a helper may not with the owner 42 blocks off"));
		helper.assertFalse(VerseStones.mayChannel(null, other, null, stoneAt), msg("a stone without an owner sings for no one"));

		Run run = new Run(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer helperPlayer = run.listed(new BlockPos(12, 1, 9));
		BlockPos lonePos = helper.absolutePos(new BlockPos(6, 1, 12));
		VerseStoneBlockEntity lone = verseStone(helper, new BlockPos(6, 1, 12), new UUID(0x7E55L, 3L));

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			run.now(() -> {
				VerseStones.use(level, lonePos, helperPlayer);
				helper.assertTrue(lone.awake(), msg("another player's stone does nothing for them"));
			});
			return;
		}

		ServerPlayer ownerPlayer = run.listed(new BlockPos(16, 1, 12));
		BlockPos ownedPos = helper.absolutePos(new BlockPos(12, 1, 12));
		VerseStoneBlockEntity owned = verseStone(helper, new BlockPos(12, 1, 12), ownerPlayer.getUUID());
		run.now(() -> {
			VerseStones.use(level, lonePos, helperPlayer);
			helper.assertTrue(lone.channeller() == null && lone.awake(), msg("a stranger's stone with its owner away refuses"));
			VerseStones.use(level, ownedPos, helperPlayer);
			helper.assertTrue(helperPlayer.getUUID().equals(owned.channeller()), msg("a helper beside the owner may channel"));
			owned.clearChannel();
		});
	}

	/**
	 * While a guard lives the stone is guarded (no channel), and its hum slows the survival players
	 * within {@code humSlowRange}, never creative ones; once the guard is gone the channel starts.
	 */
	@GameTest(structure = ARENA, maxTicks = 80)
	public void verseHumSlowsWhileGuarded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 centre = Vec3.atCenterOf(helper.absolutePos(new BlockPos(12, 1, 12)));
		double range = new PlanetBalance().humSlowRange;
		FakePlayer survivor = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), "verse_hum_test"));
		survivor.setPos(centre.add(3, -0.5, 0));
		helper.assertTrue(!survivor.isCreative() && VerseStones.humSlows(survivor, centre, range), msg("the hum slows a survival player 3 blocks off"));
		survivor.setPos(centre.add(range + 1, -0.5, 0));
		helper.assertFalse(VerseStones.humSlows(survivor, centre, range), msg("the hum does not reach past " + range + " blocks"));

		Run run = new Run(helper);
		ServerPlayer owner = run.listed(new BlockPos(12, 1, 9));
		helper.assertFalse(VerseStones.humSlows(owner, centre, range), msg("the hum never slows a creative player"));
		BlockPos pos = helper.absolutePos(new BlockPos(12, 1, 12));
		VerseStoneBlockEntity stone = verseStone(helper, new BlockPos(12, 1, 12), owner.getUUID());
		LivingEntity guard = run.entity(helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(16, 1, 16)));
		stone.addGuard(guard.getUUID());

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			run.now(() -> helper.assertTrue(VerseStones.guardsAlive(level, stone), msg("a living guard keeps the verse")));
			return;
		}

		run.step(t -> {
			helper.assertTrue(VerseStones.guardsAlive(level, stone), msg("a living guard keeps the verse"));
			VerseStones.use(level, pos, owner);
			helper.assertTrue(stone.channeller() == null, msg("no channel while a guard lives"));
			guard.discard();
			return true;
		}).step(t -> t >= 2).step(t -> {
			helper.assertFalse(VerseStones.guardsAlive(level, stone), msg("the verse is unguarded once its guard is gone"));
			VerseStones.use(level, pos, owner);
			helper.assertTrue(owner.getUUID().equals(stone.channeller()), msg("the channel starts once the guard is gone"));
			stone.clearChannel();
			return true;
		}).start();
	}

	/**
	 * Silencing goes dark and pays {@code QuestHooks.onVerseSilenced} to an online owner at once; for
	 * an owner who is away the hook is owed until they are next online.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void silencedStoneCallsHook(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Run run = new Run(helper);
		ServerPlayer owner = run.listed(new BlockPos(12, 1, 9));
		BlockPos awayPos = helper.absolutePos(new BlockPos(6, 1, 12));
		VerseStoneBlockEntity away = verseStone(helper, new BlockPos(6, 1, 12), new UUID(0x51L, 0x1E7L));
		BlockPos ownedPos = helper.absolutePos(new BlockPos(12, 1, 12));
		VerseStoneBlockEntity owned = verseStone(helper, new BlockPos(12, 1, 12), owner.getUUID());
		run.now(() -> {
			VerseStones.silence(level, awayPos, away, null);
			helper.assertFalse(away.awake() || level.getBlockState(awayPos).getValue(VerseStoneBlock.AWAKE), msg("the silenced stone goes dark"));
			helper.assertTrue(away.hookOwed(), msg("the hook is owed while the owner is away"));
			VerseStones.silence(level, ownedPos, owned, owner);
			helper.assertFalse(owned.awake(), msg("the channeller's stone goes dark"));
			helper.assertFalse(owned.hookOwed(), msg("an online owner's hook is paid at once"));
		});
	}

	// ---------------------------------------------------------------- the Hymnal and the holdfast

	/** The Hymnal is placed once per server, whoever asks first (detached data); verse stones are per player. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void hymnalPlacedOncePerServer(GameTestHelper helper) {
		helper.assertTrue(PlanetSites.shared(PlanetSites.Site.HYMNAL), msg("the Hymnal is shared"));
		helper.assertFalse(PlanetSites.shared(PlanetSites.Site.VERSE_STONE_1) || PlanetSites.shared(PlanetSites.Site.DEBTOR_CAMP),
				msg("verse stones and debtor camps are each player's own"));

		PlanetSiteData.Data data = new PlanetSiteData.Data();
		int[] placements = new int[1];
		GlobalPos placed = GlobalPos.of(Planets.TYRVANN, new BlockPos(640, 70, -310));
		GlobalPos first = PlanetSites.ensureShared(data, PlanetSites.Site.HYMNAL, () -> {
			placements[0]++;
			return placed;
		});
		GlobalPos second = PlanetSites.ensureShared(data, PlanetSites.Site.HYMNAL, () -> {
			placements[0]++;
			return GlobalPos.of(Planets.TYRVANN, BlockPos.ZERO);
		});
		helper.assertTrue(placements[0] == 1 && first.equals(placed) && second.equals(placed), msg("the second ask finds the first Hymnal"));

		Tag nbt = PlanetSiteData.Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		PlanetSiteData.Data loaded = PlanetSiteData.Data.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
		helper.assertTrue(placed.equals(loaded.shared(PlanetSites.Site.HYMNAL)), msg("the Hymnal survives the codec"));
		helper.succeed();
	}

	/**
	 * The halls sink deep enough for {@link Hymnal#COVER} blocks of ground over them and
	 * {@code hymnalDepth}, at most {@link Hymnal#MAX_SHAFT} and never into the world's floor; a door of
	 * the Hymnal opens for a creative player, from inside, or when the story allows.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void hymnalDepthAndDoorRules(GameTestHelper helper) {
		int minY = -64;
		int depth = Hymnal.TEMPLATE_DEPTH;
		helper.assertTrue(Hymnal.gapFor(50, 23, 60, depth, minY) == 0, msg("halls already 10 below the ground stay put"));
		helper.assertTrue(Hymnal.gapFor(58, 31, 60, depth, minY) == 2, msg("halls 2 below the ground sink 2 more for their cover"));
		helper.assertTrue(Hymnal.gapFor(50, 23, 60, depth + 12, minY) == 12, msg("a deeper hymnalDepth sinks them further"));
		helper.assertTrue(Hymnal.gapFor(200, 173, 60, depth, minY) == Hymnal.MAX_SHAFT, msg("the shaft is never longer than " + Hymnal.MAX_SHAFT));
		helper.assertTrue(Hymnal.gapFor(58, minY + Hymnal.BOTTOM_MARGIN + 1, 60, depth + 20, minY) == 1, msg("the halls never sink into the floor"));

		helper.assertTrue(HymnalDoors.mayOpen(false, false, true), msg("a creative player opens any door"));
		helper.assertTrue(HymnalDoors.mayOpen(false, true, false), msg("a door opens from inside"));
		helper.assertTrue(HymnalDoors.mayOpen(true, false, false), msg("a door opens when the story allows"));
		helper.assertFalse(HymnalDoors.mayOpen(false, false, false), msg("otherwise it stays shut"));
		helper.succeed();
	}

	/**
	 * The gate opens for Neutral clan standing and up, from inside, and for creative players; opened,
	 * the whole connected gate opens together and shuts again {@link HoldfastGateBlock#OPEN_TICKS}
	 * after the last one passed.
	 */
	@GameTest(structure = ARENA, maxTicks = 160)
	public void holdfastGateOpensPerPlayerFromNeutral(GameTestHelper helper) {
		for (ReputationTier tier : ReputationTier.values()) {
			boolean expected = tier.ordinal() >= ReputationTier.NEUTRAL.ordinal();
			helper.assertTrue(HoldfastGateBlock.mayOpen(tier, false, false) == expected, msg(tier + (expected ? " opens" : " is refused")));
			helper.assertTrue(HoldfastGateBlock.mayOpen(tier, true, false) && HoldfastGateBlock.mayOpen(tier, false, true),
					msg("inside, or creative, always opens at " + tier));
		}

		BlockPos gate = helper.absolutePos(new BlockPos(11, 1, 12));
		helper.assertTrue(HoldfastGateBlock.inside(gate, Direction.NORTH, Vec3.atCenterOf(gate.south())), msg("south of a north-facing gate is inside"));
		helper.assertFalse(HoldfastGateBlock.inside(gate, Direction.NORTH, Vec3.atCenterOf(gate.north())), msg("north of it is outside"));

		ServerLevel level = helper.getLevel();
		List<BlockPos> cells = new ArrayList<>();
		BlockState shut = PlanetBlocks.HOLDFAST_GATE.defaultBlockState().setValue(HoldfastGateBlock.FACING, Direction.NORTH);

		for (int x = 10; x <= 12; x++) {
			for (int y = 1; y <= 3; y++) {
				helper.setBlock(new BlockPos(x, y, 12), shut);
				cells.add(helper.absolutePos(new BlockPos(x, y, 12)));
			}
		}

		helper.assertTrue(PlanetBlocks.HOLDFAST_GATE.open(level, gate), msg("the shut gate opens"));
		helper.assertTrue(cells.stream().allMatch(cell -> level.getBlockState(cell).getValue(HoldfastGateBlock.OPEN)), msg("all nine plates open together"));
		helper.assertFalse(PlanetBlocks.HOLDFAST_GATE.open(level, gate), msg("an open gate does not open again"));
		helper.runAfterDelay(HoldfastGateBlock.OPEN_TICKS + 20, () -> {
			helper.assertTrue(cells.stream().noneMatch(cell -> level.getBlockState(cell).getValue(HoldfastGateBlock.OPEN)),
					msg("the gate shuts again once no one stands in it"));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- blocks, templates and structures

	/** Peat burns 1.5 times as long as coal ({@link PlanetM5Feature#PEAT_FUEL_SMELTS} smelts against coal's 8). */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void peatBurnsAsFuel(GameTestHelper helper) {
		// API-CHECK: Level#fuelValues and FuelValues#burnDuration(ItemStack) in 26.2.
		int peat = helper.getLevel().fuelValues().burnDuration(new ItemStack(PlanetBlocks.PEAT));
		int coal = helper.getLevel().fuelValues().burnDuration(new ItemStack(Items.COAL));
		helper.assertTrue(coal > 0 && peat * 8 == coal * PlanetM5Feature.PEAT_FUEL_SMELTS, msg("peat burns " + peat + " ticks against coal's " + coal));
		helper.succeed();
	}

	/**
	 * Every M5 template of W3 loads with its size, has a sidecar naming each of its anchors with every
	 * cell inside the size, and the verse stone's {@code STONE}, the Hymnal's doors and the holdfast's
	 * gate hold their blocks. Loads the templates; never places one.
	 */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void everyM5TemplateHasSidecarAnchorsInsideSize(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<String, List<Map.Entry<String, Block>>> blocks = Map.of("site/verse_stone", List.of(Map.entry("STONE", PlanetBlocks.VERSE_STONE)),
				"site/hymnal", List.of(Map.entry("HEART_DOOR", PlanetBlocks.SEALED_PLATING), Map.entry("UNDERCROFT_DOOR", PlanetBlocks.SEALED_PLATING)),
				"hub/holdfast", List.of(Map.entry("HOLDFAST_GATE", PlanetBlocks.HOLDFAST_GATE)));

		for (M5Template expected : TEMPLATES) {
			Identifier id = OldRepublicMod.id(expected.path());
			StructureTemplate template = level.getStructureManager().get(id).orElse(null);
			helper.assertTrue(template != null, msg("missing structure template " + id));
			Vec3i size = template.getSize();
			helper.assertTrue(size.equals(expected.size()), msg(id + " has size " + size + ", expected " + expected.size()));
			helper.assertTrue(SiteAnchors.templates().contains(id), msg(id + " has no sidecar"));

			for (String name : expected.anchors()) {
				SiteAnchors.Anchor anchor = SiteAnchors.Anchor.byName(name);
				helper.assertTrue(anchor != null, msg("unknown anchor " + name));
				helper.assertFalse(SiteAnchors.of(id, anchor).isEmpty(), msg(id + " does not name " + name));
			}

			for (SiteAnchors.Anchor anchor : SiteAnchors.Anchor.values()) {
				for (BlockPos cell : SiteAnchors.of(id, anchor)) {
					helper.assertTrue(cell.getX() >= 0 && cell.getY() >= 0 && cell.getZ() >= 0 && cell.getX() < size.getX() && cell.getY() < size.getY()
							&& cell.getZ() < size.getZ(), msg(id + " " + anchor + " " + cell.toShortString() + " lies outside " + size));
				}
			}

			for (Map.Entry<String, Block> held : blocks.getOrDefault(expected.path(), List.of())) {
				SiteAnchors.Anchor anchor = SiteAnchors.Anchor.byName(held.getKey());
				// With BlockPos.ZERO and default settings, filterBlocks gives template-local positions.
				List<BlockPos> found = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), held.getValue()).stream()
						.map(StructureTemplate.StructureBlockInfo::pos).toList();

				for (BlockPos cell : SiteAnchors.of(id, anchor)) {
					helper.assertTrue(found.contains(cell), msg(id + " " + anchor + " " + cell.toShortString() + " must hold " + held.getValue()));
				}
			}
		}

		helper.succeed();
	}

	/** The four M5 worldgen structures are registered and each is in the tag its sites are found by. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void newStructuresHaveStructureTags(GameTestHelper helper) {
		Registry<Structure> structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);

		for (Map.Entry<String, TagKey<Structure>> entry : STRUCTURE_TAGS.entrySet()) {
			Identifier id = OldRepublicMod.id(entry.getKey());
			Holder.Reference<Structure> structure = structures.get(id).orElse(null);
			helper.assertTrue(structure != null, msg("structure " + id + " is not registered"));
			helper.assertTrue(structure.is(entry.getValue()), msg(id + " must be in #" + entry.getValue().location()));
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- helpers

	/** A verse stone at {@code relative} owned by {@code owner}, its first, marked as having spawned no guards (no group spawns in a test). */
	private static VerseStoneBlockEntity verseStone(GameTestHelper helper, BlockPos relative, UUID owner) {
		helper.setBlock(relative, PlanetBlocks.VERSE_STONE.defaultBlockState());

		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(relative)) instanceof VerseStoneBlockEntity stone)) {
			throw helper.assertionException(msg("no verse stone block entity at " + relative.toShortString()));
		}

		stone.setup(owner, 1, null);
		stone.setGuards(List.of());
		return stone;
	}

	/** Holds the use key: a use every fourth tick, as the held key repeats it. */
	private static void use(ServerLevel level, BlockPos pos, ServerPlayer player, int tick) {
		if (tick % 4 == 0) {
			VerseStones.use(level, pos, player);
		}
	}

	/** A Push by {@code caster} at the block at {@code pos}, as the power dispatches it. */
	private static boolean push(ServerPlayer caster, BlockPos pos) {
		ServerLevel level = caster.level();
		return ForceBlockInteractions.dispatch(new ForceBlockContext(caster, level, pos, level.getBlockState(pos), ForceAct.PUSH, new Vec3(0, 0, 1), 1f,
				ForcePush.ABILITY_ID));
	}

	private static <T> List<T> concat(List<T> a, List<T> b) {
		List<T> out = new ArrayList<>(a);
		out.addAll(b);
		return List.copyOf(out);
	}

	private static Component msg(String text) {
		return Component.literal(text);
	}

	/**
	 * A test run over ticks: steps that each poll until they answer true (given the ticks since they
	 * began), with its listed players and entities removed when it ends, pass or fail.
	 */
	private static final class Run {
		private final GameTestHelper helper;
		private final List<IntPredicate> steps = new ArrayList<>();
		private final List<ServerPlayer> players = new ArrayList<>();
		private final List<LivingEntity> entities = new ArrayList<>();
		private int index;
		private int ticks;
		private boolean finished;

		Run(GameTestHelper helper) {
			this.helper = helper;
		}

		ServerPlayer listed(BlockPos relative) {
			ServerPlayer player = M3TestPlayers.listed(helper, relative);
			players.add(player);
			return player;
		}

		<T extends LivingEntity> T entity(T entity) {
			entities.add(entity);
			return entity;
		}

		Run step(IntPredicate step) {
			steps.add(step);
			return this;
		}

		void start() {
			helper.onEachTick(this::tick);
		}

		/** A run with nothing to wait for: runs {@code body}, cleans up (pass or fail) and succeeds. */
		void now(Runnable body) {
			try {
				body.run();
			} finally {
				clean();
			}

			helper.succeed();
		}

		private void tick() {
			if (finished) {
				return;
			}

			try {
				if (steps.get(index).test(ticks++)) {
					index++;
					ticks = 0;
				}

				if (index >= steps.size()) {
					finished = true;
					clean();
					helper.succeed();
				}
			} catch (RuntimeException e) {
				finished = true;
				clean();
				throw e;
			}
		}

		private void clean() {
			players.forEach(M3TestPlayers::remove);
			entities.forEach(LivingEntity::discard);
		}
	}
}
