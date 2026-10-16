package com.oldrepublic.ability.network;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server's live numbers for tooltips and the HUD ({@code ability_info}): for every registered
 * ability its input style and its {@code info()} map, plus every {@link com.oldrepublic.skill.NodeInfo}
 * source. Sent on join and after a data reload.
 *
 * @param entries source id (ability id or info source) to its entry
 */
public record AbilityInfoPayload(Map<String, Entry> entries) implements CustomPacketPayload {
	public static final Type<AbilityInfoPayload> TYPE = new Type<>(OldRepublicMod.id("ability_info"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityInfoPayload> CODEC = StreamCodec.of(AbilityInfoPayload::write, AbilityInfoPayload::read);

	/**
	 * @param inputStyle {@link com.oldrepublic.ability.InputStyle} ordinal, or -1 for a non-ability source
	 * @param values     info key to value
	 */
	public record Entry(int inputStyle, Map<String, Float> values) {
	}

	private static void write(RegistryFriendlyByteBuf buf, AbilityInfoPayload p) {
		buf.writeVarInt(p.entries.size());

		for (Map.Entry<String, Entry> e : p.entries.entrySet()) {
			buf.writeUtf(e.getKey());
			buf.writeVarInt(e.getValue().inputStyle() + 1);
			buf.writeVarInt(e.getValue().values().size());

			for (Map.Entry<String, Float> v : e.getValue().values().entrySet()) {
				buf.writeUtf(v.getKey());
				buf.writeFloat(v.getValue());
			}
		}
	}

	private static AbilityInfoPayload read(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		Map<String, Entry> entries = new LinkedHashMap<>();

		for (int i = 0; i < count; i++) {
			String id = buf.readUtf();
			int style = buf.readVarInt() - 1;
			int size = buf.readVarInt();
			Map<String, Float> values = new LinkedHashMap<>();

			for (int j = 0; j < size; j++) {
				values.put(buf.readUtf(), buf.readFloat());
			}

			entries.put(id, new Entry(style, values));
		}

		return new AbilityInfoPayload(entries);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
