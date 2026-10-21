package com.oldrepublic.client.force.kinetic;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.force.particle.ForceParticle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A billowing cloud of dust ({@code kinetic_dust}): it bursts out at the speed it was thrown, swells quickly and then more
 * slowly, turns as it rolls, loses its speed to the air and settles or drifts up, thinning away. Tinted with the ground's
 * colour and lit by the world (no glow), so it is pale by day and dim at night like real dust.
 */
public final class DustCloudParticle extends ForceParticle {
	private float from = 0.3f;
	private float to = 0.9f;
	private float spin;
	private float lift;
	/** Where in its life (0 to 1) it is thickest; it thins away after. */
	private float hold = 0.25f;
	/** The point a swirling mote circles in toward, or null. */
	private @Nullable Vec3 swirlCentre;
	private float swirlTurn;
	private float swirlPull;
	/** Age at which it stops dead (time held still, Force Stasis), or -1. */
	private int freezeAt = -1;
	/** Ticks it may still hang frozen: a watchdog, as the thaw normally comes first. */
	private int frozenFor;
	/** Whether it hung frozen this tick (then it does not grow between ticks either). */
	private boolean still;

	public DustCloudParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
		super(level, x, y, z, dx, dy, dz, sprites);
		color(0.62f, 0.57f, 0.49f);
		alpha(0.6f);
		size(0.3f, 0.9f);
		life(40);
		drag(0.86f);
		fall(0f, true);
		randomFrame();
		this.roll = random.nextFloat() * Mth.TWO_PI;
		this.oRoll = this.roll;
		this.spin = (random.nextFloat() - 0.5f) * 0.06f;
	}

	@Override
	public ForceParticle size(float start, float end) {
		this.from = start;
		this.to = end;
		return super.size(start, end);
	}

	/** Turn per tick, in radians (it slows as the cloud does). */
	public DustCloudParticle spin(float radians) {
		this.spin = radians;
		return this;
	}

	/** A steady drift per tick once the throw is spent: up for fine dust, down for heavy grit. */
	public DustCloudParticle lift(float perTick) {
		this.lift = perTick;
		return this;
	}

	/** Where in its life (0 to 1) it stops thickening and starts to thin away. */
	public DustCloudParticle hold(float t) {
		this.hold = Mth.clamp(t, 0.05f, 0.9f);
		return this;
	}

	/**
	 * Swirls in toward {@code centre}: each tick it turns {@code turn} radians round the vertical through it and closes
	 * {@code pull} (a fraction) of the way in, as dust drawn into a vortex.
	 */
	public DustCloudParticle swirl(Vec3 centre, float turn, float pull) {
		this.swirlCentre = centre;
		this.swirlTurn = turn;
		this.swirlPull = pull;
		fall(0f, false);
		return this;
	}

	/**
	 * Stops dead {@code after} ticks from now, where it is, as it is (size, turn and thickness held), and hangs there for at
	 * most {@code ticks} unless {@link #thaw} lets it go first: dust caught in a moment held still.
	 */
	public DustCloudParticle freeze(int after, int ticks) {
		this.freezeAt = age + Math.max(0, after);
		this.frozenFor = Math.max(1, ticks);
		return this;
	}

	/** Lets a frozen cloud go: it sinks a little and thins away over what is left of its life. */
	public void thaw() {
		if (freezeAt < 0) {
			return;
		}

		freezeAt = -1;
		frozenFor = 0;
		this.yd = -0.004;
		this.spin = (random.nextFloat() - 0.5f) * 0.02f;
	}

	/** Whether it hangs frozen now, or will. */
	public boolean frozen() {
		return freezeAt >= 0 && frozenFor > 0 && isAlive();
	}

	@Override
	public void tick() {
		this.oRoll = this.roll;

		this.still = false;

		if (freezeAt >= 0 && age >= freezeAt) {
			if (frozenFor-- > 0) {
				this.still = true;
				// Held still: no motion, no turn and no ageing, so it neither grows nor thins.
				this.xo = this.x;
				this.yo = this.y;
				this.zo = this.z;
				this.xd = 0;
				this.yd = 0;
				this.zd = 0;
				return;
			}

			thaw();
		}

		if (swirlCentre != null) {
			double ox = x - swirlCentre.x;
			double oy = y - swirlCentre.y;
			double oz = z - swirlCentre.z;
			double c = Math.cos(swirlTurn);
			double s = Math.sin(swirlTurn);
			double keep = 1 - swirlPull;
			this.xd = swirlCentre.x + (ox * c - oz * s) * keep - x;
			this.yd = swirlCentre.y + oy * keep - y;
			this.zd = swirlCentre.z + (ox * s + oz * c) * keep - z;
		}

		super.tick();

		if (!isAlive()) {
			return;
		}

		this.roll += spin;
		this.spin *= 0.96f;
		this.yd += lift * (1f - friction);
	}

	@Override
	public float getQuadSize(float partialTick) {
		float t = Mth.clamp((age + (still ? 0f : partialTick)) / (float) lifetime, 0f, 1f);
		// Swells fast while the throw lasts, then slowly.
		float grown = 1f - (1f - t) * (1f - t) * (1f - t);
		return Mth.lerp(grown, from, to);
	}

	@Override
	protected float fade(float t) {
		// Thick within a couple of ticks, however long it lasts: a burst of dust is there at once.
		float in = Math.min(1f, (age + 1) / 2.5f);

		if (t <= hold) {
			return in;
		}

		float out = 1f - (t - hold) / (1f - hold);
		return in * out * (0.4f + 0.6f * out);
	}
}
