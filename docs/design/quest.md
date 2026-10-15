# Quest and HUD design: "Echoes in the Stone" (Milestone 1)

Owner: quest/HUD worker. Status: implemented (Phase 2); this document describes the code as written. API status markers:
**[V]** verified against the 26.2 probe logs or Fabric 26.2 sources, **[U]** unverified (probe requested in
`tools/api-probe/quest.txt`, marked `// API-CHECK` in code until confirmed).

## 1. Scope and ownership

| Area | Location |
|---|---|
| Server quest logic | `com.oldrepublic.quest` (`QuestService` bodies, new classes listed below) |
| Client quest (dream, meditation FX, calling FX, journal screen, keybind) | `com.oldrepublic.client.quest` |
| HUD (Force panel, focus ring, flourishes, notices, `/oldrepublicfx`) | `com.oldrepublic.client.hud` |
| Mixins | `com.oldrepublic.mixin.quest` - **none planned**; config stays empty unless a probe forces one |
| Lang | `src/main/lang-parts/quest.json` |
| Art | `tools/assets/gen_textures_quest.py`, `textures/item/wanderers_journal.png`, `textures/gui/journal.png`, `textures/gui/hud/*.png`, `textures/particle/force_mote_*.png`, `textures/particle/resonance_shimmer_*.png` |
| Item/particle JSON | `items/wanderers_journal.json`, `models/item/wanderers_journal.json`, `particles/force_mote.json`, `particles/resonance_shimmer.json`, `data/oldrepublic/recipe/wanderers_journal.json` |
| Docs | this file, `docs/assets/quest.md` |

`QuestHooks` and `QuestStage` are unchanged. No new hooks or stages are needed for Phase 2.

## 2. Stage machine

Stored in `ForceData.questStage` (ordinal). Stages only move forward (except the core `/oldrepublic quest set|reset`).
One-shot facts are `ForceData.flags`; counters are `ForceData.counters`. Every mutation goes through
`Progression.get` / `Progression.commit`, so the client sees it through the synced `ForceAttachments.FORCE`.

```
STIRRING --(onAwakeningDue: time | sleep | command; dream ~7 s then burst)--> FOCUS
FOCUS --(meditation complete: 8 s still, crouched, journal in hand)--> PRACTICE   [ForcePush.grant]
PRACTICE --(5 hostile hits with force_push)--> CALLING
CALLING --(onCaveEntered)--> TRIAL
TRIAL --(onCrystalObtained)--> CONSTRUCTION       (onTrialCompleted grants rewards, stage stays TRIAL)
CONSTRUCTION --(onSaberConstructed)--> COMPLETE
```

Tolerant advancement (robust against hook order and testing commands): `onCaveEntered` advances from CALLING only;
`onTrialCompleted` rewards once (flag `quest:trial_done`) from any stage >= CALLING and advances CALLING -> TRIAL;
`onCrystalObtained` advances any stage in CALLING..TRIAL to CONSTRUCTION; `onSaberConstructed` advances any stage in
CALLING..CONSTRUCTION to COMPLETE. Pre-awakening players never advance from later hooks.

### Flags and counters (all in `ForceData`)

