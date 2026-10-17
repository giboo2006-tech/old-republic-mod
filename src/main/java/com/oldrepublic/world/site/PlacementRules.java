package com.oldrepublic.world.site;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryBalance;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * The shared parts of W1's runtime placement rules (spec 3.5, 3.6): spawn protection and the other
 * players' sites a new per-player site keeps away from. They feed {@code MeetingSites.Rules} for
 * {@link RuntimeSites}.
 */
public final class PlacementRules {
	/**
	 * Stand positions inside spawn protection, when {@code story.respectSpawnProtection} asks for it
	 * (overworld only): the protected square of {@code story.spawnProtectionRadius} around world
	 * spawn, widened by half the footprint so no part of the site lies in it (the M2 rule).
	 */
	public static Predicate<BlockPos> spawnProtection(ServerLevel level, int footprint) {
		StoryBalance b = StoryService.balance();

		if (!b.respectSpawnProtection || level.dimension() != Level.OVERWORLD || b.spawnProtectionRadius <= 0) {
			return pos -> false;
		}

		BlockPos spawn = level.getRespawnData().pos();
		int reach = b.spawnProtectionRadius + footprint / 2 + 1;
		return pos -> Math.abs(pos.getX() - spawn.getX()) <= reach && Math.abs(pos.getZ() - spawn.getZ()) <= reach;
	}

	/**
	 * The sites of other players that a new site of {@code player} keeps {@code story.siteMinSeparation}
	 * from (3.6): the other online players' M2 story sites, and every runtime site in this dimension
	 * that someone else owns.
	 */
	public static List<BlockPos> otherPlayersSites(ServerPlayer player) {
		List<BlockPos> sites = new ArrayList<>();
		UUID self = player.getUUID();

		for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
			if (other == player) {
				continue;
			}

			StoryData data = Story.get(other);
			addIfPresent(sites, data.meeting);
			addIfPresent(sites, data.camp);
			addIfPresent(sites, data.encounterPos);
		}

		for (WorldSites.SiteRecord record : WorldSites.all(player.level().getServer())) {
			if (record.owner() != null && !record.owner().equals(self) && record.dimension().equals(player.level().dimension())) {
				sites.add(record.origin());
			}
		}

		return sites;
	}

	private static void addIfPresent(List<BlockPos> sites, @Nullable BlockPos pos) {
		if (pos != null && !sites.contains(pos)) {
			sites.add(pos);
		}
	}

	private PlacementRules() {
	}
}
