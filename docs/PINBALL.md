# Pinball modernization: first slice

Based on PokeDaisy by Lidor Itzhari - https://github.com/lidor30/pokedaisy

This fork starts a faithful Pokémon Pinball: Ruby & Sapphire companion. The
original ROM runs in the existing mGBA core. The companion only reads memory;
it does not replace physics, draw over the ball, inject inputs, or change saves.

## Implemented

- Exact unmodified English `BPPE`, revision `0`, SHA-1
  `9fec81ce2c5df589e0371a0bf2f92a5fe8db730b` detection. Another region, revision,
  patch or same-header ROM is rejected. The raw SHA-1 includes bytes at `0xC4`;
  the RPG reader's GPIO masking must not be used for Pinball.
- A dedicated `PINBALL` companion tab, beside existing `STATES` and `SETTINGS`
  (and achievements when available). It does not inherit irrelevant RPG tabs.
- Current table, mode, one next-action hint, mode timer, mode progress, ball
  saver, balls left, displayed score and current area.
- Catch reveal/capture and Evolution phases; simple normal-play readiness,
  launch, travel, hatch and special-encounter guidance. Bonus stages have a
  generic game-screen prompt, never the main table's reused mode meanings.
- Menu/demo, initialization, pause, transitions, game-over and failed reads
  suppress stale coaching. A timed-out mode is never declared successful.
- Synthetic decoder tests and an art-free Compose UI preview path.

The persistent EXPERIMENTAL notice is intentional. Basic gameplay is checked
headlessly with a user-supplied ROM. Advanced-mode guidance is source-mapped
and synthetically tested; it has not yet been verified in real gameplay.

## Verification status

Cloud verification on 2026-10-15:

- 30 synthetic `PinballReaderTest` cases pass, including exact identity, menu/demo
  exclusion, invalid/short reads, mode/substate boundaries, Catch/Evolution
  progression, timers, saver, extra-life representation and stale-cache cleanup.
- All 3 `SiteDataExportTest` cases pass; the app and website agree on support.
- All 26 website/manual tests pass, all translation entries pass validation,
  and the website builds 29 static pages. Nothing was deployed.
- The actual Compose UI compiles against the existing Desktop Android shims.
  All 30 static render/assertion cases pass: Catch, Evolution, free play, menu
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

No Android APK build, Android hardware test or performance measurement has
been completed. Cloud Desktop renders do not establish Android/JNI integration.

Committed unit tests are authored byte arrays, not captured ROM/RAM/save data.
The optional private capture checker reads user-owned files outside the repo.
Compose preview images are synthetic UI scenarios, not screenshots of gameplay.
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

Still required: real Catch bumper/hit phases, Evolution item three/center-hole
phases, event-timer timeout boundaries, extra balls, bonus stages and rewind.
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

The reader makes three small reads (main metadata, state pointer and 1,840-byte
state prefix), on the emulator thread. It uses the existing approximately
once-per-second telemetry cadence. Timers are snapshots of game time, not a
wall-clock animation or an exact last-moment safety guarantee. Saver seconds
round up so an active subsecond saver is not shown as OFF. Pause and held-ball
sequences can freeze the game's counters. No stale cached Pinball snapshot is
restored at launch.

## Next slices, deliberately not claimed as implemented

1. Extend real-game validation to Catch, Evolution, timeouts, extra balls, bonus
   stages and rewind, then verify the integrated companion on Android.
2. Current-area missing catches and Evolution planning, using the game's actual
   encounter/eligibility rules; never predict a not-yet-selected encounter.
3. Illustrated table rules reconstructed from the player's ROM, with an
   on-demand explanation rather than distracting real-time overlays.
4. Named practice situations using existing save states, rewind and speed.
   Record assists explicitly before introducing separate clean/practice results.
5. Post-run breakdown, based on reliable run boundaries and counters.

Full-table rendering and roguelike modifiers remain experiments. This change
implements neither, and contains no new leaderboard or clean-run claims.
