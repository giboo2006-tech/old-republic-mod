package com.oldrepublic.client.equipment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.equipment.ArmourItem;
import com.oldrepublic.equipment.ArmourSet;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The chest plates that wear cloth (spec MILESTONE_4.md 6.1): the Watch battle armour's hooded
 * tabard and the Sith war armour's torn cape. The plate itself is drawn exactly as vanilla would
 * (vanilla's armour shape, {@code textures/entity/equipment/humanoid/<set>.png}); with
 * {@link ClientConfig#robeModels} on, the {@link RobeModel#PLATE_CLOTH} layer goes over it with
 * {@code textures/entity/robe/<set>.png}. The other pieces of both sets are plain equipment layers
 * that vanilla draws. Both layers copy the wearer's pose, so the cloth follows the saber and Force
 * poses like the robes do.
 */
public final class ClothedPlateRenderer implements ArmorRenderer {
	private final HumanoidModel<HumanoidRenderState> plate;
	private final HumanoidModel<HumanoidRenderState> cloth;

	public ClothedPlateRenderer(EntityRendererProvider.Context context) {
		this.plate = new HumanoidModel<>(context.bakeLayer(RobeArmorRenderer.PLAIN_CHEST));
		this.cloth = new HumanoidModel<>(context.bakeLayer(RobeModel.PLATE_CLOTH));
	}

	@Override
	public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, ItemStack stack, HumanoidRenderState state, EquipmentSlot slot,
			int light, HumanoidModel<HumanoidRenderState> contextModel) {
		if (!(stack.getItem() instanceof ArmourItem item) || slot != EquipmentSlot.CHEST) {
			return;
		}

		String asset = item.set() == ArmourSet.SITH_WAR ? "sith_war" : "watch_battle";
		OrderedSubmitNodeCollector ordered = submitNodeCollector.order(0);
		submit(contextModel, state, plate, ordered, poseStack, OldRepublicMod.id("textures/entity/equipment/humanoid/" + asset + ".png"), light, stack.hasFoil());

		if (ClientConfig.get().robeModels) {
			submit(contextModel, state, cloth, ordered, poseStack, OldRepublicMod.id("textures/entity/robe/" + asset + ".png"), light, false);
		}
	}

	private static void submit(HumanoidModel<HumanoidRenderState> contextModel, HumanoidRenderState state, HumanoidModel<HumanoidRenderState> model,
			OrderedSubmitNodeCollector ordered, PoseStack poseStack, Identifier texture, int light, boolean foil) {
		ArmorRenderer.submitTransformCopyingModel(contextModel, state, model, state, false, ordered, poseStack,
				RenderTypes.armorCutoutNoCull(texture), light, OverlayTexture.NO_OVERLAY, 0, null);

		if (foil) {
			ArmorRenderer.submitTransformCopyingModel(contextModel, state, model, state, false, ordered, poseStack,
					RenderTypes.armorEntityGlint(), light, OverlayTexture.NO_OVERLAY, 0, null);
		}
	}
}
