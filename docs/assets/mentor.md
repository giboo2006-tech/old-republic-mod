# Chapter Two assets (mentors, ledger, story particles, story structures)

All files are **placeholders**: original art made by our own scripts (PIL for textures, a Python NBT writer for
structures), deterministic, run from the repository root. Nothing is copied or imitated from Star Wars games or films.

- Textures: `python3 tools/assets/gen_textures_mentor.py`
- Structures: `python3 tools/assets/gen_structures_story.py` (uses `gen_structures.py`)
- Sounds `story.*`: recipes in `tools/assets/sounds/story.py`, rendered with `python3 tools/assets/gen_sounds.py --feature story`

| Path (under `src/main/resources/`) | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `assets/oldrepublic/textures/entity/jedi_mentor.png` | Master Ilyra Saelen, 64x64 player-layout skin with the mentor model's extra robe (u16 v32, 8x14x4), skirt (u0 v50, 9x9x5) and lowered hood (u36 v50, 9x3x5) | placeholder generated | Human woman, weathered. Sand and brown layered robes over a dark under-tunic, a hood usually down, grey-streaked hair tied back, a plain belt with a hilt clip. Calm, practical, frontier-worn; no insignia beyond a small Order mark at the belt. |
| `assets/oldrepublic/textures/entity/sith_mentor.png` | Overseer Teyvar Sorn, 64x64 skin with the half-cape (u40 v32, 8x16x1) | placeholder generated | Human man, Imperial greys, a black half-cape over one shoulder, gloves, a scar over one brow, close-cropped dark hair. Officer's bearing, not a robed sorcerer. |
| `assets/oldrepublic/textures/gui/sprites/portrait/jedi_mentor.png`, `sith_mentor.png` | Dialogue and Standing portraits, 32x32 GUI sprites (`oldrepublic:portrait/<role>_mentor`) | placeholder generated | Head-and-shoulders, three-quarter view, matching the skins; a plain dark background that reads under the dialogue frame's accent rule (pale gold / deep red). |
| `assets/oldrepublic/textures/item/poachers_ledger.png` | Poachers' Ledger, 16x16 | placeholder generated | A grimy, water-stained account book bound with cord, a page corner sticking out, a smudged tally scratched on the cover. |
| `assets/oldrepublic/textures/particle/voice_warm_0..2.png` | `oldrepublic:voice_warm`, the warm voice in Calling variant 2 | placeholder generated | Tiny soft glints, pale gold, alpha falling off to the edge. Full-bright. Not sparkly. |
| `assets/oldrepublic/textures/particle/voice_dust_0..2.png` | `oldrepublic:voice_dust`, the cold voice and the mentor's departure puff | placeholder generated | Small cold grey-blue dust flecks, lit by the world. |
| `assets/oldrepublic/textures/particle/ash_mote_0..2.png` | `oldrepublic:ash_mote`, Calling variant 3 (smoke on the ridge) | placeholder generated | Dark grey ash flakes with a faint ember edge on one, lit by the world. |
| `assets/oldrepublic/particles/voice_warm.json`, `voice_dust.json`, `ash_mote.json` | Particle sprite lists | final | - |
| `assets/oldrepublic/items/poachers_ledger.json`, `models/item/poachers_ledger.json` | Item definition and `item/generated` model | final | - |
| `data/oldrepublic/structure/story/jedi_camp.nbt` | 7x5x7 (Milestone 6: rebuilt at this size) camp: bedroll under a canvas lean-to, low stone seat, lantern on a post, training stump | placeholder generated | Keep the centre and the middle row's ends free (mentor spots). No floor circles or sigils. |
| `data/oldrepublic/structure/story/sith_camp.nbt` | 7x5x7 (Milestone 6: rebuilt at this size) camp: dark field shelter, supply crate, brazier (lit campfire), weapon rack with a training staff | placeholder generated | Same free spots. Imperial, functional, temporary. |
| `data/oldrepublic/structure/story/raider_camp.nbt` | 13x6x13 (Milestone 6: now a 51x26x51 stronghold) raider camp: palisade stubs, two tents, crates, a signal fire on hay and a cook fire, three binding posts, a smashed homestead cart | placeholder generated | Keep the crew, acolyte, captain and captive spots listed in the generator free. The signal fire's smoke is the landmark. |
| `assets/oldrepublic/sounds/story/*.ogg` | `story.two_voices`, `story.lesson_complete`, `story.duel_begin`, `story.duel_end` | placeholder generated (Phase 0) | See `docs/AUDIO_BRIEF.md`. |

The mentor model (`client.mentor.MentorModel`) is the player model plus the robe, skirt, hood and cape boxes; each
role shows only its own pieces. The kneeling/meditating pose is done in code.

Milestone 3: `gen_textures_mentor.py` now also writes `textures/particle/falling_ember_0..3.png` (Chapter Three's
Calling to Courier's Fall, see `docs/assets/quest.md`). The Chapter Two files above are byte-identical to before; no
mentor art changed.
