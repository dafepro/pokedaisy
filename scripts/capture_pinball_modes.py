#!/usr/bin/env python3
"""Reproduce natural Catch gameplay privately with the exact user-owned BPPE rev0 ROM.

Run only against the pinned native-capture/mgba_dump core. All progression uses
ordinary controller input, with branches from the states those inputs produced.
No RAM/ROM writes, cheats, existing user saves or debug progression are used.
"""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess

REPO = Path(__file__).resolve().parents[1]
BOOT = REPO / 'native-capture' / 'boot'
SHA1 = '9fec81ce2c5df589e0371a0bf2f92a5fe8db730b'
SCRIPTS = ('ruby', 'sapphire', 'pause', 'timeout')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--emulator', required=True, type=Path)
    parser.add_argument('--rom', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    emulator, rom, output = (p.resolve() for p in (args.emulator, args.rom, args.output))
    if any(p == REPO or REPO in p.parents for p in (rom, output)):
        parser.error('The ROM and generated captures must remain outside the repository.')
    if any(c.isspace() for c in str(output)) or len(str(output)) > 180:
        parser.error('Use a private output path without whitespace, shorter than 181 characters.')
    if output.exists():
        parser.error('Choose a new output directory. Existing captures are never overwritten.')
    if not emulator.is_file() or not rom.is_file():
        parser.error('The emulator and exact user-owned ROM must exist.')
    if hashlib.sha1(rom.read_bytes()).hexdigest() != SHA1:
        parser.error('Requires the unmodified English BPPE revision-0 ROM.')
    output.mkdir(parents=True)
    for name in SCRIPTS:
        filename = f'pinball-catch-{name}.txt'
        commands = (BOOT / filename).read_text().replace('@OUT@', str(output))
        for line in commands.splitlines():
            if line.startswith('dump '):
                Path(line[5:]).mkdir()
        (output / filename).write_text(commands)
        with (output / f'{name}-run.log').open('w') as log:
            subprocess.run([str(emulator), str(rom)], input=commands, text=True,
                           stdout=log, stderr=log, check=True)
    captures = list(output.glob('*/ewram.bin'))
    if len(captures) != 57:
        raise RuntimeError(f'Expected 57 captures, found {len(captures)}')
    for capture in captures:
        if capture.stat().st_size != 0x40000 or (capture.parent / 'iwram.bin').stat().st_size != 0x8000:
            raise RuntimeError(f'Incomplete capture: {capture.parent.name}')
    shutil.copyfile(BOOT / 'pinball-catch-expected.tsv', output / 'expected.tsv')
    shutil.copyfile(BOOT / 'pinball-collection-expected.tsv', output / 'collection-expected.tsv')
    print('Captured 57 natural-gameplay samples privately. No RAM/ROM injection was used.')
    print('Run :data:pinballCapture against this output; expect 554 mode and 375 collection assertions.')
    print('Keep the ROM, RAM, states and game screenshots private, outside the repository.')


if __name__ == '__main__':
    main()
