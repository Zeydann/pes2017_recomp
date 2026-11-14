#pragma once
// The one-tap skill moves: what each wheel sector sends, in guest frames.
//
// A move is a short script for the *right* stick, and sometimes a short script
// for the buttons as well (the rainbow flick is two R3 clicks, not a stick
// gesture). The stick half is deliberately only the right stick: the console
// gestures on the sheet end in a left-stick direction the player chooses --
// "then Left Stick diagonal" -- and a button cannot know which way the player
// wants to go. So a wheel sector plays the fiddly right-stick half and leaves
// the left stick alone: the player's own thumb supplies the exit, which is
// exactly what it does on a pad, minus the part that needs a stick.
//
// Nothing here drives the left stick, so movement is never frozen by a skill:
// the sequencer takes the right stick and only the right stick.
//
// The frame counts are the ones that look right on paper and are the part that
// must be checked against the title on a device. They are in frames, not
// milliseconds, so they mean the same thing at 30 fps and at 60 (see
// runtime/stick_pulses.h); a move that wants to feel slower needs more frames,
// not a longer timer. `tools/` and PES17_INPUT_SCRIPT can replay any of these
// as `rx+`/`ry+` holds before they are trusted.
//
// The order of kMoves is shared with the app: a wheel sector or a palette
// button stores an index into this table, so a move may be added at the end but
// never reordered or removed.
//
// Two of the feints used to carry the same right-stick script (left then
// right), which made the palette's Double Touch and Body Feint the same press.
// They are now distinct: Double Touch flicks the stick up, Body Feint flicks it
// down. The sheet allows either direction for both, and what really separates
// the two moves is the left-stick exit, which is the player's either way. The
// `leavesExitToPlayer` flag is here so that is written down rather than
// assumed.
#include "stick_pulses.h"

#include <cstdint>

namespace skill
{
    using Step = ::input::StickPulses::Step;

    /**
     * One leg of a button script. The rainbow flick is the only move that
     * needs buttons rather than a stick: 2x R3 has no stick equivalent.
     */
    struct ButtonStep
    {
        std::uint16_t buttons = 0;
        std::uint8_t frames = 1;
    };

    /**
     * Plays a button script one guest frame at a time, the way StickPulses
     * plays a stick script. It owns the buttons it sets for the length of the
     * script and ends by sending one frame of nothing, so the press that
     * started it cannot merge with whatever the thumb does next.
     *
     * The values are the pad's own bits (runtime/android/android.h). kR3 is
     * repeated here, rather than included from there, so this header and its
     * test stay free of Android headers; the test checks the value against the
     * pad the runtime uses.
     */
    class ButtonSeq
    {
    public:
        /** A move is a handful of clicks; more than this is a mistake. */
        static constexpr int kMaxSteps = 8;

        /** The pad bit for a click of the right stick (kRightThumb). */
        static constexpr std::uint16_t kR3 = 0x0080;

        /** Hand over a whole script. It is copied, so the caller's array may go. */
        void Play(const ButtonStep* from, int count)
        {
            if (from == nullptr || count <= 0) { Clear(); return; }
            int taken = 0;
            for (; taken < count && taken < kMaxSteps; taken++)
            {
                ButtonStep step = from[taken];
                // A step that shows for no frames is not a step: it would make
                // a click exist only in the gap between two others.
                if (step.frames == 0) step.frames = 1;
                steps[taken] = step;
            }
            stepCount = taken;
            stepIndex = 0;
            stepLeft = int(steps[0].frames);
            active = true;
        }

        /**
         * One guest frame. Writes the buttons to add and returns whether this
         * script has an opinion about them; when it returns false the caller's
         * own state stands. Called once per poll, from the guest thread.
         */
        bool Sample(std::uint16_t& buttons)
        {
            if (!active)
            {
                buttons = 0;
                return false;
            }
            if (stepIndex >= stepCount)
            {
                // The end: one frame of nothing, so the release edge is as
                // clean as the press that started the sequence.
                active = false;
                buttons = 0;
                return true;
            }
            buttons = steps[stepIndex].buttons;
            if (--stepLeft <= 0)
            {
                stepIndex++;
                stepLeft = stepIndex < stepCount ? int(steps[stepIndex].frames) : 0;
            }
            return true;
        }

