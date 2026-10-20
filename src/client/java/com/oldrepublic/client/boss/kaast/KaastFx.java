package com.oldrepublic.client.boss.kaast;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.boss.kaast.KaastEffects;
import com.oldrepublic.boss.kaast.TorvinKaast;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the {@code kaast} event (spec MILESTONE_4.md 13.3), except the music
 * ({@code BossMusicService}). The fight's sounds are the server's; this draws the looks:
 *
 * <ul>
 * <li>Phase: the banner, "Torvin Kaast, the hunter" over the phase's name.</li>
 * <li>Mine arming (a tell): a spark at the fuse as it starts blinking (the blink is the mine's own
 * light); live: a puff of dust. Sensed: dust settling onto each live mine, for the sensing player.
 * Burst: flash, smoke and clods, a shake up close.</li>
 * <li>The slam (its rise a tell): the jet's flame under him while he climbs, and dust stirring
 * unevenly where he will land; the landing's dust burst and clods.</li>
 * <li>The axe blocking a saber: sparks. The helmet cracking: grit from the visor. The yield: dust
 * settling as he kneels. The echo: pale ash drifting up as it forms, and away as it fades. The
 * choke burnt off: a gout of flame toward the caster.</li>
 * </ul>
 * Dust spreads unevenly, with gaps: no ground rings or circles.
 */
