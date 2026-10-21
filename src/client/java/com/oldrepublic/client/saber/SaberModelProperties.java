package com.oldrepublic.client.saber;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberComponents.SaberState;
import com.oldrepublic.saber.SaberComponents.StaffMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Custom item model properties and tint sources used by {@code items/lightsaber.json} and
 * {@code items/kyber_crystal.json}:
 * <ul>
 * <li>{@code oldrepublic:blade_extension} (range dispatch): 0 retracted to 1 fully extended,
 * animated over {@link #EXTEND_TICKS} ticks from the synced toggle time</li>
 * <li>{@code oldrepublic:second_blade_extension} (range dispatch, the saberstaff's lower blade,
 * spec MILESTONE_4.md 5.6): the blade extension, further limited by the staff's single-blade
 * toggle, animated the same way from its own toggle time</li>
 * <li>{@code oldrepublic:hilt_part} (select, field {@code part}): the emitter, grip or pommel id</li>
 * <li>{@code oldrepublic:blade_length} (select, MILESTONE_5.md 10.3): {@code short}, {@code standard}
 * or {@code long}; {@code items/lightsaber.json} draws the blade x0.7, x1 or x1.15 (the client's
 * swing ribbon and flying saber follow {@link #bladeScale})</li>
 * <li>{@code oldrepublic:blade_color} (tint): the chosen blade colour</li>
 * <li>{@code oldrepublic:crystal_color} (tint): a crystal's natural colour</li>
 * <li>{@code oldrepublic:saber_blade} (special model): the blade itself, drawn in code ({@link SaberBladeRenderer})</li>
 * </ul>
 */
final class SaberModelProperties {
	/** Ignition and retraction take this many ticks. */
	static final float EXTEND_TICKS = 6f;
	/** A short blade is drawn this long against the standard one (MILESTONE_5.md 10.3; the item models match). */
	static final float SHORT_BLADE = 0.7f;
	/** A long blade is drawn this long against the standard one (10.3; the item models match). */
	static final float LONG_BLADE = 1.15f;

	/** How far the blade is out, 0 to 1. Without a level (some GUI renders) it snaps. */
	record BladeExtension() implements RangeSelectItemModelProperty {
		static final MapCodec<BladeExtension> MAP_CODEC = MapCodec.unit(new BladeExtension());

		@Override
		public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
			return extension(stack, level);
		}

		@Override
		public MapCodec<BladeExtension> type() {
			return MAP_CODEC;
		}
	}

	/** How far a saberstaff's second blade is out, 0 to 1: never further than the main blade. */
	record SecondBladeExtension() implements RangeSelectItemModelProperty {
		static final MapCodec<SecondBladeExtension> MAP_CODEC = MapCodec.unit(new SecondBladeExtension());

		@Override
		public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
			return Math.min(extension(stack, level), secondExtension(stack, level));
		}

		@Override
		public MapCodec<SecondBladeExtension> type() {
			return MAP_CODEC;
		}
	}

	/** Selects a hilt part model by the variant id stored in the saber's configuration. */
	record HiltPart(String part) implements SelectItemModelProperty<String> {
		static final MapCodec<HiltPart> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.STRING.fieldOf("part").forGetter(HiltPart::part)
		).apply(i, HiltPart::new));
		static final SelectItemModelProperty.Type<HiltPart, String> TYPE = SelectItemModelProperty.Type.create(MAP_CODEC, Codec.STRING);

		@Override
		public String get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext context) {
			SaberConfig config = SaberComponents.config(stack);
			return switch (part) {
				case "emitter" -> config.emitter().id();
				case "grip" -> config.grip().id();
				case "pommel" -> config.pommel().id();
				default -> "";
			};
		}

		@Override
		public Codec<String> valueCodec() {
			return Codec.STRING;
		}

		@Override
		public SelectItemModelProperty.Type<HiltPart, String> type() {
			return TYPE;
		}
	}

	/** The blade length (MILESTONE_5.md 10.3): {@code short}, {@code standard} or {@code long}. */
	record BladeLength() implements SelectItemModelProperty<String> {
		static final MapCodec<BladeLength> MAP_CODEC = MapCodec.unit(new BladeLength());
		static final SelectItemModelProperty.Type<BladeLength, String> TYPE = SelectItemModelProperty.Type.create(MAP_CODEC, Codec.STRING);

		@Override
		public String get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext context) {
			return switch (SaberComponents.config(stack).length()) {
				case SaberConfig.LENGTH_SHORT -> "short";
				case SaberConfig.LENGTH_LONG -> "long";
				default -> "standard";
			};
		}

		@Override
		public Codec<String> valueCodec() {
			return Codec.STRING;
		}

		@Override
		public SelectItemModelProperty.Type<BladeLength, String> type() {
			return TYPE;
		}
	}

	/** Opaque ARGB of the saber's blade colour. */
	record BladeColorTint() implements ItemTintSource {
		static final MapCodec<BladeColorTint> MAP_CODEC = MapCodec.unit(new BladeColorTint());

		@Override
		public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
			return 0xFF000000 | SaberComponents.config(stack).color().rgb();
		}

		@Override
		public MapCodec<BladeColorTint> type() {
			return MAP_CODEC;
		}
	}

	/** Opaque ARGB of a crystal's natural colour; unattuned crystals are glow-white. */
	record CrystalColorTint() implements ItemTintSource {
		static final MapCodec<CrystalColorTint> MAP_CODEC = MapCodec.unit(new CrystalColorTint());

		@Override
		public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
			CrystalAttunement attunement = stack.get(ModComponents.CRYSTAL_ATTUNEMENT);
			return 0xFF000000 | (attunement == null ? 0xEAF4FF : attunement.color() & 0xFFFFFF);
		}

		@Override
		public MapCodec<CrystalColorTint> type() {
			return MAP_CODEC;
		}
	}

	/** The blade's drawn length against the standard blade (10.3): x0.7 short, x1.15 long, as in the item models. */
	static float bladeScale(ItemStack stack) {
		return switch (SaberComponents.config(stack).length()) {
			case SaberConfig.LENGTH_SHORT -> SHORT_BLADE;
			case SaberConfig.LENGTH_LONG -> LONG_BLADE;
			default -> 1f;
		};
	}

	/** Blade extension of {@code stack} right now, 0 to 1. */
	static float extension(ItemStack stack, @Nullable ClientLevel level) {
		SaberState state = SaberComponents.state(stack);
		ClientLevel clock = level != null ? level : Minecraft.getInstance().level;

		if (clock == null) {
			return state.ignited() ? 1f : 0f;
		}

		float t = Mth.clamp((clock.getGameTime() - state.toggledAt()) / EXTEND_TICKS, 0f, 1f);
		return state.ignited() ? t : 1f - t;
	}

	/**
	 * How far a blade is out {@code since} ticks (with the partial tick) after it was toggled, eased for the drawn blade
	 * ({@link SaberBladeRenderer}): igniting, it shoots out and settles (ease-out); going out, it slides back in and
	 * speeds up into the emitter (ease-in).
	 */
	static float easedExtension(boolean ignited, float since) {
		float t = Mth.clamp(since / EXTEND_TICKS, 0f, 1f);

		if (ignited) {
			float rest = 1f - t;
			return 1f - rest * rest * rest;
		}

		return 1f - t * t;
	}

	/** The staff's second-blade toggle alone, 0 (single blade) to 1 (both), animated from its toggle time. */
	static float secondExtension(ItemStack stack, @Nullable ClientLevel level) {
		StaffMode mode = SaberComponents.staffMode(stack);
		ClientLevel clock = level != null ? level : Minecraft.getInstance().level;

		if (clock == null) {
			return mode.single() ? 0f : 1f;
		}

		float t = Mth.clamp((clock.getGameTime() - mode.toggledAt()) / EXTEND_TICKS, 0f, 1f);
		return mode.single() ? 1f - t : t;
	}

	static void register() {
		RangeSelectItemModelProperties.ID_MAPPER.put(OldRepublicMod.id("blade_extension"), BladeExtension.MAP_CODEC);
		RangeSelectItemModelProperties.ID_MAPPER.put(OldRepublicMod.id("second_blade_extension"), SecondBladeExtension.MAP_CODEC);
		SelectItemModelProperties.ID_MAPPER.put(OldRepublicMod.id("hilt_part"), HiltPart.TYPE);
		SelectItemModelProperties.ID_MAPPER.put(OldRepublicMod.id("blade_length"), BladeLength.TYPE);
		ItemTintSources.ID_MAPPER.put(OldRepublicMod.id("blade_color"), BladeColorTint.MAP_CODEC);
		ItemTintSources.ID_MAPPER.put(OldRepublicMod.id("crystal_color"), CrystalColorTint.MAP_CODEC);
		SpecialModelRenderers.ID_MAPPER.put(OldRepublicMod.id("saber_blade"), SaberBladeRenderer.Unbaked.MAP_CODEC);
	}

	private SaberModelProperties() {
	}
}
