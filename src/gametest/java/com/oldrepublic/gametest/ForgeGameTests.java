package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.CrystalTuning;
import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.HiltMaterials;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberAssembly.Status;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.SaberTunings;
import com.oldrepublic.saber.forge.ForgeActionPayload;
import com.oldrepublic.saber.forge.ForgeAttachments;
import com.oldrepublic.saber.forge.ForgeData;
import com.oldrepublic.saber.forge.ForgeRules;
import com.oldrepublic.saber.forge.ForgeRules.Result;
import com.oldrepublic.saber.forge.ForgeService;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/**
 * The Forge's game tests (spec MILESTONE_7.md 5, 14): the costs by slot and tier, Study, Forge and Re-forge
 * with their refusals, what the inventory pays and receives, a full socket, a legendary part keeping its
 * quality, the request's checks on the server, the known designs surviving death and a save, and forged
 * parts carrying their materials through assembly, a retune at the bench and disassembly.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks}; players are listed mock players
 * ({@link M3TestPlayers}), removed at the end, their inventory and known designs cleared first; the bench
 * is a workbench menu with no block ({@code ContainerLevelAccess.NULL}), so no effect is sent. A rule's
 * shape is checked against the spec defaults ({@link #DEFAULTS}); what the menu takes, against the
 * configured balance.
 */
public class ForgeGameTests {
	/** The spec's numbers (5.2): rule shapes are checked against these. */
	private static final SaberBalance DEFAULTS = new SaberBalance();

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static CrystalAttunement crystal(UUID owner) {
		return new CrystalAttunement(owner, "Tester", BladeColor.BLUE.rgb());
	}

	/** A workbench menu of {@code p}'s with an empty bench, as the screen's server side. */
	private static LightsaberWorkbenchMenu bench(ServerPlayer p) {
		return new LightsaberWorkbenchMenu(0, p.getInventory(), ContainerLevelAccess.NULL);
	}

	/** A listed player with an empty inventory who knows only the starting designs. */
	private static ServerPlayer smith(GameTestHelper helper) {
		ServerPlayer p = M3TestPlayers.listed(helper);
		p.getInventory().clearContent();
		ForgeAttachments.set(p, ForgeData.EMPTY);
		return p;
	}

	/** The configured balance's costs, as the menu takes them. */
	private static ForgeRules.Costs live() {
		return ForgeRules.Costs.of(SaberToggle.balance());
	}

	/** Gives {@code p} the material's units and the credits {@code design} in {@code material} costs under {@code costs}, and {@code extraCredits}. */
	private static void fund(ServerPlayer p, HiltParts.Part design, HiltMaterial material, ForgeRules.Costs costs, int extraCredits) {
		Item unit = ForgeRules.unitItem(material);
		int units = costs.units(design);

		if (unit != null && units > 0) {
			p.getInventory().add(new ItemStack(unit, units));
		}

		Credits.give(p, costs.credits(material) + extraCredits);
	}

	/** How many of {@code item} {@code p} carries. */
	private static int count(ServerPlayer p, Item item) {
		int n = 0;

		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}

