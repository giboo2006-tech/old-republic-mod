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
		// Milestone 3 (W4): troopers, the frag grenade, their effects and radio barks.
		ModelLayerRegistry.registerModelLayer(TrooperModel.REPUBLIC, TrooperModel::createRepublicLayer);
		ModelLayerRegistry.registerModelLayer(TrooperModel.IMPERIAL, TrooperModel::createImperialLayer);
		ModelLayerRegistry.registerModelLayer(FragGrenadeModel.LAYER, FragGrenadeModel::createBodyLayer);
		EntityRendererRegistry.register(EnemyEntities.REPUBLIC_TROOPER, TrooperRenderer::republic);
		EntityRendererRegistry.register(EnemyEntities.SITH_TROOPER, TrooperRenderer::imperial);
		EntityRendererRegistry.register(EnemyEntities.FRAG_GRENADE, FragGrenadeRenderer::new);
		EnemyParticleProviders.init();
		EnemyEffectsClient.init();
		EnemyTellFx.init();
		TrooperEffectsClient.init();
		BarkHud.init();
	}

	private EnemyClient() {
	}
}
