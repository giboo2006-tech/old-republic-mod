package com.oldrepublic.quest.story;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.oldrepublic.economy.Credits;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.space.SpaceEncounters;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * From the factor to the Hymnal (spec MILESTONE_5.md 6.1, 6.5, 6.6): the shipwright's free cannons
 * and ARMED's wait for them, the blockade over Tyrvann (W2 runs the battle; this class starts it
 * on arrival in orbit, counts the failures, pays the victory and plays the scripted pass on
 * Peaceful or without free flight), the landing, the verse stones (rewards and visions in the
 * order they fall) and the Hymnal's door. It also keeps the journal's notes on worlds missing from
 * the server and on free flight (3.7, 6.9).
 */
final class TyrvannArc {
	/** Owners whose current blockade battle already counted a failure (one per battle, 6.5). */
	private static final Set<UUID> FAILED = new HashSet<>();
	/** The stones of the three verse stone sites, by index 1 to 3. */
	private static final PlanetSites.Site[] STONES = {PlanetSites.Site.VERSE_STONE_1, PlanetSites.Site.VERSE_STONE_2, PlanetSites.Site.VERSE_STONE_3};
	/** Dorrun has greeted the player at the holdfast and marked the stones (6.6). */
	static final String DORRUN_GREETED = "c5.dorrun_greeted";

	// ---------------------------------------------------------------- the journal's notes (3.7, 6.9)

	/** Keeps the flags the client's journal reads: a missing world, and free flight being off. */
	static void checkWorlds(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		StoryData data = Story.get(p);
		boolean changed = set(data, ChapterFiveRules.NO_GHESSAR, Planets.level(server, Planets.Planet.GHESSAR) == null);
		changed |= set(data, ChapterFiveRules.NO_TYRVANN, Planets.level(server, Planets.Planet.TYRVANN) == null);
		changed |= set(data, ChapterFiveRules.FLIGHT_OFF, !ChapterFiveService.flight());

		if (changed) {
			Story.commit(p, data);
		}
	}

	private static boolean set(StoryData data, String flag, boolean on) {
		return on ? data.flags.add(flag) : data.flags.remove(flag);
	}

	// ---------------------------------------------------------------- arming (6.5)

	/** ARMED begins: Tyrvann's coordinates are in the nav and the holdfast unlocks ({@link TheCall#repair}); a ship with cannons passes at once. */
	static void onArmed(ServerPlayer p) {
		TheCall.repair(p);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.coordinates"));
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c5.objective.armed"));
		checkCannons(p);
	}

	/** ARMED leaves when the ship has cannons (W1's {@code Ships.hasWeapons}). */
	static void checkCannons(ServerPlayer p) {
		if (Story.get(p).chapter5.stage() == ChapterFiveStage.ARMED && hasCannons(p)) {
			ChapterFiveService.enterStage(p, ChapterFiveStage.BLOCKADE);
		}
	}

	private static boolean hasCannons(ServerPlayer p) {
		return ShipRegistry.of(p.level().getServer(), p.getUUID()).map(Ships::hasWeapons).orElse(false);
	}

	/**
	 * {@code c5:fit_cannons}: the Saltgate shipwright fits a pair of cannons free in ARMED (the factor's
	 * confiscated funds pay; at any standing): the {@code CANNONS} upgrade, once, 200 XP, entry {@code teeth}.
	 */
	static void fitCannons(ServerPlayer p) {
		if (Story.get(p).chapter5.stage() != ChapterFiveStage.ARMED) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.blocked.not_now"));
			return;
		}

		MinecraftServer server = p.level().getServer();
		Optional<ShipRecord> ship = ShipRegistry.of(server, p.getUUID());

