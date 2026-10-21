package com.oldrepublic.client.force.energy;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The shape of one stroke of lightning: a main channel from its start to its end that zigzags at every scale (each piece
 * split again and again at a midpoint pushed aside, a little less each time, the way a real discharge looks), with forks
 * that leave it at an angle, thin out and die, some forking again. Points are kept relative to the start and tagged with
 * where along the main channel they hang, so a stroke can follow a moving hand and a moving target while it lasts.
 */
public final class Bolt {
	/**
	 * How a bolt is shaped.
	 *
	 * @param sway          the main channel's widest swing, as a share of its length
	 * @param rough         how much each finer zigzag keeps of the one before (about one half: jagged at every scale)
	 * @param piece         the length of the finest pieces, in blocks
	 * @param maxDepth      at most this many rounds of splitting (two to the power of it pieces)
	 * @param forkReach     a fork's length as a share of the channel's
	 * @param forkMaxLength the longest fork, in blocks
	 * @param subForkChance the chance a fork forks again
	 * @param taper         how much thinner the main channel ends than it starts (0 to 1)
	 */
	public record Style(float sway, float rough, float piece, int maxDepth, float forkReach, float forkMaxLength, float subForkChance, float taper) {
	}

	/** A bolt from the hands to a target several blocks off: a wide swing, long forks. */
	public static final Style CHANNEL = new Style(0.15f, 0.52f, 0.14f, 5, 0.3f, 2.2f, 0.5f, 0.3f);
	/** A leap from one creature to the next (Chain Lightning). */
	public static final Style CHAIN = new Style(0.13f, 0.52f, 0.13f, 5, 0.25f, 1.4f, 0.35f, 0.2f);
	/** Short arcs crawling over a body, along the ground, or between the fingers: tight and kinked. */
	public static final Style CRAWL = new Style(0.2f, 0.58f, 0.07f, 4, 0.35f, 0.5f, 0.2f, 0.4f);
	/** A feeler probing the air from a charging hand. */
	public static final Style FEELER = new Style(0.22f, 0.55f, 0.08f, 4, 0.4f, 0.45f, 0.35f, 0.75f);

	/** One unbroken line of a bolt. */
	static final class Strand {
		/** Points relative to the bolt's start, three floats each. */
		final float[] xyz;
		/** Where on the main channel each point hangs (0 its start, 1 its end), for following the ends as they move. */
		final float[] along;
		/** Width at each point; 1 is the main channel's at its start. */
		final float[] width;
		/** Brightness; 1 is the main channel's. */
		final float bright;

		Strand(float[] xyz, float[] along, float[] width, float bright) {
			this.xyz = xyz;
			this.along = along;
			this.width = width;
			this.bright = bright;
		}

		int size() {
			return along.length;
		}
	}

	/** Forks leave the channel at 20 to 52 degrees. */
	private static final float FORK_ANGLE_MIN = 0.35f;
	private static final float FORK_ANGLE_MAX = 0.9f;

	final Strand[] strands;

	private Bolt(Strand[] strands) {
		this.strands = strands;
	}

	/** A new bolt from the origin to {@code delta}, with {@code forks} forks off its main channel. */
	public static Bolt build(RandomSource random, Vector3fc delta, Style style, int forks) {
		float length = delta.length();
		List<Strand> strands = new ArrayList<>(1 + forks * 2);
		List<Vector3f> main = zigzag(random, new Vector3f(), new Vector3f(delta), style.sway() * length, style.rough(), depth(length, style.piece(),
				style.maxDepth()));
		int n = main.size();
		float[] along = new float[n];
		float[] width = new float[n];

		for (int i = 0; i < n; i++) {
			float t = (float) i / (n - 1);
			along[i] = t;
			width[i] = 1f - style.taper() * t;
		}

		strands.add(strand(main, along, width, 1f));
		int first = Math.max(1, Math.round(n * 0.06f));
		int last = Math.max(first, Math.round(n * 0.86f));

		for (int f = 0; f < forks && n > 2; f++) {
			int i = first + random.nextInt(last - first + 1);
			float t = along[i];
			float reach = Mth.clamp(length * style.forkReach() * (0.45f + 0.8f * random.nextFloat()) * (1f - 0.5f * t), 0.1f, style.forkMaxLength());
			Vector3f start = main.get(i);
			Vector3f direction = forkDirection(random, tangent(main, i));
			List<Vector3f> fork = zigzag(random, start, new Vector3f(direction).mul(reach).add(start), 0.2f * reach, style.rough(),
					depth(reach, style.piece() * 0.9f, 4));
			float rootWidth = width[i] * (0.5f + 0.15f * random.nextFloat());
			Strand strand = taper(fork, t, rootWidth, 0.78f);
			strands.add(strand);

			if (fork.size() >= 4 && random.nextFloat() < style.subForkChance()) {
				int j = fork.size() / 4 + random.nextInt(Math.max(1, fork.size() / 2));
				float subReach = reach * (0.3f + 0.25f * random.nextFloat());
				Vector3f subStart = fork.get(j);
				Vector3f subDirection = forkDirection(random, tangent(fork, j));
				List<Vector3f> sub = zigzag(random, subStart, new Vector3f(subDirection).mul(subReach).add(subStart), 0.22f * subReach, style.rough(),
						depth(subReach, style.piece() * 0.8f, 3));
				strands.add(taper(sub, t, strand.width[j] * 0.7f, 0.58f));
			}
		}

		return new Bolt(strands.toArray(new Strand[0]));
	}

