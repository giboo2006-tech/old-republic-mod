package com.oldrepublic.world.trial;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** One running Trial of Reflection. In memory only; see {@link TrialManager}. */
final class TrialSession {
	enum Phase {
		/** The chamber dims and the echo gathers. */
		FORMING,
		/** The echo fights. */
		FIGHT,
		/** The echo is defeated; the crystal forms after a short delay. */
		VICTORY
	}

	final UUID owner;
	ServerPlayer player;
	final ServerLevel level;
	final BlockPos stone;
	final Vec3 echoSpawn;
	/** Started by an operator command: requirements were bypassed. */
	final boolean forced;
	Phase phase = Phase.FORMING;
	int age;
	int phaseTicks;
	@Nullable UUID echoId;

	TrialSession(ServerPlayer player, ServerLevel level, BlockPos stone, Vec3 echoSpawn, boolean forced) {
		this.owner = player.getUUID();
		this.player = player;
		this.level = level;
		this.stone = stone.immutable();
		this.echoSpawn = echoSpawn;
		this.forced = forced;
	}

	Vec3 stoneTop() {
		return new Vec3(stone.getX() + 0.5, stone.getY() + 1.0, stone.getZ() + 0.5);
	}

	void setPhase(Phase next) {
		phase = next;
		phaseTicks = 0;
	}
}
