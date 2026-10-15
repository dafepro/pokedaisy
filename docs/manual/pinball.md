---
title: Pinball companion
summary: Read your current objective, timer and progress on the companion screen.
section: companion
order: 9
keywords: [pinball, catch, evolve, saver, ruby, sapphire]
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

## Exact version required

The companion only reads the unmodified English `BPPE` revision `0` ROM whose
SHA-1 is `9fec81ce2c5df589e0371a0bf2f92a5fe8db730b`. Other versions still play,
but this experimental reader does not use their memory.

The `EXPERIMENTAL` notice means testing is still limited. Basic play on both
tables has been checked with a user-supplied running game in the cloud. Catch
and Evolution guidance currently rely on public source and synthetic tests.
The integrated companion has not yet been tested on Android hardware.

## Practice and what comes next

You can still use the existing [save states](saves.md) and
[rewind and game speed](fast-forward.md). Named practice situations, collection
planning, illustrated table rules, separate assisted results and a post-run
breakdown are not part of this first slice.
