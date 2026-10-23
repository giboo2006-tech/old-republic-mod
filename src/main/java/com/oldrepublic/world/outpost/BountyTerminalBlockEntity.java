package com.oldrepublic.world.outpost;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.enemy.trooper.TrooperPost;
import com.oldrepublic.enemy.trooper.TrooperRole;
import com.oldrepublic.enemy.trooper.Troopers;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteBalance;
import com.oldrepublic.world.site.SiteBlocks;
import com.oldrepublic.world.site.SiteChecks;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SiteStates;
import com.oldrepublic.world.site.StructureLocator;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A bounty terminal's outpost (spec 3.3, 3.5, 7.4; NBT of W1 plan 4.8): the officer's UUID
 * ({@code officer}, int[4]), the four garrison UUIDs ({@code garrison}, a list of int[4] or empty
 * int[]), the last refill ({@code refill_at}) and the consecutive empty checks per slot
 * ({@code failed}, int[5]).
 *
 * <p>Every 200 ticks it applies the frozen loading rule, counts its members with {@code getEntities}
 * in its leash box (bound to this terminal through {@link TerminalBinding}), discards stale copies,
 * adopts unlisted members, and lets {@link GarrisonRules#step} decide what to spawn. Spawned members
 * are bound, persistent and leashed within {@code sites.garrisonLeash}: through W1's entity tag
 * ({@link TerminalBinding}) and through {@link Troopers#bindTerminal}, which saves the trooper's
 * {@code Terminal} (12.1). As a {@link TrooperPost} it lets W4's troopers run the same stale copy
 * check on their own tick.
 */
public class BountyTerminalBlockEntity extends BlockEntity implements TrooperPost {
	/** Ticks between checks. */
	public static final int INTERVAL = 200;
	/** Members are counted this far beyond their leash, horizontally and vertically. */
	private static final int COUNT_MARGIN = 8;
	private static final Codec<int[]> INT_ARRAY = Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of);
	private static final List<TrooperRole> REPUBLIC_GARRISON = List.of(TrooperRole.RIFLEMAN, TrooperRole.RIFLEMAN, TrooperRole.MEDIC, TrooperRole.SERGEANT);
	private static final List<TrooperRole> IMPERIAL_GARRISON = List.of(TrooperRole.RIFLEMAN, TrooperRole.RIFLEMAN, TrooperRole.GRENADIER, TrooperRole.SERGEANT);
	private static final SiteAnchors.Anchor[] SLOT_ANCHORS = {
			SiteAnchors.Anchor.OFFICER, SiteAnchors.Anchor.GARRISON_1, SiteAnchors.Anchor.GARRISON_2, SiteAnchors.Anchor.GARRISON_3, SiteAnchors.Anchor.GARRISON_4};

	private final @Nullable UUID[] members = new UUID[GarrisonRules.SLOTS];
	private final int[] failed = new int[GarrisonRules.SLOTS];
	private long refillAt;

	public BountyTerminalBlockEntity(BlockPos pos, BlockState state) {
		super(SiteBlocks.BOUNTY_TERMINAL_ENTITY, pos, state);
	}

	/** The block-entity ticker: checks every {@link #INTERVAL} ticks, staggered by position. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, BountyTerminalBlockEntity terminal) {
		if (level instanceof ServerLevel server && (server.getGameTime() + Math.floorMod(pos.asLong(), INTERVAL)) % INTERVAL == 0) {
			terminal.check(server);
		}
	}

	/** The army of this terminal's outpost. */
	public SiteStates.Army army() {
		return getBlockState().getValue(SiteStates.FACTION);
	}

	/** The UUID listed for {@code slot} (0 the officer, 1 to 4 the garrison), or null. */
	public @Nullable UUID member(int slot) {
		return slot >= 0 && slot < GarrisonRules.SLOTS ? members[slot] : null;
	}

	@Override
	public @Nullable UUID memberAt(int slot) {
		return member(slot);
	}

	/** The saved state in the form {@link GarrisonRules} works on. */
	public GarrisonRules.State state() {
		List<GarrisonRules.Slot> slots = new ArrayList<>(GarrisonRules.SLOTS);

		for (int i = 0; i < GarrisonRules.SLOTS; i++) {
			slots.add(new GarrisonRules.Slot(members[i], failed[i]));
		}

		return new GarrisonRules.State(slots, refillAt);
	}

	/** Replaces the saved state (the result of a rule step, or a test's data). */
	public void restore(GarrisonRules.State state) {
		for (int i = 0; i < GarrisonRules.SLOTS; i++) {
			members[i] = state.slots().get(i).member();
			failed[i] = Math.max(0, state.slots().get(i).failed());
		}

		refillAt = state.refillAt();
		setChanged();
	}

	/**
	 * One 200-tick check (also called directly by tests): observe, step the rule, spawn and bind what
	 * it asks for. Returns the members spawned by this check.
	 */
	public List<LivingEntity> check(ServerLevel level) {
		SiteBalance b = SiteBalance.get();
		GarrisonRules.Observation obs = observe(level, b);
		GarrisonRules.Result result = GarrisonRules.step(state(), obs, level.getGameTime(), b);
		restore(result.next());

		if (result.spawn().isEmpty()) {
			return List.of();
		}

		return spawn(level, result.spawn(), b);
	}

	/**
	 * What a check sees: the loading rule (the entities of this chunk and the 8 around it loaded, and
	 * this position entity ticking), then every entity bound to this terminal in the leash box. A
	 * member listed for its slot counts as present (and is leashed again); an unlisted member of an
	 * empty slot is adopted; any other bound entity is a stale copy and is discarded.
	 */
	public GarrisonRules.Observation observe(ServerLevel level, SiteBalance b) {
		BlockPos pos = getBlockPos();
		boolean near = SiteChecks.playerWithin(level, Vec3.atCenterOf(pos), b.garrisonRefillClearRange);

		if (!SiteChecks.entitiesReady(level, pos, 1)) {
			return new GarrisonRules.Observation(false, 0, near);
		}

		int present = 0;
		AABB box = new AABB(pos).inflate(b.garrisonLeash + COUNT_MARGIN, COUNT_MARGIN, b.garrisonLeash + COUNT_MARGIN);

		for (Entity entity : level.getEntities((Entity) null, box, e -> TerminalBinding.slotAt(e, pos) >= 0)) {
			int slot = TerminalBinding.slotAt(entity, pos);

			if (members[slot] == null && entity.isAlive()) {
				members[slot] = entity.getUUID();
				setChanged();
			}

			if (!entity.getUUID().equals(members[slot])) {
				entity.discard();
				continue;
			}

			if (entity.isAlive()) {
				present |= 1 << slot;
				TerminalBinding.leash(entity, pos, b.garrisonLeash);
			}
		}

		return new GarrisonRules.Observation(true, present, near);
	}

	private List<LivingEntity> spawn(ServerLevel level, List<Integer> slots, SiteBalance b) {
		BlockPos pos = getBlockPos();
		SiteStates.Army army = army();
		SitePiece piece = outpostPiece(level);
		List<LivingEntity> spawned = new ArrayList<>();
		List<Integer> garrison = new ArrayList<>();

		for (int slot : slots) {
			if (slot == GarrisonRules.OFFICER) {
				BlockPos origin = piece != null ? piece.origin() : pos;
				LivingEntity officer = Troopers.spawnOfficer(level, stand(piece, slot), army.faction(), Math.floorMod(origin.hashCode(), 6));
				record(officer, slot, b);
				spawned.add(officer);
			} else {
				garrison.add(slot);
			}
		}

		if (!garrison.isEmpty()) {
			List<TrooperRole> roster = army == SiteStates.Army.REPUBLIC ? REPUBLIC_GARRISON : IMPERIAL_GARRISON;
			List<TrooperRole> roles = new ArrayList<>(garrison.size());

			for (int slot : garrison) {
				roles.add(roster.get(slot - 1));
			}

			// One call for the slots filled together, so they form one squad.
			List<LivingEntity> squad = Troopers.spawnSquad(level, stand(piece, garrison.getFirst()), army.faction(), roles, null, IncidentMode.NONE);

			for (int i = 0; i < Math.min(squad.size(), garrison.size()); i++) {
				LivingEntity member = squad.get(i);
				int slot = garrison.get(i);
				BlockPos stand = stand(piece, slot);
				member.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
				record(member, slot, b);
				spawned.add(member);
			}
		}

		return spawned;
	}

	private void record(LivingEntity member, int slot, SiteBalance b) {
		TerminalBinding.bind(member, getBlockPos(), slot, b.garrisonLeash);
		// W4's own binding (saved as Terminal): slot 0 is the officer, 1 to 4 the garrison.
		Troopers.bindTerminal(member, getBlockPos(), slot, b.garrisonLeash);
		members[slot] = member.getUUID();
		failed[slot] = 0;
		setChanged();
	}

	/** The outpost piece this terminal stands in (worldgen or runtime), or null for a lone terminal. */
	private @Nullable SitePiece outpostPiece(ServerLevel level) {
		SitePiece piece = StructureLocator.pieceAt(level, getBlockPos());
		return piece != null && (piece.kind() == SiteKind.REPUBLIC_OUTPOST || piece.kind() == SiteKind.IMPERIAL_OUTPOST) ? piece : null;
	}

	/**
	 * Where a slot's member stands: its anchor in the outpost template, else (a lone terminal) two
	 * blocks in front of the screen for the officer and the four corners 3 blocks out for the garrison.
	 */
	private BlockPos stand(@Nullable SitePiece piece, int slot) {
		BlockPos anchored = piece == null ? null : SiteAnchors.find(piece, SLOT_ANCHORS[slot]);

		if (anchored != null) {
			return anchored;
		}

		BlockPos pos = getBlockPos();

		if (slot == GarrisonRules.OFFICER) {
			Direction facing = getBlockState().getValue(SiteStates.FACING);
			return pos.relative(facing, 2);
		}

		int dx = slot == 1 || slot == 4 ? -3 : 3;
		int dz = slot <= 2 ? -3 : 3;
		return pos.offset(dx, 0, dz);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		Arrays.fill(members, null);
		Arrays.fill(failed, 0);
		members[GarrisonRules.OFFICER] = input.getIntArray("officer").map(BountyTerminalBlockEntity::uuid).orElse(null);
		List<int[]> garrison = input.read("garrison", INT_ARRAY.listOf()).orElse(List.of());

		for (int i = 0; i < Math.min(garrison.size(), GarrisonRules.SLOTS - 1); i++) {
			members[i + 1] = uuid(garrison.get(i));
		}

		refillAt = input.getLongOr("refill_at", 0L);
		int[] savedFailed = input.getIntArray("failed").orElse(new int[0]);

		for (int i = 0; i < Math.min(savedFailed.length, GarrisonRules.SLOTS); i++) {
			failed[i] = Math.max(0, savedFailed[i]);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		UUID officer = members[GarrisonRules.OFFICER];

		if (officer != null) {
			output.putIntArray("officer", ints(officer));
		}

		List<int[]> garrison = new ArrayList<>(GarrisonRules.SLOTS - 1);

		for (int i = 1; i < GarrisonRules.SLOTS; i++) {
			garrison.add(Optional.ofNullable(members[i]).map(BountyTerminalBlockEntity::ints).orElse(new int[0]));
		}

		output.store("garrison", INT_ARRAY.listOf(), garrison);
		output.putLong("refill_at", refillAt);
		output.putIntArray("failed", failed.clone());
	}

	/** A UUID as the vanilla int[4] layout (most significant bits first). */
	private static int[] ints(UUID uuid) {
		long most = uuid.getMostSignificantBits();
		long least = uuid.getLeastSignificantBits();
		return new int[] {(int) (most >> 32), (int) most, (int) (least >> 32), (int) least};
	}

	/** The UUID of a vanilla int[4], or null for any other length (an empty slot). */
	private static @Nullable UUID uuid(int[] ints) {
		if (ints.length != 4) {
			return null;
		}

		return new UUID((long) ints[0] << 32 | ints[1] & 0xFFFFFFFFL, (long) ints[2] << 32 | ints[3] & 0xFFFFFFFFL);
	}
}
