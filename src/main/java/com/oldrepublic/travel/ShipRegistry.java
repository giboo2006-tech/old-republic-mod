package com.oldrepublic.travel;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.MinecraftServer;

/**
 * Every player's ship (spec MILESTONE_4.md 4.9, global {@code oldrepublic:ships}): saved with the
 * world, server only and never synced. One record per owner.
 *
 * <p>Rules and tests work on a detached {@link Data} value, as with {@code WorldSites}: nothing
 * changes the server's value in place.
 */
public final class ShipRegistry {
	/** A detached copy of the server's ships, keyed by owner. */
	public static final class Data {
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				ShipRecord.CODEC.listOf().optionalFieldOf("ships", List.of()).forGetter(d -> List.copyOf(d.ships.values()))
		).apply(i, Data::new));

		private final Map<UUID, ShipRecord> ships = new LinkedHashMap<>();

		public Data() {
		}

		private Data(List<ShipRecord> records) {
			records.forEach(this::put);
		}

		public Optional<ShipRecord> get(UUID owner) {
			return Optional.ofNullable(ships.get(owner));
		}

		/** Adds or replaces the owner's record. */
		public void put(ShipRecord record) {
			ships.put(record.owner(), record);
		}

		public void remove(UUID owner) {
			ships.remove(owner);
		}

		/** Every record (read only), in the order owners first got a ship. */
		public Collection<ShipRecord> all() {
			return Collections.unmodifiableCollection(ships.values());
		}

		/** The owner whose current body has this UUID, if any. */
		public Optional<ShipRecord> byBody(UUID body) {
			for (ShipRecord record : ships.values()) {
				if (record.body().equals(body)) {
					return Optional.of(record);
				}
			}

			return Optional.empty();
		}

		public Data copy() {
			return new Data(List.copyOf(ships.values()));
		}
	}

	/** The global attachment. Persistent, not synced. */
	public static final AttachmentType<Data> SHIPS = AttachmentRegistry.create(
			OldRepublicMod.id("ships"),
			builder -> builder.persistent(Data.CODEC)
	);

	/** The owner's ship, if they have one. */
	public static Optional<ShipRecord> of(MinecraftServer server, UUID owner) {
		return stored(server).get(owner);
	}

	/** The record whose current body has this UUID, if any (read only; no copy). */
	public static Optional<ShipRecord> byBody(MinecraftServer server, UUID body) {
		return stored(server).byBody(body);
	}

	/** Stores the owner's record (replacing any older one). */
	public static void put(MinecraftServer server, ShipRecord record) {
		Data data = snapshot(server);
		data.put(record);
		commit(server, data);
	}

	/** A copy of the server's ships. */
	public static Data snapshot(MinecraftServer server) {
		return stored(server).copy();
	}

	/** Stores a copy of {@code data} as the server's ships. */
	public static void commit(MinecraftServer server, Data data) {
		server.globalAttachments().setAttached(SHIPS, data.copy());
	}

	private static Data stored(MinecraftServer server) {
		Data data = server.globalAttachments().getAttached(SHIPS);
		return data != null ? data : new Data();
	}

	/** Loads the class so the attachment registers during mod init. */
	public static void init() {
	}

	private ShipRegistry() {
	}
}
