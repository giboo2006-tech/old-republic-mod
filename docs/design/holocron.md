# Holocrons: pedestals, gatekeepers, choices, return and carry, echoes, the Archive

Milestone 3 spec: section 4 (with 11.2, 11.3, 12.1, 13.2, 13.3, 14 and 16). This page describes how the holocron
feature implements it. Code: `com.oldrepublic.holocron` (server and common), `client.holocron` (effects, the
projected gatekeeper, the Holocrons journal tab). Tests: `HolocronGameTests` (18 methods) and the screenshot steps
in `gametest.m3.HolocronClientSteps` (35 to 37).

## Storage

- One persistent attachment `oldrepublic:holocrons` (`HolocronAttachments.HOLOCRONS`, `HolocronData`), copied on
  death and dimension change, synced **to its owner only**. Codec keys: `attuned` (ids), `choices` (id to choice
  ordinal), `returned` (ids), `attuned_at` (dimension, pedestal, site key and id of each pedestal that gave a world
  holocron), `serials` (id to the issue number of its current item), `echoes` (pedestals that gave an echo, a ring
  of at most `echoRing`), `milestones` (bits for 3, 6, 9 and 12 and six skill point drip bits), `archive` (choice
  ordinal, -1 before), `archive_pending` and `restore_day`. Every list and map is capped (16 ids, 64 echoes).
- Changes go through `HolocronService.data(p)` (a mutable copy) and `commit(p, d)`, which attaches a **sealed**
  copy: `seal()` takes the frozen `syncView()` that the network thread encodes later, so a sync never iterates
  collections the server thread is changing. Read again after a commit instead of keeping the copy.
- The item component `oldrepublic:holocron` (`HolocronStamp`: id, owner, serial) marks a holocron item. Only the
  copy whose serial equals `serials[id]` is current; restoring or returning bumps the serial and every older copy
  goes silent.
- Tunables: `config/oldrepublic/balance.json`, section `holocron` (`HolocronBalance`, read with
  `HolocronBalance.get()`).

## The holocrons

`HolocronCatalog` lists the 15 ids of `JournalEntries.HOLOCRON_IDS`: four world holocrons for each kind (Jedi pale
stone cube, Sith black glass pyramid, Wayfarer brass sphere) and the three Archive holocrons. Pedestals give a
kind's world holocrons in catalog order; a pedestal whose block entity saves a `fixed_id` gives only that
holocron (Courier's Fall: the Wayfarer's Compass). `PedestalSite.resolve` reads `fixed_id` from the block entity's
saved data by name, so it needs no world-feature class, and treats a Courier's Fall pedestal without one as fixed to
the Compass. The gatekeeper index (effects, portraits) is the id's position in that list.

Teaching (`HolocronCatalog.teaching`): seven holocrons teach an Exploration or path node; the rest are lore only.
Seal and Break never teach. A node the player already owns gives +1 skill point instead.

## Using a pedestal (`HolocronService.usePedestal`, `HolocronPedestals.use`)

1. `HolocronEvents.BEFORE_PEDESTAL` may take the use over (W2's Pathfinder ceremony).
2. Story gate: the player must be awakened and at Chapter Three's summons or later (`REFUSED_STORY`). The test
   seam `HolocronPedestals.use(..., skipStoryGate, day, random[, counted])` skips this gate and takes the day and
   the randomness as arguments.
3. One moment at a time, per player and per pedestal (`REFUSED_BUSY`): during the player's own moment "It is
   already speaking to you."; while another player's moment is open at this pedestal (its cradle holds one rising
   holocron) "Another listens to it now.", for every kind of use.
4. No awake hostile creature within `pedestalClearRange` (16) of the pedestal: hostile attitude, an engaged squad
   or one the player provoked. No-AI creatures (dormant sentinels) never count (`REFUSED_HOSTILE`).
5. Sneak-use restores a lost holocron: the first attuned, unreturned, unbroken id of the pedestal's kind of which
   the player holds no current copy gets a new serial and a new item. Once per day index
   (`gameTime / restoreCooldownTicks`) (`RESTORED`, `NOTHING_TO_RESTORE`, `RESTORE_COOLDOWN`).
6. A pedestal that has not given a world holocron starts the attunement of the kind's next id
   (`ATTUNE_STARTED`).
7. Once all four of the kind are attuned, a pedestal that has not echoed yet gives an **echo**: `echoXp`, one
   echo shard, a line from a random gatekeeper of the kind and the `holocron_echo` effect (`ECHO`).
8. Otherwise "The pedestal has spoken" (`ALREADY_SPOKEN`).

## The attunement moment (`HolocronAttunement`)

- `start`: the holocron rises out of the cradle over `riseTicks` (effect `holocron_attune`, intensity 1, entity id
  = rise ticks, variant = kind).
