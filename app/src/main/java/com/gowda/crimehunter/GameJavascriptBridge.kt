package com.gowda.crimehunter

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface

class GameJavascriptBridge(
    private val activity: Activity,
) {
    @JavascriptInterface
    fun openExternalUrl(rawUrl: String?) {
        val url = rawUrl?.takeIf { it.isNotBlank() } ?: return
        activity.runOnUiThread {
            runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }.onFailure {
                Log.w(TAG, "Could not open external URL: $url", it)
            }
        }
    }

    @JavascriptInterface
    fun shareText(title: String?, text: String?, url: String?) {
        val payload = buildString {
            if (!text.isNullOrBlank()) {
                append(text.trim())
            }
            if (!url.isNullOrBlank()) {
                if (isNotEmpty()) append("\n")
                append(url.trim())
            }
        }.ifBlank { return }

        activity.runOnUiThread {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title?.trim().orEmpty())
                putExtra(Intent.EXTRA_TEXT, payload)
            }
            activity.startActivity(Intent.createChooser(shareIntent, title ?: "Share"))
        }
    }

    @JavascriptInterface
    fun log(message: String?) {
        if (!message.isNullOrBlank()) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "GameBridge"
    }
}

