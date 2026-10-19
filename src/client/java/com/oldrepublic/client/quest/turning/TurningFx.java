package com.oldrepublic.client.quest.turning;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.enemy.EnemyParticleProviders;
import com.oldrepublic.client.force.particle.ForceParticle;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.holocron.GatekeeperFigure;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.enemy.EnemyParticles;
import com.oldrepublic.force.ForceParticles;
import com.oldrepublic.holocron.HolocronCatalog;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.quest.turning.TurningEffects;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * The turning's and rank 4's looks (spec MILESTONE_5.md 15.1, 15.3), within the budgets of 15.1: dust, motes, embers and
 * ash, never a ring, sigil, halo or glow outline, and nothing that flashes or shakes.
 *
 * <ul>
 * <li>{@code turning} 0, an offer arrives: no look beyond the messenger; the {@code story.turning.offer} sting.</li>
 * <li>{@code turning} 1, the Fall's rite: a cold wind crosses the camp, lifting grey grit off the ground and smothering
 * the fire's glow (30 {@code gather_dust} over 40 ticks).</li>
 * <li>{@code turning} 2, the Return's rite: the dust in the air settles around the player and the fire brightens, warm
 * motes rising from it (30 {@code gather_dust} over 40 ticks). Both rites' sound is the banner's ({@code lesson_complete}
 * 12, W4).</li>
 * <li>{@code rank_four} 0, Master: the light of Saelen's blade laid flat across the player's open hands, then pale motes
 * falling slowly around them (20 {@code crystal_glint}).</li>
 * <li>{@code rank_four} 1, Darth: Sorn's writ burns from its edges as he reads the name (12 {@code flare_ember}), and its
 * ash lifts (16 {@code tomb_dust}).</li>
 * <li>{@code rank_four} 2, Wayfarer: Tessaly Arn's figure folds into the Compass and forms again a step aside, and dust
 * lifts along the road beyond the pedestal (16 {@code gather_dust}); she folds back after a while.</li>
 * </ul>
 *
 * <p>The ceremony's player hears {@code story.rank_four} from the banner ({@code lesson_complete} 13); everyone else near
 * hears it from here, at the ceremony. Each effect is a few timed beats kept in a list and run from the client tick; a
 * level change or a disconnect drops them.
 */
final class TurningFx {
	private static final RandomSource RANDOM = RandomSource.create();
	/** Rites: 30 gather_dust over 40 ticks, as 10 beats of 3. */
	private static final int RITE_BEATS = 10;
	private static final int RITE_STEP = 4;
	private static final int RITE_PER_BEAT = 3;
	/** How far from the player a camp fire is looked for (blocks, horizontally; half that vertically). */
	private static final int FIRE_SEARCH = 10;
	/** Master: 8 glints along the blade, then 12 falling motes in 6 beats of 2. */
	private static final int BLADE_GLINTS = 8;
	private static final int MOTE_BEATS = 6;
	private static final int MOTES_PER_BEAT = 2;
	/** Darth: 12 embers in 4 beats of 3 along the writ's edges, 16 ash in 6 beats. */
	private static final int EMBER_BEATS = 4;
	private static final int EMBERS_PER_BEAT = 3;
	private static final int[] ASH_PER_BEAT = {3, 3, 3, 3, 2, 2};
	/** Wayfarer: the figure folds, forms again aside after this many ticks, and folds back after the second. */
	private static final int STEP_ASIDE = 20;
	private static final int FIGURE_STAYS = 90;
	/** Wayfarer: 16 gather_dust along the road in 4 beats of 4. */
	private static final int ROAD_BEATS = 4;
	private static final int ROAD_PER_BEAT = 4;

	private static final float[] COLD_GRIT = {0.62f, 0.66f, 0.70f};
	private static final float[] SMOTHER = {0.36f, 0.36f, 0.38f};
	private static final float[] SETTLING = {0.84f, 0.77f, 0.62f};
	private static final float[] FIRE_MOTE = {1.0f, 0.72f, 0.38f};
	private static final float[] ROAD_DUST = {0.80f, 0.68f, 0.46f};
	private static final int WRIT_EMBER = 0xFF9A4A;

	private record Beat(int at, Runnable action) {
	}

	private static final List<Beat> BEATS = new ArrayList<>();
	private static int ticks;
	private static @Nullable ClientLevel lastLevel;

	// ---------------------------------------------------------------- turning

	static void turning(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		switch (payload.variant()) {
			case TurningEffects.OFFER -> QuestSounds.ui(ModSounds.STORY_TURNING_OFFER, 1f, 0.9f);
			case TurningEffects.RITE_FALL -> rite(level, feet(payload, entity), true);
			case TurningEffects.RITE_RETURN -> rite(level, feet(payload, entity), false);
			default -> {
			}
		}
	}

