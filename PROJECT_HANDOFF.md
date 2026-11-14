# PES 2017 Android — Project Handoff

Last updated: 2026-11-14 (verification pass)

## Repository and branch

- Repository: `typhon-op/pes2017-recomp`
- Continuation branch: `freebuff/938b1b2d294d57d7251b2bb3`
- Previous baseline: `29d4c391a6c1348325ea1ca248bab9b2c73198a2`
- Implementation checkpoint: `0ac04d5` (present locally and on the remote branch)
- Later commits on this branch: the fake-shot / stick / sprint corrections and
  the touch-controls documentation (see `git log` on the branch)
- `docs/touch-controls.md` is the reference for every control and all sixteen
  swipe slots.

Nothing was reset, reverted or discarded. The signing key (`PES17_KEYSTORE`,
external, never in the repository) was not touched and no key was generated.
ISO importing and the Bluetooth/USB gamepad path were not modified.

## Implementation status

| Requirement | Status |
|---|---|
| Move-mode floating joystick (socket under the thumb, fixed base, knob relative to it) | Implemented; arithmetic unit-tested; not device-tested |
| Fixed-mode joystick (layout socket, knob inside it, centres on release) | Implemented; same shared arithmetic; not device-tested |
| Sprint from the rim, press 0.92 / release 0.82, R1/RB, rim glow, reset on cancel | Implemented; latch unit-tested; mapping RB = sprint matches the PES 2017 manual; not device-tested |
| Four-direction swipes on all four action buttons (16 independent slots) | Slots and classification unit-tested; every slot now ships with a PES 2017 default (modifier + button or double tap); editor binds all forms; not device-tested |
| Compound swipes: modifier + button, double tap, plain bit | Implemented (`SwipeAction`, `SwipeSequence`, `ComboHold`); timing unit-tested; every cancellation clears them; not device-tested |
| Fake shot, Shoot → drag → Pass, cancelling the shot | Corrected to PES 2017's actual cancel rule (pass pressed while shoot is held); arming unit-tested; **not device-verified** |
| Visible arrows and live direction feedback | Implemented; not device-tested |
| Camera / LOOK region with sensitivity, smoothing, saturation, invert | Input path traced to the right stick; not device-tested; no separate user deadzone (see docs) |
| Layout editor: move, resize, opacity, visibility, toggle, snap, keep-apart, reset, arrangement, per-button swipes | Implemented; add/remove of new controls is **not** implemented (controls can be hidden/shown) |
| Manual attacking/defending pads | Implemented (editor arrangement, Settings and pause-menu switch); automatic switching is **not** implemented and must not be claimed |
| Skill palette and flick skill stick with native capability probes | Previously implemented; palette kept, hidden by default |
| Skill wheel (hold, 8 sectors, native playback) | Implemented; pure geometry unit-tested; not device-tested |
| Dribbling: close control, dummy, 2x RB speed burst, stop and face | Mapped onto existing controls; no new buttons; only the tap cadence is new code |
| Multitouch and cancellation safety | Counted claims, per-pointer gestures, `ACTION_CANCEL`/pause clearing; unchanged and extended for the fake shot |
| Bluetooth/USB controller | Previously implemented; unchanged |

## Corrections made in the verification pass

1. **Fake shot.** The checkpoint sent shoot, released it (a real shot), then a
   pass pulse. PES 2017's own manual lists cancel pass/shot as **B/X/Y then A**:
   pass must go down while the shot's gauge is showing. The gesture now arms
   during the drag, presses and holds pass while shoot is still down, sends
   nothing at the lift, and suppresses the button's swipe bit for that gesture.
   Pure rule in `PadGestures.kt` (`FakeShotGesture`).
2. **Floating stick.** The checkpoint dragged the socket along with the thumb.
   The spec is a fixed base at the touch-down point with the knob moving
   relative to it; the base no longer follows. Shared vector in `TouchMath.kt`
   (`StickVector`).
3. **Sprint.** Was a single 0.92 threshold with the glow from a shaped value.
   Now a latch (`SprintLatch`): press 0.92, release 0.82, on the raw throw, glow
   only while latched, reset from every cancellation path.
4. **Compound swipes and defaults.** A slot can now be a modifier + button
   (LB/LT/RB/RT held before the bit) or a double tap, and all sixteen slots
   ship with the requested PES 2017 default. The modifier leads by 30 ms, the
   pair is held 120 ms, a double tap is 90 ms apart, and every sequence ends
   clear. `releaseEverything` (lift of the next swipe, ACTION_CANCEL, pause,
   window focus loss, editor) clears the combo, so no modifier or trigger can
   stick. The fake shot keeps strict priority over the Shoot swipe towards
   Pass (`SwipePriority`).
