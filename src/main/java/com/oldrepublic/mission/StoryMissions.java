package com.oldrepublic.mission;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.identity.AlignmentShift;
import com.oldrepublic.identity.ContextAct;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.identity.YieldOutcome;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionData.Stage;
import com.oldrepublic.mission.MissionDefinition.Choice;
import com.oldrepublic.mission.MissionDefinition.Kind;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.Rewards;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The four story missions (spec 5.4): who may take them, the choices and what each pays (values in
 * the mission JSON, applied once through {@code Identity.recordChoice}), the officers' turn-ins,
 * and the I2 hand-over escort.
 */
public final class StoryMissions {
	/** R1: all three wounded tended. */
	public static final String TEND = "tend";
	/** R1: took the supply crate and left them. */
	public static final String LEAVE = "leave";
	/** I1: burned the book at the camp's fire or at the officer. */
	public static final String BURN = "burn";
	/** I2: the sergeant spared, handed over or executed. */
	public static final String SPARE = "spare";
	public static final String HANDED_OVER = "handed_over";
	public static final String EXECUTE = "execute";
	/** I2: the downed Imperial tended (a bonus during the mission, not its outcome). */
	public static final String TEND_WOUNDED = "tend_wounded";
	/** Lang key prefix of the reasons in a choice's alignment toast: {@code <prefix><short>.<choice>}. */
	static final String REASON_PREFIX = "mission.oldrepublic.reason.";

	/** How far from the camp's edge the I2 escorts come in, and how far beyond it they walk the prisoner. */
	private static final int ESCORT_EDGE = 10;
	private static final int ESCORT_EXIT = 34;
	/** Ticks between escort checks. */
	private static final int ESCORT_CHECK_INTERVAL = 10;
	/** The owner sees a walking prisoner only this far. */
	private static final double ESCORT_SIGHT = 64;

	/** An I2 hand-over walking off: the prisoner and his two escorts. */
	private record Escort(ResourceKey<Level> dimension, UUID owner, UUID prisoner, List<UUID> escorts, long start) {
	}

	private static final List<Escort> ESCORTS = new ArrayList<>();

	// ---------------------------------------------------------------- taking a story mission

	/**
	 * Why the player cannot take this story mission now, as a dialogue fail reason (a lang key with
	 * {@code |}-separated arguments), or null when they can: done already, another mission active,
	 * the mission before it not done, its gate (dialogue conditions such as {@code c3:stage}), or the
	 * giver's army below Neutral.
	 */
	static @Nullable String blocker(ServerPlayer p, MissionDefinition def, @Nullable Entity speaker) {
		MissionData d = MissionService.get(p);

		if (def.kind() != Kind.STORY) {
			return "message.oldrepublic.mission.req.not_story";
		} else if (d.storyDone(def.id())) {
			return "message.oldrepublic.mission.req.done";
		} else if (d.active != null) {
			return "message.oldrepublic.mission.req.active";
		}

		for (String required : def.requires()) {
			if (!d.storyDone(required)) {
				return "message.oldrepublic.mission.req.requires|@mission.oldrepublic." + required + ".title";
			}
		}

		Faction army = def.issuerArmy();

		if (army != null && Identity.tier(p, army).ordinal() < ReputationTier.NEUTRAL.ordinal()) {
			return "message.oldrepublic.mission.req.standing|@faction.oldrepublic." + army.id();
		}

		return Dialogues.failReason(def.gate(), p, speaker);
	}

	/** Takes a story mission from its officer (validated again here); true when it started. */
	static boolean start(ServerPlayer p, MissionDefinition def, @Nullable Entity speaker) {
		String blocker = blocker(p, def, speaker);

		if (blocker != null) {
			OldRepublicMod.LOGGER.debug("Story mission {} refused for {}: {}", def.id(), p.getName().getString(), blocker);
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.not_now"));
			return false;
		}

		BlockPos board = speaker != null ? speaker.blockPosition() : p.blockPosition();
		return MissionService.start(p, def, def.issuer(), board, null, null);
	}

	// ---------------------------------------------------------------- choices and pay

	/**
	 * Pays a completed story mission: 1,200 XP, the giver's commendations (or what the choice says),
	 * the choice's values once, and the journal entry. {@code choice} null pays the base reward only
	 * (an I2 sergeant who fled or died undecided).
	 */
	static void pay(ServerPlayer p, MissionDefinition def, Active active, @Nullable String choice) {
		MissionBalance b = MissionFeature.balance();
		Choice picked = choice == null ? null : def.choices().get(choice);

		if (choice != null && picked == null) {
			OldRepublicMod.LOGGER.warn("Mission {} has no choice '{}'; paying the base reward", def.id(), choice);
		}

		Progression.addXp(p, b.storyXp, "mission");
		int commendations = picked != null && picked.commendations() >= 0 ? picked.commendations() : b.storyCommendations;
		Faction army = picked != null && picked.commendationArmy() != null ? picked.commendationArmy() : def.issuerArmy();

		if (army != null && commendations > 0) {
			Rewards.give(p, Commendations.of(army, commendations));
		}

		if (picked != null) {
			applyChoice(p, def, picked);
		}

		if (def.entry() != null) {
			Journal.unlockEntry(p, def.entry());
		}
	}

