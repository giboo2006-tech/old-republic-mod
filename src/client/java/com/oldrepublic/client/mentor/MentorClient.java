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
import com.oldrepublic.quest.QuestEffects;
import com.oldrepublic.quest.QuestParticles;
import com.oldrepublic.quest.story.Story;
import com.oldrepublic.quest.story.StoryData;
import com.oldrepublic.quest.story.StoryRules;
import com.oldrepublic.registry.ModSounds;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client side of the mentors and of Chapter Two's milestones: renderers, the lesson banner, the dust
 * of a mentor leaving the Crossing, and the active lesson shown on the Standing tab's mentor card.
 */
public final class MentorClient {
	private static final int INDEPENDENT_ACCENT = 0xFFC9A66B;
	private static final String[] MILESTONES = {"lesson_one", "lesson_two", "reckoning", "chapter"};
	private static final RandomSource RANDOM = RandomSource.create();

	public static void init() {
		ModelLayerRegistry.registerModelLayer(MentorModel.LAYER, MentorModel::createBodyLayer);
		EntityRendererRegistry.register(MentorEntities.JEDI_MENTOR, MentorRenderer::new);
		EntityRendererRegistry.register(MentorEntities.SITH_MENTOR, MentorRenderer::new);
		ClientEffects.register(QuestEffects.LESSON_COMPLETE, MentorClient::milestone);
		ClientEffects.register(QuestEffects.MENTOR_DEPART, MentorClient::depart);
		StandingPanel.setLessonSource(MentorClient::lessonLine);
	}

	/** A Chapter Two milestone: a banner in the path's colour and the lesson chime. */
	private static void milestone(Minecraft client, ClientLevel level, EffectEventPayload payload, @Nullable Entity entity) {
		int variant = Math.floorMod(payload.variant(), MILESTONES.length);
		Path path = Path.values()[Math.floorMod((int) payload.intensity(), Path.values().length)];
		int accent = switch (path) {
			case JEDI -> MentorRole.JEDI.accent();
			case SITH -> MentorRole.SITH.accent();
			default -> INDEPENDENT_ACCENT;
		};
		String base = "quest.oldrepublic.c2.banner." + MILESTONES[variant];
		Banners.show(Component.translatable(base), Component.translatable(base + "." + path.id()), accent);
		QuestSounds.ui(ModSounds.STORY_LESSON_COMPLETE, 1f, 0.9f);
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

	/** The Standing tab's mentor card: the current Chapter Two objective with that mentor, or null. */
	private static @Nullable Component lessonLine(String mentorId) {
		Minecraft mc = Minecraft.getInstance();
		StoryData data = mc.player == null ? null : Story.peek(mc.player);

		if (data == null || !mentorId.equals(StoryRules.mentorOf(data.storyPath()))) {
			return null;
		}

		String key = StoryRules.objectiveKey(data);
		return key == null ? null : Component.translatable(key);
	}

	private MentorClient() {
	}
}
