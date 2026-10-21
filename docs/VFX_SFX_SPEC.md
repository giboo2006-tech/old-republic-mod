# VFX and SFX specification

Every feature defines its visual and audio treatment here before it ships. The Milestone 1 sections come first, then the Milestone 2 section, then the Milestone 3 section (merged from `docs/MILESTONE_3.md` section 13 and the features' design docs at integration), then the Milestone 4 section (the same way, from `docs/MILESTONE_4.md` section 13). IDs are the contract between code, assets and `sounds.json`. All sound IDs are in the `oldrepublic` namespace; all are **placeholder** audio synthesised by `tools/assets/gen_sounds.py` until replaced (see `ASSET_MANIFEST.md`).

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
- Blade (rebuilt October 2026; it was a stack of model boxes): drawn in code (`SaberBladeRenderer`, `GlowRibbon`) as a smooth capsule turned to face the eye, a white core with a whisper of the blade colour that turns to the colour at its edge, inside a soft glow of the blade colour (added light, strongest at the centre line, gone at the edge), with a slow shimmer along the blade; full-bright. No real light is cast on the world (vanilla has no dynamic lighting; noted in KNOWN_ISSUES).
- Ignition/retraction: the drawn blade shoots out over 6 ticks, fast at first and settling at full length (smooth between ticks), with a flash at the emitter and a flare at the moving tip; it retracts over 6 ticks, slowly at first and snapping in at the end. The inventory icon switches half way (the `blade_extension` item property).
- Held (October 2026). First person, posed in code (`SaberFirstPerson`, instead of the vanilla held-item transforms): at rest the hilt low on the right and the blade angled up toward the crosshair; each attack a fast cut through the crosshair from wherever the blade is, a forehand from up on the right down to the lower left, then, while the blade is still there, a backhand back up; the follow-through held 0.22 s, then an ease back to rest (time constant 0.3 s); guard brings the blade across the body; the finisher, heavy strike, lunge and salute have their own motions; the saberstaff rests flatter (both blades in view) and twirls a half turn on each attack. Third person: the blade tilted 22° up from forward in the fist at 0.68 scale; a lit saber with no form stance held ready (arm forward, the blade up and forward at the sword-arm side, clear of the face); a lit saberstaff held in both hands, diagonally across the body (crosswise in the fist); every stance eases into a swing and back.
- Audio: `saber.ignite`, `saber.retract`, `saber.hum` (loop, positional, stops on retract, drop, death, disconnect, dimension change, item switch; since October 2026 its pitch rises by up to 8% and its volume by up to 30% with the blade's speed), `saber.swing` (4 variants, pitched to the swinging blade's hum), `saber.clash` (3 variants), `saber.deflect` (3 variants), `saber.parry` (bright ring), `saber.guard_break`, `saber.hit` (flesh/armour sizzle). All rebuilt in October 2026 around one film-style hum (AUDIO_BRIEF).
- Clash/deflect (October 2026; it was square `saber_spark` particles and a `contact_flash` sprite): drawn in code as light (`SaberSparks`, on the energy renderer) at the actual contact point. Each spark is a short streak along its flight, white-hot at first and cooling to the blade colour as it falls, that bounces off the floor and lives 7 to 13 ticks: a clash sprays 14 back from the contact, a deflection 12 along the bolt's new path, in a fan wider than the caller's cone. The contact is a burst of light that dies away over 4 ticks, or a soft glow that never flashes when flashing effects are off. The welding arm and fusion cutter, a Masterwork or Legendary craft, the stun grenade and an electrostaff blocking a saber use the same sparks and light.
- Swing trail (`BladeTrails`, October 2026): where each drawn blade really was over the last 90 ms (read from the pose it is drawn with, so it follows every swing, stance, technique and twirl), swept into a ribbon that follows the arc the blade turned through between frames: a glow of the blade colour fading fast behind the blade and toward the hilt, with a paler smear of the core just behind it. Only where the tip moves faster than 2.5 blocks a second (full at 9), so a still or carried blade leaves nothing. In first person it is kept relative to the view, so turning the head never smears it.
- Saber Throw (October 2026): the flying saber has the held blade's look on a short grey hilt, spinning 20 turns a second, with its sweep fading round behind it as a disc of light.

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
| saber | `blade_glint` | the NPC wind-up tell at the blade tip, tinted with the blade colour (unused since October 2026: drawn as light by `SaberSparks.glint`) |
| saber | `lunge_streak` | low streak of the blade colour along the lunge path (unused since October 2026: drawn as light by `SaberSparks.streak`) |
| identity | `rope_fibre` | frayed rope strands when captives are cut loose |
| story | `voice_warm` | the warm voice of Calling variant 2 (pale gold glints) |
| story | `voice_dust` | the cold voice of Calling variant 2 and the mentor's departure puff |
| story | `ash_mote` | Calling variant 3 (smoke on the ridge) |

Reused: `force_mote` (Heal, recoloured warm; rank-up motes), `air_ripple` and `gather_dust` (Pull, Jump), the saber sparks (`saber_spark`, drawn as light by `SaberSparks` since October 2026) and vanilla block particles for debris.

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
- **Lunge:** a dust kick at the start (10; soft dust of the ground's colour since October 2026, as the Force powers raise it), eight streaks of blade light along the dash (`SaberSparks.streak` since October 2026: white-hot, cooling to the blade colour, running on a little and fading in 6 to 8 ticks; they were `lunge_streak` particles), `saber.lunge`; hit sparks in the blade colour on a hit (10), a small dust puff (6) on a miss.
- **Forms:** the hilt is raised in a salute pose for the length of the change, with `saber.form_change` (a hum-pitch shift).
- **Spar:** begin and end stings `story.duel_begin` and `story.duel_end`.
- **Any living entity:** saber audio and trails now follow every living entity within 32 blocks that holds a lit saber (mentors, the Acolyte). The hum of non-players plays in the `HOSTILE` source. Trails are third person only and use the entity's main arm.

## NPC saber duelling (`npc_windup`, `npc_guard_break`)

- **Wind-up tell** (never reduced by presets): the blade drawn back, and at the blade tip a glint of light in the blade colour that opens into a four-pointed star and closes over 8 ticks, moving with the NPC (`SaberSparks.glint` since October 2026; it was a `blade_glint` particle). It is drawn as light, which the particle presets never thin. `saber.npc_windup` plays on the first swing of a string.
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

Reused: `force_mote` (Chapter Three), `crystal_glint` (pylons and shards), `air_ripple` and `push_streak` (Lift), the saber sparks (deflects; drawn as light by `SaberSparks` since October 2026) and vanilla block particles for debris.

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

---

# Milestone 4

From `docs/MILESTONE_4.md` section 13, the six areas' design docs and the client handlers as built. All six areas are merged: W1 worlds, W2 Chapter Four, vendors and economy, W3 ships and travel, W4 gear, W5 crafting and saber, and W6 Force Speed, Barrier and Choke, the foes and Kaast. The full treatments are in `docs/design/planets.md` section 4 (copied below unchanged), `docs/design/chapter_four.md` section C7, `docs/design/travel.md` sections 10 and 11, `docs/design/gear.md` sections 9 and 10, `docs/design/crafting.md` section 8, `docs/design/saber.md` section 35, `docs/design/force_m4.md` section 7 and `docs/design/foes.md` section 10; the per-event looks for artists are in `docs/assets/{planets,chapter_four,vendor,travel,gear,crafting,saber,force_m4,foes}.md`. Sound ids, files, subtitles and briefs are in `AUDIO_BRIEF.md`. None of these effects has been seen by a person yet; the client screenshots `oldrepublic_m4_10` to `75` show most of them once.

## Rules (Milestone 4)

- The art direction holds: bent air, dust, debris, pressure and light; **no ground circles, sigils, halos, glyphs or glow outlines**. Anything that spreads along the floor (ship dust, the stun grenade's kick, the Heartstone's mist) is irregular puffs with gaps.
- Tells are never thinned (`Vfx.tellCount`); every other count goes through `Vfx.count` and `Vfx.inRange`. W6's tells (the hunters', the droid's, the beasts' and Kaast's) and the wrist flamer are drawn in full at every effects setting; no other Milestone 4 effect is a tell.
- The new Force powers show only through dust, grit, leaves and bent air: no glow, shell, tendrils, ground circle, halo or outline. The only red light on a battlefield is the hunter rocket's targeting dot; mines, Rattle's scan line and the droid's eye are amber or pale.
- Flashing off replaces flicker and flashes with steady glows: the beacon blinks, the ship's running lights, the hyperspace pulse, the heat gauge during a lock, the aurelite glints, the stun grenade's flash. A block texture animation cannot read the setting, so the beacon lamp's own texture is a slow interpolated pulse that is safe with flashing off.
- Shakes go through the shake setting (`CameraShake`, `Vfx.shakeScale`).
- Every loop stops on cancel, death, disconnect, dimension change, leaving the area, and item or slot change, with a client watchdog behind it (see "Loops and watchdogs (Milestone 4)").
- New client options: `planetAmbience` (biome beds, hub beds, ashfall), `shipCamera`, `hyperspaceOverlay` (off: a plain dark fade over the same moments) and `blasterReticle`, all on by default.

## Particle budgets per active instance (Milestone 4, Standard)

| Effect | Budget (spec 13.1) | As built |
|---|---|---|
| Blaster muzzle flash and sparks (player) | 4 per shot; the bolt uses the M1 renderer | 1 muzzle glow and 2 sparks (3 for the charged shot) |
| Overheat vent | 12 steam puffs over 20 ticks | 4 jets at once, then 8 that follow the gun |
| Ship engines | idle (READY) 2 per tick; climb and descent 8 per tick; ground dust at take-off and landing 60 over 40 ticks | Exhaust: one puff per nozzle every 14 ticks while READY on the ground, 2 per nozzle per tick in the Climb and the Jump, 1.2 per nozzle in the Descent. Dust: in each gust window (Spool 40 ticks, Climb 14, Settle 18) every tick has a 45% chance of a puff of 3 to 6, about 80 over the Spool, and the descending ship kicks dust at a 50% chance per tick below 8 blocks. The take-off dust is above the spec's 60; check it in play |
| Ship damage (below 40 hull) | sparks 1 per 10 ticks, smoke 1 per 4 ticks | 3 sparks at a 1 in 24 chance per tick on the ground (1 in 8 in flight); 1 smoke every 10 ticks on the ground (every 2 in flight); below 20 hull also 1 seam smoke every 6 ticks. The in-flight rates are above the spec's |
| Hyperspace overlay | screen-space streaks, 40 at Standard, 15 at Reduced | 40 at Standard, scaled by the preset between 15 and 90 |
| Force Speed | 2 per footfall, 6 streak motes per second | 2 dust motes in the ground's colour per footfall (one every 1.4 blocks) and 6 faint bent-air streaks a second at the ankles and wrists |
| Force Barrier | 24 held dust motes, 6 per impact | Up to 24 `held_dust` motes on the sphere; 6 shaken along the surface per impact, with a bent-air ripple at the point |
| Force Choke | 10 grit motes per second under the target | 10 grit motes a second lifted from the ground under the target, and a slight bent-air tremor at the neck and shoulders |
| Jet pack burst | 14 flame and smoke over 10 ticks | 6 at once, then 8 over the next 8 ticks |
| Wrist flamer | 30 per second (a tell, never thinned) | 2 `flame_jet` motes a tick (40 a second) and a puff of smoke on one tick in three, never thinned. Above the spec's 30; check it in play |
| Ashfall (client) | 40 visible flakes at Standard | 40 in the ashfall biome, twice that in a storm (see W1 below) |
| Waystone corruption | 6 dark-vein motes per second within 8 blocks | 6 per second within 8 blocks, in a pulse of 12 ticks in 40 |

## New particle types (8)

| Owner | Particle | Use |
|---|---|---|
| W1 | `ash_flake` | Varkhast ashfall |
| W1 | `aurelite_glint` | aurelite veins at air faces, Hollow chimes and the answer |
| W1 | `duskglass_shard` | duskglass clusters and the rift bleed |
| W3 | `engine_exhaust` | nacelle exhaust by phase, the jump streak, the fuel filler puff |
| W3 | `spark_shower` | repair and weld sparks, nacelle damage sparks |
| W4 | `steam_vent` | blaster overheat, vent and perfect vent |
| W4 | `flame_jet` | the jet pack burst (W6's hunter flamer and jet hop use it by id) |
| W6 | `held_dust` | the Barrier's drifting dust and leaves, tinted in code by the ground below the caster |

Reused: the enemy feature's `muzzle_glow`, `bolt_impact` and `flare_ember`, the Force feature's `lightning_spark`, the saber feature's sparks, contact flash and lunge streaks (drawn as light by `SaberSparks` since October 2026), the world feature's `vent_grit`, and vanilla poof, smoke, cloud, end rod, dust and block particles.

## Effect events (Milestone 4)

Each row is one gameplay moment, one `EffectEventPayload`. Delivery and fields are in spec 13.3; W4's extra variants (4 to 7 of `player_blaster_fire`, 2 and 3 of `vibro_hit`, 3 to 5 of `electrostaff`) are personal feedback and loop stops.

| Area | Ids |
|---|---|
| worlds (W1) | `waystone` (corrupted ambience, cleanse), `heartstone` (wave 1 to 3, complete), `crystal_answer` (Hollow chime 0 to 3, rift bleed, the Hollow answers) |
| Chapter Four (W2) | `seeker_scan` (0 scanning, 1 leaving); `lesson_complete` gains 8 (Chapter Four complete), 9 (a planet's name) and 10 (Masterwork) |
| ships and travel (W3) | `ship_engine` (idle, spool, climb, descent, settle), `ship_jump` (out, in), `hyperspace` (start, end), `ship_repair` (sockets 0 to 3, weld, stand up, fuel, upgrade), `ship_damage`, `landing_beacon` (set, refuse, recall) |
| gear (W4) | `player_blaster_fire` (pistol, rifle, repeater, charged, hit, deflected, charge ready, charge start), `blaster_heat` (overheat, vent, perfect), `repeater_spin` (up, down), `vibro_hit` (flurry, cleave, guard, notch), `electrostaff` (charge, shock, block saber, charged, spent, sweep), `jet_pack` (burst, empty); `armour_set` (the M3 flourish) for the four new sets |
| crafting and saber (W5) | `mod_fit` (fit, remove), `bench_craft` (start, done, Masterwork, stop, tool cut), `salvage`, `consumable` (medpac, kolto, stim, crash), `scanner_pulse`, `grenade_player` (throw, stun), `saber_staff` (second blade, whirl), `lens_fit` |
| Force powers (W6) | `force_speed` (0 start, 1 end, 2 burst), `force_barrier` (0 start, 1 end, 2 pulse, 3 break), `force_barrier_impact` (0 bolt stopped, 1 bolt returned, 2 other projectile, 3 blast, 4 melee), `force_choke` (the caster: 0 start, 1 release, 2 crush), `force_choke_target` (1 held, 0 dropped) |
| foes and Kaast (W6) | `hunter_tell` (0 jet, 1 flamer, 2 rocket, 3 net, 4 charge, 5 axe), `hunter_attack` (0 jet, 1 flame, 2 rocket, 3 net), `droid_tell` (0 spin, 1 shield, 2 death), `beast_tell` (0 hollowhorn charge tell, 1 hound lunge tell, 2 howl, 3 hollowhorn run, 4 hollowhorn impact, 5 hound leap), `kaast` (0 phase, 1 mine arm, 2 mine live, 3 slam tell, 4 slam, 5 yield, 6 helmet, 7 mine sensed, 8 music, 9 clash, 10 echo, 11 burn off, 12 mine burst) |

## Worlds (W1): `waystone`, `heartstone`, `crystal_answer`, particles and ambience

Copied unchanged from `docs/design/planets.md` section 4 (the lead's decision); that section stays the source.

All particles go through `client/fx/Vfx` (`inRange`, `count`); with flashing off, glints become steady motes; the
only shake (a Heartstone wave) goes through `CameraShake`, which applies the shake setting. Nothing draws on the
ground: mist and dust rise from irregular vents with bare ground between them. Server-authoritative: every event is
sent through `network/EffectEvents` (`around`, or `toPlayer` for the per-player corrupt refresh).

### `waystone` (around; 0 corrupted ambience, 1 cleanse)

| Phase | Look | Sound |
|---|---|---|
| Anticipation (the stone is corrupted for you) | The server refreshes every 40 ticks (`toPlayer`, intensity = keep-alive 3 s) to each player within 16 blocks it is not cleansed for. Dark-vein motes (dust `#2A1220`) seep from the open faces of the stone's column in a slow pulse: 12 ticks of every 40, 6 per second, within 8 blocks | `waystone.corrupt`: a seamless 4 s low beating drone with a throb every 2 s and grit, faded in over a second |
| Activation (a Sense cast cleanses it) | Black grit (falling dust of blackstone, 14) sifts off the stone's faces | `waystone.cleanse`: the darkness drains as a falling filtered rumble while a clear tone rises |
| Travel | For 20 ticks the veins drain: dark dust slides down the four faces into the ground | |
| Impact | At tick 16 the stone's own pale motes rise (10) | `waystone.hum`, the stone's own voice returning |
| Aftermath | The clean stone: a pale mote now and then and its hum, rarely by day (1 in 90 display ticks), more often at night (1 in 40) | `waystone.hum` |

Stop rules: the corrupt loop fades for the cleanser only (the cleanse names the player), lapses 3 s after the last
refresh (leaving 16 blocks), is released at once beyond 20 blocks, on death, and is killed on disconnect and on level
change. The cleanse plays for everyone near; other players keep their own loop.

### `heartstone` (around; 0 wave 1 to 3, 1 complete)

| Phase | Look | Sound |
|---|---|---|
| Anticipation (the stone is corrupted for the nearest player) | Slow dark mist curls from the stone's base while a corrupt refresh names it (the Heartstone sends the `waystone` refresh too) | `waystone.corrupt` loop |
| Activation (a wave starts, W2 calls `Heartstones.wave`) | A ragged burst of dark mist (10 to 18) at 3 + wave vents around the base; a light shake within 12 blocks, stronger each wave | `heartstone.wave`: a deep surge, a sub thump and a dissonant swell, louder and lower each wave |
| Travel | For 2 s the mist keeps welling at the same vents (fixed per wave, so it comes in uneven patches), densest early | |
| Impact (complete, `Heartstones.complete`) | The wave's mist stops at once | `heartstone.cleansed`: a long exhale, a reversed swell opening into a warm open chord |
| Aftermath | For 4 s pale motes leave the stone's faces and drift up and away, swelling and fading like a breath | |

### `crystal_answer` (around; 0 Hollow chime 0 to 3, 1 rift bleed, 2 the Hollow answers)

| Phase | Look | Sound |
|---|---|---|
| Activation: a chime (tone 1 to 3) | Aurelite glints (5 to 9, or 2 steady motes with flashing off) and rising crystal motes on the cluster | `hollow.chime` pitched by tone: 0.794, 1, 1.26 (a third, the root, a third up) |
| Wrong stone (tone 0) | A little stone grit falls | `hollow.chime` at pitch 0.5, quieter: a dull clunk |
| Impact: the Hollow answers (variant 2) | For 2 s glints run over every crystal cluster and the open faces of aurelite within 10 blocks (at most 48 points) | `hollow.answer`: a cascade of chimes settling on an open chord |
| Rift, anticipation (BEGIN) | While held: the crystal seeps red that darkens from bruise to blood (dust, lerped), and black slivers are drawn in from the walls every 3 ticks | `rift.whisper` every 2.5 s, louder as the bleed deepens |
| Rift, impact (DONE) | A burst of black slivers (18) up and out, red drops (14) and dark motes (8) | `rift.bleed`: a glass crack, a groan sliding down and a dark bloom |
| Rift, broken (BROKEN) | Everything stops | the whisper stops at once |

The bleed is found by its holder's entity id (a break is sent from where the holder stood), and lapses on its own
100 ticks after the hold should have ended if a "done" is lost.

### Particles

| Type | Look | Where |
|---|---|---|
| `ash_flake` | Grey flakes, 3 sprites, translucent; carried by the wind with a slow sway, fall capped, fade on landing; 90 to 150 ticks | Varkhast ashfall |
| `aurelite_glint` | A gold four-point twinkle, 4 frames, full-bright | Aurelite veins at air faces, Hollow chimes and answer |
| `duskglass_shard` | A black glass sliver with a red edge, 3 sprites, tumbling, falls, fades late | Duskglass clusters, the rift bleed |

### Ambience

- **Ashfall** (Varkhast, client): flakes start around the camera under open sky only, upwind so the drift carries them
  past: 40 alive at Standard in the ashfall biome, twice that in the storm (the level's rain flag, which never wets
  Varkhast), half the rate elsewhere on Varkhast where they land only over nearby ashfall, or a quarter of them anywhere
  during the storm. The wind turns over five minutes and gusts each minute. Scaled by `planets.ashfallDensity`, the
  preset and the `Vfx` budget; follows `planetAmbience`.
- **Biome beds** (client): one non-positional loop per biome (`ambient.selvaren.*`, `ambient.varkhast.*`, 12 s
  seamless), crossfaded over 4 s as the player crosses biomes, ducked to 0.35 under cover and 0.4 under a site's own
  bed, dipped by Sense; released on death, stopped on dimension change, killed on disconnect; off with `planetAmbience`.
- **Hub beds** (`SiteAmbience`): `ambient.greenwatch` and `ambient.proving` while in the hub (site presence), off with
  `planetAmbience`. Greater tombs use the M3 tomb scenes (surface at the gate, underground below); the Sepulchre and
  the rift the tomb's underground scene; the Hollow's grotto the ruin's underground scene.
- **Block display ticks**: duskglass slivers fall and dull red catches in the glass; aurelite glints at air faces; the
  clean waystone's pale mote and hum; the Heartstone's mist while corrupted for you.
- **Music**: `music.selvaren` (an open Dorian pad and a sparse bell line, 48 s) and `music.varkhast` (a low Phrygian
  drone, a frame-drum pulse and a severe low line, 48 s) through the dimension type's `background_music`.

### Final sound of each event

| Event | Sound(s) | Category |
|---|---|---|
| `waystone` 0 | `waystone.corrupt` (loop) | blocks |
| `waystone` 1 | `waystone.cleanse`, then `waystone.hum` | blocks |
| `heartstone` 0 | `heartstone.wave` | blocks |
| `heartstone` 1 | `heartstone.cleansed` | blocks |
| `crystal_answer` 0 | `hollow.chime` (pitched by tone; 0.5 for a wrong stone) | blocks |
| `crystal_answer` 1 | `rift.whisper` (repeated while held), `rift.bleed` on done | blocks |
| `crystal_answer` 2 | `hollow.answer` | blocks |

All 19 W1 sounds are original placeholders synthesised by `tools/assets/sounds/world_m4.py` (see
`docs/assets/planets.md`); render with `python3 tools/assets/gen_sounds.py --m4 --feature world_m4`.

## Ships and travel (W3): `ship_engine`, `ship_jump`, `hyperspace`, `ship_repair`, `ship_damage`, `landing_beacon`

| Moment | Visual | Audio |
|---|---|---|
| Idle (`READY` on the ground) | A lazy puff of exhaust from each nozzle every 14 ticks; the running lights blink slowly (steady with flashing off). | `ship.idle` loop at 0.5 |
| Spool (`ship_engine` 1) | The exhaust rate rises over the 40 ticks; dust gusts from under the ship in uneven puffs; struts fold, the ramp closes; a small shake for riders. | `ship.spool`, `ship.ramp`; the idle loop rises |
| Climb (2) | Full exhaust from both nozzles; more dust for 14 ticks; a shake for riders; the third-person ship camera (`shipCamera`). | `ship.climb` |
| Jump out (`ship_jump` 0) | 36 exhaust motes pulled into a streak ahead of the ship; a shake for riders. Then the overlay. | `ship.jump_out` |
| Hyperspace (`hyperspace`, riders) | Screen-space star streaks stretching into a blue-white tunnel with a slow pulse (no pulse with flashing off; fewer streaks on Reduced), a dark vignette, the flight HUD fading; also drawn over the level loading screen. It ends after the Hyperspace phase or when the level is ready, whichever is later, and always ends (off the ship for 100 ticks, a 600-tick safety, disconnect). With `hyperspaceOverlay` off, a plain dark fade. | `ship.hyperspace` loop for the rider |
| Jump in (`ship_jump` 1) | The streak trailing behind the ship as it arrives; a lighter shake. | `ship.jump_in` |
| Descent (3) | Exhaust at 1.2 per nozzle per tick; struts down at 6 blocks; dust as the ground nears (below 8 blocks). | `ship.descent` |
| Settle (4) | Dust for 18 ticks; the exhaust dies away; a shake for riders; the destination banner. | `ship.land`, `ship.ramp` |
| Repairs (`ship_repair`) | Socket fitted (0 to 3): `spark_shower` sparks (12 for the hull socket, 8 for the others) and a clank. Weld (4): 14 sparks and 2 smoke. Stand up (5): steam from the jacks under each wing and 10 sparks as the lights come on. Fuel (6): a short exhaust puff at the filler. Upgrade (7): 6 sparks. | `ship.fit_part` (pitched: core lower, nav computer higher; fuel lower still; upgrade a little higher), `ship.weld`, `ship.power_up` with `ship.ramp` |
| Damage (`ship_damage`, intensity 1 − hull/100) | A burst of 8 to 18 sparks and 4 smoke at a nacelle; a shake for riders. Also played by each jump's wear. | `ship.damaged` once |
| Wear (continuous) | Below 40 hull one nacelle sparks now and then and trails thin smoke; below 20 the hull seam smokes, the nozzle glow stutters, the fin beacon is dead; scorch deepens as the hull falls. | `ship.damaged` loop below 20 hull |
| Beacon (`landing_beacon`) | Set: three green blinks. Refuse: two red blinks. Recall: five amber blinks. With flashing off, each is one steady glow. | `beacon.set`, `beacon.refuse`, `beacon.recall` |

## Gear (W4): `player_blaster_fire`, `blaster_heat`, `repeater_spin`, `vibro_hit`, `electrostaff`, `jet_pack`

| Moment | Visual | Audio |
|---|---|---|
| Blaster shot (`player_blaster_fire` 0 to 3) | A green-white muzzle glow for 3 ticks (larger and white-cored for the charged shot) and 2 short sparks along the shot (3 for the charged shot); the bolt is the M1 renderer with the player palette; the pose kick and a short first-person recoil. | `blaster.pistol_fire` (pistol), `blaster.fire` (rifle), `blaster.repeater.fire` (2 variants), `blaster.rifle.charged`; a shot cuts the charge whine |
| Rifle charge (7 start, 6 ready) | The reticle draws in; when loaded, corner brackets on the reticle and a steady muzzle glow. | `blaster.charge` whine for everyone near (a held sound, cut when the charge is dropped, swapped away or fired); the ready latch for the shooter is `workbench.slot` for now |
| Hit and deflect (4, 5, shooter only) | Reticle markers: an X on a hit (larger and red-orange on a kill), an amber diamond on a deflect. | none |
| Overheat (`blaster_heat` 0) | 4 hard `steam_vent` jets thrown sideways, then 8 more that follow the gun; the heat gauge flashes (steady with flashing off). | `blaster.overheat` |
| Vent (1) and perfect vent (2) | Vent: 5 puffs of steam. Perfect: one large bright puff that swells as it rises and 2 small ones; the gauge's band glows. | `blaster.vent`, `blaster.vent_perfect` |
| Repeater spin (`repeater_spin`) | While spinning, a few faint steel motes circling the muzzle. | `blaster.repeater.spin_up`, `blaster.repeater.spin_down` |
| Vibro (`vibro_hit`) | Flurry (0): 3 steel sparks and 2 flicks of grit at the impact. Cleave (1): 5 sparks spread across the sweep. Guard (2): 3 steel sparks back toward the attacker. Notch (3): 4 hot-orange sparks; a guard break adds 3 grit. | `vibro.flurry`, `vibro.cleave`; the guard clang is `vibroblade.hit` pitched by the stamina left, a notch plays `saber.clash` low, and a guard break `saber.guard_break` |
| Electrostaff (`electrostaff`) | Charge (0): the rising crackle. Charged (3): single arcs crawling at both emitter ends until the shock or the end of the charge. Shock (1): a snap of 8 arc sparks on the target and on the arc's target. Saber held off (2): a contact flash and 6 saber sparks. Sweep (5): 3 flicks of grit beside the struck enemy. Spent (4): a weak fizz. | `electrostaff.charge` (a 2.45 s rise, held sound; `SPENT` cuts it), `electrostaff.shock`, `electrostaff.clash`; the sweep plays `vibroblade.swing` low, and the fizz is `electrostaff.shock` quiet and low |
| Jet pack (`jet_pack`) | Burst (0): 6 `flame_jet` tongues from behind the shoulders along the exhaust, then 8 more over the next 8 ticks from wherever the pack is. Empty (1): the click and one grey wisp. | `jetpack.burst`, `jetpack.empty` (at most every 10 ticks) |
| Set bonus (`armour_set`) | The M3 flourish: the plate sets use the trooper clasp glints, spacer's gear the robe's cloth puff, each with its own tint (Watch green-white, Sith war red, Mandalorian gold, spacer tan) and toast colour. | `armour.set_bonus` (M3), pitched by set |

The reticle, heat gauge, jet pips and vibro guard bar are HUD (`BlasterHud`, `VibroGuardHud`; gear design section 9). They use the default balance numbers.

## Crafting and saber (W5): `mod_fit`, `bench_craft`, `salvage`, `consumable`, `scanner_pulse`, `grenade_player`, `saber_staff`, `lens_fit`

| Moment | Visual | Audio |
|---|---|---|
| Mod fitted or removed (`mod_fit`, to the player) | None; the bench screen shows the slot change. | `bench.mod_fit`, `bench.mod_remove` (as UI sounds) |
| Craft (`bench_craft` 0) | The welding arm spits 1 to 3 sparks every other tick, unevenly, with a wisp of smoke. | `bench.weld` loop at the bench, keyed by the crafter |
| Done (1) | The loop stops; 6 puffs of smoke. | `bench.done` |
| Masterwork (2) | The loop stops; 4 puffs, then a short shimmer of 14 warm gold sparks and 8 motes rising from the item; the Masterwork toast for the crafter. | `bench.masterwork` |
| Stop (3) | The loop stops. | none |
| Fusion cutter (4) | 5 hot sparks at the cut on each event. | `bench.weld`, quieter, kept alive 10 ticks after each event |
| Salvage (`salvage`) | 10 iron crumbs and 8 grit spill on the bench. | `bench.salvage` |
| Consumables (`consumable`) | Medpac, kolto, stim: 3 wisps of vapour at the arm; kolto adds 10 pale blue mist motes. Crash: none. | `medpac.use`, `kolto.inject`, `stim.inject`, `stim.crash` |
| Scanner (`scanner_pulse`, to the player) | 14 pale blue motes along the look direction, with no ring or outline; then for the marks' time, small blue motes over found resources and amber motes over creatures, and arrows at the HUD edge (at most 12). The blocks are surveyed over 40 ticks in slices. | `scanner.pulse`; `scanner.ping` when new marks are found, pitched up with their number |
| Grenades (`grenade_player`) | Throw (0): none (the grenade flies). Stun burst (1): a white contact flash (soft with flashing off), 10 white sparks and 14 dust puffs spread over the radius, with no ring; a small shake within the radius plus 2. The player's frag grenade uses the M3 grenade's fuse and burst effects (`grenade_fuse`, `grenade_burst`) and sounds. | `grenade.throw`, `grenade.stun`; the frag grenade's `grenade.fuse` and `grenade.burst` (M3) |
| Second blade (`saber_staff` 0) | The second blade extends or retracts (the item model). | `saber.staff_ignite`, or `saber.retract` (M1) pitched up |
| Whirl (`saber_staff` 1) | The heavy strike's pose and trail; a spinning disc of blade light (`StaffWhirlFx`, October 2026; it was 14 `lunge_streak`s per blade): both blades sweep a full turn round the wielder at waist height in 9 ticks, fast at first and slowing, each trailing a fan of light in the crystal's colour that fades back round 150° behind it and in toward the wielder, out to the whirl's range, with the smear of the white core just behind each blade and sparks of blade light flung off the tips as they pass (up to one per blade a tick); the disc follows the wielder, fades over the second half of the turn, and is drawn at half strength in the wielder's own first-person view; 10 puffs of soft ground-coloured dust at the feet; a small shake for the wielder. | `saber.staff_whirl` |
| Lens fitted (`lens_fit`, to the player) | A glint on the workbench screen's lens socket. | `workbench.lens_fit`, pitched by lens |

## Chapter Four (W2): `seeker_scan`, `lesson_complete` 8 to 10

| Moment | Visual | Audio |
|---|---|---|
| Descent (50 ticks) | W6's `seeker_droid` comes down from `chapter_four.seekerDescend` blocks above a point 3 ahead of the player and 1.5 above the eyes, easing out; no effect of its own. | none (the droid's own) |
| Scan (`seeker_scan` 0, `toPlayer`, intensity = the scan's ticks) | Seven rays (at least two after the VFX setting) of small red dust motes from the lens toward the player's chest, 14 degrees either side, the scan line sweeping 16 degrees down and up every 30 ticks, a hotter core mote at the lens every third tick. Nothing on the ground, no outline. Played from the hover point when there is no droid. | `seeker.scan` at the lens |
| Leaving (`seeker_scan` 1) | The fan stops at once; the droid climbs and drifts away for 30 ticks and is removed. | `seeker.chirp` twice, 7 ticks apart (pitch 1.05, then 1.25), and `seeker.fly` |
| Chapter Four complete (`lesson_complete` 8, intensity = path) | The M2 banner, "Chapter Four complete", in the path's colour (Jedi gold, Sith red, Independent amber). | `story.chapter_four` |
| A planet's name (`lesson_complete` 9, intensity = planet) | The world's name over its subtitle in the world's colour (Selvaren green, Varkhast rust), on the first landing. | `planet.arrive` |
| Masterwork (`lesson_complete` 10, intensity = path) | The banner "Masterwork" in the path's colour. | `story.lesson_complete` (M2) |
| The marker beacon (block display ticks) | One small red dust mote from the lamp every second or so. | `seeker.chirp`, quietly, now and then |

The scan fan is client-side only and stops when the scan's time ends, the droid leaves, or the level changes (`SeekerFx.reset` on a missing level or player). The vendor screen and the hub people have no effect events; `vendor.buy`, `vendor.refuse`, `credits.pickup` and `hub.apprentice_spar` are played by the server where they happen.

## Force powers (W6): `force_speed`, `force_barrier`, `force_barrier_impact`, `force_choke`, `force_choke_target`

Each event is drawn from the event alone, with no polling of server state, and every power loop has its own stop paths (see "Loops and watchdogs"). The caster's arm poses (`PowerAnimations.Kind.BARRIER`, both palms out at chest height with a slow sway, and `CHOKE`, the free arm raised toward the target and straining) start and stop with the same watchdogs, in third person through `HumanoidModelChokeMixin` and in first person through `PowerAnimations.applyFirstPerson`.

| Moment | Visual | Audio |
|---|---|---|
| Speed start (`force_speed` 0) | A puff of displaced air and a little dust at the feet. | `force.speed.start`; `force.speed.loop` (the wind at the ears) for the runner only |
| Running | 2 dust motes in the ground's colour kicked back from each footfall (one every 1.4 blocks) and 6 faint bent-air streaks a second at the ankles and wrists. No glow, no after-images. | the loop |
| Burst (2) | A line of streaks along the dash. | `force.speed.burst` |
| Speed end (1) | A second puff. | `force.speed.end` |
| Barrier raised (`force_barrier` 0) | Up to 24 `held_dust` motes (dust from the ground below, a few leaves over grass) drifting slowly on the sphere. | `force.barrier.start`; the `force.barrier.loop` hum, which for the caster sags and wavers as energy runs low |
| Impact (`force_barrier_impact` 0 to 4) | A bent-air ripple at the point on the sphere (kept at Reduced effects), the bolt fading into sparks, 6 motes shaken along the surface. | `force.barrier.impact`, one per stopped shot |
| Pulse (`force_barrier` 2) | The dust thrown outward. | `force.barrier.pulse` |
| Barrier end (1) or break (3) | End: the dust falls. Break: the dust scatters with a crack of air. | `force.barrier.break` on a break |
| Choke start (`force_choke` 0, caster) | The caster's straining arm. | `force.choke.start` and the `force.choke.loop` creak on the caster |
| Held (`force_choke_target` 1) | 10 grit motes a second lifted from the ground under the target, a slight bent-air tremor at its neck and shoulders, its hands at its throat and its feet hanging (the humanoid pose mixin). Its own hurt sounds are muffled. No glow, no tendrils. | |
| Release (`force_choke` 1) or crush (2); dropped (`force_choke_target` 0) | A puff of dust as the target drops; a heavier puff for a crush. | `force.choke.release`, or `force.choke.crush` |

## Foes and Kaast (W6): `hunter_tell`, `hunter_attack`, `droid_tell`, `beast_tell`, `kaast`

Every tell is drawn in full at every effects setting. Nothing draws a ground circle, a halo or an outline; dust spreads unevenly, with gaps. Sounds marked "server" are played by the mob's own code rather than by the client handler.

| Moment | Visual | Audio |
|---|---|---|
| Jet tell (`hunter_tell` 0) | A flicker of flame at the jet pack's nozzles. | `mandalorian.jet_tell` |
| Flamer tell (1) | The wrist comes up with a small pilot flame. | `mandalorian.flamer_tell` |
| Rocket tell (2) | A small red dot on the target for the whole tell, the only red light on the field. | `mandalorian.rocket_lock` |
| Net tell (3) | The launcher's click. | `mandalorian.net_tell` |
| Kaast's shoulder charge (4) and axe (5) tells | A scrape and dust for the charge; a glint on the axe. | `mandalorian.jet_tell` low for the charge, `boss.kaast.combo` low for the axe; `boss.kaast.charge` (server) |
| Jet hop (`hunter_attack` 0) | A gout of flame and smoke (`flame_jet`) pushed down from the nozzles. | `mandalorian.jet` |
| Flame (1) | The flamer's cone from the wrist along the look, following the hunter's synced flaming flag; it stops the moment the flame ends, the hunter is staggered or dies, or leaves this client. | `mandalorian.flamer` (looped for the flame's length) |
| Rocket (2) | A smoke trail in flight; a smoky burst. | `mandalorian.rocket` in flight (server), `mandalorian.explode` |
| Net (3) | The net's cords flying apart round the caught player. | `mandalorian.net` |
| Droid spin-up (`droid_tell` 0) | The barrels turning, with a breath of hot air. | `droid.spin_up`; `droid.fire` per bolt (server) |
| Droid shield (1) | The frontal plate locking forward; sparks and a clank where a bolt strikes it. | `droid.shield` (pitched lower when it is raised) |
| Droid death (2) | A sparking crackle until its burst. The sensor eye sweeps while it lives. | `droid.hurt` low; `droid.death` (server) |
| Hollowhorn charge tell (`beast_tell` 0) | A forehoof scraping clods for the whole tell. | `hollowhorn.snort` (server) |
| Hollowhorn run (3) and impact (4) | Dust behind the hooves; on a wall, a burst of dust and clods and a short close shake. | `hollowhorn.charge`, `hollowhorn.impact` (server) |
| Hound lunge tell (1), leap (5), howl (2) | Ash puffs under the crouch; ash kicked back at the leap; a breath of ash with the howl. | `ash_hound.growl`, `ash_hound.lunge`, `ash_hound.howl` (server) |
| Phase (`kaast` 0) | The phase banner: "Torvin Kaast, the hunter" over the phase's name (the Hunt, the Trap, the Last Stand). | `boss.kaast.phase` (server) |
| Mines (1 arm, 2 live, 7 sensed, 12 burst) | A spark and blink while arming, a puff when live, dust settling on live mines for a Force Sense user, and the burst's flash, smoke and clods. | `boss.kaast.mine_arm`, `mine_beep`, `mine_burst` (server) |
| Slam (3 tell, 4 slam) | Jet flame while he climbs and uneven dust where he will land, then the landing burst. | `boss.kaast.slam_tell`, `boss.kaast.slam` (server) |
| Yield (5) and helmet (6) | Dust settling as he kneels; grit from the cracked visor. | `boss.kaast.yield`, `boss.kaast.helmet_crack` (server) |
| Music (8) | Starts, crossfades or stops the phase's loop (see "Kaast's music"). | `music.kaast.*` |
| Clash (9) | Sparks when the axe blocks a saber. | `saber.clash`, lowered |
| Echo (10) | Pale ash as the echo forms. | |
| Burn off (11) | A short gout of the flamer at the Choke's caster. | |

Kaast's other sounds (`boss.kaast.hop`, `rifle`, `net_throw`, `axe_draw`, `combo`, `hurt`, `walk_away`) and the beasts' idle, hurt and death sounds are played by the server with the action.

### Kaast's music

`music.kaast.hunt`, `trap` and `last_stand`, one loop per phase, crossfaded by `KaastMusic` (`BossMusicService` since Milestone 5). Vanilla music is held off while it plays. It follows the M3 `ClientConfig.bossMusic` toggle.

## Loops and watchdogs (Milestone 4)

- **`waystone.corrupt`**, the **biome beds** and the **hub beds**: see W1 above.
- **`ship.idle`**: plays while a ship within 40 blocks has its engines on (READY on the ground, or in flight outside hyperspace), louder in flight, fading in and out over 20 ticks.
- **`ship.damaged`** (loop): below 20 hull (`WEAR_FAILING`) outside hyperspace.
- **`ship.hyperspace`**: the rider's hum with the overlay; it ends with the overlay (see above).
- Every ship loop stops at once when the ship is gone, the level changes or the client disconnects (`ShipLoop`).
- **`bench.weld`**: keyed by the crafter; stops on done, Masterwork or stop, or by watchdog at the craft's ticks plus 40, beyond 24 blocks, on a level change or a disconnect. The cutter's loop lapses 10 ticks after its last spark event.
- **`blaster.charge`** and **`electrostaff.charge`** are held sounds: cut when the charge is dropped, fired, spent or swapped away.
- W4's followers (the overheat steam, the jet flames, the staff's crawling arcs) stop on their end event, when their entity dies or is removed, the level changes or the item leaves the hand, with a hard tick cap as a watchdog.
- **`force.speed.loop`**, **`force.barrier.loop`** and **`force.choke.loop`** (`PowerLoop`, one per power and player): stop on the end event, by a watchdog of the length the server sent plus 20 ticks, on the player's death or removal, a level change and disconnect.
- **`mandalorian.flamer`**: follows the hunter's synced flaming flag; stops the moment the flame ends, the hunter is staggered or dies, or leaves this client.
- **`music.kaast.*`**: stops on the yield, a victory, a reset, leaving the ring, death, a level change, disconnect and the client toggle, with a 200-tick watchdog.
- The seeker's scan fan is not a sound loop: it ends with the scan's time, when the droid leaves, or on a level change.
- Loop stopping is not checked by a test (CI has no audio).

# Milestone 5

The rules, budgets, particle types and event table are in `docs/MILESTONE_5.md` section 15. Each area adds its part here, and all eight are merged: the free flight and the ships (W1), space and ship combat (W2), the worlds (W3), Chapter Five and the factions (W4), the paths and ranks (W5), the Force powers (W6), sabers, crafting and gear (W7), and the foes, bosses and the raid (W8). After the merge the lead fixed `Vfx.count` being charged once per loop pass where a loop tested it in its condition (`41b0ad2`); every count is now taken once before its loop.

## Force powers (W6): `force_stasis`, `force_valor`, `force_drain`, `force_fear`, `force_rage`, `force_mind_trick`

Every event is drawn from the event alone, with no polling of server state. The one exception is the Rage caster's
own client, which follows its own `rage` effect, so a relog keeps the loop and the vignette. Counts go through
`Vfx.count` and `Vfx.inRange`, so the presets thin them; nothing here is a tell. Nothing draws a ground circle, a halo,
a glyph or an outline. Each loop is a `PowerLoopSound`, which stops itself when its entity dies, is removed (leaves
tracking range) or leaves the client's level.

| Id | Delivery (variant, intensity) |
|---|---|
| `force_stasis` | toTracking on the target; variant = `ForceEffects.packCaster(caster id, v)`: v 0 cast, 1 held (on each creature held; intensity = seconds), 2 break (unpacked: the caster may be gone) |
| `force_valor` | toTracking; 0 cast (on the caster; intensity = seconds), 1 an ally touched (on the ally), 2 end (on any player whose valor ended) |
| `force_drain` | toTracking on the caster; pos = the target's chest, dir = from the caster's eyes to the target; variant = `packCaster(target id, v)`: v 0 start (intensity = the longest channel in seconds), 1 pulse (intensity = the distance), 2 end |
| `force_fear` | toTracking; 0 cast (on the caster; pos = the eyes; dir = the cone's axis, zero for Terror; intensity = the range or the radius), 1 a victim (on the victim; intensity = seconds) |
| `force_rage` | toTracking on the caster; 0 start (intensity = seconds), 1 end |
| `force_mind_trick` | toTracking on the target; variant = `packCaster(caster id, v)`: v 0 success (intensity = seconds, 0 for a dialogue use), 1 refused, 3 under Confusion; 2 ended (unpacked: a hit broke the calm) |

Packing the caster's or target's id in the variant extends the 15.3 table, which named only the variant numbers. The
added variants 2 and 3 of `force_mind_trick` are new too.

Phase by phase:

- **Stasis.** *Anticipation*: the caster's palm goes out, fingers spread, and holds (`STASIS`). *Activation*:
  `force.stasis.cast`, a glassy chime, at the target. *Travel*: none; the hold is instant. *Impact*: 16 `stasis_rime`
  motes drift in, settle on the target and hang perfectly still. Two small patches of bent air on its body
  (`air_ripple`) stop moving: the frozen heat haze. `force.stasis.loop` hums on it. *Aftermath*: on the break the
  motes lose their frost, turn grey and fall as grit, with `force.stasis.break`. The caster's pose holds 20 ticks.
- **Valor.** *Anticipation*: the saber hand rises, the other hand opens at the chest (`VALOR`, 26 ticks). *Activation*:
  `force.valor.cast`, a low swell, and a warm gust: 20 `valor_dust` lifted round the caster in three uneven puffs.
  *Travel*: the same puffs round each ally touched (the swell is the caster's alone). *Lasting*: one mote rises from
  each affected player every 10 ticks. *Aftermath*: `force.valor.end`, and the motes stop.
- **Drain.** *Anticipation*: the free hand reaches out, fingers curled, and draws slowly back (`DRAIN`).
  *Activation*: `force.drain.loop` on the caster. *Travel*: every tick 3 dark-red `drain_mote` leave the target and
  steer for the caster's hand: a thin stream. *Impact*: each pulse thickens the stream for a few ticks. *Aftermath*:
  the loop stops, `force.drain.end` sighs, and the pose drops.
- **Fear.** *Anticipation*: a cupped hand thrust out and twisted (`FEAR`, 14 ticks). *Activation*: `force.fear.cast`, a
  whispering rush. *Travel*: one `air_ripple` sweep down the cone. Terror's goes out all round in up to 6 broken,
  uneven arcs. *Impact*: 6 `shadow_wisp` curl up at each victim's feet, and the victim shivers (a small tremor added
  to its pose). *Lasting*: one wisp per 10 ticks while it is afraid.
- **Rage.** *Activation*: `force.rage.cast`, a growl in the Force, for everyone near. *Lasting*, seen by everyone: one
  `flare_ember` a tick trailing a lit blade, a heat haze of one `air_ripple` every 5 ticks, and the hunched posture
  (shoulders up, saber arm low). The posture is an offset added to the idle and walk pose, never over a power pose, and
  it fades out through a swing. For the caster only: `force.rage.loop` and a dark-red edge vignette that pulses
  slowly (steady with flashing effects off). *Aftermath*: `force.rage.end`, and everything stops.
- **Mind Trick.** *Anticipation*: two fingers raised beside the face, a small wave (`MIND_TRICK`, 18 ticks). *Activation*:
  `force.mind_trick.cast`, a soft low tone, at the target. *Impact*: one `air_ripple` at its head, and the calm: it
  lowers its weapon and bows its head a little while the pacify lasts. Under Confusion there is no calm. A refusal
  plays the gesture and `force.mind_trick.fail`, and the air does not bend. *Aftermath*: when a hit breaks the calm
  (variant 2), the guard comes back up.

Budgets at Standard (15.1), all met: Stasis 16 rime held, the same 16 falling. Valor 20 dust per player at the cast,
then 1 per 10 ticks. Drain 3 motes a tick. Fear one ripple sweep, then 6 wisps per victim and 1 per 10 ticks. Rage 1
ember a tick and 1 ripple per 5 ticks. Mind Trick 1 ripple. Two small additions beyond the table: Stasis's frozen haze
is 2 `air_ripple` per held creature, and Terror's sweep is up to 6 arcs instead of one ripple.

Loop stop paths (15.4):

- `force.stasis.loop` (on the target) stops on the break event, whatever ended the hold on the server (a hit, its time,
  milk, `/effect clear`, death, unload). It also stops on the target's death or removal, a level change, a disconnect,
  and a watchdog: the length the server sent, plus 20 ticks.
- `force.drain.loop` (on the caster) stops on the end event, whatever ended the channel. It also stops on the caster's
  death or removal, a level change, a disconnect, and a watchdog: the longest channel plus 30 ticks.
- `force.rage.loop` plays for the caster only, and follows the caster's own `rage` effect. It stops the tick the effect
  goes for any reason, and on death, a level change and a disconnect.

## Paths and ranks (W5): `turning`, `rank_four`

Client: `client/quest/turning/TurningFx`. Each effect is a few timed beats run from the client tick, thinned by the VFX setting (`Vfx.count`) and skipped out of range (`Vfx.inRange`); a level change or a disconnect drops the beats still pending. The full phase tables, with colours, sizes and timings, are in `docs/assets/turning.md`, A1.

| Moment | Visual | Audio |
|---|---|---|
| The offer (`turning` 0, to the player) | None: the messenger walking up is the look | `story.turning.offer` |
| The Fall's rite, "Kneel" (`turning` 1) | 30 cold-grey `gather_dust` blown across the camp over 40 ticks; a lit campfire nearby seems to gutter under darker grit | `story.turning.rite`, with the banner |
| The Return's rite, "Welcomed" (`turning` 2) | 30 warm motes settling over 40 ticks; a lit campfire nearby seems to brighten | the same |
| Master (`rank_four` 0, around the player) | 8 `crystal_glint` across the player's hands, then 12 falling | `story.rank_four` |
| Darth (`rank_four` 1) | 12 `flare_ember` burning in from the writ's edges, then 16 ash | the same |
| Wayfarer (`rank_four` 2) | The Compass figure folds, forms again a step aside at 20 ticks for 90 ticks, and 16 specks of road dust lift | the same, and `holocron.gatekeeper.form` as she forms again |

## Sabers, crafting and gear (W7): `offhand_strike`, `crystal_tune`, `bench_craft` 5, `player_blaster_fire` 8

| Moment | Visual | Audio |
|---|---|---|
| Off-hand strike (`offhand_strike`, to trackers; variant 1 a hit, 0 a miss) | Others see the off hand swing at once; the off blade leaves its own swing trail in its colour; on a hit, the M1 hit effects | `saber.offhand_swing` |
| Tuning (`crystal_tune`, to the player; variant = the tuning, intensity = the target) | 12 `crystal_glint` lift off the bench (`Vfx.count`); the tuned socket glints on the Tune tab | `workbench.tune` |
| Legendary craft (`bench_craft` 5) | The weld stops with a puff; `spark_shower` sparks rise slowly, one every 2 ticks for 40 ticks, under a warm gold glow (`SaberSparks.glow`); a "Legendary" toast. On the first Legendary (intensity 2) the chime and the toast are left to W4's banner | `bench.legendary` |
| Storm arc (`player_blaster_fire` 8, intensity = the length) | A jagged `lightning_spark` line from one enemy to the next, wandering most in the middle; small arcs where it lands | `ELECTROSTAFF_SHOCK`, pitched up |
| Stances, hums and blade length (no event) | Third-person stances and the off-arm guard, which yield to techniques and Force poses; blades drawn ×0.7 short or ×1.15 long, with the trail and the thrown blade scaled to match | Ignition, hum and retraction by emitter; one hum per lit blade |

## Worlds (W3): `sandstorm`, `verse_stone`, `stormglass`, `site_door`

Client: `client/planet/PlanetM5EffectHandlers`, `Sandstorm`, `VerseStoneClient`, `StormFlash`, `PlanetFog`. Every effect goes through `Vfx` (budgets scale with the preset and `Vfx.count`). Nothing draws a ring, sigil, halo or glowing rune in the world: bent air, dust, grit, motes, sparks and light only. No camera shake.

| Moment | Visual | Audio |
|---|---|---|
| Sandstorm rising (`sandstorm` 0, to the player) | The fog's far end pulls in to `sandstormFogEnd` (24) and its start to 0 over 200 ticks, tinted sand by the light; `sand_gust` streaks start upwind of the camera, 60 alive at full strength at Standard | `sandstorm.wind` fades in over 100 ticks |
| Sandstorm blowing | The streaks flutter along the wind (a slow seeded veer), only under open sky | The wind loop, positional to the camera |
| Sandstorm, under a roof | The storm's strength eases to 35% over 40 ticks (fog and streaks) | The wind ducks to 45% |
| Sandstorm passing (`sandstorm` 1) | The fog eases back over 200 ticks; no new streaks | The wind fades out over 100 ticks; it stops on leaving Ghessar, death or a disconnect |
| Verse stone awake (`verse_stone` 0) | 2 `verse_mote` a second drift off the monolith | `verse_stone.hum`, a positional loop within 24 blocks while the stone is awake |
| The stone's slowing pulse | One `air_ripple` swelling from 1.2 to 7 blocks around the stone (the air bends outward once); 6 motes thrown off | `verse_stone.pulse` |
| The channel (`verse_stone` 1, 2) | 2 `verse_mote` a tick stream from the stone to the channeller's hand; for the channeller, a dot ring round the crosshair fills with the progress (on the HUD, not in the world) | The `verse_stone.channel` loop, released 20 ticks after the last progress |
| A Force power cuts the channel (`verse_stone` 4) | A brighter pulse of 10 stream motes | `verse_stone.channel` pitched up |
| The channel broken (`verse_stone` 3) | The stream stops and its motes scatter | The loop stops |
| The stone silenced (`verse_stone` 5) | The faint glow in the fissures drains (the block's silent model); 30 motes fall as grit over 20 ticks | `verse_stone.silence`; the hum stops |
| Stormglass strike (`stormglass` 0) | 6 `lightning_spark` crawl over the struck basalt's top face over 10 ticks (crystal motes instead with flashing off), then 4 block-break particles of the cluster pushing up | `stormglass.form` |
| The holdfast gate shuts or opens (`site_door` 0, 1) | The plates slide; 16 dust particles of the gate fall along it over 20 ticks | `holdfast.gate` (pitched down when it shuts) |
| The Hymnal's doors open or seal (`site_door` 2, 3) | Stone grinds apart; 16 dust particles of the plating fall across the doorway over 20 ticks | `hymnal.door` (pitched down when they seal) |
| Storm flash (client, each bolt) | With flashing or storm flashes off, a low, steady brightening replaces the sky flash | Thunder stays vanilla |
| Mire fog (client, in `tyrvann_mire`) | The fog closes to 4 to 48 blocks, a cold green-grey, eased over 60 ticks | The mire bed |
| Block ambience | Sunstone ore: a faint heat shimmer (`air_ripple`) above it by day; stormglass: a spark now and then | None |

The beds are the Milestone 4 emitter's: one streamed loop per biome, hub and the Hymnal, cross-faded, ducked under cover, and ducked to 30% while W4's verse vision plays.

## Chapter Five (W4): `verse_vision`, `hologram` (sent), `lesson_complete` 11 to 15

| Moment | Visual | Audio |
|---|---|---|
| A verse stone silenced (`verse_vision`, to the player; variant = the order it fell in minus 1; intensity = 120) | A grey-gold wash over the whole screen, three subtitled lines, and a faint motif (the stave, the choir, the quiet city). Steady with flashing effects off. Ends on death, a level change or a disconnect. Never shorter than 60 ticks. Phases in `docs/assets/chapter_five.md` A1. | `story.verse_vision`; W3's ambience ducks while it plays (`dipping()`) |
| Dask's "Show me" (`hologram`, around, 240 ticks) | W8's `HologramFx` | W8's `story.hologram` |
| The report (`lesson_complete` 11) | Banner "Chapter Five complete", with the path line in the path's colour | `story.chapter_five` |
| `lesson_complete` 12 to 15 | W4's banners for W5's rites, W7's legendary craft and W8's | Each owner's sound |

## Foes and bosses (W8): `scour`, `hymnsworn`, `stormback`, `hask_warrior`, `ulvaine`, `first_cantor`, `hologram`

The server plays the one-shot sounds. The client draws every look from the event and keeps the loops.
- Counts go through `Vfx`. The tells (their variants are in `DangerTellTable`) use `Vfx.tellCount` and are never thinned.
- Nothing draws a ground circle, halo, sigil or outline.
- The only storm flash is vanilla's own bolt flash; arcs and beams flash nothing.

| Id | Delivery (variant, intensity) |
|---|---|
| `scour` | toTracking, entity = the scour. 0 ripple (every 60 ticks while burrowed); 1 burst tell (intensity = ticks; tell); 2 burst; 3 burrow; 4 the matriarch's sweep tell (intensity = ticks, dir = her facing; tell); 5 brood call; 6 the sweep lands (dir = facing; an added aftermath variant) |
| `hymnsworn` | 0 chant pulse (toTracking on the chanter); 1 storm-call tell (around; pos = the patch, intensity = ticks, entity = the caller; tell); 2 strike (around; pos = the patch); 3 Force-lightning pulse (toTracking; pos = the struck chest, dir = from the caller, intensity = the burst's ticks) |
| `stormback` | toTracking. 0 charged (intensity = the charge's ticks); 1 charge tell (intensity = ticks, dir = the charge; tell); 2 impact (pos); 3 roar tell (intensity = ticks; tell); 4 spent |
| `hask_warrior` | toTracking. 0 jet-slam tell (intensity = ticks; tell); 1 slam lands (pos); 2 shield block (pos); 3 bash tell (intensity = ticks; tell); 4 bash lands (pos). Rifle, rocket and jets send the M4 `hunter_tell` and `hunter_attack` |
| `ulvaine` | around. 0 phase (intensity = the phase; 4 = the echo forming); 1 step tell (intensity = ticks; tell); 2 step (pos = arrival, dir = from where she left); 3 lightning pulse (pos = the chest, dir = from her hand); 4 rod tell (pos = the patch, dir = from the rod, intensity = ticks; tell); 5 rod strike; 6 Hymn pulse (pos = her feet, intensity = radius, dir.x = ticks, dir.y = height; tell); 7 Crescendo (intensity = ticks; tell); 8 resonator lights (intensity 1) or breaks (0); 9 blast; 10 yield; 11 echo fades |
| `first_cantor` | around. 0 phase (intensity: 0 keystone and countdown with dir.x = ticks, 1 rise with dir.x = ticks, 2, 3 = the engine wakes, 4 = the Hymn stops); 1 beam tell (pos = origin at sweep height, dir.xz = start direction × reach, dir.y = ticks, intensity = signed sweep in degrees; tell); 2 beam (as 1, dir.y = sweep ticks); 3 slam tell (pos = feet, intensity = radius; tell); 4 slam; 5 chain link (pos = chest, dir = from the previous link, intensity = index); 6 roots tell (tell); 7 roots (intensity 0 = a freed player); 8 mire fog (intensity = width, dir.x = ticks); 9 basalt mark (tell); 10 basalt strike; 11 ring tell (tell); 12 ring (dir.x = ticks); 13 silencing point (intensity = progress); 14 silenced (dir.x = window ticks); 15 choir shield (pos = an echo's chest, dir = to his chest); 16 unravel (dir.x = ticks) |
| `hologram` | around, from W4's dialogue action: 0 start (pos = the desk, entity = the speaker, intensity = ticks, 0 = until the end); 1 end |

**Phase by phase** (from `docs/design/foes_m5.md` section 14):

| Event | Anticipation (tell) | Activation and travel | Impact | Aftermath | Sound |
|---|---|---|---|---|---|
| Bruiser cut and shove (`npc_windup`) | blade drawn back, glint | the swing | M2 hit | — | M2 wind-up, `cartel.shove` |
| Gunman and captain shots | muzzle glow 12 / 10 ticks (`CartelGlowLayer`) | the M1 bolt | `blaster.impact` | — | `blaster.fire` |
| Captain's frag | M3 `grenade_tell` (the arm drawn back) | the M3 grenade | M3 blast | — | M3 grenade sounds |
| Hask jet-slam (`hask_warrior` 0, 1) | crouch, 8 `engine_exhaust` from the pack over 20 ticks | the jets' arc | flame and a burst of the ground's dust, uneven, with gaps | dust settling | `hask.slam_tell`, `hask.slam` |
| Hask shield (2, 3, 4) | bash: shield drawn back, 4 `spark_shower` off the rim over 15 ticks | the shove | a crunch of grit; block: sparks off the shield | — | `hask.bash_tell`, `hask.bash`, `hask.shield_block` |
| Chant (`hymnsworn` 0) | — | breath fogging before the veil, grit trembling in a few uneven spots (no ring) | — | — | `hymnsworn.chant` (loop while chanting) |
| Storm call (`hymnsworn` 1, 2) | 20 `lightning_spark` crawling over an uneven patch, 10 `gather_dust` lifting, over 40 ticks; static between the raised hands | vanilla's cosmetic bolt | scorch: smoke, the ground's dust, sparks | ash settling | `hymnsworn.call_tell`, `hymnsworn.call_strike` |
| Force lightning (`hymnsworn` 3) | — | an arc from the left hand per pulse (`FoeArcs`, steady with flashing off) | sparks on the chest | — | `force.lightning.release` |
| Scour (`scour` 0–6) | ripple: a faint stir along an uneven line; burst tell: 15 `sand_burst` boiling over 15 ticks; sweep tell: 12 `sand_burst` along the tail's arc over 20 ticks | burst: 30 `sand_burst` thrown up with the ground's dust, a jolt up close | bite; sweep: dust along the arc | burrow: sand slumping in, in clumps; brood call: churning in 2–3 spots | `scour.hiss` (loop), `scour.burst_tell`, `scour.burst`, `scour.burrow`, `scour.matriarch.{sweep,call}` |
| Stormback (`stormback` 0–4) | charge: 10 `gather_dust` pawed up over 20 ticks; roar: 12 `lightning_spark` gathering over the plates over 30 ticks | the charge | a heavy thud of dust and grit | charged: a burst of static, then 1 `lightning_spark` per 4 ticks while it lasts; spent: falling sparks and a wisp of smoke | `stormback.charge_tell`, `stormback.impact`, `stormback.roar`, `stormback.charged` (loop) |
| Ulvaine step (`ulvaine` 1, 2) | 12 `gather_dust` drawn in and one `air_ripple` over 15 ticks | — | air shoved aside where she reappears | — | `boss.ulvaine.step_tell`, `boss.ulvaine.step` |
| Ulvaine lightning (3) | — | an arc from her raised hand to the struck chest per pulse | — | — | `boss.ulvaine.lightning` (loop while channelling) |
| Rods (4, 5) | `StormTellFx` on the patch over 30 ticks | vanilla's cosmetic bolt (in the domed Heart it shows from the ceiling down) | scorch under the bolt | — | `boss.ulvaine.rod_charge`, `boss.ulvaine.rod_strike` (with vanilla thunder) |
| Hymn pulse (6) | the wave itself is the tell: 48 `gather_dust` along the front every 4 ticks, an irregular wall cut into slots of varied height with gaps (the M3 Surge look) | rolls out to 16 over 40 ticks | — | — | `boss.ulvaine.pulse` |
| Crescendo (7, 8, 9) | a banner, then 6 `light_mote` rising from each standing resonator a second | a resonator lights in a flare of motes | blast: the hall shakes, dust falls from above | a broken spire sinks into its plinth in violet shards | `boss.ulvaine.crescendo`, `boss.ulvaine.resonator`, `boss.ulvaine.blast` |
| Phases, yield, echo (0, 10, 11) | — | a banner and a gust of dust | — | yield: dust as she kneels; echo: pale wisps as it comes apart | `boss.ulvaine.phase`, `boss.ulvaine.yield`, `boss.ulvaine.echo_fade` |
| Cantor beam (`first_cantor` 1, 2) | 24 `tomb_dust` lifting along the coming path over 40 ticks | the rotating beam drawn at a body's height with a faint sheet of the band it sweeps | — | — | `boss.cantor.beam_tell`, `boss.cantor.beam` (loop while sweeping) |
| Slam (3, 4) | 16 `gather_dust` lifting under him over 20 ticks | the blow | the floor cracks, debris | — | `boss.cantor.slam_tell`, `boss.cantor.slam` |
| Chain (5) | — | an arc per link | — | — | `boss.cantor.chain` |
| Roots (6, 7) | cracks spreading, 20 `tomb_dust` along them over 30 ticks | the grip | roots bursting at a freed player's feet | — | `boss.cantor.roots` |
| Mire (8) | — | a dark soft-edged stain creeping over the floor, with wisps | — | — | `boss.cantor.fog` |
| Basalt (9, 10) | `StormTellFx` on each mark over 40 ticks | a bolt from the vault (drawn by the client: no sky bolt under the roof) | stone cracking | — | `boss.cantor.mark`, `boss.cantor.strike` |
| Rings (11, 12) | 12 `vent_grit` trembling over the stones over 30 ticks | while singing, 2 `gather_dust` and one `air_ripple` per 5 ticks in uneven patches | — | — | `boss.cantor.ring_tell`, `boss.cantor.ring` (loop while singing) |
| Silencing (13, 14) | a slow column of motes over each open point; the channel draws light in | — | a silenced point bursts | his light falters during the window | `boss.cantor.silence` |
| Choir and shield (15) | — | pale motes drifting from the echoes to his robes | — | a falling echo fades into wisps | `boss.cantor.choir` (loop for each singing echo; once as the choir rises and, lower, as an echo falls) |
| Rise, phases, unravel (0, 16) | the keystone stirs the engine | he gathers out of the floor in drifting light; banners | — | 120 `spectral_wisp` over 60 ticks as he unravels | `boss.cantor.phase`, `boss.cantor.engine` (loop in phase 3), `boss.cantor.unravel` |
| `hologram` | — | the translucent figure | — | — | `story.hologram` (loop) |

Lasting looks: the Cantor and each echo shed 2 `spectral_wisp` a tick; the stormback's charged sparks and the resonators' light follow synced state.

**Budgets at Standard** are the 15.1 rows for W8, all met. One small addition: Ulvaine's echo itself draws no particles, but its fade (`ulvaine` 11) is an event moment of 30 (24 `spectral_wisp` and 6 `light_mote`).

**Loop stop paths (15.4).** Nine loops, each in `FoeLoops` with a key and a condition read every tick: `hymnsworn.chant`, `scour.hiss`, `stormback.charged`, `boss.ulvaine.lightning`, `boss.cantor.{beam,ring,choir,engine}` and `story.hologram`. Each one fades out when:
- its condition fails;
- its entity dies or is removed (at once);
- the listener leaves its range;
- the local player dies, changes level or dimension, or disconnects (at once).

`FoeWatch` restarts the loops that follow synced state. The music follows `BossMusicService`'s stop paths (12.5).

## Flight and ships (W1): `ship_flight`, `atmosphere`, `ship_refit`, `ship_disabled`

| Event | Looks | Sound |
|---|---|---|
| `ship_flight` 0 helm | the struts fold, the ramp shuts, the engines flare | `ship.flight.helm` |
| `ship_flight` 1 boost | a longer, brighter plume and a pressure puff for 10 ticks; the riders' shake (settings) | `ship.flight.boost` |
| `ship_flight` 2 thrusters | short white jets from the hull on the pressed sides | `ship.flight.thruster` |
| `ship_flight` 3 impact | sparks and the struck block's dust at the struck point | `ship.flight.impact` |
| `ship_flight` 4 autopilot | none (the M4 Jump follows) | `ship.flight.helm`, lower |
| `ship_flight` 5 edge | none (the turn itself, the HUD's warning) | `ship.flight.edge` |
| `ship_flight` 6 landing refused | none (the HUD line and the overlay message) | `ship.flight.land_warn`, the riders only |
| `atmosphere` | `AtmosphereOverlay` and `atmosphere_streak` around the hull | `ship.flight.atmosphere` |
| `ship_refit` 0, 1 / 2 | a puff of steam over the swap, then welding sparks for 40 ticks / sparks at the fitting point (a fit or a removal) | `ship.refit` / `ship.upgrade_fit` |
| `ship_disabled` 0 / 1 | sparks, smoke and lights that flicker (steady with flashing off) for the tumble, the pilot's jolt / the alarm stops | M4 `ship.damaged` and `ship.flight.alarm` (loop, the pilot) / `ship.flight.rescue` |
| running | the engine trail (2 puffs a tick at a hover to 8 at full speed, 12 for the boost's first 10 ticks), the engine loop | `ship.flight.engine`, `ship.interceptor.engine` |

Every count goes through `Vfx`. Nothing raises dust in an orbit. No shapes are drawn on the ground. Every running effect stops with its condition, its ship, a level change and a disconnect. The overlay follows `hyperspaceOverlay`.

Loop stop paths:
- The engine loop stops when the ship is no longer flown by hand. The M4 idle then takes over.
- The alarm stops when the rescue arrives, or when the pilot leaves the ship, changes level or disconnects.

## Space (W2): `ship_weapon`, `ship_hit`, `ship_shield`, `missile_lock`, `ship_destroyed`, `gunship`, `space_comm`, `space_scan`, `nav_buoy`

Client: `client/space/SpaceFx` and `SpaceLoops`. There is no dust in orbit. Smoke lasts 12–17 ticks and never rises. Fragments keep flying. Every flash is steady with flashing effects off. Weapon, hit and explosion sounds carry far, following the films' convention.

| Effect | Phase | Look | Sound |
|---|---|---|---|
| `ship_weapon` 0 cannons, 1 turret | each shot | a short warm-white muzzle glow riding with the ship (3 ticks), 2 muzzle sparks; the bolt is the renderer's streak (a white-hot core in two layers of glow in its colour, one tick of flight long, never trailing particles) | `space.cannon.fire` (two variants), `space.turret.fire` |
| `ship_weapon` 2 missile launch | launch | 3 puffs of smoke at the rack | `space.missile.launch` |
| | flight | the dark finned body rolling as it homes, a blue-white motor flame (steady with flashing off), 1 smoke puff every 2 ticks behind it | none |
| `ship_weapon` 3 overheat | 20 ticks | 6 thin smoke puffs from the barrels, following the ship | `space.cannon.overheat` |
| `ship_weapon` 4 fighter lasers, 6 gunship turret | each shot | a muzzle glow tinted by who flies it (Hymn violet, kajidic orange, clan amber; the gunship's larger and violet), 2 sparks | `space.fighter.fire` (the gunship's pitched down) |
| `ship_weapon` 5 fighter missile | launch | 3 smoke puffs | `space.missile.launch`, pitched down |
| `ship_hit` 0 shield | impact | one ripple of bent space (`air_ripple`, 1.4 to 4.5 blocks by damage, growing 2.6 times over 10 ticks) spreading over the struck side, 3 sparks; the pilot's camera jolts lightly | `space.bolt.hit_shield` |
| `ship_hit` 1 hull | impact | 8 sparks and 2 `hull_debris` thrown out of the hull with its drift; the pilot's camera shakes with the damage | `space.bolt.hit_hull` |
| `ship_hit` 2 block | impact | 3 sparks only | `blaster.impact` (existing) |
| `ship_shield` 0 down | the fall | a last ripple over the whole hull that tears into 8 sparks | `space.shield.down` |
| `ship_shield` 1 recharge | 20 ticks | one faint shimmer (a single ripple) crawling from stern to bow | `space.shield.recharge` |
| `ship_shield` 2 full | | one faint ripple | none |
| `missile_lock` 0 locking | while held | the HUD's ring closes, amber | `space.missile.lock_tone` loop, its pitch rising with the progress |
| `missile_lock` 1 locked | | the ring turns red and labelled | `space.missile.locked`; the tone holds, higher |
| `missile_lock` 2 lost | | the ring goes | the tone stops |
| `missile_lock` 3, 4 incoming | start to end | the HUD's red edge pulse and warning line (steady with flashing off) | `space.missile.warning` loop, start to end |
| `ship_destroyed` 0 fighter, 1 generator | the blast | one flash (a steady, dimmer glow with flashing off), 24 `hull_debris` tumbling outward, glowing hot then burning out to dark metal, 12 sparks; a camera jolt within 32 blocks | `space.fighter.explode`; `space.gunship.generator_down` |
| `ship_destroyed` 2 gunship blast | ticks 1, 14, 27 | a fighter-sized burst along the spine; its engines sputter | `space.fighter.explode`, pitched down |
| `ship_destroyed` 3 gunship end | tick 40 | a large flash and 60 `hull_debris`; the hull breaks in two and the halves drift apart until tick 80 | `space.gunship.explode` |
| `gunship` 0 bay opens | 30 ticks | the belly doors part (the renderer), a breath of venting exhaust | `space.gunship.launch`, low |
| `gunship` 1 launch | | two interceptors drop out of the bay through 10 puffs of exhaust | `space.gunship.launch` |
| `space_comm` | a line | a subtitle in the M4 bark style, the speaker's name in their colour (fades in over 4 ticks, out over 14) | `space.comm` (a radio crackle) |
| `space_scan` 0 scanning, 1 broken, 2 complete | while scanned | the HUD's scan meter | `space.scan` loop while scanning |
| `nav_buoy` 0 blink | every 2 s | the lamp blinks amber with the ping (steady amber with flashing off) | `space.buoy` |
| `nav_buoy` 1 reached | | the lamp turns a steady green, one green glow | `space.buoy`, higher |
| engines (client) | while near | the fighters' exhausts and the gunship's engines and running lights glow (a flicker, steady with flashing off) | `space.fighter.engine`, `space.hymn_interceptor.engine`, `space.gunship.hum`: entity-bound loops for the nearest six fighters within 56 blocks and the gunship within 96 |
