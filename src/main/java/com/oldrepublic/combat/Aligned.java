package com.oldrepublic.combat;

/** An entity that belongs to a side. Read it through {@link Allegiances#of}, which also covers players. */
public interface Aligned {
	/** The side this entity fights for; never null. */
	Allegiance allegiance();
}
