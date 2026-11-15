# Touch controls: what every control sends

This is the reference for the on-screen pad: the pad bits each control presses,
the gestures (the pass double tap, sprint, joystick modes), the compound swipe bindings,
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

- **Button swipes are direct.** A flick on an action button sends the direction
  the thumb ended on and nothing else. The fake shot's drag used to compete for
  the same gesture; it is gone, so `SwipePriority` only asks whether the
  direction was bound, and a bound direction always fires.
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

## Double-tapping pass (the one-two)

PES 2017's one-two is **L1 + pass**. On the pad, two taps of Pass inside 220 ms
(`DoubleTap`) send that combination as a short held sequence: L1 down, L1 + pass,
then everything cleared.

The single tap is untouched. The first press already sent its pass bit, and the
second press is a press of its own, so a tap that misses the window degrades
into two ordinary passes rather than nothing at all. A completed pair is
consumed, so three taps are a pair and a single rather than two pairs.

## The movement stick

Both modes share `StickVector` (`TouchMath.kt`): thumb offset from the socket
divided by the visible throw, clamped to the rim, screen-Y flipped for the guest.

- **Fixed** (default): the socket is the layout socket, so the stick is where
  the player learned it. The knob moves inside it; the last push is centred on
  release.
- **Move**: socket placed where the thumb lands on the left half and **stays
  fixed** while that thumb is down; lifting returns it to its layout position. A
  second thumb shares the base.

The throw is measured across the **whole socket**, not the knob's smaller
travel: a thumb on the rim is full deflection, which is what puts the 0.92
sprint latch where a hand can find it. The skill stick is the exception -- its
throw is 55 % of its socket, with a 0.05 deadzone and a 0.85 curve -- because a
skill move is a short flick to a sector rather than a pace to hold. The absolute
skill stick's last sample is held for 90 ms after the lift so the title's poll
can see a flick that began and ended between two frames.

## Sprint from the rim

`SprintLatch`: press at **0.92** of the raw throw, release at **0.82**, latched
between them. While latched the pad's RB bit (`0x0200`, R1) is OR'd into every
state push and the socket rim lights. Menus never sprint; every cancellation
resets the latch.

## Dribbling on the ball

The ball-carrying controls are the pad's own, so most of them need no new
button and are listed here so nobody adds one:

- **Standard dribble.** The movement stick.
- **Sprint dribble.** Hold RB, or push the stick past the latch (0.92 press,
  0.82 release) while RB is held or alone on the rim.
- **Knock ball ahead / speed burst (2x RB).** Not a new control: it is two taps
  of the Sprint button, and the pad already sends each tap as its own press
  edge, so the title sees two presses. `TouchClaims` gives every new press a
  pulse for the next poll; the only guard is `TapCadence` (40 ms), which drops
  the *pulse* of a press that is a bounce of the one before it and never the
  hold, so two real taps (a fifth of a second apart) are untouched while a
  chattering switch cannot make one press look like two.
- **Close control (hold RT + LS).** Hold the RT control while steering. RT stays
  down for as long as the finger does; the 80 ms trigger grace only delays the
  *release*, so the ball stays close through a tap on another button. A player
  who wants it latched can turn RT's existing **toggle** switch on in the
  editor; no separate control exists, because a second RT would be the same bit
  twice.
- **Stop and face goal (LS centred, tap/hold RT).** RT with the stick at zero.
  Nothing extra: the stick's own neutral is the stop.
- **Let the ball run / dummy (hold RB, no LS).** Hold Sprint with the stick
  centred. The rim latch cannot fire without a throw, so it does not fight the
  hold, and lifting returns the pad to normal.

## The skill wheel

The skill palette is one button per move and is still there, but it is hidden by
default. The default is one **wheel** control (`id: "trick"`, 64 dp, above the
action diamond and clear of both the shoot-to-pass drag and the skill stick,
match only), because eight named buttons beside the shoot diamond is a lot of
screen for something used a few times a match.

- **How it fires.** Hold the control. After `TrickWheel.HOLD_MS` (120 ms) a ring
  of eight sectors opens **around the thumb's landing point**, not around the
