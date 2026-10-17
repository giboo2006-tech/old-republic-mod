package com.oldrepublic.force.sense;

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.server.level.ServerPlayer;

/**
 * Where Force Sense looks for holocron sites: {@link #WORLD} is the real {@link StructureLocator}
 * (asynchronous, per player, on the server's shared budget); game tests pass a fake that answers
 * when the test says so.
 */
public interface SenseLocator {
	/** The player's cached nearest site of {@code kind}, read at once; null when there is none or it went stale. */
	@Nullable SitePiece cached(ServerPlayer p, SiteKind kind);

	/** Queues a search for the nearest site of {@code kind} within {@code radius} blocks that {@code exclude} does not reject. */
	void find(ServerPlayer p, SiteKind kind, int radius, Predicate<SitePiece> exclude, Consumer<@Nullable SitePiece> callback);

	/** The world's structure locator. */
	SenseLocator WORLD = new SenseLocator() {
		@Override
		public @Nullable SitePiece cached(ServerPlayer p, SiteKind kind) {
			return StructureLocator.cached(p, kind);
		}

		@Override
		public void find(ServerPlayer p, SiteKind kind, int radius, Predicate<SitePiece> exclude, Consumer<@Nullable SitePiece> callback) {
			StructureLocator.find(p, kind, radius, exclude, callback);
		}
	};
}
