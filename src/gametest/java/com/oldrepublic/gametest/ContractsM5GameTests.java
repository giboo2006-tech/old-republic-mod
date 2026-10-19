package com.oldrepublic.gametest;

import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.network.AbilityInputPayload;
import com.oldrepublic.ability.network.AbilityStatePayload;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.DangerTellTable;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.skill.SkillData;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

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
