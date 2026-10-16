# VFX and SFX specification

Every feature defines its visual and audio treatment here before it ships. The Milestone 1 sections come first, then the Milestone 2 section. Milestone 3's effects are specified in `docs/MILESTONE_3.md` section 13, and the lead merges them in here at integration. IDs are the contract between code, assets and `sounds.json`. All sound IDs are in the `oldrepublic` namespace; all are **placeholder** audio synthesised by `tools/assets/gen_sounds.py` until replaced (see `ASSET_MANIFEST.md`).

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
