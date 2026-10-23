package com.oldrepublic.boss.cantor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.raid.RaidBalance;
import com.oldrepublic.boss.raid.RaidRules;
import com.oldrepublic.enemy.hymn.StormCall;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * The verses of the First Cantor's second phase (spec MILESTONE_5.md 12.3): every {@code verseInterval}
 * he sings one of the three verses the player silenced on Tyrvann, in turn.
 *
 * <ul>
 * <li><b>Stormwood</b>: after {@code rootsTell}, roots grip everyone within {@code rootsRadius} of a
 * participant's spot for {@code rootsTicks} (Slowness IV); an ally's blow on the roots frees a player
 * ({@link #free}, from {@code LongHymn}'s attack hook: the blow lands on the roots, not the player).</li>
 * <li><b>Mire</b>: {@code fogPools} pools of black fog, {@code fogWidth} across, spread on the floor for
 * {@code fogTicks}: {@code fogDamage} a second and Slowness I inside, from a second after they spread.</li>
 * <li><b>Basalt</b>: {@link RaidRules#basaltMarks} marked spots (static crawling, grit lifting) take
 * lightning after {@code basaltTell}: {@code basaltDamage} and a shock within {@code basaltRadius}.</li>
 * </ul>
 *
 * The first marks and circles fall under participants, the rest on the hall's floor.
 */
public final class CantorVerses {
	/** The three verses, sung in this order. */
	public enum Verse {
		STORMWOOD,
		MIRE,
		BASALT
	}

	/** The roots' grip: Slowness IV. */
	static final int ROOT_AMPLIFIER = 3;
	/** The fog's Slowness I lasts this long after each sting. */
	private static final int FOG_SLOW_TICKS = 30;
	/** Hazards reach this far over and under the floor (blocks). */
	private static final double REACH_UP = 2.5;
	/** Spots fall this far from his spot at most (blocks). */
	private static final double SPREAD = 11;

	private record Pool(Vec3 centre, long from, long until) {
	}

	private final CantorSession session;
	private int timer;
	private int index;
	private @Nullable Verse last;
	// Stormwood.
	private int rootsTell = -1;
	private @Nullable Vec3 rootsCentre;
	private final Map<UUID, Long> rooted = new HashMap<>();
	// Mire.
	private final List<Pool> pools = new ArrayList<>();
	// Basalt.
	private final List<Vec3> marks = new ArrayList<>();
	private int basaltTell = -1;

	CantorVerses(CantorSession session) {
		this.session = session;
	}

	/** The verse sung last (null before the first). */
	public @Nullable Verse last() {
		return last;
	}

	/** Whether {@code player} is held by the roots now. */
	public boolean isRooted(ServerPlayer player) {
		Long until = rooted.get(player.getUUID());
		return until != null && until > session.level().getGameTime();
	}

	/** The roots' circle while its tell runs, else null. */
	public @Nullable Vec3 rootsTelling() {
		return rootsTell >= 0 ? rootsCentre : null;
	}

	/** The fog pools spreading now (their centres). */
	public List<Vec3> pools() {
		return pools.stream().map(Pool::centre).toList();
	}

	/** The basalt marks waiting for their lightning. */
	public List<Vec3> marks() {
		return List.copyOf(marks);
	}

	/** The second phase begins: the first verse after half an interval, Stormwood first. */
	void start(RaidBalance b) {
		stop();
		timer = Math.max(1, b.verseInterval / 2);
		index = 0;
		last = null;
	}

	/** Every verse stops, and everyone held is freed. */
	void stop() {
		rootsTell = -1;
		rootsCentre = null;

		for (UUID id : List.copyOf(rooted.keySet())) {
			ServerPlayer player = session.level().getServer().getPlayerList().getPlayer(id);

			if (player != null) {
				release(player);
			}
		}

		rooted.clear();
		pools.clear();
		marks.clear();
		basaltTell = -1;
	}

	/** One tick: what is set going plays out; {@code held} (Stasis) sings no new verse. */
	void tick(FirstCantor c, RaidBalance b, boolean held) {
		tickRoots(c, b);
		tickPools(c, b);
		tickMarks(c, b);

		if (held || --timer > 0) {
			return;
		}

		timer = Math.max(1, b.verseInterval);
		Verse verse = Verse.values()[Math.floorMod(index++, Verse.values().length)];
		sing(c, verse, b);
	}

	/** An operator's verse now (tests, the commands). */
	public void forceVerse(FirstCantor c, Verse verse) {
		sing(c, verse, RaidBalance.get());
	}

	private void sing(FirstCantor c, Verse verse, RaidBalance b) {
		last = verse;
		session.pose(c, FirstCantor.Action.VERSE);

		switch (verse) {
			case STORMWOOD -> callRoots(c, b);
			case MIRE -> spreadFog(c, b);
			case BASALT -> markBasalt(c, b);
		}
	}

	// ---------------------------------------------------------------- Stormwood

	private void callRoots(FirstCantor c, RaidBalance b) {
		List<Vec3> spots = spots(1);
		Vec3 centre = spots.get(0);
		rootsCentre = centre;
		rootsTell = Math.max(1, b.rootsTell);
		session.event(centre, null, c.getId(), b.rootsRadius, CantorEffects.ROOTS_TELL);
		session.play(centre, ModSounds.BOSS_CANTOR_ROOTS, 1.2f, 0.6f);
	}

	private void tickRoots(FirstCantor c, RaidBalance b) {
		long now = session.level().getGameTime();
		rooted.values().removeIf(until -> until <= now);

		if (rootsTell < 0 || --rootsTell >= 0) {
			return;
		}

		Vec3 centre = rootsCentre;
		rootsCentre = null;

		if (centre == null) {
			return;
		}

		int ticks = Math.max(1, b.rootsTicks);

		for (ServerPlayer player : session.everyoneInside()) {
			if (within(player.position(), centre, b.rootsRadius)) {
				rooted.put(player.getUUID(), now + ticks);
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, ROOT_AMPLIFIER), c);
			}
		}

		session.event(centre, new Vec3(ticks, 0, 0), c.getId(), b.rootsRadius, CantorEffects.ROOTS);
		session.play(centre, ModSounds.BOSS_CANTOR_ROOTS, 1.6f, 1f);
	}

	/**
	 * An ally struck the roots holding {@code player}: they let go (the roots' Slowness lifted, the
	 * roots crumble at the player's feet). Returns false when the player was not held.
	 */
	public boolean free(ServerPlayer player) {
		if (!isRooted(player)) {
			return false;
		}

		rooted.remove(player.getUUID());
		release(player);
		FirstCantor c = session.cantor();
		session.event(player.position(), null, c != null ? c.getId() : -1, 0f, CantorEffects.ROOTS);
		session.play(player.position(), ModSounds.BOSS_CANTOR_ROOTS, 0.8f, 1.5f);
		return true;
	}

	/** Lifts the roots' Slowness only (another Slowness, a shock's, stays). */
	private static void release(ServerPlayer player) {
		MobEffectInstance slow = player.getEffect(MobEffects.SLOWNESS);

		if (slow != null && slow.getAmplifier() == ROOT_AMPLIFIER) {
			player.removeEffect(MobEffects.SLOWNESS);
		}
	}

	// ---------------------------------------------------------------- Mire

	private void spreadFog(FirstCantor c, RaidBalance b) {
		long now = session.level().getGameTime();
		int ticks = Math.max(1, b.fogTicks);

		for (Vec3 centre : spots(Math.max(0, b.fogPools))) {
			pools.add(new Pool(centre, now + 20, now + ticks));
			session.event(centre, new Vec3(ticks, 0, 0), c.getId(), b.fogWidth, CantorEffects.FOG);
			session.play(centre, ModSounds.BOSS_CANTOR_FOG, 1.2f, 0.9f + session.random().nextFloat() * 0.2f);
		}
	}

	/** Once a second, a second after they spread: {@code fogDamage} and Slowness I to everyone in a pool (once, however many). */
	private void tickPools(FirstCantor c, RaidBalance b) {
		long now = session.level().getGameTime();
		pools.removeIf(pool -> pool.until() <= now);

		if (pools.isEmpty()) {
			return;
		}

		Set<UUID> stung = new HashSet<>();

		for (Pool pool : pools) {
			if (now < pool.from() || (now - pool.from()) % 20 != 0) {
				continue;
			}

			for (ServerPlayer player : session.everyoneInside()) {
				if (stung.contains(player.getUUID()) || !within(player.position(), pool.centre(), b.fogWidth / 2.0)) {
					continue;
				}

				stung.add(player.getUUID());

				if (player.hurtServer(session.level(), session.hymnDamage(c), b.fogDamage)) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FOG_SLOW_TICKS, 0), c);
				}
			}
		}
	}

	// ---------------------------------------------------------------- Basalt

	private void markBasalt(FirstCantor c, RaidBalance b) {
		int count = RaidRules.basaltMarks(session.count(), b);
		marks.clear();
		marks.addAll(spots(count));
		basaltTell = Math.max(1, b.basaltTell);

		for (Vec3 mark : marks) {
			session.event(mark, null, c.getId(), b.basaltRadius, CantorEffects.MARK);
		}

		session.play(session.floorCentre(), ModSounds.BOSS_CANTOR_MARK, 1.6f, 1f);
	}

	private void tickMarks(FirstCantor c, RaidBalance b) {
		if (basaltTell < 0 || --basaltTell >= 0) {
			return;
		}

		for (Vec3 mark : marks) {
			StormCall.strikeUnderRoof(session.level(), mark, b.basaltRadius, b.basaltDamage, b.basaltShockTicks, c, e -> session.hits(c, e));
			session.event(mark, null, c.getId(), b.basaltRadius, CantorEffects.STRIKE);
			session.play(mark, ModSounds.BOSS_CANTOR_STRIKE, 1.2f, 0.9f + session.random().nextFloat() * 0.2f);
		}

		marks.clear();
	}

	// ---------------------------------------------------------------- spots

	/** {@code count} spots on the floor: under the participants inside first (shuffled), then random spots near him. */
	private List<Vec3> spots(int count) {
		List<Vec3> out = new ArrayList<>();
		List<ServerPlayer> inside = new ArrayList<>(session.participantsInside());
		Collections.shuffle(inside, new Random(session.random().nextLong()));

		for (ServerPlayer player : inside) {
			if (out.size() >= count) {
				break;
			}

			out.add(new Vec3(player.getX(), session.floorY(), player.getZ()));
		}

		while (out.size() < count) {
			out.add(session.randomFloorPoint(SPREAD));
		}

		return out;
	}

	/** Whether {@code pos} stands within {@code radius} of {@code centre} across the floor, and not far over or under it. */
	private static boolean within(Vec3 pos, Vec3 centre, double radius) {
		double dx = pos.x - centre.x;
		double dz = pos.z - centre.z;
		double dy = pos.y - centre.y;
		return dx * dx + dz * dz <= radius * radius && dy >= -1.0 && dy <= REACH_UP;
	}
}
