package com.oldrepublic.enemy.trooper;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A block entity that keeps troopers at its post: the outpost's bounty terminal (W1) lists its
 * officer (slot 0) and garrison (slots 1 to 4). A trooper bound to a post through
 * {@link Troopers#bindTerminal} discards itself when it loads and the post lists a different
 * trooper in its slot, so a respawned officer never stands beside a stale duplicate (spec 7.4).
 */
public interface TrooperPost {
	/** The UUID of the trooper the post keeps in {@code slot}, or null when the slot is empty. */
	@Nullable UUID memberAt(int slot);
}
