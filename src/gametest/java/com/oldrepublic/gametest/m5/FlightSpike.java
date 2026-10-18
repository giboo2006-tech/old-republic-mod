package com.oldrepublic.gametest.m5;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;

/**
 * The free-flight spike's steps and assertions (spec MILESTONE_5.md 4.1 and 19.2; W1's file).
 * {@code FlightClientTest} connects to a dedicated server with {@code allow-flight=false}, gives
 * the player a ship and calls {@link #run}: take the helm, fly 400 ticks of scripted input (lift-off,
 * climb, a turn at full speed, boost, a dive, a hover and a landing) and check the pass criteria of
 * 4.1; once W2 and W3 are in, the orbit trip to Saltgate. Screenshots are
 * {@code oldrepublic_m5_<nnn>_<what>} in W1's band (100 to 109). Phase 0 stub: W1 fills it in.
 */
public final class FlightSpike {
	public static void run(ClientGameTestContext context, TestDedicatedServerContext server) {
	}

	private FlightSpike() {
	}
}
