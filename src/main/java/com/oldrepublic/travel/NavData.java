package com.oldrepublic.travel;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.registry.M4Items;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/**
 * {@code oldrepublic:nav_data} (spec MILESTONE_4.md 4.2, 12.1): the planet ids a nav computer
 * carries. A nav computer is crafted blank and only a loaded one fits the hulk's nav socket. It is
 * loaded by using it on a mentor (the player's path's worlds) or, for an Independent, on the wreck
 * at Courier's Fall (both worlds, from its nav log); dialogues can do the same with the action
 * {@code nav:load}.
 */
public final class NavData {
	/** Runs before the default phase, so a mentor's own use (its dialogue) does not swallow the nav computer. */
	private static final Identifier USE_PHASE = OldRepublicMod.id("nav_load");
	/** How far from a Courier's Fall record's origin a used block is looked up (the wreck is 15 × 9 × 11). */
	private static final int WRECK_SEARCH = 16;

	public static final DataComponentType<List<String>> NAV_DATA = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			OldRepublicMod.id("nav_data"),
			DataComponentType.<List<String>>builder()
					.persistent(Codec.STRING.listOf())
					.networkSynchronized(ByteBufCodecs.fromCodec(Codec.STRING.listOf()))
					.build());

	/** The planet ids on a stack (empty for anything but a loaded nav computer). */
	public static List<String> planets(ItemStack stack) {
		List<String> planets = stack.get(NAV_DATA);
		return planets == null ? List.of() : planets;
	}

	/** A nav computer with coordinates on it. */
	public static boolean isLoaded(ItemStack stack) {
		return stack.is(M4Items.NAV_COMPUTER) && !planets(stack).isEmpty();
	}

	/** The worlds a path's mentor knows (8.2): Selvaren for a Jedi, Varkhast for a Sith, both for anyone else. */
	public static List<Planets.Planet> forPath(Path path) {
		return switch (path) {
			case JEDI -> List.of(Planets.Planet.SELVAREN);
			case SITH -> List.of(Planets.Planet.VARKHAST);
			default -> List.of(Planets.Planet.SELVAREN, Planets.Planet.VARKHAST);
		};
	}

	/**
	 * Loads {@code planets} into the nav computer the player holds (main hand first, then off hand),
	 * keeping any planets already on it. Returns false when they hold none.
	 */
	public static boolean load(ServerPlayer player, List<Planets.Planet> planets) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);

			if (stack.is(M4Items.NAV_COMPUTER)) {
				List<String> ids = new ArrayList<>(planets(stack));

				for (Planets.Planet planet : planets) {
					if (planet != Planets.Planet.HOME && !ids.contains(planet.id())) {
						ids.add(planet.id());
					}
				}

				stack.set(NAV_DATA, List.copyOf(ids));
				return true;
			}
		}

		return false;
	}

	/** Loads the player's path's coordinates ({@link #forPath}). */
	public static boolean loadForPath(ServerPlayer player) {
		return load(player, forPath(Identity.path(player)));
	}

	static void init() {
		UseEntityCallback.EVENT.addPhaseOrdering(USE_PHASE, Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(USE_PHASE, NavData::useOnEntity);
		UseBlockCallback.EVENT.addPhaseOrdering(USE_PHASE, Event.DEFAULT_PHASE);
		UseBlockCallback.EVENT.register(USE_PHASE, NavData::useOnBlock);
	}

	/** A nav computer used on a mentor takes the player's path's coordinates (4.2). */
	private static InteractionResult useOnEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer p) || !(entity instanceof MentorEntity) || !p.getItemInHand(hand).is(M4Items.NAV_COMPUTER)) {
			return InteractionResult.PASS;
		}

		TravelDialogue.navLoaded(p, loadForPath(p));
		return InteractionResult.SUCCESS;
	}

	/** A nav computer used on the wreck at Courier's Fall takes the nav log's two worlds; only an Independent reads it (4.2, 8.2). */
	private static InteractionResult useOnBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer p) || !p.getItemInHand(hand).is(M4Items.NAV_COMPUTER)
				|| Identity.path(p) != Path.INDEPENDENT || !onWreck(server, hit.getBlockPos())) {
			return InteractionResult.PASS;
		}

		TravelDialogue.navLoaded(p, loadForPath(p));
		return InteractionResult.SUCCESS;
	}

	/** Whether {@code pos} is a block of a Courier's Fall wreck. */
	private static boolean onWreck(ServerLevel level, BlockPos pos) {
		WorldSites.SiteRecord record = WorldSites.nearest(level.getServer(), SiteKind.COURIERS_FALL, level.dimension(), pos, WRECK_SEARCH);
		SitePiece piece = record == null ? null : RuntimeSites.pieceOf(level, record);
		return piece != null && piece.box().isInside(pos);
	}

	private NavData() {
	}
}
