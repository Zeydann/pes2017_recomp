package com.pes2017.recomp

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * The setup screens paint their own background from the top of the window:
 * Android 15 and later hand an app the whole screen. So the system bars are
 * added as padding instead of being left to sit on top of the back strip and
 * the last row of the list.
 */
fun View.padSystemBars() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
