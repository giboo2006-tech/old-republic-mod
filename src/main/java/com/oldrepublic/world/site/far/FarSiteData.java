package com.oldrepublic.world.site.far;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The far placement requests of the world (docs/MILESTONE_6.md 3.1, global {@code oldrepublic:far_sites}):
 * every request by key, with what it asked for, its phase, its chosen target and its placement, so
 * a restart resumes each one with the same target and a repeated request never places twice. Saved
 * with the world, server only, never synced. An old save has no value: it starts empty (3.6).
 *
 * <p>Like {@code WorldSites}, nothing changes the server's value in place: {@link #snapshot} hands
 * out a copy and {@link #commit} stores a copy.
 */
public final class FarSiteData {
	private static final Codec<Rotation> ROTATION_CODEC = Codec.INT.xmap(i -> Rotation.values()[Math.floorMod(i, Rotation.values().length)], Rotation::ordinal);
	private static final Codec<SiteKind> KIND_CODEC = Codec.STRING.comapFlatMap(id -> {
		SiteKind kind = SiteKind.byId(id);
		return kind != null ? DataResult.success(kind) : DataResult.error(() -> "Unknown site kind " + id);
	}, SiteKind::id);
	private static final Codec<FarSites.Phase> PHASE_CODEC = enumCodec(FarSites.Phase.class);
	private static final Codec<FarSites.Terrain> TERRAIN_CODEC = enumCodec(FarSites.Terrain.class);

	/**
	 * The ground a request asked for.
	 *
	 * @param template      null for a ground point ({@link FarSites.Request#point})
	 * @param from          the band's centre, or the template origin of a fixed stamp
	 * @param footprint     a ground point's square side (0 for a template: its own size counts)
	 * @param fixedRotation the turn of a fixed stamp ({@link FarSites.Request#at}), else null
	 */
	public record Shape(@Nullable Identifier template, BlockPos from, int minDistance, int maxDistance, int footprint, @Nullable Rotation fixedRotation) {
		public static final Codec<Shape> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.optionalFieldOf("template").forGetter(s -> Optional.ofNullable(s.template())),
				BlockPos.CODEC.fieldOf("from").forGetter(Shape::from),
				Codec.INT.optionalFieldOf("min", 0).forGetter(Shape::minDistance),
				Codec.INT.optionalFieldOf("max", 0).forGetter(Shape::maxDistance),
				Codec.INT.optionalFieldOf("footprint", 0).forGetter(Shape::footprint),
				ROTATION_CODEC.optionalFieldOf("rotation").forGetter(s -> Optional.ofNullable(s.fixedRotation()))
		).apply(i, (template, from, min, max, footprint, rotation) -> new Shape(template.orElse(null), from, min, max, footprint, rotation.orElse(null))));

		public Shape {
			from = from.immutable();
		}

		/** Whether this is a fixed stamp ({@link FarSites.Request#at}). */
		public boolean fixed() {
			return fixedRotation != null;
		}
	}

	/**
	 * The preferences of a request (the chained options of {@link FarSites.Request}).
	 *
	 * @param bearing    the preferred direction from {@code from}, or null
	 * @param biomes     a biome tag the target keeps to, or null
	 * @param biome      one biome the target keeps to ({@link FarRequests#inBiome}), or null
	 * @param terrain    the ground preferred, or null for the template's site meta
	 * @param loadRadius the ticket radius asked for, or -1 to compute it
	 * @param kind       the record kind asked for, or null for the template's own
	 * @param hasCheck   whether the caller gave a spot check (it lives in memory only: after a
	 *                   restart the placement waits for the caller to ask again)
	 */
	public record Prefs(@Nullable Vec3 bearing, float spread, @Nullable TagKey<Biome> biomes, @Nullable ResourceKey<Biome> biome, List<BlockPos> avoid, int separation,
			FarSites.@Nullable Terrain terrain, int loadRadius, @Nullable SiteKind kind, boolean hasCheck) {
		public static final Codec<Prefs> CODEC = RecordCodecBuilder.create(i -> i.group(
				Vec3.CODEC.optionalFieldOf("bearing").forGetter(p -> Optional.ofNullable(p.bearing())),
				Codec.FLOAT.optionalFieldOf("spread", 360f).forGetter(Prefs::spread),
				TagKey.hashedCodec(Registries.BIOME).optionalFieldOf("biomes").forGetter(p -> Optional.ofNullable(p.biomes())),
				ResourceKey.codec(Registries.BIOME).optionalFieldOf("biome").forGetter(p -> Optional.ofNullable(p.biome())),
				BlockPos.CODEC.listOf().optionalFieldOf("avoid", List.of()).forGetter(Prefs::avoid),
				Codec.INT.optionalFieldOf("separation", 0).forGetter(Prefs::separation),
				TERRAIN_CODEC.optionalFieldOf("terrain").forGetter(p -> Optional.ofNullable(p.terrain())),
				Codec.INT.optionalFieldOf("load_radius", -1).forGetter(Prefs::loadRadius),
				KIND_CODEC.optionalFieldOf("kind").forGetter(p -> Optional.ofNullable(p.kind())),
				Codec.BOOL.optionalFieldOf("has_check", false).forGetter(Prefs::hasCheck)
		).apply(i, (bearing, spread, biomes, biome, avoid, separation, terrain, loadRadius, kind, hasCheck) -> new Prefs(bearing.orElse(null), spread,
				biomes.orElse(null), biome.orElse(null), avoid, separation, terrain.orElse(null), loadRadius, kind.orElse(null), hasCheck)));

		/** No preferences: what an entry saved without them reads back as. */
		public static final Prefs NONE = new Prefs(null, 360f, null, null, List.of(), 0, null, -1, null, false);

		public Prefs {
			avoid = avoid.stream().map(BlockPos::immutable).toList();
		}
	}

	/**
	 * A placement that happened: enough to rebuild its {@link RuntimeSites.Placement}.
	 *
	 * @param templateOrigin where the template's local (0, 0, 0) went (the {@code WorldSites} record's origin)
	 * @param stand          the placement's stand ({@link RuntimeSites.Placement#origin})
	 */
	public record Placed(SiteKind kind, Identifier template, BoundingBox box, Rotation rotation, BlockPos templateOrigin, BlockPos stand) {
		public static final Codec<Placed> CODEC = RecordCodecBuilder.create(i -> i.group(
				KIND_CODEC.fieldOf("kind").forGetter(Placed::kind),
				Identifier.CODEC.fieldOf("template").forGetter(Placed::template),
				BoundingBox.CODEC.fieldOf("box").forGetter(Placed::box),
				ROTATION_CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(Placed::rotation),
				BlockPos.CODEC.fieldOf("template_origin").forGetter(Placed::templateOrigin),
				BlockPos.CODEC.fieldOf("stand").forGetter(Placed::stand)
		).apply(i, Placed::new));

		/** The record of a placement. */
		public static Placed of(RuntimeSites.Placement placement) {
			SitePiece piece = placement.piece();
			return new Placed(piece.kind(), piece.template(), piece.box(), piece.rotation(), piece.origin(), placement.origin());
		}

		/** The placement again, its piece keyed as a runtime site of {@code dimension}. */
		public RuntimeSites.Placement placement(ResourceKey<Level> dimension) {
			SitePiece piece = new SitePiece(kind, template, box, rotation, templateOrigin, SitePiece.runtimeKey(dimension, templateOrigin));
			return new RuntimeSites.Placement(piece, stand, rotation);
		}
	}

	/**
	 * One request.
	 *
	 * @param target       the chosen ground point (null while choosing)
	 * @param detail       the reason or note {@link FarSites.Status#detail} reports
	 * @param placed       the placement (only when {@link FarSites.Phase#PLACED} with a template)
	 * @param since        the overworld game time the phase began
	 * @param ticketRadius the ticket's radius in chunks around the target (0 while choosing)
	 * @param refused      earlier targets where the site could not be placed (the chooser keeps away from them)
	 */
	public record Entry(String key, ResourceKey<Level> dimension, @Nullable UUID owner, Shape shape, Prefs prefs, FarSites.Phase phase, @Nullable BlockPos target,
			String detail, @Nullable Placed placed, long since, int ticketRadius, List<BlockPos> refused) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("key").forGetter(Entry::key),
				Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
				UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(e -> Optional.ofNullable(e.owner())),
				Shape.CODEC.fieldOf("shape").forGetter(Entry::shape),
				Prefs.CODEC.optionalFieldOf("prefs", Prefs.NONE).forGetter(Entry::prefs),
				PHASE_CODEC.fieldOf("phase").forGetter(Entry::phase),
				BlockPos.CODEC.optionalFieldOf("target").forGetter(e -> Optional.ofNullable(e.target())),
				Codec.STRING.optionalFieldOf("detail", "").forGetter(Entry::detail),
				Placed.CODEC.optionalFieldOf("placed").forGetter(e -> Optional.ofNullable(e.placed())),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(Entry::since),
				Codec.INT.optionalFieldOf("ticket_radius", 0).forGetter(Entry::ticketRadius),
				BlockPos.CODEC.listOf().optionalFieldOf("refused", List.of()).forGetter(Entry::refused)
		).apply(i, (key, dimension, owner, shape, prefs, phase, target, detail, placed, since, radius, refused) -> new Entry(key, dimension, owner.orElse(null),
				shape, prefs, phase, target.orElse(null), detail, placed.orElse(null), since, radius, refused)));

		public Entry {
			target = target == null ? null : target.immutable();
			refused = refused.stream().map(BlockPos::immutable).toList();
		}

		/** A new request, choosing (its {@code since} is {@code now}). */
		public static Entry choosing(String key, ResourceKey<Level> dimension, @Nullable UUID owner, Shape shape, Prefs prefs, long now) {
			return new Entry(key, dimension, owner, shape, prefs, FarSites.Phase.CHOOSING, null, "choosing", null, now, 0, List.of());
		}

		/** Whether the request still works (choosing or loading). */
		public boolean active() {
			return phase == FarSites.Phase.CHOOSING || phase == FarSites.Phase.LOADING;
		}

		/** This request with a target chosen: loading it with a ticket of {@code radius} chunks. */
		public Entry loading(BlockPos chosen, int radius, String note, long now) {
			return new Entry(key, dimension, owner, shape, prefs, FarSites.Phase.LOADING, chosen, note, null, now, radius, refused);
		}

		/** This request placed ({@code placement} null for a ground point). */
		public Entry placed(@Nullable Placed placement, @Nullable BlockPos at, String note, long now) {
			return new Entry(key, dimension, owner, shape, prefs, FarSites.Phase.PLACED, at != null ? at : target, note, placement, now, ticketRadius, refused);
		}

		/** This request failed for {@code reason}. */
		public Entry failed(String reason, long now) {
			return new Entry(key, dimension, owner, shape, prefs, FarSites.Phase.FAILED, target, reason, null, now, ticketRadius, refused);
		}

		/** This request choosing again, its current target refused for {@code reason}. */
		public Entry retry(String reason, long now) {
			List<BlockPos> more = new ArrayList<>(refused);

			if (target != null) {
				more.add(target);
			}

			return new Entry(key, dimension, owner, shape, prefs, FarSites.Phase.CHOOSING, null, reason, null, now, 0, more);
		}

		/** The state a caller sees. */
		public FarSites.Status status() {
			return new FarSites.Status(phase, target, placed == null ? null : placed.placement(dimension), detail);
		}
	}

	/**
	 * A detached copy of the world's requests, in the order they were made. At most
	 * {@link #MAX_ENTRIES} are kept; past that the oldest finished request (placed or failed) goes
	 * first, so a site placed long ago may be requested again under its key.
	 */
	public static final class Data {
		public static final int MAX_ENTRIES = 512;

		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				Entry.CODEC.listOf().optionalFieldOf("requests", List.of()).forGetter(d -> List.copyOf(d.entries))
		).apply(i, Data::new));

		private final List<Entry> entries = new ArrayList<>();

		/** An empty value (a new world, or an old save). */
		public Data() {
		}

		private Data(List<Entry> entries) {
			entries.forEach(this::put);
		}

		/** The request with this key, or null. */
		public @Nullable Entry get(String key) {
			for (Entry entry : entries) {
				if (entry.key().equals(key)) {
					return entry;
				}
			}

			return null;
		}

		/** Adds a request, or replaces the one with the same key where it stands. */
		public void put(Entry entry) {
			for (int i = 0; i < entries.size(); i++) {
				if (entries.get(i).key().equals(entry.key())) {
					entries.set(i, entry);
					return;
				}
			}

			if (entries.size() >= MAX_ENTRIES) {
				int drop = 0;

				for (int i = 0; i < entries.size(); i++) {
					if (!entries.get(i).active()) {
						drop = i;
						break;
					}
				}

				entries.remove(drop);
			}

			entries.add(entry);
		}

		/** Forgets the request with this key; false when there was none. */
		public boolean remove(String key) {
			return entries.removeIf(entry -> entry.key().equals(key));
		}

		/** Every request, oldest first (read only). */
		public List<Entry> all() {
			return Collections.unmodifiableList(entries);
		}

		/** An independent copy. */
		public Data copy() {
			return new Data(entries);
		}
	}

	/** The global attachment. Persistent, not synced. */
	public static final AttachmentType<Data> FAR_SITES = AttachmentRegistry.create(
			OldRepublicMod.id("far_sites"),
			builder -> builder.persistent(Data.CODEC)
	);

	/** Loads the class so the attachment registers during mod init ({@link FarSitesFeature#init}). */
	static void init() {
	}

	/** A copy of the server's requests; rules and tests work on it, never on the server's value. */
	public static Data snapshot(MinecraftServer server) {
		return stored(server).copy();
	}

	/** Stores a copy of {@code data} as the server's requests. */
	public static void commit(MinecraftServer server, Data data) {
		server.globalAttachments().setAttached(FAR_SITES, data.copy());
	}

	/** The request with this key on the server, or null. */
	public static @Nullable Entry get(MinecraftServer server, String key) {
		return stored(server).get(key);
	}

	/** Every request on the server, oldest first (read only). */
	public static List<Entry> all(MinecraftServer server) {
		return stored(server).all();
	}

	/** Replaces (or adds) one request and commits. */
	static void put(MinecraftServer server, Entry entry) {
		Data data = snapshot(server);
		data.put(entry);
		commit(server, data);
	}

	/** Forgets one request and commits; false when there was none. */
	static boolean remove(MinecraftServer server, String key) {
		Data data = snapshot(server);
		boolean removed = data.remove(key);

		if (removed) {
			commit(server, data);
		}

		return removed;
	}

	/** The stored value. Never changed in place: every change commits a new copy. */
	private static Data stored(MinecraftServer server) {
		Data data = server.globalAttachments().getAttached(FAR_SITES);
		return data != null ? data : new Data();
	}

	private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
		return Codec.STRING.comapFlatMap(name -> {
			for (E value : type.getEnumConstants()) {
				if (value.name().toLowerCase(Locale.ROOT).equals(name)) {
					return DataResult.success(value);
				}
			}

			return DataResult.error(() -> "Unknown " + type.getSimpleName() + " " + name);
		}, value -> value.name().toLowerCase(Locale.ROOT));
	}

	private FarSiteData() {
	}
}
