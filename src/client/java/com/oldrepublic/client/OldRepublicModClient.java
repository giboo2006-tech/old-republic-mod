package com.oldrepublic.client;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.enemy.EnemyClient;
import com.oldrepublic.client.force.ForceClient;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.hud.HudClient;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.saber.SaberClient;
import com.oldrepublic.client.world.WorldClient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class OldRepublicModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfig.load();
		ClientEffects.init();
		ClientTickEvents.END_CLIENT_TICK.register(client -> CameraShake.tick());

		HudClient.init();
		QuestClient.init();
		ForceClient.init();
		SaberClient.init();
		WorldClient.init();
		EnemyClient.init();
	}
}
