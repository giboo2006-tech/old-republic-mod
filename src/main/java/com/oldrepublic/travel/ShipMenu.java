package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.travel.network.ShipStatusPayload;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The ship screen's menu (spec MILESTONE_4.md 4.10): the hold as 36 slots (27 usable, 36 with the
 * cargo bay) and the player's inventory, shown on the Hold tab only. The hold's truth is the
 * record: every change is written back at once, and a change made elsewhere (credits paid from the
 * hold) is read back every tick, so the hold never exists twice. Only the owner's menu holds the
 * real hold; a crew member's menu has an empty, locked one and shows the Status tab.
 */
public class ShipMenu extends AbstractContainerMenu {
	public static final MenuType<ShipMenu> TYPE = Registry.register(BuiltInRegistries.MENU, OldRepublicMod.id("ship"),
			new MenuType<>(ShipMenu::new, FeatureFlags.VANILLA_SET));

	/** Slot positions on the 272 × 204 screen. */
	public static final int HOLD_X = 56;
	public static final int HOLD_Y = 40;
	public static final int INV_X = 56;
	public static final int INV_Y = 126;
	public static final int HOTBAR_Y = 184;
	public static final int HOLD_SLOTS = ShipRecord.MAX_HOLD;
	private static final int INV_START = HOLD_SLOTS;
	private static final int INV_END = INV_START + 36;
	/** The view is rebuilt (and sent when it changed) this often. */
	private static final int STATUS_INTERVAL = 10;
	/** How near (blocks, from the body's centre) a viewer who is not aboard must stay. */
	private static final double RANGE = 12;

