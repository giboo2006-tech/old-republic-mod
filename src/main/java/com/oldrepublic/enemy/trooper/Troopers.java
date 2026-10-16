package com.oldrepublic.enemy.trooper;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.identity.Faction;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Republic and Imperial troopers for other features (spec section 7): squads, officers, downed
 * troopers and escorts, and the questions other features ask about a trooper. {@code army} is
 * {@link Faction#REPUBLIC} or {@link Faction#SITH_EMPIRE}. Signatures are frozen (spec section 15.1).
 */
public final class Troopers {
	/**
	 * Spawns a squad with one trooper per role around {@code at}, under a fresh squad UUID, owned by
	 * {@code owner} when not null and carrying {@code incident}. Phase 0 stub: W4 fills this in.
	 */
	public static List<LivingEntity> spawnSquad(ServerLevel l, BlockPos at, Faction army, List<TrooperRole> roles, @Nullable UUID owner, IncidentMode incident) {
		return List.of();
	}

	/**
	 * Spawns an outpost officer; the name key is {@code npc.oldrepublic.officer.<republic|imperial>.<nameSeed>}.
	 * Phase 0 stub: W4 fills this in.
	 */
	public static LivingEntity spawnOfficer(ServerLevel l, BlockPos at, Faction army, int nameSeed) {
		return placeholder(l, at, "officer " + army.id() + " " + nameSeed);
	}

	/** Spawns a downed trooper that {@code owner} can tend (5.4). Phase 0 stub: W4 fills this in. */
	public static LivingEntity spawnDowned(ServerLevel l, BlockPos at, Faction army, UUID owner) {
		return placeholder(l, at, "downed " + army.id());
	}

	/**
	 * Spawns two unsaved troopers who walk {@code prisoner} from {@code at} to {@code exit}. Phase 0
	 * stub: W4 fills this in.
	 */
	public static List<LivingEntity> spawnEscort(ServerLevel l, BlockPos at, Faction army, LivingEntity prisoner, BlockPos exit) {
		return List.of();
	}

	/** Whether the entity is a Republic or Imperial trooper. Phase 0 stub: W4 fills this in. */
	public static boolean isTrooper(Entity e) {
		return false;
	}

	/** The trooper's role; {@link TrooperRole#RIFLEMAN} for anything else. Phase 0 stub: W4 fills this in. */
	public static TrooperRole role(Entity e) {
		return TrooperRole.RIFLEMAN;
	}

	/** The trooper's army, or null when it is not a trooper. Phase 0 stub: W4 fills this in. */
	public static @Nullable Faction faction(Entity e) {
		return null;
	}

	/** Whether the entity is a downed trooper waiting to be tended. Phase 0 stub: W4 fills this in. */
	public static boolean isDowned(Entity e) {
		return false;
	}

	/**
	 * One kneel tick of tending ({@code instant} false) or a Force Heal revive ({@code instant} true);
	 * true once the trooper stands. Phase 0 stub: W4 fills this in.
	 */
	public static boolean tend(ServerPlayer p, Entity downed, boolean instant) {
		return false;
	}

	/** The Phase 0 stand-in for a spawned trooper: an invulnerable armour stand named after the request. */
	private static LivingEntity placeholder(ServerLevel l, BlockPos at, String request) {
		ArmorStand stand = new ArmorStand(l, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		stand.setInvulnerable(true);
		stand.setCustomName(Component.literal("Trooper stub: " + request));
		stand.setCustomNameVisible(true);
		l.addFreshEntity(stand);
		return stand;
	}

	private Troopers() {
	}
}
