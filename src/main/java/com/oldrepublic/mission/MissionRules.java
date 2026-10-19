package com.oldrepublic.mission;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Path;
import com.oldrepublic.quest.story.CaptainFate;
import com.oldrepublic.world.site.SiteKind;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The pure rules of the mission feature (spec 5.4, 5.5, 5.7): board offers seeded by day, player
 * and terminal; used offers; the soft cap; the contract XP formula; own-side counting; tracker
 * bands; the R2 chief; the Jedi Order share; duties; escorts. Every daily rule takes the day as an
 * argument, so tests never touch world time.
 */
public final class MissionRules {
	/** Tracker distance bands. Synced by ordinal in {@code tracker_hint}; append only. */
	public enum Band {
		/** Farther than {@code missions.trackerNear}. */
		FAR,
		/** Between {@code trackerHere} and {@code trackerNear}. */
		NEAR,
		/** Closer than {@code trackerHere}. */
		HERE,
		/** No target in this dimension. */
		NONE
	}

	/** Who runs the R2 smuggler cache, by Hessa Dray's Chapter Two fate (5.4). */
	public enum Chief {
		/** Rell Okkar, as written: yields at the captain's fraction. */
		RELL,
		/** Hessa Dray, spared in Chapter Two: yields at once and hands over the record. */
		HESSA,
		/** Hessa's lieutenant, after her execution: fights to the end. */
		LIEUTENANT
	}

	/** One board offer: its index in the day's seeded order (its identity in {@code used_offers}) and the contract type. */
	public record Offer(int index, String type) {
	}

	/**
	 * One of the four story missions, for the Missions tab and the officers (5.4).
	 *
	 * @param army     the giver's army
	 * @param requires the story mission done first, or null
	 */
	public record StoryMission(String id, Faction army, @Nullable String requires) {
	}

	/** The story missions in the order the Missions tab lists them. */
	public static final List<StoryMission> STORY_MISSIONS = List.of(
			new StoryMission("r1_silent_patrol", Faction.REPUBLIC, null),
			new StoryMission("r2_credits_from_the_core", Faction.REPUBLIC, "r1_silent_patrol"),
			new StoryMission("i1_names", Faction.SITH_EMPIRE, null),
			new StoryMission("i2_border_incident", Faction.SITH_EMPIRE, "i1_names"));
	/** The worldgen sites a survey or secure contract works at. */
	public static final List<SiteKind> WORLDGEN_KINDS = List.of(SiteKind.JEDI_RUIN, SiteKind.SITH_TOMB, SiteKind.WRECK_FIELD);
	/** The Jedi mentor duty. */
	public static final String WATCH_DUTY = "watch_duty";
	/** The Sith mentor duty. */
	public static final String TRIBUTE = "tribute";

	/** The contract types a bounty terminal can offer, in definition order. */
	public static final List<String> BOARD_TYPES = List.of("clear_camp", "survey", "secure_site", "patrol_intercept");
	/** The contract types a mentor duty draws from. */
	public static final List<String> DUTY_TYPES = List.of("clear_camp", "survey", "secure_site");
	/** The contract type issued by the Frontier at either terminal. */
	public static final String FRONTIER_TYPE = "clear_camp";
	/** Escorts walk at least this long before leaving the owner's sight removes them. */
	static final int ESCORT_MIN_WALK_TICKS = 40;

	/** The seed of a terminal's offers for one player and day. */
	public static long boardSeed(int day, UUID player, BlockPos terminal) {
		long seed = player.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ player.getLeastSignificantBits();
		seed ^= terminal.asLong() * 0xC2B2AE3D27D4EB4FL;
		return seed ^ (long) day * 0x165667B19E3779F9L;
	}

	/**
	 * The seed of a terminal's offers in {@code dimension}: the home world's is {@link #boardSeed(int, UUID, BlockPos)}
	 * unchanged; a planet's terminal at the same position rolls its own order (8.7).
	 */
	public static long boardSeed(int day, UUID player, BlockPos terminal, ResourceKey<Level> dimension) {
		long seed = boardSeed(day, player, terminal);
		return dimension == Level.OVERWORLD ? seed : seed ^ (long) dimension.identifier().hashCode() * 0x27BB2EE687B0B0FDL;
	}

	/** {@link #offers} for a terminal in {@code dimension} (a planet hub's contract terminal). */
	public static List<Offer> offers(int day, UUID player, BlockPos terminal, ResourceKey<Level> dimension, List<String> types, Predicate<String> available,
			IntPredicate used, int count) {
		List<String> order = shuffled(boardSeed(day, player, terminal, dimension), types);
		List<Offer> offers = new ArrayList<>(count);

		for (int index = 0; index < order.size() && offers.size() < count; index++) {
			if (used.test(index) || available.test(order.get(index))) {
				offers.add(new Offer(index, order.get(index)));
			}
		}

		return offers;
	}