	/** Rounds of splitting for a line of {@code length}: pieces of about {@code piece} blocks, from 2 to {@code max} rounds. */
	private static int depth(float length, float piece, int max) {
		int depth = 2;

		while (depth < max && length / (1 << depth) > piece) {
			depth++;
		}

		return depth;
	}

	/**
	 * A line from {@code a} to {@code b} split {@code depth} times: each piece's midpoint pushed aside at random (about
	 * {@code swing} blocks the first time, {@code rough} times less each round after).
	 */
	private static List<Vector3f> zigzag(RandomSource random, Vector3f a, Vector3f b, float swing, float rough, int depth) {
		List<Vector3f> points = new ArrayList<>(2);
		points.add(a);
		points.add(b);
		float amount = swing;

		for (int round = 0; round < depth; round++) {
			List<Vector3f> next = new ArrayList<>(points.size() * 2);

			for (int i = 0; i + 1 < points.size(); i++) {
				Vector3f p = points.get(i);
				Vector3f q = points.get(i + 1);
				Vector3f mid = new Vector3f(p).add(q).mul(0.5f);
				float push = Mth.clamp((float) random.nextGaussian(), -2.2f, 2.2f) * amount;
				next.add(p);
				next.add(mid.add(across(random, new Vector3f(q).sub(p)).mul(push)));
			}

			next.add(points.get(points.size() - 1));
			points = next;
			amount *= rough;
		}

		return points;
	}

	/** A fork's direction: {@code tangent} turned aside by 20 to 52 degrees, any way round. */
	private static Vector3f forkDirection(RandomSource random, Vector3f tangent) {
		float angle = Mth.lerp(random.nextFloat(), FORK_ANGLE_MIN, FORK_ANGLE_MAX);
		return new Vector3f(tangent).mul(Mth.cos(angle)).add(across(random, tangent).mul(Mth.sin(angle))).normalize();
	}

	/** The direction of the line at point {@code i}. */
	private static Vector3f tangent(List<Vector3f> points, int i) {
		Vector3f t = new Vector3f(points.get(Math.min(points.size() - 1, i + 1))).sub(points.get(Math.max(0, i - 1)));
		return t.lengthSquared() < 1.0E-10f ? new Vector3f(0f, 1f, 0f) : t.normalize();
	}

	/** A random unit vector square to {@code axis}. */
	private static Vector3f across(RandomSource random, Vector3f axis) {
		Vector3f v = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian());
		float axisLength = axis.lengthSquared();

		if (axisLength > 1.0E-10f) {
			v.sub(new Vector3f(axis).mul(v.dot(axis) / axisLength));
		}

		if (v.lengthSquared() < 1.0E-10f) {
			v.set(Math.abs(axis.y) < 0.9f ? 0f : 1f, Math.abs(axis.y) < 0.9f ? 1f : 0f, 0f);
			v.sub(new Vector3f(axis).mul(axisLength > 1.0E-10f ? v.dot(axis) / axisLength : 0f));
		}

		return v.normalize();
	}

	/** A fork hanging at {@code along} on the channel, {@code rootWidth} wide where it leaves and thinning to a thread. */
	private static Strand taper(List<Vector3f> points, float along, float rootWidth, float bright) {
		int n = points.size();
		float[] alongs = new float[n];
		float[] widths = new float[n];

		for (int i = 0; i < n; i++) {
			float t = (float) i / (n - 1);
			alongs[i] = along;
			widths[i] = rootWidth * (1f - 0.88f * t);
		}

		return strand(points, alongs, widths, bright);
	}

	private static Strand strand(List<Vector3f> points, float[] along, float[] width, float bright) {
		float[] xyz = new float[points.size() * 3];

		for (int i = 0; i < points.size(); i++) {
			Vector3f p = points.get(i);
			xyz[i * 3] = p.x;
			xyz[i * 3 + 1] = p.y;
			xyz[i * 3 + 2] = p.z;
		}

		return new Strand(xyz, along, width, bright);
	}
}
