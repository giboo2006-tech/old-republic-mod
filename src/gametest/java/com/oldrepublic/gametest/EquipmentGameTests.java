package com.oldrepublic.gametest;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.ability.EnergyRefunds;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.equipment.ArmourAttributes;
import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.equipment.EquipmentBalance;
import com.oldrepublic.equipment.EquipmentEvents;
import com.oldrepublic.equipment.EquipmentItems;
import com.oldrepublic.equipment.EquipmentMaterials;
import com.oldrepublic.equipment.EquipmentModifiers;
import com.oldrepublic.equipment.EquipmentTraits;
import com.oldrepublic.equipment.SetBonusRules;
import com.oldrepublic.equipment.SetBonuses;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.identity.Path;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.progression.ProgressionBalance;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.ModComponents;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Milestone 3 era armour (spec section 9, test plan 17.1): per-slot modifier ids, per-piece traits
 * and set bonuses, the worked example of 9.1, Iron Discipline on every control kind, Calm and Hunger
 * (bolt parries included), the recipes, the repair and cloth tags, and the traits component codecs.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks}; players are listed mock players
 * ({@link M3TestPlayers}) removed at the end; nothing global is written. Mock players do not run the
 * vanilla equipment tick, so the tests apply worn modifiers the way vanilla does (remove by id, then
 * add) and call the equipment listener's knockback step directly.
 */
public class EquipmentGameTests {
	private static final double EPS = 1.0e-3;
	/** The {@code tomb_hunger} refund ({@code skills.tombHungerFe}, spec 14) that shares a kill with Hunger of the Tomb. */
	private static final float TOMB_HUNGER_FE = 6f;
	/** A body big enough for every ingredient count: the recipes are checked in a 3x3 grid. */
	private static final int GRID = 3;
	private static final TagKey<Item> ENCHANTABLE_ARMOR = vanillaItemTag("enchantable/armor");
	private static final TagKey<Item> ENCHANTABLE_EQUIPPABLE = vanillaItemTag("enchantable/equippable");
	private static final TagKey<Item> ENCHANTABLE_DURABILITY = vanillaItemTag("enchantable/durability");

