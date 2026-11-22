package com.pes2017.recomp

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.roundToInt

/**
 * Picking the disc image, or the folder its files were unpacked into.
 *
 * The runtime's installer reads a real path: it opens the image, walks it
 * and copies out of it, which the document-provider world cannot do without
 * copying the whole seven gigabytes somewhere first. So this is a plain
 * directory listing over the file system the app can see, and a button that
 * asks for the permission needed to see more of it.
 *
 * The listing runs off the UI thread. On a phone with thousands of files in
 * shared storage, listFiles is slow enough to drop a frame. The screen shows
 * its loading state while it waits and ignores a result from a folder the
 * player has already left.
 */
class FileBrowserActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var breadcrumbs: LinearLayout
    private lateinit var crumbsScroll: HorizontalScrollView
    private lateinit var entries: ListView
    private lateinit var empty: View
    private lateinit var emptyText: TextView
    private lateinit var loading: View
    private lateinit var grant: Button

    private var folder: File = Environment.getExternalStorageDirectory()
    private var children: List<File> = emptyList()
    private var hasParent = false

    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)
        findViewById<View>(R.id.root).padSystemBars()
        // BACK on the strip along the top; through the dispatcher, so a screen's own back handling
        // (the install's "stop copying?") applies to it too.
        findViewById<View>(R.id.back_button).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        prefs = Prefs(this)

        breadcrumbs = findViewById(R.id.breadcrumbs)
        crumbsScroll = findViewById(R.id.crumbs_scroll)
        entries = findViewById(R.id.entries)
        empty = findViewById(R.id.empty_state)
        emptyText = findViewById(R.id.empty_text)
        loading = findViewById(R.id.loading)
        grant = findViewById(R.id.grant)

        val remembered = prefs.lastBrowsed
        if (remembered.isNotEmpty() && File(remembered).isDirectory) folder = File(remembered)

        findViewById<Button>(R.id.pick_here).setOnClickListener { pick(folder) }
        grant.setOnClickListener { askForStorage() }

        entries.setOnItemClickListener { _, _, position, _ ->
            val chosen = children.getOrNull(position) ?: return@setOnItemClickListener
            if (chosen.isDirectory) {
                folder = chosen
                prefs.lastBrowsed = chosen.absolutePath
                refresh()
            } else {
                pick(chosen)
            }
        }
    }

    private fun refresh() {
        val token = ++generation
        val target = folder
        loading.visibility = View.VISIBLE
        entries.visibility = View.GONE
        empty.visibility = View.GONE
        Thread {
            val listed = try { target.listFiles() } catch (_: Throwable) { null }
            handler.post { if (token == generation) show(target, listed) }
        }.start()
    }

    private fun show(target: File, listed: Array<File>?) {
        val granted = hasStorageAccess()
        grant.visibility = if (granted) View.GONE else View.VISIBLE

        val visible = (listed ?: emptyArray()).filter { !it.name.startsWith(".") }
        // Folders first, then the images; everything else is noise on a
        // screen whose only job is finding one of the two.
        val folders = visible.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        val images = visible.filter { it.isFile && looksLikeImage(it) }.sortedBy { it.name.lowercase() }
        val parent = target.parentFile
        hasParent = parent != null && parent.canRead()
        children = buildList {
            if (hasParent && parent != null) add(parent)
            addAll(folders)
            addAll(images)
        }
        entries.adapter = Adapter()
        renderBreadcrumbs()
        loading.visibility = View.GONE

        // The "up one level" row is navigation, not an entry, so it does not
        // count as something to choose. A folder holding nothing this screen
        // can offer says so instead of showing a list of one row.
        val emptyReason = when {
            listed == null && !granted ->
                R.string.ui_unreadable
            listed == null ->
                R.string.ui_unreadable_closed
            folders.isEmpty() && images.isEmpty() -> R.string.ui_empty
            else -> null
        }
        empty.visibility = if (emptyReason == null) View.GONE else View.VISIBLE
        entries.visibility = if (emptyReason == null) View.VISIBLE else View.GONE
        if (emptyReason != null) emptyText.setText(emptyReason)
    }

    /** The path as tappable pieces, so going up does not mean walking ".." one row at a time. */
    private fun renderBreadcrumbs() {
        breadcrumbs.removeAllViews()
        val root = Environment.getExternalStorageDirectory()
        val crumbs = buildList {
            if (folder == root || folder.absolutePath.startsWith(root.absolutePath + File.separator)) {
                add(getString(R.string.ui_breadcrumb_storage) to root)
                var current = root
                for (part in folder.absolutePath.removePrefix(root.absolutePath).split(File.separator)) {
                    if (part.isEmpty()) continue
                    current = File(current, part)
                    // /storage/emulated/0: the per-user directory is the phone's
                    // own bookkeeping, not a folder anyone named.
                    if (current.parentFile == root && part.all { it.isDigit() }) continue
                    add(part to current)
                }
            } else {
                var current = File(File.separator)
                add(File.separator to current)
                for (part in folder.absolutePath.split(File.separator).filter { it.isNotEmpty() }) {
                    current = File(current, part)
                    add(part to current)
                }
            }
        }
        val dp = resources.displayMetrics.density
        crumbs.forEachIndexed { index, (label, path) ->
            if (index > 0) {
                val separator = ImageView(this).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    imageTintList = ColorStateList.valueOf(tint(R.color.ui_muted))
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    setPadding((3 * dp).roundToInt(), 0, (3 * dp).roundToInt(), 0)
                }
                breadcrumbs.addView(
                    separator,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
            val current = path.absolutePath == folder.absolutePath
            val crumb = TextView(this).apply {
                text = label
                setBackgroundResource(R.drawable.bg_crumb)
                setTextColor(tint(if (current) R.color.ui_text else R.color.ui_muted))
                textSize = 13f
                isAllCaps = false
                gravity = Gravity.CENTER_VERTICAL
                minHeight = (36 * dp).roundToInt()
                setPadding((10 * dp).roundToInt(), 0, (10 * dp).roundToInt(), 0)
                isFocusable = true
                isClickable = !current
                setOnClickListener {
                    if (!current) {
                        folder = path
                        prefs.lastBrowsed = path.absolutePath
                        refresh()
                    }
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            if (index < crumbs.lastIndex) params.marginEnd = (4 * dp).roundToInt()
            breadcrumbs.addView(crumb, params)
        }
        breadcrumbs.post { crumbsScroll.scrollTo(breadcrumbs.width, 0) }
    }

    private fun tint(color: Int): Int = ContextCompat.getColor(this, color)

    private fun looksLikeImage(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".iso") || name.endsWith(".img") || name.endsWith(".bin")
    }

    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    private fun askForStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.storage_explained, packageName))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.allow_storage) { _, _ ->
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    } catch (_: Throwable) {
                        try {
                            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        } catch (_: Throwable) {
                        }
                    }
                }
                .show()
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun pick(file: File) {
        val intent = Intent()
        intent.putExtra(EXTRA_PATH, file.absolutePath)
        setResult(RESULT_OK, intent)
        finish()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount(): Int = children.size
        override fun getItem(position: Int): Any = children[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row: View = convertView ?: layoutInflater.inflate(R.layout.view_file_row, parent, false)
            val file = children[position]
            val icon = row.findViewById<ImageView>(R.id.row_icon)
            val title = row.findViewById<TextView>(R.id.row_title)
            val detail = row.findViewById<TextView>(R.id.row_detail)
            when {
                position == 0 && hasParent -> {
                    icon.setImageResource(R.drawable.ic_arrow_up)
                    icon.imageTintList = ColorStateList.valueOf(tint(R.color.ui_muted))
                    title.setText(R.string.up_one)
                    detail.visibility = View.GONE
                }
                file.isDirectory -> {
                    icon.setImageResource(R.drawable.ic_folder)
                    icon.imageTintList = ColorStateList.valueOf(tint(R.color.ui_green))
                    title.text = file.name
                    detail.visibility = View.GONE
                }
                else -> {
                    icon.setImageResource(R.drawable.ic_disc)
                    icon.imageTintList = ColorStateList.valueOf(tint(R.color.ui_text))
                    title.text = file.name
                    detail.visibility = View.VISIBLE
                    detail.text = android.text.format.Formatter.formatFileSize(
                        this@FileBrowserActivity, file.length(),
                    )
                }
            }
            return row
        }
    }

    override fun onDestroy() {
        generation++
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}
