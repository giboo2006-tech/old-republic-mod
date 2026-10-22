package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.construct.Sentinels;
import com.oldrepublic.force.ControlEffects;
import com.oldrepublic.force.ForceAct;
import com.oldrepublic.force.ForceBlockContext;
import com.oldrepublic.force.ForceBlockInteractions;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.pull.ForcePull;
import com.oldrepublic.force.sense.SenseTargets;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.IdentityData;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.quest.story.StoryTags;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.BladeColor;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberItems;
import com.oldrepublic.saber.SaberToggle;
import com.oldrepublic.world.EchoShrines;
import com.oldrepublic.world.outpost.BountyTerminalBlockEntity;
import com.oldrepublic.world.outpost.GarrisonRules;
import com.oldrepublic.world.outpost.OutpostPlacement;
import com.oldrepublic.world.outpost.Outposts;
import com.oldrepublic.world.outpost.TerminalBinding;
import com.oldrepublic.world.puzzle.CollapsedRubbleBlock;
import com.oldrepublic.world.puzzle.CounterweightStoneBlock;
import com.oldrepublic.world.puzzle.EchoUrnBlock;
import com.oldrepublic.world.puzzle.HolocronPedestalBlock;
import com.oldrepublic.world.puzzle.HolocronPedestalBlockEntity;
import com.oldrepublic.world.puzzle.PuzzleBlocks;
import com.oldrepublic.world.puzzle.PuzzleRules;
import com.oldrepublic.world.puzzle.PuzzleSealBlock;
import com.oldrepublic.world.puzzle.PuzzleStates;
import com.oldrepublic.world.puzzle.PuzzleStates.PedestalKind;
import com.oldrepublic.world.puzzle.PuzzleStates.Skin;
import com.oldrepublic.world.puzzle.PuzzleStates.Veil;
import com.oldrepublic.world.puzzle.PuzzleStates.VentPhase;
import com.oldrepublic.world.puzzle.TombVentBlock;
import com.oldrepublic.world.puzzle.UrnKeeperBlockEntity;
import com.oldrepublic.world.puzzle.VeiledStoneBlock;
import com.oldrepublic.world.site.EchoCairnBlockEntity;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteBalance;
import com.oldrepublic.world.site.SiteBlocks;
import com.oldrepublic.world.site.SiteChecks;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SiteSpawnerBlockEntity;
import com.oldrepublic.world.site.SiteStates;
import com.oldrepublic.world.site.SpawnerGroups;
import com.oldrepublic.world.site.StructureLocator;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3 world tests (spec 17.1, {@code WorldM3GameTests}): the puzzle kit (veiled stone,
 * counterweights and step stones, the urn keeper and its urns, the pull anchor, rubble, seals, vents
 * and pedestals), block entity NBT, and (part B) the site templates, outposts, terminals, spawners,
 * cairns and shrines.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks} (at most 400); fixtures are the lead's
 * small {@code oldrepublic_test} ones; long timers run through the pure rules and the blocks'
 * idle-step seams with an explicit "player near" flag, so the listed players of other tests running
 * beside these never change a result; randomness is seeded; no test writes {@code WorldSites}. The
 * slow-kind duration modifier is registered once and answers only for entities tagged
 * {@link #TAG_SLOW_LOG}.
 */
public class WorldM3GameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	private static final String URN_FIXTURE = "oldrepublic_test:urn_fixture";
	private static final String SEAL_FIXTURE = "oldrepublic_test:seal_fixture";
	private static final String PEDESTAL_FIXTURE = "oldrepublic_test:pedestal_fixture";
	private static final Identifier URN_FIXTURE_ID = Identifier.fromNamespaceAndPath("oldrepublic_test", "urn_fixture");

	private static final String ABILITY_LIFT = "force_lift";
	/** Entities with this tag log the control kind that reached the duration modifiers and get half of a slow. */
	private static final String TAG_SLOW_LOG = "oldrepublic_test.world_slow_log";
	private static final Map<UUID, String> SLOW_LOG = new ConcurrentHashMap<>();
	private static boolean slowModifierRegistered;

	private static final List<SiteAnchors.Anchor> URN_ANCHORS = List.of(SiteAnchors.Anchor.URN_1, SiteAnchors.Anchor.URN_2,
			SiteAnchors.Anchor.URN_3, SiteAnchors.Anchor.URN_4);

	private static Component msg(String text) {
		return Component.literal(text);
	}

	// ---------------------------------------------------------------- shared helpers

	/** A Force act of {@code act} by {@code caster} on the block at the absolute {@code pos}, as a power dispatches it. */
	private static boolean act(ServerPlayer caster, BlockPos pos, ForceAct act) {
		ServerLevel level = caster.level();
		String ability = switch (act) {
			case PUSH -> ForcePush.ABILITY_ID;
			case PULL -> ForcePull.ID;
			case LIFT -> ABILITY_LIFT;
		};
		return ForceBlockInteractions.dispatch(new ForceBlockContext(caster, level, pos, level.getBlockState(pos), act, new Vec3(0, 0, 1), 1f, ability));
	}

	private static Veil veil(GameTestHelper helper, BlockPos relative) {
		return helper.getBlockState(relative).getValue(PuzzleStates.VEIL);
	}

	/** Whether the block at {@code relative} is invisible and walk-through (the OPEN / cleared look). */
	private static boolean invisible(GameTestHelper helper, BlockPos relative) {
		BlockState state = helper.getBlockState(relative);
		BlockPos pos = helper.absolutePos(relative);
		return state.getRenderShape() == RenderShape.INVISIBLE && state.getCollisionShape(helper.getLevel(), pos).isEmpty()
				&& state.getShape(helper.getLevel(), pos).isEmpty();
	}

	/**
	 * The test-local position of an absolute one in these unrotated tests. {@code
	 * GameTestHelper#relativePos} turns an unrotated test's positions a half turn, so it lands outside
	 * the test.
	 */
	private static BlockPos local(GameTestHelper helper, BlockPos absolute) {
		return absolute.subtract(helper.absolutePos(BlockPos.ZERO));
	}

	private static boolean extended(GameTestHelper helper, BlockPos relative) {
		return helper.getBlockState(relative).getValue(PuzzleStates.EXTENDED);
	}

	/** A pig that stands still at {@code relative}: a living target that is not a player. */
	private static Mob pig(GameTestHelper helper, BlockPos relative, String... tags) {
		Mob pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, relative);

		for (String tag : tags) {
			pig.addTag(tag);
		}

		return pig;
	}

	/** A dormant Phase 0 sentinel stub is an armour stand that never wakes; the real one (W5) can. */
	private static boolean canWake(ServerLevel level, LivingEntity sentinel) {
		return !(sentinel instanceof ArmorStand) && level.getDifficulty() != Difficulty.PEACEFUL;
	}

	/**
	 * Builds the urn puzzle in the urn fixture: the keeper (facing north, the template frame) on its
	 * anchor with the four offsets of the sidecar, and the four urns on theirs. Returns the keeper.
	 */
	private static UrnKeeperBlockEntity buildUrns(GameTestHelper helper) {
		BlockPos keeperLocal = SiteAnchors.of(URN_FIXTURE_ID, SiteAnchors.Anchor.URN_KEEPER).getFirst();
		helper.setBlock(keeperLocal, PuzzleBlocks.URN_KEEPER.defaultBlockState().setValue(PuzzleStates.FACING, Direction.NORTH));
		List<BlockPos> offsets = new ArrayList<>();

		for (int i = 0; i < URN_ANCHORS.size(); i++) {
			BlockPos urnLocal = SiteAnchors.of(URN_FIXTURE_ID, URN_ANCHORS.get(i)).getFirst();
			helper.setBlock(urnLocal, PuzzleBlocks.ECHO_URN.defaultBlockState().setValue(PuzzleStates.INDEX, i));
			offsets.add(urnLocal.subtract(keeperLocal));
		}

		UrnKeeperBlockEntity keeper = helper.getBlockEntity(keeperLocal, UrnKeeperBlockEntity.class);
		keeper.setUrnOffsets(offsets);
		return keeper;
	}

	// ---------------------------------------------------------------- veiled stone

	/** A 32-block veiled mass reveals and opens at most {@code sites.veilGroupMax} (27) blocks; the rest stays hidden. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void veilFloodFillStopsAt27(GameTestHelper helper) {
		SiteBalance b = SiteBalance.get();
		helper.assertTrue(b.veilGroupMax == 27, msg("sites.veilGroupMax defaults to 27, got " + b.veilGroupMax));
		List<BlockPos> mass = new ArrayList<>();

		for (int x = 8; x < 12; x++) {
			for (int y = 1; y < 3; y++) {
				for (int z = 2; z < 6; z++) {
					BlockPos pos = new BlockPos(x, y, z);
					helper.setBlock(pos, PuzzleBlocks.VEILED_STONE.defaultBlockState().setValue(PuzzleStates.SKIN, Skin.RUIN));
					mass.add(pos);
				}
			}
		}

		ServerPlayer caster = M3TestPlayers.listed(helper);

		try {
			BlockPos start = new BlockPos(8, 1, 2);
			int revealed = VeiledStoneBlock.reveal(helper.getLevel(), helper.absolutePos(start), b.veilRevealTicks);
			helper.assertTrue(revealed == 27, msg("reveal stops at 27 of the 32 connected stones, got " + revealed));
			helper.assertTrue(mass.stream().filter(p -> veil(helper, p) == Veil.REVEALED).count() == 27, msg("exactly 27 stones show their seams"));

			helper.assertTrue(act(caster, helper.absolutePos(start), ForceAct.PUSH), msg("a Push on a revealed stone opens its group"));
			long open = mass.stream().filter(p -> veil(helper, p) == Veil.OPEN).count();
			long veiled = mass.stream().filter(p -> veil(helper, p) == Veil.VEILED).count();
			helper.assertTrue(open == 27 && veiled == 5, msg("27 open and 5 still veiled, got " + open + " open and " + veiled + " veiled"));
			helper.assertTrue(mass.stream().filter(p -> veil(helper, p) == Veil.OPEN).allMatch(p -> invisible(helper, p)),
					msg("every open stone has no shape, no collision and no model"));

			BlockPos leftover = mass.stream().filter(p -> veil(helper, p) == Veil.VEILED).findFirst().orElseThrow();
			int rest = VeiledStoneBlock.reveal(helper.getLevel(), helper.absolutePos(leftover), b.veilRevealTicks);
			helper.assertTrue(rest == 5, msg("the 5 left over form their own group (open stones are not members), got " + rest));
		} finally {
			M3TestPlayers.remove(caster);
		}

		helper.succeed();
	}

	/**
	 * A veiled wall ignores a Push until Force Sense (the real provider path, through
	 * {@code SenseTargets.collect}) reveals it with a {@code BLOCK} point; then a Push opens it all.
	 * A revealed stone also opens to a hit with a lit saber, never with an unlit one.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void veilRevealThenOpenByPush(GameTestHelper helper) {
		List<BlockPos> wall = new ArrayList<>();

		for (int x = 12; x < 15; x++) {
			for (int y = 1; y < 4; y++) {
				BlockPos pos = new BlockPos(x, y, 4);
				helper.setBlock(pos, PuzzleBlocks.VEILED_STONE.defaultBlockState().setValue(PuzzleStates.SKIN, Skin.TOMB));
				wall.add(pos);
			}
		}

		ServerPlayer caster = M3TestPlayers.listed(helper, new BlockPos(13, 1, 1));
		BlockPos middle = new BlockPos(13, 2, 4);

		try {
			helper.assertTrue(!act(caster, helper.absolutePos(middle), ForceAct.PUSH), msg("a Push on a veiled (not revealed) stone does nothing"));
			helper.assertTrue(wall.stream().allMatch(p -> veil(helper, p) == Veil.VEILED), msg("the wall stays veiled"));

			List<SenseTargets.Point> points = SenseTargets.collect(caster, 16, 24, 32);
			Vec3 centre = Vec3.atCenterOf(helper.absolutePos(middle));
			helper.assertTrue(points.stream().anyMatch(p -> p.kind() == SenseTargets.Kind.BLOCK && p.pos().distanceTo(centre) < 0.01),
					msg("Sense shows one BLOCK point at the middle of the wall"));
			helper.assertTrue(wall.stream().allMatch(p -> veil(helper, p) == Veil.REVEALED), msg("Sense reveals the whole wall"));

			helper.assertTrue(act(caster, helper.absolutePos(middle), ForceAct.PULL) == false, msg("a Pull does not open a veiled stone"));
			helper.assertTrue(act(caster, helper.absolutePos(middle), ForceAct.PUSH), msg("a Push on the revealed wall opens it"));
			helper.assertTrue(wall.stream().allMatch(p -> veil(helper, p) == Veil.OPEN && invisible(helper, p)), msg("the whole wall is open and walk-through"));

			// The saber path on a second, small revealed group.
			BlockPos first = new BlockPos(17, 1, 4);
			BlockPos second = new BlockPos(17, 2, 4);
			helper.setBlock(first, PuzzleBlocks.VEILED_STONE.defaultBlockState().setValue(PuzzleStates.SKIN, Skin.WRECK));
			helper.setBlock(second, PuzzleBlocks.VEILED_STONE.defaultBlockState().setValue(PuzzleStates.SKIN, Skin.WRECK));
			ServerLevel level = helper.getLevel();
			helper.assertTrue(VeiledStoneBlock.reveal(level, helper.absolutePos(first), 160) == 2, msg("the small group reveals as 2 stones"));

			ItemStack saber = SaberItems.buildSaber(new CrystalAttunement(caster.getUUID(), "Tester", BladeColor.BLUE.rgb()), BladeColor.BLUE,
					Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, SaberToggle.balance());
			caster.setItemInHand(InteractionHand.MAIN_HAND, saber);
			helper.getBlockState(first).attack(level, helper.absolutePos(first), caster);
			helper.assertTrue(veil(helper, first) == Veil.REVEALED, msg("an unlit saber does not open it"));

			SaberToggle.setIgnited(saber, true, level.getGameTime());
			helper.getBlockState(first).attack(level, helper.absolutePos(first), caster);
			helper.assertTrue(veil(helper, first) == Veil.OPEN && veil(helper, second) == Veil.OPEN, msg("a lit saber hit opens the revealed group"));
		} finally {
			M3TestPlayers.remove(caster);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- resets

	/**
	 * The idle rule: a player near holds the age at 0; six idle steps of 200 ticks reach the 1,200
	 * ticks of {@code sites.puzzleResetTicks}; a longer configured reset stretches the step. In the
	 * world, through the blocks' idle-step seams: an open veil and cleared rubble never close on an
	 * occupant and close once it has gone.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void puzzleResetAfterIdleAge(GameTestHelper helper) {
		SiteBalance b = new SiteBalance();
		helper.assertTrue(PuzzleRules.resetInterval(b) == 200, msg("the idle step is sites.puzzleTickInterval, 200"));
		helper.assertTrue(PuzzleRules.resetStep(5, true, b).equals(new PuzzleRules.ResetStep(0, false)), msg("a player near sets the age back to 0"));

		int age = 0;

		for (int step = 1; step <= 6; step++) {
			PuzzleRules.ResetStep next = PuzzleRules.resetStep(age, false, b);
			helper.assertTrue(next.age() == step && next.reset() == (step == 6), msg("idle step " + step + " gives age " + step + (step == 6 ? " and resets" : " without a reset") + "; got " + next));
			age = next.age();
		}

		helper.assertTrue(PuzzleRules.resetStep(PuzzleStates.MAX_AGE, false, b).age() == PuzzleStates.MAX_AGE, msg("the age is capped at " + PuzzleStates.MAX_AGE));

		SiteBalance slow = new SiteBalance();
		slow.puzzleResetTicks = 3000;
		helper.assertTrue(PuzzleRules.resetInterval(slow) == 500, msg("a 3,000-tick reset stretches the step to 500 so 6 steps still reach it"));

		// In the world: an open two-stone veil with a pig standing in it.
		ServerLevel level = helper.getLevel();
		BlockPos low = new BlockPos(2, 1, 9);
		BlockPos high = new BlockPos(2, 2, 9);
		helper.setBlock(low, PuzzleBlocks.VEILED_STONE.defaultBlockState());
		helper.setBlock(high, PuzzleBlocks.VEILED_STONE.defaultBlockState());
		VeiledStoneBlock.reveal(level, helper.absolutePos(low), b.veilRevealTicks);
		helper.assertTrue(VeiledStoneBlock.open(level, helper.absolutePos(low), null) == 2, msg("the two-stone veil opens"));
		Mob occupant = pig(helper, low);

		for (int step = 1; step <= 6; step++) {
			helper.assertTrue(!VeiledStoneBlock.idleStep(level, helper.absolutePos(low), false, b), msg("idle step " + step + " never closes the veil on the pig"));
		}

		helper.assertTrue(helper.getBlockState(low).getValue(PuzzleStates.AGE) == PuzzleStates.MAX_AGE && veil(helper, high) == Veil.OPEN,
				msg("the veil is due to reset but stays open around its occupant"));
		helper.assertTrue(!VeiledStoneBlock.idleStep(level, helper.absolutePos(low), true, b) && helper.getBlockState(low).getValue(PuzzleStates.AGE) == 0,
				msg("a player near holds it open and sets its age back"));
		occupant.discard();

		for (int step = 1; step <= 5; step++) {
			VeiledStoneBlock.idleStep(level, helper.absolutePos(low), false, b);
		}

		helper.assertTrue(VeiledStoneBlock.idleStep(level, helper.absolutePos(low), false, b), msg("the sixth idle step closes the empty veil"));
		helper.assertTrue(veil(helper, low) == Veil.VEILED && veil(helper, high) == Veil.VEILED, msg("the whole group is veiled again"));

		// Cleared rubble re-forms the same way.
		BlockPos rubble = new BlockPos(4, 1, 9);
		helper.setBlock(rubble, PuzzleBlocks.COLLAPSED_RUBBLE.defaultBlockState().setValue(PuzzleStates.CLEARED, true));
		Mob climber = pig(helper, rubble);

		for (int step = 1; step <= 6; step++) {
			CollapsedRubbleBlock.idleStep(level, helper.absolutePos(rubble), false, b);
		}

		helper.assertTrue(helper.getBlockState(rubble).getValue(PuzzleStates.CLEARED), msg("rubble never re-forms on an occupant"));
		climber.discard();
		helper.assertTrue(CollapsedRubbleBlock.idleStep(level, helper.absolutePos(rubble), false, b) && !helper.getBlockState(rubble).getValue(PuzzleStates.CLEARED),
				msg("rubble re-forms once its doorway is empty"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- counterweights and step stones

	/**
	 * A sunk counterweight powers only the cell behind its front (step stones as probes on all five
	 * other sides stay retracted), also through a solid wall block there, under two facings; it rises
	 * after its warning and the ledges retract.
	 */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void counterweightPowersStepStonesBackFaceOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// Facing north: the front looks to -z, the back cell is +z.
		BlockPos weight = new BlockPos(3, 1, 13);
		BlockPos back = new BlockPos(3, 1, 14);
		List<BlockPos> others = List.of(new BlockPos(3, 1, 12), new BlockPos(2, 1, 13), new BlockPos(4, 1, 13), new BlockPos(3, 2, 13));
		helper.setBlock(weight, PuzzleBlocks.COUNTERWEIGHT_STONE.defaultBlockState().setValue(PuzzleStates.FACING, Direction.NORTH));
		helper.setBlock(back, PuzzleBlocks.STEP_STONE.defaultBlockState().setValue(PuzzleStates.FACING, Direction.SOUTH));
		others.forEach(pos -> helper.setBlock(pos, PuzzleBlocks.STEP_STONE.defaultBlockState()));

		// Facing north again, with a stone wall block behind it and the ledge behind the wall: the hidden wiring.
		BlockPos walled = new BlockPos(20, 1, 9);
		BlockPos wallBlock = new BlockPos(20, 1, 10);
		BlockPos behindWall = new BlockPos(20, 1, 11);
		helper.setBlock(walled, PuzzleBlocks.COUNTERWEIGHT_STONE.defaultBlockState().setValue(PuzzleStates.FACING, Direction.NORTH));
		helper.setBlock(wallBlock, Blocks.STONE);
		helper.setBlock(behindWall, PuzzleBlocks.STEP_STONE.defaultBlockState().setValue(PuzzleStates.FACING, Direction.SOUTH));

		// Facing east: the back cell is -x.
		BlockPos east = new BlockPos(14, 1, 2);
		BlockPos eastBack = new BlockPos(13, 1, 2);
		BlockPos eastFront = new BlockPos(15, 1, 2);
		helper.setBlock(east, PuzzleBlocks.COUNTERWEIGHT_STONE.defaultBlockState().setValue(PuzzleStates.FACING, Direction.EAST).setValue(PuzzleStates.SKIN, Skin.WRECK));
		helper.setBlock(eastBack, PuzzleBlocks.STEP_STONE.defaultBlockState());
		helper.setBlock(eastFront, PuzzleBlocks.STEP_STONE.defaultBlockState());

		ServerPlayer caster = M3TestPlayers.listed(helper, new BlockPos(3, 1, 9));

		try {
			helper.assertTrue(!extended(helper, back) && others.stream().noneMatch(p -> extended(helper, p)), msg("every ledge starts retracted"));
			helper.assertTrue(act(caster, helper.absolutePos(weight), ForceAct.PUSH), msg("a Push sinks the counterweight"));
			helper.assertTrue(!act(caster, helper.absolutePos(weight), ForceAct.PUSH), msg("a second Push on a sunk counterweight is ignored"));
			helper.assertTrue(CounterweightStoneBlock.sink(level, helper.absolutePos(walled), caster), msg("sink() is the Push path"));
			helper.assertTrue(CounterweightStoneBlock.sink(level, helper.absolutePos(east), null), msg("sink() works without a player"));
		} finally {
			M3TestPlayers.remove(caster);
		}

		helper.runAfterDelay(1, () -> {
			helper.assertTrue(helper.getBlockState(weight).getValue(PuzzleStates.SUNK), msg("the counterweight is down"));
			helper.assertRedstoneSignal(weight, Direction.NORTH, s -> s == 15, () -> msg("the back neighbour (asking toward the front, north) reads 15"));

			for (Direction side : List.of(Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN)) {
				helper.assertRedstoneSignal(weight, side, s -> s == 0, () -> msg("asked from the " + side.getOpposite() + " side, the counterweight gives 0"));
			}

			helper.assertTrue(extended(helper, back), msg("the ledge in the back cell extends"));
			helper.assertTrue(others.stream().noneMatch(p -> extended(helper, p)), msg("ledges at the front, the sides and on top stay retracted"));
			helper.assertTrue(extended(helper, behindWall), msg("strong power passes through the wall block behind it to the ledge beyond"));
			helper.assertTrue(extended(helper, eastBack) && !extended(helper, eastFront), msg("facing east, only the west (back) cell is powered"));

			// The timeline: the warning step keeps it down, the next step raises it.
			// API-CHECK: GameTestHelper#tickBlock(BlockPos) runs the block's scheduled tick (BlockState#tick); m3_tests.txt:140 shows the signature only
			helper.tickBlock(weight);
			helper.assertTrue(helper.getBlockState(weight).getValue(PuzzleStates.SUNK) && helper.getBlockState(weight).getValue(PuzzleStates.AGE) == 1,
					msg("the warning step: still down, warning played"));
			helper.tickBlock(weight);
			helper.assertTrue(!helper.getBlockState(weight).getValue(PuzzleStates.SUNK), msg("the next step raises it"));
			helper.runAfterDelay(1, () -> {
				helper.assertTrue(!extended(helper, back), msg("the ledge retracts when the counterweight rises"));
				helper.assertRedstoneSignal(weight, Direction.NORTH, s -> s == 0, () -> msg("a raised counterweight gives no power"));
				helper.succeed();
			});
		});
	}

	/** A step stone has collision only while powered: a top-slab ledge; unpowered, a thin plate against its wall, walk-through. */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void stepStoneCollisionOnlyWhenPowered(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos wall = new BlockPos(9, 2, 7);
		BlockPos ledge = new BlockPos(9, 2, 8);
		BlockPos power = new BlockPos(9, 3, 8);
		helper.setBlock(wall, Blocks.STONE_BRICKS);
		// Facing south: the ledge sticks out to +z, its wall is to the north.
		helper.setBlock(ledge, PuzzleBlocks.STEP_STONE.defaultBlockState().setValue(PuzzleStates.FACING, Direction.SOUTH));
		BlockPos abs = helper.absolutePos(ledge);

		BlockState retracted = helper.getBlockState(ledge);
		helper.assertTrue(!retracted.getValue(PuzzleStates.EXTENDED) && retracted.getCollisionShape(level, abs).isEmpty(), msg("unpowered: no collision"));
		AABB plate = retracted.getShape(level, abs).bounds();
		helper.assertTrue(plate.maxZ <= 2.0 / 16 + 1.0e-6 && plate.minY >= 0.5 - 1.0e-6, msg("unpowered: a thin plate against the north wall, got " + plate));

		helper.setBlock(power, Blocks.REDSTONE_BLOCK);
		BlockState out = helper.getBlockState(ledge);
		AABB slab = out.getCollisionShape(level, abs).bounds();
		helper.assertTrue(out.getValue(PuzzleStates.EXTENDED), msg("powered: the ledge extends"));
		helper.assertTrue(Math.abs(slab.minY - 0.5) < 1.0e-6 && Math.abs(slab.maxY - 1.0) < 1.0e-6 && slab.minX == 0 && slab.maxX == 1 && slab.minZ == 0 && slab.maxZ == 1,
				msg("powered: a full top-slab collision box, got " + slab));

		helper.setBlock(power, Blocks.AIR);
		helper.assertTrue(!extended(helper, ledge) && helper.getBlockState(ledge).getCollisionShape(level, abs).isEmpty(), msg("power gone: the ledge retracts again"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- urns

	/** The keeper's order is a permutation seeded by its position (stable, and different keepers differ); Sense playback runs once per retry window. */
	@GameTest(structure = URN_FIXTURE, maxTicks = 120)
	public void urnOrderSeededByPosition(GameTestHelper helper) {
		UrnKeeperBlockEntity keeper = buildUrns(helper);
		BlockPos at = keeper.getBlockPos();
		int[] order = keeper.order();
		int[] sorted = order.clone();
		Arrays.sort(sorted);
		helper.assertTrue(Arrays.equals(sorted, new int[] {0, 1, 2, 3}), msg("the order is a permutation of the four urns, got " + Arrays.toString(order)));
		helper.assertTrue(Arrays.equals(order, PuzzleRules.urnOrder(at)) && Arrays.equals(order, PuzzleRules.urnOrder(at.immutable())),
				msg("the order depends only on the keeper's position"));

		Set<String> seen = new HashSet<>();

		for (int i = 0; i < 16; i++) {
			seen.add(Arrays.toString(PuzzleRules.urnOrder(at.offset(i * 37, 0, i * 11))));
		}

		helper.assertTrue(seen.size() > 1, msg("keepers at different positions do not all share one order"));

		SiteBalance b = SiteBalance.get();
		ServerLevel level = helper.getLevel();
		helper.assertTrue(keeper.startPlayback(level, b) && keeper.playing(), msg("Sense starts the playback"));
		helper.assertTrue(!keeper.startPlayback(level, b), msg("a second Sense within sites.urnRetryTicks does not restart it"));
		helper.runAfterDelay((long) b.urnStepTicks * UrnKeeperBlockEntity.URNS + 2, () -> {
			helper.assertTrue(!keeper.playing(), msg("the playback ends after four echoes, " + b.urnStepTicks + " ticks apart"));
			helper.succeed();
		});
	}

	/** Each urn finds its keeper through the {@code urn_keeper} POI and the keeper's offsets, which turn with the keeper's facing; a stray urn has none. */
	@GameTest(structure = URN_FIXTURE, maxTicks = 20)
	public void urnKeeperFoundThroughPoi(GameTestHelper helper) {
		UrnKeeperBlockEntity keeper = buildUrns(helper);
		ServerLevel level = helper.getLevel();

		for (int i = 0; i < URN_ANCHORS.size(); i++) {
			BlockPos urn = helper.absolutePos(SiteAnchors.of(URN_FIXTURE_ID, URN_ANCHORS.get(i)).getFirst());
			helper.assertTrue(EchoUrnBlock.findKeeper(level, urn) == keeper, msg(URN_ANCHORS.get(i) + " finds its keeper through the POI"));
			helper.assertTrue(keeper.urnPositions().get(i).equals(urn), msg(URN_ANCHORS.get(i) + " is urn " + i + " in the north frame"));
		}

		BlockPos stray = new BlockPos(5, 1, 2);
		helper.setBlock(stray, PuzzleBlocks.ECHO_URN.defaultBlockState());
		helper.assertTrue(EchoUrnBlock.findKeeper(level, helper.absolutePos(stray)) == null, msg("an urn the keeper does not list has no keeper"));
		ServerPlayer caster = M3TestPlayers.listed(helper, new BlockPos(5, 1, 7));

		try {
			helper.assertTrue(!act(caster, helper.absolutePos(stray), ForceAct.PUSH), msg("a Push on a stray urn does nothing"));
		} finally {
			M3TestPlayers.remove(caster);
		}

		// Turned east (clockwise 90), the template frame turns with it: URN_1's offset now lands on URN_2's cell.
		BlockPos keeperLocal = local(helper, keeper.getBlockPos());
		helper.setBlock(keeperLocal, helper.getBlockState(keeperLocal).setValue(PuzzleStates.FACING, Direction.EAST));
		UrnKeeperBlockEntity turned = helper.getBlockEntity(keeperLocal, UrnKeeperBlockEntity.class);
		BlockPos urn1Offset = turned.urnOffsets().getFirst();
		BlockPos expected = keeper.getBlockPos().offset(StructureTemplate.transform(urn1Offset, Mirror.NONE, Rotation.CLOCKWISE_90, BlockPos.ZERO));
		helper.assertTrue(turned.urnPositions().getFirst().equals(expected), msg("facing east, URN_1 is its offset turned clockwise 90"));
		helper.assertTrue(expected.equals(helper.absolutePos(SiteAnchors.of(URN_FIXTURE_ID, SiteAnchors.Anchor.URN_2).getFirst())),
				msg("in the square fixture, URN_1 turned 90 is URN_2's cell"));
		helper.assertTrue(EchoUrnBlock.findKeeper(level, expected) == turned, msg("the turned keeper is still found through its POI"));
		helper.succeed();
	}

	/**
	 * A wrong push starts the sequence over, drops a dust curtain that hurts within
	 * {@code sites.urnWrongRadius} of the wrong urn (and nothing farther), and wakes the dormant
	 * sentinels near the keeper except one that sleeps for the pusher's side: with a Light pusher the
	 * Tomb sentinel wakes and the Watcher keeps sleeping. Against the Phase 0 sentinel stub nothing
	 * can wake, and the count is 0.
	 */
	@GameTest(structure = URN_FIXTURE, maxTicks = 40)
	public void wrongUrnDustAndWakeSkipsOwnSide(GameTestHelper helper) {
		UrnKeeperBlockEntity keeper = buildUrns(helper);
		ServerLevel level = helper.getLevel();
		SiteBalance b = SiteBalance.get();
		int[] order = keeper.order();
		List<BlockPos> urns = keeper.urnPositions();
		BlockPos firstUrn = urns.get(order[0]);
		BlockPos wrongUrn = urns.get(order[2]);
		BlockPos wrongLocal = local(helper, wrongUrn);
		// One pig beside the wrong urn (toward the middle), one in the far corner beside the urn across from it.
		BlockPos nearLocal = wrongLocal.offset(Integer.signum(5 - wrongLocal.getX()), 0, 0);
		BlockPos farLocal = new BlockPos(10 - nearLocal.getX(), 1, 10 - nearLocal.getZ());
		Mob near = pig(helper, nearLocal);
		Mob far = pig(helper, farLocal);

		LivingEntity watcher = Sentinels.spawn(level, helper.absolutePos(SiteAnchors.of(URN_FIXTURE_ID, SiteAnchors.Anchor.SENTINEL_1).getFirst()),
				SentinelVariant.WATCHER, false, null);
		LivingEntity tomb = Sentinels.spawn(level, helper.absolutePos(new BlockPos(5, 1, 1)), SentinelVariant.TOMB, false, null);
		boolean wakeable = canWake(level, watcher);
		ServerPlayer pusher = M3TestPlayers.listed(helper, new BlockPos(5, 1, 7));

		try {
			IdentityData light = Identity.get(pusher);
			light.alignment = 40;
			Identity.commit(pusher, light);

			helper.assertTrue(act(pusher, firstUrn, ForceAct.PUSH) && keeper.progress() == 1, msg("the first urn of the order counts"));
			UrnKeeperBlockEntity.PushResult result = keeper.push(level, wrongUrn, pusher);
			helper.assertTrue(result.outcome() == UrnKeeperBlockEntity.Outcome.WRONG && keeper.progress() == 0, msg("a wrong urn starts the sequence over"));
			helper.assertTrue(!helper.getBlockState(local(helper, keeper.getBlockPos())).getValue(PuzzleStates.SOLVED), msg("the keeper is not solved"));

			float hurt = near.getMaxHealth() - near.getHealth();
			helper.assertTrue(Math.abs(hurt - b.urnWrongDamage) < 1.0e-4, msg("the dust hurts within " + b.urnWrongRadius + " of the wrong urn by " + b.urnWrongDamage + ", got " + hurt));
			helper.assertTrue(far.getHealth() == far.getMaxHealth(), msg("the dust does not reach the far corner"));

			// Radius 1 around each sentinel: 0 means it is already awake (or cannot wake), 1 that it was still asleep.
			int tombStillAsleep = Sentinels.wakeNear(level, tomb.blockPosition(), 1, null);
			int watcherStillAsleep = Sentinels.wakeNear(level, watcher.blockPosition(), 1, null);

			if (wakeable) {
				helper.assertTrue(result.woke() >= 1 && tombStillAsleep == 0, msg("the Tomb sentinel wakes for a Light pusher's wrong urn; woke " + result.woke()));
				helper.assertTrue(watcherStillAsleep == 1, msg("the Watcher sleeps on for a Light pusher"));
			} else {
				helper.assertTrue(result.woke() == 0 && tombStillAsleep == 0 && watcherStillAsleep == 0, msg("the Phase 0 stub (or Peaceful) wakes nothing"));
			}
		} finally {
			M3TestPlayers.remove(pusher);
			watcher.discard();
			tomb.discard();
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- anchor, rubble, seal

	/** Pull only: the anchor strains for {@code sites.anchorStrainTicks}, then releases with redstone 15 on every side, then rearms. */
	@GameTest(structure = ARENA, maxTicks = 40)
	public void pullAnchorStrainThenRelease(GameTestHelper helper) {
		BlockPos anchor = new BlockPos(2, 1, 20);
		BlockPos probe = new BlockPos(2, 1, 21);
		helper.setBlock(anchor, PuzzleBlocks.PULL_ANCHOR);
		helper.setBlock(probe, PuzzleBlocks.STEP_STONE);
		SiteBalance b = SiteBalance.get();
		ServerPlayer caster = M3TestPlayers.listed(helper, new BlockPos(2, 1, 16));

		try {
			BlockPos abs = helper.absolutePos(anchor);
			helper.assertTrue(!act(caster, abs, ForceAct.PUSH) && !act(caster, abs, ForceAct.LIFT), msg("a Push or a Lift does nothing to an anchor"));
			helper.assertTrue(act(caster, abs, ForceAct.PULL) && helper.getBlockState(anchor).getValue(PuzzleStates.STRAIN) == 1, msg("a Pull makes it strain"));
			helper.assertTrue(!act(caster, abs, ForceAct.PULL), msg("a Pull while it strains is ignored"));
			helper.assertRedstoneSignal(anchor, Direction.NORTH, s -> s == 0, () -> msg("no power while it strains"));
		} finally {
			M3TestPlayers.remove(caster);
		}

		helper.runAfterDelay(b.anchorStrainTicks + 2, () -> {
			helper.assertTrue(helper.getBlockState(anchor).getValue(PuzzleStates.STRAIN) == 2, msg("after " + b.anchorStrainTicks + " ticks it releases"));

			for (Direction side : Direction.values()) {
				helper.assertRedstoneSignal(anchor, side, s -> s == 15, () -> msg("released, it gives 15 toward " + side));
			}

			helper.assertTrue(extended(helper, probe), msg("the ledge beside it extends"));
			helper.tickBlock(anchor);
			helper.assertTrue(helper.getBlockState(anchor).getValue(PuzzleStates.STRAIN) == 0, msg("after its open time it rearms"));
			helper.runAfterDelay(1, () -> {
				helper.assertTrue(!extended(helper, probe), msg("rearmed, the power is gone"));
				helper.succeed();
			});
		});
	}

	/** Only Lift moves rubble: Push and Pull do nothing; {@code sites.rubbleTicks} Lift dispatches, one per tick, clear it; a gap starts over. */
	@GameTest(structure = ARENA, maxTicks = 60)
	public void rubbleClearsOnlyByLift(GameTestHelper helper) {
		SiteBalance rules = new SiteBalance();
		PuzzleRules.RubbleChannel channel = PuzzleRules.rubbleLift(null, 100);
		helper.assertTrue(channel.equals(new PuzzleRules.RubbleChannel(100, 100)), msg("the first dispatch starts a channel"));
		helper.assertTrue(PuzzleRules.rubbleLift(channel, 104).start() == 100, msg("a dispatch within 4 ticks continues it"));
		helper.assertTrue(PuzzleRules.rubbleLift(PuzzleRules.rubbleLift(channel, 104), 109).start() == 109, msg("a 5-tick gap starts a new channel"));
		helper.assertTrue(!PuzzleRules.rubbleClears(100, 117, 118, rules) && PuzzleRules.rubbleClears(100, 118, 119, rules),
				msg("the 20th tick of an unbroken channel clears it, the 19th does not"));

		BlockPos rubble = new BlockPos(21, 1, 2);
		helper.setBlock(rubble, PuzzleBlocks.COLLAPSED_RUBBLE);
		ServerPlayer caster = M3TestPlayers.listed(helper, new BlockPos(21, 1, 6));
		BlockPos abs = helper.absolutePos(rubble);
		int ticks = SiteBalance.get().rubbleTicks;

		helper.assertTrue(!act(caster, abs, ForceAct.PUSH) && !act(caster, abs, ForceAct.PULL), msg("Push and Pull do not move rubble"));
		helper.assertTrue(!helper.getBlockState(rubble).getValue(PuzzleStates.CLEARED), msg("the rubble still stands"));

		for (int tick = 1; tick <= ticks; tick++) {
			int dispatch = tick;
			helper.runAtTickTime(tick, () -> {
				helper.assertTrue(act(caster, abs, ForceAct.LIFT), msg("Lift dispatch " + dispatch + " is taken"));
				boolean cleared = helper.getBlockState(rubble).getValue(PuzzleStates.CLEARED);
				helper.assertTrue(cleared == (dispatch == ticks), msg("after Lift dispatch " + dispatch + " the rubble is " + (cleared ? "cleared" : "standing")));
			});
		}

		helper.runAtTickTime(ticks + 2, () -> {
			M3TestPlayers.remove(caster);
			helper.assertTrue(invisible(helper, rubble), msg("cleared rubble has no shape, no collision and no model"));
			helper.assertTrue(!act(caster, abs, ForceAct.LIFT), msg("a Lift on cleared rubble does nothing"));
			helper.succeed();
		});
	}

	/**
	 * A two-cell puzzle seal opens when a counterweight behind it sinks and stays open after the
	 * counterweight rises (latched). Through its idle-step seam it never closes while powered or
	 * with something standing in the doorway, and closes once both are gone.
	 */
	@GameTest(structure = SEAL_FIXTURE, maxTicks = 20)
	public void sealLatchesAndNeverClosesOnOccupant(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos low = new BlockPos(3, 1, 2);
		BlockPos high = new BlockPos(3, 2, 2);
		BlockPos weight = new BlockPos(3, 1, 1);
		BlockPos floor = new BlockPos(3, 0, 2);
		helper.setBlock(low, PuzzleBlocks.PUZZLE_SEAL.defaultBlockState().setValue(PuzzleStates.SKIN, Skin.TOMB));
		helper.setBlock(high, PuzzleBlocks.PUZZLE_SEAL.defaultBlockState().setValue(PuzzleStates.SKIN, Skin.TOMB));
		// In front of the doorway, facing north (away from it): its back cell is the seal.
		helper.setBlock(weight, PuzzleBlocks.COUNTERWEIGHT_STONE.defaultBlockState().setValue(PuzzleStates.FACING, Direction.NORTH));
		helper.assertTrue(!helper.getBlockState(low).getValue(PuzzleStates.OPEN), msg("the seal starts closed"));

		helper.assertTrue(CounterweightStoneBlock.sink(level, helper.absolutePos(weight), null), msg("the counterweight sinks"));
		helper.assertTrue(helper.getBlockState(low).getValue(PuzzleStates.OPEN) && helper.getBlockState(high).getValue(PuzzleStates.OPEN),
				msg("power on one cell opens the whole seal"));
		helper.assertTrue(invisible(helper, low) && invisible(helper, high), msg("an open seal has no shape, no collision and no model"));

		helper.tickBlock(weight);
		helper.tickBlock(weight);
		helper.assertTrue(!helper.getBlockState(weight).getValue(PuzzleStates.SUNK), msg("the counterweight has risen"));
		helper.assertTrue(helper.getBlockState(low).getValue(PuzzleStates.OPEN), msg("the seal stays open: it latches"));

		SiteBalance b = new SiteBalance();
		Mob occupant = pig(helper, low);

		for (int step = 1; step <= 7; step++) {
			helper.assertTrue(!PuzzleSealBlock.idleStep(level, helper.absolutePos(low), false, b), msg("idle step " + step + " never closes on the pig"));
		}

		helper.assertTrue(helper.getBlockState(low).getValue(PuzzleStates.OPEN), msg("still open around its occupant"));
		occupant.discard();
		helper.setBlock(floor, Blocks.REDSTONE_BLOCK);
		helper.assertTrue(!PuzzleSealBlock.idleStep(level, helper.absolutePos(low), false, b), msg("a powered seal does not close"));
		helper.setBlock(floor, Blocks.STONE_BRICKS);
		helper.assertTrue(PuzzleSealBlock.idleStep(level, helper.absolutePos(low), false, b), msg("unpowered and empty, the reset closes it"));
		helper.assertTrue(!helper.getBlockState(low).getValue(PuzzleStates.OPEN) && !helper.getBlockState(high).getValue(PuzzleStates.OPEN),
				msg("both cells close together"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- vents

	/**
	 * The vent rule (rest without a player, IDLE → TELL → PUFF → IDLE with a player near, a shown
	 * tell always puffs) and one in-world cycle: the puff hurts a pig in the column above by
	 * {@code sites.ventDamage} and slows it through {@code ControlEffects} as kind {@code slow} (the
	 * test modifier halves a logged slow), and leaves a pig two blocks away alone.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void ventTellThenPuffDamageAndSlowKind(GameTestHelper helper) {
		registerSlowModifier();
		SiteBalance b = new SiteBalance();
		RandomSource random = RandomSource.create(734211L);
		helper.assertTrue(PuzzleRules.ventStep(VentPhase.IDLE, false, random, b).equals(new PuzzleRules.VentStep(VentPhase.IDLE, 0, false)), msg("with nobody near a vent rests"));
		helper.assertTrue(PuzzleRules.ventStep(VentPhase.IDLE, true, random, b).equals(new PuzzleRules.VentStep(VentPhase.TELL, b.ventTellTicks, false)),
				msg("with a player near, IDLE turns to TELL for sites.ventTellTicks"));
		helper.assertTrue(PuzzleRules.ventStep(VentPhase.TELL, false, random, b).equals(new PuzzleRules.VentStep(VentPhase.PUFF, PuzzleRules.VENT_PUFF_TICKS, true)),
				msg("a tell always ends in a puff, even if the player stepped away"));
		helper.assertTrue(PuzzleRules.ventStep(VentPhase.PUFF, false, random, b).equals(new PuzzleRules.VentStep(VentPhase.IDLE, 0, false)), msg("after a puff with nobody near it rests"));

		for (int i = 0; i < 64; i++) {
			PuzzleRules.VentStep after = PuzzleRules.ventStep(VentPhase.PUFF, true, random, b);
			helper.assertTrue(after.next() == VentPhase.IDLE && after.delay() >= b.ventMinTicks && after.delay() <= b.ventMaxTicks,
					msg("with a player near the next tell comes " + b.ventMinTicks + " to " + b.ventMaxTicks + " ticks later, got " + after.delay()));
		}

		BlockPos vent = new BlockPos(20, 0, 13);
		helper.setBlock(vent, PuzzleBlocks.TOMB_VENT);
		Mob caught = pig(helper, vent.above(), TAG_SLOW_LOG);
		Mob aside = pig(helper, vent.above().east(2), TAG_SLOW_LOG);
		ServerPlayer player = M3TestPlayers.listed(helper, new BlockPos(20, 1, 9));
		SiteBalance live = SiteBalance.get();

		try {
			helper.tickBlock(vent);
			helper.assertTrue(helper.getBlockState(vent).getValue(PuzzleStates.PHASE) == VentPhase.TELL && caught.getHealth() == caught.getMaxHealth(),
					msg("with a player within " + live.ventActiveRange + " the vent tells first and hurts nobody"));

			helper.tickBlock(vent);
			helper.assertTrue(helper.getBlockState(vent).getValue(PuzzleStates.PHASE) == VentPhase.PUFF, msg("then it puffs"));
			float hurt = caught.getMaxHealth() - caught.getHealth();
			helper.assertTrue(Math.abs(hurt - live.ventDamage) < 1.0e-4, msg("the puff deals " + live.ventDamage + " tomb_miasma, got " + hurt));
			MobEffectInstance slow = caught.getEffect(MobEffects.SLOWNESS);
			int expected = Math.round(live.ventSlownessTicks * 0.5f);
			helper.assertTrue(slow != null && slow.getAmplifier() == 0 && slow.getDuration() == expected,
					msg("Slowness I for the modified " + expected + " ticks, got " + (slow == null ? "none" : slow.getDuration() + " at " + slow.getAmplifier())));
			helper.assertTrue(ControlEffects.SLOW.equals(SLOW_LOG.get(caught.getUUID())), msg("the slow reaches the duration modifiers as kind slow, got " + SLOW_LOG.get(caught.getUUID())));
			helper.assertTrue(aside.getHealth() == aside.getMaxHealth() && !aside.hasEffect(MobEffects.SLOWNESS), msg("a pig two blocks aside is untouched"));

			helper.tickBlock(vent);
			helper.assertTrue(helper.getBlockState(vent).getValue(PuzzleStates.PHASE) == VentPhase.IDLE, msg("then it rests again"));
			TombVentBlock.kick(helper.getLevel(), helper.absolutePos(vent));
		} finally {
			M3TestPlayers.remove(player);
			SLOW_LOG.remove(caught.getUUID());
			SLOW_LOG.remove(aside.getUUID());
		}

		helper.succeed();
	}

	private static void registerSlowModifier() {
		if (slowModifierRegistered) {
			return;
		}

		slowModifierRegistered = true;
		ControlEffects.registerDurationModifier((target, kind) -> {
			if (!target.entityTags().contains(TAG_SLOW_LOG)) {
				return 1f;
			}

			SLOW_LOG.put(target.getUUID(), kind);
			return ControlEffects.SLOW.equals(kind) ? 0.5f : 1f;
		});
	}

	// ---------------------------------------------------------------- breaking and pedestals

	/**
	 * Every puzzle block (the pedestal included) and part B's three site blocks are unbreakable in
	 * survival: a survival player with a netherite pickaxe makes no progress, and explosions cannot
	 * move them. The resource and decor blocks do break.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void puzzleBlocksUnbreakableInSurvival(GameTestHelper helper) {
		List<Block> unbreakable = List.of(PuzzleBlocks.VEILED_STONE, PuzzleBlocks.COUNTERWEIGHT_STONE, PuzzleBlocks.STEP_STONE, PuzzleBlocks.ECHO_URN,
				PuzzleBlocks.URN_KEEPER, PuzzleBlocks.PULL_ANCHOR, PuzzleBlocks.COLLAPSED_RUBBLE, PuzzleBlocks.PUZZLE_SEAL, PuzzleBlocks.HOLOCRON_PEDESTAL,
				PuzzleBlocks.TOMB_VENT, SiteBlocks.BOUNTY_TERMINAL, SiteBlocks.ECHO_CAIRN, SiteBlocks.SITE_SPAWNER);
		List<Block> breakable = List.of(PuzzleBlocks.LUMEN_MOSS, PuzzleBlocks.FUNERARY_URN, PuzzleBlocks.WRECK_SALVAGE, PuzzleBlocks.RUIN_STONE,
				PuzzleBlocks.RUIN_STONE_CARVED, PuzzleBlocks.RUIN_STONE_CRACKED, PuzzleBlocks.RUIN_STONE_MOSSY, PuzzleBlocks.TOMB_STONE,
				PuzzleBlocks.TOMB_STONE_CARVED, PuzzleBlocks.TOMB_STONE_CRACKED, PuzzleBlocks.WRECK_PLATING, PuzzleBlocks.WRECK_PLATING_SCORCHED,
				PuzzleBlocks.WRECK_CONDUIT, PuzzleBlocks.REPUBLIC_PANEL, PuzzleBlocks.IMPERIAL_PANEL);
		ServerLevel level = helper.getLevel();
		ServerPlayer miner = M3TestPlayers.listed(helper);

		try {
			miner.setGameMode(GameType.SURVIVAL);
			miner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
			BlockPos spot = helper.absolutePos(new BlockPos(6, 1, 2));
			// The checks read only the block's properties, so nothing is placed (no site block ticks or spawns).
			for (Block block : unbreakable) {
				BlockState state = block.defaultBlockState();
				helper.assertTrue(state.getDestroySpeed(level, spot) == -1f, msg(block + " has destroy time -1"));
				helper.assertTrue(state.getDestroyProgress(miner, level, spot) == 0f, msg(block + " makes no mining progress in survival with a netherite pickaxe"));
				helper.assertTrue(block.getExplosionResistance() >= 3_600_000f, msg(block + " resists explosions (3,600,000), got " + block.getExplosionResistance()));
			}

			for (Block block : breakable) {
				helper.assertTrue(block.defaultBlockState().getDestroyProgress(miner, level, spot) > 0f, msg(block + " can be mined in survival"));
			}
		} finally {
			M3TestPlayers.remove(miner);
		}

		helper.succeed();
	}

	/**
	 * A pedestal answers a use with the silent stone while not ready, latches READY on its first
	 * power and keeps it after the power is gone; an {@code always_ready} pedestal is ready without
	 * power. Neither the use nor the latch wakes the dormant sentinel in the room.
	 */
	@GameTest(structure = PEDESTAL_FIXTURE, maxTicks = 20)
	public void pedestalReadyLatches(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pedestal = new BlockPos(3, 1, 3);
		BlockPos floor = new BlockPos(3, 0, 3);
		BlockPos wayfarer = new BlockPos(5, 1, 3);
		helper.setBlock(pedestal, PuzzleBlocks.HOLOCRON_PEDESTAL.defaultBlockState().setValue(PuzzleStates.KIND, PedestalKind.SITH));
		helper.setBlock(wayfarer, PuzzleBlocks.HOLOCRON_PEDESTAL.defaultBlockState().setValue(PuzzleStates.KIND, PedestalKind.WAYFARER)
				.setValue(PuzzleStates.ALWAYS_READY, true));
		LivingEntity sentinel = Sentinels.spawn(level, helper.absolutePos(new BlockPos(0, 1, 0)), SentinelVariant.TOMB, false, null);
		boolean wakeable = canWake(level, sentinel);
		ServerPlayer visitor = M3TestPlayers.listed(helper, new BlockPos(6, 1, 6));

		try {
			helper.assertTrue(!HolocronPedestalBlock.isReady(helper.getBlockState(pedestal)), msg("a fresh pedestal is not ready"));
			helper.useBlock(pedestal, visitor);
			helper.assertTrue(!HolocronPedestalBlock.isReady(helper.getBlockState(pedestal)), msg("using a silent pedestal changes nothing"));

			helper.setBlock(floor, Blocks.REDSTONE_BLOCK);
			helper.assertTrue(helper.getBlockState(pedestal).getValue(PuzzleStates.READY), msg("the first power latches READY"));
			helper.setBlock(floor, Blocks.SMOOTH_STONE);
			helper.assertTrue(helper.getBlockState(pedestal).getValue(PuzzleStates.READY), msg("READY stays after the power is gone"));

			BlockState always = helper.getBlockState(wayfarer);
			helper.assertTrue(HolocronPedestalBlock.isReady(always) && !always.getValue(PuzzleStates.READY), msg("always_ready is ready without power or a latch"));
			helper.assertTrue(PedestalKind.of(PedestalKind.SITH.holocron()) == PedestalKind.SITH, msg("pedestal kinds map to holocron kinds and back"));

			HolocronPedestalBlockEntity entity = helper.getBlockEntity(pedestal, HolocronPedestalBlockEntity.class);
			helper.assertTrue(entity.fixedId() == null, msg("a worldgen pedestal has no fixed holocron"));

			int stillAsleep = Sentinels.wakeNear(level, sentinel.blockPosition(), 1, null);
			helper.assertTrue(stillAsleep == (wakeable ? 1 : 0), msg("the sentinel slept through the pedestal's use and latch"));
		} finally {
			M3TestPlayers.remove(visitor);
			sentinel.discard();
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- block entity NBT

	/**
	 * Block entities survive a save and load: the urn keeper (offsets, progress, playback) and the
	 * pedestal's {@code fixed_id} (part A), the terminal, the cairn and the spawner (part B's
	 * helpers). A keeper tag written the way the structure generator writes it ({@code urns} as 12
	 * ints, nothing else) loads its offsets.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void blockEntityNbtRoundTrips(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos keeperLocal = new BlockPos(3, 1, 3);
		helper.setBlock(keeperLocal, PuzzleBlocks.URN_KEEPER.defaultBlockState().setValue(PuzzleStates.FACING, Direction.WEST));
		UrnKeeperBlockEntity keeper = helper.getBlockEntity(keeperLocal, UrnKeeperBlockEntity.class);
		List<BlockPos> offsets = List.of(new BlockPos(-2, 0, -2), new BlockPos(2, 0, -2), new BlockPos(2, 0, 2), new BlockPos(-2, 0, 2));
		keeper.setUrnOffsets(offsets);

		for (BlockPos urn : keeper.urnPositions()) {
			helper.setBlock(local(helper, urn), PuzzleBlocks.ECHO_URN);
		}

		ServerPlayer pusher = M3TestPlayers.listed(helper, new BlockPos(3, 1, 7));

		try {
			BlockPos first = keeper.urnPositions().get(keeper.order()[0]);
			helper.assertTrue(keeper.push(level, first, pusher).outcome() == UrnKeeperBlockEntity.Outcome.CORRECT, msg("one correct push sets progress 1"));
		} finally {
			M3TestPlayers.remove(pusher);
		}

		helper.assertTrue(keeper.startPlayback(level, SiteBalance.get()), msg("a playback is running"));
		BlockEntity reloaded = BlockEntity.loadStatic(keeper.getBlockPos(), keeper.getBlockState(), keeper.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
		helper.assertTrue(reloaded instanceof UrnKeeperBlockEntity copy && copy.urnOffsets().equals(offsets) && copy.progress() == 1 && copy.playing()
				&& copy.urnPositions().equals(keeper.urnPositions()), msg("the urn keeper keeps its offsets, progress and playback"));

		// The generator's layout: the id and 12 ints in the template frame, URN_1 first.
		CompoundTag generated = new CompoundTag();
		generated.putString("id", "oldrepublic:urn_keeper");
		// API-CHECK: CompoundTag#putString / putIntArray (long-standing; not in the dumps)
		generated.putIntArray("urns", new int[] {-2, 0, -2, 2, 0, -2, 2, 0, 2, -2, 0, 2});
		BlockEntity fromGenerator = BlockEntity.loadStatic(keeper.getBlockPos(), keeper.getBlockState(), generated, level.registryAccess());
		helper.assertTrue(fromGenerator instanceof UrnKeeperBlockEntity loaded && loaded.urnOffsets().equals(offsets) && loaded.progress() == 0 && !loaded.playing(),
				msg("a generator-written keeper loads its four offsets with no progress and no playback"));

		BlockPos pedestalLocal = new BlockPos(9, 1, 3);
		helper.setBlock(pedestalLocal, PuzzleBlocks.HOLOCRON_PEDESTAL.defaultBlockState().setValue(PuzzleStates.KIND, PedestalKind.WAYFARER));
		HolocronPedestalBlockEntity pedestal = helper.getBlockEntity(pedestalLocal, HolocronPedestalBlockEntity.class);
		helper.assertTrue(roundTripFixedId(level, pedestal) == null, msg("no fixed_id saves and loads as none"));
		pedestal.setFixedId("wayfarers_compass");
		helper.assertTrue("wayfarers_compass".equals(roundTripFixedId(level, pedestal)), msg("fixed_id survives a save and load"));
		pedestal.setFixedId("");
		helper.assertTrue(roundTripFixedId(level, pedestal) == null, msg("an empty fixed_id is none"));

		roundTripTerminal(helper);
		roundTripCairn(helper);
		roundTripSpawner(helper);
		helper.succeed();
	}

	private static @Nullable String roundTripFixedId(ServerLevel level, HolocronPedestalBlockEntity pedestal) {
		BlockEntity copy = BlockEntity.loadStatic(pedestal.getBlockPos(), pedestal.getBlockState(), pedestal.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
		return copy instanceof HolocronPedestalBlockEntity loaded ? loaded.fixedId() : "(not a pedestal)";
	}

	// ================================================================ part B: sites, outposts, terminals, spawners, cairns, shrines

	private static final String TERMINAL_FIXTURE = "oldrepublic_test:terminal_fixture";
	private static final Identifier TERMINAL_FIXTURE_ID = Identifier.fromNamespaceAndPath("oldrepublic_test", "terminal_fixture");
	/** The terminal fixture's member spots, slot order (officer, then GARRISON_1 to GARRISON_4). */
	private static final List<SiteAnchors.Anchor> SLOT_ANCHORS = List.of(SiteAnchors.Anchor.OFFICER, SiteAnchors.Anchor.GARRISON_1,
			SiteAnchors.Anchor.GARRISON_2, SiteAnchors.Anchor.GARRISON_3, SiteAnchors.Anchor.GARRISON_4);

	/**
	 * One frozen W1 template (spec 15.1): its size and every anchor it must have. An anchor maps to the
	 * block its cell holds, to {@link Blocks#AIR} for a stand (two air cells), or to a spawner group id
	 * (a {@code site_spawner} with that {@code group}).
	 */
	private record FrozenTemplate(String path, Vec3i size, Map<SiteAnchors.Anchor, Object> anchors) {
	}

	private static FrozenTemplate frozen(String path, int x, int y, int z, Object... anchorsAndCells) {
		Map<SiteAnchors.Anchor, Object> anchors = new LinkedHashMap<>();

		for (int i = 0; i < anchorsAndCells.length; i += 2) {
			anchors.put((SiteAnchors.Anchor) anchorsAndCells[i], anchorsAndCells[i + 1]);
		}

		return new FrozenTemplate(path, new Vec3i(x, y, z), anchors);
	}

	/** The 21 W1 templates of spec 15.1 and the three Milestone 6 start variants, with their sizes and anchors. */
	private static List<FrozenTemplate> frozenTemplates() {
		Block air = Blocks.AIR;
		Block chest = Blocks.CHEST;
		Block pedestal = PuzzleBlocks.HOLOCRON_PEDESTAL;
		Block counterweight = PuzzleBlocks.COUNTERWEIGHT_STONE;
		Block veiled = PuzzleBlocks.VEILED_STONE;
		Block vent = PuzzleBlocks.TOMB_VENT;
		Block urn = PuzzleBlocks.ECHO_URN;
		Block terminal = SiteBlocks.BOUNTY_TERMINAL;
		List<FrozenTemplate> templates = new ArrayList<>();
		templates.add(frozen("jedi_ruin/courtyard", 51, 30, 51, SiteAnchors.Anchor.STAIR_MOUTH, veiled, SiteAnchors.Anchor.SURFACE_GUARD, air,
				SiteAnchors.Anchor.DIGGERS, SpawnerGroups.DIGGERS, SiteAnchors.Anchor.CHEST_1, chest));
		templates.add(frozen("jedi_ruin/courtyard_2", 51, 30, 51, SiteAnchors.Anchor.STAIR_MOUTH, veiled, SiteAnchors.Anchor.SURFACE_GUARD, air,
				SiteAnchors.Anchor.DIGGERS, SpawnerGroups.DIGGERS, SiteAnchors.Anchor.CHEST_1, chest));
		templates.add(frozen("jedi_ruin/stair", 11, 12, 14));
		templates.add(frozen("jedi_ruin/listening_hall", 25, 14, 25, SiteAnchors.Anchor.HEART, air, SiteAnchors.Anchor.PEDESTAL_JEDI, pedestal,
				SiteAnchors.Anchor.COUNTERWEIGHT_W, counterweight, SiteAnchors.Anchor.COUNTERWEIGHT_E, counterweight,
				SiteAnchors.Anchor.COUNTERWEIGHT_S, counterweight, SiteAnchors.Anchor.SENTINEL_1, SpawnerGroups.WATCHER,
				SiteAnchors.Anchor.SENTINEL_2, SpawnerGroups.WATCHER, SiteAnchors.Anchor.CHEST_1, chest));
		templates.add(frozen("jedi_ruin/pilgrim_niche", 9, 7, 7, SiteAnchors.Anchor.PEDESTAL_WAYFARER, pedestal));
		templates.add(frozen("jedi_ruin/anchor_cache", 9, 6, 9, SiteAnchors.Anchor.CACHE, chest));
		templates.add(frozen("sith_tomb/gate", 41, 30, 41, SiteAnchors.Anchor.STAIR_MOUTH, air, SiteAnchors.Anchor.SURFACE_GUARD, air));
		templates.add(frozen("sith_tomb/gate_2", 41, 30, 41, SiteAnchors.Anchor.STAIR_MOUTH, air, SiteAnchors.Anchor.SURFACE_GUARD, air));
		templates.add(frozen("sith_tomb/path", 7, 3, 13));
		templates.add(frozen("sith_tomb/dig_camp", 31, 12, 31, SiteAnchors.Anchor.SQUAD, SpawnerGroups.DIG_TEAM, SiteAnchors.Anchor.CHEST_1, chest));
		templates.add(frozen("sith_tomb/processional", 9, 14, 27, SiteAnchors.Anchor.VENT_1, vent, SiteAnchors.Anchor.VENT_2, vent,
				SiteAnchors.Anchor.VENT_3, vent, SiteAnchors.Anchor.VENT_4, vent, SiteAnchors.Anchor.SENTINEL_1, SpawnerGroups.TOMB,
				SiteAnchors.Anchor.DIGGERS, SpawnerGroups.DIGGERS, SiteAnchors.Anchor.NICHE, veiled));
		templates.add(frozen("sith_tomb/burial_chamber", 27, 14, 27, SiteAnchors.Anchor.HEART, air, SiteAnchors.Anchor.URN_KEEPER, PuzzleBlocks.URN_KEEPER,
				SiteAnchors.Anchor.URN_1, urn, SiteAnchors.Anchor.URN_2, urn, SiteAnchors.Anchor.URN_3, urn, SiteAnchors.Anchor.URN_4, urn,
				SiteAnchors.Anchor.PEDESTAL_SITH, pedestal, SiteAnchors.Anchor.SENTINEL_1, SpawnerGroups.TOMB,
				SiteAnchors.Anchor.SENTINEL_2, SpawnerGroups.TOMB, SiteAnchors.Anchor.CHEST_1, chest));
		templates.add(frozen("sith_tomb/pilgrim_niche", 9, 7, 9, SiteAnchors.Anchor.PEDESTAL_WAYFARER, pedestal));
		templates.add(frozen("sith_tomb/rubble_room", 11, 8, 11, SiteAnchors.Anchor.CACHE, chest));
		templates.add(frozen("wreck_field/hull", 71, 26, 31, SiteAnchors.Anchor.HEART, air, SiteAnchors.Anchor.HATCH, veiled,
				SiteAnchors.Anchor.COUNTERWEIGHT, counterweight, SiteAnchors.Anchor.PEDESTAL_WAYFARER, pedestal, SiteAnchors.Anchor.DECK_CACHE, chest,
				SiteAnchors.Anchor.ANCHOR_LOCKER, chest, SiteAnchors.Anchor.SCAVENGER_1, SpawnerGroups.SCAVENGERS,
				SiteAnchors.Anchor.SCAVENGER_2, SpawnerGroups.SCAVENGERS, SiteAnchors.Anchor.SURFACE_GUARD, air));
		templates.add(frozen("wreck_field/hull_2", 71, 26, 31, SiteAnchors.Anchor.HEART, air, SiteAnchors.Anchor.HATCH, veiled,
				SiteAnchors.Anchor.COUNTERWEIGHT, counterweight, SiteAnchors.Anchor.PEDESTAL_WAYFARER, pedestal, SiteAnchors.Anchor.DECK_CACHE, chest,
				SiteAnchors.Anchor.ANCHOR_LOCKER, chest, SiteAnchors.Anchor.SCAVENGER_1, SpawnerGroups.SCAVENGERS,
				SiteAnchors.Anchor.SCAVENGER_2, SpawnerGroups.SCAVENGERS, SiteAnchors.Anchor.SURFACE_GUARD, air));
		templates.add(frozen("wreck_field/path", 7, 3, 13));
		templates.add(frozen("wreck_field/salvage_camp", 29, 12, 29, SiteAnchors.Anchor.SQUAD, SpawnerGroups.SALVAGE_CREW, SiteAnchors.Anchor.CHEST_1, chest));
		templates.add(frozen("wreck_field/scar", 23, 6, 25));

		for (String army : List.of("republic", "imperial")) {
			templates.add(frozen("outpost/" + army + "_post", 45, 34, 45, SiteAnchors.Anchor.TERMINAL, terminal,
					SiteAnchors.Anchor.OFFICER, air, SiteAnchors.Anchor.GARRISON_1, air, SiteAnchors.Anchor.GARRISON_2, air,
					SiteAnchors.Anchor.GARRISON_3, air, SiteAnchors.Anchor.GARRISON_4, air));
		}

		Map<String, String> shrineGuards = Map.of("jedi", SpawnerGroups.WATCHER, "sith", SpawnerGroups.TOMB, "wayfarer", SpawnerGroups.SCAVENGERS);

		for (String kind : List.of("jedi", "sith", "wayfarer")) {
			templates.add(frozen("site/echo_shrine_" + kind, 27, 24, 27, SiteAnchors.Anchor.HEART, air, SiteAnchors.Anchor.PEDESTAL, pedestal,
					SiteAnchors.Anchor.COUNTERWEIGHT, counterweight, SiteAnchors.Anchor.GUARD, shrineGuards.get(kind)));
		}

		return templates;
	}

	/** The pedestal kind a template's pedestal anchor must carry. */
	private static PedestalKind pedestalKind(String path, SiteAnchors.Anchor anchor) {
		return switch (anchor) {
			case PEDESTAL_JEDI -> PedestalKind.JEDI;
			case PEDESTAL_SITH -> PedestalKind.SITH;
			case PEDESTAL_WAYFARER -> PedestalKind.WAYFARER;
			default -> path.endsWith("_sith") ? PedestalKind.SITH : path.endsWith("_wayfarer") ? PedestalKind.WAYFARER : PedestalKind.JEDI;
		};
	}

	/** The template's block info of {@code block} at the template-local {@code cell}, or null. */
	private static StructureTemplate.@Nullable StructureBlockInfo infoAt(Map<Block, List<StructureTemplate.StructureBlockInfo>> cache, StructureTemplate template,
			Block block, BlockPos cell) {
		// API-CHECK: StructureTemplate#filterBlocks(BlockPos, StructurePlaceSettings, Block) (worldgen dump 2167) with
		// origin ZERO and default settings gives template-local positions; StructureBlockInfo is a record (pos, state, nbt).
		List<StructureTemplate.StructureBlockInfo> infos = cache.computeIfAbsent(block, b -> template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), b));

		for (StructureTemplate.StructureBlockInfo info : infos) {
			if (info.pos().equals(cell)) {
				return info;
			}
		}

		return null;
	}

	/**
	 * Every frozen W1 template loads with its frozen size; its sidecar names exactly its frozen
	 * anchors, once each, inside the size; and each anchor cell holds what the anchor promises (the
	 * pedestal of the right kind, a loot chest, a terminal of the right army, a vent, the keeper and
	 * its urns, a counterweight, a veiled stone, a spawner with the right group, or two air cells to
	 * stand in). Loads the templates; never places one.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void everyFrozenTemplateHasSidecarAnchorsInsideSize(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		for (FrozenTemplate frozen : frozenTemplates()) {
			Identifier id = OldRepublicMod.id(frozen.path());
			StructureTemplate template = level.getStructureManager().get(id).orElse(null);
			helper.assertTrue(template != null, msg("missing structure template " + id));
			Vec3i size = template.getSize();
			helper.assertTrue(size.equals(frozen.size()), msg(id + " has size " + size + ", frozen " + frozen.size()));
			Map<Block, List<StructureTemplate.StructureBlockInfo>> cache = new HashMap<>();

			for (SiteAnchors.Anchor anchor : SiteAnchors.Anchor.values()) {
				List<BlockPos> cells = SiteAnchors.of(id, anchor);
				Object expected = frozen.anchors().get(anchor);

				if (expected == null) {
					helper.assertTrue(cells.isEmpty(), msg(id + " names an anchor that is not frozen for it: " + anchor));
					continue;
				}

				helper.assertTrue(cells.size() == 1, msg(id + " must name " + anchor + " exactly once, has " + cells));
				BlockPos cell = cells.getFirst();
				helper.assertTrue(cell.getX() >= 0 && cell.getY() >= 0 && cell.getZ() >= 0 && cell.getX() < size.getX() && cell.getY() < size.getY()
						&& cell.getZ() < size.getZ(), msg(id + " " + anchor + " " + cell.toShortString() + " lies outside " + size));

				if (expected instanceof String group) {
					StructureTemplate.StructureBlockInfo info = infoAt(cache, template, SiteBlocks.SITE_SPAWNER, cell);
					helper.assertTrue(info != null && info.nbt() != null && group.equals(info.nbt().getStringOr("group", "")),
							msg(id + " " + anchor + " must hold a site_spawner of group " + group));
				} else if (expected == Blocks.AIR) {
					helper.assertTrue(infoAt(cache, template, Blocks.AIR, cell) != null && infoAt(cache, template, Blocks.AIR, cell.above()) != null,
							msg(id + " " + anchor + " must be two air cells to stand in"));
				} else {
					Block block = (Block) expected;
					StructureTemplate.StructureBlockInfo info = infoAt(cache, template, block, cell);
					helper.assertTrue(info != null, msg(id + " " + anchor + " must hold " + block.getDescriptionId()));

					if (block == PuzzleBlocks.HOLOCRON_PEDESTAL) {
						PedestalKind kind = pedestalKind(frozen.path(), anchor);
						helper.assertTrue(info.state().getValue(PuzzleStates.KIND) == kind, msg(id + " " + anchor + " must be a " + kind + " pedestal"));
					} else if (block == SiteBlocks.BOUNTY_TERMINAL) {
						SiteStates.Army army = frozen.path().contains("republic") ? SiteStates.Army.REPUBLIC : SiteStates.Army.IMPERIAL;
						helper.assertTrue(info.state().getValue(SiteStates.FACTION) == army, msg(id + " terminal must be " + army));
					} else if (block == Blocks.CHEST) {
						helper.assertTrue(info.nbt() != null && info.nbt().getStringOr("LootTable", "").startsWith(OldRepublicMod.MOD_ID + ":chests/"),
								msg(id + " " + anchor + " chest must carry one of the mod's chest loot tables"));
					}
				}
			}
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- outposts and the world's sites

	/**
	 * A mission's camp lookup answers only the camp: with a runtime outpost record whose box also
	 * holds the camp's stand (a forward-fallback camp set against an outpost, seen in a playthrough),
	 * {@code runtimePieceAt(..., MISSION_SITE)} gives the camp with its crate anchor. The records lie
	 * far from every test, and the server's saved records are restored.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void missionCampLookupIgnoresAnOverlappingOutpost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldSites.Data saved = WorldSites.snapshot(level.getServer());
		BlockPos outpostOrigin = new BlockPos(1_000_000, 80, 1_000_000);
		WorldSites.SiteRecord outpost = outpostRecord(SiteStates.Army.REPUBLIC, level.dimension(), outpostOrigin, Rotation.NONE);
		WorldSites.SiteRecord camp = new WorldSites.SiteRecord(SiteKind.MISSION_SITE, level.dimension(), outpostOrigin.offset(6, 0, 6), Rotation.NONE,
				OldRepublicMod.id("mission/smuggler_cache"), null, 0L);

		try {
			WorldSites.add(level.getServer(), outpost);
			WorldSites.add(level.getServer(), camp);
			SitePiece outpostPiece = RuntimeSites.pieceOf(level, outpost);
			SitePiece campPiece = RuntimeSites.pieceOf(level, camp);
			helper.assertTrue(outpostPiece != null && campPiece != null, msg("both templates load"));
			BlockPos stand = campPiece.box().getCenter();
			helper.assertTrue(outpostPiece.box().isInside(stand), msg("precondition: the outpost's box holds the camp's stand " + stand.toShortString()));
			SitePiece found = StructureLocator.runtimePieceAt(level, stand, SiteKind.MISSION_SITE);
			helper.assertTrue(found != null && found.kind() == SiteKind.MISSION_SITE && found.origin().equals(camp.origin()),
					msg("the camp answers, not the outpost; got " + (found == null ? null : found.kind() + " at " + found.origin().toShortString())));
			helper.assertTrue(SiteAnchors.find(found, SiteAnchors.Anchor.CRATE) != null, msg("the camp's crate anchor is found"));
			BlockPos outside = outpostOrigin.offset(2, 2, 2);
			helper.assertTrue(!campPiece.box().isInside(outside) && StructureLocator.runtimePieceAt(level, outside, SiteKind.MISSION_SITE) == null,
					msg("in the outpost but outside the camp, no mission piece answers"));
		} finally {
			WorldSites.commit(level.getServer(), saved);
		}

		helper.succeed();
	}

	/** A runtime outpost record of {@code army} placed at {@code origin} (the template's local 0, 0, 0). */
	private static WorldSites.SiteRecord outpostRecord(SiteStates.Army army, ResourceKey<Level> dimension, BlockPos origin, Rotation rotation) {
		return new WorldSites.SiteRecord(OutpostPlacement.kind(army), dimension, origin, rotation, OutpostPlacement.template(army), null, 0L);
	}

	/**
	 * The decision matrix of spec 3.5 on detached {@link WorldSites.Data} values: a worldgen outpost
	 * within 1024 (inclusive) wins; else a runtime outpost of the same army within 768 of its footprint
	 * centre is reused; else a new one is placed, unless runtime outposts are off or 8 exist (both
	 * armies, every dimension), when the nearest existing one is used. Never touches the server's sites.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void outpostDecideMatrix(GameTestHelper helper) {
		SiteBalance b = new SiteBalance();
		BlockPos player = new BlockPos(1000, 70, -2000);
		ResourceKey<Level> overworld = Level.OVERWORLD;
		WorldSites.Data none = new WorldSites.Data();

		// WORLDGEN: within the radius and exactly on it (1024 away); one block further is out.
		helper.assertTrue(Outposts.decide(Outposts.query(player, player.offset(300, 0, 0), none, Faction.REPUBLIC, overworld), b) == Outposts.Decision.WORLDGEN,
				msg("a worldgen outpost 300 blocks away is used"));
		helper.assertTrue(Outposts.decide(Outposts.query(player, player.offset(1024, 5, 0), none, Faction.REPUBLIC, overworld), b) == Outposts.Decision.WORLDGEN,
				msg("the worldgen radius is inclusive (1024) and horizontal"));
		helper.assertTrue(Outposts.decide(Outposts.query(player, player.offset(1025, 0, 0), none, Faction.REPUBLIC, overworld), b) == Outposts.Decision.PLACE,
				msg("a worldgen outpost 1025 away is too far, and with no runtime outpost a new one is placed"));

		// REUSE: the footprint centre (origin + 22, 22 turned by the rotation) within 768, same army only.
		WorldSites.Data near = new WorldSites.Data();
		near.add(outpostRecord(SiteStates.Army.REPUBLIC, overworld, player.offset(478, 0, -22), Rotation.NONE));
		Outposts.OutpostQuery reuse = Outposts.query(player, null, near, Faction.REPUBLIC, overworld);
		helper.assertTrue(player.offset(500, 0, 0).equals(reuse.runtimeNearest()) && reuse.runtimeCount() == 1,
				msg("the query names the footprint centre and counts the record; got " + reuse));
		helper.assertTrue(Outposts.decide(reuse, b) == Outposts.Decision.REUSE, msg("a runtime outpost 500 away is reused"));
		helper.assertTrue(Outposts.decide(Outposts.query(player, player.offset(1025, 0, 0), near, Faction.REPUBLIC, overworld), b) == Outposts.Decision.REUSE,
				msg("a far worldgen outpost does not beat a near runtime one"));
		helper.assertTrue(Outposts.decide(Outposts.query(player, player.offset(600, 0, 0), near, Faction.REPUBLIC, overworld), b) == Outposts.Decision.WORLDGEN,
				msg("a worldgen outpost in range comes first"));
		Outposts.OutpostQuery otherArmy = Outposts.query(player, null, near, Faction.SITH_EMPIRE, overworld);
		helper.assertTrue(otherArmy.runtimeNearest() == null && otherArmy.runtimeCount() == 1 && Outposts.decide(otherArmy, b) == Outposts.Decision.PLACE,
				msg("the other army's outpost is never reused, but counts toward the cap"));

		WorldSites.Data edge = new WorldSites.Data();
		edge.add(outpostRecord(SiteStates.Army.REPUBLIC, overworld, player.offset(768 - 22, 0, -22), Rotation.NONE));
		helper.assertTrue(Outposts.decide(Outposts.query(player, null, edge, Faction.REPUBLIC, overworld), b) == Outposts.Decision.REUSE,
				msg("the reuse radius is inclusive (768)"));
		WorldSites.Data beyond = new WorldSites.Data();
		beyond.add(outpostRecord(SiteStates.Army.REPUBLIC, overworld, player.offset(769 - 22, 0, -22), Rotation.NONE));
		helper.assertTrue(Outposts.decide(Outposts.query(player, null, beyond, Faction.REPUBLIC, overworld), b) == Outposts.Decision.PLACE,
				msg("a runtime outpost 769 away is not reused"));

		WorldSites.Data turned = new WorldSites.Data();
		BlockPos turnedOrigin = player.offset(100, 0, 100);
		turned.add(outpostRecord(SiteStates.Army.IMPERIAL, overworld, turnedOrigin, Rotation.CLOCKWISE_90));
		helper.assertTrue(turnedOrigin.offset(-22, 0, 22).equals(Outposts.query(player, null, turned, Faction.SITH_EMPIRE, overworld).runtimeNearest()),
				msg("the footprint centre turns with the record's rotation"));

		WorldSites.Data elsewhere = new WorldSites.Data();
		elsewhere.add(outpostRecord(SiteStates.Army.REPUBLIC, Level.NETHER, player, Rotation.NONE));
		Outposts.OutpostQuery otherDimension = Outposts.query(player, null, elsewhere, Faction.REPUBLIC, overworld);
		helper.assertTrue(otherDimension.runtimeNearest() == null && otherDimension.runtimeCount() == 1, msg("an outpost in another dimension is never near"));

		// CAP_NEAREST: 8 runtime outposts in the world (both armies, two dimensions), none in range.
		WorldSites.Data capped = new WorldSites.Data();

		for (int i = 0; i < 4; i++) {
			capped.add(outpostRecord(SiteStates.Army.REPUBLIC, overworld, player.offset(2000 + 300 * i, 0, 0), Rotation.NONE));
			capped.add(outpostRecord(SiteStates.Army.IMPERIAL, Level.NETHER, player.offset(0, 0, 300 * i), Rotation.NONE));
		}

		Outposts.OutpostQuery full = Outposts.query(player, null, capped, Faction.REPUBLIC, overworld);
		helper.assertTrue(full.runtimeCount() == b.maxRuntimeOutposts && Outposts.decide(full, b) == Outposts.Decision.CAP_NEAREST,
				msg("at the cap of " + b.maxRuntimeOutposts + " no outpost is placed; got " + full));
		helper.assertTrue(player.offset(2022, 0, 22).equals(full.runtimeNearest()), msg("CAP_NEAREST uses the nearest outpost of the army, at any distance"));
		helper.assertTrue(Outposts.decide(Outposts.query(player, player.offset(10, 0, 10), capped, Faction.REPUBLIC, overworld), b) == Outposts.Decision.WORLDGEN,
				msg("a worldgen outpost in range is still used at the cap"));
		helper.assertTrue(Outposts.decide(Outposts.query(player, null, capped, Faction.SITH_EMPIRE, overworld), b) == Outposts.Decision.CAP_NEAREST,
				msg("the cap counts both armies"));
		WorldSites.Data seven = capped.copy();
		WorldSites.Data fewer = new WorldSites.Data();
		seven.all().stream().skip(1).forEach(fewer::add);
		helper.assertTrue(Outposts.decide(Outposts.query(player, null, fewer, Faction.REPUBLIC, overworld), b) == Outposts.Decision.PLACE,
				msg("below the cap a new outpost is placed"));

		SiteBalance off = new SiteBalance();
		off.runtimeOutposts = false;
		helper.assertTrue(Outposts.decide(Outposts.query(player, null, none, Faction.REPUBLIC, overworld), off) == Outposts.Decision.CAP_NEAREST,
				msg("with runtime outposts off nothing is placed"));

		boolean refused = false;

		try {
			Outposts.query(player, null, none, Faction.JEDI_ORDER, overworld);
		} catch (IllegalArgumentException expected) {
			refused = true;
		}

		helper.assertTrue(refused, msg("the Jedi Order has no outposts"));
		helper.succeed();
	}

	/**
	 * {@code oldrepublic:world_sites} survives its codec: records (every field, owner or none, two
	 * dimensions, rotations) in order, and the expired cairn owners in order. Also: the same placement
	 * replaces its record, a copy is independent, the expired owners keep the newest 64, and the
	 * record cap drops the oldest non-outpost first. Detached values only.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void worldSitesCodecRoundTrip(GameTestHelper helper) {
		UUID owner = new UUID(0x5157E5L, 1L);
		UUID other = new UUID(0x5157E5L, 2L);
		WorldSites.Data data = new WorldSites.Data();
		data.add(new WorldSites.SiteRecord(SiteKind.ECHO_SHRINE, Level.OVERWORLD, new BlockPos(100, 64, -200), Rotation.CLOCKWISE_90,
				EchoShrines.template(HolocronKind.JEDI), owner, 3L));
		data.add(outpostRecord(SiteStates.Army.REPUBLIC, Level.OVERWORLD, new BlockPos(-640, 70, 512), Rotation.NONE));
		data.add(new WorldSites.SiteRecord(SiteKind.MISSION_SITE, Level.NETHER, new BlockPos(8, 40, 8), Rotation.COUNTERCLOCKWISE_90,
				OldRepublicMod.id("mission/smuggler_cache"), other, 41L));
		data.expireCairnOwner(owner);
		data.expireCairnOwner(other);

		Tag nbt = WorldSites.Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		WorldSites.Data back = WorldSites.Data.CODEC.parse(NbtOps.INSTANCE, nbt).getOrThrow();
		helper.assertTrue(back.all().equals(data.all()), msg("records survive the codec in order; got " + back.all()));
		helper.assertTrue(List.copyOf(back.expiredCairnOwners()).equals(List.of(owner, other)), msg("expired cairn owners survive in order"));
		WorldSites.Data empty = WorldSites.Data.CODEC.parse(NbtOps.INSTANCE, WorldSites.Data.CODEC.encodeStart(NbtOps.INSTANCE, new WorldSites.Data()).getOrThrow())
				.getOrThrow();
		helper.assertTrue(empty.all().isEmpty() && empty.expiredCairnOwners().isEmpty(), msg("an empty value round-trips empty"));

		// The same placement replaces its record (newest data, newest position in the order).
		WorldSites.Data replaced = data.copy();
		replaced.add(new WorldSites.SiteRecord(SiteKind.ECHO_SHRINE, Level.OVERWORLD, new BlockPos(100, 64, -200), Rotation.NONE,
				EchoShrines.template(HolocronKind.JEDI), owner, 9L));
		helper.assertTrue(replaced.all().size() == 3 && replaced.all().getLast().placedDay() == 9L, msg("adding the same placement replaces it"));
		helper.assertTrue(data.all().getFirst().placedDay() == 3L, msg("a copy is independent of its source"));

		// Expired owners: the newest 64 are kept.
		WorldSites.Data owners = new WorldSites.Data();

		for (int i = 0; i < WorldSites.Data.MAX_EXPIRED_OWNERS + 6; i++) {
			owners.expireCairnOwner(new UUID(0xCA1L, i));
		}

		helper.assertTrue(owners.expiredCairnOwners().size() == WorldSites.Data.MAX_EXPIRED_OWNERS && !owners.expiredCairnOwners().contains(new UUID(0xCA1L, 0))
				&& owners.expiredCairnOwners().contains(new UUID(0xCA1L, WorldSites.Data.MAX_EXPIRED_OWNERS + 5)), msg("the oldest expired owners are dropped first"));

		// The record cap keeps outposts: the oldest record that is not an outpost is dropped.
		WorldSites.Data full = new WorldSites.Data();
		WorldSites.SiteRecord outpost = outpostRecord(SiteStates.Army.IMPERIAL, Level.OVERWORLD, new BlockPos(0, 64, 0), Rotation.NONE);
		full.add(outpost);

		for (int i = 0; i < WorldSites.Data.MAX_RECORDS; i++) {
			full.add(new WorldSites.SiteRecord(SiteKind.ECHO_SHRINE, Level.OVERWORLD, new BlockPos(64 * i, 64, 4096), Rotation.NONE,
					EchoShrines.template(HolocronKind.SITH), owner, i));
		}

		helper.assertTrue(full.all().size() == WorldSites.Data.MAX_RECORDS && full.all().contains(outpost) && full.all().get(1).placedDay() == 1L,
				msg("at the cap the oldest shrine went, the outpost stayed"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- bounty terminals (terminal_fixture)

	/** The fixture's real terminal on its TERMINAL stand-in, screen south toward the OFFICER spot. */
	private static BountyTerminalBlockEntity fixtureTerminal(GameTestHelper helper, SiteStates.Army army) {
		BlockPos local = SiteAnchors.of(TERMINAL_FIXTURE_ID, SiteAnchors.Anchor.TERMINAL).getFirst();
		helper.setBlock(local, SiteBlocks.BOUNTY_TERMINAL.defaultBlockState().setValue(SiteStates.FACTION, army).setValue(SiteStates.FACING, Direction.SOUTH));
		return helper.getBlockEntity(local, BountyTerminalBlockEntity.class);
	}

	/** The absolute position of a terminal fixture anchor. */
	private static BlockPos fixtureSpot(GameTestHelper helper, SiteAnchors.Anchor anchor) {
		return helper.absolutePos(SiteAnchors.of(TERMINAL_FIXTURE_ID, anchor).getFirst());
	}

	/** An armour stand standing in for a member (as the Phase 0 trooper stubs do), bound to {@code slot} of {@code terminal}. */
	private static ArmorStand standIn(GameTestHelper helper, BlockPos absolute, BlockPos terminal, int slot) {
		ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, local(helper, absolute));

		if (slot >= 0) {
			TerminalBinding.bind(stand, terminal, slot, SiteBalance.get().garrisonLeash);
		}

		return stand;
	}

	/** A saved terminal state: {@code members} in slot order (null = never filled) and the failed counts. */
	private static GarrisonRules.State garrisonState(List<UUID> members, int[] failed, long refillAt) {
		List<GarrisonRules.Slot> slots = new ArrayList<>(GarrisonRules.SLOTS);

		for (int i = 0; i < GarrisonRules.SLOTS; i++) {
			slots.add(new GarrisonRules.Slot(members.get(i), failed[i]));
		}

		return new GarrisonRules.State(slots, refillAt);
	}

	/** Five fixed member UUIDs (seeded, never a live entity's). */
	private static List<UUID> listedMembers(long seed) {
		List<UUID> members = new ArrayList<>();

		for (int i = 0; i < GarrisonRules.SLOTS; i++) {
			members.add(new UUID(seed, i));
		}

		return members;
	}

	/**
	 * The loading rule (spec 3.3): while a terminal's entities are not loaded nothing is counted,
	 * missed or spawned, even for a terminal that never spawned; once they are, a never-filled
	 * terminal fills all five slots at once, and in the world loaded members bound to their slots
	 * count as present, so a check spawns nothing and changes nothing.
	 */
	@GameTest(structure = TERMINAL_FIXTURE, maxTicks = 100)
	public void terminalWaitsForLoadedEntities(GameTestHelper helper) {
		SiteBalance b = new SiteBalance();
		long now = 50_000L;
		GarrisonRules.Observation unloaded = new GarrisonRules.Observation(false, 0, false);
		GarrisonRules.State never = GarrisonRules.State.empty();
		GarrisonRules.Result waiting = GarrisonRules.step(never, unloaded, now, b);
		helper.assertTrue(waiting.spawn().isEmpty() && waiting.next().equals(never), msg("an unloaded, never-filled terminal waits"));
		GarrisonRules.State filled = garrisonState(listedMembers(0x7E1L), new int[] {1, 1, 1, 1, 1}, 0L);
		GarrisonRules.Result stillWaiting = GarrisonRules.step(filled, unloaded, now, b);
		helper.assertTrue(stillWaiting.spawn().isEmpty() && stillWaiting.next().equals(filled), msg("no miss is counted while the entities are not loaded"));
		GarrisonRules.Result first = GarrisonRules.step(never, new GarrisonRules.Observation(true, 0, true), now, b);
		helper.assertTrue(first.spawn().equals(List.of(0, 1, 2, 3, 4)) && first.next().refillAt() == now,
				msg("once loaded, a never-filled terminal fills every slot at once, players near or not; got " + first.spawn()));

		ServerLevel level = helper.getLevel();
		BountyTerminalBlockEntity terminal = fixtureTerminal(helper, SiteStates.Army.REPUBLIC);
		BlockPos pos = terminal.getBlockPos();
		List<ArmorStand> members = new ArrayList<>();

		for (int slot = 0; slot < GarrisonRules.SLOTS; slot++) {
			members.add(standIn(helper, fixtureSpot(helper, SLOT_ANCHORS.get(slot)), pos, slot));
		}

		terminal.restore(garrisonState(members.stream().map(Entity::getUUID).toList(), new int[GarrisonRules.SLOTS], level.getGameTime()));
		GarrisonRules.State before = terminal.state();

		helper.succeedWhen(() -> {
			helper.assertTrue(SiteChecks.entitiesReady(level, pos, 1), msg("waiting for the fixture's entities to load"));
			GarrisonRules.Observation seen = terminal.observe(level, SiteBalance.get());
			helper.assertTrue(seen.loaded() && seen.present() == 0b11111, msg("all five loaded members count as present; mask " + seen.present()));
			helper.assertTrue(terminal.check(level).isEmpty(), msg("nothing is missing, so nothing spawns"));
			helper.assertTrue(terminal.state().equals(before), msg("present members keep their slots with no misses"));
			members.forEach(Entity::discard);
		});
	}

	/**
	 * Two checks (spec 3.3, 7.4): a listed member missing on one check is only counted; on the
	 * second the officer is replaced at once, while a garrison trooper waits for the 12,000-tick
	 * refill with nobody within 48 blocks, one trooper per refill; a member seen again resets its
	 * count. In the world the replacement officer stands on the OFFICER spot, bound to slot 0, and
	 * the garrison slots keep their listed members.
	 */
	@GameTest(structure = TERMINAL_FIXTURE, maxTicks = 100)
	public void terminalRespawnsAfterTwoFailedChecks(GameTestHelper helper) {
		SiteBalance b = new SiteBalance();
		List<UUID> listed = listedMembers(0x7E2L);
		GarrisonRules.State state = garrisonState(listed, new int[GarrisonRules.SLOTS], 0L);
		GarrisonRules.Observation nobody = new GarrisonRules.Observation(true, 0, false);

		GarrisonRules.Result one = GarrisonRules.step(state, nobody, 1_000L, b);
		helper.assertTrue(one.spawn().isEmpty(), msg("one empty check spawns nothing"));

		for (int slot = 0; slot < GarrisonRules.SLOTS; slot++) {
			GarrisonRules.Slot s = one.next().slots().get(slot);
			helper.assertTrue(listed.get(slot).equals(s.member()) && s.failed() == 1, msg("slot " + slot + " keeps its member with one miss"));
		}

		GarrisonRules.Result two = GarrisonRules.step(one.next(), nobody, 1_100L, b);
		helper.assertTrue(two.spawn().equals(List.of(GarrisonRules.OFFICER)), msg("the second empty check replaces the officer only; got " + two.spawn()));
		helper.assertTrue(two.next().slots().get(GarrisonRules.OFFICER).failed() == 0 && two.next().slots().get(1).failed() == 2,
				msg("the officer's count resets; the garrison's grows"));

		GarrisonRules.Observation officerBack = new GarrisonRules.Observation(true, 1, true);
		GarrisonRules.Result crowded = GarrisonRules.step(two.next(), officerBack, b.garrisonRefillTicks, b);
		helper.assertTrue(crowded.spawn().isEmpty(), msg("no refill while a player is within " + b.garrisonRefillClearRange));
		GarrisonRules.Result refill = GarrisonRules.step(crowded.next(), new GarrisonRules.Observation(true, 1, false), b.garrisonRefillTicks, b);
		helper.assertTrue(refill.spawn().equals(List.of(1)) && refill.next().refillAt() == b.garrisonRefillTicks,
				msg("one trooper per refill, the first missing slot; got " + refill.spawn()));
		GarrisonRules.Result tooSoon = GarrisonRules.step(refill.next(), new GarrisonRules.Observation(true, 0b11, false), b.garrisonRefillTicks + 200L, b);
		helper.assertTrue(tooSoon.spawn().isEmpty(), msg("the next trooper waits for the next refill"));
		GarrisonRules.Result later = GarrisonRules.step(tooSoon.next(), new GarrisonRules.Observation(true, 0b11, false), 2L * b.garrisonRefillTicks, b);
		helper.assertTrue(later.spawn().equals(List.of(2)), msg("after another refill time the next missing trooper comes; got " + later.spawn()));

		GarrisonRules.Result seenAgain = GarrisonRules.step(one.next(), new GarrisonRules.Observation(true, 0b11110, false), 1_100L, b);
		helper.assertTrue(seenAgain.next().slots().get(3).failed() == 0 && seenAgain.spawn().equals(List.of(GarrisonRules.OFFICER)),
				msg("members seen again reset their counts"));
		SiteBalance patient = new SiteBalance();
		patient.terminalChecksBeforeRespawn = 3;
		helper.assertTrue(GarrisonRules.step(one.next(), nobody, 1_100L, patient).spawn().isEmpty(), msg("the check count comes from the balance"));

		ServerLevel level = helper.getLevel();
		BountyTerminalBlockEntity terminal = fixtureTerminal(helper, SiteStates.Army.REPUBLIC);
		BlockPos pos = terminal.getBlockPos();
		// Every listed member is gone and was missed once; the garrison's refill is not due.
		terminal.restore(garrisonState(listed, new int[] {1, 1, 1, 1, 1}, level.getGameTime()));
		boolean[] checked = {false};

		helper.succeedWhen(() -> {
			helper.assertTrue(SiteChecks.entitiesReady(level, pos, 1), msg("waiting for the fixture's entities to load"));

			if (!checked[0]) {
				checked[0] = true;
				terminal.check(level);
			}

			UUID officer = terminal.member(GarrisonRules.OFFICER);
			helper.assertTrue(officer != null && !officer.equals(listed.getFirst()), msg("the officer was replaced on the second empty check"));
			Entity replacement = level.getEntity(officer);
			helper.assertTrue(replacement != null && TerminalBinding.slotAt(replacement, pos) == GarrisonRules.OFFICER,
					msg("the new officer is bound to slot 0 of this terminal"));
			helper.assertTrue(replacement.blockPosition().equals(fixtureSpot(helper, SiteAnchors.Anchor.OFFICER)),
					msg("a lone terminal's officer stands two blocks in front of the screen, at " + replacement.blockPosition().toShortString()));

			for (int slot = 1; slot < GarrisonRules.SLOTS; slot++) {
				GarrisonRules.Slot s = terminal.state().slots().get(slot);
				helper.assertTrue(listed.get(slot).equals(s.member()) && s.failed() >= 2, msg("garrison slot " + slot + " waits for its refill"));
			}

			replacement.discard();
		});
	}

	/**
	 * The stale copy rule (spec 7.4): a member that loads while its terminal is loaded and lists
	 * someone else for its slot discards itself; the listed member, an unlisted member of an empty
	 * slot, an unbound entity and one bound to a position with no terminal stay. The terminal's check
	 * does the same in its leash box and adopts the unlisted member.
	 */
	@GameTest(structure = TERMINAL_FIXTURE, maxTicks = 100)
	public void staleOfficerDiscardsItself(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BountyTerminalBlockEntity terminal = fixtureTerminal(helper, SiteStates.Army.IMPERIAL);
		BlockPos pos = terminal.getBlockPos();
		BlockPos officerSpot = fixtureSpot(helper, SiteAnchors.Anchor.OFFICER);
		ArmorStand officer = standIn(helper, officerSpot, pos, GarrisonRules.OFFICER);
		ArmorStand stale = standIn(helper, officerSpot.east(2), pos, GarrisonRules.OFFICER);
		ArmorStand unlisted = standIn(helper, fixtureSpot(helper, SiteAnchors.Anchor.GARRISON_2), pos, 2);
		ArmorStand stranger = standIn(helper, fixtureSpot(helper, SiteAnchors.Anchor.GARRISON_3), pos, -1);
		ArmorStand orphan = standIn(helper, fixtureSpot(helper, SiteAnchors.Anchor.GARRISON_4), pos.above(2), GarrisonRules.OFFICER);
		terminal.restore(garrisonState(Arrays.asList(officer.getUUID(), null, null, null, null), new int[GarrisonRules.SLOTS], level.getGameTime()));

		TerminalBinding.onEntityLoad(stale, level);
		helper.assertTrue(stale.isRemoved(), msg("a copy whose terminal lists someone else discards itself when it loads"));

		for (ArmorStand kept : List.of(officer, unlisted, stranger, orphan)) {
			TerminalBinding.onEntityLoad(kept, level);
			helper.assertTrue(!kept.isRemoved(), msg("the listed officer, an unlisted member, an unbound stand and a stand with no terminal stay"));
		}

		ArmorStand secondStale = standIn(helper, officerSpot.west(2), pos, GarrisonRules.OFFICER);

		helper.succeedWhen(() -> {
			helper.assertTrue(SiteChecks.entitiesReady(level, pos, 1), msg("waiting for the fixture's entities to load"));
			GarrisonRules.Observation seen = terminal.observe(level, SiteBalance.get());
			helper.assertTrue(secondStale.isRemoved(), msg("the check discards a stale copy in its leash box"));
			helper.assertTrue(!officer.isRemoved() && seen.present(GarrisonRules.OFFICER), msg("the listed officer is present"));
			helper.assertTrue(unlisted.getUUID().equals(terminal.member(2)) && seen.present(2), msg("the unlisted member of an empty slot is adopted"));
			helper.assertTrue(!stranger.isRemoved() && !orphan.isRemoved(), msg("entities not bound to this terminal are left alone"));
			List.of(officer, unlisted, stranger, orphan).forEach(Entity::discard);
		});
	}

	// ---------------------------------------------------------------- site spawners, cairns, shrines

	/** A site spawner at {@code relative} with the generator's fields. */
	private static SiteSpawnerBlockEntity spawner(GameTestHelper helper, BlockPos relative, String group, int count, BlockState replaceWith) {
		helper.setBlock(relative, SiteBlocks.SITE_SPAWNER);
		SiteSpawnerBlockEntity spawner = helper.getBlockEntity(relative, SiteSpawnerBlockEntity.class);
		spawner.configure(group, count, false, replaceWith);
		return spawner;
	}

	/** The squad UUID a trooper saves as {@code Squad} (entity NBT frozen in spec 15.1), or null. */
	private static @Nullable UUID savedSquad(ServerLevel level, Entity entity) {
		// Verified against the repo's own tests: TagValueOutput.createWithContext / saveWithoutId (SentinelGameTests) and
		// TagValueInput.create (BossGameTests).
		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		entity.saveWithoutId(output);
		return TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()).read("Squad", UUIDUtil.CODEC).orElse(null);
	}

	/**
	 * A site spawner spawns its group once (spec 3.3): firing it spawns the group as one fresh squad,
	 * makes the members persistent and turns the cell into its replace-with block; it never spawns
	 * again. Two spawners of the same group make two squads with two squad ids. A third spawner
	 * fires through its own check once the loading rule holds and a listed player stands beside it.
	 * A fourth, owned spawner (an Echo Shrine's guard, 3.6) ignores that player until it is theirs,
	 * then spawns its group owned by them. With the Phase 0 trooper stubs a squad is empty, and only
	 * the once-only and replacement rules are checked. Whatever the spawners made is discarded at the
	 * end, also when a block-entity tick fired one before the test's own check.
	 */
	@GameTest(structure = ARENA, maxTicks = 100)
	public void siteSpawnerSpawnsOnceWithFreshSquadEach(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos aLocal = new BlockPos(4, 1, 4);
		BlockPos bLocal = new BlockPos(19, 1, 19);
		BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
		SiteSpawnerBlockEntity a = spawner(helper, aLocal, SpawnerGroups.DIG_TEAM, 0, Blocks.AIR.defaultBlockState());
		SiteSpawnerBlockEntity b = spawner(helper, bLocal, SpawnerGroups.DIG_TEAM, 0, lantern);

		List<LivingEntity> first = a.fire(level);
		helper.assertTrue(a.done() && helper.getBlockState(aLocal).isAir(), msg("a fired spawner is done and its cell is air"));
		helper.assertTrue(a.fire(level).isEmpty() && a.check(level).isEmpty(), msg("a spawner never spawns twice"));
		List<LivingEntity> second = b.fire(level);
		helper.assertTrue(helper.getBlockState(bLocal).equals(lantern), msg("the cell becomes the replace-with block, properties included"));

		int size = SpawnerGroups.size(SpawnerGroups.DIG_TEAM, 0, helper.absolutePos(aLocal));
		helper.assertTrue(first.size() == second.size() && (first.isEmpty() || first.size() == size),
				msg("each spawner spawns its whole group (" + size + ") or, with the stubs, nothing; got " + first.size() + " and " + second.size()));
		Set<UUID> squadsA = new HashSet<>();
		Set<UUID> squadsB = new HashSet<>();

		for (LivingEntity member : first) {
			squadsA.add(savedSquad(level, member));
			helper.assertTrue(!(member instanceof Mob mob) || mob.isPersistenceRequired(), msg("spawned members are persistent"));
		}

		for (LivingEntity member : second) {
			squadsB.add(savedSquad(level, member));
		}

		if (!first.isEmpty()) {
			helper.assertTrue(squadsA.size() == 1 && squadsB.size() == 1 && !squadsA.contains(null) && !squadsA.equals(squadsB),
					msg("each spawner makes one fresh squad: " + squadsA + " / " + squadsB));
		}

		first.forEach(Entity::discard);
		second.forEach(Entity::discard);

		BlockPos cLocal = new BlockPos(12, 1, 12);
		BlockPos dLocal = new BlockPos(12, 1, 16);
		SiteSpawnerBlockEntity c = spawner(helper, cLocal, SpawnerGroups.SCAVENGERS, 1, Blocks.AIR.defaultBlockState());
		SiteSpawnerBlockEntity d = spawner(helper, dLocal, SpawnerGroups.SCAVENGERS, 1, Blocks.AIR.defaultBlockState());
		ServerPlayer player = M3TestPlayers.listed(helper, new BlockPos(12, 1, 14));
		// Someone who is not online: an owned spawner never fires for anyone else.
		d.setOwner(new UUID(0x5EEDL, 0x0D1EL));
		List<LivingEntity> fourth = new ArrayList<>();

		helper.succeedWhen(() -> {
			if (!c.done()) {
				c.check(level);
			}

			helper.assertTrue(c.done(), msg("waiting for the spawner's chunk to load; a player stands 2 blocks away"));
			helper.assertTrue(helper.getBlockState(cLocal).isAir(), msg("the checked spawner turned to air"));
			helper.assertTrue(c.check(level).isEmpty(), msg("and does not fire again"));

			if (!d.done()) {
				helper.assertTrue(SiteChecks.entitiesReady(level, helper.absolutePos(dLocal), 0), msg("waiting for the owned spawner's chunk to load"));
				helper.assertTrue(d.check(level).isEmpty() && !d.done(), msg("an owned spawner ignores a player who is not its owner"));
				d.setOwner(player.getUUID());
				fourth.addAll(d.check(level));
			}

			helper.assertTrue(d.done() && helper.getBlockState(dLocal).isAir(), msg("the owned spawner fires once its owner stands 2 blocks away"));
			helper.assertTrue(!fourth.isEmpty() && fourth.stream().allMatch(e -> player.getUUID().equals(StoryTags.owner(e))),
					msg("its scavenger spawns owned by the owner, got " + fourth.size()));
			// Every member, including any a block-entity tick spawned before the test's own check.
			helper.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(helper.absolutePos(cLocal)).inflate(8), e -> !(e instanceof Player))
					.forEach(Entity::discard);
			M3TestPlayers.remove(player);
		});
	}

	/**
	 * Lazy cairn expiry (spec 3.3) on a detached {@link WorldSites.Data}: the cairn stays while its
	 * owner is online before the first echo or listed nowhere, and removes itself once its owner is
	 * in the expired set; a cairn without an owner never expires. The server's set is never written.
	 */
	@GameTest(structure = ARENA, maxTicks = 20)
	public void cairnRemovesItselfWhenOwnerExpired(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = M3TestPlayers.listed(helper, new BlockPos(8, 1, 6));

		try {
			BlockPos local = new BlockPos(6, 1, 6);
			helper.setBlock(local, SiteBlocks.ECHO_CAIRN);
			EchoCairnBlockEntity cairn = helper.getBlockEntity(local, EchoCairnBlockEntity.class);
			cairn.setOwner(owner.getUUID(), 1);
			BlockPos pos = helper.absolutePos(local);
			WorldSites.Data detached = new WorldSites.Data();
			detached.expireCairnOwner(new UUID(0xCA12L, 7L));

			helper.assertTrue(!cairn.checkExpiry(level, pos, owner, Set.of()), msg("an online owner before the first echo keeps the cairn"));
			helper.assertTrue(!cairn.checkExpiry(level, pos, null, detached.expiredCairnOwners()), msg("another owner's expiry leaves it"));
			helper.assertTrue(helper.getBlockState(local).is(SiteBlocks.ECHO_CAIRN), msg("the cairn is still there"));

			detached.expireCairnOwner(owner.getUUID());
			helper.assertTrue(cairn.checkExpiry(level, pos, null, detached.expiredCairnOwners()), msg("an expired owner's cairn removes itself"));
			helper.assertTrue(helper.getBlockState(local).isAir(), msg("the cairn's cell is air"));
			helper.assertTrue(!WorldSites.expiredCairnOwners(level.getServer()).contains(owner.getUUID()), msg("the server's expired owners were not written"));

			BlockPos ownerlessLocal = new BlockPos(10, 1, 6);
			helper.setBlock(ownerlessLocal, SiteBlocks.ECHO_CAIRN);
			EchoCairnBlockEntity ownerless = helper.getBlockEntity(ownerlessLocal, EchoCairnBlockEntity.class);
			helper.assertTrue(!ownerless.checkExpiry(level, helper.absolutePos(ownerlessLocal), owner, detached.expiredCairnOwners())
					&& helper.getBlockState(ownerlessLocal).is(SiteBlocks.ECHO_CAIRN), msg("a cairn without an owner never expires"));
			helper.setBlock(ownerlessLocal, Blocks.AIR);
		} finally {
			M3TestPlayers.remove(owner);
		}

		helper.succeed();
	}

	/** The arena as open grass: grass floor, air above, so the shrine search sees natural ground. */
	private static void openGround(GameTestHelper helper) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.GRASS_BLOCK);

				for (int y = 1; y < 7; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	/**
	 * The Echo Shrine search with test rings (spec 3.4, 3.6) through the seam of the 5-argument
	 * {@code EchoShrines.place} ({@code findSpot}: the search without stamping or recording): the
	 * first candidate lies along the owner's look on the ring, the shrine's entrance turns to the
	 * owner, the 27-wide template is centred on the stand with 9 layers underground and its pedestal
	 * 8 below the surface on the far side; empty rings fall back; steep or crowded rules find nothing.
	 * The owner stands at the arena's west edge so that the box, wider than the arena, stays clear of
	 * the neighbouring tests.
	 */
	@GameTest(structure = ARENA, maxTicks = 20, skyAccess = true)
	public void echoShrinePlacementWithTestRings(GameTestHelper helper) {
		openGround(helper);
		ServerLevel level = helper.getLevel();
		int recordsBefore = WorldSites.all(level.getServer()).size();
		ServerPlayer owner = M3TestPlayers.listed(helper, new BlockPos(2, 1, 12));

		try {
			Vec3 feet = owner.position();
			// Look east (+X): yaw -90.
			owner.snapTo(feet.x, feet.y, feet.z, -90f, 0f);
			MeetingSites.Rules rules = new MeetingSites.Rules(EchoShrines.FOOTPRINT, 15, EchoShrines.MAX_SLOPE, p -> false, List.of(), 0);
			int[] ring = {10};

			RuntimeSites.Spot spot = EchoShrines.findSpot(owner, HolocronKind.SITH, ring, new int[0], rules);
			helper.assertTrue(spot != null, msg("open grass 10 blocks east must take a shrine"));
			helper.assertTrue(spot.stand().equals(helper.absolutePos(new BlockPos(12, 1, 12))),
					msg("the first candidate lies 10 blocks along the look; got " + local(helper, spot.stand())));
			helper.assertTrue(spot.rotation() == Rotation.COUNTERCLOCKWISE_90, msg("the entrance (local north) turns west, to the owner; got " + spot.rotation()));
			helper.assertTrue(spot.box().minX() + spot.box().maxX() == 2 * spot.stand().getX() && spot.box().minZ() + spot.box().maxZ() == 2 * spot.stand().getZ()
					&& spot.box().getXSpan() == 27 && spot.box().getZSpan() == 27, msg("the shrine is centred on the stand: " + spot.box()));
			helper.assertTrue(spot.box().minY() == spot.stand().getY() - EchoShrines.BURIED, msg("9 layers lie underground"));

			Identifier template = EchoShrines.template(HolocronKind.SITH);
			helper.assertTrue(template.equals(OldRepublicMod.id("site/echo_shrine_sith")), msg("the Sith shrine template"));
			SitePiece piece = new SitePiece(SiteKind.ECHO_SHRINE, template, spot.box(), spot.rotation(), spot.templateOrigin(),
					SitePiece.runtimeKey(level.dimension(), spot.templateOrigin()));
			BlockPos pedestal = SiteAnchors.find(piece, SiteAnchors.Anchor.PEDESTAL);
			helper.assertTrue(pedestal != null && spot.box().isInside(pedestal) && pedestal.getY() == spot.stand().getY() - 8 && pedestal.getX() > spot.stand().getX(),
					msg("the pedestal lies 8 below the surface, beyond the entrance; got " + pedestal));

			RuntimeSites.Spot fallback = EchoShrines.findSpot(owner, HolocronKind.SITH, new int[0], ring, rules);
			helper.assertTrue(spot.equals(fallback), msg("with no rings the fallback rings are searched"));

			MeetingSites.Rules steep = new MeetingSites.Rules(EchoShrines.FOOTPRINT, 15, -1, p -> false, List.of(), 0);
			helper.assertTrue(EchoShrines.findSpot(owner, HolocronKind.SITH, ring, ring, steep) == null, msg("when every candidate fails the search returns null"));
			MeetingSites.Rules crowded = new MeetingSites.Rules(EchoShrines.FOOTPRINT, 15, EchoShrines.MAX_SLOPE, p -> false, List.of(owner.blockPosition()), 32);
			helper.assertTrue(EchoShrines.findSpot(owner, HolocronKind.SITH, ring, ring, crowded) == null, msg("another player's site within the separation blocks it"));

			helper.assertTrue(WorldSites.all(level.getServer()).size() == recordsBefore, msg("the search records nothing"));
			helper.assertTrue(level.getBlockState(spot.stand().below()).is(Blocks.GRASS_BLOCK), msg("the search stamps nothing"));
		} finally {
			M3TestPlayers.remove(owner);
		}

		helper.succeed();
	}

	// ---------------------------------------------------------------- block entity round trips (called by blockEntityNbtRoundTrips)

	/** A save and load of {@code entity} through its full metadata, as chunk saving does. */
	private static BlockEntity reload(ServerLevel level, BlockEntity entity) {
		return BlockEntity.loadStatic(entity.getBlockPos(), entity.getBlockState(), entity.saveWithFullMetadata(level.registryAccess()), level.registryAccess());
	}

	/**
	 * A bounty terminal keeps {@code officer}, {@code garrison} (empty slots included), {@code refill_at}
	 * and {@code failed}; a terminal that never spawned reloads as never filled.
	 */
	private static void roundTripTerminal(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos local = new BlockPos(15, 1, 3);
		helper.setBlock(local, SiteBlocks.BOUNTY_TERMINAL.defaultBlockState().setValue(SiteStates.FACTION, SiteStates.Army.IMPERIAL)
				.setValue(SiteStates.FACING, Direction.EAST));
		BountyTerminalBlockEntity terminal = helper.getBlockEntity(local, BountyTerminalBlockEntity.class);
		helper.assertTrue(reload(level, terminal) instanceof BountyTerminalBlockEntity fresh && fresh.state().equals(GarrisonRules.State.empty()),
				msg("a terminal that never spawned reloads as never filled"));

		GarrisonRules.State state = garrisonState(Arrays.asList(new UUID(0x0FF1CE5L, 1L), new UUID(0x0FF1CE5L, 2L), null, new UUID(0x0FF1CE5L, 4L), null),
				new int[] {0, 1, 0, 2, 0}, 123_456L);
		terminal.restore(state);
		CompoundTag saved = terminal.saveWithFullMetadata(level.registryAccess());
		helper.assertTrue(saved.getIntArray("officer").map(ints -> ints.length == 4).orElse(false) && saved.getIntArray("failed").map(ints -> ints.length == 5).orElse(false)
				&& saved.getLongOr("refill_at", 0L) == 123_456L, msg("the terminal writes officer int[4], failed int[5] and refill_at"));
		helper.assertTrue(reload(level, terminal) instanceof BountyTerminalBlockEntity copy && copy.state().equals(state),
				msg("the terminal keeps its officer, garrison (empty slots too), refill time and missed checks"));
		helper.setBlock(local, Blocks.AIR);
	}

	/** An echo cairn keeps {@code owner}, {@code index} and {@code revealed_until}. */
	private static void roundTripCairn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos local = new BlockPos(18, 1, 3);
		helper.setBlock(local, SiteBlocks.ECHO_CAIRN.defaultBlockState().setValue(SiteStates.STYLE, SiteStates.CairnStyle.CACHE));
		EchoCairnBlockEntity cairn = helper.getBlockEntity(local, EchoCairnBlockEntity.class);
		UUID owner = new UUID(0xCA1A5L, 3L);
		cairn.setOwner(owner, 2);
		helper.assertTrue(cairn.reveal(level, owner, 600), msg("the owner reveals the cairn"));
		long until = cairn.revealedUntil();
		helper.assertTrue(reload(level, cairn) instanceof EchoCairnBlockEntity copy && owner.equals(copy.owner()) && copy.index() == 2
				&& copy.revealedUntil() == until, msg("the cairn keeps its owner, index and reveal time"));
		helper.setBlock(local, Blocks.AIR);
	}

	/**
	 * A site spawner keeps {@code group}, {@code count}, {@code bound}, {@code replace_with} (with its
	 * properties) and {@code done}; a tag written as the structure generator writes it (no
	 * {@code done}) loads as not yet spawned.
	 */
	private static void roundTripSpawner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos local = new BlockPos(21, 1, 3);
		helper.setBlock(local, SiteBlocks.SITE_SPAWNER);
		SiteSpawnerBlockEntity spawner = helper.getBlockEntity(local, SiteSpawnerBlockEntity.class);
		BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true);
		spawner.configure(SpawnerGroups.SALVAGE_CREW, 3, true, lantern);
		spawner.markDone();
		UUID owner = new UUID(0x5EEDL, 0x5A11L);
		spawner.setOwner(owner);
		helper.assertTrue(reload(level, spawner) instanceof SiteSpawnerBlockEntity copy && copy.group().equals(SpawnerGroups.SALVAGE_CREW) && copy.count() == 3
				&& copy.bound() && copy.replaceWith().equals(lantern) && copy.done() && owner.equals(copy.owner()),
				msg("the spawner keeps group, count, bound, replace_with, done and owner"));

		CompoundTag generated = new CompoundTag();
		generated.putString("id", "oldrepublic:site_spawner");
		generated.putString("group", SpawnerGroups.DIGGERS);
		generated.putInt("count", 0);
		generated.putByte("bound", (byte) 0);
		CompoundTag air = new CompoundTag();
		air.putString("Name", "minecraft:air");
		// API-CHECK: CompoundTag#put / putString / putInt / putByte (long-standing; not in the dumps)
		generated.put("replace_with", air);
		BlockEntity fromGenerator = BlockEntity.loadStatic(spawner.getBlockPos(), spawner.getBlockState(), generated, level.registryAccess());
		helper.assertTrue(fromGenerator instanceof SiteSpawnerBlockEntity loaded && loaded.group().equals(SpawnerGroups.DIGGERS) && loaded.count() == 0
				&& !loaded.bound() && loaded.replaceWith().isAir() && !loaded.done() && loaded.owner() == null, msg("a generator-written spawner loads unspawned and unowned"));
		helper.setBlock(local, Blocks.AIR);
	}
}
