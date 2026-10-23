# Foe and boss assets, Milestone 5: the five foes, Darth Ulvaine, the First Cantor, the boss music and the hologram

Everything below is original art, sound and music made by our own scripts. Nothing is copied from, or imitates, a Star
Wars game or film costume, creature, droid, sound or score. The cartel's dusters, Clan Hask's plate and crest, the
Hymnsworn's robes and verses, the scour, the stormback, Ulvaine's collar and chain veil, and the First Cantor's mask
and robes of light are all our own designs.

Textures are **placeholders** drawn by PIL, deterministically, each from its own seed, so earlier files never change.
Run from the repository root:

- `python3 tools/assets/gen_textures_foes.py --part m5_entity|m5_particle|m5_item` draws the five foes, `sand_burst`
  with its JSON, and the five spawn eggs with their JSON.
- `python3 tools/assets/gen_textures_boss.py --part m5_entity|m5_particle` draws Ulvaine (and her hologram skin), the
  resonator, the First Cantor, the choir echo and their light layers, and `spectral_wisp` with its JSON.

Every UV layout is written in both the model class's Javadoc and the script. Sounds and music are **designed
stand-ins** from `tools/assets/sounds/enemy_m5.py`, rendered by `python3 tools/assets/gen_sounds.py --m5 --feature
enemy_m5` (mono OGG, 44.1 kHz, synthesised, nothing sampled). Their file names are the Phase 0 placeholder names, so
`sounds.json` and the subtitles (lead files) do not change. The design, with each effect phase by phase, is in
`docs/design/foes_m5.md` (section 14).

Palette rules:

- The cartel wears its kajidic's colours, sand-worn: ochre (166, 124, 68), wine (96, 42, 40) and kajidic gold
  (217, 164, 65).
- Clan Hask wears clan iron (76, 82, 92), ember (194, 72, 40) and bone (214, 204, 180), the same three as W2's Hask
  fighter.
- The Hymn's people (the Hymnsworn, Ulvaine, the resonators) are dark cloth and stone with violet: night-blue, black,
  violet (98, 56, 142), crystal (112, 72, 172).
- The First Cantor and his choir are pale light over shadow: light (226, 214, 176), shadow (26, 28, 40), the choir's
  pale grey-blue (150, 160, 178).
- The creatures are natural colours: the scour is the dune's sand (198, 172, 122); the stormback is slate and basalt
  (92, 96, 104 and 52, 54, 60).
- Light is never painted into a base skin. It is a separate layer drawn full-bright: the resonator's veins, the
  Cantor's and the choir's light, and the stormback's charged veins (pale blue-white, 214, 214, 255). No texture in
  this set glows red; the cartel's red-orange muzzle glow before a shot is drawn in code (`CartelGlowLayer`).

## Textures

All paths are under `src/main/resources/assets/oldrepublic/`. All are RGBA.

