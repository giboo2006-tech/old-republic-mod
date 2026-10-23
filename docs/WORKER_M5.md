# Milestone 5 worker guide (read first)

Every Milestone 5 worker reads this before starting. The spec is `docs/MILESTONE_5.md`: your scope and files are in 17.2, the frozen contracts in 17.1 and the revision notes (section 22, which records how Phase 0 was actually built), the ids in 18, the tests in 19 and the balance keys in 16. Phase 0 is built; section 22's lines from 68 on record what it actually did. Where this guide and the spec disagree, the spec's section 22 wins, then this guide, then the rest of the spec; tell the lead about any disagreement.

## 1. Ownership and the shared tree

- **A file has exactly one owner** (17.2). `tools/ownership_m5.json` is the machine-readable copy of the 17.2 tables. Everything no worker owns is the lead's, including the contract files inside your packages that the table lists under `except` (for W1, for example, `ShipState`, `ShipRecord`, `TravelData` and `TravelLocks`).
- **Inherited files.** M1 to M4 files follow the M5 worker who continues their package: W1 the ship and travel (`travel.**`), W3 the planets and the world (`planet.**`, `world.**`), W4 story, missions, identity, dialogue, mentors and vendors, W6 the skill trees, the ability input code, `client/force/**` and `force/choke/**` (the fear migration), W7 the saber, crafting and equipment code, W8 the M4 foes' new neighbours (`enemy.{cartel,hask,hymn,scour,stormback}`) and the new bosses. The other M3 and M4 Force powers' server code (`force/{speed,barrier}/**` and the rest), the troopers, constructs, droids, creatures and the M4 Mandalorians, the Collector's and Kaast's fights, and the holocron code belong to no M5 worker, so they are the lead's: changes there are requests.
- Need a change in a file you do not own? Put the exact change in your report under "Requests". Never edit it, even for a one-line fix.
- **One working tree, no branches.** The eight workers edit the same checkout at the same time, each in their own files. Workers never commit, push, stash or switch branches; the lead commits each worker's files and builds them on CI. Because other workers are editing next to you, `git status` shows their files too: never revert, format or "clean up" a file you do not own.
- Before you report, check every file you created, changed or deleted:

  ```
  python3 tools/check_ownership.py --table tools/ownership_m5.json W1 --files <each path you touched>
  ```

  It fails on any path outside your globs. `... --table tools/ownership_m5.json W1 --list` prints your globs. The lead runs the same check on your report's file list before committing.
- **Your entry points.** Phase 0 created one server and one client init class per worker and calls them from `OldRepublicMod` and `OldRepublicModClient`, after the M5 contracts. Register everything of yours from these; never edit the mod's entry points.

  | Worker | Server | Client | Mixin configs (package) |
  |---|---|---|---|
  | W1 flight and ships | `travel.FlightFeature` (and the M4 `travel.TravelFeature`) | `client.travel.FlightClient` (and `client.travel.TravelClient`) | `oldrepublic-travel.mixins.json` (`mixin.travel`) |
  | W2 space | `space.SpaceFeature` | `client.space.SpaceClient` | `oldrepublic-space.mixins.json` (`mixin.space`, new) |
  | W3 worlds | `planet.PlanetM5Feature` (and `planet.PlanetFeature`, `world.WorldFeature`) | `client.planet.PlanetM5Client` (and `client.planet.PlanetClient`, `client.world.WorldClient`) | `oldrepublic-planet.mixins.json` (`mixin.planet`), `oldrepublic-world.mixins.json` |
  | W4 story and factions | `quest.story.ChapterFiveFeature` (and the M4 `VendorFeature`, `QuestFeature`, `MissionFeature`, `IdentityFeature`) | `client.quest.ChapterFiveClient` (and the M4 clients of those features) | `oldrepublic-quest.mixins.json`, `oldrepublic-identity.mixins.json` |
  | W5 paths and ranks | `quest.turning.TurningFeature` | `client.quest.turning.TurningClient` | none (ask the lead for one) |
  | W6 powers | `force.ForceM5Feature` | `client.force.ForceM5Client` | `oldrepublic-force.mixins.json`, `oldrepublic-skill.mixins.json` |
  | W7 sabers, crafting and gear | `saber.SaberM5Feature` (and the M4 `SaberFeature`, `CraftingFeature`, `EquipmentFeature`) | `client.saber.SaberM5Client` (and the M4 clients of those features) | `oldrepublic-saber.mixins.json`, `oldrepublic-crafting.mixins.json`, `oldrepublic-equipment.mixins.json` |
  | W8 foes and bosses | `enemy.FoesM5Feature` | `client.enemy.FoesM5Client` | `oldrepublic-foes.mixins.json`, `oldrepublic-boss.mixins.json` |

