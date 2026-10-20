package com.oldrepublic.client.quest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.holocron.GatekeeperFigure;
import com.oldrepublic.holocron.HolocronCatalog;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestParticles;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Chapter Three's world moments (spec 13.3), seen by everyone near:
 *
 * <ul>
 * <li>{@code cairn_touch}: the touched cairn (grey stone) or cache (a brown satchel) settles into a
 * low puff of dust, and one pale gold mote rises from it toward the toucher; {@code story.cairn_touch}.</li>
 * <li>{@code rank_ceremony} 0, the Knighting: a pale line of blade-light lies on one shoulder of the
 * kneeling player, then the other, with a little dust drifting down each time;
 * {@code story.blade_touch} at each touch.</li>
 * <li>{@code rank_ceremony} 1, the Naming: the writ's red seal cracks with a few sparks, then the
 * old-name tablet breaks in two in a puff of grey dust and falling chips; {@code story.tablet_break}.</li>
 * <li>{@code rank_ceremony} 2, the Pathfinder: Tessaly Arn's figure projects from the Compass on the
 * Courier's Fall pedestal (W3's gatekeeper figure, reused) with rising shimmer, and folds back once
 * the dialogue is closed; {@code holocron.gatekeeper_form}.</li>
 * </ul>
 *
 * <p>The ceremonies are a few timed beats; each beat is kept in a short list and run from the client
 * tick, and a level change or disconnect drops them.
 */
public final class StoryM3Fx {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final int CAIRN_STONE = 0x8C8578;
	private static final int CACHE_SATCHEL = 0x7A5C3A;
	private static final int BLADE_LIGHT = 0xBFE6FF;
	private static final int DRIFT_DUST = 0xCBBFA6;
	private static final int SEAL_RED = 0x9A1E1E;
	private static final int TABLET_DUST = 0x77726C;
	private static final int TABLET_CHIP = 0x3C3A38;
	/** Ticks between the two shoulder touches. */
	private static final int SECOND_TOUCH = 22;
	/** Ticks from the seal cracking to the tablet breaking. */
	private static final int TABLET_BREAK = 26;
	/** The Pathfinder's witness stays at least this long, and at most the second. */
	private static final int WITNESS_MIN = 60;
	private static final int WITNESS_MAX = 900;

	private record Beat(int at, Runnable action) {
	}

	private static final List<Beat> BEATS = new ArrayList<>();
	/** The open witness: its gatekeeper index and when it formed, or -1. */
	private static int witnessIndex = -1;
	private static int witnessStart;

	// ---------------------------------------------------------------- cairn_touch

	static void cairnTouch(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 at = payload.pos();
		boolean cache = payload.variant() == QuestEffects.CAIRN_CACHE;
		QuestSounds.at(level, at, ModSounds.STORY_CAIRN_TOUCH, 0.9f, cache ? 0.92f : 1f);

		if (!Vfx.inRange(at)) {
			return;
		}

		// It settles: a low ring of dust spreading at its foot and sinking.
		DustParticleOptions dust = new DustParticleOptions(cache ? CACHE_SATCHEL : CAIRN_STONE, 0.9f);

		for (int i = 0, n = Vfx.count(12); i < n; i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double r = 0.15 + RANDOM.nextDouble() * 0.3;
			double speed = 0.02 + RANDOM.nextDouble() * 0.03;
			level.addParticle(dust, at.x + Math.cos(angle) * r, at.y - 0.3 + RANDOM.nextDouble() * 0.5, at.z + Math.sin(angle) * r,
					Math.cos(angle) * speed, -0.01, Math.sin(angle) * speed);
		}

		// One mote rises toward the one who touched it.
		Vec3 toward = payload.dir().lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : payload.dir().normalize();
		Vec3 vel = toward.scale(0.05).add(0, 0.02, 0);
		level.addParticle(QuestParticles.VOICE_WARM, at.x, at.y + 0.2, at.z, vel.x, vel.y, vel.z);
	}

	// ---------------------------------------------------------------- rank_ceremony

	static void rankCeremony(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		switch (payload.variant()) {
			case QuestEffects.RANK_KNIGHTING -> knighting(level, payload, entity);
			case QuestEffects.RANK_NAMING -> naming(level, payload, entity);
			case QuestEffects.RANK_PATHFINDER -> pathfinder(client, level, payload, entity);
			default -> {
			}
		}
	}

	/** The player's feet: the entity when this client has it, else the event's position. */
	private static Vec3 feet(EffectEventPayload payload, @Nullable Entity entity) {
		return entity != null ? entity.position() : payload.pos();
	}

