package com.oldrepublic.world.site;

import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.quest.QuestHooks;
import com.oldrepublic.quest.story.ChapterThree;
import com.oldrepublic.quest.story.ChapterThreeStage;
import com.oldrepublic.world.EchoCairns;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * An echo cairn's owner, its index and how long it stays revealed (NBT of W1 plan 4.8: {@code owner}
 * int[4], {@code index}, {@code revealed_until}).
 *
 * <p>Every 20 ticks it veils itself again once {@code revealed_until} has passed, and checks lazily
 * whether it has expired (spec 3.3): its owner is online and past LISTENING, or listed in the
 * expired-owner set of {@link WorldSites}. An expired cairn removes itself; nothing ever writes into
 * an unloaded chunk to remove one. A cairn that finds its online owner past LISTENING also lists
 * them ({@link EchoCairns#expire}), once, so their other cairns go even while they are offline.
 */
public class EchoCairnBlockEntity extends BlockEntity {
	/** Ticks between checks. */
	public static final int INTERVAL = 20;

	private @Nullable UUID owner;
	private int index;
	private long revealedUntil;

	public EchoCairnBlockEntity(BlockPos pos, BlockState state) {
		super(SiteBlocks.ECHO_CAIRN_ENTITY, pos, state);
	}

	/** The block-entity ticker: checks every {@link #INTERVAL} ticks, staggered by position. */
	public static void serverTick(Level level, BlockPos pos, BlockState state, EchoCairnBlockEntity cairn) {
		if (level instanceof ServerLevel server && (server.getGameTime() + Math.floorMod(pos.asLong(), INTERVAL)) % INTERVAL == 0) {
			cairn.tick(server);
		}
	}

	private void tick(ServerLevel level) {
		BlockPos pos = getBlockPos();
		MinecraftServer server = level.getServer();
		ServerPlayer online = owner == null ? null : server.getPlayerList().getPlayer(owner);
		Set<UUID> expired = WorldSites.expiredCairnOwners(server);

		if (checkExpiry(level, pos, online, expired)) {
			if (online != null && !expired.contains(online.getUUID())) {
				EchoCairns.expire(online);
			}

			return;
		}

		BlockState state = level.getBlockState(pos);

		if (state.is(SiteBlocks.ECHO_CAIRN) && EchoCairnBlock.revealed(state) && level.getGameTime() >= revealedUntil) {
			level.setBlock(pos, state.setValue(SiteStates.VEIL, SiteStates.CairnVeil.VEILED), Block.UPDATE_ALL);
		}
	}

	/**
	 * The lazy expiry rule (a test seam): removes the cairn at {@code pos} and returns true when its
	 * owner is in {@code expiredOwners}, or when {@code onlineOwner} is its owner and has gone past
	 * LISTENING. A cairn without an owner never expires. The real tick passes the server's set read-only.
	 */
	public boolean checkExpiry(ServerLevel level, BlockPos pos, @Nullable ServerPlayer onlineOwner, Set<UUID> expiredOwners) {
		if (owner == null) {
			return false;
		}

		boolean expired = expiredOwners.contains(owner)
				|| (onlineOwner != null && owner.equals(onlineOwner.getUUID()) && ChapterThree.atLeast(onlineOwner, ChapterThreeStage.FIRST_ECHO));

		if (!expired) {
			return false;
		}

		if (level.getBlockState(pos).is(SiteBlocks.ECHO_CAIRN)) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		}

		return true;
	}

	/**
	 * Reveals the cairn for {@code ticks} when {@code by} is its owner; a cairn already revealed stays
	 * revealed until the later of the two times. Returns whether it is now revealed.
	 */
	public boolean reveal(ServerLevel level, UUID by, int ticks) {
		if (owner == null || !owner.equals(by) || ticks <= 0) {
			return false;
		}

		BlockPos pos = getBlockPos();
		BlockState state = level.getBlockState(pos);

		if (!state.is(SiteBlocks.ECHO_CAIRN)) {
			return false;
		}

		revealedUntil = Math.max(revealedUntil, level.getGameTime() + ticks);
		setChanged();

		if (!EchoCairnBlock.revealed(state)) {
			level.setBlock(pos, state.setValue(SiteStates.VEIL, SiteStates.CairnVeil.REVEALED), Block.UPDATE_ALL);
		}

		return true;
	}

	/**
	 * A player uses the revealed cairn. Its owner touches it: {@code QuestHooks.onCairnTouched} (which
	 * plays {@code cairn_touch}), then the cairn turns to air. Anyone else is told it is not theirs.
	 */
	public void touch(ServerLevel level, ServerPlayer player) {
		BlockPos pos = getBlockPos();

		if (owner == null || !owner.equals(player.getUUID())) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.world.cairn.not_yours"));
			return;
		}

		boolean cache = level.getBlockState(pos).getValue(SiteStates.STYLE) == SiteStates.CairnStyle.CACHE;
		QuestHooks.onCairnTouched(player, pos.immutable(), index, cache);
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	/** Binds the cairn to its owner; {@code index} is 0-based, as passed to {@code onCairnTouched}. */
	public void setOwner(UUID owner, int index) {
		this.owner = owner;
		this.index = Math.max(0, index);
		setChanged();
	}

	public @Nullable UUID owner() {
		return owner;
	}

	public int index() {
		return index;
	}

	/** The game time the cairn veils itself again. */
	public long revealedUntil() {
		return revealedUntil;
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		index = Math.max(0, input.getIntOr("index", 0));
		revealedUntil = input.getLongOr("revealed_until", 0L);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.storeNullable("owner", UUIDUtil.CODEC, owner);
		output.putInt("index", index);
		output.putLong("revealed_until", revealedUntil);
	}
}
