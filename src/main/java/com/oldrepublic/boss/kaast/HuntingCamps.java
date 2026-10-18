package com.oldrepublic.boss.kaast;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.Story;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Torvin Kaast's hunting camp (spec MILESTONE_4.md 3.4, 8.6, 10.5): W1's {@code boss/hunting_camp}
 * template, one per owner, placed through {@link PlanetSites} on the owner's path world (Selvaren for
 * Jedi, Varkhast for Sith; an Independent's is on the planet they stand on), and fitted with its
 * {@code camp_gate} in the {@code GATE} opening.
 */
public final class HuntingCamps {
	/**
	 * Places the owner's hunting camp on their path world through {@code PlanetSites}, once. With
	 * {@code kaast.placeCamp} false only an existing camp is returned (an operator places it).
	 *
	 * @return the camp's anchor, or null when it is not placed yet (the caller retries)
	 */
	public static @Nullable GlobalPos place(ServerPlayer owner) {
		return place(owner, KaastBalance.get().placeCamp);
	}

	/** {@link #place(ServerPlayer)} that may place a new camp only when {@code mayPlace} (the command always may). */
	public static @Nullable GlobalPos place(ServerPlayer owner, boolean mayPlace) {
		GlobalPos found = PlanetSites.find(owner, PlanetSites.Site.HUNTING_CAMP);

		if (found != null || !mayPlace) {
			return found;
		}

		Planets.Planet planet = ChapterFourRules.pathWorld(Story.get(owner).storyPath());

		if (planet == null) {
			planet = Planets.Planet.of(owner.level().dimension());
		}

		if (planet == null || planet == Planets.Planet.HOME) {
			return null;
		}

		GlobalPos camp = PlanetSites.ensure(owner, PlanetSites.Site.HUNTING_CAMP, planet);

		if (camp != null && owner.level().dimension().equals(camp.dimension())) {
			ServerLevel level = owner.level();
			ensureGate(level, CampLayout.of(level, camp.pos(), KaastBalance.get().arenaRadius));
		}

		return camp;
	}

	/** Puts the gate's blocks (open) into the camp's {@code GATE} opening where it is still air. */
	static void ensureGate(ServerLevel level, CampLayout layout) {
		BlockState open = KaastBlocks.CAMP_GATE.defaultBlockState().setValue(CampGateBlock.OPEN, true);

		for (BlockPos pos : layout.gates()) {
			if (level.hasChunkAt(pos) && level.getBlockState(pos).canBeReplaced()) {
				level.setBlockAndUpdate(pos, open.setValue(CampGateBlock.TOP, !layout.gates().contains(pos.above())));
			}
		}
	}

	private HuntingCamps() {
	}
}
