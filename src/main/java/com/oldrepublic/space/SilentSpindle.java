package com.oldrepublic.space;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.crafting.LegendaryDrops;
import com.oldrepublic.dialogue.Dialogues;
import com.oldrepublic.planet.Clocks;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteAnchors;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.SpawnerGroups;
import com.oldrepublic.world.site.WorldSites;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Silent Spindle (spec MILESTONE_5.md 5.7, 11.4), a Republic listening station drifting in the
 * home system's orbit at {@code space.spindlePos}: one shared site per server. It is placed when a
 * player first comes within {@code space.spindlePlaceRange}: the chunks of its box are loaded
 * (generated where needed) and the template {@code station/spindle} is stamped with no terrain
 * adaptation, then recorded in {@link WorldSites} as {@link SiteKind#DERELICT_STATION}.
 *
 * <p>Once a day, when a player comes within {@code space.spindleDroidRange}, a security droid
 * ({@code security_droids}) stands up at each of the four posts that has none, kept to its post. The
 * log terminal opens W4's dialogue {@code spindle_log}; the vault rolls {@code chests/spindle_vault}
 * once per player per in-game day ({@link SpaceData#vaultDay}) with the Spindle Capacitor's
 * schematic roll ({@code space.vaultSchematicChance}, guaranteed on the {@code vaultSchematicPity}th
 * opening without it). Both are vanilla blocks at their anchors (an observer and a chest), used
 * through {@link #onUse}.
 */
public final class SilentSpindle {
	public static final Identifier TEMPLATE = OldRepublicMod.id("station/spindle");
	/** W4's dialogue: what the station heard before it went silent. */
	public static final Identifier LOG_DIALOGUE = OldRepublicMod.id("spindle_log");
	/** The Spindle Capacitor's legendary schematic (11.2). */
	public static final Identifier CAPACITOR_SCHEMATIC = OldRepublicMod.id("spindle_capacitor_legendary");
	public static final ResourceKey<LootTable> VAULT_LOOT = ResourceKey.create(Registries.LOOT_TABLE, OldRepublicMod.id("chests/spindle_vault"));
	/** The posts' droids carry this tag, so a post whose droid stands is not restocked. */
	static final String DROID_TAG = "oldrepublic.spindle_droid";
	/** The day the posts were last restocked (a global attachment, saved with the world). */
	static final AttachmentType<Long> DROID_DAY = AttachmentRegistry.create(OldRepublicMod.id("spindle"), builder -> builder.persistent(Codec.LONG));

	private static final int CHECK_INTERVAL = 20;
	/** A post's droid counts within this many blocks of it, and is kept within it. */
	private static final int POST_RADIUS = 8;
	private static final SiteAnchors.Anchor[] POSTS = {
			SiteAnchors.Anchor.SPINDLE_POST_1, SiteAnchors.Anchor.SPINDLE_POST_2, SiteAnchors.Anchor.SPINDLE_POST_3, SiteAnchors.Anchor.SPINDLE_POST_4
	};

	/** Loads the class, registering the attachment. */
	static void init() {
	}

	/** Every second: the station placed when a player first comes near, and its posts restocked once a day. */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_INTERVAL != 0) {
			return;
		}

		ServerLevel level = Planets.orbitLevel(server, Planets.Planet.HOME);

		if (level == null || level.players().isEmpty()) {
			return;
		}

		SpaceBalance b = SpaceBalance.get();
		BlockPos centre = centre(b);
		List<WorldSites.SiteRecord> records = WorldSites.all(server);
		WorldSites.SiteRecord record = recordIn(records);

		if (record == null) {
			if (placeNow(records, anyoneWithin(level, Vec3.atCenterOf(centre), b.spindlePlaceRange))) {
				place(level, centre);
			}

			return;
		}

		SitePiece piece = RuntimeSites.pieceOf(level, record);

		if (piece != null && anyoneWithin(level, Vec3.atCenterOf(piece.box().getCenter()), b.spindleDroidRange)) {
			restock(level, piece);
		}
	}

	/** Where the station's middle hangs ({@code space.spindlePos}). */
	static BlockPos centre(SpaceBalance b) {
		int[] p = b.spindlePos;
		return p.length >= 3 ? new BlockPos(p[0], p[1], p[2]) : new BlockPos(420, 150, -380);
	}

	/** The station's record, or null while it is not placed. */
	public static WorldSites.@Nullable SiteRecord record(MinecraftServer server) {
		return recordIn(WorldSites.all(server));
	}

	/** The station's record among {@code records} (the server's, or a test's detached list), or null. */
	public static WorldSites.@Nullable SiteRecord recordIn(List<WorldSites.SiteRecord> records) {
		for (WorldSites.SiteRecord record : records) {
			if (record.kind() == SiteKind.DERELICT_STATION) {
				return record;
			}
		}

		return null;
	}

	/** Whether the station is placed now (5.7): a player has come near, and it was never placed (once per server, whoever comes). */
	public static boolean placeNow(List<WorldSites.SiteRecord> records, boolean anyoneNear) {
		return anyoneNear && recordIn(records) == null;
	}

	/**
	 * Places the station with its middle at {@code centre}: the chunks of its box loaded first, then
	 * the template stamped and recorded. Null when the template is missing or could not be placed.
	 */
	static @Nullable SitePiece place(ServerLevel level, BlockPos centre) {
		StructureTemplate template = level.getStructureManager().get(TEMPLATE).orElse(null);

		if (template == null) {
			OldRepublicMod.LOGGER.error("[space] The Silent Spindle's template {} is missing", TEMPLATE);
			return null;
		}

		BlockPos stand = centre.below(template.getSize().getY() / 2);
		BlockPos origin = RuntimeSites.templateOrigin(template, stand, Rotation.NONE, 0);
		BoundingBox box = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + template.getSize().getX() - 1,
				origin.getY() + template.getSize().getY() - 1, origin.getZ() + template.getSize().getZ() - 1);
		// The void generates fast; every chunk the stamp checks is loaded (as RuntimeSites.loadForSunken does).
		box.inflatedBy(1).intersectingChunks().forEach(chunk -> level.getChunk(chunk.x(), chunk.z()));
		SitePiece piece = RuntimeSites.stamp(level, SiteKind.DERELICT_STATION, TEMPLATE, origin, Rotation.NONE);

		if (piece == null) {
			OldRepublicMod.LOGGER.error("[space] The Silent Spindle could not be placed at {}", origin);
			return null;
		}

		MinecraftServer server = level.getServer();
		WorldSites.add(server, new WorldSites.SiteRecord(SiteKind.DERELICT_STATION, level.dimension(), piece.origin(), piece.rotation(), piece.template(),
				null, Clocks.day(server)));
		OldRepublicMod.LOGGER.info("[space] The Silent Spindle was placed at {} in {}", piece.origin(), level.dimension().identifier());
		return piece;
	}

	/** Once a day: a droid at each post that has none standing (5.7). */
	static void restock(ServerLevel level, SitePiece piece) {
		MinecraftServer server = level.getServer();
		long day = Clocks.day(server);
		Long last = server.globalAttachments().getAttached(DROID_DAY);

		if (last != null && last >= day) {
			return;
		}

		List<BlockPos> posts = posts(piece);

		for (BlockPos post : posts) {
			if (!level.isPositionEntityTicking(post)) {
				// Not every post is loaded yet: try again on the next check.
				return;
			}
		}

		server.globalAttachments().setAttached(DROID_DAY, day);
		int stood = 0;

		for (BlockPos post : posts) {
			if (guarded(level, post)) {
				continue;
			}

			for (LivingEntity droid : SpawnerGroups.spawn(level, post, SpawnerGroups.SECURITY_DROIDS, 1, true, null)) {
				droid.addTag(DROID_TAG);

				if (droid instanceof Mob mob) {
					mob.setPersistenceRequired();
					mob.setHomeTo(post, POST_RADIUS);
				}

				stood++;
			}
		}

		OldRepublicMod.LOGGER.info("[space] The Silent Spindle's posts were restocked on day {}: {} droid(s)", day, stood);
	}

	private static List<BlockPos> posts(SitePiece piece) {
		List<BlockPos> posts = new ArrayList<>();

		for (SiteAnchors.Anchor anchor : POSTS) {
			BlockPos post = SiteAnchors.find(piece, anchor);

			if (post != null) {
				posts.add(post);
			}
		}

		return posts;
	}

	/** Whether a droid of the posts stands within {@link #POST_RADIUS} of {@code post}. */
	static boolean guarded(ServerLevel level, BlockPos post) {
		return !level.getEntitiesOfClass(LivingEntity.class, new AABB(post).inflate(POST_RADIUS), e -> e.isAlive() && e.entityTags().contains(DROID_TAG))
				.isEmpty();
	}

	/** A player used the block at {@code pos}: the log terminal and the vault answer; anything else passes. */
	static InteractionResult onUse(ServerPlayer p, BlockPos pos) {
		ServerLevel level = p.level();

		if (Planets.orbitOf(level.dimension()) != Planets.Planet.HOME) {
			return InteractionResult.PASS;
		}

		WorldSites.SiteRecord record = record(level.getServer());
		SitePiece piece = record == null || !record.dimension().equals(level.dimension()) ? null : RuntimeSites.pieceOf(level, record);

		if (piece == null || !piece.box().isInside(pos)) {
			return InteractionResult.PASS;
		}

		if (pos.equals(SiteAnchors.find(piece, SiteAnchors.Anchor.SPINDLE_LOG))) {
			if (!Dialogues.open(p, LOG_DIALOGUE, null)) {
				p.sendOverlayMessage(Component.translatable("space.oldrepublic.spindle.log_dead"));
			}

			return InteractionResult.SUCCESS;
		}

		if (pos.equals(SiteAnchors.find(piece, SiteAnchors.Anchor.SPINDLE_VAULT))) {
			openVault(p, pos);
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	/** The vault, today. */
	static void openVault(ServerPlayer p, BlockPos pos) {
		openVault(p, pos, Clocks.day(p.level().getServer()));
	}

	/**
	 * The vault on in-game day {@code day} (game tests pass their own): once per player per day, a roll
	 * on its table and the capacitor's schematic roll. Returns whether it opened.
	 */
	public static boolean openVault(ServerPlayer p, BlockPos pos, long day) {
		ServerLevel level = p.level();
		SpaceData data = SpaceData.get(p);

		if (!SpaceRules.vaultOpens(data.vaultDay(), day)) {
			level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1f, 0.9f);
			p.sendOverlayMessage(Component.translatable("space.oldrepublic.spindle.vault_empty"));
			return false;
		}

		SpaceData.set(p, data.withVaultDay(day));
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1f, 0.8f);

		for (ItemStack stack : Rewards.roll(p, VAULT_LOOT, Rewards.seed(p, "spindle_vault", day))) {
			SpaceRewards.give(p, stack);
		}

		SpaceBalance b = SpaceBalance.get();
		LegendaryDrops.roll(p, CAPACITOR_SCHEMATIC, b.vaultSchematicChance, b.vaultSchematicPity);
		p.sendOverlayMessage(Component.translatable("space.oldrepublic.spindle.vault_opened"));
		OldRepublicMod.LOGGER.info("[space] {} opened the Silent Spindle's vault on day {}", p.getPlainTextName(), day);
		return true;
	}

	/** Whether a non-spectator player is within {@code range} of {@code at}. */
	private static boolean anyoneWithin(ServerLevel level, Vec3 at, double range) {
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.distanceToSqr(at) <= range * range) {
				return true;
			}
		}

		return false;
	}

	private SilentSpindle() {
	}
}
