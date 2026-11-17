#pragma once
// How a touch stick's throw becomes a deflection.
//
// A touchscreen stick has no spring and no travel to feel: the finger is either
// on the glass or not, so linear-from-zero reads as a jump from standing to a
// jog the moment the thumb moves. A player who wants that softened has two
// knobs, both off by default because the title's own mapping is the reference:
//
//   * a radial deadzone -- the middle of the throw sends nothing at all, so a
//     resting thumb cannot nudge the player off the spot;
//   * a curve on what is left -- below 1 the stick reaches the edge sooner,
//     above 1 it is finer around the middle, where a walking pace lives.
//
// The title has a deadzone of its own (kGuestFloor), so a shaped throw is lifted
// to it: a host deadzone stacked under it would be dead travel the thumb feels
// as a stick that only answers halfway out.
//
// Both work on the vector, never on an axis. Per-axis shaping bends diagonals
// (pushing up-right ends up more right than up) and turns the deadzone into a
// square, which is not the stick the socket is drawn as.
#include <algorithm>
#include <cmath>

namespace stick
{
    // The title's own thumbstick deadzone, as a share of full deflection: no
    // value below it moves the player. A shaped throw starts here instead of at
    // zero, so the host's deadzone and the title's are never added together.
    inline constexpr float kGuestFloor = 0.14f;

    struct Shape
    {
        float deadzone = 0.0f;    // share of the throw that sends nothing
        float exponent = 1.0f;    // curve on what is left of it
        float range = 1.0f;       // output scale at full deflection
        float floor = 0.0f;       // where a shaped throw starts (kGuestFloor)
    };

    // The shape Apply uses: anything outside the useful range is pulled in, so a
    // slider or a recorded macro cannot ask for a stick that never reaches its
    // edge.
    inline Shape Sanitize(Shape shape)
    {
        shape.deadzone = std::clamp(shape.deadzone, 0.0f, 0.9f);
        shape.exponent = std::clamp(shape.exponent, 0.25f, 4.0f);
        shape.range = std::clamp(shape.range, 0.0f, 1.5f);
        shape.floor = std::clamp(shape.floor, 0.0f, 0.5f);
        return shape;
    }

    // Shapes (x, y) in place. False when the throw is inside the deadzone, in
    // which case both are set to exactly zero -- a residual of 0.01 rad is still
    // a nudge the title acts on.
    inline bool Apply(float& x, float& y, const Shape& shape)
    {
        const Shape s = Sanitize(shape);
        const float magnitude = std::sqrt(x * x + y * y);
        if (!(magnitude > s.deadzone))
        {
            x = y = 0.0f;
            return false;
        }

        // Out to the rim at one, then what the curve does with the rest of the
        // throw: the deadzone is not left behind at the top, so full speed is
        // still reachable by a thumb that reaches the rim. The floor starts what
        // is left at the first value the title acts on, so the two deadzones are
        // never added together.
        const float reach = std::min(magnitude, 1.0f);
        float shaped;
        if (s.deadzone > 0.0f || s.exponent != 1.0f)
        {
            const float t = std::max((reach - s.deadzone) / (1.0f - s.deadzone), 0.0f);
            shaped = s.floor + std::pow(t, s.exponent) * (1.0f - s.floor);
        }
        else
        {
            // No deadzone and no curve is the console's own mapping: the thumb's
            // throw is the deflection, and the title's floor is the only
            // deadzone in the path.
            shaped = reach;
        }
        const float out = shaped * s.range;
        const float scale = out / magnitude;
        x *= scale;
        y *= scale;

        // Sub-pixel wobble at the deadzone's edge is not movement.
        if (std::sqrt(x * x + y * y) < 0.002f)
        {
            x = y = 0.0f;
            return false;
        }
        return true;
    }
}
