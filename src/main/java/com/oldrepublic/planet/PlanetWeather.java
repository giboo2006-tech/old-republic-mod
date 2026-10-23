package com.oldrepublic.planet;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.network.EffectEvents;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

/**
 * Per-dimension weather (spec MILESTONE_5.md 3.5):
 * <ul>
 * <li><b>Ghessar</b> never rains: its rain and thunder levels are held at 0
 * ({@code PlanetWeatherCycleMixin}) and resent to its players every {@link #RESEND_TICKS} ticks and
 * on the tick the overworld's weather turns (the overworld's start and stop packets reach every
 * dimension). It has sandstorms instead, on its own cycle seeded by the world seed ({@link #next}):
 * a clear spell of {@code planets.sandstormIntervalDays} days, then a storm of
 * {@code planets.sandstormTicks} ticks; none while {@code planets.sandstorms} is off.</li>
 * <li><b>Tyrvann</b> storms for ever: both levels held at 1 and resent the same way, so vanilla's
 * lightning strikes there by itself (with its vanilla fire). Skeleton-horse traps never spawn
 * there.</li>
 * <li>Selvaren and Varkhast keep the M4 rule: they ease toward the overworld's weather.</li>
 * </ul>
 * Ghessar's state is the saved global attachment {@code oldrepublic:planet_weather}, so a storm
 * survives a restart. It is sent (payload {@code planet_weather}) to a player who joins in Ghessar,
 * arrives there or respawns there, and to everyone there when it changes, together with the
 * {@code sandstorm} effect and a line on the overlay as a storm rises or passes. {@link #kind}
 * answers on both sides: the client keeps the last state it was sent.
 */
public final class PlanetWeather {
	/** Stored and synced by ordinal: append only. */
	public enum Kind {
		CLEAR,
		SANDSTORM;

		/** The kind stored or synced as {@code ordinal}; CLEAR for an unknown one. */
		public static Kind byOrdinal(int ordinal) {
			Kind[] all = values();
			return ordinal >= 0 && ordinal < all.length ? all[ordinal] : CLEAR;
		}
	}

