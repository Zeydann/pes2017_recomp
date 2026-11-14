# Touch controls: what every control sends

This is the reference for the on-screen pad: the pad bits each control presses,
the gestures (fake shot, sprint, joystick modes), the compound swipe bindings,
and the complete default mapping of the sixteen action-button swipe slots.

The app never sends a *game action*. It sends the same Xbox 360 pad the title
reads natively -- a button mask, two triggers and two sticks (`NativeBridge`,
`runtime/kernel/input.cpp`) -- and PES 2017 decides what that means from the
player's situation. Every "PES action" below is therefore contextual.

Sources for the game's own scheme: the official PES 2017 manual's control pages
(cancel pass/shot is listed as **B / X / Y then A**; sprint dribble is
**left stick + RB**; short pass A, long pass/cross B, shoot X, through pass Y,
change player LB; L1/L2/R1/R2 are the modifiers for the lobbed, manual, driven
and controlled variants) and the project's own layout notes in `ControlLayout.kt`.

## The pad bits

| Bit | Xbox name | PlayStation name | PES 2017 meaning on the ball | Notes |
|---|---|---|---|---|
| `0x1000` | A | Cross (×) | Short pass; also the **cancel** button for a shot or pass | Off the ball the title decides |
| `0x2000` | B | Circle (○) | Lofted pass / cross | Double/triple tap are the low and ground crosses |
| `0x4000` | X | Square (□) | Shoot (power gauge while held, shot on release) | Chip = LB + shoot; finesse = RT during the gauge |
| `0x8000` | Y | Triangle (△) | Through pass | LB + Y is the lofted through ball |
| `0x0100` | LB | L1 | Change player | Also held as a modifier (one-two, chip, lofted through) |
| `0x0200` | RB | R1 | Sprint dribble while held; double tap is the knock-on | Sprint from the stick's rim sends this bit |
| trigger 0 | LT | L2 | Manual modifiers | Held by a compound swipe as an axis value |
| trigger 1 | RT | R2 | Close control; finesse shot during the gauge | Same |
| `0x0010` | START | Start | Pause | |
| `0x0020` | BACK | Select | Menu | |
| `0x0001`..`0x0008` | D-pad | D-pad | Tactics, advanced instructions with LT | Over the pitch the left stick can stand in |

## Swipe bindings

A swipe slot is a `SwipeAction` (`PadGestures.kt`): a **primary bit**, an
optional **held modifier** (LB, RB, LT or RT), and an optional **double tap**.
Plain single bits are the original binding and are unchanged; the compound half
is optional in `controls.json`, so a layout written before this keeps its plain
bits and an older build still reads the file.

How a compound swipe is emitted (`SwipeSequence`, scheduled by the overlay):

| Binding | What the pad sends |
|---|---|
| `A` (plain) | one press pulse of A on the lift (the existing path) |
| `LB + A` | LB down; 30 ms later LB+A; 120 ms later everything up |
| `RT + X` | right trigger 255; 30 ms later RT + X; 120 ms later both up |
| `B ×2` | B; 90 ms gap; B; clear |

The modifier leads the button by one step so the title reads it the way it
reads a hand, the pair is held for several polls, and **every sequence ends
with an all-clear step**. The overlay also calls `ComboHold.clear()` from every
cancellation path -- lift of the next swipe, `ACTION_CANCEL`, pause, window
focus loss, the editor, the pad being hidden -- so a modifier or trigger can
never be left down. Only one compound swipe is in flight at a time; starting a
new one clears the previous one first.

The editor lists every option: none, the plain bits, all four modifiers on each
of the four face buttons, and a double tap of each face button.

## Default sixteen slots

These are the shipped defaults (`ControlLayout.applyDefaultSwipes`), PES 2017's
own base + modifier scheme:

| Button | Direction | Default binding | Bits sent | PES 2017 action |
|---|---|---|---|---|
| Pass (A) | Up | LB + A | LB, then LB+A | One-two pass |
| Pass (A) | Down | LT + A | LT axis, then LT+A | Manual pass |
| Pass (A) | Left | RB + A | RB, then RB+A | Driven ground pass |
| Pass (A) | Right | RT + A | RT axis, then RT+A | Controlled pass |
| Through Pass (Y) | Up | LB + Y | LB, then LB+Y | Chipped through ball |
| Through Pass (Y) | Down | LT + Y | LT, then LT+Y | Manual through ball |
| Through Pass (Y) | Left | RB + Y | RB, then RB+Y | Lofted through ball |
| Through Pass (Y) | Right | RT + Y | RT, then RT+Y | Controlled through ball |
| Cross (B) | Up | LB + B | LB, then LB+B | Early / deep cross |
| Cross (B) | Down | B ×2 | B, gap, B | Low cross |
| Cross (B) | Left | LT + B | LT, then LT+B | Manual cross |
| Cross (B) | Right | RT + B | RT, then RT+B | Controlled curled cross |
| Shoot (X) | Up | LB + X | LB, then LB+X | Chip shot |
| Shoot (X) | Down | RT + X | RT, then RT+X | Controlled finesse shot |
| Shoot (X) | Left | LT + X | LT, then LT+X | Manual shot |
| Shoot (X) | Right | RB + X | RB, then RB+X | Driven shot |

