package com.oldrepublic.saber;

import java.util.Locale;
import java.util.function.IntFunction;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * What a hilt part is made of (MILESTONE_7.md 3.1), from plain steel to hymnsteel. Every part that
 * existed before Milestone 7 is steel. The tier (0 to 5) picks the part's stat bonus ({@code SaberStats}),
 * the tint colours its metal ({@code oldrepublic:hilt_material}), and one unit of {@link #itemId()} is
 * what forging a part of it costs, per unit. Saved by id and synced by ordinal: append only.
 */
public enum HiltMaterial {
	STEEL(0, 0xE6E8EC, "minecraft:iron_ingot"),
	DURASTEEL(1, 0xB4C0CE, "oldrepublic:durasteel_plate"),
	STARFALL(2, 0x8C8496, "oldrepublic:starfall_ingot"),
	AURELITE(3, 0xE0C878, "oldrepublic:aurelite_shard"),
	DUSKGLASS(3, 0x7C6C9C, "oldrepublic:duskglass_shard"),
	SUNSTONE(4, 0xE09A4C, "oldrepublic:sunstone"),
	CLAN_IRON(4, 0x5C6676, "oldrepublic:clan_iron"),
	HYMNSTEEL(5, 0xCCC2E8, "oldrepublic:hymnsteel_ingot");

	/** By id; an id this build does not know decodes as steel, so a part never fails to load. */
	public static final Codec<HiltMaterial> CODEC = Codec.STRING.xmap(id -> {
		HiltMaterial material = byId(id);
		return material != null ? material : STEEL;
	}, HiltMaterial::id);
	public static final StreamCodec<ByteBuf, HiltMaterial> STREAM_CODEC;

	static {
		HiltMaterial[] values = values();
		IntFunction<HiltMaterial> byIndex = i -> i >= 0 && i < values.length ? values[i] : STEEL;
		STREAM_CODEC = ByteBufCodecs.idMapper(byIndex, Enum::ordinal);
	}

	private final int tier;
	private final int tint;
	private final Identifier itemId;

	HiltMaterial(int tier, int tint, String itemId) {
		this.tier = tier;
		this.tint = tint;
		this.itemId = Identifier.parse(itemId);
	}

	/** {@code steel}, {@code clan_iron}, ... */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** 0 (steel) to 5 (hymnsteel). */
	public int tier() {
		return tier;
	}

	/** The colour (RGB) the part's metal is tinted with. */
	public int tint() {
		return tint;
	}

	/** The material item one unit of a part costs at the Forge. */
	public Identifier itemId() {
		return itemId;
	}

	/** Lang key of the material's name ({@code saber.oldrepublic.material.<id>}). */
	public String translationKey() {
		return "saber.oldrepublic.material." + id();
	}

	/** The material of this id (case-insensitive), or null. */
	public static @Nullable HiltMaterial byId(String id) {
		for (HiltMaterial material : values()) {
			if (material.id().equalsIgnoreCase(id)) {
				return material;
			}
		}

		return null;
	}
}