drawn button, so the ring comes to the thumb. Slide to a sector (25% of the
radius is a deadzone that chooses nothing; each change ticks if vibration is
on) and **lift**: the move bound to that sector plays. A quick tap does
**nothing** -- it is deliberately not a shortcut for a move.
- **What it plays.** The move index goes to `nativeSkillPlay`, and the runtime
  plays it from `runtime/skill_moves.h` one guest frame at a time: the right
  stick only, or buttons (the rainbow is two R3 clicks), and never the left
  stick. The wheel picks; the runtime owns the input until the last frame.
- **Exit direction.** Several moves end by asking for a left-stick direction
  the player chooses ("then push left stick"). The wheel plays the first half
  and stops; the left thumb supplies the exit. The editor says so beside the
  move's name, and the ring does not drive LS at all, so movement is never
  frozen by a skill.
- **Defaults.** One move per sector, all eight moves reachable out of the box:

  | Sector | Direction | Move (index) |
  |---|---|---|
  | 0 | right | Ronaldo Chop (6) |
  | 1 | up-right | Elastico (7) |
  | 2 | up | Body Feint (4) |
  | 3 | up-left | Rainbow Flick (2) |
  | 4 | left | Drag Back (3) |
  | 5 | down-left | Step Over (5) |
  | 6 | down | Double Touch (0) |
  | 7 | down-right | Roulette (1) |

- **Table order is a contract.** `runtime/skill_moves.h` may be appended to and
  never reordered: a saved layout stores the indices, and the C++ side refuses
  an index it does not know. Indices 0 and 4 used to be the same script (left
  then right, which made the palette's double touch and body feint the same
  press); they are now distinct -- double touch flicks up, body feint down --
  which the sheet allows for both.
- **Rainbow Flick is the pad's 2x R3.** The move is a button script
  (`ButtonSeq`), not a stick deflection: R3, release, R3, release, played in
  whole frames and merged into whatever the pad is already holding. A mirror of
  the right stick is therefore not involved.
- **Persistence and safety.** The sectors are saved as an additive `trick`
  array in `controls.json`; a layout from before the wheel keeps its defaults.
  Nothing is held while the wheel is open, so lift, `ACTION_CANCEL`, pause,
  focus loss and the editor all just close it; the runtime's own scripts end in
  a released frame.

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

The title reports only menus or a match (`runtime/game_phase.h`, and in this
build not even that -- see the note below), and **not possession**. There is
therefore **no automatic** attack/defence switching. Each control is arranged
for both pads, the menus, or one stance.

### The stance chip

The pad now carries one **stance chip** (`id: "stance"`, 40 dp, above the action
diamond, match only, movable and hideable in the editor). **One tap** flips the
attacking pad to the defending one and back, with a haptic tick and a short toast
("Attacking pad" / "Defending pad") so an accidental flip is noticed and one tap
undoes it. It writes the same `Prefs.defendingPad` the Settings switch and the
pause menu write, so the three can never disagree. A thumb that lands and slides
more than 18 dp off the chip is not counted as a tap.

The chip holds **no pad bit**: a flip changes what the labels say and which
controls are arranged for which half, never which bits a held finger is sending.
It deliberately does not call `releaseEverything`, so another thumb holding pass
keeps holding pass; a control that belongs to the other stance stops being sent
(the state push skips what is not on screen) and its claim goes when the finger
lifts.

### Dual labels, and the near-thumb switch

The title already reinterprets the same four buttons by possession; the pad says
so. Each action button carries its second line (`sub2`) and draws it in place of
the attacking name while the defending pad is out:

| Button | Attacking | Defending | Defensive action |
|---|---|---|---|
| A | PASS | **PRESS** | Pressure / contain (hold); double-tap for the foot-out tackle |
| B | CROSS | **TEAM** | Teammate pressure (hold; double-tap-and-hold for more players) |
| X | SHOOT | **SLIDE** | Slide tackle |
| Y | THROUGH | **GK** | Goalkeeper rush |
| LB | LB | **SWITCH** | Player switch |
| stick rim / RB | sprint | sprint | Defensive sprint (same 0.92/0.82 latch) |

The far top-left LB is an **attacking** control now (`PhaseMask.ATTACK`): without
the ball the switch has to be under the right thumb, so the defending pad has its
own `switch` control at the left of the diamond, holding the same LB bit. No
binding changes; a duplicate bit is harmless, and the two are never on screen
together.