	/** The rite's wind (the Fall) or stillness (the Return) through the camp around {@code feet}. */
	private static void rite(ClientLevel level, Vec3 feet, boolean fall) {
		if (!Vfx.inRange(feet)) {
			return;
		}

		Vec3 fire = fire(level, BlockPos.containing(feet));
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		Vec3 wind = new Vec3(Math.cos(angle), 0, Math.sin(angle));

		for (int beat = 0; beat < RITE_BEATS; beat++) {
			boolean atFire = fire != null && beat % 2 == 1;
			later(beat * RITE_STEP, () -> {
				int count = Vfx.count(RITE_PER_BEAT);

				for (int i = 0; i < count; i++) {
					if (atFire && i == 0) {
						fireMote(level, fire, wind, fall);
					} else if (fall) {
						gust(level, feet, wind);
					} else {
						settle(level, feet);
					}
				}
			});
		}
	}

	/** A grain of grey grit lifted off the ground upwind and carried across the camp. */
	private static void gust(ClientLevel level, Vec3 feet, Vec3 wind) {
		Vec3 side = new Vec3(-wind.z, 0, wind.x);
		Vec3 from = feet.subtract(wind.scale(2.5 + RANDOM.nextDouble() * 2)).add(side.scale((RANDOM.nextDouble() - 0.5) * 6)).add(0, 0.05, 0);
		Vec3 vel = wind.scale(0.14 + RANDOM.nextDouble() * 0.08).add(0, 0.02 + RANDOM.nextDouble() * 0.04, 0);
		ForceParticle p = dust(from, vel);

		if (p != null) {
			p.color(COLD_GRIT[0], COLD_GRIT[1], COLD_GRIT[2]).size(0.06f, 0.03f).life(26 + RANDOM.nextInt(10)).drag(0.97f);
		}
	}

	/** A mote of dust high in the air drifting down to rest around the player. */
	private static void settle(ClientLevel level, Vec3 feet) {
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		double r = 0.8 + RANDOM.nextDouble() * 2.6;
		Vec3 from = feet.add(Math.cos(angle) * r, 1.6 + RANDOM.nextDouble() * 1.2, Math.sin(angle) * r);
		ForceParticle p = dust(from, new Vec3(0, -0.035 - RANDOM.nextDouble() * 0.02, 0));

		if (p != null) {
			p.color(SETTLING[0], SETTLING[1], SETTLING[2]).size(0.06f, 0.05f).life(40 + RANDOM.nextInt(12)).drag(0.985f);
		}
	}

	/** At the fire: grit blown over it (the Fall; it gutters) or a warm mote rising from it (the Return; it brightens). */
	private static void fireMote(ClientLevel level, Vec3 fire, Vec3 wind, boolean fall) {
		if (fall) {
			Vec3 from = fire.subtract(wind.scale(0.6)).add((RANDOM.nextDouble() - 0.5) * 0.4, 0.1, (RANDOM.nextDouble() - 0.5) * 0.4);
			ForceParticle p = dust(from, wind.scale(0.06).add(0, -0.01, 0));

			if (p != null) {
				p.color(SMOTHER[0], SMOTHER[1], SMOTHER[2]).size(0.09f, 0.12f).life(24).drag(0.95f);
			}
		} else {
			Vec3 from = fire.add((RANDOM.nextDouble() - 0.5) * 0.4, 0.2, (RANDOM.nextDouble() - 0.5) * 0.4);
			ForceParticle p = dust(from, new Vec3(0, 0.05 + RANDOM.nextDouble() * 0.03, 0));

			if (p != null) {
				p.color(FIRE_MOTE[0], FIRE_MOTE[1], FIRE_MOTE[2]).size(0.05f, 0.02f).life(30).fullBright();
			}
		}
	}

