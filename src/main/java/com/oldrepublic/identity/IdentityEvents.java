package com.oldrepublic.identity;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

/** Server-side events fired by the identity feature after a value has changed and been synced. */
public final class IdentityEvents {
	/** The player's alignment changed. Fired after the change is stored. */
	public static final Event<AlignmentChanged> ALIGNMENT_CHANGED = EventFactory.createArrayBacked(AlignmentChanged.class,
			callbacks -> (player, before, after) -> {
				for (AlignmentChanged callback : callbacks) {
					callback.onAlignmentChanged(player, before, after);
				}
			});

	/** The player's path changed. Fired after the change is stored. */
	public static final Event<PathChanged> PATH_CHANGED = EventFactory.createArrayBacked(PathChanged.class,
			callbacks -> (player, before, after) -> {
				for (PathChanged callback : callbacks) {
					callback.onPathChanged(player, before, after);
				}
			});

	@FunctionalInterface
	public interface AlignmentChanged {
		void onAlignmentChanged(ServerPlayer player, int before, int after);
	}

	@FunctionalInterface
	public interface PathChanged {
		void onPathChanged(ServerPlayer player, Path before, Path after);
	}

	private IdentityEvents() {
	}
}
