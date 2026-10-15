# Design brief (verbatim)

This is Gabriel's original design brief for the mod, copied verbatim from the project thread on 2026-10-15. It is the source of truth for feature work. Do not edit it; record decisions and adaptations in `docs/DECISIONS.md` instead.

---

Act as my lead Minecraft mod developer, gameplay designer, combat designer, and technical artist. Help me design and build an ambitious Star Wars mod set in the OLD REPUBLIC ERA.

I want actual implementation, not just ideas. Inspect my existing project first, preserve working features, and develop the mod in achievable stages. If no project exists, ask for the Minecraft version and mod loader before writing version-specific code.

The working title is **Star Wars: Echoes of the Old Republic**.

**1. CORE VISION**

Create a deep, grind-heavy survival RPG experience where I begin as an inexperienced Force-sensitive individual and gradually become a powerful Jedi, Sith, or independent Force user.

The main gameplay loop should be:

Explore → discover Force-related mysteries → complete trials and missions → gain experience and resources → unlock abilities → improve equipment → fight stronger enemies → explore more dangerous locations.

I want:

- Meaningful long-term progression.
- Branching Force ability skill trees.
- Light-side and dark-side choices.
- Custom lightsabers and personal crystal discovery.
- Satisfying melee combat and Force powers.
- Old Republic enemies, factions, ruins, temples, and technology.
- Weapons, armour, droids, ships, and planetary environments.
- Exceptionally detailed, responsive VFX and SFX.
- Single-player and multiplayer compatibility.

Progression should be demanding but rewarding. Avoid making everything a repetitive mob-killing grind. Exploration, training, difficult encounters, crafting, discoveries, and decisions should all matter.

**2. SETTING AND ART DIRECTION**

Use the Old Republic era as the setting, with a clear chosen period and consistent faction identities. Draw inspiration from the atmosphere of Knights of the Old Republic and The Old Republic.

Focus on:

- Jedi and Sith orders.
- Republic and Sith military forces.
- Mandalorian clans.
- Mercenaries, bounty hunters, smugglers, and pirates.
- Ancient temples, forgotten battlefields, and mysterious ruins.
- Dangerous wilderness and crystal caverns.
- Ancient technology and buried secrets.

Do not mix in Clone Wars or Galactic Empire units as default enemies.

Create original characters and local stories within the setting. Any departures from established lore should be documented as intentional gameplay adaptations.

The art should fit Minecraft’s block-based world while adding polished custom models, animations, lighting effects, particles, and sound design. Avoid effects that look like unrelated particle spam.

Use original, appropriately licensed, or user-supplied assets. Do not assume copyrighted game audio or models are available to redistribute.

**3. STARTING AS A FORCE-SENSITIVE**

The player begins Force-sensitive, but weak and untrained.

Introduce this through a short “Awakening” sequence:

- A strange dream, distant whisper, or subtle environmental disturbance.
- A small uncontrolled Force effect.
- A discovery or encounter that reveals the player’s potential.
- A journal entry that starts their search for training.

Do not immediately give the player a lightsaber or powerful offensive abilities.

Early gameplay should involve survival, basic equipment, investigating rumours, locating ruins, and finding a mentor or holocron.

Give the player a reason to explore before choosing a faction. Joining the Jedi or Sith should feel earned.

**4. FORCE PROGRESSION AND MIDI-CHLORIANS**

I want a visible midi-chlorian-related progression system.

Treat increasing midi-chlorian level as an intentional gameplay adaptation rather than claiming it is strict lore. Separate the following systems so they have clear purposes:

- **Force Attunement Level:** Overall progression and access to higher tiers.
- **Midi-chlorian Resonance:** A slowly improving measure of awakened potential, earned primarily through major milestones.
- **Force Energy:** The resource spent when using abilities.
- **Ability Mastery:** Proficiency with individual powers.
- **Skill Points:** Used to purchase upgrades and specialisations.
- **Alignment:** The consequences of moral choices.
- **Faction Reputation:** How specific groups respond to the player.

Use a configurable initial level range, such as 1–100, with an editable experience curve.

Progression sources should include:

- Combat against appropriate enemies.
- Exploration discoveries.
- Mentor training.
- Holocrons.
- Meditation trials.
- Story and faction missions.
- Bosses.
- Rare Force-related events.

