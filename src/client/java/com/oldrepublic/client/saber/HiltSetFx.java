package com.oldrepublic.client.saber;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.quest.QuestParticles;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.HiltSetEffects;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The boss sets' client effects (docs/MILESTONE_7.md 7.2), from {@link HiltSetEffects}' events. Motes and
 * glints on bodies and in the air only: no ground circles, sigils, halos, glyphs or runes.
 * <ul>
 * <li>{@code hilt_set_mark} (Curator's Eye): {@code saber.set_mark} and a short rise of pale gold glints off
 * the marked foe; while the mark holds, an amber mote drifts up from its upper body every few ticks; when
 * the marked hit lands, a small amber burst and the mark ends;</li>
 * <li>{@code hilt_set_pulse} (the Long Hymn): {@code saber.set_pulse} and a burst of pale shimmer motes
 * that spread from the wielder's chest out to the pulse's radius (never down into the ground), with a few
 * violet motes close in.</li>
 * </ul>
 * The sets' tooltip lines are the items' own ({@code HiltSetEffects.partLines}, {@code saberLines}).
 */
public final class HiltSetFx {
	/** Curator's Eye: the Collector's amber. */
	private static final int MARK_COLOUR = 0xFFB347;
	/** The Long Hymn: the Choir's pale violet inlay. */
	private static final int PULSE_COLOUR = 0xC9B8F2;
	/** Particle counts at the Standard preset: a mark's glints, the struck burst, a pulse's shimmer and dust. */
	private static final int MARK_GLINTS = 8;
	private static final int STRUCK_MOTES = 10;
	private static final int PULSE_SHIMMER = 28;
	private static final int PULSE_DUST = 10;
	/** A marked foe sheds one mote this often, in ticks. */
	private static final int MARK_MOTE_INTERVAL = 4;
	/** A shimmer mote lives 40 to 60 ticks at a steady speed: the pulse reaches its radius in about this many. */
	private static final double PULSE_SPREAD_TICKS = 44.0;
	/** The pulse's directions run from a little below the chest to well above it, so no mote reaches the ground. */
	private static final double PULSE_LOWEST = -0.3;
	private static final double PULSE_HIGHEST = 0.85;
	private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
	private static final RandomSource RANDOM = RandomSource.create();
	/** Marked entities by id, and the client game tick their mark ends. */
	private static final Map<Integer, Long> MARKS = new HashMap<>();
	private static @Nullable ClientLevel lastLevel;

	public static void init() {
		ClientEffects.register(HiltSetEffects.MARK, HiltSetFx::onMark);
		ClientEffects.register(HiltSetEffects.PULSE, HiltSetFx::onPulse);
		ClientTickEvents.END_CLIENT_TICK.register(HiltSetFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MARKS.clear());
	}

	private static void onMark(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		if (p.variant() == HiltSetEffects.MARK_STRUCK) {
			MARKS.remove(p.entityId());

			if (Vfx.inRange(p.pos())) {
				burst(level, p.pos(), Vfx.count(STRUCK_MOTES));
			}

			return;
		}

		MARKS.put(p.entityId(), level.getGameTime() + Math.max(1, Math.round(p.intensity())));
		level.playLocalSound(p.pos().x, p.pos().y, p.pos().z, ModSounds.SABER_SET_MARK, SoundSource.PLAYERS,
				0.9f * ClientConfig.get().modSoundVolume, 0.95f + RANDOM.nextFloat() * 0.1f, false);

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		for (int i = 0, n = Vfx.count(MARK_GLINTS); i < n; i++) {
			Vec3 at = onBody(entity, p.pos());
			level.addParticle(QuestParticles.VOICE_WARM, at.x, at.y, at.z, (RANDOM.nextDouble() - 0.5) * 0.02, 0.03 + RANDOM.nextDouble() * 0.03,
					(RANDOM.nextDouble() - 0.5) * 0.02);
		}
	}

	private static void onPulse(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		level.playLocalSound(p.pos().x, p.pos().y, p.pos().z, ModSounds.SABER_SET_PULSE, SoundSource.PLAYERS,
				ClientConfig.get().modSoundVolume, 0.95f + RANDOM.nextFloat() * 0.1f, false);

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		double speed = Math.max(1.0, p.intensity()) / PULSE_SPREAD_TICKS;
		int n = Vfx.count(PULSE_SHIMMER);

		for (int i = 0; i < n; i++) {
			// Spread evenly round the wielder (a golden-angle spiral over the allowed heights).
			Vec3 dir = spiral(i, n);
			Vec3 at = p.pos().add(dir.scale(0.35));
			level.addParticle(QuestParticles.RESONANCE_SHIMMER, at.x, at.y, at.z, dir.x * speed, dir.y * speed, dir.z * speed);
		}

		for (int i = 0, m = Vfx.count(PULSE_DUST); i < m; i++) {
			Vec3 dir = spiral(RANDOM.nextInt(64), 64);
			Vec3 at = p.pos().add(dir.scale(0.4 + RANDOM.nextDouble() * 0.6));
			level.addParticle(new DustParticleOptions(PULSE_COLOUR, 0.9f), at.x, at.y, at.z, dir.x * 0.05, dir.y * 0.05, dir.z * 0.05);
		}
	}

	/** The marks' motes, and forgetting marks that ended, whose foe is gone, or of a level left behind. */
	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;

		if (level != lastLevel) {
			lastLevel = level;
			MARKS.clear();
		}

		if (level == null || MARKS.isEmpty()) {
			return;
		}

		long now = level.getGameTime();
		boolean shed = now % MARK_MOTE_INTERVAL == 0;
		Iterator<Map.Entry<Integer, Long>> it = MARKS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Long> mark = it.next();
			Entity entity = level.getEntity(mark.getKey());

			if (entity == null || !entity.isAlive() || now > mark.getValue()) {
				it.remove();
				continue;
			}

			if (shed && Vfx.inRange(entity.position())) {
				for (int i = 0, n = Vfx.count(1); i < n; i++) {
					Vec3 at = onBody(entity, entity.position());
					level.addParticle(new DustParticleOptions(MARK_COLOUR, 0.6f), at.x, at.y, at.z, 0, 0.02, 0);
				}
			}
		}
	}

	/** A small amber burst at {@code pos}: the marked hit landed. */
	private static void burst(ClientLevel level, Vec3 pos, int count) {
		for (int i = 0; i < count; i++) {
			Vec3 dir = spiral(RANDOM.nextInt(64), 64);
			ParticleOptions particle = i % 2 == 0 ? QuestParticles.VOICE_WARM : new DustParticleOptions(MARK_COLOUR, 0.7f);
			level.addParticle(particle, pos.x, pos.y, pos.z, dir.x * 0.06, dir.y * 0.06, dir.z * 0.06);
		}
	}

	/** Direction {@code i} of {@code n} on a golden-angle spiral, from {@link #PULSE_LOWEST} to {@link #PULSE_HIGHEST} in height. */
	private static Vec3 spiral(int i, int n) {
		double y = Mth.lerp((i + 0.5) / Math.max(1, n), PULSE_LOWEST, PULSE_HIGHEST);
		double ring = Math.sqrt(Math.max(0.0, 1.0 - y * y));
		double angle = i * GOLDEN_ANGLE;
		return new Vec3(Math.cos(angle) * ring, y, Math.sin(angle) * ring);
	}

	/** A random point on the upper half of {@code entity}'s body, or near {@code fallback} when it is not loaded here. */
	private static Vec3 onBody(@Nullable Entity entity, Vec3 fallback) {
		if (entity == null) {
			return fallback.add(RANDOM.nextGaussian() * 0.2, RANDOM.nextGaussian() * 0.2, RANDOM.nextGaussian() * 0.2);
		}

		AABB box = entity.getBoundingBox();
		return new Vec3(Mth.lerp(RANDOM.nextDouble(), box.minX, box.maxX), Mth.lerp(0.45 + RANDOM.nextDouble() * 0.55, box.minY, box.maxY),
				Mth.lerp(RANDOM.nextDouble(), box.minZ, box.maxZ));
	}

	private HiltSetFx() {
	}
}