		if (ship.isEmpty()) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.c5.no_ship"));
			return;
		}

		if (Ships.busy(ship.get())) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.c5.ship_busy"));
			return;
		}

		if (!ChapterFiveService.once(p, ChapterFiveRules.CANNONS_PAID)) {
			return;
		}

		Ships.update(server, p.getUUID(), r -> r.withUpgrades(r.upgrades() | ShipRecord.UPGRADE_CANNONS));
		ChapterFiveService.xp(p, ChapterFiveService.balance().cannonsXp);
		Journal.unlockEntry(p, JournalEntries.TEETH);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.cannons"));
		checkCannons(p);
	}

	// ---------------------------------------------------------------- the blockade (6.5)

	/**
	 * BLOCKADE begins: every way to Tyrvann now ends in its orbit, where the battle waits. A ship that
	 * already had cannons skipped ARMED, so the holdfast unlocks here too.
	 */
	static void onBlockade(ServerPlayer p) {
		TheCall.repair(p);
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c5.objective.blockade"));
	}

	/**
	 * The owner's ship arrived in an orbit: over Tyrvann during BLOCKADE the battle starts (W2), unless
	 * it is already running. On Peaceful or without free flight, W2's scripted pass plays; if no
	 * battle runs then, the pass is told here.
	 */
	static void onOrbitEntered(ServerPlayer owner, Planets.Planet system, boolean byJump) {
		if (!ChapterFiveRules.blockadeStarts(Story.get(owner), system)) {
			return;
		}

		if (!SpaceEncounters.blockadeRunning(owner)) {
			FAILED.remove(owner.getUUID());
			SpaceEncounters.startBlockade(owner);
		}

		boolean scripted = ChapterFiveRules.scriptedPass(ChapterFiveService.peaceful(owner), ChapterFiveService.flight());

		if (scripted && Story.get(owner).chapter5.stage() == ChapterFiveStage.BLOCKADE && !SpaceEncounters.blockadeRunning(owner)) {
			scriptedPass(owner);
		}
	}

	/** The scripted pass (6.5, 6.9): the interceptors hail "the singer" and turn away; the gunship lets the ship through. */
	private static void scriptedPass(ServerPlayer owner) {
		owner.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.blockade_pass"));
		onBlockadeBroken(owner);
	}

	/** W2: the gunship is destroyed: 2,000 XP, 200 credits, entry {@code the_blockade}, codex {@code hymn_interceptors}; on to TYRVANN. */
	static void onBlockadeBroken(ServerPlayer owner) {
		FAILED.remove(owner.getUUID());

		if (Story.get(owner).chapter5.stage() != ChapterFiveStage.BLOCKADE) {
			return;
		}

		if (ChapterFiveService.once(owner, ChapterFiveRules.BLOCKADE_PAID)) {
			ChapterFiveBalance b = ChapterFiveService.balance();
			ChapterFiveService.xp(owner, b.blockadeXp);
			Credits.give(owner, b.blockadeCredits);
			Journal.unlockEntry(owner, JournalEntries.THE_BLOCKADE);
			Journal.unlockCodex(owner, JournalEntries.CODEX_HYMN_INTERCEPTORS);
		}

		ChapterFiveService.enterStage(owner, ChapterFiveStage.TYRVANN);
	}

	/** The ship was disabled: in BLOCKADE, during the battle or in Tyrvann's orbit, the battle is lost. */
	static void onShipDisabled(ServerPlayer owner) {
		if (Story.get(owner).chapter5.stage() == ChapterFiveStage.BLOCKADE
				&& (SpaceEncounters.blockadeRunning(owner) || Planets.orbitOf(owner.level().dimension()) == Planets.Planet.TYRVANN)) {
			onBlockadeFailed(owner);
		}
	}

	/**
	 * A lost battle (6.5): one failure per battle; after {@code clanHelpFailures} of them a player
	 * without the clan at their side is sent to Varra.
	 */
	static void onBlockadeFailed(ServerPlayer owner) {
		StoryData data = Story.get(owner);

		if (data.chapter5.stage() != ChapterFiveStage.BLOCKADE || !FAILED.add(owner.getUUID())) {
			return;
		}

		data.chapter5 = data.chapter5.withBlockadeFailures(data.chapter5.blockadeFailures() + 1);
		Story.commit(owner, data);
		owner.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.blockade_failed"));

		if (ChapterFiveRules.clanHelpOffered(data, ChapterFive.clanStands(owner), ChapterFiveService.balance())) {
			owner.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.clan_help", ChapterFiveService.balance().clanHelpCredits));
		}
	}

	// ---------------------------------------------------------------- Tyrvann (6.6)

	/** TYRVANN begins: the way down is open. */
	static void onTyrvann(ServerPlayer p) {
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c5.objective.tyrvann"));
	}

	/**
	 * The owner touched down on Tyrvann (W1's hook): a landing in BLOCKADE without the battle (the
	 * autopilot with free flight off, or Peaceful) is the scripted pass; TYRVANN moves to VERSES.
	 */
	static void onLanded(ServerPlayer p) {
		ChapterFiveStage stage = Story.get(p).chapter5.stage();

		if (stage == ChapterFiveStage.BLOCKADE && ChapterFiveRules.scriptedPass(ChapterFiveService.peaceful(p), ChapterFiveService.flight())) {
			scriptedPass(p);
		}

		if (Story.get(p).chapter5.stage() == ChapterFiveStage.TYRVANN) {
			ChapterFiveService.enterStage(p, ChapterFiveStage.VERSES);
		}
	}

	/** TYRVANN's slow check: a player already on Tyrvann (a missed hook) has landed. */
	static void checkLanded(ServerPlayer p) {
		if (ChapterFiveService.planet(p) == Planets.Planet.TYRVANN) {
			onLanded(p);
		}
	}

	/**
	 * VERSES begins: the three stones' ring points are set (each stone is placed as the owner comes
	 * near, 3.3.2). Dorrun greets a player the clan trusts and marks the stones; otherwise they come
	 * from Dask's data (6.6).
	 */
	static void onVerses(ServerPlayer p) {
		boolean trusted = Identity.tier(p, Faction.CLAN_HASK).ordinal() >= ReputationTier.TRUSTED.ordinal();

		if (trusted && ChapterFiveService.once(p, DORRUN_GREETED)) {
			ChapterFiveService.npcLine(p, "dorrun", "greet");
		}

		p.sendSystemMessage(Component.translatable(trusted ? "message.oldrepublic.story.c5.stones_dorrun" : "message.oldrepublic.story.c5.stones_dask"));
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c5.objective.verses", ChapterFiveRules.versesSilenced(Story.get(p).chapter5.verses()), 3));
	}

	/** From VERSES on, on Tyrvann: the owner's unsilenced stones and the shared Hymnal are placed as the owner comes near (W3's sites). */
	static void placeSites(ServerPlayer p) {
		if (ChapterFiveService.planet(p) != Planets.Planet.TYRVANN) {
			return;
		}

		int verses = Story.get(p).chapter5.verses();

		for (int i = 1; i <= STONES.length; i++) {
			if ((verses & ChapterFiveRules.verseBit(i)) == 0) {
				PlanetSites.ensure(p, STONES[i - 1], Planets.Planet.TYRVANN);
			}
		}

		PlanetSites.ensure(p, PlanetSites.Site.HYMNAL, Planets.Planet.TYRVANN);
	}

	/**
	 * W3: the owner silenced verse stone {@code index}. Paid once per stone (1,500 XP, 100 credits);
	 * the entry, the codex page, the vision and the third stone's SP follow the order the stones fall,
	 * whatever their index (6.10).
	 */
	static void onVerseSilenced(ServerPlayer owner, int index) {
		StoryData data = Story.get(owner);
		int bit = ChapterFiveRules.verseBit(index);

		if (bit == 0 || data.chapter5.stage().ordinal() < ChapterFiveStage.VERSES.ordinal() || (data.chapter5.verses() & bit) != 0) {
			return;
		}

		int order = ChapterFiveRules.versesSilenced(data.chapter5.verses()) + 1;
		data.chapter5 = data.chapter5.withVerses(data.chapter5.verses() | bit);
		Story.commit(owner, data);

		if (ChapterFiveService.once(owner, ChapterFiveRules.VERSE_PREFIX + index)) {
			ChapterFiveBalance b = ChapterFiveService.balance();
			ChapterFiveService.xp(owner, b.verseXp);
			Credits.give(owner, b.verseCredits);
			Journal.unlockEntry(owner, ChapterFiveRules.verseEntry(order));
			String codex = ChapterFiveRules.verseCodex(order);

			if (codex != null) {
				Journal.unlockCodex(owner, codex);
			}

			if (order == 3 && b.versesSp > 0) {
				Progression.grantSkillPoints(owner, b.versesSp);
			}

			EffectEvents.toPlayer(owner, QuestEffects.VERSE_VISION, owner.position(), null, owner.getId(), b.visionTicks, Math.min(2, order - 1));
		}

		PlanetSites.markDone(owner.level().getServer(), owner.getUUID(), STONES[index - 1]);
		checkVerses(owner);
	}

	/** VERSES passes to THE_HYMNAL with three stones silenced and the duel level (6.1); below it the journal says why. */
	static void checkVerses(ServerPlayer p) {
		if (ChapterFiveRules.versesReady(Story.get(p), Progression.get(p).level, ChapterFiveService.balance())) {
			ChapterFiveService.enterStage(p, ChapterFiveStage.THE_HYMNAL);
		}
	}

	// ---------------------------------------------------------------- the Hymnal (6.7)

	/** THE_HYMNAL begins: the Heart's door opens for the owner. */
	static void onHymnal(ServerPlayer p) {
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c5.objective.hymnal", ChapterFiveService.balance().recommendedLevel));
	}

	/** The owner walked into the Hymnal's halls at THE_HYMNAL or later: the door opened for them (entry {@code the_hymnal}, 6.10). */
	static void onSiteEntered(ServerPlayer p, SitePiece piece) {
		if (piece.kind() == SiteKind.HYMNAL && piece.template().equals(PlanetSites.HYMNAL_HALLS.template())
				&& Story.get(p).chapter5.stage().ordinal() >= ChapterFiveStage.THE_HYMNAL.ordinal()
				&& ChapterFiveService.once(p, ChapterFiveRules.HYMNAL_OPEN)) {
			Journal.unlockEntry(p, JournalEntries.THE_HYMNAL);
		}
	}

	static void forget(ServerPlayer p) {
		// A failure counted for a battle stays counted across a respawn: the battle itself resets on W2's side.
	}

	static void clear() {
		FAILED.clear();
	}

	private TyrvannArc() {
	}
}
