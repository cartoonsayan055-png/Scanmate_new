package com.synthbyte.scanmate.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.synthbyte.scanmate.R
import com.synthbyte.scanmate.ui.navigation.Routes

class RecentDocumentWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val (title, subtitle) = WidgetStateStore.recentDocumentText(context)
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_recent_document).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_subtitle, subtitle)
                setOnClickPendingIntent(R.id.widget_root, routeIntent(context, Routes.HOME, 103))
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
