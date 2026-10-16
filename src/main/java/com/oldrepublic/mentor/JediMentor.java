package com.oldrepublic.mentor;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Master Ilyra Saelen, Jedi Watchwoman of this frontier sector. */
public class JediMentor extends MentorEntity {
	public JediMentor(EntityType<? extends JediMentor> type, Level level) {
		super(type, level);
	}

	@Override
	public MentorRole role() {
		return MentorRole.JEDI;
	}
}
