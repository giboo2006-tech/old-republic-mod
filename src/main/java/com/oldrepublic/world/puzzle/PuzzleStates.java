package com.oldrepublic.world.puzzle;

import com.oldrepublic.holocron.HolocronKind;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Block state properties of the puzzle kit (spec 3.3). Names and values are frozen: the structure
 * generator, the blockstate JSON and the code all use them. Enum values are stored by name in the
 * world, so only append.
 */
public final class PuzzleStates {
	/** Which site's stone a puzzle block is dressed as. */
	public enum Skin implements StringRepresentable {
		RUIN("ruin"),
		TOMB("tomb"),
		WRECK("wreck");

		private final String name;

		Skin(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** A veiled stone's state: hidden, shown by Sense, or opened. */
	public enum Veil implements StringRepresentable {
		VEILED("veiled"),
		REVEALED("revealed"),
		OPEN("open");

		private final String name;

		Veil(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** Which holocron a pedestal holds. */
	public enum PedestalKind implements StringRepresentable {
		JEDI("jedi", HolocronKind.JEDI),
		SITH("sith", HolocronKind.SITH),
		WAYFARER("wayfarer", HolocronKind.WAYFARER);

		private final String name;
		private final HolocronKind holocron;

		PedestalKind(String name, HolocronKind holocron) {
			this.name = name;
			this.holocron = holocron;
		}

		/** The holocron kind this pedestal gives. */
		public HolocronKind holocron() {
			return holocron;
		}

		/** The pedestal kind of a holocron kind. */
		public static PedestalKind of(HolocronKind kind) {
			return switch (kind) {
				case JEDI -> JEDI;
				case SITH -> SITH;
				case WAYFARER -> WAYFARER;
			};
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** A tomb vent's cycle: resting, the tell, the puff. */
	public enum VentPhase implements StringRepresentable {
		IDLE("idle"),
		TELL("tell"),
		PUFF("puff");

		private final String name;

		VentPhase(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final EnumProperty<Skin> SKIN = EnumProperty.create("skin", Skin.class);
	public static final EnumProperty<Veil> VEIL = EnumProperty.create("veil", Veil.class);
	public static final EnumProperty<PedestalKind> KIND = EnumProperty.create("kind", PedestalKind.class);
	public static final EnumProperty<VentPhase> PHASE = EnumProperty.create("phase", VentPhase.class);

	/** Idle age of the resettable blocks (one step per {@code sites.puzzleTickInterval}); also a short timeline counter. */
	// API-CHECK: IntegerProperty.create(String, int, int) (only the BlockStateProperties constants are in the dumps)
	public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 6);
	/** Pull anchor: 0 armed, 1 straining, 2 released. */
	public static final IntegerProperty STRAIN = IntegerProperty.create("strain", 0, 2);
	/** Echo urn decor index (which carving it shows). */
	public static final IntegerProperty INDEX = IntegerProperty.create("index", 0, 3);

	public static final BooleanProperty SUNK = BooleanProperty.create("sunk");
	public static final BooleanProperty EXTENDED = BooleanProperty.create("extended");
	public static final BooleanProperty SOLVED = BooleanProperty.create("solved");
	public static final BooleanProperty CLEARED = BooleanProperty.create("cleared");
	public static final BooleanProperty OPEN = BooleanProperty.create("open");
	public static final BooleanProperty READY = BooleanProperty.create("ready");
	public static final BooleanProperty ALWAYS_READY = BooleanProperty.create("always_ready");

	/** Horizontal facing; what it means is per block (see each block's Javadoc). */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	/** Highest {@link #AGE}. */
	public static final int MAX_AGE = 6;

	private PuzzleStates() {
	}
}
