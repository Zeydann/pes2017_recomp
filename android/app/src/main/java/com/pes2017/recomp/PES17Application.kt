package com.pes2017.recomp

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installed before anything else in either process, for one reason: so that
 * a crash says what it was doing.
 *
 * An app that dies on the launcher screen with nothing written down is a day
 * lost to guessing, and the interesting failures here happen on a phone that
 * is not attached to a computer. So the last exception is kept in a file,
 * and the launcher shows it on the next start.
 */
class PES17Application : Application() {

    override fun onCreate() {
        super.onCreate()
        // The language is handed to AppCompat before any screen is built, so the
        // very first window is already in the saved language. English is written
        // in on a fresh install, whatever language the phone itself uses.
        try {
            Localization.ensure(Prefs(this))
        } catch (_: Throwable) {
        }
        // Arabic's first letters: a letter-spaced label loses its first glyph
        // at the head of a right-to-left line, so every screen that is built
        // drops the tracking as soon as it is up (Localization.dropRtlTracking).
        // Registered before any activity exists, so the first screen gets it too.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val decor = activity.window?.decorView ?: return
                // After the first layout pass, when the labels exist.
                decor.post { activity.dropRtlTracking() }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        Trail.install(this)
        Trail.note(this, "app: process started")
    }
}

/**
 * Two small files in the app's own folder:
 *
 *   last-crash.txt  the stack trace of whatever killed the app last time
 *   boot-trail.txt  what the app was in the middle of, a line at a time
 *
 * The trail matters for the failures an exception handler never sees. A
 * native crash inside System.loadLibrary takes the process down without
 * unwinding anything, and the only evidence left is the last line written
 * before it: "loading the runtime library". That is the difference between
 * a bug report and a shrug.
 */
object Trail {

    private const val LIMIT = 64 * 1024

    /**
     * The launcher and the run are separate processes and both write here.
     * Each keeps its own pair of files, or one clearing its trail on a
     * successful start would throw away the other's evidence of a crash.
     */
    private val suffix: String by lazy {
        val name = try {
            File("/proc/self/cmdline").readText().trim('\u0000')
        } catch (_: Throwable) {
            ""
        }
        if (name.endsWith(":game")) "game" else "launcher"
    }

    private val CRASH: String get() = "last-crash-$suffix.txt"
    private val TRAIL: String get() = "boot-trail-$suffix.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))
                val text = buildString {
                    append(stamp()).append('\n')
                    append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                    append(", Android ").append(Build.VERSION.RELEASE)
                    append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                    append("thread: ").append(thread.name).append('\n')
                    append("what it was doing: ").append(read(context, TRAIL).trim()).append("\n\n")
                    append(writer.toString())
                }
                File(context.filesDir, CRASH).writeText(text)
            } catch (_: Throwable) {
                // Nothing useful is possible here; do not make it worse.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** One line, appended, flushed at once: the process may not survive. */
    fun note(context: Context, what: String) {
        try {
            val file = File(context.filesDir, TRAIL)
            if (file.length() > LIMIT) file.delete()
            file.appendText("${stamp()}  $what\n")
        } catch (_: Throwable) {
        }
    }

    /** Called once things are running: the trail has served its purpose. */
    fun clear(context: Context) {
        try {
            File(context.filesDir, TRAIL).delete()
        } catch (_: Throwable) {
        }
    }

    fun lastCrash(context: Context): String = read(context, CRASH)

    fun forgetCrash(context: Context) {
        try {
            File(context.filesDir, CRASH).delete()
        } catch (_: Throwable) {
        }
    }

    fun trail(context: Context): String = read(context, TRAIL)

    private fun read(context: Context, name: String): String = try {
        val file = File(context.filesDir, name)
        if (file.exists()) file.readText() else ""
    } catch (_: Throwable) {
        ""
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
}
