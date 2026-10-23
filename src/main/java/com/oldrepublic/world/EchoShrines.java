package com.oldrepublic.world;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.quest.story.ChapterThreeBalance;
import com.oldrepublic.quest.story.ChapterThreeService;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.quest.story.StoryService;
import com.oldrepublic.quest.story.StorySites;
import com.oldrepublic.world.site.PlacementRules;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SiteSpawnerBlockEntity;
import com.oldrepublic.world.site.far.FarSites;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Echo Shrines (spec 3.4 and 6.3): a half-buried shrine of one holocron kind, placed for its owner
 * when no worldgen site of that kind is near. Signatures are frozen (spec 15.1).
 *
 * <p>A shrine is the template {@code site/echo_shrine_<kind>}, placed whole on the surface with its
 * lowest layers underground (its site meta's {@code buried}, else {@value #BURIED}), its entrance
 * turned toward the owner, and recorded in {@code WorldSites} with its owner. Since Milestone 6
 * (docs/MILESTONE_6.md 3.2) the story asks {@link FarSites} for it first, {@code shrineRange}
 * blocks away ({@link #request}); the rings of {@link #place(ServerPlayer, HolocronKind)} are the
 * near search for when that fails. Its guard is the owner's encounter (3.6, 12.3): the {@code GUARD}
 * spawner waits for the owner and spawns the guard owned by them.
 */
public final class EchoShrines {
	/** The template's lowest layers set into the ground when it has no site meta: the chamber floor lies 8 below the surface. */
	public static final int BURIED = 9;
	/** Side of the square of natural ground the shrine's surface ruin needs (without site meta). */
	public static final int FOOTPRINT = 13;
	/** Side of the square that must hold no built block and no worldgen piece (without site meta). */
	public static final int CLEARANCE = 17;
	/** Allowed height difference across the footprint (without site meta). */
	public static final int MAX_SLOPE = 3;

	/**
	 * Asks far placement for {@code owner}'s shrine of {@code kind} under {@code key}
	 * (docs/MILESTONE_6.md 3.2): {@code chapter_three.shrineRange} blocks from the owner, its
	 * entrance toward them, {@code story.siteMinSeparation} from other players' sites. Idempotent by
	 * key: call it again to poll. Chapter Three sites are overworld only (6.9, 12.2): in another
	 * dimension the answer is FAILED ({@code not_overworld}) and nothing is asked. A placed shrine is
	 * recorded by far placement; {@link #settle} gives its guard to the owner.
	 */
	public static FarSites.Status request(ServerPlayer owner, HolocronKind kind, String key) {
		if (owner.level().dimension() != Level.OVERWORLD) {
			return new FarSites.Status(FarSites.Phase.FAILED, null, null, "not_overworld");
		}

		ChapterThreeBalance b = ChapterThreeService.balance();
		return FarSites.request(owner.level(), StorySites.request(key, owner.getUUID(), template(kind), owner.blockPosition(), b.shrineRange)
				.avoiding(PlacementRules.otherPlayersSites(owner), StoryService.balance().siteMinSeparation));
	}

	/**
	 * A shrine far placement placed for {@code owner}: its {@code GUARD} spawner made theirs, and its
	 * pedestal returned (the stand it was placed on when the sidecar has no {@code PEDESTAL}).
	 */
	public static BlockPos settle(ServerLevel level, RuntimeSites.Placement placement, UUID owner) {
		ownGuard(level, placement.piece(), owner);
		BlockPos pedestal = SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.PEDESTAL);
		return pedestal != null ? pedestal : placement.origin();
	}

	/**
	 * Places an Echo Shrine of {@code kind} for {@code owner} on the balance rings (the near search)
	 * with {@link #rules(ServerPlayer, HolocronKind)} and returns its pedestal position, or null when
	 * no spot was found (the caller retries later). Chapter Three sites are overworld only (6.9,
	 * 12.2): in another dimension it returns null.
	 */
	public static @Nullable BlockPos place(ServerPlayer owner, HolocronKind kind) {
		if (owner.level().dimension() != Level.OVERWORLD) {
			return null;
		}

		ChapterThreeBalance rings = ChapterThreeService.balance();
		return place(owner, kind, rings.shrineRings, rings.shrineFallback, rules(owner, kind));
	}

	/**
	 * {@link #place(ServerPlayer, HolocronKind)} with injected rings, fallback rings and rules (the
	 * test seam): {@link #findSpot}, then terrain adaptation, the stamp and the {@code WorldSites}
	 * record ({@code RuntimeSites.placeSurface}), then the guard spawner is given its owner.
	 */
	public static @Nullable BlockPos place(ServerPlayer owner, HolocronKind kind, int[] rings, int[] fallback, MeetingSites.Rules rules) {
		RuntimeSites.Placement placement = RuntimeSites.placeSurface(owner.level(), template(kind), owner.position(), owner.getLookAngle(), rings, fallback,
				rules, buried(kind), owner.getUUID());

		if (placement == null) {
			return null;
		}

		ownGuard(owner.level(), placement.piece(), owner.getUUID());
		return SiteAnchors.find(placement.piece(), SiteAnchors.Anchor.PEDESTAL);
	}

	/** Makes the shrine's {@code GUARD} spawner an owned encounter of {@code owner} (3.6, 12.3). */
	private static void ownGuard(ServerLevel level, SitePiece shrine, UUID owner) {
		BlockPos guard = SiteAnchors.find(shrine, SiteAnchors.Anchor.GUARD);

		if (guard != null && level.getBlockEntity(guard) instanceof SiteSpawnerBlockEntity spawner) {
			spawner.setOwner(owner);
		}
	}

	/**
	 * The search of the 5-argument {@link #place} without placing or recording anything (a test
	 * seam): the best spot on {@code rings} around the owner, else on {@code fallback}, or null.
	 */
	public static RuntimeSites.@Nullable Spot findSpot(ServerPlayer owner, HolocronKind kind, int[] rings, int[] fallback, MeetingSites.Rules rules) {
		return RuntimeSites.findSurface(owner.level(), template(kind), owner.position(), owner.getLookAngle(), rings, fallback, rules, buried(kind));
	}

	/** The layers the shrine of {@code kind} sets into the ground: its site meta's {@code buried}, else {@value #BURIED}. */
	public static int buried(HolocronKind kind) {
		return StorySites.buried(template(kind), BURIED);
	}

	/** The shrine template of a holocron kind: {@code oldrepublic:site/echo_shrine_<kind>}. */
	public static Identifier template(HolocronKind kind) {
		return OldRepublicMod.id("site/echo_shrine_" + kind.name().toLowerCase(Locale.ROOT));
	}

	/**
	 * The live placement rules for {@code owner}'s shrine: the footprint, clearance and slope above,
	 * spawn protection, and {@code story.siteMinSeparation} from other players' sites (3.6).
	 */
	public static MeetingSites.Rules rules(ServerPlayer owner) {
		return new MeetingSites.Rules(FOOTPRINT, CLEARANCE, MAX_SLOPE, PlacementRules.spawnProtection(owner.level(), FOOTPRINT),
				PlacementRules.otherPlayersSites(owner), StoryService.balance().siteMinSeparation);
	}

	/**
	 * The near search's rules for {@code owner}'s shrine of {@code kind} (docs/MILESTONE_6.md 3.4):
	 * the template's larger side, its site meta's clearance and slope, spawn protection for that
	 * size and the separation of {@link #rules(ServerPlayer)}; without site meta, exactly
	 * {@link #rules(ServerPlayer)}.
	 */
	public static MeetingSites.Rules rules(ServerPlayer owner, HolocronKind kind) {
		ServerLevel level = owner.level();
		int footprint = Math.max(FOOTPRINT, 2 * StorySites.halfSide(level, template(kind), FOOTPRINT / 2) + 1);
		return StorySites.nearRules(level, template(kind), FOOTPRINT, CLEARANCE, MAX_SLOPE, PlacementRules.spawnProtection(level, footprint),
				PlacementRules.otherPlayersSites(owner), StoryService.balance().siteMinSeparation);
	}

	private EchoShrines() {
	}
}
