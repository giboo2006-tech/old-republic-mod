package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.identity.Path;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.world.EchoShrines;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;
import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The first echo (spec 6.3) and the old-world guarantee (4.3). FIRST_ECHO picks a target: the
 * nearest unused worldgen site of the path's kind within {@code firstEchoRadius} (an Independent
 * takes the nearest wreck, ruin or tomb), else an Echo Shrine placed for the player far away
 * ({@code chapter_three.shrineRange}, docs/MILESTONE_6.md 3.5), or on the near rings when far
 * placement fails. The journal's Calling points to it ({@link StoryGuidance}); any pedestal of the
 * right kind ends the stage. Later, a player whose rank 3 holocrons are still missing and who has
 * no unused chamber nearby gets one extra shrine per needed kind, placed the same way.
 *
 * <p>Targets found by the locator and far requests under way are kept in memory (a restart searches
 * and asks again; the request is idempotent); shrines are saved in {@link StoryData.Chapter3#shrines}.
 */
final class FirstEcho {
	/** A pedestal the Calling points to, and the kind it holds (the Calling's tint). */
	record Target(BlockPos pos, HolocronKind kind) {
	}

	private static final Map<UUID, Target> TARGETS = new HashMap<>();
	private static final Set<UUID> SEARCHING = new HashSet<>();
	/** The extra shrine for the old-world guarantee, while its search runs, per player. */
	private static final Set<UUID> EXTRA_SEARCHING = new HashSet<>();
	/** The first echo's shrine and the old-world guarantee's shrine while far placement runs, per player: the kind. */
	private static final Map<UUID, HolocronKind> PLACING = new HashMap<>();
	private static final Map<UUID, HolocronKind> PLACING_EXTRA = new HashMap<>();
	/** Relaxed site rules for the M2 forward fallback shrine (a single ring {@code fallbackForward} out, or past the shrine's half-width). */
	private static final int SHRINE_FOOTPRINT = 7;
	private static final int SHRINE_CLEARANCE = 11;
	/** The forward fallback leaves at least this gap between the player and the shrine's edge. */
	private static final int FORWARD_GAP = 4;

	/**
	 * FIRST_ECHO begins. If a holocron of the path's kind is attuned already, the stage ends at once
	 * and the "careful man" line goes into the journal; otherwise the search starts.
	 */
	static void begin(ServerPlayer p) {
		Path path = Story.get(p).storyPath();
		String met = ChapterThreeRules.alreadyMet(path, id -> HolocronService.hasAttuned(p, id));

		if (met != null) {
			ChapterThreeService.addLead(p, ChapterThreeRules.LEAD_HOLOCRON_PREFIX + met);
			ChapterThreeService.addJournalNote(p, JournalEntries.FIRST_ECHO, "quest.oldrepublic.c3.note.careful_man");
			leave(p);
			return;
		}

		TARGETS.remove(p.getUUID());
		search(p);
	}

	/** Slow tick at FIRST_ECHO: the far shrine's request while it runs; after a restart the saved shrine is the target; a failed search retries. */
	static void tick(ServerPlayer p) {
		UUID id = p.getUUID();
		HolocronKind placing = PLACING.get(id);

		if (placing != null) {
			requestShrine(p, placing);
			return;
		}

		if (TARGETS.containsKey(id) || SEARCHING.contains(id)) {
			return;
		}

		StoryData data = Story.get(p);
		HolocronKind kind = ChapterThreeRules.echoKind(data.storyPath());

		if (!data.chapter3.shrines().isEmpty()) {
			TARGETS.put(id, new Target(data.chapter3.shrines().getFirst(), kind));
		} else if (ChapterThreeService.due(p, "first_echo")) {
			search(p);
		}
	}

	/** FIRST_ECHO is left: the entry, the codex, and on to LEADS. */
	static void leave(ServerPlayer p) {
		TARGETS.remove(p.getUUID());
		PLACING.remove(p.getUUID());
		Journal.unlockEntry(p, JournalEntries.FIRST_ECHO);
		Journal.unlockCodex(p, JournalEntries.CODEX_THE_COLLECTORS_TRAIL);
		ChapterThreeService.enterStage(p, ChapterThreeStage.LEADS);
	}

	/** Where the Calling points during FIRST_ECHO: the target, the far shrine's target while it is placed, or null while the search runs. */
	static @Nullable Target target(ServerPlayer p) {
		Target target = TARGETS.get(p.getUUID());
		HolocronKind placing = PLACING.get(p.getUUID());

		if (target != null || placing == null) {
			return target;
		}

		BlockPos pointer = StorySites.pendingPointer(p.level(), StorySites.shrineKey(p.getUUID(), placing, false));
		return pointer == null ? null : new Target(pointer, placing);
	}

	private static void search(ServerPlayer p) {
		ChapterThreeBalance b = ChapterThreeService.balance();

		if (!StoryService.inOverworld(p)) {
			ChapterThreeService.retryLater(p, "first_echo", b.retryTicks);
			return;
		}

		Path path = Story.get(p).storyPath();
		HolocronKind kind = ChapterThreeRules.echoKind(path);
		List<SiteKind> kinds = ChapterThreeRules.echoSites(path);
		UUID id = p.getUUID();
		MinecraftServer server = p.level().getServer();
		List<SitePiece> found = new ArrayList<>();
		int[] pending = {kinds.size()};
		SEARCHING.add(id);

		for (SiteKind siteKind : kinds) {
			StructureLocator.find(p, siteKind, b.firstEchoRadius, HolocronService.usedSites(p), piece -> {
				if (piece != null) {
					found.add(piece);
				}

				if (--pending[0] == 0) {
					SEARCHING.remove(id);
					ServerPlayer player = server.getPlayerList().getPlayer(id);

					if (player != null) {
						onSearched(player, found, kind);
					}
				}
			});
		}
	}

	/** Every search answered: the nearest pedestal found, else a shrine placed far away (the near rings when that fails); neither retries later. */
	private static void onSearched(ServerPlayer p, List<SitePiece> found, HolocronKind kind) {
		StoryData data = Story.get(p);

		if (data.chapter3.stage() != ChapterThreeStage.FIRST_ECHO) {
			return;
		}

		BlockPos nearest = null;

		for (SitePiece piece : found) {
			BlockPos pedestal = pedestal(piece, kind);

			if (nearest == null || p.blockPosition().distSqr(pedestal) < p.blockPosition().distSqr(nearest)) {
				nearest = pedestal;
			}
		}

		if (nearest == null && data.chapter3.shrines().size() < StoryData.Chapter3.MAX_SHRINES) {
			requestShrine(p, kind);
			return;
		}

		settleTarget(p, nearest, kind);
	}

	/**
	 * The first echo's shrine through far placement: recorded and made the target once placed, polled
	 * by {@link #tick} while under way, and the near rings ({@link #settleTarget}) when it fails.
	 */
	private static void requestShrine(ServerPlayer p, HolocronKind kind) {
		UUID id = p.getUUID();
		FarSites.Status status = EchoShrines.request(p, kind, StorySites.shrineKey(id, kind, false));

		if (StorySites.pending(status)) {
			PLACING.put(id, kind);
			return;
		}

		PLACING.remove(id);
		RuntimeSites.Placement placement = status.placed() ? status.placement() : null;

		if (placement == null) {
			settleTarget(p, null, kind);
			return;
		}

		BlockPos shrine = EchoShrines.settle(p.level(), placement, id);
		StoryData data = Story.get(p);
		data.chapter3 = ChapterThreeRules.withShrine(data.chapter3, shrine);
		data.counters.remove(ChapterThreeRules.PLACE_FAILURES);
		Story.commit(p, data);

		if (data.chapter3.stage() == ChapterThreeStage.FIRST_ECHO) {
			pointTo(p, shrine, kind);
		}
	}

	/** The target from the search (or, with none found, a shrine on the near rings), or a retry later when there is neither. */
	private static void settleTarget(ServerPlayer p, @Nullable BlockPos found, HolocronKind kind) {
		StoryData data = Story.get(p);

		if (data.chapter3.stage() != ChapterThreeStage.FIRST_ECHO) {
			return;
		}

		ChapterThreeBalance b = ChapterThreeService.balance();
		int failures = data.counter(ChapterThreeRules.PLACE_FAILURES);
		BlockPos target = ChapterThreeRules.firstEchoTarget(data, found, () -> placeShrine(p, kind, failures >= b.forwardFallbackAfter));

		if (target == null) {
			data.counters.merge(ChapterThreeRules.PLACE_FAILURES, 1, Integer::sum);
			Story.commit(p, data);
			ChapterThreeService.retryLater(p, "first_echo", b.retryTicks);
			OldRepublicMod.LOGGER.warn("No first-echo target for {} (no {} site within {} blocks, no shrine spot); retrying in {} ticks",
					p.getName().getString(), kind, b.firstEchoRadius, b.retryTicks);
			return;
		}

		data.counters.remove(ChapterThreeRules.PLACE_FAILURES);
		Story.commit(p, data);
		pointTo(p, target, kind);
	}

	/** The target is set: the Calling points to it and the player is told the way. */
	private static void pointTo(ServerPlayer p, BlockPos target, HolocronKind kind) {
		TARGETS.put(p.getUUID(), new Target(target, kind));
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.first_echo." + kind.name().toLowerCase(Locale.ROOT),
				FarSites.describe(p.blockPosition(), target)));
	}

	/** Where a site's pedestal of {@code kind} is: its anchor anywhere in the site, else the piece's centre. */
	static BlockPos pedestal(SitePiece piece, HolocronKind kind) {
		List<SiteAnchors.Anchor> anchors = switch (kind) {
			case JEDI -> List.of(SiteAnchors.Anchor.PEDESTAL_JEDI);
			case SITH -> List.of(SiteAnchors.Anchor.PEDESTAL_SITH);
			case WAYFARER -> List.of(SiteAnchors.Anchor.PEDESTAL_WAYFARER, SiteAnchors.Anchor.NICHE);
		};

		for (SiteAnchors.Anchor anchor : anchors) {
			BlockPos pos = StructureLocator.anchor(piece, anchor);

			if (pos != null) {
				return pos;
			}
		}

		return piece.box().getCenter();
	}

	/**
	 * An Echo Shrine of {@code kind} on the balance rings, or with {@code forward} the M2 forward
	 * fallback: a single ring {@code fallbackForward} blocks out (further for a shrine wider than
	 * that allows) with relaxed rules.
	 */
	private static @Nullable BlockPos placeShrine(ServerPlayer p, HolocronKind kind, boolean forward) {
		if (!forward) {
			return EchoShrines.place(p, kind);
		}

		StoryBalance sb = StoryService.balance();
		int[] ring = {Math.max(sb.fallbackForward, StorySites.halfSide(p.level(), EchoShrines.template(kind), SHRINE_FOOTPRINT / 2) + FORWARD_GAP)};
		MeetingSites.Rules rules = new MeetingSites.Rules(SHRINE_FOOTPRINT, SHRINE_CLEARANCE, sb.maxSlope + 1, Crossing.forbidden(p.level()), List.of(), 0);
		return EchoShrines.place(p, kind, ring, ring, rules);
	}

	// ---------------------------------------------------------------- the old-world guarantee

	/**
	 * Journal hold at LEADS or later: when the holocrons rank 3 (or the Pathfinder) needs are still
	 * missing and no unused site of the needed kind lies within {@code firstEchoRadius}, places one
	 * extra Echo Shrine of that kind (once per kind). An Independent needs any 3 holocrons, so every
	 * ruin, tomb and wreck counts (each holds a Wayfarer niche): the shrine comes only when all
	 * three searches find nothing.
	 */
	static void tickExtraShrine(ServerPlayer p) {
		StoryData data = Story.get(p);
		Path path = data.storyPath();
		ChapterThreeBalance b = ChapterThreeService.balance();
		boolean independent = path == Path.INDEPENDENT;
		HolocronKind kind = ChapterThreeRules.echoKind(path);
		int have = HolocronService.attunedCount(p, independent ? null : kind);
		int need = independent ? b.pathfinderHolocrons : b.rank3Holocrons;
		UUID id = p.getUUID();

		if (path == Path.NONE || EXTRA_SEARCHING.contains(id) || PLACING_EXTRA.containsKey(id) || !ChapterThreeRules.extraShrineNeeded(data, kind, have, need, b)
				|| !ChapterThreeService.due(p, "extra_shrine") || !StoryService.inOverworld(p)) {
			return;
		}

		ChapterThreeService.retryLater(p, "extra_shrine", b.retryTicks);
		EXTRA_SEARCHING.add(id);
		MinecraftServer server = p.level().getServer();
		List<SiteKind> kinds = ChapterThreeRules.echoSites(path);
		int[] pending = {kinds.size()};
		boolean[] unusedFound = {false};

		for (SiteKind siteKind : kinds) {
			StructureLocator.find(p, siteKind, b.firstEchoRadius, HolocronService.usedSites(p), piece -> {
				unusedFound[0] |= piece != null;

				if (--pending[0] > 0) {
					return;
				}

				EXTRA_SEARCHING.remove(id);
				ServerPlayer player = server.getPlayerList().getPlayer(id);

				if (player != null && !unusedFound[0]) {
					placeExtraShrine(player, kind);
				}
			});
		}
	}

	/**
	 * The old-world guarantee's shrine of {@code kind}: through far placement (polled by
	 * {@link #tickPlacing} while under way), the near rings when it fails; recorded once placed, and
	 * the player told the way. A near failure waits for the next search.
	 */
	private static void placeExtraShrine(ServerPlayer p, HolocronKind kind) {
		UUID id = p.getUUID();
		FarSites.Status status = EchoShrines.request(p, kind, StorySites.shrineKey(id, kind, true));

		if (StorySites.pending(status)) {
			PLACING_EXTRA.put(id, kind);
			return;
		}

		PLACING_EXTRA.remove(id);
		RuntimeSites.Placement placement = status.placed() ? status.placement() : null;
		BlockPos shrine = placement != null ? EchoShrines.settle(p.level(), placement, id) : EchoShrines.place(p, kind);

		if (shrine != null) {
			StoryData fresh = Story.get(p);
			ChapterThreeRules.recordExtraShrine(fresh, kind, shrine);
			Story.commit(p, fresh);
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.extra_shrine", FarSites.describe(p.blockPosition(), shrine)));
		}
	}

	/** Slow tick: the old-world guarantee's far shrine while its request runs. */
	static void tickPlacing(ServerPlayer p) {
		HolocronKind kind = PLACING_EXTRA.get(p.getUUID());

		if (kind != null) {
			placeExtraShrine(p, kind);
		}
	}

	/** The newest of the player's Echo Shrines whose pedestal has not given them a holocron yet, or null. */
	static @Nullable BlockPos unusedShrine(ServerPlayer p) {
		StoryData data = Story.get(p);
		List<BlockPos> shrines = data.chapter3.shrines();

		for (int i = shrines.size() - 1; i >= 0; i--) {
			BlockPos shrine = shrines.get(i);
			SitePiece piece = StructureLocator.pieceAt(p.level(), shrine);

			if (piece == null || !HolocronService.usedSites(p).test(piece)) {
				return shrine;
			}
		}

		return null;
	}

	static void forget(ServerPlayer p) {
		TARGETS.remove(p.getUUID());
		SEARCHING.remove(p.getUUID());
		EXTRA_SEARCHING.remove(p.getUUID());
		PLACING.remove(p.getUUID());
		PLACING_EXTRA.remove(p.getUUID());
	}

	static void clear() {
		TARGETS.clear();
		SEARCHING.clear();
		EXTRA_SEARCHING.clear();
		PLACING.clear();
		PLACING_EXTRA.clear();
	}

	private FirstEcho() {
	}
}
