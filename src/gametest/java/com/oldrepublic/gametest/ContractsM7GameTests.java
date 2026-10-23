package com.oldrepublic.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.melee.VibroWeaponItem;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.registry.M7Items;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.HiltMaterials;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.HiltSet;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberStats;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * The Milestone 7 Phase 0 contracts (spec MILESTONE_7.md 12.1, 14): green before the workers start. The appended parts,
 * materials and kinds keep every older ordinal; the materials ride the saber's config, a loose part's component and
 * the bench's assembly round trip; an all-steel hilt keeps today's numbers.
 */
public class ContractsM7GameTests {
	/** Every Milestone 7 sound event (section 11). */
	private static final List<String> SOUNDS = List.of("workbench.forge", "workbench.study",
			"saber.vanguard.ignite", "saber.vanguard.hum", "saber.vanguard.retract", "saber.warden.ignite", "saber.warden.hum", "saber.warden.retract",
			"saber.watchman.ignite", "saber.watchman.hum", "saber.watchman.retract", "saber.penitent.ignite", "saber.penitent.hum",
			"saber.penitent.retract", "saber.choir.ignite", "saber.choir.hum", "saber.choir.retract", "saber.set_pulse", "saber.set_mark",
			"vibro_axe.swing", "vibro_axe.sunder", "force_pike.charge", "force_pike.stun", "blaster.scatter.fire", "blaster.carbine.fire");

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static CrystalAttunement crystal(UUID owner) {
		return new CrystalAttunement(owner, "Tester", BladeColor.BLUE.rgb());
	}

