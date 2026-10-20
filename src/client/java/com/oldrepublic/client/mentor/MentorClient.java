package com.oldrepublic.client.mentor;

import org.jspecify.annotations.Nullable;

import com.oldrepublic.client.fx.ClientEffects;
import com.oldrepublic.client.fx.Vfx;
import com.oldrepublic.client.hud.Banners;
import com.oldrepublic.client.identity.StandingPanel;
import com.oldrepublic.client.quest.QuestSounds;
import com.oldrepublic.identity.Path;
import com.oldrepublic.mentor.MentorEntities;
import com.oldrepublic.mentor.MentorRole;
import com.oldrepublic.network.EffectEventPayload;
import com.oldrepublic.planet.Planets;
import com.oldrepublic.progression.ForceAttachments;
import com.oldrepublic.progression.ForceData;
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestParticles;
import com.oldrepublic.quest.story.ChapterFiveBalance;
import com.oldrepublic.quest.story.ChapterFiveRules;
import com.oldrepublic.quest.story.ChapterFourBalance;
import com.oldrepublic.quest.story.ChapterFourRules;
import com.oldrepublic.quest.story.ChapterThreeBalance;
import com.oldrepublic.quest.story.ChapterThreeRules;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryRules;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the mentors and of the story's milestones: renderers, the lesson banner, the dust
 * of a mentor leaving the Crossing, and the active lesson shown on the Standing tab's mentor card.
 *
 * <p>Chapter Three's milestones each have their own sound: a mission complete
 * ({@code mission.complete}), the Chapter Three report (the M2 lesson chime), the Collector fallen
 * ({@code story.collector_fallen}) and rank 3 ({@code story.rank_three}). The mentor card shows the
 * Chapter Three objective while the chapter runs (counts from the synced story and Force data,
 * thresholds from the {@link ChapterThreeBalance} defaults: the client reads no balance file).
 * Chapter Four adds the report ({@code story.chapter_four}), the first landing on a path world (the
 * world's own banner and {@code planet.arrive}) and the first Masterwork (the lesson chime).
 * Milestone 5 (spec MILESTONE_5.md 15.3) adds the Chapter Five report ({@code story.chapter_five}),
 * a turning's rite ({@code story.turning.rite}), rank 4 ({@code story.rank_four}), the first
 * Legendary craft ({@code bench.legendary}) and the Long Hymn's first clear (the lesson chime).
 */
public final class MentorClient {
	private static final int INDEPENDENT_ACCENT = 0xFFC9A66B;
	/** Banner key per {@code lesson_complete} variant ({@code QuestEffects.LESSON_*}). */
	private static final String[] MILESTONES = {"lesson_one", "lesson_two", "reckoning", "chapter", "mission", "chapter3", "boss", "rank3",
			"chapter4", "planet", "masterwork", "chapter5", "turning", "rank_four", "legendary", "long_hymn"};
	/** The arrival banner's accent per world ({@link #arrivalAccent}). */
	private static final int SELVAREN_ACCENT = 0xFF8FC6C9;
	private static final int VARKHAST_ACCENT = 0xFFC27A4E;
	private static final int GHESSAR_ACCENT = 0xFFE2C47E;
	private static final int TYRVANN_ACCENT = 0xFF9C94D6;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final ChapterThreeBalance CHAPTER_THREE_DEFAULTS = new ChapterThreeBalance();
	private static final ChapterFourBalance CHAPTER_FOUR_DEFAULTS = new ChapterFourBalance();
	private static final ChapterFiveBalance CHAPTER_FIVE_DEFAULTS = new ChapterFiveBalance();

	public static void init() {
		ModelLayerRegistry.registerModelLayer(MentorModel.LAYER, MentorModel::createBodyLayer);
		EntityRendererRegistry.register(MentorEntities.JEDI_MENTOR, MentorRenderer::new);
		EntityRendererRegistry.register(MentorEntities.SITH_MENTOR, MentorRenderer::new);
		ClientEffects.register(QuestEffects.LESSON_COMPLETE, MentorClient::milestone);
		ClientEffects.register(QuestEffects.MENTOR_DEPART, MentorClient::depart);
		StandingPanel.setLessonSource(MentorClient::lessonLine);
	}

	/** A story milestone: a banner in the path's colour and the milestone's sound. */
	private static void milestone(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		int variant = Math.floorMod(payload.variant(), MILESTONES.length);

		if (variant == QuestEffects.LESSON_PLANET) {
			arrival(payload);
			return;
		}

		Path path = Path.values()[Math.floorMod((int) payload.intensity(), Path.values().length)];
		int accent = switch (path) {
			case JEDI -> MentorRole.JEDI.accent();
			case SITH -> MentorRole.SITH.accent();
			default -> INDEPENDENT_ACCENT;
		};
		String base = "quest.oldrepublic.c2.banner." + MILESTONES[variant];
		Banners.show(Component.translatable(base), Component.translatable(base + "." + path.id()), accent);
		QuestSounds.ui(milestoneSound(variant), 1f, 0.9f);
	}

	/** The sound of a {@code lesson_complete} variant: Chapter Three's have their own (13.3). */
	private static SoundEvent milestoneSound(int variant) {
		return switch (variant) {
			case QuestEffects.LESSON_MISSION -> ModSounds.MISSION_COMPLETE;
			case QuestEffects.LESSON_BOSS -> ModSounds.STORY_COLLECTOR_FALLEN;
			case QuestEffects.LESSON_RANK_THREE -> ModSounds.STORY_RANK_THREE;
			case QuestEffects.LESSON_CHAPTER_FOUR -> ModSounds.STORY_CHAPTER_FOUR;
			case QuestEffects.LESSON_PLANET -> ModSounds.PLANET_ARRIVE;
			case QuestEffects.LESSON_CHAPTER_FIVE -> ModSounds.STORY_CHAPTER_FIVE;
			case QuestEffects.LESSON_TURNING -> ModSounds.STORY_TURNING_RITE;
			case QuestEffects.LESSON_RANK_FOUR -> ModSounds.STORY_RANK_FOUR;
			case QuestEffects.LESSON_LEGENDARY -> ModSounds.BENCH_LEGENDARY;
			default -> ModSounds.STORY_LESSON_COMPLETE;
		};
	}

	/**
	 * First landing on a path world (spec MILESTONE_4.md 8.4): the world's name over its subtitle,
	 * in the world's colour, with {@code planet.arrive}. The intensity is the {@code Planet} ordinal.
	 */
	private static void arrival(EffectEventPayload payload) {
		Planets.Planet[] planets = Planets.Planet.values();
		Planets.Planet planet = planets[Math.floorMod((int) payload.intensity(), planets.length)];
		String base = "quest.oldrepublic.c2.banner.planet." + planet.id();
		Banners.show(Component.translatable(base), Component.translatable(base + ".sub"), arrivalAccent(planet));
		QuestSounds.ui(milestoneSound(QuestEffects.LESSON_PLANET), 1f, 0.9f);
	}

	/**
	 * The arrival banner's accent per world (spec MILESTONE_5.md 3.1): Selvaren's moss-glass,
	 * Varkhast's rust, Ghessar's warm salt-gold and Tyrvann's storm violet; any other world takes the
	 * Independent gold.
	 */
	private static int arrivalAccent(Planets.Planet planet) {
		return switch (planet) {
			case SELVAREN -> SELVAREN_ACCENT;
			case VARKHAST -> VARKHAST_ACCENT;
			case GHESSAR -> GHESSAR_ACCENT;
			case TYRVANN -> TYRVANN_ACCENT;
			default -> INDEPENDENT_ACCENT;
		};
	}

	/** A mentor turns away from the Crossing: a low puff of dust at the feet, nothing more. */
	private static void depart(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		Vec3 at = entity != null ? entity.position() : payload.pos();

		if (!Vfx.inRange(at)) {
			return;
		}

		for (int i = 0; i < Vfx.count(10); i++) {
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double speed = 0.03 + RANDOM.nextDouble() * 0.04;
			level.addParticle(QuestParticles.VOICE_DUST, at.x + Math.cos(angle) * 0.3, at.y + 0.1, at.z + Math.sin(angle) * 0.3,
					Math.cos(angle) * speed, 0.01 + RANDOM.nextDouble() * 0.02, Math.sin(angle) * speed);
		}
	}

	/**
	 * The Standing tab's mentor card: the current Chapter Five objective from the call on (spec
	 * MILESTONE_5.md 6.1), else Chapter Four's from the seeker scene on, else Chapter Three's while
	 * that chapter runs, else the Chapter Two objective with that mentor, or null.
	 */
	private static @Nullable Component lessonLine(String mentorId) {
		Minecraft mc = Minecraft.getInstance();
		StoryData data = mc.player == null ? null : Story.peek(mc.player);

		if (data == null || !mentorId.equals(StoryRules.mentorOf(data.storyPath()))) {
			return null;
		}

		ForceData force = mc.player.getAttached(ForceAttachments.FORCE);
		ChapterFourRules.Objective chapterFive = ChapterFiveRules.objective(data, force == null ? 1 : force.level, CHAPTER_FIVE_DEFAULTS);

		if (chapterFive != null) {
			return chapterFive.text();
		}

		ChapterFourRules.Objective chapterFour = ChapterFourRules.objective(data, ChapterFourRules.partsFitted(data), force == null ? 1 : force.level,
				CHAPTER_FOUR_DEFAULTS);

		if (chapterFour != null) {
			return chapterFour.text();
		}

		Component chapterThree = ChapterThreeRules.objective(data, force == null ? 1 : force.level, CHAPTER_THREE_DEFAULTS);

		if (chapterThree != null) {
			return chapterThree;
		}

		String key = StoryRules.objectiveKey(data);
		return key == null ? null : Component.translatable(key);
	}

	private MentorClient() {
	}
}
