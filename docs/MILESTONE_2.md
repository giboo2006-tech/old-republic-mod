# Milestone 2: Identity and Builds (design spec)

This is the working spec for Milestone 2. It turns the second roadmap entry of `DESIGN_BRIEF.md` section 17 into concrete, testable features that extend what Milestone 1 built:

- Jedi and Sith introductory paths.
- Alignment and faction reputation.
- Initial skill trees.
- Several distinct abilities.
- Mentor progression.
- Expanded saber combat.

All numbers here are defaults. As in Milestone 1, the live values go into `config/oldrepublic/balance.json` through `BalanceConfig` sections (section 13 lists every new key). Where this spec and the brief disagree, the brief wins and the difference goes into `DECISIONS.md`.

Contents:

1. Scope, and what is left for later
2. Player journey
3. Alignment
4. Factions, reputation, paths and ranks
5. Chapter Two: the Crossing and the three paths
6. Mentors and trust
7. Skill trees
8. Abilities
9. Expanded saber combat and new NPCs
10. Controls and HUD
11. Persistence, multiplayer, dedicated server and config
12. Effects and sounds
13. Balance
14. Architecture and work split
15. Shared identifiers
16. Test plan
17. Lore notes for `DECISIONS.md`
18. Open questions

---

## 1. Scope

### In Milestone 2

| Area | What ships |
|---|---|
| Story | **Chapter Two, "Two Voices".** After Chapter One a Jedi and a Sith both sense the player, and they meet them together at **the Crossing**. The player chooses to follow the **Jedi Watchwoman**, follow the **Sith Overseer**, or **walk their own way** (Independent). Each path has a short questline: two lessons, a shared mission ("Smoke on the Ridge") and a rank trial. Independents get the mission and their own ending. |
| Alignment | A light/dark value from -100 to +100. It moves through one-time story choices, dialogue answers and capped contextual actions, never through ordinary use of an ability. It gates side-specific skills and powers, mentor acceptance and rank trials. |
| Reputation | Three factions: **Jedi Order**, **Sith Empire** and **Frontier Settlers**. Five tiers from Hostile to Honoured. Reputation gates rank promotion and changes how settlers and mentors respond. |
| Paths and ranks | Path: None, Jedi, Sith or Independent. Ranks in M2: Jedi **Initiate → Padawan**, Sith **Acolyte → Apprentice**. Higher ranks are left for M3 and later. |
| Mentors | Two mentor NPCs with a camp, lessons and a per-mentor **trust** value. Lessons teach nodes and forms. Daily training sessions (at most one per in-game day) and atonement/embrace sessions. |
| Skill trees | A data-driven tree system with four tabs: **Universal Force**, **Lightsaber Combat**, **Jedi Discipline** and **Sith Domination**. The Force Push branch choice moves into it. Nodes have level, prerequisite, path and alignment requirements. Exclusive upgrade groups, dormant nodes, slots and respec rules. A redesigned Force screen. |
| Abilities | Five new powers: **Force Pull** and **Force Jump** (universal), **Saber Throw** (lightsaber), **Force Heal** (Jedi signature) and **Force Lightning** (Sith signature). Each has upgrade branches, mastery, counterplay, animation and a VFX/SFX treatment. Force Push moves onto the new ability framework without changing its behaviour. |
| Saber combat | A three-hit light combo, a heavy attack, a lunge, two forms (**Form III Soresu** and **Form IV Ataru**), a shared saber stamina pool, guard/parry/stagger rules for NPCs with sabers, and a sparring system with no death risk. |
| New NPCs | **Sith Acolyte** (saber duelist with blocks, parries and a Force shove), **Poacher Captain** (melee pressure plus a pistol, and can yield), **Frontier Settler** (civilian, can be held captive), and the two mentors. |
| Controls | Four ability slots with keys R, Z, V and B. The heavy attack and lunge use vanilla key combinations. A form key (unbound by default). |

### Left for later milestones (on purpose)

- **M3 and later:** the redemption and corruption questlines that let a player switch path; ranks Knight/Master and Lord/Darth; the Exploration and Survival tree; Jedi enclave and Sith academy structures (M2 mentors use a small camp); holocrons; faction missions beyond the one shared mission; the Republic faction and its reputation; vendors and reputation rewards.
- More powers from the brief: Force Sense, Speed, Telekinetic Lift, Stasis, Barrier, Protect, Valor, mind influence, Choke, Drain, Fear, Rage, Crush.
- More saber forms (Shii-Cho, Makashi, Djem So, Niman, Juyo), double-bladed sabers, dual-wielding, blade locks, hilt-specific sound profiles.
- Visible signs of alignment on the character (eyes, skin), voice acting, companions and NPC apprentices.
- The first major boss (the brief puts it in M3). The Poacher Captain is an elite enemy, not a boss.

---

## 2. Player journey

1. **Quiet.** Chapter One is complete: the player has built their saber. One in-game day later, or the next time they sleep (whichever comes first, at a safe moment), the **Two Voices** dream plays. Two speakers are heard: one calm and warm, one cold and hungry. A journal entry follows.
2. **Follow the voices.** While the player holds the journal, a pull points toward a meeting point 64 to 128 blocks away on open high ground, using the Calling mechanism from Chapter One with a different look and sound.
3. **The Crossing.** At the meeting point, **Master Ilyra Saelen** (a Jedi Watchwoman of this frontier sector) and **Overseer Teyvar Sorn** (a Sith recruiter from the Empire) stand facing each other with their blades lit, a few steps apart. Neither attacks: the cold peace after the Treaty of Coruscant holds them back. Each speaks to the player. The player follows one of them, or refuses both. The mentor who is not chosen says a parting line and walks away. The choice is final for this milestone (section 5.6).
4. **Lessons.** The chosen mentor sets up a small camp at the Crossing. **Lesson 1, "The Reach"** teaches Force Pull. **Lesson 2** is a sparring lesson: "The Still Blade" (Jedi, teaches Soresu) or "Relentless" (Sith, against the rival acolyte **Brenn Ostrak**, teaches Ataru).
5. **Smoke on the Ridge.** What is left of the poacher crew from Chapter One regroups under **Captain Hessa Dray**, raids a frontier homestead and takes three settlers captive. Smoke rises from their camp. The player fights the crew and makes two choices: whether to spare or execute the Captain when she yields, and whether to free the captives or make them pay for their freedom. Independents get this mission as well.
6. **Trial.** Back at camp the mentor reacts to those choices. Once the requirements are met (level, trust, reputation, alignment), the rank trial opens. Jedi: **Trial of the Blade**, a sparring duel with the master. Sith: **Trial of Ascension**, a duel with Brenn that ends in a final choice. Success brings the rank (Padawan or Apprentice), the signature power (Force Heal or Force Lightning), skill points and the fourth ability slot. Independents finish with **"No Master"**.
7. **Ongoing.** The player can hold a daily session with the mentor (trust and XP), atone or embrace, respec their skills, and build out their trees through normal play.

---

## 3. Alignment

### 3.1 Model

- One integer per player, **-100 (dark) to +100 (light)**, starting at 0. It is stored in the identity attachment (section 11) and synced to the owner only.
- **Bands:**

| Band | Range | Name shown |
|---|---|---|
| `LIGHT` | +50 to +100 | Light |
| `LEANING_LIGHT` | +15 to +49 | Leaning to the Light |
| `BALANCED` | -14 to +14 | Balanced |
| `LEANING_DARK` | -49 to -15 | Leaning to the Dark |
| `DARK` | -100 to -50 | Dark |

- Alignment is **separate from path**. A Jedi can drift dark and an Independent can lean light, as the brief asks.

### 3.2 What moves it

Every change goes through `Identity.shift(player, AlignmentShift)` with a `sourceId`. There are three kinds.

**Major (one time per `sourceId`, no cap).** Story choices. Once a `sourceId` is recorded, the same choice can never move alignment again.

| Source id | Choice | Change |
|---|---|---|
| `reckoning.captain` | Spare the yielded Captain | +12 |
| | Execute her | -12 |
| | Walk away and let her flee | +3 |
| `reckoning.captives` | Free the captives | +10 |
| | Make them pay for their freedom | -10 |
| | Leave them bound and go | -3 |
| `sith_trial.brenn` | Spare Brenn after he yields | +15 |
| | Kill him | -15 |
| `dialogue.<id>.<node>` | Dialogue answers that carry a stance (3 or 4 per path, marked in the dialogue JSON) | ±3 to ±5 each |

**Atonement and embrace (one per in-game day, path players only).** A daily session option with the mentor: "Atone" gives +4 (Jedi), "Embrace" gives -4 (Sith). Only offered while the player's alignment blocks their rank trial (section 5.5).

**Contextual (minor, capped).** Detected by the identity feature itself, so no ability has to report "I was used". The same ability can be neutral, light or dark depending on what it is used for.

| Act (`ContextAct`) | Detected when | Change | Cap |
|---|---|---|---|
| `HELPED_OTHER` | Healing another player or a settler (Shared Light reports this), or freeing a captive villager | +1 | minor cap |
| `DEFENDED_INNOCENT` | Killing a hostile mob that damaged a villager or settler in the last 100 ticks | +1 | minor cap |
| `CRUELTY` | Force Lightning or Push slam damage dealt to a passive animal, or any damage to a yielded enemy that is not the confirmed execution | -1 | minor cap |
| `HARMED_INNOCENT` | Damaging a villager, wandering trader or settler (once per target per 200 ticks) | -2 | minor cap |
| `KILLED_INNOCENT` | Killing a villager or wandering trader (-4), or a settler (-8) | -4 / -8 | severe cap |

Anti-exploit rules:

- **Minor cap:** contextual changes add up to at most **+6 and -6 per in-game day** (counted separately; the day index comes from game time, as with meditation). They can never move alignment past **±60**. Only major choices and atonement go beyond that.
- **Severe cap:** killing innocents is not limited by the minor cap and can go past ±60, but it is capped at **-24 per in-game day**, so a villager-farm accident cannot wipe out a character in one go.
- Major choices are one-time per `sourceId`; repeating an encounter never pays twice.
- Creative mode, spectators and command-forced changes are logged and still apply. Operators use `/oldrepublic identity alignment` to set the value directly.
- Ordinary combat against hostile mobs, farming animals and harvesting never move alignment.
- No passive drift over time.

### 3.3 How it is shown

- **Standing tab** of the Force screen (section 7.7): a horizontal bar from deep red-black on the left to pale gold-white on the right, with a marker, the band name, the exact number, and the last 8 shifts (reason text, amount, game day).
- **Shift toast** (top left, under the Force HUD, 3 s): "Your choice echoes toward the Light (+12)" or "...toward the Dark (-12)", with a soft rising tone (`identity.alignment_light`) or a low falling murmur (`identity.alignment_dark`). Contextual shifts of ±1 show only a small text line, without the tone, so they do not nag.
- **Band change:** a banner ("You lean toward the Light").
- **Force screen emblem:** the attunement emblem is tinted slightly toward gold (light) or red (dark), at most 35% at the extremes. This is a UI cue only, not a character visual.
- **Journal codex** entry "The Light and the Dark" unlocks at the Crossing and explains the bands.
- Alignment is recorded from the start, but no toasts appear before the Crossing, so mechanics are introduced gradually.

### 3.4 What it gates

| Gate | Rule |
|---|---|
| Side passive nodes, tier 1 | Jedi Discipline: alignment ≥ +15. Sith Domination: ≤ -15. |
| Side passive nodes, tier 2 | Jedi: ≥ +40. Sith: ≤ -40. |
| Owned side passives | Become **dormant** (no effect, shown cracked amber, not refunded) while the requirement is no longer met. They work again as soon as it is met. |
| Jedi powers (Force Heal) | Normal at ≥ -14. **Opposed** at -49 to -15: cost ×1.5 and no mastery gain. **Refused** at ≤ -50 ("The Light does not answer"). |
| Sith powers (Force Lightning) | Mirror image: normal at ≤ +14, opposed at +15 to +49, refused at ≥ +50. |
| Mentor acceptance | The Jedi does not accept a Dark (≤ -50) student; the Sith does not accept a Light (≥ +50) one. At the Crossing everyone is close to 0, so both accept. This matters for Independents who look for a mentor later. |
| Rank trial | Jedi Trial of the Blade: ≥ 0. Sith Trial of Ascension: ≤ 0. |
| Lessons | A path player in the opposed band for their path (Jedi ≤ -15, Sith ≥ +15) gets no new lessons until they atone or embrace. Daily sessions are still offered. |

Universal and Lightsaber nodes never depend on alignment.

---

## 4. Factions, reputation, paths and ranks

### 4.1 Factions (M2)

| Id | Name | Who they are in M2 |
|---|---|---|
| `jedi_order` | Jedi Order | Master Saelen and, through her, the Order |
| `sith_empire` | Sith Empire | Overseer Sorn, Brenn, Imperial interest in the frontier |
| `frontier` | Frontier Settlers | Homesteaders of this world (the captives) |

The Republic and the Mandalorians are left for later. The faction list is an enum plus lang keys, so adding one later is cheap. The poacher crew is simply hostile and has no reputation.

### 4.2 Reputation

- Each faction has an integer from -1000 to +1000, starting at 0.
- Tiers: **Hostile** ≤ -500, **Distrusted** -499 to -100, **Neutral** -99 to +99, **Trusted** +100 to +499, **Honoured** ≥ +500.

| Event | Jedi Order | Sith Empire | Frontier |
|---|---|---|---|
| Join the Jedi at the Crossing | +150 | -100 | |
| Join the Sith | -100 | +150 | |
| Lesson completed | +40 (own) | +40 (own) | |
| Smoke on the Ridge completed | +80 (Jedi path) | +80 (Sith path) | +100 (Independent) |
| Captain executed / spared | -40 / +20 (Jedi path only) | +20 / -30 (Sith path only) | |
| Captives freed / pay for freedom / left | +20 / -40 / 0 (Jedi path) | -20 / +20 / 0 (Sith path) | +150 / -150 / -60 |
| Rank trial passed | +150 (own) | +150 (own) | |
| Each settler killed by the player | | | -100 |

Faction reputation only reacts to choices the mentor witnessed or was told about. That means the player's own path, plus Frontier for anything involving settlers.

### 4.3 Effects of reputation in M2

| Faction | Effect |
|---|---|
| Own path faction | A rank trial needs **Trusted** (≥ 100). Knight and Lord will need **Honoured** (M3, stated in the Standing tab as "later"). |
| Other path faction | An Independent can only ask a mentor to take them on later if that mentor's faction is above Distrusted (≥ -99). |
| Frontier | **Trusted:** freed settlers leave a supply bundle (vanilla loot table `oldrepublic:gameplay/settler_thanks`: bread, cooked meat, an iron ingot or two, rarely a golden carrot). **Distrusted or worse:** settlers flee from the player on sight and cannot be talked to. |

The Standing tab lists every tier and what it unlocks, so the player can see why it matters.

### 4.4 Paths and ranks

- **Path:** `NONE` until the Crossing, then `JEDI`, `SITH` or `INDEPENDENT`.
- **Allowed changes in M2:**
  - `NONE` → any path.
  - `INDEPENDENT` → `JEDI` or `SITH` once, later (section 5.6).
  - `JEDI` ↔ `SITH` is impossible in play. Only the operator command `/oldrepublic identity path <player> <path> force` can do it.
- **Rank** (integer):

| Rank | Jedi | Sith | Independent |
|---|---|---|---|
| 0 | none | none | "Wanderer" (title only) |
| 1 | Initiate (on joining) | Acolyte (on joining) | |
| 2 | Padawan (trial) | Apprentice (trial) | |
| 3 | Knight (later) | Lord (later) | |
| 4 | Master (later) | Darth (later) | |

- **Rank 2 rewards:** signature power node, +2 skill points, the fourth ability slot, Resonance milestone `first_rank` (+500), `rank_up` effect and banner.
- An Independent who finishes Smoke on the Ridge gets the fourth slot and the same `first_rank` Resonance milestone ("No Master"). The milestone id is shared, so it can never be granted twice.

---

## 5. Chapter Two: the Crossing and the three paths

The quest feature owns all of this. Chapter Two state lives in the new **story attachment** (section 11.1); Chapter One's `ForceData.questStage` stays as it is. Stages only move forward. Every reward is protected by a one-time flag or by the stage change, exactly as in Chapter One.

### 5.1 Crossing stages (`CrossingStage`, stored by ordinal, append only)

| Stage | Enter when | Objective (journal) | Leaves when |
|---|---|---|---|
| `NOT_STARTED` | default | | Chapter One `COMPLETE` |
| `WAITING` | Chapter One complete | (none shown) | 24,000 ticks after completion, or waking from ≥ 100 ticks of sleep, at a safe moment (same safety rule as the Awakening: not hurt by a mob for 100 ticks, on the ground). Overworld only; otherwise wait. |
| `VOICES` | the Two Voices dream ends | "Follow the voices while holding the journal" | owner within 24 blocks of the meeting point |
| `MEETING` | arrival | "Speak with the two who wait for you" | a choice is confirmed |
| `DECIDED` | choice made | | |

