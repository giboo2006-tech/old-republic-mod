package com.oldrepublic.gametest;

import com.oldrepublic.gametest.visuals.ForceEnergyLook;
import com.oldrepublic.gametest.visuals.ForceKineticLook;
import com.oldrepublic.gametest.visuals.SaberLook;
import com.oldrepublic.gametest.visuals.Visuals;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Close-up pictures of how the saber and the Force powers look, on a plain stage of the superflat test world, from the
 * player's own camera and from a free camera beside them ({@link Visuals}). Screenshots are
 * {@code oldrepublic_look_<steps>_<nn>_<what>}. The {@code visuals} workflow runs this test alone, with only the look steps
 * it names; the build workflow runs it with every other client test.
 */
public class VisualsClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			Visuals.stage(server);
			context.waitTicks(20);

			if (Visuals.wanted("saber")) {
				SaberLook.run(context, server);
			}

			if (Visuals.wanted("force_kinetic")) {
				ForceKineticLook.run(context, server);
			}

			if (Visuals.wanted("force_energy")) {
				ForceEnergyLook.run(context, server);
			}

			Visuals.release(context, server);
		}
	}
}
