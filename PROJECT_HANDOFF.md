# PES 2017 Android — Project Handoff

Last updated: 2026-11-14

## Repository and branch

- Repository: `typhon-op/pes2017-recomp`
- Branch carried over from the previous session: `freebuff/938b1b2d294d57d7251b2bb3`
- Baseline given in the brief: `29d4c391a6c1348325ea1ca248bab9b2c73198a2`
- HEAD at recovery: `29d4c391a6c1348325ea1ca248bab9b2c73198a2` (identical to the baseline)
- Working tree at recovery: clean; no staged, unstaged or untracked work
- `PROJECT_HANDOFF.md` before this file: did not exist
- Previous session checkpoint commit: none. The previous branch is at the baseline
  commit itself, so no implementation was lost or left behind — everything that
  was committed previously is the baseline, and nothing else existed.

Nothing was reset, reverted, cleaned or overwritten. The permanent signing key
(`PES17_KEYSTORE`, external) was not touched, no key was generated, and the ISO
importing path was not modified.

## What exists in the baseline (verified in source)

- `android/app/src/main/java/com/pes2017/recomp/ControlLayout.kt` — pad model,
  phases, per-control swipe bindings, defaults, JSON persistence.
- `TouchOverlayView.kt` — play/edit touch handling, per-finger claims, look
  deltas, d-pad, swipes, skill palette, trigger release grace, tap pulses.
- `TouchClaims.kt`, `TouchMath.kt`, `SwipeClassifier.kt` — unit-tested logic.
- `GamepadInput.kt` — physical pad (Bluetooth/USB) input, player 2 aid.
- `runtime/skill_stick.h`, `runtime/skill_moves.h`, `runtime/input_pulses.h`,
  `runtime/input_merge.h`, `runtime/look_scale.h` — native side, capability-probed
  from `NativeBridge`.
- Tests: `TouchClaimsTest`, `SwipeClassifierTest`, `ControlLayoutPhaseTest`,
  `TouchMathTest`, `GamepadInputTest`, plus native tests under `tests/`.

## Implementation status

| # | Requirement | Status |
|---|---|---|
| 1 | eFootball-style floating/movable joystick | Completed (Move mode is default; socket appears under the thumb anywhere in the left half, base follows a thumb dragged past the rim, returns to its layout spot on release) |
| 2 | Fixed joystick mode | Completed (Settings > Controls > Movement stick: Move / Fixed) |
| 3 | Sprint by outer joystick threshold | Completed (raw throw >= 0.92 sets R1/RB; socket rim lights; can be turned off in Settings) |
| 4 | Four independent directional swipes on each of the four action buttons | Completed (each face button classifies its own pointers; the editor binds four directions per button; unbound by default) |
| 5 | Visible directional arrows and gesture feedback | Completed (four chevrons on any action button with bindings; the chevron the thumb is heading for lights white while dragging) |
| 6 | Fake shot by dragging Shoot toward Pass | Completed (drag vector from shoot compared with the shoot->pass line, >= 0.70 cosine and 24 dp travel; pass bit sent on the lift) |
| 7 | Camera/LOOK control | Completed (a LOOK region in the free band between the sticks, match only, invisible in play, outlined and adjustable in the editor) |
| 8 | Layout editor improvements | Completed (drag, pinch, size, opacity, visibility, toggle, snap, keep-apart, reset one/all, plus Arrangement and per-button swipe bindings) |
| 9 | Manual attacking/defending layouts | Completed (ATTACK/DEFEND masks, Arrangement button in the editor, Settings switch and pause-menu switch; the title does not report possession, so the switch is manual) |
| 10 | Skill controls and native engine compatibility | Completed previously (SKILL palette, flick skill stick, per-entry-point capability probes with graceful fallbacks) |
| 11 | Multitouch and cancellation safety | Completed previously (counted claims per control, per-finger gestures, ACTION_CANCEL and pause release, tap pulses) |
| 12 | Bluetooth/USB controller compatibility | Completed previously (`GamepadInput`, key/motion dispatch, pad-vs-touch merge in `runtime/input_merge.h`) |

## Changes made in this session

- `ControlLayout.kt`: `PhaseMask.ATTACK`/`DEFEND`, `PadStance`, stance-aware
  `shownIn`; semantic `sub` labels on the face buttons (PASS, CROSS, SHOOT,
  THROUGH); a default `look` region.
- `Prefs.kt`: `stickMode` (Move/Fixed, Move default), `sprintFromStick`,
  `defendingPad`.
- `SwipeClassifier.kt`: `peek()` for live arrow feedback, sharing `end()`'s
  rules (the fake shot uses the landing point and the lift point instead).
- `TouchOverlayView.kt`: floating stick origin and drag-follow, edge-zone hit
  test that never steals a shoulder button, sprint from the raw throw with a rim
  highlight, swipe classification on BUTTON pointers with per-button arrows and
  live feedback, fake-shot detection, LOOK default, stance plumbing, and d-pad
  synthesis stopped during a match.
- `ControlEditPanel.kt`: swipe binding dialog for action buttons; new
  Arrangement button (both pads / menus / both match pads / attacking /
  defending).
- `SettingsActivity` + `activity_settings.xml`: Movement stick, sprint-from-rim
  switch, defending-pad switch.
- `GameActivity.kt`: stance applied on create/resume, pause-menu pad switch.
- `ControlsEditorActivity.kt`: stance applied from prefs.
- `strings.xml`: the new labels and hints.

No native runtime file was changed, so `libpes17.so` does not need rebuilding.
The changes are all in the app layer and use the existing `NativeBridge` calls.

## Verification performed

- XML: every file under `android/app/src/main/res` parses; no duplicate string
  names; every `R.string`/`R.id` reference from the Kotlin sources resolves
  (the only unmatched names are `android.R.string.ok` and the Gradle-provided
  `app_name`, both expected).
- Kotlin: delimiter balance check over every modified file, plus a read-through
  of each changed region.
- **Not run:** Gradle build, `assembleDebug`/`assembleRelease`, JUnit tests and
  any device/emulator test. This cloud workspace has no JDK and no Android SDK,
  so the APK could not be compiled here. The next session (or CI, which has the
  SDK and the signing key) must run `android/gradlew :app:assembleRelease` and
  the unit tests before shipping.

## Suggested next steps

1. Build on CI or a machine with JDK 17 + Android SDK; fix any compile findings
   (the changes were written without a compiler available).
2. On a device: check Move vs Fixed feel, sprint at the rim, one bound swipe per
   face button, fake shot, camera region, and switching the defending pad from
   the pause menu.
3. Optionally give the four action buttons default swipe bindings once the
   behaviour is confirmed on device; they are deliberately unbound so no swipe
   fires an unasked command on a pad that was not arranged for it.