Caveats:

- **Fake shot priority.** The Shoot→Pass drag is the fake shot. It arms during
  the drag and suppresses that gesture's swipe (`SwipePriority`), so the Shoot
  Right binding (RB + X) never fires over a drag towards Pass. A drag that is
  not towards Pass still fires it.
- A slide holds the button normally for the whole travel and releases it on the
  lift, so a plain tap is unchanged. A **bound** direction adds the sequence
  above; an unbound one adds nothing.
- A double tap and a modifier can be stored in the same slot (the data model
  allows it), but the sequence builder gives the double tap precedence. The
  editor sets one at a time.
- The sequences are timed for a title polling at 30-60 Hz: 30 ms is at least one
  poll, 120 ms several. They have not been tuned on a device.
- The dedicated SWIPE patch uses the same bindings and is independent of the
  action buttons.

## The fake shot

PES 2017 cancels a shot when pass goes down while its power gauge is showing
(manual: cancel pass/shot = **B / X / Y then A**), and the shot fires on the
release of shoot. So the gesture must press pass *before* the lift:

1. Thumb down on Shoot; shoot is held and the gauge runs.
2. While still down, a drag within ~45° of the Shoot→Pass line and at least
   24 dp arms the gesture: the pass bit is pressed and held, with shoot still
   down.
3. The lift releases both. Nothing else is sent -- no trailing pass pulse, and
   the Shoot swipe is suppressed for that gesture.
4. Taps, short drags, drags away from Pass and drags across the button never
   arm. Multitouch is per pointer; two fake shots keep their own pass bits.

Implemented and unit-tested; the cancel itself is not device-verified.

## The movement stick

Both modes share `StickVector` (`TouchMath.kt`): thumb offset from the socket
divided by the visible throw, clamped to the rim, screen-Y flipped for the guest.

- **Move** (default): socket placed where the thumb lands on the left half and
  **stays fixed**; the knob moves inside it. Lifting returns it to its layout
  position. A second thumb shares the base.
- **Fixed**: socket is the layout socket; knob inside it; centres on release.

## Sprint from the rim

`SprintLatch`: press at **0.92** of the raw throw, release at **0.82**, latched
between them. While latched the pad's RB bit (`0x0200`, R1) is OR'd into every
state push and the socket rim lights. Menus never sprint; every cancellation
resets the latch.

## Camera (LOOK)

A LOOK region turns the view: drags become pixel deltas (`nativeLookDelta`) and
the runtime turns them into right-stick deflection from the finger's *speed*, so
the turn is the same distance at any touch-report rate. Sensitivity X/Y,
smoothing, invert-Y and the saturation multiplier are applied
(`nativeLookSettings`). There is no separate user deadzone for LOOK: the bridge
drops residuals under 0.002 and remaps past the engine's own 14% stick deadzone.
The region is invisible in play, outlined in the editor, movable, resizable,
hideable, and under everything else. Not device-verified.

## Attacking and defending pads

The title reports only menus or a match (`runtime/game_phase.h`, from whether the
player entity exists), not possession. There is therefore **no automatic**
attack/defence switching. Each control is arranged for both pads, the menus, or
one stance, and the player switches the defending pad from Settings › Controls
or the pause menu (`Prefs.defendingPad`).

## Verification

- `PadGesturesTest`: sprint latch and fake-shot arming, plus every compound
  sequence (modifier lead, trigger axis, double tap, ending clear, mid-sequence
  clear, fake-shot priority).
- `SwipeBindingsTest`: all 16 default combinations, the primary bits behind
  them, independence and unbound cases.
- `StickVectorTest`: normalisation for both stick modes.
- The Kotlin tests need a JVM and Android SDK (not available in the cloud
  workspace); the C++ host checks in `tests/` pass (see `PROJECT_HANDOFF.md`).
- Not device-tested: the timing of the compound sequences, the double tap, the
  PES cancel window, and whether the title reads the trigger axis in time.
