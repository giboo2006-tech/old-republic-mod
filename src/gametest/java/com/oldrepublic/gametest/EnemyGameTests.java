package com.oldrepublic.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.PoacherCaptain;
import com.oldrepublic.enemy.PoacherLoot;
import com.oldrepublic.enemy.SithAcolyte;
import com.oldrepublic.enemy.trooper.FragGrenade;
import com.oldrepublic.enemy.trooper.PatrolBalance;
import com.oldrepublic.enemy.trooper.PatrolRules;
import com.oldrepublic.enemy.trooper.PatrolState;
import com.oldrepublic.enemy.trooper.TrooperBalance;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.ForceTags;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.duel.DuelState;
import com.oldrepublic.saber.duel.NpcGuard;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 2 enemies: the Sith Acolyte's Force Shove is cancelled by a Push during its wind-up
 * (the push clash), the Poacher Captain yields at 25% of her health instead of dying, and a lost
 * poacher emitter can drop again. Milestone 3 (W4): the frag grenade under Push and Pull, and the
 * natural patrol spawner's caps, site clearance and whole-squad despawn on a detached state.
 */
public class EnemyGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static FakePlayer player(GameTestHelper helper, BlockPos pos, String name) {
		ServerLevel level = helper.getLevel();
		FakePlayer player = FakePlayer.get(level, new GameProfile(UUID.randomUUID(), name));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(pos)));
		player.setYRot(0f);
		return player;
	}

	@GameTest(maxTicks = 20)
	public void pushDuringShoveWindupClashes(GameTestHelper helper) {
		SithAcolyte acolyte = helper.spawn(EnemyEntities.SITH_ACOLYTE, new BlockPos(4, 1, 6));
		// No AI: the wind-up stays open until the push arrives.
		acolyte.setNoAi(true);
		FakePlayer caster = player(helper, new BlockPos(4, 1, 2), "shove_test");
		long now = helper.getLevel().getGameTime();

		helper.assertTrue(acolyte.is(ForceTags.PUSH_RESISTANT), Component.literal("the acolyte is push resistant"));
		helper.assertTrue(!acolyte.onPushed(), Component.literal("a push outside the wind-up is no clash"));
		helper.assertTrue(acolyte.startShove() && acolyte.isShoving(), Component.literal("the shove wind-up must start"));

		ForceReactions.onPushHit(caster, acolyte);
		DuelState state = NpcGuard.state(acolyte);
		helper.assertTrue(!acolyte.isShoving(), Component.literal("a Push during the wind-up cancels the shove"));
		helper.assertTrue(state != null && state.isStaggered(now), Component.literal("the clash staggers the acolyte"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void captainYieldsAtAQuarterHealth(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PoacherCaptain captain = helper.spawn(EnemyEntities.POACHER_CAPTAIN, new BlockPos(4, 1, 6));
		captain.setNoAi(true);
		FakePlayer attacker = player(helper, new BlockPos(4, 1, 4), "captain_test");
		float max = captain.getMaxHealth();
		float threshold = max * captain.yieldHealthFraction();

		helper.assertTrue(Math.abs(captain.yieldHealthFraction() - 0.25f) < 1.0e-4 && captain.canYield(),
				Component.literal("the Captain yields at 25%"));
		captain.hurtServer(level, level.damageSources().playerAttack(attacker), max * 0.5f);
		helper.assertTrue(!captain.isYielded() && captain.getHealth() < max && captain.getHealth() > threshold,
				Component.literal("a hit that leaves her above 25% is taken normally (armour may soften it)"));

		captain.hurtServer(level, level.damageSources().playerAttack(attacker), max);
		helper.assertTrue(captain.isAlive() && captain.isYielded(), Component.literal("a lethal hit makes her yield instead of dying"));
		helper.assertTrue(Math.abs(captain.getHealth() - threshold) < 1.0e-3,
				Component.literal("her health stops at 25% (" + threshold + "), got " + captain.getHealth()));
		helper.assertTrue(captain.getTarget() == null, Component.literal("a yielded Captain drops her target"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void lostEmitterCanDropAgain(GameTestHelper helper) {
		FakePlayer player = player(helper, new BlockPos(2, 1, 2), "emitter_test");
		ForceData data = Progression.get(player);
		data.flags.add(PoacherLoot.EMITTER_FLAG);
		player.getInventory().add(new ItemStack(SaberItems.EMITTER_DUELIST));
		helper.assertTrue(!PoacherLoot.reconsiderEmitterFlag(player, data) && data.hasFlag(PoacherLoot.EMITTER_FLAG),
				Component.literal("an emitter in the inventory keeps the flag"));

		player.getInventory().clearContent();
		helper.assertTrue(PoacherLoot.reconsiderEmitterFlag(player, data) && !data.hasFlag(PoacherLoot.EMITTER_FLAG),
				Component.literal("a lost emitter clears the flag so another can drop"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- Milestone 3: frag grenade

	/**
	 * A thrown grenade spares its thrower; Push sends it on at grenadePushSpeed (1.2 blocks per
	 * tick) along the push, lofted; Pull flings it in an arc that lands at the thrower's feet (or the
	 * caster's when the thrower is gone); once redirected it lands, burns its fuse and bursts on its
	 * own thrower. Push and Pull are called directly: W3's dispatch is not needed here.
	 */
	@GameTest(structure = ARENA, maxTicks = 100)
	public void grenadePushTimes1_2AndPullBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TrooperBalance b = TrooperBalance.get();
		Mob thrower = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(3, 1, 2));
		ServerPlayer caster = M3TestPlayers.listed(helper, new BlockPos(15, 1, 2));

		try {
			// The ballistics are exact for free flight.
			Vec3 from = new Vec3(0.5, 2.6, 0.5);

			for (Vec3 to : new Vec3[] {new Vec3(6.5, 1, 0.5), new Vec3(-9, 1, 10), new Vec3(14, 0, -3)}) {
				int ticks = FragGrenade.lobTicks(Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z)));
				Vec3 landing = FragGrenade.flight(from, FragGrenade.lob(from, to, ticks), ticks);
				helper.assertTrue(landing.distanceTo(to) < 1.0e-6, msg("a lob over " + ticks + " ticks lands on " + to + ", got " + landing));
			}

			helper.assertTrue(Math.abs(FragGrenade.damageAt(8f, 3f, 0) - 8f) < 1.0e-4 && Math.abs(FragGrenade.damageAt(8f, 3f, 3) - 4f) < 1.0e-4
					&& FragGrenade.damageAt(8f, 3f, 3.01) == 0f, msg("8 damage at the centre, 4 at the edge of radius 3, none beyond"));

			FragGrenade grenade = FragGrenade.throwAt(level, thrower, caster.position());
			helper.assertTrue(grenade.isAlive() && grenade.getOwner() == thrower && grenade.thrower() == thrower, msg("the grenade belongs to its thrower"));
			helper.assertTrue(!grenade.landed() && !grenade.redirected() && !grenade.fuseStarted() && grenade.fuseTicks() == 0,
					msg("a thrown grenade is in flight with its fuse unlit"));
			helper.assertTrue(grenade.fuseLength() == b.grenadeFuseTicks, msg("the fuse is grenadeFuseTicks (" + b.grenadeFuseTicks + "), got " + grenade.fuseLength()));
			helper.assertTrue(FragGrenade.spares(thrower, thrower, false) && !FragGrenade.spares(thrower, caster, false),
					msg("before a redirect it spares its thrower, not a neutral player"));

			// Push: along the push at x1.2, climbing at least PUSH_MIN_RISE per unit of speed.
			Vec3 away = new Vec3(-1, -0.2, 0);
			helper.assertTrue(grenade.onForcePush(caster, away, 1f), msg("Push consumes the grenade"));
			Vec3 pushed = grenade.getDeltaMovement();
			helper.assertTrue(Math.abs(pushed.length() - b.grenadePushSpeed) < 1.0e-4, msg("a pushed grenade flies at " + b.grenadePushSpeed + ", got " + pushed.length()));
			helper.assertTrue(pushed.x < 0 && Math.abs(pushed.z) < 1.0e-6 && pushed.y >= FragGrenade.PUSH_MIN_RISE * b.grenadePushSpeed - 1.0e-4,
					msg("it goes along the push, lofted: " + pushed));
			helper.assertTrue(grenade.redirected() && !grenade.landed() && grenade.getOwner() == caster, msg("a pushed grenade is redirected and belongs to the caster"));
			helper.assertTrue(!FragGrenade.spares(thrower, thrower, true), msg("a redirected grenade spares nobody"));
			Vec3 steep = FragGrenade.pushVelocity(new Vec3(0.6, 0.8, 0), 1.2f);
			helper.assertTrue(steep.distanceTo(new Vec3(0.72, 0.96, 0)) < 1.0e-6, msg("a push already climbing keeps its direction: " + steep));

			// Pull: back to the thrower's feet in an arc.
			helper.assertTrue(grenade.onForcePull(caster, new Vec3(1, 0, 0), 1f), msg("Pull consumes the grenade"));
			Vec3 start = grenade.position();
			Vec3 feet = thrower.position();
			int ticks = FragGrenade.pullTicks(Math.sqrt((feet.x - start.x) * (feet.x - start.x) + (feet.z - start.z) * (feet.z - start.z)));
			Vec3 arc = FragGrenade.flight(start, grenade.getDeltaMovement(), ticks);
			helper.assertTrue(arc.distanceTo(feet) < 0.05, msg("the pulled arc lands at the thrower's feet " + feet + ", got " + arc));
			helper.assertTrue(grenade.redirected() && !grenade.landed() && grenade.getOwner() == caster, msg("a pulled grenade belongs to the caster"));

			// With the thrower gone, Pull brings it to the caster.
			Mob gone = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(3, 1, 8));
			FragGrenade orphan = FragGrenade.throwAt(level, gone, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(9, 1, 8))));
			gone.discard();
			orphan.onForcePull(caster, new Vec3(1, 0, 0), 1f);
			Vec3 orphanStart = orphan.position();
			Vec3 casterFeet = caster.position();
			int orphanTicks = FragGrenade.pullTicks(Math.sqrt((casterFeet.x - orphanStart.x) * (casterFeet.x - orphanStart.x)
					+ (casterFeet.z - orphanStart.z) * (casterFeet.z - orphanStart.z)));
			helper.assertTrue(FragGrenade.flight(orphanStart, orphan.getDeltaMovement(), orphanTicks).distanceTo(casterFeet) < 0.05,
					msg("with its thrower gone, a pulled grenade comes to the caster"));
			orphan.discard();
		} catch (RuntimeException e) {
			M3TestPlayers.remove(caster);
			throw e;
		}

		// The pulled grenade lands by its thrower, lights its fuse and bursts on it: it spares nobody now.
		helper.succeedWhen(() -> {
			DamageSource last = thrower.getLastDamageSource();
			helper.assertTrue(last != null && last.is(FragGrenade.DAMAGE), msg("the thrower is caught by its own returned grenade"));
			helper.assertTrue(thrower.getHealth() < thrower.getMaxHealth(), msg("the burst hurts the thrower"));
			helper.assertTrue(caster.getHealth() == caster.getMaxHealth(), msg("the caster, 12 blocks away, is untouched"));
			M3TestPlayers.remove(caster);
		});
	}

	/** A squad of four in the middle of the arena, recorded in a detached patrol state. */
	private static List<LivingEntity> patrolSquad(GameTestHelper helper, PatrolState state, Faction army) {
		List<TrooperRole> roles = army == Faction.REPUBLIC
				? List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.RIFLEMAN, TrooperRole.MEDIC)
				: List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.RIFLEMAN, TrooperRole.GRENADIER);
		List<LivingEntity> squad = Troopers.spawnSquad(helper.getLevel(), helper.absolutePos(new BlockPos(12, 1, 12)), army, roles, null, IncidentMode.NONE);
		helper.assertTrue(squad.size() == 4, msg("the test squad has 4 members, got " + squad.size()));
		UUID id = Troopers.squadId(squad.get(0));
		helper.assertTrue(id != null, msg("the test squad has a squad id"));
		state.add(id, squad);
		return squad;
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void patrolCapsAndSiteClearance(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PatrolBalance b = new PatrolBalance();
		PatrolState state = new PatrolState();
		List<LivingEntity> squad = patrolSquad(helper, state, Faction.REPUBLIC);
		Vec3 here = squad.get(0).position();

		// Per player: a patrol within 128 blocks blocks another for that player only.
		int nearOne = state.near(level, here.add(100, 0, 0), b.perPlayerRange);
		int nearOther = state.near(level, here.add(200, 0, 0), b.perPlayerRange);
		helper.assertTrue(state.size() == 1 && nearOne == 1 && nearOther == 0, msg("one patrol, near the first player only: " + nearOne + ", " + nearOther));
		helper.assertTrue(!PatrolRules.capsAllow(nearOne, state.size(), b), msg("a player with a patrol within 128 blocks gets no second one"));
		helper.assertTrue(PatrolRules.capsAllow(nearOther, state.size(), b), msg("a player 200 blocks away still can"));

		// Per server: at 6 patrols nobody gets another.
		helper.assertTrue(PatrolRules.capsAllow(0, b.globalCap - 1, b) && !PatrolRules.capsAllow(0, b.globalCap, b),
				msg("the server cap is " + b.globalCap));

		// Sites: 64 blocks of clearance, measured horizontally from the site.
		BlockPos site = new BlockPos(0, 64, 0);
		helper.assertTrue(!PatrolRules.clearOfSites(List.of(new Vec3(63.4, 90, 0.5)), List.of(site), b.siteClearance)
				&& PatrolRules.clearOfSites(List.of(new Vec3(64.6, 0, 0.5)), List.of(site), b.siteClearance),
				msg("63 blocks from a site is too close, 64 is clear, whatever the height"));

		// The route: passes 16 blocks from the player, then goes 96 blocks beyond on the same heading.
		Vec3 player = new Vec3(0.5, 70, 0.5);
		Vec3 start = new Vec3(60.5, 70, 0.5);

		for (boolean left : new boolean[] {true, false}) {
			List<Vec3> route = PatrolRules.route(start, player, left, b);
			Vec3 pass = route.get(0);
			Vec3 end = route.get(1);
			double passDistance = Math.hypot(pass.x - player.x, pass.z - player.z);
			Vec3 first = new Vec3(pass.x - start.x, 0, pass.z - start.z).normalize();
			Vec3 second = new Vec3(end.x - pass.x, 0, end.z - pass.z);
			helper.assertTrue(Math.abs(passDistance - b.passDistance) < 1.0e-6, msg("the route passes " + b.passDistance + " blocks from the player, got " + passDistance));
			helper.assertTrue(Math.abs(second.length() - b.routeLength) < 1.0e-6 && second.normalize().dot(first) > 0.9999,
					msg("then continues " + b.routeLength + " blocks on the same heading"));

			// A route whose far end runs into a site is refused even though its start is clear.
			List<BlockPos> atEnd = List.of(BlockPos.containing(end));
			helper.assertTrue(PatrolRules.clearOfSites(List.of(start), atEnd, b.siteClearance)
					&& !PatrolRules.clearOfSites(PatrolRules.samples(start, route, PatrolRules.ROUTE_SAMPLE_STEP), atEnd, b.siteClearance),
					msg("the clearance is checked along the whole route"));
		}

		// Out of sight: behind or well to the side of where the player looks.
		Vec3 eye = new Vec3(0, 65, 0);
		Vec3 look = new Vec3(0, -0.3, 1);
		helper.assertTrue(PatrolRules.outsideView(eye, look, new Vec3(0, 64, -60)) && PatrolRules.outsideView(eye, look, new Vec3(60, 64, 5))
				&& !PatrolRules.outsideView(eye, look, new Vec3(10, 64, 60)), msg("behind and beside are out of view, ahead is not"));

		// Placement distance, army choice and the per-player roll.
		RandomSource random = RandomSource.create(7);

		for (int i = 0; i < 64; i++) {
			double distance = PatrolRules.spawnOffset(random, b).horizontalDistance();
			helper.assertTrue(distance >= b.minDistance - 1.0e-6 && distance <= b.maxDistance + 1.0e-6, msg("a spawn offset of " + distance + " is outside 48 to 80"));
		}

		helper.assertTrue(PatrolRules.army(100, 400, random) == Faction.REPUBLIC && PatrolRules.army(400, 100, random) == Faction.SITH_EMPIRE
				&& PatrolRules.army(-1, 900, random) == Faction.SITH_EMPIRE && PatrolRules.army(900, -1, random) == Faction.REPUBLIC,
				msg("the army with the nearer outpost comes"));
		boolean sawRepublic = false;
		boolean sawEmpire = false;

		for (int i = 0; i < 32; i++) {
			Faction army = PatrolRules.army(-1, -1, random);
			sawRepublic |= army == Faction.REPUBLIC;
			sawEmpire |= army == Faction.SITH_EMPIRE;
		}

		helper.assertTrue(sawRepublic && sawEmpire, msg("with no outpost in range, either army comes"));
		int rolls = 0;

		for (int tick = 0; tick < b.intervalTicks * 3; tick++) {
			rolls += PatrolRules.rollDue(tick, 0x5EED, b.intervalTicks) ? 1 : 0;
		}

		helper.assertTrue(rolls == 3, msg("one roll per player per " + b.intervalTicks + " ticks, got " + rolls + " in three intervals"));

		helper.assertTrue(state.clear() == 1 && squad.stream().allMatch(LivingEntity::isRemoved) && state.size() == 0, msg("clearing discards the patrol"));
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void patrolDespawnsAsWholeSquad(GameTestHelper helper) {
		PatrolBalance b = new PatrolBalance();
		PatrolState state = new PatrolState();
		List<LivingEntity> squad = patrolSquad(helper, state, Faction.SITH_EMPIRE);
		LivingEntity east = squad.get(0);
		LivingEntity west = squad.get(0);

		for (LivingEntity member : squad) {
			east = member.getX() > east.getX() ? member : east;
			west = member.getX() < west.getX() ? member : west;
		}

		helper.assertTrue(east.getX() - west.getX() > 1, msg("the squad stands spread out"));

		// A player just within range of the easternmost member keeps everyone, though the others are farther.
		Vec3 watcher = east.position().add(b.despawnDistance - 0.1, 0, 0);
		helper.assertTrue(west.position().distanceTo(watcher) > b.despawnDistance, msg("the westernmost member alone is out of range"));
		helper.assertTrue(state.despawnFar(level -> List.of(watcher), b.despawnDistance) == 0 && squad.stream().noneMatch(LivingEntity::isRemoved),
				msg("one member near a player keeps the whole squad"));

		// A member that is gone drops out; the rest are still a patrol.
		LivingEntity lost = squad.get(3);
		lost.discard();
		PatrolState.Patrol patrol = state.patrols().iterator().next();
		helper.assertTrue(state.size() == 1 && patrol.members().size() == 3 && !patrol.members().contains(lost), msg("a removed member drops out of its patrol"));

		// Once every member is beyond range of every player, the whole squad goes at once.
		Vec3 gone = east.position().add(b.despawnDistance + 5, 0, 0);
		helper.assertTrue(state.despawnFar(level -> List.of(gone), b.despawnDistance) == 1, msg("the squad despawns as one"));
		helper.assertTrue(squad.stream().allMatch(LivingEntity::isRemoved) && state.size() == 0, msg("every member is discarded together"));

		// With no players in the level at all, a squad goes too.
		List<LivingEntity> second = patrolSquad(helper, state, Faction.REPUBLIC);
		helper.assertTrue(state.despawnFar(level -> List.of(), b.despawnDistance) == 1 && second.stream().allMatch(LivingEntity::isRemoved),
				msg("a patrol with no players in its level despawns"));
		helper.succeed();
	}
}
