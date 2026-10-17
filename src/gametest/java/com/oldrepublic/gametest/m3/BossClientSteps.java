package com.oldrepublic.gametest.m3;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.ArenaLayout;
import com.oldrepublic.boss.ArenaSession;
import com.oldrepublic.boss.BossArenas;
import com.oldrepublic.boss.CollectorCombatGoal;
import com.oldrepublic.boss.OrrenVasch;
import com.oldrepublic.boss.PylonFocus;
import com.oldrepublic.boss.ReliquarySites;
import com.oldrepublic.boss.block.PylonCrystal;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.construct.RuinSentinel;
import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.construct.Sentinels;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.BossOutcome;
import com.oldrepublic.quest.QuestStage;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3 client test steps for the boss feature (the Ruin Sentinels, the Reliquary and Orren
 * Vasch). {@code ExplorationClientTest} runs them last, in the shared seeded exploration world (spec
 * 17.2). Screenshots are {@code oldrepublic_m3_<nn>_<what>} with {@code nn} from 50 to 59:
 *
 * <ul>
 * <li>50 {@code sentinel_wake_slam}: a Watcher waking beside a Tomb sentinel in its slam tell, on a
 * small stage built high above the player;</li>
 * <li>51 {@code reliquary_threshold}: the hall seen from the antechamber, Vasch reading at the lectern;</li>
 * <li>52 {@code phase_archivist}: the fight's first phase (white bar, the banner) during an Archive Hurl tell;</li>
 * <li>53 {@code phase_singing_wall}: phase 2 (purple bar), the four pylons singing their currents to him;</li>
 * <li>54 {@code pylon_silenced}: a pylon cracked dark, glints falling;</li>
 * <li>55 {@code unsung}: the last pylon silenced, the currents snapping back and dust shaking down;</li>
 * <li>56 {@code phase_unkept_surge_cover}: phase 3 (red bar), the Surge gather seen from behind a column;</li>
 * <li>57 {@code yield}: he kneels, the blade retracted;</li>
 * <li>58 {@code custody_scene}: the custody ending, his wrists bound, the escort coming for him;</li>
 * <li>59 {@code hoard_open}: the vault seal crumbled and motes rising from the hoard.</li>
 * </ul>
 *
 * <p>Shortcuts, each logged: the Reliquary is forced 32 blocks ahead of the player
 * ({@code ReliquarySites.placeAt}, the command path) with its area force-loaded; the player is made
 * awakened for the fight and put back afterwards; the fight starts at once ({@code startFight}, no
 * meeting dialogue), its difficulty is pinned to Normal, the phases are jumped with
 * {@code forcePhase} (the command path), the moves are started with {@code forceMove}, the pylons are
 * silenced through their foci's Saber Throw callback, and the ending is chosen with
 * {@code BossArenas.resolve} (Spare, or Bind on the Sith path, both custody scenes). The player keeps
 * the driver's Resistance, so the fight never kills them. The Reliquary stays in the world (these are
 * the last steps); the force-loaded chunks, the stage, the player's position, Force data and camera
 * are put back.
 */
public final class BossClientSteps {
	/** The sentinel stage's floor height: above the seeded world's terrain near spawn. */
	private static final int STAGE_Y = 240;
	/** The Reliquary's surface stand, this many blocks south (+Z) of the player. */
	private static final int RELIQUARY_AHEAD = 32;
	/** Force-loaded around the stand while the hall is built and fought in (7 by 7 chunks at most). */
	private static final int FORCELOAD_RADIUS = 48;
	/** Ticks the paced hall build gets, with margin (four slabs, one a tick, then the heart). */
	private static final int BUILD_TICKS = 60;
	/** A transition runs 60 ticks; the four pylons wake 15 ticks apart after the first. */
	private static final int TRANSITION_TICKS = 70;
	private static final double EYE = 1.62;

