# Force power assets, Milestone 5: the second bar, Stasis, Valor, Drain, Fear, Rage, Mind Trick and the new nodes

Everything below is original art and sound made by our own scripts. Nothing is copied from, or imitates, a Star Wars
game or film effect, icon or sound. The powers are drawn only with rime, dust, motes, smoke, embers and bent air (spec
15). There is no glow, no shell and no beam, and nothing draws ground circles, halos, glyphs or outlines.

Textures are **placeholders** made by PIL, deterministically, with their own seeded random sequence, so earlier files
never change:

- `tools/assets/gen_textures_force.py --m5` makes the particles, their JSONs and the status effect icons.
- `tools/assets/gen_textures_skill.py --m5` makes the node icons, with the shared pen and the tree palettes.

Sounds are **designed stand-ins** from `tools/assets/sounds/force_m5.py`, rendered by
`python3 tools/assets/gen_sounds.py --m5 --feature force_m5`. Their file names are the Phase 0 placeholder names, so
`sounds.json` and the subtitles (lead files) do not change.

The design is in `docs/design/powers_m5.md`.

## Textures

All paths are under `src/main/resources/assets/oldrepublic/`.

### Particles

All are 8 × 8 RGBA, three frames each, with a final JSON in `particles/`.

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/particle/stasis_rime_{0,1,2}.png`, `particles/stasis_rime.json` | `oldrepublic:stasis_rime`: Stasis's rime motes | placeholder generated (JSON final); unused since October 2026 (`force_october.md`) | Small frosted flakes: three thin crossing facets of uneven length round a soft core. Near-white, so the code can tint them pale ice blue while held and dull grey grit when they fall. They hang perfectly still on the target, so they must read as frost on the air, never sparkles or light. Never a ring. |
| `textures/particle/drain_mote_{0,1,2}.png`, `particles/drain_mote.json` | `oldrepublic:drain_mote`: Drain's stream | placeholder generated (JSON final); unused since October 2026 (`force_october.md`) | Soft blobs with a denser heart, colourless (the code tints them dark red). Many drift in a thin line from the target to the caster's hand, so each must be small and soft-edged. Never a beam, never glowing. |
| `textures/particle/valor_dust_{0,1,2}.png`, `particles/valor_dust.json` | `oldrepublic:valor_dust`: Valor's lifted dust | placeholder generated (JSON final); unused since October 2026 (`force_october.md`) | Soft specks with a small brighter heart, colourless (the code tints them warm gold). Lifted in uneven puffs by a gust, then a few rise slowly from each player. Bright dust in sunlight, not sparks or magic. |

### Status effect icons

18 × 18, drawn as small pictograms with a dark outline; shown on the HUD and in the inventory.

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/mob_effect/stasis.png` | `oldrepublic:stasis` | placeholder generated | A pale ice crystal with rime motes around it: held still. |
| `textures/mob_effect/fear.png` | `oldrepublic:fear` | placeholder generated | Three dark smoke wisps curling up: dread, not a skull. |
| `textures/mob_effect/pacified.png` | `oldrepublic:pacified` | placeholder generated | A closed, calm eye. |
| `textures/mob_effect/valor.png` | `oldrepublic:valor` | placeholder generated | A warm upward flame with a few rising motes: courage. |
| `textures/mob_effect/rage.png` | `oldrepublic:rage` | placeholder generated | Three ragged red slashes with embers. |
| `textures/mob_effect/drained.png` | `oldrepublic:drained` | placeholder generated | A dark-red drop with a thin thread of motes leaving it. |

### Node icons

