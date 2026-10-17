package com.oldrepublic.world;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.Gson;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.quest.story.StoryService;
import com.oldrepublic.world.site.PlacementRules;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SiteSpawnerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Echo Shrines (spec 3.4 and 6.3): a small half-buried shrine of one holocron kind, placed for its
 * owner when no worldgen site of that kind is near. Signatures are frozen (spec 15.1).
 *
 * <p>A shrine is the template {@code site/echo_shrine_<kind>} (15×14×15), placed whole on the
 * surface with its lowest {@value #BURIED} layers underground, its entrance turned toward the owner,
 * and recorded in {@code WorldSites} with its owner. Its guard is the owner's encounter (3.6, 12.3):
 * the {@code GUARD} spawner waits for the owner and spawns the guard owned by them.
 */
public final class EchoShrines {
	/** The template's lowest layers set into the ground: the chamber floor lies 8 below the surface. */
	public static final int BURIED = 9;
	/** Side of the square of natural ground the shrine's surface ruin needs. */
	public static final int FOOTPRINT = 13;
	/** Side of the square that must hold no built block and no worldgen piece. */
	public static final int CLEARANCE = 17;
	/** Allowed height difference across the footprint. */
	public static final int MAX_SLOPE = 3;

	private static final Gson GSON = new Gson();

	/**
	 * W1's view of balance section {@code chapter_three} (W2's): only the two shrine ring lists, read
	 * by name because W1 merges before W2 and never depends on W2's class.
	 */
	private static final class ShrineRings {
		int[] shrineRings = {96, 128};
		int[] shrineFallback = {48, 80};
	}

	/**
	 * Places an Echo Shrine of {@code kind} for {@code owner} on the balance rings and returns its
	 * pedestal position, or null when no spot was found (the caller retries later). Chapter Three
	 * sites are overworld only (6.9, 12.2): in another dimension it returns null.
	 */
	public static @Nullable BlockPos place(ServerPlayer owner, HolocronKind kind) {
		if (owner.level().dimension() != Level.OVERWORLD) {
			return null;
		}

		ShrineRings rings = shrineRings();
		return place(owner, kind, rings.shrineRings, rings.shrineFallback, rules(owner));
	}

	/**
	 * {@link #place(ServerPlayer, HolocronKind)} with injected rings, fallback rings and rules (the
	 * test seam): {@link #findSpot}, then terrain adaptation, the stamp and the {@code WorldSites}
	 * record ({@code RuntimeSites.placeSurface}), then the guard spawner is given its owner.
	 */
	public static @Nullable BlockPos place(ServerPlayer owner, HolocronKind kind, int[] rings, int[] fallback, MeetingSites.Rules rules) {
		RuntimeSites.Placement placement = RuntimeSites.placeSurface(owner.level(), template(kind), owner.position(), owner.getLookAngle(), rings, fallback,
				rules, BURIED, owner.getUUID());

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
		return RuntimeSites.findSurface(owner.level(), template(kind), owner.position(), owner.getLookAngle(), rings, fallback, rules, BURIED);
	}

	/**
	 * The shrine rings of section {@code chapter_three}, read without replacing W2's instance in
	 * {@code BalanceConfig}'s cache: the cache keeps one object per section and parses a new one when
	 * asked for another class, which would drop any change made to W2's. Asking for {@code Object}
	 * matches whatever is cached (W2's instance, once W2 has read it), and the two lists are copied
	 * out of it through its JSON form.
	 */
	private static ShrineRings shrineRings() {
		Object section = BalanceConfig.section("chapter_three", Object.class, ShrineRings::new);
		ShrineRings rings = GSON.fromJson(GSON.toJsonTree(section), ShrineRings.class);
		return rings != null ? rings : new ShrineRings();
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

	private EchoShrines() {
	}
}
