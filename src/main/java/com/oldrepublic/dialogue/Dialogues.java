package com.oldrepublic.dialogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.network.DialogueChoicePayload;
import com.oldrepublic.dialogue.network.DialogueClosePayload;
import com.oldrepublic.dialogue.network.DialogueEndPayload;
import com.oldrepublic.dialogue.network.DialogueNodePayload;
import com.oldrepublic.identity.AlignmentShift;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityBalance;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Server entry point of the data-driven dialogue system ({@code data/oldrepublic/dialogue/*.json}).
 * One session per player; the server validates every choice: the session id, that the speaker is
 * alive and close, and the option's conditions, before applying its stance and action. Closing the
 * screen, walking away, taking damage, dying, logging out or changing dimension ends the session
 * with no choice made. {@link #ON_CLOSE} tells listeners about every ending.
 */
public final class Dialogues {
	/** What happened to a choice sent by a client. */
	public enum ChoiceResult {
		ACCEPTED,
		NO_SESSION,
		STALE_SESSION,
		SPEAKER_GONE,
		OUT_OF_RANGE,
		INVALID_OPTION,
		UNMET
	}

	/**
	 * A dialogue session ended, after it was removed: {@code chose} is true when a choice ended it
	 * (an option that closes the dialogue, or an action that closed it or opened another), false for
	 * Escape, walking away, damage, death, a dimension change, a disconnect or a forced close.
	 */
	public static final Event<Closed> ON_CLOSE = EventFactory.createArrayBacked(Closed.class,
			callbacks -> (player, dialogue, chose) -> {
				for (Closed callback : callbacks) {
					callback.onClosed(player, dialogue, chose);
				}
			});

	/** Listener for {@link #ON_CLOSE}. */
	@FunctionalInterface
	public interface Closed {
		/** {@code player}'s session of {@code dialogue} ended; {@code chose} when a choice ended it. */
		void onClosed(ServerPlayer player, Identifier dialogue, boolean chose);
	}

	private static final String INVALID_REASON = BuiltinConditions.REQ + "invalid";
	private static final Map<UUID, DialogueSession> SESSIONS = new HashMap<>();
	private static final Set<String> WARNED = new HashSet<>();
	private static int nextSession = 1;

	/** Registers the loader, payloads, built-in conditions and the session lifecycle hooks. */
	public static void init() {
		DialogueLoader.init();
		BuiltinConditions.register();

		PayloadTypeRegistry.clientboundPlay().register(DialogueNodePayload.TYPE, DialogueNodePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(DialogueEndPayload.TYPE, DialogueEndPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DialogueChoicePayload.TYPE, DialogueChoicePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DialogueClosePayload.TYPE, DialogueClosePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DialogueChoicePayload.TYPE, (payload, context) -> choose(context.player(), payload.session(), payload.option()));
		ServerPlayNetworking.registerGlobalReceiver(DialogueClosePayload.TYPE, (payload, context) -> {
			DialogueSession session = SESSIONS.get(context.player().getUUID());

			if (session != null && session.id == payload.session()) {
				SESSIONS.remove(context.player().getUUID());
				ended(context.player(), session);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(Dialogues::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0f) {
				close(player);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				close(player);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> {
			DialogueSession session = SESSIONS.remove(player.getUUID());

			if (session != null) {
				ended(player, session);
			}
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> close(player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
	}

	/**
	 * Starts dialogue {@code id} for the player, replacing any open session. A redirect registered for
	 * {@code id} is asked first and may name another dialogue to open instead, with the same speaker
	 * ({@link DialogueRedirects}, spec MILESTONE_5.md 17.1).
	 *
	 * @param speaker the entity speaking (faced, range-checked), or null for a speaker-less dialogue
	 * @return false if the dialogue does not exist or cannot open now
	 */
	public static boolean open(ServerPlayer p, Identifier id, @Nullable Entity speaker) {
		Identifier opened = DialogueRedirects.resolve(p, id);
		DialogueDefinition definition = DialogueLoader.get(opened);

		if (definition == null) {
			OldRepublicMod.LOGGER.warn("Dialogue {} does not exist", opened);
			return false;
		}

		if (!p.isAlive() || (speaker != null && !speakerInRange(p, speaker, Identity.balance().dialogueEndDistance))) {
			return false;
		}

		String start = startNode(definition, p, speaker);
		DialogueSession session = new DialogueSession(nextSession++, opened, start, speaker == null ? null : speaker.getUUID(), p.level().dimension());
		DialogueSession replaced = SESSIONS.put(p.getUUID(), session);
		send(p, session, definition, speaker);

		// After the send, so a listener that opens yet another dialogue is the last one the client sees.
		if (replaced != null) {
			ended(p, replaced);
		}

		return true;
	}

	/** Ends the player's session, if any, with no choice made (unless a choice is being applied). */
	public static void close(ServerPlayer p) {
		DialogueSession session = SESSIONS.remove(p.getUUID());

		if (session != null) {
			ServerPlayNetworking.send(p, new DialogueEndPayload(session.id));
			ended(p, session);
		}
	}

	/** The player's open session, or null. */
	public static @Nullable DialogueSession session(ServerPlayer p) {
		return SESSIONS.get(p.getUUID());
	}

	/**
	 * Handles a choice from the client: validates the session, the speaker and the option, applies
	 * the stance and the action, then shows the next node or ends the dialogue.
	 */
	public static ChoiceResult choose(ServerPlayer p, int sessionId, int optionIndex) {
		DialogueSession session = SESSIONS.get(p.getUUID());

		if (session == null) {
			return ChoiceResult.NO_SESSION;
		}

		if (session.id != sessionId) {
			return ChoiceResult.STALE_SESSION;
		}

		DialogueDefinition definition = DialogueLoader.get(session.dialogue);
		DialogueDefinition.Node node = definition == null ? null : definition.nodes().get(session.node);

		if (node == null) {
			close(p);
			return ChoiceResult.NO_SESSION;
		}

		Entity speaker = speaker(p, session);

		if (session.speaker != null) {
			if (speaker == null || !speaker.isAlive()) {
				close(p);
				return ChoiceResult.SPEAKER_GONE;
			}

			if (!speakerInRange(p, speaker, Identity.balance().dialogueChoiceDistance)) {
				p.sendOverlayMessage(Component.translatable("message.oldrepublic.identity.dialogue_too_far"));
				return ChoiceResult.OUT_OF_RANGE;
			}
		}

		if (optionIndex < 0 || optionIndex >= node.options().size()) {
			return ChoiceResult.INVALID_OPTION;
		}

		DialogueDefinition.Option option = node.options().get(optionIndex);

		if (failReason(option.conditions(), p, speaker) != null) {
			send(p, session, definition, speaker);
			return ChoiceResult.UNMET;
		}

		// Anything that ends this session while the choice is applied counts as ended by the choice.
		session.choosing = true;

		try {
			applyStance(p, option);

			if (option.action() != null) {
				runAction(p, option.action(), speaker);
			}

			if (SESSIONS.get(p.getUUID()) != session) {
				// The action opened another dialogue or closed this one.
				return ChoiceResult.ACCEPTED;
			}

			if (option.close()) {
				close(p);
			} else {
				session.node = option.next();
				send(p, session, definition, speaker);
			}
		} finally {
			session.choosing = false;
		}

		return ChoiceResult.ACCEPTED;
	}

	/**
	 * Why these conditions fail for the player, or null when all hold. Unknown or malformed
	 * conditions fail (and are logged once).
	 */
	public static @Nullable String failReason(Map<String, String> conditions, ServerPlayer p, @Nullable Entity speaker) {
		for (Map.Entry<String, String> entry : conditions.entrySet()) {
			DialogueConditions.Condition condition = DialogueConditions.get(entry.getKey());

			if (condition == null) {
				warnOnce("Unknown dialogue condition '" + entry.getKey() + "'");
				return INVALID_REASON;
			}

			try {
				String reason = condition.failReason(p, speaker, entry.getValue());

				if (reason != null) {
					return reason;
				}
			} catch (RuntimeException e) {
				warnOnce("Dialogue condition '" + entry.getKey() + "' failed on '" + entry.getValue() + "': " + e);
				return INVALID_REASON;
			}
		}

		return null;
	}

	private static String startNode(DialogueDefinition definition, ServerPlayer p, @Nullable Entity speaker) {
		for (DialogueDefinition.StartRule rule : definition.start()) {
			if (failReason(rule.conditions(), p, speaker) == null) {
				return rule.node();
			}
		}

		return definition.start().getLast().node();
	}

	private static void applyStance(ServerPlayer p, DialogueDefinition.Option option) {
		DialogueDefinition.Stance stance = option.stance();

		if (stance == null) {
			return;
		}

		if (stance.alignment() != 0) {
			Identity.shift(p, new AlignmentShift(stance.source(), stance.alignment(), AlignmentShift.Kind.MAJOR, option.text()));
		}

		String trustChoice = "trust:" + stance.source();

		if (!stance.trust().isEmpty() && !Identity.hasChoice(p, trustChoice)) {
			Identity.recordChoice(p, trustChoice);
			stance.trust().forEach((mentor, amount) -> Identity.addTrust(p, mentor, amount, option.text()));
		}
	}

	/**
	 * Runs an option's action. The text is resolved in this order: an action registered under the
	 * exact text; then, when it holds whitespace, the id before the first run of whitespace and the
	 * trimmed argument after it, as {@code id:arg}, as {@code id_arg}, and finally as the argument
	 * action {@code id} ({@link DialogueActions#registerArg}). Anything else is logged once.
	 */
	private static void runAction(ServerPlayer p, String actionText, @Nullable Entity speaker) {
		Runnable run = resolveAction(p, actionText, speaker);

		if (run == null) {
			warnOnce("Unknown dialogue action '" + actionText + "'");
			return;
		}

		try {
			run.run();
		} catch (RuntimeException e) {
			OldRepublicMod.LOGGER.error("Dialogue action {} failed", actionText, e);
		}
	}

	private static @Nullable Runnable resolveAction(ServerPlayer p, String actionText, @Nullable Entity speaker) {
		DialogueActions.Action exact = DialogueActions.get(actionText);

		if (exact != null) {
			return () -> exact.run(p, speaker);
		}

		String[] parts = actionText.trim().split("\\s+", 2);

		if (parts.length < 2) {
			return null;
		}

		String id = parts[0];
		String arg = parts[1].trim();

		for (String joined : new String[] {id + ":" + arg, id + "_" + arg}) {
			DialogueActions.Action action = DialogueActions.get(joined);

			if (action != null) {
				return () -> action.run(p, speaker);
			}
		}

		DialogueActions.ArgAction argAction = DialogueActions.getArg(id);
		return argAction == null ? null : () -> argAction.run(p, speaker, arg);
	}

	/** Sends the session's current node with its options evaluated now. */
	private static void send(ServerPlayer p, DialogueSession session, DialogueDefinition definition, @Nullable Entity speaker) {
		DialogueDefinition.Node node = definition.nodes().get(session.node);
		List<DialogueNodePayload.OptionView> options = new ArrayList<>();

		for (int i = 0; i < node.options().size() && options.size() < DialogueNodePayload.MAX_OPTIONS; i++) {
			DialogueDefinition.Option option = node.options().get(i);
			String reason = failReason(option.conditions(), p, speaker);

			if (reason != null && option.hiddenIfUnmet()) {
				continue;
			}

			options.add(new DialogueNodePayload.OptionView(i, option.text(), args(option.args(), p, speaker), reason == null, reason == null ? "" : reason));
		}

		ServerPlayNetworking.send(p, new DialogueNodePayload(session.id, session.dialogue.toString(), speaker == null ? -1 : speaker.getId(),
				definition.nameKey(), definition.portrait(), definition.accent(), node.line(), args(node.args(), p, speaker), List.copyOf(options)));
	}

	/** Replaces {@code {player}} and {@code {speaker}} with names; other arguments pass through. */
	private static List<String> args(List<String> raw, ServerPlayer p, @Nullable Entity speaker) {
		List<String> out = new ArrayList<>(raw.size());

		for (String arg : raw) {
			out.add(switch (arg) {
				case "{player}" -> p.getName().getString();
				case "{speaker}" -> speaker == null ? "" : speaker.getDisplayName().getString();
				default -> arg;
			});
		}

		return out;
	}

	private static @Nullable Entity speaker(ServerPlayer p, DialogueSession session) {
		return session.speaker == null ? null : p.level().getEntity(session.speaker);
	}

	private static boolean speakerInRange(ServerPlayer p, Entity speaker, double range) {
		return speaker.isAlive() && speaker.level() == p.level() && speaker.distanceToSqr(p) <= range * range;
	}

	/** Ends sessions whose player or speaker is gone or too far, and keeps speakers facing the player. */
	private static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}

		IdentityBalance b = Identity.balance();
		List<ServerPlayer> ending = new ArrayList<>();

		for (Iterator<Map.Entry<UUID, DialogueSession>> it = SESSIONS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, DialogueSession> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			if (player == null) {
				it.remove();
				continue;
			}

			DialogueSession session = entry.getValue();

			if (!player.isAlive() || player.level().dimension() != session.dimension) {
				ending.add(player);
				continue;
			}

			Entity speaker = speaker(player, session);

			if (session.speaker != null && (speaker == null || !speakerInRange(player, speaker, b.dialogueEndDistance))) {
				ending.add(player);
			} else if (speaker instanceof Mob mob) {
				mob.getLookControl().setLookAt(player, 30f, 30f);
				mob.getNavigation().stop();
			}
		}

		ending.forEach(Dialogues::close);
	}

	/** Fires {@link #ON_CLOSE} for a session that was just removed. */
	private static void ended(ServerPlayer p, DialogueSession session) {
		ON_CLOSE.invoker().onClosed(p, session.dialogue, session.choosing);
	}

	private static void warnOnce(String message) {
		if (WARNED.add(message)) {
			OldRepublicMod.LOGGER.warn(message);
		}
	}

	private Dialogues() {
	}
}