### The five foes

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/entity/cartel/cartel_{gunman,bruiser,captain}.png` | The cartel enforcer's three kinds, 64x64 (`CartelModel`: the classic humanoid's upper half; jacket 0,32, sleeves 24,32, tails 40,32, pauldron 0,48, goggles 40,40) | placeholder generated | Street muscle of a desert kajidic. The **gunman**: an ochre duster over a wine shirt, a wine head-wrap with dust goggles pushed up, gloves. The **bruiser**: a wine leather vest, bare forearms, an ochre wrap, a short beard. The **captain**: a long deep-wine coat with gold trim and rivets, an ochre sash, a scar, one plated pauldron on the left shoulder, greying hair. Sand worn into every hem; a ragged split at the tails. Faces stay open. Weapons are items in hand, not texture. |
| `textures/entity/hask/hask_{rifle,blade,champion}.png` | Clan Hask's three kinds, 128x64 (`HunterModel`'s layout, so the M4 hunter's poses apply; the crest at 114,0, the shield at 88,0, its boss at 106,0) | placeholder generated | Clan-iron plate over a dark undersuit, chipped to bare metal at the edges, the M4 hunter's cut. Trim: darker iron for the rifle, ember for the blade, bone for the champion (a slightly darker plate). A crest of stiff bristles along the helmet's crown, ember (bone for the champion), darker at the roots. The champion's tall shield: clan iron with a bone rim and the clan's mark in ember on its face (a vertical bar over a chevron, our own mark), scraped to bare metal here and there, a brass boss. |
| `textures/entity/hymn/hymnsworn_{chanter,blade,caller}.png` | The Hymnsworn's three kinds, 64x64 (`HymnswornModel`: robe 0,32, sleeves 24,32, stole 40,32, veil 46,32, skirt 0,48, mantle 24,48) | placeholder generated | A cult in long robes under deep hoods, a cloth veil across the lower face, a short mantle, wide sleeves with open cuffs, a fringed hem. Down the front a stole with the Hymn's verses (one invented glyph a line, alternating sides; never real script). The **chanter**: night-blue robes, a bone-white stole with violet verses. The **blade**: road-brown robes, a rust-red stole with gold verses, leather bracers and gloves. The **caller**: storm-grey violet robes, a pale stole with lightning-blue verses. |
| `textures/entity/scour/sand_scour.png` | The sand scour, 128x32 (`ScourModel`: body 0,0, head 48,0, jaw 76,0, back plate 100,0, legs 0,20, tail 8,20 / 32,20 / 52,20, club 66,20) | placeholder generated | A low armoured burrower the colour of the dunes: a sand carapace with darker segment ridges, a pale belly showing low on the flanks, old pale scars, sand-polished plate edges. A wedge head with small dark eyes with an amber glint, a hinged jaw with pale teeth and a red-brown mouth. Six short legs with dark claw tips. |
| `textures/entity/scour/sand_scour_matriarch.png` | The matriarch, same layout (drawn 1.6 times the size) | placeholder generated | As above in a darker, redder sand (178, 132, 92) with deeper ridges, and her tail ends in a heavy club of bone knobs. |
| `textures/entity/stormback/stormback.png` | The stormback, 128x64 (`StormbackModel`: body 0,0, head 72,0, brow 72,18, horns 96,18, plates 0,34, legs 18,34, tail 38,34, jaw 46,34) | placeholder generated | A heavy, broad grazer of Tyrvann: a mottled slate hide with a dark stripe down the spine, a paler muzzle, amber eyes. A row of plates like split basalt along the spine, their split facets catching the light. A broad brow shield and two short forward horns of pitted bone. Dark hooves. |
| `textures/entity/stormback/stormback_charged.png` | Its charged veins, same layout, drawn additive over the hide while it is charged | placeholder generated | Only light: a jagged crack of pale blue-white climbing each plate's faces, a glint or two beside it, and a faint line down the spine between the plates. Everything else transparent. |

### The bosses

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/entity/ulvaine/darth_ulvaine.png` | Darth Ulvaine, 64x64 (`UlvaineModel`: robe 0,32, sleeves 24,32, veil 40,32, collar 40,38, skirt 0,48, cape 24,48). Also her echo's skin, tinted pale violet and drawn translucent by the renderer | placeholder generated | A tall Sith in black and violet. Pale skin, amber eyes with dark violet about them, black hair drawn tight to the crown with a violet sheen. A high stiff collar rising behind the head, violet-lined. A veil of fine silver chain across the lower face (a fringe of drops, open links). A fitted black robe to the ankles: a violet sash, violet piping down the closure, silver clasps, a slit at the skirt with a violet hem. Long sleeves to gloved hands; a short cape behind, violet-lined, with a frayed hem. Severe and elegant, not armoured. Her saber is drawn by the saber feature. |
| `textures/entity/ulvaine/darth_ulvaine_hologram.png` | Her figure for the `hologram` effect, same layout; the renderer tints it blue and draws it translucent and full-bright | placeholder generated | Her skin's shapes rendered as projected light: brightness follows the original (dark cloth stays dim, the violet and her face bright), in pale neutral tones so the tint decides the colour; horizontal scan lines every other row and a few stuttered bands. |
| `textures/entity/ulvaine/hymn_resonator.png` | A Hymn resonator, 64x64 (`ResonatorModel`: base 0,0, shaft 0,16, shards 16,16 / 24,16 / 32,16, crown 40,16, plinth 0,48) | placeholder generated | A crystal spire about two and a half blocks tall on a squat plinth of dark worn stone (58, 54, 62) with lighter worn edges. The crystal is a deep violet (112, 72, 172) with one darker and one lighter facet, vertical streaks and a few dark flaws. Old, grown, not machined. |
| `textures/entity/ulvaine/hymn_resonator_glow.png` | Its inner light, same layout (`ResonatorGlowLayer`, full-bright while it stands) | placeholder generated | Only the light: the crystal's veins and the plinth's inlays in pale violet (178, 140, 236). Everything else transparent. |
| `textures/entity/cantor/first_cantor.png` | The First Cantor, 128x64 (`CantorModel`: robe 0,32, sleeves 24,32, mantle 0,51, hood 64,0, mask 100,0, upper skirt 64,18, lower skirt 94,18, tatters 64,36 and 64,41) | placeholder generated | A towering spectral Sith of light and shadow. Shadow under everything; a deep hood with a pale rim of light; a smooth bone-pale mask (222, 218, 206) with narrow eye slits, a closed mouth and one gilded line from the brow. A shoulder mantle with a clasp, long bell sleeves with a ragged hem of light. Light seams down the front, a band of verse, a cord. No legs: the robe falls in two tiers with thin seams of light between dark panels, thinning at the hem into tatters, strands of light trailing off. |
| `textures/entity/cantor/first_cantor_glow.png` | The light of his robes alone, same layout (`SpectralGlowLayer`, full-bright) | placeholder generated | Only the light: the seams, the verses, the hood's rim, the clasp, the tatters' strands, and the eye slits, which glow warm (255, 236, 180). Everything else transparent. |
| `textures/entity/cantor/cantor_choir.png` | A choir echo, 128x64 (`CantorChoirModel`: robe 0,32, sleeves 24,32, hood 64,0, skirt 64,18) | placeholder generated | A spectral singer in a plain hooded robe of pale grey-blue (150, 160, 178) with darker folds, pale hands, faint pale eyes and an open, singing mouth. The robe falls to the floor; no legs. |
| `textures/entity/cantor/cantor_choir_glow.png` | Its light, same layout (`SpectralGlowLayer`) | placeholder generated | Only the light: the neckline, the hem and the eyes, near-white blue (232, 240, 255). Everything else transparent. |

