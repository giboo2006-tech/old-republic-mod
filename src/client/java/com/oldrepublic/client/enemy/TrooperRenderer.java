package com.oldrepublic.client.enemy;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.enemy.EnemyItems;
import com.oldrepublic.enemy.trooper.TrooperAction;
import com.oldrepublic.enemy.trooper.TrooperEntity;
import com.oldrepublic.enemy.trooper.TrooperRole;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * Renders Republic and Imperial troopers (one renderer per army): the {@link TrooperModel} for the
 * army, the role's skin, the weapon in hand (vanilla item-in-hand layer), the pose of the current
 * {@link TrooperAction}, the tell glows of {@link TrooperGlowLayer}, a kneel while yielded, healing
 * or tending, and a downed trooper lying on its side. The weapon is hidden while disarmed, downed,
 * saluting or bound. Everything comes from synced data.
 *
 * <p>Skins: {@code textures/entity/trooper/<armyKey>_<role>.png}. A Republic grenadier or an
 * Imperial medic (normalised away by the server, but possible from old data) draws as a rifleman.
 */
public class TrooperRenderer extends HumanoidMobRenderer<TrooperEntity, TrooperRenderState, TrooperModel> {
	private static final Map<TrooperRole, Identifier> REPUBLIC_SKINS = skins("republic");
	private static final Map<TrooperRole, Identifier> IMPERIAL_SKINS = skins("imperial");
	/** Ticks a heal or tend kneel takes to settle. */
	private static final float KNEEL_EASE_TICKS = 4f;
	/** How far a downed trooper is lifted so it lies on the ground rather than in it, in blocks. */
	private static final float DOWNED_LIFT = 0.3f;

	public TrooperRenderer(EntityRendererProvider.Context context, boolean republic) {
		super(context, new TrooperModel(context.bakeLayer(republic ? TrooperModel.REPUBLIC : TrooperModel.IMPERIAL)), 0.5f);
		addLayer(new TrooperGlowLayer(this));
	}

	public static TrooperRenderer republic(EntityRendererProvider.Context context) {
		return new TrooperRenderer(context, true);
	}

	public static TrooperRenderer imperial(EntityRendererProvider.Context context) {
		return new TrooperRenderer(context, false);
	}

	private static Map<TrooperRole, Identifier> skins(String army) {
		Map<TrooperRole, Identifier> map = new EnumMap<>(TrooperRole.class);

		for (TrooperRole role : TrooperRole.values()) {
			map.put(role, OldRepublicMod.id("textures/entity/trooper/" + army + "_" + role.name().toLowerCase(Locale.ROOT) + ".png"));
		}

		return map;
	}

	/** The role whose skin and role marks are drawn: the two pairs no army fields fall back to the rifleman. */
	static TrooperRole skinRole(boolean republic, TrooperRole role) {
		if (republic && role == TrooperRole.GRENADIER || !republic && role == TrooperRole.MEDIC) {
			return TrooperRole.RIFLEMAN;
		}

		return role;
	}

	@Override
	public TrooperRenderState createRenderState() {
		return new TrooperRenderState();
	}

	@Override
	public void extractRenderState(TrooperEntity trooper, TrooperRenderState state, float partialTick) {
		super.extractRenderState(trooper, state, partialTick);
		state.republic = trooper.isRepublic();
		state.role = skinRole(state.republic, trooper.role());
		state.action = trooper.action();
		state.actionTicks = state.action == TrooperAction.IDLE ? 0f : trooper.actionTicks() + partialTick;
		state.boltStyle = trooper.boltStyle();
		state.downed = trooper.isDowned();
		state.disarmed = trooper.isDisarmed();
		state.aggressive = trooper.isAggressive();
		state.flashing = Vfx.flashing();
		state.age = trooper.tickCount + partialTick;

		boolean tending = state.action == TrooperAction.HEALING || state.action == TrooperAction.KNEEL_TEND;
		state.kneel = state.downed ? 0f : trooper.isYielded() ? 1f : tending ? Mth.clamp(state.actionTicks / KNEEL_EASE_TICKS, 0f, 1f) : 0f;

		ItemStack main = trooper.getMainHandItem();
		boolean hidden = state.downed || state.disarmed || state.action == TrooperAction.SALUTE || state.action == TrooperAction.BOUND;
		state.rifle = main.is(EnemyItems.BLASTER_RIFLE);
		state.armed = !hidden && !main.isEmpty();

		if (hidden) {
			hideMainHand(state);
		}
	}

	/** Clears the main hand's item and arm pose so the item-in-hand layer draws nothing there. */
	private static void hideMainHand(TrooperRenderState state) {
		if (state.mainArm == HumanoidArm.LEFT) {
			state.leftHandItemState.clear();
			state.leftHandItemStack = ItemStack.EMPTY;
			state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		} else {
			state.rightHandItemState.clear();
			state.rightHandItemStack = ItemStack.EMPTY;
			state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		}
	}

	@Override
	protected HumanoidModel.ArmPose getArmPose(TrooperEntity trooper, HumanoidArm arm) {
		if (arm == trooper.getMainArm() && trooper.getItemHeldByArm(arm).is(EnemyItems.BLASTER_RIFLE)) {
			TrooperAction action = trooper.action();

			if (trooper.isDisarmed() || trooper.isDowned()) {
				return HumanoidModel.ArmPose.EMPTY;
			}

			// Shouldered for the charge and while fighting; carried low while venting, in a standoff or at rest.
			boolean shouldered = action == TrooperAction.CHARGING || trooper.isAggressive() && action == TrooperAction.IDLE;
			return shouldered ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.ITEM;
		}

		return super.getArmPose(trooper, arm);
	}

	@Override
	protected void setupRotations(TrooperRenderState state, PoseStack poseStack, float bodyRot, float scale) {
		super.setupRotations(state, poseStack, bodyRot, scale);

		if (state.downed && state.deathTime <= 0f) {
			// On its side, lifted by half its width so it lies on the ground.
			poseStack.translate(0f, DOWNED_LIFT, 0f);
			poseStack.mulPose(Axis.ZP.rotationDegrees(90f));
		}
	}

	@Override
	protected void scale(TrooperRenderState state, PoseStack poseStack) {
		super.scale(state, poseStack);
		// API-CHECK: LivingEntityRenderer#scale runs after the model flip (positive y is down), as in 1.21.
		KneelPose.lower(poseStack, state.kneel);
	}

	@Override
	public Identifier getTextureLocation(TrooperRenderState state) {
		return (state.republic ? REPUBLIC_SKINS : IMPERIAL_SKINS).get(state.role);
	}
}
