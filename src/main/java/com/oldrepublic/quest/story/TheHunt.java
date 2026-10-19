package com.oldrepublic.quest.story;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.item.SchematicItem;
import com.oldrepublic.dialogue.DialogueSession;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.enemy.mandalorian.Hunters;
import com.oldrepublic.enemy.mandalorian.MandalorianHunter;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.PlanetSites;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.Journal;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.travel.ShipBalance;
import com.oldrepublic.travel.ShipRecord;
import com.oldrepublic.travel.ShipRegistry;
import com.oldrepublic.travel.ShipRules;
import com.oldrepublic.travel.ShipState;
import com.oldrepublic.travel.Ships;
import com.oldrepublic.vendor.VendorItems;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The hunt (spec MILESTONE_4.md 8.5, 8.7): the sabotage when the ship lands at home, the two
 * ambushes and their retries, the three clues, the hunter bounties of the ongoing loop, and the
 * Peaceful rule (no hunter spawns; their clues come by journal on the same triggers). All of it runs
 * from FIRST_FLIGHT on whatever the stage, recorded in {@code Chapter4.hunt}; HUNTED only begins
 * when the world arc ends and counts what is already held (8.1).
 */
public final class TheHunt {
	static final String BOUNTY_TAG = "oldrepublic.c4_bounty";
	/** The Mandalorian set's schematic pieces a hunter can drop (7.3, 8.7). */
	private static final String[] MANDALORIAN_PIECES = {"mandalorian_helmet", "mandalorian_chest", "mandalorian_legs", "mandalorian_boots"};
	/** The open sky check for "a planet surface". */
	private static final int SKY_MARGIN = 0;

	/** An ambush in progress (memory only; its hunters persist with owner tags). */
	private record Ambush(int which, ResourceKey<Level> dimension, BlockPos centre, List<UUID> members) {
	}

	private static final Map<UUID, Ambush> AMBUSHES = new HashMap<>();
	private static final Map<UUID, Long> NEXT_BOUNTY_CHECK = new HashMap<>();

	// ---------------------------------------------------------------- stages

	/** HUNTED begins (only on leaving WORLD_ARC): the objective counts the clues already held; all three and the level pass straight on. */
	static void onHunted(ServerPlayer p) {
		StoryData data = Story.get(p);
		int clues = Integer.bitCount(data.chapter4.hunt().clues());
		p.sendSystemMessage(Component.translatable("quest.oldrepublic.c4.objective.hunted", clues, ChapterFour.MAX_CLUE));
		Journal.unlockCodex(p, JournalEntries.CODEX_TORVIN_KAAST);
		checkChallenge(p);
	}

	/** HUNTED to CHALLENGED: three clues and the duel level; below it the journal says why. */
	static void checkChallenge(ServerPlayer p) {
		StoryData data = Story.get(p);
		ChapterFourBalance b = ChapterFourService.balance();
		int level = Progression.get(p).level;

		if (ChapterFourRules.challengeReady(data.chapter4, level, b)) {
			ChapterFourService.enterStage(p, ChapterFourStage.CHALLENGED);
		} else if (data.chapter4.stage() == ChapterFourStage.HUNTED && Integer.bitCount(data.chapter4.hunt().clues()) >= ChapterFour.MAX_CLUE
				&& ChapterFourService.due(p, "not_ready")) {
			ChapterFourService.retryLater(p, "not_ready", b.retryTicks * 10L);
			p.sendSystemMessage(Component.translatable("quest.oldrepublic.c4.objective.not_ready", b.duelLevel));
		}
	}

	// ---------------------------------------------------------------- clues

