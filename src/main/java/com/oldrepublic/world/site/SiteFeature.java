package com.oldrepublic.world.site;

import com.oldrepublic.world.outpost.TerminalBinding;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Server and common initialisation of W1's M3 sites (plan 4.1): the bounty terminal, echo cairn and
 * site spawner blocks with their block entities, their POI types, the
 * {@code oldrepublic:adapted_pool_element} pool element type, the {@code site_presence} payload, the
 * site tracker, the site commands and the terminal binding's entity-load listener. The shared site
 * layer (anchors, {@code WorldSites}, the locator queue) is wired by {@link SiteInfrastructure}.
 * Called from {@code WorldFeature}.
 */
public final class SiteFeature {
	public static void init() {
		SiteBlocks.init();
		SitePois.init();
		AdaptedPoolElement.init();
		PayloadTypeRegistry.clientboundPlay().register(SitePresencePayload.TYPE, SitePresencePayload.CODEC);
		SiteTracker.init();
		SiteCommands.init();
		ServerEntityEvents.ENTITY_LOAD.register(TerminalBinding::onEntityLoad);
	}

	private SiteFeature() {
	}
}
