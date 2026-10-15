package com.oldrepublic.registry;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Item data components shared between features. Feature-private components live in the feature. */
public final class ModComponents {
	/**
	 * A kyber crystal attuned to one player by the Trial of Reflection. Only the owner can build
	 * with it. {@code color} is the crystal's natural hue (RGB); the blade colour is chosen at the
	 * workbench and is cosmetic.
	 */
	public record CrystalAttunement(UUID owner, String ownerName, int color) {
		public static final Codec<CrystalAttunement> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(CrystalAttunement::owner),
				Codec.STRING.fieldOf("owner_name").forGetter(CrystalAttunement::ownerName),
				Codec.INT.fieldOf("color").forGetter(CrystalAttunement::color)
		).apply(i, CrystalAttunement::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, CrystalAttunement> STREAM_CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, CrystalAttunement::owner,
				ByteBufCodecs.STRING_UTF8, CrystalAttunement::ownerName,
				ByteBufCodecs.INT, CrystalAttunement::color,
				CrystalAttunement::new);
	}

	public static final DataComponentType<CrystalAttunement> CRYSTAL_ATTUNEMENT = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("crystal_attunement"),
			DataComponentType.<CrystalAttunement>builder().persistent(CrystalAttunement.CODEC).networkSynchronized(CrystalAttunement.STREAM_CODEC).build());

	public static void init() {
	}

	private ModComponents() {
	}
}