**Two Voices dream.** It reuses the dream overlay with a new variant: 6 s, three subtitled lines alternating between a warm and a cold voice, and a new bed sound `story.two_voices`. Taking damage ends it early and the stage still advances.

**Meeting point** (server, `MeetingSites.find`), searched when `VOICES` begins:

1. Candidate points on rings at 64, 96 and 128 blocks from the player, 16 angles each. Only chunks that are already loaded are considered.
2. A candidate passes when its 9×9 footprint is natural ground (block tag `#oldrepublic:story_ground`: grass, dirt, coarse dirt, podzol, sand, red sand, gravel, stone, snow, terracotta), the height varies by ≤ 2 across it, there is no fluid, it is outside spawn protection (`MinecraftServer#isUnderSpawnProtection`, API-CHECK), and a 13×13 area around it contains no block from `#oldrepublic:built_blocks` (beds, chests, barrels, crafting tables, doors, planks, glass, wool, and similar).
3. The best candidate has the highest score = height above the ring average + openness (sky-visible columns).
4. Fallbacks: rings at 24 to 40 blocks, then a spot 12 blocks in front of the player.

The pull while the journal is held is the Calling effect, variant 2: a pale warm shimmer and a cold drifting dust together, moving along the direction, with a soft two-tone murmur instead of the crystal ping. The exact position is never sent to the client.

**At the meeting point.** Both mentors spawn right away (the point is in loaded chunks): 6 blocks apart, facing each other, blades lit (Jedi green, Sith red; blade colour is cosmetic, as in M1). Both are persistent and owned by the player. When the owner comes within 12 blocks they turn to face them.

**The choice** goes through the dialogue system (section 6.4). Talking to either mentor shows their pitch (2 or 3 lines), a "Tell me more" branch and these options:

| Option | Confirmation | Result |
|---|---|---|
| "I will learn from you." | "Once you walk this road there is no simple way back. Follow the Jedi?" Yes / Not yet | `Identity.setPath(JEDI)`, rank 1, reputation per 4.2, trust 20. The other mentor says a parting line, retracts, walks away and despawns 16 blocks out (or after 30 s, out of sight). Camp placed. |
| (same, with the Sith) | | Mirror image. |
| "Neither of you." (either dialogue) | "Walk your own way?" | Path `INDEPENDENT`, node `unbound` granted. Both mentors leave. |
| "Not yet." | | Nothing happens. The mentors keep waiting (persistent). |

Rewards for any confirmed choice: 300 XP and Resonance milestone `crossing` (+200).

### 5.2 Path stages (`PathStage`, one value shared by all paths)

`NONE, LESSON_ONE, LESSON_TWO, RECKONING, DEBRIEF, TRIAL, COMPLETE`. Independents go `NONE → RECKONING → COMPLETE`.

#### Jedi path: "The Watchwoman's Way"

| Stage | Task | Rewards |
|---|---|---|
| `LESSON_ONE` "The Reach" | Talk to Master Saelen; she teaches Force Pull (node granted free). Then pull 5 targets: dropped items or hostile creatures. Counted from `QuestHooks.onAbilityHit` for `force_pull`; items count through the item variant of the hook. | 300 XP, trust +15, Jedi +40, entry `the_reach` |
| `LESSON_TWO` "The Still Blade" | A training spar: she makes slow, clearly telegraphed strikes (one every 50 ticks). Parry 3 of them. Spar rules `PARRIES ×3` (section 9.6). Teaches Form III Soresu (node granted free). | 300 XP, trust +15, Jedi +40, entry `the_still_blade`, codex `saber_forms` |
| `RECKONING` "Smoke on the Ridge" | Section 5.3 | |
| `DEBRIEF` | Talk to the mentor. Her lines depend on the choices; trust changes per section 6.3. | entry `the_watchwoman_listens` |
| `TRIAL` "Trial of the Blade" | Offered when: level ≥ 6, trust ≥ 60, Jedi Order ≥ Trusted, alignment ≥ 0. If any requirement fails, the dialogue names it (for alignment: "The dark clouds you", entry `the_dark_clouds`, and the Atone option). The trial is a spar: rules `YIELD`, master yields at 35% health, the player loses at 20%, the master's damage is scaled ×0.6. On a loss, retry after 600 ticks; nothing else is lost. | On a win: rank Padawan, node `force_heal`, +2 SP, 800 XP, Jedi +150, Resonance `first_rank` +500, slot 4, entry `padawan_of_the_watch`, codex `force_heal` |
| `COMPLETE` | Daily session: guided meditation with the master (the normal 8 s meditation within 8 blocks of her). | Once per in-game day: 120 XP and trust +5 (replaces the solo daily meditation that day) |

#### Sith path: "The Overseer's Due"

| Stage | Task | Rewards |
|---|---|---|
| `LESSON_ONE` "What Is Owed" | Overseer Sorn teaches Force Pull (granted free). Pull 3 hostile creatures to you and kill each one within 60 ticks of pulling it. Uses `onAbilityHit` and a pulled-entity timestamp map in the story service. | 300 XP, trust +15, Sith +40, entry `what_is_owed` |
| `LESSON_TWO` "Relentless" | Spar with Brenn Ostrak (who guards a lot). Land 3 heavy attacks or lunges (`onSaberTechnique` with `heavy` or `lunge` and hit = true). Spar rules `TECHNIQUE_HITS ×3`. Teaches Form IV Ataru (granted free). | 300 XP, trust +15, Sith +40, entry `relentless`, codex `saber_forms` |
| `RECKONING` | Section 5.3, with the Overseer's framing: "Take back what was stolen. Leave no witnesses." | |
| `DEBRIEF` | Talk to the Overseer. | entry `the_overseer_weighs` |
| `TRIAL` "Trial of Ascension" | Requirements: level ≥ 6, trust ≥ 60, Sith Empire ≥ Trusted, alignment ≤ 0. If alignment fails: "Still too soft", entry `still_too_soft`, Embrace option. A duel with Brenn: rules `YIELD`, Brenn at full damage (×1.0), yields at 15%, the player loses at 20% (the Overseer stops the fight). When Brenn yields, a dialogue offers **Spare** (+15 alignment, trust -20; the trial still passes: "Weak. But you won.") or **Kill** (-15, trust +15). | On a pass: rank Apprentice, node `force_lightning`, +2 SP, 800 XP, Sith +150, Resonance `first_rank`, slot 4, entry `apprentice`, codex `force_lightning` |
| `COMPLETE` | Daily session: a spar with Brenn if he lives, otherwise with a training remote (an invisible spar with 3 telegraphed shoves). | Once per in-game day: 120 XP, trust +5 |

If Brenn is spared he leaves the camp (flag `story:brenn_spared`, a hook for M3).

#### Independent: "My Own Way"

| Stage | Task | Rewards |
|---|---|---|
| `RECKONING` | Starts 1 in-game day after the Crossing, or right away if the player reads entry `my_own_way` and holds the journal. Smoke on the Ridge with no mentor framing. | |
| `COMPLETE` "No Master" | Finish the mission. | 1,000 XP (the mission reward), +3 SP (not +1), Frontier +100, slot 4, Resonance `first_rank`, entry `no_master` |

Independents keep the solo daily meditation from Chapter One.

### 5.3 Smoke on the Ridge (shared mission)

