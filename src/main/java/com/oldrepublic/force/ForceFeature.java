package com.oldrepublic.force;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityAction;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.force.heal.ForceHeal;
import com.oldrepublic.force.heal.HealShape;
import com.oldrepublic.force.jump.ForceJump;
import com.oldrepublic.force.jump.JumpShape;
import com.oldrepublic.force.lightning.ForceLightning;
import com.oldrepublic.force.lightning.LightningShape;
import com.oldrepublic.force.pull.ForcePull;
import com.oldrepublic.force.pull.PullShape;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.skill.SkillTrees;
import com.oldrepublic.skill.Skills;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Server and common initialisation for the force feature. Called from OldRepublicMod. */
public final class ForceFeature {
	/** The five Force powers, in the order they register. */
	private static final String[] POWERS = {ForcePush.ABILITY_ID, ForcePull.ID, ForceJump.ID, ForceHeal.ID, ForceLightning.ID};
	private static final int PRUNE_INTERVAL = 100;

	public static void init() {
		ForceParticles.init();

		Abilities.register(new PushAbility());
		Abilities.register(new ForcePull());
		Abilities.register(new ForceJump());
		Abilities.register(new ForceHeal());
		Abilities.register(new ForceLightning());

		ServerTickEvents.END_SERVER_TICK.register(ForceFeature::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearAll());
		ServerPlayerEvents.LEAVE.register(player -> forgetAll(player, false));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				forgetAll(player, true);
			}
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> forgetAll(player, true));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> onDamaged(entity, source.getEntity(), damageTaken));
		// A healing channel keeps the hands busy: no melee until it ends.
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) ->
				!level.isClientSide() && ForceHeal.isChannelling(player) ? InteractionResult.FAIL : InteractionResult.PASS);

		OldRepublicCommands.addSubcommand((context, root) -> root.then(pushCommand()).then(forceCommand()));
	}

	private static void tick(MinecraftServer server) {
		PushCharges.tick(server);
		PushEffects.tick(server);
		ForcePull.tick();
		ForceJump.tick(server);
		ForceHeal.tick();
		ForceLightning.tick();

		long now = server.overworld().getGameTime();

		if (now % PRUNE_INTERVAL == 0) {
			ForceStatus.prune(now);
			ControlEffects.prune(now);
			ForceHeal.prune(now);
		}
	}

	private static void onDamaged(LivingEntity entity, @Nullable Entity attacker, float damageTaken) {
		ForceHeal.onDamaged(entity, attacker, damageTaken);
		ForceLightning.onDamaged(entity, damageTaken);
	}

	/**
	 * Ends everything the player is doing with the Force (death, disconnect, dimension change).
	 * {@code feedback} sends the stop events so loops and poses end on every client.
	 */
	public static void forgetAll(ServerPlayer player, boolean feedback) {
		for (String id : POWERS) {
			ForceAbility ability = Abilities.get(id);

			if (ability != null && ability.isActive(player)) {
				ability.cancel(player, feedback);
			}
		}

		ForcePull.forget(player);
		ForceJump.forget(player);
		ForceHeal.forget(player);
		ForceLightning.forget(player);
	}

	private static void clearAll() {
		PushCharges.clear();
		PushEffects.clear();
		ForcePull.clear();
		ForceJump.clear();
		ForceHeal.clear();
		ForceLightning.clear();
		ForceStatus.clear();
		ControlEffects.clear();
	}

	/** {@code /oldrepublic push grant|branch <id>|cooldown|fire} (Chapter One test commands). */
	private static LiteralArgumentBuilder<CommandSourceStack> pushCommand() {
		LiteralArgumentBuilder<CommandSourceStack> branch = literal("branch");

		for (PushBranch value : PushBranch.values()) {
			String name = value == PushBranch.NONE ? "none" : value.id();
			branch.then(literal(name).executes(ctx -> setBranch(ctx.getSource().getPlayerOrException(), value)));
		}

		return literal("push")
				.then(literal("grant")
						.executes(ctx -> grantPush(ctx.getSource(), ctx.getSource().getPlayerOrException()))
						.then(argument("player", EntityArgument.player())
								.executes(ctx -> grantPush(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
				.then(branch)
				.then(literal("cooldown").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					Progression.setCooldown(player, ForcePush.ABILITY_ID, 0);
					return 1;
				}))
				.then(literal("fire").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();
					return ForcePush.cast(player, 1f) ? 1 : 0;
				}));
	}

	/**
	 * {@code /oldrepublic force grant <power|upgrade|all> [player]}, {@code force use <power>
	 * start|release|cancel} (drives a power without the slot keys, for tests and screenshots) and
	 * {@code force cooldowns} (clears the five powers' cooldowns).
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> forceCommand() {
		LiteralArgumentBuilder<CommandSourceStack> grant = literal("grant");
		LiteralArgumentBuilder<CommandSourceStack> use = literal("use");

		for (String id : grantable()) {
			grant.then(literal(id)
					.executes(ctx -> grant(ctx.getSource(), ctx.getSource().getPlayerOrException(), List.of(id)))
					.then(argument("player", EntityArgument.player())
							.executes(ctx -> grant(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), List.of(id)))));
		}

		grant.then(literal("all")
				.executes(ctx -> grant(ctx.getSource(), ctx.getSource().getPlayerOrException(), List.of(POWERS)))
				.then(argument("player", EntityArgument.player())
						.executes(ctx -> grant(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), List.of(POWERS)))));

		for (String id : POWERS) {
			LiteralArgumentBuilder<CommandSourceStack> power = literal(id);

			for (AbilityAction action : AbilityAction.values()) {
				power.then(literal(action.name().toLowerCase(Locale.ROOT))
						.executes(ctx -> use(ctx.getSource().getPlayerOrException(), id, action)));
			}

			use.then(power);
		}

		return literal("force")
				.then(grant)
				.then(use)
				.then(literal("cooldowns").executes(ctx -> {
					ServerPlayer player = ctx.getSource().getPlayerOrException();

					for (String id : POWERS) {
						Progression.setCooldown(player, id, 0);
					}

					return 1;
				}));
	}

	/** The five powers and every upgrade node of theirs. */
	private static List<String> grantable() {
		List<String> ids = new ArrayList<>(List.of(POWERS));

		for (PushBranch value : PushBranch.values()) {
			addNode(ids, value.nodeId());
		}

		for (PullShape value : PullShape.values()) {
			addNode(ids, value.nodeId());
		}

		for (JumpShape value : JumpShape.values()) {
			addNode(ids, value.nodeId());
		}

		for (HealShape value : HealShape.values()) {
			addNode(ids, value.nodeId());
		}

		for (LightningShape value : LightningShape.values()) {
			addNode(ids, value.nodeId());
		}

		return ids;
	}

	private static void addNode(List<String> ids, String nodeId) {
		if (!nodeId.isEmpty()) {
			ids.add(nodeId);
		}
	}

	/**
	 * Grants powers or upgrades. A power goes into the {@link ForceData} ability set and its skill
	 * node; an upgrade is granted as its skill node (exclusive siblings are replaced by the skills
	 * feature), or into the ability set when the trees do not know it.
	 */
	private static int grant(CommandSourceStack source, ServerPlayer player, List<String> ids) {
		ForceData data = Progression.get(player);

		for (String id : ids) {
			boolean power = List.of(POWERS).contains(id);

			if (power) {
				data.abilities.add(id);
			}

			if (SkillTrees.server().node(id) != null) {
				Progression.commit(player, data);
				Skills.grant(player, id);
				data = Progression.get(player);
			} else if (!power) {
				data.abilities.add(id);
			}
		}

		Progression.commit(player, data);
		source.sendSuccess(() -> Component.literal("Granted " + String.join(", ", ids) + " to " + player.getName().getString()), false);
		return ids.size();
	}

	private static int use(ServerPlayer player, String id, AbilityAction action) {
		ForceAbility ability = Abilities.get(id);

		if (ability == null) {
			return 0;
		}

		ability.onInput(player, action);
		return 1;
	}

	private static int grantPush(CommandSourceStack source, ServerPlayer player) {
		boolean learned = ForcePush.grant(player);
		source.sendSuccess(() -> Component.literal(learned ? "Taught Force Push to " + player.getName().getString() : "Already knows Force Push"), false);
		return 1;
	}

	private static int setBranch(ServerPlayer player, PushBranch branch) {
		ForceData data = Progression.get(player);
		data.pushBranch = branch.id();
		Progression.commit(player, data);
		player.sendSystemMessage(Component.literal("Push branch set to '" + branch.id() + "'"));
		return 1;
	}

	private ForceFeature() {
	}
}
