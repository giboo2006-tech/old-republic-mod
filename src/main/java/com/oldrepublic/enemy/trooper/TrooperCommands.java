package com.oldrepublic.enemy.trooper;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.oldrepublic.combat.IncidentMode;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.progression.Progression;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /oldrepublic trooper ...} (operator, spec 10.8 and the W4 debug commands):
 * <ul>
 * <li>{@code spawn <army> <role> <pos>}: one trooper (an officer through {@link Troopers#spawnOfficer}
 * with name seed 0);</li>
 * <li>{@code squad <army> <pos> [incident] [owner]}: a patrol composition through
 * {@link Troopers#spawnSquad}, sized for the owner's level; the owner defaults to you;</li>
 * <li>{@code downed <army> <pos> [owner]}: a downed trooper for the owner (you, else the nearest
 * player);</li>
 * <li>{@code act <action> <near> [at]}: the nearest trooper within 32 blocks of {@code near} that
 * can, performs {@code grenade} (at {@code at}), {@code flare}, {@code rally}, {@code vent},
 * {@code heal} or {@code salute} now, with its full tell.</li>
 * </ul>
 * {@code army} is {@code republic} or {@code imperial} ({@code sith_empire} also works). Every
 * player argument is a single player.
 */
public final class TrooperCommands {
	private static final List<String> ARMIES = List.of("republic", "imperial", "sith_empire");
	private static final List<String> INCIDENTS = List.of("none", "army", "army_and_owner");
	private static final List<String> ACTIONS = List.of("grenade", "flare", "rally", "vent", "heal", "salute");
	/** {@code act} looks for a trooper this far from {@code near}. */
	private static final double ACT_RANGE = 32;

	public static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("trooper")
				.then(literal("spawn")
						.then(armyArgument()
								.then(argument("role", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(roleIds(), builder))
										// API-CHECK: BlockPosArgument.blockPos() / getLoadedBlockPos(ctx, name) (vanilla; also used by SiteCommands)
										.then(argument("pos", BlockPosArgument.blockPos())
												.executes(TrooperCommands::spawn)))))
				.then(literal("squad")
						.then(armyArgument()
								.then(argument("pos", BlockPosArgument.blockPos())
										.executes(ctx -> squad(ctx, "none", ctx.getSource().getPlayer()))
										.then(argument("incident", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(INCIDENTS, builder))
												.executes(ctx -> squad(ctx, StringArgumentType.getString(ctx, "incident"), ctx.getSource().getPlayer()))
												.then(argument("owner", EntityArgument.player())
														.executes(ctx -> squad(ctx, StringArgumentType.getString(ctx, "incident"),
																EntityArgument.getPlayer(ctx, "owner"))))))))
				.then(literal("downed")
						.then(armyArgument()
								.then(argument("pos", BlockPosArgument.blockPos())
										.executes(ctx -> downed(ctx, null))
										.then(argument("owner", EntityArgument.player())
												.executes(ctx -> downed(ctx, EntityArgument.getPlayer(ctx, "owner")))))))
				.then(literal("act")
						.then(argument("action", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ACTIONS, builder))
								.then(argument("near", BlockPosArgument.blockPos())
										.executes(ctx -> act(ctx, null))
										.then(argument("at", BlockPosArgument.blockPos())
												.executes(ctx -> act(ctx, Vec3.atBottomCenterOf(BlockPosArgument.getLoadedBlockPos(ctx, "at"))))))))));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> armyArgument() {
		return argument("army", StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ARMIES, builder));
	}

	private static List<String> roleIds() {
		List<String> ids = new ArrayList<>();

		for (TrooperRole role : TrooperRole.values()) {
			ids.add(role.name().toLowerCase(Locale.ROOT));
		}

		return ids;
	}

	/** {@code republic}, or {@code imperial}/{@code sith_empire}; null for anything else. */
	static @Nullable Faction army(String id) {
		return switch (id.toLowerCase(Locale.ROOT)) {
			case "republic" -> Faction.REPUBLIC;
			case "imperial", "sith_empire", "empire", "sith" -> Faction.SITH_EMPIRE;
			default -> null;
		};
	}

	private static @Nullable Faction army(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "army");
		Faction army = army(id);

		if (army == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.trooper.bad_army", id));
		}

		return army;
	}

	private static int spawn(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Faction army = army(ctx);

		if (army == null) {
			return 0;
		}

		String roleId = StringArgumentType.getString(ctx, "role");
		TrooperRole role = null;

		for (TrooperRole candidate : TrooperRole.values()) {
			if (candidate.name().equalsIgnoreCase(roleId)) {
				role = candidate;
			}
		}

		if (role == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.trooper.bad_role", roleId));
			return 0;
		}

		ServerLevel level = ctx.getSource().getLevel();
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
		LivingEntity spawned;

		if (role == TrooperRole.OFFICER) {
			spawned = Troopers.spawnOfficer(level, pos, army, 0);
		} else {
			TrooperEntity trooper = Troopers.create(level, army, role, Vec3.atBottomCenterOf(pos), EntitySpawnReason.COMMAND);

			if (trooper == null || !level.addFreshEntity(trooper)) {
				ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.trooper.spawn.failed"));
				return 0;
			}

			trooper.setPersistenceRequired();
			spawned = trooper;
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.trooper.spawn", spawned.getDisplayName()), true);
		return 1;
	}

	private static int squad(CommandContext<CommandSourceStack> ctx, String incidentId, @Nullable ServerPlayer owner) throws CommandSyntaxException {
		Faction army = army(ctx);

		if (army == null) {
			return 0;
		}

		IncidentMode incident = switch (incidentId.toLowerCase(Locale.ROOT)) {
			case "none" -> IncidentMode.NONE;
			case "army" -> IncidentMode.ARMY;
			case "army_and_owner" -> IncidentMode.ARMY_AND_OWNER;
			default -> null;
		};

		if (incident == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.trooper.bad_incident", incidentId));
			return 0;
		}

		ServerLevel level = ctx.getSource().getLevel();
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
		ServerPlayer sizing = owner != null ? owner : nearestPlayer(level, pos);
		int playerLevel = sizing != null ? Progression.get(sizing).level : 1;
		List<LivingEntity> squad = Troopers.spawnSquad(level, pos, army, Troopers.defaultSquad(army, playerLevel),
				owner != null ? owner.getUUID() : null, incident);

		if (squad.isEmpty()) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.trooper.spawn.failed"));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.trooper.squad", squad.size(),
				squad.get(0).getType().getDescription(), incident.name().toLowerCase(Locale.ROOT)), true);
		return squad.size();
	}

	private static int downed(CommandContext<CommandSourceStack> ctx, @Nullable ServerPlayer givenOwner) throws CommandSyntaxException {
		Faction army = army(ctx);

		if (army == null) {
			return 0;
		}

		ServerLevel level = ctx.getSource().getLevel();
		BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
		ServerPlayer owner = givenOwner != null ? givenOwner : ctx.getSource().getPlayer() != null ? ctx.getSource().getPlayer() : nearestPlayer(level, pos);

		if (owner == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.trooper.downed.no_owner"));
			return 0;
		}

		LivingEntity downed = Troopers.spawnDowned(level, pos, army, owner.getUUID());
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.trooper.downed", downed.getDisplayName(), owner.getDisplayName()), true);
		return 1;
	}

	private static int act(CommandContext<CommandSourceStack> ctx, @Nullable Vec3 at) throws CommandSyntaxException {
		String action = StringArgumentType.getString(ctx, "action").toLowerCase(Locale.ROOT);

		if (!ACTIONS.contains(action)) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.trooper.bad_action", action));
			return 0;
		}

		ServerLevel level = ctx.getSource().getLevel();
		Vec3 near = Vec3.atBottomCenterOf(BlockPosArgument.getLoadedBlockPos(ctx, "near"));
		TrooperEntity best = null;

		for (TrooperEntity trooper : level.getEntitiesOfClass(TrooperEntity.class, new AABB(near, near).inflate(ACT_RANGE),
				t -> t.canPerform(action) && t.position().distanceToSqr(near) <= ACT_RANGE * ACT_RANGE)) {
			if (best == null || trooper.position().distanceToSqr(near) < best.position().distanceToSqr(near)) {
				best = trooper;
			}
		}

		if (best == null || !best.perform(action, at)) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.trooper.act.none", action));
			return 0;
		}

		TrooperEntity actor = best;
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.trooper.act", actor.getDisplayName(), action), true);
		return 1;
	}

	private static @Nullable ServerPlayer nearestPlayer(ServerLevel level, BlockPos pos) {
		ServerPlayer best = null;
		double bestDistance = Double.MAX_VALUE;

		for (ServerPlayer player : level.players()) {
			double distance = player.distanceToSqr(Vec3.atCenterOf(pos));

			if (!player.isSpectator() && distance < bestDistance) {
				best = player;
				bestDistance = distance;
			}
		}

		return best;
	}

	private TrooperCommands() {
	}
}
