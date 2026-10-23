package com.oldrepublic.planet.block;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.HubSpawners;
import com.oldrepublic.planet.PlanetBalance;
import com.oldrepublic.planet.PlanetBlocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.world.site.SiteChecks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * A hub spawner's data: its {@code role}, the {@code person} it last spawned and when it last
 * spawned ({@code checked}, overworld game time). Every {@link #INTERVAL} ticks, once the
 * loading rule holds and a player is within {@link #RANGE}: it spawns its person the first time,
 * and later again only when that person is dead or gone and {@code planets.hubRestoreTicks} (one
 * in-game day) have passed since the last spawn (spec MILESTONE_4.md 3.5).
 */
public class HubSpawnerBlockEntity extends BlockEntity {
	public static final int INTERVAL = 20;
	/** A player this close wakes the spawner. */
	public static final double RANGE = 64;

	private String role = "";
	private @Nullable UUID person;
	private long checked = Long.MIN_VALUE;

	public HubSpawnerBlockEntity(BlockPos pos, BlockState state) {
		super(PlanetBlocks.HUB_SPAWNER_ENTITY, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, HubSpawnerBlockEntity spawner) {
		if (level instanceof ServerLevel server && (server.getGameTime() + Math.floorMod(pos.asLong(), INTERVAL)) % INTERVAL == 0
				&& SiteChecks.entitiesReady(server, pos, 1) && SiteChecks.playerWithin(server, Vec3.atCenterOf(pos), RANGE)) {
			spawner.check(server, Clocks.time(server.getServer()));
		}
	}

	/** One check at overworld time {@code now}; returns the person spawned, or null when none was. */
	public @Nullable Entity check(ServerLevel level, long now) {
		if (person != null) {
			Entity current = level.getEntity(person);

			if (current != null && current.isAlive()) {
				return null;
			}
		}

		if (!due(now)) {
			return null;
		}

		Planets.Planet planet = Planets.Planet.of(level.dimension());
		checked = now;
		setChanged();

		if (planet == null || role.isEmpty()) {
			return null;
		}

		Entity spawned = HubSpawners.spawn(level, getBlockPos(), planet, role);

		if (spawned instanceof Mob mob) {
			mob.setPersistenceRequired();
		}

		person = spawned == null ? null : spawned.getUUID();
		return spawned;
	}

	/** Whether a spawn may happen at {@code now}: never spawned yet, or a full restore interval since the last one. */
	public boolean due(long now) {
		return checked == Long.MIN_VALUE || now - checked >= PlanetBalance.get().hubRestoreTicks;
	}

	public String role() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
		setChanged();
	}

	public @Nullable UUID person() {
		return person;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		role = input.getStringOr("role", "");
		person = input.read("person", UUIDUtil.CODEC).orElse(null);
		checked = input.getLongOr("checked", Long.MIN_VALUE);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putString("role", role);
		output.storeNullable("person", UUIDUtil.CODEC, person);
		output.putLong("checked", checked);
	}
}
