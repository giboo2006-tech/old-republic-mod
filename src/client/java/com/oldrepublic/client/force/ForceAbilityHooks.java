package com.oldrepublic.client.force;

import com.oldrepublic.client.ability.ClientAbilityHook;
import com.oldrepublic.client.ability.ClientAbilityHooks;
import com.oldrepublic.force.ForcePowers;
import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.heal.ForceHeal;
import com.oldrepublic.force.jump.ForceJump;
import com.oldrepublic.force.lightning.ForceLightning;
import com.oldrepublic.force.pull.ForcePull;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The client side of the five Force powers' slot keys (replaces the Milestone 1 {@code PushInput}).
 * The skills feature sends the intents; these hooks only check what the client can know (awake,
 * learned, off cooldown) to answer instantly, and predict the caster's own pose, sounds and charge
 * visuals so the key feels immediate. Every prediction is confirmed by the server's effect event or
 * dropped after {@link PushAnimations#CONFIRM_TICKS} (and at once on the own energy-fail event).
 */
final class ForceAbilityHooks {
	/** Ticks the drawn push pose waits for the server's answer to a release. */
	private static final int RELEASE_WAIT_TICKS = 10;

	static void init() {
		ClientAbilityHooks.register(ForcePush.ABILITY_ID, new Hook(ForcePush.ABILITY_ID) {
			@Override
			public void onPress(Minecraft mc) {
				PushAnimations.startPredicted(mc.player.getId());
			}

			@Override
			public void onRelease(Minecraft mc) {
				PushAnimations.awaitRelease(mc.player.getId(), RELEASE_WAIT_TICKS);
			}

			@Override
			public void onCancel(Minecraft mc) {
				PushAnimations.stopCharge(mc.player.getId());
			}
		});

		ClientAbilityHooks.register(ForcePull.ID, new Hook(ForcePull.ID) {
			@Override
			public void onPress(Minecraft mc) {
				PowerAnimations.startPredicted(mc.player.getId(), PowerAnimations.Kind.PULL, PowerAnimations.PULL_TICKS, 0);
			}
		});

		ClientAbilityHooks.register(ForceJump.ID, new Hook(ForceJump.ID) {
			@Override
			public void onPress(Minecraft mc) {
				LocalPlayer player = mc.player;

				if (player.onGround()) {
					JumpFx.startGather(player, true);
				}
			}

			@Override
			public void onCancel(Minecraft mc) {
				PowerAnimations.stop(mc.player.getId(), PowerAnimations.Kind.JUMP_GATHER);
			}
		});

		ClientAbilityHooks.register(ForceHeal.ID, new Hook(ForceHeal.ID) {
			@Override
			public void onPress(Minecraft mc) {
				LocalPlayer player = mc.player;

				if (!player.isSprinting() && !player.isUsingItem()) {
					HealFx.startChannel(player, true);
				}
			}

			@Override
			public void onCancel(Minecraft mc) {
				PowerAnimations.stop(mc.player.getId(), PowerAnimations.Kind.HEAL);
			}
		});

		ClientAbilityHooks.register(ForceLightning.ID, new Hook(ForceLightning.ID) {
			@Override
			public void onPress(Minecraft mc) {
				LightningFx.predict(mc.player);
			}

			@Override
			public void onCancel(Minecraft mc) {
				PowerAnimations.stop(mc.player.getId(), PowerAnimations.Kind.LIGHTNING);
			}
		});
	}

	/** The shared precheck: awake, learned and off cooldown, answered from the synced data. */
	private abstract static class Hook implements ClientAbilityHook {
		private final String abilityId;

		Hook(String abilityId) {
			this.abilityId = abilityId;
		}

		@Override
		public boolean precheck(Minecraft mc) {
			LocalPlayer player = mc.player;

			if (player == null || !player.isAlive() || player.isSpectator()) {
				return false;
			}

			ForceData data = player.getAttached(ForceAttachments.FORCE);

			if (data == null || !data.isAwakened()) {
				player.sendOverlayMessage(Component.translatable("message.oldrepublic.not_awakened"));
				return false;
			}

			if (!ForcePowers.knows(player, data, abilityId)) {
				player.sendOverlayMessage(Component.translatable("message.oldrepublic.force.not_learned"));
				return false;
			}

			if (data.cooldown(abilityId) > 0) {
				player.sendOverlayMessage(Component.translatable("message.oldrepublic.force.cooldown"));
				return false;
			}

			return true;
		}
	}

	private ForceAbilityHooks() {
	}
}