### Particles

Both are 8x8, with a final JSON in `particles/`.

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/particle/sand_burst_{0..3}.png`, `particles/sand_burst.json` | `oldrepublic:sand_burst`: the scour's eruptions, tells and burrowing. Four frames; the client shades each a little | placeholder generated (JSON final) | A clump of sand thrown up and breaking apart: frame 0 a dense clump, 1 cracking, 2 and 3 grains flying apart; the clump's heart holds longest. Natural sand colour, never glowing. |
| `textures/particle/spectral_wisp_{0..2}.png`, `particles/spectral_wisp.json` | `oldrepublic:spectral_wisp`: the light shed by the Cantor and his echoes, his unravelling, and Ulvaine's echo coming apart. Three frames; near-white, tinted by the client (pale blue-white by default) | placeholder generated (JSON final) | A wisp of sung light along the diagonal: a soft mote, then a streak, then a thin trace. Soft-edged and translucent; never a ring or a star. |

### Spawn eggs

| Path | Status | Brief |
|---|---|---|
| `textures/item/cartel_enforcer_spawn_egg.png` | placeholder generated | Vanilla egg style: ochre with wine spots. |
| `textures/item/hask_warrior_spawn_egg.png` | placeholder generated | Clan iron with ember spots. |
| `textures/item/hymnsworn_spawn_egg.png` | placeholder generated | Night-blue with pale violet spots (176, 150, 214). |
| `textures/item/sand_scour_spawn_egg.png` | placeholder generated | Sand with brown spots (120, 92, 60). |
| `textures/item/stormback_spawn_egg.png` | placeholder generated | Slate with pale blue-white spots (200, 200, 246). |

## JSON, data and lang

All final unless noted.

- `items/<egg>.json` (a `minecraft:model` entry) and `models/item/<egg>.json` (parent `minecraft:item/generated`,
  `layer0`) for the five eggs; `particles/sand_burst.json` and `particles/spectral_wisp.json`.
- Loot tables in `data/oldrepublic/loot_table/`:
  - `entities/sand_scour.json`: 1 to 2 leather and 0 to 1 bone, each with looting.
  - `entities/stormback.json`: 2 to 4 leather, with looting.
  - `entities/{cartel_enforcer,hask_warrior,hymnsworn,darth_ulvaine,hymn_resonator,first_cantor,cantor_choir}.json`
    have no pools, by design. The cartel's and Clan Hask's credits are to come from W4's credit drops (a request in
    the design doc, section 16). Ulvaine's and the Cantor's rewards are paid by their fights, not dropped.
  - `gameplay/long_hymn.json`: a chest table of one `oldrepublic:hymnstone_shard` a roll, rolled by the raid's
    rewards.
- Damage types in `data/oldrepublic/damage_type/`, both with exhaustion 0.1 and scaling
  `when_caused_by_living_non_player`:
  - `storm_call.json` (message id `oldrepublic.storm_call`): called lightning, from the storm-callers' call, Ulvaine's
    rods, the Cantor's basalt verse and the stormback's roar.
  - `hymn.json` (message id `oldrepublic.hymn`): the Hymn itself, from Ulvaine's pulse and Crescendo and the Cantor's
    beam, rings and mire fog.
- Lang, in `src/main/lang-parts/`:
  - `enemy.json` (15 keys): the five foes' names, their eggs, the cartel's and Clan Hask's weapon warnings, and
    `storm_call`'s death messages.
  - `boss.json` (62 keys): the four boss entities, the Ulvaine and Cantor banners and lines, their command replies,
    and `hymn`'s death messages.

## Sounds

Tells last as long as their tells, plus a short tail. The creature and boss loops are 3 or 4 seconds, seamless and not
streamed. The client keeps every loop and fades it out on every stop path. Files are under
`assets/oldrepublic/sounds/`.

| Event | File | Final sound (as rendered) | Brief |
|---|---|---|---|
| `cartel.bark` | `cartel/bark.ogg` | A gruff, wordless shout across a street: two clipped syllables, the second rising. | The warning to a Distrusted player who comes close, and the captain's call to his squad. Wordless, in no real language. |
| `cartel.hurt` | `cartel/hurt.ogg` | A clipped grunt, a buckle and a holster rattling. | |
| `cartel.death` | `cartel/death.ogg` | A long groan, the body going down and the weapon clattering away. | |
| `cartel.shove` | `cartel/shove.ogg` | A shoulder and forearm driven into someone: a swish of cloth, a heavy thud, a hard breath out. | The bruiser's shove after his cut (the M2 wind-up sound carries the tell). |
| `hask.bark` | `hask/bark.ogg` | A clan war-call through the helmet: three hard syllables, the last held and falling. | The warning to a Distrusted player who comes close, and now and then in a fight; muffled by the helmet. |
| `hask.hurt` | `hask/hurt.ogg` | A grunt through the helmet; plate knocking on plate. | |
| `hask.death` | `hask/death.ogg` | A long groan through the helmet, an armoured body falling, the jet pack's last sputter. | |
| `hask.slam_tell` | `hask/slam_tell.ogg` | The pack's turbine whining up as the warrior crouches, armour creaking. | A tell (20 ticks); must be heard over combat. |
| `hask.slam` | `hask/slam.ogg` | A crushing boot-first impact, a gout of flame and a burst of dust. | |
| `hask.shield_block` | `hask/shield_block.ogg` | A bolt splashing off clan iron: a bright clang, a sizzle and a spray of sparks. | Plays for each bolt the champion's shield stops. |
| `hask.bash_tell` | `hask/bash_tell.ogg` | A leather strap pulled tight, iron scraping, a breath held. | A tell (15 ticks). |
| `hask.bash` | `hask/bash.ogg` | A heavy, dull clang of iron on armour and a body knocked back. | |
| `hymnsworn.chant` | `hymnsworn/chant.ogg` | Seamless 4 s: three low voices on the Hymn's open fifth, a breathy murmur rising and falling, the vowel turning from oo to ah and back. | Loop while a chanter chants (it heals allies). Also played once, lower, as the Cantor calls his Hymnsworn shades. Never words. |
| `hymnsworn.hurt` | `hymnsworn/hurt.ogg` | A hoarse, breathy cry, cut short. | |
| `hymnsworn.death` | `hymnsworn/death.ogg` | A sung note that sags and breaks off, then a robed body folding to the ground. | |
| `hymnsworn.call_tell` | `hymnsworn/call_tell.ogg` | Static thickening over a patch of ground, a buzz climbing under it, grit lifting. | A tell (40 ticks); must say where. |
| `hymnsworn.call_strike` | `hymnsworn/call_strike.ogg` | A tearing crack, a burst of sparks and a short rolling boom. | Layered on vanilla thunder. |
| `scour.hiss` | `scour/hiss.ogg` | Seamless 4 s: sand shifting over something moving below, a slow breathy hiss and a low rumble. | Loop while a scour is burrowed within earshot (lower for the matriarch); the main warning of one. |
| `scour.burst_tell` | `scour/burst_tell.ogg` | The sand boils, grains seething faster, low bubbling knocks. | A tell (15 ticks). |
| `scour.burst` | `scour/burst.ogg` | A blast of sand, a shrieking chitter and the heavy body landing. | |
| `scour.burrow` | `scour/burrow.ogg` | Claws digging, then sand pouring in and slumping over the hole. | |
| `scour.hurt` | `scour/hurt.ogg` | A short, rasping chitter. | |
| `scour.death` | `scour/death.ogg` | A long screech that sags and rattles out, a body slumping into sand. | |
| `scour.matriarch.sweep` | `scour/matriarch_sweep.ogg` | The tail coiling with a dry rattle of plates, then a heavy sweep through sand. | Starts with the sweep's tell (20 ticks). |
| `scour.matriarch.call` | `scour/matriarch_call.ogg` | A deep, warbling call that breaks into chitters. | The matriarch summoning her brood. |
| `stormback.idle` | `stormback/idle.ogg` | A low grumble from deep in the chest and a heavy breath out. | A placid grazer while unprovoked. |
| `stormback.hurt` | `stormback/hurt.ogg` | A short, angry bellow. | |
| `stormback.death` | `stormback/death.ogg` | A long bellow falling away, a crash of plates and a great body hitting the ground. | |
| `stormback.charge_tell` | `stormback/charge_tell.ogg` | A snort, the head going down, a forefoot pawing grit twice. | A tell (20 ticks). |
| `stormback.impact` | `stormback/impact.ogg` | A crushing thud of head-plates, a crack, a spit of static off the plates. | |
| `stormback.roar` | `stormback/roar.ogg` | A rising bellow, and under it a whine of static gathering on the plates until it snaps. | The roar to the sky when lightning charges it (30 ticks). |
| `stormback.charged` | `stormback/charged.ogg` | Seamless 4 s: charged plates crackling, a buzzing hum swelling and fading, a thin hiss. | Loop while charged. |
| `boss.ulvaine.hurt` | `boss/ulvaine_hurt.ogg` | A sharp, controlled breath and a short grunt, with a ring of song in it. | Composed, never a scream. |
| `boss.ulvaine.phase` | `boss/ulvaine_phase.ogg` | A low boom and a choir swelling into a new chord. | With each phase banner. |
| `boss.ulvaine.step_tell` | `boss/ulvaine_step_tell.ogg` | A violet shimmer: high tones trembling upward while the air draws in. | A tell (15 ticks). |
| `boss.ulvaine.step` | `boss/ulvaine_step.ogg` | A thump of displaced air, a sung note snapping shut, a gust outward. | Where she reappears. |
| `boss.ulvaine.lightning` | `boss/ulvaine_lightning.ogg` | Seamless 3 s: Force lightning pouring from her hands, a dense crackle over a buzzing arc. | Loop while she channels. |
| `boss.ulvaine.rod_charge` | `boss/ulvaine_rod_charge.ogg` | A rod's hum climbing, static crawling toward the patch it will strike. | A tell (30 ticks). |
| `boss.ulvaine.rod_strike` | `boss/ulvaine_rod_strike.ogg` | A crack and a sizzle into the floor. | Layered on vanilla thunder. |
| `boss.ulvaine.pulse` | `boss/ulvaine_pulse.ogg` | A rising sung note riding a travelling wall of air and grit, over 40 ticks. | The pulse is its own tell; the sound must say how far it has rolled. |
| `boss.ulvaine.crescendo` | `boss/ulvaine_crescendo.ogg` | The choir climbing over a deep drone and the resonators' glassy shimmer. | The Crescendo begins: a clear "act now". |
| `boss.ulvaine.resonator` | `boss/ulvaine_resonator.ogg` | A clear, glassy strike with a long shimmer. | Pitched up as a resonator lights, down as one breaks; also its hurt sound. |
| `boss.ulvaine.blast` | `boss/ulvaine_blast.ogg` | Every voice at once in one torn chord, a concussion and the hall shaking. | The Crescendo's blast. |
| `boss.ulvaine.yield` | `boss/ulvaine_yield.ogg` | The song breaking off; a knee and a hand on stone, robes settling, one long breath. | |
| `boss.ulvaine.echo_fade` | `boss/ulvaine_echo_fade.ogg` | Voices sliding down and thinning into shimmer and a breath of wind. | Her echo comes apart. |
| `boss.cantor.hurt` | `boss/cantor_hurt.ogg` | A choral grunt: three voices thrown out of tune for an instant. | Also the choir echoes' hurt sound. |
| `boss.cantor.phase` | `boss/cantor_phase.ogg` | A deep chord moving under a low boom, the choir following it. | With each phase banner. |
| `boss.cantor.beam_tell` | `boss/cantor_beam_tell.ogg` | Dust lifting along a line, a thin sung tone gathering behind it. | A tell (40 ticks). |
| `boss.cantor.beam` | `boss/cantor_beam.ogg` | Seamless 3 s: a bright sung fifth with a buzzing edge and rushing air. | Loop while the beam sweeps. |
| `boss.cantor.slam_tell` | `boss/cantor_slam_tell.ogg` | His fists raised, air drawn up after them, a rumble building below. | A tell (20 ticks). |
| `boss.cantor.slam` | `boss/cantor_slam.ogg` | His fists driven down into stone, the floor cracking, debris raining in a deep hall. | |
| `boss.cantor.chain` | `boss/cantor_chain.ogg` | Three arcs leaping one after another, each a little higher. | |
| `boss.cantor.roots` | `boss/cantor_roots.ogg` | Roots forcing up through stone: creaking wood, the floor cracking, a slow groan of the earth. | The Stormwood verse. |
| `boss.cantor.fog` | `boss/cantor_fog.ogg` | A low wet bubbling and a cold hiss creeping outward. | The mire verse. |
| `boss.cantor.mark` | `boss/cantor_mark.ogg` | Static and grit at the marked spots, stone ticking as it heats. | A tell (40 ticks), the basalt verse. |
| `boss.cantor.strike` | `boss/cantor_strike.ogg` | Lightning finds the marks; the stone cracks and spits. | |
| `boss.cantor.ring_tell` | `boss/cantor_ring_tell.ogg` | Stones trembling, rattling faster, a low note waking in them. | A tell (30 ticks). |
| `boss.cantor.ring` | `boss/cantor_ring.ogg` | Seamless 4 s: an inharmonic stone hum with a sung tone and dancing grit. | Loop while a ring sings. |
| `boss.cantor.silence` | `boss/cantor_silence.ogg` | The choir's chord cut off mid-breath, a hush, one soft clear chime. | A verse point silenced: a reward sound. |
| `boss.cantor.unravel` | `boss/cantor_unravel.ogg` | The choir sliding apart and down, drifting glints of light, the engine falling still. | His defeat. |
| `boss.cantor.choir` | `boss/cantor_choir.ogg` | Seamless 4 s: four pale voices on a minor chord, oo turning to ah and back. | Loop for each singing echo; once as the choir rises and, lower, as an echo falls. |
| `boss.cantor.engine` | `boss/cantor_engine.ogg` | Seamless 4 s: a slow mechanical pulse under an organ drone and a metallic resonance. | Loop in phase 3, the song-engine. |
| `story.hologram` | `story/hologram.ogg` | Seamless 3 s: a projector's soft electrical hum, a thin whine, a flutter of interference. | Loop while Ulvaine's hologram shows; quiet under the dialogue. |

## Music

Two loops of eight bars each, streamed, played by `BossMusicService`. They are synthesised from formant-filtered
voices, frame drums, plucked strings, an organ and bells (no samples, no quotations). The service also plays the
Collector's three M3 loops and Kaast's three M4 loops, unchanged.

| Event | File | As rendered | Brief |
|---|---|---|---|
| `music.boss.ulvaine` | `music/boss_ulvaine.ogg` | "The Hymn's Edge": 92 BPM, eight bars of A Phrygian (about 21 s). Frame drums in a limping three-three-two, a low plucked ostinato on the flattened second, a sung lead in a woman's range, a dark pad. | A duel: tense and personal, not epic. One loop for all three phases. |
| `music.boss.cantor` | `music/boss_cantor.ogg` | "The Long Hymn": 66 BPM, eight bars in D minor (about 29 s). A four-part choir moving by whole bars, an organ pedal, a deep drum on each bar's first beat, a bell tolling every second bar. | Slow and vast, for a raid; the choir is the Cantor's. One loop for all three phases. |
