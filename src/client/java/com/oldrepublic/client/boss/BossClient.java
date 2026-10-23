package com.oldrepublic.client.boss;

import com.oldrepublic.boss.BossEntities;
import com.oldrepublic.client.boss.sentinel.SentinelClient;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/**
 * Client initialisation of the boss feature (W5 plan 4.1): B's Ruin Sentinel client first (the fight
 * music is {@code BossMusicService} since Milestone 5), then Orren Vasch's model layer and renderer, the {@code pylon_focus} renderer (none: the
 * crystal is the pylon block), the {@code archive_debris} chunk (a scaled thrown-item renderer of its
 * stone item), the boss particle providers, the effect handlers and the two hum loops. Called from
 * {@code OldRepublicModClient}.
 */
public final class BossClient {
	/** The debris chunk's item drawn this much larger than a thrown item: about three quarters of a block. */
	private static final float DEBRIS_SCALE = 3.0f;

	public static void init() {
		SentinelClient.init();
		ModelLayerRegistry.registerModelLayer(OrrenVaschModel.LAYER, OrrenVaschModel::createBodyLayer);
		EntityRendererRegistry.register(BossEntities.ORREN_VASCH, OrrenVaschRenderer::new);
		EntityRendererRegistry.register(BossEntities.PYLON_FOCUS, NoopRenderer::new);
		EntityRendererRegistry.register(BossEntities.ARCHIVE_DEBRIS, context -> new ThrownItemRenderer<>(context, DEBRIS_SCALE, false));
		BossParticleProviders.init();
		BossFx.init();
		BossLoops.init();
	}

	private BossClient() {
	}
}
