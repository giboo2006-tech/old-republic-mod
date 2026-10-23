package com.oldrepublic.saber.forge;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchMenu;

import net.minecraft.server.level.ServerPlayer;

/**
 * The server side of the Forge (MILESTONE_7.md 5.2): a {@link ForgeActionPayload} counts only while its
 * sender has a workbench open and in reach. The menu then checks the socket, the design, the player's
 * known designs, the material and the cost again before it takes or changes anything, and says why on
 * the action bar when it refuses.
 */
public final class ForgeService {
	public static void handle(ServerPlayer p, ForgeActionPayload a) {
		if (!(p.containerMenu instanceof LightsaberWorkbenchMenu menu) || !menu.stillValid(p)) {
			return;
		}

		HiltParts.Part design = ForgeRules.design(a.design());

		if (design == null) {
			return;
		}

		HiltMaterial material = material(a.material());
		boolean changed = switch (a.action()) {
			case ForgeActionPayload.STUDY -> menu.study(p, design);
			case ForgeActionPayload.FORGE -> material != null && menu.forge(p, design, material);
			case ForgeActionPayload.REFORGE -> material != null && menu.reforge(p, design, material);
			default -> false;
		};

		if (changed) {
			menu.broadcastChanges();
		}
	}

	/** The material of this ordinal, or null for one out of range. */
	public static @Nullable HiltMaterial material(int ordinal) {
		HiltMaterial[] values = HiltMaterial.values();
		return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
	}

	private ForgeService() {
	}
}
