package com.oldrepublic.skill.network;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server's skill tree definitions ({@code skill_trees}), as compact JSON per tree, sent on join
 * and after {@code /reload}. The client parses them with the same loader as the server.
 */
public record SkillTreesPayload(Map<String, String> trees) implements CustomPacketPayload {
	public static final Type<SkillTreesPayload> TYPE = new Type<>(OldRepublicMod.id("skill_trees"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SkillTreesPayload> CODEC = StreamCodec.of(SkillTreesPayload::write, SkillTreesPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, SkillTreesPayload p) {
		buf.writeVarInt(p.trees.size());

		for (Map.Entry<String, String> e : p.trees.entrySet()) {
			buf.writeUtf(e.getKey());
			// Each tree stays well under the default 32767-character string limit.
			buf.writeUtf(e.getValue());
		}
	}

	private static SkillTreesPayload read(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		Map<String, String> trees = new LinkedHashMap<>();

		for (int i = 0; i < count; i++) {
			trees.put(buf.readUtf(), buf.readUtf());
		}

		return new SkillTreesPayload(trees);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
