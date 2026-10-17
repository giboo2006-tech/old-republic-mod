package com.oldrepublic.mentor;

import java.util.Locale;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Path;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;

import net.minecraft.resources.Identifier;

/** The two mentors of Chapter Two and everything that differs between them. */
public enum MentorRole {
	/** Master Ilyra Saelen, Jedi Watchwoman. */
	JEDI(Path.JEDI, Faction.JEDI_ORDER, Faction.SITH_EMPIRE, BladeColor.GREEN, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, 0xFFE8D9A8),
	/** Overseer Teyvar Sorn. */
	SITH(Path.SITH, Faction.SITH_EMPIRE, Faction.JEDI_ORDER, BladeColor.RED, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, 0xFFB0262A);

	private final Path path;
	private final Faction faction;
	private final Faction rivalFaction;
	private final BladeColor blade;
	private final Emitter emitter;
	private final Grip grip;
	private final Pommel pommel;
	private final int accent;

	MentorRole(Path path, Faction faction, Faction rivalFaction, BladeColor blade, Emitter emitter, Grip grip, Pommel pommel, int accent) {
		this.path = path;
		this.faction = faction;
		this.rivalFaction = rivalFaction;
		this.blade = blade;
		this.emitter = emitter;
		this.grip = grip;
		this.pommel = pommel;
		this.accent = accent;
	}

	/** Trust key and dialogue suffix: {@code jedi} or {@code sith}. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public Path path() {
		return path;
	}

	public Faction faction() {
		return faction;
	}

	/** The faction that takes the reputation hit when this mentor is chosen. */
	public Faction rivalFaction() {
		return rivalFaction;
	}

	public BladeColor blade() {
		return blade;
	}

	public Emitter emitter() {
		return emitter;
	}

	public Grip grip() {
		return grip;
	}

	public Pommel pommel() {
		return pommel;
	}

	/** ARGB accent (pale gold for the Jedi, deep red for the Sith) for portraits and banners. */
	public int accent() {
		return accent;
	}

	/** GUI sprite of the mentor's portrait ({@code textures/gui/sprites/portrait/<role>_mentor.png}). */
	public Identifier portrait() {
		return OldRepublicMod.id("portrait/" + id() + "_mentor");
	}

	/** The Crossing dialogue ({@code crossing_jedi} / {@code crossing_sith}). */
	public Identifier crossingDialogue() {
		return OldRepublicMod.id("crossing_" + id());
	}

	/** The mentor's dialogue once the player follows them ({@code mentor_jedi} / {@code mentor_sith}). */
	public Identifier mentorDialogue() {
		return OldRepublicMod.id("mentor_" + id());
	}

	public static MentorRole of(Path path) {
		return path == Path.SITH ? SITH : JEDI;
	}

	public static MentorRole byId(String id) {
		return "sith".equals(id) ? SITH : JEDI;
	}

	/** The mentor's lang key for a line, {@code npc.oldrepublic.<role>.<line>}. */
	public String line(String line) {
		return "npc.oldrepublic." + id() + "." + line;
	}
}
