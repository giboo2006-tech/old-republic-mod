package com.oldrepublic.quest.story;

import java.util.List;
import java.util.function.Consumer;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.force.sense.SenseTargets;
import com.oldrepublic.identity.Path;
import com.oldrepublic.world.EchoCairns;
import com.oldrepublic.world.site.SiteBalance;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * W2's Force Sense provider (spec 10.1): during LISTENING the caster's own untouched cairns (or the
 * Independent's caches) within {@code chapter_three.cairnSenseRange} are revealed through
 * {@link EchoCairns#reveal} for {@code sites.cairnRevealTicks} and drawn as points ({@code BLOCK}
 * for a cairn, {@code CACHE} for a cache). Reads the saved positions only: no block scan.
 */
final class CairnSense implements SenseTargets.Provider {
	@Override
	public void collect(ServerPlayer caster, int blockRange, int creatureRange, Consumer<SenseTargets.Point> out) {
		StoryData data = Story.get(caster);
		StoryData.Chapter3 c = data.chapter3;

		if (c.stage() != ChapterThreeStage.LISTENING || !StoryService.inOverworld(caster)) {
			return;
		}

		ServerLevel level = caster.level();
		boolean caches = data.storyPath() == Path.INDEPENDENT;
		int range = ChapterThreeService.balance().cairnSenseRange;
		int ticks = SiteBalance.get().cairnRevealTicks;
		List<BlockPos> cairns = c.cairns();

		for (int i = 0; i < cairns.size(); i++) {
			BlockPos pos = cairns.get(i);

			if ((c.cairnsTouched() & (1 << i)) != 0 || caster.position().distanceToSqr(Vec3.atCenterOf(pos)) > (double) range * range) {
				continue;
			}

			EchoCairns.reveal(level, pos, caster.getUUID(), ticks);
			out.accept(new SenseTargets.Point(caches ? SenseTargets.Kind.CACHE : SenseTargets.Kind.BLOCK, Vec3.atCenterOf(pos), -1, Attitude.NEUTRAL));
		}
	}
}
