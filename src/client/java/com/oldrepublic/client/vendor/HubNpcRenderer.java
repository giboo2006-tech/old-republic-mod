package com.oldrepublic.client.vendor;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.mentor.MentorModel;
import com.oldrepublic.client.mentor.MentorRenderState;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.vendor.hub.HubNpc;
import com.oldrepublic.vendor.hub.HubNpcs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renders the hub people (spec MILESTONE_4.md 3.5, 7.8): the mentors' robed humanoid model with a
 * skin per person ({@code textures/entity/hub/<variant>.png}). Watch people (Ansa, apprentices,
 * Selvaren's vendors) wear the hood; Imperial ones (Kaddor, acolytes, guards, Varkhast's vendors,
 * Zarvos's echo) the half-cape, and so does every other hub's people (Saltgate's and the holdfast's,
 * MILESTONE_5.md 3.1). A variant without its own skin falls back to its role's skin.
 */
public class HubNpcRenderer extends HumanoidMobRenderer<HubNpc, MentorRenderState, MentorModel> {
	/** Whether each skin exists, looked up once per id. */
	private static final Map<Identifier, Boolean> PRESENT = new HashMap<>();

	public HubNpcRenderer(EntityRendererProvider.Context context) {
		super(context, new MentorModel(context.bakeLayer(MentorModel.LAYER)), 0.5f);
	}

	@Override
	public MentorRenderState createRenderState() {
		return new HubRenderState();
	}

	@Override
	public void extractRenderState(HubNpc npc, MentorRenderState state, float partialTick) {
		super.extractRenderState(npc, state, partialTick);
		String variant = npc.variant();
		state.jedi = hooded(npc, variant);
		state.meditating = false;

		if (state instanceof HubRenderState hub) {
			hub.texture = skin(npc, variant);
		}
	}

	@Override
	public Identifier getTextureLocation(MentorRenderState state) {
		return state instanceof HubRenderState hub && hub.texture != null ? hub.texture : HubNpcs.texture("apprentice_0");
	}

	/** Watch people wear the hood; the Empire's the half-cape. */
	private static boolean hooded(HubNpc npc, String variant) {
		return switch (npc.role()) {
			case APPRENTICE -> true;
			case ACOLYTE, GUARD -> false;
			case HOST -> variant.equals("ansa");
			case VENDOR -> !variant.equals("imperial_quartermaster") && hoodedVendors(Planets.Planet.of(npc.level().dimension()));
		};
	}

	/** Whether a world's vendors wear the Watch hood (3.1): the home world's and Selvaren's, and outside the worlds; every other hub's wear the half-cape. */
	private static boolean hoodedVendors(Planets.@Nullable Planet planet) {
		if (planet == null) {
			return true;
		}

		return switch (planet) {
			case HOME, SELVAREN -> true;
			default -> false;
		};
	}

	/** The variant's own skin when the resource exists, else the role's. */
	private static Identifier skin(HubNpc npc, String variant) {
		Identifier own = variant.isEmpty() ? null : HubNpcs.texture(variant);

		if (own != null && PRESENT.computeIfAbsent(own, id -> Minecraft.getInstance().getResourceManager().getResource(id).isPresent())) {
			return own;
		}

		return HubNpcs.texture(switch (npc.role()) {
			case HOST -> "ansa";
			case APPRENTICE -> "apprentice_0";
			case ACOLYTE -> "acolyte_0";
			case GUARD -> "proving_guard";
			case VENDOR -> "spacer";
		});
	}

	/** Forgets which skins exist (after a resource reload). */
	static void clearCache() {
		PRESENT.clear();
	}

	/** The mentors' render state with the person's skin. */
	public static class HubRenderState extends MentorRenderState {
		public Identifier texture;
	}
}
