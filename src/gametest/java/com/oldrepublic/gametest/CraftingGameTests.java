package com.oldrepublic.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingData;
import com.oldrepublic.crafting.CraftingData.Progress;
import com.oldrepublic.crafting.CraftingItems;
import com.oldrepublic.crafting.CraftingRules;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.crafting.Salvage;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.SchematicLoader;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.crafting.bench.BenchResult;
import com.oldrepublic.crafting.bench.BenchService;
import com.oldrepublic.crafting.bench.EngineeringBenchBlockEntity;
import com.oldrepublic.crafting.bench.Ingredients;
import com.oldrepublic.crafting.item.Consumables;
import com.oldrepublic.crafting.item.ScannerItem;
import com.oldrepublic.crafting.network.SchematicSyncPayload;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.equipment.EquipmentItems;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.mission.MissionData;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionData.Stage;
import com.oldrepublic.mission.MissionDefinition.Kind;
import com.oldrepublic.mission.MissionLoader;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberAssembly.Status;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberLens;
import com.oldrepublic.saber.SaberLens.Lens;
import com.oldrepublic.saber.SaberStaffs;
import com.oldrepublic.saber.SaberStats;
import com.oldrepublic.saber.SaberTechniques;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.TrainingSaberItem;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;

/**
 * Crafting and saber game tests (spec MILESTONE_4.md 17.1, W5): schematic data and sync, the
 * discipline and input gates, the tray-first take, quality odds and pity, discipline and
 * first-craft XP, the second-discipline gate, unlearning, salvage, the medpac, stims, the scanner's
 * survey, the saberstaff (build, whirl, single blade), lenses, the loader's unknown-item skip, the
 * training saber and the saved crafting data.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks} (at most 400); players are listed mock
 * players ({@link M3TestPlayers}), removed at the end; the one fixture is the lead's
 * {@code oldrepublic_test:arena_24x7x24}, otherwise the default empty structure; every random draw
 * is seeded; the loaded schematics are only read, never replaced. A rule's shape is checked against
 * the spec defaults ({@link #DEFAULTS}); live behaviour against the configured balance.
 *
 * <p>Mock players count as creative ({@code KNOWN_ISSUES}), so tests that need damage put them in
 * survival first; where a real hit cannot land on a mock player (spawn protection, PvP off), the
 * same Fabric event the hit would fire is invoked directly, so the rule is still checked.
 */
