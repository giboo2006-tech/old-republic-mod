package com.oldrepublic.world.site;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * The world's runtime sites (spec 11.1, global {@code oldrepublic:world_sites}): every template
 * {@link RuntimeSites} placed (outposts, shrines, mission sites, Courier's Fall, the Reliquary) and
 * the owners whose echo cairns have expired. Stored through Fabric's global attachments, saved with
 * the world, server only and never synced.
 *
 * <p>Rules and tests work on a detached {@link Data} value: {@link #snapshot} hands out a copy and
 * {@link #commit} stores a copy, so nothing ever changes the server's value in place.
 */
public final class WorldSites {
	/**
	 * One runtime placement. {@code origin} and {@code rotation} are what the template was placed
	 * with (rotation pivot {@link BlockPos#ZERO}, no mirror), so the piece can be rebuilt from the
	 * template's size ({@link RuntimeSites#pieceOf}).
	 *
	 * @param placedDay the world day of the placement ({@code gameTime / 24000})
	 */
	public record SiteRecord(SiteKind kind, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation, Identifier template, @Nullable UUID owner, long placedDay) {
		private static final Codec<SiteKind> KIND_CODEC = Codec.STRING.comapFlatMap(
				id -> {
					SiteKind kind = SiteKind.byId(id);
					return kind != null ? DataResult.success(kind) : DataResult.error(() -> "Unknown site kind " + id);
				},
				SiteKind::id);
		private static final Codec<Rotation> ROTATION_CODEC = Codec.INT.xmap(i -> Rotation.values()[Math.floorMod(i, Rotation.values().length)], Rotation::ordinal);

		public static final Codec<SiteRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
				KIND_CODEC.fieldOf("kind").forGetter(SiteRecord::kind),
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(SiteRecord::dimension),
				BlockPos.CODEC.fieldOf("origin").forGetter(SiteRecord::origin),
				ROTATION_CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(SiteRecord::rotation),
				Identifier.CODEC.fieldOf("template").forGetter(SiteRecord::template),
				UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(r -> Optional.ofNullable(r.owner())),
				Codec.LONG.optionalFieldOf("placed_day", 0L).forGetter(SiteRecord::placedDay)
		).apply(i, (kind, dimension, origin, rotation, template, owner, day) -> new SiteRecord(kind, dimension, origin, rotation, template, owner.orElse(null), day)));

		public SiteRecord {
			origin = origin.immutable();
		}

		/** Whether {@code other} is the same placement: same dimension, origin and template. */
		public boolean samePlacement(SiteRecord other) {
			return dimension.equals(other.dimension) && origin.equals(other.origin) && template.equals(other.template);
		}
	}

	/**
	 * A detached copy of the world's sites. Records keep their insertion order; at most
	 * {@link #MAX_RECORDS} are kept, and at most {@link #MAX_EXPIRED_OWNERS} expired cairn owners.
	 */
	public static final class Data {
		public static final int MAX_RECORDS = 1024;
		public static final int MAX_EXPIRED_OWNERS = 64;

		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				SiteRecord.CODEC.listOf().optionalFieldOf("records", List.of()).forGetter(d -> List.copyOf(d.records)),
				UUIDUtil.CODEC.listOf().optionalFieldOf("expired_cairn_owners", List.of()).forGetter(d -> List.copyOf(d.expiredCairnOwners))
		).apply(i, Data::new));

		private final List<SiteRecord> records = new ArrayList<>();
		private final LinkedHashSet<UUID> expiredCairnOwners = new LinkedHashSet<>();

		/** An empty value (a new world). */
		public Data() {
		}

		private Data(List<SiteRecord> records, List<UUID> expired) {
			records.forEach(this::add);
			expired.forEach(this::expireCairnOwner);
		}

		/**
		 * Adds a record. A record of the same placement (dimension, origin, template) is replaced, so
		 * adding twice is harmless. When {@link #MAX_RECORDS} are kept, the oldest record that is not
		 * an outpost is dropped first (outposts carry garrisons and the runtime outpost cap).
		 */
		public void add(SiteRecord record) {
			records.removeIf(record::samePlacement);

			if (records.size() >= MAX_RECORDS) {
				int drop = 0;

				for (int i = 0; i < records.size(); i++) {
					if (!isOutpost(records.get(i).kind())) {
						drop = i;
						break;
					}
				}

				records.remove(drop);
			}

			records.add(record);
		}

		/** Every record, oldest first (read only). */
		public List<SiteRecord> all() {
			return Collections.unmodifiableList(records);
		}

		/**
		 * The record of {@code kind} in {@code dimension} whose origin is horizontally nearest to
		 * {@code from}, within {@code radius} blocks (inclusive); null when there is none. Ties go to
		 * the older record.
		 */
		public @Nullable SiteRecord nearest(SiteKind kind, ResourceKey<Level> dimension, BlockPos from, int radius) {
			SiteRecord best = null;
			long bestDist = (long) radius * radius;

			for (SiteRecord record : records) {
				if (record.kind() != kind || !record.dimension().equals(dimension)) {
					continue;
				}

				long dx = record.origin().getX() - from.getX();
				long dz = record.origin().getZ() - from.getZ();
				long dist = dx * dx + dz * dz;

				if (dist <= bestDist && (best == null || dist < bestDist)) {
					best = record;
					bestDist = dist;
				}
			}

			return best;
		}

		/** How many records of {@code kind} there are in {@code dimension}. */
		public int count(SiteKind kind, ResourceKey<Level> dimension) {
			int count = 0;

			for (SiteRecord record : records) {
				if (record.kind() == kind && record.dimension().equals(dimension)) {
					count++;
				}
			}

			return count;
		}

		/** How many records of {@code kind} there are, in every dimension. */
		public int count(SiteKind kind) {
			int count = 0;

			for (SiteRecord record : records) {
				if (record.kind() == kind) {
					count++;
				}
			}

			return count;
		}

		/** Owners whose echo cairns remove themselves when they next tick (3.3), oldest first (read only). */
		public Set<UUID> expiredCairnOwners() {
			return Collections.unmodifiableSet(expiredCairnOwners);
		}

		/** Lists an owner's cairns as expired; past {@link #MAX_EXPIRED_OWNERS} the oldest entry is dropped. */
		public void expireCairnOwner(UUID owner) {
			expiredCairnOwners.remove(owner);
			expiredCairnOwners.add(owner);

			Iterator<UUID> oldest = expiredCairnOwners.iterator();

			while (expiredCairnOwners.size() > MAX_EXPIRED_OWNERS) {
				oldest.next();
				oldest.remove();
			}
		}

		/** An independent copy. */
		public Data copy() {
			return new Data(records, List.copyOf(expiredCairnOwners));
		}

		private static boolean isOutpost(SiteKind kind) {
			return kind == SiteKind.REPUBLIC_OUTPOST || kind == SiteKind.IMPERIAL_OUTPOST;
		}
	}

	/** The global attachment. Persistent, not synced. */
	public static final AttachmentType<Data> WORLD_SITES = AttachmentRegistry.create(
			OldRepublicMod.id("world_sites"),
			builder -> builder.persistent(Data.CODEC)
	);

	/** Loads the class so the attachment registers during mod init. */
	static void init() {
	}

	/** A copy of the server's sites; rules and tests work on it, never on the server's value. */
	public static Data snapshot(MinecraftServer server) {
		return stored(server).copy();
	}

	/** Stores a copy of {@code data} as the server's sites. */
	public static void commit(MinecraftServer server, Data data) {
		server.globalAttachments().setAttached(WORLD_SITES, data.copy());
	}

	/** Every record, oldest first (read only). */
	public static List<SiteRecord> all(MinecraftServer server) {
		return stored(server).all();
	}

	/** Adds a record ({@link Data#add}) and commits. */
	public static void add(MinecraftServer server, SiteRecord record) {
		Data data = snapshot(server);
		data.add(record);
		commit(server, data);
	}

	/** {@link Data#nearest} on the server's sites. */
	public static @Nullable SiteRecord nearest(MinecraftServer server, SiteKind kind, ResourceKey<Level> dimension, BlockPos from, int radius) {
		return stored(server).nearest(kind, dimension, from, radius);
	}

	/**
	 * The owners whose echo cairns have expired, read-only (the cairns' lazy expiry check reads it
	 * every 20 ticks, so it is not copied).
	 */
	public static Set<UUID> expiredCairnOwners(MinecraftServer server) {
		return stored(server).expiredCairnOwners();
	}

	/** The stored value. Never changed in place: every change commits a new copy. */
	private static Data stored(MinecraftServer server) {
		Data data = server.globalAttachments().getAttached(WORLD_SITES);
		return data != null ? data : new Data();
	}

	private WorldSites() {
	}
}
