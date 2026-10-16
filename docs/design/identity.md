# Identity: alignment, reputation, paths, trust, dialogue, yielding and settlers

Milestone 2 spec: `docs/MILESTONE_2.md` sections 3, 4, 6.3, 6.4, 9.7.3, 9.7.4 and 7.7 (Standing tab). This page
describes how the identity feature implements them. Code: `com.oldrepublic.identity`, `com.oldrepublic.dialogue`,
`client.identity`, `client.dialogue`.

## Storage

- One persistent attachment `oldrepublic:identity` (`IdentityAttachments.IDENTITY`, `IdentityData`), copied on
  death and synced **to its owner only**. Fields: `alignment` (-100..100), `alignment_log` (last 8: amount, reason
  key, game day; same reason, day and direction merge into one line), `caps` (day and today's minor light, minor
  dark and severe totals), `reputation` (faction id to -1000..1000), `path` (by id), `rank`, `trust` (mentor id to
  0..100) and `choices` (one-time source ids).
- Every change goes through `Identity` (server). It mutates a copy and calls `setAttached`, which syncs.
- Pure rules live in `IdentityRules` so game tests can drive them without a player.
- Tunables: `config/oldrepublic/balance.json`, section `identity` (`IdentityBalance`).

## Alignment

- **Major** shifts (`Cap.MAJOR`): the source id is recorded in `choices`, so a second shift from the same source is
  ignored. No cap. An empty source id (operator commands) is never recorded.
- **Minor** shifts (contextual, and `AlignmentShift.Kind.MINOR`): at most `minorDailyCap` (6) per direction per game
  day (`dayIndex = gameTime / 24000`), and never past ±`minorLimit` (60) in the direction of movement. Moving back
  toward the centre is always possible within the day's cap.
- **Severe** (`KILLED_INNOCENT`): darkness only, `severeDailyCap` (24) per day, may pass ±60.
- Creative and spectator changes and operator changes apply and are logged.
- Bands: Light ≥ 50, Leaning Light ≥ 15, Balanced, Leaning Dark ≤ -15, Dark ≤ -50 (configurable on the server;
  the client always shows the default edges).

### Contextual detection (`ContextualActs`)

| Act | Detected | Amount | Cap |
|---|---|---|---|
| `HELPED_OTHER` | Reported by abilities (`Identity.contextual`), and freeing captives | +1 | minor |
| `DEFENDED_INNOCENT` | A hostile (`Enemy`) that damaged a villager, wandering trader or settler is killed by a player within `defendWindowTicks` (100) | +1 | minor |
| `CRUELTY` | Damage from a `#oldrepublic:force_harm` damage type to a passive `CREATURE`; any player damage to a yielded entity other than the decider's confirmed execution | -1 | minor |
| `HARMED_INNOCENT` | A player damages a villager, wandering trader or settler (once per target per `harmRepeatTicks`, 200) | -2 | minor |
| `KILLED_INNOCENT` | A player kills one (direct, or it was last hurt by a player): -4, a settler -8 and Frontier reputation -100 | -4 / -8 | severe |

`contextualAlignment: false` in the balance file turns all of this off.

### Feedback

- Nothing is shown before the player has been introduced (`identity:introduced` recorded at the Crossing, or any
  path chosen); values are still recorded.
- Effect `alignment_shift` (to the owner; intensity = amount; variant = new band, flags band changed / minor).
  A major or larger shift shows a framed toast top left under the Force HUD for 3 s, "Your choice echoes toward the
  Light (+12)", with `identity.alignment_light` or `identity.alignment_dark`. A contextual shift of 3 or less shows a
  small text line only, no tone, and can be turned off with `/oldrepublicfx alignment_toasts off`.
- A band change also shows a banner ("You lean toward the Light").
- Effect `reputation_change` (to the owner): a framed toast in the faction colour; on a tier change it names the
  new tier (read a few ticks later from the synced data).

## Reputation, path, rank and trust

- Tiers: Hostile ≤ -500, Distrusted ≤ -100, Neutral, Trusted ≥ 100, Honoured ≥ 500.
- `Identity.changeReputation(player, faction, amount, sourceId, oneShot)`; one-shot sources are recorded as
  `rep:<faction>:<source>`.
- `Identity.setPath` enforces: NONE → any; INDEPENDENT → JEDI or SITH once; JEDI ↔ SITH only with `force`. Joining
  Jedi or Sith sets rank 1, applies the join reputation (+150 own, -100 rival, one shot `join.<path>`) and raises the
  mentor's trust to 20 once. It calls `QuestHooks.onPathChanged`.
- `Identity.promote` only raises the rank and sends `rank_up` (banner "Rank attained", `identity.rank_up`, a column
  of pale motes seen by everyone nearby).
- Trust is 0..100 per mentor (`jedi`, `sith`).
- The story feature applies the reputation, alignment and trust of its own choices (captain, captives, lessons,
  trials) with the source ids of section 3.2; the identity feature does not apply them a second time.

## Dialogue engine

- Files: `data/<namespace>/dialogue/<path>.json`, loaded on start and `/reload` (`DialogueLoader`). Invalid files
  are logged and skipped. Format: see the Javadoc of `DialogueDefinition`. `start` may be a node id or a list of
  `{if, node}` rules whose last rule has no conditions.
