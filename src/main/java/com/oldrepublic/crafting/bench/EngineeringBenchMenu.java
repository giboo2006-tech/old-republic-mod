package com.oldrepublic.crafting.bench;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.CraftingBlocks;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.equipment.mod.Mods;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.registry.M4Items;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Engineering Bench menu (spec MILESTONE_4.md 7.1). Slots: the bench's 9-slot parts tray and
 * the per-use output (Craft tab), the gear and mod slots (Modify), the salvage slot (Salvage), then
 * the player's inventory. Only the current tab's slots are active. The output, gear, mod and
 * salvage slots live in a transient container emptied back to the player when the menu closes.
 *
 * <p>Tabs change with menu buttons 0 to 3; every other action is a {@code bench_action} payload
 * handled by {@link BenchService}. The screen reads the synced data slots ({@code DATA_*}).
 *
 * <p>The same menu serves a trainer's commission (7.8): no tray, no Modify or Salvage, the list
 * limited to the trainer's discipline, and an instant Standard craft for the inputs plus a fee.
 */
public class EngineeringBenchMenu extends AbstractContainerMenu {
	public static final MenuType<EngineeringBenchMenu> TYPE = Registry.register(BuiltInRegistries.MENU,
			OldRepublicMod.id("engineering_bench"), new MenuType<>(EngineeringBenchMenu::new, FeatureFlags.VANILLA_SET));

	public static final int TAB_CRAFT = 0;
	public static final int TAB_MODIFY = 1;
	public static final int TAB_SALVAGE = 2;
	public static final int TAB_DISCIPLINES = 3;
	public static final int TAB_COUNT = 4;

	public static final int TRAY_START = 0;
	public static final int OUTPUT = EngineeringBenchBlockEntity.TRAY_SIZE;
	public static final int GEAR = OUTPUT + 1;
	public static final int MOD = GEAR + 1;
	public static final int SALVAGE = MOD + 1;
	public static final int INV_START = SALVAGE + 1;
	public static final int INV_END = INV_START + 36;
	public static final int HOTBAR_START = INV_END - 9;

	/** Work container indices. */
	private static final int W_OUTPUT = 0;
	private static final int W_GEAR = 1;
	private static final int W_MOD = 2;
	private static final int W_SALVAGE = 3;

	public static final int DATA_TAB = 0;
	/** Ticks done and total of the running craft; total 0 when idle. */
	public static final int DATA_PROGRESS = 1;
	public static final int DATA_TOTAL = 2;
	/**
	 * Fine and Masterwork chances of the selected schematic, in tenths of a percent; -1 without quality.
	 * {@link #DATA_MASTERWORK} is {@link #ODDS_ALWAYS_MASTERWORK} or {@link #ODDS_ALWAYS_LEGENDARY} for
	 * a schematic whose quality is fixed.
	 */
	public static final int DATA_FINE = 3;
	public static final int DATA_MASTERWORK = 4;
	/** Discipline XP the selected schematic gives now. */
	public static final int DATA_XP = 5;
	/** Quality crafts left until a guaranteed Masterwork, or 0 when not shown. */
	public static final int DATA_PITY = 6;
	/** Player XP of the selected schematic's first craft, or 0 once made. */
	public static final int DATA_FIRST_XP = 7;
	public static final int DATA_RESULT = 8;
	/** Bumped on every result, so the screen notices a repeat of the same one. */
	public static final int DATA_RESULT_SEQ = 9;
	public static final int DATA_SECOND_OPEN = 10;
	/** Salvage return, in percent. */
	public static final int DATA_SALVAGE_PCT = 11;
	/** 0 at a bench; 1 + discipline ordinal for a commission (0 more means any discipline). */
	public static final int DATA_MODE = 12;
	/** Commission fee of the selected schematic, in credits. */
	public static final int DATA_FEE = 13;
	/** The salvage XP of the item in the salvage slot. */
	public static final int DATA_SALVAGE_XP = 14;
	private static final int DATA_COUNT = 15;

	/** {@link #DATA_MASTERWORK} of a trainer's Masterwork-only recipe (MILESTONE_4.md 7.8): always Masterwork. */
	public static final int ODDS_ALWAYS_MASTERWORK = 1000;
	/** {@link #DATA_MASTERWORK} of a legendary schematic (MILESTONE_5.md 11.1): always Legendary. */
	public static final int ODDS_ALWAYS_LEGENDARY = 2000;

