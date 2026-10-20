package com.oldrepublic.enemy.hymn;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Where the five Milestone 5 foes report a player's kill (spec 12.1): the cartel enforcer, the Clan
 * Hask warrior, the Hymnsworn, the sand scour and the stormback each call {@link #report} once, from
 * their death. The report goes to {@link QuestHooks#onFoeKilled} (W4 counts contract hunts, opens
 * codex pages and applies the kill reputation), then to any {@link #listen listener}.
 *
 * <p>Listeners exist for the game tests ({@code FoesM5GameTests.newFoesReportKills}), which check
 * every foe's kind, faction and hostile snapshot without reading a story. Nothing in the mod listens.
 */
public final class FoeKills {
	/**
	 * One reported kill.
	 *
	 * @param killer     the player's UUID
	 * @param victim     the foe's UUID
	 * @param kind       the foe's entity id ({@code CartelEntities.ID} and the others)
	 * @param faction    the cartel's or the clan's faction, null for the three unaligned foes
	 * @param wasHostile {@code Attitudes.hostileSnapshot} at the killing blow
	 */
	public record Report(UUID killer, UUID victim, String kind, @Nullable Faction faction, boolean wasHostile) {
	}

	private static final List<Consumer<Report>> LISTENERS = new CopyOnWriteArrayList<>();

	/** Reports {@code killer}'s kill of {@code victim} to the quest hooks, then to the listeners. */
	public static void report(ServerPlayer killer, LivingEntity victim, String kind, @Nullable Faction faction, boolean wasHostile) {
		QuestHooks.onFoeKilled(killer, victim, kind, faction, wasHostile);

		if (LISTENERS.isEmpty()) {
			return;
		}

		Report report = new Report(killer.getUUID(), victim.getUUID(), kind, faction, wasHostile);

		for (Consumer<Report> listener : LISTENERS) {
			listener.accept(report);
		}
	}

	/** Adds {@code listener} (game tests); run the returned action to remove it again. */
	public static Runnable listen(Consumer<Report> listener) {
		LISTENERS.add(listener);
		return () -> LISTENERS.remove(listener);
	}

	private FoeKills() {
	}
}
