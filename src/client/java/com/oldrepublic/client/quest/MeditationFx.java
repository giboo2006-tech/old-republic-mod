package com.oldrepublic.client.quest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestParticles;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Meditation visuals and sound for every meditating player this client can see: pale motes drawn
 * inward, the calm breathing loop, and the break and completion one-shots. Also provides the local
 * player's progress for the HUD focus ring.
 */
public final class MeditationFx {
	private static final int SAFETY_TICKS = 40;
	private static final RandomSource RANDOM = RandomSource.create();

	private static final class Session {
		final int start;
		final int duration;
		final @Nullable LoopSound loop;

		Session(int start, int duration, @Nullable LoopSound loop) {
			this.start = start;
			this.duration = duration;
			this.loop = loop;
		}
	}

	private static final Map<Integer, Session> SESSIONS = new HashMap<>();

	static void handle(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		int id = payload.entityId();
		Session old = SESSIONS.remove(id);

		if (old != null && old.loop != null) {
			old.loop.release();
		}

		Vec3 pos = entity != null ? entity.position() : payload.pos();

		switch (payload.variant()) {
			case QuestEffects.MEDITATION_START -> {
				LoopSound loop = entity != null ? LoopSound.play(ModSounds.MEDITATION_LOOP, 0.6f, entity) : null;
				SESSIONS.put(id, new Session(QuestClient.ticks(), Math.max(1, (int) payload.intensity()), loop));
			}
			case QuestEffects.MEDITATION_BREAK -> QuestSounds.at(level, pos, ModSounds.MEDITATION_BREAK, 0.8f, 1f);
			case QuestEffects.MEDITATION_COMPLETE -> {
				QuestSounds.at(level, pos, ModSounds.MEDITATION_COMPLETE, 1f, 1f);
				settle(level, pos);
			}
			default -> {
			}
		}
	}

	static void tick(Minecraft mc) {
		ClientLevel level = mc.level;

		if (level == null) {
			reset();
			return;
		}

		int now = QuestClient.ticks();
		Iterator<Map.Entry<Integer, Session>> it = SESSIONS.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<Integer, Session> e = it.next();
			Session session = e.getValue();
			Entity entity = level.getEntity(e.getKey());

			if (entity == null || entity.isRemoved() || now - session.start > session.duration + SAFETY_TICKS) {
				if (session.loop != null) {
					session.loop.release();
				}

				it.remove();
				continue;
			}

			if (now % 2 == 0 && Vfx.inRange(entity.position())) {
				spawnInwardMotes(level, entity.position().add(0, 0.9, 0), Vfx.count(2));
			}
		}
	}

	private static void spawnInwardMotes(ClientLevel level, Vec3 center, int count) {
		for (int i = 0; i < count; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double radius = 1.6 + RANDOM.nextDouble() * 0.8;
			double dy = (RANDOM.nextDouble() - 0.3) * 1.2;
			Vec3 from = center.add(Math.cos(angle) * radius, dy, Math.sin(angle) * radius);
			Vec3 vel = center.subtract(from).scale(0.028);
			level.addParticle(QuestParticles.FORCE_MOTE, from.x, from.y, from.z, vel.x, vel.y, vel.z);
		}
	}

	/** On completion the gathered motes rise and disperse slowly. */
	private static void settle(ClientLevel level, Vec3 pos) {
		if (!Vfx.inRange(pos)) {
			return;
		}

		for (int i = 0, n = Vfx.count(14); i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double r = 0.2 + RANDOM.nextDouble() * 0.4;
			level.addParticle(QuestParticles.FORCE_MOTE, pos.x + Math.cos(angle) * r, pos.y + 0.8 + RANDOM.nextDouble() * 0.6,
					pos.z + Math.sin(angle) * r, Math.cos(angle) * 0.02, 0.02 + RANDOM.nextDouble() * 0.02, Math.sin(angle) * 0.02);
		}
	}

	/** Local player's meditation progress 0..1, or -1 when not meditating. */
	public static float ownProgress(float partialTick) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null) {
			return -1f;
		}

		Session session = SESSIONS.get(mc.player.getId());

		if (session == null) {
			return -1f;
		}

		return Mth.clamp((QuestClient.ticks() - session.start + partialTick) / session.duration, 0f, 1f);
	}

	static void reset() {
		for (Session session : SESSIONS.values()) {
			if (session.loop != null) {
				session.loop.kill();
			}
		}

		SESSIONS.clear();
	}

	private MeditationFx() {
	}
}
