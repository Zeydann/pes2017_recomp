# The reference apk

The touch controls in this project were measured against a released port rather
than invented: its stick footprint, throw, curve, sprint and menu behaviour are
what `touch-controls.md` compares ours with. This folder keeps that benchmark
reproducible.

| | |
|---|---|
| Name | PES 2013 Moderno |
| Package | `com.naruto.pes2013.moderno` |
| Version | `0.3.3-fg-szr` (versionCode 1) |
| Size | 52,451,384 bytes |
| SHA-256 | `72d04b9b6579910f2573b0868e1ce6dc1772816dfe79fc960e8f2c1e4f10087e` |

## Why the apk itself is not here

It is a third-party binary and this repository is public, so committing 50 MB of
someone else's game permanently into the history is not something this tree
does. What is stored instead is everything needed to *prove* a copy is the right
one: [reference-apk.json](reference-apk.json) holds the checksum, size, package
identity and the numbers that were read out of it.

The checksum is the identity. Any copy that matches it is the exact file the
benchmark was taken from, wherever it came from.

## Restoring it

```sh
./reference_apk/fetch.sh <url-or-path-to-game.apk>   # installs reference_apk/game.apk
./reference_apk/fetch.sh --verify <path>             # just check a file
```

A path is copied, a url is downloaded; either way the result is verified against
the manifest before it becomes `reference_apk/game.apk`, and a mismatch leaves
the old file alone. `*.apk` is in `.gitignore`, so the restored binary stays out
of `git status` and out of the next commit.

The download page was not recorded when the benchmark was taken, so the source
is supplied at fetch time rather than baked in here. The checksum is what makes
that safe.

## What was measured

Read from `com.naruto.port.TouchControlsView` (decompile with your tool of
choice; that output is scratch, not source, and stays git-ignored):

- **Movement stick:** anchored at 130/110 dp with a **62 dp** socket radius.
- **Grab footprint:** **1.35 ×** the radius — further outside the ring than ours,
  which is 1.25 ×.
- **Throw:** **1.0 ×** the socket radius, magnitude clamped radially to 1.
- **Knob drawing:** maxes at **0.62 ×** the radius, so at full speed the knob is
  about two thirds of the way out — the same 0.62 our knob uses, which is why
  `travelMatchesKnob` on is a departure from it and off is a return.
- **Deadzone and curve:** none of its own, and linear. The only shaping is the
  title's internal ~14 % centre, so a quarter of the way out sends a quarter of
  the throw. That is what `linearStickMode` reproduces.
- **Axes:** clamped to `[-4, 1]` and sent as int16 (`× 32767`); the `-4` floor is
  vestigial, never reachable from the normalised vector.
- **Sprint:** bit `0x0200` (R1/RB) at **0.95 ×** the socket radius, or a double
  tap within **300 ms** and **48 dp**; a single threshold, no release hysteresis.
- **Menus:** nothing is synthesised. The analog stick is always sent and the
  title navigates with it, so this pad never has the bits-plus-analog bounce.
- **Skill stick:** a plain absolute stick (378/82 dp anchor, 36 dp radius, throw
  = radius), not the flick reading this project uses.

Full detail, including the decoded float constants, is in the benchmark object
of [reference-apk.json](reference-apk.json).
