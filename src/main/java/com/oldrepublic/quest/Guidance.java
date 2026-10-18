package com.oldrepublic.quest;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.planet.Planets;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.travel.TravelAttachments;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The journal's Calling after Chapter Two (spec section 5.7): features register providers that may
 * ask for the player's attention, and while the journal is held only the highest-priority request
 * is sent, one pulse per interval. The quest feature calls {@link #pulse} from its journal-hold tick.
 */
public final class Guidance {
	/**
	 * One request for the Calling.
	 *
	 * @param priority higher wins; see {@link #STORY}, {@link #MISSION}, {@link #INTERROGATION},
	 *     {@link #OUTPOST}
	 * @param target where the Calling points; only its direction and closeness are sent
	 * @param callingVariant the {@code calling} effect variant ({@code QuestEffects.CALLING_*}),
	 *     optionally carrying data packed by {@link #variant(int, int)}
	 * @param source who asked, for logs and tests (for example {@code story} or {@code mission})
	 * @param dimension the target's level; null means the player's own (every M1–M3 request). A
	 *     target in another level sends the off-world Calling instead (spec MILESTONE_4.md 3.6)
	 */
	public record Request(int priority, Vec3 target, int callingVariant, String source, @Nullable ResourceKey<Level> dimension) {
		/** A request in the player's own level. */
		public Request(int priority, Vec3 target, int callingVariant, String source) {
			this(priority, target, callingVariant, source, null);
		}
	}

	/** Something that may want the Calling to point somewhere for a player. */
	@FunctionalInterface
	public interface Provider {
		/** This provider's request for the player now, or null for none. Server thread. */
		@Nullable Request request(ServerPlayer p);
	}

	/** Story targets: Courier's Fall, the first echo, the Reliquary. */
	public static final int STORY = 100;
	/** The active mission. */
	public static final int MISSION = 50;
	/** The interrogation pull toward an unused holocron chamber. */
	public static final int INTERROGATION = 40;
	/** An unvisited outpost. */
	public static final int OUTPOST = 20;

	/** {@link Request#callingVariant()} bits below this shift are the effect variant; the bits above are its data. */
	private static final int DATA_SHIFT = 8;
	private static final int VARIANT_MASK = (1 << DATA_SHIFT) - 1;

	private static final List<Provider> PROVIDERS = new ArrayList<>();

	/** Adds a provider. Call during mod initialisation. */
	public static void register(Provider provider) {
		PROVIDERS.add(provider);
	}

	/** The highest-priority request for the player, or null when no provider asks; ties go to the first registered. */
	public static @Nullable Request current(ServerPlayer p) {
		Request best = null;

		for (Provider provider : PROVIDERS) {
			Request request = provider.request(p);

			if (request != null && (best == null || request.priority() > best.priority())) {
				best = request;
			}
		}

		return best;
	}

	/**
	 * Sends one Calling pulse for {@link #current}: the {@code calling} effect to the player alone,
	 * with the direction and closeness of the target, the effect variant from the request and its
	 * data (see {@link #variant(int, int)}) as the event's entity id. Does nothing when no provider
	 * asks.
	 */
	public static void pulse(ServerPlayer p) {
		Request request = current(p);

		if (request == null) {
			return;
		}

		if (request.dimension() != null && !request.dimension().equals(p.level().dimension())) {
			offworld(p, request.dimension());
			return;
		}

		int packed = request.callingVariant();
		Calling.send(p, request.target(), packed & VARIANT_MASK, packed >>> DATA_SHIFT);
	}

	/**
	 * The off-world Calling (spec MILESTONE_4.md 3.6): the target is on another world, so the cue
	 * points at the player's ship when its body is loaded here, else their home pad when it is in
	 * this level, else nowhere (the faint cue). The data is the target's {@code Planets.Planet}
	 * ordinal, and the action bar names the world.
	 */
	private static void offworld(ServerPlayer p, ResourceKey<Level> dimension) {
		Planets.Planet planet = Planets.Planet.of(dimension);
		Vec3 cue = null;
		Entity ship = Ships.body(p.level().getServer(), p.getUUID());

		if (ship != null && ship.level() == p.level()) {
			cue = ship.position();
		} else {
			GlobalPos pad = TravelAttachments.get(p).homePad().orElse(null);

			if (pad != null && pad.dimension().equals(p.level().dimension())) {
				cue = Vec3.atCenterOf(pad.pos());
			}
		}

		Calling.send(p, cue != null ? cue : p.position(), cue != null ? QuestEffects.CALLING_OFFWORLD : QuestEffects.CALLING_NONE,
				planet != null ? planet.ordinal() : 0);

		if (planet != null) {
			p.sendOverlayMessage(Component.translatable("message.oldrepublic.calling.offworld", Component.translatable("dimension.oldrepublic." + planet.id())));
		}
	}

	/**
	 * A {@link Request#callingVariant()} that carries {@code data} (a small non-negative number) to the client in the
	 * event's entity id: the {@code HolocronKind} ordinal for {@code CALLING_HOLOCRON}, the army's
	 * {@code Faction} ordinal for {@code CALLING_OUTPOST} (spec section 13.3).
	 */
	public static int variant(int callingVariant, int data) {
		return (callingVariant & VARIANT_MASK) | data << DATA_SHIFT;
	}

	private Guidance() {
	}
}
