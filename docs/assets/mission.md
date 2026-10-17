# Mission assets (Milestone 3: missions, contracts, officers, discovery, story sounds)

All art here is a **placeholder generated** by our own deterministic scripts (PIL, numpy), original. Nothing is
copied from or traced over Star Wars games or films; the officers are original characters and their uniforms are
generic military shapes in the armies' colours (navy and gold, grey and crimson).

- Textures: `python3 tools/assets/gen_textures_quest.py` (the mission items and the officer portraits were added to
  the quest generator; its older outputs are unchanged).
- Commendations: the lead's Phase 0 art from `python3 tools/assets/gen_textures_m3items.py` (not redrawn here).
- Sounds: the 13 Milestone 3 recipes in `tools/assets/sounds/story.py`, rendered with
  `python3 tools/assets/gen_sounds.py --m3 --feature story` (the placeholder file names and `sounds.json` entries
  from Phase 0 are kept).

Paths are under `src/main/resources/assets/oldrepublic/` unless they start with `data/`.

## Items

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/item/archive_record.png` (16x16) | `archive_record`, R2's stolen record: a sealed data slate from the Watch Archive's vault, dark casing, pale-blue ledger lines, an amber vault seal | placeholder generated | A hand-sized data slate, worn and scuffed from the smugglers' hands; the vault seal is the Archive's amber, unbroken. Must read as "official record", not a weapon or a gem. |
| `textures/item/fence_book.png` (16x16) | `fence_book`, I1's book of names: a slim dark-green cloth notebook, ink-stained page edges, a cord wound round it and a red ribbon marker | placeholder generated | A cheap, much-handled notebook a fence would keep in a coat: cloth cover, pages swollen with use, a cord tied round it. Nothing official about it. |
| `items/archive_record.json`, `items/fence_book.json` | Item definitions (`minecraft:model`) | final (data) | - |
| `models/item/archive_record.json`, `models/item/fence_book.json` | `item/generated` models | final (data) | - |
| `textures/item/republic_commendation.png` (16x16) | `republic_commendation`: a blue-white enamel chevron pin on a navy field | placeholder generated (lead, Phase 0) | A small enamel service pin: a white chevron edged in pale blue on a navy oval, a thin gold rim. Clean and official; it should look awarded, not looted. |
| `textures/item/imperial_commendation.png` (16x16) | `imperial_commendation`: a crimson and black service bar with steel end clasps | placeholder generated (lead, Phase 0) | A rectangular ribbon bar, crimson with a black centre stripe, steel clasps at both ends. Severe and precise, the opposite of ornate. |

Both mission items stack to 1. Commendations are ordinary stackable items; their only use in M3 is the trooper armour
recipes (spec 9.3).

## Officer portraits

GUI sprites (`oldrepublic:portrait/officer_<army>`), drawn by the dialogue screen beside the officer's name, in the
mentor portraits' style: head and shoulders on a dim background with a rim light.

| Path (`textures/gui/sprites/portrait/`) | Who | Status | Brief for the final artist |
|---|---|---|---|
| `officer_republic.png` (32x32) | A Republic outpost officer (one portrait for all six names) | placeholder generated | Middle-aged, tired but steady; navy uniform with a muted gold shoulder stripe, a peaked cap with a gold band. A frontier garrison officer, not a parade-ground general. |
| `officer_imperial.png` (32x32) | An Imperial outpost officer | placeholder generated | Lean, composed, unreadable; grey uniform with crimson piping, a black-banded grey peaked cap. Precise posture, no insignia that copies a real or film design. |

## Data (final)

| Path | What it is |
|---|---|
| `data/oldrepublic/mission/*.json` (10) | The four story missions, the four contracts and the two duties (`docs/design/mission.md`) |
| `data/oldrepublic/dialogue/officer_republic.json`, `officer_imperial.json` | The officers (work, turn-ins, amends, holocron return, shard trade, report) |
| `data/oldrepublic/dialogue/yield_intercept.json`, `yield_border.json` | The yielded sergeants (Spare / Interrogate / Execute, Spare / Hand over / Execute) |
| `data/oldrepublic/dialogue/r1_leave_confirm.json`, `i1_burn_confirm.json` | The two at-site confirmations |
| `data/oldrepublic/loot_table/gameplay/frontier_contract.json` | Frontier contract pay: 3 durasteel, a lumen thread or grave silk, 2 to 4 emeralds |
| `data/oldrepublic/loot_table/gameplay/r1_supply.json` | R1's supply crate when the wounded are left: 6 durasteel |

The six mission camp templates (`structure/mission/*.nbt` and their anchor sidecars) are made by the story builder
(`gen_structures_story_m3.py`); the mission feature reads their anchors by name only.

## Sounds (`tools/assets/sounds/story.py`)

All synthesised (numpy), mono, deterministic; the stream flag and categories are Phase 0's `sounds.json`.

| Event | Files | Placeholder | Brief for the final sound designer |
|---|---|---|---|
| `site.discovered` | `site/discovered1..3` | A discovery sting (files 3.8 to 4.1 s with the tail): a soft low swell opening into a rising two-note bell figure over a held fifth, in three timbres (warm, hollow, metallic) and three keys | A sense of arrival and scale, never a fanfare: a breath of wind into a soft chime. The three variants should feel like one family (ruin, tomb, outpost) without being picked by site. |
| `story.ones_that_sing` | `story/ones_that_sing` (streamed, seamless loop) | 8 s dream bed: crystals singing inside stone, slow beating clusters over a deep drone, gusts that snap like banners in a far wind | Final brief: `docs/assets/quest.md`, "Sounds `story.*` (Chapter Three)" (that table is the authority for `story.*`). |
| `story.calling_holocron` | `story/calling_holocron` | A crystal hum swell: a held cluster rises, blooms and settles (3.8 s with the tail) | Final brief: `docs/assets/quest.md`, "Sounds `story.*` (Chapter Three)" (that table is the authority for `story.*`). |
| `story.calling_reliquary` | `story/calling_reliquary` | A deep resonance under stone: a low hum with slow overtones that swell and beat (5.0 s with the tail) | Final brief: `docs/assets/quest.md`, "Sounds `story.*` (Chapter Three)" (that table is the authority for `story.*`). |
| `story.calling_fall` | `story/calling_fall` | A distant crackle: embers and a far fire, a soft low roll under it (4.3 s with the tail) | Final brief: `docs/assets/quest.md`, "Sounds `story.*` (Chapter Three)" (that table is the authority for `story.*`). |
| `story.collector_fallen` | `story/collector_fallen` | A heavy low impact, crystal shards falling away, then a slow resolving chord (5.1 s with the tail) | Final brief: `docs/assets/quest.md`, "Sounds `story.*` (Chapter Three)" (that table is the authority for `story.*`). |
| `story.rank_three` | `story/rank_three` | A three-bell ascent (root, fifth, octave) over a warm swelling pad (5.3 s with the tail) | Final brief: `docs/assets/quest.md`, "Sounds `story.*` (Chapter Three)" (that table is the authority for `story.*`). |
| `story.cairn_touch` | `story/cairn_touch` | A soft stone thud, a trickle of grit, then one small rising glint (2.5 s with the tail) | Final brief: `docs/assets/quest.md`, "Sounds `story.*` (Chapter Three)" (that table is the authority for `story.*`). |
| `story.blade_touch` | `story/blade_touch` | A close, slow hum swell with a soft contact shimmer (2.7 s with the tail) | Final brief: `docs/assets/quest.md`, "Sounds `story.*` (Chapter Three)" (that table is the authority for `story.*`). |
| `story.tablet_break` | `story/tablet_break` | A seal cracks, then a stone tablet snaps in two and drops with a puff of dust (2.1 s with the tail) | Final brief: `docs/assets/quest.md`, "Sounds `story.*` (Chapter Three)" (that table is the authority for `story.*`). |
| `mission.accept` | `mission/accept` | A holo-terminal confirmation: a soft click and two rising tones (1.1 s with the tail) | A terminal accepting an order: crisp, quiet, short. |
| `mission.update` | `mission/update` | A single soft blip with a tick under it (0.5 s with the tail), quieter than accept | Progress on the action bar; must not tire after the tenth kill. |
| `mission.complete` | `mission/complete` | A three-note rising bell arpeggio and a short warm pad (3.4 s with the tail), distinct from `story.lesson_complete` | Work done and paid: satisfied, smaller than a story beat. |
