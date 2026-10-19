package com.oldrepublic.mission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.enemy.construct.SentinelVariant;
import com.oldrepublic.enemy.droid.Droids;
import com.oldrepublic.identity.Faction;
import com.oldrepublic.identity.Identity;
import com.oldrepublic.mission.MissionData.Active;
import com.oldrepublic.mission.MissionData.Stage;
import com.oldrepublic.mission.MissionDefinition.Objective;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.quest.Rewards;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubNpcs;
import com.oldrepublic.vendor.hub.HubRole;
import com.oldrepublic.world.site.SiteAnchors.Anchor;
import com.oldrepublic.world.site.SiteKind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Planet contracts (spec MILESTONE_4.md 8.7): the six templates of each hub's contract terminal,
 * on the M3 contract rules (the board, the daily full-pay count, contract XP) with credits (7.7):
 * <ul>
 * <li>{@code cleanse}: Selvaren, reach a waystone and cleanse or read it (Force Sense, a scanner
 * pulse, or the cleansing itself: a {@code survey} at a {@code WAYSTONE}); Varkhast, purge a
 * greater tomb's gate of its restless sentinels ({@code clear});</li>
 * <li>{@code cull}: a hollowhorn herd or an ash hound pack on open ground 48 to 96 blocks out
 * ({@code clear});</li>
 * <li>{@code recover}: a relic from a waystone's Watcher sentinels, or from a tomb's rogue dig-camp
 * droids ({@code secure});</li>
 * <li>{@code survey}: read a waystone or a greater tomb ({@code survey});</li>
 * <li>{@code escort}: walk an apprentice or acolyte from the hub to the nearest waystone or tomb
 * mouth ({@code escort}, new): they follow the owner, mobs can hurt them, and their death fails it;</li>
 * <li>{@code spar}: win a bout with an apprentice or acolyte at the hub under the M2 spar rules
 * ({@code spar}, new).</li>
 * </ul>
 * Pay: contract XP, the issuer's standing (Jedi Order or Sith Empire) and the contract's loot at
 * full pay, and the credits {@code MissionService} pays every contract. No commendations.
 *
 * <p>Milestone 5 (spec MILESTONE_5.md 3.1, 7.2) reads everything that differs by world from a
 * per-planet table: the board's templates and issuer ({@link #templates}, {@link #issuer}; Saltgate's
 * board for the Vessba kajidic, the holdfast's for Clan Hask), the default site kinds, the beasts a
 * hunt fights, the guards, the sentinels and the escort. Selvaren and Varkhast keep their Milestone 4
 * rows; a world without a row has no board, and a row the Milestone 5 contracts still need (their
 * other fights, the factor to escort) is empty until W4 writes it: nothing spawns.
 */
public final class PlanetContracts {
	/** The six templates of Selvaren's and Varkhast's boards (the file of each is {@code <planet>_<template>.json}). */
	public static final List<String> TEMPLATES = List.of("cleanse", "cull", "recover", "survey", "escort", "spar");
	/** Saltgate's board (MILESTONE_5.md 7.2): issued by the Vessba kajidic. */
	public static final List<String> GHESSAR_TEMPLATES = List.of("collect", "recover", "cull", "escort", "run", "bounty");
	/** The holdfast's board (7.2): issued by Clan Hask. */
	public static final List<String> TYRVANN_TEMPLATES = List.of("hunt", "spar", "cleanse", "recover", "patrol", "bounty");
	/** Cull herds stand this far from the player when the contract is accepted. */
	static final int CULL_MIN = 48;
	static final int CULL_MAX = 96;
	/** An escort arrives within this many blocks of the site. */
	static final double ESCORT_ARRIVE = 8;
	/** Ticks between escort checks. */
	private static final int ESCORT_INTERVAL = 10;

	/** A planet contract's site: where it is, and the worldgen kind when it is one. */
	public record Site(GlobalPos pos, @Nullable SiteKind kind) {
	}

	/** Each owner's escort (in memory: an escort is never saved and is respawned beside its owner). */
	private static final Map<UUID, UUID> ESCORTS = new HashMap<>();

	// ---------------------------------------------------------------- templates

