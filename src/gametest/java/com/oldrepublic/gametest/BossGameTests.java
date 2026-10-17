package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.ArchiveDebris;
import com.oldrepublic.boss.ArenaLayout;
import com.oldrepublic.boss.ArenaSession;
import com.oldrepublic.boss.BossArenas;
import com.oldrepublic.boss.BossBlocks;
import com.oldrepublic.boss.CollectorBalance;
import com.oldrepublic.boss.CollectorCombatGoal;
import com.oldrepublic.boss.CollectorCombatGoal.Move;
import com.oldrepublic.boss.CollectorHooks;
import com.oldrepublic.boss.CollectorRules;
import com.oldrepublic.boss.HeartRules;
import com.oldrepublic.boss.HeartRules.Event;
import com.oldrepublic.boss.HeartState;
import com.oldrepublic.boss.OrrenVasch;
import com.oldrepublic.boss.PylonFocus;
import com.oldrepublic.boss.ReliquarySites;
import com.oldrepublic.boss.block.PylonCrystal;
import com.oldrepublic.boss.block.ReliquaryHeartBlockEntity;
import com.oldrepublic.boss.block.ResonancePylonBlock;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.PushBranch;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3 boss fight (W5 part A, spec 17.1): the heart's state machine and saved state, the
 * reset, health scaling and late joiners, the damage gates, the leash, the phase thresholds, the
 * 8.4 interrupt causes, every pylon source, the re-sing rule, the Unsung window, Surge cover, the
 * Grip, Lift while Spent, Peaceful, the owner's resolve and the rematch hooks, and Unchained.
 *
 * <p>Fight tests run a real {@link ArenaSession} on the lead's {@code arena_24x7x24} fixture through
 * {@link BossArenas#openSession} (no heart block entity needed; some tests place one to check the
 * write-through). The fixture cells used (relative): the floor is y = 0; the dais anchor (12, 2, 12);
 * the four pylon anchors (5, 2, 5), (18, 2, 5), (18, 2, 18), (5, 2, 18); the columns (8, 1..6, 16) and
 * (16, 1..6, 16). The test layout puts the threshold at (12, 1, 4), so the hall is z &gt; 5, the
 * antechamber z &lt; 4 and the reset's antechamber floor (12, 1, 1); the lectern stands at (12, 1, 16)
 * and the heart key at (1, 1, 1). No sentinel alcoves and no debris piles: phase 2 spawns no
 * sentinels and the Hurl takes its chunk from the floor beside him.
 *
 * <p>Each session reports to a {@link CollectorHooks.Recorder}, never to {@code QuestHooks}, so no
 * test touches anyone's story. Sessions are told the difficulty with
 * {@link ArenaSession#overrideDifficulty} (Normal, or Peaceful for that test) and never change the
 * world's. Vasch's own AI is switched off where his moves would race the test ({@code setNoAi}); the
 * session still drives his phases and the moves a test starts with {@link CollectorCombatGoal#forceMove}.
 * Hits on him are non-melee magic hits from a listed player (no guard, no armour), with his
 * invulnerability ticks cleared before each, so a hit's effect is exactly the 8.3 multipliers. Mock
 * players cannot be hurt, so the Surge is checked through its server rays
 * ({@link CollectorCombatGoal#surgeExposure}). Every test closes its session and removes its players,
 * on failure too. {@code reliquaryEvaluateRejects} (W5 part B) checks {@link ReliquarySites#evaluate}'s
 * refusal reasons on the same fixture, without a session.
 *
 * <p>Test rules (15.1): {@code maxTicks} at most 400, the lead's fixtures only, listed mock players
 * ({@link M3TestPlayers}), no randomness, no global state.
 */
public class BossGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	private static final float EPSILON = 1.0e-3f;

	private static final BlockPos HEART = new BlockPos(1, 1, 1);
	private static final BlockPos THRESHOLD = new BlockPos(12, 1, 4);
	private static final BlockPos DAIS = new BlockPos(12, 2, 12);
	private static final BlockPos LECTERN = new BlockPos(12, 1, 16);
	private static final BlockPos FAR_CORNER = new BlockPos(23, 6, 23);
	private static final List<BlockPos> PYLONS = List.of(new BlockPos(5, 2, 5), new BlockPos(18, 2, 5), new BlockPos(18, 2, 18), new BlockPos(5, 2, 18));
	private static final List<BlockPos> COLUMNS = List.of(new BlockPos(8, 1, 16), new BlockPos(16, 1, 16));

	/** Standing places: three in the hall near the dais, one in the antechamber. */
	private static final BlockPos HALL_A = new BlockPos(12, 1, 9);
	private static final BlockPos HALL_B = new BlockPos(14, 1, 9);
	private static final BlockPos HALL_C = new BlockPos(10, 1, 9);
	private static final BlockPos ANTECHAMBER = new BlockPos(12, 1, 2);

	/** A transition (60 ticks) is over by then, with a margin. */
	private static final int AFTER_TRANSITION = 64;
	/** His Peaceful line before he kneels. */
	private static final int PEACEFUL_TALK = ArenaSession.PEACEFUL_TALK_TICKS;
	/** A hit far past any phase floor. */
	private static final float HUGE = 100000f;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) <= EPSILON;
	}

	// ---------------------------------------------------------------- fixtures

	/** One running fight on the fixture: its session, its recorder and the players to remove. */
	private static final class Fight {
		final GameTestHelper helper;
		final ServerLevel level;
		final ArenaSession session;
		final CollectorHooks.Recorder hooks;
		final List<ServerPlayer> players;
		/** The first failed step, if any. */
		@Nullable Throwable failure;

		Fight(GameTestHelper helper, ArenaSession session, CollectorHooks.Recorder hooks, List<ServerPlayer> players) {
			this.helper = helper;
			this.level = helper.getLevel();
			this.session = session;
			this.hooks = hooks;
			this.players = players;
		}

		/** The session's Vasch (asserted present). */
		OrrenVasch vasch() {
			OrrenVasch vasch = session.vasch();
			helper.assertTrue(vasch != null && !vasch.isRemoved(), msg("the session has its Vasch"));
			return vasch;
		}

		CollectorCombatGoal combat() {
			CollectorCombatGoal combat = vasch().combat();
			helper.assertTrue(combat != null, msg("Vasch has his combat goal"));
			return combat;
		}

		/**
		 * Runs {@code step} now; on a failed assertion the fight is closed first. Once a step has
		 * failed, later steps rethrow that failure, so a step scheduled after it cannot replace it
		 * with one about the closed fight.
		 */
		void now(Runnable step) {
			if (failure instanceof RuntimeException earlier) {
				throw earlier;
			} else if (failure instanceof Error earlier) {
				throw earlier;
			}

			try {
				step.run();
			} catch (RuntimeException | Error e) {
				failure = e;
				close();
				throw e;
			}
		}

		/** Runs {@code step} after {@code delay} ticks, closing the fight if it fails. */
		void at(long delay, Runnable step) {
			helper.runAfterDelay(delay, () -> now(step));
		}

		void close() {
			BossArenas.closeSession(level, helper.absolutePos(HEART));

			for (ServerPlayer player : players) {
				M3TestPlayers.remove(player);
			}
		}

		void done() {
			close();
			helper.succeed();
		}
	}

	/** The layout of the fixture in world space (see the class comment). */
	private static ArenaLayout layout(GameTestHelper helper) {
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(FAR_CORNER));
		List<BlockPos> pylons = PYLONS.stream().map(helper::absolutePos).toList();
		List<BlockPos> columns = COLUMNS.stream().map(helper::absolutePos).toList();
		return ArenaLayout.of(box, helper.absolutePos(HEART), helper.absolutePos(THRESHOLD), helper.absolutePos(DAIS), helper.absolutePos(LECTERN), pylons,
				List.of(), List.of(), columns);
	}

	/**
	 * A session on the fixture owned by {@code owner}, played at Normal difficulty: Vasch at the
	 * lectern, or the forming echo. {@code players} are removed when the fight closes.
	 */
	private static Fight fight(GameTestHelper helper, ServerPlayer owner, boolean echo, boolean unchained, ServerPlayer... players) {
		CollectorHooks.Recorder hooks = new CollectorHooks.Recorder();
		List<ServerPlayer> all = new ArrayList<>(List.of(players));

		if (!all.contains(owner)) {
			all.add(owner);
		}

		ArenaSession session;

		try {
			session = BossArenas.openSession(helper.getLevel(), helper.absolutePos(HEART), layout(helper), owner.getUUID(), hooks, echo, unchained);
			session.overrideDifficulty(Difficulty.NORMAL);
		} catch (RuntimeException | Error e) {
			BossArenas.closeSession(helper.getLevel(), helper.absolutePos(HEART));
			all.forEach(M3TestPlayers::remove);
			throw e;
		}

		Fight fight = new Fight(helper, session, hooks, all);
		fight.now(() -> helper.assertTrue(session.vasch() != null, msg("openSession spawns Vasch")));
		return fight;
	}

	/** A listed player at {@code relative}, awakened (Chapter One's FOCUS stage), standing on the floor. */
	private static ServerPlayer awakened(GameTestHelper helper, BlockPos relative) {
		ServerPlayer player = plain(helper, relative);
		ForceData data = Progression.get(player);
		data.questStage = QuestStage.FOCUS.ordinal();
		Progression.commit(player, data);
		return player;
	}

	/** A listed player at {@code relative} who has not awakened, standing on the floor. */
	private static ServerPlayer plain(GameTestHelper helper, BlockPos relative) {
		ServerPlayer player = M3TestPlayers.listed(helper, relative);
		player.setOnGround(true);
		return player;
	}

	private static void moveTo(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(relative));
		player.snapTo(feet.x, feet.y, feet.z);
		player.setOnGround(true);
	}

	/** The heart block with its entity at {@link #HEART}, owned by {@code owner}, in {@code state}. */
	private static ReliquaryHeartBlockEntity heart(GameTestHelper helper, ServerPlayer owner, HeartState state) {
		helper.setBlock(HEART, BossBlocks.RELIQUARY_HEART);
		ReliquaryHeartBlockEntity heart = helper.getLevel().getBlockEntity(helper.absolutePos(HEART)) instanceof ReliquaryHeartBlockEntity entity ? entity : null;
		helper.assertTrue(heart != null, msg("the heart block has its block entity"));
		heart.setOwner(owner.getUUID());
		heart.setState(state);
		return heart;
	}

	/** Resonance pylons on the four stand-ins, so the crystal states show on the blocks. */
	private static void placePylons(GameTestHelper helper) {
		for (BlockPos pylon : PYLONS) {
			helper.setBlock(pylon, BossBlocks.RESONANCE_PYLON);
		}
	}

	/** Why a pylon focus may refuse a caster: the session's state, the focus and the line from the caster's eyes to the crystal. */
	private static String reach(Fight f, ServerPlayer caster, @Nullable PylonFocus focus, int index) {
		String state = "stage " + f.session.stage() + ", phase " + f.session.phase() + ", crystal " + f.session.pylonCrystal(index);

		if (focus == null) {
			return state + ", no focus";
		}

		Vec3 eye = caster.getEyePosition();
		Vec3 centre = focus.crystalCentre();
		BlockHitResult hit = f.level.clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		return String.format(Locale.ROOT, "%s, focus removed %s, eye %.2f %.2f %.2f, crystal %.2f %.2f %.2f, %.2f blocks, clip %s at %s (%s)", state, focus.isRemoved(),
				eye.x, eye.y, eye.z, centre.x, centre.y, centre.z, eye.distanceTo(centre), hit.getType(), hit.getBlockPos().toShortString(),
				f.level.getBlockState(hit.getBlockPos()).getBlock());
	}

	private static PylonCrystal blockCrystal(GameTestHelper helper, int index) {
		BlockState state = helper.getLevel().getBlockState(helper.absolutePos(PYLONS.get(index)));
		return state.getBlock() instanceof ResonancePylonBlock ? state.getValue(ResonancePylonBlock.CRYSTAL) : PylonCrystal.DORMANT;
	}

	/** A non-melee hit of {@code amount} on Vasch from {@code attacker}; returns the health it took (0 when refused). */
	private static float hit(OrrenVasch vasch, ServerPlayer attacker, float amount) {
		ServerLevel level = (ServerLevel) vasch.level();
		vasch.invulnerableTime = 0;
		float before = vasch.getHealth();
		vasch.hurtServer(level, level.damageSources().source(DamageTypes.MAGIC, null, attacker), amount);
		return before - vasch.getHealth();
	}

	private static List<Integer> phases(CollectorHooks.Recorder hooks, UUID player) {
		return hooks.calls(CollectorHooks.Kind.PHASE).stream().filter(c -> c.player().equals(player)).map(CollectorHooks.Call::phase).toList();
	}

	private static ItemStack litSaber(ServerPlayer player) {
		ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(player.getUUID(), "Tester", BladeColor.BLUE.rgb()), BladeColor.BLUE, Emitter.GUARDIAN,
				Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
		SaberToggle.setIgnited(saber, true, player.level().getGameTime());
		return saber;
	}

	private static ReliquaryHeartBlockEntity roundTrip(ServerLevel level, ReliquaryHeartBlockEntity heart) {
		CompoundTag tag = heart.saveCustomOnly(level.registryAccess());
		ReliquaryHeartBlockEntity copy = new ReliquaryHeartBlockEntity(heart.getBlockPos(), heart.getBlockState());
		copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
		return copy;
	}

	// ---------------------------------------------------------------- the heart

	/** Every state x event of 8.2 (anything not listed leaves the state as it is) and the heart's timers at their edges. */
	@GameTest(maxTicks = 20)
	public void heartStateMachine(GameTestHelper helper) {
		record Edge(HeartState from, Event event, HeartState to) {
		}

		List<Edge> edges = List.of(
				new Edge(HeartState.DORMANT, Event.OWNER_NEAR, HeartState.READY),
				new Edge(HeartState.READY, Event.OWNER_AWAY, HeartState.DORMANT),
				new Edge(HeartState.READY, Event.FIGHT_START, HeartState.ACTIVE),
				// Peaceful: he talks, then kneels, without a fight.
				new Edge(HeartState.READY, Event.YIELD, HeartState.YIELDED),
				new Edge(HeartState.ACTIVE, Event.RESET, HeartState.READY),
				new Edge(HeartState.ACTIVE, Event.YIELD, HeartState.YIELDED),
				new Edge(HeartState.YIELDED, Event.RESOLVE, HeartState.RESOLVED));

		for (HeartState state : HeartState.values()) {
			for (Event event : Event.values()) {
				HeartState expected = state;

				for (Edge edge : edges) {
					if (edge.from() == state && edge.event() == event) {
						expected = edge.to();
					}
				}

				HeartState actual = HeartRules.step(state, event);
				helper.assertTrue(actual == expected, msg(state + " + " + event + " must give " + expected + ", got " + actual));
			}
		}

		helper.assertTrue(HeartState.YIELDED.persists() && HeartState.RESOLVED.persists() && !HeartState.ACTIVE.persists() && !HeartState.READY.persists(),
				msg("only YIELDED and RESOLVED persist"));
		helper.assertTrue(HeartState.of(-1) == HeartState.DORMANT && HeartState.of(99) == HeartState.DORMANT, msg("an unknown saved state loads as DORMANT"));

		CollectorBalance b = CollectorBalance.get();
		double ready = (double) b.readyRange * b.readyRange;
		helper.assertTrue(HeartRules.ownerNear(ready, b) && !HeartRules.ownerNear(ready + 0.01, b), msg("the owner keeps the heart READY within readyRange"));
		helper.assertTrue(!HeartRules.sealCloses(b.sealCloseTicks - 1, false, b) && HeartRules.sealCloses(b.sealCloseTicks, false, b)
				&& !HeartRules.sealCloses(b.sealCloseTicks * 4L, true, b), msg("the seal closes sealCloseTicks after the crossing, never on someone in the doorway"));
		helper.assertTrue(!HeartRules.meetingTimedOut(b.meetDialogueTicks - 1, b) && HeartRules.meetingTimedOut(b.meetDialogueTicks, b),
				msg("the meeting gives way to the fight after meetDialogueTicks"));
		helper.assertTrue(!HeartRules.resetDue(b.resetTicks - 1, b) && HeartRules.resetDue(b.resetTicks, b), msg("the fight resets after resetTicks with nobody inside"));
		helper.assertTrue(!HeartRules.participantDropped(b.participantLeaveTicks - 1, b) && HeartRules.participantDropped(b.participantLeaveTicks, b),
				msg("a participant away for participantLeaveTicks leaves the fight"));
		double dais = (double) b.daisStartRange * b.daisStartRange;
		helper.assertTrue(HeartRules.daisStart(dais, b) && !HeartRules.daisStart(dais + 0.01, b), msg("a later attempt starts within daisStartRange of the dais"));
		helper.succeed();
	}

	/** The heart's saved state: YIELDED with its attempts and notice, RESOLVED with its clears, ACTIVE coming back READY. */
	@GameTest(maxTicks = 20)
	public void heartNbtRoundTripYieldedResolvedAttempts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState block = BossBlocks.RELIQUARY_HEART.defaultBlockState();
		UUID owner = new UUID(0x0B055EEDL, 0xC0113C70L);
		BlockPos origin = new BlockPos(64, -40, 32);
		BoundingBox box = new BoundingBox(20, -40, 32, 64, -20, 76);

		ReliquaryHeartBlockEntity yielded = new ReliquaryHeartBlockEntity(new BlockPos(40, -38, 40), block);
		yielded.setOwner(owner);
		yielded.setHall(new SitePiece(SiteKind.RELIQUARY, ReliquarySites.HALL, box, Rotation.CLOCKWISE_90, origin, SitePiece.runtimeKey(level.dimension(), origin)));
		yielded.setState(HeartState.YIELDED);
		yielded.addAttempt();
		yielded.addAttempt();
		yielded.addAttempt();
		yielded.setYieldNotified(true);
		ReliquaryHeartBlockEntity a = roundTrip(level, yielded);
		helper.assertTrue(owner.equals(a.owner()), msg("the owner survives a save"));
		helper.assertTrue(a.state() == HeartState.YIELDED, msg("YIELDED survives a save, got " + a.state()));
		helper.assertTrue(a.attempts() == 3, msg("the attempts survive a save, got " + a.attempts()));
		helper.assertTrue(a.yieldNotified() && !a.firstClear() && a.echoClears() == 0 && !a.unchainedUnlocked(), msg("the yield notice survives; nothing is cleared yet"));
		BoundingBox loadedBox = a.hallBox();
		helper.assertTrue(loadedBox != null && loadedBox.minX() == box.minX() && loadedBox.minY() == box.minY() && loadedBox.minZ() == box.minZ()
				&& loadedBox.maxX() == box.maxX() && loadedBox.maxY() == box.maxY() && loadedBox.maxZ() == box.maxZ(), msg("the hall box survives a save"));

		ReliquaryHeartBlockEntity resolved = new ReliquaryHeartBlockEntity(new BlockPos(40, -38, 40), block);
		resolved.setOwner(owner);
		resolved.setState(HeartState.RESOLVED);
		resolved.addAttempt();
		resolved.setFirstClear(true);
		resolved.addEchoClear();
		resolved.addEchoClear();
		ReliquaryHeartBlockEntity r = roundTrip(level, resolved);
		helper.assertTrue(r.state() == HeartState.RESOLVED && r.firstClear() && r.attempts() == 1, msg("RESOLVED, the first clear and the attempts survive a save"));
		helper.assertTrue(r.echoClears() == 2 && r.unchainedUnlocked() && !r.yieldNotified(), msg("the echo clears and Unchained survive a save"));

		ReliquaryHeartBlockEntity active = new ReliquaryHeartBlockEntity(new BlockPos(40, -38, 40), block);
		active.setOwner(owner);
		active.setState(HeartState.ACTIVE);
		active.addAttempt();
		ReliquaryHeartBlockEntity act = roundTrip(level, active);
		helper.assertTrue(act.state() == HeartState.READY && act.attempts() == 1, msg("a fight in progress is not restored: ACTIVE loads READY, got " + act.state()));

		ReliquaryHeartBlockEntity fresh = roundTrip(level, new ReliquaryHeartBlockEntity(new BlockPos(40, -38, 40), block));
		helper.assertTrue(fresh.state() == HeartState.DORMANT && fresh.owner() == null && fresh.attempts() == 0 && fresh.hallBox() == null,
				msg("a fresh heart saves and loads DORMANT, unowned"));
		helper.succeed();
	}

	/** Nobody alive in the hall for resetTicks: he goes back to the lectern, items move to the antechamber, the next attempt starts at the dais. */
	@GameTest(structure = ARENA, maxTicks = 260)
	public void resetAfter200TicksMovesItemsToAntechamber(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		ReliquaryHeartBlockEntity heart = heart(helper, a, HeartState.READY);
		Fight f = fight(helper, a, false, false, a);
		CollectorBalance b = CollectorBalance.get();
		ArenaLayout layout = f.session.layout();
		ItemEntity[] item = new ItemEntity[1];

		f.now(() -> {
			f.session.startFight(null);
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.FIGHTING, msg("the fight starts"));
			helper.assertTrue(heart.state() == HeartState.ACTIVE && heart.attempts() == 1, msg("the heart is ACTIVE with one attempt, got " + heart.state()));

			Vec3 drop = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(16, 1, 8)));
			item[0] = new ItemEntity(f.level, drop.x, drop.y, drop.z, new ItemStack(Items.WRITABLE_BOOK));
			item[0].setPickUpDelay(1200);
			item[0].setDeltaMovement(Vec3.ZERO);
			f.level.addFreshEntity(item[0]);
			helper.assertTrue(layout.isInHall(item[0].position()), msg("the dropped item lies in the hall"));

			// Out of the hall but still in the Reliquary: listed, not away, but nobody is inside.
			moveTo(helper, a, ANTECHAMBER);
		});

		f.at(b.resetTicks - 50, () -> {
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.FIGHTING, msg("no reset before resetTicks, got " + f.session.stage()));
			helper.assertTrue(layout.isInHall(item[0].position()), msg("the item stays in the hall until the reset"));
			helper.assertTrue(f.session.isParticipant(a.getUUID()), msg("a participant in the antechamber stays listed"));
		});

		f.at(b.resetTicks + 15, () -> {
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.WAITING, msg("the fight reset after resetTicks, got " + f.session.stage()));
			helper.assertTrue(heart.state() == HeartState.READY && f.session.heartState() == HeartState.READY, msg("the heart is READY again, got " + heart.state()));
			helper.assertTrue(f.session.participants().isEmpty() && f.session.attempts() == 1, msg("the roster is cleared and the attempt counted"));
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(vasch.position().distanceTo(layout.lecternFeet()) < 1.0 && vasch.getHealth() == vasch.getMaxHealth(),
					msg("he is back at the lectern at full health"));
			Vec3 floor = Vec3.atBottomCenterOf(layout.antechamberFloor());
			Vec3 at = item[0].position();
			helper.assertTrue(!item[0].isRemoved() && layout.isInAntechamber(at) && Math.hypot(at.x - floor.x, at.z - floor.z) < 0.75,
					msg("the item moved to the antechamber floor, got " + at));
			helper.assertTrue(item[0].hasPickUpDelay(), msg("the moved item keeps its pickup rules"));

			// A later attempt: no meeting, it starts as soon as someone comes within daisStartRange of the dais.
			moveTo(helper, a, HALL_A);
		});

		f.at(b.resetTicks + 20, () -> {
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.FIGHTING, msg("the next attempt starts at the dais, got " + f.session.stage()));
			helper.assertTrue(f.session.attempts() == 2 && heart.attempts() == 2 && heart.state() == HeartState.ACTIVE, msg("the second attempt is counted"));
			f.done();
		});
	}

	// ---------------------------------------------------------------- participants and the damage gates

	/** 450 x (1 + 0.6 x (n - 1)) capped at four; two participants also scale the pylons. */
	@GameTest(structure = ARENA, maxTicks = 100)
	public void healthScalesWithTwoParticipants(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		ServerPlayer c = awakened(helper, HALL_B);
		Fight f = fight(helper, a, false, false, a, c);
		CollectorBalance b = CollectorBalance.get();

		f.now(() -> {
			helper.assertTrue(near(CollectorRules.maxHealth(1, false, b), b.maxHealth), msg("one participant: maxHealth"));
			helper.assertTrue(near(CollectorRules.maxHealth(2, false, b), b.maxHealth * (1 + b.perParticipant)), msg("two participants: x(1 + perParticipant)"));
			helper.assertTrue(near(CollectorRules.maxHealth(4, false, b), b.maxHealth * (1 + 3 * b.perParticipant)), msg("four participants: x(1 + 3 perParticipant)"));
			helper.assertTrue(near(CollectorRules.maxHealth(7, false, b), CollectorRules.maxHealth(b.scalingCap, false, b))
					&& near(CollectorRules.maxHealth(0, false, b), b.maxHealth), msg("the count is clamped to 1..scalingCap"));
			helper.assertTrue(near(CollectorRules.pylonResonance(2, b), b.pylonResonance * (1 + b.pylonScale)), msg("pylon resonance scales by pylonScale"));
			helper.assertTrue(CollectorRules.sentinelCount(1, b) == b.sentinels && CollectorRules.sentinelCount(2, b) == b.sentinels + 1
					&& CollectorRules.sentinelCount(9, b) == b.sentinelsMax, msg("sentinels: 2, +1 per extra participant, at most sentinelsMax"));

			OrrenVasch vasch = f.vasch();
			vasch.setNoAi(true);
			f.session.startFight(null);
			float expected = CollectorRules.maxHealth(2, false, b);
			helper.assertTrue(f.session.participants().size() == 2, msg("both players in the hall are listed, got " + f.session.participants().size()));
			helper.assertTrue(near(vasch.getMaxHealth(), expected) && near(vasch.getHealth(), expected), msg("his health is " + expected + ", got " + vasch.getMaxHealth()));
			helper.assertTrue(phases(f.hooks, a.getUUID()).equals(List.of(1)) && phases(f.hooks, c.getUUID()).equals(List.of(1)),
					msg("onBossPhase(1) reaches each participant once"));
			helper.assertTrue(f.session.forcePhase(2), msg("phase 2 can be forced"));
		});

		f.at(AFTER_TRANSITION, () -> {
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.FIGHTING && f.session.phase() == 2, msg("phase 2 runs after its transition"));
			helper.assertTrue(f.session.singingCount() == 4, msg("all four pylons sing, got " + f.session.singingCount()));

			for (int i = 0; i < PYLONS.size(); i++) {
				helper.assertTrue(near(f.session.pylonResonance(i), CollectorRules.pylonResonance(2, b)),
						msg("pylon " + i + " holds the two-participant resonance, got " + f.session.pylonResonance(i)));
			}

			f.done();
		});
	}

	/** A player who joins in phase 1 raises his maximum and keeps his fraction; the roster closes when phase 1 ends. */
	@GameTest(structure = ARENA, maxTicks = 100)
	public void midPhaseOneJoinKeepsFraction(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		ServerPlayer late = awakened(helper, ANTECHAMBER);
		ServerPlayer later = awakened(helper, new BlockPos(14, 1, 2));
		Fight f = fight(helper, a, false, false, a, late, later);
		CollectorBalance b = CollectorBalance.get();

		f.now(() -> {
			OrrenVasch vasch = f.vasch();
			vasch.setNoAi(true);
			f.session.startFight(null);
			helper.assertTrue(f.session.participants().equals(List.of(a.getUUID())), msg("only the player in the hall is listed"));
			float tenth = vasch.getMaxHealth() * 0.1f;
			helper.assertTrue(near(hit(vasch, a, tenth), tenth), msg("a participant's hit lands in full"));
			helper.assertTrue(near(vasch.getHealth() / vasch.getMaxHealth(), 0.9f), msg("he is at 90%"));
			moveTo(helper, late, HALL_B);
		});

		f.at(2, () -> {
			OrrenVasch vasch = f.vasch();
			float max = CollectorRules.maxHealth(2, false, b);
			helper.assertTrue(f.session.isParticipant(late.getUUID()), msg("a player who walks in during phase 1 joins"));
			helper.assertTrue(near(vasch.getMaxHealth(), max), msg("his maximum rises to " + max + ", got " + vasch.getMaxHealth()));
			helper.assertTrue(Math.abs(vasch.getHealth() - 0.9f * max) < 0.01f, msg("his health keeps its 90%, got " + vasch.getHealth()));
			helper.assertTrue(phases(f.hooks, late.getUUID()).equals(List.of(1)), msg("the joiner gets onBossPhase(1)"));
			helper.assertTrue(f.session.forcePhase(2), msg("phase 2 can be forced"));
			moveTo(helper, later, HALL_C);
		});

		f.at(6, () -> helper.assertTrue(!f.session.isParticipant(later.getUUID()), msg("nobody joins during the first transition")));

		f.at(AFTER_TRANSITION + 2, () -> {
			helper.assertTrue(f.session.phase() == 2 && !f.session.isParticipant(later.getUUID()), msg("nobody joins in phase 2"));
			helper.assertTrue(near(f.vasch().getMaxHealth(), CollectorRules.maxHealth(2, false, b)), msg("his maximum stays at two participants"));
			f.done();
		});
	}

	/** Only a listed participant standing in the hall box can hurt him, and only while the fight runs. */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void nonParticipantDamageRefused(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		ServerPlayer sleeper = plain(helper, HALL_B);
		ServerPlayer outside = awakened(helper, ANTECHAMBER);
		Fight f = fight(helper, a, false, false, a, sleeper, outside);

		f.now(() -> {
			OrrenVasch vasch = f.vasch();
			vasch.setNoAi(true);
			helper.assertTrue(hit(vasch, a, 10f) == 0f, msg("no hit lands before the fight starts"));
			f.session.startFight(null);
			helper.assertTrue(f.session.participants().equals(List.of(a.getUUID())), msg("only the awakened player in the hall is listed"));
			helper.assertTrue(hit(vasch, sleeper, 10f) == 0f, msg("a player who has not awakened cannot hurt him"));
			helper.assertTrue(hit(vasch, outside, 10f) == 0f, msg("an unlisted player in the antechamber cannot hurt him"));
			vasch.invulnerableTime = 0;
			float before = vasch.getHealth();
			vasch.hurtServer(f.level, f.level.damageSources().magic(), 10f);
			helper.assertTrue(vasch.getHealth() == before, msg("a hit with no player behind it is refused"));
			moveTo(helper, a, ANTECHAMBER);
			helper.assertTrue(hit(vasch, a, 10f) == 0f, msg("a participant outside the hall box cannot hurt him"));
			moveTo(helper, a, HALL_A);
			helper.assertTrue(near(hit(vasch, a, 10f), 10f), msg("a participant in the hall lands the hit"));
			moveTo(helper, outside, HALL_C);
		});

		f.at(2, () -> {
			helper.assertTrue(f.session.isParticipant(outside.getUUID()), msg("walking into the hall in phase 1 lists the player"));
			helper.assertTrue(near(hit(f.vasch(), outside, 10f), 10f), msg("once listed and in the hall, the hit lands"));
			f.done();
		});
	}

	/** He cannot leave the hall box: out of it, smoke, and he is back on the dais. */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void leashReturnsToDais(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		Fight f = fight(helper, a, false, false, a);
		Vec3[] corner = new Vec3[1];

		f.now(() -> {
			OrrenVasch vasch = f.vasch();
			vasch.setNoAi(true);
			f.session.startFight(null);
			Vec3 out = Vec3.atBottomCenterOf(helper.absolutePos(ANTECHAMBER));
			vasch.teleportTo(out.x, out.y, out.z);
			helper.assertTrue(!f.session.layout().isInHall(vasch), msg("he stands outside the hall box"));
		});

		f.at(2, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(vasch.position().distanceTo(f.session.layout().daisFeet()) < 0.5, msg("the leash put him back on the dais, got " + vasch.position()));
			helper.assertTrue(f.session.isFighting(), msg("the fight goes on"));
			corner[0] = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(21, 1, 21)));
			vasch.teleportTo(corner[0].x, corner[0].y, corner[0].z);
		});

		f.at(4, () -> {
			helper.assertTrue(f.vasch().position().distanceTo(corner[0]) < 0.5, msg("anywhere inside the hall box is allowed"));
			f.done();
		});
	}

	// ---------------------------------------------------------------- phases and interrupts

	/** 60% and 30% start the transitions (no damage lands during them), 10% the yield; the hooks follow. */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void phaseThresholds(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		Fight f = fight(helper, a, false, false, a);
		CollectorBalance b = CollectorBalance.get();

		f.now(() -> {
			helper.assertTrue(CollectorRules.phaseFor(0.61f, false, b) == 1 && CollectorRules.phaseFor(b.phase2At, false, b) == 2
					&& CollectorRules.phaseFor(0.31f, false, b) == 2 && CollectorRules.phaseFor(b.phase3At, false, b) == 3, msg("phases by health fraction"));
			helper.assertTrue(near(CollectorRules.floorFraction(1, false, false, b), b.phase2At) && near(CollectorRules.floorFraction(2, false, false, b), b.phase3At)
					&& near(CollectorRules.floorFraction(3, false, false, b), b.yieldAt) && CollectorRules.floorFraction(3, true, false, b) == 0f,
					msg("floors: 60%, 30%, then 10% (an echo has none)"));

			OrrenVasch vasch = f.vasch();
			vasch.setNoAi(true);
			f.session.startFight(null);
			float max = vasch.getMaxHealth();
			hit(vasch, a, HUGE);
			helper.assertTrue(near(vasch.getHealth(), b.phase2At * max), msg("phase 1 stops at 60%, got " + vasch.getHealth()));
		});

		f.at(2, () -> {
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.TRANSITION, msg("the 60% floor starts the first transition, got " + f.session.stage()));
			helper.assertTrue(hit(f.vasch(), a, 10f) == 0f, msg("no damage lands during a transition"));
			helper.assertTrue(phases(f.hooks, a.getUUID()).equals(List.of(1, 2)), msg("onBossPhase(2) at the transition, got " + phases(f.hooks, a.getUUID())));
		});

		f.at(2 + AFTER_TRANSITION, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.FIGHTING && f.session.phase() == 2 && vasch.phase() == 2, msg("phase 2 runs"));
			hit(vasch, a, HUGE);
			helper.assertTrue(near(vasch.getHealth(), b.phase3At * vasch.getMaxHealth()), msg("phase 2 stops at 30%, got " + vasch.getHealth()));
		});

		f.at(4 + AFTER_TRANSITION, () -> {
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.TRANSITION && f.vasch().phase() == 3, msg("the 30% floor starts the second transition"));
			helper.assertTrue(phases(f.hooks, a.getUUID()).equals(List.of(1, 2, 3)), msg("onBossPhase(3) at the transition"));
			helper.assertTrue(f.hooks.calls(CollectorHooks.Kind.YIELDED).isEmpty(), msg("no yield yet"));
		});

		f.at(4 + 2 * AFTER_TRANSITION, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.FIGHTING && f.session.phase() == 3, msg("phase 3 runs"));
			hit(vasch, a, HUGE);
			helper.assertTrue(near(vasch.getHealth(), b.yieldAt * vasch.getMaxHealth()), msg("phase 3 stops at 10%, got " + vasch.getHealth()));
		});

		f.at(6 + 2 * AFTER_TRANSITION, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.YIELDED && f.session.heartState() == HeartState.YIELDED, msg("10% is the yield"));
			helper.assertTrue(vasch.isKneeling() && vasch.isYielded() && vasch.isAlive(), msg("he kneels, alive"));
			List<CollectorHooks.Call> yielded = f.hooks.calls(CollectorHooks.Kind.YIELDED);
			helper.assertTrue(yielded.size() == 1 && yielded.get(0).player().equals(a.getUUID()) && BossArenas.COLLECTOR.equals(yielded.get(0).bossId()),
					msg("onBossYielded reaches the owner once, for \"collector\""));
			helper.assertTrue(f.hooks.calls(CollectorHooks.Kind.DEFEATED).isEmpty(), msg("nothing is defeated before the owner chooses"));
			f.done();
		});
	}

	/** 8.4: each tell yields only to its own causes; {@code unknown}, the Surge gather and the transitions ignore every interrupt. */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void interruptsOnlyFrom8_4Causes(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		Fight f = fight(helper, a, false, false, a);
		String[] others = {ForceReactions.CAUSE_SABER_THROW, ForceReactions.CAUSE_HEAVY_FEAR, ForceReactions.CAUSE_STAGGER, ForceReactions.CAUSE_SHOCK,
				ForceReactions.CAUSE_UNKNOWN};

		f.now(() -> {
			f.vasch().setNoAi(true);
			f.session.startFight(null);
		});

		f.at(2, () -> {
			OrrenVasch vasch = f.vasch();
			CollectorCombatGoal combat = f.combat();

			// Archive Hurl: Pull and Disarming Pull rip the chunk away.
			helper.assertTrue(combat.forceMove(Move.HURL, a) && combat.inTell(Move.HURL), msg("the Hurl tell starts"));
			ForceReactions.interruptWindup(vasch);
			ForceReactions.interruptWindup(vasch, a, ForceReactions.CAUSE_FORCE_PUSH);

			for (String cause : others) {
				ForceReactions.interruptWindup(vasch, a, cause);
			}

			helper.assertTrue(combat.move() == Move.HURL, msg("the Hurl ignores unknown, Push and the other causes"));
			ForceReactions.interruptWindup(vasch, a, ForceReactions.CAUSE_FORCE_PULL);
			helper.assertTrue(combat.move() == Move.NONE, msg("Pull rips the Hurl chunk away"));
			List<ArchiveDebris> chunks = f.level.getEntitiesOfClass(ArchiveDebris.class, AABB.of(f.session.layout().reliquary()), e -> true);
			helper.assertTrue(!chunks.isEmpty() && chunks.stream().allMatch(d -> d.state() == ArchiveDebris.State.FALLING), msg("the ripped chunk falls"));
			helper.assertTrue(combat.forceMove(Move.HURL, a), msg("the Hurl starts again"));
			ForceReactions.interruptWindup(vasch, a, ForceReactions.CAUSE_PULL_DISARM);
			helper.assertTrue(combat.move() == Move.NONE, msg("Disarming Pull rips the Hurl chunk away"));

			// Lunge: Disarming Pull and Push.
			helper.assertTrue(combat.forceMove(Move.LUNGE, a) && combat.inTell(Move.LUNGE), msg("the Lunge tell starts"));
			ForceReactions.interruptWindup(vasch, a, ForceReactions.CAUSE_FORCE_PULL);

			for (String cause : others) {
				ForceReactions.interruptWindup(vasch, a, cause);
			}

			helper.assertTrue(combat.move() == Move.LUNGE, msg("the Lunge ignores Pull and the other causes"));
			ForceReactions.interruptWindup(vasch, a, ForceReactions.CAUSE_FORCE_PUSH);
			helper.assertTrue(combat.move() == Move.NONE, msg("Push breaks the Lunge tell"));
			helper.assertTrue(combat.forceMove(Move.LUNGE, a), msg("the Lunge starts again"));
			ForceReactions.interruptWindup(vasch, a, ForceReactions.CAUSE_PULL_DISARM);
			helper.assertTrue(combat.move() == Move.NONE, msg("Disarming Pull breaks the Lunge tell"));

			// Archive Grip tell: Push, Pull, Disarming Pull.
			for (String breaker : new String[] {ForceReactions.CAUSE_FORCE_PUSH, ForceReactions.CAUSE_FORCE_PULL, ForceReactions.CAUSE_PULL_DISARM}) {
				helper.assertTrue(combat.forceMove(Move.GRIP, a) && combat.inTell(Move.GRIP), msg("the Grip tell starts"));

				for (String cause : others) {
					ForceReactions.interruptWindup(vasch, a, cause);
				}

				helper.assertTrue(combat.move() == Move.GRIP, msg("the Grip tell ignores the other causes"));
				ForceReactions.interruptWindup(vasch, a, breaker);
				helper.assertTrue(combat.move() == Move.NONE, msg(breaker + " breaks the Grip tell"));
			}

			helper.assertTrue(!Holds.isHeld(a), msg("a broken Grip tell never held anyone"));

			// Repelling Palm: no interrupt cause; only a Push reaching him clashes.
			helper.assertTrue(combat.forceMove(Move.PALM, a) && combat.inTell(Move.PALM), msg("the Palm tell starts"));
			ForceReactions.interruptWindup(vasch, a, ForceReactions.CAUSE_FORCE_PUSH);
			ForceReactions.interruptWindup(vasch, a, ForceReactions.CAUSE_FORCE_PULL);
			helper.assertTrue(combat.move() == Move.PALM, msg("interrupt causes do not stop the Palm"));
			ForceReactions.onPushHit(a, vasch);
			helper.assertTrue(combat.move() == Move.NONE, msg("a Push in the Palm tell clashes"));

			// Holocron Surge: nothing interrupts the gather.
			helper.assertTrue(combat.forceMove(Move.SURGE, a), msg("the Surge gather starts"));

			for (String cause : new String[] {ForceReactions.CAUSE_FORCE_PUSH, ForceReactions.CAUSE_FORCE_PULL, ForceReactions.CAUSE_PULL_DISARM}) {
				ForceReactions.interruptWindup(vasch, a, cause);
			}

			for (String cause : others) {
				ForceReactions.interruptWindup(vasch, a, cause);
			}

			ForceReactions.onPushHit(a, vasch);
			helper.assertTrue(combat.move() == Move.SURGE, msg("nothing interrupts the Surge gather"));

			// A transition: no move starts and no interrupt reaches him.
			helper.assertTrue(f.session.forcePhase(2) && f.session.isTransitioning(), msg("the first transition starts"));
			helper.assertTrue(combat.move() == Move.NONE, msg("the transition ends the gather"));
			helper.assertTrue(!combat.forceMove(Move.HURL, a), msg("no move starts during a transition"));
			f.done();
		});
	}

	// ---------------------------------------------------------------- phase 2: pylons and the ward

	/** Every source takes its 8.3 share off a pylon focus: saber, Push, Throw, Lightning, parried lance, Focused Blast, Pull, the Lift channel. */
	@GameTest(structure = ARENA, maxTicks = 120)
	public void pylonSilencedByEverySource(GameTestHelper helper) {
		placePylons(helper);
		ServerPlayer a = awakened(helper, HALL_A);
		// Focused Blast from a second player who is not in the fight (the branch is all that matters here).
		ServerPlayer blaster = plain(helper, ANTECHAMBER);
		// Through the skill-node mirror in the ability set: the skills feature rewrites the pushBranch
		// field from owned nodes on its periodic refresh of listed players.
		ForceData blast = Progression.get(blaster);
		blast.abilities.add(PushBranch.FOCUSED_BLAST.nodeId());
		Progression.commit(blaster, blast);
		a.setItemSlot(EquipmentSlot.MAINHAND, litSaber(a));
		Fight f = fight(helper, a, false, false, a, blaster);
		CollectorBalance b = CollectorBalance.get();
		int lift = 1;

		f.now(() -> {
			f.vasch().setNoAi(true);
			f.session.startFight(null);
			helper.assertTrue(f.session.forcePhase(2), msg("phase 2 can be forced"));
		});

		f.at(AFTER_TRANSITION, () -> {
			float full = CollectorRules.pylonResonance(1, b);

			for (int i = 0; i < PYLONS.size(); i++) {
				helper.assertTrue(f.session.pylonCrystal(i) == PylonCrystal.SINGING && blockCrystal(helper, i) == PylonCrystal.SINGING,
						msg("pylon " + i + " sings, on the block too"));
				helper.assertTrue(near(f.session.pylonResonance(i), full), msg("pylon " + i + " starts at " + full));
			}

			// Pylon 2: a saber hit, a Push, then a Saber Throw silences it.
			PylonFocus two = f.session.pylonFocus(2);
			helper.assertTrue(two != null && SaberComponents.isUsable(a.getMainHandItem()), msg("pylon 2 has a focus; the player's saber is lit"));
			two.hurtServer(f.level, f.level.damageSources().playerAttack(a), 7f);
			helper.assertTrue(near(f.session.pylonResonance(2), full - b.pylonSaber), msg("a saber hit takes pylonSaber, got " + f.session.pylonResonance(2)));
			helper.assertTrue(ForcePush.branch(a) == PushBranch.NONE && two.onForcePush(a, new Vec3(1, 0, 0), 1f), msg("a plain Push reaches the focus"));
			helper.assertTrue(near(f.session.pylonResonance(2), full - b.pylonSaber - b.pylonPush), msg("a Push takes pylonPush"));
			helper.assertTrue(two.onSaberThrowHit(a, 8f), msg("a thrown saber reaches the focus"));
			helper.assertTrue(f.session.pylonCrystal(2) == PylonCrystal.SILENCED && blockCrystal(helper, 2) == PylonCrystal.SILENCED,
					msg("a Saber Throw (pylonThrow) silences what is left"));
			helper.assertTrue(two.isRemoved() && f.session.pylonFocus(2) == null, msg("a silenced crystal loses its focus"));

			// Pylon 3: one second of Lightning, a parried sentinel lance, then Focused Blast.
			PylonFocus three = f.session.pylonFocus(3);
			helper.assertTrue(three != null, msg("pylon 3 has a focus"));

			for (int tick = 0; tick < 20; tick++) {
				three.onForceLightning(a, 0.25f);
			}

			helper.assertTrue(near(f.session.pylonResonance(3), full - b.pylonLightningPerSecond), msg("a second of Lightning takes pylonLightningPerSecond"));
			// Only players silence a pylon: an unparried lance, whose shooter is no player, takes nothing off.
			BlasterBolt stray = BlasterBolt.create(f.level, null, three.position(), 6f, 40, BlasterBolt.Style.SENTINEL, true);
			helper.assertTrue(!three.hurtServer(f.level, f.level.damageSources().source(EnemyEntities.BLASTER_DAMAGE, stray, f.vasch()), stray.damage())
					&& near(f.session.pylonResonance(3), full - b.pylonLightningPerSecond), msg("an unparried lance takes nothing off"));
			BlasterBolt lance =BlasterBolt.create(f.level, null, three.position(), 6f, 40, BlasterBolt.Style.SENTINEL, true);
			lance.redirect(new Vec3(0, 0, 1), a, true);
			helper.assertTrue(lance.heavy() && lance.parried(), msg("the lance is heavy and parried"));
			three.hurtServer(f.level, f.level.damageSources().source(EnemyEntities.BLASTER_DAMAGE, lance, a), lance.damage());
			helper.assertTrue(near(f.session.pylonResonance(3), full - b.pylonLightningPerSecond - b.pylonParriedLance), msg("a parried lance takes pylonParriedLance"));
			helper.assertTrue(ForcePush.branch(blaster) == PushBranch.FOCUSED_BLAST && three.onForcePush(blaster, new Vec3(0, 0, 1), 1f),
					msg("a Focused Blast reaches the focus"));
			helper.assertTrue(f.session.pylonCrystal(3) == PylonCrystal.SILENCED, msg("Focused Blast (pylonFocusedBlast) silences what is left"));

			// Pylon 0: Pull rips the crystal out at once.
			PylonFocus zero = f.session.pylonFocus(0);
			helper.assertTrue(zero != null && zero.onForcePull(a, new Vec3(0, 0, -1), 1f), msg("Pull reaches pylon 0 (in range and in sight)"));
			helper.assertTrue(f.session.pylonCrystal(0) == PylonCrystal.EMPTY && blockCrystal(helper, 0) == PylonCrystal.EMPTY, msg("Pull rips the crystal out"));
			helper.assertTrue(!f.session.isUnsung(), msg("one pylon still sings: no Unsung yet"));
		});

		// Pylon 1: the Lift channel, one call per tick, silences it on the pylonLiftTicks-th tick.
		for (int i = 0; i < b.pylonLiftTicks; i++) {
			f.at(AFTER_TRANSITION + 1 + i, () -> {
				PylonFocus focus = f.session.pylonFocus(lift);
				helper.assertTrue(focus != null && focus.onForceLift(a), msg("the Lift channel holds pylon 1 (in range and in sight): " + reach(f, a, focus, lift)));
			});
		}

		f.at(AFTER_TRANSITION + b.pylonLiftTicks - 2, () -> helper.assertTrue(f.session.pylonCrystal(lift) == PylonCrystal.SINGING,
				msg("a shorter Lift channel does not silence the crystal")));

		f.at(AFTER_TRANSITION + b.pylonLiftTicks + 3, () -> {
			helper.assertTrue(f.session.pylonCrystal(lift) == PylonCrystal.SILENCED && blockCrystal(helper, lift) == PylonCrystal.SILENCED,
					msg("a full Lift channel silences the crystal"));
			helper.assertTrue(f.session.singingCount() == 0 && f.session.isUnsung() && f.session.isWardDown(), msg("the last pylon falling silent is Unsung"));
			f.done();
		});
	}

	/** From below, the pylon's top edge can hide the crystal's centre while its tip is in sight: Pull still reaches it. */
	@GameTest(structure = ARENA, maxTicks = 100)
	public void pullReachesTheCrystalTipOverThePylonEdge(GameTestHelper helper) {
		placePylons(helper);
		ServerPlayer a = awakened(helper, HALL_A);
		Fight f = fight(helper, a, false, false, a);

		f.now(() -> {
			f.vasch().setNoAi(true);
			f.session.startFight(null);
			helper.assertTrue(f.session.forcePhase(2), msg("phase 2 can be forced"));
		});

		f.at(AFTER_TRANSITION, () -> {
			PylonFocus zero = f.session.pylonFocus(0);
			helper.assertTrue(zero != null, msg("pylon 0 has a focus"));
			Vec3 centre = zero.crystalCentre();
			double base = centre.y - (zero.getBoundingBox().maxY - zero.getBoundingBox().minY) / 2 - PylonFocus.CRYSTAL_HEIGHT;
			BlockPos pylon = BlockPos.containing(centre.x, base + 0.5, centre.z);
			// On the floor a block below the pylon's base, one block in front of the crystal.
			a.snapTo(centre.x, base - 1.0, centre.z - 1.0, 0f, -40f);
			Vec3 eye = a.getEyePosition();
			BlockHitResult hit = f.level.clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, a));
			helper.assertTrue(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pylon),
					msg("the pylon's edge hides the crystal's centre from the eye: " + reach(f, a, zero, 0)));
			helper.assertTrue(zero.onForcePull(a, new Vec3(0, 0, -1), 1f), msg("Pull reaches the crystal over the edge: " + reach(f, a, zero, 0)));
			helper.assertTrue(f.session.pylonCrystal(0) == PylonCrystal.EMPTY, msg("Pull rips the crystal out"));
			f.done();
		});
	}

	/** A silenced pylon may regrow once per phase, resingTicks after it fell silent, only while another sings and the ward is up. */
	@GameTest(maxTicks = 20)
	public void resingAtMostOncePerPhaseWhileAnotherSings(GameTestHelper helper) {
		CollectorBalance b = CollectorBalance.get();
		long silenced = 1000L;
		long due = silenced + b.resingTicks;
		helper.assertTrue(!CollectorRules.mayResing(due - 1, silenced, 0, true, false, b), msg("not before resingTicks"));
		helper.assertTrue(CollectorRules.mayResing(due, silenced, 0, true, false, b), msg("after resingTicks, while another sings"));
		helper.assertTrue(CollectorRules.mayResing(due + 4000, silenced, b.resingPerPhase - 1, true, false, b), msg("any time later, below the per-phase limit"));
		helper.assertTrue(!CollectorRules.mayResing(due, silenced, 0, false, false, b), msg("never while no other pylon sings"));
		helper.assertTrue(!CollectorRules.mayResing(due, silenced, b.resingPerPhase, true, false, b), msg("at most resingPerPhase times per pylon and phase"));
		helper.assertTrue(!CollectorRules.mayResing(due, silenced, 0, true, true, b), msg("never once the ward is down for the phase"));
		helper.succeed();
	}

	/** The ward (x0.35 with 3-4 singing, x0.65 with 1-2), then Unsung (x1.5 for unsungTicks), then the ward stays down (x1). */
	@GameTest(structure = ARENA, maxTicks = 220)
	public void unsungDoublesDamageWindow(GameTestHelper helper) {
		placePylons(helper);
		ServerPlayer a = awakened(helper, HALL_A);
		Fight f = fight(helper, a, false, false, a);
		CollectorBalance b = CollectorBalance.get();
		int start = AFTER_TRANSITION;

		f.now(() -> {
			f.vasch().setNoAi(true);
			f.session.startFight(null);
			helper.assertTrue(f.session.forcePhase(2), msg("phase 2 can be forced"));
		});

		f.at(start, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(f.session.singingCount() == 4, msg("four pylons sing"));
			helper.assertTrue(near(f.session.wardFactor(), b.wardHigh) && near(hit(vasch, a, 10f), 10f * b.wardHigh), msg("3-4 singing: x wardHigh"));

			for (int i : new int[] {0, 1}) {
				PylonFocus focus = f.session.pylonFocus(i);
				helper.assertTrue(focus != null && focus.onForcePull(a, new Vec3(0, 0, -1), 1f), msg("Pull reaches pylon " + i));
			}

			helper.assertTrue(f.session.singingCount() == 2 && near(hit(vasch, a, 10f), 10f * b.wardLow), msg("1-2 singing: x wardLow"));

			for (int i : new int[] {2, 3}) {
				for (int throwHit = 0; throwHit < 2; throwHit++) {
					PylonFocus focus = f.session.pylonFocus(i);
					helper.assertTrue(focus != null && focus.onSaberThrowHit(a, 8f), msg("the thrown saber reaches pylon " + i));
				}
			}

			helper.assertTrue(f.session.singingCount() == 0 && f.session.isUnsung() && f.session.isWardDown(), msg("all silent: Unsung"));
			helper.assertTrue(near(hit(vasch, a, 10f), 10f * b.unsungMultiplier), msg("Unsung: x unsungMultiplier in total"));
		});

		f.at(start + b.unsungTicks - 5, () -> {
			helper.assertTrue(f.session.isUnsung(), msg("the Unsung window lasts unsungTicks"));
			helper.assertTrue(near(hit(f.vasch(), a, 10f), 10f * b.unsungMultiplier), msg("still x unsungMultiplier near the window's end"));
		});

		f.at(start + b.unsungTicks + 5, () -> {
			helper.assertTrue(!f.session.isUnsung() && f.session.isWardDown() && near(f.session.wardFactor(), 1f), msg("after the window the ward stays down"));
			helper.assertTrue(near(hit(f.vasch(), a, 10f), 10f), msg("ward down: x1"));
			helper.assertTrue(f.session.phase() == 2 && f.session.stage() == ArenaSession.Stage.FIGHTING, msg("still phase 2"));
			f.done();
		});
	}

	// ---------------------------------------------------------------- phase 3: Surge, Grip, Spent

	/** The Surge's cover is two server rays from his chest: behind a fixture column nothing, one ray blocked or airborne half. */
	@GameTest(structure = ARENA, maxTicks = 140)
	public void surgeCoverByServerRays(GameTestHelper helper) {
		ServerPlayer covered = awakened(helper, new BlockPos(8, 1, 13));
		ServerPlayer open = awakened(helper, new BlockPos(12, 1, 20));
		ServerPlayer ledge = awakened(helper, new BlockPos(14, 1, 20));
		ServerPlayer airborne = awakened(helper, new BlockPos(5, 1, 20));
		ServerPlayer far = awakened(helper, new BlockPos(8, 1, 6));
		// A ledge at head height between him and the third player: it blocks the eye ray only.
		// API-CHECK: SlabType.TOP (net.minecraft.world.level.block.state.properties.SlabType) is not in the dumps; BlockStateProperties.SLAB_TYPE is.
		helper.setBlock(new BlockPos(13, 2, 20), Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP));
		Fight f = fight(helper, covered, false, false, covered, open, ledge, airborne, far);
		CollectorBalance b = CollectorBalance.get();

		f.now(() -> {
			f.vasch().setNoAi(true);
			f.session.startFight(null);
			helper.assertTrue(f.session.forcePhase(3), msg("phase 3 can be forced"));
		});

		f.at(AFTER_TRANSITION, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(f.session.phase() == 3 && f.session.stage() == ArenaSession.Stage.FIGHTING, msg("phase 3 runs"));
			// He stands south of column 1; the covered player is north of it.
			Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 1, 20)));
			vasch.teleportTo(stand.x, stand.y, stand.z);
			airborne.setOnGround(false);
			CollectorCombatGoal combat = f.combat();
			helper.assertTrue(combat.surgeExposure(covered) == 0f, msg("behind a column both rays are blocked: nothing"));
			helper.assertTrue(combat.surgeExposure(open) == 1f, msg("in the open: the full Surge"));
			helper.assertTrue(near(combat.surgeExposure(ledge), b.surgePartialMultiplier), msg("one ray blocked: x surgePartialMultiplier"));
			helper.assertTrue(near(combat.surgeExposure(airborne), b.surgeAirborneMultiplier), msg("airborne in the open: x surgeAirborneMultiplier"));
			helper.assertTrue(combat.surgeExposure(far) == 0f, msg("beyond surgeRadius: nothing"));
			helper.assertTrue(CollectorRules.surgeMultiplier(true, false, true, b) == b.surgePartialMultiplier * b.surgeAirborneMultiplier,
					msg("partial cover and airborne stack"));

			helper.assertTrue(combat.forceMove(Move.SURGE, covered), msg("the Surge gather starts"));
			ForceReactions.interruptWindup(vasch, open, ForceReactions.CAUSE_FORCE_PUSH);
			ForceReactions.interruptWindup(vasch, open, ForceReactions.CAUSE_PULL_DISARM);
			helper.assertTrue(combat.move() == Move.SURGE && !vasch.isSpent(), msg("the gather goes on"));
		});

		f.at(AFTER_TRANSITION + b.surgeTellTicks + 2, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(f.combat().move() == Move.NONE, msg("the Surge released after surgeTellTicks"));
			helper.assertTrue(vasch.isSpent() && vasch.action() == OrrenVasch.Action.SPENT, msg("after the release he is Spent"));
			f.done();
		});
	}

	/** The Grip holds through {@code Holds} (x1.25 taken while he channels); 8 damage from anyone, the held player's Push, or a big hit in the tell ends it. */
	@GameTest(structure = ARENA, maxTicks = 180)
	public void gripUsesHoldsAndBreaksOn8OrPush(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		ServerPlayer ally = awakened(helper, HALL_B);
		Fight f = fight(helper, a, false, false, a, ally);
		CollectorBalance b = CollectorBalance.get();
		int start = AFTER_TRANSITION;
		int held = b.gripTellTicks + 2;

		f.now(() -> {
			f.vasch().setNoAi(true);
			f.session.startFight(null);
			helper.assertTrue(f.session.forcePhase(3), msg("phase 3 can be forced"));
		});

		f.at(start, () -> {
			CollectorCombatGoal combat = f.combat();
			helper.assertTrue(combat.forceMove(Move.GRIP, a) && combat.inTell(Move.GRIP) && !Holds.isHeld(a), msg("the Grip tell starts; nobody is held yet"));
		});

		f.at(start + held, () -> {
			OrrenVasch vasch = f.vasch();
			CollectorCombatGoal combat = f.combat();
			helper.assertTrue(Holds.isHeld(a) && combat.isChannellingGrip(), msg("after the tell the target is held through Holds"));
			float first = hit(vasch, ally, 6f);
			helper.assertTrue(near(first, 6f * b.gripTakenMultiplier), msg("he takes x gripTakenMultiplier while channelling, got " + first));
			helper.assertTrue(first < b.gripBreakDamage && Holds.isHeld(a), msg("less than gripBreakDamage keeps the hold"));
			// Enough to pass gripBreakDamage in total (a margin over the exact remainder: float rounding).
			float rest = (b.gripBreakDamage - first) / b.gripTakenMultiplier + 0.2f;
			hit(vasch, ally, rest);
			helper.assertTrue(!Holds.isHeld(a) && combat.move() == Move.NONE, msg("gripBreakDamage from allies breaks the hold"));
			helper.assertTrue(combat.forceMove(Move.GRIP, a), msg("the Grip starts again"));
		});

		f.at(start + 2 * held, () -> {
			helper.assertTrue(Holds.isHeld(a), msg("held again"));
			// The held player casts Push: its cooldown rises, which the session watches.
			Progression.setCooldown(a, ForcePush.ABILITY_ID, 40);
		});

		f.at(start + 2 * held + 2, () -> {
			OrrenVasch vasch = f.vasch();
			CollectorCombatGoal combat = f.combat();
			helper.assertTrue(!Holds.isHeld(a) && combat.move() == Move.NONE, msg("the held player's Push breaks the Grip"));
			helper.assertTrue(combat.forceMove(Move.GRIP, a), msg("a third Grip tell"));
			hit(vasch, ally, b.interruptHitDamage - 1f);
			helper.assertTrue(combat.inTell(Move.GRIP), msg("a small hit does not break the tell"));
			hit(vasch, ally, b.interruptHitDamage);
			helper.assertTrue(combat.move() == Move.NONE && !Holds.isHeld(a), msg("an interruptHitDamage hit breaks the Grip tell"));
			f.done();
		});
	}

	/** Lift raises him only while Spent (to spentLiftHeight; W3's slam staggers him); Spent also takes x spentMultiplier in total. */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void liftOnlyWhileSpent(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		Fight f = fight(helper, a, false, false, a);
		CollectorBalance b = CollectorBalance.get();
		int spent = AFTER_TRANSITION + b.surgeTellTicks + 2;

		f.now(() -> {
			OrrenVasch vasch = f.vasch();
			vasch.setNoAi(true);
			f.session.startFight(null);
			helper.assertTrue(ForceReactions.liftHeight(vasch, 3f) == 0f, msg("Lift is refused in phase 1"));
			helper.assertTrue(f.session.forcePhase(3), msg("phase 3 can be forced"));
		});

		f.at(AFTER_TRANSITION, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(ForceReactions.liftHeight(vasch, 3f) == 0f, msg("Lift is refused in phase 3 outside Spent"));
			helper.assertTrue(f.combat().forceMove(Move.SURGE, a), msg("the Surge gather starts"));
			helper.assertTrue(ForceReactions.liftHeight(vasch, 3f) == 0f, msg("Lift is refused during the gather"));
		});

		f.at(spent, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(vasch.isSpent(), msg("he is Spent after the release"));
			helper.assertTrue(near(ForceReactions.liftHeight(vasch, 3f), Math.min(3f, b.spentLiftHeight)), msg("Spent: Lift raises him to spentLiftHeight"));
			helper.assertTrue(near(ForceReactions.liftHeight(vasch, 0.5f), 0.5f), msg("a lower lift is not raised"));
			helper.assertTrue(near(hit(vasch, a, 10f), 10f * b.spentMultiplier), msg("Spent: x spentMultiplier in total"));
		});

		f.at(spent + b.spentTicks + 4, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(!vasch.isSpent(), msg("Spent lasts spentTicks"));
			helper.assertTrue(ForceReactions.liftHeight(vasch, 3f) == 0f, msg("Lift is refused again"));
			helper.assertTrue(near(hit(vasch, a, 10f), 10f), msg("outside Spent a plain hit is x1 again"));
			f.done();
		});
	}

	// ---------------------------------------------------------------- endings

	/**
	 * Peaceful (6.9): the meeting, then he says his line and kneels {@code PEACEFUL_TALK_TICKS} later
	 * (so the line can be read); no fight, no bar, no phase hooks.
	 */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void peacefulRefusesFight(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		Fight f = fight(helper, a, false, false, a);
		long kneels = 2 + PEACEFUL_TALK + 2;
		ArenaSession[] command = new ArenaSession[1];
		CollectorHooks.Recorder again = new CollectorHooks.Recorder();

		f.now(() -> f.session.overrideDifficulty(Difficulty.PEACEFUL));

		f.at(2, () -> {
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.MEETING, msg("the owner crossing for the first time starts the meeting, got " + f.session.stage()));
			DialogueSession open = Dialogues.session(a);
			helper.assertTrue(open != null && ArenaSession.MEET_DIALOGUE.equals(open.dialogue()), msg("the meeting dialogue is open"));
			Dialogues.close(a);
			helper.assertTrue(f.session.isPeacefulTalking() && !f.vasch().isKneeling() && f.hooks.calls(CollectorHooks.Kind.YIELDED).isEmpty(),
					msg("after the meeting he talks first, got " + f.session.stage()));
		});

		f.at(kneels, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.YIELDED && f.session.heartState() == HeartState.YIELDED && !f.session.isPeacefulTalking(),
					msg("then he kneels, got " + f.session.stage()));
			helper.assertTrue(vasch.isKneeling() && !f.session.isFighting() && f.session.attempts() == 0, msg("no fight was started"));
			helper.assertTrue(f.hooks.calls(CollectorHooks.Kind.PHASE).isEmpty(), msg("no phase hook in Peaceful"));
			helper.assertTrue(f.hooks.calls(CollectorHooks.Kind.YIELDED).size() == 1, msg("the owner is told he yielded"));
			f.session.startFight(null);
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.YIELDED, msg("a kneeling Vasch cannot be made to fight"));

			// The command path on a fresh session (it replaces the first): startFight in Peaceful talks, then kneels.
			command[0] = BossArenas.openSession(f.level, helper.absolutePos(HEART), layout(helper), a.getUUID(), again, false, false);
			command[0].overrideDifficulty(Difficulty.PEACEFUL);
			command[0].startFight(null);
			helper.assertTrue(command[0].isPeacefulTalking() && command[0].stage() == ArenaSession.Stage.WAITING, msg("a forced start in Peaceful talks first, got " + command[0].stage()));
		});

		f.at(kneels + PEACEFUL_TALK + 2, () -> {
			helper.assertTrue(command[0].stage() == ArenaSession.Stage.YIELDED && again.calls(CollectorHooks.Kind.PHASE).isEmpty(),
					msg("a forced start in Peaceful kneels too, got " + command[0].stage()));
			f.done();
		});
	}

	/** The owner's choice: RESOLVED, first clear, onBossDefeated once per participant with the outcome, once only; the scene plays. */
	@GameTest(structure = ARENA, maxTicks = 60)
	public void resolveCallsHookWithOutcome(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		ServerPlayer ally = awakened(helper, HALL_B);
		ReliquaryHeartBlockEntity heart = heart(helper, a, HeartState.READY);
		Fight f = fight(helper, a, false, false, a, ally);

		f.now(() -> {
			OrrenVasch vasch = f.vasch();
			vasch.setNoAi(true);
			f.session.startFight(null);
			helper.assertTrue(f.session.forcePhase(4), msg("the yield can be forced"));
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.YIELDED && heart.state() == HeartState.YIELDED && heart.yieldNotified(),
					msg("YIELDED is written to the heart, with the notice"));
			helper.assertTrue(f.hooks.calls(CollectorHooks.Kind.YIELDED).size() == 1, msg("onBossYielded runs once"));
			helper.assertTrue(!BossArenas.isResolved(a), msg("not resolved before the choice"));
			helper.assertTrue(!f.session.resolve(BossOutcome.ECHO), msg("ECHO is not an owner's choice"));

			BossArenas.resolve(a, BossOutcome.EXECUTED);
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.RESOLVING, msg("the scene plays, got " + f.session.stage()));
			helper.assertTrue(heart.state() == HeartState.RESOLVED && heart.firstClear() && !heart.yieldNotified(), msg("the heart is RESOLVED with its first clear"));
			helper.assertTrue(BossArenas.isResolved(a), msg("isResolved answers true for the owner"));

			List<CollectorHooks.Call> defeated = f.hooks.calls(CollectorHooks.Kind.DEFEATED);
			helper.assertTrue(defeated.size() == 2 && f.hooks.defeatedCount(a.getUUID()) == 1 && f.hooks.defeatedCount(ally.getUUID()) == 1,
					msg("onBossDefeated once per participant, got " + defeated.size()));

			for (CollectorHooks.Call call : defeated) {
				helper.assertTrue(call.outcome() == BossOutcome.EXECUTED && a.getUUID().equals(call.owner()) && call.firstClear()
						&& BossArenas.COLLECTOR.equals(call.bossId()) && !call.unchained(), msg("each call carries the outcome, the owner and the first clear"));
			}

			helper.assertTrue(!f.session.resolve(BossOutcome.SPARED) && f.hooks.calls(CollectorHooks.Kind.DEFEATED).size() == 2, msg("the choice is made once"));
		});

		f.at(16, () -> {
			OrrenVasch vasch = f.vasch();
			helper.assertTrue(vasch.isFallen() && vasch.getMainHandItem().isEmpty(), msg("the execution scene: one stroke, he falls, the blade rolls away"));
			f.done();
		});
	}

	/** Each echo clear runs onBossDefeated exactly once per participant with ECHO and firstClear false; the heart stays RESOLVED. */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void rematchHookOncePerDayWithEcho(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		ServerPlayer ally = awakened(helper, HALL_B);
		ReliquaryHeartBlockEntity heart = heart(helper, a, HeartState.RESOLVED);
		heart.setFirstClear(true);
		Fight f = fight(helper, a, true, false, a, ally);
		CollectorHooks.Recorder second = new CollectorHooks.Recorder();
		ArenaSession[] rematch = new ArenaSession[1];

		f.now(() -> {
			OrrenVasch echo = f.vasch();
			echo.setNoAi(true);
			helper.assertTrue(f.session.isEcho() && f.session.stage() == ArenaSession.Stage.FORMING && echo.isEcho() && echo.isForming(), msg("the echo forms"));
			helper.assertTrue(echo.position().distanceTo(f.session.layout().daisFeet()) < 0.5, msg("the echo forms at the dais"));
			helper.assertTrue(f.session.participants().size() == 2, msg("both players in the hall join the echo"));
			helper.assertTrue(near(f.session.outgoing(), CollectorBalance.get().echoDamageMultiplier), msg("the echo hits x echoDamageMultiplier"));
			helper.assertTrue(f.session.forcePhase(3), msg("phase 3 can be forced"));
		});

		f.at(AFTER_TRANSITION, () -> {
			OrrenVasch echo = f.vasch();
			helper.assertTrue(f.session.phase() == 3 && f.session.stage() == ArenaSession.Stage.FIGHTING, msg("the echo's phase 3 runs"));
			hit(echo, a, HUGE);
			helper.assertTrue(echo.isAlive() && echo.getHealth() > 0f, msg("the echo is never killed outright"));
		});

		f.at(AFTER_TRANSITION + 2, () -> {
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.RESOLVING && f.session.echoCleared(), msg("the echo dissolves at its floor"));
			List<CollectorHooks.Call> defeated = f.hooks.calls(CollectorHooks.Kind.DEFEATED);
			helper.assertTrue(defeated.size() == 2 && f.hooks.defeatedCount(a.getUUID()) == 1 && f.hooks.defeatedCount(ally.getUUID()) == 1,
					msg("one hook per participant per echo clear, got " + defeated.size()));

			for (CollectorHooks.Call call : defeated) {
				helper.assertTrue(call.outcome() == BossOutcome.ECHO && !call.firstClear() && a.getUUID().equals(call.owner()) && !call.unchained(),
						msg("an echo clear is ECHO, never a first clear"));
			}

			helper.assertTrue(f.hooks.calls(CollectorHooks.Kind.YIELDED).isEmpty(), msg("the echo never yields"));
			helper.assertTrue(heart.state() == HeartState.RESOLVED && heart.echoClears() == 1 && heart.unchainedUnlocked(),
					msg("the heart stays RESOLVED; the first echo clear unlocks Unchained"));
		});

		f.at(AFTER_TRANSITION + 50, () -> {
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.ENDED && BossArenas.sessionAt(f.level, helper.absolutePos(HEART)) == null,
					msg("the dissolved echo's session ends"));
			helper.assertTrue(!f.session.forcePhase(4) && f.hooks.calls(CollectorHooks.Kind.DEFEATED).size() == 2, msg("an ended echo credits nobody again"));

			// The next rematch: once more per participant, no more.
			rematch[0] = BossArenas.openSession(f.level, helper.absolutePos(HEART), layout(helper), a.getUUID(), second, true, false);
			rematch[0].overrideDifficulty(Difficulty.NORMAL);
			helper.assertTrue(rematch[0].forcePhase(4) && rematch[0].echoCleared(), msg("the second echo clears"));
			helper.assertTrue(second.defeatedCount(a.getUUID()) == 1 && second.defeatedCount(ally.getUUID()) == 1, msg("again exactly once per participant"));
			helper.assertTrue(second.calls(CollectorHooks.Kind.DEFEATED).stream().allMatch(c -> c.outcome() == BossOutcome.ECHO && !c.firstClear()),
					msg("again ECHO, not a first clear"));
			helper.assertTrue(heart.echoClears() == 2 && heart.state() == HeartState.RESOLVED, msg("the heart counts both echo clears"));
		});

		f.at(AFTER_TRANSITION + 100, () -> {
			helper.assertTrue(rematch[0].stage() == ArenaSession.Stage.ENDED && second.calls(CollectorHooks.Kind.DEFEATED).size() == 2,
					msg("the second echo ends without another call"));
			f.done();
		});
	}

	/** Unchained: x1.5 health, x1.25 damage (replacing the echo's), phase 3 from 40%, and isUnchained during the hook. */
	@GameTest(structure = ARENA, maxTicks = 200)
	public void unchainedValues(GameTestHelper helper) {
		ServerPlayer a = awakened(helper, HALL_A);
		ReliquaryHeartBlockEntity heart = heart(helper, a, HeartState.RESOLVED);
		heart.setFirstClear(true);
		heart.addEchoClear();
		CollectorBalance b = CollectorBalance.get();
		BlockPos plainHeart = helper.absolutePos(new BlockPos(2, 1, 1));

		// Unchained needs the echo: a plain session never is.
		ArenaSession plain = BossArenas.openSession(helper.getLevel(), plainHeart, layout(helper), a.getUUID(), new CollectorHooks.Recorder(), false, true);
		boolean plainUnchained = plain.isUnchained();
		BossArenas.closeSession(helper.getLevel(), plainHeart);

		Fight f = fight(helper, a, true, true, a);

		f.now(() -> {
			helper.assertTrue(!plainUnchained, msg("a session that is not the echo is never Unchained"));
			helper.assertTrue(near(CollectorRules.maxHealth(1, true, b), b.maxHealth * b.unchainedHealth), msg("Unchained health x unchainedHealth"));
			helper.assertTrue(near(CollectorRules.outgoingMultiplier(true, true, b), b.unchainedDamage)
					&& near(CollectorRules.outgoingMultiplier(true, false, b), b.echoDamageMultiplier) && CollectorRules.outgoingMultiplier(false, false, b) == 1f,
					msg("Unchained damage replaces the echo's"));
			helper.assertTrue(near(CollectorRules.phase3At(true, b), b.unchainedPhase3At) && CollectorRules.phaseFor(0.35f, true, b) == 3
					&& CollectorRules.phaseFor(0.35f, false, b) == 2, msg("Unchained phase 3 begins at unchainedPhase3At"));

			OrrenVasch echo = f.vasch();
			echo.setNoAi(true);
			helper.assertTrue(f.session.isEcho() && f.session.isUnchained() && near(f.session.outgoing(), b.unchainedDamage), msg("the session is the Unchained echo"));
			helper.assertTrue(f.session.forcePhase(2), msg("phase 2 can be forced"));
			helper.assertTrue(near(echo.getMaxHealth(), CollectorRules.maxHealth(1, true, b)), msg("his maximum is the Unchained one, got " + echo.getMaxHealth()));
		});

		f.at(AFTER_TRANSITION, () -> {
			OrrenVasch echo = f.vasch();
			helper.assertTrue(f.session.phase() == 2 && f.session.stage() == ArenaSession.Stage.FIGHTING, msg("phase 2 runs"));
			hit(echo, a, HUGE);
			helper.assertTrue(near(echo.getHealth(), b.unchainedPhase3At * echo.getMaxHealth()), msg("phase 2 stops at 40%, got " + echo.getHealth()));
		});

		f.at(AFTER_TRANSITION + 2, () -> {
			helper.assertTrue(f.session.isTransitioning() && f.vasch().phase() == 3, msg("the 40% floor starts the second transition"));
			helper.assertTrue(f.session.forcePhase(4), msg("the echo's end can be forced"));
			List<CollectorHooks.Call> defeated = f.hooks.calls(CollectorHooks.Kind.DEFEATED);
			helper.assertTrue(defeated.size() == 1 && defeated.get(0).outcome() == BossOutcome.ECHO && defeated.get(0).unchained() && !defeated.get(0).firstClear(),
					msg("the clear is ECHO with isUnchained true during the call"));
			helper.assertTrue(heart.echoClears() == 2, msg("the heart counts the Unchained clear"));
		});

		f.at(AFTER_TRANSITION + 50, () -> {
			helper.assertTrue(f.session.stage() == ArenaSession.Stage.ENDED && !BossArenas.isUnchained(a), msg("isUnchained is false once the session is over"));
			f.done();
		});
	}

	/**
	 * W5 part B: {@code ReliquarySites.evaluate} (spec 3.6, W5 plan 4.5) accepts a clear box and
	 * refuses, by its lang-free reasons and in this order: a box over chunks that are not loaded
	 * ({@code "unloaded"}), one reaching within 8 blocks of the world's bottom ({@code "too_deep"}),
	 * one a worldgen structure piece touches ({@code "worldgen_piece"}, through the injected
	 * predicate, asked once per chunk with the box's lowest corner clipped into that chunk) and one
	 * holding a {@code #built_blocks} block ({@code "built_blocks"}). Uses only the lead's arena
	 * fixture and vanilla blocks.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void reliquaryEvaluateRejects(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Arrays.equals(ReliquarySites.capped(new int[] {96, 128, 200}), new int[] {96, 128, ReliquarySites.MAX_RING}),
				msg("rings from the config are capped at " + ReliquarySites.MAX_RING + " (3.6)"));
		BlockPos low = helper.absolutePos(new BlockPos(2, 1, 2));
		// Clear of the arena's two stone-brick cover columns (z = 16), which are built blocks.
		BlockPos high = helper.absolutePos(new BlockPos(21, 5, 14));
		BoundingBox clear = BoundingBox.fromCorners(low, high);
		List<BlockPos> asked = new ArrayList<>();
		Predicate<BlockPos> noPiece = pos -> {
			asked.add(pos.immutable());
			return false;
		};

		// A clear, loaded box: the arena's air, its dais and pylon stand-ins (none of them built blocks).
		String verdict = ReliquarySites.evaluate(level, clear, noPiece);
		helper.assertTrue(verdict == null, msg("a clear box in loaded chunks is accepted, got " + verdict));
		List<ChunkPos> chunks = clear.intersectingChunks().toList();
		helper.assertTrue(asked.size() == chunks.size(),
				msg("the worldgen predicate is asked once per chunk the box touches: " + chunks.size() + ", asked " + asked.size()));

		for (BlockPos pos : asked) {
			ChunkPos chunk = ChunkPos.containing(pos);
			helper.assertTrue(chunks.contains(chunk) && pos.getY() == clear.minY() && pos.getX() == Math.max(clear.minX(), chunk.x() << 4)
					&& pos.getZ() == Math.max(clear.minZ(), chunk.z() << 4), msg("each time with the box's lowest corner clipped into that chunk, got " + pos));
		}

		// A worldgen piece in one chunk of the box (the chunk of its highest corner).
		ChunkPos claimed = ChunkPos.containing(high);
		verdict = ReliquarySites.evaluate(level, clear, pos -> ChunkPos.containing(pos).equals(claimed));
		helper.assertTrue("worldgen_piece".equals(verdict), msg("a box a worldgen piece touches is refused as worldgen_piece, got " + verdict));

		// A built block (planks are in #oldrepublic:built_blocks).
		helper.setBlock(new BlockPos(10, 1, 10), Blocks.OAK_PLANKS);
		verdict = ReliquarySites.evaluate(level, clear, pos -> false);
		helper.assertTrue("built_blocks".equals(verdict), msg("a box holding a built block is refused as built_blocks, got " + verdict));
		verdict = ReliquarySites.evaluate(level, clear, pos -> ChunkPos.containing(pos).equals(claimed));
		helper.assertTrue("worldgen_piece".equals(verdict), msg("the worldgen check comes before the built-block check, got " + verdict));
		helper.setBlock(new BlockPos(10, 1, 10), Blocks.AIR);
		helper.assertTrue(ReliquarySites.evaluate(level, clear, pos -> false) == null, msg("with the planks gone the box is clear again"));

		// Too deep: the same columns, reaching within 8 blocks of the world's bottom.
		BoundingBox deep = new BoundingBox(clear.minX(), level.getMinY() + 2, clear.minZ(), clear.maxX(), level.getMinY() + 20, clear.maxZ());
		verdict = ReliquarySites.evaluate(level, deep, pos -> false);
		helper.assertTrue("too_deep".equals(verdict), msg("a box within 8 blocks of the bottom is refused as too_deep, got " + verdict));
		BoundingBox edge = new BoundingBox(clear.minX(), level.getMinY() + 8, clear.minZ(), clear.maxX(), level.getMinY() + 20, clear.maxZ());
		verdict = ReliquarySites.evaluate(level, edge, pos -> false);
		helper.assertTrue(!"too_deep".equals(verdict), msg("exactly 8 blocks above the bottom is deep enough, got " + verdict));

		// Unloaded: the same box a million blocks east, where no chunk is loaded; this is checked first.
		BoundingBox away = new BoundingBox(clear.minX() + 1_000_000, clear.minY(), clear.minZ(), clear.maxX() + 1_000_000, clear.maxY(), clear.maxZ());
		verdict = ReliquarySites.evaluate(level, away, pos -> true);
		helper.assertTrue("unloaded".equals(verdict), msg("a box over unloaded chunks is refused as unloaded, got " + verdict));
		BoundingBox awayDeep = new BoundingBox(away.minX(), level.getMinY() + 2, away.minZ(), away.maxX(), level.getMinY() + 20, away.maxZ());
		verdict = ReliquarySites.evaluate(level, awayDeep, pos -> true);
		helper.assertTrue("unloaded".equals(verdict), msg("unloaded chunks are checked before depth, got " + verdict));
		helper.succeed();
	}
}
