package com.oldrepublic.client.equipment;

import com.oldrepublic.equipment.EquipmentItems;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/**
 * Client initialisation for the equipment feature (spec section 9 and MILESTONE_4.md 5, 6, 11 and
 * 13): the robe models and their armour renderer (9.4), the cloth over the Watch battle and Sith war
 * chest plates, the live set line and mod lines in tooltips, the set-bonus toast (11.2) and the
 * {@code armour_set} flourish (13.3); the gear particles and effects, the blaster reticle, heat gauge
 * and jet pips, the vibroweapon guard bar and the jet pack's key. The plate sets need nothing else:
 * vanilla draws them from their equipment assets. Called from OldRepublicModClient.
 */
public final class EquipmentClient {
	public static void init() {
		ModelLayerRegistry.registerModelLayer(RobeModel.HOOD, RobeModel::createHood);
		ModelLayerRegistry.registerModelLayer(RobeModel.ROBE, () -> RobeModel.createRobe(false));
		ModelLayerRegistry.registerModelLayer(RobeModel.RANK_ROBE, () -> RobeModel.createRobe(true));
		ModelLayerRegistry.registerModelLayer(RobeModel.TROUSERS, RobeModel::createTrousers);
		ModelLayerRegistry.registerModelLayer(RobeModel.BOOTS, RobeModel::createBoots);
		// Vanilla's own armour shapes, for the plain look when robe models are turned off.
		ModelLayerRegistry.registerArmorModelLayers(RobeArmorRenderer.PLAIN, () -> HumanoidModel.createArmorMeshSet(new CubeDeformation(0.5f), new CubeDeformation(1f))
				.map(mesh -> LayerDefinition.create(mesh, 64, 32)));
		ArmorRenderer.register(RobeArmorRenderer::new, EquipmentItems.ROBES);
		ModelLayerRegistry.registerModelLayer(RobeModel.PLATE_CLOTH, RobeModel::createPlateCloth);
		ArmorRenderer.register(ClothedPlateRenderer::new, EquipmentItems.CLOTHED_PLATE);

		ItemTooltipCallback.EVENT.register(ArmourTooltips::append);
		ItemTooltipCallback.EVENT.register(GearTooltips::append);
		EquipmentEffectsClient.init();
		SetBonusToast.init();

		GearParticleProviders.init();
		GearEffectsClient.init();
		BlasterHud.init();
		VibroGuardHud.init();
		JetPackInput.init();
	}

	private EquipmentClient() {
	}
}
