# Milestone 4 worker guide (read first)

Every Milestone 4 worker reads this before starting. The spec is `docs/MILESTONE_4.md`: your scope and files are in 15.2, the frozen contracts in 15.1 and its revision notes (section 20, which records how Phase 0 was actually built), the ids in 16, the tests in 17 and the balance keys in 14. Where this guide and the spec disagree, the spec's section 20 wins, then this guide, then the rest of the spec; tell the lead about any disagreement.

## 1. Ownership and the shared tree

- **A file has exactly one owner** (15.2). `tools/ownership_m4.json` is the machine-readable copy of the 15.2 tables. Everything no worker owns is the lead's, including the contract files inside your packages that the table lists under `except` (for W3, for example, `ShipRecord`, `TravelData` and `TravelLocks`).
- **Inherited files.** M1 to M3 files follow the M4 worker who continues their package: W1 the world (`world.**`, its assets and tests), W2 story, missions, identity, dialogue and mentors, W4 the armour (`equipment.**`), W5 the saber (`saber.**`). The M3 Force, skill and holocron code, the troopers, poachers and constructs, and the Collector's fight belong to no M4 worker, so they are the lead's: changes there are requests.
- Need a change in a file you do not own? Put the exact change in your report under "Requests". Never edit it, even for a one-line fix.
- **One working tree, no branches.** This replaces the per-worker branches of 15.3 step 2: CI builds only the lead's branch. The six workers edit the same checkout at the same time, each in their own files. Workers never commit, push, stash or switch branches; the lead commits each worker's files and builds them on CI. Because other workers are editing next to you, `git status` shows their files too: never revert, format or "clean up" a file you do not own.
- Before you report, check every file you created, changed or deleted:

  ```
  python3 tools/check_ownership.py --table tools/ownership_m4.json W3 --files <each path you touched>
  ```

  It fails on any path outside your globs. `... --table tools/ownership_m4.json W3 --list` prints your globs. The lead runs the same check on your report's file list before committing.
- **Your entry points.** Phase 0 created one server and one client init class per worker and calls them from `OldRepublicMod` and `OldRepublicModClient`, after the M4 contracts. Register everything of yours from these; never edit the mod's entry points.

  | Worker | Server | Client | Mixin config (package) |
  |---|---|---|---|
  | W1 | `planet.PlanetFeature` (and the M3 `world.WorldFeature`) | `client.planet.PlanetClient` (and `client.world.WorldClient`) | `oldrepublic-planet.mixins.json` (`mixin.planet`), `oldrepublic-world.mixins.json` |
  | W2 | `vendor.VendorFeature` (and `QuestFeature`, `MissionFeature`, `IdentityFeature`) | `client.vendor.VendorClient` (and `QuestClient`, `MissionClient`, `IdentityClient`) | `oldrepublic-quest`, `oldrepublic-identity` |
  | W3 | `travel.TravelFeature` | `client.travel.TravelClient` | `oldrepublic-travel.mixins.json` (`mixin.travel`) |
  | W4 | `equipment.EquipmentFeature` | `client.equipment.EquipmentClient` | `oldrepublic-equipment.mixins.json` |
  | W5 | `crafting.CraftingFeature` (and `saber.SaberFeature`) | `client.crafting.CraftingClient` (and `client.saber.SaberClient`) | `oldrepublic-crafting.mixins.json` (`mixin.crafting`), `oldrepublic-saber.mixins.json` |
  | W6 | `force.ForceM4Feature`, `enemy.FoesFeature` (Kaast too) | `client.force.ForceM4Client`, `client.enemy.FoesClient` | `oldrepublic-foes.mixins.json` (`mixin.foes`) |

- Signatures in 15.1 are frozen. You may add methods; you never change or remove existing ones. Saved or synced enums are append only. `TravelService` is W3's file, but `moveAcross` inside it is the lead's and frozen.
- Phase 0 stubs return the values listed in 15.1 (and section 20). Until integration, code against the contracts and test your part with your own commands and game tests (15.2, "Stubs while parallel").

## 2. Milestone 4 rules

