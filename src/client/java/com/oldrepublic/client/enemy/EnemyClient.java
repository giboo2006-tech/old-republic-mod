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
		EnemyParticleProviders.init();
		EnemyEffectsClient.init();
	}

	private EnemyClient() {
	}
}
