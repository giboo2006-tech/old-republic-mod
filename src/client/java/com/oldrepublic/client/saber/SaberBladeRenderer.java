package com.oldrepublic.client.saber;

import java.util.Locale;
import java.util.function.Consumer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.client.fx.GlowRenderTypes;
import com.oldrepublic.client.fx.GlowRibbon;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberState;
import com.oldrepublic.saber.SaberComponents.StaffMode;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The lightsaber blade, drawn in code as the special part of the saber's item models ({@code oldrepublic:saber_blade} in
 * {@code items/lightsaber.json}, {@code lightsaber_staff.json} and the training sabers'), while the hilt stays a baked
 * model. Instead of stacked boxes, each blade is a smooth beam that always faces the camera ({@link GlowRibbon}): a white
 * core with a soft edge in the blade colour, alpha blended so the colour reads against a bright sky, inside a wide, soft
 * glow of added light that blooms at night. The tip is rounded, the glow spills over the emitter, the glow shimmers a
 * little along the blade and flickers faintly, and the blade grows out and back smoothly between ticks, with a flare at
 * the moving tip and a flash at the emitter as it ignites. Behind a fast-moving blade, {@link BladeTrails} draws its
 * motion trail. No shader or post-processing is involved: the glow is geometry and blending.
 *
 * <p>Coordinates are the item model's (sixteenths of a block, the hilt's emitter top at y 6), so the item model's display
 * transforms place the blade with the hilt.
 */
final class SaberBladeRenderer implements SpecialModelRenderer<SaberBladeRenderer.Blade> {
	/** Which hilt the blade comes out of, set by the item model. */
	enum Kind {
		/** One blade up out of a saber's emitter (short, standard or long). */
		SINGLE,
		/** The saberstaff: a blade out of each end, the lower one on its own toggle. */
		STAFF,
		/** The training saber: a shorter, slimmer, fainter blade. */
		TRAINING;

		static final Codec<Kind> CODEC = Codec.STRING.comapFlatMap(Kind::parse, k -> k.name().toLowerCase(Locale.ROOT));

		private static DataResult<Kind> parse(String id) {
			for (Kind kind : values()) {
				if (kind.name().equalsIgnoreCase(id)) {
					return DataResult.success(kind);
				}
			}

			return DataResult.error(() -> "Unknown saber blade kind: " + id);
		}
	}

