# Pinball modernization: live guidance and collection

Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy

This fork starts a faithful Pokémon Pinball: Ruby & Sapphire companion. The
original ROM runs in the existing mGBA core. The companion only reads memory;
it does not replace physics, draw over the ball, inject inputs, or change saves.

## Implemented

- Exact unmodified English `BPPE`, revision `0`, SHA-1
  `9fec81ce2c5df589e0371a0bf2f92a5fe8db730b` detection. Another region, revision,
  patch or same-header ROM is rejected. The raw SHA-1 includes bytes at `0xC4`;
  the RPG reader's GPIO masking must not be used for Pinball.
- Dedicated `PINBALL` and on-demand `COLLECT` tabs, beside existing `STATES` and `SETTINGS`
  (and achievements when available). It does not inherit irrelevant RPG tabs.
- Current table, mode, one next-action hint, mode timer, mode progress, ball
  saver, balls left, displayed score and current area.
- Catch reveal/capture and Evolution phases; simple normal-play readiness,
  launch, travel, hatch and special-encounter guidance. Bonus stages have a
  generic game-screen prompt, never the main table's reused mode meanings.
- Menu/demo, initialization, pause, transitions, game-over and failed reads
  suppress stale coaching. A timed-out mode is never declared successful.
- Current-area collection catalog with separate two/three-arrow pools, caught
  status, missing filter, first-catch and previous-target exclusions. This is
  a catalog, not a random-target prediction; special encounters are excluded.
- Actual run-party evolution planning with fixed, one-of and both-on-success
  outcomes. Names, metadata and encounter pools are read from the exact user's
  ROM; no species/name/encounter data tables or art are redistributed.
- Synthetic decoder tests and an art-free Compose UI preview path.

The persistent EXPERIMENTAL notice is intentional. Basic gameplay, natural Catch success on both
tables, timeout and collection updates are checked headlessly with a user-supplied
ROM. Evolution and bonus-stage guidance still need real-mode gameplay checks.

## Verification status

Cloud verification on 2026-10-15:

- 30 synthetic `PinballReaderTest` cases pass, including exact identity, menu/demo
  exclusion, invalid/short reads, mode/substate boundaries, Catch/Evolution
  progression, timers, saver, extra-life representation and stale-cache cleanup.
- All 3 `SiteDataExportTest` cases pass; the app and website agree on support.
- All 26 website/manual tests pass, all translation entries pass validation,
  and the website builds 29 static pages. Nothing was deployed.
- The actual Compose UI compiles against the existing Desktop Android shims.
  At checkpoint `48991a3`, all 30 static render/assertion cases passed: Catch, Evolution, free play, menu
  and unavailable states at 1240x1080, 1280x960 and 1920x1080 in light/dark mode.
  Two repeated Back-navigation cycles, two repeated sampling/tab cycles and
  the shared Settings-to-States-to-Back check also pass. Screenshot inputs are
  synthetic; they are not evidence of real Catch/Evolution gameplay.
- The exact user-supplied ROM runs in pinned mGBA `26b7884`. The production
  Kotlin reader passes 304 assertions against 43 freshly reproduced private
  real-RAM captures:
  both tables' boot/title/menu/selection, launch, ordinary play, pause and
  resume, score, balls, area, saver countdown/return/expiry, ball loss, game-over,
  demo exclusion and state reload. Both reloads restored byte-identical EWRAM.
  No memory injection was used.
  Pause froze the saver at 3,418 ticks across 120 frames, then resume advanced
  it to 3,386. The original game screens were privately inspected.

Additional cloud checks on 2026-10-15:

- 27 collection decoder tests pass, including independent arrow pools, shared
  versus caught flags, actual party eligibility, branching outcomes and guards.
