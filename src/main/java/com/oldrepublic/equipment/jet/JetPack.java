package com.oldrepublic.equipment.jet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.enemy.mandalorian.Netting;
import com.oldrepublic.equipment.ArmourSets;
import com.oldrepublic.equipment.EquipmentM4Balance;
import com.oldrepublic.equipment.EquipmentM4Balance.ModNumbers;
import com.oldrepublic.equipment.GearEffects;
import com.oldrepublic.equipment.SetBonusRules;
import com.oldrepublic.equipment.mod.ModEffects;
import com.oldrepublic.force.jump.ForceJump;
import com.oldrepublic.network.EffectEvents;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The jet pack (spec MILESTONE_4.md 6.4), a chest mod: jump pressed while airborne sends
 * {@link JetBurstPayload}; the server checks the worn pack, the charges and every refusal (on the
 * ground, in water, riding, flying or gliding with elytra, held by {@code Holds}, or during a Force
 * Jump leap, whose glide keeps the jump key), then sets the velocity: {@code jetUp} upward plus
 * {@code jetForward} along the horizontal look, half the old horizontal speed kept. A burst is a
 * velocity change, not flight, so the server's floating check sees ordinary jumps. Charges
 * ({@code jetCharges}, one more with Hunter's Tenacity) refill together after {@code jetRefillTicks}
 * on the ground; a burst within {@code jetLandWindow} ticks before landing halves that fall's damage
 * ({@link #fallMultiplier}). The charges left are synced to the wearer for the HUD pips
 * ({@link #JET}). Server side.
 */
public final class JetPack {
	/** The jet pack's charges as the wearer's HUD shows them. */
	public record JetView(int charges, int max) {
		public static final StreamCodec<ByteBuf, JetView> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, JetView::charges,
				ByteBufCodecs.VAR_INT, JetView::max,
				JetView::new);
	}

	/** The worn pack's charges, synced to the wearer only; absent without a pack. Not persistent. */
	public static final AttachmentType<JetView> JET = AttachmentRegistry.create(
			OldRepublicMod.id("jet_pack"),
			builder -> builder.syncWith(JetView.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Ticks between two "empty" clicks, so a held key does not click every tick. */
	private static final int EMPTY_CLICK_GAP = 10;
	/** How far behind the wearer's centre the pack sits, blocks. */
	private static final double PACK_BACK = 0.3;
	private static final double PACK_HEIGHT = 1.05;

	/** One wearer's charges. */
	private static final class State {
		int used;
		int groundTicks;
		long lastBurst = Long.MIN_VALUE / 2;
		long lastEmpty = Long.MIN_VALUE / 2;
		@Nullable JetView sent;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	/** Registers the payload, its receiver, the tick and the cleanup listeners. */
	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(JetBurstPayload.TYPE, JetBurstPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(JetBurstPayload.TYPE, (payload, context) -> burst(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(JetPack::tick);
		ServerPlayerEvents.LEAVE.register(player -> STATES.remove(player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> STATES.remove(oldPlayer.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
	}

	// ---------------------------------------------------------------- pure rules

	/** The burst velocity: {@code up} upward, {@code forward} along the horizontal look, half the old horizontal speed kept. */
	public static Vec3 burstVelocity(Vec3 old, Vec3 look, float up, float forward) {
		Vec3 flat = look.horizontal();
		Vec3 dir = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
		return new Vec3(old.x * 0.5 + dir.x * forward, up, old.z * 0.5 + dir.z * forward);
	}

	/** Whether a burst at {@code lastBurst} is within {@code window} ticks before a landing at {@code now}. */
	public static boolean inLandingWindow(long lastBurst, long now, int window) {
		long since = now - lastBurst;
		return since >= 0 && since <= window;
	}

	/** Ground ticks after one more tick: counted on the ground, reset in the air. */
	public static int groundTicks(int groundTicks, boolean onGround) {
		return onGround ? groundTicks + 1 : 0;
	}

	/** Charges used after {@code groundTicks} on the ground: all back at {@code refillTicks}. */
	public static int usedAfter(int used, int groundTicks, int refillTicks) {
		return groundTicks >= refillTicks ? 0 : used;
	}

	// ---------------------------------------------------------------- the burst

	/**
	 * A burst request from {@code player}. Refused without a word when a rule forbids it; with every
	 * charge spent it clicks empty instead. Returns whether a burst fired.
	 */
	public static boolean burst(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || !ModEffects.jetPack(player) || player.onGround() || player.isInWater()
				|| player.isPassenger() || player.getAbilities().flying || player.isFallFlying() || ForceJump.isLeaping(player)
				|| Holds.isHeld(player) || Netting.isNetted(player)) {
			return false;
		}

		ModNumbers m = EquipmentM4Balance.mods();
		State state = state(player);
		long now = player.level().getGameTime();

		if (now - state.lastBurst < m.jetBurstGapTicks) {
			return false;
		}

		if (state.used >= maxCharges(player, m)) {
			if (now - state.lastEmpty >= EMPTY_CLICK_GAP) {
				state.lastEmpty = now;
				EffectEvents.toTracking(player, GearEffects.JET_PACK, packPos(player), null, player.getId(), 0f, GearEffects.JET_EMPTY);
			}

			return false;
		}

		state.used++;
		state.lastBurst = now;
		state.groundTicks = 0;
		player.setDeltaMovement(burstVelocity(player.getDeltaMovement(), player.getLookAngle(), m.jetUp, m.jetForward));
		player.hurtMarked = true;
		player.resetFallDistance();
		Vec3 exhaust = new Vec3(0, -1, 0).subtract(player.getLookAngle().horizontal().scale(0.4));
		EffectEvents.toTracking(player, GearEffects.JET_PACK, packPos(player), exhaust, player.getId(), 1f, GearEffects.JET_BURST);
		sync(player, state, maxCharges(player, m));
		return true;
	}

	/** The pack's charges for {@code player}: the pack's own and Hunter's Tenacity's. */
	public static int maxCharges(LivingEntity e, ModNumbers m) {
		return SetBonusRules.jetCharges(ArmourSets.worn(e), EquipmentM4Balance.get(), m.jetCharges);
	}

	/** Charges {@code player} has left (the full count when the state is gone). */
	public static int charges(ServerPlayer player) {
		ModNumbers m = EquipmentM4Balance.mods();
		State state = STATES.get(player.getUUID());
		return maxCharges(player, m) - (state == null ? 0 : state.used);
	}

	/**
	 * The fall damage taken multiplier of a landing now: {@code jetFallPct} after a burst within
	 * {@code jetLandWindow} ticks, else 1.
	 */
	public static float fallMultiplier(LivingEntity e) {
		State state = STATES.get(e.getUUID());

		if (state == null) {
			return 1f;
		}

		ModNumbers m = EquipmentM4Balance.mods();
		return inLandingWindow(state.lastBurst, e.level().getGameTime(), m.jetLandWindow) ? Math.max(0f, 1f + m.jetFallPct / 100f) : 1f;
	}

	/** Where the pack's nozzles are: behind the wearer's shoulders. */
	private static Vec3 packPos(ServerPlayer player) {
		Vec3 back = player.getLookAngle().horizontal();
		back = back.lengthSqr() < 1.0E-6 ? Vec3.ZERO : back.normalize().scale(-PACK_BACK);
		return player.position().add(back.x, PACK_HEIGHT, back.z);
	}

	// ---------------------------------------------------------------- the tick

	private static void tick(MinecraftServer server) {
		ModNumbers m = EquipmentM4Balance.mods();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean wearing = ModEffects.jetPack(player);
			State state = STATES.get(player.getUUID());

			if (state == null && !wearing) {
				continue;
			}

			state = state(player);
			state.groundTicks = groundTicks(state.groundTicks, player.onGround());
			state.used = usedAfter(state.used, state.groundTicks, m.jetRefillTicks);

			if (wearing) {
				sync(player, state, maxCharges(player, m));
				continue;
			}

			// The pack came off: no pips; spent charges still refill on the ground before the state goes.
			if (state.sent != null) {
				state.sent = null;
				player.removeAttached(JET);
			}

			if (state.used == 0) {
				STATES.remove(player.getUUID());
			}
		}
	}

	/** Syncs the pips when they changed. */
	private static void sync(ServerPlayer player, State state, int max) {
		JetView view = new JetView(Math.max(0, max - state.used), max);

		if (!view.equals(state.sent)) {
			state.sent = view;
			player.setAttached(JET, view);
		}
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	/** Spends the charges as if burst (tests): {@code used} charges gone, the last burst {@code ticksAgo}. */
	public static void spend(ServerPlayer player, int used, long ticksAgo) {
		State state = state(player);
		state.used = used;
		state.groundTicks = 0;
		state.lastBurst = player.level().getGameTime() - ticksAgo;
	}

	private JetPack() {
	}
}