	/** The item model's {@code model} entry: {@code {"type": "oldrepublic:saber_blade", "kind": "single", "first_person": true}}. */
	record Unbaked(Kind kind, boolean firstPerson) implements SpecialModelRenderer.Unbaked<Blade> {
		static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Kind.CODEC.fieldOf("kind").forGetter(Unbaked::kind),
				Codec.BOOL.optionalFieldOf("first_person", false).forGetter(Unbaked::firstPerson)
		).apply(i, Unbaked::new));

		@Override
		public SpecialModelRenderer<Blade> bake(SpecialModelRenderer.BakingContext context) {
			return new SaberBladeRenderer(kind, firstPerson);
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}

	/**
	 * One frame's blade, read from the stack.
	 *
	 * @param extension how far the (upper) blade is out, 0 to 1, eased
	 * @param second    how far a staff's lower blade is out, 0 to 1, eased
	 * @param since     ticks since the blade was last toggled (with the partial tick), for the ignition flash
	 * @param lit       the blade is lit (or lighting), not going out
	 * @param time      the level's game time with the partial tick, for the flicker
	 */
	record Blade(ItemStack stack, int rgb, float extension, float second, float since, boolean lit, float lengthScale, float time) {
	}

	/** Sixteenths of a block: the item model's unit. */
	private static final float PX = 1f / 16f;
	/** Where a single saber's blade starts: a little inside the emitter (its top is at y 6), so no gap shows. */
	private static final float SINGLE_BASE = 5.6f;
	private static final float SINGLE_LENGTH = 25.4f;
	private static final float TRAINING_LENGTH = 20.4f;
	/** The staff's hilt ends (model y), as {@code gen_textures_saber.py} builds them, and each blade's length. */
	private static final float STAFF_TOP = 13.714f;
	private static final float STAFF_BOTTOM = 2.286f;
	private static final float STAFF_LENGTH = 17.9f;
	private static final float LONGEST = SaberModelProperties.LONG_BLADE;
	/** The core and its coloured edge, and the glow around it: half widths in model units. */
	private static final float BODY_RADIUS = 1.05f;
	private static final float GLOW_RADIUS = 3.1f;
	private static final float TRAINING_BODY_RADIUS = 0.85f;
	private static final float TRAINING_GLOW_RADIUS = 2.1f;
	/** Points along a blade for its shimmer. */
	private static final int SHIMMER_POINTS = 7;

	private final Kind kind;
	private final boolean firstPerson;

	SaberBladeRenderer(Kind kind, boolean firstPerson) {
		this.kind = kind;
		this.firstPerson = firstPerson;
	}

	@Override
	public Blade extractArgument(ItemStack stack) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float time = level == null ? 0f : (level.getGameTime() % 24000L) + partial;
		SaberState state = SaberComponents.state(stack);
		float since = level == null ? 100f : Math.max(0f, level.getGameTime() - state.toggledAt() + partial);
		float extension = SaberModelProperties.easedExtension(state.ignited(), since);
		float second = 1f;

		if (kind == Kind.STAFF) {
			StaffMode mode = SaberComponents.staffMode(stack);
			float modeSince = level == null ? 100f : Math.max(0f, level.getGameTime() - mode.toggledAt() + partial);
			second = SaberModelProperties.easedExtension(!mode.single(), modeSince);
		}

		int rgb = SaberComponents.config(stack).color().rgb();
		float scale = kind == Kind.SINGLE ? SaberModelProperties.bladeScale(stack) : 1f;
		return new Blade(stack, rgb, extension, Math.min(extension, second), since, state.ignited(), scale, time);
	}

	@Override
	public void submit(Blade blade, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
		if (blade == null || blade.extension() <= 0.001f && !(blade.lit() && blade.since() < 3f)) {
			return;
		}

		Matrix4f pose = new Matrix4f(poseStack.last().pose());
		Vector3f eye = GlowRibbon.eye(pose);
		boolean training = kind == Kind.TRAINING;
		float bodyRadius = (training ? TRAINING_BODY_RADIUS : BODY_RADIUS) * PX;
		float glowRadius = (training ? TRAINING_GLOW_RADIUS : GLOW_RADIUS) * PX;
		float glowStrength = training ? 0.55f : 1f;
		float flicker = flicker(blade.time());
		GlowRibbon.Profile body = body(blade.rgb());
		GlowRibbon.Profile glow = glow(blade.rgb());
		GlowRibbon.Profile flare = flare(blade.rgb());
		Segment[] segments = switch (kind) {
			case SINGLE -> new Segment[] {segment(SINGLE_BASE, 1f, SINGLE_LENGTH * blade.lengthScale(), blade.extension())};
			case TRAINING -> new Segment[] {segment(SINGLE_BASE, 1f, TRAINING_LENGTH, blade.extension())};
			case STAFF -> new Segment[] {segment(STAFF_TOP - 0.4f, 1f, STAFF_LENGTH, blade.extension()),
					segment(STAFF_BOTTOM + 0.4f, -1f, STAFF_LENGTH, blade.second())};
		};
		// The ignition flash at the emitter for the first ticks of lighting, and the flare at a tip still moving.
		float flash = blade.lit() ? Mth.clamp(1f - blade.since() / 3f, 0f, 1f) : 0f;

		collector.submitCustomGeometry(poseStack, GlowRenderTypes.glow(), (p, buffer) -> {
			for (Segment s : segments) {
				if (s.length() > 0.001f) {
					glowBeam(p, buffer, eye, s, glowRadius * (0.97f + 0.06f * flicker), glow, glowStrength * (0.9f + 0.1f * flicker), blade.time());
				}

				float moving = s.extension() > 0f && s.extension() < 1f ? 1f - s.extension() : 0f;

				if (moving > 0f) {
					GlowRibbon.disc(p, buffer, eye, s.tip(), glowRadius * 1.25f, flare, 0.85f * moving);
				}

				if (flash > 0f) {
					GlowRibbon.disc(p, buffer, eye, s.base(), glowRadius * 1.5f, flare, flash);
				}
			}
		});
		collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (p, buffer) -> {
			for (Segment s : segments) {
				if (s.length() > 0.001f) {
					GlowRibbon.capsule(p, buffer, eye, s.base(), s.tip(), bodyRadius, body, 1f);
				}
			}
		});

		for (int i = 0; i < segments.length; i++) {
			Segment s = segments[i];

			if (s.length() > 0.001f) {
				BladeTrails.recordAndDraw(collector, blade.stack(), i, firstPerson, pose.transformPosition(new Vector3f(s.base())),
						pose.transformPosition(new Vector3f(s.tip())), blade.rgb(), (firstPerson ? 0.75f : 1f) * glowStrength);
			}
		}
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		float r = BODY_RADIUS * PX;
		float low;
		float high;

		switch (kind) {
			case STAFF -> {
				low = (STAFF_BOTTOM - STAFF_LENGTH) * PX;
				high = (STAFF_TOP + STAFF_LENGTH) * PX;
			}
			case TRAINING -> {
				low = SINGLE_BASE * PX;
				high = (SINGLE_BASE + TRAINING_LENGTH) * PX;
			}
			default -> {
				low = SINGLE_BASE * PX;
				high = (SINGLE_BASE + SINGLE_LENGTH * LONGEST) * PX;
			}
		}

		for (float y : new float[] {low, high}) {
			output.accept(new Vector3f(0.5f - r, y, 0.5f - r));
			output.accept(new Vector3f(0.5f + r, y, 0.5f + r));
		}
	}

	/** One blade: from {@code base} along {@code direction} (+1 up, -1 down the model's y) for its drawn length. */
	private record Segment(Vector3f base, Vector3f tip, float length, float extension) {
	}

	private static Segment segment(float baseY, float direction, float fullLength, float extension) {
		float length = fullLength * extension;
		Vector3f base = new Vector3f(0.5f, baseY * PX, 0.5f);
		Vector3f tip = new Vector3f(0.5f, (baseY + direction * Math.max(length, 0.01f)) * PX, 0.5f);
		return new Segment(base, tip, length * PX, extension);
	}

	/** The glow along a blade, its brightness shimmering a little from point to point and frame to frame. */
	private static void glowBeam(PoseStack.Pose pose, VertexConsumer buffer, Vector3fc eye, Segment s, float radius, GlowRibbon.Profile glow,
			float strength, float time) {
		Vector3fc[] points = new Vector3fc[SHIMMER_POINTS];
		float[] radii = new float[SHIMMER_POINTS];
		float[] alphas = new float[SHIMMER_POINTS];

		for (int i = 0; i < SHIMMER_POINTS; i++) {
			float f = (float) i / (SHIMMER_POINTS - 1);
			points[i] = new Vector3f(s.base()).lerp(s.tip(), f);
			float shimmer = noise(i * 3.7f + time * 1.9f);
			radii[i] = radius * (1f + 0.04f * shimmer);
			alphas[i] = strength * (0.93f + 0.07f * shimmer);
		}

		GlowRibbon.beam(pose, buffer, eye, points, radii, alphas, glow, true, true);
	}

	/** The white core, a whisper of the blade colour, then the colour itself fading out at the edge. */
	private static GlowRibbon.Profile body(int rgb) {
		return new GlowRibbon.Profile(new float[] {0f, 0.34f, 0.5f, 0.68f, 1f}, new int[] {
				0xFF000000 | mix(0xFFFFFF, rgb, 0.03f),
				0xFF000000 | mix(0xFFFFFF, rgb, 0.12f),
				0xF2000000 | mix(0xFFFFFF, rgb, 0.55f),
				0x9C000000 | rgb,
				rgb});
	}

	/** The glow: the blade colour, strongest at the centre line, gone at the edge. */
	private static GlowRibbon.Profile glow(int rgb) {
		return new GlowRibbon.Profile(new float[] {0f, 0.16f, 0.38f, 0.66f, 1f}, new int[] {
				0xA8000000 | rgb,
				0x80000000 | rgb,
				0x3C000000 | rgb,
				0x12000000 | rgb,
				rgb});
	}

	/** The ignition flash and the moving tip's flare: near white in the middle, the blade colour around it. */
	private static GlowRibbon.Profile flare(int rgb) {
		return new GlowRibbon.Profile(new float[] {0f, 0.25f, 0.6f, 1f}, new int[] {
				0xB0000000 | mix(0xFFFFFF, rgb, 0.3f),
				0x70000000 | rgb,
				0x20000000 | rgb,
				rgb});
	}

	/** A faint, film-like flicker, -1 to 1: smooth noise at about thirty steps a second plus a slow breath. */
	private static float flicker(float time) {
		return Mth.clamp(0.75f * noise(time * 1.5f) + 0.25f * Mth.sin(time * 0.31f), -1f, 1f);
	}

	/** Smooth value noise, -1 to 1. */
	private static float noise(float x) {
		int i = Mth.floor(x);
		float f = x - i;
		float t = f * f * (3f - 2f * f);
		return Mth.lerp(t, hash(i), hash(i + 1));
	}

	private static float hash(int n) {
		int h = n * 0x27D4EB2D;
		h ^= h >>> 15;
		h *= 0x2C1B3C6D;
		h ^= h >>> 12;
		return (h & 0xFFFF) / 32767.5f - 1f;
	}

	/** {@code a} blended toward {@code b} by {@code t} (RGB, no alpha). */
	private static int mix(int a, int b, float t) {
		int r = Math.round(Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
		return r << 16 | g << 8 | bl;
	}
}
