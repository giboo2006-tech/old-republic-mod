# Milestone 3 worker guide (read first)

Every Milestone 3 worker reads this before starting. The spec is `docs/MILESTONE_3.md`: your scope and files are in 15.2, the frozen contracts in 15.1 and its "Phase 0 notes (as built)", the ids in 16, the tests in 17 and the balance keys in 14. Where this guide and the spec disagree, the spec wins; tell the lead.

## 1. Ownership and the shared tree

- **A file has exactly one owner** (15.2). `tools/ownership_m3.json` is the machine-readable copy of the 15.2 tables. Everything no worker owns is the lead's, including the contract files inside your packages that the table lists under `except` (for W3, for example, `ForceBlockHandler` and `sense/SenseTargets`).
- Need a change in a file you do not own? Put the exact change in your report under "Requests". Never edit it, even for a one-line fix.
- **One working tree, no branches.** The five workers edit the same checkout at the same time, each in their own files. Workers never commit, push, stash or switch branches; the lead commits each worker's files and builds them on CI (15.3). Because other workers are editing next to you, `git status` shows their files too: never revert, format or "clean up" a file you do not own.
- Before you report, check every file you created, changed or deleted:

  ```
  python3 tools/check_ownership.py W3 --files <each path you touched>
  ```

  It fails on any path outside your globs. `python3 tools/check_ownership.py W3 --list` prints your globs. The lead runs the same check on your report's file list before committing.
- Signatures in 15.1 are frozen. You may add methods; you never change or remove existing ones. Saved or synced enums are append only.
- Phase 0 stubs return the values listed under "Stub return values" in 15.1. Until integration, code against the contracts and test your part with your own commands and game tests (15.2, "Stubs while parallel").

## 2. Lessons from Milestone 2

Each of these cost a CI round or a broken playthrough in M2.

- **Sealed attachments.** Fabric encodes a synced attachment later, on the network thread. If the server thread is still mutating that same object, the encoder can throw `ConcurrentModificationException`, and the player is disconnected. Therefore:
  - Commit with `player.setAttached(TYPE, data.copy().seal())`. The sync codec maps through `X::syncView`, which is the frozen copy taken by `seal()`. Look at `Progression.commit` and `ForceData` before writing your own attachment.
  - After any commit, call `get` again before you change anything. The object you held is no longer the attached one, so further changes to it are lost or overwrite someone else's commit.
  - Never mutate a synced view, and never mutate what the client reads through `getAttached`.
- **Commands take a single player** (`EntityArgument.player()`, used as `@p` or a name), never a selector list. In M2 the client steps passed `@a` to such commands, and the screenshots showed nothing set up. Client steps and the playthrough bot pass `@p`.
- **Key handling.**
  - Drain `consumeClick()` every client tick for every key you own. A key pressed and released between two ticks never shows in `isDown()`.
  - For HOLD_RELEASE abilities, a tap is a short hold: the press and its release are both sent in the same tick (`client/ability/AbilityInput`). Bots must tap a held power as a real short hold.
- **All randomness in tests is seeded.** Any random roll a test depends on uses a `RandomSource.create(seed)` that the test controls. In M2 a winning spar blow was randomly parried and the test flaked.
- **Game test structures stay under the barrier ceiling.** The game test enclosure puts a barrier ceiling at y = 8, and heightmaps read it as the ground. Fixtures are at most 7 blocks high and use `skyAccess = true` wherever a heightmap matters. The lead's M3 fixtures under `src/gametest/resources/data/oldrepublic_test/structure/` are built to this rule (15.1). Ask the lead for new fixtures: `src/gametest/resources/**` is the lead's.
- **26.2 names that bit M2.** Vanilla entity type constants live in `EntityTypes` (`EntityTypes.ZOMBIE`), not `EntityType`. Tag checks on entities use `Entity#is(TagKey)`. 26.2 has no getter for the server's spawn-protection radius.
- **Loops stop** on cancel, death, disconnect, dimension change and item or slot change. Write a client watchdog for any loop that a server event normally stops.

## 3. Test rules (frozen, from 15.1)

- Every test declares `maxTicks`, and none exceeds 400. Long timers are tested through pure rule functions (`*Rules.step(state, now, balance)`), one per timer: puzzle reset, vents, garrison refill, the heart, re-sing, and provocation and incident expiry. That way a 12,000-tick rule is checked in a few ticks.
- Server tests use the lead's small fixtures (`arena_24x7x24`, `urn_fixture`, `seal_fixture`, `pedestal_fixture`, `terminal_fixture`, `anchor_fixture`). They never place a whole M3 template; they load it and read its sidecar. Whole templates and worldgen run only in client tests, through `/oldrepublic site place`.
- Placement is tested through the seams, never with the balance rings:
  - `Outposts.decide`;
  - the `EchoShrines.place` overload with test rings;
  - `ReliquarySites.evaluate`;
  - `WorldSites.Data`.
