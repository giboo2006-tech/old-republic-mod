package com.oldrepublic.boss.cantor;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.boss.raid.RaidBalance;
import com.oldrepublic.boss.raid.RaidSession;
import com.oldrepublic.boss.raid.UndercroftLayout;
import com.oldrepublic.force.ForceReactions;
import com.oldrepublic.quest.story.ChapterFive;
import com.oldrepublic.quest.story.ChapterFiveStage;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * The Long Hymn's runs (spec MILESTONE_5.md 12.3, 12.4): one {@link CantorSession} per undercroft, in
 * memory only (a restart resets a running raid, the M3 rule). A player uses the engine's keystone
 * (anchor {@code KEYSTONE}) to begin: never on Peaceful, one Hymn at a time per undercroft, and only
 * when the player or someone playing inside has finished Chapter Five. The undercroft door's rule is
 * {@link #mayOpenUndercroft} (W3's door asks it). The roots of the Stormwood verse let go when an ally
 * strikes them, and damage breaks a silencing point's channel.
 */
public final class LongHymn {
	private static final List<CantorSession> SESSIONS = new ArrayList<>();

	/** Common initialisation (from {@code RaidFeature}): the entities, the particle, the reactions, the tick, the hooks and the commands. */
	public static void init() {
		CantorEntities.init();
		CantorParticles.init();
		ForceReactions.register(new CantorReactions());

		ServerTickEvents.END_SERVER_TICK.register(LongHymn::tick);
		// Before the levels save, so no raid's entities linger.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
		UseBlockCallback.EVENT.register(LongHymn::onUseBlock);
		AttackEntityCallback.EVENT.register(LongHymn::onAttack);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(LongHymn::afterDamage);

		CantorCommands.init();
	}

	// ---------------------------------------------------------------- the runs

	/**
	 * Starts a Long Hymn in the undercroft {@code layout} (the keystone, the commands, the tests): a
	 * countdown of {@code countdownTicks} (0: the Hymn begins on the next tick), then everyone inside is
	 * listed and the First Cantor rises.
	 *
	 * @return the run, or null when one already runs there or the world is on Peaceful
	 */
	public static @Nullable CantorSession start(ServerLevel level, UndercroftLayout layout, @Nullable ServerPlayer starter, int countdownTicks) {
		if (level.getDifficulty() == Difficulty.PEACEFUL || sessionIn(level, layout) != null) {
			return null;
		}

		CantorSession session = new CantorSession(level, layout, starter == null ? null : starter.getUUID());
		SESSIONS.add(session);
		session.begin(countdownTicks);
		return session;
	}

	/** The running Hymn in the undercroft laid out by {@code layout}, or null. */
	public static @Nullable CantorSession sessionIn(ServerLevel level, UndercroftLayout layout) {
		for (CantorSession session : SESSIONS) {
			if (!session.isOver() && session.level() == level && session.layout().cantor().equals(layout.cantor())) {
				return session;
			}
		}

		return null;
	}

	/** The running Hymn whose undercroft holds {@code pos}, or null. */
	public static @Nullable CantorSession sessionAt(ServerLevel level, BlockPos pos) {
		for (CantorSession session : SESSIONS) {
			if (!session.isOver() && session.level() == level && session.layout().box().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
				return session;
			}
		}

		return null;
	}

	/** The running Hymn that lists {@code player}, else the one around them, or null. */
	public static @Nullable CantorSession sessionOf(ServerPlayer player) {
		for (CantorSession session : SESSIONS) {
			if (!session.isOver() && session.isParticipant(player)) {
				return session;
			}
		}

		return sessionAt(player.level(), player.blockPosition());
	}

	/** Every running Hymn. */
	public static List<CantorSession> sessions() {
		return SESSIONS.stream().filter(s -> !s.isOver()).toList();
	}

	static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) {
			return;
		}

		for (CantorSession session : List.copyOf(SESSIONS)) {
			session.tick();
		}

		SESSIONS.removeIf(CantorSession::isOver);
	}

	/** Stops every run quietly (the server stopping, the tests). */
	public static void clear() {
		for (CantorSession session : List.copyOf(SESSIONS)) {
			session.stop(true);
		}

		SESSIONS.clear();
	}

	// ---------------------------------------------------------------- the keystone and the door

	/** Fabric {@code UseBlockCallback}: the engine's keystone (any block at the {@code KEYSTONE} anchor). Elsewhere it passes. */
	static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p) || p.isSpectator()
				|| !(level instanceof ServerLevel server)) {
			return InteractionResult.PASS;
		}

		return onKeystoneUsed(server, hit.getBlockPos(), p) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/**
	 * {@code player} used the block at {@code pos}: if it is an undercroft's keystone, the Hymn begins
	 * (or the reason it does not is shown).
	 *
	 * @return whether it was a keystone (the use is handled)
	 */
	public static boolean onKeystoneUsed(ServerLevel level, BlockPos pos, ServerPlayer player) {
		UndercroftLayout layout = UndercroftLayout.at(level, pos);

		if (layout == null || !layout.isKeystone(pos)) {
			return false;
		}

		String refusal = refusal(level, layout, player);

		if (refusal != null) {
			player.sendOverlayMessage(Component.translatable(refusal));
		} else {
			start(level, layout, player, RaidBalance.get().countdownTicks);
		}

		return true;
	}

	/** Why {@code player} cannot begin the Hymn in {@code layout} now (a lang key), or null when they can. */
	public static @Nullable String refusal(ServerLevel level, UndercroftLayout layout, ServerPlayer player) {
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return "boss.oldrepublic.cantor.peaceful";
		}

		if (sessionIn(level, layout) != null) {
			return "boss.oldrepublic.cantor.busy";
		}

		if (ChapterFive.atLeast(player, ChapterFiveStage.COMPLETE)) {
			return null;
		}

		for (ServerPlayer other : level.players()) {
			if (other.isAlive() && !other.isSpectator() && layout.inside(other.position()) && ChapterFive.atLeast(other, ChapterFiveStage.COMPLETE)) {
				return null;
			}
		}

		return "boss.oldrepublic.cantor.sealed";
	}

	/**
	 * Whether the undercroft door at {@code door} opens for {@code player} (12.3; W3's door asks): never
	 * on Peaceful; for a listed participant of the Hymn running there; once its join window has closed,
	 * for no one else (the M3 seal rule); otherwise for a player whose Chapter Five is complete (anyone
	 * may come in with them while the door stands open).
	 */
	public static boolean mayOpenUndercroft(ServerPlayer player, BlockPos door) {
		ServerLevel level = player.level();

		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}

		UndercroftLayout layout = UndercroftLayout.at(level, door);
		CantorSession running = layout == null ? null : sessionIn(level, layout);
		RaidSession raid = running == null ? null : running.raid();

		if (raid != null && raid.isParticipant(player.getUUID())) {
			return true;
		}

		if (raid != null && !raid.isEnded() && !raid.joinOpen()) {
			return false;
		}

		return ChapterFive.atLeast(player, ChapterFiveStage.COMPLETE);
	}

	// ---------------------------------------------------------------- the hooks

	/** Fabric {@code AttackEntityCallback}: a blow on a player the roots hold strikes the roots instead, and frees them. */
	static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer attacker) || !(target instanceof ServerPlayer held) || attacker == held
				|| SESSIONS.isEmpty()) {
			return InteractionResult.PASS;
		}

		for (CantorSession session : SESSIONS) {
			if (!session.isOver() && session.level() == level && session.layout().inside(attacker.position()) && session.verses().free(held)) {
				return InteractionResult.SUCCESS;
			}
		}

		return InteractionResult.PASS;
	}

	/** Fabric {@code AFTER_DAMAGE}: damage breaks the player's silencing channel. */
	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (damageTaken <= 0f || !(entity instanceof ServerPlayer player) || SESSIONS.isEmpty()) {
			return;
		}

		for (CantorSession session : SESSIONS) {
			if (!session.isOver()) {
				session.engine().onDamaged(player);
			}
		}
	}

	private LongHymn() {
	}
}
