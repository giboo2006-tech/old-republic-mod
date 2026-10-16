package com.oldrepublic.client.mentor;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** Render state of a Chapter Two mentor. */
public class MentorRenderState extends HumanoidRenderState {
	/** Master Saelen (hood) rather than Overseer Sorn (half-cape). */
	public boolean jedi;
	/** Kneeling in meditation at the camp. */
	public boolean meditating;
}