**These labels are presentation, not remapping.** The game decides what A/B/X/Y
mean from possession. The public sources also disagree on one of them: the
official PES 2016 Xbox manual puts the slide tackle on X, while a 2017 guide puts
it on B. That must be checked in the title before the labels are treated as
fact; "match-up" is not part of PES 2017's documented defensive set and is
deliberately absent here.

**Phase signal caveat.** `runtime/title.h` still holds placeholders for the
title's client states (`T_DATA_ClientStates = 0`), so `player::NowRunning()`
answers `Unknown` and the app never learns that a match has started. The stance
chip is therefore the only on-pitch way to change pads; automatic switching needs
that address chain derived first.

## The pad's look

The pad is drawn with a small shared palette (`ControlTheme`): near-black glass
under every control, a coloured rim per family, and a white specular arc on the
face buttons. Effects are **SUBTLE by default**: on a press the control settles
by 4 % over 90 ms and a soft aura in its own accent grows; on the lift the light
fades over 140 ms. There is no idle animation and no full-screen tint, so the
pitch stays readable and a resting pad costs exactly what it did before.

- `Prefs.controlFx` is `subtle` (default), `full` (adds an expanding ring) or
  `off` (flat colours, no frames). Settings → Controls → **Touch effects**
  toggles subtle/off.
- Frames are requested only while a press is inside its 90 + 140 ms window
  (`postInvalidateOnAnimation`); an idle pad does not redraw itself.
- The overlay times its own draw. If the average exceeds 6 ms over thirty
  draws for three samples in a row, effects turn themselves off for the rest
  of the session; the controls stay exactly where they are.
- Durations are fixed and short (90/140 ms), so the pad never feels like it
  is animating at the player rather than responding to them.

## Spacing and the gesture corridor

The factory pad (`PROFILE_MODERN`, 800 × 360 reference) puts the four actions
64 dp across, spread a little wider than the old diamond:
A `-110, -54`, B `-48, -110`, X `-172, -110`, Y `-110, -172` (all from the
nearest edge). With both 6 dp touch slacks counted, every diagonal pair now
clears by **7.5 dp** instead of 1–2 dp. The defending switch moved to
`-212, -45` and clears X by 4.3 dp while staying 17 dp above the bottom edge.
`PROFILE_CLASSIC` restores the previous 68 dp diamond and switch position
verbatim for anyone who preferred it.

Two rules keep it honest:

- **Nearest centre wins.** Two hit areas can still overlap by the slack. The
  control whose centre is closest to the finger is the one claimed, regardless
  of the order in the file (the old code took the first in the list).
- **Gesture corridors.** `GestureCorridor` checks each bound direction as a
  32 dp line from the button's centre: it must end inside the usable screen
  (8 dp side margin, 16 dp bottom inset) and must not pass within another
  control's radius plus slack. The editor prints "Swipe tracks clear" or the
  direction and what blocks it; `TouchGeometryTest` walks all sixteen default
  bindings and the skill ring.

## Input safety, in invariants

1. **Ownership.** A bit exists only because a pointer is claimed by the control
   that owns it; `pushState` reads claims, never raw coordinates.
2. **Movement is movement.** The stick branch writes stick axes only. The
   sprint latch writes only RB. No stick position can become A/B/X/Y.
3. **Swipes are local.** A swipe starts only on a `BUTTON` or the `SWIPE`
   patch, and is read only on that finger's lift. A finger that landed on the
   stick can never produce a swipe.
4. **The pass double tap is local.** Only a button whose **role** is `PASS` is
   read for it. Roles survive renaming, so a cosmetic label cannot move it.
5. **The stance chip holds nothing** and does not release another thumb's
   button; controls that leave the pad simply stop being sent.
6. **Nothing survives a cancel.** Pause, hide, focus loss and `ACTION_CANCEL`
   cancel the wheel timer, combos, trigger grace, pulses and held arms.

## Customizing the pad

Settings → Controls → **Arrange** (or pause → Edit controls) opens the same
editor as before, with a **More…** menu on the strip:

- **Change binding…** for an action button: any pad bit, or none. The role
  follows the bit automatically (A pass, B cross, X shoot, Y through).
- **Rename…**: the printed name and the on-ball caption. Behaviour uses the
  role, not the text.
