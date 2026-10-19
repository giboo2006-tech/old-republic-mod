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
 * <li>{@link #BOOTS}: boots with cloth wraps;</li>
 * <li>{@link #PLATE_CLOTH}: the tabard or cape over the Watch battle and Sith war chest plates
 * (Milestone 4);</li>
 * <li>the rank 4 pieces (MILESTONE_5.md 8.5): {@link #MASTER_ROBE}, the rank robe with a long cloak;
 * {@link #DARTH_ROBE}, the rank robe with a high-collared mantle; {@link #WAYFARER_MANTLE}, a jacket
 * under a travelling capelet and mantle; and {@link #CANTORS_MASK} (12.3), a face plate with a crest
 * and a strap.</li>
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
	/** The cloth over the Watch battle and Sith war chest plates ({@link ClothedPlateRenderer}). */
	public static final ModelLayerLocation PLATE_CLOTH = layer("plate_cloth");
	/** {@code jedi_master_robe} (128x64). */
	public static final ModelLayerLocation MASTER_ROBE = layer("master_robe");
	/** {@code sith_darth_robe} (128x64). */
	public static final ModelLayerLocation DARTH_ROBE = layer("darth_robe");
	/** {@code wayfarer_mantle} (64x64, its own layout). */
	public static final ModelLayerLocation WAYFARER_MANTLE = layer("wayfarer_mantle");
	/** {@code cantors_mask} (64x32). */
	public static final ModelLayerLocation CANTORS_MASK = layer("cantors_mask");

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
		robe(mesh.getRoot(), rank, true);
		return LayerDefinition.create(mesh, 64, 64);
	}

	/**
	 * The robe's parts on {@code root} (the 64x64 layout above): the body, belt and sleeves with their
	 * cuffs; {@code backPanel} the panel hanging from the belt; {@code rank} the mantle and the sash.
	 * Returns the body.
	 */
	private static PartDefinition robe(PartDefinition root, boolean rank, boolean backPanel) {
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 0)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, OUTER), PartPose.ZERO);
		body.addOrReplaceChild("robe_belt", CubeListBuilder.create().texOffs(28, 32)
				.addBox(-4f, 8.5f, -2f, 8f, 2f, 4f, new CubeDeformation(1.3f)), PartPose.ZERO);

		if (backPanel) {
			// Hangs from the belt at the back and flares out a little behind the legs.
			body.addOrReplaceChild("robe_back_panel", CubeListBuilder.create().texOffs(48, 16)
					.addBox(-3.5f, 0f, 0f, 7f, 9f, 1f), PartPose.offsetAndRotation(0f, 9.5f, 3.2f, 0.12f, 0f, 0f));
		}

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

		return body;
	}

	/**
	 * The Jedi Master's robe (MILESTONE_5.md 8.5): the rank robe with a long cloak in place of the back
	 * panel. The cloak comes out from under the mantle and hangs to the waist on the body; its two
	 * lower halves hang from the legs to the ankles, so the cloak parts as the legs swing instead of
	 * cutting through them. Drapes over both shoulders follow the arms, and the cloak's hood lies
	 * folded at the collar.
	 *
	 * <p>128x64: the left half is the robe layout above; the right half holds the cloak 64,0
	 * (10x13x1), the hems 88,0 (5x9x1), the shoulder drapes 64,16 (5x4x5) and the hood fold 64,28
	 * (8x3x2), mirrored in {@code gen_textures_equipment.py} (RANK_FOUR_UV).
	 */
	public static LayerDefinition createMasterRobe() {
		MeshDefinition mesh = skeleton();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = robe(root, true, false);
		body.addOrReplaceChild("robe_cloak", CubeListBuilder.create().texOffs(64, 0)
				.addBox(-5f, 0f, 0f, 10f, 13f, 1f), PartPose.offsetAndRotation(0f, -0.6f, 3.15f, 0.05f, 0f, 0f));
		body.addOrReplaceChild("robe_cloak_fold", CubeListBuilder.create().texOffs(64, 28)
				.addBox(-4f, -1.5f, 0f, 8f, 3f, 2f, new CubeDeformation(0.2f)), PartPose.offset(0f, 0.4f, 4.3f));
		hem(root, "right_leg", "robe_cloak_hem_right", 88, 0, -3.1f, 5f, 9f, 4.0f, false);
		hem(root, "left_leg", "robe_cloak_hem_left", 88, 0, -1.9f, 5f, 9f, 4.0f, true);
		drape(root, "right_arm", "robe_drape_right", 64, 16, -3.5f, false);
		drape(root, "left_arm", "robe_drape_left", 64, 16, -1.5f, true);
		return LayerDefinition.create(mesh, 128, 64);
	}

	/**
	 * The Darth's robe (MILESTONE_5.md 8.5): the rank robe under a high-collared mantle. The collar
	 * rises behind the head and flares at the sides, the mantle falls as a short cape to the middle of
	 * the back (over the back panel's top) and covers both shoulders with heavy pauldron drapes.
	 *
	 * <p>128x64: the left half is the robe layout above; the right half holds the collar's back 64,0
	 * (10x7x1), its sides 88,0 (1x6x5), the pauldron drapes 64,16 (5x4x5) and the cape 64,28
	 * (11x10x1).
	 */
	public static LayerDefinition createDarthRobe() {
		MeshDefinition mesh = skeleton();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = robe(root, true, true);
		body.addOrReplaceChild("robe_collar_back", CubeListBuilder.create().texOffs(64, 0)
				.addBox(-5f, -7f, 0f, 10f, 7f, 1f), PartPose.offsetAndRotation(0f, 0.8f, 3.9f, -0.2f, 0f, 0f));
		body.addOrReplaceChild("robe_collar_right", CubeListBuilder.create().texOffs(88, 0)
				.addBox(-0.5f, -6f, -2f, 1f, 6f, 5f), PartPose.offsetAndRotation(-5.6f, 0.8f, 0.8f, 0f, 0f, -0.15f));
		body.addOrReplaceChild("robe_collar_left", CubeListBuilder.create().texOffs(88, 0).mirror()
				.addBox(-0.5f, -6f, -2f, 1f, 6f, 5f), PartPose.offsetAndRotation(5.6f, 0.8f, 0.8f, 0f, 0f, 0.15f));
		body.addOrReplaceChild("robe_mantle_cape", CubeListBuilder.create().texOffs(64, 28)
				.addBox(-5.5f, 0f, 0f, 11f, 10f, 1f), PartPose.offsetAndRotation(0f, 1f, 3.5f, 0.08f, 0f, 0f));
		drape(root, "right_arm", "robe_pauldron_right", 64, 16, -3.5f, false);
		drape(root, "left_arm", "robe_pauldron_left", 64, 16, -1.5f, true);
		return LayerDefinition.create(mesh, 128, 64);
	}

	/**
	 * The Wayfarer's mantle (MILESTONE_5.md 8.5): a travelling jacket under a capelet that covers the
	 * shoulders, a brass clasp at the throat, a lowered hood at the back and a long mantle split behind
	 * the legs (its upper part on the body, its hems on the legs, as the Master's cloak). It counts as
	 * the spacer's jacket.
	 *
	 * <p>Its own 64x64 layout, mirrored in {@code gen_textures_equipment.py} (WAYFARER_UV): capelet 0,0
	 * (11x5x7); jacket 36,0 (8x12x4); sleeves 0,16 (4x12x4); mantle 16,16 (9x12x1); belt 36,16
	 * (8x2x4); hood fold 36,24 (8x3x2); clasp 48,36 (2x2x1); shoulder caps 0,44 (5x4x5); hems 48,44
	 * (4x8x1).
	 */
	public static LayerDefinition createWayfarerMantle() {
		MeshDefinition mesh = skeleton();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(36, 0)
				.addBox(-4f, 0f, -2f, 8f, 12f, 4f, OUTER), PartPose.ZERO);
		body.addOrReplaceChild("robe_belt", CubeListBuilder.create().texOffs(36, 16)
				.addBox(-4f, 8.5f, -2f, 8f, 2f, 4f, new CubeDeformation(1.3f)), PartPose.ZERO);
		body.addOrReplaceChild("robe_capelet", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-5.5f, -0.3f, -3.5f, 11f, 5f, 7f, new CubeDeformation(0.75f)), PartPose.ZERO);
		body.addOrReplaceChild("robe_clasp", CubeListBuilder.create().texOffs(48, 36)
				.addBox(-1f, 0f, -1f, 2f, 2f, 1f), PartPose.offset(0f, 0.8f, -4.0f));
		body.addOrReplaceChild("robe_hood_fold", CubeListBuilder.create().texOffs(36, 24)
				.addBox(-4f, -1.5f, 0f, 8f, 3f, 2f, new CubeDeformation(0.2f)), PartPose.offset(0f, 0.3f, 3.6f));
		body.addOrReplaceChild("robe_mantle_back", CubeListBuilder.create().texOffs(16, 16)
				.addBox(-4.5f, 0f, 0f, 9f, 12f, 1f), PartPose.offsetAndRotation(0f, 0.6f, 3.3f, 0.06f, 0f, 0f));
		root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 16)
				.addBox(-3f, -2f, -2f, 4f, 12f, 4f, OUTER), PartPose.offset(-5f, 2f, 0f));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 16).mirror()
				.addBox(-1f, -2f, -2f, 4f, 12f, 4f, OUTER), PartPose.offset(5f, 2f, 0f));
		drape(root, "right_arm", "robe_shoulder_right", 0, 44, -3.5f, false);
		drape(root, "left_arm", "robe_shoulder_left", 0, 44, -1.5f, true);
		hem(root, "right_leg", "robe_mantle_hem_right", 48, 44, -2.6f, 4f, 8f, 4.1f, false);
		hem(root, "left_leg", "robe_mantle_hem_left", 48, 44, -1.4f, 4f, 8f, 4.1f, true);
		return LayerDefinition.create(mesh, 64, 64);
	}

	/**
	 * The Cantor's Mask (MILESTONE_5.md 12.3): a face plate just in front of the face and the skin's
	 * outer layer, a tuning-fork crest above the brow, and a strap round the head at eye height. All
	 * parts hang from the head, so the mask turns with it.
	 *
	 * <p>64x32, mirrored in {@code gen_textures_equipment.py} (MASK_UV): plate 0,0 (9x9x1); crest 24,0
	 * (3x4x1); side straps 0,12 (1x1x9); back strap 24,8 (9x1x1).
	 */
	public static LayerDefinition createCantorsMask() {
		MeshDefinition mesh = skeleton();
		PartDefinition head = mesh.getRoot().getChild("head");
		head.addOrReplaceChild("robe_mask_plate", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-4.5f, -8.5f, -5.6f, 9f, 9f, 1f), PartPose.ZERO);
		head.addOrReplaceChild("robe_mask_crest", CubeListBuilder.create().texOffs(24, 0)
				.addBox(-1.5f, -4f, 0f, 3f, 4f, 1f), PartPose.offsetAndRotation(0f, -8.3f, -5.5f, -0.25f, 0f, 0f));
		head.addOrReplaceChild("robe_mask_strap_right", CubeListBuilder.create().texOffs(0, 12)
				.addBox(-5.1f, -5.5f, -4.6f, 1f, 1f, 9f), PartPose.ZERO);
		head.addOrReplaceChild("robe_mask_strap_left", CubeListBuilder.create().texOffs(0, 12).mirror()
				.addBox(4.1f, -5.5f, -4.6f, 1f, 1f, 9f), PartPose.ZERO);
		head.addOrReplaceChild("robe_mask_strap_back", CubeListBuilder.create().texOffs(24, 8)
				.addBox(-4.5f, -5.5f, 4.1f, 9f, 1f, 1f), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 32);
	}

	/**
	 * A cloth drape over the top of an arm ({@code x} is the box's left edge in the arm's space: -3.5
	 * on the right arm, -1.5 on the left), following the arm.
	 */
	private static void drape(PartDefinition root, String arm, String name, int u, int v, float x, boolean mirror) {
		CubeListBuilder cubes = CubeListBuilder.create().texOffs(u, v);

		if (mirror) {
			cubes.mirror();
		}

		root.getChild(arm).addOrReplaceChild(name, cubes.addBox(x, -2.6f, -2.5f, 5f, 4f, 5f, new CubeDeformation(1.2f)), PartPose.ZERO);
	}

	/**
	 * A cloth hem {@code width} by {@code length} hanging behind a leg at depth {@code z}, starting at
	 * {@code x} in the leg's space and swinging with it.
	 */
	private static void hem(PartDefinition root, String leg, String name, int u, int v, float x, float width, float length, float z, boolean mirror) {
		CubeListBuilder cubes = CubeListBuilder.create().texOffs(u, v);

		if (mirror) {
			cubes.mirror();
		}

		root.getChild(leg).addOrReplaceChild(name, cubes.addBox(x, 0f, 0f, width, length, 1f),
				PartPose.offsetAndRotation(0f, 0.4f, z, 0.05f, 0f, 0f));
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

	/**
	 * The cloth worn over a chest plate (spec MILESTONE_4.md 6.1): a back panel long enough for a
	 * cape, a front tabard panel hanging from the belt and a lowered hood folded at the back of the
	 * collar. One shape serves both sets; each texture cuts away what its set lacks (the Watch tabard
	 * keeps the hood and a short back panel; the Sith war armour keeps a long torn cape). All parts
	 * hang from the body, so they turn and bend with it.
	 *
	 * <p>UV layout (64x64), mirrored in {@code gen_textures_equipment.py} (PLATE_CLOTH_UV): back
	 * panel 0,0 (9x20x1); front panel 24,0 (6x10x1); hood fold 0,24 (8x3x2).
	 */
	public static LayerDefinition createPlateCloth() {
		MeshDefinition mesh = skeleton();
		PartDefinition body = mesh.getRoot().getChild("body");
		body.addOrReplaceChild("robe_cloth_back", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-4.5f, 0f, 0f, 9f, 20f, 1f), PartPose.offsetAndRotation(0f, 0.2f, 3.4f, 0.1f, 0f, 0f));
		body.addOrReplaceChild("robe_cloth_front", CubeListBuilder.create().texOffs(24, 0)
				.addBox(-3f, 0f, -1f, 6f, 10f, 1f), PartPose.offsetAndRotation(0f, 9.6f, -3.3f, -0.06f, 0f, 0f));
		body.addOrReplaceChild("robe_cloth_hood", CubeListBuilder.create().texOffs(0, 24)
				.addBox(-4f, -1.5f, 0f, 8f, 3f, 2f, new CubeDeformation(0.2f)), PartPose.offset(0f, 0.5f, 2.6f));
		return LayerDefinition.create(mesh, 64, 64);
	}

	private RobeModel() {
	}
}
