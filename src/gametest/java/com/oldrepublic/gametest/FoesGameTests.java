package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.authlib.GameProfile;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.AbilityLocks;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.boss.kaast.CampGateBlock;
import com.oldrepublic.boss.kaast.CampLayout;
import com.oldrepublic.boss.kaast.CampMine;
import com.oldrepublic.boss.kaast.KaastBalance;
import com.oldrepublic.boss.kaast.KaastBlocks;
import com.oldrepublic.boss.kaast.KaastEntities;
import com.oldrepublic.boss.kaast.KaastFight;
import com.oldrepublic.boss.kaast.KaastRules;
import com.oldrepublic.boss.kaast.KaastSession;
import com.oldrepublic.boss.kaast.Rattle;
import com.oldrepublic.boss.kaast.TorvinKaast;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.CombatHooks;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.EnemyEntities;
import com.oldrepublic.enemy.creature.AshHound;
import com.oldrepublic.enemy.creature.CreatureBalance;
import com.oldrepublic.enemy.creature.CreatureEntities;
import com.oldrepublic.enemy.creature.CreatureRules;
import com.oldrepublic.enemy.creature.Hollowhorn;
import com.oldrepublic.enemy.droid.DroidEntities;
import com.oldrepublic.enemy.droid.DroidReactions;
import com.oldrepublic.enemy.droid.Droids;
import com.oldrepublic.enemy.droid.SecurityDroid;
import com.oldrepublic.enemy.droid.SeekerDroid;
import com.oldrepublic.enemy.mandalorian.HunterBalance;
import com.oldrepublic.enemy.mandalorian.HunterDamage;
import com.oldrepublic.enemy.mandalorian.HunterEntities;
import com.oldrepublic.enemy.mandalorian.HunterNet;
import com.oldrepublic.enemy.mandalorian.HunterRocket;
import com.oldrepublic.enemy.mandalorian.HunterRules;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.enemy.mandalorian.Netting;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceDamage;
import com.oldrepublic.force.ForceM4Feature;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.force.barrier.BarrierBalance;
import com.oldrepublic.force.barrier.ForceBarrier;
import com.oldrepublic.force.choke.ChokedTargets;
import com.oldrepublic.force.choke.ForceChoke;
import com.oldrepublic.force.jump.ForceJump;
import com.oldrepublic.force.speed.ForceSpeed;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterFourStage;
import com.oldrepublic.quest.story.KaastOutcome;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberCombat;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.vendor.hub.HubNpcs;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 4 foes (spec MILESTONE_4.md 10 and 17.1): the Mandalorian hunter (tells before every
 * attack, the jet hop, the flamer's cone, the rocket, the story yield, the anti-farm drops, the
 * net), the Mk IV security droid (resistances, shock, the one-time shield, the Proving's patrol),
 * the hollowhorn (neutral until struck or a calf is crowded, the charge stunned on a wall), the ash
 * hound (pack spots, the parried lunge) and Torvin Kaast (phases, the one Choke, the listed
 * interrupts, mines, scaling, the reset, the yield, the resolution hook, the daily rematch).
 *
 * <p>Every test runs in the lead's {@code oldrepublic_test:arena_24x7x24} fixture; walls and high
 * ground a test needs are built in the arena by the test itself. Players are listed mock players
 * ({@link M3TestPlayers}) where a rule reads {@code level.players()}, else Fabric fake players.
 * Whatever a test spawns or starts (Kaast fights, runs, barriers, nets, guards) is undone when it
 * ends, whichever step ends it ({@link Cleanup}); a test that could time out also cleans up just
 * before its limit. Kaast fights use a hand-made {@link CampLayout} inside the arena.
 */
public class FoesGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	/** Kaast's camp in the arena: the pit's centre on the dais, one nest at the north end, the gate's anchor to the south. */
	private static final BlockPos HEART = new BlockPos(12, 2, 12);
	private static final BlockPos NEST = new BlockPos(12, 1, 3);
	private static final BlockPos GATE = new BlockPos(12, 1, 20);

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

		/** Ends the player's Speed run, Barrier and Choke, frees them from a net, logs a listed player out. */
		<T extends ServerPlayer> T player(T player) {
			undo.add(() -> {
				ForceM4Feature.forgetAll(player, false);
				Netting.free(player, Netting.FREED_GONE);
				M3TestPlayers.remove(player);
			});
			return player;
		}

		void then(Runnable action) {
			undo.add(action);
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

	/** A cleanup whose last act sweeps the arena of foes and shots ({@link #sweep}). */
	private static Cleanup cleanup(GameTestHelper helper) {
		Cleanup cleanup = new Cleanup();
		cleanup.then(() -> sweep(helper));
		return cleanup;
	}

	/** The arena's volume, {@code height} blocks up from its floor (square, so the same however the test is rotated). */
	private static AABB arena(GameTestHelper helper, double height) {
		Vec3 centre = helper.absoluteVec(new Vec3(12, 0, 12));
		return new AABB(centre, centre).inflate(12.0, 0.0, 12.0).expandTowards(0.0, height, 0.0);
	}

	/** Removes every hunter, droid of Kaast's, mine, bolt, rocket and net left inside the arena. */
	private static void sweep(GameTestHelper helper) {
		AABB arena = arena(helper, 10);

		for (Entity entity : helper.getLevel().getEntitiesOfClass(Entity.class, arena, e -> e instanceof MandalorianHunter || e instanceof Rattle
				|| e instanceof CampMine || e instanceof BlasterBolt || e instanceof HunterRocket || e instanceof HunterNet)) {
			entity.discard();
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

	/** Succeeds on the first tick {@code check} passes (it is retried every tick); cleans up then, or at {@code deadline}. */
	private static void succeedWhen(GameTestHelper helper, Cleanup cleanup, long deadline, Runnable check) {
		helper.runAfterDelay(deadline, cleanup::run);
		helper.succeedWhen(() -> {
			check.run();
			cleanup.run();
		});
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-3;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	/** A listed player in survival who takes no damage (Resistance V), so foes treat them as a real target. */
	private static ServerPlayer survivor(GameTestHelper helper, Cleanup cleanup, BlockPos relative) {
		ServerPlayer player = cleanup.player(M3TestPlayers.listed(helper, relative));
		player.setGameMode(GameType.SURVIVAL);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 4));
		return player;
	}

	/** A listed player in creative: present for proximity rules, ignored by mines and beasts. */
	private static ServerPlayer onlooker(GameTestHelper helper, Cleanup cleanup, BlockPos relative) {
		ServerPlayer player = cleanup.player(M3TestPlayers.listed(helper, relative));
		player.setGameMode(GameType.CREATIVE);
		return player;
	}

	/** A survival fake player at {@code relative}, facing +z, awakened and knowing {@code abilities}, energy full. */
	private static FakePlayer caster(GameTestHelper helper, Cleanup cleanup, BlockPos relative, String... abilities) {
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "foes_test"));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(relative)));
		face(player, 0f, 0f);
		awaken(player, abilities);
		return cleanup.player(player);
	}

	/** Awakens {@code player} (Focus reached) with {@code abilities} known and full energy. */
	private static void awaken(ServerPlayer player, String... abilities) {
		ForceData data = Progression.get(player);
		data.questStage = QuestStage.FOCUS.ordinal();
		data.abilities.addAll(List.of(abilities));
		data.energy = data.maxEnergy;
		Progression.commit(player, data);
	}

	private static void refill(ServerPlayer player) {
		ForceData data = Progression.get(player);
		data.energy = data.maxEnergy;
		Progression.commit(player, data);
	}

	private static float energy(ServerPlayer player) {
		return Progression.get(player).energy;
	}

	private static void face(ServerPlayer player, float yaw, float pitch) {
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.setYHeadRot(yaw);
	}

	/** Turns {@code player}'s view toward {@code target} (powers aim along the view). */
	private static void aim(ServerPlayer player, Vec3 target) {
		Vec3 d = target.subtract(player.getEyePosition());
		float yaw = (float) (Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90f;
		float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * (180.0 / Math.PI));
		face(player, yaw, pitch);
	}

	/** Turns a mob to face +z, body and head. */
	private static void faceSouth(LivingEntity entity) {
		entity.setYRot(0f);
		entity.setXRot(0f);
		entity.yBodyRot = 0f;
		entity.yHeadRot = 0f;
	}

	private static ForceAbility power(GameTestHelper helper, String id) {
		ForceAbility ability = Abilities.get(id);
		helper.assertTrue(ability != null, "power " + id + " must be registered");
		return ability;
	}

	/** A wall of stone from {@code (x0, 1, z0)} to {@code (x1, top, z1)}, inclusive. */
	private static void wall(GameTestHelper helper, int x0, int z0, int x1, int z1, int top) {
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = 1; y <= top; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
	}

	private static MandalorianHunter hunter(GameTestHelper helper, Cleanup cleanup, BlockPos relative) {
		return cleanup.entity(helper.spawnWithNoFreeWill(HunterEntities.MANDALORIAN_HUNTER, relative));
	}

	/** The test camp: the dais is the pit, one nest, six mine spots 5 blocks out, the gate's cells when asked. */
	private static CampLayout camp(GameTestHelper helper, double radius, boolean gate) {
		BlockPos heart = helper.absolutePos(HEART);
		List<BlockPos> mines = new ArrayList<>();

		for (int i = 0; i < 6; i++) {
			double angle = i * Math.PI / 3;
			mines.add(heart.offset((int) Math.round(Math.cos(angle) * 5), -1, (int) Math.round(Math.sin(angle) * 5)));
		}

		List<BlockPos> gates = gate ? CampLayout.gateCells(heart, helper.absolutePos(GATE)) : List.of();
		return new CampLayout(heart, List.of(helper.absolutePos(NEST)), List.copyOf(mines), gates, radius);
	}

	/** Begins {@code owner}'s fight in {@code layout}; the cleanup resets it. */
	private static KaastSession fight(GameTestHelper helper, Cleanup cleanup, ServerPlayer owner, CampLayout layout) {
		KaastSession session = KaastFight.begin(owner.getUUID(), helper.getLevel(), layout, false, false);
		cleanup.then(() -> KaastFight.reset(owner));
		helper.assertTrue(session != null && session.kaast() != null, "the Kaast fight must begin");
		return session;
	}

	private static TorvinKaast kaast(GameTestHelper helper, KaastSession session) {
		TorvinKaast kaast = session.kaast();
		helper.assertTrue(kaast != null && kaast.isAlive(), "Kaast must be in the fight");
		return kaast;
	}

	/** The hunters Kaast called (fight spawns owned by the fight's owner), sent away at once so they never wander into another test. */
	private static int dismissCalledHunters(GameTestHelper helper, UUID owner) {
		AABB around = new AABB(helper.absolutePos(HEART)).inflate(24);
		List<MandalorianHunter> called = helper.getLevel().getEntitiesOfClass(MandalorianHunter.class, around,
				h -> !(h instanceof TorvinKaast) && h.isFightSpawn() && owner.equals(h.owner()));

		for (MandalorianHunter hunter : called) {
			hunter.discard();
		}

		return called.size();
	}

	/** A plain blaster bolt (not in the level) at {@code pos}, and its damage source. */
	private static DamageSource boltSource(ServerLevel level, @Nullable Entity shooter, Vec3 pos) {
		BlasterBolt bolt = BlasterBolt.create(level, shooter, pos, 4f, 20);
		return level.damageSources().source(EnemyEntities.BLASTER_DAMAGE, bolt, shooter);
	}

	// ---------------------------------------------------------------- the Mandalorian hunter (10.1)

	/**
	 * What one hunter showed and did, tick by tick: every bolt needs the rifle's aim tell just
	 * before it (the second of a burst within the burst gap), every flame, jet hop and rocket its own
	 * gadget tell, held for that tell's length and ended in the tick the gadget acts.
	 */
	private static final class TellLog {
		private final MandalorianHunter hunter;
		private final HunterBalance b = HunterBalance.get();
		private final Set<Integer> seen = new HashSet<>();
		private int chargeRun;
		private int lastChargeRun;
		private int sinceCharge = 1000;
		private int gadgetRun;
		private int gadgetKind = MandalorianHunter.GADGET_NONE;
		private int lastGadgetRun;
		private int lastGadgetKind = MandalorianHunter.GADGET_NONE;
		private int sinceGadget = 1000;
		private boolean wasFlaming;
		private boolean wasJetting;
		private int bolts;
		private int gadgets;

		TellLog(MandalorianHunter hunter) {
			this.hunter = hunter;
		}

		/** One tick's look; returns the broken rule, or null. */
		@Nullable String observe(ServerLevel level) {
			if (hunter.isCharging()) {
				chargeRun++;
				sinceCharge = 0;
			} else {
				if (chargeRun > 0) {
					lastChargeRun = chargeRun;
					chargeRun = 0;
				}

				sinceCharge++;
			}

			int gadget = hunter.gadget();

			if (gadget == MandalorianHunter.GADGET_NONE) {
				if (gadgetRun > 0) {
					lastGadgetRun = gadgetRun;
					lastGadgetKind = gadgetKind;
					gadgetRun = 0;
				}

				sinceGadget++;
			} else {
				if (gadget != gadgetKind) {
					gadgetRun = 0;
				}

				gadgetKind = gadget;
				gadgetRun++;
				sinceGadget = 0;
			}

			boolean flaming = hunter.isFlaming();
			String broken = flaming && !wasFlaming ? gadgetAct(MandalorianHunter.GADGET_FLAMER, b.flamerTellTicks, "the flame") : null;
			wasFlaming = flaming;

			boolean jetting = hunter.isJetting();

			if (broken == null && jetting && !wasJetting) {
				broken = gadgetAct(MandalorianHunter.GADGET_JET, b.jetTellTicks, "the jet hop");
			}

			wasJetting = jetting;
			AABB around = hunter.getBoundingBox().inflate(8);

			for (HunterRocket rocket : level.getEntitiesOfClass(HunterRocket.class, around, r -> r.getOwner() == hunter && seen.add(r.getId()))) {
				broken = broken != null ? broken : gadgetAct(MandalorianHunter.GADGET_ROCKET, b.rocketTellTicks, "rocket " + rocket.getId());
			}

			for (BlasterBolt bolt : level.getEntitiesOfClass(BlasterBolt.class, around, x -> x.getOwner() == hunter && seen.add(x.getId()))) {
				if (lastChargeRun >= b.rifleTellTicks - 2 && sinceCharge <= b.burstGapTicks + 3) {
					bolts++;
				} else if (broken == null) {
					broken = "bolt " + bolt.getId() + " without the rifle tell (last aim " + lastChargeRun + " ticks, " + sinceCharge + " ticks ago)";
				}
			}

			return broken;
		}

		private @Nullable String gadgetAct(int kind, int tell, String what) {
			if (lastGadgetKind == kind && lastGadgetRun >= tell - 2 && sinceGadget <= 3) {
				gadgets++;
				return null;
			}

			return what + " came without its tell (last tell " + lastGadgetKind + " for " + lastGadgetRun + " ticks, " + sinceGadget + " ticks ago)";
		}
	}

	@GameTest(structure = ARENA, maxTicks = 200)
	public void hunterTellsPrecedeAttacks(GameTestHelper helper) {
		HunterBalance d = new HunterBalance();
		helper.assertTrue(d.rifleTellTicks == 10 && d.burst == 2 && d.flamerTellTicks > 0 && d.jetTellTicks > 0 && d.rocketTellTicks > 0,
				"every attack has a tell: the rifle's is 10 ticks before a burst of 2");
		helper.assertTrue(HunterRules.flamerDue(d, d.flamerCooldownTicks, 3) && !HunterRules.flamerDue(d, d.flamerCooldownTicks, 4.5)
				&& !HunterRules.flamerDue(d, d.flamerCooldownTicks - 1, 3), "the flamer: under 4 blocks, once it has cooled");
		helper.assertTrue(HunterRules.hopDue(d, d.jetIntervalTicks, 12) && HunterRules.hopDue(d, d.jetMinGapTicks, 3)
				&& !HunterRules.hopDue(d, d.jetIntervalTicks - 1, 12) && !HunterRules.hopDue(d, d.jetMinGapTicks - 1, 3),
				"the hop: every 200 ticks, or when rushed but never twice within 60");
		helper.assertTrue(HunterRules.rocketDue(d, d.rocketIntervalTicks, 12) && !HunterRules.rocketDue(d, d.rocketIntervalTicks, 6)
				&& !HunterRules.rocketDue(d, d.rocketIntervalTicks, 21) && !HunterRules.rocketDue(d, d.rocketIntervalTicks - 1, 12),
				"the rocket: every 300 ticks at 8 to 20 blocks");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		// Backstops behind both targets and a wall behind the close hunter, so no stray shot leaves the arena.
		wall(helper, 1, 18, 7, 18, 5);
		wall(helper, 16, 9, 22, 9, 5);
		wall(helper, 16, 17, 22, 17, 3);
		// Fake players, not listed mock players: Mob#setTarget and #getTarget drop creative players (asValidTarget), and a
		// mock player always answers isCreative() true, so a hunter could never hold a mock player as its target.
		FakePlayer far = caster(helper, cleanup, new BlockPos(4, 1, 15));
		FakePlayer close = caster(helper, cleanup, new BlockPos(19, 1, 12));
		MandalorianHunter rifleman = cleanup.entity(helper.spawn(HunterEntities.MANDALORIAN_HUNTER, new BlockPos(4, 1, 4)));
		MandalorianHunter flamer = cleanup.entity(helper.spawn(HunterEntities.MANDALORIAN_HUNTER, new BlockPos(19, 1, 15)));
		TellLog rifleLog = new TellLog(rifleman);
		TellLog flamerLog = new TellLog(flamer);

		helper.onEachTick(() -> guarded(cleanup, () -> {
			if (rifleman.isAlive() && rifleman.getTarget() != far) {
				rifleman.setTarget(far);
			}

			if (flamer.isAlive() && flamer.getTarget() != close) {
				flamer.setTarget(close);
			}

			String broken = rifleLog.observe(level);
			helper.assertTrue(broken == null, "the rifleman broke the tell rule: " + broken);
			broken = flamerLog.observe(level);
			helper.assertTrue(broken == null, "the close hunter broke the tell rule: " + broken);
		}));
		succeedWhen(helper, cleanup, 198, () -> {
			helper.assertTrue(rifleLog.bolts >= d.burst, "the rifleman fires a full burst after its aim tell (" + rifleLog.bolts + " bolts so far)");
			helper.assertTrue(flamerLog.gadgets >= 1, "the close hunter uses a gadget after that gadget's tell");
		});
	}

	// skyAccess: without it the test is roofed with barrier blocks, the heightmap's top is that roof and every hop spot lands on it.
	@GameTest(structure = ARENA, maxTicks = 60, skyAccess = true)
	public void hunterJetHopToCover(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);

		// High ground: a ring of stone 3 high from 5 to 11 blocks around the dais, open air above it.
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				double distance = Math.hypot(x - 12, z - 12);

				if (distance >= 5.0 && distance <= 11.0) {
					for (int y = 1; y <= 6; y++) {
						helper.setBlock(new BlockPos(x, y, z), y <= 3 ? Blocks.STONE : Blocks.AIR);
					}
				}
			}
		}

		MandalorianHunter hunter = hunter(helper, cleanup, new BlockPos(12, 2, 12));
		MandalorianHunter pushed = hunter(helper, cleanup, new BlockPos(1, 1, 1));
		double highGround = helper.absolutePos(new BlockPos(0, 4, 0)).getY();
		Vec3 landing = helper.absoluteVec(new Vec3(1.5, 1, 7.5));
		List<Vec3> spots = new ArrayList<>();

		guarded(cleanup, () -> {
			for (int i = 0; i < 6; i++) {
				Vec3 spot = hunter.findHopSpot(level, null, 6, 10);
				helper.assertTrue(spot != null, "the hunter finds a spot to hop to");
				double across = horizontal(spot, hunter.position());
				helper.assertTrue(spot.y >= highGround - 1.0e-6 && across >= 5.0 && across <= 11.0,
						"the hop lands on the high ground 6 to 10 blocks out, got " + spot + " at " + across);
				spots.add(spot);
			}

			hunter.startHop(level, spots.get(0), 16, 1);
			pushed.startHop(level, landing, 16, 3);
		});
		step(helper, cleanup, 3, () -> {
			helper.assertTrue(hunter.isJetting() && hunter.isHopping() && pushed.isJetting(), "both hunters are up on their jets");
			pushed.onPushedInAir();
			helper.assertTrue(!pushed.isJetting() && !pushed.isHopping(), "a Push in the air ends the hop");
		});
		last(helper, cleanup, 30, () -> {
			Vec3 spot = spots.get(0);
			helper.assertTrue(!hunter.isJetting() && hunter.onGround(), "the hop is over and the hunter stands");
			helper.assertTrue(horizontal(hunter.position(), spot) < 1.0 && Math.abs(hunter.getY() - spot.y) < 0.6,
					"the hunter landed on the high ground at " + spot + ", stands at " + hunter.position());
			helper.assertTrue(pushed.onGround() && pushed.hasEffect(MobEffects.SLOWNESS), "the pushed hunter fell and is staggered on landing");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void flamerConeAndBarrierHalves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		HunterBalance d = new HunterBalance();
		helper.assertTrue(near(d.flamerConeLength, 3) && near(d.flamerConeDegrees, 30) && near(d.flamerDamage, 2) && d.flamerDamageInterval == 5,
				"the flamer: a 3-block cone of 30 degrees, 2 damage every 5 ticks");

		MandalorianHunter hunter = hunter(helper, cleanup, new BlockPos(8, 1, 4));
		Mob front = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(8, 1, 7)));
		Mob behind = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(8, 1, 1)));
		Mob aside = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(10, 1, 5)));
		FakePlayer guard = caster(helper, cleanup, new BlockPos(20, 1, 4), ForceBarrier.ID);

		guarded(cleanup, () -> {
			faceSouth(hunter);
			DamageSource flame = level.damageSources().source(HunterDamage.WRIST_FLAMER);
			helper.assertTrue(near(ForceBarrier.damageMultiplier(new BarrierBalance(), flame, guard.position(), 2), 0.5),
					"a barrier halves flame (the wrist flamer counts as flame)");
			power(helper, ForceBarrier.ID).onInput(guard, AbilityAction.START);
			helper.assertTrue(ForceBarrier.isChannelling(guard), "the barrier is up");
			helper.assertTrue(near(ForceBarrier.modifyDamage(guard, flame, 4f), 2f), "the flamer's 4 damage comes through a barrier as 2");
			helper.assertTrue(near(ForceBarrier.modifyDamage(guard, level.damageSources().magic(), 4f), 4f), "magic is not flame");
		});
		step(helper, cleanup, 1, () -> {
			faceSouth(hunter);
			hunter.startFlame(level, d.flamerTicks, d.flamerDamage, d.flamerDamageInterval, d.flamerConeLength, d.flamerConeDegrees, d.flamerFireSeconds);
			helper.assertTrue(hunter.isFlaming(), "the flame is lit");
		});
		last(helper, cleanup, 4, () -> {
			helper.assertTrue(front.getHealth() < front.getMaxHealth() && front.isOnFire(), "the pig in the cone is burned and set alight");
			helper.assertTrue(near(behind.getHealth(), behind.getMaxHealth()) && !behind.isOnFire(), "the pig behind the hunter is untouched");
			helper.assertTrue(near(aside.getHealth(), aside.getMaxHealth()), "the pig off to the side, outside the 30 degrees, is untouched");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void rocketNotSaberDeflectableButPushable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		ServerPlayer guard = cleanup.player(M3TestPlayers.listed(helper, new BlockPos(2, 1, 2)));
		MandalorianHunter shooter = hunter(helper, cleanup, new BlockPos(2, 1, 9));
		FakePlayer pusher = caster(helper, cleanup, new BlockPos(20, 1, 4), ForcePush.ABILITY_ID);
		HunterBalance d = HunterBalance.get();
		cleanup.then(() -> {
			guard.stopUsingItem();
			SaberGuard.forget(guard);
		});

		try {
			face(guard, 0f, 0f);
			ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(guard.getUUID(), "Tester", BladeColor.BLUE.rgb()), BladeColor.BLUE,
					Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
			SaberToggle.setIgnited(saber, true, level.getGameTime());
			guard.setItemInHand(InteractionHand.MAIN_HAND, saber);
			guard.startUsingItem(InteractionHand.MAIN_HAND);
			helper.assertTrue(!SaberGuard.guardingSaber(guard).isEmpty(), "the player must be guarding");

			HunterRocket blocked = cleanup.entity(HunterRocket.launch(level, shooter, guard.position().add(0, 1.2, 0.4), new Vec3(0, 0, -1),
					d.rocketSpeed, d.rocketDamage, d.rocketRadius, d.rocketLifetimeTicks));
			helper.assertTrue(!blocked.saberDeflectable() && near(blocked.guardDrain(), 30f), "the rocket is not saber-deflectable and drains 30 guard");
			Vec3 before = blocked.getDeltaMovement();
			blocked.redirect(new Vec3(0, 0, 1), guard, true);
			helper.assertTrue(blocked.getDeltaMovement().equals(before) && blocked.getOwner() == shooter, "a saber never redirects it");
			CombatHooks.DeflectResult result = CombatHooks.onProjectileHit(guard, blocked);
			helper.assertTrue(result == CombatHooks.DeflectResult.BLOCKED, "a saber guard only blocks the rocket, got " + result);

			HunterRocket returned = cleanup.entity(HunterRocket.launch(level, shooter, helper.absoluteVec(new Vec3(12.5, 3.2, 6.5)), new Vec3(0, 0, -1),
					d.rocketSpeed, d.rocketDamage, d.rocketRadius, d.rocketLifetimeTicks));
			helper.assertTrue(returned.onForcePush(pusher, new Vec3(0, 0, 1), 1f), "Push answers the rocket");
			helper.assertTrue(returned.returned() && returned.getOwner() == pusher, "pushed back, the rocket is the caster's");
			helper.assertTrue(returned.getDeltaMovement().subtract(new Vec3(0, 0, d.rocketPushSpeed)).length() < 1.0e-3,
					"it flies back along the Push at " + d.rocketPushSpeed + " blocks a tick");

			HunterRocket stopped = cleanup.entity(HunterRocket.launch(level, shooter, helper.absoluteVec(new Vec3(20.5, 3.2, 20.5)), new Vec3(0, 0, -1),
					d.rocketSpeed, d.rocketDamage, d.rocketRadius, d.rocketLifetimeTicks));
			stopped.onBarrier(pusher, stopped.position(), new Vec3(0, 0, 1));
			helper.assertTrue(stopped.isRemoved(), "a barrier bursts the rocket at its surface");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void hunterYieldDialogue(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		MandalorianHunter first = hunter(helper, cleanup, new BlockPos(4, 1, 4));
		MandalorianHunter second = hunter(helper, cleanup, new BlockPos(8, 1, 4));
		MandalorianHunter bounty = hunter(helper, cleanup, new BlockPos(12, 1, 4));
		MandalorianHunter called = hunter(helper, cleanup, new BlockPos(16, 1, 4));
		FakePlayer attacker = caster(helper, cleanup, new BlockPos(6, 1, 8));

		try {
			helper.assertTrue(HunterRules.ambushOf(2, true) == MandalorianHunter.AMBUSH_TWO && HunterRules.ambushOf(2, false) == MandalorianHunter.AMBUSH_ONE
					&& HunterRules.ambushOf(1, false) == MandalorianHunter.AMBUSH_NONE, "a lone hunter is a bounty, a group an ambush");
			UUID group = UUID.randomUUID();
			first.joinAmbush(group, MandalorianHunter.AMBUSH_TWO);
			second.joinAmbush(group, MandalorianHunter.AMBUSH_TWO);
			helper.assertTrue(!first.canYield() && !second.canYield(), "while another of the ambush stands, neither yields");
			second.discard();
			helper.assertTrue(first.isLastOfGroup() && first.canYield(), "the last hunter of a story ambush may yield");

			float max = first.getMaxHealth();
			helper.assertTrue(!first.hurtServer(level, level.damageSources().playerAttack(attacker), max - 1f), "the blow that would fell it is taken by the yield");
			helper.assertTrue(first.isYielded() && first.isAlive() && near(first.getHealth(), max * first.yieldHealthFraction()),
					"it kneels at 20% health, at " + first.getHealth() + " of " + max);
			helper.assertTrue(MandalorianHunter.YIELD_DIALOGUE.equals(first.yieldDialogue()), "its yield opens the hunter's own dialogue");

			helper.assertTrue(!bounty.canYield(), "a bounty hunter never yields");
			called.joinAmbush(UUID.randomUUID(), MandalorianHunter.AMBUSH_ONE);
			called.markFightSpawn();
			helper.assertTrue(!called.canYield(), "a hunter Kaast called into his fight never yields");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void noFarmDropsFromBounties(GameTestHelper helper) {
		UUID owner = UUID.randomUUID();
		UUID stranger = UUID.randomUUID();
		helper.assertTrue(!HunterRules.dropsLoot(true, null, stranger, false), "a hunter Kaast called never drops");
		helper.assertTrue(HunterRules.dropsLoot(false, null, stranger, false), "an unowned hunter drops for anyone");
		helper.assertTrue(!HunterRules.dropsLoot(false, owner, stranger, false), "another player's bounty drops nothing for a stranger");
		helper.assertTrue(HunterRules.dropsLoot(false, owner, owner, false) && HunterRules.dropsLoot(false, owner, stranger, true),
				"it drops for its owner, or when the owner fought beside the killer");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		ServerPlayer killer = onlooker(helper, cleanup, new BlockPos(12, 1, 20));
		ServerPlayer partner = onlooker(helper, cleanup, new BlockPos(14, 1, 20));
		MandalorianHunter theirs = hunter(helper, cleanup, new BlockPos(2, 1, 16));
		MandalorianHunter mine = hunter(helper, cleanup, new BlockPos(5, 1, 16));
		MandalorianHunter unowned = hunter(helper, cleanup, new BlockPos(8, 1, 16));
		MandalorianHunter called = hunter(helper, cleanup, new BlockPos(16, 1, 16));
		MandalorianHunter beside = hunter(helper, cleanup, new BlockPos(20, 1, 16));
		List<MandalorianHunter> bounties = new ArrayList<>();

		guarded(cleanup, () -> {
			StoryTags.setOwner(theirs, stranger);
			StoryTags.setOwner(mine, killer.getUUID());
			StoryTags.setOwner(beside, partner.getUUID());
			called.markFightSpawn();

			for (MandalorianHunter h : List.of(theirs, mine, unowned, called, beside)) {
				h.hurtServer(level, level.damageSources().playerAttack(killer), 1f);
			}

			helper.assertTrue(!theirs.mayDropLoot(level), "another player's bounty struck by a stranger may not drop");
			helper.assertTrue(mine.mayDropLoot(level) && unowned.mayDropLoot(level), "the owner's own kill and an unowned hunter may drop");
			helper.assertTrue(!called.mayDropLoot(level), "a hunter Kaast called may not drop");
			helper.assertTrue(beside.mayDropLoot(level), "a bounty whose owner fights beside the killer may drop");

			for (int i = 0; i < 8; i++) {
				MandalorianHunter bounty = hunter(helper, cleanup, new BlockPos(2 + i * 2, 1, 6));
				StoryTags.setOwner(bounty, stranger);
				bounties.add(bounty);
				bounty.hurtServer(level, level.damageSources().playerAttack(killer), 1000f);
			}
		});
		last(helper, cleanup, 3, () -> {
			helper.assertTrue(bounties.stream().noneMatch(LivingEntity::isAlive), "the stranger's bounties are dead");
			AABB arena = arena(helper, 8);
			List<ItemEntity> loot = level.getEntitiesOfClass(ItemEntity.class, arena,
					item -> item.getItem().is(M3Items.DURASTEEL_PLATE) || item.getItem().is(M4Items.JET_PACK));
			helper.assertTrue(loot.isEmpty(), "killing another player's bounties drops no plate and no jet pack, found " + loot.size());

			for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, arena, e -> true)) {
				drop.discard();
			}
		});
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void netEndsSpeedBarrierJump(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		ForceAbility speed = power(helper, ForceSpeed.ID);
		ForceAbility barrier = power(helper, ForceBarrier.ID);
		ServerPlayer runner = survivor(helper, cleanup, new BlockPos(4, 1, 4));
		ServerPlayer shielded = survivor(helper, cleanup, new BlockPos(12, 1, 4));
		ServerPlayer caught = survivor(helper, cleanup, new BlockPos(20, 1, 4));
		MandalorianHunter atRunner = hunter(helper, cleanup, new BlockPos(4, 1, 9));
		MandalorianHunter atShielded = hunter(helper, cleanup, new BlockPos(12, 1, 9));
		// Inside the barrier's sphere already: a net thrown from there never crosses the surface.
		MandalorianHunter inside = hunter(helper, cleanup, new BlockPos(20, 1, 5));
		KaastBalance k = new KaastBalance();

		guarded(cleanup, () -> {
			awaken(runner, ForceSpeed.ID, ForceBarrier.ID, ForceJump.ID, ForcePush.ABILITY_ID);
			awaken(shielded, ForceBarrier.ID);
			awaken(caught, ForceBarrier.ID);
			speed.onInput(runner, AbilityAction.START);
			barrier.onInput(shielded, AbilityAction.START);
			barrier.onInput(caught, AbilityAction.START);
			helper.assertTrue(ForceSpeed.isRunning(runner) && ForceBarrier.isChannelling(shielded) && ForceBarrier.isChannelling(caught),
					"the run and both barriers are up");
			cleanup.entity(HunterNet.throwAt(level, atRunner, runner, k.netRootTicks, k.netSaberHits));
			cleanup.entity(HunterNet.throwAt(level, atShielded, shielded, k.netRootTicks, k.netSaberHits));
			cleanup.entity(HunterNet.throwAt(level, inside, caught, k.netRootTicks, k.netSaberHits));
		});
		last(helper, cleanup, 16, () -> {
			helper.assertTrue(Netting.isNetted(runner), "the net catches the runner");
			helper.assertTrue(!ForceSpeed.isRunning(runner), "the net ends Force Speed");
			helper.assertTrue(near(runner.getAttributeValue(Attributes.JUMP_STRENGTH), 0) && near(runner.getAttributeValue(Attributes.MOVEMENT_SPEED), 0),
					"netted, the player can neither move nor jump");
			helper.assertTrue(AbilityLocks.refusal(runner, ForceJump.ID) != null && AbilityLocks.refusal(runner, ForceSpeed.ID) != null
					&& AbilityLocks.refusal(runner, ForceBarrier.ID) != null, "netted, no Force Jump, Speed or Barrier");
			barrier.onInput(runner, AbilityAction.START);
			helper.assertTrue(!ForceBarrier.isChannelling(runner), "a Barrier cannot be raised in the net");

			helper.assertTrue(!Netting.isNetted(shielded) && ForceBarrier.isChannelling(shielded), "a raised Barrier makes a thrown net fall short");
			helper.assertTrue(Netting.isNetted(caught) && !ForceBarrier.isChannelling(caught), "a net that lands ends the Barrier");

			helper.assertTrue(AbilityLocks.refusal(runner, ForcePush.ABILITY_ID) == null, "Push is the way out of the net");
			helper.assertTrue(!Netting.isNetted(runner) && !near(runner.getAttributeValue(Attributes.JUMP_STRENGTH), 0), "the Push broke the net");
		});
	}

	// ---------------------------------------------------------------- the security droid (10.2)

	@GameTest(structure = ARENA, maxTicks = 20)
	public void droidResistsBlasterAndImmuneToChoke(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		SecurityDroid droid = cleanup.entity(helper.spawnWithNoFreeWill(DroidEntities.SECURITY_DROID, new BlockPos(4, 1, 10)));
		SeekerDroid seeker = cleanup.entity(helper.spawnWithNoFreeWill(DroidEntities.SEEKER_DROID, new BlockPos(8, 1, 10)));
		Rattle rattle = cleanup.entity(helper.spawnWithNoFreeWill(KaastEntities.KAAST_DROID, new BlockPos(16, 1, 10)));
		Mob poacher = cleanup.entity(helper.spawnWithNoFreeWill(EnemyEntities.CRYSTAL_POACHER, new BlockPos(20, 1, 10)));
		FakePlayer caster = caster(helper, cleanup, new BlockPos(4, 1, 4), ForceChoke.ID);
		DroidReactions reactions = new DroidReactions();

		try {
			DamageSource bolt = boltSource(level, poacher, droid.position().add(0, 1.2, 2));
			BlasterBolt back = BlasterBolt.create(level, poacher, droid.position().add(0, 1.2, 2), 4f, 20);
			back.redirect(new Vec3(0, 0, -1), caster, true);
			DamageSource parried = level.damageSources().source(EnemyEntities.BLASTER_DAMAGE, back, caster);
			helper.assertTrue(near(reactions.modify(droid, bolt, 10f), 5f), "blaster bolts deal half to the droid");
			helper.assertTrue(near(reactions.modify(droid, parried, 10f), 15f), "a bolt a saber parried back deals 150%");
			helper.assertTrue(near(reactions.modify(poacher, bolt, 10f), 10f), "other creatures are not affected");
			helper.assertTrue(near(DamageModifiers.apply(droid, bolt, 10f), 5f), "the resistance is registered");

			helper.assertTrue(!ForceChoke.breathes(droid) && !ForceChoke.breathes(seeker) && !ForceChoke.breathes(rattle), "droids do not breathe");
			aim(caster, ForcePowers.chest(droid));
			float before = energy(caster);
			power(helper, ForceChoke.ID).onInput(caster, AbilityAction.START);
			helper.assertTrue(!ForceChoke.isChoking(caster) && near(energy(caster), before), "Choke on the droid is refused and costs nothing");

			helper.assertTrue(!droid.hurtServer(level, level.damageSources().source(HunterDamage.WRIST_FLAMER), 4f) && droid.fireImmune(),
					"the flamer and fire do nothing to it");
			helper.assertTrue(!droid.canBeAffected(new MobEffectInstance(MobEffects.POISON, 100)) && !droid.canBeAffected(new MobEffectInstance(MobEffects.WITHER, 100)),
					"poison and wither do nothing to it");
			helper.assertTrue(near(ForceReactions.liftHeight(droid, 3f), 0) && near(ForceReactions.liftHeight(seeker, 3f), 0), "Lift cannot raise a droid");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	@GameTest(structure = ARENA, maxTicks = 50)
	public void droidShockWeakness(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		SecurityDroid droid = cleanup.entity(helper.spawnWithNoFreeWill(DroidEntities.SECURITY_DROID, new BlockPos(6, 1, 10)));
		Mob poacher = cleanup.entity(helper.spawnWithNoFreeWill(EnemyEntities.CRYSTAL_POACHER, new BlockPos(18, 1, 10)));
		FakePlayer caster = caster(helper, cleanup, new BlockPos(6, 1, 4));
		DroidReactions reactions = new DroidReactions();

		guarded(cleanup, () -> {
			DamageSource lightning = level.damageSources().source(ForceDamage.LIGHTNING);
			helper.assertTrue(near(reactions.modify(droid, lightning, 4f), 6f), "Force Lightning deals 150% to the droid");
			helper.assertTrue(near(reactions.modify(poacher, lightning, 4f), 4f), "and the usual to others");
			helper.assertTrue(near(DamageModifiers.apply(droid, lightning, 4f), 6f), "the weakness is registered");
			helper.assertTrue(!droid.isStaggered(), "a fresh droid is not staggered");
			int ticks = ControlEffects.stagger(caster, droid, 30, 1, ControlEffects.SHOCK);
			helper.assertTrue(ticks > 0 && droid.isStaggered(), "a shock staggers it: no spin, no volley");
		});
		last(helper, cleanup, 40, () -> helper.assertTrue(!droid.isStaggered(), "the stagger wears off"));
	}

	@GameTest(structure = ARENA, maxTicks = 130)
	public void droidShieldOnceBelowHalf(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		SecurityDroid droid = cleanup.entity(helper.spawnWithNoFreeWill(DroidEntities.SECURITY_DROID, new BlockPos(12, 1, 6)));
		long[] raisedAt = {-1};

		step(helper, cleanup, 1, () -> {
			faceSouth(droid);
			helper.assertTrue(!droid.isShielded() && !droid.shieldUsed(), "no shield at full health");
			droid.setHealth(droid.getMaxHealth() * 0.5f + 1f);
			droid.hurtServer(level, level.damageSources().magic(), 2f);
			helper.assertTrue(droid.isShielded() && droid.shieldUsed(), "the shield comes up once the droid falls to half health");
			raisedAt[0] = level.getGameTime();

			float health = droid.getHealth();
			droid.invulnerableTime = 0;
			helper.assertTrue(!droid.hurtServer(level, boltSource(level, null, droid.position().add(0, 1.2, 3)), 4f) && near(droid.getHealth(), health),
					"a bolt from the front stops on the shield");
			droid.invulnerableTime = 0;
			helper.assertTrue(droid.hurtServer(level, boltSource(level, null, droid.position().add(0, 1.2, -3)), 4f) && droid.getHealth() < health,
					"a bolt from behind gets past it");
		});
		last(helper, cleanup, 110, () -> {
			helper.assertTrue(!droid.isShielded(), "the shield drops after 100 ticks (raised at " + raisedAt[0] + ")");
			droid.invulnerableTime = 0;
			droid.setHealth(10f);
			droid.hurtServer(level, level.damageSources().magic(), 1f);
			helper.assertTrue(!droid.isShielded() && droid.shieldUsed(), "it comes up only once a life");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void hubPatrolDroidSparesSith(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		FakePlayer sith = caster(helper, cleanup, new BlockPos(4, 1, 4));
		FakePlayer jedi = caster(helper, cleanup, new BlockPos(20, 1, 4));

		try {
			Identity.setPath(sith, Path.SITH, true);
			Identity.setPath(jedi, Path.JEDI, true);
			LivingEntity patrol = Droids.spawnHubPatrol(level, helper.absolutePos(new BlockPos(8, 1, 10)));
			helper.assertTrue(patrol instanceof SecurityDroid, "the Proving's patrol is a security droid");
			cleanup.entity(patrol);
			LivingEntity escort = Droids.spawnSecurity(level, helper.absolutePos(new BlockPos(16, 1, 10)), null, false);
			helper.assertTrue(escort instanceof SecurityDroid, "an escort droid spawns");
			cleanup.entity(escort);

			SecurityDroid guard = (SecurityDroid) patrol;
			helper.assertTrue(guard.isImperialGuard() && guard.entityTags().contains(HubNpcs.HUB_TAG), "an Imperial guard droid, tagged as hub staff");
			helper.assertTrue(Attitudes.toward(guard, sith) == Attitude.NEUTRAL, "never hostile to a Sith");
			DroidReactions reactions = new DroidReactions();
			helper.assertTrue(reactions.toward(guard, jedi) == null && reactions.toward(escort, sith) == null,
					"the rule spares only Sith, and only from Imperial droids");
			helper.succeed();
		} finally {
			cleanup.run();
		}
	}

	// ---------------------------------------------------------------- the hollowhorn and the ash hound (10.3, 10.4)

	@GameTest(structure = ARENA, maxTicks = 70)
	public void hollowhornNeutralUntilHitOrCalf(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		// Clear of the arena's lodestone pylon at (5, 5): a hollowhorn at (4, 1, 4) has its head in the amethyst, takes a
		// point of suffocation every 10 ticks and stays in hurt cooldown, so a 1-point strike would not land.
		Hollowhorn first = cleanup.entity(helper.spawnWithNoFreeWill(CreatureEntities.HOLLOWHORN, new BlockPos(3, 1, 4)));
		Hollowhorn second = cleanup.entity(helper.spawnWithNoFreeWill(CreatureEntities.HOLLOWHORN, new BlockPos(4, 1, 7)));
		ServerPlayer walker = survivor(helper, cleanup, new BlockPos(4, 1, 10));
		List<Hollowhorn> herd = new ArrayList<>();
		List<ServerPlayer> visitors = new ArrayList<>();

		step(helper, cleanup, 25, () -> {
			helper.assertTrue(!first.isAngryAt(walker) && !second.isAngryAt(walker) && first.getTarget() == null,
					"grown hollowhorns ignore a player walking by");
			helper.assertTrue(first.hurtServer(level, level.damageSources().playerAttack(walker), 1f), "the player strikes one");
			helper.assertTrue(first.isProvokedBy(walker) && second.isProvokedBy(walker), "struck, the whole herd turns on the player");

			// Beside the lodestone pylon at (18, 18), not inside it.
			Hollowhorn calf = cleanup.entity(helper.spawnWithNoFreeWill(CreatureEntities.HOLLOWHORN, new BlockPos(20, 1, 20)));
			calf.setCalf(true);
			herd.add(calf);
			herd.add(cleanup.entity(helper.spawnWithNoFreeWill(CreatureEntities.HOLLOWHORN, new BlockPos(18, 1, 15))));
			visitors.add(survivor(helper, cleanup, new BlockPos(18, 1, 21)));
			visitors.add(onlooker(helper, cleanup, new BlockPos(21, 1, 18)));
		});
		last(helper, cleanup, 50, () -> {
			Hollowhorn mother = herd.get(1);
			ServerPlayer crowding = visitors.get(0);
			ServerPlayer creative = visitors.get(1);
			helper.assertTrue(herd.get(0).isCalf(), "the calf is a calf");
			helper.assertTrue(mother.isAngryAt(crowding) && !mother.isProvokedBy(crowding), "a player crowding a calf angers the herd, unprovoked");
			helper.assertTrue(!mother.isAngryAt(creative), "a creative player is ignored");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 140)
	public void hollowhornChargeStunsOnWall(GameTestHelper helper) {
		CreatureBalance d = new CreatureBalance();
		helper.assertTrue(CreatureRules.chargeDue(d, 8, 0, true, false) && !CreatureRules.chargeDue(d, 3, 0, true, false)
				&& !CreatureRules.chargeDue(d, 8, 0, true, true) && !CreatureRules.chargeDue(d, 8, 0, false, false),
				"it charges from 4 to 14 blocks with a clear view, never as a calf");
		helper.assertTrue(near(CreatureRules.hollowhornDamage(d, 4f, false), 4f) && near(CreatureRules.hollowhornDamage(d, 4f, true), 6f),
				"stunned, it takes 150% damage");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		wall(helper, 3, 7, 9, 7, 1);
		Hollowhorn bull = cleanup.entity(helper.spawn(CreatureEntities.HOLLOWHORN, new BlockPos(6, 1, 3)));
		// A villager, not a mock player: Mob#setTarget drops creative players (asValidTarget) and a mock player always answers
		// isCreative() true; a fake player is not in the level, so the herd's anger (kept by UUID) could not find it.
		Mob target = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(6, 1, 11)));
		List<Integer> states = new ArrayList<>();
		float[] stunnedLoss = {-1f};

		guarded(cleanup, () -> bull.angerHerd(level, target, true));
		helper.onEachTick(() -> guarded(cleanup, () -> {
			int state = bull.state();

			if (states.isEmpty() || states.get(states.size() - 1) != state) {
				states.add(state);
			}

			if (state == Hollowhorn.STATE_STUNNED && stunnedLoss[0] < 0f) {
				bull.invulnerableTime = 0;
				float before = bull.getHealth();
				bull.hurtServer(level, level.damageSources().magic(), 4f);
				stunnedLoss[0] = before - bull.getHealth();
			}
		}));
		succeedWhen(helper, cleanup, 138, () -> {
			helper.assertTrue(stunnedLoss[0] >= 0f, "the charge ends stunned against the wall (states so far " + states + ")");
			int tell = states.indexOf(Hollowhorn.STATE_TELL);
			int charge = states.indexOf(Hollowhorn.STATE_CHARGING);
			int stunned = states.indexOf(Hollowhorn.STATE_STUNNED);
			helper.assertTrue(tell >= 0 && tell < charge && charge < stunned, "tell, then charge, then the stun: " + states);
			helper.assertTrue(Math.abs(stunnedLoss[0] - 6f) < 0.01f, "a 4-point hit on the stunned hollowhorn deals 6, dealt " + stunnedLoss[0]);
		});
	}

	@GameTest(structure = ARENA, maxTicks = 60)
	public void ashHoundPackFlanksAndLungeParried(GameTestHelper helper) {
		helper.assertTrue(CreatureRules.role(0) == CreatureRules.Role.FRONT && CreatureRules.role(1) == CreatureRules.Role.FLANK_LEFT
				&& CreatureRules.role(2) == CreatureRules.Role.FLANK_RIGHT && CreatureRules.role(3) == CreatureRules.Role.FLANK_LEFT,
				"one hound holds the front, the rest take the flanks in turn");
		Vec3 target = new Vec3(10, 64, 10);
		Vec3 frontDir = new Vec3(1, 0, 0);
		Vec3 front = CreatureRules.spot(target, frontDir, CreatureRules.Role.FRONT, 3, 5);
		Vec3 left = CreatureRules.spot(target, frontDir, CreatureRules.Role.FLANK_LEFT, 3, 5).subtract(target);
		Vec3 right = CreatureRules.spot(target, frontDir, CreatureRules.Role.FLANK_RIGHT, 3, 5).subtract(target);
		helper.assertTrue(front.distanceTo(target.add(3, 0, 0)) < 1.0e-6, "the front hound holds 3 blocks out on the pack's line");
		double leftAngle = Math.toDegrees(Math.acos(left.normalize().dot(frontDir)));
		double rightAngle = Math.toDegrees(Math.acos(right.normalize().dot(frontDir)));
		helper.assertTrue(Math.abs(leftAngle - CreatureRules.FLANK_DEGREES) < 0.01 && Math.abs(rightAngle - CreatureRules.FLANK_DEGREES) < 0.01
				&& Math.signum(left.z) == -Math.signum(right.z) && Math.abs(left.length() - 5) < 1.0e-6,
				"the flankers circle 110 degrees to either side, 5 blocks out");
		helper.assertTrue(CreatureRules.houndMaySpawn(true, false, -1, 64) && CreatureRules.houndMaySpawn(false, true, -1, 64)
				&& !CreatureRules.houndMaySpawn(false, false, -1, 64) && !CreatureRules.houndMaySpawn(true, false, 63.0 * 63.0, 64)
				&& CreatureRules.houndMaySpawn(true, false, 65.0 * 65.0, 64), "hounds come at dusk, at night or in the ashfall, never near the Proving");

		Cleanup cleanup = cleanup(helper);
		AshHound parried = cleanup.entity(helper.spawnWithNoFreeWill(CreatureEntities.ASH_HOUND, new BlockPos(6, 1, 8)));
		AshHound idle = cleanup.entity(helper.spawnWithNoFreeWill(CreatureEntities.ASH_HOUND, new BlockPos(16, 1, 8)));
		FakePlayer defender = caster(helper, cleanup, new BlockPos(6, 1, 4));

		guarded(cleanup, () -> {
			parried.onParried(defender, SaberCombat.Strike.NPC_LIGHT);
			helper.assertTrue(parried.isStaggered() && parried.state() == AshHound.STATE_STAGGERED, "a parried hound is turned aside and staggered");
			idle.interrupt(null);
			helper.assertTrue(!idle.isStaggered(), "an interrupt only staggers a hound in its lunge");
		});
		last(helper, cleanup, CreatureBalance.get().lungeStaggerTicks + 5, () -> helper.assertTrue(!parried.isStaggered(), "the stagger wears off"));
	}

	// ---------------------------------------------------------------- Torvin Kaast (10.5)

	@GameTest(structure = ARENA, maxTicks = 20)
	public void kaastPhaseThresholds(GameTestHelper helper) {
		KaastBalance d = new KaastBalance();
		helper.assertTrue(KaastRules.phaseOf(0.66f, d) == 1 && KaastRules.phaseOf(0.65f, d) == 2 && KaastRules.phaseOf(0.31f, d) == 2
				&& KaastRules.phaseOf(0.30f, d) == 3, "phase 2 at 65%, phase 3 at 30%");
		helper.assertTrue(KaastRules.huntersDue(0.5f, false, d) && !KaastRules.huntersDue(0.51f, false, d) && !KaastRules.huntersDue(0.4f, true, d),
				"he calls his hunters once, at 50%");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		FakePlayer owner = caster(helper, cleanup, new BlockPos(20, 1, 20));
		FakePlayer stranger = caster(helper, cleanup, new BlockPos(22, 1, 20));

		last(helper, cleanup, 1, () -> {
			KaastSession session = fight(helper, cleanup, owner, camp(helper, 9, false));
			TorvinKaast kaast = kaast(helper, session);
			helper.assertTrue(session.phase() == 1 && kaast.phase() == 1 && session.rattle() != null, "the fight opens in phase 1 with Rattle scanning");
			helper.assertTrue(!KaastFight.phase(stranger, 2), "only the owner's fight can be jumped");

			helper.assertTrue(KaastFight.phase(owner, 2), "the phase command reaches the fight");
			helper.assertTrue(session.phase() == 2 && kaast.phase() == 2 && session.rattle() == null, "phase 2: Rattle flies off");
			helper.assertTrue(session.isTransitioning() && !kaast.hurtServer(level, level.damageSources().magic(), 5f),
					"the change of phase is a breath: no damage lands");
			helper.assertTrue(!kaast.isAxeDrawn(), "the axe stays sheathed until phase 3");

			helper.assertTrue(KaastFight.phase(owner, 3), "on to phase 3");
			helper.assertTrue(session.phase() == 3 && kaast.phase() == 3 && kaast.isAxeDrawn() && session.rattle() != null,
					"phase 3: the helmet cracks, the axe comes out, Rattle is back");
			helper.assertTrue(dismissCalledHunters(helper, owner.getUUID()) >= 1, "below half health he called his hunters");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 30)
	public void kaastChokeOnlyOnceInPhase3(GameTestHelper helper) {
		KaastBalance d = new KaastBalance();
		helper.assertTrue(KaastRules.chokeTicks(1, false, 60, d) == 0 && KaastRules.chokeTicks(2, false, 60, d) == 0
				&& KaastRules.chokeTicks(3, false, 60, d) == d.chokeTicks && KaastRules.chokeTicks(3, true, 60, d) == 0,
				"Choke holds him only in phase 3, only once, for at most 20 ticks");

		Cleanup cleanup = cleanup(helper);
		ForceAbility choke = power(helper, ForceChoke.ID);
		FakePlayer owner = caster(helper, cleanup, new BlockPos(20, 1, 20));
		FakePlayer caster = caster(helper, cleanup, new BlockPos(12, 1, 9), ForceChoke.ID);
		TorvinKaast[] held = new TorvinKaast[1];

		step(helper, cleanup, 1, () -> {
			KaastSession session = fight(helper, cleanup, owner, camp(helper, 9, false));
			TorvinKaast kaast = kaast(helper, session);
			held[0] = kaast;
			helper.assertTrue(ForceReactions.chokeTicks(kaast, 60) == 0, "phase 1: he does not let a Choke hold him");
			aim(caster, ForcePowers.chest(kaast));
			float before = energy(caster);
			choke.onInput(caster, AbilityAction.START);
			helper.assertTrue(!ForceChoke.isChoking(caster) && near(energy(caster), before), "the phase 1 Choke is refused for free");

			KaastFight.phase(owner, 3);
			dismissCalledHunters(helper, owner.getUUID());
			helper.assertTrue(ForceReactions.chokeTicks(kaast, 60) == Math.min(60, KaastBalance.get().chokeTicks), "phase 3: a Choke may hold him for 20 ticks");
			aim(caster, ForcePowers.chest(kaast));
			choke.onInput(caster, AbilityAction.START);
			helper.assertTrue(ForceChoke.isChoking(caster) && ChokedTargets.isChoked(kaast), "the phase 3 Choke holds him");
		});
		step(helper, cleanup, 5, () -> {
			TorvinKaast kaast = held[0];
			helper.assertTrue(kaast.chokeUsed(), "the hold uses up his one Choke");
			choke.onInput(caster, AbilityAction.RELEASE);
			helper.assertTrue(!ForceChoke.isChoking(caster), "released");
		});
		last(helper, cleanup, 8, () -> {
			TorvinKaast kaast = held[0];
			Progression.setCooldown(caster, ForceChoke.ID, 0);
			refill(caster);
			aim(caster, ForcePowers.chest(kaast));
			choke.onInput(caster, AbilityAction.START);
			helper.assertTrue(!ForceChoke.isChoking(caster) && ForceReactions.chokeTicks(kaast, 60) == 0, "a second Choke never holds him");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 40)
	public void kaastInterruptsOnlyFromListed(GameTestHelper helper) {
		helper.assertTrue(!KaastRules.interrupts(KaastRules.Interrupt.OTHER, 1, true, true) && !KaastRules.interrupts(KaastRules.Interrupt.OTHER, 3, false, false),
				"a stagger, a stun, a shock or a Push on the ground never interrupts him");
		helper.assertTrue(KaastRules.interrupts(KaastRules.Interrupt.PUSH_OFF_NEST, 1, true, false) && !KaastRules.interrupts(KaastRules.Interrupt.PUSH_OFF_NEST, 1, false, false)
				&& !KaastRules.interrupts(KaastRules.Interrupt.PUSH_OFF_NEST, 2, true, false), "a Push knocks him off a nest, in phase 1 only");
		helper.assertTrue(KaastRules.interrupts(KaastRules.Interrupt.PARRIED_COMBO, 3, false, false) && !KaastRules.interrupts(KaastRules.Interrupt.PARRIED_COMBO, 2, false, false)
				&& KaastRules.interrupts(KaastRules.Interrupt.KNEEL_HEAVY, 2, false, true) && !KaastRules.interrupts(KaastRules.Interrupt.KNEEL_HEAVY, 2, false, false)
				&& KaastRules.interrupts(KaastRules.Interrupt.PARRIED_BOLT, 2, false, false)
				&& KaastRules.interrupts(KaastRules.Interrupt.RETURNED_HEAVY_BOLT, 3, false, false),
				"a parried combo (phase 3), a heavy blow while he kneels, a parried or returned bolt do");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		FakePlayer owner = caster(helper, cleanup, new BlockPos(20, 1, 20));
		FakePlayer caster = caster(helper, cleanup, new BlockPos(12, 1, 9));
		TorvinKaast[] boss = new TorvinKaast[1];

		step(helper, cleanup, 1, () -> {
			KaastSession session = fight(helper, cleanup, owner, camp(helper, 9, false));
			TorvinKaast kaast = kaast(helper, session);
			boss[0] = kaast;
			helper.assertTrue(kaast.nest() >= 0, "he starts on a nest");

			int slowed = ControlEffects.stagger(caster, kaast, 20, 1);
			MobEffectInstance slow = kaast.getEffect(MobEffects.SLOWNESS);
			helper.assertTrue(!kaast.isStaggered(), "a stagger does not interrupt him");
			helper.assertTrue(slowed == Math.round(20 * (1f - KaastBalance.get().controlShorten)) && slow != null && slow.getDuration() <= slowed,
					"control effects on him are 40% shorter: 20 ticks of slow became " + slowed);
			helper.assertTrue(near(ForceReactions.liftHeight(kaast, 3f), 0), "Lift cannot raise him");

			BlasterBolt bolt = BlasterBolt.create(level, kaast, ForcePowers.chest(kaast).add(0, 0, 2), 4f, 20);
			bolt.redirect(new Vec3(0, 0, -1), caster, true);
			kaast.hurtServer(level, level.damageSources().source(EnemyEntities.BLASTER_DAMAGE, bolt, caster), 2f);
			helper.assertTrue(kaast.isStaggered() && kaast.nest() >= 0, "his own bolt parried back staggers him");
		});
		last(helper, cleanup, 28, () -> {
			TorvinKaast kaast = boss[0];
			helper.assertTrue(!kaast.isStaggered() && kaast.nest() >= 0, "the parried-bolt stagger is over; he is still on the nest");
			ForceReactions.onPushHit(caster, kaast);
			helper.assertTrue(kaast.isStaggered() && kaast.isFallStaggered() && kaast.nest() < 0, "a Push knocks him off the nest, staggered");
			helper.assertTrue(near(ForceReactions.liftHeight(kaast, 3f), Math.min(3f, KaastBalance.get().liftFallBlocks)), "while he falls, Lift raises him 1 block");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 60)
	public void kaastMinesArmThenLive(GameTestHelper helper) {
		KaastBalance d = new KaastBalance();
		helper.assertTrue(d.mineArmTicks == 40 && d.mines == 6, "6 mines that arm over 2 seconds");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		TorvinKaast setter = KaastEntities.TORVIN_KAAST.create(level, EntitySpawnReason.EVENT);
		helper.assertTrue(setter != null, "Kaast can be created");
		Mob pig = cleanup.entity(helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 1, 3)));
		MandalorianHunter ally = hunter(helper, cleanup, new BlockPos(12, 1, 3));
		onlooker(helper, cleanup, new BlockPos(20, 1, 3));
		List<CampMine> mines = new ArrayList<>();

		KaastBalance live = KaastBalance.get();

		guarded(cleanup, () -> {
			for (BlockPos at : List.of(new BlockPos(3, 1, 3), new BlockPos(12, 1, 3), new BlockPos(20, 1, 3))) {
				CampMine mine = CampMine.place(level, setter, helper.absolutePos(at), live);
				helper.assertTrue(mine != null, "the mine is set");
				mines.add(cleanup.entity(mine));
			}
		});
		step(helper, cleanup, live.mineArmTicks - 4, () -> {
			helper.assertTrue(mines.stream().noneMatch(CampMine::isLive) && mines.stream().noneMatch(CampMine::isRemoved), "arming mines are not live yet");
			helper.assertTrue(near(pig.getHealth(), pig.getMaxHealth()), "an arming mine does not go off");
		});
		last(helper, cleanup, live.mineArmTicks + 6, () -> {
			helper.assertTrue(mines.get(0).isRemoved() && (!pig.isAlive() || pig.getHealth() < pig.getMaxHealth()),
					"once live, the mine under the pig goes off and hurts it");
			helper.assertTrue(mines.get(1).isLive() && !mines.get(1).isRemoved() && ally.isAlive(), "his own side never sets a mine off");
			helper.assertTrue(mines.get(2).isLive() && !mines.get(2).isRemoved(), "a creative player never sets a mine off");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void kaastHealthScaling(GameTestHelper helper) {
		KaastBalance d = new KaastBalance();
		helper.assertTrue(near(KaastRules.maxHealth(1, d), 500) && near(KaastRules.maxHealth(2, d), 800) && near(KaastRules.maxHealth(4, d), 1400)
				&& near(KaastRules.maxHealth(9, d), 1400), "500 health, +60% for each further participant, up to 4");
		helper.assertTrue(KaastRules.mines(1, d) == 6 && KaastRules.mines(3, d) == 10 && KaastRules.hunters(1, d) == 2 && KaastRules.hunters(2, d) == 3,
				"mines +2 and hunters +1 for each further participant");

		Cleanup cleanup = cleanup(helper);
		FakePlayer owner = caster(helper, cleanup, new BlockPos(22, 1, 22));
		onlooker(helper, cleanup, new BlockPos(9, 1, 8));
		onlooker(helper, cleanup, new BlockPos(15, 1, 8));
		KaastSession[] fight = new KaastSession[1];

		step(helper, cleanup, 1, () -> {
			KaastSession session = fight(helper, cleanup, owner, camp(helper, 9, false));
			fight[0] = session;
			TorvinKaast kaast = kaast(helper, session);
			helper.assertTrue(session.participants().size() == 2, "the two players in the ring are listed, got " + session.participants().size());
			double two = KaastRules.maxHealth(2, KaastBalance.get());
			helper.assertTrue(near(kaast.getMaxHealth(), two) && near(kaast.getHealth(), two), "two participants: 800 health, got " + kaast.getMaxHealth());
			onlooker(helper, cleanup, new BlockPos(12, 1, 16));
		});
		last(helper, cleanup, 4, () -> {
			KaastSession session = fight[0];
			TorvinKaast kaast = kaast(helper, session);
			double three = KaastRules.maxHealth(3, KaastBalance.get());
			// The max health attribute stops at 1024, so 1,100 is carried as 1024 health taking 1024/1100 of each hit.
			helper.assertTrue(session.participants().size() == 3 && near(kaast.scaledMaxHealth(), three)
					&& near(kaast.getMaxHealth(), KaastRules.attributeHealth(three)),
					"a third joins: 1,100 health (the attribute capped at 1024), got " + kaast.scaledMaxHealth() + " as " + kaast.getMaxHealth());
			ServerLevel level = helper.getLevel();
			float before = kaast.getHealth();
			kaast.invulnerableTime = 0;
			kaast.hurtServer(level, level.damageSources().magic(), 11f);
			float taken = before - kaast.getHealth();
			helper.assertTrue(Math.abs(taken - 11f * KaastRules.damageScale(three)) < 0.01f && taken < 11f,
					"past the cap a hit is scaled by 1024/1100: 11 damage took " + taken);
		});
	}

	@GameTest(structure = ARENA, maxTicks = 230)
	public void kaastResetAfter200(GameTestHelper helper) {
		KaastBalance d = new KaastBalance();
		helper.assertTrue(!KaastRules.resetDue(199, d) && KaastRules.resetDue(200, d), "the camp resets after 200 ticks with nobody in the ring");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		ServerPlayer owner = onlooker(helper, cleanup, new BlockPos(12, 1, 9));
		CampLayout layout = camp(helper, 5, true);
		BlockPos gate = layout.gates().get(0);
		TorvinKaast[] boss = new TorvinKaast[1];

		step(helper, cleanup, 1, () -> {
			KaastSession session = fight(helper, cleanup, owner, layout);
			boss[0] = kaast(helper, session);
			helper.assertTrue(session.isParticipant(owner.getUUID()), "the owner in the ring is listed");
			helper.assertTrue(level.getBlockState(gate).getBlock() instanceof CampGateBlock && !level.getBlockState(gate).getValue(CampGateBlock.OPEN),
					"the gate shuts when the fight begins");
			Vec3 away = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
			owner.snapTo(away.x, away.y, away.z);
		});
		step(helper, cleanup, 190, () -> helper.assertTrue(KaastFight.session(owner.getUUID()) != null, "the fight still waits before 200 empty ticks"));
		last(helper, cleanup, 215, () -> {
			helper.assertTrue(KaastFight.session(owner.getUUID()) == null && boss[0].isRemoved(), "200 ticks with nobody in the ring reset the camp");
			helper.assertTrue(level.getBlockState(gate).getValue(CampGateBlock.OPEN), "the reset opens the gate");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void kaastYieldAt10(GameTestHelper helper) {
		KaastBalance d = new KaastBalance();
		helper.assertTrue(KaastRules.yieldDue(50f, 500f, d) && !KaastRules.yieldDue(51f, 500f, d) && near(KaastRules.floor(500f, d), 50f),
				"he yields at 10% and never falls below it");

		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		FakePlayer owner = caster(helper, cleanup, new BlockPos(22, 1, 22));

		last(helper, cleanup, 2, () -> {
			KaastSession session = fight(helper, cleanup, owner, camp(helper, 9, false));
			TorvinKaast kaast = kaast(helper, session);
			float max = kaast.getMaxHealth();
			kaast.hurtServer(level, level.damageSources().magic(), max * 2f);
			float floor = KaastRules.floor(max, KaastBalance.get());
			helper.assertTrue(kaast.isAlive() && near(kaast.getHealth(), floor), "a killing blow leaves him at 10%, at " + kaast.getHealth());
			helper.assertTrue(session.isYielded() && kaast.isKnelt(), "he kneels and takes off his helmet");
			kaast.invulnerableTime = 0;
			helper.assertTrue(!kaast.hurtServer(level, level.damageSources().magic(), 10f) && near(kaast.getHealth(), floor),
					"knelt, he takes no more damage");
		});
	}

	@GameTest(structure = ARENA, maxTicks = 60)
	public void resolveCallsHook(GameTestHelper helper) {
		Cleanup cleanup = cleanup(helper);
		ServerPlayer alone = onlooker(helper, cleanup, new BlockPos(2, 1, 22));
		ServerPlayer fighter = onlooker(helper, cleanup, new BlockPos(12, 1, 9));
		TorvinKaast[] boss = new TorvinKaast[1];

		step(helper, cleanup, 1, () -> {
			for (ServerPlayer player : List.of(alone, fighter)) {
				StoryData data = Story.get(player);
				data.chapter4 = data.chapter4.withStage(ChapterFourStage.DUEL, 0L);
				Story.commit(player, data);
				helper.assertTrue(!Story.get(player).flags.contains(ChapterFourRules.KAAST_PAID), "nothing paid yet");
			}

			KaastFight.resolve(alone, KaastOutcome.SPARED);
			helper.assertTrue(Story.get(alone).flags.contains(ChapterFourRules.KAAST_PAID), "with no fight running, the choice still goes through the defeat hook");

			KaastSession session = fight(helper, cleanup, fighter, camp(helper, 9, false));
			boss[0] = kaast(helper, session);
			KaastFight.resolve(fighter, KaastOutcome.SPARED);
			helper.assertTrue(Story.get(fighter).flags.contains(ChapterFourRules.KAAST_PAID), "the running fight's choice goes through the defeat hook");
			helper.assertTrue(session.isYielded() && boss[0].isKnelt(), "the choice ends the fight");
		});
		last(helper, cleanup, 45, () -> helper.assertTrue(KaastFight.session(fighter.getUUID()) == null && boss[0].isRemoved(),
				"spared, he jets away and the fight is over"));
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void rematchOncePerDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Cleanup cleanup = cleanup(helper);
		ServerPlayer owner = onlooker(helper, cleanup, new BlockPos(12, 1, 18));
		BlockPos gateCell = new BlockPos(12, 1, 20);

		last(helper, cleanup, 1, () -> {
			long today = Clocks.day(level.getServer());
			StoryData data = Story.get(owner);
			data.chapter4 = data.chapter4.withStage(ChapterFourStage.COMPLETE, 0L)
					.withDuel(StoryData.Chapter4.Duel.NONE.withCamp(GlobalPos.of(level.dimension(), helper.absolutePos(HEART))));
			data.counters.put(ChapterFourRules.REMATCH_DAY, (int) today);
			Story.commit(owner, data);
			helper.assertTrue(!ChapterFourRules.rematchDue(Story.get(owner), today) && ChapterFourRules.rematchDue(Story.get(owner), today + 1),
					"the rematch is due once each in-game day");

			helper.setBlock(gateCell, KaastBlocks.CAMP_GATE.defaultBlockState().setValue(CampGateBlock.OPEN, true).setValue(CampGateBlock.TOP, true));
			cleanup.then(() -> KaastFight.reset(owner));
			helper.useBlock(gateCell, owner);
			helper.assertTrue(KaastFight.session(owner.getUUID()) == null, "the echo already fought today does not come again");

			data = Story.get(owner);
			data.counters.put(ChapterFourRules.REMATCH_DAY, (int) today - 1);
			Story.commit(owner, data);
			helper.useBlock(gateCell, owner);
			KaastSession echo = KaastFight.session(owner.getUUID());

			if (level.getDifficulty() == Difficulty.PEACEFUL) {
				helper.assertTrue(echo == null, "no echo on Peaceful");
			} else {
				helper.assertTrue(echo != null && echo.isEcho() && echo.kaast() != null && echo.kaast().isEcho(), "a new day: the gate calls the hunter's echo");
			}

			KaastFight.reset(owner);
		});
	}
}
