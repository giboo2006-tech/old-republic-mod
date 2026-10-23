package com.oldrepublic.world.outpost;

import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * How an outpost's officer and garrison are bound to their bounty terminal (W1 plan 4.9). Each
 * member carries the vanilla entity tag {@code oldrepublic.terminal:<x>,<y>,<z>:<slot>} (slot 0 the
 * officer, 1 to 4 the garrison), is persistent, and is leashed to the terminal with
 * {@code Mob#setHomeTo}. The tag works on W4's troopers and on the Phase 0 stand-ins alike.
 *
 * <p>The stale copy rule (spec 3.3, 7.4): a member that loads while its terminal is loaded and lists
 * a different UUID for its slot discards itself, so a trooper that was replaced while its chunk was
 * unloaded never comes back as a second copy.
 */
public final class TerminalBinding {
	/** Prefix of the binding tag. */
	public static final String TAG_PREFIX = "oldrepublic.terminal:";

	/** A parsed binding: the terminal's position and the member's slot. */
	public record Binding(BlockPos terminal, int slot) {
	}

	/** The binding tag for {@code slot} of the terminal at {@code terminal}. */
	public static String tag(BlockPos terminal, int slot) {
		return TAG_PREFIX + terminal.getX() + "," + terminal.getY() + "," + terminal.getZ() + ":" + slot;
	}

	/** The entity's binding, or null when it carries no well-formed binding tag. */
	public static @Nullable Binding of(Entity entity) {
		for (String tag : entity.entityTags()) {
			Binding binding = parse(tag);

			if (binding != null) {
				return binding;
			}
		}

		return null;
	}

	/** The slot the entity holds at the terminal at {@code terminal}, or -1 when it is not bound to it. */
	public static int slotAt(Entity entity, BlockPos terminal) {
		Binding binding = of(entity);
		return binding != null && binding.terminal().equals(terminal) ? binding.slot() : -1;
	}

	/**
	 * Binds a freshly spawned member: replaces any older binding tag, makes a mob persistent and
	 * leashes it to the terminal ({@link #leash}).
	 */
	public static void bind(Entity entity, BlockPos terminal, int slot, int leash) {
		for (String tag : List.copyOf(entity.entityTags())) {
			if (tag.startsWith(TAG_PREFIX)) {
				entity.removeTag(tag);
			}
		}

		entity.addTag(tag(terminal, slot));

		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
		}

		leash(entity, terminal, leash);
	}

	/**
	 * Keeps a mob within {@code leash} blocks of its terminal. Applied on every terminal check, since
	 * whether the vanilla home survives a save is not verified.
	 */
	public static void leash(Entity entity, BlockPos terminal, int leash) {
		// API-CHECK: Mob#setHomeTo(BlockPos, int) is verified (enemy.txt:6745); whether the home is saved is not.
		if (entity instanceof Mob mob && (!mob.hasHome() || !mob.getHomePosition().equals(terminal) || mob.getHomeRadius() != leash)) {
			mob.setHomeTo(terminal, leash);
		}
	}

	/**
	 * The stale copy rule on entity load: a bound member whose terminal is loaded and lists a
	 * different UUID for its slot is discarded. A terminal that lists nobody for the slot adopts it on
	 * its next check instead.
	 */
	public static void onEntityLoad(Entity entity, ServerLevel level) {
		Binding binding = of(entity);

		if (binding == null || !level.isLoaded(binding.terminal())) {
			return;
		}

		if (level.getBlockEntity(binding.terminal()) instanceof BountyTerminalBlockEntity terminal) {
			UUID listed = terminal.member(binding.slot());

			if (listed != null && !listed.equals(entity.getUUID())) {
				entity.discard();
			}
		}
	}

	private static @Nullable Binding parse(String tag) {
		if (!tag.startsWith(TAG_PREFIX)) {
			return null;
		}

		String[] parts = tag.substring(TAG_PREFIX.length()).split(":");

		if (parts.length != 2) {
			return null;
		}

		String[] xyz = parts[0].split(",");

		if (xyz.length != 3) {
			return null;
		}

		try {
			int slot = Integer.parseInt(parts[1]);

			if (slot < 0 || slot >= GarrisonRules.SLOTS) {
				return null;
			}

			return new Binding(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])), slot);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private TerminalBinding() {
	}
}
