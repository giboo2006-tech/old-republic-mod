package com.oldrepublic.quest.story;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.FactionM5Balance;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.mission.MissionData;
import com.oldrepublic.mission.MissionService;
import com.oldrepublic.mission.PlanetContracts;
import com.oldrepublic.mission.SpaceContracts;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.M5Items;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Chapter Five's dialogue conditions and actions (spec MILESTONE_5.md 6.11), used by {@code c5_*},
 * the Saltgate and holdfast people's dialogues, the fence's and the shipwright's, and
 * {@code spindle_log}. Every action checks the chapter again on the server, so a stale or forged
 * choice changes nothing. A boolean condition whose argument starts with {@code !} is negated.
 *
 * <p>Conditions of 6.11: {@code c5:stage} (at least this stage), {@code c5:stage_is} (one of a
 * comma-separated list), {@code c5:way} ({@code kajidic} / {@code clan} / {@code force}, or {@code any}:
 * that way is complete), {@code c5:kaast} ({@code spare} / {@code strip} / {@code end}: Chapter Four's
 * outcome) and {@code c5:yield} (Ulvaine waits for this owner's choice). The story's own:
 * <ul>
 * <li>{@code c5:audience}: Vessba grants her audience now (both jobs done; the debt alone without free flight);</li>
 * <li>{@code c5:door}: the counting-house door is the way in; {@code c5:no_power}: the player can use neither Mind Trick nor Fear now;</li>
 * <li>{@code c5:debt} ({@code choice} / {@code box} / {@code ran}): the debt job waits for the debtor's choice, or for Teel with the box or without it;</li>
 * <li>{@code c5:dask_ready} (empty, or a choice id): Dask waits for the player's choice (that choice may be made);</li>
 * <li>{@code c5:honour_owed}, {@code c5:helmet_asked}, {@code c5:helmet} (the helmet is asked for and carried),
 * {@code c5:met_varra}, {@code c5:clan_help} (the clan may be hired for the blockade);</li>
 * <li>{@code c5:flight} (free flight is on), {@code c5:cannons} (the story's free cannons wait in ARMED);</li>
 * <li>{@code c5:report_ready}; {@code c5:cipher} (a board id: the cipher can reroll its bounty today);
 * {@code c5:shard} (the fence's weekly shard, at Trusted).</li>
 * </ul>
 * Actions of 6.11: {@code c5:way <id>}, {@code c5:mission <id>}, {@code c5:dask <release|clan|end>},
 * {@code c5:report}, {@code c5:fit_cannons}, {@code c5:cipher <board>}, {@code c5:buy_shard},
 * {@code c5:hire_clan}, {@code ulvaine:resolve <spare|bind|end>}. The story's own:
 * {@code c5:debt <force|pay|run>}, {@code c5:debt_claim <box|trick|bribe|back>},
 * {@code c5:doorman <trick|fear|push>}, {@code c5:helmet}, {@code c5:hologram} and
 * {@code c5:codex <id>} (a page a dialogue opens: {@code the_spindle}).
 */
final class ChapterFiveDialogue {
	private static final String NOT_NOW = "message.oldrepublic.story.blocked.not_now";
	private static final String CREDITS = "message.oldrepublic.story.blocked.credits";
	private static final String REQ = "message.oldrepublic.story.c5.req.";
	/** The codex pages a dialogue may open ({@code c5:codex}): the Silent Spindle's log (5.7). */
	private static final Set<String> DIALOGUE_CODEX = Set.of(JournalEntries.CODEX_THE_SPINDLE);
	/** Days in a week, for the fence's weekly shard. */
	private static final int WEEK = 7;

	static void init() {
		registerConditions();
		registerActions();
	}

	// ---------------------------------------------------------------- conditions

	private static void registerConditions() {
		DialogueConditions.register("c5:stage", (p, speaker, arg) -> {
			ChapterFiveStage at = ChapterFiveRules.parseStage(arg);
			return at != null && Story.get(p).chapter5.stage().ordinal() >= at.ordinal() ? null : NOT_NOW;
		});
		DialogueConditions.register("c5:stage_is", (p, speaker, arg) -> {
			ChapterFiveStage stage = Story.get(p).chapter5.stage();
			return Arrays.stream(arg.split(",")).map(ChapterFiveRules::parseStage).anyMatch(s -> s == stage) ? null : NOT_NOW;
		});
		DialogueConditions.register("c5:way", (p, speaker, arg) -> check(arg, wayDone(Story.get(p), body(arg))));
		DialogueConditions.register("c5:kaast", (p, speaker, arg) -> {
			KaastOutcome kaast = ChapterFiveRules.kaast(Story.get(p));
			return kaast != null && Arrays.stream(arg.split(",")).map(s -> KaastOutcome.byId(s.trim().toLowerCase(Locale.ROOT))).anyMatch(o -> o == kaast)
					? null
					: NOT_NOW;
		});
		DialogueConditions.register("c5:yield", (p, speaker, arg) -> check(arg, ChapterFiveRules.choiceOpen(Story.get(p))));

		DialogueConditions.register("c5:audience", (p, speaker, arg) -> check(arg, ChapterFiveRules.audienceReady(Story.get(p), ChapterFiveService.flight())));
		DialogueConditions.register("c5:door", (p, speaker, arg) -> check(arg, ChapterFiveRules.doorOpen(Story.get(p))));
		DialogueConditions.register("c5:no_power", (p, speaker, arg) -> check(arg, !SaltgateWays.knowsDoorPower(p, speaker)));
		DialogueConditions.register("c5:debt", (p, speaker, arg) -> check(arg, debtState(p, body(arg))));
		DialogueConditions.register("c5:dask_ready", (p, speaker, arg) -> check(arg, daskReady(Story.get(p), body(arg))));
		DialogueConditions.register("c5:honour_owed", (p, speaker, arg) -> check(arg, ChapterFiveRules.honourOwed(Story.get(p))));
		DialogueConditions.register("c5:helmet_asked", (p, speaker, arg) -> check(arg, ChapterFiveRules.helmetAsked(Story.get(p))));
		DialogueConditions.register("c5:helmet", (p, speaker, arg) -> ChapterFiveRules.helmetAsked(Story.get(p)) && SaltgateWays.carriesHelmet(p)
				? null
				: REQ + "helmet");
		DialogueConditions.register("c5:met_varra", (p, speaker, arg) -> check(arg, Story.get(p).hasFlag(ChapterFiveRules.MET_VARRA)));
		DialogueConditions.register("c5:clan_help", (p, speaker, arg) -> check(arg, clanHelpOpen(p)));
		DialogueConditions.register("c5:flight", (p, speaker, arg) -> check(arg, ChapterFiveService.flight()));
		DialogueConditions.register("c5:cannons", (p, speaker, arg) -> {
			StoryData data = Story.get(p);
			return check(arg, data.chapter5.stage() == ChapterFiveStage.ARMED && !data.hasFlag(ChapterFiveRules.CANNONS_PAID));
		});
		DialogueConditions.register("c5:report_ready", (p, speaker, arg) -> check(arg, ChapterFiveRules.reportReady(Story.get(p))));
		DialogueConditions.register("c5:cipher", (p, speaker, arg) -> cipherBlocker(p, body(arg)));
		DialogueConditions.register("c5:shard", (p, speaker, arg) -> shardBlocker(p));
	}

	/** {@code null} when {@code value} holds (or fails, for an argument starting with {@code !}), else "not now". */
	private static @Nullable String check(String arg, boolean value) {
		return negated(arg) != value ? null : NOT_NOW;
	}

	private static boolean negated(String arg) {
		return arg.trim().startsWith("!");
	}

	/** The argument without its {@code !}, trimmed and in lower case. */
	private static String body(String arg) {
		String trimmed = arg.trim();
		return (trimmed.startsWith("!") ? trimmed.substring(1) : trimmed).trim().toLowerCase(Locale.ROOT);
	}

	/** {@code c5:way}: the way with this id is complete, or for {@code any} some way is. */
	private static boolean wayDone(StoryData data, String id) {
		if ("any".equals(id)) {
			return data.chapter5.chosenWay() != null;
		}

		ChapterFive.Way way = ChapterFive.Way.byId(id);
		return way != null && data.chapter5.hasWay(way);
	}

	/** {@code c5:debt}: the debt job's state, read from the active mission and the debtor's choice. */
	private static boolean debtState(ServerPlayer p, String state) {
		MissionData.Active active = MissionService.get(p).active;

		if (active == null || !ChapterFiveRules.DEBT.equals(active.id)) {
			return false;
		}

		StoryData data = Story.get(p);
		boolean chosen = data.hasFlag(SaltgateWays.DEBT_FORCE) || data.hasFlag(SaltgateWays.DEBT_PAID) || data.hasFlag(SaltgateWays.DEBT_RAN);
		boolean ran = data.hasFlag(SaltgateWays.DEBT_RAN);
		boolean back = active.stage == MissionData.Stage.RETURN;

		return switch (state) {
			case "choice" -> !back && !chosen;
			case "box" -> back && !ran;
			case "ran" -> back && ran;
			default -> false;
		};
	}

	/** {@code c5:dask_ready}: Dask waits for the choice; with a choice id, that one may be made (6.4). */
	private static boolean daskReady(StoryData data, String choice) {
		if (choice.isEmpty()) {
			return data.chapter5.stage() == ChapterFiveStage.THE_FACTOR && data.hasFlag(ChapterFiveRules.COUNTING_HOUSE)
					&& data.chapter5.dask() == StoryData.Chapter5.DASK_NONE;
		}

		return ChapterFiveRules.daskChoiceOpen(data, ChapterFiveRules.daskChoice(choice));
	}

	/** Whether Varra offers to fly in with the clan: enough failed blockades, no clan support, not hired yet (6.5). */
	private static boolean clanHelpOpen(ServerPlayer p) {
		StoryData data = Story.get(p);
		return !data.hasFlag(ChapterFive.CLAN_HIRED) && ChapterFiveRules.clanHelpOffered(data, ChapterFive.clanStands(p), ChapterFiveService.balance());
	}

	/** Why the fence's cipher cannot reroll {@code board}'s bounty now: used today, that bounty already taken today or in flight. */
	private static @Nullable String cipherBlocker(ServerPlayer p, String board) {
		StoryData data = Story.get(p);
		long day = ChapterFiveService.day(p);

		if (!ChapterFiveRules.cipherDue(data, day)) {
			return REQ + "cipher_used";
		}

		if (board.isEmpty()) {
			return null;
		}

		Planets.Planet planet = Planets.Planet.byId(board);

		if (planet == null || PlanetContracts.issuer(planet) == null) {
			return NOT_NOW;
		}

		String bounty = planet.id() + "_" + SpaceContracts.BOUNTY;
		return ChapterFiveRules.bountyDue(data, planet.id(), day) && !bounty.equals(MissionService.activeId(p)) ? null : REQ + "bounty_taken";
	}

	/** Why the fence's hymnstone shard cannot be bought now: the kajidic does not trust the player yet, or this week's is sold (7.1). */
	private static @Nullable String shardBlocker(ServerPlayer p) {
		if (Identity.tier(p, Faction.VESSBA_KAJIDIC).ordinal() < ReputationTier.TRUSTED.ordinal()) {
			return REQ + "trusted";
		}

		return ChapterFiveRules.shardDue(Story.get(p), ChapterFiveService.day(p) / WEEK) ? null : REQ + "shard_week";
	}

	// ---------------------------------------------------------------- actions

	private static void registerActions() {
		DialogueActions.registerArg("c5:way", (p, speaker, arg) -> SaltgateWays.wayAction(p, arg.trim().toLowerCase(Locale.ROOT)));
		DialogueActions.registerArg("c5:mission", (p, speaker, arg) -> startMission(p, speaker, arg.trim()));
		DialogueActions.registerArg("c5:dask", CountingHouse::choose);
		DialogueActions.register("c5:report", (p, speaker) -> UlvaineChoice.report(p));
		DialogueActions.register("c5:fit_cannons", (p, speaker) -> TyrvannArc.fitCannons(p));
		DialogueActions.registerArg("c5:cipher", (p, speaker, arg) -> cipher(p, body(arg)));
		DialogueActions.register("c5:buy_shard", (p, speaker) -> buyShard(p));
		DialogueActions.register("c5:hire_clan", (p, speaker) -> SaltgateWays.hireClan(p));
		DialogueActions.registerArg("ulvaine:resolve", (p, speaker, arg) -> UlvaineChoice.resolve(p, arg));

		DialogueActions.registerArg("c5:debt", (p, speaker, arg) -> SaltgateWays.debtChoice(p, speaker, body(arg)));
		DialogueActions.registerArg("c5:debt_claim", (p, speaker, arg) -> SaltgateWays.debtClaim(p, speaker, body(arg)));
		DialogueActions.registerArg("c5:doorman", (p, speaker, arg) -> SaltgateWays.doorman(p, speaker, body(arg)));
		DialogueActions.register("c5:helmet", (p, speaker) -> SaltgateWays.returnHelmet(p));
		DialogueActions.register("c5:hologram", CountingHouse::hologram);
		DialogueActions.registerArg("c5:codex", (p, speaker, arg) -> openCodex(p, body(arg)));
	}

	/** {@code c5:mission <id>}: one of the chapter's story missions from its giver, checked again by the mission service. */
	private static void startMission(ServerPlayer p, @Nullable Entity giver, String id) {
		if (ChapterFiveRules.isChapterMission(id)) {
			SaltgateWays.startMission(p, giver, id);
		}
	}

	/**
	 * {@code c5:cipher <board>}: the fence's cipher rerolls today's space bounty at that board (7.1):
	 * 50 credits, once a day; the board's bounty seed takes the cipher's count (5.8).
	 */
	static void cipher(ServerPlayer p, String board) {
		if (board.isEmpty() || cipherBlocker(p, board) != null) {
			p.sendOverlayMessage(Component.translatable(NOT_NOW));
			return;
		}

		int price = FactionM5Balance.get().cipherCredits;

		if (!Credits.take(p, price, true)) {
			p.sendOverlayMessage(Component.translatable(CREDITS, price));
			return;
		}

		StoryData data = Story.get(p);
		data.counters.put(ChapterFiveRules.CIPHER_DAY, (int) ChapterFiveService.day(p));
		data.counters.merge(ChapterFiveRules.CIPHER_PREFIX + board, 1, Integer::sum);
		Story.commit(p, data);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.cipher." + board));
	}

	/** {@code c5:buy_shard}: the fence's hymnstone shard at Trusted, 400 credits, once a week (7.1, {@code c5.shard_week}). */
	static void buyShard(ServerPlayer p) {
		if (shardBlocker(p) != null) {
			p.sendOverlayMessage(Component.translatable(NOT_NOW));
			return;
		}

		int price = FactionM5Balance.get().shardCredits;

		if (!Credits.take(p, price, true)) {
			p.sendOverlayMessage(Component.translatable(CREDITS, price));
			return;
		}

		StoryData data = Story.get(p);
		data.counters.put(ChapterFiveRules.SHARD_WEEK, (int) (ChapterFiveService.day(p) / WEEK));
		Story.commit(p, data);
		Rewards.give(p, new ItemStack(M5Items.HYMNSTONE_SHARD));
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c5.shard_bought"));
	}

	/** {@code c5:codex <id>}: a codex page a dialogue opens (only the pages listed here). */
	private static void openCodex(ServerPlayer p, String id) {
		if (DIALOGUE_CODEX.contains(id)) {
			Journal.unlockCodex(p, id);
		}
	}

	private ChapterFiveDialogue() {
	}
}