- **Different on the defending pad**: snapshots the control's current place,
  size, opacity and visibility into a defending copy. While the DEF pad is out,
  dragging and the sliders edit that copy; choose the menu again to merge it.
- **Add a control…**: restores any factory control the player removed (d-pad,
  L3/R3, skill palette, second LB, and so on). The movement stick and camera
  region cannot be removed.
- **Remove this control.** Undo brings it back; the removal is remembered in
  the file.
- **Undo / Redo**: twenty snapshots per editing session, including drags and
  pinch-resizes.
- **Profiles…**: load the Modern or Classic factory pad, save the current pad
  under a name, or load/delete a saved profile. Profiles are plain JSON files
  beside `controls.json`.

## Saving, repair and the way back

`controls.json` is now **version 7**. Older files are migrated onto the current
defaults (positions, sizes, swipes and wheel kept; unknown ids dropped; new
controls defaulted) instead of being deleted as before. The file records the
per-stance copy, role, display names, and removed controls.

- Every `save()` rotates the previous file to `controls.backup.json`.
- If the file cannot be parsed, it is copied to `controls.broken.json` and the
  session starts on the factory pad; nothing is deleted.
- On load, the movement stick and camera region are restored if missing, and a
  pad with fewer than three visible controls is treated as broken and reset.
- **Restore default controls** (Settings → Controls, and the editor's Reset
  all) confirms, saves the current pad as the backup, then writes the factory
  pad for the active profile.
- **Restore last backup** brings back the one generation kept.

## The control guide

`ControlGuideActivity` is a full-screen, landscape guide reached from the
launcher (**Controls guide**) and from the pause menu. Its cards read the
player's **current** layout: the live wheel sectors, the defending labels, and
every bound swipe on the action buttons, plus essentials, dribbling, input
safety and where the editor lives. It is built from the same glass/accent
tokens as the pad and has no image assets to drift out of date.

## The d-pad and its idle fade

The d-pad is now on screen in a match as well as in the menus, at `200, 120`
(110 dp): clear of the movement stick (37 dp), the near LB (5.2 dp) and the
rest of the pad. Over the pitch it is a tactical control first, so it gets out
of the way by itself:

- After **four seconds** (`DpadAutoHide.IDLE_MS`, inside the requested 3–5 s)
  without a touch, it fades to invisible over **280 ms**. The fade is linear
  and runs on the view's own 32 ms tick; only the d-pad's alpha changes, so no
  other control dims or flickers with it.
- A tap anywhere in a ring of 1.6 × its radius plus 12 dp wakes it
  **instantly** to full opacity. A tap squarely on the invisible control only
  reveals it, so no arm fires from a shape the player cannot see; a tap in the
  wider ring still reaches its neighbour (the near LB is inside that ring), so
  the wake zone never swallows a pass or a camera drag. Touching a visible
  d-pad restarts the clock.
- While a d-pad arm is held the clock will not hide it (`dpadBits` is checked),
  and a hidden d-pad is skipped by hit testing, so the touch falls through to
  the pitch.
- In the title's menus the d-pad never fades: it is navigation there, and the
  phase check restricts the clock to `GamePhase.MATCH`.
- The editor draws it always, faded only by the usual "not on this screen"
  treatment, and entering the editor cancels the clock.
- Settings → Controls → **Auto-hide D-Pad** (on by default) turns the whole
  behaviour off, leaving it permanently visible. The preference is read live,
  so the next match honours it without a restart.

The four bits it holds, the stick stand-in and the menu behaviour are
unchanged; this only changes when the control is drawn and hit-testable.

## Verification

- `PadGesturesTest`: the sprint latch, the pass double tap's window and its
  consumed pair, every compound sequence (modifier lead, trigger axis, double
  tap, ending clear, mid-sequence clear), the wheel's hold and sector
  arithmetic, and the sprint tap cadence behind the 2x RB speed burst.
- `SkillWheelTest`: the eight moves in the table, the exit hints, every default
  sector bound and unique, the default wheel's sector order, and that a bad
  index is not stored.
- `tests/skill_moves_test.cpp` (host, passing): eight moves, every move ends,
  the two feints are distinct, the step over is continuous, the rainbow is two
  R3 clicks with a release between them, and the button sequencer clears.