	private final Player player;
	private final @Nullable UUID owner;
	private final boolean ownerView;
	private final SimpleContainer hold = new SimpleContainer(HOLD_SLOTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			holdChanged();
		}
	};
	/** The hold as last written to (or read from) the record. */
	private List<ItemStack> synced = List.of();
	private boolean loading;
	private @Nullable ShipStatusPayload lastStatus;
	private int age;
	/** Client: the Hold tab is showing, and how many hold slots are usable. */
	private boolean holdShown;
	private int clientHoldSize = 27;

	/** Loads the class, registering {@link #TYPE}. */
	static void init() {
	}

	/** Client constructor. */
	public ShipMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, null, false);
	}

	private ShipMenu(int containerId, Inventory inventory, @Nullable UUID owner, boolean ownerView) {
		super(TYPE, containerId);
		this.player = inventory.player;
		this.owner = owner;
		this.ownerView = ownerView;

		for (int i = 0; i < HOLD_SLOTS; i++) {
			addSlot(new HoldSlot(hold, i, HOLD_X + i % 9 * 18, HOLD_Y + i / 9 * 18));
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new InventorySlot(inventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
			}
		}

		for (int col = 0; col < 9; col++) {
			addSlot(new InventorySlot(inventory, col, INV_X + col * 18, HOTBAR_Y));
		}

		if (owner != null && ownerView) {
			load();
		}
	}

	/** Opens the screen of {@code record}'s ship for {@code viewer}: the owner's full screen, or a crew member's Status tab. */
	public static void open(ServerPlayer viewer, ShipRecord record) {
		boolean ownerView = record.owner().equals(viewer.getUUID());
		Component title = Component.translatable("ship.oldrepublic.name." + ShipRules.paint(record.upgrades()));
		viewer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ShipMenu(id, inventory, record.owner(), ownerView), title));

		if (viewer.containerMenu instanceof ShipMenu menu) {
			menu.sendStatus(viewer, true);
		}
	}

	/** The ship's owner (server side). */
	UUID owner() {
		return owner == null ? player.getUUID() : owner;
	}

	// ---------------------------------------------------------------- the hold

	private void load() {
		MinecraftServer server = server();
		ShipRecord record = server == null || owner == null ? null : ShipRegistry.of(server, owner).orElse(null);
		List<ItemStack> stacks = record == null ? List.of() : record.hold();
		loading = true;

		try {
			for (int i = 0; i < HOLD_SLOTS; i++) {
				hold.setItem(i, i < stacks.size() ? stacks.get(i).copy() : ItemStack.EMPTY);
			}
		} finally {
			loading = false;
		}

		synced = copy(stacks);
	}

	/** Writes the hold to the record (the owner's menu, server side). */
	private void holdChanged() {
		MinecraftServer server = server();

		if (loading || !ownerView || owner == null || server == null) {
			return;
		}

		List<ItemStack> contents = new ArrayList<>();

		for (int i = 0; i < HOLD_SLOTS; i++) {
			contents.add(hold.getItem(i).copy());
		}

		while (!contents.isEmpty() && contents.getLast().isEmpty()) {
			contents.removeLast();
		}

		ShipRegistry.of(server, owner).ifPresent(r -> ShipRegistry.put(server, r.withHold(contents)));
		synced = copy(contents);
	}

	@Override
	public void broadcastChanges() {
		if (owner != null && player instanceof ServerPlayer viewer) {
			if (ownerView && !same(ShipRegistry.of(viewer.level().getServer(), owner).map(ShipRecord::hold).orElse(List.of()), synced)) {
				load();
			}

			if (++age % STATUS_INTERVAL == 0) {
				sendStatus(viewer, false);
			}
		}

		super.broadcastChanges();
	}

	/** Sends the screen's view when it changed (or always, with {@code force}). */
	void sendStatus(ServerPlayer viewer, boolean force) {
		ShipStatusPayload status = owner == null ? null : ShipActions.status(viewer, owner, ShipStatusPayload.NOTICE_NONE, 0);

		if (status != null && (force || !status.equals(lastStatus))) {
			lastStatus = status;
			TravelNetwork.send(viewer, status);
		}
	}

	/** Usable hold slots: from the record on the server, from the last view on the client. */
	private int holdSize() {
		MinecraftServer server = server();

		if (owner == null || server == null) {
			return clientHoldSize;
		}

		return ShipRegistry.of(server, owner).map(r -> ShipRules.holdSize(r, ShipBalance.get())).orElse(0);
	}

	// ---------------------------------------------------------------- client view

	/** Client: whether the hold and inventory slots are shown (the Hold tab). */
	public void showHold(boolean shown) {
		this.holdShown = shown;
	}

	/** Client: the usable hold slots, from {@code ship_status}. */
	public void setHoldSize(int size) {
		this.clientHoldSize = Math.clamp(size, 0, HOLD_SLOTS);
	}

	private boolean slotsShown() {
		return owner != null || holdShown;
	}

	// ---------------------------------------------------------------- vanilla menu

	@Override
	public ItemStack quickMoveStack(Player clicker, int index) {
		Slot slot = slots.get(index);

		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();
		boolean holdOpen = ownerView || owner == null && holdShown;
		boolean moved;

		if (index < INV_START) {
			moved = moveItemStackTo(stack, INV_START, INV_END, true);
		} else {
			moved = holdOpen && moveItemStackTo(stack, 0, holdSize(), false);
		}

		if (!moved) {
			return ItemStack.EMPTY;
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

	@Override
	public boolean stillValid(Player viewer) {
		MinecraftServer server = server();

		if (owner == null || server == null) {
			return true;
		}

		CourierShuttle ship = Ships.shuttle(server, owner);
		return ShipRegistry.of(server, owner).isPresent() && ship != null && ship.level() == viewer.level()
				&& (viewer.getVehicle() == ship || viewer.distanceToSqr(ship) <= RANGE * RANGE);
	}

	private @Nullable MinecraftServer server() {
		return player instanceof ServerPlayer serverPlayer ? serverPlayer.level().getServer() : null;
	}

	private static List<ItemStack> copy(List<ItemStack> stacks) {
		List<ItemStack> out = new ArrayList<>(stacks.size());

		for (ItemStack stack : stacks) {
			out.add(stack.copy());
		}

		return out;
	}

	/** Whether two holds hold the same stacks, ignoring trailing empty slots. */
	private static boolean same(List<ItemStack> a, List<ItemStack> b) {
		int size = Math.max(a.size(), b.size());

		for (int i = 0; i < size; i++) {
			ItemStack x = i < a.size() ? a.get(i) : ItemStack.EMPTY;
			ItemStack y = i < b.size() ? b.get(i) : ItemStack.EMPTY;

			if (!ItemStack.matches(x, y)) {
				return false;
			}
		}

		return true;
	}

	/** A hold slot: the owner's, within the hold's size; shown on the Hold tab. */
	private final class HoldSlot extends Slot {
		private final int index;

		HoldSlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
			this.index = index;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return (ownerView || owner == null) && index < holdSize();
		}

		@Override
		public boolean mayPickup(Player clicker) {
			return ownerView || owner == null;
		}

		@Override
		public boolean isActive() {
			return slotsShown() && index < holdSize();
		}
	}

	/** A player inventory slot, shown on the Hold tab. */
	private final class InventorySlot extends Slot {
		InventorySlot(Container container, int index, int x, int y) {
			super(container, index, x, y);
		}

		@Override
		public boolean isActive() {
			return slotsShown();
		}
	}
}
