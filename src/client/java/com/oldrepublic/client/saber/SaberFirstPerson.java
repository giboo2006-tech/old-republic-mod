package com.oldrepublic.client.saber;

import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.saber.LightsaberItem;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberStaffItem;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * The lightsaber in the first-person hand, posed here instead of by the vanilla held-item transforms (the hand mixin
 * {@code ItemInHandRendererSaberMixin} hands every saber to {@link #apply}; the item models' first-person display is the
 * identity, so this pose is the whole of it). A held item in vanilla only bobs and dips; a saber is held and swung:
 * <ul>
 * <li>at rest, the hilt low on the right and the blade angled up across the view toward the crosshair (the staff a little
 * flatter, so both of its blades show);</li>
 * <li>each attack is a fast cut through the crosshair from wherever the blade is: a forehand from up on the right down to the
 * lower left, and, while the blade is still down there from the last cut, a backhand back up to the right, so a run of
 * attacks flows from one cut into the next; the staff twirls a half turn instead, on through a run of attacks;</li>
 * <li>after a cut the blade holds its follow-through for a moment, then eases back to rest, slowly, so the return never
 * reads as a second cut;</li>
 * <li>guarding (the use key) brings the blade across the body, easing in and out;</li>
 * <li>the techniques ({@link SaberAnimations}): the finisher chops down from overhead, the heavy strike draws back over
 * the shoulder and sweeps wide, the lunge thrusts along the look, the form salute raises the blade before the face.</li>
 * </ul>
 * A saber in the left hand is the mirror image. Each pose is where the grip is in the hand's view space (x right, y up,
 * z toward the viewer; the hand is drawn with its own fixed field of view) and which way the blade points; poses in
 * between are blended with an eased position and a turn along the shortest arc, so every motion is smooth. The saber is
 * only lowered while it is put away (the vanilla re-equip); the vanilla dip after each attack is left out, since the swing
 * itself is the motion, and a saber drawn into the hand rises into place.
 */
public final class SaberFirstPerson {
	/** The saber's size in the hand against its item model; the staff, twice as long, is held a little smaller. */
	private static final float SCALE = 0.6f;
	private static final float STAFF_SCALE = 0.5f;
	/**
	 * Where the hand closes on the hilt along the model's Y axis, in model pixels: the single hilt's grip, the staff's middle
	 * (also where a flying saber spins, {@link ThrownSaberFx}).
	 */
	static final float GRIP_Y = 1.8f;
	static final float STAFF_GRIP_Y = 8f;
	/** How far down a saber being put away or drawn sits (the vanilla re-equip's depth), and how long a drawn one takes to rise. */
	private static final float LOWERED = 0.6f;
	private static final long RAISE_NANOS = 220_000_000L;
	/** A hand not drawn for this long starts over: it rises again and its next swing is a forehand. */
	private static final long FORGET_NANOS = 500_000_000L;
	/**
	 * How long a finished cut holds its follow-through, and the time constant (seconds) of its ease back to rest after that:
	 * slow enough that at the saber's attack pace (about 0.6 s) the blade is still mostly where the last cut left it.
	 */
	private static final long HOLD_NANOS = 220_000_000L;
	private static final float SETTLE = 0.3f;
	/**
	 * A cut that starts while more than this much of the last cut's end still shows follows on from it (the other cut); about
	 * 0.8 s after the last attack, it is a forehand again.
	 */
	private static final float CHAIN = 0.4f;
	/** How much of the swing the staff's half turn takes; it is still for the rest. */
	private static final float SPIN_END = 0.7f;
	/** The time constant of the guard easing in and out, in seconds. */
	private static final float GUARD_EASE = 0.06f;

	/** Where the grip is (view space) and which way the blade points (a unit vector). */
	private record Pose(Vector3fc grip, Vector3fc dir) {
	}

	/** A pose at a point of a motion, 0 to 1; a null pose is where the motion starts (and, for a technique, ends). */
	private record Key(float at, @Nullable Pose pose) {
	}

