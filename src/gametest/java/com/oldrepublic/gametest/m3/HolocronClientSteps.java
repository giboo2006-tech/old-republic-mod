package com.oldrepublic.gametest.m3;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.client.holocron.HolocronClient;
import com.oldrepublic.client.quest.JournalScreen;
import com.oldrepublic.gametest.m2.ForceClientSteps;
import com.oldrepublic.holocron.HolocronAttunement;
import com.oldrepublic.holocron.HolocronBalance;
import com.oldrepublic.holocron.HolocronData;
import com.oldrepublic.holocron.HolocronEffects;
import com.oldrepublic.holocron.HolocronKind;
import com.oldrepublic.holocron.HolocronPedestals;
import com.oldrepublic.holocron.HolocronService;
import com.oldrepublic.holocron.HolocronStamp;
import com.oldrepublic.holocron.PedestalSite;
import com.oldrepublic.network.EffectEvents;
import com.oldrepublic.registry.ModComponents;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;

/**
 * Milestone 3 client test steps for the holocron feature (pedestals, attunement, Sense and Lift).
 * {@code ExplorationClientTest} runs them in the shared seeded exploration world (spec 17.2).
 * Screenshots are {@code oldrepublic_m3_<nn>_<what>} with {@code nn} from 30 to 39: the force
 * feature's 30 to 34 through {@link ForceClientSteps#runM3}, then the holocron feature's own:
 *
 * <ul>
 * <li>35 {@code gatekeeper_projection}: the risen Jedi holocron and its projected gatekeeper, on a
 * small stage built high above the player, through the pedestal test seam (story gate skipped);
 * the moment is then cancelled, so nothing is attuned;</li>
 * <li>36 {@code holocrons_tab}: the Holocrons journal tab after four operator grants and three
 * echo shards, reading the first Jedi holocron on its right page;</li>
 * <li>37 {@code archive_keep}: the Archive "keep" effect at the pedestal (the effect alone; the
 * Archive choice itself stays open for the boss steps).</li>
 * </ul>
 *
 * <p>The player's holocron data, the granted holocron items and shards, the stage and the
 * player's position are put back afterwards; the grants' XP and teachings remain.
 */
public final class HolocronClientSteps {
	/** The stage's floor height: above the seeded world's terrain near spawn. */
	private static final int STAGE_Y = 240;
	/** The pedestal stands this many blocks south (+Z) of the player. */
	private static final int PEDESTAL_DZ = 3;
	private static final List<String> SHELF_GRANTS = List.of("wayfarers_compass", "wayfarer_pell", "jedi_morrane", "sith_zarvos");
	private static final int SHARDS = 3;

