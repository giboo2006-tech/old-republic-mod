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
 * resistances reduce blaster and melee damage taken. The Milestone 4 traits sit in the optional
 * nested {@link Extra} (spec MILESTONE_4.md 6.1), so stacks saved before it decode with
 * {@link Extra#NONE}.
 */
public record EquipmentTraits(ArmourSet set, float costPct, float regenPct, int maxEnergy, float blasterResist, float meleeResist, Extra extra) {
	/**
	 * The Milestone 4 traits, fractions like the rest: {@code speedPct} (movement speed, shown; the
	 * piece's attribute modifier does the work), {@code controlPct} (control effects on the wearer,
	 * negative is shorter) and {@code fallPct} (fall damage taken, negative is less).
	 */
	public record Extra(float speedPct, float controlPct, float fallPct) {
		/** No M4 traits: every M3 piece, and any stack saved before M4. */
		public static final Extra NONE = new Extra(0f, 0f, 0f);

		public static final Codec<Extra> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.FLOAT.optionalFieldOf("speed_pct", 0f).forGetter(Extra::speedPct),
				Codec.FLOAT.optionalFieldOf("control_pct", 0f).forGetter(Extra::controlPct),
				Codec.FLOAT.optionalFieldOf("fall_pct", 0f).forGetter(Extra::fallPct)
		).apply(i, Extra::new));
		public static final StreamCodec<ByteBuf, Extra> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.FLOAT, Extra::speedPct,
				ByteBufCodecs.FLOAT, Extra::controlPct,
				ByteBufCodecs.FLOAT, Extra::fallPct,
				Extra::new);
	}

	private static final Codec<ArmourSet> SET_CODEC = Codec.STRING.comapFlatMap(
			id -> {
				ArmourSet set = ArmourSet.byId(id);
				return set != null ? DataResult.success(set) : DataResult.error(() -> "Unknown armour set " + id);
			},
			ArmourSet::id);
	private static final StreamCodec<ByteBuf, ArmourSet> SET_STREAM_CODEC = ByteBufCodecs.idMapper(ArmourSet::byIndex, ArmourSet::ordinal);

	public static final Codec<EquipmentTraits> CODEC = RecordCodecBuilder.create(i -> i.group(
			SET_CODEC.fieldOf("set").forGetter(EquipmentTraits::set),
			Codec.FLOAT.optionalFieldOf("cost_pct", 0f).forGetter(EquipmentTraits::costPct),
			Codec.FLOAT.optionalFieldOf("regen_pct", 0f).forGetter(EquipmentTraits::regenPct),
			Codec.INT.optionalFieldOf("max_energy", 0).forGetter(EquipmentTraits::maxEnergy),
			Codec.FLOAT.optionalFieldOf("blaster_resist", 0f).forGetter(EquipmentTraits::blasterResist),
			Codec.FLOAT.optionalFieldOf("melee_resist", 0f).forGetter(EquipmentTraits::meleeResist),
			Extra.CODEC.optionalFieldOf("extra", Extra.NONE).forGetter(EquipmentTraits::extra)
	).apply(i, EquipmentTraits::new));
	/** Written field by field, so the record's seven fields need no composite of that size. */
	public static final StreamCodec<ByteBuf, EquipmentTraits> STREAM_CODEC = StreamCodec.of(
			(buf, traits) -> {
				SET_STREAM_CODEC.encode(buf, traits.set());
				ByteBufCodecs.FLOAT.encode(buf, traits.costPct());
				ByteBufCodecs.FLOAT.encode(buf, traits.regenPct());
				ByteBufCodecs.VAR_INT.encode(buf, traits.maxEnergy());
				ByteBufCodecs.FLOAT.encode(buf, traits.blasterResist());
				ByteBufCodecs.FLOAT.encode(buf, traits.meleeResist());
				Extra.STREAM_CODEC.encode(buf, traits.extra());
			},
			buf -> new EquipmentTraits(SET_STREAM_CODEC.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), ByteBufCodecs.FLOAT.decode(buf), Extra.STREAM_CODEC.decode(buf)));

	/** An M3 piece's traits: no {@link Extra}. */
	public EquipmentTraits(ArmourSet set, float costPct, float regenPct, int maxEnergy, float blasterResist, float meleeResist) {
		this(set, costPct, regenPct, maxEnergy, blasterResist, meleeResist, Extra.NONE);
	}
}