	/**
	 * The board's issuer on {@code planet}: the Jedi Order on Selvaren, the Sith Empire on Varkhast, the
	 * Vessba kajidic on Ghessar and Clan Hask on Tyrvann (7.2); null at home and on a world without a board.
	 */
	public static @Nullable Faction issuer(Planets.Planet planet) {
		return switch (planet) {
			case SELVAREN -> Faction.JEDI_ORDER;
			case VARKHAST -> Faction.SITH_EMPIRE;
			case GHESSAR -> Faction.VESSBA_KAJIDIC;
			case TYRVANN -> Faction.CLAN_HASK;
			default -> null;
		};
	}

	/** The templates of {@code planet}'s board, in board order; empty at home and on a world without a board. */
	public static List<String> templates(Planets.Planet planet) {
		return switch (planet) {
			case SELVAREN, VARKHAST -> TEMPLATES;
			case GHESSAR -> GHESSAR_TEMPLATES;
			case TYRVANN -> TYRVANN_TEMPLATES;
			default -> List.of();
		};
	}

	/** The contract ids of a planet's board, in template order ({@code <planet>_<template>}); empty without a board. */
	public static List<String> types(Planets.Planet planet) {
		List<String> templates = templates(planet);
		List<String> types = new ArrayList<>(templates.size());

		for (String template : templates) {
			types.add(planet.id() + "_" + template);
		}

		return types;
	}

	/** The template part of a planet contract's id ({@code cull} of {@code varkhast_cull}). */
	public static String template(MissionDefinition def) {
		String planet = def.planet();
		return planet != null && def.id().startsWith(planet + "_") ? def.id().substring(planet.length() + 1) : def.id();
	}

	/** The world of a planet contract (its {@code planet}); null for any other mission. */
	static Planets.@Nullable Planet planetOf(MissionDefinition def) {
		return def.planet() == null ? null : Planets.Planet.byId(def.planet());
	}

	/** The worldgen kinds a template works at: its {@code site_kinds}, else the planet's own sites ({@link #siteKinds}). */
	static List<SiteKind> kinds(MissionDefinition def) {
		List<SiteKind> kinds = new ArrayList<>();

		for (String id : def.siteKinds()) {
			SiteKind kind = SiteKind.byId(id);

			if (kind != null) {
				kinds.add(kind);
			}
		}

		if (kinds.isEmpty()) {
			kinds.addAll(siteKinds(planetOf(def)));
		}

		return kinds;
	}

	// ---------------------------------------------------------------- per-planet rows (MILESTONE_5.md 3.1, 7.2)

	/**
	 * A planet's own worldgen sites, where its contracts work when their file names none: Selvaren's
	 * waystones, Varkhast's greater tombs, Ghessar's crashed freighters and kajidic posts, Tyrvann's
	 * hymn shrines and mire graves (3.2, 3.3); none for any other world.
	 */
	static List<SiteKind> siteKinds(Planets.@Nullable Planet planet) {
		if (planet == null) {
			return List.of();
		}

		return switch (planet) {
			case SELVAREN -> List.of(SiteKind.WAYSTONE);
			case VARKHAST -> List.of(SiteKind.GREATER_TOMB);
			case GHESSAR -> List.of(SiteKind.CRASHED_FREIGHTER, SiteKind.KAJIDIC_POST);
			case TYRVANN -> List.of(SiteKind.HYMN_SHRINE, SiteKind.MIRE_GRAVE);
			default -> List.of();
		};
	}

	/**
	 * The beast a {@code clear} contract hunts on open ground, by entity id: Selvaren's hollowhorns and
	 * Varkhast's ash hounds ({@code cull}), Ghessar's sand scours ({@code cull}) and Tyrvann's
	 * stormbacks ({@code hunt}, 7.2); null for any other template or world.
	 */
	static @Nullable String beast(Planets.@Nullable Planet planet, String template) {
		if (planet == null) {
			return null;
		}

		return switch (planet) {
			case SELVAREN -> "cull".equals(template) ? "hollowhorn" : null;
			case VARKHAST -> "cull".equals(template) ? "ash_hound" : null;
			case GHESSAR -> "cull".equals(template) ? "sand_scour" : null;
			case TYRVANN -> "hunt".equals(template) ? "stormback" : null;
			default -> null;
		};
	}

