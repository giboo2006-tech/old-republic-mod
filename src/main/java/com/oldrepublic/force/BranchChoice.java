package com.oldrepublic.force;

import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Server-side rules for choosing a Force Push branch. */
public final class BranchChoice {
	/** Outcome of a branch request; every value except {@link #OK} has an explanation message. */
	public enum Result {
		OK,
		NOT_AWAKENED,
		NOT_LEARNED,
		LEVEL_TOO_LOW,
		INVALID,
		ALREADY_CHOSEN,
		NOT_ENOUGH_POINTS;

		public String messageKey() {
			return "message.oldrepublic.force.branch." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Skill points needed to switch to a branch from {@code currentBranchId}. */
	public static int cost(String currentBranchId, PushBalance b) {
		return currentBranchId == null || currentBranchId.isEmpty() ? b.branchFirstCost : b.branchRechooseCost;
	}

	/** Pure validation, without side effects. */
	public static Result check(ForceData data, PushBranch target, PushBalance b) {
		if (!data.isAwakened()) {
			return Result.NOT_AWAKENED;
		}

		if (!data.hasAbility(ForcePush.ABILITY_ID)) {
			return Result.NOT_LEARNED;
		}

		if (data.level < b.branchUnlockLevel) {
			return Result.LEVEL_TOO_LOW;
		}

		if (target == PushBranch.NONE) {
			return Result.INVALID;
		}

		if (target.id().equals(data.pushBranch)) {
			return Result.ALREADY_CHOSEN;
		}

		if (data.skillPoints < cost(data.pushBranch, b)) {
			return Result.NOT_ENOUGH_POINTS;
		}

		return Result.OK;
	}

	/** Validates and applies a request from the client. */
	public static Result apply(ServerPlayer player, String branchId) {
		PushBalance b = ForcePush.balance();
		ForceData data = Progression.get(player);
		PushBranch target = PushBranch.byId(branchId);
		Result result = check(data, target, b);

		if (result != Result.OK) {
			player.sendOverlayMessage(Component.translatable(result.messageKey(), b.branchUnlockLevel, cost(data.pushBranch, b)));
			return result;
		}

		int cost = cost(data.pushBranch, b);
		data.skillPoints -= cost;
		data.pushBranch = target.id();
		Progression.commit(player, data);
		player.sendSystemMessage(Component.translatable("message.oldrepublic.force.branch_chosen",
				Component.translatable("force.oldrepublic.branch." + target.id()), cost));
		return result;
	}

	private BranchChoice() {
	}
}
