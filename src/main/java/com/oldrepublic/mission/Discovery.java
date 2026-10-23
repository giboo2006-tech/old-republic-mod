package com.oldrepublic.mission;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.planet.PlanetBalance;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.server.level.ServerPlayer;

/**
 * Discovery (spec 3.7, 6.7): the first time a player enters a site of each kind it is recorded in
 * {@link MissionData#discovered}, pays XP (300 for a Jedi Ruin, Sith Tomb or Wreck Field, 150 for
 * each army's outpost; in M4, 150 for the first waystone and 300 for the first greater tomb, from
 * {@code planets}; nothing for the others), shows the "Discovered" toast with
 * {@code site.discovered}, and unlocks the codex pages that belong to it. The trooper and sentinel
 * codex pages also open on the first kill or tend ({@link #onTrooperMet}, {@link #onSentinelMet}).
 */
public final class Discovery {
	/**
	 * XP for discovering the first site of {@code kind}: structures, outposts, the planets' waystones
	 * and greater tombs, or nothing. Landing on a planet and reaching its hub pay from Chapter Four.
	 */
	public static int xp(SiteKind kind, MissionBalance b) {
		return switch (kind) {
			case JEDI_RUIN, SITH_TOMB, WRECK_FIELD -> b.discoveryStructureXp;
			case REPUBLIC_OUTPOST, IMPERIAL_OUTPOST -> b.discoveryOutpostXp;
			case WAYSTONE -> PlanetBalance.get().siteDiscoveryXp;
			case GREATER_TOMB -> PlanetBalance.get().greaterTombDiscoveryXp;
			default -> 0;
		};
	}

	/** Whether a site kind is discovered at all (mission camps belong to one player and are not "found"). */
	public static boolean counts(SiteKind kind) {
		return kind != SiteKind.MISSION_SITE;
	}

	/** The player entered a site piece: records the kind the first time and pays for it. */
	static void onSiteEntered(ServerPlayer p, SitePiece piece) {
		SiteKind kind = piece.kind();

		if (!counts(kind)) {
			return;
		}

		MissionData d = MissionService.get(p);

		if (!d.markDiscovered(kind.id())) {
			return;
		}

		MissionService.commit(p, d);
		int xp = xp(kind, MissionFeature.balance());

		if (xp > 0) {
			Progression.addXp(p, xp, "discovery");
		}

		MissionEffects.discovered(p, kind);

		switch (kind) {
			case REPUBLIC_OUTPOST -> {
				Journal.unlockCodex(p, JournalEntries.CODEX_THE_TREATY_LINE);
				onTrooperMet(p, Faction.REPUBLIC);
			}
			case IMPERIAL_OUTPOST -> {
				Journal.unlockCodex(p, JournalEntries.CODEX_THE_TREATY_LINE);
				onTrooperMet(p, Faction.SITH_EMPIRE);
			}
			case JEDI_RUIN, SITH_TOMB -> onSentinelMet(p);
		}
	}

	/** The first outpost of an army, or the first of its troopers killed or tended: that army's codex page. */
	static void onTrooperMet(ServerPlayer p, @Nullable Faction army) {
		if (army == Faction.REPUBLIC) {
			Journal.unlockCodex(p, JournalEntries.CODEX_REPUBLIC_TROOPERS);
		} else if (army == Faction.SITH_EMPIRE) {
			Journal.unlockCodex(p, JournalEntries.CODEX_IMPERIAL_TROOPERS);
		}
	}

	/** The first ruin or tomb, or the first construct destroyed: the Ruin Sentinels codex page. */
	static void onSentinelMet(ServerPlayer p) {
		Journal.unlockCodex(p, JournalEntries.CODEX_RUIN_SENTINELS);
	}

	private Discovery() {
	}
}
