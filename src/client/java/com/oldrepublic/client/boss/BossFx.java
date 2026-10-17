package com.oldrepublic.client.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.ArchiveDebris;
import com.oldrepublic.boss.BossBlocks;
import com.oldrepublic.boss.BossEffects;
import com.oldrepublic.boss.BossParticles;
import com.oldrepublic.boss.OrrenVasch;
import com.oldrepublic.boss.PylonFocus;
import com.oldrepublic.client.boss.BossParticleProviders.BossParticle;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.hud.Banners;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.WorldParticles;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client handlers for the fifteen Collector effect events (spec 13.3, {@link BossEffects}) and the short
 * running effects that follow them, plus the fight's ambient look:
 *
 * <ul>
 * <li>{@code boss_hurl}: the tell tears a chunk loose with grit pouring off it while it rises
 * ({@code hurl_lift}); the throw trails dust ({@code hurl_throw}); ripped, it falls between them in a
 * dust burst ({@code hurl_rip}); returned, a bent-air streak back at him ({@code hurl_return}); caught,
 * it hangs with grit trickling from it (the Lift's own loop is the sound).</li>
 * <li>{@code boss_palm}: dust drawn in toward the palm ({@code palm_tell}); an air shove and the M2
 * clash burst (the M2 acolyte shove sound).</li>
 * <li>{@code boss_lash}: a seam of floor dust lifts in a line toward the target ({@code lash_tell}); a
 * travelling ridge of dust and grit about a block high ({@code lash_wave}).</li>
 * <li>{@code boss_surge}: the gather streams floor dust and grit in toward him while his cloak lifts
 * ({@code surge_gather}); the release is {@link SurgeWallFx} ({@code surge_release}); the clash breaks the
 * wall around the player ({@code surge_clash}).</li>
 * <li>{@code boss_spent}: he sags, the holocron gutters, dust settles ({@code spent}).</li>
 * <li>{@code boss_grip}: pebbles and dust rise around the target's feet ({@code grip}); dust trickles off
 * the held player ({@code grip_hold}); a burst of grit ({@code grip_break}).</li>
 * <li>{@code boss_lunge}: dust kicked back from the crouch, then a streak ({@code lunge}).</li>
 * <li>{@code boss_ward}: the ward level drives {@link BossLoops}'s hum and the sparse haze specks off his
 * shoulders and blade; Unsung snaps the last currents back into the pylons and shakes dust from the
 * ceiling ({@code unsung}).</li>
 * <li>{@code boss_phase}: the phase banners ({@link Banners}), and for the transitions ceiling dust,
 * crystal light or shards streaming into him and a camera shake ({@code transition}); every variant
 * also drives {@link BossLoops}' stop rules (the music is B's {@code BossMusic}).</li>
 * <li>{@code boss_yield}, {@code boss_leash}, {@code boss_echo}, {@code collector_resolve},
 * {@code pylon_state}, {@code hoard_open}: as their 13.3 rows.</li>
 * </ul>
 *
 * <p>Ambient while he fights: ward haze in phase 2, thin wavering crystal currents from each singing
 * pylon to him, the holocron's crimson motes and the cloak's fraying dust in phase 3 (steady, never
 * flickering), and slow smoke at the echo's edges. Tell particles (variant 0 of hurl, palm, lash, surge,
 * grip and lunge) go through {@link Vfx#tellCount} and are never thinned by presets; everything else
 * goes through {@link Vfx#count}. With flashing effects off nothing flickers or flashes: transition light
 * and guttering sparks are steady glows.
 */
final class BossFx {
	/** The amber of his blade. */
	private static final int AMBER = 0xFFB347;
	/** The pale crystal of the pylons' currents. */
	private static final int CRYSTAL = 0xCFEFFF;
	/** The stolen holocron's crimson seams. */
	private static final int CRIMSON = 0xC8303C;
	/** The ward's heat haze: near-white, a little warm. */
	private static final int HAZE = 0xFFF2DC;
	/** The echo's smoke. */
	private static final int SMOKE = 0xA4A8AE;
	/** The cloak's fraying dust in phase 3: faded ochre. */
	private static final int FRAY = 0xA89272;
	/** The Singing Wall's banner accent. */
	private static final int PURPLE = 0xFFB08CE6;
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
	/** Ticks a running effect may run before the synced action has caught up with the event. */
	private static final int SYNC_GRACE = 4;
	/** Ticks the fall forward takes after the execution's stroke. */
	private static final float FALL_TICKS = 8f;
	/** The stroke lands this long after {@code collector_resolve} 2 (the scene's own timing). */
	private static final int STROKE_TICKS = 12;
	/** How far the pylons' currents and the Unsung snap-back look for pylons. */
	private static final double PYLON_RANGE = 32.0;
	private static final RandomSource RANDOM = RandomSource.create();

	private enum Kind {
		HURL_TELL(OrrenVasch.Action.HURL),
		CAUGHT(null),
		PALM_TELL(OrrenVasch.Action.PALM),
		LASH_TELL(OrrenVasch.Action.LASH),
		LASH_WAVE(null),
		SURGE_GATHER(OrrenVasch.Action.SURGE),
		SPENT(OrrenVasch.Action.SPENT),
		GRIP_TELL(null),
		GRIP_HOLD(null),
		LUNGE_TELL(OrrenVasch.Action.LUNGE),
		TRANSITION(null),
		ECHO_FORM(null),
		ECHO_DISSOLVE(null),
		REGROW(null),
		EXECUTION(null);

		/** The action the run follows (it ends when he leaves it), or null for a run with its own length. */
		final OrrenVasch.@Nullable Action action;

		Kind(OrrenVasch.@Nullable Action action) {
			this.action = action;
		}
	}

	private static final class Run {
		final int entityId;
		final Kind kind;
		final long start;
		final int length;
		final Vec3 pos;
		final Vec3 dir;
		final float value;

		Run(int entityId, Kind kind, long start, int length, Vec3 pos, Vec3 dir, float value) {
			this.entityId = entityId;
			this.kind = kind;
			this.start = start;
			this.length = Math.max(1, length);
			this.pos = pos;
			this.dir = dir;
			this.value = value;
		}
	}

	private static final List<Run> RUNS = new ArrayList<>();
	/** The echo's form and dissolve, by Vasch id (the renderer's presence). */
	private static final Map<Integer, Run> FORMS = new HashMap<>();
	private static final Map<Integer, Run> DISSOLVES = new HashMap<>();
	/** The client tick each Vasch was first seen fallen, and whether the stroke was seen (then the fall animates). */
	private static final Map<Integer, Long> FALLEN = new HashMap<>();
	private static final Map<Integer, Long> STROKES = new HashMap<>();
	private static @Nullable ClientLevel lastLevel;
	private static long ticks;

	static void init() {
		ClientEffects.register(BossEffects.HURL, BossFx::onHurl);
		ClientEffects.register(BossEffects.PALM, BossFx::onPalm);
		ClientEffects.register(BossEffects.LASH, BossFx::onLash);
		ClientEffects.register(BossEffects.SURGE, BossFx::onSurge);
		ClientEffects.register(BossEffects.SPENT, BossFx::onSpent);
		ClientEffects.register(BossEffects.GRIP, BossFx::onGrip);
		ClientEffects.register(BossEffects.LUNGE, BossFx::onLunge);
		ClientEffects.register(BossEffects.WARD, BossFx::onWard);
		ClientEffects.register(BossEffects.PHASE, BossFx::onPhase);
		ClientEffects.register(BossEffects.YIELD, BossFx::onYield);
		ClientEffects.register(BossEffects.LEASH, BossFx::onLeash);
		ClientEffects.register(BossEffects.ECHO, BossFx::onEcho);
		ClientEffects.register(BossEffects.RESOLVE, BossFx::onResolve);
		ClientEffects.register(BossEffects.PYLON_STATE, BossFx::onPylon);
		ClientEffects.register(BossEffects.HOARD_OPEN, BossFx::onHoard);
		ClientTickEvents.END_CLIENT_TICK.register(BossFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	// ---------------------------------------------------------------- renderer queries

	/** The echo's presence, 0 to 1: gathering out of smoke over its form, draining as it dissolves. */
	static float presence(OrrenVasch vasch, float partialTick) {
		Run dissolve = DISSOLVES.get(vasch.getId());

		if (dissolve != null) {
			return 1f - Mth.clamp((ticks - dissolve.start + partialTick) / dissolve.length, 0f, 1f);
		}

		Run form = FORMS.get(vasch.getId());

		if (form != null) {
			return Mth.clamp((ticks - form.start + partialTick) / form.length, 0f, 1f);
		}

		// The form event was missed (joined late): ease in from the entity's own age.
		return vasch.isForming() ? Mth.clamp((vasch.tickCount + partialTick) / 60f, 0f, 1f) : 1f;
	}

	/** How far he has fallen forward after the stroke, 0 to 1; already lying when first seen without the stroke. */
	static float fall(OrrenVasch vasch, float partialTick) {
		if (!STROKES.containsKey(vasch.getId())) {
			return 1f;
		}

		// The stroke was seen: he falls from the tick his fallen flag first shows.
		Long since = FALLEN.get(vasch.getId());
		return since == null ? 0f : Mth.clamp((ticks - since + partialTick) / FALL_TICKS, 0f, 1f);
	}

	// ---------------------------------------------------------------- boss_hurl

	private static void onHurl(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 pos = p.pos();

		switch (p.variant()) {
			case BossEffects.HURL_LIFT -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_HURL_LIFT, 1.1f);
				start(p.entityId(), Kind.HURL_TELL, Math.round(p.intensity()), pos, Vec3.ZERO, 0f);

				if (Vfx.inRange(pos)) {
					// The chunk tears loose from the pile: chips and a little dust.
					BlockState ground = groundAt(level, pos);

					for (int i = 0; i < Vfx.tellCount(5); i++) {
						Vec3 v = jitter(0.08).add(0, 0.12 + RANDOM.nextDouble() * 0.1, 0);
						block(level, ground.isAir() ? COBBLE : ground, pos.add(jitter(0.35)), v);
					}

					for (int i = 0; i < Vfx.tellCount(3); i++) {
						dust(pos.add(jitter(0.3)), jitter(0.03).add(0, 0.03, 0), 0.18f);
					}
				}
			}
			case BossEffects.HURL_THROW -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_HURL_THROW, 1.1f);

				if (Vfx.inRange(pos)) {
					Vec3 back = normal(p.dir()).scale(-0.06);

					for (int i = 0; i < Vfx.count(4); i++) {
						dust(pos.add(jitter(0.25)), back.add(jitter(0.02)), 0.16f);
					}
				}
			}
			case BossEffects.HURL_RIPPED -> {
				// Intensity 1: ripped away by a Pull and falling between them; 0: crumbled where it struck.
				boolean ripped = p.intensity() >= 1f;
				play(level, pos, ModSounds.BOSS_COLLECTOR_HURL_RIP, ripped ? 1.2f : 0.8f);

				if (Vfx.inRange(pos)) {
					BlockState ground = groundAt(level, pos);

					for (int i = 0; i < Vfx.count(10); i++) {
						Vec3 v = jitter(0.15).add(0, 0.1 + RANDOM.nextDouble() * 0.15, 0);
						block(level, i % 2 == 0 || ground.isAir() ? COBBLE : ground, pos.add(jitter(0.3)), v);
					}

					for (int i = 0; i < Vfx.count(6); i++) {
						double angle = RANDOM.nextDouble() * Mth.TWO_PI;
						dust(pos.add(0, -0.2, 0), new Vec3(Math.cos(angle) * 0.12, 0.02, Math.sin(angle) * 0.12), 0.3f);
					}
				}

				if (ripped) {
					shakeNear(mc, pos, 6.0, 0.1f);
				}
			}
			case BossEffects.HURL_RETURNED -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_HURL_RETURN, 1.1f);

				if (Vfx.inRange(pos)) {
					// A bent-air streak along the new path, back toward him.
					Vec3 dir = normal(p.dir());

					for (int i = 0; i < Vfx.count(8); i++) {
						Vec3 at = pos.add(dir.scale(RANDOM.nextDouble() * 2.5)).add(jitter(0.15));
						level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, dir.x * 0.25, dir.y * 0.25, dir.z * 0.25);
					}
				}
			}
			case BossEffects.HURL_CAUGHT -> start(p.entityId(), Kind.CAUGHT, 400, pos, Vec3.ZERO, 0f);
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- boss_palm

	private static void onPalm(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 pos = p.pos();

		switch (p.variant()) {
			case BossEffects.PALM_TELL -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_PALM_TELL, 1f);
				start(p.entityId(), Kind.PALM_TELL, Math.round(p.intensity()), pos, p.dir(), 0f);
			}
			case BossEffects.PALM_RELEASE -> {
				play(level, pos, ModSounds.ACOLYTE_SHOVE, 1.1f, 0.85f);

				if (Vfx.inRange(pos)) {
					// The air shove: a cone of pressure rolling out along his facing, dust off the floor.
					Vec3 dir = normal(p.dir());

					for (int i = 0; i < Vfx.count(14); i++) {
						Vec3 v = dir.add(jitter(0.3)).normalize().scale(0.3 + RANDOM.nextDouble() * 0.2);
						level.addParticle(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, v.x, v.y * 0.5, v.z);
					}

					if (entity != null) {
						for (int i = 0; i < Vfx.count(4); i++) {
							dust(entity.position().add(dir.scale(0.6)).add(jitter(0.3).multiply(1, 0, 1)), dir.scale(0.15).add(0, 0.02, 0), 0.22f);
						}
					}
				}

				shakeNear(mc, pos, 6.0, 0.15f);
			}
			case BossEffects.PALM_CLASH -> {
				// The M2 clash burst: the palm snuffed out, he reels.
				play(level, pos, ModSounds.ACOLYTE_SHOVE, 1f, 0.6f);

				if (Vfx.inRange(pos)) {
					for (int i = 0; i < Vfx.count(14); i++) {
						dust(pos, jitter(0.25), 0.2f);
					}

					if (Vfx.flashing()) {
						level.addParticle(ParticleTypes.EXPLOSION, pos.x, pos.y, pos.z, 0, 0, 0);
					} else {
						for (int i = 0; i < 3; i++) {
							level.addParticle(ParticleTypes.POOF, pos.x, pos.y, pos.z, 0, 0.01, 0);
						}
					}
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- boss_lash

	private static void onLash(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		if (p.variant() == BossEffects.LASH_TELL) {
			play(level, p.pos(), ModSounds.BOSS_COLLECTOR_LASH_TELL, 1f);
			Vec3 dir = normal(p.dir().multiply(1, 0, 1));
			// The seam shows as far as the floor runs straight (clipped like the ridge will be).
			float reach = (float) lineReach(level, p.pos(), dir, 14.0, entity != null ? entity : mc.player);
			start(p.entityId(), Kind.LASH_TELL, Math.round(p.intensity()), p.pos(), dir, reach);
			return;
		}

		play(level, p.pos(), ModSounds.BOSS_COLLECTOR_LASH_WAVE, 1.2f);
		double speed = Math.max(0.1, p.dir().length());
		int length = (int) Math.ceil(Math.max(1f, p.intensity()) / speed);
		start(p.entityId(), Kind.LASH_WAVE, length, p.pos(), p.dir(), p.intensity());
	}

	// ---------------------------------------------------------------- boss_surge

	private static void onSurge(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 pos = p.pos();

		switch (p.variant()) {
			case BossEffects.SURGE_GATHER -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_SURGE_GATHER, 1.2f);
				start(p.entityId(), Kind.SURGE_GATHER, Math.round(p.intensity()), entity != null ? entity.position() : pos.subtract(0, 1.365, 0), Vec3.ZERO, 0f);
			}
			case BossEffects.SURGE_RELEASE -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_SURGE_RELEASE, 1.5f);
				float radius = Math.max(1f, p.intensity());

				if (Vfx.inRange(pos)) {
					SurgeWallFx.release(level, pos, radius, entity);
				}

				if (mc.player != null) {
					double distance = mc.player.position().distanceTo(pos);

					if (distance < radius + 2.0) {
						CameraShake.add(0.1f + 0.25f * (float) (1.0 - distance / (radius + 2.0)));
					}
				}
			}
			case BossEffects.SURGE_CLASH -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_SURGE_CLASH, 1.3f);

				if (Vfx.inRange(pos)) {
					SurgeWallFx.clash(level, pos, p.dir(), p.intensity());
				}
			}
			default -> {
			}
		}
	}

	private static void onSpent(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		play(level, p.pos(), ModSounds.BOSS_COLLECTOR_SPENT, 1f);
		start(p.entityId(), Kind.SPENT, Math.round(p.intensity()), p.pos(), Vec3.ZERO, 0f);
	}

	// ---------------------------------------------------------------- boss_grip (entity = the target)

	private static void onGrip(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity target) {
		Vec3 pos = p.pos();

		switch (p.variant()) {
			case BossEffects.GRIP_TELL -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_GRIP, 1.1f);
				start(p.entityId(), Kind.GRIP_TELL, Math.round(p.intensity()), pos, Vec3.ZERO, 0f);
			}
			case BossEffects.GRIP_HOLD -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_GRIP_HOLD, p.intensity() >= 1f ? 1f : 0.7f);
				start(p.entityId(), Kind.GRIP_HOLD, 20, pos, Vec3.ZERO, 0f);
			}
			case BossEffects.GRIP_BREAK -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_GRIP_BREAK, 1.2f);
				RUNS.removeIf(run -> run.entityId == p.entityId() && (run.kind == Kind.GRIP_TELL || run.kind == Kind.GRIP_HOLD));
				Vec3 body = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : pos.add(0, 0.9, 0);

				if (Vfx.inRange(body)) {
					for (int i = 0; i < Vfx.count(12); i++) {
						block(level, COBBLE, body.add(jitter(0.3)), jitter(0.2).add(0, 0.08, 0));
					}

					for (int i = 0; i < Vfx.count(4); i++) {
						dust(body.add(jitter(0.3)), jitter(0.08), 0.22f);
					}
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- boss_lunge

	private static void onLunge(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 pos = p.pos();
		Vec3 dir = normal(p.dir().multiply(1, 0, 1));

		if (p.variant() == BossEffects.LUNGE_TELL) {
			play(level, pos, ModSounds.BOSS_COLLECTOR_LUNGE, 0.8f, 0.8f);
			start(p.entityId(), Kind.LUNGE_TELL, Math.round(p.intensity()), pos, dir, 0f);

			if (Vfx.inRange(pos)) {
				// Dust kicked back from the crouch.
				for (int i = 0; i < Vfx.tellCount(5); i++) {
					dust(pos.add(jitter(0.3).multiply(1, 0, 1)).add(0, 0.1, 0), dir.scale(-0.15).add(jitter(0.04)).add(0, 0.03, 0), 0.2f);
				}
			}

			return;
		}

		play(level, pos, ModSounds.BOSS_COLLECTOR_LUNGE, 1.2f, 1.1f);

		if (!Vfx.inRange(pos)) {
			return;
		}

		// The streak along the lunge, and dust where he pushed off.
		for (int i = 0; i < Vfx.count(10); i++) {
			Vec3 at = pos.add(dir.scale(RANDOM.nextDouble() * 6.0)).add(0, 0.9 + RANDOM.nextDouble() * 0.5, 0);
			level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, dir.x * 0.12, 0, dir.z * 0.12);
		}

		for (int i = 0; i < Vfx.count(4); i++) {
			dust(pos.add(jitter(0.3).multiply(1, 0, 1)).add(0, 0.1, 0), dir.scale(-0.1).add(0, 0.03, 0), 0.24f);
		}
	}

	// ---------------------------------------------------------------- boss_ward

	private static void onWard(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		if (p.variant() == BossEffects.WARD_LEVEL) {
			BossLoops.ward(p.entityId(), Math.round(p.intensity()));
			return;
		}

		// Unsung: the last currents snap back into the pylons, he reels, dust shakes from the ceiling.
		play(level, p.pos(), ModSounds.BOSS_COLLECTOR_UNSUNG, 1.4f);
		BossLoops.wardDown(p.entityId());
		shakeNear(mc, p.pos(), 12.0, 0.2f);

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		Vec3 chest = entity != null ? entity.position().add(0, entity.getBbHeight() * 0.7, 0) : p.pos().add(0, 1.4, 0);

		for (BlockPos pylon : BossLoops.pylonsNear(p.pos(), PYLON_RANGE)) {
			Vec3 crystal = crystalOf(pylon);
			Vec3 path = crystal.subtract(chest);

			for (int i = 0; i < Vfx.count(5); i++) {
				Vec3 from = chest.add(path.scale(RANDOM.nextDouble() * 0.3));
				current(from, path.scale(1.0 / 8.0), CRYSTAL, 8);
			}
		}

		ceilingDust(level, p.pos(), 5.0, Vfx.count(16));
	}

	// ---------------------------------------------------------------- boss_phase (to each participant)

	private static void onPhase(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		BossLoops.phase(p.variant());

		if (p.intensity() < 1f) {
			// A sync-only resend for a joining or returning participant: no banner, no transition.
			return;
		}

		boolean echo = entity instanceof OrrenVasch vasch && vasch.isEcho();
		Component title = Component.translatable(echo ? "boss.oldrepublic.collector.banner.echo" : "boss.oldrepublic.collector.banner.title");

		switch (p.variant()) {
			case BossEffects.PHASE_START -> Banners.show(title, Component.translatable("boss.oldrepublic.collector.banner.archivist"), UiColors.TEXT);
			case BossEffects.PHASE_TRANSITION_ONE, BossEffects.PHASE_TRANSITION_TWO -> {
				boolean one = p.variant() == BossEffects.PHASE_TRANSITION_ONE;
				Banners.show(title, Component.translatable(one ? "boss.oldrepublic.collector.banner.singing_wall" : "boss.oldrepublic.collector.banner.unkept"),
						one ? PURPLE : UiColors.DANGER);
				play(level, p.pos(), ModSounds.BOSS_COLLECTOR_TRANSITION, 1.5f, one ? 1f : 0.85f);
				CameraShake.add(one ? 0.3f : 0.4f);
				start(p.entityId(), Kind.TRANSITION, 60, p.pos(), Vec3.ZERO, one ? 1f : 2f);
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- boss_yield, boss_leash, boss_echo

	private static void onYield(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		play(level, p.pos(), ModSounds.BOSS_COLLECTOR_YIELD, 1.2f);
		BossLoops.wardDown(p.entityId());

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		// The crystal light dims (a few motes sinking), the dust settles.
		for (int i = 0; i < Vfx.count(5); i++) {
			BossParticle mote = current(p.pos().add(jitter(0.5)).add(0, 1.2 + RANDOM.nextDouble() * 0.6, 0), new Vec3(0, -0.02, 0), CRYSTAL, 24);

			if (mote != null) {
				mote.steady().alpha(0.5f);
			}
		}

		fallingDust(level, p.pos().add(0, 1.8, 0), 0.6, Vfx.count(12), STONE);
	}

	private static void onLeash(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		boolean vanish = p.variant() == BossEffects.LEASH_VANISH;
		play(level, p.pos(), ModSounds.BOSS_COLLECTOR_LEASH, vanish ? 1.1f : 0.85f, vanish ? 1f : 1.15f);

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		// A puff of smoke where he was; smoke and settling dust where he reappears.
		for (int i = 0; i < Vfx.count(vanish ? 14 : 10); i++) {
			Vec3 at = p.pos().add(jitter(0.35).multiply(1, 0, 1)).add(0, RANDOM.nextDouble() * 1.9, 0);
			smoke(at, jitter(0.05).add(0, 0.01, 0));
		}

		if (!vanish) {
			fallingDust(level, p.pos().add(0, 2.2, 0), 0.6, Vfx.count(8), STONE);
		}
	}

	private static void onEcho(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		int length = Math.max(1, Math.round(p.intensity()));

		if (p.variant() == BossEffects.ECHO_FORM) {
			play(level, p.pos(), ModSounds.BOSS_COLLECTOR_ECHO_FORM, 1.2f);
			Run run = start(p.entityId(), Kind.ECHO_FORM, length, p.pos(), Vec3.ZERO, 0f);
			FORMS.put(p.entityId(), run);
			DISSOLVES.remove(p.entityId());
			return;
		}

		play(level, p.pos(), ModSounds.BOSS_COLLECTOR_ECHO_FADE, 1.2f);
		Run run = start(p.entityId(), Kind.ECHO_DISSOLVE, length, p.pos(), Vec3.ZERO, 0f);
		DISSOLVES.put(p.entityId(), run);
		FORMS.remove(p.entityId());
	}

	// ---------------------------------------------------------------- collector_resolve

	private static void onResolve(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 pos = p.pos();

		switch (p.variant()) {
			case BossEffects.RESOLVE_CUSTODY, BossEffects.RESOLVE_BOUND, BossEffects.RESOLVE_BROUGHT_IN -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_BINDERS, 1f);

				if (Vfx.inRange(pos)) {
					Vec3 hands = pos.add(normal(entity != null ? entity.getLookAngle().multiply(1, 0, 1) : Vec3.ZERO).scale(0.35)).add(0, 1.0, 0);

					for (int i = 0; i < Vfx.count(4); i++) {
						level.addParticle(ParticleTypes.POOF, hands.x, hands.y, hands.z, 0, 0.01, 0);
					}
				}
			}
			case BossEffects.RESOLVE_EXECUTE -> {
				STROKES.put(p.entityId(), ticks);
				start(p.entityId(), Kind.EXECUTION, 40, pos, p.dir(), 0f);
			}
			case BossEffects.RESOLVE_WALK_OUT_SITH -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_WALK_OUT, 1f);

				// The blade set down on the dais: a last steady amber glow at its emitter.
				if (Vfx.inRange(pos)) {
					for (int i = 0; i < Vfx.count(3); i++) {
						BossParticle glow = current(pos.add(jitter(0.25).multiply(1, 0, 1)).add(0, 0.1, 0), new Vec3(0, 0.005, 0), AMBER, 30);

						if (glow != null) {
							glow.steady();
						}
					}
				}
			}
			case BossEffects.RESOLVE_RELEASE -> {
				play(level, pos, ModSounds.BOSS_COLLECTOR_WALK_OUT, 1f);

				if (Vfx.inRange(pos)) {
					fallingDust(level, pos.add(0, 1.8, 0), 0.4, Vfx.count(4), STONE);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- pylon_state

	private static void onPylon(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity vasch) {
		Vec3 crystal = p.pos();
		BlockPos pylon = BlockPos.containing(crystal.x, crystal.y - PylonFocus.CRYSTAL_HEIGHT - 0.4 + 0.5, crystal.z);
		BossLoops.notePylon(pylon);
		BlockState chips = level.getBlockState(pylon).isAir() ? Blocks.AMETHYST_BLOCK.defaultBlockState() : level.getBlockState(pylon);

		switch (p.variant()) {
			case BossEffects.PYLON_SING, BossEffects.PYLON_RESUNG -> {
				BossLoops.pylonSings(mc, pylon);

				if (Vfx.inRange(crystal)) {
					for (int i = 0; i < Vfx.count(6); i++) {
						Vec3 at = crystal.add(jitter(0.3));
						level.addParticle(WorldParticles.CRYSTAL_GLINT, at.x, at.y, at.z, 0, 0.01, 0);
					}
				}
			}
			case BossEffects.PYLON_HIT -> {
				// Higher and sharper as its resonance runs out.
				float pitch = 1.25f - Mth.clamp(p.intensity() / 60f, 0f, 0.4f);
				play(level, crystal, ModSounds.PYLON_CRACK, 0.9f, pitch, SoundSource.BLOCKS);

				if (Vfx.inRange(crystal)) {
					for (int i = 0; i < Vfx.count(5); i++) {
						block(level, chips, crystal.add(jitter(0.2)), jitter(0.12).add(0, 0.08, 0));
					}

					for (int i = 0; i < Vfx.count(2); i++) {
						Vec3 at = crystal.add(jitter(0.25));
						level.addParticle(WorldParticles.CRYSTAL_GLINT, at.x, at.y, at.z, 0, 0, 0);
					}
				}
			}
			case BossEffects.PYLON_SILENCED -> {
				boolean ripped = p.intensity() >= 1f;
				play(level, crystal, ModSounds.PYLON_SILENCE, 1.2f, 0.95f + RANDOM.nextFloat() * 0.1f, SoundSource.BLOCKS);
				BossLoops.pylonQuiet(pylon);

				if (Vfx.inRange(crystal)) {
					// The crystal cracks dark: chips and a fall of guttering glints.
					for (int i = 0; i < Vfx.count(6); i++) {
						block(level, chips, crystal.add(jitter(0.25)), jitter(0.1).add(0, 0.05, 0));
					}

					for (int i = 0; i < Vfx.count(8); i++) {
						Vec3 at = crystal.add(jitter(0.3));
						level.addParticle(WorldParticles.CRYSTAL_GLINT, at.x, at.y, at.z, 0, -0.04 - RANDOM.nextDouble() * 0.04, 0);
					}

					if (ripped) {
						for (int i = 0; i < Vfx.count(4); i++) {
							dust(crystal.add(jitter(0.3)), jitter(0.06), 0.2f);
						}
					}
				}
			}
			case BossEffects.PYLON_REGROW -> {
				play(level, crystal, ModSounds.PYLON_REGROW, 1f, 1f, SoundSource.BLOCKS);
				start(-1, Kind.REGROW, Math.round(p.intensity()), crystal, Vec3.ZERO, 0f);
			}
			case BossEffects.PYLON_SHATTER -> {
				play(level, crystal, ModSounds.PYLON_SHATTER, 1.3f, 0.95f + RANDOM.nextFloat() * 0.1f, SoundSource.BLOCKS);
				BossLoops.pylonQuiet(pylon);

				if (!Vfx.inRange(crystal)) {
					return;
				}

				for (int i = 0; i < Vfx.count(6); i++) {
					block(level, chips, crystal.add(jitter(0.2)), jitter(0.15).add(0, 0.1, 0));
				}

				if (vasch != null) {
					// The shards stream into him.
					Vec3 chest = vasch.position().add(0, vasch.getBbHeight() * 0.7, 0);

					for (int i = 0; i < Vfx.count(10); i++) {
						Vec3 from = crystal.add(jitter(0.25));
						int life = 8 + RANDOM.nextInt(4);
						BossParticle shard = current(from, chest.subtract(from).scale(1.0 / life), 0xFFFFFF, life);

						if (shard != null) {
							shard.size(0.05f);
						}
					}
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- hoard_open

	private static void onHoard(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 hoard = p.pos();
		play(level, hoard, ModSounds.HOARD_OPEN, 1.2f, 1f, SoundSource.BLOCKS);

		Vec3 seal = p.dir().lengthSqr() > 1.0e-6 ? hoard.add(p.dir()) : hoard;

		if (Vfx.inRange(seal)) {
			// The vault seal crumbles.
			BlockState stone = BossBlocks.RELIQUARY_SEAL.defaultBlockState();

			for (int i = 0; i < Vfx.count(16); i++) {
				block(level, stone, seal.add(jitter(0.6)), jitter(0.1).add(0, 0.05, 0));
			}

			for (int i = 0; i < Vfx.count(6); i++) {
				dust(seal.add(jitter(0.5)), jitter(0.05), 0.3f);
			}
		}

		if (Vfx.inRange(hoard)) {
			// Motes rise from the three holocrons on the plinth.
			for (int i = 0; i < Vfx.count(9); i++) {
				Vec3 at = hoard.add((i % 3 - 1) * 0.4 + (RANDOM.nextDouble() - 0.5) * 0.15, 0.6, (RANDOM.nextDouble() - 0.5) * 0.3);
				level.addParticle(WorldParticles.CRYSTAL_GLINT, at.x, at.y, at.z, 0, 0.02 + RANDOM.nextDouble() * 0.01, 0);
			}
		}
	}

	// ---------------------------------------------------------------- running effects and the ambient look

	private static Run start(int entityId, Kind kind, int length, Vec3 pos, Vec3 dir, float value) {
		RUNS.removeIf(run -> run.entityId == entityId && entityId >= 0 && run.kind == kind);
		Run run = new Run(entityId, kind, ticks, length, pos, dir, value);
		RUNS.add(run);
		return run;
	}

	private static void tick(Minecraft mc) {
		ticks++;
		ClientLevel level = mc.level;

		if (level != lastLevel) {
			lastLevel = level;
			clear();
		}

		if (level == null || mc.player == null) {
			return;
		}

		SurgeWallFx.tick(level);

		for (Iterator<Run> it = RUNS.iterator(); it.hasNext();) {
			Run run = it.next();
			long age = ticks - run.start;

			if (age >= run.length) {
				it.remove();
				continue;
			}

			Entity entity = run.entityId >= 0 ? level.getEntity(run.entityId) : null;

			// A tell ends with its action (cancelled by an interrupt or a stagger) or with him.
			if (run.kind.action != null && age > SYNC_GRACE
					&& (!(entity instanceof OrrenVasch vasch) || !vasch.isAlive() || vasch.action() != run.kind.action)) {
				it.remove();
				continue;
			}

			if (!tickRun(level, run, entity, age)) {
				it.remove();
			}
		}

		ambient(mc, level);
	}

	/** One tick of a running effect; false when it is over. */
	private static boolean tickRun(ClientLevel level, Run run, @Nullable Entity entity, long age) {
		if (run.kind == Kind.CAUGHT && (!(entity instanceof ArchiveDebris chunk) || chunk.state() != ArchiveDebris.State.CAUGHT)) {
			// Released (dropped, or flung back at him): the hanging effect ends.
			return false;
		}

		Vec3 focus = entity != null ? entity.position() : run.pos;

		if (!Vfx.inRange(focus)) {
			return true;
		}

		switch (run.kind) {
			case HURL_TELL -> tickHurlTell(level, run);
			case CAUGHT -> {
				// Hanging in bent air, grit trickling from it.
				if (age % 3 == 0) {
					block(level, COBBLE, focus.add(jitter(0.25)), new Vec3(0, -0.04, 0));
				}

				if (age % 4 == 0) {
					BossParticle air = BossParticleProviders.spawn(BossParticles.WARD_HAZE, focus.add(jitter(0.5)), jitter(0.01));

					if (air != null) {
						air.tint(0xFFFFFF).alpha(0.3f);
					}
				}
			}
			case PALM_TELL -> {
				// Dust drawn in toward the drawn-back palm.
				Vec3 palm = entity instanceof OrrenVasch vasch ? vasch.offHandPoint().subtract(0, 0.5, 0) : run.pos;

				for (int i = 0; i < Vfx.tellCount(2); i++) {
					Vec3 from = palm.add(jitter(1.0).normalize().scale(1.0 + RANDOM.nextDouble() * 0.6));
					BossParticle mote = dust(from, palm.subtract(from).scale(0.12), 0.12f);

					if (mote != null) {
						mote.life(8);
					}
				}
			}
			case LASH_TELL -> {
				// The seam of floor dust lifts, running out from him along the line.
				float progress = Mth.clamp((age + 1f) / run.length, 0f, 1f);
				double length = Math.max(1.0, run.value * Math.min(1f, 0.3f + progress));

				for (int i = 0; i < Vfx.tellCount(3); i++) {
					Vec3 at = run.pos.add(run.dir.scale(RANDOM.nextDouble() * length)).add(0, 0.05, 0);
					seam(at, new Vec3(0, 0.04 + RANDOM.nextDouble() * 0.04, 0).add(jitter(0.01)));
				}
			}
			case LASH_WAVE -> {
				// The ridge travels: dust and grit thrown up about a block high at its front.
				Vec3 front = run.pos.add(run.dir.scale(age));
				Vec3 dir = normal(run.dir);
				Vec3 side = new Vec3(-dir.z, 0, dir.x);

				for (int i = 0; i < Vfx.count(3); i++) {
					Vec3 at = front.add(side.scale((RANDOM.nextDouble() - 0.5) * 0.9)).add(0, 0.1 + RANDOM.nextDouble() * 0.6, 0);
					seam(at, dir.scale(0.05).add(0, 0.1 + RANDOM.nextDouble() * 0.06, 0));
				}

				if (age % 2 == 0) {
					BlockState ground = groundAt(level, front);
					block(level, ground.isAir() ? COBBLE : ground, front.add(0, 0.2, 0), dir.scale(0.05).add(0, 0.18, 0));
				}
			}
			case SURGE_GATHER -> tickSurgeGather(level, run, entity, age);
			case SPENT -> {
				// Dust settles off him; the holocron's light gutters (falling crimson sparks, steady with flashing off).
				if (age % 3 == 0 && entity != null) {
					fallingDust(level, entity.position().add(0, entity.getBbHeight() * 0.9, 0), 0.35, Vfx.count(1), STONE);
				}

				if (age % 4 == 0 && age < 32 && entity instanceof OrrenVasch vasch && vasch.phase() >= 3) {
					BossParticle spark = current(vasch.offHandPoint().subtract(0, 0.9, 0), new Vec3(0, -0.02, 0), CRIMSON, 12);

					if (spark != null) {
						spark.gravity(0.15f);

						if (!Vfx.flashing()) {
							spark.steady();
						} else if (age % 8 == 0) {
							spark.alpha(0.35f);
						}
					}
				}
			}
			case GRIP_TELL -> {
				// Pebbles and dust rise around the target's feet.
				Vec3 feet = entity != null ? entity.position() : run.pos;
				BlockState ground = groundAt(level, feet);

				for (int i = 0; i < Vfx.tellCount(2); i++) {
					double angle = RANDOM.nextDouble() * Mth.TWO_PI;
					Vec3 at = feet.add(Math.cos(angle) * 0.6, 0.1, Math.sin(angle) * 0.6);
					block(level, ground.isAir() ? COBBLE : ground, at, new Vec3(0, 0.12 + RANDOM.nextDouble() * 0.08, 0));
				}

				if (age % 2 == 0) {
					dust(feet.add(jitter(0.5).multiply(1, 0, 1)).add(0, 0.1, 0), new Vec3(0, 0.04, 0), 0.16f);
				}
			}
			case GRIP_HOLD -> {
				// Dust trickling off the held player.
				if (age % 2 == 0 && entity != null) {
					fallingDust(level, entity.position().add(0, entity.getBbHeight() * 0.5, 0), 0.35, Vfx.count(1), STONE);
				}
			}
			case LUNGE_TELL -> {
				if (age % 3 == 0) {
					Vec3 feet = entity != null ? entity.position() : run.pos;

					for (int i = 0; i < Vfx.tellCount(1); i++) {
						dust(feet.add(jitter(0.3).multiply(1, 0, 1)).add(0, 0.1, 0), run.dir.scale(-0.12).add(0, 0.03, 0), 0.18f);
					}
				}
			}
			case TRANSITION -> tickTransition(level, run, entity, age);
			case ECHO_FORM -> {
				// Smoke gathers from the floor into his shape.
				for (int i = 0; i < Vfx.count(2); i++) {
					double angle = RANDOM.nextDouble() * Mth.TWO_PI;
					double r = 1.2 + RANDOM.nextDouble();
					Vec3 from = run.pos.add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r);
					Vec3 to = run.pos.add(jitter(0.2).multiply(1, 0, 1)).add(0, 0.3 + RANDOM.nextDouble() * 1.5, 0);
					smoke(from, to.subtract(from).scale(0.06));
				}
			}
			case ECHO_DISSOLVE -> {
				// He breaks into slow smoke that drains into the floor.
				Vec3 base = entity != null ? entity.position() : run.pos;

				for (int i = 0; i < Vfx.count(2); i++) {
					Vec3 at = base.add(jitter(0.3).multiply(1, 0, 1)).add(0, RANDOM.nextDouble() * 1.9, 0);
					BossParticle smoke = smoke(at, new Vec3((RANDOM.nextDouble() - 0.5) * 0.02, -0.03, (RANDOM.nextDouble() - 0.5) * 0.02));

					if (smoke != null) {
						smoke.gravity(0.004f);
					}
				}
			}
			case REGROW -> {
				// The crystal grows back: glints rising at it and motes drawn into it.
				if (age % 3 == 0) {
					Vec3 at = run.pos.add(jitter(0.2));
					level.addParticle(WorldParticles.CRYSTAL_GLINT, at.x, at.y, at.z, 0, 0.01, 0);
				}

				if (age % 6 == 0) {
					Vec3 from = run.pos.add(jitter(1.0).normalize().scale(0.8));
					current(from, run.pos.subtract(from).scale(1.0 / 10.0), CRYSTAL, 10);
				}
			}
			case EXECUTION -> tickExecution(level, run, entity, age);
			default -> {
			}
		}

		return true;
	}

	/** Grit pours off the chunk as it rises from the pile above him. */
	private static void tickHurlTell(ClientLevel level, Run run) {
		ArchiveDebris chunk = null;
		double best = Double.MAX_VALUE;

		for (ArchiveDebris candidate : level.getEntitiesOfClass(ArchiveDebris.class, new AABB(run.pos, run.pos).inflate(14.0), d -> d.state() == ArchiveDebris.State.HELD)) {
			double distance = candidate.position().distanceToSqr(run.pos);

			if (distance < best) {
				best = distance;
				chunk = candidate;
			}
		}

		if (chunk == null) {
			return;
		}

		for (int i = 0; i < Vfx.tellCount(1); i++) {
			block(level, COBBLE, chunk.position().add(jitter(0.3)), new Vec3(0, -0.05, 0));
		}

		if ((ticks - run.start) % 2 == 0) {
			fallingDust(level, chunk.position(), 0.3, Vfx.tellCount(1), STONE);
		}
	}

	/**
	 * The Surge gather (a tell, never thinned): floor dust and grit stream in toward him from all sides
	 * at irregular distances. 2 puffs a tick and a grit chip every other tick: 75 over the 30 ticks.
	 */
	private static void tickSurgeGather(ClientLevel level, Run run, @Nullable Entity entity, long age) {
		Vec3 feet = entity != null ? entity.position() : run.pos;

		for (int i = 0; i < Vfx.tellCount(2); i++) {
			double angle = RANDOM.nextDouble() * Mth.TWO_PI;
			double r = 3.5 + RANDOM.nextDouble() * 2.5;
			Vec3 from = feet.add(Math.cos(angle) * r, 0.1 + RANDOM.nextDouble() * 0.5, Math.sin(angle) * r);
			Vec3 in = feet.add(0, 0.6, 0).subtract(from).normalize().scale(0.22 + RANDOM.nextDouble() * 0.08);
			BossParticle puff = dust(from, in, 0.18f);

			if (puff != null) {
				puff.life(14).drag(0.97f);
			}
		}

		if (age % 2 == 0) {
			double angle = RANDOM.nextDouble() * Mth.TWO_PI;
			Vec3 from = feet.add(Math.cos(angle) * 3.0, 0.15, Math.sin(angle) * 3.0);
			BlockState ground = groundAt(level, from);

			for (int i = 0; i < Vfx.tellCount(1); i++) {
				block(level, ground.isAir() ? COBBLE : ground, from, feet.subtract(from).normalize().scale(0.25).add(0, 0.05, 0));
			}
		}
	}

	/**
	 * The transitions: ceiling dust over the dais; in the first, crystal light drawn into the lifted
	 * crystal (twinkling glints, or a steady glow with flashing off); in the second, shards streaming
	 * into him from all sides.
	 */
	private static void tickTransition(ClientLevel level, Run run, @Nullable Entity entity, long age) {
		Vec3 base = entity != null ? entity.position() : run.pos;

		if (age < 30) {
			ceilingDust(level, base, 6.0, Vfx.count(1));
		}

		if (age >= 45) {
			return;
		}

		if (run.value < 2f) {
			Vec3 crystal = entity instanceof OrrenVasch vasch ? vasch.offHandPoint().add(0, 0.4, 0) : base.add(0, 2.4, 0);
			Vec3 from = crystal.add(jitter(1.0).normalize().scale(1.5));

			if (Vfx.flashing()) {
				level.addParticle(WorldParticles.CRYSTAL_GLINT, from.x, from.y, from.z, (crystal.x - from.x) * 0.08, (crystal.y - from.y) * 0.08, (crystal.z - from.z) * 0.08);
			} else {
				BossParticle glow = current(from, crystal.subtract(from).scale(1.0 / 12.0), CRYSTAL, 12);

				if (glow != null) {
					glow.steady();
				}
			}

			return;
		}

		Vec3 chest = base.add(0, 1.35, 0);

		for (int i = 0; i < Vfx.count(2); i++) {
			Vec3 from = chest.add(jitter(1.0).normalize().scale(3.0 + RANDOM.nextDouble() * 3.0));
			int life = 10 + RANDOM.nextInt(5);
			BossParticle shard = current(from, chest.subtract(from).scale(1.0 / life), 0xFFFFFF, life);

			if (shard != null) {
				shard.size(0.045f);
			}
		}
	}

	/** The execution: the stroke lands; his amber blade rolls away and gutters out; dust settles. */
	private static void tickExecution(ClientLevel level, Run run, @Nullable Entity entity, long age) {
		Vec3 base = entity != null ? entity.position() : run.pos;

		if (age == STROKE_TICKS) {
			play(level, base, ModSounds.BOSS_COLLECTOR_EXECUTE, 1.2f);

			for (int i = 0; i < Vfx.count(4); i++) {
				level.addParticle(ParticleTypes.POOF, base.x + (RANDOM.nextDouble() - 0.5), base.y + 0.1, base.z + (RANDOM.nextDouble() - 0.5), 0, 0.01, 0);
			}

			fallingDust(level, base.add(0, 2.0, 0), 0.8, Vfx.count(10), STONE);
		}

		if (age > STROKE_TICKS && age <= STROKE_TICKS + 12) {
			// The blade rolls a block and a half to his side and gutters out.
			Vec3 facing = entity != null ? normal(entity.getLookAngle().multiply(1, 0, 1)) : new Vec3(0, 0, 1);
			Vec3 side = new Vec3(-facing.z, 0, facing.x);
			float t = (age - STROKE_TICKS) / 12f;
			Vec3 at = base.add(side.scale(0.4 + 1.1 * t)).add(0, 0.08, 0);
			BossParticle spark = current(at, new Vec3(0, 0.01, 0), AMBER, 10);

			if (spark != null) {
				spark.alpha(0.9f * (1f - t));

				if (!Vfx.flashing()) {
					spark.steady();
				} else if (age % 3 == 0) {
					spark.alpha(0.25f);
				}
			}
		}
	}

	/** The fight's look while he stands: ward haze, pylon currents, the holocron, the fraying cloak, the echo's edges. */
	private static void ambient(Minecraft mc, ClientLevel level) {
		List<Integer> seen = new ArrayList<>();

		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof OrrenVasch vasch) || !vasch.isAlive()) {
				continue;
			}

			int id = vasch.getId();
			seen.add(id);

			if (vasch.isFallen()) {
				FALLEN.putIfAbsent(id, ticks);
			} else {
				FALLEN.remove(id);
			}

			if (!Vfx.inRange(vasch.position()) || vasch.isFallen()) {
				continue;
			}

			long phaseTick = ticks + id;

			if (vasch.phase() == 2 && !vasch.isKneeling()) {
				int ward = BossLoops.wardLevel(id);

				if (ward > 0 && phaseTick % 2 == 0) {
					wardHaze(vasch, Vfx.count(ward));
				}

				for (BlockPos pylon : BossLoops.singingPylons(level, vasch.position(), PYLON_RANGE)) {
					if ((phaseTick + pylon.hashCode()) % 3 == 0) {
						pylonCurrent(vasch, crystalOf(pylon));
					}
				}
			}

			if (vasch.phase() >= 3 && !vasch.isEcho()) {
				unkept(vasch, phaseTick);
			}

			if (vasch.isEcho() && phaseTick % 3 == 0) {
				// Slow smoke breaking off his outline.
				double angle = RANDOM.nextDouble() * Mth.TWO_PI;
				Vec3 edge = vasch.position().add(Math.cos(angle) * 0.4, 0.2 + RANDOM.nextDouble() * 1.7, Math.sin(angle) * 0.4);

				for (int i = 0; i < Vfx.count(1); i++) {
					smoke(edge, new Vec3(Math.cos(angle) * 0.01, 0.008, Math.sin(angle) * 0.01));
				}
			}
		}

		FALLEN.keySet().retainAll(seen);
		STROKES.keySet().removeIf(id -> !seen.contains(id) && ticks - STROKES.get(id) > 100);
		FORMS.keySet().removeIf(id -> !seen.contains(id) && ticks - FORMS.get(id).start > 200);
		DISSOLVES.keySet().removeIf(id -> !seen.contains(id) && ticks - DISSOLVES.get(id).start > 200);
	}

	/** Sparse heat-haze specks off his shoulders and along his blade; never a bubble or an outline. */
	private static void wardHaze(OrrenVasch vasch, int count) {
		Vec3 look = normal(vasch.getLookAngle().multiply(1, 0, 1));
		Vec3 right = new Vec3(-look.z, 0, look.x);

		for (int i = 0; i < count; i++) {
			Vec3 at;

			if (i % 2 == 0) {
				at = vasch.position().add(right.scale(RANDOM.nextBoolean() ? 0.38 : -0.38)).add(0, 1.45 + RANDOM.nextDouble() * 0.1, 0);
			} else {
				// Along the blade, held out in his right hand.
				double along = 0.3 + RANDOM.nextDouble() * 0.9;
				at = vasch.position().add(right.scale(-0.4)).add(look.scale(0.3 + along * 0.4)).add(0, 0.85 + along * 0.6, 0);
			}

			BossParticle haze = BossParticleProviders.spawn(BossParticles.WARD_HAZE, at.add(jitter(0.08)), new Vec3(0, 0.012 + RANDOM.nextDouble() * 0.01, 0));

			if (haze != null) {
				haze.tint(HAZE);

				if (!Vfx.flashing()) {
					haze.steady();
				}
			}
		}
	}

	/** One thin, wavering current mote from a singing pylon to him. */
	private static void pylonCurrent(OrrenVasch vasch, Vec3 crystal) {
		Vec3 chest = vasch.position().add(0, vasch.getBbHeight() * 0.7, 0);
		Vec3 path = chest.subtract(crystal);
		int life = Mth.clamp((int) Math.round(path.length() / 0.45), 8, 40);

		for (int i = 0; i < Vfx.count(1); i++) {
			BossParticle mote = current(crystal.add(jitter(0.12)), path.scale(1.0 / life), CRYSTAL, life);

			if (mote != null && !Vfx.flashing()) {
				mote.steady();
			}
		}
	}

	/** Phase 3: the holocron's crimson motes at his off-hand and the cloak's edges fraying into dust (steady). */
	private static void unkept(OrrenVasch vasch, long phaseTick) {
		if (phaseTick % 6 == 0) {
			Vec3 hand = vasch.offHandPoint().subtract(0, vasch.action() == OrrenVasch.Action.SURGE ? 0 : 0.95, 0);
			BossParticle mote = current(hand.add(jitter(0.08)), new Vec3(0, 0.01, 0), CRIMSON, 18);

			if (mote != null) {
				mote.steady().alpha(0.7f).size(0.03f);
			}
		}

		if (phaseTick % 4 == 0) {
			Vec3 look = normal(vasch.getLookAngle().multiply(1, 0, 1));
			Vec3 right = new Vec3(-look.z, 0, look.x);
			Vec3 edge = vasch.position().add(look.scale(-0.3)).add(right.scale((RANDOM.nextDouble() - 0.5) * 0.6)).add(0, 0.3 + RANDOM.nextDouble() * 1.0, 0);

			for (int i = 0; i < Vfx.count(1); i++) {
				BossParticle fray = dust(edge, look.scale(-0.01).add(0, 0.006, 0), 0.07f);

				if (fray != null) {
					fray.tint(FRAY).alpha(0.5f).life(24);
				}
			}
		}
	}

	// ---------------------------------------------------------------- particle helpers

	private static @Nullable BossParticle dust(Vec3 pos, Vec3 velocity, float size) {
		BossParticle puff = BossParticleProviders.spawn(BossParticles.SURGE_DUST, pos, velocity);

		if (puff != null) {
			puff.size(size);
		}

		return puff;
	}

	private static @Nullable BossParticle smoke(Vec3 pos, Vec3 velocity) {
		BossParticle smoke = BossParticleProviders.spawn(BossParticles.ECHO_SMOKE, pos, velocity);

		if (smoke != null) {
			smoke.tint(SMOKE);
		}

		return smoke;
	}

	private static void seam(Vec3 pos, Vec3 velocity) {
		BossParticleProviders.spawn(BossParticles.LASH_SEAM, pos, velocity);
	}

	/** A full-bright mote travelling {@code velocity} per tick for {@code life} ticks. */
	private static @Nullable BossParticle current(Vec3 pos, Vec3 velocity, int rgb, int life) {
		BossParticle mote = BossParticleProviders.spawn(BossParticles.WARD_CURRENT, pos, velocity);

		if (mote != null) {
			mote.tint(rgb).life(life);
		}

		return mote;
	}

	private static void block(ClientLevel level, BlockState state, Vec3 pos, Vec3 velocity) {
		level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
	}

	/** Fine dust drifting down from around {@code pos}. */
	private static void fallingDust(ClientLevel level, Vec3 pos, double spread, int count, BlockState state) {
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, state);

		for (int i = 0; i < count; i++) {
			Vec3 at = pos.add(jitter(spread));
			level.addParticle(dust, at.x, at.y, at.z, 0, 0, 0);
		}
	}

	/** Dust shaken from the ceiling over {@code center}: each speck from the underside of the first block above it. */
	private static void ceilingDust(ClientLevel level, Vec3 center, double radius, int count) {
		for (int i = 0; i < count; i++) {
			double angle = RANDOM.nextDouble() * Mth.TWO_PI;
			double r = Math.sqrt(RANDOM.nextDouble()) * radius;
			double x = center.x + Math.cos(angle) * r;
			double z = center.z + Math.sin(angle) * r;
			BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

			for (int dy = 2; dy <= 16; dy++) {
				probe.set(x, center.y + dy, z);
				BlockState state = level.getBlockState(probe);

				if (!state.isAir()) {
					level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), x, probe.getY() - 0.05, z, 0, 0, 0);
					break;
				}
			}
		}
	}

	/** How far a straight line along the floor runs from {@code from} before a block, up to {@code max}. */
	private static double lineReach(ClientLevel level, Vec3 from, Vec3 dir, double max, @Nullable Entity rayEntity) {
		if (rayEntity == null) {
			return max;
		}

		Vec3 start = from.add(0, 0.3, 0);
		BlockHitResult hit = level.clip(new ClipContext(start, start.add(dir.scale(max)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rayEntity));
		return hit.getType() == HitResult.Type.MISS ? max : start.distanceTo(hit.getLocation());
	}

	private static Vec3 crystalOf(BlockPos pylon) {
		return Vec3.atBottomCenterOf(pylon).add(0, PylonFocus.CRYSTAL_HEIGHT + 0.4, 0);
	}

	private static BlockState groundAt(ClientLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at.x, at.y - 0.2, at.z);
		BlockState state = level.getBlockState(pos);
		return state.isAir() ? level.getBlockState(pos.below()) : state;
	}

	private static void shakeNear(Minecraft mc, Vec3 pos, double range, float amount) {
		if (mc.player != null && mc.player.position().distanceTo(pos) < range) {
			CameraShake.add(amount);
		}
	}

	private static Vec3 normal(Vec3 dir) {
		return dir.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : dir.normalize();
	}

	private static Vec3 jitter(double scale) {
		return new Vec3((RANDOM.nextDouble() - 0.5) * 2 * scale, (RANDOM.nextDouble() - 0.5) * 2 * scale, (RANDOM.nextDouble() - 0.5) * 2 * scale);
	}

	// ---------------------------------------------------------------- sounds

	/** A positional one-shot with a little pitch spread (AUDIO_BRIEF variation). */
	private static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume) {
		play(level, pos, sound, volume, 0.95f + RANDOM.nextFloat() * 0.1f, SoundSource.HOSTILE);
	}

	private static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
		play(level, pos, sound, volume, pitch, SoundSource.HOSTILE);
	}

	private static void play(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch, SoundSource source) {
		level.playLocalSound(pos.x, pos.y, pos.z, sound, source, volume * ClientConfig.get().modSoundVolume, pitch, false);
	}

	/** Forgets every running effect (level change, disconnect). */
	private static void clear() {
		RUNS.clear();
		FORMS.clear();
		DISSOLVES.clear();
		FALLEN.clear();
		STROKES.clear();
		SurgeWallFx.clear();
	}

	private BossFx() {
	}
}