	/**
	 * Applies a story choice once (the recorded choice {@code mission.<short>.<choice>} guards it):
	 * reputation (one-shot per faction, a Jedi's Order share on Republic gains), a major shift,
	 * contextual acts, loot, codex, Resonance, and the mission item handed over.
	 *
	 * @return false when it was applied before
	 */
	public static boolean applyChoice(ServerPlayer p, MissionDefinition def, Choice choice) {
		String id = def.choiceId(choice.id());

		if (Identity.hasChoice(p, id)) {
			return false;
		}

		Identity.recordChoice(p, id);

		for (Map.Entry<String, Integer> rep : choice.rep().entrySet()) {
			Faction faction = Faction.byId(rep.getKey());

			if (faction != null && rep.getValue() != 0) {
				Identity.changeReputation(p, faction, rep.getValue(), id, true);

				// The Jedi Order's share is paid only on Republic work (5.4), so sparing Brannick in I2 pays none.
				if (def.issuerArmy() == Faction.REPUBLIC) {
					MissionService.payJediShare(p, faction, rep.getValue(), id);
				}
			}
		}

		if (choice.alignment() != 0) {
			Identity.shift(p, new AlignmentShift(id, choice.alignment(), AlignmentShift.Kind.MAJOR, REASON_PREFIX + def.shortId() + "." + choice.id()));
		}

		for (ContextAct act : choice.context()) {
			Identity.contextual(p, act, null);
		}

		if (choice.loot() != null) {
			Rewards.giveLoot(p, ResourceKey.create(Registries.LOOT_TABLE, choice.loot()), Rewards.seed(p, id, 0));
		}

		if (choice.codex() != null) {
			Journal.unlockCodex(p, choice.codex());
		}

		if (choice.resonance() != null && choice.resonanceAmount() > 0) {
			Progression.grantResonanceMilestone(p, choice.resonance(), choice.resonanceAmount());
		}

		Item item = MissionItems.byId(def.item());

		if (choice.consume() && item != null && !MissionItems.take(p, item)) {
			OldRepublicMod.LOGGER.warn("{} chose {} without carrying {}", p.getName().getString(), id, def.item());
		}

		return true;
	}

	/**
	 * A turn-in choice at an officer ({@code mission:turn_in <choice>}): the story mission waits for
	 * its turn-in, the choice's own conditions hold (which officer, the ledger flag), and the item is
	 * carried when the choice hands it over.
	 */
	static void turnIn(ServerPlayer p, @Nullable Entity speaker, String choiceName) {
		MissionData d = MissionService.get(p);
		Active active = d.active;
		MissionDefinition def = active == null ? null : MissionLoader.get(active.id);
		Choice choice = def == null ? null : def.choices().get(choiceName);

		if (active == null || def == null || active.kind != Kind.STORY || active.stage != Stage.RETURN || choice == null) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.not_now"));
			return;
		}