	/**
	 * The sentinels of a {@code clear} contract that hunts no beast: the restless tomb sentinels on
	 * Selvaren and Varkhast (Varkhast's {@code cleanse}, the Milestone 4 rule); null for any other
	 * world (Ghessar's and Tyrvann's other fights are W4's, 7.2).
	 */
	static @Nullable SentinelVariant clearSentinels(Planets.@Nullable Planet planet) {
		return planet == Planets.Planet.SELVAREN || planet == Planets.Planet.VARKHAST ? SentinelVariant.TOMB : null;
	}

	/** Who walks with an {@code escort} contract: an apprentice on Selvaren, an acolyte on Varkhast; null for any other world (W4's, 7.2). */
	static @Nullable HubRole escortRole(Planets.@Nullable Planet planet) {
		if (planet == null) {
			return null;
		}

		return switch (planet) {
			case SELVAREN -> HubRole.APPRENTICE;
			case VARKHAST -> HubRole.ACOLYTE;
			default -> null;
		};
	}

	// ---------------------------------------------------------------- placing

	/**
	 * Finds the accepted contract's site and calls back once (null when there is none in reach):
	 * the hub for a spar, open ground for a beast hunt (a cull, {@link #beast}), else the nearest
	 * worldgen site of its kinds (a survey skips sites already read).
	 */
	static void place(ServerPlayer p, MissionDefinition def, Consumer<@Nullable Site> callback) {
		ServerLevel level = p.level();
		Objective objective = def.objective();
		String template = template(def);
		Planets.Planet planet = planetOf(def);

		if (objective == Objective.SPAR) {
			BlockPos hub = planet == null ? null : PlanetHubs.hub(level.getServer(), planet);
			callback.accept(new Site(GlobalPos.of(level.dimension(), hub != null ? hub : p.blockPosition()), SiteKind.PLANET_HUB));
			return;
		}

		if (beast(planet, template) != null) {
			Active active = MissionService.get(p).active;
			long seed = active == null ? p.getRandom().nextLong() : active.seed;
			callback.accept(new Site(GlobalPos.of(level.dimension(), openGround(level, p.blockPosition(), seed)), null));
			return;
		}

		List<SiteKind> kinds = kinds(def);

		// A world without sites of its own (3.1): nothing to find, and MissionSites.nearest would never call back.
		if (kinds.isEmpty()) {
			callback.accept(null);
			return;
		}

		MissionBalance b = MissionFeature.balance();
		List<BlockPos> surveyed = List.copyOf(MissionService.get(p).surveyed);
		boolean survey = objective == Objective.SURVEY;
		int radius = survey ? b.surveyRadius : b.secureRadius;
		MissionSites.nearest(p, kinds, radius, survey ? piece -> surveyed.contains(MissionSites.heart(piece)) : null, piece -> {
			if (piece == null) {
				callback.accept(null);
				return;
			}

			BlockPos at = survey ? MissionSites.heart(piece) : MissionSites.surfaceGuard(level, piece);
			callback.accept(new Site(GlobalPos.of(level.dimension(), at), piece.kind()));
		});
	}

	/** A seeded spot {@link #CULL_MIN} to {@link #CULL_MAX} blocks from {@code from}, on the surface when its chunk is loaded. */
	static BlockPos openGround(ServerLevel level, BlockPos from, long seed) {
		RandomSource random = RandomSource.create(seed ^ 0x43554C4CL);
		double angle = random.nextDouble() * Math.PI * 2;
		int distance = CULL_MIN + random.nextInt(CULL_MAX - CULL_MIN + 1);
		int x = from.getX() + (int) Math.round(Math.cos(angle) * distance);
		int z = from.getZ() + (int) Math.round(Math.sin(angle) * distance);
		int y = level.hasChunkAt(x, z) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : from.getY();
		return new BlockPos(x, y, z);
	}

	/** The site is set: an escort contract's apprentice or acolyte falls in beside the owner. */
	static void onSiteSet(ServerPlayer p, MissionDefinition def) {
		if (def.objective() == Objective.ESCORT) {
			spawnEscort(p, def);
		}
	}

	// ---------------------------------------------------------------- spawning at the site

