package com.oldrepublic.combat;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityLocks;
import com.oldrepublic.force.ForcePush;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Holding a player in place: the Archive Grip, a PvP Lift (spec 8.3, 12.1, 12.3). Player movement is
 * decided by the client, so velocity nudges alone cannot hold a player. Instead:
 * <ul>
 * <li>the server sets the synced {@link #HELD} attachment (owner only, never saved) and a transient
 * {@code GRAVITY} modifier {@code oldrepublic:held_gravity} (-1.0, {@code ADD_MULTIPLIED_TOTAL})
 * that brings gravity to 0, so vanilla stops counting floating ticks and a dedicated server with
 * {@code allow-flight=false} never kicks the held player;</li>
 * <li>each tick the server steers the player toward the anchor and, once the lift has settled,
 * teleports them back if they drift more than 0.5 blocks;</li>
 * <li>the client half ({@code client.combat.ClientHolds}) zeroes movement, swallows the action keys
 * and draws the hint;</li>
 * <li>an {@link AbilityLocks} lock refuses every power except {@code force_push}, and that only when
 * the hold allows it.</li>
 * </ul>
 * Every way a hold ends goes through {@link #release}, which also removes the gravity modifier.
 */
public final class Holds {
	/** Id of the transient gravity modifier (frozen). */
	public static final Identifier GRAVITY_MODIFIER = OldRepublicMod.id("held_gravity");

	/**
	 * What the held player's client needs: when the hold started and ends (game time), who holds them,
	 * the point they are held at and whether casting Push is allowed.
	 */
	public record HeldState(long since, long until, String source, Vec3 anchor, boolean pushAllowed) {
		public static final StreamCodec<ByteBuf, HeldState> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, HeldState::since,
				ByteBufCodecs.VAR_LONG, HeldState::until,
				ByteBufCodecs.STRING_UTF8, HeldState::source,
				Vec3.STREAM_CODEC, HeldState::anchor,
				ByteBufCodecs.BOOL, HeldState::pushAllowed,
				HeldState::new);
	}

	/** {@code oldrepublic:held}: transient (cleared on load), synced to its owner only. Never mutated: a new hold replaces it. */
	public static final AttachmentType<HeldState> HELD = AttachmentRegistry.create(
			OldRepublicMod.id("held"),
			builder -> builder.syncWith(HeldState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Drift from the anchor, in blocks, beyond which the server teleports the player back. */
	private static final double MAX_DRIFT = 0.5;
	/** Ticks the player gets to rise to the anchor before drift counts. */
	private static final int SETTLE_TICKS = 10;
	/** Fraction of the way to the anchor the server's velocity covers each tick. */
	private static final double STEER = 0.5;
	/** Steps when shortening an anchor that would put the player inside blocks. */
	private static final int ANCHOR_STEPS = 10;

	/**
	 * Holds {@code p} at {@code anchor} for {@code ticks}. The anchor is shortened toward the player
	 * until their box fits there, so a low ceiling never traps them in a block. A new hold replaces
	 * the current one. Does nothing for spectators, dead players or {@code ticks <= 0}.
	 *
	 * @param source      who holds the player (for example {@code collector_grip}, {@code force_lift}, {@code command})
	 * @param pushAllowed whether casting Force Push stays allowed (and is the way out)
	 */
	public static void hold(ServerPlayer p, int ticks, String source, Vec3 anchor, boolean pushAllowed) {
		if (ticks <= 0 || !p.isAlive() || p.isSpectator()) {
			return;
		}

		long now = p.level().getGameTime();
		p.setAttached(HELD, new HeldState(now, now + ticks, source, freeAnchor(p, anchor), pushAllowed));
		AttributeInstance gravity = p.getAttribute(Attributes.GRAVITY);

		if (gravity != null) {
			gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_MODIFIER, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}

		p.resetFallDistance();
	}

	/**
	 * Ends any hold on {@code p} and removes the gravity modifier; safe to call when {@code p} is not
	 * held. Reasons used here and by callers: {@code break}, {@code release}, {@code expired},
	 * {@code death}, {@code logout}, {@code dimension}, {@code respawn}, {@code reset}, {@code command}.
	 */
	public static void release(ServerPlayer p, String reason) {
		AttributeInstance gravity = p.getAttribute(Attributes.GRAVITY);

		if (gravity != null) {
			gravity.removeModifier(GRAVITY_MODIFIER);
		}

		HeldState held = p.removeAttached(HELD);

		if (held != null) {
			p.resetFallDistance();
			OldRepublicMod.LOGGER.debug("Released {} from hold {} ({})", p.getName().getString(), held.source(), reason);
		}
	}

	/** Whether {@code p} is held. Reads the synced attachment, so it also works for the local player on the client. */
	public static boolean isHeld(Player p) {
		return p.hasAttached(HELD);
	}

	/** Whether {@code p} is held and may cast Force Push. Works on both sides, like {@link #isHeld}. */
	public static boolean pushAllowed(Player p) {
		HeldState held = p.getAttached(HELD);
		return held != null && held.pushAllowed();
	}

	/** Registers the release paths, the server tick and the ability lock. Called from {@link CombatContracts#init}. */
	static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(Holds::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				release(player, "death");
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> release(player, "logout"));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> release(player, "dimension"));
		// Leaving the End copies every attribute modifier onto the new player object: drop ours.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> release(newPlayer, "respawn"));
		AbilityLocks.register((player, abilityId) -> {
			HeldState held = player.getAttached(HELD);

			if (held == null || (held.pushAllowed() && ForcePush.ABILITY_ID.equals(abilityId))) {
				return null;
			}

			return Component.translatable("message.oldrepublic.combat.held");
		});
	}

	/** Every player in the level, so game-test mock players listed in a level are held too. */
	private static void tick(ServerLevel level) {
		long now = level.getGameTime();

		for (ServerPlayer player : level.players()) {
			HeldState held = player.getAttached(HELD);

			if (held == null) {
				continue;
			}

			if (now >= held.until() || !player.isAlive() || player.isSpectator()) {
				release(player, now >= held.until() ? "expired" : "release");
				continue;
			}

			Vec3 offset = held.anchor().subtract(player.position());

			if (now - held.since() >= SETTLE_TICKS && offset.lengthSqr() > MAX_DRIFT * MAX_DRIFT) {
				player.teleportTo(held.anchor().x, held.anchor().y, held.anchor().z);
			} else {
				player.setDeltaMovement(offset.scale(STEER));
				player.hurtMarked = true;
			}

			player.resetFallDistance();
		}
	}

	/** The farthest point on the way from the player to {@code anchor} where their box fits. */
	private static Vec3 freeAnchor(ServerPlayer p, Vec3 anchor) {
		Vec3 offset = anchor.subtract(p.position());
		AABB box = p.getBoundingBox();

		for (int step = ANCHOR_STEPS; step > 0; step--) {
			Vec3 move = offset.scale(step / (double) ANCHOR_STEPS);

			if (p.level().noCollision(p, box.move(move))) {
				return p.position().add(move);
			}
		}

		return p.position();
	}

	private Holds() {
	}
}