	/** {@code types} in a seeded order (Fisher-Yates); the same seed always gives the same order. */
	public static List<String> shuffled(long seed, List<String> types) {
		List<String> order = new ArrayList<>(types);
		SplittableRandom random = new SplittableRandom(seed);

		for (int i = order.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			String swap = order.get(i);
			order.set(i, order.get(j));
			order.set(j, swap);
		}

		return order;
	}

	/**
	 * The day's offers of a terminal for a player: the first {@code count} entries of the seeded
	 * order whose site exists ({@code available}) or that were already used today ({@code used}), so a
	 * used offer keeps its place and is never replaced or offered again that day.
	 */
	public static List<Offer> offers(int day, UUID player, BlockPos terminal, List<String> types, Predicate<String> available, IntPredicate used,
			int count) {
		List<String> order = shuffled(boardSeed(day, player, terminal), types);
		List<Offer> offers = new ArrayList<>(count);

		for (int index = 0; index < order.size() && offers.size() < count; index++) {
			if (used.test(index) || available.test(order.get(index))) {
				offers.add(new Offer(index, order.get(index)));
			}
		}

		return offers;
	}

	/** Which of {@code variants} briefings an offer or mission shows, from its seed. */
	public static int briefingVariant(long seed, int variants) {
		return variants <= 1 ? 0 : (int) Math.floorMod(seed ^ (seed >>> 29), (long) variants);
	}

	/** Starts a new day's board and contract counts when {@code day} differs from the stored ones. */
	public static void rollDay(MissionData d, int day) {
		if (d.boardDay != day) {
			d.boardDay = day;
			d.usedOffers.clear();
		}

		if (d.contractDay != day) {
			d.contractDay = day;
			d.contractsToday = 0;
		}
	}

	/** Whether the offer was accepted, completed or abandoned today (call {@link #rollDay} first). */
	public static boolean offerUsed(MissionData d, BlockPos terminal, int index) {
		return offerUsed(d, terminal, Level.OVERWORLD, index);
	}

	/** {@link #offerUsed(MissionData, BlockPos, int)} for a terminal in {@code dimension}. */
	public static boolean offerUsed(MissionData d, BlockPos terminal, ResourceKey<Level> dimension, int index) {
		return d.usedOffers.contains(new MissionData.UsedOffer(terminal, index, dimension));
	}

	/** Marks an offer used today. False when it already was or {@code max} offers are remembered. */
	public static boolean useOffer(MissionData d, BlockPos terminal, int index, int max) {
		return useOffer(d, terminal, Level.OVERWORLD, index, max);
	}

	/** {@link #useOffer(MissionData, BlockPos, int, int)} for a terminal in {@code dimension}. */
	public static boolean useOffer(MissionData d, BlockPos terminal, ResourceKey<Level> dimension, int index, int max) {
		MissionData.UsedOffer used = new MissionData.UsedOffer(terminal.immutable(), index, dimension);

		if (d.usedOffers.contains(used) || d.usedOffers.size() >= Math.min(max, MissionData.MAX_USED_OFFERS)) {
			return false;
		}

		d.usedOffers.add(used);
		return true;
	}

	/** Whether the next contract completed today pays in full (call {@link #rollDay} first). */
	public static boolean fullPay(MissionData d, MissionBalance b) {
		return d.contractsToday < b.fullPayPerDay;
	}

	/** Contract XP: min(cap, base + per level x level). */
	public static int contractXp(int level, MissionBalance b) {
		return Math.min(b.contractXpCap, b.contractXpBase + b.contractXpPerLevel * Math.max(0, level));
	}

	/** XP actually paid: in full, or times {@code overCapMultiplier} past the soft cap. */
	public static int paidXp(int xp, boolean fullPay, MissionBalance b) {
		return fullPay ? xp : (int) Math.round(xp * b.overCapMultiplier);
	}

	/** The own-side key a completed contract counts under: the issuer's faction id, or {@code mentor} for duties. */
	public static String ownSideKey(MissionDefinition.Kind kind, String issuer) {
		return kind == MissionDefinition.Kind.DUTY ? MissionData.OWN_SIDE_MENTOR : issuer;
	}

	/** Whether contracts of {@code key} count toward rank 3 for {@code path} (5.5). */
	public static boolean countsAsOwnSide(Path path, String key) {
		return switch (path) {
			case JEDI -> key.equals(Faction.REPUBLIC.id()) || key.equals(MissionData.OWN_SIDE_MENTOR);
			case SITH -> key.equals(Faction.SITH_EMPIRE.id()) || key.equals(MissionData.OWN_SIDE_MENTOR);
			case INDEPENDENT -> true;
			case NONE -> false;
		};
	}

	/** Own-side contracts toward rank 3: Jedi Republic and Jedi duties, Sith Imperial and Sith duties, Independent any. */
	public static int ownSideCount(Path path, Map<String, Integer> ownSide) {
		int total = 0;

		for (Map.Entry<String, Integer> entry : ownSide.entrySet()) {
			if (countsAsOwnSide(path, entry.getKey())) {
				total += entry.getValue();
			}
		}

		return total;
	}

