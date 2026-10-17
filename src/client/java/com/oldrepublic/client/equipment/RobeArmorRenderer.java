package com.oldrepublic.client.equipment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.equipment.ArmourItem;
import com.oldrepublic.equipment.ArmourSet;

import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The Fabric armour renderer of the ten robe items (spec 9.4). Fabric cancels vanilla armour
 * rendering for every item with a registered renderer, and registration is permanent, so this
 * renderer honours {@link ClientConfig#robeModels} itself:
 * <ul>
 * <li>on: the {@link RobeModel} of the slot (hood, robe or rank 3 robe, trousers, boots) with its
 * 64x64 texture {@code textures/entity/robe/<asset>.png};</li>
 * <li>off: vanilla's armour shapes (baked into this mod's own {@link #PLAIN} layers from
 * {@code HumanoidModel.createArmorMeshSet}) with the plain equipment layer
 * {@code textures/entity/equipment/humanoid[_leggings]/<asset>.png}, which looks exactly like the
 * vanilla equipment layer.</li>
 * </ul>
 * Both paths copy the pose of the wearer's model through
 * {@link ArmorRenderer#submitTransformCopyingModel}, so the robes follow the saber and Force poses,
 * and both draw the enchantment glint. Hoods never let vanilla draw the item on the head.
 */
public final class RobeArmorRenderer implements ArmorRenderer {
	/** The plain armour shapes (vanilla's inner 0.5 / outer 1.0 deformation, 64x32). */
	public static final ModelLayerLocation PLAIN_HEAD = plain("head");
	public static final ModelLayerLocation PLAIN_CHEST = plain("chest");
	public static final ModelLayerLocation PLAIN_LEGS = plain("legs");
	public static final ModelLayerLocation PLAIN_FEET = plain("feet");
	public static final ArmorModelSet<ModelLayerLocation> PLAIN = new ArmorModelSet<>(PLAIN_HEAD, PLAIN_CHEST, PLAIN_LEGS, PLAIN_FEET);

	private final HumanoidModel<HumanoidRenderState> hood;
	private final HumanoidModel<HumanoidRenderState> robe;
	private final HumanoidModel<HumanoidRenderState> rankRobe;
	private final HumanoidModel<HumanoidRenderState> trousers;
	private final HumanoidModel<HumanoidRenderState> boots;
	private final HumanoidModel<HumanoidRenderState> plainHead;
	private final HumanoidModel<HumanoidRenderState> plainChest;
	private final HumanoidModel<HumanoidRenderState> plainLegs;
	private final HumanoidModel<HumanoidRenderState> plainFeet;

	public RobeArmorRenderer(EntityRendererProvider.Context context) {
		this.hood = new HumanoidModel<>(context.bakeLayer(RobeModel.HOOD));
		this.robe = new HumanoidModel<>(context.bakeLayer(RobeModel.ROBE));
		this.rankRobe = new HumanoidModel<>(context.bakeLayer(RobeModel.RANK_ROBE));
		this.trousers = new HumanoidModel<>(context.bakeLayer(RobeModel.TROUSERS));
		this.boots = new HumanoidModel<>(context.bakeLayer(RobeModel.BOOTS));
		this.plainHead = new HumanoidModel<>(context.bakeLayer(PLAIN_HEAD));
		this.plainChest = new HumanoidModel<>(context.bakeLayer(PLAIN_CHEST));
		this.plainLegs = new HumanoidModel<>(context.bakeLayer(PLAIN_LEGS));
		this.plainFeet = new HumanoidModel<>(context.bakeLayer(PLAIN_FEET));
	}

	private static ModelLayerLocation plain(String slot) {
		return new ModelLayerLocation(OldRepublicMod.id("robe_plain"), slot);
	}

	/** The equipment asset name of a robe item: {@code jedi_robes}, {@code sith_robes}, or the rank 3 {@code jedi_knight}, {@code sith_lord}. */
	static String asset(ArmourItem item) {
		boolean jedi = item.set() == ArmourSet.JEDI_ROBES;

		if (item.isRankRobe()) {
			return jedi ? "jedi_knight" : "sith_lord";
		}

		return jedi ? "jedi_robes" : "sith_robes";
	}

	@Override
	public void render(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, ItemStack stack, HumanoidRenderState state, EquipmentSlot slot,
			int light, HumanoidModel<HumanoidRenderState> contextModel) {
		if (!(stack.getItem() instanceof ArmourItem item)) {
			return;
		}

		String asset = asset(item);
		HumanoidModel<HumanoidRenderState> model;
		Identifier texture;

		if (ClientConfig.get().robeModels) {
			model = switch (slot) {
				case HEAD -> hood;
				case CHEST -> item.isRankRobe() ? rankRobe : robe;
				case LEGS -> trousers;
				default -> boots;
			};
			texture = OldRepublicMod.id("textures/entity/robe/" + asset + ".png");
		} else {
			model = switch (slot) {
				case HEAD -> plainHead;
				case CHEST -> plainChest;
				case LEGS -> plainLegs;
				default -> plainFeet;
			};
			String layer = slot == EquipmentSlot.LEGS ? "humanoid_leggings" : "humanoid";
			texture = OldRepublicMod.id("textures/entity/equipment/" + layer + "/" + asset + ".png");
		}

		OrderedSubmitNodeCollector ordered = submitNodeCollector.order(0);
		ArmorRenderer.submitTransformCopyingModel(contextModel, state, model, state, false, ordered, poseStack,
				RenderTypes.armorCutoutNoCull(texture), light, OverlayTexture.NO_OVERLAY, 0, null);

		if (stack.hasFoil()) {
			ArmorRenderer.submitTransformCopyingModel(contextModel, state, model, state, false, ordered, poseStack,
					RenderTypes.armorEntityGlint(), light, OverlayTexture.NO_OVERLAY, 0, null);
		}
	}

	/** Robes are only ever drawn by this renderer: never the item model on the head. */
	@Override
	public boolean shouldRenderDefaultHeadItem(LivingEntity entity, ItemStack stack) {
		return false;
	}
}