	private static final Pose REST = pose(0.50f, -0.40f, -0.78f, dir(112f, 22f));
	private static final Pose STAFF_REST = pose(0.32f, -0.28f, -0.84f, dir(128f, 22f));
	private static final Pose GUARD = pose(0.36f, -0.26f, -0.70f, dir(150f, 25f));
	/** The staff across the body, both blades in view. */
	private static final Pose STAFF_GUARD = pose(0.16f, -0.22f, -0.80f, dir(165f, 20f));
	/** Where the staff's grip is pushed during a twirl: forward and toward the middle. */
	private static final Vector3fc SPIN_GRIP = new Vector3f(0.16f, -0.24f, -0.92f);
	private static final Pose LUNGE = pose(0.25f, -0.26f, -0.92f, dir(118f, 72f));
	private static final Pose SALUTE = pose(0.05f, -0.30f, -0.62f, dir(90f, 5f));
	/** The heavy strike's wind-up: drawn back over the right shoulder, the blade leaning back past the edge of the view. */
	private static final Pose WINDUP = pose(0.58f, -0.10f, -0.70f, dir(58f, -12f));

	/**
	 * Forehand: from wherever the blade is, cocked back up on the right, then down through the crosshair (the blade reaching
	 * into the view) to the lower left, where it is held. The first key (null) is the pose the cut starts from.
	 */
	private static final Key[] FOREHAND = {
			new Key(0f, null),
			new Key(0.14f, pose(0.56f, -0.20f, -0.70f, dir(75f, -12f))),
			new Key(0.30f, pose(0.40f, -0.24f, -0.86f, dir(125f, 38f))),
			new Key(0.40f, pose(0.24f, -0.27f, -0.90f, dir(165f, 50f))),
			new Key(0.52f, pose(0.10f, -0.30f, -0.88f, dir(195f, 45f))),
			new Key(0.66f, pose(0.04f, -0.31f, -0.84f, dir(205f, 38f))),
			new Key(1f, pose(0.05f, -0.32f, -0.82f, dir(203f, 34f)))
	};
	/** Backhand: from the forehand's end low on the left, up through the crosshair to the upper right, where it is held. */
	private static final Key[] BACKHAND = {
			new Key(0f, null),
			new Key(0.14f, pose(0.02f, -0.33f, -0.80f, dir(212f, 25f))),
			new Key(0.30f, pose(0.14f, -0.28f, -0.88f, dir(175f, 48f))),
			new Key(0.40f, pose(0.30f, -0.25f, -0.90f, dir(125f, 52f))),
			new Key(0.52f, pose(0.44f, -0.23f, -0.86f, dir(80f, 42f))),
			new Key(0.66f, pose(0.52f, -0.25f, -0.80f, dir(55f, 25f))),
			new Key(1f, pose(0.52f, -0.26f, -0.80f, dir(58f, 24f)))
	};
	/** The finisher: raised overhead, the blade hanging back, then chopped straight down the middle and held. */
	private static final Key[] CHOP = {
			new Key(0f, null),
			new Key(0.22f, pose(0.18f, 0.08f, -0.60f, chop(-40f))),
			new Key(0.36f, pose(0.14f, -0.02f, -0.72f, chop(10f))),
			new Key(0.46f, pose(0.11f, -0.12f, -0.80f, chop(60f))),
			new Key(0.56f, pose(0.10f, -0.20f, -0.80f, chop(105f))),
			new Key(0.66f, pose(0.10f, -0.24f, -0.78f, chop(125f))),
			new Key(0.82f, pose(0.10f, -0.24f, -0.78f, chop(125f))),
			new Key(1f, null)
	};
	/** The heavy strike: from the wind-up, a wide flat sweep right to left, held at the end. */
	private static final Key[] HEAVY = {
			new Key(0f, WINDUP),
			new Key(0.30f, pose(0.40f, -0.22f, -0.84f, dir(118f, 30f))),
			new Key(0.45f, pose(0.12f, -0.32f, -0.84f, dir(182f, 28f))),
			new Key(0.60f, pose(-0.05f, -0.38f, -0.80f, dir(208f, 20f))),
			new Key(0.80f, pose(-0.05f, -0.38f, -0.80f, dir(210f, 18f))),
			new Key(1f, null)
	};

