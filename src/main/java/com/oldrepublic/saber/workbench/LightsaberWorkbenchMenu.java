package com.oldrepublic.saber.workbench;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.crafting.CraftingData.Progress;
import com.oldrepublic.crafting.Discipline;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.CrystalTuning;
import com.oldrepublic.saber.LensItem;
import com.oldrepublic.saber.SaberAssembly;
import com.oldrepublic.saber.SaberAssembly.Status;
import com.oldrepublic.saber.SaberBalance;
import com.oldrepublic.saber.SaberComponents;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberEffects;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberStats;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.saber.SaberTunings;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
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
 * The workbench menu. Its seven slots live in a transient container that is emptied back into the
 * player's inventory when the menu closes (including disconnects), so items are never lost or
 * duplicated. Every decision (construct, disassemble, recolour) happens on the server in
 * {@link #clickMenuButton}; the screen only shows the synced {@link #data(int) data slots}.
 *
 * <p>Milestone 4 (spec 5.6, 5.7): the second-crystal slot shows in staff mode (the staff grip in
 * the grip slot, or a saberstaff on the bench), where the pommel slot locks; the lens slot shows
 * when the player has Artifice 2, carries a lens, or the slot holds one. A newly fitted lens plays
 * the {@code lens_fit} effect to the player.
 *
 * <p>Milestone 5 (MILESTONE_5.md 10.3, 10.4): the screen's Assemble tab gains the blade length switch
 * ({@link #BUTTON_LENGTH}, free, on a built single lightsaber of the player's own) and its Tune tab
 * tunes a crystal ({@link #BUTTON_TUNE}): the loose crystal, the loose second crystal, or either
 * crystal of the saber on the bench, for credits and a material ({@link SaberTunings}), with the
 * {@code crystal_tune} effect to the player. The tabs are the screen's own; the slots are the same
 * in both.
 */
public class LightsaberWorkbenchMenu extends AbstractContainerMenu {
	public static final MenuType<LightsaberWorkbenchMenu> TYPE = Registry.register(BuiltInRegistries.MENU,
			OldRepublicMod.id("lightsaber_workbench"), new MenuType<>(LightsaberWorkbenchMenu::new, FeatureFlags.VANILLA_SET));

	/** Buttons 0-7 select a blade colour (palette index). */
	public static final int BUTTON_CONSTRUCT = 100;
	public static final int BUTTON_DISASSEMBLE = 101;
	/** Blade length (10.3): {@code BUTTON_LENGTH + SaberConfig.LENGTH_*}. */
	public static final int BUTTON_LENGTH = 110;
	/** Crystal tuning (10.4): {@code BUTTON_TUNE + target * TUNE_STRIDE + CrystalTuning ordinal}. */
	public static final int BUTTON_TUNE = 200;
	public static final int TUNE_STRIDE = 10;
	/** Tune targets: the loose crystal, the loose second crystal, the built saber's crystal, a built saberstaff's second crystal. */
	public static final int TUNE_CRYSTAL = 0;
	public static final int TUNE_SECOND = 1;
	public static final int TUNE_SABER = 2;
	public static final int TUNE_SABER_SECOND = 3;
	public static final int TUNE_TARGETS = 4;

	/** Data slot indices. Stats are fixed-point so they fit integer data slots. */
	public static final int DATA_COLOR = 0;
	public static final int DATA_DAMAGE_X100 = 1;
	public static final int DATA_SPEED_X100 = 2;
	public static final int DATA_REACH_X100 = 3;
	public static final int DATA_STAMINA = 4;
	public static final int DATA_PARRY_X10 = 5;
	public static final int DATA_STATUS = 6;
	public static final int DATA_HAS_PREVIEW = 7;
	private static final int DATA_COUNT = 8;
	/** The Artifice level that opens the lens slot (5.7). */
	public static final int LENS_ARTIFICE_LEVEL = 2;

	/** Player inventory slots follow the seven bench slots. */
	private static final int INV_START = SaberAssembly.SIZE;
	private static final int INV_END = INV_START + 36;
	private static final int HOTBAR_START = INV_END - 9;

	private final ContainerLevelAccess access;
	private final Player player;
	private final UUID playerId;
	private final SimpleContainerData data = new SimpleContainerData(DATA_COUNT);
	private final Container container = new SimpleContainer(SaberAssembly.SIZE) {
		@Override
		public void setChanged() {
			super.setChanged();
			slotsChanged(this);
		}
	};
	/** Contents seen at the last {@link #slotsChanged}, to detect newly placed items. */
	private final boolean[] filled = new boolean[SaberAssembly.SIZE];
	private BladeColor color = BladeColor.BLUE;
	/** Set once the player picks a colour, so placing a crystal no longer overrides it. */
	private boolean colorChosen;
	/** True while a button rewrites the slots, to avoid one placement sound per slot. */
	private boolean rewriting;

	/** Loads the class, registering {@link #TYPE}. */
	public static void init() {
	}

	/** Client constructor. */
	public LightsaberWorkbenchMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, ContainerLevelAccess.NULL);
	}

	public LightsaberWorkbenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
		super(TYPE, containerId);
		this.access = access;
		this.player = inventory.player;
		this.playerId = inventory.player.getUUID();

		addSlot(new BenchSlot(container, SaberAssembly.CRYSTAL, 10, 20));
		addSlot(new BenchSlot(container, SaberAssembly.EMITTER, 10, 42));
		addSlot(new BenchSlot(container, SaberAssembly.GRIP, 10, 64));
		addSlot(new BenchSlot(container, SaberAssembly.POMMEL, 10, 86));
		addSlot(new BenchSlot(container, SaberAssembly.SABER, 40, 53));
		addSlot(new BenchSlot(container, SaberAssembly.SECOND, 40, 20));
		addSlot(new BenchSlot(container, SaberAssembly.LENS, 40, 86));
		addStandardInventorySlots(inventory, 34, 118);
		addDataSlots(data);
		recompute();
	}

	/** Synced value for the screen; see the {@code DATA_*} constants. */
	public int data(int index) {
		return data.get(index);
	}

	public Status status() {
		return Status.byCode(data.get(DATA_STATUS));
	}

	/** Staff mode (5.6), from the bench's slots (synced like any slot, so true on both sides). */
	public boolean isStaffMode() {
		return SaberAssembly.isStaffBuild(container);
	}

	/** Whether the lens slot shows (5.7): Artifice 2, a lens in the player's inventory, or a lens already in it. */
	public boolean lensSlotOpen() {
		if (!container.getItem(SaberAssembly.LENS).isEmpty()) {
			return true;
		}

		int artifice = CraftingAttachments.get(player).progress(Discipline.ARTIFICE).map(Progress::level).orElse(0);

		if (artifice >= LENS_ARTIFICE_LEVEL) {
			return true;
		}

		Inventory inventory = player.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).getItem() instanceof LensItem) {
				return true;
			}
		}

		return player.containerMenu == this && player.containerMenu.getCarried().getItem() instanceof LensItem;
	}

	/** Whether bench slot {@code benchSlot} is shown and usable now. */
	public boolean benchSlotActive(int benchSlot) {
		boolean filled = !container.getItem(benchSlot).isEmpty();

		return switch (benchSlot) {
			case SaberAssembly.SECOND -> filled || isStaffMode();
			case SaberAssembly.LENS -> lensSlotOpen();
			// Staff mode locks the pommel slot; a pommel already in it stays reachable to take out.
			case SaberAssembly.POMMEL -> filled || !isStaffMode();
			default -> true;
		};
	}

	@Override
	public boolean clickMenuButton(Player clicker, int id) {
		if (!(clicker instanceof ServerPlayer serverPlayer)) {
			return false;
		}

		boolean changed;

		if (id >= 0 && id < BladeColor.values().length) {
			changed = selectColor(BladeColor.byIndex(id));
		} else if (id == BUTTON_CONSTRUCT) {
			changed = construct(serverPlayer);
		} else if (id == BUTTON_DISASSEMBLE) {
			changed = disassemble();
		} else if (id >= BUTTON_LENGTH && id <= BUTTON_LENGTH + SaberConfig.LENGTH_LONG) {
			changed = setLength(id - BUTTON_LENGTH);
		} else if (id >= BUTTON_TUNE && id < BUTTON_TUNE + TUNE_TARGETS * TUNE_STRIDE) {
			CrystalTuning tuning = CrystalTuning.byIndex((id - BUTTON_TUNE) % TUNE_STRIDE);
			changed = tuning != null && tune(serverPlayer, (id - BUTTON_TUNE) / TUNE_STRIDE, tuning);
		} else {
			changed = false;
		}

		if (changed) {
			recompute();
		}

		return changed;
	}

	private boolean selectColor(BladeColor newColor) {
		color = newColor;
		colorChosen = true;
		ItemStack saber = container.getItem(SaberAssembly.SABER);

		// Recolouring a finished saber in place: only its owner, only with the part slots empty.
		if (SaberAssembly.status(container, playerId) == Status.CAN_DISASSEMBLE) {
			ItemStack recoloured = saber.copy();
			recoloured.set(SaberComponents.SABER_CONFIG, SaberComponents.config(saber).withColor(newColor));
			rewrite(() -> container.setItem(SaberAssembly.SABER, recoloured));
			playBenchSound(ModSounds.WORKBENCH_SLOT, 1.2f);
		}

		return true;
	}

	/** Whether the bench holds a built single lightsaber whose length the player may set (10.3; staffs stay standard). */
	public boolean canSetLength() {
		ItemStack saber = container.getItem(SaberAssembly.SABER);
		return saber.getItem() == SaberItems.LIGHTSABER && SaberAssembly.ownsSaber(saber, playerId);
	}

	/** The built saber's blade length, {@code SaberConfig.LENGTH_*}, or -1 without a single lightsaber on the bench. */
	public int benchLength() {
		ItemStack saber = container.getItem(SaberAssembly.SABER);
		return saber.getItem() == SaberItems.LIGHTSABER ? SaberComponents.config(saber).length() : -1;
	}

	/** The length switch: free, on the player's own built single lightsaber; the reach component follows. */
	private boolean setLength(int length) {
		if (!canSetLength() || benchLength() == length) {
			return false;
		}

		ItemStack saber = container.getItem(SaberAssembly.SABER).copy();
		saber.set(SaberComponents.SABER_CONFIG, SaberComponents.config(saber).withLength(length));
		SaberToggle.refreshModifiers(saber, false);
		rewrite(() -> container.setItem(SaberAssembly.SABER, saber));
		playBenchSound(ModSounds.WORKBENCH_SLOT, length == SaberConfig.LENGTH_SHORT ? 1.3f : length == SaberConfig.LENGTH_LONG ? 0.8f : 1f);
		return true;
	}

	/**
	 * The stack a Tune target is on: a loose crystal's slot, or the saber slot for the built saber's
	 * crystals. Both sides (the screen reads the synced slots).
	 */
	public ItemStack tuneStack(int target) {
		return switch (target) {
			case TUNE_CRYSTAL -> container.getItem(SaberAssembly.CRYSTAL);
			case TUNE_SECOND -> container.getItem(SaberAssembly.SECOND);
			case TUNE_SABER, TUNE_SABER_SECOND -> SaberAssembly.isBenchSaber(container.getItem(SaberAssembly.SABER)) ? container.getItem(SaberAssembly.SABER) : ItemStack.EMPTY;
			default -> ItemStack.EMPTY;
		};
	}

	/** Whether Tune target {@code target} has a crystal (attuned or not). */
	public boolean hasTuneTarget(int target) {
		ItemStack stack = tuneStack(target);
		return !stack.isEmpty() && (target != TUNE_SABER_SECOND || SaberComponents.config(stack).second().isPresent());
	}

	/** The crystal of Tune target {@code target}, or null (none, or an unattuned singing crystal). */
	public @Nullable CrystalAttunement tuneCrystal(int target) {
		ItemStack stack = tuneStack(target);

		if (stack.isEmpty()) {
			return null;
		}

		return switch (target) {
			case TUNE_CRYSTAL, TUNE_SECOND -> stack.get(ModComponents.CRYSTAL_ATTUNEMENT);
			case TUNE_SABER -> SaberComponents.config(stack).crystal();
			case TUNE_SABER_SECOND -> SaberComponents.config(stack).second().orElse(null);
			default -> null;
		};
	}

	/**
	 * Tunes target {@code target} (10.4): the player's own attuned crystal, for {@code tuneCredits}
	 * and the material; the old tuning goes without a refund. Refusals say why on the action bar.
	 */
	private boolean tune(ServerPlayer serverPlayer, int target, CrystalTuning tuning) {
		SaberBalance b = SaberToggle.balance();
		ItemStack stack = tuneStack(target);
		CrystalAttunement crystal = tuneCrystal(target);
		SaberTunings.Result result;

		if (!hasTuneTarget(target)) {
			result = SaberTunings.Result.NO_CRYSTAL;
		} else if (crystal == null) {
			result = SaberTunings.Result.UNATTUNED;
		} else if ((target == TUNE_SABER || target == TUNE_SABER_SECOND) && !SaberAssembly.ownsSaber(stack, playerId)) {
			result = SaberTunings.Result.NOT_YOURS;
		} else {
			result = SaberTunings.check(serverPlayer, crystal, tuning, b);
		}

		if (result != SaberTunings.Result.TUNED || !SaberTunings.pay(serverPlayer, tuning, b)) {
			serverPlayer.sendOverlayMessage(Component.translatable(result == SaberTunings.Result.TUNED ? SaberTunings.Result.NO_MATERIALS.messageKey() : result.messageKey()));
			return false;
		}

		int slot = target == TUNE_CRYSTAL ? SaberAssembly.CRYSTAL : target == TUNE_SECOND ? SaberAssembly.SECOND : SaberAssembly.SABER;
		ItemStack tuned = slot == SaberAssembly.SABER ? SaberTunings.retunedSaber(stack, target == TUNE_SABER_SECOND, tuning) : SaberTunings.retunedCrystal(stack, tuning);
		rewrite(() -> container.setItem(slot, tuned));
		access.execute((level, pos) -> EffectEvents.toPlayer(serverPlayer, SaberEffects.CRYSTAL_TUNE, Vec3.atCenterOf(pos).add(0, 0.6, 0), new Vec3(0, 1, 0),
				serverPlayer.getId(), target, tuning.ordinal()));
		return true;
	}

	private boolean construct(ServerPlayer serverPlayer) {
		ItemStack[] saber = new ItemStack[1];
		String name = serverPlayer.getPlainTextName();
		rewrite(() -> saber[0] = SaberAssembly.assemble(container, playerId, name, color, SaberToggle.balance()));

		if (saber[0].isEmpty()) {
			return false;
		}

		access.execute((level, pos) -> EffectEvents.around((ServerLevel) level, SaberEffects.WORKBENCH_ASSEMBLE,
				Vec3.atCenterOf(pos).add(0, 0.6, 0), new Vec3(0, 1, 0), serverPlayer.getId(), 1f, color.ordinal()));
		QuestHooks.onSaberConstructed(serverPlayer);
		return true;
	}

	private boolean disassemble() {
		SaberConfig[] config = new SaberConfig[1];
		rewrite(() -> config[0] = SaberAssembly.disassemble(container, playerId));

		if (config[0] == null) {
			return false;
		}

		color = config[0].color();
		colorChosen = true;
		playBenchSound(ModSounds.WORKBENCH_SLOT, 0.8f);
		return true;
	}

	private void rewrite(Runnable change) {
		rewriting = true;

		try {
			change.run();
		} finally {
			rewriting = false;
		}
	}

	@Override
	public void slotsChanged(Container changed) {
		super.slotsChanged(changed);

		if (!(player instanceof ServerPlayer)) {
			return;
		}

		boolean placed = false;
		boolean lensFitted = false;

		for (int slot = 0; slot < SaberAssembly.SIZE; slot++) {
			boolean now = !container.getItem(slot).isEmpty();
			placed |= now && !filled[slot];
			lensFitted |= slot == SaberAssembly.LENS && now && !filled[slot];
			filled[slot] = now;
		}

		if (lensFitted && !rewriting && container.getItem(SaberAssembly.LENS).getItem() instanceof LensItem lens && player instanceof ServerPlayer serverPlayer) {
			// The lens seats with a glint: to the player only, the variant naming the lens.
			access.execute((level, pos) -> EffectEvents.toPlayer(serverPlayer, SaberEffects.LENS_FIT, Vec3.atCenterOf(pos).add(0, 0.6, 0), new Vec3(0, 1, 0),
					serverPlayer.getId(), 1f, lens.lens().ordinal()));
		}

		ItemStack crystal = container.getItem(SaberAssembly.CRYSTAL);
		CrystalAttunement attunement = crystal.get(ModComponents.CRYSTAL_ATTUNEMENT);

		if (!colorChosen && attunement != null) {
			color = BladeColor.nearest(attunement.color());
		} else if (!colorChosen && SaberAssembly.isSingingCrystal(crystal)) {
			// An unattuned singing crystal: its natural hue (spec 8.8).
			color = BladeColor.AMBER;
		}

		ItemStack saber = container.getItem(SaberAssembly.SABER);

		if (!saber.isEmpty()) {
			if (!rewriting) {
				color = SaberComponents.config(saber).color();
			}

			// A blade never stays lit on the bench.
			if (SaberComponents.isIgnited(saber)) {
				access.execute((level, pos) -> SaberToggle.setIgnited(saber, false, level.getGameTime()));
			}
		}

		if (placed && !rewriting && !lensFitted) {
			playBenchSound(ModSounds.WORKBENCH_SLOT, 1f);
		}

		recompute();
	}

	/** Recomputes the synced status and stat preview from the server's balance values. */
	private void recompute() {
		if (!(player instanceof ServerPlayer)) {
			return;
		}

		data.set(DATA_COLOR, color.ordinal());
		data.set(DATA_STATUS, SaberAssembly.status(container, playerId).ordinal());
		SaberStats stats = SaberAssembly.preview(container, SaberToggle.balance());
		data.set(DATA_HAS_PREVIEW, stats != null ? 1 : 0);

		if (stats != null) {
			data.set(DATA_DAMAGE_X100, (int) Math.round(stats.damage() * 100));
			data.set(DATA_SPEED_X100, (int) Math.round(stats.attackSpeed() * 100));
			data.set(DATA_REACH_X100, (int) Math.round(stats.reachBonus() * 100));
			data.set(DATA_STAMINA, Math.round(stats.maxStamina()));
			data.set(DATA_PARRY_X10, Math.round(stats.parryTicks() * 10));
		}
	}

	private void playBenchSound(SoundEvent sound, float pitch) {
		access.execute((level, pos) -> level.playSound(null, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, 0.8f, pitch));
	}

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

	/** Shift-click into the first empty bench slot that accepts the item. */
	private boolean moveIntoBench(ItemStack stack) {
		for (int i = 0; i < SaberAssembly.SIZE; i++) {
			Slot target = slots.get(i);

			if (!target.hasItem() && target.mayPlace(stack)) {
				return moveItemStackTo(stack, i, i + 1, false);
			}
		}

		return false;
	}

	@Override
	public void removed(Player closer) {
		super.removed(closer);
		access.execute((level, pos) -> clearContainer(closer, container));
	}

	@Override
	public boolean stillValid(Player viewer) {
		return stillValid(access, viewer, SaberItems.WORKBENCH);
	}

	/** One item per slot, and only what {@link SaberAssembly#mayPlace} allows for this player. */
	private final class BenchSlot extends Slot {
		private final int benchSlot;

		BenchSlot(Container benchContainer, int benchSlot, int x, int y) {
			super(benchContainer, benchSlot, x, y);
			this.benchSlot = benchSlot;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			if (!benchSlotActive(benchSlot) || (benchSlot == SaberAssembly.SECOND && !isStaffMode())) {
				return false;
			}

			return SaberAssembly.mayPlace(container, benchSlot, stack, playerId);
		}

		@Override
		public boolean isActive() {
			return benchSlotActive(benchSlot);
		}

		@Override
		public int getMaxStackSize() {
			return 1;
		}
	}
}
