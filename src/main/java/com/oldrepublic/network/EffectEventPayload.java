package com.oldrepublic.network;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * One gameplay moment that clients turn into visuals and sound locally (a push released, a bolt
 * deflected, a crystal discharging). One packet per event, never per particle.
 *
 * @param type      effect id, e.g. {@code oldrepublic:force_push}
 * @param pos       world position the effect is anchored to
 * @param dir       direction (unit vector) or {@link Vec3#ZERO}
 * @param entityId  related entity network id, or -1
 * @param intensity effect-specific strength (charge, damage, level...)
 * @param variant   effect-specific variant (branch, phase, colour...)
 */
public record EffectEventPayload(Identifier type, Vec3 pos, Vec3 dir, int entityId, float intensity, int variant) implements CustomPacketPayload {
	public static final Type<EffectEventPayload> TYPE = new Type<>(OldRepublicMod.id("effect_event"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EffectEventPayload> CODEC = StreamCodec.of(EffectEventPayload::write, EffectEventPayload::read);

	private static void write(RegistryFriendlyByteBuf buf, EffectEventPayload p) {
		Identifier.STREAM_CODEC.encode(buf, p.type);
		buf.writeDouble(p.pos.x);
		buf.writeDouble(p.pos.y);
		buf.writeDouble(p.pos.z);
		buf.writeFloat((float) p.dir.x);
		buf.writeFloat((float) p.dir.y);
		buf.writeFloat((float) p.dir.z);
		buf.writeVarInt(p.entityId + 1);
		buf.writeFloat(p.intensity);
		buf.writeVarInt(p.variant);
	}

	private static EffectEventPayload read(RegistryFriendlyByteBuf buf) {
		Identifier type = Identifier.STREAM_CODEC.decode(buf);
		Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
		Vec3 dir = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
		int entityId = buf.readVarInt() - 1;
		float intensity = buf.readFloat();
		int variant = buf.readVarInt();
		return new EffectEventPayload(type, pos, dir, entityId, intensity, variant);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
