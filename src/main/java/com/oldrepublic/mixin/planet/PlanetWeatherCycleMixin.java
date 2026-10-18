package com.oldrepublic.mixin.planet;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Keeps the shared weather cycle at its vanilla pace with the planets loaded. Every level that can
 * have weather runs {@code ServerLevel#advanceWeatherCycle} each tick, and the clear, rain and
 * thunder timers it counts down live in the server-wide {@code WeatherData}, so Selvaren and
 * Varkhast would each count them down again. Only the overworld advances them: on any other
 * level the method's {@code advanceWeather} game rule read, which gates nothing but the timer
 * updates, answers false. A planet still eases its own rain and thunder levels toward the shared
 * state each tick, so it follows the global weather (Varkhast's ashfall storm is its rain flag).
 * The sleep reset in {@code ServerLevel#tick} is untouched.
 */
@Mixin(ServerLevel.class)
abstract class PlanetWeatherCycleMixin {
	@Redirect(method = "advanceWeatherCycle", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
	private Object oldrepublic$overworldAdvancesWeather(GameRules rules, GameRule<?> rule) {
		if (rule == GameRules.ADVANCE_WEATHER && ((ServerLevel) (Object) this).dimension() != Level.OVERWORLD) {
			return Boolean.FALSE;
		}

		return rules.get(rule);
	}
}