- Conditions: `path`, `path_not`, `rank_min`, `alignment_min`, `alignment_max`, `trust_min`, `reputation_min`,
  `flag` (progression flag or identity choice, `!` negates), plus any registered with `DialogueConditions`.
  Unknown or malformed conditions fail closed with "Unavailable" and are logged once.
- Reasons travel as `key|arg|arg` (`@key` arguments are translated on the client).
- Sessions (`DialogueSession`, one per player, in memory): the server sends `dialogue_node`, the client answers with
  `dialogue_choice (session, option index)`. The server re-checks the session id, that the speaker is alive and
  within 6 blocks ("Too far away to answer" otherwise) and the conditions (a failed check resends the node). Then it
  applies the stance (a major shift with the option text as the reason; trust once per stance source), runs the
  action and moves on or closes.
- A session ends (`dialogue_end`) when the screen closes (`dialogue_close`), the speaker is gone or over 8 blocks
  away, the player takes damage, dies, logs out or changes dimension, or the server stops. Mob speakers stop and
  face the player while talking.
- Screen (`DialogueScreen`): a holo panel in the lower third, the speaker's name over an accent rule, the portrait
  when the dialogue names one, the line typing out at 60 characters per second (Space, Enter or a click shows all of
  it), numbered replies picked by click or keys 1 to 6, disabled replies dimmed with the amber reason underneath.
  The game is not paused. Sounds `dialogue.open` and `dialogue.select`.

## Yielding (`Yielding`, `Yieldable`)

- Fabric `ALLOW_DAMAGE`: a player hit that would take a `Yieldable` (with `canYield()`) to or below
  `max health × yieldHealthFraction()` is cancelled, health is set to exactly the threshold and the entity yields
  (`setYielded(true)`; the entity kneels, lowers its weapon and stops its goals). Effect `yield`: a ring of dust and
  the entity's surrender line (`captain.yield` / `acolyte.yield`, chosen by entity type). The decider gets an
  overlay prompt: use it to decide, or strike again to execute.
- While yielded every damage is cancelled (area effects included). The decider's direct melee strike warns "Strike
  again to execute"; a second strike within `executeConfirmTicks` (60) executes. Any other player damage counts as
  cruelty.
- Using the entity opens `oldrepublic:yield` (Spare / Execute, each confirmed on its own node, or turn away).
- No decider within `yieldLeaveDistance` (32), or `yieldTimeoutTicks` (2400) passed: it flees.
- Every outcome calls `Yieldable.onYieldResolved` and `QuestHooks.onYieldResolved`, once (resolved entities are
  remembered for the session, and yielded entities loaded from disk are tracked again).

## Frontier Settler (`FrontierSettler`, `Settlers`)

- Passive humanoid, 16 HP, three skins. States `FREE` (wanders near home), `CAPTIVE` (kneels bound, persistent, not
  pushable, ×0.25 damage from `#force_harm` player damage) and `FLEEING` (runs toward home, despawns after 600 ticks
  or 24 blocks).
- Settlers flee from players they distrust (Frontier ≤ Distrusted, within 12 blocks with line of sight) and will not
  talk to them. A free settler greets according to Frontier standing.
- Talking to a captive (the owner only) opens `oldrepublic:captives`: cut them loose / your freedom has a price /
  leave them, each confirmed, plus "who did this".
- `Settlers.resolveCaptives` resolves the whole group once (`captives:<group>` recorded): freed or paid settlers
  drop their rope (effect `settler_freed`: rope fibres and `settler.freed`) and run home; leaving them keeps them
  bound. Then `QuestHooks.onCaptivesResolved` (the story feature applies the choice's alignment, reputation and
  trust), and only then the loot: paid captives drop `gameplay/captive_payment`; freed captives drop
  `gameplay/settler_thanks` when Frontier is now Trusted (so the +150 for freeing them counts) and count as
  `HELPED_OTHER`.

## Standing panel (`StandingPanel`)

Hosted by the Force screen's Standing tab. Three pages picked with chips:

- **Alignment:** the bar from deep red-black through steel to pale gold with the band edges ticked and a marker,
  the band and value, and the last 8 shifts (reason, game day, amount).
- **Factions:** each faction's icon, name, tier and value, a centred bar with tier edges ticked, and what the tier
  unlocks.
- **Path:** path and rank title (with rank pips), the next rank's requirements with ✓/✗ (Trusted with the own
  order, mentor trust 60, alignment on the right side of 0, then the trial), and the mentor card: portrait, trust
  bar with the 20 / 30 / 60 marks, and the active lesson when the story feature provides one
  (`StandingPanel.setLessonSource`).

## Commands (operators)

`/oldrepublic identity [show <player>]`, `alignment <player> set|shift|minor <n>`, `contextual <player> <act>`,
`reputation <player> <faction> set|add <n>`, `path <player> <path> [force]`, `rank <player> set|promote <n>`,
`trust <player> <mentor> set|add <n>`, `choice <player> add|remove <id>`, `introduce <player>`, `reset <player>`,
`settler free|captives`, `yield <entity>`. `/oldrepublic dialogue list|open <id> [speaker]|choose <n>|close`.
Every change is logged.