	/**
	 * A {@code clear} planet contract's fighters: the hunt's beasts ({@link #beast}), else the world's
	 * sentinels ({@link #clearSentinels}: Varkhast's restless tomb sentinels); none on a world without a row.
	 */
	static int spawnBeasts(ServerLevel level, ServerPlayer owner, MissionDefinition def, Active active, List<BlockPos> spots) {
		UUID id = owner.getUUID();
		Planets.Planet planet = planetOf(def);
		String beast = beast(planet, template(def));

		if (beast == null) {
			SentinelVariant sentinels = clearSentinels(planet);
			return sentinels == null ? 0 : MissionSites.spawnSentinels(level, id, active.id, spots, active.target, sentinels).size();
		}

		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(OldRepublicMod.id(beast)).orElse(null);

		if (type == null || spots.isEmpty()) {
			OldRepublicMod.LOGGER.warn("Planet contract {}: no '{}' entity to spawn", active.id, beast);
			return 0;
		}

		int spawned = 0;

		for (int i = 0; i < active.target; i++) {
			BlockPos at = MissionSites.stand(level, spots.get(i % spots.size()));
			Entity entity = type.create(level, EntitySpawnReason.EVENT);

			if (!(entity instanceof Mob mob)) {
				continue;
			}

			mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360f, 0f);
			MissionSites.tag(mob, id, active.id, MissionSites.ROLE_ENEMY);

			if (level.addFreshEntity(mob)) {
				mob.setTarget(owner);
				spawned++;
			}
		}

