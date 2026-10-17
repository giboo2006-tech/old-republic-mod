package com.oldrepublic.holocron;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.identity.Path;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.skill.SkillIds;

import net.minecraft.resources.Identifier;

/**
 * The fifteen holocrons of spec 4.2 in {@link JournalEntries#HOLOCRON_IDS} order: the twelve world
 * holocrons (four per kind, each kind in its fixed order) and the three Archive holocrons from the
 * hoard, with their kind, their gatekeeper and what each teaches.
 */
public final class HolocronCatalog {
	/**
	 * One holocron.
	 *
	 * @param order   1 to 4 inside its kind for a world holocron (the order pedestals give them); 0 for an Archive one
	 * @param archive true for the three hoard holocrons, which never count toward the collection
	 */
	public record Entry(String id, HolocronKind kind, int order, boolean archive) {
	}

	public static final String WAYFARERS_COMPASS = "wayfarers_compass";
	public static final String WAYFARER_PELL = "wayfarer_pell";
	public static final String ARCHIVE_WATCH = "archive_watch";
	public static final String ARCHIVE_CODEX = "archive_codex";
	public static final String ARCHIVE_UNBOUND = "archive_unbound";
	/** World holocrons per kind. */
	public static final int PER_KIND = 4;
	/** World holocrons in all. */
	public static final int WORLD_COUNT = 12;

	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

	static {
		int[] order = new int[HolocronKind.values().length];

		for (String id : JournalEntries.HOLOCRON_IDS) {
			boolean archive = id.startsWith("archive_");
			HolocronKind kind = switch (id) {
				case ARCHIVE_WATCH -> HolocronKind.JEDI;
				case ARCHIVE_CODEX -> HolocronKind.SITH;
				case ARCHIVE_UNBOUND -> HolocronKind.WAYFARER;
				default -> id.startsWith("jedi_") ? HolocronKind.JEDI : id.startsWith("sith_") ? HolocronKind.SITH : HolocronKind.WAYFARER;
			};
			ENTRIES.put(id, new Entry(id, kind, archive ? 0 : ++order[kind.ordinal()], archive));
		}
	}

	/** Every holocron, in 4.2 order (Wayfarer, Jedi, Sith rows, then the Archive). */
	public static List<Entry> all() {
		return List.copyOf(ENTRIES.values());
	}

	/** The holocron {@code id}; throws for an unknown id. */
	public static Entry get(String id) {
		Entry entry = ENTRIES.get(id);

		if (entry == null) {
			throw new IllegalArgumentException("Unknown holocron " + id);
		}

		return entry;
	}

	/** The holocron {@code id}, or null for an unknown id. */
	public static @Nullable Entry find(String id) {
		return ENTRIES.get(id);
	}

	/** The kind of {@code id}, or null for an unknown id. */
	public static @Nullable HolocronKind kindOf(String id) {
		Entry entry = ENTRIES.get(id);
		return entry == null ? null : entry.kind();
	}

	public static boolean isWorld(String id) {
		Entry entry = ENTRIES.get(id);
		return entry != null && !entry.archive();
	}

	/** The four world holocrons of {@code kind}, in the order pedestals give them. */
	public static List<Entry> world(HolocronKind kind) {
		return ENTRIES.values().stream().filter(e -> !e.archive() && e.kind() == kind).toList();
	}

	/** The three Archive holocrons. */
	public static List<Entry> archive() {
		return ENTRIES.values().stream().filter(Entry::archive).toList();
	}

	/** World holocrons in the return order (spec 4.6): the Jedi row, then the Sith row, then the Wayfarer row. */
	public static List<Entry> returnOrder() {
		List<Entry> out = new ArrayList<>();
		out.addAll(world(HolocronKind.JEDI));
		out.addAll(world(HolocronKind.SITH));
		out.addAll(world(HolocronKind.WAYFARER));
		return out;
	}

	/** {@code HOLOCRON_IDS.indexOf(id)}, 0 to 14: the gatekeeper index of effects and portraits. */
	public static int gatekeeperIndex(String id) {
		return JournalEntries.HOLOCRON_IDS.indexOf(id);
	}

	/** The id at a gatekeeper index, or null when out of range. */
	public static @Nullable String byGatekeeperIndex(int index) {
		return index >= 0 && index < JournalEntries.HOLOCRON_IDS.size() ? JournalEntries.HOLOCRON_IDS.get(index) : null;
	}

	/** Lang key of the gatekeeper's name (an Archive holocron's is its own title). */
	public static String nameKey(String id) {
		return "holocron.oldrepublic.gatekeeper." + id;
	}

	/** The speakerless dialogue {@code oldrepublic:holocron_<id>}. */
	public static Identifier dialogue(String id) {
		return OldRepublicMod.id("holocron_" + id);
	}

	/**
	 * The skill node {@code id} teaches a reader on {@code path} who chose {@code choice}, or null for
	 * lore only (4.2). Seal and Break never teach. The Archive holocrons teach only when kept, which
	 * {@link HolocronArchive} handles; here they answer their node for any choice that teaches.
	 */
	public static @Nullable String teaching(String id, Path path, HolocronChoice choice) {
		if (choice == HolocronChoice.SEAL || choice == HolocronChoice.BREAK) {
			return null;
		}

		return switch (id) {
			case WAYFARERS_COMPASS -> path == Path.INDEPENDENT ? SkillIds.FORCE_SENSE : SkillIds.RELIC_LORE;
			case WAYFARER_PELL -> path == Path.INDEPENDENT ? SkillIds.FORCE_LIFT : SkillIds.TRAILWISE;
			case "wayfarer_osk" -> SkillIds.QUIET_STEP;
			case "jedi_morrane" -> SkillIds.WATCHERS_INSIGHT;
			case "jedi_sarrow" -> SkillIds.MENDING_BREATH;
			case "sith_zarvos" -> SkillIds.TOMB_HUNGER;
			case "sith_ysolde" -> SkillIds.CRACKLING_GRASP;
			case ARCHIVE_WATCH -> SkillIds.ARCHIVISTS_WARD;
			case ARCHIVE_CODEX -> SkillIds.ARCHIVISTS_HUNGER;
			case ARCHIVE_UNBOUND -> SkillIds.ARCHIVISTS_ROAD;
			default -> null;
		};
	}

	/** The shelf tint of a kind: warm gold, rust crimson or brass (ARGB). */
	public static int tint(HolocronKind kind) {
		return switch (kind) {
			case JEDI -> 0xFFF2D27A;
			case SITH -> 0xFFC2483A;
			case WAYFARER -> 0xFFC9A05A;
		};
	}

	private HolocronCatalog() {
	}
}
