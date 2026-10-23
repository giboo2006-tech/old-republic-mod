package com.oldrepublic.world.puzzle;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A holocron pedestal's data (spec 4.3, NBT 4.8): {@code fixed_id}, the one holocron a story
 * pedestal always gives (written by W2's Courier's Fall generator, read by W3's pedestal flow);
 * absent or empty for every worldgen pedestal, which choose by kind.
 */
public class HolocronPedestalBlockEntity extends BlockEntity {
	private static final String KEY_FIXED_ID = "fixed_id";

	private String fixedId = "";
	/** Idle steps without a player near while ready; transient (a reload only delays a reset). */
	private int idleAge;

	public HolocronPedestalBlockEntity(BlockPos pos, BlockState state) {
		super(PuzzleBlocks.HOLOCRON_PEDESTAL_ENTITY, pos, state);
	}

	/** The holocron id this pedestal always gives, or null for a pedestal that chooses by kind. */
	public @Nullable String fixedId() {
		return fixedId.isEmpty() ? null : fixedId;
	}

	/** Sets (or, with null or "", clears) the fixed holocron id. */
	public void setFixedId(@Nullable String id) {
		fixedId = id == null ? "" : id;
		setChanged();
	}

	/** One idle step of a ready pedestal. Returns true when it resets now. */
	boolean idleStep(boolean playerNear, SiteBalance b) {
		PuzzleRules.ResetStep step = PuzzleRules.resetStep(idleAge, playerNear, b);
		idleAge = step.reset() ? 0 : step.age();
		return step.reset();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		fixedId = input.getStringOr(KEY_FIXED_ID, "");
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);

		if (!fixedId.isEmpty()) {
			output.putString(KEY_FIXED_ID, fixedId);
		}
	}
}
