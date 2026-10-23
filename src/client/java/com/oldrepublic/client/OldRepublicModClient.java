package com.oldrepublic.client;

import com.oldrepublic.client.boss.BossClient;
import com.oldrepublic.client.combat.CombatClient;
import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.crafting.CraftingClient;
import com.oldrepublic.client.enemy.EnemyClient;
import com.oldrepublic.client.enemy.FoesClient;
import com.oldrepublic.client.enemy.FoesM5Client;
import com.oldrepublic.client.equipment.EquipmentClient;
import com.oldrepublic.client.force.ForceClient;
import com.oldrepublic.client.force.ForceM4Client;
import com.oldrepublic.client.force.ForceM5Client;
import com.oldrepublic.client.fx.CameraShake;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.holocron.HolocronClient;
import com.oldrepublic.client.hud.HudClient;
import com.oldrepublic.client.identity.IdentityClient;
import com.oldrepublic.client.mission.MissionClient;
import com.oldrepublic.client.planet.PlanetClient;
import com.oldrepublic.client.planet.PlanetM5Client;
import com.oldrepublic.client.quest.ChapterFiveClient;
import com.oldrepublic.client.quest.QuestClient;
import com.oldrepublic.client.quest.turning.TurningClient;
import com.oldrepublic.client.saber.HiltSetFx;
import com.oldrepublic.client.saber.SaberClient;
import com.oldrepublic.client.saber.SaberM5Client;
import com.oldrepublic.client.saber.workbench.ForgeClient;
import com.oldrepublic.client.skill.SkillClient;
import com.oldrepublic.client.space.SpaceClient;
import com.oldrepublic.client.travel.FlightClient;
import com.oldrepublic.client.travel.TravelClient;
import com.oldrepublic.client.vendor.VendorClient;
import com.oldrepublic.client.world.WorldClient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class OldRepublicModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientConfig.load();
		ClientEffects.init();
		ClientTickEvents.END_CLIENT_TICK.register(client -> CameraShake.tick());
		CombatClient.init();

		HudClient.init();
		QuestClient.init();
		ForceClient.init();
		SaberClient.init();
		WorldClient.init();
		EnemyClient.init();
		IdentityClient.init();
		SkillClient.init();
		HolocronClient.init();
		MissionClient.init();
		EquipmentClient.init();
		BossClient.init();
		PlanetClient.init();
		TravelClient.init();
		CraftingClient.init();
		VendorClient.init();
		ForceM4Client.init();
		FoesClient.init();
		PlanetM5Client.init();
		FlightClient.init();
		SpaceClient.init();
		SaberM5Client.init();
		ForceM5Client.init();
		FoesM5Client.init();
		TurningClient.init();
		ChapterFiveClient.init();
		ForgeClient.init();
		HiltSetFx.init();
	}
}
