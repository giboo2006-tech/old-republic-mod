package com.oldrepublic.client.force;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.force.ForcePush;
import com.oldrepublic.force.PushBranch;
import com.oldrepublic.force.network.ChooseBranchPayload;
import com.oldrepublic.force.network.PushInfoPayload;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.progression.Progression;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The Force screen (default K): attunement level, XP, Resonance, skill points, energy, Push mastery,
 * and the three Push branches with their exact numbers. Choosing sends a request; the server
 * validates it and the synced data refreshes the screen.
 */
public final class ForceScreen extends Screen {
	private static final int TEXT = 0xFFE6E0D2;
	private static final int MUTED = 0xFFA89F8C;
	private static final int ACCENT = 0xFF8FB4D9;
	private static final int BUTTON_WIDTH = 124;
	private static final PushBranch[] BRANCHES = {PushBranch.REPULSE_WAVE, PushBranch.FOCUSED_BLAST, PushBranch.DEFLECTING_PULSE};

	/** Snapshot of what the widgets were built from, to rebuild them when the synced data changes. */
	private String builtFrom = "";

	private ForceScreen() {
		super(Component.translatable("screen.oldrepublic.force.title"));
	}

	/** Opens the screen, or says nothing stirs yet when the player is not awakened. */
	static void open(Minecraft mc, LocalPlayer player) {
		ForceData data = player.getAttached(ForceAttachments.FORCE);

		if (data == null || !data.isAwakened()) {
			player.sendOverlayMessage(Component.translatable("message.oldrepublic.not_awakened"));
			return;
		}

		mc.gui.setScreen(new ForceScreen());
	}

	private @Nullable ForceData data() {
		return minecraft.player == null ? null : minecraft.player.getAttached(ForceAttachments.FORCE);
	}

