package com.oldrepublic.saber.forge;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * The Forge (docs/MILESTONE_7.md 5): the workbench's third tab, where a player studies hilt designs and
 * forges and re-forges parts in the hilt materials. Registers the player's {@link ForgeAttachments known
 * designs} and the {@link ForgeActionPayload Study, Forge and Re-forge intent}; the rules are
 * {@link ForgeRules}, the actions the workbench menu's. Called once from {@code M7Contracts.init()}.
 */
public final class ForgeFeature {
	public static void init() {
		ForgeAttachments.init();

		PayloadTypeRegistry.serverboundPlay().register(ForgeActionPayload.TYPE, ForgeActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ForgeActionPayload.TYPE, (payload, context) -> ForgeService.handle(context.player(), payload));
	}

	private ForgeFeature() {
	}
}
