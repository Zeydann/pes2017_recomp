package org.savagewind.port

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.ScrollView
import android.widget.TextView

/**
 * Host shell for the Android port.
 *
 * This activity exists to prove the Gradle -> CMake -> NDK path and to give the
 * runtime somewhere to surface status once it is linked. It does not run a game
 * and must not be presented as the port.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            this.text = buildString {
                appendLine("Savage Wind — Android host shell")
                appendLine()
                appendLine(nativeBuildInfo())
                appendLine()
                appendLine("This build contains no game files and no recompiled game code.")
                appendLine("The native runtime is not linked yet; see PORT-HANDOFF.md.")
            }
            textSize = 14f
            gravity = Gravity.START
            setPadding(48, 48, 48, 48)
        }

        setContentView(ScrollView(this).apply { addView(text) })
    }

    private external fun nativeBuildInfo(): String

    companion object {
        init {
            System.loadLibrary("savagewind_host")
        }
    }
}
