package com.oldrepublic.world.site;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.world.puzzle.TombVentBlock;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Per-player site presence (spec 3.7), generalising {@code CaveTracker}. Every
 * {@code sites.presenceCheckTicks} per player (staggered by entity id) it finds the site piece the
 * player stands in through {@link StructureLocator#pieceAt}, with a per-player cache: while the
 * player stays inside the last piece's box no lookup runs. When the piece changes it sends
 * {@code site_presence} (the client ambience) and calls {@code QuestHooks.onSiteEntered}. While the
 * player is in a processional it kicks the piece's vents ({@code VENT_1}–{@code VENT_4}), which
 * worldgen never starts by itself.
 */
public final class SiteTracker {
	/** The processional template, whose vents are kicked. */
	public static final Identifier PROCESSIONAL = OldRepublicMod.id("sith_tomb/processional");
	private static final SiteAnchors.Anchor[] VENTS = {SiteAnchors.Anchor.VENT_1, SiteAnchors.Anchor.VENT_2, SiteAnchors.Anchor.VENT_3, SiteAnchors.Anchor.VENT_4};

	private static final class State {
		@Nullable ResourceKey<Level> dimension;
		@Nullable SitePiece piece;
		/** False until the client has been told the current piece (join, respawn, dimension change). */
		boolean sent;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static int ticks;

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SiteTracker::tick);
		ServerPlayerEvents.JOIN.register(player -> STATES.remove(player.getUUID()));
		ServerPlayerEvents.LEAVE.register(player -> STATES.remove(player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> resend(newPlayer));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> resend(player));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
	}

	/** The piece the player was last seen in (server side), or null. */
	public static @Nullable SitePiece current(ServerPlayer player) {
		State state = STATES.get(player.getUUID());
		return state == null ? null : state.piece;
	}

	private static void resend(ServerPlayer player) {
		State state = STATES.get(player.getUUID());

		if (state != null) {
			state.sent = false;
			state.piece = null;
		}
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		int interval = Math.max(1, SiteBalance.get().presenceCheckTicks);

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((ticks + (player.getId() & 0xFF)) % interval == 0) {
				update(player);
			}
		}
	}

	private static void update(ServerPlayer player) {
		State state = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		ResourceKey<Level> dimension = player.level().dimension();

		if (state.dimension != dimension) {
			state.dimension = dimension;
			state.piece = null;
			state.sent = false;
		}

		BlockPos pos = player.blockPosition();
		SitePiece piece;

		if (player.isSpectator() || !player.isAlive()) {
			piece = null;
		} else if (state.piece != null && state.piece.box().isInside(pos)) {
			piece = state.piece;
		} else {
			piece = StructureLocator.pieceAt(player.level(), pos);
		}

		boolean changed = !samePiece(state.piece, piece);
		state.piece = piece;

		if (changed || !state.sent) {
			state.sent = true;
			ServerPlayNetworking.send(player, SitePresencePayload.of(piece));
		}

		if (changed && piece != null) {
			QuestHooks.onSiteEntered(player, piece);
		}

		if (piece != null && PROCESSIONAL.equals(piece.template())) {
			for (SiteAnchors.Anchor vent : VENTS) {
				for (BlockPos cell : SiteAnchors.all(piece, vent)) {
					if (player.level().isLoaded(cell)) {
						TombVentBlock.kick(player.level(), cell);
					}
				}
			}
		}
	}

	private static boolean samePiece(@Nullable SitePiece a, @Nullable SitePiece b) {
		if (a == null || b == null) {
			return a == b;
		}

		return a.template().equals(b.template()) && a.siteKey().equals(b.siteKey()) && Objects.equals(a.origin(), b.origin());
	}

	private SiteTracker() {
	}
}
