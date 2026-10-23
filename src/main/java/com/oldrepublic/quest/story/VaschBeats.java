package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.enemy.Poachers;
import com.oldrepublic.mission.Missions;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vasch's presence on the trail (spec 6.4), two cheap beats, once each: at two leads the next
 * holocron carries his recorded message; at three leads his paid poachers hunt the player the next
 * time they are on the surface away from every site.
 */
final class VaschBeats {
	/** Marks the counter-hunt's poachers (with the owner kept in memory; none survives a restart). */
	static final String AMBUSH_TAG = "oldrepublic.c3.ambush";
	/** Candidate spots tried per attempt, and the wait before trying again when none was hidden. */
	private static final int SPOT_TRIES = 16;
	private static final int NO_SPOT_RETRY_TICKS = 200;
	/** How high above the ground the hidden-spot check looks (a standing poacher's eyes). */
	private static final double EYE_HEIGHT = 1.6;

	/** The counter-hunt's poachers per owner. */
	private static final Map<UUID, List<UUID>> AMBUSHERS = new HashMap<>();

	// ---------------------------------------------------------------- the message

	/** Whether the next attunement should carry Vasch's message (the {@code c3:vasch_message} condition). */
	static boolean messageDue(StoryData data, ChapterThreeBalance b) {
		return data.chapter3.leads().size() >= b.vaschMessageLeads && !data.hasFlag(ChapterThreeRules.VASCH_MESSAGE);
	}

	/** The message was heard (W3's gatekeeper node, through the {@code c3:vasch_message} action). */
	static void messageHeard(ServerPlayer p) {
		StoryData data = Story.get(p);

		if (data.flags.add(ChapterThreeRules.VASCH_MESSAGE)) {
			Story.commit(p, data);
			ChapterThreeService.addJournalNote(p, JournalEntries.FIRST_ECHO, "quest.oldrepublic.c3.note.vasch_message");
		}
	}

	/**
	 * After an attunement: if the player had enough leads before it and the message is still unheard,
	 * the gatekeeper's dialogue played it as its last node (W3 opens that node on the same rule), so
	 * it is marked heard here even when the node's own {@code c3:vasch_message} action did not run.
	 */
	static void onHolocronAttuned(ServerPlayer p, int leadsBefore) {
		if (leadsBefore >= ChapterThreeService.balance().vaschMessageLeads) {
			messageHeard(p);
		}
	}

	// ---------------------------------------------------------------- the counter-hunt

	/** Slow tick: removes a hunt the player outran, and starts it when the moment is right. */
	static void tick(ServerPlayer p) {
		dismissIfFar(p);
		StoryData data = Story.get(p);
		ChapterThreeBalance b = ChapterThreeService.balance();
		ChapterThreeStage stage = data.chapter3.stage();

		if (stage.ordinal() < ChapterThreeStage.LEADS.ordinal() || stage.ordinal() > ChapterThreeStage.RELIQUARY.ordinal()
				|| data.chapter3.leads().size() < b.ambushLeads || data.hasFlag(ChapterThreeRules.AMBUSH) || !StoryService.inOverworld(p)
				|| OnesThatSing.dreaming(p) || !ChapterThreeService.due(p, "ambush") || !onSurface(p) || !awayFromSites(p, data, b.ambushMinSiteDistance)) {
			return;
		}

		Vec3 spot = hiddenSpot(p, b);

		if (spot == null) {
			ChapterThreeService.retryLater(p, "ambush", NO_SPOT_RETRY_TICKS);
			return;
		}

		ServerLevel level = p.level();
		int count = Missions.campCrew(Progression.get(p).level);
		List<LivingEntity> crew = Poachers.spawnCrew(level, BlockPos.containing(spot), count, p.getUUID());

		if (crew.isEmpty()) {
			ChapterThreeService.retryLater(p, "ambush", b.retryTicks);
			return;
		}

		List<UUID> ids = new ArrayList<>();

		for (LivingEntity poacher : crew) {
			poacher.addTag(AMBUSH_TAG);
			ids.add(poacher.getUUID());

			if (poacher instanceof Mob mob) {
				mob.setTarget(p);
			}
		}

		AMBUSHERS.put(p.getUUID(), ids);
		data.flags.add(ChapterThreeRules.AMBUSH);
		Story.commit(p, data);
		StoryService.say(p, Component.translatable("npc.oldrepublic.c3.ambusher"), "message.oldrepublic.story.c3.ambush_bark");
		ChapterThreeService.addJournalNote(p, JournalEntries.FIRST_ECHO, "quest.oldrepublic.c3.note.ambush");
	}

