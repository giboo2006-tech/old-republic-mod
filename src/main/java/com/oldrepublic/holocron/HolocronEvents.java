package com.oldrepublic.holocron;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.SitePiece;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Events of the holocron feature for other features. */
public final class HolocronEvents {
	/**
	 * A player used a holocron pedestal, before the holocron feature handles it. The first listener
	 * that returns true takes the use over (W2's Pathfinder ceremony at Courier's Fall), and the
	 * pedestal does nothing more.
	 */
	public static final Event<BeforePedestal> BEFORE_PEDESTAL = EventFactory.createArrayBacked(BeforePedestal.class,
			callbacks -> (p, pos, kind, site) -> {
				for (BeforePedestal callback : callbacks) {
					if (callback.intercept(p, pos, kind, site)) {
						return true;
					}
				}

				return false;
			});

	/** Listener for {@link #BEFORE_PEDESTAL}. */
	@FunctionalInterface
	public interface BeforePedestal {
		/**
		 * @param site the site piece the pedestal belongs to, or null outside any site
		 * @return true to take the pedestal use over
		 */
		boolean intercept(ServerPlayer p, BlockPos pos, HolocronKind kind, @Nullable SitePiece site);
	}

	private HolocronEvents() {
	}
}
