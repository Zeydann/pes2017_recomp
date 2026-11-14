# Touch controls: what every control sends

This is the reference for the on-screen pad: the pad bits each control presses,
the gestures (fake shot, sprint, joystick modes), and the complete mapping of
the sixteen action-button swipe slots.

The app never sends a *game action*. It sends the same Xbox 360 pad the title
reads natively -- a button mask, two triggers and two sticks (`NativeBridge`,
`runtime/kernel/input.cpp`) -- and PES 2017 decides what that means from the
player's situation. Every "PES action" below is therefore contextual.

Sources for the game's own scheme: the official PES 2017 manual's control pages
(cancel pass/shot is listed as **B / X / Y then A**; sprint dribble is
**left stick + RB**; short pass A, long pass/cross B, shoot X, through pass Y,
change player LB) and the project's own layout notes in `ControlLayout.kt`.

## The pad bits

| Bit | Xbox name | PlayStation name | PES 2017 meaning on the ball | Notes |
|---|---|---|---|---|
| `0x1000` | A | Cross (×) | Short pass; also the **cancel** button for a shot or pass | On its own, off the ball the title decides |
| `0x2000` | B | Circle (○) | Lofted pass / cross | Double and triple tap are the low and ground crosses |
| `0x4000` | X | Square (□) | Shoot (power gauge while held, shot on release) | Chip = LB + shoot; finesse = R2 during the gauge |
| `0x8000` | Y | Triangle (△) | Through pass | Lofted through pass = LB + Y (LB must be held) |
| `0x0100` | LB | L1 | Change player | Also the modifier for one-twos, chip shots and lofted through balls, which need it *held* |
| `0x0200` | RB | R1 | Sprint dribble while held with the stick; double tap is the knock-on | Sprint from the stick's rim sends this bit |
| trigger 0/1 | LT | L2 | Manual modifiers | Not reachable from a swipe: a swipe sends a button bit |
| trigger 0/1 | RT | R2 | Close control; finesse shot during the gauge | Same limit as LT |
| `0x0010` | START | Start | Pause | |
| `0x0020` | BACK | Select | Menu | |
| `0x0001`..`0x0008` | D-pad | D-pad | Tactics (up/down), advanced instructions with LT | Over the pitch the left stick can stand in for it |

## The fake shot (corrected)

The gesture is: **hold Shoot, drag towards Pass, release.** What the app sends
matters, and the previous checkpoint sent the wrong thing.

PES 2017's manual lists *cancel pass/shot* as **B, X or Y followed by A**: while
the shot's power gauge is showing, pressing pass cancels the shot and the player
feints. The shot fires on the *release* of the shoot button, so a "shot then
pass" sent after the lift is a real shot followed by a real pass -- the opposite
of a fake.

The corrected sequence, implemented in `TouchOverlayView` with the rule in
`PadGestures.kt`:

1. The thumb goes down on Shoot. Shoot is held, as any button is.
2. While the thumb is still down, a drag whose direction is within ~45° of the
   line from Shoot to Pass and which has travelled at least 24 dp arms the
   gesture. At that moment the **pass bit is pressed and held**, while shoot is
   still down -- the title's own cancel command.
3. The lift releases shoot and pass together. Nothing else is sent: no pass
   pulse at the end, and the button's own swipe direction is suppressed for
   that gesture (one gesture, one action).
4. A tap, a short drag, a drag away from pass, or a drag across the button never
   arms anything, so ordinary Shoot and Pass taps are untouched.

Multitouch: the gesture belongs to the pointer that pressed shoot; a second
thumb on the pad has its own state, and two simultaneous fake shots keep their
own pass bits. `ACTION_CANCEL`, pause, focus loss and the editor clear every
armed gesture and the held pass bit (`releaseEverything`).

**Verified here:** the rule and its edges are unit-tested (`PadGesturesTest`).
**Not verified:** that PES 2017 on the 360 build accepts the cancel exactly at
the arming sample on a real device. This needs a device test: shoot animation
swings and no shot or pass leaves the player.

## The movement stick

Both modes share one piece of arithmetic (`StickVector` in `TouchMath.kt`): the
thumb's offset from the socket, divided by the throw the player can see (the
knob's own travel by default), clamped to the rim, with the screen's Y flipped
to the guest's.

- **Move** (default, `Prefs.stickMode = "move"`): the socket is placed wherever
  the thumb lands on the left half of the screen. It does **not** follow the
  thumb: the knob moves inside it, and the deflection is relative to that fixed
  base. Lifting returns the socket to its layout position. A second thumb on
  the stick shares the base instead of re-spawning it.
- **Fixed** (`"fixed"`): the socket is the one the layout drew; the knob moves
  inside it and centres on release.

Releasing the stick sends zero, and `releaseEverything` (pause, focus loss,
edit mode, `ACTION_CANCEL`) drops the deflection and the sprint latch.

