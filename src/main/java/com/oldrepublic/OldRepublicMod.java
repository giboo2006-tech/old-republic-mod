package com.oldrepublic;

import com.oldrepublic.boss.BossFeature;
import com.oldrepublic.combat.CombatContracts;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.crafting.CraftingFeature;
import com.oldrepublic.enemy.EnemyFeature;
import com.oldrepublic.enemy.FoesFeature;
import com.oldrepublic.enemy.FoesM5Feature;
import com.oldrepublic.equipment.EquipmentFeature;
import com.oldrepublic.force.ForceFeature;
import com.oldrepublic.force.ForceM4Feature;
import com.oldrepublic.force.ForceM5Feature;
import com.oldrepublic.holocron.HolocronFeature;
import com.oldrepublic.identity.IdentityFeature;
import com.oldrepublic.mission.MissionFeature;
import com.oldrepublic.network.ModNetworking;
import com.oldrepublic.planet.PlanetFeature;
import com.oldrepublic.planet.PlanetM5Feature;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestFeature;
import com.oldrepublic.quest.story.ChapterFiveFeature;
import com.oldrepublic.quest.turning.TurningFeature;
import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.M4Contracts;
import com.oldrepublic.registry.M5Contracts;
import com.oldrepublic.registry.ModRegistry;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberFeature;
import com.oldrepublic.saber.SaberM5Feature;
import com.oldrepublic.skill.SkillFeature;
import com.oldrepublic.space.SpaceFeature;
import com.oldrepublic.travel.FlightFeature;
import com.oldrepublic.travel.TravelFeature;
import com.oldrepublic.vendor.VendorFeature;
import com.oldrepublic.world.StructuresFeature;
import com.oldrepublic.world.WorldFeature;
import com.oldrepublic.world.site.SiteInfrastructure;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OldRepublicMod implements ModInitializer {
	public static final String MOD_ID = "oldrepublic";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BalanceConfig.reload();
		ServerLifecycleEvents.SERVER_STARTING.register(server -> BalanceConfig.reload());

		ModSounds.init();
		ModComponents.init();
		ModNetworking.init();
		ForceAttachments.init();
		Progression.init();
		CombatContracts.init();
		SiteInfrastructure.init();

		// Features. Order matters only for creative tab ordering.
		QuestFeature.init();
		ForceFeature.init();
		SaberFeature.init();
		WorldFeature.init();
		EnemyFeature.init();
		IdentityFeature.init();
		SkillFeature.init();
		M3Items.init();
		HolocronFeature.init();
		MissionFeature.init();
		EquipmentFeature.init();
		BossFeature.init();
		M4Contracts.init();
		PlanetFeature.init();
		TravelFeature.init();
		CraftingFeature.init();
		VendorFeature.init();
		ForceM4Feature.init();
		FoesFeature.init();
		M5Contracts.init();
		PlanetM5Feature.init();
		FlightFeature.init();
		SpaceFeature.init();
		SaberM5Feature.init();
		ForceM5Feature.init();
		FoesM5Feature.init();
		TurningFeature.init();
		ChapterFiveFeature.init();
		StructuresFeature.init();

		OldRepublicCommands.init();
		ModRegistry.registerTab();
		LOGGER.info("Star Wars: Echoes of the Old Republic loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
