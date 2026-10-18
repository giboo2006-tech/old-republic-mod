package com.oldrepublic.vendor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.vendor.network.VendorBuyPayload;
import com.oldrepublic.vendor.network.VendorStockPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Vendors (spec MILESTONE_4.md 7.7, 7.8): opening a vendor's screen, and every purchase, sale and
 * credit exchange it sends back, all checked on the server. A vendor serves the player last opened
 * on it ({@link Session}, in memory only); a purchase is refused before payment when the gate, the
 * day's limit, the player's credits or the inventory space say no.
 *
 * <p>Daily limits are per player per in-game day ({@code Clocks.day}), kept in the story
 * attachment's counters ({@code vendor.day} and {@code vendor.<stock>.<line>}), which are cleared
 * when the day changes, so they survive a relog and stay bounded.
 */
public final class Vendors {
	/** Counter of the day the vendor counters belong to. */
	static final String DAY_COUNTER = "vendor.day";
	static final String COUNTER_PREFIX = "vendor.";

	/** The vendor a player has open: what {@code vendor_buy} is checked against. */
	private record Session(int entityId, UUID entity, String stock, List<Item> sells) {
	}

	private static final Map<UUID, Session> SESSIONS = new HashMap<>();

	/** Opens the vendor screen of stock {@code stock} for {@code p}, sold by {@code vendor}. */
	public static void open(ServerPlayer p, Entity vendor, Identifier stock) {
		VendorStock s = VendorStocks.get(stock.toString());

		if (s == null) {
			OldRepublicMod.LOGGER.warn("Vendor {} has no stock '{}'", vendor.getUUID(), stock);
			p.sendOverlayMessage(Component.translatable("vendor.oldrepublic.no_stock").withStyle(ChatFormatting.GRAY));
			return;
		}

		Faction faction = s.faction().orElse(null);

		if (faction != null && VendorRules.refuses(faction, Identity.tier(p, faction), Identity.path(p))) {
			p.sendSystemMessage(Component.translatable("vendor.oldrepublic.refused." + faction.id()).withStyle(ChatFormatting.GRAY));
			sound(p, ModSounds.VENDOR_REFUSE);
			return;
		}

		send(p, vendor, s);
	}

	/** A {@code vendor_buy} from the client: re-checked in full, then answered with a fresh stock. */
	public static void handle(ServerPlayer p, VendorBuyPayload payload) {
		Session session = SESSIONS.get(p.getUUID());
		Entity vendor = session == null || session.entityId() != payload.vendor() ? null : p.level().getEntity(payload.vendor());
		VendorStock s = session == null ? null : VendorStocks.get(session.stock());

		if (session == null || vendor == null || s == null || !vendor.getUUID().equals(session.entity()) || !vendor.isAlive() || !inReach(p, vendor)) {
			return;
		}

		Faction faction = s.faction().orElse(null);

		if (faction != null && VendorRules.refuses(faction, Identity.tier(p, faction), Identity.path(p))) {
			refuse(p, "vendor.oldrepublic.refused." + faction.id());
			return;
		}

		switch (payload.action()) {
			case VendorBuyPayload.BUY -> buy(p, s, payload.index(), payload.count());
			case VendorBuyPayload.SELL -> sell(p, s, session, payload.index(), payload.count());
			case VendorBuyPayload.TO_BUNDLE -> toBundle(p);
			case VendorBuyPayload.TO_CHITS -> toChits(p);
			default -> {
				return;
			}
		}

		send(p, vendor, s);
	}

	// ---------------------------------------------------------------- buying

	/**
	 * Buys {@code requested} of line {@code index}: the gate, the day's limit, inventory room for all
	 * of it, then payment (inventory and a near ship's hold, exact change). Returns how many were bought.
	 */
	public static int buy(ServerPlayer p, VendorStock s, int index, int requested) {
		if (index < 0 || index >= s.entries().size()) {
			return 0;
		}

		VendorBalance b = VendorFeature.balance();
		VendorEntry entry = s.entries().get(index);
		Faction gate = s.gateOf(entry);
		ReputationTier tier = gate == null ? ReputationTier.NEUTRAL : Identity.tier(p, gate);
		int bought = boughtToday(p, s.id(), index);
		int level = entry.discipline().map(d -> Crafting.level(p, d)).orElse(0);
		String lock = VendorRules.lockReason(entry, gate, tier, level, bought);

		if (lock != null) {
			refuse(p, lock);
			return 0;
		}

		int count = VendorRules.allowedCount(entry, requested, bought, b);
		int each = VendorRules.price(entry.price(), honoured(p, s), b);
		long total = (long) each * count;

		if (total > Integer.MAX_VALUE) {
			refuse(p, "vendor.oldrepublic.refused.credits");
			return 0;
		}

		ItemStack unit = entry.stack();

		if (!hasRoom(p, unit, count)) {
			refuse(p, "vendor.oldrepublic.refused.room");
			return 0;
		}

		if (!Credits.take(p, (int) total, true)) {
			refuse(p, "vendor.oldrepublic.refused.credits");
			return 0;
		}

		for (int i = 0; i < count; i++) {
			Rewards.give(p, unit.copy());
		}

		if (entry.dailyLimit() > 0) {
			addBought(p, s.id(), index, count);
		}

		sound(p, ModSounds.VENDOR_BUY);
		QuestHooks.onPurchase(p, s.id(), unit.copyWithCount(unit.getCount() * count), (int) total);
		return count;
	}