5. **Skill wheel, eight moves and the real rainbow.** The palette was five
   hidden buttons; the default is now one wheel control: hold 120 ms, slide to
   one of eight sectors, lift to play, with a quick tap doing nothing. The
   native table grew from five moves to eight (step over, Ronaldo chop,
   elastico appended at 5-7), indices 0 and 4 were made distinct scripts
   (double touch flicks up, body feint down), and the rainbow is the sheet's
   **2x R3** via a new frame-based `skill::ButtonSeq` rather than a stick flick.
   The wheel only chooses an index: the runtime still owns the stick, and the
   left stick is always the player's exit.

## Tests actually executed

Host C++ checks, compiled directly with `g++ -std=c++20 -I runtime` (the repo's
checks are standalone `main()` programs, so no CMake is needed here):

| Check | Result |
|---|---|
| `dynamic_scale_test` | 50 checks, 0 failures |
| `game_phase_test` | 12 checks, 0 failed |
| `input_merge_test` | 9 checks, 0 failed |
| `input_pulses_test` | 9 checks, 0 failed |
| `log_repeat_test` | 19 checks, 0 failures |
| `look_scale_test` | 7 checks, 0 failed |
| `skill_moves_test` | 40 checks, 0 failed (eight moves, distinct feints, R3 rainbow, button sequencer) |
| `skill_stick_test` | 47 checks, 0 failed |
| `stick_arrows_test` | 15 checks, 0 failed |
| `stick_curve_test` | 25 checks, 0 failed |
| `stick_pulses_test` | 62 checks, 0 failed |
| `texture_volume_test` | 196 checks, 0 failures |
| `texture_store_test` | all checks passed |
| `version_test` | 97 checks passed |

That is the previous 281 checks plus 196 + 97 and the texture store, with
`skill_moves_test` re-run at 40 checks after the wheel work. Not run:
`bc_decoder_test` (needs Vulkan headers, absent here), `durable_test` (pulls in
`ppc_recomp_shared.h`, which only the recompiled guest build generates), and
every Kotlin/JUnit test, including the new `PadGesturesTest`,
`StickVectorTest`, `SwipeBindingsTest`, `SkillWheelTest` and the existing
`TouchClaimsTest`,
`SwipeClassifierTest`, `TouchMathTest`, `ControlLayoutPhaseTest` and
`GamepadInputTest` -- this workspace has no JDK 17 and no Android SDK, and large
downloads were deliberately not started.

Static checks done: every modified Kotlin file balances its delimiters; every
`res/**/*.xml` parses; `R.string`/`R.id` references resolve.

## Not verified — needs a device or CI

1. `android/gradlew :app:assembleRelease` (JDK 17 + Android SDK) and
   `android/gradlew :app:testDebugUnitTest`.
2. Fake shot on a real match: shot swing cancels, no shot and no pass leave the
   player, ordinary shoot and pass taps unchanged.
3. Both stick modes on 16:9 and 20:9 screens; base does not move during a push;
   simultaneous stick and buttons; lift/cancel resets.
4. Sprint at 0.92 on, 0.85 held on, 0.82 off; no stuck R1 after pause or focus
   loss; physical pad sprint unaffected.
5. Swipes: confirm each of the sixteen default combinations (modifier lead,
   trigger axis, low cross double tap), each arrow, and that a plain tap is
   unchanged.
6. The compound timing on a real title poll: 30 ms modifier lead, 120 ms hold,
   90 ms double-tap gap; adjust `SwipeSequence` from what the match does.
7. The wheel on a real thumb: 120 ms hold opens it reliably without opening on
   ordinary presses, 25% deadzone is comfortable, and each of the eight moves
   is recognised by the title. Adjust `TrickWheel` and `skill_moves.h` from
   what the match does, not from the sheet.
8. Close control with RT held while steering, the RB dummy with the stick
   centred, and the 2x RB speed burst (two taps must read as two presses).
9. Camera region turns the view and does not steal touches.
10. The defending pad appears from Settings and from the pause menu.

## Suggested next steps

1. Run the two Gradle commands on CI (it has the SDK and the signing secret) and
   fix any compile findings; the Kotlin here was written without a compiler.
2. Device-test items 2-10 above and record the results here.
3. Tune the compound and wheel timings if the title misses a modifier, a double
   tap or a move; they live in two places (`SwipeSequence`/`TrickWheel` in
   `PadGestures.kt`, and the frame counts in `runtime/skill_moves.h`).
