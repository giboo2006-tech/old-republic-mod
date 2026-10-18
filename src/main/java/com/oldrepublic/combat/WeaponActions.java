package com.oldrepublic.combat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The Weapon Action key's server side (spec MILESTONE_4.md 11.1, 15.1): one key, default N, whose
 * meaning belongs to the held item (vent a blaster, the saberstaff's spin, the electrostaff's
 * charge). Owners register a handler for the stacks they own; the first handler whose predicate
 * matches the main-hand stack gets the press and the release.
 */
public final class WeaponActions {
	/** What a registered item does with the key. */
	public interface Handler {
		void onAction(ServerPlayer p, ItemStack held, boolean pressed);
	}

	private record Entry(Predicate<ItemStack> held, Handler handler) {
	}

	private static final List<Entry> HANDLERS = new CopyOnWriteArrayList<>();

	/** Registers {@code handler} for main-hand stacks that match {@code held}. Mod init only. */
	public static void register(Predicate<ItemStack> held, Handler handler) {
		HANDLERS.add(new Entry(held, handler));
	}

	/** Routes one press or release (the payload receiver, and tests). Returns whether a handler took it. */
	public static boolean dispatch(ServerPlayer p, boolean pressed) {
		if (p.isSpectator() || !p.isAlive()) {
			return false;
		}

		ItemStack held = p.getMainHandItem();

		if (held.isEmpty()) {
			return false;
		}

		for (Entry entry : HANDLERS) {
			if (entry.held().test(held)) {
				entry.handler().onAction(p, held, pressed);
				return true;
			}
		}

		return false;
	}

	/** Registers the payload and its receiver. Call once during mod init. */
	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(WeaponActionPayload.TYPE, WeaponActionPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(WeaponActionPayload.TYPE, (payload, context) -> dispatch(context.player(), payload.pressed()));
	}

	private WeaponActions() {
	}
}
