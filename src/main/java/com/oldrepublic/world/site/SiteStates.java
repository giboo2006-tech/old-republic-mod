package com.oldrepublic.world.site;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.identity.Faction;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Block state properties of the three site blocks (W1 plan 4.3): {@code echo_cairn} (style, veil),
 * {@code bounty_terminal} (faction, facing) and {@code site_spawner} (none). The names and values
 * are what the structure generator, the blockstate JSON and the code all use, so they are frozen.
 */
public final class SiteStates {
	/** What a revealed echo cairn looks like: a small stacked-stone cairn or a half-buried satchel. */
	public enum CairnStyle implements StringRepresentable {
		CAIRN("cairn"),
		CACHE("cache");

		private final String name;

		CairnStyle(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** Whether an echo cairn shows: VEILED has no model, collision or occlusion; REVEALED lasts {@code sites.cairnRevealTicks}. */
	public enum CairnVeil implements StringRepresentable {
		VEILED("veiled"),
		REVEALED("revealed");

		private final String name;

		CairnVeil(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** The army a bounty terminal (and its outpost) belongs to. Sent as an ordinal in {@code terminal_use}, so only append. */
	public enum Army implements StringRepresentable {
		REPUBLIC("republic", Faction.REPUBLIC),
		IMPERIAL("imperial", Faction.SITH_EMPIRE);

		private final String name;
		private final Faction faction;

		Army(String name, Faction faction) {
			this.name = name;
			this.faction = faction;
		}

		/** The faction that issues this army's missions and owns its troopers. */
		public Faction faction() {
			return faction;
		}

		/** The army of a faction, or null for factions without an army (the Jedi Order, the Frontier). */
		public static @Nullable Army of(Faction faction) {
			for (Army army : values()) {
				if (army.faction == faction) {
					return army;
				}
			}

			return null;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final EnumProperty<CairnStyle> STYLE = EnumProperty.create("style", CairnStyle.class);
	/** The cairn's veil; a separate property object from the puzzle kit's {@code veil}. */
	public static final EnumProperty<CairnVeil> VEIL = EnumProperty.create("veil", CairnVeil.class);
	public static final EnumProperty<Army> FACTION = EnumProperty.create("faction", Army.class);
	/** The bounty terminal's screen side. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	private SiteStates() {
	}
}
