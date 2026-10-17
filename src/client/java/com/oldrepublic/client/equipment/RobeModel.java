package com.oldrepublic.client.equipment;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The robe models of the Jedi and Sith robes (spec 9.4), one layer per slot, each a full humanoid
 * skeleton ({@code head}, {@code hat}, {@code body}, both arms and legs, so the Fabric transform copy
 * finds every vanilla part) with cubes only on the slot's own parts:
 * <ul>
 * <li>{@link #HOOD}: a deep hood with a brim over the face, side flaps framing it and a cowl over
 * the shoulders;</li>
 * <li>{@link #ROBE}: the robe body, wide sleeves with bell cuffs, a belt and a back panel hanging
 * from the waist; {@link #RANK_ROBE} adds the rank 3 mantle and the diagonal sash;</li>
 * <li>{@link #TROUSERS}: loose trousers with front and back skirt panels on each leg, so they follow
 * the legs;</li>
 * <li>{@link #BOOTS}: boots with cloth wraps.</li>
 * </ul>
 * The extra parts are children named {@code robe_*}, never a vanilla or player part name, so the
 * transform copy leaves their own pose alone.
 *
 * <p>UV layout (64x64), mirrored in {@code tools/assets/gen_textures_equipment.py} (ROBE_UV): hood
 * 0,0; brim 0,57; flaps 56,0; cowl 0,32; torso 32,0; sleeves 0,16; cuffs 24,39; back panel 48,16;
 * belt 28,32; mantle 30,50; sash 56,26; waist 0,39; legs 16,16; skirt front 44,39; skirt back 0,46;
 * boots 32,16; wraps 10,48. Left limbs mirror the right ones.
 */
public final class RobeModel {
	public static final ModelLayerLocation HOOD = layer("hood");
	public static final ModelLayerLocation ROBE = layer("robe");
	public static final ModelLayerLocation RANK_ROBE = layer("rank_robe");
	public static final ModelLayerLocation TROUSERS = layer("trousers");
	public static final ModelLayerLocation BOOTS = layer("boots");

	/** Robe cloth sits just outside vanilla's outer armour shell. */
	private static final CubeDeformation OUTER = new CubeDeformation(1.05f);
	private static final CubeDeformation INNER = new CubeDeformation(0.55f);

	private static ModelLayerLocation layer(String name) {
		return new ModelLayerLocation(OldRepublicMod.id("robe"), name);
	}

	/** The seven humanoid parts in vanilla's default pose, all without cubes. */
	private static MeshDefinition skeleton() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5f, 2f, 0f));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5f, 2f, 0f));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9f, 12f, 0f));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9f, 12f, 0f));
		return mesh;
	}

	public static LayerDefinition createHood() {
		MeshDefinition mesh = skeleton();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-4f, -8f, -4f, 8f, 8f, 8f, OUTER), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("robe_hood_brim", CubeListBuilder.create().texOffs(0, 57)
				.addBox(-4.5f, -9.4f, -6.1f, 9f, 2f, 2f), PartPose.ZERO);
		head.addOrReplaceChild("robe_hood_flap_right", CubeListBuilder.create().texOffs(56, 0)
				.addBox(-5.5f, -7.6f, -6.1f, 1f, 7f, 2f), PartPose.ZERO);
		head.addOrReplaceChild("robe_hood_flap_left", CubeListBuilder.create().texOffs(56, 0).mirror()
				.addBox(4.5f, -7.6f, -6.1f, 1f, 7f, 2f), PartPose.ZERO);
		root.getChild("body").addOrReplaceChild("robe_cowl", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-4.5f, -0.5f, -2.5f, 9f, 2f, 5f, new CubeDeformation(0.85f)), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** The robe; {@code rank} adds the mantle and sash of {@code jedi_knight_robe} and {@code sith_lord_robe}. */
	public static LayerDefinition createRobe(boolean rank) {
		MeshDefinition mesh = skeleton();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 0)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, OUTER), PartPose.ZERO);
		body.addOrReplaceChild("robe_belt", CubeListBuilder.create().texOffs(28, 32)
				.addBox(-4f, 8.5f, -2f, 8f, 2f, 4f, new CubeDeformation(1.3f)), PartPose.ZERO);
		// Hangs from the belt at the back and flares out a little behind the legs.
		body.addOrReplaceChild("robe_back_panel", CubeListBuilder.create().texOffs(48, 16)
				.addBox(-3.5f, 0f, 0f, 7f, 9f, 1f), PartPose.offsetAndRotation(0f, 9.5f, 3.2f, 0.12f, 0f, 0f));

		PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 16)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f, OUTER), PartPose.offset(-5f, 2f, 0f));
		rightArm.addOrReplaceChild("robe_cuff_right", CubeListBuilder.create().texOffs(24, 39)
				.addBox(-3.5f, 5.5f, -2.5f, 5f, 4f, 5f, new CubeDeformation(1.15f)), PartPose.ZERO);
		PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 16).mirror()
				.addBox(-1f, -2f, -2f, 4f, 12f, 4f, OUTER), PartPose.offset(5f, 2f, 0f));
		leftArm.addOrReplaceChild("robe_cuff_left", CubeListBuilder.create().texOffs(24, 39).mirror()
				.addBox(-1.5f, 5.5f, -2.5f, 5f, 4f, 5f, new CubeDeformation(1.15f)), PartPose.ZERO);

		if (rank) {
			body.addOrReplaceChild("robe_mantle", CubeListBuilder.create().texOffs(30, 50)
					.addBox(-5f, -1.2f, -3f, 10f, 3f, 6f, new CubeDeformation(1.1f)), PartPose.ZERO);
			// Across the chest from the left shoulder to the right hip.
			body.addOrReplaceChild("robe_sash", CubeListBuilder.create().texOffs(56, 26)
					.addBox(-1f, -7.5f, -0.5f, 2f, 15f, 1f), PartPose.offsetAndRotation(0f, 6f, -3.3f, 0f, 0f, 0.6f));
		}

		return LayerDefinition.create(mesh, 64, 64);
	}

	public static LayerDefinition createTrousers() {
		MeshDefinition mesh = skeleton();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 39)
				.addBox(-4f, 9f, -2f, 8f, 3f, 4f, INNER), PartPose.ZERO);
		leg(root, "right_leg", "right", -1.9f, false);
		leg(root, "left_leg", "left", 1.9f, true);
		return LayerDefinition.create(mesh, 64, 64);
	}

	/** A trouser leg with a skirt panel in front and one behind, both moving with the leg. */
	private static void leg(PartDefinition root, String name, String side, float x, boolean mirror) {
		CubeListBuilder trouser = CubeListBuilder.create().texOffs(16, 16);
		CubeListBuilder front = CubeListBuilder.create().texOffs(44, 39);
		CubeListBuilder back = CubeListBuilder.create().texOffs(0, 46);

		if (mirror) {
			trouser.mirror();
			front.mirror();
			back.mirror();
		}

		PartDefinition leg = root.addOrReplaceChild(name, trouser.addBox(-2f, 0f, -2f, 4f, 12f, 4f, INNER), PartPose.offset(x, 12f, 0f));
		leg.addOrReplaceChild("robe_skirt_front_" + side, front.addBox(-2f, -1f, -3.7f, 4f, 10f, 1f, new CubeDeformation(0.15f)), PartPose.ZERO);
		leg.addOrReplaceChild("robe_skirt_back_" + side, back.addBox(-2f, -1f, 2.7f, 4f, 10f, 1f, new CubeDeformation(0.15f)), PartPose.ZERO);
	}

	public static LayerDefinition createBoots() {
		MeshDefinition mesh = skeleton();
		PartDefinition root = mesh.getRoot();
		boot(root, "right_leg", "right", -1.9f, false);
		boot(root, "left_leg", "left", 1.9f, true);
		return LayerDefinition.create(mesh, 64, 64);
	}

	private static void boot(PartDefinition root, String name, String side, float x, boolean mirror) {
		CubeListBuilder boot = CubeListBuilder.create().texOffs(32, 16);
		CubeListBuilder wrap = CubeListBuilder.create().texOffs(10, 48);

		if (mirror) {
			boot.mirror();
			wrap.mirror();
		}

		PartDefinition leg = root.addOrReplaceChild(name, boot.addBox(-2f, 7f, -2f, 4f, 5f, 4f, OUTER), PartPose.offset(x, 12f, 0f));
		leg.addOrReplaceChild("robe_wrap_" + side, wrap.addBox(-2.5f, 6.5f, -2.5f, 5f, 2f, 5f, new CubeDeformation(0.75f)), PartPose.ZERO);
	}

	private RobeModel() {
	}
}
