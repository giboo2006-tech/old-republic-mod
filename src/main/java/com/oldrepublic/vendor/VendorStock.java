package com.oldrepublic.vendor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.crafting.item.SchematicItem;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.ReputationTier;
import com.oldrepublic.registry.M4Components;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One vendor's stock (spec MILESTONE_4.md 7.8), parsed from {@code data/oldrepublic/vendor/<id>.json}:
 *
 * <pre>{@code
 * {
 *   "faction": "jedi_order",            // optional: whose tiers gate the lines, who is refused (VendorRules#refuses)
 *   "entries": [
 *     {"item": "oldrepublic:silverleaf", "count": 4, "price": 24},
 *     {"item": "oldrepublic:watch_battle_chest", "price": 600, "tier": "trusted"},
 *     {"schematic": "oldrepublic:fusion_cutter_masterwork", "price": 900, "discipline": "medtech", "level": 5, "daily_limit": 1},
 *     {"item": "oldrepublic:hyperdrive_motivator", "price": 400, "faction": "republic", "tier": "neutral"}
 *   ],
 *   "buys": {"oldrepublic:hollowhorn_hide": 20}   // the Sell tab: items bought back at sellRate x this price (each)
 * }
 * }</pre>
 *
 * Lines whose item does not exist (a missing id) are skipped with a warning, so one bad line never
 * empties a vendor. Every stocked item can also be sold back at the sell rate of its price.
 *
 * @param id      the stock id (the file's path)
 * @param faction the vendor's faction, or empty
 * @param entries the lines, in file order (their index is what {@code vendor_buy} quotes)
 * @param buys    item to reference price (per item) for the Sell tab
 */
public record VendorStock(String id, Optional<Faction> faction, List<VendorEntry> entries, Map<Item, Integer> buys) {
	public VendorStock {
		entries = List.copyOf(entries);
		buys = Collections.unmodifiableMap(new LinkedHashMap<>(buys));
	}

	/** The faction gating {@code entry}: its own, else the vendor's, else none. */
	public @Nullable Faction gateOf(VendorEntry entry) {
		return entry.faction().or(this::faction).orElse(null);
	}

	/**
	 * The reference price of one {@code item} when sold here: the {@code buys} price, else the
	 * cheapest stocked line of it (per item), else 0 (not bought).
	 */
	public int referencePrice(Item item) {
		Integer listed = buys.get(item);

		if (listed != null) {
			return listed;
		}

		int best = 0;

		for (VendorEntry entry : entries) {
			ItemStack stack = entry.item();

			if (stack.is(item) && !stack.has(M4Components.SCHEMATIC)) {
				int each = Math.max(1, entry.price() / Math.max(1, stack.getCount()));
				best = best == 0 ? each : Math.min(best, each);
			}
		}

		return best;
	}

	/** Parses and validates one stock file. Throws {@link JsonParseException} with a readable message. */
	public static VendorStock parse(String id, JsonElement json) {
		if (!json.isJsonObject()) {
			throw new JsonParseException("vendor stock must be a JSON object");
		}

		JsonObject root = json.getAsJsonObject();
		Optional<Faction> faction = Optional.ofNullable(faction(root, "faction"));
		List<VendorEntry> entries = new ArrayList<>();

		if (!root.has("entries") || !root.get("entries").isJsonArray()) {
			throw new JsonParseException("vendor stock needs an 'entries' array");
		}

		int index = 0;

		for (JsonElement element : root.getAsJsonArray("entries")) {
			try {
				VendorEntry entry = entry(element);

				if (entry != null) {
					entries.add(entry);
				}
			} catch (JsonParseException | IllegalStateException | UnsupportedOperationException | NumberFormatException e) {
				OldRepublicMod.LOGGER.warn("Vendor {}: skipping line {}: {}", id, index, e.getMessage());
			}

			index++;
		}

		Map<Item, Integer> buys = new LinkedHashMap<>();

		if (root.has("buys") && root.get("buys").isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("buys").entrySet()) {
				Item item = item(entry.getKey());

				if (item == null) {
					OldRepublicMod.LOGGER.warn("Vendor {}: skipping unknown sell item {}", id, entry.getKey());
					continue;
				}

				buys.put(item, Math.max(0, entry.getValue().getAsInt()));
			}
		}

		return new VendorStock(id, faction, entries, buys);
	}

	private static @Nullable VendorEntry entry(JsonElement element) {
		if (!element.isJsonObject()) {
			throw new JsonParseException("a line must be an object");
		}

		JsonObject json = element.getAsJsonObject();
		ItemStack stack;

		if (json.has("schematic")) {
			Identifier schematic = Identifier.tryParse(json.get("schematic").getAsString());

			if (schematic == null) {
				throw new JsonParseException("bad schematic id " + json.get("schematic"));
			}

			stack = SchematicItem.of(schematic);
		} else {
			String itemId = json.has("item") ? json.get("item").getAsString() : "";
			Item item = item(itemId);

			if (item == null) {
				OldRepublicMod.LOGGER.warn("Vendor line skipped: unknown item '{}'", itemId);
				return null;
			}

			int max = new ItemStack(item).getMaxStackSize();
			stack = new ItemStack(item, Math.max(1, Math.min(max, json.has("count") ? json.get("count").getAsInt() : 1)));
		}

		int price = json.has("price") ? json.get("price").getAsInt() : 0;
		Optional<Faction> faction = Optional.ofNullable(faction(json, "faction"));
		int tier = tier(json);
		Optional<Discipline> discipline = Optional.empty();

		if (json.has("discipline")) {
			Discipline d = Discipline.byId(json.get("discipline").getAsString());

			if (d == null) {
				throw new JsonParseException("unknown discipline " + json.get("discipline"));
			}

			discipline = Optional.of(d);
		}

		int level = json.has("level") ? json.get("level").getAsInt() : 0;
		int dailyLimit = json.has("daily_limit") ? json.get("daily_limit").getAsInt() : 0;
		return new VendorEntry(stack, price, faction, tier, discipline, level, dailyLimit);
	}

	private static int tier(JsonObject json) {
		if (!json.has("tier")) {
			return 0;
		}

		JsonElement tier = json.get("tier");

		if (tier.isJsonPrimitive() && tier.getAsJsonPrimitive().isNumber()) {
			return Math.max(0, Math.min(ReputationTier.values().length - 1, tier.getAsInt()));
		}

		String name = tier.getAsString().toUpperCase(Locale.ROOT);

		for (ReputationTier t : ReputationTier.values()) {
			if (t.name().equals(name)) {
				return t.ordinal();
			}
		}

		throw new JsonParseException("unknown tier " + tier);
	}

	private static @Nullable Faction faction(JsonObject json, String key) {
		if (!json.has(key)) {
			return null;
		}

		Faction faction = Faction.byId(json.get(key).getAsString());

		if (faction == null) {
			throw new JsonParseException("unknown faction " + json.get(key));
		}

		return faction;
	}

	private static @Nullable Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
	}
}
