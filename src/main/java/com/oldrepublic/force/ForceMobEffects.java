package com.oldrepublic.force;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Milestone 5 status effects (MILESTONE_5.md 9.2): {@code oldrepublic:stasis}, {@code fear}, {@code pacified},
 * {@code valor}, {@code rage} and {@code drained}. Each carries only its icon ({@code textures/mob_effect/<id>.png}), its
 * timer and its name ({@code effect.oldrepublic.<id>}); what it does lives in the powers' code, which reads the effect as
 * the state: the stasis and pacify cancels, the flee goal, and the transient attribute modifiers of Valor and Rage, whose
 * amounts come from {@code balance.json} (a {@code MobEffect}'s own modifiers are fixed when it registers, before the
 * balance file is read). Applied with particles off and the icon on; the mod's own effect events draw them.
 *
 * <p>{@code force/ForceEffects} stays the Milestone 2 class of effect-event ids.
 */
public final class ForceMobEffects {
	/** Held motionless (Force Stasis). */
	public static final Holder<MobEffect> STASIS = register("stasis", MobEffectCategory.HARMFUL, 0xC6E4EE);
	/** Afraid: flees, or slowed and unable to sprint (Force Fear, Choke's Dread, the saber's Dread Presence). */
	public static final Holder<MobEffect> FEAR = register("fear", MobEffectCategory.HARMFUL, 0x2A2030);
	/** Stops fighting and wanders (Mind Trick). */
	public static final Holder<MobEffect> PACIFIED = register("pacified", MobEffectCategory.NEUTRAL, 0xA8B8C4);
	/** A Master's rallying courage (Force Valor). */
	public static final Holder<MobEffect> VALOR = register("valor", MobEffectCategory.BENEFICIAL, 0xF0D48C);
	/** A Darth's fury (Force Rage). */
	public static final Holder<MobEffect> RAGE = register("rage", MobEffectCategory.BENEFICIAL, 0x8A1C22);
	/** Life drawn away (Force Drain): slowed while the drain holds, and a drained player's powers cost more. */
	public static final Holder<MobEffect> DRAINED = register("drained", MobEffectCategory.HARMFUL, 0x5C2C34);

	/** Loads the class, which registers the six effects. Call during mod initialisation. */
	public static void init() {
	}

	/**
	 * Gives {@code target} {@code effect} for {@code ticks} with particles off and the icon on (amplifier 0, not ambient).
	 * A shorter instance never cuts a longer one short (vanilla's update rule). Returns whether the effect took.
	 */
	public static boolean apply(LivingEntity target, Holder<MobEffect> effect, int ticks, @Nullable Entity source) {
		return ticks > 0 && target.addEffect(new MobEffectInstance(effect, ticks, 0, false, false, true), source);
	}

	/** Ticks left of {@code effect} on {@code entity}, 0 when it has none. */
	public static int remaining(LivingEntity entity, Holder<MobEffect> effect) {
		MobEffectInstance instance = entity.getEffect(effect);
		return instance == null ? 0 : instance.getDuration();
	}

	private static Holder<MobEffect> register(String name, MobEffectCategory category, int color) {
		// API-CHECK: the HUD and the inventory draw an effect's icon from textures/mob_effect/<id>.png, where vanilla's own icons
		// sit (read through the GUI atlas as the sprite <namespace>:mob_effect/<id>). Should the probe show another place,
		// only the six icons move (gen_textures_force.py, MOB_EFFECTS); nothing here changes.
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, OldRepublicMod.id(name), new PowerEffect(category, color));
	}

	/** One of the powers' effects: an icon, a timer and a name, with no modifiers and no tick of its own. */
	static final class PowerEffect extends MobEffect {
		PowerEffect(MobEffectCategory category, int color) {
			super(category, color);
		}
	}

	private ForceMobEffects() {
	}
}
