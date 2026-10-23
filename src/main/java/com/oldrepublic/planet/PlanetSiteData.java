package com.oldrepublic.planet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;

/**
 * Where the planets' runtime places are (spec MILESTONE_4.md 3.4, 3.5, 12.1; global
 * {@code oldrepublic:planet_sites}): each planet's hub (its centre, pad bays and medbay, all in
 * that planet's level), the shared Heartstone and Sepulchre, and every owner's own sites with
 * whether the owner finished them. The templates themselves are recorded in {@code WorldSites};
 * this is the quick index the story, travel and respawn read. Persistent, server only, never
 * synced; changed only through detached copies ({@link #snapshot} and {@link #commit}).
 */
public final class PlanetSiteData {
	/** Owner site entries kept at most; the oldest is dropped first. */
	public static final int MAX_OWNED = 1024;

	/**
	 * A placed hub. All positions are feet positions in the planet's level.
	 *
	 * @param centre the pad centre (the template's {@code HEART} anchor)
	 * @param raised whether it stands on the fallback platform (3.5)
	 */
	public record Hub(BlockPos centre, List<BlockPos> padBays, BlockPos medbay, boolean raised) {
		public static final Codec<Hub> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("centre").forGetter(Hub::centre),
				BlockPos.CODEC.listOf().optionalFieldOf("pad_bays", List.of()).forGetter(Hub::padBays),
				BlockPos.CODEC.fieldOf("medbay").forGetter(Hub::medbay),
				Codec.BOOL.optionalFieldOf("raised", false).forGetter(Hub::raised)
		).apply(i, Hub::new));

		public Hub {
			centre = centre.immutable();
			padBays = padBays.stream().map(BlockPos::immutable).toList();
			medbay = medbay.immutable();
		}
	}

	/** One owner's site: where it is and whether the owner has finished it. */
	public record Owned(UUID owner, PlanetSites.Site site, GlobalPos pos, boolean done) {
		public static final Codec<Owned> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Owned::owner),
				SITE_CODEC.fieldOf("site").forGetter(Owned::site),
				GlobalPos.CODEC.fieldOf("pos").forGetter(Owned::pos),
				Codec.BOOL.optionalFieldOf("done", false).forGetter(Owned::done)
		).apply(i, Owned::new));
	}

	/** Sites by lower-case name, so an appended site never shifts saved ones. */
	static final Codec<PlanetSites.Site> SITE_CODEC = Codec.STRING.comapFlatMap(id -> {
		for (PlanetSites.Site site : PlanetSites.Site.values()) {
			if (site.name().toLowerCase(Locale.ROOT).equals(id)) {
				return DataResult.success(site);
			}
		}

		return DataResult.error(() -> "Unknown planet site " + id);
	}, site -> site.name().toLowerCase(Locale.ROOT));

	private static final Codec<Planets.Planet> PLANET_CODEC = Codec.STRING.comapFlatMap(id -> {
		Planets.Planet planet = Planets.Planet.byId(id);
		return planet != null ? DataResult.success(planet) : DataResult.error(() -> "Unknown planet " + id);
	}, Planets.Planet::id);

	/** A detached copy of the index. */
	public static final class Data {
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(PLANET_CODEC, Hub.CODEC).optionalFieldOf("hubs", Map.of()).forGetter(d -> Map.copyOf(d.hubs)),
				Codec.unboundedMap(SITE_CODEC, GlobalPos.CODEC).optionalFieldOf("shared", Map.of()).forGetter(d -> Map.copyOf(d.shared)),
				Owned.CODEC.listOf().optionalFieldOf("owned", List.of()).forGetter(d -> List.copyOf(d.owned))
		).apply(i, Data::new));

		private final Map<Planets.Planet, Hub> hubs = new EnumMap<>(Planets.Planet.class);
		private final Map<PlanetSites.Site, GlobalPos> shared = new EnumMap<>(PlanetSites.Site.class);
		private final List<Owned> owned = new ArrayList<>();

		public Data() {
		}

		private Data(Map<Planets.Planet, Hub> hubs, Map<PlanetSites.Site, GlobalPos> shared, List<Owned> owned) {
			this.hubs.putAll(hubs);
			this.shared.putAll(shared);
			owned.forEach(this::putOwned);
		}

		public @Nullable Hub hub(Planets.Planet planet) {
			return hubs.get(planet);
		}

		public void putHub(Planets.Planet planet, Hub hub) {
			hubs.put(planet, hub);
		}

		/** The server-wide position of a shared site (the Heartstone, the Sepulchre), or null. */
		public @Nullable GlobalPos shared(PlanetSites.Site site) {
			return shared.get(site);
		}

		public void putShared(PlanetSites.Site site, GlobalPos pos) {
			shared.put(site, pos);
		}

		/** The owner's entry for {@code site}, or null. */
		public @Nullable Owned owned(UUID owner, PlanetSites.Site site) {
			for (Owned entry : owned) {
				if (entry.owner().equals(owner) && entry.site() == site) {
					return entry;
				}
			}

			return null;
		}

		/** Adds or replaces the owner's entry for that site; past {@link #MAX_OWNED} the oldest entry goes. */
		public void putOwned(Owned entry) {
			owned.removeIf(e -> e.owner().equals(entry.owner()) && e.site() == entry.site());

			if (owned.size() >= MAX_OWNED) {
				owned.removeFirst();
			}

			owned.add(entry);
		}

		/** Every owner entry, oldest first (read only). */
		public List<Owned> owned() {
			return Collections.unmodifiableList(owned);
		}

		public Data copy() {
			return new Data(hubs, shared, owned);
		}
	}

	/** The global attachment. Persistent, not synced. */
	public static final AttachmentType<Data> PLANET_SITES = AttachmentRegistry.create(OldRepublicMod.id("planet_sites"),
			builder -> builder.persistent(Data.CODEC));

	/** A copy of the server's index. */
	public static Data snapshot(MinecraftServer server) {
		Data data = server.globalAttachments().getAttached(PLANET_SITES);
		return data != null ? data.copy() : new Data();
	}

	/** Stores a copy of {@code data} as the server's index. */
	public static void commit(MinecraftServer server, Data data) {
		server.globalAttachments().setAttached(PLANET_SITES, data.copy());
	}

	/** The hub of {@code planet}, or null when it is not placed. */
	public static @Nullable Hub hub(MinecraftServer server, Planets.Planet planet) {
		Data data = server.globalAttachments().getAttached(PLANET_SITES);
		return data != null ? data.hub(planet) : null;
	}

	/** The owner's entry for {@code site} on the server, read without a copy, or null. */
	public static @Nullable Owned owned(MinecraftServer server, UUID owner, PlanetSites.Site site) {
		Data data = server.globalAttachments().getAttached(PLANET_SITES);
		return data != null ? data.owned(owner, site) : null;
	}

	/** The server-wide position of a shared site, read without a copy, or null. */
	public static @Nullable GlobalPos shared(MinecraftServer server, PlanetSites.Site site) {
		Data data = server.globalAttachments().getAttached(PLANET_SITES);
		return data != null ? data.shared(site) : null;
	}

	/** Loads the class so the attachment registers during init. */
	static void init() {
	}

	private PlanetSiteData() {
	}
}
