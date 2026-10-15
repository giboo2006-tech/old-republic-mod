# VFX and SFX specification (Milestone 1)

Every feature defines its visual and audio treatment here before it ships. IDs are the contract between code, assets and `sounds.json`. All sound IDs are in the `oldrepublic` namespace; all are **placeholder** audio synthesised by `tools/assets/gen_sounds.py` until replaced (see `ASSET_MANIFEST.md`).

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
Every sound event above has a subtitle key `subtitles.oldrepublic.<id>`.
