package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.platform.InputConstants;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.enemy.CrystalPoacher;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.PushShape;
import com.oldrepublic.force.PushTargeting;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestItems;
import com.oldrepublic.quest.QuestStage;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberGuard;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;
import com.oldrepublic.world.CaveLocator;
import com.oldrepublic.world.WorldBlocks;
import com.oldrepublic.world.block.ResonantSealBlock;
import com.oldrepublic.world.trial.CrystalGrant;
import com.oldrepublic.world.trial.ShadowEchoEntity;
import com.oldrepublic.world.trial.TrialManager;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Plays Milestone 1 the way a player would, in a survival world with normal terrain and a fixed
 * seed: sleep to trigger the Awakening, crouch with the journal to meditate, push hostile mobs,
 * follow the Calling to a generated crystal cave, fight the poacher camp, open the seal, win the
 * Trial of Reflection, loot the hidden chamber, craft the parts and the workbench, build the
 * lightsaber and guard against blaster fire. Inputs are real key presses, mouse clicks and menu
 * clicks; quest state is only read, never set.
 *
 * <p>Every check is recorded as PASS, FAIL or STUCK instead of failing at once, and every shortcut
 * (teleporting over long distances, time of day, vanilla items, keep-alive effects, removing stray
 * mobs) is logged. The summary is printed with every line prefixed {@code PLAYTHROUGH:}; the test
 * fails at the end only if a step got stuck, so all screenshots are still taken and uploaded.
 */
