package com.oldrepublic.client.holocron;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.hud.Banners;
import com.oldrepublic.holocron.HolocronCatalog;
import com.oldrepublic.holocron.HolocronData;
import com.oldrepublic.holocron.HolocronService;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * The "Holocron attuned · gatekeeper" banner and toast (spec 4.4, 11.2), from a client diff of the
 * synced {@code oldrepublic:holocrons} attachment: every id that appears in {@link HolocronData#attuned}
 * gets one banner through {@link Banners} and one toast. The three Archive holocrons, which arrive
 * together when the hoard is kept, share one.
 *
 * <p>What the first sync after joining carries is only recorded, never announced. A player with no
 * holocron data has no attachment at all, so the join window ({@link #JOIN_GRACE_TICKS}) decides
 * when an absent view stops meaning "not synced yet" and starts meaning "nothing attuned". The
 * known set survives respawns and dimension changes (the new player object is briefly unsynced)
 * and is reset on disconnect.
 */
final class HolocronAttunedWatcher {
	/** How long after joining a first view is taken as the existing state rather than news. */
	private static final int JOIN_GRACE_TICKS = 100;
	private static final SystemToast.SystemToastId TOAST = new SystemToast.SystemToastId(5000L);

	private static @Nullable Set<String> known;
	private static int ticksInWorld;

	static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}

		ticksInWorld++;
		HolocronData view = HolocronService.view(mc.player);

		if (view == null) {
			if (known == null && ticksInWorld > JOIN_GRACE_TICKS) {
				// No data was synced on joining: the player has attuned nothing yet.
				known = new HashSet<>();
			}

			return;
		}

		if (known == null) {
			known = new HashSet<>(view.attuned);
			return;
		}

		List<String> fresh = new ArrayList<>();

		for (String id : view.attuned) {
			if (!known.contains(id)) {
				fresh.add(id);
			}
		}

		// Ids an operator reset removed are forgotten, so attuning them again is news again.
		known.retainAll(view.attuned);
		known.addAll(fresh);
		boolean archiveAnnounced = false;

		for (String id : fresh) {
			HolocronCatalog.Entry entry = HolocronCatalog.find(id);

			if (entry == null) {
				continue;
			}

			if (entry.archive()) {
				if (!archiveAnnounced) {
					archiveAnnounced = true;
					announce(Component.translatable("screen.oldrepublic.holocrons.archive"), HolocronCatalog.tint(entry.kind()));
				}
			} else {
				announce(Component.translatable(HolocronCatalog.nameKey(id)), HolocronCatalog.tint(entry.kind()));
			}
		}
	}

	private static void announce(Component name, int accent) {
		Banners.show(Component.translatable("holocron.oldrepublic.banner.attuned"), name, accent);
		SystemToast.add(Minecraft.getInstance().gui.toastManager(), TOAST, Component.translatable("holocron.oldrepublic.toast.attuned"), name);
	}

	/** Forgets everything (disconnect): the next join records its state silently again. */
	static void reset() {
		known = null;
		ticksInWorld = 0;
	}

	private HolocronAttunedWatcher() {
	}
}
