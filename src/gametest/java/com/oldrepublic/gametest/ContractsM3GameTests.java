package com.oldrepublic.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.ability.AbilityLocks;
import com.oldrepublic.combat.Attitude;
import com.oldrepublic.combat.Attitudes;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.combat.DangerTellTable;
import com.oldrepublic.combat.Holds;
import com.oldrepublic.enemy.BlasterBolt;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.lightning.ForceLightning;
import com.oldrepublic.progression.ForceModifier;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.progression.ProgressionBalance;
import com.oldrepublic.quest.story.MeetingSites;
import com.oldrepublic.world.outpost.Outposts;
import com.oldrepublic.world.site.LocatorQueue;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteBalance;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3 Phase 0 contracts (spec 17.1), green before the workers start: attitudes, damage and
 * Force modifiers, holds and ability locks, the Danger Sense tell table, site anchors under every
 * rotation, the runtime site search and terrain adaptation, the locator queue (with its own fake
 * lookup, never the server's queue), detached {@code WorldSites} values, the outpost decision and
 * the bolt styles.
 *
 * <p>Test rules (15.1): every test declares {@code maxTicks}; fixtures are the small
 * {@code oldrepublic_test} ones; no test writes {@code WorldSites} or the server's locator queue.
 * The damage and Force modifier tests register one inert modifier each, once: it answers only for
 * entities carrying a test tag or for a test ability id, so it changes nothing else.
 */
public class ContractsM3GameTests {
	private static final String ARENA = "oldrepublic_test:arena_24x7x24";
	private static final Identifier ANCHOR_FIXTURE = Identifier.fromNamespaceAndPath("oldrepublic_test", "anchor_fixture");

	private static final String TAG_DAMAGE_X10 = "oldrepublic_test.damage_x10";
	private static final String TAG_DAMAGE_X0_1 = "oldrepublic_test.damage_x0_1";
	private static final String TAG_DAMAGE_X1_5 = "oldrepublic_test.damage_x1_5";
	private static final String TAG_REGEN_LOW = "oldrepublic_test.regen_low";
	private static final String TAG_REGEN_HIGH = "oldrepublic_test.regen_high";
	private static final String ABILITY_CHEAP = "oldrepublic_test:cheap";
	private static final String ABILITY_DEAR = "oldrepublic_test:dear";
	private static final String ABILITY_PLAIN = "oldrepublic_test:plain";

	private static boolean testModifiersRegistered;

	private static Component msg(String text) {
		return Component.literal(text);
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0e-4;
	}

	/** The inert test modifiers (see the class comment), registered on first use. */
	private static void registerTestModifiers() {
		if (testModifiersRegistered) {
			return;
		}

		testModifiersRegistered = true;
		DamageModifiers.register((victim, source, amount) -> {
			if (victim.entityTags().contains(TAG_DAMAGE_X10)) {
				return amount * 10f;
			} else if (victim.entityTags().contains(TAG_DAMAGE_X0_1)) {
				return amount * 0.1f;
			} else if (victim.entityTags().contains(TAG_DAMAGE_X1_5)) {
				return amount * 1.5f;
			}

			return amount;
		});
		ForceModifiers.register(new ForceModifier() {
			@Override
			public float costMultiplier(Player p, String abilityId) {
				return ABILITY_CHEAP.equals(abilityId) ? 0.1f : ABILITY_DEAR.equals(abilityId) ? 10f : 1f;
			}

			@Override
			public float regenMultiplier(Player p) {
				return p.entityTags().contains(TAG_REGEN_LOW) ? 0.1f : p.entityTags().contains(TAG_REGEN_HIGH) ? 10f : 1f;
			}
		});
	}

	// ---------------------------------------------------------------- combat contracts

	@GameTest(maxTicks = 20)
	public void attitudeFallbackEnemyHostileElseNeutral(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
			LivingEntity villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(4, 1, 2));
			helper.assertTrue(zombie instanceof Enemy && !(villager instanceof Enemy), msg("the M2 rule: a zombie is an Enemy, a villager is not"));
			helper.assertTrue(Attitudes.toward(zombie, player) == Attitude.HOSTILE, msg("no rule claims a zombie: the fallback is HOSTILE"));
			helper.assertTrue(Attitudes.toward(villager, player) == Attitude.NEUTRAL, msg("no rule claims a villager: the fallback is NEUTRAL"));
			helper.assertTrue(Attitudes.countsAsHostile(player, zombie), msg("killing a zombie counts as hostile, as in M2"));
			helper.assertTrue(!Attitudes.countsAsHostile(player, villager), msg("killing a villager does not count as hostile, as in M2"));
			helper.assertTrue(!Attitudes.shieldedFromArea(player, villager) && !Attitudes.shieldedFromArea(player, zombie),
					msg("entities no rule covers are never shielded from area powers"));
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void damageModifiersClampQuarterToDouble(GameTestHelper helper) {
		registerTestModifiers();
		ServerLevel level = helper.getLevel();
		LivingEntity zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 1, 2));
		ProgressionBalance b = Progression.balance();
		float plain = DamageModifiers.apply(zombie, level.damageSources().generic(), 4f);
		helper.assertTrue(near(plain, 4f), msg("an untagged victim takes the damage unchanged, got " + plain));

		zombie.addTag(TAG_DAMAGE_X10);
		float high = DamageModifiers.apply(zombie, level.damageSources().generic(), 4f);
		helper.assertTrue(near(high, 4f * b.damageModifierMax), msg("x10 is clamped to x2, got " + high));

		zombie.removeTag(TAG_DAMAGE_X10);
		zombie.addTag(TAG_DAMAGE_X0_1);
		float low = DamageModifiers.apply(zombie, level.damageSources().generic(), 4f);
		helper.assertTrue(near(low, 4f * b.damageModifierMin), msg("x0.1 is clamped to x0.25, got " + low));

		zombie.removeTag(TAG_DAMAGE_X0_1);
		zombie.addTag(TAG_DAMAGE_X1_5);
		float inside = DamageModifiers.apply(zombie, level.damageSources().generic(), 4f);
		helper.assertTrue(near(inside, 6f), msg("x1.5 lies inside the range and applies as is, got " + inside));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void forceModifiersClampCostAndRegen(GameTestHelper helper) {
		registerTestModifiers();
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			ProgressionBalance b = Progression.balance();
			helper.assertTrue(near(ForceModifiers.cost(player, ABILITY_PLAIN), 1f), msg("a neutral ability costs x1"));
			helper.assertTrue(near(ForceModifiers.cost(player, ABILITY_CHEAP), b.modifierCostMin), msg("x0.1 cost is clamped to modifierCostMin"));
			helper.assertTrue(near(ForceModifiers.cost(player, ABILITY_DEAR), b.modifierCostMax), msg("x10 cost is clamped to modifierCostMax"));
			helper.assertTrue(near(ForceModifiers.regen(player), 1f), msg("an untagged player regenerates at x1"));

			player.addTag(TAG_REGEN_LOW);
			helper.assertTrue(near(ForceModifiers.regen(player), b.modifierRegenMin), msg("x0.1 regen is clamped to modifierRegenMin"));
			player.removeTag(TAG_REGEN_LOW);
			player.addTag(TAG_REGEN_HIGH);
			helper.assertTrue(near(ForceModifiers.regen(player), b.modifierRegenMax), msg("x10 regen is clamped to modifierRegenMax"));
			player.removeTag(TAG_REGEN_HIGH);
		} finally {
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void abilityLocksRefuseWhileHeld(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper);

		try {
			helper.assertTrue(AbilityLocks.refusal(player, ForceLightning.ID) == null, msg("a free player may cast"));

			Holds.hold(player, 100, "test", player.position(), true);
			helper.assertTrue(AbilityLocks.refusal(player, ForceLightning.ID) != null, msg("a held player may not cast Lightning"));
			helper.assertTrue(AbilityLocks.refusal(player, ForcePush.ABILITY_ID) == null, msg("Push stays allowed when the hold allows it"));

			Holds.hold(player, 100, "test", player.position(), false);
			helper.assertTrue(AbilityLocks.refusal(player, ForcePush.ABILITY_ID) != null, msg("Push is refused when the hold forbids it"));

			Holds.release(player, "release");
			helper.assertTrue(AbilityLocks.refusal(player, ForceLightning.ID) == null && AbilityLocks.refusal(player, ForcePush.ABILITY_ID) == null,
					msg("after the release every power is allowed again"));
		} finally {
			Holds.release(player, "release");
			M3TestPlayers.remove(player);
		}

		helper.succeed();
	}

	@GameTest(maxTicks = 40)
	public void holdsReleaseOnEveryPathRemovesGravityModifier(GameTestHelper helper) {
		ServerPlayer player = M3TestPlayers.listed(helper);
		double free = player.getAttributeValue(Attributes.GRAVITY);

		// Path 1: an explicit release.
		Holds.hold(player, 200, "test", player.position(), true);
		helper.assertTrue(Holds.isHeld(player) && heldGravity(player), msg("a hold adds the gravity modifier"));
		helper.assertTrue(near(player.getAttributeValue(Attributes.GRAVITY), 0), msg("a held player has no gravity"));
		Holds.release(player, "release");
		assertFree(helper, player, free, "release");

		// Path 2: the hold runs out (the level tick sees listed players only).
		Holds.hold(player, 3, "test", player.position(), true);
		helper.runAfterDelay(8, () -> {
			assertFree(helper, player, free, "expired");

			// Path 3: logging out.
			Holds.hold(player, 200, "test", player.position(), true);
			helper.assertTrue(heldGravity(player), msg("held again before the logout"));
			M3TestPlayers.remove(player);
			assertFree(helper, player, free, "logout");
			helper.succeed();
		});
	}

	private static boolean heldGravity(ServerPlayer player) {
		AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
		return gravity != null && gravity.hasModifier(Holds.GRAVITY_MODIFIER);
	}

	private static void assertFree(GameTestHelper helper, ServerPlayer player, double free, String path) {
		helper.assertTrue(!Holds.isHeld(player), msg(path + ": the hold is gone"));
		helper.assertTrue(!heldGravity(player), msg(path + ": the gravity modifier is gone"));
		helper.assertTrue(near(player.getAttributeValue(Attributes.GRAVITY), free), msg(path + ": gravity is back to " + free));
	}

	@GameTest(maxTicks = 20)
	public void dangerTellTableMatches13_3(GameTestHelper helper) {
		String[] firstVariantOnly = {"npc_windup", "captain_heavy", "acolyte_shove", "tomb_vent", "sentinel_slam", "sentinel_lance",
				"boss_hurl", "boss_palm", "boss_lash", "boss_surge", "boss_grip", "boss_lunge"};
		String[] anyVariant = {"trooper_charge", "grenade_tell", "grenade_fuse"};

		for (String id : firstVariantOnly) {
			helper.assertTrue(DangerTellTable.isTell(OldRepublicMod.id(id), 0), msg(id + " variant 0 is a tell"));
			helper.assertTrue(!DangerTellTable.isTell(OldRepublicMod.id(id), 1), msg(id + " variant 1 is not a tell"));
		}

		for (String id : anyVariant) {
			for (int variant = 0; variant < 4; variant++) {
				helper.assertTrue(DangerTellTable.isTell(OldRepublicMod.id(id), variant), msg(id + " variant " + variant + " is a tell"));
			}
		}

		for (String id : new String[] {"veil_reveal", "urn_wrong", "pedestal_ready", "force_sense", "lift"}) {
			helper.assertTrue(!DangerTellTable.isTell(OldRepublicMod.id(id), 0), msg(id + " is not a tell"));
		}

		helper.assertTrue(!DangerTellTable.isTell(Identifier.fromNamespaceAndPath("minecraft", "npc_windup"), 0), msg("only oldrepublic ids are tells"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- site anchors and runtime sites

	/** The anchor fixture's marker block under each anchor (see gen_test_fixtures_m3.py). */
	private static final Map<SiteAnchors.Anchor, Block> MARKERS = Map.of(
			SiteAnchors.Anchor.HEART, Blocks.GOLD_BLOCK,
			SiteAnchors.Anchor.PEDESTAL, Blocks.DIAMOND_BLOCK,
			SiteAnchors.Anchor.GUARD, Blocks.EMERALD_BLOCK,
			SiteAnchors.Anchor.CHEST_1, Blocks.LAPIS_BLOCK);

	/**
	 * Stamps the anchor fixture into the arena under all four rotations (90 included) and checks that
	 * every sidecar anchor, mapped by {@link SiteAnchors#toWorld}, lands on its marker block.
	 */
	@GameTest(structure = ARENA, maxTicks = 20, skyAccess = true)
	public void siteAnchorsRotated90(GameTestHelper helper) {
		helper.assertTrue(SiteAnchors.of(ANCHOR_FIXTURE, SiteAnchors.Anchor.HEART).equals(List.of(new BlockPos(1, 1, 5))),
				msg("the anchor fixture's sidecar must load: HEART at (1, 1, 5), got " + SiteAnchors.of(ANCHOR_FIXTURE, SiteAnchors.Anchor.HEART)));
		helper.assertTrue(SiteAnchors.of(ANCHOR_FIXTURE, SiteAnchors.Anchor.CHEST_1).size() == 2, msg("CHEST_1 has two cells, in sidecar order"));

		// Non-overlapping origins inside the 24 x 24 arena for the 5 x 3 x 7 fixture under each rotation.
		Map<Rotation, BlockPos> origins = Map.of(
				Rotation.NONE, new BlockPos(1, 1, 1),
				Rotation.CLOCKWISE_90, new BlockPos(20, 1, 1),
				Rotation.CLOCKWISE_180, new BlockPos(8, 1, 22),
				Rotation.COUNTERCLOCKWISE_90, new BlockPos(14, 1, 22));

		for (Map.Entry<Rotation, BlockPos> entry : origins.entrySet()) {
			Rotation rotation = entry.getKey();
			BlockPos origin = helper.absolutePos(entry.getValue());
			SitePiece piece = RuntimeSites.stamp(helper.getLevel(), SiteKind.JEDI_RUIN, ANCHOR_FIXTURE, origin, rotation);
			helper.assertTrue(piece != null, msg("the anchor fixture must place under " + rotation));

			for (Map.Entry<SiteAnchors.Anchor, Block> marker : MARKERS.entrySet()) {
				for (BlockPos pos : SiteAnchors.all(piece, marker.getKey())) {
					helper.assertTrue(piece.box().isInside(pos), msg(marker.getKey() + " under " + rotation + " lies inside the piece"));
					helper.assertTrue(helper.getLevel().getBlockState(pos).is(marker.getValue()),
							msg(marker.getKey() + " under " + rotation + " must be on its marker at " + helper.relativePos(pos)));
				}
			}

			if (rotation == Rotation.CLOCKWISE_90) {
				// Clockwise 90 about the origin maps local (x, z) to (-z, x).
				helper.assertTrue(origin.offset(-5, 1, 1).equals(SiteAnchors.find(piece, SiteAnchors.Anchor.HEART)), msg("HEART (1, 1, 5) turned 90 is (-5, 1, 1)"));
			}
		}

		helper.succeed();
	}

	/** Lays grass over the whole arena floor and clears everything above it. */
	private static void openArena(GameTestHelper helper) {
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
	 * The surface search on injected rings: the first candidate lies along {@code forward}, the
	 * template turns its entrance (local north) toward the origin, is centred on the stand with its
	 * lowest layer in the ground, and an empty ring list falls back to the fallback rings. Uses the
	 * non-recording seams ({@code findSurface}, {@code stamp}): {@code placeAt} writes
	 * {@code WorldSites}, which no test may.
	 */
	@GameTest(structure = ARENA, maxTicks = 20, skyAccess = true)
	public void surfaceSpotOnInjectedRingsAndFallback(GameTestHelper helper) {
		openArena(helper);
		ServerLevel level = helper.getLevel();
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 1, 12)));
		Vec3 east = new Vec3(1, 0, 0);
		MeetingSites.Rules rules = new MeetingSites.Rules(5, 7, 2, pos -> false, List.of(), 0);

		RuntimeSites.Spot spot = RuntimeSites.findSurface(level, ANCHOR_FIXTURE, origin, east, new int[] {6}, new int[0], rules, 1);
		helper.assertTrue(spot != null, msg("open grass on ring 6 must pass"));
		helper.assertTrue(spot.stand().equals(helper.absolutePos(new BlockPos(18, 1, 12))), msg("the first candidate is 6 blocks east, got " + helper.relativePos(spot.stand())));
		helper.assertTrue(spot.rotation() == Rotation.COUNTERCLOCKWISE_90, msg("the entrance turns west, toward the origin; got " + spot.rotation()));
		helper.assertTrue(spot.box().minX() + spot.box().maxX() == 2 * spot.stand().getX() && spot.box().minZ() + spot.box().maxZ() == 2 * spot.stand().getZ(),
				msg("the template is centred on the stand: " + spot.box()));
		helper.assertTrue(spot.box().minY() == spot.stand().getY() - 1, msg("buried 1: the lowest layer replaces the ground"));

		RuntimeSites.Spot fallback = RuntimeSites.findSurface(level, ANCHOR_FIXTURE, origin, east, new int[0], new int[] {6}, rules, 1);
		helper.assertTrue(spot.equals(fallback), msg("with no rings the fallback rings are searched"));

		MeetingSites.Rules steep = new MeetingSites.Rules(5, 7, -1, pos -> false, List.of(), 0);
		helper.assertTrue(RuntimeSites.findSurface(level, ANCHOR_FIXTURE, origin, east, new int[] {6}, new int[] {4}, steep, 1) == null,
				msg("when every candidate fails the search returns null (the caller retries)"));

		SitePiece piece = RuntimeSites.stamp(level, SiteKind.JEDI_RUIN, ANCHOR_FIXTURE, spot.templateOrigin(), spot.rotation());
		helper.assertTrue(piece != null && piece.box().equals(spot.box()), msg("the stamped piece has the spot's box"));
		BlockPos heart = SiteAnchors.find(piece, SiteAnchors.Anchor.HEART);
		helper.assertTrue(heart != null && level.getBlockState(heart).is(Blocks.GOLD_BLOCK), msg("the stamped fixture's HEART is on its marker"));
		helper.succeed();
	}

	@GameTest(structure = ARENA, maxTicks = 20, skyAccess = true)
	public void adaptationNeverFillsBesideBuiltBlocks(GameTestHelper helper) {
		// Ground: dirt at y = 1, grass at y = 2, over the arena floor.
		for (int x = 2; x <= 11; x++) {
			for (int z = 2; z <= 11; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.GRASS_BLOCK);

				for (int y = 3; y < 7; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}

		// A two-deep hole beside a player's planks, and one in open ground; a tuft of grass above.
		helper.setBlock(new BlockPos(4, 2, 5), Blocks.OAK_PLANKS);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.AIR);
		helper.setBlock(new BlockPos(5, 1, 5), Blocks.AIR);
		helper.setBlock(new BlockPos(8, 2, 8), Blocks.AIR);
		helper.setBlock(new BlockPos(8, 1, 8), Blocks.AIR);
		helper.setBlock(new BlockPos(9, 3, 9), Blocks.SHORT_GRASS);

		BlockPos min = helper.absolutePos(new BlockPos(3, 2, 3));
		BlockPos max = helper.absolutePos(new BlockPos(10, 4, 10));
		RuntimeSites.adaptTerrain(helper.getLevel(), BoundingBox.fromCorners(min, max), min.getY());

		helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(4, 2, 5));
		helper.assertTrue(helper.getBlockState(new BlockPos(5, 2, 5)).isAir() && helper.getBlockState(new BlockPos(5, 1, 5)).isAir(),
				msg("the hole beside the planks is never filled"));
		helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(8, 2, 8));
		helper.assertBlockPresent(Blocks.DIRT, new BlockPos(8, 1, 8));
		helper.assertTrue(helper.getBlockState(new BlockPos(9, 3, 9)).isAir(), msg("natural clutter above the footprint is cleared"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the locator queue (fake lookup)

	/** A fake lookup: a fixed plan, fixed pieces per cell, and a log of the cells checked. */
	private static final class FakeLookup implements LocatorQueue.Lookup {
		private final List<LocatorQueue.Cell> plan;
		private final Map<LocatorQueue.Cell, List<SitePiece>> pieces = new HashMap<>();
		private final List<LocatorQueue.Cell> checked = new ArrayList<>();

		FakeLookup(List<LocatorQueue.Cell> plan) {
			this.plan = plan;
		}

		FakeLookup with(int x, int z, int ring, SitePiece... found) {
			pieces.put(new LocatorQueue.Cell(x, z, ring), List.of(found));
			return this;
		}

		@Override
		public List<LocatorQueue.Cell> plan(LocatorQueue.Search search) {
			return plan;
		}

		@Override
		public List<SitePiece> check(LocatorQueue.Search search, LocatorQueue.Cell cell) {
			checked.add(cell);
			return pieces.getOrDefault(cell, List.of());
		}
	}

	/** A callback that remembers whether and with what it ran. */
	private static final class Result {
		boolean done;
		@Nullable SitePiece piece;

		void accept(@Nullable SitePiece found) {
			done = true;
			piece = found;
		}
	}

	private static final BlockPos FROM = new BlockPos(0, 64, 0);

	/** A Jedi ruin start piece whose box is centred on ({@code x}, {@code z}). */
	private static SitePiece ruinAt(int x, int z, String template) {
		BoundingBox box = new BoundingBox(x - 2, 60, z - 2, x + 2, 64, z + 2);
		return new SitePiece(SiteKind.JEDI_RUIN, OldRepublicMod.id(template), box, Rotation.NONE, new BlockPos(x - 2, 60, z - 2),
				SitePiece.worldgenKey(Level.OVERWORLD, new ChunkPos(x >> 4, z >> 4)));
	}

	private static LocatorQueue.Search search(GameTestHelper helper, UUID player, int radius) {
		return new LocatorQueue.Search(helper.getLevel(), player, SiteKind.JEDI_RUIN, FROM, radius, null);
	}

	@GameTest(maxTicks = 20)
	public void locatorQueueChecksFourCellsPerTick(GameTestHelper helper) {
		FakeLookup lookup = new FakeLookup(LocatorQueue.ringCells(0, 0, 3));
		LocatorQueue queue = new LocatorQueue(lookup, 4);
		Result first = new Result();
		Result second = new Result();
		queue.submit(search(helper, UUID.randomUUID(), 768), 0, first::accept);
		queue.submit(search(helper, UUID.randomUUID(), 768), 0, second::accept);
		helper.assertTrue(lookup.checked.isEmpty() && !first.done, msg("submitting checks nothing"));

		int ticks = 0;

		while (!queue.idle() && ticks < 100) {
			ticks++;
			int checked = queue.tick(ticks);
			helper.assertTrue(checked <= 4, msg("tick " + ticks + " checked " + checked + " cells; at most 4 across all searches"));
		}

		helper.assertTrue(lookup.checked.size() == 50, msg("two searches of 25 cells check 50 cells, got " + lookup.checked.size()));
		helper.assertTrue(ticks == 13, msg("50 cells at 4 per tick take 13 ticks, took " + ticks));
		helper.assertTrue(first.done && second.done && first.piece == null && second.piece == null, msg("both searches end with null"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void locatorNotFoundRadius768FinishesWithinBudget(GameTestHelper helper) {
		int rings = LocatorQueue.rings(768, 32, 4);
		helper.assertTrue(rings == 3, msg("768 blocks at spacing 32 is ceil(1.5) + 1 = 3 rings, got " + rings));
		helper.assertTrue(LocatorQueue.rings(100_000, 32, 4) == 4, msg("rings are capped at 4"));
		helper.assertTrue(LocatorQueue.ringCells(0, 0, rings).size() == 25, msg("3 rings are 1 + 8 + 16 cells"));

		FakeLookup lookup = new FakeLookup(LocatorQueue.ringCells(0, 0, rings));
		LocatorQueue queue = new LocatorQueue(lookup, 4);
		Result result = new Result();
		queue.submit(search(helper, UUID.randomUUID(), 768), 0, result::accept);
		int ticks = 0;

		while (!result.done && ticks < 100) {
			ticks++;
			helper.assertTrue(queue.tick(ticks) <= 4, msg("never more than 4 cells per tick"));
		}

		helper.assertTrue(result.done && result.piece == null, msg("a search that finds nothing calls back with null"));
		helper.assertTrue(ticks == 7, msg("25 cells at 4 per tick finish on tick 7, took " + ticks));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void locatorDropsResultsBeyondRadius(GameTestHelper helper) {
		SitePiece far = ruinAt(300, 0, "jedi_ruin/courtyard");
		SitePiece kept = ruinAt(200, 0, "jedi_ruin/courtyard");
		SitePiece nearer = ruinAt(0, 180, "jedi_ruin/courtyard");
		SitePiece taken = ruinAt(-100, 0, "jedi_ruin/taken");
		SitePiece outerRing = ruinAt(50, 0, "jedi_ruin/courtyard");
		FakeLookup lookup = new FakeLookup(LocatorQueue.ringCells(0, 0, 3))
				.with(0, 0, 0, far)
				.with(1, 0, 1, kept)
				.with(0, 1, 1, nearer)
				.with(-1, 0, 1, taken)
				.with(2, 0, 2, outerRing);
		LocatorQueue queue = new LocatorQueue(lookup, 4);
		Result result = new Result();
		UUID player = UUID.randomUUID();
		LocatorQueue.Search filtered = new LocatorQueue.Search(helper.getLevel(), player, SiteKind.JEDI_RUIN, FROM, 256,
				piece -> piece.template().getPath().equals("jedi_ruin/taken"));
		queue.submit(filtered, 0, result::accept);
		long finished = 0;

		while (!result.done && finished < 10) {
			finished++;
			queue.tick(finished);
		}

		helper.assertTrue(result.done, msg("the search must finish"));
		helper.assertTrue(nearer.equals(result.piece), msg("the nearest kept result wins: the 300-block start is beyond 256, the taken one is excluded; got " + result.piece));
		helper.assertTrue(lookup.checked.stream().noneMatch(cell -> cell.ring() == 2), msg("once ring 1 holds a result, ring 2 is not checked"));

		// The cache: fresh for the same player, kind and filter; stale after 600 ticks or a 48-block move.
		ResourceKey<Level> dimension = helper.getLevel().dimension();
		long now = finished + 2;
		Result again = new Result();
		queue.submit(filtered, now, again::accept);
		helper.assertTrue(again.done && nearer.equals(again.piece), msg("a fresh cached result is handed back at once"));
		helper.assertTrue(nearer.equals(queue.cached(player, SiteKind.JEDI_RUIN, dimension, FROM, now)), msg("cached() reads it synchronously"));
		helper.assertTrue(queue.cached(player, SiteKind.JEDI_RUIN, dimension, FROM.offset(48, 0, 0), now) == null, msg("a 48-block move makes it stale"));
		helper.assertTrue(queue.cached(player, SiteKind.JEDI_RUIN, dimension, FROM, finished + LocatorQueue.DEFAULT_CACHE_TICKS) == null, msg("600 ticks make it stale"));
		helper.assertTrue(queue.cached(UUID.randomUUID(), SiteKind.JEDI_RUIN, dimension, FROM, now) == null, msg("the cache is per player"));
		helper.succeed();
	}

	// ---------------------------------------------------------------- WorldSites.Data and outposts (detached values)

	private static WorldSites.SiteRecord record(SiteKind kind, ResourceKey<Level> dimension, BlockPos origin, String template) {
		return new WorldSites.SiteRecord(kind, dimension, origin, Rotation.CLOCKWISE_90, OldRepublicMod.id(template), UUID.randomUUID(), 3L);
	}

	@GameTest(maxTicks = 20)
	public void worldSitesDataCap512AndNearest(GameTestHelper helper) {
		WorldSites.Data data = new WorldSites.Data();

		for (int i = 0; i < 4; i++) {
			data.add(record(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, new BlockPos(i * 1000, 64, 0), "outpost/republic_post"));
		}

		for (int i = 0; i < 600; i++) {
			data.add(record(SiteKind.MISSION_SITE, Level.OVERWORLD, new BlockPos(0, 64, 5000 + 10 * i), "mission/fence_camp"));
		}

		helper.assertTrue(data.all().size() == WorldSites.Data.MAX_RECORDS, msg("at most 512 records, got " + data.all().size()));
		helper.assertTrue(data.count(SiteKind.REPUBLIC_OUTPOST) == 4, msg("outposts are never dropped for the cap"));
		helper.assertTrue(data.count(SiteKind.MISSION_SITE) == 508, msg("the oldest mission sites are dropped"));
		helper.assertTrue(data.all().get(4).origin().getZ() == 5000 + 10 * 92, msg("the oldest kept mission site is the 93rd"));

		WorldSites.Data copy = data.copy();
		copy.add(record(SiteKind.ECHO_SHRINE, Level.OVERWORLD, new BlockPos(1, 64, 1), "site/echo_shrine_jedi"));
		helper.assertTrue(data.count(SiteKind.ECHO_SHRINE) == 0 && copy.count(SiteKind.ECHO_SHRINE) == 1, msg("a copy is independent"));

		WorldSites.Data near = new WorldSites.Data();
		near.add(record(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, new BlockPos(100, 64, 0), "outpost/republic_post"));
		near.add(record(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, new BlockPos(300, 64, 0), "outpost/republic_post"));
		near.add(record(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, new BlockPos(-50, 64, 0), "outpost/republic_post"));
		near.add(record(SiteKind.REPUBLIC_OUTPOST, Level.NETHER, new BlockPos(10, 64, 0), "outpost/republic_post"));
		BlockPos from = new BlockPos(0, 64, 0);
		WorldSites.SiteRecord nearest = near.nearest(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, from, 512);
		helper.assertTrue(nearest != null && nearest.origin().getX() == -50, msg("the nearest overworld outpost is at -50"));
		helper.assertTrue(near.nearest(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, from, 49) == null, msg("nothing within 49 blocks"));
		helper.assertTrue(near.nearest(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, from, 50) != null, msg("the radius is inclusive"));
		WorldSites.SiteRecord nether = near.nearest(SiteKind.REPUBLIC_OUTPOST, Level.NETHER, from, 512);
		helper.assertTrue(nether != null && nether.origin().getX() == 10, msg("nearest stays in its dimension"));
		helper.assertTrue(near.nearest(SiteKind.IMPERIAL_OUTPOST, Level.OVERWORLD, from, 512) == null, msg("nearest stays in its kind"));

		near.add(record(SiteKind.REPUBLIC_OUTPOST, Level.OVERWORLD, new BlockPos(100, 64, 0), "outpost/republic_post"));
		helper.assertTrue(near.all().size() == 4, msg("the same placement added twice is kept once"));

		List<UUID> owners = new ArrayList<>();

		for (int i = 0; i < 70; i++) {
			UUID owner = UUID.randomUUID();
			owners.add(owner);
			near.expireCairnOwner(owner);
		}

		helper.assertTrue(near.expiredCairnOwners().size() == WorldSites.Data.MAX_EXPIRED_OWNERS, msg("at most 64 expired cairn owners"));
		helper.assertTrue(!near.expiredCairnOwners().contains(owners.getFirst()) && near.expiredCairnOwners().contains(owners.getLast()),
				msg("the oldest expired owners are dropped first"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void worldSitesDataCodecRoundTrip(GameTestHelper helper) {
		WorldSites.Data data = new WorldSites.Data();
		data.add(record(SiteKind.ECHO_SHRINE, Level.OVERWORLD, new BlockPos(12, 70, -40), "site/echo_shrine_sith"));
		data.add(new WorldSites.SiteRecord(SiteKind.IMPERIAL_OUTPOST, Level.NETHER, new BlockPos(-3, 40, 9), Rotation.CLOCKWISE_180,
				OldRepublicMod.id("outpost/imperial_post"), null, 12L));
		UUID expired = UUID.randomUUID();
		data.expireCairnOwner(expired);

		JsonElement json = WorldSites.Data.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		WorldSites.Data back = WorldSites.Data.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.all().equals(data.all()), msg("records survive the codec: " + json));
		helper.assertTrue(back.expiredCairnOwners().equals(data.expiredCairnOwners()), msg("expired cairn owners survive the codec"));
		helper.succeed();
	}

	@GameTest(maxTicks = 20)
	public void outpostDecideEveryDecision(GameTestHelper helper) {
		SiteBalance b = new SiteBalance();
		BlockPos player = new BlockPos(0, 64, 0);

		helper.assertTrue(decide(b, player, new BlockPos(600, 64, 0), null, 0) == Outposts.Decision.WORLDGEN, msg("a worldgen outpost within 640 is used"));
		helper.assertTrue(decide(b, player, new BlockPos(640, 64, 0), new BlockPos(10, 64, 0), 3) == Outposts.Decision.WORLDGEN,
				msg("640 is inclusive, and worldgen wins over a nearer runtime outpost"));
		helper.assertTrue(decide(b, player, new BlockPos(700, 64, 0), new BlockPos(0, 64, 500), 3) == Outposts.Decision.REUSE,
				msg("else a runtime outpost within 512 is reused"));
		helper.assertTrue(decide(b, player, null, new BlockPos(0, 64, 600), 3) == Outposts.Decision.PLACE, msg("else a new one is placed"));
		helper.assertTrue(decide(b, player, null, null, 7) == Outposts.Decision.PLACE, msg("7 runtime outposts are below the cap"));
		helper.assertTrue(decide(b, player, null, new BlockPos(0, 64, 2000), 8) == Outposts.Decision.CAP_NEAREST, msg("at 8 the nearest is used"));

		SiteBalance off = new SiteBalance();
		off.runtimeOutposts = false;
		helper.assertTrue(decide(off, player, null, null, 0) == Outposts.Decision.CAP_NEAREST, msg("with runtime outposts off nothing is placed"));
		helper.succeed();
	}

	private static Outposts.Decision decide(SiteBalance b, BlockPos player, @Nullable BlockPos worldgen, @Nullable BlockPos runtime, int count) {
		return Outposts.decide(new Outposts.OutpostQuery(player, worldgen, runtime, count), b);
	}

	// ---------------------------------------------------------------- bolts

	@GameTest(maxTicks = 20)
	public void blasterBoltCreateEveryStyle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 muzzle = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		BlasterBolt plain = BlasterBolt.create(level, null, muzzle, 5f, 60);
		helper.assertTrue(plain.style() == BlasterBolt.Style.POACHER && !plain.heavy(), msg("the five-argument form makes a light poacher bolt"));

		for (BlasterBolt.Style style : BlasterBolt.Style.values()) {
			for (boolean heavy : new boolean[] {false, true}) {
				BlasterBolt bolt = BlasterBolt.create(level, null, muzzle, 5f, 60, style, heavy);
				helper.assertTrue(bolt.style() == style && bolt.heavy() == heavy, msg("create keeps style " + style + " and heavy " + heavy));
			}
		}

		helper.succeed();
	}
}
