package com.oldrepublic.saber;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.registry.M3Items;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents.SaberConfig;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Workbench rules, independent of the menu so they can be tested: what the five slots allow and
 * the atomic construct / disassemble operations. Slots: 0 crystal, 1 emitter, 2 grip, 3 pommel,
 * 4 saber.
 *
 * <p>The crystal slot takes the player's own attuned kyber crystal, or a singing crystal (spec
 * 8.8) that is either unattuned or attuned to the player. A singing crystal attunes to the builder
 * on construction with the natural hue {@link BladeColor#AMBER}, and the saber carries
 * {@link SaberComponents#SABER_SINGING}; disassembly gives the singing crystal back.
 */
public final class SaberAssembly {
	public static final int CRYSTAL = 0;
	public static final int EMITTER = 1;
	public static final int GRIP = 2;
	public static final int POMMEL = 3;
	public static final int SABER = 4;
	public static final int SIZE = 5;

	/** What the bench can do with its current contents. Codes are synced to the screen. */
	public enum Status {
		EMPTY,
		READY,
		MISSING_PARTS,
		NOT_YOUR_CRYSTAL,
		CAN_DISASSEMBLE,
		NOT_YOUR_SABER,
		SLOTS_NOT_EMPTY;

		public static Status byCode(int code) {
			Status[] values = values();
			return code >= 0 && code < values.length ? values[code] : EMPTY;
		}

		/** Lang key of the status line shown in the workbench screen. */
		public String translationKey() {
			return "container.oldrepublic.lightsaber_workbench.status." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** Whether {@code stack} may go into {@code slot} for {@code player}. */
	public static boolean mayPlace(int slot, ItemStack stack, UUID player) {
		return switch (slot) {
			case CRYSTAL -> mayUseCrystal(stack, player);
			case EMITTER -> part(stack) instanceof Emitter;
			case GRIP -> part(stack) instanceof Grip;
			case POMMEL -> part(stack) instanceof Pommel;
			case SABER -> stack.getItem() == SaberItems.LIGHTSABER;
			default -> false;
		};
	}

	/** A kyber crystal attuned to {@code player}, or a singing crystal that is unattuned or attuned to {@code player}. */
	public static boolean mayUseCrystal(ItemStack crystal, UUID player) {
		if (crystal.getItem() == SaberItems.KYBER_CRYSTAL) {
			return ownsCrystal(crystal, player);
		}

		return isSingingCrystal(crystal) && (crystal.get(ModComponents.CRYSTAL_ATTUNEMENT) == null || ownsCrystal(crystal, player));
	}

	/** The Collector's reward crystal (spec 8.8). */
	public static boolean isSingingCrystal(ItemStack stack) {
		return stack.getItem() == M3Items.SINGING_CRYSTAL;
	}

	public static boolean ownsCrystal(ItemStack crystal, UUID player) {
		CrystalAttunement attunement = crystal.get(ModComponents.CRYSTAL_ATTUNEMENT);
		return attunement != null && attunement.owner().equals(player);
	}

	/** True for sabers whose crystal is attuned to {@code player}. */
	public static boolean ownsSaber(ItemStack saber, UUID player) {
		return saber.getItem() == SaberItems.LIGHTSABER && SaberComponents.config(saber).crystal().owner().equals(player);
	}

	public static Status status(Container c, UUID player) {
		boolean partsEmpty = c.getItem(CRYSTAL).isEmpty() && c.getItem(EMITTER).isEmpty() && c.getItem(GRIP).isEmpty() && c.getItem(POMMEL).isEmpty();
		ItemStack saber = c.getItem(SABER);

		if (!saber.isEmpty()) {
			if (!partsEmpty) {
				return Status.SLOTS_NOT_EMPTY;
			}

			return ownsSaber(saber, player) ? Status.CAN_DISASSEMBLE : Status.NOT_YOUR_SABER;
		}

		if (partsEmpty) {
			return Status.EMPTY;
		}

		ItemStack crystal = c.getItem(CRYSTAL);

		if (!crystal.isEmpty() && !mayUseCrystal(crystal, player)) {
			return Status.NOT_YOUR_CRYSTAL;
		}

		if (crystal.isEmpty() || emitter(c) == null || grip(c) == null || pommel(c) == null) {
			return Status.MISSING_PARTS;
		}

		return Status.READY;
	}

	/**
	 * Builds the saber: consumes the crystal and the three parts and puts the retracted saber in
	 * the saber slot. Returns the new saber, or {@link ItemStack#EMPTY} if the bench is not ready.
	 */
	public static ItemStack assemble(Container c, UUID player, BladeColor color, SaberBalance balance) {
		return assemble(c, player, "", color, balance);
	}

	/**
	 * {@link #assemble(Container, UUID, BladeColor, SaberBalance)} with the builder's name, which an
	 * unattuned singing crystal takes on as it attunes to the builder.
	 */
	public static ItemStack assemble(Container c, UUID player, String playerName, BladeColor color, SaberBalance balance) {
		if (status(c, player) != Status.READY) {
			return ItemStack.EMPTY;
		}

		ItemStack crystalStack = c.getItem(CRYSTAL);
		boolean singing = isSingingCrystal(crystalStack);
		CrystalAttunement crystal = crystalStack.get(ModComponents.CRYSTAL_ATTUNEMENT);

		if (singing && crystal == null) {
			crystal = singingAttunement(player, playerName);
		}

		ItemStack saber = SaberItems.buildSaber(crystal, color, emitter(c), grip(c), pommel(c), balance);

		if (singing) {
			saber.set(SaberComponents.SABER_SINGING, true);
		}

		for (int slot = CRYSTAL; slot <= POMMEL; slot++) {
			c.setItem(slot, ItemStack.EMPTY);
		}

		c.setItem(SABER, saber);
		return saber;
	}

	/**
	 * Takes a saber apart: returns the attuned crystal and the three parts to slots 0-3. Only the
	 * crystal's owner can do this, and only with the part slots empty. Returns the saber's
	 * configuration, or null if nothing happened.
	 */
	public static @Nullable SaberConfig disassemble(Container c, UUID player) {
		if (status(c, player) != Status.CAN_DISASSEMBLE) {
			return null;
		}

		ItemStack saber = c.getItem(SABER);
		SaberConfig config = SaberComponents.config(saber);
		boolean singing = SaberComponents.isSinging(saber);
		c.setItem(SABER, ItemStack.EMPTY);
		c.setItem(CRYSTAL, singing ? singingCrystalStack(config.crystal()) : crystalStack(config.crystal()));
		c.setItem(EMITTER, new ItemStack(SaberItems.partItem(config.emitter())));
		c.setItem(GRIP, new ItemStack(SaberItems.partItem(config.grip())));
		c.setItem(POMMEL, new ItemStack(SaberItems.partItem(config.pommel())));
		return config;
	}

	/** A kyber crystal carrying {@code attunement}. */
	public static ItemStack crystalStack(CrystalAttunement attunement) {
		ItemStack stack = new ItemStack(SaberItems.KYBER_CRYSTAL);
		stack.set(ModComponents.CRYSTAL_ATTUNEMENT, attunement);
		return stack;
	}

	/** A singing crystal carrying {@code attunement} (what disassembly gives back). */
	public static ItemStack singingCrystalStack(CrystalAttunement attunement) {
		ItemStack stack = new ItemStack(M3Items.SINGING_CRYSTAL);
		stack.set(ModComponents.CRYSTAL_ATTUNEMENT, attunement);
		return stack;
	}

	/** A singing crystal attuning to its builder: the natural hue is {@link BladeColor#AMBER}. */
	public static CrystalAttunement singingAttunement(UUID player, String playerName) {
		return new CrystalAttunement(player, playerName, BladeColor.AMBER.rgb());
	}

	/** Preview stats for the parts on the bench (or of the saber in the saber slot), or null. */
	public static @Nullable SaberStats preview(Container c, SaberBalance balance) {
		ItemStack saber = c.getItem(SABER);

		if (!saber.isEmpty()) {
			return SaberStats.of(SaberComponents.config(saber), balance);
		}

		Emitter emitter = emitter(c);
		Grip grip = grip(c);
		Pommel pommel = pommel(c);
		return emitter != null && grip != null && pommel != null ? SaberStats.of(emitter, grip, pommel, balance) : null;
	}

	private static HiltParts.@Nullable Part part(ItemStack stack) {
		return stack.getItem() instanceof HiltPartItem item ? item.part() : null;
	}

	private static @Nullable Emitter emitter(Container c) {
		return part(c.getItem(EMITTER)) instanceof Emitter e ? e : null;
	}

	private static @Nullable Grip grip(Container c) {
		return part(c.getItem(GRIP)) instanceof Grip g ? g : null;
	}

	private static @Nullable Pommel pommel(Container c) {
		return part(c.getItem(POMMEL)) instanceof Pommel p ? p : null;
	}

	private SaberAssembly() {
	}
}
