package com.oldrepublic.holocron;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.AlignmentShift;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.Story;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The Archive hoard choice after the boss (spec 4.8): one decision for all three Archive holocrons,
 * made once per player and stored only in {@link HolocronData#archive}. The owner decides once the
 * boss is resolved; a co-op helper decides after {@link #markPending}.
 */
public final class HolocronArchive {
	/** The hoard dialogue. */
	public static final Identifier DIALOGUE = OldRepublicMod.id("archive_hoard");
	/** {@code Chapter3.boss} value once the boss is resolved. */
	private static final int BOSS_RESOLVED = 2;

	/** The plinth each player opened the hoard at, for the effect's position. Transient. */
	private static final Map<UUID, BlockPos> PLINTHS = new HashMap<>();

	static void init() {
		ServerPlayerEvents.LEAVE.register(player -> PLINTHS.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PLINTHS.clear());
	}

	/**
	 * Opens the hoard dialogue at {@code plinth} for a player who still owes the choice: a helper
	 * marked pending, or the owner whose boss is resolved. Anyone else, or a player who already
	 * chose, gets a line instead.
	 */
	static void open(ServerPlayer p, BlockPos plinth) {
		HolocronData d = HolocronService.data(p);

		if (d.archive != HolocronData.NO_ARCHIVE) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.archive.decided"));
			return;
		}

		if (!d.archivePending && Story.get(p).chapter3.boss() != BOSS_RESOLVED) {
			p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.archive.sealed"));
			return;
		}

		PLINTHS.put(p.getUUID(), plinth.immutable());
		Dialogues.open(p, DIALOGUE, null);
	}

	/** Marks the choice as owed (a co-op helper with pending credit, 8.9). Ignored once decided. */
	static void markPending(ServerPlayer p) {
		HolocronData d = HolocronService.data(p);

		if (d.archive == HolocronData.NO_ARCHIVE && !d.archivePending) {
			d.archivePending = true;
			HolocronService.commit(p, d);
		}
	}

	/**
	 * Applies the choice once: every choice gives XP, a journal entry, the recorded choice, the quest
	 * hook and the effect; then Return, Keep or Unmake gives its own rewards by path (4.8).
	 *
	 * @return false when the player already chose
	 */
	static boolean resolve(ServerPlayer p, ArchiveChoice choice) {
		HolocronData d = HolocronService.data(p);

		if (d.archive != HolocronData.NO_ARCHIVE) {
			return false;
		}

		HolocronBalance b = HolocronBalance.get();
		Path path = Identity.path(p);
		d.archive = choice.ordinal();
		d.archivePending = false;

		if (choice == ArchiveChoice.KEEP) {
			for (HolocronCatalog.Entry entry : HolocronCatalog.archive()) {
				d.attuned.add(entry.id());
				d.choices.put(entry.id(), HolocronChoice.OWN.ordinal());
				d.serials.put(entry.id(), d.serial(entry.id()) + 1);
			}
		}

		HolocronService.commit(p, d);

		Progression.addXp(p, b.archiveXp, "archive");
		String source = "archive." + choice.name().toLowerCase(Locale.ROOT);

		switch (choice) {
			case RETURN -> returnArchive(p, path, source, b);
			case KEEP -> keep(p, path, source, b);
			case UNMAKE -> unmake(p, source, b);
		}

		// After the shifts: a major shift is refused once its source id is already a recorded choice.
		Identity.recordChoice(p, source);

		QuestHooks.onArchiveResolved(p, choice);

		BlockPos plinth = PLINTHS.remove(p.getUUID());
		Vec3 at = plinth == null ? p.position().add(0, 1, 0) : new Vec3(plinth.getX() + 0.5, plinth.getY() + 1.0, plinth.getZ() + 0.5);
		int variant = switch (choice) {
			case RETURN -> HolocronEffects.ARCHIVE_RETURN;
			case KEEP -> HolocronEffects.ARCHIVE_KEEP;
			case UNMAKE -> HolocronEffects.ARCHIVE_UNMAKE;
		};
		EffectEvents.around(p.level(), HolocronEffects.ARCHIVE, at, null, p.getId(), 1f, variant);
		return true;
	}

	private static void returnArchive(ServerPlayer p, Path path, String source, HolocronBalance b) {
		Journal.unlockEntry(p, JournalEntries.ARCHIVE_RETURNED);

		switch (path) {
			case JEDI -> {
				Identity.changeReputation(p, Faction.JEDI_ORDER, b.archiveReturnOwnRep, source, true);
				Identity.changeReputation(p, Faction.REPUBLIC, b.archiveReturnRepublicRep, source, true);
				Identity.addTrust(p, MentorRole.JEDI.id(), b.archiveReturnTrust, "holocron.oldrepublic.trust.archive");
				Identity.shift(p, new AlignmentShift(source, b.archiveReturnLight, AlignmentShift.Kind.MAJOR, "holocron.oldrepublic.shift.archive_return"));
			}
			case SITH -> {
				Identity.changeReputation(p, Faction.SITH_EMPIRE, b.archiveReturnOwnRep, source, true);
				Identity.addTrust(p, MentorRole.SITH.id(), b.archiveReturnTrust, "holocron.oldrepublic.trust.archive");
			}
			default -> {
				Identity.changeReputation(p, Faction.FRONTIER, b.archiveReturnFrontierRep, source, true);
				Identity.changeReputation(p, Faction.REPUBLIC, b.archiveReturnRepublicRep, source, true);
				Identity.shift(p, new AlignmentShift(source, b.archiveReturnIndependentLight, AlignmentShift.Kind.MAJOR,
						"holocron.oldrepublic.shift.archive_scatter"));
			}
		}

		archiveCodex(p);
	}

	private static void keep(ServerPlayer p, Path path, String source, HolocronBalance b) {
		Journal.unlockEntry(p, JournalEntries.ARCHIVE_KEPT);
		HolocronData d = HolocronService.data(p);

		for (HolocronCatalog.Entry entry : HolocronCatalog.archive()) {
			HolocronRewards.give(p, HolocronItems.stack(entry.id(), p.getUUID(), d.serial(entry.id())), null);
			String node = HolocronCatalog.teaching(entry.id(), path, HolocronChoice.OWN);

			if (node != null) {
				HolocronRewards.teach(p, node);
			}
		}

		int shift = switch (path) {
			case JEDI -> b.archiveKeepJediShift;
			case SITH -> 0;
			default -> b.archiveKeepIndependentShift;
		};

		if (shift != 0) {
			Identity.shift(p, new AlignmentShift(source, shift, AlignmentShift.Kind.MAJOR, "holocron.oldrepublic.shift.archive_keep"));
		}

		archiveCodex(p);
		HolocronCarry.refresh(p);
	}

	private static void unmake(ServerPlayer p, String source, HolocronBalance b) {
		Journal.unlockEntry(p, JournalEntries.ARCHIVE_UNMADE);
		Progression.grantResonanceMilestone(p, "archive_unmade", b.archiveUnmakeResonance);
		Identity.shift(p, new AlignmentShift(source, b.archiveUnmakeLight, AlignmentShift.Kind.MAJOR, "holocron.oldrepublic.shift.archive_unmake"));
		Identity.changeReputation(p, Faction.JEDI_ORDER, b.archiveUnmakeRepLoss, source, true);
		Identity.changeReputation(p, Faction.SITH_EMPIRE, b.archiveUnmakeRepLoss, source, true);
		Journal.unlockCodex(p, JournalEntries.CODEX_ARCHIVE_UNMADE);
	}

	/** The three Archive holocrons' codex entries (Return and Keep). */
	private static void archiveCodex(ServerPlayer p) {
		for (HolocronCatalog.Entry entry : HolocronCatalog.archive()) {
			Journal.unlockCodex(p, JournalEntries.holocronCodex(entry.id()));
		}
	}

	private HolocronArchive() {
	}
}
