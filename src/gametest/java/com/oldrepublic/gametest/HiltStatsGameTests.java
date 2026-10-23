package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.enemy.construct.SentinelEntities;
import com.oldrepublic.enemy.droid.DroidEntities;
import com.oldrepublic.enemy.hymn.HymnDamage;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.progression.ProgressionBalance;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.CrystalTuning;
import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.HiltMaterials;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.HiltSet;
import com.oldrepublic.saber.HiltSetEffects;
import com.oldrepublic.saber.HiltTraits;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberForm;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberStats;
import com.oldrepublic.saber.SaberToggle;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 7's hilt numbers (spec MILESTONE_7.md 3, 6.3, 7.2 and test plan 14, HS): each material tier's bonus in each
 * slot; the worked example of 3.2; the order of the steps (the parts and the staff, then the materials, then the blade
 * length, then the tunings, then the form); the parry cap; every new design's numbers; the boss sets' counting (a
 * saberstaff's two) and two-part bonuses; the materials' traits and the sets' three-part effects; the tooltip lines; and
 * today's numbers unchanged for every steel hilt.
 *
 * <p>Test rules (the Milestone 5 rules of SaberM5GameTests): every test declares {@code maxTicks}; the only fixture is
 * the {@code oldrepublic_test:arena_24x7x24} arena; players are listed mock players ({@link M3TestPlayers}) put in
 * survival and logged out when the test ends, whichever step ends it ({@link Cleanup}). Where a guard must have been
 * held for a set number of ticks (a parry or a block) the guard is a fake player that reports it, as SaberGameTests'
 * guards do, forgotten when the test ends. Made-up wielders' marks and counts are forgotten too. Numbers come from the
 * live {@code saber} balance section unless a check is about the spec's defaults, so a tuned file still passes. Saber
 * hits are dealt through {@link SaberCombat#strike} for an explicit amount (a held saber's attribute modifiers do not
 * matter here), so what waits for a hit to land (duskglass, Contrition, the Long Hymn) is seen through the hit's real
 * path ({@code AFTER_DAMAGE}). Target dummies are NoFreeWill zombies with no armour and fire resistance (the arena is
 * open to the sky); every placement and the check that reads it happen in one step.
 */
public class HiltStatsGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	private static final double EPS = 1.0e-4;
	/** Any power: the hilt's Force cost changes every power's cost alike. */
	private static final String SOME_POWER = "force_push";
	/** A player's Force energy before a hit whose restored energy is measured: far below the maximum. */
	private static final float LOW_ENERGY = 10f;
	/** A saber hit's damage in the live checks: small, so every dummy lives through the test. */
	private static final float HIT = 1f;
	/** A blow's damage on a guard. */
	private static final float BLOW = 2f;
	/** Contrition's check puts the dummy at this share of the threshold health. */
	private static final float UNDER_THRESHOLD = 0.6f;
	/** A guard held this long blocks: far past any parry window (the cap is 11 ticks). */
	private static final int LONG_GUARD_TICKS = 40;
	/** The parts that existed before Milestone 7 (Grip.STAFF is the saberstaff's, numberless on a single saber). */
	private static final Emitter[] OLD_EMITTERS = {Emitter.GUARDIAN, Emitter.DUELIST, Emitter.RELIQUARY, Emitter.CANTOR};
	private static final Grip[] OLD_GRIPS = {Grip.WRAPPED, Grip.RIDGED, Grip.STAFF, Grip.SUNFORGED};
	private static final Pommel[] OLD_POMMELS = {Pommel.WEIGHTED, Pommel.BALANCED, Pommel.ARCHIVIST, Pommel.HASK};
	/** Lang key of a part's name in a material (HiltTraits). */
	private static final String NAMED_KEY = "saber.oldrepublic.part.named";

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

		/** Lowers a listed player's guard, forgets its guard, its mark and its hymn count, then logs it out. */
		ServerPlayer player(ServerPlayer player) {
			undo.add(() -> {
				player.stopUsingItem();
				SaberGuard.forget(player);
				HiltSetEffects.forget(player.getUUID());
				M3TestPlayers.remove(player);
			});
			return player;
		}

		/** Lowers a guarding fake player's guard and forgets its guard and its mark. */
		<T extends ServerPlayer> T guard(T player) {
			undo.add(() -> {
				player.stopUsingItem();
				SaberGuard.forget(player);
				HiltSetEffects.forget(player.getUUID());
			});
			return player;
		}

		/** Forgets a made-up wielder's mark and hymn count. */
		UUID wielder(UUID id) {
			undo.add(() -> HiltSetEffects.forget(id));
			return id;
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

	/** Runs the whole test {@code body} now and succeeds; cleans up either way. */
	private static void now(GameTestHelper helper, Cleanup cleanup, Runnable body) {
		try {
			body.run();
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	/**
	 * A fake player that has guarded for {@code ticksUsing} ticks (SaberGameTests' guard): whether a blow is parried or
	 * blocked depends on how long the guard has been held, which a test player would only reach by waiting.
	 */
	private static final class GuardingPlayer extends FakePlayer {
		private final int ticksUsing;

		GuardingPlayer(ServerLevel level, int ticksUsing) {
			super(level, new GameProfile(UUID.randomUUID(), "hilt_guard"));
			this.ticksUsing = ticksUsing;
		}

		@Override
		public int getTicksUsingItem() {
			return isUsingItem() ? ticksUsing : 0;
		}
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < EPS;
	}

	/** {@code 1 + pct/100}. */
	private static double ratio(double pct) {
		return 1.0 + pct / 100.0;
	}

	/** Whether {@code got} holds exactly {@code want} (six tiers, steel to hymnsteel). */
	private static boolean tiers(float[] got, float... want) {
		if (got.length != want.length) {
			return false;
		}

		for (int i = 0; i < want.length; i++) {
			if (!near(got[i], want[i])) {
				return false;
			}
		}

		return true;
	}

	/** Stats from their seven numbers: the expected side of a check. */
	private static SaberStats stats(double damage, double speed, double reach, double stamina, double parry, double drainPct, double costPct) {
		return new SaberStats(damage, speed, reach, (float) stamina, (float) parry, (float) drainPct, (float) costPct);
	}

	/** Whether two stats agree in all seven numbers. */
	private static boolean same(SaberStats a, SaberStats b) {
		return near(a.damage(), b.damage()) && near(a.attackSpeed(), b.attackSpeed()) && near(a.reachBonus(), b.reachBonus())
				&& near(a.maxStamina(), b.maxStamina()) && near(a.parryTicks(), b.parryTicks()) && near(a.guardDrainPct(), b.guardDrainPct())
				&& near(a.forceCostPct(), b.forceCostPct());
	}

	private static void expect(GameTestHelper helper, String what, SaberStats got, SaberStats want) {
		helper.assertTrue(same(got, want), what + ": expected " + want + ", got " + got);
	}

	/**
	 * Milestone 6's hilt numbers: {@code SaberStats.of(emitter, grip, pommel, b)} as it was before Milestone 7, for the
	 * parts that existed then. Every steel hilt must still come to these.
	 */
	private static SaberStats todays(Emitter emitter, Grip grip, @Nullable Pommel pommel, SaberBalance b) {
		SaberStats.Legendary l = SaberStats.legendary();
		double damage = b.baseDamage;
		double speed = b.baseAttackSpeed;
		double reach = 0;
		float stamina = b.guardStamina;
		float parry = b.parryWindowTicks;
		double damagePct = 0;
		double speedPct = 0;

		switch (emitter) {
			case GUARDIAN -> {
				reach += b.guardianReach;
				speed += b.guardianSpeed;
			}
			case DUELIST -> {
				speed += b.duelistSpeed;
				damage += b.duelistDamage;
			}
			case CANTOR -> damagePct += l.cantorDamagePct();
			default -> {
			}
		}

		switch (grip) {
			case WRAPPED -> stamina *= b.wrappedStaminaMultiplier;
			case RIDGED -> speed += b.ridgedSpeed;
			case SUNFORGED -> speedPct += l.sunforgedSpeedPct();
			default -> {
			}
		}

		if (pommel != null) {
			switch (pommel) {
				case WEIGHTED -> {
					damage += b.weightedDamage;
					speed += b.weightedSpeed;
				}
				case BALANCED -> parry *= b.balancedParryMultiplier;
				case ARCHIVIST -> {
					parry += b.archivistParryTicks;
					speed += b.archivistAttackSpeed;
				}
				case HASK -> {
					stamina += l.haskGuard();
					parry += l.haskParryTicks();
				}
				default -> {
				}
			}
		}

		if (grip == Grip.SUNFORGED) {
			stamina += l.sunforgedGuard();
		}

		return new SaberStats(damage * (1.0 + damagePct / 100.0), Math.max(0.1, speed * (1.0 + speedPct / 100.0)), reach, stamina, parry);
	}

	/** Milestone 6's saberstaff numbers ({@code SaberStats.staff} before Milestone 7). */
	private static SaberStats todaysStaff(Emitter emitter, boolean bothBlades, SaberBalance b) {
		SaberStats s = todays(emitter, Grip.STAFF, null, b);
		double speed = s.attackSpeed() + b.staffSpeedBonus;
		double reach = s.reachBonus() + b.staffReach;

		if (!bothBlades) {
			return new SaberStats(s.damage(), speed, reach, s.maxStamina(), s.parryTicks());
		}

		return new SaberStats(s.damage() * (1.0 + b.staffDamagePct / 100.0), speed, reach, s.maxStamina(), Math.max(0f, s.parryTicks() + b.staffParryTicks));
	}

	private static CrystalAttunement crystal(UUID owner, @Nullable CrystalTuning tuning) {
		return new CrystalAttunement(owner, "Tester", BladeColor.BLUE.rgb(), tuning == null ? "" : tuning.id());
	}

	/** Every part made of {@code material}. */
	private static HiltMaterials all(HiltMaterial material) {
		return new HiltMaterials(material, material, material);
	}

	/** A retracted single saber of these parts in {@code materials}, around {@code owner}'s untuned crystal. */
	private static ItemStack saber(UUID owner, Emitter emitter, Grip grip, Pommel pommel, HiltMaterials materials) {
		return SaberItems.buildSaber(crystal(owner, null), BladeColor.BLUE, emitter, grip, pommel, materials, SaberToggle.balance());
	}

	/**
	 * A retracted saberstaff of {@code emitter} and {@code grip} in {@code materials} (the builder takes the staff grip, so
	 * another grip is fitted after, as a crafted config could carry it).
	 */
	private static ItemStack staff(UUID owner, Emitter emitter, Grip grip, HiltMaterials materials) {
		ItemStack stack = SaberItems.buildStaff(crystal(owner, null), crystal(owner, null), BladeColor.VIOLET, emitter, materials, SaberToggle.balance());
		SaberConfig built = SaberComponents.config(stack);
		return configure(stack, new SaberConfig(built.crystal(), built.color(), built.emitter(), grip, built.pommel(), built.second(), built.lens(),
				built.length(), built.materials()));
	}

	/** Gives {@code stack} {@code config} and rewrites its modifiers and reach, as the workbench does. */
	private static ItemStack configure(ItemStack stack, SaberConfig config) {
		stack.set(SaberComponents.SABER_CONFIG, config);
		SaberToggle.refreshModifiers(stack, SaberComponents.isIgnited(stack));
		return stack;
	}

	private static ItemStack lit(ItemStack stack, ServerLevel level) {
		SaberToggle.setIgnited(stack, true, level.getGameTime());
		return stack;
	}

	/** A listed player at {@code relative}, in survival, on the ground, facing south (+z) and level. */
	private static ServerPlayer listed(GameTestHelper helper, Cleanup cleanup, BlockPos relative) {
		ServerPlayer player = cleanup.player(M3TestPlayers.listed(helper, relative));
		player.setGameMode(GameType.SURVIVAL);
		player.setYRot(0f);
		player.setXRot(0f);
		player.setYHeadRot(0f);
		player.setOnGround(true);
		return player;
	}

	/** Puts {@code stack} in {@code player}'s {@code hand}; returns the held stack. */
	private static ItemStack hold(ServerPlayer player, InteractionHand hand, ItemStack stack) {
		player.setItemInHand(hand, stack);
		return player.getItemInHand(hand);
	}

	/** A guard at {@code relative} facing south (+z) that has held up these parts, lit, for {@code ticksUsing} ticks. */
	private static GuardingPlayer guard(GameTestHelper helper, Cleanup cleanup, BlockPos relative, int ticksUsing, Emitter emitter, Grip grip, Pommel pommel,
			HiltMaterials materials) {
		ServerLevel level = helper.getLevel();
		GuardingPlayer player = cleanup.guard(new GuardingPlayer(level, ticksUsing));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(relative)));
		player.setYRot(0f);
		player.setXRot(0f);
		player.setItemInHand(InteractionHand.MAIN_HAND, lit(saber(player.getUUID(), emitter, grip, pommel, materials), level));
		player.startUsingItem(InteractionHand.MAIN_HAND);
		helper.assertTrue(player.isUsingItem() && !SaberGuard.guardingSaber(player).isEmpty(), "precondition: the fake player guards");
		return player;
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

	/** One saber hit of {@code strike} by {@code player} on {@code target}, for {@link #HIT}: returns the health it took. */
	private static float hit(GameTestHelper helper, ServerPlayer player, LivingEntity target, SaberCombat.Strike strike) {
		ServerLevel level = helper.getLevel();
		float before = target.getHealth();
		target.invulnerableTime = 0;
		helper.assertTrue(SaberCombat.strike(target, level, level.damageSources().playerAttack(player), HIT, strike), "precondition: the saber hit lands");
		return before - target.getHealth();
	}

	/** The Force energy one saber hit of {@code strike} on {@code target} gives {@code player} back, from {@link #LOW_ENERGY}. */
	private static float energyFromHit(GameTestHelper helper, ServerPlayer player, LivingEntity target, SaberCombat.Strike strike) {
		ForceData data = Progression.get(player);
		data.energy = LOW_ENERGY;
		Progression.commit(player, data);
		hit(helper, player, target, strike);
		return Progression.get(player).energy - LOW_ENERGY;
	}

	/** How many times as much a main-hand hit deals to {@code target} under Contrition's threshold as at full health. */
	private static double lowHealthRatio(GameTestHelper helper, ServerPlayer player, Mob target, SaberBalance b) {
		target.setHealth(target.getMaxHealth());
		float high = hit(helper, player, target, SaberCombat.Strike.LIGHT);
		target.setHealth(target.getMaxHealth() * b.penitentLowHealthFraction * UNDER_THRESHOLD);
		float low = hit(helper, player, target, SaberCombat.Strike.LIGHT);
		helper.assertTrue(high > 0f && target.isAlive(), "precondition: both hits land and the dummy lives");
		return low / (double) high;
	}

	/** {@code guard} blocks {@code blow} (no parry, no guard break): returns the guard stamina it drained. */
	private static float blockDrain(GameTestHelper helper, GuardingPlayer guard, ServerLevel level, DamageSource blow) {
		float before = SaberGuard.stamina(guard);
		float through = SaberCombat.modifyIncomingDamage(guard, level, blow, BLOW, null);
		float drained = before - SaberGuard.stamina(guard);
		helper.assertTrue(through > 0f && drained > 0f && !SaberGuard.isBroken(guard),
				"precondition: the guard blocks the blow (" + through + " through, " + drained + " drained)");
		return drained;
	}

	private static String key(Component component) {
		return component.getContents() instanceof TranslatableContents text ? text.getKey() : "";
	}

	/** The lang keys of the lines {@code lines} writes, in order. */
	private static List<String> keys(Consumer<Consumer<Component>> lines) {
		List<String> keys = new ArrayList<>();
		lines.accept(line -> keys.add(key(line)));
		return keys;
	}

	// ---------------------------------------------------------------- materials (3.1, 3.2)

	@GameTest(maxTicks = 20)
	public void materialBonusesPerSlot(GameTestHelper helper) {
		SaberBalance d = new SaberBalance();
		helper.assertTrue(tiers(d.materialEmitterDamage, 0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f) && tiers(d.materialGripSpeedPct, 0f, 1.5f, 3f, 4.5f, 6f, 7.5f)
				&& tiers(d.materialGripGuard, 0f, 4f, 8f, 12f, 16f, 20f) && tiers(d.materialPommelParry, 0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f)
				&& tiers(d.materialPommelGuard, 0f, 4f, 8f, 12f, 16f, 20f), "3.2's defaults, steel to hymnsteel");
		helper.assertTrue(SaberStats.tier(null, 2) == 0f && SaberStats.tier(new float[] {0f, 1f}, 3) == 0f && SaberStats.tier(d.materialGripGuard, -1) == 0f,
				"a missing tier (no array, an edited array too short, a bad tier) adds nothing");

		// Each material in each slot, the other two steel: the emitter's flat damage, the grip's speed and guard, the pommel's
		// parry ticks and guard. A saberstaff counts its emitter once and its grip; its pommel entry is a placeholder.
		SaberBalance b = SaberToggle.balance();
		SaberStats base = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, b);
		SaberStats staff = SaberStats.staff(Emitter.DUELIST, Grip.STAFF, true, b);
		HiltMaterial steel = HiltMaterial.STEEL;

		for (HiltMaterial m : HiltMaterial.values()) {
			int tier = m.tier();
			float damage = SaberStats.tier(b.materialEmitterDamage, tier);
			float speedPct = SaberStats.tier(b.materialGripSpeedPct, tier);
			float gripGuard = SaberStats.tier(b.materialGripGuard, tier);
			float parry = SaberStats.tier(b.materialPommelParry, tier);
			float pommelGuard = SaberStats.tier(b.materialPommelGuard, tier);
			expect(helper, "a " + m.id() + " emitter", base.withMaterials(new HiltMaterials(m, steel, steel), true, b),
					stats(base.damage() + damage, base.attackSpeed(), base.reachBonus(), base.maxStamina(), base.parryTicks(), 0, 0));
			expect(helper, "a " + m.id() + " grip", base.withMaterials(new HiltMaterials(steel, m, steel), true, b),
					stats(base.damage(), Math.max(0.1, base.attackSpeed() * ratio(speedPct)), base.reachBonus(), base.maxStamina() + gripGuard, base.parryTicks(), 0, 0));
			expect(helper, "a " + m.id() + " pommel", base.withMaterials(new HiltMaterials(steel, steel, m), true, b),
					stats(base.damage(), base.attackSpeed(), base.reachBonus(), base.maxStamina() + pommelGuard, base.parryTicks() + parry, 0, 0));
			expect(helper, "a saberstaff all in " + m.id(), staff.withMaterials(all(m), false, b), stats(staff.damage() + damage,
					Math.max(0.1, staff.attackSpeed() * ratio(speedPct)), staff.reachBonus(), staff.maxStamina() + gripGuard, staff.parryTicks(), 0, 0));
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void workedExample(GameTestHelper helper) {
		// 3.2, with the spec's defaults: Guardian/Wrapped/Weighted deals 8 damage at 1.35 attacks per second.
		SaberBalance d = new SaberBalance();
		SaberStats steel = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, d);
		helper.assertTrue(near(steel.damage(), 8.0) && near(steel.attackSpeed(), 1.35), "precondition: Guardian/Wrapped/Weighted, 8 damage at 1.35");
		double steelDps = steel.damage() * steel.attackSpeed();

		SaberStats hymn = steel.withMaterials(all(HiltMaterial.HYMNSTEEL), true, d);
		expect(helper, "Guardian/Wrapped/Weighted all in hymnsteel", hymn, stats(9.0, 1.35 * 1.075, 0.25, 120 + 20 + 20, 6 + 1.0, 0, 0));
		double hymnDps = hymn.damage() * hymn.attackSpeed();
		helper.assertTrue(Math.round(hymn.attackSpeed() * 100) == 145 && Math.round(hymnDps * 10) == 131 && Math.round(steelDps * 10) == 108
				&& Math.round((hymnDps / steelDps - 1) * 100) == 21, "all in hymnsteel: 9 damage at 1.45, 13.1 DPS against 10.8, about 21% more (got " + hymnDps + ")");

		SaberStats starfall = steel.withMaterials(all(HiltMaterial.STARFALL), true, d);
		expect(helper, "Guardian/Wrapped/Weighted all in starfall iron", starfall, stats(8.4, 1.35 * 1.03, 0.25, 120 + 8 + 8, 6 + 0.4, 0, 0));
		double starfallDps = starfall.damage() * starfall.attackSpeed();
		helper.assertTrue(Math.round(starfall.attackSpeed() * 100) == 139 && Math.round(starfallDps * 10) == 117
				&& Math.round((starfallDps / steelDps - 1) * 100) == 8, "all in starfall iron: 8.4 damage at 1.39, 11.7 DPS, 8% more (got " + starfallDps + ")");

		// A saber of those parts (its config path), with the live numbers.
		SaberBalance b = SaberToggle.balance();
		ItemStack built = saber(UUID.randomUUID(), Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, all(HiltMaterial.HYMNSTEEL));
		helper.assertTrue(SaberComponents.config(built).materials().equals(all(HiltMaterial.HYMNSTEEL)), "the built saber keeps its materials");
		expect(helper, "a built hymnsteel saber", SaberStats.of(built, b),
				SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, b).withMaterials(all(HiltMaterial.HYMNSTEEL), true, b));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void orderOfSteps(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		UUID owner = UUID.randomUUID();
		HiltMaterials hymn = all(HiltMaterial.HYMNSTEEL);
		int top = HiltMaterial.HYMNSTEEL.tier();
		float emitterDamage = SaberStats.tier(b.materialEmitterDamage, top);
		float gripSpeedPct = SaberStats.tier(b.materialGripSpeedPct, top);
		float gripGuard = SaberStats.tier(b.materialGripGuard, top);
		float pommelParry = SaberStats.tier(b.materialPommelParry, top);
		float pommelGuard = SaberStats.tier(b.materialPommelGuard, top);

		// A single saber (12.1): its parts, then the materials (the flat damage before every percentage), then the blade
		// length, then the tunings (a Keen crystal).
		SaberStats parts = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, b);
		SaberConfig config = new SaberConfig(crystal(owner, CrystalTuning.KEEN), BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED,
				Optional.empty(), Optional.empty(), SaberConfig.LENGTH_LONG, hymn);
		SaberStats full = SaberStats.of(config, b);
		expect(helper, "parts, materials, length, tunings", full, stats((parts.damage() + emitterDamage) * ratio(b.longDamagePct) * ratio(b.keenPct),
				Math.max(0.1, Math.max(0.1, parts.attackSpeed() * ratio(gripSpeedPct)) * ratio(b.longSpeedPct)), parts.reachBonus() + b.longReach,
				parts.maxStamina() + gripGuard + pommelGuard, parts.parryTicks() + pommelParry, 0, 0));
		ItemStack stack = configure(saber(owner, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, hymn), config);
		expect(helper, "the saber's own stats", SaberStats.of(stack, b), full);

		// Then the form: the materials' parry ticks are the hilt's, so the form's multiplier counts them (the spec's
		// defaults: (6 + 1) x 1.5 = 10.5, not 6 x 1.5 + 1 = 10).
		SaberBalance d = new SaberBalance();
		SaberStats hymnHilt = SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, d).withMaterials(hymn, true, d);
		helper.assertTrue(near(hymnHilt.withForm(SaberForm.SORESU.modifiers(d), 0, 0f, d).parryTicks(), 10.5),
				"a hymnsteel pommel's tick comes before Soresu's multiplier: 10.5 ticks");

		// A saberstaff: its own numbers (two blades), then the materials (its emitter once and its grip, never a pommel),
		// then both crystals' tunings.
		ItemStack staffStack = SaberItems.buildStaff(crystal(owner, CrystalTuning.KEEN), crystal(owner, CrystalTuning.SWIFT), BladeColor.VIOLET, Emitter.DUELIST,
				hymn, b);
		helper.assertTrue(SaberComponents.config(staffStack).materials().equals(new HiltMaterials(HiltMaterial.HYMNSTEEL, HiltMaterial.HYMNSTEEL, HiltMaterial.STEEL)),
				"a saberstaff's pommel entry stays steel");
		SaberStats plainStaff = SaberStats.staff(Emitter.DUELIST, Grip.STAFF, !SaberComponents.staffMode(staffStack).single(), b);
		expect(helper, "a hymnsteel saberstaff", SaberStats.of(staffStack, b), stats((plainStaff.damage() + emitterDamage) * ratio(b.keenPct),
				Math.max(0.1, Math.max(0.1, plainStaff.attackSpeed() * ratio(gripSpeedPct)) * ratio(b.swiftPct)), plainStaff.reachBonus(),
				plainStaff.maxStamina() + gripGuard, plainStaff.parryTicks(), 0, 0));
		expect(helper, "a pommel material on a saberstaff", plainStaff.withMaterials(new HiltMaterials(HiltMaterial.STEEL, HiltMaterial.STEEL,
				HiltMaterial.HYMNSTEEL), false, b), plainStaff);
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void parryCapHolds(GameTestHelper helper) {
		// The spec's defaults: the widest hilt there is goes past the cap on its own; the window stops at 11 in any form.
		SaberBalance d = new SaberBalance();
		SaberStats widest = SaberStats.of(Emitter.WARDEN, Grip.RELIQUARY, Pommel.ARCHIVIST, d).withMaterials(all(HiltMaterial.HYMNSTEEL), true, d);
		helper.assertTrue(near(widest.parryTicks(), 6 + 1.0 + 0.5 + 2 + 1 + 1.0),
				"Warden/Reliquary/Archivist in hymnsteel: 6 + 1 + 0.5 + 2 + 1 (the Collector's two parts) + 1 = 11.5 ticks, got " + widest.parryTicks());
		helper.assertTrue(near(d.parryWindowCap, 11) && near(widest.withForm(SaberForm.UNFORMED.modifiers(d), 0, 0f, d).parryTicks(), 11)
				&& near(widest.withForm(SaberForm.SORESU.modifiers(d), 0, 3f, d).parryTicks(), 11), "the window stops at parryWindowCap (11)");

		// Every hilt with the best pommel material, in Soresu with flat passive ticks, stays under the live cap.
		SaberBalance b = SaberToggle.balance();

		if (b.parryWindowCap > 0f) {
			SaberForm.Modifiers soresu = SaberForm.SORESU.modifiers(b);
			HiltMaterials best = all(HiltMaterial.HYMNSTEEL);

			for (Emitter e : Emitter.values()) {
				for (Grip g : Grip.values()) {
					for (Pommel p : Pommel.values()) {
						float window = SaberStats.of(e, g, p, b).withMaterials(best, true, b).withForm(soresu, 0, 4f, b).parryTicks();
						helper.assertTrue(window <= b.parryWindowCap + EPS, e + "/" + g + "/" + p + " in hymnsteel, in Soresu: " + window + " ticks, over the cap");
					}
				}

				float staffWindow = SaberStats.staff(e, Grip.STAFF, true, b).withMaterials(best, false, b).withForm(soresu, 0, 4f, b).parryTicks();
				helper.assertTrue(staffWindow <= b.parryWindowCap + EPS, "a " + e + " saberstaff in hymnsteel, in Soresu: " + staffWindow + " ticks, over the cap");
			}
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the designs (6.3) and today's numbers

	@GameTest(maxTicks = 20)
	public void newDesignNumbers(GameTestHelper helper) {
		SaberBalance d = new SaberBalance();
		helper.assertTrue(near(d.vanguardDamage, 0.5) && near(d.vanguardSpeed, -0.1) && near(d.wardenParryTicks, 1.0) && near(d.wardenGuardDrainPct, -10)
				&& near(d.wardenSpeed, -0.15) && near(d.watchmanEmitterReach, 0.15) && near(d.watchmanEmitterDamage, 0.3) && near(d.penitentEmitterDamage, 0.6)
				&& near(d.penitentEmitterSpeed, -0.05) && near(d.choirEmitterDamagePct, 5) && near(d.choirEmitterForceCostPct, 3), "6.3's emitters");
		helper.assertTrue(near(d.bandedGuard, 10) && near(d.bandedSpeed, 0.05) && near(d.knurledSpeed, 0.15) && near(d.knurledGuardMultiplier, 0.9)
				&& near(d.flutedDamagePct, 5) && near(d.reliquaryGripSpeed, 0.05) && near(d.reliquaryGripGuard, 10) && near(d.reliquaryGripParryTicks, 0.5)
				&& near(d.watchmanGripGuard, 20) && near(d.penitentGripSpeed, 0.1) && near(d.penitentGripDamagePct, 2) && near(d.choirGripSpeedPct, 6), "6.3's grips");
		helper.assertTrue(near(d.ringedParryTicks, 0.5) && near(d.ringedSpeed, 0.05) && near(d.domedForceCostPct, 4) && near(d.watchmanPommelParryTicks, 1.0)
				&& near(d.penitentPommelDamage, 0.5) && near(d.choirPommelParryTicks, 0.5) && near(d.choirPommelGuard, 10), "6.3's pommels");

		// Each design against a hilt whose other two parts change nothing it changes and make no set (live numbers).
		SaberBalance b = SaberToggle.balance();
		SaberStats e0 = SaberStats.of(Emitter.RELIQUARY, Grip.RIDGED, Pommel.WEIGHTED, b);
		expect(helper, "the Vanguard emitter", SaberStats.of(Emitter.VANGUARD, Grip.RIDGED, Pommel.WEIGHTED, b),
				stats(e0.damage() + b.vanguardDamage, e0.attackSpeed() + b.vanguardSpeed, e0.reachBonus(), e0.maxStamina(), e0.parryTicks(), 0, 0));
		expect(helper, "the Warden emitter", SaberStats.of(Emitter.WARDEN, Grip.RIDGED, Pommel.WEIGHTED, b), stats(e0.damage(), e0.attackSpeed() + b.wardenSpeed,
				e0.reachBonus(), e0.maxStamina(), e0.parryTicks() + b.wardenParryTicks, b.wardenGuardDrainPct, 0));
		expect(helper, "the Watchman emitter", SaberStats.of(Emitter.WATCHMAN, Grip.RIDGED, Pommel.WEIGHTED, b),
				stats(e0.damage() + b.watchmanEmitterDamage, e0.attackSpeed(), e0.reachBonus() + b.watchmanEmitterReach, e0.maxStamina(), e0.parryTicks(), 0, 0));
		expect(helper, "the Penitent emitter", SaberStats.of(Emitter.PENITENT, Grip.RIDGED, Pommel.WEIGHTED, b),
				stats(e0.damage() + b.penitentEmitterDamage, e0.attackSpeed() + b.penitentEmitterSpeed, e0.reachBonus(), e0.maxStamina(), e0.parryTicks(), 0, 0));
		expect(helper, "the Choir emitter", SaberStats.of(Emitter.CHOIR, Grip.RIDGED, Pommel.WEIGHTED, b), stats(e0.damage() * ratio(b.choirEmitterDamagePct),
				e0.attackSpeed(), e0.reachBonus(), e0.maxStamina(), e0.parryTicks(), 0, -b.choirEmitterForceCostPct));

		SaberStats g0 = SaberStats.of(Emitter.GUARDIAN, Grip.STAFF, Pommel.WEIGHTED, b);
		expect(helper, "the Banded grip", SaberStats.of(Emitter.GUARDIAN, Grip.BANDED, Pommel.WEIGHTED, b),
				stats(g0.damage(), g0.attackSpeed() + b.bandedSpeed, g0.reachBonus(), g0.maxStamina() + b.bandedGuard, g0.parryTicks(), 0, 0));
		expect(helper, "the Knurled grip", SaberStats.of(Emitter.GUARDIAN, Grip.KNURLED, Pommel.WEIGHTED, b),
				stats(g0.damage(), g0.attackSpeed() + b.knurledSpeed, g0.reachBonus(), g0.maxStamina() * b.knurledGuardMultiplier, g0.parryTicks(), 0, 0));
		expect(helper, "the Fluted grip", SaberStats.of(Emitter.GUARDIAN, Grip.FLUTED, Pommel.WEIGHTED, b),
				stats(g0.damage() * ratio(b.flutedDamagePct), g0.attackSpeed(), g0.reachBonus(), g0.maxStamina(), g0.parryTicks(), 0, 0));
		expect(helper, "the Reliquary grip", SaberStats.of(Emitter.GUARDIAN, Grip.RELIQUARY, Pommel.WEIGHTED, b), stats(g0.damage(),
				g0.attackSpeed() + b.reliquaryGripSpeed, g0.reachBonus(), g0.maxStamina() + b.reliquaryGripGuard, g0.parryTicks() + b.reliquaryGripParryTicks, 0, 0));
		expect(helper, "the Watchman grip", SaberStats.of(Emitter.GUARDIAN, Grip.WATCHMAN, Pommel.WEIGHTED, b),
				stats(g0.damage(), g0.attackSpeed(), g0.reachBonus(), g0.maxStamina() + b.watchmanGripGuard, g0.parryTicks(), 0, 0));
		expect(helper, "the Penitent grip", SaberStats.of(Emitter.GUARDIAN, Grip.PENITENT, Pommel.WEIGHTED, b),
				stats(g0.damage() * ratio(b.penitentGripDamagePct), g0.attackSpeed() + b.penitentGripSpeed, g0.reachBonus(), g0.maxStamina(), g0.parryTicks(), 0, 0));
		expect(helper, "the Choir grip", SaberStats.of(Emitter.GUARDIAN, Grip.CHOIR, Pommel.WEIGHTED, b),
				stats(g0.damage(), g0.attackSpeed() * ratio(b.choirGripSpeedPct), g0.reachBonus(), g0.maxStamina(), g0.parryTicks(), 0, 0));

		SaberStats p0 = SaberStats.of(Emitter.GUARDIAN, Grip.STAFF, null, b);
		expect(helper, "the Ringed pommel", SaberStats.of(Emitter.GUARDIAN, Grip.STAFF, Pommel.RINGED, b),
				stats(p0.damage(), p0.attackSpeed() + b.ringedSpeed, p0.reachBonus(), p0.maxStamina(), p0.parryTicks() + b.ringedParryTicks, 0, 0));
		expect(helper, "the Domed pommel", SaberStats.of(Emitter.GUARDIAN, Grip.STAFF, Pommel.DOMED, b),
				stats(p0.damage(), p0.attackSpeed(), p0.reachBonus(), p0.maxStamina(), p0.parryTicks(), 0, -b.domedForceCostPct));
		expect(helper, "the Watchman pommel", SaberStats.of(Emitter.GUARDIAN, Grip.STAFF, Pommel.WATCHMAN, b),
				stats(p0.damage(), p0.attackSpeed(), p0.reachBonus(), p0.maxStamina(), p0.parryTicks() + b.watchmanPommelParryTicks, 0, 0));
		expect(helper, "the Penitent pommel", SaberStats.of(Emitter.GUARDIAN, Grip.STAFF, Pommel.PENITENT, b),
				stats(p0.damage() + b.penitentPommelDamage, p0.attackSpeed(), p0.reachBonus(), p0.maxStamina(), p0.parryTicks(), 0, 0));
		expect(helper, "the Choir pommel", SaberStats.of(Emitter.GUARDIAN, Grip.STAFF, Pommel.CHOIR, b),
				stats(p0.damage(), p0.attackSpeed(), p0.reachBonus(), p0.maxStamina() + b.choirPommelGuard, p0.parryTicks() + b.choirPommelParryTicks, 0, 0));

		// The two numbers that are not attributes ride through every later step: materials, length, tunings, form, the staff.
		UUID owner = UUID.randomUUID();
		SaberConfig config = new SaberConfig(crystal(owner, CrystalTuning.WARDING), BladeColor.BLUE, Emitter.WARDEN, Grip.RIDGED, Pommel.DOMED,
				Optional.empty(), Optional.empty(), SaberConfig.LENGTH_SHORT, all(HiltMaterial.CLAN_IRON));
		SaberStats carried = SaberStats.of(config, b).withForm(SaberForm.SORESU.modifiers(b), 1, 1f, b);
		helper.assertTrue(near(carried.guardDrainPct(), b.wardenGuardDrainPct) && near(carried.forceCostPct(), -b.domedForceCostPct),
				"a Warden/Domed saber keeps its guard drain and Force cost through materials, length, tunings and form: " + carried);
		SaberStats staffWarden = SaberStats.staff(Emitter.WARDEN, Grip.STAFF, true, b).withMaterials(all(HiltMaterial.CLAN_IRON), false, b);
		SaberStats staffChoir = SaberStats.staff(Emitter.CHOIR, Grip.STAFF, false, b);
		helper.assertTrue(near(staffWarden.guardDrainPct(), b.wardenGuardDrainPct) && near(staffChoir.forceCostPct(), -b.choirEmitterForceCostPct),
				"a saberstaff keeps its emitter's guard drain and Force cost, with two blades or one");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void steelKeepsTodaysNumbers(GameTestHelper helper) {
		// Today's pinned numbers (SaberGameTests.statsMatchSpec), with the spec's defaults.
		SaberBalance d = new SaberBalance();
		expect(helper, "Guardian/Wrapped/Weighted", SaberStats.of(Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, d), stats(8.0, 1.35, 0.25, 120, 6, 0, 0));
		expect(helper, "Duelist/Ridged/Balanced", SaberStats.of(Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, d), stats(6.5, 1.85, 0, 100, 6.6, 0, 0));

		// Every hilt of the parts that existed before Milestone 7, all steel, against Milestone 6's computation; the live
		// numbers. The one change is deliberate: the Reliquary emitter and the Archivist pommel are two parts of the
		// Collector's set (7.1), so together they have its two-part parry tick (7.2).
		SaberBalance b = SaberToggle.balance();
		UUID owner = UUID.randomUUID();

		for (Emitter e : OLD_EMITTERS) {
			for (Grip g : OLD_GRIPS) {
				for (Pommel p : OLD_POMMELS) {
					String hilt = e + "/" + g + "/" + p;
					SaberStats today = todays(e, g, p, b);

					if (e == Emitter.RELIQUARY && p == Pommel.ARCHIVIST) {
						today = stats(today.damage(), today.attackSpeed(), today.reachBonus(), today.maxStamina(), today.parryTicks() + b.collectorSet2Parry, 0, 0);
					}

					SaberStats current = SaberStats.of(e, g, p, b);
					expect(helper, hilt, current, today);
					expect(helper, hilt + " all steel", current.withMaterials(HiltMaterials.DEFAULT, true, b), current);

					if (g != Grip.STAFF) {
						expect(helper, "a built " + hilt, SaberStats.of(SaberItems.buildSaber(crystal(owner, null), BladeColor.GREEN, e, g, p, b), b), current);
					}
				}

				expect(helper, e + "/" + g + " with no pommel", SaberStats.of(e, g, null, b), todays(e, g, null, b));
			}

			expect(helper, "a " + e + " saberstaff", SaberStats.staff(e, Grip.STAFF, true, b), todaysStaff(e, true, b));
			expect(helper, "a " + e + " saberstaff with one blade", SaberStats.staff(e, Grip.STAFF, false, b), todaysStaff(e, false, b));
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the boss sets (7.1, 7.2)

	@GameTest(maxTicks = 20)
	public void setCounting(GameTestHelper helper) {
		SaberBalance d = new SaberBalance();
		helper.assertTrue(near(d.collectorSet2Parry, 1) && d.collectorMarkTicks == 60 && near(d.collectorMarkDamagePct, 20) && near(d.watchmanSet2Guard, 15)
				&& near(d.watchmanRegenPct, 25) && d.watchmanSlowTicks == 20 && d.watchmanSlowAmplifier == 1 && near(d.penitentSet2DamagePct, 5)
				&& near(d.penitentEnergyOnHit, 1) && near(d.penitentLowHealthPct, 10) && near(d.penitentLowHealthFraction, 0.5) && near(d.choirSet2CostPct, 5)
				&& d.choirPulseHits == 5 && d.choirPulseWindowTicks == 200 && near(d.choirPulseDamage, 3) && near(d.choirPulseRadius, 3)
				&& near(d.choirPulseKnockback, 0.4), "7.2's defaults");

		// The stats' own set agrees with the sets' count for every hilt, and for every saberstaff (no pommel).
		UUID owner = UUID.randomUUID();
		CrystalAttunement crystal = crystal(owner, null);

		for (Emitter e : Emitter.values()) {
			for (Grip g : Grip.values()) {
				for (Pommel p : Pommel.values()) {
					helper.assertTrue(SaberStats.activeSet(e, g, p) == HiltSet.active(new SaberConfig(crystal, BladeColor.BLUE, e, g, p), false),
							"the set of " + e + "/" + g + "/" + p);
				}

				helper.assertTrue(SaberStats.activeSet(e, g, null) == HiltSet.active(new SaberConfig(crystal, BladeColor.BLUE, e, g, Pommel.CHOIR), true),
						"the set of a " + e + "/" + g + " saberstaff");
			}
		}

		SaberBalance b = SaberToggle.balance();

		for (HiltSet set : HiltSet.values()) {
			String id = set.id();
			SaberConfig full = new SaberConfig(crystal, BladeColor.BLUE, set.emitter(), set.grip(), set.pommel());
			helper.assertTrue(HiltSet.count(full, set, false) == 3 && HiltSet.count(full, set, true) == 2, id + ": three parts, two on a saberstaff");
			helper.assertTrue(HiltSetEffects.fullSet(saber(owner, set.emitter(), set.grip(), set.pommel(), HiltMaterials.DEFAULT)) == set,
					id + ": all three parts make the full set");

			for (ItemStack two : List.of(saber(owner, set.emitter(), set.grip(), Pommel.WEIGHTED, HiltMaterials.DEFAULT),
					saber(owner, set.emitter(), Grip.WRAPPED, set.pommel(), HiltMaterials.DEFAULT),
					saber(owner, Emitter.GUARDIAN, set.grip(), set.pommel(), HiltMaterials.DEFAULT))) {
				helper.assertTrue(HiltSet.active(SaberComponents.config(two), false) == set && HiltSetEffects.fullSet(two) == null,
						id + ": any two parts have the two-part bonus only");
			}

			ItemStack one = saber(owner, set.emitter(), Grip.WRAPPED, Pommel.WEIGHTED, HiltMaterials.DEFAULT);
			helper.assertTrue(HiltSet.active(SaberComponents.config(one), false) == null && HiltSetEffects.fullSet(one) == null, id + ": one part is no set");

			// A saberstaff counts its emitter and its grip: the two-part bonus at most.
			ItemStack staff = staff(owner, set.emitter(), set.grip(), HiltMaterials.DEFAULT);
			SaberConfig staffConfig = SaberComponents.config(staff);
			helper.assertTrue(HiltSet.count(staffConfig, set, true) == 2 && HiltSet.active(staffConfig, true) == set && HiltSetEffects.fullSet(staff) == null,
					id + ": a saberstaff of the set's emitter and grip has two parts, never three");
			expect(helper, "a " + id + " saberstaff", SaberStats.of(staff, b),
					SaberStats.staff(set.emitter(), set.grip(), !SaberComponents.staffMode(staff).single(), b));
			ItemStack builtStaff = SaberItems.buildStaff(crystal, crystal, BladeColor.VIOLET, set.emitter(), b);
			helper.assertTrue(HiltSet.active(SaberComponents.config(builtStaff), true) == null, id + ": a saberstaff with the staff grip has one part");
		}

		// The two-part bonuses, live numbers.
		expect(helper, "the Collector's Reliquary emitter and Archivist pommel", SaberStats.of(Emitter.RELIQUARY, Grip.RIDGED, Pommel.ARCHIVIST, b),
				stats(b.baseDamage, b.baseAttackSpeed + b.ridgedSpeed + b.archivistAttackSpeed, 0, b.guardStamina,
						b.parryWindowTicks + b.archivistParryTicks + b.collectorSet2Parry, 0, 0));
		expect(helper, "the Collector's two parts with a Balanced pommel (the set's tick after the pommel's multiplier)",
				SaberStats.of(Emitter.RELIQUARY, Grip.RELIQUARY, Pommel.BALANCED, b), stats(b.baseDamage, b.baseAttackSpeed + b.reliquaryGripSpeed, 0,
						b.guardStamina + b.reliquaryGripGuard, (b.parryWindowTicks + b.reliquaryGripParryTicks) * b.balancedParryMultiplier + b.collectorSet2Parry, 0, 0));
		expect(helper, "the full Collector set (its two-part tick once)", SaberStats.of(Emitter.RELIQUARY, Grip.RELIQUARY, Pommel.ARCHIVIST, b),
				stats(b.baseDamage, b.baseAttackSpeed + b.reliquaryGripSpeed + b.archivistAttackSpeed, 0, b.guardStamina + b.reliquaryGripGuard,
						b.parryWindowTicks + b.reliquaryGripParryTicks + b.archivistParryTicks + b.collectorSet2Parry, 0, 0));
		expect(helper, "the Watchman's emitter and grip", SaberStats.of(Emitter.WATCHMAN, Grip.WATCHMAN, Pommel.WEIGHTED, b),
				stats(b.baseDamage + b.watchmanEmitterDamage + b.weightedDamage, b.baseAttackSpeed + b.weightedSpeed, b.watchmanEmitterReach,
						b.guardStamina + b.watchmanGripGuard + b.watchmanSet2Guard, b.parryWindowTicks, 0, 0));
		expect(helper, "the Penitent's emitter and grip", SaberStats.of(Emitter.PENITENT, Grip.PENITENT, Pommel.WEIGHTED, b),
				stats((b.baseDamage + b.penitentEmitterDamage + b.weightedDamage) * ratio(b.penitentGripDamagePct + b.penitentSet2DamagePct),
						b.baseAttackSpeed + b.penitentEmitterSpeed + b.penitentGripSpeed + b.weightedSpeed, 0, b.guardStamina, b.parryWindowTicks, 0, 0));
		expect(helper, "the Choir's emitter and pommel", SaberStats.of(Emitter.CHOIR, Grip.RIDGED, Pommel.CHOIR, b),
				stats(b.baseDamage * ratio(b.choirEmitterDamagePct), b.baseAttackSpeed + b.ridgedSpeed, 0, b.guardStamina + b.choirPommelGuard,
						b.parryWindowTicks + b.choirPommelParryTicks, 0, -(b.choirEmitterForceCostPct + b.choirSet2CostPct)));
		helper.assertTrue(near(SaberStats.of(Emitter.PENITENT, Grip.PENITENT, Pommel.WEIGHTED, d).damage(), (7 + 0.6 + 1) * 1.07),
				"the spec's defaults: the Penitent emitter and grip with a Weighted pommel deal (7 + 0.6 + 1) x 1.07 = 9.2");
		helper.succeed();
	}

	// ---------------------------------------------------------------- traits (3.3) and set effects (7.2): the math

	@GameTest(maxTicks = 20)
	public void traitMath(GameTestHelper helper) {
		SaberBalance d = new SaberBalance();
		helper.assertTrue(near(d.starfallDroidDamagePct, 4) && near(d.aureliteForceCostPct, 3) && near(d.duskglassEnergyOnHit, 0.5)
				&& near(d.sunstoneIgniteChancePct, 4) && d.sunstoneIgniteTicks == 40 && near(d.clanIronGuardDrainPct, 5) && near(d.hymnsteelForceDamagePct, 3),
				"3.3's defaults");
		// Per part, added up (3.1): a full starfall hilt deals 12% more to droids.
		helper.assertTrue(near(HiltTraits.starfallPct(3, d), 12) && near(HiltTraits.energyOnHit(3, d), 1.5) && near(HiltTraits.igniteChance(3, d), 0.12)
				&& near(HiltTraits.forceDamageMultiplier(3, d), 1.09) && near(HiltTraits.forceCostMultiplier(3, 0f, d), 0.91)
				&& near(HiltTraits.guardDrainMultiplier(3, 0f, d), 0.85), "three parts of a material: three times one part's trait");
		helper.assertTrue(near(HiltTraits.guardDrainMultiplier(3, d.wardenGuardDrainPct, d), 0.75)
				&& near(HiltTraits.forceCostMultiplier(3, -(d.choirEmitterForceCostPct + d.domedForceCostPct), d), 0.84),
				"the hilt's own numbers add to the traits': a clan iron Warden drains 25% less, an aurelite Choir/Domed hilt's powers cost 16% less");
		helper.assertTrue(HiltTraits.igniteChance(100, d) == 1f && HiltTraits.forceCostMultiplier(100, 0f, d) == 0f && HiltTraits.guardDrainMultiplier(100, 0f, d) == 0f
				&& HiltTraits.starfallPct(-1, d) == 0f && HiltTraits.energyOnHit(0, d) == 0f, "a chance stops at 1, a multiplier at 0; no parts add nothing");

		UUID owner = UUID.randomUUID();
		SaberConfig mixed = new SaberConfig(crystal(owner, null), BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED)
				.withMaterials(new HiltMaterials(HiltMaterial.STARFALL, HiltMaterial.STARFALL, HiltMaterial.SUNSTONE));
		helper.assertTrue(HiltTraits.count(mixed, false, HiltMaterial.STARFALL) == 2 && HiltTraits.count(mixed, false, HiltMaterial.SUNSTONE) == 1
				&& HiltTraits.count(mixed, true, HiltMaterial.SUNSTONE) == 0 && HiltTraits.count(mixed, false, HiltMaterial.STEEL) == 0,
				"each part of a material counts; a saberstaff never counts its pommel entry");
		helper.assertTrue(HiltTraits.count(SaberItems.partStack(Emitter.GUARDIAN, HiltMaterial.STARFALL), HiltMaterial.STARFALL) == 0, "a loose part is no saber");

		// Starfall iron in the strike: the lit saber that struck, against droids and constructs only (live numbers).
		ServerLevel level = helper.getLevel();
		SaberBalance b = SaberToggle.balance();
		LivingEntity droid = DroidEntities.SECURITY_DROID.create(level, EntitySpawnReason.EVENT);
		LivingEntity sentinel = SentinelEntities.RUIN_SENTINEL.create(level, EntitySpawnReason.EVENT);
		LivingEntity golem = EntityTypes.IRON_GOLEM.create(level, EntitySpawnReason.EVENT);
		LivingEntity zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(droid != null && sentinel != null && golem != null && zombie != null, "precondition: the targets are made");
		helper.assertTrue(HiltTraits.isDroidOrConstruct(droid) && HiltTraits.isDroidOrConstruct(sentinel) && HiltTraits.isDroidOrConstruct(golem)
				&& !HiltTraits.isDroidOrConstruct(zombie), "droids and constructs (a Ruin Sentinel, a golem) count, a zombie does not");
		ItemStack starfall = lit(saber(owner, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, all(HiltMaterial.STARFALL)), level);
		double three = ratio(3 * b.starfallDroidDamagePct);
		helper.assertTrue(near(HiltTraits.strikeMultiplier(starfall, droid, b), three) && near(HiltTraits.strikeMultiplier(starfall, sentinel, b), three)
				&& HiltTraits.strikeMultiplier(starfall, zombie, b) == 1f, "a lit starfall saber: starfallDroidDamagePct per part against droids and constructs");
		ItemStack dark = saber(owner, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, all(HiltMaterial.STARFALL));
		helper.assertTrue(HiltTraits.strikeMultiplier(dark, droid, b) == 1f, "a retracted saber's trait does nothing");
		ItemStack thrown = lit(saber(owner, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, all(HiltMaterial.STARFALL)), level);
		thrown.set(SaberComponents.SABER_THROWN, owner);
		helper.assertTrue(HiltTraits.strikeMultiplier(thrown, droid, b) == 1f, "a thrown saber is not in hand");
		ItemStack staff = lit(staff(owner, Emitter.DUELIST, Grip.STAFF, all(HiltMaterial.STARFALL)), level);
		helper.assertTrue(near(HiltTraits.strikeMultiplier(staff, droid, b), ratio(2 * b.starfallDroidDamagePct)),
				"a starfall saberstaff counts its emitter once and its grip: two parts");

		// Clan iron and the Warden emitter on the guard.
		ItemStack warden = saber(owner, Emitter.WARDEN, Grip.WRAPPED, Pommel.WEIGHTED, all(HiltMaterial.CLAN_IRON));
		helper.assertTrue(near(HiltTraits.guardDrainMultiplier(warden, SaberStats.of(warden, b), b), HiltTraits.guardDrainMultiplier(3, b.wardenGuardDrainPct, b)),
				"a clan iron Warden saber: the emitter's and the material's guard drain together");
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void setEffectMath(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		Cleanup cleanup = new Cleanup();

		now(helper, cleanup, () -> {
			// Curator's Eye: one mark per wielder, on one foe, through its last tick, gone once a hit lands on it.
			UUID wielder = cleanup.wielder(UUID.randomUUID());
			UUID other = cleanup.wielder(UUID.randomUUID());
			UUID foe = UUID.randomUUID();
			UUID bystander = UUID.randomUUID();
			long t = 1000L;
			long until = t + b.collectorMarkTicks;
			HiltSetEffects.mark(wielder, foe, until);
			helper.assertTrue(!HiltSetEffects.consumeMark(wielder, bystander, t) && !HiltSetEffects.consumeMark(other, foe, t),
					"the mark counts for its wielder's hits on its foe only");
			helper.assertTrue(HiltSetEffects.consumeMark(wielder, foe, until), "the mark holds through its last tick");
			helper.assertTrue(!HiltSetEffects.consumeMark(wielder, foe, t), "the marked hit ends the mark");
			HiltSetEffects.mark(wielder, foe, until);
			helper.assertTrue(!HiltSetEffects.consumeMark(wielder, foe, until + 1) && !HiltSetEffects.consumeMark(wielder, foe, t),
					"an expired mark gives nothing, and is gone");
			HiltSetEffects.mark(wielder, foe, until);
			HiltSetEffects.mark(wielder, bystander, until);
			helper.assertTrue(!HiltSetEffects.consumeMark(wielder, foe, t) && HiltSetEffects.consumeMark(wielder, bystander, t), "a new parry moves the mark");

			// The Long Hymn: every choirPulseHits-th hit within the window releases the pulse, and the count starts again.
			int hits = Math.max(1, b.choirPulseHits);
			helper.assertTrue(b.choirPulseWindowTicks >= hits, "precondition: the window fits a hit a tick");
			UUID singer = cleanup.wielder(UUID.randomUUID());

			for (int round = 0; round < 2; round++) {
				for (int i = 1; i <= hits; i++) {
					boolean pulse = HiltSetEffects.countHymnHit(singer, t + (long) round * hits + i, b);
					helper.assertTrue(pulse == (i == hits), "round " + (round + 1) + ", hit " + i + (i == hits ? " releases the pulse" : " does not"));
				}
			}

			if (hits > 1) {
				UUID slow = cleanup.wielder(UUID.randomUUID());

				for (int i = 0; i < hits * 2; i++) {
					helper.assertTrue(!HiltSetEffects.countHymnHit(slow, t + (long) i * b.choirPulseWindowTicks, b), "hits a whole window apart never add up");
				}
			}

			// Contrition: a foe under penitentLowHealthFraction of its health.
			float max = 20f;
			float threshold = max * b.penitentLowHealthFraction;
			helper.assertTrue(HiltSetEffects.contrition(threshold - 0.01f, max, b) && !HiltSetEffects.contrition(threshold, max, b)
					&& !HiltSetEffects.contrition(max, max, b) && !HiltSetEffects.contrition(1f, 0f, b), "Contrition: under the threshold only");

			// Vigil's guard regeneration: the full Watchman hilt held; not two parts, not a saberstaff (which has two at most).
			UUID owner = UUID.randomUUID();
			ItemStack watchman = saber(owner, Emitter.WATCHMAN, Grip.WATCHMAN, Pommel.WATCHMAN, all(HiltMaterial.AURELITE));
			ItemStack twoParts = saber(owner, Emitter.WATCHMAN, Grip.WATCHMAN, Pommel.WEIGHTED, all(HiltMaterial.AURELITE));
			ItemStack staff = staff(owner, Emitter.WATCHMAN, Grip.WATCHMAN, HiltMaterials.DEFAULT);
			helper.assertTrue(near(HiltSetEffects.guardRegenMultiplier(watchman, b), ratio(b.watchmanRegenPct)) && HiltSetEffects.guardRegenMultiplier(twoParts, b) == 1f
					&& HiltSetEffects.guardRegenMultiplier(staff, b) == 1f && HiltSetEffects.guardRegenMultiplier(ItemStack.EMPTY, b) == 1f,
					"Vigil: a full Watchman hilt's guard comes back watchmanRegenPct faster");
			helper.assertTrue(HiltSetEffects.fullSet(ItemStack.EMPTY) == null && HiltSetEffects.fullSet(SaberItems.partStack(Emitter.WATCHMAN)) == null,
					"no saber, no set");

			// Forgetting a wielder (logout, respawn) ends its mark and starts its count again.
			HiltSetEffects.mark(wielder, foe, until);
			HiltSetEffects.countHymnHit(wielder, t, b);
			HiltSetEffects.forget(wielder);
			helper.assertTrue(!HiltSetEffects.consumeMark(wielder, foe, t), "forgetting a wielder ends its mark");

			for (int i = 1; i < hits; i++) {
				helper.assertTrue(!HiltSetEffects.countHymnHit(wielder, t + i, b), "forgetting a wielder starts its count again");
			}
		});
	}

	@GameTest(maxTicks = 20)
	public void tooltipLines(GameTestHelper helper) {
		// 3.4: a part in a material carries its name ("Starfall Vanguard Emitter"), steel keeps the plain one, and a name
		// that already carries one (a legendary part's gold name, baked from its name) is not prefixed twice.
		Component base = Component.translatable("item.oldrepublic.emitter_vanguard");
		Component named = HiltTraits.partName(HiltMaterial.STARFALL, base);
		helper.assertTrue(HiltTraits.partName(HiltMaterial.STEEL, base) == base && key(named).equals(NAMED_KEY), "a material part's name, a steel part's");
		Component gold = named.copy().withStyle(ChatFormatting.GOLD);
		helper.assertTrue(HiltTraits.partName(HiltMaterial.STARFALL, gold) == gold && HiltTraits.partName(HiltMaterial.HYMNSTEEL, named) == named,
				"a name that already carries a material keeps it");
		helper.assertTrue(key(SaberItems.EMITTER_VANGUARD.getName(SaberItems.partStack(Emitter.VANGUARD, HiltMaterial.STARFALL))).equals(NAMED_KEY)
				&& !key(SaberItems.EMITTER_VANGUARD.getName(SaberItems.partStack(Emitter.VANGUARD))).equals(NAMED_KEY), "a loose part's own name");

		helper.assertTrue(keys(out -> HiltTraits.materialLines(HiltParts.Slot.EMITTER, HiltMaterial.STARFALL, out))
				.equals(List.of("saber.oldrepublic.material.line", "saber.oldrepublic.trait.line")), "a starfall part: its material's line, then its trait");
		helper.assertTrue(keys(out -> HiltTraits.materialLines(HiltParts.Slot.GRIP, HiltMaterial.DURASTEEL, out)).equals(List.of("saber.oldrepublic.material.line"))
				&& keys(out -> HiltTraits.materialLines(HiltParts.Slot.POMMEL, HiltMaterial.STEEL, out)).equals(List.of("saber.oldrepublic.material.line.plain")),
				"durasteel has no trait; steel shows its name and tier only");
		helper.assertTrue(keys(out -> HiltSetEffects.partLines(Grip.WATCHMAN, out)).equals(List.of("saber.oldrepublic.set.part", "saber.oldrepublic.set.two",
				"saber.oldrepublic.set.three")) && keys(out -> HiltSetEffects.partLines(Grip.BANDED, out)).isEmpty(), "a set part names its set and both bonuses");

		UUID owner = UUID.randomUUID();
		SaberConfig full = SaberComponents.config(saber(owner, Emitter.CHOIR, Grip.CHOIR, Pommel.CHOIR, all(HiltMaterial.HYMNSTEEL)));
		SaberConfig two = SaberComponents.config(saber(owner, Emitter.CHOIR, Grip.WRAPPED, Pommel.CHOIR, HiltMaterials.DEFAULT));
		helper.assertTrue(keys(out -> HiltSetEffects.saberLines(full, false, out)).size() == 3 && keys(out -> HiltSetEffects.saberLines(two, false, out)).size() == 2
				&& keys(out -> HiltSetEffects.saberLines(full, true, out)).size() == 2,
				"a saber shows its set and the bonuses that count: both with three parts, the first with two (or on a saberstaff)");
		helper.assertTrue(keys(out -> HiltTraits.saberTraitLines(full, false, out)).equals(List.of("saber.oldrepublic.trait.saber_line"))
				&& keys(out -> HiltTraits.saberTraitLines(two, false, out)).isEmpty(), "a saber shows one line for each material with a trait");
		helper.assertTrue(HiltTraits.number(4f).equals("4") && HiltTraits.number(0.5f).equals("0.5") && HiltTraits.number(0.15f).equals("0.15")
				&& HiltTraits.number(7.5f).equals("7.5") && HiltTraits.number(0.2f).equals("0.2"), "numbers show without trailing zeros");
		helper.succeed();
	}

	// ---------------------------------------------------------------- traits and set effects in play

	@GameTest(maxTicks = 20)
	public void forceCostAndDamageFromTheHilt(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		Cleanup cleanup = new Cleanup();

		now(helper, cleanup, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer player = listed(helper, cleanup, new BlockPos(1, 1, 1));
			UUID id = player.getUUID();
			ProgressionBalance pb = Progression.balance();
			float before = ForceModifiers.cost(player, SOME_POWER);
			helper.assertTrue(HiltTraits.forceCostMultiplier(player, b) == 1f, "no saber: no change");

			// Aurelite and the hilt's own Force cost (the Choir emitter, the Domed pommel) add up, while lit in the main hand.
			ItemStack choir = hold(player, InteractionHand.MAIN_HAND, saber(id, Emitter.CHOIR, Grip.RIDGED, Pommel.DOMED, all(HiltMaterial.AURELITE)));
			float hiltPct = -(b.choirEmitterForceCostPct + b.domedForceCostPct);
			float mult = HiltTraits.forceCostMultiplier(3, hiltPct, b);
			helper.assertTrue(near(SaberStats.of(choir, b).forceCostPct(), hiltPct), "the hilt's own Force cost: the Choir emitter's and the Domed pommel's");
			helper.assertTrue(before * mult >= pb.modifierCostMin && before <= pb.modifierCostMax, "precondition: the cost clamp leaves room for the hilt");
			helper.assertTrue(near(ForceModifiers.cost(player, SOME_POWER), before), "a retracted saber changes nothing");
			lit(choir, level);
			helper.assertTrue(near(HiltTraits.forceCostMultiplier(player, b), mult) && near(ForceModifiers.cost(player, SOME_POWER), before * mult),
					"a lit aurelite Choir/Domed saber: powers cost x" + mult);
			choir.set(SaberComponents.SABER_THROWN, id);
			helper.assertTrue(near(ForceModifiers.cost(player, SOME_POWER), before), "a thrown saber is not in hand");
			choir.remove(SaberComponents.SABER_THROWN);
			hold(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			hold(player, InteractionHand.OFF_HAND, choir);
			helper.assertTrue(near(ForceModifiers.cost(player, SOME_POWER), before), "the main hand's saber only");
			hold(player, InteractionHand.OFF_HAND, ItemStack.EMPTY);

			// The Choir's two parts (7.2): the set's cost with the emitter's own.
			hold(player, InteractionHand.MAIN_HAND, lit(saber(id, Emitter.CHOIR, Grip.CHOIR, Pommel.WEIGHTED, HiltMaterials.DEFAULT), level));
			float choirTwo = HiltTraits.forceCostMultiplier(0, -(b.choirEmitterForceCostPct + b.choirSet2CostPct), b);
			helper.assertTrue(before * choirTwo >= pb.modifierCostMin && near(ForceModifiers.cost(player, SOME_POWER), before * choirTwo),
					"the Choir's two parts: powers cost choirSet2CostPct less, with the emitter's own");

			// Hymnsteel: Force power damage by the lit saber's wielder, through DamageModifiers (whose clamp holds).
			LivingEntity victim = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
			helper.assertTrue(victim != null, "precondition: a zombie is made");
			DamageSource lightning = level.damageSources().source(ForceDamage.LIGHTNING, player, player);
			ItemStack hymn = hold(player, InteractionHand.MAIN_HAND, saber(id, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, all(HiltMaterial.HYMNSTEEL)));
			float plain = DamageModifiers.apply(victim, lightning, 10f);
			lit(hymn, level);
			float boosted = DamageModifiers.apply(victim, lightning, 10f);
			float hymnMult = HiltTraits.forceDamageMultiplier(3, b);
			helper.assertTrue(plain / 10f * hymnMult <= pb.damageModifierMax && plain / 10f >= pb.damageModifierMin,
					"precondition: the damage clamp leaves room for hymnsteel");
			helper.assertTrue(near(hymnMult, ratio(3 * b.hymnsteelForceDamagePct)) && near(boosted / plain, hymnMult),
					"a lit hymnsteel saber: Force power damage hymnsteelForceDamagePct higher per part (got x" + boosted / plain + ")");
			helper.assertTrue(HiltTraits.forceDamageMultiplier(victim, b) == 1f, "only a player's lit saber counts");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void hitsRestoreEnergyAndContrition(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		Cleanup cleanup = new Cleanup();

		now(helper, cleanup, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer player = listed(helper, cleanup, new BlockPos(12, 1, 3));
			UUID id = player.getUUID();
			Mob target = dummy(helper, cleanup, new BlockPos(12, 1, 5));
			Mob foe = dummy(helper, cleanup, new BlockPos(14, 1, 5));
			helper.assertTrue(Progression.get(player).maxEnergy - LOW_ENERGY > 2 * (3 * b.duskglassEnergyOnHit + b.penitentEnergyOnHit),
					"precondition: room below the maximum energy");

			// Duskglass: each hit that lands restores duskglassEnergyOnHit per part (against a steel saber's hit).
			hold(player, InteractionHand.MAIN_HAND, lit(saber(id, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, HiltMaterials.DEFAULT), level));
			float steel = energyFromHit(helper, player, target, SaberCombat.Strike.LIGHT);
			hold(player, InteractionHand.MAIN_HAND, lit(saber(id, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, all(HiltMaterial.DUSKGLASS)), level));
			float duskglass = energyFromHit(helper, player, target, SaberCombat.Strike.LIGHT);
			helper.assertTrue(near(duskglass - steel, 3 * b.duskglassEnergyOnHit),
					"a duskglass hilt: each hit restores duskglassEnergyOnHit per part (got " + (duskglass - steel) + ")");

			// A dual wielder: the off-hand saber's trait counts for its own hits, the main saber's for the main hand's.
			hold(player, InteractionHand.MAIN_HAND, lit(saber(id, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, HiltMaterials.DEFAULT), level));
			hold(player, InteractionHand.OFF_HAND, lit(saber(id, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, all(HiltMaterial.DUSKGLASS)), level));
			float offhand = energyFromHit(helper, player, target, SaberCombat.Strike.OFFHAND);
			float mainHand = energyFromHit(helper, player, target, SaberCombat.Strike.LIGHT);
			helper.assertTrue(near(offhand - mainHand, 3 * b.duskglassEnergyOnHit), "an off-hand hit counts the off saber's duskglass, a main-hand hit the main saber's");
			hold(player, InteractionHand.OFF_HAND, ItemStack.EMPTY);

			// Contrition, the full Penitent set (in clan iron, as Ulvaine drops it): Force energy per hit, and more damage to a
			// foe under the threshold. Two parts are the control.
			hold(player, InteractionHand.MAIN_HAND, lit(saber(id, Emitter.PENITENT, Grip.PENITENT, Pommel.WEIGHTED, all(HiltMaterial.CLAN_IRON)), level));
			float twoGain = energyFromHit(helper, player, target, SaberCombat.Strike.LIGHT);
			double twoRatio = lowHealthRatio(helper, player, foe, b);
			hold(player, InteractionHand.MAIN_HAND, lit(saber(id, Emitter.PENITENT, Grip.PENITENT, Pommel.PENITENT, all(HiltMaterial.CLAN_IRON)), level));
			float fullGain = energyFromHit(helper, player, target, SaberCombat.Strike.LIGHT);
			double fullRatio = lowHealthRatio(helper, player, foe, b);
			helper.assertTrue(near(fullGain - twoGain, b.penitentEnergyOnHit), "Contrition: each hit restores penitentEnergyOnHit (got " + (fullGain - twoGain) + ")");
			helper.assertTrue(near(fullRatio / twoRatio, ratio(b.penitentLowHealthPct)),
					"Contrition: penitentLowHealthPct more to a foe under the threshold (got x" + fullRatio / twoRatio + ")");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void longHymnPulse(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		Cleanup cleanup = new Cleanup();

		now(helper, cleanup, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer player = listed(helper, cleanup, new BlockPos(12, 1, 3));
			Mob struck = dummy(helper, cleanup, new BlockPos(12, 1, 5));
			Mob nearby = dummy(helper, cleanup, new BlockPos(14, 1, 3));
			Mob far = dummy(helper, cleanup, new BlockPos(12, 1, 10));
			int hits = b.choirPulseHits;
			double radius = b.choirPulseRadius;
			Vec3 chest = player.position().add(0, player.getBbHeight() * 0.6, 0);
			helper.assertTrue(hits >= 2 && hits * 2 * HIT < struck.getHealth(), "precondition: a pulse takes several hits, and the struck dummy lives through two");
			helper.assertTrue(nearby.getBoundingBox().distanceToSqr(chest) <= radius * radius && far.getBoundingBox().distanceToSqr(chest) > radius * radius,
					"precondition: one dummy stands within the pulse's radius, one beyond it");

			ItemStack choir = hold(player, InteractionHand.MAIN_HAND,
					lit(saber(player.getUUID(), Emitter.CHOIR, Grip.CHOIR, Pommel.CHOIR, all(HiltMaterial.HYMNSTEEL)), level));
			helper.assertTrue(HiltSetEffects.fullSet(choir) == HiltSet.CHOIR, "precondition: a full Choir hilt");
			float pulse = DamageModifiers.apply(nearby, level.damageSources().source(HymnDamage.HYMN, null, player), b.choirPulseDamage);
			float farHealth = far.getHealth();

			for (int round = 1; round <= 2; round++) {
				float nearHealth = nearby.getHealth();
				nearby.invulnerableTime = 0;
				nearby.setDeltaMovement(Vec3.ZERO);
				float perHit = 0f;

				for (int i = 1; i < hits; i++) {
					perHit = hit(helper, player, struck, SaberCombat.Strike.LIGHT);
				}

				helper.assertTrue(nearby.getHealth() == nearHealth, "round " + round + ": no pulse before hit " + hits);
				float last = hit(helper, player, struck, SaberCombat.Strike.LIGHT);
				float lost = nearHealth - nearby.getHealth();
				helper.assertTrue(Math.abs(lost - pulse) < 1.0e-3f, "round " + round + ": hit " + hits + " releases a pulse of " + pulse + "; the nearby dummy lost " + lost);
				helper.assertTrue(Math.abs(last - perHit) < 1.0e-3f, "round " + round + ": the struck foe takes its hit and not the pulse");
				helper.assertTrue(nearby.getDeltaMovement().horizontal().dot(nearby.position().subtract(player.position()).horizontal()) > 0.0,
						"round " + round + ": the pulse pushes the nearby dummy away");
			}

			helper.assertTrue(far.getHealth() == farHealth, "the dummy beyond the radius is untouched");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void guardsMarkDrainAndSlow(GameTestHelper helper) {
		SaberBalance b = SaberToggle.balance();
		Cleanup cleanup = new Cleanup();

		now(helper, cleanup, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos guardAt = new BlockPos(12, 1, 3);
			Mob foe = dummy(helper, cleanup, new BlockPos(12, 1, 5));
			Mob brute = dummy(helper, cleanup, new BlockPos(13, 1, 6));
			DamageSource parried = level.damageSources().mobAttack(foe);
			DamageSource blocked = level.damageSources().mobAttack(brute);

			// Curator's Eye: a full Collector hilt's parry marks the attacker; the next saber hit on it deals
			// collectorMarkDamagePct more, once. Two parts mark nothing.
			GuardingPlayer collector = guard(helper, cleanup, guardAt, 0, Emitter.RELIQUARY, Grip.RELIQUARY, Pommel.ARCHIVIST,
					new HiltMaterials(HiltMaterial.STEEL, HiltMaterial.STARFALL, HiltMaterial.STEEL));
			helper.assertTrue(SaberCombat.modifyIncomingDamage(collector, level, parried, BLOW, null) == 0f, "precondition: the Collector's guard parries the blow");
			float marked = hit(helper, collector, foe, SaberCombat.Strike.LIGHT);
			float plain = hit(helper, collector, foe, SaberCombat.Strike.LIGHT);
			helper.assertTrue(plain > 0f && near(marked / plain, ratio(b.collectorMarkDamagePct)),
					"Curator's Eye: the marked hit deals x" + marked / plain + ", then the mark is gone");
			GuardingPlayer twoParts = guard(helper, cleanup, guardAt, 0, Emitter.RELIQUARY, Grip.RELIQUARY, Pommel.WEIGHTED, HiltMaterials.DEFAULT);
			helper.assertTrue(SaberCombat.modifyIncomingDamage(twoParts, level, parried, BLOW, null) == 0f, "precondition: the two-part guard parries the blow");
			float first = hit(helper, twoParts, foe, SaberCombat.Strike.LIGHT);
			float second = hit(helper, twoParts, foe, SaberCombat.Strike.LIGHT);
			helper.assertTrue(near(first, second), "two Collector parts mark nothing");

			// Blocks: clan iron drains the guard clanIronGuardDrainPct less per part, the Warden emitter wardenGuardDrainPct.
			GuardingPlayer steel = guard(helper, cleanup, guardAt, LONG_GUARD_TICKS, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, HiltMaterials.DEFAULT);
			float steelDrain = blockDrain(helper, steel, level, blocked);
			helper.assertTrue(brute.getEffect(MobEffects.SLOWNESS) == null, "a plain hilt's block slows nothing");
			GuardingPlayer clan = guard(helper, cleanup, guardAt, LONG_GUARD_TICKS, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, all(HiltMaterial.CLAN_IRON));
			float clanDrain = blockDrain(helper, clan, level, blocked);
			GuardingPlayer warden = guard(helper, cleanup, guardAt, LONG_GUARD_TICKS, Emitter.WARDEN, Grip.WRAPPED, Pommel.WEIGHTED, HiltMaterials.DEFAULT);
			float wardenDrain = blockDrain(helper, warden, level, blocked);
			helper.assertTrue(near(clanDrain / steelDrain, HiltTraits.guardDrainMultiplier(3, 0f, b))
					&& near(wardenDrain / steelDrain, HiltTraits.guardDrainMultiplier(0, b.wardenGuardDrainPct, b)),
					"a block drains x" + clanDrain / steelDrain + " with clan iron, x" + wardenDrain / steelDrain + " with the Warden emitter");

			// Vigil: a full Watchman hilt's block slows the attacker.
			GuardingPlayer watchman = guard(helper, cleanup, guardAt, LONG_GUARD_TICKS, Emitter.WATCHMAN, Grip.WATCHMAN, Pommel.WATCHMAN, all(HiltMaterial.AURELITE));
			blockDrain(helper, watchman, level, blocked);
			MobEffectInstance slow = brute.getEffect(MobEffects.SLOWNESS);
			helper.assertTrue(slow != null && slow.getAmplifier() == b.watchmanSlowAmplifier && slow.getDuration() == b.watchmanSlowTicks,
					"Vigil: the blocked attacker is slowed for watchmanSlowTicks");
		});
	}
}