	/** Whether the player's inventory can take {@code copies} of {@code unit} without dropping any. */
	public static boolean hasRoom(ServerPlayer p, ItemStack unit, int copies) {
		long needed = (long) unit.getCount() * copies;
		int max = unit.getMaxStackSize();
		long room = 0;

		for (ItemStack slot : p.getInventory().getNonEquipmentItems()) {
			if (slot.isEmpty()) {
				room += max;
			} else if (ItemStack.isSameItemSameComponents(slot, unit)) {
				room += Math.max(0, Math.min(max, slot.getMaxStackSize()) - slot.getCount());
			}

			if (room >= needed) {
				return true;
			}
		}

		return room >= needed;
	}

	/** Whether the player is Honoured with the vendor's faction (the 0.9 price). */
	static boolean honoured(ServerPlayer p, VendorStock s) {
		return s.faction().map(f -> Identity.tier(p, f) == ReputationTier.HONOURED).orElse(false);
	}

	// ---------------------------------------------------------------- selling and exchange

	/** Sells up to {@code requested} of the sell line {@code index} at the sell rate; named and damaged items are kept. */
	private static void sell(ServerPlayer p, VendorStock s, Session session, int index, int requested) {
		if (index < 0 || index >= session.sells().size()) {
			return;
		}

		Item item = session.sells().get(index);
		int each = VendorRules.sellPrice(s.referencePrice(item), VendorFeature.balance());

		if (each <= 0) {
			return;
		}

		int left = Math.max(1, requested);
		int sold = 0;
		List<ItemStack> inventory = p.getInventory().getNonEquipmentItems();

		for (int i = 0; i < inventory.size() && left > 0; i++) {
			ItemStack stack = inventory.get(i);

			if (sellable(stack) && stack.is(item)) {
				int n = Math.min(left, stack.getCount());
				stack.shrink(n);
				left -= n;
				sold += n;
			}
		}

		if (sold > 0) {
			p.getInventory().setChanged();
			Credits.give(p, sold * each);
			sound(p, ModSounds.CREDITS_PICKUP);
		}
	}

	/** Whether a stack can be sold: not credits, not damaged, not a named drop or a schematic. */
	static boolean sellable(ItemStack stack) {
		return !stack.isEmpty() && !stack.is(M4Items.CREDIT_CHIT) && !stack.is(M4Items.CREDIT_BUNDLE) && !stack.isDamaged()
				&& !stack.has(M4Components.NAMED) && !stack.has(M4Components.SCHEMATIC);
	}

	/** 100 loose chits into a bundle (7.7). */
	static boolean toBundle(ServerPlayer p) {
		if (chits(p) < Credits.BUNDLE) {
			refuse(p, "vendor.oldrepublic.refused.chits");
			return false;
		}

		// Credits.take spends loose chits first, so exactly 100 chits go; give() makes them a bundle.
		if (!Credits.take(p, Credits.BUNDLE, false)) {
			return false;
		}

		Credits.give(p, Credits.BUNDLE);
		sound(p, ModSounds.CREDITS_PICKUP);
		return true;
	}

	/** A bundle into 100 chits (7.7); what does not fit drops for the player only. */
	static boolean toChits(ServerPlayer p) {
		List<ItemStack> inventory = p.getInventory().getNonEquipmentItems();

		for (ItemStack stack : inventory) {
			if (stack.is(M4Items.CREDIT_BUNDLE)) {
				stack.shrink(1);
				p.getInventory().setChanged();
				int chits = Credits.BUNDLE;
				int max = new ItemStack(M4Items.CREDIT_CHIT).getMaxStackSize();

				while (chits > 0) {
					int n = Math.min(max, chits);
					Rewards.give(p, new ItemStack(M4Items.CREDIT_CHIT, n));
					chits -= n;
				}

				sound(p, ModSounds.CREDITS_PICKUP);
				return true;
			}
		}

		refuse(p, "vendor.oldrepublic.refused.bundles");
		return false;
	}

	private static int chits(ServerPlayer p) {
		int n = 0;

		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (stack.is(M4Items.CREDIT_CHIT)) {
				n += stack.getCount();
			}
		}

