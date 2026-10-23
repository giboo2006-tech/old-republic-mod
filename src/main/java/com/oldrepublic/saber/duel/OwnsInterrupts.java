package com.oldrepublic.saber.duel;

/**
 * Marker for a {@link SaberDuelist} that decides its own wind-up interrupts in its own
 * {@code ForceReactions} handler (the Collector, spec 8.4). {@code SaberForceReactions} then never
 * interrupts its wind-up; it still answers guarding, guard drain and Push hits for it.
 */
public interface OwnsInterrupts {
}
