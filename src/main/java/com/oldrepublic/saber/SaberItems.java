package com.oldrepublic.saber;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.oldrepublic.registry.ModComponents.CrystalAttunement;
import com.oldrepublic.registry.ModRegistry;
import com.oldrepublic.saber.HiltParts.Emitter;
import com.oldrepublic.saber.HiltParts.Grip;
import com.oldrepublic.saber.HiltParts.Pommel;
import com.oldrepublic.saber.SaberComponents.SaberConfig;
import com.oldrepublic.saber.SaberComponents.SaberState;
import com.oldrepublic.saber.workbench.LightsaberWorkbenchBlock;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Items and blocks of the saber feature, in creative tab order. */
public final class SaberItems {
	private static final Map<HiltParts.Part, HiltPartItem> PARTS = new HashMap<>();
	/** The pommel a saberstaff's config keeps (5.6): never rendered, counted or returned. */
	public static final Pommel STAFF_POMMEL = Pommel.BALANCED;

	public static final KyberCrystalItem KYBER_CRYSTAL = ModRegistry.item("kyber_crystal", KyberCrystalItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());

	public static final HiltPartItem EMITTER_GUARDIAN = part(Emitter.GUARDIAN);
	public static final HiltPartItem EMITTER_DUELIST = part(Emitter.DUELIST);
	public static final HiltPartItem EMITTER_RELIQUARY = part(Emitter.RELIQUARY);
	public static final HiltPartItem GRIP_WRAPPED = part(Grip.WRAPPED);
	public static final HiltPartItem GRIP_RIDGED = part(Grip.RIDGED);
	/** The saberstaff's grip (spec MILESTONE_4.md 5.6); vendors sell it, Artifice makes it. */
	public static final HiltPartItem GRIP_STAFF = part(Grip.STAFF);
	public static final HiltPartItem POMMEL_WEIGHTED = part(Pommel.WEIGHTED);
	public static final HiltPartItem POMMEL_BALANCED = part(Pommel.BALANCED);
	public static final HiltPartItem POMMEL_ARCHIVIST = part(Pommel.ARCHIVIST);

	/**
	 * Default components use the built-in balance defaults; every ignite, retract and construct
	 * rewrites the modifiers from the server's balance file.
	 */
	public static final LightsaberItem LIGHTSABER = ModRegistry.item("lightsaber", LightsaberItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
					.component(SaberComponents.SABER_STATE, SaberState.RETRACTED)
					.attributes(SaberStats.of(SaberConfig.DEFAULT, new SaberBalance()).modifiers(false, new SaberBalance())));

	/** The saberstaff (spec MILESTONE_4.md 5.6); built at the workbench, both blades lit by default. */
	public static final SaberStaffItem LIGHTSABER_STAFF = ModRegistry.item("lightsaber_staff", SaberStaffItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
					.component(SaberComponents.SABER_STATE, SaberState.RETRACTED)
					.attributes(SaberStats.staff(SaberConfig.DEFAULT.emitter(), Grip.STAFF, true, new SaberBalance()).modifiers(false, new SaberBalance())));

	/** The training sabers (5.5): a fixed amber blade, and the Proving's red-tinted one. */
	public static final TrainingSaberItem TRAINING_SABER = training("training_saber", BladeColor.AMBER);
	public static final TrainingSaberItem TRAINING_SABER_RED = training("training_saber_red", BladeColor.RED);

	/** Saber lenses (5.7), made by Artifice. */
	public static final LensItem LENS_HONED = lens(SaberLens.Lens.HONED);
	public static final LensItem LENS_STEADY = lens(SaberLens.Lens.STEADY);
	public static final LensItem LENS_RESONANT = lens(SaberLens.Lens.RESONANT);

	public static final LightsaberWorkbenchBlock WORKBENCH = ModRegistry.block("lightsaber_workbench", LightsaberWorkbenchBlock::new,
			BlockBehaviour.Properties.of().strength(3.5f, 6f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops());

	private static HiltPartItem part(HiltParts.Part part) {
		HiltPartItem item = ModRegistry.item(part.itemId(), p -> new HiltPartItem(p, part), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
		PARTS.put(part, item);
		return item;
	}

	private static TrainingSaberItem training(String id, BladeColor color) {
		return ModRegistry.item(id, p -> new TrainingSaberItem(p, color), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
				.component(SaberComponents.SABER_CONFIG, TrainingSaberItem.config(color))
				.component(SaberComponents.SABER_STATE, SaberState.RETRACTED)
				.attributes(TrainingSaberItem.defaultModifiers(color)));
	}

	private static LensItem lens(SaberLens.Lens lens) {
		return ModRegistry.item(lens.itemId().getPath(), p -> new LensItem(p, lens), new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
	}

	/** The item of a lens kind. */
	public static LensItem lensItem(SaberLens.Lens lens) {
		return switch (lens) {
			case HONED -> LENS_HONED;
			case STEADY -> LENS_STEADY;
			case RESONANT -> LENS_RESONANT;
		};
	}

	/** The item of a hilt part. */
	public static HiltPartItem partItem(HiltParts.Part part) {
		return PARTS.get(part);
	}

	/** A complete saber built from the given parts around {@code crystal}. */
	public static ItemStack buildSaber(CrystalAttunement crystal, BladeColor color, Emitter emitter, Grip grip, Pommel pommel, SaberBalance balance) {
		ItemStack stack = new ItemStack(LIGHTSABER);
		SaberConfig config = new SaberConfig(crystal, color, emitter, grip, pommel);
		stack.set(SaberComponents.SABER_CONFIG, config);
		stack.set(SaberComponents.SABER_STATE, SaberState.RETRACTED);
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, SaberStats.of(config, balance).modifiers(false, balance));
		return stack;
	}

	/**
	 * A complete saberstaff around two crystals (5.6): one emitter at both ends and the staff grip.
	 * The config's pommel is a placeholder the staff never renders, counts or returns.
	 */
	public static ItemStack buildStaff(CrystalAttunement crystal, CrystalAttunement second, BladeColor color, Emitter emitter, SaberBalance balance) {
		ItemStack stack = new ItemStack(LIGHTSABER_STAFF);
		SaberConfig config = new SaberConfig(crystal, color, emitter, Grip.STAFF, STAFF_POMMEL, Optional.of(second), Optional.empty());
		stack.set(SaberComponents.SABER_CONFIG, config);
		stack.set(SaberComponents.SABER_STATE, SaberState.RETRACTED);
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, SaberStats.of(stack, balance).modifiers(false, balance));
		return stack;
	}

	static void init() {
		// Pre-built training sabers for creative testing; their crystal belongs to nobody.
		CrystalAttunement nobody = new CrystalAttunement(new UUID(0L, 0L), "", BladeColor.GREEN.rgb());
		ModRegistry.tabEntry(() -> buildSaber(nobody, BladeColor.GREEN, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, new SaberBalance()));
		ModRegistry.tabEntry(() -> buildSaber(nobody, BladeColor.YELLOW, Emitter.GUARDIAN, Grip.RIDGED, Pommel.BALANCED, new SaberBalance()));
		ModRegistry.tabEntry(() -> buildStaff(nobody, nobody, BladeColor.VIOLET, Emitter.DUELIST, new SaberBalance()));
	}

	private SaberItems() {
	}
}
