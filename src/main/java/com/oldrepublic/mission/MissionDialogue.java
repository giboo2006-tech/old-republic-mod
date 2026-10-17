package com.oldrepublic.mission;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionData.Stage;
import com.oldrepublic.mission.MissionDefinition.Kind;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The mission feature's dialogue conditions and actions (spec 4.3 of the W2 plan), used by the
 * officers, the mentors' hubs and the two confirmations:
 *
 * <ul>
 * <li>{@code mission:available} (a story mission id, or {@code duty}): it can be taken now.</li>
 * <li>{@code mission:active} (an id, {@code any} or {@code duty}): that mission is active.</li>
 * <li>{@code mission:ready} (an id, or {@code duty}): active and waiting for its turn-in choice.</li>
 * <li>{@code mission:officer} ({@code republic} or {@code sith_empire}): the speaker is that army's officer.</li>
 * <li>{@code mission:start <id|duty>}, {@code mission:turn_in <choice>}, {@code mission:abandon},
 * {@code mission:leave_wounded}, {@code mission:burn_book}.</li>
 * </ul>
 */
final class MissionDialogue {
	private static final String REQ = "message.oldrepublic.mission.req.";
	private static final String DUTY = "duty";
	private static final String ANY = "any";

	static void register() {
		DialogueConditions.register("mission:available", (p, speaker, arg) -> available(p, speaker, arg.trim()));
		DialogueConditions.register("mission:active", (p, speaker, arg) -> matches(MissionService.get(p).active, arg.trim()) ? null : REQ + "not_active");
		DialogueConditions.register("mission:ready", (p, speaker, arg) -> {
			Active active = MissionService.get(p).active;
			return matches(active, arg.trim()) && active.stage == Stage.RETURN ? null : REQ + "not_ready";
		});
		DialogueConditions.register("mission:officer", (p, speaker, arg) -> isOfficer(speaker, arg.trim()) ? null : REQ + "officer");

		DialogueActions.registerArg("mission:start", (p, speaker, arg) -> start(p, speaker, arg.trim()));
		DialogueActions.registerArg("mission:turn_in", (p, speaker, arg) -> StoryMissions.turnIn(p, speaker, arg.trim()));
		DialogueActions.register("mission:abandon", (p, speaker) -> MissionService.abandon(p));
		DialogueActions.register("mission:leave_wounded", (p, speaker) -> StoryMissions.confirmAtSite(p, StoryMissions.LEAVE, Stage.DECIDE));
		DialogueActions.register("mission:burn_book", (p, speaker) -> StoryMissions.confirmAtSite(p, StoryMissions.BURN, Stage.RETURN));
	}

	/** {@code mission:available}: a story mission's blocker, or for {@code duty} today's mentor duty. */
	private static @Nullable String available(ServerPlayer p, @Nullable Entity speaker, String arg) {
		if (DUTY.equals(arg)) {
			return dutyBlocker(p, speaker);
		}

		MissionDefinition def = MissionLoader.get(arg);
		return def == null ? REQ + "unknown" : StoryMissions.blocker(p, def, speaker);
	}

	/** Why today's mentor duty cannot be taken: not a Jedi or Sith, taken today, another mission active, or its gate. */
	static @Nullable String dutyBlocker(ServerPlayer p, @Nullable Entity speaker) {
		String id = MissionRules.dutyFor(Identity.path(p));
		MissionDefinition def = id == null ? null : MissionLoader.get(id);
		MissionData d = MissionService.get(p);

		if (def == null) {
			return REQ + "no_duty";
		} else if (!MissionRules.dutyOpen(d, MissionService.day(p), MissionFeature.balance().dutiesPerDay)) {
			return REQ + "duty_taken";
		} else if (d.active != null) {
			return REQ + "active";
		}

		return Dialogues.failReason(def.gate(), p, speaker);
	}

	private static boolean matches(@Nullable Active active, String arg) {
		if (active == null) {
			return false;
		}

		return switch (arg) {
			case ANY -> true;
			case DUTY -> active.kind == Kind.DUTY;
			default -> active.id.equals(arg);
		};
	}

	/** Whether the speaker is an outpost officer of the army with this faction id. */
	static boolean isOfficer(@Nullable Entity speaker, String army) {
		Faction faction = Faction.byId(army);
		return speaker != null && faction != null && Troopers.isTrooper(speaker) && Troopers.role(speaker) == TrooperRole.OFFICER
				&& Troopers.faction(speaker) == faction;
	}

	/** {@code mission:start <id|duty>}: takes a story mission from its officer, or today's duty from the mentor. */
	private static void start(ServerPlayer p, @Nullable Entity speaker, String arg) {
		if (!DUTY.equals(arg)) {
			MissionDefinition def = MissionLoader.get(arg);

			if (def != null) {
				StoryMissions.start(p, def, speaker);
			}

			return;
		}

		Path path = Identity.path(p);
		String id = MissionRules.dutyFor(path);
		MissionDefinition def = id == null ? null : MissionLoader.get(id);

		if (def == null || dutyBlocker(p, speaker) != null) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.mission.not_now"));
			return;
		}

		int day = MissionService.day(p);
		String base = MissionRules.firstDutyType(def.base(), MissionRules.dutyOrder(day, p.getUUID()));

		if (base != null && MissionService.start(p, def, MissionDefinition.MENTOR, speaker != null ? speaker.blockPosition() : p.blockPosition(), base, null)) {
			Active active = MissionService.get(p).active;
			int variant = MissionRules.briefingVariant(active == null ? day : active.seed, def.briefings());
			p.sendSystemMessage(Component.translatable(MissionRules.briefingKey(base, path == Path.JEDI ? "jedi" : "sith", variant)));
		}
	}

	private MissionDialogue() {
	}
}
