package me.timschneeberger.rootlessjamesdsp.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import me.timschneeberger.rootlessjamesdsp.service.NotificationListenerService
import me.timschneeberger.rootlessjamesdsp.utils.ContextExtensions.getAppName


object ApplicationUtils {
    fun describe(ctx: Context): String {
        return "package=${ctx.packageName}; app_name=${ctx.getAppName()}; label=${ctx.getAppName()}"
    }

    fun getIntentForNotificationAccess(packageName: String, notificationAccessServiceClass: Class<out NotificationListenerService>): Intent =
        getIntentForNotificationAccess(packageName, notificationAccessServiceClass.name)

    private fun getIntentForNotificationAccess(packageName: String, notificationAccessServiceClassName: String): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(packageName, notificationAccessServiceClassName).flattenToString())
        }
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        val value = "$packageName/$notificationAccessServiceClassName"
        val key = ":settings:fragment_args_key"
        intent.putExtra(key, value)
        intent.putExtra(":settings:show_fragment_args", Bundle().also { it.putString(key, value) })
        return intent
    }
}