package com.oldrepublic.boss.cantor;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.raid.RaidBalance;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The song-engine of the First Cantor's last phase, the Long Hymn (spec MILESTONE_5.md 12.3).
 *
 * <p>The three floor rings ({@code RING_1..3}) sing in turn: each trembles for {@code ringTell}
 * ({@code RING_TELL}), then sings for {@code ringTicks} ({@code RING}), {@code ringDamage} a second to
 * everyone on it; only one sings at a time, so the others are always safe.
 *
 * <p>The three silencing points ({@code SILENCE_1..3}) open with the phase. A participant crouching
 * within {@code silenceReach} of an open point channels it for {@code silenceTicks} (the verse stone's
 * rule, 3.3.2: damage breaks the channel); a silenced point opens a {@code windowTicks} window in which
 * he takes {@code windowMultiplier} damage (a new silence restarts the window, it never stacks). Once
 * all three are silenced and the window has closed, they open again.
 */
public final class CantorEngine {
	/** A channel's progress is shown this often. */
	private static final int PROGRESS_TICKS = 10;
	/** The rings reach this far over the floor (blocks). */
	private static final double RING_REACH_UP = 2.5;

	/** One silencing point: open or spent, and who channels it. */
	private static final class Point {
		final BlockPos pos;
		boolean spent;
		@Nullable UUID channeller;
		int progress;

		Point(BlockPos pos) {
			this.pos = pos;
		}

		Vec3 centre() {
			return Vec3.atBottomCenterOf(pos);
		}
	}

	private final CantorSession session;
	private Point[] points = new Point[0];
	private int ring;
	private int ringTell = -1;
	private int ringSing = -1;
	private int ringTicks;
	private int window;
	private boolean running;

	CantorEngine(CantorSession session) {
		this.session = session;
	}

	/** Whether the engine runs (the last phase). */
	public boolean isRunning() {
		return running;
	}

	/** Whether a silenced point's window is open. */
	public boolean windowOpen() {
		return window > 0;
	}

	/** Ticks left on the open window. */
	public int window() {
		return window;
	}

	/** The ring that sings now (0 to 2), or -1. */
	public int singingRing() {
		return running && ringSing >= 0 ? ring : -1;
	}

	/** The ring whose stones tremble now (0 to 2), or -1. */
	public int tellingRing() {
		return running && ringTell >= 0 ? ring : -1;
	}

	/** Whether point {@code index} is silenced (spent until all three are). */
	public boolean silenced(int index) {
		return index >= 0 && index < points.length && points[index].spent;
	}

	/** Point {@code index}'s channel progress in ticks. */
	public int progress(int index) {
		return index >= 0 && index < points.length ? points[index].progress : 0;
	}

	/** Whether {@code player} channels a point now. */
	public boolean isChannelling(UUID player) {
		for (Point point : points) {
			if (player.equals(point.channeller)) {
				return true;
			}
		}

		return false;
	}

	/** The engine wakes: the first ring trembles and the three points open. */
	void start(RaidBalance b) {
		stop();
		running = true;
		List<BlockPos> spots = session.layout().silence();
		points = new Point[spots.size()];

		for (int i = 0; i < points.length; i++) {
			points[i] = new Point(spots.get(i));
		}

		ring = 0;
		tellRing(b);
		openPoints();
	}

	/** The engine stops (the clear, a wipe): the rings fall quiet and the window closes. */
	void stop() {
		running = false;
		ringTell = -1;
		ringSing = -1;
		window = 0;
		points = new Point[0];
	}

	void tick(FirstCantor c, RaidBalance b) {
		if (!running) {
			return;
		}

		tickRing(c, b);
		tickPoints(c, b);

		if (window > 0 && --window == 0) {
			reopenWhenSpent();
		}
	}

	// ---------------------------------------------------------------- the rings

	private void tellRing(RaidBalance b) {
		List<BlockPos> rings = session.layout().rings();

		if (rings.isEmpty()) {
			return;
		}

		ring = Math.floorMod(ring, rings.size());
		ringTell = Math.max(1, b.ringTell);
		ringSing = -1;
		Vec3 centre = Vec3.atBottomCenterOf(rings.get(ring));
		FirstCantor c = session.cantor();
		session.event(centre, null, c != null ? c.getId() : -1, b.ringRadius, CantorEffects.RING_TELL);
		session.play(centre, ModSounds.BOSS_CANTOR_RING_TELL, 1.4f, 1f);
	}

	private void tickRing(FirstCantor c, RaidBalance b) {
		List<BlockPos> rings = session.layout().rings();

		if (rings.isEmpty()) {
			return;
		}

		Vec3 centre = Vec3.atBottomCenterOf(rings.get(Math.floorMod(ring, rings.size())));

		if (ringTell >= 0) {
			if (--ringTell < 0) {
				ringTicks = Math.max(1, b.ringTicks);
				ringSing = 0;
				session.event(centre, new Vec3(ringTicks, 0, 0), c.getId(), b.ringRadius, CantorEffects.RING);
			}

			return;
		}

		if (ringSing < 0) {
			return;
		}

		ringSing++;

		if (ringSing % 20 == 0) {
			for (ServerPlayer player : session.everyoneInside()) {
				if (onRing(player.position(), centre, b.ringRadius)) {
					player.hurtServer(session.level(), session.hymnDamage(c), b.ringDamage);
				}
			}
		}

		if (ringSing >= ringTicks) {
			ring = Math.floorMod(ring + 1, rings.size());
			tellRing(b);
		}
	}

