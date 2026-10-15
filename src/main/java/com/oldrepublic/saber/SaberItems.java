package com.oldrepublic.saber;

import java.util.HashMap;
import java.util.Map;
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

	public static final KyberCrystalItem KYBER_CRYSTAL = ModRegistry.item("kyber_crystal", KyberCrystalItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());

	public static final HiltPartItem EMITTER_GUARDIAN = part(Emitter.GUARDIAN);
	public static final HiltPartItem EMITTER_DUELIST = part(Emitter.DUELIST);
	public static final HiltPartItem GRIP_WRAPPED = part(Grip.WRAPPED);
	public static final HiltPartItem GRIP_RIDGED = part(Grip.RIDGED);
	public static final HiltPartItem POMMEL_WEIGHTED = part(Pommel.WEIGHTED);
	public static final HiltPartItem POMMEL_BALANCED = part(Pommel.BALANCED);

	/**
	 * Default components use the built-in balance defaults; every ignite, retract and construct
	 * rewrites the modifiers from the server's balance file.
	 */
	public static final LightsaberItem LIGHTSABER = ModRegistry.item("lightsaber", LightsaberItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
					.component(SaberComponents.SABER_STATE, SaberState.RETRACTED)
					.attributes(SaberStats.of(SaberConfig.DEFAULT, new SaberBalance()).modifiers(false, new SaberBalance())));

	public static final LightsaberWorkbenchBlock WORKBENCH = ModRegistry.block("lightsaber_workbench", LightsaberWorkbenchBlock::new,
			BlockBehaviour.Properties.of().strength(3.5f, 6f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops());

	private static HiltPartItem part(HiltParts.Part part) {
		HiltPartItem item = ModRegistry.item(part.itemId(), p -> new HiltPartItem(p, part), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
		PARTS.put(part, item);
		return item;
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

	static void init() {
		// Pre-built training sabers for creative testing; their crystal belongs to nobody.
		CrystalAttunement nobody = new CrystalAttunement(new UUID(0L, 0L), "", BladeColor.GREEN.rgb());
		ModRegistry.tabEntry(() -> buildSaber(nobody, BladeColor.GREEN, Emitter.DUELIST, Grip.RIDGED, Pommel.BALANCED, new SaberBalance()));
		ModRegistry.tabEntry(() -> buildSaber(nobody, BladeColor.YELLOW, Emitter.GUARDIAN, Grip.RIDGED, Pommel.BALANCED, new SaberBalance()));
	}

	private SaberItems() {
	}
}
