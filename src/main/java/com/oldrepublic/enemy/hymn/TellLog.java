package com.oldrepublic.enemy.hymn;

/**
 * A foe's own record of its danger tells and heavy hits (spec MILESTONE_5.md 12.1: "a danger tell
 * before every heavy hit"), kept on the server so the game tests can check the order. Each tell
 * covers the one heavy hit that ends it; a heavy hit with no tell running out just before it counts
 * as untold. Shared by the five Milestone 5 foes and the bosses.
 */
public final class TellLog {
	/** A heavy hit may land this many ticks after its tell ran out (a charge's run, a jet-slam's flight). */
	private static final int LATE_SLACK = 60;

	private long tellStart = Long.MIN_VALUE;
	private int tellTicks;
	private int tells;
	private int heavyHits;
	private int untold;

	/** A tell of {@code ticks} starts at game time {@code now}. */
	public void tell(long now, int ticks) {
		tellStart = now;
		tellTicks = Math.max(0, ticks);
		tells++;
	}

	/** The tell in progress was cancelled (an interrupt): no heavy hit follows it. */
	public void cancel() {
		tellStart = Long.MIN_VALUE;
	}

	/** A heavy hit lands at {@code now}; it is told when a tell started at least its length before, and not long before. */
	public void heavy(long now) {
		heavyHits++;
		long since = now - tellStart;

		if (tellStart == Long.MIN_VALUE || since < tellTicks - 1 || since > tellTicks + LATE_SLACK) {
			untold++;
		}

		tellStart = Long.MIN_VALUE;
	}

	public int tells() {
		return tells;
	}

	public int heavyHits() {
		return heavyHits;
	}

	/** Heavy hits that came without their tell; always 0 for a foe that keeps the rule. */
	public int untold() {
		return untold;
	}
}