## Sprint from the rim

`SprintLatch` (`PadGestures.kt`) reads the **raw** stick throw, not the shaped
one: press at 0.92, release at 0.82, so a thumb resting on the threshold cannot
chatter between running and jogging. While latched, the pad's R1/RB bit is OR'd
into every state push, the same bit the sprint button sends, and the socket's
rim lights green. Menus never sprint (the stick is driving the d-pad there) and
any cancellation resets the latch.

## The sixteen swipe slots

Every action button carries its own four directions, each with its own bit
(`ControlElement.swipeUp/Down/Left/Right`, sent through `swipeBit`). A swipe is
read on the lift by `SwipeClassifier` (minimum 18 dp, 300 ms, dominant axis);
a bound direction sends one pulse bit, a normal tap still sends the button.

All sixteen slots ship **unbound**: no swipe fires an unasked command. Defaults
would have to invent a PES action for each direction -- PES's variations are
mostly modifiers that must be *held* (LB + pass, LB + shoot, R2 during the
gauge), which a one-bit swipe cannot express, and duplicating the button's own
action would fire two commands for one gesture. The editor is the mapping
surface: select a button, tap **Swipe directions**, choose a bit per direction.

| Button | Direction | Default | Sends if bound to a bit | PES 2017 action of that bit |
|---|---|---|---|---|
| Pass (A) | Up | Unassigned | — | — |
| Pass (A) | Down | Unassigned | — | — |
| Pass (A) | Left | Unassigned | — | — |
| Pass (A) | Right | Unassigned | — | — |
| Through Pass (Y) | Up | Unassigned | — | — |
| Through Pass (Y) | Down | Unassigned | — | — |
| Through Pass (Y) | Left | Unassigned | — | — |
| Through Pass (Y) | Right | Unassigned | — | — |
| Shoot (X) | Up | Unassigned | — | — |
| Shoot (X) | Down | Unassigned | — | — |
| Shoot (X) | Left | Unassigned | — | — |
| Shoot (X) | Right | Unassigned | — | — |
| Cross (B) | Up | Unassigned | — | — |
| Cross (B) | Down | Unassigned | — | — |
| Cross (B) | Left | Unassigned | — | — |
| Cross (B) | Right | Unassigned | — | — |

What a slide in a given direction does when it is unbound: the button itself is
held for the whole travel and released on the lift -- exactly as if the thumb
had pressed and released it without sliding. That is the normal tap, not a
second action.

Caveats that apply to any assignment:

- A swipe sends a **tap** of that bit (held for one poll through
  `nativeTouchPulse`, or a 34 ms hold on a runtime without it). Commands that
  need a *held* modifier (LB + pass, LB + shoot) cannot be produced by one
  swipe and are not offered as a single direction.
- Assigning a direction the button already sends (for example Shoot → X) fires
  the same bit twice for one gesture. The editor allows it; do not do it.
- A slide on Shoot towards Pass is the fake shot (above). If a Shoot direction
  is also bound toward pass, the fake shot wins and the swipe bit is suppressed.
- Swipes on the dedicated SWIPE patch work the same way and are independent of
  the action buttons.

| Assignment | Where it is set | What is tested |
|---|---|---|
| Four directions per action button | Editor → select button → Swipe directions | Slot independence unit-tested (`SwipeBindingsTest`); classification unit-tested (`SwipeClassifierTest`) |
| Persistence of the four bits | `controls.json`, `ControlLayout` `swipes` array | Load/save code reviewed; not unit-tested (needs Android `org.json`) |
| Direction feedback | Four chevrons on the button; the one the thumb heads for lights white | Drawing code reviewed; not device-tested |
| Haptics | One tick on press and on fake-shot arming | `Prefs.vibration`; not device-tested |

## Camera (LOOK)

A LOOK region turns the view: its drags are sent as pixel deltas
(`nativeLookDelta`), and the runtime turns them into right-stick deflection from
the finger's *speed*, so the turn is the same distance at any touch-report rate.
Sensitivity X/Y, smoothing, invert-Y and the saturation multiplier are applied
(`nativeLookSettings`). There is no separate user deadzone for LOOK: the bridge
drops residuals under 0.002 and remaps past the engine's own 14% stick deadzone
so small movements turn. The region is invisible in play, outlined in the
editor, can be moved, resized, hidden, and is under everything else, so it never
takes a touch from a control. The camera region is a working input path, but the
turn itself has not been device-verified.

## Attacking and defending pads

The title reports only menus or a match (`runtime/game_phase.h`, from whether
the player entity exists), not possession. There is therefore **no automatic**
attack/defence switching. Instead each control is arranged for both pads, the
menus, or one stance (`PhaseMask`), and the player switches the defending pad
from Settings › Controls or the pause menu (`Prefs.defendingPad`). Automatic
switching is not implemented and is not claimed.
