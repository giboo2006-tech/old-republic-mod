package com.oldrepublic.boss.raid;

import java.util.Collection;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code boss_music} (S2C, spec MILESTONE_5.md 12.5, 18.2): a boss fight's music for one participant.
 * {@code track} 1 to n crossfades to that boss's n-th loop (as registered with the client's
 * {@code BossMusicService}) over 40 ticks; 0 fades it out. Every boss sends it: the Collector
 * ({@link #COLLECTOR}, tracks 1 to 3), Kaast ({@link #KAAST}, 1 to 3), Ulvaine ({@link #ULVAINE}) and the
 * First Cantor ({@link #CANTOR}).
 *
 * @param bossId the boss's music id
 * @param track  1 to n, or 0 to stop
 */
public record BossMusicPayload(String bossId, int track) implements CustomPacketPayload {
	public static final String COLLECTOR = "collector";
	public static final String KAAST = "kaast";
	public static final String ULVAINE = "ulvaine";
	public static final String CANTOR = "cantor";
	/** The track that stops the music. */
	public static final int STOP = 0;

	public static final Type<BossMusicPayload> TYPE = new Type<>(OldRepublicMod.id("boss_music"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BossMusicPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, BossMusicPayload::bossId,
			ByteBufCodecs.VAR_INT, BossMusicPayload::track,
			BossMusicPayload::new);

	/** Registers the payload type (common init, from {@code FoesM5Feature}). */
	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
	}

	/** Sends {@code bossId}'s {@code track} (0 stops) to {@code player}, when their client can receive it. */
	public static void send(ServerPlayer player, String bossId, int track) {
		BossMusicPayload payload = new BossMusicPayload(bossId, Math.max(0, track));

		if (ServerPlayNetworking.canSend(player, TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	/** The same to each of {@code players}. */
	public static void send(Collection<ServerPlayer> players, String bossId, int track) {
		for (ServerPlayer player : players) {
			send(player, bossId, track);
		}
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
