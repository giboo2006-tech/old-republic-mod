package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.boss.BossArenas;
import com.oldrepublic.combat.Allegiance;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.combat.CombatHooks.DeflectResult;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.construct.RuinSentinel;
import com.oldrepublic.enemy.construct.SentinelBalance;
import com.oldrepublic.enemy.construct.SentinelEntities;
import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.construct.Sentinels;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberForm;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3 Ruin Sentinels (W5, spec 7.7 and 17.1): the two variants and their saved data, the
 * side rule that keeps a sentinel dormant for its own side (alignment 15), {@code Sentinels.wakeNear}
 * never waking a sentinel dormant for its cause, bound sentinels being hostile to every side (and
 * crumbling for their owner in Peaceful), and the lance: blocked by a late guard, returned only by a
 * parry (staggering the sentinel), and aimed at the crosshair inside a boss arena.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks} (at most 400); fixtures are the lead's
 * {@code oldrepublic_test:arena_24x7x24}; proximity tests use listed players ({@link M3TestPlayers})
 * removed at the end; randomness is never needed; no test changes the difficulty, the day time or
 * {@code WorldSites}. The difficulty is read, never set: in Peaceful the tests assert the Peaceful
 * rules instead (dormant sentinels never wake for players; an owner-bound sentinel crumbles). The
 * guards that meet a lance are fake players outside the level (no sentinel ever notices them), with
 * their use time pinned as in {@code SaberGameTests}. The one boss arena a test tracks is untracked
 * in its clean-up.
 */
