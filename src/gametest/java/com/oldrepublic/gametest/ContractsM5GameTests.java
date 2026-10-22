package com.oldrepublic.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.network.AbilityInputPayload;
import com.oldrepublic.ability.network.AbilityStatePayload;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.DangerTellTable;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.dialogue.DialogueRedirects;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.IdentityRules;
import com.oldrepublic.mission.MissionDefinition;
import com.oldrepublic.mission.PlanetContracts;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.planet.worldgen.PlanetWorldgen;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.travel.Destination;
import com.oldrepublic.travel.ShipBalance;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelData;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SpawnerGroups;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The Milestone 5 Phase 0 contracts (spec MILESTONE_5.md 17.1, 19.1): green before the workers
 * start. Every test is a codec, an enum or a pure rule; the new planets and orbits do not exist on
 * the game-test server.
 */
public class ContractsM5GameTests {
	private static Component msg(String text) {
		return Component.literal(text);
	}

	@GameTest(maxTicks = 20)
	public void planetAppendsKeepOrdinals(GameTestHelper helper) {
		helper.assertTrue(List.of(Planets.Planet.values()).equals(List.of(Planets.Planet.HOME, Planets.Planet.SELVAREN, Planets.Planet.VARKHAST,
				Planets.Planet.GHESSAR, Planets.Planet.TYRVANN)), msg("Ghessar and Tyrvann follow the M4 worlds"));
		helper.assertTrue(Planets.GHESSAR.identifier().equals(OldRepublicMod.id("ghessar")) && Planets.TYRVANN.identifier().equals(OldRepublicMod.id("tyrvann")),
				msg("the new planets' level keys"));

		for (Planets.Planet planet : Planets.Planet.values()) {
			helper.assertTrue(Planets.Planet.byId(planet.id()) == planet && Planets.Planet.of(planet.level()) == planet, msg("id and level round-trip: " + planet));
		}

		helper.assertTrue(PlanetSites.Site.VERSE_STONE_1.ordinal() == PlanetSites.Site.SEPULCHRE.ordinal() + 1
				&& PlanetSites.Site.values()[PlanetSites.Site.values().length - 1] == PlanetSites.Site.DETENTION_POST, msg("the new sites follow the Sepulchre"));
		helper.assertTrue(PlanetSites.planetOf(PlanetSites.Site.VERSE_STONE_2) == Planets.Planet.TYRVANN && PlanetSites.planetOf(PlanetSites.Site.HYMNAL) == Planets.Planet.TYRVANN
				&& PlanetSites.planetOf(PlanetSites.Site.DEBTOR_CAMP) == Planets.Planet.GHESSAR
				&& PlanetSites.planetOf(PlanetSites.Site.DETENTION_POST) == Planets.Planet.VARKHAST && PlanetSites.shared(PlanetSites.Site.HYMNAL)
				&& !PlanetSites.shared(PlanetSites.Site.VERSE_STONE_1), msg("each new site has its world, and only the Hymnal is shared"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void orbitKeysRoundTrip(GameTestHelper helper) {
		Set<ResourceKey<Level>> orbits = new HashSet<>();

		for (Planets.Planet planet : Planets.Planet.values()) {
			ResourceKey<Level> orbit = Planets.orbit(planet);
			helper.assertTrue(orbit.identifier().equals(OldRepublicMod.id("orbit_" + planet.id())) && orbits.add(orbit),
					msg("one oldrepublic:orbit_<id> key per world: " + planet));
			helper.assertTrue(Planets.orbitOf(orbit) == planet && Planets.isOrbit(orbit) && Planets.Planet.of(orbit) == null,
					msg("the orbit names its world and is not one: " + planet));
			helper.assertTrue(Planets.orbitOf(planet.level()) == null && !Planets.isOrbit(planet.level()), msg("a world is not an orbit: " + planet));
		}

		helper.assertTrue(Planets.orbitOf(Level.NETHER) == null && !Planets.isOrbit(Level.END), msg("the Nether and the End are not orbits"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void isPlanetCoversNewWorldsAndNotOrbits(GameTestHelper helper) {
		helper.assertTrue(Planets.planets().equals(List.of(Planets.Planet.SELVAREN, Planets.Planet.VARKHAST, Planets.Planet.GHESSAR, Planets.Planet.TYRVANN)),
				msg("every planet but the home world"));

		for (Planets.Planet planet : Planets.Planet.values()) {
			helper.assertTrue(Planets.isPlanet(planet.level()) == (planet != Planets.Planet.HOME), msg("isPlanet is every world but home: " + planet));
			helper.assertTrue(!Planets.isPlanet(Planets.orbit(planet)), msg("an orbit is not a planet: " + planet));
		}

		helper.assertTrue(!Planets.isPlanet(Level.NETHER) && !Planets.isPlanet(Level.END), msg("the Nether and the End are not planets"));
		ServerLevel home = helper.getLevel();
		helper.assertTrue(Planets.isStoryLevel(home) && Planets.isHomeWorld(home) && !Planets.isPlanet(home) && !Planets.isOrbit(home),
				msg("the test level is the home world"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void everyPlanetHasTableRows(GameTestHelper helper) {
		Set<Identifier> hubs = new HashSet<>();

		for (Planets.Planet planet : Planets.planets()) {
			Identifier hub = PlanetHubs.template(planet);
			helper.assertTrue(hub != null && SiteKind.ofTemplate(hub) == SiteKind.PLANET_HUB && hubs.add(hub), msg("a hub template of its own: " + planet));
			Destination pad = Destination.hubOf(planet);
			helper.assertTrue(pad != null && pad.planet() == planet, msg("a ship destination at the hub: " + planet));
			helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(OldRepublicMod.id("music." + planet.id())), msg("its music is a sound event: " + planet));
			String ambience = "ambient." + planet.id() + ".";
			helper.assertTrue(BuiltInRegistries.SOUND_EVENT.keySet().stream().anyMatch(id -> id.getNamespace().equals(OldRepublicMod.MOD_ID) && id.getPath().startsWith(ambience)),
					msg("an ambience bed: " + planet));
			helper.assertTrue(PlanetContracts.issuer(planet) != null && !PlanetContracts.templates(planet).isEmpty(), msg("a contract board: " + planet));
			helper.assertTrue(PlanetWorldgen.biomeTag(planet) != null, msg("a biome tag: " + planet));
			helper.assertTrue(JournalEntries.arrivalEntry(planet) != null && JournalEntries.arrivalCodex(planet) != null && JournalEntries.hubCodex(planet) != null,
					msg("the first landing's entry and codex pages: " + planet));
		}

		helper.assertTrue(PlanetHubs.template(Planets.Planet.HOME) == null && PlanetContracts.issuer(Planets.Planet.HOME) == null
				&& JournalEntries.arrivalEntry(Planets.Planet.HOME) == null && Destination.hubOf(Planets.Planet.HOME) == Destination.HOME,
				msg("the home world has its pad but no hub, board or arrival entry"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void allegianceAppends(GameTestHelper helper) {
		Allegiance[] values = Allegiance.values();
		String[] m4 = {"NONE", "CREW", "REPUBLIC", "EMPIRE", "ANCIENT", "ARCHIVE", "HUNTERS"};

		for (int i = 0; i < m4.length; i++) {
			helper.assertTrue(values[i].name().equals(m4[i]), msg("ordinal " + i + " is still " + m4[i] + ", got " + values[i]));
		}

		helper.assertTrue(values.length == 10 && values[7] == Allegiance.KAJIDIC && values[8] == Allegiance.CLAN && values[9] == Allegiance.HYMN,
				msg("the kajidic, the clan and the Hymn are appended in that order"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void controlKindsDistinct(GameTestHelper helper) {
		helper.assertTrue(ControlEffects.FEAR.equals("fear") && ControlEffects.STASIS.equals("stasis") && ControlEffects.PACIFY.equals("pacify"),
				msg("the M5 kinds are fear, stasis and pacify"));
		Set<String> kinds = Set.of(ControlEffects.STAGGER, ControlEffects.SLOW, ControlEffects.DISARM, ControlEffects.SHOCK, ControlEffects.HOLD,
				ControlEffects.FEAR, ControlEffects.STASIS, ControlEffects.PACIFY);
		helper.assertTrue(kinds.size() == 8, msg("no kind shares another's name"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void skillDataPadsOldSavesToEightSlots(GameTestHelper helper) {
		helper.assertTrue(SkillData.SLOT_COUNT == 8 && SkillData.BAR_SLOTS == 4, msg("two bars of four"));
		JsonObject json = new JsonObject();
		JsonArray slots = new JsonArray();
		slots.add("force_push");
		slots.add("");
		slots.add("force_pull");
		slots.add("");
		json.add("slots", slots);
		json.addProperty("open_slots", 4);
		SkillData data = SkillData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(data.slots.size() == SkillData.SLOT_COUNT, msg("a four-slot save loads with eight slots, got " + data.slots.size()));
		helper.assertTrue(data.slot(0).equals("force_push") && data.slot(2).equals("force_pull"), msg("the first bar keeps its powers"));

		for (int i = SkillData.BAR_SLOTS; i < SkillData.SLOT_COUNT; i++) {
			helper.assertTrue(data.slot(i).isEmpty() && !data.isOpen(i), msg("second-bar slot " + i + " is empty and closed"));
		}

		helper.assertTrue(data.openSlots == 4 && data.isOpen(3), msg("the first bar keeps its four open slots"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void skillDataWithoutSecondBarDecodes(GameTestHelper helper) {
		SkillData data = SkillData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
		helper.assertTrue(data.openSlots == 3 && data.openSlots2 == 0, msg("an empty save has three open slots and no second bar"));
		data.openSlots2 = 2;
		JsonObject json = SkillData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().getAsJsonObject();
		helper.assertTrue(json.has("open_slots_2") && json.get("open_slots_2").getAsInt() == 2, msg("open_slots_2 is written"));
		SkillData back = SkillData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.openSlots2 == 2 && back.isOpen(4) && back.isOpen(5) && !back.isOpen(6) && !back.isOpen(7),
				msg("two open second-bar slots are slots 4 and 5"));
		helper.assertTrue(back.copy().openSlots2 == 2, msg("copy keeps the second bar"));
		json.remove("open_slots_2");
		helper.assertTrue(SkillData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().openSlots2 == 0, msg("a save without the field has no second bar"));
		helper.assertTrue(!back.isOpen(-1) && !back.isOpen(SkillData.SLOT_COUNT), msg("slots outside 0 to 7 are never open"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void abilityPayloadsCarrySlotsZeroToSeven(GameTestHelper helper) {
		helper.assertTrue(AbilityInputPayload.SENSE_SLOT == SkillData.SLOT_COUNT, msg("Force Sense keeps the slot just past the eight"));

		for (int slot = 0; slot <= AbilityInputPayload.SENSE_SLOT; slot++) {
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());

			try {
				AbilityInputPayload.CODEC.encode(buf, new AbilityInputPayload(slot, AbilityAction.RELEASE));
				AbilityInputPayload input = AbilityInputPayload.CODEC.decode(buf);
				helper.assertTrue(input.slot() == slot && input.action() == AbilityAction.RELEASE, msg("ability_input carries slot " + slot));
				AbilityStatePayload.CODEC.encode(buf, new AbilityStatePayload(slot, "force_push", true));
				AbilityStatePayload state = AbilityStatePayload.CODEC.decode(buf);
				helper.assertTrue(state.slot() == slot && state.abilityId().equals("force_push") && state.active(), msg("ability_state carries slot " + slot));
			} finally {
				buf.release();
			}
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void saberConfigWithoutLengthDecodes(GameTestHelper helper) {
		SaberConfig old = SaberConfig.DEFAULT;
		JsonObject json = SaberConfig.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow().getAsJsonObject();
		helper.assertTrue(!json.has("length"), msg("a standard blade writes no length"));
		SaberConfig back = SaberConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.equals(old) && back.length() == SaberConfig.LENGTH_STANDARD, msg("an M4 saber decodes as standard"));

		SaberConfig longBlade = old.withLength(SaberConfig.LENGTH_LONG);
		JsonObject longJson = SaberConfig.CODEC.encodeStart(JsonOps.INSTANCE, longBlade).getOrThrow().getAsJsonObject();
		helper.assertTrue(longJson.has("length") && SaberConfig.CODEC.parse(JsonOps.INSTANCE, longJson).getOrThrow().equals(longBlade),
				msg("a long blade round-trips"));
		helper.assertTrue(longBlade.withColor(BladeColor.RED).length() == SaberConfig.LENGTH_LONG
				&& longBlade.withLens(java.util.Optional.of(OldRepublicMod.id("lens_honed"))).length() == SaberConfig.LENGTH_LONG,
				msg("withColor and withLens keep the length"));
		helper.assertTrue(old.withLength(7).length() == SaberConfig.LENGTH_LONG && old.withLength(-2).length() == SaberConfig.LENGTH_STANDARD,
				msg("the length clamps to 0 to 2"));
		ByteBuf buf = Unpooled.buffer();

		try {
			SaberConfig.STREAM_CODEC.encode(buf, longBlade.withLength(SaberConfig.LENGTH_SHORT));
			helper.assertTrue(SaberConfig.STREAM_CODEC.decode(buf).length() == SaberConfig.LENGTH_SHORT, msg("the stream codec carries the length"));
		} finally {
			buf.release();
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void crystalAttunementWithoutTuningDecodes(GameTestHelper helper) {
		CrystalAttunement plain = new CrystalAttunement(UUID.randomUUID(), "Tester", BladeColor.BLUE.rgb());
		JsonObject json = CrystalAttunement.CODEC.encodeStart(JsonOps.INSTANCE, plain).getOrThrow().getAsJsonObject();
		helper.assertTrue(!json.has("tuning"), msg("an untuned crystal writes no tuning"));
		CrystalAttunement back = CrystalAttunement.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.equals(plain) && back.tuning().isEmpty(), msg("an M4 crystal decodes untuned"));

		CrystalAttunement tuned = plain.withTuning("resonant");
		helper.assertTrue(CrystalAttunement.CODEC.parse(JsonOps.INSTANCE, CrystalAttunement.CODEC.encodeStart(JsonOps.INSTANCE, tuned).getOrThrow())
				.getOrThrow().equals(tuned), msg("a tuning round-trips"));
		ByteBuf buf = Unpooled.buffer();

		try {
			CrystalAttunement.STREAM_CODEC.encode(buf, tuned);
			helper.assertTrue(CrystalAttunement.STREAM_CODEC.decode(buf).equals(tuned), msg("the stream codec carries the tuning"));
		} finally {
			buf.release();
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void qualityAcceptsLegendary(GameTestHelper helper) {
		Quality[] values = Quality.values();
		helper.assertTrue(values.length == 4 && values[0] == Quality.STANDARD && values[1] == Quality.FINE && values[2] == Quality.MASTERWORK
				&& values[3] == Quality.LEGENDARY, msg("Legendary is the fourth tier, after the M4 three"));
		helper.assertTrue(M4Components.QUALITY.codecOrThrow().parse(JsonOps.INSTANCE, new JsonPrimitive(3)).result().orElse(-1) == 3,
				msg("the quality component accepts 3"));
		helper.assertTrue(M4Components.QUALITY.codecOrThrow().parse(JsonOps.INSTANCE, new JsonPrimitive(4)).result().isEmpty(),
				msg("the quality component refuses 4"));
		helper.assertTrue(Quality.LEGENDARY.weapon() >= Quality.MASTERWORK.weapon() && Quality.LEGENDARY.armour() >= Quality.MASTERWORK.armour()
				&& Quality.LEGENDARY.durability() >= Quality.MASTERWORK.durability(), msg("Legendary is never below Masterwork"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void qualityKeepsThreeValueArrays(GameTestHelper helper) {
		Quality[] values = Quality.values();
		float[] weapon = new float[values.length];
		float[] armour = new float[values.length];
		float[] durability = new float[values.length];

		for (Quality q : values) {
			weapon[q.ordinal()] = q.weapon();
			armour[q.ordinal()] = q.armour();
			durability[q.ordinal()] = q.durability();
		}

		// Synchronous: the multipliers are restored before any other test runs a tick.
		try {
			Quality.configure(new float[] {1.0f, 1.1f, 1.2f}, new float[] {1.0f, 1.2f, 1.3f}, new float[] {1.0f, 1.3f, 1.6f});
			helper.assertTrue(Quality.FINE.weapon() == 1.1f && Quality.MASTERWORK.armour() == 1.3f && Quality.MASTERWORK.durability() == 1.6f,
					msg("three values set the M4 tiers"));
			helper.assertTrue(Quality.LEGENDARY.weapon() == 1.18f && Quality.LEGENDARY.armour() == 1.40f && Quality.LEGENDARY.durability() == 1.75f,
					msg("three values keep Legendary's built-in 1.18, 1.40 and 1.75"));
			Quality.configure(new float[] {1.0f, 1.1f, 1.2f, 1.3f}, new float[] {1.0f, 1.2f, 1.3f, 1.5f}, new float[] {1.0f, 1.3f, 1.6f, 1.9f});
			helper.assertTrue(Quality.LEGENDARY.weapon() == 1.3f && Quality.LEGENDARY.armour() == 1.5f && Quality.LEGENDARY.durability() == 1.9f,
					msg("four values set Legendary too"));
			Quality.configure(new float[] {2.0f, 2.0f}, new float[] {2.0f}, new float[] {});
			helper.assertTrue(Quality.FINE.weapon() == 1.1f && Quality.LEGENDARY.armour() == 1.5f, msg("other lengths are ignored"));
		} finally {
			Quality.configure(weapon, armour, durability);
		}

		helper.assertTrue(Quality.LEGENDARY.weapon() == weapon[3] && Quality.FINE.armour() == armour[1], msg("the multipliers are restored"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void lensQualityAcceptsLegendary(GameTestHelper helper) {
		helper.assertTrue(SaberComponents.LENS_QUALITY.codecOrThrow().parse(JsonOps.INSTANCE, new JsonPrimitive(3)).result().orElse(-1) == 3,
				msg("the lens quality accepts 3"));
		helper.assertTrue(SaberComponents.LENS_QUALITY.codecOrThrow().parse(JsonOps.INSTANCE, new JsonPrimitive(4)).result().isEmpty(),
				msg("the lens quality refuses 4"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void destinationAppendsMapToPlanets(GameTestHelper helper) {
		helper.assertTrue(List.of(Destination.values()).equals(List.of(Destination.HOME, Destination.GREENWATCH, Destination.PROVING, Destination.HUNTING_CAMP,
				Destination.SALTGATE, Destination.HOLDFAST)), msg("Saltgate and the holdfast follow the M4 four"));
		helper.assertTrue(Destination.SALTGATE.planet() == Planets.Planet.GHESSAR && Destination.HOLDFAST.planet() == Planets.Planet.TYRVANN
				&& Destination.byId("saltgate") == Destination.SALTGATE && Destination.byId("holdfast") == Destination.HOLDFAST,
				msg("the new destinations are on Ghessar and Tyrvann"));
		helper.assertTrue(Destination.hubOf(Planets.Planet.HOME) == Destination.HOME && Destination.hubOf(Planets.Planet.SELVAREN) == Destination.GREENWATCH
				&& Destination.hubOf(Planets.Planet.VARKHAST) == Destination.PROVING && Destination.hubOf(Planets.Planet.GHESSAR) == Destination.SALTGATE
				&& Destination.hubOf(Planets.Planet.TYRVANN) == Destination.HOLDFAST, msg("every planet has its hub destination"));

		for (Destination d : Destination.values()) {
			helper.assertTrue((d.milestone() == 4) == (d.ordinal() < 4) && d.hiddenWhileLocked() == (d.milestone() > 4),
					msg("only the M5 destinations stay off a locked Nav tab: " + d));
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void flyingCountsAsFlying(GameTestHelper helper) {
		ShipState[] states = ShipState.values();
		helper.assertTrue(states.length == 8 && states[7] == ShipState.FLYING && ShipState.FLYING.id().equals("flying"), msg("FLYING is appended"));
		helper.assertTrue(ShipState.FLYING.flying() && ShipState.LAUNCHING.flying() && ShipState.IN_TRANSIT.flying() && ShipState.LANDING.flying()
				&& !ShipState.HULK.flying() && !ShipState.GROUNDED.flying() && !ShipState.READY.flying() && !ShipState.DISABLED.flying(),
				msg("flying() is the M4 three and FLYING"));
		TravelService.Refusal[] refusals = TravelService.Refusal.values();
		int n = refusals.length;
		helper.assertTrue(refusals[n - 5] == TravelService.Refusal.UNAVAILABLE && refusals[n - 4] == TravelService.Refusal.NO_HELM
				&& refusals[n - 3] == TravelService.Refusal.BLOCKADED && refusals[n - 2] == TravelService.Refusal.NO_ROOM
				&& refusals[n - 1] == TravelService.Refusal.UNDER_FIRE, msg("the four refusals follow UNAVAILABLE"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void upgradeBitsAvoidSocketsPlatesAndPaint(GameTestHelper helper) {
		int[] m5 = {ShipRecord.UPGRADE_CANNONS, ShipRecord.UPGRADE_SHIELD, ShipRecord.UPGRADE_THRUSTERS, ShipRecord.UPGRADE_MISSILES,
				ShipRecord.CLASS_INTERCEPTOR, ShipRecord.PART_SPINDLE_CAPACITOR, ShipRecord.PART_HYMN_DRIVE};
		int all = 0;

		for (int bit : m5) {
			helper.assertTrue(Integer.bitCount(bit) == 1 && (all & bit) == 0, msg("one bit each, none twice: " + Integer.toBinaryString(bit)));
			all |= bit;
		}

		int m4 = ShipRecord.UPGRADE_SEAT | ShipRecord.UPGRADE_CARGO | ShipRecord.UPGRADE_PLATING | ShipRecord.UPGRADE_TANK;
		ShipBalance b = new ShipBalance();
		ShipRecord r = ShipRecord.hulk(UUID.randomUUID(), UUID.randomUUID(), GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), 0f, "T-1").withUpgrades(all);
		helper.assertTrue((all & m4) == 0 && ShipRules.plates(r) == 0 && ShipRules.paint(r.upgrades()) == 0
				&& !ShipRules.socketDone(r, ShipRules.SOCKET_CORE_INDEX, b) && !ShipRules.socketDone(r, ShipRules.SOCKET_MOTIVATOR_INDEX, b)
				&& !ShipRules.socketDone(r, ShipRules.SOCKET_NAV_INDEX, b), msg("no M5 bit reads as an M4 upgrade, socket, plate or paint"));
		helper.assertTrue(Ships.hasWeapons(r) && Ships.interceptor(r) && !Ships.hasWeapons(r.withUpgrades(m4)) && !Ships.interceptor(r.withUpgrades(m4)),
				msg("hasWeapons and interceptor read their bits"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void travelDataWithoutHelmDecodes(GameTestHelper helper) {
		TravelData m4 = TravelData.EMPTY.withUnlocked(Destination.GREENWATCH, true).withRecallDay(3L);
		JsonObject json = TravelData.CODEC.encodeStart(JsonOps.INSTANCE, m4).getOrThrow().getAsJsonObject();
		json.remove("helm");
		TravelData old = TravelData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(old.equals(m4) && !old.helm(), msg("an M4 save decodes without the helm, got " + old));
		TravelData helm = old.withHelm(true);
		TravelData back = TravelData.CODEC.parse(JsonOps.INSTANCE, TravelData.CODEC.encodeStart(JsonOps.INSTANCE, helm).getOrThrow()).getOrThrow();
		helper.assertTrue(back.equals(helm) && back.withRecallDay(4L).withTowDay(5L).withUnlocked(Destination.PROVING, true).helm(),
				msg("the helm round-trips and the with* methods keep it"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void storyDataWithoutChapter5OrTurningDecodes(GameTestHelper helper) {
		helper.assertTrue(List.of(ChapterFiveStage.values()).equals(List.of(ChapterFiveStage.NOT_STARTED, ChapterFiveStage.WAITING, ChapterFiveStage.CALLED,
				ChapterFiveStage.SALTGATE, ChapterFiveStage.THE_FACTOR, ChapterFiveStage.ARMED, ChapterFiveStage.BLOCKADE, ChapterFiveStage.TYRVANN,
				ChapterFiveStage.VERSES, ChapterFiveStage.THE_HYMNAL, ChapterFiveStage.AFTERMATH, ChapterFiveStage.COMPLETE)), msg("the 6.1 stages, in order"));
		helper.assertTrue(List.of(TurningKind.values()).equals(List.of(TurningKind.NONE, TurningKind.FALL, TurningKind.RETURN))
				&& List.of(TurningStage.values()).equals(List.of(TurningStage.NONE, TurningStage.OFFERED, TurningStage.TASK, TurningStage.CONFRONTATION,
						TurningStage.RITE, TurningStage.COMPLETE)), msg("the 8.3 kinds and steps, in order"));

		JsonObject json = StoryData.CODEC.encodeStart(JsonOps.INSTANCE, new StoryData()).getOrThrow().getAsJsonObject();
		json.remove("chapter5");
		json.remove("turning");
		StoryData old = StoryData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(old.chapter5.equals(StoryData.Chapter5.NEW) && old.turning.equals(StoryData.Turning.NEW),
				msg("an M4 save decodes to a new Chapter Five and no turning"));
		helper.assertTrue(old.chapter5.stage() == ChapterFiveStage.NOT_STARTED && old.turning.kind() == TurningKind.NONE
				&& old.turning.stage() == TurningStage.NONE, msg("nothing has started"));

		StoryData data = new StoryData();
		data.chapter5 = data.chapter5.withStage(ChapterFiveStage.VERSES, 2400L).withWay(ChapterFive.Way.CLAN).withDask(StoryData.Chapter5.DASK_CLAN)
				.withVerses(0b101);
		data.turning = data.turning.withStage(TurningKind.FALL, TurningStage.TASK).withTask(2);
		StoryData back = StoryData.CODEC.parse(JsonOps.INSTANCE, StoryData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
		helper.assertTrue(back.chapter5.equals(data.chapter5) && back.turning.equals(data.turning), msg("both records round-trip, got " + back.chapter5
				+ " and " + back.turning));
		StoryData copy = data.copy();
		helper.assertTrue(copy.chapter5.equals(data.chapter5) && copy.turning.equals(data.turning), msg("copy keeps both records"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void journalAppendsAfterChapterFour(GameTestHelper helper) {
		List<String> entries = JournalEntries.ENTRIES;
		int reportC4 = entries.indexOf(JournalEntries.REPORT_C4);
		int factorAsks = entries.indexOf(JournalEntries.THE_FACTOR_ASKS);
		helper.assertTrue(reportC4 >= 0 && factorAsks == reportC4 + 1, msg("Chapter Five's entries follow Chapter Four's last"));
		helper.assertTrue(entries.indexOf(JournalEntries.REPORT_C5) > factorAsks && entries.indexOf(JournalEntries.THE_FALL_OFFERED) > entries.indexOf(JournalEntries.REPORT_C5)
				&& entries.get(entries.size() - 1).equals(JournalEntries.THE_FIRST_CANTOR_FALLS), msg("the paths, ranks and raid entries close the list"));
		helper.assertTrue(Set.copyOf(entries).size() == entries.size() && Set.copyOf(JournalEntries.CODEX).size() == JournalEntries.CODEX.size(),
				msg("no id twice"));
		helper.assertTrue(JournalEntries.CHAPTER_FIRSTS.size() == 5 && JournalEntries.CHAPTER_FIRSTS.get(4).equals(JournalEntries.THE_FACTOR_ASKS),
				msg("Chapter Five's header sits above the factor's ask"));
		helper.assertTrue(QuestEffects.LESSON_CHAPTER_FIVE == 11 && QuestEffects.LESSON_TURNING == 12 && QuestEffects.LESSON_RANK_FOUR == 13
				&& QuestEffects.LESSON_LEGENDARY == 14 && QuestEffects.LESSON_LONG_HYMN == 15, msg("the banners are 11 to 15"));
		helper.assertTrue(JournalEntries.SELVAREN.equals(JournalEntries.arrivalEntry(Planets.Planet.SELVAREN))
				&& JournalEntries.VARKHAST.equals(JournalEntries.arrivalEntry(Planets.Planet.VARKHAST))
				&& JournalEntries.CODEX_THE_WATCH_ON_SELVAREN.equals(JournalEntries.hubCodex(Planets.Planet.SELVAREN))
				&& JournalEntries.CODEX_THE_PROVING_CODEX.equals(JournalEntries.hubCodex(Planets.Planet.VARKHAST)), msg("the M4 arrival rows are unchanged"));
		helper.assertTrue(JournalEntries.SALTGATE.equals(JournalEntries.arrivalEntry(Planets.Planet.GHESSAR))
				&& JournalEntries.TYRVANN.equals(JournalEntries.arrivalEntry(Planets.Planet.TYRVANN))
				&& JournalEntries.CODEX_THE_VESSBA_KAJIDIC.equals(JournalEntries.hubCodex(Planets.Planet.GHESSAR))
				&& JournalEntries.CODEX_CLAN_HASK.equals(JournalEntries.hubCodex(Planets.Planet.TYRVANN)), msg("the new planets have their 3.1 rows"));
		helper.assertTrue(JournalEntries.arrivalEntry(Planets.Planet.HOME) == null && JournalEntries.hubCodex(Planets.Planet.HOME) == null,
				msg("the home world has no arrival row"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void factionAppendsAndStandingRowsFollowValues(GameTestHelper helper) {
		helper.assertTrue(List.of(Faction.values()).equals(List.of(Faction.JEDI_ORDER, Faction.SITH_EMPIRE, Faction.FRONTIER, Faction.REPUBLIC,
				Faction.VESSBA_KAJIDIC, Faction.CLAN_HASK)), msg("the kajidic and the clan are appended"));
		helper.assertTrue(Faction.byId("vessba_kajidic") == Faction.VESSBA_KAJIDIC && Faction.byId("clan_hask") == Faction.CLAN_HASK,
				msg("the new ids read back"));
		List<Faction> m4 = List.of(Faction.JEDI_ORDER, Faction.SITH_EMPIRE, Faction.REPUBLIC, Faction.FRONTIER);
		IdentityData data = new IdentityData();
		helper.assertTrue(IdentityRules.standingRows(data).equals(m4) && !IdentityRules.knows(data, Faction.VESSBA_KAJIDIC),
				msg("an M4 save shows its four rows in the M4 order"));
		data.choices.add(IdentityRules.metChoice(Faction.CLAN_HASK));
		helper.assertTrue(IdentityRules.standingRows(data).equals(List.of(Faction.JEDI_ORDER, Faction.SITH_EMPIRE, Faction.REPUBLIC, Faction.FRONTIER,
				Faction.CLAN_HASK)), msg("a met faction gets its row"));
		data.choices.add(IdentityRules.metChoice(Faction.VESSBA_KAJIDIC));
		helper.assertTrue(IdentityRules.standingRows(data).equals(List.of(Faction.JEDI_ORDER, Faction.SITH_EMPIRE, Faction.REPUBLIC, Faction.FRONTIER,
				Faction.VESSBA_KAJIDIC, Faction.CLAN_HASK)), msg("later factions follow in values() order"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void missionObjectivesAppend(GameTestHelper helper) {
		MissionDefinition.Objective[] values = MissionDefinition.Objective.values();
		String[] m4 = {"CLEAR", "RECOVER", "RESCUE", "SURVEY", "SECURE", "INTERCEPT", "YIELD", "ESCORT", "SPAR"};

		for (int i = 0; i < m4.length; i++) {
			helper.assertTrue(values[i].name().equals(m4[i]), msg("objective " + i + " is still " + m4[i]));
		}

		helper.assertTrue(values.length == 11 && values[9] == MissionDefinition.Objective.SPACE_RUN && values[10] == MissionDefinition.Objective.SPACE_CLEAR,
				msg("the space objectives are appended"));
		helper.assertTrue(MissionDefinition.Objective.SPACE_RUN.space() && MissionDefinition.Objective.SPACE_CLEAR.space()
				&& !MissionDefinition.Objective.SPACE_RUN.combat() && !MissionDefinition.Objective.SPACE_CLEAR.combat()
				&& !MissionDefinition.Objective.CLEAR.space(), msg("space objectives are space, not ground combat"));
		helper.assertTrue(MissionDefinition.Objective.SPACE_RUN.id().equals("space_run") && MissionDefinition.Objective.SPACE_CLEAR.id().equals("space_clear"),
				msg("their ids are space_run and space_clear"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void bossDispatchById(GameTestHelper helper) {
		helper.assertTrue(QuestHooks.bossChapter("kaast") == 4, msg("Kaast reports to Chapter Four"));
		helper.assertTrue(QuestHooks.bossChapter("ulvaine") == 5, msg("Ulvaine reports to Chapter Five"));
		helper.assertTrue(QuestHooks.bossChapter("collector") == 3 && QuestHooks.bossChapter("first_cantor") == 3,
				msg("every other boss reports to Chapter Three"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void dialogueRedirectApplies(GameTestHelper helper) {
		FakePlayer p = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "redirect_test"));
		String from = "contracts_m5_redirect_from";
		String to = "contracts_m5_redirect_to";
		String foreign = "othermod:contracts_m5_talk";

		try {
			DialogueRedirects.register(from, (player, id) -> id.equals(from) ? to : null);
			DialogueRedirects.register(to, (player, id) -> "contracts_m5_third");
			helper.assertTrue(DialogueRedirects.resolve(p, OldRepublicMod.id(from)).equals(OldRepublicMod.id(to)),
					msg("the redirect's answer opens instead, one hop only"));
			helper.assertTrue(DialogueRedirects.resolve(p, OldRepublicMod.id("contracts_m5_unredirected")).equals(OldRepublicMod.id("contracts_m5_unredirected")),
					msg("a dialogue without a redirect opens unchanged"));
			boolean threw = false;

			try {
				DialogueRedirects.register(from, (player, id) -> null);
			} catch (IllegalStateException e) {
				threw = true;
			}

			helper.assertTrue(threw, msg("a second redirect for one dialogue is refused"));
			String[] seen = new String[1];
			DialogueRedirects.register(foreign, (player, id) -> {
				seen[0] = id;
				return null;
			});
			Identifier foreignId = Identifier.fromNamespaceAndPath("othermod", "contracts_m5_talk");
			helper.assertTrue(DialogueRedirects.resolve(p, foreignId).equals(foreignId) && foreign.equals(seen[0]),
					msg("another namespace's dialogue is named in full, and null opens it unchanged"));
			helper.succeed();
		} finally {
			DialogueRedirects.unregister(from);
			DialogueRedirects.unregister(to);
			DialogueRedirects.unregister(foreign);
		}
	}

	@GameTest(maxTicks = 20)
	public void spawnerGroupsRegister(GameTestHelper helper) {
		String id = "contracts_m5_group";
		SpawnerGroups.Group group = new SpawnerGroups.Group() {
			@Override
			public int size(int count, BlockPos pos) {
				return count > 0 ? count : 4;
			}

			@Override
			public List<LivingEntity> spawn(ServerLevel level, BlockPos at, int size, boolean bound, @Nullable UUID owner) {
				return List.of();
			}
		};
		helper.assertTrue(!SpawnerGroups.known(id) && SpawnerGroups.size(id, 0, BlockPos.ZERO) == 0, msg("an unregistered group is unknown"));

		try {
			SpawnerGroups.register(id, group);
			helper.assertTrue(SpawnerGroups.known(id) && SpawnerGroups.size(id, 0, BlockPos.ZERO) == 4 && SpawnerGroups.size(id, 2, BlockPos.ZERO) == 2,
					msg("a registered group is known and sizes itself"));
			helper.assertTrue(SpawnerGroups.spawn(helper.getLevel(), BlockPos.ZERO, id, 0, false, null).isEmpty(), msg("and spawns through its own code"));
			boolean twice = false;
			boolean builtIn = false;

			try {
				SpawnerGroups.register(id, group);
			} catch (IllegalArgumentException e) {
				twice = true;
			}

			try {
				SpawnerGroups.register(SpawnerGroups.DIG_TEAM, group);
			} catch (IllegalArgumentException e) {
				builtIn = true;
			}

			helper.assertTrue(twice && builtIn, msg("a second registration and a built-in id are refused"));
		} finally {
			SpawnerGroups.unregister(id);
		}

		helper.assertTrue(!SpawnerGroups.known(id) && SpawnerGroups.known(SpawnerGroups.DIG_TEAM), msg("unregistering leaves the built-in groups"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void everyM5TemplateMapsToASiteKind(GameTestHelper helper) {
		// Every template of 18.1.
		List<String> templates = List.of("hub/saltgate", "hub/holdfast", "planet/crashed_freighter", "planet/kajidic_post", "planet/hymn_shrine",
				"planet/mire_grave", "site/debtor_camp", "site/verse_stone", "site/hymnal_arch", "site/hymnal", "site/detention_post", "station/spindle");

		for (String path : templates) {
			helper.assertTrue(SiteKind.ofTemplate(OldRepublicMod.id(path)) != null, msg("a site kind for " + path));
		}

		// Milestone 6 appends the story camps and the raider stronghold after the Spindle (MILESTONE_6.md 7.1).
		helper.assertTrue(SiteKind.CRASHED_FREIGHTER.ordinal() == SiteKind.SHIP_CRADLE.ordinal() + 1
				&& SiteKind.STORY_CAMP.ordinal() == SiteKind.DERELICT_STATION.ordinal() + 1, msg("the new kinds follow the ship cradle"));
		helper.assertTrue(SiteKind.ofTemplate(PlanetHubs.SALTGATE) == SiteKind.PLANET_HUB && SiteKind.ofTemplate(PlanetHubs.HOLDFAST) == SiteKind.PLANET_HUB
				&& SiteKind.ofTemplate(OldRepublicMod.id("site/hymnal_arch")) == SiteKind.HYMNAL
				&& SiteKind.ofTemplate(OldRepublicMod.id("station/spindle")) == SiteKind.DERELICT_STATION
				&& SiteKind.ofTemplate(OldRepublicMod.id("ship/beacon_pad")) == null, msg("the hubs, the Hymnal's arch and the Spindle map as 18.1 says"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void dangerTellTableMatches15_3(GameTestHelper helper) {
		assertTells(helper, "scour", 5, 1, 4);
		assertTells(helper, "hymnsworn", 3, 1);
		assertTells(helper, "stormback", 4, 1, 3);
		assertTells(helper, "hask_warrior", 4, 0, 3);
		assertTells(helper, "ulvaine", 9, 1, 4, 6, 7);
		assertTells(helper, "first_cantor", 13, 1, 3, 6, 9, 11);
		helper.assertTrue(DangerTellTable.isTell(OldRepublicMod.id("kaast"), 1) && !DangerTellTable.isTell(OldRepublicMod.id("kaast"), 0),
				msg("the M4 rows are unchanged"));
		helper.succeed();
	}

	/** Variants 0 to {@code upTo} of {@code effect}: exactly {@code tells} are tells. */
	private static void assertTells(GameTestHelper helper, String effect, int upTo, int... tells) {
		for (int v = 0; v <= upTo; v++) {
			boolean tell = false;

			for (int t : tells) {
				tell |= t == v;
			}

			helper.assertTrue(DangerTellTable.isTell(OldRepublicMod.id(effect), v) == tell, msg(effect + " " + v + (tell ? " is" : " is not") + " a tell"));
		}
	}
}
