package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.CollectorHooks;
import com.oldrepublic.boss.cantor.CantorChoir;
import com.oldrepublic.boss.cantor.CantorSession;
import com.oldrepublic.boss.cantor.CantorVerses;
import com.oldrepublic.boss.cantor.FirstCantor;
import com.oldrepublic.boss.cantor.LongHymn;
import com.oldrepublic.boss.mentor.MentorDuel;
import com.oldrepublic.boss.mentor.MentorDuelBalance;
import com.oldrepublic.boss.raid.RaidBalance;
import com.oldrepublic.boss.raid.RaidData;
import com.oldrepublic.boss.raid.RaidRewards;
import com.oldrepublic.boss.raid.RaidRules;
import com.oldrepublic.boss.raid.RaidSession;
import com.oldrepublic.boss.raid.UndercroftLayout;
import com.oldrepublic.boss.ulvaine.DarthUlvaine;
import com.oldrepublic.boss.ulvaine.HeartLayout;
import com.oldrepublic.boss.ulvaine.UlvaineBalance;
import com.oldrepublic.boss.ulvaine.UlvaineFight;
import com.oldrepublic.boss.ulvaine.UlvaineRules;
import com.oldrepublic.boss.ulvaine.UlvaineSession;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.enemy.cartel.CartelBalance;
import com.oldrepublic.enemy.cartel.CartelEnforcer;
import com.oldrepublic.enemy.cartel.CartelEnforcers;
import com.oldrepublic.enemy.cartel.CartelEntities;
import com.oldrepublic.enemy.hask.HaskEntities;
import com.oldrepublic.enemy.hask.HaskWarrior;
import com.oldrepublic.enemy.hask.HaskWarriors;
import com.oldrepublic.enemy.hymn.FoeKills;
import com.oldrepublic.enemy.hymn.HymnEntities;
import com.oldrepublic.enemy.hymn.Hymnsworn;
import com.oldrepublic.enemy.hymn.HymnswornBalance;
import com.oldrepublic.enemy.hymn.Hymnsworns;
import com.oldrepublic.enemy.hymn.TellLog;
import com.oldrepublic.enemy.scour.SandScour;
import com.oldrepublic.enemy.scour.ScourBalance;
import com.oldrepublic.enemy.scour.ScourEntities;
import com.oldrepublic.enemy.scour.Scours;
import com.oldrepublic.enemy.stormback.Stormback;
import com.oldrepublic.enemy.stormback.StormbackBalance;
import com.oldrepublic.enemy.stormback.StormbackEntities;
import com.oldrepublic.enemy.stormback.Stormbacks;
import com.oldrepublic.enemy.trooper.TrooperAttitudeRule;
import com.oldrepublic.force.fear.ForceFear;
import com.oldrepublic.force.fear.Frightened;
import com.oldrepublic.force.mindtrick.MindTrick;
import com.oldrepublic.force.stasis.StasisHolds;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.mentor.MentorEntities;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.saber.duel.DuelistProfile;
import com.oldrepublic.saber.duel.DuelistProfiles;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 5 foes and bosses (spec MILESTONE_5.md 12 and 19.1): the five new foes' tells, standings,
 * immunities and kill reports; the mentors' true duel; Darth Ulvaine's three phases, her yield, the
 * health scaling, the resolve hook, the daily echo and her Stasis break; and the Long Hymn raid's
 * participants, scaling, weekly lockout, pity, wipe, choir shield, verses, rings and window.
 *
 * <p>Every test is in the {@code oldrepublic_test:arena_24x7x24} fixture. The bosses fight in layouts
 * made by hand inside it ({@link #heart}, {@link #undercroft}) rather than in placed sites: a placed
 * site would be recorded in the world's site registry, and no test places a whole Milestone 5
 * template. Foes cannot hold a listed mock player as their target (a mock player is always creative
 * to {@code Mob#setTarget}), so they fight survival fake players or villagers; rules that read the
 * level's player list (warnings, crowding, the Heart and the undercroft) use listed players
 * ({@link M3TestPlayers}), which are invulnerable and never ticked. Blows a test lands itself are
 * indirect magic with the player as the cause (armour and saber guards stay out of the numbers),
 * unless the test is about the kind of blow. Boss hooks go to a {@link CollectorHooks.Recorder}, kill
 * reports to a {@link FoeKills} listener. Where production code rolls its own dice (a verse's spot,
 * the echo's shard) the tests check what holds for any roll. Whatever a test starts and spawns is
 * undone when it ends, whichever step ends it ({@link Cleanup}).
 */
public class FoesM5GameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	/** Health compares to this. */
	private static final float EPS = 0.01f;

	// ---------------------------------------------------------------- helpers

	/** A foe whose tells and heavy hits a test watches, and the prey it is kept on (null: its own choice). */
	private record Watched(String name, Mob mob, TellLog log, @Nullable LivingEntity prey) {
	}

	/** A player's level and the XP into it, to measure what a payment added across level-ups. */
	private record Xp(int level, long xp) {
		static Xp of(ServerPlayer player) {
			ForceData data = Progression.get(player);
			return new Xp(data.level, data.xp);
		}
	}

	/** What a test started and spawned, undone once, newest first, by whichever step ends the test. */
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

		/** A listed player: logged out at the end. */
		ServerPlayer player(ServerPlayer player) {
			undo.add(() -> M3TestPlayers.remove(player));
			return player;
		}

		void then(Runnable step) {
			undo.add(step);
		}

		void run() {
			if (done) {
				return;
			}

			done = true;

			for (int i = undo.size() - 1; i >= 0; i--) {
				try {
					undo.get(i).run();
				} catch (RuntimeException e) {
					OldRepublicMod.LOGGER.warn("FoesM5GameTests: a cleanup step failed", e);
				}
			}

			undo.clear();
		}
	}

	/** A cleanup whose last step sweeps the arena of anything a fight left behind. */
	private static Cleanup cleanup(GameTestHelper helper) {
		Cleanup cleanup = new Cleanup();
		cleanup.then(() -> sweep(helper));
		return cleanup;
	}

	/** Discards every entity but players in the arena (summoned chanters, spires, the choir, dropped items). */
	private static void sweep(GameTestHelper helper) {
		for (Entity entity : helper.getLevel().getEntitiesOfClass(Entity.class, arena(helper, 10), e -> !(e instanceof Player))) {
			entity.discard();
		}
	}

	/** The arena's floor area, {@code height} blocks up. */
	private static AABB arena(GameTestHelper helper, double height) {
		Vec3 centre = helper.absoluteVec(new Vec3(12, 0, 12));
		return new AABB(centre, centre).inflate(12.0, 0.0, 12.0).expandTowards(0.0, height, 0.0);
	}

	/** Runs {@code body}; if it throws, the cleanup runs first. */
	private static void guarded(Cleanup cleanup, Runnable body) {
		try {
			body.run();
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/** A step {@code delay} ticks in. */
	private static void step(GameTestHelper helper, Cleanup cleanup, long delay, Runnable body) {
		helper.runAfterDelay(delay, () -> guarded(cleanup, body));
	}

	/** The last step: the test succeeds if {@code body} passes; the cleanup runs either way. */
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

	/**
	 * Asks {@code body} every tick from {@code from} to {@code deadline}: true ends the test in success,
	 * a failed assertion ends it in failure, and still false at the deadline fails it with {@code what}.
	 */
	private static void watch(GameTestHelper helper, Cleanup cleanup, int from, int deadline, String what, IntPredicate body) {
		boolean[] done = new boolean[1];

		for (int t = from; t <= deadline; t++) {
			int tick = t;
			helper.runAfterDelay(tick, () -> {
				if (done[0]) {
					return;
				}

				boolean finished;

				try {
					finished = body.test(tick);
				} catch (RuntimeException | Error e) {
					done[0] = true;
					cleanup.run();
					throw e;
				}

				if (finished) {
					done[0] = true;
					cleanup.run();
					helper.succeed();
				} else if (tick == deadline) {
					done[0] = true;
					cleanup.run();
					helper.fail(what + " (not seen by tick " + deadline + ")");
				}
			});
		}
	}

	/** Peaceful removes the foes and stops the fights (6.9): the live tests pass there without running. */
	private static boolean skippedOnPeaceful(GameTestHelper helper, String name) {
		if (helper.getLevel().getDifficulty() != Difficulty.PEACEFUL) {
			return false;
		}

		OldRepublicMod.LOGGER.info("FoesM5GameTests.{}: skipped on Peaceful", name);
		helper.succeed();
		return true;
	}

	/** A listed survival player at relative ({@code x}, 1, {@code z}): the hazards and the rules count them. */
	private static ServerPlayer fighter(GameTestHelper helper, Cleanup cleanup, int x, int z) {
		ServerPlayer player = cleanup.player(M3TestPlayers.listed(helper, new BlockPos(x, 1, z)));
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** A listed creative player: in the fight's lists, but no hazard or foe takes notice of them. */
	private static ServerPlayer onlooker(GameTestHelper helper, Cleanup cleanup, int x, int z) {
		ServerPlayer player = cleanup.player(M3TestPlayers.listed(helper, new BlockPos(x, 1, z)));
		player.setGameMode(GameType.CREATIVE);
		return player;
	}

	/** A survival fake player a foe can hold as its target (not in the player list), proof against harm. */
	private static ServerPlayer fake(GameTestHelper helper, int x, int z) {
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "foes_m5_test"));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 1, z))));
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 4));
		return player;
	}

	/** A mob without its goals: its own server step (balance, standing, crowding, chant) still runs. */
	private static <E extends Mob> E still(GameTestHelper helper, Cleanup cleanup, EntityType<E> type, int x, int z) {
		return cleanup.entity(helper.spawnWithNoFreeWill(type, new BlockPos(x, 1, z)));
	}

	/** A mob that does nothing at all. */
	private static <E extends Mob> E dummy(GameTestHelper helper, Cleanup cleanup, EntityType<E> type, int x, int z) {
		E mob = still(helper, cleanup, type, x, z);
		mob.setNoAi(true);
		return mob;
	}

	private static <T> T present(GameTestHelper helper, @Nullable T value, String what) {
		helper.assertTrue(value != null, what + " could not be made");
		return value;
	}

	private static BlockPos at(GameTestHelper helper, int x, int y, int z) {
		return helper.absolutePos(new BlockPos(x, y, z));
	}

	/** Moves a listed player's feet to the bottom centre of relative ({@code x}, 1, {@code z}). */
	private static void moveTo(GameTestHelper helper, ServerPlayer player, int x, int z) {
		Vec3 feet = Vec3.atBottomCenterOf(at(helper, x, 1, z));
		player.snapTo(feet.x, feet.y, feet.z);
	}

	/** A blow with no kind of its own: indirect magic caused by {@code by} (no armour, no saber guard). */
	private static DamageSource spell(ServerLevel level, Entity by) {
		return level.damageSources().source(DamageTypes.INDIRECT_MAGIC, null, by);
	}

	/** Lands {@code amount} on {@code target} now, whatever it took last tick. */
	private static boolean hit(ServerLevel level, LivingEntity target, DamageSource source, float amount) {
		target.invulnerableTime = 0;
		return target.hurtServer(level, source, amount);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-3;
	}

	/** A stone wall from relative ({@code x0}, {@code z0}) to ({@code x1}, {@code z1}), {@code top} blocks high. */
	private static void wall(GameTestHelper helper, int x0, int z0, int x1, int z1, int top) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
				for (int y = 1; y <= top; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
	}

	/** The XP added between {@code from} and {@code to}, counting the levels gained on the way. */
	private static long gained(Xp from, Xp to) {
		long total = to.xp() - from.xp();

		for (int level = from.level(); level < to.level(); level++) {
			total += Progression.xpToNext(level);
		}

		return total;
	}

	/** Stormglass shards lying within 3 blocks of {@code at}. */
	private static int shards(ServerLevel level, Vec3 at) {
		int count = 0;

		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3.0), i -> i.getItem().is(M5Items.STORMGLASS_SHARD))) {
			count += item.getItem().getCount();
		}

		return count;
	}

	/**
	 * A Heart of the Hymnal made by hand in the arena: her dais in the middle (relative (12, 2, 12)), a
	 * 10.5-block hall, rods at the four sides, pylons east and west of the lectern's row, the resonator
	 * spots around her.
	 */
	private static HeartLayout heart(GameTestHelper helper) {
		return new HeartLayout(at(helper, 12, 2, 12), 10.5, UlvaineBalance.get().heartHeight,
				List.of(at(helper, 12, 1, 3), at(helper, 21, 1, 12), at(helper, 12, 1, 21), at(helper, 3, 1, 12)),
				List.of(at(helper, 3, 1, 8), at(helper, 21, 1, 8)),
				List.of(at(helper, 7, 1, 9), at(helper, 17, 1, 9), at(helper, 17, 1, 15), at(helper, 7, 1, 15)),
				at(helper, 12, 1, 8));
	}

	/**
	 * An undercroft made by hand in the arena, from relative z 0 to {@code maxZ} (the rest of the arena is
	 * outside it): his spot at (12, 1, 8), the choir around him, the three silencing points and rings.
	 */
	private static UndercroftLayout undercroft(GameTestHelper helper, int maxZ) {
		BlockPos a = at(helper, 0, 0, 0);
		BlockPos b = at(helper, 23, 6, maxZ);
		BlockPos lo = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		BlockPos hi = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
		return new UndercroftLayout(lo, hi, at(helper, 12, 1, 8), at(helper, 12, 1, 10), null,
				List.of(at(helper, 19, 1, 8), at(helper, 5, 1, 8), at(helper, 12, 1, 2), at(helper, 12, 1, 15)),
				List.of(at(helper, 3, 1, 3), at(helper, 20, 1, 2), at(helper, 3, 1, 16)),
				List.of(at(helper, 17, 1, 13), at(helper, 7, 1, 13), at(helper, 12, 1, 17)));
	}

	/** {@code owner}'s fight (or echo) in {@link #heart}, reporting to {@code hooks}; reset at the end, any open yield closed. */
	private static UlvaineSession ulvaineFight(GameTestHelper helper, Cleanup cleanup, ServerPlayer owner, boolean echo, CollectorHooks.Sink hooks) {
		UlvaineSession session = UlvaineFight.begin(owner.getUUID(), helper.getLevel(), heart(helper), echo, false, hooks);
		helper.assertTrue(session != null, "Ulvaine's fight could not begin");
		cleanup.then(() -> {
			UlvaineFight.reset(owner);

			if (UlvaineFight.yieldOpen(owner.getUUID())) {
				UlvaineFight.resolve(owner, BossOutcome.SPARED, new CollectorHooks.Recorder());
			}
		});
		return session;
	}

	private static DarthUlvaine ulvaine(GameTestHelper helper, UlvaineSession session) {
		return present(helper, session.ulvaine(), "Darth Ulvaine");
	}

	/** A Long Hymn in {@code layout} that begins on the next tick; stopped quietly at the end. */
	private static CantorSession hymn(GameTestHelper helper, Cleanup cleanup, UndercroftLayout layout) {
		CantorSession session = LongHymn.start(helper.getLevel(), layout, null, 0);
		helper.assertTrue(session != null, "the Long Hymn could not start");
		cleanup.then(() -> session.stop(true));
		return session;
	}

	private static FirstCantor cantor(GameTestHelper helper, CantorSession session) {
		return present(helper, session.cantor(), "the First Cantor");
	}

	private static RaidSession raid(GameTestHelper helper, CantorSession session) {
		return present(helper, session.raid(), "the raid");
	}

	// ---------------------------------------------------------------- the five foes (12.1)

	/**
	 * 12.1: "a danger tell before every heavy hit". Five lanes walled off from each other, one foe each:
	 * the stormback charges the villager that struck it, the scour bursts under its prey, the cartel
	 * bruiser and the Hask blade cut at a player their faction hates, and the Hymnsworn caller calls the
	 * storm on its prey. No heavy hit comes without its tell, and each foe lands at least one.
	 */
	@GameTest(structure = ARENA, maxTicks = 300)
	public void tellsPrecedeHeavyHits(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "tellsPrecedeHeavyHits")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();

			for (int z : new int[] {4, 9, 14, 19}) {
				wall(helper, 0, z, 23, z, 5);
			}

			List<Watched> watched = new ArrayList<>();
			// Lane A: a stormback and the villager that will strike it first (proof against the charge).
			Mob bait = dummy(helper, cleanup, EntityTypes.VILLAGER, 16, 2);
			bait.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 4));
			Stormback stormback = cleanup.entity(present(helper, Stormbacks.spawn(level, at(helper, 3, 1, 2), null), "a stormback"));
			watched.add(new Watched("the stormback", stormback, stormback.tellLog(), null));
			// Lane B: a scour under the sand three blocks from its prey.
			SandScour scour = cleanup.entity(present(helper, Scours.spawn(level, at(helper, 10, 1, 7), false, null), "a scour"));
			watched.add(new Watched("the scour", scour, scour.tellLog(), fake(helper, 13, 7)));
			// Lane C: a cartel bruiser and a player the cartel hates.
			ServerPlayer cartelPrey = fake(helper, 5, 11);
			Identity.setReputation(cartelPrey, Faction.VESSBA_KAJIDIC, -600);
			CartelEnforcer bruiser = cleanup.entity(present(helper,
					CartelEnforcers.spawn(level, at(helper, 2, 1, 11), CartelEnforcer.Variant.BRUISER, null), "a cartel bruiser"));
			watched.add(new Watched("the cartel bruiser", bruiser, bruiser.tellLog(), cartelPrey));
			// Lane D: a Hask blade and a player the clan hates.
			ServerPlayer haskPrey = fake(helper, 13, 17);
			Identity.setReputation(haskPrey, Faction.CLAN_HASK, -600);
			HaskWarrior blade = cleanup.entity(present(helper,
					HaskWarriors.spawn(level, at(helper, 10, 1, 17), HaskWarrior.Variant.BLADE, null), "a Hask blade"));
			watched.add(new Watched("the Hask blade", blade, blade.tellLog(), haskPrey));
			// Lane E: a Hymnsworn caller eleven blocks from its prey.
			Hymnsworn caller = cleanup.entity(present(helper,
					Hymnsworns.spawn(level, at(helper, 2, 1, 21), Hymnsworn.Variant.CALLER, false, null), "a Hymnsworn caller"));
			watched.add(new Watched("the Hymnsworn caller", caller, caller.tellLog(), fake(helper, 13, 21)));

			watch(helper, cleanup, 1, 280, "a told heavy hit from every new foe", tick -> {
				if (tick == 1) {
					stormback.invulnerableTime = 0;
					stormback.hurtServer(level, level.damageSources().mobAttack(bait), 0.5f);
				}

				boolean all = true;

				for (Watched w : watched) {
					helper.assertTrue(w.mob().isAlive(), w.name() + " died");
					LivingEntity prey = w.prey();

					if (prey != null && w.mob().getTarget() != prey) {
						w.mob().setTarget(prey);
					}

					helper.assertTrue(w.log().untold() == 0, w.name() + " landed a heavy hit without its tell");
					helper.assertTrue(w.log().tells() >= w.log().heavyHits(), w.name() + " landed more heavy hits than it told");
					all &= w.log().heavyHits() >= 1;
				}

				return all;
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.1, 7: the cartel and the clan by the player's standing (hostile, wary, neutral, allied), the
	 * Peaceful cap, engaged against provoked, and owned groups (the cartel's owner is its target, the
	 * clan's owner its ally). Then live: a gunman warns a wary player and leaves a neutral one alone;
	 * once the warned player draws a rifle, the fight is on and counts as hostile.
	 */
	@GameTest(structure = ARENA, maxTicks = 140)
	public void enforcerAttitudeByStanding(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "enforcerAttitudeByStanding")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			CartelBalance b = CartelBalance.get();
			CartelEnforcer cartel = dummy(helper, cleanup, CartelEntities.CARTEL_ENFORCER, 3, 3);
			HaskWarrior hask = dummy(helper, cleanup, HaskEntities.HASK_WARRIOR, 6, 3);
			CartelEnforcer ownedCartel = dummy(helper, cleanup, CartelEntities.CARTEL_ENFORCER, 9, 3);
			HaskWarrior ownedHask = dummy(helper, cleanup, HaskEntities.HASK_WARRIOR, 12, 3);
			int[] standings = {-600, -200, 0, 200, 600};
			ReputationTier[] tiers = {ReputationTier.HOSTILE, ReputationTier.DISTRUSTED, ReputationTier.NEUTRAL, ReputationTier.TRUSTED,
					ReputationTier.HONOURED};
			Attitude[] expected = {Attitude.HOSTILE, Attitude.WARY, Attitude.NEUTRAL, Attitude.ALLIED, Attitude.ALLIED};
			ServerPlayer[] players = new ServerPlayer[standings.length];

			for (int i = 0; i < standings.length; i++) {
				players[i] = fake(helper, 3 + 3 * i, 7);
				Identity.setReputation(players[i], Faction.VESSBA_KAJIDIC, standings[i]);
				Identity.setReputation(players[i], Faction.CLAN_HASK, standings[i]);
			}

			// Live: a gunman, a player it distrusts (who draws later) and one it does not.
			CartelEnforcer gunman = still(helper, cleanup, CartelEntities.CARTEL_ENFORCER, 6, 15);
			gunman.setVariant(CartelEnforcer.Variant.GUNMAN);
			ServerPlayer wary = fighter(helper, cleanup, 6, 19);
			ServerPlayer calm = fighter(helper, cleanup, 10, 15);
			Identity.setReputation(wary, Faction.VESSBA_KAJIDIC, -200);
			Identity.setReputation(calm, Faction.VESSBA_KAJIDIC, 0);

			step(helper, cleanup, 1, () -> {
				for (int i = 0; i < standings.length; i++) {
					ServerPlayer p = players[i];
					boolean hostile = expected[i] == Attitude.HOSTILE;
					helper.assertTrue(Identity.tier(p, Faction.VESSBA_KAJIDIC) == tiers[i] && Identity.tier(p, Faction.CLAN_HASK) == tiers[i],
							"a standing of " + standings[i] + " is " + tiers[i]);
					helper.assertTrue(Attitudes.toward(cartel, p) == expected[i], "an enforcer is " + expected[i] + " to a " + tiers[i] + " player");
					helper.assertTrue(Attitudes.toward(hask, p) == expected[i], "a Hask warrior is " + expected[i] + " to a " + tiers[i] + " player");
					helper.assertTrue(Attitudes.countsAsHostile(p, cartel) == hostile && Attitudes.countsAsHostile(p, hask) == hostile,
							"only a hostile standing makes the fight hostile (" + tiers[i] + ")");
				}

				helper.assertTrue(TrooperAttitudeRule.attitude(ReputationTier.HOSTILE, true) == Attitude.NEUTRAL
						&& TrooperAttitudeRule.attitude(ReputationTier.DISTRUSTED, true) == Attitude.NEUTRAL
						&& TrooperAttitudeRule.attitude(ReputationTier.HONOURED, true) == Attitude.ALLIED, "Peaceful caps a standing's attitude at neutral");

				// Engaged (a warned player drew on it): the fight counts as hostile. Provoked (struck first): it fights back, never hostile.
				ServerPlayer neutral = players[2];
				long now = level.getGameTime();
				cartel.standing().engage(neutral.getUUID(), now + b.engageTicks);
				helper.assertTrue(Attitudes.countsAsHostile(neutral, cartel) && Attitudes.toward(cartel, neutral) == Attitude.NEUTRAL
						&& !Attitudes.provokedBy(cartel, neutral), "an engaged enforcer counts as hostile while its standing stays neutral");
				hask.standing().provoke(neutral.getUUID(), now + b.provokeTicks);
				helper.assertTrue(Attitudes.provokedBy(hask, neutral) && !Attitudes.countsAsHostile(neutral, hask),
						"a provoked warrior fights back, and the fight is not hostile");

				// Owned groups (6.3): a cartel squad hunts its owner whatever the standing; a Hask group fights beside its owner.
				StoryTags.setOwner(ownedCartel, players[4].getUUID());
				StoryTags.setOwner(ownedHask, players[0].getUUID());
				helper.assertTrue(Attitudes.toward(ownedCartel, players[4]) == Attitude.HOSTILE, "an owned cartel squad is hostile to its honoured owner");
				helper.assertTrue(Attitudes.toward(ownedHask, players[0]) == Attitude.ALLIED, "an owned Hask group is allied to its hated owner");
				helper.assertTrue(Attitudes.toward(ownedCartel, players[2]) == Attitude.NEUTRAL, "to anyone outside the owner's party it keeps the standing");
			});

			boolean[] warned = new boolean[1];
			watch(helper, cleanup, 2, 120, "the gunman's warning, then its fight once the warned player drew", tick -> {
				long now = level.getGameTime();
				helper.assertTrue(!gunman.standing().warnedWithin(calm.getUUID(), now, b.warnRepeatTicks) && !gunman.standing().engaged(calm.getUUID(), now),
						"the gunman warned or engaged a player of neutral standing");

				if (!warned[0]) {
					if (!gunman.standing().warnedWithin(wary.getUUID(), now, b.warnRepeatTicks)) {
						return false;
					}

					warned[0] = true;
					helper.assertTrue(Attitudes.toward(gunman, wary) == Attitude.WARY && !Attitudes.countsAsHostile(wary, gunman),
							"a warned player is still only wary, not fought");
					wary.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EnemyItems.BLASTER_RIFLE));
					return false;
				}

				return gunman.standing().engaged(wary.getUUID(), now) && Attitudes.countsAsHostile(wary, gunman);
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/** 12.1, 9.2: a Clan Hask warrior is fearless and strong-willed; a cartel enforcer can be frightened and tricked. */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void haskFearlessAndStrongWilled(GameTestHelper helper) {
		Cleanup cleanup = cleanup(helper);

		try {
			HaskWarrior hask = dummy(helper, cleanup, HaskEntities.HASK_WARRIOR, 6, 6);
			CartelEnforcer cartel = dummy(helper, cleanup, CartelEntities.CARTEL_ENFORCER, 16, 6);

			last(helper, cleanup, 1, () -> {
				helper.assertTrue(hask.getType().is(ForceFear.FEARLESS) && hask.getType().is(MindTrick.STRONG_WILLED),
						"hask_warrior is in #oldrepublic:fearless and #oldrepublic:strong_willed");
				helper.assertTrue(Frightened.isImmune(hask) && ForceFear.apply(hask, null, 100) == 0 && !Frightened.isAfraid(hask),
						"Fear does not take a Hask warrior");
				helper.assertFalse(MindTrick.trickable(hask), "a Mind Trick does not take a Hask warrior");
				helper.assertTrue(cartel.getType().is(MindTrick.MIND_TRICKABLE) && MindTrick.trickable(cartel), "a Mind Trick takes an enforcer");
				helper.assertTrue(!Frightened.isImmune(cartel) && ForceFear.apply(cartel, null, 100) > 0 && Frightened.isAfraid(cartel),
						"Fear takes an enforcer");
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.1: a chanter's chant heals the Hymnsworn within {@code chantRange} by {@code chantHeal} each
	 * {@code chantIntervalTicks}, and nothing else: not one farther off, not a villager beside it.
	 */
	@GameTest(structure = ARENA, maxTicks = 140)
	public void chantHealsAllies(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "chantHealsAllies")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			HymnswornBalance b = HymnswornBalance.get();
			Hymnsworn chanter = still(helper, cleanup, HymnEntities.HYMNSWORN, 6, 3);
			chanter.setVariant(Hymnsworn.Variant.CHANTER);
			Hymnsworn near = dummy(helper, cleanup, HymnEntities.HYMNSWORN, 9, 3);
			Hymnsworn far = dummy(helper, cleanup, HymnEntities.HYMNSWORN, 17, 3);
			Mob villager = dummy(helper, cleanup, EntityTypes.VILLAGER, 7, 6);
			LivingEntity[] hurt = {near, far, villager};
			float[] start = new float[hurt.length];

			step(helper, cleanup, 1, () -> {
				for (int i = 0; i < hurt.length; i++) {
					hurt[i].setHealth(hurt[i].getMaxHealth() - 10f);
					start[i] = hurt[i].getHealth();
				}
			});

			last(helper, cleanup, 101, () -> {
				helper.assertTrue(chanter.isChanting(), "the chanter chants while an ally near it is hurt");
				helper.assertTrue(near.getHealth() >= start[0] + 3 * b.chantHeal - EPS && near.getHealth() <= near.getMaxHealth() + EPS,
						"the chant healed the Hymnsworn within " + b.chantRange + " blocks (" + start[0] + " -> " + near.getHealth() + ")");
				helper.assertTrue(Math.abs(far.getHealth() - start[1]) < EPS, "the chant reached a Hymnsworn " + b.chantRange + "+ blocks off");
				helper.assertTrue(Math.abs(villager.getHealth() - start[2]) < EPS, "the chant healed a villager");
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.1: the caller's storm call marks the patch under its prey (the tell), and {@code callTellTicks}
	 * later strikes that patch, not the prey who stepped off it: {@code callDamage} and the shock to a
	 * villager left on the patch, nothing to one standing clear.
	 */
	@GameTest(structure = ARENA, maxTicks = 240)
	public void stormCallTellThenStrike(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "stormCallTellThenStrike")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			HymnswornBalance b = HymnswornBalance.get();
			Hymnsworn caller = cleanup.entity(present(helper,
					Hymnsworns.spawn(level, at(helper, 3, 1, 20), Hymnsworn.Variant.CALLER, false, null), "a Hymnsworn caller"));
			ServerPlayer prey = fake(helper, 12, 20);
			Mob under = dummy(helper, cleanup, EntityTypes.VILLAGER, 12, 21);
			Mob[] clear = new Mob[1];
			long[] toldAt = {-1};
			float[] underHealth = new float[1];

			watch(helper, cleanup, 1, 200, "the storm call's strike", tick -> {
				TellLog log = caller.tellLog();
				helper.assertTrue(caller.isAlive(), "the caller died");

				if (caller.getTarget() != prey) {
					caller.setTarget(prey);
				}

				if (toldAt[0] < 0) {
					helper.assertTrue(log.heavyHits() == 0, "the storm struck before its tell");

					if (caller.action() != Hymnsworn.ACTION_CALL) {
						return false;
					}

					// The tell: the patch is marked under the prey, who steps off it; one villager stays on it, another stands clear.
					toldAt[0] = tick;
					helper.assertTrue(log.tells() >= 1, "the call's tell was logged");
					underHealth[0] = under.getHealth();
					prey.setPos(Vec3.atBottomCenterOf(at(helper, 19, 1, 20)));
					clear[0] = dummy(helper, cleanup, EntityTypes.VILLAGER, 19, 20);
					return false;
				}

				if (log.heavyHits() < 1) {
					return false;
				}

				long after = tick - toldAt[0];
				helper.assertTrue(Math.abs(after - b.callTellTicks) <= 1, "the strike landed " + after + " ticks after its tell (" + b.callTellTicks + ")");
				helper.assertTrue(Math.abs(underHealth[0] - under.getHealth() - b.callDamage) < EPS,
						"the strike dealt " + b.callDamage + " on the marked patch (" + underHealth[0] + " -> " + under.getHealth() + ")");
				helper.assertTrue(under.hasEffect(MobEffects.SLOWNESS), "the strike shocked whoever stood on the patch");
				helper.assertTrue(clear[0].getHealth() >= clear[0].getMaxHealth() - EPS && !clear[0].hasEffect(MobEffects.SLOWNESS),
						"the strike touched whoever stood clear of the patch");
				helper.assertTrue(log.untold() == 0, "the strike came without its tell");
				return true;
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.1: a scour's cycle at its prey: the burst told under the sand ({@code burstTellTicks}), up for
	 * {@code surfaceTicks}, down again for at least {@code burrowTicks}, then the next told burst.
	 */
	@GameTest(structure = ARENA, maxTicks = 300)
	public void scourBurrowBurstCycle(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "scourBurrowBurstCycle")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			ScourBalance b = ScourBalance.get();
			SandScour scour = cleanup.entity(present(helper, Scours.spawn(level, at(helper, 4, 1, 8), false, null), "a scour"));
			ServerPlayer prey = fake(helper, 7, 8);
			// The ticks of: the first tell, the burst, the dive, the second tell, the second burst.
			long[] seen = {-1, -1, -1, -1, -1};

			watch(helper, cleanup, 1, 280, "two burrow-and-burst cycles", tick -> {
				helper.assertTrue(scour.isAlive(), "the scour died");

				if (scour.getTarget() != prey) {
					scour.setTarget(prey);
				}

				boolean telling = scour.action() == SandScour.ACTION_BURST;

				if (seen[0] < 0) {
					if (telling) {
						helper.assertTrue(scour.isBurrowed(), "the burst is told from under the sand");
						seen[0] = tick;
					}
				} else if (seen[1] < 0) {
					if (!scour.isBurrowed()) {
						seen[1] = tick;
					}
				} else if (seen[2] < 0) {
					if (scour.isBurrowed()) {
						seen[2] = tick;
					}
				} else if (seen[3] < 0) {
					if (telling) {
						seen[3] = tick;
					}
				} else if (!scour.isBurrowed()) {
					seen[4] = tick;
				}

				if (seen[4] < 0) {
					return false;
				}

				helper.assertTrue(Math.abs(seen[1] - seen[0] - b.burstTellTicks) <= 1,
						"the burst came " + (seen[1] - seen[0]) + " ticks after its tell (" + b.burstTellTicks + ")");
				helper.assertTrue(Math.abs(seen[2] - seen[1] - b.surfaceTicks) <= 2, "it stayed up " + (seen[2] - seen[1]) + " ticks (" + b.surfaceTicks + ")");
				helper.assertTrue(seen[3] - seen[2] >= b.burrowTicks - 1, "it told again " + (seen[3] - seen[2]) + " ticks after it dove (at least " + b.burrowTicks + ")");
				helper.assertTrue(Math.abs(seen[4] - seen[3] - b.burstTellTicks) <= 1,
						"the second burst came " + (seen[4] - seen[3]) + " ticks after its tell (" + b.burstTellTicks + ")");
				TellLog log = scour.tellLog();
				helper.assertTrue(log.tells() == 2 && log.heavyHits() == 2 && log.untold() == 0,
						"two tells and two bursts, none untold (" + log.tells() + ", " + log.heavyHits() + ", " + log.untold() + ")");
				return true;
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.1: under the sand a scour cannot be aimed at, struck or shot, and only what bypasses
	 * invulnerability reaches it; surfaced it takes blows; wounded below {@code fleeBelow} it dives, and
	 * nothing reaches it again.
	 */
	@GameTest(structure = ARENA, maxTicks = 120)
	public void scourHurtOnlyAboveGround(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "scourHurtOnlyAboveGround")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			ScourBalance b = ScourBalance.get();
			SandScour hidden = cleanup.entity(present(helper, Scours.spawn(level, at(helper, 3, 1, 3), false, null), "a hidden scour"));
			hidden.setNoAi(true);
			ServerPlayer striker = fake(helper, 6, 3);
			SandScour hunter = cleanup.entity(present(helper, Scours.spawn(level, at(helper, 14, 1, 4), false, null), "a hunting scour"));
			ServerPlayer prey = fake(helper, 16, 4);
			boolean[] wounded = new boolean[1];

			step(helper, cleanup, 1, () -> {
				helper.assertTrue(hidden.isBurrowed(), "a scour spawns under the sand");
				float health = hidden.getHealth();
				helper.assertFalse(hit(level, hidden, level.damageSources().magic(), 5f), "magic reached a burrowed scour");
				helper.assertFalse(hit(level, hidden, level.damageSources().playerAttack(striker), 5f), "a player's blow reached a burrowed scour");
				helper.assertTrue(Math.abs(hidden.getHealth() - health) < EPS, "a burrowed scour lost health");
				helper.assertTrue(!hidden.isPickable() && !hidden.isAttackable() && !hidden.canBeHitByProjectile() && !hidden.canBeSeenAsEnemy(),
						"a burrowed scour could be aimed at, struck, shot or targeted");
				helper.assertTrue(hit(level, hidden, level.damageSources().fellOutOfWorld(), 5f) && hidden.getHealth() < health,
						"what bypasses invulnerability reaches a scour under the sand");
			});

			watch(helper, cleanup, 2, 100, "the hunting scour's burst, its wound and its dive", tick -> {
				if (hunter.getTarget() != prey) {
					hunter.setTarget(prey);
				}

				if (!wounded[0]) {
					if (hunter.isBurrowed()) {
						return false;
					}

					helper.assertTrue(hunter.isPickable() && hunter.isAttackable() && hunter.canBeHitByProjectile(), "a surfaced scour can be aimed at, struck and shot");
					float wound = hunter.getHealth() - (hunter.getMaxHealth() * b.fleeBelow - 1f);
					helper.assertTrue(hit(level, hunter, level.damageSources().magic(), wound), "a surfaced scour takes a blow");
					helper.assertTrue(hunter.getHealth() < hunter.getMaxHealth() * b.fleeBelow, "the blow left it under " + b.fleeBelow + " of its health");
					wounded[0] = true;
					return false;
				}

				if (!hunter.isBurrowed()) {
					return false;
				}

				float health = hunter.getHealth();
				helper.assertFalse(hit(level, hunter, level.damageSources().magic(), 5f), "a blow reached the wounded scour under the sand");
				helper.assertTrue(Math.abs(hunter.getHealth() - health) < EPS, "the wounded scour lost health under the sand");
				return true;
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.1, 7: a stormback is neutral. Struck first it turns on its attacker, provoked (it fights back, the
	 * fight never hostile); crowded within {@code provokeRange} for {@code provokeTicks} it turns on the
	 * crowder unprovoked (a hostile fight). A creative player beside it is never its quarrel.
	 */
	@GameTest(structure = ARENA, maxTicks = 140)
	public void stormbackNeutralUntilProvoked(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "stormbackNeutralUntilProvoked")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			StormbackBalance b = StormbackBalance.get();
			Stormback crowded = still(helper, cleanup, StormbackEntities.STORMBACK, 8, 7);
			ServerPlayer crowder = fighter(helper, cleanup, 8, 10);
			ServerPlayer watcher = onlooker(helper, cleanup, 11, 7);
			Stormback struck = still(helper, cleanup, StormbackEntities.STORMBACK, 19, 9);
			ServerPlayer striker = fighter(helper, cleanup, 19, 20);

			step(helper, cleanup, 1, () -> {
				helper.assertTrue(Attitudes.toward(struck, striker) == Attitude.NEUTRAL && !Attitudes.countsAsHostile(striker, struck),
						"a stormback is neutral to a player who leaves it be");
				helper.assertTrue(hit(level, struck, level.damageSources().playerAttack(striker), 2f), "the striker's blow landed");
				helper.assertTrue(struck.isAngryAt(striker) && struck.isProvokedBy(striker), "struck first, it turns on its attacker, provoked");
				helper.assertTrue(Attitudes.provokedBy(struck, striker) && !Attitudes.countsAsHostile(striker, struck)
						&& Attitudes.toward(struck, striker) == Attitude.NEUTRAL, "a provoked stormback fights back, and the fight is not hostile");
			});

			step(helper, cleanup, 40, () -> helper.assertTrue(!crowded.isAngryAt(crowder) && !Attitudes.countsAsHostile(crowder, crowded),
					"a stormback turned on a player before " + b.provokeTicks + " ticks of crowding"));

			watch(helper, cleanup, 41, 120, "the crowded stormback's anger", tick -> {
				helper.assertFalse(crowded.isAngryAt(watcher), "a stormback turned on a creative player");

				if (!crowded.isAngryAt(crowder)) {
					return false;
				}

				helper.assertTrue(!crowded.isProvokedBy(crowder) && Attitudes.countsAsHostile(crowder, crowded) && !Attitudes.provokedBy(crowded, crowder),
						"a stormback crowded into anger fights as a hostile foe, not a provoked one");
				return true;
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.1: lightning charges a stormback without hurting or burning it (its blows deal
	 * {@code 1 + chargedDamageBonus}); a charged stormback drops stormglass, an uncharged one none.
	 * Below {@code roarBelow} it roars once (its told heavy blow, which charges it), and its next charge
	 * deals {@code chargedChargeDamage} and shocks.
	 */
	@GameTest(structure = ARENA, maxTicks = 300)
	public void stormbackChargedByLightning(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "stormbackChargedByLightning")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			StormbackBalance b = StormbackBalance.get();
			Stormback struck = dummy(helper, cleanup, StormbackEntities.STORMBACK, 4, 20);
			Stormback plain = dummy(helper, cleanup, StormbackEntities.STORMBACK, 16, 20);
			Stormback roarer = cleanup.entity(present(helper, Stormbacks.spawn(level, at(helper, 3, 1, 3), null), "a stormback"));
			Mob bait = dummy(helper, cleanup, EntityTypes.VILLAGER, 16, 3);
			Vec3[] fell = new Vec3[2];
			float[] baitHealth = new float[1];
			boolean[] roared = new boolean[1];

			step(helper, cleanup, 1, () -> {
				LightningBolt bolt = present(helper, EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT), "a lightning bolt");
				float health = struck.getHealth();
				struck.thunderHit(level, bolt);
				helper.assertTrue(struck.isCharged() && near(struck.damageMultiplier(), 1f + b.chargedDamageBonus),
						"lightning charges a stormback: its blows deal " + (1f + b.chargedDamageBonus) + "x");
				helper.assertTrue(near(struck.getHealth(), health) && !struck.isOnFire() && struck.getRemainingFireTicks() <= 0, "the lightning hurt or burned it");
				helper.assertTrue(!plain.isCharged() && near(plain.damageMultiplier(), 1f), "an unstruck stormback is not charged");
				fell[0] = struck.position();
				fell[1] = plain.position();
				struck.kill(level);
				plain.kill(level);
			});

			step(helper, cleanup, 2, () -> {
				roarer.invulnerableTime = 0;
				roarer.hurtServer(level, level.damageSources().mobAttack(bait), 0.5f);
				roarer.setHealth(roarer.getMaxHealth() * (b.roarBelow - 0.1f));
				baitHealth[0] = bait.getHealth();
				helper.assertTrue(roarer.isAngryAt(bait) && !roarer.isCharged(), "the stormback turned on the villager, uncharged");
			});

			step(helper, cleanup, 3, () -> {
				int dropped = shards(level, fell[0]);
				helper.assertTrue(dropped >= b.stormglassMin && dropped <= b.stormglassMax,
						"a charged stormback drops " + b.stormglassMin + " to " + b.stormglassMax + " stormglass shards (" + dropped + ")");
				helper.assertTrue(shards(level, fell[1]) == 0, "an uncharged stormback dropped stormglass");
			});

			watch(helper, cleanup, 4, 270, "the roar, then a charged charge", tick -> {
				TellLog log = roarer.tellLog();
				helper.assertTrue(roarer.isAlive(), "the roaring stormback died");
				helper.assertTrue(log.untold() == 0, "a heavy blow came without its tell");

				if (!roared[0]) {
					if (!roarer.hasRoared()) {
						helper.assertTrue(log.heavyHits() == 0, "it charged before its roar");
						baitHealth[0] = bait.getHealth();
						return false;
					}

					roared[0] = true;
					helper.assertTrue(log.heavyHits() == 1 && roarer.isCharged(), "the roar is its told heavy blow, and it charges it");
				}

				if (log.heavyHits() < 2) {
					baitHealth[0] = bait.getHealth();
					return false;
				}

				float lost = baitHealth[0] - bait.getHealth();
				helper.assertTrue(Math.abs(lost - b.chargedChargeDamage) < EPS, "a charged charge deals " + b.chargedChargeDamage + " (" + lost + ")");
				helper.assertTrue(bait.hasEffect(MobEffects.SLOWNESS), "a charged charge shocks");
				return true;
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	// ---------------------------------------------------------------- the mentors' true duel (12.1)

	/**
	 * 12.1: the true duel. The mentor fights at {@code health} with {@code mentor_true} (its numbers the
	 * duel's balance); the blow that would take it under {@code yieldAt} is refused and it yields, itself
	 * again. The player's death resets the duel at once; a player gone for {@code leaveTicks} resets it
	 * then. With no mob to fight the mentor yields at once.
	 */
	@GameTest(structure = ARENA, maxTicks = 260)
	public void mentorDuelYieldsAndResets(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "mentorDuelYieldsAndResets")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			MentorDuelBalance b = MentorDuelBalance.get();
			MentorEntity yielding = still(helper, cleanup, MentorEntities.JEDI_MENTOR, 4, 4);
			MentorEntity widowed = still(helper, cleanup, MentorEntities.JEDI_MENTOR, 12, 4);
			MentorEntity left = still(helper, cleanup, MentorEntities.JEDI_MENTOR, 20, 4);
			ServerPlayer winner = fighter(helper, cleanup, 4, 7);
			ServerPlayer fallen = fighter(helper, cleanup, 12, 7);
			ServerPlayer leaver = fighter(helper, cleanup, 20, 7);
			List<MentorDuel.Result> won = new ArrayList<>();
			List<MentorDuel.Result> died = new ArrayList<>();
			List<MentorDuel.Result> gone = new ArrayList<>();
			List<MentorDuel.Result> standIn = new ArrayList<>();
			float[] base = new float[1];

			step(helper, cleanup, 1, () -> {
				base[0] = yielding.getMaxHealth();
				DuelistProfile profile = DuelistProfiles.get(DuelistProfiles.MENTOR_TRUE);
				helper.assertTrue(b.strings.length >= 2, "the true duel's strings are a range");
				helper.assertTrue(profile.id().equals(DuelistProfiles.MENTOR_TRUE) && near(profile.damage(), b.damage) && near(profile.guardStamina(), b.guard)
						&& near(profile.parryChance(), b.parry), "mentor_true takes its damage, guard and parry from the duel's balance");
				helper.assertTrue(profile.stringMin() == b.strings[0] && profile.stringMax() == Math.max(b.strings[0], b.strings[1]),
						"mentor_true's strings run " + b.strings[0] + " to " + b.strings[1]);
				helper.assertTrue(profile.windupTicks() == b.windupTicks && profile.gapTicks() == b.gapTicks && profile.recoveryTicks() == b.recoveryTicks,
						"mentor_true's timings are the duel's");
				MentorDuel.start(winner, yielding, MentorDuel.Kind.SAELEN, won::add);
				MentorDuel.start(fallen, widowed, MentorDuel.Kind.SAELEN, died::add);
				MentorDuel.start(leaver, left, MentorDuel.Kind.SAELEN, gone::add);
				ServerPlayer[] players = {winner, fallen, leaver};
				MentorEntity[] mentors = {yielding, widowed, left};

				for (int i = 0; i < players.length; i++) {
					helper.assertTrue(MentorDuel.isDuelling(players[i]) && MentorDuel.isDuelling(mentors[i]), "the duel began");
					helper.assertTrue(near(mentors[i].getMaxHealth(), b.health) && near(mentors[i].getHealth(), b.health), "the mentor fights at " + b.health + " health");
					helper.assertTrue(mentors[i].profile().id().equals(DuelistProfiles.MENTOR_TRUE) && mentors[i].isSparringWith(players[i]),
							"the mentor duels its player with mentor_true");
				}
			});

			step(helper, cleanup, 2, () -> {
				helper.assertTrue(hit(level, yielding, spell(level, winner), 100f) && near(yielding.getHealth(), b.health - 100f), "the player's blow landed on the mentor");
				fallen.setHealth(0f);
			});

			step(helper, cleanup, 3, () -> {
				helper.assertTrue(hit(level, yielding, spell(level, winner), 100f) && near(yielding.getHealth(), b.health - 200f), "the player's blow landed on the mentor");
				M3TestPlayers.remove(leaver);
			});

			step(helper, cleanup, 4, () -> {
				helper.assertFalse(hit(level, yielding, spell(level, winner), 100f), "the blow that would take the mentor under " + b.yieldAt + " landed");
				helper.assertTrue(!MentorDuel.isDuelling(yielding) && !MentorDuel.isDuelling(winner), "the mentor yielded: the duel is over");
				helper.assertTrue(near(yielding.getMaxHealth(), base[0]) && near(yielding.getHealth(), base[0]), "the yielded mentor is itself again, whole");
				helper.assertTrue(yielding.profile().id().equals(DuelistProfiles.JEDI_MASTER) && !yielding.isSparring(), "the yielded mentor's profile and partner are its own again");
				helper.assertTrue(died.equals(List.of(MentorDuel.Result.RESET)), "the player's death reset the duel (" + died + ")");
				helper.assertTrue(!MentorDuel.isDuelling(widowed) && near(widowed.getMaxHealth(), base[0]), "the reset mentor is itself again");
				fallen.setHealth(fallen.getMaxHealth());
			});

			step(helper, cleanup, 5, () -> {
				helper.assertTrue(won.equals(List.of(MentorDuel.Result.YIELDED)), "the duel ended in the mentor's yield (" + won + ")");
				Entity stand = present(helper, EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.EVENT), "an armour stand");
				MentorDuel.start(winner, stand, MentorDuel.Kind.SAELEN, standIn::add);
			});

			step(helper, cleanup, 6, () -> helper.assertTrue(standIn.equals(List.of(MentorDuel.Result.YIELDED)),
					"with no mob to fight, the duel ends in a yield at once (" + standIn + ")"));

			step(helper, cleanup, 150, () -> helper.assertTrue(MentorDuel.isDuelling(left) && gone.isEmpty(),
					"the duel waits " + b.leaveTicks + " ticks for a player who left"));

			watch(helper, cleanup, 151, 240, "the reset of the duel whose player left", tick -> {
				if (gone.isEmpty()) {
					return false;
				}

				helper.assertTrue(gone.equals(List.of(MentorDuel.Result.RESET)), "the duel whose player left reset (" + gone + ")");
				helper.assertTrue(!MentorDuel.isDuelling(left) && near(left.getMaxHealth(), base[0]) && left.profile().id().equals(DuelistProfiles.JEDI_MASTER),
						"the mentor is itself again");
				return true;
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	// ---------------------------------------------------------------- Darth Ulvaine (12.2)

	/**
	 * 12.2: her three phases and the yield. No blow takes her past a phase's threshold: at
	 * {@code phase2At} the storm (a transition she takes nothing in, two chanters at the pylons that cut
	 * her damage to {@code pylonDamagePct}), at {@code phase3At} the Hymn, under {@code crescendoAt} the
	 * Crescendo's resonators, at {@code yieldAt} she kneels. The phase hook fires once a phase, the yield
	 * hook once; sparing her reports one first-clear defeat through the fight's own hooks.
	 */
	@GameTest(structure = ARENA, maxTicks = 240)
	public void ulvainePhasesAndYield(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "ulvainePhasesAndYield")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			UlvaineBalance b = UlvaineBalance.get();
			ServerPlayer owner = onlooker(helper, cleanup, 12, 17);
			CollectorHooks.Recorder hooks = new CollectorHooks.Recorder();
			UlvaineSession session = ulvaineFight(helper, cleanup, owner, false, hooks);
			DarthUlvaine u = ulvaine(helper, session);
			float max = u.getMaxHealth();
			DamageSource blow = spell(level, owner);
			float[] mark = new float[1];

			step(helper, cleanup, 1, () -> {
				helper.assertTrue(session.participants().equals(List.of(owner.getUUID())) && near(max, UlvaineRules.maxHealth(1, b)),
						"she fights the owner alone at " + UlvaineRules.maxHealth(1, b));
				helper.assertTrue(hit(level, u, blow, max), "a participant's blow landed in the duel");
				helper.assertTrue(near(u.getHealth(), max * b.phase2At), "no blow takes her past " + b.phase2At + " in the duel (" + u.getHealth() + ")");
				helper.assertTrue(session.phase() == UlvaineRules.PHASE_STORM && u.phase() == UlvaineRules.PHASE_STORM && session.isTransitioning(),
						"at " + b.phase2At + " the storm begins with its transition");
				helper.assertTrue(session.chanters().size() == 2, "two chanters stand at the pylons (" + session.chanters().size() + ")");
				helper.assertTrue(phases(helper, hooks, owner).equals(List.of(UlvaineRules.PHASE_STORM)), "the phase hook fired once, for the storm");
				mark[0] = u.getHealth();
			});

			step(helper, cleanup, 2, () -> {
				helper.assertFalse(hit(level, u, blow, 50f), "a blow landed in the transition");
				helper.assertTrue(near(u.getHealth(), mark[0]), "she lost health in the transition");
			});

			step(helper, cleanup, 3 + b.transitionTicks, () -> {
				helper.assertFalse(session.isTransitioning(), "the transition ended after " + b.transitionTicks + " ticks");
				float before = u.getHealth();
				helper.assertTrue(hit(level, u, blow, 100f), "a blow landed in the storm");
				helper.assertTrue(near(before - u.getHealth(), 100f * UlvaineRules.pylonMultiplier(true, b)),
						"while a chanter stands she takes " + b.pylonDamagePct + "% (" + (before - u.getHealth()) + ")");

				for (Hymnsworn chanter : session.chanters()) {
					chanter.kill(level);
				}
			});

			step(helper, cleanup, 4 + b.transitionTicks, () -> {
				helper.assertTrue(session.chanters().isEmpty(), "the chanters fell");
				float before = u.getHealth();
				helper.assertTrue(hit(level, u, blow, 100f) && near(before - u.getHealth(), 100f), "with the chanters down she takes the whole blow");
			});

			step(helper, cleanup, 5 + b.transitionTicks, () -> {
				helper.assertTrue(hit(level, u, blow, max), "a blow landed in the storm");
				helper.assertTrue(near(u.getHealth(), max * b.phase3At), "no blow takes her past " + b.phase3At + " in the storm (" + u.getHealth() + ")");
				helper.assertTrue(session.phase() == UlvaineRules.PHASE_HYMN && session.isTransitioning(), "at " + b.phase3At + " the Hymn begins");
				helper.assertTrue(phases(helper, hooks, owner).equals(List.of(UlvaineRules.PHASE_STORM, UlvaineRules.PHASE_HYMN)), "the phase hook fired once more, for the Hymn");
			});

			int hymn = 8 + 2 * b.transitionTicks;

			step(helper, cleanup, hymn, () -> {
				helper.assertFalse(session.isTransitioning(), "the Hymn's transition ended");
				helper.assertTrue(hit(level, u, blow, u.getHealth() - (max * b.crescendoAt - 10f)), "a blow landed in the Hymn");
				helper.assertTrue(session.crescendoBegun() && session.resonators().size() == heart(helper).resonators().size(),
						"under " + b.crescendoAt + " the Crescendo lights the resonators (" + session.resonators().size() + ")");
				helper.assertTrue(hooks.calls(CollectorHooks.Kind.YIELDED).isEmpty() && !session.isYielded(), "she yielded early");
			});

			last(helper, cleanup, hymn + 1, () -> {
				helper.assertTrue(hit(level, u, blow, max), "a blow landed in the Hymn");
				helper.assertTrue(near(u.getHealth(), UlvaineRules.floor(max, b)), "no blow takes her under " + b.yieldAt + " (" + u.getHealth() + ")");
				helper.assertTrue(session.isYielded() && u.isKneeling() && UlvaineFight.yieldOpen(owner.getUUID()), "at " + b.yieldAt + " she kneels and the choice opens");
				List<CollectorHooks.Call> yields = hooks.calls(CollectorHooks.Kind.YIELDED);
				helper.assertTrue(yields.size() == 1 && yields.get(0).player().equals(owner.getUUID()) && UlvaineFight.BOSS_ID.equals(yields.get(0).bossId()),
						"the yield hook fired once, for the owner");
				helper.assertTrue(session.resonators().isEmpty() && session.chanters().isEmpty(), "the yield clears the hall");
				helper.assertFalse(hit(level, u, blow, 10f), "a blow landed while she kneels");
				CollectorHooks.Recorder fallback = new CollectorHooks.Recorder();
				UlvaineFight.resolve(owner, BossOutcome.SPARED, fallback);
				List<CollectorHooks.Call> defeated = hooks.calls(CollectorHooks.Kind.DEFEATED);
				helper.assertTrue(defeated.size() == 1, "sparing her reported " + defeated.size() + " defeats");
				CollectorHooks.Call call = defeated.get(0);
				helper.assertTrue(call.player().equals(owner.getUUID()) && UlvaineFight.BOSS_ID.equals(call.bossId()) && call.outcome() == BossOutcome.SPARED
						&& owner.getUUID().equals(call.owner()) && call.firstClear(), "sparing her is the owner's first clear (" + call + ")");
				helper.assertTrue(fallback.calls().isEmpty() && !UlvaineFight.yieldOpen(owner.getUUID()), "the running fight reports through its own hooks, and the choice is made");
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.2: her health is {@code maxHealth} plus {@code perParticipant} for each participant after the
	 * first, up to {@code scalingCap}. Past the 1024 attribute cap the attribute stops and her blows are
	 * scaled instead. Live: the owner and a helper begin; a helper who walks in during the duel joins
	 * (she stays whole); of two more, only one finds a place.
	 */
	@GameTest(structure = ARENA, maxTicks = 60)
	public void ulvaineHealthScalingAndCap(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "ulvaineHealthScalingAndCap")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			UlvaineBalance fresh = new UlvaineBalance();
			double[] scaled = {600, 960, 1320, 1680, 1680};

			for (int n = 1; n <= scaled.length; n++) {
				helper.assertTrue(near(UlvaineRules.maxHealth(n, fresh), scaled[n - 1]), n + " participants: " + scaled[n - 1] + " (" + UlvaineRules.maxHealth(n, fresh) + ")");
			}

			helper.assertTrue(near(UlvaineRules.attributeHealth(1320), 1024) && near(UlvaineRules.damageScale(1320), 1024.0 / 1320.0)
					&& near(UlvaineRules.damageScale(960), 1), "past 1024 the attribute stops and the blows on her scale");
			helper.assertTrue(UlvaineRules.phaseOf(fresh.phase2At + 0.01f, fresh) == UlvaineRules.PHASE_DUEL && UlvaineRules.phaseOf(fresh.phase2At, fresh) == UlvaineRules.PHASE_STORM
					&& UlvaineRules.phaseOf(fresh.phase3At + 0.01f, fresh) == UlvaineRules.PHASE_STORM && UlvaineRules.phaseOf(fresh.phase3At, fresh) == UlvaineRules.PHASE_HYMN,
					"the phases turn at " + fresh.phase2At + " and " + fresh.phase3At);
			helper.assertTrue(near(UlvaineRules.floor(600f, fresh), 60f), "she yields at 10%");
			helper.assertTrue(near(UlvaineRules.pylonMultiplier(true, fresh), 0.2f) && near(UlvaineRules.pylonMultiplier(false, fresh), 1f), "a standing chanter cuts her damage to 20%");
			helper.assertTrue(UlvaineRules.crescendoDue(0.2f, UlvaineRules.PHASE_HYMN, false, fresh) && !UlvaineRules.crescendoDue(0.21f, UlvaineRules.PHASE_HYMN, false, fresh)
					&& !UlvaineRules.crescendoDue(0.1f, UlvaineRules.PHASE_STORM, false, fresh) && !UlvaineRules.crescendoDue(0.1f, UlvaineRules.PHASE_HYMN, true, fresh),
					"the Crescendo comes once, at 20% in the Hymn");

			ServerLevel level = helper.getLevel();
			UlvaineBalance b = UlvaineBalance.get();
			ServerPlayer owner = onlooker(helper, cleanup, 12, 17);
			ServerPlayer first = onlooker(helper, cleanup, 12, 7);
			ServerPlayer second = onlooker(helper, cleanup, 0, 0);
			ServerPlayer third = onlooker(helper, cleanup, 23, 0);
			ServerPlayer fourth = onlooker(helper, cleanup, 0, 23);
			UlvaineSession session = ulvaineFight(helper, cleanup, owner, false, new CollectorHooks.Recorder());
			DarthUlvaine u = ulvaine(helper, session);
			float[] mark = new float[1];

			step(helper, cleanup, 1, () -> {
				double two = UlvaineRules.maxHealth(2, b);
				helper.assertTrue(session.participants().size() == 2 && session.isParticipant(first.getUUID()), "the owner and the helper inside are listed");
				helper.assertTrue(near(session.maxHealth(), two) && near(u.scaledMaxHealth(), two) && near(u.getMaxHealth(), UlvaineRules.attributeHealth(two))
						&& near(u.getHealth(), u.getMaxHealth()), "two participants: " + two + " (" + u.getMaxHealth() + ")");
				moveTo(helper, second, 8, 12);
			});

			step(helper, cleanup, 3, () -> {
				double three = UlvaineRules.maxHealth(3, b);
				helper.assertTrue(session.participants().size() == 3 && session.isParticipant(second.getUUID()), "a helper who came in during the duel joined");
				helper.assertTrue(near(u.scaledMaxHealth(), three) && near(u.getMaxHealth(), UlvaineRules.attributeHealth(three)) && near(u.getHealth(), u.getMaxHealth()),
						"three: " + three + ", the attribute capped, and she is still whole (" + u.getHealth() + ")");
				float before = u.getHealth();
				helper.assertTrue(hit(level, u, spell(level, owner), 11f), "a participant's blow landed");
				helper.assertTrue(near(before - u.getHealth(), 11f * UlvaineRules.damageScale(three)),
						"past the cap a blow is scaled by " + UlvaineRules.damageScale(three) + " (" + (before - u.getHealth()) + ")");
				mark[0] = u.getHealth();
				moveTo(helper, third, 16, 12);
				moveTo(helper, fourth, 12, 9);
			});

			last(helper, cleanup, 5, () -> {
				double four = UlvaineRules.maxHealth(b.scalingCap, b);
				helper.assertTrue(session.participants().size() == b.scalingCap, "the list stops at " + b.scalingCap + " (" + session.participants().size() + ")");
				helper.assertTrue(session.isParticipant(third.getUUID()) != session.isParticipant(fourth.getUUID()), "exactly one of the two late helpers found a place");
				helper.assertTrue(near(u.scaledMaxHealth(), four) && near(u.getMaxHealth(), UlvaineRules.attributeHealth(four)) && near(u.getHealth(), mark[0]),
						"at the cap: " + four + ", her health where it was");
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.2: {@code UlvaineFight.resolve} reports the defeat. With no fight running the owner's own hook
	 * fires (a first clear); in a fight the yield hook fires once for the owner, and the choice reports
	 * one defeat per participant (the owner's the first clear) through the fight's hooks, then the bound
	 * scene plays and she is gone.
	 */
	@GameTest(structure = ARENA, maxTicks = 120)
	public void resolveCallsHook(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "resolveCallsHook")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerPlayer stranger = onlooker(helper, cleanup, 2, 2);
			CollectorHooks.Recorder alone = new CollectorHooks.Recorder();
			UlvaineFight.resolve(stranger, BossOutcome.EXECUTED, alone);
			List<CollectorHooks.Call> own = alone.calls(CollectorHooks.Kind.DEFEATED);
			helper.assertTrue(own.size() == 1 && own.get(0).player().equals(stranger.getUUID()) && UlvaineFight.BOSS_ID.equals(own.get(0).bossId())
					&& own.get(0).outcome() == BossOutcome.EXECUTED && stranger.getUUID().equals(own.get(0).owner()) && own.get(0).firstClear(),
					"with no fight running, the owner's own defeat hook fires (" + own + ")");

			ServerPlayer owner = onlooker(helper, cleanup, 12, 17);
			ServerPlayer ally = onlooker(helper, cleanup, 12, 7);
			CollectorHooks.Recorder hooks = new CollectorHooks.Recorder();
			UlvaineSession session = ulvaineFight(helper, cleanup, owner, false, hooks);
			DarthUlvaine u = ulvaine(helper, session);

			step(helper, cleanup, 1, () -> {
				helper.assertTrue(UlvaineFight.forceYield(owner) && session.isYielded(), "she kneels");
				List<CollectorHooks.Call> yields = hooks.calls(CollectorHooks.Kind.YIELDED);
				helper.assertTrue(yields.size() == 1 && yields.get(0).player().equals(owner.getUUID()) && UlvaineFight.BOSS_ID.equals(yields.get(0).bossId()),
						"the yield hook fired once, for the owner (" + yields + ")");
				helper.assertTrue(UlvaineFight.yieldOpen(owner.getUUID()), "the choice is open");
			});

			step(helper, cleanup, 2, () -> {
				CollectorHooks.Recorder fallback = new CollectorHooks.Recorder();
				UlvaineFight.resolve(owner, BossOutcome.BOUND, fallback);
				helper.assertTrue(fallback.calls().isEmpty(), "a running fight reports through its own hooks");
				List<CollectorHooks.Call> defeated = hooks.calls(CollectorHooks.Kind.DEFEATED);
				helper.assertTrue(defeated.size() == 2 && hooks.defeatedCount(owner.getUUID()) == 1 && hooks.defeatedCount(ally.getUUID()) == 1,
						"one defeat for each participant (" + defeated + ")");

				for (CollectorHooks.Call call : defeated) {
					boolean isOwner = call.player().equals(owner.getUUID());
					helper.assertTrue(UlvaineFight.BOSS_ID.equals(call.bossId()) && call.outcome() == BossOutcome.BOUND && owner.getUUID().equals(call.owner())
							&& call.firstClear() == isOwner, "the owner's defeat is the first clear, the helper's not (" + call + ")");
				}

				helper.assertFalse(UlvaineFight.yieldOpen(owner.getUUID()), "the choice is made");
			});

			watch(helper, cleanup, 3, 100, "the bound scene's end", tick -> UlvaineFight.session(owner.getUUID()) == null && u.isRemoved());
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.2: the echo pays once a day ({@code echoXp}, one more echo for the deed). The echo gathers
	 * before it fights, never yields and reports no phase; at its fall each participant is paid for the
	 * day and the defeat hook reports {@code ECHO}, never a first clear; then it comes apart.
	 */
	@GameTest(structure = ARENA, maxTicks = 160)
	public void echoOncePerDay(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "echoOncePerDay")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			UlvaineBalance b = UlvaineBalance.get();
			RaidData once = RaidData.EMPTY.withEcho(5);
			helper.assertTrue(once.echoOn(5) && !once.echoOn(6) && once.echoes() == 1, "an echo is recorded for its day");
			helper.assertTrue(!UlvaineRules.echoDue(5, 5) && UlvaineRules.echoDue(5, 6), "the echo comes once a day");

			ServerPlayer payer = onlooker(helper, cleanup, 2, 2);
			Xp start = Xp.of(payer);
			UlvaineFight.payEcho(payer, 100);
			Xp paid = Xp.of(payer);
			helper.assertTrue(RaidData.of(payer).echoes() == 1 && RaidData.of(payer).echoOn(100) && gained(start, paid) == b.echoXp,
					"the day's first echo pays " + b.echoXp + " (" + gained(start, paid) + ")");
			UlvaineFight.payEcho(payer, 100);
			Xp again = Xp.of(payer);
			helper.assertTrue(RaidData.of(payer).echoes() == 1 && gained(paid, again) == 0, "a second echo the same day pays nothing");
			UlvaineFight.payEcho(payer, 101);
			helper.assertTrue(RaidData.of(payer).echoes() == 2 && gained(again, Xp.of(payer)) == b.echoXp, "the next day's echo pays again");

			ServerPlayer owner = onlooker(helper, cleanup, 12, 17);
			CollectorHooks.Recorder hooks = new CollectorHooks.Recorder();
			UlvaineSession session = ulvaineFight(helper, cleanup, owner, true, hooks);
			DarthUlvaine u = ulvaine(helper, session);

			step(helper, cleanup, 1, () -> {
				helper.assertTrue(session.isEcho() && session.isForming(), "the echo gathers first");
				helper.assertFalse(hit(level, u, spell(level, owner), 10f), "a blow landed on the gathering echo");
			});

			step(helper, cleanup, 2, () -> {
				helper.assertTrue(UlvaineFight.phase(owner, UlvaineRules.PHASE_HYMN) && session.phase() == UlvaineRules.PHASE_HYMN, "the echo jumps to her Hymn");
				helper.assertFalse(UlvaineFight.forceYield(owner), "the echo yielded");
			});

			last(helper, cleanup, 6 + b.transitionTicks, () -> {
				helper.assertFalse(session.isTransitioning(), "the Hymn's transition ended");
				hit(level, u, spell(level, owner), u.getMaxHealth() * 2f);
				List<CollectorHooks.Call> defeated = hooks.calls(CollectorHooks.Kind.DEFEATED);
				helper.assertTrue(defeated.size() == 1 && defeated.get(0).player().equals(owner.getUUID()) && defeated.get(0).outcome() == BossOutcome.ECHO
						&& !defeated.get(0).firstClear() && UlvaineFight.BOSS_ID.equals(defeated.get(0).bossId()), "the echo's fall reports ECHO, never a first clear (" + defeated + ")");
				helper.assertTrue(hooks.calls(CollectorHooks.Kind.PHASE).isEmpty() && hooks.calls(CollectorHooks.Kind.YIELDED).isEmpty(), "the echo reported a phase or a yield");
				helper.assertTrue(RaidData.of(owner).echoOn(Clocks.day(level.getServer())), "the echo was paid for today");
				helper.assertTrue(UlvaineFight.session(owner.getUUID()) == null && u.isRemoved(), "the echo came apart");
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	// ---------------------------------------------------------------- the Long Hymn (12.3)

	/**
	 * 12.3: the raid's scaling for one to eight participants (the Cantor, each echo, the shades, the
	 * basalt marks) and its join window. Live: seven players inside when the Hymn begins are listed; an
	 * eighth who walks in during the window joins and everything rescales, keeping its fraction; a ninth
	 * does not. Past the 1024 cap the blows on him are scaled; an unlisted player cannot hurt him.
	 */
	@GameTest(structure = ARENA, maxTicks = 80)
	public void raidParticipantsAndScalingOneToEight(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "raidParticipantsAndScalingOneToEight")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			RaidBalance fresh = new RaidBalance();

			for (int n = 1; n <= 8; n++) {
				double factor = 1.0 + 0.5 * (n - 1);
				helper.assertTrue(near(RaidRules.maxHealth(n, fresh), 2000 * factor) && near(RaidRules.choirHealth(n, fresh), 60 * factor),
						n + " participants: the Cantor at " + 2000 * factor + ", each echo at " + 60 * factor);
			}

			helper.assertTrue(near(RaidRules.maxHealth(9, fresh), RaidRules.maxHealth(8, fresh)), "the scaling stops at eight");
			helper.assertTrue(RaidRules.shades(1, fresh) == 2 && RaidRules.shades(2, fresh) == 2 && RaidRules.shades(4, fresh) == 3 && RaidRules.shades(8, fresh) == 5,
					"two shades a wave, one more for every two participants past two");
			helper.assertTrue(RaidRules.basaltMarks(1, fresh) == 5 && RaidRules.basaltMarks(4, fresh) == 5 && RaidRules.basaltMarks(5, fresh) == 6
					&& RaidRules.basaltMarks(8, fresh) == 9, "five basalt marks, one more for each participant past four");
			helper.assertTrue(RaidRules.mayJoin(fresh.joinTicks, 7, fresh) && !RaidRules.mayJoin(fresh.joinTicks + 1, 7, fresh) && !RaidRules.mayJoin(0, 8, fresh),
					"players join in the first " + fresh.joinTicks + " ticks, eight at most");

			ServerLevel level = helper.getLevel();
			RaidBalance b = RaidBalance.get();
			int[][] spots = {{2, 1}, {6, 1}, {10, 1}, {14, 1}, {18, 1}, {21, 1}, {2, 12}};
			List<ServerPlayer> inside = new ArrayList<>();

			for (int[] spot : spots) {
				inside.add(fighter(helper, cleanup, spot[0], spot[1]));
			}

			ServerPlayer late = fighter(helper, cleanup, 4, 21);
			ServerPlayer tooLate = fighter(helper, cleanup, 10, 22);
			CantorSession session = hymn(helper, cleanup, undercroft(helper, 18));

			step(helper, cleanup, 1, () -> {
				FirstCantor c = cantor(helper, session);
				double seven = RaidRules.maxHealth(spots.length, b);
				helper.assertTrue(session.count() == spots.length && !session.isParticipant(late), "everyone inside when the Hymn began is listed (" + session.count() + ")");
				helper.assertTrue(near(c.scaledMaxHealth(), seven) && near(c.getMaxHealth(), RaidRules.attributeHealth(seven)) && near(c.getHealth(), c.getMaxHealth()),
						spots.length + " participants: the Cantor at " + seven + " (" + c.getMaxHealth() + " in the attribute)");
				List<CantorChoir> choir = session.choir();
				helper.assertTrue(choir.size() == 4, "the four echoes stand (" + choir.size() + ")");

				for (CantorChoir echo : choir) {
					helper.assertTrue(near(echo.getMaxHealth(), RaidRules.choirHealth(spots.length, b)), "each echo at " + RaidRules.choirHealth(spots.length, b));
				}

				moveTo(helper, late, 6, 12);
			});

			step(helper, cleanup, 3, () -> {
				FirstCantor c = cantor(helper, session);
				double eight = RaidRules.maxHealth(spots.length + 1, b);
				helper.assertTrue(session.count() == spots.length + 1 && session.isParticipant(late), "a player who came in during the join window was listed");
				helper.assertTrue(near(c.scaledMaxHealth(), eight) && near(c.getMaxHealth(), RaidRules.attributeHealth(eight)) && near(c.getHealth(), c.getMaxHealth()),
						"the Cantor rescaled to " + eight + ", still whole");

				for (CantorChoir echo : session.choir()) {
					helper.assertTrue(near(echo.getMaxHealth(), RaidRules.choirHealth(spots.length + 1, b)) && near(echo.getHealth(), echo.getMaxHealth()),
							"each echo rescaled to " + RaidRules.choirHealth(spots.length + 1, b) + ", still whole");
				}

				moveTo(helper, tooLate, 16, 12);
			});

			last(helper, cleanup, 5, () -> {
				FirstCantor c = cantor(helper, session);
				helper.assertTrue(session.count() == b.maxPlayers && !session.isParticipant(tooLate), "the raid lists at most " + b.maxPlayers + " (" + session.count() + ")");
				session.skipRise();

				for (CantorChoir echo : session.choir()) {
					echo.kill(level);
				}

				float before = c.getHealth();
				helper.assertTrue(hit(level, c, spell(level, inside.get(0)), 90f), "a participant's blow landed");
				float dealt = before - c.getHealth();
				helper.assertTrue(Math.abs(dealt - 90f * RaidRules.damageScale(RaidRules.maxHealth(b.maxPlayers, b))) < EPS,
						"past the cap a blow on him is scaled by " + RaidRules.damageScale(RaidRules.maxHealth(b.maxPlayers, b)) + " (" + dealt + ")");
				float after = c.getHealth();
				helper.assertFalse(hit(level, c, spell(level, tooLate), 90f), "an unlisted player's blow landed");
				helper.assertTrue(near(c.getHealth(), after), "an unlisted player hurt him");
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.3: the raid week is seven days; the first clear of a week pays in full, a second that week only
	 * {@code helpXp}, the next week in full again; a schematic comes on a {@code schematicChance} roll and
	 * surely after {@code schematicPity} clears without one (the pity then starts over). The days and
	 * weeks are arguments: no clock is touched.
	 */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void raidWeeklyLockoutAndPity(GameTestHelper helper) {
		Cleanup cleanup = cleanup(helper);

		try {
			RaidBalance b = RaidBalance.get();
			RaidBalance fresh = new RaidBalance();
			helper.assertTrue(RaidRules.week(0, fresh) == 0 && RaidRules.week(6, fresh) == 0 && RaidRules.week(7, fresh) == 1 && RaidRules.week(13, fresh) == 1
					&& RaidRules.week(14, fresh) == 2, "a raid week is seven days");
			helper.assertTrue(RaidRules.schematicDue(2, 0.99f, fresh) && !RaidRules.schematicDue(1, 0.99f, fresh) && RaidRules.schematicDue(0, 0.1f, fresh)
					&& !RaidRules.schematicDue(1, 0.25f, fresh), "a schematic comes on a 25% roll, and surely after two clears without one");
			RaidData cleared = RaidData.EMPTY.withClear(3, 1);
			helper.assertTrue(cleared.clearedIn(3) && !cleared.clearedIn(4) && cleared.clears() == 1 && cleared.pity() == 1, "a clear is recorded for its week");

			last(helper, cleanup, 1, () -> {
				ServerPlayer newcomer = onlooker(helper, cleanup, 4, 4);
				helper.assertTrue(RaidRewards.pay(newcomer, 12) && RaidData.of(newcomer).clears() == 1 && RaidData.of(newcomer).clearedIn(12),
						"a player's first clear is reported as the first and recorded for its week");

				ServerPlayer veteran = onlooker(helper, cleanup, 8, 4);
				RaidData.set(veteran, new RaidData(5, 0, 1, -1, 0));
				Xp start = Xp.of(veteran);
				helper.assertFalse(RaidRewards.pay(veteran, 12), "a veteran's clear was reported as a first");
				Xp paid = Xp.of(veteran);
				helper.assertTrue(gained(start, paid) == b.clearXp && RaidData.of(veteran).clearedIn(12), "the week's first clear pays " + b.clearXp);
				helper.assertFalse(RaidRewards.pay(veteran, 12), "a second clear was reported as a first");
				Xp helped = Xp.of(veteran);
				helper.assertTrue(gained(paid, helped) == b.helpXp && RaidData.of(veteran).clears() == 2,
						"a second clear the same week pays only " + b.helpXp + " (" + gained(paid, helped) + ")");
				RaidRewards.pay(veteran, 13);
				helper.assertTrue(gained(helped, Xp.of(veteran)) == b.clearXp && RaidData.of(veteran).clears() == 3, "the next week's clear pays in full");

				ServerPlayer unlucky = onlooker(helper, cleanup, 12, 4);
				RaidData.set(unlucky, new RaidData(5, b.schematicPity, 1, -1, 0));
				int unknown = RaidRewards.unknownSchematics(unlucky).size();
				helper.assertTrue(unknown > 0, "a new player knows none of the raid's schematics");
				RaidRewards.pay(unlucky, 12);
				helper.assertTrue(RaidData.of(unlucky).pity() == 0 && RaidRewards.unknownSchematics(unlucky).size() == unknown - 1,
						"after " + b.schematicPity + " clears without a schematic the next is sure, and the pity starts over");
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.3: a participant gone for {@code participantLeaveTicks} leaves the list; with nobody listed
	 * inside for {@code wipeResetTicks} the Hymn wipes: he is gone, no clear, and the next keystone
	 * starts afresh.
	 */
	@GameTest(structure = ARENA, maxTicks = 280)
	public void raidWipeResets(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "raidWipeResets")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			RaidBalance b = RaidBalance.get();
			UndercroftLayout layout = undercroft(helper, 18);
			ServerPlayer player = fighter(helper, cleanup, 12, 4);
			CantorSession session = hymn(helper, cleanup, layout);
			FirstCantor[] cantor = new FirstCantor[1];
			long[] leftAt = {-1};

			step(helper, cleanup, 1, () -> {
				cantor[0] = cantor(helper, session);
				helper.assertTrue(raid(helper, session).count() == 1 && session.isParticipant(player), "the one player inside is listed");
				moveTo(helper, player, 12, 21);
			});

			watch(helper, cleanup, 2, 260, "the wipe", tick -> {
				RaidSession raid = raid(helper, session);

				if (leftAt[0] < 0) {
					helper.assertFalse(session.isOver(), "the Hymn stopped while its participant was still listed");

					if (raid.count() == 0) {
						leftAt[0] = tick;
						helper.assertTrue(tick >= b.participantLeaveTicks, "the participant left the list after " + (tick - 1) + " ticks away (" + b.participantLeaveTicks + ")");
					}

					return false;
				}

				if (!session.isOver()) {
					return false;
				}

				helper.assertTrue(tick >= b.wipeResetTicks, "the Hymn wiped after " + (tick - 1) + " empty ticks (" + b.wipeResetTicks + ")");
				helper.assertTrue(!session.isCleared() && cantor[0].isRemoved() && raid.isEnded() && LongHymn.sessionIn(level, layout) == null,
						"a wipe ends the Hymn: he is gone, and nothing is cleared");
				CantorSession again = LongHymn.start(level, layout, null, 0);
				helper.assertTrue(again != null, "the next keystone starts the Hymn afresh");
				again.stop(true);
				return true;
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	// ---------------------------------------------------------------- kill reports, Stasis

	/**
	 * 12.1, 5.2: each new foe reports a player's kill once, with its kind, its faction (the cartel's and
	 * the clan's; none for the three unaligned) and whether the fight counted as hostile when the blow
	 * landed (the stormback is neutral: no). A Hymn shade and a death nobody caused report nothing.
	 */
	@GameTest(structure = ARENA, maxTicks = 120)
	public void newFoesReportKills(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "newFoesReportKills")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			ServerPlayer killer = fake(helper, 12, 4);
			Identity.setReputation(killer, Faction.VESSBA_KAJIDIC, -600);
			Identity.setReputation(killer, Faction.CLAN_HASK, -600);
			SandScour scour = cleanup.entity(present(helper, Scours.spawn(level, at(helper, 12, 1, 7), false, null), "a scour"));
			CartelEnforcer cartel = dummy(helper, cleanup, CartelEntities.CARTEL_ENFORCER, 3, 12);
			HaskWarrior hask = dummy(helper, cleanup, HaskEntities.HASK_WARRIOR, 6, 12);
			Hymnsworn hymnsworn = dummy(helper, cleanup, HymnEntities.HYMNSWORN, 9, 12);
			Hymnsworn shade = dummy(helper, cleanup, HymnEntities.HYMNSWORN, 15, 12);
			shade.setShade(true);
			Stormback stormback = dummy(helper, cleanup, StormbackEntities.STORMBACK, 19, 9);
			Hymnsworn unclaimed = dummy(helper, cleanup, HymnEntities.HYMNSWORN, 3, 18);
			List<UUID> victims = List.of(cartel.getUUID(), hask.getUUID(), hymnsworn.getUUID(), shade.getUUID(), stormback.getUUID(), scour.getUUID(),
					unclaimed.getUUID());
			List<FoeKills.Report> reports = new ArrayList<>();
			cleanup.then(FoeKills.listen(report -> {
				if (victims.contains(report.victim())) {
					reports.add(report);
				}
			}));

			watch(helper, cleanup, 1, 100, "the scour's surfacing, for the kills", tick -> {
				if (scour.getTarget() != killer) {
					scour.setTarget(killer);
				}

				if (scour.isBurrowed()) {
					return false;
				}

				DamageSource blow = level.damageSources().playerAttack(killer);

				for (LivingEntity victim : List.<LivingEntity>of(cartel, hask, hymnsworn, shade, stormback, scour)) {
					hit(level, victim, blow, 10000f);
					helper.assertFalse(victim.isAlive(), victim.getType().getDescriptionId() + " lived through the killing blow");
				}

				hit(level, unclaimed, level.damageSources().fellOutOfWorld(), 10000f);
				helper.assertFalse(unclaimed.isAlive(), "the unclaimed Hymnsworn lived");
				helper.assertTrue(reports.size() == 5, "five kills reported (" + reports + ")");
				reported(helper, reports, cartel, killer, CartelEntities.ID, Faction.VESSBA_KAJIDIC, true);
				reported(helper, reports, hask, killer, HaskEntities.ID, Faction.CLAN_HASK, true);
				reported(helper, reports, hymnsworn, killer, HymnEntities.HYMNSWORN_ID, null, true);
				reported(helper, reports, scour, killer, ScourEntities.ID, null, true);
				reported(helper, reports, stormback, killer, StormbackEntities.ID, null, false);
				return true;
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/** 9.2, 12.2: Ulvaine breaks any Stasis after {@code stasisMaxTicks}, however long it was cast for. */
	@GameTest(structure = ARENA, maxTicks = 80)
	public void ulvaineBreaksStasisAfter20Ticks(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "ulvaineBreaksStasisAfter20Ticks")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			UlvaineBalance b = UlvaineBalance.get();
			ServerPlayer owner = onlooker(helper, cleanup, 12, 17);
			UlvaineSession session = ulvaineFight(helper, cleanup, owner, false, new CollectorHooks.Recorder());
			DarthUlvaine u = ulvaine(helper, session);
			cleanup.then(() -> StasisHolds.release(u));

			step(helper, cleanup, 1, () -> {
				int ticks = StasisHolds.apply(u, null, 200, 0);
				helper.assertTrue(ticks > b.stasisMaxTicks + 5 && StasisHolds.isFrozen(u), "a Stasis longer than " + b.stasisMaxTicks + " ticks took her (" + ticks + ")");
			});

			step(helper, cleanup, b.stasisMaxTicks - 4, () -> helper.assertTrue(StasisHolds.isFrozen(u) && u.stasisTicks() > 0,
					"she is still held " + (b.stasisMaxTicks - 5) + " ticks in"));

			last(helper, cleanup, b.stasisMaxTicks + 6, () -> {
				helper.assertFalse(StasisHolds.isFrozen(u), "she was still held after " + b.stasisMaxTicks + " ticks");
				helper.assertTrue(u.stasisTicks() == 0, "her Stasis count started over");
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	/**
	 * 12.3: the First Cantor's three phases. The choir's song shields him (a blow is turned, the shield
	 * shows) until the echoes fall. The verses: Stormwood's roots told under a participant, then holding
	 * them with Slowness IV until an ally frees them; Mire's fog pools; Basalt's marks, more for more
	 * players. The Hymn: the engine's first ring trembles, then sings; a participant crouching at a
	 * silencing point for {@code silenceTicks} opens his window, and in it he takes double.
	 */
	@GameTest(structure = ARENA, maxTicks = 260)
	public void cantorChoirShieldVersesRingsAndWindow(GameTestHelper helper) {
		if (skippedOnPeaceful(helper, "cantorChoirShieldVersesRingsAndWindow")) {
			return;
		}

		Cleanup cleanup = cleanup(helper);

		try {
			ServerLevel level = helper.getLevel();
			RaidBalance b = RaidBalance.get();
			UndercroftLayout layout = undercroft(helper, 23);
			// The channeller crouches on the first silencing point from the start; the striker lands every blow.
			ServerPlayer channeller = fighter(helper, cleanup, 3, 3);
			channeller.setShiftKeyDown(true);
			ServerPlayer striker = fighter(helper, cleanup, 21, 6);
			CantorSession session = hymn(helper, cleanup, layout);
			double scaled = RaidRules.maxHealth(2, b);
			int rooted = 4 + b.rootsTell + 3;
			int hymn = rooted + 3;
			int sings = hymn + b.ringTell + 3;
			float[] plain = new float[1];

			step(helper, cleanup, 1, () -> {
				FirstCantor c = cantor(helper, session);
				session.skipRise();
				helper.assertTrue(session.count() == 2 && near(c.scaledMaxHealth(), scaled), "two participants: the Cantor at " + scaled);
				helper.assertTrue(session.choir().size() == 4 && session.shielded(), "the choir sings and shields him");
				float health = c.getHealth();
				helper.assertFalse(hit(level, c, spell(level, striker), 30f), "a blow landed through the choir's shield");
				helper.assertTrue(near(c.getHealth(), health), "he lost health behind the shield");
			});

			step(helper, cleanup, 2, () -> {
				helper.assertTrue(cantor(helper, session).isShielded(), "his shield shows while the choir sings");

				for (CantorChoir echo : session.choir()) {
					echo.kill(level);
				}

				helper.assertTrue(session.choir().isEmpty() && !session.shielded(), "with the choir fallen his shield is down");
			});

			step(helper, cleanup, 3, () -> {
				FirstCantor c = cantor(helper, session);
				helper.assertFalse(c.isShielded(), "his shield still shows");
				float health = c.getHealth();
				helper.assertTrue(hit(level, c, spell(level, striker), 30f), "a blow landed once the choir fell");
				helper.assertTrue(Math.abs(health - c.getHealth() - 30f * RaidRules.damageScale(scaled)) < EPS, "the blow is scaled for two (" + (health - c.getHealth()) + ")");
			});

			step(helper, cleanup, 4, () -> {
				FirstCantor c = cantor(helper, session);
				helper.assertTrue(session.forcePhase(RaidRules.PHASE_VERSES) && session.phase() == RaidRules.PHASE_VERSES, "the verses begin");
				session.skipTransition();
				session.verses().forceVerse(c, CantorVerses.Verse.STORMWOOD);
				Vec3 roots = present(helper, session.verses().rootsTelling(), "Stormwood's circle");
				boolean underOne = near(roots.x, channeller.getX()) && near(roots.z, channeller.getZ()) || near(roots.x, striker.getX()) && near(roots.z, striker.getZ());
				helper.assertTrue(underOne && near(roots.y, layout.cantor().getY()), "Stormwood's roots are told under a participant, on the floor (" + roots + ")");
			});

			step(helper, cleanup, rooted, () -> {
				List<ServerPlayer> held = new ArrayList<>();

				for (ServerPlayer p : List.of(channeller, striker)) {
					if (session.verses().isRooted(p)) {
						held.add(p);
					}
				}

				helper.assertTrue(held.size() == 1, "the roots took the one participant in their circle (" + held.size() + ")");
				ServerPlayer caught = held.get(0);
				MobEffectInstance slow = caught.getEffect(MobEffects.SLOWNESS);
				// The roots' grip is Slowness IV (amplifier 3).
				helper.assertTrue(slow != null && slow.getAmplifier() == 3, "the roots hold with Slowness IV");
				helper.assertTrue(session.verses().free(caught) && !session.verses().isRooted(caught), "an ally's blow frees them");
				MobEffectInstance after = caught.getEffect(MobEffects.SLOWNESS);
				helper.assertTrue(after == null || after.getAmplifier() != 3, "the roots' Slowness stayed on the freed player");
			});

			step(helper, cleanup, rooted + 1, () -> {
				session.verses().forceVerse(cantor(helper, session), CantorVerses.Verse.MIRE);
				helper.assertTrue(session.verses().pools().size() == b.fogPools, "Mire spreads " + b.fogPools + " fog pools (" + session.verses().pools().size() + ")");
			});

			step(helper, cleanup, rooted + 2, () -> {
				session.verses().forceVerse(cantor(helper, session), CantorVerses.Verse.BASALT);
				helper.assertTrue(session.verses().marks().size() == RaidRules.basaltMarks(2, b),
						"Basalt marks " + RaidRules.basaltMarks(2, b) + " spots for two (" + session.verses().marks().size() + ")");
			});

			step(helper, cleanup, hymn, () -> {
				FirstCantor c = cantor(helper, session);
				helper.assertTrue(session.forcePhase(RaidRules.PHASE_HYMN) && session.phase() == RaidRules.PHASE_HYMN, "the Hymn begins");
				session.skipTransition();
				helper.assertTrue(session.verses().pools().isEmpty() && session.verses().marks().isEmpty(), "the verses fall quiet in the Hymn");
				helper.assertTrue(session.engine().isRunning() && session.engine().tellingRing() == 0 && session.engine().singingRing() == -1,
						"the engine wakes: its first ring trembles before it sings");
				float health = c.getHealth();
				helper.assertTrue(hit(level, c, spell(level, striker), 30f), "a blow landed in the Hymn");
				plain[0] = health - c.getHealth();
			});

			step(helper, cleanup, sings, () -> helper.assertTrue(session.engine().singingRing() == 0 && session.engine().tellingRing() == -1,
					"the first ring sings " + b.ringTell + " ticks after its tell"));

			watch(helper, cleanup, sings + 1, 250, "the silenced point's window", tick -> {
				FirstCantor c = cantor(helper, session);
				helper.assertTrue(session.engine().isChannelling(channeller.getUUID()) || session.engine().silenced(0),
						"the crouching participant channels the point at their feet");

				if (!session.engine().silenced(0) || !c.isWindowOpen()) {
					return false;
				}

				helper.assertTrue(session.windowOpen() && session.engine().window() > 0, "a silenced point opens his window");
				float health = c.getHealth();
				helper.assertTrue(hit(level, c, spell(level, striker), 30f), "a blow landed in the window");
				float opened = health - c.getHealth();
				float multiplier = RaidRules.windowMultiplier(true, b);
				helper.assertTrue(Math.abs(opened - plain[0] * multiplier) < EPS, "in the window he takes " + multiplier + "x (" + plain[0] + " -> " + opened + ")");
				return true;
			});
		} catch (RuntimeException | Error e) {
			cleanup.run();
			throw e;
		}
	}

	// ---------------------------------------------------------------- shared checks

	/** The phases the phase hook reported, in order; every call must be Ulvaine's, for {@code player}. */
	private static List<Integer> phases(GameTestHelper helper, CollectorHooks.Recorder hooks, ServerPlayer player) {
		List<Integer> out = new ArrayList<>();

		for (CollectorHooks.Call call : hooks.calls(CollectorHooks.Kind.PHASE)) {
			helper.assertTrue(call.player().equals(player.getUUID()) && UlvaineFight.BOSS_ID.equals(call.bossId()), "a phase hook went astray (" + call + ")");
			out.add(call.phase());
		}

		return out;
	}

	/** {@code victim}'s death was reported once, by {@code killer}, as {@code kind} of {@code faction}, hostile or not. */
	private static void reported(GameTestHelper helper, List<FoeKills.Report> reports, LivingEntity victim, ServerPlayer killer, String kind,
			@Nullable Faction faction, boolean hostile) {
		List<FoeKills.Report> mine = reports.stream().filter(r -> r.victim().equals(victim.getUUID())).toList();
		helper.assertTrue(mine.size() == 1, kind + " reported its death " + mine.size() + " times");
		FoeKills.Report report = mine.get(0);
		helper.assertTrue(report.killer().equals(killer.getUUID()) && report.kind().equals(kind) && report.faction() == faction && report.wasHostile() == hostile,
				kind + " was reported as " + report + " (expected " + faction + ", hostile " + hostile + ")");
	}
}
