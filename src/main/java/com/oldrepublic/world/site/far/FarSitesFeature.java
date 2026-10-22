package com.oldrepublic.world.site.far;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.TicketType;

/**
 * J1's entry point for far placement (docs/MILESTONE_6.md 7.1, 8.2): the ticket type, the
 * {@code oldrepublic:far_sites} attachment and the server tick that drives the requests. Called from
 * {@code StructuresFeature}.
 */
public final class FarSitesFeature {
	/**
	 * {@code oldrepublic:far_site}: loads the area around a far target (no simulation, never saved).
	 * Its timeout is a safety net: the requests refresh it while they need it and remove it after.
	 */
	static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, OldRepublicMod.id("far_site"),
			new TicketType(FarJobs.TICKET_TIMEOUT, TicketType.FLAG_LOADING));

	/** Registers the ticket type (by loading this class), the attachment, the tick and the clean-up when the server stops. */
	public static void init() {
		FarSiteData.init();
		ServerTickEvents.END_SERVER_TICK.register(FarJobs::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FarJobs.clear());
	}

	private FarSitesFeature() {
	}
}