	/** A clue was recorded (by W6's yield or droid death, the sabotage, or the Peaceful journal): its XP, entries and the stage. */
	static void onClue(ServerPlayer p, int clue) {
		ChapterFourBalance b = ChapterFourService.balance();
		StoryData data = Story.get(p);
		StoryData.Chapter4.Hunt hunt = data.chapter4.hunt();

		switch (clue) {
			case 1 -> {
				ChapterFourService.xp(p, b.sabotageXp);
				Journal.unlockEntry(p, JournalEntries.SABOTAGE);
				Journal.unlockCodex(p, JournalEntries.CODEX_TORVIN_KAAST);
			}
			case 2 -> {
				data.chapter4 = data.chapter4.withHunt(hunt.withAmbush(hunt.ambushDay() >= 0 ? hunt.ambushDay() : ChapterFourService.day(p),
						Math.max(1, hunt.ambushes())));
				Planets.Planet world = Planets.Planet.of(p.level().dimension());
				data.flags.add(ChapterFourRules.AMBUSH_WORLD_PREFIX + (world == null ? "home" : world.id()));
				Story.commit(p, data);
				ChapterFourService.xp(p, b.ambushXp);
				Journal.unlockEntry(p, JournalEntries.FIRST_AMBUSH);
				openUnlessShowing(p, OldRepublicMod.id("c4_hunter_yield"));
			}
			case 3 -> {
				data.chapter4 = data.chapter4.withHunt(hunt.withAmbush(hunt.ambushDay(), Math.max(2, hunt.ambushes())));
				Story.commit(p, data);
				ChapterFourService.xp(p, b.ambushXp);
				Journal.unlockEntry(p, JournalEntries.THE_BUYER);
				Journal.unlockCodex(p, JournalEntries.CODEX_THE_BUYER);
				Rewards.give(p, new ItemStack(VendorItems.SEALED_CONTRACT));
			}
			default -> {
			}
		}

		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.clue." + clue, Integer.bitCount(Story.get(p).chapter4.hunt().clues()),
				ChapterFour.MAX_CLUE));
		AMBUSHES.remove(p.getUUID());
		checkChallenge(p);
	}

	private static void openUnlessShowing(ServerPlayer p, Identifier dialogue) {
		DialogueSession open = Dialogues.session(p);

		if (open == null || !dialogue.equals(open.dialogue())) {
			Dialogues.open(p, dialogue, null);
		}
	}

	// ---------------------------------------------------------------- the sabotage

	/**
	 * The ship settled: the first time it lands at home after FIRST_FLIGHT (flown or recalled), the
	 * charge in the strut blows (hull 10, so it cannot jump until repaired to 20) and the datapad
	 * beside it names Kaast (clue 1). Only the ship's own landing triggers it.
	 */
	static void onShipStateChanged(ServerPlayer owner, ShipState from, ShipState to) {
		boolean settled = (from == ShipState.LANDING || from == ShipState.IN_TRANSIT) && (to == ShipState.GROUNDED || to == ShipState.READY
				|| to == ShipState.DISABLED);
		StoryData data = Story.get(owner);

		if (!settled || !ChapterFourRules.huntRunning(data.chapter4) || data.chapter4.hunt().sabotageDone() || !visitedPlanet(data)) {
			return;
		}

		ShipRecord ship = ShipRegistry.of(owner.level().getServer(), owner.getUUID()).orElse(null);

		if (ship == null || Planets.Planet.of(ship.pos().dimension()) != Planets.Planet.HOME) {
			return;
		}

		sabotage(owner, ship);
	}

	/** Whether the player has landed on a planet, any of them (the ship has left home since FIRST_FLIGHT began). */
	private static boolean visitedPlanet(StoryData data) {
		for (Planets.Planet planet : Planets.planets()) {
			if (data.hasFlag(ChapterFourRules.LANDED_PREFIX + planet.id())) {
				return true;
			}
		}

		return false;
	}

	/** Blows the charge: hull {@code ship.sabotageHull} (10) and DISABLED (4.4), the mark recorded, and clue 1. Public through the command for tests. */
	static void sabotage(ServerPlayer owner, @Nullable ShipRecord ship) {
		StoryData data = Story.get(owner);

		if (data.chapter4.hunt().sabotageDone()) {
			return;
		}

		data.chapter4 = data.chapter4.withHunt(data.chapter4.hunt().withSabotage());
		Story.commit(owner, data);

		if (ship != null) {
			// Through Ships.update, which also plays the ship_damage burst on the body (W3).
			ShipBalance b = ShipBalance.get();
			Ships.update(owner.level().getServer(), owner.getUUID(), r -> {
				ShipRecord damaged = r.withHull(Math.min(r.hull(), b.sabotageHull));
				return damaged.withState(ShipRules.restingState(damaged, b));
			});
		}

		owner.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.sabotage"));
		ChapterFour.addClue(owner, 1);
	}

	// ---------------------------------------------------------------- the ambushes

	/** The hunt's slow checks from FIRST_FLIGHT on: the ambushes and their retries, then the bounties. */
	static void tick(ServerPlayer p) {
		Ambush ambush = AMBUSHES.get(p.getUUID());

		if (ambush != null) {
			tickAmbush(p, ambush);
			return;
		}

		StoryData data = Story.get(p);
		long day = ChapterFourService.day(p);
		ChapterFourBalance b = ChapterFourService.balance();
		Planets.Planet world = Planets.Planet.of(p.level().dimension());

		if (world == null || !inTheOpen(p, world, b)) {
			return;
		}

		if (world != Planets.Planet.HOME && ChapterFourRules.ambushOneDue(data, day) && !ChapterFour.hasClue(p, 2)) {
			ambush(p, 1, ChapterFourService.peaceful(p));
		} else if (ChapterFourRules.ambushTwoDue(data, day, world.id(), b) && !ChapterFour.hasClue(p, 3)) {
			ambush(p, 2, ChapterFourService.peaceful(p));
		} else {
			checkBounty(p, data, day, b, world);
		}
	}

	/** On a world's open surface, at least {@code ambushMinDistance} from the hub and from any site. */
	private static boolean inTheOpen(ServerPlayer p, Planets.Planet world, ChapterFourBalance b) {
		ServerLevel level = p.level();

		if (!level.canSeeSky(p.blockPosition().above(SKY_MARGIN)) || p.isPassenger()) {
			return false;
		}

		if (world != Planets.Planet.HOME) {
			BlockPos hub = PlanetHubs.hub(level.getServer(), world);

			if (hub != null && p.position().distanceToSqr(Vec3.atCenterOf(hub)) < sq(b.ambushMinDistance)) {
				return false;
			}
		}

		return StructureLocator.piecesNear(level, p.blockPosition(), b.ambushMinDistance).isEmpty();
	}

	/**
	 * Ambush {@code which} (1 or 2) is due: the first brings 3 or 4 hunters, the second 2 hunters and
	 * a security droid. On Peaceful ({@code peaceful}, passed by the caller so the game tests need not
	 * change the server's difficulty) no one comes and the clue arrives by journal (8.8).
	 */
	public static void ambush(ServerPlayer p, int which, boolean peaceful) {
		boolean first = which == 1;
		int count = first ? ChapterFourRules.ambushSize(Progression.get(p).level, ChapterFourService.balance()) : 2;
		startAmbush(p, first ? 1 : 2, count, !first, peaceful);
	}

	/**
	 * An ambush drops in (W6's {@link Hunters#spawnAmbush}, at W1's {@code ambush_rocks} when it
	 * could be placed near, else on a ring 24 to 32 blocks out). On Peaceful no one comes: the clue
	 * arrives by journal instead.
	 */
	private static void startAmbush(ServerPlayer p, int which, int count, boolean withDroid, boolean peaceful) {
		if (peaceful) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.peaceful_clue"));
			ChapterFour.addClue(p, which + 1);
			return;
		}

		if (!ChapterFourService.due(p, "ambush")) {
			return;
		}

		ChapterFourBalance b = ChapterFourService.balance();
		ServerLevel level = p.level();
		Planets.Planet world = Planets.Planet.of(level.dimension());
		BlockPos at = null;

		if (world != null && world != Planets.Planet.HOME) {
			GlobalPos rocks = PlanetSites.ensure(p, which == 1 ? PlanetSites.Site.AMBUSH_ONE : PlanetSites.Site.AMBUSH_TWO, world);

			if (rocks != null && rocks.dimension().equals(level.dimension()) && p.position().distanceToSqr(Vec3.atCenterOf(rocks.pos())) <= sq(b.ambushRange[1] * 2)) {
				at = rocks.pos();
			}
		}

		if (at == null) {
			at = ringSpot(level, p, b);
		}

		List<LivingEntity> hunters = Hunters.spawnAmbush(level, at, count, p.getUUID(), withDroid);

		if (hunters.isEmpty()) {
			ChapterFourService.retryLater(p, "ambush", b.retryTicks);
			return;
		}

		List<UUID> ids = new ArrayList<>();

		for (LivingEntity hunter : hunters) {
			StoryTags.setOwner(hunter, p.getUUID());
			ids.add(hunter.getUUID());
		}

		AMBUSHES.put(p.getUUID(), new Ambush(which, level.dimension(), at, ids));
		StoryData data = Story.get(p);
		String site = which == 1 ? ChapterFourRules.SITE_AMBUSH_ONE : ChapterFourRules.SITE_AMBUSH_TWO;
		data.chapter4 = data.chapter4.withSites(data.chapter4.sites().with(site, GlobalPos.of(level.dimension(), at)));
		Story.commit(p, data);
		p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.ambush." + which));
	}

	/** A spot on the ground {@code ambushRange} blocks from the player, toward where they look. */
	private static BlockPos ringSpot(ServerLevel level, ServerPlayer p, ChapterFourBalance b) {
		RandomSource random = level.getRandom();
		int min = b.ambushRange[0];
		int max = Math.max(min, b.ambushRange.length > 1 ? b.ambushRange[1] : min);
		double distance = min + random.nextInt(max - min + 1);
		double angle = Math.toRadians(p.getYRot() + 90) + (random.nextDouble() - 0.5) * Math.PI;
		int x = (int) Math.floor(p.getX() + Math.cos(angle) * distance);
		int z = (int) Math.floor(p.getZ() + Math.sin(angle) * distance);
		int y = level.hasChunkAt(x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : p.getBlockY();
		return new BlockPos(x, y, z);
	}

	/**
	 * A running ambush: fled (out of the level or past {@code ambushLeaveRange}) it goes, to retry a
	 * day later; beaten without the clue arriving (every hunter dead before one could yield, or the
	 * droid gone) the clue is given here.
	 */
	private static void tickAmbush(ServerPlayer p, Ambush ambush) {
		ChapterFourBalance b = ChapterFourService.balance();
		ServerLevel level = p.level().getServer().getLevel(ambush.dimension());
		boolean fled = !p.level().dimension().equals(ambush.dimension()) || p.position().distanceToSqr(Vec3.atCenterOf(ambush.centre())) > sq(b.ambushLeaveRange);

		if (fled) {
			discard(level, ambush);
			AMBUSHES.remove(p.getUUID());
			StoryData data = Story.get(p);
			data.counters.put(ChapterFourRules.AMBUSH_RETRY_DAY, (int) (ChapterFourService.day(p) + b.ambushRetryDays));
			Story.commit(p, data);
			p.sendSystemMessage(Component.translatable("message.oldrepublic.story.c4.ambush.fled"));
			return;
		}

		if (level != null && alive(level, ambush) == 0) {
			AMBUSHES.remove(p.getUUID());
			ChapterFour.addClue(p, ambush.which() + 1);
		}
	}

	private static int alive(ServerLevel level, Ambush ambush) {
		int alive = 0;

		for (UUID id : ambush.members()) {
			Entity entity = level.getEntity(id);

			if (entity != null && entity.isAlive()) {
				alive++;
			}
		}

		return alive;
	}

	private static void discard(@Nullable ServerLevel level, Ambush ambush) {
		if (level == null) {
			return;
		}

		for (UUID id : ambush.members()) {
			Entity entity = level.getEntity(id);

			if (entity != null) {
				entity.discard();
			}
		}
	}

	/** The first ambush's world is recorded when its clue arrives; a planet landing has nothing more to do here. */
	static void onPlanetArrived(ServerPlayer p, Planets.Planet planet) {
		AMBUSHES.remove(p.getUUID());
	}

	// ---------------------------------------------------------------- hunter bounties (8.7)

	/** At most once a day, 10% per 1,200 ticks in the open on a planet: a lone hunter with a contract on the player. */
	private static void checkBounty(ServerPlayer p, StoryData data, long day, ChapterFourBalance b, Planets.Planet world) {
		if (world == Planets.Planet.HOME || !ChapterFourRules.hunterBountyOpen(data, day, b) || ChapterFourService.peaceful(p)) {
			return;
		}

		long now = StoryService.now(p);
		Long next = NEXT_BOUNTY_CHECK.get(p.getUUID());

		if (next == null) {
			NEXT_BOUNTY_CHECK.put(p.getUUID(), now + b.hunterCheckTicks);
			return;
		}

		if (now < next) {
			return;
		}

		NEXT_BOUNTY_CHECK.put(p.getUUID(), now + b.hunterCheckTicks);

		if (p.getRandom().nextFloat() >= b.hunterChance) {
			return;
		}

		List<LivingEntity> hunters = Hunters.spawnAmbush(p.level(), ringSpot(p.level(), p, b), 1, p.getUUID(), false);

		if (hunters.isEmpty()) {
			return;
		}

		for (LivingEntity hunter : hunters) {
			hunter.addTag(BOUNTY_TAG);
		}

		StoryData fresh = Story.get(p);
		fresh.counters.put(ChapterFourRules.HUNTER_DAY, (int) day);
		Story.commit(p, fresh);
		p.sendOverlayMessage(Component.translatable("message.oldrepublic.story.c4.bounty"));
	}

	/**
	 * A hunter killed by a player (7.3, 8.7, 10.1): a Mandalorian armour schematic piece on a
	 * {@code hunterSchematicChance} (8%) roll, guaranteed by the {@code hunterSchematicPity}th (10th)
	 * hunter, the pity counted per player. Only hunters whose loot counts roll (W6's anti-farm rule,
	 * {@link MandalorianHunter#mayDropLoot}: never one Kaast called, never another player's bounty
	 * farmed alone) and never anything a hub spawned. This is the only roll of that piece for
	 * hunters; W6's loot table has none.
	 */
	static void onHunterKilled(ServerPlayer p, LivingEntity hunter, boolean owned) {
		if (hunter.entityTags().contains(HubNpcs.HUB_TAG)) {
			return;
		}

		boolean counts = hunter instanceof MandalorianHunter mandalorian
				? mandalorian.mayDropLoot(p.level())
				: hunter.entityTags().contains(BOUNTY_TAG) && ChapterFourService.ownedBy(hunter, p);

		if (!counts) {
			return;
		}

		ChapterFourBalance b = ChapterFourService.balance();
		StoryData data = Story.get(p);
		int misses = data.counter(ChapterFourRules.HUNTER_PITY);
		boolean rolled = p.getRandom().nextFloat() < b.hunterSchematicChance;

		if (ChapterFourRules.pityDue(misses, rolled, b.hunterSchematicPity)) {
			data.counters.put(ChapterFourRules.HUNTER_PITY, 0);
			Story.commit(p, data);
			ItemStack piece = schematicPiece(p.getRandom());

			if (!piece.isEmpty()) {
				Rewards.give(p, piece);
			}
		} else {
			data.counters.put(ChapterFourRules.HUNTER_PITY, misses + 1);
			Story.commit(p, data);
		}
	}

	/** A Mandalorian armour schematic piece: W5's {@code schematic} item naming one piece; empty when W5's item is missing. */
	static ItemStack schematicPiece(RandomSource random) {
		return schematicItem(OldRepublicMod.id(MANDALORIAN_PIECES[random.nextInt(MANDALORIAN_PIECES.length)]));
	}

	/** W5's {@code schematic} item teaching {@code schematic} ({@link SchematicItem#of}). */
	static ItemStack schematicItem(Identifier schematic) {
		return SchematicItem.of(schematic);
	}

	// ---------------------------------------------------------------- lifecycle

	/** A logout or dimension change while an ambush runs counts as fleeing it (the hunters go; they come back a day later). */
	static void forget(ServerPlayer p) {
		Ambush ambush = AMBUSHES.remove(p.getUUID());

		if (ambush != null) {
			discard(p.level().getServer().getLevel(ambush.dimension()), ambush);
		}

		NEXT_BOUNTY_CHECK.remove(p.getUUID());
	}

	static void clear() {
		AMBUSHES.clear();
		NEXT_BOUNTY_CHECK.clear();
	}

	/** Whether an ambush is running for the player (tests). */
	public static boolean ambushRunning(ServerPlayer p) {
		return AMBUSHES.containsKey(p.getUUID());
	}

	private static double sq(double v) {
		return v * v;
	}

	private TheHunt() {
	}
}
