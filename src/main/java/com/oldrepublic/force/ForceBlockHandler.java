package com.oldrepublic.force;

/** What one reactive block does when a power acts on it. Registered with {@link ForceBlockInteractions#register}. */
@FunctionalInterface
public interface ForceBlockHandler {
	/** Applies the act on the server. Returns true when the block consumed it. */
	boolean handle(ForceBlockContext ctx);
}