	/** Unit horizontal vector from the mentor toward the player; south when the event has none. */
	private static Vec3 fromMentor(EffectEventPayload payload) {
		Vec3 flat = new Vec3(payload.dir().x, 0, payload.dir().z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	private static void knighting(ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 feet = feet(payload, entity);
		Vec3 toPlayer = fromMentor(payload);
		Vec3 side = new Vec3(-toPlayer.z, 0, toPlayer.x);
		// Kneeling: the shoulders sit lower than a standing player's.
		Vec3 left = feet.add(side.scale(0.34)).add(0, 1.15, 0);
		Vec3 right = feet.add(side.scale(-0.34)).add(0, 1.15, 0);
		int now = QuestClient.ticks();
		touch(level, left, toPlayer);
		BEATS.add(new Beat(now + SECOND_TOUCH, () -> touch(level, right, toPlayer)));
	}

	/** One shoulder touch: the flat of the lit blade laid across it, and a little dust. */
	private static void touch(ClientLevel level, Vec3 shoulder, Vec3 toPlayer) {
		QuestSounds.at(level, shoulder, ModSounds.STORY_BLADE_TOUCH, 0.9f, 1f);

		if (!Vfx.inRange(shoulder)) {
			return;
		}

		DustParticleOptions light = new DustParticleOptions(BLADE_LIGHT, 0.55f);
		// The blade runs from the shoulder back toward the mentor and up a little.
		int glints = Vfx.count(7);

		for (int i = 0; i < glints; i++) {
			double along = i / (double) Math.max(1, glints - 1) * 1.1;
			Vec3 at = shoulder.subtract(toPlayer.scale(along)).add(0, along * 0.18, 0);
			level.addParticle(light, at.x, at.y, at.z, 0, 0.002, 0);
		}

		level.addParticle(QuestParticles.FORCE_MOTE, shoulder.x, shoulder.y + 0.1, shoulder.z, 0, 0.01, 0);
		DustParticleOptions dust = new DustParticleOptions(DRIFT_DUST, 0.45f);

		for (int i = 0, n = Vfx.count(4); i < n; i++) {
			level.addParticle(dust, shoulder.x + RANDOM.nextGaussian() * 0.2, shoulder.y + 0.3 + RANDOM.nextDouble() * 0.3,
					shoulder.z + RANDOM.nextGaussian() * 0.2, 0, -0.015, 0);
		}
	}

	private static void naming(ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 feet = feet(payload, entity);
		Vec3 toPlayer = fromMentor(payload);
		// Held up between them, toward the mentor.
		Vec3 writ = feet.subtract(toPlayer.scale(0.9)).add(0, 1.3, 0);
		Vec3 tablet = feet.subtract(toPlayer.scale(0.9)).add(0, 1.0, 0);
		int now = QuestClient.ticks();
		sealCracks(level, writ);
		BEATS.add(new Beat(now + TABLET_BREAK, () -> tabletBreaks(level, tablet)));
	}

	private static void sealCracks(ClientLevel level, Vec3 at) {
		QuestSounds.at(level, at, ModSounds.STORY_TABLET_BREAK, 0.35f, 1.7f);

		if (!Vfx.inRange(at)) {
			return;
		}

		DustParticleOptions spark = new DustParticleOptions(SEAL_RED, 0.5f);

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			level.addParticle(spark, at.x, at.y, at.z, RANDOM.nextGaussian() * 0.04, 0.02 + RANDOM.nextDouble() * 0.03, RANDOM.nextGaussian() * 0.04);
		}
	}

	private static void tabletBreaks(ClientLevel level, Vec3 at) {
		QuestSounds.at(level, at, ModSounds.STORY_TABLET_BREAK, 1f, 1f);

		if (!Vfx.inRange(at)) {
			return;
		}

		DustParticleOptions puff = new DustParticleOptions(TABLET_DUST, 1f);

		for (int i = 0, n = Vfx.count(12); i < n; i++) {
			level.addParticle(puff, at.x, at.y, at.z, RANDOM.nextGaussian() * 0.05, 0.01 + RANDOM.nextDouble() * 0.03, RANDOM.nextGaussian() * 0.05);
		}

		DustParticleOptions chip = new DustParticleOptions(TABLET_CHIP, 0.7f);

		for (int i = 0, n = Vfx.count(5); i < n; i++) {
			level.addParticle(chip, at.x + RANDOM.nextGaussian() * 0.15, at.y, at.z + RANDOM.nextGaussian() * 0.15, 0, -0.08, 0);
		}
	}

	/** The Compass projects Tessaly Arn as witness over the pedestal (pos = the pedestal's top). */
	private static void pathfinder(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 top = payload.pos();
		BlockPos pedestal = BlockPos.containing(top.x, top.y - 1, top.z);
		Vec3 player = entity != null ? entity.position() : client.player != null ? client.player.position() : top.add(0, 0, 2);
		Vec3 toward = player.subtract(Vec3.atBottomCenterOf(pedestal));
		int index = HolocronCatalog.gatekeeperIndex(HolocronCatalog.WAYFARERS_COMPASS);
		QuestSounds.at(level, top, ModSounds.HOLOCRON_GATEKEEPER_FORM, 1f, 1f);

		if (index >= 0) {
			GatekeeperFigure.show(pedestal, toward, index);
			witnessIndex = index;
			witnessStart = QuestClient.ticks();
		}

		if (!Vfx.inRange(top)) {
			return;
		}

		for (int i = 0, n = Vfx.count(10); i < n; i++) {
			level.addParticle(QuestParticles.RESONANCE_SHIMMER, top.x + RANDOM.nextGaussian() * 0.25, top.y + RANDOM.nextDouble() * 0.4,
					top.z + RANDOM.nextGaussian() * 0.25, 0, 0.02 + RANDOM.nextDouble() * 0.02, 0);
		}
	}

	// ---------------------------------------------------------------- tick and reset

	static void tick(Minecraft mc) {
		int now = QuestClient.ticks();

		if (!BEATS.isEmpty()) {
			List<Beat> due = new ArrayList<>();

			for (Iterator<Beat> it = BEATS.iterator(); it.hasNext();) {
				Beat beat = it.next();

				if (now >= beat.at()) {
					it.remove();
					due.add(beat);
				}
			}

			if (mc.level != null) {
				due.forEach(beat -> beat.action().run());
			}
		}

		// The witness folds once the Pathfinder's dialogue is closed (or after the longest wait).
		if (witnessIndex >= 0) {
			int age = now - witnessStart;

			if (age >= WITNESS_MAX || (age >= WITNESS_MIN && mc.gui.screen() == null)) {
				GatekeeperFigure.fold(witnessIndex);
				witnessIndex = -1;
			}
		}
	}

	static void reset() {
		BEATS.clear();
		witnessIndex = -1;
	}

	private StoryM3Fx() {
	}
}
