package com.oldrepublic.quest.turning;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.Guidance;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.TurningKind;
import com.oldrepublic.quest.story.TurningStage;
import com.oldrepublic.travel.Ships;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The journal's Calling during a turning's task (spec MILESTONE_5.md 8.3), just under the main story's priority: the
 * Fall points to the Greenwatch (Ansa), then to the Proving (Kaddor); the Return to the detention post, then to the
 * player's ship, then to the Greenwatch's pads. The confrontations and the rites need no Calling: the mentors come.
 */
final class TurningGuidance {
	/** Just under the story's own requests. */
	static final int PRIORITY = Guidance.STORY - 10;
	private static final String SOURCE = "turning";

	/** The turning's request for the player now, or null. */
	static Guidance.@Nullable Request request(ServerPlayer p) {
		StoryData.Turning t = Story.get(p).turning;

		if (t.stage() != TurningStage.TASK) {
			return null;
		}

		MinecraftServer server = p.level().getServer();

		if (t.kind() == TurningKind.FALL) {
			return hub(server, t.task() == TurningRules.FALL_CARRYING ? Planets.Planet.VARKHAST : Planets.Planet.SELVAREN);
		}

		if (t.kind() != TurningKind.RETURN) {
			return null;
		}

		return switch (TurningRules.phase(t.task())) {
			case TurningRules.RETURN_HELD -> {
				GlobalPos post = ReturnTask.guidancePost(p);
				yield post == null ? null : new Guidance.Request(PRIORITY, Vec3.atCenterOf(post.pos()), QuestEffects.CALLING_FOUND, SOURCE, post.dimension());
			}
			case TurningRules.RETURN_ESCORT -> {
				Entity ship = Ships.body(server, p.getUUID());
				yield ship == null ? null : new Guidance.Request(PRIORITY, ship.position(), QuestEffects.CALLING_FOUND, SOURCE, ship.level().dimension());
			}
			case TurningRules.RETURN_ABOARD -> hub(server, Planets.Planet.SELVAREN);
			default -> null;
		};
	}

	private static Guidance.@Nullable Request hub(MinecraftServer server, Planets.Planet planet) {
		BlockPos hub = PlanetHubs.hub(server, planet);
		return hub == null ? null : new Guidance.Request(PRIORITY, Vec3.atCenterOf(hub), QuestEffects.CALLING_FOUND, SOURCE, planet.level());
	}

	private TurningGuidance() {
	}
}
