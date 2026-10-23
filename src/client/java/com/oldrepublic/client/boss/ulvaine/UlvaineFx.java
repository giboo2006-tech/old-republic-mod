package com.oldrepublic.client.boss.ulvaine;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.ulvaine.DarthUlvaine;
import com.oldrepublic.boss.ulvaine.HymnResonator;
import com.oldrepublic.boss.ulvaine.UlvaineEffects;
import com.oldrepublic.client.enemy.hymn.FoeArcs;
import com.oldrepublic.client.enemy.hymn.FoeFx;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the {@code ulvaine} event (spec MILESTONE_5.md 12.2, 15.1, 15.3). The server plays
 * the one-shot {@code boss.ulvaine.*} sounds; this draws the looks, shows the phase banners and keeps
 * the lightning's loop:
 *
 * <ul>
 * <li>Phase: a banner (her name, or her echo's, over the phase's name) and a gust of dust about her.</li>
 * <li>Hymn step tell (a tell): 12 {@code gather_dust} drawn in about her and one {@code air_ripple}
 * over the tell; the arrival: air shoved aside where she reappears.</li>
 * <li>Force lightning: an arc from her raised hand to the struck chest per damage pulse; the
 * {@code boss.ulvaine.lightning} loop plays while her synced action is the channel.</li>
 * <li>A rod's tell (a tell): {@link StormTellFx} on the patch; its strike: the scorch under the bolt.</li>
 * <li>Hymn pulse (a tell): an irregular wall of bent air and lifted grit rolling out to the radius,
 * cut into slots of varied height with gaps (the M3 Surge look, never a ring): 48 {@code gather_dust}
 * along the front every 4 ticks.</li>
 * <li>Crescendo: a banner, then 6 {@code light_mote} rising from each standing resonator a second (a
 * tell while any stands); a resonator lights with a flare of motes and breaks into violet shards;
 * the blast shakes the hall and brings dust down from above.</li>
 * <li>The yield: dust as she drops to one knee. The echo's end: it comes apart into pale wisps; at the
 * end of the scene she leaves in a breath of shadow.</li>
 * </ul>
 */