Include protection against trivial grinding, such as repeated use against harmless targets or farming the same low-level enemy endlessly. Do not punish normal combat.

Meditation should be an active or limited training activity, not unlimited AFK experience.

Major powers should require a combination of level, prerequisite skills, resources, and trials. Levels alone should not unlock everything.

**5. LIGHT SIDE, DARK SIDE, AND FACTIONS**

Keep moral alignment separate from faction membership.

A player might be a Jedi who is drifting toward the dark side, or an unaffiliated Force user with light-side tendencies.

Allow players to:

- Seek out the Jedi.
- Pursue Sith training.
- Remain independent, with their own advantages and limitations.

Alignment should change through meaningful decisions, mission outcomes, and contextual actions. Avoid treating every use of a particular ability as an identical moral event.

Examples of choices:

- Spare or execute a defeated enemy.
- Rescue prisoners or exploit them.
- Protect a settlement or intimidate it.
- Return an artefact or keep it for power.
- Resolve conflict peacefully or use coercion.

Light-side progression should emphasise protection, discipline, healing, precision, and control.

Dark-side progression should emphasise aggression, fear, domination, lightning, and risky bursts of power.

Neither side should simply be better. Both need interesting combat and progression options.

Changing allegiance should be possible through a substantial redemption or corruption questline. Do not let players cheaply swap paths to collect every exclusive benefit.

Faction ranks should require missions and trials in addition to levels. Possible progression paths include:

- Jedi Initiate → Padawan → Knight → Master.
- Sith Acolyte → Apprentice → Lord → Darth.

These are proposed gameplay ranks; adapt them to the selected setting and narrative.

**6. BRANCHING SKILL TREES**

Create a readable, interactive skill-tree screen with distinct branches:

- Universal Force Skills.
- Jedi Discipline.
- Sith Domination.
- Lightsaber Combat.
- Exploration and Survival.

Every ability must have:

- A gameplay purpose.
- Unlock requirements.
- Force Energy cost.
- Cooldown.
- Range and targeting rules.
- Duration, where applicable.
- Upgrade branches.
- Ability Mastery progression.
- Counterplay.
- Custom animation.
- Defined VFX and SFX.
- Multiplayer synchronisation rules.

Universal abilities could include:

- Force Sense.
- Force Push.
- Force Pull.
- Force Jump.
- Force Speed.
- Saber Throw.
- Telekinetic Lift.

Light-side abilities could include:

- Force Heal.
- Force Barrier.
- Force Stasis.
- Force Protect.
- Force Valor.
- Limited mind influence on appropriate NPCs.

Dark-side abilities could include:

- Force Lightning.
- Force Choke.
- Force Drain.
- Force Fear.
- Force Rage.
- Force Crush.

Design abilities with meaningful limitations. Bosses should have partial resistance, reduced control duration, or specific vulnerability windows instead of being permanently stun-locked.

Avoid creating every ability as a renamed projectile with different damage.

For example, Force Push might branch into:

- A wide crowd-control cone.
- A narrow, powerful blast.
- A defensive pulse useful against projectiles.

The player should make build choices rather than eventually acquiring every upgrade with no trade-offs.

**7. LIGHTSABERS AND CRYSTAL DISCOVERY**

Obtaining the first lightsaber should be a major milestone.

Build a questline around:

1. Discovering information about a crystal cave.
2. Finding the cave.
3. Navigating environmental hazards and hostile creatures.
4. Completing a personal Force trial.
5. Discovering a resonant crystal.
6. Gathering hilt components.
7. Constructing the lightsaber at a dedicated workbench.

Use “kyber crystal” as the player-facing term if appropriate, while documenting any terminology adapted for the chosen Old Republic setting.

Crystal caves should feel special:

- Distinct formations.
- Ambient resonance.
- Subtle particles.
- Carefully controlled lighting effects.
- Rare chambers.
- Environmental storytelling.
- A sense that the crystal is calling to the player.

Crystal discovery should combine exploration and a personal trial rather than simply mining random ore.

Lightsaber customisation should eventually include:

