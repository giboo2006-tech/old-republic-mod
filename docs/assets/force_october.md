# Force effect assets, October 2026: the energy and kinetic powers redrawn

Everything below is original art made by our own scripts. Nothing is copied from, or imitates, a Star Wars game or film
effect. The Force is drawn with bent air, dust, debris, pressure and light (`VFX_SFX_SPEC.md`, "After Milestone 5"):
nothing draws ground circles, halos, glyphs or outlines.

Most of the new look has no asset at all. The light of the energy and mind powers (bolts, flares, sparks, motes, strands
and glints) and the pressure fronts of the kinetic powers are drawn in code (`client/force/energy/`,
`client/force/kinetic/PressureFront`). What is left is nine particle types, whose sprites are **placeholders** made by
Python with the standard library only, deterministically, each sprite set from its own seeded random sequence:

- `tools/assets/gen_textures_force_kinetic.py` makes the `kinetic_*` sprites and their JSONs (F2);
- `tools/assets/gen_textures_force_energy.py` makes the `energy_*` sprites and their JSONs (F1);
- `tools/assets/gen_textures_foes.py` remakes the Barrier's `held_dust` motes as soft specks (`force_m4.md`).

Every sprite is near-white greyscale with alpha, so the game tints it: dust and grit take the colour of the ground they
came off, smoke and mist are tinted for each power. No sound was added or changed for the Force powers.

## Textures

All paths are under `src/main/resources/assets/oldrepublic/`. Each JSON in `particles/` is final.

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/particle/kinetic_dust_{0..5}.png`, `particles/kinetic_dust.json` | `oldrepublic:kinetic_dust`, 32x32: the kinetic powers' dust clouds | placeholder generated | Billowing dust: a dense, lumpy heart thinning softly through ragged lobes to nothing, lit from above. Six shapes so a cloud of them never repeats. Near-white; the code tints it the ground's colour, paler in the air. |
| `textures/particle/kinetic_pebble_{0..5}.png`, `particles/kinetic_pebble.json` | `oldrepublic:kinetic_pebble`, 16x16: grit and pebbles | placeholder generated | Small faceted chips of stone lit from the upper left, with a dark rim so a thrown or floating pebble reads against the sky. |
| `textures/particle/kinetic_streak_{0..2}.png`, `particles/kinetic_streak.json` | `oldrepublic:kinetic_streak`, 32x32: streaks of moving air | placeholder generated | A thin line of bent air along the frame: a pale core with a dark fringe either side, faint at the tail and gone at the head; whole, thin and broken. Stretched along its travel in code. |
| `textures/particle/kinetic_shard_{0..3}.png`, `particles/kinetic_shard.json` | `oldrepublic:kinetic_shard`, 16x16: Stasis's frost splinters at the break | placeholder generated | Long, thin, uneven slivers of frost with a bright edge and a darker one. |
| `textures/particle/kinetic_rime_{0..3}.png`, `particles/kinetic_rime.json` | `oldrepublic:kinetic_rime`, 16x16: Stasis's frost crusts and still frost motes | placeholder generated | A lumpy crust of hoarfrost with short needles bristling out of one side and a few glints. |
| `textures/particle/kinetic_leaf_{0..3}.png`, `particles/kinetic_leaf.json` | `oldrepublic:kinetic_leaf`, 16x16: leaves on the Barrier and in a Speed runner's footfalls, over grass | placeholder generated | A torn-off leaf: a pointed oval with a darker midrib and a lighter edge, each at its own slant. The code tints it the grass colour. |
| `textures/particle/energy_smoke_{0..3}.png`, `particles/energy_smoke.json` | `oldrepublic:energy_smoke`, 32x32: scorch smoke after lightning, Rage's smoke | placeholder generated | Soft, billowing puffs of smoke with a denser heart and a torn edge, lit a little from above. |
| `textures/particle/energy_dust_{0..3}.png`, `particles/energy_dust.json` | `oldrepublic:energy_dust`, 32x32: grainy dust kicked off the ground | placeholder generated | A clumped, speckled cloud with grains at its ragged edge. |
| `textures/particle/energy_mist_{0..3}.png`, `particles/energy_mist.json` | `oldrepublic:energy_mist`, 32x32: Fear's dark vapour, what Drain tears loose | placeholder generated | Long, thin wisps of mist that drift and curl, densest along a wavering line and faint at both ends. |

39 PNGs and 9 JSONs. The types are registered in `force.KineticParticles` and `force.EnergyParticles`.

## Unused since October 2026

The Force powers no longer spawn these types, which stay registered because the id checks name them; their sprites
and JSONs stay in the jar and need no final art: `push_streak`, `push_impact`, `pull_streak`, `jump_trail`,
`lightning_segment`, `ward_glint`, `sense_mote`, `sense_stream`, `lift_shimmer` (`force.md`), `stasis_rime`,
`drain_mote` and `valor_dust` (`powers_m5.md`).
