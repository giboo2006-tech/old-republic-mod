package com.oldrepublic.mission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Everything the mission feature remembers about a player (spec 5.7, 12.1): the active mission, the
 * story missions done, today's contract and board use, own-side contracts toward rank 3, today's
 * duty, the site kinds discovered, the sites surveyed and the interrogation pull. Lives in the
 * persistent, copy-on-death player attachment {@link MissionAttachments#MISSIONS}, synced to the
 * owning client only and sealed like the other player attachments.
 *
 * <p>Mutable on the server; change it only through {@code MissionService} (which commits with a
 * sealed copy and reads it again afterwards) or {@link MissionRules} (pure rules on a detached copy).
 */
public final class MissionData {
	public static final int MAX_STORY_DONE = 8;
	public static final int MAX_USED_OFFERS = 16;
	public static final int MAX_OWN_SIDE = 4;
	public static final int MAX_DISCOVERED = 16;
	public static final int MAX_SURVEYED = 32;
	public static final int MAX_CAMEOS = 8;

	/** Own-side keys: the issuing army's faction id, {@code frontier}, or this one for mentor duties. */
	public static final String OWN_SIDE_MENTOR = "mentor";

	/** Where the active mission stands. Synced by ordinal; append only. */
	public enum Stage {
		/** Accepted; its site or target is still being found. */
		PLACING,
		/** The site exists; the owner has not reached it yet. */
		TRAVEL,
		/** The owner reached the site; the objective counts. */
		ACTIVE,
		/** The fighting is done; a decision at the site is pending (R1's wounded, I2's sergeant). */
		DECIDE,
		/** The objective is done; a turn-in choice at an officer is pending (R2, I1). */
		RETURN
	}

	/** Bits of {@link Active#flags}. */
	public static final int FLAG_SPAWNED = 1;
	public static final int FLAG_ITEM_ISSUED = 1 << 1;
	public static final int FLAG_CHIEF_DONE = 1 << 2;
	public static final int FLAG_PEACEFUL = 1 << 3;
	/** I2: the downed Imperial has been tended (its standing is paid once). */
	public static final int FLAG_WOUNDED_TENDED = 1 << 4;
	/** The pending choice (R1 leave, I1 burn) was confirmed; the mission completes with it. */
	public static final int FLAG_CHOSEN = 1 << 5;

	/**
	 * The active mission.
	 *
	 * @see MissionData#active
	 */
	public static final class Active {
		static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(a -> a.id),
				Codec.STRING.optionalFieldOf("base", "").forGetter(a -> a.base),
				Codec.INT.optionalFieldOf("kind", 0).forGetter(a -> a.kind.ordinal()),
				Codec.INT.optionalFieldOf("stage", 0).forGetter(a -> a.stage.ordinal()),
				BlockPos.CODEC.optionalFieldOf("site").forGetter(a -> Optional.ofNullable(a.site)),
				Codec.INT.optionalFieldOf("progress", 0).forGetter(a -> a.progress),
				Codec.INT.optionalFieldOf("target", 0).forGetter(a -> a.target),
				Codec.INT.optionalFieldOf("started_day", 0).forGetter(a -> a.startedDay),
				Codec.LONG.optionalFieldOf("seed", 0L).forGetter(a -> a.seed),
				Codec.STRING.optionalFieldOf("issuer", "").forGetter(a -> a.issuer),
				BlockPos.CODEC.optionalFieldOf("board").forGetter(a -> Optional.ofNullable(a.board)),
				Codec.INT.optionalFieldOf("flags", 0).forGetter(a -> a.flags),
				Codec.STRING.optionalFieldOf("site_kind", "").forGetter(a -> a.siteKind),
				Codec.INT.optionalFieldOf("extra", 0).forGetter(a -> a.extra),
				Where.CODEC.optionalFieldOf("where", Where.HOME).forGetter(a -> new Where(a.siteDimension, a.boardDimension))
		).apply(i, Active::new));

		/**
		 * The levels of {@link #site} and {@link #board} (spec MILESTONE_4.md 3.6), nested to keep the
		 * codec under 16 fields. Saves without it are home-world missions.
		 */
		record Where(ResourceKey<Level> site, ResourceKey<Level> board) {
			static final Where HOME = new Where(Level.OVERWORLD, Level.OVERWORLD);
			static final Codec<Where> CODEC = RecordCodecBuilder.create(i -> i.group(
					Level.RESOURCE_KEY_CODEC.optionalFieldOf("site", Level.OVERWORLD).forGetter(Where::site),
					Level.RESOURCE_KEY_CODEC.optionalFieldOf("board", Level.OVERWORLD).forGetter(Where::board)
			).apply(i, Where::new));
		}

		/** The mission's id (a {@code data/oldrepublic/mission} file). */
		public String id;
		/** For a duty, the contract type it runs ({@code clear_camp}, ...); otherwise the id. */
		public String base;
		public MissionDefinition.Kind kind;
		public Stage stage;
		/** The site's stand (runtime sites), the structure point to reach (survey, secure), or null while placing. */
		public @Nullable BlockPos site;
		public int progress;
		public int target;
		public int startedDay;
		/** Seed of the mission's own rolls (briefing, duty type, loot). */
		public long seed;
		/** The issuing faction's id, or {@link MissionDefinition#MENTOR}. */
		public String issuer;
		/** Where it was taken (the terminal or the officer), where turn-ins point back to. */
		public @Nullable BlockPos board;
		/** {@link #FLAG_SPAWNED} and the other flag bits. */
		public int flags;
		/** The {@code SiteKind} id of the worldgen site a survey or secure contract works at; empty otherwise. */
		public String siteKind;
		/** A second counter some objectives keep (R1: wounded tended so far). */
		public int extra;
		/** The level of {@link #site}; the overworld for every M1–M3 mission. */
		public ResourceKey<Level> siteDimension = Level.OVERWORLD;
		/** The level of {@link #board}; the overworld for every M1–M3 mission. */
		public ResourceKey<Level> boardDimension = Level.OVERWORLD;

		public Active(String id, String base, MissionDefinition.Kind kind, String issuer, int startedDay, long seed, @Nullable BlockPos board) {
			this(id, base, kind.ordinal(), Stage.PLACING.ordinal(), Optional.empty(), 0, 0, startedDay, seed, issuer, Optional.ofNullable(board), 0, "", 0,
					Where.HOME);
		}

		private Active(String id, String base, int kind, int stage, Optional<BlockPos> site, int progress, int target, int startedDay, long seed,
				String issuer, Optional<BlockPos> board, int flags, String siteKind, int extra, Where where) {
			this.id = id;
			this.base = base.isEmpty() ? id : base;
			this.kind = MissionDefinition.Kind.values()[Math.floorMod(kind, MissionDefinition.Kind.values().length)];
			this.stage = Stage.values()[Math.floorMod(stage, Stage.values().length)];
			this.site = site.orElse(null);
			this.progress = progress;
			this.target = target;
			this.startedDay = startedDay;
			this.seed = seed;
			this.issuer = issuer;
			this.board = board.orElse(null);
			this.flags = flags;
			this.siteKind = siteKind;
			this.extra = extra;
			this.siteDimension = where.site();
			this.boardDimension = where.board();
		}

		Active copy() {
			return new Active(id, base, kind.ordinal(), stage.ordinal(), Optional.ofNullable(site), progress, target, startedDay, seed, issuer,
					Optional.ofNullable(board), flags, siteKind, extra, new Where(siteDimension, boardDimension));
		}

		/** {@link #site} with its level, or null. */
		public @Nullable GlobalPos siteGlobal() {
			return site == null ? null : GlobalPos.of(siteDimension, site);
		}

		/** {@link #board} with its level, or null. */
		public @Nullable GlobalPos boardGlobal() {
			return board == null ? null : GlobalPos.of(boardDimension, board);
		}

		public boolean has(int flag) {
			return (flags & flag) != 0;
		}

		public void set(int flag) {
			flags |= flag;
		}
	}

	/** A board offer already accepted, completed or abandoned today. */
	public record UsedOffer(BlockPos terminal, int index) {
		static final Codec<UsedOffer> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("terminal").forGetter(UsedOffer::terminal),
				Codec.INT.fieldOf("index").forGetter(UsedOffer::index)
		).apply(i, UsedOffer::new));
	}

	public static final Codec<MissionData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Active.CODEC.optionalFieldOf("active").forGetter(d -> Optional.ofNullable(d.active)),
			Codec.STRING.listOf().optionalFieldOf("story_done", List.of()).forGetter(d -> d.storyDone),
			Codec.INT.optionalFieldOf("contract_day", -1).forGetter(d -> d.contractDay),
			Codec.INT.optionalFieldOf("contracts_today", 0).forGetter(d -> d.contractsToday),
			Codec.INT.optionalFieldOf("board_day", -1).forGetter(d -> d.boardDay),
			UsedOffer.CODEC.listOf().optionalFieldOf("used_offers", List.of()).forGetter(d -> d.usedOffers),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("own_side", Map.of()).forGetter(d -> d.ownSide),
			Codec.INT.optionalFieldOf("duty_day", -1).forGetter(d -> d.dutyDay),
			Codec.INT.optionalFieldOf("duties_today", 0).forGetter(d -> d.dutiesToday),
			Codec.STRING.listOf().optionalFieldOf("discovered", List.of()).forGetter(d -> d.discovered),
			BlockPos.CODEC.listOf().optionalFieldOf("surveyed", List.of()).forGetter(d -> d.surveyed),
			Codec.LONG.optionalFieldOf("pull_until", 0L).forGetter(d -> d.pullUntil),
			Codec.STRING.listOf().optionalFieldOf("cameos", List.of()).forGetter(d -> d.cameos)
	).apply(i, MissionData::new));

	/** The active mission, or null. A player has at most one, story or contract. */
	public @Nullable Active active;
	/** Story missions completed, in order (at most {@link #MAX_STORY_DONE}). */
	public final List<String> storyDone = new ArrayList<>();
	/** The day {@link #contractsToday} counts, or -1. */
	public int contractDay = -1;
	/** Contracts and duties completed on {@link #contractDay}; the first {@code fullPayPerDay} pay in full. */
	public int contractsToday;
	/** The day {@link #usedOffers} belongs to, or -1. */
	public int boardDay = -1;
	/** Board offers used today (at most {@link #MAX_USED_OFFERS}). */
	public final List<UsedOffer> usedOffers = new ArrayList<>();
	/** Contracts completed per issuer key (faction id or {@link #OWN_SIDE_MENTOR}), at most {@link #MAX_OWN_SIDE} keys. */
	public final Map<String, Integer> ownSide = new HashMap<>();
	/** The day the last mentor duty was taken, or -1. */
	public int dutyDay = -1;
	/** Mentor duties taken on {@link #dutyDay} (a set day counts at least one; {@code missions.dutiesPerDay}). */
	public int dutiesToday;
	/** {@code SiteKind} ids seen at least once (at most {@link #MAX_DISCOVERED}). */
	public final List<String> discovered = new ArrayList<>();
	/** Heart positions of the sites already surveyed (at most {@link #MAX_SURVEYED}, oldest dropped). */
	public final List<BlockPos> surveyed = new ArrayList<>();
	/** Game time until which the interrogation pull (or the freed settler's lead) lasts; 0 for none. */
	public long pullUntil;
	/** One-time Chapter Two cameos already played (at most {@link #MAX_CAMEOS}), such as {@code settler}. */
	public final List<String> cameos = new ArrayList<>();

	/** Frozen copy for syncing (see {@link #syncView()}); never copied, persisted or mutated. */
	private volatile @Nullable MissionData syncView;

	public MissionData() {
	}

	private MissionData(Optional<Active> active, List<String> storyDone, int contractDay, int contractsToday, int boardDay, List<UsedOffer> usedOffers,
			Map<String, Integer> ownSide, int dutyDay, int dutiesToday, List<String> discovered, List<BlockPos> surveyed, long pullUntil,
			List<String> cameos) {
		this.active = active.map(Active::copy).orElse(null);
		this.storyDone.addAll(storyDone.subList(0, Math.min(MAX_STORY_DONE, storyDone.size())));
		this.contractDay = contractDay;
		this.contractsToday = contractsToday;
		this.boardDay = boardDay;
		this.usedOffers.addAll(usedOffers.subList(0, Math.min(MAX_USED_OFFERS, usedOffers.size())));
		ownSide.entrySet().stream().limit(MAX_OWN_SIDE).forEach(e -> this.ownSide.put(e.getKey(), e.getValue()));
		this.dutyDay = dutyDay;
		this.dutiesToday = dutiesToday;
		this.discovered.addAll(discovered.subList(0, Math.min(MAX_DISCOVERED, discovered.size())));
		this.surveyed.addAll(surveyed.subList(Math.max(0, surveyed.size() - MAX_SURVEYED), surveyed.size()));
		this.pullUntil = pullUntil;
		this.cameos.addAll(cameos.subList(0, Math.min(MAX_CAMEOS, cameos.size())));
	}

	public MissionData copy() {
		return new MissionData(Optional.ofNullable(active), storyDone, contractDay, contractsToday, boardDay, usedOffers, ownSide, dutyDay, dutiesToday,
				discovered, surveyed, pullUntil, cameos);
	}

	/**
	 * Takes the frozen copy that syncing sends ({@link #syncView()}) and returns this. Server thread
	 * only; the commit, the attachment initializer and loading call it.
	 */
	MissionData seal() {
		this.syncView = copy();
		return this;
	}

	/**
	 * What the network sends for this object: the copy taken by the last {@link #seal()}, which
	 * nothing ever mutates (Fabric encodes attachment syncs later, on the network thread).
	 */
	MissionData syncView() {
		MissionData view = syncView;
		return view != null ? view : this;
	}

	public boolean storyDone(String missionId) {
		return storyDone.contains(missionId);
	}

	public boolean discovered(String siteKindId) {
		return discovered.contains(siteKindId);
	}

	/** Records a story mission as done; false when it already was or the list is full. */
	public boolean markStoryDone(String missionId) {
		if (storyDone.contains(missionId) || storyDone.size() >= MAX_STORY_DONE) {
			return false;
		}

		storyDone.add(missionId);
		return true;
	}

	/** Records a site kind as discovered; true the first time. */
	public boolean markDiscovered(String siteKindId) {
		if (discovered.contains(siteKindId) || discovered.size() >= MAX_DISCOVERED) {
			return false;
		}

		discovered.add(siteKindId);
		return true;
	}

	/** Records a surveyed heart, dropping the oldest past {@link #MAX_SURVEYED}. */
	public void markSurveyed(BlockPos heart) {
		if (surveyed.contains(heart)) {
			return;
		}

		surveyed.add(heart.immutable());

		while (surveyed.size() > MAX_SURVEYED) {
			surveyed.removeFirst();
		}
	}

	/** Records a one-time cameo as played; true the first time. */
	public boolean markCameo(String cameo) {
		if (cameos.contains(cameo) || cameos.size() >= MAX_CAMEOS) {
			return false;
		}

		cameos.add(cameo);
		return true;
	}

	/** Contracts completed for the issuer key (see {@link #ownSide}). */
	public int ownSide(String key) {
		return ownSide.getOrDefault(key, 0);
	}
}
