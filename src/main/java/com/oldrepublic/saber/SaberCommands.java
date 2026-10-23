package com.oldrepublic.saber;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.oldrepublic.command.OldRepublicCommands;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberGuard.GuardSnapshot;
import com.oldrepublic.saber.duel.DuelistProfile;
import com.oldrepublic.saber.duel.NpcGuard;
import com.oldrepublic.saber.duel.SaberDuelist;
import com.oldrepublic.saber.duel.Spar;
import com.oldrepublic.saber.duel.SparRules;
import com.oldrepublic.saber.throwing.SaberThrow;
import com.oldrepublic.skill.Skills;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /oldrepublic saber ...} test commands:
 * <ul>
 * <li>{@code give [<color> <emitter> <grip> <pommel> [<material>]]}: a saber attuned to you, all its parts
 * of one material (steel without)</li>
 * <li>{@code crystal [<color>]}: a kyber crystal attuned to you</li>
 * <li>{@code parts [<material>]}: one of every hilt part, in that material (MILESTONE_7.md 3.1)</li>
 * <li>{@code part <emitter|grip|pommel> <variant> [<material>]}: one hilt part in a material</li>
 * <li>{@code material <material> [<emitter|grip|pommel>]}: makes the saber in your main hand (all its
 * parts, or one) of that material; a saberstaff's pommel stays steel</li>
 * <li>{@code stats}: the main hand saber's numbers before the form (MILESTONE_7.md 3.2, 6.3) and its set</li>
 * <li>{@code stamina [refill|set <amount>]}: shows, refills or sets your saber stamina</li>
 * <li>{@code form <unformed|soresu|ataru>}: grants the form node if needed and changes to it (at once if a change is refused)</li>
 * <li>{@code combo}: your current combo step</li>
 * <li>{@code recall}: ends your saber throws and clears the thrown marker</li>
 * <li>{@code throw}: Saber Throw with all its rules, without a slot</li>
 * <li>{@code lunge}: a lunge at the nearest valid target, with all its rules</li>
 * <li>{@code spar [yield|parries|technique_hits] [count]}: spars with the nearest saber NPC within 16 blocks</li>
 * </ul>
 */
final class SaberCommands {
	static void init() {
		OldRepublicCommands.addSubcommand((buildContext, root) -> root.then(literal("saber")
				.then(literal("give")
						.executes(ctx -> give(ctx, BladeColor.BLUE, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.WEIGHTED, HiltMaterial.STEEL))
						.then(word("color", BladeColor.values())
								.then(word("emitter", Emitter.values())
										.then(word("grip", Grip.values())
												.then(word("pommel", Pommel.values())
														.executes(ctx -> giveConfigured(ctx, HiltMaterial.STEEL))
														.then(word("material", HiltMaterial.values())
																.executes(ctx -> giveConfigured(ctx, parse(ctx, "material", HiltMaterial.values())))))))))
				.then(literal("crystal")
						.executes(ctx -> crystal(ctx, BladeColor.CYAN))
						.then(word("color", BladeColor.values())
								.executes(ctx -> crystal(ctx, parse(ctx, "color", BladeColor.values())))))
				.then(literal("parts")
						.executes(ctx -> parts(ctx, HiltMaterial.STEEL))
						.then(word("material", HiltMaterial.values())
								.executes(ctx -> parts(ctx, parse(ctx, "material", HiltMaterial.values())))))
				.then(literal("part")
						.then(partBranch("emitter", Emitter.values()))
						.then(partBranch("grip", Grip.values()))
						.then(partBranch("pommel", Pommel.values())))
				.then(literal("material")
						.then(word("material", HiltMaterial.values())
								.executes(ctx -> material(ctx, null, parse(ctx, "material", HiltMaterial.values())))
								.then(word("slot", HiltParts.Slot.values())
										.executes(ctx -> material(ctx, parse(ctx, "slot", HiltParts.Slot.values()), parse(ctx, "material", HiltMaterial.values()))))))
				.then(literal("stats").executes(SaberCommands::stats))
				.then(literal("stamina")
						.executes(ctx -> stamina(ctx, false))
						.then(literal("refill").executes(ctx -> stamina(ctx, true)))
						.then(literal("set").then(argument("amount", FloatArgumentType.floatArg(0f))
								.executes(ctx -> setStamina(ctx, FloatArgumentType.getFloat(ctx, "amount"))))))
				.then(literal("form")
						.then(word("form", SaberForm.values())
								.executes(ctx -> form(ctx, parse(ctx, "form", SaberForm.values())))))
				.then(literal("combo").executes(SaberCommands::combo))
				.then(literal("recall").executes(SaberCommands::recall))
				.then(literal("throw").executes(SaberCommands::throwSaber))
				.then(literal("lunge").executes(SaberCommands::lunge))
				.then(literal("spar")
						.executes(ctx -> spar(ctx, SparRules.Goal.YIELD, 1))
						.then(word("goal", SparRules.Goal.values())
								.executes(ctx -> spar(ctx, parse(ctx, "goal", SparRules.Goal.values()), 3))
								.then(argument("count", IntegerArgumentType.integer(1, 20))
										.executes(ctx -> spar(ctx, parse(ctx, "goal", SparRules.Goal.values()), IntegerArgumentType.getInteger(ctx, "count"))))))));
	}

