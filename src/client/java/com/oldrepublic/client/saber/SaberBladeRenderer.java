package com.oldrepublic.client.saber;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

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
 * <p>Coordinates are the item model's (sixteenths of a block, the hilt along y at x 8, z 8), so the item model's display
 * transforms place the blade with the hilt. Each emitter's item model says where its blade leaves it (MILESTONE_7.md
 * 10.4): the mouth, a lean toward +x away from the hilt's axis (the Penitent's curved neck, 8.2) and side blades along
 * +z and -z (the Warden's quillons, 8.1, spread across the view in first person, which looks at the thumb side, +x).
 * Quillons light and go out with their blade, a third as wide and by default {@value #QUILLON_LENGTH} units long; they are
 * only drawn (the hit tests know nothing of them) and leave no trail. On a saberstaff the lower end is the upper one
 * turned half round the z axis through the staff's centre (10.3).
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

	/**
	 * The item model's {@code model} entry, for example {@code {"type": "oldrepublic:saber_blade", "kind": "single",
	 * "first_person": true, "mouth": [8, 5.92], "quillons": {"y": 5.26, "z": 2.95, "length": 3.5}}} (MILESTONE_7.md 10.4).
	 * Only {@code kind} is required; without the others the blade leaves the default mouth ({@link Mouth#SINGLE}, a
	 * staff's {@link Mouth#STAFF}) straight along the hilt, with no quillons, as before.
	 *
	 * @param mouth    where the blade leaves the emitter, [x, y] in model units; on a staff the upper blade's, the lower one
	 *                 leaving from (16 - x, 16 - y) the opposite way
	 * @param tilt     the blade's lean from the hilt's axis, degrees about the model's z axis, positive toward +x
	 * @param quillons the emitter's side blades, if it has them
	 */
	record Unbaked(Kind kind, boolean firstPerson, Optional<Mouth> mouth, float tilt, Optional<Quillons> quillons)
			implements SpecialModelRenderer.Unbaked<Blade> {
		static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Kind.CODEC.fieldOf("kind").forGetter(Unbaked::kind),
				Codec.BOOL.optionalFieldOf("first_person", false).forGetter(Unbaked::firstPerson),
				Mouth.CODEC.optionalFieldOf("mouth").forGetter(Unbaked::mouth),
				Codec.FLOAT.optionalFieldOf("tilt", 0f).forGetter(Unbaked::tilt),
				Quillons.CODEC.optionalFieldOf("quillons").forGetter(Unbaked::quillons)
		).apply(i, Unbaked::new));

		@Override
		public SpecialModelRenderer<Blade> bake(SpecialModelRenderer.BakingContext context) {
			return new SaberBladeRenderer(kind, firstPerson, mouth.orElse(kind == Kind.STAFF ? Mouth.STAFF : Mouth.SINGLE), tilt,
					quillons.orElse(null));
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}

	/** Where a blade leaves its emitter, in model units (always at z 8, the plane of the hilt's axis). */
	record Mouth(float x, float y) {
		/** A single saber's blade by default: a little inside the emitter's top (y 6), so no gap shows. */
		static final Mouth SINGLE = new Mouth(8f, SINGLE_BASE);
		/** A staff's upper blade by default: a little inside the upper emitter's top. */
		static final Mouth STAFF = new Mouth(8f, STAFF_TOP - 0.4f);
		static final Codec<Mouth> CODEC = Codec.FLOAT.listOf().comapFlatMap(Mouth::parse, m -> List.of(m.x(), m.y()));

		private static DataResult<Mouth> parse(List<Float> xy) {
			if (xy.size() != 2) {
				return DataResult.error(() -> "A blade mouth is [x, y], not " + xy);
			}

			return DataResult.success(new Mouth(xy.get(0), xy.get(1)));
		}
	}

	/**
	 * Two side blades (the Warden's crossguard, MILESTONE_7.md 8.1): from (8, {@code y}, 8 + {@code z}) pointing +z and from
	 * (8, {@code y}, 8 - {@code z}) pointing -z, {@code length} model units long; on a staff, a second pair with the lower
	 * blade at height 16 - {@code y}, pointing +z and -z too (the half turn about z that makes the lower end keeps z).
	 */
	record Quillons(float y, float z, float length) {
		static final Codec<Quillons> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.FLOAT.fieldOf("y").forGetter(Quillons::y),
				Codec.FLOAT.fieldOf("z").forGetter(Quillons::z),
				Codec.FLOAT.optionalFieldOf("length", QUILLON_LENGTH).forGetter(Quillons::length)
		).apply(i, Quillons::new));
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
	static final float PX = 1f / 16f;
	/** Where a single saber's blade starts by default: a little inside the emitter (its top is at y 6), so no gap shows. */
	private static final float SINGLE_BASE = 5.6f;
	private static final float SINGLE_LENGTH = 25.4f;
	private static final float TRAINING_LENGTH = 20.4f;
	/** The top of the staff's hilt (model y) before the emitters had their own mouths, and each staff blade's length. */
	private static final float STAFF_TOP = 13.714f;
	private static final float STAFF_LENGTH = 17.9f;
	private static final float LONGEST = SaberModelProperties.LONG_BLADE;
	/** The core and its coloured edge, and the glow around it: half widths in model units. */
	static final float BODY_RADIUS = 1.05f;
	static final float GLOW_RADIUS = 3.1f;
	private static final float TRAINING_BODY_RADIUS = 0.85f;
	private static final float TRAINING_GLOW_RADIUS = 2.1f;
	/** Quillons (8.1): their length when the item model gives none, and their width against their blade's. */
	private static final float QUILLON_LENGTH = 3.5f;
	private static final float QUILLON_WIDTH = 1f / 3f;
	/** Points along a blade for its shimmer. */
	private static final int SHIMMER_POINTS = 7;

	private final Kind kind;
	private final boolean firstPerson;
	private final Mouth mouth;
	/** Which way the (upper) blade points in the model's x-y plane: straight up the hilt, or leaning by its tilt. */
	private final float dirX;
	private final float dirY;
	private final @Nullable Quillons quillons;

	SaberBladeRenderer(Kind kind, boolean firstPerson, Mouth mouth, float tilt, @Nullable Quillons quillons) {
		this.kind = kind;
		this.firstPerson = firstPerson;
		this.mouth = mouth;
		this.dirX = Mth.sin(tilt * Mth.DEG_TO_RAD);
		this.dirY = Mth.cos(tilt * Mth.DEG_TO_RAD);
		this.quillons = quillons;
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
		Segment[] segments = segments(blade.extension(), blade.second(), blade.lengthScale());
		// The ignition flash at the emitter for the first ticks of lighting, and the flare at a tip still moving.
		float flash = blade.lit() ? Mth.clamp(1f - blade.since() / 3f, 0f, 1f) : 0f;

		collector.submitCustomGeometry(poseStack, GlowRenderTypes.glow(), (p, buffer) -> {
			for (Segment s : segments) {
				float width = s.quillon() ? QUILLON_WIDTH : 1f;

				if (s.length() > 0.001f) {
					glowBeam(p, buffer, eye, s, width * glowRadius * (0.97f + 0.06f * flicker), glow, glowStrength * (0.9f + 0.1f * flicker),
							blade.time());
				}

				float moving = s.extension() > 0f && s.extension() < 1f ? 1f - s.extension() : 0f;

				if (moving > 0f) {
					GlowRibbon.disc(p, buffer, eye, s.tip(), width * glowRadius * 1.25f, flare, 0.85f * moving);
				}

				// The main blade's flash covers its quillons' ports too.
				if (flash > 0f && !s.quillon()) {
					GlowRibbon.disc(p, buffer, eye, s.base(), glowRadius * 1.5f, flare, flash);
				}
			}
		});
		collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (p, buffer) -> {
			for (Segment s : segments) {
				if (s.length() > 0.001f) {
					GlowRibbon.capsule(p, buffer, eye, s.base(), s.tip(), (s.quillon() ? QUILLON_WIDTH : 1f) * bodyRadius, body, 1f);
				}
			}
		});

		// The main blades leave a trail (blade 0, a staff's lower blade 1); the quillons do not.
		for (int i = 0; i < segments.length; i++) {
			Segment s = segments[i];

			if (!s.quillon() && s.length() > 0.001f) {
				BladeTrails.recordAndDraw(collector, blade.stack(), i, firstPerson, pose.transformPosition(new Vector3f(s.base())),
						pose.transformPosition(new Vector3f(s.tip())), blade.rgb(), (firstPerson ? 0.75f : 1f) * glowStrength);
			}
		}
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		float r = BODY_RADIUS * PX;

		// Every blade fully out, a single saber's at the longest length.
		for (Segment s : segments(1f, 1f, kind == Kind.SINGLE ? LONGEST : 1f)) {
			for (Vector3f end : new Vector3f[] {s.base(), s.tip()}) {
				output.accept(new Vector3f(end).sub(r, r, r));
				output.accept(new Vector3f(end).add(r, r, r));
			}
		}
	}

	/**
	 * One blade, in blocks of the item model's space: from {@code base} to {@code tip}, its drawn length, how far out it is,
	 * and whether it is a quillon.
	 */
	private record Segment(Vector3f base, Vector3f tip, float length, float extension, boolean quillon) {
	}

	/**
	 * The blades at these extensions: the main blade (a staff's upper one, then its lower one leaving from the point opposite
	 * its mouth through the staff's centre, the other way), then any quillons, each pair with its own blade.
	 */
	private Segment[] segments(float extension, float second, float lengthScale) {
		boolean staff = kind == Kind.STAFF;
		float length = switch (kind) {
			case SINGLE -> SINGLE_LENGTH * lengthScale;
			case TRAINING -> TRAINING_LENGTH;
			case STAFF -> STAFF_LENGTH;
		};
		Segment[] out = new Segment[(staff ? 2 : 1) * (quillons == null ? 1 : 3)];
		int n = 0;
		out[n++] = segment(new Vector3f(mouth.x(), mouth.y(), 8f), new Vector3f(dirX, dirY, 0f), length, extension, false);

		if (staff) {
			out[n++] = segment(new Vector3f(16f - mouth.x(), 16f - mouth.y(), 8f), new Vector3f(-dirX, -dirY, 0f), length, second, false);
		}

		if (quillons != null) {
			float q = quillons.length();

			for (float side : new float[] {1f, -1f}) {
				Vector3f along = new Vector3f(0f, 0f, side);
				out[n++] = segment(new Vector3f(8f, quillons.y(), 8f + side * quillons.z()), along, q, extension, true);

				if (staff) {
					out[n++] = segment(new Vector3f(8f, 16f - quillons.y(), 8f + side * quillons.z()), along, q, second, true);
				}
			}
		}

		return out;
	}

	/** A blade from {@code from} (model units) along the unit vector {@code along}, {@code fullLength} long when fully out. */
	private static Segment segment(Vector3f from, Vector3f along, float fullLength, float extension, boolean quillon) {
		float length = fullLength * extension;
		Vector3f base = new Vector3f(from).mul(PX);
		Vector3f tip = new Vector3f(along).mul(Math.max(length, 0.01f)).add(from).mul(PX);
		return new Segment(base, tip, length * PX, extension, quillon);
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
	static GlowRibbon.Profile body(int rgb) {
		return new GlowRibbon.Profile(new float[] {0f, 0.34f, 0.5f, 0.68f, 1f}, new int[] {
				0xFF000000 | mix(0xFFFFFF, rgb, 0.03f),
				0xFF000000 | mix(0xFFFFFF, rgb, 0.12f),
				0xF2000000 | mix(0xFFFFFF, rgb, 0.55f),
				0x9C000000 | rgb,
				rgb});
	}

	/** The glow: the blade colour, strongest at the centre line, gone at the edge. */
	static GlowRibbon.Profile glow(int rgb) {
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
	static int mix(int a, int b, float t) {
		int r = Math.round(Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
		return r << 16 | g << 8 | bl;
	}
}
