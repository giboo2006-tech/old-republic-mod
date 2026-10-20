package com.oldrepublic.planet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.cantor.LongHymn;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveStage;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;
import com.oldrepublic.world.site.WorldSites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Hymnal's two sealed doors (spec MILESTONE_5.md 3.3.3, 6.7, 12.3), sheets of
 * {@code sealed_plating} at the halls' {@code HEART_DOOR} and {@code UNDERCROFT_DOOR} anchors:
 * <ul>
 * <li>The <b>Heart's door</b> opens for a player whose Chapter Five has reached
 * {@code THE_HYMNAL}.</li>
 * <li>The <b>undercroft door</b> opens by the Long Hymn's rule ({@link LongHymn#mayOpenUndercroft}:
 * a finished Chapter Five, the raid's participants once its join window closes, never on
 * Peaceful).</li>
 * <li>Either opens for a creative player, and always from its inner side (the Heart, the undercroft's
 * vestibule), so nobody is shut in.</li>
 * </ul>
 * A use on the sheet opens it whole: the plating is lifted out (stone grinding apart, grit falling)
 * and anyone with the player walks through. It seals again {@link #OPEN_TICKS} later, once nothing
 * stands in the doorway. Neither the fusion cutter nor a Force Push opens plating inside the Hymnal
 * ({@code SealedPlatingBlock}). Open doors live in memory: after a restart, or if anything else left
 * a doorway empty, the sweep reseals it when a player comes near.
 */
public final class HymnalDoors {
	/** The two doors. */
	public enum Door {
		HEART(SiteAnchors.Anchor.HEART_DOOR, Direction.SOUTH, PlanetEffects.DOOR_HEART, "message.oldrepublic.hymnal.heart_sealed"),
		UNDERCROFT(SiteAnchors.Anchor.UNDERCROFT_DOOR, Direction.EAST, PlanetEffects.DOOR_UNDERCROFT, "message.oldrepublic.hymnal.undercroft_sealed");

		final SiteAnchors.Anchor anchor;
		/** The door's inner side in the halls' own frame: the Heart lies south of its door, the vestibule east of its. */
		final Direction inner;
		final int variant;
		final String refusal;

		Door(SiteAnchors.Anchor anchor, Direction inner, int variant, String refusal) {
			this.anchor = anchor;
			this.inner = inner;
			this.variant = variant;
			this.refusal = refusal;
		}
	}

	/** A door found at a position: the halls piece, which door, and its plating cells (world). */
	public record Found(SitePiece piece, Door door, List<BlockPos> cells) {
	}

	/** An open door: its cells and when it may seal again. */
	private record Open(ResourceKey<Level> dimension, Door door, List<BlockPos> cells, long sealAt) {
	}

	/** Ticks an opened door stands open before it seals again (and longer while anyone stands in it). */
	public static final int OPEN_TICKS = 100;
	/** Open doors are checked this often. */
	private static final int CHECK_TICKS = 10;
	/** The reseal sweep over the recorded halls runs this often... */
	private static final int SWEEP_TICKS = 100;
	/** ...for halls with a player this close to their door. */
	private static final double SWEEP_RANGE = 64;
	private static final int REFUSE_COOLDOWN = 20;

	private static final Map<BlockPos, Open> OPEN = new HashMap<>();
	private static final Map<UUID, Long> LAST_REFUSAL = new HashMap<>();

	/**
	 * The rule (pure, for the tests): a door opens for a creative player, from its inner side, and
	 * when the story allows it ({@code allowed}: the Heart's stage, the Long Hymn's rule).
	 */
	public static boolean mayOpen(boolean allowed, boolean inside, boolean creative) {
		return creative || inside || allowed;
	}

	/** The Hymnal door whose plating includes {@code pos}, or null when {@code pos} is not one. */
	public static @Nullable Found doorAt(ServerLevel level, BlockPos pos) {
		SitePiece piece = StructureLocator.runtimePieceAt(level, pos, SiteKind.HYMNAL);

		if (piece == null) {
			return null;
		}

		for (Door door : Door.values()) {
			List<BlockPos> cells = SiteAnchors.all(piece, door.anchor);

			if (cells.contains(pos)) {
				return new Found(piece, door, cells);
			}
		}

		return null;
	}

	/** Whether {@code pos} lies in one of the Hymnal's pieces: its plating answers no cutter and no push. */
	public static boolean inHymnal(ServerLevel level, BlockPos pos) {
		return StructureLocator.runtimePieceAt(level, pos, SiteKind.HYMNAL) != null;
	}

	/** Whether {@code position} is on the door's inner side (the Heart, the vestibule). */
	public static boolean inside(Found found, Vec3 position) {
		Direction inner = found.piece().rotation().rotate(found.door().inner);
		Vec3 offset = position.subtract(centre(found.cells()));
		return offset.x * inner.getStepX() + offset.z * inner.getStepZ() > 0;
	}

	/** A use of the plating at {@code pos}: opens its door or says why not; passes for plating that is no Hymnal door. */
	public static InteractionResult use(ServerLevel level, BlockPos pos, ServerPlayer player) {
		Found found = doorAt(level, pos);

		if (found == null) {
			return InteractionResult.PASS;
		}

		boolean allowed = switch (found.door()) {
			case HEART -> ChapterFive.atLeast(player, ChapterFiveStage.THE_HYMNAL);
			case UNDERCROFT -> LongHymn.mayOpenUndercroft(player, pos);
		};

		if (!mayOpen(allowed, inside(found, player.position()), player.isCreative())) {
			refuse(player, found.door(), level.getGameTime());
		} else {
			open(level, found);
		}

		return InteractionResult.SUCCESS;
	}

	/** Lifts the door's plating out and starts its clock (a test seam too); false when it was open already. */
	public static boolean open(ServerLevel level, Found found) {
		int moved = 0;

		for (BlockPos cell : found.cells()) {
			if (level.getBlockState(cell).is(PlanetBlocks.SEALED_PLATING)) {
				level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				moved++;
			}
		}

		OPEN.put(found.cells().getFirst(), new Open(level.dimension(), found.door(), List.copyOf(found.cells()), level.getGameTime() + OPEN_TICKS));

		if (moved == 0) {
			return false;
		}

		EffectEvents.around(level, PlanetEffects.SITE_DOOR, centre(found.cells()), null, -1, moved, found.door().variant);
		return true;
	}

	/** Every {@link #CHECK_TICKS}: seals the doors that are due and empty; every {@link #SWEEP_TICKS}, the doorways a restart left open. */
	static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();

		if (now % CHECK_TICKS != 0) {
			return;
		}

		for (Iterator<Open> it = OPEN.values().iterator(); it.hasNext();) {
			Open open = it.next();
			ServerLevel level = server.getLevel(open.dimension());

			if (level == null || !loaded(level, open.cells())) {
				it.remove();
			} else if (level.getGameTime() >= open.sealAt() && !occupied(level, open.cells())) {
				seal(level, open.door(), open.cells());
				it.remove();
			}
		}

		if (now % SWEEP_TICKS == 0) {
			sweep(server);
		}
	}

	/** Forgets every open door and refusal clock (server stop). */
	static void clear() {
		OPEN.clear();
		LAST_REFUSAL.clear();
	}

	/** Forgets one player's refusal clock (logout). */
	static void forget(UUID player) {
		LAST_REFUSAL.remove(player);
	}

	/** Reseals any door of a recorded Hymnal halls piece that stands empty and untracked with a player near it. */
	private static void sweep(MinecraftServer server) {
		for (WorldSites.SiteRecord record : WorldSites.all(server)) {
			if (record.kind() != SiteKind.HYMNAL || !record.template().equals(PlanetSites.HYMNAL_HALLS.template())) {
				continue;
			}

			ServerLevel level = server.getLevel(record.dimension());
			SitePiece piece = level == null ? null : RuntimeSites.pieceOf(level, record);

			if (piece == null) {
				continue;
			}

			for (Door door : Door.values()) {
				List<BlockPos> cells = SiteAnchors.all(piece, door.anchor);

				if (!cells.isEmpty() && !OPEN.containsKey(cells.getFirst()) && loaded(level, cells) && playerNear(level, centre(cells))
						&& !occupied(level, cells)) {
					seal(level, door, cells);
				}
			}
		}
	}

	/** Puts the plating back into the empty cells of a doorway. */
	private static void seal(ServerLevel level, Door door, List<BlockPos> cells) {
		int moved = 0;

		for (BlockPos cell : cells) {
			if (level.getBlockState(cell).isAir()) {
				level.setBlock(cell, PlanetBlocks.SEALED_PLATING.defaultBlockState(), Block.UPDATE_ALL);
				moved++;
			}
		}

		if (moved > 0) {
			EffectEvents.around(level, PlanetEffects.SITE_DOOR, centre(cells), null, -1, -moved, door.variant);
		}
	}

	private static boolean occupied(ServerLevel level, List<BlockPos> cells) {
		for (BlockPos cell : cells) {
			if (!level.getEntities((Entity) null, new AABB(cell), Entity::isAlive).isEmpty()) {
				return true;
			}
		}

		return false;
	}

	private static boolean loaded(ServerLevel level, List<BlockPos> cells) {
		for (BlockPos cell : cells) {
			if (!level.hasChunkAt(cell.getX(), cell.getZ())) {
				return false;
			}
		}

		return true;
	}

	private static boolean playerNear(ServerLevel level, Vec3 centre) {
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(centre) <= SWEEP_RANGE * SWEEP_RANGE) {
				return true;
			}
		}

		return false;
	}

	private static Vec3 centre(List<BlockPos> cells) {
		Vec3 sum = Vec3.ZERO;

		for (BlockPos cell : cells) {
			sum = sum.add(Vec3.atCenterOf(cell));
		}

		return cells.isEmpty() ? sum : sum.scale(1.0 / cells.size());
	}

	/** The door's refusal on the overlay, at most once a second per player. */
	private static void refuse(ServerPlayer player, Door door, long now) {
		Long last = LAST_REFUSAL.get(player.getUUID());

		if (last != null && now >= last && now - last < REFUSE_COOLDOWN) {
			return;
		}

		LAST_REFUSAL.put(player.getUUID(), now);

		if (LAST_REFUSAL.size() > 256) {
			LAST_REFUSAL.entrySet().removeIf(e -> now - e.getValue() > REFUSE_COOLDOWN);
		}

		player.sendOverlayMessage(Component.translatable(door.refusal));
	}

	private HymnalDoors() {
	}
}