	public static void run(ClientGameTestContext context, TestServerContext server) {
		double[] origin = new double[5];
		ForceData[] before = new ForceData[1];
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			origin[0] = p.getX();
			origin[1] = p.getY();
			origin[2] = p.getZ();
			origin[3] = p.getYRot();
			origin[4] = p.getXRot();
			before[0] = Progression.get(p).copy();
		});
		int bx = Mth.floor(origin[0]);
		int bz = Mth.floor(origin[2]);
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		sentinels(context, server, bx, bz);

		BlockPos stand = new BlockPos(bx, 0, bz + RELIQUARY_AHEAD);
		server.runCommand(String.format(Locale.ROOT, "forceload add %d %d %d %d", stand.getX() - FORCELOAD_RADIUS, stand.getZ() - FORCELOAD_RADIUS,
				stand.getX() + FORCELOAD_RADIUS, stand.getZ() + FORCELOAD_RADIUS));
		context.waitTicks(40);

		try {
			// The Reliquary is lit by its crystals alone: night vision so the review can see the fight.
			server.runCommand("effect give @p minecraft:night_vision infinite 0 true");
			reliquary(context, server, stand);
		} finally {
			// Put back what can be put back: Force data, effects, position, force-loading and camera.
			server.runCommand("effect clear @p minecraft:night_vision");
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				Dialogues.close(p);
				Progression.commit(p, before[0]);
			});
			context.setScreen(() -> null);
			server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.1f %.1f", origin[0], origin[1], origin[2], origin[3], origin[4]));
			server.runCommand("forceload remove all");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(20);
		}
	}

	// ---------------------------------------------------------------- 50: the sentinels

	/** 50: a Watcher in its wake tell beside a Tomb sentinel raising its arms for the slam. */
	private static void sentinels(ClientGameTestContext context, TestServerContext server, int bx, int bz) {
		buildStage(server, bx, bz, "minecraft:mossy_stone_bricks", "minecraft:stone_bricks");
		server.runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 8", bx, STAGE_Y, bz));
		context.waitTicks(30);

		int[] ids = server.computeOnServer(s -> {
			ServerLevel level = player(s).level();
			RuinSentinel watcher = sentinel(level, new BlockPos(bx - 2, STAGE_Y, bz + 7), SentinelVariant.WATCHER);
			RuinSentinel tomb = sentinel(level, new BlockPos(bx + 2, STAGE_Y, bz + 7), SentinelVariant.TOMB);
			return new int[] {watcher.getId(), tomb.getId()};
		});
		// Tracked on the client before the tells start, so their effects reach it.
		context.waitTicks(10);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);

			if (p.level().getEntity(ids[0]) instanceof RuinSentinel watcher) {
				watcher.wake(p);
			}

			if (p.level().getEntity(ids[1]) instanceof RuinSentinel tomb) {
				tomb.beginSlam();
			}
		});
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m3_50_sentinel_wake_slam");

		server.runOnServer(s -> {
			ServerLevel level = player(s).level();

			for (int id : ids) {
				Entity sentinel = level.getEntity(id);

				if (sentinel != null) {
					sentinel.discard();
				}
			}
		});
		buildStage(server, bx, bz, "minecraft:air", "minecraft:air");
		context.waitTicks(5);
	}

	private static RuinSentinel sentinel(ServerLevel level, BlockPos at, SentinelVariant variant) {
		RuinSentinel sentinel = (RuinSentinel) Sentinels.spawn(level, at, variant, false, null);
		// Facing the player, north.
		Vec3 feet = Vec3.atBottomCenterOf(at);
		sentinel.snapTo(feet.x, feet.y, feet.z, 180f, 0f);
		sentinel.setYHeadRot(180f);
		sentinel.setYBodyRot(180f);
		return sentinel;
	}

	/** A floor with a back wall behind the sentinels, so their lumens and dust read. */
	private static void buildStage(TestServerContext server, int bx, int bz, String floor, String wall) {
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", bx - 6, STAGE_Y, bz - 2, bx + 6, STAGE_Y + 6, bz + 10));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s", bx - 6, STAGE_Y - 1, bz - 2, bx + 6, STAGE_Y - 1, bz + 10, floor));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s", bx - 6, STAGE_Y, bz + 10, bx + 6, STAGE_Y + 5, bz + 10, wall));
	}

	// ---------------------------------------------------------------- 51-59: the Reliquary

	private static void reliquary(ClientGameTestContext context, TestServerContext server, BlockPos stand) {
		BlockPos heart = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			BlockPos surface = new BlockPos(stand.getX(), level.getHeight(Heightmap.Types.WORLD_SURFACE, stand.getX(), stand.getZ()), stand.getZ());
			OldRepublicMod.LOGGER.info("Boss client step: shortcut, forcing the Reliquary at {} (ReliquarySites.placeAt)", surface);
			return ReliquarySites.placeAt(p, surface, Rotation.NONE);
		});
		context.waitTicks(BUILD_TICKS);

		// 51: from the antechamber, the hall and Vasch reading at the lectern (the heart is READY with its owner this near).
		ArenaLayout layout = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ArenaSession session = BossArenas.ensureSession(p);

			if (session == null) {
				throw new IllegalStateException("No Collector session at the forced Reliquary heart " + heart);
			}

			session.overrideDifficulty(Difficulty.NORMAL);
			return session.layout();
		});
		server.runCommand(tpFacing(Vec3.atBottomCenterOf(layout.antechamberFloor()), layout.daisFeet().add(0, 1.2, 0)));
		context.waitTicks(60);
		context.takeScreenshot("oldrepublic_m3_51_reliquary_threshold");

		// 52: the fight begins; the Archivist tears a chunk loose for a Hurl.
		Vec3 inside = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ForceData data = Progression.get(p);

			if (!data.isAwakened()) {
				OldRepublicMod.LOGGER.info("Boss client step: shortcut, the player is awakened for the fight (put back afterwards)");
				data.questStage = QuestStage.FOCUS.ordinal();
				Progression.commit(p, data);
			}

			// Into the hall, 7 blocks from the dais toward the doorway, so the player is listed at the start.
			Vec3 toDoor = Vec3.atBottomCenterOf(layout.threshold()).subtract(layout.daisFeet()).multiply(1, 0, 1).normalize();
			Vec3 at = floorNear(p.level(), layout.daisFeet().add(toDoor.scale(7.0)));
			p.teleportTo(at.x, at.y, at.z);
			ArenaSession session = session(p);
			OldRepublicMod.LOGGER.info("Boss client step: shortcut, starting the fight at once (no meeting dialogue)");
			session.startFight(null);
			return at;
		});
		server.runCommand(tpFacing(inside, layout.daisFeet().add(0, 1.2, 0)));
		context.waitTicks(15);
		move(server, CollectorCombatGoal.Move.HURL);
		context.waitTicks(14);
		server.runCommand(viewVasch(server, 6.0));
		context.waitTicks(2);
		context.takeScreenshot("oldrepublic_m3_52_phase_archivist");

		// 53: the Singing Wall: the transition, the pylons waking, their currents and the ward haze.
		phase(server, 2);
		context.waitTicks(TRANSITION_TICKS + 50);
		server.runCommand(viewVasch(server, 7.0));
		context.waitTicks(4);
		context.takeScreenshot("oldrepublic_m3_53_phase_singing_wall");

		// 54: one pylon cracked dark.
		Vec3 crystal = server.computeOnServer(s -> crystal(session(player(s)), 0));
		server.runCommand(tpFacing(floorNear(server, crystal.add(viewOffset(layout, crystal, 4.0))), crystal));
		context.waitTicks(4);
		server.runOnServer(s -> silence(player(s), 0));
		context.waitTicks(6);
		context.takeScreenshot("oldrepublic_m3_54_pylon_silenced");

		// 55: the last pylon silenced: Unsung.
		server.runCommand(viewVasch(server, 6.0));
		context.waitTicks(4);
		server.runOnServer(s -> {
			ServerPlayer p = player(s);

			for (int i = 1; i < layout.pylons().size(); i++) {
				silence(p, i);
			}

			ArenaSession session = session(p);
			OldRepublicMod.LOGGER.info("Boss client step: Unsung {} (ward down {})", session.isUnsung(), session.isWardDown());
		});
		context.waitTicks(6);
		context.takeScreenshot("oldrepublic_m3_55_unsung");

		// 56: Unkept; the Surge gather seen from behind a column.
		phase(server, 3);
		context.waitTicks(TRANSITION_TICKS + 10);
		String cover = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ArenaSession session = session(p);
			OrrenVasch vasch = vasch(session);
			Vec3 dais = layout.daisFeet();
			vasch.teleportTo(dais.x, dais.y, dais.z);
			vasch.getNavigation().stop();
			return coverView(p.level(), layout, dais);
		});
		server.runCommand(cover);
		context.waitTicks(4);
		move(server, CollectorCombatGoal.Move.SURGE);
		context.waitTicks(22);
		context.takeScreenshot("oldrepublic_m3_56_phase_unkept_surge_cover");
		context.waitTicks(40);

		// 57: the yield: he kneels; his dialogue is closed for the picture (closing it decides nothing).
		phase(server, 4);
		context.waitTicks(40);
		server.runOnServer(s -> Dialogues.close(player(s)));
		context.setScreen(() -> null);
		server.runCommand(viewVasch(server, 4.0));
		context.waitTicks(20);
		context.takeScreenshot("oldrepublic_m3_57_yield");

		// 59 then 58: the ending; the hoard opens at once, the escort takes longer to come down.
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			BossOutcome outcome = Identity.path(p) == Path.SITH ? BossOutcome.BOUND : BossOutcome.SPARED;
			OldRepublicMod.LOGGER.info("Boss client step: shortcut, resolving the Collector as {} (BossArenas.resolve)", outcome);
			BossArenas.resolve(p, outcome);
		});
		server.runCommand(hoardView(server, layout));
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m3_59_hoard_open");

		server.runCommand(viewVasch(server, 5.0));
		context.waitTicks(80);
		server.runCommand(viewVasch(server, 5.0));
		context.waitTicks(4);
		context.takeScreenshot("oldrepublic_m3_58_custody_scene");
	}

	// ---------------------------------------------------------------- fight shortcuts

	private static void phase(TestServerContext server, int n) {
		server.runOnServer(s -> {
			boolean jumped = session(player(s)).forcePhase(n);
			OldRepublicMod.LOGGER.info("Boss client step: shortcut, forcePhase({}) gave {}", n, jumped);
		});
	}

	private static void move(TestServerContext server, CollectorCombatGoal.Move which) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			CollectorCombatGoal combat = vasch(session(p)).combat();
			boolean started = combat != null && combat.forceMove(which, p);
			OldRepublicMod.LOGGER.info("Boss client step: shortcut, forceMove({}) gave {}", which, started);
		});
	}

	/** Silences pylon {@code index} through its focus (Saber Throw hits) until its crystal goes dark. */
	private static void silence(ServerPlayer p, int index) {
		ArenaSession session = session(p);

		for (int i = 0; i < 40 && session.pylonCrystal(index) == PylonCrystal.SINGING; i++) {
			PylonFocus focus = session.pylonFocus(index);

			if (focus == null) {
				break;
			}

			focus.onSaberThrowHit(p, 0f);
		}
	}

	// ---------------------------------------------------------------- views

	/** The player {@code distance} blocks from Vasch toward the doorway, looking at his chest. */
	private static String viewVasch(TestServerContext server, double distance) {
		return server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ArenaSession session = session(p);
			OrrenVasch vasch = vasch(session);
			Vec3 toDoor = Vec3.atBottomCenterOf(session.layout().threshold()).subtract(vasch.position()).multiply(1, 0, 1);
			Vec3 dir = toDoor.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : toDoor.normalize();
			Vec3 at = floorNear(p.level(), vasch.position().add(dir.scale(distance)));
			return tpFacing(at, vasch.position().add(0, 1.3, 0));
		});
	}

	/**
	 * Behind the column nearest the dais, half in its shadow: Vasch at the dais is partly hidden by the
	 * column, the gathering dust streams past it. Without columns, a plain view from 8 blocks.
	 */
	private static String coverView(ServerLevel level, ArenaLayout layout, Vec3 dais) {
		BlockPos column = null;
		double best = Double.MAX_VALUE;

		for (BlockPos candidate : layout.columns()) {
			double distance = Vec3.atBottomCenterOf(candidate).distanceToSqr(dais);

			if (distance > 9.0 && distance < best) {
				best = distance;
				column = candidate;
			}
		}

		Vec3 look = dais.add(0, 1.3, 0);

		if (column == null) {
			Vec3 toDoor = Vec3.atBottomCenterOf(layout.threshold()).subtract(dais).multiply(1, 0, 1).normalize();
			return tpFacing(floorNear(level, dais.add(toDoor.scale(8.0))), look);
		}

		Vec3 base = Vec3.atBottomCenterOf(column);
		Vec3 away = base.subtract(dais).multiply(1, 0, 1).normalize();
		Vec3 side = new Vec3(-away.z, 0, away.x);
		Vec3 at = floorNear(level, base.add(away.scale(1.8)).add(side.scale(0.8)));
		return tpFacing(at, look);
	}

	/** A few blocks from the hoard plinth (or the vault seal), toward the dais, looking at it. */
	private static String hoardView(TestServerContext server, ArenaLayout layout) {
		return server.computeOnServer(s -> {
			ServerLevel level = player(s).level();
			BlockPos target = layout.plinth() != null ? layout.plinth() : layout.vaultSeals().isEmpty() ? layout.dais() : layout.vaultSeals().getFirst();
			Vec3 hoard = Vec3.atBottomCenterOf(target);
			Vec3 toDais = layout.daisFeet().subtract(hoard).multiply(1, 0, 1);
			Vec3 dir = toDais.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : toDais.normalize();
			return tpFacing(floorNear(level, hoard.add(dir.scale(4.0))), hoard.add(0, 0.8, 0));
		});
	}

	/** An offset {@code distance} blocks from a pylon crystal toward the dais (the open side of the hall). */
	private static Vec3 viewOffset(ArenaLayout layout, Vec3 crystal, double distance) {
		Vec3 toDais = layout.daisFeet().subtract(crystal).multiply(1, 0, 1);
		Vec3 dir = toDais.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : toDais.normalize();
		return dir.scale(distance).add(0, -1.25, 0);
	}

	private static Vec3 crystal(ArenaSession session, int index) {
		return Vec3.atBottomCenterOf(session.layout().pylons().get(index)).add(0, PylonFocus.CRYSTAL_HEIGHT + 0.4, 0);
	}

	/** A {@code tp} that stands the player at {@code feet} looking at {@code look}. */
	private static String tpFacing(Vec3 feet, Vec3 look) {
		double dx = look.x - feet.x;
		double dy = look.y - (feet.y + EYE);
		double dz = look.z - feet.z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.1f %.1f", feet.x, feet.y, feet.z, yaw, pitch);
	}

	private static Vec3 floorNear(TestServerContext server, Vec3 at) {
		return server.computeOnServer(s -> floorNear(player(s).level(), at));
	}

	/** The nearest standing spot to {@code at} in its column (solid below, two free cells), or {@code at}. */
	private static Vec3 floorNear(ServerLevel level, Vec3 at) {
		BlockPos base = BlockPos.containing(at);

		for (int dy : List.of(0, 1, -1, 2, -2, 3, -3, 4, -4)) {
			BlockPos feet = base.above(dy);

			if (!level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
					&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
				return new Vec3(at.x, feet.getY(), at.z);
			}
		}

		return at;
	}

	// ---------------------------------------------------------------- lookups

	private static ArenaSession session(ServerPlayer p) {
		ArenaSession session = BossArenas.sessionOwnedBy(p);

		if (session == null) {
			throw new IllegalStateException("The Collector session of " + p.getPlainTextName() + " is gone");
		}

		return session;
	}

	private static OrrenVasch vasch(ArenaSession session) {
		@Nullable OrrenVasch vasch = session.vasch();

		if (vasch == null) {
			throw new IllegalStateException("The Collector session has no Orren Vasch (stage " + session.stage() + ")");
		}

		return vasch;
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private BossClientSteps() {
	}
}
