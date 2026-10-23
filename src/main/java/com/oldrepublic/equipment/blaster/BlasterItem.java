package com.oldrepublic.equipment.blaster;

import java.util.function.Consumer;

import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.equipment.ArmourItem;
import com.oldrepublic.registry.M4Components;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.Level;

/**
 * A player's blaster (spec MILESTONE_4.md 5.1): {@code blaster_pistol} and {@code blaster_rifle}
 * (M1 and M2 ids, registered in place by the lead's {@code EnemyItems}; troopers keep holding them),
 * {@code heavy_repeater} and the named {@code kaast_longshot}; the {@code scatter_blaster} and the
 * {@code blaster_carbine} of MILESTONE_7.md 9.1 ({@code M7Items}).
 * <p>
 * Use fires from the main hand: the first bolt leaves on the tick of the press, and holding use
 * fires again at the weapon's rate ({@link #onUseTick}); the server does all of it
 * ({@link Blasters}). The constructor completes the properties it is given: durability, the
 * movement while firing ({@code use_effects}), one durability per bash ({@code weapon}) and the bash
 * attack speed. The bash damage is a player-only bonus ({@link #getAttackDamageBonus}), so a trooper
 * holding a rifle keeps its own melee numbers.
 */
public class BlasterItem extends Item {
	/** The five blasters. Stored by ordinal; append only. */
	public enum Kind {
		PISTOL,
		RIFLE,
		REPEATER,
		/** The scatter blaster (MILESTONE_7.md 9.1). Appended: ordinals are stable, and a balance array without its entry gives it the last one. */
		SCATTER,
		/** The blaster carbine (9.1). */
		CARBINE
	}

	/** Registration-time numbers (items exist before the balance file is read). */
	private static final BlasterBalance DEFAULTS = new BlasterBalance();
	/** Bash attack speed (attacks per second); the player's base is 4. */
	private static final double BASH_SPEED = 1.0;
	/** Holding use keeps the trigger down for up to an hour. */
	private static final int USE_DURATION = 72000;

	private final Kind kind;

	public BlasterItem(Kind kind, Item.Properties properties) {
		this(kind, properties, Quality.STANDARD);
	}

	/**
	 * A blaster built at {@code builtIn} quality (a named drop): its registered durability carries the
	 * quality's multiplier. The stack's quality component itself is set by the caller's properties.
	 */
	public BlasterItem(Kind kind, Item.Properties properties, Quality builtIn) {
		super(complete(kind, properties, builtIn));
		this.kind = kind;
	}

	private static Item.Properties complete(Kind kind, Item.Properties properties, Quality builtIn) {
		float slow = switch (kind) {
			case RIFLE -> DEFAULTS.rifleSlowPct;
			case REPEATER -> DEFAULTS.repeaterSlowPct;
			case SCATTER -> DEFAULTS.scatterSlowPct;
			default -> 0f;
		};
		ItemAttributeModifiers attributes = ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, BASH_SPEED - 4.0, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.build();
		return properties
				.durability(Math.round(BlasterBalance.of(DEFAULTS.durability, kind) * builtIn.durability()))
				.attributes(attributes)
				// No sprinting with the repeater; the rifle and the scatter slow a little, the pistol and the carbine not at all.
				.component(DataComponents.USE_EFFECTS, new UseEffects(kind != Kind.REPEATER, true, 1f - slow / 100f))
				.component(DataComponents.WEAPON, new Weapon(1));
	}

	public Kind kind() {
		return kind;
	}

	/** Fires from the main hand; the off hand does nothing with a blaster. */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);

		if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel) {
			Blasters.pressTrigger(serverPlayer, stack, this);
		}

		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	/** Holding the trigger: the next bolt at the weapon's rate. */
	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
		if (entity instanceof ServerPlayer player && level instanceof ServerLevel) {
			Blasters.holdTrigger(player, stack, this);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remainingUseDuration) {
		if (entity instanceof ServerPlayer player && level instanceof ServerLevel) {
			Blasters.releaseTrigger(player, this);
		}

		return false;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		// The aim pose is the mod's own (client.equipment.BlasterPoses), not a vanilla one.
		return ItemUseAnimation.NONE;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return USE_DURATION;
	}

	/** The bash: a player hits for {@code bashDamage} in all (base 1 plus this bonus). */
	@Override
	public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
		return source.getEntity() instanceof Player ? Math.max(0f, DEFAULTS.bashDamage - 1f) : 0f;
	}

	/** A pistol whip staggers. */
	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);

		if (attacker instanceof ServerPlayer player) {
			Blasters.onBash(player, target);
		}
	}

	/** Heat changes every shot: never replay the re-equip bob for it (spec 5.1). */
	@Override
	public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		BlasterBalance b = DEFAULTS;
		float quality = Crafting.quality(stack).weapon();
		float damage = BlasterRules.boltDamage(kind, quality, b);
		// Per second with the trigger held: everything one pull sends (a whole blast or burst) at the rate.
		String perSecond = ArmourItem.number(20f * BlasterRules.pullDamage(kind, quality, b) / BlasterBalance.of(b.rate, kind));

		if (kind == Kind.SCATTER || kind == Kind.CARBINE) {
			int count = kind == Kind.SCATTER ? BlasterRules.pellets(kind, b) : BlasterRules.burst(kind, b);
			tooltip.accept(Component.translatable("equipment.oldrepublic.blaster.stats." + kind.name().toLowerCase(java.util.Locale.ROOT),
					count, ArmourItem.number(damage), perSecond).withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.accept(Component.translatable("equipment.oldrepublic.blaster.stats", ArmourItem.number(damage), perSecond)
					.withStyle(ChatFormatting.GRAY));
		}

		tooltip.accept(Component.translatable("equipment.oldrepublic.blaster.special." + kind.name().toLowerCase(java.util.Locale.ROOT))
				.withStyle(ChatFormatting.DARK_GRAY));
		tooltip.accept(Component.translatable(kind == Kind.PISTOL ? "equipment.oldrepublic.blaster.one_hand" : "equipment.oldrepublic.blaster.two_hands")
				.withStyle(ChatFormatting.DARK_GRAY));

		if (stack.has(M4Components.NAMED)) {
			tooltip.accept(Component.translatable("equipment.oldrepublic.blaster.fixed_barrel").withStyle(ChatFormatting.GOLD));
		}
	}
}