	/** The tracker band for a horizontal distance in blocks. */
	public static Band band(double distance, MissionBalance b) {
		if (distance < b.trackerHere) {
			return Band.HERE;
		}

		return distance <= b.trackerNear ? Band.NEAR : Band.FAR;
	}

	/** The yaw (degrees, Minecraft convention: 0 faces +Z) that looks from {@code from} toward {@code to}. */
	public static float yaw(Vec3 from, Vec3 to) {
		return Mth.wrapDegrees((float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90f);
	}

	/** The R2 chief: Hessa if spared, her lieutenant if executed, otherwise Rell Okkar. */
	public static Chief r2Chief(CaptainFate fate) {
		return switch (fate) {
			case SPARED -> Chief.HESSA;
			case EXECUTED -> Chief.LIEUTENANT;
			default -> Chief.RELL;
		};
	}

	/** Jedi Order standing paid alongside a Republic gain from Republic work: half of it for a Jedi, none otherwise. */
	public static int jediShare(Path path, int republicGain, MissionBalance b) {
		return path == Path.JEDI && republicGain > 0 ? (int) Math.floor(republicGain * b.jediOrderShare) : 0;
	}

	/**
	 * Whether a mentor duty is still open on {@code day}: fewer than {@code perDay}
	 * ({@code missions.dutiesPerDay}) taken that day. A duty day that is set counts at least one.
	 */
	public static boolean dutyOpen(MissionData d, int day, int perDay) {
		return d.dutyDay != day || dutiesOn(d, day) < perDay;
	}

	/** Mentor duties taken on {@code day} (a set duty day counts at least one). */
	public static int dutiesOn(MissionData d, int day) {
		return d.dutyDay != day ? 0 : Math.max(1, d.dutiesToday);
	}

	/** The contract types today's duty tries, in order: the first whose site exists is used. */
	public static List<String> dutyOrder(int day, UUID player) {
		return shuffled(boardSeed(day, player, BlockPos.ZERO) ^ 0x5DEECE66DL, DUTY_TYPES);
	}

	/**
	 * The issuer part of a briefing key ({@code mission.oldrepublic.contract.<type>.<issuer>.<n>}):
	 * {@code republic}, {@code imperial}, {@code frontier}, or {@code jedi} for the Jedi Order; a later
	 * faction uses its id ({@code vessba_kajidic}, {@code clan_hask}, spec MILESTONE_5.md 7.2).
	 */
	public static String issuerKey(Faction issuer) {
		return switch (issuer) {
			case REPUBLIC -> "republic";
			case SITH_EMPIRE -> "imperial";
			case FRONTIER -> "frontier";
			case JEDI_ORDER -> "jedi";
			default -> issuer.id();
		};
	}

	/** Lang key of a contract briefing: the base type, who frames it ({@link #issuerKey}, or {@code jedi} / {@code sith} for duties) and the variant. */
	public static String briefingKey(String type, String issuerKey, int variant) {
		return "mission.oldrepublic.contract." + type + "." + issuerKey + "." + variant;
	}

	/** The duty a mentor gives on {@code path}: {@code watch_duty} for a Jedi, {@code tribute} for a Sith, null otherwise. */
	public static @Nullable String dutyFor(Path path) {
		return switch (path) {
			case JEDI -> WATCH_DUTY;
			case SITH -> TRIBUTE;
			default -> null;
		};
	}

	/** The first type of today's duty order that the duty allows. */
	public static @Nullable String firstDutyType(List<String> allowed, List<String> order) {
		for (String type : order) {
			if (allowed.contains(type)) {
				return type;
			}
		}

		return null;
	}

	/** The type after {@code current} in today's duty order that the duty allows, or null when it was the last. */
	public static @Nullable String nextDutyType(List<String> allowed, List<String> order, String current) {
		int from = order.indexOf(current);

		for (int i = from + 1; i < order.size(); i++) {
			if (allowed.contains(order.get(i))) {
				return order.get(i);
			}
		}

		return null;
	}

	/**
	 * Lang key of the objective line for a mission's base type at a stage: shared lines while
	 * placing and travelling, {@code mission.oldrepublic.<base>.objective.<stage>} at the site.
	 */
	public static String objectiveKey(String base, MissionData.Stage stage) {
		return switch (stage) {
			case PLACING -> "mission.oldrepublic.stage.placing";
			case TRAVEL -> "mission.oldrepublic.stage.travel";
			default -> "mission.oldrepublic." + base + ".objective." + stage.name().toLowerCase(Locale.ROOT);
		};
	}

	/** Whether an escort group (and its prisoner) should be removed: out of the owner's sight after a short walk, or out of time. */
	public static boolean escortGone(long age, boolean seenByOwner, MissionBalance b) {
		return age >= b.escortDespawnTicks || (!seenByOwner && age >= ESCORT_MIN_WALK_TICKS);
	}

	private MissionRules() {
	}
}
