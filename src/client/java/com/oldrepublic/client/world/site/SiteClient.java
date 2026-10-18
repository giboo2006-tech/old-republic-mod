package com.oldrepublic.client.world.site;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.oldrepublic.client.config.ClientConfig;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.world.site.SiteEffects;
import com.oldrepublic.world.site.SitePresencePayload;
import com.oldrepublic.world.site.SiteStates;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client initialisation of the site layer, called from {@code WorldClient}:
 * <ul>
 * <li>the {@code site_presence} receiver, which drives {@link SiteAmbience} (beds, one-shots,
 * particles, the Republic mast lamp);</li>
 * <li>the {@code terminal_use} handler: the terminal's screen brightens and holds steady for a
 * moment (a soft patch of light in the army's colour on the screen face; nothing flashes) and
 * {@code terminal.use} plays;</li>
 * <li>the Force Sense ambience dip: for 20 ticks after the local player's own Sense gesture the site
 * beds and one-shots play 30% quieter (spec 10.1), eased in over 3 ticks and out over 6;</li>
 * <li>the client tick and the disconnect reset.</li>
 * </ul>
 */
public final class SiteClient {
	private static final int DIP_TICKS = 20;
	private static final float DIP = 0.3f;
	private static final int DIP_EASE_IN = 3;
	private static final int DIP_EASE_OUT = 6;
	/** How long a used terminal's screen stays bright, and the fade at its end. */
	private static final int SCREEN_TICKS = 30;
	private static final int SCREEN_FADE = 8;
	private static final int REPUBLIC_SCREEN = 0xBFE2FF;
	private static final int IMPERIAL_SCREEN = 0xFF7A55;

	private static final List<ScreenGlow> SCREENS = new ArrayList<>();
	private static long ticks;
	private static long dipStart = Long.MIN_VALUE;
	private static Object lastLevel;

	/** A terminal screen held bright for {@link #SCREEN_TICKS}. */
	private record ScreenGlow(Vec3 face, Vec3 normal, int color, int[] age) {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(SitePresencePayload.TYPE,
				(payload, context) -> SiteAmbience.INSTANCE.presence(context.client(), payload));
		ClientEffects.register(SiteEffects.TERMINAL_USE, SiteClient::terminalUse);
		ClientEffects.addListener(SiteClient::onEffect);
		ClientTickEvents.END_CLIENT_TICK.register(SiteClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> client.execute(SiteClient::reset));
	}

	/**
	 * Volume factor of the mod's site ambience: 0.7 during the 20 ticks after the local player's
	 * Force Sense gesture (eased in and out), else 1.
	 */
	public static float senseDip() {
		if (dipStart == Long.MIN_VALUE) {
			return 1f;
		}

		long age = ticks - dipStart;

		if (age < 0 || age >= DIP_TICKS) {
			return 1f;
		}

		float in = Mth.clamp((age + 1) / (float) DIP_EASE_IN, 0f, 1f);
		float out = Mth.clamp((DIP_TICKS - age) / (float) DIP_EASE_OUT, 0f, 1f);
		return 1f - DIP * Math.min(in, out);
	}

	/** Whether a site bed is playing for the local player (the planets' biome beds duck under it). */
	public static boolean siteBed() {
		return SiteAmbience.INSTANCE.hasBed();
	}

	/** Sees every effect event: the local player's own Sense gesture starts the dip. */
	private static void onEffect(EffectEventPayload payload) {
		if (!ForceEffects.SENSE.equals(payload.effect()) || payload.variant() != ForceEffects.SENSE_GESTURE) {
			return;
		}

		Minecraft client = Minecraft.getInstance();

		if (client.player != null && payload.entityId() == client.player.getId()) {
			dipStart = ticks;
		}
	}

	/**
	 * {@code terminal_use}: pos = the terminal's centre, dir = its screen side, variant = the
	 * {@link SiteStates.Army} ordinal.
	 */
	private static void terminalUse(Minecraft client, ClientLevel level, EffectEventPayload payload, Entity entity) {
		Vec3 normal = payload.dir().lengthSqr() < 1.0E-4 ? new Vec3(0, 0, -1) : payload.dir().normalize();
		boolean imperial = payload.variant() == SiteStates.Army.IMPERIAL.ordinal();
		Vec3 center = payload.pos();
		float volume = 0.8f * ClientConfig.get().modSoundVolume;

		if (volume > 0f) {
			level.playLocalSound(center.x, center.y, center.z, ModSounds.TERMINAL_USE, SoundSource.BLOCKS, volume,
					imperial ? 0.94f : 1f, false);
		}

		Vec3 face = center.add(normal.scale(0.52));
		SCREENS.removeIf(glow -> glow.face().distanceToSqr(face) < 0.01);
		SCREENS.add(new ScreenGlow(face, normal, imperial ? IMPERIAL_SCREEN : REPUBLIC_SCREEN, new int[1]));
	}

	private static void tick(Minecraft client) {
		ClientLevel level = client.level;

		if (level != lastLevel) {
			lastLevel = level;
			SCREENS.clear();
		}

		ticks++;
		SiteAmbience.INSTANCE.tick(client);

		if (level != null) {
			tickScreens(level);
		}
	}

	/**
	 * Holds each used screen bright: a soft, still patch of light motes in the army's colour over
	 * the upper screen face, steady for its length and thinning over the last {@link #SCREEN_FADE}
	 * ticks. Nothing blinks, so the look is the same with flashing effects off.
	 */
	private static void tickScreens(ClientLevel level) {
		RandomSource random = level.getRandom();
		Iterator<ScreenGlow> it = SCREENS.iterator();

		while (it.hasNext()) {
			ScreenGlow glow = it.next();
			int age = ++glow.age()[0];

			if (age > SCREEN_TICKS) {
				it.remove();
				continue;
			}

			if (!Vfx.inRange(glow.face())) {
				continue;
			}

			boolean fading = age > SCREEN_TICKS - SCREEN_FADE;

			if (fading && age % 2 != 0) {
				continue;
			}

			int count = Vfx.count(age <= 3 ? 3 : 1);
			// Horizontal tangent of the screen face.
			Vec3 side = new Vec3(-glow.normal().z, 0, glow.normal().x);
			DustParticleOptions light = new DustParticleOptions(glow.color(), fading ? 0.35f : 0.5f);

			for (int i = 0; i < count; i++) {
				Vec3 at = glow.face()
						.add(side.scale((random.nextDouble() - 0.5) * 0.62))
						.add(0, -0.05 + random.nextDouble() * 0.38, 0);
				level.addParticle(light, at.x, at.y, at.z, 0, 0, 0);
			}
		}
	}

	/** Disconnect: stop the beds, forget the presence, the screens and the dip. */
	private static void reset() {
		SCREENS.clear();
		dipStart = Long.MIN_VALUE;
		lastLevel = null;
		SiteAmbience.INSTANCE.reset();
	}

	private SiteClient() {
	}
}
