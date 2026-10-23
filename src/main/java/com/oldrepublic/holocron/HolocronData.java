package com.oldrepublic.holocron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;

/**
 * One player's holocrons (spec 4, 12.1): which are attuned and how, which were returned, the
 * pedestals that gave them, the current issue number of each, the claimed echoes, the progression
 * mask, the Archive choice and the last restore day. Lives in the persistent, copy-on-death
 * {@link HolocronAttachments#HOLOCRONS} attachment, synced to its owner only.
 *
 * <p>Mutable on the server; after changing it call {@link HolocronService#commit} and read it again
 * with {@link HolocronService#data}. Clients only read the synced view.
 */
public final class HolocronData {
	/** Cap of the id sets and maps ({@code attuned}, {@code choices}, {@code returned}, {@code serials}, {@code attuned_at}). */
	public static final int MAX_IDS = 16;
	/** Cap of the claimed echo pedestals; the oldest is dropped beyond it. */
	public static final int MAX_ECHOES = 64;
	/** {@link #archive} value before the hoard choice. */
	public static final int NO_ARCHIVE = -1;
	/** {@link #restoreDay} value for "never restored". */
	public static final long NEVER = -1L;

	/** Milestone bits: Resonance {@code holocrons_3}, +1 SP at 6, {@code holocrons_9}, the title at 12. */
	public static final int BIT_3 = 1;
	public static final int BIT_6 = 1 << 1;
	public static final int BIT_9 = 1 << 2;
	public static final int BIT_12 = 1 << 3;
	/** First of the six skill point drip bits (bits 4 to 9: the 2nd, 4th, ..., 12th world holocron). */
	public static final int DRIP_SHIFT = 4;
	public static final int DRIP_BITS = 6;

