package me.timschneeberger.rootlessjamesdsp.utils

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.SparseArray
import androidx.annotation.RequiresApi
import androidx.core.util.isEmpty
import androidx.core.util.isNotEmpty
import androidx.core.util.valueIterator
import me.timschneeberger.rootlessjamesdsp.BuildConfig
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.activity.AppCompatibilityActivity
import me.timschneeberger.rootlessjamesdsp.activity.MainActivity
import me.timschneeberger.rootlessjamesdsp.model.root.EffectSessionEntry
import me.timschneeberger.rootlessjamesdsp.model.rootless.AudioSessionEntry
import me.timschneeberger.rootlessjamesdsp.service.RootlessAudioProcessorService
import me.timschneeberger.rootlessjamesdsp.utils.ContextExtensions.getAppName
import me.timschneeberger.rootlessjamesdsp.utils.ContextExtensions.getAppNameFromUid


object ServiceNotificationHelper {
    private fun createNotificationBuilder(context: Context, channel: String): Notification.Builder {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(context, channel)
        else
            Notification.Builder(context)
    }

    fun pushPermissionPromptNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        intent.putExtra(MainActivity.EXTRA_FORCE_SHOW_CAPTURE_PROMPT, true)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        val contentIntent =
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = createNotificationBuilder(context, Constants.CHANNEL_ID_PERMISSION_PROMPT)
            .setContentTitle(context.getString(R.string.notification_request_permission_title))
            .setContentText(context.getString(R.string.notification_request_permission))
            .setSmallIcon(R.drawable.ic_tune_vertical_variant_24dp)
            .setContentIntent(contentIntent)
            .build()

