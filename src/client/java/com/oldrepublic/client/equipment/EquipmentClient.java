package com.oldrepublic.client.equipment;

import com.oldrepublic.equipment.EquipmentItems;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

/**
 * Client initialisation for the equipment feature (spec section 9): the robe models and their
 * armour renderer (9.4), the live set line in tooltips (9.1), the set-bonus toast (11.2) and the
 * {@code armour_set} flourish (13.3). The trooper sets need nothing here: vanilla draws them from
 * their equipment assets. Called from OldRepublicModClient.
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

		ItemTooltipCallback.EVENT.register(ArmourTooltips::append);
		EquipmentEffectsClient.init();
		SetBonusToast.init();
	}

	private EquipmentClient() {
	}
}
