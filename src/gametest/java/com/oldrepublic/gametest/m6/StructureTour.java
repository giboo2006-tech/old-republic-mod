package com.oldrepublic.gametest.m6;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Pair;
import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.gametest.visuals.Visuals;
import com.oldrepublic.planet.PlanetHubs;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.travel.TravelService;
import com.oldrepublic.world.site.RuntimeSites;
import com.oldrepublic.world.site.SiteKind;
import com.oldrepublic.world.site.SiteMeta;
import com.oldrepublic.world.site.SitePiece;
import com.oldrepublic.world.site.StructureLocator;
import com.oldrepublic.world.site.far.FarSites;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The structure tour of the structures overhaul (docs/MILESTONE_6.md 9): every structure on its own
 * world's terrain, photographed as a player would come on it. {@code StructureTourClientTest} runs it
 * in a seeded world; its pictures are the acceptance review.
 *
 * <p><b>Where.</b> Each world's stops take slots on a line along +X from ({@link #LINE_START},
 * {@link #LINE_START}), {@link #SPACING} blocks apart. A worldgen structure is the instance the world
 * itself generates nearest its slot ({@code ChunkGenerator#findNearestMapStructure}, as
 * {@code /locate} finds it), loaded so that its chunks generate with it, terrain blending and all.
 * A site template is placed as the game places a far site ({@link FarSites}): on dry ground of the
 * kind it prefers, chosen from the world generator's noise within {@link #FAR_REACH} of its slot, with
 * its terrain fitted. Any other runtime template (a variant, a deep piece) is stamped with its stop's
 * buried layers on the flattest dry spot near its slot, the ground fitted
 * ({@link RuntimeSites#adaptTerrain}). A hub is placed where the game places it
 * ({@link PlanetHubs#ensure}).
 *
 * <p><b>Pictures</b>, as a spectator with the HUD hidden, at noon:
 * <ul>
 * <li>{@code aerial}: the whole box from the south-east, {@link #AERIAL_PITCH} degrees down, far
 * enough back to frame it, raised over any hill in the way;</li>
 * <li>{@code ground}: from eye height on the ground in front of the box, on the first side whose
 * view of the box's middle no terrain blocks, as a player walking up sees it;</li>
 * <li>{@code close}, {@code close2}, ...: the stop's chosen views, each in the frame of one of its
 * pieces (at midnight when asked).</li>
 * </ul>
 * A stop wholly under the ground (a deep piece placed on its own) gets only its chosen views. Shots
 * are {@code oldrepublic_m6_<nnn>_<name>_<view>}, {@code nnn} the stop's number. A worldgen stop made
 * with {@link Stop#variants} is followed by a template stop for each variant the world did not
 * generate there.
 *
 * <p>A stop that fails (no instance found, a template that will not place, a timeout) is logged and
 * the tour goes on; the run fails at the end with every failed stop named. Side effects: the placed
 * templates and hubs stay in the world; the player ends back where they started, in survival, with
 * the HUD shown.
 */
public final class StructureTour {
	/** Where each world's line of slots starts (x and z), far from spawn and every hub. */
	private static final int LINE_START = 2400;
	/** Blocks between slots: two stops' views never share their loaded areas. */
	private static final int SPACING = 640;
	/** The search radius for a worldgen structure, in placement cells (as {@code /locate} counts them). */
	private static final int LOCATE_RADIUS = 12;
	/** The far edge of the band, around its slot, in which a site template is placed as the game places a far site. */
	private static final int FAR_REACH = 256;
	/**
	 * Where the dry ground for any other runtime template is looked for: a grid around its slot,
	 * {@link #SEARCH_STEP} apart, {@link #SEARCH_ALONG} each way along the line (less than half the
	 * spacing) and {@link #SEARCH_ACROSS} each way across it.
	 */
	private static final int SEARCH_ALONG = 288;
	private static final int SEARCH_ACROSS = 960;
	private static final int SEARCH_STEP = 32;
	/** The spread of heights under a footprint, in blocks, that ends the dry-ground search at once. */
	private static final int FLAT_ENOUGH = 2;
	/** How far a ground camera rises, on its second pass, to see over trees between it and the box. */
	private static final int GROUND_RISE_HIGH = 20;
	/** Blocks around a stop's box that are force-loaded with it, for the cameras' columns. */
	private static final int MARGIN = 24;
	/** Degrees the aerial view looks down. */
	private static final double AERIAL_PITCH = 35;
	/** How far an aerial camera may rise to see over a hill between it and its structure. */
	private static final int AERIAL_RISE = 48;
	/** How far a ground camera may rise (over a bush, a bank) before its side is given up. */
	private static final int GROUND_RISE = 8;
	/**
	 * The client's render distance during the tour, in chunks: the land around a structure is part of
	 * the picture. The server is told of it too ({@code Options#save} sends the client's settings), or
	 * it goes on sending only the five chunks client game tests start with.
	 */
	private static final int RENDER_DISTANCE = 8;
	/** Eye height of a standing player (and of a spectator). */
	private static final double EYE = 1.62;
	/** Ticks a force-loaded area gets to generate and load. */
	private static final int LOAD_TIMEOUT = 4800;
	/** Ticks a hub gets to be placed. */
	private static final int HUB_TIMEOUT = 3600;
	/** Ticks the client gets to arrive in a level, then to have the camera's chunk. */
	private static final int CLIENT_TIMEOUT = 1200;
	/** Ticks the client gets to download and draw every chunk in view; after that the shot is taken as it stands. */
	private static final int RENDER_TIMEOUT = 1800;
	/** Ticks for the view to settle (particles, light, animated textures) before a shot. */
	private static final int SETTLE = 30;

	/** How a stop is put in the world. */
	public enum Kind {
		/** A worldgen structure, found where the world generates it. */
		WORLDGEN,
		/** A runtime template, placed near its slot. */
		TEMPLATE,
		/** A planet's hub, placed where the game places it. */
		HUB
	}

	/**
	 * A chosen view, in the frame of the stop's piece made from {@code template}: the camera at the
	 * centre of the {@code eye} cell looking at the centre of the {@code look} cell, at midnight when
	 * {@code night}.
	 */
	public record Close(Identifier template, BlockPos eye, BlockPos look, boolean night) {
	}

	/**
	 * One stop: the level, how it is placed, the structure or template id, the shots' name, the
	 * buried layers of a template that is not a site template (else -1: the command's own, or the
	 * site meta's; for a worldgen stop, those of the variants it places as template stops, else -1),
	 * the template placed first for a template stamped over it (else null) and the chosen views.
	 */
	public record Stop(ResourceKey<Level> level, Kind kind, Identifier id, String name, int buried, @Nullable Identifier base, List<Close> closes) {
		public static Stop worldgen(ResourceKey<Level> level, String structure, String name, Close... closes) {
			return new Stop(level, Kind.WORLDGEN, OldRepublicMod.id(structure), name, -1, null, List.of(closes));
		}

		/**
		 * A worldgen structure built from one of several variant templates, a chosen view for each: the
		 * variant the world generated is shot where it stands, and each other one is then placed as a
		 * template stop of its own ({@code buried} layers into the ground, named after the variant).
		 */
		public static Stop variants(ResourceKey<Level> level, String structure, String name, int buried, Close... closes) {
			return new Stop(level, Kind.WORLDGEN, OldRepublicMod.id(structure), name, buried, null, List.of(closes));
		}

		public static Stop template(ResourceKey<Level> level, String template, String name, int buried, Close... closes) {
			return new Stop(level, Kind.TEMPLATE, OldRepublicMod.id(template), name, buried, null, List.of(closes));
		}

		/**
		 * A sparse template stamped over {@code base} at the base's origin and rotation, as the game
		 * builds a camp into a site: the base is placed first, as a template stop would place it.
		 */
		public static Stop overlay(ResourceKey<Level> level, String template, String base, String name, Close... closes) {
			return new Stop(level, Kind.TEMPLATE, OldRepublicMod.id(template), name, -1, OldRepublicMod.id(base), List.of(closes));
		}

		public static Stop hub(Planets.Planet planet, String name, Close... closes) {
			Identifier template = PlanetHubs.template(planet);

			if (template == null) {
				throw new IllegalArgumentException(planet.id() + " has no hub");
			}

			return new Stop(planet.level(), Kind.HUB, template, name, -1, null, List.of(closes));
		}
	}

	/** {@link Close} in short: a template, eye x y z, look x y z, night. */
	public static Close close(String template, int ex, int ey, int ez, int lx, int ly, int lz, boolean night) {
		return new Close(OldRepublicMod.id(template), new BlockPos(ex, ey, ez), new BlockPos(lx, ly, lz), night);
	}

	/** A piece of a placed stop: its template, where the template's (0, 0, 0) went and its turn. */
	private record Frame(Identifier template, BlockPos origin, Rotation rotation) {
		Vec3 centre(BlockPos local) {
			return Vec3.atCenterOf(origin.offset(StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO)));
		}
	}

	/** A placed stop: its box (every piece), the ground around it and its pieces. */
	private record Placed(BoundingBox box, int ground, List<Frame> frames) {
	}

	/** One picture to take. */
	private record View(String suffix, Vec3 eye, Vec3 look, boolean night) {
	}

	/** Runs the tour over {@code stops} in order; fails at the end when any stop failed. */
	public static void run(ClientGameTestContext context, TestSingleplayerContext world, List<Stop> stops) {
		TestServerContext server = world.getServer();
		double[] origin = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			return new double[] {p.getX(), p.getY(), p.getZ(), p.getYRot()};
		});
		Map<ResourceKey<Level>, Integer> slots = new HashMap<>();
		List<String> failed = new ArrayList<>();
		ResourceKey<Level> current = Level.OVERWORLD;
		int renderDistance = context.computeOnClient(mc -> mc.options.renderDistance().get());
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(RENDER_DISTANCE);
			mc.options.save();
		});
		List<Stop> queue = new ArrayList<>(stops);
		server.runCommand("gamemode spectator @p");
		server.runCommand("weather clear");
		server.runCommand("time set noon");
		Visuals.hud(context, false);

		try {
			for (int i = 0; i < queue.size(); i++) {
				Stop stop = queue.get(i);
				int slot = slots.merge(stop.level(), 1, Integer::sum) - 1;
				BlockPos slotPos = new BlockPos(LINE_START + slot * SPACING, 160, LINE_START);

				try {
					if (!stop.level().equals(current)) {
						cross(context, server, stop.level(), Vec3.atBottomCenterOf(slotPos));
						current = stop.level();
					}

					queue.addAll(i + 1, visit(context, world, server, stop, i + 1, slotPos));
				} catch (AssertionError | RuntimeException e) {
					failed.add(stop.name() + " (" + stop.id() + "): " + e.getMessage());
					OldRepublicMod.LOGGER.warn("Structure tour: {} failed", stop.name(), e);
				} finally {
					in(server, stop.level(), "forceload remove all");
				}
			}
		} finally {
			ResourceKey<Level> from = current;
			server.runOnServer(s -> {
				ServerPlayer p = player(s);

				if (!from.equals(Level.OVERWORLD)) {
					TravelService.moveAcross(p, s.overworld(), new Vec3(origin[0], origin[1], origin[2]), (float) origin[3]);
				}
			});
			context.waitFor(client -> client.level != null && client.level.dimension().equals(Level.OVERWORLD), CLIENT_TIMEOUT);
			server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.1f 0", origin[0], origin[1], origin[2], origin[3]));
			server.runCommand("gamemode survival @p");
			server.runCommand("time set noon");
			context.runOnClient(mc -> {
				mc.options.renderDistance().set(renderDistance);
				mc.options.save();
			});
			// The loading screen of the move home takes key presses: the HUD key works once it is gone.
			context.waitFor(client -> client.gui.screen() == null, CLIENT_TIMEOUT);
			settle(context, world);
			Visuals.hud(context, true);
			context.waitTicks(20);
		}

		if (!failed.isEmpty()) {
			throw new AssertionError("Structure tour: " + failed.size() + " of " + queue.size() + " stops failed: " + String.join("; ", failed));
		}
	}

	// ---------------------------------------------------------------- one stop

	/** Places and photographs one stop; returns the template stops of the variants a {@link Stop#variants} stop did not show. */
	private static List<Stop> visit(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, Stop stop, int number,
			BlockPos slot) {
		Placed placed = switch (stop.kind()) {
			case WORLDGEN -> worldgen(server, stop, slot);
			case TEMPLATE -> template(server, stop, slot);
			case HUB -> hub(server, stop);
		};
		List<View> views = server.computeOnServer(s -> views(level(s, stop.level()), placed, stop));
		OldRepublicMod.LOGGER.info("Structure tour {}: {} {} at {} (box {}x{}x{}, ground {}, {} pieces)", number, stop.name(), stop.id(),
				placed.box().getCenter().toShortString(), placed.box().getXSpan(), placed.box().getYSpan(), placed.box().getZSpan(), placed.ground(),
				placed.frames().size());

		for (View view : views) {
			shoot(context, world, server, stop.level(), view, String.format(Locale.ROOT, "oldrepublic_m6_%03d_%s_%s", number, stop.name(), view.suffix()));
		}

		List<Stop> others = new ArrayList<>();

		if (stop.kind() == Kind.WORLDGEN && stop.buried() >= 0) {
			for (Close close : stop.closes()) {
				if (placed.frames().stream().noneMatch(f -> f.template().equals(close.template()))) {
					String path = close.template().getPath();
					others.add(new Stop(stop.level(), Kind.TEMPLATE, close.template(), stop.name() + "_" + path.substring(path.lastIndexOf('/') + 1),
							stop.buried(), null, List.of(close)));
				}
			}
		}

		return others;
	}

	/** The instance of a worldgen structure the world generates nearest the slot, loaded with its surroundings. */
	private static Placed worldgen(TestServerContext server, Stop stop, BlockPos slot) {
		StructureStart start = server.computeOnServer(s -> locate(level(s, stop.level()), stop.id(), slot));

		if (start == null) {
			throw new AssertionError("none within " + LOCATE_RADIUS + " placement cells of " + slot.toShortString());
		}

		BoundingBox box = start.getBoundingBox();
		load(server, stop.level(), box.inflatedBy(MARGIN));
		List<Frame> frames = new ArrayList<>();

		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single) {
				frames.add(new Frame(single.getTemplateLocation(), pool.getPosition(), pool.getRotation()));
			}
		}

		int ground = server.computeOnServer(s -> groundAround(level(s, stop.level()), box));
		return new Placed(box, ground, frames);
	}

	/**
	 * The start of the instance of {@code structure} nearest {@code slot}, with its start chunk
	 * generated (so the start is known); null when there is none in reach.
	 */
	private static @Nullable StructureStart locate(ServerLevel level, Identifier structure, BlockPos slot) {
		Holder.Reference<Structure> holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structure).orElse(null);

		if (holder == null) {
			throw new AssertionError("no structure " + structure);
		}

		Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), slot,
				LOCATE_RADIUS, false);

		if (found == null) {
			return null;
		}

		ChunkPos chunk = ChunkPos.containing(found.getFirst());
		ChunkAccess access = level.getChunk(chunk.x(), chunk.z());
		StructureStart start = access.getStartForStructure(holder.value());

		if (start == null || !start.isValid()) {
			throw new AssertionError("located at " + found.getFirst().toShortString() + ", but that chunk holds no start of it");
		}

		return start;
	}

	/**
	 * A runtime template on the flattest dry spot near the slot; for an overlay stop, its base placed
	 * so and the template stamped over it at the base's origin and rotation.
	 */
	private static Placed template(TestServerContext server, Stop stop, BlockPos slot) {
		if (stop.base() == null) {
			return place(server, stop, slot, stop.id());
		}

		Placed base = place(server, stop, slot, stop.base());
		Frame frame = base.frames().getFirst();
		server.runOnServer(s -> {
			ServerLevel level = level(s, stop.level());
			StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(frame.rotation()).setRotationPivot(BlockPos.ZERO).setIgnoreEntities(true);
			// As the game stamps it (StorySites#stampOver): without shape updates, so a replaced bed or door stays whole.
			loadTemplate(level, stop.id()).placeInWorld(level, frame.origin(), frame.origin(), settings.setKnownShape(true), level.getRandom(),
					Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		});
		return new Placed(base.box(), base.ground(), List.of(frame, new Frame(stop.id(), frame.origin(), frame.rotation())));
	}

	/**
	 * {@code id} on dry ground near the slot: a site template whose stop names no buried layers as the
	 * game places a far site ({@link #far}); anything else, or a site that finds no far ground, on the
	 * flattest dry spot near the slot ({@link #flattest}), with {@code /oldrepublic site place} when it
	 * is such a site template, else stamped with the ground fitted.
	 */
	private static Placed place(TestServerContext server, Stop stop, BlockPos slot, Identifier id) {
		if (SiteKind.ofTemplate(id) != null && stop.buried() < 0) {
			Placed far = far(server, stop, slot, id);

			if (far != null) {
				return far;
			}
		}

		Vec3i size = server.computeOnServer(s -> loadTemplate(level(s, stop.level()), id).getSize());
		BlockPos stand = server.computeOnServer(s -> flattest(level(s, stop.level()), slot, size));
		int reach = Math.max(size.getX(), size.getZ()) / 2 + MARGIN;
		load(server, stop.level(), new BoundingBox(stand.getX() - reach, stand.getY(), stand.getZ() - reach, stand.getX() + reach, stand.getY(),
				stand.getZ() + reach));

		if (SiteKind.ofTemplate(id) != null && stop.buried() < 0) {
			in(server, stop.level(), String.format(Locale.ROOT, "oldrepublic site place %s %d %d %d", id, stand.getX(), stand.getY(), stand.getZ()));
			SitePiece piece = server.computeOnServer(s -> {
				for (SitePiece candidate : StructureLocator.piecesNear(level(s, stop.level()), stand, Math.max(size.getX(), size.getZ()))) {
					if (candidate.runtime() && candidate.template().equals(id)) {
						return candidate;
					}
				}

				return null;
			});

			if (piece == null) {
				throw new AssertionError("nothing was placed at " + stand.toShortString() + " (see the command's reply in the log)");
			}

			return new Placed(piece.box(), stand.getY(), List.of(new Frame(piece.template(), piece.origin(), piece.rotation())));
		}

		int buried = stop.buried() >= 0 ? stop.buried() : SiteMeta.of(id).buried();
		return server.computeOnServer(s -> {
			ServerLevel level = level(s, stop.level());
			StructureTemplate template = loadTemplate(level, id);
			BlockPos origin = RuntimeSites.templateOrigin(template, stand, Rotation.NONE, buried);
			StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(Rotation.NONE).setRotationPivot(BlockPos.ZERO).setIgnoreEntities(true);
			BoundingBox box = template.getBoundingBox(settings, origin);
			RuntimeSites.adaptTerrain(level, box, stand.getY() - 1);
			template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
			return new Placed(box, stand.getY(), List.of(new Frame(id, origin, Rotation.NONE)));
		});
	}

	/**
	 * A site template placed as the game places a far site ({@link FarSites}): ground chosen from the
	 * world generator's noise within {@link #FAR_REACH} of the slot (dry, of the kind its site meta
	 * prefers, clear of villages and the like), loaded, and the site placed there with its terrain
	 * fitted and its entrance facing the slot. Null, with a warning, when no ground is found.
	 */
	private static @Nullable Placed far(TestServerContext server, Stop stop, BlockPos slot, Identifier id) {
		String key = "structure_tour/" + stop.name();
		server.runOnServer(s -> FarSites.request(level(s, stop.level()), FarSites.Request.of(key, null, id, slot, 0, FAR_REACH)));
		server.waitFor(s -> {
			FarSites.Status status = FarSites.poll(level(s, stop.level()), key);
			return status.placed() || status.failed();
		}, LOAD_TIMEOUT);
		FarSites.Status status = server.computeOnServer(s -> FarSites.poll(level(s, stop.level()), key));
		RuntimeSites.Placement placement = status.placement();

		if (placement == null) {
			OldRepublicMod.LOGGER.warn("Structure tour: no far ground for {} ({}); it goes on the flattest spot near its slot", stop.name(),
					status.detail());
			return null;
		}

		BoundingBox box = placement.piece().box();
		load(server, stop.level(), box.inflatedBy(MARGIN));
		return new Placed(box, placement.origin().getY() + RuntimeSites.buriedOf(id),
				List.of(new Frame(placement.piece().template(), placement.origin(), placement.rotation())));
	}

	/** A planet's hub where the game places it, and every hub piece (and the Sepulchre) recorded around it. */
	private static Placed hub(TestServerContext server, Stop stop) {
		Planets.Planet planet = planet(stop.level());
		server.runOnServer(s -> PlanetHubs.ensure(s, planet));
		server.waitFor(s -> PlanetHubs.hub(s, planet) != null, HUB_TIMEOUT);
		BlockPos centre = server.computeOnServer(s -> PlanetHubs.hub(s, planet));
		load(server, stop.level(), new BoundingBox(centre).inflatedBy(48 + MARGIN));
		return server.computeOnServer(s -> {
			List<Frame> frames = new ArrayList<>();
			BoundingBox box = null;

			for (SitePiece piece : StructureLocator.piecesNear(level(s, stop.level()), centre, 48)) {
				// The Sepulchre under the Proving gives a frame for a chosen view, but the hub's box alone frames the shots.
				if (piece.kind() == SiteKind.PLANET_HUB || piece.kind() == SiteKind.SEPULCHRE) {
					frames.add(new Frame(piece.template(), piece.origin(), piece.rotation()));
				}

				if (piece.kind() == SiteKind.PLANET_HUB) {
					box = box == null ? piece.box() : BoundingBox.encapsulatingBoxes(List.of(box, piece.box())).orElse(box);
				}
			}

			if (box == null) {
				throw new AssertionError("the hub is placed at " + centre.toShortString() + " but no hub piece is recorded there");
			}

			return new Placed(box, centre.getY(), frames);
		});
	}

	// ---------------------------------------------------------------- views

	/**
	 * The stop's pictures: the aerial, the ground view (neither for a stop wholly under the ground),
	 * then its chosen views (each in the first piece of its template).
	 */
	private static List<View> views(ServerLevel level, Placed placed, Stop stop) {
		BoundingBox box = placed.box();
		int height = Math.max(0, box.maxY() - placed.ground());
		Vec3 middle = new Vec3(box.minX() + box.getXSpan() / 2.0, placed.ground() + Mth.clamp(height * 0.3, 2, 16), box.minZ() + box.getZSpan() / 2.0);
		List<View> views = new ArrayList<>();

		if (box.maxY() >= placed.ground()) {
			views.add(new View("aerial", aerial(level, box, middle), middle, false));
			views.add(ground(level, box, placed.ground(), height));
		}

		for (int i = 0; i < stop.closes().size(); i++) {
			Close close = stop.closes().get(i);
			Frame frame = placed.frames().stream().filter(f -> f.template().equals(close.template())).findFirst().orElse(null);

			if (frame == null) {
				OldRepublicMod.LOGGER.warn("Structure tour: {} has no {} piece for its chosen view", stop.name(), close.template());
				continue;
			}

			views.add(new View(i == 0 ? "close" : "close" + (i + 1), frame.centre(close.eye()), frame.centre(close.look()), close.night()));
		}

		return views;
	}

	/** The aerial camera: from the south-east, far enough back to frame the box, raised over terrain in the way. */
	private static Vec3 aerial(ServerLevel level, BoundingBox box, Vec3 middle) {
		double span = Math.max(box.getXSpan(), box.getZSpan());
		double distance = Mth.clamp(span * 0.7 + 10, 20, 130);
		double flat = distance * Math.cos(Math.toRadians(AERIAL_PITCH)) / Math.sqrt(2);
		Vec3 eye = middle.add(flat, distance * Math.sin(Math.toRadians(AERIAL_PITCH)), flat);

		for (int rise = 0; rise <= AERIAL_RISE; rise += 4) {
			Vec3 at = eye.add(0, rise, 0);

			if (at.y > surface(level, Mth.floor(at.x), Mth.floor(at.z)) + 3 && clearOfTerrain(level, at, middle, box)) {
				return at;
			}
		}

		OldRepublicMod.LOGGER.warn("Structure tour: terrain still blocks the aerial view of {} after rising {} blocks", box, AERIAL_RISE);
		return eye.add(0, AERIAL_RISE, 0);
	}

	/**
	 * The ground view: from eye height on dry ground just in front of the box, looking at its middle;
	 * the first side (south, east, west, north) whose view no terrain or tall plant blocks, the camera
	 * raised up to {@link #GROUND_RISE} blocks over a bush or a bank; then the same sides again with the
	 * camera raised up to {@link #GROUND_RISE_HIGH} blocks over the trees; else a low view from the
	 * south, 15 degrees down, over whatever is in the way.
	 */
	private static View ground(ServerLevel level, BoundingBox box, int groundY, int height) {
		Vec3 middle = new Vec3(box.minX() + box.getXSpan() / 2.0, groundY + Mth.clamp(height * 0.4, 2, 12), box.minZ() + box.getZSpan() / 2.0);
		double span = Math.max(box.getXSpan(), box.getZSpan());
		double back = span / 2.0 + Mth.clamp(span * 0.15, 5, 16);
		int[][] sides = {{0, 1}, {1, 0}, {-1, 0}, {0, -1}};
		int[][] passes = {{0, GROUND_RISE, 2}, {GROUND_RISE + 4, GROUND_RISE_HIGH, 4}};

		for (int[] pass : passes) {
			for (int[] side : sides) {
				int x = Mth.floor(middle.x + side[0] * back);
				int z = Mth.floor(middle.z + side[1] * back);

				if (!dry(level, x, z)) {
					continue;
				}

				int y = surface(level, x, z);

				for (int rise = pass[0]; rise <= pass[1]; rise += pass[2]) {
					Vec3 eye = new Vec3(x + 0.5, y + rise + EYE, z + 0.5);

					if (clearOfTerrain(level, eye, middle, box)) {
						return new View("ground", eye, middle, false);
					}
				}
			}
		}

		double distance = span * 0.6 + 12;
		Vec3 eye = middle.add(0, distance * Math.sin(Math.toRadians(15)), distance * Math.cos(Math.toRadians(15)));
		int floor = surface(level, Mth.floor(eye.x), Mth.floor(eye.z)) + 2;
		return new View("ground", eye.y < floor ? new Vec3(eye.x, floor, eye.z) : eye, middle, false);
	}

	/**
	 * True when nothing natural stands between {@code eye} and {@code target} before the line reaches
	 * the box. The line meets blocks by their outlines, so a reed bed with nothing to collide with
	 * still stands in the way.
	 */
	private static boolean clearOfTerrain(ServerLevel level, Vec3 eye, Vec3 target, BoundingBox box) {
		BlockHitResult hit = level.clip(new ClipContext(eye, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player(level.getServer())));
		return hit.getType() == HitResult.Type.MISS || box.inflatedBy(2).isInside(hit.getBlockPos()) || !terrain(level.getBlockState(hit.getBlockPos()));
	}

	/** Natural ground, rock, sand, snow, foliage, a plant taller than a player's knee, or a fluid. */
	private static boolean terrain(BlockState state) {
		return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(BlockTags.SNOW)
				|| state.is(BlockTags.TERRACOTTA) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.GRAVEL)
				|| state.is(Blocks.SANDSTONE) || state.is(Blocks.CLAY) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BAMBOO)
				|| state.is(Blocks.CACTUS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.VINE)
				|| !state.getFluidState().isEmpty();
	}

	// ---------------------------------------------------------------- the camera

	/** Puts the spectator at the view's eye looking at its target, waits until everything in view is drawn, and takes the shot. */
	private static void shoot(ClientGameTestContext context, TestSingleplayerContext world, TestServerContext server, ResourceKey<Level> level, View view,
			String shot) {
		Vec3 d = view.look().subtract(view.eye());
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		in(server, level, String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.2f %.2f", view.eye().x, view.eye().y - EYE, view.eye().z, yaw, pitch));
		int chunkX = SectionPos.blockToSectionCoord(Mth.floor(view.eye().x));
		int chunkZ = SectionPos.blockToSectionCoord(Mth.floor(view.eye().z));
		context.waitFor(client -> client.level != null && client.level.dimension().equals(level) && client.level.hasChunk(chunkX, chunkZ), CLIENT_TIMEOUT);
		settle(context, world);
		server.runCommand(view.night() ? "time set midnight" : "time set noon");
		context.waitTicks(SETTLE);
		context.takeScreenshot(shot);

		if (view.night()) {
			server.runCommand("time set noon");
		}
	}

	/**
	 * Waits until the client holds every chunk within {@link #RENDER_DISTANCE} - 1 of the camera, round,
	 * and has drawn them all; after {@link #RENDER_TIMEOUT} the shot is taken as it stands, with a
	 * warning. Fabric's own download wait wants the whole square of the render distance, whose corners
	 * the server's round view never sends beyond six chunks.
	 */
	private static void settle(ClientGameTestContext context, TestSingleplayerContext world) {
		try {
			context.waitFor(StructureTour::chunksInView, RENDER_TIMEOUT);
			world.getConnection().waitForChunksRender(false, RENDER_TIMEOUT);
		} catch (AssertionError e) {
			OldRepublicMod.LOGGER.warn("Structure tour: the view was not all drawn within {} ticks; the shot is taken as it stands", RENDER_TIMEOUT);
		}
	}

	/** True when the client holds every chunk within the render distance less one of the camera, round. */
	private static boolean chunksInView(Minecraft client) {
		if (client.level == null || client.player == null) {
			return false;
		}

		int reach = Math.min(RENDER_DISTANCE, client.options.getEffectiveRenderDistance()) - 1;
		int centreX = SectionPos.blockToSectionCoord(client.player.getBlockX());
		int centreZ = SectionPos.blockToSectionCoord(client.player.getBlockZ());

		for (int dz = -reach; dz <= reach; dz++) {
			for (int dx = -reach; dx <= reach; dx++) {
				if (dx * dx + dz * dz <= reach * reach && client.level.getChunk(centreX + dx, centreZ + dz, ChunkStatus.FULL, false) == null) {
					return false;
				}
			}
		}

		return true;
	}

	/** Crosses to {@code level} at {@code at} (the only cross-level move) and waits until the client is there. */
	private static void cross(ClientGameTestContext context, TestServerContext server, ResourceKey<Level> level, Vec3 at) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			TravelService.moveAcross(p, level(s, level), at, p.getYRot());
		});
		context.waitFor(client -> client.level != null && client.level.dimension().equals(level), CLIENT_TIMEOUT);
	}

	// ---------------------------------------------------------------- the world

	/** Force-loads every chunk of {@code box} (in tiles of at most 16 by 16 chunks) and waits until all are loaded. */
	private static void load(TestServerContext server, ResourceKey<Level> level, BoundingBox box) {
		int minX = SectionPos.blockToSectionCoord(box.minX());
		int maxX = SectionPos.blockToSectionCoord(box.maxX());
		int minZ = SectionPos.blockToSectionCoord(box.minZ());
		int maxZ = SectionPos.blockToSectionCoord(box.maxZ());

		for (int x = minX; x <= maxX; x += 16) {
			for (int z = minZ; z <= maxZ; z += 16) {
				in(server, level, String.format(Locale.ROOT, "forceload add %d %d %d %d", x << 4, z << 4, (Math.min(x + 15, maxX) << 4) + 15,
						(Math.min(z + 15, maxZ) << 4) + 15));
			}
		}

		server.waitFor(s -> {
			ServerLevel l = level(s, level);

			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					if (!l.hasChunk(x, z)) {
						return false;
					}
				}
			}

			return true;
		}, LOAD_TIMEOUT);
	}

	/**
	 * The flattest dry spot near the slot for a footprint of {@code size}, judged from the world
	 * generator's noise heights (nothing is generated to judge it): of the points of the search grid
	 * ({@link #SEARCH_ALONG}, {@link #SEARCH_ACROSS}), nearest the slot first, the first whose middle
	 * and corners are all dry and within {@link #FLAT_ENOUGH} blocks of each other, else the one with
	 * the least spread, dry before wet. Its height is the generated surface's; loads that one chunk.
	 */
	private static BlockPos flattest(ServerLevel level, BlockPos slot, Vec3i size) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		int halfX = size.getX() / 2 + 2;
		int halfZ = size.getZ() / 2 + 2;
		int[][] samples = {{0, 0}, {-halfX, -halfZ}, {halfX, -halfZ}, {-halfX, halfZ}, {halfX, halfZ}};
		List<BlockPos> points = new ArrayList<>();

		for (int dx = -SEARCH_ALONG; dx <= SEARCH_ALONG; dx += SEARCH_STEP) {
			for (int dz = -SEARCH_ACROSS; dz <= SEARCH_ACROSS; dz += SEARCH_STEP) {
				points.add(slot.offset(dx, 0, dz));
			}
		}

		points.sort(Comparator.comparingDouble(point -> point.distSqr(slot)));
		BlockPos best = slot;
		int bestScore = Integer.MAX_VALUE;

		for (BlockPos point : points) {
			int low = Integer.MAX_VALUE;
			int high = Integer.MIN_VALUE;
			int wet = 0;

			for (int[] sample : samples) {
				int x = point.getX() + sample[0];
				int z = point.getZ() + sample[1];
				// Over water the surface is the water's and the floor the bed's; on land they are one.
				int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
				low = Math.min(low, floor);
				high = Math.max(high, floor);
				wet += generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, random) > floor ? 1 : 0;

				if (wet > 0 && bestScore < 1000) {
					break;
				}
			}

			int score = high - low + wet * 1000;

			if (score < bestScore) {
				bestScore = score;
				best = point;
			}

			if (score <= FLAT_ENOUGH) {
				break;
			}
		}

		return new BlockPos(best.getX(), surface(level, best.getX(), best.getZ()), best.getZ());
	}

	/** The ground level around a box: the median surface height just outside its four corners. */
	private static int groundAround(ServerLevel level, BoundingBox box) {
		int[] heights = {
				surface(level, box.minX() - 3, box.minZ() - 3),
				surface(level, box.maxX() + 3, box.minZ() - 3),
				surface(level, box.minX() - 3, box.maxZ() + 3),
				surface(level, box.maxX() + 3, box.maxZ() + 3)};
		Arrays.sort(heights);
		return (heights[1] + heights[2]) / 2;
	}

	/** True when the column at x, z ends on something that is not a fluid. */
	private static boolean dry(ServerLevel level, int x, int z) {
		int y = surface(level, x, z) - 1;
		return level.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty();
	}

	/** The first free height of the column at x, z, ignoring leaves; loads the chunk. */
	private static int surface(ServerLevel level, int x, int z) {
		level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	private static StructureTemplate loadTemplate(ServerLevel level, Identifier id) {
		return level.getStructureManager().get(id).orElseThrow(() -> new AssertionError("no template " + id));
	}

	private static Planets.Planet planet(ResourceKey<Level> level) {
		for (Planets.Planet planet : Planets.Planet.values()) {
			if (planet.level().equals(level)) {
				return planet;
			}
		}

		throw new AssertionError(level.identifier() + " is not a planet");
	}

	/** Runs {@code command} in {@code level}. */
	private static void in(TestServerContext server, ResourceKey<Level> level, String command) {
		server.runCommand("execute in " + level.identifier() + " run " + command);
	}

	private static ServerLevel level(MinecraftServer server, ResourceKey<Level> key) {
		ServerLevel level = server.getLevel(key);

		if (level == null) {
			throw new AssertionError("no level " + key.identifier());
		}

		return level;
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private StructureTour() {
	}
}
