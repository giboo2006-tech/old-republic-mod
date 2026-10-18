package com.oldrepublic.travel;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The rider's camera distance (spec MILESTONE_4.md 4.6): vanilla backs the third-person camera off
 * only for a living vehicle, so a rider of the ship gets a transient {@code CAMERA_DISTANCE}
 * modifier ({@code oldrepublic:ship_camera}, +8 blocks) while aboard. It is removed on dismount,
 * and a once-a-second sweep removes it from anyone no longer aboard (death, a lost body); being
 * transient, it is never saved, so a disconnect cannot leave it behind.
 */
public final class ShipCamera {
	public static final Identifier MODIFIER = OldRepublicMod.id("ship_camera");

	/** Adds the modifier to a new rider. */
	static void attach(ServerPlayer player) {
		AttributeInstance distance = attribute(player);

		if (distance != null) {
			distance.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, ShipBalance.get().cameraDistance, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** Removes it (dismount, death, landing). */
	static void detach(ServerPlayer player) {
		AttributeInstance distance = attribute(player);

		if (distance != null && distance.hasModifier(MODIFIER)) {
			distance.removeModifier(MODIFIER);
		}
	}

	/** The sweep: anyone who carries the modifier but rides no ship loses it. */
	static void sweep(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.getVehicle() instanceof CourierShuttle)) {
				detach(player);
			}
		}
	}

	// CAMERA_DISTANCE is one of LivingEntity#createLivingAttributes, so players carry it; null only skips the modifier.
	private static @Nullable AttributeInstance attribute(ServerPlayer player) {
		return player.getAttribute(Attributes.CAMERA_DISTANCE);
	}

	private ShipCamera() {
	}
}
