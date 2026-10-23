package com.oldrepublic.space;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.mission.MissionDefinition;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.quest.story.ChapterFiveRules;
import com.oldrepublic.quest.story.FactionStanding;
import com.oldrepublic.registry.M5Items;
import com.oldrepublic.space.network.SpaceEncounterPayload;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The runtime of the space objectives {@code space_run} and {@code space_clear} (spec MILESTONE_5.md
 * 5.8, 5.9): W4's mission service starts each here, and W2 reports its end through
 * {@code QuestHooks.onSpaceObjectiveDone}. Owned by W2 space.
 *
 * <p><b>space_run.</b> A {@code sealed_cargo} goes into the ship's hold at the start. In the run's
 * orbit, a nav buoy's point is chosen {@code space.buoyDistance} blocks from where the ship first
 * flies there, shown on the HUD from anywhere in that orbit. Once the ship is under way a patrol
 * (Clan Hask's for {@code c5_cargo}, pirates otherwise) arrives ahead of it and shadows it, neutral:
 * one of its fighters within {@code space.scanRange} with a clear line for {@code space.scanTicks}
 * fills the scan meter, which drains while none does. A full meter fails the run; firing on the
 * patrol turns it hostile and ends the scan, and destroying or outrunning it ends it for this
 * attempt. Reaching the buoy with the crate completes the run.
 *
 * <p><b>space_clear.</b> A marked point in the orbit (seeded by the day), shown on the HUD from
 * anywhere there. When the ship comes near, the group waits: 3 to 6 fighters, or a bounty's named
 * ace (doubled shield and hull, faster guns) and two wingmen. Destroying them all completes it, and
 * a bounty rolls {@code gameplay/space_bounty} for the owner (W4 pays the rest). On Peaceful no
 * fighters come, and reaching the point completes it.
 *
 * <p>The objectives are saved ({@link SpaceData}); their patrols and groups are encounters, which
 * are not: leaving the orbit, being disabled or a restart takes them away, and they come again on
 * the next approach.
 */
public final class SpaceMissions {
	/** The bounty's loot roll (5.8). */
	public static final ResourceKey<LootTable> SPACE_BOUNTY = ResourceKey.create(Registries.LOOT_TABLE, OldRepublicMod.id("gameplay/space_bounty"));
	/** The buoy is placed when the ship comes this near its point, and reached within {@link #BUOY_REACH}. */
	private static final double BUOY_PLACE = 128;
	private static final double BUOY_REACH = 12;
	/** A group to clear waits for the ship within this range of its point; on Peaceful, reaching this near completes it. */
	private static final double CLEAR_PLACE = 160;
	private static final double CLEAR_PEACEFUL_REACH = 64;
	/** The run's patrol comes once the ship has flown this share of the way to the buoy, this far ahead of it. */
	private static final double PATROL_PROGRESS = 0.3;
	private static final double PATROL_AHEAD = 110;

	/** One objective's moment-to-moment state, never saved. */
	private static final class Live {
		/** The run's start in this orbit, for its progress toward the buoy. */
		@Nullable Vec3 start;
		@Nullable UUID buoy;
		int scan;
		boolean scanning;
		/** This attempt's patrol was destroyed or outrun: it does not come back until the ship leaves and returns. */
		boolean patrolDone;
	}

	private static final Map<UUID, Map<String, Live>> LIVE = new HashMap<>();
	/** How each player's objectives last ended (true: completed), by mission; dropped when they leave. */
	private static final Map<UUID, Map<String, Boolean>> RESULTS = new HashMap<>();

	/**
	 * Starts mission {@code missionId}'s space objective of kind {@code kind} for {@code p} in the
	 * orbit of {@code system}, with a named ace when {@code ace} (a space bounty, 5.8). Returns false
	 * when it is refused now.
	 */
	public static boolean start(ServerPlayer p, String missionId, MissionDefinition.Objective kind, Planets.Planet system, boolean ace) {
		if (kind != MissionDefinition.Objective.SPACE_RUN && kind != MissionDefinition.Objective.SPACE_CLEAR) {
			return false;
		}

		SpaceData data = SpaceData.get(p);

		if (data.run(missionId).isPresent()) {
			return true;
		}

		MinecraftServer server = p.level().getServer();
		int ciphers = ace ? FactionStanding.ciphers(p, missionId.substring(0, Math.max(0, missionId.indexOf('_')))) : 0;
		long seed = Rewards.seed(p, "space:" + missionId + (ciphers > 0 ? "#" + ciphers : ""), Clocks.day(server));
		SpaceData.Run run;

		if (kind == MissionDefinition.Objective.SPACE_RUN) {
			// The crate rides in the ship's hold: no ship, or no room, and the run cannot start.
			if (ShipRegistry.of(server, p.getUUID()).isEmpty() || !ShipHolds.stow(server, p.getUUID(), new ItemStack(M5Items.SEALED_CARGO)).isEmpty()) {
				return false;
			}

			run = new SpaceData.Run(missionId, false, system, false, Optional.empty(), seed);
		} else {
			run = new SpaceData.Run(missionId, true, system, ace, Optional.of(SpaceRules.clearPoint(seed)), seed);
		}

		SpaceData.set(p, data.with(run));
		Map<String, Boolean> results = RESULTS.get(p.getUUID());

		if (results != null) {
			results.remove(missionId);
		}

		OldRepublicMod.LOGGER.info("[space] {} started {} {} over {}{}", p.getPlainTextName(), run.clear() ? "space_clear" : "space_run", missionId,
				system.id(), ace ? " (ace)" : "");
		SpaceEncounters.refresh(server, p.getUUID());
		return true;
	}

	/** Ends mission {@code missionId}'s space objective for {@code p} without reporting it (the mission was abandoned or failed). */
	public static void cancel(ServerPlayer p, String missionId) {
		Optional<SpaceData.Run> run = SpaceData.get(p).run(missionId);

		if (run.isPresent()) {
			close(p, run.get());
			OldRepublicMod.LOGGER.info("[space] {} cancelled {}", p.getPlainTextName(), missionId);
		}
	}

	/** Whether {@code p} is flying mission {@code missionId}'s space objective. */
	public static boolean isRunning(ServerPlayer p, String missionId) {
		return SpaceData.get(p).run(missionId).isPresent();
	}

	/**
	 * The mission whose patrol or group {@code fighter} flies in, or null: W4 reads it in
	 * {@code onShipKill} (each Clan Hask fighter of {@code c5_cargo}'s patrol costs clan standing, 5.9).
	 */
	public static @Nullable String missionOf(Entity fighter) {
		Encounter e = SpaceEncounters.encounterOf(fighter);
		return e == null ? null : e.missionId;
	}

	// ---------------------------------------------------------------- ending

	/** The objective ended: reported to W4's mission service, and everything it placed taken away. */
	private static void done(ServerPlayer p, SpaceData.Run run, boolean success) {
		close(p, run);
		RESULTS.computeIfAbsent(p.getUUID(), id -> new HashMap<>()).put(run.missionId(), success);
		OldRepublicMod.LOGGER.info("[space] {} {} {}", p.getPlainTextName(), success ? "completed" : "failed", run.missionId());
		QuestHooks.onSpaceObjectiveDone(p, run.missionId(), success);
	}

	/** Removes the run, its encounter, its buoy and (for a run) the crate. */
	private static void close(ServerPlayer p, SpaceData.Run run) {
		MinecraftServer server = p.level().getServer();
		SpaceData.set(p, SpaceData.get(p).without(run.missionId()));
		Live live = live(p.getUUID(), run.missionId(), false);

		if (live != null && live.buoy != null) {
			ServerLevel level = Planets.orbitLevel(server, run.system());
			Entity buoy = level == null ? null : level.getEntity(live.buoy);

			if (buoy != null) {
				buoy.discard();
			}
		}

		Map<String, Live> runs = LIVE.get(p.getUUID());

		if (runs != null) {
			runs.remove(run.missionId());
		}

		Encounter e = encounter(p.getUUID(), run.missionId());

		if (e != null && !e.finished()) {
			// A patrol that finished its scan is left to turn away; everything else goes now.
			SpaceEncounters.end(server, e, SpaceEncounters.End.MISSION);
		}

		if (!run.clear()) {
			removeCrate(p);
		}

		SpaceEncounters.refresh(server, p.getUUID());
	}

	/** One crate out of the hold, else out of the pockets. */
	private static void removeCrate(ServerPlayer p) {
		if (ShipHolds.take(p.level().getServer(), p.getUUID(), M5Items.SEALED_CARGO, 1) > 0) {
			return;
		}

		for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
			ItemStack stack = p.getInventory().getItem(slot);

			if (stack.is(M5Items.SEALED_CARGO)) {
				stack.shrink(1);
				p.getInventory().setChanged();
				return;
			}
		}
	}

	private static boolean hasCrate(ServerPlayer p) {
		return ShipHolds.count(p.level().getServer(), p.getUUID(), M5Items.SEALED_CARGO) > 0 || p.getInventory().contains(new ItemStack(M5Items.SEALED_CARGO));
	}

	// ---------------------------------------------------------------- ticking

	/** One server tick: each objective of each player whose ship flies in its orbit. */
	static void tick(MinecraftServer server) {
		SpaceBalance b = SpaceBalance.get();
		long now = server.overworld().getGameTime();

		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			List<SpaceData.Run> runs = SpaceData.get(p).runs();
			ShipBody ship = runs.isEmpty() ? null : ShipWeapons.piloted(p);

			if (ship == null || !(ship.level() instanceof ServerLevel level) || ship.state() != ShipState.FLYING) {
				continue;
			}

			for (SpaceData.Run run : runs) {
				if (!level.dimension().equals(Planets.orbit(run.system()))) {
					continue;
				}

				boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;

				if (run.clear()) {
					clear(p, run, ship, level, b, peaceful);
				} else {
					run(p, run, ship, level, b, now, peaceful);
				}

				if (!SpaceData.get(p).run(run.missionId()).isPresent()) {
					// It ended this tick; the rest of the list is stale.
					break;
				}
			}
		}
	}

	private static void run(ServerPlayer p, SpaceData.Run run, ShipBody ship, ServerLevel level, SpaceBalance b, long now, boolean peaceful) {
		Live live = live(p.getUUID(), run.missionId(), true);
		Vec3 at = ship.getBoundingBox().getCenter();

		if (run.point().isEmpty()) {
			run = run.withPoint(SpaceRules.buoyPoint(at, b, RandomSource.create(run.seed() ^ now)));
			SpaceData.set(p, SpaceData.get(p).with(run));
			SpaceEncounters.refresh(level.getServer(), p.getUUID());
		}

		Vec3 point = run.point().get();

		if (live.start == null) {
			live.start = at;
		}

		double distance = at.distanceTo(point);
		placeBuoy(p, live, level, point, distance);

		if (distance <= BUOY_REACH) {
			reachBuoy(p, run, live, level, point);
			return;
		}

		Encounter e = encounter(p.getUUID(), run.missionId());

		if (e == null) {
			double flown = 1.0 - distance / Math.max(1.0, live.start.distanceTo(point));

			if (!live.patrolDone && flown >= PATROL_PROGRESS && !peaceful && SpaceEncounters.ofOwner(p.getUUID()) == null) {
				scanningPatrol(p, run, ship, level, point, b);
			}

			return;
		}

		scan(p, e, live, ship, level, b, now);
	}

	private static void placeBuoy(ServerPlayer p, Live live, ServerLevel level, Vec3 point, double distance) {
		if (distance > BUOY_PLACE || live.buoy != null && level.getEntity(live.buoy) != null) {
			return;
		}

		if (!level.isPositionEntityTicking(BlockPos.containing(point))) {
			return;
		}

		NavBuoy buoy = SpaceEntities.NAV_BUOY.create(level, EntitySpawnReason.EVENT);

		if (buoy != null) {
			buoy.snapTo(point.x, point.y - 1.2, point.z, 0f, 0f);
			buoy.mark(p.getUUID());
			level.addFreshEntity(buoy);
			live.buoy = buoy.getUUID();
		}
	}

	private static void reachBuoy(ServerPlayer p, SpaceData.Run run, Live live, ServerLevel level, Vec3 point) {
		boolean cargo = hasCrate(p);

		if (live.buoy != null && level.getEntity(live.buoy) instanceof NavBuoy buoy) {
			buoy.setReached();
			EffectEvents.around(level, SpaceEffects.NAV_BUOY, point, null, buoy.getId(), 1f, SpaceEffects.BUOY_REACHED);
			// The buoy stays lit for the moment; its own check removes it once the run is gone.
			live.buoy = null;
		}

		done(p, run, cargo);
	}

	/** The run's patrol arrives ahead of the ship, neutral, shadowing it to scan it (5.9). */
	private static void scanningPatrol(ServerPlayer p, SpaceData.Run run, ShipBody ship, ServerLevel level, Vec3 point, SpaceBalance b) {
		Vec3 at = ship.getBoundingBox().getCenter();
		Vec3 ahead = at.add(point.subtract(at).normalize().scale(PATROL_AHEAD));
		Vec3 arrival = SpaceEncounters.arrival(level, ahead, 24);

		if (arrival != null) {
			scanningPatrol(p, run, ship, level, point, arrival, b);
		}
	}

	/** The run's patrol at {@code arrival}: Clan Hask's for {@code c5_cargo}, pirates otherwise, hailing. */
	private static void scanningPatrol(ServerPlayer p, SpaceData.Run run, ShipBody ship, ServerLevel level, Vec3 point, Vec3 arrival, SpaceBalance b) {
		boolean clan = ChapterFiveRules.CARGO.equals(run.missionId());
		SpaceRules.Kind kind = clan ? SpaceRules.Kind.HASK_FIGHTER : SpaceRules.Kind.RAIDER;
		String hail = clan ? SpaceComms.SCAN_CLAN : SpaceComms.SCAN_PIRATE;
		RandomSource random = RandomSource.create(run.seed() + 7);
		int size = 2 + random.nextInt(2);
		Encounter e = SpaceEncounters.begin(Encounter.Type.RUN_PATROL, p, level, run.system(), run.missionId(), point,
				List.of(hail, SpaceComms.SCAN_DONE, SpaceComms.SCAN_PROVOKED));
		SpaceEncounters.group(e, level, kind, arrival, size, SpaceRules.Side.NEUTRAL, ship, b);
		SpaceEncounters.say(level.getServer(), e, hail, SpaceComms.speaker(kind));
	}

	/** The scan meter (5.9): fills while a neutral patrol fighter in range sees the ship, drains while none does; full fails the run. */
	private static void scan(ServerPlayer p, Encounter e, Live live, ShipBody ship, ServerLevel level, SpaceBalance b, long now) {
		if (e.provoked || e.finished()) {
			return;
		}

		boolean scanning = scanned(level, e, ship, b);
		live.scan = SpaceRules.scanStep(live.scan, scanning, b);
		float meter = live.scan / (float) Math.max(1, b.scanTicks);

		if (scanning && !live.scanning) {
			EffectEvents.toPlayer(p, SpaceEffects.SPACE_SCAN, ship.position(), null, ship.getId(), meter, SpaceEffects.SCAN_SCANNING);
		} else if (!scanning && live.scanning && live.scan < b.scanTicks) {
			EffectEvents.toPlayer(p, SpaceEffects.SPACE_SCAN, ship.position(), null, ship.getId(), meter, SpaceEffects.SCAN_BROKEN);
		}

		live.scanning = scanning;

		if (live.scan < b.scanTicks) {
			return;
		}

		// Scanned: the run fails, and the patrol turns away with what it found.
		EffectEvents.toPlayer(p, SpaceEffects.SPACE_SCAN, ship.position(), null, ship.getId(), 1f, SpaceEffects.SCAN_COMPLETE);
		SpaceEncounters.windDown(level.getServer(), e, now);
		SpaceEncounters.say(level.getServer(), e, SpaceComms.SCAN_DONE, SpaceComms.speaker(kindOf(level, e)));
		live.scanning = false;
		SpaceData.get(p).run(e.missionId == null ? "" : e.missionId).ifPresent(run -> done(p, run, false));
	}

	/** Whether a neutral fighter of {@code e} within {@code space.scanRange} of the ship has a clear line to it. */
	private static boolean scanned(ServerLevel level, Encounter e, ShipBody ship, SpaceBalance b) {
		Vec3 target = ship.getBoundingBox().getCenter();

		for (UUID id : e.members) {
			if (!(level.getEntity(id) instanceof Starfighter fighter) || fighter.side() != SpaceRules.Side.NEUTRAL) {
				continue;
			}

			Vec3 from = fighter.getBoundingBox().getCenter();

			if (from.distanceToSqr(target) > (double) b.scanRange * b.scanRange) {
				continue;
			}

			HitResult hit = level.clip(new ClipContext(from, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, fighter));

			if (hit.getType() == HitResult.Type.MISS) {
				return true;
			}
		}

		return false;
	}

	private static SpaceRules.Kind kindOf(ServerLevel level, Encounter e) {
		for (UUID id : e.members) {
			if (level.getEntity(id) instanceof Starfighter fighter) {
				return fighter.kind();
			}
		}

		return SpaceRules.Kind.RAIDER;
	}

	private static void clear(ServerPlayer p, SpaceData.Run run, ShipBody ship, ServerLevel level, SpaceBalance b, boolean peaceful) {
		Vec3 point = run.point().orElse(null);

		if (point == null || encounter(p.getUUID(), run.missionId()) != null) {
			return;
		}

		double distance = ship.getBoundingBox().getCenter().distanceTo(point);

		if (peaceful) {
			// No fighters on Peaceful (5.5): the group has scattered, and reaching its point is enough.
			if (distance <= CLEAR_PEACEFUL_REACH) {
				done(p, run, true);
			}

			return;
		}

		if (distance > CLEAR_PLACE || SpaceEncounters.ofOwner(p.getUUID()) != null || !level.isPositionEntityTicking(BlockPos.containing(point))) {
			return;
		}

		RandomSource random = RandomSource.create(run.seed());
		SpaceRules.Kind kind = run.system() == Planets.Planet.TYRVANN ? SpaceRules.Kind.HYMN_INTERCEPTOR : SpaceRules.Kind.RAIDER;
		String ace = run.ace() ? SpaceComms.ACE_NAME + random.nextInt(SpaceComms.ACE_NAMES) : "";
		String hail = run.ace() ? SpaceComms.ACE_HAIL + random.nextInt(SpaceComms.ACE_HAILS) : SpaceComms.hail(kind, random);
		Encounter e = SpaceEncounters.begin(Encounter.Type.CLEAR, p, level, run.system(), run.missionId(), point, List.of(hail, SpaceComms.ACE_DOWN));
		e.ace = ace;

		if (run.ace()) {
			Starfighter leader = SpaceEncounters.spawn(e, level, kind, point, SpaceRules.Side.HOSTILE, true, ship, b);

			if (leader != null) {
				leader.setCustomName(Component.translatable(ace));
			}

			Vec3 behind = point.subtract(ship.getBoundingBox().getCenter().subtract(point).normalize().scale(8));
			List<Starfighter> wingmen = SpaceEncounters.group(e, level, kind, behind, Math.max(0, b.aceWingmen), SpaceRules.Side.HOSTILE, ship, b);

			for (int i = 0; leader != null && i < wingmen.size(); i++) {
				wingmen.get(i).follow(leader.getUUID(), i + 1);
			}
		} else {
			SpaceEncounters.group(e, level, kind, point, SpaceRules.between(b.clearGroupSize, random, 3), SpaceRules.Side.HOSTILE, ship, b);
		}

		SpaceEncounters.say(level.getServer(), e, hail, run.ace() ? SpaceEffects.SPEAKER_ACE : SpaceComms.speaker(kind));
	}

	// ---------------------------------------------------------------- the encounters' news

	/** A group to clear was destroyed to the last ship: the objective is complete, and a bounty rolls its loot. */
	static void onGroupCleared(MinecraftServer server, Encounter e) {
		ServerPlayer p = server.getPlayerList().getPlayer(e.owner);
		SpaceData.Run run = p == null || e.missionId == null ? null : SpaceData.get(p).run(e.missionId).orElse(null);

		if (p == null || run == null) {
			SpaceEncounters.end(server, e, SpaceEncounters.End.MISSION);
			return;
		}

		if (run.ace()) {
			for (ItemStack stack : Rewards.roll(p, SPACE_BOUNTY, run.seed())) {
				SpaceRewards.give(p, stack);
			}
		}

		done(p, run, true);
	}

	/** A run's patrol was fired on: the scan stops (5.9). */
	static void onProvoked(MinecraftServer server, Encounter e) {
		ServerPlayer p = server.getPlayerList().getPlayer(e.owner);
		Live live = e.missionId == null ? null : live(e.owner, e.missionId, false);

		if (p != null && live != null && live.scanning) {
			live.scanning = false;
			EffectEvents.toPlayer(p, SpaceEffects.SPACE_SCAN, p.position(), null, -1, live.scan / (float) Math.max(1, SpaceBalance.get().scanTicks),
					SpaceEffects.SCAN_BROKEN);
		}
	}

	/**
	 * An objective's encounter ended. A run's patrol destroyed or outrun is gone for this attempt;
	 * one lost when the ship left (or was disabled) comes again on the next approach.
	 */
	static void onEncounterEnded(MinecraftServer server, Encounter e, SpaceEncounters.End reason) {
		Live live = e.missionId == null ? null : live(e.owner, e.missionId, false);

		if (live == null) {
			return;
		}

		ServerPlayer p = server.getPlayerList().getPlayer(e.owner);

		if (live.scanning && p != null) {
			EffectEvents.toPlayer(p, SpaceEffects.SPACE_SCAN, p.position(), null, -1, 0f, SpaceEffects.SCAN_BROKEN);
		}

		live.scanning = false;
		live.scan = 0;
		live.patrolDone = reason == SpaceEncounters.End.CLEARED || reason == SpaceEncounters.End.OUTRUN;

		if (reason == SpaceEncounters.End.LEFT || reason == SpaceEncounters.End.DISABLED || reason == SpaceEncounters.End.LOGOUT) {
			live.start = null;
		}
	}

	/** The owner's ship was disabled: every objective's attempt starts over on the next flight. */
	static void onDisabled(ServerPlayer owner) {
		Map<String, Live> runs = LIVE.get(owner.getUUID());

		if (runs != null) {
			for (Live live : runs.values()) {
				live.start = null;
				live.patrolDone = false;
				live.scan = 0;
				live.scanning = false;
			}
		}
	}

	/** The player left: their objectives' moment-to-moment state is dropped (the objectives stay saved). */
	static void forget(UUID player) {
		LIVE.remove(player);
		RESULTS.remove(player);
	}

	// ---------------------------------------------------------------- the HUD and other readers

	/** The scan meter of {@code owner}'s run (0 to 1), or -1 when no patrol scans them. */
	public static float scanMeter(UUID owner) {
		Map<String, Live> runs = LIVE.get(owner);

		if (runs == null) {
			return -1f;
		}

		for (Map.Entry<String, Live> entry : runs.entrySet()) {
			Encounter e = encounter(owner, entry.getKey());

			if (e != null && !e.provoked && !e.finished()) {
				return entry.getValue().scan / (float) Math.max(1, SpaceBalance.get().scanTicks);
			}
		}

		return -1f;
	}

	/** The objective {@code p} flies in the orbit they are in, as the HUD shows it with no encounter running, or null. */
	static @Nullable SpaceEncounterPayload view(ServerPlayer p) {
		for (SpaceData.Run run : SpaceData.get(p).runs()) {
			if (run.point().isPresent() && p.level().dimension().equals(Planets.orbit(run.system()))) {
				String ace = run.ace() ? SpaceComms.ACE_NAME + RandomSource.create(run.seed()).nextInt(SpaceComms.ACE_NAMES) : "";
				return new SpaceEncounterPayload(run.clear() ? SpaceEncounterPayload.CLEAR : SpaceEncounterPayload.RUN, run.point(), List.of(), ace);
			}
		}

		return null;
	}

	/** Whether {@code p} flies an objective in the orbit they are in (random patrols leave them alone meanwhile). */
	static boolean busy(ServerPlayer p) {
		for (SpaceData.Run run : SpaceData.get(p).runs()) {
			if (p.level().dimension().equals(Planets.orbit(run.system()))) {
				return true;
			}
		}

		return false;
	}

	/** Whether the buoy {@code buoy} still marks a run of {@code runner}'s (the buoy's own check). */
	static boolean buoyWanted(UUID runner, UUID buoy) {
		Map<String, Live> runs = LIVE.get(runner);

		if (runs == null) {
			return false;
		}

		for (Live live : runs.values()) {
			if (buoy.equals(live.buoy)) {
				return true;
			}
		}

		return false;
	}

	private static @Nullable Live live(UUID owner, String missionId, boolean create) {
		if (!create) {
			Map<String, Live> runs = LIVE.get(owner);
			return runs == null ? null : runs.get(missionId);
		}

		return LIVE.computeIfAbsent(owner, id -> new HashMap<>()).computeIfAbsent(missionId, id -> new Live());
	}

	private static @Nullable Encounter encounter(UUID owner, String missionId) {
		Encounter e = SpaceEncounters.ofOwner(owner);
		return e != null && missionId.equals(e.missionId) ? e : null;
	}

	// ---------------------------------------------------------------- game tests

	/**
	 * Game tests: one tick of {@code p}'s objectives with {@code ship} standing in for their piloted,
	 * flying ship in each objective's orbit (the test server has no orbits, and no helm to fly by), on
	 * a world that is not Peaceful.
	 */
	public static void stepForTest(ServerPlayer p, ShipBody ship) {
		if (!(ship.level() instanceof ServerLevel level)) {
			return;
		}

		SpaceBalance b = SpaceBalance.get();
		long now = level.getServer().overworld().getGameTime();

		for (SpaceData.Run run : SpaceData.get(p).runs()) {
			if (run.clear()) {
				clear(p, run, ship, level, b, false);
			} else {
				run(p, run, ship, level, b, now, false);
			}

			if (!SpaceData.get(p).run(run.missionId()).isPresent()) {
				break;
			}
		}

		notPeaceful(p);
	}

	/** The encounter a test hook began plays by a world that is not Peaceful, whatever the difficulty the tests share is meanwhile. */
	private static void notPeaceful(ServerPlayer p) {
		Encounter e = SpaceEncounters.ofOwner(p.getUUID());

		if (e != null && e.peaceful == null) {
			e.peaceful = false;
		}
	}

	/** Game tests: the point of {@code p}'s objective {@code missionId} (the buoy's, or the group's) moved to {@code point}. */
	public static void movePointForTest(ServerPlayer p, String missionId, Vec3 point) {
		SpaceData data = SpaceData.get(p);
		data.run(missionId).ifPresent(run -> SpaceData.set(p, data.with(run.withPoint(point))));
	}

	/**
	 * Game tests: the scanning patrol of {@code p}'s {@code space_run} {@code missionId} arrives at
	 * {@code at} now, as it would ahead of the ship. False when no such run is under way (it needs one
	 * {@link #stepForTest} first) or the owner already has an encounter.
	 */
	public static boolean scanPatrolForTest(ServerPlayer p, String missionId, ShipBody ship, Vec3 at) {
		SpaceData.Run run = SpaceData.get(p).run(missionId).orElse(null);

		if (run == null || run.clear() || run.point().isEmpty() || !(ship.level() instanceof ServerLevel level)
				|| SpaceEncounters.ofOwner(p.getUUID()) != null) {
			return false;
		}

		scanningPatrol(p, run, ship, level, run.point().get(), at, SpaceBalance.get());
		notPeaceful(p);
		return SpaceEncounters.ofOwner(p.getUUID()) != null;
	}

	/** How {@code player}'s objective {@code missionId} last ended (true: completed, false: failed), or null while it runs or if it never ended. */
	public static @Nullable Boolean lastResult(UUID player, String missionId) {
		Map<String, Boolean> results = RESULTS.get(player);
		return results == null ? null : results.get(missionId);
	}

	/** Tests: the moment-to-moment state forgotten. */
	static void reset() {
		LIVE.clear();
		RESULTS.clear();
	}

	private SpaceMissions() {
	}
}
