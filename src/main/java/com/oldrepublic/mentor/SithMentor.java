package com.oldrepublic.mentor;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Overseer Teyvar Sorn, a Sith recruiter scouting the frontier for the Empire. */
public class SithMentor extends MentorEntity {
	public SithMentor(EntityType<? extends SithMentor> type, Level level) {
		super(type, level);
	}

	@Override
	public MentorRole role() {
		return MentorRole.SITH;
	}
}
