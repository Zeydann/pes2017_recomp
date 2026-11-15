package com.pes2017.recomp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The default pad's spacing, swipes and wheel, as numbers rather than as pixels
 * on a phone: a change that crowds a neighbour, shortens a swipe track or parks
 * the skill ring on a button fails here.
 *
 * The units are the layout's own dp space (ControlLayout.defaults is laid out
 * for 800 x 360), so a phone's density cancels out. A control's hit area is its
 * radius plus the 6 dp touch slack on each side, which is why "clearance" here
 * subtracts 12 dp from the centre distance.
 */
class TouchGeometryTest {

    private fun layout() = ControlLayout.defaults(800f, 360f)

    private fun x(element: ControlElement) = element.xFrac * 800f
    private fun y(element: ControlElement) = element.yFrac * 360f
    private fun radius(element: ControlElement) = element.sizeDp / 2f

    private fun clearance(a: ControlElement, b: ControlElement) =
        hypot(x(a) - x(b), y(a) - y(b)) - radius(a) - radius(b) - 12f

    @Test
    fun theActionDiamondHasRoomForAThumbBetweenEveryPair() {
        val layout = layout()
        val ids = listOf("a", "b", "x", "y")
        for (first in ids.indices) {
            for (second in first + 1 until ids.size) {
                val a = layout.find(ids[first])!!
                val b = layout.find(ids[second])!!
                assertTrue(
                    "${ids[first]} and ${ids[second]} are only ${clearance(a, b)} dp apart",
                    clearance(a, b) >= 6f,
                )
            }
        }
        // 64 dp across, as the retune promised.
        assertEquals(64f, layout.find("a")!!.sizeDp, 0.01f)
    }

    @Test
    fun everyDefaultSwipeHasACorridorWithNothingInIt() {
        val layout = layout()
        for (element in layout.elements) {
            for (direction in GestureCorridor.DIRECTIONS) {
                if (element.swipeBit(direction) == 0) continue
                val issues = GestureCorridor.issues(element, direction, layout)
                assertTrue(
                    "${element.id} $direction runs into $issues",
                    issues.isEmpty(),
                )
            }
        }
    }

    @Test
    fun theSkillWheelRingClearsTheActionControls() {
        val layout = layout()
        val wheel = layout.find("trick")!!
        val ring = radius(wheel) * 1.95f
        for (other in layout.elements) {
            if (other === wheel) continue
            if (other.kind == ControlKind.LOOK) continue
            // The ring is drawn under the thumb that opened it; the skill stick
            // and the menu buttons are not things a thumb reaches for while
            // that hold is in progress, so only the action controls are checked.
            if (other.kind == ControlKind.STICK) continue
            if (other.id == "start" || other.id == "back") continue
            if (!other.visible) continue
            if (!other.shownIn(GamePhase.MATCH, PadStance.ATTACK)) continue
            val gap = hypot(x(wheel) - x(other), y(wheel) - y(other)) -
                ring - radius(other) - 6f
            assertTrue("the wheel ring reaches ${other.id}", gap > 0f)
        }
    }

    @Test
    fun theDpadClearsEveryControlOnItsPad() {
        val layout = layout()
        val dpad = layout.find("dpad") ?: error("the default pad has a d-pad")
        for (other in layout.elements) {
            if (other === dpad) continue
            if (other.kind == ControlKind.LOOK) continue
            if (!other.visible) continue
            if (!other.shownIn(GamePhase.MATCH, PadStance.ATTACK)) continue
            assertTrue(
                "the d-pad overlaps ${other.id}",
                clearance(dpad, other) > 0f,
            )
        }
    }

    @Test
    fun theStanceChipAndSwitchStayInsideTheGestureBarInset() {
        val layout = layout()
        for (id in listOf("stance", "switch", "a")) {
            val element = layout.find(id)!!
            assertTrue(
                "$id reaches into the bottom inset",
                y(element) + radius(element) <= 360f - 8f,
            )
        }
    }
}
