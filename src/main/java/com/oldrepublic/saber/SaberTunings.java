package com.oldrepublic.saber;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Either;
import com.oldrepublic.crafting.Schematic;
import com.oldrepublic.crafting.bench.Ingredients;
import com.oldrepublic.economy.Credits;
import com.oldrepublic.progression.ForceModifier;
import com.oldrepublic.progression.ForceModifiers;
import com.oldrepublic.registry.ModComponents;
import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.saber.SaberComponents.SaberConfig;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Crystal tuning (spec MILESTONE_5.md 10.4): the rules behind the workbench's Tune tab, and the
 * Attuned tuning's Force cost. A tuning costs {@code tuneCredits} credits and its material
 * ({@link CrystalTuning#materialStack}), replaces the old one with no refund, and needs an attuned
 * crystal of the player's own (an unattuned singing crystal attunes only when a saber is built
 * around it). The stat tunings are read by {@link SaberStats#tuned}; Attuned is a
 * {@link ForceModifier}: while a lit saber in either hand carries it, Force powers cost
 * {@code resonantPct} less, counted once whichever saber (or crystal) carries it. Server side,
 * except {@link #carries} and {@link #attunedMultiplier}, which only read stacks.
 */
public final class SaberTunings {
	/** What a tuning request came to; each has a status line {@code screen.oldrepublic.workbench.tune.status.<id>}. */
	public enum Result {
		TUNED,
		/** No crystal there. */
		NO_CRYSTAL,
		/** Not the player's crystal or saber. */
		NOT_YOURS,
		/** An unattuned singing crystal. */
		UNATTUNED,
		/** It already has this tuning. */
		ALREADY,
		/** Short of the material. */
		NO_MATERIALS,
		/** Short of credits. */
		NO_CREDITS;

		public String messageKey() {
			return "screen.oldrepublic.workbench.tune.status." + name().toLowerCase(Locale.ROOT);
		}
	}

	/** The material a tuning takes, as a crafting input (for {@link Ingredients}). */
	public static List<Schematic.Input> materials(CrystalTuning tuning, SaberBalance b) {
		return List.of(new Schematic.Input(Either.left(tuning.material()), Math.max(1, tuning.materialCount(b))));
	}

	/**
	 * Whether {@code player} may give {@code crystal} the tuning {@code tuning} now (ownership, the
	 * credits and the material), without taking anything.
	 */
	public static Result check(ServerPlayer player, @Nullable CrystalAttunement crystal, CrystalTuning tuning, SaberBalance b) {
		if (crystal == null) {
			return Result.NO_CRYSTAL;
		}

		if (!crystal.owner().equals(player.getUUID())) {
			return Result.NOT_YOURS;
		}

		if (tuning.id().equals(crystal.tuning())) {
			return Result.ALREADY;
		}

		if (!Ingredients.has(Ingredients.sources(null, player), materials(tuning, b))) {
			return Result.NO_MATERIALS;
		}

		if (Credits.count(player, true) < b.tuneCredits) {
			return Result.NO_CREDITS;
		}

		return Result.TUNED;
	}

	/** Takes the credits and the material, all or nothing. */
	public static boolean pay(ServerPlayer player, CrystalTuning tuning, SaberBalance b) {
		if (!Credits.take(player, b.tuneCredits, true)) {
			return false;
		}

		if (!Ingredients.take(null, player, materials(tuning, b))) {
			Credits.give(player, b.tuneCredits);
			return false;
		}

		return true;
	}

	/** A copy of the loose crystal {@code crystal} with {@code tuning}, or empty if it is not attuned. */
	public static ItemStack retunedCrystal(ItemStack crystal, CrystalTuning tuning) {
		CrystalAttunement attunement = crystal.get(ModComponents.CRYSTAL_ATTUNEMENT);

		if (attunement == null) {
			return ItemStack.EMPTY;
		}

		ItemStack copy = crystal.copy();
		copy.set(ModComponents.CRYSTAL_ATTUNEMENT, attunement.withTuning(tuning.id()));
		return copy;
	}

	/**
	 * A copy of {@code saber} with its crystal ({@code second} false) or a saberstaff's second crystal
	 * retuned, its attribute modifiers and reach rewritten (retracted: a bench saber is never lit), or
	 * empty if there is no such crystal.
	 */
	public static ItemStack retunedSaber(ItemStack saber, boolean second, CrystalTuning tuning) {
		SaberConfig config = SaberComponents.config(saber);
		SaberConfig updated;

		if (second) {
			if (config.second().isEmpty()) {
				return ItemStack.EMPTY;
			}

			updated = config.withSecond(Optional.of(config.second().get().withTuning(tuning.id())));
		} else {
			updated = new SaberConfig(config.crystal().withTuning(tuning.id()), config.color(), config.emitter(), config.grip(), config.pommel(),
					config.second(), config.lens(), config.length());
		}

		ItemStack copy = saber.copy();
		copy.set(SaberComponents.SABER_CONFIG, updated);
		SaberToggle.refreshModifiers(copy, SaberComponents.isIgnited(copy));
		return copy;
	}

	/** Whether any crystal of {@code saber} carries {@code tuning}. */
	public static boolean carries(ItemStack saber, CrystalTuning tuning) {
		return saber.getItem() instanceof LightsaberItem && CrystalTuning.count(SaberComponents.config(saber), tuning) > 0;
	}

	/**
	 * The Attuned tuning's multiplier on {@code player}'s Force costs: {@code 1 - resonantPct/100} while
	 * a lit, un-thrown saber in either hand carries it (once, however many crystals do), else 1.
	 */
	public static float attunedMultiplier(Player player, SaberBalance b) {
		for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
			if (SaberComponents.isUsable(stack) && carries(stack, CrystalTuning.RESONANT)) {
				return Math.max(0f, 1f - b.resonantPct / 100f);
			}
		}

		return 1f;
	}

	static void init() {
		ForceModifiers.register(new ForceModifier() {
			@Override
			public float costMultiplier(Player p, String abilityId) {
				return attunedMultiplier(p, SaberToggle.balance());
			}
		});
	}

	private SaberTunings() {
	}
}
