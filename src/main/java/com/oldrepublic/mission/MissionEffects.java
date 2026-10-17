package com.oldrepublic.mission;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.world.site.SiteKind;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Effect events of the mission feature (spec section 13.3). Both go to the player alone; the client
 * draws the toast and the tracker flash and plays the sounds ({@code client.mission.MissionFx}).
 */
public final class MissionEffects {
	/** A site kind seen for the first time: the "Discovered" toast and {@code site.discovered}. Variant = {@link SiteKind} ordinal. */
	public static final Identifier SITE_DISCOVERED = OldRepublicMod.id("site_discovered");
	/**
	 * The active mission changed: variant {@link #UPDATE_ACCEPT} ({@code mission.accept}) or
	 * {@link #UPDATE_PROGRESS} ({@code mission.update}); intensity = progress, entity id = target.
	 */
	public static final Identifier MISSION_UPDATE = OldRepublicMod.id("mission_update");
	public static final int UPDATE_ACCEPT = 0;
	public static final int UPDATE_PROGRESS = 1;

	/** Sends the discovery toast for {@code kind}. */
	static void discovered(ServerPlayer p, SiteKind kind) {
		EffectEvents.toPlayer(p, SITE_DISCOVERED, p.position(), null, p.getId(), 1f, kind.ordinal());
	}

	/** Sends a mission update: {@link #UPDATE_ACCEPT} or {@link #UPDATE_PROGRESS} with the objective's counter. */
	static void update(ServerPlayer p, int variant, int progress, int target) {
		EffectEvents.toPlayer(p, MISSION_UPDATE, p.position(), null, Math.max(0, target), progress, variant);
	}

	private MissionEffects() {
	}
}
