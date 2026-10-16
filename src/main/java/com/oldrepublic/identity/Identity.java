package com.oldrepublic.identity;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The player's identity: alignment, faction reputation, path, rank, mentor trust and recorded
 * choices. Readers work on both sides (the attachment is synced to its owner); writers are server
 * only and validate, cap, sync and give feedback in one place.
 */
public final class Identity {
	/**
	 * Choice id another feature records (for example on arrival at the Crossing) to start showing
	 * alignment and reputation toasts before a path is chosen. Once the player has a path they show
	 * anyway; before either, changes are recorded silently so mechanics are introduced gradually.
	 */
	public static final String INTRODUCED = "identity:introduced";

	/** Server-side balance values of the identity feature. */
	public static IdentityBalance balance() {
		return BalanceConfig.section("identity", IdentityBalance.class, IdentityBalance::new);
	}

	// ---------------------------------------------------------------- data access

	/** The live server-side data. Call {@link #commit} after changing it. */
	public static IdentityData get(ServerPlayer player) {
		return player.getAttachedOrCreate(IdentityAttachments.IDENTITY);
	}

	/** Stores (and so syncs) the data. */
	public static void commit(ServerPlayer player, IdentityData data) {
		player.setAttached(IdentityAttachments.IDENTITY, data.copy().seal());
	}

	/** The data on either side, or null when none was ever created (a fresh player). */
	public static @Nullable IdentityData view(Player player) {
		return player.getAttached(IdentityAttachments.IDENTITY);
	}

	private static long gameTime(ServerPlayer player) {
		return player.level().getGameTime();
	}

	// ---------------------------------------------------------------- alignment

	/** -100 (dark) to +100 (light). */
	public static int alignment(Player p) {
		IdentityData data = view(p);
		return data == null ? 0 : data.alignment;
	}

	/** The alignment band (server: configured edges; client: default edges). */
	public static AlignmentBand band(Player p) {
		return p instanceof ServerPlayer ? IdentityRules.band(alignment(p), balance()) : AlignmentBand.of(alignment(p));
	}

	/** Applies a shift. Returns false if it was ignored (a repeated major source or a cap). */
	public static boolean shift(ServerPlayer p, AlignmentShift shift) {
		IdentityRules.Cap cap = shift.kind() == AlignmentShift.Kind.MAJOR ? IdentityRules.Cap.MAJOR : IdentityRules.Cap.MINOR;
		return applyShift(p, shift.sourceId(), shift.amount(), cap, shift.reasonKey());
	}

	/**
	 * Applies a shift with an explicit cap (the severe cap of killing innocents is internal to this
	 * feature) and sends the toast, the band change and the event.
	 */
	static boolean applyShift(ServerPlayer p, String sourceId, int amount, IdentityRules.Cap cap, String reasonKey) {
		IdentityBalance b = balance();
		IdentityData data = get(p);
		int before = data.alignment;
		AlignmentBand bandBefore = IdentityRules.band(before, b);
		Integer applied = IdentityRules.shift(data, sourceId, amount, cap, reasonKey, IdentityRules.dayIndex(gameTime(p)), b);

		if (applied == null) {
			return false;
		}

		commit(p, data);

		if (p.isCreative() || p.isSpectator()) {
			OldRepublicMod.LOGGER.info("Alignment of {} (creative/spectator) shifted {} by {} from {} ({})", p.getName().getString(), applied, sourceId, before, reasonKey);
		}

		if (applied != 0) {
			AlignmentBand bandAfter = IdentityRules.band(data.alignment, b);

			if (introduced(data)) {
				int variant = bandAfter.ordinal()
						| (bandAfter != bandBefore ? IdentityEffects.FLAG_BAND_CHANGED : 0)
						| (cap == IdentityRules.Cap.MAJOR ? 0 : IdentityEffects.FLAG_MINOR);
				EffectEvents.toPlayer(p, IdentityEffects.ALIGNMENT_SHIFT, p.position(), null, p.getId(), applied, variant);
			}

			IdentityEvents.ALIGNMENT_CHANGED.invoker().onAlignmentChanged(p, before, data.alignment);
		}

		return true;
	}

	/** Reports a contextual act; the identity feature decides the amount and applies the caps. */
	public static void contextual(ServerPlayer p, ContextAct act, @Nullable Entity target) {
		ContextualActs.report(p, act, target);
	}

	/** Sets alignment directly (operator command). Logged; not capped; no log entry for the caps. */
	public static void setAlignment(ServerPlayer p, int value, String reasonKey) {
		IdentityData data = get(p);
		int before = data.alignment;
		applyShift(p, "", value - before, IdentityRules.Cap.MAJOR, reasonKey);
		OldRepublicMod.LOGGER.info("Alignment of {} set to {} by command (was {})", p.getName().getString(), alignment(p), before);
	}

	// ---------------------------------------------------------------- reputation

	/** -1000 to +1000. */
	public static int reputation(Player p, Faction f) {
		IdentityData data = view(p);
		return data == null ? 0 : data.reputation(f);
	}

	/** The reputation tier with a faction (server: configured edges; client: default edges). */
	public static ReputationTier tier(Player p, Faction f) {
		return p instanceof ServerPlayer ? IdentityRules.tier(reputation(p, f), balance()) : ReputationTier.of(reputation(p, f));
	}