	public static void run(ClientGameTestContext context, TestServerContext server) {
		ForceClientSteps.runM3(context, server);

		double[] origin = new double[5];
		HolocronData[] before = new HolocronData[1];
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			origin[0] = p.getX();
			origin[1] = p.getY();
			origin[2] = p.getZ();
			origin[3] = p.getYRot();
			origin[4] = p.getXRot();
			before[0] = HolocronService.data(p);
		});
		int bx = Mth.floor(origin[0]);
		int bz = Mth.floor(origin[2]);

		buildStage(server, bx, bz, "minecraft:polished_deepslate", "minecraft:deepslate_bricks");
		server.runCommand(String.format(Locale.ROOT, "tp @p %d.5 %d %d.5 0 12", bx, STAGE_Y, bz));
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(30);

		projection(context, server, bx, bz);
		shelf(context, server);
		archiveKeep(context, server, bx, bz);

		// Put everything back: holocron data, granted items and shards, position, stage, camera.
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			removeGrantedItems(p, before[0]);
			HolocronService.commit(p, before[0]);
		});
		server.runCommand("clear @p " + OldRepublicMod.id("holocron_shard") + " " + SHARDS);
		server.runCommand(String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.1f %.1f", origin[0], origin[1], origin[2], origin[3], origin[4]));
		buildStage(server, bx, bz, "minecraft:air", "minecraft:air");
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(20);
	}

	/** A floor with a dark back wall behind where the figure stands, so the projection reads. */
	private static void buildStage(TestServerContext server, int bx, int bz, String floor, String wall) {
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", bx - 5, STAGE_Y, bz - 2, bx + 5, STAGE_Y + 6, bz + 9));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s", bx - 5, STAGE_Y - 1, bz - 2, bx + 5, STAGE_Y - 1, bz + 9, floor));
		server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d %s", bx - 5, STAGE_Y, bz + 8, bx + 5, STAGE_Y + 5, bz + 8, wall));
	}

	/** 35: the gatekeeper projected from the risen holocron, with its dialogue open. */
	private static void projection(ClientGameTestContext context, TestServerContext server, int bx, int bz) {
		BlockPos pedestal = new BlockPos(bx, STAGE_Y, bz + PEDESTAL_DZ);
		server.runCommand(String.format(Locale.ROOT, "setblock %d %d %d %s[kind=jedi]", pedestal.getX(), pedestal.getY(), pedestal.getZ(),
				OldRepublicMod.id("holocron_pedestal")));
		context.waitTicks(10);

		HolocronPedestals.Outcome outcome = server.computeOnServer(s -> {
			ServerPlayer p = player(s);
			ServerLevel level = p.level();
			long day = level.getGameTime() / Math.max(1, HolocronBalance.get().restoreCooldownTicks);
			PedestalSite site = PedestalSite.of(level.dimension(), pedestal, "test|holocron_client_steps", null);
			return HolocronPedestals.use(p, site, HolocronKind.JEDI, false, true, day, RandomSource.create(35L), creature -> false);
		});

		if (outcome != HolocronPedestals.Outcome.ATTUNE_STARTED) {
			throw new AssertionError("The pedestal gave " + outcome + " instead of an attunement; screenshot 35 would show no gatekeeper");
		}

		// The rise, the gatekeeper delay and the figure assembling, with margin.
		HolocronBalance b = HolocronBalance.get();
		context.waitTicks(b.riseTicks + b.gatekeeperDelayTicks + 40);
		context.takeScreenshot("oldrepublic_m3_35_gatekeeper_projection");

		// Nothing is attuned: the figure folds and the holocron sinks back.
		server.runOnServer(s -> HolocronAttunement.cancel(player(s), "client_test"));
		context.waitTicks(40);
	}

	/** 36: the Holocrons tab with four holocrons attuned and carried, and three echo shards. */
	private static void shelf(ClientGameTestContext context, TestServerContext server) {
		for (String id : SHELF_GRANTS) {
			server.runCommand("oldrepublic holocron grant @p " + id);
		}

		server.runCommand("oldrepublic holocron shards @p " + SHARDS);
		// The attachment syncs, and the four banners and toasts of the grants play out first.
		context.waitTicks(260);
		context.runOnClient(client -> {
			HolocronClient.selectOnShelf("jedi_morrane");
			JournalScreen.openOnTab("holocrons");
		});
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m3_36_holocrons_tab");
		context.setScreen(() -> null);
		context.waitTicks(5);
	}

	/** 37: the Archive kept: the three seams warm and their light drifts into the player. */
	private static void archiveKeep(ClientGameTestContext context, TestServerContext server, int bx, int bz) {
		server.runOnServer(s -> {
			ServerPlayer p = player(s);
			Vec3 plinthTop = new Vec3(bx + 0.5, STAGE_Y + 1.0, bz + PEDESTAL_DZ + 0.5);
			EffectEvents.around(p.level(), HolocronEffects.ARCHIVE, plinthTop, null, p.getId(), 1f, HolocronEffects.ARCHIVE_KEEP);
		});
		context.waitTicks(10);
		context.takeScreenshot("oldrepublic_m3_37_archive_keep");
		context.waitTicks(30);
	}

	/** Removes holocron items of ids the player had not attuned before the steps (the grants' copies). */
	private static void removeGrantedItems(ServerPlayer p, HolocronData before) {
		Set<String> kept = new HashSet<>(before.attuned);
		Inventory inventory = p.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			HolocronStamp stamp = inventory.getItem(i).get(ModComponents.HOLOCRON);

			if (stamp != null && !kept.contains(stamp.id())) {
				inventory.removeItemNoUpdate(i);
			}
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private HolocronClientSteps() {
	}
}
