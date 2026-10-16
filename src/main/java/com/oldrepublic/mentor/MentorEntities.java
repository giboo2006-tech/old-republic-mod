package com.oldrepublic.mentor;

import com.oldrepublic.OldRepublicMod;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity types of the two mentors. No natural spawns: the story places them. */
public final class MentorEntities {
	public static final ResourceKey<EntityType<?>> JEDI_MENTOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("jedi_mentor"));
	public static final ResourceKey<EntityType<?>> SITH_MENTOR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, OldRepublicMod.id("sith_mentor"));

	public static final EntityType<JediMentor> JEDI_MENTOR = Registry.register(BuiltInRegistries.ENTITY_TYPE, JEDI_MENTOR_KEY,
			EntityType.Builder.of(JediMentor::new, MobCategory.MISC)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.fireImmune()
					.build(JEDI_MENTOR_KEY));

	public static final EntityType<SithMentor> SITH_MENTOR = Registry.register(BuiltInRegistries.ENTITY_TYPE, SITH_MENTOR_KEY,
			EntityType.Builder.of(SithMentor::new, MobCategory.MISC)
					.sized(0.6f, 1.95f)
					.eyeHeight(1.62f)
					.clientTrackingRange(10)
					.fireImmune()
					.build(SITH_MENTOR_KEY));

	public static EntityType<? extends MentorEntity> type(MentorRole role) {
		return role == MentorRole.SITH ? SITH_MENTOR : JEDI_MENTOR;
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(JEDI_MENTOR, MentorEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SITH_MENTOR, MentorEntity.createAttributes());
	}

	private MentorEntities() {
	}
}
