package com.oldrepublic.client.fx;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.oldrepublic.OldRepublicMod;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Additive light that never hides what is behind it: the saber blade's glow and any effect drawn as light (sparks, arcs,
 * shimmer). Plain geometry and blending, no post-processing: the vanilla lightning shader and blend (colour times alpha
 * added to the frame, faded by fog), with depth tested but not written, so soft edges never cut holes in what is drawn
 * after them. Light added over a bright daytime sky shows little, so anything that must read by day also needs an
 * alpha-blended layer (the blade's coloured fringe, {@code RenderTypes.debugQuads()}).
 */
public final class GlowRenderTypes {
	/** Position and colour quads, added to the frame; depth tested, not written; both faces drawn. */
	private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
			.withLocation(OldRepublicMod.id("pipeline/glow"))
			.withVertexShader("core/rendertype_lightning")
			.withFragmentShader("core/rendertype_lightning")
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build());
	private static final RenderType GLOW = RenderType.create("oldrepublic_glow", RenderSetup.builder(GLOW_PIPELINE).sortOnUpload().createRenderSetup());

	/** Textured particles added to the frame (the vanilla particle shader), depth tested, not written. */
	private static final RenderPipeline GLOW_PARTICLE_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.PARTICLE_SNIPPET)
			.withLocation(OldRepublicMod.id("pipeline/glow_particle"))
			.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.withCull(false)
			.build());
	private static final SingleQuadParticle.Layer GLOW_PARTICLES = new SingleQuadParticle.Layer(true,
			SingleQuadParticle.Layer.TRANSLUCENT.textureAtlasLocation(), GLOW_PARTICLE_PIPELINE);

	/** Additive position-and-colour quads ({@code addVertex(pose, x, y, z).setColor(argb)}, four per quad). */
	public static RenderType glow() {
		return GLOW;
	}

	/**
	 * The particle layer for a {@code SingleQuadParticle} that should read as light: its sprite from the particle atlas,
	 * its colour times alpha added to the frame. Return it from the particle's {@code getLayer()}.
	 */
	public static SingleQuadParticle.Layer glowParticles() {
		return GLOW_PARTICLES;
	}

	/** Loads the class so the pipelines are registered with the rest at start-up. */
	public static void init() {
	}

	private GlowRenderTypes() {
	}
}