	/** The attacks: a single saber's two cuts, and the staff's half turn. */
	private enum Cut {
		FOREHAND, BACKHAND, SPIN
	}

	/** What a hand remembers between frames. */
	private static final class Hand {
		/** When this hand last drew a saber, and when that saber came into it. */
		long lastNanos;
		long drawnNanos;
		@Nullable SaberConfig config;
		/** The swing progress last frame. */
		float lastSwing;
		/** The cut under way or last made (null: none since the saber was drawn), and the pose it started from. */
		@Nullable Cut cut;
		Pose from = REST;
		/** Where the last cut ended and when (0 while one is under way). */
		Pose end = REST;
		long endNanos;
		/** The pose the swing, or the ease back from it, gave last frame (before the guard and the techniques). */
		Pose swingPose = REST;
		/** The staff is held the other way round (each half turn swaps its ends; it looks the same either way). */
		boolean flipped;
		/** How far into the guard, eased, 0 to 1. */
		float guard;
	}

	private static final Hand MAIN = new Hand();
	private static final Hand OFF = new Hand();

	/** True when {@code stack} is drawn by {@link #apply} in {@code player}'s first-person hand (not while scoping, where nothing is drawn). */
	public static boolean handles(AbstractClientPlayer player, ItemStack stack) {
		return stack.getItem() instanceof LightsaberItem && !player.isScoping();
	}

