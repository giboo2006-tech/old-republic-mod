package com.oldrepublic.equipment.melee;

import java.util.Locale;
import java.util.function.Consumer;

import com.oldrepublic.crafting.Quality;
import com.oldrepublic.equipment.ArmourItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.Level;

/**
 * A vibroweapon (spec MILESTONE_4.md 5.2): the {@code vibroblade} (M2 id, registered in place by the
 * lead's {@code EnemyItems}; the Poacher Captain keeps holding it), the {@code vibrosword}, and the
 * {@code electrostaff} ({@link ElectrostaffItem}, 5.3). The constructor completes the properties it
 * is given: damage, attack speed, reach, durability and one durability per hit.
 * <p>
 * Right-click raises a guard that blocks part of every melee hit from the front, paid from the M2
 * guard stamina ({@link MeleeCombat}); it never blocks bolts and never parries. The blade's flurry
 * and the sword's cleave are in {@link MeleeCombat} too.
 */
public class VibroWeaponItem extends Item {
	/** The weapons. Append only. */
	public enum Kind {
		BLADE,
		SWORD,
		STAFF,
		/** The vibro-axe (MILESTONE_7.md 9.1). Appended: ordinals are stable. */
		AXE,
		/** The force pike (9.1). */
		PIKE
	}

	/** Registration-time numbers (items exist before the balance file is read). */
	private static final MeleeBalance DEFAULTS = new MeleeBalance();
	/** The player's base attack damage and speed, which item modifiers add to. */
	private static final double PLAYER_BASE_DAMAGE = 1.0;
	private static final double PLAYER_BASE_SPEED = 4.0;
	private static final int GUARD_DURATION = 72000;
	/** The staff's longer swing, ticks (the vanilla default is 6). */
	private static final int STAFF_SWING_TICKS = 9;

	private final Kind kind;

	/** The vibroblade, as the lead's {@code EnemyItems} registers it. */
	public VibroWeaponItem(Item.Properties properties) {
		this(Kind.BLADE, properties, Quality.STANDARD);
	}

	public VibroWeaponItem(Kind kind, Item.Properties properties) {
		this(kind, properties, Quality.STANDARD);
	}

	/** A weapon built at {@code builtIn} quality: its registered damage and durability carry the multipliers. */
	public VibroWeaponItem(Kind kind, Item.Properties properties, Quality builtIn) {
		super(complete(kind, properties, builtIn));
		this.kind = kind;
	}

	private static Item.Properties complete(Kind kind, Item.Properties properties, Quality builtIn) {
		MeleeBalance b = DEFAULTS;
		float damage = switch (kind) {
			case BLADE -> b.vibrobladeDamage;
			case SWORD -> b.vibroswordDamage;
			default -> b.staffDamage;
		};
		float speed = switch (kind) {
			case BLADE -> b.vibrobladeSpeed;
			case SWORD -> b.vibroswordSpeed;
			default -> b.staffSpeed;
		};
		float reach = switch (kind) {
			case BLADE -> 0f;
			case SWORD -> b.vibroswordReach;
			default -> b.staffReach;
		};
		int durability = switch (kind) {
			case BLADE -> b.vibrobladeDurability;
			case SWORD -> b.vibroswordDurability;
			default -> b.staffDurability;
		};
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
						damage * builtIn.weapon() - PLAYER_BASE_DAMAGE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
						speed - PLAYER_BASE_SPEED, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);

		if (reach > 0f) {
			// Melee reach reads ENTITY_INTERACTION_RANGE when the item has no attack_range component (AttackRange.defaultFor).
			attributes.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(MeleeCombat.REACH_ID, reach, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND);
		}

		properties.durability(Math.round(durability * builtIn.durability()))
				.attributes(attributes.build())
				.component(DataComponents.WEAPON, new Weapon(1));

		if (kind == Kind.STAFF) {
			properties.component(DataComponents.SWING_ANIMATION, new SwingAnimation(SwingAnimation.DEFAULT.type(), STAFF_SWING_TICKS));
		}

		return properties;
	}

	public Kind kind() {
		return kind;
	}

	/** Raises the guard (main hand only). */
	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);

		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BLOCK;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return GUARD_DURATION;
	}

	/** The blade's flurry bonus on the chain's third hit. */
	@Override
	public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
		return source.getEntity() instanceof ServerPlayer player ? MeleeCombat.attackBonus(player, target, kind) : 0f;
	}

	/** After a hit lands: the flurry, cleave, staff shock and sweep, and the shock edge. */
	@Override
	public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		super.hurtEnemy(stack, target, attacker);

		if (attacker instanceof ServerPlayer player) {
			MeleeCombat.onHit(player, stack, kind, target);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		String id = kind.name().toLowerCase(Locale.ROOT);
		tooltip.accept(Component.translatable("equipment.oldrepublic.melee.guard",
				ArmourItem.number(100f * (1f - MeleeRules.guardThrough(kind, DEFAULTS)))).withStyle(ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("equipment.oldrepublic.melee.special." + id).withStyle(ChatFormatting.DARK_GRAY));

		if (kind == Kind.STAFF) {
			tooltip.accept(Component.translatable("equipment.oldrepublic.melee.blocks_sabers").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
