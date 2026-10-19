package com.oldrepublic.quest.turning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.force.mindtrick.MindTrick;
import com.oldrepublic.force.mindtrick.PowerDialogues;
import com.oldrepublic.identity.AlignmentShift;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.travel.ShipBody;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.vendor.hub.HubRole;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SpawnerGroups;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Return's task, "Amends" (spec MILESTONE_5.md 8.3): three Republic prisoners held at the player's Imperial detention
 * post on Varkhast (W3's {@code detention_post} site, 3.4). Four guards stand there (two security droids and two
 * Imperial troopers) and a warden ({@code turn_warden}). Defeat the guards, or Mind Trick the warden (on Peaceful: talk
 * to him, and there are no guards), and the prisoners follow the player (W5's own escort runtime). At the ship's ramp
 * they board as the task's passengers: removed, and recorded in {@code turning.task}. At the next touchdown on a
 * Greenwatch pad bay on Selvaren they step off beside the ramp. If the ship is disabled or recalled first, the player
 * leaves Varkhast before all three are aboard, or the player dies, they are back in their cells and the step restarts.
 *
 * <p>The post's people live only in memory ({@link Scene}): spawned when the player comes near the post, removed when
 * they go far, log out or restart the step; one saved with its chunk is removed when it loads. Prisoners and the warden
 * cannot be hurt. The troopers stand calm until the player strikes a guard of the post (then they fight the player as
 * an incident, so the fight counts as hostile), so the Mind Trick can walk the prisoners out past them.
 */
final class ReturnTask {
	/** Every person of a post scene, and the landed prisoners. */
	static final String SCENE_TAG = "oldrepublic.turning.post";
	static final String GUARD_TAG = "oldrepublic.turning.guard";
	static final String PRISONER_TAG = "oldrepublic.turning.prisoner";
	static final String WARDEN_TAG = "oldrepublic.turning.warden";
	/** Prisoners put down at the Greenwatch, thanking the player before they walk off. */
	static final String LANDED_TAG = "oldrepublic.turning.landed";
	private static final String INDEX_PREFIX = "oldrepublic.turning.index.";
	private static final String SOURCE = "turn.return.task";
	/** The post's people appear within this distance of the player and go beyond the far one. */
	private static final double SCENE_NEAR = 48.0;
	private static final double SCENE_FAR = 96.0;
	/** A prisoner farther than this from the player falls in beside them. */
	private static final double STRAY = 20.0;
	/** A prisoner this close (horizontally) to the landed ship's hull or its ramp boards. */
	private static final double BOARD_REACH = 5.0;
	private static final double RAMP_REACH = 3.0;
	/** Ticks the landed prisoners stay beside the ramp before they walk off. */
	private static final int THANKS_TICKS = 200;
	/** Ticks between two looks for scenes whose owner left. */
	private static final int SWEEP_TICKS = 200;
	private static final Map<UUID, Scene> SCENES = new HashMap<>();
	/** Owners whose ship was seen flying without them once (a recall); a second look in a row restarts the step. */
	private static final Set<UUID> RECALL_SEEN = new HashSet<>();

	/** One player's detention post as it stands now. */
	private static final class Scene {
		final ResourceKey<Level> dimension;
		final BlockPos post;
		final boolean peaceful;
		final @Nullable UUID[] guards = new UUID[TurningRules.GUARDS];
		final @Nullable UUID[] prisoners = new UUID[TurningRules.PRISONERS];
		final BlockPos[] cells = new BlockPos[TurningRules.PRISONERS];
		@Nullable UUID warden;

		Scene(ResourceKey<Level> dimension, BlockPos post, boolean peaceful) {
			this.dimension = dimension;
			this.post = post;
			this.peaceful = peaceful;

			for (int i = 0; i < cells.length; i++) {
				cells[i] = post;
			}
		}

		boolean holds(UUID id) {
			if (id.equals(warden)) {
				return true;
			}

			for (UUID guard : guards) {
				if (id.equals(guard)) {
					return true;
				}
			}

			for (UUID prisoner : prisoners) {
				if (id.equals(prisoner)) {
					return true;
				}
			}

			return false;
		}
	}

	/** Where the post's people stand. */
	private record Layout(BlockPos warden, BlockPos[] cells, BlockPos[] guards) {
	}

	// ---------------------------------------------------------------- state

	/** Whether the player's Return is at its task. */
	static boolean active(ServerPlayer p) {
		StoryData.Turning t = Story.get(p).turning;
		return t.kind() == TurningKind.RETURN && t.stage() == TurningStage.TASK;
	}

	private static int task(ServerPlayer p) {
		return Story.get(p).turning.task();
	}

	private static void setTask(ServerPlayer p, int task) {
		StoryData data = Story.get(p);

		if (data.turning.task() != task) {
			data.turning = data.turning.withTask(task);
			Story.commit(p, data);
		}
	}

	private static Planets.@Nullable Planet planet(Entity entity) {
		return Planets.Planet.of(entity.level().dimension());
	}

	// ---------------------------------------------------------------- the slow tick

	/** Slow tick at the Return's task: the post, the escort, or the passengers aboard. */
	static void tick(ServerPlayer p) {
		int task = task(p);

		switch (TurningRules.phase(task)) {
			case TurningRules.RETURN_HELD -> tickHeld(p, task);
			case TurningRules.RETURN_ESCORT -> tickEscort(p, task);
			case TurningRules.RETURN_ABOARD -> tickAboard(p);
			default -> setTask(p, TurningRules.returnTask(TurningRules.RETURN_HELD, 0));
		}
	}

	/** Every server tick: drops the scenes of players who are gone (a safety net; logging out removes them too). */
	static void tickServer(MinecraftServer server) {
		if (SCENES.isEmpty() || server.getTickCount() % SWEEP_TICKS != 0) {
			return;
		}

		for (UUID owner : Set.copyOf(SCENES.keySet())) {
			if (server.getPlayerList().getPlayer(owner) == null) {
				teardown(server, owner);
			}
		}
	}

	/** At the post: the scene comes and goes with the player; the prisoners walk out when the last guard falls. */
	private static void tickHeld(ServerPlayer p, int task) {
		MinecraftServer server = p.level().getServer();
		Scene scene = SCENES.get(p.getUUID());

		if (planet(p) != Planets.Planet.VARKHAST) {
			if (scene != null) {
				teardown(server, p.getUUID());
			}

			return;
		}

		BlockPos post = post(p);

		if (post == null) {
			return;
		}

		double dist = Math.sqrt(p.position().subtract(Vec3.atBottomCenterOf(post)).horizontalDistanceSqr());
		boolean peaceful = TurningService.peaceful(p);

		if (scene != null && (dist > SCENE_FAR || scene.peaceful != peaceful)) {
			teardown(server, p.getUUID());
			return;
		}

		if (scene == null) {
			if (dist <= SCENE_NEAR && p.level().hasChunkAt(post)) {
				spawnScene(p, post, task);
			}

			return;
		}

		ServerLevel level = p.level();
		int down = TurningRules.guardsDown(task);

		for (int i = 0; i < TurningRules.GUARDS; i++) {
			UUID id = scene.guards[i];

			if (id != null && (level.getEntity(id) == null || !level.getEntity(id).isAlive())) {
				// Gone without a death we saw (removed by something else): it no longer stands guard.
				scene.guards[i] = null;
				down |= 1 << i;
			}
		}

		for (int i = 0; i < TurningRules.PRISONERS; i++) {
			if (find(level, scene.prisoners[i]) == null) {
				HubNpc prisoner = spawnPrisoner(level, scene.cells[i], i, p.getUUID());
				scene.prisoners[i] = prisoner == null ? null : prisoner.getUUID();
			}
		}

		if (down != TurningRules.guardsDown(task)) {
			setTask(p, TurningRules.returnTask(TurningRules.RETURN_HELD, down));
		}

		if (!scene.peaceful && down == TurningRules.ALL_GUARDS) {
			release(p, scene, "fight");
		}
	}

	/** On the way to the ship: stragglers fall in, the missing come back, and the prisoners board at the ramp. */
	private static void tickEscort(ServerPlayer p, int task) {
		MinecraftServer server = p.level().getServer();

		if (planet(p) != Planets.Planet.VARKHAST) {
			restart(p);
			return;
		}

		ServerLevel level = p.level();
		Scene scene = SCENES.computeIfAbsent(p.getUUID(), id -> new Scene(level.dimension(), p.blockPosition(), TurningService.peaceful(p)));

		if (Math.sqrt(p.position().subtract(Vec3.atBottomCenterOf(scene.post)).horizontalDistanceSqr()) > SCENE_FAR) {
			// The guards and the warden stay behind; the prisoners walk with the player.
			discardPostPeople(level, scene);
		}

		boolean aboard = Ships.isAboard(p);
		ShipBody ship = Ships.ship(server, p.getUUID());
		ShipRecord record = ShipRegistry.of(server, p.getUUID()).orElse(null);
		boolean landedHere = ship != null && ship.level() == level && record != null
				&& (record.state() == ShipState.READY || record.state() == ShipState.GROUNDED);
		Vec3 ramp = landedHere ? ship.getDismountLocationForPassenger(p) : null;
		int boarded = TurningRules.boarded(task);

		for (int i = 0; i < TurningRules.PRISONERS; i++) {
			if ((boarded & (1 << i)) != 0) {
				continue;
			}

			HubNpc prisoner = find(level, scene.prisoners[i]);

			if (prisoner == null && !aboard) {
				prisoner = spawnBeside(level, p, i);
				scene.prisoners[i] = prisoner == null ? null : prisoner.getUUID();
			} else if (prisoner != null && !aboard && prisoner.distanceToSqr(p) > STRAY * STRAY) {
				BlockPos at = TurningService.standNear(level, p.blockPosition(), 1, 3, p.getRandom());

				if (at != null) {
					prisoner.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, prisoner.getYRot(), 0f);
					prisoner.getNavigation().stop();
				}
			}

			if (prisoner != null && landedHere && boards(prisoner, ship, ramp)) {
				prisoner.discard();
				scene.prisoners[i] = null;
				boarded |= 1 << i;
				TurningService.say(p, prisoner.getDisplayName(), "turning.oldrepublic.return.boards");
			}
		}

		if (boarded == TurningRules.ALL_PRISONERS) {
			setTask(p, TurningRules.returnTask(TurningRules.RETURN_ABOARD, TurningRules.guardsDown(task), boarded));
			teardown(server, p.getUUID());
			p.sendSystemMessage(Component.translatable("turning.oldrepublic.return.aboard"));
		} else if (boarded != TurningRules.boarded(task)) {
			setTask(p, TurningRules.returnTask(TurningRules.RETURN_ESCORT, TurningRules.guardsDown(task), boarded));
		}
	}

	/** Near the hull or the ramp of the landed ship. */
	private static boolean boards(HubNpc prisoner, ShipBody ship, @Nullable Vec3 ramp) {
		if (ramp != null && prisoner.position().subtract(ramp).horizontalDistanceSqr() <= RAMP_REACH * RAMP_REACH) {
			return true;
		}

		return prisoner.position().subtract(ship.position()).horizontalDistanceSqr() <= BOARD_REACH * BOARD_REACH
				&& Math.abs(prisoner.getY() - ship.getY()) < 4.0;
	}

	/** Aboard: a disabled ship, a recall (the ship flies without its owner) or a lost ship sends them back to the cells. */
	private static void tickAboard(ServerPlayer p) {
		ShipRecord record = ShipRegistry.of(p.level().getServer(), p.getUUID()).orElse(null);

		if (record == null || record.state() == ShipState.DISABLED || record.state() == ShipState.HULK) {
			restart(p);
			return;
		}

		if (record.state().flying() && !Ships.isAboard(p)) {
			if (!RECALL_SEEN.add(p.getUUID())) {
				restart(p);
			}

			return;
		}

		RECALL_SEEN.remove(p.getUUID());
	}

	// ---------------------------------------------------------------- the post

	/** The post's position on Varkhast: the site once W3 placed it, or the middle of its ring while its template is missing. */
	private static @Nullable BlockPos post(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		GlobalPos found = PlanetSites.ensure(p, PlanetSites.Site.DETENTION_POST, Planets.Planet.VARKHAST);

		if (found == null && !templateReady(p.level())) {
			found = PlanetSites.ringPoint(server, p.getUUID(), PlanetSites.Site.DETENTION_POST, Planets.Planet.VARKHAST);

			if (found != null && p.level().hasChunkAt(found.pos())) {
				found = GlobalPos.of(found.dimension(), p.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, found.pos()));
			}
		}

		return found != null && found.dimension().equals(p.level().dimension()) ? found.pos() : null;
	}

	/** Where the Calling points while the prisoners are held: the post, or its ring until it is placed. */
	static @Nullable GlobalPos guidancePost(ServerPlayer p) {
		GlobalPos found = PlanetSites.find(p, PlanetSites.Site.DETENTION_POST);
		return found != null ? found
				: PlanetSites.ringPoint(p.level().getServer(), p.getUUID(), PlanetSites.Site.DETENTION_POST, Planets.Planet.VARKHAST);
	}

	private static boolean templateReady(ServerLevel level) {
		return level.getStructureManager().get(PlanetSites.DETENTION_POST_PIECE.template()).isPresent();
	}

	private static void spawnScene(ServerPlayer p, BlockPos post, int task) {
		ServerLevel level = p.level();
		boolean peaceful = TurningService.peaceful(p);
		Layout layout = layout(level, post);
		Scene scene = new Scene(level.dimension(), post, peaceful);
		SCENES.put(p.getUUID(), scene);
		int down = TurningRules.guardsDown(task);

		if (!peaceful) {
			for (int i = 0; i < TurningRules.GUARDS; i++) {
				if ((down & (1 << i)) != 0) {
					continue;
				}

				LivingEntity guard = spawnGuard(level, layout.guards()[i], i, p.getUUID());

				if (guard == null) {
					// A guard that could not be made (its feature missing) does not stand in the way.
					down |= 1 << i;
				} else {
					scene.guards[i] = guard.getUUID();
				}
			}
		}

		HubNpc warden = HubNpcs.spawn(level, layout.warden(), HubRole.HOST, "warden");

		if (warden != null) {
			mark(warden, p.getUUID(), WARDEN_TAG, -1);
			warden.setAnchor(layout.warden(), 2);
			scene.warden = warden.getUUID();
		}

		for (int i = 0; i < TurningRules.PRISONERS; i++) {
			scene.cells[i] = layout.cells()[i];
			HubNpc prisoner = spawnPrisoner(level, layout.cells()[i], i, p.getUUID());
			scene.prisoners[i] = prisoner == null ? null : prisoner.getUUID();
		}

		if (down != TurningRules.guardsDown(task)) {
			setTask(p, TurningRules.returnTask(TurningRules.RETURN_HELD, down));
		}
	}

	/** The anchors of the post's template, or a plain layout around the post while the template is missing. */
	private static Layout layout(ServerLevel level, BlockPos post) {
		SitePiece piece = piece(level, post);

		if (piece != null) {
			BlockPos warden = SiteAnchors.find(piece, SiteAnchors.Anchor.WARDEN);
			BlockPos[] cells = {SiteAnchors.find(piece, SiteAnchors.Anchor.PRISONER_1), SiteAnchors.find(piece, SiteAnchors.Anchor.PRISONER_2),
					SiteAnchors.find(piece, SiteAnchors.Anchor.PRISONER_3)};
			BlockPos[] guards = {SiteAnchors.find(piece, SiteAnchors.Anchor.GUARD_1), SiteAnchors.find(piece, SiteAnchors.Anchor.GUARD_2),
					SiteAnchors.find(piece, SiteAnchors.Anchor.GUARD_3), SiteAnchors.find(piece, SiteAnchors.Anchor.GUARD_4)};

			if (warden != null && allSet(cells) && allSet(guards)) {
				return new Layout(warden, cells, guards);
			}
		}

		BlockPos[] cells = {stand(level, post.offset(-2, 0, 3)), stand(level, post.offset(0, 0, 3)), stand(level, post.offset(2, 0, 3))};
		BlockPos[] guards = {stand(level, post.offset(-4, 0, -1)), stand(level, post.offset(4, 0, -1)), stand(level, post.offset(-3, 0, 6)),
				stand(level, post.offset(3, 0, 6))};
		return new Layout(stand(level, post.offset(0, 0, -2)), cells, guards);
	}

	private static boolean allSet(@Nullable BlockPos[] positions) {
		for (BlockPos pos : positions) {
			if (pos == null) {
				return false;
			}
		}

		return true;
	}

	private static @Nullable SitePiece piece(ServerLevel level, BlockPos post) {
		WorldSites.SiteRecord record = WorldSites.nearest(level.getServer(), SiteKind.DETENTION_POST, level.dimension(), post, 48);
		return record == null ? null : RuntimeSites.pieceOf(level, record);
	}

	/** A feet cell at {@code near}'s column: the nearest standable height, else the surface. */
	private static BlockPos stand(ServerLevel level, BlockPos near) {
		for (int dy = 0; dy <= 6; dy++) {
			if (TurningService.standable(level, near.above(dy))) {
				return near.above(dy);
			}

			if (dy > 0 && TurningService.standable(level, near.below(dy))) {
				return near.below(dy);
			}
		}

		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near);
	}

	private static @Nullable LivingEntity spawnGuard(ServerLevel level, BlockPos at, int index, UUID owner) {
		List<LivingEntity> spawned = index < 2
				? SpawnerGroups.spawn(level, at, SpawnerGroups.SECURITY_DROIDS, 1, false, owner)
				: Troopers.spawnSquad(level, at, Faction.SITH_EMPIRE, List.of(TrooperRole.RIFLEMAN), owner, IncidentMode.NONE);
		LivingEntity guard = spawned.isEmpty() ? null : spawned.getFirst();

		if (guard != null) {
			mark(guard, owner, GUARD_TAG, index);
		}

		return guard;
	}

	private static @Nullable HubNpc spawnPrisoner(ServerLevel level, BlockPos at, int index, UUID owner) {
		HubNpc prisoner = HubNpcs.spawn(level, at, HubRole.VENDOR, "republic_prisoner");

		if (prisoner == null) {
			return null;
		}

		prisoner.setCustomName(Component.translatable("turning.oldrepublic.prisoner." + (index + 1)));
		prisoner.setAnchor(at, 1);
		mark(prisoner, owner, PRISONER_TAG, index);
		return prisoner;
	}

	/** A prisoner who fell behind (or a restart lost them) falls in again beside the player, following. */
	private static @Nullable HubNpc spawnBeside(ServerLevel level, ServerPlayer p, int index) {
		BlockPos at = TurningService.standNear(level, p.blockPosition(), 1, 3, p.getRandom());
		HubNpc prisoner = at == null ? null : spawnPrisoner(level, at, index, p.getUUID());

		if (prisoner != null) {
			prisoner.setEscortOwner(p.getUUID());
		}

		return prisoner;
	}

	private static void mark(Entity entity, UUID owner, String role, int index) {
		StoryTags.setOwner(entity, owner);
		entity.addTag(SCENE_TAG);
		entity.addTag(role);

		if (index >= 0) {
			entity.addTag(INDEX_PREFIX + index);
		}
	}

	private static int index(Entity entity) {
		for (String tag : entity.entityTags()) {
			if (tag.startsWith(INDEX_PREFIX)) {
				try {
					return Integer.parseInt(tag.substring(INDEX_PREFIX.length()));
				} catch (NumberFormatException e) {
					return -1;
				}
			}
		}

		return -1;
	}

	private static @Nullable HubNpc find(ServerLevel level, @Nullable UUID id) {
		Entity entity = id == null ? null : level.getEntity(id);
		return entity instanceof HubNpc npc && npc.isAlive() ? npc : null;
	}

	// ---------------------------------------------------------------- freeing them

	/** {@code turn:free trick|talk}: the warden opens the cells, to a Mind Trick or (on Peaceful) a talk. */
	static void free(ServerPlayer p, @Nullable Entity speaker, String how) {
		Scene scene = SCENES.get(p.getUUID());

		if (!active(p) || TurningRules.phase(task(p)) != TurningRules.RETURN_HELD || scene == null) {
			return;
		}

		switch (how) {
			case "trick" -> {
				// The option's condition was checked when it was chosen; checked again here for any other caller.
				DialogueConditions.Condition knows = DialogueConditions.get(PowerDialogues.KNOWS);

				if (knows == null || knows.failReason(p, speaker, MindTrick.ID) != null) {
					return;
				}

				DialogueActions.Action trick = DialogueActions.get(PowerDialogues.MIND_TRICK_USE);

				if (trick != null) {
					trick.run(p, speaker);
				}
			}
			case "talk" -> {
				if (!TurningService.peaceful(p)) {
					return;
				}
			}
			default -> {
				return;
			}
		}

		release(p, scene, how);
	}

	/** The cells open: the prisoners follow the player (fight, trick or talk). */
	private static void release(ServerPlayer p, Scene scene, String how) {
		ServerLevel level = p.level();

		for (int i = 0; i < TurningRules.PRISONERS; i++) {
			HubNpc prisoner = find(level, scene.prisoners[i]);

			if (prisoner == null) {
				prisoner = spawnPrisoner(level, scene.cells[i], i, p.getUUID());
				scene.prisoners[i] = prisoner == null ? null : prisoner.getUUID();
			}

			if (prisoner != null) {
				prisoner.setEscortOwner(p.getUUID());
			}
		}

		setTask(p, TurningRules.returnTask(TurningRules.RETURN_ESCORT, TurningRules.guardsDown(task(p))));
		p.sendSystemMessage(Component.translatable("turning.oldrepublic.return.freed." + how));
		TurningService.say(p, Component.translatable("turning.oldrepublic.prisoner.1"), "turning.oldrepublic.return.prisoner.freed");
	}

	/** Whether the player's prisoners are waiting at the post (the warden's dialogue offers the trick or the talk). */
	static boolean held(ServerPlayer p) {
		return active(p) && TurningRules.phase(task(p)) == TurningRules.RETURN_HELD;
	}

	// ---------------------------------------------------------------- the Greenwatch

	/**
	 * A touchdown (W1's {@code onPlanetArrived}): on a Greenwatch pad bay with the prisoners aboard, they step off beside
	 * the ramp and the task is done.
	 */
	static void onPlanetArrived(ServerPlayer p, Planets.Planet planet) {
		if (!active(p) || TurningRules.phase(task(p)) != TurningRules.RETURN_ABOARD || planet != Planets.Planet.SELVAREN) {
			return;
		}

		MinecraftServer server = p.level().getServer();
		ShipBody ship = Ships.ship(server, p.getUUID());
		Vec3 at = ship != null ? ship.position() : p.position();

		if (!TurningRules.onGreenwatchBay(at, PlanetHubs.padBays(server, Planets.Planet.SELVAREN), PlanetHubs.hub(server, Planets.Planet.SELVAREN))) {
			p.sendSystemMessage(Component.translatable("turning.oldrepublic.return.not_here"));
			return;
		}

		ServerLevel level = ship != null && ship.level() instanceof ServerLevel shipLevel ? shipLevel : p.level();
		Vec3 ramp = ship != null ? ship.getDismountLocationForPassenger(p) : p.position();
		BlockPos base = BlockPos.containing(ramp);

		for (int i = 0; i < TurningRules.PRISONERS; i++) {
			BlockPos spot = TurningService.standNear(level, base, 1, 2, p.getRandom());
			HubNpc prisoner = HubNpcs.spawn(level, spot != null ? spot : base, HubRole.VENDOR, "republic_prisoner");

			if (prisoner != null) {
				prisoner.setCustomName(Component.translatable("turning.oldrepublic.prisoner." + (i + 1)));
				StoryTags.setOwner(prisoner, p.getUUID());
				prisoner.addTag(LANDED_TAG);
				prisoner.addTag(PRISONER_TAG);
				TurningService.walkOff(prisoner, ramp, THANKS_TICKS);
			}
		}

		TurningService.say(p, Component.translatable("turning.oldrepublic.prisoner.1"), "turning.oldrepublic.return.prisoner.thanks");
		complete(p);
	}

	/** "Amends" is done: 1,500 XP, Jedi Order +100, Sith Empire -150, light +5, entry {@code amends}; Sorn will come. */
	static void complete(ServerPlayer p) {
		TurningBalance b = TurningService.balance();
		StoryData data = Story.get(p);
		data.turning = data.turning.withStage(TurningKind.RETURN, TurningStage.CONFRONTATION).withTask(TurningRules.CONFRONT_WAITING);
		Story.commit(p, data);
		teardown(p.level().getServer(), p.getUUID());
		RECALL_SEEN.remove(p.getUUID());

		Progression.addXp(p, b.taskXp, "turning");
		Identity.changeReputation(p, Faction.JEDI_ORDER, b.taskRepGain, SOURCE, true);
		Identity.changeReputation(p, Faction.SITH_EMPIRE, b.taskRepLoss, SOURCE, true);
		Identity.shift(p, new AlignmentShift(SOURCE, b.returnLight, AlignmentShift.Kind.MAJOR, "turning.oldrepublic.reason.return_task"));
		TurningService.entry(p, JournalEntries.AMENDS);
		p.sendSystemMessage(Component.translatable("turning.oldrepublic.return.delivered"));
		Confrontations.waitFor(p);
	}

	// ---------------------------------------------------------------- restarts and cleanup

	/** Back in their cells: the step starts again (the player died, left Varkhast, or the ship was disabled or recalled). */
	static void restart(ServerPlayer p) {
		if (!active(p)) {
			return;
		}

		teardown(p.level().getServer(), p.getUUID());
		RECALL_SEEN.remove(p.getUUID());
		int start = TurningRules.returnTask(TurningRules.RETURN_HELD, 0);

		if (task(p) != start) {
			setTask(p, start);
			p.sendSystemMessage(Component.translatable("turning.oldrepublic.return.restart"));
		}
	}

	/** Removes the player's scene and every one of its people still in the world. */
	private static void teardown(MinecraftServer server, UUID owner) {
		Scene scene = SCENES.remove(owner);
		ServerLevel level = scene == null ? null : server.getLevel(scene.dimension);

		if (level == null) {
			return;
		}

		discardPostPeople(level, scene);

		for (int i = 0; i < scene.prisoners.length; i++) {
			discard(level, scene.prisoners[i]);
			scene.prisoners[i] = null;
		}
	}

	private static void discardPostPeople(ServerLevel level, Scene scene) {
		for (int i = 0; i < scene.guards.length; i++) {
			discard(level, scene.guards[i]);
			scene.guards[i] = null;
		}

		discard(level, scene.warden);
		scene.warden = null;
	}

	private static void discard(ServerLevel level, @Nullable UUID id) {
		Entity entity = id == null ? null : level.getEntity(id);

		if (entity != null) {
			entity.discard();
		}
	}

	// ---------------------------------------------------------------- events

	/** A guard of a post died: one fewer between the player and the cells. */
	static void onEntityDied(LivingEntity entity) {
		if (!entity.entityTags().contains(GUARD_TAG) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}

		UUID owner = StoryTags.owner(entity);
		int index = index(entity);
		ServerPlayer p = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);

		if (p == null || index < 0 || index >= TurningRules.GUARDS || !held(p)) {
			return;
		}

		int task = task(p);
		setTask(p, TurningRules.returnTask(TurningRules.RETURN_HELD, TurningRules.guardsDown(task) | (1 << index)));
		Scene scene = SCENES.get(owner);

		if (scene != null) {
			scene.guards[index] = null;
		}
	}

	/**
	 * Fabric {@code ALLOW_DAMAGE}: the prisoners and the warden cannot be hurt (but by {@code /kill} and the void); the
	 * owner's first blow on a guard of the post turns the troopers on them, so the fight counts as hostile.
	 */
	static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		Set<String> tags = entity.entityTags();

		if (!tags.contains(SCENE_TAG) && !tags.contains(LANDED_TAG)) {
			return true;
		}

		if (tags.contains(PRISONER_TAG) || tags.contains(WARDEN_TAG)) {
			return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		}

		if (tags.contains(GUARD_TAG) && source.getEntity() instanceof ServerPlayer attacker && StoryTags.isOwnedBy(entity, attacker.getUUID())) {
			provoke(attacker);
		}

		return true;
	}

	/** The troopers of the player's post fight them from now on (an incident with the owner that never ends). */
	private static void provoke(ServerPlayer p) {
		Scene scene = SCENES.get(p.getUUID());
		ServerLevel level = scene == null ? null : p.level().getServer().getLevel(scene.dimension);

		if (level == null) {
			return;
		}

		for (UUID id : scene.guards) {
			if (id != null && level.getEntity(id) instanceof TrooperEntity trooper && trooper.incidentMode() != IncidentMode.ARMY_AND_OWNER) {
				trooper.setIncident(IncidentMode.ARMY_AND_OWNER, Long.MAX_VALUE);
			}
		}
	}

	/** A person of a post saved with its chunk (or a landed prisoner) is no longer anybody's: it leaves. */
	static void onEntityLoad(Entity entity) {
		Set<String> tags = entity.entityTags();

		if (!tags.contains(SCENE_TAG) && !tags.contains(LANDED_TAG)) {
			return;
		}

		if (!tags.contains(LANDED_TAG)) {
			for (Scene scene : SCENES.values()) {
				if (scene.holds(entity.getUUID())) {
					return;
				}
			}
		}

		TurningService.discardLater(entity);
	}

	/** The player used a person of their post: the warden's dialogue, or a prisoner's word. Returns whether it was handled. */
	static boolean onUsed(ServerPlayer p, HubNpc npc) {
		Set<String> tags = npc.entityTags();

		if (!tags.contains(SCENE_TAG) && !tags.contains(LANDED_TAG)) {
			return false;
		}

		if (!StoryTags.isOwnedBy(npc, p.getUUID())) {
			p.sendOverlayMessage(Component.translatable("turning.oldrepublic.return.not_yours"));
			return true;
		}

		if (tags.contains(WARDEN_TAG)) {
			Dialogues.open(p, OldRepublicMod.id("turn_warden"), npc);
		} else if (tags.contains(PRISONER_TAG)) {
			String line = tags.contains(LANDED_TAG) ? "thanks" : held(p) ? "held" : "follow";
			TurningService.say(p, npc.getDisplayName(), "turning.oldrepublic.return.prisoner." + line);
		}

		return true;
	}

	static void forget(ServerPlayer p) {
		teardown(p.level().getServer(), p.getUUID());
		RECALL_SEEN.remove(p.getUUID());
	}

	static void clear() {
		SCENES.clear();
		RECALL_SEEN.clear();
	}

	private ReturnTask() {
	}
}