	/**
	 * Moves {@code poseStack} from the first-person hand's space to the saber's item model space for this frame.
	 *
	 * @param swing the hand's swing, 0 to 1 (0 when it is not swinging)
	 * @param equip the vanilla re-equip, 0 (up) to 1 (lowered)
	 */
	public static void apply(AbstractClientPlayer player, InteractionHand hand, HumanoidArm arm, ItemStack stack, float partialTick, float swing,
			float equip, PoseStack poseStack) {
		boolean staff = stack.getItem() instanceof SaberStaffItem;
		Hand state = hand == InteractionHand.MAIN_HAND ? MAIN : OFF;
		long now = System.nanoTime();
		SaberConfig config = SaberComponents.config(stack);

		if (state.lastNanos == 0L || now - state.lastNanos > FORGET_NANOS || !config.equals(state.config)) {
			// Drawn into the hand (or back in view): rise into place, at rest, guard down, the next swing a forehand.
			state.drawnNanos = now;
			state.config = config;
			state.lastSwing = 0f;
			state.cut = null;
			state.endNanos = 0L;
			state.flipped = false;
			state.swingPose = staff ? STAFF_REST : REST;
			state.guard = 0f;
		}

		float seconds = state.lastNanos == 0L ? 0f : Math.min(0.25f, (now - state.lastNanos) / 1.0E9f);
		state.lastNanos = now;
		Pose rest = staff ? flip(STAFF_REST, state.flipped) : REST;
		Pose pose = swing(state, staff, swing, now, rest);
		state.swingPose = pose;

		boolean guarding = player.isUsingItem() && player.getUsedItemHand() == hand;
		state.guard += ((guarding ? 1f : 0f) - state.guard) * (1f - (float) Math.exp(-seconds / GUARD_EASE));
		pose = mix(pose, staff ? flip(STAFF_GUARD, state.flipped) : GUARD, smooth(state.guard));

		SaberPose technique = SaberAnimations.pose(player, partialTick);

		if (technique != null && technique.leftArm() == (arm == HumanoidArm.LEFT)) {
			pose = technique(pose, technique);
		}

		// Lowered only while it is put away (the hand already holds something else) and while it rises after being drawn.
		boolean puttingAway = player.getItemInHand(hand).getItem() != stack.getItem();
		float raise = Mth.clamp((now - state.drawnNanos) / (float) RAISE_NANOS, 0f, 1f);
		float lowered = Math.max(puttingAway ? equip : 0f, 1f - smooth(raise));

		Vector3f grip = new Vector3f(pose.grip());
		Vector3f dir = new Vector3f(pose.dir());

		if (arm == HumanoidArm.LEFT) {
			grip.x = -grip.x;
			dir.x = -dir.x;
		}

		float scale = staff ? STAFF_SCALE : SCALE;
		poseStack.translate(grip.x, grip.y - LOWERED * lowered, grip.z);
		// The blade (model +Y) along the pose's direction by the shortest turn; the hilt's side details (model +X) toward the eye.
		poseStack.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, dir.x, dir.y, dir.z).rotateY(-Mth.HALF_PI));
		poseStack.scale(scale, scale, scale);
		// The item model is drawn about its centre (8, 8, 8); bring the grip there.
		poseStack.translate(0f, (8f - (staff ? STAFF_GRIP_Y : GRIP_Y)) / 16f, 0f);
	}

	/**
	 * The pose the hand's swing gives this frame: the cut under way; or, between cuts, the last cut's end held a moment and
	 * then eased back to {@code rest}. A new swing (the progress starting over) begins a cut from the pose last frame: the
	 * other cut while the blade is still near the last one's end, else a forehand (the staff always twirls).
	 */
	private static Pose swing(Hand state, boolean staff, float swing, long now, Pose rest) {
		if (swing > 0f && (state.lastSwing <= 0f || swing < state.lastSwing - 0.05f)) {
			boolean follows = state.cut != null && (state.lastSwing > 0f || settled(now - state.endNanos) > CHAIN);
			state.cut = staff ? Cut.SPIN : follows && state.cut == Cut.FOREHAND ? Cut.BACKHAND : Cut.FOREHAND;
			state.from = state.swingPose;
			state.endNanos = 0L;
		}

		boolean ended = swing <= 0f && state.lastSwing > 0f;
		state.lastSwing = swing;

		if (state.cut == null) {
			return rest;
		}

		if (swing > 0f) {
			return cut(state.cut, state.from, swing);
		}

		if (ended || state.endNanos == 0L) {
			state.end = cut(state.cut, state.from, 1f);
			state.endNanos = now;

			if (staff) {
				// Which way round the staff now is: the end of the turn nearer the way it rests.
				state.flipped = state.end.dir().dot(STAFF_REST.dir()) < 0f;
				rest = flip(STAFF_REST, state.flipped);
			}
		}

		return mix(rest, state.end, settled(now - state.endNanos));
	}

	/** How much of a finished cut's end still shows {@code nanos} after it ended: all of it for the hold, then less and less. */
	private static float settled(long nanos) {
		return nanos <= HOLD_NANOS ? 1f : (float) Math.exp(-(nanos - HOLD_NANOS) / 1.0E9 / SETTLE);
	}

	/** Cut {@code cut} at {@code progress}, started from {@code from}. */
	private static Pose cut(Cut cut, Pose from, float progress) {
		return switch (cut) {
			case FOREHAND -> sample(FOREHAND, progress, from);
			case BACKHAND -> sample(BACKHAND, progress, from);
			case SPIN -> spin(from, progress);
		};
	}

	/**
	 * The staff's twirl: a half turn from {@code from} about the axis square to the staff that is nearest the line of sight
	 * (so it spins almost flat across the view and ends along the same line, its ends swapped), clockwise on screen, easing
	 * in and out over {@value #SPIN_END} of the swing, the grip pushed forward and in as it turns. A run of attacks twirls it
	 * on round the same way.
	 */
	private static Pose spin(Pose from, float progress) {
		Vector3fc d = from.dir();
		Vector3f axis = new Vector3f(-d.x() * d.z(), -d.y() * d.z(), 1f - d.z() * d.z());

		if (axis.lengthSquared() < 1.0E-4f) {
			// The staff pointing along the line of sight (never in these poses): turn it about the vertical.
			axis.set(0f, 1f, 0f);
		}

		float t = Mth.clamp(progress / SPIN_END, 0f, 1f);
		Vector3f dir = new Quaternionf().rotationAxis(-Mth.PI * smooth(t), axis.normalize()).transform(new Vector3f(d));
		Vector3f grip = new Vector3f(from.grip()).lerp(SPIN_GRIP, 0.6f * Mth.sin(t * Mth.PI));
		return new Pose(grip, dir.normalize());
	}

	/** {@code pose} with the blade the other way along the same line when {@code flipped} (for the staff, the same look). */
	private static Pose flip(Pose pose, boolean flipped) {
		return flipped ? new Pose(pose.grip(), new Vector3f(pose.dir()).negate()) : pose;
	}

	/** {@code pose} with the technique pose blended over it; the motions start from and come back to {@code pose}. */
	private static Pose technique(Pose pose, SaberPose technique) {
		float amount = technique.amount();
		float progress = technique.progress();

		return switch (technique.kind()) {
			case FINISHER -> mix(pose, sample(CHOP, progress, pose), amount);
			case HEAVY_WINDUP -> mix(pose, WINDUP, amount);
			case HEAVY_STRIKE -> mix(pose, sample(HEAVY, progress, pose), amount);
			case LUNGE, THROW -> mix(pose, LUNGE, amount);
			case SALUTE -> mix(pose, SALUTE, amount * Math.min(1f, Mth.sin(progress * Mth.PI) * 1.6f));
			// The catch and the duelists' guard and stagger states are not the player's in first person.
			default -> pose;
		};
	}

	/** The motion {@code keys} at {@code progress}: eased from each key to the next, a null key being {@code start}. */
	private static Pose sample(Key[] keys, float progress, Pose start) {
		for (int i = 1; i < keys.length; i++) {
			Key to = keys[i];

			if (progress <= to.at() || i == keys.length - 1) {
				Key from = keys[i - 1];
				float t = Mth.clamp((progress - from.at()) / Math.max(1.0E-4f, to.at() - from.at()), 0f, 1f);
				return mix(orStart(from, start), orStart(to, start), smooth(t));
			}
		}

		return start;
	}

	private static Pose orStart(Key key, Pose start) {
		return key.pose() != null ? key.pose() : start;
	}

	/** {@code a} blended toward {@code b}: the grip moved in a line, the blade turned along the shortest arc. */
	private static Pose mix(Pose a, Pose b, float t) {
		if (t <= 0f) {
			return a;
		}

		if (t >= 1f) {
			return b;
		}

		return new Pose(new Vector3f(a.grip()).lerp(b.grip(), t), slerp(a.dir(), b.dir(), t));
	}

	private static Vector3f slerp(Vector3fc a, Vector3fc b, float t) {
		float cos = Mth.clamp(a.dot(b), -1f, 1f);
		float angle = (float) Math.acos(cos);
		float sin = Mth.sin(angle);

		if (sin < 1.0E-3f) {
			// The same way (or, never in these poses, opposite): no arc to follow.
			return new Vector3f(t < 0.5f ? a : b);
		}

		float wa = Mth.sin((1f - t) * angle) / sin;
		float wb = Mth.sin(t * angle) / sin;
		return new Vector3f(a).mul(wa).add(b.x() * wb, b.y() * wb, b.z() * wb).normalize();
	}

	private static float smooth(float t) {
		return t * t * (3f - 2f * t);
	}

	private static Pose pose(float x, float y, float z, Vector3fc dir) {
		return new Pose(new Vector3f(x, y, z), dir);
	}

	/**
	 * A blade direction in the hand's view space: {@code angle} degrees around the view from the right (90 straight up,
	 * 180 left), tilted {@code tilt} degrees away from the eye (negative: back toward it).
	 */
	private static Vector3fc dir(float angle, float tilt) {
		double a = Math.toRadians(angle);
		double t = Math.toRadians(tilt);
		return new Vector3f((float) (Math.cos(a) * Math.cos(t)), (float) (Math.sin(a) * Math.cos(t)), (float) -Math.sin(t)).normalize();
	}

	/** A direction in the finisher's chop: {@code angle} degrees down from straight up, through forward, a little to the right. */
	private static Vector3fc chop(float angle) {
		double a = Math.toRadians(angle);
		return new Vector3f(0.05f, (float) Math.cos(a), (float) -Math.sin(a)).normalize();
	}

	private SaberFirstPerson() {
	}
}
