#!/usr/bin/env python3
"""Reproduce real Pinball captures privately; never accepts or writes game data in the repo.

Requires native-capture/mgba_dump.c compiled against pinned mGBA 26b7884.
This only presses buttons and reloads states produced by those inputs. It does
not patch ROM/RAM, apply cheats, or exercise Android/JNI. The generated screenshots
are private copyrighted game output, not distributable test fixtures.
"""
import argparse
import hashlib
import shutil
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
BOOT = REPO / 'native-capture' / 'boot'
SHA1 = '9fec81ce2c5df589e0371a0bf2f92a5fe8db730b'


def outside_repo(path):
    return path != REPO and REPO not in path.parents


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--emulator', type=Path, required=True, help='Built mgba_dump executable')
    parser.add_argument('--rom', type=Path, required=True, help='Private legally obtained BPPE rev0 ROM')
    parser.add_argument('--output', type=Path, required=True, help='New private directory outside the repository')
    args = parser.parse_args()
    rom, output, emulator = (p.resolve() for p in (args.rom, args.output, args.emulator))
    if not outside_repo(rom) or not outside_repo(output):
        parser.error('ROM and capture output must remain outside the repository.')
    if any(c.isspace() for c in str(output)) or len(str(output)) > 180:
        parser.error('mgba_dump requires output paths without whitespace; keep the root under 180 characters.')
    if output.exists():
        parser.error('Choose a new output directory; existing captures are never overwritten.')
    with rom.open('rb') as source:
        digest = hashlib.sha1(source.read()).hexdigest()
    if digest != SHA1:
        parser.error('Unsupported ROM identity. Requires exact unmodified English BPPE revision 0.')
    if not emulator.is_file():
        parser.error('Build native-capture/mgba_dump.c first and pass its executable.')
    output.mkdir(parents=True)
    for table in ('ruby', 'sapphire'):
        commands = (BOOT / f'pinball-{table}.txt').read_text().replace('@OUT@', str(output))
        for line in commands.splitlines():
            if line.startswith('dump '):
                Path(line[5:]).mkdir()
        script = output / f'{table}-commands.txt'
        script.write_text(commands)
        with script.open() as source, (output / f'{table}-run.log').open('w') as log:
            subprocess.run([str(emulator), str(rom)], stdin=source, stdout=log, stderr=log, check=True)
        for stage in ('freeplay', 'restored'):
            if len((output / f'{table}_{stage}' / 'ewram.bin').read_bytes()) != 0x40000:
                raise RuntimeError(f'{table}/{stage}: incomplete EWRAM capture')
        if (output / f'{table}_freeplay' / 'ewram.bin').read_bytes() != (output / f'{table}_restored' / 'ewram.bin').read_bytes():
            raise RuntimeError(f'{table}: state reload did not restore byte-identical EWRAM')
    shutil.copyfile(BOOT / 'pinball-expected.tsv', output / 'expected.tsv')
    captures = list(output.glob('*/ewram.bin'))
    for capture in captures:
        if len(capture.read_bytes()) != 0x40000:
            raise RuntimeError(f'{capture.parent.name}: incomplete EWRAM')
        if (capture.parent / 'iwram.bin').stat().st_size != 0x8000:
            raise RuntimeError(f'{capture.parent.name}: incomplete IWRAM')
    print(f'Captured {len(captures)} private samples; Ruby/Sapphire state restoration matched byte-for-byte.')
    print('Run the production Kotlin decoder with :data:pinballCapture and -Prom/-Pcaptures paths to verify expected.tsv.')
    print('Keep all generated ROM/RAM/save/state/screenshots private. These are not publishable fixtures.')


if __name__ == '__main__':
    main()
