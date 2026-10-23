package com.oldrepublic.holocron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.world.site.SiteKind;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The attunement moment on the server (spec 4.4): the holocron rises, the gatekeeper forms 30 ticks
 * later and the dialogue opens; a confirmed choice attunes, anything else (closing the dialogue
 * without a choice, damage, death, leaving, a dimension change, walking away, the server stopping,
 * or {@link #MAX_MOMENT_TICKS} passing) sinks the holocron back with nothing attuned and nothing
 * consumed. At most one moment per player and one per pedestal; the state is transient and never
 * saved, so a relog simply starts over.
 */
public final class HolocronAttunement {
	/** One player's open attunement. */
	private static final class Pending {
		final String id;
		final PedestalSite site;
		final long startTick;
		boolean open;

		Pending(String id, PedestalSite site, long startTick) {
			this.id = id;
			this.site = site;
			this.startTick = startTick;
		}
	}

	/** No moment stays open longer than this: the client's rising holocron and hum have the same watchdog. */
	static final int MAX_MOMENT_TICKS = 2400;

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(HolocronAttunement::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0f) {
				cancel(player, "damage");
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				cancel(player, "death");
			}
		});
		ServerPlayerEvents.LEAVE.register(player -> cancel(player, "leave", false));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> cancel(player, "dimension"));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> PENDING.clear());
	}

	/** Whether the player is in an attunement moment. */
	public static boolean pending(ServerPlayer p) {
		return PENDING.containsKey(p.getUUID());
	}

	/**
	 * Whether anyone has a moment open at the pedestal at {@code pos} in {@code dim}. Its cradle holds
	 * one rising holocron at a time, so the pedestal refuses everyone else until that moment ends.
	 */
	public static boolean pendingAt(ResourceKey<Level> dim, BlockPos pos) {
		for (Pending pending : PENDING.values()) {
			if (pending.site.dim() == dim && pending.site.pos().equals(pos)) {
				return true;
			}
		}

		return false;
	}

	/** The holocron of the player's open attunement, or null. */
	public static @Nullable String pendingId(ServerPlayer p) {
		Pending pending = PENDING.get(p.getUUID());
		return pending == null ? null : pending.id;
	}

	/** The kind of site the player's open attunement happens at, or null (read by {@code holocron:at_site}). */
	public static @Nullable SiteKind pendingSiteKind(ServerPlayer p) {
		Pending pending = PENDING.get(p.getUUID());
		return pending == null ? null : pending.site.siteKind();
	}

	/**
	 * Starts the moment for {@code id} at {@code site}: the rise is sent around the pedestal, and the
	 * gatekeeper and the dialogue follow after {@code gatekeeperDelayTicks}. Called by the pedestal.
	 */
	static void start(ServerPlayer p, PedestalSite site, String id) {
		HolocronBalance b = HolocronBalance.get();
		PENDING.put(p.getUUID(), new Pending(id, site, p.level().getGameTime()));
		EffectEvents.around(p.level(), HolocronEffects.ATTUNE, site.top(), null, b.riseTicks, 1f, HolocronCatalog.get(id).kind().ordinal());
	}

	/**
	 * Confirms the open attunement with {@code choice}: the choose action and tests call this. It
	 * checks that a moment is open and that {@link HolocronRules#allowed} has the choice for the
	 * player's path, then attunes with every first-time reward and folds the gatekeeper back.
	 *
	 * @return false (and nothing changes) when no moment is open or the choice is not allowed
	 */
	public static boolean confirm(ServerPlayer p, HolocronChoice choice) {
		Pending pending = PENDING.get(p.getUUID());

		if (pending == null || !HolocronRules.allowed(Identity.path(p), HolocronCatalog.get(pending.id).kind()).contains(choice)) {
			return false;
		}

		PENDING.remove(p.getUUID());
		boolean attuned = HolocronRewards.attune(p, pending.id, choice, pending.site);
		fold(p, pending);

		if (!attuned) {
			OldRepublicMod.LOGGER.warn("Holocron {} was already attuned by {} when the moment confirmed", pending.id, p.getName().getString());
		}

		return attuned;
	}

	/**
	 * Ends the open attunement with nothing attuned and nothing consumed: the holocron sinks back,
	 * the gatekeeper fades and the holocron's dialogue closes. A no-op without an open moment.
	 */
	public static void cancel(ServerPlayer p, String reason) {
		cancel(p, reason, true);
	}

	private static void cancel(ServerPlayer p, String reason, boolean online) {
		Pending pending = PENDING.remove(p.getUUID());

		if (pending == null) {
			return;
		}

		// The sink plays where the pedestal is: after a dimension change the player is already elsewhere.
		ServerLevel at = p.level().getServer().getLevel(pending.site.dim());

		if (at != null) {
			EffectEvents.around(at, HolocronEffects.ATTUNE, pending.site.top(), null, HolocronBalance.get().riseTicks, 0f,
					HolocronCatalog.get(pending.id).kind().ordinal());
		}

		if (online) {
			fold(p, pending);
			DialogueSession session = Dialogues.session(p);

			if (session != null && session.dialogue().equals(HolocronCatalog.dialogue(pending.id))) {
				Dialogues.close(p);
			}

			if ("walked_away".equals(reason)) {
				p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.attune.sank"));
			} else if ("timeout".equals(reason)) {
				p.sendOverlayMessage(Component.translatable("holocron.oldrepublic.attune.faded"));
			}
		}

		OldRepublicMod.LOGGER.debug("Attunement of {} by {} cancelled: {}", pending.id, p.getName().getString(), reason);
	}

	/** {@link Dialogues#ON_CLOSE}: the holocron's dialogue ended while the choice was still open. */
	static void onDialogueClosed(ServerPlayer p, Identifier dialogue) {
		Pending pending = PENDING.get(p.getUUID());

		if (pending != null && pending.open && dialogue.equals(HolocronCatalog.dialogue(pending.id))) {
			cancel(p, "closed");
		}
	}

	/** Folds the gatekeeper back into the holocron, if it had formed. */
	private static void fold(ServerPlayer p, Pending pending) {
		if (pending.open) {
			EffectEvents.toPlayer(p, HolocronEffects.GATEKEEPER, pending.site.top(), towardPlayer(p, pending.site), p.getId(), 0f,
					HolocronCatalog.gatekeeperIndex(pending.id));
		}
	}

	/** The unit horizontal vector from the pedestal toward the player (the figure faces the player). */
	static Vec3 towardPlayer(ServerPlayer p, PedestalSite site) {
		Vec3 top = site.top();
		Vec3 flat = new Vec3(p.getX() - top.x, 0, p.getZ() - top.z);
		return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	private static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}

		HolocronBalance b = HolocronBalance.get();
		double endDistance = Identity.balance().dialogueEndDistance;
		List<ServerPlayer> due = new ArrayList<>();
		List<ServerPlayer> away = new ArrayList<>();
		List<ServerPlayer> expired = new ArrayList<>();

		for (Map.Entry<UUID, Pending> entry : PENDING.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Pending pending = entry.getValue();

			if (player == null) {
				continue;
			}

			long age = player.level().getGameTime() - pending.startTick;

			if (player.level().dimension() != pending.site.dim() || player.position().distanceToSqr(pending.site.top()) > endDistance * endDistance) {
				away.add(player);
			} else if (age >= MAX_MOMENT_TICKS) {
				expired.add(player);
			} else if (!pending.open && age >= b.gatekeeperDelayTicks) {
				due.add(player);
			}
		}

		away.forEach(player -> cancel(player, "walked_away"));
		expired.forEach(player -> cancel(player, "timeout"));
		due.forEach(HolocronAttunement::open);
		PENDING.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
	}

	/** The gatekeeper forms and the holocron's dialogue opens. */
	private static void open(ServerPlayer p) {
		Pending pending = PENDING.get(p.getUUID());

		if (pending == null) {
			return;
		}

		pending.open = true;
		EffectEvents.toPlayer(p, HolocronEffects.GATEKEEPER, pending.site.top(), towardPlayer(p, pending.site), p.getId(), 1f,
				HolocronCatalog.gatekeeperIndex(pending.id));

		if (!Dialogues.open(p, HolocronCatalog.dialogue(pending.id), null)) {
			OldRepublicMod.LOGGER.warn("Holocron dialogue {} could not open for {}", HolocronCatalog.dialogue(pending.id), p.getName().getString());
			cancel(p, "no_dialogue");
		}
	}

	private HolocronAttunement() {
	}
}
