# Vendors, credits, hub people and planet contracts (Milestone 4)

Owner: W2 (story, vendors, economy). Spec: `docs/MILESTONE_4.md` 3.5 (the hubs' people), 7.7 (credits), 7.8 (vendors,
the vendor screen, the contract terminal), 8.7 (planet contracts, the hub spar), 12 (saved state), 14 (`vendors`), 16.
Chapter Four is in `docs/design/chapter_four.md`; the art and sound are in `docs/assets/vendor.md`.

Server-authoritative throughout: stock loads on the server only and reaches a client when its vendor screen opens; the
screen sends intents (`vendor_buy`) and the server re-checks everything before money or items move.

## V1. Classes

| Class | Role |
|---|---|
| `vendor.VendorFeature` | Common init: the stock loader, the payloads, `VendorItems`, `HubEntities`/`HubNpcs`, `CreditDrops`; the balance section `vendors`. Called by the lead from `OldRepublicMod`. |
| `vendor.VendorStocks` | Reads `data/oldrepublic/vendor/*.json` on server start and `/reload` as JSON only (in 26.2 a reload runs before item components are bound, so no `ItemStack` is built there); each stock is parsed on first use, memoised and forgotten on the next reload. A bad file or stock is logged and skipped. |
| `vendor.VendorStock`, `VendorEntry` | One stock (id, faction, entries, the `buys` map) and one line (the lead's frozen record: stack, price, faction, tier ordinal, discipline, level, daily limit). |
| `vendor.Vendors` | `open` (refusal check, then `vendor_stock`), `handle` (a `vendor_buy`: reach, gate, day's limit, price, credits, space), `buy`, the Sell tab, the chit/bundle exchange, `boughtToday`, `openStock`. Sessions are in memory. |
| `vendor.VendorRules` | Pure rules: `price` (multiplier, the Honoured discount), `sellPrice`, `refuses`, `lockReason`, `dailyLeft`, `allowedCount`, `contractCredits`, `backPay`, `creditDrop`. |
| `vendor.CreditDrops` | Enemy credit drops (troopers, Mandalorians, security droids; never natural patrols). |
| `vendor.VendorItems`, `KeepsakeItem` | The story items and the two blocks. |
| `vendor.block.ContractTerminalBlock` | A hub's contract board (facing; unbreakable in survival). |
| `vendor.block.MarkerBeaconBlock` | Kaast's beacon (blinking lamp, chirps; replays the challenge for its owner). |
| `vendor.hub.HubRole`, `HubEntities`, `HubNpc`, `HubNpcs` | The hub people: `hub_host`, `apprentice`, `acolyte_hub`, `proving_guard`, `vendor` entity types; the `hub_spawner` factories; what each says; the hub spar; the escort; Zarvos's echo. |
| `vendor.network.VendorStockPayload`, `VendorBuyPayload` | S2C stock (entries, prices, lock reasons, per-day counts, the player's credits) and C2S intent (index, count, mode). |
| `mission.PlanetContracts` | The planet contract templates, `escort` and `spar` objectives, the terminal board per dimension. |
| client `vendor.VendorScreen`, `VendorClient`, `HubNpcRenderer` | The screen, the client init, the hub people's renderer (the mentors' robed model, a skin per person). |

## V2. Credits (7.7)

- Credits are items: `credit_chit` (1) and `credit_bundle` (100), the lead's `M4Items` and `economy.Credits`. Spending
  takes chits first and breaks bundles as needed; a near ship's hold counts (`holdPaymentRange`).
- **Sources:** contracts pay `contractCreditsBase + contractCreditsPerLevel x level` at full pay only (the M3 daily
  full-pay count); arc missions `arcMissionCredits`; Kaast and the stash; selling at a vendor; the one-time M3 back pay
  (`backPayPerMilestone` per milestone, at most `backPayMax`); enemy drops.
- **Enemy drops:** a trooper, Mandalorian or security droid killed by a player in a fight that counts drops 1 to 6
  chits at 50% (`creditDropChance`, `creditDrop`). Natural patrols never drop (the M3 anti-farm rule).
- `credits.pickup` plays for the player when a vendor pays them (a sale) and on the chit/bundle exchange. Chits picked
  up from the ground keep the vanilla pickup sound.

## V3. Vendors (7.8)

| Stock (`data/oldrepublic/vendor/<id>.json`) | Where | Gate |
|---|---|---|
| `watch_quartermaster` | the Greenwatch | Jedi Order tier; Sith refused |
| `imperial_quartermaster` | the Proving | Sith Empire tier; Jedi refused |
| `trainer_medtech`, `trainer_artifice` | the Greenwatch | none (trainers serve every path) |
| `trainer_armstech`, `trainer_armourcraft` | the Proving | none |
| `spacer` | both hubs | none |
| `requisition_republic`, `requisition_imperial` | M3 outposts, through the officers' "Requisition" option | the officer's faction; refused at Wary |

Stock format: `faction` (optional), `buys` (item → base value; the vendor pays `sellRate` of it), and `entries`, each
with `item` or `schematic` (W5's schematic item), `count`, `price`, optional `tier` (`neutral`, `trusted`, `honoured`),
`discipline` and `level` (trainer lines), `daily_limit`. Prices: the training saber (and the Proving's red one) is 150;
the motivator 400 (360 at Honoured, `honouredDiscount`).

Rules: below Neutral, or the opposing path at the other side's hub, a faction vendor refuses (`vendor.refuse`, a
message). A line above the player's tier, or above their discipline level, shows locked with its reason ("Requires
Trusted with the Jedi Order", "Requires Armstech 3", "Sold out today"). Daily limits are per player per in-game day
(`Clocks.day`), in the story attachment's counters, cleared when the day changes. A purchase is refused before
payment for missing credits or space; success plays `vendor.buy`. Trainers also unlearn and take commissions (W5's
`discipline:unlearn`, `trainer:commission`, fees in W5's `CraftingRules`).

**The screen:** a Buy tab (icon, name, price for this player, left today, the lock reason in amber), a Sell tab (what
the vendor takes and for how much), a footer with the quantity, the Buy/Sell button and total, the chit/bundle
exchange; the player's credits in the header. Not a pause screen. It refreshes in place on each `vendor_stock` and
closes when the vendor is gone or out of `reach`.

## V4. Hub people (3.5, 8.3)

W1's hub templates carry `hub_spawner` anchors with roles; `HubNpcs` registers the factories:

| Role | Entity | Variant | Notes |
|---|---|---|---|
| `host` | `hub_host` | `ansa` (Selvaren), `kaddor` (Varkhast) | First talk `c4_<host>_meet`, then `c4_<host>`. Welcome or cold (Wary) line on arrival. The free daily recall (W3's `ship:recall_free`). |
| `apprentice` | `apprentice` | `apprentice_0..2` (seeded by spawner position) | Barks; `hub:spar` (casual spar, training saber). |
| `acolyte` | `acolyte_hub` | `acolyte_0..3` | Barks; `hub:spar`. |
| `guard` | `proving_guard` | `proving_guard` | Barks; wary of Jedi. |
| `vendor:<stock>` | `vendor` | the stock id | Opens `vendor_<stock>` or `trainer_*`. |

Hub people are persistent, leashed to their spawner and untouchable by players and mobs (the M3 terminal rule,
Peaceful-safe), except as a spar partner (only their partner, through the `Spar` rules, never lethal) or an escort
(mobs can hurt them; not saved; respawned beside the owner after a restart). Area powers skip them (the attitude
provider). Zarvos's echo (the Sepulchre) is a `hub_host` with `echo` set: still, invulnerable, not saved.

## V5. Planet contracts (8.7)

Each hub's `contract_terminal` opens its planet's board (`Missions.openPlanetBoard`), seeded per day, player and
dimension (`MissionRules.boardSeed(day, uuid, terminal, dimension)`); offers are used per dimension. Six templates per
planet: `cleanse`, `cull`, `recover`, `survey`, `escort` (new objective: an apprentice or acolyte follows the owner to
the nearest waystone or tomb mouth; its death fails the contract) and `spar` (new objective: win a bout at the hub,
`hubSparYield` and `hubSparDamage`). Pay: contract XP, the issuer's standing and loot `gameplay/planet_contract` at full
pay, plus contract credits.

## V6. Saved state

Vendor day counters: story counters `vendor.day` and `vendor.<stock>.<line>`. Back pay: `StoryData.chapter4.back_pay`.
Escorts and spar bouts are in memory. Stocks are data, not saved.

## V7. Tests (`StoryM4GameTests`)

`vendorTierGatesAndRefusals`, `vendorDailyLimitPerPlayer`, `commissionChargesFeeAndStandardQuality` (W5's
commission through the bench), `contractCreditsOnlyAtFullPay`, `requisitionRefusedAtWary`,
`planetContractsSeededPerDimension`, `backPayOnceCapped300`, `motivatorThreeRoutes` (the requisition stock).
Client: `oldrepublic_m4_24_vendor_locked`.

## V8. Balance (`vendors`)

| Key | Default | Meaning |
|---|---|---|
| `priceMultiplier` | 1.0 | Every price |
| `honouredDiscount` | 0.9 | At Honoured with the vendor's faction |
| `sellRate` | 0.2 | Share of the base value paid for sold items |
| `holdPaymentRange` | 32 | A ship's hold within this range pays too |
| `creditDropChance`, `creditDrop` | 0.5, [1, 6] | Enemy credit drops |
| `contractCreditsBase`, `contractCreditsPerLevel` | 25, 5 | Contract credits at full pay |
| `backPayPerMilestone`, `backPayMax` | 100, 300 | The M3 back pay |
| `maxPurchase` | 64 | Most copies of a line in one purchase |
| `reach` | 8 | Blocks from the vendor for the screen to stay open |
| `hubSparYield`, `hubSparDamage` | 0.4, 0.5 | The hub spar: the partner yields at this health fraction; its damage multiplier |