	/** On the surface: standing at (or above) the highest motion-blocking block of the column. */
	private static boolean onSurface(ServerPlayer p) {
		int top = p.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ());
		return p.getY() >= top - 1;
	}

	/**
	 * Away from every site: not inside a site piece, no runtime site record and no cached worldgen
	 * site within {@code distance}, and none of the player's own story places.
	 */
	private static boolean awayFromSites(ServerPlayer p, StoryData data, int distance) {
		ServerLevel level = p.level();
		BlockPos at = p.blockPosition();

		if (StructureLocator.pieceAt(level, at) != null) {
			return false;
		}

		for (SiteKind kind : SiteKind.values()) {
			SitePiece cached = StructureLocator.cached(p, kind);

			if (WorldSites.nearest(level.getServer(), kind, level.dimension(), at, distance) != null
					|| (cached != null && near(at, cached.box().getCenter(), distance))) {
				return false;
			}
		}

		// The player's own story sites (with the Chapter Three fall, shrines and Reliquary).
		List<BlockPos> own = new ArrayList<>();
		Crossing.addSites(own, data);
		return own.stream().noneMatch(place -> near(at, place, distance));
	}

	private static boolean near(BlockPos a, BlockPos b, int distance) {
		long dx = a.getX() - b.getX();
		long dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz < (long) distance * distance;
	}

	/** A loaded ground spot {@code ambushRange} blocks away that the player cannot see, or null. */
	private static @Nullable Vec3 hiddenSpot(ServerPlayer p, ChapterThreeBalance b) {
		ServerLevel level = p.level();
		Vec3 eye = p.getEyePosition();
		int min = b.ambushRange[0];
		int max = b.ambushRange.length > 1 ? b.ambushRange[1] : min;
		double phase = p.getRandom().nextDouble() * Math.PI * 2;

		for (int i = 0; i < SPOT_TRIES; i++) {
			double angle = phase + i * Math.PI * 2 / SPOT_TRIES;
			double radius = min + (max - min) * ((i * 7) % SPOT_TRIES) / (double) SPOT_TRIES;
			double x = p.getX() + Math.cos(angle) * radius;
			double z = p.getZ() + Math.sin(angle) * radius;

			if (!level.hasChunkAt(BlockPos.containing(x, p.getY(), z))) {
				continue;
			}

			Vec3 ground = StoryMentors.ground(level, x, z);
			Vec3 head = ground.add(0, EYE_HEIGHT, 0);
			boolean hidden = level.clip(new ClipContext(eye, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS;

			if (hidden && !level.getBlockState(BlockPos.containing(ground)).liquid()) {
				return ground;
			}
		}

		return null;
	}

	/** The hunt breaks off when the player gets {@code ambushDespawnDistance} away (or leaves the level). */
	private static void dismissIfFar(ServerPlayer p) {
		List<UUID> ids = AMBUSHERS.get(p.getUUID());

		if (ids == null) {
			return;
		}

		double far = ChapterThreeService.balance().ambushDespawnDistance;
		ids.removeIf(id -> {
			Entity poacher = p.level().getEntityInAnyDimension(id);

			if (poacher == null || !poacher.isAlive()) {
				return true;
			}

			if (poacher.level() != p.level() || poacher.distanceToSqr(p) > far * far) {
				poacher.discard();
				return true;
			}

			return false;
		});

		if (ids.isEmpty()) {
			AMBUSHERS.remove(p.getUUID());
		}
	}

	/** Whether a loaded entity is a leftover of a counter-hunt no one tracks any more (after a restart). */
	static boolean isLeftover(Entity entity) {
		if (!entity.entityTags().contains(AMBUSH_TAG)) {
			return false;
		}

		return AMBUSHERS.values().stream().noneMatch(ids -> ids.contains(entity.getUUID()));
	}

	/** The player left: their hunters go with them. */
	static void forget(ServerPlayer p) {
		List<UUID> ids = AMBUSHERS.remove(p.getUUID());

		if (ids != null) {
			for (UUID id : ids) {
				Entity poacher = p.level().getEntityInAnyDimension(id);

				if (poacher != null) {
					poacher.discard();
				}
			}
		}
	}

	static void clear() {
		AMBUSHERS.clear();
	}

	private VaschBeats() {
	}
}