        SystemServices.get(context, NotificationManager::class.java)
            .notify(Constants.NOTIFICATION_ID_PERMISSION_PROMPT, notification)
    }

    fun pushServiceNotification(context: Context, sessions: Array<AudioSessionEntry>?) {
        val notification = createServiceNotification(context, sessions)
        SystemServices.get(context, NotificationManager::class.java)
            .notify(Constants.NOTIFICATION_ID_SERVICE, notification)
    }

    fun pushServiceNotificationRoot(context: Context, sessions: SparseArray<EffectSessionEntry>?) {
        val notification = createServiceNotificationRoot(context, sessions)
        SystemServices.get(context, NotificationManager::class.java)
            .notify(Constants.NOTIFICATION_ID_SERVICE, notification)
    }

    fun pushServiceNotificationLegacy(context: Context) {
        val notification = createServiceNotificationLegacy(context)
        SystemServices.get(context, NotificationManager::class.java)
            .notify(Constants.NOTIFICATION_ID_SERVICE, notification)
    }

    fun createServiceNotificationLegacy(context: Context): Notification {
        val enabled = context
            .getSharedPreferences(Constants.PREF_APP, Context.MODE_PRIVATE)
            .getBoolean(context.getString(R.string.key_powered_on), true)
        return createServiceNotification(
            context,
            context.getString(
                if(enabled) R.string.notification_processing_title
                else R.string.notification_processing_disabled_title
            ),
            context.getString(R.string.notification_processing_legacy)
        )
    }

    private fun createServiceNotificationRoot(context: Context, sessions: SparseArray<EffectSessionEntry>?): Notification {
        val apps = sessions?.valueIterator()?.asSequence()?.joinToString(", ") {
            if(it.packageName == null)
                return@joinToString "?"
            context.getAppName(it.packageName!!) ?: it.packageName as CharSequence
        }

        val enabled = context
            .getSharedPreferences(Constants.PREF_APP, Context.MODE_PRIVATE)
            .getBoolean(context.getString(R.string.key_powered_on), true)

        return createServiceNotification(context,
            context.getString(
                if(enabled) R.string.notification_processing_title
                else R.string.notification_processing_disabled_title
            ), when {
                sessions?.isNotEmpty() == true -> context.getString(R.string.notification_processing, apps)
                sessions?.isEmpty() == true -> context.getString(R.string.notification_idle)
                else -> context.getString(R.string.notification_waiting)
            })
    }

    fun createServiceNotification(context: Context, sessions: Array<AudioSessionEntry>?): Notification {
        val apps = sessions?.distinct()?.joinToString(", ") {
            context.getAppNameFromUid(it.uid) ?: it.packageName
        }

        return createServiceNotification(context,
            context.getAppName(),
            when {
                sessions?.isNotEmpty() == true -> context.getString(R.string.notification_processing, apps)
                sessions?.isEmpty() == true -> context.getString(R.string.notification_idle)
                else -> context.getString(R.string.notification_waiting)
            })
    }

    private fun createServiceNotification(context: Context, title: String, message: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

        val contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = createNotificationBuilder(context, Constants.CHANNEL_ID_SERVICE)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_tune_vertical_variant_24dp)
            .setContentIntent(contentIntent)
            .setOngoing(true)

        if(BuildConfig.ROOTLESS)
            builder.addAction(createStopAction(context))

        return builder.build()
    }


    fun pushSessionLossNotification(context: Context, mediaProjectionStartIntent: Intent?) {
        val intent = Intent(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

        val contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = createNotificationBuilder(context, Constants.CHANNEL_ID_SESSION_LOSS)
            .setContentTitle(context.getString(R.string.session_control_loss_notification_title))
            .setContentText(context.getString(R.string.session_control_loss_notification))
            .setSmallIcon(R.drawable.ic_baseline_warning_24dp)
            .addAction(createRetryAction(context, mediaProjectionStartIntent))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        SystemServices.get(context, NotificationManager::class.java)
            .notify(Constants.NOTIFICATION_ID_SESSION_LOSS, notification)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun pushAppIssueNotification(context: Context, mediaProjectionStartIntent: Intent?, data: AudioSessionEntry) {
        val intent = createAppTroubleshootIntent(context, mediaProjectionStartIntent, data, directLaunch = false)
        val contentIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = createNotificationBuilder(context, Constants.CHANNEL_ID_APP_INCOMPATIBILITY)
            .setContentTitle(context.getString(R.string.session_app_compat_notification_title))
            .setContentText(context.getString(R.string.session_app_compat_notification))
            .setSmallIcon(R.drawable.ic_baseline_warning_24dp)
            .addAction(createAppTroubleshootAction(context, mediaProjectionStartIntent, data))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        SystemServices.get(context, NotificationManager::class.java)
            .notify(Constants.NOTIFICATION_ID_APP_INCOMPATIBILITY, notification)
    }

    private fun createStopAction(context: Context): Notification.Action {
        val stopIntent = createStopIntent(context)
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIcon = Icon.createWithResource(context, R.drawable.ic_close_24dp)
        val stopString = context.getString(R.string.action_stop)
        val actionBuilder = Notification.Action.Builder(stopIcon, stopString, stopPendingIntent)
        return actionBuilder.build()
    }

    private fun createRetryAction(context: Context, mediaProjectionStartIntent: Intent?): Notification.Action? {
        mediaProjectionStartIntent ?: return null
        val retryIntent = createStartIntent(context, mediaProjectionStartIntent)
        val retryPendingIntent = PendingIntent.getService(
            context,
            0,
            retryIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val retryIcon = Icon.createWithResource(context, R.drawable.ic_baseline_refresh_24dp)
        val retryString = context.getString(R.string.action_retry)
        val actionBuilder = Notification.Action.Builder(retryIcon, retryString, retryPendingIntent)
        return actionBuilder.build()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createAppTroubleshootAction(context: Context, mediaProjectionStartIntent: Intent?, data: AudioSessionEntry): Notification.Action? {
        mediaProjectionStartIntent ?: return null
        val fixIntent = PendingIntent.getService(
            context,
            0,
            createAppTroubleshootIntent(context, mediaProjectionStartIntent, data, directLaunch = false),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val fixIcon = Icon.createWithResource(context, R.drawable.ic_twotone_chevron_right_24dp)
        val fixString = context.getString(R.string.action_fix)
        val actionBuilder = Notification.Action.Builder(fixIcon, fixString, fixIntent)
        return actionBuilder.build()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun createAppTroubleshootIntent(ctx: Context, mediaProjectionData: Intent?, data: AudioSessionEntry, directLaunch: Boolean): Intent {
        val intent = Intent(ctx, AppCompatibilityActivity::class.java)
        intent.action = RootlessAudioProcessorService.ACTION_START
        intent.putExtra(RootlessAudioProcessorService.EXTRA_MEDIA_PROJECTION_DATA, mediaProjectionData)
        intent.putExtra(RootlessAudioProcessorService.EXTRA_APP_UID, data.uid)
        intent.putExtra(RootlessAudioProcessorService.EXTRA_APP_COMPAT_INTERNAL_CALL, directLaunch)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent
    }

    fun createStopIntent(ctx: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && BuildConfig.ROOTLESS) {
            with(Intent(ctx, RootlessAudioProcessorService::class.java)) {
                action = RootlessAudioProcessorService.ACTION_STOP
                this
            }
        }
        else
            throw IllegalStateException()
    }

    fun createStartIntent(ctx: Context, mediaProjectionData: Intent? = null): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && BuildConfig.ROOTLESS) {
            with(Intent(ctx, RootlessAudioProcessorService::class.java)) {
                action = RootlessAudioProcessorService.ACTION_START
                putExtra(RootlessAudioProcessorService.EXTRA_MEDIA_PROJECTION_DATA, mediaProjectionData)
                this
            }
        }
        else
            throw IllegalStateException()
    }
}