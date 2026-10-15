# Private Pinball runtime reproduction

This script reproduces the **limited headless validation**, using the user's own
exact English BPPE revision-0 ROM. It does not provide a ROM or captured fixtures.
Keep the ROM, state files, RAM and screenshots outside the repository and private.
The generated command scripts use ordinary controller input, plus reloads of
states produced by that same input; there are no memory writes or cheats.

The reference run used mGBA `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`, the
repository's pinned core, built with the Android project's core feature flags.
Use an installed C compiler, CMake and Python 3. Here `$PRIVATE` is a new private
working directory outside this checkout. Its path must contain no whitespace.

```sh
git submodule update --init third_party/mgba
cmake -S third_party/mgba -B "$PRIVATE/mgba-build" \
  -DCMAKE_BUILD_TYPE=Release -DBUILD_STATIC=ON -DBUILD_SHARED=OFF \
  -DDISABLE_FRONTENDS=ON -DDISABLE_DEPS=ON -DSKIP_LIBRARY=OFF \
  -DBUILD_GL=OFF -DBUILD_GLES2=OFF -DBUILD_GLES3=OFF \
  -DBUILD_PERF=OFF -DBUILD_TEST=OFF -DBUILD_SUITE=OFF \
  -DBUILD_LIBRETRO=OFF -DBUILD_PYTHON=OFF -DBUILD_LTO=OFF \
  -DUSE_DEBUGGERS=OFF -DUSE_GDB_STUB=OFF -DUSE_EDITLINE=OFF \
  -DUSE_FFMPEG=OFF -DUSE_DISCORD_RPC=OFF -DUSE_LZMA=OFF \
  -DUSE_LUA=OFF -DUSE_ELF=OFF -DUSE_SQLITE3=OFF \
  -DUSE_LIBZIP=OFF -DUSE_MINIZIP=OFF -DUSE_PNG=OFF -DUSE_ZLIB=OFF \
  -DM_CORE_GBA=ON -DM_CORE_GB=ON
cmake --build "$PRIVATE/mgba-build" --parallel
cc -O2 -DUSE_PTHREADS -DM_CORE_GBA -DM_CORE_GB \
  -Ithird_party/mgba/include -I"$PRIVATE/mgba-build/include" \
  native-capture/mgba_dump.c "$PRIVATE/mgba-build/libmgba.a" \
  -lm -lpthread -ldl -o "$PRIVATE/mgba_dump"
python3 scripts/capture_pinball.py \
  --emulator "$PRIVATE/mgba_dump" --rom "$PRIVATE/pinball.gba" \
  --output "$PRIVATE/captures"
```

The output directory must not exist. The capture script checks the exact ROM
SHA-1 and refuses repository-local inputs or output, then generates 43 named
samples plus `expected.tsv`. The TSV contains only redacted field expectations
from source mappings and privately inspected real screenshots/RAM. It contains
no ROM, memory bytes, save files, assets or decompilation code.

Run the production Kotlin decoder on those captures using the UI preview's
`:data:pinballCapture` task, with `-Prom` and `-Pcaptures` pointing to the private
ROM and capture directory. See [the preview instructions](../../ui-preview/README.md).
A decoder run must report **304 assertions across 43 samples**. The capture script
also requires both reloads to restore byte-identical full EWRAM.

## Covered by this sequence

- Boot, title menu, table-selection menu and attract-demo suppression
- Ruby and Sapphire launch, free play, score 500 and area selection
- Real paused screens and saver counters frozen over 120 emulated frames
- Resume, saver return, subsecond saver rounding and saver expiry
- Ball-end transitions; reserve count 2 → 1 → 0; game-over suppression
- Restoring an earlier real state after later gameplay

The demo happens to contain Catch-like data. It validates exclusion of demo
play; it **does not validate live Catch guidance**. Catch, Evolution, event timer
boundaries, extra balls, bonus stages, rewind, Android/JNI rendering, hardware
behavior, latency and performance remain unverified by this sequence. The
private mGBA save-state reload is not an Android application state-load test.

`press` consumes two frames. `hold KEYS N` consumes N+1 frames. Timing is pinned
to this emulator/ROM combination, with a fresh session and no prior save file.
The script never passes an existing user save file to the emulator.