- After `gatekeeperDelayTicks` the gatekeeper forms in front of the attuning player only (`holocron_gatekeeper`
  to that player, direction = pedestal to player, variant = gatekeeper index) and the speakerless dialogue
  `oldrepublic:holocron_<id>` opens.
- The dialogue's choose actions call `confirm(p, choice)`, which checks `HolocronRules.allowed(path, kind)`:
  Jedi reading Sith: Seal or Listen; Sith reading Jedi: Break or Listen; an Independent (or no path) reading Jedi
  or Sith: Listen; everything else: Remember (`OWN`).
- Cancel paths, all with nothing attuned and nothing consumed (the holocron sinks back, `holocron_attune`
  intensity 0, and the figure folds): the dialogue closes with no choice (`Dialogues.ON_CLOSE`), damage during
  the moment, death, logging out, changing dimension, walking more than the dialogue end distance away, server
  stop, and a moment open for `MAX_MOMENT_TICKS` (2,400, the client display's watchdog), so nobody can hold a
  shared pedestal busy. The sink event goes to the pedestal's own level, even after a dimension change.

## Choices and first rewards (`HolocronRewards.attune`)

Once per id: the data (`attuned`, `choices`, `attuned_at` for a pedestal), `baseXp` XP (+`relicLoreXpBonus` with
Relic Lore), `resonance` Resonance plus `listenResonance` (a Jedi-path Listen to a Sith holocron; a Sith-path Listen
adds none) or `breakResonance` (Break), the codex entry, the teaching, and the choice's consequences:

| Choice | Alignment (major, once per source) | Other |
|---|---|---|
| Remember (`OWN`) | none | the item |
| Seal | `sealShift` (+3) | the item (sealed: no teaching) |
| Listen | Jedi `jediListenShift` (-4), Sith `sithListenShift` (+4) | the item; "the lesson waits" when the node is dormant |
| Break | `breakShift` (-3), Sith mentor trust +`breakTrust` | no item, no serial |

Every confirmed choice sends `holocron_choice` (variant = choice, kind in bits 8+, intensity 1 at a pedestal and 0
for an operator grant), then `progress`: a skill point every `spEvery` (2) world holocrons, Resonance at 3, 9 and
12, +1 SP at 6, and the Keeper of Echoes title at 12, each bit granted once. `QuestHooks.onHolocronAttuned` fires
last.

## Return, carry, shards (`HolocronReturns`, `HolocronCarry`)

- **Return**: the mentor's or an officer's dialogue calls `holocron:return:<jedi|sith|republic|imperial>`. The
  first world holocron in return order that the player carries as their own current copy and the receiver accepts
  (Saelen: Jedi holocrons and the Sith ones a Jedi sealed; Sorn: Sith holocrons from a Sith; officers: any) is
  taken, marked returned and its serial bumped. Mentors give trust and Order reputation, officers reputation and
  a commendation; an army that holds the player Hostile refuses. The knowledge and nodes stay.
- **Carry**: +`carriedRegenPct`% Force regen per distinct, owned, current holocron in the main inventory, at most
  `carriedMax`, of the kinds the path draws on (Jedi not Sith, Sith not Jedi, others any). A `ForceModifier`
  with a cached count refreshed from an inventory hash every `carryRefreshTicks`; a change re-syncs the Force data.
- **Shards**: `shards:exchange:<army>` trades `shardsPerCommendation` shards for a commendation;
  `shards:amends:<army>` gives `amendsShards` shards for +`amendsRep` standing while the army is Wary.

## The Archive (`HolocronArchive`)

After the boss, the hoard plinth calls `HolocronService.openArchive(p, plinth)`: the owner whose boss is resolved
or a helper marked pending (`markArchivePending`) gets the `archive_hoard` dialogue; everyone else a line. The
choice applies once (`resolveArchive`): `archiveXp` XP, a journal entry, the recorded choice, the quest hook and
the `archive_choice` effect at the plinth, then:

- **Return**: Jedi: Order and Republic reputation, mentor trust and a light shift; Sith: Empire reputation and
  trust; others: Frontier and Republic reputation and a small light shift. The Archive codex entries unlock.
- **Keep**: the three Archive holocrons are attuned (choice Remember, items given), each teaching its node for the
  path; Jedi and Independents take a dark shift.
- **Unmake**: Resonance, a light shift, and -50 reputation with both Orders.

Shifts are applied before the choice id is recorded, because a major shift is refused once its source is a
recorded choice.

## Commands

`/oldrepublic holocron grant <player> <id>` (full first-time rewards; Remember, or Listen when Remember is not
allowed), `reset <player>`, `shards <player> <n>` and `status <player>`.

## Client

- **Banner and toast** (`HolocronAttunedWatcher`): a diff of the synced attachment shows "Holocron attuned" with
  the gatekeeper's name as a banner (`Banners.show`) and a toast. The state found right after joining is only
  recorded; the Archive's three holocrons share one banner.
- **Tooltip**: the item names its gatekeeper; the client adds the owner's status line (answers you, silent because
  a newer copy exists, sealed, returned, or bound to someone else).