	@GameTest(maxTicks = 20)
	public void hiltPartsAppendKeepOrdinals(GameTestHelper helper) {
		helper.assertTrue(List.of(Emitter.values()).equals(List.of(Emitter.GUARDIAN, Emitter.DUELIST, Emitter.RELIQUARY, Emitter.CANTOR, Emitter.VANGUARD,
				Emitter.WARDEN, Emitter.WATCHMAN, Emitter.PENITENT, Emitter.CHOIR)), msg("the five emitters follow the Cantor's"));
		helper.assertTrue(List.of(Grip.values()).equals(List.of(Grip.WRAPPED, Grip.RIDGED, Grip.STAFF, Grip.SUNFORGED, Grip.BANDED, Grip.KNURLED,
				Grip.FLUTED, Grip.RELIQUARY, Grip.WATCHMAN, Grip.PENITENT, Grip.CHOIR)), msg("the seven grips follow the Sunforged"));
		helper.assertTrue(List.of(Pommel.values()).equals(List.of(Pommel.WEIGHTED, Pommel.BALANCED, Pommel.ARCHIVIST, Pommel.HASK, Pommel.RINGED,
				Pommel.DOMED, Pommel.WATCHMAN, Pommel.PENITENT, Pommel.CHOIR)), msg("the five pommels follow the Hask"));

		for (HiltParts.Part[] group : new HiltParts.Part[][] {Emitter.values(), Grip.values(), Pommel.values()}) {
			for (HiltParts.Part part : group) {
				HiltParts.Slot slot = HiltParts.slot(part);
				helper.assertTrue(part.itemId().equals(slot.id() + "_" + part.id()), msg("a part's item id is its slot and id: " + part.itemId()));
				helper.assertTrue(SaberItems.hasItem(part) && SaberItems.partItem(part).part() == part
						&& BuiltInRegistries.ITEM.getKey(SaberItems.partItem(part)).equals(OldRepublicMod.id(part.itemId())),
						msg("every part has its own item: " + part.itemId()));
			}
		}

		helper.assertTrue(HiltParts.Slot.EMITTER.id().equals("emitter") && HiltParts.slot(Grip.CHOIR) == HiltParts.Slot.GRIP
				&& HiltParts.slot(Pommel.CHOIR) == HiltParts.Slot.POMMEL && HiltParts.slot(Emitter.CHOIR) == HiltParts.Slot.EMITTER,
				msg("the slots of the Choir parts"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void hiltMaterialLadder(GameTestHelper helper) {
		HiltMaterial[] values = HiltMaterial.values();
		String[] ids = {"steel", "durasteel", "starfall", "aurelite", "duskglass", "sunstone", "clan_iron", "hymnsteel"};
		int[] tiers = {0, 1, 2, 3, 3, 4, 4, 5};
		int[] tints = {0xE6E8EC, 0xB4C0CE, 0x8C8496, 0xE0C878, 0x7C6C9C, 0xE09A4C, 0x5C6676, 0xCCC2E8};
		helper.assertTrue(values.length == ids.length, msg("eight materials, got " + values.length));

		for (int i = 0; i < values.length; i++) {
			HiltMaterial m = values[i];
			helper.assertTrue(m.id().equals(ids[i]) && m.tier() == tiers[i] && m.tint() == tints[i], msg("3.1's row " + i + ": " + m));
			helper.assertTrue(HiltMaterial.byId(m.id()) == m, msg("byId round-trips " + m));
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(m.itemId()), msg("a unit of " + m + " is a registered item: " + m.itemId()));
			helper.assertTrue(HiltMaterial.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive(m.id())).getOrThrow() == m, msg("the codec reads " + m));
		}

		helper.assertTrue(HiltMaterial.byId("unobtainium") == null, msg("byId knows only the ladder"));
		helper.assertTrue(HiltMaterial.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("unobtainium")).getOrThrow() == HiltMaterial.STEEL,
				msg("an unknown id decodes as steel"));
		ByteBuf buf = Unpooled.buffer();

		try {
			for (HiltMaterial m : values) {
				HiltMaterial.STREAM_CODEC.encode(buf, m);
				helper.assertTrue(HiltMaterial.STREAM_CODEC.decode(buf) == m, msg("the stream codec carries " + m));
			}
		} finally {
			buf.release();
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void hiltMaterialsCodec(GameTestHelper helper) {
		JsonElement steel = HiltMaterials.CODEC.encodeStart(JsonOps.INSTANCE, HiltMaterials.DEFAULT).getOrThrow();
		helper.assertTrue(steel.isJsonObject() && steel.getAsJsonObject().isEmpty(), msg("all steel writes nothing, got " + steel));
		helper.assertTrue(HiltMaterials.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow().equals(HiltMaterials.DEFAULT),
				msg("nothing reads as all steel"));

		HiltMaterials mixed = HiltMaterials.DEFAULT.with(HiltParts.Slot.GRIP, HiltMaterial.STARFALL).with(HiltParts.Slot.POMMEL, HiltMaterial.HYMNSTEEL);
		helper.assertTrue(mixed.of(HiltParts.Slot.EMITTER) == HiltMaterial.STEEL && mixed.of(HiltParts.Slot.GRIP) == HiltMaterial.STARFALL
				&& mixed.pommel() == HiltMaterial.HYMNSTEEL, msg("with and of name the slot"));
		JsonObject json = HiltMaterials.CODEC.encodeStart(JsonOps.INSTANCE, mixed).getOrThrow().getAsJsonObject();
		helper.assertTrue(!json.has("emitter") && json.get("grip").getAsString().equals("starfall") && json.get("pommel").getAsString().equals("hymnsteel"),
				msg("only the non-steel parts are written, by id: " + json));
		helper.assertTrue(HiltMaterials.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(mixed), msg("the materials round-trip"));
		ByteBuf buf = Unpooled.buffer();

		try {
			HiltMaterials.STREAM_CODEC.encode(buf, mixed);
			helper.assertTrue(HiltMaterials.STREAM_CODEC.decode(buf).equals(mixed), msg("the stream codec carries the materials"));
		} finally {
			buf.release();
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void saberConfigCarriesMaterials(GameTestHelper helper) {
		SaberConfig old = SaberConfig.DEFAULT;
		JsonObject json = SaberConfig.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow().getAsJsonObject();
		helper.assertTrue(!json.has("materials"), msg("an all-steel saber writes no materials"));
		helper.assertTrue(SaberConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(old) && old.materials().equals(HiltMaterials.DEFAULT),
				msg("a saber from before Milestone 7 decodes all steel"));

		HiltMaterials materials = new HiltMaterials(HiltMaterial.AURELITE, HiltMaterial.CLAN_IRON, HiltMaterial.DUSKGLASS);
		SaberConfig forged = old.withMaterials(materials);
		JsonObject forgedJson = SaberConfig.CODEC.encodeStart(JsonOps.INSTANCE, forged).getOrThrow().getAsJsonObject();
		helper.assertTrue(forgedJson.has("materials") && SaberConfig.CODEC.parse(JsonOps.INSTANCE, forgedJson).getOrThrow().equals(forged),
				msg("a forged saber's materials round-trip"));
		helper.assertTrue(forged.withColor(BladeColor.RED).materials().equals(materials) && forged.withLength(SaberConfig.LENGTH_LONG).materials().equals(materials)
				&& forged.withLens(Optional.of(OldRepublicMod.id("lens_honed"))).materials().equals(materials)
				&& forged.withSecond(Optional.of(old.crystal())).materials().equals(materials), msg("every with keeps the materials"));
		helper.assertTrue(new SaberConfig(old.crystal(), old.color(), old.emitter(), old.grip(), old.pommel()).materials().equals(HiltMaterials.DEFAULT)
				&& new SaberConfig(old.crystal(), old.color(), old.emitter(), old.grip(), old.pommel(), Optional.empty(), Optional.empty(), 2).materials()
						.equals(HiltMaterials.DEFAULT), msg("the older constructors give all steel"));
		ByteBuf buf = Unpooled.buffer();

		try {
			SaberConfig.STREAM_CODEC.encode(buf, forged);
			helper.assertTrue(SaberConfig.STREAM_CODEC.decode(buf).equals(forged), msg("the stream codec carries the materials"));
		} finally {
			buf.release();
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void loosePartsCarryTheirMaterial(GameTestHelper helper) {
		helper.assertTrue(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(SaberComponents.HILT_MATERIAL).equals(OldRepublicMod.id("hilt_material")),
				msg("the component is oldrepublic:hilt_material"));
		ItemStack steel = SaberItems.partStack(Emitter.VANGUARD, HiltMaterial.STEEL);
		helper.assertTrue(!steel.has(SaberComponents.HILT_MATERIAL) && SaberComponents.material(steel) == HiltMaterial.STEEL
				&& ItemStack.isSameItemSameComponents(steel, SaberItems.partStack(Emitter.VANGUARD)), msg("a steel part carries no material"));
		ItemStack starfall = SaberItems.partStack(Grip.KNURLED, HiltMaterial.STARFALL);
		helper.assertTrue(starfall.get(SaberComponents.HILT_MATERIAL) == HiltMaterial.STARFALL && SaberComponents.material(starfall) == HiltMaterial.STARFALL,
				msg("a starfall part says so"));
		ItemStack legendary = SaberItems.partStack(Grip.SUNFORGED, HiltMaterial.HYMNSTEEL);
		helper.assertTrue(SaberComponents.material(legendary) == HiltMaterial.HYMNSTEEL && legendary.get(DataComponents.RARITY) == Rarity.EPIC,
				msg("a legendary part keeps its quality in any material"));
		helper.assertTrue(SaberComponents.material(ItemStack.EMPTY) == HiltMaterial.STEEL, msg("an empty socket counts as steel"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void designKinds(GameTestHelper helper) {
		List<HiltParts.Part> craftable = List.of(Emitter.GUARDIAN, Emitter.DUELIST, Emitter.VANGUARD, Emitter.WARDEN, Grip.WRAPPED, Grip.RIDGED, Grip.STAFF,
				Grip.BANDED, Grip.KNURLED, Grip.FLUTED, Pommel.WEIGHTED, Pommel.BALANCED, Pommel.RINGED, Pommel.DOMED);
		List<HiltParts.Part> known = List.of(Grip.WRAPPED, Grip.RIDGED, Pommel.WEIGHTED, Pommel.BALANCED);
		List<HiltParts.Part> sets = List.of(Emitter.RELIQUARY, Grip.RELIQUARY, Pommel.ARCHIVIST, Emitter.WATCHMAN, Grip.WATCHMAN, Pommel.WATCHMAN,
				Emitter.PENITENT, Grip.PENITENT, Pommel.PENITENT, Emitter.CHOIR, Grip.CHOIR, Pommel.CHOIR);
		List<HiltParts.Part> legendary = List.of(Emitter.CANTOR, Grip.SUNFORGED, Pommel.HASK);

		for (HiltParts.Part[] group : new HiltParts.Part[][] {Emitter.values(), Grip.values(), Pommel.values()}) {
			for (HiltParts.Part part : group) {
				String id = part.itemId();
				helper.assertTrue(SaberItems.isCraftableDesign(part) == craftable.contains(part), msg("5.1's craftable designs: " + id));
				helper.assertTrue(SaberItems.isKnownByDefault(part) == known.contains(part), msg("5.1's designs known from the start: " + id));
				helper.assertTrue(SaberItems.isSetPart(part) == sets.contains(part), msg("7.1's set parts: " + id));
				helper.assertTrue(SaberItems.isLegendary(part) == legendary.contains(part), msg("the legendary parts: " + id));
				helper.assertTrue(craftable.contains(part) || sets.contains(part) || legendary.contains(part), msg("every part has its kind: " + id));
			}
		}

		for (HiltParts.Part part : List.of(Emitter.VANGUARD, Emitter.WARDEN, Grip.BANDED, Grip.KNURLED, Grip.FLUTED, Pommel.RINGED, Pommel.DOMED)) {
			helper.assertTrue(new ItemStack(SaberItems.partItem(part)).get(DataComponents.RARITY) == Rarity.UNCOMMON, msg("a new craftable design is uncommon: " + part.itemId()));
		}

		for (HiltParts.Part part : List.of(Grip.RELIQUARY, Emitter.WATCHMAN, Grip.WATCHMAN, Pommel.WATCHMAN, Emitter.PENITENT, Grip.PENITENT, Pommel.PENITENT,
				Emitter.CHOIR, Grip.CHOIR, Pommel.CHOIR)) {
			helper.assertTrue(new ItemStack(SaberItems.partItem(part)).get(DataComponents.RARITY) == Rarity.RARE, msg("a new set part is rare: " + part.itemId()));
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void hiltSetsCount(GameTestHelper helper) {
		helper.assertTrue(List.of(HiltSet.values()).equals(List.of(HiltSet.COLLECTOR, HiltSet.WATCHMAN, HiltSet.PENITENT, HiltSet.CHOIR))
				&& HiltSet.CHOIR.id().equals("choir"), msg("four sets in 7.1's order"));
		helper.assertTrue(HiltSet.of(Emitter.RELIQUARY) == HiltSet.COLLECTOR && HiltSet.of(Grip.RELIQUARY) == HiltSet.COLLECTOR
				&& HiltSet.of(Pommel.ARCHIVIST) == HiltSet.COLLECTOR && HiltSet.of(Grip.WATCHMAN) == HiltSet.WATCHMAN && HiltSet.of(Pommel.PENITENT) == HiltSet.PENITENT
				&& HiltSet.of(Emitter.CHOIR) == HiltSet.CHOIR && HiltSet.of(Emitter.CANTOR) == null && HiltSet.of(Grip.WRAPPED) == null,
				msg("each part's set"));
		CrystalAttunement nobody = crystal(new UUID(0L, 0L));
		SaberConfig full = new SaberConfig(nobody, BladeColor.AMBER, Emitter.RELIQUARY, Grip.RELIQUARY, Pommel.ARCHIVIST);
		SaberConfig two = new SaberConfig(nobody, BladeColor.RED, Emitter.PENITENT, Grip.WRAPPED, Pommel.PENITENT);
		SaberConfig one = new SaberConfig(nobody, BladeColor.BLUE, Emitter.WATCHMAN, Grip.CHOIR, Pommel.HASK);
		helper.assertTrue(HiltSet.count(full, HiltSet.COLLECTOR, false) == 3 && HiltSet.active(full, false) == HiltSet.COLLECTOR, msg("a full Collector hilt"));
		helper.assertTrue(HiltSet.count(two, HiltSet.PENITENT, false) == 2 && HiltSet.active(two, false) == HiltSet.PENITENT, msg("two Penitent parts"));
		helper.assertTrue(HiltSet.active(one, false) == null, msg("one part of two sets is no set"));
		SaberConfig staff = new SaberConfig(nobody, BladeColor.VIOLET, Emitter.CHOIR, Grip.STAFF, Pommel.CHOIR);
		helper.assertTrue(HiltSet.count(staff, HiltSet.CHOIR, true) == 1 && HiltSet.active(staff, true) == null,
				msg("a staff's placeholder pommel never counts"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void assemblyCarriesMaterials(GameTestHelper helper) {
		SaberBalance b = new SaberBalance();
		UUID owner = UUID.randomUUID();
		ItemStack emitter = SaberItems.partStack(Emitter.VANGUARD, HiltMaterial.STARFALL);
		ItemStack grip = SaberItems.partStack(Grip.BANDED, HiltMaterial.HYMNSTEEL);
		ItemStack pommel = SaberItems.partStack(Pommel.DOMED, HiltMaterial.STEEL);
		SimpleContainer bench = new SimpleContainer(SaberAssembly.SIZE);
		bench.setItem(SaberAssembly.CRYSTAL, SaberAssembly.crystalStack(crystal(owner)));
		bench.setItem(SaberAssembly.EMITTER, emitter.copy());
		bench.setItem(SaberAssembly.GRIP, grip.copy());
		bench.setItem(SaberAssembly.POMMEL, pommel.copy());
		helper.assertTrue(SaberAssembly.status(bench, owner) == SaberAssembly.Status.READY, msg("precondition: parts of any material assemble"));
		helper.assertTrue(SaberAssembly.materials(bench).equals(new HiltMaterials(HiltMaterial.STARFALL, HiltMaterial.HYMNSTEEL, HiltMaterial.STEEL)),
				msg("the bench reads each socket's material"));

		ItemStack saber = SaberAssembly.assemble(bench, owner, BladeColor.GREEN, b);
		helper.assertTrue(!saber.isEmpty() && SaberComponents.config(saber).materials().equals(new HiltMaterials(HiltMaterial.STARFALL, HiltMaterial.HYMNSTEEL,
				HiltMaterial.STEEL)), msg("the saber keeps its parts' materials"));
		helper.assertTrue(SaberAssembly.disassemble(bench, owner) != null, msg("precondition: the owner takes the saber apart"));
		helper.assertTrue(ItemStack.isSameItemSameComponents(bench.getItem(SaberAssembly.EMITTER), emitter)
				&& ItemStack.isSameItemSameComponents(bench.getItem(SaberAssembly.GRIP), grip)
				&& ItemStack.isSameItemSameComponents(bench.getItem(SaberAssembly.POMMEL), pommel), msg("each part comes back in its material"));

		// A saberstaff: its emitters and its grip keep their materials; it has no pommel to give back.
		SimpleContainer staffBench = new SimpleContainer(SaberAssembly.SIZE);
		ItemStack staffEmitter = SaberItems.partStack(Emitter.WARDEN, HiltMaterial.AURELITE);
		ItemStack staffGrip = SaberItems.partStack(Grip.STAFF, HiltMaterial.CLAN_IRON);
		staffBench.setItem(SaberAssembly.CRYSTAL, SaberAssembly.crystalStack(crystal(owner)));
		staffBench.setItem(SaberAssembly.SECOND, SaberAssembly.crystalStack(crystal(owner)));
		staffBench.setItem(SaberAssembly.EMITTER, staffEmitter.copy());
		staffBench.setItem(SaberAssembly.GRIP, staffGrip.copy());
		helper.assertTrue(SaberAssembly.status(staffBench, owner) == SaberAssembly.Status.READY, msg("precondition: the staff bench is ready"));
		ItemStack staff = SaberAssembly.assemble(staffBench, owner, BladeColor.VIOLET, b);
		helper.assertTrue(SaberComponents.config(staff).materials().equals(new HiltMaterials(HiltMaterial.AURELITE, HiltMaterial.CLAN_IRON, HiltMaterial.STEEL)),
				msg("the staff keeps its emitter's and grip's materials"));
		helper.assertTrue(SaberAssembly.disassemble(staffBench, owner) != null, msg("precondition: the owner takes the staff apart"));
		helper.assertTrue(ItemStack.isSameItemSameComponents(staffBench.getItem(SaberAssembly.EMITTER), staffEmitter)
				&& ItemStack.isSameItemSameComponents(staffBench.getItem(SaberAssembly.GRIP), staffGrip)
				&& staffBench.getItem(SaberAssembly.POMMEL).isEmpty(), msg("the staff's parts come back in their materials, and no pommel"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void allSteelKeepsTodaysNumbers(GameTestHelper helper) {
		SaberBalance b = new SaberBalance();

		for (Emitter e : new Emitter[] {Emitter.GUARDIAN, Emitter.DUELIST, Emitter.RELIQUARY, Emitter.CANTOR}) {
			for (Grip g : new Grip[] {Grip.WRAPPED, Grip.RIDGED, Grip.SUNFORGED}) {
				for (Pommel p : new Pommel[] {Pommel.WEIGHTED, Pommel.BALANCED, Pommel.ARCHIVIST, Pommel.HASK}) {
					SaberStats plain = SaberStats.of(e, g, p, b);
					helper.assertTrue(plain.withMaterials(HiltMaterials.DEFAULT, true, b).equals(plain), msg("all steel adds nothing: " + e + " " + g + " " + p));
				}
			}

			SaberStats staff = SaberStats.staff(e, Grip.STAFF, true, b);
			helper.assertTrue(staff.withMaterials(HiltMaterials.DEFAULT, false, b).equals(staff), msg("an all-steel staff adds nothing: " + e));
		}

		for (float[] byTier : new float[][] {b.materialEmitterDamage, b.materialGripSpeedPct, b.materialGripGuard, b.materialPommelParry,
				b.materialPommelGuard}) {
			helper.assertTrue(byTier.length == 6 && byTier[0] == 0f, msg("each material bonus has six tiers and steel's is 0"));
		}

		helper.assertTrue(b.forgeCredits.length == 6 && b.forgeCredits[0] == 0, msg("the Forge's credits have six tiers and steel is free"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void milestoneSevenItems(GameTestHelper helper) {
		helper.assertTrue(List.of(VibroWeaponItem.Kind.values()).equals(List.of(VibroWeaponItem.Kind.BLADE, VibroWeaponItem.Kind.SWORD, VibroWeaponItem.Kind.STAFF,
				VibroWeaponItem.Kind.AXE, VibroWeaponItem.Kind.PIKE)), msg("the axe and the pike follow the staff"));
		helper.assertTrue(List.of(BlasterItem.Kind.values()).equals(List.of(BlasterItem.Kind.PISTOL, BlasterItem.Kind.RIFLE, BlasterItem.Kind.REPEATER,
				BlasterItem.Kind.SCATTER, BlasterItem.Kind.CARBINE)), msg("the scatter blaster and the carbine follow the repeater"));
		helper.assertTrue(M7Items.VIBRO_AXE.kind() == VibroWeaponItem.Kind.AXE && M7Items.FORCE_PIKE.kind() == VibroWeaponItem.Kind.PIKE
				&& M7Items.SCATTER_BLASTER.kind() == BlasterItem.Kind.SCATTER && M7Items.BLASTER_CARBINE.kind() == BlasterItem.Kind.CARBINE,
				msg("each new weapon has its kind"));
		helper.assertTrue(BuiltInRegistries.ITEM.getKey(M7Items.VIBRO_AXE).equals(OldRepublicMod.id("vibro_axe"))
				&& BuiltInRegistries.ITEM.getKey(M7Items.FORCE_PIKE).equals(OldRepublicMod.id("force_pike"))
				&& BuiltInRegistries.ITEM.getKey(M7Items.SCATTER_BLASTER).equals(OldRepublicMod.id("scatter_blaster"))
				&& BuiltInRegistries.ITEM.getKey(M7Items.BLASTER_CARBINE).equals(OldRepublicMod.id("blaster_carbine")), msg("the weapons' ids"));

		for (String id : List.of("starfall_ore", "deepslate_starfall_ore", "starfall_rock", "clan_iron_ore")) {
			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(OldRepublicMod.id(id)) && BuiltInRegistries.ITEM.getValue(OldRepublicMod.id(id)) instanceof BlockItem,
					msg("a block with its item: " + id));
		}

		for (String id : List.of("raw_starfall", "starfall_ingot", "hymnsteel_ingot")) {
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(OldRepublicMod.id(id)), msg("an item: " + id));
		}

		helper.assertTrue(Mods.slotOf(M7Items.MOD_STARFALL_EDGE) == ModSlot.EDGE && Mods.slotOf(M7Items.MOD_CLAN_IRON_EDGE) == ModSlot.EDGE
				&& Mods.slotOf(M7Items.MOD_AURELITE_BARREL) == ModSlot.BARREL && Mods.slotOf(M7Items.MOD_DUSKGLASS_CELL) == ModSlot.CELL,
				msg("each material mod fits its slot"));
		helper.assertTrue(Mods.fits(new ItemStack(M7Items.VIBRO_AXE), ModSlot.EDGE, new ItemStack(M7Items.MOD_CLAN_IRON_EDGE))
				&& Mods.fits(new ItemStack(M7Items.BLASTER_CARBINE), ModSlot.BARREL, new ItemStack(M7Items.MOD_AURELITE_BARREL)),
				msg("the new weapons take mods like the old ones"));

		for (String id : SOUNDS) {
			helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(OldRepublicMod.id(id)), msg("a sound event: " + id));
		}

		helper.succeed();
	}
}
