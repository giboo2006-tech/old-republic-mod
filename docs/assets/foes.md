# Foes assets, Milestone 4: the Mandalorian hunter, the security droid, the beasts, Torvin Kaast

Everything below is original art, sound and music made by our own scripts; nothing is copied from or imitates a Star
Wars game or film costume, droid, creature, sound or score. The hunters' helmet is our own T-visor shape (a rounded
dome, a flat T slot, a range-finder stalk), the droid a narrow sentry of our own design, the beasts invented for the
two planets. Textures are **placeholders** from `tools/assets/gen_textures_foes.py` (PIL, deterministic, seeded; run
from the repository root, `--part entity|item|block`); every UV layout is written in both the model class's Javadoc
and the script. Sounds and music are **designed stand-ins** from `tools/assets/sounds/enemy_m4.py`, rendered by
`python3 tools/assets/gen_sounds.py --m4 --feature enemy_m4` (mono OGG, 44.1 kHz, synthesised, nothing sampled); file
names are the Phase 0 placeholder names, so `sounds.json` and the subtitles (lead files) do not change. The design is in
`docs/design/foes.md`.

Palette rules: worn and muted, readable in the planets' light. The only red light on a battlefield is the hunter
rocket's targeting dot: mines, Rattle's scan line and the droid's eye are amber or pale, and the Imperial droid's red
is paint, not light.

## Textures

