package com.oldrepublic.client.enemy;

import com.oldrepublic.enemy.EnemyEntities;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Client initialisation for the enemy feature. Called from OldRepublicModClient. */
public final class EnemyClient {
	public static void init() {
		ModelLayerRegistry.registerModelLayer(PoacherModel.LAYER, PoacherModel::createBodyLayer);
		EntityRendererRegistry.register(EnemyEntities.CRYSTAL_POACHER, CrystalPoacherRenderer::new);
		EntityRendererRegistry.register(EnemyEntities.BLASTER_BOLT, BlasterBoltRenderer::new);
		ModelLayerRegistry.registerModelLayer(AcolyteModel.LAYER, AcolyteModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(CaptainModel.LAYER, CaptainModel::createBodyLayer);
		EntityRendererRegistry.register(EnemyEntities.SITH_ACOLYTE, SithAcolyteRenderer::new);
		EntityRendererRegistry.register(EnemyEntities.POACHER_CAPTAIN, PoacherCaptainRenderer::new);
		EnemyParticleProviders.init();
		EnemyEffectsClient.init();
		EnemyTellFx.init();
	}

	private EnemyClient() {
	}
}
