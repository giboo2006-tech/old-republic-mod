package com.oldrepublic.client.skill;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.ability.network.AbilityInfoPayload;
import com.oldrepublic.ability.network.AbilityStatePayload;
import com.oldrepublic.client.ability.AbilityInput;
import com.oldrepublic.client.ability.ClientAbilityInfo;
import com.oldrepublic.client.force.ForceKeys;
import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.hud.Banners;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.client.ui.UiColors;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.registry.ModSounds;
import com.oldrepublic.skill.SkillData;
import com.oldrepublic.skill.SkillEffects;
import com.oldrepublic.skill.SkillNode;
import com.oldrepublic.skill.SkillTree;
import com.oldrepublic.skill.SkillTreeSet;
import com.oldrepublic.skill.SkillTrees;
import com.oldrepublic.skill.Skills;
import com.oldrepublic.skill.network.OpenSkillsPayload;
import com.oldrepublic.skill.network.SkillTreesPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * Client initialisation for the skills feature: the Force keys, the slot key handling
 * ({@link AbilityInput}), the synced trees and ability numbers, the Force screen (K, or opened by
 * the server for a respec) and the skill sounds and banners. Called from OldRepublicModClient.
 */
public final class SkillClient {
	private static @Nullable ClientLevel lastLevel;
	/** Owned node ranks last seen, to announce nodes learned while the screen is closed. */
	private static @Nullable Map<String, Integer> knownNodes;

	public static void init() {
		ForceKeys.init();

		ClientPlayNetworking.registerGlobalReceiver(SkillTreesPayload.TYPE, (payload, context) -> SkillTrees.setClient(SkillTrees.fromJson(payload.trees())));
		ClientPlayNetworking.registerGlobalReceiver(AbilityInfoPayload.TYPE, (payload, context) -> ClientAbilityInfo.set(payload));
		ClientPlayNetworking.registerGlobalReceiver(AbilityStatePayload.TYPE, (payload, context) -> AbilityInput.onState(payload));
		ClientPlayNetworking.registerGlobalReceiver(OpenSkillsPayload.TYPE, (payload, context) -> ForceScreen.open(context.client(), payload.respec(), null));

		ClientEffects.register(SkillEffects.NODE_LEARNED, SkillClient::onNodeLearned);
		ClientEffects.register(SkillEffects.RESPEC, SkillClient::onRespec);

		ClientTickEvents.END_CLIENT_TICK.register(SkillClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static void tick(Minecraft mc) {
		if (mc.level != lastLevel) {
			// New level (dimension change or join): nothing charges across it.
			lastLevel = mc.level;
			AbilityInput.reset();
		}

		AbilityInput.tick(mc);

		while (ForceKeys.FORCE_SCREEN.consumeClick()) {
			if (mc.gui.screen() == null) {
				ForceScreen.open(mc, false, null);
			}
		}

		watchLearned(mc);
	}

	/** Announces newly owned nodes with a banner while the Force screen is closed (it flashes them itself). */
	private static void watchLearned(Minecraft mc) {
		SkillData data = mc.player == null ? null : Skills.data(mc.player);

		if (data == null) {
			return;
		}

		Map<String, Integer> nodes = new HashMap<>(data.nodes);

		if (knownNodes != null && !(mc.gui.screen() instanceof ForceScreen)) {
			SkillTreeSet trees = SkillTrees.client();

			for (Map.Entry<String, Integer> e : nodes.entrySet()) {
				SkillNode node = trees.node(e.getKey());

				if (node != null && e.getValue() > knownNodes.getOrDefault(e.getKey(), 0)) {
					SkillTree tree = trees.tree(node.tree());
					int accent = tree != null && tree.tint() != 0 ? tree.tint() : UiColors.HOLO;
					Component title = Component.translatable(data.isGranted(node.id()) ? "screen.oldrepublic.skills.banner.granted" : "screen.oldrepublic.skills.banner.learned");
					Banners.show(title, Component.translatable(node.nameKey()), accent);
					ForceScreen.flash(node.id());
					ForceScreen.showTab(node.tree());
				}
			}
		}

		knownNodes = nodes;
	}

	private static void onNodeLearned(Minecraft client, ClientLevel level, EffectEventPayload payload, Entity entity) {
		// Rank 2 and 3 purchases ring a little higher.
		QuestSounds.ui(ModSounds.SKILL_LEARN, 1f + 0.06f * Math.max(0f, payload.intensity() - 1f), 0.8f);
	}

	private static void onRespec(Minecraft client, ClientLevel level, EffectEventPayload payload, Entity entity) {
		QuestSounds.ui(ModSounds.SKILL_RESPEC, 1f, 0.8f);

		if (!(client.gui.screen() instanceof ForceScreen)) {
			ForceScreen.open(client, false, null);
		}
	}

	/** The slot assignment click (the Force screen plays it when a slot changes). */
	static void playSlot() {
		QuestSounds.ui(ModSounds.SKILL_SLOT, 1f, 0.7f);
	}

	private static void reset() {
		SkillTrees.setClient(SkillTreeSet.EMPTY);
		ClientAbilityInfo.reset();
		AbilityInput.reset();
		knownNodes = null;
		lastLevel = null;
	}

	private SkillClient() {
	}
}