- Hilt designs.
- Emitters.
- Grips.
- Pommel options.
- Blade colours.
- Small controlled blade-length variations.
- Different ignition and hum profiles.
- Single blades.
- Double-bladed sabers.
- Dual-wielding.

Keep colour primarily cosmetic so players can use their favourite colour without sacrificing their build.

Make components and upgrades provide balanced trade-offs. A lightsaber should feel dangerous without instantly trivialising every enemy.

**8. COMBAT AND OTHER EQUIPMENT**

Combat needs more depth than holding the attack button.

Include:

- Light and heavy attacks.
- Blocking.
- Timed parries.
- Guard stamina.
- Dodging or evasive movement.
- Blaster deflection.
- Clear enemy attack tells.
- Recovery windows.
- Combining Force powers with melee attacks.

A timed deflection can return a bolt accurately. Basic blocking should provide less precise protection and consume resources.

Plan lightsaber combat specialisations, but implement a small number well before adding many forms.

Other equipment should include:

- Blaster pistols.
- Blaster rifles.
- Heavy blasters.
- Vibroblades.
- Electrostaffs.
- Training sabers.
- Jedi robes.
- Sith robes and armour.
- Republic military armour.
- Mandalorian-inspired equipment.
- Utility tools and medical items.

Different equipment types should support different builds. Heavy armour might improve protection while reducing mobility or Force efficiency.

**9. ENEMIES AND BOSSES**

Create enemies with different behaviours, not just different health pools.

Enemy categories:

- Soldiers using cover and ranged fire.
- Melee fighters applying pressure.
- Droids with specialised resistances.
- Beasts with lunges and territorial behaviour.
- Force users who block, counter, and use abilities.
- Support units that heal or reinforce allies.
- Bounty hunters with gadgets and traps.

Example enemies:

- Sith acolytes.
- Jedi opponents where the narrative supports it.
- Republic and Sith patrols.
- Battle droids.
- Mercenaries.
- Pirates.
- Mandalorian hunters.
- Cave creatures.
- Ancient security constructs.
- Corrupted temple guardians.

Bosses should feature readable phases, distinct arenas, specific mechanics, and meaningful rewards.

Possible bosses:

- An ancient tomb guardian.
- A rogue Sith master.
- A powerful war droid.
- A dangerous cave beast protecting a crystal chamber.
- An elite Mandalorian hunter.

Use stagger resistance and control recovery to prevent permanent crowd control. Scale multiplayer encounters fairly.

**10. BIOMES, STRUCTURES, AND PLANETS**

Begin with a manageable collection of structures and biomes in the existing world. Expand into planet-themed dimensions once the main gameplay works.

Potential environments include:

- Tython-inspired forests and Jedi ruins.
- Korriban-inspired deserts and Sith tombs.
- Dromund Kaas-inspired stormy jungles.
- Tatooine-inspired settlements and wastelands.
- Deep crystal caverns.
- Ancient underground complexes.
- Ruined battlefields.

Each environment needs distinct resources, enemies, structures, ambience, and exploration rewards.

Add:

- Jedi enclaves.
- Sith academies.
- Abandoned temples.
- Military outposts.
- Smuggler camps.
- Crashed ships.
- Hidden holocron chambers.
- Bounty terminals.
- Crystal cave entrances.

Avoid making every location equally common. Important discoveries should require effort without becoming frustratingly rare.

**11. SHIPS AND TRAVEL**

Ships should be a long-term progression goal.

Build this in stages:

- First: repairable crashed shuttles or transport terminals.
- Next: ships that provide travel between unlocked destinations.
- Later: controllable local flight, if technically practical.
- Finally: expanded ship combat or space exploration as a separate major feature.

Do not promise seamless space travel before proving it is feasible.

Ships could require:

- Hull components.
- Power systems.
- Fuel or energy.
- Navigation data.
- Repairs.
- Upgrades.

Give them landing, take-off, engine, cockpit, and damage effects. Make ownership and multiplayer access rules explicit.

**12. VFX — A CORE REQUIREMENT**

Detailed VFX are one of the main reasons I want this mod.

Do not leave them as a vague “add polish later” task. Every implemented ability and weapon must have a defined visual treatment during its development.

Effects should communicate:

