package com.oldrepublic.dialogue.network;

import java.util.ArrayList;
import java.util.List;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: show this dialogue node. Arguments are plain text, or {@code @lang.key} to be
 * translated on the client.
 *
 * @param session   the session id the client must quote back with its choice
 * @param dialogue  dialogue id, for logging and tests
 * @param speakerId the speaker's entity network id, or -1
 * @param nameKey   lang key of the speaker's name, or empty to use the speaker entity's name
 * @param portrait  GUI sprite id of the portrait, or empty for none
 * @param accent    ARGB accent colour under the name, or 0
 * @param line      lang key of the spoken line
 * @param lineArgs  arguments of the line
 * @param options   the visible options in order
 */
public record DialogueNodePayload(int session, String dialogue, int speakerId, String nameKey, String portrait, int accent,
		String line, List<String> lineArgs, List<OptionView> options) implements CustomPacketPayload {
	public static final Type<DialogueNodePayload> TYPE = new Type<>(OldRepublicMod.id("dialogue_node"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DialogueNodePayload> CODEC = StreamCodec.of(DialogueNodePayload::write, DialogueNodePayload::read);

	/** Most options a node can show (number keys 1 to 6). */
	public static final int MAX_OPTIONS = 6;
	private static final int MAX_ARGS = 8;

	/**
	 * One option as the client sees it.
	 *
	 * @param index  the option's index in the node, quoted back in the choice
	 * @param text   lang key of the option text
	 * @param args   arguments of the text
	 * @param enabled whether its conditions hold
	 * @param reason why it is disabled: a lang key, then arguments separated by {@code |}; empty when enabled
	 */
	public record OptionView(int index, String text, List<String> args, boolean enabled, String reason) {
	}

	private static void write(RegistryFriendlyByteBuf buf, DialogueNodePayload p) {
		buf.writeVarInt(p.session);
		buf.writeUtf(p.dialogue);
		buf.writeVarInt(p.speakerId + 1);
		buf.writeUtf(p.nameKey);
		buf.writeUtf(p.portrait);
		buf.writeInt(p.accent);
		buf.writeUtf(p.line);
		writeStrings(buf, p.lineArgs);
		buf.writeVarInt(Math.min(MAX_OPTIONS, p.options.size()));

		for (int i = 0; i < Math.min(MAX_OPTIONS, p.options.size()); i++) {
			OptionView o = p.options.get(i);
			buf.writeVarInt(o.index());
			buf.writeUtf(o.text());
			writeStrings(buf, o.args());
			buf.writeBoolean(o.enabled());
			buf.writeUtf(o.reason());
		}
	}

	private static DialogueNodePayload read(RegistryFriendlyByteBuf buf) {
		int session = buf.readVarInt();
		String dialogue = buf.readUtf();
		int speakerId = buf.readVarInt() - 1;
		String nameKey = buf.readUtf();
		String portrait = buf.readUtf();
		int accent = buf.readInt();
		String line = buf.readUtf();
		List<String> lineArgs = readStrings(buf);
		int count = Math.min(MAX_OPTIONS, buf.readVarInt());
		List<OptionView> options = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			options.add(new OptionView(buf.readVarInt(), buf.readUtf(), readStrings(buf), buf.readBoolean(), buf.readUtf()));
		}

		return new DialogueNodePayload(session, dialogue, speakerId, nameKey, portrait, accent, line, lineArgs, List.copyOf(options));
	}

	private static void writeStrings(RegistryFriendlyByteBuf buf, List<String> values) {
		int count = Math.min(MAX_ARGS, values.size());
		buf.writeVarInt(count);

		for (int i = 0; i < count; i++) {
			buf.writeUtf(values.get(i));
		}
	}

	private static List<String> readStrings(RegistryFriendlyByteBuf buf) {
		int count = Math.min(MAX_ARGS, buf.readVarInt());
		List<String> values = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			values.add(buf.readUtf());
		}

		return List.copyOf(values);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
