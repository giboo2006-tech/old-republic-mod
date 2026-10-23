# Vendors, credits and hub people: art and sound (Milestone 4)

Owner: W2. All PLACEHOLDER, original and generated: textures by `tools/assets/gen_textures_hub.py` (PIL, deterministic
seeds), sounds by `tools/assets/sounds/story_m4.py` (numpy synthesis, nothing sampled). Design: `docs/design/vendor.md`.

## V1. Sounds

| Event | File | Length | Design |
|---|---|---|---|
| `vendor.buy` | `vendor/buy.ogg` | 0.6 s | Three quick chits counted onto a counter (filtered clicks), then a soft two-tone accept beep from the vendor's slate (B5 then E6) |
| `vendor.refuse` | `vendor/refuse.ogg` | 0.45 s | Two short low buzzes from the slate (A3 then F3), soft-gated pulse waves under a low-pass |
| `credits.pickup` (a vendor pays, the exchange) | `credits/pickup.ogg` | 0.35 s | Two or three bright clinks of small metal-edged chits (short inharmonic rings at 2.6 to 3.4 kHz with a click each) |
| `hub.apprentice_spar` | `hub/apprentice_spar.ogg` | 0.7 s | Training sabers meeting: a low, dull blade buzz with a short decay, a wooden-ish knock and a brief swish; quieter and duller than a duelling clash |

## V2. Hub people (`textures/entity/hub/<variant>.png`, 64x64)

The mentors' robed humanoid (`client.mentor.MentorModel`): the vanilla humanoid parts plus a robe layer (16,32), a
skirt (0,50), a hood (36,50) and a half-cape (40,32); the hat layer (32,0) carries helmets, caps and goggles. Hooded
people show the hood, the Empire's the cape (`HubNpcRenderer.hooded`); a layer left transparent is not drawn, so coats,
armour and aprons leave the legs bare. A variant without its own skin falls back to its role's.

| Variant | Look |
|---|---|
| `ansa` | Grey hair streaked lighter and tied back, a lined face; moss over-robe open over undyed cream, bark belt, deep green hood, green skirt |
| `kaddor` | Cropped dark hair grey at the temples, a scar through the right brow; grey uniform coat with red trim and a brass pin, black gloves and boots, black half-cape, long grey coat skirt |
| `zarvos` | The Sepulchre's echo: cold violet-grey skin, long dark hair, ember eyes; violet-black robe with purple trim, a dark cape |
| `apprentice_0..2` | Young apprentices (three faces and hair colours): plain sage or sand tunics to the hip, leather belts, light hoods |
| `acolyte_0..3` | Acolytes (four faces: cropped, shaved, red, pale blond): dark grey robes, a red sash, black half-capes |
| `proving_guard` | A plain grey helmet with a dark red visor band and cheek guards; chest plates over a dark suit, a red stripe, knee plates; a short black cape |
| `watch_quartermaster` | Full brown beard; a leather apron with a pocket seam over a green tunic; a green hood |
| `imperial_quartermaster` | A peaked grey cap with a brass badge; grey coat with a red armband stripe and trim; a dark cape |
| `spacer` | Goggles on the brow (pale blue lenses); a worn tan flight jacket with a pale collar; a scarf hood on Selvaren, a brown duster cape on Varkhast |
| `trainer_armstech` | Cropped hair and a dark beard, red-lensed goggles; olive coveralls and a tool belt |
| `trainer_armourcraft` | Grey hair and beard; a dark leather smith's apron over a grey shirt, heavy gloves |
| `trainer_artifice` | Long dark hair; a slate-blue robe with crystal-blue trim, a blue hood |
| `trainer_medtech` | Copper hair tied back; an off-white tunic with teal bands (no cross or other protected emblem), a pale hood |

## V3. Credits and the terminal

| Asset | Look |
|---|---|
| `item/credit_chit` | A small brass-rimmed tablet (9x6 px) with a teal data stripe and a highlight corner |
| `item/credit_bundle` | Three chits stacked on a diagonal, bound with a red strap |
| `block/contract_terminal_front` | A dark console screen with an amber header bar and three teal entry lines, three coloured keys below, a bevelled grey frame |
| `block/contract_terminal_side` | Grey panels with vent slats, a seam and a darker lower band, corner bolts |
| `block/contract_terminal_top` | A plate with a seam, bolts and a small teal status light |

The terminal is a full cube facing the player who placed it (`facing`, the blockstate rotates the model); block light 5.

## V4. The vendor screen

Drawn with the M3 UI kit (`client.ui.Ui`): the panel, the faction emblem (`identity/faction_<id>`) by the vendor's
name, the player's credits in the header, item icons on each row, prices in gold (affordable) or grey, the lock reason
in amber on a locked row, the day's remaining count, the quantity field and the Buy/Sell button in the footer. No new
GUI textures were needed.
