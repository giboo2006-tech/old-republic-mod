# Skill assets (Milestone 2, skills worker)

Generator: `tools/assets/gen_textures_skill.py` (deterministic, original PIL line art). Run from the repository root.

All icons are **placeholders** (generated). Final art brief for every icon: 16×16 GUI sprite, readable at 1:1 on the dark node socket, the 18×18 HUD socket and the slot bar; a 1 px dark outline; holo line-art style matching the UI kit (`docs/assets/ui.md`); colour by tree: Universal holo blue-white (#8FD8FF/#E2F6FF), Lightsaber pale blade blue with steel hilts, Jedi pale gold (#E8D7A0), Sith deep red (#D63E42 with pale cores). No text, no real-world or franchise symbols, no copied game icons. Locked, dormant and refused states are drawn in code (tint, cracked rim, hatch), so icons need only the normal state.

| Path | What | Status | Brief |
|---|---|---|---|
| `assets/oldrepublic/textures/gui/sprites/skill/force_heal.png` | `force_heal` node icon (jedi) | placeholder generated | open palm with soft golden motes |
| `assets/oldrepublic/textures/gui/sprites/skill/heal_shared_light.png` | `heal_shared_light` node icon (jedi) | placeholder generated | three linked motes |
| `assets/oldrepublic/textures/gui/sprites/skill/heal_serene_ward.png` | `heal_serene_ward` node icon (jedi) | placeholder generated | translucent gold ward shape with a core |
| `assets/oldrepublic/textures/gui/sprites/skill/centered_mind.png` | `centered_mind` node icon (jedi) | placeholder generated | concentric rings around a still point |
| `assets/oldrepublic/textures/gui/sprites/skill/calm_focus.png` | `calm_focus` node icon (jedi) | placeholder generated | a drop above still water lines |
| `assets/oldrepublic/textures/gui/sprites/skill/guardians_resolve.png` | `guardians_resolve` node icon (jedi) | placeholder generated | two figures under a protecting arc |
| `assets/oldrepublic/textures/gui/sprites/skill/saber_lunge.png` | `saber_lunge` node icon (saber) | placeholder generated | diagonal lit blade with speed lines |
| `assets/oldrepublic/textures/gui/sprites/skill/saber_throw.png` | `saber_throw` node icon (saber) | placeholder generated | blade inside a spinning arc |
| `assets/oldrepublic/textures/gui/sprites/skill/throw_ricochet.png` | `throw_ricochet` node icon (saber) | placeholder generated | zig-zag path between two impact points |
| `assets/oldrepublic/textures/gui/sprites/skill/throw_rending_arc.png` | `throw_rending_arc` node icon (saber) | placeholder generated | heavy glowing arc with debris |
| `assets/oldrepublic/textures/gui/sprites/skill/steady_guard.png` | `steady_guard` node icon (saber) | placeholder generated | vertical blade between two guard brackets |
| `assets/oldrepublic/textures/gui/sprites/skill/riposte.png` | `riposte` node icon (saber) | placeholder generated | two crossed blades with a return curl |
| `assets/oldrepublic/textures/gui/sprites/skill/deflection_control.png` | `deflection_control` node icon (saber) | placeholder generated | blade turning a bolt toward a small aim ring |
| `assets/oldrepublic/textures/gui/sprites/skill/form_soresu.png` | `form_soresu` node icon (saber) | placeholder generated | horizontal blade inside a closed defensive circle |
| `assets/oldrepublic/textures/gui/sprites/skill/form_ataru.png` | `form_ataru` node icon (saber) | placeholder generated | blade rising out of an acrobatic spiral |
| `assets/oldrepublic/textures/gui/sprites/skill/force_lightning.png` | `force_lightning` node icon (sith) | placeholder generated | palm with two forked red-white bolts |
| `assets/oldrepublic/textures/gui/sprites/skill/lightning_chain.png` | `lightning_chain` node icon (sith) | placeholder generated | bolts linking three points |
| `assets/oldrepublic/textures/gui/sprites/skill/lightning_consuming.png` | `lightning_consuming` node icon (sith) | placeholder generated | bolt spiralling into a vortex |
| `assets/oldrepublic/textures/gui/sprites/skill/fury.png` | `fury` node icon (sith) | placeholder generated | a jagged flame |
| `assets/oldrepublic/textures/gui/sprites/skill/hungering_dark.png` | `hungering_dark` node icon (sith) | placeholder generated | a dark void ringed in red, motes drawn inward |
| `assets/oldrepublic/textures/gui/sprites/skill/dread_presence.png` | `dread_presence` node icon (sith) | placeholder generated | a hooded silhouette, marks fleeing on both sides |
| `assets/oldrepublic/textures/gui/sprites/skill/force_push.png` | `force_push` node icon (universal) | placeholder generated | open palm in profile, three bent-air arcs ahead |
| `assets/oldrepublic/textures/gui/sprites/skill/push_repulse_wave.png` | `push_repulse_wave` node icon (universal) | placeholder generated | palm with wide fanning arcs and low dust |
| `assets/oldrepublic/textures/gui/sprites/skill/push_focused_blast.png` | `push_focused_blast` node icon (universal) | placeholder generated | palm, converging lance of air into a wall with debris |
| `assets/oldrepublic/textures/gui/sprites/skill/push_deflecting_pulse.png` | `push_deflecting_pulse` node icon (universal) | placeholder generated | figure dot inside a broken shell of air, two bolts turned away |
| `assets/oldrepublic/textures/gui/sprites/skill/force_pull.png` | `force_pull` node icon (universal) | placeholder generated | palm facing left, arcs curving toward it, an arrow inward |
| `assets/oldrepublic/textures/gui/sprites/skill/pull_disarm.png` | `pull_disarm` node icon (universal) | placeholder generated | blaster pistol tumbling toward an open hand |
| `assets/oldrepublic/textures/gui/sprites/skill/pull_reel.png` | `pull_reel` node icon (universal) | placeholder generated | curling hook arrow drawing a figure toward the hand |
| `assets/oldrepublic/textures/gui/sprites/skill/force_jump.png` | `force_jump` node icon (universal) | placeholder generated | stacked chevrons rising from a ground line |
| `assets/oldrepublic/textures/gui/sprites/skill/jump_crushing_descent.png` | `jump_crushing_descent` node icon (universal) | placeholder generated | downward arrow striking ground with cracks and a shock arc |
| `assets/oldrepublic/textures/gui/sprites/skill/jump_soaring_leap.png` | `jump_soaring_leap` node icon (universal) | placeholder generated | long rising arc with trailing streaks |
| `assets/oldrepublic/textures/gui/sprites/skill/deep_reserves.png` | `deep_reserves` node icon (universal) | placeholder generated | steel vessel filled in bands |
| `assets/oldrepublic/textures/gui/sprites/skill/quickened_flow.png` | `quickened_flow` node icon (universal) | placeholder generated | three flowing wavy lines with an arrowhead |
| `assets/oldrepublic/textures/gui/sprites/skill/unbound.png` | `unbound` node icon (universal) | placeholder generated | a broken chain link with sparks |

## Sounds

Recipes in `tools/assets/sounds/skill.py` (placeholder synthesis; `python3 tools/assets/gen_sounds.py --feature skill`).

| Event | Status | Brief |
|---|---|---|
| `skill.learn` | placeholder generated | A crystal click with a rising, warm swell (about 1.2 s); bright but soft, no musical sting. Pitched up slightly in code per rank. |
| `skill.slot` | placeholder generated | Two dry mechanical clicks, a part seating in a socket (0.3 s). |
| `skill.respec` | placeholder generated | An unwinding: descending chimes over a fading low pad (1.8 s), calm rather than sad. |

## Data

`data/oldrepublic/skill_tree/{universal,saber,jedi,sith}.json`: the four trees (final content, numbers come from balance).
