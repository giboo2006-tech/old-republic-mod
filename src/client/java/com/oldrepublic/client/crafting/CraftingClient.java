package com.oldrepublic.client.crafting;

import java.util.List;

import com.oldrepublic.client.quest.JournalTabs;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingItems;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.crafting.SchematicLoader;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.crafting.bench.EngineeringBenchMenu;
import com.oldrepublic.crafting.network.SchematicSyncPayload;
import com.oldrepublic.registry.M4Components;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Milestone 4 client initialisation for crafting (spec 7, 11.3, 13.3): the schematic sync, the
 * Engineering Bench screen, the crafting effects, the scanner's marks, the toasts, the quality
 * tooltip line, the thrown stun grenade's renderer and the journal's Crafting tab. Owned by W5
 * crafting and saber; the lead calls it from {@code OldRepublicModClient}.
 */
public final class CraftingClient {
	private static final float GRENADE_SCALE = 0.75f;

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(SchematicSyncPayload.TYPE, (payload, context) -> receive(context.client(), payload));
		MenuScreens.register(EngineeringBenchMenu.TYPE, EngineeringBenchScreen::new);
		EntityRendererRegistry.register(CraftingItems.STUN_GRENADE_ENTITY, context -> new ThrownItemRenderer<>(context, GRENADE_SCALE, false));
		CraftingFx.init();
		ScannerMarks.init();
		CraftingToasts.init();
		ClientTickEvents.END_CLIENT_TICK.register(CraftingClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
		ItemTooltipCallback.EVENT.register(CraftingClient::tooltip);
		JournalTabs.register(new JournalTabs.Tab(CraftingPanel.TAB_ID, Component.translatable("screen.oldrepublic.crafting_tab.tab"), CraftingPanel.TAB_ORDER,
				CraftingPanel::new, CraftingPanel::visible));
	}

	/**
	 * The server's schematics. An integrated server shares this JVM's {@link Schematics} store and
	 * has already filled it, so only a remote server's copy is applied.
	 */
	private static void receive(Minecraft client, SchematicSyncPayload payload) {
		if (client.hasSingleplayerServer()) {
			return;
		}

		Schematics.replace(SchematicLoader.parseAll(payload.schematics(), null));
	}

	private static void tick(Minecraft mc) {
		CraftingFx.tick(mc);
		ScannerMarks.tick(mc);
		CraftingToasts.tick(mc);
	}

	private static void reset() {
		CraftingFx.clear();
		ScannerMarks.clear();
		CraftingToasts.reset();
	}

	/** The quality line (7.4) under the name of a Fine, Masterwork or Legendary item; Legendary's is gold (MILESTONE_5.md 11.1). */
	private static void tooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		if (!stack.has(M4Components.QUALITY)) {
			return;
		}

		Quality q = Crafting.quality(stack);

		if (q == Quality.STANDARD) {
			return;
		}

		ChatFormatting color = switch (q) {
			case LEGENDARY -> ChatFormatting.GOLD;
			case MASTERWORK -> ChatFormatting.LIGHT_PURPLE;
			default -> ChatFormatting.AQUA;
		};
		lines.add(Math.min(1, lines.size()), Component.translatable("quality.oldrepublic." + q.name().toLowerCase(java.util.Locale.ROOT)).withStyle(color));
	}

	private CraftingClient() {
	}
}