	/** Whether {@code pos} stands on the ring around {@code centre}. */
	static boolean onRing(Vec3 pos, Vec3 centre, double radius) {
		double dx = pos.x - centre.x;
		double dz = pos.z - centre.z;
		double dy = pos.y - centre.y;
		return dx * dx + dz * dz <= radius * radius && dy >= -1.0 && dy <= RING_REACH_UP;
	}

	// ---------------------------------------------------------------- the silencing points

	private void openPoints() {
		FirstCantor c = session.cantor();

		for (Point point : points) {
			point.spent = false;
			point.channeller = null;
			point.progress = 0;
			session.event(point.centre(), null, c != null ? c.getId() : -1, 0f, CantorEffects.SILENCE_POINT);
		}
	}

	private void reopenWhenSpent() {
		for (Point point : points) {
			if (!point.spent) {
				return;
			}
		}

		openPoints();
	}

	private void tickPoints(FirstCantor c, RaidBalance b) {
		for (int i = 0; i < points.length; i++) {
			Point point = points[i];

			if (point.spent) {
				continue;
			}

			ServerPlayer channeller = point.channeller == null ? null : session.level().getServer().getPlayerList().getPlayer(point.channeller);

			if (point.channeller != null && (channeller == null || !channels(channeller, point, b))) {
				breakChannel(point, c, channeller);
				channeller = null;
			}

			if (point.channeller == null) {
				channeller = findChanneller(point, b);

				if (channeller == null) {
					continue;
				}

				point.channeller = channeller.getUUID();
				point.progress = 0;
			}

			point.progress++;

			if (point.progress >= Math.max(1, b.silenceTicks)) {
				silence(i, c, b);
			} else if (point.progress % PROGRESS_TICKS == 0 && channeller != null) {
				float done = point.progress / (float) Math.max(1, b.silenceTicks);
				session.event(point.centre(), null, c.getId(), done, CantorEffects.SILENCE_POINT);
				channeller.sendOverlayMessage(Component.translatable("boss.oldrepublic.cantor.silencing", Math.round(done * 100)));
			}
		}
	}

	/** Whether {@code player} keeps channelling {@code point}: a listed participant, alive, inside, crouching within reach. */
	private boolean channels(ServerPlayer player, Point point, RaidBalance b) {
		return session.isParticipant(player) && player.isAlive() && !player.isSpectator() && player.isShiftKeyDown()
				&& session.layout().inside(player.position()) && player.position().distanceToSqr(point.centre()) <= b.silenceReach * b.silenceReach;
	}

	private @Nullable ServerPlayer findChanneller(Point point, RaidBalance b) {
		for (ServerPlayer player : session.participantsInside()) {
			if (!isChannelling(player.getUUID()) && channels(player, point, b)) {
				return player;
			}
		}

		return null;
	}

	private void breakChannel(Point point, FirstCantor c, @Nullable ServerPlayer channeller) {
		boolean begun = point.progress > 0;
		point.channeller = null;
		point.progress = 0;

		if (begun) {
			session.event(point.centre(), null, c.getId(), 0f, CantorEffects.SILENCE_POINT);

			if (channeller != null) {
				channeller.sendOverlayMessage(Component.translatable("boss.oldrepublic.cantor.silence_broken"));
			}
		}
	}

	/** {@code player} took damage: a channel of theirs breaks (the verse stone's rule). */
	void onDamaged(ServerPlayer player) {
		FirstCantor c = session.cantor();

		if (c == null) {
			return;
		}

		for (Point point : points) {
			if (player.getUUID().equals(point.channeller)) {
				breakChannel(point, c, player);
			}
		}
	}

	/** Point {@code index} falls silent: his window opens (or opens again, from the start). */
	private void silence(int index, FirstCantor c, RaidBalance b) {
		Point point = points[index];
		point.spent = true;
		point.channeller = null;
		point.progress = 0;
		window = Math.max(1, b.windowTicks);
		c.setWindowOpen(true);
		session.event(point.centre(), new Vec3(window, 0, 0), c.getId(), 1f, CantorEffects.SILENCED);
		session.play(point.centre(), ModSounds.BOSS_CANTOR_SILENCE, 2f, 1f);
		Component message = Component.translatable("boss.oldrepublic.cantor.window");

		for (ServerPlayer player : session.participantsInside()) {
			player.sendOverlayMessage(message);
		}
	}

	/** An operator's silence of point {@code index} (tests, the commands). Returns false when it is already spent. */
	public boolean forceSilence(int index) {
		FirstCantor c = session.cantor();

		if (!running || c == null || index < 0 || index >= points.length || points[index].spent) {
			return false;
		}

		silence(index, c, RaidBalance.get());
		return true;
	}

	/** An operator's end of the window (tests): it closes now, and spent points open again when all three are. */
	public void closeWindow() {
		if (window <= 0) {
			return;
		}

		window = 0;
		FirstCantor c = session.cantor();

		if (c != null) {
			c.setWindowOpen(false);
		}

		reopenWhenSpent();
	}
}