- Collection UI passes 24 light-theme render/assertion cases across the same
  three display sizes, plus eight targeted dark cases and a French short-display
  long-list check. Pointer clicks exercise pool/filter/tab changes, branching
  outcomes, scrolling and choice retention across simulated telemetry and
  menu-to-play updates. These are Desktop UI checks, not Android pause actions.
  Two additional CAPTURED previews pass using recorded natural Ruby Catch RAM:
  the area catalog and actual-party evolution planner. The latter is not evidence
  of successful Evolution gameplay. Final representative images were inspected
  for readable controls, status contrast and persistent experimental disclosure.
- Both natural Catch runs and a timeout replay byte-identically in EWRAM and
  IWRAM from normal button scripts. Production `PinballReader` passes 554
  assertions across 57 new captures. See [mode evidence](PINBALL_MODES_VALIDATION.md).
- Production collection decoding passes 375 portable assertions across all 57
  captures, expanding an earlier overlapping 298-check private set. It checks
  seen-to-caught registration, party updates, pause, pool exclusions and timeout
  that stays seen-only. All 224 encounter slots and 205 species records
  independently match pinned source and the private exact ROM. See
  [collection mapping and evidence](PINBALL_COLLECTION.md).

The collection slice and side-by-side Android debug APK built successfully from
a frozen private source snapshot based on `48991a3`. All 57 Android JVM tests
in the two Pinball decoder classes pass. Its debug signature, isolated package
and provider metadata, ZIP integrity/alignment and arm64 native libraries were
verified. No ROM or save is bundled. No Android hardware/emulator run or
performance measurement has been completed. Cloud Desktop renders and a
compiled APK do not establish Android runtime integration.

The complete 8 MiB ROM was also compared through the pinned native mGBA
`busRead32` API at fresh boot and after loading a natural Catch state: both
comparisons had zero byte differences, including `0xC4..0xC9` without masking.
This checks the native read path used for identity, not the Android JNI bridge.

Committed unit tests are authored byte arrays, not captured ROM/RAM/save data.
The optional private capture checker reads user-owned files outside the repo.
Default Compose previews are synthetic UI scenarios, not screenshots of gameplay.
The separate opt-in CAPTURED collection previews decode private recorded RAM;
they are Desktop renders of real state, not live Android screenshots.
No game art, ROM, save-state or copyrighted ROM dump is added to this repository.

### Reproduction and remaining release checks

[Private runtime reproduction](../native-capture/boot/PINBALL.md) documents the
pinned-core build and ordinary-button input scripts. `scripts/capture_pinball.py`
requires a user-owned exact ROM and a fresh private output directory outside the
repository. This command verifies identity and runs the production decoder:

```sh
gradle -p ui-preview :data:pinballCapture \
  -Prom=/private/game.gba -Pcaptures=/private/captures
```

Only redacted field expectations and input scripts are committed.

Still required: Evolution item three/center-hole phases, extra balls, bonus
stages and rewind.
The demo's Catch-like memory establishes demo exclusion, not Catch guidance.
Test the complete companion on Android, including state load, lifecycle and
real-time readability, before treating this as a release. A headless mGBA state
reload does not establish the Android application's state-load integration.
A different ROM needs a separately evidenced profile.

## Primary-source mapping

