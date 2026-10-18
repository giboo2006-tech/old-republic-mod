package com.oldrepublic.enemy.mandalorian;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityLocks;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.barrier.ForceBarrier;
import com.oldrepublic.force.speed.ForceSpeed;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Players caught in a {@link HunterNet} (spec MILESTONE_4.md 10.5): rooted where they stood (movement
 * and jump strength cut to nothing by transient modifiers, and pulled back if knocked more than a
 * little way), with Force Speed and Force Barrier ended at the catch and every power but Force Push
 * refused. Casting Push breaks the net; so do the net's own saber and friend rules. Every way a net
 * ends goes through {@link #free}, which removes the modifiers. Server side; nothing is saved.
 */
public final class Netting {
	public static final Identifier SPEED_MODIFIER = OldRepublicMod.id("hunter_net_root");
	public static final Identifier JUMP_MODIFIER = OldRepublicMod.id("hunter_net_jump");
	/** Reasons for {@link #free}. */
	public static final String FREED_EXPIRED = "expired";
	public static final String FREED_PUSH = "push";
	public static final String FREED_SABER = "saber";
	public static final String FREED_TEAMMATE = "teammate";
	public static final String FREED_GONE = "gone";
	/** Drift from the spot where the net caught them beyond which the player is put back. */
	private static final double MAX_DRIFT = 0.6;

	/** One caught player: the net, when it ends (game time) and where they stand. */
	private record Snare(HunterNet net, long until, Vec3 anchor) {
	}

	private static final Map<UUID, Snare> SNARES = new HashMap<>();

	/** Registers the lock, the tick and the release paths. Called from {@code FoesFeature}. */
	public static void init() {
		AbilityLocks.register((player, abilityId) -> {
			if (!SNARES.containsKey(player.getUUID())) {
				return null;
			}

			if (ForcePush.ABILITY_ID.equals(abilityId)) {
				// Push is the way out: the net breaks and the Push goes ahead.
				free(player, FREED_PUSH);
				return null;
			}

			return Component.translatable("message.oldrepublic.enemy.netted");
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				free(player, FREED_GONE);
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> free(player, FREED_GONE));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> free(player, FREED_GONE));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> free(newPlayer, FREED_GONE));
	}

	/** Catches {@code player} in {@code net} for {@code ticks}: Speed and Barrier end, and they cannot move or jump. */
	static void snare(ServerPlayer player, HunterNet net, int ticks) {
		Snare old = SNARES.remove(player.getUUID());

		if (old != null && old.net() != net) {
			old.net().discard();
		}

		ForceSpeed.end(player, true);

		if (ForceBarrier.isChannelling(player)) {
			ForceBarrier.drop(player);
		}

		SNARES.put(player.getUUID(), new Snare(net, player.level().getGameTime() + ticks, player.position()));
		modifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, true);
		modifier(player, Attributes.JUMP_STRENGTH, JUMP_MODIFIER, true);
		player.setSprinting(false);
	}

	/** Whether {@code player} is caught in a net. */
	public static boolean isNetted(ServerPlayer player) {
		return SNARES.containsKey(player.getUUID());
	}

	/** Whether {@code player}'s current net is {@code net}. */
	static boolean isNetBy(ServerPlayer player, HunterNet net) {
		Snare snare = SNARES.get(player.getUUID());
		return snare != null && snare.net() == net;
	}

	/** Frees {@code player} (safe when not caught): the modifiers go, and so does the net. */
	public static void free(ServerPlayer player, String reason) {
		Snare snare = SNARES.remove(player.getUUID());
		modifier(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, false);
		modifier(player, Attributes.JUMP_STRENGTH, JUMP_MODIFIER, false);

		if (snare != null) {
			snare.net().discard();
			OldRepublicMod.LOGGER.debug("{} freed from a net ({})", player.getName().getString(), reason);
		}
	}

	private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, boolean on) {
		AttributeInstance instance = player.getAttribute(attribute);

		if (instance == null) {
			return;
		}

		if (on) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			instance.removeModifier(id);
		}
	}

	/** Ends nets whose time is up and keeps caught players where the net caught them. */
	public static void tick(MinecraftServer server) {
		if (SNARES.isEmpty()) {
			return;
		}

		for (Map.Entry<UUID, Snare> entry : Map.copyOf(SNARES).entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Snare snare = entry.getValue();

			if (player == null) {
				SNARES.remove(entry.getKey());
				snare.net().discard();
				continue;
			}

			if (player.level().getGameTime() >= snare.until() || snare.net().isRemoved()) {
				free(player, FREED_EXPIRED);
				continue;
			}

			Vec3 offset = snare.anchor().subtract(player.position()).multiply(1, 0, 1);

			if (offset.lengthSqr() > MAX_DRIFT * MAX_DRIFT) {
				player.teleportTo(snare.anchor().x, player.getY(), snare.anchor().z);
			}
		}
	}

	/** The net on {@code player}, or null. */
	public static @Nullable HunterNet netOn(ServerPlayer player) {
		Snare snare = SNARES.get(player.getUUID());
		return snare == null ? null : snare.net();
	}

	/** Forgets every net (server stop). */
	public static void clear() {
		SNARES.clear();
	}

	private Netting() {
	}
}
