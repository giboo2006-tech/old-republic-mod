package com.oldrepublic.saber.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Either;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.bench.Ingredients;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.saber.HiltMaterial;
import com.oldrepublic.saber.HiltMaterials;
import com.oldrepublic.saber.HiltPartItem;
import com.oldrepublic.saber.HiltParts;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.HiltSet;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberStats;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Forge's rules (MILESTONE_7.md 5.2): what a Study, a Forge and a Re-forge need, what they cost and
 * the part a Re-forge leaves. The checks only read, so the workbench menu acts on them on the server and
 * the screen shows them on the client (with the credits the player carries; the server also counts a near
 * ship's hold, as the Tune tab does). Paying takes the credits and the material's units the way the Tune
 * tab takes its own ({@link Credits}, then {@link Ingredients}, all or nothing).
 */
public final class ForgeRules {
	/** Credit prices run by tier, 0 (steel) to the highest material's. */
	public static final int TIERS = maxTier() + 1;

	/** What a Forge request came to; each has a status line {@code screen.oldrepublic.workbench.forge.status.<id>}. */
	public enum Result {
		OK,
		/** No part of that design in its socket (Study, Re-forge). */
		NO_PART,
		/** A set or legendary design: never studied or forged, only re-forged. */
		NOT_CRAFTABLE,
		/** The design is known already (Study). */
		KNOWN,
		/** The design is not known yet (Forge). */
		UNKNOWN,
		/** The part is made of that material already (Re-forge). */
		SAME_MATERIAL,
		/** Short of the material's units. */
		NO_MATERIALS,
		/** Short of credits. */
		NO_CREDITS;

		public String messageKey() {
			return "screen.oldrepublic.workbench.forge.status." + name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * What forging costs (balance section {@code saber}): the material's units a part takes by slot (the
	 * staff grip its own) and the credits by the material's tier. The workbench menu syncs these to the
	 * screen, so a server's edited balance shows.
	 */
	public record Costs(int emitterUnits, int gripUnits, int staffGripUnits, int pommelUnits, int[] creditsByTier) {
		public static Costs of(SaberBalance b) {
			return new Costs(b.forgeUnitsEmitter, b.forgeUnitsGrip, b.forgeUnitsStaffGrip, b.forgeUnitsPommel,
					b.forgeCredits != null ? b.forgeCredits.clone() : new int[0]);
		}

		/** The units of its material {@code design} takes. */
		public int units(HiltParts.Part design) {
			return Math.max(0, switch (HiltParts.slot(design)) {
				case EMITTER -> emitterUnits;
				case GRIP -> design == Grip.STAFF ? staffGripUnits : gripUnits;
				case POMMEL -> pommelUnits;
			});
		}

		/** The credits a part of {@code material} takes. */
		public int credits(HiltMaterial material) {
			return creditsForTier(material.tier());
		}

		/** The credits a part of a material of {@code tier} takes; a tier past an edited array's end pays its last price. */
		public int creditsForTier(int tier) {
			if (creditsByTier.length == 0) {
				return 0;
			}

			return Math.max(0, creditsByTier[Math.clamp(tier, 0, creditsByTier.length - 1)]);
		}
	}

	// ---------------------------------------------------------------- designs

	/** Every design of {@code slot}, in its enum's order. */
	public static List<HiltParts.Part> designs(HiltParts.Slot slot) {
		return switch (slot) {
			case EMITTER -> List.<HiltParts.Part>of(Emitter.values());
			case GRIP -> List.<HiltParts.Part>of(Grip.values());
			case POMMEL -> List.<HiltParts.Part>of(Pommel.values());
		};
	}

	/** The designs of {@code slot} the Forge makes (5.1), in their enum's order. */
	public static List<HiltParts.Part> craftable(HiltParts.Slot slot) {
		List<HiltParts.Part> out = new ArrayList<>();

		for (HiltParts.Part part : designs(slot)) {
			if (SaberItems.isCraftableDesign(part)) {
				out.add(part);
			}
		}

		return out;
	}

	/** The design with this item id ({@code emitter_vanguard}), or null. */
	public static HiltParts.@Nullable Part design(String itemId) {
		for (HiltParts.Slot slot : HiltParts.Slot.values()) {
			for (HiltParts.Part part : designs(slot)) {
				if (part.itemId().equals(itemId)) {
					return part;
				}
			}
		}

		return null;
	}

	/** The design of a loose part, or null. */
	public static HiltParts.@Nullable Part part(ItemStack stack) {
		return stack.getItem() instanceof HiltPartItem item ? item.part() : null;
	}

	/** The bench socket ({@code SaberAssembly.EMITTER}, {@code GRIP} or {@code POMMEL}) a design goes in. */
	public static int socket(HiltParts.Part design) {
		return socket(HiltParts.slot(design));
	}

	public static int socket(HiltParts.Slot slot) {
		return switch (slot) {
			case EMITTER -> SaberAssembly.EMITTER;
			case GRIP -> SaberAssembly.GRIP;
			case POMMEL -> SaberAssembly.POMMEL;
		};
	}

	// ---------------------------------------------------------------- costs

	/** The item a material's units are ({@link HiltMaterial#itemId()}), or null if it is not registered. */
	public static @Nullable Item unitItem(HiltMaterial material) {
		return BuiltInRegistries.ITEM.getOptional(material.itemId()).orElse(null);
	}

	/**
	 * The material units {@code design} in {@code material} takes, as a crafting input (for
	 * {@link Ingredients}): empty when the balance asks for none, null when the unit item is missing.
	 */
	public static @Nullable List<Schematic.Input> inputs(HiltParts.Part design, HiltMaterial material, Costs costs) {
		int units = costs.units(design);

		if (units <= 0) {
			return List.of();
		}

		Item item = unitItem(material);
		return item == null ? null : List.of(new Schematic.Input(Either.left(item), units));
	}

	// ---------------------------------------------------------------- checks (either side; they only read)

	/**
	 * Study (5.2): {@code socket} (the stack in a part socket) holds a part of a craftable design the player
	 * does not know yet. Free; the part stays.
	 */
	public static Result checkStudy(ItemStack socket, ForgeData known) {
		HiltParts.Part part = part(socket);

		if (part == null) {
			return Result.NO_PART;
		}

		if (!SaberItems.isCraftableDesign(part)) {
			return Result.NOT_CRAFTABLE;
		}

		return known.knows(part) ? Result.KNOWN : Result.OK;
	}

	/**
	 * Forge (5.2): a known craftable {@code design} in {@code material}, paid from {@code sources} (the
	 * stacks that may pay, {@link Ingredients#sources}) and {@code credits} (what the player has).
	 */
	public static Result checkForge(HiltParts.Part design, HiltMaterial material, ForgeData known, List<ItemStack> sources, long credits, Costs costs) {
		if (!SaberItems.isCraftableDesign(design)) {
			return Result.NOT_CRAFTABLE;
		}

		if (!known.knows(design)) {
			return Result.UNKNOWN;
		}

		return checkCost(design, material, sources, credits, costs);
	}

	/**
	 * Re-forge (5.2): {@code socket} holds a part of {@code design} (craftable, set or legendary) that is not
	 * made of {@code material} yet; the same cost as forging that design in {@code material}.
	 */
	public static Result checkReforge(ItemStack socket, HiltParts.Part design, HiltMaterial material, List<ItemStack> sources, long credits, Costs costs) {
		if (part(socket) != design) {
			return Result.NO_PART;
		}

		if (SaberComponents.material(socket) == material) {
			return Result.SAME_MATERIAL;
		}

		return checkCost(design, material, sources, credits, costs);
	}

	/** Whether the material's units and the credits for {@code design} in {@code material} are there. */
	public static Result checkCost(HiltParts.Part design, HiltMaterial material, List<ItemStack> sources, long credits, Costs costs) {
		List<Schematic.Input> inputs = inputs(design, material, costs);

		if (inputs == null || !Ingredients.has(sources, inputs)) {
			return Result.NO_MATERIALS;
		}

		return credits < costs.credits(material) ? Result.NO_CREDITS : Result.OK;
	}

	// ---------------------------------------------------------------- acting (server)

	/**
	 * Takes the cost of {@code design} in {@code material} from {@code player}, all or nothing: the credits
	 * (with a near ship's hold, as the Tune tab), then the units from the inventory; the credits come back
	 * if the units are short.
	 */
	public static boolean pay(ServerPlayer player, HiltParts.Part design, HiltMaterial material, Costs costs) {
		List<Schematic.Input> inputs = inputs(design, material, costs);
		int credits = costs.credits(material);

		if (inputs == null || !Credits.take(player, credits, true)) {
			return false;
		}

		if (!Ingredients.take(null, player, inputs)) {
			Credits.give(player, credits);
			return false;
		}

		return true;
	}

	/**
	 * {@code part} re-forged in {@code material}: the same stack (its design, its quality and every other
	 * component) with the new material; the old one is gone. A steel part goes without the component.
	 */
	public static ItemStack reforged(ItemStack part, HiltMaterial material) {
		ItemStack out = part.copyWithCount(1);

		if (material == HiltMaterial.STEEL) {
			out.remove(SaberComponents.HILT_MATERIAL);
		} else {
			out.set(SaberComponents.HILT_MATERIAL, material);
		}

		return out;
	}

	// ---------------------------------------------------------------- the stat change (5.3)

	/**
	 * The stats of a hilt of these parts and materials as the bench builds it, for the Forge tab's stat
	 * change: the staff grip makes a saberstaff (MILESTONE_4.md 5.6); a missing part is a steel stand-in
	 * that adds no number (the Reliquary emitter, whose numbers are {@code SaberGuard}'s; no grip numbers;
	 * no pommel), so a change against an empty socket shows what the part itself adds. Beside a Collector
	 * part the stand-in emitter is the Guardian, so a stand-in never completes a set (7.2).
	 */
	public static SaberStats hiltStats(@Nullable Emitter emitter, @Nullable Grip grip, @Nullable Pommel pommel, HiltMaterials materials, SaberBalance b) {
		HiltMaterials m = materials;

		if (emitter == null) {
			m = m.with(HiltParts.Slot.EMITTER, HiltMaterial.STEEL);
		}

		if (grip == null) {
			m = m.with(HiltParts.Slot.GRIP, HiltMaterial.STEEL);
		}

		if (pommel == null || grip == Grip.STAFF) {
			m = m.with(HiltParts.Slot.POMMEL, HiltMaterial.STEEL);
		}

		Emitter e = emitter != null ? emitter : standInEmitter(grip, pommel);

		if (grip == Grip.STAFF) {
			return SaberStats.staff(e, Grip.STAFF, true, b).withMaterials(m, false, b);
		}

		// The staff grip adds no number to a single saber (its numbers are staff()'s): the missing grip's stand-in.
		return SaberStats.of(e, grip != null ? grip : Grip.STAFF, pommel, b).withMaterials(m, pommel != null, b);
	}

	/** {@link #hiltStats} of the parts in three socket stacks, each with its own material. */
	public static SaberStats benchStats(ItemStack emitter, ItemStack grip, ItemStack pommel, SaberBalance b) {
		return hiltStats(part(emitter) instanceof Emitter e ? e : null, part(grip) instanceof Grip g ? g : null, part(pommel) instanceof Pommel p ? p : null,
				new HiltMaterials(SaberComponents.material(emitter), SaberComponents.material(grip), SaberComponents.material(pommel)), b);
	}

	private static Emitter standInEmitter(@Nullable Grip grip, @Nullable Pommel pommel) {
		HiltSet set = HiltSet.of(Emitter.RELIQUARY);
		boolean pairs = set != null && (grip != null && HiltSet.of(grip) == set || pommel != null && HiltSet.of(pommel) == set);
		return pairs ? Emitter.GUARDIAN : Emitter.RELIQUARY;
	}

	private static int maxTier() {
		int max = 0;

		for (HiltMaterial material : HiltMaterial.values()) {
			max = Math.max(max, material.tier());
		}

		return max;
	}

	private ForgeRules() {
	}
}
