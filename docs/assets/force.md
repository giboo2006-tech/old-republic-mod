# Force feature assets (Force Push)

All sprites are **placeholders generated** by `tools/assets/gen_textures_force.py` (PIL, deterministic). They are original, near-white greyscale with alpha so the game can tint them (dust takes the map colour of the ground). Nothing in this set glows; there are no ground circles, sigils or halos.

Particle textures live in `src/main/resources/assets/oldrepublic/textures/particle/`, sprite lists in `src/main/resources/assets/oldrepublic/particles/`.

| Path | What it is | Status | Brief for a final artist |
|---|---|---|---|
| `textures/particle/air_ripple_0..3.png` (32x32) | Bent-air shell, 4 frames played over the particle's life | placeholder generated | Should read as heat-haze / refraction, not a drawn ring: a broken, wavering soft rim, transparent centre, alpha at most ~60% at the rim, colourless (very light grey). Frames: rim widens and thins out. Rendered at alpha 0.10-0.25 in game, sized 0.3-2 blocks. |
| `textures/particle/push_streak_0..2.png` (16x16) | Directional dust streak, one random frame per particle | placeholder generated | Horizontal streak pointing right (+X = direction of travel), tapered tail on the left, brightest at the head, soft vertical falloff. Light dusty grey. The game rotates it along the velocity. 3 variants of thickness/raggedness. |
| `textures/particle/push_impact_0..3.png` (32x32) | Pressure-and-dust puff where a push lands | placeholder generated | Dense noisy dust ball that opens up and thins out over 4 frames; warm light grey (tinted slightly sandy in code). No sparks, no glow. |
| `textures/particle/gather_dust_0..2.png` (8x8) | Dust mote (charging, aftermath) | placeholder generated | Small irregular soft blob, white/light grey so it can be tinted by the ground colour. 3 shape variants. |
| `textures/particle/fail_mote_0..1.png` (8x8) | Dull mote dropping from the hand on energy failure | placeholder generated | Like gather_dust but harder-edged and darker; tinted dull grey in code. |
| `particles/air_ripple.json`, `push_streak.json`, `push_impact.json`, `gather_dust.json`, `fail_mote.json` | Sprite lists for the particle types | final (data) | n/a |
| `data/oldrepublic/tags/entity_type/push_resistant.json` | Entity type tag for push resistance | final (data); world/enemy add members | n/a |

Sounds used (owned by the audio brief, not by this file): `force.push.charge`, `force.push.release` (3 variants, pitch ±6% applied in code), `force.push.impact`, `force.push.slam`, `force.fail`.

Debris uses vanilla `minecraft:block` particles of the actual ground/wall/foliage block, so it needs no assets.
