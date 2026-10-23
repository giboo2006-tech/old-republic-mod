package com.oldrepublic.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.combat.WeaponActions;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.enemy.SithAcolyte;
import com.oldrepublic.enemy.droid.DroidEntities;
import com.oldrepublic.equipment.GearItems;
import com.oldrepublic.equipment.melee.MeleeBalance;
import com.oldrepublic.equipment.melee.MeleeCombat;
import com.oldrepublic.equipment.melee.MeleeRules;
import com.oldrepublic.equipment.melee.VibroWeaponItem;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.mixin.equipment.LivingEntityAttackStrengthAccessor;
import com.oldrepublic.registry.M7Items;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.duel.DuelState;
import com.oldrepublic.saber.duel.GuardState;
import com.oldrepublic.saber.duel.NpcGuard;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 7's melee weapons (docs/MILESTONE_7.md 9.1, 9.2 and 14, WM): the vibro-axe's and the force
 * pike's registered numbers and guards; Sunder against a vibro guard, a player's saber guard and a
 * saber NPC's guard; the stun thrust (the charge through the Weapon Action, the stun, the push, a droid
 * struck harder instead); the Starfall Edge (damage to droids and constructs, longer life) and the
 * Clan Iron Edge (a saber held without a notch, less guard drain).
 *
 * <p>Test rules (MILESTONE_4.md 15.1): every test declares {@code maxTicks} (at most 400); players are
 * listed mock players ({@link M3TestPlayers}) removed at the end, which also drops their melee state
 * through the leave event; the guarding saber player is a fake player whose guard is forgotten at the
 * end; nothing global is written. The numbers come from the live balance sections, so a tuned file
 * still passes; the registered item numbers are checked against the registration defaults. The pure
 * rules are driven with explicit ticks; the in-world checks call the hooks the inputs use
 * ({@code MeleeCombat.swing}, {@code WeaponActions.dispatch}, {@code Player#attack}) or the pieces of
 * the damage pipeline in order ({@code SaberCombat.modifyIncomingDamage}, then {@code MeleeCombat.onHit}
 * as the item's {@code hurtEnemy} calls it). Mock players do not run the vanilla equipment tick, so
 * held modifiers are applied the way vanilla does (remove by id, then add).
 */
public class MeleeM7GameTests {
	private static final double EPS = 1.0e-3;
	/** A full attack ticker: far past any weapon's cooldown. */
	private static final int FULL_TICKER = 100;
	/** A tick far from zero for the pure rules, so no subtraction goes negative. */
	private static final long T0 = 10_000L;
	/** The player's spot; targets stand south of it (+z), the way it faces. */
	private static final BlockPos PLAYER_AT = new BlockPos(2, 1, 1);
	/** A second player two blocks south, facing the first. */
	private static final BlockPos FOE_AT = new BlockPos(2, 1, 3);
	/** The guarding saber player's spot, facing +z; its attacker stands south of it at {@link #ATTACKER_AT}. */
	private static final BlockPos GUARD_AT = new BlockPos(4, 1, 4);
	private static final BlockPos ATTACKER_AT = new BlockPos(4, 1, 6);
	/** Ticks the fake saber player has been guarding: far past any parry window. */
	private static final int LONG_GUARD = 200;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < EPS;
	}

	// ---------------------------------------------------------------- fixtures

	/** A listed player at {@link #PLAYER_AT}, on the ground, facing south (+z) and level. */
	private static ServerPlayer player(GameTestHelper helper) {
		return listed(helper, PLAYER_AT, 0f);
	}

	/** A listed player at {@link #FOE_AT}, facing north (-z), toward {@link #player}. */
	private static ServerPlayer foe(GameTestHelper helper) {
		return listed(helper, FOE_AT, 180f);
	}

	private static ServerPlayer listed(GameTestHelper helper, BlockPos at, float yaw) {
		ServerPlayer player = M3TestPlayers.listed(helper, at);
		player.setGameMode(GameType.SURVIVAL);
		player.setYRot(yaw);
		player.setXRot(0f);
		player.setYHeadRot(yaw);
		player.setOnGround(true);
		player.xo = player.getX();
		player.zo = player.getZ();
		return player;
	}

	/** Puts {@code stack} in the main hand with its modifiers applied as vanilla's equipment tick does; returns the held stack. */
	private static ItemStack hold(ServerPlayer player, ItemStack stack) {
		player.getMainHandItem().forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			AttributeInstance instance = player.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
			}
		});
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			AttributeInstance instance = player.getAttribute(attribute);

			if (instance != null) {
				instance.removeModifier(modifier.id());
				instance.addOrUpdateTransientModifier(modifier);
			}
		});
		return player.getMainHandItem();
	}

	/** {@code gear} with one {@code mod} fitted in its edge slot. */
	private static ItemStack edged(ItemStack gear, ItemStack mod) {
		return Mods.fit(gear, ModSlot.EDGE, mod);
	}

	/** One player attack on {@code target} with a full attack charge, as the attack packet does (the callback's swing first). */
	private static void strike(ServerPlayer player, LivingEntity target, VibroWeaponItem.Kind kind) {
		((LivingEntityAttackStrengthAccessor) player).oldrepublic$setAttackStrengthTicker(FULL_TICKER);
		MeleeCombat.swing(player, kind, target.getId(), 1f, player.level().getGameTime());
		player.attack(target);
	}

	/** The total of {@code attribute}'s ADD_VALUE main-hand modifiers on {@code stack}. */
	private static double modifier(ItemStack stack, Holder<Attribute> attribute) {
		ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		double total = 0.0;

		for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
			if (entry.attribute().value() == attribute.value()) {
				total += entry.modifier().amount();
			}
		}

		return total;
	}

	/** The amplifier of {@code entity}'s Slowness, or -1 without it. */
	private static int slowness(LivingEntity entity) {
		MobEffectInstance slow = entity.getEffect(MobEffects.SLOWNESS);
		return slow == null ? -1 : slow.getAmplifier();
	}

	/** A fake server player whose time in the guard is pinned, so the parry window can be stepped over. */
	private static final class GuardingPlayer extends FakePlayer {
		private final int ticksUsing;

		GuardingPlayer(ServerLevel level, int ticksUsing) {
			super(level, new GameProfile(UUID.randomUUID(), "sunder_guard_test"));
			this.ticksUsing = ticksUsing;
		}

		@Override
		public int getTicksUsingItem() {
			return isUsingItem() ? ticksUsing : 0;
		}
	}

	/** A fake player at {@link #GUARD_AT} facing +z, guarding with an ignited Guardian/Wrapped/Weighted saber past its parry window. */
	private static GuardingPlayer guardingSaber(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GuardingPlayer player = new GuardingPlayer(level, LONG_GUARD);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(GUARD_AT)));
		player.setYRot(0f);
		player.setXRot(0f);
		ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(player.getUUID(), "Tester", BladeColor.BLUE.rgb()), BladeColor.BLUE,
				Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
		SaberToggle.setIgnited(saber, true, level.getGameTime());
		player.setItemInHand(InteractionHand.MAIN_HAND, saber);
		player.startUsingItem(InteractionHand.MAIN_HAND);
		helper.assertTrue(player.isUsingItem() && !SaberGuard.guardingSaber(player).isEmpty(), msg("the fake player must be guarding"));
		return player;
	}

	/** A Sith Acolyte at {@code pos} (relative) facing -z, its AI off so the guard state stays as set. */
	private static SithAcolyte acolyte(GameTestHelper helper, BlockPos pos) {
		SithAcolyte acolyte = helper.spawn(EnemyEntities.SITH_ACOLYTE, pos);
		acolyte.setNoAi(true);
		acolyte.setYRot(180f);
		acolyte.setYBodyRot(180f);
		acolyte.setYHeadRot(180f);
		return acolyte;
	}

	/** An ignited lightsaber for a mob's main hand. */
	private static ItemStack litSaber(ServerLevel level) {
		ItemStack saber = new ItemStack(SaberItems.LIGHTSABER);
		SaberToggle.setIgnited(saber, true, level.getGameTime());
		return saber;
	}

	// ---------------------------------------------------------------- the new kinds (9.1)

	@GameTest(maxTicks = 20)
	public void axeAndPikeNumbers(GameTestHelper helper) {
		MeleeBalance registered = new MeleeBalance();
		MeleeBalance b = MeleeBalance.get();
		helper.assertTrue(M7Items.VIBRO_AXE.kind() == VibroWeaponItem.Kind.AXE && M7Items.FORCE_PIKE.kind() == VibroWeaponItem.Kind.PIKE,
				msg("the vibro-axe and the force pike are the AXE and PIKE kinds"));

		ItemStack axe = new ItemStack(M7Items.VIBRO_AXE);
		ItemStack pike = new ItemStack(M7Items.FORCE_PIKE);
		helper.assertTrue(near(1.0 + modifier(axe, Attributes.ATTACK_DAMAGE), registered.axeDamage)
				&& near(4.0 + modifier(axe, Attributes.ATTACK_SPEED), registered.axeSpeed)
				&& near(modifier(axe, Attributes.ENTITY_INTERACTION_RANGE), registered.axeReach),
				msg("the axe hits for " + registered.axeDamage + " at " + registered.axeSpeed + "/s with +" + registered.axeReach + " reach, got "
						+ (1.0 + modifier(axe, Attributes.ATTACK_DAMAGE)) + ", " + (4.0 + modifier(axe, Attributes.ATTACK_SPEED)) + ", "
						+ modifier(axe, Attributes.ENTITY_INTERACTION_RANGE)));
		helper.assertTrue(near(1.0 + modifier(pike, Attributes.ATTACK_DAMAGE), registered.pikeDamage)
				&& near(4.0 + modifier(pike, Attributes.ATTACK_SPEED), registered.pikeSpeed)
				&& near(modifier(pike, Attributes.ENTITY_INTERACTION_RANGE), registered.pikeReach),
				msg("the pike hits for " + registered.pikeDamage + " at " + registered.pikeSpeed + "/s with +" + registered.pikeReach + " reach"));
		helper.assertTrue(axe.getMaxDamage() == registered.axeDurability && pike.getMaxDamage() == registered.pikeDurability,
				msg("durability " + registered.axeDurability + " and " + registered.pikeDurability + ", got " + axe.getMaxDamage() + " and " + pike.getMaxDamage()));
		helper.assertTrue(axe.has(DataComponents.WEAPON) && pike.has(DataComponents.WEAPON) && axe.has(DataComponents.SWING_ANIMATION)
				&& pike.has(DataComponents.SWING_ANIMATION), msg("both wear one durability a hit and swing their own speed"));
		helper.assertTrue(near(MeleeRules.guardThrough(VibroWeaponItem.Kind.AXE, b), 1.0 - b.guardAxePct / 100.0)
				&& near(MeleeRules.guardThrough(VibroWeaponItem.Kind.PIKE, b), 1.0 - b.guardPikePct / 100.0),
				msg("the axe's guard blocks " + b.guardAxePct + "%, the pike's " + b.guardPikePct + "%"));
		helper.assertTrue(Mods.slotsOf(axe).contains(ModSlot.EDGE) && Mods.slotsOf(pike).contains(ModSlot.EDGE),
				msg("both take an edge mod"));

		ServerPlayer player = player(helper);

		try {
			hold(player, new ItemStack(M7Items.VIBRO_AXE));
			helper.assertTrue(near(player.getAttributeValue(Attributes.ATTACK_DAMAGE), registered.axeDamage),
					msg("held, the axe's attack damage is " + registered.axeDamage + ", got " + player.getAttributeValue(Attributes.ATTACK_DAMAGE)));
			hold(player, new ItemStack(M7Items.FORCE_PIKE));
			helper.assertTrue(near(player.getAttributeValue(Attributes.ATTACK_DAMAGE), registered.pikeDamage),
					msg("held, the pike's attack damage is " + registered.pikeDamage));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void axeAndPikeGuards(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		SaberBalance sb = SaberToggle.balance();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Mob front = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, FOE_AT);

		try {
			DamageSource blow = level.damageSources().mobAttack(front);

			for (ItemStack weapon : new ItemStack[] {new ItemStack(M7Items.VIBRO_AXE), new ItemStack(M7Items.FORCE_PIKE)}) {
				VibroWeaponItem item = (VibroWeaponItem) weapon.getItem();
				hold(player, weapon);
				player.startUsingItem(InteractionHand.MAIN_HAND);
				helper.assertTrue(player.isUsingItem(), msg("precondition: the " + item.kind() + " guard is up"));
				float before = MeleeCombat.stamina(player);
				helper.assertTrue(near(MeleeCombat.guardMultiplier(player, blow, 4f), MeleeRules.guardThrough(item.kind(), b)),
						msg("a front blow is cut to the " + item.kind() + "'s share"));
				helper.assertTrue(near(before - MeleeCombat.stamina(player), MeleeRules.guardDrain(4f, sb.meleeDrainBase, sb.meleeDrainPerDamage)),
						msg("and drains the guard as the other vibro guards do"));
				player.stopUsingItem();
			}
		} finally {
			player.stopUsingItem();
			front.discard();
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- Sunder (9.1)

	@GameTest(maxTicks = 20)
	public void sunderRules(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		helper.assertTrue(MeleeRules.sunders(VibroWeaponItem.Kind.AXE, b.sunderMinStrength, b) && MeleeRules.sunders(VibroWeaponItem.Kind.AXE, 1f, b)
				&& !MeleeRules.sunders(VibroWeaponItem.Kind.AXE, b.sunderMinStrength - 0.01f, b)
				&& !MeleeRules.sunders(VibroWeaponItem.Kind.SWORD, 1f, b) && !MeleeRules.sunders(VibroWeaponItem.Kind.PIKE, 1f, b),
				msg("only a full swing (" + b.sunderMinStrength + ") of the axe sunders"));
		helper.assertTrue(near(MeleeRules.sunderDrain(10f, b), 10f * b.sunderDrainMultiplier)
				&& near(MeleeRules.sunderExtra(10f, b) + 10f, MeleeRules.sunderDrain(10f, b)),
				msg("a Sunder drains x" + b.sunderDrainMultiplier + "; the extra on top of a guard's own drain makes up the rest"));
		helper.assertTrue(near(MeleeRules.swingDamage(10f, 1f), 10f) && near(MeleeRules.swingDamage(10f, 0f), 2f)
				&& near(MeleeRules.swingDamage(10f, 0.5f), 4f), msg("swing damage scales as vanilla's 0.2 + 0.8 x strength^2"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void sunderDrainsAndBreaksVibroGuard(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		SaberBalance sb = SaberToggle.balance();
		ServerLevel level = helper.getLevel();
		ServerPlayer guard = player(helper);
		ServerPlayer axeman = foe(helper);

		try {
			hold(guard, new ItemStack(GearItems.VIBROSWORD));
			guard.startUsingItem(InteractionHand.MAIN_HAND);
			hold(axeman, new ItemStack(M7Items.VIBRO_AXE));
			DamageSource blow = level.damageSources().playerAttack(axeman);
			long now = level.getGameTime();
			float amount = 4f;
			float plainDrain = MeleeRules.guardDrain(amount, sb.meleeDrainBase, sb.meleeDrainPerDamage);
			helper.assertTrue(plainDrain + MeleeRules.sunderDrain(plainDrain, b) < MeleeCombat.stamina(guard),
					msg("test setup: a plain blow and one Sunder must not empty a full guard"));

			// A half swing is no Sunder: the ordinary drain.
			float before = MeleeCombat.stamina(guard);
			MeleeCombat.swing(axeman, VibroWeaponItem.Kind.AXE, guard, 0.5f, now);
			MeleeCombat.guardMultiplier(guard, blow, amount);
			helper.assertTrue(near(before - MeleeCombat.stamina(guard), plainDrain), msg("a half swing drains " + plainDrain));

			// A full swing drains three times as fast.
			before = MeleeCombat.stamina(guard);
			MeleeCombat.swing(axeman, VibroWeaponItem.Kind.AXE, guard, 1f, now);
			float through = MeleeCombat.guardMultiplier(guard, blow, amount);
			helper.assertTrue(near(before - MeleeCombat.stamina(guard), MeleeRules.sunderDrain(plainDrain, b)) && guard.isUsingItem(),
					msg("a full swing drains " + MeleeRules.sunderDrain(plainDrain, b) + ", got " + (before - MeleeCombat.stamina(guard))));
			helper.assertTrue(near(through, MeleeRules.guardThrough(VibroWeaponItem.Kind.SWORD, b)), msg("the blow itself is still guarded"));

			// Full swings until the guard breaks: the guard's owner is staggered.
			for (int i = 0; i < 10 && guard.isUsingItem(); i++) {
				MeleeCombat.swing(axeman, VibroWeaponItem.Kind.AXE, guard, 1f, now);
				MeleeCombat.guardMultiplier(guard, blow, amount);
			}

			helper.assertTrue(!guard.isUsingItem() && MeleeCombat.stamina(guard) <= 0f, msg("Sunder broke the guard"));
			helper.assertTrue(slowness(guard) >= b.sunderStaggerAmplifier,
					msg("and staggered its owner: Slowness " + b.sunderStaggerAmplifier + " or more, got " + slowness(guard)));
		} finally {
			guard.stopUsingItem();
			M3TestPlayers.remove(guard);
			M3TestPlayers.remove(axeman);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void sunderDrainsAndBreaksSaberGuard(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		SaberBalance sb = SaberToggle.balance();
		ServerLevel level = helper.getLevel();
		GuardingPlayer guard = guardingSaber(helper);
		ServerPlayer axeman = listed(helper, ATTACKER_AT, 180f);

		try {
			ItemStack axe = hold(axeman, new ItemStack(M7Items.VIBRO_AXE));
			float attack = (float) axeman.getAttributeValue(Attributes.ATTACK_DAMAGE);
			long now = level.getGameTime();
			// The weakest full swing, so its drain stays well inside a full guard.
			float strength = b.sunderMinStrength;
			float swing = MeleeRules.swingDamage(attack, strength);
			float base = MeleeRules.guardDrain(swing, sb.meleeDrainBase, sb.meleeDrainPerDamage);

			// What the guard pays for this blow's own drain (its form and staff multipliers included).
			float full = SaberGuard.stamina(guard);
			SaberGuard.drainGuard(guard, base);
			float own = full - SaberGuard.stamina(guard);
			SaberGuard.refill(guard);
			helper.assertTrue(own > 0f && MeleeRules.sunderExtra(own, b) < full,
					msg("test setup: the Sunder's extra drain (" + MeleeRules.sunderExtra(own, b) + ") must fit in a full guard (" + full + ")"));

			// After the blow lands, Sunder drains the rest: with the guard's own drain, three times as fast.
			MeleeCombat.swing(axeman, VibroWeaponItem.Kind.AXE, guard, strength, now);
			MeleeCombat.onHit(axeman, axe, VibroWeaponItem.Kind.AXE, guard);
			float extra = full - SaberGuard.stamina(guard);
			helper.assertTrue(near(extra, MeleeRules.sunderExtra(own, b)), msg("Sunder adds " + MeleeRules.sunderExtra(own, b) + " to the guard's own "
					+ own + ", got " + extra));
			SaberGuard.refill(guard);

			// The whole blow through the damage pipeline.
			MeleeCombat.swing(axeman, VibroWeaponItem.Kind.AXE, guard, strength, now);
			SaberCombat.modifyIncomingDamage(guard, level, level.damageSources().playerAttack(axeman), swing);
			MeleeCombat.onHit(axeman, axe, VibroWeaponItem.Kind.AXE, guard);
			float total = full - SaberGuard.stamina(guard);

			if (MeleeRules.sunderDrain(own, b) < full) {
				helper.assertTrue(near(total, MeleeRules.sunderDrain(own, b)) && !SaberGuard.isBroken(guard),
						msg("blow and Sunder drain " + MeleeRules.sunderDrain(own, b) + " in all, got " + total));
			} else {
				helper.assertTrue(SaberGuard.isBroken(guard), msg("blow and Sunder (" + MeleeRules.sunderDrain(own, b) + ") break a guard of " + full));
			}

			// Not a Sunder: a half swing, and a full swing at a guard turned away.
			SaberGuard.refill(guard);
			guard.startUsingItem(InteractionHand.MAIN_HAND);
			MeleeCombat.swing(axeman, VibroWeaponItem.Kind.AXE, guard, 0.5f, now);
			MeleeCombat.onHit(axeman, axe, VibroWeaponItem.Kind.AXE, guard);
			helper.assertTrue(near(SaberGuard.stamina(guard), full), msg("a half swing adds nothing"));
			guard.setYRot(180f);
			MeleeCombat.swing(axeman, VibroWeaponItem.Kind.AXE, guard, 1f, now);
			MeleeCombat.onHit(axeman, axe, VibroWeaponItem.Kind.AXE, guard);
			helper.assertTrue(near(SaberGuard.stamina(guard), full), msg("a guard not raised against the axe is not sundered"));
			guard.setYRot(0f);

			// A nearly empty guard breaks: staggered.
			SaberGuard.set(guard, 1f);
			MeleeCombat.swing(axeman, VibroWeaponItem.Kind.AXE, guard, 1f, now);
			MeleeCombat.onHit(axeman, axe, VibroWeaponItem.Kind.AXE, guard);
			helper.assertTrue(SaberGuard.isBroken(guard), msg("Sunder broke the saber guard"));
			helper.assertTrue(slowness(guard) >= b.sunderStaggerAmplifier,
					msg("and staggered the guard's owner: Slowness " + b.sunderStaggerAmplifier + " or more, got " + slowness(guard)));
		} finally {
			guard.stopUsingItem();
			SaberGuard.forget(guard);
			M3TestPlayers.remove(axeman);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void sunderDrainsAndBreaksNpcGuard(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		SaberBalance sb = SaberToggle.balance();
		ServerLevel level = helper.getLevel();
		SithAcolyte acolyte = acolyte(helper, new BlockPos(2, 1, 4));
		ServerPlayer axeman = player(helper);

		try {
			DuelState duel = NpcGuard.state(acolyte);
			helper.assertTrue(duel != null, msg("the acolyte's guard is registered by its combat goal"));
			ItemStack axe = hold(axeman, new ItemStack(M7Items.VIBRO_AXE));
			float attack = (float) axeman.getAttributeValue(Attributes.ATTACK_DAMAGE);
			DamageSource blow = level.damageSources().playerAttack(axeman);
			long now = level.getGameTime();
			float base = MeleeRules.guardDrain(attack, sb.npcDrainBase, sb.npcDrainPerDamage);
			duel.refill();
			acolyte.setGuardState(GuardState.GUARDING);
			float full = duel.stamina(now);
			helper.assertTrue(base < full, msg("test setup: one plain blow must not break the acolyte's guard (" + base + " of " + full + ")"));

			// A half swing: only the guard's own drain.
			MeleeCombat.swing(axeman, VibroWeaponItem.Kind.AXE, acolyte, 0.5f, now);
			SaberCombat.modifyIncomingDamage(acolyte, level, blow, attack);
			MeleeCombat.onHit(axeman, axe, VibroWeaponItem.Kind.AXE, acolyte);
			helper.assertTrue(near(full - duel.stamina(now), base) && !duel.isBroken(now), msg("a plain blow drains " + base));

			// A full swing: the NPC's guard pays three times the drain.
			duel.refill();
			acolyte.setGuardState(GuardState.GUARDING);
			MeleeCombat.swing(axeman, VibroWeaponItem.Kind.AXE, acolyte, 1f, now);
			SaberCombat.modifyIncomingDamage(acolyte, level, blow, attack);
			MeleeCombat.onHit(axeman, axe, VibroWeaponItem.Kind.AXE, acolyte);

			if (MeleeRules.sunderDrain(base, b) < full) {
				helper.assertTrue(near(full - duel.stamina(now), MeleeRules.sunderDrain(base, b)) && !duel.isBroken(now),
						msg("Sunder drains " + MeleeRules.sunderDrain(base, b) + " in all"));
			} else {
				helper.assertTrue(duel.isBroken(now) && acolyte.guardState() == GuardState.BROKEN,
						msg("Sunder's " + MeleeRules.sunderDrain(base, b) + " breaks the acolyte's guard of " + full));
				helper.assertTrue(slowness(acolyte) >= b.sunderStaggerAmplifier,
						msg("and staggers it: Slowness " + b.sunderStaggerAmplifier + " or more, got " + slowness(acolyte)));
			}

			// The real attack, end to end: callback swing, the damage pipeline, the item's hurtEnemy.
			SithAcolyte second = acolyte(helper, new BlockPos(3, 1, 3));
			DuelState secondDuel = NpcGuard.state(second);
			helper.assertTrue(secondDuel != null, msg("the second acolyte's guard is registered"));
			secondDuel.refill();
			second.setGuardState(GuardState.GUARDING);
			NpcGuard.drain(second, Math.max(0f, secondDuel.stamina(now) - base * 2f));
			float left = secondDuel.stamina(now);
			strike(axeman, second, VibroWeaponItem.Kind.AXE);
			helper.assertTrue(secondDuel.isBroken(now) && slowness(second) >= b.sunderStaggerAmplifier,
					msg("a real full swing at a guard with " + left + " left breaks it (" + MeleeRules.sunderDrain(base, b) + ") and staggers the acolyte"));
			second.discard();
		} finally {
			acolyte.discard();
			M3TestPlayers.remove(axeman);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the stun thrust (9.1)

	@GameTest(maxTicks = 20)
	public void pikeChargeRules(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		helper.assertTrue(!MeleeRules.pikeCharged(T0, T0 + b.pikeChargeTicks - 1, b) && MeleeRules.pikeCharged(T0, T0 + b.pikeChargeTicks, b)
				&& !MeleeRules.pikeCharged(-1, T0, b), msg("the pike charges in " + b.pikeChargeTicks + " ticks"));
		helper.assertTrue(MeleeRules.pikeChargeLive(T0, T0 + b.pikeChargeWindow, b) && !MeleeRules.pikeChargeLive(T0, T0 + b.pikeChargeWindow + 1, b)
				&& !MeleeRules.pikeChargeLive(-1, T0, b), msg("a charge lasts " + b.pikeChargeWindow + " ticks"));
		helper.assertTrue(MeleeRules.chargeLoaded(VibroWeaponItem.Kind.PIKE, T0, T0 + b.pikeChargeTicks, b)
				&& MeleeRules.chargeLoaded(VibroWeaponItem.Kind.STAFF, T0, T0 + b.staffChargeTicks, b)
				&& MeleeRules.chargeLive(VibroWeaponItem.Kind.PIKE, T0, T0 + b.pikeChargeWindow, b)
				&& MeleeRules.chargeLive(VibroWeaponItem.Kind.STAFF, T0, T0 + b.staffChargeWindow, b),
				msg("the staff and the pike each keep their own charge numbers"));
		helper.assertTrue(near(MeleeRules.thrustDamage(10f, true, b), 10f * b.pikeDroidMultiplier) && near(MeleeRules.thrustDamage(10f, false, b), 10f),
				msg("a droid takes x" + b.pikeDroidMultiplier + " from a charged thrust"));
		helper.succeed();
	}

	@GameTest(maxTicks = 80)
	public void pikeChargesThroughWeaponAction(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		ServerPlayer player = player(helper);
		Mob pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, FOE_AT);
		Runnable cleanUp = () -> {
			pig.discard();
			M3TestPlayers.remove(player);
		};

		try {
			hold(player, new ItemStack(M7Items.FORCE_PIKE));
			helper.assertTrue(WeaponActions.dispatch(player, true), msg("the Weapon Action belongs to the held pike"));
			helper.assertTrue(!MeleeCombat.pikeCharged(player) && !MeleeCombat.staffCharged(player), msg("the charge takes time to load"));
		} catch (RuntimeException | Error e) {
			cleanUp.run();
			throw e;
		}

		helper.runAfterDelay(b.pikeChargeTicks + 2, () -> {
			try {
				helper.assertTrue(MeleeCombat.pikeCharged(player) && !MeleeCombat.staffCharged(player),
						msg("after " + b.pikeChargeTicks + " ticks the pike's charge is loaded (and it is no staff charge)"));
				strike(player, pig, VibroWeaponItem.Kind.PIKE);
				helper.assertTrue(slowness(pig) == b.pikeStunSlowness, msg("the charged hit stuns: Slowness " + b.pikeStunSlowness + ", got " + slowness(pig)));
				helper.assertTrue(!MeleeCombat.pikeCharged(player), msg("and spends the charge"));
				helper.succeed();
			} finally {
				cleanUp.run();
			}
		});
	}

	@GameTest(maxTicks = 20)
	public void stunThrustStunsPushesAndSparesDroids(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Mob pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, FOE_AT);
		Mob resting = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 1, 3));
		Mob droid = helper.spawnWithNoFreeWill(DroidEntities.SECURITY_DROID, new BlockPos(1, 1, 3));

		try {
			helper.assertTrue(MeleeCombat.isDroid(droid) && !MeleeCombat.isDroid(pig), msg("precondition: a security droid is a droid, a pig is not"));
			hold(player, new ItemStack(M7Items.FORCE_PIKE));
			float attack = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
			long now = level.getGameTime();

			// A real charged hit: stunned (Slowness and Weakness), the charge spent.
			MeleeCombat.chargedSince(player, VibroWeaponItem.Kind.PIKE, 0);
			helper.assertTrue(MeleeCombat.pikeCharged(player) && !MeleeCombat.staffCharged(player), msg("the pike is charged"));
			strike(player, pig, VibroWeaponItem.Kind.PIKE);
			MobEffectInstance slow = pig.getEffect(MobEffects.SLOWNESS);
			MobEffectInstance weak = pig.getEffect(MobEffects.WEAKNESS);
			helper.assertTrue(slow != null && slow.getAmplifier() == b.pikeStunSlowness && slow.getDuration() <= b.pikeStunTicks && slow.getDuration() > 0,
					msg("stunned: Slowness " + b.pikeStunSlowness + " for up to " + b.pikeStunTicks + " ticks"));
			helper.assertTrue(weak != null && weak.getAmplifier() == b.pikeStunWeakness, msg("and Weakness " + b.pikeStunWeakness));
			helper.assertTrue(!MeleeCombat.pikeCharged(player), msg("the charge is spent"));

			// The push, on a pig at rest: straight away from the wielder.
			MeleeCombat.stunThrust(player, resting, b);
			Vec3 pushed = resting.getDeltaMovement();
			Vec3 away = resting.position().subtract(player.position()).horizontal().normalize().scale(b.pikeStunKnockback);
			helper.assertTrue(near(pushed.x, away.x) && near(pushed.z, away.z) && pushed.y > 0.0,
					msg("pushed back " + b.pikeStunKnockback + " away from the wielder, got " + pushed));

			// A droid: struck harder, not stunned.
			MeleeCombat.chargedSince(player, VibroWeaponItem.Kind.PIKE, 0);
			MeleeCombat.swing(player, VibroWeaponItem.Kind.PIKE, droid, 1f, now);
			float expected = MeleeRules.thrustDamage(MeleeRules.swingDamage(attack, 1f), true, b) - MeleeRules.swingDamage(attack, 1f);
			helper.assertTrue(near(MeleeCombat.attackBonus(player, droid, VibroWeaponItem.Kind.PIKE), expected),
					msg("a charged thrust deals " + expected + " more to a droid"));
			MeleeCombat.swing(player, VibroWeaponItem.Kind.PIKE, resting, 1f, now);
			helper.assertTrue(near(MeleeCombat.attackBonus(player, resting, VibroWeaponItem.Kind.PIKE), 0f), msg("and nothing more to anything else"));
			MeleeCombat.stunThrust(player, droid, b);
			helper.assertTrue(!droid.hasEffect(MobEffects.SLOWNESS) && !droid.hasEffect(MobEffects.WEAKNESS), msg("a droid is not stunned"));

			// A spent or stale charge adds nothing.
			MeleeCombat.chargedSince(player, VibroWeaponItem.Kind.PIKE, b.pikeChargeWindow + 1);
			MeleeCombat.swing(player, VibroWeaponItem.Kind.PIKE, droid, 1f, now);
			helper.assertTrue(!MeleeCombat.pikeCharged(player) && near(MeleeCombat.attackBonus(player, droid, VibroWeaponItem.Kind.PIKE), 0f),
					msg("a charge older than " + b.pikeChargeWindow + " ticks is gone"));
		} finally {
			pig.discard();
			resting.discard();
			droid.discard();
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the edges (9.2)

	@GameTest(maxTicks = 20)
	public void starfallEdgeHitsDroidsAndConstructsHarder(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Mob droid = helper.spawnWithNoFreeWill(DroidEntities.SECURITY_DROID, new BlockPos(1, 1, 3));
		Mob golem = helper.spawnWithNoFreeWill(EntityTypes.IRON_GOLEM, new BlockPos(4, 1, 4));
		Mob pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, FOE_AT);

		try {
			helper.assertTrue(MeleeCombat.isConstruct(golem) && !MeleeCombat.isConstruct(pig) && !MeleeCombat.isConstruct(droid),
					msg("an iron golem is a construct"));
			float ratio = MeleeRules.starfallRatio(true, b);
			helper.assertTrue(near(ratio, 1.0 + b.starfallEdgeDamagePct / 100.0) && near(MeleeRules.starfallRatio(false, b), 1.0),
					msg("+" + b.starfallEdgeDamagePct + "% to droids and constructs only"));

			hold(player, edged(new ItemStack(M7Items.VIBRO_AXE), new ItemStack(M7Items.MOD_STARFALL_EDGE)));
			helper.assertTrue(Mods.has(player.getMainHandItem(), M7Items.MOD_STARFALL_EDGE), msg("precondition: the edge is fitted"));
			DamageSource blow = level.damageSources().playerAttack(player);
			helper.assertTrue(near(MeleeCombat.starfallEdge(droid, blow, 10f), 10f * ratio) && near(MeleeCombat.starfallEdge(golem, blow, 10f), 10f * ratio),
					msg("a melee blow with the edge deals x" + ratio + " to a droid and a construct"));
			helper.assertTrue(near(MeleeCombat.starfallEdge(pig, blow, 10f), 10f), msg("but not to a pig"));
			helper.assertTrue(near(MeleeCombat.starfallEdge(droid, level.damageSources().magic(), 10f), 10f), msg("nor anything that is not a blow"));
			float withEdge = DamageModifiers.apply(droid, blow, 10f);

			hold(player, new ItemStack(M7Items.VIBRO_AXE));
			helper.assertTrue(near(MeleeCombat.starfallEdge(droid, blow, 10f), 10f), msg("a weapon without the edge gets nothing"));
			float without = DamageModifiers.apply(droid, blow, 10f);
			helper.assertTrue(without > 0f && near(withEdge / without, ratio), msg("the registered modifier gives x" + ratio + ", got " + withEdge / without));
		} finally {
			droid.discard();
			golem.discard();
			pig.discard();
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void starfallEdgeLastsLonger(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Mob pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, FOE_AT);
		Mob duelist = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(1, 1, 3));

		try {
			helper.assertTrue(MeleeRules.notchWear(b.saberNotch, false, b) == b.saberNotch
					&& MeleeRules.notchWear(b.saberNotch, true, b) == Math.round(b.saberNotch * 100f / (100f + b.starfallEdgeDurabilityPct)),
					msg("a notch takes " + MeleeRules.notchWear(b.saberNotch, true, b) + " instead of " + b.saberNotch + " with the edge"));

			// Hits: one point of wear back every (100 + pct) / pct hits (5 at the default 25%).
			int hitsPerRefund = Math.round((100f + b.starfallEdgeDurabilityPct) / b.starfallEdgeDurabilityPct);
			ItemStack axe = hold(player, edged(new ItemStack(M7Items.VIBRO_AXE), new ItemStack(M7Items.MOD_STARFALL_EDGE)));
			axe.setDamageValue(20);

			for (int hit = 1; hit <= hitsPerRefund; hit++) {
				MeleeCombat.onHit(player, axe, VibroWeaponItem.Kind.AXE, pig);
				int expected = hit < hitsPerRefund ? 20 : 19;
				helper.assertTrue(axe.getDamageValue() == expected, msg("after hit " + hit + " the wear is " + expected + ", got " + axe.getDamageValue()));
			}

			ItemStack plain = hold(player, new ItemStack(M7Items.VIBRO_AXE));
			plain.setDamageValue(20);

			for (int hit = 1; hit <= hitsPerRefund; hit++) {
				MeleeCombat.onHit(player, plain, VibroWeaponItem.Kind.AXE, pig);
			}

			helper.assertTrue(plain.getDamageValue() == 20, msg("a weapon without the edge gets nothing back"));

			// A guarded saber blow notches less.
			duelist.setItemSlot(EquipmentSlot.MAINHAND, litSaber(level));
			ItemStack sword = hold(player, edged(new ItemStack(GearItems.VIBROSWORD), new ItemStack(M7Items.MOD_STARFALL_EDGE)));
			player.startUsingItem(InteractionHand.MAIN_HAND);
			MeleeCombat.guardMultiplier(player, level.damageSources().mobAttack(duelist), 6f);
			helper.assertTrue(sword.getDamageValue() == MeleeRules.notchWear(b.saberNotch, true, b),
					msg("the guarded saber blow notches " + MeleeRules.notchWear(b.saberNotch, true, b) + ", got " + sword.getDamageValue()));
		} finally {
			player.stopUsingItem();
			pig.discard();
			duelist.discard();
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void clanIronEdgeHoldsSabersAndDrainsLess(GameTestHelper helper) {
		MeleeBalance b = MeleeBalance.get();
		SaberBalance sb = SaberToggle.balance();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper);
		Mob duelist = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, FOE_AT);

		try {
			helper.assertTrue(near(MeleeRules.edgeDrain(20f, true, b), 20f * (1f - b.clanIronEdgeDrainPct / 100f)) && near(MeleeRules.edgeDrain(20f, false, b), 20f),
					msg("the Clan Iron Edge takes " + b.clanIronEdgeDrainPct + "% off the guard drain"));
			duelist.setItemSlot(EquipmentSlot.MAINHAND, litSaber(level));
			DamageSource blow = level.damageSources().mobAttack(duelist);
			float drain = MeleeRules.guardDrain(6f, sb.meleeDrainBase, sb.meleeDrainPerDamage);

			ItemStack blade = hold(player, edged(new ItemStack(EnemyItems.VIBROBLADE), new ItemStack(M7Items.MOD_CLAN_IRON_EDGE)));
			player.startUsingItem(InteractionHand.MAIN_HAND);
			float before = MeleeCombat.stamina(player);
			helper.assertTrue(near(MeleeCombat.guardMultiplier(player, blow, 6f), MeleeRules.guardThrough(VibroWeaponItem.Kind.BLADE, b)),
					msg("a clan iron vibroblade guards a saber blow"));
			helper.assertTrue(blade.getDamageValue() == 0, msg("without a notch, as the electrostaff: " + blade.getDamageValue()));
			helper.assertTrue(near(before - MeleeCombat.stamina(player), MeleeRules.edgeDrain(drain, true, b)),
					msg("and pays " + MeleeRules.edgeDrain(drain, true, b) + " of guard instead of " + drain));
			player.stopUsingItem();

			ItemStack bare = hold(player, new ItemStack(EnemyItems.VIBROBLADE));
			player.startUsingItem(InteractionHand.MAIN_HAND);
			before = MeleeCombat.stamina(player);
			MeleeCombat.guardMultiplier(player, blow, 6f);
			helper.assertTrue(bare.getDamageValue() == b.saberNotch && near(before - MeleeCombat.stamina(player), drain),
					msg("a vibroblade without it notches by " + b.saberNotch + " and pays " + drain));
		} finally {
			player.stopUsingItem();
			duelist.discard();
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}
}