        /** Let go now, without draining: the pad was hidden or the mode changed. */
        void Clear()
        {
            active = false;
            stepCount = stepIndex = stepLeft = 0;
        }

        /** Whether a script is still being played out. */
        bool Active() const { return active; }

    private:
        ButtonStep steps[kMaxSteps]{};
        int stepCount = 0;
        int stepIndex = 0;
        int stepLeft = 0;
        bool active = false;
    };

    struct Move
    {
        /** Stable name, for the log and for a test that has to find it. */
        const char* id;
        Step steps[10];
        int count;
        /**
         * True when the gesture continues into a left-stick direction the
         * player picks. The wheel plays the right-stick half and leaves that
         * to the thumb; the flag also lets the editor say so.
         */
        bool leavesExitToPlayer;
        /** The button half, if the move has one. Empty for a stick-only move. */
        ButtonStep buttons[6];
        int buttonCount = 0;
    };

    namespace pads
    {
        // 0.85 of the pad's range, the magnitude runtime/skill_stick.h sends:
        // comfortably above the title's own ~14% deadzone.
        inline constexpr std::int16_t kFull = 27852;
        // The same at 45 degrees.
        inline constexpr std::int16_t kDiag = 19695;
    }

    inline constexpr Move kMoves[] =
    {
        // Double Touch: one flick up, then the window the player's own exit
        // lands in. The sheet allows up or down; Body Feint takes down, so the
        // two buttons are not the same press.
        { "double-touch",
          { { 0, pads::kFull, 2 }, { 0, 0, 3 } }, 2, true },

        // Roulette: right stick all the way round. The one move that is a
        // rotation rather than a sequence of flicks, so it runs continuously.
        { "roulette",
          { {  pads::kFull,        0, 2, 1 },
            {  pads::kDiag,  pads::kDiag, 2, 1 },
            {         0,  pads::kFull, 2, 1 },
            { -pads::kDiag,  pads::kDiag, 2, 1 },
            { -pads::kFull,        0, 2, 1 },
            { -pads::kDiag, -pads::kDiag, 2, 1 },
            {         0, -pads::kFull, 2, 1 },
            {  pads::kDiag, -pads::kDiag, 2, 1 },
            {  pads::kFull,        0, 2, 1 } }, 9, true },

        // Rainbow Flick: two clicks of the right stick, the way the sheet says
        // (2x R3). No stick deflection at all, so a thumb on the glass is
        // never mistaken for part of the move.
        { "rainbow-flick",
          {}, 0, false,
          { { ButtonSeq::kR3, 1 }, { 0, 1 }, { ButtonSeq::kR3, 1 }, { 0, 3 } }, 4 },

        // Drag Back: hold the right stick back, then let go.
        { "drag-back",
          { { 0, -pads::kFull, 10 } }, 1, false },

        // Body Feint: one flick down. Distinct from the Double Touch above, and
        // nothing is expected of the player afterwards beyond steering.
        { "body-feint",
          { { 0, -pads::kFull, 2 }, { 0, 0, 2 } }, 2, true },

        // Step Over: a quarter circle, right, up-right, up, run continuously so
        // the title sees one sweep rather than three separate flicks.
        { "step-over",
          { {  pads::kFull,        0, 2, 1 },
            {  pads::kDiag,  pads::kDiag, 2, 1 },
            {         0,  pads::kFull, 2, 0 } }, 3, true },

        // Ronaldo Chop: one flick to the right; the exit is the player's.
        { "ronaldo-chop",
          { { pads::kFull, 0, 3 } }, 1, true },

        // Elastico: one flick down-right; the reverse of the sheet's example,
        // and the exit is the player's.
        { "elastico",
          { {  pads::kDiag, -pads::kDiag, 3 } }, 1, true },
    };

    inline constexpr int kMoveCount = int(sizeof(kMoves) / sizeof(kMoves[0]));

    /** The move at an index, or null when the app asked for one that is not here. */
    inline const Move* At(int index)
    {
        if (index < 0 || index >= kMoveCount) return nullptr;
        return &kMoves[index];
    }
}
