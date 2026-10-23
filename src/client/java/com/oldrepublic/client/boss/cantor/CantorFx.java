package com.oldrepublic.client.boss.cantor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.oldrepublic.boss.cantor.CantorChoir;
import com.oldrepublic.boss.cantor.CantorEffects;
import com.oldrepublic.boss.cantor.FirstCantor;
import com.oldrepublic.client.enemy.hymn.FoeArcs;
import com.oldrepublic.client.enemy.hymn.FoeFx;
import com.oldrepublic.client.enemy.hymn.FoeGlow;
import com.oldrepublic.client.enemy.hymn.FoeLoops;
import com.oldrepublic.client.enemy.hymn.FoeTimers;
import com.oldrepublic.client.enemy.hymn.FoeWatch;
import com.oldrepublic.client.enemy.hymn.StormTellFx;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.hud.Banners;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the {@code first_cantor} event (spec MILESTONE_5.md 12.3, 15.1, 15.3, 15.4). The
 * server plays the one-shot {@code boss.cantor.*} sounds; this draws the looks, shows the phase
 * banners and keeps the loops ({@code boss.cantor.beam} while the beam sweeps, {@code boss.cantor.ring}
 * while a ring sings, {@code boss.cantor.choir} for each singing echo, {@code boss.cantor.engine}
 * through the third phase):
 *
 * <ul>
 * <li>Phases: the keystone stirs the engine; he rises out of the floor in drifting light; banners for
 * the Choir, the Verses and the Long Hymn; the engine wakes; a wipe or reset quiets everything.</li>
 * <li>The verse beam: 24 {@code tomb_dust} lifting along its coming path over the tell (a tell),
 * then the rotating beam of pale light itself, drawn in the world at a body's height with a faint
 * sheet showing the band it sweeps (a jump does not clear it).</li>
 * <li>The slam: 16 {@code gather_dust} lifting under him over the tell (a tell), then the blow.</li>
 * <li>Chain lightning: an arc per link.</li>
 * <li>Stormwood: cracks spreading as roots push up (20 {@code tomb_dust} along them over the tell, a
 * tell), the grip, and roots bursting at a freed player's feet. Mire: pools of black fog creeping
 * over the floor (a dark soft-edged stain drawn on it, with wisps). Basalt: {@link StormTellFx} on
 * each mark (a tell), and a bolt from the vault onto it.</li>
 * <li>The rings: 12 {@code vent_grit} trembling over the stones over the tell (a tell); while one
 * sings, 2 {@code gather_dust} and one {@code air_ripple} per 5 ticks in uneven patches.</li>
 * <li>The silencing points: a slow column of motes over each open point; the channel draws light
 * into it; a silenced point bursts. The shield: pale motes drifting from the echoes to his robes.
 * He unravels into 120 {@code spectral_wisp} over 60 ticks.</li>
 * <li>He and each echo shed 2 {@code spectral_wisp} a tick; a falling echo fades into wisps.</li>
 * </ul>
 */
