package com.oldrepublic.client.planet;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.PlanetWeatherPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Milestone 5 client initialisation for Ghessar's and Tyrvann's weather, the verse stones and the
 * planets' new effects (section 3, 15). Owned by W3 worlds; the lead calls it from
 * {@code OldRepublicModClient}, so the worker never edits the client entry point.
 * <ul>
 * <li>the {@code planet_weather} receiver and the sandstorm ({@link Sandstorm}), its fog and the
 * mire's ({@link PlanetFog}, through {@code mixin.planet.PlanetFogMixin});</li>
 * <li>the dimmed storm flash ({@link StormFlash}, through {@code mixin.planet.StormFlashMixin});</li>
 * <li>the verse stones' motes, loops and channel ring ({@link VerseStoneClient});</li>
 * <li>the {@code sandstorm}, {@code verse_stone}, {@code stormglass} and {@code site_door} effects
 * ({@link PlanetM5EffectHandlers});</li>
 * <li>the client tick of all of these, and their reset on disconnect.</li>
 * </ul>
 * The {@code sand_gust} and {@code verse_mote} providers are registered with the M4 ones
 * ({@code PlanetParticleProviders}), the six new biome beds and the new block ambience live in
 * {@code PlanetAmbience}, and the Saltgate, holdfast and Hymnal beds in
 * {@code client.world.site.SiteAmbience}. Ghessar's twin sun is cut (spec MILESTONE_5.md 1, cut item 1).
 */
public final class PlanetM5Client {
	public static void init() {
		PlanetM5EffectHandlers.init();
		ClientPlayNetworking.registerGlobalReceiver(PlanetWeatherPayload.TYPE, (payload, context) -> Sandstorm.INSTANCE.receive(context.client(), payload.state()));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Sandstorm.INSTANCE.tick(client);
			PlanetFog.tick(client);
			StormFlash.tick(client);
			VerseStoneClient.INSTANCE.tick(client);
			Bursts.tick(client);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			Sandstorm.INSTANCE.reset();
			PlanetFog.reset();
			VerseStoneClient.INSTANCE.reset();
			Bursts.clear();
		}));

		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, OldRepublicMod.id("storm_flash"), StormFlash::extract);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, OldRepublicMod.id("verse_channel_ring"), VerseStoneClient::extractRing);
	}

	private PlanetM5Client() {
	}
}
