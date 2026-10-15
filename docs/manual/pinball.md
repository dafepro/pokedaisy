---
title: Pinball companion
summary: Read live objectives and plan missing catches or current party evolutions.
section: companion
order: 9
keywords: [pinball, catch, evolve, collection, saver, ruby, sapphire]
---

This fork adds an experimental companion for Pokémon Pinball: Ruby & Sapphire.
Your game plays normally. Open `PINBALL` to see one suggested next action, your
current mode and its progress, the timer, `BALL SAVER`, `BALLS LEFT`, score and
area. The panel stays on the companion screen.

`BALLS LEFT` includes the ball in play and any reserve balls. It is not the
number of the ball you are playing. The timer and saver update about once a
second from game memory. They can pause when the game pauses or holds the ball.
Watch the game itself when timing is tight.

The panel hides old objectives on menus and demo play. It also waits through
mode changes and animations rather than guessing what has happened. Bonus
stages currently direct you to the objective on the game screen.

## Collection planning

Pause the game when you want time to plan, then open `COLLECT`. Opening the
companion tab does not pause the game for you. `CATCH`
lists the current area's regular encounters, marks those already caught, and
lets you show only missing catches. `2 ARROWS` and `3 ARROWS` are separate
pools: a third arrow can replace an encounter, not just add more. The current
arrow count is shown separately from the pool you are browsing.

Some rows need a first catch in this run, and the previous target cannot be
chosen for the next regular Catch. These notes describe the current rules,
not the next random target. Special encounters are outside this catalog.

`EVOLVE` shows only the evolvable party from your current run. It distinguishes
one possible outcome from multiple forms registered together. The missing
filter keeps a branching evolution's full group, including caught forms, so
it never promises which form you will get. Names and encounter data come from
your own ROM. Browsing also works while the game is paused.

## Exact version required

The companion only reads the unmodified English `BPPE` revision `0` ROM whose
SHA-1 is `9fec81ce2c5df589e0371a0bf2f92a5fe8db730b`. Other versions still play,
but this experimental reader does not use their memory.

The `EXPERIMENTAL` notice means testing is still limited. Basic play, successful Catch modes on both
tables, timer expiry and collection updates have been checked with a
user-supplied running game in the cloud. Evolution outcomes are mapped from
public source and ROM data; the full Evolution mode still needs gameplay checks.
The integrated companion has not yet been tested on Android hardware.

## Practice and what comes next

You can still use the existing [save states](saves.md) and
[rewind and game speed](fast-forward.md). Named practice situations, illustrated
table rules, separate assisted results and a post-run breakdown are still planned.

## Experimental Android build

The debug test app is named `PokeDaisy Pinball (Debug)` and installs separately
from regular PokeDaisy. Its default settings and saves are private to that app.
It does not offer in-app updates from the upstream release channel.

Keep your original installation. Use copied ROMs and saves for testing. If you
select the same shared save folder in both apps, they can still write the same
files; a separate app name does not protect that shared folder. The debug build
has not yet been tested on Android hardware.
