package com.oldrepublic.crafting;

import com.oldrepublic.registry.ModSounds;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Legendary schematic drops and their guaranteed progress (spec MILESTONE_5.md 11.4): the Spindle's
 * vault (W2), space bounties (W4) and the freighter holds on Ghessar (W3) call {@link #roll} each
 * time the player gets a chance at their schematic. A miss adds one to the player's counter for
 * that schematic ({@code CraftingData.legendaryMisses}); the try that reaches {@code pity} always
 * teaches it, and a hit clears the counter. A schematic the player already knows is never rolled
 * and never counted. Learning goes through {@link Crafting#teach} (the codex entry
 * {@code legendary_components} on the first) with a chat line and a chime; the caller shows the rest
 * of its own scene. The raid's counter is {@code RaidData.pity} (W8), not this.
 */
public final class LegendaryDrops {
	/**
	 * Rolls {@code chance} (0 to 1) for {@code p} to learn the legendary schematic {@code schematic} now, with the guarantee
	 * {@code pity} (the try at which it always drops: 8 means the 8th try without it; 0 or less, no guarantee, 11.4); a
	 * schematic the player knows is never rolled. True when the player learns it now.
	 */
	public static boolean roll(ServerPlayer p, Identifier schematic, float chance, int pity) {
		return roll(p, schematic, chance, pity, p.getRandom());
	}

	/** {@link #roll(ServerPlayer, Identifier, float, int)} with a given random source (tests seed it). */
	public static boolean roll(ServerPlayer p, Identifier schematic, float chance, int pity, RandomSource random) {
		CraftingData data = CraftingAttachments.get(p);
		String key = schematic.toString();

		if (Crafting.knows(p, schematic)) {
			// Known (taught by a vendor, a reward or an earlier drop): never rolled, and its old counter is dropped.
			if (data.misses(key) > 0) {
				CraftingAttachments.set(p, data.withMisses(key, 0));
			}

			return false;
		}

		int misses = data.misses(key);

		if (!hits(misses, chance, pity, random.nextFloat())) {
			CraftingAttachments.set(p, data.withMisses(key, misses + 1));
			return false;
		}

		CraftingAttachments.set(p, data.withMisses(key, 0));
		Crafting.teach(p, schematic);
		Schematic s = Schematics.get(schematic);
		Component name = s != null ? s.result().getHoverName() : Component.literal(schematic.getPath());
		p.sendSystemMessage(Component.translatable("crafting.oldrepublic.schematic.legendary_found", name).withStyle(ChatFormatting.GOLD));
		p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), ModSounds.BENCH_MASTERWORK, SoundSource.PLAYERS, 0.7f, 1.25f);
		return true;
	}

	/**
	 * The rule (11.4): with {@code misses} tries already missed, this try teaches the schematic when it
	 * is the {@code pity}th or later (a positive {@code pity}), or when {@code draw} (0 to 1) falls
	 * under {@code chance}.
	 */
	public static boolean hits(int misses, float chance, int pity, float draw) {
		if (pity > 0 && misses + 1 >= pity) {
			return true;
		}

		return draw < Math.max(0f, Math.min(1f, chance));
	}

	private LegendaryDrops() {
	}
}
