package com.oldrepublic.client.enemy.hymn;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.hymn.HymnEffects;
import com.oldrepublic.enemy.hymn.Hymnsworn;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the {@code hymnsworn} event (spec MILESTONE_5.md 15.1, 15.3). The server plays the
 * one-shot sounds; this draws the looks and keeps the chant's loop:
 *
 * <ul>
 * <li>Chant pulse: the chanter's breath fogs before the veil and grit trembles on the ground around
 * it, in a few uneven spots (no ring). The {@code hymnsworn.chant} loop plays while its synced action
 * is the chant.</li>
 * <li>Storm-call tell (a tell): {@link StormTellFx} on the marked patch, and static crackling between
 * the caller's raised hands.</li>
 * <li>Storm-call strike: vanilla's bolt is the server's (visual only); this adds the scorch: smoke
 * rising, the ground's dust thrown up, sparks, and ash settling.</li>
 * <li>Force lightning: one arc from the caller's left hand to the struck chest per damage pulse, and
 * sparks where it lands.</li>
 * </ul>
 */
public final class HymnFx {
	/** The storm-caller's Force lightning: pale violet-blue. */
	static final int LIGHTNING_RGB = 0x9C8CFF;
	private static final double CHANT_LOOP_RANGE = 24.0;

	public static void init() {
		ClientEffects.register(HymnEffects.EVENT, HymnFx::onEvent);
		FoeWatch.watch(Hymnsworn.class, 5, HymnFx::watchChant);
	}

	private static void onEvent(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		Vec3 at = p.pos();

		if (!Vfx.inRange(at)) {
			return;
		}

		switch (p.variant()) {
			case HymnEffects.CHANT -> chant(level, at, entity, Math.max(2f, p.intensity()));
			case HymnEffects.CALL_TELL -> {
				StormTellFx.tell(at, 2.5, Math.max(1, Math.round(p.intensity())));

				if (entity instanceof LivingEntity caller) {
					handsCrackle(level, caller);
				}
			}
			case HymnEffects.CALL_STRIKE -> scorch(mc, level, at, Math.max(1f, p.intensity()));
			case HymnEffects.LIGHTNING -> {
				Vec3 from = entity instanceof LivingEntity caster ? leftHand(caster) : at.subtract(p.dir().scale(4));
				FoeArcs.arc(from, at, LIGHTNING_RGB, 4, 0.14f, 2);

				for (int i = 0, n = Vfx.count(4); i < n; i++) {
					FoeFx.add(level, FoeFx.lightningSpark(), at.add(FoeFx.jitterVec(0.2)), FoeFx.jitterVec(0.12));
				}
			}
			default -> {
			}
		}
	}

	/** The breath fogging before the veil, and grit trembling in a few spots within {@code range}. */
	private static void chant(ClientLevel level, Vec3 eyes, @Nullable Entity entity, float range) {
		Vec3 forward = entity != null ? FoeFx.facing(entity) : new Vec3(0, 0, 1);
		Vec3 mouth = eyes.add(forward.scale(0.3)).add(0, -0.15, 0);

		for (int i = 0, n = Vfx.count(3); i < n; i++) {
			Vec3 v = forward.scale(0.02 + FoeFx.RANDOM.nextDouble() * 0.02).add(FoeFx.jitter(0.006), 0.006, FoeFx.jitter(0.006));
			FoeFx.add(level, ParticleTypes.WHITE_SMOKE, mouth.add(FoeFx.jitterVec(0.05)), v);
		}

		Vec3 feet = entity != null ? entity.position() : eyes.subtract(0, 1.6, 0);
		double reach = Math.min(range, 6.0);
		int spots = 2 + FoeFx.RANDOM.nextInt(2);

		for (int s = 0; s < spots; s++) {
			double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double r = 1.0 + FoeFx.RANDOM.nextDouble() * (reach - 1.0);
			Vec3 spot = feet.add(Math.cos(angle) * r, 0.05, Math.sin(angle) * r);
			ParticleOptions grit = FoeFx.ground(level, spot);

			for (int i = 0, n = Vfx.count(3); i < n; i++) {
				FoeFx.add(level, grit, spot.add(FoeFx.jitter(0.3), 0, FoeFx.jitter(0.3)), new Vec3(0, 0.04 + FoeFx.RANDOM.nextDouble() * 0.04, 0));
			}
		}
	}

	/** Static crackling between the storm-caller's raised hands (part of the tell, never thinned). */
	private static void handsCrackle(ClientLevel level, LivingEntity caller) {
		Vec3 top = caller.position().add(0, caller.getBbHeight() + 0.45, 0);

		for (int i = 0, n = Vfx.tellCount(4); i < n; i++) {
			FoeFx.add(level, FoeFx.lightningSpark(), top.add(FoeFx.jitter(0.35), FoeFx.jitter(0.1), FoeFx.jitter(0.35)), FoeFx.jitterVec(0.04));
		}
	}

	/** Under vanilla's bolt: smoke rising, the ground's dust thrown up, sparks, ash settling. */
	private static void scorch(Minecraft mc, ClientLevel level, Vec3 at, float radius) {
		ParticleOptions ground = FoeFx.ground(level, at);

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			double angle = FoeFx.RANDOM.nextDouble() * Math.PI * 2;
			double speed = 0.08 + FoeFx.RANDOM.nextDouble() * 0.12 * radius;
			FoeFx.add(level, ground, at.x, at.y + 0.1, at.z, Math.cos(angle) * speed, 0.2 + FoeFx.RANDOM.nextDouble() * 0.15, Math.sin(angle) * speed);
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			FoeFx.add(level, ParticleTypes.SMOKE, at.add(FoeFx.jitter(radius * 0.5), 0.1, FoeFx.jitter(radius * 0.5)), new Vec3(0, 0.05, 0));
		}

		for (int i = 0, n = Vfx.count(8); i < n; i++) {
			FoeFx.add(level, FoeFx.lightningSpark(), at.add(FoeFx.jitter(radius * 0.4), 0.15, FoeFx.jitter(radius * 0.4)), FoeFx.jitterVec(0.15));
		}

		for (int i = 0, n = Vfx.count(6); i < n; i++) {
			FoeFx.add(level, ParticleTypes.ASH, at.add(FoeFx.jitter(radius * 0.6), 0.8 + FoeFx.RANDOM.nextDouble(), FoeFx.jitter(radius * 0.6)), Vec3.ZERO);
		}

		FoeFx.shake(mc, at, radius + 4, 0.2f);
	}

	/** The left hand of a humanoid caster (it casts with its off hand), from its body's facing. */
	static Vec3 leftHand(LivingEntity caster) {
		float yaw = caster.yBodyRot * ((float) Math.PI / 180f);
		Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 left = new Vec3(forward.z, 0, -forward.x);
		return caster.position().add(0, caster.getBbHeight() * 0.72, 0).add(forward.scale(0.55)).add(left.scale(0.35));
	}

	/** The chant loop of every chanting Hymnsworn near the player (it stops when the chant ends). */
	private static void watchChant(Minecraft mc, ClientLevel level, Hymnsworn hymnsworn) {
		if (hymnsworn.isChanting()) {
			FoeLoops.start("hymnsworn.chant:" + hymnsworn.getId(), ModSounds.HYMNSWORN_CHANT, SoundSource.HOSTILE, hymnsworn, hymnsworn.position(), 0.6f,
					0.95f + (hymnsworn.getId() % 5) * 0.025f, CHANT_LOOP_RANGE, hymnsworn::isChanting);
		}
	}

	private HymnFx() {
	}
}