public final class KaastFx {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final BlockParticleOption SETTLING_DUST = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState());

	/** A slam on its way: Kaast's id to where he lands and the client tick the rise ends. */
	private record Slam(Vec3 landing, long until) {
	}

	private static final Map<Integer, Slam> SLAMS = new HashMap<>();
	private static long ticks;

	public static void init() {
		ClientEffects.register(KaastEffects.EVENT, KaastFx::onEvent);
		ClientTickEvents.END_CLIENT_TICK.register(KaastFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SLAMS.clear());
	}

	private static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 at = p.pos();

		switch (p.variant()) {
			case KaastEffects.PHASE -> banner(entity, Math.round(p.intensity()));
			case KaastEffects.MINE_ARM -> {
				for (int i = 0; i < Vfx.tellCount(3); i++) {
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.2, at.z, jitter(0.05), 0.08, jitter(0.05));
				}
			}
			case KaastEffects.MINE_LIVE -> {
				for (int i = 0; i < Vfx.count(3); i++) {
					level.addParticle(ParticleTypes.POOF, at.x + jitter(0.2), at.y + 0.1, at.z + jitter(0.2), 0, 0.01, 0);
				}
			}
			case KaastEffects.MINE_SENSED -> {
				for (int i = 0; i < 6; i++) {
					level.addParticle(SETTLING_DUST, at.x + jitter(0.25), at.y + 0.6 + RANDOM.nextDouble() * 0.4, at.z + jitter(0.25), 0, 0, 0);
				}
			}
			case KaastEffects.MINE_BURST -> burst(mc, level, at, Math.max(1f, p.intensity()), 12);
			case KaastEffects.SLAM_TELL -> SLAMS.put(p.entityId(), new Slam(at, ticks + Math.max(1, Math.round(p.intensity()))));
			case KaastEffects.SLAM -> {
				SLAMS.remove(p.entityId());
				burst(mc, level, at, Math.max(1f, p.intensity()), 18);
			}
			case KaastEffects.YIELD -> {
				for (int i = 0; i < Vfx.count(8); i++) {
					level.addParticle(ParticleTypes.POOF, at.x + jitter(0.6), at.y + 0.1, at.z + jitter(0.6), 0, 0.01, 0);
				}
			}
			case KaastEffects.HELMET -> {
				if (p.intensity() < 0.5f) {
					// The crack: grit and a few flakes of paint from the visor.
					for (int i = 0; i < Vfx.count(6); i++) {
						level.addParticle(ParticleTypes.CRIT, at.x + jitter(0.15), at.y, at.z + jitter(0.15), jitter(0.1), 0.05, jitter(0.1));
					}
				}
			}
			case KaastEffects.CLASH -> {
				play(level, at, ModSounds.SABER_CLASH, 0.9f, 0.8f + RANDOM.nextFloat() * 0.1f);

				for (int i = 0; i < Vfx.count(8); i++) {
					level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, jitter(0.3), RANDOM.nextDouble() * 0.25, jitter(0.3));
				}
			}
			case KaastEffects.ECHO -> {
				boolean appear = p.intensity() > 0.5f;

				for (int i = 0; i < Vfx.count(16); i++) {
					double height = RANDOM.nextDouble() * 2;
					Vec3 v = appear ? new Vec3(jitter(0.01), 0.03, jitter(0.01)) : new Vec3(jitter(0.08), 0.05, jitter(0.08));
					level.addParticle(ParticleTypes.WHITE_ASH, at.x + jitter(0.4), at.y + height, at.z + jitter(0.4), v.x, v.y, v.z);
				}
			}
			case KaastEffects.BURN_OFF -> {
				Vec3 dir = p.dir().lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : p.dir().normalize();

				for (int i = 0; i < Vfx.count(12); i++) {
					Vec3 v = dir.add(jitter(0.25), jitter(0.15), jitter(0.25)).normalize().scale(0.3 + RANDOM.nextDouble() * 0.15);
					level.addParticle(flameJet(), at.x, at.y, at.z, v.x, v.y, v.z);
				}
			}
			default -> {
			}
		}
	}

	/** The phase banner: the boss's name (or the echo's) over the phase's name. */
	private static void banner(@Nullable Entity entity, int phase) {
		boolean echo = entity instanceof TorvinKaast kaast && kaast.isEcho();
		Component title = Component.translatable(echo ? "boss.oldrepublic.kaast.echo" : "boss.oldrepublic.kaast.name");

		switch (phase) {
			case 1 -> Banners.show(title, Component.translatable("boss.oldrepublic.kaast.banner.hunt"), UiColors.AMBER);
			case 2 -> Banners.show(title, Component.translatable("boss.oldrepublic.kaast.banner.trap"), UiColors.BRASS_LIGHT);
			case 3 -> Banners.show(title, Component.translatable("boss.oldrepublic.kaast.banner.last_stand"), UiColors.DANGER);
			default -> {
			}
		}
	}

	private static void tick(Minecraft mc) {
		ticks++;
		ClientLevel level = mc.level;

		if (level == null) {
			SLAMS.clear();
			return;
		}

		for (Iterator<Map.Entry<Integer, Slam>> it = SLAMS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Slam> entry = it.next();
			Entity kaast = level.getEntity(entry.getKey());
			Slam slam = entry.getValue();

			if (kaast == null || ticks > slam.until() + 30) {
				it.remove();
				continue;
			}

			if (ticks > slam.until()) {
				continue;
			}

			// The rise is a tell: the jet's flame under him and dust stirring where he will land, never thinned.
			for (int i = 0; i < Vfx.tellCount(2); i++) {
				level.addParticle(flameJet(), kaast.getX() + jitter(0.15), kaast.getY() + 0.9, kaast.getZ() + jitter(0.15), 0, -0.3, 0);
			}

			if (ticks % 3 == 0) {
				Vec3 spot = slam.landing();
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double r = 0.5 + RANDOM.nextDouble() * 2.2;

				for (int i = 0; i < Vfx.tellCount(2); i++) {
					level.addParticle(ParticleTypes.POOF, spot.x + Math.cos(angle) * r + jitter(0.3), spot.y + 0.1, spot.z + Math.sin(angle) * r + jitter(0.3),
							0, 0.02, 0);
				}
			}
		}
	}

	/** A blast on the ground: a flash, smoke, clods of the ground thrown up, a shake up close. */
	private static void burst(Minecraft mc, ClientLevel level, Vec3 at, float radius, int base) {
		level.addParticle(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 0, 0, 0);

		if (Vfx.inRange(at)) {
			ParticleOptions clods = ground(level, at);

			for (int i = 0; i < Vfx.count(base); i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double speed = 0.1 + RANDOM.nextDouble() * 0.15 * radius;
				level.addParticle(i % 2 == 0 ? ParticleTypes.POOF : clods, at.x, at.y + 0.2, at.z, Math.cos(angle) * speed, 0.15 + RANDOM.nextDouble() * 0.2,
						Math.sin(angle) * speed);
			}

			for (int i = 0; i < Vfx.count(base / 3); i++) {
				level.addParticle(ParticleTypes.LARGE_SMOKE, at.x + jitter(radius * 0.5), at.y + 0.3, at.z + jitter(radius * 0.5), 0, 0.04, 0);
			}
		}

		if (mc.player != null && mc.player.position().distanceTo(at) < radius + 4) {
			CameraShake.add(0.35f);
		}
	}

	private static ParticleOptions ground(ClientLevel level, Vec3 at) {
		BlockState below = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
		return below.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, below);
	}

	/** W4's {@code flame_jet} particle, looked up by id (13.2), or vanilla flame if it is missing. */
	private static ParticleOptions flameJet() {
		return BuiltInRegistries.PARTICLE_TYPE.getValue(OldRepublicMod.id("flame_jet")) instanceof SimpleParticleType jet ? jet : ParticleTypes.FLAME;
	}

	private static double jitter(double amount) {
		return (RANDOM.nextDouble() * 2 - 1) * amount;
	}

	private static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.HOSTILE, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	private KaastFx() {
	}
}