	private static <E extends Enum<E>> RequiredArgumentBuilder<CommandSourceStack, String> word(String name, E[] values) {
		List<String> ids = Arrays.stream(values).map(SaberCommands::id).toList();
		return argument(name, StringArgumentType.word()).suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ids, builder));
	}

	/** {@code part <slot> <variant> [<material>]} for one slot's variants. */
	private static <E extends Enum<E> & HiltParts.Part> LiteralArgumentBuilder<CommandSourceStack> partBranch(String slot, E[] values) {
		return literal(slot).then(word("variant", values)
				.executes(ctx -> givePart(ctx, parse(ctx, "variant", values), HiltMaterial.STEEL))
				.then(word("material", HiltMaterial.values())
						.executes(ctx -> givePart(ctx, parse(ctx, "variant", values), parse(ctx, "material", HiltMaterial.values())))));
	}

	private static String id(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static <E extends Enum<E>> E parse(CommandContext<CommandSourceStack> ctx, String name, E[] values) throws CommandSyntaxException {
		String raw = StringArgumentType.getString(ctx, name);

		for (E value : values) {
			if (id(value).equals(raw)) {
				return value;
			}
		}

		throw new SimpleCommandExceptionType(
				Component.translatable("commands.oldrepublic.saber.unknown", name, raw)).create();
	}

	private static int giveConfigured(CommandContext<CommandSourceStack> ctx, HiltMaterial material) throws CommandSyntaxException {
		return give(ctx, parse(ctx, "color", BladeColor.values()), parse(ctx, "emitter", Emitter.values()),
				parse(ctx, "grip", Grip.values()), parse(ctx, "pommel", Pommel.values()), material);
	}

	private static int give(CommandContext<CommandSourceStack> ctx, BladeColor color, Emitter emitter, Grip grip, Pommel pommel, HiltMaterial material)
			throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ItemStack saber = SaberItems.buildSaber(attunement(player, color), color, emitter, grip, pommel, new HiltMaterials(material, material, material),
				SaberToggle.balance());
		player.getInventory().placeItemBackInInventory(saber);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.give", color.displayName()), false);
		return 1;
	}

	private static int crystal(CommandContext<CommandSourceStack> ctx, BladeColor color) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		player.getInventory().placeItemBackInInventory(SaberAssembly.crystalStack(attunement(player, color)));
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.crystal", color.displayName()), false);
		return 1;
	}

	private static int parts(CommandContext<CommandSourceStack> ctx, HiltMaterial material) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();

		for (HiltParts.Part[] group : new HiltParts.Part[][] {Emitter.values(), Grip.values(), Pommel.values()}) {
			for (HiltParts.Part part : group) {
				// A part with no item of its own would only repeat its stand-in; legendary parts come with their quality (11.1).
				if (SaberItems.hasItem(part)) {
					player.getInventory().placeItemBackInInventory(SaberItems.partStack(part, material));
				}
			}
		}

		ctx.getSource().sendSuccess(() -> material == HiltMaterial.STEEL ? Component.translatable("commands.oldrepublic.saber.parts")
				: Component.translatable("commands.oldrepublic.saber.parts.material", Component.translatable(material.translationKey())), false);
		return 1;
	}

	/** One hilt part in {@code material} (MILESTONE_7.md 3.1); a legendary part comes with its quality. */
	private static int givePart(CommandContext<CommandSourceStack> ctx, HiltParts.Part part, HiltMaterial material) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ItemStack stack = SaberItems.partStack(part, material);
		Component name = stack.getHoverName();
		player.getInventory().placeItemBackInInventory(stack);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.part", name), false);
		return 1;
	}

	/**
	 * Makes the saber in the main hand of {@code material}: every part, or the part in {@code slot}. A
	 * saberstaff's pommel is a placeholder it never counts, so it stays steel (as {@code SaberItems.buildStaff}).
	 */
	private static int material(CommandContext<CommandSourceStack> ctx, HiltParts.@Nullable Slot slot, HiltMaterial material) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ItemStack saber = player.getMainHandItem();

		if (!(saber.getItem() instanceof LightsaberItem) || saber.getItem() instanceof TrainingSaberItem) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.saber.material.none"));
			return 0;
		}

		SaberConfig config = SaberComponents.config(saber);
		HiltMaterials materials = slot == null ? new HiltMaterials(material, material, material) : config.materials().with(slot, material);

		if (saber.getItem() instanceof SaberStaffItem) {
			materials = materials.with(HiltParts.Slot.POMMEL, HiltMaterial.STEEL);
		}

		saber.set(SaberComponents.SABER_CONFIG, config.withMaterials(materials));
		SaberToggle.refreshModifiers(saber, SaberComponents.isIgnited(saber));
		Component held = saber.getHoverName();
		Component name = Component.translatable(material.translationKey());
		ctx.getSource().sendSuccess(() -> slot == null ? Component.translatable("commands.oldrepublic.saber.material", held, name)
				: Component.translatable("commands.oldrepublic.saber.material.slot", held, slot.id(), name), false);
		return 1;
	}

	/** The main hand saber's numbers before the form and passives (its parts, materials, length and tunings), and its set. */
	private static int stats(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		ItemStack saber = player.getMainHandItem();

		if (!(saber.getItem() instanceof LightsaberItem)) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.saber.material.none"));
			return 0;
		}

		SaberStats s = SaberStats.of(saber, SaberToggle.balance());
		SaberConfig config = SaberComponents.config(saber);
		boolean staff = saber.getItem() instanceof SaberStaffItem;
		HiltSet set = HiltSet.active(config, staff);
		Component held = saber.getHoverName();
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.stats", held, HiltTraits.number((float) s.damage()),
				HiltTraits.number((float) s.attackSpeed()), HiltTraits.number((float) s.reachBonus()), HiltTraits.number(s.maxStamina()),
				HiltTraits.number(s.parryTicks()), HiltTraits.number(s.guardDrainPct()), HiltTraits.number(s.forceCostPct())), false);
		ctx.getSource().sendSuccess(() -> set == null ? Component.translatable("commands.oldrepublic.saber.stats.none")
				: Component.translatable("commands.oldrepublic.saber.stats.set", HiltSetEffects.setName(set), String.valueOf(HiltSet.count(config, set, staff))), false);
		return 1;
	}

	private static int stamina(CommandContext<CommandSourceStack> ctx, boolean refill) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();

		if (refill) {
			SaberGuard.refill(player);
		}

		GuardSnapshot snapshot = SaberGuard.snapshot(player);

		if (snapshot == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.saber.stamina.none"));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.stamina",
				String.format("%.1f", snapshot.stamina()), String.format("%.0f", snapshot.max()), String.valueOf(snapshot.broken())), false);
		return 1;
	}

	private static int setStamina(CommandContext<CommandSourceStack> ctx, float amount) throws CommandSyntaxException {
		SaberGuard.set(ctx.getSource().getPlayerOrException(), amount);
		return stamina(ctx, false);
	}

	private static int form(CommandContext<CommandSourceStack> ctx, SaberForm form) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();

		if (form != SaberForm.UNFORMED && Skills.rank(player, form.nodeId()) <= 0) {
			Skills.grant(player, form.nodeId());
		}

		// The normal change (salute, switch time); set at once if it is refused (in combat).
		if (!SaberForms.request(player, form.nodeId())) {
			Skills.setActiveForm(player, form.nodeId());
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.form", form.displayName()), false);
		return 1;
	}

	private static int combo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		SaberBalance b = SaberToggle.balance();
		int step = ComboTracker.step(player.getUUID(), player.level().getGameTime(), SaberPassives.form(player).modifiers(b).comboWindow());
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.combo", step), false);
		return step;
	}

	private static int recall(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		SaberThrow.recall(ctx.getSource().getPlayerOrException());
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.recall"), false);
		return 1;
	}

	/** Throws the held saber with every Saber Throw rule (the ability normally sits in a slot). */
	private static int throwSaber(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		return SaberThrow.throwSaber(ctx.getSource().getPlayerOrException()) ? 1 : 0;
	}

	/** Lunges at the nearest valid target with every lunge rule (normally Sprint + Attack). */
	private static int lunge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		float range = SaberPassives.form(player).modifiers(SaberToggle.balance()).lungeRange() + 0.5f;
		LivingEntity target = null;

		for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range))) {
			if (SaberTechniques.validLungeTarget(player, candidate, range) && (target == null || player.distanceTo(candidate) < player.distanceTo(target))) {
				target = candidate;
			}
		}

		if (target == null || !SaberTechniques.startLunge(player, target)) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.saber.lunge.failed"));
			return 0;
		}

		return 1;
	}

	private static int spar(CommandContext<CommandSourceStack> ctx, SparRules.Goal goal, int count) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		Mob partner = null;
		double best = 16 * 16;

		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16), m -> m instanceof SaberDuelist && m.isAlive())) {
			double d = mob.distanceToSqr(player);

			if (d < best && NpcGuard.state(mob) != null) {
				best = d;
				partner = mob;
			}
		}

		if (partner == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.saber.spar.none"));
			return 0;
		}

		DuelistProfile profile = NpcGuard.state(partner).profile();
		float yield = profile.yieldFraction() > 0f ? profile.yieldFraction() : 0.3f;
		Mob sparred = partner;
		boolean started = Spar.start(player, partner, new SparRules(goal, count, yield, 1f), result -> player.sendSystemMessage(
				Component.translatable("commands.oldrepublic.saber.spar.result", sparred.getDisplayName(), result.name())));

		if (!started) {
			ctx.getSource().sendFailure(Component.translatable("commands.oldrepublic.saber.spar.busy"));
			return 0;
		}

		ctx.getSource().sendSuccess(() -> Component.translatable("commands.oldrepublic.saber.spar.start", sparred.getDisplayName()), false);
		return 1;
	}

	private static CrystalAttunement attunement(ServerPlayer player, BladeColor color) {
		return new CrystalAttunement(player.getUUID(), player.getName().getString(), color.rgb());
	}

	private SaberCommands() {
	}
}
