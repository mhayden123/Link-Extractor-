package io.github.mhayden123.linkcleaner

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * Invisible (translucent) activity that only shows a dialog.
 *
 * - Launched from the share sheet: cleans the shared link and shows a preview.
 * - Launched from the app icon: asks you to paste a link, then shows the preview.
 */
class ShareActivity : Activity() {

    private val dialogTheme: Int
        get() {
            val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return if (night == Configuration.UI_MODE_NIGHT_YES) {
                android.R.style.Theme_DeviceDefault_Dialog_Alert
            } else {
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shared = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else {
            null
        }

        if (shared == null) {
            askForLink()
        } else {
            showPreview(shared)
        }
    }

    private fun askForLink() {
        val input = EditText(this).apply {
            hint = getString(R.string.paste_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this, dialogTheme)
            .setTitle(R.string.app_name)
            .setView(container)
            .setPositiveButton(R.string.clean) { _, _ -> showPreview(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showPreview(text: String) {
        val result = UrlCleaner.clean(text)
        if (result == null) {
            Toast.makeText(this, R.string.no_link_found, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_preview, null)
        view.findViewById<TextView>(R.id.original).text = result.original
        view.findViewById<TextView>(R.id.cleaned).text = result.cleaned
        view.findViewById<TextView>(R.id.changes).apply {
            if (result.changes.isEmpty()) {
                setText(R.string.already_clean)
            } else {
                text = result.changes.joinToString("\n") { "• $it" }
            }
            visibility = View.VISIBLE
        }

        // We set each button's click handler after show() so that a button can
        // decide whether to close the dialog.
        val dialog = AlertDialog.Builder(this, dialogTheme)
            .setTitle(R.string.app_name)
            .setView(view)
            .setPositiveButton(R.string.copy, null)
            .setNegativeButton(R.string.share, null)
            .setNeutralButton(R.string.open, null)
            .setOnDismissListener { finish() }
            .create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            copy(result.cleaned)
            dialog.dismiss()
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, result.cleaned)
            }
            startActivity(Intent.createChooser(send, getString(R.string.share)))
            dialog.dismiss()
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.cleaned)))
                dialog.dismiss()
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copy(url: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clip_label), url))
        // Android 13 (API 33)+ shows its own "Copied" confirmation; avoid a duplicate.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }
}
