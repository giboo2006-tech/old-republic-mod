package com.oldrepublic.force;

import com.oldrepublic.OldRepublicMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/** Tags defined by the force feature. */
public final class ForceTags {
	/** Bosses and trial enemies: knockback x0.4 and stagger diminishing returns. Other features add members. */
	public static final TagKey<EntityType<?>> PUSH_RESISTANT = TagKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("push_resistant"));

	private ForceTags() {
	}
}
