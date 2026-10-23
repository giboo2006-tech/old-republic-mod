package com.oldrepublic.enemy.hymn;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.world.site.SpawnerGroups;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Where the Hymnsworn come from (spec MILESTONE_5.md 3.3.2, 12.1): the spawner group {@code hymnsworn}
 * (verse stones with 3, 4 or 5 guards, a group of 5 with a storm-caller; Hymn shrines; the Hymnal's
 * processional), and the spawns of Ulvaine's fight and the raid.
 */
public final class Hymnsworns {
	/** The spawner group id. */
	public static final String GROUP = "hymnsworn";
	/** A group spawner whose data says no count spawns this many. */
	public static final int DEFAULT_SIZE = 3;
	/** A group this large brings a storm-caller (the third verse stone's five, 3.3.2). */
	public static final int CALLER_FROM = 5;

	/** Registers the group. Called from {@code FoesM5Feature}. */
	public static void init() {
		SpawnerGroups.register(GROUP, new SpawnerGroups.Group() {
			@Override
			public int size(int count, BlockPos pos) {
				return count > 0 ? count : DEFAULT_SIZE;
			}

			@Override
			public List<LivingEntity> spawn(ServerLevel level, BlockPos at, int size, boolean bound, @Nullable UUID owner) {
				return new ArrayList<>(spawnGroup(level, at, size, owner));
			}
		});
	}

	/**
	 * The kinds of a group of {@code size}: a storm-caller first in a group of five or more, then a
	 * chanter for every third member and blades for the rest (3: chanter and two blades; 4: two of
	 * each; 5: a caller, two chanters, two blades).
	 */
	public static List<Hymnsworn.Variant> group(int size) {
		List<Hymnsworn.Variant> kinds = new ArrayList<>();
		int rest = size;

		if (size >= CALLER_FROM) {
			kinds.add(Hymnsworn.Variant.CALLER);
			rest--;
		}

		for (int i = 0; i < rest; i++) {
			kinds.add(i % 3 == 0 ? Hymnsworn.Variant.CHANTER : Hymnsworn.Variant.BLADE);
		}

		return kinds;
	}

	/** Spawns a group of {@code size} ({@link #group}) around {@code at}, owned when {@code owner} is set (a per-player verse stone). */
	public static List<Hymnsworn> spawnGroup(ServerLevel level, BlockPos at, int size, @Nullable UUID owner) {
		List<Hymnsworn.Variant> kinds = group(Math.max(0, size));
		List<Hymnsworn> out = new ArrayList<>(kinds.size());

		for (int i = 0; i < kinds.size(); i++) {
			Hymnsworn.Variant kind = kinds.get(i);
			Hymnsworn h = FoeSpawns.spawn(level, HymnEntities.HYMNSWORN, at, i, kinds.size(), owner, e -> e.setVariant(kind));

			if (h != null) {
				out.add(h);
			}
		}

		return out;
	}

	/** One Hymnsworn of {@code kind} at {@code at} (a pylon's chanter, a raid shade when {@code shade}); null when refused. */
	public static @Nullable Hymnsworn spawn(ServerLevel level, BlockPos at, Hymnsworn.Variant kind, boolean shade, @Nullable UUID owner) {
		return FoeSpawns.spawn(level, HymnEntities.HYMNSWORN, at, 0, 1, owner, e -> {
			e.setVariant(kind);
			e.setShade(shade);
		});
	}

	/**
	 * A boss fight's minion of {@code kind} at {@code at}, the {@code index}-th of {@code size} (placed
	 * around the spot): never saved, since its fight ends with a restart and brings its own (Ulvaine's
	 * pylon chanters, the Long Hymn's shades when {@code shade}). Null when refused.
	 */
	public static @Nullable Hymnsworn summon(ServerLevel level, BlockPos at, int index, int size, Hymnsworn.Variant kind, boolean shade,
			@Nullable UUID owner) {
		return FoeSpawns.spawn(level, HymnEntities.HYMNSWORN, at, index, Math.max(1, size), owner, e -> {
			e.setSummoned(true);
			e.setVariant(kind);
			e.setShade(shade);
		});
	}

	private Hymnsworns() {
	}
}
