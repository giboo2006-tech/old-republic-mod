package com.oldrepublic.client.quest.turning;

import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.quest.turning.RankFourChecklistPayload;
import com.oldrepublic.quest.turning.TurningEffects;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Milestone 5 client initialisation for the turning and rank 4 effects and the rank 4 checklist
 * view's data (section 8). Owned by W5 paths and ranks; the lead calls it from {@code
 * OldRepublicModClient}, so the worker never edits the client entry point.
 *
 * <p>Receives the {@code rank_four_checklist} payload into {@link RankFourClient} (W4's Standing panel reads it), and
 * draws the {@code turning} and {@code rank_four} effects ({@link TurningFx}). A disconnect forgets both.
 */
public final class TurningClient {
	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(RankFourChecklistPayload.TYPE, (payload, context) -> RankFourClient.receive(payload));
		ClientEffects.register(TurningEffects.TURNING, TurningFx::turning);
		ClientEffects.register(TurningEffects.RANK_FOUR, TurningFx::rankFour);
		ClientTickEvents.END_CLIENT_TICK.register(TurningFx::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			RankFourClient.reset();
			TurningFx.reset();
		}));
	}

	private TurningClient() {
	}
}