| Key | Kind | Meaning |
|---|---|---|
| `journal:<entry>` | flag | entry unlocked (see section 7) |
| `codex:<entry>` | flag | codex entry unlocked |
| `quest:trial_done`, `quest:complete_rewarded` | flag | one-shot reward guards (listed again for completeness) |
| `quest:trial_done`, `quest:complete_rewarded` | flag | one-shot reward guards |
| `resonance:awakening`, `resonance:first_push`, `resonance:trial`, `resonance:first_saber` | flag | set by `Progression.grantResonanceMilestone` (milestone ids are a contract: other features granting the same milestone must use the same id so it can never be granted twice) |
| `push_practice` | counter | hostile push hits during PRACTICE (0..5) |
| `quest_poacher_kills` | counter | poacher kills (journal display only; bad-luck protection is the enemy feature's) |
| `meditation_day` | counter | game-day index (`gameTime / 24000`) of the last rewarded training meditation |
| `journal_seq` | counter | increments on every new entry, lets the client detect "new" entries |

## 3. Server classes (package `com.oldrepublic.quest`)

| Class | Responsibility |
|---|---|
| `QuestFeature.init()` | registers item, particles, events, tick, `/oldrepublic journal` subcommand |
| `QuestService` (existing, package-private) | hook bodies, stage transitions, rewards |
| `QuestBalance` | balance section `quest` (public fields with defaults, read via `BalanceConfig.section`) |
| `QuestItems` | `WANDERERS_JOURNAL = ModRegistry.item("wanderers_journal", JournalItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))` |
| `JournalItem extends Item` | `use(Level, Player, InteractionHand)` **[V]**: client side calls `JournalItem.clientOpener` (a `static Consumer<Player>` set by client init, so no client class is referenced), returns `InteractionResult.SUCCESS` **[V]** |
| `Journal` | `unlock(ServerPlayer, String entryId)`, `unlockCodex(...)`, `give(ServerPlayer)`; sends the `journal_entry` effect |
| `JournalEntries` | the canonical ordered entry and codex id lists (common code; the client screen reads it too) |
| `Awakening` | dream timers (transient `Map<UUID, Pending>`), damage skip, burst, journal hand-out |
| `Meditation` | per-player transient session (`Map<UUID, Session>`), per-tick validation, completion |
| `Calling` | periodic direction ping for CALLING players holding the journal |
| `QuestParticles` | registers `oldrepublic:force_mote` and `oldrepublic:resonance_shimmer` (`FabricParticleTypes.simple()` **[V]** from fabric testmod; `Registry.register(BuiltInRegistries.PARTICLE_TYPE, id, type)` **[V]**) |

Transient maps are cleared on `ServerPlayConnectionEvents.DISCONNECT` **[V]** and on server stop; nothing about an
in-progress dream or meditation is persisted, so relog simply restarts it (the dream trigger fires again; a
meditation must be redone). No progression is lost or duplicated.

### Events used
- `ServerTickEvents.END_SERVER_TICK` **[V]**: meditation validation, dream timers, calling pings.
- `EntitySleepEvents.START_SLEEPING` / `STOP_SLEEPING` **[V]** (Fabric source: fired from `LivingEntity.startSleeping` RETURN
  and `stopSleeping` HEAD, on both logical sides, so filter `instanceof ServerPlayer`). START records `player.tickCount`;
  STOP calls `QuestHooks.onAwakeningDue(player, "sleep")` only if the player slept at least 100 ticks (the vanilla time
  needed to skip the night), so hopping in and out of bed does not trigger it.
- `ServerLivingEntityEvents.AFTER_DAMAGE` **[V]** (signature from Fabric source: `(LivingEntity, DamageSource, float baseDamage, float damageTaken, boolean blocked)`): skips the dream, breaks meditation.
- `AttackEntityCallback.EVENT` **[V]**: breaks meditation (returns `PASS`).
- `ServerPlayConnectionEvents.DISCONNECT` **[V]**, `ServerPlayerEvents.AFTER_RESPAWN` **[V]** (clear sessions).

## 4. Awakening

Entry: `QuestService.onAwakeningDue(player, reason)`. Note `Progression` calls it **every tick** once `stirTicks`
passes the threshold, so it must be idempotent.

1. Ignore if `data.isAwakened()` or a dream is already pending for that player.
2. For `time`: defer (return, the next tick retries) while the player is in danger: hurt within the last 5 s
   (`getLastHurtByMobTimestamp` **[V]** vs `tickCount`), in lava, or not on ground and not a passenger
   (`onGround()` **[V]**, `isPassenger()` **[V]**). `sleep` and `command` start immediately.
3. Start dream: `pending.put(uuid, dreamTicks=140)`, `EffectEvents.toPlayer(player, AWAKENING_DREAM, pos, null, id, dreamTicks, 0)`.
4. Each tick: count down. On `AFTER_DAMAGE` for a pending player: send `AWAKENING_DREAM` variant 1 (cancel overlay)
   and run the burst now ("skipped if damaged").
5. Burst (`Awakening.burst`):
   - `level.getEntities(player, AABB(radius 5), predicate)` **[V]**; `ItemEntity` gets an outward hop
     (`push(double,double,double)` + `hurtMarked = true` **[V]**), small non-player living mobs (bounding width <= 0.9,
     balance `burstMaxEntityWidth`) get a stronger outward `push`. No damage. Bosses and trial enemies are far wider, so
     no tag check is needed in M1.
   - Player: `addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1))` **[V]**.
   - `EffectEvents.around(level, AWAKENING_BURST, pos, null, player.getId(), radius, 0)` so others see the ripple.
   - `Journal.giveIfMissing(player)`: only if the inventory has no journal (`inventory.contains(Predicate<ItemStack>)` **[V]**
     with `stack.getItem() == QuestItems.WANDERERS_JOURNAL` **[V]**), via
     `getInventory().placeItemBackInInventory(stack)` **[V]** (drops at feet if full). This also covers re-awakening after
     `/oldrepublic quest reset`: never a duplicate.
   - Stage -> FOCUS, unlock `journal:the_dream`, `codex:the_force`, then
     `Progression.grantResonanceMilestone(player, "awakening", b.awakeningResonance)`.

Client (`client.quest.DreamOverlay`, HUD element added with `HudElementRegistry.addLast` **[V]** so it covers everything):
- Fade in 1 s to 90% black, slow pulse (alpha +/- 4.5% over 4 s, steady when flashing is off), three whisper lines
  (`dream.oldrepublic.line1..3`) each fading in/out over ~2 s, centered, grey-blue italic; fade out over the last 1 s.
  Timer in real client ticks from the payload's `intensity`.
- Sounds: `AWAKENING_WHISPERS` one-shot (UI, non-positional) + `AWAKENING_DRONE` as a looping
  `AbstractTickableSoundInstance` **[V]** that fades its own volume and calls `stop()` **[V]** when the dream ends,
  is cancelled (variant 1), the client disconnects (`ClientPlayConnectionEvents.DISCONNECT` **[V]**), the level changes or
  the player dies. Whispers are also stopped via `SoundManager.stop(SoundInstance)` **[V]** on cancel.
- Burst handler (`awakening_burst`, any client in range, through `Vfx.inRange`/`Vfx.count`): one expanding `air_ripple`
  shell (reuses the force feature's common particle type `com.oldrepublic.force.ForceParticles.AIR_RIPPLE`), block particles `BlockParticleOption(ParticleTypes.BLOCK, state)` **[V]**
  from leaves (the eight vanilla leaf blocks) and grass/ferns/bushes found in the burst sphere, leaves drifting down and
  grass hopping up (budget `Vfx.count(36)`); `AWAKENING_BURST` at the
  player via `level.playLocalSound(x,y,z,...)` **[V]**; owner gets `CameraShake.add(0.25f)`.

## 5. Meditation (FOCUS, then a limited training activity)

Server (`Meditation`, every tick per player):
- Eligible: alive, not spectator, awakened, crouching (`isShiftKeyDown()` **[V]**), on ground, not a passenger, journal in
  main or off hand (`getItemInHand(hand)` **[V]**).
- Start after 10 ticks of eligibility: anchor = position, record start tick, level. Training gate: if stage >= PRACTICE
  and `meditation_day == gameTime / 24000` the session does not start; the player gets one overlay message
  `message.oldrepublic.meditation_rested` (`sendOverlayMessage` **[V]**) per crouch.
- Validation each tick: horizontal drift from anchor < 0.15 blocks and vertical < 0.3, still crouching, same level, journal
  still in hand. Damage (`AFTER_DAMAGE`) or an attack (`AttackEntityCallback`) or a swing (`player.swinging` **[V field]**)
  breaks it.
- Effect events (`toTracking`, so others see and hear it): `oldrepublic:meditation` variant 0 = start (intensity = duration
  ticks), 1 = break, 2 = complete.
- Completion (160 ticks, balance `meditationTicks`):
  - FOCUS: `com.oldrepublic.force.ForcePush.grant(player)` (force feature), XP `meditationFirstXp`, unlock
    `journal:stillness` and `codex:cold_peace`, stage -> PRACTICE.
  - Later: XP `meditationDailyXp` (default 40) once per game day (`counters.meditation_day`). **Anti-AFK:** requires an
    active crouch held with the journal for 8 s, gives XP at most once per 24000 game ticks, and the game day index uses
    `Level#getGameTime` **[V]** which does not jump when the night is slept through, so beds cannot skip the
    wait. Death does not reset it (counters persist; cooldowns would not).

Client (`client.quest.MeditationFx`): state per entity id from the events (start tick, duration). While active:
`force_mote` particles spawn 1.5-2.5 blocks around the meditator and drift inward and orbit loosely (budgeted by
`Vfx.count`, culled by `Vfx.inRange`); positional `MEDITATION_LOOP` tickable instance bound to the entity that stops
on break/complete/entity removed (`isRemoved()` **[V]**)/level change/disconnect/timeout (duration + 40 ticks safety).
`MEDITATION_BREAK` or `MEDITATION_COMPLETE` one-shots at the entity. The HUD focus ring reads the owner's own state.

## 6. Practice and the Calling

- `onAbilityUsed(player, "force_push", n)`: the `first_push` Resonance milestone is granted by the force feature itself
  (flag `resonance:first_push`); quest never grants it. During PRACTICE,
  `push_practice += n` (clamped to 5); overlay message `Practice: 3/5`; at 5: XP `practiceXp`, unlock `journal:the_pull`
  and `codex:kyber`, stage -> CALLING.
- `Calling` (every 50 ticks, CALLING stage, journal in either hand): `CaveLocator.findNearestCave(player)` (world feature,
  cached there). Sends `oldrepublic:calling` to the owner only, with `pos` = player eye position, `dir` = horizontal unit
  vector to the cave (the exact cave position is never sent), `intensity` = closeness 0..1 (`1 - clamp(dist/512)`),
  variant 0; variant 1 = "no cave in range" (client plays nothing, journal shows "the pull is faint here").
- Client (`client.quest.CallingFx`): for ~2.5 s after each ping, 3-6 `resonance_shimmer` particles appear 4-7 blocks
  away along `dir` (+-15 degree spread, eye height +-1) and drift slowly along `dir`; one `CRYSTAL_CALLING` ping,
  volume = 0.25 + 0.75 * max(0, dot(look, dir)) and slight pitch rise with closeness, played non-positionally
  (`SimpleSoundInstance.forUI` **[U argument order]**, scaled by `ClientConfig.modSoundVolume`).

## 7. Story: "Echoes in the Stone"

Setting: roughly 3,640 BBY, the cold peace after the Treaty of Coruscant. The Republic is exhausted, the Jedi have
withdrawn from Coruscant to Tython, the Sith Empire waits beyond the Outer Rim. The player is a wanderer on an
unnamed frontier world beyond the Republic's charts, with no master and no Order. The journal is the player's own
notes, first person, plain and observant; codex entries are in a more distant, encyclopaedic voice ("Notes gathered").

| # | Entry id (`journal:`) | Title | Unlocked by | Gist |
|---|---|---|---|---|
| 1 | `the_dream` | The Dream | awakening | Voices in a dark place, the ground pushed away from me when I woke. Objective: calm the disturbance (meditate). |
| 2 | `stillness` | Stillness | first meditation | In the quiet the air moved because I asked it to. Practise on things that mean harm. |
| 3 | `the_pull` | The Pull | practice done | A thin note in the back of the skull, like a struck glass, always from one direction. |
| 4 | `voices_in_the_rock` | Voices in the Rock | cave entered | A camp at the mouth: cutters, drills, crates of broken crystal. Something deeper is still singing. |
| 5 | `cutters` | Cutters | first poacher killed (any stage >= FOCUS) | Mercenaries paid by the crystal; they hear nothing. |
| 6 | `returned_fire` | Returned Fire | first bolt deflected | The bolt came back as if it had been waiting to. |
| 7 | `what_waits_below` | What Waits Below | first trial failure | It knew every move before I made it. It was me. |
| 8 | `reflection` | Reflection | trial completed | I stopped fighting it, and it was gone. |
| 9 | `the_crystal` | The Crystal | crystal obtained | Warm in my hand. A crystal needs a hilt: an emitter (the poachers carry them), grip and pommel; the hidden chamber; a workbench. |
| 10 | `a_blade_of_my_own` | A Blade of My Own | saber constructed | The chapter closes; the search for a teacher begins. |

Codex (`codex:`): `the_force` (awakening), `cold_peace` - "The Cold Peace" (first meditation), `kyber` - "Kyber
Crystals" (the pull), `poachers` - "Crystal Poachers" (cave entered or first poacher), `the_trial` - "The Trial of
Reflection" (trial started), `lightsaber` - "The Lightsaber" (crystal obtained).

Objective text per stage (`quest.oldrepublic.objective.<stage>`) plus counters on the left page:
STIRRING "Something is stirring" (only seen if opened by keybind before awakening), FOCUS "Calm the disturbance:
crouch and stay still with the journal in hand", PRACTICE "Push hostile creatures (n/5)", CALLING "Follow the pull
while holding the journal", TRIAL "Complete the Trial of Reflection at the heart of the cave", CONSTRUCTION "Gather an
emitter, a grip and a pommel; build your lightsaber at a workbench" with live checkmarks from the client inventory
(item ids from the shared table looked up in `BuiltInRegistries.ITEM`), COMPLETE "Chapter complete".

### Lore departures (gameplay adaptations, also to be listed in DECISIONS)
- Midi-chlorian Resonance rising with milestones (design brief section 4).
- Kyber found in a cave on an unnamed frontier world rather than Ilum or another named world; many worlds hide crystal veins.
- A Force-sensitive with no master finds a crystal and builds a saber alone; framed as the crystal choosing the wanderer and
  as a rough first blade, with the search for a teacher as the next chapter.
- The Trial of Reflection and the Shadow Echo are original, inspired by cave trials in the wider canon, not a canonical ritual.
- "Crystal poachers" as a mercenary crew is original.

## 8. Journal screen (`client.quest.JournalScreen extends Screen`)

- Opened by `JournalItem.use` (client side) and by keybind `key.oldrepublic.journal` (default J, GLFW 74) checked in
  `ClientTickEvents.END_CLIENT_TICK` with `consumeClick()` **[V]**; J or Esc closes (`keyPressed(KeyEvent)` **[V]**,
  `KeyMapping.matches(KeyEvent)` **[V]**). Opening plays `JOURNAL_OPEN`.
- Texture `oldrepublic:textures/gui/journal.png`, 320x200 drawn with
  `graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, w, h, texW, texH)` **[V]** (Fabric testmod), scaled down
  when the GUI is narrower than the book (pose `scale` on `Matrix3x2fStack` **[U method names]**).
- Left page: chapter title, current objective (word-wrapped with `font.split(FormattedText, width)` **[V]** and
  `graphics.text(font, FormattedCharSequence, x, y, argb, false)` **[V]**), progress counters, Resonance/Attunement summary.
- Right page: two custom-drawn tabs "Entries" / "Codex" (clicks handled in `mouseClicked(MouseButtonEvent, boolean)` **[V]**),
  a scrollable feed clipped with `enableScissor/disableScissor` **[V]**, scrolled with the wheel
  (`mouseScrolled(double,double,double,double)` **[V]**) and a thin scroll bar. Entries appear in canonical order. Entries
  unlocked during this session (from `journal_entry` events) are marked "(new)", the journal opens on the right tab and
  scrolls to the first of them.
- Text colours are full ARGB (`0xFF3B2F25` ink on the page) since 26.x drops alpha-less colours.
- Before awakening the screen shows blank worn pages with one line ("The pages are empty.").

## 9. HUD (`client.hud`)

The persistent Force panel returns early unless awakened, `ClientConfig.get().showHud` is on, the HUD is not hidden
(`mc.gui.hud.isHidden()`, as used by Fabric's HUD tests) and the player is not spectating. The focus ring, flourishes
and the journal notice are moment-to-moment feedback and stay visible when `showHud` is off (they still respect F1). Registered with `HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id, element)` **[V]**
so chat draws over it. The panel sits in the **top-left corner** (x=6, y=6), which vanilla leaves empty (hearts, hunger,
armour, air and the XP bar are all around the hotbar; effects are top-right; boss bars top-centre), so nothing overlaps.

| Element | Class | Shows from |
|---|---|---|
| Force Energy meter (82x6 bar in a durasteel frame, blue-white fill, slow shimmer, numeric `42/56` on hover-free text) | `ForceHud` | awakening (2 s fade/slide-in the first time it appears in a session after the stage changes) |
| Midi-chlorian Resonance (small crystal glyph + number) | `ForceHud` | awakening |
| Attunement level + XP bar (thin bar under the meter) | `ForceHud` | first XP or stage >= PRACTICE |
| Force Push cooldown (14x14 glyph, dark sweep that empties as `cooldowns.force_push` counts down; total = largest value seen since it appeared) | `ForceHud` | ability `force_push` learned |
| Meditation focus ring (thin ring around the crosshair, 48 segments drawn with `fill`, fills over 8 s) | `FocusRingHud` | own meditation active |
| Level-up / resonance-up banner (short centred banner at 28% height, fades in/out over 3 s) | `FlourishHud` | effect events |
| "Journal updated" notice (top-left under the panel, journal icon + text, 4 s) | `FlourishHud` | `journal_entry` effect |
| Dream overlay | `client.quest.DreamOverlay` (registered with `addLast`) | dream |

Effect handlers owned here: `oldrepublic:level_up` -> banner "Attunement Level N" + `MEDITATION_COMPLETE` at pitch 1.2;
`oldrepublic:resonance_up` -> banner "Resonance +N" + `CRYSTAL_CHIME` at pitch 0.8 (both UI sounds, scaled by
`modSoundVolume`). `oldrepublic:journal_entry` -> notice + `JOURNAL_ENTRY`.

## 10. `/oldrepublicfx` (`client.hud.ClientCommandsFx`)

`ClientCommandRegistrationCallback.EVENT` **[V]** with `ClientCommands.literal/argument` **[V]**:
`/oldrepublicfx` (prints current settings), `preset <reduced|standard|high|cinematic>`, `shake <0-100>`,
`flashing <on|off>`, `hud <on|off>`, `volume <0-100>`; each saves with `ClientConfig.save()` and replies with
`source.sendFeedback(Component)` **[V]**.

## 11. Effect events (ids owned by quest/HUD)

| Id | Sent with | pos / dir / entity / intensity / variant |
|---|---|---|
| `oldrepublic:awakening_dream` | `toPlayer` | player pos / - / player / duration ticks / 0 start, 1 cancel |
| `oldrepublic:awakening_burst` | `around` | player pos / - / player / radius / 0 |
| `oldrepublic:meditation` | `toTracking` | player pos / - / player / duration ticks / 0 start, 1 break, 2 complete |
| `oldrepublic:calling` | `toPlayer` | eye pos / dir to cave / player / closeness 0..1 / 0 found, 1 none |
| `oldrepublic:journal_entry` | `toPlayer` | player pos / - / player / entry index / 0 journal, 1 codex |

No custom payloads and no client-to-server packets: meditation is detected server side and the journal reads the synced
attachment.

## 12. Balance section `quest` (defaults)

`dreamTicks 140`, `burstRadius 5.0`, `burstSlownessTicks 60`, `meditationTicks 160`, `meditationStartDelayTicks 10`,
`meditationMaxDrift 0.15`, `meditationFirstXp 60`, `meditationDailyXp 40`, `practiceHits 5`, `practiceXp 150`,
`callingIntervalTicks 50`, `trialXp 400`, `completeXp 500`, `completeSkillPoints 2`, resonance:
`awakeningResonance 250`, `trialResonance 600`, `firstSaberResonance 400` (`first_push` belongs to the force feature).

## 13. Persistence and edge cases

- Death: data is copy-on-death; sessions are transient and end (meditation breaks, dream is skipped by the lethal damage).
- Relog mid-dream: nothing persisted; the time trigger re-fires on the next tick after join, the sleep trigger needs another sleep.
- Dimension change: meditation breaks (level check); calling keeps working (CaveLocator is per dimension).
- Journal lost: craftable (book + leather + iron nugget, shapeless; recipe JSON format to be confirmed by probe) and
  `/oldrepublic journal` (op) gives one; the keybind opens the screen without the item.
- Multiplayer: all decisions server side; only owner gets dream, calling and journal events; meditation and burst are
  visible to trackers.

## 14. Dependencies and requests to other features / lead

- Force: `com.oldrepublic.force.ForcePush.grant(ServerPlayer)` must exist; `ForceParticles.AIR_RIPPLE` (common particle type,
  reused for the awakening ripple); force grants the `first_push` Resonance milestone.
  Force should call `QuestHooks.onAbilityUsed(player, "force_push", hostileHits)` after each successful cast.
- World: `CaveLocator.findNearestCave` and calls to `onCaveEntered`, `onTrialStarted/Completed/Failed`, `onCrystalObtained`.
- Enemy/saber: `onPoacherKilled`, `onBoltDeflected`, `onSaberConstructed`.
- Keybind category: the journal key uses `com.oldrepublic.client.force.ForceKeys.CATEGORY` (created by the force feature,
  lead decision); quest never registers a category itself.

## 15. API verification summary

Verified against the 26.2 probe logs or Fabric 26.2 sources: everything used in the code except the items below.
Notable confirmations from the Phase 2 probes: `Level#getGameTime`, `Entity#setDeltaMovement`, `MobEffects.SLOWNESS`,
`SimpleSoundInstance.forUI(event, pitch, volume)` (bytecode), `SingleQuadParticle.Layer.TRANSLUCENT`,
`EntitySleepEvents` callbacks, `ServerLivingEntityEvents.AfterDamage(entity, source, base, taken, blocked)`,
`ClientCommands`, `FabricParticleTypes.simple()`, `PendingParticleProvider`, `Gui.screen()/setScreen()` (screens are
opened with `mc.gui.setScreen`), `Screen.extractRenderStateWithTooltipAndSubtitles` (draws the background itself),
`mouseScrolled(double,double,double,double)`, `mouseClicked(MouseButtonEvent, boolean)`, `KeyEvent`, `ARGB.multiplyAlpha`,
`Registry#getOptional(Identifier)`, the item model / recipe JSON formats, `Matrix3x2fStack` push/pop/translate/scale
(used by Fabric's HUD code).

Still marked `// API-CHECK` in code:
- `GuiGraphicsExtractor#enableScissor` taking coordinates in the current pose (used inside the scaled journal).
- The ARGB tint argument of the 11-argument `blit` overload.

