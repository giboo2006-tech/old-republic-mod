# Audio brief

Every sound currently shipped is a **procedurally synthesised placeholder** produced by `tools/assets/gen_sounds.py` (numpy oscillators, filtered noise, inharmonic partials, a simple noise-burst convolution reverb). No recording was sampled or imitated. This file tells a sound designer what each event should finally be, so the placeholders can be replaced file for file without touching code.

Regenerate placeholders: `python3 tools/assets/gen_sounds.py` (add `--java` to rewrite `ModSounds.java`, `--only saber/hum` to render one file, `--feature <module>` to render one recipe module, `--m2` or `--m3` to render one milestone's events, `--keep-wav DIR` to keep the WAVs). The Milestone 1 events come first, then the Milestone 2 events, then the Milestone 3 events.

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
