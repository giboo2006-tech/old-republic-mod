package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingData;
import com.oldrepublic.crafting.CraftingData.Progress;
import com.oldrepublic.crafting.CraftingRules;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.LegendaryDrops;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.crafting.Salvage;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.SchematicLoader;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.crafting.bench.BenchResult;
import com.oldrepublic.crafting.bench.BenchService;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.enemy.droid.DroidEntities;
import com.oldrepublic.equipment.ArmourAttributes;
import com.oldrepublic.equipment.ArmourItem;
import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.equipment.EquipmentEvents;
import com.oldrepublic.equipment.EquipmentItems;
import com.oldrepublic.equipment.EquipmentM4Balance;
import com.oldrepublic.equipment.EquipmentModifiers;
import com.oldrepublic.equipment.EquipmentTraits;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.blaster.Blasters;
import com.oldrepublic.equipment.melee.MeleeCombat;
import com.oldrepublic.equipment.melee.VibroWeaponItem;
import com.oldrepublic.equipment.mod.LegendaryMods;
import com.oldrepublic.equipment.mod.ModEffects;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mixin.equipment.LivingEntityAttackStrengthAccessor;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.progression.ProgressionBalance;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.CrystalTuning;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberDual;
import com.oldrepublic.saber.SaberDual.Result;
import com.oldrepublic.saber.SaberForm;
import com.oldrepublic.saber.SaberForms;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberLens;
import com.oldrepublic.saber.SaberPassives;
import com.oldrepublic.saber.SaberStances;
import com.oldrepublic.saber.SaberStats;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.SaberTunings;
import com.oldrepublic.skill.CombatTracker;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 sabers, crafting and gear (spec MILESTONE_5.md 10, 11, 8.5, 12.3 and test plan 19.1, W7): the three new
 * forms' numbers and the form-change rules; dual-wielding's off-hand strike (two lit sabers, a live striker, a live
 * target in sight and in the off saber's own reach, its own recovery clock) and its penalty outside Jar'Kai; blade
 * length; crystal tuning, its price, its trip through the bench and the Attuned tuning's single count; the Legendary tier
 * (never rolled, never commissioned, never salvaged, its multipliers and gold name); the pity counters; the eight W7
 * legendary components; the rank 4 pieces and the Cantor's Mask; the concussion missile schematic.
 *
 * <p>Test rules (MILESTONE_5.md 19, the M4 rules): every test declares {@code maxTicks}; the only fixture is the
 * {@code oldrepublic_test:arena_24x7x24} arena; players are listed mock players ({@link M3TestPlayers}) put in survival
 * (creative reaches further) and logged out when the test ends, whichever step ends it ({@link Cleanup}); the leave
 * event drops their off-hand clock, form change and blaster state. Randoms are seeded and nothing global is written.
 * Numbers come from the live balance sections unless a check is about the spec's defaults, so a tuned file still
 * passes. Mock players do not run the vanilla equipment tick, so held modifiers are applied the way vanilla applies
 * them ({@link #hold}); the mod's own server tick (form changes, the forms' speed modifier, stances) does run for them.
 * Target dummies are NoFreeWill zombies with no armour and fire resistance (the arena is open to the sky); every
 * placement and the check that reads it happen in one step, so nothing moves or heals in between.
 */
public class SaberM5GameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	private static final double EPS = 1.0e-3;
	/** A full attack ticker: far past any weapon's cooldown. */
	private static final int FULL_TICKER = 100;
	/** How far around a shooter its bolts are looked for (and discarded at the end). */
	private static final double BOLT_SEARCH = 24.0;
	/** Any power: the Attuned tuning changes every power's cost alike. */
	private static final String SOME_POWER = "force_push";
	private static final long SEED = 0x5ABE57L;
	private static final String FORM_MAKASHI = SaberForm.MAKASHI.nodeId();
	private static final String FORM_DJEM_SO = SaberForm.DJEM_SO.nodeId();
	private static final String FORM_JAR_KAI = SaberForm.JAR_KAI.nodeId();
	/** The blade lengths ordered by reach. */
	private static final int[] LENGTHS_BY_REACH = {SaberConfig.LENGTH_SHORT, SaberConfig.LENGTH_STANDARD, SaberConfig.LENGTH_LONG};
	private static final String[] LENGTH_NAMES = {"short", "standard", "long"};

	/** One legendary schematic of 11.2 and 11.3: what it makes, its discipline (null for common) and its inputs by item id. */
	private record LegendarySpec(String output, @Nullable Discipline discipline, Map<String, Integer> inputs) {
		Identifier schematic() {
			return OldRepublicMod.id(output + "_legendary");
		}
	}

	/** The ten legendary schematics (MILESTONE_5.md 11.2, 11.3). */
	private static final List<LegendarySpec> LEGENDARIES = List.of(
			new LegendarySpec("emitter_cantor", Discipline.ARTIFICE,
					Map.of("oldrepublic:hymnstone_shard", 2, "oldrepublic:stormglass_shard", 2, "oldrepublic:masterwork_fragment", 1)),
			new LegendarySpec("pommel_hask", Discipline.ARTIFICE, Map.of("oldrepublic:clan_iron", 4, "oldrepublic:hymnstone_shard", 1)),
			new LegendarySpec("grip_sunforged", Discipline.ARTIFICE, Map.of("oldrepublic:sunstone", 4, "oldrepublic:alloy_plate", 2)),
			new LegendarySpec("lens_stormglass", Discipline.ARTIFICE, Map.of("oldrepublic:stormglass_shard", 4, "oldrepublic:hymnstone_shard", 1)),
			new LegendarySpec("mod_clan_iron_plating", Discipline.ARMOURCRAFT, Map.of("oldrepublic:clan_iron", 4, "oldrepublic:alloy_plate", 2)),
			new LegendarySpec("mod_hymnweave_lining", Discipline.ARMOURCRAFT, Map.of("oldrepublic:hymnstone_shard", 2, "oldrepublic:stormglass_shard", 2)),
			new LegendarySpec("mod_storm_cell", Discipline.ARMSTECH, Map.of("oldrepublic:stormglass_shard", 4, "oldrepublic:power_core", 1)),
			new LegendarySpec("mod_sunstone_edge", Discipline.ARMSTECH, Map.of("oldrepublic:sunstone", 4, "oldrepublic:masterwork_fragment", 1)),
			new LegendarySpec("spindle_capacitor", null,
					Map.of("oldrepublic:alloy_plate", 4, "oldrepublic:power_core", 2, "oldrepublic:hymnstone_shard", 1)),
			new LegendarySpec("hymn_tuned_drive", null,
					Map.of("oldrepublic:hymnstone_shard", 2, "oldrepublic:hyperdrive_motivator", 1, "oldrepublic:stormglass_shard", 4)));

	// ---------------------------------------------------------------- helpers

	/** What a test started and spawned, undone once, by whichever step ends the test. */
	private static final class Cleanup {
		private final List<Runnable> undo = new ArrayList<>();
		private boolean done;

		<T extends Entity> T entity(T entity) {
			undo.add(() -> {
				if (!entity.isRemoved()) {
					entity.discard();
				}
			});
			return entity;
		}

		/** Lowers the player's guard, clears their off-hand clock, blaster state and bolts, then logs them out. */
		ServerPlayer player(ServerPlayer player) {
			undo.add(() -> {
				player.stopUsingItem();
				SaberDual.resetClock(player);
				Blasters.forget(player);
				bolts(player).forEach(Entity::discard);
				M3TestPlayers.remove(player);
			});
			return player;
		}

		void run() {
			if (done) {
				return;
			}

			done = true;

			for (int i = undo.size() - 1; i >= 0; i--) {
				undo.get(i).run();
			}
		}
	}

	/** Runs {@code body} now; a failure cleans up before it is rethrown. */
	private static void guarded(Cleanup cleanup, Runnable body) {
		try {
			body.run();
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/** Runs {@code body} {@code delay} ticks after the start; a failure cleans up. */
	private static void step(GameTestHelper helper, Cleanup cleanup, long delay, Runnable body) {
		helper.runAfterDelay(delay, () -> guarded(cleanup, body));
	}

	/** The last step: runs {@code body} {@code delay} ticks after the start and succeeds; cleans up either way. */
	private static void last(GameTestHelper helper, Cleanup cleanup, long delay, Runnable body) {
		helper.runAfterDelay(delay, () -> {
			try {
				body.run();
				helper.succeed();
			} finally {
				cleanup.run();
			}
		});
	}

	/** Runs the whole test {@code body} now and succeeds; cleans up either way. */
	private static void now(GameTestHelper helper, Cleanup cleanup, Runnable body) {
		try {
			body.run();
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < EPS;
	}

	/** {@code 1 + pct/100}. */
	private static double ratio(float pct) {
		return 1.0 + pct / 100.0;
	}

	/** A listed player at {@code relative}, in survival, on the ground, facing south (+z) and level. */
	private static ServerPlayer listed(GameTestHelper helper, Cleanup cleanup, BlockPos relative) {
		ServerPlayer player = cleanup.player(M3TestPlayers.listed(helper, relative));
		player.setGameMode(GameType.SURVIVAL);
		face(player, 0f);
		player.setOnGround(true);
		player.xo = player.getX();
		player.zo = player.getZ();
		return player;
	}

	private static void face(ServerPlayer player, float yaw) {
		player.setYRot(yaw);
		player.setXRot(0f);
		player.setYHeadRot(yaw);
	}

	/** Moves {@code player}'s feet to the bottom centre of {@code relative}, still facing south. */
	private static void moveTo(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(relative));
		player.snapTo(feet.x, feet.y, feet.z);
		face(player, 0f);
	}

	private static CrystalAttunement crystal(UUID owner, @Nullable CrystalTuning tuning) {
		return new CrystalAttunement(owner, "Tester", BladeColor.BLUE.rgb(), tuning == null ? "" : tuning.id());
	}

	/** A retracted single saber of these parts around {@code owner}'s crystal with {@code tuning}. */
	private static ItemStack saber(UUID owner, Emitter emitter, Grip grip, Pommel pommel, @Nullable CrystalTuning tuning) {
		return SaberItems.buildSaber(crystal(owner, tuning), BladeColor.BLUE, emitter, grip, pommel, SaberToggle.balance());
	}

	/** The usual main-hand saber: Guardian, Wrapped, Weighted (a reach bonus). */
	private static ItemStack guardian(UUID owner) {
		return saber(owner, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, null);
	}

	/** The usual off-hand saber: Duelist, Ridged, Balanced (no reach bonus). */
	private static ItemStack duelist(UUID owner) {
		return saber(owner, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, null);
	}

	/** A retracted saberstaff around two of {@code owner}'s crystals. */
	private static ItemStack staff(UUID owner, @Nullable CrystalTuning first, @Nullable CrystalTuning second) {
		return SaberItems.buildStaff(crystal(owner, first), crystal(owner, second), BladeColor.BLUE, Emitter.DUELIST, SaberToggle.balance());
	}

	/** Gives {@code stack} {@code config} and rewrites its modifiers and reach, as the workbench does. */
	private static ItemStack configure(ItemStack stack, SaberConfig config) {
		stack.set(SaberComponents.SABER_CONFIG, config);
		SaberToggle.refreshModifiers(stack, SaberComponents.isIgnited(stack));
		return stack;
	}

	private static ItemStack withLength(ItemStack stack, int length) {
		return configure(stack, SaberComponents.config(stack).withLength(length));
	}

	private static ItemStack lit(ItemStack stack, ServerPlayer player) {
		SaberToggle.setIgnited(stack, true, player.level().getGameTime());
		return stack;
	}

	private static ItemStack dark(ItemStack stack, ServerPlayer player) {
		SaberToggle.setIgnited(stack, false, player.level().getGameTime());
		return stack;
	}

	/** Puts {@code stack} in the main hand with its modifiers applied as vanilla's equipment tick does; returns the held stack. */
	private static ItemStack hold(ServerPlayer player, ItemStack stack) {
		unapply(player, player.getMainHandItem());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		ItemStack held = player.getMainHandItem();
		held.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			AttributeInstance instance = player.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
				instance.addOrUpdateTransientModifier(modifier);
			}
		});
		return held;
	}

	/** Re-applies the held stack's modifiers after it changed in hand (lit, retracted). */
	private static ItemStack rehold(ServerPlayer player) {
		return hold(player, player.getMainHandItem());
	}

	private static void unapply(ServerPlayer player, ItemStack stack) {
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			AttributeInstance instance = player.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
			}
		});
		// A saber retracted in hand no longer lists the reach modifier it added while lit.
		AttributeInstance reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);

		if (reach != null) {
			reach.removeModifier(SaberStats.REACH_MODIFIER_ID);
		}
	}

	/** Puts {@code stack} in the off hand (a saber's modifiers are main-hand only); returns the held stack. */
	private static ItemStack offhand(ServerPlayer player, ItemStack stack) {
		player.setItemInHand(InteractionHand.OFF_HAND, stack);
		return player.getOffhandItem();
	}

	/** Holds a blaster drawn long ago (no draw delay); returns the held stack. */
	private static ItemStack drawn(ServerPlayer player, ItemStack gun) {
		ItemStack held = hold(player, gun);
		Blasters.drawnLongAgo(player, held);
		return held;
	}

	/** A NoFreeWill zombie at {@code relative}: no armour, and fire resistance against the open sky. */
	private static Mob dummy(GameTestHelper helper, Cleanup cleanup, BlockPos relative) {
		Mob mob = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, relative));
		AttributeInstance armour = mob.getAttribute(Attributes.ARMOR);

		if (armour != null) {
			armour.setBaseValue(0.0);
		}

		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		return mob;
	}

	/**
	 * Puts {@code target} straight south of {@code player} with its near face {@code gap} blocks from the player's eyes
	 * (the eyes are inside the target's height and width, so the eye-to-box distance is the gap).
	 */
	private static void placeAhead(LivingEntity target, ServerPlayer player, double gap) {
		target.snapTo(player.getX(), player.getY(), player.getZ() + gap + target.getBbWidth() / 2.0);
	}

	/** {@code player}'s off-hand strike at {@code targetId}, which must come to {@code expected}. */
	private static void expectStrike(GameTestHelper helper, ServerPlayer player, int targetId, Result expected, String what) {
		Result got = SaberDual.strike(player, targetId);
		helper.assertTrue(got == expected, what + ": expected " + expected + ", got " + got);
	}

	/** The amount of modifier {@code id} on {@code entity}'s {@code attribute}, or NaN when it is not there. */
	private static double modifier(LivingEntity entity, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = entity.getAttribute(attribute);
		AttributeModifier m = instance == null ? null : instance.getModifier(id);
		return m == null ? Double.NaN : m.amount();
	}

	/** The amount of {@code stack}'s own modifier {@code id} on {@code attribute}, or NaN when it has none. */
	private static double itemModifier(ItemStack stack, Holder<Attribute> attribute, Identifier id) {
		for (ItemAttributeModifiers.Entry e : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (e.attribute().value() == attribute.value() && e.modifier().id().equals(id)) {
				return e.modifier().amount();
			}
		}

		return Double.NaN;
	}

	/** The armour {@code stack}'s own modifiers give. */
	private static double armour(ItemStack stack) {
		double sum = 0.0;

		for (ItemAttributeModifiers.Entry e : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (e.attribute().value() == Attributes.ARMOR.value() && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
				sum += e.modifier().amount();
			}
		}

		return sum;
	}

	/** {@code stack}'s {@code attack_range} component (fails when it has none). */
	private static AttackRange range(GameTestHelper helper, ItemStack stack) {
		AttackRange range = stack.get(DataComponents.ATTACK_RANGE);
		helper.assertTrue(range != null, "a saber must carry an attack_range component");
		return range;
	}

	private static @Nullable CrystalTuning tuningOf(ItemStack crystal) {
		CrystalAttunement attunement = crystal.get(ModComponents.CRYSTAL_ATTUNEMENT);
		return attunement == null ? null : CrystalTuning.of(attunement);
	}

	/** How many of {@code item} {@code player} carries outside the armour and off-hand slots. */
	private static int count(ServerPlayer player, Item item) {
		int n = 0;

		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}

		return n;
	}

	/** {@code s}'s inputs by item id ({@code #tag} for a tag), counts summed. */
	private static Map<String, Integer> inputs(Schematic s) {
		Map<String, Integer> out = new HashMap<>();

		for (Schematic.Input input : s.inputs()) {
			String key = input.what().map(item -> BuiltInRegistries.ITEM.getKey(item).toString(), tag -> "#" + tag.location());
			out.merge(key, input.count(), Integer::sum);
		}

		return out;
	}

	/** Gives {@code player} discipline {@code d} at {@code level} (with that level's XP). */
	private static void setDiscipline(GameTestHelper helper, ServerPlayer player, Discipline d, int level) {
		int[] xp = CraftingBalance.get().levelXp;
		int at = xp.length == 0 ? 0 : xp[Math.max(0, Math.min(xp.length, level) - 1)];
		CraftingAttachments.set(player, CraftingAttachments.get(player).withProgress(new Progress(d, level, at, 0)));
		helper.assertTrue(Crafting.level(player, d) == level, "precondition: " + d.id() + " at level " + level);
	}

	private static int misses(ServerPlayer player, Identifier schematic) {
		return CraftingAttachments.get(player).misses(schematic.toString());
	}

	/** Grants {@code form}'s node and makes it the active form at once (no change delay). */
	private static void setForm(GameTestHelper helper, ServerPlayer player, SaberForm form) {
		if (form != SaberForm.UNFORMED) {
			Skills.grant(player, form.nodeId());
		}

		Skills.setActiveForm(player, form.nodeId());
		helper.assertTrue(Skills.activeForm(player).equals(form.nodeId()), "precondition: " + form.id() + " is the active form");
	}

	/** The forms' speed modifier on {@code player}: {@code pct} percent of the total, or absent at 0. */
	private static void expectFormSpeed(GameTestHelper helper, ServerPlayer player, float pct, String what) {
		AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
		AttributeModifier m = speed == null ? null : speed.getModifier(SaberForms.FORM_SPEED_ID);

		if (pct == 0f) {
			helper.assertTrue(m == null, what + ": no form speed modifier expected, found " + (m == null ? 0.0 : m.amount()));
			return;
		}

		helper.assertTrue(m != null && near(m.amount(), pct / 100.0) && m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
				what + ": the form speed modifier should multiply the total by " + (1.0 + pct / 100.0) + ", found " + (m == null ? "none" : m.amount()));
	}

	/** Wears {@code stack} in {@code slot} with its modifiers applied (remove by id, then add), as vanilla's equipment tick does. */
	private static void wear(LivingEntity entity, EquipmentSlot slot, ItemStack stack) {
		entity.getItemBySlot(slot).forEachModifier(slot, (attribute, modifier) -> {
			AttributeInstance instance = entity.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
			}
		});
		entity.setItemSlot(slot, stack);
		stack.forEachModifier(slot, (attribute, modifier) -> {
			AttributeInstance instance = entity.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
				instance.addOrUpdateTransientModifier(modifier);
			}
		});
	}

	/** Wears all four pieces of {@code set}. */
	private static void wearSet(LivingEntity entity, ArmourSet set) {
		for (EquipmentSlot slot : ArmourSets.SLOTS) {
			wear(entity, slot, ArmourSets.piece(set, slot));
		}
	}

	/** {@code set}'s helmet with one Legendary {@code mod} fitted in its armour slot. */
	private static ItemStack moddedHead(GameTestHelper helper, ArmourSet set, Item mod) {
		ItemStack head = Mods.fit(ArmourSets.piece(set, EquipmentSlot.HEAD), ModSlot.ARMOUR, Crafting.withQuality(new ItemStack(mod), Quality.LEGENDARY));
		helper.assertTrue(Mods.has(head, mod), "precondition: the mod is fitted");
		return head;
	}

	/** The bolts {@code shooter} owns near them. */
	private static List<BlasterBolt> bolts(ServerPlayer shooter) {
		return shooter.level().getEntitiesOfClass(BlasterBolt.class, shooter.getBoundingBox().inflate(BOLT_SEARCH),
				bolt -> bolt.getOwner() == shooter && !bolt.isRemoved());
	}

	/** The one bolt {@code shooter} just fired (fails when there is not exactly one). */
	private static BlasterBolt onlyBolt(GameTestHelper helper, ServerPlayer shooter) {
		List<BlasterBolt> found = bolts(shooter);
		helper.assertTrue(found.size() == 1, "expected exactly one bolt in flight, found " + found.size());
		return found.get(0);
	}

	private static DamageSource boltSource(ServerLevel level, BlasterBolt bolt, Entity shooter) {
		return level.damageSources().source(EnemyEntities.BLASTER_DAMAGE, bolt, shooter);
	}

	/** One player attack on {@code target} with a full attack charge, as the attack packet does (the callback's swing first). */
	private static void strike(ServerPlayer player, LivingEntity target, VibroWeaponItem.Kind kind) {
		((LivingEntityAttackStrengthAccessor) player).oldrepublic$setAttackStrengthTicker(FULL_TICKER);
		MeleeCombat.swing(player, kind, target.getId(), 1f, player.level().getGameTime());
		player.attack(target);
	}

	// ---------------------------------------------------------------- forms (10.1)

	@GameTest(maxTicks = 40)
	public void formModifiers(GameTestHelper helper) {
		SaberBalance d = new SaberBalance();
		helper.assertTrue(d.makashiParryTicks == 2f && d.makashiRiposteWindow == 20 && d.makashiPassiveWindow == 30 && d.makashiRipostePct == 40f
				&& d.makashiDuelistPct == 15f && d.makashiBoltGuardPct == 20f,
				"Makashi's defaults (16.4): +2 parry ticks, riposte 20 ticks (30 with the passive), +40% riposte, +15% on duellists, +20% bolt drain");
		helper.assertTrue(d.djemSoHeavyPct == 20f && d.djemSoWindupCut == 2 && d.djemSoStaggerTicks == 10 && near(d.djemSoReturnChance, 0.6)
				&& d.djemSoSpeedPct == -5f, "Djem So's defaults (16.4): +20% heavy, 2 ticks off the wind-up, a 10-tick stagger, 0.6 bolt return, -5% speed");
		helper.assertTrue(d.jarKaiSpeedPct == 10f && d.jarKaiGuard == 15f && d.jarKaiParryTicks == -1f,
				"Jar'Kai's defaults (16.4): +10% speed, +15 guard, -1 parry tick");

		for (SaberBalance b : List.of(d, SaberToggle.balance())) {
			SaberForm.Modifiers makashi = SaberForm.MAKASHI.modifiers(b);
			SaberForm.Traits mt = makashi.traits();
			helper.assertTrue(makashi.parryTicks() == b.makashiParryTicks && makashi.parryMult() == 1f && makashi.guardArc() == b.guardArcDegrees
					&& makashi.damageMult() == 1f && makashi.attackSpeedBonus() == 0f && makashi.heavyWindupTicks() == b.heavyWindupTicks,
					"Makashi: Unformed's numbers with makashiParryTicks on the parry window");
			helper.assertTrue(mt.riposteTicks() == b.makashiRiposteWindow && mt.riposteTicksWithPassive() == b.makashiPassiveWindow
					&& near(mt.riposteMult(), ratio(b.makashiRipostePct)) && near(mt.duelistDamageMult(), ratio(b.makashiDuelistPct))
					&& near(mt.boltDrainMult(), ratio(b.makashiBoltGuardPct)) && mt.speedPct() == 0f && mt.heavyDamageMult() == 1f
					&& mt.staminaBonus() == 0f && mt.offhandPenalty(),
					"Makashi's traits: its riposte window and damage, the duellist bonus, the bolt drain, the off-hand penalty");

			SaberForm.Modifiers djemSo = SaberForm.DJEM_SO.modifiers(b);
			SaberForm.Traits dt = djemSo.traits();
			helper.assertTrue(djemSo.parryTicks() == 0f && djemSo.heavyWindupTicks() == Math.max(1, b.heavyWindupTicks - b.djemSoWindupCut),
					"Djem So: the heavy wind-up djemSoWindupCut ticks shorter");
			helper.assertTrue(dt.speedPct() == b.djemSoSpeedPct && near(dt.heavyDamageMult(), ratio(b.djemSoHeavyPct)) && dt.parryStaggerTicks() == b.djemSoStaggerTicks
					&& dt.boltReturnChance() == b.djemSoReturnChance && dt.boltDrainMult() == 1f && dt.riposteTicks() == 0 && dt.staminaBonus() == 0f
					&& dt.offhandPenalty(), "Djem So's traits: slower, heavier heavies, the parry stagger, the bolt return");

			SaberForm.Modifiers jarKai = SaberForm.JAR_KAI.modifiers(b);
			SaberForm.Traits jt = jarKai.traits();
			helper.assertTrue(jarKai.parryTicks() == b.jarKaiParryTicks && jarKai.heavyWindupTicks() == b.heavyWindupTicks && jarKai.guardArc() == b.guardArcDegrees,
					"Jar'Kai: Unformed's numbers with jarKaiParryTicks");
			helper.assertTrue(jt.speedPct() == b.jarKaiSpeedPct && jt.staminaBonus() == b.jarKaiGuard && !jt.offhandPenalty() && jt.heavyDamageMult() == 1f
					&& jt.riposteTicks() == 0 && jt.boltReturnChance() == 0f, "Jar'Kai's traits: faster, more guard, no off-hand penalty");

			for (SaberForm old : new SaberForm[] {SaberForm.UNFORMED, SaberForm.SORESU, SaberForm.ATARU}) {
				helper.assertTrue(old.modifiers(b).traits().equals(SaberForm.Traits.NONE), old.id() + " has no Milestone 5 traits");
			}

			SaberStats hilt = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, b);
			SaberStats inJarKai = hilt.withForm(jarKai, 0, 0f, b);
			helper.assertTrue(near(inJarKai.maxStamina(), hilt.maxStamina() + b.jarKaiGuard),
					"Jar'Kai's guard is added after the multipliers: expected " + (hilt.maxStamina() + b.jarKaiGuard) + ", got " + inJarKai.maxStamina());
			helper.assertTrue(near(inJarKai.parryTicks(), SaberStats.parryWindow(hilt.parryTicks(), 0f, 1f, b.jarKaiParryTicks, b.parryWindowCap))
					&& near(hilt.withForm(makashi, 0, 0f, b).parryTicks(), SaberStats.parryWindow(hilt.parryTicks(), 0f, 1f, b.makashiParryTicks, b.parryWindowCap)),
					"the forms' parry ticks go on the window after the multiplier, under the cap");
		}

		// The spec's defaults, worked: a 6-tick hilt is 8 under Makashi, 5 under Jar'Kai, and Makashi with 5 flat ticks hits the cap of 11.
		SaberStats hilt = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, d);
		helper.assertTrue(near(hilt.parryTicks(), 6.0) && near(hilt.withForm(SaberForm.MAKASHI.modifiers(d), 0, 0f, d).parryTicks(), 8.0)
				&& near(hilt.withForm(SaberForm.JAR_KAI.modifiers(d), 0, 0f, d).parryTicks(), 5.0)
				&& near(hilt.withForm(SaberForm.MAKASHI.modifiers(d), 0, 5f, d).parryTicks(), d.parryWindowCap),
				"default parry windows: 6 -> Makashi 8, Jar'Kai 5, Makashi with 5 flat ticks capped at " + d.parryWindowCap);
		helper.assertTrue(near(hilt.withForm(SaberForm.JAR_KAI.modifiers(d), 0, 0f, d).maxStamina(), hilt.maxStamina() + 15.0), "default Jar'Kai guard: +15");

		SaberBalance quick = new SaberBalance();
		quick.heavyWindupTicks = 2;
		quick.djemSoWindupCut = 5;
		helper.assertTrue(SaberForm.DJEM_SO.modifiers(quick).heavyWindupTicks() == 1, "Djem So's wind-up never drops below 1 tick");

		helper.assertTrue(SaberForm.MAKASHI.ordinal() == 3 && SaberForm.DJEM_SO.ordinal() == 4 && SaberForm.JAR_KAI.ordinal() == 5,
				"the new forms are appended after Ataru (stances and effects send the ordinal)");
		helper.assertTrue(SaberForm.byNode(FORM_MAKASHI) == SaberForm.MAKASHI && SaberForm.byNode(FORM_DJEM_SO) == SaberForm.DJEM_SO
				&& SaberForm.byNode(FORM_JAR_KAI) == SaberForm.JAR_KAI && SaberForm.byNode("form_unknown") == SaberForm.UNFORMED,
				"forms by node id: form_makashi, form_djem_so, form_jar_kai; anything else is Unformed");
		helper.assertTrue(SaberForm.byId("makashi") == SaberForm.MAKASHI && SaberForm.byId("djem_so") == SaberForm.DJEM_SO
				&& SaberForm.byId("jar_kai") == SaberForm.JAR_KAI && SaberForm.byId("bogus") == null, "forms by id");

		// In the world: the forms' attack speed follows the lit blades, tick by tick.
		Cleanup cleanup = new Cleanup();
		ServerPlayer[] p = new ServerPlayer[1];
		double[] mainSpeed = new double[1];

		guarded(cleanup, () -> {
			ServerPlayer player = p[0] = listed(helper, cleanup, new BlockPos(1, 1, 1));
			ItemStack main = hold(player, lit(guardian(player.getUUID()), player));
			mainSpeed[0] = SaberStats.of(main, SaberToggle.balance()).attackSpeed();
			setForm(helper, player, SaberForm.DJEM_SO);
			Skills.grant(player, FORM_JAR_KAI);
		});

		step(helper, cleanup, 3, () -> {
			ServerPlayer player = p[0];
			float pct = SaberToggle.balance().djemSoSpeedPct;
			expectFormSpeed(helper, player, pct, "Djem So with a lit saber");
			helper.assertTrue(near(player.getAttributeValue(Attributes.ATTACK_SPEED), mainSpeed[0] * (1.0 + pct / 100.0)),
					"Djem So's speed change multiplies the total: expected " + mainSpeed[0] * (1.0 + pct / 100.0) + ", got "
							+ player.getAttributeValue(Attributes.ATTACK_SPEED));
			dark(player.getMainHandItem(), player);
			rehold(player);
		});

		step(helper, cleanup, 6, () -> {
			ServerPlayer player = p[0];
			expectFormSpeed(helper, player, 0f, "Djem So with the saber retracted");
			setForm(helper, player, SaberForm.JAR_KAI);
			lit(player.getMainHandItem(), player);
			rehold(player);
		});

		step(helper, cleanup, 9, () -> {
			ServerPlayer player = p[0];
			expectFormSpeed(helper, player, 0f, "Jar'Kai with one blade");
			helper.assertTrue(SaberPassives.selectedForm(player) == SaberForm.JAR_KAI && SaberPassives.form(player) == SaberForm.UNFORMED,
					"Jar'Kai is chosen but acts as Unformed with one blade");
			helper.assertTrue(SaberStances.stance(player) == SaberForm.UNFORMED, "Jar'Kai with one blade shows the Unformed stance");
			offhand(player, lit(duelist(player.getUUID()), player));
		});

		last(helper, cleanup, 12, () -> {
			ServerPlayer player = p[0];
			expectFormSpeed(helper, player, SaberToggle.balance().jarKaiSpeedPct, "Jar'Kai with two lit blades");
			helper.assertTrue(SaberPassives.form(player) == SaberForm.JAR_KAI && SaberStances.stance(player) == SaberForm.JAR_KAI,
					"two lit blades: Jar'Kai acts and shows");
		});
	}

	@GameTest(maxTicks = 120)
	public void formChangeRules(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		int settle = b.formSwitchTicks + 2;
		helper.assertTrue(b.formSwitchTicks >= 1 && 2 * settle + 4 <= 120, "precondition: the live formSwitchTicks (" + b.formSwitchTicks + ") fits this test");
		Cleanup cleanup = new Cleanup();
		ServerPlayer[] p = new ServerPlayer[1];

		guarded(cleanup, () -> {
			ServerPlayer player = p[0] = listed(helper, cleanup, new BlockPos(1, 1, 1));
			hold(player, lit(guardian(player.getUUID()), player));
			helper.assertTrue(!SaberForms.request(player, FORM_MAKASHI), "a form the player does not own is refused");
			helper.assertTrue(!SaberForms.request(player, "form_unknown"), "an unknown form is refused");
			helper.assertTrue(!SaberForms.request(player, ""), "the form already set (Unformed) is refused");
			helper.assertTrue(!SaberForms.isSwitching(player), "a refused request starts no change");

			Skills.grant(player, FORM_MAKASHI);
			Skills.grant(player, FORM_JAR_KAI);
			helper.assertTrue(SaberForms.request(player, FORM_MAKASHI), "an owned form starts a change out of combat");
			helper.assertTrue(SaberForms.isSwitching(player) && SaberPassives.selectedForm(player) == SaberForm.UNFORMED,
					"the change takes formSwitchTicks: still Unformed meanwhile");
			helper.assertTrue(!SaberForms.request(player, FORM_JAR_KAI), "no second change during a change");
		});

		step(helper, cleanup, settle, () -> {
			ServerPlayer player = p[0];
			helper.assertTrue(!SaberForms.isSwitching(player) && SaberPassives.selectedForm(player) == SaberForm.MAKASHI
					&& Skills.activeForm(player).equals(FORM_MAKASHI), "the change completes after formSwitchTicks: Makashi is set");
			helper.assertTrue(SaberStances.stance(player) == SaberForm.MAKASHI, "others see the Makashi stance");
			helper.assertTrue(!SaberForms.request(player, FORM_MAKASHI), "the form already set is refused");
			helper.assertTrue(SaberForms.request(player, FORM_JAR_KAI), "a change to Jar'Kai starts");
		});

		last(helper, cleanup, 2 * settle, () -> {
			ServerPlayer player = p[0];
			UUID id = player.getUUID();
			helper.assertTrue(SaberPassives.selectedForm(player) == SaberForm.JAR_KAI, "the second change completes: Jar'Kai is set");
			helper.assertTrue(SaberPassives.form(player) == SaberForm.UNFORMED && SaberStances.stance(player) == SaberForm.UNFORMED,
					"Jar'Kai with one blade acts and shows as Unformed");

			ItemStack off = offhand(player, duelist(id));
			helper.assertTrue(SaberPassives.form(player) == SaberForm.UNFORMED, "a dark off-hand saber is no second blade");
			lit(off, player);
			helper.assertTrue(SaberPassives.form(player) == SaberForm.JAR_KAI && SaberStances.stance(player) == SaberForm.JAR_KAI,
					"two lit single sabers: Jar'Kai acts and shows");
			off.set(SaberComponents.SABER_THROWN, id);
			helper.assertTrue(SaberPassives.form(player) == SaberForm.UNFORMED, "a thrown off-hand saber is not in hand");
			off.remove(SaberComponents.SABER_THROWN);

			ItemStack main = player.getMainHandItem();
			hold(player, lit(staff(id, null, null), player));
			helper.assertTrue(SaberPassives.form(player) == SaberForm.UNFORMED, "a saberstaff is never one of two blades");
			hold(player, main);
			helper.assertTrue(SaberPassives.form(player) == SaberForm.JAR_KAI, "the single saber back in hand: Jar'Kai again");
			hold(player, ItemStack.EMPTY);
			offhand(player, ItemStack.EMPTY);
			helper.assertTrue(SaberPassives.form(player) == SaberForm.UNFORMED, "no blades: Unformed");
			hold(player, main);

			CombatTracker.mark(player);
			helper.assertTrue(!SaberForms.request(player, "") && !SaberForms.isSwitching(player), "no change within formCombatLockTicks of a fight");
		});
	}

	// ---------------------------------------------------------------- dual-wielding (10.2)

	@GameTest(structure = ARENA, maxTicks = 100)
	public void offhandStrikeValidates(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = new Cleanup();
		ServerPlayer[] p = new ServerPlayer[1];
		Mob[] near = new Mob[1];
		Mob[] mid = new Mob[1];
		long[] struckAt = new long[1];
		int[] delay = new int[1];
		// [0]: a check saw the off hand still recovering; [1]: a check saw it ready.
		boolean[] seen = new boolean[2];

		guarded(cleanup, () -> {
			SaberBalance b = SaberToggle.balance();
			ServerPlayer player = p[0] = listed(helper, cleanup, new BlockPos(2, 1, 2));
			UUID id = player.getUUID();
			Mob target = near[0] = dummy(helper, cleanup, new BlockPos(2, 1, 4));
			Mob far = dummy(helper, cleanup, new BlockPos(2, 1, 13));
			mid[0] = dummy(helper, cleanup, new BlockPos(8, 1, 10));
			// Behind the lodestone and amethyst pylon at (5, 1..2, 5), seen from (5, 1, 3).
			Mob hidden = dummy(helper, cleanup, new BlockPos(5, 1, 7));
			Mob corpse = dummy(helper, cleanup, new BlockPos(10, 1, 4));
			Mob gone = dummy(helper, cleanup, new BlockPos(12, 1, 4));
			int goneId = gone.getId();
			gone.discard();
			corpse.setHealth(0f);

			// A form change in progress (a second player, before anyone has fought).
			ServerPlayer switching = listed(helper, cleanup, new BlockPos(4, 1, 2));
			hold(switching, lit(guardian(switching.getUUID()), switching));
			offhand(switching, lit(duelist(switching.getUUID()), switching));
			Skills.grant(switching, FORM_MAKASHI);
			helper.assertTrue(SaberForms.request(switching, FORM_MAKASHI), "precondition: the second player's form change starts");
			expectStrike(helper, switching, target.getId(), Result.BUSY, "an off-hand strike during a form change");

			hold(player, lit(guardian(id), player));
			expectStrike(helper, player, target.getId(), Result.NOT_DUAL, "one saber");
			ItemStack off = offhand(player, duelist(id));
			expectStrike(helper, player, target.getId(), Result.NOT_DUAL, "a dark off-hand saber");
			lit(off, player);
			off.set(SaberComponents.SABER_THROWN, id);
			expectStrike(helper, player, target.getId(), Result.NOT_DUAL, "a thrown off-hand saber");
			off.remove(SaberComponents.SABER_THROWN);
			offhand(player, lit(staff(id, null, null), player));
			expectStrike(helper, player, target.getId(), Result.NOT_DUAL, "a saberstaff in the off hand");
			ItemStack offSaber = offhand(player, lit(duelist(id), player));
			helper.assertTrue(SaberDual.isDualWielding(player), "two lit single sabers are a dual wield");

			player.startUsingItem(InteractionHand.MAIN_HAND);
			expectStrike(helper, player, target.getId(), Result.BUSY, "an off-hand strike while guarding");
			player.stopUsingItem();

			expectStrike(helper, player, player.getId(), Result.BAD_TARGET, "the striker itself");
			expectStrike(helper, player, goneId, Result.BAD_TARGET, "an entity no longer there");
			expectStrike(helper, player, corpse.getId(), Result.BAD_TARGET, "a dead target");

			moveTo(helper, player, new BlockPos(5, 1, 3));
			helper.assertTrue(!player.hasLineOfSight(hidden) && player.isWithinAttackRange(offSaber, hidden.getBoundingBox(), SaberDual.REACH_TOLERANCE),
					"precondition: the dummy behind the pylon is in reach but out of sight");
			expectStrike(helper, player, hidden.getId(), Result.BAD_TARGET, "a target out of sight");
			moveTo(helper, player, new BlockPos(2, 1, 2));

			expectStrike(helper, player, far.getId(), Result.OUT_OF_REACH, "a target beyond the off saber's reach");
			helper.assertTrue(SaberDual.offhandReady(player), "a refused strike starts no recovery");

			int ticks = delay[0] = SaberDual.offhandDelay(player, b);
			int expected = SaberDual.offhandTicks(SaberStats.of(offSaber, b).attackSpeed(), 1.0, true, b);
			helper.assertTrue(ticks == expected, "the off hand recovers by the off saber's speed, offhandSpeed slower (Unformed): expected " + expected
					+ " ticks, got " + ticks);
			helper.assertTrue(ticks >= 3 && ticks <= 60, "precondition: the live off-hand recovery (" + ticks + " ticks) fits this test");

			target.invulnerableTime = 0;
			float before = target.getHealth();
			expectStrike(helper, player, target.getId(), Result.STRUCK, "a live target in sight and in reach");
			struckAt[0] = level.getGameTime();
			helper.assertTrue(target.getHealth() < before, "the off-hand strike deals damage");
			expectStrike(helper, player, target.getId(), Result.NOT_READY, "a second strike before the recovery ends");

			player.setHealth(0f);
			expectStrike(helper, player, target.getId(), Result.NOT_DUAL, "a dead striker");
			player.setHealth(player.getMaxHealth());
		});

		// Either side of the recovery's end (the server allows READY_SLACK ticks early).
		int d = delay[0];

		for (int k = d - 2; k <= d; k++) {
			step(helper, cleanup, k, () -> {
				long now = level.getGameTime();
				boolean expected = now + SaberDual.READY_SLACK >= struckAt[0] + d;
				helper.assertTrue(SaberDual.offhandReady(p[0]) == expected,
						(now - struckAt[0]) + " ticks after a strike with a " + d + "-tick recovery the off hand should be " + (expected ? "ready" : "recovering"));
				seen[expected ? 1 : 0] = true;
			});
		}

		last(helper, cleanup, d + 1, () -> {
			helper.assertTrue(seen[0] && seen[1], "the checks saw the off hand recovering, then ready");
			ServerPlayer player = p[0];
			UUID id = player.getUUID();
			expectStrike(helper, player, -1, Result.SWUNG, "a swing at nothing");
			expectStrike(helper, player, near[0].getId(), Result.NOT_READY, "a swing at nothing starts the recovery too");
			SaberDual.resetClock(player);

			// The off saber's own reach (its attack_range): a short blade falls short where a long one lands.
			ItemStack shortOff = withLength(lit(duelist(id), player), SaberConfig.LENGTH_SHORT);
			ItemStack longOff = withLength(lit(duelist(id), player), SaberConfig.LENGTH_LONG);
			double rShort = range(helper, shortOff).maxReach();
			double rLong = range(helper, longOff).maxReach();
			helper.assertTrue(rShort < rLong, "precondition: a short blade reaches less far than a long one");
			Mob target = mid[0];
			placeAhead(target, player, (rShort + rLong) / 2.0 + SaberDual.REACH_TOLERANCE);
			offhand(player, shortOff);
			expectStrike(helper, player, target.getId(), Result.OUT_OF_REACH, "a short off-hand blade halfway to the long blade's reach");
			offhand(player, longOff);
			target.invulnerableTime = 0;
			expectStrike(helper, player, target.getId(), Result.STRUCK, "a long off-hand blade at the same spot");
			SaberDual.resetClock(player);

			offhand(player, lit(new ItemStack(SaberItems.TRAINING_SABER), player));
			near[0].invulnerableTime = 0;
			expectStrike(helper, player, near[0].getId(), Result.STRUCK, "a lit training saber is an off-hand saber too (spars)");
		});
	}

	@GameTest(maxTicks = 20)
	public void offhandPenaltyUnlessJarKai(GameTestHelper helper) {
		SaberBalance d = new SaberBalance();
		helper.assertTrue(d.offhandDamage == 0.7f && d.offhandSpeed == 0.2f && d.dualParryTicks == 1f,
				"10.2's defaults: the off hand deals 70% and recovers 20% slower; two blades add 1 parry tick");
		helper.assertTrue(near(SaberDual.offhandDamage(10.0, 0.0, true, d), 7.0) && near(SaberDual.offhandDamage(10.0, 0.0, false, d), 10.0)
				&& near(SaberDual.offhandDamage(10.0, 2.0, true, d), 8.4),
				"off-hand damage: the off saber's plus the player's extra, times offhandDamage unless Jar'Kai");
		helper.assertTrue(SaberDual.offhandTicks(2.0, 1.0, false, d) == 10 && SaberDual.offhandTicks(2.0, 1.0, true, d) == 12
				&& SaberDual.offhandTicks(2.0, 1.1, false, d) == 9 && SaberDual.offhandTicks(2.0, 0.5, true, d) == 24
				&& SaberDual.offhandTicks(100.0, 1.0, true, d) == 1,
				"off-hand recovery: 20 ticks over the off saber's speed times the player's ratio, offhandSpeed longer unless Jar'Kai, at least 1");

		for (SaberForm form : SaberForm.values()) {
			helper.assertTrue(form.modifiers(d).traits().offhandPenalty() == (form != SaberForm.JAR_KAI), form.id() + ": only Jar'Kai lifts the off-hand penalty");
		}

		helper.assertTrue(SaberPassives.effective(SaberForm.JAR_KAI, false) == SaberForm.UNFORMED && SaberPassives.effective(SaberForm.JAR_KAI, true) == SaberForm.JAR_KAI
				&& SaberPassives.effective(SaberForm.MAKASHI, true) == SaberForm.MAKASHI && SaberPassives.effective(SaberForm.SORESU, false) == SaberForm.SORESU,
				"Jar'Kai acts only while dual-wielding; the other forms always");

		Cleanup cleanup = new Cleanup();
		ServerPlayer[] p = new ServerPlayer[1];

		guarded(cleanup, () -> {
			SaberBalance b = SaberToggle.balance();
			ServerPlayer player = p[0] = listed(helper, cleanup, new BlockPos(1, 1, 1));
			UUID id = player.getUUID();
			ItemStack main = hold(player, lit(guardian(id), player));
			ItemStack off = offhand(player, lit(duelist(id), player));
			SaberStats mainStats = SaberStats.of(main, b);
			SaberStats offStats = SaberStats.of(off, b);
			double extra = Math.max(0.0, player.getAttributeValue(Attributes.ATTACK_DAMAGE) - mainStats.damage());
			double speedRatio = player.getAttributeValue(Attributes.ATTACK_SPEED) / mainStats.attackSpeed();
			helper.assertTrue(near(extra, 0.0) && near(speedRatio, 1.0), "precondition: only the main saber changes the attack numbers");
			helper.assertTrue(SaberDual.penalised(player, b), "Unformed takes the off-hand penalty");
			helper.assertTrue(near(SaberDual.offhandDamage(player, b), SaberDual.offhandDamage(offStats.damage(), extra, true, b)),
					"Unformed: the off hand deals offhandDamage of the off saber's damage");
			helper.assertTrue(SaberDual.offhandDelay(player, b) == SaberDual.offhandTicks(offStats.attackSpeed(), speedRatio, true, b),
					"Unformed: the off hand recovers offhandSpeed slower");

			float dual = SaberPassives.stats(player, main, b, 0f).parryTicks();
			dark(off, player);
			float single = SaberPassives.stats(player, main, b, 0f).parryTicks();
			lit(off, player);
			helper.assertTrue(b.parryWindowCap <= 0f || single + b.dualParryTicks <= b.parryWindowCap, "precondition: the dual-wield tick fits under the cap");
			helper.assertTrue(near(dual - single, b.dualParryTicks), "two lit blades add dualParryTicks to the parry window: " + single + " -> " + dual);

			setForm(helper, player, SaberForm.JAR_KAI);
			helper.assertTrue(SaberPassives.form(player) == SaberForm.JAR_KAI && !SaberDual.penalised(player, b), "Jar'Kai with two lit blades lifts the penalty");
			helper.assertTrue(near(SaberDual.offhandDamage(player, b), SaberDual.offhandDamage(offStats.damage(), extra, false, b)),
					"Jar'Kai: the off hand deals the off saber's full damage");
		});

		last(helper, cleanup, 3, () -> {
			ServerPlayer player = p[0];
			SaberBalance b = SaberToggle.balance();
			ItemStack off = player.getOffhandItem();
			double speedRatio = player.getAttributeValue(Attributes.ATTACK_SPEED) / SaberStats.of(player.getMainHandItem(), b).attackSpeed();
			helper.assertTrue(near(speedRatio, ratio(b.jarKaiSpeedPct)), "Jar'Kai's speed reaches the off hand through the player's speed ratio: " + speedRatio);
			helper.assertTrue(SaberDual.offhandDelay(player, b) == SaberDual.offhandTicks(SaberStats.of(off, b).attackSpeed(), speedRatio, false, b),
					"Jar'Kai: the off hand recovers at the off saber's speed, scaled, with no penalty");
			dark(off, player);
			helper.assertTrue(SaberPassives.form(player) == SaberForm.UNFORMED && SaberDual.penalised(player, b),
					"the off saber retracted: Jar'Kai acts as Unformed and the penalty is back");
		});
	}

	// ---------------------------------------------------------------- blade length (10.3)

	@GameTest(structure = ARENA, maxTicks = 20)
	public void bladeLengthStats(GameTestHelper helper) {
		SaberBalance d = new SaberBalance();
		helper.assertTrue(d.shortReach == -0.5f && d.shortSpeedPct == 10f && d.shortDamagePct == -10f && d.longReach == 0.5f && d.longSpeedPct == -10f
				&& d.longDamagePct == 5f, "10.3's defaults: short -0.5 reach, +10% speed, -10% damage; long +0.5 reach, -10% speed, +5% damage");

		SaberBalance b = SaberToggle.balance();
		SaberStats base = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, b);
		SaberStats shortBlade = base.withLength(SaberConfig.LENGTH_SHORT, b);
		SaberStats longBlade = base.withLength(SaberConfig.LENGTH_LONG, b);
		helper.assertTrue(base.withLength(SaberConfig.LENGTH_STANDARD, b).equals(base), "a standard blade changes nothing");
		helper.assertTrue(near(shortBlade.damage(), base.damage() * ratio(b.shortDamagePct))
				&& near(shortBlade.attackSpeed(), Math.max(0.1, base.attackSpeed() * ratio(b.shortSpeedPct)))
				&& near(shortBlade.reachBonus(), base.reachBonus() + b.shortReach) && shortBlade.maxStamina() == base.maxStamina()
				&& shortBlade.parryTicks() == base.parryTicks(), "a short blade: shortDamagePct damage, shortSpeedPct speed, shortReach reach; guard unchanged");
		helper.assertTrue(near(longBlade.damage(), base.damage() * ratio(b.longDamagePct))
				&& near(longBlade.attackSpeed(), Math.max(0.1, base.attackSpeed() * ratio(b.longSpeedPct)))
				&& near(longBlade.reachBonus(), base.reachBonus() + b.longReach) && longBlade.maxStamina() == base.maxStamina()
				&& longBlade.parryTicks() == base.parryTicks(), "a long blade: longDamagePct damage, longSpeedPct speed, longReach reach; guard unchanged");

		UUID owner = UUID.randomUUID();
		SaberConfig tooLong = new SaberConfig(crystal(owner, null), BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, Optional.empty(),
				Optional.empty(), 7);
		helper.assertTrue(tooLong.length() == SaberConfig.LENGTH_LONG && tooLong.withLength(-3).length() == SaberConfig.LENGTH_STANDARD
				&& tooLong.withLength(SaberConfig.LENGTH_SHORT).length() == SaberConfig.LENGTH_SHORT, "the blade length is kept to 0..2");
		helper.assertTrue(SaberStats.of(tooLong, b).equals(longBlade), "a long saber's stats are its parts' with the long blade");

		ItemStack staffStack = staff(owner, null, null);
		SaberStats staffStats = SaberStats.of(staffStack, b);
		configure(staffStack, SaberComponents.config(staffStack).withLength(SaberConfig.LENGTH_LONG));
		helper.assertTrue(SaberStats.of(staffStack, b).equals(staffStats), "a saberstaff ignores the blade length (staffs stay standard)");

		ItemStack guardian = guardian(owner);
		AttackRange retracted = range(helper, guardian);
		helper.assertTrue(retracted.minReach() == 0f && near(retracted.maxReach(), 3.0) && near(retracted.maxCreativeReach(), 5.0)
				&& retracted.hitboxMargin() == 0f, "a retracted saber reaches like a hand: 3 blocks, 5 in creative");
		SaberToggle.setIgnited(guardian, true, 0L);
		AttackRange litRange = range(helper, guardian);
		helper.assertTrue(near(litRange.maxReach(), 3.0 + base.reachBonus()) && near(litRange.maxCreativeReach(), litRange.maxReach() + 2.0),
				"a lit saber adds its reach bonus: " + litRange.maxReach());

		for (int length : LENGTHS_BY_REACH) {
			ItemStack blade = withLength(guardian.copy(), length);
			double bonus = base.withLength(length, b).reachBonus();
			helper.assertTrue(near(range(helper, blade).maxReach(), Math.max(0.5, 3.0 + bonus)), "blade length " + length + " reaches 3 + " + bonus);
			double interaction = itemModifier(blade, Attributes.ENTITY_INTERACTION_RANGE, SaberStats.REACH_MODIFIER_ID);
			helper.assertTrue(bonus == 0.0 ? Double.isNaN(interaction) : near(interaction, bonus),
					"blade length " + length + ": the interaction reach follows the blade (" + bonus + "), got " + interaction);
		}

		ItemStack darkShort = withLength(guardian(owner), SaberConfig.LENGTH_SHORT);
		helper.assertTrue(Double.isNaN(itemModifier(darkShort, Attributes.ENTITY_INTERACTION_RANGE, SaberStats.REACH_MODIFIER_ID))
				&& near(range(helper, darkShort).maxReach(), 3.0), "a retracted blade of any length reaches like a hand");

		// In the world: each length's reach, measured from the eyes to the target's box (no tolerance).
		Cleanup cleanup = new Cleanup();
		now(helper, cleanup, () -> {
			ServerPlayer player = listed(helper, cleanup, new BlockPos(2, 1, 2));
			Mob target = dummy(helper, cleanup, new BlockPos(2, 1, 12));
			ItemStack[] blades = new ItemStack[3];
			double[] r = new double[3];

			for (int i = 0; i < 3; i++) {
				blades[i] = withLength(lit(guardian(player.getUUID()), player), LENGTHS_BY_REACH[i]);
				r[i] = range(helper, blades[i]).maxReach();
			}

			helper.assertTrue(r[0] < r[1] && r[1] < r[2], "precondition: short < standard < long reach");
			double[] gaps = {r[0] - 0.3, (r[0] + r[1]) / 2.0, (r[1] + r[2]) / 2.0};

			for (int g = 0; g < gaps.length; g++) {
				placeAhead(target, player, gaps[g]);

				for (int i = 0; i < blades.length; i++) {
					boolean in = player.isWithinAttackRange(blades[i], target.getBoundingBox(), 0.0);
					helper.assertTrue(in == (i >= g), "a " + LENGTH_NAMES[i] + " blade (reach " + r[i] + ") at " + gaps[g] + " blocks should be "
							+ (i >= g ? "in" : "out of") + " reach");
				}
			}

			// Creative reaches 2 blocks further with every length.
			double beyond = r[0] + 1.75;
			helper.assertTrue(beyond > r[2], "precondition: the creative check stands beyond the long blade's survival reach");
			placeAhead(target, player, beyond);
			// API-CHECK: AttackRange#effectiveMaxRange is taken to read maxCreativeReach for a creative player (its body is not probed).
			player.setGameMode(GameType.CREATIVE);

			for (int i = 0; i < blades.length; i++) {
				helper.assertTrue(player.isWithinAttackRange(blades[i], target.getBoundingBox(), 0.0), "in creative a " + LENGTH_NAMES[i] + " blade reaches " + beyond);
			}

			player.setGameMode(GameType.SURVIVAL);

			for (int i = 0; i < blades.length; i++) {
				helper.assertTrue(!player.isWithinAttackRange(blades[i], target.getBoundingBox(), 0.0),
						"in survival a " + LENGTH_NAMES[i] + " blade does not reach " + beyond);
			}
		});
	}

	// ---------------------------------------------------------------- crystal tuning (10.4)

	@GameTest(maxTicks = 20)
	public void tuningSurvivesDisassembly(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		UUID owner = UUID.randomUUID();
		CrystalAttunement keen = crystal(owner, CrystalTuning.KEEN);
		ItemStack keenCrystal = SaberAssembly.crystalStack(keen);
		SimpleContainer bench = new SimpleContainer(SaberAssembly.SIZE);
		bench.setItem(SaberAssembly.CRYSTAL, keenCrystal.copy());
		bench.setItem(SaberAssembly.EMITTER, new ItemStack(SaberItems.EMITTER_DUELIST));
		bench.setItem(SaberAssembly.GRIP, new ItemStack(SaberItems.GRIP_RIDGED));
		bench.setItem(SaberAssembly.POMMEL, new ItemStack(SaberItems.POMMEL_BALANCED));
		helper.assertTrue(SaberAssembly.status(bench, owner) == SaberAssembly.Status.READY, "precondition: the bench is ready for the owner");

		ItemStack saber = SaberAssembly.assemble(bench, owner, BladeColor.VIOLET, b);
		SaberStats plain = SaberStats.of(Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, b);
		helper.assertTrue(!saber.isEmpty() && CrystalTuning.of(SaberComponents.config(saber).crystal()) == CrystalTuning.KEEN,
				"a saber built around a keen crystal carries the tuning");
		helper.assertTrue(near(SaberStats.of(saber, b).damage(), plain.damage() * ratio(b.keenPct)), "the keen crystal sharpens the built saber");

		helper.assertTrue(SaberAssembly.disassemble(bench, owner) != null, "precondition: the owner takes the saber apart");
		helper.assertTrue(ItemStack.isSameItemSameComponents(bench.getItem(SaberAssembly.CRYSTAL), keenCrystal), "the crystal comes back keen, unchanged");
		ItemStack rebuilt = SaberAssembly.assemble(bench, owner, BladeColor.VIOLET, b);
		helper.assertTrue(!rebuilt.isEmpty() && CrystalTuning.of(SaberComponents.config(rebuilt).crystal()) == CrystalTuning.KEEN,
				"rebuilt around the same crystal: still keen");

		ItemStack swift = SaberTunings.retunedSaber(rebuilt, false, CrystalTuning.SWIFT);
		SaberConfig before = SaberComponents.config(rebuilt);
		SaberConfig after = SaberComponents.config(swift);
		helper.assertTrue(swift != rebuilt && CrystalTuning.of(before.crystal()) == CrystalTuning.KEEN && CrystalTuning.of(after.crystal()) == CrystalTuning.SWIFT,
				"retuning gives a retuned copy and leaves the old stack as it was");
		helper.assertTrue(after.color() == before.color() && after.crystal().color() == before.crystal().color(), "a tuning never changes the blade's colour");
		SaberStats swiftStats = SaberStats.of(swift, b);
		helper.assertTrue(near(swiftStats.attackSpeed(), Math.max(0.1, plain.attackSpeed() * ratio(b.swiftPct))) && near(swiftStats.damage(), plain.damage()),
				"swift replaces keen: faster, and back to the plain damage");
		helper.assertTrue(near(itemModifier(swift, Attributes.ATTACK_SPEED, Item.BASE_ATTACK_SPEED_ID), swiftStats.attackSpeed() - 4.0),
				"the retuned saber's attack speed modifier is rewritten");
		helper.assertTrue(SaberTunings.retunedSaber(rebuilt, true, CrystalTuning.SWIFT).isEmpty(), "a single saber has no second crystal to tune");

		ItemStack warding = SaberTunings.retunedCrystal(keenCrystal, CrystalTuning.WARDING);
		helper.assertTrue(tuningOf(warding) == CrystalTuning.WARDING && tuningOf(keenCrystal) == CrystalTuning.KEEN, "a loose crystal retunes as a copy");
		helper.assertTrue(SaberTunings.retunedCrystal(new ItemStack(SaberItems.KYBER_CRYSTAL), CrystalTuning.KEEN).isEmpty(), "an unattuned crystal takes no tuning");

		ItemStack staffStack = staff(owner, CrystalTuning.KEEN, CrystalTuning.WARDING);
		SaberStats staffPlain = SaberStats.staff(Emitter.DUELIST, Grip.STAFF, true, b);
		SaberStats staffStats = SaberStats.of(staffStack, b);
		helper.assertTrue(near(staffStats.damage(), staffPlain.damage() * ratio(b.keenPct)) && near(staffStats.maxStamina(), staffPlain.maxStamina() + b.wardingGuard),
				"both of a saberstaff's crystals count: one keen, one warding");
		ItemStack steadySecond = SaberTunings.retunedSaber(staffStack, true, CrystalTuning.STEADY);
		SaberConfig steadyConfig = SaberComponents.config(steadySecond);
		helper.assertTrue(steadyConfig.second().isPresent() && CrystalTuning.of(steadyConfig.second().get()) == CrystalTuning.STEADY
				&& CrystalTuning.of(steadyConfig.crystal()) == CrystalTuning.KEEN, "a staff's second crystal is tuned on its own");
		SimpleContainer staffBench = new SimpleContainer(SaberAssembly.SIZE);
		staffBench.setItem(SaberAssembly.SABER, staffStack.copy());
		helper.assertTrue(SaberAssembly.disassemble(staffBench, owner) != null, "precondition: the owner takes the staff apart");
		helper.assertTrue(tuningOf(staffBench.getItem(SaberAssembly.CRYSTAL)) == CrystalTuning.KEEN
				&& tuningOf(staffBench.getItem(SaberAssembly.SECOND)) == CrystalTuning.WARDING, "taking a staff apart returns both crystals, still tuned");

		// A long, swift saber saves and loads unchanged (the optional length and tuning fields are written).
		ItemStack stored = withLength(swift.copy(), SaberConfig.LENGTH_LONG);
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = ItemStack.CODEC.encodeStart(ops, stored).getOrThrow();
		ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		SaberConfig loadedConfig = SaberComponents.config(loaded);
		helper.assertTrue(loadedConfig.length() == SaberConfig.LENGTH_LONG && CrystalTuning.of(loadedConfig.crystal()) == CrystalTuning.SWIFT
				&& ItemStack.isSameItemSameComponents(loaded, stored), "a long, swift saber round-trips through the item codec");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void tuningEffects(GameTestHelper helper) {
		SaberBalance d = new SaberBalance();
		helper.assertTrue(d.tuneCredits == 100 && d.keenPct == 5f && d.swiftPct == 6f && d.steadyTicks == 1f && d.wardingGuard == 15f && d.resonantPct == 5f
				&& d.tuneSunstone == 2 && d.tuneStormglass == 2 && d.tuneHymnstone == 1,
				"10.4's defaults: 100 credits; keen +5% damage, swift +6% speed, poised +1 parry, warding +15 guard, attuned -5% Force cost; 2/2/1 materials");
		String[] ids = {"keen", "swift", "steady", "warding", "resonant"};
		helper.assertTrue(CrystalTuning.values().length == ids.length, "five tunings");

		for (int i = 0; i < ids.length; i++) {
			CrystalTuning t = CrystalTuning.values()[i];
			helper.assertTrue(t.id().equals(ids[i]) && CrystalTuning.byId(ids[i]) == t && CrystalTuning.byIndex(i) == t,
					"tuning " + i + " is " + ids[i] + " (saved by id, sent by ordinal)");
		}

		helper.assertTrue(CrystalTuning.byId("") == null && CrystalTuning.byId("bogus") == null && CrystalTuning.byIndex(ids.length) == null
				&& CrystalTuning.byIndex(-1) == null, "no tuning for an empty or unknown id or an index out of range");
		helper.assertTrue(CrystalTuning.KEEN.material() == M5Items.SUNSTONE && CrystalTuning.SWIFT.material() == M5Items.SUNSTONE
				&& CrystalTuning.STEADY.material() == M5Items.STORMGLASS_SHARD && CrystalTuning.WARDING.material() == M5Items.STORMGLASS_SHARD
				&& CrystalTuning.RESONANT.material() == M5Items.HYMNSTONE_SHARD, "the materials: sunstone, stormglass shards, a hymnstone shard");
		helper.assertTrue(CrystalTuning.KEEN.materialCount(d) == 2 && CrystalTuning.WARDING.materialCount(d) == 2 && CrystalTuning.RESONANT.materialCount(d) == 1
				&& CrystalTuning.SWIFT.materialStack(d).getCount() == 2, "the material counts");

		SaberBalance b = SaberToggle.balance();
		UUID owner = UUID.randomUUID();
		SaberStats base = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, b);
		SaberStats keen = base.tuned(crystal(owner, CrystalTuning.KEEN), null, b);
		SaberStats swift = base.tuned(crystal(owner, CrystalTuning.SWIFT), null, b);
		SaberStats steady = base.tuned(crystal(owner, CrystalTuning.STEADY), null, b);
		SaberStats warding = base.tuned(crystal(owner, CrystalTuning.WARDING), null, b);
		helper.assertTrue(near(keen.damage(), base.damage() * ratio(b.keenPct)) && keen.attackSpeed() == base.attackSpeed() && keen.maxStamina() == base.maxStamina(),
				"keen: keenPct more damage, nothing else");
		helper.assertTrue(near(swift.attackSpeed(), Math.max(0.1, base.attackSpeed() * ratio(b.swiftPct))) && swift.damage() == base.damage(),
				"swift: swiftPct more attack speed");
		helper.assertTrue(near(steady.parryTicks(), base.parryTicks() + b.steadyTicks) && steady.maxStamina() == base.maxStamina(),
				"poised: steadyTicks more parry window");
		helper.assertTrue(near(warding.maxStamina(), base.maxStamina() + b.wardingGuard) && warding.parryTicks() == base.parryTicks(),
				"warding: wardingGuard more guard stamina");
		helper.assertTrue(base.tuned(crystal(owner, CrystalTuning.RESONANT), null, b).equals(base) && base.tuned(crystal(owner, null), null, b).equals(base)
				&& base.tuned(null, null, b).equals(base), "attuned and untuned crystals change no stat");
		SaberStats steadyMakashi = steady.withForm(SaberForm.MAKASHI.modifiers(b), 0, 5f, b);
		helper.assertTrue(near(steadyMakashi.parryTicks(), SaberStats.parryWindow(steady.parryTicks(), 5f, 1f, b.makashiParryTicks, b.parryWindowCap))
				&& (b.parryWindowCap <= 0f || steadyMakashi.parryTicks() <= b.parryWindowCap), "the poised tick goes on before the form, under the cap");
		SaberStats staffBase = SaberStats.staff(Emitter.DUELIST, Grip.STAFF, true, b);
		helper.assertTrue(near(staffBase.tuned(crystal(owner, CrystalTuning.KEEN), crystal(owner, CrystalTuning.KEEN), b).damage(),
				staffBase.damage() * (1.0 + 2.0 * b.keenPct / 100.0)), "two keen crystals in a staff count twice");

		Cleanup cleanup = new Cleanup();
		now(helper, cleanup, () -> {
			ServerPlayer player = listed(helper, cleanup, new BlockPos(1, 1, 1));
			UUID id = player.getUUID();
			SaberStats plainOff = SaberStats.of(Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, b);

			// A keen off-hand crystal sharpens only the off hand's strikes, and the other way round.
			ItemStack plainMain = hold(player, lit(guardian(id), player));
			ItemStack keenOff = offhand(player, lit(saber(id, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, CrystalTuning.KEEN), player));
			double extra = Math.max(0.0, player.getAttributeValue(Attributes.ATTACK_DAMAGE) - SaberStats.of(plainMain, b).damage());
			helper.assertTrue(near(SaberStats.of(keenOff, b).damage(), plainOff.damage() * ratio(b.keenPct))
					&& near(SaberDual.offhandDamage(player, b), SaberDual.offhandDamage(SaberStats.of(keenOff, b).damage(), extra, true, b)),
					"a keen off-hand crystal sharpens the off hand's strikes");
			helper.assertTrue(near(player.getAttributeValue(Attributes.ATTACK_DAMAGE), SaberStats.of(plainMain, b).damage()), "...and not the main hand's");
			hold(player, lit(saber(id, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, CrystalTuning.KEEN), player));
			offhand(player, lit(duelist(id), player));
			helper.assertTrue(near(SaberDual.offhandDamage(player, b), SaberDual.offhandDamage(plainOff.damage(), 0.0, true, b)),
					"a keen main-hand crystal leaves the off hand's strikes alone");

			// The Tune tab's checks, in order, then the price.
			CrystalAttunement own = crystal(id, null);
			CrystalTuning t = CrystalTuning.KEEN;
			helper.assertTrue(SaberTunings.check(player, null, t, b) == SaberTunings.Result.NO_CRYSTAL, "no crystal");
			helper.assertTrue(SaberTunings.check(player, crystal(UUID.randomUUID(), null), t, b) == SaberTunings.Result.NOT_YOURS, "someone else's crystal");
			helper.assertTrue(SaberTunings.check(player, crystal(id, t), t, b) == SaberTunings.Result.ALREADY, "the tuning it already has");
			helper.assertTrue(count(player, t.material()) == 0 && SaberTunings.check(player, own, t, b) == SaberTunings.Result.NO_MATERIALS, "no material");
			player.getInventory().add(t.materialStack(b));
			helper.assertTrue(SaberTunings.check(player, own, t, b) == (b.tuneCredits > 0 ? SaberTunings.Result.NO_CREDITS : SaberTunings.Result.TUNED),
					"the material but no credits");
			Credits.give(player, b.tuneCredits);
			helper.assertTrue(SaberTunings.check(player, own, t, b) == SaberTunings.Result.TUNED, "the material and the credits");
			helper.assertTrue(SaberTunings.check(player, crystal(id, CrystalTuning.SWIFT), t, b) == SaberTunings.Result.TUNED,
					"a tuned crystal may be retuned (the old tuning is replaced)");
			helper.assertTrue(SaberTunings.pay(player, t, b), "paying succeeds");
			helper.assertTrue(count(player, t.material()) == 0 && Credits.count(player, true) == 0, "paying takes the material and tuneCredits credits");
			helper.assertTrue(SaberTunings.check(player, own, t, b) == SaberTunings.Result.NO_MATERIALS, "nothing is left for a second tuning");
		});
	}

	@GameTest(maxTicks = 20)
	public void resonantCountsOnce(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		float attuned = Math.max(0f, 1f - b.resonantPct / 100f);
		Cleanup cleanup = new Cleanup();

		now(helper, cleanup, () -> {
			ServerPlayer player = listed(helper, cleanup, new BlockPos(1, 1, 1));
			UUID id = player.getUUID();
			ProgressionBalance pb = Progression.balance();
			float before = ForceModifiers.cost(player, SOME_POWER);
			helper.assertTrue(SaberTunings.attunedMultiplier(player, b) == 1f, "no saber: no change");
			helper.assertTrue(before * attuned >= pb.modifierCostMin && before <= pb.modifierCostMax, "precondition: the cost clamp leaves room for the tuning");

			ItemStack resonant = hold(player, saber(id, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, CrystalTuning.RESONANT));
			helper.assertTrue(SaberTunings.attunedMultiplier(player, b) == 1f, "a retracted Attuned saber changes nothing");
			lit(resonant, player);
			helper.assertTrue(SaberTunings.attunedMultiplier(player, b) == attuned && near(ForceModifiers.cost(player, SOME_POWER), before * attuned),
					"a lit Attuned saber: powers cost resonantPct less");
			offhand(player, lit(saber(id, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, CrystalTuning.RESONANT), player));
			helper.assertTrue(SaberTunings.attunedMultiplier(player, b) == attuned && near(ForceModifiers.cost(player, SOME_POWER), before * attuned),
					"two Attuned sabers count once");
			hold(player, lit(guardian(id), player));
			helper.assertTrue(SaberTunings.attunedMultiplier(player, b) == attuned, "an Attuned saber in the off hand alone counts");
			offhand(player, ItemStack.EMPTY);

			ItemStack thrown = hold(player, lit(saber(id, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, CrystalTuning.RESONANT), player));
			thrown.set(SaberComponents.SABER_THROWN, id);
			helper.assertTrue(SaberTunings.attunedMultiplier(player, b) == 1f, "a thrown Attuned saber is not in hand");

			ItemStack staffStack = hold(player, lit(staff(id, CrystalTuning.RESONANT, CrystalTuning.RESONANT), player));
			helper.assertTrue(CrystalTuning.count(SaberComponents.config(staffStack), CrystalTuning.RESONANT) == 2
					&& SaberTunings.attunedMultiplier(player, b) == attuned && near(ForceModifiers.cost(player, SOME_POWER), before * attuned),
					"a staff with two Attuned crystals still counts once");
		});
	}

	// ---------------------------------------------------------------- the Legendary tier (11.1)

	/** The {@code level} a schematic file of {@code discipline} ends with after the loader's legendary pass at {@code legendaryLevel}. */
	private static int levelAfter(Identifier id, String discipline, int legendaryLevel) {
		JsonObject json = new JsonObject();
		json.addProperty("discipline", discipline);
		json.addProperty("level", 1);
		JsonElement out = SchematicLoader.withLegendaryLevel(id, json, legendaryLevel);
		return out.getAsJsonObject().get("level").getAsInt();
	}

	@GameTest(maxTicks = 20)
	public void legendaryNeverRolledNorSalvaged(GameTestHelper helper) {
		Identifier legendaryId = OldRepublicMod.id("test_part_legendary");
		helper.assertTrue(levelAfter(legendaryId, "artifice", 3) == 3 && levelAfter(legendaryId, "artifice", 9) == 5 && levelAfter(legendaryId, "artifice", 0) == 1,
				"a discipline's legendary schematic takes legendaryLevel, kept to 1..5");
		helper.assertTrue(levelAfter(legendaryId, "common", 3) == 1 && levelAfter(OldRepublicMod.id("test_part"), "artifice", 3) == 1,
				"a common legendary schematic and a plain one keep their level");

		int legendaryLevel = Math.max(1, Math.min(5, CraftingBalance.get().legendaryLevel));
		Cleanup cleanup = new Cleanup();

		now(helper, cleanup, () -> {
			ServerPlayer player = listed(helper, cleanup, new BlockPos(1, 1, 1));
			setDiscipline(helper, player, Discipline.ARTIFICE, 5);
			setDiscipline(helper, player, Discipline.ARMSTECH, 5);
			RandomSource random = RandomSource.create(SEED);

			for (LegendarySpec spec : LEGENDARIES) {
				Identifier id = spec.schematic();
				Schematic s = Schematics.get(id);
				helper.assertTrue(s != null, id + " must load");
				helper.assertTrue(Crafting.isLegendary(s) && !s.quality() && s.learned() == Schematic.Learned.ITEM && s.discipline().orElse(null) == spec.discipline()
						&& s.level() == (spec.discipline() == null ? 1 : legendaryLevel),
						id + ": a legendary schematic of its discipline at the legendary level, taught by an item, with no quality roll");
				helper.assertTrue(BuiltInRegistries.ITEM.getKey(s.outputItem()).equals(OldRepublicMod.id(spec.output())) && s.outputCount() == 1
						&& inputs(s).equals(spec.inputs()), id + ": makes one " + spec.output() + " from " + spec.inputs() + "; the file has " + inputs(s));

				CraftingData before = CraftingAttachments.get(player);
				helper.assertTrue(Crafting.roll(player, s, random) == Quality.LEGENDARY && CraftingAttachments.get(player).equals(before),
						id + ": always Legendary, with no draw and no pity");
				helper.assertTrue(!Crafting.knows(player, id), id + ": unknown until taught");
				Crafting.teach(player, id);
				helper.assertTrue(Crafting.knows(player, id) && !BenchService.mayCommission(player, s, null)
						&& !BenchService.mayCommission(player, s, s.discipline().orElse(null)) && BenchService.commission(player, id, null) == BenchResult.LOCKED,
						id + ": never commissioned, even when known");

				ItemStack made = Crafting.applyQuality(s.result(), Quality.LEGENDARY);
				helper.assertTrue(Salvage.tooRare(made) && !Salvage.canSalvage(made) && Salvage.schematicFor(made) == null && Salvage.returns(made, 1f).isEmpty()
						&& BenchService.salvage(player, made) == BenchResult.TOO_RARE, id + ": too rare to break down");
				helper.assertTrue(Salvage.tooRare(s.result()), id + ": its item is too rare however it was made");
			}

			ItemStack legendaryPistol = Crafting.applyQuality(new ItemStack(EnemyItems.BLASTER_PISTOL), Quality.LEGENDARY);
			helper.assertTrue(Salvage.tooRare(legendaryPistol) && BenchService.salvage(player, legendaryPistol) == BenchResult.TOO_RARE,
					"anything of Legendary quality is too rare to break down");
			helper.assertTrue(!Salvage.tooRare(new ItemStack(EnemyItems.BLASTER_PISTOL)), "a Standard pistol is not");

			Schematic pistol = Salvage.schematicFor(new ItemStack(EnemyItems.BLASTER_PISTOL));
			helper.assertTrue(pistol != null && pistol.quality(), "precondition: the pistol has a quality schematic");

			for (int i = 0; i < 200; i++) {
				helper.assertTrue(Crafting.roll(player, pistol, random) != Quality.LEGENDARY, "a quality roll never gives Legendary");
			}

			for (int i = 0; i <= 100; i++) {
				float r = i / 100f;
				helper.assertTrue(CraftingRules.pick(0.3f, 0.1f, r) != Quality.LEGENDARY && CraftingRules.pick(1f, 1f, r) != Quality.LEGENDARY
						&& CraftingRules.pick(0f, 0f, r) != Quality.LEGENDARY, "the quality pick never gives Legendary (draw " + r + ")");
			}
		});
	}

	@GameTest(maxTicks = 20)
	public void legendaryMultipliers(GameTestHelper helper) {
		CraftingBalance d = new CraftingBalance();
		helper.assertTrue(Arrays.equals(d.qualityWeaponDamage, new float[] {1.0f, 1.06f, 1.12f, 1.18f}) && Arrays.equals(d.qualityArmour, new float[] {1.0f, 1.15f, 1.30f, 1.40f})
				&& Arrays.equals(d.qualityDurability, new float[] {1.0f, 1.25f, 1.5f, 1.75f}) && d.legendaryLevel == 5,
				"11.1's defaults: Legendary is x1.18 damage, x1.40 armour, x1.75 durability; legendary schematics at level 5");
		helper.assertTrue(Quality.LEGENDARY.ordinal() == 3 && Quality.of(3) == Quality.LEGENDARY && Quality.of(9) == Quality.LEGENDARY && Quality.of(-1) == Quality.STANDARD,
				"Legendary is the fourth quality; saved ordinals are clamped");

		CraftingBalance c = CraftingBalance.get();
		helper.assertTrue(Quality.LEGENDARY.weapon() == (c.qualityWeaponDamage.length == 4 ? c.qualityWeaponDamage[3] : 1.18f)
				&& Quality.LEGENDARY.armour() == (c.qualityArmour.length == 4 ? c.qualityArmour[3] : 1.40f)
				&& Quality.LEGENDARY.durability() == (c.qualityDurability.length == 4 ? c.qualityDurability[3] : 1.75f),
				"the live four-value tables set Legendary's multipliers; a three-value table keeps the default");

		ItemStack plain = new ItemStack(EnemyItems.VIBROBLADE);
		double damage0 = itemModifier(plain, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID);
		Integer max0 = plain.get(DataComponents.MAX_DAMAGE);
		helper.assertTrue(!Double.isNaN(damage0) && max0 != null && max0 > 0, "precondition: a vibroblade has attack damage and durability");
		ItemStack legendary = Crafting.applyQuality(plain.copy(), Quality.LEGENDARY);
		float w = Quality.LEGENDARY.weapon();
		helper.assertTrue(near(itemModifier(legendary, Attributes.ATTACK_DAMAGE, Item.BASE_ATTACK_DAMAGE_ID), (damage0 + 1.0) * w - 1.0),
				"a Legendary weapon deals x" + w + " damage");
		Integer maxL = legendary.get(DataComponents.MAX_DAMAGE);
		helper.assertTrue(maxL != null && maxL == Math.max(1, Math.round(max0 * Quality.LEGENDARY.durability())), "a Legendary weapon lasts x" + Quality.LEGENDARY.durability());
		helper.assertTrue(legendary.get(DataComponents.RARITY) == Rarity.EPIC && Crafting.quality(legendary) == Quality.LEGENDARY, "a Legendary item is epic");
		Component gold = plain.getItemName().copy().withStyle(ChatFormatting.GOLD);
		helper.assertTrue(gold.equals(legendary.get(DataComponents.ITEM_NAME)), "a Legendary item's name is gold");
		ItemStack masterwork = Crafting.applyQuality(plain.copy(), Quality.MASTERWORK);
		helper.assertTrue(Objects.equals(masterwork.get(DataComponents.ITEM_NAME), plain.get(DataComponents.ITEM_NAME))
				&& masterwork.get(DataComponents.RARITY) == Rarity.EPIC, "a Masterwork item is epic but keeps its name (no gold)");

		ItemStack chest = new ItemStack(EquipmentItems.WATCH_BATTLE_CHEST);
		double armour0 = armour(chest);
		ItemStack legendaryChest = Crafting.applyQuality(chest.copy(), Quality.LEGENDARY);
		helper.assertTrue(armour0 > 0.0 && near(armour(legendaryChest), Math.floor(armour0 * Quality.LEGENDARY.armour() + Crafting.ARMOUR_EPSILON)),
				"Legendary armour: x" + Quality.LEGENDARY.armour() + ", rounded down");

		if (Quality.LEGENDARY.armour() == 1.40f && armour0 == 5.0) {
			helper.assertTrue(near(armour(legendaryChest), 7.0), "5 armour at Legendary is 7 (the float 1.40 is just under 1.4)");
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the pity counters (11.4)

	/** Rolls {@code id} at chance 0 until the {@code pity}th try, which must teach it. */
	private static void reachPity(GameTestHelper helper, ServerPlayer player, Identifier id, int pity, RandomSource random) {
		while (misses(player, id) < pity - 1) {
			int before = misses(player, id);
			helper.assertTrue(!LegendaryDrops.roll(player, id, 0f, pity, random) && misses(player, id) == before + 1,
					id + ": try " + (before + 1) + " of " + pity + " misses at chance 0 and counts");
		}

		helper.assertTrue(LegendaryDrops.roll(player, id, 0f, pity, random), id + ": the try number " + pity + " always teaches it");
		helper.assertTrue(misses(player, id) == 0 && Crafting.knows(player, id), id + ": learned, its counter cleared");
	}

	@GameTest(maxTicks = 20)
	public void pityCounters(GameTestHelper helper) {
		helper.assertTrue(!LegendaryDrops.hits(0, 0f, 8, 0.99f) && !LegendaryDrops.hits(6, 0f, 8, 0.99f) && LegendaryDrops.hits(7, 0f, 8, 0.99f)
				&& LegendaryDrops.hits(12, 0f, 8, 0.99f), "with pity 8, the 8th try (after 7 misses) always hits");
		helper.assertTrue(LegendaryDrops.hits(0, 0.5f, 8, 0.4f) && !LegendaryDrops.hits(0, 0.5f, 8, 0.6f), "before that, a draw under the chance hits");
		helper.assertTrue(!LegendaryDrops.hits(100, 0f, 0, 0f) && LegendaryDrops.hits(0, 2f, 0, 0.999f) && !LegendaryDrops.hits(0, -1f, 0, 0f),
				"pity 0 means no guarantee; the chance is kept to 0..1");

		Identifier capacitor = OldRepublicMod.id("spindle_capacitor_legendary");
		Identifier drive = OldRepublicMod.id("hymn_tuned_drive_legendary");
		Identifier edge = OldRepublicMod.id("mod_sunstone_edge_legendary");
		Identifier cell = OldRepublicMod.id("mod_storm_cell_legendary");
		Cleanup cleanup = new Cleanup();

		now(helper, cleanup, () -> {
			ServerPlayer player = listed(helper, cleanup, new BlockPos(1, 1, 1));
			RandomSource random = RandomSource.create(SEED);

			for (int i = 0; i < 3; i++) {
				helper.assertTrue(!LegendaryDrops.roll(player, capacitor, 0f, 8, random), "a capacitor miss");
			}

			for (int i = 0; i < 2; i++) {
				helper.assertTrue(!LegendaryDrops.roll(player, edge, 0f, 30, random), "an edge miss");
			}

			helper.assertTrue(misses(player, capacitor) == 3 && misses(player, edge) == 2 && misses(player, drive) == 0, "each schematic counts its own misses");
			// 11.4's guarantees: the vault's capacitor on the 8th try, the bounties' drive on the 25th, the holds' edge on the 30th.
			reachPity(helper, player, capacitor, 8, random);
			helper.assertTrue(misses(player, edge) == 2, "learning one schematic leaves the other counters alone");
			reachPity(helper, player, drive, 25, random);
			reachPity(helper, player, edge, 30, random);

			CraftingAttachments.set(player, CraftingAttachments.get(player).withMisses(cell.toString(), 5));
			helper.assertTrue(misses(player, cell) == 5, "precondition: five storm cell misses");
			helper.assertTrue(LegendaryDrops.roll(player, cell, 1f, 0, random) && misses(player, cell) == 0 && Crafting.knows(player, cell),
					"a hit teaches it and clears the counter");

			for (int i = 0; i < 5; i++) {
				helper.assertTrue(!LegendaryDrops.roll(player, cell, 1f, 1, random) && misses(player, cell) == 0, "a learned schematic is never rolled again");
			}
		});
	}

	@GameTest(maxTicks = 20)
	public void knownSchematicNeverRolled(GameTestHelper helper) {
		Identifier cell = OldRepublicMod.id("mod_storm_cell_legendary");
		Identifier lens = OldRepublicMod.id("lens_stormglass_legendary");
		Identifier missile = OldRepublicMod.id("concussion_missile");
		Cleanup cleanup = new Cleanup();

		now(helper, cleanup, () -> {
			ServerPlayer player = listed(helper, cleanup, new BlockPos(1, 1, 1));
			RandomSource random = RandomSource.create(SEED);

			// Taught by a vendor or a reward with an old counter still standing.
			CraftingAttachments.set(player, CraftingAttachments.get(player).withMisses(cell.toString(), 5));
			Crafting.teach(player, cell);
			Set<Identifier> known = Set.copyOf(CraftingAttachments.get(player).known());

			for (int i = 0; i < 10; i++) {
				helper.assertTrue(!LegendaryDrops.roll(player, cell, 1f, 1, random), "a known schematic is never rolled, even at chance 1 and pity 1");
			}

			helper.assertTrue(misses(player, cell) == 0 && CraftingAttachments.get(player).known().equals(known), "its stale counter is dropped and nothing else changes");

			setDiscipline(helper, player, Discipline.ARMSTECH, 3);
			helper.assertTrue(Crafting.knows(player, missile), "precondition: Armstech 3 knows the concussion missile schematic");
			helper.assertTrue(!LegendaryDrops.roll(player, missile, 1f, 1, random) && misses(player, missile) == 0,
					"a schematic known through the discipline level is never rolled either");

			helper.assertTrue(!Crafting.knows(player, lens) && LegendaryDrops.roll(player, lens, 1f, 0, random) && Crafting.knows(player, lens),
					"an unknown schematic drops at chance 1");

			for (int i = 0; i < 5; i++) {
				helper.assertTrue(!LegendaryDrops.roll(player, lens, 1f, 1, random), "once learned, never again");
			}
		});
	}

	// ---------------------------------------------------------------- the eight W7 legendary components (11.2)

	@GameTest(structure = ARENA, maxTicks = 20)
	public void legendaryComponentEffects(GameTestHelper helper) {
		helper.assertTrue(SaberStats.Legendary.DEFAULTS.equals(new SaberStats.Legendary(8f, 15f, 1f, 8f, 5f)),
				"11.2's defaults: the Cantor's Emitter +8% damage, the Hask Pommel +15 guard and +1 parry tick, the Sunforged Grip +8% speed and +5 guard");
		helper.assertTrue(SaberStats.legendary().equals(SaberStats.Legendary.of(CraftingBalance.get())), "the legendary parts use the live crafting numbers");
		SaberBalance b = SaberToggle.balance();
		SaberStats.Legendary l = SaberStats.legendary();

		SaberStats cantor = SaberStats.of(Emitter.CANTOR, Grip.WRAPPED, Pommel.WEIGHTED, b);
		SaberStats reliquary = SaberStats.of(Emitter.RELIQUARY, Grip.WRAPPED, Pommel.WEIGHTED, b);
		helper.assertTrue(near(cantor.damage(), reliquary.damage() * ratio(l.cantorDamagePct())) && near(cantor.attackSpeed(), reliquary.attackSpeed())
				&& near(cantor.reachBonus(), reliquary.reachBonus()) && near(cantor.maxStamina(), reliquary.maxStamina()) && near(cantor.parryTicks(), reliquary.parryTicks()),
				"the Cantor's Emitter: cantorDamagePct more damage than a numberless emitter, nothing else");
		SaberStats hask = SaberStats.of(Emitter.GUARDIAN, Grip.RIDGED, Pommel.HASK, b);
		SaberStats noPommel = SaberStats.of(Emitter.GUARDIAN, Grip.RIDGED, null, b);
		helper.assertTrue(near(hask.maxStamina(), noPommel.maxStamina() + l.haskGuard()) && near(hask.parryTicks(), noPommel.parryTicks() + l.haskParryTicks())
				&& near(hask.damage(), noPommel.damage()) && near(hask.attackSpeed(), noPommel.attackSpeed()),
				"the Hask Pommel: haskGuard more guard and haskParryTicks more parry window");
		SaberStats sunforged = SaberStats.of(Emitter.DUELIST, Grip.SUNFORGED, Pommel.WEIGHTED, b);
		SaberStats bareGrip = SaberStats.of(Emitter.DUELIST, Grip.STAFF, Pommel.WEIGHTED, b);
		helper.assertTrue(near(sunforged.attackSpeed(), Math.max(0.1, bareGrip.attackSpeed() * ratio(l.sunforgedSpeedPct())))
				&& near(sunforged.maxStamina(), bareGrip.maxStamina() + l.sunforgedGuard()) && near(sunforged.damage(), bareGrip.damage()),
				"the Sunforged Grip: sunforgedSpeedPct more speed and sunforgedGuard more guard");

		for (HiltParts.Part part : new HiltParts.Part[] {Emitter.CANTOR, Grip.SUNFORGED, Pommel.HASK}) {
			ItemStack loose = SaberItems.partStack(part);
			helper.assertTrue(SaberItems.isLegendary(part) && SaberItems.hasItem(part) && loose.is(SaberItems.partItem(part)) && Crafting.quality(loose) == Quality.LEGENDARY,
					part.itemId() + ": its own item, Legendary when it comes out of a saber");
		}

		helper.assertTrue(SaberItems.partItem(Emitter.CANTOR) == SaberItems.EMITTER_CANTOR && SaberItems.partItem(Grip.SUNFORGED) == SaberItems.GRIP_SUNFORGED
				&& SaberItems.partItem(Pommel.HASK) == SaberItems.POMMEL_HASK, "the legendary parts' items");
		helper.assertTrue(!SaberItems.isLegendary(Emitter.RELIQUARY) && !SaberItems.isLegendary(Grip.STAFF) && Crafting.quality(SaberItems.partStack(Emitter.GUARDIAN)) == Quality.STANDARD,
				"the other parts are plain");
		helper.assertTrue(SaberItems.lensItem(SaberLens.Lens.STORMGLASS) == SaberItems.LENS_STORMGLASS
				&& SaberLens.Lens.byItemId(OldRepublicMod.id("lens_stormglass")) == SaberLens.Lens.STORMGLASS, "the Stormglass Lens's item");
		helper.assertTrue(Mods.slotOf(LegendaryMods.CLAN_IRON_PLATING) == ModSlot.ARMOUR && Mods.slotOf(LegendaryMods.HYMNWEAVE_LINING) == ModSlot.ARMOUR
				&& Mods.slotOf(LegendaryMods.STORM_CELL) == ModSlot.CELL && Mods.slotOf(LegendaryMods.SUNSTONE_EDGE) == ModSlot.EDGE && LegendaryMods.ALL.size() == 4,
				"the legendary mods' slots: plating and lining in armour, the cell in a blaster's cell slot, the edge on a vibroweapon");

		Cleanup cleanup = new Cleanup();
		now(helper, cleanup, () -> {
			ServerLevel level = helper.getLevel();
			CraftingBalance c = CraftingBalance.get();
			EquipmentModifiers gear = new EquipmentModifiers();
			// Facing north (-z), so fired bolts fly away from the dummies.
			ServerPlayer player = listed(helper, cleanup, new BlockPos(12, 1, 3));
			face(player, 180f);
			UUID id = player.getUUID();
			Mob struck = dummy(helper, cleanup, new BlockPos(11, 1, 7));
			Mob nextTo = dummy(helper, cleanup, new BlockPos(13, 1, 7));
			Mob wearer = dummy(helper, cleanup, new BlockPos(20, 1, 20));
			Mob burn = dummy(helper, cleanup, new BlockPos(14, 1, 3));

			// The Stormglass Lens: finishers shock, droids take more; always Legendary.
			ItemStack lensed = guardian(id);
			lensed.set(SaberComponents.SABER_CONFIG, SaberComponents.config(lensed).withLens(Optional.of(SaberLens.Lens.STORMGLASS.itemId())));
			lensed.set(SaberComponents.LENS_QUALITY, Quality.LEGENDARY.ordinal());
			ItemStack held = hold(player, lensed);
			LivingEntity droid = DroidEntities.SECURITY_DROID.create(level, EntitySpawnReason.EVENT);
			helper.assertTrue(droid != null && MeleeCombat.isDroid(droid), "precondition: a security droid is a droid");
			helper.assertTrue(SaberLens.finisherShockTicks(player, held) == 0 && near(SaberLens.damageMult(player, held, droid), 1.0),
					"a retracted saber's lens does nothing");
			lit(held, player);
			helper.assertTrue(SaberLens.finisherShockTicks(player, held) == c.stormglassShockTicks, "a lit Stormglass Lens: finishers shock for stormglassShockTicks");
			helper.assertTrue(near(SaberLens.damageMult(player, held, droid), ratio(c.stormglassDroidPct)) && near(SaberLens.damageMult(player, held, struck), 1.0),
					"a lit Stormglass Lens: stormglassDroidPct more damage to droids, none to others");
			ItemStack lensBack = SaberAssembly.lensStack(held);
			helper.assertTrue(lensBack.is(SaberItems.LENS_STORMGLASS) && Crafting.quality(lensBack) == Quality.LEGENDARY, "the lens comes off the saber Legendary");

			// Clan-Iron Plating: flat armour while worn, less blaster damage.
			wearSet(wearer, ArmourSet.WATCH_BATTLE);
			ItemStack plainHelmet = wearer.getItemBySlot(EquipmentSlot.HEAD).copy();
			wear(wearer, EquipmentSlot.HEAD, moddedHead(helper, ArmourSet.WATCH_BATTLE, LegendaryMods.CLAN_IRON_PLATING));
			EquipmentEvents.updateGear(wearer, ArmourSets.worn(wearer));
			helper.assertTrue(near(modifier(wearer, Attributes.ARMOR, ArmourAttributes.MOD_CLAN_IRON), c.clanIronArmour)
					&& near(ModEffects.wornClanIronBlaster(wearer, c), 1.0 - c.clanIronBlasterPct / 100.0),
					"Clan-Iron Plating: clanIronArmour armour and clanIronBlasterPct less blaster damage per piece");
			BlasterBolt bolt = BlasterBolt.create(level, player, wearer.position(), 10f, 20);
			DamageSource boltHit = boltSource(level, bolt, player);
			float withPlating = gear.modify(wearer, boltHit, 10f);
			wear(wearer, EquipmentSlot.HEAD, plainHelmet);
			EquipmentEvents.updateGear(wearer, ArmourSets.worn(wearer));
			float withoutPlating = gear.modify(wearer, boltHit, 10f);
			helper.assertTrue(Double.isNaN(modifier(wearer, Attributes.ARMOR, ArmourAttributes.MOD_CLAN_IRON)), "taking the plating off takes its armour away");
			helper.assertTrue(withoutPlating > 0f && near(withPlating / withoutPlating, 1.0 - c.clanIronBlasterPct / 100.0),
					"Clan-Iron Plating: a bolt deals " + withPlating / withoutPlating + " of its damage to the plated set");

			// Hymnweave Lining: more maximum Force Energy and faster regeneration.
			wearSet(player, ArmourSet.JEDI_ROBES);
			int energyPlain = gear.maxEnergyBonus(player);
			int bonusPlain = ForceModifiers.maxEnergyBonus(player);
			float regenPlain = gear.regenMultiplier(player);
			wear(player, EquipmentSlot.HEAD, moddedHead(helper, ArmourSet.JEDI_ROBES, LegendaryMods.HYMNWEAVE_LINING));
			int lining = Math.round(c.hymnweaveEnergy);
			helper.assertTrue(gear.maxEnergyBonus(player) - energyPlain == lining && ForceModifiers.maxEnergyBonus(player) - bonusPlain == lining,
					"Hymnweave Lining: hymnweaveEnergy more maximum Force Energy");
			helper.assertTrue(regenPlain > 0f && near(gear.regenMultiplier(player) / regenPlain, ratio(c.hymnweaveRegenPct)),
					"Hymnweave Lining: hymnweaveRegenPct faster regeneration");

			// The Storm Cell: a hit arcs to the nearest other enemy for stormCellPct of the bolt.
			helper.assertTrue(struck.distanceTo(nextTo) <= c.stormCellRange && struck.distanceTo(burn) > c.stormCellRange && struck.distanceTo(wearer) > c.stormCellRange,
					"precondition: only the second dummy stands within the arc's range of the first");
			ItemStack stormGun = Mods.fit(new ItemStack(EnemyItems.BLASTER_PISTOL), ModSlot.CELL, Crafting.withQuality(new ItemStack(LegendaryMods.STORM_CELL), Quality.LEGENDARY));
			helper.assertTrue(ModEffects.stormCell(stormGun), "precondition: the Storm Cell is fitted");
			ItemStack gun = drawn(player, stormGun);
			helper.assertTrue(Blasters.tryFire(player, gun, (BlasterItem) gun.getItem(), level.getGameTime()), "precondition: the pistol fires");
			BlasterBolt shot = onlyBolt(helper, player);
			struck.invulnerableTime = 0;
			nextTo.invulnerableTime = 0;
			float nextBefore = nextTo.getHealth();
			struck.hurtServer(level, boltSource(level, shot, player), shot.damage());
			float arc = shot.damage() * ModEffects.boltDamage(gun, EquipmentM4Balance.mods()) * c.stormCellPct / 100f;
			float lost = nextBefore - nextTo.getHealth();
			helper.assertTrue(Math.abs(lost - arc) < 0.01f, "the Storm Cell's arc deals " + arc + " to the next enemy; it lost " + lost);
			shot.discard();

			ItemStack plainGun = drawn(player, new ItemStack(EnemyItems.BLASTER_PISTOL));
			helper.assertTrue(Blasters.tryFire(player, plainGun, (BlasterItem) plainGun.getItem(), level.getGameTime()), "precondition: the plain pistol fires");
			BlasterBolt plainShot = onlyBolt(helper, player);
			struck.invulnerableTime = 0;
			nextTo.invulnerableTime = 0;
			float plainBefore = nextTo.getHealth();
			struck.hurtServer(level, boltSource(level, plainShot, player), plainShot.damage());
			helper.assertTrue(nextTo.getHealth() == plainBefore, "a pistol without the Storm Cell does not arc");
			plainShot.discard();

			// The Sunstone Edge: more melee damage, and the struck target burns.
			ItemStack plainBlade = new ItemStack(EnemyItems.VIBROBLADE);
			ItemStack edged = Mods.fit(new ItemStack(EnemyItems.VIBROBLADE), ModSlot.EDGE, Crafting.withQuality(new ItemStack(LegendaryMods.SUNSTONE_EDGE), Quality.LEGENDARY));
			helper.assertTrue(near(ModEffects.sunstoneEdge(edged, c), ratio(c.sunstoneEdgePct)) && ModEffects.sunstoneFireTicks(edged, c) == Math.max(0, c.sunstoneEdgeFireTicks)
					&& ModEffects.sunstoneEdge(plainBlade, c) == 1f && ModEffects.sunstoneFireTicks(plainBlade, c) == 0,
					"the Sunstone Edge's numbers: sunstoneEdgePct more damage, sunstoneEdgeFireTicks of fire");
			DamageSource melee = player.damageSources().playerAttack(player);
			hold(player, plainBlade);
			float plainHit = gear.modify(burn, melee, 10f);
			hold(player, edged);
			float edgedHit = gear.modify(burn, melee, 10f);
			helper.assertTrue(plainHit > 0f && near(edgedHit / plainHit, ratio(c.sunstoneEdgePct)), "the Sunstone Edge: a hit deals " + edgedHit / plainHit + " times as much");

			hold(player, plainBlade);
			burn.clearFire();
			burn.invulnerableTime = 0;
			strike(player, burn, VibroWeaponItem.Kind.BLADE);
			helper.assertTrue(!burn.isOnFire(), "a plain vibroblade sets nothing alight");
			hold(player, edged);
			burn.invulnerableTime = 0;
			strike(player, burn, VibroWeaponItem.Kind.BLADE);
			helper.assertTrue(burn.isOnFire() && burn.getRemainingFireTicks() >= c.sunstoneEdgeFireTicks - 1,
					"a Sunstone Edge hit sets its target alight for sunstoneEdgeFireTicks (" + burn.getRemainingFireTicks() + " left)");
		});
	}

	// ---------------------------------------------------------------- the rank 4 pieces and the Cantor's Mask (8.5, 12.3)

	@GameTest(maxTicks = 20)
	public void robesCountAsSetPieces(GameTestHelper helper) {
		/** A rank 4 piece, the chest piece it stands for and the path whose ceremony gives it. */
		record RankFour(ArmourItem robe, ArmourItem below, Path path) {
		}

		List<RankFour> rows = List.of(new RankFour(EquipmentItems.JEDI_MASTER_ROBE, EquipmentItems.JEDI_ROBE, Path.JEDI),
				new RankFour(EquipmentItems.SITH_DARTH_ROBE, EquipmentItems.SITH_ROBE, Path.SITH),
				new RankFour(EquipmentItems.WAYFARER_MANTLE, EquipmentItems.SPACER_JACKET, Path.INDEPENDENT));
		Cleanup cleanup = new Cleanup();

		now(helper, cleanup, () -> {
			ServerPlayer player = listed(helper, cleanup, new BlockPos(1, 1, 1));

			for (RankFour row : rows) {
				ArmourItem robe = row.robe();
				ArmourSet set = row.below().set();
				ItemStack stack = new ItemStack(robe);
				String name = BuiltInRegistries.ITEM.getKey(robe).getPath();
				EquipmentTraits traits = stack.get(ModComponents.EQUIPMENT_TRAITS);
				helper.assertTrue(robe.set() == set && robe.type() == ArmorType.CHESTPLATE && robe.rank() == 4 && !robe.isRankRobe() && traits != null && traits.set() == set,
						name + ": a chest piece of its set (its traits say so), given at rank 4");
				helper.assertTrue(stack.has(M4Components.NAMED) && stack.get(DataComponents.RARITY) == Rarity.EPIC && !Salvage.canSalvage(stack)
						&& Salvage.schematicFor(stack) == null, name + ": named and epic, never crafted or salvaged");
				helper.assertTrue(near(armour(stack), armour(new ItemStack(row.below())) + 1.0), name + ": one more armour than the piece it stands for");
				helper.assertTrue(ArmourSets.rankFourRobe(row.path()).is(robe), "the " + row.path() + " path's rank 4 piece is " + name);

				for (EquipmentSlot slot : ArmourSets.SLOTS) {
					wear(player, slot, slot == EquipmentSlot.CHEST ? new ItemStack(robe) : ArmourSets.piece(set, slot));
				}

				helper.assertTrue(ArmourSets.count(player, set) == 4 && ArmourSets.fullSet(player, set), name + " completes its set");
				wear(player, EquipmentSlot.HEAD, new ItemStack(EquipmentItems.CANTORS_MASK));
				helper.assertTrue(ArmourSets.count(player, set) == 3, "the Cantor's Mask is no piece of any set");
			}

			helper.assertTrue(ArmourSets.rankFourRobe(Path.NONE).isEmpty(), "no path, no rank 4 piece");

			ItemStack mask = new ItemStack(EquipmentItems.CANTORS_MASK);
			Equippable equippable = mask.get(DataComponents.EQUIPPABLE);
			helper.assertTrue(equippable != null && equippable.slot() == EquipmentSlot.HEAD, "the Cantor's Mask is worn on the head");
			helper.assertTrue(armour(mask) == 0.0 && !mask.has(ModComponents.EQUIPMENT_TRAITS) && !mask.isDamageableItem() && mask.has(M4Components.NAMED)
					&& !Salvage.canSalvage(mask) && mask.get(DataComponents.RARITY) == Rarity.EPIC,
					"the Cantor's Mask: cosmetic (no armour, no traits), never damaged, named, epic");
		});
	}

	// ---------------------------------------------------------------- the concussion missile (11.3, 5.3)

	@GameTest(maxTicks = 20)
	public void concussionMissileSchematic(GameTestHelper helper) {
		Identifier id = OldRepublicMod.id("concussion_missile");
		Schematic s = Schematics.get(id);
		helper.assertTrue(s != null, "the concussion missile schematic must load");
		helper.assertTrue(s.discipline().orElse(null) == Discipline.ARMSTECH && s.level() == 3 && !s.quality() && s.learned() == Schematic.Learned.AUTO
				&& !Crafting.isLegendary(s), "an Armstech level 3 schematic, known on reaching the level, with no quality roll");
		helper.assertTrue(s.outputItem() == M5Items.CONCUSSION_MISSILE && s.outputCount() == 4, "it makes four concussion missiles");
		Map<String, Integer> expected = Map.of("oldrepublic:durasteel_plate", 2, "minecraft:gunpowder", 1, "minecraft:redstone", 1);
		helper.assertTrue(inputs(s).equals(expected), "from two durasteel plates, gunpowder and redstone; the file has " + inputs(s));

		CraftingData armstech3 = CraftingData.EMPTY.withProgress(new Progress(Discipline.ARMSTECH, 3, 0, 0));
		CraftingData armstech2 = CraftingData.EMPTY.withProgress(new Progress(Discipline.ARMSTECH, 2, 0, 0));
		CraftingData artifice5 = CraftingData.EMPTY.withProgress(new Progress(Discipline.ARTIFICE, 5, 0, 0));
		helper.assertTrue(Crafting.knows(armstech3, s) && Crafting.craftRefusal(armstech3, s) == null && Crafting.craftable(armstech3).contains(s),
				"Armstech 3 knows it and may craft it");
		helper.assertTrue(!Crafting.knows(armstech2, s) && Crafting.craftRefusal(armstech2, s) != null && !Crafting.craftable(armstech2).contains(s),
				"Armstech 2 does not");
		helper.assertTrue(!Crafting.knows(CraftingData.EMPTY, s) && !Crafting.knows(artifice5, s), "nor anyone without Armstech");

		ItemStack result = s.result();
		ItemStack one = result.copyWithCount(1);
		helper.assertTrue(result.is(M5Items.CONCUSSION_MISSILE) && result.getCount() == 4 && !Salvage.canSalvage(one) && Salvage.returns(one, 1f).isEmpty(),
				"a missile is never salvaged (its schematic makes four)");

		Cleanup cleanup = new Cleanup();
		now(helper, cleanup, () -> {
			ServerPlayer player = listed(helper, cleanup, new BlockPos(1, 1, 1));
			setDiscipline(helper, player, Discipline.ARMSTECH, 3);
			CraftingData before = CraftingAttachments.get(player);
			RandomSource random = RandomSource.create(SEED);

			for (int i = 0; i < 20; i++) {
				helper.assertTrue(Crafting.roll(player, s, random) == Quality.STANDARD, "a schematic without quality always makes Standard");
			}

			helper.assertTrue(CraftingAttachments.get(player).equals(before), "...and moves no pity");
		});
	}
}