public class CraftingGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	/** The day index the survey contract is given. */
	private static final int DAY = 40;
	/** The spec's crafting numbers (14.3): rule shapes are checked against these. */
	private static final CraftingBalance DEFAULTS = new CraftingBalance();
	private static final UUID OWNER = new UUID(0x5ABE5ABE5ABE5ABEL, 0x0123456789ABCDEFL);
	private static final UUID STRANGER = new UUID(0x1111222233334444L, 0x5555666677778888L);

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-4;
	}

	private static Identifier id(String path) {
		return OldRepublicMod.id(path);
	}

	// ---------------------------------------------------------------- fixtures

	private static Schematic.Input input(Item item, int count) {
		return new Schematic.Input(Either.left(item), count);
	}

	/** The blaster pistol's inputs (data/oldrepublic/schematic/blaster_pistol.json). */
	private static List<Schematic.Input> pistolInputs() {
		return List.of(input(M3Items.DURASTEEL_PLATE, 3), input(Items.IRON_INGOT, 2), input(Items.REDSTONE, 2), input(Items.COPPER_INGOT, 1));
	}

	/** A test schematic that is not in the loaded set (tests never replace the loaded schematics). */
	private static Schematic schematic(String path, @Nullable Discipline discipline, int level, boolean quality, int xp, Schematic.Learned learned) {
		return new Schematic(id("test/" + path), Optional.ofNullable(discipline), level, pistolInputs(), new ItemStack(EnemyItems.BLASTER_PISTOL), quality, 40, xp,
				learned);
	}

	/** Total discipline XP at the start of {@code level} under the live balance. */
	private static int xpAt(int level) {
		int[] levelXp = CraftingBalance.get().levelXp;
		return levelXp.length == 0 ? 0 : levelXp[Math.max(0, Math.min(levelXp.length - 1, level - 1))];
	}

	/** {@code p}'s crafting data with {@code d} at {@code level} and {@code pity}. */
	private static void setDiscipline(ServerPlayer p, Discipline d, int level, int pity) {
		CraftingAttachments.set(p, CraftingAttachments.get(p).withProgress(new Progress(d, level, xpAt(level), pity)));
	}

	private static Progress progress(ServerPlayer p, Discipline d) {
		return CraftingAttachments.get(p).progress(d).orElseThrow(() -> new AssertionError("the player has no " + d.id()));
	}

	/** A player's total player XP: every level's XP below the current level plus the XP into it. */
	private static long totalXp(ServerPlayer p) {
		ForceData data = Progression.get(p);
		long total = data.xp;

		for (int level = 1; level < data.level; level++) {
			total += Progression.xpToNext(level);
		}

		return total;
	}

	private static int count(List<ItemStack> stacks, Item item) {
		int n = 0;

		for (ItemStack stack : stacks) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}

		return n;
	}

	private static int count(ServerPlayer p, Item item) {
		return count(p.getInventory().getNonEquipmentItems(), item);
	}

	private static @Nullable String key(@Nullable Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : null;
	}

	private static CrystalAttunement crystal(UUID owner, BladeColor color) {
		return new CrystalAttunement(owner, "Tester", color.rgb());
	}

	/** A zombie with a helmet (it never burns) and no AI. */
	private static Mob zombie(GameTestHelper helper, BlockPos relative) {
		Mob zombie = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, relative);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		return zombie;
	}

	private static float attackDamageModifier(ItemStack stack) {
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

		for (ItemAttributeModifiers.Entry e : modifiers.modifiers()) {
			if (e.attribute().value() == Attributes.ATTACK_DAMAGE.value()) {
				return (float) e.modifier().amount();
			}
		}

		return Float.NaN;
	}

	// ---------------------------------------------------------------- schematics (7.3)

	/** The data files load; the sync payload carries every loaded file, and the client parse of it matches the server's set. */
	@GameTest(maxTicks = 20)
	public void schematicJsonLoadsAndSyncs(GameTestHelper helper) {
		Schematic medpac = Schematics.get(id("medpac"));
		helper.assertTrue(medpac != null, msg("data/oldrepublic/schematic/medpac.json must be loaded"));
		helper.assertTrue(medpac.discipline().equals(Optional.of(Discipline.MEDTECH)) && medpac.level() == 1 && !medpac.quality()
				&& medpac.output().is(CraftingItems.MEDPAC) && medpac.output().getCount() == 2 && medpac.inputs().size() == 3
				&& medpac.learned() == Schematic.Learned.AUTO && medpac.craftTicks() == 30 && medpac.xp() == 20,
				msg("medpac: Medtech 1, no quality, two medpacs from three inputs, 30 ticks, 20 XP, auto"));

		Schematic pistol = Schematics.get(id("blaster_pistol"));
		helper.assertTrue(pistol != null && pistol.discipline().equals(Optional.of(Discipline.ARMSTECH)) && pistol.quality()
				&& pistol.inputs().equals(pistolInputs()) && pistol.output().is(EnemyItems.BLASTER_PISTOL),
				msg("blaster_pistol: an Armstech quality schematic with its four inputs"));

		for (String own : List.of("grip_staff", "lens_honed", "lens_steady", "lens_resonant", "frag_grenade", "scanner")) {
			helper.assertTrue(Schematics.get(id(own)) != null, msg("W5's schematic " + own + " must be loaded"));
		}

		Map<Identifier, String> json = SchematicLoader.serverJson();
		Set<Identifier> loaded = new HashSet<>();
		Schematics.all().forEach(s -> loaded.add(s.id()));
		helper.assertTrue(json.keySet().equals(loaded), msg("the sync text covers exactly the loaded schematics: " + json.size() + " vs " + loaded.size()));

		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());

		try {
			SchematicSyncPayload.CODEC.encode(buf, new SchematicSyncPayload(json));
			SchematicSyncPayload back = SchematicSyncPayload.CODEC.decode(buf);
			helper.assertTrue(back.schematics().equals(json), msg("schematic_sync survives its stream codec"));

			List<Schematic> parsed = SchematicLoader.parseAll(back.schematics(), null);
			helper.assertTrue(parsed.size() == loaded.size(), msg("the client parses every synced schematic: " + parsed.size() + " of " + loaded.size()));

			for (Schematic client : parsed) {
				Schematic server = Schematics.get(client.id());
				helper.assertTrue(server != null && client.discipline().equals(server.discipline()) && client.level() == server.level()
						&& client.inputs().equals(server.inputs()) && ItemStack.matches(client.output(), server.output())
						&& client.quality() == server.quality() && client.craftTicks() == server.craftTicks() && client.xp() == server.xp()
						&& client.learned() == server.learned(), msg("the client's " + client.id() + " matches the server's"));
			}
		} finally {
			buf.release();
		}

		helper.succeed();
	}

	/** A file naming an unregistered item is skipped (logged) and the others still load; a bad discipline is refused the same way. */
	@GameTest(maxTicks = 20)
	public void unknownItemIdsSkippedWithWarning(GameTestHelper helper) {
		String good = "{\"discipline\":\"armstech\",\"level\":2,\"inputs\":[{\"item\":\"oldrepublic:durasteel_plate\",\"count\":2},"
				+ "{\"tag\":\"minecraft:planks\"}],\"output\":{\"item\":\"oldrepublic:blaster_pistol\"}}";
		String unknownInput = "{\"discipline\":\"armstech\",\"inputs\":[{\"item\":\"oldrepublic:no_such_part\",\"count\":2}],"
				+ "\"output\":{\"item\":\"oldrepublic:blaster_pistol\"}}";
		String unknownOutput = "{\"inputs\":[{\"item\":\"minecraft:iron_ingot\"}],\"output\":{\"item\":\"oldrepublic:no_such_gun\"}}";
		String badDiscipline = "{\"discipline\":\"jedi\",\"inputs\":[{\"item\":\"minecraft:iron_ingot\"}],\"output\":{\"item\":\"minecraft:iron_ingot\"}}";
		String bothKinds = "{\"inputs\":[{\"item\":\"minecraft:iron_ingot\",\"tag\":\"minecraft:planks\"}],\"output\":{\"item\":\"minecraft:iron_ingot\"}}";

		helper.assertTrue(SchematicLoader.unknownItem(JsonParser.parseString(unknownInput)).equals(Optional.of(id("no_such_part"))),
				msg("an unregistered input item is found"));
		helper.assertTrue(SchematicLoader.unknownItem(JsonParser.parseString(unknownOutput)).equals(Optional.of(id("no_such_gun"))),
				msg("an unregistered output item is found"));
		helper.assertTrue(SchematicLoader.unknownItem(JsonParser.parseString(good)).isEmpty(), msg("a good file names no unknown item"));
		helper.assertTrue(SchematicLoader.parse(id("test/unknown_input"), JsonParser.parseString(unknownInput)) == null, msg("an unknown input skips the file"));
		helper.assertTrue(SchematicLoader.parse(id("test/unknown_output"), JsonParser.parseString(unknownOutput)) == null, msg("an unknown output skips the file"));
		helper.assertTrue(SchematicLoader.parse(id("test/bad_discipline"), JsonParser.parseString(badDiscipline)) == null, msg("an unknown discipline skips the file"));
		helper.assertTrue(SchematicLoader.parse(id("test/both"), JsonParser.parseString(bothKinds)) == null, msg("an input with both item and tag is refused"));

		Schematic parsed = SchematicLoader.parse(id("test/good"), JsonParser.parseString(good));
		helper.assertTrue(parsed != null && parsed.id().equals(id("test/good")) && parsed.discipline().equals(Optional.of(Discipline.ARMSTECH))
				&& parsed.level() == 2 && parsed.inputs().size() == 2 && parsed.inputs().get(1).count() == 1 && parsed.inputs().get(1).what().right().isPresent()
				&& parsed.output().is(EnemyItems.BLASTER_PISTOL) && !parsed.quality() && parsed.craftTicks() == 40 && parsed.xp() == 0
				&& parsed.learned() == Schematic.Learned.AUTO, msg("a good file parses with the defaults: no quality, 40 ticks, 0 XP, auto"));

		Map<Identifier, String> texts = new java.util.LinkedHashMap<>();
		texts.put(id("test/unknown_input"), unknownInput);
		texts.put(id("test/good"), good);
		texts.put(id("test/broken"), "{ not json");
		texts.put(id("test/unknown_output"), unknownOutput);
		Map<Identifier, String> kept = new java.util.LinkedHashMap<>();
		List<Schematic> all = SchematicLoader.parseAll(texts, kept);
		helper.assertTrue(all.size() == 1 && all.get(0).id().equals(id("test/good")) && kept.keySet().equals(Set.of(id("test/good"))),
				msg("the rest still load: only the good file is kept, and only its text is synced"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the bench (7.1, 7.2)

	/** A discipline schematic needs the discipline at its level (and to be known); common ones need none; the inputs must all be there. */
	@GameTest(maxTicks = 20)
	public void craftNeedsDisciplineLevelAndInputs(GameTestHelper helper) {
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			Schematic auto1 = schematic("auto1", Discipline.ARMSTECH, 1, true, 20, Schematic.Learned.AUTO);
			Schematic auto3 = schematic("auto3", Discipline.ARMSTECH, 3, true, 60, Schematic.Learned.AUTO);
			Schematic taught3 = schematic("taught3", Discipline.ARMSTECH, 3, true, 60, Schematic.Learned.ITEM);
			Schematic medtech1 = schematic("medtech1", Discipline.MEDTECH, 1, false, 20, Schematic.Learned.AUTO);
			Schematic common = schematic("common", null, 1, false, 0, Schematic.Learned.AUTO);

			CraftingData none = CraftingData.EMPTY;
			helper.assertTrue(key(Crafting.craftRefusal(none, auto1)) != null, msg("without Armstech an Armstech schematic is refused"));
			helper.assertTrue(Crafting.craftRefusal(none, common) == null, msg("a common schematic needs no discipline"));

			CraftingData one = none.withProgress(new Progress(Discipline.ARMSTECH, 1, 0, 0));
			helper.assertTrue(Crafting.craftRefusal(one, auto1) == null, msg("Armstech 1 crafts a level 1 Armstech schematic"));
			helper.assertTrue("crafting.oldrepublic.refuse.unknown".equals(key(Crafting.craftRefusal(one, auto3))),
					msg("an auto schematic two levels up is not known yet"));
			helper.assertTrue(key(Crafting.craftRefusal(one, medtech1)) != null, msg("another discipline's schematic is refused"));

			CraftingData taught = one.withKnown(taught3.id());
			helper.assertTrue(Crafting.knows(taught, taught3) && "crafting.oldrepublic.refuse.level".equals(key(Crafting.craftRefusal(taught, taught3))),
					msg("a taught schematic above the crafter's level is known but refused for the level"));

			CraftingData three = taught.withProgress(new Progress(Discipline.ARMSTECH, 3, xpAt(3), 0));
			helper.assertTrue(Crafting.craftRefusal(three, auto3) == null && Crafting.craftRefusal(three, taught3) == null,
					msg("at Armstech 3 both level 3 schematics craft"));
			helper.assertTrue(Crafting.craftable(CraftingData.EMPTY).stream().allMatch(s -> s.discipline().isEmpty()),
					msg("with no discipline only common schematics are craftable"));

			// Inputs: none, then all of them, then one short.
			List<Schematic.Input> inputs = auto1.inputs();
			helper.assertTrue(!Ingredients.has(Ingredients.sources(null, p), inputs), msg("an empty inventory has no inputs"));
			p.getInventory().add(new ItemStack(M3Items.DURASTEEL_PLATE, 3));
			p.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
			p.getInventory().add(new ItemStack(Items.REDSTONE, 2));
			helper.assertTrue(!Ingredients.has(Ingredients.sources(null, p), inputs), msg("one input short (the copper) is not enough"));
			p.getInventory().add(new ItemStack(Items.COPPER_INGOT, 1));
			helper.assertTrue(Ingredients.has(Ingredients.sources(null, p), inputs), msg("every input present"));
			helper.assertTrue(Ingredients.count(Ingredients.sources(null, p), inputs.get(0)) == 3, msg("three durasteel plates are counted"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** Inputs come from the tray first, then the inventory; named items never count; a short craft takes nothing. */
	@GameTest(maxTicks = 20)
	public void craftTakesFromTrayAndInventory(GameTestHelper helper) {
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			SimpleContainer tray = new SimpleContainer(EngineeringBenchBlockEntity.TRAY_SIZE);
			tray.setItem(0, new ItemStack(M3Items.DURASTEEL_PLATE, 2));
			tray.setItem(1, new ItemStack(Items.IRON_INGOT, 2));
			ItemStack named = new ItemStack(Items.REDSTONE, 2);
			named.set(M4Components.NAMED, "test_named");
			tray.setItem(2, named);
			p.getInventory().add(new ItemStack(M3Items.DURASTEEL_PLATE, 5));
			p.getInventory().add(new ItemStack(Items.REDSTONE, 2));
			p.getInventory().add(new ItemStack(Items.COPPER_INGOT, 1));

			helper.assertTrue(Ingredients.take(tray, p, pistolInputs()), msg("the inputs are all there, the tray's and the inventory's together"));
			helper.assertTrue(tray.getItem(0).isEmpty() && tray.getItem(1).isEmpty(), msg("the tray's durasteel and iron go first"));
			helper.assertTrue(tray.getItem(2).getCount() == 2, msg("named redstone in the tray is never taken"));
			helper.assertTrue(count(p, M3Items.DURASTEEL_PLATE) == 4 && count(p, Items.REDSTONE) == 0 && count(p, Items.COPPER_INGOT) == 0,
					msg("the rest come from the inventory: one durasteel, the redstone and the copper"));

			// One short: nothing is taken.
			p.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
			p.getInventory().add(new ItemStack(Items.REDSTONE, 2));
			helper.assertTrue(!Ingredients.take(tray, p, pistolInputs()), msg("without copper the craft cannot take its inputs"));
			helper.assertTrue(count(p, M3Items.DURASTEEL_PLATE) == 4 && count(p, Items.IRON_INGOT) == 2 && count(p, Items.REDSTONE) == 2
					&& tray.getItem(2).getCount() == 2, msg("a refused take changes nothing (all or nothing)"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- quality and pity (7.4)

	/** Fine 18% and Masterwork 3% at the schematic's level, +6% and +3% per level above it; seeded draws land on those odds. */
	@GameTest(maxTicks = 20)
	public void qualityOddsByLevel(GameTestHelper helper) {
		CraftingBalance b = DEFAULTS;
		helper.assertTrue(near(CraftingRules.fineChance(1, 1, b), 0.18) && near(CraftingRules.masterworkChance(1, 1, b), 0.03),
				msg("at the schematic's level: Fine 18%, Masterwork 3%"));
		helper.assertTrue(near(CraftingRules.fineChance(5, 2, b), 0.36) && near(CraftingRules.masterworkChance(5, 2, b), 0.12),
				msg("three levels above: Fine 36%, Masterwork 12%"));
		helper.assertTrue(near(CraftingRules.fineChance(1, 3, b), 0.18) && near(CraftingRules.masterworkChance(1, 3, b), 0.03),
				msg("a schematic above the crafter never lowers the odds below the base"));

		helper.assertTrue(CraftingRules.pick(0.18f, 0.03f, 0.0f) == Quality.MASTERWORK && CraftingRules.pick(0.18f, 0.03f, 0.029f) == Quality.MASTERWORK
				&& CraftingRules.pick(0.18f, 0.03f, 0.03f) == Quality.FINE && CraftingRules.pick(0.18f, 0.03f, 0.209f) == Quality.FINE
				&& CraftingRules.pick(0.18f, 0.03f, 0.211f) == Quality.STANDARD && CraftingRules.pick(0.18f, 0.03f, 0.999f) == Quality.STANDARD,
				msg("one draw: Masterwork below 0.03, Fine below 0.21, Standard above"));

		int[][] levels = {{1, 1}, {5, 2}};

		for (int[] at : levels) {
			RandomSource random = RandomSource.create(0xC0FFEEL + at[0]);
			float fine = CraftingRules.fineChance(at[0], at[1], b);
			float masterwork = CraftingRules.masterworkChance(at[0], at[1], b);
			int n = 20000;
			int[] counts = new int[Quality.values().length];

			for (int i = 0; i < n; i++) {
				counts[CraftingRules.pick(fine, masterwork, random.nextFloat()).ordinal()]++;
			}

			double fineRate = counts[Quality.FINE.ordinal()] / (double) n;
			double masterworkRate = counts[Quality.MASTERWORK.ordinal()] / (double) n;
			helper.assertTrue(Math.abs(fineRate - fine) < 0.015 && Math.abs(masterworkRate - masterwork) < 0.01,
					msg("level " + at[0] + " making level " + at[1] + ": seeded rates " + fineRate + " / " + masterworkRate + " for " + fine + " / " + masterwork));
		}

		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			setDiscipline(p, Discipline.ARMSTECH, 2, 3);
			Schematic plain = schematic("plain", Discipline.ARMSTECH, 1, false, 20, Schematic.Learned.AUTO);
			RandomSource random = RandomSource.create(7L);

			for (int i = 0; i < 20; i++) {
				helper.assertTrue(Crafting.roll(p, plain, random) == Quality.STANDARD, msg("a schematic without quality is always Standard"));
			}

			helper.assertTrue(progress(p, Discipline.ARMSTECH).pity() == 3, msg("crafts without quality leave the pity count alone"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** Every quality craft below Masterwork adds one; at 10 the next is Masterwork without a draw and the count resets. */
	@GameTest(maxTicks = 20)
	public void pityGuaranteesMasterworkAt10(GameTestHelper helper) {
		helper.assertTrue(DEFAULTS.pityCrafts == 10, msg("spec 14.3: pityCrafts 10"));
		helper.assertTrue(!CraftingRules.pityDue(9, DEFAULTS) && CraftingRules.pityDue(10, DEFAULTS), msg("pity is due at 10, not 9"));
		helper.assertTrue(CraftingRules.pityAfter(4, Quality.STANDARD) == 5 && CraftingRules.pityAfter(4, Quality.FINE) == 5
				&& CraftingRules.pityAfter(9, Quality.MASTERWORK) == 0, msg("below Masterwork adds one; a Masterwork resets"));
		helper.assertTrue(CraftingRules.craftsToGuarantee(10, DEFAULTS) == 1 && CraftingRules.craftsToGuarantee(0, DEFAULTS) == 11,
				msg("the Craft tab's count: the next craft at 10, the eleventh from 0"));

		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			CraftingBalance b = CraftingBalance.get();
			helper.assertTrue(b.pityCrafts > 0, msg("precondition: pity is on in the live balance"));
			Schematic s = schematic("pity", Discipline.ARMSTECH, 1, true, 20, Schematic.Learned.AUTO);
			setDiscipline(p, Discipline.ARMSTECH, 1, 0);
			RandomSource random = RandomSource.create(0x9177L);
			int rolls = 0;

			while (progress(p, Discipline.ARMSTECH).pity() < b.pityCrafts) {
				helper.assertTrue(rolls++ < 1000, msg("the pity count never reached " + b.pityCrafts));
				int before = progress(p, Discipline.ARMSTECH).pity();
				Quality q = Crafting.roll(p, s, random);
				int after = progress(p, Discipline.ARMSTECH).pity();
				helper.assertTrue(q == Quality.MASTERWORK ? after == 0 : after == before + 1,
						msg("roll " + rolls + " was " + q + ": pity " + before + " -> " + after));
			}

			helper.assertTrue(Crafting.roll(p, s, random) == Quality.MASTERWORK, msg("at " + b.pityCrafts + " the next quality craft is Masterwork"));
			helper.assertTrue(progress(p, Discipline.ARMSTECH).pity() == 0, msg("the guaranteed Masterwork resets the count"));

			// The count is per discipline: Medtech's is untouched by Armstech crafts.
			CraftingAttachments.set(p, CraftingAttachments.get(p).withProgress(new Progress(Discipline.MEDTECH, 1, 0, 6)));
			Crafting.roll(p, s, random);
			helper.assertTrue(progress(p, Discipline.MEDTECH).pity() == 6, msg("another discipline's pity count does not move"));
			helper.assertTrue(Crafting.isMasterworkOnly(schematic("lens_masterwork", Discipline.ARTIFICE, 5, true, 0, Schematic.Learned.TRAINER))
					&& !Crafting.isMasterworkOnly(s), msg("a _masterwork recipe is Masterwork-only"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- XP (7.2)

	/** A schematic two or more levels below the crafter gives a quarter of its discipline XP. */
	@GameTest(maxTicks = 20)
	public void lowLevelXpQuarter(GameTestHelper helper) {
		helper.assertTrue(CraftingRules.craftXp(20, 3, 1, DEFAULTS) == 5 && CraftingRules.craftXp(40, 5, 2, DEFAULTS) == 10,
				msg("two or more levels below: a quarter"));
		helper.assertTrue(CraftingRules.craftXp(20, 2, 1, DEFAULTS) == 20 && CraftingRules.craftXp(20, 1, 1, DEFAULTS) == 20,
				msg("one level below or at level: full XP"));

		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			CraftingBalance b = CraftingBalance.get();
			setDiscipline(p, Discipline.ARMSTECH, 3, 0);
			int start = progress(p, Discipline.ARMSTECH).xp();
			Schematic low = schematic("low", Discipline.ARMSTECH, 1, true, 20, Schematic.Learned.AUTO);
			Crafting.Award award = Crafting.awardCraft(p, low, Quality.STANDARD);
			int quarter = CraftingRules.craftXp(20, 3, 1, b);
			helper.assertTrue(award.disciplineXp() == quarter && progress(p, Discipline.ARMSTECH).xp() == start + quarter,
					msg("Armstech 3 making a level 1 schematic gains " + quarter + ", got " + award.disciplineXp()));

			Schematic close = schematic("close", Discipline.ARMSTECH, 2, true, 20, Schematic.Learned.AUTO);
			int before = progress(p, Discipline.ARMSTECH).xp();
			Crafting.Award full = Crafting.awardCraft(p, close, Quality.FINE);
			helper.assertTrue(full.disciplineXp() == 20 && progress(p, Discipline.ARMSTECH).xp() == before + 20, msg("a level 2 schematic gives its full 20"));

			Schematic other = schematic("other", Discipline.MEDTECH, 1, false, 20, Schematic.Learned.AUTO);
			helper.assertTrue(Crafting.awardCraft(p, other, Quality.STANDARD).disciplineXp() == 0, msg("a discipline the player lacks gains nothing"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** The first craft of each schematic gives player XP (30 per schematic level) once. */
	@GameTest(maxTicks = 20)
	public void firstCraftPlayerXpOnce(GameTestHelper helper) {
		helper.assertTrue(CraftingRules.firstCraftXp(1, DEFAULTS) == 30 && CraftingRules.firstCraftXp(3, DEFAULTS) == 90, msg("30 player XP per schematic level"));
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			CraftingBalance b = CraftingBalance.get();
			setDiscipline(p, Discipline.ARMSTECH, 2, 0);
			Schematic s = schematic("first", Discipline.ARMSTECH, 2, true, 20, Schematic.Learned.AUTO);
			long start = totalXp(p);
			Crafting.Award first = Crafting.awardCraft(p, s, Quality.STANDARD);
			long afterFirst = totalXp(p);
			helper.assertTrue(first.firstCraft() && afterFirst - start == CraftingRules.firstCraftXp(2, b),
					msg("the first craft gives " + CraftingRules.firstCraftXp(2, b) + " player XP, got " + (afterFirst - start)));
			helper.assertTrue(CraftingAttachments.get(p).firstCrafts().contains(s.id()), msg("the first craft is recorded"));

			Crafting.Award second = Crafting.awardCraft(p, s, Quality.MASTERWORK);
			helper.assertTrue(!second.firstCraft() && totalXp(p) == afterFirst, msg("the second craft gives no player XP"));

			Schematic another = schematic("first_other", Discipline.ARMSTECH, 1, true, 20, Schematic.Learned.AUTO);
			Crafting.awardCraft(p, another, Quality.STANDARD);
			helper.assertTrue(totalXp(p) - afterFirst == CraftingRules.firstCraftXp(1, b), msg("another schematic's first craft pays again"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** The second discipline opens at level 2 in the first (or player level 13); a third is never allowed. */
	@GameTest(maxTicks = 20)
	public void secondDisciplineGate(GameTestHelper helper) {
		helper.assertTrue(CraftingRules.secondOpen(2, 1, DEFAULTS) && CraftingRules.secondOpen(1, 13, DEFAULTS) && !CraftingRules.secondOpen(1, 12, DEFAULTS),
				msg("open at discipline level 2 or player level 13"));
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			CraftingBalance b = CraftingBalance.get();
			helper.assertTrue(Progression.get(p).level < b.secondDisciplinePlayerLevel, msg("precondition: a new player is below level " + b.secondDisciplinePlayerLevel));
			helper.assertTrue(Crafting.choose(p, Discipline.ARMSTECH) == Crafting.ChoiceResult.CHOSEN && Crafting.level(p, Discipline.ARMSTECH) == 1,
					msg("the first discipline is free and starts at level 1"));
			helper.assertTrue(CraftingAttachments.get(p).chosenAt() >= 0, msg("the day of the first choice is kept"));
			helper.assertTrue(Crafting.choose(p, Discipline.ARMSTECH) == Crafting.ChoiceResult.ALREADY_HAS, msg("choosing it again does nothing"));
			helper.assertTrue(!Crafting.secondOpen(p) && Crafting.choose(p, Discipline.MEDTECH) == Crafting.ChoiceResult.SECOND_LOCKED
					&& !Crafting.has(p, Discipline.MEDTECH), msg("the second is locked at Armstech 1"));

			setDiscipline(p, Discipline.ARMSTECH, b.secondDisciplineLevel, 0);
			helper.assertTrue(Crafting.secondOpen(p) && Crafting.choose(p, Discipline.MEDTECH) == Crafting.ChoiceResult.CHOSEN
					&& Crafting.level(p, Discipline.MEDTECH) == 1, msg("at Armstech " + b.secondDisciplineLevel + " the second opens"));
			helper.assertTrue(Crafting.choose(p, Discipline.ARTIFICE) == Crafting.ChoiceResult.FULL && !Crafting.has(p, Discipline.ARTIFICE),
					msg("a third discipline is refused"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** A trainer's unlearn costs 500 credits (refused without them), drops level, XP and pity, and keeps learned schematics locked. */
	@GameTest(maxTicks = 20)
	public void unlearnCostsAndResets(GameTestHelper helper) {
		helper.assertTrue(DEFAULTS.unlearnCost == 500, msg("spec 14.3: unlearnCost 500"));
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			CraftingBalance b = CraftingBalance.get();
			DialogueActions.ArgAction unlearn = DialogueActions.getArg("discipline:unlearn");
			helper.assertTrue(unlearn != null, msg("the discipline:unlearn dialogue action is registered"));
			Crafting.choose(p, Discipline.ARMSTECH);
			setDiscipline(p, Discipline.ARMSTECH, 3, 4);
			Identifier taughtId = id("test/taught_rifle");
			Crafting.teach(p, taughtId);
			Schematic taught = new Schematic(taughtId, Optional.of(Discipline.ARMSTECH), 2, pistolInputs(), new ItemStack(EnemyItems.BLASTER_PISTOL), true, 40, 30,
					Schematic.Learned.ITEM);
			helper.assertTrue(Crafting.craftRefusal(CraftingAttachments.get(p), taught) == null, msg("precondition: the taught schematic crafts at Armstech 3"));

			unlearn.run(p, null, "armstech");
			helper.assertTrue(Crafting.level(p, Discipline.ARMSTECH) == 3, msg("without " + b.unlearnCost + " credits the trainer refuses"));

			Credits.give(p, b.unlearnCost + 100);
			helper.assertTrue(Credits.count(p, false) == b.unlearnCost + 100, msg("precondition: the player carries the fee and 100 more"));
			unlearn.run(p, null, "armstech");
			CraftingData data = CraftingAttachments.get(p);
			helper.assertTrue(!data.has(Discipline.ARMSTECH) && data.unlearned().contains(Discipline.ARMSTECH), msg("Armstech is dropped and listed as unlearned"));
			helper.assertTrue(Credits.count(p, false) == 100, msg("the fee is taken, the change kept: " + Credits.count(p, false)));
			helper.assertTrue(data.known().contains(taughtId) && key(Crafting.craftRefusal(data, taught)) != null,
					msg("the learned schematic stays known but locked"));

			unlearn.run(p, null, "medtech");
			helper.assertTrue(Credits.count(p, false) == 100, msg("unlearning a discipline the player lacks costs nothing"));

			helper.assertTrue(Crafting.choose(p, Discipline.ARMSTECH) == Crafting.ChoiceResult.CHOSEN, msg("Armstech can be chosen again"));
			Progress restarted = progress(p, Discipline.ARMSTECH);
			helper.assertTrue(restarted.level() == 1 && restarted.xp() == 0 && restarted.pity() == 0 && !CraftingAttachments.get(p).unlearned().contains(Discipline.ARMSTECH),
					msg("it starts over at level 1 with no XP or pity"));
			setDiscipline(p, Discipline.ARMSTECH, 2, 0);
			helper.assertTrue(Crafting.craftRefusal(CraftingAttachments.get(p), taught) == null, msg("its learned schematic unlocks at the level again"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- salvage (7.5)

	/** Half of each input (rounded down, at least one of the main input), the fitted mods, and a fragment for a Masterwork. */
	@GameTest(maxTicks = 20)
	public void salvageReturnsHalfAndMods(GameTestHelper helper) {
		helper.assertTrue(near(DEFAULTS.salvageReturn, 0.5) && CraftingRules.salvageCount(3, true, 0.5f) == 1 && CraftingRules.salvageCount(1, true, 0.5f) == 1
				&& CraftingRules.salvageCount(1, false, 0.5f) == 0 && CraftingRules.salvageCount(5, false, 0.5f) == 2,
				msg("half, rounded down; the main input returns at least one"));

		ItemStack pistol = new ItemStack(EnemyItems.BLASTER_PISTOL);
		Schematic s = Salvage.schematicFor(pistol);
		helper.assertTrue(s != null && s.id().equals(id("blaster_pistol")), msg("precondition: the blaster pistol's schematic is loaded"));

		ItemStack mod = new ItemStack(M4Items.MOD_FOCUSED_BARREL);
		ModSlot slot = Mods.slotOf(mod);
		helper.assertTrue(slot != null && Mods.fits(pistol, slot, mod), msg("precondition: a focused barrel fits a blaster pistol"));
		Mods.fit(pistol, slot, mod);
		helper.assertTrue(mod.isEmpty() && Mods.has(pistol, M4Items.MOD_FOCUSED_BARREL), msg("precondition: the mod is fitted"));

		List<ItemStack> back = Salvage.returns(pistol, 0.5f);
		helper.assertTrue(count(back, M3Items.DURASTEEL_PLATE) == 1 && count(back, Items.IRON_INGOT) == 1 && count(back, Items.REDSTONE) == 1
				&& count(back, Items.COPPER_INGOT) == 0, msg("3 durasteel, 2 iron, 2 redstone and 1 copper give back 1, 1, 1 and 0: " + back));
		helper.assertTrue(count(back, M4Items.MOD_FOCUSED_BARREL) == 1 && count(back, M4Items.MASTERWORK_FRAGMENT) == 0,
				msg("the fitted mod comes back unchanged; a Standard pistol gives no fragment"));

		ItemStack masterwork = Crafting.applyQuality(new ItemStack(EnemyItems.BLASTER_PISTOL), Quality.MASTERWORK);
		helper.assertTrue(count(Salvage.returns(masterwork, 0.5f), M4Items.MASTERWORK_FRAGMENT) == 1, msg("a Masterwork adds a masterwork fragment"));

		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			CraftingBalance b = CraftingBalance.get();
			setDiscipline(p, Discipline.ARMSTECH, 1, 0);
			int xp = progress(p, Discipline.ARMSTECH).xp();
			helper.assertTrue(BenchService.salvage(p, pistol) == BenchResult.SALVAGED, msg("the bench salvages the pistol"));
			helper.assertTrue(count(p, M3Items.DURASTEEL_PLATE) == CraftingRules.salvageCount(3, true, b.salvageReturn)
					&& count(p, M4Items.MOD_FOCUSED_BARREL) == 1, msg("the returns and the mod reach the inventory"));
			helper.assertTrue(progress(p, Discipline.ARMSTECH).xp() == xp + Math.round(s.xp() * b.salvageXp),
					msg("salvage gives a share of the craft XP to the discipline"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** Named drops, story items, M3 robes and plain materials are never salvaged. */
	@GameTest(maxTicks = 20)
	public void namedAndStoryItemsCannotSalvage(GameTestHelper helper) {
		helper.assertTrue(Salvage.canSalvage(new ItemStack(EnemyItems.BLASTER_PISTOL)), msg("precondition: a plain blaster pistol can be salvaged"));

		ItemStack named = new ItemStack(EnemyItems.BLASTER_PISTOL);
		named.set(M4Components.NAMED, "test_named_pistol");
		helper.assertTrue(!Salvage.canSalvage(named) && Salvage.returns(named, 0.5f).isEmpty(), msg("a named drop is refused"));

		for (Item item : List.of(M3Items.SINGING_CRYSTAL, EquipmentItems.JEDI_KNIGHT_ROBE, M3Items.DURASTEEL_PLATE, CraftingItems.MEDPAC,
				SaberItems.TRAINING_SABER, SaberItems.LIGHTSABER)) {
			ItemStack stack = new ItemStack(item);
			helper.assertTrue(!Salvage.canSalvage(stack), msg(stack.getItem() + " is not made by a discipline schematic and cannot be salvaged"));
		}

		helper.assertTrue(!Salvage.canSalvage(ItemStack.EMPTY), msg("nothing is not salvageable"));
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			helper.assertTrue(BenchService.salvage(p, named) == BenchResult.NOT_SALVAGEABLE && count(p, M3Items.DURASTEEL_PLATE) == 0,
					msg("the bench refuses the named pistol and gives nothing"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- consumables and tools (7.6)

	/** A medpac heals 8 over 40 ticks and puts every healing item on the shared cooldown; a hit of 4 or more while using it interrupts. */
	@GameTest(structure = ARENA, maxTicks = 120)
	public void medpacSharedCooldownAndInterrupt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = M3TestPlayers.listed(helper, new BlockPos(4, 1, 4));
		CraftingBalance b = CraftingBalance.get();
		float[] start = new float[1];

		try {
			p.setGameMode(GameType.SURVIVAL);
			ItemStack medpac = new ItemStack(CraftingItems.MEDPAC, 3);
			p.setItemInHand(InteractionHand.MAIN_HAND, medpac);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(CraftingItems.KOLTO_INJECTOR));
			p.setHealth(6f);
			start[0] = p.getHealth();
			CraftingItems.MEDPAC.finishUsingItem(medpac, level, p);
			helper.assertTrue(Consumables.healing(p), msg("the medpac starts healing over time"));
			helper.assertTrue(p.getCooldowns().isOnCooldown(new ItemStack(CraftingItems.MEDPAC)) && p.getCooldowns().isOnCooldown(new ItemStack(CraftingItems.KOLTO_INJECTOR)),
					msg("the medpac and the kolto injector share the cooldown"));
			helper.assertTrue(CraftingItems.MEDPAC.use(level, p, InteractionHand.MAIN_HAND).equals(InteractionResult.FAIL)
					&& CraftingItems.KOLTO_INJECTOR.use(level, p, InteractionHand.OFF_HAND).equals(InteractionResult.FAIL),
					msg("neither can be used during the cooldown"));
		} catch (RuntimeException e) {
			M3TestPlayers.remove(p);
			throw e;
		}

		helper.runAfterDelay(b.medpacTicks + 5, () -> {
			try {
				float expected = Math.min(p.getMaxHealth(), start[0] + b.medpacHeal);
				helper.assertTrue(p.getHealth() >= expected - 0.05f && !Consumables.healing(p),
						msg("after " + b.medpacTicks + " ticks the medpac healed " + b.medpacHeal + ": " + start[0] + " -> " + p.getHealth()));

				// The interrupt, through the event a hit fires: below the threshold nothing happens, at it the use stops.
				DamageSource hit = level.damageSources().generic();
				p.startUsingItem(InteractionHand.MAIN_HAND);
				helper.assertTrue(p.isUsingItem() && p.getUseItem().is(CraftingItems.MEDPAC), msg("precondition: the player is using a medpac"));
				float small = b.medpacInterruptDamage - 1f;
				ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(p, hit, small, small, false);
				helper.assertTrue(p.isUsingItem(), msg("a hit of " + small + " does not interrupt"));
				ServerLivingEntityEvents.AFTER_DAMAGE.invoker().afterDamage(p, hit, b.medpacInterruptDamage, b.medpacInterruptDamage, false);
				helper.assertTrue(!p.isUsingItem(), msg("a hit of " + b.medpacInterruptDamage + " interrupts the medpac"));

				// End to end, when a real hit can land on the mock player.
				p.startUsingItem(InteractionHand.MAIN_HAND);
				float health = p.getHealth();
				p.invulnerableTime = 0;

				if (p.hurtServer(level, hit, b.medpacInterruptDamage + 1f) && p.getHealth() < health) {
					helper.assertTrue(!p.isUsingItem(), msg("a real hit of " + (b.medpacInterruptDamage + 1f) + " interrupts the medpac"));
				}
			} finally {
				M3TestPlayers.remove(p);
			}

			helper.succeed();
		});
	}

	/** A stim runs 1,200 ticks, then crashes for 200; one at a time. */
	@GameTest(maxTicks = 20)
	public void stimCrash(GameTestHelper helper) {
		CraftingBalance d = DEFAULTS;
		helper.assertTrue(d.stimTicks == 1200 && d.crashTicks == 200 && near(d.combatStimPct, 8) && near(d.focusStimPct, 20),
				msg("spec 14.3: 1,200 ticks, a 200-tick crash, +8% damage, +20% regen"));
		long at = 5000L;
		helper.assertTrue(Consumables.phase(at, at, d) == Consumables.Phase.ACTIVE && Consumables.phase(at, at + 1199, d) == Consumables.Phase.ACTIVE,
				msg("active for 1,200 ticks"));
		helper.assertTrue(Consumables.phase(at, at + 1200, d) == Consumables.Phase.CRASH && Consumables.phase(at, at + 1399, d) == Consumables.Phase.CRASH,
				msg("then the crash for 200"));
		helper.assertTrue(Consumables.phase(at, at + 1400, d) == Consumables.Phase.DONE, msg("then done"));

		ServerLevel level = helper.getLevel();
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CraftingItems.COMBAT_STIM, 2));
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(CraftingItems.FOCUS_STIM));
			helper.assertTrue(Consumables.current(p) == null, msg("precondition: no stim running"));
			helper.assertTrue(!CraftingItems.COMBAT_STIM.use(level, p, InteractionHand.MAIN_HAND).equals(InteractionResult.FAIL)
					&& Consumables.current(p) == Consumables.Stim.COMBAT && Consumables.phase(p, Consumables.Stim.COMBAT) == Consumables.Phase.ACTIVE,
					msg("a combat stim takes"));
			helper.assertTrue(Consumables.phase(p, Consumables.Stim.FOCUS) == Consumables.Phase.DONE, msg("the focus stim is not running"));
			helper.assertTrue(CraftingItems.FOCUS_STIM.use(level, p, InteractionHand.OFF_HAND).equals(InteractionResult.FAIL)
					&& CraftingItems.COMBAT_STIM.use(level, p, InteractionHand.MAIN_HAND).equals(InteractionResult.FAIL)
					&& Consumables.current(p) == Consumables.Stim.COMBAT, msg("a second stim is refused while one runs"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** A scanner pulse at the survey heart counts for the survey, like Sense; one far from it does not; the scanner then cools down. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void scannerCountsSurvey(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = M3TestPlayers.listed(helper, new BlockPos(12, 1, 12));
		BlockPos here = p.blockPosition();

		try {
			helper.assertTrue(MissionLoader.get("survey") != null, msg("precondition: the survey contract is loaded"));
			Active survey = new Active("survey", "survey", Kind.CONTRACT, Faction.REPUBLIC.id(), DAY, 0x5EEDL, null);
			survey.stage = Stage.ACTIVE;
			survey.site = here.offset(40, 0, 0);
			survey.siteDimension = level.dimension();
			MissionData data = MissionService.get(p);
			data.active = survey;
			MissionService.commit(p, data);

			ScannerItem.pulse(p);
			MissionData far = MissionService.get(p);
			helper.assertTrue(far.active != null && !far.surveyed.contains(here.offset(40, 0, 0)), msg("a pulse 40 blocks from the heart does not count"));

			far.active.site = here;
			MissionService.commit(p, far);
			ItemStack scanner = new ItemStack(CraftingItems.SCANNER);
			p.setItemInHand(InteractionHand.MAIN_HAND, scanner);
			helper.assertTrue(!CraftingItems.SCANNER.use(level, p, InteractionHand.MAIN_HAND).equals(InteractionResult.FAIL), msg("the scanner pulses"));
			helper.assertTrue(MissionService.get(p).surveyed.contains(here), msg("the pulse at the heart marks it surveyed"));
			helper.assertTrue(p.getCooldowns().isOnCooldown(scanner) && CraftingItems.SCANNER.use(level, p, InteractionHand.MAIN_HAND).equals(InteractionResult.FAIL),
					msg("the scanner cools down after a pulse"));
		} finally {
			MissionData clean = MissionService.get(p);
			clean.active = null;
			MissionService.commit(p, clean);
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the saberstaff (5.6)

	/** A staff needs the staff grip, an emitter, two of the builder's crystals and no pommel; it comes apart the same way. */
	@GameTest(maxTicks = 20)
	public void saberstaffBuildNeedsTwoCrystalsAndGrip(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		CrystalAttunement main = crystal(OWNER, BladeColor.BLUE);
		CrystalAttunement second = crystal(OWNER, BladeColor.GREEN);
		SimpleContainer c = new SimpleContainer(SaberAssembly.SIZE);
		c.setItem(SaberAssembly.CRYSTAL, SaberAssembly.crystalStack(main));
		c.setItem(SaberAssembly.EMITTER, new ItemStack(SaberItems.EMITTER_GUARDIAN));
		c.setItem(SaberAssembly.GRIP, new ItemStack(SaberItems.GRIP_STAFF));
		helper.assertTrue(SaberAssembly.isStaffBuild(c) && SaberAssembly.status(c, OWNER) == Status.STAFF_NEEDS_SECOND,
				msg("the staff grip makes a staff build, which needs a second crystal"));
		helper.assertTrue(SaberAssembly.assemble(c, OWNER, BladeColor.BLUE, b).isEmpty(), msg("one crystal builds nothing"));

		ItemStack pommel = new ItemStack(SaberItems.POMMEL_BALANCED);
		helper.assertTrue(!SaberAssembly.mayPlace(c, SaberAssembly.POMMEL, pommel, OWNER), msg("a staff build locks the pommel slot"));
		c.setItem(SaberAssembly.POMMEL, pommel);
		helper.assertTrue(SaberAssembly.status(c, OWNER) == Status.STAFF_HAS_POMMEL, msg("a pommel left in the slot blocks the staff"));
		c.setItem(SaberAssembly.POMMEL, ItemStack.EMPTY);

		helper.assertTrue(!SaberAssembly.mayPlace(SaberAssembly.SECOND, SaberAssembly.crystalStack(crystal(STRANGER, BladeColor.RED)), OWNER),
				msg("another player's crystal never goes in the second slot"));
		helper.assertTrue(!SaberAssembly.mayPlace(SaberAssembly.SECOND, new ItemStack(M3Items.SINGING_CRYSTAL), OWNER),
				msg("a singing crystal is only ever the main crystal"));
		c.setItem(SaberAssembly.SECOND, SaberAssembly.crystalStack(crystal(STRANGER, BladeColor.RED)));
		helper.assertTrue(SaberAssembly.status(c, OWNER) == Status.NOT_YOUR_CRYSTAL, msg("a stranger's second crystal is refused"));
		c.setItem(SaberAssembly.SECOND, SaberAssembly.crystalStack(second));
		helper.assertTrue(SaberAssembly.mayPlace(SaberAssembly.SECOND, c.getItem(SaberAssembly.SECOND), OWNER) && SaberAssembly.status(c, OWNER) == Status.READY,
				msg("two of the builder's crystals, an emitter and the staff grip: ready"));

		ItemStack staff = SaberAssembly.assemble(c, OWNER, BladeColor.BLUE, b);
		SaberConfig config = SaberComponents.config(staff);
		helper.assertTrue(staff.is(SaberItems.LIGHTSABER_STAFF) && SaberStaffs.isStaff(staff) && config.grip() == Grip.STAFF && config.emitter() == Emitter.GUARDIAN
				&& config.crystal().equals(main) && config.second().equals(Optional.of(second)), msg("a saberstaff with both crystals"));
		helper.assertTrue(SaberAssembly.partsEmpty(c) && c.getItem(SaberAssembly.SABER) == staff, msg("the parts are used up"));

		helper.assertTrue(SaberAssembly.status(c, STRANGER) == Status.NOT_YOUR_SABER, msg("only the crystal's owner takes it apart"));
		helper.assertTrue(SaberAssembly.disassemble(c, OWNER) != null, msg("the owner takes it apart"));
		helper.assertTrue(SaberAssembly.ownsCrystal(c.getItem(SaberAssembly.CRYSTAL), OWNER) && c.getItem(SaberAssembly.EMITTER).is(SaberItems.EMITTER_GUARDIAN)
				&& c.getItem(SaberAssembly.GRIP).is(SaberItems.GRIP_STAFF) && c.getItem(SaberAssembly.POMMEL).isEmpty()
				&& second.equals(c.getItem(SaberAssembly.SECOND).get(com.oldrepublic.registry.ModComponents.CRYSTAL_ATTUNEMENT))
				&& c.getItem(SaberAssembly.SABER).isEmpty(), msg("both crystals, the emitter and the staff grip come back, and no pommel"));

		SimpleContainer single = new SimpleContainer(SaberAssembly.SIZE);
		single.setItem(SaberAssembly.CRYSTAL, SaberAssembly.crystalStack(main));
		single.setItem(SaberAssembly.EMITTER, new ItemStack(SaberItems.EMITTER_DUELIST));
		single.setItem(SaberAssembly.GRIP, new ItemStack(SaberItems.GRIP_WRAPPED));
		single.setItem(SaberAssembly.POMMEL, new ItemStack(SaberItems.POMMEL_WEIGHTED));
		single.setItem(SaberAssembly.SECOND, SaberAssembly.crystalStack(second));
		helper.assertTrue(SaberAssembly.status(single, OWNER) == Status.SECOND_NEEDS_STAFF, msg("a second crystal needs the staff grip"));
		single.setItem(SaberAssembly.SECOND, ItemStack.EMPTY);
		helper.assertTrue(SaberAssembly.assemble(single, OWNER, BladeColor.BLUE, b).is(SaberItems.LIGHTSABER), msg("without it, an ordinary saber"));

		helper.assertTrue(!SaberAssembly.isBenchSaber(new ItemStack(SaberItems.TRAINING_SABER))
				&& !SaberAssembly.mayPlace(SaberAssembly.SABER, new ItemStack(SaberItems.TRAINING_SABER), OWNER),
				msg("a training saber cannot be taken apart"));
		helper.succeed();
	}

	/** The staff's heavy attack is a whirl: after its wind-up it hits every enemy within whirlRange, behind the player too, and nothing further. */
	@GameTest(structure = ARENA, maxTicks = 80)
	public void staffWhirlHitsAllInRadius(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SaberBalance b = SaberToggle.balance();
		helper.assertTrue(b.whirlRange >= 2f, msg("precondition: the targets at 2 blocks are inside whirlRange " + b.whirlRange));
		ServerPlayer p = M3TestPlayers.listed(helper, new BlockPos(12, 1, 12));
		int farOffset = (int) Math.ceil(b.whirlRange) + 2;
		List<Mob> inside = List.of(zombie(helper, new BlockPos(14, 1, 12)), zombie(helper, new BlockPos(10, 1, 12)), zombie(helper, new BlockPos(12, 1, 14)));
		Mob outside = zombie(helper, new BlockPos(12 + farOffset, 1, 12));
		float[] before = new float[inside.size() + 1];

		try {
			ItemStack staff = SaberItems.buildStaff(crystal(p.getUUID(), BladeColor.BLUE), crystal(p.getUUID(), BladeColor.GREEN), BladeColor.BLUE, Emitter.GUARDIAN, b);
			SaberToggle.setIgnited(staff, true, level.getGameTime());
			p.setItemInHand(InteractionHand.MAIN_HAND, staff);

			for (int i = 0; i < inside.size(); i++) {
				before[i] = inside.get(i).getHealth();
			}

			before[inside.size()] = outside.getHealth();
			helper.assertTrue(SaberTechniques.startHeavy(p) && SaberTechniques.isWhirling(p), msg("the heavy attack with a lit staff is a whirl"));
		} catch (RuntimeException e) {
			M3TestPlayers.remove(p);
			throw e;
		}

		helper.runAfterDelay(b.whirlWindup + 6, () -> {
			try {
				helper.assertTrue(!SaberTechniques.isWindingUp(p), msg("the whirl struck after its " + b.whirlWindup + "-tick wind-up"));

				for (int i = 0; i < inside.size(); i++) {
					Mob target = inside.get(i);
					helper.assertTrue(target.getHealth() < before[i], msg("the zombie at " + target.blockPosition() + ", 2 blocks away, is hit"));
				}

				helper.assertTrue(outside.getHealth() >= before[inside.size()], msg("the zombie " + farOffset + " blocks away is not hit"));
			} finally {
				M3TestPlayers.remove(p);
				inside.forEach(Mob::discard);
				outside.discard();
			}

			helper.succeed();
		});
	}

	/** The Weapon Action retracts and relights the second blade: one blade has a normal saber's damage and parry, two the staff's. */
	@GameTest(maxTicks = 20)
	public void singleBladeToggle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SaberBalance b = SaberToggle.balance();
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			ItemStack built = SaberItems.buildStaff(crystal(p.getUUID(), BladeColor.BLUE), crystal(p.getUUID(), BladeColor.GREEN), BladeColor.BLUE, Emitter.GUARDIAN, b);
			SaberToggle.setIgnited(built, true, level.getGameTime());
			p.setItemInHand(InteractionHand.MAIN_HAND, built);
			ItemStack staff = p.getMainHandItem();
			SaberStats saber = SaberStats.of(Emitter.GUARDIAN, Grip.STAFF, null, b);
			SaberStats both = SaberStats.of(staff, b);
			float arc = 90f;

			helper.assertTrue(SaberStaffs.bothBlades(staff) && near(both.damage(), saber.damage() * (1.0 + b.staffDamagePct / 100.0))
					&& near(both.parryTicks(), Math.max(0f, saber.parryTicks() + b.staffParryTicks)), msg("two blades: the staff's damage and parry window"));
			helper.assertTrue(near(SaberStaffs.guardArc(arc, staff, b), arc + b.staffConeDegrees) && near(SaberStaffs.drainMult(staff, b), 1f + b.staffGuardDrainPct / 100f),
					msg("two lit blades widen the guard and drain it faster"));

			SaberStaffs.toggleSingle(p, staff);
			SaberStats one = SaberStats.of(staff, b);
			helper.assertTrue(SaberComponents.staffMode(staff).single() && !SaberStaffs.bothBlades(staff), msg("the action retracts the second blade"));
			helper.assertTrue(near(one.damage(), saber.damage()) && near(one.parryTicks(), saber.parryTicks())
					&& near(one.attackSpeed(), saber.attackSpeed() + b.staffSpeedBonus) && near(one.reachBonus(), saber.reachBonus() + b.staffReach),
					msg("one blade: a saber's damage and parry, the staff's speed and reach"));
			helper.assertTrue(near(attackDamageModifier(staff), attackDamageModifier(withModifiers(staff, one, b))) && SaberComponents.isIgnited(staff),
					msg("the held staff's modifiers follow at once and it stays lit"));
			helper.assertTrue(near(SaberStaffs.guardArc(arc, staff, b), arc) && near(SaberStaffs.drainMult(staff, b), 1f), msg("one blade guards like a saber"));

			SaberStaffs.toggleSingle(p, staff);
			helper.assertTrue(SaberStaffs.bothBlades(staff) && near(SaberStats.of(staff, b).damage(), both.damage()), msg("the action relights the second blade"));

			SaberToggle.setIgnited(staff, false, level.getGameTime());
			helper.assertTrue(near(SaberStaffs.guardArc(arc, staff, b), arc), msg("an unlit staff widens nothing"));

			ItemStack plain = SaberItems.buildSaber(crystal(p.getUUID(), BladeColor.BLUE), BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED,
					com.oldrepublic.saber.HiltParts.Pommel.BALANCED, b);
			SaberStaffs.toggleSingle(p, plain);
			helper.assertTrue(!plain.has(SaberComponents.STAFF_MODE), msg("the action does nothing to a single saber"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** A copy of {@code staff} carrying the modifiers of {@code stats}, lit: what the toggle should have written. */
	private static ItemStack withModifiers(ItemStack staff, SaberStats stats, SaberBalance b) {
		ItemStack copy = staff.copy();
		copy.set(DataComponents.ATTRIBUTE_MODIFIERS, stats.modifiers(true, b));
		return copy;
	}

	// ---------------------------------------------------------------- lenses (5.7)

	/** The lens slot opens at Artifice 2 or with a lens carried; a lens fitted at construction acts only while lit, and comes back with its quality. */
	@GameTest(maxTicks = 20)
	public void lensSlotAndEffects(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SaberBalance sb = SaberToggle.balance();
		helper.assertTrue(near(Lens.HONED.value(Quality.STANDARD, DEFAULTS), 6) && near(Lens.HONED.value(Quality.MASTERWORK, DEFAULTS), 12)
				&& near(Lens.STEADY.value(Quality.STANDARD, DEFAULTS), 1) && near(Lens.STEADY.value(Quality.MASTERWORK, DEFAULTS), 2)
				&& near(Lens.RESONANT.value(Quality.FINE, DEFAULTS), 6.5), msg("spec 5.7: honed 6/9/12%, steady 1/1/2 ticks, resonant 5/6.5/8%"));
		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			CraftingBalance b = CraftingBalance.get();
			LightsaberWorkbenchMenu menu = new LightsaberWorkbenchMenu(0, p.getInventory(), ContainerLevelAccess.NULL);
			helper.assertTrue(!menu.lensSlotOpen(), msg("without Artifice 2 or a lens the lens slot stays hidden"));
			p.getInventory().add(new ItemStack(SaberItems.LENS_STEADY));
			helper.assertTrue(menu.lensSlotOpen(), msg("carrying a lens opens the slot"));
			p.getInventory().clearContent();
			setDiscipline(p, Discipline.ARTIFICE, 1, 0);
			helper.assertTrue(!menu.lensSlotOpen(), msg("Artifice 1 is not enough"));
			setDiscipline(p, Discipline.ARTIFICE, LightsaberWorkbenchMenu.LENS_ARTIFICE_LEVEL, 0);
			helper.assertTrue(menu.lensSlotOpen() && menu.benchSlotActive(SaberAssembly.LENS), msg("Artifice 2 opens the slot"));
			helper.assertTrue(menu.getSlot(SaberAssembly.LENS).mayPlace(new ItemStack(SaberItems.LENS_HONED))
					&& !menu.getSlot(SaberAssembly.LENS).mayPlace(new ItemStack(SaberItems.KYBER_CRYSTAL)), msg("the slot takes lenses only"));

			ItemStack honed = buildWithLens(p, SaberItems.LENS_HONED, Quality.MASTERWORK, sb);
			helper.assertTrue(SaberLens.lens(honed) == Lens.HONED && SaberLens.quality(honed) == Quality.MASTERWORK, msg("the lens and its quality are fitted"));
			helper.assertTrue(near(SaberLens.mastery(p, sb), 1f), msg("precondition: no lens_mastery"));
			p.setItemInHand(InteractionHand.MAIN_HAND, honed);
			helper.assertTrue(near(SaberLens.damageMult(p), 1f), msg("an unlit saber's lens does nothing"));
			SaberToggle.setIgnited(p.getMainHandItem(), true, level.getGameTime());
			float honedPct = Lens.HONED.value(Quality.MASTERWORK, b);
			helper.assertTrue(near(SaberLens.damageMult(p), 1f + honedPct / 100f) && near(SaberLens.forceMult(p), 1f),
					msg("a lit Masterwork honed lens: saber damage +" + honedPct + "%, no Force bonus"));

			ItemStack steady = buildWithLens(p, SaberItems.LENS_STEADY, Quality.STANDARD, sb);
			SaberToggle.setIgnited(steady, true, level.getGameTime());
			helper.assertTrue(near(SaberLens.parryTicks(p, steady, sb), Lens.STEADY.value(Quality.STANDARD, b)) && near(SaberLens.parryTicks(p, honed, sb), 0f),
					msg("a lit steady lens adds parry ticks; a honed one adds none"));

			ItemStack resonant = buildWithLens(p, SaberItems.LENS_RESONANT, Quality.FINE, sb);
			SaberToggle.setIgnited(resonant, true, level.getGameTime());
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			p.setItemInHand(InteractionHand.OFF_HAND, resonant);
			helper.assertTrue(near(SaberLens.forceMult(p), 1f + Lens.RESONANT.value(Quality.FINE, b) / 100f), msg("a lit resonant lens in either hand raises Force damage"));

			// A lens is fitted at construction only, and comes back with its quality.
			SimpleContainer c = new SimpleContainer(SaberAssembly.SIZE);
			c.setItem(SaberAssembly.SABER, honed);
			c.setItem(SaberAssembly.LENS, new ItemStack(SaberItems.LENS_STEADY));
			helper.assertTrue(SaberAssembly.status(c, p.getUUID()) == Status.SLOTS_NOT_EMPTY, msg("a built saber takes no new lens"));
			c.setItem(SaberAssembly.LENS, ItemStack.EMPTY);
			helper.assertTrue(SaberAssembly.disassemble(c, p.getUUID()) != null, msg("the saber comes apart"));
			ItemStack back = c.getItem(SaberAssembly.LENS);
			helper.assertTrue(back.is(SaberItems.LENS_HONED) && Crafting.quality(back) == Quality.MASTERWORK, msg("the lens comes back, still Masterwork"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}

	/** A Wrapped/Guardian/Balanced saber of {@code p}'s crystal with {@code lensItem} of quality {@code q} fitted at the bench. */
	private static ItemStack buildWithLens(ServerPlayer p, Item lensItem, Quality q, SaberBalance b) {
		SimpleContainer c = new SimpleContainer(SaberAssembly.SIZE);
		c.setItem(SaberAssembly.CRYSTAL, SaberAssembly.crystalStack(crystal(p.getUUID(), BladeColor.BLUE)));
		c.setItem(SaberAssembly.EMITTER, new ItemStack(SaberItems.EMITTER_GUARDIAN));
		c.setItem(SaberAssembly.GRIP, new ItemStack(SaberItems.GRIP_WRAPPED));
		c.setItem(SaberAssembly.POMMEL, new ItemStack(SaberItems.POMMEL_BALANCED));
		c.setItem(SaberAssembly.LENS, Crafting.applyQuality(new ItemStack(lensItem), q));
		ItemStack saber = SaberAssembly.assemble(c, p.getUUID(), BladeColor.BLUE, b);

		if (saber.isEmpty()) {
			throw new AssertionError("the bench did not build a saber with " + lensItem);
		}

		return saber;
	}

	// ---------------------------------------------------------------- the training saber (5.5)

	/** A training saber's killing blow on a player leaves them at 1 health; a real saber's, or one on a mob, is not spared. */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void trainingSaberNeverKillsPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer victim = M3TestPlayers.listed(helper, new BlockPos(8, 1, 8));
		ServerPlayer trainer = M3TestPlayers.listed(helper, new BlockPos(9, 1, 8));
		Mob dummy = zombie(helper, new BlockPos(14, 1, 8));

		try {
			victim.setGameMode(GameType.SURVIVAL);
			trainer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SaberItems.TRAINING_SABER));
			DamageSource training = level.damageSources().playerAttack(trainer);
			helper.assertTrue(TrainingSaberItem.isTrainingBlow(training), msg("a hit by a player holding a training saber is a training blow"));

			victim.setHealth(2f);
			helper.assertTrue(TrainingSaberItem.spares(victim, training) && near(victim.getHealth(), 1f), msg("the killing blow leaves the player at 1 health"));
			helper.assertTrue(!TrainingSaberItem.spares(dummy, training), msg("a mob is not spared"));

			victim.setHealth(2f);
			helper.assertTrue(!ServerLivingEntityEvents.ALLOW_DEATH.invoker().allowDeath(victim, training, 50f) && near(victim.getHealth(), 1f),
					msg("the death event refuses the training saber's kill"));

			ItemStack real = SaberItems.buildSaber(crystal(trainer.getUUID(), BladeColor.RED), BladeColor.RED, Emitter.GUARDIAN, Grip.WRAPPED,
					com.oldrepublic.saber.HiltParts.Pommel.BALANCED, SaberToggle.balance());
			trainer.setItemInHand(InteractionHand.MAIN_HAND, real);
			helper.assertTrue(!TrainingSaberItem.isTrainingBlow(level.damageSources().playerAttack(trainer))
					&& !TrainingSaberItem.spares(victim, level.damageSources().playerAttack(trainer)), msg("a real saber is not a training blow"));
			trainer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SaberItems.TRAINING_SABER));

			SaberBalance b = SaberToggle.balance();
			helper.assertTrue(near(SaberStats.of(new ItemStack(SaberItems.TRAINING_SABER), b).damage(), b.trainingSaberDamage),
					msg("a training saber hits for trainingSaberDamage"));
		} catch (RuntimeException e) {
			M3TestPlayers.remove(victim);
			M3TestPlayers.remove(trainer);
			dummy.discard();
			throw e;
		}

		// End to end, when a real hit can land on the mock player (PvP on, no spawn protection).
		helper.runAfterDelay(5, () -> {
			try {
				DamageSource training = level.damageSources().playerAttack(trainer);
				victim.setHealth(2f);
				victim.invulnerableTime = 0;
				boolean landed = victim.hurtServer(level, training, 50f);

				if (landed) {
					helper.assertTrue(victim.isAlive() && near(victim.getHealth(), 1f), msg("a real killing blow with a training saber leaves the player at 1 health"));
				}
			} finally {
				M3TestPlayers.remove(victim);
				M3TestPlayers.remove(trainer);
				dummy.discard();
			}

			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- saved data (12.1)

	/** CraftingData survives JSON, NBT and the sync codec, loads old saves as empty, clamps its bounds, and is copied on death. */
	@GameTest(maxTicks = 20)
	public void craftingDataCodecRoundTripAndCopyOnDeath(GameTestHelper helper) {
		CraftingData data = new CraftingData(List.of(new Progress(Discipline.ARMSTECH, 3, 400, 7), new Progress(Discipline.MEDTECH, 1, 20, 0)),
				List.of(Discipline.ARTIFICE), Set.of(id("heavy_repeater"), id("lens_honed")), Set.of(id("blaster_pistol")), 12L);

		JsonElement json = CraftingData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		helper.assertTrue(CraftingData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(data), msg("the data survives JSON: " + json));
		Tag nbt = CraftingData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		helper.assertTrue(CraftingData.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow().equals(data), msg("the data survives NBT: " + nbt));

		StreamCodec<ByteBuf, CraftingData> stream = ByteBufCodecs.fromCodec(CraftingData.CODEC);
		ByteBuf buf = Unpooled.buffer();

		try {
			stream.encode(buf, data);
			helper.assertTrue(stream.decode(buf).equals(data), msg("the data survives the sync codec"));
		} finally {
			buf.release();
		}

		helper.assertTrue(CraftingData.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow().equals(CraftingData.EMPTY), msg("a save without the fields loads empty"));
		helper.assertTrue(CraftingData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"disciplines\":[{\"id\":\"jedi\"}]}")).isError(),
				msg("an unknown discipline is an error"));

		CraftingData crowded = new CraftingData(List.of(new Progress(Discipline.ARMSTECH, 9, -5, -1), new Progress(Discipline.MEDTECH, 1, 0, 0),
				new Progress(Discipline.ARTIFICE, 1, 0, 0)), List.of(), Set.of(), Set.of(), -1L);
		Progress clamped = crowded.disciplines().get(0);
		helper.assertTrue(crowded.disciplines().size() == CraftingData.MAX_DISCIPLINES && clamped.level() == 5 && clamped.xp() == 0 && clamped.pity() == 0,
				msg("at most two disciplines; level 1 to 5, XP and pity never negative"));
		helper.assertTrue(data.withProgress(new Progress(Discipline.ARTIFICE, 1, 0, 0)).equals(data), msg("a third discipline is refused"));

		helper.assertTrue(CraftingAttachments.CRAFTING.copyOnDeath(), msg("crafting data is copied on death"));
		helper.assertTrue(CraftingAttachments.CRAFTING.isSynced(), msg("crafting data is synced to its owner"));

		ServerPlayer p = M3TestPlayers.listed(helper);

		try {
			CraftingAttachments.set(p, data);
			helper.assertTrue(CraftingAttachments.get(p).equals(data) && Crafting.level(p, Discipline.ARMSTECH) == 3 && Crafting.has(p, Discipline.MEDTECH)
					&& !Crafting.has(p, Discipline.ARTIFICE), msg("the attachment holds the record"));
		} finally {
			M3TestPlayers.remove(p);
		}

		helper.succeed();
	}
}
