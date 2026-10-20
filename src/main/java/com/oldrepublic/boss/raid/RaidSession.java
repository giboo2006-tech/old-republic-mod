package com.oldrepublic.boss.raid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.oldrepublic.planet.Clocks;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.GameType;

/**
 * The shared raid session (spec MILESTONE_5.md 12.4): a fight with many owners. It keeps the
 * participant set (everyone playing inside the undercroft when the Hymn begins, and anyone who comes
 * in during the first {@code joinTicks}, up to {@code maxPlayers}), counts each one's time away (a
 * participant alive outside, in another dimension or offline for {@code participantLeaveTicks} is no
 * longer one; a dead one stays listed and may come back in), notices the wipe (no participant alive
 * inside for {@code wipeResetTicks}), and keeps the boss bar and the {@code boss_music} with the
 * list, re-syncing the bar once a second (the combat digest: {@code ServerBossEvent} has no range
 * logic). Each participant's weekly lockout is in their {@link RaidData}. Nothing here is saved: a
 * restart ends the raid (the M3 rule).
 */
public final class RaidSession {
	/** The boss bar's list is checked against the participants once a second. */
	private static final int BAR_SYNC_TICKS = 20;

	/** What one tick changed. */
	public record Tick(boolean joined, boolean left, boolean wiped) {
	}

	private final ServerLevel level;
	private final UndercroftLayout layout;
	private final String musicId;
	/** Participant id to the ticks it has been away. */
	private final Map<UUID, Integer> participants = new LinkedHashMap<>();
	private final ServerBossEvent bar;
	private int age;
	private int emptyTicks;
	private boolean ended;

	public RaidSession(ServerLevel level, UndercroftLayout layout, Component name, BossEvent.BossBarColor color, BossEvent.BossBarOverlay overlay,
			String musicId) {
		this.level = level;
		this.layout = layout;
		this.musicId = musicId;
		this.bar = new ServerBossEvent(UUID.randomUUID(), name, color, overlay);
	}

	public ServerLevel level() {
		return level;
	}

	public UndercroftLayout layout() {
		return layout;
	}

	/** Ticks since the Hymn began. */
	public int age() {
		return age;
	}

	public boolean isParticipant(UUID id) {
		return participants.containsKey(id);
	}

	/** The listed participants' ids, in joining order. */
	public List<UUID> participants() {
		return List.copyOf(participants.keySet());
	}

	public int count() {
		return participants.size();
	}

	/** Whether newcomers may still join (the first {@code joinTicks}, with room left). */
	public boolean joinOpen() {
		return RaidRules.mayJoin(age, participants.size(), RaidBalance.get());
	}

	/** Whether {@code player} plays inside the undercroft now (alive, not spectating, in its box). */
	public boolean playsInside(ServerPlayer player) {
		return player.level() == level && player.isAlive() && !player.isSpectator() && layout.inside(player.position());
	}

	/** The listed participants inside now, alive and playing. */
	public List<ServerPlayer> inside() {
		List<ServerPlayer> out = new ArrayList<>();

		for (UUID id : participants.keySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null && playsInside(player)) {
				out.add(player);
			}
		}

		return out;
	}

	/** The listed participants online now, wherever they are. */
	public List<ServerPlayer> online() {
		List<ServerPlayer> out = new ArrayList<>();

		for (UUID id : participants.keySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null) {
				out.add(player);
			}
		}

		return out;
	}

	/**
	 * Every player playing inside the undercroft (listed or not; creative and spectators aside, by game
	 * mode, since a game test's mock player always answers {@code isCreative()} true): the Hymn's
	 * hazards touch them all.
	 */
	public List<ServerPlayer> everyoneInside() {
		List<ServerPlayer> out = new ArrayList<>();

		for (ServerPlayer player : level.players()) {
			if (playsInside(player) && player.gameMode() != GameType.CREATIVE) {
				out.add(player);
			}
		}

		return out;
	}

	/** The bar's fill (0 to 1). */
	public void setProgress(float progress) {
		bar.setProgress(Math.clamp(progress, 0f, 1f));
	}

	public void setName(Component name) {
		bar.setName(name);
	}

	/**
	 * One tick of the Hymn: newcomers join while the window is open, away and empty time is counted,
	 * the leavers go, and the bar is re-synced once a second.
	 */
	public Tick tick() {
		if (ended) {
			return new Tick(false, false, false);
		}

		age++;
		RaidBalance b = RaidBalance.get();
		boolean joined = admit();
		boolean left = false;
		List<UUID> gone = new ArrayList<>();

		for (Map.Entry<UUID, Integer> entry : participants.entrySet()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
			// A dead participant waits on the death screen without counting; alive, the clock runs until they are back inside.
			boolean stays = player != null && player.level() == level && (!player.isAlive() || playsInside(player));
			int away = stays ? 0 : entry.getValue() + 1;
			entry.setValue(away);

			if (RaidRules.leftRaid(away, b)) {
				gone.add(entry.getKey());
			}
		}

		for (UUID id : gone) {
			participants.remove(id);
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);

			if (player != null) {
				bar.removePlayer(player);
				BossMusicPayload.send(player, musicId, BossMusicPayload.STOP);
			}

			left = true;
		}

		if (age % BAR_SYNC_TICKS == 0) {
			syncBar();
		}

		emptyTicks = inside().isEmpty() ? emptyTicks + 1 : 0;
		return new Tick(joined, left, RaidRules.wiped(emptyTicks, b));
	}

	/** While the window is open, everyone playing inside joins (at most {@code maxPlayers}). Returns whether anyone did. */
	public boolean admit() {
		boolean joined = false;

		for (ServerPlayer player : level.players()) {
			if (!joinOpen()) {
				break;
			}

			if (!participants.containsKey(player.getUUID()) && playsInside(player)) {
				add(player);
				joined = true;
			}
		}

		return joined;
	}

	/** Lists {@code player} (the tests, the window's joins): the bar and the music. */
	public void add(ServerPlayer player) {
		if (participants.putIfAbsent(player.getUUID(), 0) == null) {
			bar.addPlayer(player);
			BossMusicPayload.send(player, musicId, 1);
		}
	}

	/** Re-adds the online participants missing from the bar (a relog makes a new player object) and drops anyone else. */
	private void syncBar() {
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (!participants.containsKey(player.getUUID())) {
				bar.removePlayer(player);
			}
		}

		for (ServerPlayer player : online()) {
			if (!bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
				BossMusicPayload.send(player, musicId, 1);
			}
		}
	}

	/** The raid is over (a clear, a wipe, a reset): the bar goes and the music stops. Idempotent. */
	public void end() {
		if (ended) {
			return;
		}

		ended = true;

		for (ServerPlayer player : online()) {
			BossMusicPayload.send(player, musicId, BossMusicPayload.STOP);
		}

		bar.removeAllPlayers();
	}

	public boolean isEnded() {
		return ended;
	}

	// ---------------------------------------------------------------- the weekly lockout

	/** The raid week now ({@code Clocks.day / weekDays}). */
	public static long week(MinecraftServer server) {
		return RaidRules.week(Clocks.day(server), RaidBalance.get());
	}

	/** Whether {@code player}'s rewarded clear of this week is already taken. */
	public static boolean clearedThisWeek(ServerPlayer player) {
		return RaidData.of(player).clearedIn(week(player.level().getServer()));
	}
}