	/**
	 * Changes reputation with a faction.
	 *
	 * @param oneShot when true, the change applies only once per {@code sourceId}
	 */
	public static void changeReputation(ServerPlayer p, Faction f, int amount, String sourceId, boolean oneShot) {
		IdentityBalance b = balance();
		IdentityData data = get(p);
		ReputationTier tierBefore = IdentityRules.tier(data.reputation(f), b);
		Integer applied = IdentityRules.changeReputation(data, f, amount, sourceId, oneShot);

		if (applied == null) {
			return;
		}

		commit(p, data);

		if (applied != 0 && introduced(data)) {
			ReputationTier tierAfter = IdentityRules.tier(data.reputation(f), b);
			int variant = f.ordinal() | (tierAfter != tierBefore ? IdentityEffects.FLAG_TIER_CHANGED : 0);
			EffectEvents.toPlayer(p, IdentityEffects.REPUTATION_CHANGE, p.position(), null, p.getId(), applied, variant);
		}
	}

	/** Sets reputation directly (operator command). */
	public static void setReputation(ServerPlayer p, Faction f, int value) {
		IdentityData data = get(p);
		IdentityRules.setReputation(data, f, value);
		commit(p, data);
	}

	// ---------------------------------------------------------------- path and rank

	public static Path path(Player p) {
		IdentityData data = view(p);
		return data == null ? Path.NONE : data.path;
	}

	/**
	 * Sets the path. Refuses changes the rules do not allow (JEDI to SITH and back) unless
	 * {@code force} is set (operator command). Returns whether the path changed.
	 *
	 * <p>Joining the Jedi or the Sith also makes the player an Initiate / Acolyte (rank 1), sets
	 * the mentor's trust to the starting value and moves reputation with both orders (once per
	 * path). Fires {@link IdentityEvents#PATH_CHANGED} and {@link QuestHooks#onPathChanged}.
	 */
	public static boolean setPath(ServerPlayer p, Path path, boolean force) {
		IdentityData data = get(p);

		if (!IdentityRules.pathChangeAllowed(data, path, force)) {
			return false;
		}

		Path before = data.path;
		IdentityRules.applyPath(data, path);
		commit(p, data);

		if (force) {
			OldRepublicMod.LOGGER.info("Path of {} forced from {} to {}", p.getName().getString(), before, path);
		}

		Faction own = IdentityRules.factionOf(path);
		String mentor = IdentityRules.mentorOf(path);

		if (own != null && mentor != null) {
			IdentityBalance b = balance();
			Faction rival = own == Faction.JEDI_ORDER ? Faction.SITH_EMPIRE : Faction.JEDI_ORDER;
			changeReputation(p, own, b.joinRep, "join." + path.id(), true);
			changeReputation(p, rival, b.rivalRep, "join." + path.id(), true);
			IdentityData after = get(p);

			if (after.choices.add("trust:join." + path.id())) {
				IdentityRules.addTrust(after, mentor, Math.max(0, b.trustStart - after.trust(mentor)));
				commit(p, after);
			}
		}

		IdentityEvents.PATH_CHANGED.invoker().onPathChanged(p, before, path);
		QuestHooks.onPathChanged(p, path);
		return true;
	}

	/** 0 = none, 1 = Initiate / Acolyte, 2 = Padawan / Apprentice. */
	public static int rank(Player p) {
		IdentityData data = view(p);
		return data == null ? 0 : data.rank;
	}

	/**
	 * Raises the rank to {@code rank} (never lowers it) and plays the rank-up moment: a banner for
	 * the player and a visible swell for everyone nearby.
	 */
	public static void promote(ServerPlayer p, int rank) {
		IdentityData data = get(p);

		if (rank <= data.rank) {
			return;
		}

		data.rank = rank;
		commit(p, data);
		EffectEvents.toTracking(p, IdentityEffects.RANK_UP, p.position(), null, p.getId(), rank, data.path.ordinal());
	}

	/** Sets the rank directly, up or down, without the rank-up moment (operator command). */
	public static void setRank(ServerPlayer p, int rank) {
		IdentityData data = get(p);
		data.rank = Math.max(0, rank);
		commit(p, data);
	}

	// ---------------------------------------------------------------- trust

	/** Trust with a mentor, 0 to 100. {@code mentorId} is {@code jedi} or {@code sith}. */
	public static int trust(Player p, String mentorId) {
		IdentityData data = view(p);
		return data == null ? 0 : data.trust(mentorId);
	}

	/** Adds (or with a negative amount removes) trust, clamped to 0 to 100. */
	public static void addTrust(ServerPlayer p, String mentorId, int amount, String reasonKey) {
		IdentityData data = get(p);
		int applied = IdentityRules.addTrust(data, mentorId, amount);

		if (applied != 0) {
			commit(p, data);
			OldRepublicMod.LOGGER.debug("Trust of {} with {} changed by {} ({})", p.getName().getString(), mentorId, applied, reasonKey);
		}
	}

	// ---------------------------------------------------------------- choices

	public static boolean hasChoice(Player p, String choiceId) {
		IdentityData data = view(p);
		return data != null && data.choices.contains(choiceId);
	}

	public static void recordChoice(ServerPlayer p, String choiceId) {
		IdentityData data = get(p);

		if (data.choices.add(choiceId)) {
			commit(p, data);
		}
	}

	/** Whether alignment and reputation toasts are shown yet (see {@link #INTRODUCED}). */
	static boolean introduced(IdentityData data) {
		return data.path != Path.NONE || data.choices.contains(INTRODUCED);
	}

	private Identity() {
	}
}
