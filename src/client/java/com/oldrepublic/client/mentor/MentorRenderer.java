package com.oldrepublic.client.mentor;

import com.oldrepublic.OldRepublicMod;
import com.oldrepublic.mentor.MentorEntity;
import com.oldrepublic.mentor.MentorRole;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/** Renders Master Saelen and Overseer Sorn: their own skins, the saber in hand, the kneel while meditating. */
public class MentorRenderer extends HumanoidMobRenderer<MentorEntity, MentorRenderState, MentorModel> {
	private static final Identifier JEDI = OldRepublicMod.id("textures/entity/jedi_mentor.png");
	private static final Identifier SITH = OldRepublicMod.id("textures/entity/sith_mentor.png");

	public MentorRenderer(EntityRendererProvider.Context context) {
		super(context, new MentorModel(context.bakeLayer(MentorModel.LAYER)), 0.5f);
	}

	@Override
	public MentorRenderState createRenderState() {
		return new MentorRenderState();
	}

	@Override
	public void extractRenderState(MentorEntity mentor, MentorRenderState state, float partialTick) {
		super.extractRenderState(mentor, state, partialTick);
		state.jedi = mentor.role() == MentorRole.JEDI;
		state.meditating = mentor.isMeditating();
	}

	@Override
	public Identifier getTextureLocation(MentorRenderState state) {
		return state.jedi ? JEDI : SITH;
	}
}