public class SentinelGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	/** The dormant check runs every 10 ticks; a wake must be seen within this many. */
	private static final int WAKE_CHECK_TICKS = 25;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-3;
	}

	private static boolean peaceful(GameTestHelper helper) {
		return helper.getLevel().getDifficulty() == Difficulty.PEACEFUL;
	}

	// ---------------------------------------------------------------- fixtures

	/** A dormant sentinel spawned through the frozen {@link Sentinels#spawn} at {@code relative}. */
	private static RuinSentinel sentinel(GameTestHelper helper, BlockPos relative, SentinelVariant variant, boolean bound, @Nullable UUID owner) {
		return (RuinSentinel) Sentinels.spawn(helper.getLevel(), helper.absolutePos(relative), variant, bound, owner);
	}

	/** Sets a player's alignment directly (no shift caps, no log). */
	private static void align(ServerPlayer player, int alignment) {
		IdentityData data = Identity.get(player);
		data.alignment = alignment;
		Identity.commit(player, data);
	}

	/** A listed player at {@code relative} with {@code alignment}, in survival so a sentinel can target them. */
	private static ServerPlayer aligned(GameTestHelper helper, BlockPos relative, int alignment) {
		ServerPlayer player = M3TestPlayers.listed(helper, relative);
		player.setGameMode(GameType.SURVIVAL);
		align(player, alignment);
		return player;
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

	/** A fresh sentinel loaded from {@code tag}, not added to the level. */
	private static RuinSentinel load(GameTestHelper helper, CompoundTag tag) {
		ServerLevel level = helper.getLevel();
		RuinSentinel copy = SentinelEntities.RUIN_SENTINEL.create(level, EntitySpawnReason.LOAD);
		helper.assertTrue(copy != null, msg("the type creates a sentinel"));
		copy.load(input(level, tag));
		return copy;
	}

	/**
	 * A fake server player (outside the level: no sentinel ever sees it) that has held its guard for a
	 * fixed number of ticks: 0 is inside the parry window, 20 is past it.
	 */
	private static final class GuardingPlayer extends FakePlayer {
		private final int ticksUsing;

		GuardingPlayer(ServerLevel level, int ticksUsing, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
			this.ticksUsing = ticksUsing;
		}

		@Override
		public int getTicksUsingItem() {
			return isUsingItem() ? ticksUsing : 0;
		}
	}

	/**
	 * A fake player at {@code relative} facing {@code yaw} (pitch 0), holding a lit saber with
	 * {@code emitter}; guarding (use time {@code ticksUsing}) when {@code ticksUsing >= 0}.
	 */
	private static GuardingPlayer guard(GameTestHelper helper, BlockPos relative, float yaw, int ticksUsing, Emitter emitter, String name) {
		ServerLevel level = helper.getLevel();
		GuardingPlayer player = new GuardingPlayer(level, Math.max(0, ticksUsing), name);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(relative)));
		turn(player, yaw, 0f);
		ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(player.getUUID(), name, BladeColor.GREEN.rgb()), BladeColor.GREEN,
				emitter, Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
		SaberToggle.setIgnited(saber, true, level.getGameTime());
		player.setItemInHand(InteractionHand.MAIN_HAND, saber);

		if (ticksUsing >= 0) {
			player.startUsingItem(InteractionHand.MAIN_HAND);
			helper.assertTrue(player.isUsingItem() && !SaberGuard.guardingSaber(player).isEmpty(), msg("the fake player " + name + " must be guarding"));
		}

		return player;
	}

	private static void turn(Entity entity, float yaw, float pitch) {
		entity.setYRot(yaw);
		entity.setXRot(pitch);
		entity.setYHeadRot(yaw);
	}

	/** Turns {@code player} so its crosshair runs from its eyes through {@code target}. */
	private static void lookAt(Entity player, Vec3 target) {
		Vec3 to = target.subtract(player.getEyePosition());
		float yaw = (float) (Mth.atan2(to.z, to.x) * (180.0 / Math.PI)) - 90f;
		float pitch = (float) -(Mth.atan2(to.y, to.horizontalDistance()) * (180.0 / Math.PI));
		turn(player, yaw, pitch);
	}

	/** A heavy {@code SENTINEL} bolt fired by {@code shooter} at {@code target}'s chest from 1.5 blocks out, as the lance is. */
	private static BlasterBolt lance(ServerLevel level, Entity shooter, Entity target) {
		SentinelBalance b = SentinelBalance.get();
		Vec3 chest = target.getBoundingBox().getCenter();
		Vec3 from = shooter.getEyePosition().subtract(chest).normalize();
		BlasterBolt bolt = BlasterBolt.create(level, shooter, chest.add(from.scale(1.5)), b.lanceDamage, 40, BlasterBolt.Style.SENTINEL, true);
		bolt.setDeltaMovement(from.reverse().scale(b.lanceSpeed));
		return bolt;
	}

	private static double degreesBetween(Vec3 a, Vec3 b) {
		return Math.toDegrees(Math.acos(Mth.clamp(a.normalize().dot(b.normalize()), -1.0, 1.0)));
	}

	/**
	 * A test run tick by tick: each step is polled once per tick until it reports done, then the next
	 * step starts on the following tick; after the last step the clean-up runs and the test succeeds.
	 * A failing step also runs the clean-up before the failure propagates.
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

	// ---------------------------------------------------------------- variants and saved data

	/** Watcher 48 hp, Tomb 60, armour 8, their speeds; dormant and ANCIENT; Variant, Bound, Owner and Dormant survive a save, read tolerantly. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void sentinelVariantsAndNbt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SentinelBalance b = SentinelBalance.get();
		SentinelBalance defaults = new SentinelBalance();
		helper.assertTrue(near(defaults.watcherHealth, 48) && near(defaults.tombHealth, 60) && near(defaults.armor, 8)
				&& near(defaults.watcherSpeed, 0.18) && near(defaults.tombSpeed, 0.16), msg("spec 14: Watcher 48 hp / 0.18, Tomb 60 hp / 0.16, armour 8"));
		UUID owner = UUID.randomUUID();
		RuinSentinel watcher = sentinel(helper, new BlockPos(8, 1, 4), SentinelVariant.WATCHER, false, null);
		RuinSentinel tomb = sentinel(helper, new BlockPos(14, 1, 4), SentinelVariant.TOMB, true, owner);

		try {
			helper.assertTrue(watcher.variant() == SentinelVariant.WATCHER && tomb.variant() == SentinelVariant.TOMB, msg("each keeps its variant"));
			helper.assertTrue(near(watcher.getMaxHealth(), b.watcherHealth) && near(watcher.getHealth(), b.watcherHealth),
					msg("a Watcher spawns with " + b.watcherHealth + " health, got " + watcher.getHealth() + "/" + watcher.getMaxHealth()));
			helper.assertTrue(near(tomb.getMaxHealth(), b.tombHealth) && near(tomb.getHealth(), b.tombHealth),
					msg("a Tomb sentinel spawns with " + b.tombHealth + " health, got " + tomb.getHealth() + "/" + tomb.getMaxHealth()));
			helper.assertTrue(near(watcher.getAttributeBaseValue(Attributes.ARMOR), b.armor) && near(tomb.getAttributeBaseValue(Attributes.ARMOR), b.armor),
					msg("both have " + b.armor + " armour"));
			helper.assertTrue(near(watcher.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), b.speed(SentinelVariant.WATCHER))
					&& near(tomb.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), b.speed(SentinelVariant.TOMB)), msg("each moves at its variant's speed"));
			helper.assertTrue(watcher.isDormant() && tomb.isDormant() && !watcher.isBound() && tomb.isBound() && owner.equals(tomb.owner()) && watcher.owner() == null,
					msg("spawned dormant, with the bound flag and owner given"));
			helper.assertTrue(watcher.allegiance() == Allegiance.ANCIENT && tomb.allegiance() == Allegiance.ANCIENT && !tomb.isArchive(),
					msg("worldgen and trail sentinels are ANCIENT"));
			helper.assertTrue(Sentinels.isSentinel(watcher) && watcher.shouldBeSaved() && watcher.isPersistenceRequired(), msg("a sentinel is saved and persistent"));

			// The saved layout (W5 plan 4.6).
			CompoundTag tag = save(level, tomb);
			ValueInput saved = input(level, tag);
			helper.assertTrue(saved.getStringOr("Variant", "").equals("tomb"), msg("Variant is the lowercase variant name"));
			helper.assertTrue(saved.getBooleanOr("Bound", false) && saved.getBooleanOr("Dormant", false), msg("Bound and Dormant are saved"));
			helper.assertTrue(saved.read("Owner", UUIDUtil.CODEC).filter(owner::equals).isPresent(), msg("Owner is the owner's UUID"));
			ValueInput savedWatcher = input(level, save(level, watcher));
			helper.assertTrue(savedWatcher.getStringOr("Variant", "").equals("watcher") && !savedWatcher.getBooleanOr("Bound", true),
					msg("a Watcher saves as watcher, unbound"));

			RuinSentinel copy = load(helper, tag);
			helper.assertTrue(copy.variant() == SentinelVariant.TOMB && copy.isBound() && owner.equals(copy.owner()) && copy.isDormant(),
					msg("variant, bound flag, owner and dormancy survive a reload"));

			// An awake sentinel reloads awake.
			watcher.wake(null);
			helper.assertTrue(!load(helper, save(level, watcher)).isDormant(), msg("an awake sentinel reloads awake"));

			// Read tolerantly: an ordinal, then anything else as a Watcher.
			CompoundTag ordinal = save(level, tomb);
			// CompoundTag#putInt(String, int) is in the dumps (enemy/world bytecode).
			// API-CHECK: CompoundTag#putString(String, String) (long-standing; not in the dumps)
			ordinal.putInt("Variant", SentinelVariant.TOMB.ordinal());
			helper.assertTrue(load(helper, ordinal).variant() == SentinelVariant.TOMB, msg("an ordinal Variant is read"));
			CompoundTag unknown = save(level, tomb);
			unknown.putString("Variant", "colossus");
			helper.assertTrue(load(helper, unknown).variant() == SentinelVariant.WATCHER, msg("an unknown Variant reads as a Watcher"));
			unknown.putString("Variant", "TOMB");
			helper.assertTrue(load(helper, unknown).variant() == SentinelVariant.TOMB, msg("the name is read case-blind"));
		} finally {
			watcher.discard();
			tomb.discard();
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the side rule

	/**
	 * Alignment +15 keeps a Watcher dormant (it bows its head) while a Tomb sentinel wakes; -15 the
	 * other way round; 14 keeps neither. A dormant sentinel never keeps a player from resting.
	 */
	@GameTest(structure = ARENA, maxTicks = 120)
	public void sentinelDormantForOwnSideAt15(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int threshold = SentinelBalance.get().dormantAlignment;
		helper.assertTrue(new SentinelBalance().dormantAlignment == 15, msg("spec 7.7: the side threshold is 15"));
		boolean peaceful = peaceful(helper);
		RuinSentinel watcher = sentinel(helper, new BlockPos(8, 1, 4), SentinelVariant.WATCHER, false, null);
		RuinSentinel tomb = sentinel(helper, new BlockPos(14, 1, 4), SentinelVariant.TOMB, false, null);
		// Within 6 blocks of both (5 each).
		ServerPlayer player = aligned(helper, new BlockPos(11, 1, 8), threshold);

		new Script(helper)
				.then(() -> {
					// The rule itself, at the edges.
					helper.assertTrue(watcher.sleepsFor(player) && !tomb.sleepsFor(player), msg("at +" + threshold + " a Watcher sleeps on and a Tomb sentinel does not"));
					align(player, threshold - 1);
					helper.assertTrue(!watcher.sleepsFor(player) && !tomb.sleepsFor(player), msg("at +" + (threshold - 1) + " neither sleeps on"));
					align(player, -threshold);
					helper.assertTrue(!watcher.sleepsFor(player) && tomb.sleepsFor(player), msg("at -" + threshold + " a Tomb sentinel sleeps on and a Watcher does not"));
					align(player, -(threshold - 1));
					helper.assertTrue(!watcher.sleepsFor(player) && !tomb.sleepsFor(player), msg("at -" + (threshold - 1) + " neither sleeps on"));
					helper.assertTrue(!watcher.isPreventingPlayerRest(level, player) && !tomb.isPreventingPlayerRest(level, player),
							msg("a dormant sentinel never keeps a player from resting"));
					align(player, threshold);
				})
				.await("the Watcher bows to a Light player" + (peaceful ? "" : " and the Tomb sentinel wakes"), WAKE_CHECK_TICKS,
						() -> watcher.isBowing() && (peaceful || !tomb.isDormant()))
				.then(() -> {
					helper.assertTrue(watcher.isDormant() && watcher.isDormantFor(player), msg("the Watcher stays dormant for its own side"));
					helper.assertTrue(peaceful ? tomb.isDormant() : !tomb.isDormant(),
							msg(peaceful ? "in Peaceful the Tomb sentinel never wakes for a player" : "the Tomb sentinel wakes for a Light player"));
					helper.assertTrue(!tomb.isBowing(), msg("only Watchers bow"));
					align(player, -threshold);
				})
				.await(peaceful ? "the Watcher stops bowing to a Dark player" : "the Watcher wakes for a Dark player", WAKE_CHECK_TICKS,
						() -> peaceful ? !watcher.isBowing() : !watcher.isDormant())
				.then(() -> helper.assertTrue(!watcher.isBowing(), msg("a Watcher no longer bows once the player is not of its side")))
				.finallyDo(() -> {
					M3TestPlayers.remove(player);
					watcher.discard();
					tomb.discard();
				})
				.run();
	}

	/** {@code Sentinels.wakeNear} never wakes a sentinel that is dormant for its cause's side; bound ones and the radius as given. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void wakeNearNeverWakesOwnSide(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean peaceful = peaceful(helper);
		// Far (over 6 blocks) from both groups, so nothing wakes by proximity.
		ServerPlayer light = aligned(helper, new BlockPos(20, 1, 10), 40);
		ServerPlayer dark = aligned(helper, new BlockPos(20, 1, 14), -40);
		List<RuinSentinel> all = new ArrayList<>();

		try {
			// Group A around (6, 1, 2), group B around (6, 1, 21): Watcher, Tomb, bound Watcher, 3 blocks apart.
			RuinSentinel watcherA = sentinel(helper, new BlockPos(3, 1, 2), SentinelVariant.WATCHER, false, null);
			RuinSentinel tombA = sentinel(helper, new BlockPos(6, 1, 2), SentinelVariant.TOMB, false, null);
			RuinSentinel boundA = sentinel(helper, new BlockPos(9, 1, 2), SentinelVariant.WATCHER, true, null);
			RuinSentinel watcherB = sentinel(helper, new BlockPos(3, 1, 21), SentinelVariant.WATCHER, false, null);
			RuinSentinel tombB = sentinel(helper, new BlockPos(6, 1, 21), SentinelVariant.TOMB, false, null);
			RuinSentinel boundB = sentinel(helper, new BlockPos(9, 1, 21), SentinelVariant.TOMB, true, null);
			all.addAll(List.of(watcherA, tombA, boundA, watcherB, tombB, boundB));
			BlockPos centreA = helper.absolutePos(new BlockPos(6, 1, 2));
			BlockPos centreB = helper.absolutePos(new BlockPos(6, 1, 21));

			if (peaceful) {
				helper.assertTrue(Sentinels.wakeNear(level, centreA, 4, light) == 0 && Sentinels.wakeNear(level, centreA, 4, null) == 0,
						msg("in Peaceful wakeNear wakes nothing"));
				helper.assertTrue(all.stream().allMatch(RuinSentinel::isDormant), msg("every sentinel sleeps on in Peaceful"));
				helper.succeed();
				return;
			}

			helper.assertTrue(Sentinels.wakeNear(level, centreA, 0, null) == 0, msg("radius 0 wakes nothing"));
			int woken = Sentinels.wakeNear(level, centreA, 4, light);
			helper.assertTrue(woken == 2 && !tombA.isDormant() && !boundA.isDormant(), msg("a Light cause wakes the Tomb sentinel and the bound Watcher, woke " + woken));
			helper.assertTrue(watcherA.isDormant(), msg("never the Watcher, which is dormant for the Light side"));
			helper.assertTrue(Sentinels.wakeNear(level, centreA, 4, light) == 0, msg("asking again wakes nothing more"));
			helper.assertTrue(Sentinels.wakeNear(level, centreA, 4, null) == 1 && !watcherA.isDormant(), msg("with no cause the Watcher wakes too"));

			helper.assertTrue(Sentinels.wakeNear(level, centreB, 1, dark) == 0 && tombB.isDormant() && watcherB.isDormant() && boundB.isDormant(),
					msg("radius 1 reaches only the Tomb sentinel, which is dormant for the Dark side"));
			woken = Sentinels.wakeNear(level, centreB, 4, dark);
			helper.assertTrue(woken == 2 && !watcherB.isDormant() && !boundB.isDormant() && tombB.isDormant(),
					msg("a Dark cause wakes the Watcher and the bound Tomb sentinel, never the Tomb sentinel; woke " + woken));
			helper.assertTrue(watcherA.action() == RuinSentinel.Action.WAKING && boundB.action() == RuinSentinel.Action.WAKING,
					msg("a woken sentinel plays its wake tell"));
		} finally {
			M3TestPlayers.remove(light);
			M3TestPlayers.remove(dark);
			all.forEach(Entity::discard);
		}

		helper.succeed();
	}

	/**
	 * A bound sentinel sleeps for no side: it is hostile to every alignment and wakes for its own
	 * side's players; a dormant unbound one is not hostile to anyone. In Peaceful an owner-bound
	 * sentinel crumbles when its owner arrives, and an unowned bound one never wakes for a player.
	 */
	@GameTest(structure = ARENA, maxTicks = 100)
	public void boundAlwaysHostile(GameTestHelper helper) {
		boolean peaceful = peaceful(helper);
		ServerPlayer player = aligned(helper, new BlockPos(12, 1, 8), 100);
		// Owned by the player; both within 6 blocks of it (4.5 each).
		RuinSentinel boundWatcher = sentinel(helper, new BlockPos(10, 1, 4), SentinelVariant.WATCHER, true, player.getUUID());
		RuinSentinel boundTomb = sentinel(helper, new BlockPos(14, 1, 4), SentinelVariant.TOMB, true, null);
		RuinSentinel freeWatcher = sentinel(helper, new BlockPos(20, 1, 20), SentinelVariant.WATCHER, false, null);

		new Script(helper)
				.then(() -> {
					helper.assertTrue(!boundWatcher.sleepsFor(player) && !boundWatcher.isDormantFor(player) && freeWatcher.sleepsFor(player),
							msg("a bound Watcher never sleeps for a Light player; an unbound one does"));
					align(player, -100);
					helper.assertTrue(!boundTomb.sleepsFor(player), msg("a bound Tomb sentinel never sleeps for a Dark player"));
					align(player, 100);
					helper.assertTrue(Attitudes.toward(boundWatcher, player) == Attitude.HOSTILE && Attitudes.toward(boundTomb, player) == Attitude.HOSTILE,
							msg("bound sentinels are hostile"));
					helper.assertTrue(Attitudes.countsAsHostile(player, boundWatcher) && Attitudes.countsAsHostile(player, boundTomb),
							msg("and killing one counts as hostile"));
					helper.assertTrue(freeWatcher.isDormant() && Attitudes.toward(freeWatcher, player) == Attitude.NEUTRAL && !Attitudes.countsAsHostile(player, freeWatcher),
							msg("a dormant unbound sentinel is a statue: not hostile, so a pedestal beside it opens"));
				})
				.await(peaceful ? "the owner-bound sentinel crumbles for its owner in Peaceful" : "both bound sentinels wake for a Light player", WAKE_CHECK_TICKS,
						() -> peaceful ? boundWatcher.isRemoved() : !boundWatcher.isDormant() && !boundTomb.isDormant())
				.then(() -> {
					if (peaceful) {
						helper.assertTrue(boundTomb.isDormant() && !boundTomb.isRemoved(), msg("an unowned bound sentinel never wakes for a player in Peaceful"));
					}
				})
				// What its targeting goals follow: awake, past the wake tell, and able to attack the player. getTarget() itself
				// cannot be checked: Mob#setTarget and #getTarget pass through asValidTarget, which drops creative players, and a
				// mock player is always creative.
				.await("the woken Watcher may target the player after its wake tell", peaceful ? 1 : SentinelBalance.get().wakeTicks + 15,
						() -> peaceful || (!boundWatcher.isDormant() && boundWatcher.action() != RuinSentinel.Action.WAKING && boundWatcher.canAttack(player)))
				.finallyDo(() -> {
					M3TestPlayers.remove(player);
					boundWatcher.discard();
					boundTomb.discard();
					freeWatcher.discard();
				})
				.run();
	}

	// ---------------------------------------------------------------- the lance

	/**
	 * The real lance: one heavy SENTINEL bolt of {@code lanceDamage}. A guard outside its parry window
	 * blocks it for {@code heavyBoltGuardDrain} and never sends it back; a parry returns it straight
	 * at the sentinel for {@code lanceReturnDamage}, and the hit staggers the sentinel.
	 */
	@GameTest(structure = ARENA, maxTicks = 120)
	public void lanceReturnedOnlyByParry(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SentinelBalance sb = SentinelBalance.get();
		SaberBalance b = SaberToggle.balance();
		// The sentinel faces the open arena (+z); the guards stand 10 blocks in front, facing it.
		RuinSentinel sentinel = sentinel(helper, new BlockPos(9, 1, 3), SentinelVariant.TOMB, false, null);
		GuardingPlayer open = guard(helper, new BlockPos(9, 1, 13), 180f, -1, Emitter.GUARDIAN, "lance_open");
		GuardingPlayer late = guard(helper, new BlockPos(9, 1, 13), 180f, 20, Emitter.GUARDIAN, "lance_late");
		GuardingPlayer parrier = guard(helper, new BlockPos(9, 1, 13), 180f, 0, Emitter.GUARDIAN, "lance_parry");
		AABB arena = helper.getBounds();
		BlasterBolt[] fired = new BlasterBolt[1];
		float[] healthBefore = new float[1];

		new Script(helper)
				.then(() -> {
					helper.assertTrue(!BossArenas.isInArena(parrier), msg("test setup: no boss arena here"));
					sentinel.beginLance(late);
					helper.assertTrue(sentinel.action() == RuinSentinel.Action.LANCE_TELL && !sentinel.isDormant(), msg("the lance tell starts and wakes the sentinel"));
				})
				.await("the sentinel fires its lance", sb.lanceTellTicks + 10, () -> {
					List<BlasterBolt> bolts = level.getEntitiesOfClass(BlasterBolt.class, arena, bolt -> bolt.getOwner() == sentinel && !bolt.isRemoved());
					fired[0] = bolts.isEmpty() ? null : bolts.get(0);
					return fired[0] != null;
				})
				.then(() -> {
					BlasterBolt bolt = fired[0];
					helper.assertTrue(bolt.heavy() && bolt.style() == BlasterBolt.Style.SENTINEL && near(bolt.damage(), sb.lanceDamage),
							msg("the lance is one heavy SENTINEL bolt of " + sb.lanceDamage + " damage"));
					helper.assertTrue(bolt.getDeltaMovement().normalize().z > 0.9, msg("fired at the guard, in front"));

					helper.assertTrue(CombatHooks.onProjectileHit(open, bolt) == DeflectResult.NONE, msg("without a guard the lance hits"));

					float before = SaberGuard.stamina(late);
					float drain = b.heavyBoltGuardDrain * SaberForm.UNFORMED.modifiers(b).drainMult();
					DeflectResult blocked = CombatHooks.onProjectileHit(late, bolt);
					helper.assertTrue(blocked == DeflectResult.BLOCKED, msg("a late guard blocks the lance, got " + blocked));
					helper.assertTrue(near(SaberGuard.stamina(late), Math.max(0f, before - drain)),
							msg("blocking drains " + drain + ": " + before + " -> " + SaberGuard.stamina(late)));
					helper.assertTrue(!bolt.isDeflected() && !bolt.parried() && bolt.getOwner() == sentinel && near(bolt.damage(), sb.lanceDamage),
							msg("a blocked lance is never sent back"));

					DeflectResult parried = CombatHooks.onProjectileHit(parrier, bolt);
					helper.assertTrue(parried == DeflectResult.PARRIED, msg("a guard inside its parry window parries the lance, got " + parried));
					helper.assertTrue(bolt.parried() && bolt.isDeflected() && bolt.getOwner() == parrier, msg("the returned lance is marked parried and now the parrier's"));
					helper.assertTrue(near(bolt.damage(), sb.lanceReturnDamage), msg("it hits for " + sb.lanceReturnDamage + ", got " + bolt.damage()));
					Vec3 toShooter = sentinel.getEyePosition().subtract(bolt.position());
					helper.assertTrue(degreesBetween(bolt.getDeltaMovement(), toShooter) < 1.0, msg("outside an arena it flies straight back at the sentinel"));
					healthBefore[0] = sentinel.getHealth();
				})
				.await("the returned lance hits the sentinel", 30, () -> sentinel.getHealth() < healthBefore[0])
				.then(() -> {
					helper.assertTrue(sentinel.action() == RuinSentinel.Action.STAGGERED, msg("its own lance parried back staggers it, got " + sentinel.action()));
					helper.assertTrue(fired[0].isRemoved(), msg("the bolt is spent"));
				})
				.finallyDo(() -> {
					SaberGuard.forget(late);
					SaberGuard.forget(parrier);
					SaberGuard.forget(open);
					sentinel.discard();

					if (fired[0] != null && !fired[0].isRemoved()) {
						fired[0].discard();
					}
				})
				.run();
	}

	/**
	 * Inside a boss arena a parried lance goes toward the crosshair (here a pig standing in for a
	 * pylon focus, 27 degrees off the sentinel), at most 12 degrees off it; outside, straight back at
	 * the sentinel. The Reliquary emitter adds 15% to the returned lance.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void parriedLanceAimsAtCrosshairInArena(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SentinelBalance sb = SentinelBalance.get();
		SaberBalance b = SaberToggle.balance();
		RuinSentinel sentinel = sentinel(helper, new BlockPos(12, 1, 2), SentinelVariant.WATCHER, false, null);
		Mob focus = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(9, 1, 2));
		GuardingPlayer parrier = guard(helper, new BlockPos(12, 1, 8), 180f, 0, Emitter.GUARDIAN, "crosshair_parry");
		GuardingPlayer reliquary = guard(helper, new BlockPos(12, 1, 8), 180f, 0, Emitter.RELIQUARY, "crosshair_reliquary");
		Vec3 focusCentre = focus.getBoundingBox().getCenter();
		lookAt(parrier, focusCentre);
		lookAt(reliquary, focusCentre);
		BlockPos heart = helper.absolutePos(new BlockPos(12, 2, 12));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(23, 6, 23)));

		try {
			helper.assertTrue(degreesBetween(parrier.getLookAngle(), sentinel.getEyePosition().subtract(parrier.getEyePosition())) > b.reliquaryDeflectDegrees + 5,
					msg("test setup: the sentinel is well off the crosshair"));

			// Outside an arena: straight back at the shooter.
			helper.assertTrue(!BossArenas.isInArena(parrier), msg("test setup: no arena tracked yet"));
			BlasterBolt outside = lance(level, sentinel, parrier);
			helper.assertTrue(CombatHooks.onProjectileHit(parrier, outside) == DeflectResult.PARRIED, msg("the lance is parried"));
			helper.assertTrue(degreesBetween(outside.getDeltaMovement(), sentinel.getEyePosition().subtract(outside.position())) < 1.0,
					msg("outside an arena the parried lance flies back at the sentinel"));

			BossArenas.trackArena(level, heart, box);
			helper.assertTrue(BossArenas.isInArena(parrier), msg("the parrier now stands in a tracked arena"));
			BlasterBolt inside = lance(level, sentinel, parrier);
			helper.assertTrue(CombatHooks.onProjectileHit(parrier, inside) == DeflectResult.PARRIED && inside.parried(), msg("the lance is parried in the arena"));
			Vec3 out = inside.getDeltaMovement();
			helper.assertTrue(degreesBetween(out, focusCentre.subtract(inside.position())) < 1.0, msg("it flies at the entity on the crosshair"));
			helper.assertTrue(degreesBetween(out, parrier.getLookAngle()) <= b.reliquaryDeflectDegrees + 1.0,
					msg("within " + b.reliquaryDeflectDegrees + " degrees of the crosshair, got " + degreesBetween(out, parrier.getLookAngle())));
			helper.assertTrue(degreesBetween(out, sentinel.getEyePosition().subtract(inside.position())) > b.reliquaryDeflectDegrees,
					msg("and not back at the sentinel"));
			helper.assertTrue(near(inside.damage(), sb.lanceReturnDamage), msg("a returned lance hits for " + sb.lanceReturnDamage));

			BlasterBolt bonus = lance(level, sentinel, reliquary);
			helper.assertTrue(CombatHooks.onProjectileHit(reliquary, bonus) == DeflectResult.PARRIED, msg("the Reliquary saber parries the lance"));
			helper.assertTrue(near(bonus.damage(), sb.lanceReturnDamage * (1f + b.reliquaryParryBonus)),
					msg("the Reliquary emitter adds " + b.reliquaryParryBonus * 100 + "%: " + sb.lanceReturnDamage * (1f + b.reliquaryParryBonus) + ", got " + bonus.damage()));
			helper.assertTrue(degreesBetween(bonus.getDeltaMovement(), focusCentre.subtract(bonus.position())) < 1.0, msg("and aims at the crosshair too"));
		} finally {
			BossArenas.untrackArena(level, heart);
			SaberGuard.forget(parrier);
			SaberGuard.forget(reliquary);
			sentinel.discard();
		}

		helper.assertTrue(!BossArenas.isInArena(parrier), msg("the arena is untracked again"));
		helper.succeed();
	}
}