		if (Dialogues.failReason(choice.conditions(), p, speaker) != null) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.not_here"));
			return;
		}

		Item item = MissionItems.byId(def.item());

		if (choice.consume() && item != null && !MissionItems.carries(p, item)) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.need_item", item.getName(new ItemStack(item))));
			return;
		}

		MissionService.complete(p, choiceName);
	}

	/**
	 * A choice confirmed at the site ({@code mission:leave_wounded} in R1's decision,
	 * {@code mission:burn_book} with the fence's book on the way back): completes the mission with it
	 * when the active mission is waiting for exactly that at its camp.
	 */
	static void confirmAtSite(ServerPlayer p, String choiceName, Stage stage) {
		MissionData d = MissionService.get(p);
		Active active = d.active;
		MissionDefinition def = active == null ? null : MissionLoader.get(active.id);
		double reach = MissionFeature.balance().siteSpawnDistance;

		if (active == null || def == null || active.stage != stage || !def.choices().containsKey(choiceName) || active.site == null
				|| p.position().distanceToSqr(Vec3.atCenterOf(active.site)) > reach * reach) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.not_now"));
			return;
		}

		Item item = MissionItems.byId(def.item());

		if (def.choices().get(choiceName).consume() && item != null && !MissionItems.carries(p, item)) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.need_item", item.getName(new ItemStack(item))));
			return;
		}

		active.set(MissionData.FLAG_CHOSEN);
		MissionService.commit(p, d);
		MissionService.complete(p, choiceName);
	}

	/** I2: the downed Imperial stood up after being tended (+20 Sith Empire, once). */
	static void onWoundedTended(ServerPlayer p, Active active) {
		MissionDefinition def = MissionLoader.get(active.id);
		Choice bonus = def == null ? null : def.choices().get(TEND_WOUNDED);

		if (bonus == null || active.has(MissionData.FLAG_WOUNDED_TENDED)) {
			return;
		}

		MissionData d = MissionService.get(p);

		if (d.active != null) {
			d.active.set(MissionData.FLAG_WOUNDED_TENDED);
			MissionService.commit(p, d);
		}

		applyChoice(p, def, bonus);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.wounded_tended"));
	}

	/**
	 * The I2 choice the sergeant's yield decision makes ({@code yield_border}): Spare, Hand over or
	 * Execute; null for a flight or an interrogation, which end the mission without a choice.
	 */
	public static @Nullable String yieldChoice(YieldOutcome outcome) {
		return switch (outcome) {
			case SPARED -> SPARE;
			case HANDED_OVER -> HANDED_OVER;
			case EXECUTED -> EXECUTE;
			case FLED, INTERROGATED -> null;
		};
	}

	// ---------------------------------------------------------------- the I2 hand-over

	/**
	 * Two Imperial troopers come in from the edge of the post, bind the sergeant's hands (W4 binds
	 * him on {@code HANDED_OVER}) and walk him off away from the player. All three are removed once
	 * the owner loses sight of the prisoner after a short walk, or after {@code escortDespawnTicks}.
	 */
	static void handOver(ServerPlayer p, LivingEntity sergeant, @Nullable BlockPos site) {
		ServerLevel level = p.level();
		Vec3 centre = site != null ? Vec3.atCenterOf(site) : sergeant.position();
		Vec3 away = sergeant.position().subtract(p.position()).multiply(1, 0, 1);
		away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
		BlockPos edge = surface(level, centre.add(away.scale(ESCORT_EDGE)));
		BlockPos exit = surface(level, centre.add(away.scale(ESCORT_EXIT)));
		UUID owner = p.getUUID();
		String missionId = MissionSites.missionOf(sergeant);
		sergeant.addTag(MissionSites.ROLE_PREFIX + MissionSites.ROLE_ESCORT);
		List<UUID> escorts = new ArrayList<>();

		for (LivingEntity escort : Troopers.spawnEscort(level, edge, Faction.SITH_EMPIRE, sergeant, exit)) {
			MissionSites.tag(escort, owner, missionId != null ? missionId : "i2_border_incident", MissionSites.ROLE_ESCORT);
			escorts.add(escort.getUUID());
		}

		ESCORTS.add(new Escort(level.dimension(), owner, sergeant.getUUID(), List.copyOf(escorts), level.getGameTime()));
	}

	private static BlockPos surface(ServerLevel level, Vec3 at) {
		int x = (int) Math.floor(at.x);
		int z = (int) Math.floor(at.z);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	/** Removes hand-over groups that are out of their owner's sight or out of time ({@link MissionRules#escortGone}). */
	static void tickEscorts(MinecraftServer server) {
		if (ESCORTS.isEmpty() || server.overworld().getGameTime() % ESCORT_CHECK_INTERVAL != 0) {
			return;
		}

		MissionBalance b = MissionFeature.balance();
		Iterator<Escort> it = ESCORTS.iterator();

		while (it.hasNext()) {
			Escort escort = it.next();
			ServerLevel level = server.getLevel(escort.dimension());

			if (level == null) {
				it.remove();
				continue;
			}

			Entity prisoner = level.getEntity(escort.prisoner());
			ServerPlayer owner = server.getPlayerList().getPlayer(escort.owner());
			boolean seen = prisoner != null && owner != null && owner.level() == level && owner.distanceTo(prisoner) <= ESCORT_SIGHT
					&& owner.hasLineOfSight(prisoner);
			long age = level.getGameTime() - escort.start();

			if (prisoner == null || MissionRules.escortGone(age, seen, b)) {
				if (prisoner != null) {
					prisoner.discard();
				}

				for (UUID id : escort.escorts()) {
					Entity member = level.getEntity(id);

					if (member != null) {
						member.discard();
					}
				}

				it.remove();
			}
		}
	}

	/** Forgets the escorts (server stopped); unsaved escorts are gone with the level anyway. */
	static void clear() {
		ESCORTS.clear();
	}

	private StoryMissions() {
	}
}
