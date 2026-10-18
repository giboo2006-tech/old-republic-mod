package com.oldrepublic.crafting.bench;

import java.util.ArrayList;
import java.util.List;

import com.oldrepublic.crafting.CraftingBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The bench's saved state: the 9-slot parts tray (7.1). Every menu open on the bench shares this
 * container, as with a chest; breaking the bench drops what the tray holds.
 */
public class EngineeringBenchBlockEntity extends BlockEntity {
	public static final int TRAY_SIZE = 9;
	private static final String KEY_TRAY = "tray";

	private final SimpleContainer tray = new SimpleContainer(TRAY_SIZE) {
		@Override
		public void setChanged() {
			super.setChanged();
			EngineeringBenchBlockEntity.this.setChanged();
		}
	};

	public EngineeringBenchBlockEntity(BlockPos pos, BlockState state) {
		super(CraftingBlocks.ENGINEERING_BENCH_ENTITY, pos, state);
	}

	/** The parts tray (live). */
	public SimpleContainer tray() {
		return tray;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		List<ItemStack> saved = input.read(KEY_TRAY, ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());

		for (int i = 0; i < TRAY_SIZE; i++) {
			tray.setItem(i, i < saved.size() ? saved.get(i) : ItemStack.EMPTY);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		List<ItemStack> items = new ArrayList<>(TRAY_SIZE);

		for (int i = 0; i < TRAY_SIZE; i++) {
			items.add(tray.getItem(i));
		}

		output.store(KEY_TRAY, ItemStack.OPTIONAL_CODEC.listOf(), items);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);

		if (level != null) {
			for (int i = 0; i < TRAY_SIZE; i++) {
				Block.popResource(level, pos, tray.removeItemNoUpdate(i));
			}
		}
	}
}
