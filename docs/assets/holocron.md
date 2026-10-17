# Holocron assets (Milestone 3: holocrons, gatekeepers, the Archive)

All art here is a **placeholder generated** by our own deterministic script (PIL), original, run from anywhere.
Nothing is copied from or traced over Star Wars games or films: the shapes follow the spec's own descriptions (4.1,
4.2) and the gatekeepers are original characters.

- Textures, item definitions, item models and particle sprite lists: `python3 tools/assets/gen_textures_holocron.py`
- The echo shard's art is still the lead's Phase 0 placeholder from `tools/assets/gen_textures_m3items.py` (not
  redrawn here).
- Sounds `holocron.*`: recipes in `tools/assets/sounds/holocron.py` (Part A of W3), rendered with
  `python3 tools/assets/gen_sounds.py --m3 --feature holocron`.

Paths are under `src/main/resources/assets/oldrepublic/` unless they start with `data/`.

## Items

Each holocron has a lit look (glowing seams) and a dark silhouette. The dark look is chosen by the item's custom
model data string `dark`: the Holocrons shelf draws unfound, sealed, broken and returned holocrons with it, and
`RisingHolocron` uses it for a sealed holocron as it leaves the pedestal. In the inventory (display context `gui`)
the holocron is a flat 16x16 icon; in hands, on the ground, in item frames and over the pedestal it is a small 3D
model (6 units across, so the `fixed` display shows a 0.375-block holocron) whose seam elements are slightly
outset, unshaded and carry `light_emission` like the lightsaber blade.

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/item/jedi_holocron.png` (16x16) | Jedi holocron icon body: an isometric pale stone cube | placeholder generated | Pale, faintly warm limestone, slightly chipped corners, worn smooth by hands. Reads as old stone, not marble or plastic. |
| `textures/item/jedi_holocron_seams.png` (16x16) | Its seams layer (`layer1`, transparent elsewhere): gold seams on the three front edges and across each face | placeholder generated | Thin warm gold lines, brightest at the joins, as if light leaks from inside. No runes, no glyphs. |
| `textures/item/jedi_holocron_dark.png` (16x16) | Dark silhouette icon (seams dim, no glow) | placeholder generated | The same cube, unlit: grey stone, seams a dull bronze line. Must still read as "a holocron you have not found". |
| `textures/item/jedi_holocron_model.png` (16x16) | Material sheet of the 3D model: body (top left 8x8), seams (top right), dark body (bottom left), dark seams (bottom right) | placeholder generated | Keep the four quadrants; the models' UVs point into them. Stone grain in the body quadrants, a flat bright gold in the lit seam quadrant. |
| `textures/item/sith_holocron.png`, `_seams.png`, `_dark.png`, `_model.png` | Sith holocron: a black glass stepped pyramid seen corner on, crimson seams along the steps and the front edge | placeholder generated | Volcanic black glass with sharp, cold highlights; seams a deep crimson, darker than a saber blade, like light under the surface. No skulls, no spikes. |
| `textures/item/wayfarer_holocron.png`, `_seams.png`, `_dark.png`, `_model.png` | Wayfarer holocron: a worn brass sphere with a tilted ring, pale gold seams on its meridian and equator | placeholder generated | Travel-worn brass, rubbed bright where held, darker in the grooves; the ring a separate band of darker metal. Instrument-like, a navigator's object. |
| `items/jedi_holocron.json`, `sith_holocron.json`, `wayfarer_holocron.json` | Item definitions: select on custom model data `dark`, then on display context (`gui` the flat icon, otherwise the 3D model) | final (data, generated) | - |
| `models/item/<kind>_holocron.json`, `<kind>_holocron_dark.json` | Flat icons (`item/generated`: body `layer0`, seams `layer1`; the dark one only its icon) | final (data, generated) | - |
| `models/item/<kind>_holocron_held.json`, `<kind>_holocron_held_dark.json` | 3D models: cube, stepped pyramid (four tiers) or sphere with a tilted ring, built from boxes, with outset emissive seam elements; display transforms for hands, ground, head and frame | final (data, generated) | A modeller may replace the boxes; keep the size (6 units) and the emissive seam elements. |
| `textures/item/holocron_shard.png` (16x16) | Echo shard (`oldrepublic:holocron_shard`) | placeholder generated (lead's Phase 0 art, from `gen_textures_m3items.py`) | A thumb-sized splinter of pale, neutral crystal (the same for every kind) with a faint glow at its core. Not a cut gem; a natural shard. |
| `items/holocron_shard.json`, `models/item/holocron_shard.json` | Shard item definition and `item/generated` model | final (data, Phase 0, unchanged) | - |

## Gatekeepers: portraits and the projected figure

The portraits are GUI sprites (`oldrepublic:portrait/gatekeeper_<id>`), drawn by the dialogue screen and by the
Holocrons tab. They are painted as projections, not paintings: a field of light mapped to the kind's tint on a dark
backdrop, with horizontal banding, a rim light and one slipped row. Tints by kind: Jedi warm gold, Sith rust
crimson, Wayfarer brass.

| Path (`textures/gui/sprites/portrait/`) | Gatekeeper (spec 4.2) | Status | Brief for the final artist |
|---|---|---|---|
| `gatekeeper_wayfarers_compass.png` (32x32) | Tessaly Arn, a pilgrim of no Order | placeholder generated | Young-to-middle-aged woman, short hair, goggles pushed up on the brow, a travel scarf. Open, curious face. Brass tint. |
| `gatekeeper_wayfarer_pell.png` | Pell of the Long Road | placeholder generated | Old man, hood up, full beard, plain robe. Patient, weathered. Brass tint. |
| `gatekeeper_wayfarer_osk.png` | Navigator Osk Brevan | placeholder generated | Broad face, short hair under a scout's cap, stubble, a high collar and a lens eyepiece over one eye. Brass tint. |
| `gatekeeper_wayfarer_maelis.png` | Cartographer Maelis Dunn | placeholder generated | Narrow face, hair tied back under a headband, a scarf. Precise, a mapmaker's squint. Brass tint. |
| `gatekeeper_jedi_morrane.png` | Master Aveth Morrane, founder of the Watch | placeholder generated | Elderly, hood up, long beard, lined face, robe. The founder: stern but kind. Gold tint. |
| `gatekeeper_jedi_sarrow.png` | Ione Sarrow, a healer of the old war | placeholder generated | Woman, hair in a bun, simple robe. Tired eyes. Gold tint. |
| `gatekeeper_jedi_ketterin.png` | Knight Ruan Ketterin | placeholder generated | Broad-faced man, short hair, a scar across the cheek, light armour pauldrons over the robe. Gold tint. |
| `gatekeeper_jedi_vanterre.png` | Archivist Sela Vanterre, Vasch's teacher | placeholder generated | Woman, long hair, a thin circlet, a high scholar's collar. Gentle and sad. Gold tint. |
| `gatekeeper_sith_zarvos.png` | Lord Hadrek Zarvos | placeholder generated | Heavy face, shaved head with a raised crest, stubble, heavy pauldrons. Crimson tint. |
| `gatekeeper_sith_ysolde.png` | Ysolde the Unbowed | placeholder generated | Woman, long hair, a high collar, facial tattoo lines. Defiant. Crimson tint. |
| `gatekeeper_sith_thrane.png` | Overseer Calix Thrane | placeholder generated | Bald, a narrow visor across the eyes, an officer's collar. An administrator, not a warrior. Crimson tint. |
| `gatekeeper_sith_torr.png` | Ilvane Torr, Keeper of the Sealed Dead | placeholder generated | Hooded, the face hidden behind a smooth mask, robe. Still and funereal. Crimson tint. |
| `gatekeeper_figure.png` (64x64) | The projected figure's skin (player skin layout, overlay layers transparent), multiplied by the kind's tint in code and drawn full-bright and translucent by `GatekeeperFigure` | placeholder generated (texture path approved by the lead) | Pale, almost white, horizontally banded, more transparent toward the feet; a hooded robe silhouette so every gatekeeper reads as "a recorded person", never a specific face. No glow outline. |

The three Archive holocrons' dialogues and shelf niches use `oldrepublic:portrait/orren_vasch` (W5's sprite, spec
16): until W5's portrait exists, the GUI shows the missing-texture sprite there.

## Particles

| Path | What it is | Status | Brief for the final artist |
|---|---|---|---|
| `textures/particle/holocron_mote_0.png` (8x8) | `oldrepublic:holocron_mote`: soft round speck of seam light (rise, seams splitting, edge motes of the forming and folding figure, Listen and Keep streams, echo glints) | placeholder generated | White soft dot with a 1 px brighter core, alpha falling to the edge; tinted by kind in code and drawn full-bright. Never a star or sparkle. |
| `textures/particle/projection_band_0.png` (16x16) | `oldrepublic:projection_band`: thin soft horizontal line drifting up through the figure | placeholder generated | One thin white line with soft vertical falloff, faded at both ends; tinted in code, alpha about 0.3. Should read as a scanline of a projection, not a ring. |
| `particles/holocron_mote.json`, `particles/projection_band.json` | Sprite lists | final (data, generated) | - |

The alcove dust, settling grit and the seal's and the Archive's exhales are `holocron_mote` particles in their
unlit dust mode (tinted sandy, lit by the world instead of glowing). The Break and Unmake chips are vanilla
`minecraft:block` particles of calcite (Jedi), blackstone (Sith) or gold block (Wayfarer).

## Dialogues (data)

`data/oldrepublic/dialogue/`, text in `src/main/lang-parts/holocron.json`. All are original writing. Each world
holocron's dialogue has no speaker entity: the name is the gatekeeper's, the portrait theirs, the accent the kind's
seam colour.

| File | Nodes | Notes |
|---|---|---|
| `holocron_wayfarers_compass.json` | `f1`-`f3` (at Courier's Fall), `strongbox`, `l1`-`l3`, `v1`-`v3` + `vasch`, `choose`, `replay`-`replay3` | Courier's Fall opening with the strongbox note; otherwise the common shape |
| `holocron_wayfarer_pell.json`, `holocron_wayfarer_osk.json` | `l1`-`l3`, `v1`-`v3` + `vasch`, `choose`, `replay`-`replay3` | `vasch` beat when `holocron:vasch_due` |
| `holocron_wayfarer_maelis.json` | `l1`-`l4`, `v1`-`v4` + `vasch`, `choose`, `replay`-`replay4` | four lines (the watchtower over the sinkhole) |
| `holocron_jedi_{morrane,sarrow,ketterin,vanterre}.json` | `l1`-`l3`, `v1`-`v3` + `vasch`, `choose`, `replay`-`replay3` | `choose` shows only what the path allows: Remember (Jedi), Break (Sith), Listen (Sith, Independent, none), and "Not yet" |
| `holocron_sith_{zarvos,ysolde,thrane,torr}.json` | `l1`-`l3`, `v1`-`v3` + `vasch`, `choose`, `replay`-`replay3` | Remember (Sith), Seal (Jedi), Listen (Jedi, Independent, none), and "Not yet" |
| `holocron_archive_{watch,codex,unbound}.json` | `replay`-`replay3` | replay only: these are never attuned at a pedestal |
| `archive_hoard.json` | `open`, `notes`, `confirm_return`, `confirm_keep`, `confirm_unmake` | the Archive choice after the boss |

Lang (`holocron.json`): `holocron.oldrepublic.*` (gatekeeper names, statuses, hints, messages), the three holocron
items, `dialogue.oldrepublic.{holocron_*,archive_hoard}.*`, the 15 codex texts `journal.oldrepublic.codex.holocron_*`
and `screen.oldrepublic.holocrons.*`. The dialogue files and the lang part are plain data: edit them directly.
"Not yet" (or closing the screen) leaves the moment with nothing attuned and nothing consumed.

## Sounds (from Part A)

The 12 `holocron.*` events of section 16. Recipes: `tools/assets/sounds/holocron.py` (W3 Part A), rendered with `python3 tools/assets/gen_sounds.py --m3 --feature holocron` (never `--java`). Every file keeps its placeholder name, so `sounds.json`, the subtitles (`lang-parts/sounds.json`) and `ModSounds` are unchanged. All files are mono 44.1 kHz Ogg Vorbis, **placeholder generated** (synthesised from scratch; no recorded or third-party audio). Palette: old stone and crystal. A holocron's voice is a soft, slowly beating crystal chord (D with a fifth and a ninth, each partial doubled a fraction of a hertz apart) over stone grit. Nothing is a fanfare; nothing is a sci-fi "computer" sound.

| Event | File (`sounds/...`) | Loop / stream | Plays when | Placeholder now | Brief for the final sound |
|---|---|---|---|---|---|
| `holocron.rise` | `holocron/rise.ogg` | one-shot | `holocron_attune` intensity 1: the holocron lifts out of its pedestal cradle (4.4) | 1.8 s: stone grit, the chord rising softly underneath, a dust breath | Weight leaving stone: a short grinding scrape as the holocron frees itself, then a low, almost subliminal tone that rises with it. 1.5 to 2 s. Soft attack; must not mask a trooper or sentinel tell. |
| `holocron.open` | `holocron/open.ogg` | one-shot | The seams unlock as the rise ends (`holocron_attune`, after the rise) | 2.2 s: three quick small clicks, the chord blooming an octave up, faint high shimmer | A small, precise mechanism releasing (three tiny catches, like a puzzle box), and the holocron's tone blooming out of the seams. Intimate, close-miked; 2 s tail. |
| `holocron.gatekeeper_form` | `holocron/gatekeeper_form.ogg` | one-shot | `holocron_gatekeeper` intensity 1: the figure assembles from the seams (heard by the attuning player only) | Rising airy noise gathering, then a warm chord fading in | A projected presence arriving: light gathering into a shape. A soft rising shimmer that resolves into a warm, sustained chord, with no voice and no whoosh. About 2 s. |
| `holocron.gatekeeper_fade` | `holocron/gatekeeper_fade.ogg` | one-shot | `holocron_gatekeeper` intensity 0: the figure folds back into the seams (choice made, or the moment cancelled) | The chord sinks and thins into a last breath of shimmer | The reverse of the form: the chord descends and thins, ending in a quiet exhale of shimmer. 1.5 to 2 s; reads as "gone", not "failed". |
| `holocron.hum` | `holocron/hum.ogg` | **seamless loop, not streamed** (volume 0.6) | While a holocron is risen and its gatekeeper speaks; a client watchdog stops it on fade, disconnect, death, dimension change, 16 blocks of distance or 2,400 ticks | 4 s seamless loop: the chord very soft, beating slowly, faint crystalline air above | The holocron's resting voice under dialogue: very quiet, slowly beating crystal chord with a breath of air above it. Must loop seamlessly (no click at the seam, no audible phrase), sit under speech and subtitles, and never pulse in a way that could be mistaken for a danger tell. 4 to 8 s loop. |
| `holocron.seal` | `holocron/seal.ogg` | one-shot | `holocron_choice` variant 0 (Seal: a Jedi seals a Sith holocron) | A soft stone settle and a long dust exhale | Closing, not destroying: the seams draw shut with a soft stone settle, the tone cut off under a long, calm dust exhale. Grave and quiet. |
| `holocron.break` | `holocron/break.ogg` | one-shot | `holocron_choice` variant 1 (Break: a Sith breaks a Jedi holocron) | A crack, a crunch, the light escaping and guttering out | Pale stone split along its seams: a sharp crack, a short crunch of shell, then the trapped light escaping as a thin tone that gutters out. Violent but brief (under 1.5 s); no glass-shatter cliché. |
| `holocron.listen` | `holocron/listen.ogg` | one-shot | `holocron_choice` variant 2 (Listen: the lesson is taken without keeping the holocron) | A gentle descending cascade of small chimes and a breath in | Knowledge flowing into the listener: a soft descending cascade of tiny crystal chimes that ends in a quiet inhale, as if the player took it in. 1.5 to 2 s. |
| `holocron.echo` | `holocron/echo.ogg` | one-shot | `holocron_echo`: an attuned pedestal yields an echo and a shard (4.7) | A faint, far-off chord in a large hall, then a small tick of a shard settling | Distance and memory: the holocron chord heard as if from the far end of a great hall, then a single small tick of a crystal shard settling on stone. Quiet; about 2.5 s with a long reverb tail. |
| `holocron.archive_return` | `holocron/archive_return.ogg` | one-shot | `archive_choice` variant 0 (Return the Archive) | Soft tones fading, cloth, a clasp | The three Archive holocrons dimmed and packed for travel: their tones fading one by one, a rustle of cloth, the click of a travelling case's clasp. Respectful, small. |
| `holocron.archive_keep` | `holocron/archive_keep.ogg` | one-shot | `archive_choice` variant 1 (Keep the Archive) | The chord swells warmly and rests | The seams warming and their light drifting into the keeper: the chord swelling warmly from three sources into one, then resting. A sense of weight accepted; no triumph. |
| `holocron.unmake` | `holocron/unmake.ogg` | one-shot | `archive_choice` variant 2 (Unmake the Archive) | The seams flare and drain, the shells crack and slump, a low chord resolves under it | Three holocrons unmade at once: the seams flare (a brief bright swell) and drain away, the shells crack and slump in stone, and a low chord resolves beneath it all. Heaviest of the set; up to 3 s. |
