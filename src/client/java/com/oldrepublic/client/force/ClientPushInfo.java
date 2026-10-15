package com.oldrepublic.client.force;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.network.PushInfoPayload;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;

/** The server's Force Push numbers, as last received in a {@link PushInfoPayload}. */
public final class ClientPushInfo {
	/** Used until the server has sent its values (matches the spec defaults). */
	private static final float DEFAULT_MIN_COST = 18f;
	private static final float DEFAULT_MIN_CHARGE_SECONDS = 0.25f;

	private static @Nullable PushInfoPayload info;

	public static @Nullable PushInfoPayload get() {
		return info;
	}

	static void set(@Nullable PushInfoPayload payload) {
		info = payload;
	}

	/** Cheapest possible push for this player (uncharged, after mastery). */
	static float minimumCost(ForceData data) {
		float cost = info != null ? info.costMin() : DEFAULT_MIN_COST;
		float perRank = info != null ? info.masteryCostPerRank() : 0f;
		int rank = Progression.masteryRank(data.mastery(ForcePush.ABILITY_ID));
		return cost * Math.max(0f, 1f - perRank * rank);
	}

	static int minChargeTicks() {
		return Math.round((info != null ? info.minChargeSeconds() : DEFAULT_MIN_CHARGE_SECONDS) * 20f);
	}

	private ClientPushInfo() {
	}
}