		return n;
	}

	/** The first loose part of {@code design} in {@code p}'s inventory, or empty. */
	private static ItemStack carried(ServerPlayer p, HiltParts.Part design) {
		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (ForgeRules.part(stack) == design) {
				return stack;
			}
		}

		return ItemStack.EMPTY;
	}

	// ---------------------------------------------------------------- costs

	/** 5.2's units by slot and credits by tier; an edited balance's short or missing arrays; the bench syncs the server's costs. */
	@GameTest(maxTicks = 20)
	public void forgeCostsFollowTheBalance(GameTestHelper helper) {
		ForgeRules.Costs c = ForgeRules.Costs.of(DEFAULTS);
		helper.assertTrue(ForgeRules.TIERS == 6, msg("six credit tiers, steel to hymnsteel"));
		helper.assertTrue(c.units(Emitter.VANGUARD) == 2 && c.units(Emitter.CANTOR) == 2 && c.units(Grip.BANDED) == 3 && c.units(Grip.SUNFORGED) == 3
				&& c.units(Grip.STAFF) == 5 && c.units(Pommel.DOMED) == 2 && c.units(Pommel.ARCHIVIST) == 2,
				msg("5.2: 2 units an emitter, 3 a grip, 5 the staff grip, 2 a pommel"));
		int[] credits = {0, 20, 40, 80, 80, 120, 120, 200};

		for (HiltMaterial m : HiltMaterial.values()) {
			helper.assertTrue(c.credits(m) == credits[m.ordinal()], msg("5.2: a " + m.id() + " part costs " + credits[m.ordinal()] + " credits, got " + c.credits(m)));
			Item unit = ForgeRules.unitItem(m);
			helper.assertTrue(unit != null && BuiltInRegistries.ITEM.getKey(unit).equals(m.itemId()), msg("a material's unit item is registered: " + m.itemId()));
		}

		Item starfall = ForgeRules.unitItem(HiltMaterial.STARFALL);
		List<Schematic.Input> inputs = ForgeRules.inputs(Grip.KNURLED, HiltMaterial.STARFALL, c);
		helper.assertTrue(starfall != null && inputs != null && inputs.size() == 1 && inputs.get(0).count() == 3 && inputs.get(0).matches(new ItemStack(starfall))
				&& !inputs.get(0).matches(new ItemStack(Items.IRON_INGOT)), msg("a starfall grip takes 3 starfall ingots and nothing else"));

		SaberBalance edited = new SaberBalance();
		edited.forgeCredits = new int[] {0, 10};
		edited.forgeUnitsPommel = -4;
		ForgeRules.Costs e = ForgeRules.Costs.of(edited);
		helper.assertTrue(e.credits(HiltMaterial.STEEL) == 0 && e.credits(HiltMaterial.DURASTEEL) == 10 && e.credits(HiltMaterial.HYMNSTEEL) == 10,
				msg("a short credits array: a higher tier pays its last price"));
		List<Schematic.Input> none = ForgeRules.inputs(Pommel.RINGED, HiltMaterial.AURELITE, e);
		helper.assertTrue(e.units(Pommel.RINGED) == 0 && none != null && none.isEmpty(), msg("negative units count as none"));
		edited.forgeCredits = null;
		helper.assertTrue(ForgeRules.Costs.of(edited).credits(HiltMaterial.HYMNSTEEL) == 0, msg("no credits array: no credits are asked"));

		ServerPlayer p = smith(helper);

		try {
			ForgeRules.Costs live = live();
			ForgeRules.Costs synced = bench(p).forgeCosts();
			boolean same = synced.emitterUnits() == live.emitterUnits() && synced.gripUnits() == live.gripUnits()
					&& synced.staffGripUnits() == live.staffGripUnits() && synced.pommelUnits() == live.pommelUnits();

			for (int tier = 0; tier < ForgeRules.TIERS; tier++) {
				same &= synced.creditsForTier(tier) == live.creditsForTier(tier);
			}

			helper.assertTrue(same, msg("the bench's data slots carry the server's costs to the screen"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- Study

	/** 5.1, 5.2: the starting designs; Study needs the unknown craftable design in its socket, is free, keeps the part, and happens once. */
	@GameTest(maxTicks = 20)
	public void studyLearnsTheDesignInItsSocket(GameTestHelper helper) {
		ForgeData start = ForgeData.EMPTY;
		helper.assertTrue(start.knows(Grip.WRAPPED) && start.knows(Grip.RIDGED) && start.knows(Pommel.WEIGHTED) && start.knows(Pommel.BALANCED)
				&& !start.knows(Emitter.GUARDIAN) && !start.knows(Emitter.VANGUARD) && !start.knows(Grip.STAFF) && !start.knows(Pommel.RINGED),
				msg("5.1: the crafting-table designs are known from the start, no others"));
		helper.assertTrue(ForgeRules.checkStudy(ItemStack.EMPTY, start) == Result.NO_PART && ForgeRules.checkStudy(new ItemStack(Items.IRON_INGOT), start) == Result.NO_PART,
				msg("nothing to study without a part"));
		helper.assertTrue(ForgeRules.checkStudy(SaberItems.partStack(Emitter.VANGUARD, HiltMaterial.AURELITE), start) == Result.OK,
				msg("an unknown craftable design of any material can be studied"));
		helper.assertTrue(ForgeRules.checkStudy(SaberItems.partStack(Grip.WRAPPED), start) == Result.KNOWN, msg("a known design is not studied"));
		helper.assertTrue(ForgeRules.checkStudy(SaberItems.partStack(Emitter.CANTOR), start) == Result.NOT_CRAFTABLE
				&& ForgeRules.checkStudy(SaberItems.partStack(Grip.RELIQUARY), start) == Result.NOT_CRAFTABLE, msg("legendary and set designs are never studied"));
		ForgeData one = start.study(Emitter.VANGUARD);
		helper.assertTrue(one.knows(Emitter.VANGUARD) && one.studied().equals(List.of("emitter_vanguard")) && one.study(Emitter.VANGUARD) == one,
				msg("studying adds the design once"));
		helper.assertTrue(start.study(Grip.WRAPPED) == start, msg("a starting design is never stored"));

		ServerPlayer p = smith(helper);

		try {
			Credits.give(p, 50);
			LightsaberWorkbenchMenu menu = bench(p);
			ItemStack vanguard = SaberItems.partStack(Emitter.VANGUARD, HiltMaterial.AURELITE);
			menu.getSlot(SaberAssembly.EMITTER).set(vanguard.copy());
			helper.assertTrue(!menu.study(p, Emitter.WARDEN) && !ForgeAttachments.get(p).knows(Emitter.WARDEN), msg("only the design in its socket is studied"));
			helper.assertTrue(menu.study(p, Emitter.VANGUARD) && ForgeAttachments.get(p).knows(Emitter.VANGUARD), msg("Study learns the Vanguard"));
			helper.assertTrue(ItemStack.isSameItemSameComponents(menu.getSlot(SaberAssembly.EMITTER).getItem(), vanguard) && Credits.count(p, false) == 50,
					msg("Study is free and the part stays in its material"));
			helper.assertTrue(!menu.study(p, Emitter.VANGUARD), msg("a design is studied once"));
			menu.getSlot(SaberAssembly.EMITTER).set(SaberItems.partStack(Emitter.CANTOR));
			helper.assertTrue(!menu.study(p, Emitter.CANTOR) && !ForgeAttachments.get(p).knows(Emitter.CANTOR), msg("a legendary part teaches nothing"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- Forge

	/** 5.2: Forge needs a known craftable design, the material's units and the credits. */
	@GameTest(maxTicks = 20)
	public void forgeChecksKnownDesignsAndCosts(GameTestHelper helper) {
		ForgeRules.Costs c = ForgeRules.Costs.of(DEFAULTS);
		ForgeData start = ForgeData.EMPTY;
		Item plate = ForgeRules.unitItem(HiltMaterial.DURASTEEL);
		helper.assertTrue(plate != null && ForgeRules.unitItem(HiltMaterial.STEEL) == Items.IRON_INGOT, msg("precondition: durasteel is paid in plates, steel in iron"));
		List<ItemStack> threePlates = List.of(new ItemStack(plate, 3));
		helper.assertTrue(ForgeRules.checkForge(Emitter.CANTOR, HiltMaterial.STEEL, start, threePlates, 1000, c) == Result.NOT_CRAFTABLE
				&& ForgeRules.checkForge(Pommel.ARCHIVIST, HiltMaterial.STEEL, start, threePlates, 1000, c) == Result.NOT_CRAFTABLE,
				msg("legendary and set designs are never forged"));
		helper.assertTrue(ForgeRules.checkForge(Emitter.VANGUARD, HiltMaterial.DURASTEEL, start, threePlates, 1000, c) == Result.UNKNOWN,
				msg("an unknown design is studied first"));
		helper.assertTrue(ForgeRules.checkForge(Grip.WRAPPED, HiltMaterial.DURASTEEL, start, threePlates, 20, c) == Result.OK,
				msg("a known grip in durasteel: 3 plates and 20 credits"));
		helper.assertTrue(ForgeRules.checkForge(Grip.WRAPPED, HiltMaterial.DURASTEEL, start, List.of(new ItemStack(plate, 2)), 20, c) == Result.NO_MATERIALS,
				msg("2 plates are short"));
		helper.assertTrue(ForgeRules.checkForge(Grip.WRAPPED, HiltMaterial.DURASTEEL, start, threePlates, 19, c) == Result.NO_CREDITS, msg("19 credits are short"));
		helper.assertTrue(ForgeRules.checkForge(Grip.WRAPPED, HiltMaterial.STARFALL, start, threePlates, 1000, c) == Result.NO_MATERIALS,
				msg("plates do not pay for starfall"));
		helper.assertTrue(ForgeRules.checkForge(Pommel.WEIGHTED, HiltMaterial.STEEL, start, List.of(new ItemStack(Items.IRON_INGOT, 2)), 0, c) == Result.OK,
				msg("a steel pommel: 2 iron ingots and no credits"));
		ForgeData staff = start.study(Grip.STAFF);
		helper.assertTrue(ForgeRules.checkForge(Grip.STAFF, HiltMaterial.STEEL, staff, List.of(new ItemStack(Items.IRON_INGOT, 4)), 0, c) == Result.NO_MATERIALS
				&& ForgeRules.checkForge(Grip.STAFF, HiltMaterial.STEEL, staff, List.of(new ItemStack(Items.IRON_INGOT, 5)), 0, c) == Result.OK,
				msg("the staff grip takes 5 units"));
		helper.succeed();
	}

	/** 5.2: a Forge takes the units and the credits from the inventory and puts the part in its empty socket; a refusal takes nothing. */
	@GameTest(maxTicks = 20)
	public void forgeTakesUnitsAndCredits(GameTestHelper helper) {
		ServerPlayer p = smith(helper);

		try {
			ForgeRules.Costs live = live();
			LightsaberWorkbenchMenu menu = bench(p);
			Item plate = ForgeRules.unitItem(HiltMaterial.DURASTEEL);
			int units = live.units(Grip.RIDGED);
			int credits = live.credits(HiltMaterial.DURASTEEL);
			fund(p, Grip.RIDGED, HiltMaterial.DURASTEEL, live, 7);
			helper.assertTrue(menu.forgeFitsSocket(Grip.RIDGED) && menu.forge(p, Grip.RIDGED, HiltMaterial.DURASTEEL), msg("a known design forges"));
			ItemStack forged = menu.getSlot(SaberAssembly.GRIP).getItem();
			helper.assertTrue(ForgeRules.part(forged) == Grip.RIDGED && SaberComponents.material(forged) == HiltMaterial.DURASTEEL && forged.getCount() == 1,
					msg("a durasteel Ridged grip lands in the empty grip socket"));
			helper.assertTrue(count(p, plate) == 0 && Credits.count(p, false) == 7,
					msg(units + " plates and " + credits + " credits are taken, the rest kept: " + Credits.count(p, false)));
			helper.assertTrue(carried(p, Grip.RIDGED).isEmpty(), msg("nothing goes to the inventory while the socket takes it"));

			menu.getSlot(SaberAssembly.GRIP).set(ItemStack.EMPTY);
			helper.assertTrue(!menu.forge(p, Emitter.VANGUARD, HiltMaterial.STEEL) && !menu.forge(p, Emitter.CANTOR, HiltMaterial.STEEL)
					&& menu.getSlot(SaberAssembly.EMITTER).getItem().isEmpty() && Credits.count(p, false) == 7, msg("an unknown or legendary design is refused"));

			if (units > 0) {
				p.getInventory().add(new ItemStack(plate, units - 1));
				Credits.give(p, credits);
				int before = Credits.count(p, false);
				helper.assertTrue(!menu.forge(p, Grip.RIDGED, HiltMaterial.DURASTEEL) && count(p, plate) == units - 1 && Credits.count(p, false) == before
						&& menu.getSlot(SaberAssembly.GRIP).getItem().isEmpty(), msg("short of plates: refused, nothing taken"));
			}

			// Once studied, the Vanguard forges; a steel part carries no material.
			p.getInventory().clearContent();
			ForgeAttachments.set(p, ForgeData.EMPTY.study(Emitter.VANGUARD));
			fund(p, Emitter.VANGUARD, HiltMaterial.STEEL, live, 0);
			helper.assertTrue(menu.forge(p, Emitter.VANGUARD, HiltMaterial.STEEL), msg("a studied design forges"));
			ItemStack steel = menu.getSlot(SaberAssembly.EMITTER).getItem();
			helper.assertTrue(ForgeRules.part(steel) == Emitter.VANGUARD && !steel.has(SaberComponents.HILT_MATERIAL) && count(p, Items.IRON_INGOT) == 0,
					msg("a steel Vanguard for its iron"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** 5.2: with its socket full (or the pommel socket locked by a staff), a forged part goes to the inventory. */
	@GameTest(maxTicks = 20)
	public void fullSocketSendsThePartToTheInventory(GameTestHelper helper) {
		ServerPlayer p = smith(helper);

		try {
			ForgeRules.Costs live = live();
			LightsaberWorkbenchMenu menu = bench(p);
			ItemStack wrapped = SaberItems.partStack(Grip.WRAPPED, HiltMaterial.STARFALL);
			menu.getSlot(SaberAssembly.GRIP).set(wrapped.copy());
			helper.assertTrue(!menu.forgeFitsSocket(Grip.RIDGED), msg("precondition: the grip socket is full"));
			fund(p, Grip.RIDGED, HiltMaterial.AURELITE, live, 0);
			helper.assertTrue(menu.forge(p, Grip.RIDGED, HiltMaterial.AURELITE), msg("a full socket does not stop the Forge"));
			ItemStack made = carried(p, Grip.RIDGED);
			helper.assertTrue(SaberComponents.material(made) == HiltMaterial.AURELITE && made.getCount() == 1
					&& ItemStack.isSameItemSameComponents(menu.getSlot(SaberAssembly.GRIP).getItem(), wrapped),
					msg("the aurelite Ridged grip goes to the inventory; the socket keeps its starfall Wrapped grip"));

			menu.getSlot(SaberAssembly.GRIP).set(SaberItems.partStack(Grip.STAFF));
			helper.assertTrue(menu.isStaffMode() && !menu.forgeFitsSocket(Pommel.WEIGHTED), msg("precondition: the staff grip locks the pommel socket"));
			fund(p, Pommel.WEIGHTED, HiltMaterial.STEEL, live, 0);
			helper.assertTrue(menu.forge(p, Pommel.WEIGHTED, HiltMaterial.STEEL) && menu.getSlot(SaberAssembly.POMMEL).getItem().isEmpty()
					&& !carried(p, Pommel.WEIGHTED).isEmpty(), msg("a pommel forged at a staff bench goes to the inventory"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- Re-forge

	/** 5.2: Re-forge needs the part in its socket and a new material, costs what forging it would, and keeps the design and the quality. */
	@GameTest(maxTicks = 20)
	public void reforgeKeepsDesignAndQuality(GameTestHelper helper) {
		ForgeRules.Costs c = ForgeRules.Costs.of(DEFAULTS);
		Item plate = ForgeRules.unitItem(HiltMaterial.DURASTEEL);
		List<ItemStack> plenty = plate == null ? List.of() : List.of(new ItemStack(plate, 64));
		ItemStack steelVanguard = SaberItems.partStack(Emitter.VANGUARD);
		helper.assertTrue(ForgeRules.checkReforge(ItemStack.EMPTY, Emitter.VANGUARD, HiltMaterial.DURASTEEL, plenty, 1000, c) == Result.NO_PART
				&& ForgeRules.checkReforge(steelVanguard, Emitter.WARDEN, HiltMaterial.DURASTEEL, plenty, 1000, c) == Result.NO_PART,
				msg("only the part in its socket is re-forged"));
		helper.assertTrue(ForgeRules.checkReforge(steelVanguard, Emitter.VANGUARD, HiltMaterial.STEEL, plenty, 1000, c) == Result.SAME_MATERIAL,
				msg("not into its own material"));
		helper.assertTrue(ForgeRules.checkReforge(steelVanguard, Emitter.VANGUARD, HiltMaterial.DURASTEEL, plenty, 20, c) == Result.OK
				&& ForgeRules.checkReforge(steelVanguard, Emitter.VANGUARD, HiltMaterial.DURASTEEL, plenty, 19, c) == Result.NO_CREDITS,
				msg("a design need not be known to be re-forged; it costs what forging it would"));

		ItemStack legendary = SaberItems.partStack(Emitter.CANTOR);
		ItemStack sunstone = ForgeRules.reforged(legendary, HiltMaterial.SUNSTONE);
		helper.assertTrue(sunstone.is(legendary.getItem()) && sunstone.getCount() == 1 && SaberComponents.material(sunstone) == HiltMaterial.SUNSTONE
				&& Crafting.quality(sunstone) == Quality.LEGENDARY && sunstone.get(DataComponents.RARITY) == Rarity.EPIC,
				msg("a re-forged Cantor's Emitter stays Legendary"));
		ItemStack back = ForgeRules.reforged(sunstone, HiltMaterial.STEEL);
		helper.assertTrue(!back.has(SaberComponents.HILT_MATERIAL) && ItemStack.isSameItemSameComponents(back, legendary),
				msg("re-forged in steel it is the plain legendary part again"));

		ServerPlayer p = smith(helper);

		try {
			ForgeRules.Costs live = live();
			LightsaberWorkbenchMenu menu = bench(p);
			menu.getSlot(SaberAssembly.EMITTER).set(legendary.copy());
			fund(p, Emitter.CANTOR, HiltMaterial.SUNSTONE, live, 0);
			helper.assertTrue(menu.reforge(p, Emitter.CANTOR, HiltMaterial.SUNSTONE), msg("the Cantor's Emitter re-forges"));
			ItemStack inSocket = menu.getSlot(SaberAssembly.EMITTER).getItem();
			helper.assertTrue(ForgeRules.part(inSocket) == Emitter.CANTOR && SaberComponents.material(inSocket) == HiltMaterial.SUNSTONE
					&& Crafting.quality(inSocket) == Quality.LEGENDARY && inSocket.getCount() == 1, msg("in its socket: the Cantor's Emitter in sunstone, still Legendary"));
			helper.assertTrue(count(p, ForgeRules.unitItem(HiltMaterial.SUNSTONE)) == 0 && Credits.count(p, false) == 0, msg("the sunstone and the credits are taken"));

			fund(p, Emitter.CANTOR, HiltMaterial.SUNSTONE, live, 0);
			int before = Credits.count(p, false);
			helper.assertTrue(!menu.reforge(p, Emitter.CANTOR, HiltMaterial.SUNSTONE) && Credits.count(p, false) == before,
					msg("into the same material: refused, nothing taken"));

			p.getInventory().clearContent();
			menu.getSlot(SaberAssembly.GRIP).set(SaberItems.partStack(Grip.RELIQUARY));
			fund(p, Grip.RELIQUARY, HiltMaterial.CLAN_IRON, live, 0);
			helper.assertTrue(menu.reforge(p, Grip.RELIQUARY, HiltMaterial.CLAN_IRON)
					&& SaberComponents.material(menu.getSlot(SaberAssembly.GRIP).getItem()) == HiltMaterial.CLAN_IRON, msg("a set part re-forges"));
			helper.assertTrue(ForgeAttachments.get(p).studied().isEmpty(), msg("re-forging teaches nothing"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the request

	/** The C2S request counts only with a workbench open; an unknown design, action or material is ignored. */
	@GameTest(maxTicks = 20)
	public void forgeServiceNeedsAnOpenBench(GameTestHelper helper) {
		helper.assertTrue(ForgeRules.design("emitter_vanguard") == Emitter.VANGUARD && ForgeRules.design("pommel_hask") == Pommel.HASK
				&& ForgeRules.design("emitter_nowhere") == null, msg("a design is named by its item id"));
		helper.assertTrue(ForgeService.material(0) == HiltMaterial.STEEL && ForgeService.material(HiltMaterial.HYMNSTEEL.ordinal()) == HiltMaterial.HYMNSTEEL
				&& ForgeService.material(-1) == null && ForgeService.material(HiltMaterial.values().length) == null, msg("a material is named by its ordinal"));
		ServerPlayer p = smith(helper);

		try {
			ForgeRules.Costs live = live();
			LightsaberWorkbenchMenu menu = bench(p);
			menu.getSlot(SaberAssembly.EMITTER).set(SaberItems.partStack(Emitter.WARDEN));
			ForgeActionPayload study = new ForgeActionPayload(ForgeActionPayload.STUDY, Emitter.WARDEN.itemId(), 0);
			ForgeService.handle(p, study);
			helper.assertTrue(!ForgeAttachments.get(p).knows(Emitter.WARDEN), msg("without a workbench open the request is ignored"));

			// As the menu a player opens at a bench (the block's reach check passes without a block).
			// API-CHECK: Player.containerMenu is assignable (public, not final), as openMenu sets it.
			p.containerMenu = menu;
			ForgeService.handle(p, new ForgeActionPayload(ForgeActionPayload.STUDY, "emitter_nowhere", 0));
			ForgeService.handle(p, new ForgeActionPayload(9, Emitter.WARDEN.itemId(), 0));
			helper.assertTrue(!ForgeAttachments.get(p).knows(Emitter.WARDEN), msg("an unknown design or action is ignored"));
			ForgeService.handle(p, study);
			helper.assertTrue(ForgeAttachments.get(p).knows(Emitter.WARDEN), msg("with the bench open, Study goes through"));

			fund(p, Emitter.WARDEN, HiltMaterial.DURASTEEL, live, 0);
			int credits = Credits.count(p, false);
			ForgeService.handle(p, new ForgeActionPayload(ForgeActionPayload.REFORGE, Emitter.WARDEN.itemId(), HiltMaterial.values().length));
			helper.assertTrue(SaberComponents.material(menu.getSlot(SaberAssembly.EMITTER).getItem()) == HiltMaterial.STEEL && Credits.count(p, false) == credits,
					msg("a material out of range is ignored"));
			ForgeService.handle(p, new ForgeActionPayload(ForgeActionPayload.REFORGE, Emitter.WARDEN.itemId(), HiltMaterial.DURASTEEL.ordinal()));
			helper.assertTrue(SaberComponents.material(menu.getSlot(SaberAssembly.EMITTER).getItem()) == HiltMaterial.DURASTEEL,
					msg("Re-forge goes through the open bench"));
		} finally {
			p.containerMenu = p.inventoryMenu;
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the known designs

	/** ForgeData survives JSON, NBT (a save) and the sync codec, is bounded, and is saved, copied on death and synced to its owner. */
	@GameTest(maxTicks = 20)
	public void forgeDataSurvivesDeathAndSaves(GameTestHelper helper) {
		ForgeData data = ForgeData.EMPTY.study(Emitter.VANGUARD).study(Grip.FLUTED).study(Pommel.DOMED);
		JsonElement json = ForgeData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		helper.assertTrue(ForgeData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(data), msg("the known designs survive JSON: " + json));
		Tag nbt = ForgeData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		helper.assertTrue(ForgeData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().equals(data), msg("the known designs survive NBT: " + nbt));
		StreamCodec<ByteBuf, ForgeData> stream = ByteBufCodecs.fromCodec(ForgeData.CODEC);
		ByteBuf buf = Unpooled.buffer();

		try {
			stream.encode(buf, data);
			helper.assertTrue(stream.decode(buf).equals(data), msg("the known designs survive the sync codec"));
		} finally {
			buf.release();
		}

		helper.assertTrue(ForgeData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow().equals(ForgeData.EMPTY),
				msg("a save without the field loads with the starting designs only"));
		helper.assertTrue(new ForgeData(List.of("grip_fluted", "emitter_vanguard", "grip_fluted")).studied().equals(List.of("emitter_vanguard", "grip_fluted")),
				msg("sorted, without repeats"));
		List<String> many = new ArrayList<>();

		for (int i = 0; i < ForgeData.MAX_STUDIED + 10; i++) {
			many.add("design_" + (1000 + i));
		}

		helper.assertTrue(new ForgeData(many).studied().size() == ForgeData.MAX_STUDIED, msg("a save or a packet holds at most " + ForgeData.MAX_STUDIED));
		helper.assertTrue(ForgeAttachments.FORGE.isPersistent() && ForgeAttachments.FORGE.copyOnDeath() && ForgeAttachments.FORGE.isSynced(),
				msg("the known designs are saved, copied on death and synced to their owner"));

		ServerPlayer p = smith(helper);

		try {
			helper.assertTrue(ForgeAttachments.get(p).equals(ForgeData.EMPTY), msg("precondition: the starting designs only"));
			ForgeAttachments.set(p, data);
			helper.assertTrue(ForgeAttachments.get(p).equals(data) && ForgeAttachments.get(p).knows(Grip.FLUTED) && !ForgeAttachments.get(p).knows(Grip.KNURLED),
					msg("the attachment holds the studied designs"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- assembly

	/** 5.3: assembly stays free and takes forged parts of any material; the saber keeps them through a retune, and its parts after. */
	@GameTest(maxTicks = 20)
	public void assemblyCarriesForgedMaterials(GameTestHelper helper) {
		ServerPlayer p = smith(helper);

		try {
			ForgeRules.Costs live = live();
			ForgeAttachments.set(p, ForgeData.EMPTY.study(Emitter.VANGUARD));
			LightsaberWorkbenchMenu menu = bench(p);
			menu.getSlot(SaberAssembly.CRYSTAL).set(SaberAssembly.crystalStack(crystal(p.getUUID())));
			fund(p, Emitter.VANGUARD, HiltMaterial.STARFALL, live, 0);
			fund(p, Grip.RIDGED, HiltMaterial.HYMNSTEEL, live, 0);
			fund(p, Pommel.BALANCED, HiltMaterial.DURASTEEL, live, 0);
			helper.assertTrue(menu.forge(p, Emitter.VANGUARD, HiltMaterial.STARFALL) && menu.forge(p, Grip.RIDGED, HiltMaterial.HYMNSTEEL)
					&& menu.forge(p, Pommel.BALANCED, HiltMaterial.DURASTEEL), msg("the three parts are forged into their sockets"));
			ItemStack emitter = menu.getSlot(SaberAssembly.EMITTER).getItem().copy();
			ItemStack grip = menu.getSlot(SaberAssembly.GRIP).getItem().copy();
			ItemStack pommel = menu.getSlot(SaberAssembly.POMMEL).getItem().copy();
			helper.assertTrue(menu.status() == Status.READY, msg("forged parts of any material assemble"));

			Credits.give(p, 30);
			HiltMaterials forged = new HiltMaterials(HiltMaterial.STARFALL, HiltMaterial.HYMNSTEEL, HiltMaterial.DURASTEEL);
			helper.assertTrue(menu.clickMenuButton(p, LightsaberWorkbenchMenu.BUTTON_CONSTRUCT), msg("Construct builds the saber"));
			ItemStack saber = menu.getSlot(SaberAssembly.SABER).getItem();
			helper.assertTrue(SaberComponents.config(saber).materials().equals(forged), msg("the saber keeps the forged materials"));
			helper.assertTrue(Credits.count(p, false) == 30 && menu.getSlot(SaberAssembly.EMITTER).getItem().isEmpty(), msg("assembly is free"));

			// A retune at the bench changes the crystal, never the hilt's materials.
			SaberBalance sb = SaberToggle.balance();

			for (Schematic.Input input : SaberTunings.materials(CrystalTuning.KEEN, sb)) {
				input.what().left().ifPresent(item -> p.getInventory().add(new ItemStack(item, input.count())));
			}

			Credits.give(p, sb.tuneCredits);
			helper.assertTrue(menu.clickMenuButton(p, LightsaberWorkbenchMenu.BUTTON_TUNE + LightsaberWorkbenchMenu.TUNE_SABER * LightsaberWorkbenchMenu.TUNE_STRIDE
					+ CrystalTuning.KEEN.ordinal()), msg("the saber on the bench is tuned"));
			SaberConfig tuned = SaberComponents.config(menu.getSlot(SaberAssembly.SABER).getItem());
			helper.assertTrue(tuned.crystal().tuning().equals(CrystalTuning.KEEN.id()) && tuned.materials().equals(forged), msg("a retune keeps the hilt's materials"));

			helper.assertTrue(menu.clickMenuButton(p, LightsaberWorkbenchMenu.BUTTON_DISASSEMBLE), msg("Disassemble takes it apart"));
			helper.assertTrue(ItemStack.isSameItemSameComponents(menu.getSlot(SaberAssembly.EMITTER).getItem(), emitter)
					&& ItemStack.isSameItemSameComponents(menu.getSlot(SaberAssembly.GRIP).getItem(), grip)
					&& ItemStack.isSameItemSameComponents(menu.getSlot(SaberAssembly.POMMEL).getItem(), pommel),
					msg("each part comes back in the material it was forged in"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}
}