- `SwipeBindingsTest`: all 16 default combinations, the primary bits behind
  them, independence and unbound cases.
- `DefensiveLayoutTest`: the stance chip and near-thumb switch ship on the right
  pads, the four defensive names, that the chip holds no bit, and that neither
  new control overlaps any other control on its own pad (a numeric clearance
  test, so a later default move fails loudly).
- `TouchGeometryTest`: the retuned diamond keeps at least 6 dp between every
  pair, every default swipe's corridor is clear, the skill ring clears every
  control on the attacking pad, the d-pad clears every control on its pad, and
  the new controls stay out of the bottom gesture inset.
- `DpadAutoHideTest`: fully visible for the whole idle window, fading from
  four seconds, linear to hidden at +280 ms, instant restore on a touch, a
  touch during the fade cancels it, and the remaining idle time counts from
  the last touch.
- `LayoutSerializationTest`: a v7 round trip keeps roles, names and the
  defending copy; a v6 file is migrated rather than dropped; a removed control
  stays removed across a reload; the stick cannot be removed; `resetAll`
  restores 64 dp modern spacing; the Classic profile is 68 dp; unreadable and
  newer files are refused without throwing.
- `StickVectorTest`: normalisation for both stick modes.
- The Kotlin tests need a JVM and Android SDK (not available in the cloud
  workspace); the C++ host checks in `tests/` pass (see `PROJECT_HANDOFF.md`).
- Not device-tested: the timing of the compound sequences, the double tap, the
  PES cancel window, and whether the title reads the trigger axis in time.

## Fitting the pad to the screen

`ScreenFit` (and `SafeAreaDp`) takes the window in dp and the system's
`systemBars() | displayCutout()` insets, and answers two questions for every
control: how big may it be, and where may its centre go. Fitting is a view of
the layout, never a save — the file keeps its fractions and dp sizes, so a pad
edited on a notched phone is the same pad on one without a notch.

A control is only ever shrunk, never grown, and never below 44 dp. Positions
are clamped so the whole circle (plus the touch slack) stays inside the safe
rectangle, which is what stops a button landing under a punch-hole camera or
the gesture bar.

## Saved layouts (slots)

Five numbered slots plus as many named layouts as the player makes, in
`LayoutStore`. A slot's file name is derived and fixed
(`controls_slot_3.json`); the name the player typed lives *inside* the layout
file, so renaming never renames a file and the list shows what was typed. A name
is sanitised only to derive an id, and the id can never contain a dot or a path
separator.

The old `controls_profile_*.json` files are folded into slots on first use, with
the name recovered from the file name. Saving over an existing slot asks first;
duplicating never lands on top of another pad.

## Import and export

`LayoutEnvelope` wraps a layout with `format: "pes17-controls"`, an envelope
version, a name, the app version, the creation time and the device size. Import
refuses: a file over 512 KB, anything that is not JSON, the wrong format tag, a
newer envelope, a newer layout version, no controls, or more than 64 controls.
A rejection names the reason and changes nothing.