- Signatures in 17.1 are frozen. You may add methods; you never change or remove existing ones. Saved or synced enums are append only. `TravelService` is W1's file, but `moveAcross` inside it is the lead's and frozen.
- Phase 0 stubs return the values listed in 17.1 (and section 22). Until integration, code against the contracts and test your part with your own commands and game tests (17.2, "Stubs while parallel").

## 2. Milestone 5 rules

- **Only `TravelService.moveAcross` moves anything between levels.** Before calling it, hold a chunk ticket on the destination. No code keeps an entity reference across a jump or a sky-to-orbit transition: a change of level makes a new Java object with the same UUID; keep UUIDs and use the entity `moveAcross` returns.
- **A piloted ship is moved only by its pilot's client**, or by the server moves of 4.1 (the sky and orbit transitions, a jump's arrival, the orbit's edge, a tow), each of which also sends `ClientboundMoveVehiclePacket.fromEntity(ship)` to the pilot. `Flights` and `Ships.keepOnPad` never touch a piloted ship.
- **The client never trusts a payload's numbers** for damage, heat, cooldowns, speed or a hit. Payloads carry presses and targets; the server decides.
- **Every new saved position carries its dimension**: a `GlobalPos`, or a position plus a `ResourceKey<Level>`.
- **Levels.** Use `Planets.isHomeWorld(level)` for M1 to M3 content, `Planets.isStoryLevel(level)` for story, missions and sites (home world or planet), `Planets.isPlanet(level)` for the planets alone, and `Planets.isOrbit(level)` / `Planets.orbitOf(key)` for the orbits. An orbit is not a story level. Never compare `level.dimension()` with `Level.OVERWORLD` in new code. Time of day and day counts come from `Clocks`.
- **The planet and orbit dimensions do not exist on the game-test server.** Server tests fly in the overworld's sky (for flight the home world is a planet), use detached data and fixtures, and test every cross-level rule on pure functions. Real planets, orbits and travel are tested only in client tests (19.2).
- **Every new `switch` over an append-only enum has a `default` branch** (or is a `switch` statement): `Planets.Planet`, `Faction`, `Allegiance`, `SiteKind`, `ShipState`, `Destination`, `ChapterFiveStage`, `TurningStage`, `TurningKind`, `SaberForm`, `Quality`, `MissionDefinition.Objective`, `BossOutcome` and the other saved enums. A `switch` expression must be exhaustive, so appending a value would break it in someone else's file.
- **Codecs.** `RecordCodecBuilder` takes at most 16 fields: nest records as `StoryData.Chapter4` does. New fields on saved records are `optionalFieldOf` with a default, so old saves decode.
- **Registrations.** Each owner registers its own entity types, particle types (in a `<Feature>Particles` class), blocks, mob effects and any item that needs its own class. `registry/M5Items` (the lead's) holds only the plain items several workers name; their textures, models and lang belong to the id's owner in 18.1. Data loaders skip an entry whose item id is unknown, with a warning, so your part loads before the others are committed.
- **Control.** Fear, Stasis and pacify take their length from `ControlEffects.diminished(target, ticks, kind)` alone (it applies the duration modifiers itself; never call `duration` as well), with the kinds `ControlEffects.FEAR`, `STASIS` and `PACIFY` (the M2 diminishing returns on push-resistant targets, bosses resist, 9.2). Status effects are W6's; other workers apply them through W6's API.
- **Art direction (unchanged, and new for M5).** No ground circles, sigils, halos, glyphs or glow outlines. Danger tells are never thinned by the particle preset. With `flashingEffects` off, flashes become steady glows; Tyrvann's lightning flashes also dim with `stormFlashes`. Nothing in an orbit uses ground dust (`gather_dust`, `tomb_dust`, dust puffs): space has no air. Loops stop on every cancel path (13.4 of M4).
- **Weapon Action** (the N key) keeps the M4 registry: `WeaponActions.register(stack -> ..., (player, pressed) -> ...)`.
- **Keys.** A new key's default must differ from every other key's default, the mod's and vanilla's (vanilla's F3 debug keys aside): `m2/SkillClientSteps.checkKeyDefaults` fails the client test otherwise and lists every clash (13.1).

## 3. Lessons from Milestones 2 to 4

- **Sealed attachments.** Fabric encodes a synced attachment later, on the network thread. Mutable attachments commit with `player.setAttached(TYPE, data.copy().seal())` and sync through a frozen view (see `Progression.commit` and `ForceData`); after any commit, `get` again before changing anything. Immutable records (such as `TravelData` and `CraftingData`) need no seal: replace the whole record. Global state lives in detached data with `snapshot` and `commit` (see `ShipRegistry`, `WorldSites`).
- **Commands take a single player** (`EntityArgument.player()`, used as `@p` or a name), never a selector list. Client steps and the playthrough bot pass `@p`.
- **Key handling.** Drain `consumeClick()` every client tick for every key you own. For HOLD_RELEASE abilities, a tap is a short hold: the press and its release are both sent in the same tick (`client/ability/AbilityInput`).
- **All randomness in tests is seeded** with a `RandomSource.create(seed)` the test controls.
- **Game test structures stay under the barrier ceiling** at y = 8: fixtures are at most 7 blocks high and use `skyAccess = true` wherever a heightmap matters. Ask the lead for new fixtures: `src/gametest/resources/**` is the lead's.
- **26.2 names.** Vanilla entity types are `EntityTypes.ZOMBIE`, not `EntityType`. Tag checks on entities use `Entity#is(TagKey)`. A `ServerPlayer` reaches its level with `player.level()` (a `ServerLevel`) and the server with `player.level().getServer()`. Action-bar text is `sendOverlayMessage(Component)`. The 36 main and hotbar slots are `getInventory().getNonEquipmentItems()`.
- **Fabric's `FakePlayer` refuses to ride anything** (`startRiding` always returns false). Tests that need a rider or a pilot use `M3TestPlayers.listed(helper)` and `M3TestPlayers.remove(player)` afterwards.
- **Loops stop** on cancel, death, disconnect, dimension change, leaving the area and item or slot change. Write a client watchdog for any loop that a server event normally stops.
- **Hub people** stand on `hub_spawner` blocks with a role (`HubSpawners`): the owner of a role registers its factory from its feature; a role nobody registered leaves the spot empty with a warning, so the hubs load before every worker is in.

## 4. Test rules (frozen, from 17.1 and 19)

- Every test declares `maxTicks`, and no server test exceeds 400. Long timers are tested through pure rule functions (`*Rules.step(state, now, balance)`), one per timer.
- Server tests use small fixtures and never place a whole M5 template; they load it and read its sidecar. Whole templates, worldgen and real dimension travel run only in client tests.
- No test writes global state it does not restore: not `WorldSites`, `ShipRegistry`, the `StructureLocator` queue, the world's day time or its weather. Take a `snapshot` first and `commit` it back in a `finally`. Daily and weekly rules take the day as an argument.
- Proximity rules use `M3TestPlayers.listed(helper)`, a mock player that is in `level.players()`. Two-player rules use two listed players.

Write your server test class in `src/gametest/java/com/oldrepublic/gametest/` with the method names of 19.1 (`FlightGameTests` W1, `SpaceGameTests` W2, `PlanetM5GameTests` W3, `StoryM5GameTests` W4, `TurningGameTests` W5, `ForceM5GameTests` W6, `SaberM5GameTests` W7, `FoesM5GameTests` W8). Write your client screenshot steps in your `m5/<Feature>ClientSteps.run(context, server)` (Phase 0 stubs: `Flight`, `Space`, `Planet`, `Story`, `Turning`, `Force`, `Saber`, `Foes`), with screenshots named `oldrepublic_m5_<nnn>_<what>` in your band: W1 100 to 109, W2 110 to 124, W3 125 to 139, W4 140 to 154, W5 155 to 164, W6 165 to 174, W7 175 to 184, W8 185 to 199. `M5ClientTest` runs the steps in the merge order (planets, flight, space, sabers, powers, foes, turning, story). The lead registers your server test class. W1's assertions for the dedicated-server spike go in `m5/FlightSpike.java` (W1's file), which the lead's `FlightClientTest` driver calls.

## 5. Ids, lang and the id check

- Section 18 marks every id as new or "existing, reused". A reused id is never registered again, gets no new lang key and is not added to `events_m5.json`.
- `python3 tools/check_m5_ids.py` runs on every CI push. It fails when a new id already existed before Milestone 5, a reused id is missing or registered again, or `events_m5.json` drifts from section 15.4. `--report` lists which new ids exist so far.
- **Lang:** write your strings only in your own parts (18.5). `mergeLang` fails the build when one key appears in two parts, even with the same value.
- **Tags:** new tag files are the lead's; owners fill their own entries by request (17.2).

## 6. Sounds

- `tools/assets/sounds/events_m5.json` (the lead's) lists every new M5 sound id with its owning recipe module (`feature`), subtitle, `loop`, `stream`, `volume` and optional `variants`. Every id already has placeholder audio, a `sounds.json` entry, a subtitle and a `ModSounds` constant: dots become underscores, so `ship.flight.engine` is `ModSounds.SHIP_FLIGHT_ENGINE`.
- Recipe modules (`tools/assets/sounds/<feature>.py`): W1 `flight`, W2 `space`, W3 `world_m5`, W4 `story_m5`, W5 `turning`, W6 `force_m5`, W7 `saber_m5`, W8 `enemy_m5`. Create yours if it does not exist (`FILES = {...}`, `import gen_sounds as g` for the DSP helpers; see `force_m4.py`).
- **Keep the placeholder file names**: an id's files are `<first part of the id>/<rest with dots as underscores>`, numbered `1..n` when it has variants. Then `sounds.json` and the subtitles stay byte-identical.
- Run `python3 tools/assets/gen_sounds.py --m5 --feature <feature>`. Do not pass `--java`: `ModSounds` is the lead's and already complete. `--placeholders --m5` lists the ids still on placeholder audio.
- A different number of variants, or a change to a subtitle, `loop`, `stream` or `volume`, is a request.
- Loops must be seamless and stop by the rules of 15.4.

## 7. Docs and the report

- Update your `docs/design/<feature>.md` (17.2 names each) and `docs/assets/<feature>.md` (M5 sections): the phase-by-phase treatment of each effect (15), the final sound of each new event and the art of each new texture. Do not edit the top-level docs (`FEATURE_CHECKLIST`, `BALANCE`, `KNOWN_ISSUES`, `ASSET_MANIFEST`, `DECISIONS`, `VFX_SFX_SPEC`, `AUDIO_BRIEF`); put the text you want there in your report.
- Mark any API you could not verify with `// API-CHECK: <what>`, and list it in your report. There is no local Java build: the lead compiles on CI and sends back compile errors and test failures from your files.
- Your report lists: every file you created, changed or deleted; the ownership check's output; requests for files you do not own (exact changes); API-CHECKs; tests written; anything cut, with the spec's cut order; and what a person still has to play by hand.
