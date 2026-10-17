# VFX and SFX specification

Every feature defines its visual and audio treatment here before it ships. The Milestone 1 sections come first, then the Milestone 2 section, then the Milestone 3 section (merged from `docs/MILESTONE_3.md` section 13 and the features' design docs at integration). IDs are the contract between code, assets and `sounds.json`. All sound IDs are in the `oldrepublic` namespace; all are **placeholder** audio synthesised by `tools/assets/gen_sounds.py` until replaced (see `ASSET_MANIFEST.md`).

Art direction: effects should look like the films and games, not like Minecraft magic. No ground circles, sigils or halos. Force effects read as bent air, dust and debris, pressure and light, not glowing runes. Effects must never hide an enemy's attack tell.

## Quality presets

| Setting | Reduced | Standard | High | Cinematic |
|---|---|---|---|---|
| Particle multiplier | 0.35 | 1.0 | 1.5 | 2.2 |
| Effect render distance (blocks) | 24 | 48 | 64 | 96 |
| Max active mod particles | 300 | 900 | 1600 | 2600 |
| Ambient cave motes | sparse | normal | dense | dense + glints |

Separate client options: screen shake (0-100%, default 60%), flashing effects (on/off, default on; off replaces flashes with steady glows), and a mod SFX volume slider.

## Effect events

The server sends one `EffectEventPayload(type, position, direction, entityId, intensity, variant)` per gameplay event to players tracking the area. The client builds all particles, light and sound from it. No per-particle packets.

## Force Push (`force_push`)

| Phase | Visual | Audio |
|---|---|---|
| Anticipation (charging, up to 1 s) | Player's arm draws back; loose dust and small debris (block particles of the ground underfoot) drift *toward* the hand; air around the palm shimmers faintly (`air_ripple` particles, small, low alpha). Visible to other players. | `force.push.charge` rising low air rush, loops until release (stops on cancel, death, disconnect). |
| Activation | Arm snaps forward (open palm). | `force.push.release` deep pressure thump plus whoosh; 3 variants, pitch ±6%. |
| Travel | A cone of bent air: 3 to 5 expanding `air_ripple` shells travelling along the cone at 18 blocks/s, faint and colourless, plus directional dust streaks (`push_streak`) showing the cone edge. Branch shapes: Repulse Wave wide and low, Focused Blast a tight fast spear with brighter streaks, Deflecting Pulse a spherical shell. | — |
| Impact | Each target hit: `push_impact` burst on the side facing the caster plus block dust from what they stand on. Wall slam (Focused Blast): extra debris and `force.push.slam`. | `force.push.impact` per target (max 3 played per cast, extra targets attenuated). |
| Aftermath | Dust settles for 1 to 1.5 s; struck grass and leaves shed a few particles. | — |
| Camera | Caster: tiny kick (scaled by screen-shake setting). | — |
| Failure (not enough energy) | Hand flickers, a couple of dull motes fall. | `force.fail` hollow dull fizz (distinct from every ability sound). |

## Awakening (`awakening_dream`, `awakening_burst`)
- Dream: fade to near-black with a slow pulse, three subtitled whisper lines, `awakening.whispers` (layered breathy voices, no words) and `awakening.drone` (low sustained tone). Duration about 7 s. Cannot be interrupted by mobs (player is safe during sleep) and is skipped if the player takes damage.
- Burst: a single soft `air_ripple` shell expanding to radius 5 around the player, leaves and grass shed particles, items hop. `awakening.burst` (soft concussive bloom).

## Meditation (`meditation`)
- Player crouched; motes (`force_mote`, pale, slow) drift inward and orbit loosely; a thin HUD focus ring fills (HUD, not in-world).
- `meditation.loop` breathy calm pad; `meditation.complete` soft chime; `meditation.break` short exhale.

## The Calling
- While holding the journal: faint `resonance_shimmer` particles appear 4 to 7 blocks away in the direction of the nearest cave and drift toward it. `crystal.calling` soft tonal ping every few seconds, louder when facing the cave.

## Crystal caves
- Ambient: sparse `crystal_mote` particles near crystal formations (density from preset), occasional `crystal_glint` on cluster faces, a slow light pulse on kyber clusters (block light is static vanilla light; the "pulse" is particle-based, not dynamic lighting).
- Audio: `crystal.resonance` looping positional hum near large formations (attenuation 16 blocks), `ambient.cave` low wind/drip bed, `crystal.chime` random soft chimes.
- Unstable cluster hazard: tell = cluster brightens with gathering sparks and `crystal.unstable_charge` for 1.2 s; then `crystal.discharge` crack, damage 4 in 2.5 blocks, `crystal_spark` burst.

## Trial of Reflection
- Start: chamber particles fade, `trial.begin` (reverse swell), the Shadow Echo forms from dark smoke (`shadow_wisp`) with `trial.echo_form`.
- Echo tells: lunge = crouch + `shadow_wisp` trail + `trial.echo_lunge`; mirrored push = arm draw with dark-tinted ripples.
- End: echo dissolves into wisps, `trial.complete` rising choir-like pad, crystal grows on the stone with `crystal.form` and a burst of `crystal_glint`.

## Lightsaber
- Blade: white-hot core and coloured outer glow layer, both rendered full-bright (`light_emission` 15 on the model elements). No real light is cast on the world (vanilla has no dynamic lighting; noted in KNOWN_ISSUES).
- Ignition/retraction: blade extends/retracts over 6 ticks (client-side blade-extension item property).
- Audio: `saber.ignite`, `saber.retract`, `saber.hum` (loop, positional, stops on retract, drop, death, disconnect, dimension change, item switch), `saber.swing` (4 variants), `saber.clash` (3 variants), `saber.deflect` (3 variants), `saber.parry` (bright ring), `saber.guard_break`, `saber.hit` (flesh/armour sizzle).
- Clash/deflect: `saber_spark` burst at the actual contact point plus a one-tick `contact_flash` (suppressed when flashing effects are off).
- Swing trail: short-lived ribbon of the blade colour along the swing arc in third person (approximation from swing progress; see KNOWN_ISSUES).

## Crystal Poacher and blaster
- Tell: rifle muzzle glow (`muzzle_glow`) and `blaster.charge` whine for 0.6 s.
- Shot: `blaster.fire` (2 variants), red elongated full-bright bolt with a short fading trail; `blaster.bolt_flyby` when passing within 3 blocks of a player.
- Impact: `blaster.impact` + small scorch-spark burst (`bolt_impact`).
- Voice: no voice lines in M1 (asset brief lists them).

## Lightsaber Workbench
- `workbench.slot` component seat click; `workbench.assemble` layered mechanical clicks + crystal hum swell + ignition test; on construct, `crystal_glint` burst above the bench.

## Subtitles
Every sound event in this file has a subtitle key `subtitles.oldrepublic.<id>`.

---

# Milestone 2

From `docs/MILESTONE_2.md` sections 8, 9 and 12, the features' design docs (`docs/design/{force,saber,enemy,identity,skill,quest}.md`) and the client handlers as built. Sound ids, files and placeholder notes are in `AUDIO_BRIEF.md`.

## Rules added in Milestone 2

- Art direction as in Milestone 1: bent air, dust, debris, pressure and light; no ground circles, sigils, halos or glyphs.
- Tells are never hidden or reduced by presets. The NPC saber wind-up glint, the Acolyte's shove gathering and the Captain's heavy wind-up are spawned outside `Vfx.count`.
- Flashing off replaces flicker and flashes with steady glows (for example, Lightning arcs hold a steady alpha of 0.85).
- Every loop stops on cancel, death, disconnect, dimension change, and item or slot change. Client watchdogs back this up (see "Loops and watchdogs").

## Particle budgets per active instance (Standard; scaled by the preset multiplier through `Vfx.count`)

| Effect | Budget |
|---|---|
| Lightning | ≤ 70 live segments per caster |
| Heal | ≤ 40 motes |
| Jump landing | ≤ 50 |
| Pull | ≤ 35 |
| Thrown saber trail | ≤ 30 |

## New particle types (11)

| Feature | Particle | Use |
|---|---|---|
| force | `pull_streak` | grainy dust streak along a pulled target's path, rotated toward the caster |
| force | `jump_trail` | faint mote behind a leaping player (none on Reduced) |
| force | `lightning_segment` | one straight piece of a Force Lightning arc, stretched along the segment, full-bright |
| force | `lightning_spark` | fingertip crackle, contact sparks, disarm metal sparks (tinted warm) |
| force | `ward_glint` | Serene Ward glint on the body outline |
| saber | `blade_glint` | the NPC wind-up tell at the blade tip, tinted with the blade colour |
| saber | `lunge_streak` | low streak of the blade colour along the lunge path |
| identity | `rope_fibre` | frayed rope strands when captives are cut loose |
| story | `voice_warm` | the warm voice of Calling variant 2 (pale gold glints) |
| story | `voice_dust` | the cold voice of Calling variant 2 and the mentor's departure puff |
| story | `ash_mote` | Calling variant 3 (smoke on the ridge) |

Reused: `force_mote` (Heal, recoloured warm; rank-up motes), `air_ripple` and `gather_dust` (Pull, Jump), the saber `saber_spark` sparks and vanilla block particles for debris.

## Effect events

| Feature | Ids |
|---|---|
| identity | `alignment_shift` (toPlayer; intensity = amount, variant = new band and flags), `reputation_change` (toPlayer; variant = faction and flags, intensity = amount), `rank_up` (toTracking; variant = path), `yield` (toTracking; entity = yielder), `settler_freed` (around; entity) |
| story | `calling` variants 2 (voices) and 3 (smoke); `awakening_dream` variant 2 (Two Voices start); `lesson_complete` (toPlayer); `mentor_depart` (around; entity) |
| skill | `node_learned` (toPlayer; intensity = rank, variant = tree), `respec` (toPlayer; intensity = refund) |
| force | `force_pull`, `force_pull_hit`, `force_pull_disarm`, `force_jump_gather`, `force_jump_launch`, `force_jump_land`, `force_jump_slam`, `force_jump_glide`, `force_heal`, `force_heal_ally`, `force_heal_ward`, `force_lightning`, `force_lightning_target`, `force_lightning_hit` (fields in `docs/design/force.md` 12) |
| saber and enemy | `saber_combo`, `saber_heavy`, `saber_lunge`, `saber_form`, `saber_throw`, `saber_throw_catch`, `npc_guard_break`, `npc_windup`, `acolyte_shove`, `acolyte_shove_clash`, `captain_heavy`, `spar`; reuses `saber_clash`, `saber_parry`, `saber_hit`, `blaster_fire`, `bolt_impact` |

Lightning sends about 4 packets per second per caster (a hit every 5 ticks, target changes at most every 4).

## Force Pull (`force_pull`)

| Phase | Visual | Audio |
|---|---|---|
| Anticipation | A low-alpha `air_ripple` contracting at the palm, short streaks along the reach. | `force.pull.cast` (2 variants) |
| Travel | `pull_streak` particles peel off the target's outline and the ground and stream toward the caster (the reverse of push streaks). Dust and block bits are kicked from under a dragged target. Pulled items leave short dust trails. No glow outline on the target. | `force.pull.drag`, positional at the target |
| Arrival | A small dust puff at the caster's feet. | `force.pull.catch` |
| Disarm | A few warm metal sparks at the weapon hand. A disarmed poacher is drawn with the rifle lowered. | `force.pull.disarm` |

## Force Jump (`force_jump`)

| Phase | Visual | Audio |
|---|---|---|
| Gather (held) | Crouched; dust (block particles of the ground) drawn in around the feet; a faint, short-lived low shimmer at ground level every 6 ticks, never a ring decal. | `force.jump.gather` (loop) |
| Launch | A compressed dust burst at the take-off point, scaled with charge. | `force.jump.launch` |
| Travel | Subtle `jump_trail` motes behind the player for 10 ticks (none on Reduced). Soaring Leap glide: arms spread. | `force.jump.glide` (loop, while gliding) |
| Landing | Dust and block chips by fall strength and surface: sand bursts, stone chips, snow powder. | `force.jump.land` (soft or heavy by intensity) |
| Crushing Descent | A ground-level `air_ripple` shell and a radial debris spray; caster shake 0.25. | `force.jump.slam` |

## Force Heal (`force_heal`)

| Phase | Visual | Audio |
|---|---|---|
| Start | Pale gold-white `force_mote` particles (warmer than the meditation motes) drift inward from about 2 blocks around. | `force.heal.start` |
| Sustain | Motes stream into the chest and rise loosely along the body; on each heal tick, a very faint, small `air_ripple` pulse at the chest. **No halo, ring or column of light.** | `force.heal.loop` (loop, positional) |
| End | Motes drift upward and fade over 20 ticks. | `force.heal.end` |
| Interrupted (a hit of ≥ 4 damage, or any stagger) | The motes scatter outward and fall. | `force.heal.interrupt` |
| Shared Light | A thin stream of motes from the caster to each ally. | |
| Serene Ward | Sparse `ward_glint` specks on the body silhouette every 4 ticks for its duration. | `force.heal.ward` |

## Force Lightning (`force_lightning`)

| Phase | Visual | Audio |
|---|---|---|
| Charge (8 ticks; others see the tell) | Tiny `lightning_spark` specks jump between the fingertips, with 2 or 3 short arcs (0.3 blocks) around the hand. | `force.lightning.charge` |
| Arcs (held, at most 80 ticks) | Built from `lightning_segment` quads (the `push_streak` technique). Each arc is a jittered polyline of 8 to 14 segments from the fingertips to the target's torso, rebuilt every 2 ticks, with 1 + mastery-rank forks of 0.5 to 1.5 blocks. Arcs per caster: Reduced 1 with no forks, Standard and High 2, Cinematic 3. Chain arcs run from the primary target to the others. White core, pale violet-blue edges; segments live 2 to 3 ticks. Flicker: alpha 0.7 to 1.0 per rebuild, or a steady 0.85 with flashing off. In first person the arcs leave from just below the view. Caster shake 0.03 while arcing. | `force.lightning.loop` (loop, positional at the caster) |
| Contact | A `lightning_spark` burst on the target surface each damage tick, plus the vanilla hurt flash. | `force.lightning.impact` (at most 2 per 10 ticks per caster) |
| Guarded | A lit saber facing the caster takes the arcs. | `saber.lightning_block` (at most 1 per 10 ticks) |
| Stop | Every live segment is removed within a tick; 2 or 3 faint dark smoke wisps at the hand for 10 ticks, then nothing. No dynamic light (see Known issues). | `force.lightning.release` |

## Saber combat (`saber_combo`, `saber_heavy`, `saber_lunge`, `saber_form`, `spar`)

- **Combo:** each step picks the swing-trail arc (left to right, right to left, vertical). The third step (the finisher) adds a raised overhead arm pose and `saber.finisher`.
- **Heavy attack:** the tell is the blade drawn back over the shoulder for 10 ticks, with a blade-tip glint and the rising `saber.heavy_windup`, visible to all. The strike uses the `HEAVY` trail style, kicks dust at the feet (8) and plays `saber.heavy_strike`; the attacker gets a 0.2 shake. A cancelled wind-up just drops the pose.
- **Lunge:** a dust kick at the start (10), eight `lunge_streak` particles of the blade colour along the dash, `saber.lunge`; hit sparks in the blade colour on a hit (10), a small dust puff (6) on a miss.
- **Forms:** the hilt is raised in a salute pose for the length of the change, with `saber.form_change` (a hum-pitch shift).
- **Spar:** begin and end stings `story.duel_begin` and `story.duel_end`.
- **Any living entity:** saber audio and trails now follow every living entity within 32 blocks that holds a lit saber (mentors, the Acolyte). The hum of non-players plays in the `HOSTILE` source. Trails are third person only and use the entity's main arm.

## NPC saber duelling (`npc_windup`, `npc_guard_break`)

- **Wind-up tell** (never reduced by presets): the blade drawn back, a bright `blade_glint` at the blade tip in the blade colour, spawned through the particle engine directly so presets never thin it. `saber.npc_windup` plays on the first swing of a string.
- The NPC's synced `GUARDING` and `BROKEN` states show as poses. A broken NPC guard reuses the player guard-break effect.

## Saber Throw (`saber_throw`, `saber_throw_catch`)

| Phase | Visual | Audio |
|---|---|---|
| Throw | The throw pose; the saber item shows empty while thrown. | `saber.throw` |
| Flight | The thrown saber renders the real blade (full-bright core and glow) spinning at 20 rotations per second, with a circular trail ribbon in the blade colour (the swing-trail code). | `saber.throw_loop` (loop, positional on the entity; stops when it is removed) |
| Hit | The existing `saber_hit` sparks. On a block: blade-colour sparks (12) and a little smoke (5), with `saber.hit` pitched up. | `saber.hit` |
| Catch | The catch pose. | `saber.throw_catch` |

## Sith Acolyte and Poacher Captain (`acolyte_shove`, `acolyte_shove_clash`, `captain_heavy`)

- **Force Shove wind-up** (16 ticks; tell, never reduced): the hand thrust forward, a ring of ten deep red-black dust ripples gathering at the palm (the Shadow Echo's visual language), dust at the feet, `acolyte.shove_windup`.
- **Shove release:** a cone of dark dust ripples (24) and a few cloud puffs (8) rolling out along the shove; shake 0.15 for players within 7 blocks; `acolyte.shove`.
- **Push clash** (a Push during the wind-up): the shove is snuffed out in a dark burst (18) with a small explosion puff; `acolyte.shove` pitched down to 0.6. The Acolyte reels.
- **Captain's overhead heavy** (16 ticks; tell, never reduced): the vibroblade raised, a spark-and-glint over the blade, the `captain.heavy_windup` shout. Strike: shake 0.2 for players within 4 blocks and dust at her feet (6).
- **Captain's pistol:** a muzzle glow layer during the single-shot tell; `blaster.pistol_fire`. Vibroblade: `vibroblade.swing` and `vibroblade.hit`.
- **Yield:** dust kicked up as the NPC kneels (8), with its surrender line (`captain.yield` or `acolyte.yield`). Hurt and death sounds: `acolyte.hurt`, `acolyte.death`, `captain.hurt`, `captain.death`.

## Identity feedback (`alignment_shift`, `reputation_change`, `rank_up`, `settler_freed`)

- Nothing is shown before the player has been introduced (at the Crossing, or on choosing any path); values are still recorded.
- **Alignment shift:** a major or larger shift shows a framed toast at the top left, under the Force HUD, for 3 s ("Your choice echoes toward the Light (+12)"), with `identity.alignment_light` or `identity.alignment_dark`. A contextual shift of 3 or less shows a small text line only, with no sound, and can be turned off with `/oldrepublicfx alignment_toasts off`. A band change also shows a banner ("You lean toward the Light").
- **Reputation change:** a framed toast in the faction colour; on a tier change it names the new tier.
- **Rank up:** a banner in the path colour and `identity.rank_up` for the player; others hear it positionally, quieter. Pale `force_mote` motes rise slowly around the player (24 at Standard).
- **Settler freed:** rope fibres (12) and `settler.freed`, played lower (pitch 0.85) when the captives were made to pay. Settler hurt and death: `settler.hurt`, `settler.death`.
- **Dialogue:** `dialogue.open` when the panel opens, `dialogue.select` on a reply.

## Chapter Two (`awakening_dream` variant 2, `calling` variants 2 and 3, `lesson_complete`, `mentor_depart`)

| Moment | Visual | Sound | Budget / stop |
|---|---|---|---|
| Two Voices dream (6 s) | The dream overlay with three subtitled lines alternating between a warm and a cold voice | `story.two_voices` bed | ends at 6 s or on damage; the bed stops with it |
| Calling, voices (variant 2) | `voice_warm` glints and `voice_dust` drifting along the direction; the exact position is never sent | two low crystal-calling tones | `Vfx.count`; only while the journal is held |
| Calling, smoke (variant 3) | `ash_mote` drift | none (the camp's real campfire smoke guides) | `Vfx.count` |
| Mentor departs | a low puff of `voice_dust` at the feet | none | 10 particles before `Vfx.count`, range-checked |
| Lesson, mission or chapter milestone | a banner in the path colour | `story.lesson_complete` | one-shot |

## Skills (`node_learned`, `respec`)

| Moment | Treatment |
|---|---|
| Node learned | `skill.learn`, pitched up 6% per rank above 1. On the Force screen, a bright double ring fades around the node over 24 ticks (no strobe, so flashing off leaves it as is); with the screen closed, a banner. No world particles (it is a menu action). |
| Slot assigned | `skill.slot` while the screen is open. |
| Respec | `skill.respec`; the Force screen opens on the tree. |

## Loops and watchdogs

- Loops that must stop: `force.jump.gather`, `force.jump.glide`, `force.heal.loop`, `force.lightning.loop`, `saber.throw_loop` and `story.two_voices`.
- Client watchdogs:
  - Heal: 76 ticks.
  - Lightning: arcs are hidden after 6 ticks without a damage-tick refresh (they return with the next one), and the channel is forgotten after 40 ticks.
  - Jump: gather 70 ticks, glide 50.
  - Predicted starts: 15 ticks.
- A level change or a disconnect clears every effect.
- Concurrency limits: `force.lightning.impact` ≤ 2 per 10 ticks per caster; `saber.lightning_block` ≤ 1 per 10 ticks; `saber.clash` ≤ 1 per entity per 3 ticks (as in Milestone 1).

# Milestone 3

From `docs/MILESTONE_3.md` section 13, the features' design docs and the client handlers as built. The full phase-by-phase treatments are in `docs/design/world.md` section 24, `docs/design/force.md` section 22, `docs/design/holocron.md` ("Effects (spec 13.3), phase by phase"), `docs/design/enemy.md` section 35, `docs/design/equipment.md` and `docs/design/boss.md` section 11; the per-event looks for artists are in `docs/assets/*.md`. Sound ids, files, subtitles and briefs are in `AUDIO_BRIEF.md`. None of these effects has been seen by a person yet; the client screenshots `oldrepublic_m3_10` to `59` show most of them once CI passes.

## Rules added in Milestone 3

- Art direction as before: bent air, dust, debris, pressure and light; **no ground circles, sigils, halos, glyphs or glow outlines**.
- Anything that spreads along the floor (the Surge, grenade bursts, the sentinel slam, the Lift slam) is built from irregular dust puffs and debris of varied heights, with gaps; never a continuous ring, and never a flat `air_ripple` sprite at floor level.
- No effect claims refraction: "bent air" is particles and motion.
- **Tells are never thinned.** Every particle of a row that `DangerTellTable.isTell` classifies as a tell goes through `Vfx.tellCount`, which ignores the preset. Ambient, travel and aftermath particles go through `Vfx.count` as before.
- Flashing off replaces flicker and flashes with steady glows (the grenade tell and fuse, the flare, the sentinel core and lumen, the gatekeeper's banding, the Unmake and the boss shatters).
- Every loop stops on cancel, death, disconnect, dimension change, leaving the area, and item or slot change, with a client watchdog behind it (see "Loops and watchdogs (Milestone 3)").

## Particle budgets per active instance (Milestone 3, Standard)

| Effect | Budget |
|---|---|
| Sense reveal (inhale, streams and clusters) | ≤ 60 (8 inhale, 24 stream specks, 30 cluster motes; the 12 holocron pulse motes come after the inhale) |
| Lift (shimmer column and falling pebbles) | ≤ 30 (about 20 live in practice) |
| Holocron attunement, including the gatekeeper figure | ≤ 120 |
| Trooper muzzle and impact | ≤ 8 per shot |
| Grenade burst | ≤ 40 |
| Sentinel slam | ≤ 35 |
| Boss Surge (gather and release) | ≤ 160 (159 as built) |
| Boss hall, all sources | ≤ 900 (worst moment of phase 2 about 300; phase 3 with a Surge about 220) |
| Site ambience, per site | ≤ 40 live |

## New particle types (23)

| Feature | Particle | Use |
|---|---|---|
| world | `light_mote` | warm sunlit mote that drifts and sways: ruin light shafts, lumen moss, pedestals, urn echoes |
| world | `leaf_fall` | a small leaf that tumbles and settles: ruin courtyard ambience |
| world | `tomb_dust` | grey-brown dust that sifts down: ceiling dust, crumbling, dust curtains, puffs |
| world | `veil_dust` | fine pale dust seeping from mortar lines: veil reveal and open, urn plume, seal crumble |
| world | `vent_grit` | heavy chips and pebbles with gravity and bounce: vent tell, rubble, counterweights, step stones, anchors |
| world | `miasma` | dull grey-green puff that swells, lingers about 30 ticks and thins: tomb vent puff and wisp |
| world | `conduit_spark` | short white-hot spark that falls, bounces and cools to orange: wreck conduits |
| story | `falling_ember` | ember streaks slanting down toward Courier's Fall (Calling variant 8) |
| force | `sense_mote` | Sense cluster mote, the inhale motes and the holocron pulses; tinted by attitude or kind |
| force | `sense_stream` | tiny dust grain running from the caster to each sensed point |
| force | `lift_shimmer` | tall faint wisp of bent air under a held target |
| holocron | `holocron_mote` | soft speck of seam light: the rise, the figure's edges, the Listen and Keep streams, echo glints, exhales |
| holocron | `projection_band` | thin horizontal line drifting up through the gatekeeper figure (steady with flashing off) |
| enemy | `kolto_mist` | pale teal-green vapour of the medic's kolto and of tending |
| enemy | `vent_steam` | steam wisps from rifle vents; also the bent-air streak of a redirected grenade |
| enemy | `grenade_smoke` | lumpy grey-brown smoke of the burst and fuse; the dust scuffs of a rally and of a trooper standing |
| enemy | `flare_ember` | hot core and spark: the flare (red), the burst (orange), the rally (gold), the armour set flourish |
| boss | `ward_haze` | heat-haze specks drifting off Vasch while the ward holds; never a bubble or halo |
| boss | `ward_current` | thin wavering current from each singing pylon to Vasch |
| boss | `surge_dust` | sandy dust puffs of the Surge's broken wall |
| boss | `lash_seam` | grit streak of the Lash's seam and travelling ridge |
| boss | `echo_smoke` | grey smoke at the echo's edges as it forms and dissolves |
| boss | `lumen_spark` | Ruin Sentinel lumen spark, tinted pale blue (Watcher) or red (Tomb); sputters only with flashing on |

Reused: `force_mote` (Chapter Three), `crystal_glint` (pylons and shards), `air_ripple` and `push_streak` (Lift), `saber_spark` (deflects) and vanilla block particles for debris.

## Effect events (Milestone 3)

Each row is one gameplay moment, one `EffectEventPayload`. Delivery and fields are in spec 13.3.

| Feature | Ids |
|---|---|
| world | `veil_reveal`, `veil_open`, `counterweight` (sink, warn, rise), `step_stone` (extend, retract), `urn_echo` (step 0 to 3), `urn_wrong`, `anchor` (strain, release), `rubble_crumble`, `seal_open`, `puzzle_solved` (toPlayer, sound only), `pedestal_ready`, `tomb_vent` (tell, puff), `terminal_use`; the site ambience follows `SitePresencePayload` |
| story and missions | `site_discovered` (toast), `calling` variants 4 `HOLOCRON`, 5 `CONTRACT`, 6 `RELIQUARY`, 7 `OUTPOST`, 8 `FALL`; `awakening_dream` variant 3; `lesson_complete` variants 4 to 7 (mission, chapter3, boss, rank3); `cairn_touch`; `rank_ceremony` (Knighting, Naming, Pathfinder); `mission_update` (accept, progress) |
| force and holocron | `force_sense` (gesture; holocron variant), `force_lift` (start, end, slam, hurl), `force_lift_target`, `holocron_attune`, `holocron_gatekeeper` (form, fade), `holocron_choice` (seal, break, listen), `holocron_echo`, `archive_choice` (return, keep, unmake) |
| troopers and armour | `trooper_charge`, `blaster_fire` (extended with the bolt style), `trooper_vent`, `kolto_heal`, `trooper_tend` (tending, stands), `trooper_rally`, `flare_call`, `grenade_tell`, `grenade_fuse`, `grenade_burst`, `grenade_return` (push, pull), `trooper_bark`, `trooper_salute`, `armour_set` |
| boss, sentinels and saber | `sentinel_wake` (wake, bow), `sentinel_slam` (tell, impact), `sentinel_core`, `sentinel_lance` (charge, fire), `sentinel_death`, `boss_hurl` (lift, throw, ripped, returned, caught), `boss_palm`, `boss_lash`, `boss_surge` (gather, release, clash), `boss_spent`, `boss_grip` (tell, hold, break), `boss_lunge`, `boss_ward` (ward, Unsung), `boss_phase` (0 to 6), `boss_yield`, `boss_leash`, `boss_echo`, `collector_resolve` (scenes 0 to 5), `pylon_state` (sing, hit, silenced, regrow tell, re-sung, shatter), `npc_deflect`, `hoard_open` |

## Sites and puzzles (`veil_*`, `counterweight`, `urn_*`, `anchor`, `rubble_crumble`, `seal_open`, `tomb_vent`)

| Moment | Visual | Audio |
|---|---|---|
| Veil reveal | Up to 36 fine `veil_dust` grains seep from the mortar lines of the group's open faces; the seams catch the light, steadily. While revealed, each stone keeps seeping a grain now and then. | `puzzle.veil_reveal` |
| Veil open | Chunks of the skin's stone fall inward (up to 40), a dust fall from the tops (up to 30), a small `tomb_dust` puff outward (up to 16). | `puzzle.veil_crumble` |
| Counterweight | Sink: dust squeezed from the seams and a grit puff. Warn: a grit trickle. Rise: dust slides off. | `puzzle.counterweight_sink`, `_warn`, `_rise` |
| Step stones | A grit spill along the ledge. | `puzzle.step_extend`, `puzzle.step_retract` |
| Urn echo | A slow plume of fine dust from the urn mouth for 24 ticks, warm `light_mote`s rising inside it. | `puzzle.urn_echo` (D, E, F, G by step, no random pitch) |
| Wrong urn | A 3-block curtain of dust and grit from the ceiling. | `puzzle.urn_wrong` |
| Pull anchor | Strain: the ring shivers, shedding rust motes and dust. Release: a grit puff. | `puzzle.anchor_strain`, `puzzle.anchor_release` |
| Rubble | When a Lift channel starts on the heap, the crumble plays quietly and 4 chips fall (the tell); grit slides off for the channel; then a dust burst with rolling pebbles. | `puzzle.rubble_crumble` |
| Seal, pedestal, solved | Seal: a dust burst, then the face crumbles. Pedestal ready: thin light in the seams, slow motes from the cradle. Solved: no visual. | `puzzle.seal_open`, `puzzle.pedestal_ready`, `puzzle.solved` |
| Tomb vent | Tell (15 ticks, never thinned): grit thrown up through the grate, a thin miasma wisp every 4 ticks. Puff: a grey-green miasma cloud 1.5 wide and 2 high that lingers about 30 ticks, with grit chips. | `vent.hiss`, `vent.puff` |
| Terminal | The screen brightens (steady). | `terminal.use` |

**Site ambience** (`SiteAmbience`; no tells). Entry: the scene's bed fades in over 60 ticks (`ambient.jedi_ruin` 0.55, `ambient.sith_tomb` 0.6, `ambient.wreck` 0.55, `ambient.outpost` 0.4, `ambient.reliquary` 0.6); moving between scenes cross-fades. Presence: one-shots 4 to 18 blocks away (`ambient.ruin_chime` every 15 to 40 s, `ambient.tomb_whisper` every 8 to 20 s, `ambient.wreck_creak` every 10 to 30 s) and at most 40 live particles per site (motes in sun shafts, falling leaves, lit dust, tomb dust, conduit sparks). A Sense cast dips the beds 30% for 20 ticks. Exit: leaving or dying fades the bed over 60 ticks. The outpost mast lamp's flare is client particles, with none when flashing is off.

## Chapter Three and missions (`calling` 4 to 8, `awakening_dream` 3, `lesson_complete` 4 to 7, `cairn_touch`, `rank_ceremony`, `mission_update`)

| Moment | Visual | Sound |
|---|---|---|
| The Ones That Sing dream (6 s) | The dream overlay: banner silhouettes snapping in wind, a crystal glow inside stone. Damage cuts it short. | `story.ones_that_sing` bed |
| Calling, holocron | Pale motes along the direction, tinted by kind (warm gold, rust, brass), brighter when closer. | `story.calling_holocron` |
| Calling, contract / outpost | A thin drift of ash-grey motes at eye height / faint blue-white or crimson motes by army. | the M1 Calling chime (pitched down for a contract) |
| Calling, Reliquary | Deep amber glints; a hum that grows with closeness. | `story.calling_reliquary` |
| Calling, Courier's Fall | `falling_ember` streaks slanting down toward the site. | `story.calling_fall` |
| Site discovered | A toast. | `site.discovered` (3 variants) |
| Lesson, mission, chapter, boss, rank 3 | The M2 flourish and banner. | `story.lesson_complete`, `mission.complete`, `story.collector_fallen`, `story.rank_three` |
| Cairn touched | The cairn settles into dust; one mote rises toward the player. | `story.cairn_touch` |
| Rank ceremony | Knighting: Saelen's lit blade touches each shoulder, a slow hum, drifting dust. Naming: the writ's seal cracks, then the old-name tablet breaks in two with a dust puff. Pathfinder: Tessaly Arn's figure projects from the Compass and nods. | `story.blade_touch`, `story.tablet_break`, `holocron.gatekeeper_form` |
| Mission accepted or updated | HUD only. | `mission.accept`, `mission.update` |

## Force Sense and Danger Sense (`force_sense`)

| Phase | Visual | Audio |
|---|---|---|
| Cast | Pose `SENSE` for 10 ticks (two fingers to the temple). Others see the gesture and a breath of dust. | `force.sense.cast`; the site and cave beds dip 30% for 20 ticks |
| Inhale (0 to 15) | 2 `sense_mote`s every 4 ticks rise around the waist and drift outward (8). | |
| Streams (from 6) | `sense_stream` specks from the chest to each point, nearest first (up to 24). | |
| Clusters | Waves of `sense_mote`s drifting at chest height at each point, tinted hostile ember, wary amber, neutral grey-white, allied pale blue, blocks pale gold. No outline. | `force.sense.reveal` per hidden point (at most 6 per cast, 3 ticks apart) |
| Holocron | Three pulses 10 ticks apart, 4 kind-tinted motes each, along the direction; a `calling` follow-up within 40 ticks on a cold cast. | `story.calling_holocron` (quiet) |
| End | At 160 ticks (240 with Seeker) the clusters stop; death, respawn, a level change or a disconnect clear everything at once. | |

**Danger Sense** (client only): an attack tell from `DangerTellTable` within 12 blocks and outside the 100° front cone plays `force.sense.danger` (a dry prickle) from the tell's side and shows dust specks at that screen edge (`DangerEdgeHud`), at most once per 20 ticks per source.

## Telekinetic Lift (`force_lift`, `force_lift_target`)

| Phase | Visual | Audio |
|---|---|---|
| Start | Pose `LIFT`: the free arm raised, palm up, trembling; 3 bent-air ripples at the palm. | `force.lift.start`; `force.lift.loop` starts |
| Rise (10 ticks) | The target rises 2.5 blocks (1.2 if resistant) and turns slowly; a humanoid flails. | |
| Held | Staggered `lift_shimmer` wisps in the column under the feet (never a disc), dark pebbles dropping, pale dust sifting, a ripple off the palm every 6 ticks. | `force.lift.loop` |
| Slam | 10 irregular dust puffs of varied reach and height (gaps, never a ring), 8 debris of the landing block, a 0.1 shake within 5 blocks. | `force.lift.slam` |
| Hurl | 8 faint `push_streak`s along the throw and a bent-air ripple. | `force.lift.hurl` |

**Mending Breath** (Heal node): dull grey-green wisps leave the cleansed body and thin as they rise; the Heal channel sounds carry it.

## Holocrons (`holocron_attune`, `holocron_gatekeeper`, `holocron_choice`, `holocron_echo`, `archive_choice`)

| Moment | Visual | Audio |
|---|---|---|
| Attune | The holocron rises from the cradle, its seams open, alcove dust drifts upward. | `holocron.rise`, `holocron.open` |
| Gatekeeper | A narrow spill of light from the seams widens into a thin, slightly translucent figure 2 blocks beyond, tinted by kind, with faint `projection_band` banding (steady with flashing off); `holocron_mote`s only at its edges while it forms and folds back. Escape or damage folds it and the holocron sinks. | `holocron.gatekeeper_form`, `holocron.hum` (loop), `holocron.gatekeeper_fade` |
| Seal / Break / Listen | Seal: the seams close with a dust exhale. Break: the pale stone splits along its gold seams, warm light escapes and gutters out, chips and gold dust fall. Listen: motes flow from the holocron into the chest. | `holocron.seal`, `holocron.break`, `holocron.listen` |
| Echo | A faint gatekeeper silhouette, then a shard forms in the cradle. | `holocron.echo` |
| Archive | Return: the three holocrons dim and settle into a case, the clasp closes. Keep: their seams warm and motes drift into the player. Unmake: the seams flare, light drains out as dust, the shells crack and slump (a steady glow with flashing off). | `holocron.archive_return`, `holocron.archive_keep`, `holocron.unmake` |

## Troopers and armour (`trooper_*`, `grenade_*`, `flare_call`, `kolto_heal`, `armour_set`)

| Moment | Visual | Audio |
|---|---|---|
| Charge (12 ticks; tell) | Muzzle glow tinted by bolt style. | `trooper.republic.charge`, `trooper.imperial.charge` |
| Fire | Muzzle flash by style. Republic: blue-white bursts of 3 bolts 4 ticks apart. Imperial: one slow crimson bolt with a lower thump. | `trooper.republic.fire`, `trooper.imperial.fire` |
| Vent | Heat shimmer and `vent_steam` from the rifle vents after the third burst. | `trooper.vent` |
| Kolto | Pale teal `kolto_mist` around the patient's chest. | `trooper.kolto` |
| Tend and stand | Hands at the downed trooper's chest, a little teal vapour every 20 ticks; it pushes up on one arm and rises, dust falling from the armour. | `trooper.tend`, `trooper.stand` |
| Rally | A raised fist, gold `flare_ember` motes, a dust scuff. | `trooper.rally` |
| Flare | A crimson flare climbs about 20 blocks trailing embers and smoke and hangs 40 ticks (steady with flashing off). | `trooper.flare` |
| Grenade tell (20 ticks) | Arm drawn back, a blinking indicator (steady with flashing off). | `grenade.arm` |
| Grenade fuse (40 ticks) | A quickening blink and thin smoke curling from the landed grenade, so the player can see where it lies. | `grenade.fuse` (rising ticks) |
| Grenade burst | Dust and `grenade_smoke`, sparks, debris of the ground block, a brief pressure shimmer; no ring decal; the glow fades without flashing when flashing is off. | `grenade.burst` |
| Grenade redirected | A bent-air streak (`vent_steam`, pale blue) along the new path. | the power's own sound |
| Bark, salute | The bark line above the hotbar with a role tag (hidden with `showBarks` off); the salute pose. | `trooper.radio` |
| Hurt, death | Vanilla hurt flash. | `trooper.hurt`, `trooper.death` |
| Armour set completed | About 12 `flare_ember` particles at the shoulders (a cloth or clasp flourish) and a toast for 70 ticks. | `armour.set_bonus`; `armour.equip_trooper` or `armour.equip_robe` on equipping a piece |

## Boss, sentinels and saber (`sentinel_*`, `boss_*`, `collector_resolve`, `pylon_state`, `npc_deflect`, `hoard_open`)

| Moment | Visual | Audio |
|---|---|---|
| Sentinel wake / bow | Wake: the lumen ignites, stone dust sheds from the shoulders. Bow (own side): the head lowers, a little dust falls. | `sentinel.wake` |
| Sentinel slam | Tell: arms raised, dust shedding. Impact: debris along the 90° arc, never a ring (at most 35). | `sentinel.slam_tell`, `sentinel.slam` |
| Sentinel core | The chest plates part and the lumen brightens (steady) for 40 ticks. | `sentinel.core_exposed` |
| Sentinel lance | Charge: motes drawn into the lumen. Fire: an amber-white heavy bolt. | `sentinel.lance_charge`, `sentinel.lance_fire` |
| Sentinel death | The lumen gutters out; the plates slump into stone and bronze debris. | `sentinel.death` (`sentinel.hurt` on hits) |
| Hurl | A chunk tears loose with grit; the throw; ripped down with a dust burst; returned along a bent-air streak; caught in bent air. | `boss.collector.hurl_lift`, `_throw`, `_rip`, `_return` |
| Palm | Dust drawn to the palm; an air shove; the M2 clash burst. | `boss.collector.palm_tell`; release and clash reuse the acolyte shove sounds |
| Lash | A seam of floor dust lifts toward the target; then a ridge travels at 0.8 blocks per tick. | `boss.collector.lash_tell`, `boss.collector.lash_wave` |
| Surge | Gather: floor dust and grit stream inward, his cloak streams. Release: an irregular wall of `surge_dust` and debris with gaps, clipped by blocks on the client (cover behind columns shows). Clash: the wall breaks around the clashing player. | `boss.collector.surge_gather`, `_surge_release`, `_surge_clash` |
| Spent | He sags; the holocron's light gutters; dust settles. | `boss.collector.spent` |
| Grip | Pebbles and dust rise around the target; the held player strains; a grit burst on the break. | `boss.collector.grip`, `_grip_hold`, `_grip_break` |
| Lunge | A crouch with dust kicked back; a streak. | `boss.collector.lunge` |
| Ward and pylons | Sparse `ward_haze` specks, denser with more pylons singing; `ward_current` motes from each singing pylon. Pylon hit: crystal chips. Silenced: the crystal cracks dark and glints fall. Regrow: it grows back. Unsung: the currents snap back, he reels off the dais, ceiling dust. Shatter: shards fly into him. | `boss.collector.ward_hum` (loop), `pylon.sing` (loop), `pylon.crack`, `pylon.silence`, `pylon.regrow`, `boss.collector.unsung`, `pylon.shatter` |
| Phases | Banners and the boss bar; ceiling dust, glints or shards streaming into him, camera shake. | `boss.collector.transition`, the music |
| Yield, leash | He kneels, the blade retracts, crystal light dims. Leash: a smoke puff where he was, smoke and settling dust at the dais. | `boss.collector.yield` (music drops to a drone), `boss.collector.leash` |
| Echo | Smoke gathers from the floor over 60 ticks into his desaturated shape (`echo_smoke`; the form event is sent around the dais); at 0 health he drains into the floor as smoke. | `boss.collector.echo_form`, `boss.collector.echo_fade` |
| Endings | Custody (0, 3, 5): troopers come down, binders lock, he is walked out. Spare (1): he sets his blade on the dais and walks out alone; for a Sith Spare the blade shows as a steady glow. Execute (2): a single stroke, he falls forward, his amber blade rolls away and gutters out (no gore). Release (4): he picks up his satchel and walks out. | `boss.collector.binders`, `boss.collector.execute`, `boss.collector.walk_out` |
| Hoard | The vault seal crumbles; motes rise from the three holocrons, from the plinth toward the vault seal. | `hoard.open` |
| NPC deflection | A blade sweep with sparks at the blade. | the M1 deflect sound |
| Singing crystal | An amber blade; a crystal layer over the hum. | `saber.hum.singing` (layer at 0.45) |

## Loops and watchdogs (Milestone 3)

- **Site beds** (`ambient.jedi_ruin`, `sith_tomb`, `wreck`, `outpost`, `reliquary`): fade out over 60 ticks on leaving or death; stop at once on a dimension change; a disconnect kills them.
- **`story.ones_that_sing`**: stops with the dream, at 6 s or on damage.
- **`force.lift.loop`**: stops with the Lift pose, on the `force_lift` end event, or by watchdog at the hold plus 30 ticks (130 for one's own prediction); death, removal, a level change or a disconnect clear it.
- **`holocron.hum`**: stops when the figure folds, by watchdog, beyond 16 blocks, at 2,400 ticks, on death, a level change or a disconnect.
- **`boss.collector.ward_hum`** and **`pylon.sing`** (at most 4): stop on death, beyond 48 blocks, a level or dimension change, a disconnect, or a music-stop event; watchdogs stop any loop that outlives its state, and a pylon's song restarts when the player comes back within 48 blocks.
- **Boss music** (`music.collector.archivist`, `singing_wall`, `unkept`): streamed in the MUSIC source, crossfades over 40 ticks between phases, drops to a drone at the yield, holds vanilla music off while it plays, and stops on `boss_phase` 6. `ClientConfig.bossMusic` turns it off.
- **`saber.hum.singing`** follows the saber hum and stops with it.
- Concurrency limits: `trooper.*.fire` ≤ 3 per squad per 4 ticks (server); `grenade.burst` ≤ 2 per 10 ticks; `grenade.fuse` ≤ 2 at once; `trooper.tend` 1 per tending player; `pylon.sing` ≤ 4 loops; `force.sense.reveal` ≤ 6 per cast; `trooper.radio` follows the bark rate limits.
- Loop stopping is not checked by a test (CI has no audio).