Export goes through the Share Sheet (a cache file handed out read-only by the
app's `FileProvider`) or, on import, through the system document picker — the
app never needs a path it does not have permission for.

## One haptics switch

`HapticsPolicy` is the only place that decides whether a buzz may happen, and
`Haptics` is the only thing that buzzes. The pad's ticks, the compound
commands, the skill wheel and the match's own rumble all read the same switch,
which is why turning haptics off actually turns all of it off. A phone with no
motor still gets control ticks (the view's haptic feedback) but not rumble.

## OFF controls in the editor

A control switched off is not drawn in play. In the editor it is drawn at
exactly 30% (`ControlOffState.OFF_ALPHA`), with its press glow forced to none,
and with a red `#D32F2F` rounded **OFF** pill under it — the badge is above the
selection ring, so a selected off control still reads as off. The visibility
switch is labelled **On/Off**.

## Latches: the jockey button and the two toggles

A hold ends when the thumb lifts; a latch does not. Three controls latch, and
all three do it through the same state ([TouchClaims]): what is down is kept
beside the control's id, so no finger has to stay on it.

- **JCK (jockey)** is a left trigger with a switch on it: one tap holds LT, the
  next lets go. It draws as LT does, and carries the latch halo while it is on.
  Hidden until the editor turns it on.
- **TG1 / TG2** are custom latches: bind one to any bit with **Change binding**
  and it holds that bit until the second tap. Unbound, they hold nothing, which
  is why they ship hidden and empty.

While a latch is engaged the control keeps a bright ring just outside its rim
and a dot on its shoulder, in one accent colour for every toggle, so "on" looks
the same wherever it is. Everything lets go together: a pause, a reset, a control
being reset, and a stance flip all clear the latches, so a held trigger can never
outlive the pad it was set on and be invisible to the player. The OFF state is
unchanged -- an off control is still drawn at 0.30 with no glow and its badge,
latch or no latch -- and the halo is not drawn for an off control at all, since a
ring is a glow and an off control sends nothing to justify one.

## The two tactical five-way buttons (SW1, SW2)

Same five actions as a spare -- four swipes and a centre tap -- on a control
sized for a thumb, with the title's tactical commands already bound:

| | SW1 (tactics) | SW2 (instructions) |
|---|---|---|
| Up | D-pad up: next preset tactic | LT + D-pad up: attack 1 |
| Down | LB + D-pad down: quick tactics | LT + D-pad down: attack 2 |
| Left | double-tap D-pad down: level down | LT + D-pad left: defence 1 |
| Right | double-tap D-pad up: level up | LT + D-pad right: defence 2 |
| Centre | custom slot (unbound) | custom slot (unbound) |

Quick tactics has no arm of its own in the title's pad, so it rides the nearest
one that does -- LB with the down arm, the modifier family the rest of the pad
uses -- and is one tap in the editor to move. The centre tap is the fifth row of
the swipe-binding dialog (**Centre tap**) and starts empty on both.

They inherit everything the pad's swipes already do: the rim chevrons (faint
where nothing is bound, solid where something is, white under the thumb that is
travelling that way), the tapered trail with its arrowhead, the direction-lock
haptic, and the corridor warning in the editor. They hold nothing themselves, so
nothing is sent until the thumb lifts.

## Input safety and the cost of a frame

**Holds are counted by source, not overwritten.** `HoldMixer` keeps one mask per
source -- `PHYSICAL_TOUCH`, `JOCKEY_TOGGLE`, `TG1_TOGGLE`, `TG2_TOGGLE`,
`MACRO_SEQUENCE` -- and the state pushed to the title is their union. Each source
states the whole truth every frame, so no bit can be lifted by a source that never
held it: SW2's `LT + arm` sequence ending cannot release the LT the jockey has
latched, and two sources on one button both have to let go before it comes up.
A latched spare or button has no source of its own and counts as the player's own
hold, so it ends with the pad.

**Compound sequences are queued, not raced.** `MacroSequencer` is a FIFO of
absolute due times. A flick that arrives while another sequence is still playing
is queued behind it -- the previous one finishes, a 50 ms gap passes, and then the
next begins -- so the title sees the commands in the order they were flicked and
never two half-sequences interleaved on the d-pad. Every beat in a sequence is the
same 50 ms dwell, which is two or three polls at the title's frame rate. One timer
drives the whole queue.

**Swipes on the tactical buttons use their own cone.** `SwipeClassifier` takes a
per-gesture dominance, so SW1/SW2 accept a flick anywhere inside 45 degrees of an
arm (`CARDINAL_DOMINANCE`), while an action button keeps the pad's 1.30 rule and
sends nothing for a diagonal. A leaning tactical flick therefore fires the arm it
leans towards instead of nothing (or, worse, its neighbour).

**Nothing measured twice.** Label widths are measured once per string at a
reference text size and scaled from that ratio, and a control's fitted pixel
geometry is cached against the inputs it was derived from (fraction, size, stance,
window, insets), so a frame does no fitting arithmetic per control. Finished swipe
trails are pruned once a frame rather than only where they happen to be drawn --
a trail whose control is no longer on screen used to keep the animation loop
asking for frames for the rest of the match -- and the d-pad's idle loop stops
outright while an arm is held and once it has faded out.

**Layouts are written atomically.** `LayoutStore.writeAtomically` writes to a
sibling `.part` file, flushes it, and renames it over the target; if the rename is
refused the target is removed before the move is retried, and the temporary never
survives a failure. A crash mid-save leaves the previous layout readable, never a
truncated one.
