package com.oldrepublic.client.enemy;

import java.util.List;

import com.oldrepublic.boss.BossEntities;
import com.oldrepublic.boss.cantor.CantorEntities;
import com.oldrepublic.boss.kaast.KaastEntities;
import com.oldrepublic.boss.raid.BossMusicPayload;
import com.oldrepublic.boss.ulvaine.UlvaineEntities;
import com.oldrepublic.client.boss.cantor.CantorChoirModel;
import com.oldrepublic.client.boss.cantor.CantorChoirRenderer;
import com.oldrepublic.client.boss.cantor.CantorFx;
import com.oldrepublic.client.boss.cantor.CantorModel;
import com.oldrepublic.client.boss.cantor.CantorRenderer;
import com.oldrepublic.client.boss.cantor.SpectralWispParticle;
import com.oldrepublic.client.boss.music.BossMusicService;
import com.oldrepublic.client.boss.ulvaine.HologramFx;
import com.oldrepublic.client.boss.ulvaine.ResonatorModel;
import com.oldrepublic.client.boss.ulvaine.ResonatorRenderer;
import com.oldrepublic.client.boss.ulvaine.UlvaineFx;
import com.oldrepublic.client.boss.ulvaine.UlvaineModel;
import com.oldrepublic.client.boss.ulvaine.UlvaineRenderer;
import com.oldrepublic.client.enemy.cartel.CartelModel;
import com.oldrepublic.client.enemy.cartel.CartelRenderer;
import com.oldrepublic.client.enemy.hask.HaskFx;
import com.oldrepublic.client.enemy.hask.HaskModel;
import com.oldrepublic.client.enemy.hask.HaskRenderer;
import com.oldrepublic.client.enemy.hymn.FoeArcs;
import com.oldrepublic.client.enemy.hymn.FoeLoops;
import com.oldrepublic.client.enemy.hymn.FoeTimers;
import com.oldrepublic.client.enemy.hymn.FoeWatch;
import com.oldrepublic.client.enemy.hymn.HymnFx;
import com.oldrepublic.client.enemy.hymn.HymnswornModel;
import com.oldrepublic.client.enemy.hymn.HymnswornRenderer;
import com.oldrepublic.client.enemy.hymn.StormTellFx;
import com.oldrepublic.client.enemy.scour.SandBurstParticle;
import com.oldrepublic.client.enemy.scour.ScourFx;
import com.oldrepublic.client.enemy.scour.ScourModel;
import com.oldrepublic.client.enemy.scour.ScourRenderer;
import com.oldrepublic.client.enemy.stormback.StormbackFx;
import com.oldrepublic.client.enemy.stormback.StormbackModel;
import com.oldrepublic.client.enemy.stormback.StormbackRenderer;
import com.oldrepublic.enemy.cartel.CartelEntities;
import com.oldrepublic.enemy.hask.HaskEntities;
import com.oldrepublic.enemy.hymn.HymnEntities;
import com.oldrepublic.enemy.scour.ScourEntities;
import com.oldrepublic.enemy.stormback.StormbackEntities;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Milestone 5 client initialisation for the five foes' renderers, Ulvaine, the First Cantor, the
 * hologram and the boss music service (section 12). Owned by W8 foes and bosses; the lead calls it
 * from {@code OldRepublicModClient}, so the worker never edits the client entry point.
 *
 * <p>The music service takes every boss's loops here (12.5): the Collector's three phases, Kaast's
 * three, and one track each for Ulvaine and the First Cantor.
 */
public final class FoesM5Client {
	public static void init() {
		ModelLayerRegistry.registerModelLayer(CartelModel.LAYER, CartelModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(HaskModel.LAYER, HaskModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(HymnswornModel.LAYER, HymnswornModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(ScourModel.LAYER, ScourModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(StormbackModel.LAYER, StormbackModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(UlvaineModel.LAYER, UlvaineModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(ResonatorModel.LAYER, ResonatorModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(CantorModel.LAYER, CantorModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(CantorChoirModel.LAYER, CantorChoirModel::createBodyLayer);

		EntityRendererRegistry.register(CartelEntities.CARTEL_ENFORCER, CartelRenderer::new);
		EntityRendererRegistry.register(HaskEntities.HASK_WARRIOR, HaskRenderer::new);
		EntityRendererRegistry.register(HymnEntities.HYMNSWORN, HymnswornRenderer::new);
		EntityRendererRegistry.register(ScourEntities.SAND_SCOUR, ScourRenderer::new);
		EntityRendererRegistry.register(StormbackEntities.STORMBACK, StormbackRenderer::new);
		EntityRendererRegistry.register(UlvaineEntities.DARTH_ULVAINE, UlvaineRenderer::new);
		EntityRendererRegistry.register(UlvaineEntities.HYMN_RESONATOR, ResonatorRenderer::new);
		EntityRendererRegistry.register(CantorEntities.FIRST_CANTOR, CantorRenderer::new);
		EntityRendererRegistry.register(CantorEntities.CANTOR_CHOIR, CantorChoirRenderer::new);

		SandBurstParticle.register();
		SpectralWispParticle.register();

		// The shared machinery first, then each foe's and boss's effects.
		FoeLoops.init();
		FoeWatch.init();
		FoeTimers.init();
		StormTellFx.init();
		FoeArcs.init();
		HymnFx.init();
		HaskFx.init();
		ScourFx.init();
		StormbackFx.init();
		UlvaineFx.init();
		HologramFx.init();
		CantorFx.init();

		BossMusicService.register(BossMusicPayload.COLLECTOR, BossEntities.ORREN_VASCH,
				List.of(ModSounds.MUSIC_COLLECTOR_ARCHIVIST, ModSounds.MUSIC_COLLECTOR_SINGING_WALL, ModSounds.MUSIC_COLLECTOR_UNKEPT));
		BossMusicService.register(BossMusicPayload.KAAST, KaastEntities.TORVIN_KAAST,
				List.of(ModSounds.MUSIC_KAAST_HUNT, ModSounds.MUSIC_KAAST_TRAP, ModSounds.MUSIC_KAAST_LAST_STAND));
		BossMusicService.register(BossMusicPayload.ULVAINE, UlvaineEntities.DARTH_ULVAINE, List.of(ModSounds.MUSIC_BOSS_ULVAINE));
		BossMusicService.register(BossMusicPayload.CANTOR, CantorEntities.FIRST_CANTOR, List.of(ModSounds.MUSIC_BOSS_CANTOR));
		BossMusicService.init();
	}

	private FoesM5Client() {
	}
}
