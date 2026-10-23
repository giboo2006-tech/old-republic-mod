package com.oldrepublic.gametest;

import java.util.List;

import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.equipment.EquipmentM4Balance;
import com.oldrepublic.equipment.EquipmentM4Balance.ModNumbers;
import com.oldrepublic.equipment.blaster.BlasterBalance;
import com.oldrepublic.equipment.blaster.BlasterHeat;
import com.oldrepublic.equipment.blaster.BlasterItem;
import com.oldrepublic.equipment.blaster.BlasterRules;
import com.oldrepublic.equipment.blaster.Blasters;
import com.oldrepublic.equipment.mod.ModEffects;
import com.oldrepublic.equipment.mod.ModSlot;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.M7Items;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The Milestone 7 blasters (spec MILESTONE_7.md 9.1, 9.2 and 14, WB): the scatter blaster's and the
 * carbine's numbers, a pre-Milestone 7 balance file padded for them, the scatter's pellets (one blast,
 * one heat, all landing on one target), the carbine's burst (its bolts on the tick, its heat shared out,
 * cut short by an overheat or a vent), and the Aurelite Barrel and the Duskglass Cell.
 *
 * <p>Test rules as {@code GearGameTests}: every test declares {@code maxTicks}; players are listed mock
 * players removed at the end; nothing global is written. The pinned numbers read
 * {@code new BlasterBalance()} (the plan's table); the in-world checks read the live section, so a tuned
 * file still passes them. Shots go through the same hook the input uses ({@code Blasters.tryFire}), the
 * burst's later bolts through {@code Blasters.tickFor} at explicit ticks, and pellet hits through
 * {@code hurtServer} with the pellet's own damage source, as its flight would land them.
 */
public class BlasterM7GameTests {
	private static final double EPS = 1.0e-3;
	/** How far around the shooter fired bolts are looked for (and discarded at the end). */
	private static final double BOLT_SEARCH = 24.0;
	/** A tick far from zero for the pure rules, so no subtraction goes negative. */
	private static final long T0 = 10_000L;
	/** The player's spot in the fixtures; targets stand south of it (+z), the way it faces. */
	private static final BlockPos PLAYER_AT = new BlockPos(2, 1, 1);

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < EPS;
	}

	// ---------------------------------------------------------------- fixtures

	/** A listed player at {@link #PLAYER_AT}, on the ground, facing south (+z) and level, standing still. */
	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper, PLAYER_AT);
		player.setGameMode(GameType.SURVIVAL);
		player.setYRot(0f);
		player.setXRot(0f);
		player.setYHeadRot(0f);
		player.setOnGround(true);
		player.xo = player.getX();
		player.zo = player.getZ();
		return player;
	}

	/** Holds a blaster drawn long ago (no draw delay); returns the held stack. */
	private static ItemStack drawn(ServerPlayer player, ItemStack gun) {
		player.setItemInHand(InteractionHand.MAIN_HAND, gun);
		ItemStack held = player.getMainHandItem();
		Blasters.drawnLongAgo(player, held);
		return held;
	}

	/** The bolts {@code shooter} owns near them. */
	private static List<BlasterBolt> bolts(ServerPlayer shooter) {
		return shooter.level().getEntitiesOfClass(BlasterBolt.class, shooter.getBoundingBox().inflate(BOLT_SEARCH), bolt -> bolt.getOwner() == shooter);
	}

	/** Drops the player's blaster state and bolts, then logs them out. */
	private static void done(ServerPlayer player) {
		Blasters.forget(player);
		bolts(player).forEach(Entity::discard);
		M3TestPlayers.remove(player);
	}

	private static DamageSource boltSource(ServerLevel level, BlasterBolt bolt, Entity shooter) {
		return level.damageSources().source(EnemyEntities.BLASTER_DAMAGE, bolt, shooter);
	}

	/** The angle in degrees between two directions. */
	private static double degreesBetween(Vec3 a, Vec3 b) {
		double cos = a.normalize().dot(b.normalize());
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
	}

	// ---------------------------------------------------------------- numbers (9.1, 9.2)

	@GameTest(maxTicks = 20)
	public void newKindsNumbers(GameTestHelper helper) {
		BlasterBalance d = new BlasterBalance();
		BlasterItem.Kind scatter = BlasterItem.Kind.SCATTER;
		BlasterItem.Kind carbine = BlasterItem.Kind.CARBINE;

		helper.assertTrue(near(BlasterBalance.of(d.damage, scatter), 1.6) && d.scatterPellets == 6 && near(BlasterBalance.of(d.hipSpread, scatter), 8.0)
				&& near(BlasterBalance.of(d.speed, scatter), 1.8) && BlasterBalance.of(d.life, scatter) == 8 && BlasterBalance.of(d.rate, scatter) == 18,
				msg("scatter: 6 pellets of 1.6 in 8 degrees, bolts 1.8 x 8 ticks, a blast every 18 ticks"));
		helper.assertTrue(near(BlasterBalance.of(d.heatPerShot, scatter), 28) && near(BlasterBalance.of(d.cooling, scatter), 2.5)
				&& BlasterBalance.of(d.overheatLock, scatter) == 70 && BlasterBalance.of(d.ventLock, scatter) == 25
				&& BlasterBalance.of(d.durability, scatter) == 700 && near(d.scatterSlowPct, 15),
				msg("scatter: heat 28, cooling 2.5, overheat 70, vent 25, durability 700, movement -15%"));
		helper.assertTrue(near(BlasterBalance.of(d.damage, carbine), 2.6) && d.burstSize == 3 && d.burstGap == 2 && BlasterBalance.of(d.rate, carbine) == 14
				&& near(BlasterBalance.of(d.hipSpread, carbine), 1.2) && near(BlasterBalance.of(d.speed, carbine), 2.5) && BlasterBalance.of(d.life, carbine) == 50,
				msg("carbine: 3-bolt bursts of 2.6, 2 ticks apart, a burst every 14 ticks, spread 1.2, bolts 2.5 x 50"));
		helper.assertTrue(near(BlasterBalance.of(d.heatPerShot, carbine), 20) && near(BlasterBalance.of(d.cooling, carbine), 2.2)
				&& BlasterBalance.of(d.overheatLock, carbine) == 70 && BlasterBalance.of(d.ventLock, carbine) == 25
				&& BlasterBalance.of(d.durability, carbine) == 750,
				msg("carbine: heat 20 a burst, cooling 2.2, overheat 70, vent 25, durability 750"));
		helper.assertTrue(near(d.aureliteSpreadPct, -30) && near(d.aureliteDamagePct, 5) && near(d.duskglassHeatPct, -20),
				msg("aurelite barrel: spread -30%, bolt damage +5%; duskglass cell: heat per shot -20%"));

		// The old kinds keep their numbers.
		helper.assertTrue(near(BlasterBalance.of(d.damage, BlasterItem.Kind.PISTOL), 4.0) && near(BlasterBalance.of(d.damage, BlasterItem.Kind.RIFLE), 5.0)
				&& near(BlasterBalance.of(d.damage, BlasterItem.Kind.REPEATER), 2.5) && BlasterBalance.of(d.rate, BlasterItem.Kind.REPEATER) == 3,
				msg("pistol, rifle and repeater unchanged"));

		// Registered items carry the registration defaults.
		ItemStack scatterGun = new ItemStack(M7Items.SCATTER_BLASTER);
		ItemStack carbineGun = new ItemStack(M7Items.BLASTER_CARBINE);
		helper.assertTrue(scatterGun.getMaxDamage() == 700 && carbineGun.getMaxDamage() == 750, msg("durability 700 and 750"));
		// The same float arithmetic as BlasterItem's, so the record compares exactly.
		// API-CHECK: UseEffects is a record (component-wise equals) and DataComponents.USE_EFFECTS reads back what
		// BlasterItem set (both are BlasterItem's own M4 usage; the equality is new).
		helper.assertTrue(new UseEffects(true, true, 1f - d.scatterSlowPct / 100f).equals(scatterGun.get(DataComponents.USE_EFFECTS)),
				msg("the scatter slows its holder 15% while firing and lets them sprint"));
		helper.assertTrue(new UseEffects(true, true, 1f).equals(carbineGun.get(DataComponents.USE_EFFECTS)),
				msg("the carbine does not slow its holder"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void oldBalanceFilePadsNewKinds(GameTestHelper helper) {
		BlasterBalance d = new BlasterBalance();
		BlasterBalance old = new BlasterBalance();
		// A file written before Milestone 7: three entries, one tuned by hand.
		old.damage = new float[] {4.5f, 5.0f, 2.5f};
		old.rate = new int[] {8, 10, 3};
		old.heatPerShot = new float[] {15f, 14f, 7f};
		old.durability = new int[] {600};
		old.fillNewKinds();

		helper.assertTrue(old.damage.length == BlasterItem.Kind.values().length && near(old.damage[0], 4.5),
				msg("the tuned entries stay and the array grows to every kind"));
		helper.assertTrue(near(BlasterBalance.of(old.damage, BlasterItem.Kind.SCATTER), BlasterBalance.of(d.damage, BlasterItem.Kind.SCATTER))
				&& BlasterBalance.of(old.rate, BlasterItem.Kind.CARBINE) == BlasterBalance.of(d.rate, BlasterItem.Kind.CARBINE)
				&& near(BlasterBalance.of(old.heatPerShot, BlasterItem.Kind.CARBINE), BlasterBalance.of(d.heatPerShot, BlasterItem.Kind.CARBINE)),
				msg("the scatter and the carbine take their own defaults, not the repeater's numbers"));
		helper.assertTrue(old.durability.length == 1, msg("a shorter hand-edited array keeps the last-value rule"));

		float[] before = old.damage;
		old.fillNewKinds();
		helper.assertTrue(old.damage == before, msg("padding twice changes nothing"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the rules

	@GameTest(maxTicks = 20)
	public void pelletAndBurstRules(GameTestHelper helper) {
		BlasterBalance d = new BlasterBalance();

		for (BlasterItem.Kind kind : BlasterItem.Kind.values()) {
			int pellets = kind == BlasterItem.Kind.SCATTER ? d.scatterPellets : 1;
			int burst = kind == BlasterItem.Kind.CARBINE ? d.burstSize : 1;
			helper.assertTrue(BlasterRules.pellets(kind, d) == pellets && BlasterRules.burst(kind, d) == burst,
					msg(kind + ": " + pellets + " pellets a blast, " + burst + " bolts a pull"));
			helper.assertTrue(near(BlasterRules.heatPerBolt(kind, d) * burst, BlasterBalance.of(d.heatPerShot, kind)),
					msg(kind + ": a whole pull adds the kind's heat per shot"));
		}

		helper.assertTrue(near(BlasterRules.pullDamage(BlasterItem.Kind.SCATTER, 1f, d), 9.6), msg("point blank, a scatter blast is 9.6"));
		helper.assertTrue(near(BlasterRules.pullDamage(BlasterItem.Kind.CARBINE, 1f, d), 7.8), msg("a carbine burst is 7.8"));
		helper.assertTrue(!BlasterRules.burstBoltDue(T0, 1, T0 + d.burstGap - 1, d) && BlasterRules.burstBoltDue(T0, 1, T0 + d.burstGap, d)
				&& !BlasterRules.burstBoltDue(T0, 2, T0 + 2L * d.burstGap - 1, d) && BlasterRules.burstBoltDue(T0, 2, T0 + 2L * d.burstGap, d),
				msg("a burst's bolts are burstGap ticks apart"));

		// Back to back carbine bolts overheat on the last bolt of the fifth burst (15 thirds of 20 are 99.99999 in floats).
		BlasterItem.Kind carbine = BlasterItem.Kind.CARBINE;
		BlasterHeat h = BlasterHeat.COLD;
		int bolts = 0;

		while (bolts < 100) {
			h = BlasterRules.afterShot(h, T0 + bolts, BlasterRules.heatPerBolt(carbine, d), carbine, d);
			bolts++;

			if (BlasterRules.overheated(h, T0 + bolts - 1)) {
				break;
			}
		}

		int expected = (int) Math.ceil(BlasterRules.MAX_HEAT / BlasterBalance.of(d.heatPerShot, carbine)) * d.burstSize;
		helper.assertTrue(bolts == expected, msg("the carbine overheats on bolt " + expected + ", got " + bolts));

		// The scatter's pellets keep inside the cone wherever their slice of it is.
		ServerPlayer player = player(helper);

		try {
			for (int i = 0; i < 12; i++) {
				Vec3 dir = Blasters.cone(player, 8f, 1.0, i * Math.PI / 6.0);
				double off = degreesBetween(dir, player.getLookAngle());
				helper.assertTrue(off <= 8.0 + 0.05 && off >= 8.0 - 0.3, msg("a pellet at the cone's edge is 8 degrees off the look, got " + off));
			}

			helper.assertTrue(degreesBetween(Blasters.cone(player, 8f, 0.0, 1.0), player.getLookAngle()) < 0.01, msg("and at its centre along the look"));
		} finally {
			done(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the scatter blaster

	@GameTest(maxTicks = 20)
	public void scatterBlastIsSixPellets(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlasterBalance b = BlasterBalance.get();
		BlasterItem.Kind kind = BlasterItem.Kind.SCATTER;
		ServerPlayer player = player(helper);

		try {
			ItemStack gun = drawn(player, new ItemStack(M7Items.SCATTER_BLASTER));
			BlasterItem item = (BlasterItem) gun.getItem();
			long now = level.getGameTime();
			helper.assertTrue(Blasters.tryFire(player, gun, item, now), msg("the scatter fires"));

			List<BlasterBolt> pellets = bolts(player);
			helper.assertTrue(pellets.size() == b.scatterPellets, msg("one blast is " + b.scatterPellets + " pellets, found " + pellets.size()));
			float spread = BlasterBalance.of(b.hipSpread, kind);

			for (BlasterBolt pellet : pellets) {
				helper.assertTrue(near(pellet.damage(), BlasterBalance.of(b.damage, kind)) && pellet.style() == BlasterBolt.Style.PLAYER && !pellet.heavy(),
						msg("each pellet is a player bolt of " + BlasterBalance.of(b.damage, kind)));
				helper.assertTrue(Math.abs(pellet.getDeltaMovement().length() - BlasterBalance.of(b.speed, kind)) < 0.01,
						msg("at the scatter's bolt speed: " + pellet.getDeltaMovement().length()));
				helper.assertTrue(degreesBetween(pellet.getDeltaMovement(), player.getLookAngle()) <= spread + 0.05,
						msg("inside the " + spread + " degree spread"));
			}

			helper.assertTrue(near(Blasters.heat(gun).heat(), BlasterBalance.of(b.heatPerShot, kind)), msg("the blast adds its heat once: " + Blasters.heat(gun)));
			helper.assertTrue(gun.getDamageValue() == 1, msg("and one durability"));

			int rate = BlasterBalance.of(b.rate, kind);
			helper.assertTrue(!Blasters.tryFire(player, gun, item, now + rate - 1), msg("no blast before the rate"));
			helper.assertTrue(Blasters.tryFire(player, gun, item, now + rate) && bolts(player).size() == 2 * b.scatterPellets, msg("the next blast at the rate"));
		} finally {
			done(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void scatterPelletsAllLandPointBlank(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlasterBalance b = BlasterBalance.get();
		ServerPlayer player = player(helper);
		Mob target = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(2, 1, 3));
		Mob control = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(4, 1, 3));

		try {
			ItemStack gun = drawn(player, new ItemStack(M7Items.SCATTER_BLASTER));
			helper.assertTrue(Blasters.tryFire(player, gun, (BlasterItem) gun.getItem(), level.getGameTime()), msg("the scatter fires"));
			List<BlasterBolt> pellets = bolts(player);
			helper.assertTrue(pellets.size() == b.scatterPellets, msg("precondition: a blast of " + b.scatterPellets + " pellets"));

			// Every pellet strikes the one target in the same tick, as a point-blank blast lands them.
			float start = target.getHealth();
			float first = -1f;

			for (BlasterBolt pellet : pellets) {
				target.hurtServer(level, boltSource(level, pellet, player), pellet.damage());
				pellet.discard();

				if (first < 0f) {
					first = start - target.getHealth();
				}
			}

			float total = start - target.getHealth();
			helper.assertTrue(first > 0f, msg("the first pellet lands"));
			helper.assertTrue(Math.abs(total - first * b.scatterPellets) < 0.01, msg("all " + b.scatterPellets + " land in full: " + total + " for " + first + " each"));
			helper.assertTrue(target.invulnerableTime > 10, msg("the last pellet leaves the usual hurt cooldown"));

			// A lone bolt (the pistol's) still meets the hurt cooldown: a second at once lands nothing.
			ItemStack pistol = drawn(player, new ItemStack(EnemyItems.BLASTER_PISTOL));
			helper.assertTrue(Blasters.tryFire(player, pistol, (BlasterItem) pistol.getItem(), level.getGameTime()), msg("the pistol fires"));
			BlasterBolt bolt = bolts(player).get(0);
			float before = control.getHealth();
			control.hurtServer(level, boltSource(level, bolt, player), bolt.damage());
			float once = before - control.getHealth();
			control.hurtServer(level, boltSource(level, bolt, player), bolt.damage());
			helper.assertTrue(once > 0f && Math.abs(before - control.getHealth() - once) < 0.01, msg("two pistol hits at once land once"));
		} finally {
			target.discard();
			control.discard();
			done(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the carbine

	@GameTest(maxTicks = 20)
	public void carbineFiresThreeBoltBursts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlasterBalance b = BlasterBalance.get();
		BlasterItem.Kind kind = BlasterItem.Kind.CARBINE;
		ServerPlayer player = player(helper);

		try {
			ItemStack gun = drawn(player, new ItemStack(M7Items.BLASTER_CARBINE));
			BlasterItem item = (BlasterItem) gun.getItem();
			float perBolt = BlasterRules.heatPerBolt(kind, b);
			long now = level.getGameTime();

			helper.assertTrue(Blasters.tryFire(player, gun, item, now) && bolts(player).size() == 1 && Blasters.burstFired(player) == 1,
					msg("a pull fires the burst's first bolt"));
			helper.assertTrue(near(Blasters.heat(gun).heat(), perBolt), msg("which adds its share of the burst's heat: " + Blasters.heat(gun)));
			helper.assertTrue(!Blasters.tryFire(player, gun, item, now + 1), msg("no new burst while one is going"));

			for (int bolt = 2; bolt <= b.burstSize; bolt++) {
				long due = now + (long) (bolt - 1) * b.burstGap;
				Blasters.tickFor(player, due - 1);
				helper.assertTrue(bolts(player).size() == bolt - 1, msg("bolt " + bolt + " not before its tick"));
				Blasters.tickFor(player, due);
				helper.assertTrue(bolts(player).size() == bolt, msg("bolt " + bolt + " " + (bolt - 1) * b.burstGap + " ticks after the first"));
			}

			helper.assertTrue(Blasters.burstFired(player) == 0, msg("the burst is over"));
			helper.assertTrue(near(Blasters.heat(gun).heat(), BlasterBalance.of(b.heatPerShot, kind)), msg("a whole burst adds the burst's heat: " + Blasters.heat(gun)));
			Blasters.tickFor(player, now + (long) b.burstSize * b.burstGap);
			helper.assertTrue(bolts(player).size() == b.burstSize, msg("and no more bolts follow"));

			for (BlasterBolt bolt : bolts(player)) {
				helper.assertTrue(near(bolt.damage(), BlasterBalance.of(b.damage, kind)) && Math.abs(bolt.getDeltaMovement().length() - BlasterBalance.of(b.speed, kind)) < 0.01,
						msg("each bolt has the carbine's damage and speed"));
			}

			int rate = BlasterBalance.of(b.rate, kind);
			helper.assertTrue(!Blasters.tryFire(player, gun, item, now + rate - 1), msg("the next burst waits for the rate, counted from the last burst's start"));
			helper.assertTrue(Blasters.tryFire(player, gun, item, now + rate) && bolts(player).size() == b.burstSize + 1, msg("the next burst starts at the rate"));
			helper.assertTrue(gun.getDamageValue() == b.burstSize + 1, msg("one durability a bolt"));
		} finally {
			done(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void carbineBurstStopsOnOverheatOrVent(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlasterBalance b = BlasterBalance.get();
		BlasterItem.Kind kind = BlasterItem.Kind.CARBINE;
		float perBolt = BlasterRules.heatPerBolt(kind, b);
		ServerPlayer player = player(helper);

		try {
			// Hot enough that the second bolt overheats it.
			ItemStack gun = drawn(player, new ItemStack(M7Items.BLASTER_CARBINE));
			BlasterItem item = (BlasterItem) gun.getItem();
			long now = level.getGameTime();
			gun.set(M4Components.BLASTER_HEAT, new BlasterHeat(BlasterRules.MAX_HEAT - 1.5f * perBolt, now, 0L, 0));
			helper.assertTrue(Blasters.tryFire(player, gun, item, now), msg("the first bolt leaves"));
			Blasters.tickFor(player, now + b.burstGap);
			helper.assertTrue(BlasterRules.overheated(Blasters.heat(gun), now + b.burstGap), msg("the second overheats it"));
			Blasters.tickFor(player, now + 2L * b.burstGap);
			helper.assertTrue(bolts(player).size() == 2 && Blasters.burstFired(player) == 0, msg("and the burst stops there: " + bolts(player).size() + " bolts"));
			done(player);

			// A vent between bolts ends the burst too.
			player = player(helper);
			gun = drawn(player, new ItemStack(M7Items.BLASTER_CARBINE));
			now = level.getGameTime();
			gun.set(M4Components.BLASTER_HEAT, new BlasterHeat(b.ventMin + 10f, now, 0L, 0));
			helper.assertTrue(Blasters.tryFire(player, gun, item, now), msg("a burst starts hot"));
			Blasters.action(player, true);
			Blasters.action(player, false);
			helper.assertTrue(BlasterRules.locked(Blasters.heat(gun), now) && near(Blasters.heat(gun).heat(), 0), msg("Weapon Action vents the carbine"));
			Blasters.tickFor(player, now + b.burstGap);
			Blasters.tickFor(player, now + 2L * b.burstGap);
			helper.assertTrue(bolts(player).size() == 1 && Blasters.burstFired(player) == 0, msg("and the burst's other bolts never leave"));
		} finally {
			done(player);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the material mods (9.2)

	@GameTest(maxTicks = 20)
	public void aureliteBarrelAndDuskglassCell(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlasterBalance b = BlasterBalance.get();
		ModNumbers m = EquipmentM4Balance.mods();
		ItemStack plain = new ItemStack(EnemyItems.BLASTER_PISTOL);
		helper.assertTrue(near(ModEffects.spread(plain, m, b), 1) && near(ModEffects.boltDamage(plain, m, b), 1) && near(ModEffects.heat(plain, m, b), 1),
				msg("no mods: no change"));

		ItemStack barrel = new ItemStack(M7Items.MOD_AURELITE_BARREL);
		ItemStack cell = new ItemStack(M7Items.MOD_DUSKGLASS_CELL);
		ItemStack modded = new ItemStack(EnemyItems.BLASTER_PISTOL);
		Mods.fit(modded, ModSlot.BARREL, barrel);
		Mods.fit(modded, ModSlot.CELL, cell);
		helper.assertTrue(Mods.has(modded, M7Items.MOD_AURELITE_BARREL) && Mods.has(modded, M7Items.MOD_DUSKGLASS_CELL) && barrel.isEmpty() && cell.isEmpty(),
				msg("the barrel and the cell fit a pistol"));
		helper.assertTrue(near(ModEffects.spread(modded, m, b), 1 + b.aureliteSpreadPct / 100.0), msg("the aurelite barrel narrows the spread " + b.aureliteSpreadPct + "%"));
		helper.assertTrue(near(ModEffects.boltDamage(modded, m, b), 1 + b.aureliteDamagePct / 100.0), msg("and adds " + b.aureliteDamagePct + "% bolt damage"));
		helper.assertTrue(near(ModEffects.heat(modded, m, b), 1 + b.duskglassHeatPct / 100.0), msg("the duskglass cell cuts heat per shot " + b.duskglassHeatPct + "%"));
		helper.assertTrue(near(ModEffects.spread(modded, m), ModEffects.spread(modded, m, b)), msg("the server form reads the same live numbers"));

		ServerPlayer player = player(helper);

		try {
			ItemStack gun = drawn(player, modded);
			BlasterItem item = (BlasterItem) gun.getItem();
			helper.assertTrue(Blasters.tryFire(player, gun, item, level.getGameTime()), msg("the modded pistol fires"));
			float expected = BlasterBalance.of(b.heatPerShot, item.kind()) * (1 + b.duskglassHeatPct / 100f);
			helper.assertTrue(near(Blasters.heat(gun).heat(), expected), msg("a shot heats it " + expected + ", got " + Blasters.heat(gun)));
			BlasterBolt bolt = bolts(player).get(0);
			helper.assertTrue(near(Blasters.modDamage(boltSource(level, bolt, player)), 1 + b.aureliteDamagePct / 100.0),
					msg("its bolt carries the barrel's damage for the damage modifier"));
		} finally {
			done(player);
		}

		helper.succeed();
	}
}
