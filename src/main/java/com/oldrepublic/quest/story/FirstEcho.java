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
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The first echo (spec 6.3) and the old-world guarantee (4.3). FIRST_ECHO picks a target: the
 * nearest unused worldgen site of the path's kind within {@code firstEchoRadius} (an Independent
 * takes the nearest wreck, ruin or tomb), else an Echo Shrine placed for the player. The journal's
 * Calling points to it ({@link StoryGuidance}); any pedestal of the right kind ends the stage. Later,
 * a player whose rank 3 holocrons are still missing and who has no unused chamber nearby gets one
 * extra shrine per needed kind.
 *
 * <p>Targets found by the locator are kept in memory (a restart searches again); shrines are saved
 * in {@link StoryData.Chapter3#shrines}.
 */
final class FirstEcho {
	/** A pedestal the Calling points to, and the kind it holds (the Calling's tint). */
	record Target(BlockPos pos, HolocronKind kind) {
	}

	private static final Map<UUID, Target> TARGETS = new HashMap<>();
	private static final Set<UUID> SEARCHING = new HashSet<>();
	/** The extra shrine for the old-world guarantee, while its search runs, per player. */
	private static final Set<UUID> EXTRA_SEARCHING = new HashSet<>();
	/** Relaxed site rules for the M2 forward fallback shrine (a single ring {@code fallbackForward} out). */
	private static final int SHRINE_FOOTPRINT = 7;
	private static final int SHRINE_CLEARANCE = 11;

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

	/** Slow tick at FIRST_ECHO: after a restart the saved shrine is the target; a failed search retries. */
	static void tick(ServerPlayer p) {
		UUID id = p.getUUID();

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
		Journal.unlockEntry(p, JournalEntries.FIRST_ECHO);
		Journal.unlockCodex(p, JournalEntries.CODEX_THE_COLLECTORS_TRAIL);
		ChapterThreeService.enterStage(p, ChapterThreeStage.LEADS);
	}

	/** Where the Calling points during FIRST_ECHO, or null while the search runs. */
	static @Nullable Target target(ServerPlayer p) {
		return TARGETS.get(p.getUUID());
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

	/** Every search answered: the nearest pedestal found, else a shrine; neither retries later. */
	private static void onSearched(ServerPlayer p, List<SitePiece> found, HolocronKind kind) {
		StoryData data = Story.get(p);

		if (data.chapter3.stage() != ChapterThreeStage.FIRST_ECHO) {
			return;
		}

		ChapterThreeBalance b = ChapterThreeService.balance();
		BlockPos nearest = null;

		for (SitePiece piece : found) {
			BlockPos pedestal = pedestal(piece, kind);

			if (nearest == null || p.blockPosition().distSqr(pedestal) < p.blockPosition().distSqr(nearest)) {
				nearest = pedestal;
			}
		}

		int failures = data.counter(ChapterThreeRules.PLACE_FAILURES);
		BlockPos target = ChapterThreeRules.firstEchoTarget(data, nearest, () -> placeShrine(p, kind, failures >= b.forwardFallbackAfter));

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
		TARGETS.put(p.getUUID(), new Target(target, kind));
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.first_echo." + kind.name().toLowerCase(Locale.ROOT)));
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
	 * fallback: a single ring {@code fallbackForward} blocks out with relaxed rules.
	 */
	private static @Nullable BlockPos placeShrine(ServerPlayer p, HolocronKind kind, boolean forward) {
		if (!forward) {
			return EchoShrines.place(p, kind);
		}

		StoryBalance sb = StoryService.balance();
		int[] ring = {sb.fallbackForward};
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

		if (path == Path.NONE || EXTRA_SEARCHING.contains(id) || !ChapterThreeRules.extraShrineNeeded(data, kind, have, need, b)
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

	private static void placeExtraShrine(ServerPlayer p, HolocronKind kind) {
		BlockPos shrine = EchoShrines.place(p, kind);

		if (shrine != null) {
			StoryData fresh = Story.get(p);
			ChapterThreeRules.recordExtraShrine(fresh, kind, shrine);
			Story.commit(p, fresh);
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c3.extra_shrine"));
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
	}

	static void clear() {
		TARGETS.clear();
		SEARCHING.clear();
		EXTRA_SEARCHING.clear();
	}

	private FirstEcho() {
	}
}
