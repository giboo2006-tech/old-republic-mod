package com.oldrepublic.gametest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.enemy.trooper.BarkCategory;
import com.oldrepublic.enemy.trooper.Barks;
import com.oldrepublic.enemy.trooper.ColdPeace;
import com.oldrepublic.enemy.trooper.PlayerExpiries;
import com.oldrepublic.enemy.trooper.Reinforcements;
import com.oldrepublic.enemy.trooper.Squads;
import com.oldrepublic.enemy.trooper.TrooperAction;
import com.oldrepublic.enemy.trooper.TrooperAttitudeRule;
import com.oldrepublic.enemy.trooper.TrooperBalance;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.enemy.trooper.TrooperEvents;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.enemy.trooper.TrooperRules;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.ForceStatus;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.FrontierSettler;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityBalance;
import com.oldrepublic.identity.IdentityEntities;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3 troopers (W4, spec 17.1): the attitude table and its overrides (Wary warning,
 * provocation, engagement, incidents, ally defence, Peaceful), the cold peace and the standoff,
 * expiries, the saved state, spawning rules, downed troopers, drops, bolts, the role specials and
 * their counterplay, bark and fire-sound limits, despawning and sleep.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks} (at most 400); fixtures are the lead's
 * {@code oldrepublic_test:arena_24x7x24} or the default empty structure; players are listed mock
 * players ({@link M3TestPlayers}) removed at the end; randomness is seeded; no test changes the
 * difficulty, the day time, {@code WorldSites} or the patrol state. Mock players cannot be hurt
 * (they never finish loading), so the ally defence is driven through the hook the damage event
 * calls, and a bolt that reaches a player is recognised by the bolt stopping there.
 */
