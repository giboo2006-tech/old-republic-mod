package com.oldrepublic.crafting.item;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.crafting.Crafting;
import com.oldrepublic.crafting.CraftingAttachments;
import com.oldrepublic.crafting.CraftingItems;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.Schematics;
import com.oldrepublic.registry.M4Components;
import com.oldrepublic.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A {@code schematic} item (spec MILESTONE_4.md 7.3): one item id whose {@code oldrepublic:schematic}
 * component names what it teaches. Using it teaches the schematic when the player has its
 * discipline; otherwise it says which discipline it needs and is kept. Loot, hunter drops, vendors
 * and story rewards make them with {@link #of}.
 */
public class SchematicItem extends Item {
	public SchematicItem(Properties properties) {
		super(properties);
	}

	/** A schematic item that teaches {@code schematic}. */
	public static ItemStack of(Identifier schematic) {
		ItemStack stack = new ItemStack(CraftingItems.SCHEMATIC);
		stack.set(M4Components.SCHEMATIC, schematic);
		return stack;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		Identifier id = stack.get(M4Components.SCHEMATIC);

		if (!(player instanceof ServerPlayer p)) {
			return InteractionResult.SUCCESS;
		}

		Schematic s = id == null ? null : Schematics.get(id);

		if (s == null) {
			p.sendOverlayMessage(Component.translatable("crafting.oldrepublic.schematic.unreadable").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}

		if (CraftingAttachments.get(p).known().contains(id)) {
			p.sendOverlayMessage(Component.translatable("crafting.oldrepublic.schematic.known").withStyle(ChatFormatting.GRAY));
			return InteractionResult.FAIL;
		}

		if (s.discipline().isPresent() && !Crafting.has(p, s.discipline().get())) {
			p.sendOverlayMessage(Component.translatable("crafting.oldrepublic.schematic.needs", Crafting.name(s.discipline().get())).withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}

		Crafting.teach(p, id);
		p.sendSystemMessage(Component.translatable("crafting.oldrepublic.schematic.learned", s.output().getHoverName()).withStyle(ChatFormatting.GOLD));
		p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), ModSounds.BENCH_DONE, SoundSource.PLAYERS, 0.6f, 1.4f);
		stack.consume(1, p);
		return InteractionResult.SUCCESS;
	}

	@Override
	public Component getName(ItemStack stack) {
		Schematic s = schematic(stack);
		return s == null ? super.getName(stack) : Component.translatable("item.oldrepublic.schematic.of", s.output().getHoverName());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		Schematic s = schematic(stack);

		if (s == null) {
			return;
		}

		Component discipline = s.discipline().map(Crafting::name).orElse(Component.translatable("discipline.oldrepublic.common"));
		tooltip.accept(Component.translatable("item.oldrepublic.schematic.discipline", discipline, s.level()).withStyle(ChatFormatting.GRAY));
	}

	private static @Nullable Schematic schematic(ItemStack stack) {
		Identifier id = stack.get(M4Components.SCHEMATIC);
		return id == null ? null : Schematics.get(id);
	}
}