- **Only `TravelService.moveAcross` moves anything between levels** (or `TravelService.launch`, which uses it). Before calling it, hold a chunk ticket on the destination (`ServerChunkCache#addTicketWithRadius`, the PORTAL type or W3's own): riders that vanilla did not remount are found by UUID, and a UUID lookup only sees entities in tracked chunks.
- **No code keeps an entity reference across a jump.** A change of level makes a new Java object with the same UUID and removes the old one; keep UUIDs, and use the entity `moveAcross` returns.
- **Every new saved position carries its dimension**: a `GlobalPos`, or a position plus a `ResourceKey<Level>` (as `MissionData.Active.Where` does).
- **No new overworld-only check without `Planets`.** Use `Planets.isHomeWorld(level)` for M1 to M3 content, `Planets.isStoryLevel(level)` for M4 story, missions and sites, and `Planets.isPlanet(level)` for the planets alone. Never compare `level.dimension()` with `Level.OVERWORLD` in new code. Time of day and day counts come from `Clocks` (a planet's game time is the overworld's).
- **The planet dimensions do not exist on the game-test server** (the CI log shows `selvaren=false, varkhast=false`). Server tests use detached data, fixtures and the overworld; real planets and real travel are tested only in client tests (17.2).
- **Registrations.** Each owner registers its own entity types, particle types (in a `<Feature>Particles` class, as M3), blocks and any item that needs its own class. `registry/M4Items` (the lead's) holds only the plain items several workers name; their textures, models and lang belong to the id's owner in 16.1. Data loaders (schematics, vendor stock, missions) skip an entry whose item id is unknown, with a warning, so your part loads before the others are merged.
- **Weapon Action** (the N key): register a handler with `WeaponActions.register(stack -> ..., (player, pressed) -> ...)` from your feature. The first handler whose predicate matches the main-hand stack gets the press and the release.
- **Switch expressions over append-only enums.** A `switch` expression must be exhaustive, so appending a value breaks every switch expression over that enum, often in someone else's file. Over `SiteKind`, `ChapterFourStage`, `ShipState`, `Discipline`, `ModSlot`, `Planets.Planet` and the other saved enums, write a `default` branch (or a `switch` statement).
- **Codecs.** `RecordCodecBuilder` takes at most 16 fields: nest records as `StoryData.Chapter4` does. New fields on saved records are `optionalFieldOf` with a default, so old saves decode.

## 3. Lessons from Milestones 2 and 3

- **Sealed attachments.** Fabric encodes a synced attachment later, on the network thread. If the server thread is still mutating that same object, the encoder can throw `ConcurrentModificationException`, and the player is disconnected. Mutable attachments commit with `player.setAttached(TYPE, data.copy().seal())` and sync through a frozen view (see `Progression.commit` and `ForceData`); after any commit, `get` again before changing anything. Immutable records (such as `TravelData` and `CraftingData`) need no seal: replace the whole record. Global state lives in detached data with `snapshot` and `commit` (see `ShipRegistry`, `WorldSites`).
- **Commands take a single player** (`EntityArgument.player()`, used as `@p` or a name), never a selector list. Client steps and the playthrough bot pass `@p`.
- **Key handling.** Drain `consumeClick()` every client tick for every key you own. For HOLD_RELEASE abilities, a tap is a short hold: the press and its release are both sent in the same tick (`client/ability/AbilityInput`).
- **All randomness in tests is seeded** with a `RandomSource.create(seed)` the test controls.
- **Game test structures stay under the barrier ceiling** at y = 8: fixtures are at most 7 blocks high and use `skyAccess = true` wherever a heightmap matters. Ask the lead for new fixtures: `src/gametest/resources/**` is the lead's.
- **26.2 names.** Vanilla entity types are `EntityTypes.ZOMBIE`, not `EntityType`. Tag checks on entities use `Entity#is(TagKey)`. A `ServerPlayer` reaches its level with `player.level()` (a `ServerLevel`) and the server with `player.level().getServer()`. Action-bar text is `sendOverlayMessage(Component)`. The 36 main and hotbar slots are `getInventory().getNonEquipmentItems()`.
- **Fabric's `FakePlayer` refuses to ride anything** (`startRiding` always returns false). Tests that need a rider use `M3TestPlayers.listed(helper)` and `M3TestPlayers.remove(player)` afterwards.
- **Loops stop** on cancel, death, disconnect, dimension change, leaving the area and item or slot change. Write a client watchdog for any loop that a server event normally stops.

## 4. Test rules (frozen, from 15.1 and 17)

- Every test declares `maxTicks`, and no server test exceeds 400. Long timers are tested through pure rule functions (`*Rules.step(state, now, balance)`), one per timer, so a 12,000-tick rule is checked in a few ticks.
- Server tests use small fixtures and never place a whole M4 template; they load it and read its sidecar. Whole templates, worldgen and real dimension travel run only in client tests.
- No test writes global state it does not restore: not `WorldSites`, `ShipRegistry`, the `StructureLocator` queue or the world's day time. Take a `snapshot` first and `commit` it back in a `finally`. Daily rules take the day as an argument.
- Proximity rules use `M3TestPlayers.listed(helper)`, a mock player that is in `level.players()`. Two-player rules use two listed players.

Write your server test class in `src/gametest/java/com/oldrepublic/gametest/` with the method names of 17.1 (`PlanetGameTests` W1, `StoryM4GameTests` W2, `TravelGameTests` W3, `GearGameTests` W4, `CraftingGameTests` W5, `ForceM4GameTests` and `FoesGameTests` W6). Write your client screenshot steps in your `m4/<Feature>ClientSteps.run(context, server)` (Phase 0 stubs: `Planet`, `Story`, `Travel`, `Gear`, `Crafting`, `Foes`), with screenshots named `oldrepublic_m4_<nn>_<what>` in your band: W1 10 to 19, W2 20 to 29, W3 30 to 39, W4 40 to 49, W5 50 to 59, W6 60 to 69. `M4ClientTest` runs the steps in the merge order (planets, travel, gear, crafting, foes, story). The lead registers your server test class.

## 5. Ids, lang and the id check

- Section 16 marks every id as new or "existing, reused". A reused id is never registered again, gets no new lang key and is not added to `events_m4.json`. Phase 0 decisions: `blaster_pistol`, `blaster_rifle` and `vibroblade` are reused (their classes are now `BlasterItem` and `VibroWeaponItem`); `frag_grenade` is a **new** item (it existed only as an entity); the staff grip is `grip_staff`.
- `python3 tools/check_m4_ids.py` runs on every CI push. It fails when a new id already existed before Milestone 4, a reused id is missing or registered again, or `events_m4.json` drifts from section 16. `--report` lists which new ids exist so far.
- **Lang:** write your strings only in your own parts (16.5). `mergeLang` fails the build when one key appears in two parts, even with the same value.
- **Tags:** new tag files are the lead's; owners fill their own entries by request (15.2).

## 6. Sounds

- `tools/assets/sounds/events_m4.json` (the lead's) lists every new M4 sound id with its owning recipe module (`feature`), subtitle, `loop`, `stream`, `volume` and optional `variants`. Every id already has placeholder audio, a `sounds.json` entry, a subtitle and a `ModSounds` constant: dots become underscores, so `force.barrier.loop` is `ModSounds.FORCE_BARRIER_LOOP`.
- Recipe modules (`tools/assets/sounds/<feature>.py`): W1 `world_m4`, W2 `story_m4`, W3 `ship`, W4 `gear`, W5 `crafting` and `saber`, W6 `force_m4` and `enemy_m4`. Create yours if it does not exist (`FILES = {...}`, `import gen_sounds as g` for the DSP helpers; see `force.py`).
- **Keep the placeholder file names**: an id's files are `<first part of the id>/<rest with dots as underscores>`, numbered `1..n` when it has variants. Then `sounds.json` and the subtitles stay byte-identical.
- Run `python3 tools/assets/gen_sounds.py --m4 --feature <feature>`. Do not pass `--java`: `ModSounds` is the lead's and already complete. `--placeholders` lists the ids still on placeholder audio.
- A different number of variants, or a change to a subtitle, `loop`, `stream` or `volume`, is a request.
- The player pistol and rifle reuse `blaster.pistol_fire` and `blaster.fire`.
- Loops must be seamless and stop by the rules of 13.4.

## 7. Docs and the report

- Update your `docs/design/<feature>.md` and `docs/assets/<feature>.md` (M4 sections): the phase-by-phase treatment of each effect (13), the final sound of each new event and the art of each new texture. Do not edit the top-level docs (`FEATURE_CHECKLIST`, `BALANCE`, `KNOWN_ISSUES`, `ASSET_MANIFEST`, `DECISIONS`, `VFX_SFX_SPEC`, `AUDIO_BRIEF`); put the text you want there in your report.
- Mark any API you could not verify with `// API-CHECK: <what>`, and list it in your report. There is no local Java build: the lead compiles on CI and sends back compile errors and test failures from your files.
- Your report lists: every file you created, changed or deleted; the ownership check's output; requests for files you do not own (exact changes); API-CHECKs; tests written; anything cut, with the spec's cut order; and what a person still has to play by hand.
