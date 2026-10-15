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

The persistent EXPERIMENTAL notice is intentional. Source interpretation and
synthetic fixtures are not live gameplay validation.

## Verification status

At the initial source checkpoint, cloud compilation and synthetic tests are
being run. No Android APK build, Android hardware test, live Pinball ROM run,
performance measurement or real-RAM capture has been completed. No ROM was
available. See subsequent commits for completed cloud verification results.

Tests are authored byte arrays, not captured ROM/RAM/save data. Preview images
are synthetic UI scenarios, not screenshots of gameplay. No game art, ROM,
save-state or copyrighted ROM dump is added to this repository.

### Required real-game validation

Use a legally obtained user-supplied ROM matching the exact SHA-1 above. Confirm
Ruby and Sapphire launch, ordinary play, Catch (bumper and hit phases), Evolution
(item 3 and center hole), timeout, pause, ball saver, extra ball, bonus stage,
menu return, state load and rewind. Compare the panel against the game screen
and raw memory captured between frames. Test on Android before treating this as
a release. A different ROM needs a separately evidenced profile.

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

1. Validate and refine this slice with a user-supplied ROM and real captures.
2. Current-area missing catches and Evolution planning, using the game's actual
   encounter/eligibility rules; never predict a not-yet-selected encounter.
3. Illustrated table rules reconstructed from the player's ROM, with an
   on-demand explanation rather than distracting real-time overlays.
4. Named practice situations using existing save states, rewind and speed.
   Record assists explicitly before introducing separate clean/practice results.
5. Post-run breakdown, based on reliable run boundaries and counters.

Full-table rendering and roguelike modifiers remain experiments. This change
implements neither, and contains no new leaderboard or clean-run claims.