16 × 16 GUI sprites in `textures/gui/sprites/skill/`, in each tree's light: Jedi pale gold, Sith deep red,
Exploration weathered brass and sand, Saber pale blade blue and steel.

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `force_stasis.png` | Force Stasis (Jedi) | placeholder generated | An open palm with an ice crystal hanging before it. |
| `stasis_field.png` | Stasis Field | placeholder generated | Three crystals held together: several foes at once. |
| `stasis_lock.png` | Stasis Lock | placeholder generated | One crystal with two clamps closing on it. |
| `force_valor.png` | Force Valor (Jedi Master) | placeholder generated | A raised blade beside an open palm, with rising motes. |
| `valor_shared.png` | Rallying Light | placeholder generated | Three allies as points of light, linked in a triangle. |
| `valor_steadfast.png` | Steadfast | placeholder generated | A blade planted on a ground line, unmoving. |
| `force_drain.png` | Force Drain (Sith) | placeholder generated | A clawed palm with motes shrinking toward it. |
| `drain_life.png` | Drain Life | placeholder generated | A dark heart-shaped drop trailing motes. |
| `drain_essence.png` | Drain Essence | placeholder generated | A spiral of motes winding into a core. |
| `force_fear.png` | Force Fear (Sith) | placeholder generated | A cupped palm sending out waves of smoke. |
| `fear_terror.png` | Terror | placeholder generated | Broken rings going out all round: uneven arcs, never a clean circle. |
| `fear_dread.png` | Nightmare | placeholder generated | An eye over curling wisps. |
| `force_rage.png` | Force Rage (Sith Darth) | placeholder generated | A blade with embers streaming off it. |
| `rage_unbound.png` | Bloodthirst | placeholder generated | A blade and a red drop. |
| `rage_cold.png` | Cold Fury | placeholder generated | A dimmer blade with a frosted edge and small crystals. |
| `force_mind_trick.png` | Mind Trick (Exploration) | placeholder generated | A head in profile, two raised fingers beside it, and soft arcs of bent air. |
| `mind_trick_confuse.png` | Confusion | placeholder generated | A head with a spiral in it. |
| `mind_trick_mass.png` | Many Minds | placeholder generated | Three heads under one arc. |
| `wayfarers_stride.png` | Wayfarer's Grace (Exploration, Independent) | placeholder generated | Footprints walking toward a low sun. |
| `form_makashi.png` | Form II: Makashi (saber, W7's mechanics) | placeholder generated | A slender blade with a small precise arc. |
| `form_djem_so.png` | Form V: Djem So | placeholder generated | A heavy diagonal blade with impact lines. |
| `form_jar_kai.png` | Jar'Kai | placeholder generated | Two crossed blades. |

### Rage vignette

The vignette has no texture. It is drawn in code (`RageFx.extractVignette`) as filled dark-red bands that fade inward
from the screen's edges.

## Sounds

All are synthesised from noise, filters, sweeps, partials and thumps (the `gen_sounds.py` helpers); nothing is
sampled. They are mono OGG at 44.1 kHz. Loops are seamless and are started and stopped by the client
(`PowerLoopSound`).

| Event | File (under `assets/oldrepublic/sounds/`) | Length | Final sound (as rendered) | Brief for the final sound designer |
|---|---|---|---|---|
| `force.stasis.cast` | `force/stasis_cast.ogg` | 2.66 s | A glassy chime: a struck pane of ice ringing high and clean, with a dry crackle of frost as it takes hold. | The moment the air locks. Clean and cold, not magical sparkle. |
| `force.stasis.loop` | `force/stasis_loop.ogg` | 3.00 s, loop | A faint, high shimmer of still air over slow creaks of rime settling. | Plays on the held creature until the hold ends; quiet, so several can play at once. |
| `force.stasis.break` | `force/stasis_break.ogg` | 1.31 s | The hold shatters: a crisp crack of ice and a patter of grit falling away. | Must read in a fight: the creature is free. |
| `force.valor.cast` | `force/valor_cast.ogg` | 3.54 s | A low swell: a warm open chord rising out of a breath of air, then settling, unhurried. | Rallying and warm, never triumphal brass. |
| `force.valor.end` | `force/valor_end.ogg` | 2.16 s | The chord's last breath falls away softly. | A quiet sign that the window has closed. |
| `force.drain.loop` | `force/drain_loop.ogg` | 2.00 s, loop | A slow, wet pull of air through a narrow throat, throbbing twice a second (the pulses), over a dark drone. | Uneasy, not gory. The throb should line up with the 10-tick pulses. |
| `force.drain.end` | `force/drain_end.ogg` | 1.42 s | The pull sighs out downward and a soft, hollow thud follows. | |
| `force.fear.cast` | `force/fear_cast.ogg` | 2.62 s | A whispering rush: many breathy voices surge past at once on a cold gust, then thin out. | No words, no screams; dread carried on the air. |
| `force.rage.cast` | `force/rage_cast.ogg` | 1.99 s | A growl in the Force: a low, rough snarl rising under a hard push of pressure. | Fury letting go; heard by everyone near. |
| `force.rage.loop` | `force/rage_loop.ogg` | 2.40 s, loop | Blood pounding in the ears (a heavy double beat) over a low, uneasy rumble. | The caster hears it alone, under the game's own sounds; it must loop with no seam. |
| `force.rage.end` | `force/rage_end.ogg` | 2.24 s | The fury drains out: a long, falling breath and the rumble sinking away. | |
| `force.mind_trick.cast` | `force/mind_trick_cast.ogg` | 2.78 s | A soft, low tone: one calm note that bends very slightly and fades, with air moving gently round it. | Gentle persuasion; also plays in dialogue. |
| `force.mind_trick.fail` | `force/mind_trick_fail.ogg` | 0.72 s | The same calm note starts, meets something hard and stops dead in a dull, muted knock. | Clearly a refusal, yet still soft. |
| `force.focus` | `force/focus.ogg` | 0.23 s | A soft click with a faint, short tick of tone: the second bar comes forward. | Heard by the local player only, every time Focus is pressed; must never tire. |

## Data and lang

- `data/oldrepublic/skill_tree/{jedi,sith,exploration,saber}.json`: the 22 new nodes and the compacted layouts. Final
  content; the numbers come from balance.
- `data/oldrepublic/damage_type/force_drain.json` (final).
- `data/oldrepublic/tags/entity_type/{fearless,strong_willed,mind_trickable,force_resistant}.json` (final; every entry
  is `required: false`).
- `src/main/lang-parts/force.json`: power names, refusal lines, effect names and death messages.
  `src/main/lang-parts/skill.json`: node names, tooltips, the Focus key and the slot strip's lines. Both are final.
