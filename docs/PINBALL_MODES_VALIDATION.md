# Natural Catch runtime evidence

2026-10-15. Exact user-supplied English BPPE revision 0, SHA-1
`9fec81ce2c5df589e0371a0bf2f92a5fe8db730b`, and pinned mGBA
`26b7884bc25a5933960f3cdcd98bac1ae14d42e2`.

## Result

57 new private RAM/screen/state captures reproduce byte-identical full EWRAM and
IWRAM when the compressed input scripts are replayed through the unchanged
`mgba_dump`. Both table scripts start from a fresh boot with no existing save.
The pause and timeout scripts branch only from actual states created by the Ruby
script. The exploration probe added read-only telemetry; the final reproduction
uses the unmodified capture executable. No RAM/ROM injection, cheats or debug
progression were used, and normal ball physics were preserved.

- Ruby natural Catch: accumulator 0, 1, 3, 6; hits 0, 1, 2, 3; capture animation;
  successful capture and party addition.
- Sapphire natural Catch: the same sequence, including a live accumulator-6
  reveal frame and saver-return interruptions.
- Ruby catch pause: event timer 6,704 and saver 5,137 ticks are unchanged across
  120 paused frames. Resume advances them; progress is suppressed while paused.
- Ruby timeout: 120, 119, 61, 60, 59, 2 and 1 ticks; zero/cleanup; return to free
  play. The seen-only flag remains 1, caught count stays 0, and party stays empty.
- Successful Ruby capture: species index 81, MAGNEMITE, flag 1 to 4 at frame
  7,360; run caught count and party size 0 to 1.
- Successful Sapphire capture: species index 16, CASCOON, flag 1 to 4 at frame
  10,911; run caught count and party size 0 to 1.

Screens were privately inspected for both bumper/capture sequences, the natural
catches, pause menu, countdown and timeout. `timerSeconds` uses the source's
floor(ticks/60). The rendered game HUD can lag the sampled RAM by one emulated
frame at an exact boundary: raw 59 ticks still had the preceding 0:01 picture;
raw 2 ticks visibly showed 0:00. This is not a wall-clock timer guarantee.
The first paused-frame capture similarly precedes the fully drawn pause menu;
the later paused capture shows it. Numeric metadata is the authority at that
sampling boundary.

## Reproducible inputs and assertions

The four `native-capture/boot/pinball-catch-*.txt` input templates and
`pinball-catch-expected.tsv` contain only ordinary button inputs and redacted
field expectations. `pinball-collection-expected.tsv` adds the collection
expectations. `scripts/capture_pinball_modes.py` uses the same
private-output and exact-ROM-identity safeguards as `capture_pinball.py`.
The production mode reader passed all 554 assertions across these 57 samples.
The production collection reader passed another 375 assertions, including
seen-to-caught status, the new evolution candidate, pause behavior, encounter
pools, previous-target exclusions and seen-only timeout.
The integrated capture wrapper was also independently rerun and all 57 full
EWRAM and IWRAM samples matched byte-for-byte.

The original 43-sample, 304-assertion basic-play workflow remains unchanged.
See the [capture suites](../native-capture/PINBALL.md) and the
[pinned-core build](../native-capture/boot/PINBALL.md).

After the pinned-core build, use a new private output directory:

```sh
python3 scripts/capture_pinball_modes.py \
  --emulator "$PRIVATE/mgba_dump" --rom "$PRIVATE/pinball.gba" \
  --output "$PRIVATE/catch-captures"
gradle -p ui-preview :data:pinballCapture \
  -Prom="$PRIVATE/pinball.gba" -Pcaptures="$PRIVATE/catch-captures"
```

The screenshot, ROM, saved-state, EWRAM and IWRAM artifacts stay private and
outside the repository. This report and text inputs do not grant art publication.

## Still unverified

Three bounded ordinary-input continuations from the real caught state did not
reach Evolution mode. Each was limited to 5,000 controller decisions (usually six emulated frames
each), ending earlier at game-over; one reached a single Evolution arrow. Two continuations
ended in a roulette or shop interaction that the exploratory controller policy
did not complete. These limits describe the coverage attempt, not a game bug. Its item collection, third-item reset, center-hole finish,
and completed evolution remain source-mapped/synthetic, not natural-gameplay
validated. Extra balls, playable bonus-stage objectives, rewind, Android/JNI,
APK/device integration, readability in live play and performance remain outside
this capture suite. This is not a complete end-to-end game or Android test.
