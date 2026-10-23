package com.oldrepublic.planet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.Attitude;
import com.oldrepublic.force.sense.SenseTargets;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.world.puzzle.CounterweightStoneBlock;
import com.oldrepublic.world.puzzle.PuzzleEffects;
import com.oldrepublic.world.puzzle.PuzzleSealBlock;
import com.oldrepublic.world.puzzle.PuzzleStates;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The expeditions' puzzles (spec MILESTONE_4.md 8.4), run for each owner while they are at their
 * own site. Puzzle progress lives in memory; what a player finished is saved in
 * {@link PlanetSiteData} ({@code done}), and the expedition is reported once
 * ({@link QuestHooks#onExpeditionDone}).
 *
 * <ul>
 * <li><b>The Singing Hollow.</b> Three of the grotto's six crystal clusters ({@code CHIME_1..6})
 * hum, in an order seeded by the owner. A Force Sense cast in the grotto reveals them and they
 * chime in order, each a higher tone ({@code crystal_answer} variant 0). The owner Pushes the
 * hanging counterweights ({@code WEIGHT_n}, under {@code CHIME_n}) in that order; a wrong stone
 * clunks and the order starts again. The third right stone opens the seal ({@code GATE}) and every
 * crystal answers (variant 2); the crystal at the bottom ({@code RELIC}) then answers the owner's
 * hand and the expedition is done.</li>
 * <li><b>The duskglass rift.</b> The owner takes hold of the crystal at the bottom ({@code RELIC},
 * use) and stays within reach while the whispers play for {@code planets.riftBleedTicks}; it bleeds
 * red and the expedition is done. Stepping away breaks it off.</li>
 * <li><b>The Wayfarer cache.</b> Each of its three platforms ({@code PLATFORM_1..3}) starts to
 * grind when anyone stands on it, drops away {@code planets.sinkingPlatformTicks} later and comes
 * back {@code planets.platformResetTicks} after that. Tessaly's teaching at the vault is W2's.</li>
 * </ul>
 */
public final class Expeditions {
	/** Ticks between two puzzle checks. */
	private static final int INTERVAL = 5;
	/** An owner is "at" a site within this many blocks of its piece's centre. */
	private static final int NEAR = 48;
	/** The rift's crystal must be held from this close. */
	private static final double BLEED_REACH = 4.0;
	/** Ticks between the Hollow's chimes. */
	private static final int CHIME_GAP = 20;
	/** How many clusters hum. */
	private static final int SEQUENCE = 3;

	private static final List<SiteAnchors.Anchor> CHIMES = List.of(SiteAnchors.Anchor.CHIME_1, SiteAnchors.Anchor.CHIME_2, SiteAnchors.Anchor.CHIME_3,
			SiteAnchors.Anchor.CHIME_4, SiteAnchors.Anchor.CHIME_5, SiteAnchors.Anchor.CHIME_6);
	private static final List<SiteAnchors.Anchor> WEIGHTS = List.of(SiteAnchors.Anchor.WEIGHT_1, SiteAnchors.Anchor.WEIGHT_2, SiteAnchors.Anchor.WEIGHT_3,
			SiteAnchors.Anchor.WEIGHT_4, SiteAnchors.Anchor.WEIGHT_5, SiteAnchors.Anchor.WEIGHT_6);
	private static final List<SiteAnchors.Anchor> PLATFORMS = List.of(SiteAnchors.Anchor.PLATFORM_1, SiteAnchors.Anchor.PLATFORM_2,
			SiteAnchors.Anchor.PLATFORM_3);

	/** One owner's Hollow: the humming order, how far they are into it, and the weights seen down. */
	static final class Hollow {
		final int[] order;
		int progress;
		boolean solved;
		final boolean[] down = new boolean[CHIMES.size()];

		Hollow(int[] order) {
			this.order = order;
		}
	}

	/** One platform of a cache: its cells and states, when it was stood on, when it drops back. */
	private static final class Platform {
		final List<BlockPos> cells;
		final List<BlockState> states = new ArrayList<>();
		long steppedAt = -1;
		long riseAt = -1;

		Platform(List<BlockPos> cells) {
			this.cells = cells;
		}
	}

	private record Chime(GlobalPos at, long when, int tone) {
	}

	private static final Map<UUID, Hollow> HOLLOWS = new HashMap<>();
	private static final Map<UUID, Long> BLEEDING = new HashMap<>();
	private static final Map<GlobalPos, Platform> PLATFORMS_STATE = new HashMap<>();
	private static final List<Chime> CHIMES_QUEUED = new ArrayList<>();
	private static final long ORDER_SALT = 0x484f4c4c4f57L;

	/**
	 * The humming order of an owner's Hollow: {@value #SEQUENCE} different cluster indexes (0 to 5),
	 * seeded by the owner. Public for the tests.
	 */
	public static int[] order(UUID owner) {
		RandomSource random = RandomSource.create(owner.getMostSignificantBits() ^ owner.getLeastSignificantBits() ^ ORDER_SALT);
		List<Integer> indexes = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
		int[] order = new int[SEQUENCE];

		for (int i = 0; i < SEQUENCE; i++) {
			order[i] = indexes.remove(random.nextInt(indexes.size()));
		}

		return order;
	}

	/**
	 * One step of the Hollow's rule (a test seam): {@code weight} was pushed down. Returns 1 for a
	 * right stone, 2 for the stone that solves it, 0 for a wrong stone (the order starts again).
	 */
	static int press(Hollow hollow, int weight) {
		if (hollow.solved) {
			return 1;
		}

		if (hollow.order[hollow.progress] != weight) {
			hollow.progress = 0;
			return 0;
		}

		hollow.progress++;

		if (hollow.progress >= SEQUENCE) {
			hollow.solved = true;
			return 2;
		}

		return 1;
	}

	/** A fresh Hollow for {@code owner} (tests). */
	static Hollow hollow(UUID owner) {
		return new Hollow(order(owner));
	}

	static void tick(MinecraftServer server) {
		long now = Clocks.time(server);
		playChimes(server, now);

		if (now % INTERVAL != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();

			if (!Planets.isPlanet(level) || !player.isAlive()) {
				BLEEDING.remove(player.getUUID());
				continue;
			}

			SitePiece grotto = ownPiece(level, player, PlanetSites.HOLLOW_GROTTO.template());

			if (grotto != null) {
				tickHollow(level, player, grotto);
			}

			tickRift(level, player, now);
		}

		tickPlatforms(server, now);
	}

	/** A Force Sense cast in the owner's grotto: the humming clusters show and chime in order. */
	static void collectSense(ServerPlayer caster, int blockRange, int creatureRange, Consumer<SenseTargets.Point> out) {
		ServerLevel level = caster.level();
		SitePiece grotto = Planets.isPlanet(level) ? ownPiece(level, caster, PlanetSites.HOLLOW_GROTTO.template()) : null;

		if (grotto == null || done(caster, PlanetSites.Site.SINGING_HOLLOW)) {
			return;
		}

		Hollow hollow = HOLLOWS.computeIfAbsent(caster.getUUID(), Expeditions::hollow);
		long now = Clocks.time(level.getServer());

		for (int i = 0; i < SEQUENCE; i++) {
			BlockPos cluster = SiteAnchors.find(grotto, CHIMES.get(hollow.order[i]));

			if (cluster != null) {
				out.accept(new SenseTargets.Point(SenseTargets.Kind.BLOCK, Vec3.atCenterOf(cluster), -1, Attitude.NEUTRAL));
				CHIMES_QUEUED.add(new Chime(GlobalPos.of(level.dimension(), cluster), now + (long) i * CHIME_GAP, i + 1));
			}
		}
	}

	/**
	 * The use hook: the crystal at the bottom of the owner's Hollow (once solved) or rift (starts the
	 * bleed). Returns SUCCESS when it consumed the use.
	 */
	static InteractionResult onUse(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();

		if (!Planets.isPlanet(level)) {
			return InteractionResult.PASS;
		}

		SitePiece grotto = ownPiece(level, player, PlanetSites.HOLLOW_GROTTO.template());

		if (grotto != null && pos.equals(SiteAnchors.find(grotto, SiteAnchors.Anchor.RELIC))) {
			Hollow hollow = HOLLOWS.get(player.getUUID());

			if (hollow == null || !hollow.solved) {
				return InteractionResult.PASS;
			}

			EffectEvents.around(level, PlanetEffects.CRYSTAL_ANSWER, Vec3.atCenterOf(pos), null, player.getId(), 1f, PlanetEffects.CRYSTAL_ANSWER_ALL);
			finish(player, PlanetSites.Site.SINGING_HOLLOW);
			return InteractionResult.SUCCESS;
		}

		SitePiece rift = ownPiece(level, player, PlanetSites.RIFT.template());

		if (rift != null && pos.equals(SiteAnchors.find(rift, SiteAnchors.Anchor.RELIC)) && !done(player, PlanetSites.Site.DUSKGLASS_RIFT)
				&& !BLEEDING.containsKey(player.getUUID())) {
			BLEEDING.put(player.getUUID(), Clocks.time(level.getServer()));
			EffectEvents.around(level, PlanetEffects.CRYSTAL_ANSWER, Vec3.atCenterOf(pos), null, player.getId(), PlanetEffects.BLEED_BEGIN,
					PlanetEffects.CRYSTAL_BLEED);
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	/** Forgets an owner's running puzzle state (logout); a held bleed breaks off. */
	static void forget(ServerPlayer player) {
		HOLLOWS.remove(player.getUUID());

		if (BLEEDING.remove(player.getUUID()) != null) {
			EffectEvents.around(player.level(), PlanetEffects.CRYSTAL_ANSWER, player.position(), null, player.getId(), PlanetEffects.BLEED_BROKEN,
					PlanetEffects.CRYSTAL_BLEED);
		}
	}

	static void clear() {
		HOLLOWS.clear();
		BLEEDING.clear();
		PLATFORMS_STATE.clear();
		CHIMES_QUEUED.clear();
	}

	private static void tickHollow(ServerLevel level, ServerPlayer player, SitePiece grotto) {
		if (done(player, PlanetSites.Site.SINGING_HOLLOW)) {
			return;
		}

		Hollow hollow = HOLLOWS.computeIfAbsent(player.getUUID(), Expeditions::hollow);

		if (hollow.solved) {
			openGate(level, grotto);
			return;
		}

		for (int i = 0; i < WEIGHTS.size(); i++) {
			BlockPos weight = SiteAnchors.find(grotto, WEIGHTS.get(i));
			BlockState state = weight == null ? null : level.getBlockState(weight);
			boolean down = state != null && state.getBlock() instanceof CounterweightStoneBlock && state.getValue(PuzzleStates.SUNK);

			if (down && !hollow.down[i]) {
				int result = press(hollow, i);
				BlockPos cluster = SiteAnchors.find(grotto, CHIMES.get(i));
				Vec3 at = Vec3.atCenterOf(cluster != null ? cluster : weight);

				if (result == 0) {
					EffectEvents.around(level, PlanetEffects.CRYSTAL_ANSWER, Vec3.atCenterOf(weight), null, player.getId(), 0f, PlanetEffects.CRYSTAL_CHIME);
				} else {
					EffectEvents.around(level, PlanetEffects.CRYSTAL_ANSWER, at, null, player.getId(), hollow.progress, PlanetEffects.CRYSTAL_CHIME);
				}

				if (result == 2) {
					openGate(level, grotto);
					BlockPos heart = SiteAnchors.find(grotto, SiteAnchors.Anchor.HEART);
					EffectEvents.around(level, PlanetEffects.CRYSTAL_ANSWER, Vec3.atCenterOf(heart != null ? heart : grotto.box().getCenter()), null, player.getId(), 1f,
							PlanetEffects.CRYSTAL_ANSWER_ALL);
				}
			}

			hollow.down[i] = down;
		}
	}

	/** Opens the Hollow's seal (it closes by the M3 rule when no one is near, and opens again while solved). */
	private static void openGate(ServerLevel level, SitePiece grotto) {
		for (BlockPos gate : SiteAnchors.all(grotto, SiteAnchors.Anchor.GATE)) {
			if (level.getBlockState(gate).getBlock() instanceof PuzzleSealBlock && !level.getBlockState(gate).getValue(PuzzleStates.OPEN)) {
				PuzzleSealBlock.open(level, gate);
			}
		}
	}

	private static void tickRift(ServerLevel level, ServerPlayer player, long now) {
		Long since = BLEEDING.get(player.getUUID());

		if (since == null) {
			return;
		}

		SitePiece rift = ownPiece(level, player, PlanetSites.RIFT.template());
		BlockPos relic = rift == null ? null : SiteAnchors.find(rift, SiteAnchors.Anchor.RELIC);

		if (relic == null || player.position().distanceToSqr(Vec3.atCenterOf(relic)) > BLEED_REACH * BLEED_REACH) {
			BLEEDING.remove(player.getUUID());
			Vec3 at = relic != null ? Vec3.atCenterOf(relic) : player.position();
			EffectEvents.around(level, PlanetEffects.CRYSTAL_ANSWER, at, null, player.getId(), PlanetEffects.BLEED_BROKEN, PlanetEffects.CRYSTAL_BLEED);
			return;
		}

		if (now - since >= PlanetBalance.get().riftBleedTicks) {
			BLEEDING.remove(player.getUUID());
			EffectEvents.around(level, PlanetEffects.CRYSTAL_ANSWER, Vec3.atCenterOf(relic), null, player.getId(), PlanetEffects.BLEED_DONE, PlanetEffects.CRYSTAL_BLEED);
			finish(player, PlanetSites.Site.DUSKGLASS_RIFT);
		}
	}

	/** The cache platforms of every owner who is at their cache: stepped on, dropped, risen. */
	private static void tickPlatforms(MinecraftServer server, long now) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			SitePiece cache = Planets.isStoryLevel(level) ? ownPiece(level, player, PlanetSites.CACHE.template()) : null;

			if (cache == null) {
				continue;
			}

			for (SiteAnchors.Anchor anchor : PLATFORMS) {
				List<BlockPos> cells = SiteAnchors.all(cache, anchor);

				if (!cells.isEmpty()) {
					PLATFORMS_STATE.computeIfAbsent(GlobalPos.of(level.dimension(), cells.getFirst()), at -> new Platform(cells));
				}
			}
		}

		for (Iterator<Map.Entry<GlobalPos, Platform>> it = PLATFORMS_STATE.entrySet().iterator(); it.hasNext();) {
			Map.Entry<GlobalPos, Platform> entry = it.next();
			ServerLevel level = server.getLevel(entry.getKey().dimension());
			Platform platform = entry.getValue();

			if (level == null || !level.hasChunkAt(entry.getKey().pos())) {
				if (platform.riseAt < 0) {
					it.remove();
				}

				continue;
			}

			stepPlatform(level, platform, now);
		}
	}

	private static void stepPlatform(ServerLevel level, Platform platform, long now) {
		PlanetBalance b = PlanetBalance.get();
		BlockPos centre = platform.cells.get(platform.cells.size() / 2);

		if (platform.riseAt >= 0) {
			if (now >= platform.riseAt) {
				for (int i = 0; i < platform.cells.size(); i++) {
					if (level.getBlockState(platform.cells.get(i)).isAir()) {
						level.setBlock(platform.cells.get(i), platform.states.get(i), Block.UPDATE_ALL);
					}
				}

				platform.riseAt = -1;
				platform.steppedAt = -1;
				platform.states.clear();
				PuzzleEffects.send(level, PuzzleEffects.COUNTERWEIGHT, centre, 1f, PuzzleEffects.COUNTERWEIGHT_RISE);
			}

			return;
		}

		if (platform.steppedAt < 0) {
			for (ServerPlayer player : level.players()) {
				if (player.onGround() && platform.cells.contains(player.getOnPos())) {
					platform.steppedAt = now;
					PuzzleEffects.send(level, PuzzleEffects.COUNTERWEIGHT, centre, 1f, PuzzleEffects.COUNTERWEIGHT_WARN);
					break;
				}
			}

			return;
		}

		if (now - platform.steppedAt >= b.sinkingPlatformTicks) {
			for (BlockPos cell : platform.cells) {
				platform.states.add(level.getBlockState(cell));
				level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}

			platform.riseAt = now + b.platformResetTicks;
			PuzzleEffects.send(level, PuzzleEffects.RUBBLE_CRUMBLE, centre, platform.cells.size(), PuzzleEffects.RUBBLE_COLLAPSE);
		}
	}

	private static void playChimes(MinecraftServer server, long now) {
		for (Iterator<Chime> it = CHIMES_QUEUED.iterator(); it.hasNext();) {
			Chime chime = it.next();

			if (now < chime.when()) {
				continue;
			}

			it.remove();
			ServerLevel level = server.getLevel(chime.at().dimension());

			if (level != null) {
				EffectEvents.around(level, PlanetEffects.CRYSTAL_ANSWER, Vec3.atCenterOf(chime.at().pos()), null, -1, chime.tone(), PlanetEffects.CRYSTAL_CHIME);
			}
		}
	}

	private static void finish(ServerPlayer player, PlanetSites.Site site) {
		if (PlanetSites.markDone(player.level().getServer(), player.getUUID(), site)) {
			HOLLOWS.remove(player.getUUID());
			QuestHooks.onExpeditionDone(player, site);
		}
	}

	private static boolean done(ServerPlayer player, PlanetSites.Site site) {
		PlanetSiteData.Owned entry = PlanetSiteData.owned(player.level().getServer(), player.getUUID(), site);
		return entry != null && entry.done();
	}

	/** The owner's own runtime piece of {@code template} in this level, when they are within {@value #NEAR} blocks of it. */
	private static @Nullable SitePiece ownPiece(ServerLevel level, ServerPlayer owner, Identifier template) {
		for (WorldSites.SiteRecord record : WorldSites.all(level.getServer())) {
			if (!template.equals(record.template()) || !owner.getUUID().equals(record.owner()) || !record.dimension().equals(level.dimension())) {
				continue;
			}

			SitePiece piece = RuntimeSites.pieceOf(level, record);

			if (piece != null && piece.box().inflatedBy(NEAR).isInside(owner.blockPosition())) {
				return piece;
			}
		}

		return null;
	}

	private Expeditions() {
	}
}
