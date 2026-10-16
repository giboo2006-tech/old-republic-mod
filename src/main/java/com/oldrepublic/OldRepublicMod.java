package com.oldrepublic;

import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.config.BalanceConfig;
import com.oldrepublic.enemy.EnemyFeature;
import com.oldrepublic.force.ForceFeature;
import com.oldrepublic.identity.IdentityFeature;
import com.oldrepublic.network.ModNetworking;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestFeature;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModRegistry;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.SaberFeature;
import com.oldrepublic.skill.SkillFeature;
import com.oldrepublic.world.WorldFeature;

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

		// Features. Order matters only for creative tab ordering.
		QuestFeature.init();
		ForceFeature.init();
		SaberFeature.init();
		WorldFeature.init();
		EnemyFeature.init();
		IdentityFeature.init();
		SkillFeature.init();

		OldRepublicCommands.init();
		ModRegistry.registerTab();
		LOGGER.info("Star Wars: Echoes of the Old Republic loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
