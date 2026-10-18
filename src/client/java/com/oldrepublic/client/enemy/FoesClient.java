package com.oldrepublic.client.enemy;

import com.oldrepublic.boss.kaast.KaastEntities;
import com.oldrepublic.client.boss.kaast.CampMineRenderer;
import com.oldrepublic.client.boss.kaast.KaastFx;
import com.oldrepublic.client.boss.kaast.KaastMusic;
import com.oldrepublic.client.boss.kaast.KaastRenderer;
import com.oldrepublic.client.boss.kaast.RattleRenderer;
import com.oldrepublic.client.enemy.creature.AshHoundModel;
import com.oldrepublic.client.enemy.creature.AshHoundRenderer;
import com.oldrepublic.client.enemy.creature.BeastFx;
import com.oldrepublic.client.enemy.creature.HollowhornModel;
import com.oldrepublic.client.enemy.creature.HollowhornRenderer;
import com.oldrepublic.client.enemy.droid.DroidFx;
import com.oldrepublic.client.enemy.droid.DroidModel;
import com.oldrepublic.client.enemy.droid.DroidRenderer;
import com.oldrepublic.client.enemy.droid.SeekerModel;
import com.oldrepublic.client.enemy.droid.SeekerRenderer;
import com.oldrepublic.client.enemy.mandalorian.HunterFx;
import com.oldrepublic.client.enemy.mandalorian.HunterModel;
import com.oldrepublic.client.enemy.mandalorian.HunterNetRenderer;
import com.oldrepublic.client.enemy.mandalorian.HunterRenderer;
import com.oldrepublic.client.enemy.mandalorian.HunterRocketRenderer;
import com.oldrepublic.enemy.creature.CreatureEntities;
import com.oldrepublic.enemy.droid.DroidEntities;
import com.oldrepublic.enemy.mandalorian.HunterEntities;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Milestone 4 client initialisation for the foes (spec MILESTONE_4.md 10, 13.3): the model layers
 * and renderers of the ten W6 entities, their effect events, and the Kaast fight's music. Owned by
 * W6 Force and foes; the lead calls it from {@code OldRepublicModClient}, so the worker never edits
 * the client entry point.
 */
public final class FoesClient {
	public static void init() {
		ModelLayerRegistry.registerModelLayer(HunterModel.LAYER, HunterModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(HunterRocketRenderer.LAYER, HunterRocketRenderer.RocketModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(HunterNetRenderer.LAYER, HunterNetRenderer.NetModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(DroidModel.LAYER, DroidModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(SeekerModel.LAYER, SeekerModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(HollowhornModel.LAYER, HollowhornModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(AshHoundModel.LAYER, AshHoundModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(CampMineRenderer.LAYER, CampMineRenderer.MineModel::createBodyLayer);

		EntityRendererRegistry.register(HunterEntities.MANDALORIAN_HUNTER, HunterRenderer::new);
		EntityRendererRegistry.register(HunterEntities.HUNTER_ROCKET, HunterRocketRenderer::new);
		EntityRendererRegistry.register(HunterEntities.HUNTER_NET, HunterNetRenderer::new);
		EntityRendererRegistry.register(DroidEntities.SECURITY_DROID, DroidRenderer::new);
		EntityRendererRegistry.register(DroidEntities.SEEKER_DROID, SeekerRenderer::seeker);
		EntityRendererRegistry.register(CreatureEntities.HOLLOWHORN, HollowhornRenderer::new);
		EntityRendererRegistry.register(CreatureEntities.ASH_HOUND, AshHoundRenderer::new);
		EntityRendererRegistry.register(KaastEntities.TORVIN_KAAST, KaastRenderer::new);
		EntityRendererRegistry.register(KaastEntities.KAAST_DROID, RattleRenderer::new);
		EntityRendererRegistry.register(KaastEntities.CAMP_MINE, CampMineRenderer::new);

		HunterFx.init();
		DroidFx.init();
		BeastFx.init();
		KaastFx.init();
		KaastMusic.init();
	}

	private FoesClient() {
	}
}