public final class UlvaineFx {
	/** Her Force lightning: violet, with the arcs' pale core. */
	static final int LIGHTNING_RGB = 0xA66BFF;
	/** The lightning loop is heard within the Heart. */
	private static final double LOOP_RANGE = 48.0;
	private static final BlockParticleOption SHARDS = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState());
	/** Angular slots of a pulse's wall. */
	private static final int SLOTS = 24;
	/** Dust along the wall's front each burst (a tell: never thinned). */
	private static final int PULSE_DUST = 48;
	private static final int PULSE_BURST_TICKS = 4;
	private static final int MAX_WAVES = 4;

	/** A Hymn pulse rolling out: its slots keep their heights and gaps for the whole roll. */
	private static final class Wave {
		final Vec3 origin;
		final double radius;
		final int ticks;
		final double[] heights = new double[SLOTS];
		final double[] angles = new double[SLOTS];
		int age;

		Wave(Vec3 origin, double radius, int ticks, double height) {
			this.origin = origin;
			this.radius = radius;
			this.ticks = Math.max(1, ticks);
			int gaps = 4 + FoeFx.RANDOM.nextInt(3);

			for (int i = 0; i < SLOTS; i++) {
				angles[i] = (i + 0.2 + FoeFx.RANDOM.nextDouble() * 0.6) * Math.PI * 2 / SLOTS;
				// Knee to over the head, never even.
				heights[i] = height * (0.35 + FoeFx.RANDOM.nextDouble() * 0.75);
			}

			for (int g = 0; g < gaps; g++) {
				int slot = FoeFx.RANDOM.nextInt(SLOTS);
				heights[slot] = 0;

				if (FoeFx.RANDOM.nextBoolean()) {
					heights[(slot + 1) % SLOTS] = 0;
				}
			}
		}
	}

	private static final List<Wave> WAVES = new ArrayList<>();
	private static boolean initialised;

	public static void init() {
		if (initialised) {
			return;
		}

		initialised = true;
		ClientEffects.register(UlvaineEffects.EVENT, UlvaineFx::onEvent);
		FoeWatch.watch(DarthUlvaine.class, 2, UlvaineFx::watchUlvaine);
		FoeWatch.watch(HymnResonator.class, 10, UlvaineFx::watchResonator);
		ClientTickEvents.END_CLIENT_TICK.register(UlvaineFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> WAVES.clear());
	}

	/** Hymn pulses rolling now (the client tests read it). */
	public static int activeWaves() {
		return WAVES.size();
	}

	private static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 at = p.pos();

		if (p.variant() == UlvaineEffects.PHASE) {
			// The banner shows wherever the participant stands in the Heart.
			banner(entity, Math.round(p.intensity()));
		} else if (p.variant() == UlvaineEffects.CRESCENDO) {
			Banners.show(Component.translatable("boss.oldrepublic.ulvaine.banner.crescendo"),
					Component.translatable("boss.oldrepublic.ulvaine.banner.crescendo.sub"), UiColors.DANGER);
		}

		if (!Vfx.inRange(at)) {
			return;
		}

		switch (p.variant()) {
			case UlvaineEffects.PHASE -> gust(level, at);
			case UlvaineEffects.STEP_TELL -> stepTell(level, at, entity, Math.max(1, Math.round(p.intensity())));
			case UlvaineEffects.STEP -> arrival(mc, level, at, p.dir());
			case UlvaineEffects.LIGHTNING -> lightning(level, at, p.dir(), entity);
			case UlvaineEffects.ROD_TELL -> StormTellFx.tell(at, 2.5, Math.max(1, Math.round(p.intensity())));
			case UlvaineEffects.ROD_STRIKE -> scorch(mc, level, at, Math.max(1f, p.intensity()));
			case UlvaineEffects.PULSE -> pulse(level, at, p.intensity(), p.dir());
			case UlvaineEffects.CRESCENDO -> crescendo(level, at);
			case UlvaineEffects.RESONATOR -> resonator(mc, level, at, p.intensity() > 0.5f);
			case UlvaineEffects.BLAST -> blast(mc, level, at, Math.max(8f, p.intensity()));
			case UlvaineEffects.YIELD -> kneel(level, at);
			case UlvaineEffects.ECHO_FADE -> fade(level, at, entity, p.intensity() > 0.5f);
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- moments

	/** The phase banner: her name (or her echo's) over the phase's name; 4 is the echo gathering. */
	private static void banner(@Nullable Entity entity, int phase) {
		boolean echo = phase == 4 || entity instanceof DarthUlvaine ulvaine && ulvaine.isEcho();
		Component title = Component.translatable(echo ? "boss.oldrepublic.ulvaine.echo" : "boss.oldrepublic.ulvaine.name");

		switch (phase) {
			case 1, 4 -> Banners.show(title, Component.translatable("boss.oldrepublic.ulvaine.banner.duel"), UiColors.BRASS_LIGHT);
			case 2 -> Banners.show(title, Component.translatable("boss.oldrepublic.ulvaine.banner.storm"), UiColors.AMBER);
			case 3 -> Banners.show(title, Component.translatable("boss.oldrepublic.ulvaine.banner.hymn"), UiColors.DANGER);
			default -> {
			}
		}
	}

	/** A gust of dust thrown out about her as a phase turns (uneven, a few spots). */
	private static void gust(ClientLevel level, Vec3 feet) {
		ParticleOptions ground = FoeFx.ground(level, feet);

		for (int i = 0, n = Vfx.count(14); i < n; i++) {
			double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double r = 0.4 + FoeFx.RANDOM.nextDouble() * 1.2;
			double speed = 0.08 + FoeFx.RANDOM.nextDouble() * 0.12;
			FoeFx.add(level, ground, feet.x + Math.cos(angle) * r, feet.y + 0.1, feet.z + Math.sin(angle) * r,
					Math.cos(angle) * speed, 0.05 + FoeFx.RANDOM.nextDouble() * 0.08, Math.sin(angle) * speed);
		}

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			FoeFx.add(level, FoeFx.gatherDust(), feet.add(FoeFx.jitter(1.2), 0.2 + FoeFx.RANDOM.nextDouble() * 1.6, FoeFx.jitter(1.2)),
					new Vec3(0, 0.03, 0));
		}
	}

	/** The Hymn step's tell: dust drawn in about her over the tell, and one ripple of bent air. */
	private static void stepTell(ClientLevel level, Vec3 feet, @Nullable Entity entity, int ticks) {
		FoeFx.add(level, FoeFx.airRipple(), feet.add(0, 1.1, 0), Vec3.ZERO);
		FoeTimers.spread(Vfx.tellCount(12), ticks, entity, (lvl, count, age) -> {
			Vec3 centre = entity != null && !entity.isRemoved() ? entity.position() : feet;

			for (int i = 0; i < count; i++) {
				double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
				double r = 0.9 + FoeFx.RANDOM.nextDouble() * 0.5;
				Vec3 from = centre.add(Math.cos(angle) * r, 0.2 + FoeFx.RANDOM.nextDouble() * 1.7, Math.sin(angle) * r);
				Vec3 in = centre.add(0, 1.0, 0).subtract(from).scale(0.06);
				FoeFx.add(lvl, FoeFx.gatherDust(), from, in);
			}
		});
	}

	/** She reappears: the air shoved aside where she stands, a smear of dust along where she came from. */
	private static void arrival(Minecraft mc, ClientLevel level, Vec3 feet, Vec3 dir) {
		Vec3 travel = FoeFx.flat(dir, new Vec3(0, 0, 1));
		FoeFx.add(level, FoeFx.airRipple(), feet.add(0, 1.0, 0), Vec3.ZERO);
		FoeFx.add(level, FoeFx.airRipple(), feet.add(0, 0.4, 0), Vec3.ZERO);

		for (int i = 0, n = Vfx.count(12); i < n; i++) {
			double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double speed = 0.1 + FoeFx.RANDOM.nextDouble() * 0.12;
			FoeFx.add(level, FoeFx.gatherDust(), feet.add(0, 0.2 + FoeFx.RANDOM.nextDouble() * 1.5, 0),
					new Vec3(Math.cos(angle) * speed, 0.01, Math.sin(angle) * speed));
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			double back = 0.3 + FoeFx.RANDOM.nextDouble() * 1.5;
			FoeFx.add(level, FoeFx.shadowWisp(), feet.subtract(travel.scale(back)).add(FoeFx.jitter(0.25), 0.3 + FoeFx.RANDOM.nextDouble() * 1.3,
					FoeFx.jitter(0.25)), travel.scale(0.03));
		}

		FoeFx.shake(mc, feet, 6, 0.12f);
	}

	/** One damage pulse of her channel: an arc from her raised hand to the chest, sparks where it lands. */
	private static void lightning(ClientLevel level, Vec3 chest, Vec3 dir, @Nullable Entity entity) {
		Vec3 from = entity instanceof DarthUlvaine ulvaine ? ulvaine.handPoint() : chest.subtract(FoeFx.flat(dir, new Vec3(0, 0, 1)).scale(4)).add(0, 0.4, 0);
		FoeArcs.arc(from, chest, LIGHTNING_RGB, 4, 0.16f, 3);

		for (int i = 0, n = Vfx.count(5); i < n; i++) {
			FoeFx.add(level, FoeFx.lightningSpark(), chest.add(FoeFx.jitterVec(0.25)), FoeFx.jitterVec(0.12));
		}
	}

	/** Under the rod's bolt: the patch's dust thrown up, smoke, sparks and ash. */
	private static void scorch(Minecraft mc, ClientLevel level, Vec3 at, float radius) {
		ParticleOptions ground = FoeFx.ground(level, at);

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double speed = 0.08 + FoeFx.RANDOM.nextDouble() * 0.1 * radius;
			FoeFx.add(level, ground, at.x, at.y + 0.1, at.z, Math.cos(angle) * speed, 0.2 + FoeFx.RANDOM.nextDouble() * 0.15, Math.sin(angle) * speed);
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			FoeFx.add(level, ParticleTypes.SMOKE, at.add(FoeFx.jitter(radius * 0.5), 0.1, FoeFx.jitter(radius * 0.5)), new Vec3(0, 0.05, 0));
		}

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			FoeFx.add(level, FoeFx.lightningSpark(), at.add(FoeFx.jitter(radius * 0.4), 0.15, FoeFx.jitter(radius * 0.4)), FoeFx.jitterVec(0.15));
		}

		for (int i = 0, n = Vfx.count(5); i < n; i++) {
			FoeFx.add(level, ParticleTypes.ASH, at.add(FoeFx.jitter(radius * 0.6), 0.8 + FoeFx.RANDOM.nextDouble(), FoeFx.jitter(radius * 0.6)), Vec3.ZERO);
		}

		FoeFx.shake(mc, at, radius + 5, 0.25f);
	}

	/** A Hymn pulse begins: its wall rolls out from her feet (dir.x = its ticks, dir.y = its height). */
	private static void pulse(ClientLevel level, Vec3 origin, float radius, Vec3 shape) {
		int ticks = shape.x > 0 ? (int) Math.round(shape.x) : 40;
		double height = shape.y > 0 ? shape.y : 1.5;

		if (WAVES.size() >= MAX_WAVES) {
			WAVES.remove(0);
		}

		WAVES.add(new Wave(origin, Math.max(2f, radius), ticks, height));
		FoeFx.add(level, FoeFx.airRipple(), origin.add(0, 0.8, 0), Vec3.ZERO);
	}

	/** The Crescendo begins: dust spiralling up about her as she sings. */
	private static void crescendo(ClientLevel level, Vec3 feet) {
		for (int i = 0, n = Vfx.count(20); i < n; i++) {
			double angle = i * 0.9 + FoeFx.RANDOM.nextDouble() * 0.4;
			double r = 0.8 + FoeFx.RANDOM.nextDouble() * 0.8;
			Vec3 at = feet.add(Math.cos(angle) * r, 0.1 + i * 0.12, Math.sin(angle) * r);
			Vec3 swirl = new Vec3(-Math.sin(angle) * 0.06, 0.05, Math.cos(angle) * 0.06);
			FoeFx.add(level, FoeFx.gatherDust(), at, swirl);
		}
	}

	/** A resonator lights (a flare of motes up its spire) or breaks (violet shards and scattered light). */
	private static void resonator(Minecraft mc, ClientLevel level, Vec3 mid, boolean lit) {
		if (lit) {
			for (int i = 0, n = Vfx.count(10); i < n; i++) {
				FoeFx.add(level, FoeFx.lightMote(), mid.add(FoeFx.jitter(0.3), FoeFx.jitter(1.0), FoeFx.jitter(0.3)),
						new Vec3(FoeFx.jitter(0.02), 0.04 + FoeFx.RANDOM.nextDouble() * 0.03, FoeFx.jitter(0.02)));
			}

			return;
		}

		for (int i = 0, n = Vfx.count(20); i < n; i++) {
			FoeFx.add(level, SHARDS, mid.add(FoeFx.jitter(0.3), FoeFx.jitter(1.0), FoeFx.jitter(0.3)),
					new Vec3(FoeFx.jitter(0.25), 0.1 + FoeFx.RANDOM.nextDouble() * 0.2, FoeFx.jitter(0.25)));
		}

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			FoeFx.add(level, FoeFx.lightMote(), mid.add(FoeFx.jitterVec(0.4)), FoeFx.jitterVec(0.1));
		}

		FoeFx.shake(mc, mid, 8, 0.15f);
	}

	/** The Crescendo's blast: the hall shakes, and dust and grit sift down around the player. */
	private static void blast(Minecraft mc, ClientLevel level, Vec3 centre, float radius) {
		FoeFx.shake(mc, centre, radius + 8, 0.9f);
		LocalPlayer player = mc.player;
		Vec3 around = player != null && player.position().distanceToSqr(centre) < (radius + 8) * (radius + 8) ? player.position() : centre;

		for (int i = 0, n = Vfx.count(26); i < n; i++) {
			Vec3 at = around.add(FoeFx.jitter(9), 4 + FoeFx.RANDOM.nextDouble() * 3, FoeFx.jitter(9));
			FoeFx.add(level, i % 3 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH, at, new Vec3(0, -0.05, 0));
		}

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			FoeFx.add(level, FoeFx.airRipple(), centre.add(FoeFx.jitter(radius * 0.6), 1.0, FoeFx.jitter(radius * 0.6)), Vec3.ZERO);
		}
	}

	/** She drops to one knee: a little dust about her. */
	private static void kneel(ClientLevel level, Vec3 feet) {
		ParticleOptions ground = FoeFx.ground(level, feet);

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			FoeFx.add(level, ground, feet.add(FoeFx.jitter(0.6), 0.05, FoeFx.jitter(0.6)),
					new Vec3(FoeFx.jitter(0.05), 0.04 + FoeFx.RANDOM.nextDouble() * 0.04, FoeFx.jitter(0.05)));
		}
	}

	/**
	 * The echo comes apart into pale wisps rising from where it stood ({@code scene} false), or, at the
	 * end of the scene, she leaves in a breath of shadow ({@code scene} true).
	 */
	private static void fade(ClientLevel level, Vec3 feet, @Nullable Entity entity, boolean scene) {
		double height = entity != null ? entity.getBbHeight() : 1.95;

		if (scene) {
			for (int i = 0, n = Vfx.count(12); i < n; i++) {
				FoeFx.add(level, FoeFx.shadowWisp(), feet.add(FoeFx.jitter(0.35), FoeFx.RANDOM.nextDouble() * height, FoeFx.jitter(0.35)),
						new Vec3(FoeFx.jitter(0.02), 0.02, FoeFx.jitter(0.02)));
			}

			FoeFx.add(level, FoeFx.airRipple(), feet.add(0, 1.0, 0), Vec3.ZERO);
			return;
		}

		for (int i = 0, n = Vfx.count(24); i < n; i++) {
			FoeFx.add(level, FoeFx.spectralWisp(), feet.add(FoeFx.jitter(0.35), FoeFx.RANDOM.nextDouble() * height, FoeFx.jitter(0.35)),
					new Vec3(FoeFx.jitter(0.03), 0.03 + FoeFx.RANDOM.nextDouble() * 0.04, FoeFx.jitter(0.03)));
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			FoeFx.add(level, FoeFx.lightMote(), feet.add(FoeFx.jitter(0.3), 0.5 + FoeFx.RANDOM.nextDouble() * height, FoeFx.jitter(0.3)),
					new Vec3(0, 0.05, 0));
		}
	}

	// ---------------------------------------------------------------- the pulse's wall

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;

		if (level == null) {
			WAVES.clear();
			return;
		}

		if (mc.isPaused()) {
			return;
		}

		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext();) {
			Wave wave = it.next();

			if (wave.age % PULSE_BURST_TICKS == 0 && Vfx.inRange(wave.origin)) {
				burst(level, wave);
			}

			if (++wave.age > wave.ticks) {
				it.remove();
			}
		}
	}

	/** One burst along the wall's front: dust in the standing slots at their own heights, bent air, grit. */
	private static void burst(ClientLevel level, Wave wave) {
		double r = Math.max(0.8, wave.radius * wave.age / wave.ticks);
		double outward = wave.radius / wave.ticks;
		int dust = Vfx.tellCount(PULSE_DUST);

		for (int i = 0; i < dust; i++) {
			int slot = FoeFx.RANDOM.nextInt(SLOTS);

			if (wave.heights[slot] <= 0) {
				// A gap: the next standing slot takes this one's dust, so the front keeps its count.
				slot = nextStanding(wave, slot);

				if (slot < 0) {
					return;
				}
			}

			double angle = wave.angles[slot] + FoeFx.jitter(Math.PI / SLOTS * 0.8);
			double h = FoeFx.RANDOM.nextDouble() * wave.heights[slot];
			Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 at = wave.origin.add(out.scale(r + FoeFx.jitter(0.25))).add(0, 0.1 + h, 0);
			FoeFx.add(level, FoeFx.gatherDust(), at, out.scale(outward * 0.6).add(0, 0.02, 0));
		}

		for (int i = 0, n = Vfx.count(3); i < n; i++) {
			int slot = nextStanding(wave, FoeFx.RANDOM.nextInt(SLOTS));

			if (slot >= 0) {
				Vec3 out = new Vec3(Math.cos(wave.angles[slot]), 0, Math.sin(wave.angles[slot]));
				FoeFx.add(level, FoeFx.airRipple(), wave.origin.add(out.scale(r)).add(0, wave.heights[slot] * 0.6, 0), Vec3.ZERO);
			}
		}

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			int slot = nextStanding(wave, FoeFx.RANDOM.nextInt(SLOTS));

			if (slot >= 0) {
				Vec3 out = new Vec3(Math.cos(wave.angles[slot]), 0, Math.sin(wave.angles[slot]));
				Vec3 at = wave.origin.add(out.scale(r)).add(0, 0.05, 0);
				FoeFx.add(level, FoeFx.ground(level, at), at, new Vec3(0, 0.12 + FoeFx.RANDOM.nextDouble() * 0.1, 0));
			}
		}
	}

	/** The first slot from {@code from} (inclusive, going round) that stands, or -1 when none does. */
	private static int nextStanding(Wave wave, int from) {
		for (int k = 0; k < SLOTS; k++) {
			int slot = (from + k) % SLOTS;

			if (wave.heights[slot] > 0) {
				return slot;
			}
		}

		return -1;
	}

	// ---------------------------------------------------------------- watchers

	/** Her lightning loop while she channels, and a crackle at her hand between the damage pulses. */
	private static void watchUlvaine(Minecraft mc, ClientLevel level, DarthUlvaine ulvaine) {
		if (ulvaine.action() != DarthUlvaine.Action.LIGHTNING) {
			return;
		}

		FoeLoops.start("boss.ulvaine.lightning:" + ulvaine.getId(), ModSounds.BOSS_ULVAINE_LIGHTNING, SoundSource.HOSTILE, ulvaine, ulvaine.position(),
				0.9f, 1f, LOOP_RANGE, () -> ulvaine.isAlive() && ulvaine.action() == DarthUlvaine.Action.LIGHTNING);

		if (Vfx.inRange(ulvaine.position())) {
			FoeFx.add(level, FoeFx.lightningSpark(), ulvaine.handPoint().add(FoeFx.jitterVec(0.12)), FoeFx.jitterVec(0.05));
		}
	}

	/** 6 motes a second rising from each standing resonator (a tell: never thinned). */
	private static void watchResonator(Minecraft mc, ClientLevel level, HymnResonator spire) {
		if (spire.isDeadOrDying() || !Vfx.inRange(spire.position())) {
			return;
		}

		for (int i = 0, n = Vfx.tellCount(3); i < n; i++) {
			Vec3 at = spire.position().add(FoeFx.jitter(0.3), 0.6 + FoeFx.RANDOM.nextDouble() * 1.6, FoeFx.jitter(0.3));
			FoeFx.add(level, FoeFx.lightMote(), at, new Vec3(FoeFx.jitter(0.01), 0.03 + FoeFx.RANDOM.nextDouble() * 0.02, FoeFx.jitter(0.01)));
		}
	}

	private UlvaineFx() {
	}
}
