package com.oldrepublic.client.holocron;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.quest.JournalTabs;
import com.oldrepublic.holocron.HolocronCatalog;
import com.oldrepublic.holocron.HolocronChoice;
import com.oldrepublic.holocron.HolocronData;
import com.oldrepublic.holocron.HolocronItem;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.holocron.HolocronStamp;
import com.oldrepublic.registry.ModComponents;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Client initialisation for the holocron feature (spec section 4): the attunement moment (the
 * rising holocron, the projected gatekeeper and its hum, the choice effects), echoes and the
 * Archive choice, the attuned banner and toast, the holocron particles, the Holocrons journal tab
 * and the owner's status line on a holocron's tooltip. Called from OldRepublicModClient.
 */
public final class HolocronClient {
	private static long ticks;
	private static @Nullable ClientLevel lastLevel;

	public static void init() {
		HolocronParticleProviders.init();
		HolocronFx.init();
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(RisingHolocron::render);
		LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(GatekeeperFigure::render);
		ClientTickEvents.END_CLIENT_TICK.register(HolocronClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnect());
		ItemTooltipCallback.EVENT.register(HolocronClient::tooltip);
		JournalTabs.register(new JournalTabs.Tab("holocrons", Component.translatable("screen.oldrepublic.holocrons.tab"), 20,
				HolocronShelfPanel::new, HolocronShelfPanel::visible));
	}

	/**
	 * Makes the next Holocrons tab opened read holocron {@code id} on its right page, as if it had
	 * been hovered. Used by the client test steps.
	 */
	public static void selectOnShelf(String id) {
		HolocronShelfPanel.selectNext(id);
	}

	/** The holocron animation clock: client ticks spent in a level, not counting pauses. */
	static long ticks() {
		return ticks;
	}

	/** {@link #ticks()} plus the partial tick, for smooth motion at any frame rate. */
	static double time() {
		return ticks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			// Join, respawn or dimension change: displays and entity ids of the old level are stale.
			lastLevel = mc.level;
			clearDisplays();
		}

		HolocronAttunedWatcher.tick(mc);
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;

		if (level == null || player == null || mc.isPaused()) {
			return;
		}

		ticks++;
		RisingHolocron.tick(level);
		GatekeeperFigure.tick(level, player);
		HolocronFx.tick(level);
	}

	private static void clearDisplays() {
		RisingHolocron.clear();
		GatekeeperFigure.clear();
		HolocronFx.clear();
	}

	private static void disconnect() {
		clearDisplays();
		HolocronHumSound.silence();
		HolocronAttunedWatcher.reset();
		lastLevel = null;
	}

	/**
	 * The owner's status line under the gatekeeper line (spec 4.9): whether this copy answers its
	 * owner, is silent because a newer copy was issued, was sealed or returned, or belongs to
	 * someone else. Only the client knows who is looking, so it is not part of the item's own text.
	 */
	private static void tooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		HolocronStamp stamp = stack.get(ModComponents.HOLOCRON);
		LocalPlayer player = Minecraft.getInstance().player;

		if (stamp == null || player == null || !(stack.getItem() instanceof HolocronItem) || HolocronCatalog.find(stamp.id()) == null) {
			return;
		}

		Component line;

		if (!stamp.owner().equals(player.getUUID())) {
			line = status("other", ChatFormatting.GRAY);
		} else {
			HolocronData data = HolocronService.view(player);

			if (data != null && data.returned.contains(stamp.id())) {
				line = status("returned", ChatFormatting.DARK_AQUA);
			} else if (data == null || !data.isAttuned(stamp.id()) || stamp.serial() != data.serial(stamp.id())) {
				line = status("stale", ChatFormatting.DARK_GRAY);
			} else if (data.choice(stamp.id()) == HolocronChoice.SEAL) {
				line = status("sealed", ChatFormatting.DARK_RED);
			} else {
				line = status("current", ChatFormatting.GOLD);
			}
		}

		// Under the item name and the gatekeeper line.
		lines.add(Math.min(lines.size(), 2), line);
	}

	private static Component status(String key, ChatFormatting color) {
		return Component.translatable("holocron.oldrepublic.item.status." + key).withStyle(color, ChatFormatting.ITALIC);
	}

	private HolocronClient() {
	}
}