- **Site.** 96 to 160 blocks from the mentor camp (Independent: from the player), chosen with the same `MeetingSites` rules using a 13×13 footprint. (Milestone 6: the template is now Captain Dray's stronghold, 51×26×51, placed by far placement 450 to 750 blocks out on high ground first; this search, with footprint 51, clearance 75 and slope 6 from its site meta, is the fallback.) The template `oldrepublic:story/raider_camp` is placed there: palisade stubs, tents, crates, **two lit campfires** (their vanilla smoke column guides from a distance), three binding posts, and one landmark "smashed homestead cart". The pull from the journal points to it (Calling variant 3: drifting ash motes).
- **Placement.** The terrain is adapted: dirt or stone is filled down to the ground under the footprint, natural blocks (grass, flowers, leaves, snow) above it are cleared, and nothing else is removed. If `story.placeStructures` is false (server config), no blocks are placed and only the entities spawn (section 11.3).
- **Spawn.** The entities spawn the first time the owner comes within 48 blocks; until then the site only exists as data. All of them are persistent and tagged with the owner's UUID and the encounter id:
  - 4 Crystal Poachers (existing entity), +1 per extra awakened player within 32 blocks when it spawns (at most +3);
  - 1 **Sith Acolyte** in `HOSTILE` mode ("a deserter the Captain hired"; the same entity on every path);
  - **Captain Hessa Dray** (`poacher_captain`);
  - 3 **Frontier Settlers** in `CAPTIVE` state at the posts. Poachers never target them.
- **Crew defeated** = every hostile encounter mob is dead, and the Captain has yielded or is dead. Any player can deal the damage; the kills count for the owner.
- **Captain choice.** She yields at 25% health (section 9.7.3). Only the owner decides:
  - Spare: she hands over the **Poachers' Ledger** (`poachers_ledger`, a written-book-like lore item: who buys the crystals; a hook for M3) and leaves.
  - Execute: confirmed with a second strike within 60 ticks, or the Execute dialogue option. The ledger drops.
  - Walk away (owner more than 32 blocks away, or 2,400 ticks pass): she flees and drops the ledger at the post.
- **Captives choice.** One decision for the group, made through any captive's dialogue:
  - "Cut them loose": they run home, and leave the supply bundle if Frontier is Trusted.
  - "Your freedom has a price": they hand over the bundle loot, then run.
  - "Leave them": they stay bound.
  - If the owner leaves the site (128 blocks) with captives still unresolved, that counts as **Leave them**.
- **Accidents.** If one of the player's area effects kills a captive, that is `KILLED_INNOCENT` (-8) and Frontier -100. Captives take ×0.25 damage from player area effects (lightning chain, jump shockwave, push slam) to make accidents rarer.
- **Completion.** Crew defeated, Captain resolved and captives resolved (or abandoned). Rewards: 1,000 XP, +1 SP (Jedi/Sith) or +3 SP (Independent), Resonance milestone `reckoning` (+300), faction reputation per 4.2, entries `smoke_on_the_ridge` (start), one of `a_life_spared` / `a_debt_collected` / `she_ran`, one of `cut_loose` / `the_price_of_freedom` / `left_bound`, and `the_ledger` once the ledger is picked up.
- **Cleanup.** Entities that remain after completion despawn once the owner is 128 blocks away. The blocks stay as environmental history; nothing is removed from the world.

### 5.4 Journal (Chapter Two)

The journal screen gains chapter headers in the Entries feed: "I. Echoes in the Stone", "II. Two Voices". The left page shows the current Chapter Two objective once Chapter Two has started. Entries keep the first-person voice of Chapter One. Codex entries keep the "Notes gathered" voice.

| Entry id | Title | Unlocked by |
|---|---|---|
| `two_voices` | Two Voices | dream |
| `the_crossing` | The Crossing | arrival at the meeting point |
| `the_watchwoman` / `the_overseer` / `my_own_way` | The Watchwoman / The Overseer / My Own Way | the choice |
| `the_reach` / `what_is_owed` | The Reach / What Is Owed | lesson 1 complete |
| `the_still_blade` / `relentless` | The Still Blade / Relentless | lesson 2 complete |
| `smoke_on_the_ridge` | Smoke on the Ridge | mission start |
| `a_life_spared` / `a_debt_collected` / `she_ran` | | Captain resolved |
| `cut_loose` / `the_price_of_freedom` / `left_bound` | | captives resolved |
| `the_ledger` | The Ledger | ledger picked up |
| `the_watchwoman_listens` / `the_overseer_weighs` | | debrief |
| `the_dark_clouds` / `still_too_soft` | | trial blocked by alignment |
| `padawan_of_the_watch` / `apprentice` / `no_master` | | chapter complete |

Codex: `jedi_order` ("The Jedi in the Cold Peace"), `sith_empire` ("The Empire Beyond the Rim"), `jedi_watch` ("Watchmen of the Order"), `sith_overseers`, `the_frontier`, `light_and_dark`, `saber_forms`, `force_heal`, `force_lightning`.

### 5.5 Mentor reactions and blocked trials

- Path players whose alignment blocks the trial, or who are in the opposed band (no new lessons), get the **Atone** (Jedi) or **Embrace** (Sith) daily session: an 8 s meditation within 8 blocks of the mentor. It gives ±4 alignment and trust +3, once per in-game day, separately from the normal session.
- The player is never stuck: atonement, sessions, lessons and the mission together can always reach every requirement.

### 5.6 How replayable the choice is

- There is one Crossing per character. Joining Jedi or Sith is permanent in M2. The redemption and corruption questlines that let a player switch are left for M3 or later (open question 1).
- An **Independent may join a mentor once, later.** In the journal, the meditation option "Reach out" shows the two voices again. The mentor arrives within 60 s near the player if: their faction is above Distrusted, the player's alignment is allowed (3.4), and they are not refused. Joining late starts that path at `LESSON_ONE`. Smoke on the Ridge is not repeated: the debrief uses what the player already chose, and the trial follows as normal. Joining removes `unbound` (dormant, see 7.3), and the fourth slot stays.
- `/oldrepublic story reset` (operator) clears Chapter Two progress for testing. It does not reset alignment or reputation: use the identity commands for those.

---

## 6. Mentors and trust

### 6.1 Mentor NPCs

| | Jedi | Sith |
|---|---|---|
| Entity | `oldrepublic:jedi_mentor` | `oldrepublic:sith_mentor` |
| Name (lang) | Master Ilyra Saelen, Jedi Watchwoman | Overseer Teyvar Sorn |
| Look | Human woman, weathered sand and brown robes with a dark under-tunic, a hood that is usually down, grey-streaked hair (64×64 skin, outer layer used for the robe) | Human man, Imperial greys under a black half-cape, a scar over one brow, gloved |
| Saber | Green blade, Guardian emitter look | Red blade, Duelist emitter look |
| Creed (drives trust) | Mercy, restraint, protecting the defenceless | Strength, taking what is owed, no witnesses |

Common behaviour (mentor package, quest worker):

- A `PathfinderMob` that is invulnerable to everything except `/kill` and the void. Attacking a mentor does nothing except a line ("Save it for the training ground.") and trust -5, at most once per 200 ticks.
- Persistent; never despawns. **Owned** by one player UUID. Other players see the mentor, but interacting gives "She is waiting for someone else."
- Stays within 6 blocks of its **anchor** (the camp). When the owner is within 8 blocks: looks at them. Otherwise: idle loop (meditates crouched, or walks between camp points).
- Holds a lightsaber `ItemStack` built with `SaberAssembly`: ignited during the Crossing, demonstrations and spars, otherwise retracted. Uses the saber feature's `DuelistCombatGoal` (9.6) only during spars.
- **Lost mentor:** if the owner's mentor UUID cannot be found when they reach out (chunk deleted, killed by command), a new one is spawned at the anchor. There is never more than one mentor per owner: the spawn code looks up the UUID first and removes duplicates found on load.
- **Far away or in another dimension:** journal meditation "Reach out" → "Ask them to come". After 60 s the mentor arrives near the player in the overworld and the anchor moves there. The old camp stays as flavour.

### 6.2 Camps

Templates `oldrepublic:story/jedi_camp` and `oldrepublic:story/sith_camp`, 7×7, placed at the meeting point with the same rules as 5.3.

- **Jedi camp:** a bedroll, a low stone seat, a lantern on a post, a training stump, a small canvas lean-to.
- **Sith camp:** a dark field shelter, an Imperial supply crate, a brazier (campfire), a weapon rack with a training staff.

No floor circles or sigils.

### 6.3 Trust

- One value per mentor, 0 to 100, stored in the identity attachment (`trust.jedi`, `trust.sith`). On joining, the chosen mentor's trust is set to **20**.

| Event | Jedi | Sith |
|---|---|---|
| Lesson completed | +15 | +15 |
| Smoke on the Ridge completed | +10 | +10 |
| Captain spared / executed / fled | +10 / -20 / 0 | -15 / +10 / -5 |
| Captives freed / paid / left | +10 / -20 / -5 | -5 / +10 / 0 |
| Dialogue answers that match / oppose the creed | +3 / -3 (one time each) | same |
| Daily session | +5 (once per in-game day) | +5 |
| Atone / Embrace session | +3 | +3 |
| Attacking the mentor | -5 (at most once per 200 ticks) | same |

- **Thresholds:** lesson 1 at 20 (immediate), lesson 2 at 30, trial at 60.
- With neutral choices the path reaches 20 + 15 + 15 + 10 = 60 at the debrief. Choices that oppose the creed cost a few in-game days of sessions to make up for.
- Trust is shown in the Standing tab: mentor portrait, a bar with the lesson and trial thresholds marked, and the current lesson's state.

### 6.4 Dialogue system (identity worker, used by everyone)

- **Data-driven.** `data/oldrepublic/dialogue/<path>.json`, loaded on server start and on `/reload`.

```json
{
  "speaker": "oldrepublic:jedi_mentor",
  "start": "greet",
  "nodes": {
    "greet": {
      "line": "dialogue.oldrepublic.crossing_jedi.greet",
      "options": [
        { "text": "dialogue.oldrepublic.crossing_jedi.opt.join", "if": { "path": "none" }, "next": "confirm" },
        { "text": "dialogue.oldrepublic.common.opt.more", "next": "lore" },
        { "text": "dialogue.oldrepublic.common.opt.leave", "close": true }
      ]
    },
    "confirm": {
      "line": "dialogue.oldrepublic.crossing_jedi.confirm",
      "options": [
        { "text": "dialogue.oldrepublic.common.opt.yes", "action": "story:join_jedi", "close": true },
        { "text": "dialogue.oldrepublic.common.opt.not_yet", "next": "greet" }
      ]
    }
  }
}
```

- **Conditions** (`if`, all must hold): built in are `path`, `rank_min`, `alignment_min`, `alignment_max`, `trust_min` (`{"mentor":"jedi","value":30}`), `reputation_min`, `flag`. Features add their own with `DialogueConditions.register`, for example `story:stage`.
  - An option whose condition fails is shown **disabled**, with the reason from the condition (for example "Requires Trusted standing with the Jedi Order"), unless it is marked `"hidden_if_unmet": true`.
- **Stance:** `"stance": {"source": "<id>", "alignment": 5, "trust": {"jedi": 3}}` on an option applies a one-time major alignment shift and trust change when the option is picked.
- **Actions:** `"action": "<id>"` runs a handler that a feature registered with `DialogueActions.register`. Unknown ids are logged and ignored.
- **Server sessions.** The server holds one `DialogueSession` per player (dialogue id, node, speaker entity id, a session counter).
  - It sends `dialogue_node` (S2C) with the speaker, the line key with arguments, and the options (text key, enabled, reason key).
  - The client answers with `dialogue_choice` (session, option index).
  - The server checks the session id, that the speaker is alive and within 6 blocks, and the condition again, then applies.
  - Closing the screen, moving more than 8 blocks away, taking damage, dying, logging out or changing dimension ends the session (`dialogue_end`) with no choice made.
- **Screen.** A holo panel across the lower third (UI kit). It shows the speaker name and, when the speaker is a mentor, a portrait. The line types out at 60 characters per second; click or Space shows it all. Options are numbered 1 to 4 and can be picked with the number keys. The game is not paused (multiplayer-safe); the speaker turns to face the player. Sounds: `dialogue.open` and `dialogue.select`. There is no voice acting; key lines get subtitles through the text itself.
- **Confirmations** for irreversible choices are their own dialogue node (as in the example above), never a single click.

---

## 7. Skill trees

### 7.1 Structure

- **Data-driven trees:** `data/oldrepublic/skill_tree/<tree>.json`, one file per tab, loaded at server start and on `/reload`, and sent to clients on join and after a reload (`skill_trees` payload).
- **Tabs and visibility:**

| Tab | Appears when |
|---|---|
| Universal | Force Push is learned |
| Lightsaber Combat | the first saber is built |
| Jedi Discipline, Sith Domination | the Crossing |

  Both side tabs are visible to every path, because drifting is possible.
- **Node kinds:**

| Kind | Meaning |
|---|---|
| `ability` | Unlocks an active power |
| `branch` | An exclusive upgrade inside a `group` |
| `passive` | 1 to 3 ranks |
| `form` | A saber form; several can be owned, one is active |
| `technique` | A saber move, such as the lunge |

- Node fields:

```json
{
  "id": "pull_disarm",
  "kind": "branch",
  "group": "pull_shape",
  "ability": null,
  "cost": [1],
  "min_level": [6],
  "requires": ["force_pull"],
  "path": null,
  "alignment_min": null,
  "alignment_max": null,
  "granted_only": false,
  "pos": [1, 3],
  "icon": "oldrepublic:skill/pull_disarm",
  "info": ["knock_mult", "disarm_seconds"]
}
```

  - `cost` and `min_level` are arrays with one entry per rank.
  - `granted_only` nodes cannot be bought; quests and lessons grant them.
  - `info` lists the value keys the tooltip shows, filled from the ability's `info()` map, which comes from server balance.

### 7.2 Node table

"Lv" is the minimum level per rank. "Req" lists the prerequisites. Exclusive groups are marked "| group".

**Universal Force (`universal`)**

| Id | Kind | Cost | Lv | Req | Effect |
|---|---|---|---|---|---|
| `force_push` | ability, granted | – | – | | Chapter One. Unchanged. |
| `push_repulse_wave` / `push_focused_blast` / `push_deflecting_pulse` | branch, group `push_shape` | 1 | 3 | force_push | M1 branches, numbers unchanged |
| `force_pull` | ability | 2 | 4 | force_push | 8.1; lesson 1 grants it free |
| `pull_disarm` / `pull_reel` | branch, group `pull_shape` | 1 | 6 | force_pull | 8.1 |
| `force_jump` | ability | 2 | 5 | force_push | 8.2 |
| `jump_crushing_descent` / `jump_soaring_leap` | branch, group `jump_shape` | 1 | 8 | force_jump | 8.2 |
| `deep_reserves` | passive ×3 | 1 / 1 / 2 | 2 / 5 / 9 | | +8 max Force Energy per rank |
| `quickened_flow` | passive ×2 | 1 / 2 | 4 / 8 | deep_reserves 1 | +10% energy regen and -5 ticks regen delay per rank |
| `unbound` | passive, granted, path Independent | – | – | | Universal power costs ×0.85 and cooldowns ×0.9. Dormant once on a path. |

**Lightsaber Combat (`saber`)**

| Id | Kind | Cost | Lv | Req | Effect |
|---|---|---|---|---|---|
| `saber_lunge` | technique | 1 | 4 | | Lunge (9.3) |
| `steady_guard` | passive ×3 | 1 / 1 / 1 | 3 / 6 / 9 | | +10% saber stamina per rank |
| `deflection_control` | passive | 1 | 5 | | Bolts deflected without a parry go within 20° of the crosshair instead of a random 50° arc |
| `riposte` | passive | 2 | 6 | steady_guard 1 | After a parry, the next hit within 20 ticks deals +40% |
| `form_soresu` | form | 2 | 5 | | 9.4; Jedi lesson 2 grants it free |
| `form_ataru` | form | 2 | 5 | | 9.4; Sith lesson 2 grants it free |
| `saber_throw` | ability | 2 | 7 | saber_lunge | 8.3 |
| `throw_ricochet` / `throw_rending_arc` | branch, group `throw_shape` | 1 | 9 | saber_throw | 8.3 |

**Jedi Discipline (`jedi`)**

| Id | Kind | Cost | Lv | Req | Effect |
|---|---|---|---|---|---|
| `force_heal` | ability, granted, path Jedi | – | – | | 8.4 (Padawan trial) |
| `heal_shared_light` / `heal_serene_ward` | branch, group `heal_shape` | 1 | 8 | force_heal | 8.4 |
| `centered_mind` | passive ×2, alignment ≥ +15 | 1 / 1 | 3 / 6 | | +1 parry-window tick per rank (precision) |
| `calm_focus` | passive, alignment ≥ +40 | 2 | 6 | centered_mind 1 | -10 ticks regen delay, +10% max Force Energy |
| `guardians_resolve` | passive, alignment ≥ +40 | 2 | 9 | calm_focus | While you guard, other players within 6 blocks take 10% less damage |

**Sith Domination (`sith`)**

| Id | Kind | Cost | Lv | Req | Effect |
|---|---|---|---|---|---|
| `force_lightning` | ability, granted, path Sith | – | – | | 8.5 (Apprentice trial) |
| `lightning_chain` / `lightning_consuming` | branch, group `lightning_shape` | 1 | 8 | force_lightning | 8.5 |
| `fury` | passive ×2, alignment ≤ -15 | 1 / 1 | 3 / 6 | | +6% saber and Force damage per rank while below 50% health (a risky burst) |
| `hungering_dark` | passive, alignment ≤ -40 | 2 | 6 | fury 1 | Killing a hostile restores 6 Force Energy (at most once per 40 ticks) |
| `dread_presence` | passive, alignment ≤ -40 | 2 | 9 | hungering_dark | Heavy attacks make non-boss hostiles flee for 40 ticks (fear) |

Every node's tooltip lists its exact numbers, cost, every requirement with ✓/✗, and for powers the Force Energy cost, cooldown, range, duration, mastery effect and counterplay line.

**Expected budget.** Chapter One ends at about level 4 with about 4 unspent points. By the end of M2 (about level 7) a path player has about 9 to 10 spendable points plus the free lesson nodes. The purchasable content is about 14 points per tab (Universal, Lightsaber) and 7 per side tab. That is roughly 40% of the M2 nodes, so builds differ.

### 7.3 Rules (server, `SkillService`)

**Buying a node** (`learn_node` payload) checks in this order:

1. Awakened.
2. The tab is visible.
3. The node is not `granted_only`.
4. Level ≥ `min_level[rank]`.
5. Every requirement is owned and not dormant.
6. Path matches.
7. Alignment requirement is met.
8. The next rank exists.
9. Enough skill points.

The first failure answers with a specific message key (as `BranchChoice.Result` does today).

**Exclusive groups:**

- Buying a node while a sibling in the same group is owned **switches**: cost = node cost + `switchPenalty` (2). The old node is removed and not refunded. This keeps today's Push rule exactly: a first choice costs 1, switching costs 3.
- Switching is refused while the power is charging or channelling, and within 60 ticks of combat (damage dealt or taken).

**Granted nodes** (Push, Heal, Lightning, `unbound`, and lesson grants of Pull and the forms):

- Cost 0. They are never refunded or removed by a respec.
- A lesson that grants a node the player already bought gives **no refund**. The node tooltip warns "Your mentor will teach this" while the matching lesson is still ahead.

**Dormant nodes:**

- An owned node whose alignment or path requirement is no longer met is dormant: no effect, and it is not refunded.
- Abilities follow 3.4 instead.
- `unbound` is dormant on any path.

**Full respec** (the "Unburden" dialogue option at the mentor; Independents use a meditation stone):

- Needs: not in combat (60 ticks), and at most once per **72,000 ticks** of game time (3 in-game days).
- Refunds every point spent minus 10%, rounded up, minimum 1.
- Granted nodes stay. Slots are cleared except for granted abilities. The active form falls back to Unformed unless it was granted.
- `respec` effect; the new Force screen opens on the tree.

**Stat modifiers:** when nodes change or a node becomes dormant or active again, the skill service writes `mod_max_energy`, `mod_regen_pct` and `mod_regen_delay` into `ForceData.counters`. `Progression` reads them (a Phase 0 lead change, section 14.1). Saber passives are read live by the saber feature through `Skills.active(player, id)`.

### 7.4 Force Push migration

- `ForceData.pushBranch` stays as a **mirror** that only the skill service writes, so Force Push code and saved worlds keep working.
- On a player's first load in M2: if `pushBranch` is set and no `push_*` node is owned, grant the matching node at no cost, and grant `force_push` if the ability is known.
- `ChooseBranchPayload`, `BranchChoice` and `PushInfoPayload` are removed. The skill system and the ability-info payload replace them.

### 7.5 Ability slots

- `skills.slots` holds 4 entries, ability id or empty.
- 3 slots are open from the start. The 4th unlocks at rank 2 or on completing No Master.
- Slot 1 is Force Push after migration. A newly learned power goes into the first free slot.
- **Assigning:** pick a power node in the Force screen, then "Assign to slot 1-4", or drag its icon onto a slot. Refused within 60 ticks of combat.
- At most 5 actives exist in M2 (Push, Pull, Jump, Throw, plus Heal or Lightning), so at most one is left unslotted. That loadout choice is intended.

### 7.6 Mastery

- Every power uses `Progression.addMastery` and the existing rank thresholds (50 / 150 / 400).
- Mastery only grows from combat-relevant use, defined per power in section 8.
- Mastery makes a power better within its role. It never changes the branch.

### 7.7 Force screen (redesign; skills worker)

**Layout:** the durasteel panel from the UI kit, at most 460×260 and adapting to small windows as today.

| Part | Contents |
|---|---|
| Header | Emblem (alignment tint) with the XP ring, title, level and XP bar, Resonance, Force Energy, **skill points** |
| Tab strip | Universal · Lightsaber · Jedi · Sith · **Standing**. Tabs appear as unlocked. The Jedi tab is tinted pale gold and the Sith tab deep red; the tint only marks which side, it never hides anything. |
| Tree view (left, about 60%) | Node sockets (20×20 icons) on the grid from `pos`, connected by thin lines. Exclusive groups fork from one small "or" diamond. |
| Detail panel (right, about 40%) | Name, kind, rank pips, the description, live numbers (from ability info, so they come from the server), mastery rank and points to the next rank, requirements with ✓/✗, and buttons: **Learn (n SP)** / **Switch (n SP)**, **Assign to slot**, and **Set active form** for forms. |
| Footer | Slot bar 1-4 with key labels; **Respec** (enabled only when allowed, with the reason otherwise); Done. |

**Node states:**

| State | Look |
|---|---|
| Owned | Lit rim |
| Available | Slow pulsing rim |
| Locked | Dim, with a lock glyph and the first failed requirement under the name |
| Dormant | Cracked amber rim |
| Path-locked | "Jedi path" or "Sith path" label |
| Granted | A small crest |

**Standing tab:** the alignment bar and shift log (3.3); the three faction bars with tier names, tick marks and a "what this unlocks" list; path and rank with the next rank's requirements (each with ✓/✗); the mentor card with trust bar and thresholds; the active lesson.

**Sounds:** `skill.learn` (a crystal click with a rising swell) on purchase, `skill.slot` on assigning, `skill.respec` (an unwinding sound) on respec.

---

## 8. Abilities

Every power has the brief's required fields. Every power:

- is server-authoritative;
- costs Force Energy through `Progression.trySpendEnergy`, which plays the M1 failure feedback;
- runs its cost and cooldown through `AbilityCosts.costMultiplier` and `cooldownMultiplier` (`unbound`, the opposed-alignment rule);
- stores its cooldown in `ForceData.cooldowns` under its ability id;
- is cancelled on death, disconnect, dimension change, a screen opening, or the slot changing;
- sends one effect event per gameplay moment;
- has client VFX/SFX that go through `Vfx` budgets and the flashing and shake settings, and never hide enemy tells.

The common rule for **push-resistant targets** (`#oldrepublic:push_resistant`) and bosses carries over:

- displacement ×0.4;
- control effects (stagger, disarm, shock, fear) get diminishing returns within 200 ticks: the 2nd is half as long, the 3rd and later do nothing.

**PvP:** every power with a `pvpEffects` flag (default false) affects other players only when that flag is on **and** the server allows PvP, never in creative, never on teammates (same scoreboard team). Healing allies ignores the PvP flags. **No power changes terrain in M2.**

### 8.1 Force Pull (`force_pull`, Universal)

| Field | Value |
|---|---|
| Purpose | Control and utility, the counterpart to Push. Single target and precise: yanks one enemy (or loose items) to you. |
| Unlock | Universal tree, level 4, 2 SP, requires Push; lesson 1 grants it free. |
| Input | **Tap.** 3-tick reach (wind-up), then the pull. |
| Cost / cooldown | 16 FE / 120 ticks (6 s). |
| Range and targeting | 12 blocks. Crosshair ray; if no entity is on the ray, the living entity nearest the aim axis within an 8° half-angle cone. Line of sight from the eyes. If there is no living target, every item entity within 2.5 blocks of where the ray ends is pulled. |
| Effect | Living target: velocity toward the caster `= dir × min(1.6, 0.35 + 0.11 × distance) + (0, 0.25 + 0.03 × max(0, height difference), 0)`. While the target is within 2.5 blocks of the caster during the 20 ticks afterwards, its velocity is ×0.3 so it lands at your feet instead of flying past. Push-resistant ×0.4. Knockback resistance reduces it by the same weighting as Push (0.6). Targets that are guarding with a saber and face the caster: ×0.5. |
| Branches (group `pull_shape`, level 6) | **Disarming Pull:** pull ×0.6. A Crystal Poacher or the Captain cannot fire for 120 ticks (the rifle is pulled out of line and shown as lowered; no item changes hands). The Acolyte loses 40 guard stamina and cannot guard for 40 ticks. PvP: the target's main-hand item gets a 40-tick item cooldown. **Reeling Pull:** pull ×1.2. On arrival the target is staggered (Slowness III, 20 ticks), and your next saber hit within 30 ticks deals +30%. |
| Mastery | +1 per hostile living target pulled. Per rank: +5% range, -5% cost. |
| Counterplay | Single target, line of sight, a visible reach before it lands, resistance on heavy and push-resistant targets, saber guard halves it. |
| Animation | The free arm reaches out with fingers clawed (3 ticks), then snaps back toward the chest (6 ticks). Third person: render-state pose `PULL`. First person: the hand jabs forward, then pulls in. |
| VFX | Anticipation: low-alpha `air_ripple` contracting at the palm. Travel: `pull_streak` particles peel off the target's outline and ground and stream toward the caster (the reverse of push streaks), with dust kicked from under the target's feet while it is dragged. Arrival: a small dust puff at the caster's feet. Disarm: a few `saber_spark`-style metal sparks at the weapon. Item pull: items leave short dust trails. No glow outline on the target. |
| SFX | `force.pull.cast` (an inhaled reverse pressure swell, 2 variants), `force.pull.drag` (a short gritty whoosh, positional at the target), `force.pull.catch` (a low thud on arrival), `force.pull.disarm` (a metallic wrench). |
| Multiplayer | Effect `force_pull` (cast: pos = hand, entity = caster, variant = branch); `force_pull_hit` (entity = target, intensity = speed); `force_pull_disarm`. Pulled players (PvP) get their motion through `hurtMarked`, as Push does. |

### 8.2 Force Jump (`force_jump`, Universal)

| Field | Value |
|---|---|
| Purpose | Mobility: reach ledges, close or open distance, set up Ataru aerial strikes. |
| Unlock | Level 5, 2 SP, requires Push. |
| Input | **Hold and release.** Hold to gather (crouched; minimum 4 ticks, full at 16), release to leap. Must be on the ground, or within 3 ticks of leaving it. Holding past 60 ticks cancels at no cost. |
| Cost / cooldown | 14 to 24 FE by charge / 100 ticks. |
| Leap | Vertical speed `lerp(c, 0.75, 1.15)` blocks per tick (apex about 3.5 to 7 blocks). Horizontal `lerp(c, 0.45, 0.9)` along the horizontal look (about 5 to 10 blocks). Set on the server, sent to the client with `hurtMarked`; the client predicts only the visuals (1-RTT delay accepted, as for Push). |
| Fall protection | On the first landing within 80 ticks, fall damage is cancelled up to (this leap's apex height + 4) blocks of fall; anything beyond that counts normally. |
| Landing recovery | Slowness II for 6 ticks (none with Ataru active). |
| Branches (group `jump_shape`, level 8) | **Crushing Descent:** landing after ≥ 3 blocks of fall sends out a shockwave, radius 3, damage `3 + 0.5 × blocks fallen` (at most 8), outward knock 0.8, stagger 15 ticks; push-resistant targets take ×0.5. **Soaring Leap:** horizontal ×1.35, vertical ×1.1. Hold the vanilla Jump key in the air to glide: fall speed capped at 0.15 blocks per tick for up to 30 ticks once per leap, 0.4 FE per tick. |
| Mastery | +1 per leap that covers ≥ 4 blocks while a hostile is within 16 blocks at take-off. Per rank: -5% cost, +4% height. |
| Counterplay | A visible gather, no control in the air, landing recovery, long cooldown. |
| Animation | Crouched gather (vanilla crouch plus arms drawn back). At launch, arms swing up. Glide: arms spread. |
| VFX | Gather: dust (block particles of the ground) gathers inward around the feet, a faint low ripple at ground level (a short-lived shimmer, never a ring decal). Launch: a compressed dust burst at the take-off point, scaled with charge. Travel: subtle `jump_trail` motes behind the player for 10 ticks (Reduced preset: none). Landing: dust and block particles scaled by fall strength and the landing surface (sand bursts, stone chips, snow powder). Crushing Descent: a radial debris spray and a ground-level `air_ripple` shell, plus caster shake 0.25. |
| SFX | `force.jump.gather` (a low rising rush, loops while held), `force.jump.launch` (a pressure thump plus whoosh), `force.jump.land` (soft or heavy, by intensity), `force.jump.slam` (Crushing Descent impact), `force.jump.glide` (an airy loop; stops on landing, death or dimension change). |
| Multiplayer | Effects `force_jump_gather` (start/stop), `force_jump_launch` (intensity = charge), `force_jump_land` (intensity = fall, variant = surface sound group), `force_jump_slam`. Servers with `allow-flight=false`: the leap and the glide stay below the vanilla floating check because the player keeps falling (falling speed ≥ 0.15). The dedicated-server test must confirm this (API-CHECK). |

### 8.3 Saber Throw (`saber_throw`, Lightsaber Combat; saber worker)

| Field | Value |
|---|---|
| Purpose | A ranged saber strike that comes back to the hand. Hits enemies at range without a projectile weapon and works with the lunge and Pull. |
| Unlock | Level 7, 2 SP, requires the lunge. Needs an **ignited** saber in the main hand. |
| Input | **Tap.** |
| Cost / cooldown | 20 FE / 160 ticks, counted from the catch. |
| Flight | A `thrown_saber` entity (noSave) flies along the look at 1.3 blocks per tick for up to 14 blocks or until a block, then homes back to the thrower at 1.5 blocks per tick. If it has not returned after 60 ticks it snaps back. Each living target is hit once per throw (out and back), at most 3. Damage = the saber's ignited damage × 0.8. A target guarding with a saber and facing it **blocks** it: clash sparks, the guard is drained 15 (player) or 20 (NPC), and the saber returns at once. |
| No duplication | The item never leaves the inventory. While thrown, the stack carries a `saber_thrown` component (persistent; holds a throw UUID). The hand renders empty and the item cannot be used. On return, the component is cleared on whichever inventory stack has that throw UUID. On the thrower's death, logout, a dimension change or a server stop, the entity is discarded and the component cleared. A stack found with the component and no matching entity (for example after a crash) is cleared in `inventoryTick` after 60 ticks. |
| Branches (group `throw_shape`, level 9) | **Ricochet:** after the first hit, it turns toward the nearest other hostile within 6 blocks, up to 2 bounces, -20% damage per bounce. **Rending Arc:** slower (1.0 blocks per tick), range 12, passes through targets, damage factor 1.12 (0.8 × 1.4), stagger 15 ticks. |
| Mastery | +1 per hostile hit. Per rank: +5% range. |
| Counterplay | Travel time, can be blocked by saber guards, line of sight; the thrower has no saber until it returns (no guard). |
| Animation | An overhand arm whip; the catch puts the arm up. |
| VFX | The thrown saber entity renders the real blade (full-bright core and glow) spinning at 20 rotations per second, with a circular trail ribbon in the blade colour (reusing the swing-trail code). Hits use the existing `saber_hit` sparks. On blocks: a short scorch spark burst. |
| SFX | `saber.throw` (a swing whoosh with an ignition snap), `saber.throw_loop` (a spinning hum with Doppler-like pitch from velocity, positional on the entity; stops when it is removed), `saber.throw_catch` (a hilt slap with a hum swell). |
| Multiplayer | The entity is tracked normally (clientTrackingRange 8, updateInterval 1). Effects `saber_throw` (start) and `saber_throw_catch`. Hits on players follow the server's PvP setting, as a weapon. |

### 8.4 Force Heal (`force_heal`, Jedi signature)

| Field | Value |
|---|---|
| Purpose | Protection and sustain for the light side: a short, interruptible channel that rewards finding a moment of calm in a fight. |
| Unlock | Granted by the Padawan trial (Jedi path). |
| Alignment | Normal at ≥ -14; opposed (cost ×1.5, no mastery) at -49 to -15; refused at ≤ -50. |
| Input | **Channel** (hold). 6-tick start-up, then it heals while held, for at most 60 ticks. |
| Cost | 8 FE at the start plus 3 FE per heal tick (12 FE per second). |
| Effect | 1 HP every 5 ticks to the caster (+10% per mastery rank). Movement ×0.5; no attacking or guarding; sprinting cancels it. |
| Cooldown | 200 ticks after the channel ends. If interrupted, 100. |
| Interruption | A single hit of ≥ 4 damage after armour, or any stagger. Plays `force.heal.interrupt`. |
| Branches (group `heal_shape`, level 8) | **Shared Light:** also heals other players (not ones you are fighting: damaged by you or damaging you in the last 200 ticks), settlers, villagers and tamed animals within 6 blocks at 60% of the rate. Cost +25%. Reports `HELPED_OTHER` (minor, capped). **Serene Ward:** if the channel lasted ≥ 20 ticks, ending it grants Absorption I for 200 ticks and restores 30 saber stamina. |
| Mastery | +1 per 4 HP healed while a hostile is within 16 blocks. |
| Counterplay | Slow and committed, interrupted by any solid hit, long cooldown. Lightning and pressure shut it down. |
| Animation | Both hands drawn together at the chest, head slightly bowed (pose `HEAL`). |
| VFX | Start: pale gold-white `force_mote` particles drift inward from about 2 blocks around (warmer than the meditation motes). Sustain: motes stream into the chest and rise loosely along the body; on each heal tick, a very faint `air_ripple` pulse at the chest (small, low alpha). There is **no halo, ring or column of light**. End: motes drift upward and fade over 20 ticks. Ward: sparse `ward_glint` specks that follow the body silhouette for its duration. Shared Light: a thin stream of motes from caster to each ally. Interrupted: the motes scatter outward and fall. |
| SFX | `force.heal.start` (a warm breathy swell), `force.heal.loop` (a soft tonal pad, positional, looped; stops on end, cancel, death, disconnect or dimension change), `force.heal.end` (a settling tone), `force.heal.ward` (a soft glassy chime), `force.heal.interrupt` (a broken breath and a dull drop). |
| Multiplayer | Effects `force_heal` (variant 0 start, 1 end, 2 interrupted; entity = caster), `force_heal_ally` (entity = ally, start/stop), `force_heal_ward`. Clients run the channel visuals from start until end, with a watchdog: stop if no end has arrived 10 ticks after the 60-tick maximum. |

### 8.5 Force Lightning (`force_lightning`, Sith signature)

| Field | Value |
|---|---|
| Purpose | Aggression and risky bursts for the dark side: sustained damage at mid range that punishes guard-less enemies and costs the caster to hold. |
| Unlock | Granted by the Apprentice trial (Sith path). |
| Alignment | Normal at ≤ +14; opposed (cost ×1.5, no mastery) at +15 to +49; refused at ≥ +50. |
| Input | **Channel.** An 8-tick charge (sparks crawl over the hand; others see the tell), then arcs while held, for at most 80 ticks. |
| Cost | 6 FE at the start plus 0.75 FE per tick (15 FE per second). |
| Cooldown | 160 ticks after release; 200 if it ran the full 80 ticks. |
| Targeting | Re-chosen every 4 ticks: the living entity nearest the aim axis within a 20° half-angle cone and 9 blocks, in line of sight. If there is none, the arcs strike the block the crosshair ray hits within 9 blocks (visual only; no fire, no block change). |
| Damage | 1.5 HP every 5 ticks (6 per second) of damage type `oldrepublic:force_lightning` (armour applies). +8% per mastery rank. While hit, the target is **shocked**: Slowness I for 10 ticks, refreshed, and wind-ups of non-boss NPCs are interrupted (diminishing returns on push-resistant targets). |
| Burn | After 60 ticks of continuous channelling, the caster takes 0.5 HP every 5 ticks: the dark side's price. |
| Saber guard | A player or NPC guarding with a lit saber and facing the caster takes ×0.2 damage, loses 3 stamina per damage tick, and is not shocked. Their guard breaks if the stamina runs out. Plays `saber.lightning_block`. |
| Interruption | The caster takes a single hit of ≥ 4 damage. |
| Branches (group `lightning_shape`, level 8) | **Chain Lightning:** arcs jump from the primary target to up to 2 more targets within 4 blocks of it, at 60% damage. Cost +20%. **Consuming Lightning:** single target. Damage ramps +15% per second of unbroken contact (at most +60%), and the caster heals 20% of the damage dealt. |
| Mastery | +1 per damage tick on a hostile, at most 5 per cast. Rank also raises the visual complexity (forks per arc = 1 + rank). |
| Counterplay | Short range, a visible charge, line of sight, saber guards, interrupted by solid hits, self-damage after 3 s, long cooldown. Push-resistant targets take ×0.6 and are not interrupted after the first shock in a diminishing-returns window. |
| PvP | `force_lightning.pvpEffects` false by default. |
| Animation | Pose `LIGHTNING`: the free arm (both arms if no saber is held) thrust forward with fingers spread, the body leaning slightly into it. |
| VFX (brief: branching arcs, flicker, charge sparks, contact sparks, complexity with mastery, clear start and stop, nothing left behind) | **Charge:** tiny `lightning_spark` specks jump between the fingertips, with 2 or 3 short arcs (0.3 blocks) around the hand. **Arcs:** built from `lightning_segment` particles, one rotated quad per segment (the `push_streak` technique). Each arc is a jittered polyline of 8 to 14 segments from the fingertips to the target's torso, re-randomised every 2 ticks, with 1 + mastery-rank forks of 0.5 to 1.5 blocks. Two main arcs at Standard; Reduced has 1 arc and no forks; Cinematic has 3 arcs. Colour: a white core with pale violet-blue edges; segments live 2 to 3 ticks. **Flicker:** alpha varies 0.7 to 1.0 per refresh; with flashing off, steady 0.85. **Contact:** a `lightning_spark` burst on the target surface each damage tick, and the target's vanilla hurt flash. **Stop:** every arc is removed within 1 tick, followed by 2 or 3 faint dark smoke wisps at the hand for 10 ticks, and nothing after that. A client watchdog removes arcs if no refresh event arrives for 6 ticks, and on caster death, removal or a level change. **Camera:** caster shake 0.03 while channelling. No dynamic light (vanilla cannot do it; KNOWN_ISSUES). |
| SFX | `force.lightning.charge` (a crackling build), `force.lightning.loop` (a sustained crackle and hiss, positional at the caster, looped), `force.lightning.impact` (a sizzle at the target, at most 2 per 10 ticks), `force.lightning.release` (a snap with a decaying crackle tail). The loop stops on release, cancel, death, disconnect or dimension change. |
| Multiplayer | `force_lightning` (variant 0 charge start, 1 arcs start, 2 stop; entity = caster; intensity = mastery rank). `force_lightning_target` (entity = target, variant = slot 0 to 2, intensity 1 for on and 0 for off), sent only when the target set changes. `force_lightning_hit` (pos = contact) on damage ticks for the impact sound and sparks. That is about 4 packets per second per caster at most. |

### 8.6 Force Push changes

- Push becomes a `ForceAbility` (charge input, unchanged numbers and VFX). Its branch now comes from the skill tree (7.4).
- New interplay: a Push that hits a **Sith Acolyte during its shove wind-up** cancels the shove and staggers the acolyte for 30 ticks ("push clash", effect `acolyte_shove_clash`).
- A Push hitting a **guarding** saber NPC that faces the caster has its knockback ×0.5 and drains 20 guard stamina.

---

## 9. Expanded saber combat and new NPCs

Saber worker. Server-authoritative; the client only sends intents.

### 9.1 Saber stamina

- The M1 guard stamina becomes **saber stamina**: one pool for guarding, heavy attacks and lunges.
- Regeneration is unchanged (15 per second, 20 ticks after the last drain).
- `steady_guard` raises the maximum, and forms modify it.
- The existing guard bar in the HUD shows it.

### 9.2 Light combo

- A saber hit with attack strength ≥ 0.9 that comes within 16 ticks of the previous combo hit continues the string:

| Step | Swing | Damage | Extra |
|---|---|---|---|
| 1 | Horizontal slash | ×1.0 | |
| 2 | Backhand | ×1.0 | |
| 3 | Overhead finisher | ×1.3 | Knockback 0.45; NPC guard drain ×2 |

- The string resets after step 3, after the window runs out, or when the player is hit.
- Steps travel in the `saber_combo` effect (variant = step). Clients pick the swing-trail arc (left to right, right to left, vertical), the swing sound variant and, for the finisher, a raised overhead arm pose and `saber.finisher`.

### 9.3 Heavy attack and lunge

**Heavy attack:** Sneak + Attack with a lit saber.

- Input: `ClientPreAttackCallback` (verified to exist in Fabric API 26.2) returns true and sends `saber_action HEAVY`.
- Needs ≥ 15 stamina, which is spent.
- Wind-up 10 ticks. The tell: blade drawn back over the shoulder, `saber.heavy_windup` hum swell, visible to all. Movement ×0.5 during the wind-up. Being hit for ≥ 4 damage cancels it.
- The strike: a 90° arc, reach = player reach + 0.5, up to 3 targets, damage ×1.75 of the ignited saber damage, NPC guard drain ×3, cannot be parried, staggers non-boss targets for 20 ticks (Slowness IV, interrupts wind-ups).
- Cooldown 24 ticks.
- Effects `saber_heavy` (variant 0 wind-up, 1 strike) and `saber.heavy_strike`.

**Lunge:** Sprint + Attack when no entity is within reach but a living target is 3.5 to 7 blocks away in the crosshair (6° aim assist).

- Needs `saber_lunge`.
- Sends `saber_action LUNGE <targetId>`. The server checks distance ≤ 7.5, line of sight, stamina 20, cooldown 60.
- The dash: a horizontal velocity of 1.1 blocks per tick toward the target for up to 8 ticks (set on the server, `hurtMarked`). The strike lands when within 2.5 blocks: damage ×1.2, cannot be parried. A miss after 8 ticks ends it.
- Effect `saber_lunge` (start, hit). VFX: a dust kick at the start, a low streak of the blade colour along the path, and the hit sparks. SFX: `saber.lunge`.

### 9.4 Forms (stances)

- A player can own both forms; one is **active** (stored in the skills attachment).
- Changing form: the Force screen, or the `Saber Form` key (cycles Unformed → owned forms, unbound by default). Only allowed with no combat for 60 ticks. It takes 20 ticks, with no attacking or guarding during the change.
- Effect `saber_form` (variant = form). Visual: the hilt is raised in a salute pose, and `saber.form_change` plays (a hum-pitch shift).

| | Unformed | **Form III Soresu** (defence) | **Form IV Ataru** (aggression and mobility) |
|---|---|---|---|
| Parry window | base | ×1.5 | -2 ticks |
| Guard arc | 100° | 150° | 100° |
| Stamina drain while guarding | ×1.0 | ×0.7 | ×1.0 |
| Max stamina | ×1.0 | ×1.0 | ×0.75 |
| Stamina regen | ×1.0 | ×1.2 | ×1.0 |
| Deflection without a parry | random 50° | toward the crosshair ±15° | random 50° |
| Melee damage | ×1.0 | ×0.85 | ×1.0 |
| Attack speed | +0 | +0 | +0.15 per second |
| Heavy wind-up | 10 | 14 | 10 |
| Lunge | normal | not available | cost 12, range 9, cooldown 40 |
| Combo window | 16 | 16 | 20 |
| Force Jump | | | cost ×0.75, no landing recovery, **aerial strike**: the first attack while airborne after a Force Jump (or a fall ≥ 1.5 blocks) deals ×1.4 with no wind-up |

### 9.5 NPC saber guard and parry (`SaberDuelist`)

Saber NPCs (the Acolyte, the mentors, Brenn) implement `SaberDuelist`:

- **Guard state** (synced through entity data for the pose): `READY`, `GUARDING`, `ATTACKING`, `RECOVERING`, `BROKEN`.
- **Guarding:**
  - A player's melee saber hit from within 60° of the NPC's facing does ×0.15 damage.
  - The NPC loses `6 + 3 × damage` stamina (×3 for a heavy attack, ×2 for a finisher).
  - Plays `saber_clash` at the real contact point (the existing effect).
- **Broken:** at 0 stamina the guard breaks (`npc_guard_break`). For 40 ticks the NPC cannot attack or guard and takes ×1.25 damage. This is the vulnerability window.
- **NPC parry:**
  - Only against **light** attacks.
  - Only while NPC stamina is ≥ 70% and it is `READY` or `GUARDING`.
  - Chance from its profile (Acolyte 35%, Jedi master 50%).
  - On a parry the player is staggered (Slowness III, 15 ticks; attack strength reset) and `saber_parry` plays.
  - Counterplay is clear: heavy attacks and lunges are never parried and break guards.
- **NPC attack strings:**
  - Profile-driven: 2 to 3 swings, a first wind-up of 10 ticks, then 7 ticks between swings, then 30 ticks of recovery (vulnerable).
  - The tell (never reduced by quality presets): the blade drawn back, `saber.npc_windup`, and a bright blade-tip glint particle.
  - The player's existing guard and parry handle these hits. A parry staggers the NPC, as in M1, and `saber_clash` sparks at the blade contact.
- **Force interactions:**
  - Lightning against a guarding NPC: 8.5.
  - Push: 8.6.
  - Disarming Pull: 8.1.
  - Thrown saber: blocked (8.3).

### 9.6 Duels and sparring

**`DuelistCombatGoal(Mob & SaberDuelist, Supplier<DuelistProfile>)`** decides when to approach, circle (strafe at 2.5 to 3.5 blocks), guard (more after taking hits), attack strings, use the profile's Force move, and back off when broken.

**`Spar.start(player, partner, SparRules, onEnd)`** runs a duel with no death risk:

| Rule | Meaning |
|---|---|
| Arena | Within 16 blocks of the start point. Leaving it forfeits. |
| Goals | `YIELD` (bring the partner to its yield fraction), `PARRIES n`, `TECHNIQUE_HITS n` (heavy or lunge hits) |
| Player safety | A partner hit that would take the player below 20% health is cancelled and the spar ends as **LOST**. The partner's damage is scaled by `partnerDamageScale`. |
| Win | A player hit that would take the partner below its yield fraction is cancelled and the spar ends as **WON**. The partner kneels (yield pose) and says a line. |
| Interrupted | Damage from any third entity to either side ends the spar as **INTERRUPTED** (no penalty). |
| Ends without a result | Death, logout, dimension change or server stop. |

Spar sessions are in memory only. Effects `spar` (variant 0 begin, 1 won, 2 lost); sounds `story.duel_begin` and `story.duel_end`.

**Profiles** (data in the balance class):

| Profile | HP | Damage | Stamina | Parry | String | Wind-up | Force move | Yield |
|---|---|---|---|---|---|---|---|---|
| `acolyte` | 40 | 6 | 60 | 35% | 2-3 | 10 | shove | none (hostile) |
| `rival` (Brenn) | 40 | 6 | 70 | 35% | 3 | 10 | shove | 15% (trial) / 40% (lesson) |
| `jedi_master` | 60 | 5 | 120 | 50% | 2 | 12 | none | 35% |
| `sith_master` | not fought in M2 | | | | | | | |
| `training` | - | 1 | ∞ | 0% | 1 strike per 50 ticks | 14 | none | n/a |

### 9.7 New NPCs

#### 9.7.1 Sith Acolyte (`sith_acolyte`, saber worker)

- **Look:** a human or near-human in dark Imperial acolyte robes, red blade. 64×64 skin; variant skins for Brenn and the deserter.
- **Stats:** HP 40, armour 4, speed 0.30, follow range 24, knockback resistance 0.3. In `#push_resistant`.
- **Modes** (saved): `HOSTILE` (attacks players; used in Smoke on the Ridge) and `RIVAL` (neutral, owned; used for Brenn: spar partner, and can be killed in the Sith trial). In `RIVAL` mode it only fights inside a spar.
- **Behaviour:** `DuelistCombatGoal` with profile `acolyte` or `rival`.
- **Force Shove:**
  - Cooldown 200 ticks.
  - Wind-up 16 ticks. The tell: the hand thrust forward, dust gathering at the palm with dark-tinted ripples (the Shadow Echo's visual language), `acolyte.shove_windup`.
  - A cone of 50° and 6 blocks, knockback 1.1 (+0.3 up), 2 damage.
  - Countered by a Push during the wind-up (8.6).
- **Drops:** XP through `Progression` (40 HP gives 60 XP). Loot table `oldrepublic:entities/sith_acolyte`: iron nuggets; 10% chance of a random hilt part from M1 (the M1 bad-luck rules do not apply).
- **Spawning:** encounters and commands only in M2. No natural spawns.

#### 9.7.2 Poacher Captain (`poacher_captain`, saber worker, enemy package)

- **Role:** a melee pressure fighter with a sidearm, and the mission's elite.
- **Stats:** HP 36, armour 5, speed 0.28, knockback resistance 0.4. In `#push_resistant`.
- **Melee:** a vibroblade (render-only item `vibroblade`). Normal swing: 6 damage, 12-tick tell. **Overhead heavy:** wind-up 16 ticks (blade raised, `captain.heavy_windup` shout), 9 damage, drains a guard ×2; a parry staggers her for 40 ticks. Cooldown 100.
- **Pistol:** `blaster_pistol` (render-only). Used at 4 to 12 blocks: a 12-tick muzzle-glow tell, one deflectable bolt of 4 damage (the existing `blaster_bolt`), cooldown 40.
- **Yields** at 25% health (`Yieldable`, below).

#### 9.7.3 Yielding (identity worker owns the mechanic; NPCs implement `Yieldable`)

- A `Yieldable` entity whose next damage from a player would take it below `yieldHealthFraction`: the damage is clamped so it stays exactly at the threshold, and it enters the **yielded** state:
  - drops to its knees (pose);
  - its weapon is lowered;
  - effect `yield` and `captain.yield` / `acolyte.yield`;
  - it stops all goals.
- **Yielded entities:**
  - are invulnerable to everything except melee from the deciding player (the encounter owner, or any player if `yieldOwner` is null);
  - ignore area effects.
- **Using the entity** opens the yield dialogue: Spare / Execute.
- **Striking it once** shows "Strike again to execute" (overlay); a second strike within 60 ticks executes it. This prevents accidental executions.
- **Walking away** (32 blocks) or **2,400 ticks** passing: it flees (`FLED`).
- The outcome goes to `QuestHooks.onYieldResolved` and to the alignment table in 3.2.

#### 9.7.4 Frontier Settler (`frontier_settler`, identity worker)

- **Look:** a human civilian in a worn work tunic; 3 skin variants.
- **Stats:** HP 16, passive.
- **States:**

| State | Behaviour |
|---|---|
| `FREE` | Wanders near its home point |
| `CAPTIVE` | Bound at a post, kneeling, rope detail on the skin overlay |
| `FLEEING` | Runs toward its home direction and despawns 24 blocks away or after 600 ticks |

- **Talking to a captive** opens the captives dialogue (5.3).
- **Killing a settler** is a contextual dark act (3.2).
- Poachers and the Acolyte never target settlers.
- Persistent while `CAPTIVE`.
- Sounds: `settler.hurt`, `settler.death`, `settler.freed`.

---

## 10. Controls and HUD

### 10.1 Key plan

| Action | Default | Category | Notes |
|---|---|---|---|
| Force Power slot 1 | R | Echoes of the Old Republic | Same binding id as M1 (`key.oldrepublic.use_force_power`, now labelled "Force Power 1"), so existing bindings carry over |
| Force Power slot 2 | Z | same | |
| Force Power slot 3 | V | same | |
| Force Power slot 4 | B | same | Unlocks at rank 2 / No Master |
| Force screen | K | same | unchanged |
| Journal | J | same | unchanged |
| Toggle Lightsaber | G | same | unchanged |
| Saber Form (cycle) | unbound | same | |
| Heavy attack | Sneak + Attack | vanilla keys | lit saber |
| Lunge | Sprint + Attack | vanilla keys | needs the node and a target out of reach |
| Guard / parry | Use (hold) | vanilla | unchanged |
| Glide (Soaring Leap) | Jump (hold, airborne) | vanilla | |

**Rules:**

- **Hold and channel powers:** press starts, release fires or ends.
- Only one power can be charging or channelling at a time. Pressing another slot while one is active cancels the first (no cost if it was still charging below its minimum) and is otherwise ignored.
- Opening a screen cancels.
- None of the defaults clash with vanilla 26.2 defaults (C and X, the toolbar keys, are avoided). A client test checks that no two mod keys share a default.
- All keys can be rebound.

A radial "Force wheel" is deliberately left out of M2. Four slots are enough for at most five powers and keep combat input direct.

### 10.2 HUD (top-left Force cluster, skills worker)

- **Ability sockets:** a row of up to 4 sockets (18×18) under the energy bar. Each shows the icon, the key label, a cooldown sweep, an amber rim when energy is below the cost, a charge or channel progress arc around the socket while active, and a cross-hatch when the power is refused by alignment.
- A socket appears only once that slot holds a power (gradual introduction).
- The crosshair brackets from M1 now show the charge of any hold-and-release power.
- Banners (lead's `Banners` API): level up, Resonance, **rank up** ("Padawan of the Watch"), **alignment band change**. Toasts: journal updated, **alignment shift**, **reputation change** ("Jedi Order +40").

---

## 11. Persistence, multiplayer, dedicated server and config

### 11.1 Saved state (all attachments persistent, copy-on-death, synced to the owner only, unless noted)

| Attachment | Owner | Contents |
|---|---|---|
| `oldrepublic:force` (M1) | lead | Unchanged. New counters: `mod_max_energy`, `mod_regen_pct`, `mod_regen_delay` (written by skills). `pushBranch` becomes a mirror. |
| `oldrepublic:identity` | identity | `alignment`; `alignment_log` (last 8: amount, reason key, day); `minor_day`, `minor_light`, `minor_dark`, `severe_dark` (daily caps); `reputation` map; `path`; `rank`; `trust` map; `choices` set (recorded major source ids, choice outcomes). |
| `oldrepublic:story` | quest | `crossing_stage`, `path_stage`; `meeting` (dimension + pos); `camp` (pos); `mentor_uuid`, `rival_uuid`; `encounter` {pos, state NOT_PLACED/PLACED/SPAWNED/DONE, `captain` NONE/YIELDED/SPARED/EXECUTED/FLED, `captives` NONE/FREED/EXPLOITED/ABANDONED}; `counters` (lesson progress); `session_day`, `atone_day`, `trial_retry_at` |
| `oldrepublic:skills` | skills | `nodes` (id → rank), `slots[4]`, `active_form`, `respec_day`, `spent` |
| `oldrepublic:saber_guard` (M1) | saber | Now carries stamina for the shared pool. Not persistent (refills on login), as today. |
| Item component `oldrepublic:saber_thrown` | saber | Throw UUID; cleared on return, death, logout, or after 60 ticks with no matching entity. |
| Entities | | Mentors, Brenn, settlers and encounter mobs: persistent (`setPersistenceRequired`), with owner UUID and encounter id. `thrown_saber`: `noSave`. |

### 11.2 Edge cases

| Event | Behaviour |
|---|---|
| Death | Channels, charges, the jump-fall protection, spars (no result) and dialogues end. The thrown saber returns (component cleared). Slots, nodes, alignment and reputation are kept (copy-on-death). A yielded Captain stays yielded until the timeout. |
| Respawn | Energy refills (M1). Cooldowns are cleared (M1 behaviour, kept). |
| Dimension change | Same as death for active things. Mentors do not follow; "Ask them to come" works only in the overworld. Encounter and meeting sites are overworld only. Chapter Two waits while the player is elsewhere. |
| Relog | Dialogue closed with no choice. Spar ended with no result. Mentors and the encounter persist. A relog during the Two Voices dream replays it (same as the M1 dream). |
| Server restart | Spar and dialogue sessions are cleared. Entities persist. Story stages are saved. |
| Crash while a saber is thrown | The component is cleared after 60 ticks in the inventory. No item is ever created, so nothing can be duplicated. |
| Lost mentor or rival entity | Found by UUID on demand, otherwise respawned at the anchor. Duplicates found on load (same owner, same role) are removed. |

### 11.3 Multiplayer

- Every power, combo step, heavy attack, lunge, form change, yield and spar sends compact effect events. Clients build every particle and sound locally (M1 rule).
- Lightning stays around 4 packets per second per caster (8.5).
- Story NPCs are **per player** and **owned**: others see them but cannot talk to them.
- Choices are owner-only. Kills in an encounter by anyone count for the owner. Encounter size scales with the awakened players nearby (5.3).
- Two players can be at the Crossing at once. Each has their own meeting point and mentors, and `MeetingSites` keeps sites at least 48 blocks apart.
- Heal Shared Light affects other players without any PvP flag. Pull, Lightning, the jump shockwave and Push affect players only per 8 (the `pvpEffects` flags plus the server's PvP setting, never teammates).

### 11.4 Dedicated server

- No client classes in `src/main` (as M1). Dialogue and Force screens, input, the HUD and VFX live in `src/client`.
- Data-driven skill trees and dialogues load on the server and are synced.
- The game tests' dedicated-server start covers the new registries.
- The Jump and Glide floating-kick check (8.2) needs a manual or automated check with `allow-flight=false`.

### 11.5 Config

**Server (`balance.json`) new sections:**

| Section | Notes |
|---|---|
| `identity`, `story`, `skills` | |
| `force_pull`, `force_jump`, `force_heal`, `force_lightning` | |
| `saber` | Extended |
| `saber_throw` | |
| `sith_acolyte`, `poacher_captain` | |

**Notable server switches:**

| Key | Default | Effect |
|---|---|---|
| `story.placeStructures` | true | When false, camps and the raider camp place no blocks; only the entities spawn |
| `story.respectSpawnProtection` | true | |
| `force_pull.pvpEffects`, `force_jump.pvpEffects`, `force_lightning.pvpEffects` | false | |
| `identity.contextualAlignment` | true | Turns off the minor and severe contextual detection for servers that dislike it; story choices still apply |

**Client (`ClientConfig`, lead):** one new toggle, `showMinorAlignmentToasts` (default true). Existing presets, shake and flashing apply to every new effect.

**Data:** `data/oldrepublic/skill_tree/*.json`, `data/oldrepublic/dialogue/*.json`, loot tables `entities/sith_acolyte`, `entities/poacher_captain`, `gameplay/settler_thanks`, `gameplay/captive_payment`, block tags `story_ground` and `built_blocks`, damage types `force_lightning`, `force_impact` (jump shockwave) and `thrown_saber`.

---

## 12. Effects and sounds

Each worker writes the full phase-by-phase treatment (anticipation, activation, travel, impact, aftermath) into their design doc; the lead merges it into `VFX_SFX_SPEC.md` as a "Milestone 2" section.

Art direction applies throughout:

- bent air, dust, debris, pressure and light;
- no ground circles, sigils, halos or glyphs;
- tells are never hidden or reduced by presets;
- flashing off replaces flicker and flashes with steady glows;
- every loop stops on cancel, death, disconnect, dimension change and item or slot change.

**Particle budgets per active instance at Standard** (scaled by the preset multiplier; all through `Vfx.count`):

| Effect | Budget |
|---|---|
| Lightning | ≤ 70 live segments per caster |
| Heal | ≤ 40 motes |
| Jump landing | ≤ 50 |
| Pull | ≤ 35 |
| Thrown saber trail | ≤ 30 |

**New particle types:**

| Owner | Particles |
|---|---|
| force | `pull_streak`, `jump_trail`, `lightning_segment`, `lightning_spark`, `ward_glint` (`force_mote` is reused from quest; `air_ripple` and `gather_dust` from force) |
| saber | `blade_glint` (NPC wind-up tell), `lunge_streak` |
| identity | `rope_fibre` (cutting captives loose) |

Sound ids are listed in section 15. Every sound has a subtitle (`subtitles.oldrepublic.<id>`), positional attenuation, and the variation rules of `AUDIO_BRIEF.md` (pitch randomisation, no immediate variant repeat, concurrency limits: `force.lightning.impact` ≤ 2 per 10 ticks per caster, `saber.clash` ≤ 1 per entity per 3 ticks).

---

## 13. Balance (defaults)

Ticks: 20 per second. "SP" means skill points.

### `identity` (`identity.IdentityBalance`)

| Key | Default | Meaning |
|---|---|---|
| `lightThreshold` / `leaningThreshold` | 50 / 15 | Band edges |
| `tier1Alignment` / `tier2Alignment` | 15 / 40 | Side node gates |
| `minorDailyCap` | 6 | Per direction, per in-game day |
| `minorLimit` | 60 | Contextual shifts cannot go beyond ±60 |
| `severeDailyCap` | 24 | Killing innocents |
| `helpedOther` / `defendedInnocent` / `cruelty` / `harmedInnocent` | +1 / +1 / -1 / -2 | |
| `killedVillager` / `killedSettler` | -4 / -8 | |
| `defendWindowTicks` | 100 | |
| `harmRepeatTicks` | 200 | |
| `repHostile` / `repDistrusted` / `repTrusted` / `repHonoured` | -500 / -100 / 100 / 500 | Tier edges |
| `joinRep` / `rivalRep` | 150 / -100 | |
| `trustStart` | 20 | |
| `trustAttackPenalty` | 5 (at most once per 200 ticks) | |
| `yieldTimeoutTicks` / `yieldLeaveDistance` / `executeConfirmTicks` | 2400 / 32 / 60 | |
| `settlerHealth` | 16 | |
| `contextualAlignment` | true | |

### `story` (`quest.StoryBalance`)

| Key | Default |
|---|---|
| `crossingDelayTicks` | 24000 |
| `crossingMinSleepTicks` | 100 |
| `meetingRings` | [64, 96, 128] |
| `meetingFallback` | [24, 40] |
| `meetingFootprint` | 9 |
| `encounterFootprint` | 13 |
| `maxSlope` | 2 |
| `siteMinSeparation` | 48 |
| `mentorLeash` | 6 |
| `lessonXp` / `lessonTrust` / `lessonRep` | 300 / 15 / 40 |
| `lessonTwoTrust` | 30 (threshold) |
| `pullLessonTargets` / `sithPullKills` / `sithPullKillWindowTicks` | 5 / 3 / 60 |
| `parryLessonCount` / `techniqueLessonCount` | 3 / 3 |
| `encounterMinDist` / `encounterMaxDist` | 96 / 160 |
| `encounterSpawnDistance` | 48 |
| `poachers` / `extraPerPlayer` / `extraMax` | 4 / 1 / 3 |
| `captiveCount` | 3 |
| `abandonDistance` | 128 |
| `reckoningXp` | 1000 |
| `reckoningSp` / `reckoningSpIndependent` | 1 / 3 |
| `reckoningRep` / `independentFrontierRep` | 80 / 100 |
| `trialMinLevel` / `trialMinTrust` / `trialMinRep` | 6 / 60 / 100 |
| `trialXp` / `trialSp` / `trialRetryTicks` | 800 / 2 / 600 |
| `crossingXp` | 300 |
| `resonanceCrossing` / `resonanceReckoning` / `resonanceFirstRank` | 200 / 300 / 500 |
| `sessionXp` / `sessionTrust` / `atoneTrust` / `atoneAlignment` | 120 / 5 / 3 / 4 |
| `mentorSummonTicks` | 1200 |
| `placeStructures` / `respectSpawnProtection` | true / true |

Resonance in M2 adds +1,000 (crossing 200, reckoning 300, first rank 500): +6 max FE and +0.25 FE/s with the M1 formula.

### `skills` (`skill.SkillBalance`)

| Key | Default |
|---|---|
| `switchPenalty` | 2 |
| `respecLoss` | 0.10 (rounded up, minimum 1) |
| `respecCooldownTicks` | 72000 |
| `combatLockTicks` | 60 |
| `baseSlots` / `maxSlots` | 3 / 4 |
| `deepReservesPerRank` | 8 FE |
| `quickenedRegenPctPerRank` / `quickenedDelayPerRank` | 10 / 5 ticks |
| `centeredParryTicksPerRank` | 1 |
| `calmFocusDelay` / `calmFocusMaxEnergyPct` | 10 / 10 |
| `guardiansResolveReduction` / `guardiansResolveRadius` | 0.10 / 6 |
| `furyPerRank` / `furyHealthFraction` | 0.06 / 0.5 |
| `hungeringEnergy` / `hungeringCooldownTicks` | 6 / 40 |
| `dreadFleeTicks` | 40 |
| `unboundCostMult` / `unboundCooldownMult` | 0.85 / 0.9 |
| `opposedCostMult` | 1.5 |

### `force_pull`

| Key | Default |
|---|---|
| `cost` / `cooldownTicks` / `windupTicks` | 16 / 120 / 3 |
| `range` / `aimAssistHalfAngle` | 12 / 8 |
| `speedBase` / `speedPerBlock` / `speedMax` | 0.35 / 0.11 / 1.6 |
| `lift` / `liftPerBlockAbove` | 0.25 / 0.03 |
| `brakeDistance` / `brakeFactor` / `brakeWindowTicks` | 2.5 / 0.3 / 20 |
| `itemRadius` | 2.5 |
| `guardingMult` | 0.5 |
| `knockbackResistanceWeight` | 0.6 |
| `masteryRangePerRank` / `masteryCostPerRank` | 0.05 / 0.05 |
| `disarmKnockMult` / `disarmTicks` / `acolyteGuardDrain` / `acolyteNoGuardTicks` / `pvpItemCooldownTicks` | 0.6 / 120 / 40 / 40 / 40 |
| `reelKnockMult` / `reelStaggerTicks` / `reelBonus` / `reelWindowTicks` | 1.2 / 20 / 0.30 / 30 |
| `pvpEffects` | false |

### `force_jump`

| Key | Default |
|---|---|
| `minChargeTicks` / `fullChargeTicks` / `maxHoldTicks` / `coyoteTicks` | 4 / 16 / 60 / 3 |
| `costMin` / `costMax` / `cooldownTicks` | 14 / 24 / 100 |
| `verticalMin` / `verticalMax` / `horizontalMin` / `horizontalMax` | 0.75 / 1.15 / 0.45 / 0.9 |
| `fallProtectionExtra` / `protectionWindowTicks` / `landingSlowTicks` | 4 / 80 / 6 |
| `masteryMinDistance` / `masteryHostileRadius` / `masteryCostPerRank` / `masteryHeightPerRank` | 4 / 16 / 0.05 / 0.04 |
| `crushMinFall` / `crushRadius` / `crushDamageBase` / `crushDamagePerBlock` / `crushDamageMax` / `crushKnock` / `crushStaggerTicks` / `crushResistantMult` | 3 / 3 / 3 / 0.5 / 8 / 0.8 / 15 / 0.5 |
| `soarHorizontalMult` / `soarVerticalMult` / `glideMaxFallSpeed` / `glideMaxTicks` / `glideCostPerTick` | 1.35 / 1.1 / 0.15 / 30 / 0.4 |
| `pvpEffects` | false |

### `force_heal`

| Key | Default |
|---|---|
| `startupTicks` / `intervalTicks` / `maxChannelTicks` | 6 / 5 / 60 |
| `startCost` / `costPerHeal` | 8 / 3 |
| `healPerTick` / `masteryHealPerRank` | 1.0 / 0.10 |
| `cooldownTicks` / `interruptedCooldownMult` / `interruptDamage` | 200 / 0.5 / 4 |
| `moveMult` | 0.5 |
| `sharedRadius` / `sharedRate` / `sharedCostMult` / `combatExcludeTicks` | 6 / 0.6 / 1.25 / 200 |
| `wardMinChannelTicks` / `wardAbsorptionTicks` / `wardAbsorptionAmplifier` / `wardStamina` | 20 / 200 / 0 / 30 |
| `masteryHpPerPoint` / `masteryHostileRadius` | 4 / 16 |

### `force_lightning`

| Key | Default |
|---|---|
| `chargeTicks` / `maxChannelTicks` / `retargetTicks` | 8 / 80 / 4 |
| `startCost` / `costPerTick` | 6 / 0.75 |
| `cooldownTicks` / `fullChannelCooldownTicks` | 160 / 200 |
| `range` / `halfAngle` | 9 / 20 |
| `damage` / `damageIntervalTicks` / `masteryDamagePerRank` | 1.5 / 5 / 0.08 |
| `shockTicks` | 10 |
| `burnAfterTicks` / `burnDamage` | 60 / 0.5 |
| `interruptDamage` | 4 |
| `guardDamageMult` / `guardDrainPerTick` | 0.2 / 3 |
| `resistantDamageMult` | 0.6 |
| `chainTargets` / `chainRange` / `chainDamageMult` / `chainCostMult` | 2 / 4 / 0.6 / 1.2 |
| `consumeRampPerSecond` / `consumeRampMax` / `consumeDrain` | 0.15 / 0.6 / 0.2 |
| `masteryPerCastMax` | 5 |
| `pvpEffects` | false |

### `saber` (additions to `saber.SaberBalance`)

| Key | Default |
|---|---|
| `comboWindowTicks` / `comboMinStrength` / `finisherDamageMult` / `finisherKnockback` / `finisherGuardDrainMult` | 16 / 0.9 / 1.3 / 0.45 / 2 |
| `heavyWindupTicks` / `heavyDamageMult` / `heavyArcDegrees` / `heavyReachBonus` / `heavyMaxTargets` | 10 / 1.75 / 90 / 0.5 / 3 |
| `heavyStaminaCost` / `heavyCooldownTicks` / `heavyGuardDrainMult` / `heavyStaggerTicks` / `heavyInterruptDamage` / `heavyMoveMult` | 15 / 24 / 3 / 20 / 4 / 0.5 |
| `lungeMinRange` / `lungeMaxRange` / `lungeAimAssist` / `lungeSpeed` / `lungeMaxTicks` / `lungeHitDistance` | 3.5 / 7 / 6 / 1.1 / 8 / 2.5 |
| `lungeDamageMult` / `lungeStaminaCost` / `lungeCooldownTicks` | 1.2 / 20 / 60 |
| `formSwitchTicks` / `formCombatLockTicks` | 20 / 60 |
| `soresuParryMult` / `soresuGuardArc` / `soresuDrainMult` / `soresuRegenMult` / `soresuDeflectAim` / `soresuDamageMult` / `soresuHeavyWindupExtra` | 1.5 / 150 / 0.7 / 1.2 / 15 / 0.85 / 4 |
| `ataruSpeed` / `ataruStaminaMult` / `ataruParryTicks` / `ataruLungeCost` / `ataruLungeRange` / `ataruLungeCooldown` / `ataruComboWindow` / `ataruAerialMult` / `ataruJumpCostMult` / `ataruMinFall` | 0.15 / 0.75 / -2 / 12 / 9 / 40 / 20 / 1.4 / 0.75 / 1.5 |
| `riposteWindowTicks` / `riposteDamageMult` | 20 / 1.4 |
| `deflectionControlDegrees` | 20 |
| `steadyGuardPerRank` | 0.10 |
| `npcGuardArc` / `npcBlockDamageMult` / `npcDrainBase` / `npcDrainPerDamage` / `npcBrokenTicks` / `npcBrokenDamageMult` | 120 / 0.15 / 6 / 3 / 40 / 1.25 |
| `npcParryMinStamina` / `npcParryStaggerTicks` | 0.7 / 15 |
| `lightningBlockSoundInterval` | 10 |
| `pushOnGuardKnockMult` / `pushOnGuardDrain` | 0.5 / 20 |
| `sparArenaRadius` / `sparPlayerLoseFraction` | 16 / 0.2 |

### `saber_throw`

| Key | Default |
|---|---|
| `cost` / `cooldownTicks` | 20 / 160 |
| `speed` / `returnSpeed` / `range` / `maxTicks` | 1.3 / 1.5 / 14 / 60 |
| `damageMult` / `maxTargets` | 0.8 / 3 |
| `blockDrainPlayer` / `blockDrainNpc` | 15 / 20 |
| `ricochetRange` / `ricochetBounces` / `ricochetFalloff` | 6 / 2 / 0.2 |
| `rendingSpeed` / `rendingRange` / `rendingDamageMult` / `rendingStaggerTicks` | 1.0 / 12 / 1.4 / 15 |
| `masteryRangePerRank` | 0.05 |
| `orphanClearTicks` | 60 |

### `sith_acolyte`

| Key | Default |
|---|---|
| `health` / `armor` / `speed` / `followRange` / `knockbackResistance` | 40 / 4 / 0.30 / 24 / 0.3 |
| `damage` | 6 |
| `guardStamina` / `guardRegen` / `guardRegenDelayTicks` | 60 / 10 / 30 |
| `parryChance` | 0.35 |
| `stringMin` / `stringMax` / `firstWindupTicks` / `swingGapTicks` / `recoveryTicks` | 2 / 3 / 10 / 7 / 30 |
| `shoveCooldownTicks` / `shoveWindupTicks` / `shoveRange` / `shoveConeDegrees` / `shoveStrength` / `shoveLift` / `shoveDamage` / `shoveClashStaggerTicks` | 200 / 16 / 6 / 50 / 1.1 / 0.3 / 2 / 30 |
| `rivalGuardStamina` / `rivalTrialYield` / `rivalLessonYield` | 70 / 0.15 / 0.4 |
| `masterHealth` / `masterDamage` / `masterGuardStamina` / `masterParryChance` / `masterYield` / `masterSparDamageScale` / `masterWindupTicks` | 60 / 5 / 120 / 0.5 / 0.35 / 0.6 / 12 |
| `trainingStrikeInterval` / `trainingWindupTicks` / `trainingDamage` | 50 / 14 / 1 |

### `poacher_captain`

| Key | Default |
|---|---|
| `health` / `armor` / `speed` / `knockbackResistance` | 36 / 5 / 0.28 / 0.4 |
| `swingDamage` / `swingTellTicks` | 6 / 12 |
| `heavyDamage` / `heavyWindupTicks` / `heavyCooldownTicks` / `heavyGuardDrainMult` / `parryStaggerTicks` | 9 / 16 / 100 / 2 / 40 |
| `pistolMinRange` / `pistolMaxRange` / `pistolTellTicks` / `pistolDamage` / `pistolCooldownTicks` | 4 / 12 / 12 / 4 / 40 |
| `yieldFraction` | 0.25 |

---

## 14. Architecture and work split

### 14.1 Phase 0 (lead, before workers start)

The lead writes the following **contracts** with real signatures and minimal bodies so every worker compiles against them from day one. The bodies are then filled in by the worker who owns them (listed in brackets). Signatures are frozen: new methods can be added, existing ones are not changed.

```java
// com.oldrepublic.identity  [identity]
public enum Path { NONE, JEDI, SITH, INDEPENDENT; String id(); static Path byId(String id); }
public enum Faction { JEDI_ORDER, SITH_EMPIRE, FRONTIER; String id(); static Faction byId(String id); }
public enum AlignmentBand { DARK, LEANING_DARK, BALANCED, LEANING_LIGHT, LIGHT; static AlignmentBand of(int alignment); }
public enum ReputationTier { HOSTILE, DISTRUSTED, NEUTRAL, TRUSTED, HONOURED; static ReputationTier of(int rep); }
public enum ContextAct { HELPED_OTHER, DEFENDED_INNOCENT, CRUELTY, HARMED_INNOCENT, KILLED_INNOCENT }
public enum YieldOutcome { SPARED, EXECUTED, FLED }
public enum CaptiveOutcome { FREED, EXPLOITED, ABANDONED }
public record AlignmentShift(String sourceId, int amount, Kind kind, String reasonKey) { public enum Kind { MAJOR, MINOR } }
public final class Identity {
	public static int alignment(Player p);                                  // client and server (synced attachment)
	public static AlignmentBand band(Player p);
	public static boolean shift(ServerPlayer p, AlignmentShift shift);      // false if ignored (repeat or cap)
	public static void contextual(ServerPlayer p, ContextAct act, @Nullable Entity target);
	public static int reputation(Player p, Faction f);
	public static void changeReputation(ServerPlayer p, Faction f, int amount, String sourceId, boolean oneShot);
	public static Path path(Player p);
	public static boolean setPath(ServerPlayer p, Path path, boolean force);
	public static int rank(Player p);
	public static void promote(ServerPlayer p, int rank);
	public static int trust(Player p, String mentorId);                     // "jedi" | "sith"
	public static void addTrust(ServerPlayer p, String mentorId, int amount, String reasonKey);
	public static boolean hasChoice(Player p, String choiceId);
	public static void recordChoice(ServerPlayer p, String choiceId);
}
public final class IdentityEvents {                                       // Fabric Event<>
	Event<AlignmentChanged> ALIGNMENT_CHANGED;  // (ServerPlayer, int before, int after)
	Event<PathChanged> PATH_CHANGED;            // (ServerPlayer, Path before, Path after)
}
public interface Yieldable {
	boolean canYield();
	float yieldHealthFraction();
	@Nullable UUID yieldDecider();      // null = any player
	boolean isYielded();
	void setYielded(boolean yielded);   // entity handles pose and goals
	void onYieldResolved(YieldOutcome outcome);
}
public final class Settlers { public static List<Entity> spawnCaptives(ServerLevel l, List<BlockPos> posts, UUID owner, String groupId); }

// com.oldrepublic.dialogue  [identity]
public final class Dialogues { public static boolean open(ServerPlayer p, Identifier id, @Nullable Entity speaker); public static void close(ServerPlayer p); }
public final class DialogueActions { public static void register(String id, Action a); public interface Action { void run(ServerPlayer p, @Nullable Entity speaker); } }
public final class DialogueConditions { public static void register(String id, Condition c); public interface Condition { @Nullable String failReason(ServerPlayer p, @Nullable Entity speaker, String arg); } } // null = met

// com.oldrepublic.ability  [skills owns the runtime; force and saber implement abilities]
public enum AbilityAction { START, RELEASE, CANCEL }
public enum InputStyle { TAP, HOLD_RELEASE, CHANNEL }
public interface ForceAbility {
	String id();
	InputStyle inputStyle();
	void onInput(ServerPlayer p, AbilityAction action);
	void cancel(ServerPlayer p, boolean feedback);
	boolean isActive(ServerPlayer p);        // charging or channelling
	Map<String, Float> info();               // live balance values for tooltips
}
public final class Abilities { public static void register(ForceAbility a); public static @Nullable ForceAbility get(String id); public static Collection<ForceAbility> all(); }
public final class AbilityCosts {
	public static float costMultiplier(ServerPlayer p, String abilityId);
	public static float cooldownMultiplier(ServerPlayer p, String abilityId);
	public static boolean refused(ServerPlayer p, String abilityId);       // alignment refusal, sends feedback
	public static boolean masteryAllowed(ServerPlayer p, String abilityId);
}

// com.oldrepublic.skill  [skills]
public final class Skills {
	public static int rank(Player p, String nodeId);
	public static boolean active(Player p, String nodeId);                 // owned and not dormant
	public static String activeForm(Player p);                             // "" = Unformed
	public static boolean setActiveForm(ServerPlayer p, String formNodeId); // validated by the caller (saber)
	public static void grant(ServerPlayer p, String nodeId);               // granted nodes; no refund
	public static int slots(Player p);
}

// com.oldrepublic.saber.duel  [saber]
public interface SaberDuelist { GuardState guardState(); ItemStack duelSaber(); }
public record DuelistProfile(String id, float damage, float guardStamina, float parryChance, int stringMin, int stringMax, int windupTicks, int gapTicks, int recoveryTicks, boolean shove, float yieldFraction) {}
public final class DuelistProfiles { public static DuelistProfile get(String id); }
public class DuelistCombatGoal extends Goal { public <T extends Mob & SaberDuelist> DuelistCombatGoal(T mob, Supplier<DuelistProfile> profile); }
public record SparRules(Goal goal, int count, float partnerYieldFraction, float partnerDamageScale) { public enum Goal { YIELD, PARRIES, TECHNIQUE_HITS } }
public enum SparResult { WON, LOST, INTERRUPTED, ENDED }
public final class Spar { public static boolean start(ServerPlayer p, Mob partner, SparRules rules, Consumer<SparResult> onEnd); public static boolean inSpar(Player p); }

// com.oldrepublic.quest.QuestHooks  [lead adds; the quest worker implements in QuestService]
static void onAbilityHit(ServerPlayer p, String abilityId, @Nullable Entity target);          // target null = item batch
static void onSaberTechnique(ServerPlayer p, String technique, @Nullable LivingEntity target, boolean hit); // heavy|lunge|finisher|parry
static void onYieldResolved(ServerPlayer p, LivingEntity yielded, YieldOutcome outcome);
static void onCaptivesResolved(ServerPlayer p, String groupId, CaptiveOutcome outcome);
static void onNodeLearned(ServerPlayer p, String nodeId);
static void onPathChanged(ServerPlayer p, Path path);

// client
com.oldrepublic.client.ability.ClientAbilityHooks.register(String id, ClientAbilityHook hook)   [skills runtime; force and saber register]
interface ClientAbilityHook { default boolean precheck(Minecraft mc) { return true; } default void onPress(Minecraft mc) {} default void onRelease(Minecraft mc) {} default void onCancel(Minecraft mc) {} }
com.oldrepublic.client.hud.Banners.show(Component title, Component subtitle, int accentArgb)      [lead; FlourishHud renders the queue]
```

**Other lead Phase 0 changes:**

- `Progression.recalc` and the regen tick read `mod_max_energy`, `mod_regen_pct` and `mod_regen_delay` from `ForceData.counters`.
- `OldRepublicMod` and `OldRepublicModClient` call the new `IdentityFeature`, `SkillFeature`, `IdentityClient` and `SkillClient` (the quest, force and saber features already exist).
- `fabric.mod.json` lists `oldrepublic-identity.mixins.json` and `oldrepublic-skill.mixins.json` (empty configs).
- `ModSounds`, `sounds.json` and subtitles get every sound id in section 15: the lead extends `gen_sounds.py` to import per-feature recipe modules `tools/assets/sounds/<feature>.py`, which each worker owns, and adds a shared event list `tools/assets/sounds/events_m2.json`.
- The UI kit gets `TabStrip`, a node socket and a portrait frame (`client/ui`).
- `ClientConfig.showMinorAlignmentToasts`.
- Data tags `story_ground` and `built_blocks`.
- Gametest `fabric.mod.json` lists the new test classes.

**Phase 0 notes (as built).** Where the pseudo-Java above left a choice open, this is what the code does. These are part of the frozen contract.

- **`IdentityEvents`:** `public static final Event<...>` fields built with `EventFactory.createArrayBacked`. The callback interfaces are nested: `IdentityEvents.AlignmentChanged#onAlignmentChanged(ServerPlayer, int before, int after)` and `IdentityEvents.PathChanged#onPathChanged(ServerPlayer, Path before, Path after)`. Fire them after the new value is stored.
- **Enums:** `Path.id()` and `Faction.id()` are the lower-case names (`independent`, `jedi_order`). `Path.byId` returns `NONE` for an unknown id; `Faction.byId` returns null (`@Nullable`). `AlignmentBand.of` and `ReputationTier.of` already implement the edges of 3.1 and 4.2 (constants for now; the identity worker may read them from `IdentityBalance`).
- **Registries:** `Abilities`, `DialogueActions`, `DialogueConditions` and `ClientAbilityHooks` are real registries that throw on a duplicate id. Added lookups: `DialogueActions.get(id)`, `DialogueConditions.get(id)`, `ClientAbilityHooks.get(id)` (all `@Nullable`). `Abilities.all()` is read-only, in registration order. `ClientAbilityHook` is a public interface in `com.oldrepublic.client.ability`.
- **Stubs:** `Identity` stores nothing and returns neutral defaults (alignment 0, `BALANCED`, reputation 0, path `NONE`, rank 0, trust 0; `shift`/`setPath`/`hasChoice` false). `Skills` returns 0 / false / `""` / 3 slots. `AbilityCosts` returns ×1, not refused, mastery allowed. `Dialogues.open` and `Spar.start` return false. `Settlers.spawnCaptives` returns an empty list. The new `QuestHooks` methods call package-private `QuestService` stubs of the same names.
- **Saber duel:** `GuardState` (not listed above) is `saber.duel.GuardState { READY, GUARDING, ATTACKING, RECOVERING, BROKEN }`. `DuelistCombatGoal` is a non-final class with protected `mob()`, `duelist()` and `profile()`; the stub's `canUse()` is false. `DuelistProfiles.get` returns the 9.6 table values (training: infinite stamina, gap 50 ticks) and falls back to `acolyte` for an unknown id. `SparRules.Goal` shadows Minecraft's `Goal` by name: write `SparRules.Goal` in files that also import `net.minecraft.world.entity.ai.goal.Goal`.
- **Stat modifier counters** (`Progression.MOD_*` constants, read in `recalc` and the regen tick; an absent key changes nothing): `mod_max_energy` = flat FE added to max energy; `mod_max_energy_pct` (**added**, for `calm_focus`) = percent applied after every flat bonus; `mod_regen_pct` = percent added to regen per second; `mod_regen_delay` = ticks taken off the regen delay (never below 0). The skill service writes them and calls `Progression.commit`, which recomputes max energy.
- **Banners:** `Banners.show(title, subtitle, accent)` queues into `FlourishHud` (at most 4 waiting; extras are dropped). `title` is the small upper line, `subtitle` the large one. The M1 level-up and Resonance banners go through the same queue, so nothing overwrites another.
- **Standing panel:** `public class StandingPanel` in `client/identity`, used as an instance: `void render(GuiGraphicsExtractor, Font, int x, int y, int w, int h, float partialTick)` and `boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)` (the vanilla `Screen` signature; true = consumed). The Force screen creates one and forwards to it.
- **UI kit** (`client/ui`): `TabStrip` (`TabStrip.Tab(id, label, tint)`, tint 0 = none; `HEIGHT` 14; `render(g, font, x, y, w, mouseX, mouseY, partialTick)`; `mouseClicked(event)` returns true when another tab opened, and the host plays the sound; `setTabs` keeps the open tab; `select(id)`, `selectedId()`). `NodeSocket` (`SIZE` 20; `State { OWNED, AVAILABLE, LOCKED, DORMANT }`; `draw(g, x, y, icon, state, granted, selected, hovered, time, alpha)`; `link(...)` and `orDiamond(...)` for the tree lines). `PortraitFrame.draw(g, x, y, size, portraitSprite, accent, time, alpha)`; portraits are GUI sprites (`textures/gui/sprites/...`).
- **Client option:** `ClientConfig.showMinorAlignmentToasts` (default true), saved in `client.json` like the other options and toggled with `/oldrepublicfx alignment_toasts on|off`.
- **Sounds:** `tools/assets/sounds/events_m2.json` (lead) lists every id from section 15 with its owning `feature`, `subtitle`, `loop` and optional `volume` / `attenuation_distance`. Each `tools/assets/sounds/<feature>.py` (worker) defines `FILES = {"<event id>": {"<group>/<file>": generator, ...}}`, one or more variants per event, and may `import gen_sounds as g` for the DSP helpers. `python3 tools/assets/gen_sounds.py --feature <feature>` renders one module's files (`--m2` all of them, `--java` also rewrites `ModSounds`); `sounds.json` and `lang-parts/sounds.json` are always rewritten in full. Every id has placeholder audio and a `ModSounds` constant (dots become underscores: `force.pull.cast` → `FORCE_PULL_CAST`).
- **Data and resources:** block tags `story_ground` (natural surface blocks) and `built_blocks` (player-made blocks; tag references are optional entries, so a missing tag never breaks loading). Empty mixin configs `oldrepublic-identity.mixins.json` and `oldrepublic-skill.mixins.json` (packages `com.oldrepublic.mixin.identity` / `.skill`). No empty lang parts were needed: `mergeLang` merges whichever parts exist, so W1 and W3 create `identity.json` and `skill.json` themselves. The gametest `fabric.mod.json` entries wait until the test classes exist.

### 14.2 Workers and ownership

Five workers. **A file has exactly one owner.** Shared files (lead) are changed only through requests in a worker's report.

| Worker | Owns (code) | Owns (data, assets, docs) | Delivers |
|---|---|---|---|
| **W1 identity** | `com.oldrepublic.identity.**` (fills the contracts), `com.oldrepublic.dialogue.**`, `client/identity/**` (Standing tab panel, toasts), `client/dialogue/**` (dialogue screen), `mixin/identity/**` | lang part `identity.json` (alignment, factions, ranks, Standing, settler and yield dialogues, `dialogue.oldrepublic.common.*`), `data/oldrepublic/dialogue/{yield,captives}.json`, loot `gameplay/settler_thanks`, `gameplay/captive_payment`, `tools/assets/gen_textures_identity.py` (settler skins, rope fibre, faction icons), `tools/assets/sounds/identity.py`, `docs/design/identity.md`, `docs/assets/identity.md` | Alignment (3), reputation, path, rank and trust storage (4, 6.3), the dialogue engine and screen (6.4), yielding (9.7.3), Frontier Settler (9.7.4), contextual detection, Standing panel (7.7; the skills worker's screen embeds it through `StandingPanel.render(...)`, see note), toasts, `/oldrepublic identity` and `/oldrepublic dialogue` commands |
| **W2 quest/story** | `com.oldrepublic.quest.**` except `QuestHooks`, `com.oldrepublic.mentor.**`, `client/quest/**`, `client/mentor/**` | lang `quest.json`, `data/oldrepublic/dialogue/{crossing_jedi,crossing_sith,mentor_jedi,mentor_sith,brenn,independent}.json`, `data/oldrepublic/structure/story/*.nbt`, `tools/assets/gen_structures_story.py`, `gen_textures_mentor.py` (mentor skins, portraits, ledger), `sounds/story.py`, item `poachers_ledger`, `docs/design/quest.md` (Chapter Two section), `docs/assets/mentor.md` | Chapter Two (5), mentors and camps (6.1, 6.2), lessons, Smoke on the Ridge orchestration (site, spawn, completion), trials using `Spar`, the journal update, meeting sites, the Calling variants, the Two Voices dream, `/oldrepublic story` commands |
| **W3 skills** | `com.oldrepublic.skill.**`, `com.oldrepublic.ability.**` (runtime: `AbilityRuntime`, payloads), `client/skill/**` (new Force screen), `client/ability/**` (key handling, ability info cache), `client/hud/**` except `Banners.java`, `client/force/ForceKeys.java` (moved under its ownership; it may move to `client/skill`). It **deletes** `force/BranchChoice.java`, `force/network/ChooseBranchPayload.java`, `force/network/PushInfoPayload.java`, `client/force/ForceScreen.java`, `client/force/ClientPushInfo.java`. | lang `skill.json` (node names and descriptions, screen, the key names `key.oldrepublic.use_force_power`, `force_screen`, `force_slot_2..4`, `saber_form`, which W4 removes from `force.json`), `data/oldrepublic/skill_tree/*.json`, `gen_textures_skill.py` (node icons, sockets), `sounds/skill.py`, `docs/design/skill.md`, `docs/assets/skill.md` | Trees, rules and respec (7), ability runtime and slots (7.5, 10.1), the ability info payload, the Force screen with tabs (7.7; it embeds W1's Standing panel), HUD sockets and banners (10.2), Push migration (7.4), stat modifiers, `/oldrepublic skills` commands |
| **W4 force** | `com.oldrepublic.force.**` (minus the files W3 deletes), `client/force/**` (minus `ForceKeys`, `ForceScreen`, `ClientPushInfo`), `mixin/force/**`, `oldrepublic-force.mixins.json` | lang `force.json`, `gen_textures_force.py`, `sounds/force.py`, damage types `force_lightning`, `force_impact`, `docs/design/force.md` (M2 section), `docs/assets/force.md` | Push on the ability framework (8.6), Force Pull, Force Jump, Force Heal and Force Lightning with every VFX/SFX and animation (8), removal of `PushInputPayload` and `PushInput` (prediction moves into `ClientAbilityHook`s) |
| **W5 saber and enemy** | `com.oldrepublic.saber.**` (including `saber/duel`), `com.oldrepublic.enemy.**`, `client/saber/**`, `client/enemy/**`, `mixin/saber/**`, `mixin/enemy/**`, both mixin configs | lang `saber.json`, `enemy.json`, `gen_textures_saber.py`, `gen_textures_enemy.py` (acolyte, captain, vibroblade, pistol), `sounds/saber.py`, `sounds/enemy.py`, `tags/entity_type/push_resistant.json`, loot tables `entities/sith_acolyte`, `entities/poacher_captain`, damage type `thrown_saber`, `docs/design/saber.md` and `docs/design/enemy.md` (M2 sections), `docs/assets/{saber,enemy}.md` | Saber stamina, combo, heavy attack, lunge and forms (9.1 to 9.4), NPC guard and parry (9.5), the duelist goal, sparring and profiles (9.6), Saber Throw (8.3), Sith Acolyte and Poacher Captain (9.7), saber audio and trails generalised from players to any `LivingEntity` (mentors and the acolyte hum and leave trails), `/oldrepublic saber ...` and `/oldrepublic enemy spawn ...` commands |

Notes on boundaries:

- **The Standing tab** is a `StandingPanel` class in `client/identity` (W1) with `render(GuiGraphicsExtractor, Font, int x, int y, int w, int h, float partialTick)` and `mouseClicked`. W3's Force screen hosts it as its last tab. The method signature is part of the Phase 0 contract.
- **Poses:** W4's `HumanoidModelMixin` poses the **free arm** for powers (`PULL`, `HEAL`, `LIGHTNING`, `JUMP`). W5's saber mixin poses the **saber arm** (combo steps, heavy wind-up, form salute, throw and catch, NPC guard). With no saber held, Lightning uses both arms (W4).
- **Mentor combat:** W2's mentor entities implement `SaberDuelist` and add W5's `DuelistCombatGoal`. Brenn is W5's `SithAcolyte` in `RIVAL` mode, spawned by W2.
- **Captain and acolyte yielding:** W5 implements `Yieldable` on them; W1 runs the logic and dialogue; W2 reacts in `QuestService.onYieldResolved`.
- **Ledger:** W2 gives or drops `poachers_ledger` from `onYieldResolved` and from the captain's death (through `ServerLivingEntityEvents.AFTER_DEATH` with the encounter tag), not through W5's loot table.
- **Lesson grants:** W2 calls `Skills.grant(player, "force_pull")` and so on.
- **Dependencies to stub around while parallel:** W2 and W3 need W1's `Dialogues` and `Identity` (stubs exist); W2 needs W5's `Spar` (stub); W4 and W5 need W3's runtime (stub registry and slot 1 fallback). Every worker tests their own part with commands until integration.

### 14.3 Integration order (lead)

1. Phase 0 contracts merged and compiling.
2. Workers deliver in parallel.
3. Merge order W3 → W4 → W5 → W1 → W2, so the ability runtime is in place before the powers, and story content goes last.
4. Fix compile errors on CI and send them back.
5. Run the server and client tests.
6. PlaythroughTest extension.
7. Update the top-level docs (`FEATURE_CHECKLIST`, `BALANCE`, `KNOWN_ISSUES`, `ASSET_MANIFEST`, `VFX_SFX_SPEC`, `DECISIONS`).

---

## 15. Shared identifiers

All ids are in the `oldrepublic` namespace.

### Items, blocks, entities, data

| Kind | Id | Owner |
|---|---|---|
| Item | `poachers_ledger` | W2 |
| Item (render-only, not obtainable in survival) | `vibroblade`, `blaster_pistol` | W5 |
| Entity | `jedi_mentor`, `sith_mentor` | W2 |
| Entity | `sith_acolyte`, `poacher_captain`, `thrown_saber` | W5 |
| Entity | `frontier_settler` | W1 |
| Item component | `saber_thrown` | W5 |
| Attachment | `identity` | W1 |
| Attachment | `story` | W2 |
| Attachment | `skills` | W3 |
| Structure template | `story/jedi_camp`, `story/sith_camp`, `story/raider_camp` | W2 |
| Dialogue | `crossing_jedi`, `crossing_sith`, `mentor_jedi`, `mentor_sith`, `brenn`, `independent` | W2 |
| Dialogue | `yield`, `captives` | W1 |
| Skill tree | `universal`, `saber`, `jedi`, `sith` | W3 |
| Damage type | `force_lightning`, `force_impact` | W4 |
| Damage type | `thrown_saber` | W5 |
| Loot table | `entities/sith_acolyte`, `entities/poacher_captain` | W5 |
| Loot table | `gameplay/settler_thanks`, `gameplay/captive_payment` | W1 |
| Block tag | `story_ground`, `built_blocks` | lead |
| Entity type tag | `#push_resistant` += `sith_acolyte`, `poacher_captain` | W5 |
| Ability ids (also skill nodes, cooldown and mastery keys) | `force_push`, `force_pull`, `force_jump`, `force_heal`, `force_lightning` | W4 |
| Ability ids | `saber_throw` | W5 |
| Resonance milestone ids | `crossing`, `reckoning`, `first_rank` | W2 grants |
| Mentor ids (trust keys) | `jedi`, `sith` | |
| Choice / source ids | `reckoning.captain`, `reckoning.captives`, `sith_trial.brenn`, `dialogue.<dialogue>.<node>` | W2 (W1 records) |

### Payloads

| Id | Direction | Owner |
|---|---|---|
| `ability_input` (slot, action) | C2S | W3 |
| `learn_node` (node id) | C2S | W3 |
| `assign_slot` (slot, ability id) | C2S | W3 |
| `respec_confirm` | C2S | W3 |
| `skill_trees` (definitions) | S2C | W3 |
| `ability_info` (id → map) | S2C | W3 |
| `saber_action` (HEAVY / LUNGE targetId / FORM_CYCLE) | C2S | W5 |
| `dialogue_choice` (session, option) | C2S | W1 |
| `dialogue_close` | C2S | W1 |
| `dialogue_node` | S2C | W1 |
| `dialogue_end` | S2C | W1 |
| Removed: `push_input`, `choose_branch`, `push_info` | | W4 / W3 |

### Effect events

| Owner | Ids |
|---|---|
| W1 | `alignment_shift` (toPlayer; intensity = amount, variant = new band), `reputation_change` (toPlayer; variant = faction, intensity = amount), `rank_up` (toTracking; variant = path), `yield` (toTracking; entity = yielder), `settler_freed` (around; entity) |
| W2 | `calling` variants 2 (voices) and 3 (smoke); `awakening_dream` variant 2 (Two Voices start); `lesson_complete` (toPlayer); `mentor_depart` (around; entity) |
| W3 | `node_learned` (toPlayer; variant = tree), `respec` (toPlayer) |
| W4 | `force_pull`, `force_pull_hit`, `force_pull_disarm`, `force_jump_gather`, `force_jump_launch`, `force_jump_land`, `force_jump_slam`, `force_heal`, `force_heal_ally`, `force_heal_ward`, `force_lightning`, `force_lightning_target`, `force_lightning_hit` |
| W5 | `saber_combo`, `saber_heavy`, `saber_lunge`, `saber_form`, `saber_throw`, `saber_throw_catch`, `npc_guard_break`, `npc_windup`, `acolyte_shove`, `acolyte_shove_clash`, `captain_heavy`, `spar`; reuses `saber_clash`, `saber_parry`, `saber_hit`, `blaster_fire`, `bolt_impact` |

### Sounds

| Group | Ids | Recipe owner |
|---|---|---|
| identity | `identity.alignment_light`, `identity.alignment_dark`, `identity.rank_up`, `dialogue.open`, `dialogue.select`, `settler.hurt`, `settler.death`, `settler.freed` | W1 |
| story | `story.two_voices`, `story.lesson_complete`, `story.duel_begin`, `story.duel_end` | W2 |
| skill | `skill.learn`, `skill.slot`, `skill.respec` | W3 |
| force | `force.pull.cast`, `force.pull.drag`, `force.pull.catch`, `force.pull.disarm`, `force.jump.gather`, `force.jump.launch`, `force.jump.land`, `force.jump.slam`, `force.jump.glide`, `force.heal.start`, `force.heal.loop`, `force.heal.end`, `force.heal.ward`, `force.heal.interrupt`, `force.lightning.charge`, `force.lightning.loop`, `force.lightning.impact`, `force.lightning.release` | W4 |
| saber | `saber.finisher`, `saber.heavy_windup`, `saber.heavy_strike`, `saber.lunge`, `saber.form_change`, `saber.throw`, `saber.throw_loop`, `saber.throw_catch`, `saber.npc_windup`, `saber.lightning_block` | W5 |
| enemy | `acolyte.shove_windup`, `acolyte.shove`, `acolyte.hurt`, `acolyte.death`, `acolyte.yield`, `captain.heavy_windup`, `captain.hurt`, `captain.death`, `captain.yield`, `vibroblade.swing`, `vibroblade.hit`, `blaster.pistol_fire` | W5 |

Loops (must stop correctly): `force.jump.gather`, `force.jump.glide`, `force.heal.loop`, `force.lightning.loop`, `saber.throw_loop`, `story.two_voices`.

### Lang prefixes

Duplicate keys across parts fail the build.

| Part | Prefixes |
|---|---|
| `identity.json` (W1) | `alignment.oldrepublic.*`, `faction.oldrepublic.*`, `rank.oldrepublic.*`, `path.oldrepublic.*`, `screen.oldrepublic.standing.*`, `message.oldrepublic.identity.*`, `dialogue.oldrepublic.common.*`, `dialogue.oldrepublic.yield.*`, `dialogue.oldrepublic.captives.*`, `entity.oldrepublic.frontier_settler` |
| `quest.json` (W2) | Existing, plus `journal.oldrepublic.entry.<new ids>`, `journal.oldrepublic.codex.<new ids>`, `quest.oldrepublic.c2.*`, `dialogue.oldrepublic.<crossing_*,mentor_*,brenn,independent>.*`, `npc.oldrepublic.*` (Saelen, Sorn, Brenn, Hessa Dray), `message.oldrepublic.story.*`, `entity.oldrepublic.{jedi,sith}_mentor`, `item.oldrepublic.poachers_ledger` |
| `skill.json` (W3) | `skill.oldrepublic.*` (`tree.<id>`, `node.<id>.name/.desc`, `req.*`, `state.*`), `screen.oldrepublic.skills.*`, `message.oldrepublic.skill.*`, `key.oldrepublic.*` (all mod keys except journal and saber toggle) |
| `force.json` (W4) | `force.oldrepublic.*`, `message.oldrepublic.force.*`, `ability.oldrepublic.<id>.*` (feedback lines). Obsolete `screen.oldrepublic.force.*` and the two key lines are removed. |
| `saber.json` / `enemy.json` (W5) | `saber.oldrepublic.*`, `form.oldrepublic.*`, `message.oldrepublic.saber.*`; `entity.oldrepublic.{sith_acolyte,poacher_captain,thrown_saber}`, `item.oldrepublic.{vibroblade,blaster_pistol}` |
| `sounds.json` (lead) | `subtitles.oldrepublic.*` (generated) |

---

## 16. Test plan

Distinguish compile, automated checks and manual play, as in M1. Nothing counts as "played" until a person plays it.

### 16.1 Server game tests (`./gradlew build`; each worker writes their own class, the lead registers it)

| Class | Owner | Covers |
|---|---|---|
| `IdentityGameTests` | W1 | Band edges; clamping to ±100; a major shift applies once per source; minor daily caps per direction and the ±60 limit; severe cap; rep tiers; `setPath` refuses JEDI↔SITH without force and allows INDEPENDENT→JEDI once; trust clamping; codec round trip and copy-on-death; yield clamp (an entity hit past its threshold ends exactly at the threshold and is yielded); execute-confirm timing; dialogue condition evaluation and failure reasons; dialogue session rejects a stale session id and an out-of-range speaker. |
| `StoryGameTests` | W2 | Crossing stages only move forward; the choice applies once (a second "join" is ignored); decline → Independent plus `unbound`; late join gates (alignment, rep); lesson grants with no refund; Smoke on the Ridge completion logic (crew, captain, captives, abandoned at 128 blocks); trial requirements matrix; `MeetingSites` picks a flat natural site and rejects slopes, water, spawn protection and built blocks (template test areas); `placeStructures=false` places no blocks; one mentor per owner (duplicate removal on load). |
| `SkillGameTests` | W3 | Purchase validation matrix (level, prerequisites, points, path, alignment, granted only); group switch cost and removal; dormant on alignment loss and active again on recovery; respec refund rounding, cooldown, granted nodes kept; slot rules and combat lock; Push migration from `pushBranch`; every tree JSON loads, has no cycles, and every `ability` node names a registered ability; stat modifiers reach `Progression` (max energy). |
| `ForceGameTests` (extended) | W4 | Pull targeting (aim assist, line of sight) and speed formula; brake; disarm stops a poacher firing; Jump velocity by charge and fall protection limit; Crushing Descent damage formula; Heal per tick, interruption at ≥ 4 damage, Shared Light excludes players in combat; Lightning damage per tick, guard absorption and drain, chain target selection, burn after 60 ticks, opposed-alignment cost ×1.5 and refusal; PvP flags off means no effect on players. |
| `SaberGameTests` (extended), `EnemyGameTests` | W5 | Combo step timing and reset; heavy damage and drain maths, cannot be parried; lunge validation distance and line of sight; form modifiers in `SaberStats`; NPC guard block, drain and break windows; NPC parry only against light attacks above 70% stamina; thrown saber: the item never leaves the inventory, the component is cleared on death, logout and orphan timeout (no duplication); spar safety (the player never drops below 20% from the partner and can never die); acolyte shove cancelled by a Push during the wind-up; captain yields at 25%. |

### 16.2 Client game tests (screenshots reviewed)

- A new `IdentityBuildsClientTest` (lead) calls each worker's `src/gametest/java/com/oldrepublic/gametest/m2/<Feature>ClientSteps.run(context, server)`.
- Screenshots are named `oldrepublic_m2_<nn>_<what>`.

| Worker | Screenshots |
|---|---|
| W3 | Force screen on every tab including a locked node, dormant node and detail panel; HUD with 4 sockets during a cooldown and a channel |
| W1 | Standing tab; dialogue screen with a disabled option and its reason; alignment toast; yield prompt |
| W2 | The Crossing with both mentors lit; a camp; the raider camp from 30 blocks with its smoke; Chapter Two journal pages |
| W4 | Pull mid-drag; Jump gather, launch and landing; Heal channel; Lightning charge, arcs on a target, chain, and the frame after stop (nothing left) |
| W5 | Combo finisher pose; heavy wind-up tell; lunge; Soresu and Ataru salute; thrown saber in flight; acolyte wind-up tell and guard break; captain yield; spar win |

A check fails the test if two mod keys share a default binding.

### 16.3 PlaythroughTest extension (lead)

After Chapter One, the same world continues with real inputs where possible:

1. Sleep (or wait a day) for the Two Voices dream.
2. Hold the journal and walk toward the voices (a teleport is logged if over 128 blocks).
3. Use the mentors and pick **Jedi** with number keys in the dialogue.
4. Lesson 1: pull spawned zombies (the spawn is logged).
5. Lesson 2: timed guard presses to parry 3 strikes.
6. Travel to the raider camp. Fight with combo, heavy attack and lunge. Bring the Captain to yield and **Spare**. **Free** the captives.
7. Debrief, then win the trial spar. Assert Padawan, `force_heal` granted, slot 4, alignment > 0.
8. Cast Heal while damaged.

A second, shorter run on a fresh player uses command shortcuts (logged) to reach the Sith Trial of Ascension, kill Brenn, and cast Lightning. A third run picks **Neither** and checks `unbound` and the Independent ending.

Every shortcut is logged, as in M1.

### 16.4 Manual checks (owner or a tester)

- Feel of combo, heavy attack and lunge timing.
- Lightning readability at all four presets.
- Audio loops stopping on death, logout and dimension change.
- Two players at separate Crossings at once.
- A dedicated server with `allow-flight=false` and Soaring Leap.

---

## 17. Lore notes for `DECISIONS.md` (lead adds these)

- **Era stays about 3,640 BBY, the cold peace.** A Jedi Watchwoman (the Watchmen are an Old Republic Jedi role) guards this frontier sector. An Imperial Sith Overseer scouts the frontier for Force-sensitives to send to the Empire. Neither may openly fight on neutral ground because of the Treaty of Coruscant, which is why the Crossing is a standoff.
- **Adult ranks.** In canon, Jedi Initiates are usually children taken young. Here an adult wanderer is accepted as an Initiate (Sith: Acolyte) at the Crossing and becomes a Padawan or Apprentice after a trial. This is a gameplay adaptation.
- **Original characters:** Master Ilyra Saelen, Overseer Teyvar Sorn, Acolyte Brenn Ostrak and Captain Hessa Dray are original. No names are taken from the Old Republic games.
- **Saber forms** Soresu and Ataru are lore form names used for their known character (defence; acrobatic aggression). Their numbers are gameplay.
- **Alignment as a number** from -100 to +100 is a game abstraction of the brief's light/dark consequences.
- **Lightning colour** is white with violet-blue edges, consistent with the films and games, and chosen for readability, not copied from any asset.

---

## 18. Open questions

Only the decisions that block development. Each has a recommended default, so work can go ahead without waiting; the lead records the owner's answer in `DECISIONS.md`.

1. **Can a Jedi or Sith change path in Milestone 2?** The brief wants redemption and corruption through a substantial questline. Building one now would double the story work.
   **Default:** no path change in M2 (operator command only). Drifting alignment has real consequences (dormant passives, refused powers, blocked trials, atonement), and the redemption and corruption questlines come in M3, when there are enclave and academy locations to set them in.
2. **How much does Independent get in M2?** Without a mentor there is no signature power.
   **Default:** Independents get the `unbound` passive (cheaper, faster universal powers), the same mission and choices, +3 SP instead of +1, the fourth slot and the `first_rank` Resonance. Their own signature power (for example Force Sense or Telekinetic Lift through a holocron) comes in M3.
3. **Heavy attack input.** Sneak + Attack is simple and conflict-free but slows movement. Hold-to-charge on the attack button would need the vanilla light attack to be delayed until release.
   **Default:** Sneak + Attack in M2. A client option for hold-to-charge can follow if play-testing asks for it.
4. **Placing blocks into an existing world for camps and the raider camp.** This is atmospheric, but it edits terrain near players.
   **Default:** place the small templates on natural ground only (checks in 5.1: no built blocks, outside spawn protection, terrain adapted, never remove non-natural blocks). Servers can switch to entities only with `story.placeStructures=false`.
