package com.oldrepublic.force.sense;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Attitude;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * What a Force Sense cast reveals (spec 10.1). Features register a {@link Provider} for the hidden
 * things they own (veiled stone, cairns and caches, anchors, urns, creatures); the power collects
 * every provider's points with {@link #collect}. Providers use POI queries or entity lookups, never
 * block scans. Register during init. Server side.
 */
public final class SenseTargets {
	/** What a revealed point is. Sent to the client by ordinal: append only. */
	public enum Kind {
		BLOCK,
		CACHE,
		ANCHOR,
		URN,
		CREATURE
	}

	/**
	 * One revealed point.
	 *
	 * @param kind     what the point is
	 * @param pos      where the client draws it (a block's centre, a creature's chest)
	 * @param entityId the creature's entity id, or -1 for blocks
	 * @param attitude how a creature regards the caster (the client tints by it); blocks use {@link Attitude#NEUTRAL}
	 */
	public record Point(Kind kind, Vec3 pos, int entityId, Attitude attitude) {
	}

	/** One feature's source of hidden points. */
	public interface Provider {
		/** Adds every point within range of {@code caster} to {@code out}: blocks within {@code blockRange}, creatures within {@code creatureRange}. */
		void collect(ServerPlayer caster, int blockRange, int creatureRange, Consumer<Point> out);
	}

	private static final List<Provider> PROVIDERS = new ArrayList<>();

	/** Adds a provider. */
	public static void register(Provider provider) {
		PROVIDERS.add(provider);
	}

	/** Every provider's points for one cast, nearest to the caster first, at most {@code max}. */
	public static List<Point> collect(ServerPlayer caster, int blockRange, int creatureRange, int max) {
		List<Point> points = new ArrayList<>();

		for (Provider provider : PROVIDERS) {
			try {
				provider.collect(caster, blockRange, creatureRange, points::add);
			} catch (RuntimeException e) {
				OldRepublicMod.LOGGER.error("A Force Sense provider failed for {}", caster.getName().getString(), e);
			}
		}

		Vec3 eye = caster.getEyePosition();
		points.sort(Comparator.comparingDouble(p -> p.pos().distanceToSqr(eye)));
		return List.copyOf(points.subList(0, Math.clamp(max, 0, points.size())));
	}

	private SenseTargets() {
	}
}
