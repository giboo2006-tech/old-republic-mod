package com.oldrepublic.quest;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.CombatEvents;
import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.equipment.ArmourSet;
import com.oldrepublic.holocron.ArchiveChoice;
import com.oldrepublic.holocron.HolocronChoice;
import com.oldrepublic.identity.CaptiveOutcome;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Path;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.story.ChapterFourService;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The single place where gameplay systems tell the introductory quest that something happened.
 * Every feature calls these; the quest feature decides what they mean for the current stage.
 * Signatures are a contract between features: add new hooks, do not change existing ones.
 */
public final class QuestHooks {
	/** The Awakening should begin now. {@code reason} is {@code time}, {@code sleep} or {@code command}. */
	public static void onAwakeningDue(ServerPlayer player, String reason) {
		QuestService.onAwakeningDue(player, reason);
	}

	public static void onLevelUp(ServerPlayer player, int level) {
		QuestService.onLevelUp(player, level);
	}

	/**
	 * An ability was used successfully. {@code hostileHits} counts distinct hostile living
	 * targets it affected.
	 */
	public static void onAbilityUsed(ServerPlayer player, String abilityId, int hostileHits) {
		QuestService.onAbilityUsed(player, abilityId, hostileHits);
	}

	/** The player entered the bounds of a crystal cave structure for the first time this visit. */
	public static void onCaveEntered(ServerPlayer player) {
		QuestService.onCaveEntered(player);
	}

	public static void onTrialStarted(ServerPlayer player) {
		QuestService.onTrialStarted(player);
	}

	public static void onTrialCompleted(ServerPlayer player) {
		QuestService.onTrialCompleted(player);
	}

	public static void onTrialFailed(ServerPlayer player) {
		QuestService.onTrialFailed(player);
	}

	/** The player picked up their attuned kyber crystal. */
	public static void onCrystalObtained(ServerPlayer player) {
		QuestService.onCrystalObtained(player);
	}

	/** A Crystal Poacher was killed by this player. */
	public static void onPoacherKilled(ServerPlayer player) {
		QuestService.onPoacherKilled(player);
	}

	/** The player constructed a lightsaber at the workbench. */
	public static void onSaberConstructed(ServerPlayer player) {
		QuestService.onSaberConstructed(player);
	}

	/** A blaster bolt was deflected ({@code parried} = returned to the shooter), shooter unknown. */
	public static void onBoltDeflected(ServerPlayer player, boolean parried) {
		onBoltDeflected(player, parried, null);
	}

	/**
	 * A blaster bolt was deflected ({@code parried} = returned to the shooter). A parry also fires
	 * {@link CombatEvents#PARRY} as a projectile parry, with the shooter when it is living.
	 */
	public static void onBoltDeflected(ServerPlayer p, boolean parried, @Nullable Entity shooter) {
		QuestService.onBoltDeflected(p, parried);

		if (parried) {
			CombatEvents.PARRY.invoker().onParry(p, shooter instanceof LivingEntity living ? living : null, true);
		}
	}

	/**
	 * An ability affected a target. {@code target} is null for a batch of item entities (Force Pull
	 * on loose items). A living target also fires {@link CombatEvents#POWER_HIT}.
	 */
	public static void onAbilityHit(ServerPlayer p, String abilityId, @Nullable Entity target) {
		QuestService.onAbilityHit(p, abilityId, target);

		if (target instanceof LivingEntity living) {
			CombatEvents.POWER_HIT.invoker().onPowerHit(p, abilityId, living);
		}
	}

	/**
	 * A saber technique was performed. {@code technique} is {@code heavy}, {@code lunge},
	 * {@code finisher} or {@code parry}; {@code hit} says whether it connected. Fires
	 * {@link CombatEvents#TECHNIQUE} for every technique, and {@link CombatEvents#PARRY} as a melee
	 * parry for {@code parry}.
	 */
	public static void onSaberTechnique(ServerPlayer p, String technique, @Nullable LivingEntity target, boolean hit) {
		QuestService.onSaberTechnique(p, technique, target, hit);
		CombatEvents.TECHNIQUE.invoker().onTechnique(p, technique, target, hit);

		if ("parry".equals(technique)) {
			CombatEvents.PARRY.invoker().onParry(p, target, false);
		}
	}

	/** The player decided the fate of an entity that yielded. */
	public static void onYieldResolved(ServerPlayer p, LivingEntity yielded, YieldOutcome outcome) {
		QuestService.onYieldResolved(p, yielded, outcome);
	}

	/** The player resolved a group of captives. */
	public static void onCaptivesResolved(ServerPlayer p, String groupId, CaptiveOutcome outcome) {
		QuestService.onCaptivesResolved(p, groupId, outcome);
	}

	/** A skill node was bought or granted. */
	public static void onNodeLearned(ServerPlayer p, String nodeId) {
		QuestService.onNodeLearned(p, nodeId);
	}

	/** The player's path changed (after it is stored). */
	public static void onPathChanged(ServerPlayer p, Path path) {
		QuestService.onPathChanged(p, path);
	}

	// Milestone 3 hooks (spec section 15.1): the quest feature gives them meaning.

	/** The player entered a site piece; the world feature reports presence whenever the piece changes. */
	public static void onSiteEntered(ServerPlayer p, SitePiece piece) {
		QuestService.onSiteEntered(p, piece);
	}

	/** The player solved puzzle {@code puzzleId} of a site. */
	public static void onPuzzleSolved(ServerPlayer p, SitePiece piece, String puzzleId) {
		QuestService.onPuzzleSolved(p, piece, puzzleId);
	}