public final class CantorFx {
	/** The verse beam's pale gold-white. */
	private static final int BEAM_RGB = 0xFFF0D2;
	private static final int BEAM_CORE_RGB = 0xFFFDF4;
	/** His chain lightning: a cold pale blue. */
	private static final int CHAIN_RGB = 0xBFD6FF;
	/** Basalt's bolt from the vault. */
	private static final int STRIKE_RGB = 0xCFE0FF;
	/** The beam's width (raid.beamWidth's default; the event carries its reach and arc, not its width). */
	private static final float BEAM_WIDTH = 1.2f;
	/** The band the beam sweeps, about its middle (blocks): 0.3 to 1.9 over the floor. */
	private static final float BAND_HALF = 0.8f;
	/** The beam's middle is this far over the floor. */
	private static final double BAND_MIDDLE = 1.1;
	/** Tell lengths the events do not carry (the raid balance defaults): the slam, the roots, a basalt mark, a ring. */
	private static final int SLAM_TELL_TICKS = 20;
	private static final int ROOTS_TELL_TICKS = 30;
	private static final int MARK_TELL_TICKS = 40;
	private static final int RING_TELL_TICKS = 30;
	/** The loops are heard within the undercroft. */
	private static final double LOOP_RANGE = 48.0;
	private static final double CHOIR_LOOP_RANGE = 32.0;
	private static final String BEAM_LOOP = "boss.cantor.beam";
	private static final String RING_LOOP = "boss.cantor.ring";
	private static final String ENGINE_LOOP = "boss.cantor.engine";
	/** Root fibres: dark, twisted wood. */
	private static final BlockParticleOption ROOT_BITS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MANGROVE_ROOTS.defaultBlockState());
	private static final int FOG_RGB = 0x0B0812;
	private static final int FOG_FADE_TICKS = 20;
	private static final int FOG_SEGMENTS = 18;

	/** The verse beam sweeping now. */
	private static final class Beam {
		final Vec3 origin;
		final Vec3 start;
		final double length;
		final double arc;
		final int ticks;
		final long since;

		Beam(Vec3 origin, Vec3 start, double length, double arc, int ticks, long since) {
			this.origin = origin;
			this.start = start;
			this.length = length;
			this.arc = arc;
			this.ticks = Math.max(1, ticks);
			this.since = since;
		}

		/** Its direction at {@code time} (ticks with the partial tick). */
		Vec3 direction(double time) {
			double done = Mth.clamp((time - since) / ticks, 0.0, 1.0);
			return turn(start, arc * done);
		}
	}

	/** A floor ring singing now: a few fixed patches of its stones where the grit dances. */
	private static final class Ring {
		final Vec3 centre;
		final int ticks;
		final long since;
		final Vec3[] patches;

		Ring(Vec3 centre, double radius, int ticks, long since) {
			this.centre = centre;
			this.ticks = Math.max(1, ticks);
			this.since = since;
			int count = 3 + FoeFx.RANDOM.nextInt(2);
			this.patches = new Vec3[count];

			for (int i = 0; i < count; i++) {
				double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
				double r = radius * (0.25 + FoeFx.RANDOM.nextDouble() * 0.6);
				patches[i] = centre.add(Math.cos(angle) * r, 0.05, Math.sin(angle) * r);
			}
		}
	}

	/** A pool of Mire's black fog: an uneven stain on the floor. */
	private static final class Pool {
		final Vec3 centre;
		final double radius;
		final int ticks;
		final long since;
		final float[] reach = new float[FOG_SEGMENTS];

		Pool(Vec3 centre, double radius, int ticks, long since) {
			this.centre = centre;
			this.radius = radius;
			this.ticks = Math.max(1, ticks);
			this.since = since;

			for (int i = 0; i < FOG_SEGMENTS; i++) {
				reach[i] = 0.8f + FoeFx.RANDOM.nextFloat() * 0.35f;
			}
		}

		/** 0 to 1: spreading out, then thinning away at the end. */
		float presence(double time) {
			double age = time - since;
			return (float) Math.min(Mth.clamp(age / FOG_FADE_TICKS, 0, 1), Mth.clamp((ticks - age) / FOG_FADE_TICKS, 0, 1));
		}
	}

	private static @Nullable Beam beam;
	private static @Nullable Ring ring;
	private static final List<Pool> POOLS = new ArrayList<>();
	private static final List<Vec3> OPEN_POINTS = new ArrayList<>();
	/** The choir echoes seen singing, and where: an echo that falls fades into wisps there. */
	private static final Map<Integer, Vec3> CHOIR = new HashMap<>();
	private static long ticks;
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}

		initialised = true;
		ClientEffects.register(CantorEffects.EVENT, CantorFx::onEvent);
		FoeWatch.watch(FirstCantor.class, 1, CantorFx::watchCantor);
		FoeWatch.watch(CantorChoir.class, 1, CantorFx::watchChoir);
		ClientTickEvents.END_CLIENT_TICK.register(CantorFx::tick);
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(CantorFx::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> quiet());
	}

	/** Whether the verse beam sweeps now (the client tests read it). */
	public static boolean beamActive() {
		return beam != null;
	}

	/** Whether a floor ring sings now (the client tests read it). */
	public static boolean ringActive() {
		return ring != null;
	}

	/** Mire pools showing now (the client tests read it). */
	public static int pools() {
		return POOLS.size();
	}

	/** A direction (x, z) turned by {@code angle} radians, as the server turns the beam (CantorEffects). */
	static Vec3 turn(Vec3 dir, double angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		return new Vec3(dir.x * cos - dir.z * sin, 0, dir.x * sin + dir.z * cos);
	}

	private static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 at = p.pos();

		if (p.variant() == CantorEffects.PHASE) {
			phase(mc, level, at, entity, Math.round(p.intensity()), (int) Math.round(p.dir().x));
			return;
		}

		if (p.variant() == CantorEffects.UNRAVEL) {
			// The engine falls silent: whatever plays stops, wherever the player stands.
			quiet();
		}

		if (!Vfx.inRange(at)) {
			return;
		}

		switch (p.variant()) {
			case CantorEffects.BEAM_TELL -> beamTell(at, p.dir(), p.intensity());
			case CantorEffects.BEAM -> beamSweep(at, p.dir(), p.intensity());
			case CantorEffects.SLAM_TELL -> slamTell(at, Math.max(1f, p.intensity()), entity);
			case CantorEffects.SLAM -> slam(mc, level, at, Math.max(1f, p.intensity()));
			case CantorEffects.CHAIN -> chain(level, at, p.dir());
			case CantorEffects.ROOTS_TELL -> rootsTell(at, Math.max(1f, p.intensity()));
			case CantorEffects.ROOTS -> roots(mc, level, at, p.intensity(), (int) Math.round(p.dir().x));
			case CantorEffects.FOG -> fog(level, at, Math.max(1f, p.intensity()), (int) Math.round(p.dir().x));
			case CantorEffects.MARK -> StormTellFx.tell(at, Math.max(0.5f, p.intensity()), MARK_TELL_TICKS);
			case CantorEffects.STRIKE -> strike(mc, level, at, Math.max(0.5f, p.intensity()));
			case CantorEffects.RING_TELL -> ringTell(at, Math.max(1f, p.intensity()));
			case CantorEffects.RING -> ringSings(at, Math.max(1f, p.intensity()), (int) Math.round(p.dir().x));
			case CantorEffects.SILENCE_POINT -> silencePoint(level, at, p.intensity());
			case CantorEffects.SILENCED -> silenced(mc, level, at);
			case CantorEffects.SHIELD -> shield(level, at, p.dir());
			case CantorEffects.UNRAVEL -> unravel(at, entity, (int) Math.round(p.dir().x));
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- phases

	private static void phase(Minecraft mc, ClientLevel level, Vec3 at, @Nullable Entity entity, int phase, int length) {
		Component title = Component.translatable("boss.oldrepublic.cantor.name");

		switch (phase) {
			case 0 -> Banners.show(Component.translatable("boss.oldrepublic.cantor.banner.keystone"),
					Component.translatable("boss.oldrepublic.cantor.banner.keystone.sub"), UiColors.BRASS_LIGHT);
			case 1 -> Banners.show(title, Component.translatable("boss.oldrepublic.cantor.banner.choir"), UiColors.HOLO_BRIGHT);
			case 2 -> Banners.show(title, Component.translatable("boss.oldrepublic.cantor.banner.verses"), UiColors.AMBER);
			case 3 -> Banners.show(title, Component.translatable("boss.oldrepublic.cantor.banner.hymn"), UiColors.DANGER);
			default -> quiet();
		}

		if (!Vfx.inRange(at)) {
			return;
		}

		switch (phase) {
			case 0 -> {
				// The keystone turns: the engine stirs below, grit coughs up from the vents and dust sifts down.
				FoeFx.shake(mc, at, 24, 0.2f);

				for (int i = 0, n = Vfx.count(12); i < n; i++) {
					Vec3 vent = at.add(FoeFx.jitter(6), 0.05, FoeFx.jitter(6));
					FoeFx.add(level, FoeFx.ventGrit(), vent, new Vec3(FoeFx.jitter(0.02), 0.08 + FoeFx.RANDOM.nextDouble() * 0.06, FoeFx.jitter(0.02)));
				}

				for (int i = 0, n = Vfx.count(10); i < n; i++) {
					FoeFx.add(level, FoeFx.tombDust(), at.add(FoeFx.jitter(7), 4 + FoeFx.RANDOM.nextDouble() * 2, FoeFx.jitter(7)), new Vec3(0, -0.03, 0));
				}
			}
			case 1 -> {
				// He rises out of the engine through the floor in drifting light.
				Vec3 floor = at.subtract(0, 0.75, 0);
				FoeFx.shake(mc, at, 24, 0.35f);

				for (int i = 0, n = Vfx.count(16); i < n; i++) {
					double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
					double speed = 0.05 + FoeFx.RANDOM.nextDouble() * 0.08;
					FoeFx.add(level, FoeFx.tombDust(), floor.add(Math.cos(angle) * 0.6, 0.05, Math.sin(angle) * 0.6),
							new Vec3(Math.cos(angle) * speed, 0.08 + FoeFx.RANDOM.nextDouble() * 0.08, Math.sin(angle) * speed));
				}

				FoeTimers.spread(Vfx.count(40), Math.max(1, length), entity, (lvl, count, age) -> {
					for (int i = 0; i < count; i++) {
						double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
						double r = 0.3 + FoeFx.RANDOM.nextDouble() * 0.7;
						FoeFx.add(lvl, FoeFx.spectralWisp(), floor.add(Math.cos(angle) * r, 0.05, Math.sin(angle) * r),
								new Vec3(0, 0.06 + FoeFx.RANDOM.nextDouble() * 0.06, 0));
					}
				});
			}
			case 2 -> {
				for (int i = 0, n = Vfx.count(20); i < n; i++) {
					FoeFx.add(level, FoeFx.spectralWisp(), at.add(FoeFx.jitter(0.6), 0.5 + FoeFx.RANDOM.nextDouble() * 2.4, FoeFx.jitter(0.6)),
							FoeFx.jitterVec(0.12));
				}
			}
			case 3 -> {
				// The engine wakes: the hall shudders and grit spits from vents across the floor, a few at a time.
				FoeFx.shake(mc, at, 32, 0.6f);
				int vents = 5 + FoeFx.RANDOM.nextInt(2);

				for (int v = 0; v < vents; v++) {
					double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
					double r = 3 + FoeFx.RANDOM.nextDouble() * 8;
					Vec3 vent = at.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);

					for (int i = 0, n = Vfx.count(4); i < n; i++) {
						FoeFx.add(level, FoeFx.ventGrit(), vent.add(FoeFx.jitter(0.4), 0.05, FoeFx.jitter(0.4)),
								new Vec3(FoeFx.jitter(0.03), 0.12 + FoeFx.RANDOM.nextDouble() * 0.1, FoeFx.jitter(0.03)));
					}

					FoeFx.add(level, FoeFx.airRipple(), vent.add(0, 0.6, 0), Vec3.ZERO);
				}
			}
			default -> {
			}
		}
	}

	/** A wipe, a reset, the unravelling or a disconnect: the beam, the rings, the pools and the points all stop. */
	private static void quiet() {
		beam = null;
		ring = null;
		POOLS.clear();
		OPEN_POINTS.clear();
		FoeLoops.stop(BEAM_LOOP);
		FoeLoops.stop(RING_LOOP);
	}

	// ---------------------------------------------------------------- the verse beam

	/** Dust lifting along the beam's coming path over its tell (dir.y = the tell's ticks). */
	private static void beamTell(Vec3 origin, Vec3 shape, float degrees) {
		Vec3 flat = new Vec3(shape.x, 0, shape.z);
		double length = Math.max(2.0, flat.length());
		Vec3 start = flat.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : flat.normalize();
		double arc = Math.toRadians(degrees);
		int tell = Math.max(1, (int) Math.round(shape.y));
		double floorY = origin.y - BAND_MIDDLE;

		FoeTimers.spread(Vfx.tellCount(24), tell, null, (lvl, count, age) -> {
			for (int i = 0; i < count; i++) {
				Vec3 dir = turn(start, arc * FoeFx.RANDOM.nextDouble());
				double r = 1.5 + FoeFx.RANDOM.nextDouble() * (length - 1.5);
				Vec3 at = new Vec3(origin.x + dir.x * r, floorY + 0.05, origin.z + dir.z * r);
				FoeFx.add(lvl, FoeFx.tombDust(), at, new Vec3(FoeFx.jitter(0.01), 0.04 + FoeFx.RANDOM.nextDouble() * 0.05, FoeFx.jitter(0.01)));
			}
		});
	}

	/** The sweep begins (dir.y = its ticks): the beam is drawn turning, and its hum plays while it does. */
	private static void beamSweep(Vec3 origin, Vec3 shape, float degrees) {
		Vec3 flat = new Vec3(shape.x, 0, shape.z);
		Vec3 start = flat.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : flat.normalize();
		beam = new Beam(origin, start, Math.max(2.0, flat.length()), Math.toRadians(degrees), (int) Math.round(shape.y), ticks);
		FoeLoops.start(BEAM_LOOP, ModSounds.BOSS_CANTOR_BEAM, SoundSource.HOSTILE, null, origin, 1f, 1f, LOOP_RANGE, CantorFx::beamActive);
	}

	// ---------------------------------------------------------------- the second phase

	/** Dust lifting under him over the slam's tell. */
	private static void slamTell(Vec3 floor, float radius, @Nullable Entity cantor) {
		FoeTimers.spread(Vfx.tellCount(16), SLAM_TELL_TICKS, cantor, (lvl, count, age) -> {
			for (int i = 0; i < count; i++) {
				double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
				double r = Math.sqrt(FoeFx.RANDOM.nextDouble()) * radius * 0.85;
				FoeFx.add(lvl, FoeFx.gatherDust(), floor.add(Math.cos(angle) * r, 0.05, Math.sin(angle) * r),
						new Vec3(0, 0.04 + FoeFx.RANDOM.nextDouble() * 0.05, 0));
			}
		});
	}

	/** The slam lands: the floor's dust thrown out in uneven clusters, the air shoved, the hall jolted. */
	private static void slam(Minecraft mc, ClientLevel level, Vec3 floor, float radius) {
		ParticleOptions ground = FoeFx.ground(level, floor);
		int clusters = 5;

		for (int c = 0; c < clusters; c++) {
			double angle = (c + FoeFx.RANDOM.nextDouble() * 0.7) * Math.PI * 2 / clusters;

			for (int i = 0, n = Vfx.count(5); i < n; i++) {
				double a = angle + FoeFx.jitter(0.3);
				double speed = 0.15 + FoeFx.RANDOM.nextDouble() * 0.2;
				FoeFx.add(level, ground, floor.x + Math.cos(a) * 0.8, floor.y + 0.1, floor.z + Math.sin(a) * 0.8,
						Math.cos(a) * speed, 0.12 + FoeFx.RANDOM.nextDouble() * 0.12, Math.sin(a) * speed);
			}
		}

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			double a = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double r = radius * (0.4 + FoeFx.RANDOM.nextDouble() * 0.5);
			FoeFx.add(level, FoeFx.airRipple(), floor.add(Math.cos(a) * r, 0.5, Math.sin(a) * r), Vec3.ZERO);
		}

		FoeFx.shake(mc, floor, radius + 10, 0.6f);
	}

	/** One link of his chain lightning (dir = the whole way from the previous link). */
	private static void chain(ClientLevel level, Vec3 chest, Vec3 fromPrevious) {
		Vec3 from = chest.subtract(fromPrevious);
		FoeArcs.arc(from, chest, CHAIN_RGB, 5, 0.15f, 2);

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			FoeFx.add(level, FoeFx.lightningSpark(), chest.add(FoeFx.jitterVec(0.25)), FoeFx.jitterVec(0.12));
		}
	}

	/** Stormwood's tell: cracks spreading out from the centre as the roots push up beneath them. */
	private static void rootsTell(Vec3 centre, float radius) {
		int cracks = 5 + FoeFx.RANDOM.nextInt(3);
		double[] angles = new double[cracks];

		for (int i = 0; i < cracks; i++) {
			angles[i] = (i + FoeFx.RANDOM.nextDouble() * 0.6) * Math.PI * 2 / cracks;
		}

		FoeTimers.spread(Vfx.tellCount(20), ROOTS_TELL_TICKS, null, (lvl, count, age) -> {
			double front = radius * Mth.clamp((age + 1) / (double) ROOTS_TELL_TICKS, 0.15, 1.0);

			for (int i = 0; i < count; i++) {
				int crack = FoeFx.RANDOM.nextInt(cracks);
				// The crack wanders: its angle bends a little with the distance.
				double r = front * (0.6 + FoeFx.RANDOM.nextDouble() * 0.4);
				double a = angles[crack] + Math.sin(r * 1.3 + crack) * 0.18;
				Vec3 at = centre.add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
				FoeFx.add(lvl, FoeFx.tombDust(), at, new Vec3(FoeFx.jitter(0.01), 0.03 + FoeFx.RANDOM.nextDouble() * 0.04, FoeFx.jitter(0.01)));
			}
		});
	}

	/** The roots grip (radius > 0): fibres burst up across the circle and keep writhing; radius 0: a freed player's roots break. */
	private static void roots(Minecraft mc, ClientLevel level, Vec3 at, float radius, int length) {
		if (radius <= 0f) {
			for (int i = 0, n = Vfx.count(10); i < n; i++) {
				FoeFx.add(level, ROOT_BITS, at.add(FoeFx.jitter(0.4), 0.1, FoeFx.jitter(0.4)),
						new Vec3(FoeFx.jitter(0.12), 0.15 + FoeFx.RANDOM.nextDouble() * 0.1, FoeFx.jitter(0.12)));
			}

			for (int i = 0, n = Vfx.count(4); i < n; i++) {
				FoeFx.add(level, FoeFx.tombDust(), at.add(FoeFx.jitter(0.4), 0.1, FoeFx.jitter(0.4)), new Vec3(0, 0.05, 0));
			}

			return;
		}

		for (int i = 0, n = Vfx.count(22); i < n; i++) {
			double a = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(FoeFx.RANDOM.nextDouble()) * radius;
			FoeFx.add(level, ROOT_BITS, at.add(Math.cos(a) * r, 0.05, Math.sin(a) * r),
					new Vec3(FoeFx.jitter(0.05), 0.12 + FoeFx.RANDOM.nextDouble() * 0.12, FoeFx.jitter(0.05)));
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			FoeFx.add(level, FoeFx.tombDust(), at.add(FoeFx.jitter(radius * 0.7), 0.1, FoeFx.jitter(radius * 0.7)), new Vec3(0, 0.06, 0));
		}

		FoeFx.shake(mc, at, radius + 6, 0.2f);

		// While they hold: a fibre or two writhing somewhere in the circle (a lasting moment: 1 per 2 ticks).
		FoeTimers.spread(Vfx.count(Math.max(1, length / 2)), Math.max(1, length), null, (lvl, count, age) -> {
			for (int i = 0; i < count; i++) {
				double a = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
				double r = Math.sqrt(FoeFx.RANDOM.nextDouble()) * radius;
				FoeFx.add(lvl, ROOT_BITS, at.add(Math.cos(a) * r, 0.05, Math.sin(a) * r), new Vec3(0, 0.08, 0));
			}
		});
	}

	/** A pool of Mire's fog begins to spread (intensity = its width, dir.x = its ticks). */
	private static void fog(ClientLevel level, Vec3 centre, float width, int length) {
		POOLS.removeIf(pool -> pool.centre.distanceToSqr(centre) < 0.25);
		POOLS.add(new Pool(centre, width / 2.0, Math.max(1, length), ticks));

		for (int i = 0, n = Vfx.count(14); i < n; i++) {
			double a = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double speed = 0.03 + FoeFx.RANDOM.nextDouble() * 0.04;
			FoeFx.add(level, FoeFx.shadowWisp(), centre.add(0, 0.1, 0), new Vec3(Math.cos(a) * speed, 0.005, Math.sin(a) * speed));
		}
	}

	/** Basalt's strike: the mark's static ends, a bolt drops from the vault, and the stone is scorched. */
	private static void strike(Minecraft mc, ClientLevel level, Vec3 at, float radius) {
		StormTellFx.end(at);
		FoeArcs.arc(at.add(FoeFx.jitter(0.6), 7, FoeFx.jitter(0.6)), at, STRIKE_RGB, 5, 0.32f, 3);
		ParticleOptions ground = FoeFx.ground(level, at);

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			double a = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double speed = 0.08 + FoeFx.RANDOM.nextDouble() * 0.12;
			FoeFx.add(level, ground, at.x, at.y + 0.1, at.z, Math.cos(a) * speed, 0.18 + FoeFx.RANDOM.nextDouble() * 0.12, Math.sin(a) * speed);
		}

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			FoeFx.add(level, FoeFx.lightningSpark(), at.add(FoeFx.jitter(radius * 0.5), 0.15, FoeFx.jitter(radius * 0.5)), FoeFx.jitterVec(0.15));
		}

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			FoeFx.add(level, ParticleTypes.SMOKE, at.add(FoeFx.jitter(radius * 0.4), 0.1, FoeFx.jitter(radius * 0.4)), new Vec3(0, 0.05, 0));
		}

		FoeFx.shake(mc, at, radius + 6, 0.25f);
	}

	// ---------------------------------------------------------------- the engine (the third phase)

	/** A ring's stones tremble before it sings: grit jittering over them, mostly toward the rim. */
	private static void ringTell(Vec3 centre, float radius) {
		FoeTimers.spread(Vfx.tellCount(12), RING_TELL_TICKS, null, (lvl, count, age) -> {
			for (int i = 0; i < count; i++) {
				double a = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
				double r = radius * (0.55 + 0.45 * Math.sqrt(FoeFx.RANDOM.nextDouble()));
				FoeFx.add(lvl, FoeFx.ventGrit(), centre.add(Math.cos(a) * r, 0.05, Math.sin(a) * r),
						new Vec3(FoeFx.jitter(0.03), 0.05 + FoeFx.RANDOM.nextDouble() * 0.04, FoeFx.jitter(0.03)));
			}
		});
	}

	/** A ring sings (dir.x = its ticks): the grit dances and the air shimmers over it, and its loop plays. */
	private static void ringSings(Vec3 centre, float radius, int ringTicks) {
		ring = new Ring(centre, radius, ringTicks, ticks);
		FoeLoops.start(RING_LOOP, ModSounds.BOSS_CANTOR_RING, SoundSource.HOSTILE, null, centre.add(0, 0.5, 0), 1f, 1f, LOOP_RANGE,
				CantorFx::ringActive);
	}

	/** A silencing point opens or its channel breaks (progress 0), or its channel goes on (progress up to 1). */
	private static void silencePoint(ClientLevel level, Vec3 point, float progress) {
		if (progress <= 0f) {
			if (OPEN_POINTS.stream().noneMatch(open -> open.distanceToSqr(point) < 0.25)) {
				OPEN_POINTS.add(point);
			}

			for (int i = 0, n = Vfx.count(6); i < n; i++) {
				FoeFx.add(level, FoeFx.lightMote(), point.add(FoeFx.jitter(0.3), 0.2 + FoeFx.RANDOM.nextDouble() * 0.6, FoeFx.jitter(0.3)),
						new Vec3(0, 0.04, 0));
			}

			return;
		}

		// The channel draws the song down into the point: more light the further it has gone.
		int count = Vfx.count(2 + Math.round(progress * 4));

		for (int i = 0; i < count; i++) {
			Vec3 from = point.add(FoeFx.jitter(0.9), 1.4 + FoeFx.RANDOM.nextDouble() * 0.6, FoeFx.jitter(0.9));
			FoeFx.add(level, FoeFx.spectralWisp(), from, point.add(0, 0.3, 0).subtract(from).scale(0.05));
		}
	}

	/** A point is silenced: it bursts, and his window opens. */
	private static void silenced(Minecraft mc, ClientLevel level, Vec3 point) {
		OPEN_POINTS.removeIf(open -> open.distanceToSqr(point) < 0.25);

		for (int i = 0, n = Vfx.count(24); i < n; i++) {
			double a = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double speed = 0.06 + FoeFx.RANDOM.nextDouble() * 0.08;
			FoeFx.add(level, FoeFx.spectralWisp(), point.add(0, 0.5 + FoeFx.RANDOM.nextDouble(), 0),
					new Vec3(Math.cos(a) * speed, 0.02 + FoeFx.RANDOM.nextDouble() * 0.04, Math.sin(a) * speed));
		}

		FoeFx.add(level, FoeFx.airRipple(), point.add(0, 1.0, 0), Vec3.ZERO);
		FoeFx.add(level, FoeFx.airRipple(), point.add(0, 0.4, 0), Vec3.ZERO);
		FoeFx.shake(mc, point, 12, 0.25f);
	}

	/** The choir shields him: pale motes drift from an echo's chest and settle on his robes (dir = the whole way). */
	private static void shield(ClientLevel level, Vec3 from, Vec3 way) {
		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			Vec3 start = from.add(way.scale(FoeFx.RANDOM.nextDouble() * 0.15)).add(FoeFx.jitterVec(0.2));
			FoeFx.add(level, FoeFx.spectralWisp(), start, way.scale(0.05).add(FoeFx.jitterVec(0.01)));
		}
	}

	/** He unravels (dir.x = its ticks): 120 wisps drifting off his body over the unravelling. */
	private static void unravel(Vec3 chest, @Nullable Entity cantor, int length) {
		FoeTimers.spread(Vfx.count(120), Math.max(1, length), null, (lvl, count, age) -> {
			Vec3 centre = cantor != null && !cantor.isRemoved() ? cantor.position().add(0, cantor.getBbHeight() * 0.55, 0) : chest;

			for (int i = 0; i < count; i++) {
				Vec3 at = centre.add(FoeFx.jitter(0.6), FoeFx.jitter(1.3), FoeFx.jitter(0.6));
				Vec3 out = at.subtract(centre).scale(0.04).add(0, 0.03 + FoeFx.RANDOM.nextDouble() * 0.03, 0);
				FoeFx.add(lvl, FoeFx.spectralWisp(), at, out);
			}
		});
	}

	// ---------------------------------------------------------------- watchers

	/** His robes shed light (2 wisps a tick); the engine's loop plays through the third phase. */
	private static void watchCantor(Minecraft mc, ClientLevel level, FirstCantor cantor) {
		if (cantor.phase() >= 3 && cantor.action() != FirstCantor.Action.UNRAVEL) {
			FoeLoops.start(ENGINE_LOOP, ModSounds.BOSS_CANTOR_ENGINE, SoundSource.HOSTILE, cantor, cantor.position(), 0.8f, 1f, LOOP_RANGE,
					() -> cantor.isAlive() && cantor.phase() >= 3 && cantor.action() != FirstCantor.Action.UNRAVEL);
		}

		if (!Vfx.inRange(cantor.position()) || cantor.action() == FirstCantor.Action.UNRAVEL) {
			return;
		}

		robeWisps(level, cantor, Vfx.count(2), 0.5, 0.55);
	}

	/** Each echo sheds light (2 wisps a tick, 1 every other tick while silenced) and sings its loop. */
	private static void watchChoir(Minecraft mc, ClientLevel level, CantorChoir echo) {
		CHOIR.put(echo.getId(), echo.position());

		if (!echo.isSilent()) {
			FoeLoops.start("boss.cantor.choir:" + echo.getId(), ModSounds.BOSS_CANTOR_CHOIR, SoundSource.HOSTILE, echo, echo.position(), 0.5f,
					0.92f + Math.floorMod(echo.getId(), 4) * 0.04f, CHOIR_LOOP_RANGE, () -> echo.isAlive() && !echo.isSilent());
		}

		if (!Vfx.inRange(echo.position()) || (echo.isSilent() && echo.tickCount % 2 == 0)) {
			return;
		}

		robeWisps(level, echo, echo.isSilent() ? 1 : Vfx.count(2), 0.3, 0.35);
	}

	/** Wisps leaving a spectral robe: mostly from the hem and the sleeves, drifting off and up. */
	private static void robeWisps(ClientLevel level, Entity figure, int count, double halfWidth, double sleeve) {
		double height = figure.getBbHeight();

		for (int i = 0; i < count; i++) {
			boolean hem = FoeFx.RANDOM.nextFloat() < 0.6f;
			double y = hem ? FoeFx.RANDOM.nextDouble() * height * 0.3 : height * (0.4 + FoeFx.RANDOM.nextDouble() * 0.3);
			double reach = hem ? halfWidth : sleeve;
			double a = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			Vec3 at = figure.position().add(Math.cos(a) * reach, y, Math.sin(a) * reach);
			FoeFx.add(level, FoeFx.spectralWisp(), at, new Vec3(Math.cos(a) * 0.01, 0.006 + FoeFx.RANDOM.nextDouble() * 0.01, Math.sin(a) * 0.01));
		}
	}

	// ---------------------------------------------------------------- ticking

	private static void tick(Minecraft mc) {
		ticks++;
		ClientLevel level = mc.level;

		if (level == null) {
			quiet();
			CHOIR.clear();
			return;
		}

		if (mc.isPaused()) {
			return;
		}

		fallenEchoes(level);
		Beam sweeping = beam;

		if (sweeping != null && ticks - sweeping.since > sweeping.ticks + 2) {
			beam = null;
		}

		Ring singing = ring;

		if (singing != null) {
			long age = ticks - singing.since;

			if (age > singing.ticks) {
				ring = null;
			} else if (age % 5 == 0 && Vfx.inRange(singing.centre)) {
				Vec3 patch = singing.patches[FoeFx.RANDOM.nextInt(singing.patches.length)];

				for (int i = 0, n = Vfx.tellCount(2); i < n; i++) {
					FoeFx.add(level, FoeFx.gatherDust(), patch.add(FoeFx.jitter(0.6), 0, FoeFx.jitter(0.6)),
							new Vec3(FoeFx.jitter(0.02), 0.06 + FoeFx.RANDOM.nextDouble() * 0.05, FoeFx.jitter(0.02)));
				}

				FoeFx.add(level, FoeFx.airRipple(), patch.add(FoeFx.jitter(0.4), 0.5, FoeFx.jitter(0.4)), Vec3.ZERO);
			}
		}

		for (Iterator<Pool> it = POOLS.iterator(); it.hasNext();) {
			Pool pool = it.next();

			if (ticks - pool.since > pool.ticks) {
				it.remove();
			} else if (Vfx.inRange(pool.centre)) {
				// The fog creeps: a dark wisp and a curl of smoke somewhere over it each tick.
				double a = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
				double r = Math.sqrt(FoeFx.RANDOM.nextDouble()) * pool.radius * 0.9;
				Vec3 at = pool.centre.add(Math.cos(a) * r, 0.08, Math.sin(a) * r);
				FoeFx.add(level, FoeFx.shadowWisp(), at, new Vec3(Math.cos(a) * 0.01, 0.004, Math.sin(a) * 0.01));

				if (Vfx.count(2) > 1) {
					FoeFx.add(level, ParticleTypes.SMOKE, at.add(FoeFx.jitter(0.5), 0, FoeFx.jitter(0.5)), new Vec3(0, 0.005, 0));
				}
			}
		}

		if (ticks % 10 == 0) {
			for (Vec3 point : OPEN_POINTS) {
				if (Vfx.inRange(point)) {
					for (int i = 0, n = Vfx.tellCount(2); i < n; i++) {
						FoeFx.add(level, FoeFx.lightMote(), point.add(FoeFx.jitter(0.25), 0.1 + FoeFx.RANDOM.nextDouble() * 0.4, FoeFx.jitter(0.25)),
								new Vec3(0, 0.035, 0));
					}
				}
			}
		}
	}

	/** An echo seen singing that is now falling: it fades into wisps where it stood. */
	private static void fallenEchoes(ClientLevel level) {
		for (Iterator<Map.Entry<Integer, Vec3>> it = CHOIR.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Vec3> seen = it.next();
			Entity entity = level.getEntity(seen.getKey());

			if (entity instanceof CantorChoir echo && echo.isAlive()) {
				continue;
			}

			it.remove();

			if (entity instanceof CantorChoir echo && echo.isDeadOrDying() && Vfx.inRange(echo.position())) {
				for (int i = 0, n = Vfx.count(20); i < n; i++) {
					FoeFx.add(level, FoeFx.spectralWisp(), echo.position().add(FoeFx.jitter(0.3), FoeFx.RANDOM.nextDouble() * 1.9, FoeFx.jitter(0.3)),
							new Vec3(FoeFx.jitter(0.03), 0.04 + FoeFx.RANDOM.nextDouble() * 0.04, FoeFx.jitter(0.03)));
				}
			}
		}
	}

	// ---------------------------------------------------------------- the world-drawn beam and fog

	private static void render(LevelRenderContext context) {
		Beam sweeping = beam;

		if ((sweeping == null || !Vfx.inRange(sweeping.origin)) && POOLS.isEmpty()) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		double time = ticks + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 cam = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);

		if (sweeping != null && Vfx.inRange(sweeping.origin)) {
			float flicker = Vfx.flashing() ? 0.9f + 0.1f * Mth.sin((float) time * 0.9f) : 1f;
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> drawBeam(pose, buffer, sweeping, time,
					flicker));
		}

		if (!POOLS.isEmpty()) {
			List<Pool> pools = List.copyOf(POOLS);
			context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
				for (Pool pool : pools) {
					if (Vfx.inRange(pool.centre)) {
						drawPool(pose, buffer, pool, pool.presence(time));
					}
				}
			});
		}

		poseStack.popPose();
	}

	/** The beam at its present angle: a soft glow, a bright core, a fading trail behind it, and the faint band it sweeps. */
	private static void drawBeam(PoseStack.Pose pose, VertexConsumer buffer, Beam b, double time, float flicker) {
		Vec3 dir = b.direction(time);
		Vec3 end = b.origin.add(dir.scale(b.length));
		FoeGlow.strip(pose, buffer, b.origin, end, BEAM_WIDTH, FoeGlow.argb(0.22f * flicker, BEAM_RGB));
		FoeGlow.strip(pose, buffer, b.origin, end, BEAM_WIDTH * 0.25f, FoeGlow.argb(0.85f * flicker, BEAM_CORE_RGB));

		// The trail: where it was a moment ago, fading.
		for (int k = 1; k <= 3; k++) {
			Vec3 past = b.direction(time - k * 1.5);
			FoeGlow.strip(pose, buffer, b.origin, b.origin.add(past.scale(b.length)), BEAM_WIDTH * 0.6f, FoeGlow.argb(0.1f * flicker / k, BEAM_RGB));
		}

		// The band it sweeps, a body's height: an upright sheet of faint light along it.
		Vec3 half = new Vec3(0, BAND_HALF, 0);
		FoeGlow.ribbon(pose, buffer, b.origin, end, half, FoeGlow.argb(0.06f * flicker, BEAM_RGB));
	}

	/** A pool of black fog: an uneven stain, darkest in the middle, its edge soft. */
	private static void drawPool(PoseStack.Pose pose, VertexConsumer buffer, Pool pool, float presence) {
		if (presence <= 0.01f) {
			return;
		}

		float x = (float) pool.centre.x;
		float y = (float) pool.centre.y + 0.03f;
		float z = (float) pool.centre.z;
		int core = FoeGlow.argb(0.55f * presence, FOG_RGB);
		int edge = FoeGlow.argb(0f, FOG_RGB);
		double radius = pool.radius * (0.4 + 0.6 * presence);

		for (int i = 0; i < FOG_SEGMENTS; i++) {
			int j = (i + 1) % FOG_SEGMENTS;
			double a0 = i * Math.PI * 2 / FOG_SEGMENTS;
			double a1 = j * Math.PI * 2 / FOG_SEGMENTS;
			float x0 = x + (float) (Math.cos(a0) * radius * pool.reach[i]);
			float z0 = z + (float) (Math.sin(a0) * radius * pool.reach[i]);
			float x1 = x + (float) (Math.cos(a1) * radius * pool.reach[j]);
			float z1 = z + (float) (Math.sin(a1) * radius * pool.reach[j]);
			// The filled-box type draws quads: the centre is doubled to make each slice a triangle (both windings).
			buffer.addVertex(pose, x, y, z).setColor(core);
			buffer.addVertex(pose, x0, y, z0).setColor(edge);
			buffer.addVertex(pose, x1, y, z1).setColor(edge);
			buffer.addVertex(pose, x, y, z).setColor(core);
			buffer.addVertex(pose, x, y, z).setColor(core);
			buffer.addVertex(pose, x1, y, z1).setColor(edge);
			buffer.addVertex(pose, x0, y, z0).setColor(edge);
			buffer.addVertex(pose, x, y, z).setColor(core);
		}
	}

	private CantorFx() {
	}
}