	private static TagKey<Item> vanillaItemTag(String path) {
		return TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace(path));
	}

	/** A vanilla item by id (26.2 groups the dyed wools into a colour collection; their ids are unchanged). */
	private static Item vanilla(String path) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(path));

		if (item == Items.AIR) {
			throw new IllegalStateException("no vanilla item minecraft:" + path);
		}

		return item;
	}

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < EPS;
	}

	/** Puts {@code stack} on and applies its modifiers as vanilla's equipment tick does (remove by id, then add). */
	private static void wear(LivingEntity entity, ItemStack stack) {
		EquipmentSlot slot = slotOf(stack);
		takeOff(entity, slot);
		entity.setItemSlot(slot, stack);
		stack.forEachModifier(slot, (attribute, modifier) -> {
			AttributeInstance instance = entity.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
				instance.addOrUpdateTransientModifier(modifier);
			}
		});
	}

	/** Takes off what {@code slot} holds and removes its modifiers by id, as vanilla does. */
	private static void takeOff(LivingEntity entity, EquipmentSlot slot) {
		ItemStack old = entity.getItemBySlot(slot);
		old.forEachModifier(slot, (attribute, modifier) -> {
			AttributeInstance instance = entity.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
			}
		});
		entity.setItemSlot(slot, ItemStack.EMPTY);
	}

	private static void strip(LivingEntity entity) {
		for (EquipmentSlot slot : ArmourSets.SLOTS) {
			takeOff(entity, slot);
		}

		EquipmentEvents.updateKnockback(entity, ArmourSets.worn(entity));
	}

	/** Wears the first {@code pieces} pieces of {@code set}, head first, and nothing else. */
	private static void wearSet(LivingEntity entity, ArmourSet set, int pieces) {
		strip(entity);

		for (int i = 0; i < pieces; i++) {
			wear(entity, ArmourSets.piece(set, ArmourSets.SLOTS[i]));
		}

		EquipmentEvents.updateKnockback(entity, ArmourSets.worn(entity));
	}

	private static EquipmentSlot slotOf(ItemStack stack) {
		if (stack.getItem() instanceof com.oldrepublic.equipment.ArmourItem armour) {
			return armour.type().getSlot();
		}

		throw new IllegalArgumentException("not an era armour piece: " + stack);
	}

	/** Sets the player's alignment and path directly (no shift caps, no log). */
	private static void identity(ServerPlayer player, int alignment, Path path) {
		IdentityData data = Identity.get(player);
		data.alignment = alignment;
		data.path = path;
		Identity.commit(player, data);
	}

	private static float energy(ServerPlayer player) {
		return Progression.get(player).energy;
	}

	private static void drain(ServerPlayer player) {
		ForceData data = Progression.get(player);
		data.energy = 0f;
		Progression.commit(player, data);
	}

	private static double pow(double base, int n) {
		return Math.pow(base, n);
	}

	// ---------------------------------------------------------------- attributes

	@GameTest(maxTicks = 20)
	public void perSlotModifierIdsStack(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
			AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
			AttributeInstance toughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
			helper.assertTrue(speed != null && armour != null && toughness != null, msg("a player has speed, armour and toughness"));
			double base = speed.getValue();
			EquipmentBalance defaults = new EquipmentBalance();

			wearSet(player, ArmourSet.REPUBLIC_TROOPER, 4);
			int speedModifiers = 0;

			for (EquipmentSlot slot : ArmourSets.SLOTS) {
				helper.assertTrue(ArmourAttributes.id(slot, "speed").toString().equals("oldrepublic:armour." + slot.getName() + ".speed"),
						msg("the speed id names its slot: " + ArmourAttributes.id(slot, "speed")));
				speedModifiers += speed.hasModifier(ArmourAttributes.id(slot, "speed")) ? 1 : 0;
				helper.assertTrue(armour.hasModifier(ArmourAttributes.id(slot, "armor")), msg("each piece adds its own armour modifier (" + slot.getName() + ")"));
			}

			helper.assertTrue(speedModifiers == 4, msg("4 trooper pieces give 4 speed modifiers, got " + speedModifiers));
			helper.assertTrue(near(speed.getValue(), base * (1 + 4 * defaults.trooperSpeedPct / 100.0)),
					msg("4 pieces at -1.5% each give -6% speed: " + speed.getValue() + " from " + base));
			helper.assertTrue(near(armour.getValue(), 15), msg("Republic trooper armour totals 15, got " + armour.getValue()));
			helper.assertTrue(near(toughness.getValue(), 2.0), msg("toughness 0.5 per piece totals 2, got " + toughness.getValue()));

			takeOff(player, EquipmentSlot.LEGS);
			speedModifiers = 0;

			for (EquipmentSlot slot : ArmourSets.SLOTS) {
				speedModifiers += speed.hasModifier(ArmourAttributes.id(slot, "speed")) ? 1 : 0;
			}

			helper.assertTrue(speedModifiers == 3 && !speed.hasModifier(ArmourAttributes.id(EquipmentSlot.LEGS, "speed")),
					msg("removing the leggings leaves 3 speed modifiers, got " + speedModifiers));
			helper.assertTrue(near(speed.getValue(), base * (1 + 3 * defaults.trooperSpeedPct / 100.0)), msg("3 pieces give -4.5% speed"));
			helper.assertTrue(near(armour.getValue(), 10), msg("without the leggings (5) armour is 10, got " + armour.getValue()));

			wearSet(player, ArmourSet.JEDI_ROBES, 4);
			helper.assertTrue(near(speed.getValue(), base), msg("robes never slow the wearer"));
			helper.assertTrue(near(armour.getValue(), 7) && near(toughness.getValue(), 0), msg("Jedi robes total 7 armour and no toughness"));

			wearSet(player, ArmourSet.SITH_TROOPER, 4);
			AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			helper.assertTrue(knockback != null && near(knockback.getValue(), 4 * 0.05 + defaults.sithSetKnockbackResist),
					msg("Sith trooper armour: 0.05 per piece plus Iron Discipline's 0.2, got " + (knockback == null ? "none" : knockback.getValue())));
		} finally {
			strip(player);
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- traits and bonuses

	@GameTest(maxTicks = 20)
	public void perPieceAndSetTraits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = M3TestPlayers.listed(helper);
		Zombie shooter = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
		EquipmentModifiers modifiers = new EquipmentModifiers();
		EquipmentBalance b = EquipmentBalance.get();

		try {
			BlasterBolt bolt = BlasterBolt.create(level, shooter, shooter.position(), 4f, 60);
			DamageSource blaster = level.damageSources().source(EnemyEntities.BLASTER_DAMAGE, bolt, shooter);
			DamageSource melee = level.damageSources().mobAttack(shooter);
			identity(player, 0, Path.JEDI);

			for (int pieces = 0; pieces <= 4; pieces++) {
				wearSet(player, ArmourSet.REPUBLIC_TROOPER, pieces);
				double blasterExpected = pow(1 + b.republicBlasterPct / 100.0, pieces) * (pieces == 4 ? 1 + b.republicSetBlasterPct / 100.0 : 1);
				helper.assertTrue(near(modifiers.modify(player, blaster, 10f), 10 * blasterExpected),
						msg(pieces + " Republic pieces: blaster damage x" + blasterExpected + ", got " + modifiers.modify(player, blaster, 10f) / 10));
				helper.assertTrue(near(modifiers.modify(player, melee, 10f), 10), msg("Republic armour does not touch melee"));
				helper.assertTrue(near(modifiers.costMultiplier(player, "force_push"), pow(1 + b.trooperCostPct / 100.0, pieces)), msg(pieces + " trooper pieces: cost"));
				helper.assertTrue(near(modifiers.regenMultiplier(player), pow(1 + b.trooperRegenPct / 100.0, pieces)), msg(pieces + " trooper pieces: regen"));
				helper.assertTrue(near(ArmourSets.boltGuardDrainMultiplier(player), pieces == 4 ? 1 + b.republicSetBoltDrainPct / 100.0 : 1),
						msg("Shoulder to Shoulder's guard drain only at 4/4"));
			}

			for (int pieces = 0; pieces <= 4; pieces++) {
				wearSet(player, ArmourSet.SITH_TROOPER, pieces);
				helper.assertTrue(near(modifiers.modify(player, melee, 10f), 10 * pow(1 + b.sithMeleePct / 100.0, pieces)), msg(pieces + " Sith trooper pieces: melee"));
				helper.assertTrue(near(modifiers.modify(player, blaster, 10f), 10), msg("Sith trooper armour does not touch blaster damage"));
				helper.assertTrue(ArmourSets.fullSet(player, ArmourSet.SITH_TROOPER) == (pieces == 4), msg("fullSet only at 4/4"));
			}

			// Two pieces of each trooper set: both count toward cost and regen, neither set is full.
			strip(player);
			wear(player, ArmourSets.piece(ArmourSet.REPUBLIC_TROOPER, EquipmentSlot.HEAD));
			wear(player, ArmourSets.piece(ArmourSet.REPUBLIC_TROOPER, EquipmentSlot.CHEST));
			wear(player, ArmourSets.piece(ArmourSet.SITH_TROOPER, EquipmentSlot.LEGS));
			wear(player, ArmourSets.piece(ArmourSet.SITH_TROOPER, EquipmentSlot.FEET));
			helper.assertTrue(near(modifiers.costMultiplier(player, "force_push"), pow(1 + b.trooperCostPct / 100.0, 4)), msg("mixed trooper pieces all count"));
			helper.assertTrue(!ArmourSets.fullSet(player, ArmourSet.REPUBLIC_TROOPER) && !ArmourSets.fullSet(player, ArmourSet.SITH_TROOPER),
					msg("2 + 2 is no set"));
			helper.assertTrue(near(modifiers.modify(player, blaster, 10f), 10 * pow(1 + b.republicBlasterPct / 100.0, 2)), msg("mixed: 2 Republic pieces resist bolts"));

			// Jedi robes: per piece regen and cost; Calm's Heal discount needs 4/4 and the light gate.
			for (int pieces = 0; pieces <= 4; pieces++) {
				wearSet(player, ArmourSet.JEDI_ROBES, pieces);
				helper.assertTrue(near(modifiers.regenMultiplier(player), pow(1 + b.jediRegenPct / 100.0, pieces)), msg(pieces + " Jedi robe pieces: regen"));
				helper.assertTrue(near(modifiers.costMultiplier(player, "force_heal"), pow(1 + b.jediCostPct / 100.0, pieces)),
						msg(pieces + " Jedi robe pieces at alignment 0: no Calm discount"));
			}

			identity(player, b.setAlignment, Path.JEDI);
			double fullJedi = pow(1 + b.jediCostPct / 100.0, 4);
			helper.assertTrue(near(modifiers.costMultiplier(player, "force_heal"), fullJedi * (1 + b.jediSetHealCost / 100.0)),
					msg("Calm of the Watch at +" + b.setAlignment + ": Force Heal -15%"));
			helper.assertTrue(near(modifiers.costMultiplier(player, "force_push"), fullJedi), msg("Calm discounts Heal only"));
			helper.assertTrue(near(ForceModifiers.cost(player, "force_heal"), modifiers.costMultiplier(player, "force_heal")),
					msg("the armour modifier is registered with ForceModifiers"));

			// The Knight's robe counts as the robe of the set.
			wear(player, new ItemStack(EquipmentItems.JEDI_KNIGHT_ROBE));
			helper.assertTrue(ArmourSets.count(player, ArmourSet.JEDI_ROBES) == 4 && ArmourSets.fullSet(player, ArmourSet.JEDI_ROBES),
					msg("jedi_knight_robe counts as the Jedi robe"));
			helper.assertTrue(ArmourSets.rankRobe(ArmourSet.JEDI_ROBES).is(EquipmentItems.JEDI_KNIGHT_ROBE)
					&& ArmourSets.rankRobe(ArmourSet.SITH_ROBES).is(EquipmentItems.SITH_LORD_ROBE)
					&& ArmourSets.rankRobe(ArmourSet.REPUBLIC_TROOPER).isEmpty(), msg("rankRobe names the two rank 3 robes"));

			// A Balanced Independent gets Calm, on Lift instead of Heal.
			identity(player, 0, Path.INDEPENDENT);
			helper.assertTrue(near(modifiers.costMultiplier(player, "force_lift"), fullJedi * (1 + b.jediSetHealCost / 100.0))
					&& near(modifiers.costMultiplier(player, "force_heal"), fullJedi), msg("an Independent's Calm discounts Lift, not Heal"));

			// Sith robes: max energy per piece; Hunger's Lightning discount needs 4/4 and the dark gate.
			identity(player, -b.setAlignment, Path.SITH);

			for (int pieces = 0; pieces <= 4; pieces++) {
				wearSet(player, ArmourSet.SITH_ROBES, pieces);
				helper.assertTrue(modifiers.maxEnergyBonus(player) == pieces * b.sithMaxEnergy, msg(pieces + " Sith robe pieces: +" + pieces * b.sithMaxEnergy + " max FE"));
				double lightning = pow(1 + b.sithCostPct / 100.0, pieces) * (pieces == 4 ? 1 + b.sithSetLightningCost / 100.0 : 1);
				helper.assertTrue(near(modifiers.costMultiplier(player, "force_lightning"), lightning), msg(pieces + " Sith robe pieces: Lightning cost"));
			}

			wear(player, new ItemStack(EquipmentItems.SITH_LORD_ROBE));
			helper.assertTrue(ArmourSets.fullSet(player, ArmourSet.SITH_ROBES), msg("sith_lord_robe counts as the Sith robe"));
			identity(player, 0, Path.SITH);
			helper.assertTrue(near(modifiers.costMultiplier(player, "force_lightning"), pow(1 + b.sithCostPct / 100.0, 4)),
					msg("Hunger needs alignment -" + b.setAlignment + " or lower"));
			helper.assertTrue(ArmourSets.disguise(player) == com.oldrepublic.combat.Allegiance.NONE, msg("the uniform disguise is cut: NONE"));
		} finally {
			strip(player);
			M3TestPlayers.remove(player);
			shooter.discard();
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void workedExample9_1(GameTestHelper helper) {
		EquipmentBalance b = new EquipmentBalance();
		SetBonusRules.Wearer nobody = SetBonusRules.Wearer.NOBODY;
		SetBonusRules.Worn trooper = new SetBonusRules.Worn(4, 0, 0, 0);
		SetBonusRules.Worn jedi = new SetBonusRules.Worn(0, 0, 4, 0);

		float trooperCost = SetBonusRules.costMultiplier(trooper, nobody, "force_push", b);
		float trooperRegen = SetBonusRules.regenMultiplier(trooper, b);
		helper.assertTrue(Math.abs(trooperCost - 1.216f) < 0.001f, msg("a full trooper set costs x1.216, got " + trooperCost));
		helper.assertTrue(Math.abs(trooperRegen - 0.815f) < 0.001f, msg("a full trooper set regenerates x0.815, got " + trooperRegen));

		double speed = 0;

		for (EquipmentSlot slot : ArmourSets.SLOTS) {
			ItemStack piece = ArmourSets.piece(ArmourSet.SITH_TROOPER, slot);
			double[] sum = {0};
			piece.forEachModifier(slot, (attribute, modifier) -> {
				if (modifier.id().equals(ArmourAttributes.id(slot, "speed"))) {
					sum[0] += modifier.amount();
				}
			});
			speed += sum[0];
		}

		helper.assertTrue(near(speed, -0.06), msg("a full trooper set gives -6% speed, got " + speed));

		float jediRegen = SetBonusRules.regenMultiplier(jedi, b);
		float jediCost = SetBonusRules.costMultiplier(jedi, nobody, "force_push", b);
		helper.assertTrue(Math.abs(jediRegen - 1.262f) < 0.001f, msg("full Jedi robes regenerate x1.262, got " + jediRegen));
		helper.assertTrue(Math.abs(jediCost - 0.922f) < 0.001f, msg("full Jedi robes cost x0.922, got " + jediCost));

		// The clamps belong to ForceModifiers (0.70-1.50 cost, 0.50-1.50 regen); every set stays inside them alone.
		ProgressionBalance progression = new ProgressionBalance();
		helper.assertTrue(trooperCost <= progression.modifierCostMax && jediCost >= progression.modifierCostMin
				&& trooperRegen >= progression.modifierRegenMin && jediRegen <= progression.modifierRegenMax, msg("each set alone is inside the clamps"));
		helper.assertTrue(EnergyRefunds.fit(0f, b.sithSetKillFe, progression.killRefundCap) == b.sithSetKillFe
				&& EnergyRefunds.fit(0f, b.jediSetParryFe, progression.parryRefundCap) == b.jediSetParryFe,
				msg("each robe bonus alone fits under its refund cap"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void ironDisciplineShortensEveryKind(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper);
		EquipmentBalance b = EquipmentBalance.get();
		int base = 100;
		int shortened = Math.round(base * (1 + b.sithSetControlPct / 100f));
		List<String> kinds = List.of(ControlEffects.STAGGER, ControlEffects.SLOW, ControlEffects.DISARM, ControlEffects.SHOCK, ControlEffects.HOLD);

		try {
			wearSet(player, ArmourSet.SITH_TROOPER, 3);

			for (String kind : kinds) {
				helper.assertTrue(ControlEffects.duration(player, base, kind) == base, msg("3/4 Sith trooper armour leaves " + kind + " at " + base));
			}

			AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			helper.assertTrue(knockback != null && !knockback.hasModifier(ArmourAttributes.SET_KNOCKBACK), msg("no set knockback modifier below 4/4"));

			wearSet(player, ArmourSet.SITH_TROOPER, 4);

			for (String kind : kinds) {
				int got = ControlEffects.duration(player, base, kind);
				helper.assertTrue(got == shortened, msg("Iron Discipline shortens " + kind + " to " + shortened + ", got " + got));
			}

			helper.assertTrue(knockback.hasModifier(ArmourAttributes.SET_KNOCKBACK), msg("4/4 adds the transient knockback modifier"));

			takeOff(player, EquipmentSlot.HEAD);
			EquipmentEvents.updateKnockback(player, ArmourSets.worn(player));
			helper.assertTrue(!knockback.hasModifier(ArmourAttributes.SET_KNOCKBACK) && ControlEffects.duration(player, base, ControlEffects.HOLD) == base,
					msg("taking a piece off ends Iron Discipline at once"));

			wearSet(player, ArmourSet.REPUBLIC_TROOPER, 4);
			helper.assertTrue(ControlEffects.duration(player, base, ControlEffects.STAGGER) == base, msg("Shoulder to Shoulder does not shorten controls"));
		} finally {
			strip(player);
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	/**
	 * Calm and Hunger pay through the shared refund ledger: the parries happen on separate ticks
	 * (one parry is one moment), and a second refund for the same parry or kill (as
	 * {@code watchers_insight} or {@code tomb_hunger} pays) stops at the progression cap.
	 */
	@GameTest(maxTicks = 20)
	public void calmAndHungerIncludingBoltParries(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = M3TestPlayers.listed(helper);
		Zombie shooter = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 4));
		EquipmentBalance b = EquipmentBalance.get();
		ProgressionBalance caps = Progression.balance();
		float calm = EnergyRefunds.fit(0f, b.jediSetParryFe, caps.parryRefundCap);
		float hunger = EnergyRefunds.fit(0f, b.sithSetKillFe, caps.killRefundCap);
		Runnable cleanUp = () -> {
			strip(player);
			EnergyRefunds.forget(player);
			M3TestPlayers.remove(player);
			shooter.discard();
		};

		// Tick 0. Calm of the Watch: a bolt parry restores 5 FE (under the 6 FE per-parry cap).
		stage(cleanUp, () -> {
			wearSet(player, ArmourSet.JEDI_ROBES, 4);
			identity(player, b.setAlignment, Path.JEDI);
			drain(player);
			QuestHooks.onBoltDeflected(player, true, shooter);
			helper.assertTrue(near(energy(player), calm), msg("a bolt parry restores " + calm + " FE, got " + energy(player)));
		});

		// Tick 1: a melee parry restores 5 FE too; another refund for that same parry stops at the cap.
		helper.runAfterDelay(1, () -> stage(cleanUp, () -> {
			QuestHooks.onSaberTechnique(player, "parry", shooter, true);
			helper.assertTrue(near(energy(player), 2 * calm), msg("a melee parry restores " + calm + " FE too, got " + energy(player)));

			float other = EnergyRefunds.forParry(player, level.getGameTime(), caps.parryRefundCap);
			helper.assertTrue(near(other, caps.parryRefundCap - calm),
					msg("Calm and a second refund for one parry give at most " + caps.parryRefundCap + " FE together, the second got " + other));

			drain(player);
			QuestHooks.onBoltDeflected(player, false, shooter);
			helper.assertTrue(near(energy(player), 0), msg("a deflection that is not a parry restores nothing"));

			identity(player, 0, Path.JEDI);
			helper.assertTrue(SetBonuses.calm(player) == 0f, msg("below +" + b.setAlignment + " Calm is off"));
		}));

		// Tick 2: the Balanced Independent, then Hunger of the Tomb (one moment per victim).
		helper.runAfterDelay(2, () -> {
			try {
				identity(player, 0, Path.INDEPENDENT);
				helper.assertTrue(near(SetBonuses.calm(player), calm), msg("a Balanced Independent has Calm"));

				// A kill soon after the killer's power hit restores 8 FE (under the 12 FE per-kill cap).
				wearSet(player, ArmourSet.SITH_ROBES, 4);
				identity(player, -b.setAlignment, Path.SITH);
				drain(player);
				Zombie victim = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 6));
				QuestHooks.onAbilityHit(player, "force_push", victim);
				victim.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
				helper.assertTrue(!victim.isAlive(), msg("the victim dies"));
				helper.assertTrue(near(energy(player), hunger), msg("a kill after a power hit restores " + hunger + " FE, got " + energy(player)));

				// tomb_hunger paid 6 FE for this kill first: Hunger adds only what the 12 FE cap leaves.
				drain(player);
				Zombie shared = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
				QuestHooks.onAbilityHit(player, "force_push", shared);
				float tomb = EnergyRefunds.forKill(player, shared, TOMB_HUNGER_FE);
				shared.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
				float total = Math.min(caps.killRefundCap, tomb + hunger);
				helper.assertTrue(near(tomb, TOMB_HUNGER_FE) && near(energy(player), total),
						msg("tomb_hunger and Hunger give at most " + caps.killRefundCap + " FE for one kill, got " + energy(player)));

				drain(player);
				Zombie unmarked = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(6, 1, 2));
				unmarked.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
				helper.assertTrue(near(energy(player), 0), msg("a kill without a power hit restores nothing"));

				helper.assertTrue(SetBonusRules.hungerWindow(100, 100 + b.sithSetWindowTicks, b) && !SetBonusRules.hungerWindow(100, 101 + b.sithSetWindowTicks, b),
						msg("the window is " + b.sithSetWindowTicks + " ticks, inclusive"));

				identity(player, 0, Path.SITH);
				drain(player);
				Zombie gated = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 6));
				QuestHooks.onAbilityHit(player, "force_push", gated);
				gated.hurtServer(level, level.damageSources().playerAttack(player), 1000f);
				helper.assertTrue(near(energy(player), 0), msg("above -" + b.setAlignment + " Hunger is off"));
			} finally {
				cleanUp.run();
			}

			helper.succeed();
		});
	}

	/** Runs one step of a multi-tick test; on a failure cleans up before the failure ends the test. */
	private static void stage(Runnable cleanUp, Runnable step) {
		try {
			step.run();
		} catch (RuntimeException | Error e) {
			cleanUp.run();
			throw e;
		}
	}

	// ---------------------------------------------------------------- data

	@GameTest(maxTicks = 20)
	public void recipesFitShapesAndCostCommendations(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Item durasteel = M3Items.DURASTEEL_PLATE;
		List<String> problems = new ArrayList<>();

		for (ArmourSet set : ArmourSet.values()) {
			if (set.isM4()) {
				// The Milestone 4 sets are made at the Engineering Bench (W5's schematics), not by recipes.
				continue;
			}

			boolean trooper = set == ArmourSet.REPUBLIC_TROOPER || set == ArmourSet.SITH_TROOPER;
			Item commendation = set == ArmourSet.REPUBLIC_TROOPER ? M3Items.REPUBLIC_COMMENDATION : M3Items.IMPERIAL_COMMENDATION;
			Item otherCommendation = set == ArmourSet.REPUBLIC_TROOPER ? M3Items.IMPERIAL_COMMENDATION : M3Items.REPUBLIC_COMMENDATION;
			Item thread = set == ArmourSet.JEDI_ROBES ? M3Items.LUMEN_THREAD : M3Items.GRAVE_SILK;
			Item cloth = vanilla(set == ArmourSet.JEDI_ROBES ? "white_wool" : "black_wool");

			for (EquipmentSlot slot : ArmourSets.SLOTS) {
				ItemStack piece = ArmourSets.piece(set, slot);
				Identifier id = BuiltInRegistries.ITEM.getKey(piece.getItem());
				JsonObject json = readRecipe(level, id);

				if (json == null) {
					problems.add(id + ": no recipe file");
					continue;
				}

				String[] shape = vanillaShape(slot);
				List<String> pattern = new ArrayList<>();

				for (JsonElement row : json.getAsJsonArray("pattern")) {
					pattern.add(row.getAsString());
				}

				// Every filled cell of the vanilla armour shape is an ingredient, and nothing else is.
				for (int r = 0; r < shape.length; r++) {
					for (int c = 0; c < GRID; c++) {
						boolean wanted = shape[r].charAt(c) == 'X';
						boolean filled = r < pattern.size() && c < pattern.get(r).length() && pattern.get(r).charAt(c) != ' ';

						if (wanted != filled) {
							problems.add(id + ": cell " + r + "," + c + " does not follow the vanilla shape");
						}
					}
				}

				if (pattern.size() != shape.length) {
					problems.add(id + ": " + pattern.size() + " rows, the vanilla shape has " + shape.length);
				}

				String result = json.getAsJsonObject("result").get("id").getAsString();

				if (!result.equals(id.toString())) {
					problems.add(id + ": result is " + result);
				}

				// The loaded recipe accepts exactly the costed grid; the wrong army's commendation fails.
				Optional<RecipeHolder<?>> holder = level.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, id));

				if (holder.isEmpty()) {
					problems.add(id + ": not loaded");
					continue;
				}

				@SuppressWarnings("unchecked")
				Recipe<CraftingInput> recipe = (Recipe<CraftingInput>) holder.get().value();
				List<ItemStack> grid = trooper ? trooperGrid(slot, durasteel, commendation) : robeGrid(slot, cloth, thread);

				if (!recipe.matches(CraftingInput.of(GRID, shape.length, grid), level)) {
					problems.add(id + ": does not match its costed grid " + grid);
				}

				if (trooper) {
					List<ItemStack> wrongArmy = trooperGrid(slot, durasteel, otherCommendation);
					List<ItemStack> noCommendation = trooperGrid(slot, durasteel, durasteel);

					if (recipe.matches(CraftingInput.of(GRID, shape.length, wrongArmy), level)
							|| recipe.matches(CraftingInput.of(GRID, shape.length, noCommendation), level)) {
						problems.add(id + ": crafts without its own army's commendation");
					}

					long commendations = grid.stream().filter(stack -> stack.is(commendation)).count();
					long plates = grid.stream().filter(stack -> stack.is(durasteel)).count();
					int[] wantedPlates = {4, 7, 6, 3};

					if (commendations != 1 || plates != wantedPlates[slotIndex(slot)]) {
						problems.add(id + ": costs " + plates + " durasteel and " + commendations + " commendations");
					}
				}
			}
		}

		for (Item rank : List.of(EquipmentItems.JEDI_KNIGHT_ROBE, EquipmentItems.SITH_LORD_ROBE)) {
			Identifier id = BuiltInRegistries.ITEM.getKey(rank);

			if (level.recipeAccess().byKey(ResourceKey.create(Registries.RECIPE, id)).isPresent()) {
				problems.add(id + ": the rank 3 robes are never crafted");
			}
		}

		helper.assertTrue(problems.isEmpty(), msg("recipe problems: " + problems));
		helper.succeed();
	}

	/** The vanilla armour shapes (iron armour): helmet, chestplate, leggings, boots. */
	private static String[] vanillaShape(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> new String[] {"XXX", "X X"};
			case CHEST -> new String[] {"X X", "XXX", "XXX"};
			case LEGS -> new String[] {"XXX", "X X", "X X"};
			default -> new String[] {"X X", "X X"};
		};
	}

	private static int slotIndex(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> 0;
			case CHEST -> 1;
			case LEGS -> 2;
			default -> 3;
		};
	}

	/** The costed trooper grid (9.3): the commendation in the top middle, the centre or one boot cell, durasteel elsewhere. */
	private static List<ItemStack> trooperGrid(EquipmentSlot slot, Item plate, Item commendation) {
		String[] shape = vanillaShape(slot);
		int commendationCell = switch (slot) {
			case HEAD, LEGS -> 1;
			case CHEST -> 4;
			default -> 0;
		};
		List<ItemStack> grid = new ArrayList<>();

		for (int i = 0; i < shape.length * GRID; i++) {
			char cell = shape[i / GRID].charAt(i % GRID);
			grid.add(cell != 'X' ? ItemStack.EMPTY : new ItemStack(i == commendationCell ? commendation : plate));
		}

		return grid;
	}

	/** The costed robe grid (9.3): thread where the recipe puts it, leather in the boots' lower cells, cloth elsewhere. */
	private static List<ItemStack> robeGrid(EquipmentSlot slot, Item cloth, Item thread) {
		String[] layout = switch (slot) {
			case HEAD -> new String[] {"WTW", "W W"};
			case CHEST -> new String[] {"W W", "TWT", "WWW"};
			case LEGS -> new String[] {"WTW", "W W", "W W"};
			default -> new String[] {"W T", "L L"};
		};
		List<ItemStack> grid = new ArrayList<>();

		for (String row : layout) {
			for (char cell : row.toCharArray()) {
				grid.add(switch (cell) {
					case 'W' -> new ItemStack(cloth);
					case 'T' -> new ItemStack(thread);
					case 'L' -> new ItemStack(Items.LEATHER);
					default -> ItemStack.EMPTY;
				});
			}
		}

		return grid;
	}

	private static JsonObject readRecipe(ServerLevel level, Identifier id) {
		Identifier file = Identifier.fromNamespaceAndPath(id.getNamespace(), "recipe/" + id.getPath() + ".json");
		Optional<Resource> resource = level.getServer().getResourceManager().getResource(file);

		if (resource.isEmpty()) {
			return null;
		}

		try (Reader reader = resource.get().openAsReader()) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	@GameTest(maxTicks = 20)
	public void repairTagsResolve(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		ItemStack plate = new ItemStack(M3Items.DURASTEEL_PLATE);
		ItemStack lumen = new ItemStack(M3Items.LUMEN_THREAD);
		ItemStack silk = new ItemStack(M3Items.GRAVE_SILK);

		for (com.oldrepublic.equipment.ArmourItem item : EquipmentItems.ALL) {
			ItemStack stack = new ItemStack(item);
			ItemStack repair = switch (item.set()) {
				case REPUBLIC_TROOPER, SITH_TROOPER -> plate;
				case JEDI_ROBES -> lumen;
				case SITH_ROBES -> silk;
				default -> throw new IllegalStateException("not a Milestone 3 set: " + item.set());
			};

			if (!stack.isValidRepairItem(repair)) {
				problems.add(item + " is not repaired by " + repair);
			}

			for (ItemStack other : List.of(plate, lumen, silk)) {
				if (other != repair && stack.isValidRepairItem(other)) {
					problems.add(item + " is also repaired by " + other);
				}
			}

			if (!stack.is(ENCHANTABLE_ARMOR) || !stack.is(ENCHANTABLE_EQUIPPABLE) || !stack.is(ENCHANTABLE_DURABILITY)) {
				problems.add(item + " is missing from enchantable/armor, equippable or durability");
			}
		}

		if (!plate.is(EquipmentMaterials.REPAIRS_TROOPER) || !lumen.is(EquipmentMaterials.REPAIRS_JEDI) || !silk.is(EquipmentMaterials.REPAIRS_SITH)) {
			problems.add("a repair tag does not hold its material");
		}

		for (Item wool : List.of(vanilla("white_wool"), vanilla("light_gray_wool"), vanilla("brown_wool"))) {
			if (!new ItemStack(wool).is(EquipmentMaterials.JEDI_ROBE_CLOTH) || new ItemStack(wool).is(EquipmentMaterials.SITH_ROBE_CLOTH)) {
				problems.add(wool + " should be Jedi robe cloth only");
			}
		}

		for (Item wool : List.of(vanilla("black_wool"), vanilla("gray_wool"), vanilla("red_wool"))) {
			if (!new ItemStack(wool).is(EquipmentMaterials.SITH_ROBE_CLOTH) || new ItemStack(wool).is(EquipmentMaterials.JEDI_ROBE_CLOTH)) {
				problems.add(wool + " should be Sith robe cloth only");
			}
		}

		helper.assertTrue(problems.isEmpty(), msg("repair and cloth problems: " + problems));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void equipmentTraitsCodecRoundTrip(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();

		for (com.oldrepublic.equipment.ArmourItem item : EquipmentItems.ALL) {
			EquipmentTraits traits = new ItemStack(item).get(ModComponents.EQUIPMENT_TRAITS);

			if (traits == null || !traits.equals(EquipmentItems.defaultTraits(item.set())) || traits.set() != item.set()) {
				problems.add(item + " carries " + traits);
			}
		}

		helper.assertTrue(problems.isEmpty(), msg("every piece carries its set's default traits: " + problems));

		EquipmentTraits odd = new EquipmentTraits(ArmourSet.SITH_ROBES, -0.02f, 0.125f, 5, 0.06f, 0.04f);
		JsonElement json = EquipmentTraits.CODEC.encodeStart(JsonOps.INSTANCE, odd).getOrThrow();
		helper.assertTrue(json.getAsJsonObject().get("set").getAsString().equals("sith_robes"), msg("the set is saved by its id: " + json));
		helper.assertTrue(EquipmentTraits.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().equals(odd), msg("the traits survive the codec: " + json));

		JsonObject minimal = new JsonObject();
		minimal.addProperty("set", "jedi_robes");
		helper.assertTrue(EquipmentTraits.CODEC.parse(JsonOps.INSTANCE, minimal).getOrThrow().equals(new EquipmentTraits(ArmourSet.JEDI_ROBES, 0f, 0f, 0, 0f, 0f)),
				msg("missing numbers default to 0"));
		minimal.addProperty("set", "beskar");
		helper.assertTrue(EquipmentTraits.CODEC.parse(JsonOps.INSTANCE, minimal).isError(), msg("an unknown set is an error"));

		ByteBuf buf = Unpooled.buffer();

		try {
			EquipmentTraits.STREAM_CODEC.encode(buf, odd);
			helper.assertTrue(EquipmentTraits.STREAM_CODEC.decode(buf).equals(odd), msg("the traits survive the stream codec"));
		} finally {
			buf.release();
		}

		ItemStack stack = new ItemStack(EquipmentItems.SITH_LORD_ROBE);
		stack.set(ModComponents.EQUIPMENT_TRAITS, odd);
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag saved = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
		ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(loaded.is(EquipmentItems.SITH_LORD_ROBE) && odd.equals(loaded.get(ModComponents.EQUIPMENT_TRAITS)),
				msg("a saved stack keeps its traits: " + saved));
		helper.succeed();
	}
}
