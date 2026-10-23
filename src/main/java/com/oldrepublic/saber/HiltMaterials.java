package com.oldrepublic.saber;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The materials of a saber's three parts (MILESTONE_7.md 3.1), carried in its {@code SaberConfig}. A
 * saberstaff's pommel entry is a placeholder it never counts. Each field is optional in the codec and
 * missing means steel.
 */
public record HiltMaterials(HiltMaterial emitter, HiltMaterial grip, HiltMaterial pommel) {
	/** Every part steel: every saber built before Milestone 7. */
	public static final HiltMaterials DEFAULT = new HiltMaterials(HiltMaterial.STEEL, HiltMaterial.STEEL, HiltMaterial.STEEL);

	public static final Codec<HiltMaterials> CODEC = RecordCodecBuilder.create(i -> i.group(
			HiltMaterial.CODEC.optionalFieldOf("emitter", HiltMaterial.STEEL).forGetter(HiltMaterials::emitter),
			HiltMaterial.CODEC.optionalFieldOf("grip", HiltMaterial.STEEL).forGetter(HiltMaterials::grip),
			HiltMaterial.CODEC.optionalFieldOf("pommel", HiltMaterial.STEEL).forGetter(HiltMaterials::pommel)
	).apply(i, HiltMaterials::new));

	public static final StreamCodec<ByteBuf, HiltMaterials> STREAM_CODEC = StreamCodec.composite(
			HiltMaterial.STREAM_CODEC, HiltMaterials::emitter,
			HiltMaterial.STREAM_CODEC, HiltMaterials::grip,
			HiltMaterial.STREAM_CODEC, HiltMaterials::pommel,
			HiltMaterials::new);

	/** The material of {@code slot}'s part. */
	public HiltMaterial of(HiltParts.Slot slot) {
		return switch (slot) {
			case EMITTER -> emitter;
			case GRIP -> grip;
			case POMMEL -> pommel;
		};
	}

	/** These materials with {@code slot}'s part made of {@code material}. */
	public HiltMaterials with(HiltParts.Slot slot, HiltMaterial material) {
		return switch (slot) {
			case EMITTER -> new HiltMaterials(material, grip, pommel);
			case GRIP -> new HiltMaterials(emitter, material, pommel);
			case POMMEL -> new HiltMaterials(emitter, grip, material);
		};
	}
}
