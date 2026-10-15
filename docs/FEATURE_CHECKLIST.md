# Feature checklist

Status per feature, with how it has been checked. Legend:
- **Built**: code and data exist and compile on CI.
- **Server test**: covered by a server game test (`./gradlew build`).
- **Client test**: driven by the client game tests, with screenshots reviewed (`./gradlew runClientGameTest`). `ClientSmokeTest` uses commands to reach each feature; `PlaythroughTest` plays the whole chapter on a normal-terrain world with real inputs (it teleports between distant places and logs every shortcut).
- **Played**: hand-tested in game by a person. Nothing is at this level yet.

## Milestone 1 (vertical slice)

| Feature | Built | Server test | Client test | Played | Notes |
|---|---|---|---|---|---|
| Force awakening (sleep or time trigger, dream, burst, journal) | ✓ | ✓ stage rules | ✓ dream and after | | Sleep trigger not driven by a test |
| Persistent progression (level, XP curve, Resonance, energy, skill points, mastery, anti-grind) | ✓ | ✓ curve, save round trip | ✓ HUD values | | Death/relog keep data by design (copy-on-death attachment) |
| Force Energy meter and regen | ✓ | | ✓ HUD | | |
| Force Push, charge and release | ✓ | ✓ cone, thresholds, costs | ✓ charge, release, aftermath | | |
| Force Push branches (Repulse Wave, Focused Blast, Deflecting Pulse) | ✓ | ✓ branch numbers and choice rules | ✓ Force screen | | Branch effects not driven by the client test |
| Meditation teaches Push; practice; the Calling | ✓ | ✓ practice counting, daily meditation | | | Calling depends on a generated cave nearby |
| Wanderer's Journal screen and codex | ✓ | ✓ unique entry ids | ✓ journal | | |
| Crystal cave structure (camp, descent, heart) | ✓ | ✓ templates load | ✓ natural caves found and played (playthrough) | | |
| Unstable crystal cluster hazard | ✓ | | | | |
| Resonant seal | ✓ | ✓ refuses un-awakened player | | | |
| Trial of Reflection and Shadow Echo | ✓ | ✓ requirements, orphan echo cleanup | ✓ forming, echo, crystal | | |
| Attuned kyber crystal (owner-only) | ✓ | | ✓ crystal forms | | Owner-only pickup needs two players |
| Hilt parts and loot (emitters, grips, pommels; bad-luck protection) | ✓ | | | | |
| Lightsaber workbench | ✓ | ✓ construct/disassemble returns the same crystal, slot ownership | ✓ screen | | |
| Customisable lightsaber (colour, parts, stats) | ✓ | ✓ stat maths | ✓ ignited, first and third person | | |
| Blocking, parry and bolt deflection | ✓ | | ✓ guard vs poacher | | |
| Crystal Poacher (ranged enemy, tells, bursts, cover) | ✓ | | ✓ fight | | |
| Intro quest (stages Stirring to Complete) | ✓ | ✓ forward-only stages | ✓ through the trial | | |
| VFX/SFX with quality presets, shake and flashing settings | ✓ | | partial (visuals only; CI has no audio) | | `/oldrepublicfx` client command changes settings |
| Single-player | ✓ | | ✓ | | |
| Dedicated server | ✓ | ✓ (game tests run on a dedicated server) | ✓ client joins and pushes | | |

## Supporting work
| Item | Status |
|---|---|
| Balance config (`config/oldrepublic/balance.json`) | ✓ see `docs/BALANCE.md` |
| Asset manifest | ✓ see `docs/ASSET_MANIFEST.md` |
| Known issues | ✓ see `docs/KNOWN_ISSUES.md` |
| Decisions and lore departures | ✓ see `docs/DECISIONS.md` |
| License | ✓ All Rights Reserved, see `LICENSE` |

## Later milestones (from the design brief, not started)
More Force powers and skill trees, Jedi/Sith alignment and reputation, more crystals and saber forms, more enemies and bosses, more structures, ships.
