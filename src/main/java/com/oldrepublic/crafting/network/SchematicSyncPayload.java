package com.oldrepublic.crafting.network;

import java.util.LinkedHashMap;
import java.util.Map;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Every schematic the server loaded ({@code schematic_sync}, S2C, spec MILESTONE_4.md 16.2), as
 * compact JSON per id, sent on join and after {@code /reload}. The client parses them with the
 * server's loader, so both sides share one format (the skill-tree pattern).
 */
public record SchematicSyncPayload(Map<Identifier, String> schematics) implements CustomPacketPayload {
	public static final Type<SchematicSyncPayload> TYPE = new Type<>(OldRepublicMod.id("schematic_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SchematicSyncPayload> CODEC = StreamCodec.of(SchematicSyncPayload::write, SchematicSyncPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, SchematicSyncPayload p) {
		buf.writeVarInt(p.schematics.size());

		for (Map.Entry<Identifier, String> e : p.schematics.entrySet()) {
			buf.writeUtf(e.getKey().toString());
			// One schematic is a few hundred characters, far under the string limit.
			buf.writeUtf(e.getValue());
		}
	}

	private static SchematicSyncPayload read(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		Map<Identifier, String> schematics = new LinkedHashMap<>();

		for (int i = 0; i < count; i++) {
			schematics.put(Identifier.parse(buf.readUtf()), buf.readUtf());
		}

		return new SchematicSyncPayload(schematics);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
