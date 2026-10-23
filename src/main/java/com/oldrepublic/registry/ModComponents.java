package com.oldrepublic.registry;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.equipment.EquipmentTraits;
import com.oldrepublic.holocron.HolocronStamp;

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
	 * workbench and is cosmetic. {@code tuning} is the crystal's tuning id ({@code keen},
	 * {@code swift}, {@code steady}, {@code warding} or {@code resonant}; MILESTONE_5.md 10.4), or
	 * {@code ""} for none: it lives on the crystal, so it travels through disassembly and reassembly.
	 */
	public record CrystalAttunement(UUID owner, String ownerName, int color, String tuning) {
		public static final Codec<CrystalAttunement> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(CrystalAttunement::owner),
				Codec.STRING.fieldOf("owner_name").forGetter(CrystalAttunement::ownerName),
				Codec.INT.fieldOf("color").forGetter(CrystalAttunement::color),
				// Absent on M1–M4 crystals (and not written while untuned), so old stacks decode unchanged (10.4).
				Codec.STRING.optionalFieldOf("tuning", "").forGetter(CrystalAttunement::tuning)
		).apply(i, CrystalAttunement::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, CrystalAttunement> STREAM_CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, CrystalAttunement::owner,
				ByteBufCodecs.STRING_UTF8, CrystalAttunement::ownerName,
				ByteBufCodecs.INT, CrystalAttunement::color,
				ByteBufCodecs.STRING_UTF8, CrystalAttunement::tuning,
				CrystalAttunement::new);

		/** A null tuning reads as none. */
		public CrystalAttunement {
			tuning = tuning == null ? "" : tuning;
		}

		/** An untuned crystal (every M1–M4 crystal). */
		public CrystalAttunement(UUID owner, String ownerName, int color) {
			this(owner, ownerName, color, "");
		}

		/** This crystal with {@code newTuning} ({@code ""} for none); retuning replaces the old tuning (10.4). */
		public CrystalAttunement withTuning(String newTuning) {
			return new CrystalAttunement(owner, ownerName, color, newTuning);
		}
	}

	public static final DataComponentType<CrystalAttunement> CRYSTAL_ATTUNEMENT = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("crystal_attunement"),
			DataComponentType.<CrystalAttunement>builder().persistent(CrystalAttunement.CODEC).networkSynchronized(CrystalAttunement.STREAM_CODEC).build());

	/** A holocron's identity, owner and issue number (spec section 4.1); the holocron feature reads and writes it. */
	public static final DataComponentType<HolocronStamp> HOLOCRON = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("holocron"),
			DataComponentType.<HolocronStamp>builder().persistent(HolocronStamp.CODEC).networkSynchronized(HolocronStamp.STREAM_CODEC).build());

	/** An era armour piece's set and per-piece traits (spec section 9.2); the equipment feature reads and writes it. */
	public static final DataComponentType<EquipmentTraits> EQUIPMENT_TRAITS = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("equipment_traits"),
			DataComponentType.<EquipmentTraits>builder().persistent(EquipmentTraits.CODEC).networkSynchronized(EquipmentTraits.STREAM_CODEC).build());

	public static void init() {
	}

	private ModComponents() {
	}
}
