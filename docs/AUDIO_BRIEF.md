# Audio brief

Every sound currently shipped is a **procedurally synthesised placeholder** produced by `tools/assets/gen_sounds.py` (numpy oscillators, filtered noise, inharmonic partials, a simple noise-burst convolution reverb). No recording was sampled or imitated. This file tells a sound designer what each event should finally be, so the placeholders can be replaced file for file without touching code.

Regenerate placeholders: `python3 tools/assets/gen_sounds.py` (add `--java` to rewrite `ModSounds.java`, `--only saber/hum` to render one file, `--feature <module>` to render one recipe module, `--m2`, `--m3` or `--m4` to render one milestone's events, `--keep-wav DIR` to keep the WAVs). The Milestone 1 events come first, then the Milestone 2 events, then the Milestone 3 events, then the Milestone 4 events.

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

## Milestone 3 events

The 121 Milestone 3 events (123 files) are listed in `tools/assets/sounds/events_m3.json` (id, owning recipe module, subtitle, `loop`, `stream`, `volume`, and `variants` for `site.discovered`). Every event now has its own synthesised recipe in its feature's module `tools/assets/sounds/<feature>.py`; render one module with `python3 tools/assets/gen_sounds.py --feature <feature>`, or all of them with `--m3`. `--placeholders` lists the events still on the generic Phase 0 placeholder, and lists none. The delivery rules above apply unchanged. The targets below come from the features' asset briefs (`docs/assets/{world,quest,mission,force,holocron,enemy,equipment,boss,saber}.md`), where the placeholder recipe of each event is also described.

### Concurrency and variation (Milestone 3 additions)

- Pitch is randomised ±5% per play unless noted. `puzzle.urn_echo` is never randomised: steps 0 to 3 play D, E, F, G exactly (pitch 0.891, 1, 1.059, 1.189).
- Rate limits:
  - `trooper.republic.fire` and `trooper.imperial.fire`: at most 3 per squad per 4 ticks (server).
  - `grenade.burst`: at most 2 per 10 ticks. `grenade.fuse`: at most 2 at once.
  - `trooper.tend`: 1 per tending player. `trooper.radio` follows the bark rate limits.
  - `force.sense.reveal`: at most 6 per cast, at least 3 ticks apart.
  - `pylon.sing`: at most 4 loops at once.
- Streamed: the five ambience beds (`ambient.jedi_ruin`, `ambient.sith_tomb`, `ambient.wreck`, `ambient.outpost`, `ambient.reliquary`), the Chapter Three dream bed `story.ones_that_sing` and the three boss music loops.
- Bed volumes as played: `ambient.jedi_ruin` 0.55, `ambient.sith_tomb` 0.6, `ambient.wreck` 0.55, `ambient.outpost` 0.4, `ambient.reliquary` 0.6. Each fades in and out over 60 ticks. A Force Sense cast dips them 30% for 20 ticks.
- One-shot intervals: `ambient.ruin_chime` every 15 to 40 s, `ambient.tomb_whisper` every 8 to 20 s, `ambient.wreck_creak` every 10 to 30 s, each 4 to 18 blocks from the player.
- Loops that code must stop (on cancel, death, disconnect, dimension change, leaving the area, item or slot change, or the end of their moment):
  - the five beds (fade over 60 ticks on leaving or death; stop at once on a dimension change);
  - `story.ones_that_sing` (with the dream, at 6 s or on damage);
  - `force.lift.loop` (with the Lift pose; watchdog at the hold plus 30 ticks);
  - `holocron.hum` (when the figure folds, beyond 16 blocks, at 2,400 ticks);
  - `boss.collector.ward_hum` and `pylon.sing` (beyond 48 blocks or on a music-stop event; a pylon's song restarts when the player comes back within 48 blocks);
  - the boss music `music.collector.{archivist,singing_wall,unkept}` (streamed in the MUSIC source, 40-tick crossfade between phases, a drone at the yield, vanilla music held off while it plays; `ClientConfig.bossMusic` turns it off);
  - `saber.hum.singing` (a layer at 0.45 that follows the saber hum).
- No voice acting: the trooper barks are subtitled lines with `trooper.radio`, and Vasch, the gatekeepers and the officers speak in subtitles only. `boss.collector.hurt` and `boss.collector.spent` carry a breath and a grunt that a voice actor should replace (see "Voice lines needed later").

### World: sites, puzzles and outposts (`world.py`)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `ambient.jedi_ruin` | `ambient/jedi_ruin` | loop, stream, volume 0.6 | "Wind moves through old stone" | Wind moving through gaps in old stone, a soft whistle in one crack, leaves stirring now and then, and under it a barely heard warm resonance (an open fifth): a place that was once looked after. |
| `ambient.ruin_chime` | `ambient/ruin_chime` | volume 0.7 | "Distant chime" | A distant, dark chime knocking twice in the wind, as if heard through stone. Played every 15 to 40 s in ruins. |
| `ambient.sith_tomb` | `ambient/sith_tomb` | loop, stream, volume 0.6 | "Tomb air moans" | Hollow, low moaning air in narrow passages, a sub rumble, a faint beating drone a semitone wide, rare drips. Oppressive but not loud. |
| `ambient.tomb_whisper` | `ambient/tomb_whisper` | volume 0.7 | "Whispers in the stone" | Two breathy, wordless voices overlapping far down a passage (no real words). Played every 8 to 20 s in tombs. |
| `ambient.wreck` | `ambient/wreck` | loop, stream, volume 0.6 | "Wind over twisted metal" | Wind over torn hull plates with two thin whistles, a slow metal groan, and the tick of cooling metal. |
| `ambient.wreck_creak` | `ambient/wreck_creak` | volume 0.7 | "Hull creaks" | A hull plate creaking under its own weight: a slow stick-slip through metal resonances. Played every 10 to 30 s in wrecks. |
| `ambient.outpost` | `ambient/outpost` | loop, stream, volume 0.5 | "Outpost hums" | Mains hum and a generator thrum, a ventilation fan, two soft console beeps. |
| `puzzle.veil_reveal` | `puzzle/veil_reveal` | one-shot | "Stone hisses with dust" | A soft granular hiss of fine grains sifting from mortar, with a faint high tone as the seams catch the light. |
| `puzzle.veil_crumble` | `puzzle/veil_crumble` | one-shot | "Veiled stone crumbles" | The veiled stones fall in on themselves: a soft thud, a run of crumbling grains and small chunks, then settling dust. Louder for larger groups (the client scales the volume by group size). |
| `puzzle.counterweight_sink` | `puzzle/counterweight_sink` | one-shot | "Counterweight sinks" | A heavy block grinding down into its alcove, grit spilling, then a low settling thump. |
| `puzzle.counterweight_warn` | `puzzle/counterweight_warn` | one-shot | "Counterweight grinds" | The stone catching and slipping: a stuttering scrape with grit trickling. It plays 40 ticks before the rise, so the player knows the door will close. |
| `puzzle.counterweight_rise` | `puzzle/counterweight_rise` | one-shot | "Counterweight rises" | The block grinding back up, its pitch rising, then a soft knock as it sits flush. |
| `puzzle.step_extend` | `puzzle/step_extend` | one-shot | "Ledge slides out" | A ledge sliding out of the wall, stopping with a solid thunk. |
| `puzzle.step_retract` | `puzzle/step_retract` | one-shot | "Ledge slides back" | The ledge drawing back, quicker and lower, with a muffled stop. |
| `puzzle.urn_echo` | `puzzle/urn_echo` | one-shot | "An urn sings" | A clay urn singing one breathy, hollow note (recorded at E4) with a slow swell and a long soft tail. The client pitches it exactly, with no randomisation: steps 0 to 3 play D, E, F, G (a minor tetrachord, pitch 0.891, 1, 1.059, 1.189), so the keeper's order is heard as a short scale. |
| `puzzle.urn_wrong` | `puzzle/urn_wrong` | one-shot | "Ceiling rumbles" | A low rumble through the ceiling, then a rattle of pebbles and dust coming down. |
| `puzzle.anchor_strain` | `puzzle/anchor_strain` | one-shot | "Anchor strains" | An iron ring dragged against its stone: a tightening creak, a chain shiver, grit. |
| `puzzle.anchor_release` | `puzzle/anchor_release` | one-shot | "Anchor releases" | The ring gives: an iron clank and a bounce, a mechanism thunking open behind the stone. |
| `puzzle.rubble_crumble` | `puzzle/rubble_crumble` | one-shot | "Rubble collapses" | A jammed heap giving way: a heavy slump, stones tumbling, pebbles rolling off, dust. The client also plays it quiet and low (volume 0.45, pitch 0.7) when a Lift channel starts on the heap (the shudder). |
| `puzzle.seal_open` | `puzzle/seal_open` | one-shot | "Seal crumbles open" | A sealed face cracks, crumbles away in grains and chunks, and the air behind it breathes out. |
| `puzzle.solved` | `puzzle/solved` | one-shot | "Something resolves" | A soft resolving chord (D, G and A held, the G settling to F sharp) under a quiet bell. Played only to the solver, non-positional, no pitch variation. |
| `puzzle.pedestal_ready` | `puzzle/pedestal_ready` | one-shot | "Pedestal awakens" | The pedestal waking: stone shifts, a low hum swells inside it, then one warm bell. Pitched by kind: Jedi as recorded, Sith a minor third down, Wayfarer a tone down. |
| `vent.hiss` | `vent/hiss` | one-shot | "Vent hisses" | The tell: gas hissing up through the grate, rising over the 15-tick tell, grit rattling on the bars. It must always be audible over the tomb bed. |
| `vent.puff` | `vent/puff` | one-shot | "Vent belches miasma" | A dull whump of foul air, a breathy rush that thins out, a little grit. |
| `terminal.use` | `terminal/use` | one-shot | "Terminal chirps" | A key click, two rising console tones and a short data trill. |

### Chapter Three and missions (`story.py`)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `site.discovered` | `site/discovered1..3` | one-shot | "Site discovered" | A sense of arrival and scale, never a fanfare: a breath of wind into a soft chime. The three variants should feel like one family (ruin, tomb, outpost) without being picked by site. |
| `story.ones_that_sing` | `story/ones_that_sing` | loop, stream, volume 0.8 | "Crystals sing" | Wind across an open plain and cloth banners snapping, under it a deep, distant choir-like hum that seems to come from inside stone. About 6 s of material, seamless. No words. |
| `story.calling_holocron` | `story/calling_holocron` | one-shot | "A holocron calls" | A short crystal hum that swells and fades (about 1.5 s), warm and clear, quieter than the Calling chime. |
| `story.calling_reliquary` | `story/calling_reliquary` | one-shot | "Something deep resonates" | A low, resonant hum with a slow beat, as if a large crystal chamber is breathing far away (about 2 s). |
| `story.calling_fall` | `story/calling_fall` | one-shot | "Distant crackle" | A distant crackle of burning metal and a faint settling creak (about 1.5 s). |
| `story.collector_fallen` | `story/collector_fallen` | one-shot | "The Collector is beaten" | A heavy low chord falling away, the ring of a dying ward, then quiet (about 2.5 s). Solemn, not triumphant. |
| `story.rank_three` | `story/rank_three` | one-shot | "New rank conferred" | A rising two-part chord, brighter and longer than `story.lesson_complete` (about 2.5 s). |
| `story.cairn_touch` | `story/cairn_touch` | one-shot | "Cairn settles into dust" | Stones settling on stones with a dry dust puff and one soft high crystal note (about 1 s). |
| `story.blade_touch` | `story/blade_touch` | one-shot | "Blade touches a shoulder" | A lit blade lowered close: a soft hum that swells and a gentle crackle at the touch (about 1 s). |
| `story.tablet_break` | `story/tablet_break` | one-shot | "Tablet breaks" | A wax seal cracking, then a stone tablet snapping in two with a dust fall (about 1.5 s). |
| `mission.accept` | `mission/accept` | one-shot | "Mission accepted" | A terminal accepting an order: crisp, quiet, short. |
| `mission.update` | `mission/update` | one-shot | "Mission updated" | Progress on the action bar; must not tire after the tenth kill. |
| `mission.complete` | `mission/complete` | one-shot | "Mission complete" | Work done and paid: satisfied, smaller than a story beat. |

### Force Sense and Telekinetic Lift (`force.py`)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `force.sense.cast` | `force/sense_cast` | one-shot | "Force Sense" | Turning attention outward: a quiet breath in, then a soft resonant swell (no chord stab, no whoosh) that opens and settles in about 1.5 s. Intimate and calm; the player will hear it every few minutes, so nothing bright or tiring. Must leave room for the reveal ticks that follow. |
| `force.sense.reveal` | `force/sense_reveal` | one-shot | "Something is sensed" | Something found: a single small tick of stone or crystal with a short, faint resonance, clearly directional. Under 0.5 s, soft attack, no chime melody. Six in a row must not sound like a slot machine. |
| `force.sense.danger` | `force/sense_danger` | one-shot | "Danger sensed" | The hair on the neck rising: a short, dry prickle that rises and stops dead, readable in stereo as coming from one side or from behind. Under 0.5 s, never louder than the tell it warns about, and nothing that sounds like a UI alarm. |
| `force.lift.start` | `force/lift_start` | one-shot | "Force lift" | Weight leaving the ground: a short low swell of pressure, a lurch as the target comes free, a sprinkle of grit. About 1 s; heavy rather than magical. |
| `force.lift.loop` | `force/lift_loop` | loop, volume 0.7 | "Something hangs in the air" | Held weight under strain: a low, slightly unsteady hum with a trickle of grit and dust. Must loop seamlessly (no click at the seam, no audible phrase), sit under combat and dialogue, and never pulse like a danger tell. 3 to 6 s loop. |
| `force.lift.slam` | `force/lift_slam` | one-shot | "Lifted target slams down" | A body driven into the ground: a heavy, short thud with a crunch of stone or earth and a breath of dust settling. Punchy but under 1.5 s; no metal, no explosion. |
| `force.lift.hurl` | `force/lift_hurl` | one-shot | "Lifted target hurled" | A heavy body flung: a rush of displaced air that passes quickly, with tumbling grit. Under 1 s, weighty, with no cartoon whoosh. |

### Holocrons (`holocron.py`)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `holocron.rise` | `holocron/rise` | one-shot | "Holocron rises" | Weight leaving stone: a short grinding scrape as the holocron frees itself, then a low, almost subliminal tone that rises with it. 1.5 to 2 s. Soft attack; must not mask a trooper or sentinel tell. |
| `holocron.open` | `holocron/open` | one-shot | "Holocron opens" | A small, precise mechanism releasing (three tiny catches, like a puzzle box), and the holocron's tone blooming out of the seams. Intimate, close-miked; 2 s tail. |
| `holocron.gatekeeper_form` | `holocron/gatekeeper_form` | one-shot | "Gatekeeper appears" | A projected presence arriving: light gathering into a shape. A soft rising shimmer that resolves into a warm, sustained chord, with no voice and no whoosh. About 2 s. |
| `holocron.gatekeeper_fade` | `holocron/gatekeeper_fade` | one-shot | "Gatekeeper fades" | The reverse of the form: the chord descends and thins, ending in a quiet exhale of shimmer. 1.5 to 2 s; reads as "gone", not "failed". |
| `holocron.hum` | `holocron/hum` | loop, volume 0.6 | "Holocron hums" | The holocron's resting voice under dialogue: very quiet, slowly beating crystal chord with a breath of air above it. Must loop seamlessly (no click at the seam, no audible phrase), sit under speech and subtitles, and never pulse in a way that could be mistaken for a danger tell. 4 to 8 s loop. |
| `holocron.seal` | `holocron/seal` | one-shot | "Holocron sealed" | Closing, not destroying: the seams draw shut with a soft stone settle, the tone cut off under a long, calm dust exhale. Grave and quiet. |
| `holocron.break` | `holocron/break` | one-shot | "Holocron breaks" | Pale stone split along its seams: a sharp crack, a short crunch of shell, then the trapped light escaping as a thin tone that gutters out. Violent but brief (under 1.5 s); no glass-shatter cliché. |
| `holocron.listen` | `holocron/listen` | one-shot | "Holocron shares its lesson" | Knowledge flowing into the listener: a soft descending cascade of tiny crystal chimes that ends in a quiet inhale, as if the player took it in. 1.5 to 2 s. |
| `holocron.echo` | `holocron/echo` | one-shot | "An echo lingers" | Distance and memory: the holocron chord heard as if from the far end of a great hall, then a single small tick of a crystal shard settling on stone. Quiet; about 2.5 s with a long reverb tail. |
| `holocron.archive_return` | `holocron/archive_return` | one-shot | "Holocrons packed away" | The three Archive holocrons dimmed and packed for travel: their tones fading one by one, a rustle of cloth, the click of a travelling case's clasp. Respectful, small. |
| `holocron.archive_keep` | `holocron/archive_keep` | one-shot | "Holocrons kept" | The seams warming and their light drifting into the keeper: the chord swelling warmly from three sources into one, then resting. A sense of weight accepted; no triumph. |
| `holocron.unmake` | `holocron/unmake` | one-shot | "Holocrons unmade" | Three holocrons unmade at once: the seams flare (a brief bright swell) and drain away, the shells crack and slump in stone, and a low chord resolves beneath it all. Heaviest of the set; up to 3 s. |

### Troopers and the frag grenade (`enemy.py`)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `trooper.republic.charge` | `trooper/republic_charge` | one-shot | "Republic rifle charges" | The Republic rifle's charge tell, entity-bound and stopped the moment the shot fires: a clean, bright rising whine over 12 ticks, recognisably "about to fire" and distinct from the poacher's dirtier charge. |
| `trooper.republic.fire` | `trooper/republic_fire` | one-shot | "Republic rifle fires" | One shot of a three-round burst: crisp and light, a sharp energy crack with a small mechanical action, short tail. Must stay pleasant at three per squad per 4 ticks. |
| `trooper.imperial.charge` | `trooper/imperial_charge` | one-shot | "Imperial rifle charges" | The Imperial rifle gathering one heavy shot: lower, buzzier and more menacing than the Republic whine, same length. |
| `trooper.imperial.fire` | `trooper/imperial_fire` | one-shot | "Imperial rifle fires" | A single heavy shot: lower and fuller than the Republic bolt, a thump you feel, a longer echoing tail. |
| `trooper.vent` | `trooper/vent` | one-shot | "Rifle vents heat" | Hot gas released from the rifle's heat sink after three bursts: a pressurised hiss that falls off over 2 s, then small metallic ticks of cooling. |
| `trooper.kolto` | `trooper/kolto` | one-shot | "Kolto hisses" | A medic's kolto injector: a click as the seal breaks, a soft wet spray hiss with a faint, reassuring rising tone; clinical, not magical. |
| `trooper.tend` | `trooper/tend` | one-shot | "Wounds tended" | Hands working over a downed trooper: a field dressing torn and pressed, a strap cinched, a short kolto spritz. Played every 20 ticks while tending (at most one per tending player). |
| `trooper.stand` | `trooper/stand` | one-shot | "Trooper rises" | A trooper pushing up from the ground: a muffled effort breath through the helmet, plates and webbing settling, one firm boot step. |
| `trooper.rally` | `trooper/rally` | one-shot | "Trooper rallies" | A sergeant's rally: a gloved fist on a chest plate, then a short wordless shout through the helmet filter (no intelligible words, so no language is implied). |
| `trooper.flare` | `trooper/flare` | one-shot | "Flare launched" | A signal flare: a hollow launcher pop, then a bright fizzing burn receding upward; should carry over a fight. |
| `trooper.radio` | `trooper/radio` | one-shot | "Radio clicks" | The helmet comm keying up under a bark line: a key click, a short squelch of narrow-band static, a click off. Subtle; plays with each bark the player is near enough to read. |
| `trooper.hurt` | `trooper/hurt` | one-shot | "Trooper hurts" | Pain through a helmet filter: muffled, short, human, with armour rattle. A few variants would help in a final pass (the id has one file now; more is a request to the lead). |
| `trooper.death` | `trooper/death` | one-shot | "Trooper dies" | A last breath through the helmet, the weapon and plates hitting the ground, a final settle. Not gory. |
| `grenade.arm` | `grenade/arm` | one-shot | "Grenade armed" | The grenadier's tell: a pin pulled and a lever flipped (two clicks and a small ping), then a short electronic arming beep. Must be readable through gunfire. |
| `grenade.fuse` | `grenade/fuse` | one-shot | "Grenade ticks" | The landed grenade's fuse, entity-bound: a rising tick that speeds up over 40 ticks to the burst, so the player can hear where it lies. At most two play at once. |
| `grenade.burst` | `grenade/burst` | one-shot | "Grenade explodes" | A frag burst at close range: a hard crack and a boom, fragments whining off, dirt and grit pattering down. No terrain damage, so no collapsing-rubble sounds. At most two per 10 ticks. |

### Era armour (`equipment.py`)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `armour.equip_trooper` | `armour/equip_trooper` | one-shot | "Armour clasps" | Hard composite plates seated against a padded undersuit: a muted knock (not a ringing metal clang), two crisp magnetic or latch clasps, a webbing strap pulled tight, a light settle of plates. Short (under 0.8 s), dry, close-miked. Played by vanilla as the material's equip sound. |
| `armour.equip_robe` | `armour/equip_robe` | one-shot | "Robe rustles" | Heavy woven cloth drawn over the shoulders: one full swish, the folds settling, and a small leather-and-metal buckle closing at the end. Warm and soft, no rustling plastic. |
| `armour.set_bonus` | `armour/set_bonus` | one-shot | "Armour set settles" | The "set complete" sting heard only by the wearer: a final clasp or cloth settle, then a short warm rising interval (a fifth) in a plucked-metal or soft bell timbre, decaying cleanly. Not a fanfare and not magical chimes; it should sit under combat sounds. The client pitches it per set: 1.0 Republic, 0.9 Imperial, 1.06 Jedi, 0.88 Sith. |

### Ruin Sentinels, Orren Vasch and the Reliquary (`boss.py`)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `sentinel.wake` | `sentinel/wake` | one-shot | "Sentinel wakes" | Over the 20-tick wake: heavy stone grinding against stone, dust trickling, then a low crystal hum rising as the lumen catches. Massive, slow, not robotic. Also played soft and high for the Watcher's bow. |
| `sentinel.slam_tell` | `sentinel/slam_tell` | one-shot | "Sentinel raises its arms" | The 20-tick tell must be readable with eyes closed: arms of stone and bronze lifting, strain rising to a peak just before the blow. |
| `sentinel.slam` | `sentinel/slam` | one-shot | "Sentinel slams" | Two stone fists driven into the floor: a deep, short impact with a crunch of floor and a spray of chips; a sub-bass thump that does not boom for long. |
| `sentinel.core_exposed` | `sentinel/core_exposed` | one-shot | "Sentinel core opens" | The plates part and the core's hum is suddenly exposed and bright: an audible "now" for the 40-tick weak window. |
| `sentinel.lance_charge` | `sentinel/lance_charge` | one-shot | "Sentinel lance charges" | The 24-tick tell: motes drawn in, a rising crystal whine that peaks where the bolt fires; the parry cue, so its end must be sharp and predictable. |
| `sentinel.lance_fire` | `sentinel/lance_fire` | one-shot | "Sentinel lance fires" | A heavy amber-white bolt: weightier than a blaster, with a crystalline crack; must not be confused with poacher blasters. |
| `sentinel.hurt` | `sentinel/hurt` | one-shot | "Sentinel cracks" | Stone and bronze taking a hit; no voice. |
| `sentinel.death` | `sentinel/death` | one-shot | "Sentinel crumbles" | The lumen gutters out (a falling hum), the plates slump and break into rubble and dust. |
| `boss.collector.hurt` | `boss/collector_hurt` | one-shot | "Collector hurts" | A tired older man's pained exhale or grunt (voice actor), with cloth movement; several takes. |
| `boss.collector.hurl_lift` | `boss/collector_hurl_lift` | one-shot | "Stone tears loose" | The Hurl tell (24 ticks): a chunk tearing from a debris pile, grit pouring off it, a low Force hum swelling. |
| `boss.collector.hurl_throw` | `boss/collector_hurl_throw` | one-shot | "Stone hurled" | A heavy stone flung by the Force: pressure push and a rough, tumbling whoosh. |
| `boss.collector.hurl_rip` | `boss/collector_hurl_rip` | one-shot | "Stone crashes down" | Pulled out of his hold: a wrench of the Force and the chunk crashing to the floor. |
| `boss.collector.hurl_return` | `boss/collector_hurl_return` | one-shot | "Stone flung back" | A Push sending the chunk back at him: the player's Push signature plus a stone streak. |
| `boss.collector.palm_tell` | `boss/collector_palm_tell` | one-shot | "Collector draws back his palm" | The 16-tick Palm tell: an inward draw that stops dead just before the release (the Push-clash cue). |
| `boss.collector.lash_tell` | `boss/collector_lash_tell` | one-shot | "Floor dust lifts" | The blade tip dragging and a seam of dust lifting in a line; the hum rises over 20 ticks. |
| `boss.collector.lash_wave` | `boss/collector_lash_wave` | one-shot | "Force lash rolls" | A 1-block ridge of dust and grit travelling along the floor at 0.8 blocks per tick: positional, passing by. |
| `boss.collector.surge_gather` | `boss/collector_surge_gather` | one-shot | "Air rushes toward the Collector" | The Surge tell: the air bends toward him, the stolen holocron's dissonant undertone underneath. Long enough to reach cover. |
| `boss.collector.surge_release` | `boss/collector_surge_release` | one-shot | "Force surge" | The Surge breaking outward: the largest sound of the fight, a wall of air, dust and tumbling stone. |
| `boss.collector.surge_clash` | `boss/collector_surge_clash` | one-shot | "Surge breaks against a blade" | The wall breaking against a guard or a Push: saber-on-pressure clash and air tearing past the player. |
| `boss.collector.spent` | `boss/collector_spent` | one-shot | "Collector falters" | He sags after the Surge: a ragged breath (voice), the holocron's light guttering. The punish window. |
| `boss.collector.grip` | `boss/collector_grip` | one-shot | "Force grip" | The Archive Grip closing. Not a choking voice: pressure, creak, pebbles. |
| `boss.collector.grip_hold` | `boss/collector_grip_hold` | one-shot | "Grip tightens" | Held in the Grip: a straining pressure, layered under the player's own breathing if any. |
| `boss.collector.grip_break` | `boss/collector_grip_break` | one-shot | "Grip breaks" | The Grip broken: release and a burst of grit. |
| `boss.collector.lunge` | `boss/collector_lunge` | one-shot | "Collector lunges" | The Unkept lunge: a dust kick and a fast blade streak. |
| `boss.collector.ward_hum` | `boss/collector_ward_hum` | loop, volume 0.7 | "Ward hums" | The Resonance Ward around him while pylons sing: a shimmering crystal resonance that works at any volume (it may be played softer as pylons fall). Must sit under dialogue and combat. |
| `boss.collector.unsung` | `boss/collector_unsung` | one-shot | "Ward collapses" | The last pylon falls: the ward collapses, the currents snap back, a falling tone and ceiling dust. A payoff moment. |
| `boss.collector.transition` | `boss/collector_transition` | one-shot | "The Reliquary shakes" | The phase sting: the Reliquary shakes and the crystals rise in a choir. |
| `boss.collector.yield` | `boss/collector_yield` | one-shot | "Collector yields" | He yields: blade retract, crystals dim, quiet resolution. |
| `boss.collector.leash` | `boss/collector_leash` | one-shot | "Collector vanishes in smoke" | He resets to the dais in a puff of smoke. |
| `boss.collector.echo_form` | `boss/collector_echo_form` | one-shot | "Echo gathers" | The rematch echo gathering from smoke over 60 ticks: cold, ghostly, not horror. |
| `boss.collector.echo_fade` | `boss/collector_echo_fade` | one-shot | "Echo dissolves" | The echo breaking into slow smoke that drains into the floor. |
| `boss.collector.binders` | `boss/collector_binders` | one-shot | "Binders lock" | Custody ending: Republic or Imperial binders locking. |
| `boss.collector.execute` | `boss/collector_execute` | one-shot | "A single stroke" | The execution ending: restrained, not gory; the last thing heard is his amber blade's hum dying. |
| `boss.collector.walk_out` | `boss/collector_walk_out` | one-shot | "Footsteps recede" | The release ending: footsteps receding up the spiral. |
| `pylon.sing` | `pylon/sing` | loop, volume 0.6 | "Pylon sings" | Each singing pylon, positional: a sustained, almost vocal crystal tone. Up to four play at once with the ward hum, so each should be narrow in pitch and still sound good pitched a little up or down. |
| `pylon.crack` | `pylon/crack` | one-shot | "Pylon cracks" | A hit on the crystal: a chip and a ring. Frequent: keep it short and not tiring. |
| `pylon.silence` | `pylon/silence` | one-shot | "Pylon falls silent" | A pylon silenced: the crystal cracks dark and its song falls away. |
| `pylon.regrow` | `pylon/regrow` | one-shot | "Crystal regrows" | The 60-tick re-sing tell: crystal growing back with a rising whine; a warning to hit it. |
| `pylon.shatter` | `pylon/shatter` | one-shot | "Pylon shatters" | A pylon shattered into him (a Pull rip or Unsung): bright glass crash and ringing shards. |
| `music.collector.archivist` | `music/collector_archivist` | loop, stream | "Battle music: The Archivist" | Phase 1 music (`SoundSource.MUSIC`): measured and melancholy, a scholar's duel. The three loops crossfade over 40 ticks, so they should share key relationships and tempo feel; 1 to 2 minutes each in the final. |
| `music.collector.singing_wall` | `music/collector_singing_wall` | loop, stream | "Battle music: The Singing Wall" | Phase 2: the pylons' song becomes the music's ostinato; urgency rising. |
| `music.collector.unkept` | `music/collector_unkept` | loop, stream | "Battle music: Unkept" | Phase 3: dark and driving, the stolen holocron's dissonance in the harmony. |
| `ambient.reliquary` | `ambient/reliquary` | loop, stream, volume 0.6 | "Reliquary hums" | The Reliquary's bed outside the fight: cold, sacred, a little wrong. Quiet enough for dialogue. |
| `hoard.open` | `hoard/open` | one-shot | "Vault seal crumbles" | The vault seal crumbling and motes rising from three holocrons: a release, then a resolving shimmer. |

### Saber (`saber.py`)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `saber.hum.singing` | `saber/hum_singing` | loop, volume 0.6 | "Singing crystal hums" | A layer over the normal hum of a saber built around the singing crystal, sitting above the hum (below 400 Hz) and never masking it: a high, clear crystal tone, like a wet finger circling a glass, a fundamental near E6 with a fifth above and a faint octave, each beating slowly against a partner a hair off pitch, so it shimmers rather than whistles. No melody and no attack; seamless; nothing sharp above 8 kHz. The code plays it at 0.45. |

## Milestone 4 events

The 139 Milestone 4 events are listed in `tools/assets/sounds/events_m4.json` (id, owning recipe module, subtitle, `loop`, `stream`, `volume`, and `variants` for `blaster.repeater.fire`). This section covers all 139 events (140 files) of the six areas: W1 worlds (`world_m4.py`, 19), W2 Chapter Four and vendors (`story_m4.py`, 9), W3 ships and travel (`ship.py`, 16), W4 gear (`gear.py`, 14 events, 15 files), W5 crafting and saber (`crafting.py`, 14, and `saber.py`, 4) and W6 Force powers and foes (`force_m4.py`, 13, and `enemy_m4.py`, 50, three of which are Kaast's music). Each event has its own synthesised recipe in its module, except `workbench.tune`; render one module with `python3 tools/assets/gen_sounds.py --m4 --feature <module>`, or every Milestone 4 event with `--m4`. `--placeholders` lists the events still on the generic Phase 0 placeholder; of the Milestone 4 events it lists only `workbench.tune`. The delivery rules above apply unchanged, and every file keeps its Phase 0 name, so `sounds.json` does not change when a file is replaced. The targets below come from the features' asset briefs (`docs/assets/{planets,chapter_four,vendor,travel,gear,crafting,saber,force_m4,foes}.md`), where the placeholder recipe of each event is also described; the lengths are the current placeholder files.

### Concurrency, reuse and variation (Milestone 4 additions)

- Pitch is randomised ±5% per play unless noted. Exact pitches, never randomised: `hollow.chime` plays 0.794, 1 and 1.26 for the Hollow's three tones and 0.5 for a wrong stone; `ship.fit_part` is 0.8 for the power core, 1.3 for the nav computer, 0.75 for a fuel cell and 1.1 for an upgrade; `workbench.lens_fit` is 1, 1.06 and 1.12 by lens; `scanner.ping` rises from 1.1 to 1.6 with the number of marks; `heartstone.wave` is lower and louder each wave.
- Exact pitches from W2 and W6: `seeker.chirp` plays twice, 7 ticks apart, at 1.05 and then 1.25 as the seeker droid leaves; `boss.kaast.mine_beep` beeps every 10 ticks while a mine arms, rising from 1 to 1.5, and then every 40 ticks at 1.6 and a lower volume once the mine is live; `droid.shield` plays lower when the plate is raised than when a bolt strikes it; the caster's `force.barrier.loop` drops in pitch and wavers as Force Energy runs low.
- Rate limits: `jetpack.empty` at most once every 10 ticks; `rift.whisper` restarts every 2.5 s while the rift crystal is held; `scanner.ping` plays when a survey slice finds new marks; `force.barrier.impact` plays once per stopped shot; `mandalorian.bark` plays on about one engagement in three; `credits.pickup` plays only when a vendor pays the player and at the exchange.
- Streamed: the six biome beds (`ambient.selvaren.*`, `ambient.varkhast.*`), the two hub beds (`ambient.greenwatch`, `ambient.proving`), the two planet music loops and Kaast's three music loops (`music.kaast.*`).
- Kaast's music plays in `SoundSource.MUSIC` (the vanilla music slider) and is not positioned. `KaastMusic` crossfades the three loops over 40 ticks as the phases change, holds vanilla music off while a loop plays or fades, and follows the M3 `ClientConfig.bossMusic` toggle.
- Beds as played: the biome beds crossfade over 4 s as the player crosses biomes, duck to 0.35 under cover and to 0.4 under a site's own bed, and dip for a Force Sense cast; the hub beds play through the M3 site ambience. All of them follow the `planetAmbience` client option. The planet music is the dimension's `background_music` and follows the vanilla music slider.
- Reused sounds (no new events): the player's pistol and rifle fire `blaster.pistol_fire` and `blaster.fire`; the rifle's charge whine is `blaster.charge`; the charged shot's "loaded" latch reuses `workbench.slot` (a candidate for its own `blaster.charge_ready` event, a short mechanical latch heard only by the shooter); the vibro guard clang is `vibroblade.hit`, a notch `saber.clash` played low, a guard break `saber.guard_break`; the electrostaff sweep is `vibroblade.swing` played low; the second blade's retract is `saber.retract` pitched up; the M4 sets' flourish is `armour.set_bonus`; the off-world Calling is `crystal.calling` pitched up. From W2 and W6: the seeker droid's own idle and hurt sounds are `seeker.fly` and `seeker.chirp`; Kaast's shoulder-charge tell is `mandalorian.jet_tell` played low and his axe tell `boss.kaast.combo` played low; his axe blocking a saber is `saber.clash` lowered; a dying droid's crackle is `droid.hurt` played low.
- Loops that code must stop (on cancel, death, disconnect, dimension change, leaving the area, item or slot change, or the end of their moment):
  - `waystone.corrupt` (cleansed for this player, beyond 16 blocks with a 3 s lapse, at once beyond 20 blocks);
  - the biome and hub beds (released on death, stopped on a dimension change, killed on disconnect);
  - `ship.idle` (engines off, hyperspace, the ship gone), `ship.damaged` (hull back above 20, hyperspace) and `ship.hyperspace` (the overlay's end, off the ship for 100 ticks, a 600-tick safety);
  - `bench.weld` (done, Masterwork or stop; watchdog at the craft's ticks plus 40, beyond 24 blocks; the fusion cutter's use lapses 10 ticks after its last spark);
  - the held sounds `blaster.charge` and `electrostaff.charge` (dropped, fired, spent or swapped away);
  - `force.speed.loop`, `force.barrier.loop` and `force.choke.loop` (`PowerLoop`, one per power and player: the power's end event, a watchdog of the length the server sent plus 20 ticks, the player's death or removal, a level change, disconnect);
  - `mandalorian.flamer` (the hunter's synced flaming flag: the flame ends, the hunter is staggered or dies, or it leaves this client);
  - `music.kaast.*` (the yield, a victory, a reset, leaving the ring, death, a level change, disconnect, the client toggle, and a watchdog once no Kaast has been within 64 blocks for 200 ticks).
- No voice acting is needed in Milestone 4. The Proving's loudspeaker, the rift's whispers and the hunters' and Kaast's hurt, death and bark sounds are wordless by design (a voice through a sealed helmet). The dialogue of Chapter Four, the vendors and the hub people is subtitles only.

### Worlds: waystones, the Heartstone, expeditions, ambience and music (`world_m4.py`, W1)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `waystone.corrupt` | `waystone/corrupt` | loop, volume 0.6, 4.0 s | "Corrupted waystone throbs" | Something wrong living inside old stone: a slow, sick pulse in time with the veins (every 2 s), low and close, unsettling but not a monster. Seamless. Plays for each player within 16 blocks the stone is not cleansed for. |
| `waystone.cleanse` | `waystone/cleanse` | one-shot, 4.3 s | "Waystone cleansed" | The darkness draining out of the stone as the stone's own clear note comes back up through it; relief. |
| `waystone.hum` | `waystone/hum` | one-shot, 5.5 s | "Waystone hums" | The clean stone's own voice: a soft, low, bell-like hum, felt more than heard; heard now and then near a clean stone, more at night. |
| `heartstone.wave` | `heartstone/wave` | one-shot, 3.9 s | "Corruption wells up" | Corruption welling up out of the ground around the great stone; three waves of it, each played lower and louder. |
| `heartstone.cleansed` | `heartstone/cleansed` | one-shot, 7.2 s | "Heartstone cleansed" | A long exhale after a fight: wide, warm, a held breath let go. |
| `hollow.chime` | `hollow/chime` | one-shot, 4.6 s | "Hollow crystal chimes" | One crystal of the Singing Hollow ringing. The client pitches it to three tones a third apart (0.794, 1, 1.26) and to 0.5 for a wrong stone, so keep it a clean single pitch with a long ring. |
| `hollow.answer` | `hollow/answer` | one-shot, 6.6 s | "A crystal answers" | Every crystal in the grotto answering at once, settling into a chord that hangs in the cave. |
| `rift.whisper` | `rift/whisper` | one-shot, 4.3 s | "The rift whispers" | Whispers from the black glass while the crystal is held: wordless, breathy, many voices; no intelligible words in any language. Restarted every 2.5 s, louder as the bleed deepens. |
| `rift.bleed` | `rift/bleed` | one-shot, 4.5 s | "Crystal bleeds red" | The crystal turning: a crack of glass, a falling groan and a dark, ringing bloom. |
| `ambient.selvaren.highwood` | `ambient/selvaren_highwood` | loop, stream, volume 0.6, 12.0 s | "Wind in the high trees" | Cool highland forest: wind high in tall trees, leaves, a few distant (invented) birds. Seamless; mixed low as a bed. |
| `ambient.selvaren.meadow` | `ambient/selvaren_meadow` | loop, stream, volume 0.6, 12.0 s | "Meadow breeze" | Open heather uplands under a wide sky; breeze and grass. |
| `ambient.selvaren.tarn` | `ambient/selvaren_tarn` | loop, stream, volume 0.6, 12.0 s | "Water laps at the shore" | A misty lake in a stone cup: lapping water, reeds, one long low call across the water. |
| `ambient.varkhast.redrock` | `ambient/varkhast_redrock` | loop, stream, volume 0.6, 12.0 s | "Dry wind over red rock" | Dry wind over red rock and through hoodoos; grit. |
| `ambient.varkhast.ashfall` | `ambient/varkhast_ashfall` | loop, stream, volume 0.6, 12.0 s | "Ash hisses as it falls" | Ash falling on a grey plain: a soft steady hiss and a low wind. |
| `ambient.varkhast.tomb_valley` | `ambient/varkhast_tomb_valley` | loop, stream, volume 0.6, 12.0 s | "Wind moans through the valley" | Wind moaning along carved cliffs and far thunder that brings no rain. |
| `ambient.greenwatch` | `ambient/greenwatch` | loop, stream, volume 0.5, 12.0 s | "Enclave stirs" | The enclave quietly at work: tools on wood, a chime, a clearing in a forest. |
| `ambient.proving` | `ambient/proving` | loop, stream, volume 0.5, 12.0 s | "Proving grounds stir" | An Imperial outpost: a distant wordless loudspeaker, drill boots, metal; no intelligible words. |
| `music.selvaren` | `music/selvaren` | loop, stream, 48.0 s | "Music: Selvaren" | Selvaren's music (the dimension's `background_music`, vanilla music slider): old, open, patient; cool strings or pads and a few bell notes. Original composition, no resemblance to any franchise theme. |
| `music.varkhast` | `music/varkhast` | loop, stream, 48.0 s | "Music: Varkhast" | Varkhast's music: severe, dry, martial without marching; low drones and drums. Original composition, no resemblance to any franchise theme. |

### Ships and travel (`ship.py`, W3)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `ship.idle` | `ship/idle` | loop, volume 0.7, 3.0 s | "Ship engines idle" | The courier's engines at rest: a low hum, a soft wavering turbine whine and a rumble of air. Played at 0.5 on the ground and louder in flight; seamless. |
| `ship.spool` | `ship/spool` | one-shot, 2.8 s | "Engines spool up" | The engines spooling up: a whine climbing in pitch, a swelling rumble, latches clicking. |
| `ship.climb` | `ship/climb` | one-shot, 4.1 s | "Ship climbs" | A roar of thrust swelling and holding, with a slowly rising tone, then easing away. |
| `ship.descent` | `ship/descent` | one-shot, 4.5 s | "Ship descends" | A repulsor whine falling in pitch, the rumble settling with it. |
| `ship.land` | `ship/land` | one-shot, 2.3 s | "Ship touches down" | Touchdown: a heavy thump, the struts' creak, a long pneumatic hiss. |
| `ship.ramp` | `ship/ramp` | one-shot, 1.9 s | "Ramp moves" | A hydraulic motor under load, then a clunk as the ramp seats. |
| `ship.jump_out` | `ship/jump_out` | one-shot, 2.4 s | "Ship jumps to hyperspace" | A pressure thump, then a tearing streak climbing to nothing. |
| `ship.jump_in` | `ship/jump_in` | one-shot, 2.3 s | "Ship drops out of hyperspace" | A streak falling out of the high register into a heavy thump. |
| `ship.hyperspace` | `ship/hyperspace` | loop, volume 0.8, 4.0 s | "Hyperdrive hums" | The rider's hum in hyperspace: a deep drone beating slowly, an airy rush, a slow swell. Seamless. |
| `ship.fit_part` | `ship/fit_part` | one-shot, 1.2 s | "Part fitted" | A heavy clank, a latch click and a short rising hum. Pitched per part: the power core 0.8, the nav computer 1.3, a fuel cell 0.75, an upgrade 1.1. |
| `ship.weld` | `ship/weld` | one-shot, 1.1 s | "Hull welded" | Arc crackle and hiss over a buzz, then the metal ticking as it cools. |
| `ship.power_up` | `ship/power_up` | one-shot, 3.4 s | "Ship powers up" | The hulk standing up: relays clack, a tone rises through the systems and settles into the idle. |
| `ship.damaged` | `ship/damaged` | loop, volume 0.7, 2.0 s | "Damaged engine crackles" | A damaged engine: sparks, an irregular sputter, a failing buzz. Loops below 20 hull, and plays once with each `ship_damage` burst. |
| `beacon.set` | `beacon/set` | one-shot, 0.8 s | "Beacon set" | A latch click and two rising beeps. |
| `beacon.refuse` | `beacon/refuse` | one-shot, 0.7 s | "Beacon refuses" | Two low buzzing tones falling. |
| `beacon.recall` | `beacon/recall` | one-shot, 2.9 s | "Ship recalled" | Three ascending beeps, then a distant engine swell. |

### Gear: blasters, vibroweapons, the electrostaff, the jet pack (`gear.py`, W4)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `blaster.repeater.fire` | `blaster/repeater_fire1`, `blaster/repeater_fire2` | one-shot, 2 variants, 0.4 s | "Repeater fires" | One bolt of a heavy rotary blaster: short, punchy and low, with a mechanical tick of the barrel cluster. A stream at 6 to 7 shots per second must read as a rattle, not a row of single shots; no long tail. |
| `blaster.rifle.charged` | `blaster/rifle_charged` | one-shot, 1.3 s | "Charged shot fires" | The heavy bolt: the rifle's report an octave down with a hard transient crack and a rolling tail. Clearly bigger than a normal shot, still an energy weapon, not a firearm. |
| `blaster.overheat` | `blaster/overheat` | one-shot, 2.3 s | "Blaster overheats" | The gun choking: a short two-tone warning, a latch or heat-sink clank, then a pressurised steam scream that decays over two seconds. Alarming but not shrill. |
| `blaster.vent` | `blaster/vent` | one-shot, 0.8 s | "Blaster vents heat" | A deliberate manual vent: a latch thrown, a quick burst of steam. Satisfying, short. |
| `blaster.vent_perfect` | `blaster/vent_perfect` | one-shot, 1.4 s | "Blaster vents cleanly" | The same vent done right: cleaner and brighter, ending in a small rising two-note chime that says "good timing" without sounding magical. |
| `blaster.repeater.spin_up` | `blaster/repeater_spin_up` | one-shot, 1.1 s | "Repeater spins up" | An electric motor and bearings spinning up over about half a second, reaching a steady whir. Must be audible from a few blocks as a warning. |
| `blaster.repeater.spin_down` | `blaster/repeater_spin_down` | one-shot, 1.0 s | "Repeater spins down" | The motor winding down, the barrels coasting to a stop with a few soft ratchet clicks. |
| `vibro.flurry` | `vibro/flurry` | one-shot, 0.6 s | "Vibroblade flurry" | Two fast cuts in a row over the vibroblade's high buzz. Light, quick, sharp. |
| `vibro.cleave` | `vibro/cleave` | one-shot, 0.9 s | "Vibrosword cleaves" | A wide two-handed sweep: a heavy whoosh, the sword's lower buzz, a thud as it carries through more than one body. |
| `electrostaff.charge` | `electrostaff/charge` | one-shot, 2.5 s | "Electrostaff crackles" | Emitters charging over the 2-second charge: a rising hum with crackles growing denser. Not a loop; the arcs carry the loaded state afterwards. A final version could add a quiet crackle loop while loaded. |
| `electrostaff.shock` | `electrostaff/shock` | one-shot, 0.9 s | "Electrostaff shocks" | The charged hit: a hard electric snap and a short buzzing tail. Also played quiet and low as the fizz of a spent charge. |
| `electrostaff.clash` | `electrostaff/clash` | one-shot, 1.5 s | "Electrostaff blocks a lightsaber" | A saber stopped by the staff's emitters: a sharp contact, an energy sizzle and a short metallic ring. Distinct from a saber clash. |
| `jetpack.burst` | `jetpack/burst` | one-shot, 1.4 s | "Jet pack roars" | A short rocket burst: a hard ignition pop, a roaring rush of flame, a rumble that fades as the wearer rises. Loud and physical. |
| `jetpack.empty` | `jetpack/empty` | one-shot, 0.4 s | "Jet pack clicks empty" | An igniter clicking with no fuel: a dry click and a weak sputter. At most every 10 ticks. |

### Crafting: the bench, consumables, the scanner, grenades (`crafting.py`, W5)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `bench.mod_fit` | `bench/mod_fit` | one-shot, 0.5 s | "Mod fitted" | A mod seating into a weapon or armour piece: precise and mechanical, with the satisfaction of a part that fits. Short, since players fit several in a row. Played as a UI sound. |
| `bench.mod_remove` | `bench/mod_remove` | one-shot, 0.6 s | "Mod removed" | The reverse of `mod_fit`: a release, a little looser and lower. It must not sound like something breaking. |
| `bench.weld` | `bench/weld` | loop, volume 0.7, 2.0 s | "Bench welds" | The craft's working loop for the length of a craft (2 to 6 s): an arc welder at a distance, crackle and hiss over a low buzz. It must loop cleanly at any length, sit under dialogue and not tire after many crafts. Also the fusion cutter's loop, quieter. |
| `bench.done` | `bench/done` | one-shot, 1.6 s | "Crafting done" | The item is ready: a clean stop and a small "done" cue, like a tool powering down plus a soft notification. |
| `bench.masterwork` | `bench/masterwork` | one-shot, 3.2 s | "Masterwork crafted" | Rare and rewarding (a few crafts in a hundred at level 1, or the pity guarantee). Warmer and grander than `bench.done` but still short; it plays with a toast. Never a fanfare or brass. |
| `bench.salvage` | `bench/salvage` | one-shot, 1.1 s | "Parts salvaged" | Taking an item apart: a quick ratchet and parts settling on a metal surface. |
| `medpac.use` | `medpac/use` | one-shot, 1.2 s | "Medpac applied" | Applying a field dressing: tearing a sealed pack and a short cold spray. The confirm tone is optional; it must not sound like a UI beep. |
| `kolto.inject` | `kolto/inject` | one-shot, 1.0 s | "Kolto injected" | A quick injection of a healing fluid: a pneumatic press with a liquid, bubbling quality that sets it apart from the stims. |
| `stim.inject` | `stim/inject` | one-shot, 1.1 s | "Stim injected" | A combat stimulant: harder and faster than kolto, with a sense of the heart picking up. |
| `stim.crash` | `stim/crash` | one-shot, 1.9 s | "Stim wears off" | The stim wearing off. It should read as fatigue, not damage. |
| `scanner.pulse` | `scanner/pulse` | one-shot, 1.9 s | "Scanner pulses" | A sensor sweep spreading through the area: a rising tone and a wide tail. The tail sets the sense of range. Played as a UI sound. |
| `scanner.ping` | `scanner/ping` | one-shot, 0.5 s | "Scanner pings" | A find is marked: short, clear. It plays when the survey finds new marks, pitched up with their number (1.1 to 1.6), so it must not be shrill. |
| `grenade.throw` | `grenade/throw` | one-shot, 0.8 s | "Grenade thrown" | The pin pulled and the grenade thrown. The M3 grenade's fuse and burst cover the rest of the frag grenade. |
| `grenade.stun` | `grenade/stun` | one-shot, 1.4 s | "Stun grenade bursts" | A non-lethal electrical burst: a snap of discharge that spreads, with no fire or debris. Keep the high ring faint; it must not reproduce tinnitus at full volume. |

### Saberstaff and lenses (`saber.py`, W5)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `saber.staff_ignite` | `saber/staff_ignite` | one-shot, 1.4 s | "Second blade ignites" | A second blade joining a lit one: the M1 ignition's family, shorter, with a brief beat between the two hums. It must not mask the first blade's hum. |
| `saber.staff_whirl` | `saber/staff_whirl` | one-shot, 1.2 s | "Saberstaff whirls" | A full turn of two blades around the player: a doubled pass, the second slightly higher, and a firm end. It replaces the heavy strike's sound for the whirl, so it can be bigger than a combo swing. |
| `workbench.lens_fit` | `workbench/lens_fit` | one-shot, 1.3 s | "Lens fitted" | A small glass lens seating in an emitter: precise and delicate. It plays in the workbench screen, so keep it soft. Pitched by lens (1, 1.06, 1.12). |
| `workbench.tune` | `workbench/tune` | one-shot, 2.0 s | "Crystal tuned" | Reserved and unused: crystal tuning is cut (spec section 20). Still the generic Phase 0 placeholder; no final sound is needed unless tuning comes back. |

### Chapter Four, vendors and the hub (`story_m4.py`, W2)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `story.chapter_four` | `story/chapter_four` | one-shot, 4.6 s | "Chapter Four begins" | The sting under the "Chapter Four complete" banner (`lesson_complete` 8), played once when the chapter ends after Kaast: resolved and weighty, a muted brass swell under a short bell figure that settles on an open fifth. Not a fanfare. The subtitle says "begins", although the sound plays at the chapter's end. |
| `planet.arrive` | `planet/arrive` | one-shot, 4.2 s | "New world reached" | The sting under a planet's name on the first landing (`lesson_complete` 9): wind over open ground, a soft pad gliding up, one high chime as the name settles. Wide and calm. |
| `seeker.scan` | `seeker/scan` | one-shot, 2.3 s | "Seeker droid scans" | The seeker droid's scan at the lens: a thin electronic buzz whose tone sweeps down and up once every 1.5 s, matching the scan line, with a faint servo whirr. Cold and probing, not alarming. |
| `seeker.chirp` | `seeker/chirp` | one-shot, 0.5 s | "Seeker droid chirps" | A little droid voice: two quick upward blips. The client plays it twice at rising pitch as the droid leaves, and the marker beacon plays it quietly now and then, so it must stay short and never shrill. |
| `seeker.fly` | `seeker/fly` | one-shot, 2.2 s | "Seeker droid hovers" | A small repulsor hum rising and thinning as the droid climbs away; also the droid's idle sound. |
| `vendor.buy` | `vendor/buy` | one-shot, 0.7 s | "Purchase made" | Chits counted onto a counter, then a soft two-tone accept beep from the vendor's slate. Short and pleasant, since players buy several things in a row. |
| `vendor.refuse` | `vendor/refuse` | one-shot, 0.6 s | "Purchase refused" | Two short low buzzes from the slate: clearly "no", but not harsh. |
| `credits.pickup` | `credits/pickup` | one-shot, 0.4 s | "Credits clink" | Two or three bright clinks of small metal-edged chits. Played only when a vendor pays the player and at the exchange. |
| `hub.apprentice_spar` | `hub/apprentice_spar` | one-shot, 0.8 s | "Apprentices spar" | Training sabers meeting as an apprentice spar starts at the hub: a dull, low blade buzz with a wooden knock and a brief swish, quieter and duller than a duelling clash. |

### Force Speed, Force Barrier and Force Choke (`force_m4.py`, W6)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `force.speed.start` | `force/speed_start` | one-shot, 1.1 s | "Force speed" | Effort and lift, not magic: a sharp breath in, a whoosh that leans forward, one light footfall. Under a second. |
| `force.speed.loop` | `force/speed_loop` | loop, volume 0.7, 3.0 s | "Wind rushes past" | Wind past the runner's ears, gusting slowly, a faint whistle. Heard by the runner only and under the game's own sounds; it must loop with no seam or pulse. |
| `force.speed.end` | `force/speed_end` | one-shot, 0.9 s | "Speed fades" | The wind falling away with an easy exhale; quieter than the start. |
| `force.speed.burst` | `force/speed_burst` | one-shot, 0.6 s | "Force dash" | A short, fast tear of air and a push off the ground. Punchy; it plays with the dash. |
| `force.barrier.start` | `force/barrier_start` | one-shot, 1.8 s | "Force barrier" | The air thickening around the caster: a deep inhale and a pressure swell that settles into a held weight. |
| `force.barrier.loop` | `force/barrier_loop` | loop, volume 0.7, 2.0 s | "Barrier hums" | A low hum of held air with a slow beat. The client lowers its pitch and makes it waver as Force Energy runs low, so keep it low and steady enough for that to read. Seamless. |
| `force.barrier.impact` | `force/barrier_impact` | one-shot, 0.6 s | "Barrier absorbs a hit" | Something stopped dead in the air: a muffled thud and a spit of sparks. It must cut through a firefight at one play per stopped shot without tiring. |
| `force.barrier.pulse` | `force/barrier_pulse` | one-shot, 1.4 s | "Barrier pulses outward" | The held air let go at once: a thump and an outward rush with dust in it. |
| `force.barrier.break` | `force/barrier_break` | one-shot, 1.1 s | "Barrier breaks" | The barrier giving way: a sharp crack of air, a stumble of pressure, falling grit. Alarming, since it means the caster is open. |
| `force.choke.start` | `force/choke_start` | one-shot, 1.2 s | "Force choke" | A tight, low pressure closing in: a sinking tone and a squeezed rush. Menace, not gore. |
| `force.choke.loop` | `force/choke_loop` | loop, volume 0.7, 2.0 s | "Choked target strains" | On the caster while the grip holds: a strained creak of held pressure over a low, uneasy drone. The target's own hurt sounds are muffled meanwhile. No choking voice. Seamless. |
| `force.choke.release` | `force/choke_release` | one-shot, 1.0 s | "Choke released" | The grip opening: pressure draining away and a body dropping with a soft thud. |
| `force.choke.crush` | `force/choke_crush` | one-shot, 1.3 s | "Choke crushes" | A closing fist: a hard crunch of pressure and a heavier drop than the release. No bone sounds. |

### Mandalorian hunters, the security droid and the beasts (`enemy_m4.py`, W6)

Every tell here must be heard over combat; the hunters' voices are wordless, a voice through a sealed helmet.

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `mandalorian.jet_tell` | `mandalorian/jet_tell` | one-shot, 1.4 s | "Jet pack whines" | The hop's tell: a rising turbine whine and a few sputters of pilot flame at the pack. Also Kaast's shoulder-charge tell, played low. |
| `mandalorian.flamer_tell` | `mandalorian/flamer_tell` | one-shot, 1.0 s | "Pilot flame hisses" | The flamer's tell, a close-range warning: the wrist coming up with a gauntlet click and a pilot flame catching. |
| `mandalorian.rocket_lock` | `mandalorian/rocket_lock` | one-shot, 1.4 s | "Rocket locks on" | The rocket's tell, with the red dot: a targeting tone beeping faster and settling on a steady lock. |
| `mandalorian.net_tell` | `mandalorian/net_tell` | one-shot, 0.5 s | "Gauntlet clicks" | The net's tell: two dry gauntlet clicks and a spring winding tight. |
| `mandalorian.jet` | `mandalorian/jet` | one-shot, 1.7 s | "Hunter jets" | The hop: a hard burst of thrust that trails off as the hunter lands. |
| `mandalorian.flamer` | `mandalorian/flamer` | loop, 2.0 s | "Flamer roars" | A roaring gout of flame with a fluttering edge, looped by the client for the flame's length. Seamless. |
| `mandalorian.rocket` | `mandalorian/rocket` | one-shot, 1.5 s | "Rocket launches" | A launch pop, then a sustained hiss that recedes with the rocket. |
| `mandalorian.explode` | `mandalorian/explode` | one-shot, 2.5 s | "Rocket explodes" | The rocket's blast: a deep thump, a broadband crack, falling debris. |
| `mandalorian.net` | `mandalorian/net` | one-shot, 0.7 s | "Net snares" | A weighted net whipping open and dropping over its catch: a rope swish and a slap. |
| `mandalorian.hurt` | `mandalorian/hurt` | one-shot, 0.4 s | "Hunter hurts" | A grunt through a sealed helmet: band-limited, with a short metallic box resonance. |
| `mandalorian.death` | `mandalorian/death` | one-shot, 1.7 s | "Hunter dies" | A long groan through the helmet and armour hitting the ground. |
| `mandalorian.bark` | `mandalorian/bark` | one-shot, 0.7 s | "Hunter calls out" | A wordless two-syllable call through the helmet as a hunter engages. No words in any language. |
| `droid.spin_up` | `droid/spin_up` | one-shot, 1.4 s | "Droid barrels whir" | The repeater arm's 20-tick tell: a rising motor whir with a rattle of bearings. |
| `droid.fire` | `droid/fire` | one-shot, 0.4 s | "Droid fires" | A short, hard repeater shot with a metallic snap, distinct from the troopers' and the player's blasters. Plays once per bolt, so keep it short. |
| `droid.shield` | `droid/shield` | one-shot, 1.0 s | "Droid shield sparks" | The frontal plate locking forward with a clank, and a crackle of sparks where a bolt strikes it. |
| `droid.death` | `droid/death` | one-shot, 2.4 s | "Droid shorts out" | Sparks, a falling hum as the power drains, and a small burst. |
| `droid.hurt` | `droid/hurt` | one-shot, 0.5 s | "Droid sparks" | A clang on plate and a brief crackle. Also the dying droid's crackle, played low. |
| `droid.step` | `droid/step` | one-shot, 0.4 s | "Droid clanks" | A heavy metal foot and a servo settling. Played quietly with every step, so it must not tire. |
| `droid.alert` | `droid/alert` | one-shot, 1.0 s | "Droid sounds the alarm" | A flat two-tone alarm, once, as the droid first engages. |
| `hollowhorn.snort` | `hollowhorn/snort` | one-shot, 1.1 s | "Hollowhorn snorts" | The charge tell: a heavy snort and a hoof scraping dirt. |
| `hollowhorn.charge` | `hollowhorn/charge` | one-shot, 1.9 s | "Hollowhorn charges" | A galloping rush of hooves and a bellow. |
| `hollowhorn.impact` | `hollowhorn/impact` | one-shot, 1.2 s | "Hollowhorn rams" | Horns into a wall: a deep crack and a dazed grunt. |
| `hollowhorn.idle` | `hollowhorn/idle` | one-shot, 1.7 s | "Hollowhorn lows" | A low, contented lowing. |
| `hollowhorn.hurt` | `hollowhorn/hurt` | one-shot, 0.6 s | "Hollowhorn hurts" | A short pained bellow. |
| `hollowhorn.death` | `hollowhorn/death` | one-shot, 2.1 s | "Hollowhorn dies" | A falling bellow and the body settling into the grass. |
| `ash_hound.growl` | `ash_hound/growl` | one-shot, 1.0 s | "Ash hound growls" | The lunge tell: a low, rattling growl through bared teeth. |
| `ash_hound.howl` | `ash_hound/howl` | one-shot, 3.6 s | "Ash hound howls" | A thin howl that rises and falls and calls the pack; it should carry about 24 blocks. |
| `ash_hound.lunge` | `ash_hound/lunge` | one-shot, 0.6 s | "Ash hound lunges" | A snarl, claws on stone and a snap of jaws. |
| `ash_hound.idle` | `ash_hound/idle` | one-shot, 1.2 s | "Ash hound pants" | Quick, dry panting. |
| `ash_hound.hurt` | `ash_hound/hurt` | one-shot, 0.4 s | "Ash hound yelps" | A sharp yelp. |
| `ash_hound.death` | `ash_hound/death` | one-shot, 1.3 s | "Ash hound dies" | A whine that falls away and a light body dropping. |

### Torvin Kaast and his music (`enemy_m4.py`, W6)

| Id | File | Kind | Subtitle | Final target |
|---|---|---|---|---|
| `boss.kaast.hurt` | `boss/kaast_hurt` | one-shot, 0.4 s | "Kaast hurts" | A curt, controlled grunt through the helmet, with heavier plate than the hunters. |
| `boss.kaast.hop` | `boss/kaast_hop` | one-shot, 2.2 s | "Kaast jets away" | His heavier jet: a deep blast of thrust that carries him across the ring. |
| `boss.kaast.rifle` | `boss/kaast_rifle` | one-shot, 1.3 s | "Kaast's rifle fires" | A heavy slug rifle crack with a long tail across the camp. |
| `boss.kaast.phase` | `boss/kaast_phase` | one-shot, 1.9 s | "Kaast changes tactics" | With the phase banner: a gauntlet comm click, a short burst of static and the axe's strap pulled tight. |
| `boss.kaast.mine_arm` | `boss/kaast_mine_arm` | one-shot, 1.2 s | "Mine armed" | A twist, a latch and a rising arming tone. |
| `boss.kaast.mine_beep` | `boss/kaast_mine_beep` | one-shot, 0.2 s | "Mine beeps" | One short high beep per blink; the arming tell. The server plays it every 10 ticks while a mine arms, rising in pitch, then softer every 40 ticks once it is live, so it must stay readable but not grate. |
| `boss.kaast.mine_burst` | `boss/kaast_mine_burst` | one-shot, 2.2 s | "Mine explodes" | A ground mine going off, sharper and dirtier than the rocket. |
| `boss.kaast.net_throw` | `boss/kaast_net_throw` | one-shot, 1.2 s | "Net thrown" | A launcher's thump and the net spinning out. |
| `boss.kaast.axe_draw` | `boss/kaast_axe_draw` | one-shot, 1.7 s | "Kaast draws his axe" | The vibro-axe coming off his back for the last phase: a scrape of steel and a rising, rasping hum. |
| `boss.kaast.combo` | `boss/kaast_combo` | one-shot, 0.7 s | "Kaast's axe swings" | One axe swing: a heavy rasping whoosh. Also the axe tell, played low. |
| `boss.kaast.charge` | `boss/kaast_charge` | one-shot, 1.2 s | "Kaast charges" | The shoulder charge: heavy boots, a grunt and armour clattering. |
| `boss.kaast.slam_tell` | `boss/kaast_slam_tell` | one-shot, 2.8 s | "Kaast rises on his jets" | The slam's tell: he rises on his jets, a building roar and a whine climbing toward the dive. |
| `boss.kaast.slam` | `boss/kaast_slam` | one-shot, 2.7 s | "Kaast slams down" | The landing's crushing impact and a spray of earth. |
| `boss.kaast.helmet_crack` | `boss/kaast_helmet_crack` | one-shot, 0.9 s | "Helmet cracks" | A sharp crack of plate and a tinkle of falling fragments. |
| `boss.kaast.yield` | `boss/kaast_yield` | one-shot, 2.6 s | "Kaast yields" | The axe dropped into the dirt, a knee on the ground, a long tired breath. |
| `boss.kaast.walk_away` | `boss/kaast_walk_away` | one-shot, 3.2 s | "Kaast walks away" | Unhurried armoured footsteps fading out. |
| `music.kaast.hunt` | `music/kaast_hunt` | loop, stream, 21.8 s | "Battle music: The Hunt" | Phase 1 (`SoundSource.MUSIC`): patient and stalking, a frame-drum heartbeat, sparse enough for the rifle and the rocket lock's beeps to read over it. The three loops crossfade over 40 ticks, so they should share key relationships and tempo feel; 1 to 2 minutes each in the final. Original composition, no resemblance to any franchise theme. |
| `music.kaast.trap` | `music/kaast_trap` | loop, stream, 19.2 s | "Battle music: The Trap" | Phase 2: tense and ticking, a muted metal tick on every beat, leaving room for the mine beeps. |
| `music.kaast.last_stand` | `music/kaast_last_stand` | loop, stream, 15.5 s | "Battle music: The Last Stand" | Phase 3, with the axe out: driving drums and a low saw; the fight's peak. |

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

Milestone 3 (no voice acting is planned for M3; these are for a later voiced pass):
- Orren Vasch: a tired, articulate older man. His pain and Spent breaths (`boss.collector.hurt`, `boss.collector.spent`), and his dialogue lines in `c3_collector_*` if they are ever voiced.
- Trooper barks: the bark lines of both armies by category (warning, "Move along", salute, flank, rally, flare, ally down), Republic and Imperial voices heard through a helmet comm, delivered so `trooper.radio` can stay as the key click around them.
- Officers and gatekeepers: their dialogue lines are subtitles only.

Milestone 4 (no voice acting is planned for M4; these are for a later voiced pass):
- Torvin Kaast: a hired gun, controlled and unhurried. His pain grunt and yield breath (`boss.kaast.hurt`, `boss.kaast.yield`), and his challenge and yield lines (`c4_kaast_challenge`, `c4_kaast_yield`) if they are ever voiced.
- Mandalorian hunters: their hurt, death and bark sounds are wordless helmet voices now; a voiced pass could add short barks by situation (engage, a gadget used, ally down), delivered through the same helmet filter.
- Chapter Four's people, the vendors and the hub people: their dialogue lines are subtitles only.
