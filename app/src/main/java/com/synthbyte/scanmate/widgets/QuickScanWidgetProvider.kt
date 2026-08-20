package com.synthbyte.scanmate.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.synthbyte.scanmate.MainActivity
import com.synthbyte.scanmate.R
import com.synthbyte.scanmate.ui.navigation.Routes

class QuickScanWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_quick_scan)
            views.setOnClickPendingIntent(R.id.widget_root, routeIntent(context, Routes.CAMERA_SCAN, 101))
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}

internal fun routeIntent(context: Context, route: String, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(MainActivity.EXTRA_SHORTCUT_ROUTE, route)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
