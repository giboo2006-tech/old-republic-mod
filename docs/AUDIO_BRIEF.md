# Audio brief

Every sound currently shipped is a **procedurally synthesised placeholder** produced by `tools/assets/gen_sounds.py` (numpy oscillators, filtered noise, inharmonic partials, a simple noise-burst convolution reverb). No recording was sampled or imitated. This file tells a sound designer what each event should finally be, so the placeholders can be replaced file for file without touching code.

Regenerate placeholders: `python3 tools/assets/gen_sounds.py` (add `--java` to rewrite `ModSounds.java`, `--only saber/hum` to render one file, `--feature <module>` to render one recipe module, `--m2` or `--m3` to render one milestone's events, `--keep-wav DIR` to keep the WAVs). The Milestone 1 events come first, then the Milestone 2 events; Milestone 3 has a short placeholder note.

## Delivery rules for replacements

- Files: `src/main/resources/assets/oldrepublic/sounds/<group>/<name>.ogg`, **mono** (Minecraft only spatialises mono), 44.1 kHz, Ogg Vorbis around q4 to q5. Keep the same file names, or update `sounds.json`.
- Peaks about -3 dBFS. Relative level is set by `volume` in `sounds.json` and by code; deliver one-shots normalised and the beds a little lower in perceived loudness.
- Loops must be sample-accurate seamless (no fade at either end) and 2 to 6 s. Loops longer than 4 s use `"stream": true`.
- No sub-only content: Minecraft is often played on laptop speakers and headsets. Every low-end layer needs a 150 to 600 Hz harmonic or noise partner so it reads on small speakers.
- Mood references are to tone and texture (old electrical machinery, cathedral reverb, desert wind), never to specific copyrighted recordings. Original material only.
- Subtitles: `subtitles.oldrepublic.<id>` (English strings in `src/main/lang-parts/sounds.json`).

## Concurrency and variation (applies in code)

- Randomise pitch per play in code: one-shots ±4%, `force.push.release` ±6% (spec), swings ±5%, chimes ±8%.
- Do not stack: at most one `saber.hum` per entity; at most 3 `force.push.impact` per cast (spec), extras attenuated; `saber.clash`/`saber.deflect` rate-limited to one per entity per 3 ticks; `blaster.bolt_flyby` at most once per bolt per player; `crystal.chime` at most one per cluster per 4 s.
- Avoid repeating the same variant twice in a row (pick from the remaining variants).
- Loops (`saber.hum`, `force.push.charge`, `meditation.loop`, `crystal.resonance`, `ambient.cave`, `awakening.drone`) must be stopped by code on cancel, death, disconnect, dimension change, item switch or leaving range.

## Events

Format: **id** — files — final target — current placeholder — notes.

### Lightsaber

**saber.ignite** — `saber/ignite` (1.5 s)
- Target: a sharp electrical snap (5 to 20 ms) into a rising, slightly unstable buzz that overshoots and settles onto the hum pitch within 0.3 s, with a short airy "extension" whoosh and a few sparks of crackle. Ends sitting on the hum so the loop can take over seamlessly. Character: dangerous, mechanical-electrical, confident.
- Placeholder: broadband noise snap; detuned saw/pulse buzz gliding 38 Hz → 106 Hz → 92 Hz; band-pass noise sweep 250 Hz → 3.5 kHz; decaying crackle; small room.
- Notes: code starts `saber.hum` about 6 ticks after this. Hilt-specific profiles later (emitter types) should vary the snap and overshoot.

**saber.retract** — `saber/retract` (1.0 s)
- Target: hum pitch dives and thins over 0.3 to 0.5 s with a reverse-whoosh sucking quality, a final small electrical fizz and a mechanical emitter click.
- Placeholder: buzz gliding 92 → 30 Hz with decay, downward noise sweep 2.6 kHz → 180 Hz, fizz crackle, faint click at 0.38 s.

**saber.hum** — `saber/hum` (LOOP, 4.0 s, positional, volume 0.6)
- Target: a rich, slightly unstable low electrical hum (fundamental around 80 to 100 Hz with strong 2nd to 6th harmonics so it reads on small speakers), slow beating between two close oscillators, faint high-frequency hiss and the occasional tiny crackle. It should feel alive, not static. Two layers ideally: the core hum and a "projector-like" mechanical buzz.
- Placeholder: two saws 0.7% apart (≈0.6 Hz beating) + a 27% pulse an octave up + a quiet sub, soft-saturated, low-passed at 2.4 kHz with a 380 Hz and 1.1 kHz presence; ±0.4% pitch drift; 7% amplitude wobble; faint hiss and sparse crackle. Fully periodic over 4 s, so the seam is inaudible.
- Notes: motion response is done by `saber.swing`, not by modulating this loop. Stop rules in spec.

**saber.swing** — `saber/swing1..4` (0.64 to 0.97 s)
- Target: the blade passing the listener: pitch rises as it approaches, peaks, then drops as it recedes (doppler), a loudness swell at the closest point, a brighter harmonic layer at the peak and an airy whoosh shaped to the arc. Four arcs: medium (1), slow heavy (2), fast flick (3), wide sweeping (4).
- Placeholder: hum-buzz with doppler pitch curve (+13 to +22% before the pass, -10 to -16% after) and amplitude swell, an extra bright layer at 1.5× pitch at the peak, band-passed noise whoosh whose centre follows the swell; variants differ in length, peak time, base pitch, depth, whoosh brightness and noise seed.
- Notes: code can pick variant by swing speed (3 for fast, 2/4 for heavy) once that is tracked.

**saber.clash** — `saber/clash1..3` (≈1.1 s)
- Target: violent blade-on-blade contact: a hard crack, a metallic-electrical ring with inharmonic partials, a burst of sizzling sparks and a brief distorted spike of both hums. Short, punchy, readable in a fight.
- Placeholder: noise crack; two inharmonic ring clusters (1085/1240/1420 Hz bases); spark crackle through 2.5 kHz high-pass; saturated buzz spike at 104 to 131 Hz with +70% pitch transient; short room.

**saber.deflect** — `saber/deflect1..3` (≈0.62 s)
- Target: a blaster bolt being batted away: a bright downward "zap" chirp, a short high metallic ping and a spark spray. Lighter and quicker than a clash.
- Placeholder: band-limited saw chirp (2.0 to 2.65 kHz down to 520 to 700 Hz in ~40 ms), short ring (2.45 to 3.3 kHz), crackle, small hum spike.

**saber.parry** — `saber/parry` (2.1 s)
- Target: the perfect-timing reward: a clean, bright, bell-like ring with a long shimmering tail on top of a tight contact crack. Must stand out from clash and deflect immediately.
- Placeholder: inharmonic bell at 1568 Hz (six partials), beating shimmer pair at ~3.1 kHz, crack, short hum spike, longer reverb.

**saber.guard_break** — `saber/guard_break` (1.8 s)
- Target: the guard collapsing: heavy impact, a distorted hum that stutters and dives, metallic groan, lingering crackle. Loud and unmistakable (gameplay-critical feedback).
- Placeholder: low thump 75 → 32 Hz, wide crack, low metallic ring, heavily saturated buzz diving 150 → 40 Hz gated by an accelerating stutter, long crackle tail.

**saber.hit** — `saber/hit` (0.9 s)
- Target: blade contacting a body or armour: a short sear/sizzle, a soft hum bump and a muted impact. Tasteful, not gory.
- Placeholder: band-passed noise sear at 3.8 kHz, dense crackle decaying over 0.2 s, hum bump, 110 Hz thump.

### Blaster (Crystal Poacher)

**blaster.charge** — `blaster/charge` (0.6 s, exact)
- Target: the poacher's attack tell. A rising capacitor whine (≈500 Hz → 2.5 kHz) with increasingly nervous vibrato and a ticking hiss, ending exactly when the shot fires. Must cut through combat audio at 14 blocks.
- Placeholder: exactly that: 3-harmonic whine 480 → 2600 Hz (accelerating), vibrato 8 → 38 Hz, fifth-above partner, rising hiss band 2 → 7 kHz, accelerating ticks. Loudest mid-band content in the set (RMS -13.5 dBFS).
- Notes: keep the length at 0.6 s to match the tell in code; never duck it under other mod sounds.

**blaster.fire** — `blaster/fire1..2` (≈0.7 s)
- Target: an energy shot: very fast descending chirps with a springy metallic resonance, a tight transient and a small low punch. Two variants differing in pitch and ring.
- Placeholder: two stacked exponential chirps (3.2 kHz → 180 Hz and 2.75 kHz → 150 Hz) through a feedback comb (740 / 610 Hz) plus a narrow resonance, noise transient, 120 → 50 Hz thump.

**blaster.bolt_flyby** — `blaster/bolt_flyby` (0.8 s)
- Target: a bolt passing within 3 blocks: buzzing tone with strong doppler drop and a sharp loudness peak, plus air tear.
- Placeholder: saw-buzz at 520 Hz with ±14% doppler and an inverse-square-like swell, 2nd harmonic, whoosh, sparse crackle.

**blaster.impact** — `blaster/impact` (0.67 s)
- Target: bolt hitting stone or a body: a crack, a scorching sizzle and a small dull thud; slightly different from a saber hit.
- Placeholder: crack, fast zap chirp down, sizzle crackle, 140 → 55 Hz thump, dust.

### Force

**force.push.charge** — `force/push_charge` (LOOP, 3.0 s, volume 0.8)
- Target: air being drawn in: a low rushing that seems to keep rising, a soft sub pressure and grit as dust is pulled. Code may raise pitch with charge level (0.9 → 1.15).
- Placeholder: three staggered band-passed noise sweeps (110 → 1000 Hz, Shepard-style so it rises forever but loops), low rumble, quiet 41 Hz sub. Periodic over 3 s.

**force.push.release** — `force/push_release1..3` (≈1.55 s)
- Target: a deep pressure thump with an expanding wall of air: sub punch plus a lowpass-sweeping pressure wave and a whoosh that travels away, with a second softer wave just behind. No magic sparkle.
- Placeholder: saturated 66 → 30 Hz thump, noise with a low-pass opening to ~2.5 kHz and closing, band-passed whoosh, delayed second wave, outdoor-ish reverb. Variants are baked at pitch 0.94 / 1.00 / 1.06 with different seeds.
- Notes: Repulse Wave / Focused Blast / Deflecting Pulse should get distinct release sounds later (wide and low / tight and cracking / rounded pulse).

**force.push.impact** — `force/push_impact` (0.68 s)
- Target: a body struck by the wave: dull thump, cloth/armour flap, dust puff.
- Placeholder: 95 → 45 Hz thump, 420 Hz body knock, low-passed dust burst.
- Notes: max 3 per cast.

**force.push.slam** — `force/push_slam` (2.3 s)
- Target: a target hitting a wall (Focused Blast): heavy boom, crunch, then debris and pebbles skittering for about a second.
- Placeholder: low boom, 900 Hz crunch, decaying debris crackle, 14 small pebble pings, dust tail.

**force.fail** — `force/fail` (0.72 s)
- Target: "not enough energy": hollow, dull, deflating fizz. Must not resemble any successful ability and should not be annoying when spammed.
- Placeholder: square tone 140 → 95 Hz through a narrow 310 Hz band and comb (hollow), dull low-passed fizz, small puff.

### Awakening and meditation

**awakening.whispers** — `awakening/whispers` (7.5 s)
- Target: several layered breathy voices from different distances, no intelligible words, rising and falling; big dark space. Synced with three subtitled lines in code. Later: replace with recorded whispered performances processed into unintelligibility.
- Placeholder: five unvoiced "voices": noise through three moving formant band-passes (vowel-like targets), syllable envelopes with occasional sibilants, voices offset in time and formant scale, long reverb.

**awakening.drone** — `awakening/drone` (LOOP, 6.0 s, streamed, volume 0.8)
- Target: low sustained tone with slow beating and a dark wind bed; unsettling but not horror.
- Placeholder: 55 Hz with 0.5 Hz beating partner, fifth and octave, low-passed saw for harmonics, low wind, wrapped reverb. Periodic over 6 s.

**awakening.burst** — `awakening/burst` (2.9 s)
- Target: a soft concussive bloom (the Force escaping uncontrolled): slow-attack pressure swell, gentle sub, airy shimmer; softer than a Push.
- Placeholder: low-pass noise bloom 100 → 1.2 kHz → 200 Hz, soft 50 Hz thump, airy hiss, faint inharmonic ring, long reverb.

**meditation.loop** — `meditation/loop` (LOOP, 4.0 s, volume 0.6)
- Target: calm pad with one slow breath per cycle (inhale, exhale), warm and open.
- Placeholder: suspended chord (110/165/247.5/330 Hz) with slow beating, band-passed breath noise whose colour changes between inhale and exhale, wrapped reverb. Periodic over 4 s.

**meditation.complete** — `meditation/complete` (3.8 s)
- Target: soft two-note chime, warm and resolving.
- Placeholder: two bell tones (659 and 988 Hz) 130 ms apart, soft air, reverb.

**meditation.break** — `meditation/break` (0.9 s)
- Target: a short disappointed exhale.
- Placeholder: band-passed noise exhale sweeping 1.6 kHz → 0.5 kHz, a small lip puff.

### Crystals and cave

**crystal.calling** — `crystal/calling` (3.6 s)
- Target: a soft tonal ping with a tiny airy lead-in, beautiful and inviting; played every few seconds, louder when facing the cave (volume in code).
- Placeholder: 880 Hz with a 2.6 Hz beating partner and light harmonics, airy pre-swell, long bright reverb.

**crystal.resonance** — `crystal/resonance` (LOOP, 4.0 s, volume 0.6, attenuation 16)
- Target: a glassy humming chord from large formations, slow beating, faint high shimmer; positional.
- Placeholder: inharmonic partials on 196 Hz (1, 1.5, 2.76, 4.07, 5.4) each with a slightly detuned twin, shimmer noise at 6 kHz, wrapped reverb. Periodic over 4 s.

**crystal.chime** — `crystal/chime1..4` (2.8 to 3.6 s, attenuation 16)
- Target: random soft glassy chimes near clusters; varied pitches in a consonant set so overlaps never clash.
- Placeholder: five-partial inharmonic bells at C6, E6, D6, G6 with jittered partial ratios and different decays.

**crystal.unstable_charge** — `crystal/unstable_charge` (1.2 s, exact)
- Target: the hazard tell: a rising, tremoloing glassy tone with accelerating crackle and tiny glass ticks, ending just before the discharge. Must be clearly audible and recognisable.
- Placeholder: tone 300 → 1200 Hz with tremolo 6 → 32 Hz, accelerating crackle, glass ticks that get louder, rising hiss.

**crystal.discharge** — `crystal/discharge` (1.6 s)
- Target: a sharp crystalline crack and zap, shards of glassy ringing, small boom, crackle tail.
- Placeholder: crack, saw zap 3.2 kHz → 180 Hz, 26 scattered glass pings, small low boom, crackle tail.

**crystal.form** — `crystal/form` (4.4 s)
- Target: the kyber crystal growing: a rising cascade of tiny crystalline ticks, a swelling resonant chord, and one clear final chime. Reward moment.
- Placeholder: 32 grains rising from 600 Hz to 2.4 kHz, swelling 196 Hz resonance chord, crunchy crackle, final C6 chime.

**ambient.cave** — `ambient/cave` (LOOP, 6.0 s, streamed, volume 0.7)
- Target: low cave wind and room tone with occasional echoing drips. Final version should be 20 to 40 s or a loop plus separate random drip one-shots to avoid audible repetition.
- Placeholder: slowly modulated low-passed wind, low room rumble, five drips (pitch-rising blips) with long wrapped reverb. Periodic over 6 s.

### Trial of Reflection

**trial.begin** — `trial/begin` (3.2 s)
- Target: a reversed swell that sucks in and lands on a low, dark hit as the chamber dims.
- Placeholder: reversed bell+noise reverb tail (1.6 s) into a 62 → 36 Hz thump with a low saw tone and reverb.

**trial.echo_form** — `trial/echo_form` (3.1 s)
- Target: dark smoke coalescing into a figure: swirling filtered air, a low detuned growl, a breathy hiss.
- Placeholder: swirling low-pass noise, two detuned low saws drifting 58 → 48 Hz, moving breath band.

**trial.echo_lunge** — `trial/echo_lunge` (1.1 s)
- Target: the Echo's attack tell: a quick inhale-like pre-swell, then a dark whoosh with a growl. Must be readable even under the player's own saber sounds.
- Placeholder: rising low-pass pre-swell, band-passed whoosh peaking at 0.26 s, rough 70 Hz growl.

**trial.complete** — `trial/complete` (5.5 s)
- Target: a rising, choir-like major pad that swells and resolves; awe, not triumph fanfare. Later: real choir or vocal pad.
- Placeholder: A major chord of detuned saws with vibrato, gliding up a whole tone, shaped through "ah" formants (800/1150/2900 Hz), breath and shimmer, big reverb.

### Workbench

**workbench.slot** — `workbench/slot` (0.44 s)
- Target: a precise component seat: two small metallic clicks and a tiny ring.
- Placeholder: two resonant clicks (2.2/3.7/5.1 kHz resonances + 320 Hz body), tiny ring.

**workbench.assemble** — `workbench/assemble` (3.4 s)
- Target: layered mechanical assembly clicks and a ratchet, crystal hum swelling as the crystal is seated, then a test ignition.
- Placeholder: six clicks, nine-click ratchet, 196 Hz crystal chord swell, then the `saber/ignite` synthesis cut short at 1.95 s.

### Journal

**journal.open** — `journal/open` (0.71 s)
- Target: leather-bound cover thump and soft page rustle.
- Placeholder: low cover knock and thump, grainy band-passed paper rustle.

**journal.entry** — `journal/entry` (2.5 s)
- Target: a page flip plus a soft two-note tone signalling new content.
- Placeholder: band-passed page-flip sweep, rustle, E5 then B5 soft bell tones.

### Crystal Poacher

**poacher.hurt** — `poacher/hurt1..2` (≈0.45 s)
- Target (M1): gear and armour rattle plus a short non-verbal grunt. Final: recorded actor grunts layered with webbing and plate rattles.
- Placeholder: 9 small metallic rattles, short grunt-like formant filtered breath/buzz (500 and 1000 Hz), small thump.

**poacher.death** — `poacher/death` (1.5 s)
- Target: falling grunt and exhale, gear clattering, body thud, a dropped item clink.
- Placeholder: descending formant grunt, exhale, 16 rattles, thud at 0.78 s, a final clink.

## Milestone 2 events

The 55 Milestone 2 events are listed in `tools/assets/sounds/events_m2.json` (id, owning recipe module, subtitle, loop, volume). Each feature's recipe module `tools/assets/sounds/<feature>.py` synthesises them; render one with `python3 tools/assets/gen_sounds.py --feature <feature>`, or all of them with `--m2`. The delivery rules above apply unchanged. Durations include the reverb tail.

### Concurrency and variation (Milestone 2 additions)

- Pitch is randomised ±5% per play for the saber techniques (`saber.finisher`, `saber.heavy_strike`, `saber.lunge`, `saber.throw`, `saber.npc_windup`) and for `acolyte.shove`.
- `skill.learn` is pitched up 6% per rank above 1. `settler.freed` plays at pitch 0.85 when the captives were made to pay.
- Rate limits:
  - `force.lightning.impact`: at most 2 per 10 ticks per caster.
  - `saber.lightning_block`: at most 1 per 10 ticks.
  - `saber.clash`: at most 1 per entity per 3 ticks (as in Milestone 1).
- Loops that code must stop (on cancel, death, disconnect, dimension change, item or slot change, or the end of their moment): `force.jump.gather`, `force.jump.glide`, `force.heal.loop`, `force.lightning.loop`, `saber.throw_loop`, `story.two_voices`.
- The hum of a lit saber now also plays for non-player entities (mentors, the Sith Acolyte), in the `HOSTILE` source.

### Identity and dialogue (`identity.py`)

**identity.alignment_light**: `identity/alignment_light` (3.2 s)
- Target: a warm, airy rising chime, under a second; hopeful, never triumphant.
- Placeholder: two soft bell tones (C5, then G5 0.18 s later) with a breath of high air and a long, bright reverb.
- Notes: plays for major or larger shifts only; contextual shifts of 3 or less are silent.

**identity.alignment_dark**: `identity/alignment_dark` (2.7 s)
- Target: a low, breathy, descending murmur with a little grit; uneasy, not an evil villain.
- Placeholder: two detuned low saws (98 Hz, falling 18%) under a falling band-passed breath, with a dark reverb.

**identity.rank_up**: `identity/rank_up` (4.5 s)
- Target: a short, solemn swell with a resolving final note; ceremony rather than fanfare.
- Placeholder: a rising, opening noise swell into a G major bell chord (G4, B4, D5) at 0.75 s, with a long reverb.

**dialogue.open**: `dialogue/open` (0.7 s)
- Target: a quiet holo-projector blip as the dialogue panel opens.
- Placeholder: a fast upward band-passed noise sweep with a short E6 tone.

**dialogue.select**: `dialogue/select` (0.2 s)
- Target: a short, soft click, distinct from vanilla buttons.
- Placeholder: one crisp synthesised click in a small room.

**settler.hurt**: `settler/hurt1..2` (about 0.45 s)
- Target: human pain sounds without words (two or three variants).
- Placeholder: short formant-filtered grunts at two pitches.

**settler.death**: `settler/death` (1.4 s)
- Target: a falling cry, an exhale and a body settling.
- Placeholder: a descending formant grunt, a breath and a soft thud at 0.7 s.

**settler.freed**: `settler/freed` (1.0 s)
- Target: a rope cut and a relieved exhale.
- Placeholder: a crack and a rustle of fibres, then a falling breath.

### Chapter Two story (`story.py`)

**story.two_voices**: `story/two_voices` (LOOP, 6.0 s, streamed, volume 0.8)
- Target: the Two Voices dream bed. A warm, hopeful pad and a cold, hollow drone trade places, without words; the subtitled lines sit on top.
- Placeholder: an A3/E4/A4 sine pad and a low-passed 73.5 Hz saw drone crossfading over the 6 s period, soft air and a big reverb. Seamless.
- Notes: stops when the dream ends (6 s) or the player takes damage.

**story.lesson_complete**: `story/lesson_complete` (3.4 s)
- Target: a clear two-note bell for a lesson, mission or chapter milestone; warm and brief, not a fanfare.
- Placeholder: D5, then A5 0.16 s later, as bell partials with a long, bright reverb.
- Notes: Milestone 3 reuses this event (spec 16).

**story.duel_begin**: `story/duel_begin` (2.2 s)
- Target: a low drum-like hit with a metallic ring, as a spar or trial starts.
- Placeholder: a low thump with a 980 Hz metal ring in a hall reverb.

**story.duel_end**: `story/duel_end` (2.4 s)
- Target: a ringing release as the duel ends, settling down.
- Placeholder: a 740 Hz metal ring over a falling noise sweep.

### Skills (`skill.py`)

**skill.learn**: `skill/learn` (2.2 s)
- Target: a crystal click with a rising, warm swell (about 1.2 s); bright but soft, no musical sting.
- Placeholder: a click, a G4/D5/G5 sine swell and a G6 chime.
- Notes: pitched up 6% per rank above 1.

**skill.slot**: `skill/slot` (0.2 s)
- Target: two dry mechanical clicks, a part seating in a socket.
- Placeholder: two synthesised clicks 45 ms apart.

**skill.respec**: `skill/respec` (2.8 s)
- Target: an unwinding: descending chimes over a fading low pad; calm rather than sad.
- Placeholder: ten falling chime notes over a G3 pad that sinks by 10%.

### Force powers (`force.py`)

**force.pull.cast**: `force/pull_cast1..2` (1.3 s)
- Target: an inhaled, reversed pressure swell, the mirror of the Push release. Air is drawn toward the hand and ends in a soft catch.
- Placeholder: low-passed noise that opens as it swells and is cut off at 0.35 s, with a small sub thump. The two variants differ in pitch (×1.0 and ×0.9).

**force.pull.drag**: `force/pull_drag` (0.8 s, positional at the target)
- Target: a short, gritty whoosh of something dragged over the ground.
- Placeholder: grit crackle band-passed at 1.4 kHz, plus a whoosh peaking at 0.25 s.

**force.pull.catch**: `force/pull_catch` (0.6 s)
- Target: a low thud as the target lands at the caster's feet.
- Placeholder: a 90 to 40 Hz thump with a dusty noise burst.

**force.pull.disarm**: `force/pull_disarm` (0.9 s)
- Target: a metallic wrench as the weapon is torn out of line.
- Placeholder: an inharmonic metal ring at 1.3 kHz, a crack and a short scrape.

**force.jump.gather**: `force/jump_gather` (LOOP, 2.0 s, volume 0.8)
- Target: a low, rising rush that keeps building while the leap is held: pressure gathering under the feet.
- Placeholder: two overlapping band-passed noise risers (rising 2.5 octaves from 90 Hz, staggered so the rise never ends) over a low rumble. Seamless.

**force.jump.launch**: `force/jump_launch` (1.2 s)
- Target: a pressure thump plus a whoosh at take-off.
- Placeholder: a saturated 70 to 32 Hz thump and a rising whoosh.

**force.jump.land**: `force/jump_land_soft`, `force/jump_land_heavy` (0.6 to 0.7 s)
- Target: a soft or heavy landing by fall height; dust, not bone.
- Placeholder: a thump plus a low-passed dust burst; the heavy file is lower and longer.
- Notes: code picks the file by intensity.

**force.jump.slam**: `force/jump_slam` (2.2 s)
- Target: the Crushing Descent impact: a heavy ground boom, debris and an outward rush of air.
- Placeholder: a saturated 55 to 26 Hz boom, falling debris crackle and a closing low-pass noise wave, with a big reverb.

**force.jump.glide**: `force/jump_glide` (LOOP, 2.0 s, volume 0.7)
- Target: a light, steady rush of air while gliding.
- Placeholder: noise band-passed around 900 Hz, with a slow wobble of the centre frequency. Seamless.
- Notes: stops on landing, death or dimension change.

**force.heal.start**: `force/heal_start` (2.1 s)
- Target: a warm, breathy swell.
- Placeholder: a rising breath with a soft C4/G4 pad and a bright reverb.

**force.heal.loop**: `force/heal_loop` (LOOP, 4.0 s, volume 0.7, positional)
- Target: a soft, warm tonal pad; calm, never churchy.
- Placeholder: detuned C4/G4/C5 sine pairs with a gentle swell and a faint 5 kHz shimmer. Seamless.
- Notes: stops on the end of the channel, cancel, death, disconnect or dimension change.

**force.heal.end**: `force/heal_end` (2.9 s)
- Target: a settling tone.
- Placeholder: C5 bell partials with a long reverb.

**force.heal.ward**: `force/heal_ward` (2.8 s)
- Target: a soft, glassy chime as Serene Ward settles on the body.
- Placeholder: an A6 (1760 Hz) chime.

**force.heal.interrupt**: `force/heal_interrupt` (1.1 s)
- Target: a broken breath and a dull drop.
- Placeholder: a falling breath chopped at 18 Hz, then a low thump.

**force.lightning.charge**: `force/lightning_charge` (0.9 s)
- Target: a crackling build.
- Placeholder: high-passed crackle that grows denser and a buzz rising from 120 to 180 Hz.

**force.lightning.loop**: `force/lightning_loop` (LOOP, 2.0 s, volume 0.8, positional at the caster)
- Target: a sustained electrical crackle and hiss, dangerous and alive.
- Placeholder: dense high-passed crackle, a 4.5 kHz hiss and a 150 Hz buzz with a 2 Hz wobble. Seamless.

**force.lightning.impact**: `force/lightning_impact` (0.5 s)
- Target: a sizzle at the target.
- Placeholder: a decaying high crackle and a small crack.
- Notes: at most 2 per 10 ticks per caster.

**force.lightning.release**: `force/lightning_release` (0.7 s)
- Target: a snap with a decaying crackle tail.
- Placeholder: a crack and a crackle tail that decays over about 0.15 s.

### Saber (`saber.py`)

All saber recipes reuse the Milestone 1 saber tone and swing synthesis.

**saber.finisher**: `saber/finisher` (0.9 s)
- Target: the third step of the combo: a heavier, longer swing with a downward chop.
- Placeholder: the swing synthesis, slower and deeper (84 Hz base).

**saber.heavy_windup**: `saber/heavy_windup` (1.0 s)
- Target: a rising hum swell as the blade is drawn back; a readable tell.
- Placeholder: the saber tone rising 25% in pitch and level over 0.5 s.

**saber.heavy_strike**: `saber/heavy_strike` (1.0 s)
- Target: a heavy sweep with weight at the point of impact.
- Placeholder: a slow swing (80 Hz) plus a thump at 0.2 s.

**saber.lunge**: `saber/lunge` (0.9 s)
- Target: a fast dash: the hum swelling past with a sharp whoosh.
- Placeholder: the saber tone with a 20% pitch swell and a bright whoosh peaking at 0.3 s.

**saber.form_change**: `saber/form_change` (1.3 s)
- Target: a shift in hum pitch as the stance settles.
- Placeholder: the saber tone bending up 12% and back.

**saber.throw**: `saber/throw` (0.6 s)
- Target: a swing whoosh with an ignition snap as the blade leaves the hand.
- Placeholder: a fast, bright swing plus a crack.

**saber.throw_loop**: `saber/throw_loop` (LOOP, 2.0 s, volume 0.7, positional on the thrown saber)
- Target: a spinning hum with Doppler-like pitch from its velocity.
- Placeholder: the saber tone with a 10 Hz amplitude and pitch wobble. Seamless.
- Notes: stops when the thrown saber is removed (catch, timeout, owner death, logout, level change).

**saber.throw_catch**: `saber/throw_catch` (0.9 s)
- Target: the hilt slapping into the palm, with a hum swell.
- Placeholder: a low-passed slap and a hum that settles from +10% pitch.

**saber.npc_windup**: `saber/npc_windup` (1.0 s)
- Target: the NPC attack tell: a rising hum and a bright glint that cut through combat.
- Placeholder: the saber tone rising 30%, plus a 2.6 kHz glint tone at 0.45 s.
- Notes: plays on the first swing of a string only.

**saber.lightning_block**: `saber/lightning_block` (0.8 s)
- Target: a blade absorbing lightning: crackle grinding against a strained hum.
- Placeholder: a high crackle and the hum at ×1.3 pitch.
- Notes: at most 1 per 10 ticks.

### Sith Acolyte, Poacher Captain and their weapons (`enemy.py`)

**acolyte.shove_windup**: `acolyte/shove_windup` (1.8 s)
- Target: dark-tinted, gathering pressure; a clear tell before the shove.
- Placeholder: low-passed noise opening up to about 800 Hz over a growl rising from 55 to 65 Hz.

**acolyte.shove**: `acolyte/shove` (1.3 s)
- Target: a dark pressure thump and rush, rougher than `force.push.release`.
- Placeholder: a saturated 60 to 28 Hz thump and a closing noise rush.
- Notes: the push clash plays it at pitch 0.6.

**acolyte.hurt**: `acolyte/hurt1..2` (about 0.45 s)
- Target: short, sharp pain sounds without words.
- Placeholder: formant grunts at 150 and 135 Hz.

**acolyte.death**: `acolyte/death` (1.5 s)
- Target: a falling cry and the body dropping.
- Placeholder: a grunt falling from 140 to 85 Hz, with a thud at 0.75 s.

**acolyte.yield**: `acolyte/yield` (1.2 s)
- Target: a winded exhale and a knee on the ground: surrender, not death.
- Placeholder: a falling breath, a short voiced grunt and a knee thump at 0.45 s.

**captain.heavy_windup**: `captain/heavy_windup` (1.1 s)
- Target: the Captain's shout as she raises the vibroblade; a clear tell.
- Placeholder: a rising formant grunt (170 to 210 Hz).

**captain.hurt**: `captain/hurt1..2` (about 0.47 s)
- Target: gruff pain grunts without words.
- Placeholder: formant grunts at 190 and 175 Hz.

**captain.death**: `captain/death` (1.4 s)
- Target: a falling cry and the body dropping.
- Placeholder: a grunt falling from 185 to 110 Hz, with a thud.

**captain.yield**: `captain/yield` (1.2 s)
- Target: a winded exhale and a knee on the ground.
- Placeholder: as `acolyte.yield`, at the Captain's voice pitch.

**vibroblade.swing**: `vibroblade/swing1..2` (about 0.57 s)
- Target: the high motor buzz of a vibroblade passing in a whoosh.
- Placeholder: a whoosh with a 240 Hz saw buzz band-passed at 2.2 kHz; the variants differ in pitch (×1.0 and ×0.9).

**vibroblade.hit**: `vibroblade/hit` (0.5 s)
- Target: a buzzing blade biting into armour.
- Placeholder: a body knock, a burst of buzz and a small thump.

**blaster.pistol_fire**: `blaster/pistol_fire1..2` (about 0.7 s)
- Target: a lighter, snappier shot than the poachers' rifle.
- Placeholder: the Milestone 1 blaster chirp synthesis, starting higher (3.6 and 3.3 kHz against the rifle's 3.2 and 2.75 kHz), with a faster fall and a brighter metallic comb.

## Milestone 3 events (placeholders)

The 121 new Milestone 3 events are in `tools/assets/sounds/events_m3.json`. It also sets `stream` per event: the ambience beds, the Chapter Three dream bed and the three boss music loops stream.

Until a worker writes its recipes, each event plays a labelled, generic placeholder from `gen_sounds.py` (`m3_placeholder`): a chime, impact, rise, click, voice, hum, bed or music pad, pitched per id. `python3 tools/assets/gen_sounds.py --placeholders` lists the events still on placeholder audio.

The targets are in `docs/MILESTONE_3.md` section 13.3 and in the workers' `docs/assets/*.md`. The lead adds them here at integration.

## Voice lines needed later (not synthesised)

Crystal Poacher barks, gruff mercenary voice, 3 to 4 takes each, delivered as separate events (`poacher.bark.<situation>`), subtitled, rate-limited to one bark per poacher per 6 s and one per group per 2 s:
- Spot player: "Got company!", "Someone's at the cave!", "Hold it right there."
- Recognises a Force user: "That's one of those Force freaks.", "Jedi? Out here?"
- Lightsaber seen: "Lightsaber! Keep your distance!", "Don't let it get close!"
- Bolt deflected: "It's blocking the shots!", "Spread out, flank it!"
- Reposition / retreat: "Falling back!", "Cover me!", "Moving!"
- Ally down: "We lost one!", "Man down!"
- Pushed by the Force: "Whoa!", "What hit me?"
- Low health: "I need a medpac!"
- Idle at camp: "These crystals better be worth it.", "Boss says the cave's cursed.", "Quiet tonight."
- Losing the target: "Where'd it go?", "Keep your eyes open."
- Pain grunts and death cries (to replace the placeholder hurt/death).