- No test writes global state: not `WorldSites`, the patrol caps, the `StructureLocator` queue or the world's day time. Daily rules take the day as an argument (5.5).
- Proximity rules use `M3TestPlayers.listed(helper)`, a mock player that is in `level.players()`. M2's `FakePlayer` is not listed, so proximity rules never see it. Two-player rules use two listed players. All randomness is seeded.

Write your server test class in `src/gametest/java/com/oldrepublic/gametest/`, with the method names of 17.1. Write your client screenshot steps in your `m3/<Feature>ClientSteps.run(context, server)`, with screenshots named `oldrepublic_m3_<nn>_<what>` in your band (17.2). The lead registers the classes.

## 4. Ids, lang and the id check

- Section 16 marks every id as new or "existing, reused". A reused id is never registered again, gets no new lang key and is not added to `events_m3.json`.
- `python3 tools/check_m3_ids.py` runs on every CI push. It fails when:
  - a new id already existed before Milestone 3;
  - a reused id is missing, or is registered again;
  - `events_m3.json` drifts from section 16.

  `--report` lists which new particles, items, blocks and entities exist so far.
- **Lang:** write your strings only in your own part `src/main/lang-parts/<part>.json` (16, "Lang prefixes"). `mergeLang` fails the build when one key appears in two parts, even with the same value.
- **Tags:** Phase 0 already lists every M3 block and entity in the shared tags as an optional entry (`{"id": ..., "required": false}`). Registering your block or entity is enough for it to join `#built_blocks`, `#push_resistant`, `c:bosses`, `mineable/pickaxe` or `enchantable/*`. The fill-in tags follow 15.2:
  - W1 fills `force_reactive` and the structure and biome tags;
  - W3 owns `force_harm`;
  - W4 fills `repairs/*`, `jedi_robe_cloth` and `sith_robe_cloth`.

  Any other change to `data/minecraft/tags/**` or the shared `oldrepublic` tags is a request.

## 5. Sounds

- `tools/assets/sounds/events_m3.json` (the lead's) lists the 121 new sound ids of section 16. Each entry has its owning recipe module (`feature`), subtitle, `loop`, `stream` and `volume`; `site.discovered` also has `variants`. Every id already has placeholder audio, a `sounds.json` entry, a subtitle and a `ModSounds` constant: dots become underscores, so `boss.collector.hurl_lift` is `ModSounds.BOSS_COLLECTOR_HURL_LIFT`. For a vanilla API that takes a `Holder<SoundEvent>`, such as an armour material's equip sound, use `ModSounds.holder(ModSounds.X)`.
- The placeholder audio is a generic, labelled sketch: a chime, impact, rise, click, voice, hum, bed or music pad, pitched per id. It exists so that code and subtitles work from day one. Replace it with your own recipes:
  1. Add the ids to `FILES` in your recipe module `tools/assets/sounds/<feature>.py`:
     - W1: `world.py`;
     - W2: `story.py`;
     - W3: `force.py`, `holocron.py`;
     - W4: `enemy.py`, `equipment.py`;
     - W5: `boss.py`, `saber.py`.

     Create the module if it does not exist yet: `FILES = {...}`, with `import gen_sounds as g` for the DSP helpers (see `force.py`).
  2. **Keep the placeholder file names.** An id's files are `<first part of the id>/<rest with dots as underscores>`, numbered `1..n` when it has variants. For example, `boss.collector.hurl_lift` is `boss/collector_hurl_lift`, and the three `site.discovered` variants are `site/discovered1` to `site/discovered3`. Then `sounds.json` and the subtitles stay byte-identical, and they are the lead's files.
  3. Run `python3 tools/assets/gen_sounds.py --m3 --feature <feature>`. Do not pass `--java`, because `ModSounds` is the lead's and already complete. `python3 tools/assets/gen_sounds.py --placeholders` lists the ids still on placeholder audio.
- A different number of variants, or a change to a subtitle, `loop`, `stream` or `volume`, is a request: the lead edits `events_m3.json` and regenerates.
- Loops must be seamless. Streaming follows each id's `stream` flag in `events_m3.json`, not the loop's length: the five ambience beds, `story.ones_that_sing` and the three boss music loops stream; the hums (`force.lift.loop`, `holocron.hum`, `boss.collector.ward_hum`, `pylon.sing`, `saber.hum.singing`) do not.
- Describe the final sound of every new event in your `docs/assets/<feature>.md`. The lead merges these briefs into `AUDIO_BRIEF.md` and `VFX_SFX_SPEC.md` at integration (15.3, step 7).

## 6. Docs and the report

- Update your `docs/design/<feature>.md` and `docs/assets/<feature>.md` (M3 sections). Do not edit the top-level docs (`FEATURE_CHECKLIST`, `BALANCE`, `KNOWN_ISSUES`, `ASSET_MANIFEST`, `DECISIONS`, `VFX_SFX_SPEC`, `AUDIO_BRIEF`). Put the text you want there in your report.
- Mark any API you could not verify against the probe dumps with `// API-CHECK: <what>`, and list it in your report.
- Expect the lead to send back CI compile errors and test failures from your files.
