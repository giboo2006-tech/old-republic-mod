package com.oldrepublic.client.space;

import com.oldrepublic.space.SpaceEntities;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;

/**
 * Milestone 5 client initialisation for the orbit sky, the planet impostors, the fighters'
 * renderers, the ship weapons' effects and the combat HUD (5.1, 5.10). Owned by W2 space; the lead
 * calls it from {@code OldRepublicModClient}, so the worker never edits the client entry point.
 *
 * <p>Every client tick runs the space state, the sky, the gunner's and pilot's fire input, the loops,
 * the timed effects and the HUD's target list, in that order; a disconnect clears all of them (a
 * dimension change clears what depends on the level in each one's own tick).
 */
public final class SpaceClient {
	public static void init() {
		ModelLayerRegistry.registerModelLayer(StarfighterModel.HYMN, StarfighterModel::createHymnLayer);
		ModelLayerRegistry.registerModelLayer(StarfighterModel.RAIDER, StarfighterModel::createRaiderLayer);
		ModelLayerRegistry.registerModelLayer(StarfighterModel.HASK, StarfighterModel::createHaskLayer);
		ModelLayerRegistry.registerModelLayer(GunshipModel.LAYER, GunshipModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(ShipMissileRenderer.LAYER, ShipMissileRenderer.MissileModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(NavBuoyRenderer.LAYER, NavBuoyRenderer.BuoyModel::createBodyLayer);

		EntityRendererRegistry.register(SpaceEntities.HYMN_INTERCEPTOR, StarfighterRenderer::hymn);
		EntityRendererRegistry.register(SpaceEntities.KAJIDIC_RAIDER, StarfighterRenderer::raider);
		EntityRendererRegistry.register(SpaceEntities.HASK_FIGHTER, StarfighterRenderer::hask);
		EntityRendererRegistry.register(SpaceEntities.BLOCKADE_GUNSHIP, GunshipRenderer::new);
		// The generator is drawn on the gunship's spine by GunshipRenderer (no drift between two entities).
		EntityRendererRegistry.register(SpaceEntities.GUNSHIP_GENERATOR, NoopRenderer::new);
		EntityRendererRegistry.register(SpaceEntities.SHIP_BOLT, ShipBoltRenderer::new);
		EntityRendererRegistry.register(SpaceEntities.SHIP_MISSILE, ShipMissileRenderer::new);
		EntityRendererRegistry.register(SpaceEntities.NAV_BUOY, NavBuoyRenderer::new);

		SpaceParticleProviders.init();
		SpaceClientState.init();
		SpaceFx.init();
		CombatHud.init();
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(OrbitSky::render);
		ClientTickEvents.END_CLIENT_TICK.register(SpaceClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	private static void tick(Minecraft mc) {
		SpaceClientState.tick(mc);
		OrbitSky.tick(mc);
		ShipWeaponInput.tick(mc);
		SpaceLoops.tick(mc);
		SpaceFx.tick(mc);
		CombatHud.tick(mc);
	}

	private static void clear() {
		SpaceClientState.clear();
		ShipWeaponInput.clear();
		SpaceLoops.clear();
		SpaceFx.clear();
		CombatHud.clear();
		OrbitSky.clear();
		SkyMaps.clear();
	}

	private SpaceClient() {
	}
}