- Anticipation.
- Activation.
- Direction and travel.
- Impact.
- Lingering aftermath.

Examples:

**Force Push**
- Brief hand and arm animation.
- Dust and loose debris gathering.
- A readable expanding pressure wave.
- Directional particles showing the cone.
- An impact burst when targets are hit.
- Optional subtle camera feedback.

**Force Lightning**
- Branching, animated arcs.
- Controlled flicker and changing arc paths.
- Brief charge-up sparks around the hand.
- Contact sparks on the target.
- Stronger visual complexity at higher mastery.
- Clear start and stop behaviour.
- No permanent visual leftovers when interrupted.

**Force Choke**
- A distinct hand pose.
- The target visibly lifting.
- Restrained particles and environmental movement.
- Clear channeling feedback.
- Immediate release animation when interrupted.

**Force Jump**
- A compressed dust burst at take-off.
- Subtle movement trails.
- Landing effects based on impact strength and surface.

**Lightsabers**
- Bright blade core and coloured outer layer.
- Smooth ignition and retraction.
- Motion trails matched to blade movement.
- Clash sparks at the actual contact point.
- Brief contact flashes.
- Temporary scorch effects where practical.
- Optional local illumination through a supported implementation.

**Crystal Caverns**
- Sparse floating particles.
- Crystal glints and gentle pulses.
- Localised ambience.
- Effects that guide exploration without revealing every secret.

Do not fake unsupported shader features or claim bloom, refraction, or dynamic lighting works unless it actually does in the target renderer.

Provide a strong baseline appearance without optional shaders. Enhanced rendering integrations must be optional.

Use distance culling, particle budgets, effect pooling where appropriate, and configurable quality levels.

Support Reduced, Standard, High, and Cinematic presets. Add separate settings for screen shake and flashing effects. Visual clutter must not hide combat tells.

**13. SFX — EQUALLY IMPORTANT**

Sound design must receive the same attention as visuals.

Do not reuse one generic sound for every ability.

Build layered sound events where appropriate:

- Activation.
- Movement or travel.
- Sustained loop.
- Impact.
- Release or decay.

Lightsabers need:

- Ignition and retraction.
- Continuous hum.
- Movement-sensitive swings.
- Several clash variations.
- Blaster deflection.
- Surface contact.
- Distinct sound profiles for selected hilt configurations.

Force powers need:

- Push and pull pressure sounds.
- Lightning charge, crackle, sustained loop, and release.
- Choke channeling and target struggle.
- Healing activation and sustain.
- Barrier activation, impact, and break.
- Distinct feedback when an ability fails due to insufficient energy.

World audio needs:

- Crystal resonance.
- Temple ambience.
- Sith tomb whispers.
- Machinery.
- Distant wildlife.
- Ship engines.
- Droid movement and vocalisation.
- Different environmental soundscapes.

Use positional audio and sensible attenuation. Design variation and concurrency limits so repeated attacks do not become irritating or overwhelming.

Stop loops correctly when a player dies, disconnects, changes dimension, cancels an ability, or loses the target.

Provide separate volume controls where practical and subtitles for important audio cues.

If final audio assets are missing, create the sound-event structure and a precise asset brief. Clearly label temporary placeholders. Never claim assets exist when they do not.

**14. QUESTS, GRIND, AND ENDGAME**

The grind should build toward memorable rewards.

Include:

- Mentor trials.
- Faction missions.
- Bounties.
- Ruin exploration.
- Holocron hunts.
- Crafting projects.
- Rare enemy encounters.
- Boss challenges.
- Crystal expeditions.

Avoid mandatory real-world timers for core progression.

Provide clear progress toward important rewards rather than relying entirely on low-probability drops. Use guaranteed milestones or bad-luck protection where appropriate.

Endgame could include:

- High-level temples and tombs.
- Legendary component crafting.
- Advanced ability specialisations.
- Difficult faction storylines.
- Multiplayer boss encounters.
- Building a personal enclave.
- Training NPC apprentices as a later expansion.

Prestige systems should be optional and should not erase progression without a clear benefit and explicit player choice.

**15. UI AND PLAYER EXPERIENCE**

Create a cohesive Old Republic-inspired interface that remains easy to read.

Include:

- Force Energy meter.
- Level and experience.
- Midi-chlorian Resonance.
- Ability hotbar.
- Cooldowns.
- Skill trees.
- Alignment and faction reputation.
- Quest journal.
- Lightsaber workbench.
- Codex for discoveries.

Use clear tooltips with exact costs, prerequisites, and effects.

Avoid overwhelming the player with every system at spawn. Introduce mechanics gradually.

Provide rebindable controls and prevent key conflicts where possible.

**16. TECHNICAL REQUIREMENTS**

Before implementing, inspect the project and establish:

- Minecraft version.
- Mod loader and version.
- Java version.
- Build system.
- Existing dependencies.
- Current implemented features.

Verify APIs against documentation for the actual target versions. Do not invent methods, dependencies, or compatibility claims.

Separate systems cleanly:

- Player progression and persistence.
- Alignment and reputation.
- Ability execution.
- Combat.
- Items and crafting.
- Entities and AI.
- Quests.
- World generation.
- Client rendering.
- Audio.
- Networking.
- Configuration.

Make gameplay server-authoritative. Validate ability unlocks, costs, cooldowns, range, line of sight, and valid targets on the server.

Keep rendering and audio code out of dedicated-server execution paths.

Synchronise gameplay state efficiently. Do not send packets for every individual particle when a compact effect event can reproduce the visuals client-side.

Use data-driven definitions where supported for skills, quests, loot, balance, and world-generation settings.

Define death, respawn, dimension transfer, and save/load behaviour. Avoid progression loss or item duplication.

Respect protected areas and player permissions for destructive abilities. Make terrain damage and PvP effects configurable.

Test dedicated-server startup early, not only after completing the mod.

**17. IMPLEMENTATION ROADMAP**

Treat the full concept as a long-term roadmap. Build a polished vertical slice first.

**Milestone 1: First Playable Experience**
- Force-sensitive awakening.
- Persistent player progression.
- Force Energy.
- Force Push with upgrades.
- One crystal cave structure and discovery trial.
- A lightsaber workbench and one customisable saber.
- One ranged enemy.
- Basic blocking or deflection.
- A short introductory quest.
- Proper VFX and SFX for these features.
- Working single-player and dedicated-server behaviour.

This should demonstrate the intended quality of the final mod.

**Milestone 2: Identity and Builds**
- Jedi and Sith introductory paths.
- Alignment and faction reputation.
- Initial skill trees.
- Several distinct abilities.
- Mentor progression.
- Expanded saber combat.

**Milestone 3: Exploration**
- More structures and environments.
- Holocrons.
- Faction missions.
- Additional enemies.
- First major boss.

**Milestone 4: Broader Content**
- Additional weapons and armour.
- More crafting and specialisation.
- Planet-themed dimensions.
- Transport ships.

**Milestone 5: Advanced Features**
- Expanded flight and ship systems.
- Endgame encounters.
- Further faction stories.
- Optional large-scale systems supported by the established foundation.

Do not begin every milestone simultaneously.

**18. HOW I WANT YOU TO WORK**

Start by:

1. Inspecting the repository.
2. Summarising what already works.
3. Identifying only the decisions that genuinely block development.
4. Proposing a practical architecture and first milestone.
5. Beginning implementation once the required platform details are known.

For each development batch:

- State what you are implementing.
- Make the actual changes.
- Run the available build and meaningful checks.
- Explain how to test it in Minecraft.
- Report what works, what is incomplete, and what remains unverified.
- List missing art or audio assets honestly.
- Continue with the next coherent task when authorised.

Keep a feature checklist, balance configuration, asset manifest, and known-issues list.

A feature is not finished merely because it compiles. It needs working gameplay, save/load behaviour, appropriate feedback, and verified client/server handling.

Do not report in-game testing unless you actually performed it. Clearly distinguish compilation, automated checks, and manual game testing.

My highest priorities are:
- Rewarding progression from weak Force-sensitive to powerful specialist.
- A convincing Old Republic atmosphere.
- A memorable personal lightsaber journey.
- Meaningful Jedi and Sith choices.
- Force abilities that feel powerful and distinct.
- Detailed, polished VFX and SFX.
- A stable playable mod that grows through completed milestones.