Research reference: [pret/pokepinballrs at 33ad42e](https://github.com/pret/pokepinballrs/tree/33ad42e8f2afca891831f03179820b96a3fdef6a).
Offsets and enum meanings were independently translated into a small read-only
Kotlin decoder. No decompilation source or assets are redistributed here.

| Source | Meaning used |
| --- | --- |
| `README.md`, `Makefile` | Whole-ROM SHA-1, BPPE, revision 0 |
| `sym_ewram.txt`, `include/main.h` | `gMain=0x0200B0C0`, current-state pointer `0x020314E0` |
| `include/global.h` | Game state `0x02000000`, field layouts |
| `include/constants/global.h` | Main state 2 is gameplay; state 3 is demo |
| `include/constants/fields.h` | Ruby/Sapphire 0/1; bonus boards 2..7 |
| `include/constants/board/main_board.h` | Board modes, substates and transition phase |
| `src/all_board_process8.c` | Event timer mode and floor(ticks/60) display |
| `src/ruby_board_indicators.c`, `src/all_board_process2.c` | Saver counter, flashing lamp and freeze behavior |
| `src/main_board_bumpers.c`, `src/ruby_process3_entities_2.c` | Reveal accumulator 0,1,3,6 means 0,1,2,3 bumper hits |
| `src/all_board_process6_collision.c` | Three creature hits complete capture |
| `src/main_board_catch_normal_and_jirachi_modes.c` | Catch preparation, appearance and shared timeout/success ending |
| `src/main_board_evolution_mode.c` | Item counter resets after the third item; substates 3/4 require the center hole |
| `src/ruby_catch_holes.c`, `src/sapphire_process3.c` | Two Catch arrows ready; Evolution needs three arrows and an eligible party |
| `src/all_board_pinball_game_main.c`, `src/all_board_mode_change_and_debug_menu.c` | `numLives` is reserve balls, not a ball ordinal |
| `include/constants/areas.h`, `include/constants/score.h` | Area names, low/high score base 100,000,000 |

The live reader makes three small reads (main metadata, state pointer and a
1,840-byte state prefix). Collection adds six reads totaling 7,017 bytes, including
the ROM metadata and current area pools, and keeps no cross-ROM cache. Both run
on the emulator thread. They use the existing approximately
once-per-second telemetry cadence. Timers are snapshots of game time, not a
wall-clock animation or an exact last-moment safety guarantee. Saver seconds
round up so an active subsecond saver is not shown as OFF. Pause and held-ball
sequences can freeze the game's counters. No stale cached Pinball snapshot is
restored at launch.

## Safe Android test packaging

The fork's debug variant uses application ID `com.pokedaisy.app.pinball.debug`,
label `PokeDaisy Pinball (Debug)` and version suffix `-pinball-debug`. Namespace,
activity classes and JNI names remain unchanged. Release packaging is unchanged.
The provider authority follows `${applicationId}.updates`; default app-private
saves/settings are isolated. In-app update checks are disabled for this debug
build; existing package/signer checks remain in place for release builds.

The debug build also prunes unused Material icon implementations with a narrowly
scoped R8 rule. Optimization and renaming are disabled; non-icon source classes
and members are retained. Compiler-generated lambda wrappers may differ from
the unshrunk D8 output. Named warning exceptions cover only optional JVM adapter
classes that were already absent from the unshrunk APK. This does not add support
for those optional archive formats. Native libraries are losslessly compressed
in the debug APK and extracted by Android at installation; release packaging is
unchanged. Android runtime validation is still required.

This does **not** isolate a user-selected shared save folder. Use copied test
ROMs/saves and do not point both apps at the same writable saves. Never uninstall
the existing app to make a test APK fit. The verified collection debug APK is
ARM64-only and requires Android 8 or newer. The initial baseline APK from
`48991a3` uses upstream's application ID and is retained only as private build
evidence; user installation should use the separately packaged collection build.
`make run`, `scripts/smoke_test.sh` and `scripts/capture_fixture.sh` default to
the isolated debug package and use fully qualified activity classes. A `PKG`
override is available for deliberate upstream testing. Their targets were checked
statically without running adb. The RPG fixture workflow is not the private
Pinball capture workflow; do not add Pinball RAM or saves to the repository.

## Next slices, deliberately not claimed as implemented

1. Extend real-game validation to Evolution, extra balls, bonus stages and
   rewind, then verify the integrated companion on Android.
2. Illustrated table rules reconstructed from the player's ROM, with an
   on-demand explanation rather than distracting real-time overlays.
3. Named practice situations using existing save states, rewind and speed.
   Record assists explicitly before introducing separate clean/practice results.
4. Post-run breakdown, based on reliable run boundaries and counters.

Full-table rendering and roguelike modifiers remain experiments. This change
implements neither, and contains no new leaderboard or clean-run claims.
