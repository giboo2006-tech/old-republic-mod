package com.oldrepublic.saber;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.registry.M5Items;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.SaberComponents.SaberConfig;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A crystal's tuning (spec MILESTONE_5.md 10.4), kept by id in {@code CrystalAttunement.tuning}, so it
 * travels with the crystal through disassembly and reassembly. Tunings change numbers only, never the
 * blade's colour. Stored by id; the {@code crystal_tune} effect sends the ordinal: append only.
 */
public enum CrystalTuning {
	/** +{@code keenPct} damage. */
	KEEN,
	/** +{@code swiftPct} attack speed. */
	SWIFT,
	/** Poised: +{@code steadyTicks} parry window (under the cap). */
	STEADY,
	/** +{@code wardingGuard} guard stamina. */
	WARDING,
	/** Attuned: Force powers cost {@code resonantPct} less while the saber is lit ({@link SaberTunings}). */
	RESONANT;

	/** The saved id, e.g. {@code keen}. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The tuning's name, {@code saber.oldrepublic.tuning.<id>}. */
	public Component displayName() {
		return Component.translatable("saber.oldrepublic.tuning." + id());
	}

	/** The material a tuning takes besides the credits: sunstone, stormglass shards or a hymnstone shard. */
	public Item material() {
		return switch (this) {
			case KEEN, SWIFT -> M5Items.SUNSTONE;
			case STEADY, WARDING -> M5Items.STORMGLASS_SHARD;
			default -> M5Items.HYMNSTONE_SHARD;
		};
	}

	/** How many of {@link #material()} a tuning takes. */
	public int materialCount(SaberBalance b) {
		return switch (this) {
			case KEEN, SWIFT -> b.tuneSunstone;
			case STEADY, WARDING -> b.tuneStormglass;
			default -> b.tuneHymnstone;
		};
	}

	/** The materials as a stack, for tooltips and the Tune tab. */
	public ItemStack materialStack(SaberBalance b) {
		return new ItemStack(material(), Math.max(1, materialCount(b)));
	}

	/** By {@link #id()}, or null for none or an unknown id. */
	public static @Nullable CrystalTuning byId(String id) {
		for (CrystalTuning t : values()) {
			if (t.id().equals(id)) {
				return t;
			}
		}

		return null;
	}

	/** By ordinal (the effect's variant), or null out of range. */
	public static @Nullable CrystalTuning byIndex(int index) {
		CrystalTuning[] values = values();
		return index >= 0 && index < values.length ? values[index] : null;
	}

	/** The tuning of {@code crystal}, or null. */
	public static @Nullable CrystalTuning of(CrystalAttunement crystal) {
		return byId(crystal.tuning());
	}

	/** How many of {@code config}'s crystals carry {@code tuning}: 0, 1, or 2 on a saberstaff (both count, 10.4). */
	public static int count(SaberConfig config, CrystalTuning tuning) {
		return count(config.crystal(), config.second().orElse(null), tuning);
	}

	/** How many of the two crystals (either may be null) carry {@code tuning}. */
	public static int count(@Nullable CrystalAttunement first, @Nullable CrystalAttunement second, CrystalTuning tuning) {
		int n = first != null && of(first) == tuning ? 1 : 0;
		return n + (second != null && of(second) == tuning ? 1 : 0);
	}
}
