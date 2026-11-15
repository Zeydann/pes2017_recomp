package com.pes2017.recomp

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The app's only way to buzz.
 *
 * Before this there were two: the pad called the view's own haptic feedback and
 * the match drove a [Vibrator], both reading `Prefs.vibration` separately. That
 * is two switches that look like one, and the bug it invites -- a player turns
 * vibration off and the pad still ticks -- is the sort that survives review.
 * Every call site now goes through [HapticsPolicy], so one switch really does
 * govern everything.
 *
 * The motor is looked up once: `VibratorManager` on Android 12 and later, the
 * older service before that, and a phone that refuses is remembered as having
 * no motor rather than being asked on every goal.
 */
class Haptics(context: Context, private val prefs: Prefs) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.applicationContext
                .getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Throwable) {
        null
    }

    /** Whether the phone can actually buzz. A phone without one still ticks. */
    val hasVibrator: Boolean
        get() = try {
            vibrator?.hasVibrator() == true
        } catch (_: Throwable) {
            false
        }

    /** Whether [event] may happen now, by the player's switch and the hardware. */
    fun allowed(event: HapticEvent): Boolean =
        HapticsPolicy.allows(prefs.haptics, hasVibrator, event)

    /**
     * A control's own tick. It goes through the view rather than the motor so it
     * follows the same path as every other control press in the app -- and so a
     * phone with no motor still answers, which is how several devices emulate a
     * key press.
     */
    fun tick(view: View?, event: HapticEvent = HapticEvent.TAP) {
        if (!allowed(event)) return
        if (view == null) return
        try {
            view.performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
        } catch (_: Throwable) {
            // A phone that refuses to buzz is not a reason to stop playing.
        }
    }

    /**
     * The title's own feedback: a goal, a full-time whistle. Long enough to feel,
     * short enough not to be mistaken for a notification.
     */
    fun match(durationMs: Long = 160L, amplitude: Int = 180) {
        if (!allowed(HapticEvent.MATCH)) return
        val device = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val level = if (device.hasAmplitudeControl()) {
                    amplitude.coerceIn(1, 255)
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                device.vibrate(VibrationEffect.createOneShot(durationMs, level))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(durationMs)
            }
        } catch (_: Throwable) {
        }
    }
}
