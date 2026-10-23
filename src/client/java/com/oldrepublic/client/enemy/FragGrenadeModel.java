package com.oldrepublic.client.enemy;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Frag grenade model: a ribbed cylinder-like body with a raised grip band, a fuse cap and its
 * arming lever, centred on the origin. Model space is y-down like every vanilla model (the cap is
 * at negative y); the renderer flips it upright with {@code scale(-1, -1, 1)}. The fuse light on the cap
 * is drawn by the renderer as glow. UV layout (32x16), mirrored in
 * {@code tools/assets/gen_textures_enemy.py}: body 0,0; band 0,7; cap 12,0; lever 20,0.
 */
public class FragGrenadeModel extends EntityModel<FragGrenadeRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("frag_grenade"), "main");
	/** The top of the fuse cap, in blocks above the model's centre, where the light sits. */
	public static final float CAP_TOP = 3f / 16f;

	public FragGrenadeModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-1.5f, -2f, -1.5f, 3f, 4f, 3f), PartPose.ZERO);
		root.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 7)
				.addBox(-1.5f, -0.5f, -1.5f, 3f, 1f, 3f, new CubeDeformation(0.25f)), PartPose.ZERO);
		root.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(12, 0)
				.addBox(-1f, -3f, -1f, 2f, 1f, 2f), PartPose.ZERO);
		root.addOrReplaceChild("lever", CubeListBuilder.create().texOffs(20, 0)
				.addBox(1.1f, -2.5f, -0.5f, 1f, 3f, 1f), PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 16);
	}
}
