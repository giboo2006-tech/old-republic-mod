package com.oldrepublic.equipment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The {@code oldrepublic:equipment_traits} item component (spec section 9.2): the set a piece
 * belongs to and its per-piece traits. Percentages are fractions ({@code 0.05} = 5%); a positive
 * {@code costPct} makes Force powers dearer, a positive {@code regenPct} speeds regeneration, and the
 * resistances reduce blaster and melee damage taken.
 */
public record EquipmentTraits(ArmourSet set, float costPct, float regenPct, int maxEnergy, float blasterResist, float meleeResist) {
	private static final Codec<ArmourSet> SET_CODEC = Codec.STRING.comapFlatMap(
			id -> {
				ArmourSet set = ArmourSet.byId(id);
				return set != null ? DataResult.success(set) : DataResult.error(() -> "Unknown armour set " + id);
			},
			ArmourSet::id);

	public static final Codec<EquipmentTraits> CODEC = RecordCodecBuilder.create(i -> i.group(
			SET_CODEC.fieldOf("set").forGetter(EquipmentTraits::set),
			Codec.FLOAT.optionalFieldOf("cost_pct", 0f).forGetter(EquipmentTraits::costPct),
			Codec.FLOAT.optionalFieldOf("regen_pct", 0f).forGetter(EquipmentTraits::regenPct),
			Codec.INT.optionalFieldOf("max_energy", 0).forGetter(EquipmentTraits::maxEnergy),
			Codec.FLOAT.optionalFieldOf("blaster_resist", 0f).forGetter(EquipmentTraits::blasterResist),
			Codec.FLOAT.optionalFieldOf("melee_resist", 0f).forGetter(EquipmentTraits::meleeResist)
	).apply(i, EquipmentTraits::new));
	public static final StreamCodec<ByteBuf, EquipmentTraits> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.idMapper(ArmourSet::byIndex, ArmourSet::ordinal), EquipmentTraits::set,
			ByteBufCodecs.FLOAT, EquipmentTraits::costPct,
			ByteBufCodecs.FLOAT, EquipmentTraits::regenPct,
			ByteBufCodecs.VAR_INT, EquipmentTraits::maxEnergy,
			ByteBufCodecs.FLOAT, EquipmentTraits::blasterResist,
			ByteBufCodecs.FLOAT, EquipmentTraits::meleeResist,
			EquipmentTraits::new);
}
