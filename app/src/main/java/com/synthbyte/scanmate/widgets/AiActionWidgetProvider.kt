package com.synthbyte.scanmate.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.synthbyte.scanmate.R
import com.synthbyte.scanmate.ui.navigation.Routes

class AiActionWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_ai_action)
            views.setOnClickPendingIntent(R.id.widget_root, routeIntent(context, Routes.AI_ASSISTANT, 102))
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