	/**
	 * The pedestal that gave one world holocron.
	 *
	 * @param dim  the dimension id
	 * @param site the site key (a {@code SitePiece} key, or {@code <dim>|pedestal|x|y|z} outside any site)
	 */
	public record AttunedAt(String dim, BlockPos pos, String site, String id) {
		public static final Codec<AttunedAt> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dim").forGetter(AttunedAt::dim),
				BlockPos.CODEC.fieldOf("pos").forGetter(AttunedAt::pos),
				Codec.STRING.fieldOf("site").forGetter(AttunedAt::site),
				Codec.STRING.fieldOf("id").forGetter(AttunedAt::id)
		).apply(i, AttunedAt::new));

		public AttunedAt {
			pos = pos.immutable();
		}
	}

	/** A pedestal whose echo the player claimed. */
	public record PedestalRef(String dim, BlockPos pos) {
		public static final Codec<PedestalRef> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dim").forGetter(PedestalRef::dim),
				BlockPos.CODEC.fieldOf("pos").forGetter(PedestalRef::pos)
		).apply(i, PedestalRef::new));

		public PedestalRef {
			pos = pos.immutable();
		}
	}

	private static final Codec<Set<String>> ID_SET = Codec.STRING.listOf().xmap(
			list -> new LinkedHashSet<>(list.stream().limit(MAX_IDS).toList()), List::copyOf);
	private static final Codec<Map<String, Integer>> ID_MAP = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(
			map -> limit(map), map -> map);

	public static final Codec<HolocronData> CODEC = RecordCodecBuilder.create(i -> i.group(
			ID_SET.optionalFieldOf("attuned", Set.of()).forGetter(d -> d.attuned),
			ID_MAP.optionalFieldOf("choices", Map.of()).forGetter(d -> d.choices),
			ID_SET.optionalFieldOf("returned", Set.of()).forGetter(d -> d.returned),
			AttunedAt.CODEC.listOf().optionalFieldOf("attuned_at", List.of()).forGetter(d -> d.attunedAt),
			ID_MAP.optionalFieldOf("serials", Map.of()).forGetter(d -> d.serials),
			PedestalRef.CODEC.listOf().optionalFieldOf("echoes", List.of()).forGetter(d -> d.echoes),
			Codec.INT.optionalFieldOf("milestones", 0).forGetter(d -> d.milestones),
			Codec.INT.optionalFieldOf("archive", NO_ARCHIVE).forGetter(d -> d.archive),
			Codec.BOOL.optionalFieldOf("archive_pending", false).forGetter(d -> d.archivePending),
			Codec.LONG.optionalFieldOf("restore_day", NEVER).forGetter(d -> d.restoreDay)
	).apply(i, HolocronData::new));

	/** World holocrons attuned, and the Archive ids once kept. */
	public final Set<String> attuned = new LinkedHashSet<>();
	/** Holocron id to its {@link HolocronChoice} ordinal. */
	public final Map<String, Integer> choices = new HashMap<>();
	/** World holocrons handed over to a mentor or officer (never restorable). */
	public final Set<String> returned = new LinkedHashSet<>();
	/** The pedestal of each world holocron, in attunement order. */
	public final List<AttunedAt> attunedAt = new ArrayList<>();
	/** Holocron id to the current issue number of its item (first issue 1); older copies are stale. */
	public final Map<String, Integer> serials = new HashMap<>();
	/** Pedestals whose echo was claimed, oldest first. */
	public final List<PedestalRef> echoes = new ArrayList<>();
	/** Progression mask ({@link #BIT_3} ... {@link #BIT_12}, drip bits from {@link #DRIP_SHIFT}). */
	public int milestones;
	/** {@link ArchiveChoice} ordinal, or {@link #NO_ARCHIVE}. */
	public int archive = NO_ARCHIVE;
	/** The hoard choice is owed (owner after the boss, or a co-op helper with pending credit). */
	public boolean archivePending;
	/** Day index of the last restore, or {@link #NEVER}. */
	public long restoreDay = NEVER;

	/** Frozen copy for syncing (see {@link #syncView()}); never copied, persisted or mutated. */
	private volatile @Nullable HolocronData syncView;

	public HolocronData() {
	}

	private HolocronData(Set<String> attuned, Map<String, Integer> choices, Set<String> returned, List<AttunedAt> attunedAt,
			Map<String, Integer> serials, List<PedestalRef> echoes, int milestones, int archive, boolean archivePending, long restoreDay) {
		this.attuned.addAll(attuned);
		this.choices.putAll(choices);
		this.returned.addAll(returned);
		this.attunedAt.addAll(attunedAt.subList(0, Math.min(attunedAt.size(), MAX_IDS)));
		this.serials.putAll(serials);
		this.echoes.addAll(echoes.subList(Math.max(0, echoes.size() - MAX_ECHOES), echoes.size()));
		this.milestones = milestones;
		this.archive = archive;
		this.archivePending = archivePending;
		this.restoreDay = restoreDay;
	}

	private static Map<String, Integer> limit(Map<String, Integer> map) {
		Map<String, Integer> out = new HashMap<>();
		map.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(MAX_IDS).forEach(e -> out.put(e.getKey(), e.getValue()));
		return out;
	}

	public HolocronData copy() {
		return new HolocronData(attuned, choices, returned, attunedAt, serials, echoes, milestones, archive, archivePending, restoreDay);
	}

	/**
	 * Takes the frozen copy that syncing sends ({@link #syncView()}) and returns this. Server
	 * thread only; {@link HolocronService#commit}, the attachment initializer and loading call it.
	 */
	public HolocronData seal() {
		this.syncView = copy();
		return this;
	}

	/**
	 * What the network sends for this object: the copy taken by the last {@link #seal()}, which
	 * nothing ever mutates. Fabric encodes attachment syncs later, on the network thread, so it must
	 * never iterate the live collections the server thread keeps changing in place.
	 */
	public HolocronData syncView() {
		HolocronData view = syncView;
		return view != null ? view : this;
	}

	/** Whether {@link #seal()} has run on this object (every attached value is sealed). */
	public boolean isSealed() {
		return syncView != null;
	}

	// ---------------------------------------------------------------- reads

	public boolean isAttuned(String id) {
		return attuned.contains(id);
	}

	/** The choice made for {@code id}, or null when it is not attuned. */
	public @Nullable HolocronChoice choice(String id) {
		Integer ordinal = choices.get(id);
		HolocronChoice[] values = HolocronChoice.values();
		return ordinal == null || ordinal < 0 || ordinal >= values.length ? null : values[ordinal];
	}

	/** The current issue number of {@code id}'s item, 0 when none was issued. */
	public int serial(String id) {
		return serials.getOrDefault(id, 0);
	}

	/** The Archive choice, or null before the hoard was resolved. */
	public @Nullable ArchiveChoice archiveChoice() {
		ArchiveChoice[] values = ArchiveChoice.values();
		return archive < 0 || archive >= values.length ? null : values[archive];
	}

	/** Whether this pedestal already gave a world holocron. */
	public boolean gaveAt(String dim, BlockPos pos) {
		return attunedAt.stream().anyMatch(a -> a.dim().equals(dim) && a.pos().equals(pos));
	}

	/** Whether a pedestal of {@code kind} in the site {@code siteKey} already gave a world holocron. */
	public boolean usedSite(String siteKey, @Nullable HolocronKind kind) {
		return attunedAt.stream().anyMatch(a -> a.site().equals(siteKey) && (kind == null || HolocronCatalog.kindOf(a.id()) == kind));
	}

	public boolean echoClaimed(String dim, BlockPos pos) {
		return echoes.stream().anyMatch(e -> e.dim().equals(dim) && e.pos().equals(pos));
	}
}
