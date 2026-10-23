package com.oldrepublic.client.saber.workbench;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.saber.SaberSparks;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.forge.ForgeEffects;
import com.oldrepublic.saber.forge.ForgeService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The Forge tab's client side (docs/MILESTONE_7.md 5.2, 5.3): the handlers of the Forge's effects. The
 * known designs reach the screen through the {@code oldrepublic:forge} attachment's own sync, and the
 * tab itself is {@link ForgeTab}. Called once from {@code OldRepublicModClient}.
 * <ul>
 * <li>{@code workbench_forge}: {@code workbench.forge}, a flash of the material's colour over the bench
 * and a shower of sparks, white-hot cooling to forge orange and to the material's colour, with a puff of
 * smoke; on the forging player's screen the forge well and the part's socket glint.</li>
 * <li>{@code workbench_study}: {@code workbench.study}, a soft holo-blue glow and motes rising off the
 * bench; on the studying player's screen the well and the socket glint holo blue.</li>
 * </ul>
 */
public final class ForgeClient {
	/** {@code workbench_forge}'s budget (15.1): hot sparks, sparks of the material's colour, smoke. */
	private static final int HOT_SPARKS = 12;
	private static final int TINT_SPARKS = 8;
	private static final int SMOKE = 4;
	/** {@code workbench_study}'s motes. */
	private static final int STUDY_MOTES = 8;
	/** What the hot sparks cool to, and the study glow (RGB). */
	private static final int FORGE_ORANGE = 0xFF9A3C;
	private static final int HOLO = 0x8FD8FF;
	private static final RandomSource RANDOM = RandomSource.create();

	public static void init() {
		ClientEffects.register(ForgeEffects.FORGE, ForgeClient::onForge);
		ClientEffects.register(ForgeEffects.STUDY, ForgeClient::onStudy);
	}

	private static void onForge(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		HiltMaterial material = ForgeService.material(p.variant());
		int tint = (material != null ? material : HiltMaterial.STEEL).tint();
		level.playLocalSound(p.pos().x, p.pos().y, p.pos().z, ModSounds.WORKBENCH_FORGE, SoundSource.BLOCKS,
				0.9f * ClientConfig.get().modSoundVolume, 0.95f + RANDOM.nextFloat() * 0.1f, false);

		if (entity != null && entity == mc.player) {
			ForgeTab.onForge(level.getGameTime(), Math.round(p.intensity()), p.variant());
		}

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		Vec3 up = new Vec3(0, 1, 0);
		SaberSparks.flash(p.pos(), tint);
		SaberSparks.sparks(p.pos(), up, 0.9, Vfx.count(HOT_SPARKS), FORGE_ORANGE, 0.25);
		SaberSparks.sparks(p.pos(), up, 0.7, Vfx.count(TINT_SPARKS), tint, 0.2);

		for (int i = 0, n = Vfx.count(SMOKE); i < n; i++) {
			double vx = (RANDOM.nextDouble() - 0.5) * 0.02;
			double vz = (RANDOM.nextDouble() - 0.5) * 0.02;
			level.addParticle(ParticleTypes.SMOKE, p.pos().x, p.pos().y, p.pos().z, vx, 0.03 + RANDOM.nextDouble() * 0.02, vz);
		}
	}

	private static void onStudy(Minecraft mc, ClientLevel level, EffectEventPayload p, @Nullable Entity entity) {
		level.playLocalSound(p.pos().x, p.pos().y, p.pos().z, ModSounds.WORKBENCH_STUDY, SoundSource.BLOCKS,
				0.9f * ClientConfig.get().modSoundVolume, 1f, false);

		if (entity != null && entity == mc.player) {
			ForgeTab.onStudy(level.getGameTime(), p.variant());
		}

		if (!Vfx.inRange(p.pos())) {
			return;
		}

		SaberSparks.glow(p.pos(), HOLO);

		for (int i = 0, n = Vfx.count(STUDY_MOTES); i < n; i++) {
			double ox = (RANDOM.nextDouble() - 0.5) * 0.5;
			double oz = (RANDOM.nextDouble() - 0.5) * 0.5;
			level.addParticle(ParticleTypes.END_ROD, p.pos().x + ox, p.pos().y, p.pos().z + oz, 0, 0.02 + RANDOM.nextDouble() * 0.02, 0);
		}
	}

	private ForgeClient() {
	}
}
