package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.world.site.SiteChecks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Chunk tickets that keep a pad loaded while a ship flies to it (spec MILESTONE_4.md 4.6): the
 * Spool adds one at the destination, the flight refreshes it, and it is released
 * {@code travel.ticketLingerTicks} after Settle. The type's own timeout is a safety net only, so a
 * lost release can never keep chunks loaded for long; tickets are never saved.
 */
public final class TravelTickets {
	/** Ticks a ticket lives without a refresh. */
	private static final long TIMEOUT = 1200L;

	// API-CHECK: TicketType is registered in BuiltInRegistries.TICKET_TYPE (1.21.5+). If this registry is
	// missing in 26.2, replace SHIP_PAD with TicketType.PORTAL (the spec's fallback) and delete this registration.
	/** {@code oldrepublic:ship_pad}: loads and simulates, keeps the dimension active, not saved. */
	public static final TicketType SHIP_PAD = Registry.register(BuiltInRegistries.TICKET_TYPE, OldRepublicMod.id("ship_pad"),
			new TicketType(TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE));

	/** A ticket that is held, and the game time it is released (Long.MAX_VALUE while a flight holds it). */
	private record Held(ResourceKey<Level> dimension, ChunkPos chunk, int radius, long releaseAt) {
	}

	private static final List<Held> HELD = new ArrayList<>();

	/** Loads the class at mod init, so {@link #SHIP_PAD} is registered before the registries freeze. */
	public static void init() {
	}

	/** Adds (or refreshes) a ticket of {@code radius} chunks around {@code pos}. */
	public static void hold(MinecraftServer server, GlobalPos pos, int radius) {
		ServerLevel level = server.getLevel(pos.dimension());

		if (level == null) {
			return;
		}

		ChunkPos chunk = ChunkPos.containing(pos.pos());
		level.getChunkSource().addTicketWithRadius(SHIP_PAD, chunk, radius);
		HELD.removeIf(h -> h.dimension().equals(pos.dimension()) && h.chunk().equals(chunk) && h.radius() == radius);
		HELD.add(new Held(pos.dimension(), chunk, radius, Long.MAX_VALUE));
	}

	/** Lets the ticket around {@code pos} go after {@code lingerTicks}. */
	public static void release(MinecraftServer server, GlobalPos pos, int radius, int lingerTicks) {
		ChunkPos chunk = ChunkPos.containing(pos.pos());
		long at = Clocks.time(server) + Math.max(0, lingerTicks);
		List<Held> updated = new ArrayList<>();

		for (Iterator<Held> it = HELD.iterator(); it.hasNext();) {
			Held h = it.next();

			if (h.dimension().equals(pos.dimension()) && h.chunk().equals(chunk) && h.radius() == radius) {
				it.remove();
				updated.add(new Held(h.dimension(), h.chunk(), h.radius(), at));
			}
		}

		HELD.addAll(updated);
	}

	/** Refreshes held tickets before their timeout and removes released ones whose time has come. */
	static void tick(MinecraftServer server) {
		long now = Clocks.time(server);

		for (Iterator<Held> it = HELD.iterator(); it.hasNext();) {
			Held h = it.next();
			ServerLevel level = server.getLevel(h.dimension());

			if (level == null) {
				it.remove();
			} else if (now >= h.releaseAt()) {
				level.getChunkSource().removeTicketWithRadius(SHIP_PAD, h.chunk(), h.radius());
				it.remove();
			} else if (now % (TIMEOUT / 2) == 0) {
				level.getChunkSource().addTicketWithRadius(SHIP_PAD, h.chunk(), h.radius());
			}
		}
	}

	/** Whether the pad's chunks are loaded with their entities and the pad itself is entity-ticking. */
	public static boolean ready(ServerLevel level, BlockPos pos, int radius) {
		return SiteChecks.entitiesReady(level, pos, Math.min(radius, 1));
	}

	static void clear() {
		HELD.clear();
	}

	private TravelTickets() {
	}
}