- **Holocrons tab** (`HolocronShelfPanel`, journal tab `holocrons`, shown once anything is attuned): the 3x4 shelf
  (Jedi, Sith, Wayfarer rows) and, once the hoard is resolved (`archive_pending` or a recorded choice), the
  Archive's three niches; lit holocrons or dark silhouettes (also dark when
  sealed, broken or returned) with small marks for carried, sealed, broken and returned; "n/12 attuned" and a
  count per row; "Carried: +8% regen (2/3)" (the client shows the default balance values); the echo shard count.
  The right page reads the hovered holocron: portrait, name, kind, status (Broken, Returned, Carried, Sealed,
  Listened, At rest; the Archive's fate), what it taught, or the kind's hint while unfound.

## Effects (spec 13.3), phase by phase

All particles go through `Vfx` (range, density); the attunement with its figure stays within 120 particles at
Standard. Nothing uses rings, glyphs or flashes: with flashing effects off every flare becomes a steady glow and
the projection's banding stops breathing.

### `holocron_attune`

1. **Rise** (`RisingHolocron`): the real holocron item model rises 0.5 blocks out of the cradle over the rise
   ticks, easing out, turning slowly; `holocron.rise`. Pale alcove dust drifts upward around the pedestal the whole
   rise.
2. **Seams split**: at seven tenths of the rise, `holocron.open` and a dozen thin specks of seam light spill
   sideways from the holocron's edges.
3. **Hover**: it bobs gently; seam light motes leave it every few ticks.
4. **Sink** (intensity 0): it eases back into the cradle (the solid pedestal hides it) and a little grit settles.
   A watchdog sinks any holocron left hovering after 2,400 ticks.

### `holocron_gatekeeper` (to the attuning player only)

1. **Form** (`GatekeeperFigure`): a narrow translucent pyramid of light spills from the holocron's seams to a spot
   two blocks beyond the pedestal, widening as a thin standing figure assembles there over 20 ticks, facing the
   player: the player model with the pale banded projection skin, full-bright, multiplied by the kind's tint (warm
   gold, rust crimson, brass). Motes drift only at its edges while it assembles. `holocron.gatekeeper_form` and the
   `holocron.hum` loop start.
2. **Stand**: faint `projection_band` particles drift up through it every few ticks; a slow breath of its alpha
   with flashing on, steady with it off.
3. **Fold** (intensity 0): it narrows back into the light over 16 ticks, edge motes again;
   `holocron.gatekeeper_fade`, the hum fades out. A watchdog folds it on death, more than 16 blocks away, a level
   change or after 2,400 ticks; the hum has its own watchdog with the same limits.

### `holocron_choice`

- **Seal**: the holocron goes dark (the item's dark variant), the seams close with a low uneven exhale of dust and
  the last seam light is drawn in; then it drifts into the player's hands. `holocron.seal`.
- **Break**: the holocron is gone at once; the shell splits into chips (calcite, blackstone or gold), its light
  escapes outward and gutters out (dimmer and slower with flashing off), and fine dust of the seam colour falls for
  a moment. `holocron.break`.
- **Listen**: the holocron drifts into the player's hands while motes of its light stream into their chest for a
  second. `holocron.listen`.
- **Remember**: it settles into the player's hands with a short stream of light (the choice-specific sound is the
  dialogue's own). With intensity 0 (an operator grant) the same plays at the player.

### `holocron_echo`

A faint, brief silhouette of a gatekeeper over the pedestal (half the figure's alpha, 2.5 seconds), then crystal
glints converge on the cradle where the echo shard forms. `holocron.echo`.

### `archive_choice`

- **Return**: the three seams' light sinks and goes out, a dust exhale; `holocron.archive_return`.
- **Keep**: the seams warm with a slow glow and their light drifts into the player; `holocron.archive_keep`.
- **Unmake**: one after another the seams flare (a steady glow with flashing off), the light drains out as falling
  dust and the shells crack into chips; `holocron.unmake`.

## Tests

`HolocronGameTests` drives every rule through the seams (`HolocronPedestals.use` with the story gate skipped, a
fixed day and a seeded random; `HolocronAttunement.confirm`): ordering, gates and hostiles, the four choices and
their shifts, return and serials, carry, echoes and shards, restore and its day, milestones, the Archive, the
commands, sync sealing and the balance view. The client steps build a small stage high above the player, run the
real pedestal flow to the projected gatekeeper (screenshot 35) and cancel it, grant four holocrons for the tab
(36), play the Archive "keep" effect (37), then put the player's holocron data, items, shards, position and the
stage back.