		return n;
	}

	private static int bundles(ServerPlayer p) {
		int n = 0;

		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (stack.is(M4Items.CREDIT_BUNDLE)) {
				n += stack.getCount();
			}
		}

		return n;
	}

	// ---------------------------------------------------------------- daily limits

	/** How many of line {@code index} of {@code stock} the player bought today. */
	public static int boughtToday(ServerPlayer p, String stock, int index) {
		StoryData data = Story.get(p);
		int today = (int) Clocks.day(p.level().getServer());
		return data.counter(DAY_COUNTER) == today ? data.counter(lineKey(stock, index)) : 0;
	}

	private static void addBought(ServerPlayer p, String stock, int index, int count) {
		StoryData data = Story.get(p);
		int today = (int) Clocks.day(p.level().getServer());

		if (data.counter(DAY_COUNTER) != today || !data.counters.containsKey(DAY_COUNTER)) {
			data.counters.keySet().removeIf(k -> k.startsWith(COUNTER_PREFIX));
			data.counters.put(DAY_COUNTER, today);
		}

		data.counters.merge(lineKey(stock, index), count, Integer::sum);
		Story.commit(p, data);
	}

	static String lineKey(String stock, int index) {
		return COUNTER_PREFIX + stock + "." + index;
	}

	// ---------------------------------------------------------------- the screen

	/** Sends the vendor's stock as this player sees it now, and remembers the session. */
	static void send(ServerPlayer p, Entity vendor, VendorStock s) {
		VendorBalance b = VendorFeature.balance();
		boolean honoured = honoured(p, s);
		List<VendorStockPayload.Line> lines = new ArrayList<>();

		for (int i = 0; i < s.entries().size() && lines.size() < VendorStockPayload.MAX_LINES; i++) {
			VendorEntry entry = s.entries().get(i);
			Faction gate = s.gateOf(entry);
			ReputationTier tier = gate == null ? ReputationTier.NEUTRAL : Identity.tier(p, gate);
			int bought = boughtToday(p, s.id(), i);
			int level = entry.discipline().map(d -> Crafting.level(p, d)).orElse(0);
			String lock = VendorRules.lockReason(entry, gate, tier, level, bought);
			lines.add(new VendorStockPayload.Line(i, entry.stack(), VendorRules.price(entry.price(), honoured, b), lock == null ? "" : lock,
					VendorRules.dailyLeft(entry, bought)));
		}

		Map<Item, Integer> held = new LinkedHashMap<>();

		for (ItemStack stack : p.getInventory().getNonEquipmentItems()) {
			if (sellable(stack) && s.referencePrice(stack.getItem()) > 0) {
				held.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}

		List<Item> sellItems = new ArrayList<>();
		List<VendorStockPayload.Sell> sells = new ArrayList<>();

		for (Map.Entry<Item, Integer> e : held.entrySet()) {
			if (sells.size() >= VendorStockPayload.MAX_SELLS) {
				break;
			}

			sells.add(new VendorStockPayload.Sell(sells.size(), new ItemStack(e.getKey(), Math.max(1, Math.min(99, e.getValue()))),
					VendorRules.sellPrice(s.referencePrice(e.getKey()), b)));
			sellItems.add(e.getKey());
		}

		SESSIONS.put(p.getUUID(), new Session(vendor.getId(), vendor.getUUID(), s.id(), List.copyOf(sellItems)));
		ServerPlayNetworking.send(p, new VendorStockPayload(vendor.getId(), s.id(), s.faction().map(Enum::ordinal).orElse(-1), lines, sells,
				Credits.count(p, true), chits(p), bundles(p), honoured));
	}

	private static boolean inReach(ServerPlayer p, Entity vendor) {
		double reach = VendorFeature.balance().reach;
		return vendor.level() == p.level() && vendor.distanceToSqr(p) <= reach * reach;
	}

	private static void refuse(ServerPlayer p, String encoded) {
		p.sendOverlayMessage(reason(encoded).copy().withStyle(ChatFormatting.RED));
		sound(p, ModSounds.VENDOR_REFUSE);
	}

	/** An encoded reason ({@code key|arg|@nested}) as a component, on the server (overlay messages). */
	static Component reason(String encoded) {
		String[] parts = encoded.split("\\|");
		Object[] args = new Object[parts.length - 1];

		for (int i = 1; i < parts.length; i++) {
			args[i - 1] = parts[i].startsWith("@") ? Component.translatable(parts[i].substring(1)) : parts[i];
		}

		return Component.translatable(parts[0], args);
	}

	private static void sound(ServerPlayer p, SoundEvent sound) {
		p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), sound, SoundSource.PLAYERS, 0.7f, 1f);
	}

	/** Forgets a player's open vendor (logout, dimension change). */
	static void forget(UUID player) {
		SESSIONS.remove(player);
	}

	/** Forgets every session (server stopped). */
	static void clear() {
		SESSIONS.clear();
	}

	/** The stock id a player has open, or null (tests). */
	public static @Nullable String openStock(ServerPlayer p) {
		Session session = SESSIONS.get(p.getUUID());
		return session == null ? null : session.stock();
	}

	private Vendors() {
	}
}
