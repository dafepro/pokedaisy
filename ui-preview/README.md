# ui-preview

Renders PokeDaisy's real Compose UI to PNGs **without the Android SDK** — a
Compose _Desktop_ (JVM) build of the app's own sources against small
hand-written Android shims. Built for environments where Google's Maven /
`dl.google.com` is unreachable (e.g. Claude Code cloud sessions), where the
Android app itself can't even compile. Where the SDK _is_ available, prefer
Paparazzi (`./gradlew :app:recordPaparazziDebug`, see the repo's CLAUDE.md) —
but note Paparazzi only covers the companion; this also renders the top-screen
Library and Settings.

```bash
cd ui-preview
gradle render                    # all screens -> build/shots/*.png
gradle render -Ponly=settings    # names containing "settings"
gradle render -Pgame=EMERALD     # companion as another GameKind
gradle render -Plang=DE          # the app's text in another language (JA FR DE IT ES BR PT)
gradle render -Pgame=EMERALD -Prom=/path/pokeemerald.gba   # + the GUIDE's live pages
gradle render -Pgame=EMERALD -PgameText=EMERALD_LEGACY -Prom=/path/legacy.gba   # Emerald Legacy (its own save fixture)
gradle render -PcompanionW=960 -PcompanionH=1080   # the companion as a single-screen side panel
gradle :data:test test            # synthetic Pinball reader + website compatibility parity
gradle render -Ponly=pinball      # synthetic Pinball UI, navigation, and three device sizes
gradle render -Ponly=pinball -Pdark=true -Pout=build/pinball-dark
# Short-lived cloud sessions can isolate repeated navigation (default 4 rounds):
gradle render -Ponly=pinball-synthetic-back-navigation-thor -PpinballNavRounds=2
```

The Pinball tests and screenshots use synthetic data, not a ROM or captured gameplay.
They check the reader's bounds and state transitions, the actual Compose layout, and
repeated SETTINGS / STATES / BACK navigation. They do not establish live-ROM or Android-device correctness.
The root `test` task also verifies that the website's checked-in compatibility data
matches the app's tables. Neither test task needs an Android SDK.

In a cloud sandbox where the JVM's home directory is read-only, Skiko needs a writable
cache location. Create a local directory and set `JAVA_TOOL_OPTIONS="-Duser.home=/absolute/path/to/java-home"`
before running Gradle; this applies to the renderer's child JVM as well. A writable
`XDG_CACHE_HOME` directory also lets native Fontconfig cache its font lookup results.

`-Prom` points the GUIDE's live pages (HERE's wild list, NEXT BOSS) at a ROM
byte-identical to retail FireRed rev 1 / Emerald - e.g. the pinned decomp built
unpatched - with the `firered_vanilla` / `emerald_vanilla` save fixture as the
save (its flags mark what's done). Without it the GUIDE shots show HERE's area
lists and the hand-written pages only.
The `guide-town-*` shots render HERE in a town, scrolled to PEOPLE / ITEMS.

(Any Gradle 8.x works; there's no wrapper here. `./gradlew -p ui-preview render`
from the repo root uses the app's.) The first build takes several
minutes — the data module's generated species/move tables are huge — after
that a UI edit re-renders in well under a minute.

It doubles as the only **compile check** for UI code in such environments:
the app's `companion/**`, `LibraryActivity`, `SettingsActivity` & co. are
compiled as-is. (The Paparazzi test itself can't be compiled here: Paparazzi's
`DeviceConfig` exposes Google-hosted layoutlib types.)

## What's rendered

`src/render/Main.kt` — each companion tab on the AYN Thor's bottom screen
(1240x1080 landscape @ 2.625) with a fake party/bag/savestate list, some tapped states
(the FF SPEED pick-list, HOTKEYS, the CLOSE GAME confirm), and the
Library (list + grid) and Settings pages on the top screen (1920x1080 @ 2.5).
`CompanionScreen(initialTab = …)` picks the tab; taps go through
`runSkikoComposeUiTest`'s `performClick()`. Add a `Shot` for a new screen.
Game art (party slots, backdrops, region maps - FireRed / Emerald's and the hacks') isn't bundled -
the app rebuilds it from the ROM (`RomArt`) - so the renderer does the same at
startup from the decomp builds under `$DECOMPS` (`pokefirered/`, `pokeemerald/`),
`-Prom` or `-PartRoms=a.gba,b.gba`, into `build/scratch/files/rom-art`. The fake
party's mon icons come from `-PmonIcons=DIR` (or `$MON_ICONS`): one
`<species>.png` per internal Gen 3 species id, e.g. the private ROM-hack project's
`tools/telemetry-viewer/assets/pokemon`. Without it the party shows no icons. Without
any of those ROMs (a cloud session) those games show their fallbacks: the drawn
party slot, no backdrop, a text-only map. A FireRed-engine hack's own region map
(`RomRegionMap`) comes from `-Prom` too: `gradle render -Pgame=UNBOUND
-Prom=<unbound.gba> -Ponly=map`.

The `*-back-*` shots open something, press the device BACK (`pressBack()`) and
should look like the screen they started from; the SETTINGS ones assert each
step, so a miss prints `action failed`.

## How it works / quirks

- `data/` compiles `companion/data`, `companion/*.kt`, `Prefs`, `Hotkeys`,
  `GbaControls`, `MgbaCore` (its JNI `external fun`s compile fine and are
  never called) + `data/shims` (`Bitmap` as an int array, `BitmapFactory` via
  ImageIO, in-memory `SharedPreferences`, `Context.assets` reading
  `app/src/main/assets`, `LocalContext`, `Font(path, assets)`, …). Separate
  module so it compiles once.
- The root module compiles `companion/ui/**` and the two Activities from a
  copy (`prepSrc`) that rewrites `Icons.AutoMirrored.Filled.X` to
  `Icons.Filled.X` (not in desktop Compose 1.5), plus `src/shims`
  (`ComponentActivity` whose `setContent {}` is captured, `Intent`, `Uri`,
  `Toast`, activity-result stubs, …).
- Pinned to **Compose Multiplatform 1.5.12 / Kotlin 1.9.22**: CMP 1.6+ needs
  androidx runtime jars that only exist on Google Maven.
- Popups (a `DropdownMenu`) are separate roots; `captureAllRoots()` draws
  them over the window, so a shot can show an open menu. Long presses go
  through `performSemanticsAction(SemanticsActions.OnLongClick)` - injected
  touch long-clicks didn't reach `combinedClickable` here.
- The prefs shim is shared by every shot; `activity {}` resets the fields
  shots depend on (view mode, SteamGridDB key) first.
- The renderer drives a **manual clock** — the party icons and map highlight
  animate forever, so `waitForIdle()` would hang.
- Desktop ≠ Android: text metrics, ripple and focus behavior differ slightly
  (a clicked row can keep a focus highlight here that touch mode wouldn't
  show). Good for layout, spacing, colors and "does it compose at all" — the
  device is still the final word.
- If the compiler reports "Unresolved reference" for something that plainly
  exists, delete `build/kotlin` (stale incremental cache — happened once).

### Private Pinball RAM captures

With a user-owned exact supported ROM and `native-capture/mgba_dump` captures,
run the production decoder against real RAM without Android:

```sh
gradle -p ui-preview :data:pinballCapture \
  -Prom=/private/PokemonPinball.gba -Pcaptures=/private/pinball-captures
```

The capture directory contains named subdirectories, each with a complete
`ewram.bin`. The tool verifies the raw ROM SHA-1 before decoding. An optional
`expected.tsv` in the capture directory asserts three tab-separated columns:
`capture-folder`, `field`, `expected-value`. Fields are `phase`, `table`, `mode`,
`timer`, `saver`, `balls`, `score`, `area`, `progress`, and `objective`; absent
values use literal `null`. Author expectations from independent game-screen
observations, not by copying the decoder output. Keep the ROM, RAM, saves and
frames outside this repository. This tool does not demonstrate Android/JNI or
on-device UI integration by itself.
