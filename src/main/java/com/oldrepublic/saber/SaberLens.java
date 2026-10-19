package com.oldrepublic.saber;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.Quality;
import com.oldrepublic.equipment.melee.MeleeCombat;
import com.oldrepublic.identity.ContextualActs;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Saber lenses (spec MILESTONE_4.md 5.7), made by Artifice and fitted at the Lightsaber Workbench.
 * The config keeps the lens id ({@code SaberConfig.lens}) and {@link SaberComponents#LENS_QUALITY}
 * its quality. Each acts only while the saber is ignited and in a hand, and {@code lens_mastery}
 * raises it by {@code lensMasteryPct}:
 * <ul>
 * <li>{@code lens_honed}: saber damage +6% (Fine 9, Masterwork 12), in the saber damage pipeline;</li>
 * <li>{@code lens_steady}: parry window +1 tick (Masterwork 2), a flat bonus under the cap;</li>
 * <li>{@code lens_resonant}: Force power damage +5% (Fine 6.5, Masterwork 8), a damage modifier on
 * {@code oldrepublic:force_harm} damage;</li>
 * <li>{@code lens_stormglass} (MILESTONE_5.md 11.2, always Legendary): finishers shock the target
 * for {@code stormglassShockTicks} and strikes deal {@code stormglassDroidPct} more to droids.</li>
 * </ul>
 * The values by quality are {@code crafting.lensHoned}, {@code lensSteady} and {@code lensResonant};
 * the stormglass lens has one value at every quality.
 */
public final class SaberLens {
	/** The skill node that strengthens lenses (W6's {@code skill_tree/saber.json}). */
	public static final String MASTERY = "lens_mastery";
	private static final CraftingBalance DEFAULTS = new CraftingBalance();

	/** A lens kind. Its item id is {@code lens_<id>}. Fitted by item id and sent by ordinal: append only. */
	public enum Lens {
		HONED,
		STEADY,
		RESONANT,
		/** The Stormglass Lens, a legendary lens (MILESTONE_5.md 11.2). */
		STORMGLASS;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Identifier itemId() {
			return OldRepublicMod.id("lens_" + id());
		}

		/** The lens with this item id, or null. */
		public static @Nullable Lens byItemId(Identifier id) {
			for (Lens lens : values()) {
				if (lens.itemId().equals(id)) {
					return lens;
				}
			}

			return null;
		}

		/** The raw value at {@code quality} (percent, ticks for the steady lens, the droid bonus in percent for the stormglass lens). */
		public float value(Quality quality, CraftingBalance b) {
			float[] values = switch (this) {
				case HONED -> b.lensHoned;
				case STEADY -> b.lensSteady;
				case RESONANT -> b.lensResonant;
				case STORMGLASS -> new float[] {b.stormglassDroidPct};
			};
			return CraftingBalance.byQuality(values, quality);
		}
	}

	/** The lens fitted in {@code saber}, or null. */
	public static @Nullable Lens lens(ItemStack saber) {
		if (!(saber.getItem() instanceof LightsaberItem)) {
			return null;
		}

		return SaberComponents.config(saber).lens().map(Lens::byItemId).orElse(null);
	}

	/** The quality of the lens fitted in {@code saber}. */
	public static Quality quality(ItemStack saber) {
		Integer q = saber.get(SaberComponents.LENS_QUALITY);
		return q == null ? Quality.STANDARD : Quality.of(q);
	}

	/** {@code lens_mastery}'s multiplier for {@code player} (1 without the node). */
	public static float mastery(@Nullable Player player, SaberBalance b) {
		return player != null && SaberPassives.has(player, MASTERY) ? 1f + b.lensMasteryPct / 100f : 1f;
	}

	/** The effect of {@code saber}'s lens of {@code kind} for its wielder, or 0: percent, or ticks for the steady lens. */
	public static float effect(@Nullable Player wielder, ItemStack saber, Lens kind) {
		if (lens(saber) != kind || !SaberComponents.isUsable(saber)) {
			return 0f;
		}

		return kind.value(quality(saber), crafting(wielder)) * mastery(wielder, SaberToggle.balance());
	}

	/** The live crafting balance on the server; the defaults on a client, which never reads the balance file. */
	static CraftingBalance crafting(@Nullable Player player) {
		return player != null && player.level().isClientSide() ? DEFAULTS : CraftingBalance.get();
	}

	/** {@code lens_steady}'s flat parry ticks for {@code saber} in {@code player}'s hand. */
	public static float parryTicks(Player player, ItemStack saber, SaberBalance b) {
		if (lens(saber) != Lens.STEADY || !SaberComponents.isIgnited(saber)) {
			return 0f;
		}

		return Lens.STEADY.value(quality(saber), crafting(player)) * mastery(player, b);
	}

	/** {@code lens_honed}'s damage multiplier for a saber strike by {@code attacker} with its main hand. */
	public static float damageMult(Player attacker) {
		return 1f + effect(attacker, attacker.getMainHandItem(), Lens.HONED) / 100f;
	}

	/**
	 * The damage multiplier of the lens in {@code saber} (the hand that struck) for a strike on
	 * {@code target}: a honed lens always, a stormglass lens against droids (MILESTONE_5.md 11.2).
	 */
	public static float damageMult(Player attacker, ItemStack saber, LivingEntity target) {
		float pct = effect(attacker, saber, Lens.HONED);

		if (MeleeCombat.isDroid(target)) {
			pct += effect(attacker, saber, Lens.STORMGLASS);
		}

		return 1f + pct / 100f;
	}

	/** How long a finisher from {@code saber} shocks its target: the stormglass lens's ticks (with {@code lens_mastery}), else 0. */
	public static int finisherShockTicks(Player attacker, ItemStack saber) {
		if (lens(saber) != Lens.STORMGLASS || !SaberComponents.isUsable(saber)) {
			return 0;
		}

		return Math.round(crafting(attacker).stormglassShockTicks * mastery(attacker, SaberToggle.balance()));
	}

	/** The ignited saber with a resonant lens in either of {@code caster}'s hands, as a multiplier on Force damage. */
	public static float forceMult(LivingEntity caster) {
		if (!(caster instanceof Player player)) {
			return 1f;
		}

		float pct = Math.max(effect(player, player.getMainHandItem(), Lens.RESONANT), effect(player, player.getOffhandItem(), Lens.RESONANT));
		return 1f + pct / 100f;
	}

	static void init() {
		DamageModifiers.register((victim, source, amount) -> {
			if (source.is(ContextualActs.FORCE_HARM) && source.getEntity() instanceof LivingEntity caster) {
				return amount * forceMult(caster);
			}

			return amount;
		});
	}

	private SaberLens() {
	}
}