	/** The player cast Force Sense; {@code revealed} counts the points it showed. */
	public static void onForceSense(ServerPlayer p, int revealed) {
		QuestService.onForceSense(p, revealed);
	}

	/** The player touched their cairn (or, with {@code cache}, their cache) number {@code index}. */
	public static void onCairnTouched(ServerPlayer p, BlockPos pos, int index, boolean cache) {
		QuestService.onCairnTouched(p, pos, index, cache);
	}

	/** The player attuned a holocron and chose what to do with it; {@code first} for their first attunement of this id. */
	public static void onHolocronAttuned(ServerPlayer p, String holocronId, HolocronChoice choice, boolean first) {
		QuestService.onHolocronAttuned(p, holocronId, choice, first);
	}

	/** The player decided the Archive's fate. */
	public static void onArchiveResolved(ServerPlayer p, ArchiveChoice choice) {
		QuestService.onArchiveResolved(p, choice);
	}

	/**
	 * The player killed a trooper of {@code army}. {@code wasHostile} is the
	 * {@code Attitudes.hostileSnapshot} at the killing blow; the quest feature applies all kill
	 * reputation.
	 */
	public static void onTrooperKilled(ServerPlayer p, LivingEntity trooper, Faction army, TrooperRole role, boolean wasHostile) {
		QuestService.onTrooperKilled(p, trooper, army, role, wasHostile);
	}

	/** The player brought a downed trooper of {@code army} back to its feet. */
	public static void onTrooperTended(ServerPlayer p, LivingEntity trooper, Faction army) {
		QuestService.onTrooperTended(p, trooper, army);
	}

	/** The player destroyed a Ruin Sentinel. */
	public static void onConstructKilled(ServerPlayer p, LivingEntity construct, SentinelVariant variant) {
		QuestService.onConstructKilled(p, construct, variant);
	}

	/** The player put on a piece of an era armour set. */
	public static void onArmourEquipped(ServerPlayer p, ArmourSet set) {
		QuestService.onArmourEquipped(p, set);
	}

	/** A boss fight the player takes part in entered {@code phase}. */
	public static void onBossPhase(ServerPlayer p, String bossId, int phase) {
		QuestService.onBossPhase(p, bossId, phase);
	}

	/** The owner's boss yielded and waits for the owner's decision. */
	public static void onBossYielded(ServerPlayer owner, String bossId) {
		QuestService.onBossYielded(owner, bossId);
	}

	/**
	 * A boss fight ended with {@code outcome}; called once per participant. {@code owner} is the
	 * fight's owner and {@code firstClear} marks the owner's first clear. The quest feature grants
	 * every reward.
	 */
	public static void onBossDefeated(ServerPlayer p, String bossId, BossOutcome outcome, UUID owner, boolean firstClear) {
		QuestService.onBossDefeated(p, bossId, outcome, owner, firstClear);
	}

	// Milestone 4 (spec MILESTONE_4.md 15.1): the senders are named on each hook; the story reacts in ChapterFourService.

	/** W3 fires this when a flight settles on a planet; {@code first} marks the player's first landing there. */
	public static void onPlanetArrived(ServerPlayer p, Planets.Planet planet, boolean first) {
		ChapterFourService.onPlanetArrived(p, planet, first);
	}

	/** W3: the owner's ship changed state. */
	public static void onShipStateChanged(ServerPlayer owner, ShipState from, ShipState to) {
		ChapterFourService.onShipStateChanged(owner, from, to);
	}

	/** W3: a repair socket was completed (0 hull, 1 power core, 2 motivator, 3 nav computer). */
	public static void onShipPartFitted(ServerPlayer owner, int socket) {
		ChapterFourService.onShipPartFitted(owner, socket);
	}

	/** W5: the player crafted from {@code schematic} at {@code quality} (a {@code Quality} ordinal). */
	public static void onCrafted(ServerPlayer p, Identifier schematic, int quality, boolean first) {
		ChapterFourService.onCrafted(p, schematic, quality, first);
	}

	/** W5: a discipline reached {@code level} (the first choice reports level 1). */
	public static void onDisciplineLevel(ServerPlayer p, String discipline, int level) {
		ChapterFourService.onDisciplineLevel(p, discipline, level);
	}

	/** W2: the player bought {@code bought} from {@code vendor} for {@code price} credits. */
	public static void onPurchase(ServerPlayer p, String vendor, ItemStack bought, int price) {
		ChapterFourService.onPurchase(p, vendor, bought, price);
	}

	/** W1: a corrupted waystone (or the Heartstone) was cleansed. */
	public static void onWaystoneCleansed(ServerPlayer p, BlockPos pos, boolean heartstone) {
		ChapterFourService.onWaystoneCleansed(p, pos, heartstone);
	}

	/** W6: the player killed a Mandalorian hunter; {@code owned} when it was hunting them. */
	public static void onHunterKilled(ServerPlayer p, LivingEntity hunter, boolean owned) {
		ChapterFourService.onHunterKilled(p, hunter, owned);
	}

	/** W6: the player destroyed a security droid. */
	public static void onDroidKilled(ServerPlayer p, LivingEntity droid) {
		ChapterFourService.onDroidKilled(p, droid);
	}

	/** W6: the player killed a planet beast ({@code hollowhorn} or {@code ash_hound}). */
	public static void onBeastKilled(ServerPlayer p, LivingEntity beast, String kind) {
		ChapterFourService.onBeastKilled(p, beast, kind);
	}

	/** W1: the player finished an expedition site. */
	public static void onExpeditionDone(ServerPlayer p, PlanetSites.Site site) {
		ChapterFourService.onExpeditionDone(p, site);
	}

	private QuestHooks() {
	}
}