	/** {@link #DATA_MODE} of a commission for any discipline. */
	public static final int MODE_COMMISSION_ANY = 1 + Discipline.values().length;

	private final ContainerLevelAccess access;
	private final Player player;
	private final Container tray;
	private final @Nullable Entity trainer;
	private final SimpleContainerData data = new SimpleContainerData(DATA_COUNT);
	private final SimpleContainer work = new SimpleContainer(4) {
		@Override
		public void setChanged() {
			super.setChanged();
			slotsChanged(this);
		}
	};
	private @Nullable Identifier selected;

	/** Loads the class, registering {@link #TYPE}. */
	public static void init() {
	}

	/** Client constructor. */
	public EngineeringBenchMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new SimpleContainer(EngineeringBenchBlockEntity.TRAY_SIZE), ContainerLevelAccess.NULL, null);
	}

	/**
	 * Server constructor: a bench ({@code trainer} null) or a commission ({@code tray} is then a
	 * throwaway container that is never active).
	 */
	public EngineeringBenchMenu(int containerId, Inventory inventory, Container tray, ContainerLevelAccess access, @Nullable Entity trainer) {
		super(TYPE, containerId);
		this.access = access;
		this.player = inventory.player;
		this.tray = tray;
		this.trainer = trainer;
		checkContainerSize(tray, EngineeringBenchBlockEntity.TRAY_SIZE);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				addSlot(new TabSlot(tray, row * 3 + col, 206 + col * 18, 36 + row * 18, TAB_CRAFT));
			}
		}

		addSlot(new TabSlot(work, W_OUTPUT, 224, 96, TAB_CRAFT) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});
		addSlot(new TabSlot(work, W_GEAR, 22, 52, TAB_MODIFY) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return super.mayPlace(stack) && !Mods.slotsOf(stack).isEmpty();
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addSlot(new TabSlot(work, W_MOD, 22, 84, TAB_MODIFY) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return super.mayPlace(stack) && M4Items.MODS.contains(stack.getItem());
			}
		});
		addSlot(new TabSlot(work, W_SALVAGE, 22, 64, TAB_SALVAGE) {
			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		addStandardInventorySlots(inventory, 34, 118);
		addDataSlots(data);

		if (player instanceof ServerPlayer serverPlayer) {
			data.set(DATA_MODE, trainer == null ? 0 : MODE_COMMISSION_ANY);
			// The first numbers (the discipline choice gate, the salvage share) go out with the opening sync.
			BenchService.refresh(serverPlayer, this);
		}
	}

	/** Opens a trainer's commission menu (the {@code trainer:commission} dialogue action, 7.8). */
	public static void openCommission(ServerPlayer p, @Nullable Entity trainer, @Nullable Discipline discipline) {
		Component title = discipline == null
				? Component.translatable("screen.oldrepublic.bench.commission")
				: Component.translatable("screen.oldrepublic.bench.commission_of", Component.translatable("discipline.oldrepublic." + discipline.id()));
		p.openMenu(new SimpleMenuProvider((id, inventory, opener) -> {
			EngineeringBenchMenu menu = new EngineeringBenchMenu(id, inventory, new SimpleContainer(EngineeringBenchBlockEntity.TRAY_SIZE), ContainerLevelAccess.NULL,
					trainer != null ? trainer : opener);
			menu.data.set(DATA_MODE, discipline == null ? MODE_COMMISSION_ANY : 1 + discipline.ordinal());
			return menu;
		}, title));
	}

	// ---------------------------------------------------------------- state for the screen and the service

	/** Synced value for the screen; see the {@code DATA_*} constants. */
	public int data(int index) {
		return data.get(index);
	}

	public int tab() {
		return data.get(DATA_TAB);
	}

	/** Whether this menu is a trainer's commission rather than a bench. */
	public boolean isCommission() {
		return data.get(DATA_MODE) != 0;
	}

	/** The commission's discipline, or null at a bench or for any discipline. */
	public @Nullable Discipline commissionDiscipline() {
		int mode = data.get(DATA_MODE);
		return mode >= 1 && mode <= Discipline.values().length ? Discipline.values()[mode - 1] : null;
	}

	/** The parts tray (the bench's, or a client copy). */
	public Container trayContainer() {
		return tray;
	}

	/** The bench position, or null for a commission. */
	public @Nullable Vec3 benchCentre() {
		return access.evaluate((level, pos) -> Vec3.atCenterOf(pos)).orElse(null);
	}

	public ContainerLevelAccess access() {
		return access;
	}

	@Nullable Identifier selected() {
		return selected;
	}

	void select(@Nullable Identifier id) {
		selected = id;
	}

	void set(int index, int value) {
		data.set(index, value);
	}

	/** Shows {@code result} on the screen's status line. */
	void report(BenchResult result) {
		data.set(DATA_RESULT, result.ordinal());
		data.set(DATA_RESULT_SEQ, (data.get(DATA_RESULT_SEQ) + 1) & 0x3FFF);
	}

	ItemStack output() {
		return work.getItem(W_OUTPUT);
	}

	void setOutput(ItemStack stack) {
		work.setItem(W_OUTPUT, stack);
	}

	ItemStack gear() {
		return work.getItem(W_GEAR);
	}

	ItemStack mod() {
		return work.getItem(W_MOD);
	}

	ItemStack salvageInput() {
		return work.getItem(W_SALVAGE);
	}

	void setSalvageInput(ItemStack stack) {
		work.setItem(W_SALVAGE, stack);
	}

	/** Marks the work slots changed after an item in them was edited in place. */
	void workChanged() {
		work.setChanged();
	}

	@Override
	public boolean clickMenuButton(Player clicker, int id) {
		if (id < 0 || id >= TAB_COUNT || (isCommission() && id != TAB_CRAFT)) {
			return false;
		}

		data.set(DATA_TAB, id);
		return true;
	}

	@Override
	public void slotsChanged(Container changed) {
		super.slotsChanged(changed);

		if (player instanceof ServerPlayer serverPlayer) {
			BenchService.refresh(serverPlayer, this);
		}
	}

	// ---------------------------------------------------------------- shift-click

	@Override
	public ItemStack quickMoveStack(Player clicker, int index) {
		Slot slot = slots.get(index);

		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (index < INV_START) {
			if (!moveItemStackTo(stack, INV_START, INV_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (!moveIntoBench(stack)) {
			boolean moved = index < HOTBAR_START
					? moveItemStackTo(stack, HOTBAR_START, INV_END, false)
					: moveItemStackTo(stack, INV_START, HOTBAR_START, false);

			if (!moved) {
				return ItemStack.EMPTY;
			}
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}

		slot.onTake(clicker, stack);
		return original;
	}

	/** Shift-click from the inventory into the current tab's slots. */
	private boolean moveIntoBench(ItemStack stack) {
		return switch (tab()) {
			case TAB_CRAFT -> !isCommission() && moveItemStackTo(stack, TRAY_START, TRAY_START + EngineeringBenchBlockEntity.TRAY_SIZE, false);
			case TAB_MODIFY -> moveInto(stack, GEAR) || moveInto(stack, MOD);
			case TAB_SALVAGE -> moveInto(stack, SALVAGE);
			default -> false;
		};
	}

	private boolean moveInto(ItemStack stack, int index) {
		Slot target = slots.get(index);
		return target.isActive() && target.mayPlace(stack) && moveItemStackTo(stack, index, index + 1, false);
	}

	// ---------------------------------------------------------------- closing

	@Override
	public void removed(Player closer) {
		super.removed(closer);

		if (closer instanceof ServerPlayer serverPlayer) {
			BenchService.onClosed(serverPlayer, this);

			if (serverPlayer.isAlive() && !serverPlayer.hasDisconnected()) {
				// Back to the player; what does not fit drops for them only.
				for (int i = 0; i < work.getContainerSize(); i++) {
					Rewards.give(serverPlayer, work.removeItemNoUpdate(i));
				}

				return;
			}
		}

		clearContainer(closer, work);
	}

	@Override
	public boolean stillValid(Player viewer) {
		if (trainer != null) {
			return trainer.isAlive() && trainer.level() == viewer.level() && trainer.distanceToSqr(viewer) <= 64.0;
		}

		return stillValid(access, viewer, CraftingBlocks.ENGINEERING_BENCH);
	}

	/** A slot that is only active (shown, clickable) on one tab, and never in a commission. */
	private class TabSlot extends Slot {
		private final int tab;

		TabSlot(Container container, int index, int x, int y, int tab) {
			super(container, index, x, y);
			this.tab = tab;
		}

		@Override
		public boolean isActive() {
			return tab() == tab && !isCommission();
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return !isCommission();
		}
	}
}
