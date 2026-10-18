# Force assets, Milestone 4: Force Speed, Force Barrier, Force Choke and the new tree nodes

Everything below is original art and sound made by our own scripts; nothing is copied from or imitates a Star Wars game
or film effect, icon or sound. The powers are drawn only with dust, grit, leaves and bent air (spec 13): no glow, no
shell, no tendrils, no ground circles, halos or outlines. Textures are **placeholders** from
`tools/assets/gen_textures_foes.py` (PIL, deterministic, seeded; run from the repository root with `--part particle` or
`--part skill`; the skill icons use the shared pen and palettes of `gen_textures_skill.py`). Sounds are **designed
stand-ins** from `tools/assets/sounds/force_m4.py`, rendered by `python3 tools/assets/gen_sounds.py --m4 --feature
force_m4`; their file names are the Phase 0 placeholder names, so `sounds.json` and the subtitles (lead files) do not
change. The design is in `docs/design/force_m4.md`.

## Textures

| Path (under `src/main/resources/assets/oldrepublic/`) | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/particle/held_dust_{0,1,2}.png`, `particles/held_dust.json` | `oldrepublic:held_dust`: the Barrier's drifting motes, 8x8 | placeholder generated (JSON final) | Three irregular specks: a clump, a flake and a pair of grains, mid grey with soft alpha edges. Tinted in code (the ground's colour below the caster, leaf green over grass), so keep them colourless. They must read as dust caught in still air, never as sparks or light. |
| `textures/gui/sprites/skill/force_speed.png` | Node icon, 16x16, universal palette | placeholder generated | A striding figure leaning forward, bent-air streaks trailing behind, a little dust at the heels. |
| `textures/gui/sprites/skill/speed_burst.png` | Node icon | placeholder generated | A short arrow of compressed air leaping forward, streaks behind it. |
| `textures/gui/sprites/skill/speed_long_road.png` | Node icon | placeholder generated | A road vanishing toward the horizon with a faint streak along it. |
| `textures/gui/sprites/skill/speed_wayfarer.png` | Node icon, exploration palette | placeholder generated | A walking staff and a bolt bending away from it. |
| `textures/gui/sprites/skill/force_barrier.png` | Node icon, Jedi palette | placeholder generated | Dust held on a sphere (never a drawn shell): a ring of specks around two raised palms. |
| `textures/gui/sprites/skill/barrier_shelter.png` | Node icon | placeholder generated | Two figures under one wide spread of held dust. |
| `textures/gui/sprites/skill/barrier_return.png` | Node icon | placeholder generated | A bolt meeting the held dust and turning back. |
| `textures/gui/sprites/skill/guardians_patience.png` | Node icon | placeholder generated | A saber held level and a slow trickle of light rising into the palm. |
| `textures/gui/sprites/skill/lift_learned.png` | Node icon for both `lift_learned_jedi` and `lift_learned_sith` | placeholder generated | A stone rising above an upturned palm, with bent air between them. |
| `textures/gui/sprites/skill/force_choke.png` | Node icon, Sith palette | placeholder generated | A clawed hand closing, and a small figure lifted off the ground beneath it. |
| `textures/gui/sprites/skill/choke_crush.png` | Node icon | placeholder generated | A fist closed hard, with cracks of pressure around it. |
| `textures/gui/sprites/skill/choke_dread.png` | Node icon | placeholder generated | A held figure in the middle, two smaller ones backing away. |
| `textures/gui/sprites/skill/cruel_focus.png` | Node icon | placeholder generated | A claw letting go, and a fork of lightning following the drop. |
| `textures/gui/sprites/skill/staff_whirl.png` | Node icon, saber palette (W5's mechanic) | placeholder generated | A double-bladed staff mid-spin, with the arc of its turn. |
| `textures/gui/sprites/skill/lens_mastery.png` | Node icon, saber palette (W5's mechanic) | placeholder generated | A cut lens catching light, set in a steel ring. |

The skill tree JSON (`data/oldrepublic/skill_tree/{universal,exploration,jedi,sith,saber}.json`) and the lang
(`src/main/lang-parts/skill.json`, `force.json`: node names, tooltips, power names, refusals and HUD lines) are final.

## Sounds

All synthesised from noise, filters, sweeps and thumps (`gen_sounds.py` helpers); nothing is sampled. Mono OGG, 44.1
kHz. Loops are seamless and are started and stopped by the client (`PowerLoop`).

| Event | File (under `assets/oldrepublic/sounds/`) | Final sound (as rendered) | Brief for the final sound designer |
|---|---|---|---|
| `force.speed.start` | `force/speed_start.ogg` | A sharp breath in through the teeth, then a whoosh that leans forward, a light kick. | Effort and lift, not magic: breath, air, one footfall. Under a second. |
| `force.speed.loop` | `force/speed_loop.ogg` | Seamless 3 s: soft wind past the ears, gusting slowly, a faint whistle. | Heard by the runner only, under the game's own sounds; must loop with no seam or pulse. |
| `force.speed.end` | `force/speed_end.ogg` | The wind falls away with an easy exhale. | A release, quieter than the start. |
| `force.speed.burst` | `force/speed_burst.ogg` | A short, fast tear of air and a push off the ground. | Punchy and short; plays with the dash. |
| `force.barrier.start` | `force/barrier_start.ogg` | A deep inhale and a pressure swell that settles into a held weight. | The air thickening around the caster. |
| `force.barrier.loop` | `force/barrier_loop.ogg` | Seamless: a low hum of held air with a slow beat. | The client lowers its pitch and makes it waver as Force Energy runs low; keep it low and steady so that reads. |
| `force.barrier.impact` | `force/barrier_impact.ogg` | Something stopped dead in the air: a muffled thud and a spit of sparks. | Must cut through a firefight: one per stopped shot. |
| `force.barrier.pulse` | `force/barrier_pulse.ogg` | The held air let go all at once: a thump and an outward rush with dust in it. | |
| `force.barrier.break` | `force/barrier_break.ogg` | The barrier giving way: a sharp crack of air, a stumble of pressure, falling grit. | Alarming; it means the caster is open. |
| `force.choke.start` | `force/choke_start.ogg` | A tight, low pressure closing in: a sinking tone and a squeezed rush. | Menace, not gore. |
| `force.choke.loop` | `force/choke_loop.ogg` | Seamless: a strained creak of held pressure over a low, uneasy drone. | For the caster; the target's own hurt sounds are muffled meanwhile. |
| `force.choke.release` | `force/choke_release.ogg` | The grip opens: pressure drains away and the body drops with a soft thud. | |
| `force.choke.crush` | `force/choke_crush.ogg` | A closing fist: a hard crunch of pressure and a heavier drop. | Heavier than the release; no bone sounds. |
