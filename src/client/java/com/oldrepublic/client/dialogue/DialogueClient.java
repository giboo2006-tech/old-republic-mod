package com.oldrepublic.client.dialogue;

import java.util.List;

import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.dialogue.network.DialogueEndPayload;
import com.oldrepublic.dialogue.network.DialogueNodePayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client side of the dialogue engine: opens the {@link DialogueScreen} on the first node of a
 * session, updates it in place on later nodes, and closes it when the server ends the session.
 * Also turns the wire format of lines and reasons into components.
 */
public final class DialogueClient {
	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(DialogueNodePayload.TYPE, (payload, context) -> show(context.client(), payload));
		ClientPlayNetworking.registerGlobalReceiver(DialogueEndPayload.TYPE, (payload, context) -> end(context.client(), payload.session()));
	}

	private static void show(Minecraft mc, DialogueNodePayload payload) {
		if (mc.gui.screen() instanceof DialogueScreen screen && screen.session() == payload.session()) {
			screen.show(payload);
			return;
		}

		mc.gui.setScreen(new DialogueScreen(payload));
		QuestSounds.ui(ModSounds.DIALOGUE_OPEN, 1f, 0.6f);
	}

	private static void end(Minecraft mc, int session) {
		if (mc.gui.screen() instanceof DialogueScreen screen && screen.session() == session) {
			screen.endedByServer();
			mc.gui.setScreen(null);
		}
	}

	/**
	 * A lang key with dialogue arguments: {@code @key} arguments are translated, everything else is
	 * literal text (names were already filled in by the server).
	 */
	public static Component text(String key, List<String> args) {
		Object[] parts = new Object[args.size()];

		for (int i = 0; i < parts.length; i++) {
			parts[i] = argument(args.get(i));
		}

		return Component.translatable(key, parts);
	}

	/** A condition's failure reason, {@code key|arg|arg}, as a component. */
	public static Component reason(String encoded) {
		String[] parts = encoded.split("\\|", -1);
		return text(parts[0], List.of(parts).subList(1, parts.length));
	}

	private static Component argument(String arg) {
		return arg.startsWith("@") ? Component.translatable(arg.substring(1)) : Component.literal(arg);
	}

	private DialogueClient() {
	}
}
