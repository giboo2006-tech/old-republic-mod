package com.oldrepublic.skill;

/** Ids of the nodes whose effects this feature applies or that other code looks up. */
public final class SkillIds {
	public static final String FORCE_PUSH = "force_push";
	/** Force Sense: owning it shows the Exploration tree. */
	public static final String FORCE_SENSE = "force_sense";
	public static final String PUSH_PREFIX = "push_";
	public static final String PUSH_GROUP = "push_shape";
	public static final String DEEP_RESERVES = "deep_reserves";
	public static final String QUICKENED_FLOW = "quickened_flow";
	public static final String UNBOUND = "unbound";
	public static final String CENTERED_MIND = "centered_mind";
	public static final String CALM_FOCUS = "calm_focus";
	public static final String GUARDIANS_RESOLVE = "guardians_resolve";
	public static final String FURY = "fury";
	public static final String HUNGERING_DARK = "hungering_dark";
	public static final String DREAD_PRESENCE = "dread_presence";

	/** Force Speed (Milestone 4): Wayfarer's Grace makes it cheaper. */
	public static final String FORCE_SPEED = "force_speed";
	/** Telekinetic Lift: the Independent signature power of the Exploration tree. */
	public static final String FORCE_LIFT = "force_lift";
	/** Exclusive group of the Force Sense branches. */
	public static final String SENSE_GROUP = "sense_shape";
	public static final String SENSE_DANGER = "sense_danger";
	public static final String SENSE_SEEKER = "sense_seeker";
	public static final String TRAILWISE = "trailwise";
	public static final String RELIC_LORE = "relic_lore";
	public static final String HARDY = "hardy";
	public static final String SCAVENGER = "scavenger";
	public static final String QUIET_STEP = "quiet_step";
	/** Exclusive group of the Telekinetic Lift branches. */
	public static final String LIFT_GROUP = "lift_shape";
	public static final String LIFT_SUSPEND = "lift_suspend";
	public static final String LIFT_HURL = "lift_hurl";
	public static final String ARCHIVISTS_ROAD = "archivists_road";
	public static final String PATHFINDERS_POISE = "pathfinders_poise";
	/** Granted holocron and rank nodes of the Jedi tree. */
	public static final String WATCHERS_INSIGHT = "watchers_insight";
	public static final String MENDING_BREATH = "mending_breath";
	public static final String ARCHIVISTS_WARD = "archivists_ward";
	public static final String KNIGHTS_VIGIL = "knights_vigil";
	/** Granted holocron and rank nodes of the Sith tree. */
	public static final String TOMB_HUNGER = "tomb_hunger";
	public static final String CRACKLING_GRASP = "crackling_grasp";
	public static final String ARCHIVISTS_HUNGER = "archivists_hunger";
	public static final String LORDS_DOMINION = "lords_dominion";
	/**
	 * Wayfarer's Grace, the rank 4 Wayfarer's granted passive (MILESTONE_5.md 8.5): more maximum Force Energy, and Force
	 * Speed costs less. (Force Speed's own {@code speed_wayfarer} branch is "Wayfarer's Stride".)
	 */
	public static final String WAYFARERS_STRIDE = "wayfarers_stride";

	/** Tree ids. */
	public static final String TREE_UNIVERSAL = "universal";
	public static final String TREE_SABER = "saber";
	public static final String TREE_JEDI = "jedi";
	public static final String TREE_SITH = "sith";
	/** The Exploration and Survival tree (shown once Force Sense is owned). */
	public static final String TREE_EXPLORATION = "exploration";

	private SkillIds() {
	}
}
