# Private Pinball capture suites

Both suites require the player's exact unmodified English BPPE revision-0 ROM
and the repository's pinned mGBA core. Follow the
[build and private-output instructions](boot/PINBALL.md). Never put a ROM,
RAM dump, save state or game screenshot into the public repository.

## Basic gameplay

The original workflow is unchanged:

```sh
python3 scripts/capture_pinball.py \
  --emulator "$PRIVATE/mgba_dump" --rom "$PRIVATE/pinball.gba" \
  --output "$PRIVATE/basic-captures"
gradle -p ui-preview :data:pinballCapture \
  -Prom="$PRIVATE/pinball.gba" -Pcaptures="$PRIVATE/basic-captures"
```

Expected: **304 assertions across 43 samples**. This covers both tables' basic
lifecycle, saver, ball loss, game-over, demo suppression and state restoration.

## Natural Catch gameplay

The separate suite uses fresh controller-only boots on both tables, followed
by branches from the Ruby states it produced:

```sh
python3 scripts/capture_pinball_modes.py \
  --emulator "$PRIVATE/mgba_dump" --rom "$PRIVATE/pinball.gba" \
  --output "$PRIVATE/catch-captures"
gradle -p ui-preview :data:pinballCapture \
  -Prom="$PRIVATE/pinball.gba" -Pcaptures="$PRIVATE/catch-captures"
```

Expected: **554 mode assertions and 375 collection assertions across 57 samples**. Both catches progress through
bumpers and creature hits, update the seen flag to caught, and add an evolution
candidate to the run party. Ruby branches cover pause/resume and timer expiry
without a catch. The two suites total 858 mode-reader assertions and 375 collection-reader
assertions across 100 samples (1,233 assertions overall). They are separate output directories and checker invocations.

`pinball-catch-ruby.txt` and `pinball-catch-sapphire.txt` are fresh-boot inputs;
`pinball-catch-pause.txt` and `pinball-catch-timeout.txt` load only states created
by the Ruby input. All four scripts use ordinary controls, without memory
writes, cheats or debug progression. The unchanged `mgba_dump` reproduced all
57 samples with byte-identical full EWRAM and IWRAM.

The game's rendered HUD can lag the sampled RAM by one emulated frame at a
countdown boundary. Assertions use the mapped floor(ticks/60), not a wall-clock
promise. See the [detailed results and remaining limits](../docs/PINBALL_MODES_VALIDATION.md).
Evolution gameplay, bonus objectives, extra balls and Android/JNI still need
separate validation. These tests do not establish hardware readiness.
