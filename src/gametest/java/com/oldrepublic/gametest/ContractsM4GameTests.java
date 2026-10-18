package com.oldrepublic.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityLocks;
import com.oldrepublic.combat.DangerTellTable;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.melee.VibroWeaponItem;
import com.oldrepublic.mission.MissionData;
import com.oldrepublic.mission.MissionDefinition;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.planet.TerrainPalette;
import com.oldrepublic.quest.Guidance;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * The Milestone 4 Phase 0 contracts (spec MILESTONE_4.md 15.1, 17.1): green before the workers
 * start. Every test is detached data or a small entity setup; real planet travel by players is the
 * client suite's.
 */
public class ContractsM4GameTests {
	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static FakePlayer player(GameTestHelper helper, BlockPos pos, String name) {
		ServerLevel level = helper.getLevel();
		FakePlayer player = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), name));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(pos)));
		return player;
	}

	@GameTest(maxTicks = 20)
	public void planetLevelsExistOnTestServer(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		boolean selvaren = Planets.level(server, Planets.Planet.SELVAREN) != null;
		boolean varkhast = Planets.level(server, Planets.Planet.VARKHAST) != null;
		// Logged either way: when the test server makes no planets, the planet tests move to the client suite (17.1).
		OldRepublicMod.LOGGER.info("[ContractsM4] planet levels on the game-test server: selvaren={}, varkhast={}", selvaren, varkhast);
		helper.assertTrue(selvaren == varkhast, msg("both planets or neither (selvaren " + selvaren + ", varkhast " + varkhast + ")"));
		helper.assertTrue(Planets.level(server, Planets.Planet.HOME) == server.overworld(), msg("HOME is the overworld"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void isStoryLevelSplit(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel overworld = server.overworld();
		helper.assertTrue(Planets.isStoryLevel(overworld) && Planets.isHomeWorld(overworld) && !Planets.isPlanet(overworld),
				msg("the overworld is a story level and the home world, not a planet"));
		ServerLevel nether = server.getLevel(Level.NETHER);

		if (nether != null) {
			helper.assertTrue(!Planets.isStoryLevel(nether) && !Planets.isHomeWorld(nether) && !Planets.isPlanet(nether), msg("the Nether is none of them"));
		}

		for (Planets.Planet planet : new Planets.Planet[] {Planets.Planet.SELVAREN, Planets.Planet.VARKHAST}) {
			ServerLevel level = Planets.level(server, planet);

			if (level != null) {
				helper.assertTrue(Planets.isStoryLevel(level) && !Planets.isHomeWorld(level) && Planets.isPlanet(level),
						msg(planet.id() + " is a story level and a planet, not the home world"));
			}

			helper.assertTrue(Planets.Planet.of(planet.level()) == planet && Planets.Planet.byId(planet.id()) == planet, msg(planet.id() + " round-trips"));
		}

		helper.assertTrue(Planets.Planet.of(Level.NETHER) == null && Planets.Planet.byId("nether") == null, msg("the Nether is no Planet"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void clocksUseOverworldTime(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		long time = server.overworld().getGameTime();
		helper.assertTrue(Clocks.time(server) == time && Clocks.day(server) == time / Clocks.TICKS_PER_DAY, msg("the clock is the overworld's"));

		for (Planets.Planet planet : Planets.Planet.values()) {
			ServerLevel level = Planets.level(server, planet);

			// A data-pack dimension's level data derives from the overworld's, so its game time is the same (3.6).
			if (level != null) {
				helper.assertTrue(level.getGameTime() == time, msg(planet.id() + " shares the overworld game time, got " + level.getGameTime() + " vs " + time));
			}
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void terrainPaletteFallsBackToGrassDirtStone(GameTestHelper helper) {
		TerrainPalette.Layers layers = TerrainPalette.at(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
		helper.assertTrue(layers.equals(TerrainPalette.Layers.DEFAULT), msg("an overworld biome without a row gets grass, dirt and stone, got " + layers));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void worldSitesCountPerDimensionAndCap1024(GameTestHelper helper) {
		helper.assertTrue(WorldSites.Data.MAX_RECORDS == 1024, msg("the record cap is 1,024"));
		WorldSites.Data data = new WorldSites.Data();
		data.add(site(SiteKind.WAYSTONE, Planets.SELVAREN, new BlockPos(0, 70, 0), "planet/waystone"));
		data.add(site(SiteKind.WAYSTONE, Planets.SELVAREN, new BlockPos(200, 70, 0), "planet/waystone"));
		data.add(site(SiteKind.WAYSTONE, Level.OVERWORLD, new BlockPos(0, 70, 0), "planet/waystone"));
		helper.assertTrue(data.count(SiteKind.WAYSTONE, Planets.SELVAREN) == 2 && data.count(SiteKind.WAYSTONE, Level.OVERWORLD) == 1
				&& data.count(SiteKind.WAYSTONE, Planets.VARKHAST) == 0, msg("counted per dimension"));
		helper.assertTrue(data.count(SiteKind.WAYSTONE) == 3, msg("count(kind) still counts every dimension"));
		helper.assertTrue(SiteKind.ofTemplate(OldRepublicMod.id("greater_tomb/crypt_a")) == SiteKind.GREATER_TOMB
				&& SiteKind.ofTemplate(OldRepublicMod.id("hub/proving")) == SiteKind.PLANET_HUB
				&& SiteKind.ofTemplate(OldRepublicMod.id("hub/sepulchre")) == SiteKind.SEPULCHRE
				&& SiteKind.ofTemplate(OldRepublicMod.id("ship/cradle")) == SiteKind.SHIP_CRADLE
				&& SiteKind.ofTemplate(OldRepublicMod.id("mission/ambush_rocks")) == SiteKind.MISSION_SITE
				&& SiteKind.ofTemplate(OldRepublicMod.id("ship/beacon_pad")) == null, msg("the M4 template paths map to their kinds"));
		helper.succeed();
	}

	private static WorldSites.SiteRecord site(SiteKind kind, net.minecraft.resources.ResourceKey<Level> dimension, BlockPos origin, String template) {
		return new WorldSites.SiteRecord(kind, dimension, origin, Rotation.NONE, OldRepublicMod.id(template), null, 0L);
	}

	@GameTest(maxTicks = 20)
	public void guidanceRequestOldConstructorMeansSameLevel(GameTestHelper helper) {
		Guidance.Request old = new Guidance.Request(Guidance.MISSION, Vec3.ZERO, 5, "test");
		Guidance.Request away = new Guidance.Request(Guidance.STORY, Vec3.ZERO, 6, "test", Planets.SELVAREN);
		helper.assertTrue(old.dimension() == null, msg("the M3 constructor means the player's own level"));
		helper.assertTrue(Planets.SELVAREN.equals(away.dimension()), msg("the new constructor keeps the dimension"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void missionActiveWithoutWhereDecodesAsOverworld(GameTestHelper helper) {
		MissionData data = new MissionData();
		data.active = new MissionData.Active("c4_greenwatch", "c4_greenwatch", MissionDefinition.Kind.STORY, "mentor", 3, 7L, new BlockPos(1, 64, 2));
		data.active.site = new BlockPos(10, 70, 20);
		JsonObject json = MissionData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
		json.getAsJsonObject("active").remove("where");
		MissionData old = MissionData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(old.active != null && old.active.siteDimension == Level.OVERWORLD && old.active.boardDimension == Level.OVERWORLD,
				msg("a save without where is a home-world mission"));
		helper.assertTrue(GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 70, 20)).equals(old.active.siteGlobal()), msg("siteGlobal combines them"));

		data.active.siteDimension = Planets.VARKHAST;
		MissionData back = MissionData.CODEC.parse(JsonOps.INSTANCE, MissionData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
		helper.assertTrue(back.active != null && back.active.siteDimension.equals(Planets.VARKHAST) && back.active.boardDimension == Level.OVERWORLD,
				msg("where round-trips"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void chapter4AbsentDecodes(GameTestHelper helper) {
		JsonObject json = StoryData.CODEC.encodeStart(JsonOps.INSTANCE, new StoryData()).getOrThrow().getAsJsonObject();
		json.remove("chapter4");
		StoryData old = StoryData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(old.chapter4.equals(StoryData.Chapter4.NEW), msg("an M3 save decodes to a new Chapter Four"));

		StoryData data = new StoryData();
		data.chapter4 = data.chapter4.withStage(ChapterFourStage.HUNTED, 1200L)
				.withArc(new StoryData.Chapter4.Arc("varkhast", 3, 1))
				.withHunt(StoryData.Chapter4.Hunt.NONE.withClues(0b101));
		StoryData back = StoryData.CODEC.parse(JsonOps.INSTANCE, StoryData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
		helper.assertTrue(back.chapter4.equals(data.chapter4), msg("Chapter Four round-trips, got " + back.chapter4));
		helper.assertTrue(back.chapter4.arcPlanet() == Planets.Planet.VARKHAST, msg("the arc planet reads back"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void shipRegistryDataCodecRoundTrip(GameTestHelper helper) {
		var ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		UUID owner = UUID.randomUUID();
		ShipRecord ship = ShipRecord.hulk(owner, UUID.randomUUID(), GlobalPos.of(Planets.SELVAREN, new BlockPos(5, 80, -5)), 90f, "GR-17")
				.withState(ShipState.IN_TRANSIT).withHull(85).withFuel(3).withUpgrades(ShipRecord.UPGRADE_SEAT | ShipRecord.UPGRADE_TANK)
				.withHold(List.of(new ItemStack(Items.IRON_INGOT, 5), ItemStack.EMPTY, new ItemStack(M4Items.HYPERFUEL_CELL, 2)))
				.withCrew(List.of(UUID.randomUUID()))
				.withFlight(Optional.of(new ShipRecord.Flight(Destination.HOME, Destination.GREENWATCH, 3)));
		ShipRegistry.Data data = new ShipRegistry.Data();
		data.put(ship);
		JsonElement json = ShipRegistry.Data.CODEC.encodeStart(ops, data).getOrThrow();
		ShipRegistry.Data back = ShipRegistry.Data.CODEC.parse(ops, json).getOrThrow();
		ShipRecord read = back.get(owner).orElse(null);
		helper.assertTrue(read != null, msg("the record comes back"));
		helper.assertTrue(read.body().equals(ship.body()) && read.pos().equals(ship.pos()) && read.yaw() == 90f && read.state() == ShipState.IN_TRANSIT
				&& read.hull() == 85 && read.fuel() == 3 && read.has(ShipRecord.UPGRADE_TANK) && !read.has(ShipRecord.UPGRADE_CARGO)
				&& read.crew().equals(ship.crew()) && read.hullCode().equals("GR-17") && read.flight().equals(ship.flight()), msg("every field round-trips"));
		helper.assertTrue(read.hold().size() == 3 && read.hold().get(0).is(Items.IRON_INGOT) && read.hold().get(0).getCount() == 5
				&& read.hold().get(1).isEmpty() && read.hold().get(2).is(M4Items.HYPERFUEL_CELL), msg("the hold keeps its slots, empty ones included"));
		helper.assertTrue(back.byBody(ship.body()).isPresent() && back.byBody(UUID.randomUUID()).isEmpty(), msg("byBody finds the owner"));
		helper.assertTrue(ship.withHull(150).hull() == 100 && ship.withHull(-3).hull() == 0, msg("hull clamps to 0..100"));
		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void moveAcrossKeepsUuidAndRemountsRiders(GameTestHelper helper) {
		ServerLevel from = helper.getLevel();
		MinecraftServer server = from.getServer();
		ServerLevel to = Planets.level(server, Planets.Planet.SELVAREN);

		if (to == null) {
			to = server.getLevel(Level.NETHER);
		}

		if (to == null) {
			to = from;
		}

		Entity cart = helper.spawn(EntityTypes.MINECART, new BlockPos(2, 1, 2));
		Entity pig = helper.spawn(EntityTypes.PIG, new BlockPos(2, 1, 2));
		helper.assertTrue(pig.startRiding(cart, true, false), msg("the pig boards the cart"));
		UUID cartId = cart.getUUID();
		UUID pigId = pig.getUUID();
		Vec3 dest = new Vec3(0.5, 200, 0.5);
		// Load the destination chunk so the moved entities are found by UUID at once.
		to.getChunk(0, 0);

		Entity moved = TravelService.moveAcross(cart, to, dest, 45f);
		ServerLevel target = to;

		try {
			helper.assertTrue(moved.getUUID().equals(cartId), msg("the vehicle keeps its UUID"));
			helper.assertTrue(moved.level() == target && moved.position().distanceTo(dest) < 0.01, msg("the vehicle is at the destination"));
			Entity rider = target.getEntity(pigId);
			helper.assertTrue(rider != null && rider.getVehicle() == moved, msg("the rider is riding the moved vehicle"));

			if (target != from) {
				helper.assertTrue(moved != cart && cart.isRemoved(), msg("a change of level makes a new object and removes the old one"));
			}

			helper.succeed();
		} finally {
			Entity rider = target.getEntity(pigId);

			if (rider != null) {
				rider.discard();
			}

			moved.discard();
		}
	}

	@GameTest(maxTicks = 20)
	public void creditsTakeAllOrNothingWithChange(GameTestHelper helper) {
		FakePlayer p = player(helper, new BlockPos(1, 1, 1), "credits_test");
		p.getInventory().add(new ItemStack(M4Items.CREDIT_BUNDLE, 3));
		p.getInventory().add(new ItemStack(M4Items.CREDIT_CHIT, 50));
		helper.assertTrue(Credits.count(p, false) == 350, msg("3 bundles and 50 chits are 350, got " + Credits.count(p, false)));
		helper.assertTrue(!Credits.take(p, 420, false) && Credits.count(p, false) == 350, msg("too little pays nothing and takes nothing"));
		helper.assertTrue(Credits.take(p, 120, false), msg("120 is paid"));
		helper.assertTrue(Credits.count(p, false) == 230, msg("exact change: 230 left, got " + Credits.count(p, false)));
		helper.assertTrue(Credits.take(p, 230, false) && Credits.count(p, false) == 0, msg("the rest is paid to the last credit"));
		Credits.give(p, 205);
		helper.assertTrue(Credits.count(p, false) == 205, msg("give adds 205, got " + Credits.count(p, false)));
		helper.assertTrue(Credits.take(p, 0, false) && !Credits.take(p, -5, false), msg("zero is free; a negative amount is refused"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void reusedItemIdsAreNewClassesAndStillRender(GameTestHelper helper) {
		var pistol = BuiltInRegistries.ITEM.getValue(OldRepublicMod.id("blaster_pistol"));
		var rifle = BuiltInRegistries.ITEM.getValue(OldRepublicMod.id("blaster_rifle"));
		var blade = BuiltInRegistries.ITEM.getValue(OldRepublicMod.id("vibroblade"));
		helper.assertTrue(pistol instanceof BlasterItem b && b.kind() == BlasterItem.Kind.PISTOL && pistol == EnemyItems.BLASTER_PISTOL,
				msg("blaster_pistol is the pistol BlasterItem"));
		helper.assertTrue(rifle instanceof BlasterItem b && b.kind() == BlasterItem.Kind.RIFLE && rifle == EnemyItems.BLASTER_RIFLE,
				msg("blaster_rifle is the rifle BlasterItem"));
		helper.assertTrue(blade instanceof VibroWeaponItem && blade == EnemyItems.VIBROBLADE, msg("vibroblade is a VibroWeaponItem"));
		helper.assertTrue(new ItemStack(pistol).getMaxStackSize() == 1 && new ItemStack(blade).getMaxStackSize() == 1, msg("they still stack to one"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void saberConfigOldStackDecodesWithoutSecondOrLens(GameTestHelper helper) {
		SaberConfig old = SaberConfig.DEFAULT;
		JsonObject json = SaberConfig.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow().getAsJsonObject();
		helper.assertTrue(!json.has("second") && !json.has("lens"), msg("a single-crystal saber writes neither field"));
		SaberConfig back = SaberConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.equals(old) && back.second().isEmpty() && back.lens().isEmpty(), msg("an M3 saber decodes unchanged"));

		SaberConfig staff = old.withSecond(Optional.of(new CrystalAttunement(UUID.randomUUID(), "Two", BladeColor.GREEN.rgb())))
				.withLens(Optional.of(OldRepublicMod.id("lens_honed")));
		SaberConfig staffBack = SaberConfig.CODEC.parse(JsonOps.INSTANCE, SaberConfig.CODEC.encodeStart(JsonOps.INSTANCE, staff).getOrThrow()).getOrThrow();
		helper.assertTrue(staffBack.equals(staff), msg("second and lens round-trip"));
		helper.assertTrue(staff.withColor(BladeColor.RED).second().equals(staff.second()) && staff.withColor(BladeColor.RED).lens().equals(staff.lens()),
				msg("withColor keeps the second crystal and the lens"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void abilityLocksRefuseWhileFlying(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ShipRegistry.Data before = ShipRegistry.snapshot(server);
		FakePlayer p = player(helper, new BlockPos(2, 1, 2), "flying_test");
		Entity cart = helper.spawn(EntityTypes.MINECART, new BlockPos(2, 1, 2));

		try {
			helper.assertTrue(p.startRiding(cart, true, false), msg("the player boards the stand-in body"));
			ShipRecord ship = ShipRecord.hulk(p.getUUID(), cart.getUUID(), GlobalPos.of(helper.getLevel().dimension(), cart.blockPosition()), 0f, "T-1")
					.withState(ShipState.IN_TRANSIT);
			ShipRegistry.put(server, ship);
			helper.assertTrue(AbilityLocks.refusal(p, "force_push") != null, msg("no power while the ship is in transit"));
			ShipRegistry.put(server, ship.withState(ShipState.READY));
			helper.assertTrue(AbilityLocks.refusal(p, "force_push") == null, msg("powers work aboard a READY ship"));
			p.stopRiding();
			ShipRegistry.put(server, ship.withState(ShipState.LANDING));
			helper.assertTrue(AbilityLocks.refusal(p, "force_push") == null, msg("the lock is only for riders"));
			helper.succeed();
		} finally {
			ShipRegistry.commit(server, before);
			cart.discard();
		}
	}

	@GameTest(maxTicks = 20)
	public void dangerTellTableMatches13_3(GameTestHelper helper) {
		for (int v = 0; v < 4; v++) {
			helper.assertTrue(DangerTellTable.isTell(OldRepublicMod.id("hunter_tell"), v), msg("hunter_tell " + v + " is a tell"));
		}

		helper.assertTrue(DangerTellTable.isTell(OldRepublicMod.id("droid_tell"), 0) && !DangerTellTable.isTell(OldRepublicMod.id("droid_tell"), 1)
				&& !DangerTellTable.isTell(OldRepublicMod.id("droid_tell"), 2), msg("only the droid's spin-up is a tell"));
		helper.assertTrue(DangerTellTable.isTell(OldRepublicMod.id("beast_tell"), 0) && DangerTellTable.isTell(OldRepublicMod.id("beast_tell"), 1)
				&& !DangerTellTable.isTell(OldRepublicMod.id("beast_tell"), 2), msg("the charge and the lunge are tells, the howl is not"));

		for (int v = 0; v <= 6; v++) {
			boolean tell = v == 1 || v == 3;
			helper.assertTrue(DangerTellTable.isTell(OldRepublicMod.id("kaast"), v) == tell, msg("kaast " + v + (tell ? " is" : " is not") + " a tell"));
		}

		helper.assertTrue(DangerTellTable.isTell(OldRepublicMod.id("trooper_charge"), 5) && DangerTellTable.isTell(OldRepublicMod.id("boss_grip"), 0)
				&& !DangerTellTable.isTell(OldRepublicMod.id("boss_grip"), 1), msg("the M3 rows are unchanged"));
		helper.assertTrue(!DangerTellTable.isTell(OldRepublicMod.id("kaast"), -1) && !DangerTellTable.isTell(OldRepublicMod.id("kaast"), 40),
				msg("out-of-range variants are never tells"));
		helper.succeed();
	}
}