public class TrooperGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	/** Seed of the drop rolls. */
	private static final long DROP_SEED = 0x7200F00DL;
	private static final int DROP_ROLLS = 2000;
	/** How far the seeded drop rate may stray from {@code durasteelChance} over {@link #DROP_ROLLS} rolls. */
	private static final float DROP_TOLERANCE = 0.03f;
	/** Speed and lifetime of the hand-aimed bolts in {@link #boltStyleSyncedAndAllyPass}. */
	private static final double LANE_BOLT_SPEED = 1.5;
	private static final int LANE_BOLT_LIFE = 12;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	// ---------------------------------------------------------------- fixtures

	/** Spawns a squad through the real spawner, centred on {@code relative}, and checks every role came. */
	private static List<TrooperEntity> squad(GameTestHelper helper, Faction army, List<TrooperRole> roles, BlockPos relative,
			@Nullable UUID owner, IncidentMode incident) {
		List<LivingEntity> spawned = Troopers.spawnSquad(helper.getLevel(), helper.absolutePos(relative), army, roles, owner, incident);
		helper.assertTrue(spawned.size() == roles.size(), msg("spawnSquad spawns every role of " + roles + ", got " + spawned.size()));
		List<TrooperEntity> troopers = new ArrayList<>(spawned.size());

		for (LivingEntity entity : spawned) {
			helper.assertTrue(entity instanceof TrooperEntity, msg("spawnSquad spawns troopers"));
			troopers.add((TrooperEntity) entity);
		}

		return troopers;
	}

	/** A trooper alone in its own squad. */
	private static TrooperEntity trooper(GameTestHelper helper, Faction army, TrooperRole role, BlockPos relative) {
		return squad(helper, army, List.of(role), relative, null, IncidentMode.NONE).get(0);
	}

	/** No AI: the trooper stays where it is and does nothing on its own. */
	private static void freeze(List<TrooperEntity> troopers) {
		for (TrooperEntity trooper : troopers) {
			trooper.setNoAi(true);
		}
	}

	private static void place(GameTestHelper helper, Entity entity, BlockPos relative) {
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(relative));
		entity.snapTo(feet.x, feet.y, feet.z);
	}

	/** A listed player in survival, so troopers can see them as an enemy. */
	private static ServerPlayer survivor(GameTestHelper helper, BlockPos relative) {
		ServerPlayer player = M3TestPlayers.listed(helper, relative);
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** A reputation value inside {@code tier} under the configured tier edges. */
	private static int reputation(ReputationTier tier) {
		IdentityBalance b = Identity.balance();
		return switch (tier) {
			case HOSTILE -> b.repHostile;
			case DISTRUSTED -> b.repDistrusted;
			case NEUTRAL -> 0;
			case TRUSTED -> b.repTrusted;
			case HONOURED -> b.repHonoured;
		};
	}

	/** The spec 5.2 table. */
	private static Attitude expected(ReputationTier tier) {
		return switch (tier) {
			case HOSTILE -> Attitude.HOSTILE;
			case DISTRUSTED -> Attitude.WARY;
			case NEUTRAL -> Attitude.NEUTRAL;
			case TRUSTED, HONOURED -> Attitude.ALLIED;
		};
	}

	// Verified against the Fabric reference: fabric-data-attachment-api-v1 CommonAttachmentTests (lines 175, 181, 258-263).
	private static CompoundTag save(ServerLevel level, Entity entity) {
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		entity.saveWithoutId(output);
		return output.buildResult();
	}

	private static ValueInput input(ServerLevel level, CompoundTag tag) {
		return TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 1.0e-3f;
	}

	/**
	 * A test run tick by tick: each step is polled once per tick until it reports done, then the next
	 * step starts on the following tick; after the last step the clean-up runs and the test succeeds.
	 * One {@link GameTestHelper#onEachTick} registration drives it, so nothing is scheduled from
	 * inside a scheduled task. A failing step also runs the clean-up before the failure propagates.
	 */
	private static final class Script {
		/** One step: polled with the ticks it has been running, true when done. */
		@FunctionalInterface
		interface Step {
			boolean poll(int ticks);
		}

		private final GameTestHelper helper;
		private final List<Step> steps = new ArrayList<>();
		private final List<Runnable> cleanUp = new ArrayList<>();
		private int index;
		private int ticks;
		private boolean finished;

		Script(GameTestHelper helper) {
			this.helper = helper;
		}

		Script then(Runnable action) {
			steps.add(t -> {
				action.run();
				return true;
			});
			return this;
		}

		Script step(Step step) {
			steps.add(step);
			return this;
		}

		/** Waits until {@code condition} holds, failing after {@code maxTicks}. */
		Script await(String what, int maxTicks, BooleanSupplier condition) {
			steps.add(t -> {
				if (condition.getAsBoolean()) {
					return true;
				}

				helper.assertTrue(t < maxTicks, msg("timed out after " + maxTicks + " ticks waiting until " + what));
				return false;
			});
			return this;
		}

		/** Lets {@code count} ticks pass. */
		Script idle(int count) {
			steps.add(t -> t >= count);
			return this;
		}

		Script finallyDo(Runnable action) {
			cleanUp.add(action);
			return this;
		}

		void run() {
			helper.onEachTick(this::tick);
		}

		private void tick() {
			if (finished) {
				return;
			}

			try {
				if (steps.get(index).poll(ticks++)) {
					index++;
					ticks = 0;
				}

				if (index >= steps.size()) {
					finished = true;
					cleanUp.forEach(Runnable::run);
					helper.succeed();
				}
			} catch (RuntimeException e) {
				finished = true;
				cleanUp.forEach(Runnable::run);
				throw e;
			}
		}
	}

	// ---------------------------------------------------------------- attitudes

	/** Spec 5.2: Hostile attacks on sight, Distrusted is Wary, Neutral ignores, Trusted and Honoured are Allied; only the army's own reputation counts. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void attitudeTableEveryTier(GameTestHelper helper) {
		for (ReputationTier tier : ReputationTier.values()) {
			helper.assertTrue(TrooperAttitudeRule.attitude(tier, false) == expected(tier), msg(tier + " maps to " + expected(tier)));
		}

		ServerPlayer player = M3TestPlayers.listed(helper, new BlockPos(12, 1, 2));

		try {
			TrooperEntity republic = trooper(helper, Faction.REPUBLIC, TrooperRole.RIFLEMAN, new BlockPos(6, 1, 12));
			TrooperEntity imperial = trooper(helper, Faction.SITH_EMPIRE, TrooperRole.RIFLEMAN, new BlockPos(18, 1, 12));
			freeze(List.of(republic, imperial));
			ReputationTier[] tiers = ReputationTier.values();

			for (TrooperEntity trooper : List.of(republic, imperial)) {
				Faction army = trooper.army();
				Faction other = army == Faction.REPUBLIC ? Faction.SITH_EMPIRE : Faction.REPUBLIC;

				for (ReputationTier tier : tiers) {
					Identity.setReputation(player, army, reputation(tier));
					// The other army's opinion is the opposite, and must not matter.
					Identity.setReputation(player, other, reputation(tiers[tiers.length - 1 - tier.ordinal()]));
					helper.assertTrue(Identity.tier(player, army) == tier, msg("precondition: the player is " + tier + " with " + army));

					String what = trooper.armyKey() + " trooper, player " + tier;
					helper.assertTrue(Attitudes.toward(trooper, player) == expected(tier), msg(what + ": " + expected(tier)));
					helper.assertTrue(Attitudes.countsAsHostile(player, trooper) == (tier == ReputationTier.HOSTILE),
							msg(what + ": only the Hostile tier counts as hostile"));
					helper.assertTrue(Attitudes.shieldedFromArea(player, trooper) == (tier != ReputationTier.HOSTILE),
							msg(what + ": area powers skip every trooper that is not hostile and not provoked"));
				}
			}
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	/** One squad is Allied to A and Wary to B at once; firing on B (engagement) changes nothing for A. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void oneSquadAlliedToAWaryToB(GameTestHelper helper) {
		// Off the arena's lodestones and its centre plinth.
		ServerPlayer a = survivor(helper, new BlockPos(6, 1, 6));
		ServerPlayer b = survivor(helper, new BlockPos(17, 1, 17));

		try {
			Identity.setReputation(a, Faction.REPUBLIC, reputation(ReputationTier.TRUSTED));
			Identity.setReputation(b, Faction.REPUBLIC, reputation(ReputationTier.DISTRUSTED));
			List<TrooperEntity> squad = squad(helper, Faction.REPUBLIC,
					List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.RIFLEMAN, TrooperRole.MEDIC), new BlockPos(12, 1, 7), null,
					IncidentMode.NONE);
			freeze(squad);

			for (TrooperEntity member : squad) {
				String who = member.role() + ": ";
				helper.assertTrue(Attitudes.toward(member, a) == Attitude.ALLIED, msg(who + "Allied to A"));
				helper.assertTrue(Attitudes.toward(member, b) == Attitude.WARY, msg(who + "Wary to B"));
				helper.assertTrue(!member.wants(a) && !member.wants(b), msg(who + "fights neither before B is engaged"));
				helper.assertTrue(!Attitudes.countsAsHostile(b, member), msg(who + "a Wary fight does not count as hostile yet"));
			}

			long now = helper.getLevel().getGameTime();
			TrooperEvents.engageSquad(squad.get(0), b, now);

			for (TrooperEntity member : squad) {
				String who = member.role() + ": ";
				helper.assertTrue(member.isEngagedWith(b.getUUID(), now), msg(who + "the whole squad is engaged with B"));
				// wants() is what the targeting goal follows. getTarget() itself cannot be checked here: Mob#setTarget and
				// #getTarget pass through asValidTarget, which drops creative players, and a mock player is always creative.
				helper.assertTrue(member.wants(b), msg(who + "the whole squad wants B"));
				helper.assertTrue(Attitudes.countsAsHostile(b, member) && !Attitudes.shieldedFromArea(b, member),
						msg(who + "B's fight now counts as hostile"));
				helper.assertTrue(Attitudes.toward(member, a) == Attitude.ALLIED && !member.wants(a) && !member.isEngagedWith(a.getUUID(), now),
						msg(who + "still Allied to A"));
				helper.assertTrue(Attitudes.shieldedFromArea(a, member), msg(who + "A's area powers still skip the squad"));
			}
		} finally {
			M3TestPlayers.remove(a);
			M3TestPlayers.remove(b);
		}

		helper.succeed();
	}

	/**
	 * Wary (5.2): the leader warns a player within 16 blocks; 120 ticks within 8 blocks after the
	 * warning, and the squad opens fire (engaged). A lit saber within 8 blocks cuts the wait short.
	 */
	@GameTest(structure = ARENA, maxTicks = 220)
	public void waryWarnsThenEngagesAfter120Ticks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TrooperBalance b = TrooperBalance.get();
		// Two squads far enough apart (over warnRange) that each only deals with its own player.
		ServerPlayer waiting = survivor(helper, new BlockPos(3, 1, 9));
		ServerPlayer saber = survivor(helper, new BlockPos(20, 1, 14));
		List<TrooperEntity> first = squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN), new BlockPos(3, 1, 3),
				null, IncidentMode.NONE);
		List<TrooperEntity> second = squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN), new BlockPos(20, 1, 20),
				null, IncidentMode.NONE);

		for (ServerPlayer player : List.of(waiting, saber)) {
			Identity.setReputation(player, Faction.SITH_EMPIRE, reputation(ReputationTier.DISTRUSTED));
		}

		ItemStack lit = SaberItems.buildSaber(new CrystalAttunement(saber.getUUID(), "Tester", BladeColor.BLUE.rgb()), BladeColor.BLUE,
				Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
		SaberToggle.setIgnited(lit, true, level.getGameTime());
		saber.setItemInHand(InteractionHand.MAIN_HAND, lit);

		TrooperEntity firstLeader = first.get(0);
		TrooperEntity secondLeader = second.get(0);
		long[] warnedWaiting = {Long.MIN_VALUE};
		long[] warnedSaber = {Long.MIN_VALUE};
		boolean[] saberDone = {false};

		new Script(helper)
				.step(t -> {
					long now = level.getGameTime();
					Long warned = Squads.warnedAt(firstLeader, waiting.getUUID());

					if (warned != null && warnedWaiting[0] == Long.MIN_VALUE) {
						warnedWaiting[0] = warned;
						helper.assertTrue(!firstLeader.isEngagedWith(waiting.getUUID(), now), msg("the warning comes before any shot"));
					}

					if (!saberDone[0]) {
						Long warnedLit = Squads.warnedAt(secondLeader, saber.getUUID());

						if (warnedLit != null && warnedSaber[0] == Long.MIN_VALUE) {
							warnedSaber[0] = warnedLit;
						}

						if (secondLeader.isEngagedWith(saber.getUUID(), now)) {
							helper.assertTrue(warnedSaber[0] != Long.MIN_VALUE, msg("even a lit saber is warned first"));
							helper.assertTrue(now - warnedSaber[0] < b.waryTicks / 2,
									msg("a lit saber within " + b.waryRange + " blocks is fired on at once, not after " + b.waryTicks + " ticks (took "
											+ (now - warnedSaber[0]) + ")"));

							for (TrooperEntity member : second) {
								helper.assertTrue(member.isEngagedWith(saber.getUUID(), now), msg("the whole second squad is engaged"));
							}

							// Done with this squad: it stops, and its player leaves.
							freeze(second);
							M3TestPlayers.remove(saber);
							saberDone[0] = true;
						}
					}

					if (firstLeader.isEngagedWith(waiting.getUUID(), now)) {
						helper.assertTrue(warnedWaiting[0] != Long.MIN_VALUE, msg("engaged without a warning"));
						long waited = now - warnedWaiting[0];
						helper.assertTrue(waited >= b.waryTicks - 1 && waited <= b.waryTicks + 6,
								msg("the squad opens fire " + b.waryTicks + " ticks after the warning, took " + waited));

						for (TrooperEntity member : first) {
							helper.assertTrue(member.isEngagedWith(waiting.getUUID(), now) && Attitudes.countsAsHostile(waiting, member),
									msg("every member is engaged, and the fight counts as hostile"));
						}

						helper.assertTrue(saberDone[0], msg("the lit saber was fired on long before the timer ran out"));
						return true;
					}

					helper.assertTrue(t < b.waryTicks + 60, msg("the Wary squad never opened fire"));
					return false;
				})
				.finallyDo(() -> {
					M3TestPlayers.remove(waiting);
					M3TestPlayers.remove(saber);
				})
				.run();
	}

	/** A Neutral player's hit provokes the whole squad: it fights back, but the fight still counts as not hostile. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void neutralHitProvokesSquad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TrooperBalance b = TrooperBalance.get();
		ServerPlayer player = survivor(helper, new BlockPos(12, 1, 4));

		try {
			Identity.setReputation(player, Faction.REPUBLIC, reputation(ReputationTier.NEUTRAL));
			List<TrooperEntity> squad = squad(helper, Faction.REPUBLIC, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.MEDIC),
					new BlockPos(12, 1, 12), null, IncidentMode.NONE);
			TrooperEntity bystander = trooper(helper, Faction.REPUBLIC, TrooperRole.RIFLEMAN, new BlockPos(20, 1, 20));
			freeze(squad);
			freeze(List.of(bystander));

			for (TrooperEntity member : squad) {
				helper.assertTrue(Attitudes.shieldedFromArea(player, member) && !member.wants(player), msg("before the hit: a Neutral bystander"));
			}

			long now = level.getGameTime();
			helper.assertTrue(squad.get(1).hurtServer(level, level.damageSources().playerAttack(player), 1f), msg("precondition: the hit lands"));

			for (TrooperEntity member : squad) {
				String who = member.role() + ": ";
				helper.assertTrue(member.isProvokedBy(player.getUUID(), now) && Attitudes.provokedBy(member, player), msg(who + "provoked"));
				helper.assertTrue(member.wants(player), msg(who + "fights the player who struck first"));
				helper.assertTrue(!Attitudes.countsAsHostile(player, member), msg(who + "the fight still counts as not hostile (no kill XP)"));
				helper.assertTrue(!Attitudes.shieldedFromArea(player, member), msg(who + "area powers now reach it"));
				helper.assertTrue(member.isProvokedBy(player.getUUID(), now + b.provokedTicks - 1) && !member.isProvokedBy(player.getUUID(), now + b.provokedTicks),
						msg(who + "provoked for exactly " + b.provokedTicks + " ticks"));
			}

			helper.assertTrue(!bystander.isProvokedBy(player.getUUID(), now) && Attitudes.shieldedFromArea(player, bystander),
					msg("another squad is not provoked"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	/** ARMY: the squad fights the other army, never players. ARMY_AND_OWNER: also its owner, and its sergeant yields. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void incidentModes(GameTestHelper helper) {
		TrooperBalance b = TrooperBalance.get();
		ServerPlayer owner = survivor(helper, new BlockPos(2, 1, 2));
		ServerPlayer other = survivor(helper, new BlockPos(21, 1, 2));

		try {
			for (ServerPlayer player : List.of(owner, other)) {
				Identity.setReputation(player, Faction.REPUBLIC, reputation(ReputationTier.NEUTRAL));
				Identity.setReputation(player, Faction.SITH_EMPIRE, reputation(ReputationTier.NEUTRAL));
			}

			List<TrooperEntity> contract = squad(helper, Faction.REPUBLIC, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.MEDIC),
					new BlockPos(6, 1, 12), owner.getUUID(), IncidentMode.ARMY_AND_OWNER);
			List<TrooperEntity> army = squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.GRENADIER),
					new BlockPos(18, 1, 12), null, IncidentMode.ARMY);
			TrooperEntity quietImperial = trooper(helper, Faction.SITH_EMPIRE, TrooperRole.RIFLEMAN, new BlockPos(12, 1, 20));
			TrooperEntity quietRepublic = trooper(helper, Faction.REPUBLIC, TrooperRole.RIFLEMAN, new BlockPos(12, 1, 4));
			freeze(contract);
			freeze(army);
			freeze(List.of(quietImperial, quietRepublic));
			long now = helper.getLevel().getGameTime();

			for (TrooperEntity member : contract) {
				String who = "ARMY_AND_OWNER " + member.role() + ": ";
				helper.assertTrue(member.incidentMode() == IncidentMode.ARMY_AND_OWNER, msg(who + "carries the incident"));
				helper.assertTrue(member.incidentIncludes(owner.getUUID(), now) && member.wants(owner) && Attitudes.countsAsHostile(owner, member),
						msg(who + "engaged with its owner from the start"));
				helper.assertTrue(!member.incidentIncludes(other.getUUID(), now) && !member.wants(other) && !Attitudes.countsAsHostile(other, member),
						msg(who + "not with anyone else"));
				helper.assertTrue(member.wants(quietImperial) && quietImperial.wants(member), msg(who + "the cold peace is broken both ways"));
			}

			for (TrooperEntity member : army) {
				String who = "ARMY " + member.role() + ": ";
				helper.assertTrue(member.incidentMode() == IncidentMode.ARMY, msg(who + "carries the incident"));
				helper.assertTrue(!Attitudes.countsAsHostile(owner, member) && !Attitudes.countsAsHostile(other, member) && !member.wants(owner)
						&& !member.wants(other), msg(who + "never includes a player"));
				helper.assertTrue(member.wants(quietRepublic) && quietRepublic.wants(member), msg(who + "fights the other army, which fights back"));
			}

			helper.assertTrue(!quietImperial.wants(quietRepublic) && !quietRepublic.wants(quietImperial), msg("untagged troopers keep the cold peace"));

			TrooperEntity contractSergeant = contract.get(0);
			helper.assertTrue(contractSergeant.canYield() && near(contractSergeant.yieldHealthFraction(), b.interceptYield)
					&& contractSergeant.yieldDialogue().equals(TrooperEntity.YIELD_INTERCEPT), msg("the ARMY_AND_OWNER sergeant yields into yield_intercept"));
			helper.assertTrue(contractSergeant.yieldDecider() != null && contractSergeant.yieldDecider().equals(owner.getUUID()),
					msg("the owner decides the yield"));
			helper.assertTrue(!army.get(0).canYield() && !contract.get(1).canYield(), msg("an ARMY sergeant and a rifleman never yield"));

			UUID id = owner.getUUID();
			helper.assertTrue(TrooperRules.incidentIncludes(IncidentMode.ARMY_AND_OWNER, now + 10, now, id, id)
					&& !TrooperRules.incidentIncludes(IncidentMode.ARMY, now + 10, now, id, id)
					&& !TrooperRules.incidentIncludes(IncidentMode.ARMY_AND_OWNER, now + 10, now, null, id), msg("the pure incident rule"));
		} finally {
			M3TestPlayers.remove(owner);
			M3TestPlayers.remove(other);
		}

		helper.succeed();
	}

	/** An Imperial hit on a player the Republic squad is Allied with starts an ARMY incident on both squads for incidentTicks. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void allyDefenceStartsIncident(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TrooperBalance b = TrooperBalance.get();
		ServerPlayer ally = survivor(helper, new BlockPos(12, 1, 12));

		try {
			Identity.setReputation(ally, Faction.REPUBLIC, reputation(ReputationTier.TRUSTED));
			Identity.setReputation(ally, Faction.SITH_EMPIRE, reputation(ReputationTier.NEUTRAL));
			List<TrooperEntity> guards = squad(helper, Faction.REPUBLIC, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.RIFLEMAN),
					new BlockPos(12, 1, 6), null, IncidentMode.NONE);
			List<TrooperEntity> rivals = squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN),
					new BlockPos(12, 1, 20), null, IncidentMode.NONE);
			freeze(guards);
			freeze(rivals);
			TrooperEntity attacker = rivals.get(1);

			for (TrooperEntity guard : guards) {
				helper.assertTrue(guard.incidentMode() == IncidentMode.NONE && !guard.wants(attacker), msg("before: the cold peace holds"));
			}

			long now = level.getGameTime();
			// What the damage listener does when the Imperial's hit lands on the player.
			TrooperEvents.defendAlly(level, ally, attacker, now);

			for (TrooperEntity guard : guards) {
				String who = "guard " + guard.role() + ": ";
				helper.assertTrue(guard.incidentMode() == IncidentMode.ARMY && guard.incidentUntil() == now + b.incidentTicks,
						msg(who + "ARMY incident for " + b.incidentTicks + " ticks"));
				helper.assertTrue(guard.getTarget() == attacker && guard.wants(attacker) && guard.wants(rivals.get(0)),
						msg(who + "turns on the attacker and its squad"));
			}

			for (TrooperEntity rival : rivals) {
				helper.assertTrue(rival.incidentMode() == IncidentMode.ARMY && rival.incidentUntil() == now + b.incidentTicks,
						msg("rival " + rival.role() + ": the attacker's squad carries the incident too"));
			}

			helper.assertTrue(!Attitudes.countsAsHostile(ally, guards.get(0)), msg("the ARMY incident never turns the guards on their player"));

			// Anything that is no trooper is simply fought.
			Mob villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(14, 1, 14));
			TrooperEvents.defendAlly(level, ally, villager, now);

			for (TrooperEntity guard : guards) {
				helper.assertTrue(guard.getTarget() == villager && guard.wants(villager), msg("guard " + guard.role() + ": defends against a mob"));
			}

			// A trooper of the guards' own army never makes them turn.
			TrooperEntity friend = trooper(helper, Faction.REPUBLIC, TrooperRole.RIFLEMAN, new BlockPos(4, 1, 4));
			freeze(List.of(friend));
			TrooperEvents.defendAlly(level, ally, friend, now);
			helper.assertTrue(guards.get(0).getTarget() == villager && !guards.get(0).wants(friend), msg("the guards never turn on their own army"));
		} finally {
			M3TestPlayers.remove(ally);
		}

		helper.succeed();
	}

	/** Peaceful (5.2) caps every tier at Neutral; Allied stays Allied. The world difficulty is never changed. */
	@GameTest(maxTicks = 20)
	public void peacefulNeverAttacksPlayers(GameTestHelper helper) {
		for (ReputationTier tier : ReputationTier.values()) {
			Attitude attitude = TrooperAttitudeRule.attitude(tier, true);
			helper.assertTrue(attitude != Attitude.HOSTILE && attitude != Attitude.WARY, msg(tier + " on Peaceful is never Hostile or Wary"));
			helper.assertTrue(attitude == (expected(tier) == Attitude.ALLIED ? Attitude.ALLIED : Attitude.NEUTRAL),
					msg(tier + " on Peaceful is " + (expected(tier) == Attitude.ALLIED ? "Allied" : "Neutral")));
		}

		helper.assertTrue(TrooperAttitudeRule.attitude(ReputationTier.HOSTILE, false) == Attitude.HOSTILE,
				msg("the cap comes from Peaceful alone"));
		helper.succeed();
	}

	/** Frontier settlers are never targets, even for a squad under an incident that a settler hurt. */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void neverAttackSettlers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		FrontierSettler settler = helper.spawnWithNoFreeWill(IdentityEntities.FRONTIER_SETTLER, new BlockPos(12, 1, 12));
		ServerPlayer friend = survivor(helper, new BlockPos(16, 1, 12));
		Identity.setReputation(friend, Faction.SITH_EMPIRE, reputation(ReputationTier.TRUSTED));
		List<TrooperEntity> squad = squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN), new BlockPos(12, 1, 6),
				null, IncidentMode.ARMY);
		float health = settler.getHealth();

		for (TrooperEntity member : squad) {
			helper.assertTrue(!member.wants(settler) && !member.canAttack(settler), msg(member.role() + " never wants a settler"));
			member.setTarget(settler);
			helper.assertTrue(member.getTarget() == null, msg(member.role() + " refuses a settler as its target"));
		}

		TrooperEntity hit = squad.get(1);
		hit.hurtServer(level, level.damageSources().mobAttack(settler), 1f);
		helper.assertTrue(hit.getTarget() == null && !hit.wants(settler), msg("hurt by a settler, it still does not fight back"));
		TrooperEvents.defendAlly(level, friend, settler, level.getGameTime());
		helper.assertTrue(squad.get(0).getTarget() == null, msg("nor when the settler strikes an Allied player"));

		helper.runAfterDelay(20, () -> {
			try {
				for (TrooperEntity member : squad) {
					helper.assertTrue(member.getTarget() == null && !member.wants(settler), msg(member.role() + ": no target after 20 ticks of AI"));
				}

				helper.assertTrue(settler.isAlive() && near(settler.getHealth(), health), msg("the settler is unharmed"));
			} finally {
				M3TestPlayers.remove(friend);
			}

			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- cold peace, expiries

	/** The pure cold peace: who fights whom, and the standoff phases. */
	@GameTest(maxTicks = 20)
	public void coldPeaceShouldEngageAndStandoffStep(GameTestHelper helper) {
		TrooperBalance b = TrooperBalance.get();

		for (IncidentMode x : IncidentMode.values()) {
			for (IncidentMode y : IncidentMode.values()) {
				boolean either = x != IncidentMode.NONE || y != IncidentMode.NONE;
				helper.assertTrue(ColdPeace.shouldEngage(x, y, true) == either, msg(x + " vs " + y + " under the cold peace: " + either));
				helper.assertTrue(ColdPeace.shouldEngage(x, y, false), msg(x + " vs " + y + " without the cold peace: always"));
			}
		}

		long t = 5000;
		ColdPeace.Standoff none = ColdPeace.Standoff.NONE;
		helper.assertTrue(ColdPeace.standoffStep(none, false, t, b) == none, msg("no rival, no standoff"));

		ColdPeace.Standoff halted = ColdPeace.standoffStep(none, true, t, b);
		helper.assertTrue(halted.phase() == ColdPeace.Phase.HALTED && halted.since() == t, msg("a rival halts the squad"));
		helper.assertTrue(ColdPeace.standoffStep(halted, true, t + b.standoffTicks - 1, b) == halted, msg("halted until standoffTicks"));

		ColdPeace.Standoff walking = ColdPeace.standoffStep(halted, true, t + b.standoffTicks, b);
		helper.assertTrue(walking.phase() == ColdPeace.Phase.WALKING_ON, msg("after standoffTicks it walks on"));
		helper.assertTrue(ColdPeace.standoffStep(walking, true, t + b.standoffTicks + 500, b) == walking, msg("walking on while the rival is near"));
		helper.assertTrue(ColdPeace.standoffStep(walking, false, t + 900, b).phase() == ColdPeace.Phase.NONE, msg("the rival gone: over"));
		helper.assertTrue(ColdPeace.standoffStep(halted, false, t + 1, b).phase() == ColdPeace.Phase.NONE, msg("a rival that leaves ends the halt"));
		helper.succeed();
	}

	/** Provoked and engaged lists and incidents expire exactly on time (explicit game times), at most four players each. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void provocationAndIncidentExpiry(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TrooperBalance b = TrooperBalance.get();
		long t = 1_000_000L;
		UUID[] players = new UUID[PlayerExpiries.MAX + 1];

		for (int i = 0; i < players.length; i++) {
			players[i] = new UUID(0x7200_0000L, i);
		}

		PlayerExpiries list = new PlayerExpiries();
		list.put(players[0], TrooperRules.expiry(t, b.provokedTicks));
		helper.assertTrue(list.active(players[0], t + b.provokedTicks - 1) && !list.active(players[0], t + b.provokedTicks),
				msg("active for exactly provokedTicks"));

		for (int i = 1; i < players.length; i++) {
			list.put(players[i], TrooperRules.expiry(t + i, b.provokedTicks));
		}

		helper.assertTrue(list.size() == PlayerExpiries.MAX && !list.active(players[0], t), msg("a fifth player drops the one expiring soonest"));
		list.put(players[1], t);
		helper.assertTrue(list.active(players[1], t + b.provokedTicks), msg("a repeat keeps the later expiry"));
		list.prune(t + 10L * b.provokedTicks);
		helper.assertTrue(list.isEmpty(), msg("pruned once expired"));
		helper.assertTrue(TrooperRules.expiry(Long.MAX_VALUE - 5, 100) == Long.MAX_VALUE, msg("an expiry never overflows"));

		helper.assertTrue(TrooperRules.incidentAt(IncidentMode.ARMY, t + 50, t + 49) == IncidentMode.ARMY
				&& TrooperRules.incidentAt(IncidentMode.ARMY, t + 50, t + 50) == IncidentMode.NONE, msg("an incident ends at its expiry"));
		long[] merged = TrooperRules.mergeIncident(IncidentMode.ARMY, t + 50, IncidentMode.ARMY_AND_OWNER, t + 20, t);
		helper.assertTrue(merged[0] == IncidentMode.ARMY_AND_OWNER.ordinal() && merged[1] == t + 50, msg("the stronger mode and the later expiry win"));
		long[] replaced = TrooperRules.mergeIncident(IncidentMode.ARMY_AND_OWNER, t - 1, IncidentMode.ARMY, t + 30, t);
		helper.assertTrue(replaced[0] == IncidentMode.ARMY.ordinal() && replaced[1] == t + 30, msg("an expired incident is simply replaced"));

		ServerPlayer owner = M3TestPlayers.listed(helper, new BlockPos(4, 1, 4));
		TrooperEntity trooper = trooper(helper, Faction.REPUBLIC, TrooperRole.RIFLEMAN, new BlockPos(12, 1, 12));
		freeze(List.of(trooper));
		trooper.provoke(players[0], t);
		trooper.engage(players[1], t);
		helper.assertTrue(trooper.isProvokedBy(players[0], t + b.provokedTicks - 1) && !trooper.isProvokedBy(players[0], t + b.provokedTicks),
				msg("the trooper's provocation expires on time"));
		helper.assertTrue(trooper.isEngagedWith(players[1], t + b.engagedTicks - 1) && !trooper.isEngagedWith(players[1], t + b.engagedTicks),
				msg("the trooper's engagement expires on time"));

		// A live incident five ticks long.
		long now = level.getGameTime();
		trooper.setOwner(owner.getUUID());
		trooper.setIncident(IncidentMode.ARMY_AND_OWNER, now + 5);
		helper.assertTrue(trooper.incidentMode() == IncidentMode.ARMY_AND_OWNER && Attitudes.countsAsHostile(owner, trooper),
				msg("during the incident its owner counts as hostile"));

		helper.runAfterDelay(6, () -> {
			try {
				helper.assertTrue(trooper.incidentMode() == IncidentMode.NONE && !Attitudes.countsAsHostile(owner, trooper),
						msg("after it, the owner is a bystander again"));
			} finally {
				M3TestPlayers.remove(owner);
			}

			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- saving and spawning

	/** Every frozen NBT key is written under its name and read back: Role, Squad, Incident, Provoked, Engaged, BleedAt, Terminal (and Yield, Downed, NameSeed). */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void trooperNbtRoundTrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long now = level.getGameTime();
		UUID owner = new UUID(0x7200_0002L, 0);
		UUID provokedA = new UUID(0x7200_0002L, 1);
		UUID provokedB = new UUID(0x7200_0002L, 2);
		UUID engagedC = new UUID(0x7200_0002L, 3);
		List<TrooperEntity> squad = squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.SERGEANT, TrooperRole.GRENADIER), new BlockPos(12, 1, 12),
				owner, IncidentMode.ARMY_AND_OWNER);
		freeze(squad);
		TrooperEntity sergeant = squad.get(0);
		BlockPos terminal = helper.absolutePos(new BlockPos(2, 1, 2));
		sergeant.setIncident(IncidentMode.ARMY_AND_OWNER, now + 5000);
		sergeant.provoke(provokedA, now);
		sergeant.provoke(provokedB, now + 10);
		sergeant.engage(engagedC, now);
		Troopers.bleedOut(sergeant, now + 7000);
		Troopers.bindTerminal(sergeant, terminal, 2, 12);
		Troopers.setYield(sergeant, 0.2f, TrooperEntity.YIELD_BORDER);

		CompoundTag tag = save(level, sergeant);
		ValueInput saved = input(level, tag);
		helper.assertTrue(saved.getStringOr("Role", "").equals("SERGEANT"), msg("Role is the role name"));
		helper.assertTrue(saved.read("Squad", UUIDUtil.CODEC).filter(sergeant.squad()::equals).isPresent(), msg("Squad is the squad UUID"));
		helper.assertTrue(saved.child("Incident").filter(c -> c.getIntOr("Mode", -1) == IncidentMode.ARMY_AND_OWNER.ordinal()
				&& c.getLongOr("Until", 0) == now + 5000).isPresent(), msg("Incident {Mode, Until}"));
		helper.assertTrue(saved.read("Provoked", PlayerExpiries.CODEC).filter(p -> p.size() == 2).isPresent(), msg("Provoked lists two players"));
		helper.assertTrue(saved.read("Engaged", PlayerExpiries.CODEC).filter(p -> p.size() == 1).isPresent(), msg("Engaged lists one player"));
		helper.assertTrue(saved.getLongOr("BleedAt", -1) == now + 7000, msg("BleedAt"));
		helper.assertTrue(saved.child("Terminal").filter(c -> c.read("Pos", BlockPos.CODEC).filter(terminal::equals).isPresent()
				&& c.getIntOr("Slot", -1) == 2).isPresent(), msg("Terminal {Pos, Slot}"));
		helper.assertTrue(saved.child("Yield").filter(c -> near(c.getFloatOr("Fraction", -1f), 0.2f)
				&& c.getStringOr("Dialogue", "").equals(TrooperEntity.YIELD_BORDER.toString())).isPresent(), msg("Yield {Fraction, Dialogue}"));

		TrooperEntity copy = EnemyEntities.SITH_TROOPER.create(level, EntitySpawnReason.LOAD);
		helper.assertTrue(copy != null, msg("the type creates a trooper"));
		copy.load(input(level, tag));
		helper.assertTrue(copy.role() == TrooperRole.SERGEANT && sergeant.squad().equals(copy.squad()) && owner.equals(copy.owner()),
				msg("role, squad and owner survive"));
		helper.assertTrue(copy.incidentMode() == IncidentMode.ARMY_AND_OWNER && copy.incidentUntil() == now + 5000 && copy.incidentIncludes(owner, now),
				msg("the incident survives"));
		helper.assertTrue(copy.isProvokedBy(provokedA, now) && copy.isProvokedBy(provokedB, now) && copy.isEngagedWith(engagedC, now)
				&& !copy.isEngagedWith(provokedA, now), msg("provocations and engagements survive"));
		helper.assertTrue(copy.isProvokedBy(provokedB, now + 10 + TrooperBalance.get().provokedTicks - 1)
				&& !copy.isProvokedBy(provokedA, now + TrooperBalance.get().provokedTicks), msg("with their expiries"));
		helper.assertTrue(copy.bleedAt() == now + 7000 && terminal.equals(copy.terminal()) && copy.terminalSlot() == 2, msg("BleedAt and Terminal survive"));
		helper.assertTrue(copy.canYield() && near(copy.yieldHealthFraction(), 0.2f) && copy.yieldDialogue().equals(TrooperEntity.YIELD_BORDER),
				msg("the yield set by the mission survives"));
		helper.assertTrue(!copy.isDowned() && copy.squadSize() == 2, msg("not downed; the squad size survives"));

		// The yield survives a relog (spec 12.2): a yielded sergeant stays yielded, a handed-over one stays bound.
		helper.assertTrue(!saved.getBooleanOr("Yielded", true) && !saved.getBooleanOr("YieldResolved", true) && !saved.getBooleanOr("Bound", true),
				msg("Yielded, YieldResolved and Bound are saved as false before any yield"));
		sergeant.setYielded(true);
		CompoundTag yieldedTag = save(level, sergeant);
		helper.assertTrue(input(level, yieldedTag).getBooleanOr("Yielded", false), msg("Yielded is saved"));
		TrooperEntity yieldedCopy = EnemyEntities.SITH_TROOPER.create(level, EntitySpawnReason.LOAD);
		helper.assertTrue(yieldedCopy != null, msg("the type creates a trooper"));
		yieldedCopy.load(input(level, yieldedTag));
		helper.assertTrue(yieldedCopy.isYielded() && !yieldedCopy.canFight() && yieldedCopy.canYield(), msg("a yielded sergeant is still yielded after a relog"));

		sergeant.onYieldResolved(YieldOutcome.HANDED_OVER);
		CompoundTag boundTag = save(level, sergeant);
		ValueInput boundSaved = input(level, boundTag);
		helper.assertTrue(!boundSaved.getBooleanOr("Yielded", true) && boundSaved.getBooleanOr("YieldResolved", false) && boundSaved.getBooleanOr("Bound", false),
				msg("a handed-over sergeant saves YieldResolved and Bound"));
		TrooperEntity boundCopy = EnemyEntities.SITH_TROOPER.create(level, EntitySpawnReason.LOAD);
		helper.assertTrue(boundCopy != null, msg("the type creates a trooper"));
		boundCopy.load(input(level, boundTag));
		helper.assertTrue(boundCopy.isBound() && boundCopy.action() == TrooperAction.BOUND && !boundCopy.isYielded() && !boundCopy.canYield()
				&& !boundCopy.canFight(), msg("a handed-over prisoner stays bound, never yields again and never fights after a relog"));

		// An officer keeps its name seed, a downed trooper its state.
		TrooperEntity officer = (TrooperEntity) Troopers.spawnOfficer(level, helper.absolutePos(new BlockPos(4, 1, 20)), Faction.REPUBLIC, 4);
		TrooperEntity downed = (TrooperEntity) Troopers.spawnDowned(level, helper.absolutePos(new BlockPos(20, 1, 20)), Faction.REPUBLIC, owner);
		TrooperEntity officerCopy = EnemyEntities.REPUBLIC_TROOPER.create(level, EntitySpawnReason.LOAD);
		TrooperEntity downedCopy = EnemyEntities.REPUBLIC_TROOPER.create(level, EntitySpawnReason.LOAD);
		helper.assertTrue(officerCopy != null && downedCopy != null, msg("the type creates troopers"));
		officerCopy.load(input(level, save(level, officer)));
		downedCopy.load(input(level, save(level, downed)));
		helper.assertTrue(officerCopy.isOfficer() && officerCopy.nameSeed() == 4, msg("NameSeed survives"));
		helper.assertTrue(downedCopy.isDowned() && downedCopy.bleedAt() < 0 && owner.equals(downedCopy.owner()),
				msg("Downed survives; without bleedOut there is no BleedAt"));
		helper.succeed();
	}

	/** A STRUCTURE spawn never re-rolls a saved Role; roles are normalised for the army and carry their weapon. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void structureSpawnKeepsRole(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(new BlockPos(12, 1, 12));
		TrooperEntity grenadier = trooper(helper, Faction.SITH_EMPIRE, TrooperRole.GRENADIER, new BlockPos(12, 1, 12));
		freeze(List.of(grenadier));
		CompoundTag tag = save(level, grenadier);

		for (int attempt = 0; attempt < 8; attempt++) {
			TrooperEntity placed = EnemyEntities.SITH_TROOPER.create(level, EntitySpawnReason.STRUCTURE);
			helper.assertTrue(placed != null, msg("the type creates a trooper"));
			placed.load(input(level, tag));
			placed.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
			helper.assertTrue(placed.role() == TrooperRole.GRENADIER, msg("a structure trooper keeps its saved GRENADIER role, got " + placed.role()));
			helper.assertTrue(placed.isPersistenceRequired() && placed.getMainHandItem().is(EnemyItems.BLASTER_PISTOL),
					msg("persistent, with its pistol"));
		}

		// The same saved role in a Republic trooper: the Republic has no grenadiers.
		TrooperEntity republic = EnemyEntities.REPUBLIC_TROOPER.create(level, EntitySpawnReason.STRUCTURE);
		helper.assertTrue(republic != null, msg("the type creates a trooper"));
		republic.load(input(level, tag));
		helper.assertTrue(republic.role() == TrooperRole.RIFLEMAN, msg("a Republic GRENADIER loads as a RIFLEMAN"));

		// Without a saved role a structure trooper draws one from its army's composition, once.
		TrooperEntity fresh = EnemyEntities.SITH_TROOPER.create(level, EntitySpawnReason.STRUCTURE);
		helper.assertTrue(fresh != null, msg("the type creates a trooper"));
		place(helper, fresh, new BlockPos(6, 1, 6));
		fresh.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
		TrooperRole drawn = fresh.role();
		helper.assertTrue(drawn == TrooperRole.SERGEANT || drawn == TrooperRole.RIFLEMAN || drawn == TrooperRole.GRENADIER,
				msg("an Imperial structure trooper draws an Imperial role, got " + drawn));
		fresh.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.STRUCTURE, null);
		helper.assertTrue(fresh.role() == drawn, msg("and keeps it"));

		// Normalisation and weapons through the spawner.
		List<TrooperRole> all = List.of(TrooperRole.RIFLEMAN, TrooperRole.SERGEANT, TrooperRole.MEDIC, TrooperRole.GRENADIER);
		List<TrooperEntity> republicSquad = squad(helper, Faction.REPUBLIC, all, new BlockPos(6, 1, 18), null, IncidentMode.NONE);
		List<TrooperEntity> imperialSquad = squad(helper, Faction.SITH_EMPIRE, all, new BlockPos(18, 1, 18), null, IncidentMode.NONE);
		freeze(republicSquad);
		freeze(imperialSquad);
		helper.assertTrue(republicSquad.get(3).role() == TrooperRole.RIFLEMAN, msg("a Republic GRENADIER becomes a RIFLEMAN"));
		helper.assertTrue(imperialSquad.get(2).role() == TrooperRole.RIFLEMAN, msg("an Imperial MEDIC becomes a RIFLEMAN"));

		for (TrooperEntity trooper : List.of(republicSquad.get(0), republicSquad.get(1), republicSquad.get(2), imperialSquad.get(0), imperialSquad.get(1),
				imperialSquad.get(3))) {
			boolean rifle = TrooperRules.carriesRifle(trooper.role());
			helper.assertTrue(trooper.getMainHandItem().is(rifle ? EnemyItems.BLASTER_RIFLE : EnemyItems.BLASTER_PISTOL),
					msg(trooper.armyKey() + " " + trooper.role() + " holds its " + (rifle ? "rifle" : "pistol")));
			float[] stats = TrooperRules.stats(trooper.army(), trooper.role(), TrooperBalance.get());
			helper.assertTrue(near(trooper.getMaxHealth(), stats[0]), msg(trooper.armyKey() + " " + trooper.role() + " has " + stats[0] + " health"));
		}

		TrooperEntity officer = (TrooperEntity) Troopers.spawnOfficer(level, helper.absolutePos(new BlockPos(2, 1, 12)), Faction.SITH_EMPIRE, 1);
		helper.assertTrue(officer.getMainHandItem().isEmpty(), msg("an officer is unarmed"));
		helper.succeed();
	}

	/** Downed troopers are never targets and take no damage; BleedAt removes them on their tick and as they load; tending stands them up. */
	@GameTest(structure = ARENA, maxTicks = 120)
	public void downedNotTargetableAndBleedsOutOnLoad(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TrooperBalance b = TrooperBalance.get();
		ServerPlayer owner = survivor(helper, new BlockPos(2, 1, 2));
		long now = level.getGameTime();
		BlockPos downedAt = helper.absolutePos(new BlockPos(8, 1, 8));
		TrooperEntity downed = (TrooperEntity) Troopers.spawnDowned(level, downedAt, Faction.REPUBLIC, owner.getUUID());
		List<TrooperEntity> rivals = squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.RIFLEMAN), new BlockPos(16, 1, 16), null, IncidentMode.ARMY);
		freeze(rivals);
		float health = downed.getHealth();

		helper.assertTrue(downed.isDowned() && Troopers.isDowned(downed) && !downed.canFight(), msg("downed"));
		helper.assertTrue(!downed.canBeSeenAsEnemy() && !rivals.get(0).wants(downed), msg("never a target, even for the other army under an incident"));
		helper.assertTrue(!downed.hurtServer(level, level.damageSources().playerAttack(owner), 10f)
				&& !downed.hurtServer(level, level.damageSources().mobAttack(rivals.get(0)), 10f) && near(downed.getHealth(), health),
				msg("takes no damage"));
		helper.assertTrue(downed.bleedAt() < 0, msg("never bleeds out until a mission says so"));

		// On its tick.
		TrooperEntity onTick = (TrooperEntity) Troopers.spawnDowned(level, helper.absolutePos(new BlockPos(4, 1, 16)), Faction.REPUBLIC, owner.getUUID());
		Troopers.bleedOut(onTick, now + 3);

		// As it loads: a saved downed trooper whose time has passed never ticks again.
		TrooperEntity saved = (TrooperEntity) Troopers.spawnDowned(level, helper.absolutePos(new BlockPos(20, 1, 4)), Faction.SITH_EMPIRE, owner.getUUID());
		Troopers.bleedOut(saved, now);
		CompoundTag tag = save(level, saved);
		saved.discard();
		TrooperEntity loaded = EnemyEntities.SITH_TROOPER.create(level, EntitySpawnReason.LOAD);
		helper.assertTrue(loaded != null, msg("the type creates a trooper"));
		loaded.load(input(level, tag));
		loaded.setUUID(new UUID(0x7200_0001L, 0));
		place(helper, loaded, new BlockPos(20, 1, 4));
		helper.assertTrue(loaded.isDowned() && loaded.bleedAt() == now, msg("precondition: loaded downed with its BleedAt"));
		level.addFreshEntity(loaded);
		helper.assertTrue(loaded.isRemoved(), msg("a bled-out downed trooper is discarded as it loads"));

		// Tending: kneel ticks count once per tick however many callers drive them.
		TrooperEntity tended = (TrooperEntity) Troopers.spawnDowned(level, helper.absolutePos(new BlockPos(8, 1, 16)), Faction.REPUBLIC, owner.getUUID());
		int[] kneels = {0};

		new Script(helper)
				.step(t -> {
					kneels[0]++;
					boolean stood = Troopers.tend(owner, tended, false);
					helper.assertTrue(!Troopers.tend(owner, tended, false), msg("a second call in the same tick does not count"));

					if (!stood) {
						helper.assertTrue(tended.isDowned() && tended.tendProgress() == kneels[0], msg("one kneel tick per game tick"));
						helper.assertTrue(kneels[0] < b.tendTicks, msg("still downed after " + kneels[0] + " of " + b.tendTicks + " kneel ticks"));
						return false;
					}

					helper.assertTrue(kneels[0] == b.tendTicks, msg("stands after exactly " + b.tendTicks + " kneel ticks, took " + kneels[0]));
					helper.assertTrue(!tended.isDowned() && tended.isLeaving() && tended.getHealth() >= tended.getMaxHealth() * 0.5f - 1.0e-3f,
							msg("it stands at half health or more and walks off"));
					return true;
				})
				.then(() -> {
					helper.assertTrue(onTick.isRemoved(), msg("BleedAt discards a downed trooper on its tick"));
					helper.assertTrue(downed.isAlive() && downed.isDowned(), msg("without BleedAt it waits"));
				})
				.finallyDo(() -> M3TestPlayers.remove(owner))
				.run();
	}

	/** Only a player's kill drops anything: one durasteel plate at durasteelChance, never armour or commendations. Loot tables are empty. */
	@GameTest(maxTicks = 20)
	public void dropsNeverArmourOrCommendations(GameTestHelper helper) {
		TrooperBalance b = new TrooperBalance();
		RandomSource random = RandomSource.create(DROP_SEED);
		int plates = 0;

		for (int i = 0; i < DROP_ROLLS; i++) {
			List<ItemStack> drops = TrooperRules.deathDrops(random, true, b);
			helper.assertTrue(drops.size() <= 1, msg("at most one drop"));

			for (ItemStack stack : drops) {
				helper.assertTrue(stack.is(M3Items.DURASTEEL_PLATE) && stack.getCount() == 1, msg("the only drop is one durasteel plate"));
				helper.assertTrue(!stack.is(M3Items.REPUBLIC_COMMENDATION) && !stack.is(M3Items.IMPERIAL_COMMENDATION), msg("never a commendation"));
			}

			plates += drops.size();
			helper.assertTrue(TrooperRules.deathDrops(random, false, b).isEmpty(), msg("nothing without a player's kill"));
		}

		float rate = plates / (float) DROP_ROLLS;
		helper.assertTrue(Math.abs(rate - b.durasteelChance) < DROP_TOLERANCE,
				msg("plates drop at about " + b.durasteelChance + ", got " + rate + " (seed " + DROP_SEED + ")"));

		TrooperBalance never = new TrooperBalance();
		never.durasteelChance = 0f;
		TrooperBalance always = new TrooperBalance();
		always.durasteelChance = 1f;
		helper.assertTrue(TrooperRules.deathDrops(random, true, never).isEmpty() && TrooperRules.deathDrops(random, true, always).size() == 1,
				msg("the balance key decides"));

		RandomSource first = RandomSource.create(DROP_SEED);
		RandomSource second = RandomSource.create(DROP_SEED);

		for (int i = 0; i < 64; i++) {
			helper.assertTrue(TrooperRules.deathDrops(first, true, b).size() == TrooperRules.deathDrops(second, true, b).size(),
					msg("the same seed drops the same"));
		}

		for (String table : List.of("republic_trooper", "sith_trooper")) {
			JsonObject json = lootTable(table);
			helper.assertTrue(json != null, msg("loot_table/entities/" + table + ".json exists"));
			helper.assertTrue(json.get("type").getAsString().equals("minecraft:entity") && json.getAsJsonArray("pools").isEmpty(),
					msg(table + ": an empty entity table (the plate comes from the code, so the balance key applies)"));
		}

		helper.succeed();
	}

	private static @Nullable JsonObject lootTable(String name) {
		String path = "data/oldrepublic/loot_table/entities/" + name + ".json";

		try (InputStream stream = TrooperGameTests.class.getClassLoader().getResourceAsStream(path)) {
			if (stream == null) {
				return null;
			}

			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader).getAsJsonObject();
			}
		} catch (Exception e) {
			return null;
		}
	}

	// ---------------------------------------------------------------- bolts

	/**
	 * Trooper bolts carry their army's synced style; they pass through the shooter's own army and
	 * the players it is Allied with, and stop at anyone else. Each lane ends at a villager, which is
	 * only hurt when the bolt passed whatever stood in front of it.
	 */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void boltStyleSyncedAndAllyPass(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TrooperBalance b = TrooperBalance.get();
		TrooperEntity shooter = trooper(helper, Faction.REPUBLIC, TrooperRole.RIFLEMAN, new BlockPos(2, 1, 4));
		TrooperEntity imperial = trooper(helper, Faction.SITH_EMPIRE, TrooperRole.RIFLEMAN, new BlockPos(2, 1, 19));
		// The shooter's squadmate, so it is friendly by army and by squad alike.
		List<TrooperEntity> mates = squad(helper, Faction.REPUBLIC, List.of(TrooperRole.RIFLEMAN), new BlockPos(8, 1, 4), null, IncidentMode.NONE);
		TrooperEntity mate = mates.get(0);
		TrooperEntity target = trooper(helper, Faction.REPUBLIC, TrooperRole.RIFLEMAN, new BlockPos(8, 1, 19));
		freeze(List.of(shooter, imperial, mate, target));
		place(helper, mate, new BlockPos(8, 1, 4));
		place(helper, target, new BlockPos(8, 1, 19));
		ServerPlayer allied = survivor(helper, new BlockPos(8, 1, 9));
		ServerPlayer neutral = survivor(helper, new BlockPos(8, 1, 14));
		Identity.setReputation(allied, Faction.REPUBLIC, reputation(ReputationTier.TRUSTED));
		Identity.setReputation(neutral, Faction.REPUBLIC, reputation(ReputationTier.NEUTRAL));
		Mob behindMate = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(14, 1, 4));
		Mob behindAllied = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(14, 1, 9));
		Mob behindNeutral = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(14, 1, 14));
		Mob behindTarget = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(14, 1, 19));

		try {
			helper.assertTrue(shooter.boltStyle() == BlasterBolt.Style.REPUBLIC && imperial.boltStyle() == BlasterBolt.Style.IMPERIAL,
					msg("each army has its bolt style"));

			// The bolts fireAt makes: style from the synced data, never heavy, the role's damage.
			for (TrooperEntity firing : List.of(shooter, imperial)) {
				float damage = firing.isRepublic() ? b.republicBoltDamage : b.imperialBoltDamage;
				firing.fireAt(level, behindTarget, damage, firing.isRepublic() ? b.boltSpeed : b.imperialBoltSpeed);
				List<BlasterBolt> fired = level.getEntitiesOfClass(BlasterBolt.class, firing.getBoundingBox().inflate(4), bolt -> bolt.getOwner() == firing);
				helper.assertTrue(fired.size() == 1, msg(firing.armyKey() + ": one bolt per fireAt"));
				BlasterBolt bolt = fired.get(0);
				helper.assertTrue(bolt.style() == firing.boltStyle() && !bolt.heavy() && near(bolt.damage(), damage),
						msg(firing.armyKey() + ": a " + firing.boltStyle() + " bolt, not heavy, " + damage + " damage"));
				bolt.discard();
			}

			lane(helper, shooter, new BlockPos(3, 1, 4));
			lane(helper, shooter, new BlockPos(3, 1, 9));
			lane(helper, shooter, new BlockPos(3, 1, 14));
			lane(helper, imperial, new BlockPos(3, 1, 19));
		} catch (RuntimeException e) {
			M3TestPlayers.remove(allied);
			M3TestPlayers.remove(neutral);
			throw e;
		}

		float mateHealth = mate.getHealth();
		float targetHealth = target.getHealth();

		helper.runAfterDelay(LANE_BOLT_LIFE + 4, () -> {
			try {
				helper.assertTrue(near(mate.getHealth(), mateHealth) && behindMate.getHealth() < behindMate.getMaxHealth(),
						msg("a bolt passes through the shooter's squadmate"));
				helper.assertTrue(behindAllied.getHealth() < behindAllied.getMaxHealth(), msg("a bolt passes through a player the shooter is Allied with"));
				helper.assertTrue(near(behindNeutral.getHealth(), behindNeutral.getMaxHealth()), msg("a Neutral player stops the bolt"));
				helper.assertTrue(target.getHealth() < targetHealth && near(behindTarget.getHealth(), behindTarget.getMaxHealth()),
						msg("an Imperial bolt hits a Republic trooper: no friendly fire only within a side"));
			} finally {
				M3TestPlayers.remove(allied);
				M3TestPlayers.remove(neutral);
			}

			helper.succeed();
		});
	}

	/** A hand-aimed bolt from {@code owner} along +x at chest height, starting at {@code relative}. */
	private static void lane(GameTestHelper helper, TrooperEntity owner, BlockPos relative) {
		Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(relative)).add(0, 1.2, 0);
		BlasterBolt bolt = BlasterBolt.create(helper.getLevel(), owner, start, 4f, LANE_BOLT_LIFE, owner.boltStyle(), false);
		bolt.setDeltaMovement(new Vec3(LANE_BOLT_SPEED, 0, 0));
		helper.getLevel().addFreshEntity(bolt);
	}

	/** The Imperial rifleman's shot (7.3): after the charge tell, one normal (not heavy) crimson bolt of imperialBoltDamage. */
	@GameTest(structure = ARENA, maxTicks = 120)
	public void imperialBoltIsNormal(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TrooperBalance b = TrooperBalance.get();
		TrooperEntity rifleman = trooper(helper, Faction.SITH_EMPIRE, TrooperRole.RIFLEMAN, new BlockPos(4, 1, 12));
		// Twelve blocks away: inside the 10-18 band, so it holds its ground and fires.
		Mob villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(16, 1, 12));
		boolean[] charged = {false};
		BlasterBolt[] seen = {null};

		new Script(helper)
				// The villager "struck first": the trooper fights back.
				.then(() -> helper.assertTrue(rifleman.hurtServer(level, level.damageSources().mobAttack(villager), 1f), msg("precondition: the hit lands")))
				.await("the rifleman fires", 90, () -> {
					charged[0] |= rifleman.action() == TrooperAction.CHARGING;
					List<BlasterBolt> bolts = level.getEntitiesOfClass(BlasterBolt.class, rifleman.getBoundingBox().inflate(24),
							bolt -> bolt.getOwner() == rifleman);

					if (bolts.isEmpty()) {
						return false;
					}

					seen[0] = bolts.get(0);
					return true;
				})
				.then(() -> {
					BlasterBolt bolt = seen[0];
					helper.assertTrue(charged[0], msg("the " + b.chargeTicks + "-tick charge tell came first"));
					helper.assertTrue(bolt.style() == BlasterBolt.Style.IMPERIAL, msg("a crimson Imperial bolt"));
					helper.assertTrue(!bolt.heavy(), msg("a normal bolt: a guard can deflect it"));
					helper.assertTrue(near(bolt.damage(), b.imperialBoltDamage), msg("imperialBoltDamage " + b.imperialBoltDamage + ", got " + bolt.damage()));
				})
				.run();
	}

	// ---------------------------------------------------------------- role specials

	/** The kolto channel: a hit of interruptDamage or a Push breaks it with no heal; a lighter hit does not, and the ally is healed by medicHeal. */
	@GameTest(structure = ARENA, maxTicks = 300)
	public void medicChannelInterruptedByHit4(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TrooperBalance b = TrooperBalance.get();
		List<TrooperEntity> squad = squad(helper, Faction.REPUBLIC, List.of(TrooperRole.MEDIC, TrooperRole.RIFLEMAN), new BlockPos(12, 1, 12), null,
				IncidentMode.NONE);
		TrooperEntity medic = squad.get(0);
		TrooperEntity patient = squad.get(1);
		freeze(List.of(patient));
		patient.setHealth(patient.getMaxHealth() * 0.4f);
		float before = patient.getHealth();

		helper.assertTrue(TrooperRules.hitInterrupts(TrooperAction.HEALING, b.interruptDamage, b)
				&& !TrooperRules.hitInterrupts(TrooperAction.HEALING, b.interruptDamage - 0.01f, b)
				&& !TrooperRules.hitInterrupts(TrooperAction.CHARGING, 100f, b), msg("only a channel breaks, at interruptDamage or more"));

		new Script(helper)
				// A hit of interruptDamage.
				.then(() -> helper.assertTrue(medic.perform("heal", null), msg("the medic takes the heal request")))
				.await("the medic kneels into the channel", 60, () -> medic.action() == TrooperAction.HEALING)
				.then(() -> helper.assertTrue(medic.hurtServer(level, level.damageSources().generic(), b.interruptDamage), msg("precondition: the hit lands")))
				.await("the hit breaks the channel", 3, () -> medic.action() != TrooperAction.HEALING)
				.idle(b.medicChannelTicks + 5)
				.then(() -> helper.assertTrue(near(patient.getHealth(), before), msg("a broken channel heals nothing")))
				// A Push.
				.then(() -> helper.assertTrue(medic.perform("heal", null), msg("the medic takes the heal request")))
				.await("the medic kneels again", 60, () -> medic.action() == TrooperAction.HEALING)
				.then(() -> ForceReactions.interruptWindup(medic, null, ForceReactions.CAUSE_FORCE_PUSH))
				.await("the Push breaks the channel", 3, () -> medic.action() != TrooperAction.HEALING)
				.idle(b.medicChannelTicks + 5)
				.then(() -> helper.assertTrue(near(patient.getHealth(), before), msg("a pushed medic heals nothing")))
				// A lighter hit.
				.then(() -> helper.assertTrue(medic.perform("heal", null), msg("the medic takes the heal request")))
				.await("the medic kneels a third time", 60, () -> medic.action() == TrooperAction.HEALING)
				.then(() -> helper.assertTrue(medic.hurtServer(level, level.damageSources().generic(), b.interruptDamage - 1f),
						msg("precondition: the lighter hit lands")))
				.await("the channel completes", b.medicChannelTicks + 5, () -> medic.action() != TrooperAction.HEALING)
				.then(() -> helper.assertTrue(near(patient.getHealth(), Math.min(patient.getMaxHealth(), before + b.medicHeal)),
						msg("a hit under interruptDamage does not stop the heal of " + b.medicHeal + ", health " + patient.getHealth())))
				.run();
	}

	/**
	 * Rally and the flare: a stagger, Push, Pull, Disarming Pull or the kill during the tell cancels
	 * it with no effect; an untouched tell rallies the squad (Resistance I, shorter gap) or calls
	 * reinforceCount riflemen. The scheduled arrivals are cancelled at the end.
	 */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void rallyAndReinforcementCancelWindows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TrooperBalance b = TrooperBalance.get();
		List<TrooperEntity> republic = squad(helper, Faction.REPUBLIC, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.RIFLEMAN),
				new BlockPos(5, 1, 5), null, IncidentMode.NONE);
		List<TrooperEntity> imperialA = squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN), new BlockPos(19, 1, 5),
				null, IncidentMode.NONE);
		List<TrooperEntity> imperialB = squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN), new BlockPos(5, 1, 19),
				null, IncidentMode.NONE);
		List<TrooperEntity> imperialC = squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN), new BlockPos(19, 1, 19),
				null, IncidentMode.NONE);
		// Only the sergeants think; the rest stay where they are (well inside rallyRadius).
		freeze(republic.subList(1, 3));
		freeze(List.of(imperialA.get(1), imperialB.get(1), imperialC.get(1)));
		TrooperEntity rally = republic.get(0);
		TrooperEntity flareA = imperialA.get(0);
		TrooperEntity flareB = imperialB.get(0);
		TrooperEntity flareC = imperialC.get(0);
		UUID squadA = Squads.key(flareA);
		UUID squadB = Squads.key(flareB);
		UUID squadC = Squads.key(flareC);
		int longestTell = Math.max(b.rallyTellTicks, b.reinforceTellTicks);
		boolean[] cancelled = new boolean[3];
		long[] rallyStart = {0};
		boolean[] started = new boolean[3];

		new Script(helper)
				// Window 1: a stagger on the Rally fist, a Push on flare A, the kill on flare B.
				.then(() -> helper.assertTrue(rally.perform("rally", null) && flareA.perform("flare", null) && flareB.perform("flare", null),
						msg("the sergeants take the requests")))
				.step(t -> {
					if (!cancelled[0] && rally.action() == TrooperAction.RALLY_TELL) {
						ForceReactions.interruptWindup(rally, null, ForceReactions.CAUSE_STAGGER);
						cancelled[0] = true;
					}

					if (!cancelled[1] && flareA.action() == TrooperAction.FLARE_TELL) {
						ForceReactions.interruptWindup(flareA, null, ForceReactions.CAUSE_FORCE_PUSH);
						cancelled[1] = true;
					}

					if (!cancelled[2] && flareB.action() == TrooperAction.FLARE_TELL) {
						flareB.kill(level);
						cancelled[2] = true;
					}

					helper.assertTrue(t < 20, msg("every tell shows within 20 ticks"));
					return cancelled[0] && cancelled[1] && cancelled[2];
				})
				.await("the interrupted tells drop", 3, () -> rally.action() != TrooperAction.RALLY_TELL && flareA.action() != TrooperAction.FLARE_TELL)
				.idle(longestTell + 5)
				.then(() -> {
					for (TrooperEntity member : republic) {
						helper.assertTrue(!member.isRallied() && !member.hasEffect(MobEffects.RESISTANCE), msg("a staggered Rally gives nothing"));
					}

					helper.assertTrue(Reinforcements.pending(squadA) == 0, msg("a pushed flare calls nobody"));
					helper.assertTrue(Reinforcements.pending(squadB) == 0 && !flareB.isAlive(), msg("a flare sergeant killed in the tell calls nobody"));
				})
				// Window 2: an untouched Rally, a Disarming Pull on flare A, an untouched flare C.
				.then(() -> helper.assertTrue(rally.perform("rally", null) && flareA.perform("flare", null) && flareC.perform("flare", null),
						msg("the sergeants take the requests")))
				.step(t -> {
					if (!started[0] && rally.action() == TrooperAction.RALLY_TELL) {
						rallyStart[0] = level.getGameTime();
						started[0] = true;
					}

					if (!started[1] && flareA.action() == TrooperAction.FLARE_TELL) {
						ForceStatus.disarm(flareA, 120);
						started[1] = true;
					}

					started[2] |= flareC.action() == TrooperAction.FLARE_TELL;
					helper.assertTrue(t < 20, msg("every tell shows within 20 ticks"));
					return started[0] && started[1] && started[2];
				})
				.await("the Rally and flare C finish", longestTell + 10,
						() -> rally.action() != TrooperAction.RALLY_TELL && flareC.action() != TrooperAction.FLARE_TELL)
				.then(() -> {
					helper.assertTrue(level.getGameTime() - rallyStart[0] >= b.rallyTellTicks - 1, msg("the Rally fist showed its full " + b.rallyTellTicks
							+ " ticks"));

					for (TrooperEntity member : republic) {
						helper.assertTrue(member.isRallied() && member.hasEffect(MobEffects.RESISTANCE), msg(member.role() + " is rallied with Resistance"));
					}

					helper.assertTrue(TrooperRules.shotCooldown(b.burstCooldown, true, b) < b.burstCooldown
							&& TrooperRules.shotCooldown(b.burstCooldown, true, b) == Math.round(b.burstCooldown * (1f - b.rallyFireRate)),
							msg("a rallied burst gap is " + b.rallyFireRate * 100 + "% shorter"));
					helper.assertTrue(Reinforcements.pending(squadA) == 0 && flareA.action() != TrooperAction.FLARE_TELL,
							msg("a Disarming Pull cancels the flare"));
					helper.assertTrue(Reinforcements.pending(squadC) == b.reinforceCount && !Squads.flareReady(flareC),
							msg("the flare calls " + b.reinforceCount + " riflemen, once per encounter"));
					// The flare marks the encounter; it ends encounterEndTicks after the last fight (pure rule, explicit times).
					long flared = Squads.lastCombat(flareC);
					helper.assertTrue(flared != TrooperRules.NEVER && flared <= level.getGameTime(), msg("the flare marks the squad's encounter"));
					helper.assertTrue(!TrooperRules.encounterOver(TrooperRules.NEVER, flared, b.encounterEndTicks)
							&& !TrooperRules.encounterOver(flared, flared + b.encounterEndTicks - 1, b.encounterEndTicks)
							&& TrooperRules.encounterOver(flared, flared + b.encounterEndTicks, b.encounterEndTicks),
							msg("the encounter, and with it the used flare, ends exactly " + b.encounterEndTicks + " ticks after the last fight"));
				})
				.finallyDo(() -> {
					// Nobody actually comes: the arrivals would land outside the arena.
					Reinforcements.cancel(squadA);
					Reinforcements.cancel(squadB);
					Reinforcements.cancel(squadC);
				})
				.run();
	}

	// ---------------------------------------------------------------- barks, despawning, peace, sleep

	/** One bark per trooper per barkTrooperTicks and one per squad per barkSquadTicks; at most three fire sounds per squad per four ticks. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void barkRateLimits(GameTestHelper helper) {
		TrooperBalance b = TrooperBalance.get();
		long now = 10_000;
		long never = TrooperRules.NEVER;
		helper.assertTrue(TrooperRules.barkAllowed(now, never, never, b), msg("a first bark is allowed"));
		helper.assertTrue(!TrooperRules.barkAllowed(now, now - b.barkTrooperTicks + 1, never, b)
				&& TrooperRules.barkAllowed(now, now - b.barkTrooperTicks, never, b), msg("one per trooper per " + b.barkTrooperTicks + " ticks"));
		helper.assertTrue(!TrooperRules.barkAllowed(now, never, now - b.barkSquadTicks + 1, b)
				&& TrooperRules.barkAllowed(now, never, now - b.barkSquadTicks, b), msg("one per squad per " + b.barkSquadTicks + " ticks"));
		helper.assertTrue(!TrooperRules.barkAllowed(now, now - 10 * b.barkTrooperTicks, now - 1, b), msg("both limits apply"));

		List<TrooperEntity> squad = squad(helper, Faction.REPUBLIC, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN), new BlockPos(6, 1, 6), null,
				IncidentMode.NONE);
		TrooperEntity loner = trooper(helper, Faction.REPUBLIC, TrooperRole.RIFLEMAN, new BlockPos(18, 1, 18));
		TrooperEntity gunner = trooper(helper, Faction.SITH_EMPIRE, TrooperRole.RIFLEMAN, new BlockPos(18, 1, 6));
		freeze(squad);
		freeze(List.of(loner, gunner));
		helper.assertTrue(Barks.say(squad.get(0), BarkCategory.SPOT), msg("the sergeant barks"));
		helper.assertTrue(!Barks.say(squad.get(0), BarkCategory.HURT), msg("not twice"));
		helper.assertTrue(!Barks.say(squad.get(1), BarkCategory.SPOT), msg("nor does his squad right after"));
		helper.assertTrue(Barks.say(loner, BarkCategory.SPOT), msg("another squad may"));

		for (int i = 0; i < TrooperRules.FIRE_SOUNDS_PER_WINDOW; i++) {
			helper.assertTrue(Squads.claimFireSound(gunner), msg("fire sound " + (i + 1) + " plays"));
		}

		helper.assertTrue(!Squads.claimFireSound(gunner), msg("beyond three per window: flash only"));
		helper.runAfterDelay(TrooperRules.FIRE_SOUND_WINDOW, () -> {
			helper.assertTrue(Squads.claimFireSound(gunner), msg("the next window plays again"));
			helper.succeed();
		});
	}

	/** No trooper is ever removed for being far from players: garrisons stay and patrols despawn as whole squads. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void notRemovedWhenFarAway(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<TrooperEntity> all = new ArrayList<>();
		all.addAll(squad(helper, Faction.REPUBLIC, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.MEDIC), new BlockPos(4, 1, 4), null,
				IncidentMode.NONE));
		all.addAll(squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.GRENADIER), new BlockPos(20, 1, 4),
				null, IncidentMode.NONE));
		all.add((TrooperEntity) Troopers.spawnOfficer(level, helper.absolutePos(new BlockPos(4, 1, 20)), Faction.REPUBLIC, 0));
		all.add((TrooperEntity) Troopers.spawnOfficer(level, helper.absolutePos(new BlockPos(20, 1, 20)), Faction.SITH_EMPIRE, 1));
		all.add((TrooperEntity) Troopers.spawnDowned(level, helper.absolutePos(new BlockPos(12, 1, 20)), Faction.REPUBLIC, new UUID(0x7200_0003L, 0)));
		freeze(all);

		for (TrooperEntity trooper : all) {
			helper.assertTrue(trooper.isPersistenceRequired(), msg(trooper.armyKey() + " " + trooper.role() + " from a spawner is persistent"));
		}

		List<LivingEntity> patrol = Troopers.spawnSquad(level, helper.absolutePos(new BlockPos(12, 1, 12)), Faction.REPUBLIC,
				Troopers.defaultSquad(Faction.REPUBLIC, 1), null, IncidentMode.NONE);
		Troopers.startPatrol(patrol, List.of(helper.absolutePos(new BlockPos(12, 1, 22))));

		for (LivingEntity member : patrol) {
			TrooperEntity trooper = (TrooperEntity) member;
			trooper.setNoAi(true);
			helper.assertTrue(Troopers.isPatrol(trooper) && !trooper.shouldBeSaved(), msg("a patrol member is never saved"));
			all.add(trooper);
		}

		TrooperEntity loose = EnemyEntities.REPUBLIC_TROOPER.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(loose != null, msg("the type creates a trooper"));
		place(helper, loose, new BlockPos(12, 1, 4));
		level.addFreshEntity(loose);
		loose.setNoAi(true);
		helper.assertTrue(!loose.isPersistenceRequired(), msg("precondition: nobody made this one persistent"));
		all.add(loose);

		for (TrooperEntity trooper : all) {
			for (double distanceSq : new double[] {0, 32 * 32, 128 * 128 + 1, 1.0e8}) {
				helper.assertTrue(!trooper.removeWhenFarAway(distanceSq), msg(trooper.armyKey() + " " + trooper.role() + " is not removed at distance^2 "
						+ distanceSq));
			}
		}

		loose.checkDespawn();
		helper.assertTrue(!loose.isRemoved(), msg("the despawn check leaves even a non-persistent trooper"));
		helper.succeed();
	}

	/** Trooper types are allowed in Peaceful, and an officer survives the despawn check; only /kill and the void hurt it. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void officersSurvivePeacefulTick(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(EnemyEntities.REPUBLIC_TROOPER.isAllowedInPeaceful() && EnemyEntities.SITH_TROOPER.isAllowedInPeaceful(),
				msg("trooper types are allowed in Peaceful (no notInPeaceful)"));
		ServerPlayer player = M3TestPlayers.listed(helper, new BlockPos(2, 1, 2));

		try {
			TrooperEntity republic = (TrooperEntity) Troopers.spawnOfficer(level, helper.absolutePos(new BlockPos(6, 1, 12)), Faction.REPUBLIC, 2);
			TrooperEntity imperial = (TrooperEntity) Troopers.spawnOfficer(level, helper.absolutePos(new BlockPos(18, 1, 12)), Faction.SITH_EMPIRE, 5);
			Mob villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(12, 1, 18));

			for (TrooperEntity officer : List.of(republic, imperial)) {
				String who = officer.armyKey() + " officer: ";
				helper.assertTrue(officer.isOfficer() && officer.isPersistenceRequired() && officer.getMainHandItem().isEmpty(),
						msg(who + "persistent and unarmed"));
				helper.assertTrue(Component.translatable("npc.oldrepublic.officer." + officer.armyKey() + "." + officer.nameSeed())
						.equals(officer.getCustomName()), msg(who + "named by its name seed"));
				officer.checkDespawn();
				helper.assertTrue(!officer.isRemoved(), msg(who + "survives the despawn check"));
				float health = officer.getHealth();

				for (var source : List.of(level.damageSources().playerAttack(player), level.damageSources().mobAttack(villager),
						level.damageSources().magic(), level.damageSources().generic())) {
					helper.assertTrue(!officer.hurtServer(level, source, 1000f) && near(officer.getHealth(), health), msg(who + "takes no "
							+ source.getMsgId() + " damage"));
				}

				helper.assertTrue(!officer.canBeSeenAsEnemy() && !officer.canFight(), msg(who + "a non-combatant, never a target"));
			}

			helper.assertTrue(imperial.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE) && !imperial.isAlive(), msg("/kill works"));
			helper.assertTrue(republic.hurtServer(level, level.damageSources().fellOutOfWorld(), Float.MAX_VALUE) && !republic.isAlive(),
					msg("the void works"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	/** Troopers are not Monsters or Enemies, so even a squad Hostile to the player never keeps them from sleeping. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void troopersNeverBlockSleep(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer sleeper = M3TestPlayers.listed(helper, new BlockPos(12, 1, 12));

		try {
			Identity.setReputation(sleeper, Faction.REPUBLIC, reputation(ReputationTier.HOSTILE));
			Identity.setReputation(sleeper, Faction.SITH_EMPIRE, reputation(ReputationTier.HOSTILE));
			List<TrooperEntity> troopers = new ArrayList<>();
			troopers.addAll(squad(helper, Faction.REPUBLIC, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.MEDIC), new BlockPos(9, 1, 12),
					null, IncidentMode.NONE));
			troopers.addAll(squad(helper, Faction.SITH_EMPIRE, List.of(TrooperRole.SERGEANT, TrooperRole.RIFLEMAN, TrooperRole.GRENADIER),
					new BlockPos(15, 1, 12), null, IncidentMode.ARMY));
			troopers.add((TrooperEntity) Troopers.spawnOfficer(level, helper.absolutePos(new BlockPos(12, 1, 9)), Faction.REPUBLIC, 0));
			freeze(troopers);
			helper.assertTrue(EnemyEntities.REPUBLIC_TROOPER.getCategory() == MobCategory.MISC && EnemyEntities.SITH_TROOPER.getCategory() == MobCategory.MISC,
					msg("troopers are MISC, not MONSTER"));

			for (TrooperEntity trooper : troopers) {
				Entity entity = trooper;
				helper.assertTrue(!(entity instanceof Enemy) && !(entity instanceof Monster), msg(trooper.role() + " is no Monster or Enemy"));
			}

			// What a bed asks: monsters within 8 x 5 x 8 that prevent rest.
			AABB around = new AABB(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 1, 12))), Vec3.atBottomCenterOf(
					helper.absolutePos(new BlockPos(12, 1, 12)))).inflate(8, 5, 8);
			helper.assertTrue(level.getEntitiesOfClass(Monster.class, around, monster -> monster.isPreventingPlayerRest(level, sleeper)).isEmpty(),
					msg("nothing keeps the player awake"));

			// The same question finds a zombie.
			helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(12, 1, 15));
			helper.assertTrue(!level.getEntitiesOfClass(Monster.class, around, monster -> monster.isPreventingPlayerRest(level, sleeper)).isEmpty(),
					msg("a zombie still does"));
		} finally {
			M3TestPlayers.remove(sleeper);
		}

		helper.succeed();
	}
}