		return spawned;
	}

	/**
	 * A {@code secure} planet contract's guards: Watcher sentinels at a waystone (Selvaren), rogue
	 * dig-camp droids at a tomb (Varkhast); none on any other world (W4's, 7.2).
	 */
	static int spawnGuards(ServerPlayer owner, MissionDefinition def, Active active, boolean spawn) {
		if (!spawn) {
			owner.sendSystemMessage(Component.translatable("message.oldrepublic.mission.binding_failed"));
			return 0;
		}

		ServerLevel level = owner.level();
		BlockPos site = active.site;

		if (site == null) {
			return 0;
		}

		// Without a runtime piece every anchor falls back to a ring around the site.
		List<BlockPos> spots = MissionSites.spots(level, null, site, List.of(Anchor.SURFACE_GUARD, Anchor.SURFACE_GUARD, Anchor.SURFACE_GUARD, Anchor.SURFACE_GUARD));
		Planets.Planet planet = planetOf(def);

		if (planet == Planets.Planet.SELVAREN) {
			return MissionSites.spawnSentinels(level, owner.getUUID(), active.id, spots, active.target, SentinelVariant.WATCHER).size();
		}

		if (planet != Planets.Planet.VARKHAST) {
			return 0;
		}

		int spawned = 0;

		for (int i = 0; i < active.target; i++) {
			LivingEntity droid = Droids.spawnSecurity(level, MissionSites.stand(level, spots.get(i % spots.size())), owner.getUUID(), false);

			if (droid != null) {
				MissionSites.tag(droid, owner.getUUID(), active.id, MissionSites.ROLE_ENEMY);
				spawned++;
			}
		}

		return spawned;
	}

	// ---------------------------------------------------------------- escorts

	/** The escort falls in beside the owner: an apprentice or an acolyte ({@link #escortRole}); null on a world without a row. */
	private static @Nullable HubNpc spawnEscort(ServerPlayer p, MissionDefinition def) {
		ServerLevel level = p.level();
		HubRole role = escortRole(planetOf(def));

		if (role == null) {
			return null;
		}

		boolean acolyte = role == HubRole.ACOLYTE;
		String variant = (acolyte ? "acolyte_" : "apprentice_") + Math.floorMod(p.getUUID().hashCode(), acolyte ? 4 : 3);
		BlockPos at = MissionSites.stand(level, p.blockPosition().relative(p.getDirection().getOpposite(), 2));
		HubNpc escort = HubNpcs.spawn(level, at, role, variant);

		if (escort == null) {
			return null;
		}

		escort.removeTag(HubNpcs.HUB_TAG);
		escort.setEscortOwner(p.getUUID());
		ESCORTS.put(p.getUUID(), escort.getUUID());
		p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.escort_joins", escort.getDisplayName()));
		return escort;
	}

	/** The escort died on the way: the contract fails. */
	public static void escortDied(ServerLevel level, UUID owner) {
		ESCORTS.remove(owner);
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(owner);
		Active active = p == null ? null : MissionService.get(p).active;
		MissionDefinition def = active == null ? null : MissionLoader.get(active.id);

		if (p != null && def != null && def.isPlanetContract() && def.objective() == Objective.ESCORT) {
			p.sendSystemMessage(Component.translatable("message.oldrepublic.mission.escort_lost"));
			MissionService.abandon(p);
		}
	}

	/**
	 * Every server tick: each owner's escort. One that reached the site completes the contract; one
	 * that is missing (left behind in an unloaded chunk, a restart) falls in again beside its owner
	 * while the owner is on the contract's world.
	 */
	static void tick(MinecraftServer server) {
		if (server.getTickCount() % ESCORT_INTERVAL != 0) {
			return;
		}

		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			MissionData d = MissionService.view(p);
			Active active = d == null ? null : d.active;
			MissionDefinition def = active == null ? null : MissionLoader.get(active.id);

			if (def == null || !def.isPlanetContract() || def.objective() != Objective.ESCORT || active.site == null
					|| active.stage == Stage.PLACING || !p.level().dimension().equals(active.siteDimension)) {
				continue;
			}

			UUID escortId = ESCORTS.get(p.getUUID());
			Entity escort = escortId == null ? null : p.level().getEntity(escortId);

			if (escort == null || !escort.isAlive()) {
				if (escort == null && p.isAlive()) {
					spawnEscort(p, def);
				}

				continue;
			}

			if (escort.position().distanceToSqr(Vec3.atCenterOf(active.site)) <= ESCORT_ARRIVE * ESCORT_ARRIVE) {
				if (active.stage == Stage.TRAVEL) {
					MissionService.checkArrival(p);
				}

				p.sendSystemMessage(Component.translatable("message.oldrepublic.story.says", escort.getDisplayName(),
						Component.translatable("message.oldrepublic.mission.escort_thanks")));
				MissionService.trigger(p, Objectives.Trigger.ARRIVED, null);
			}
		}
	}

	// ---------------------------------------------------------------- spars

	/** Whether the player's spar contract is waiting for its bout (accepted and arrived at the hub). */
	public static boolean sparWaiting(ServerPlayer p) {
		Active active = MissionService.get(p).active;
		MissionDefinition def = active == null ? null : MissionLoader.get(active.id);
		return def != null && def.isPlanetContract() && def.objective() == Objective.SPAR && active.stage == Stage.ACTIVE;
	}

	/** The contract's bout was won. */
	public static void sparWon(ServerPlayer p) {
		if (sparWaiting(p)) {
			MissionService.trigger(p, Objectives.Trigger.SPAR_WON, null);
		}
	}

	// ---------------------------------------------------------------- pay and cleanup

	/** A planet contract's full pay beside its XP and credits: the issuer's standing and the contract's loot (no commendations). */
	static void pay(ServerPlayer p, MissionDefinition def, Active active, String source) {
		Faction issuer = Faction.byId(active.issuer);

		if (issuer != null) {
			Identity.changeReputation(p, issuer, MissionFeature.balance().contractRep, source, false);
		}

		if (def.loot() != null) {
			Rewards.giveLoot(p, ResourceKey.create(Registries.LOOT_TABLE, def.loot()), active.seed);
		}
	}

	/** The contract ended (done or abandoned): its escort goes home with it. */
	static void cleanup(ServerPlayer p, Active active) {
		UUID escortId = ESCORTS.remove(p.getUUID());
		Entity escort = escortId == null ? null : p.level().getEntity(escortId);

		if (escort != null && escort.isAlive()) {
			escort.discard();
		}
	}

	/**
	 * A player's in-memory state on leave, respawn or a dimension change: the escort's id is kept
	 * (an unloaded escort is simply respawned beside the owner by {@link #tick}).
	 */
	static void forget(UUID player) {
	}

	static void clear() {
		ESCORTS.clear();
	}

	private PlanetContracts() {
	}
}
