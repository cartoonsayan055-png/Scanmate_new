package com.synthbyte.scanmate.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.synthbyte.scanmate.data.Document

object WidgetStateStore {
    private const val PREFS_NAME = "scanmate_widget_state"
    private const val KEY_TITLE = "recent_title"
    private const val KEY_SUBTITLE = "recent_subtitle"

    fun publishRecentDocument(context: Context, document: Document?) {
        val title = document?.title?.takeIf { it.isNotBlank() } ?: "Recent documents"
        val subtitle = document?.let {
            listOf(it.workspace.ifBlank { "Inbox" }, it.category.ifBlank { "General" })
                .filter { value -> value.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "Tap to open ScanMate" }
        } ?: "Open ScanMate to scan or import"

        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_SUBTITLE, subtitle)
                .apply()
            requestRecentWidgetRefresh(context.applicationContext)
        }
    }

    fun recentDocumentText(context: Context): Pair<String, String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return Pair(
            prefs.getString(KEY_TITLE, null) ?: "Recent documents",
            prefs.getString(KEY_SUBTITLE, null) ?: "Open ScanMate to scan or import"
        )
    }

    private fun requestRecentWidgetRefresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, RecentDocumentWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        val intent = Intent(context, RecentDocumentWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }
}
