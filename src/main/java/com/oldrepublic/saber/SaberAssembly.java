package com.oldrepublic.saber;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.Quality;
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
 * Workbench rules, independent of the menu so they can be tested: what the seven slots allow and
 * the atomic construct / disassemble operations. Slots: 0 crystal, 1 emitter, 2 grip, 3 pommel,
 * 4 saber, 5 second crystal, 6 lens (the last two added in Milestone 4, after the saber slot so
 * the M1–M3 slot numbers keep their meaning).
 *
 * <p><b>Saberstaff</b> (spec MILESTONE_4.md 5.6): with {@code grip_staff} in the grip slot the
 * bench is in staff mode. A staff takes two crystals (the second slot holds a kyber crystal of the
 * player's), one emitter (fitted at both ends) and the staff grip; the pommel slot is locked. A
 * second crystal without the staff grip is refused. <b>Lens</b> (5.7): an optional lens of any
 * quality goes into the build; its quality rides on {@link SaberComponents#LENS_QUALITY} and comes
 * back with the lens on disassembly. A training saber never goes on the bench (5.5).
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
	/** A saberstaff's second crystal (5.6). */
	public static final int SECOND = 5;
	/** The optional lens (5.7). */
	public static final int LENS = 6;
	public static final int SIZE = 7;

	/** What the bench can do with its current contents. Codes are synced to the screen. */
	public enum Status {
		EMPTY,
		READY,
		MISSING_PARTS,
		NOT_YOUR_CRYSTAL,
		CAN_DISASSEMBLE,
		NOT_YOUR_SABER,
		SLOTS_NOT_EMPTY,
		/** Staff mode with something in the (locked) pommel slot. */
		STAFF_HAS_POMMEL,
		/** A second crystal without the staff grip. */
		SECOND_NEEDS_STAFF,
		/** Staff mode with everything but the second crystal. */
		STAFF_NEEDS_SECOND;

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
			case SABER -> isBenchSaber(stack);
			// Only a kyber crystal: a singing crystal is the main crystal (its saber sings).
			case SECOND -> stack.getItem() == SaberItems.KYBER_CRYSTAL && ownsCrystal(stack, player);
			case LENS -> stack.getItem() instanceof LensItem;
			default -> false;
		};
	}

	/**
	 * {@link #mayPlace(int, ItemStack, UUID)} with the bench's contents: in staff mode the pommel
	 * slot takes nothing.
	 */
	public static boolean mayPlace(Container c, int slot, ItemStack stack, UUID player) {
		if (slot == POMMEL && isStaffBuild(c)) {
			return false;
		}

		return mayPlace(slot, stack, player);
	}

	/** A saber the bench takes apart: a lightsaber or a saberstaff, never a training saber. */
	public static boolean isBenchSaber(ItemStack stack) {
		return stack.getItem() == SaberItems.LIGHTSABER || stack.getItem() == SaberItems.LIGHTSABER_STAFF;
	}

	/** Staff mode (5.6): the staff grip in the grip slot, or a saberstaff in the saber slot. */
	public static boolean isStaffBuild(Container c) {
		return grip(c) == Grip.STAFF || c.getItem(SABER).getItem() instanceof SaberStaffItem;
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
		return isBenchSaber(saber) && SaberComponents.config(saber).crystal().owner().equals(player);
	}

	/** Whether every part slot (all but the saber slot) is empty. */
	public static boolean partsEmpty(Container c) {
		for (int slot : PART_SLOTS) {
			if (!c.getItem(slot).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	/** The slots that hold parts, in the order they are cleared. */
	private static final int[] PART_SLOTS = {CRYSTAL, EMITTER, GRIP, POMMEL, SECOND, LENS};

	public static Status status(Container c, UUID player) {
		boolean partsEmpty = partsEmpty(c);
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
		ItemStack second = c.getItem(SECOND);

		if ((!crystal.isEmpty() && !mayUseCrystal(crystal, player)) || (!second.isEmpty() && !mayPlace(SECOND, second, player))) {
			return Status.NOT_YOUR_CRYSTAL;
		}

		if (isStaffBuild(c)) {
			if (!c.getItem(POMMEL).isEmpty()) {
				return Status.STAFF_HAS_POMMEL;
			}

			if (crystal.isEmpty() || emitter(c) == null) {
				return Status.MISSING_PARTS;
			}

			return second.isEmpty() ? Status.STAFF_NEEDS_SECOND : Status.READY;
		}

		if (!second.isEmpty()) {
			return Status.SECOND_NEEDS_STAFF;
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

		ItemStack saber;

		if (isStaffBuild(c)) {
			CrystalAttunement second = c.getItem(SECOND).get(ModComponents.CRYSTAL_ATTUNEMENT);
			saber = SaberItems.buildStaff(crystal, second, color, emitter(c), balance);
		} else {
			saber = SaberItems.buildSaber(crystal, color, emitter(c), grip(c), pommel(c), balance);
		}

		if (singing) {
			saber.set(SaberComponents.SABER_SINGING, true);
		}

		ItemStack lensStack = c.getItem(LENS);

		if (lensStack.getItem() instanceof LensItem lens) {
			saber.set(SaberComponents.SABER_CONFIG, SaberComponents.config(saber).withLens(Optional.of(lens.lens().itemId())));
			saber.set(SaberComponents.LENS_QUALITY, Crafting.quality(lensStack).ordinal());
		}

		for (int slot : PART_SLOTS) {
			c.setItem(slot, ItemStack.EMPTY);
		}

		c.setItem(SABER, saber);
		return saber;
	}

	/**
	 * Takes a saber apart: returns the attuned crystal (with its tuning, MILESTONE_5.md 10.4) and the
	 * three parts to slots 0-3 (a legendary part with its Legendary quality), a staff's second crystal
	 * to {@link #SECOND} (and no pommel), and a fitted lens, with its quality, to {@link #LENS}. Only
	 * the crystal's owner can do this, and only with the part slots empty.
	 * Returns the saber's configuration, or null if nothing happened.
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
		c.setItem(EMITTER, SaberItems.partStack(config.emitter()));
		c.setItem(GRIP, SaberItems.partStack(config.grip()));
		c.setItem(POMMEL, saber.getItem() instanceof SaberStaffItem ? ItemStack.EMPTY : SaberItems.partStack(config.pommel()));
		c.setItem(SECOND, config.second().map(SaberAssembly::crystalStack).orElse(ItemStack.EMPTY));
		c.setItem(LENS, lensStack(saber));
		return config;
	}

	/** The lens fitted in {@code saber}, as an item with its quality, or empty. */
	public static ItemStack lensStack(ItemStack saber) {
		SaberLens.Lens lens = SaberLens.lens(saber);

		if (lens == null) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = new ItemStack(SaberItems.lensItem(lens));
		Quality quality = SaberLens.quality(saber);

		if (quality != Quality.STANDARD) {
			Crafting.applyQuality(stack, quality);
		}

		return stack;
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

	/**
	 * Preview stats for the parts on the bench (or of the saber in the saber slot, with its length and
	 * tunings), or null. A new build counts the tunings of the crystals on the bench (MILESTONE_5.md
	 * 10.4) and is standard length (the length switch acts on a built saber, 10.3).
	 */
	public static @Nullable SaberStats preview(Container c, SaberBalance balance) {
		ItemStack saber = c.getItem(SABER);

		if (!saber.isEmpty()) {
			return SaberStats.of(saber, balance);
		}

		Emitter emitter = emitter(c);
		Grip grip = grip(c);
		CrystalAttunement crystal = c.getItem(CRYSTAL).get(ModComponents.CRYSTAL_ATTUNEMENT);

		if (grip == Grip.STAFF) {
			CrystalAttunement second = c.getItem(SECOND).get(ModComponents.CRYSTAL_ATTUNEMENT);
			return emitter != null ? SaberStats.staff(emitter, grip, true, balance).tuned(crystal, second, balance) : null;
		}

		Pommel pommel = pommel(c);
		return emitter != null && grip != null && pommel != null ? SaberStats.of(emitter, grip, pommel, balance).tuned(crystal, null, balance) : null;
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
