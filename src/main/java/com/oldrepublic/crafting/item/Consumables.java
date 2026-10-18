package com.oldrepublic.crafting.item;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.combat.DamageModifiers;
import com.oldrepublic.crafting.CraftingBalance;
import com.oldrepublic.crafting.CraftingEffects;
import com.oldrepublic.crafting.CraftingItems;
import com.oldrepublic.identity.ContextualActs;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.progression.ForceModifier;
import com.oldrepublic.progression.ForceModifiers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The server state of the Medtech consumables (spec MILESTONE_4.md 7.6): medpac heal-over-time and
 * the shared healing cooldown, the medpac's interrupt, and the stims with their crash. Stims and
 * heals are short (at most 1,400 ticks) and live in memory: a relog ends them, crash included.
 */
public final class Consumables {
	/** A stim kind. */
	public enum Stim {
		COMBAT,
		FOCUS
	}

	/** Where a stim is in its run (pure, {@link #phase}). */
	public enum Phase {
		ACTIVE,
		CRASH,
		DONE
	}

	private record Heal(float perTick, int ticksLeft) {
	}

	private record Injected(Stim stim, long at) {
	}

	private static final Map<UUID, Heal> HEALS = new HashMap<>();
	private static final Map<UUID, Injected> STIMS = new HashMap<>();

	public static void init() {
		DamageModifiers.register((victim, source, amount) -> {
			if (source.getEntity() instanceof ServerPlayer attacker && !source.is(ContextualActs.FORCE_HARM) && phase(attacker, Stim.COMBAT) == Phase.ACTIVE) {
				return amount * (1f + CraftingBalance.get().combatStimPct / 100f);
			}

			return amount;
		});
		ForceModifiers.register(new ForceModifier() {
			@Override
			public float regenMultiplier(Player p) {
				Phase phase = phase(p, Stim.FOCUS);

				if (phase == Phase.ACTIVE) {
					return 1f + CraftingBalance.get().focusStimPct / 100f;
				}

				return phase == Phase.CRASH ? 1f - CraftingBalance.get().focusStimPct / 100f : 1f;
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer p && damageTaken >= CraftingBalance.get().medpacInterruptDamage
					&& p.isUsingItem() && p.getUseItem().is(CraftingItems.MEDPAC)) {
				p.stopUsingItem();
				p.sendOverlayMessage(Component.translatable("crafting.oldrepublic.medpac.interrupted").withStyle(ChatFormatting.RED));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				forget(p);
			}
		});
		ServerPlayerEvents.LEAVE.register(Consumables::forget);
	}

	/** Puts every healing item on cooldown after a heal: {@code self} for {@code selfTicks}, the medpac for the shared cooldown. */
	static void healCooldowns(Player p, ItemStack self, int selfTicks) {
		int shared = CraftingBalance.get().healCooldown;
		p.getCooldowns().addCooldown(new ItemStack(CraftingItems.MEDPAC), shared);
		p.getCooldowns().addCooldown(new ItemStack(CraftingItems.KOLTO_INJECTOR), shared);
		p.getCooldowns().addCooldown(self, Math.max(selfTicks, shared));
	}

	/** Heals {@code total} over {@code ticks} (a new medpac replaces a running one). */
	static void startHeal(ServerPlayer p, float total, int ticks) {
		int n = Math.max(1, ticks);
		HEALS.put(p.getUUID(), new Heal(total / n, n));
	}

	/** Whether {@code p} is healing over time from a medpac. */
	public static boolean healing(Player p) {
		return HEALS.containsKey(p.getUUID());
	}

	/**
	 * Injects {@code stim}: refused while any stim runs or crashes (one at a time).
	 *
	 * @return whether it took
	 */
	static boolean inject(ServerPlayer p, Stim stim) {
		Injected current = STIMS.get(p.getUUID());

		if (current != null && phase(current.at, now(p), CraftingBalance.get()) != Phase.DONE) {
			return false;
		}

		STIMS.put(p.getUUID(), new Injected(stim, now(p)));
		return true;
	}

	/** The phase of {@code p}'s {@code stim}, or {@link Phase#DONE} without it. */
	public static Phase phase(Player p, Stim stim) {
		Injected current = STIMS.get(p.getUUID());

		if (current == null || current.stim != stim || p.level().getServer() == null) {
			return Phase.DONE;
		}

		return phase(current.at, now(p), CraftingBalance.get());
	}

	/** The phase of a stim injected at {@code at}, at {@code now} (pure). */
	public static Phase phase(long at, long now, CraftingBalance b) {
		long age = now - at;

		if (age < b.stimTicks) {
			return Phase.ACTIVE;
		}

		return age < (long) b.stimTicks + b.crashTicks ? Phase.CRASH : Phase.DONE;
	}

	/** The stim {@code p} has running or crashing, or null. */
	public static @Nullable Stim current(Player p) {
		Injected current = STIMS.get(p.getUUID());
		return current == null ? null : current.stim;
	}

	private static long now(Player p) {
		MinecraftServer server = p.level().getServer();
		return server == null ? 0L : server.getTickCount();
	}

	/** Server tick: heal-over-time steps, and stims moving into their crash or ending. */
	public static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Heal>> heals = HEALS.entrySet().iterator();

		while (heals.hasNext()) {
			Map.Entry<UUID, Heal> e = heals.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());

			if (p == null || !p.isAlive()) {
				heals.remove();
				continue;
			}

			p.heal(e.getValue().perTick());
			int left = e.getValue().ticksLeft() - 1;

			if (left <= 0) {
				heals.remove();
			} else {
				e.setValue(new Heal(e.getValue().perTick(), left));
			}
		}

		CraftingBalance b = CraftingBalance.get();
		long now = server.getTickCount();
		Iterator<Map.Entry<UUID, Injected>> stims = STIMS.entrySet().iterator();

		while (stims.hasNext()) {
			Map.Entry<UUID, Injected> e = stims.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			Injected stim = e.getValue();

			if (p == null) {
				stims.remove();
				continue;
			}

			long age = now - stim.at;

			if (age == b.stimTicks) {
				crash(p, stim.stim, b);
			} else if (phase(stim.at, now, b) == Phase.DONE) {
				stims.remove();
			}
		}
	}

	private static void crash(ServerPlayer p, Stim stim, CraftingBalance b) {
		if (stim == Stim.COMBAT) {
			p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, b.crashTicks, 0));
		}

		p.sendOverlayMessage(Component.translatable("crafting.oldrepublic.stim.crash").withStyle(ChatFormatting.GRAY));
		EffectEvents.toTracking(p, CraftingEffects.CONSUMABLE, p.position().add(0, 1.2, 0), null, p.getId(), 1f, CraftingEffects.CRASH);
	}

	private static void forget(ServerPlayer p) {
		HEALS.remove(p.getUUID());
		STIMS.remove(p.getUUID());
	}

	/** Clears everything (server stop). */
	public static void clear() {
		HEALS.clear();
		STIMS.clear();
	}

	private Consumables() {
	}
}
