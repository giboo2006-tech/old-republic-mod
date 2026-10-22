package com.oldrepublic.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.quest.JournalEntries;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.CrossingStage;
import com.oldrepublic.quest.story.EncounterState;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryBalance;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryService;
import com.oldrepublic.quest.story.StorySites;
import com.oldrepublic.quest.story.StoryStructures;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.far.FarSites;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * The story's far sites (docs/MILESTONE_6.md 3.5, 8.2): the raider stronghold's people at its
 * anchors with the old camp's layout as the per-anchor fallback, the encampment's stamp rule and its
 * stamp that keeps what players put in the Crossing, the near search taking over when far placement
 * fails (the Crossing and the stronghold), a request still under way changing nothing, and the
 * journal's notes of the way.
 */
public class StoryFarGameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	private static final String NOTE_DIR = "quest.oldrepublic.far.note.dir.";
	private static final String NOTE_DIST = "quest.oldrepublic.far.note.dist.";

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static ServerPlayer player(GameTestHelper helper, String name) {
		FakePlayer player = FakePlayer.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
		return player;
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static int max(int... values) {
		int out = Integer.MIN_VALUE;

		for (int value : values) {
			out = Math.max(out, value);
		}

		return out;
	}

	private static int last(int[] values) {
		return values.length == 0 ? 0 : values[values.length - 1];
	}

	/** A piece of {@code template} with its local (0, 0, 0) at {@code origin}, turned by {@code rotation} (as runtime sites are placed). */
	private static @Nullable SitePiece piece(ServerLevel level, Identifier id, SiteKind kind, BlockPos origin, Rotation rotation) {
		Optional<StructureTemplate> template = level.getStructureManager().get(id);

		if (template.isEmpty()) {
			return null;
		}

		BoundingBox box = template.get().getBoundingBox(new StructurePlaceSettings().setRotation(rotation).setRotationPivot(BlockPos.ZERO), origin);
		return new SitePiece(kind, id, box, rotation, origin, SitePiece.runtimeKey(level.dimension(), origin));
	}

	/** Every cell of {@code box} (mutable positions, as {@link BlockPos#betweenClosed} gives them). */
	private static Iterable<BlockPos> cells(BoundingBox box) {
		return BlockPos.betweenClosed(new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()));
	}

	/** Whether two cells share a face. */
	private static boolean beside(BlockPos a, BlockPos b) {
		return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ()) == 1;
	}

	private static void discardMentors(ServerLevel level, StoryData data) {
		for (UUID id : new UUID[] {data.jediMentor, data.sithMentor}) {
			Entity mentor = id == null ? null : level.getEntity(id);

			if (mentor != null) {
				mentor.discard();
			}
		}
	}

	// ---------------------------------------------------------------- the raider stronghold's people

	@GameTest
	public void raiderSpotsUseAnchorsElseTheOldLayout(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos site = helper.absolutePos(new BlockPos(3, 2, 3));
		List<BlockPos> crew = StoryStructures.crewSpots(level, site);
		List<BlockPos> captives = StoryStructures.captiveSpots(level, site);
		BlockPos acolyte = StoryStructures.acolyteSpot(level, site);
		BlockPos captain = StoryStructures.captainSpot(level, site);

		// No piece (an old save's camp, the near search without blocks): every spot is the old layout's.
		StorySites.RaiderSpots bare = StorySites.raiderSpots(level, null, site);
		helper.assertTrue(bare.crew().equals(crew) && bare.captives().equals(captives) && bare.acolyte().equals(acolyte) && bare.captain().equals(captain),
				msg("without a piece every spot comes from the StoryStructures layout"));

		// A piece whose sidecar has none of the stronghold's anchors: still the old layout, anchor by anchor.
		SitePiece crossing = piece(level, StoryStructures.CROSSING, SiteKind.STORY_CAMP, site, Rotation.NONE);

		if (crossing != null) {
			StorySites.RaiderSpots fallback = StorySites.raiderSpots(level, crossing, site);
			helper.assertTrue(fallback.crew().equals(crew) && fallback.captives().equals(captives) && fallback.acolyte().equals(acolyte)
					&& fallback.captain().equals(captain), msg("a piece without CREW/ACOLYTE/CAPTAIN/CAPTIVE anchors falls back to the layout"));
		}

		// The stronghold itself, in two rotations: each anchor the sidecar has wins, the rest fall back by index.
		SiteAnchors.Anchor[] crewAnchors = {SiteAnchors.Anchor.CREW_1, SiteAnchors.Anchor.CREW_2, SiteAnchors.Anchor.CREW_3, SiteAnchors.Anchor.CREW_4,
				SiteAnchors.Anchor.CREW_5, SiteAnchors.Anchor.CREW_6, SiteAnchors.Anchor.CREW_7};
		SiteAnchors.Anchor[] captiveAnchors = {SiteAnchors.Anchor.CAPTIVE_1, SiteAnchors.Anchor.CAPTIVE_2, SiteAnchors.Anchor.CAPTIVE_3};

		for (Rotation rotation : new Rotation[] {Rotation.NONE, Rotation.CLOCKWISE_90}) {
			SitePiece camp = piece(level, StoryStructures.RAIDER_CAMP, SiteKind.RAIDER_STRONGHOLD, site.offset(-8, -1, -8), rotation);
			helper.assertTrue(camp != null, msg("the template " + StoryStructures.RAIDER_CAMP + " must load"));
			StorySites.RaiderSpots spots = StorySites.raiderSpots(level, camp, site);
			helper.assertTrue(spots.crew().size() == crew.size() && spots.captives().size() == captives.size(),
					msg(rotation + ": as many crew and captive spots as the layout has, got " + spots.crew().size() + " and " + spots.captives().size()));

			for (int i = 0; i < crew.size(); i++) {
				BlockPos anchor = i < crewAnchors.length ? SiteAnchors.find(camp, crewAnchors[i]) : null;
				BlockPos expected = anchor != null ? anchor : crew.get(i);
				helper.assertTrue(spots.crew().get(i).equals(expected), msg(rotation + ": crew " + (i + 1) + " stands at " + expected + ", got " + spots.crew().get(i)));
				helper.assertTrue(anchor == null || camp.box().isInside(anchor), msg(rotation + ": crew anchor " + (i + 1) + " lies inside the stronghold"));
			}

			for (int i = 0; i < captives.size(); i++) {
				BlockPos anchor = SiteAnchors.find(camp, captiveAnchors[i]);
				BlockPos expected = anchor != null ? anchor : captives.get(i);
				helper.assertTrue(spots.captives().get(i).equals(expected), msg(rotation + ": captive " + (i + 1) + " kneels at " + expected));
			}

			BlockPos acolyteAnchor = SiteAnchors.find(camp, SiteAnchors.Anchor.ACOLYTE);
			BlockPos captainAnchor = SiteAnchors.find(camp, SiteAnchors.Anchor.CAPTAIN);
			helper.assertTrue(spots.acolyte().equals(acolyteAnchor != null ? acolyteAnchor : acolyte), msg(rotation + ": the acolyte's spot"));
			helper.assertTrue(spots.captain().equals(captainAnchor != null ? captainAnchor : captain), msg(rotation + ": the Captain's spot"));
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the encampment

	@GameTest
	public void encampmentStampRule(GameTestHelper helper) {
		double distance = new StoryBalance().encampmentStampDistance;
		helper.assertTrue(distance == 64, msg("story.encampmentStampDistance defaults to 64 (docs/MILESTONE_6.md 3.5), is " + distance));
		BlockPos crossing = new BlockPos(100, 70, -40);
		helper.assertTrue(!StorySites.stampDue(crossing, crossing, distance), msg("standing on the Crossing: no stamp"));
		helper.assertTrue(!StorySites.stampDue(crossing.offset(63, 0, 0), crossing, distance), msg("63 blocks away: not yet"));
		helper.assertTrue(StorySites.stampDue(crossing.offset(0, 0, -64), crossing, distance), msg("exactly 64 blocks away: stamped"));
		helper.assertTrue(StorySites.stampDue(crossing.offset(46, 0, 45), crossing, distance), msg("64.4 blocks away on a diagonal: stamped"));
		helper.assertTrue(!StorySites.stampDue(crossing.offset(45, 0, 45), crossing, distance), msg("63.6 blocks away on a diagonal: not yet"));
		helper.assertTrue(!StorySites.stampDue(crossing.offset(10, 200, 10), crossing, distance), msg("height does not count, only the horizontal distance"));

		// The stamp reuses the Crossing's origin and rotation, so the encampments must keep its size.
		ServerLevel level = helper.getLevel();
		Optional<StructureTemplate> base = level.getStructureManager().get(StoryStructures.CROSSING);
		helper.assertTrue(base.isPresent(), msg("the template " + StoryStructures.CROSSING + " must load"));
		helper.assertTrue(StoryStructures.encampment(MentorRole.JEDI).equals(StoryStructures.JEDI_ENCAMPMENT)
				&& StoryStructures.encampment(MentorRole.SITH).equals(StoryStructures.SITH_ENCAMPMENT), msg("each side's encampment template"));

		for (Identifier id : new Identifier[] {StoryStructures.JEDI_ENCAMPMENT, StoryStructures.SITH_ENCAMPMENT}) {
			Optional<StructureTemplate> camp = level.getStructureManager().get(id);
			helper.assertTrue(camp.isEmpty() || camp.get().getSize().equals(base.get().getSize()),
					msg(id + " has size " + camp.map(StructureTemplate::getSize).orElse(null) + ", the Crossing " + base.get().getSize()));
		}

		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 20)
	public void encampmentStampKeepsWhatPlayersPut(GameTestHelper helper) {
		// The split: every cell of the box but the kept ones (a repeat and a cell outside change nothing), each in one part.
		BoundingBox box = new BoundingBox(0, 0, 0, 4, 3, 4);
		List<BlockPos> keep = List.of(new BlockPos(0, 0, 0), new BlockPos(2, 1, 3), new BlockPos(4, 3, 4), new BlockPos(2, 1, 3), new BlockPos(9, 9, 9));
		List<BoundingBox> parts = StorySites.without(box, keep);
		helper.assertTrue(StorySites.without(box, List.of()).equals(List.of(box)), msg("with nothing kept the box is stamped whole"));

		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int y = box.minY(); y <= box.maxY(); y++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					BlockPos cell = new BlockPos(x, y, z);
					long holders = parts.stream().filter(part -> part.isInside(cell)).count();
					helper.assertTrue(holders == (keep.contains(cell) ? 0 : 1), msg(cell + " lies in " + holders + " parts"));
				}
			}
		}

		// A base (the Jedi travel camp, turned) and a reference stamp of the overlay (the Sith travel camp, same size) on clear arena floor.
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(7, 1, 8));
		BlockPos reference = helper.absolutePos(new BlockPos(21, 1, 8));
		BlockPos shift = reference.subtract(origin);
		SitePiece base = RuntimeSites.stamp(level, SiteKind.STORY_CAMP, StoryStructures.JEDI_CAMP, origin, Rotation.CLOCKWISE_90);
		SitePiece overlay = RuntimeSites.stamp(level, SiteKind.STORY_CAMP, StoryStructures.SITH_CAMP, reference, Rotation.CLOCKWISE_90);
		helper.assertTrue(base != null && overlay != null && base.box().getXSpan() == overlay.box().getXSpan()
				&& base.box().getYSpan() == overlay.box().getYSpan() && base.box().getZSpan() == overlay.box().getZSpan(),
				msg("the travel camps must load, with one size"));

		// Untouched, only the base's containers are kept: its built blocks (fences, lanterns, the path), its beds and its campfire are its own.
		Set<BlockPos> before = new HashSet<>(StorySites.keptCells(level, base.box(), base));

		for (BlockPos cell : cells(base.box())) {
			boolean container = level.getBlockEntity(cell) instanceof Container;
			helper.assertTrue(container == before.contains(cell), msg(cell + " (" + level.getBlockState(cell) + ") kept=" + before.contains(cell) + ", container="
					+ container));
		}

		// A player's chest (with a diamond) and planks where the overlay builds and the base left air.
		List<BlockPos> open = new ArrayList<>();

		for (BlockPos cell : cells(base.box())) {
			if (level.getBlockState(cell).isAir() && !level.getBlockState(cell.offset(shift)).isAir()) {
				open.add(cell.immutable());
			}
		}

		helper.assertTrue(open.size() >= 2, msg("the overlay builds where the base left air (" + open.size() + " cells)"));
		BlockPos chestAt = open.getFirst();
		BlockPos planksAt = open.getLast();
		level.setBlock(chestAt, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(planksAt, Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);

		if (level.getBlockEntity(chestAt) instanceof ChestBlockEntity chest) {
			chest.setItem(0, new ItemStack(Items.DIAMOND));
		}

		List<BlockPos> kept = StorySites.keptCells(level, base.box(), base);
		helper.assertTrue(kept.contains(chestAt) && kept.contains(planksAt) && kept.size() == before.size() + 2,
				msg("the chest and the planks are kept besides the base's containers, got " + kept.size() + " cells for " + before.size() + " + 2"));
		List<Block> keptBlocks = kept.stream().map(cell -> level.getBlockState(cell).getBlock()).toList();

		// The stamp over the base: the kept cells stay, the rest of what the overlay builds is the overlay's.
		helper.assertTrue(StorySites.stampOver(level, StoryStructures.SITH_CAMP, base), msg("the stamp over the base goes ahead"));

		for (int i = 0; i < kept.size(); i++) {
			helper.assertTrue(level.getBlockState(kept.get(i)).getBlock() == keptBlocks.get(i), msg(kept.get(i) + " was kept as " + keptBlocks.get(i)));
		}

		helper.assertTrue(level.getBlockEntity(chestAt) instanceof ChestBlockEntity chest && chest.getItem(0).is(Items.DIAMOND),
				msg("the player's chest still holds its diamond"));
		int built = 0;

		for (BlockPos cell : cells(base.box())) {
			BlockState wanted = level.getBlockState(cell.offset(shift));

			if (wanted.isAir() || kept.contains(cell) || kept.stream().anyMatch(other -> beside(other, cell))) {
				continue;
			}

			helper.assertTrue(level.getBlockState(cell).getBlock() == wanted.getBlock(), msg(cell + " holds " + level.getBlockState(cell) + ", the overlay " + wanted));
			built++;
		}

		helper.assertTrue(built > 0, msg("the overlay built something"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- far placement failing, and under way

	@GameTest
	public void crossingFallsBackToTheNearSearch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, "far_crossing");
		StoryData data = Story.get(player);
		data.crossingStage = CrossingStage.VOICES.ordinal();
		Story.commit(player, data);

		try {
			StorySites.settleCrossing(player, new FarSites.Status(FarSites.Phase.CHOOSING, null, null, "choosing"));
			helper.assertTrue(Story.get(player).meeting == null, msg("a request still choosing sets no meeting point"));
			BlockPos target = player.blockPosition().offset(400, 0, 0);
			StorySites.settleCrossing(player, new FarSites.Status(FarSites.Phase.LOADING, target, null, "loading"));
			helper.assertTrue(Story.get(player).meeting == null, msg("a request still loading sets no meeting point (guidance only points at its target)"));
			helper.assertTrue(ChapterThreeRules.notes(Story.get(player), JournalEntries.TWO_VOICES).isEmpty(), msg("no note of the way before the meeting point"));

			StorySites.settleCrossing(player, new FarSites.Status(FarSites.Phase.FAILED, null, null, "no_dry_ground"));
			StoryData after = Story.get(player);
			StoryBalance b = StoryService.balance();
			int reach = max(last(b.meetingRings), last(b.meetingFallback), b.fallbackForward) + 2;
			helper.assertTrue(after.meeting != null, msg("a failed far request falls back to the near search: a meeting point is set"));
			double distance = horizontal(after.meeting, player.blockPosition());
			helper.assertTrue(distance <= reach, msg("the near search's meeting point lies within " + reach + " blocks, got " + distance));
			helper.assertTrue(after.crossing() == CrossingStage.VOICES, msg("the voices still call until the player arrives"));
			List<String> notes = ChapterThreeRules.notes(after, JournalEntries.TWO_VOICES);
			helper.assertTrue(notes.size() == 2 && notes.get(0).startsWith(NOTE_DIR) && notes.get(1).startsWith(NOTE_DIST),
					msg("the journal notes the way (direction, then distance) under two_voices, got " + notes));

			BlockPos meeting = after.meeting;
			StorySites.settleCrossing(player, new FarSites.Status(FarSites.Phase.PLACED, target, null, "point"));
			helper.assertTrue(meeting.equals(Story.get(player).meeting), msg("a later answer does not move a meeting point already set"));
			discardMentors(level, Story.get(player));

			// A ground point placed far away (story.placeStructures off): the meeting is its target.
			ServerPlayer second = player(helper, "far_crossing_point");
			StoryData other = Story.get(second);
			other.crossingStage = CrossingStage.VOICES.ordinal();
			Story.commit(second, other);
			BlockPos far = second.blockPosition().offset(-30, 0, 420);
			StorySites.settleCrossing(second, new FarSites.Status(FarSites.Phase.PLACED, far, null, "point"));
			helper.assertTrue(far.equals(Story.get(second).meeting), msg("a placed ground point is the meeting point, got " + Story.get(second).meeting));
			discardMentors(level, Story.get(second));
		} finally {
			discardMentors(level, Story.get(player));
		}

		helper.succeed();
	}

	@GameTest
	public void strongholdFallsBackToTheNearSearch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = player(helper, "far_stronghold");
		StoryData data = Story.get(player);
		data.crossingStage = CrossingStage.DECIDED.ordinal();
		data.path = Path.INDEPENDENT.id();
		Story.commit(player, data);
		StoryBalance b = StoryService.balance();
		boolean structures = b.placeStructures;
		b.placeStructures = false;

		try {
			StorySites.settleStronghold(player, new FarSites.Status(FarSites.Phase.CHOOSING, null, null, "choosing"));
			StorySites.settleStronghold(player, new FarSites.Status(FarSites.Phase.LOADING, player.blockPosition().offset(0, 0, 500), null, "loading"));
			helper.assertTrue(Story.get(player).encounterState() == EncounterState.NOT_PLACED && Story.get(player).encounterPos == null,
					msg("a request under way places nothing"));

			StorySites.settleStronghold(player, new FarSites.Status(FarSites.Phase.FAILED, null, null, "load_timeout"));
			StoryData after = Story.get(player);
			helper.assertTrue(after.encounterState() == EncounterState.PLACED && after.encounterPos != null,
					msg("a failed far request falls back to the near search: the site is set, got " + after.encounterState()));
			int forward = max(b.fallbackForward * 2, StorySites.halfSide(level, StoryStructures.RAIDER_CAMP, StoryStructures.RAIDER_SIZE / 2) + 8);
			int reach = max(b.encounterMaxDist, last(b.encounterFallback), forward) + 2;
			double distance = horizontal(after.encounterPos, player.blockPosition());
			helper.assertTrue(distance <= reach, msg("the near search's site lies within " + reach + " blocks of an Independent, got " + distance));
			List<String> notes = ChapterThreeRules.notes(after, JournalEntries.SMOKE_ON_THE_RIDGE);
			helper.assertTrue(notes.size() == 2 && notes.get(0).startsWith(NOTE_DIR) && notes.get(1).startsWith(NOTE_DIST),
					msg("the journal notes the way under smoke_on_the_ridge, got " + notes));

			BlockPos site = after.encounterPos;
			StorySites.settleStronghold(player, new FarSites.Status(FarSites.Phase.PLACED, site.offset(300, 0, 0), null, "point"));
			helper.assertTrue(site.equals(Story.get(player).encounterPos), msg("a later answer does not move a site already placed"));
		} finally {
			b.placeStructures = structures;
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- the journal's notes of the way

	@GameTest
	public void notesOfTheWay(GameTestHelper helper) {
		BlockPos from = new BlockPos(0, 64, 0);
		helper.assertTrue(StorySites.directionNote(from, new BlockPos(0, 64, -550)).equals(NOTE_DIR + "n"), msg("-z is north"));
		helper.assertTrue(StorySites.directionNote(from, new BlockPos(400, 64, 400)).equals(NOTE_DIR + "se"), msg("+x +z is south-east"));
		helper.assertTrue(StorySites.directionNote(from, new BlockPos(-500, 64, 0)).equals(NOTE_DIR + "w"), msg("-x is west"));
		helper.assertTrue(StorySites.distanceNote(from, new BlockPos(60, 64, 0)).equals(NOTE_DIST + "close"), msg("under 100 blocks is close"));
		helper.assertTrue(StorySites.distanceNote(from, new BlockPos(0, 64, -550)).equals(NOTE_DIST + "6"), msg("550 blocks rounds to six hundred"));
		helper.assertTrue(StorySites.distanceNote(from, new BlockPos(0, 64, 449)).equals(NOTE_DIST + "4"), msg("449 blocks rounds to four hundred"));
		helper.assertTrue(StorySites.distanceNote(from, new BlockPos(1000, 64, 0)).equals(NOTE_DIST + "10"), msg("1000 blocks is ten hundred"));
		helper.assertTrue(StorySites.distanceNote(from, new BlockPos(2000, 64, 0)).equals(NOTE_DIST + "far"), msg("past 1000 blocks is far"));

		List<String> keys = new ArrayList<>();

		for (String dir : new String[] {"n", "ne", "e", "se", "s", "sw", "w", "nw"}) {
			keys.add(NOTE_DIR + dir);
		}

		keys.add(NOTE_DIST + "close");
		keys.add(NOTE_DIST + "far");

		for (int hundreds = 1; hundreds <= 10; hundreds++) {
			keys.add(NOTE_DIST + hundreds);
		}

		JsonObject lang = mergedLang();
		helper.assertTrue(lang != null, msg("the merged assets/oldrepublic/lang/en_us.json must be on the classpath"));

		for (String key : keys) {
			helper.assertTrue(lang.has(key), msg("the lang key " + key + " has a text"));
		}

		helper.succeed();
	}

	/** The build's merged English lang file (src/main/lang-parts), or null when it cannot be read. */
	private static @Nullable JsonObject mergedLang() {
		try (InputStream in = StoryFarGameTests.class.getResourceAsStream("/assets/oldrepublic/lang/en_us.json")) {
			return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}
}
