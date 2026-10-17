package com.oldrepublic.holocron;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.world.site.SiteKind;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The holocron feature's dialogue conditions and actions (spec 6.8) and its {@link Dialogues#ON_CLOSE}
 * listener. The dialogue engine has no argument mechanism for these ids, so every argument of an
 * action is registered as its own full id ({@code holocron:return:jedi} and so on).
 */
public final class HolocronDialogue {
	/** Lang key prefix of the fail reasons. */
	public static final String REQ = "holocron.oldrepublic.req.";
	/** Story flag set (by the story) once Vasch's recorded message has played. */
	public static final String VASCH_FLAG = "c3.vasch_message";
	/** Leads needed before Vasch's message plays (6.4). */
	public static final int VASCH_LEADS = 2;

	static void init() {
		DialogueConditions.register("holocron:carrying", HolocronDialogue::carrying);
		DialogueConditions.register("holocron:attuned", (p, speaker, arg) -> HolocronService.hasAttuned(p, arg.trim()) ? null : REQ + "attuned");
		DialogueConditions.register("shards", (p, speaker, arg) -> {
			int n = Integer.parseInt(arg.trim());
			return HolocronRewards.shards(p) >= n ? null : REQ + "shards|" + n;
		});
		DialogueConditions.register("holocron:vasch_due", (p, speaker, arg) -> vaschDue(p) ? null : REQ + "vasch");
		DialogueConditions.register("holocron:at_site", HolocronDialogue::atSite);

		for (String receiver : new String[] {HolocronReturns.JEDI, HolocronReturns.SITH, HolocronReturns.REPUBLIC, HolocronReturns.IMPERIAL}) {
			DialogueActions.register("holocron:return:" + receiver, (p, speaker) -> HolocronService.returnTo(p, receiver));
		}

		for (HolocronChoice choice : HolocronChoice.values()) {
			DialogueActions.register("holocron:choose:" + choice.name().toLowerCase(Locale.ROOT), (p, speaker) -> {
				if (!HolocronAttunement.confirm(p, choice)) {
					p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.attune.refused"));
				}
			});
		}

		for (ArchiveChoice choice : ArchiveChoice.values()) {
			DialogueActions.register("archive:choose:" + choice.name().toLowerCase(Locale.ROOT), (p, speaker) -> HolocronService.resolveArchive(p, choice));
		}

		for (String army : new String[] {HolocronReturns.REPUBLIC, HolocronReturns.IMPERIAL}) {
			DialogueActions.register("shards:exchange:" + army, (p, speaker) -> HolocronReturns.exchangeShards(p, army));
			DialogueActions.register("shards:amends:" + army, (p, speaker) -> HolocronReturns.amends(p, army));
		}

		Dialogues.ON_CLOSE.register((player, dialogue, chose) -> HolocronAttunement.onDialogueClosed(player, dialogue));
	}

	/**
	 * {@code holocron:carrying} ({@code jedi}, {@code sith}, {@code wayfarer} or {@code any}): the player
	 * carries their own current copy of an unreturned world holocron of that kind.
	 */
	private static @Nullable String carrying(ServerPlayer p, @Nullable Entity speaker, String arg) {
		String kindId = arg.trim().toLowerCase(Locale.ROOT);
		HolocronKind kind = "any".equals(kindId) ? null : HolocronKind.valueOf(kindId.toUpperCase(Locale.ROOT));
		HolocronData d = HolocronService.data(p);

		for (String id : HolocronRewards.heldCurrent(p, d)) {
			HolocronCatalog.Entry entry = HolocronCatalog.get(id);

			if (!entry.archive() && (kind == null || entry.kind() == kind)) {
				return null;
			}
		}

		return REQ + "carrying";
	}

	/** {@code holocron:at_site} (a site kind id, or {@code !id}): the kind of site of the open attunement. */
	private static @Nullable String atSite(ServerPlayer p, @Nullable Entity speaker, String arg) {
		String value = arg.trim();
		boolean negate = value.startsWith("!");
		SiteKind wanted = SiteKind.byId(negate ? value.substring(1) : value);

		if (wanted == null) {
			throw new IllegalArgumentException("unknown site kind " + value);
		}

		boolean at = HolocronAttunement.pendingSiteKind(p) == wanted;
		return at != negate ? null : REQ + "at_site";
	}

	/** Vasch's recorded message is due: at least two leads and the message not yet played (6.4). Read only. */
	public static boolean vaschDue(ServerPlayer p) {
		StoryData story = Story.get(p);
		return story.chapter3.leads().size() >= VASCH_LEADS && !story.hasFlag(VASCH_FLAG);
	}

	private HolocronDialogue() {
	}
}
