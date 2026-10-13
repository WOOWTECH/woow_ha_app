package io.homeassistant.companion.android.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.common.util.cancel
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.notifications.MessagingManager.Companion.KEY_TEXT_REPLY
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val FIRE_EVENT = "FIRE_EVENT"
        const val EXTRA_NOTIFICATION_TAG = "EXTRA_NOTIFICATION_TAG"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
        const val EXTRA_NOTIFICATION_DB = "EXTRA_NOTIFICATION_DB"
        const val EXTRA_NOTIFICATION_ACTION = "EXTRA_ACTION_KEY"
    }

    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job())

    @Inject
    lateinit var serverManager: ServerManager

    @Inject
    lateinit var messagingManager: MessagingManager

    @Inject
    lateinit var notificationDao: NotificationDao

    override fun onReceive(context: Context, intent: Intent) {
        val notificationAction = IntentCompat.getParcelableExtra(
            intent,
            EXTRA_NOTIFICATION_ACTION,
            NotificationAction::class.java,
        )

        if (notificationAction == null) {
            Timber.e("Failed to get notification action.")
            return
        }

        val tag = intent.getStringExtra(EXTRA_NOTIFICATION_TAG)
        val messageId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val databaseId = intent.getLongExtra(EXTRA_NOTIFICATION_DB, 0)

        val isReply = notificationAction.key == "REPLY" || notificationAction.behavior?.lowercase() == "textinput"
        var replyText: String? = null

        val onComplete: () -> Unit = {
            if (isReply && !replyText.isNullOrBlank()) {
                val replies = notificationAction.data.entries
                    .filter { it.key.startsWith(MessagingManager.SOURCE_REPLY_HISTORY) }
                    .sortedBy { it.key.substringAfter(MessagingManager.SOURCE_REPLY_HISTORY).toInt() }
                    .map { it.value } + replyText
                messagingManager.handleMessage(
                    notificationAction.data + replies
                        .takeLast(3)
                        .mapIndexed { index, text ->
                            "${MessagingManager.SOURCE_REPLY_HISTORY}$index" to text!!
                        }
                        .toMap(),
                    "${MessagingManager.SOURCE_REPLY}$databaseId",
                )
            } else {
                val notificationManagerCompat = NotificationManagerCompat.from(context)
                notificationManagerCompat.cancel(
                    tag,
                    messageId,
                    true,
                )
            }
        }
        val onFailure: () -> Unit = {
            Handler(context.mainLooper).post {
                Toast.makeText(context, commonR.string.event_error, Toast.LENGTH_LONG).show()
            }
        }

        if (isReply) {
            replyText = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_TEXT_REPLY).toString()
            notificationAction.data += Pair("reply_text", replyText)
        }

        when (intent.action) {
            FIRE_EVENT -> {
                ioScope.launch {
                    val serverId = resolveNotificationActionServerId(
                        notificationDao = notificationDao,
                        serverManager = serverManager,
                        databaseId = databaseId,
                        action = notificationAction,
                    )
                    fireEvent(notificationAction, serverId, onComplete, onFailure)
                }
            }
        }
    }

    private suspend fun fireEvent(
        action: NotificationAction,
        serverId: Int,
        onComplete: () -> Unit,
        onFailure: () -> Unit,
    ) {
        try {
            serverManager.integrationRepository(serverId).fireEvent(
                "mobile_app_notification_action",
                notificationActionEventData(action),
            )
            onComplete()
        } catch (e: Exception) {
            Timber.e(e, "Unable to fire event.")
            onFailure()
        }
    }
}

/** Resolves the action's server even when its notification history row has been evicted. */
internal suspend fun resolveNotificationActionServerId(
    notificationDao: NotificationDao,
    serverManager: ServerManager,
    databaseId: Long,
    action: NotificationAction,
): Int = notificationDao.get(databaseId.toInt())?.serverId
    ?: action.data[NotificationData.WEBHOOK_ID]?.let { webhookId ->
        serverManager.getServer(webhookId = webhookId)?.id
    }
    ?: ServerManager.SERVER_ID_ACTIVE

/** Keeps action data and replies intact while excluding local reply history from the event. */
internal fun notificationActionEventData(action: NotificationAction): Map<String, String> = action.data
    .filter { !it.key.startsWith(MessagingManager.SOURCE_REPLY_HISTORY) }
    .plus(Pair("action", action.key))