	/** The top of the nearest lit campfire around {@code at}, or null when the camp has none. */
	private static @Nullable Vec3 fire(ClientLevel level, BlockPos at) {
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		int half = FIRE_SEARCH / 2;

		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-FIRE_SEARCH, -half, -FIRE_SEARCH), at.offset(FIRE_SEARCH, half, FIRE_SEARCH))) {
			BlockState state = level.getBlockState(pos);

			if (state.is(BlockTags.CAMPFIRES) && state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
				double d = pos.distSqr(at);

				if (d < bestDist) {
					bestDist = d;
					best = pos.immutable();
				}
			}
		}

		return best == null ? null : Vec3.atBottomCenterOf(best).add(0, 0.5, 0);
	}

	// ---------------------------------------------------------------- rank_four

	static void rankFour(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		boolean mine = client.player != null && payload.entityId() == client.player.getId();

		if (!mine) {
			// The ceremony's player hears it from the banner; the others hear it here.
			QuestSounds.at(level, payload.pos(), ModSounds.STORY_RANK_FOUR, 1f, 1f);
		}

		switch (payload.variant()) {
			case TurningEffects.MASTER -> master(level, feet(payload, entity), toPlayer(payload));
			case TurningEffects.DARTH -> darth(level, feet(payload, entity), toPlayer(payload));
			case TurningEffects.WAYFARER -> wayfarer(level, payload, entity);
			default -> {
			}
		}
	}

	/** The blade's light across the open hands, then pale motes falling slowly around the new Master. */
	private static void master(ClientLevel level, Vec3 feet, Vec3 toPlayer) {
		if (!Vfx.inRange(feet)) {
			return;
		}

		// The player faces the mentor: the hands are a little toward her, the blade lies across them.
		Vec3 hands = feet.subtract(toPlayer.scale(0.35)).add(0, 1.0, 0);
		Vec3 side = new Vec3(-toPlayer.z, 0, toPlayer.x);
		int glints = Vfx.count(BLADE_GLINTS);

		for (int i = 0; i < glints; i++) {
			double along = glints == 1 ? 0 : i / (double) (glints - 1) - 0.5;
			Vec3 at = hands.add(side.scale(along * 1.0));
			later(i % 2 == 0 ? 0 : 3, () -> level.addParticle(WorldParticles.CRYSTAL_GLINT, at.x, at.y, at.z, 0, 0.002, 0));
		}

		for (int beat = 0; beat < MOTE_BEATS; beat++) {
			later(8 + beat * 6, () -> {
				for (int i = 0; i < Vfx.count(MOTES_PER_BEAT); i++) {
					double angle = RANDOM.nextDouble() * Math.PI * 2;
					double r = 0.3 + RANDOM.nextDouble() * 0.7;
					level.addParticle(WorldParticles.CRYSTAL_GLINT, feet.x + Math.cos(angle) * r, feet.y + 1.9 + RANDOM.nextDouble() * 0.6,
							feet.z + Math.sin(angle) * r, 0, -0.03 - RANDOM.nextDouble() * 0.015, 0);
				}
			});
		}
	}

	/** The writ held up between them burns from its edges inward, and its ash lifts. */
	private static void darth(ClientLevel level, Vec3 feet, Vec3 toPlayer) {
		Vec3 writ = feet.subtract(toPlayer.scale(0.9)).add(0, 1.3, 0);

		if (!Vfx.inRange(writ)) {
			return;
		}

		Vec3 side = new Vec3(-toPlayer.z, 0, toPlayer.x);

		for (int beat = 0; beat < EMBER_BEATS; beat++) {
			// Each beat burns a little nearer the middle of the sheet.
			double inset = beat * 0.05;
			later(beat * 6, () -> {
				for (int i = 0; i < Vfx.count(EMBERS_PER_BEAT); i++) {
					Vec3 at = writ.add(edgePoint(side, 0.25 - inset, 0.18 - inset * 0.7));
					EnemyParticleProviders.EnemyParticle ember = EnemyParticleProviders.spawn(EnemyParticles.FLARE_EMBER, at,
							new Vec3((RANDOM.nextDouble() - 0.5) * 0.02, 0.01 + RANDOM.nextDouble() * 0.02, (RANDOM.nextDouble() - 0.5) * 0.02));

					if (ember != null) {
						ember.tint(WRIT_EMBER).life(16 + RANDOM.nextInt(8));
					}
				}
			});
		}

		for (int beat = 0; beat < ASH_PER_BEAT.length; beat++) {
			int base = ASH_PER_BEAT[beat];
			later(6 + beat * 6, () -> {
				for (int i = 0; i < Vfx.count(base); i++) {
					Vec3 at = writ.add(side.scale((RANDOM.nextDouble() - 0.5) * 0.5)).add(0, (RANDOM.nextDouble() - 0.5) * 0.3, 0);
					level.addParticle(WorldParticles.TOMB_DUST, at.x, at.y, at.z, (RANDOM.nextDouble() - 0.5) * 0.02, 0.03 + RANDOM.nextDouble() * 0.03,
							(RANDOM.nextDouble() - 0.5) * 0.02);
				}
			});
		}
	}

	/** A point on the edge of a sheet {@code 2 * halfW} wide and {@code 2 * halfH} tall, facing along the writ's normal. */
	private static Vec3 edgePoint(Vec3 side, double halfW, double halfH) {
		double w = Math.max(0.02, halfW);
		double h = Math.max(0.02, halfH);
		double t = RANDOM.nextDouble() * 2 - 1;

		return switch (RANDOM.nextInt(4)) {
			case 0 -> side.scale(t * w).add(0, h, 0);
			case 1 -> side.scale(t * w).add(0, -h, 0);
			case 2 -> side.scale(w).add(0, t * h, 0);
			default -> side.scale(-w).add(0, t * h, 0);
		};
	}

	/**
	 * Tessaly Arn's figure folds into the Compass, forms again a step aside (pos = the pedestal's top, dir = toward the
	 * player), and dust lifts along the road beyond the pedestal. Held by an operator's command away from the Compass
	 * (no direction), only the dust lifts, around the player.
	 */
	private static void wayfarer(ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 flat = new Vec3(payload.dir().x, 0, payload.dir().z);
		int index = HolocronCatalog.gatekeeperIndex(HolocronCatalog.WAYFARERS_COMPASS);

		if (flat.lengthSqr() < 1.0e-4 || index < 0) {
			Vec3 feet = feet(payload, entity);
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			road(level, feet, new Vec3(Math.cos(angle), 0, Math.sin(angle)), feet.y);
			return;
		}

		Vec3 toward = flat.normalize();
		Vec3 top = payload.pos();
		BlockPos pedestal = BlockPos.containing(top.x, top.y - 1, top.z);
		// She steps to the side the road does not take; the road runs on beyond the pedestal, away from the player.
		double turn = RANDOM.nextBoolean() ? 1.0 : -1.0;
		Vec3 aside = new Vec3(toward.x * 0.5 - toward.z * 0.866 * turn, 0, toward.z * 0.5 + toward.x * 0.866 * turn);
		GatekeeperFigure.fold(index);
		later(STEP_ASIDE, () -> {
			GatekeeperFigure.show(pedestal, aside, index);
			QuestSounds.at(level, top, ModSounds.HOLOCRON_GATEKEEPER_FORM, 0.6f, 1.1f);
			Vec3 start = Vec3.atBottomCenterOf(pedestal);
			road(level, start, toward.reverse(), pedestal.getY());
		});
		later(STEP_ASIDE + FIGURE_STAYS, () -> GatekeeperFigure.fold(index));
	}

	/** Dust lifting in patches along a road that runs from {@code from} along {@code dir}, 1.5 to 8 blocks out. */
	private static void road(ClientLevel level, Vec3 from, Vec3 dir, double groundY) {
		if (!Vfx.inRange(from)) {
			return;
		}

		Vec3 side = new Vec3(-dir.z, 0, dir.x);

		for (int beat = 0; beat < ROAD_BEATS; beat++) {
			int step = beat;
			later(beat * 8, () -> {
				for (int i = 0; i < Vfx.count(ROAD_PER_BEAT); i++) {
					// Each beat further out, in uneven patches with gaps between them.
					double along = 1.5 + step * 1.7 + RANDOM.nextDouble() * 1.4;
					double across = (RANDOM.nextDouble() - 0.5) * 1.2;
					Vec3 at = new Vec3(from.x, groundY + 0.05, from.z).add(dir.scale(along)).add(side.scale(across));
					ForceParticle p = dust(at, dir.scale(0.02).add(0, 0.03 + RANDOM.nextDouble() * 0.03, 0));

					if (p != null) {
						p.color(ROAD_DUST[0], ROAD_DUST[1], ROAD_DUST[2]).size(0.06f, 0.04f).life(30 + RANDOM.nextInt(10)).drag(0.97f);
					}
				}
			});
		}
	}

	// ---------------------------------------------------------------- helpers

	private static @Nullable ForceParticle dust(Vec3 at, Vec3 velocity) {
		Particle particle = Minecraft.getInstance().particleEngine.createParticle(ForceParticles.GATHER_DUST, at.x, at.y, at.z, velocity.x, velocity.y,
				velocity.z);
		return particle instanceof ForceParticle force ? force : null;
	}

	/** The player's feet: the entity when this client has it, else the event's position. */
	private static Vec3 feet(EffectEventPayload payload, @Nullable Entity entity) {
		return entity != null ? entity.position() : payload.pos();
	}

	/** Unit horizontal vector from the mentor toward the player; south when the event has none. */
	private static Vec3 toPlayer(EffectEventPayload payload) {
		Vec3 flat = new Vec3(payload.dir().x, 0, payload.dir().z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	private static void later(int delay, Runnable action) {
		if (delay <= 0) {
			action.run();
		} else {
			BEATS.add(new Beat(ticks + delay, action));
		}
	}

	// ---------------------------------------------------------------- tick and reset

	static void tick(Minecraft mc) {
		ticks++;

		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			BEATS.clear();
			return;
		}

		if (BEATS.isEmpty() || mc.level == null) {
			return;
		}

		List<Beat> due = new ArrayList<>();

		for (Iterator<Beat> it = BEATS.iterator(); it.hasNext();) {
			Beat beat = it.next();

			if (ticks >= beat.at()) {
				it.remove();
				due.add(beat);
			}
		}

		due.forEach(beat -> beat.action().run());
	}

	static void reset() {
		BEATS.clear();
		lastLevel = null;
	}

	private TurningFx() {
	}
}
