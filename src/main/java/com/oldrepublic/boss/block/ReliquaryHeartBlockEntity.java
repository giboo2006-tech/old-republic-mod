package com.oldrepublic.boss.block;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.BossArenas;
import com.oldrepublic.boss.BossBlocks;
import com.oldrepublic.boss.HeartState;
import com.oldrepublic.boss.ReliquarySites;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Reliquary's heart (spec 8.2, 12.1): the saved half of the Collector fight. It stores the
 * owner, the {@link HeartState}, whether the first clear is done, the owner's attempts, the echo
 * clears and whether Unchained is unlocked, and where the hall was placed (so the arena can be
 * rebuilt after a restart). The fight itself, the {@code ArenaSession}, lives in memory in
 * {@link BossArenas} and is ticked from here. {@code ACTIVE} is never loaded back: a fight in progress
 * when the chunk unloads or the server stops simply resets, so it loads as {@code READY}.
 *
 * <p>NBT (frozen, 4.11): {@code Owner} (UUID), {@code State} (ordinal), {@code FirstClear},
 * {@code Attempts}, {@code EchoClears}, {@code Unchained}, {@code HallOrigin} (int[3]),
 * {@code HallRotation} (ordinal), {@code HallBox} (int[6]); added {@code YieldNotified} (the owner's
 * {@code onBossYielded} hook has run for this yield, so a restart does not repeat it).
 */
public class ReliquaryHeartBlockEntity extends BlockEntity {
	private static final String KEY_OWNER = "Owner";
	private static final String KEY_STATE = "State";
	private static final String KEY_FIRST_CLEAR = "FirstClear";
	private static final String KEY_ATTEMPTS = "Attempts";
	private static final String KEY_ECHO_CLEARS = "EchoClears";
	private static final String KEY_UNCHAINED = "Unchained";
	private static final String KEY_HALL_ORIGIN = "HallOrigin";
	private static final String KEY_HALL_ROTATION = "HallRotation";
	private static final String KEY_HALL_BOX = "HallBox";
	private static final String KEY_YIELD_NOTIFIED = "YieldNotified";

	private @Nullable UUID owner;
	private HeartState state = HeartState.DORMANT;
	private boolean firstClear;
	private int attempts;
	private int echoClears;
	private boolean unchained;
	private @Nullable BlockPos hallOrigin;
	private Rotation hallRotation = Rotation.NONE;
	private @Nullable BoundingBox hallBox;
	private boolean yieldNotified;

	public ReliquaryHeartBlockEntity(BlockPos pos, BlockState state) {
		super(BossBlocks.RELIQUARY_HEART_ENTITY, pos, state);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, ReliquaryHeartBlockEntity heart) {
		if (level instanceof ServerLevel server) {
			BossArenas.tickHeart(server, pos, heart);
		}
	}

	/** The player whose Reliquary this is, or null for a heart placed by hand with no owner yet. */
	public @Nullable UUID owner() {
		return owner;
	}

	public HeartState state() {
		return state;
	}

	/** Fights the owner has started (not counting rematches); 1 or more skips the meeting dialogue. */
	public int attempts() {
		return attempts;
	}

	/** Whether the first clear is done: the owner resolved Vasch's fate at least once. */
	public boolean firstClear() {
		return firstClear;
	}

	/** Echo sessions won (any participant count); the first one unlocks Unchained. */
	public int echoClears() {
		return echoClears;
	}

	/** Whether the rematch may be called Unchained (8.9: offered after the first echo clear). */
	public boolean unchainedUnlocked() {
		return unchained;
	}

	/**
	 * The hall piece this heart belongs to, rebuilt from the saved placement, or null when the heart
	 * was never given one (a heart set by hand, or a test fixture).
	 */
	public @Nullable SitePiece hallPiece() {
		if (hallOrigin == null || hallBox == null || level == null) {
			return null;
		}

		return new SitePiece(SiteKind.RELIQUARY, ReliquarySites.HALL, hallBox, hallRotation, hallOrigin, SitePiece.runtimeKey(level.dimension(), hallOrigin));
	}

	/** The saved hall box (the whole hall template), or null. */
	public @Nullable BoundingBox hallBox() {
		return hallBox;
	}

	public void setOwner(@Nullable UUID owner) {
		this.owner = owner;
		setChanged();
	}

	public void setState(HeartState state) {
		if (this.state != state) {
			this.state = state;
			setChanged();
		}
	}

	public void setFirstClear(boolean firstClear) {
		this.firstClear = firstClear;
		setChanged();
	}

	public void addAttempt() {
		attempts++;
		setChanged();
	}

	public void addEchoClear() {
		echoClears++;
		unchained = true;
		setChanged();
	}

	/** Whether the owner's {@code onBossYielded} hook already ran for the current yield. */
	public boolean yieldNotified() {
		return yieldNotified;
	}

	public void setYieldNotified(boolean notified) {
		if (yieldNotified != notified) {
			yieldNotified = notified;
			setChanged();
		}
	}

	/** Remembers where the hall was placed (from {@code BossArenas.onReliquaryPlaced}). */
	public void setHall(SitePiece hall) {
		hallOrigin = hall.origin();
		hallRotation = hall.rotation();
		BoundingBox box = hall.box();
		hallBox = new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
		setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read(KEY_OWNER, UUIDUtil.CODEC).orElse(null);
		HeartState loaded = HeartState.of(input.getIntOr(KEY_STATE, HeartState.DORMANT.ordinal()));
		// A fight never survives an unload or a restart: it resets (8.6).
		state = loaded == HeartState.ACTIVE ? HeartState.READY : loaded;
		firstClear = input.getBooleanOr(KEY_FIRST_CLEAR, false);
		attempts = Math.max(0, input.getIntOr(KEY_ATTEMPTS, 0));
		echoClears = Math.max(0, input.getIntOr(KEY_ECHO_CLEARS, 0));
		unchained = input.getBooleanOr(KEY_UNCHAINED, false);
		yieldNotified = input.getBooleanOr(KEY_YIELD_NOTIFIED, false);
		int[] origin = input.getIntArray(KEY_HALL_ORIGIN).orElse(new int[0]);
		int[] box = input.getIntArray(KEY_HALL_BOX).orElse(new int[0]);
		int rotation = input.getIntOr(KEY_HALL_ROTATION, 0);
		hallRotation = rotation >= 0 && rotation < Rotation.values().length ? Rotation.values()[rotation] : Rotation.NONE;
		hallOrigin = origin.length == 3 ? new BlockPos(origin[0], origin[1], origin[2]) : null;
		hallBox = box.length == 6 ? new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]) : null;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable(KEY_OWNER, UUIDUtil.CODEC, owner);
		output.putInt(KEY_STATE, state.ordinal());
		output.putBoolean(KEY_FIRST_CLEAR, firstClear);
		output.putInt(KEY_ATTEMPTS, attempts);
		output.putInt(KEY_ECHO_CLEARS, echoClears);
		output.putBoolean(KEY_UNCHAINED, unchained);
		output.putBoolean(KEY_YIELD_NOTIFIED, yieldNotified);

		if (hallOrigin != null && hallBox != null) {
			output.putIntArray(KEY_HALL_ORIGIN, new int[] {hallOrigin.getX(), hallOrigin.getY(), hallOrigin.getZ()});
			output.putInt(KEY_HALL_ROTATION, hallRotation.ordinal());
			output.putIntArray(KEY_HALL_BOX, new int[] {hallBox.minX(), hallBox.minY(), hallBox.minZ(), hallBox.maxX(), hallBox.maxY(), hallBox.maxZ()});
		}
	}
}
