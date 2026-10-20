package com.oldrepublic.client.boss.ulvaine;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * A Hymn resonator (spec MILESTONE_5.md 12.2): a crystal spire on a squat stone plinth, about two
 * and a half blocks tall. A four-sided shaft rises from the plinth, turned a quarter, with three
 * shorter shards leaning off it at uneven angles and a small crown crystal on top that turns very
 * slowly. The inner light is a second pass ({@link ResonatorGlowLayer}).
 *
 * <p>UV layout (64x64), mirrored in {@code tools/assets/gen_textures_boss.py}: base 0,0; shaft 0,16;
 * shard A 16,16; shard B 24,16; shard C 32,16; crown 40,16; plinth 0,48.
 */
public class ResonatorModel extends EntityModel<ResonatorRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(OldRepublicMod.id("hymn_resonator"), "main");

	private final ModelPart crown;

	public ResonatorModel(ModelPart root) {
		super(root);
		this.crown = root.getChild("spire").getChild("crown");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		// The plinth: a broad block and a narrower step, standing on the floor (y 24 is the feet).
		root.addOrReplaceChild("base", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-6f, -4f, -6f, 12f, 4f, 12f)
				.texOffs(0, 48).addBox(-4.5f, -6f, -4.5f, 9f, 2f, 9f), PartPose.offset(0f, 24f, 0f));

		PartDefinition spire = root.addOrReplaceChild("spire", CubeListBuilder.create().texOffs(0, 16)
				.addBox(-2f, -26f, -2f, 4f, 26f, 4f), PartPose.offsetAndRotation(0f, 18f, 0f, 0f, 0.785f, 0f));
		spire.addOrReplaceChild("shard_a", CubeListBuilder.create().texOffs(16, 16)
				.addBox(-1f, -14f, -1f, 2f, 14f, 2f), PartPose.offsetAndRotation(2.4f, 0f, 0.3f, 0.05f, 0f, 0.3f));
		spire.addOrReplaceChild("shard_b", CubeListBuilder.create().texOffs(24, 16)
				.addBox(-1f, -10f, -1f, 2f, 10f, 2f), PartPose.offsetAndRotation(-2.2f, 0f, 1.1f, -0.24f, 0f, -0.32f));
		spire.addOrReplaceChild("shard_c", CubeListBuilder.create().texOffs(32, 16)
				.addBox(-1f, -8f, -1f, 2f, 8f, 2f), PartPose.offsetAndRotation(-0.5f, 0f, -2.3f, 0.36f, 0f, 0.06f));
		spire.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(40, 16)
				.addBox(-1.5f, -6f, -1.5f, 3f, 6f, 3f), PartPose.offsetAndRotation(0f, -26f, 0f, 0f, 0.4f, 0f));

		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(ResonatorRenderer.State state) {
		super.setupAnim(state);
		// The crown crystal turns very slowly while the spire stands.
		crown.yRot = 0.4f + state.ageInTicks * 0.012f;
	}
}
