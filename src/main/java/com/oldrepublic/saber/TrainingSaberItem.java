package com.oldrepublic.saber;

import java.util.UUID;
import java.util.function.Consumer;

import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.throwing.ThrownSaber;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The training saber, {@code training_saber} and {@code training_saber_red} (spec MILESTONE_4.md
 * 5.5): a low-power amber (or red) blade for sparring, with a fixed config around no crystal. It
 * blocks, parries and deflects like a lightsaber with the same guard and costs, deals
 * {@code saber.trainingSaberDamage}, never kills a player (a killing blow leaves them at 1 health,
 * {@link #spares}) and cannot be taken apart (the workbench's saber slot refuses it).
 */
public class TrainingSaberItem extends LightsaberItem {
	/** The crystal that belongs to nobody: a training saber has no owner. */
	public static final CrystalAttunement NO_CRYSTAL = new CrystalAttunement(new UUID(0L, 0L), "", BladeColor.AMBER.rgb());

	private final BladeColor color;

	public TrainingSaberItem(Properties properties, BladeColor color) {
		super(properties);
		this.color = color;
	}

	public BladeColor color() {
		return color;
	}

	/** The fixed config of a training saber with a {@code color} blade: the training emitter, a wrapped grip, a balanced pommel. */
	public static SaberConfig config(BladeColor color) {
		return new SaberConfig(new CrystalAttunement(NO_CRYSTAL.owner(), "", color.rgb()), color, Emitter.GUARDIAN, Grip.WRAPPED, Pommel.BALANCED);
	}

	/** The stats of a training saber: its config's, with the fixed training damage. */
	public static SaberStats stats(BladeColor color, SaberBalance b) {
		SaberStats s = SaberStats.of(config(color), b);
		return new SaberStats(b.trainingSaberDamage, s.attackSpeed(), s.reachBonus(), s.maxStamina(), s.parryTicks());
	}

	/** The default attribute modifiers (retracted) of a training saber, from the built-in balance defaults. */
	public static ItemAttributeModifiers defaultModifiers(BladeColor color) {
		SaberBalance b = new SaberBalance();
		return stats(color, b).modifiers(false, b);
	}

	public static boolean isTraining(ItemStack stack) {
		return stack.getItem() instanceof TrainingSaberItem;
	}

	/**
	 * Whether {@code source} is a training saber's blow: a melee hit by a player holding one, or a
	 * thrown training saber. Such a blow never kills a player.
	 */
	public static boolean isTrainingBlow(DamageSource source) {
		if (source.getDirectEntity() instanceof ThrownSaber thrown) {
			return isTraining(thrown.saber());
		}

		// The striking hand's saber: a dual-wielder's off-hand strike (MILESTONE_5.md 10.2) counts its own blade.
		return source.getDirectEntity() instanceof LivingEntity attacker && source.getEntity() == attacker && isTraining(SaberCombat.weapon(attacker));
	}

	/**
	 * ALLOW_DEATH: a player struck down by a training saber is left at 1 health instead (5.5).
	 * Returns true when the death was refused.
	 */
	public static boolean spares(LivingEntity victim, DamageSource source) {
		if (!(victim instanceof ServerPlayer) || !isTrainingBlow(source)) {
			return false;
		}

		victim.setHealth(1f);
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.oldrepublic.lightsaber.color", color.displayName()).withStyle(style -> style.withColor(color.rgb())));
		tooltip.accept(Component.translatable("item.oldrepublic.training_saber.desc").withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.oldrepublic.training_saber.safe").withStyle(ChatFormatting.DARK_AQUA));
		tooltip.accept(Component.translatable(SaberComponents.state(stack).ignited() ? "item.oldrepublic.lightsaber.ignited" : "item.oldrepublic.lightsaber.retracted")
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