	@Override
	protected void init() {
		ForceData data = data();
		builtFrom = signature(data);
		int total = BRANCHES.length * BUTTON_WIDTH + (BRANCHES.length - 1) * 6;
		int x = (width - total) / 2;
		int y = height / 2 + 34;

		for (PushBranch branch : BRANCHES) {
			boolean current = data != null && branch.id().equals(data.pushBranch);
			Component label = Component.translatable("force.oldrepublic.branch." + branch.id());
			Button button = Button.builder(current ? Component.literal("> ").append(label).append(" <") : label, b -> choose(branch))
					.bounds(x, y, BUTTON_WIDTH, 20)
					.tooltip(Tooltip.create(tooltip(branch, data)))
					.build();
			button.active = data != null && canChoose(branch, data);
			addRenderableWidget(button);
			x += BUTTON_WIDTH + 6;
		}

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(width / 2 - 50, y + 30, 100, 20)
				.build());
	}

	@Override
	public void tick() {
		super.tick();

		if (!builtFrom.equals(signature(data()))) {
			rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		ForceData data = data();

		if (data == null) {
			return;
		}

		int cx = width / 2;
		int y = height / 2 - 92;
		graphics.centeredText(font, title, cx, y, ACCENT);
		y += 18;
		graphics.centeredText(font, Component.translatable("screen.oldrepublic.force.level", data.level, data.xp, data.xpToNext), cx, y, TEXT);
		y += 12;
		graphics.centeredText(font, Component.translatable("screen.oldrepublic.force.resonance", data.resonance), cx, y, TEXT);
		y += 12;
		graphics.centeredText(font, Component.translatable("screen.oldrepublic.force.energy", Math.round(data.energy), Math.round(data.maxEnergy)), cx, y, TEXT);
		y += 12;
		graphics.centeredText(font, Component.translatable("screen.oldrepublic.force.skill_points", data.skillPoints), cx, y, TEXT);
		y += 18;

		if (data.hasAbility(ForcePush.ABILITY_ID)) {
			int points = data.mastery(ForcePush.ABILITY_ID);
			graphics.centeredText(font, Component.translatable("screen.oldrepublic.force.push_mastery", Progression.masteryRank(points), points), cx, y, TEXT);
		} else {
			graphics.centeredText(font, Component.translatable("screen.oldrepublic.force.push_unknown"), cx, y, MUTED);
		}

		y += 12;
		PushBranch branch = PushBranch.byId(data.pushBranch);
		Component branchName = branch == PushBranch.NONE
				? Component.translatable("force.oldrepublic.branch.none")
				: Component.translatable("force.oldrepublic.branch." + branch.id());
		graphics.centeredText(font, Component.translatable("screen.oldrepublic.force.current_branch", branchName), cx, y, TEXT);
		y += 18;
		graphics.centeredText(font, branchRule(data), cx, y, MUTED);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Component branchRule(ForceData data) {
		PushInfoPayload info = ClientPushInfo.get();
		int unlock = info != null ? info.unlockLevel() : 3;
		int first = info != null ? info.firstCost() : 1;
		int again = info != null ? info.rechooseCost() : 3;

		if (data.level < unlock) {
			return Component.translatable("screen.oldrepublic.force.branch_locked", unlock);
		}

		return Component.translatable("screen.oldrepublic.force.branch_rule", first, again);
	}

	private static boolean canChoose(PushBranch branch, ForceData data) {
		PushInfoPayload info = ClientPushInfo.get();
		int unlock = info != null ? info.unlockLevel() : 3;
		int cost = data.pushBranch.isEmpty() ? (info != null ? info.firstCost() : 1) : (info != null ? info.rechooseCost() : 3);
		return data.hasAbility(ForcePush.ABILITY_ID) && data.level >= unlock && !branch.id().equals(data.pushBranch) && data.skillPoints >= cost;
	}

	private static Component tooltip(PushBranch branch, @Nullable ForceData data) {
		PushInfoPayload info = ClientPushInfo.get();
		String key = "screen.oldrepublic.force.tooltip." + branch.id();

		if (info == null) {
			return Component.translatable(key + ".short").append("\n").append(Component.translatable("screen.oldrepublic.force.tooltip.no_data"));
		}

		Component numbers = switch (branch) {
			case REPULSE_WAVE -> Component.translatable(key, f(info.repulseCone()), f(info.repulseRange()),
					f(info.knockbackMin() * info.repulseKnockback()), f(info.knockbackMax() * info.repulseKnockback()),
					f(info.staggerSeconds()), f(info.cooldownSeconds()), f(info.costMin()), f(info.costMax()));
			case FOCUSED_BLAST -> Component.translatable(key, f(info.focusedCone()), f(info.focusedRange()),
					f(info.knockbackMin() * info.focusedKnockback()), f(info.knockbackMax() * info.focusedKnockback()),
					f(info.slamDamageMin()), f(info.slamDamageMax()), f(info.cooldownSeconds()), f(info.costMin()), f(info.costMax()));
			case DEFLECTING_PULSE -> Component.translatable(key, f(info.deflectRadius()),
					f(info.knockbackMin() * info.deflectKnockback()), f(info.knockbackMax() * info.deflectKnockback()),
					f(info.deflectCooldownSeconds()), f(info.costMin()), f(info.costMax()));
			case NONE -> Component.empty();
		};

		int cost = data == null || data.pushBranch.isEmpty() ? info.firstCost() : info.rechooseCost();
		return Component.translatable(key + ".short").append("\n").append(numbers).append("\n")
				.append(Component.translatable("screen.oldrepublic.force.tooltip.base", f(info.baseCone()), f(info.baseRange())))
				.append("\n").append(Component.translatable("screen.oldrepublic.force.tooltip.cost", cost));
	}

	private static String f(float value) {
		return Math.abs(value - Math.round(value)) < 0.05f ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
	}

	private static String signature(@Nullable ForceData data) {
		return data == null ? "" : data.level + "|" + data.skillPoints + "|" + data.pushBranch + "|" + data.abilities.size() + "|" + (ClientPushInfo.get() != null);
	}

	private void choose(PushBranch branch) {
		ClientPlayNetworking.send(new ChooseBranchPayload(branch.id()));
	}
}
