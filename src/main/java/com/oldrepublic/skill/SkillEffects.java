package com.oldrepublic.skill;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.resources.Identifier;

/** Effect event ids of the skills feature. */
public final class SkillEffects {
	/** A node was bought or granted (to the owner; variant = tree order, intensity = new rank). */
	public static final Identifier NODE_LEARNED = OldRepublicMod.id("node_learned");
	/** A full respec happened (to the owner; intensity = refunded points). */
	public static final Identifier RESPEC = OldRepublicMod.id("respec");

	private SkillEffects() {
	}
}
