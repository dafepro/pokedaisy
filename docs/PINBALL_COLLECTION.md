# Pinball collection: source mapping and private reproduction

This slice adds an on-demand, read-only area checklist and evolution planner for
one exact unmodified English `BPPE` revision-0 ROM, SHA-1
`9fec81ce2c5df589e0371a0bf2f92a5fe8db730b`. The caller verifies this identity before
using either Pinball reader. A matching cartridge header alone is insufficient.

The collection is a catalog of **regular area possibilities**. Two Catch arrows
and three Catch arrows use separate pools. Three arrows can replace a species,
not merely add rarer species to the same pool. The checklist never predicts the
next randomly selected target and does not display catch probabilities.

Special encounters, e-Reader guest encounters, Hatch mode and bonus-stage catches
are outside this area catalog. A special-guest or forced-special flag is surfaced
as a warning, not treated as an ordinary area encounter. A rare special encounter
can also happen without those flags, so their absence is not a completeness claim.

## Primary source and exact-ROM checks

Reference: [pret/pokepinballrs at 33ad42e8f2afca891831f03179820b96a3fdef6a](https://github.com/pret/pokepinballrs/tree/33ad42e8f2afca891831f03179820b96a3fdef6a).
This is an independently written decoder. No decompilation source, ROM-derived
name table, encounter table, game art, RAM or save data is bundled.

| Reference | Mapping |
| --- | --- |
| `include/main.h`, `sym_ewram.txt` | `gMain=0x0200B0C0`; 205 saved Dex flag bytes begin at `gMain+0x74` |
| `include/variables.h`, `src/pokedex.c` | Flags 0 unseen, 1 seen, 2 shared, 3 shared and seen, 4 caught; only 4 counts as caught |
| `data/mon_locations.inc` | Regular wild pools at `0x08055A84`: 14 areas, two arrow variants, eight little-endian species IDs per variant; 205 is the empty sentinel |
| `src/data.c`, `src/data/species.h`, `include/types.h` | Species records at `0x086A3700`: 205 records, 24-byte stride; English name at `+7` for 10 bytes, evolution method at `+0x14`, immediate target at `+0x15` |
| `include/constants/species.h` | Pinball has its own zero-based IDs; ID 0 is a real species, not the RPG empty species |
| `include/constants/areas.h`, `data/rom_1.s` | Area index and valid table/area pairing |
| `include/global.h` | Run-party IDs at state `+0x270`, size at `+0x281`, previous Catch target at `+0x59C`, run catch count at `+0x5F0` |
| `src/main_board_catch_hatch_picker.c` | First-catch and previous-target exclusions; actual run-party registration and special evolution outcomes |
| `src/all_board_state_transitions_and_idle.c` | Current arrow lamps are cleared/stored during activity modes; report current readiness only in ordinary play |

All 224 encounter slots and all 205 names, evolution methods and evolution targets
were compared with the pinned source using the exact private ROM. The 24-byte
record stride was verified against the bytes, including two trailing padding
bytes after the named fields. These tables are read from the player's own ROM at
runtime, not copied into app assets or generated Kotlin tables.

### Catch conditions

The regular-pool picker gives no weight to the previous Catch target. It also
excludes rare species and species with no evolution before the run's first catch.
The rare-species exception includes some evolving species, so an evolution target
alone is insufficient to decide eligibility. These conditions are shown per row;
the species remains in the area checklist so its requirements remain visible.

All valid ordinary Dex states have positive base weights. Missing evolutions and
e-Reader bonuses can change those weights; the app does not turn them into odds.
A row can be in the two-arrow pool, the three-arrow pool, or both. The checklist
shows that membership independently of current arrow readiness.

The previous-target exclusion concerns the **next regular selection**. During an
active Catch mode the game has already replaced its previous-target field with
the selected species. Therefore this flag is not a claim that the current target
was ineligible when it was selected.

### Evolution conditions

Only actual members of the run's 16-slot evolvable party are listed. A species
being caught in the saved Dex does not make it a current run-party member.
Duplicate party members retain their individual slots. Targets represent one
evolution step, with caught/missing state read independently for each target.

- Ordinary species use the immediate target from the ROM record.
- Gloom's immediate outcome is table-dependent.
- Wurmple and Clamperl have one-of-two outcomes. The game favors an uncaught form;
  the planner lists both possibilities without guessing the frame-dependent choice.
- Nincada registers both listed forms when the evolution succeeds.

These special cases come from `RegisterCaptureOrEvolution`, not RPG evolution
rules. Their model behavior is synthetically tested. Completing these branching
Evolution scenarios in real gameplay remains a separate validation task.

## Sampling and stale-state handling

`PinballCollectionReader.read(MemoryReader)` is called between emulated frames,
using the existing sampler. The separate `COLLECT` screen is the on-demand view;
Compose does not read emulated memory. The basic `PinballReader` API is unchanged.

A complete collection read is six bounded reads totaling 7,017 bytes: 16 bytes of
main metadata, four pointer bytes, the existing 1,840-byte state prefix, 205 Dex
bytes, 4,920 ROM species bytes and the current area's 32 pool bytes. The reader has
no cross-sample or cross-ROM cache. This is not a measured performance guarantee.

Menus, demo playback, launch before area choice, bonus stages, banners, resets,
ball-end sequences and mode transitions return empty collection rows. Pause keeps
the stable catalog available to browse. Unknown pointers, areas, Dex flags,
species metadata, party members and incomplete reads fail closed with empty rows.

## Reproduce the collection checks

Follow the [private core build instructions](../native-capture/boot/PINBALL.md),
then run the [natural Catch capture suite](../native-capture/PINBALL.md). Keep every
ROM, generated capture, save state and game screenshot outside this repository.
Here `$PRIVATE` is your private working directory, and the capture output must be
new. Use the pinned core and exact ROM; no emulator memory writes are necessary.

```sh
python3 scripts/capture_pinball_modes.py \
  --emulator "$PRIVATE/mgba_dump" --rom "$PRIVATE/pinball.gba" \
  --output "$PRIVATE/catch-captures"
gradle -p ui-preview :data:pinballCapture \
  -Prom="$PRIVATE/pinball.gba" -Pcaptures="$PRIVATE/catch-captures"
```

The capture script copies these public-safe assertion templates into the private
capture directory:

- `native-capture/boot/pinball-catch-expected.tsv` becomes `expected.tsv`.
- `native-capture/boot/pinball-collection-expected.tsv` becomes `collection-expected.tsv`.

For captures made before the collection template was added, copy the second file
manually into the private output as `collection-expected.tsv`. It requires all 57
named natural Catch samples, including the pause and timeout branches.

The production checker verifies the exact ROM and complete 256 KiB EWRAM files,
then runs both real readers with ROM and RAM reads correctly routed. Expected:

```text
PASS: 554 real-capture assertions across 57 samples
PASS: 375 collection assertions across 57 samples
```

The separate original basic suite still uses its existing `expected.tsv` and
must report 304 assertions across 43 samples. Collection fields do not rename or
replace any original mode-reader fields or expectations.

The new 375 portable assertions expand the earlier private 298-assertion check
across 52 captures. They include all original Catch selection/registration and
timeout cases, plus five pause/resume captures and explicit two/three-arrow pool
membership and first-catch exclusion checks. The totals describe different
assertion sets and must not be added as independent coverage.

### Checker field format

Both expected files use three tab-separated columns: capture folder, field key,
and exact expected value. Empty values are valid, and null is the literal `null`.
Per-entry fields are always available to assertions. Add
`-PcollectionDetails=true` to print them as well as the concise summary.

Summary fields include `collection.phase`, `collection.table`, `collection.area`,
`collection.catchCount`, `collection.catchIds`, `collection.missingCount`,
`collection.partySize`, `collection.partyIds`, `collection.empty`,
`collection.catchArrows`, `collection.evolutionReady`, and
`collection.specialEncounterPending`. Catch IDs are sorted numerically in this
machine-readable summary; this does not change UI ordering.

Per-Catch keys use `collection.catch.<Pinball species ID>.` followed by `name`,
`status`, `caught`, `twoArrows`, `threeArrows`, `firstCatchRequired`, or
`excludedPreviousTarget`. Evolution keys use
`collection.evolution.<zero-based party slot>.` followed by `species`, `name`,
`outcome`, or `targets`. Each target adds
`target.<Pinball species ID>.name`, `.status`, and `.caught`. Target IDs are
comma-separated for one-of or both-form outcomes.

## Verification limits

- All 27 collection decoder unit tests use authored synthetic bytes.
- Real controller-only captures verify saved seen-to-caught changes on both
  tables, actual run-party additions, timeout without a catch, pause browsing,
  and suppression during menus/transitions.
- The whole ROM catalog matches the pinned source, but this is not an actual
  gameplay capture of every species, area, special flag, or evolution branch.
- Android/JNI integration, hardware usability and performance need their own
  validation. Desktop compilation and headless captures do not establish them.
- No changes to physics, input injection, saves, RNG, ROM data or game outcomes
  are performed by the companion readers.
