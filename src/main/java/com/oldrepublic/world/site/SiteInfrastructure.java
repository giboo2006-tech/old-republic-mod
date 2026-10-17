package com.oldrepublic.world.site;

/**
 * Wires the shared site layer (spec 15.1): the site-anchor reload listener, the
 * {@code oldrepublic:world_sites} global attachment and the structure locator's queue and server
 * tick. {@code OldRepublicMod} calls {@link #init} once, after {@code Progression.init()} and
 * before the features.
 */
public final class SiteInfrastructure {
	/** Registers the anchor sidecar loader, the world sites attachment and the locator's events. */
	public static void init() {
		SiteAnchors.init();
		WorldSites.init();
		StructureLocator.init();
	}

	private SiteInfrastructure() {
	}
}