public class PlaythroughTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("oldrepublic-playthrough");
	private static final String SEED = "echoes-of-the-old-republic";

	private static final Function<Options, KeyMapping> FORWARD = o -> o.keyUp;
	private static final Function<Options, KeyMapping> JUMP = o -> o.keyJump;
	private static final Function<Options, KeyMapping> SNEAK = o -> o.keyShift;
	private static final Function<Options, KeyMapping> SPRINT = o -> o.keySprint;
	private static final Function<Options, KeyMapping> USE = o -> o.keyUse;
	private static final Function<Options, KeyMapping> ATTACK = o -> o.keyAttack;
	private static final Function<Options, KeyMapping> INVENTORY = o -> o.keyInventory;

	/** Hold time of a practice push: past the minimum charge, short enough to afford two in a row. */
	private static final int PUSH_HOLD_TICKS = 12;
	/** Health below which the bot gets keep-alive effects (logged as a shortcut). */
	private static final float RESCUE_HEALTH = 8f;
	/** Eye-to-aim-point distance the bot closes to before mining (survival block reach is 4.5). */
	private static final double MINE_REACH = 3.5;

	private enum Result {
		PASS, FAIL, STUCK, INFO
	}

	private record Entry(String step, Result result, String details) {
	}

	/** Thrown when the playthrough cannot continue; the summary is still printed. */
	private static final class Stuck extends RuntimeException {
		Stuck(String message) {
			super(message);
		}
	}

	/** Thrown after the player died and respawned; the current step is retried once. */
	private static final class Died extends RuntimeException {
		Died(String message) {
			super(message);
		}
	}

	/** Client view of the player for one tick. */
	private record Me(Vec3 pos, Vec3 eye, boolean collided, boolean onGround, float strength, int picked, float health, boolean dead) {
	}

	/** A blaster bolt seen near the player. */
	private record Bolt(UUID id, boolean deflected, boolean towardPoacher) {
	}

	/** Server view of a fight target. */
	private record Target(boolean alive, Vec3 pos, double height, int id) {
	}

	/**
	 * The heart piece of a generated cave, located from its meditation stone and resonant seal. Maps
	 * template-local coordinates of {@code crystal_cave/heart} (see tools/assets/gen_structures.py)
	 * to world positions, whatever the piece's rotation: local +z points from the seal to the stone.
	 */
	private record Heart(BlockPos stone, int dx, int dz, boolean locatorHitStone, int sealBlocks) {
		BlockPos block(int x, int y, int z) {
			int ox = x - 22;
			int oy = y - 1;
			int oz = z - 38;
			return stone.offset(ox * dz + oz * dx, oy, -ox * dx + oz * dz);
		}

		Vec3 feet(int x, int y, int z) {
			return Vec3.atBottomCenterOf(block(x, y, z));
		}

		/** A point offset from the centre of a local block, offsets given in local axes. */
		Vec3 point(int x, int y, int z, double ox, double oy, double oz) {
			Vec3 center = Vec3.atCenterOf(block(x, y, z));
			return center.add(ox * dz + oz * dx, oy, -ox * dx + oz * dz);
		}

		/** Yaw that faces local +z (from the cave entrance towards the trial chamber). */
		float yawInward() {
			return (float) (-Mth.atan2(dx, dz) * Mth.RAD_TO_DEG);
		}
	}

	/** Game messages the client received (chat and action bar), for logging refusals. */
	private static final List<String> MESSAGES = Collections.synchronizedList(new ArrayList<>());
	private static boolean listening;
	/** Damage the player took while {@link #recordHits} was on, described with the hit's direction (server thread writes). */
	private static final List<String> HITS = Collections.synchronizedList(new ArrayList<>());
	private static volatile boolean recordHits;

	private final List<Entry> entries = new ArrayList<>();
	private final Map<String, Integer> shortcuts = new LinkedHashMap<>();
	private final List<BlockPos> knownCaves = new ArrayList<>();
	private ClientGameTestContext context;
	private TestSingleplayerContext singleplayer;
	private TestServerContext server;
	private int screenshots;
	private boolean stuck;
	private boolean forwardHeld;
	private int lastRescueTick = -1000;
	private int ticks;
	private float damageTaken;
	private @Nullable Heart heart;
	private @Nullable Vec3 lastCamp;
	/** Why the last {@link #walkTo} gave up (drop ahead, stalled, timeout). */
	private String walkStop = "";

	@Override
	public void runTest(ClientGameTestContext context) {
		this.context = context;

		if (!listening) {
			listening = true;
			// Records what the player is told, e.g. vanilla's bed refusals on the action bar.
			ClientReceiveMessageEvents.GAME.register((message, overlay) -> MESSAGES.add((overlay ? "[action bar] " : "[chat] ") + message.getString()));
			// Records where the hits the guard let through came from.
			ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
				if (recordHits && entity instanceof ServerPlayer player) {
					HITS.add(describeHit(player, source, taken));
				}
			});
		}

		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(creator -> {
					creator.setSeed(SEED);
					creator.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
				})
				.create()) {
			this.singleplayer = world;
			this.server = world.getServer();

			try {
				play();
			} catch (Stuck e) {
				LOG.info("PLAYTHROUGH-LOG: stopped: {}", e.getMessage());
			} catch (Throwable t) {
				note("unexpected error", Result.STUCK, t.getClass().getSimpleName() + ": " + t.getMessage());
				LOG.error("PLAYTHROUGH-LOG: unexpected error", t);
				shot("error");
			} finally {
				releaseAll();
			}
		}

		printSummary();

		if (stuck) {
			throw new AssertionError("Playthrough got stuck; see the PLAYTHROUGH: summary in the log");
		}
	}

	private void play() {
		step("01 start", this::start);
		step("03 awakening", this::awakening);
		step("04 meditation", this::meditation);
		step("05 practice", this::practice);
		step("06 calling", this::calling);
		step("07 crafting", this::craftParts);
		step("08 travel", this::travelToCave);
		step("09 camp fight", () -> fightCamp("camp 1", false));
		step("10 seal", this::enterHeart);
		step("11 trial", this::trial);
		step("12 crystal", this::crystal);
		step("13 hidden chamber", this::hiddenChamber);
		step("14 emitter", this::obtainEmitter);
		step("15 workbench", this::buildSaber);
		step("16 saber", this::saberCombat);
		step("17 finish", this::finish);
	}

	/** Runs a step; if the player dies during it, the step is retried once from the respawn point. */
	private void step(String name, Runnable body) {
		for (int attempt = 1; ; attempt++) {
			try {
				logWhere(name, attempt == 1 ? "start" : "retry " + attempt);
				body.run();

				if (context.computeOnClient(mc -> mc.player.isDeadOrDying())) {
					// Died somewhere the fight and walk loops do not watch (e.g. while waiting).
					died(name);
				}

				logWhere(name, "end");
				return;
			} catch (Died e) {
				if (attempt >= 2) {
					stuck(name, "died again on the retry: " + e.getMessage());
				}

				note(name, Result.INFO, "retrying the step after dying");
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Steps
	// ---------------------------------------------------------------------------------------------

	private void start() {
		waitForChunks("spawn");
		server.runCommand("weather clear");
		// API-CHECK: LevelReader#getBiome + Holder#unwrapKey (standard, not in the probe dumps).
		String where = onServer(p -> p.blockPosition().toShortString() + " in "
				+ p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
		ForceData data = data();
		boolean survival = onServer(p -> !p.isCreative() && !p.isSpectator());
		check("01 world", survival && stage(data) == QuestStage.STIRRING && !data.hasAbility("force_push"),
				"seed '" + SEED + "', spawn " + where + ", survival=" + survival + ", stage=" + stage(data));
		shot("spawn");
		moveToStartSpot();

		shortcut("gave starting kit a survivor would have: white bed, 16 bread, iron sword, iron pickaxe, iron armour (equipped)");
		server.runCommand("give @a minecraft:iron_sword");
		server.runCommand("give @a minecraft:white_bed");
		server.runCommand("give @a minecraft:bread 16");
		server.runCommand("give @a minecraft:iron_pickaxe");
		server.runCommand("item replace entity @a armor.head with minecraft:iron_helmet");
		server.runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate");
		server.runCommand("item replace entity @a armor.legs with minecraft:iron_leggings");
		server.runCommand("item replace entity @a armor.feet with minecraft:iron_boots");
		shortcut("keepInventory on, so a death (logged as FAIL) does not end the run");
		server.runCommand("gamerule keep_inventory true");
		server.runCommand("gamerule keepInventory true");
		shortcut("saturation effect for the whole run (the bot does not eat)");
		server.runCommand("effect give @a minecraft:saturation infinite 0 true");
		context.waitTicks(5);

		// Before the Awakening the Force key must do nothing.
		float energyBefore = data().energy;
		context.getInput().holdKeyFor(InputConstants.KEY_R, 20);
		context.waitTicks(5);
		ForceData after = data();
		check("02 no powers before the Awakening", !after.hasAbility("force_push") && after.cooldown("force_push") == 0
				&& after.energy == energyBefore && stage(after) == QuestStage.STIRRING,
				"held R for 20 ticks: cooldown=" + after.cooldown("force_push") + ", energy " + energyBefore + " -> " + after.energy);
	}

	/** Sleeps in a bed at night, which should trigger the Awakening dream on waking. */
	private void awakening() {
		String step = "03 awakening (sleep)";
		shortcut("time set to night so the bed can be used");
		server.runCommand("time set 13000");
		context.waitTicks(5);
		clearInterference(step);
		Item bedItem = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("white_bed"));
		boolean awakened = false;

		for (int attempt = 1; attempt <= 3 && !awakened; attempt++) {
			if (attempt > 1) {
				// Another spot: a flat place further along, with a fresh bed.
				Vec3 spot = onServer(p -> findFlatSpot(p.level(), p.blockPosition().getX() + 12, p.blockPosition().getZ(), 40));

				if (spot != null) {
					shortcut("teleported to another flat spot for the bed after a failed sleep");
					teleport(spot, 0f);
				}

				if (count(bedItem) == 0) {
					shortcut("gave another white bed for the next sleep attempt");
					server.runCommand("give @a minecraft:white_bed");
					context.waitTicks(3);
				}

				shortcut("time set to night again for sleep attempt " + attempt);
				server.runCommand("time set 13000");
				context.waitTicks(3);
			}

			// API-CHECK: BlockTags.BEDS and BlockState#is(TagKey) (standard, not in the probe dumps).
			BlockPos bed = placeBlock(bedItem, state -> state.is(BlockTags.BEDS), true);

			if (bed == null) {
				note(step, Result.FAIL, "attempt " + attempt + ": could not place the bed by right-clicking the ground");
				continue;
			}

			note(step, Result.INFO, "attempt " + attempt + ": bed placed by right-clicking the ground at " + bed.toShortString());
			boolean asleep = sleepIn(step, attempt, bed);

			if (!asleep) {
				continue;
			}

			context.waitTicks(40);
			shot("sleeping");
			boolean woke = waitServer(300, p -> !p.isSleeping());
			context.waitTicks(30);
			shot("dream");
			awakened = waitServer(260, p -> stage(Progression.get(p)) != QuestStage.STIRRING);
			note(step, awakened ? Result.INFO : Result.FAIL, "attempt " + attempt + ": woke=" + woke + ", awakened=" + awakened);
		}

		ForceData data = data();
		boolean journal = count(QuestItems.WANDERERS_JOURNAL) == 1;

		if (!awakened) {
			stuck(step, "sleeping did not trigger the Awakening (stage " + stage(data) + "); the time trigger needs "
					+ Progression.balance().awakeningTicks + " ticks alive, too long to wait");
		}

		context.waitTicks(20);
		shot("awakened");
		check(step, stage(data) == QuestStage.FOCUS && journal && data.hasFlag("resonance:awakening")
				&& data.hasFlag(JournalEntries.entryFlag(JournalEntries.THE_DREAM)),
				"stage=" + stage(data) + ", journal=" + journal + ", resonance=" + data.resonance + ", energy=" + data.energy + "/" + data.maxEnergy);
	}

	/**
	 * Clears monsters that would block resting, steps next to the bed, aims at the middle of the
	 * bed's foot and right-clicks it (twice if needed). Logs vanilla's refusal message and the
	 * server-side conditions when the player does not fall asleep.
	 */
	private boolean sleepIn(String step, int attempt, BlockPos bed) {
		int removed = onServer(p -> {
			int n = 0;

			for (Entity m : p.level().getEntities((Entity) null, new AABB(bed).inflate(10), x -> x instanceof Monster && x.isAlive() && !(x instanceof CrystalPoacher))) {
				m.discard();
				n++;
			}

			return n;
		});

		if (removed > 0) {
			shortcut("removed " + removed + " monster(s) within 10 blocks of the bed (vanilla refuses sleep with monsters nearby)");
		}

		// Stand next to the bed, on the side of the player.
		Vec3 me = context.computeOnClient(mc -> mc.player.position());
		Vec3 foot = Vec3.atBottomCenterOf(bed);
		Vec3 away = new Vec3(me.x - foot.x, 0, me.z - foot.z);
		Vec3 beside = away.lengthSqr() < 1.0E-4 ? foot.add(1.2, 0, 0) : foot.add(away.normalize().scale(1.3));
		walkTo(beside, 0.4, 100, false, null);
		boolean asleep = false;

		for (int click = 1; click <= 2 && !asleep; click++) {
			int mark = MESSAGES.size();
			// The bed is 9/16 of a block tall: aim at the middle of its body, not above it.
			aimAt(new Vec3(bed.getX() + 0.5, bed.getY() + 0.3, bed.getZ() + 0.5));
			context.waitTick();
			String looking = lookingAt();
			context.getInput().pressKey(USE);
			asleep = waitServer(40, ServerPlayer::isSleeping);

			if (!asleep) {
				List<String> said;

				synchronized (MESSAGES) {
					said = new ArrayList<>(MESSAGES.subList(Math.min(mark, MESSAGES.size()), MESSAGES.size()));
				}

				String conditions = onServer(p -> {
					ServerLevel level = p.level();
					Vec3 c = Vec3.atBottomCenterOf(bed);
					long monsters = level.getEntities((Entity) null, new AABB(c.x - 8, c.y - 5, c.z - 8, c.x + 8, c.y + 5, c.z + 8),
							x -> x instanceof Monster m && m.isPreventingPlayerRest(level, p)).size();
					BlockState state = level.getBlockState(bed);
					return "bed block " + BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath() + ", player " + p.blockPosition().toShortString()
							+ String.format(Locale.ROOT, " (%.1f blocks from the bed)", p.position().distanceTo(c)) + ", bright outside=" + level.isBrightOutside()
							+ ", clock time " + level.getOverworldClockTime() % 24000 + ", monsters preventing rest " + monsters
							+ ", above the bed " + BuiltInRegistries.BLOCK.getKey(level.getBlockState(bed.above()).getBlock()).getPath();
				});
				note(step, Result.FAIL, "attempt " + attempt + " click " + click + ": not sleeping; crosshair on " + looking + "; messages " + said + "; " + conditions);
			} else {
				note(step, Result.INFO, "attempt " + attempt + " click " + click + ": sleeping (crosshair on " + looking + ")");
			}
		}

		return asleep;
	}

	/**
	 * The centre of the nearest flat, dry 5x5 patch (equal surface height, solid non-fluid ground,
	 * no leaves) to (ox, oz) within {@code radius}, searched in a fixed order so a seed always gives
	 * the same spot. Server thread only.
	 */
	private static @Nullable Vec3 findFlatSpot(ServerLevel level, int ox, int oz, int radius) {
		for (int r = 0; r <= radius; r += 2) {
			for (int dx = -r; dx <= r; dx += 2) {
				for (int dz = -r; dz <= r; dz += 2) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}

					int cx = ox + dx;
					int cz = oz + dz;
					level.getChunk(cx >> 4, cz >> 4, ChunkStatus.FULL, true);
					int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
					boolean flat = true;

					for (int x = cx - 2; x <= cx + 2 && flat; x++) {
						for (int z = cz - 2; z <= cz + 2 && flat; z++) {
							level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
							BlockPos ground = new BlockPos(x, h - 1, z);
							BlockState state = level.getBlockState(ground);
							flat = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) == h
									&& level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) == h
									&& !state.getCollisionShape(level, ground).isEmpty() && state.getFluidState().isEmpty() && !state.is(Blocks.POWDER_SNOW)
									&& open(level, ground.above()) && open(level, ground.above(2));
						}
					}

					if (flat) {
						return new Vec3(cx + 0.5, h, cz + 0.5);
					}
				}
			}
		}

		return null;
	}

	/** No collision and no fluid (air, grass, a thin snow layer). */
	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty() && !state.is(Blocks.POWDER_SNOW);
	}

	/** Teleports to a fixed flat spot near the world origin, so every run starts in the same place. */
	private void moveToStartSpot() {
		Vec3 spot = onServer(p -> findFlatSpot(p.level(), 0, 0, 64));

		if (spot == null) {
			note("01 start", Result.INFO, "no flat 5x5 spot within 64 blocks of 0, 0; staying at the spawn point");
			return;
		}

		shortcut("teleported from the (randomised) spawn point to a fixed flat spot near 0, 0 so every run starts in the same place");
		teleport(spot, 0f);
		String biome = onServer(p -> p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
		note("01 start", Result.INFO, "start spot " + BlockPos.containing(spot).toShortString() + " in " + biome);
	}

	/** Crouches still with the journal in hand until the meditation teaches Force Push. */
	private void meditation() {
		String step = "04 meditation";
		clearInterference(step);

		if (!select(QuestItems.WANDERERS_JOURNAL)) {
			stuck(step, "could not put the journal in hand");
		}

		context.getInput().holdKey(SNEAK);
		context.waitTicks(80);
		shot("meditation");
		boolean learned = waitServer(260, p -> Progression.get(p).hasAbility("force_push"));
		context.getInput().releaseKey(SNEAK);
		ForceData data = data();
		check(step, learned && stage(data) == QuestStage.PRACTICE,
				"crouched still holding the journal: push learned=" + learned + ", stage=" + stage(data) + ", xp=" + data.xp + ", level=" + data.level);

		if (!learned) {
			stuck(step, "meditation never completed");
		}
	}

	/** Pushes hostile mobs until the practice counter completes. */
	private void practice() {
		String step = "05 practice";
		clearInterference(step);
		shortcut("summoned 3 slowed husks as push targets instead of waiting for night spawns");
		List<UUID> targets = new ArrayList<>(summonTargets(4));
		context.waitTicks(10);
		int casts = 0;

		while (casts < 8 && stage(data()) == QuestStage.PRACTICE) {
			if (casts == 4) {
				// Terrain may have put the first targets out of reach or sight: try the other side.
				shortcut("summoned 3 more husks on the other side after 4 casts without finishing the practice");
				targets.addAll(summonTargets(-4));
				context.waitTicks(10);
			}

			waitServer(300, p -> {
				ForceData d = Progression.get(p);
				return d.cooldown("force_push") == 0 && d.energy >= 25f;
			});
			Target target = nearest(targets);

			if (target == null) {
				break;
			}

			aimAt(target.pos().add(0, target.height() * 0.6, 0));
			context.waitTick();
			context.getInput().holdKey(InputConstants.KEY_R);
			context.waitTicks(PUSH_HOLD_TICKS);

			if (casts == 0) {
				shot("push_charge");
			}

			// Which husks the server would count as targets right now, and where everything is.
			String targeting = onServer(p -> {
				float charge = PushShape.chargeFromHold(ForcePush.balance(), PUSH_HOLD_TICKS);
				PushShape shape = ForcePush.shapeFor(p, charge);
				Set<UUID> hit = new HashSet<>();

				for (PushTargeting.Target t : PushTargeting.find(p, shape, ForcePush.balance())) {
					hit.add(t.entity().getUUID());
				}

				List<String> marks = new ArrayList<>();

				for (int i = 0; i < targets.size(); i++) {
					marks.add("#" + (i + 1) + (hit.contains(targets.get(i)) ? " yes" : " no"));
				}

				return String.format(Locale.ROOT, "charge %.2f, knockback %.2f, range %.1f, half angle %.0f, player pitch %.1f, targeted %s", charge,
						shape.knockback(), shape.range(), shape.halfAngle(), p.getXRot(), marks);
			});
			Map<UUID, Vec3> before = huskPositions(targets);
			context.getInput().releaseKey(InputConstants.KEY_R);
			Map<UUID, List<Motion>> motion = new LinkedHashMap<>();

			for (int i = 0; i <= 16; i++) {
				for (Map.Entry<UUID, Motion> m : huskMotion(targets).entrySet()) {
					motion.computeIfAbsent(m.getKey(), k -> new ArrayList<>()).add(m.getValue());
				}

				context.waitTick();

				if (i == 4 && casts == 0) {
					shot("push_release");
				}
			}

			note(step, Result.INFO, "cast " + (casts + 1) + " at release: " + targeting);

			for (int i = 0; i < targets.size(); i++) {
				List<Motion> samples = motion.get(targets.get(i));
				Vec3 start = before.get(targets.get(i));

				if (samples != null && start != null) {
					note(step, Result.INFO, "cast " + (casts + 1) + " husk #" + (i + 1) + ": " + describeMotion(start, samples));
				}
			}

			casts++;
			ForceData d = data();
			note(step, Result.INFO, "cast " + casts + ": practice " + d.counter("push_practice") + "/5, energy " + Math.round(d.energy)
					+ ", mastery " + d.mastery("force_push"));
			context.waitTicks(9);
		}

		ForceData data = data();
		check(step, stage(data) == QuestStage.CALLING && data.hasFlag("resonance:first_push"),
				casts + " real R casts, stage=" + stage(data) + ", practice=" + data.counter("push_practice") + ", mastery=" + data.mastery("force_push"));

		select(Items.IRON_SWORD);
		int killed = 0;

		for (UUID id : targets) {
			if (fight(id, 500, false, step)) {
				killed++;
			}
		}

		int left = onServer(p -> {
			int n = 0;

			for (UUID id : targets) {
				Entity e = p.level().getEntity(id);

				if (e != null && e.isAlive()) {
					e.discard();
					n++;
				}
			}

			return n;
		});

		if (left > 0) {
			shortcut("removed " + left + " practice husk(s) the bot could not finish off");
		}

		note(step, Result.INFO, "killed " + killed + "/" + targets.size() + " husks with the sword");

		if (stage(data) != QuestStage.CALLING) {
			stuck(step, "practice did not complete");
		}
	}

	/** Holds the journal and checks the Calling has a cave to point at. */
	private void calling() {
		String step = "06 calling";

		if (!select(QuestItems.WANDERERS_JOURNAL)) {
			note(step, Result.FAIL, "journal not found in the inventory");
		}

		server.runCommand("execute as @a run oldrepublic locatecave");
		BlockPos cave = null;

		for (int i = 0; i < 40 && cave == null; i++) {
			cave = onServer(CaveLocator::findNearestCave);
			context.waitTick();
		}

		if (cave == null) {
			stuck(step, "CaveLocator found no crystal cave within its search radius");
		}

		BlockPos found = cave;
		Vec3 me = context.computeOnClient(mc -> mc.player.position());
		double distance = Vec3.atCenterOf(found).distanceTo(me);
		aimAt(Vec3.atCenterOf(found).add(0, me.y - found.getY(), 0));
		context.waitTicks(60);
		shot("calling");
		note(step, Result.PASS, "Calling points at " + found.toShortString() + ", " + Math.round(distance) + " blocks away (direction only is sent to the client)");
	}

	/** Crafts the grip, the pommel and the workbench at a crafting table, by clicking the grid. */
	private void craftParts() {
		String step = "07 crafting";
		clearInterference(step);
		shortcut("gave vanilla crafting materials (2 crafting tables, iron, amethyst, smooth stone, leather, string, iron block, nuggets)");
		server.runCommand("give @a minecraft:crafting_table 2");
		server.runCommand("give @a minecraft:iron_ingot 4");
		server.runCommand("give @a minecraft:amethyst_shard 2");
		server.runCommand("give @a minecraft:smooth_stone 3");
		server.runCommand("give @a minecraft:leather 2");
		server.runCommand("give @a minecraft:string 2");
		server.runCommand("give @a minecraft:iron_block 1");
		server.runCommand("give @a minecraft:iron_nugget 4");
		context.waitTicks(5);

		BlockPos table = placeBlock(Items.CRAFTING_TABLE, state -> state.is(Blocks.CRAFTING_TABLE), false);

		if (table == null) {
			shortcut("crafting table placed with /setblock (no free spot for a real placement)");
			BlockPos feet = onServer(p -> p.blockPosition());
			table = feet.offset(0, 0, 2);
			server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d minecraft:crafting_table", table.getX(), table.getY(), table.getZ()));
			context.waitTicks(3);
		}

		if (!openBlock(table, mc -> mc.player.containerMenu instanceof CraftingMenu)) {
			note(step, Result.FAIL, "crafting table screen did not open");
			return;
		}

		boolean grip = craft("grip_wrapped", new String[] { " L ", "SIS", " L " },
				Map.of('L', Items.LEATHER, 'S', Items.STRING, 'I', Items.IRON_INGOT), SaberItems.GRIP_WRAPPED);
		boolean pommel = craft("pommel_weighted", new String[] { " N ", "NBN" },
				Map.of('N', Items.IRON_NUGGET, 'B', Items.IRON_BLOCK), SaberItems.POMMEL_WEIGHTED);
		shot("crafting");
		boolean bench = craft("lightsaber_workbench", new String[] { "III", "ACA", "SSS" },
				Map.of('I', Items.IRON_INGOT, 'A', Items.AMETHYST_SHARD, 'C', Items.CRAFTING_TABLE, 'S', Items.SMOOTH_STONE), SaberItems.WORKBENCH.asItem());
		closeScreen();
		check(step, grip && pommel && bench, "grip_wrapped=" + grip + ", pommel_weighted=" + pommel + ", lightsaber_workbench=" + bench);
	}

	/** Travels to the cave the Calling points at and walks into the poacher camp. */
	private void travelToCave() {
		String step = "08 travel to the cave";
		BlockPos cave = onServer(CaveLocator::findNearestCaveNow);

		if (cave == null) {
			stuck(step, "no cave found");
		}

		if (knownCaves.isEmpty()) {
			knownCaves.add(cave);
		}

		boolean campFound = approachCamp(step, cave);

		if (campFound && lastCamp != null && !onServer(CaveLocator::isInsideCave)) {
			walkTo(lastCamp, 2, 400, true, () -> onServer(CaveLocator::isInsideCave));
		}

		waitServer(30, p -> stage(Progression.get(p)) == QuestStage.TRIAL);
		ForceData data = data();
		boolean inside = onServer(CaveLocator::isInsideCave);
		shot("camp");
		check(step, campFound && stage(data) == QuestStage.TRIAL,
				"camp poachers found=" + campFound + ", inside cave bounds=" + inside + ", stage=" + stage(data)
						+ ", journal voices_in_the_rock=" + data.hasFlag(JournalEntries.entryFlag(JournalEntries.VOICES_IN_THE_ROCK)));
	}

	/**
	 * Teleports near a cave (its start or, when the locator refined it, its meditation stone), finds
	 * the poachers, logs how the camp sits on the terrain and walks up to it, avoiding drops.
	 */
	private boolean approachCamp(String step, BlockPos cave) {
		CaveInfo info = caveInfo(cave);
		Vec3 yard = info != null && info.camp() != null ? campYard(info.camp()) : null;
		note(step, Result.INFO, "cave structure at " + cave.toShortString() + ": " + (info == null ? "NOT found"
				: box(info.structure()) + ", camp " + box(info.camp()) + ", descent " + box(info.descent()) + ", heart " + box(info.heart())));
		int x = yard != null ? Mth.floor(yard.x) : cave.getX();
		int z = (yard != null ? Mth.floor(yard.z) : cave.getZ()) + 40;
		double distance = onServer(p -> Math.sqrt(p.blockPosition().distSqr(new BlockPos(x, p.blockPosition().getY(), z))));
		int y = surfaceY(x, z);
		boolean isStone = onServer(p -> p.level().getBlockState(cave).is(WorldBlocks.MEDITATION_STONE));
		shortcut("teleported " + Math.round(distance) + " blocks to 40 blocks south of the " + (yard != null ? "camp piece" : "cave position") + " of the cave at "
				+ cave.toShortString());
		teleport(new Vec3(x + 0.5, y, z + 0.5), 180f);
		shortcut("time set to day on arrival");
		server.runCommand("time set 1000");
		clearInterference(step);

		if (yard == null) {
			// No camp piece found: fall back to looking for the poachers.
			List<UUID> poachers = List.of();

			for (int i = 0; i < 10 && poachers.isEmpty(); i++) {
				poachers = poachersNear(96);
				context.waitTicks(10);
			}

			if (poachers.isEmpty()) {
				note(step, Result.FAIL, "no camp piece and no crystal poachers within 96 blocks of " + cave.toShortString());
				return false;
			}

			yard = nearest(poachers).pos();
			note(step, Result.FAIL, "no camp piece found in the structure; heading for the nearest poacher at " + BlockPos.containing(yard).toShortString());
		}

		lastCamp = yard;
		logCampGround(step, yard, cave);
		logPoachers(step, info);
		boolean reached = walkTo(yard, 3, 900, true, () -> onServer(CaveLocator::isInsideCave));

		if (!reached) {
			Vec3 spot = safeSpotNear(yard);
			shortcut("walk to the camp stopped (" + walkStop + "); teleported to the surface next to the camp yard");
			note(step, Result.INFO, "walk to the camp stopped: " + walkStop + "; teleporting to " + BlockPos.containing(spot).toShortString());
			teleport(spot, 180f);
		}

		if (!waitServer(40, CaveLocator::isInsideCave) && info != null && info.camp() != null) {
			// Still not inside: step into the yard itself (the camp floor), then log what pieceAt says.
			Vec3 inside = yard;
			shortcut("teleported onto the camp yard because the player was not inside the camp piece");
			teleport(inside, 180f);
			waitServer(40, CaveLocator::isInsideCave);
		}

		return true;
	}

	/** Logs every poacher within 96 blocks and where it stands relative to the camp piece. */
	private void logPoachers(String step, @Nullable CaveInfo info) {
		List<UUID> poachers = poachersNear(96);
		List<String> parts = new ArrayList<>();

		for (UUID id : poachers) {
			Target t = target(id);
			parts.add(BlockPos.containing(t.pos()).toShortString() + " " + relativeToCamp(t.pos(), info));
		}

		note(step, Result.INFO, poachers.size() + " poacher(s) within 96 blocks: " + parts);
	}

	/** Where a position is relative to the camp piece box: inside, or the offset outside it. */
	private static String relativeToCamp(Vec3 pos, @Nullable CaveInfo info) {
		if (info == null || info.camp() == null) {
			return "(no camp piece)";
		}

		BoundingBox c = info.camp();
		BlockPos b = BlockPos.containing(pos);

		if (c.isInside(b)) {
			return "(inside the camp piece)";
		}

		int dx = b.getX() < c.minX() ? b.getX() - c.minX() : b.getX() > c.maxX() ? b.getX() - c.maxX() : 0;
		int dy = b.getY() < c.minY() ? b.getY() - c.minY() : b.getY() > c.maxY() ? b.getY() - c.maxY() : 0;
		int dz = b.getZ() < c.minZ() ? b.getZ() - c.minZ() : b.getZ() > c.maxZ() ? b.getZ() - c.maxZ() : 0;
		String other = info.pieceContaining(b);
		return "(outside the camp piece by dx " + dx + ", dy " + dy + ", dz " + dz + (other != null ? ", inside the " + other + " piece" : "") + ")";
	}

	/**
	 * Logs how the camp piece sits on the terrain: its yard height against the ground around it, and
	 * how many foundation columns hang over air (the camp can overhang on steep slopes).
	 */
	private void logCampGround(String step, Vec3 poacherPos, BlockPos cave) {
		String info = onServer(p -> {
			ServerLevel level = p.level();
			BlockPos at = BlockPos.containing(poacherPos);
			// API-CHECK: StructureManager#getStructureWithPieceAt, StructureStart#getPieces and the
			// BoundingBox minX()/maxX()/minY()/minZ()/maxZ() accessors (standard, used by CaveLocator).
			StructureStart start = caveStartAt(level, cave);

			if (start == null) {
				return "no crystal cave structure found at " + cave.toShortString();
			}

			for (StructurePiece piece : start.getPieces()) {
				if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single
						&& CaveLocator.CAMP_TEMPLATE.equals(single.getTemplateLocation())) {
					BoundingBox box = piece.getBoundingBox();
					int yard = box.minY() + 5;
					int low = Integer.MAX_VALUE;
					int high = Integer.MIN_VALUE;
					int[] xs = { box.minX() - 3, (box.minX() + box.maxX()) / 2, box.maxX() + 3 };
					int[] zs = { box.minZ() - 3, (box.minZ() + box.maxZ()) / 2, box.maxZ() + 3 };

					for (int sx : xs) {
						for (int sz : zs) {
							if (sx == xs[1] && sz == zs[1]) {
								continue;
							}

							level.getChunk(sx >> 4, sz >> 4, ChunkStatus.FULL, true);
							int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sx, sz);
							low = Math.min(low, h);
							high = Math.max(high, h);
						}
					}

					int columns = 0;
					int hanging = 0;

					for (int cx = box.minX(); cx <= box.maxX(); cx += 3) {
						for (int cz = box.minZ(); cz <= box.maxZ(); cz += 3) {
							BlockPos below = new BlockPos(cx, box.minY() - 1, cz);
							columns++;

							if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
								hanging++;
							}
						}
					}

					return "camp yard at y " + yard + " (piece y " + box.minY() + ".." + box.maxY() + "), surrounding ground y " + low + ".." + high
							+ ", foundation columns over air " + hanging + "/" + columns + (hanging > 0 ? " -> camp OVERHANGS" : "")
							+ (high - low > 12 ? " -> steep terrain" : "");
				}
			}

			return "no camp piece found in the structure at " + at.toShortString();
		});
		note(step, Result.INFO, "camp ground: " + info);
	}

	/**
	 * Fights the poachers around the player with the sword (or ignited saber) and picks up what
	 * they drop. With {@code untilEmitter} it stops as soon as an emitter is in the inventory.
	 */
	private int fightCamp(String label, boolean untilEmitter) {
		String step = "09 poacher fight (" + label + ")";
		select(Items.IRON_SWORD);
		float before = damageTaken;
		int killsBefore = data().counter("poacher_kills");
		int kills = 0;
		int fights = 0;
		Set<UUID> failed = new HashSet<>();
		CaveInfo info = knownCaves.isEmpty() ? null : caveInfo(knownCaves.get(knownCaves.size() - 1));

		if (lastCamp != null && context.computeOnClient(mc -> mc.player.position()).distanceTo(lastCamp) > 48) {
			// Back from a respawn: return to the camp.
			Vec3 spot = safeSpotNear(lastCamp);
			shortcut("teleported back to the camp after respawning (" + label + ")");
			teleport(spot, 180f);
			clearInterference(step);
		}

		while (fights < 6 && !(untilEmitter && hasEmitter())) {
			List<UUID> poachers = poachersNear(48).stream().filter(x -> !failed.contains(x)).toList();

			if (poachers.isEmpty()) {
				break;
			}

			UUID id = poachers.get(0);
			fights++;
			Target start = target(id);

			if (fight(id, 900, false, step)) {
				kills++;
				context.waitTicks(15);

				if (kills == 1) {
					shot("poacher_down");
				}

				collectDrops(step, 12);
			} else {
				failed.add(id);
				Target t = target(id);
				Vec3 me = context.computeOnClient(mc -> mc.player.position());
				note(step, Result.FAIL, "could not finish poacher " + fights + " within 900 ticks: it started at " + BlockPos.containing(start.pos()).toShortString()
						+ " " + relativeToCamp(start.pos(), info) + ", now " + (t.alive() ? BlockPos.containing(t.pos()).toShortString() + " " + relativeToCamp(t.pos(), info)
								+ String.format(Locale.ROOT, ", %.1f blocks from the player (height difference %.1f)", t.pos().distanceTo(me), t.pos().y - me.y)
								: "dead") + "; skipping it");
			}
		}

		ForceData data = data();
		String loot = "parts in inventory: " + partsSummary();
		check(step, kills > 0, kills + " poacher(s) killed, poacher_kills " + killsBefore + " -> " + data.counter("poacher_kills")
				+ ", damage taken " + Math.round(damageTaken - before) + ", " + loot
				+ ", journal cutters=" + data.hasFlag(JournalEntries.entryFlag(JournalEntries.CUTTERS)));
		return kills;
	}

	/** Goes down to the heart, walks the cavern and opens the resonant seal. */
	private void enterHeart() {
		String step = "10 seal";
		BlockPos cave = knownCaves.get(0);

		if (lastCamp != null && context.computeOnClient(mc -> mc.player.position()).distanceTo(lastCamp) > 160) {
			// Back from a respawn: return to the first camp so the locator refines to its heart.
			Vec3 camp = lastCamp;
			Vec3 spot = safeSpotNear(camp);
			shortcut("teleported back to the camp after respawning");
			teleport(spot, 180f);
		}

		heart = findHeart();

		if (heart == null || heart.sealBlocks() == 0 || (heart.dx() == 0 && heart.dz() == 0)) {
			stuck(step, "could not locate the heart piece (meditation stone or seal) near " + cave.toShortString()
					+ (heart == null ? "" : ", stone " + heart.stone().toShortString() + ", seal blocks " + heart.sealBlocks()));
		}

		Heart h = heart;
		note("10 locator", h.locatorHitStone() ? Result.PASS : Result.FAIL,
				"CaveLocator heart position " + (h.locatorHitStone() ? "is" : "is NOT") + " the meditation stone; stone at " + h.stone().toShortString()
						+ ", seal blocks " + h.sealBlocks() + ", inward axis (" + h.dx() + ", " + h.dz() + ")");

		Vec3 entrance = h.feet(22, 1, 5);
		shortcut("teleported down the camp shaft to the heart entrance tunnel (ladders and the stepped tunnel are not bot-navigable)");
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f 0", entrance.x, entrance.y, entrance.z, h.yawInward()));
		waitForChunks(step);
		clearInterference(step);
		context.waitTicks(20);
		shot("cavern");

		boolean walked = walkPath(step, h.feet(22, 1, 14), h.feet(22, 1, 22), h.feet(22, 1, 27));
		BlockPos seal = h.block(22, 2, 29);
		Vec3 sealFace = h.point(22, 2, 29, 0, 0, -0.5);
		double reach = context.computeOnClient(mc -> mc.player.getEyePosition()).distanceTo(sealFace);

		if (reach > 4.0) {
			shortcut("teleported in front of the seal: the cavern walk ended out of reach of it");
			note(step, Result.INFO, String.format(Locale.ROOT, "seal out of reach (%.1f blocks) after the walk; teleporting to the doorway", reach));
			teleport(h.feet(22, 1, 27), h.yawInward());
		}

		String before = onServer(p -> {
			ForceData d = Progression.get(p);
			return "stage " + stage(d) + ", awakened=" + d.isAwakened() + ", push=" + d.hasAbility("force_push") + ", seal refusal="
					+ ResonantSealBlock.refusalKey(p);
		});
		aimAt(sealFace);
		context.waitTick();
		String looking = lookingAt();
		note(step, Result.INFO, "before the click: " + before + "; crosshair on " + looking);
		context.getInput().pressKey(USE);
		boolean isSeal = onServer(p -> p.level().getBlockState(seal).is(WorldBlocks.RESONANT_SEAL));
		boolean open = false;

		for (int t = 0; t < 20 && !open; t++) {
			open = sealOpen(seal);
			context.waitTick();
		}

		shot("seal");

		if (!open) {
			String refusal = onServer(ResonantSealBlock::refusalKey);
			// The refusal is shown to the player as an overlay message with this translation key.
			String shown = refusal == null ? "none (the seal accepts this player)"
					: refusal + " = \"" + context.computeOnClient(mc -> net.minecraft.network.chat.Component.translatable(refusal).getString()) + "\"";
			note(step, Result.INFO, "the seal stayed closed; refusal message: " + shown + "; crosshair was on " + looking);
		}

		check(step, open, "walked the cavern=" + walked + ", seal block where expected=" + isSeal + ", right-clicked the seal: open=" + open);

		if (!open) {
			stuck(step, "the resonant seal did not open");
		}
	}

	/** Starts the trial at the meditation stone and fights the Shadow Echo, retrying on failure. */
	private void trial() {
		String step = "11 trial";
		Heart h = heart;
		float before = damageTaken;
		int attempts = 0;
		boolean won = false;

		while (attempts < 3 && !won) {
			attempts++;
			returnToHeart(step);
			boolean started = onServer(TrialManager::isInTrial);

			if (!started) {
				enterChamber(step);
				int mark = MESSAGES.size();
				// The stone is 10/16 tall: aim into its body (y + 0.35), not at its top edge.
				aimAt(h.point(22, 1, 38, 0, -0.15, 0));
				context.waitTick();
				String looking = lookingAt();
				double distance = context.computeOnClient(mc -> mc.player.getEyePosition()).distanceTo(Vec3.atCenterOf(h.stone()));
				context.getInput().pressKey(USE);
				started = waitServer(20, TrialManager::isInTrial);

				if (!started) {
					String refusal = onServer(p -> TrialManager.requirementRefusal(Progression.get(p), p.isCreative()));
					List<String> said;

					synchronized (MESSAGES) {
						said = new ArrayList<>(MESSAGES.subList(Math.min(mark, MESSAGES.size()), MESSAGES.size()));
					}

					String held = onServer(p -> BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).getPath() + (p.isShiftKeyDown() ? ", sneaking" : ""));
					note(step, Result.FAIL, "attempt " + attempts + ": using the stone did not start the trial (requirement refusal " + refusal + "); crosshair on "
							+ looking + String.format(Locale.ROOT, ", eye %.2f blocks from the stone centre", distance) + ", holding " + held + ", messages " + said);
					continue;
				}

				note(step, Result.INFO, "attempt " + attempts + ": trial started (crosshair on " + looking + String.format(Locale.ROOT, ", %.2f blocks)", distance));
			}

			context.waitTicks(20);
			shot("trial_forming");
			UUID echo = null;

			for (int i = 0; i < 80 && echo == null; i++) {
				echo = onServer(p -> {
					for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(16), x -> x instanceof ShadowEchoEntity && x.isAlive())) {
						if (p.getUUID().equals(((ShadowEchoEntity) e).ownerId())) {
							return e.getUUID();
						}
					}

					return null;
				});
				context.waitTick();
			}

			if (echo == null) {
				note(step, Result.FAIL, "attempt " + attempts + ": no Shadow Echo formed");
				continue;
			}

			context.waitTicks(5);
			shot("shadow_echo");
			select(Items.IRON_SWORD);
			boolean killed = fight(echo, 1600, true, step);
			context.waitTicks(10);
			won = data().hasFlag(CrystalGrant.TRIAL_COMPLETE);
			boolean inTrial = onServer(TrialManager::isInTrial);
			note(step, won ? Result.INFO : Result.FAIL, "attempt " + attempts + ": echo killed=" + killed + ", trial complete=" + won + ", still in trial=" + inTrial);
		}

		ForceData data = data();
		check(step, won && stage(data) == QuestStage.TRIAL && data.hasFlag(JournalEntries.entryFlag(JournalEntries.REFLECTION)),
				attempts + " attempt(s), damage taken " + Math.round(damageTaken - before) + ", stage=" + stage(data) + ", resonance=" + data.resonance);

		if (!won) {
			stuck(step, "could not win the Trial of Reflection");
		}
	}

	/** Walks from the cavern side through the seal (opening it if it closed) to the meditation stone. */
	private void enterChamber(String step) {
		Heart h = heart;
		Vec3 me = context.computeOnClient(mc -> mc.player.position());

		if (me.distanceTo(Vec3.atBottomCenterOf(h.stone())) < 6) {
			walkTo(h.feet(22, 1, 35), 0.6, 120, false, null);
			return;
		}

		walkTo(h.feet(22, 1, 27), 0.6, 300, false, null);
		BlockPos seal = h.block(22, 2, 29);

		if (!sealOpen(seal)) {
			aimAt(h.point(22, 2, 29, 0, 0, -0.5));
			context.waitTick();
			context.getInput().pressKey(USE);
			context.waitTicks(5);
		}

		walkPath(step, h.feet(22, 1, 31), h.feet(22, 1, 35));
	}

	/** Waits for the crystal to form on the stone and picks it up by walking to it. */
	private void crystal() {
		String step = "12 crystal";
		Heart h = heart;
		BlockPos stone = h.stone();

		if (returnToHeart(step)) {
			enterChamber(step);
		}

		boolean formed = waitServer(120, p -> !p.level().getEntities((Entity) null, new AABB(stone).inflate(2),
				e -> e instanceof ItemEntity item && item.getItem().is(SaberItems.KYBER_CRYSTAL)).isEmpty());
		context.waitTicks(10);
		shot("crystal_formed");
		boolean obtained = false;
		int[][] sides = { { 0, -1 }, { -1, 0 }, { 1, 0 }, { 0, 1 } };

		for (int[] side : sides) {
			walkTo(h.feet(22 + side[0], 1, 38 + side[1]), 0.5, 160, false, null);
			obtained = waitServer(50, p -> stage(Progression.get(p)) == QuestStage.CONSTRUCTION);

			if (obtained) {
				break;
			}
		}

		ForceData data = data();
		int owned = onServer(p -> {
			int n = 0;

			for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
				ItemStack stack = p.getInventory().getItem(slot);
				CrystalAttunement attunement = stack.get(ModComponents.CRYSTAL_ATTUNEMENT);

				if (stack.is(SaberItems.KYBER_CRYSTAL) && attunement != null && attunement.owner().equals(p.getUUID())) {
					n++;
				}
			}

			return n;
		});
		check(step, formed && obtained && owned == 1, "formed on the stone=" + formed + ", picked up=" + obtained + ", attuned crystals carried=" + owned
				+ ", stage=" + stage(data));

		if (owned == 0) {
			stuck(step, "no attuned crystal in the inventory");
		}
	}

	/** Leaves the chamber, braves the unstable passage, mines the veined wall and loots the chest. */
	private void hiddenChamber() {
		String step = "13 hidden chamber";
		Heart h = heart;
		float before = damageTaken;

		if (returnToHeart(step)) {
			// Respawned and back at the entrance tunnel: walk the cavern up to the side passage.
			walkPath(step, h.feet(22, 1, 14), h.feet(22, 1, 22));
		} else {
			walkTo(h.feet(22, 1, 31), 0.6, 200, false, null);
			BlockPos seal = h.block(22, 2, 29);

			if (!sealOpen(seal)) {
				aimAt(h.point(22, 2, 29, 0, 0, 0.5));
				context.waitTick();
				context.getInput().pressKey(USE);
				context.waitTicks(5);
			}
		}

		boolean walked = walkPath(step, h.feet(22, 1, 26), h.feet(26, 1, 18), h.feet(31, 1, 18), h.feet(38, 1, 18), h.feet(38, 1, 9));
		// Is the hidden chamber really hidden? Search a walkable route to the chest without mining.
		String wall = onServer(p -> {
			List<String> names = new ArrayList<>();

			for (int y = 1; y <= 3; y++) {
				for (int x = 37; x <= 39; x++) {
					BlockPos b = h.block(x, y, 7);
					names.add(BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(b).getBlock()).getPath());
				}
			}

			return String.join(" ", names);
		});
		BlockPos chestFront = h.block(38, 1, 3);
		BlockPos from = onServer(p -> p.blockPosition());
		int route = onServer(p -> walkableSteps(p.level(), from, chestFront, BoundingBox.fromCorners(from, chestFront).inflatedBy(24), 60000));
		note(step, route < 0 ? Result.INFO : Result.FAIL, "veined wall (local x 37..39, y 1..3) before mining: " + wall + "; walkable route from "
				+ from.toShortString() + " to the chest without mining: " + (route < 0 ? "none (the chamber is hidden)" : route + " steps (the chamber is NOT hidden)"));
		Vec3 eye = context.computeOnClient(mc -> mc.player.getEyePosition());
		note(step, Result.INFO, String.format(Locale.ROOT, "mining from %s, eye %.2f blocks from the upper wall block", from.toShortString(),
				eye.distanceTo(Vec3.atCenterOf(h.block(38, 2, 7)))));
		select(Items.IRON_PICKAXE);
		boolean upper = mine(step, h.block(38, 2, 7), h.point(38, 2, 7, 0, 0, 0.45));
		boolean lower = mine(step, h.block(38, 1, 7), h.point(38, 1, 7, 0, 0, 0.45));
		shot("hidden_wall");
		walkPath(step, h.feet(38, 1, 6), h.feet(38, 1, 4));
		BlockPos chest = h.block(38, 1, 2);
		boolean isChest = onServer(p -> p.level().getBlockState(chest).is(Blocks.CHEST));
		List<String> loot = new ArrayList<>();

		if (isChest && openBlock(chest, mc -> mc.player.containerMenu != mc.player.inventoryMenu && mc.player.containerMenu.slots.size() == 63)) {
			context.waitTicks(5);
			shot("hidden_chest");
			loot.addAll(context.computeOnClient(mc -> {
				List<String> names = new ArrayList<>();
				AbstractContainerMenu menu = mc.player.containerMenu;

				for (int slot = 0; slot < 27; slot++) {
					ItemStack stack = menu.slots.get(slot).getItem();

					if (!stack.isEmpty()) {
						names.add(stack.getCount() + "x " + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
						mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
					}
				}

				return names;
			}));
			context.waitTicks(5);
			closeScreen();
		}

		check(step, walked && upper && lower && isChest, "walked=" + walked + ", mined veined wall=" + (upper && lower) + ", chest found=" + isChest
				+ ", loot " + loot + ", damage taken (unstable clusters) " + Math.round(damageTaken - before));
	}

	/** Makes sure an emitter is in the inventory, visiting further poacher camps if needed. */
	private void obtainEmitter() {
		String step = "14 emitter";

		for (int visit = 2; visit <= 3 && !hasEmitter(); visit++) {
			BlockPos other = findOtherCave();

			if (other == null) {
				note(step, Result.FAIL, "no further crystal cave found to hunt poachers in");
				break;
			}

			knownCaves.add(other);
			note(step, Result.INFO, "no emitter yet after " + data().counter("poacher_kills") + " poacher kill(s); heading to cave " + visit + " at " + other.toShortString());

			if (approachCamp(step, other)) {
				fightCamp("camp " + visit, true);
			}
		}

		check(step, hasEmitter(), "poacher_kills=" + data().counter("poacher_kills") + ", parts: " + partsSummary());

		if (!hasEmitter()) {
			stuck(step, "no emitter obtained, the saber cannot be built");
		}
	}

	/** Places the workbench, shift-clicks the parts in, constructs the saber and takes it out. */
	private void buildSaber() {
		String step = "15 workbench";
		boolean threatened = !poachersNear(40).isEmpty();
		BlockPos feet = onServer(p -> p.blockPosition());
		boolean underground = feet.getY() < surfaceY(feet.getX(), feet.getZ()) - 4;

		if (threatened || underground) {
			Vec3 from = context.computeOnClient(mc -> mc.player.position());
			int fx = Mth.floor(from.x);
			int fz = Mth.floor(from.z);
			// Dry, solid, flat ground (never water) with no poachers around it.
			Vec3 spot = onServer(p -> {
				int[][] offsets = { { 80, 0 }, { -80, 0 }, { 0, 80 }, { 0, -80 }, { 120, 120 }, { -120, -120 } };

				for (int[] o : offsets) {
					Vec3 found = findFlatSpot(p.level(), fx + o[0], fz + o[1], 48);

					if (found != null && p.level().getEntities((Entity) null, new AABB(BlockPos.containing(found)).inflate(40),
							e -> e instanceof CrystalPoacher && e.isAlive()).isEmpty()) {
						return found;
					}
				}

				return null;
			});

			if (spot == null) {
				note(step, Result.FAIL, "no dry flat spot without poachers found within reach; assembling here");
			} else {
				shortcut("teleported to a dry flat spot " + Math.round(spot.distanceTo(from)) + " blocks away to assemble the saber in peace ("
						+ (threatened ? "poachers nearby" : "underground") + ")");
				teleport(spot, 0f);
				note(step, Result.INFO, "assembly spot " + BlockPos.containing(spot).toShortString());
			}
		}

		clearInterference(step);
		BlockPos bench = placeBlock(SaberItems.WORKBENCH.asItem(), state -> state.is(SaberItems.WORKBENCH), false);

		if (bench == null) {
			BlockPos at = onServer(p -> p.blockPosition());
			note(step, Result.FAIL, "could not place the workbench at " + at.toShortString() + " (standing on "
					+ onServer(p -> BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(at.below()).getBlock()).getPath()) + "); trying another spot");
			Vec3 spot = onServer(p -> findFlatSpot(p.level(), at.getX() + 16, at.getZ(), 48));

			if (spot != null) {
				shortcut("teleported to another flat spot to place the workbench");
				teleport(spot, 0f);
				bench = placeBlock(SaberItems.WORKBENCH.asItem(), state -> state.is(SaberItems.WORKBENCH), false);
			}
		}

		if (bench == null) {
			stuck(step, "could not place the crafted workbench");
		}

		if (!openBlock(bench, mc -> mc.player.containerMenu instanceof LightsaberWorkbenchMenu)) {
			stuck(step, "the workbench screen did not open");
		}

		List<Item> parts = new ArrayList<>(List.of(SaberItems.KYBER_CRYSTAL, SaberItems.GRIP_WRAPPED, SaberItems.POMMEL_WEIGHTED));
		parts.add(count(SaberItems.EMITTER_GUARDIAN) > 0 ? SaberItems.EMITTER_GUARDIAN : SaberItems.EMITTER_DUELIST);

		for (Item part : parts) {
			context.runOnClient(mc -> {
				AbstractContainerMenu menu = mc.player.containerMenu;

				for (int slot = SaberAssembly.SIZE; slot < menu.slots.size(); slot++) {
					if (menu.slots.get(slot).getItem().is(part)) {
						mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, mc.player);
						return;
					}
				}
			});
			context.waitTicks(3);
		}

		boolean ready = waitClient(40, mc -> mc.player.containerMenu instanceof LightsaberWorkbenchMenu menu && menu.status() == SaberAssembly.Status.READY);
		String preview = context.computeOnClient(mc -> mc.player.containerMenu instanceof LightsaberWorkbenchMenu menu
				? "status " + menu.status() + ", damage " + menu.data(LightsaberWorkbenchMenu.DATA_DAMAGE_X100) / 100.0
						+ ", speed " + menu.data(LightsaberWorkbenchMenu.DATA_SPEED_X100) / 100.0
						+ ", stamina " + menu.data(LightsaberWorkbenchMenu.DATA_STAMINA)
						+ ", parry " + menu.data(LightsaberWorkbenchMenu.DATA_PARRY_X10) / 10.0
				: "screen closed");
		shot("workbench_ready");
		note(step, ready ? Result.INFO : Result.FAIL, "parts shift-clicked in: " + preview);

		if (ready) {
			boolean clicked = context.tryClickScreenButton("container.oldrepublic.lightsaber_workbench.construct");
			note(step, clicked ? Result.INFO : Result.FAIL, "clicked the Construct button=" + clicked);
		}

		boolean built = waitClient(40, mc -> mc.player.containerMenu.slots.size() > SaberAssembly.SABER
				&& mc.player.containerMenu.slots.get(SaberAssembly.SABER).getItem().is(SaberItems.LIGHTSABER));
		shot("workbench_built");

		if (built) {
			context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, SaberAssembly.SABER, 0, ContainerInput.QUICK_MOVE, mc.player));
			context.waitTicks(5);
		}

		closeScreen();
		context.waitTicks(10);
		ForceData data = data();
		boolean ownsSaber = onServer(p -> {
			for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
				ItemStack stack = p.getInventory().getItem(slot);

				if (stack.is(SaberItems.LIGHTSABER) && SaberAssembly.ownsSaber(stack, p.getUUID())) {
					return true;
				}
			}

			return false;
		});
		check(step, built && ownsSaber && stage(data) == QuestStage.COMPLETE && data.hasFlag("resonance:first_saber"),
				"constructed=" + built + ", own saber in inventory=" + ownsSaber + ", stage=" + stage(data) + ", skill points=" + data.skillPoints
						+ ", level=" + data.level + ", resonance=" + data.resonance);

		if (!ownsSaber) {
			stuck(step, "no lightsaber in the inventory");
		}
	}

	/** Ignites the saber with G, guards against a poacher's blaster fire, then fights with it. */
	private void saberCombat() {
		String step = "16 saber";

		if (!select(SaberItems.LIGHTSABER)) {
			stuck(step, "could not take the saber in hand");
		}

		context.getInput().pressKey(InputConstants.KEY_G);
		boolean ignited = waitServer(10, p -> SaberComponents.isIgnited(p.getMainHandItem()));
		context.waitTicks(10);
		shot("saber_ignited");
		check(step + " ignite", ignited, "pressed G: ignited=" + ignited);

		if (!findLivePoachers(step)) {
			note(step + " guard", Result.FAIL, "no live poachers left to test the guard against");
			return;
		}

		guardTest(step + " guard");

		float before = damageTaken;
		int killsBefore = data().counter("poacher_kills");
		int kills = 0;

		for (int i = 0; i < 4; i++) {
			List<UUID> poachers = poachersNear(48);

			if (poachers.isEmpty()) {
				break;
			}

			if (fight(poachers.get(0), 900, false, step)) {
				kills++;
			}
		}

		check(step + " fight", kills > 0, kills + " poacher(s) cut down with the saber, poacher_kills " + killsBefore + " -> " + data().counter("poacher_kills")
				+ ", damage taken " + Math.round(damageTaken - before));
	}

	/**
	 * Holds the guard against a camp, turning to face whichever poacher shows its shot tell (as a
	 * player would; the guard covers a 100 degree front arc), then tries timed parries, recording
	 * what happens to the bolts and where every hit that got through came from.
	 */
	private void guardTest(String step) {
		List<UUID> poachers = poachersNear(64);

		if (poachers.isEmpty()) {
			note(step, Result.FAIL, "the poachers are gone");
			return;
		}

		UUID id = poachers.get(0);
		Target target = target(id);
		walkTo(target.pos(), 10, 300, true, null);

		Set<UUID> seen = new HashSet<>();
		Set<UUID> deflected = new HashSet<>();
		Set<UUID> returned = new HashSet<>();
		float healthBefore = damageTaken;
		float minStamina = Float.MAX_VALUE;
		boolean broke = false;

		UUID threat = id;
		int threatTicks = 0;
		HITS.clear();
		recordHits = true;
		context.getInput().holdKey(USE);

		for (int t = 0; t < 240; t++) {
			Target now = target(id);

			if (!now.alive()) {
				break;
			}

			trackBolts(seen, deflected, returned);
			SaberGuard.GuardSnapshot guard = onServer(SaberGuard::snapshot);

			if (guard != null) {
				minStamina = Math.min(minStamina, guard.stamina());
				broke |= guard.broken();
			}

			// Face the poacher about to fire, as a player would, and keep facing it while its bolt flies.
			UUID charging = chargingPoacher();

			if (charging != null) {
				threat = charging;
				threatTicks = 20;
			} else if (--threatTicks <= 0) {
				threat = id;
			}

			Target facing = target(threat);
			Target aim = facing.alive() ? facing : now;
			aimAt(aim.pos().add(0, aim.height() * 0.8, 0));
			tickMe(step);

			if (t == 60) {
				shot("saber_guard");
			}
		}

		context.getInput().releaseKey(USE);
		recordHits = false;
		List<String> hits;

		synchronized (HITS) {
			hits = new ArrayList<>(HITS);
		}

		String held = "held guard 240 ticks: bolts seen " + seen.size() + " (within 32 blocks, misses included), deflected " + deflected.size()
				+ " (of which aimed back at a poacher " + returned.size() + "), damage taken " + Math.round(damageTaken - healthBefore) + ", min stamina "
				+ (minStamina == Float.MAX_VALUE ? "n/a" : Math.round(minStamina)) + ", guard broke=" + broke + ", hits " + hits;
		check(step, deflected.size() > 0, held);

		// Timed parries: raise the guard just as a bolt is about to arrive.
		int attempts = 0;
		int parriesBefore = returned.size();
		seen.clear();

		for (int t = 0; t < 400 && attempts < 4; t++) {
			Target now = target(id);

			if (!now.alive()) {
				break;
			}

			boolean charging = onServer(p -> p.level().getEntity(id) instanceof CrystalPoacher poacher && poacher.isCharging());
			aimAt(now.pos().add(0, now.height() * 0.8, 0));

			if (charging) {
				attempts++;
				context.waitTicks(8);
				context.getInput().holdKey(USE);

				for (int i = 0; i < 16; i++) {
					trackBolts(seen, deflected, returned);
					tickMe(step);
				}

				context.getInput().releaseKey(USE);
				context.waitTicks(10);
			} else {
				tickMe(step);
			}
		}

		ForceData data = data();
		note(step + " parry", returned.size() > parriesBefore ? Result.PASS : Result.INFO, attempts + " timed guard raise(s), bolts sent straight back "
				+ (returned.size() - parriesBefore) + " (estimated from bolt direction), journal returned_fire="
				+ data.hasFlag(JournalEntries.entryFlag(JournalEntries.RETURNED_FIRE)));
	}

	private void finish() {
		String step = "17 journal and force screens";
		clearInterference(step);
		context.getInput().pressKey(InputConstants.KEY_J);
		boolean journal = waitClient(40, mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("JournalScreen"));
		context.waitTicks(10);
		shot("journal");
		closeScreen();
		context.getInput().pressKey(InputConstants.KEY_K);
		boolean force = waitClient(40, mc -> mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().equals("ForceScreen"));
		context.waitTicks(10);
		shot("force_screen");
		closeScreen();

		ForceData data = data();
		List<String> missing = new ArrayList<>();

		for (String entry : JournalEntries.ENTRIES) {
			if (!data.hasFlag(JournalEntries.entryFlag(entry))) {
				missing.add(entry);
			}
		}

		check(step, journal && force, "J opened journal=" + journal + ", K opened force screen=" + force);
		note("18 final state", stage(data) == QuestStage.COMPLETE ? Result.PASS : Result.FAIL, "stage=" + stage(data) + ", level " + data.level + ", xp " + data.xp
				+ ", resonance " + data.resonance + ", skill points " + data.skillPoints + ", push mastery " + data.mastery("force_push")
				+ ", journal entries missing " + missing + ", total damage taken " + Math.round(damageTaken));
		server.runCommand("execute as @a run oldrepublic progress");
	}

	// ---------------------------------------------------------------------------------------------
	// Movement and combat
	// ---------------------------------------------------------------------------------------------

	/**
	 * Walks towards {@code target} (horizontal distance) with forward held, jumping when blocked.
	 * Stops at {@code tolerance}, when {@code done} becomes true, on timeout or when stuck.
	 */
	private boolean walkTo(Vec3 target, double tolerance, int timeout, boolean sprint, @Nullable BooleanSupplier done) {
		Vec3 lastCheck = null;
		int stalls = 0;
		walkStop = "timeout after " + timeout + " ticks";

		try {
			for (int t = 0; t < timeout; t++) {
				Me me = me();

				if (me.dead()) {
					died("walk");
				}

				double distance = me.pos().subtract(target).horizontalDistance();

				if (distance <= tolerance || (done != null && t % 10 == 0 && done.getAsBoolean())) {
					walkStop = "";
					return true;
				}

				if (me.onGround()) {
					String drop = dropAhead(me.pos(), target);

					if (drop != null) {
						walkStop = drop + " at " + BlockPos.containing(me.pos()).toShortString() + ", " + Math.round(distance) + " blocks from the target";
						return false;
					}
				}

				if (t % 40 == 0) {
					if (lastCheck != null && me.pos().distanceTo(lastCheck) < 0.5) {
						stalls++;

						if (stalls >= 3) {
							walkStop = "stalled at " + BlockPos.containing(me.pos()).toShortString() + ", " + Math.round(distance) + " blocks from the target, ahead: "
									+ describeAhead(me.pos(), target);
							return false;
						}
					} else {
						stalls = 0;
					}

					lastCheck = me.pos();
				}

				aimFlat(me.eye(), target);
				setForward(true, sprint);
				keepAlive("walk");

				if (me.collided() && me.onGround()) {
					context.getInput().pressKey(JUMP);
				} else {
					tickMe("walk");
				}
			}

			return false;
		} finally {
			setForward(false, false);
		}
	}

	/**
	 * Looks 0.8 and 1.6 blocks ahead towards {@code target} for a drop: no solid ground (or water)
	 * from one block above the feet down to three below, or lava. Returns a description or null.
	 */
	private @Nullable String dropAhead(Vec3 pos, Vec3 target) {
		Vec3 flat = new Vec3(target.x - pos.x, 0, target.z - pos.z);

		if (flat.lengthSqr() < 1.0E-4) {
			return null;
		}

		Vec3 dir = flat.normalize();
		return context.computeOnClient(mc -> {
			for (double ahead : new double[] { 0.8, 1.6 }) {
				Vec3 probe = pos.add(dir.scale(ahead));
				int x = Mth.floor(probe.x);
				int z = Mth.floor(probe.z);
				int feet = Mth.floor(pos.y + 0.01);
				boolean ground = false;

				for (int y = feet + 1; y >= feet - 3 && !ground; y--) {
					BlockPos at = new BlockPos(x, y, z);
					BlockState state = mc.level.getBlockState(at);

					// API-CHECK: BlockState#getFluidState().isEmpty() (as in PushFx).
					if (state.is(Blocks.LAVA)) {
						return "lava ahead at " + at.toShortString();
					}

					ground = !state.getCollisionShape(mc.level, at).isEmpty() || !state.getFluidState().isEmpty();
				}

				if (!ground) {
					return "drop of more than 3 blocks ahead at " + x + ", " + z;
				}
			}

			return null;
		});
	}

	/**
	 * A standing spot on the surface 2 to 6 blocks from {@code target} whose ground is solid (not
	 * fluid) and within 3 blocks of the target's height; the target itself if there is none.
	 */
	private Vec3 safeSpotNear(Vec3 target) {
		int tx = Mth.floor(target.x);
		int tz = Mth.floor(target.z);
		int ty = Mth.floor(target.y);
		int[][] dirs = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 }, { 1, 1 }, { -1, 1 }, { 1, -1 }, { -1, -1 } };

		for (int r = 2; r <= 6; r++) {
			for (int[] d : dirs) {
				int x = tx + d[0] * r;
				int z = tz + d[1] * r;
				Vec3 spot = onServer(p -> {
					ServerLevel level = p.level();
					level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
					int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
					BlockPos ground = new BlockPos(x, y - 1, z);
					BlockState state = level.getBlockState(ground);
					boolean solid = !state.getCollisionShape(level, ground).isEmpty() && state.getFluidState().isEmpty();
					boolean free = level.getBlockState(new BlockPos(x, y, z)).getCollisionShape(level, new BlockPos(x, y, z)).isEmpty()
							&& level.getBlockState(new BlockPos(x, y + 1, z)).getCollisionShape(level, new BlockPos(x, y + 1, z)).isEmpty();
					return solid && free && Math.abs(y - ty) <= 3 ? new Vec3(x + 0.5, y, z + 0.5) : null;
				});

				if (spot != null) {
					return spot;
				}
			}
		}

		return target;
	}

	/** Teleports the player (a logged shortcut at the call site) and waits for the chunks. */
	private void teleport(Vec3 pos, float yaw) {
		server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f 0", pos.x, pos.y, pos.z, yaw));
		context.waitTicks(2);
		waitForChunks("teleport");
	}

	/**
	 * Handles a death: logs it as a FAIL with its cause, presses Respawn on the death screen and
	 * throws {@link Died} so the step is retried from the respawn point.
	 */
	private void died(String step) {
		releaseAll();
		String cause = onServer(p -> {
			try {
				// API-CHECK: LivingEntity#getCombatTracker + CombatTracker#getDeathMessage (standard).
				return p.getCombatTracker().getDeathMessage().getString();
			} catch (RuntimeException e) {
				return "unknown cause (" + e.getClass().getSimpleName() + ")";
			}
		});
		Vec3 at = context.computeOnClient(mc -> mc.player.position());
		note(step, Result.FAIL, "the player died at " + BlockPos.containing(at).toShortString() + ": " + cause);
		context.waitTicks(20);
		shot("death");
		boolean alive = false;

		for (int t = 0; t < 200 && !alive; t++) {
			if (t % 10 == 0) {
				// Real click on the death screen's Respawn button (enabled after a short delay).
				context.tryClickScreenButton("deathScreen.respawn");
			}

			context.waitTick();
			alive = context.computeOnClient(mc -> mc.player != null && mc.player.isAlive() && mc.gui.screen() == null);
		}

		if (!alive) {
			stuck(step, "died (" + cause + ") and could not respawn through the death screen");
		}

		waitForChunks(step);
		lastRescueTick = -1000;
		Vec3 spawn = context.computeOnClient(mc -> mc.player.position());
		boolean kept = count(Items.IRON_SWORD) > 0;
		note(step, Result.INFO, "respawned at " + BlockPos.containing(spawn).toShortString() + " with the inventory " + (kept ? "kept" : "LOST"));
		throw new Died(step + ": " + cause);
	}

	/** Back near the heart after a respawn: teleports to the cavern entrance tunnel if far away. */
	private boolean returnToHeart(String step) {
		Heart h = heart;
		Vec3 me = context.computeOnClient(mc -> mc.player.position());

		if (h != null && me.distanceTo(Vec3.atBottomCenterOf(h.stone())) > 40) {
			Vec3 entrance = h.feet(22, 1, 5);
			shortcut("returned to the cave after respawning (" + step + ")");
			teleport(entrance, h.yawInward());
			clearInterference(step);
			return true;
		}

		return false;
	}

	/** The blocks just ahead towards {@code target} at feet, head and above-head height (diagnostics). */
	private String describeAhead(Vec3 pos, Vec3 target) {
		Vec3 flat = new Vec3(target.x - pos.x, 0, target.z - pos.z);
		Vec3 probe = flat.lengthSqr() < 1.0E-4 ? pos : pos.add(flat.normalize().scale(0.8));
		return context.computeOnClient(mc -> {
			List<String> names = new ArrayList<>();

			for (int dy = -1; dy <= 2; dy++) {
				BlockPos at = BlockPos.containing(probe.x, pos.y + 0.01 + dy, probe.z);
				names.add("y" + (dy >= 0 ? "+" : "") + dy + " " + BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(at).getBlock()).getPath());
			}

			return String.join(", ", names);
		});
	}

	/** A block with a partial collision shape (like a crystal cluster) right ahead at feet or head height, or null. */
	private @Nullable BlockPos partialObstacleAhead(Vec3 pos, Vec3 target) {
		Vec3 flat = new Vec3(target.x - pos.x, 0, target.z - pos.z);

		if (flat.lengthSqr() < 1.0E-4) {
			return null;
		}

		Vec3 dir = flat.normalize();
		return context.computeOnClient(mc -> {
			for (double ahead : new double[] { 0.6, 1.2 }) {
				Vec3 probe = pos.add(dir.scale(ahead));

				for (int dy = 0; dy <= 1; dy++) {
					BlockPos at = BlockPos.containing(probe.x, pos.y + 0.01 + dy, probe.z);
					BlockState state = mc.level.getBlockState(at);

					if (!state.getCollisionShape(mc.level, at).isEmpty() && !state.isCollisionShapeFullBlock(mc.level, at) && !state.is(Blocks.CHEST)) {
						return at;
					}
				}
			}

			return null;
		});
	}

	/**
	 * Walks a list of waypoints; returns false if any leg did not arrive. A leg that stalls on a
	 * partial block in the way (a crystal cluster on the cavern floor) mines it, as a player would,
	 * and tries the leg once more.
	 */
	private boolean walkPath(String step, Vec3... points) {
		boolean all = true;

		for (Vec3 point : points) {
			boolean arrived = walkTo(point, 0.6, 300, false, null);

			if (!arrived) {
				Vec3 at = context.computeOnClient(mc -> mc.player.position());
				note(step, Result.INFO, "walk leg to " + BlockPos.containing(point).toShortString() + " stopped at " + BlockPos.containing(at).toShortString()
						+ ": " + walkStop);
				BlockPos obstacle = partialObstacleAhead(at, point);

				if (obstacle != null) {
					String name = onServer(p -> BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(obstacle).getBlock()).getPath());
					select(Items.IRON_PICKAXE);
					boolean mined = mine(obstacle, Vec3.atCenterOf(obstacle));
					note(step, Result.INFO, "mined the " + name + " in the way at " + obstacle.toShortString() + ": " + mined);
					arrived = walkTo(point, 0.6, 300, false, null);

					if (!arrived) {
						note(step, Result.INFO, "walk leg to " + BlockPos.containing(point).toShortString() + " still stopped: " + walkStop);
					}
				}
			}

			all &= arrived;
		}

		return all;
	}

	/** What the crosshair is on (client hit result), for logs. */
	private String lookingAt() {
		return context.computeOnClient(mc -> {
			HitResult hit = mc.hitResult;

			if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
				return "block " + BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(block.getBlockPos()).getBlock()).getPath() + " at "
						+ block.getBlockPos().toShortString();
			}

			if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
				return "an entity";
			}

			return "nothing in reach";
		});
	}

	/**
	 * Melee fight: chase (sprinting, so a kiting ranged enemy cannot simply walk away), aim at the
	 * middle of the target's body and swing whenever the attack is fully charged and the crosshair
	 * is on the target, which also means it is within reach. With {@code usePush} also charges a
	 * Force Push when close and affordable. A target that stays out of reach above or below the
	 * bot (on a ledge it cannot climb to) is reached with a logged teleport.
	 *
	 * @return whether the target died
	 */
	private boolean fight(UUID id, int timeout, boolean usePush, String step) {
		int lastPush = -1000;
		int blocked = 0;
		int outOfReach = 0;
		int swings = 0;

		try {
			for (int t = 0; t < timeout; t++) {
				Target target = target(id);

				if (!target.alive()) {
					return true;
				}

				Me me = me();

				if (me.dead()) {
					died(step);
				}

				keepAlive(step);
				aimAt(target.pos().add(0, target.height() * 0.5, 0));
				double distance = me.pos().subtract(target.pos()).horizontalDistance();
				double rise = target.pos().y - me.pos().y;
				String drop = distance > 1.8 && me.onGround() ? dropAhead(me.pos(), target.pos()) : null;
				setForward(distance > 1.8 && drop == null, distance > 2.5 && drop == null);
				outOfReach = me.picked() == target.id() ? 0 : outOfReach + 1;

				if (outOfReach >= 160 && Math.abs(rise) > 2.5 && distance < 8) {
					// Close by but on a ledge the bot cannot climb (or below a drop it will not take).
					outOfReach = 0;
					Vec3 spot = safeSpotNear(target.pos());
					shortcut("teleported next to a fight target " + Math.round(rise) + " blocks above/below the bot (" + step + ")");
					note(step, Result.INFO, "fight target out of reach " + Math.round(rise) + " blocks up; teleporting to " + BlockPos.containing(spot).toShortString());
					teleport(spot, 0f);
					continue;
				}

				if (drop != null && ++blocked >= 60) {
					// The target stays across a drop: hop over to safe ground next to it.
					blocked = 0;
					Vec3 spot = safeSpotNear(target.pos());
					shortcut("teleported across a drop towards a fight target (" + step + ")");
					note(step, Result.INFO, "fight target behind a drop (" + drop + "); teleporting to " + BlockPos.containing(spot).toShortString());
					teleport(spot, 0f);
					continue;
				} else if (drop == null) {
					blocked = 0;
				}

				if (usePush && t - lastPush > 100 && distance < 5 && pushReady()) {
					lastPush = t;
					setForward(false, false);
					context.getInput().holdKeyFor(InputConstants.KEY_R, PUSH_HOLD_TICKS);
					continue;
				}

				if (me.strength() >= 0.95f && me.picked() == target.id()) {
					swings++;
					context.getInput().pressKey(ATTACK);
				} else if (me.collided() && me.onGround()) {
					context.getInput().pressKey(JUMP);
				} else {
					tickMe(step);
				}
			}

			boolean dead = !target(id).alive();

			if (!dead) {
				note(step, Result.INFO, "fight timed out after " + timeout + " ticks with " + swings + " charged swing(s) on the target");
			}

			return dead;
		} finally {
			setForward(false, false);
		}
	}

	/** Holds the attack key on a block until it breaks. */
	private boolean mine(BlockPos pos, Vec3 face) {
		return mine(null, pos, face);
	}

	/**
	 * Holds the attack key on a block until it breaks; with a step, logs what happened. Walks into
	 * reach first and re-aims every tick: a knock (an unstable cluster's discharge, a hit) moves
	 * the bot while it mines, and with a fixed view the crosshair can drift off the block or out of
	 * reach, which silently restarts or stops the mining.
	 */
	private boolean mine(@Nullable String step, BlockPos pos, Vec3 face) {
		String was = blockName(pos);

		if (onServer(p -> p.level().getBlockState(pos).isAir())) {
			if (step != null) {
				note(step, Result.INFO, "mine " + pos.toShortString() + ": already air (" + was + ")");
			}

			return true;
		}

		approachToMine(pos, face);
		aimAt(face);
		context.waitTick();
		String looking = lookingAt();
		double reachAtStart = eyeDistance(face);
		String tool = onServer(p -> BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).getPath());
		float damageBefore = damageTaken;
		context.getInput().holdKey(ATTACK);
		boolean broken = false;
		int held = 0;
		int onTarget = 0;
		int reapproached = 0;

		try {
			for (; held < 160 && !broken; held++) {
				aimAt(face);
				tickMe("mine");

				if (pos.equals(crosshairBlock())) {
					onTarget++;
				} else if (held % 20 == 19 && reapproached < 3) {
					// Knocked away or out of reach: step back in, then keep mining.
					reapproached++;
					context.getInput().releaseKey(ATTACK);
					approachToMine(pos, face);
					aimAt(face);
					context.waitTick();
					context.getInput().holdKey(ATTACK);
				}

				broken = onServer(p -> p.level().getBlockState(pos).isAir());
			}
		} finally {
			context.getInput().releaseKey(ATTACK);
		}

		if (step != null) {
			note(step, Result.INFO, String.format(Locale.ROOT, "mine %s (%s): crosshair on %s, eye %.2f blocks from the aim point, tool %s, held attack %d ticks"
					+ " (crosshair on the block %d of them, re-approached %d times, damage taken meanwhile %d), broken=%s, now %s", pos.toShortString(), was,
					looking, reachAtStart, tool, held, onTarget, reapproached, Math.round(damageTaken - damageBefore), broken, blockName(pos)));
		}

		return broken;
	}

	/** Walks towards a block to mine until the aim point is comfortably within block reach. */
	private void approachToMine(BlockPos pos, Vec3 face) {
		if (eyeDistance(face) > MINE_REACH) {
			walkTo(Vec3.atBottomCenterOf(pos), 1.2, 80, false, () -> eyeDistance(face) <= MINE_REACH);
		}
	}

	private double eyeDistance(Vec3 point) {
		return context.computeOnClient(mc -> mc.player.getEyePosition().distanceTo(point));
	}

	/** The block under the crosshair (client hit result), or null. */
	private @Nullable BlockPos crosshairBlock() {
		return context.computeOnClient(mc -> mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null);
	}

	private String blockName(BlockPos pos) {
		return onServer(p -> BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(pos).getBlock()).getPath());
	}

	/**
	 * Breadth-first search over standing spots (two free blocks over solid ground, steps of up to
	 * one block up or three down) from {@code from} to {@code to}, inside {@code bounds}. Returns
	 * the path length, or -1 when there is none. Server thread only.
	 */
	private static int walkableSteps(ServerLevel level, BlockPos from, BlockPos to, BoundingBox bounds, int limit) {
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		Map<BlockPos, Integer> seen = new java.util.HashMap<>();
		queue.add(from);
		seen.put(from, 0);

		while (!queue.isEmpty() && seen.size() < limit) {
			BlockPos cur = queue.poll();
			int d = seen.get(cur);

			if (cur.getX() == to.getX() && cur.getZ() == to.getZ() && Math.abs(cur.getY() - to.getY()) <= 1) {
				return d;
			}

			for (int[] dir : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
				for (int dy = 1; dy >= -3; dy--) {
					BlockPos next = cur.offset(dir[0], dy, dir[1]);

					if (!bounds.isInside(next) || seen.containsKey(next)) {
						continue;
					}

					boolean headroom = dy <= 0 || passable(level, cur.above(2));

					if (headroom && passable(level, next) && passable(level, next.above()) && !passable(level, next.below())) {
						seen.put(next, d + 1);
						queue.add(next);
						break;
					}

					if (!passable(level, next)) {
						break;
					}
				}
			}
		}

		return -1;
	}

	private static boolean passable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getCollisionShape(level, pos).isEmpty() && !state.is(Blocks.LAVA);
	}

	private void setForward(boolean forward, boolean sprint) {
		if (forward != forwardHeld) {
			if (forward) {
				context.getInput().holdKey(FORWARD);
			} else {
				context.getInput().releaseKey(FORWARD);
				context.getInput().releaseKey(SPRINT);
			}

			forwardHeld = forward;
		}

		if (forward) {
			if (sprint) {
				context.getInput().holdKey(SPRINT);
			} else {
				context.getInput().releaseKey(SPRINT);
			}
		}
	}

	private void releaseAll() {
		try {
			setForward(false, false);
			context.getInput().releaseKey(SNEAK);
			context.getInput().releaseKey(USE);
			context.getInput().releaseKey(ATTACK);
			context.getInput().releaseKey(InputConstants.KEY_R);
		} catch (RuntimeException e) {
			LOG.warn("PLAYTHROUGH-LOG: could not release keys", e);
		}
	}

	/** One tick, tracking damage taken from the client's view of the player's health. */
	private void tickMe(String step) {
		float before = context.computeOnClient(mc -> mc.player.getHealth());
		context.waitTick();
		ticks++;
		float after = context.computeOnClient(mc -> mc.player.getHealth());

		if (after < before) {
			damageTaken += before - after;
		}
	}

	/** Keep-alive for fights a bot cannot dodge: resistance and an instant heal when health is low. */
	private void keepAlive(String step) {
		float health = context.computeOnClient(mc -> mc.player.getHealth());

		if (health < RESCUE_HEALTH && ticks - lastRescueTick > 200) {
			lastRescueTick = ticks;
			shortcut("keep-alive effects (resistance + instant health) at low health during " + step);
			server.runCommand("effect give @a minecraft:resistance 20 2 true");
			server.runCommand("effect give @a minecraft:instant_health 1 1 true");
		}
	}

	private Me me() {
		return context.computeOnClient(mc -> new Me(mc.player.position(), mc.player.getEyePosition(), mc.player.horizontalCollision, mc.player.onGround(),
				mc.player.getAttackStrengthScale(0f), mc.crosshairPickEntity == null ? -1 : mc.crosshairPickEntity.getId(), mc.player.getHealth(),
				mc.player.isDeadOrDying()));
	}

	private void aimAt(Vec3 point) {
		Vec3 eye = context.computeOnClient(mc -> mc.player.getEyePosition());
		double dx = point.x - eye.x;
		double dy = point.y - eye.y;
		double dz = point.z - eye.z;
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
		float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
		context.getInput().lookAt(yaw, pitch);
	}

	private void aimFlat(Vec3 eye, Vec3 point) {
		float yaw = (float) (Mth.atan2(point.z - eye.z, point.x - eye.x) * Mth.RAD_TO_DEG) - 90f;
		context.getInput().lookAt(yaw, 15f);
	}

	private Target target(UUID id) {
		return onServer(p -> {
			Entity e = p.level().getEntity(id);
			return e == null || !e.isAlive() ? new Target(false, Vec3.ZERO, 0, -1) : new Target(true, e.position(), e.getBbHeight(), e.getId());
		});
	}

	private @Nullable Target nearest(List<UUID> ids) {
		Vec3 me = context.computeOnClient(mc -> mc.player.position());
		return ids.stream().map(this::target).filter(Target::alive).min(Comparator.comparingDouble(t -> t.pos().distanceToSqr(me))).orElse(null);
	}

	private boolean pushReady() {
		return onServer(p -> {
			ForceData d = Progression.get(p);
			return d.cooldown("force_push") == 0 && d.energy >= 25f;
		});
	}

	/** Live crystal poachers within {@code radius} of the player, nearest first. */
	/** The nearest poacher within 32 blocks that is showing its shot tell, or null. */
	private @Nullable UUID chargingPoacher() {
		return onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(32), e -> e instanceof CrystalPoacher poacher && poacher.isAlive()
				&& poacher.isCharging()).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::getUUID).orElse(null));
	}

	private List<UUID> poachersNear(double radius) {
		return onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(radius), e -> e instanceof CrystalPoacher && e.isAlive())
				.stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::getUUID).toList());
	}

	/** Walks over the item drops near the player. */
	private void collectDrops(String step, double radius) {
		for (int i = 0; i < 8; i++) {
			List<Vec3> items = onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(radius), e -> e instanceof ItemEntity && e.isAlive())
					.stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(p))).map(Entity::position).toList());

			if (items.isEmpty()) {
				return;
			}

			if (!walkTo(items.get(0), 0.4, 160, false, null)) {
				return;
			}

			context.waitTicks(12);
		}
	}

	/** Records the blaster bolts near the player: seen, deflected, and deflected straight back at a poacher. */
	/**
	 * One hit for the guard log: damage type, damage taken, how far off the player's facing it came
	 * from (a bolt by its direction of travel, anything else by its source position) and whether
	 * the guard was up. The documented guard arc is 100 degrees, so up to 50 off should be blocked.
	 */
	private static String describeHit(ServerPlayer player, DamageSource source, float taken) {
		Vec3 from = source.getDirectEntity() instanceof BlasterBolt bolt ? bolt.getDeltaMovement().reverse()
				: source.getSourcePosition() == null ? null : source.getSourcePosition().subtract(player.position());
		String angle = "?";

		if (from != null && from.horizontal().lengthSqr() > 1.0e-6) {
			Vec3 facing = Vec3.directionFromRotation(0f, player.getYRot());
			double cos = Mth.clamp(facing.dot(from.horizontal().normalize()), -1.0, 1.0);
			angle = Math.round(Math.toDegrees(Math.acos(cos))) + " deg off facing";
		}

		return String.format(Locale.ROOT, "%s %.1f from %s, guard up=%s", source.getMsgId(), taken, angle, !SaberGuard.guardingSaber(player).isEmpty());
	}

	private void trackBolts(Set<UUID> seen, Set<UUID> deflected, Set<UUID> returned) {
		List<Bolt> bolts = onServer(p -> {
			List<Bolt> out = new ArrayList<>();

			for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(32), x -> x instanceof BlasterBolt)) {
				BlasterBolt bolt = (BlasterBolt) e;
				boolean towardPoacher = false;

				if (bolt.isDeflected()) {
					Vec3 velocity = bolt.getDeltaMovement();

					for (Entity poacher : p.level().getEntities((Entity) null, bolt.getBoundingBox().inflate(24), x -> x instanceof CrystalPoacher && x.isAlive())) {
						Vec3 to = poacher.getEyePosition().subtract(bolt.position());

						if (velocity.lengthSqr() > 1.0e-4 && to.lengthSqr() > 1.0e-4 && velocity.normalize().dot(to.normalize()) > 0.97) {
							towardPoacher = true;
						}
					}
				}

				out.add(new Bolt(bolt.getUUID(), bolt.isDeflected(), towardPoacher));
			}

			return out;
		});

		for (Bolt bolt : bolts) {
			seen.add(bolt.id());

			if (bolt.deflected() && deflected.add(bolt.id()) && bolt.towardPoacher()) {
				returned.add(bolt.id());
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// World, items and screens
	// ---------------------------------------------------------------------------------------------

	/** Summons three slowed husks {@code dz} blocks south (or north) of the player and returns their ids. */
	private List<UUID> summonTargets(int dz) {
		BlockPos feet = onServer(p -> p.blockPosition());
		Set<UUID> before = new HashSet<>(husksNear());

		for (int i = -1; i <= 1; i++) {
			int x = feet.getX() + i;
			int z = feet.getZ() + dz;
			int y = surfaceY(x, z);
			server.runCommand(String.format(Locale.ROOT, "summon minecraft:husk %d %d %d {PersistenceRequired:1b,Rotation:[180f,0f],"
					+ "active_effects:[{id:\"minecraft:slowness\",amplifier:3,duration:-1,show_particles:0b}]}", x, y, z));
		}

		context.waitTicks(2);
		return husksNear().stream().filter(id -> !before.contains(id)).toList();
	}

	/** Server-side motion of one entity in one game tick. */
	private record Motion(long time, Vec3 pos, Vec3 velocity, boolean onGround, boolean collided) {
	}

	private Map<UUID, Motion> huskMotion(List<UUID> ids) {
		return onServer(p -> {
			Map<UUID, Motion> result = new LinkedHashMap<>();
			long time = p.level().getGameTime();

			for (UUID id : ids) {
				Entity e = p.level().getEntity(id);

				if (e != null && e.isAlive()) {
					result.put(id, new Motion(time, e.position(), e.getDeltaMovement(), e.onGround(), e.horizontalCollision));
				}
			}

			return result;
		});
	}

	/**
	 * Finds the impulse tick (first sample whose horizontal speed exceeds 0.3 blocks/tick) and
	 * reports velocity and displacement from the pre-release position at +0, +1, +2, +4 and +10.
	 */
	private static String describeMotion(Vec3 start, List<Motion> samples) {
		int k = -1;

		for (int i = 0; i < samples.size() && k < 0; i++) {
			if (samples.get(i).velocity().horizontalDistance() > 0.3) {
				k = i;
			}
		}

		if (k < 0) {
			double max = samples.stream().mapToDouble(m -> m.velocity().horizontalDistance()).max().orElse(0);
			Motion last = samples.get(samples.size() - 1);
			return String.format(Locale.ROOT, "no impulse seen (max horizontal speed %.2f), moved %.2f in %d ticks", max,
					last.pos().subtract(start).horizontalDistance(), samples.size());
		}

		List<String> parts = new ArrayList<>();

		for (int off : new int[] { 0, 1, 2, 4, 10 }) {
			int i = k + off;

			if (i < samples.size()) {
				Motion m = samples.get(i);
				parts.add(String.format(Locale.ROOT, "+%d: v(%.2f, %.2f, %.2f) moved %.2f%s%s", off, m.velocity().x, m.velocity().y, m.velocity().z,
						m.pos().subtract(start).horizontalDistance(), m.onGround() ? " ground" : " air", m.collided() ? " COLLIDED" : ""));
			}
		}

		Motion last = samples.get(samples.size() - 1);
		return "impulse seen " + k + " tick(s) after release (game tick " + samples.get(k).time() + "): " + String.join("; ", parts)
				+ String.format(Locale.ROOT, "; final %.2f", last.pos().subtract(start).horizontalDistance());
	}

	/** Server positions of the live entities among {@code ids}. */
	private Map<UUID, Vec3> huskPositions(List<UUID> ids) {
		return onServer(p -> {
			Map<UUID, Vec3> positions = new LinkedHashMap<>();

			for (UUID id : ids) {
				Entity e = p.level().getEntity(id);

				if (e != null && e.isAlive()) {
					positions.put(id, e.position());
				}
			}

			return positions;
		});
	}

	private static String huskDisplacement(Map<UUID, Vec3> before, Map<UUID, Vec3> after) {
		List<String> parts = new ArrayList<>();
		int i = 0;

		for (Map.Entry<UUID, Vec3> entry : before.entrySet()) {
			i++;
			Vec3 now = after.get(entry.getKey());
			parts.add("#" + i + " " + (now == null ? "gone" : String.format(Locale.ROOT, "%.2f", now.subtract(entry.getValue()).horizontalDistance())));
		}

		return parts.isEmpty() ? "(no husks)" : String.join(", ", parts);
	}

	private List<UUID> husksNear() {
		return onServer(p -> p.level().getEntities((Entity) null, p.getBoundingBox().inflate(12),
				e -> e.isAlive() && "husk".equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())).stream().map(Entity::getUUID).toList());
	}

	/**
	 * Places a block item the way a player does: picks it in the hotbar, looks at the top of a free
	 * ground block two blocks away and right-clicks. Returns the placed position or null.
	 */
	private @Nullable BlockPos placeBlock(Item item, Predicate<BlockState> placed, boolean needsLength) {
		if (!select(item)) {
			return null;
		}

		BlockPos ground = onServer(p -> {
			ServerLevel level = p.level();
			BlockPos feet = p.blockPosition();
			int[][] dirs = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };

			for (int distance = 2; distance <= 3; distance++) {
				for (int[] d : dirs) {
					for (int dy = 1; dy >= -2; dy--) {
						BlockPos g = feet.offset(d[0] * distance, dy - 1, d[1] * distance);
						BlockPos above = g.above();
						// API-CHECK: Level#getFluidState + FluidState#isEmpty (standard, not in the probe dumps).
						boolean solid = !level.getBlockState(g).getCollisionShape(level, g).isEmpty() && level.getFluidState(g).isEmpty();
						boolean free = level.getBlockState(above).getCollisionShape(level, above).isEmpty()
								&& level.getBlockState(above.above()).getCollisionShape(level, above.above()).isEmpty()
								&& level.getFluidState(above).isEmpty();
						BlockPos head = above.offset(d[0], 0, d[1]);
						boolean headFree = !needsLength || (level.getBlockState(head).getCollisionShape(level, head).isEmpty()
								&& !level.getBlockState(head.below()).getCollisionShape(level, head.below()).isEmpty());

						if (solid && free && headFree) {
							return g;
						}

						if (solid) {
							break;
						}
					}
				}
			}

			return null;
		});

		if (ground == null) {
			return null;
		}

		aimAt(new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5));
		context.waitTick();
		context.getInput().pressKey(USE);
		BlockPos at = ground.above();
		return waitServer(10, p -> placed.test(p.level().getBlockState(at))) ? at : null;
	}

	/** Right-clicks a block and waits until {@code opened} holds on the client. */
	private boolean openBlock(BlockPos pos, Predicate<Minecraft> opened) {
		aimAt(Vec3.atCenterOf(pos).add(0, 0.45, 0));
		context.waitTick();
		context.getInput().pressKey(USE);
		return waitClient(40, opened);
	}

	/**
	 * Crafts one item at the open crafting table by clicking: picks up each ingredient stack, right
	 * clicks one into the grid cell, puts the rest back, then shift-clicks the result.
	 */
	private boolean craft(String name, String[] pattern, Map<Character, Item> key, Item result) {
		int before = count(result);
		boolean placedAll = context.computeOnClient(mc -> {
			if (!(mc.player.containerMenu instanceof CraftingMenu menu)) {
				return false;
			}

			for (int row = 0; row < pattern.length; row++) {
				for (int col = 0; col < pattern[row].length(); col++) {
					Item ingredient = key.get(pattern[row].charAt(col));

					if (ingredient == null) {
						continue;
					}

					int source = -1;

					for (int slot = 10; slot < menu.slots.size(); slot++) {
						if (menu.slots.get(slot).getItem().is(ingredient)) {
							source = slot;
							break;
						}
					}

					if (source < 0) {
						return false;
					}

					int cell = 1 + row * 3 + col;
					mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.PICKUP, mc.player);
					mc.gameMode.handleContainerInput(menu.containerId, cell, 1, ContainerInput.PICKUP, mc.player);
					mc.gameMode.handleContainerInput(menu.containerId, source, 0, ContainerInput.PICKUP, mc.player);
				}
			}

			return true;
		});
		context.waitTicks(4);

		if (placedAll) {
			context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 0, ContainerInput.QUICK_MOVE, mc.player));
			context.waitTicks(4);
		}

		boolean crafted = count(result) > before;
		note("07 crafting", crafted ? Result.INFO : Result.FAIL, name + ": ingredients placed=" + placedAll + ", crafted=" + crafted);
		return crafted;
	}

	/** Closes the open screen with Escape, as a player would (so the server closes the menu too). */
	private void closeScreen() {
		if (context.computeOnClient(mc -> mc.gui.screen() != null)) {
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(3);
		}

		if (context.computeOnClient(mc -> mc.gui.screen() != null)) {
			context.setScreen(() -> null);
		}
	}

	/**
	 * Puts {@code item} in the main hand: a hotbar number key if it is in the hotbar, else a swap
	 * into hotbar slot 9 through the inventory screen first.
	 */
	private boolean select(Item item) {
		int slot = onServer(p -> {
			for (int i = 0; i < 36; i++) {
				if (p.getInventory().getItem(i).is(item)) {
					return i;
				}
			}

			return -1;
		});

		if (slot < 0) {
			return false;
		}

		if (slot >= 9) {
			context.getInput().pressKey(INVENTORY);
			waitClient(20, mc -> mc.gui.screen() != null);
			int from = slot;
			context.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, from, 8, ContainerInput.SWAP, mc.player));
			context.waitTicks(3);
			closeScreen();
			slot = 8;
		}

		int hotbar = slot;
		context.getInput().pressKey(o -> o.keyHotbarSlots[hotbar]);
		return waitServer(10, p -> p.getMainHandItem().is(item));
	}

	/** Removes stray vanilla hostiles near the player so they do not interfere with a scripted step. */
	private void clearInterference(String step) {
		int removed = onServer(p -> {
			int n = 0;

			for (Entity e : p.level().getEntities((Entity) null, p.getBoundingBox().inflate(24),
					x -> x instanceof Enemy && x.isAlive() && !(x instanceof CrystalPoacher) && !(x instanceof ShadowEchoEntity))) {
				e.discard();
				n++;
			}

			return n;
		});

		if (removed > 0) {
			shortcut("removed stray hostile mobs that wandered in (" + step + ")");
		}
	}

	/** Searches for a crystal cave far from the ones already visited. */
	private @Nullable BlockPos findOtherCave() {
		BlockPos origin = knownCaves.get(knownCaves.size() - 1);
		int[][] offsets = { { 700, 0 }, { -700, 0 }, { 0, 700 }, { 0, -700 }, { 700, 700 }, { -700, -700 } };

		for (int[] offset : offsets) {
			BlockPos found = onServer(p -> p.level().findNearestMapStructure(CaveLocator.CRYSTAL_CAVES, origin.offset(offset[0], 0, offset[1]), 6, false));

			if (found != null && knownCaves.stream().allMatch(known -> known.distSqr(found) > 200 * 200)) {
				return found;
			}
		}

		return null;
	}

	/** Goes to a camp that still has live poachers: the last one visited, else a new cave. */
	private boolean findLivePoachers(String step) {
		if (lastCamp != null) {
			Vec3 camp = lastCamp;
			int x = Mth.floor(camp.x);
			int z = Mth.floor(camp.z) + 24;
			int y = surfaceY(x, z);
			shortcut("teleported back towards the last poacher camp for the saber test");
			server.runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 180 0", x, y, z));
			waitForChunks(step);
			context.waitTicks(20);

			if (!poachersNear(64).isEmpty()) {
				return true;
			}
		}

		for (int tries = 0; tries < 2; tries++) {
			BlockPos other = findOtherCave();

			if (other == null) {
				return false;
			}

			knownCaves.add(other);

			if (approachCamp(step, other) && !poachersNear(48).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	/** Locates the heart piece from the locator result, the meditation stone and the seal blocks. */
	private @Nullable Heart findHeart() {
		return onServer(p -> {
			BlockPos located = CaveLocator.findNearestCaveNow(p);

			if (located == null) {
				return null;
			}

			ServerLevel level = p.level();
			BlockPos stone = located;
			boolean hit = level.getBlockState(located).is(WorldBlocks.MEDITATION_STONE);

			if (!hit) {
				BlockPos found = TrialManager.findStone(level, located, 48);

				if (found == null) {
					return null;
				}

				stone = found;
			}

			long sx = 0;
			long sz = 0;
			int n = 0;

			for (BlockPos pos : BlockPos.betweenClosed(stone.offset(-12, -1, -12), stone.offset(12, 4, 12))) {
				if (level.getBlockState(pos).is(WorldBlocks.RESONANT_SEAL)) {
					sx += pos.getX();
					sz += pos.getZ();
					n++;
				}
			}

			if (n == 0) {
				return new Heart(stone, 0, 0, hit, 0);
			}

			double vx = stone.getX() - (double) sx / n;
			double vz = stone.getZ() - (double) sz / n;
			int dx = Math.abs(vx) > Math.abs(vz) ? (int) Math.signum(vx) : 0;
			int dz = Math.abs(vz) >= Math.abs(vx) ? (int) Math.signum(vz) : 0;
			return new Heart(stone, dx, dz, hit, n);
		});
	}

	/** Piece bounds of one crystal cave structure, read on the server. */
	private record CaveInfo(BoundingBox structure, @Nullable BoundingBox camp, @Nullable BoundingBox descent, @Nullable BoundingBox heart, int pieces) {
		@Nullable String pieceContaining(BlockPos pos) {
			if (camp != null && camp.isInside(pos)) {
				return "camp";
			}

			if (descent != null && descent.isInside(pos)) {
				return "descent";
			}

			return heart != null && heart.isInside(pos) ? "heart" : null;
		}
	}

	/**
	 * The crystal cave structure with a piece at {@code pos}, or else one whose start is in the
	 * chunk of {@code pos} (the locator's start position). Server thread only.
	 */
	private static @Nullable StructureStart caveStartAt(ServerLevel level, BlockPos pos) {
		level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, true);
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, CaveLocator.CRYSTAL_CAVES);

		if (start != null && start.isValid()) {
			return start;
		}

		ChunkAccess chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS, true);

		for (StructureStart candidate : chunk.getAllStarts().values()) {
			if (candidate != null && candidate.isValid() && candidate.getPieces().stream().anyMatch(piece -> CaveLocator.CAMP_TEMPLATE.equals(templateOf(piece)))) {
				return candidate;
			}
		}

		return null;
	}

	private static @Nullable Identifier templateOf(StructurePiece piece) {
		return piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single ? single.getTemplateLocation() : null;
	}

	/** Bounds of the cave structure around {@code pos} (see {@link #caveStartAt}), or null. */
	private @Nullable CaveInfo caveInfo(BlockPos pos) {
		return onServer(p -> {
			StructureStart start = caveStartAt(p.level(), pos);

			if (start == null) {
				return null;
			}

			BoundingBox camp = null;
			BoundingBox descent = null;
			BoundingBox heartBox = null;

			for (StructurePiece piece : start.getPieces()) {
				Identifier template = templateOf(piece);

				if (CaveLocator.CAMP_TEMPLATE.equals(template)) {
					camp = piece.getBoundingBox();
				} else if (CaveLocator.DESCENT_TEMPLATE.equals(template)) {
					descent = piece.getBoundingBox();
				} else if (CaveLocator.HEART_TEMPLATE.equals(template)) {
					heartBox = piece.getBoundingBox();
				}
			}

			return new CaveInfo(start.getBoundingBox(), camp, descent, heartBox, start.getPieces().size());
		});
	}

	private static String box(@Nullable BoundingBox b) {
		return b == null ? "none" : "[" + b.minX() + " " + b.minY() + " " + b.minZ() + " .. " + b.maxX() + " " + b.maxY() + " " + b.maxZ() + "]";
	}

	/** Centre of the camp yard (one block above the camp floor, see gen_structures.py). */
	private static Vec3 campYard(BoundingBox camp) {
		return new Vec3((camp.minX() + camp.maxX() + 1) / 2.0, camp.minY() + 5, (camp.minZ() + camp.maxZ() + 1) / 2.0);
	}

	/**
	 * Logs where the player is: quest stage, {@code CaveLocator.pieceAt} at the player's block, and
	 * the bounds of the nearest crystal cave structure with which piece box (checked directly)
	 * contains the player, so a disagreement with pieceAt shows up.
	 */
	private void logWhere(String step, String when) {
		try {
			BlockPos ref = heart != null ? heart.stone() : knownCaves.isEmpty() ? null : knownCaves.get(0);
			BlockPos cave = ref != null ? ref
					: onServer(p -> p.level().findNearestMapStructure(CaveLocator.CRYSTAL_CAVES, p.blockPosition(), 6, false));
			CaveInfo info = cave == null ? null : caveInfo(cave);
			String where = onServer(p -> {
				BlockPos pos = p.blockPosition();
				Identifier piece = CaveLocator.pieceAt(p.level(), pos);
				return "stage " + stage(Progression.get(p)) + ", player " + pos.toShortString() + ", pieceAt=" + (piece == null ? "none" : piece.getPath())
						+ ", isInsideCave=" + CaveLocator.isInsideCave(p);
			});
			BlockPos me = onServer(p -> p.blockPosition());
			String structure = info == null ? "no cave structure found" + (cave == null ? "" : " at " + cave.toShortString())
					: "cave " + box(info.structure()) + " (" + info.pieces() + " pieces): camp " + box(info.camp()) + ", descent " + box(info.descent())
							+ ", heart " + box(info.heart()) + ", piece box containing the player=" + info.pieceContaining(me);
			note(step, Result.INFO, "where (" + when + "): " + where + "; " + structure);
		} catch (RuntimeException e) {
			note(step, Result.INFO, "where (" + when + "): could not inspect: " + e);
		}
	}

	/** First free block above the surface at x, z; loads (and generates) the chunk first. */
	private int surfaceY(int x, int z) {
		return onServer(p -> {
			// Level#getHeight answers the minimum height for chunks that are not loaded.
			p.level().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
			return p.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		});
	}

	/** Whether the resonant seal block at {@code pos} is open (false if it is not a seal at all). */
	private boolean sealOpen(BlockPos pos) {
		return onServer(p -> {
			BlockState state = p.level().getBlockState(pos);
			return state.is(WorldBlocks.RESONANT_SEAL) && state.getValue(ResonantSealBlock.OPEN);
		});
	}

	private void waitForChunks(String step) {
		try {
			singleplayer.getConnection().waitForChunksRender(true, 2400);
		} catch (AssertionError e) {
			note(step, Result.INFO, "chunks did not finish rendering within 2400 ticks: " + e.getMessage());
		}
	}

	private int count(Item item) {
		return onServer(p -> {
			int n = 0;

			for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
				ItemStack stack = p.getInventory().getItem(slot);

				if (stack.is(item)) {
					n += stack.getCount();
				}
			}

			return n;
		});
	}

	private boolean hasEmitter() {
		return count(SaberItems.EMITTER_GUARDIAN) + count(SaberItems.EMITTER_DUELIST) > 0;
	}

	private String partsSummary() {
		return "emitters " + (count(SaberItems.EMITTER_GUARDIAN) + count(SaberItems.EMITTER_DUELIST))
				+ ", grips " + (count(SaberItems.GRIP_WRAPPED) + count(SaberItems.GRIP_RIDGED))
				+ ", pommels " + (count(SaberItems.POMMEL_WEIGHTED) + count(SaberItems.POMMEL_BALANCED))
				+ ", iron nuggets " + count(Items.IRON_NUGGET);
	}

	private ForceData data() {
		return onServer(p -> Progression.get(p).copy());
	}

	private static QuestStage stage(ForceData data) {
		return QuestStage.of(data.questStage);
	}

	private <T> T onServer(Function<ServerPlayer, T> function) {
		return server.computeOnServer(s -> function.apply(s.getPlayerList().getPlayers().getFirst()));
	}

	/** Polls a server-side condition once per tick for up to {@code timeout} ticks. */
	private boolean waitServer(int timeout, Predicate<ServerPlayer> condition) {
		for (int t = 0; t < timeout; t++) {
			if (onServer(condition::test)) {
				return true;
			}

			tickMe("wait");
		}

		return onServer(condition::test);
	}

	/** Polls a client-side condition once per tick for up to {@code timeout} ticks. */
	private boolean waitClient(int timeout, Predicate<Minecraft> condition) {
		for (int t = 0; t < timeout; t++) {
			if (context.computeOnClient(condition::test)) {
				return true;
			}

			context.waitTick();
		}

		return context.computeOnClient(condition::test);
	}

	// ---------------------------------------------------------------------------------------------
	// Recording
	// ---------------------------------------------------------------------------------------------

	private void shot(String step) {
		context.takeScreenshot(String.format(Locale.ROOT, "play_%02d_%s", ++screenshots, step));
	}

	private void check(String step, boolean ok, String details) {
		note(step, ok ? Result.PASS : Result.FAIL, details);
	}

	private void note(String step, Result result, String details) {
		entries.add(new Entry(step, result, details));
		LOG.info("PLAYTHROUGH-LOG: [{}] {}: {}", result, step, details);
	}

	private void shortcut(String what) {
		if (shortcuts.merge(what, 1, Integer::sum) == 1) {
			LOG.info("PLAYTHROUGH-LOG: SHORTCUT: {}", what);
		}
	}

	private void stuck(String step, String details) {
		stuck = true;
		note(step, Result.STUCK, details);
		logWhere(step, "stuck");
		shot("stuck");
		throw new Stuck(step + ": " + details);
	}

	private void printSummary() {
		long pass = entries.stream().filter(e -> e.result() == Result.PASS).count();
		long fail = entries.stream().filter(e -> e.result() == Result.FAIL).count();
		LOG.info("PLAYTHROUGH: ==================== Milestone 1 playthrough ====================");
		LOG.info("PLAYTHROUGH: seed '{}', {} pass, {} fail, stuck={}", SEED, pass, fail, stuck);

		for (Entry entry : entries) {
			if (entry.result() != Result.INFO) {
				LOG.info("PLAYTHROUGH: {} | {} | {}", entry.result(), entry.step(), entry.details());
			}
		}

		LOG.info("PLAYTHROUGH: ---- details ----");

		for (Entry entry : entries) {
			if (entry.result() == Result.INFO) {
				LOG.info("PLAYTHROUGH: INFO | {} | {}", entry.step(), entry.details());
			}
		}

		LOG.info("PLAYTHROUGH: ---- shortcuts ----");

		for (Map.Entry<String, Integer> shortcut : shortcuts.entrySet()) {
			LOG.info("PLAYTHROUGH: SHORTCUT: {}{}", shortcut.getKey(), shortcut.getValue() > 1 ? " (x" + shortcut.getValue() + ")" : "");
		}

		LOG.info("PLAYTHROUGH: ==================================================================");
	}
}