	/**
	 * A world's weather and the game tick it lasts until (the global attachment
	 * {@code oldrepublic:planet_weather}): a storm ends at {@code untilTick}, a clear spell gives way
	 * to the next storm there.
	 */
	public record State(Kind kind, long untilTick) {
		public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("kind").forGetter(state -> state.kind().ordinal()),
				Codec.LONG.fieldOf("until").forGetter(State::untilTick))
				.apply(instance, (kind, until) -> new State(Kind.byOrdinal(kind), until)));
		/** No weather known yet: clear, and due at once. */
		public static final State NONE = new State(Kind.CLEAR, 0L);

		/** Whether a storm blows at game tick {@code now}. */
		public boolean storming(long now) {
			return kind == Kind.SANDSTORM && now < untilTick;
		}
	}

	/** Ghessar's weather, saved; not synced (the payload carries it). */
	public static final AttachmentType<State> PLANET_WEATHER = AttachmentRegistry.create(OldRepublicMod.id("planet_weather"),
			builder -> builder.persistent(State.CODEC));
	/** Rain and thunder levels are resent to the players of Ghessar and Tyrvann this often. */
	public static final int RESEND_TICKS = 20;
	/** The client's sandstorm fog closes in, or lifts, over this many ticks (15.3). */
	public static final int FOG_TICKS = 200;
	public static final int DAY_TICKS = 24000;
	/** The two worlds whose rain and thunder are held. */
	private static final List<Planets.Planet> HELD = List.of(Planets.Planet.GHESSAR, Planets.Planet.TYRVANN);
	/** At most this many spells are stepped through at once (the cycle never falls this far behind). */
	private static final int MAX_STEPS = 16;

	/** The client's copy of Ghessar's weather (the last {@code planet_weather} payload). */
	private static volatile State clientState = State.NONE;
	/** Whether the overworld rained last tick: its turning resends the held levels at once. */
	private static boolean overworldRaining;

	/** Registers the payload and the sync on joining, arriving and respawning (from {@code PlanetM5Feature}). */
	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(PlanetWeatherPayload.TYPE, PlanetWeatherPayload.CODEC);
		ServerPlayerEvents.JOIN.register(PlanetWeather::sync);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sync(newPlayer));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> sync(player));
	}

	/** The weather in {@code level}, on either side: CLEAR except while a storm blows on Ghessar. */
	public static Kind kind(Level level) {
		if (!level.dimension().equals(Planets.GHESSAR)) {
			return Kind.CLEAR;
		}

		State state = level instanceof ServerLevel server ? state(server.getServer()) : clientState;
		return state.storming(level.getGameTime()) ? Kind.SANDSTORM : Kind.CLEAR;
	}

	/** Ghessar's saved state ({@link State#NONE} before its first tick). */
	public static State state(MinecraftServer server) {
		State state = server.globalAttachments().getAttached(PLANET_WEATHER);
		return state != null ? state : State.NONE;
	}

	/** The client's copy of Ghessar's weather. */
	public static State clientState() {
		return clientState;
	}

	/** Sets the client's copy (the {@code planet_weather} receiver; {@link State#NONE} on disconnect). Client thread. */
	public static void setClientState(State state) {
		clientState = state;
	}

	/**
	 * The rain and thunder level {@code dimension} is held at (pure, for the tests): 0 on Ghessar, 1 on
	 * Tyrvann, null where the shared weather rules.
	 */
	public static @Nullable Float heldLevel(ResourceKey<Level> dimension) {
		if (dimension.equals(Planets.GHESSAR)) {
			return 0f;
		}

		return dimension.equals(Planets.TYRVANN) ? 1f : null;
	}

	/** Whether lightning in {@code dimension} may never spawn a skeleton-horse trap (Tyrvann). */
	public static boolean noHorseTraps(ResourceKey<Level> dimension) {
		return dimension.equals(Planets.TYRVANN);
	}

	// ---------------------------------------------------------------- the cycle (pure, for the tests)

	/** The first clear spell of a world whose Ghessar has no weather yet, starting at {@code now}. */
	public static State first(long seed, long now, PlanetBalance balance) {
		return new State(Kind.CLEAR, now + clearTicks(spellRandom(seed, now), balance));
	}

	/**
	 * What follows {@code ended} once its {@code untilTick} comes: a storm after a clear spell, a clear
	 * spell after a storm, both seeded by the world seed and that tick. With
	 * {@code planets.sandstorms} off a clear spell is followed by another.
	 */
	public static State next(long seed, State ended, PlanetBalance balance) {
		long start = ended.untilTick();
		RandomSource random = spellRandom(seed, start);

		if (ended.kind() == Kind.SANDSTORM || !balance.sandstorms) {
			return new State(Kind.CLEAR, start + clearTicks(random, balance));
		}

		return new State(Kind.SANDSTORM, start + stormTicks(random, balance));
	}

	/**
	 * {@code state} brought up to game tick {@code now}: each spell that has run out gives way to the
	 * next; a storm blowing while {@code planets.sandstorms} is off ends now.
	 */
	public static State advance(long seed, State state, long now, PlanetBalance balance) {
		State current = state;

		if (!balance.sandstorms && current.storming(now)) {
			current = next(seed, new State(Kind.SANDSTORM, now), balance);
		}

		for (int i = 0; i < MAX_STEPS && now >= current.untilTick(); i++) {
			current = next(seed, current, balance);
		}

		return current;
	}

	private static RandomSource spellRandom(long seed, long tick) {
		return RandomSource.create(seed ^ tick * 0x9E3779B97F4A7C15L);
	}

	private static int clearTicks(RandomSource random, PlanetBalance balance) {
		return between(random, balance.sandstormIntervalDays, 3, DAY_TICKS);
	}

	private static int stormTicks(RandomSource random, PlanetBalance balance) {
		return between(random, balance.sandstormTicks, 3000, 1);
	}

	/** A whole number of ticks in {@code range} ({@code [min, max]} times {@code unit}, inclusive); {@code fallback} when empty. */
	private static int between(RandomSource random, int[] range, int fallback, int unit) {
		int min = Math.max(1, range.length == 0 ? fallback : range[0]) * unit;
		int max = Math.max(min, (range.length == 0 ? fallback : range[range.length - 1]) * unit);
		return min + random.nextInt(max - min + 1);
	}

	// ---------------------------------------------------------------- the server

	/** One server tick: Ghessar's cycle, and the held levels resent when due. */
	static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		ServerLevel ghessar = Planets.level(server, Planets.Planet.GHESSAR);

		if (ghessar != null) {
			cycle(server, ghessar, now);
		}

		boolean raining = server.overworld().isRaining();

		if (now % RESEND_TICKS == 0 || raining != overworldRaining) {
			resend(server);
		}

		overworldRaining = raining;
	}

	/** Forgets the overworld's last weather (server stop). */
	static void clear() {
		overworldRaining = false;
	}

	/**
	 * Starts a storm of {@code ticks} on Ghessar now, or ends the one blowing ({@code ticks} 0: the next
	 * clear spell starts now). The command's path; false when the server has no Ghessar.
	 */
	public static boolean force(MinecraftServer server, int ticks) {
		ServerLevel ghessar = Planets.level(server, Planets.Planet.GHESSAR);

		if (ghessar == null) {
			return false;
		}

		long now = ghessar.getGameTime();
		State after = ticks > 0 ? new State(Kind.SANDSTORM, now + ticks) : next(ghessar.getSeed(), new State(Kind.SANDSTORM, now), PlanetBalance.get());
		change(server, ghessar, state(server), after, now);
		return true;
	}

	private static void cycle(MinecraftServer server, ServerLevel ghessar, long now) {
		State before = server.globalAttachments().getAttached(PLANET_WEATHER);
		PlanetBalance balance = PlanetBalance.get();
		State after = before == null ? first(ghessar.getSeed(), now, balance) : advance(ghessar.getSeed(), before, now, balance);

		if (!after.equals(before)) {
			change(server, ghessar, before == null ? State.NONE : before, after, now);
		}
	}

	/** Saves {@code after} and tells everyone in Ghessar; a storm rising or passing also plays its effect and its line. */
	private static void change(MinecraftServer server, ServerLevel ghessar, State before, State after, long now) {
		server.globalAttachments().setAttached(PLANET_WEATHER, after);
		boolean was = before.storming(now);
		boolean is = after.storming(now);

		if (was != is) {
			OldRepublicMod.LOGGER.info("Ghessar: {} (until tick {})", is ? "a sandstorm rises" : "the sandstorm passes", after.untilTick());
		}

		PlanetWeatherPayload payload = new PlanetWeatherPayload(after);

		for (ServerPlayer player : ghessar.players()) {
			ServerPlayNetworking.send(player, payload);

			if (was != is) {
				EffectEvents.toPlayer(player, PlanetEffects.SANDSTORM, player.position(), null, -1, 1f,
						is ? PlanetEffects.SANDSTORM_START : PlanetEffects.SANDSTORM_END);
				player.sendOverlayMessage(Component.translatable(is ? "weather.oldrepublic.sandstorm.rising" : "weather.oldrepublic.sandstorm.passing"));
			}
		}
	}

	/** A player now in Ghessar gets its weather, and the storm's effect while one blows. */
	private static void sync(ServerPlayer player) {
		if (!player.level().dimension().equals(Planets.GHESSAR)) {
			return;
		}

		State state = state(player.level().getServer());
		ServerPlayNetworking.send(player, new PlanetWeatherPayload(state));

		if (state.storming(player.level().getGameTime())) {
			EffectEvents.toPlayer(player, PlanetEffects.SANDSTORM, player.position(), null, -1, 1f, PlanetEffects.SANDSTORM_START);
		}
	}

	/** Sends the held rain and thunder levels to everyone in Ghessar and Tyrvann. */
	private static void resend(MinecraftServer server) {
		for (Planets.Planet planet : HELD) {
			Float level = heldLevel(planet.level());

			if (level != null) {
				server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, level), planet.level());
				server.getPlayerList().broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level), planet.level());
			}
		}
	}

	private PlanetWeather() {
	}
}
