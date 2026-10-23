package com.oldrepublic.force.mindtrick;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.ability.Abilities;
import com.oldrepublic.ability.AbilityInfoKeys;
import com.oldrepublic.ability.ForceAbility;
import com.oldrepublic.dialogue.DialogueActions;
import com.oldrepublic.dialogue.DialogueConditions;
import com.oldrepublic.force.ForceEffects;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.fear.ForceFear;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;
import com.oldrepublic.quest.QuestHooks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The powers' dialogue hooks (MILESTONE_5.md 6.11, 9.6, 9.8):
 * <ul>
 * <li>condition {@code ability:knows} (an ability id): the player knows the power, it is off cooldown and they can afford
 * its base cost now; a failure names the power;</li>
 * <li>actions {@code mind_trick:use} and {@code fear:use}: spend the power's cost and start its cooldown as if cast, with
 * its sound and look at the speaker (Mind Trick) or around the player (Fear); the dialogue then moves on. Each counts as
 * a successful use for mastery.</li>
 * </ul>
 */
public final class PowerDialogues {
	public static final String KNOWS = "ability:knows";
	public static final String MIND_TRICK_USE = "mind_trick:use";
	public static final String FEAR_USE = "fear:use";
	/** Failure reasons: a lang key, then the power's name (translated on the client). */
	static final String REQ_KNOWS = "ability.oldrepublic.req.knows";
	static final String REQ_READY = "ability.oldrepublic.req.ready";
	static final String REQ_ENERGY = "ability.oldrepublic.req.energy";

	/** Registers the condition and the two actions. Call once, from the feature's init. */
	public static void init() {
		DialogueConditions.register(KNOWS, PowerDialogues::knows);
		DialogueActions.register(MIND_TRICK_USE, (player, speaker) -> use(player, speaker, MindTrick.ID));
		DialogueActions.register(FEAR_USE, (player, speaker) -> use(player, speaker, ForceFear.ID));
	}

	/** {@code ability:knows}: null when met, else the reason ({@code key|@name}). */
	static @Nullable String knows(ServerPlayer player, @Nullable Entity speaker, String arg) {
		String id = arg.trim();
		String name = "|@skill.oldrepublic.node." + id + ".name";
		ForceAbility ability = Abilities.get(id);
		ForceData data = Progression.get(player);

		if (ability == null || !ForcePowers.knows(player, data, id)) {
			return REQ_KNOWS + name;
		}

		if (Progression.isOnCooldown(player, id)) {
			return REQ_READY + name;
		}

		float cost = cost(player, ability);
		return player.isCreative() || data.energy + 1.0e-3f >= cost ? null : REQ_ENERGY + name + "|" + Math.round(cost);
	}

	/** The power's base cost for {@code player}, with the cost multipliers (alignment, skills, forms). */
	static float cost(ServerPlayer player, ForceAbility ability) {
		return AbilityInfoKeys.startCost(ability.info()) * ForcePowers.costMultiplier(player, ability.id());
	}

	/** Spends the cost and starts the cooldown as if cast; the dialogue moves on whatever happens here. */
	static void use(ServerPlayer player, @Nullable Entity speaker, String id) {
		ForceAbility ability = Abilities.get(id);

		if (ability == null) {
			return;
		}

		// The condition checked the energy when the option was shown; a player who spent it since still moves on.
		Progression.trySpendEnergy(player, cost(player, ability));
		int cooldown = id.equals(MindTrick.ID) ? MindTrick.balance().cooldownTicks : ForceFear.balance().cooldownTicks;
		Progression.setCooldown(player, id, ForcePowers.cooldown(player, id, cooldown));
		ForcePowers.addMastery(player, id, 1);

		if (id.equals(MindTrick.ID) && speaker instanceof LivingEntity target) {
			EffectEvents.toTracking(target, MindTrick.EFFECT, ForcePowers.chest(target), null, target.getId(), 0f,
					ForceEffects.packCaster(player.getId(), MindTrick.VARIANT_SUCCESS));
		} else if (id.equals(ForceFear.ID)) {
			EffectEvents.toTracking(player, ForceFear.EFFECT, player.getEyePosition(), player.getLookAngle(), player.getId(), 4f, ForceFear.VARIANT_CAST);
		}

		QuestHooks.onAbilityUsed(player, id, 0);
	}

	private PowerDialogues() {
	}
}
