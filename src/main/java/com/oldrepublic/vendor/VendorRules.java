package com.oldrepublic.vendor;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.ReputationTier;

/**
 * The pure rules of vendors and credits (spec MILESTONE_4.md 7.7, 7.8): prices, the sell rate,
 * gates, daily limits, contract credits and back pay (commission fees are W5's
 * {@code CraftingRules}). No game state: callers pass what they read, so the game tests check
 * every rule directly.
 */
public final class VendorRules {
	/** Lang prefix of every refusal and lock reason. */
	public static final String REASON = "vendor.oldrepublic.locked.";

	/**
	 * What one line costs: {@code base x priceMultiplier}, times {@code honouredDiscount} at Honoured
	 * with the vendor's faction. A free line stays free; a priced line never rounds down to nothing.
	 */
	public static int price(int base, boolean honoured, VendorBalance b) {
		if (base <= 0) {
			return 0;
		}

		double price = base * Math.max(0, b.priceMultiplier) * (honoured ? b.honouredDiscount : 1.0);
		return Math.max(1, (int) Math.round(price));
	}

	/** What a vendor pays for one item whose buy price (per item) is {@code buyPrice}: the sell rate of it, at least 1 when it has a price. */
	public static int sellPrice(int buyPrice, VendorBalance b) {
		if (buyPrice <= 0) {
			return 0;
		}

		return Math.max(1, (int) Math.floor(buyPrice * Math.max(0, b.sellRate)));
	}

	/** Credits a planet or outpost contract pays at full pay (7.7): {@code base + perLevel x level}. */
	public static int contractCredits(int level, VendorBalance b) {
		return Math.max(0, b.contractCreditsBase + b.contractCreditsPerLevel * Math.max(0, level));
	}

	/**
	 * Whether a faction vendor turns the player away (3.5, 7.8): below Neutral with its faction, or of
	 * the opposing path at the other side's hub (a Sith at the Greenwatch, a Jedi at the Proving). A
	 * vendor without a faction (trainers, the spacer) serves everyone. Every other faction, such as the
	 * Vessba kajidic and Clan Hask, refuses only below Neutral (spec MILESTONE_5.md 7).
	 */
	public static boolean refuses(@Nullable Faction vendorFaction, ReputationTier tier, Path path) {
		if (vendorFaction == null) {
			return false;
		}

		if (tier.ordinal() < ReputationTier.NEUTRAL.ordinal()) {
			return true;
		}

		return switch (vendorFaction) {
			case JEDI_ORDER -> path == Path.SITH;
			case SITH_EMPIRE -> path == Path.JEDI;
			default -> false;
		};
	}

	/**
	 * Why a line is locked for the player, as an encoded reason ({@code key|arg|@nested}), or null when
	 * it can be bought now.
	 *
	 * @param faction         the faction gating the line (its own, else the vendor's), or null
	 * @param tier            the player's tier with {@code faction} (ignored without one)
	 * @param disciplineLevel the player's level in the line's discipline (0 without it), ignored when the line needs none
	 * @param boughtToday     how many of this line the player bought today
	 */
	public static @Nullable String lockReason(VendorEntry entry, @Nullable Faction faction, ReputationTier tier, int disciplineLevel, int boughtToday) {
		if (faction != null && entry.tier() > 0 && tier.ordinal() < entry.tier()) {
			ReputationTier needed = ReputationTier.values()[Math.min(entry.tier(), ReputationTier.values().length - 1)];
			return REASON + "tier|@faction.oldrepublic.tier." + needed.name().toLowerCase(java.util.Locale.ROOT) + "|@faction.oldrepublic." + faction.id();
		}

		if (entry.discipline().isPresent() && disciplineLevel < Math.max(1, entry.level())) {
			return REASON + "discipline|@discipline.oldrepublic." + entry.discipline().get().id() + "|" + Math.max(1, entry.level());
		}

		if (dailyLeft(entry, boughtToday) == 0) {
			return REASON + "sold_out";
		}

		return null;
	}

	/** Purchases of the line still allowed today; -1 for no limit. */
	public static int dailyLeft(VendorEntry entry, int boughtToday) {
		return entry.dailyLimit() <= 0 ? -1 : Math.max(0, entry.dailyLimit() - Math.max(0, boughtToday));
	}

	/**
	 * How many of a line a purchase of {@code requested} may buy: at least 1, at most
	 * {@code maxPurchase}, and within the daily limit.
	 */
	public static int allowedCount(VendorEntry entry, int requested, int boughtToday, VendorBalance b) {
		int count = Math.max(1, Math.min(requested, Math.max(1, b.maxPurchase)));
		int left = dailyLeft(entry, boughtToday);
		return left < 0 ? count : Math.min(count, left);
	}

	/** The M3 back pay owed for {@code milestones} milestones (8.8): per milestone, capped. */
	public static int backPay(int milestones, VendorBalance b) {
		return Math.max(0, Math.min(b.backPayMax, Math.max(0, milestones) * b.backPayPerMilestone));
	}

	/** How many chits an enemy drop holds for a roll in [0, 1): uniformly within {@code creditDrop}. */
	public static int creditDrop(double roll, VendorBalance b) {
		int min = b.creditDrop.length > 0 ? Math.max(0, b.creditDrop[0]) : 1;
		int max = b.creditDrop.length > 1 ? Math.max(min, b.creditDrop[1]) : min;
		return min + (int) Math.floor(Math.max(0, Math.min(0.999999, roll)) * (max - min + 1));
	}

	private VendorRules() {
	}
}