| Path (under `src/main/resources/assets/oldrepublic/`) | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/entity/mandalorian/hunter_{0,1,2,3}.png` | The hunter's four paints (ochre, slate blue, olive, dusty maroon), 128x64 (`HunterModel` layout) | placeholder generated | Worn plate chipped to bare metal at the edges over a dark undersuit; the T-visor painted black on the helmet shell; a range-finder stalk; gauntlets with the flamer nozzle, rocket tube and net launcher; a leather kama; a jet pack with two nozzles; the rifle (gunmetal, a scope). Practical and scuffed, never shiny. Paint differs, cut does not. |
| `textures/entity/kaast/torvin_kaast.png` | Torvin Kaast, 128x64, same layout | placeholder generated | Charcoal plate with bone-white trim, unmistakably the leader: heavier pauldrons, trophies on the belt, the vibro-axe (dark haft, a pale edge) in the axe UVs. The face under the helmet (shown in the yield) is a weathered older man with a short grey beard. |
| `textures/entity/kaast/torvin_kaast_cracked.png` | Kaast from phase 3: the helmet cracked across the visor | placeholder generated | As above, with a crack running through the visor and a chip missing from the dome. |
| `textures/entity/mandalorian/hunter_rocket.png` | The wrist rocket, 32x16 | placeholder generated | A stubby grey rocket with a dark nose band and four small fins. |
| `textures/entity/mandalorian/hunter_net.png` | The net, 64x64, cut-out | placeholder generated | Cords every four texels, knotted where they cross, everything else transparent. Rope brown with darker knots. |
| `textures/entity/kaast/camp_mine.png` | A camp mine, 32x16 | placeholder generated | A squat scrap-metal disc with three prongs and a small lamp housing; the lamp light is drawn in code (amber). |
| `textures/entity/droid/security_droid.png` | The Mk IV droid in a hunter escort's colours (worn tan-olive plate with rust spots), 64x64 (`DroidModel` layout) | placeholder generated | A tall, narrow sentry: a dark frame, a sensor head with one eye slit and an antenna, a chest plate and shoulder caps, twin rotary blasters on the forearms, a frontal plate shield folded on the chest. Weathered, scratched. |
| `textures/entity/droid/security_droid_imperial.png` | The same in Imperial colours (dark grey, clean, no rust) | placeholder generated | Dark grey; the final art may add crimson accents and a crest of our own on the chest plate (not any real emblem). |
| `textures/entity/droid/security_droid_eye.png` | The eye layer (emissive), 64x64 | placeholder generated | Only the eye slit: a short bright slit in the middle of the head's front face (8,8 rows 3 and 4); pale amber-white. Everything else transparent. |
| `textures/entity/droid/seeker_droid.png` | The seeker droid, 32x32 (`SeekerModel` layout) | placeholder generated | A dark faceted ball with one lens, three antennae and two short prongs; plain grey-black, a pale lens. |
| `textures/entity/kaast/kaast_droid.png` | Rattle, the same model in Kaast's paint | placeholder generated | Charcoal with bone-white trim and a few scratched tally marks; a pale amber lens. |
| `textures/entity/creature/hollowhorn.png` | The hollowhorn, 128x64 (`HollowhornModel` layout) | placeholder generated | A shaggy elk-like grazer the size of a horse: warm brown coat, a heavy, lighter tan chest mane, pale muzzle, horns that sweep back and curl up and forward (bone with dark tips). |
| `textures/entity/creature/hollowhorn_bull.png` | The herd bull | placeholder generated | The same, darker and greyer at the mane, heavier scarred horns. |
| `textures/entity/creature/hollowhorn_corrupted.png` | The corrupted variant | placeholder generated | The coat dulled toward ash grey with dark violet veins running along the neck and flanks, the eyes pale. Unsettling, not gory. |
| `textures/entity/creature/ash_hound.png` | The ash hound, 64x64 (`AshHoundModel` layout) | placeholder generated | A lean, hairless hound in ash grey, darker along the spine, deep chest, long skull, small swept ears, a whip tail, pale eyes. Reads against red rock and falling ash. |
| `textures/item/raw_hollowhorn.png`, `cooked_hollowhorn.png` | Food, 16x16 | placeholder generated | A thick cut with a bone; raw red-pink with fat, cooked brown and seared. |
| `textures/item/hollowhorn_hide.png`, `hollowhorn_horn.png` | Drops (lead's ids), 16x16 | placeholder generated | A shaggy brown hide pegged out; a curled bone horn with a dark tip. |
| `textures/item/ash_hound_fang.png` | Drop, 16x16 | placeholder generated | One long pale fang, ash-stained at the root. |
| `textures/item/droid_actuator.png` | Drop, 16x16 | placeholder generated | A small servo with a gear and cut wires, gunmetal and brass. |
| `textures/item/{mandalorian_hunter,security_droid,hollowhorn,ash_hound}_spawn_egg.png` | Spawn eggs, 16x16 | placeholder generated | Vanilla egg style: hunter ochre with dark grey spots, droid tan with pale amber spots, hollowhorn brown with cream, hound grey with pale spots. |
| `textures/block/camp_gate.png` | The camp gate, 16x16 | placeholder generated | Scrap plates riveted together, streaked with rust, a dark seam where the door slides. |

JSON written with the assets (final unless noted): `items/*.json` and `models/item/*.json` for the six items and four
eggs; `blockstates/camp_gate.json` and `models/block/camp_gate{,_open,_hidden}.json` (closed: the full door; open:
only the top row's header shows, the cells below are invisible and empty); the loot tables
`loot_table/entities/{mandalorian_hunter,security_droid,seeker_droid,hollowhorn,ash_hound,torvin_kaast,kaast_droid,camp_mine,hunter_rocket,hunter_net}.json`
and `loot_table/chests/kaast_stash.json` (camp dressing); the damage types `damage_type/{wrist_flamer,mandalorian_rocket,camp_mine,jet_slam,force_choke}.json`;
the lang in `src/main/lang-parts/enemy.json` and `boss.json`.

## Sounds

| Event | File (under `assets/oldrepublic/sounds/`) | Final sound (as rendered) | Brief |
|---|---|---|---|
| `mandalorian.jet_tell` | `mandalorian/jet_tell.ogg` | A rising turbine whine and a few sputters of pilot flame at the pack. | A tell: must be heard over combat. |
| `mandalorian.flamer_tell` | `mandalorian/flamer_tell.ogg` | The wrist comes up with a gauntlet click; a pilot flame catches and hisses. | A tell; close-range warning. |
| `mandalorian.rocket_lock` | `mandalorian/rocket_lock.ogg` | A targeting tone that beeps faster and settles on a steady lock. | A tell; plays with the red dot. |
| `mandalorian.net_tell` | `mandalorian/net_tell.ogg` | Two dry gauntlet clicks and a spring winding tight. | A tell. |
| `mandalorian.jet` | `mandalorian/jet.ogg` | The hop: a hard burst of thrust that trails off as he lands. | |
| `mandalorian.flamer` | `mandalorian/flamer.ogg` | Seamless: a roaring gout of flame with a fluttering edge. | Looped by the client for the flame's length. |
| `mandalorian.rocket` | `mandalorian/rocket.ogg` | A pop, then a sustained, receding hiss. | |
| `mandalorian.explode` | `mandalorian/explode.ogg` | The rocket's blast: a deep thump, a broadband crack, falling debris. | |
| `mandalorian.net` | `mandalorian/net.ogg` | A weighted net whips open and drops over its catch: a rope swish and a slap. | |
| `mandalorian.hurt`, `.death`, `.bark` | `mandalorian/{hurt,death,bark}.ogg` | A voice through a sealed helmet (band-limited, a short metallic box resonance): a grunt; a long groan and armour hitting the ground; a wordless two-syllable call. | No words in any language. |
| `droid.spin_up` | `droid/spin_up.ogg` | The arm barrels spin up: a rising motor whir with a rattle of bearings. | A tell (20 ticks). |
| `droid.fire` | `droid/fire.ogg` | A short, hard repeater shot with a metallic snap. | Distinct from the troopers' blasters. |
| `droid.shield` | `droid/shield.ogg` | The plate locks forward with a clank and a crackle of sparks. | |
| `droid.death` | `droid/death.ogg` | Sparks, a falling hum as the power drains, and a small burst. | |
| `droid.hurt`, `.step`, `.alert` | `droid/{hurt,step,alert}.ogg` | A clang on plate and a brief crackle; a heavy metal foot and a servo settling; a flat two-tone alarm. | |
| `hollowhorn.snort` | `hollowhorn/snort.ogg` | The charge tell: a heavy snort and a hoof scraping dirt. | A tell. |
| `hollowhorn.charge` | `hollowhorn/charge.ogg` | A galloping rush of hooves and a bellow. | |
| `hollowhorn.impact` | `hollowhorn/impact.ogg` | Horns into a wall: a deep crack and a dazed grunt. | |
| `hollowhorn.idle`, `.hurt`, `.death` | `hollowhorn/{idle,hurt,death}.ogg` | A low, contented lowing; a short pained bellow; a falling bellow and the body settling into the grass. | |
| `ash_hound.growl` | `ash_hound/growl.ogg` | The lunge tell: a low, rattling growl through bared teeth. | A tell. |
| `ash_hound.howl` | `ash_hound/howl.ogg` | A thin, rising and falling howl that carries. | Calls the pack; should carry 24 blocks. |
| `ash_hound.lunge` | `ash_hound/lunge.ogg` | A snarl, claws on stone and a snap of jaws. | |
| `ash_hound.idle`, `.hurt`, `.death` | `ash_hound/{idle,hurt,death}.ogg` | Quick, dry panting; a sharp yelp; a whine that falls away and a light body dropping. | |
| `boss.kaast.hurt` | `boss/kaast_hurt.ogg` | A curt, controlled grunt through the helmet; heavier plate. | |
| `boss.kaast.hop` | `boss/kaast_hop.ogg` | His heavier jet: a deep blast of thrust that carries him across the ring. | |
| `boss.kaast.rifle` | `boss/kaast_rifle.ogg` | A heavy slug rifle crack with a long tail across the camp. | |
| `boss.kaast.phase` | `boss/kaast_phase.ogg` | A gauntlet comm click, a short burst of static and the axe's strap pulled tight. | |
| `boss.kaast.mine_arm`, `.mine_beep`, `.mine_burst` | `boss/kaast_mine_{arm,beep,burst}.ogg` | A twist, a latch and a rising arming tone; one short high beep per blink; a ground mine going off, sharper and dirtier than the rocket. | The beep is the arming tell. |
| `boss.kaast.net_throw` | `boss/kaast_net_throw.ogg` | A launcher's thump and the net spinning out. | |
| `boss.kaast.axe_draw` | `boss/kaast_axe_draw.ogg` | The vibro-axe off his back: a scrape of steel and a rising, rasping hum. | |
| `boss.kaast.combo` | `boss/kaast_combo.ogg` | One axe swing: a heavy rasping whoosh. | |
| `boss.kaast.charge` | `boss/kaast_charge.ogg` | Heavy boots, a grunt and armour clattering. | A tell. |
| `boss.kaast.slam_tell`, `.slam` | `boss/kaast_slam_tell.ogg`, `kaast_slam.ogg` | He rises on his jets, a building roar and a whine climbing toward the dive; the landing's crushing impact and a spray of earth. | |
| `boss.kaast.helmet_crack` | `boss/kaast_helmet_crack.ogg` | A sharp crack of plate and a tinkle of falling fragments. | |
| `boss.kaast.yield` | `boss/kaast_yield.ogg` | The axe dropped into the dirt, a knee on the ground, a long tired breath. | |
| `boss.kaast.walk_away` | `boss/kaast_walk_away.ogg` | Unhurried armoured footsteps fading out. | |

## Music

Three loops, one per phase, crossfaded by `KaastMusic` (`BossMusicService` since Milestone 5); synthesised from a
frame drum, metal taps, a plucked low string and a reedy pulse lead (no samples, no quotations).

| Event | File | As rendered | Brief |
|---|---|---|---|
| `music.kaast.hunt` | `music/kaast_hunt.ogg` | Phase 1, "The Hunt": 88 BPM, 8 bars of E minor; patient and stalking, a frame-drum heartbeat. | Sparse, so the rifle and the rocket's beeps read over it. |
| `music.kaast.trap` | `music/kaast_trap.ogg` | Phase 2, "The Trap": 100 BPM, 8 bars of B minor; tense and ticking, a muted metal tick on every beat. | Leaves room for the mine beeps. |
| `music.kaast.last_stand` | `music/kaast_last_stand.ogg` | Phase 3, "The Last Stand": 124 BPM, 8 bars of E Phrygian; the axe out, driving drums and a low saw. | The fight's peak. |
