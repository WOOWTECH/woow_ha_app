package io.homeassistant.companion.android.notifications

import io.homeassistant.companion.android.common.data.servers.ServerManager
import io.homeassistant.companion.android.common.notifications.NotificationData
import io.homeassistant.companion.android.database.notification.NotificationDao
import io.homeassistant.companion.android.database.notification.NotificationItem
import io.homeassistant.companion.android.database.server.Server
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class NotificationActionReceiverTest {

    private val notificationDao = mockk<NotificationDao>()
    private val serverManager = mockk<ServerManager>()

    @Test
    fun `Given retained history when resolving action then row server takes precedence over webhook and active server`() = runTest {
        coEvery { notificationDao.get(FIXTURE_DATABASE_ID.toInt()) } returns notification(serverId = 7)

        assertEquals(7, resolveServer(action()))

        coVerify(exactly = 1) { notificationDao.get(FIXTURE_DATABASE_ID.toInt()) }
        confirmVerified(notificationDao, serverManager)
    }

    @Test
    fun `Given evicted history and another active server when resolving action then webhook server wins`() = runTest {
        coEvery { notificationDao.get(FIXTURE_DATABASE_ID.toInt()) } returns null
        coEvery { serverManager.getServer(webhookId = FIXTURE_WEBHOOK_ID) } returns server(id = 8)
        coEvery { serverManager.getServer(id = ServerManager.SERVER_ID_ACTIVE) } returns server(id = 9)

        assertEquals(8, resolveServer(action()))

        coVerify(exactly = 1) { notificationDao.get(FIXTURE_DATABASE_ID.toInt()) }
        coVerify(exactly = 1) { serverManager.getServer(webhookId = FIXTURE_WEBHOOK_ID) }
        confirmVerified(notificationDao, serverManager)
    }

    @Test
    fun `Given history without server when resolving action then webhook fallback is preserved`() = runTest {
        coEvery { notificationDao.get(FIXTURE_DATABASE_ID.toInt()) } returns notification(serverId = null)
        coEvery { serverManager.getServer(webhookId = FIXTURE_WEBHOOK_ID) } returns server(id = 8)

        assertEquals(8, resolveServer(action()))
    }

    @Test
    fun `Given evicted history and unknown webhook when resolving action then active sentinel is used`() = runTest {
        coEvery { notificationDao.get(FIXTURE_DATABASE_ID.toInt()) } returns null
        coEvery { serverManager.getServer(webhookId = FIXTURE_WEBHOOK_ID) } returns null

        assertEquals(ServerManager.SERVER_ID_ACTIVE, resolveServer(action()))

        coVerify(exactly = 1) { notificationDao.get(FIXTURE_DATABASE_ID.toInt()) }
        coVerify(exactly = 1) { serverManager.getServer(webhookId = FIXTURE_WEBHOOK_ID) }
        confirmVerified(notificationDao, serverManager)
    }

    @Test
    fun `Given evicted history and no webhook when resolving action then active sentinel is used without lookup`() = runTest {
        coEvery { notificationDao.get(FIXTURE_DATABASE_ID.toInt()) } returns null

        assertEquals(ServerManager.SERVER_ID_ACTIVE, resolveServer(action(data = emptyMap())))

        coVerify(exactly = 1) { notificationDao.get(FIXTURE_DATABASE_ID.toInt()) }
        confirmVerified(notificationDao, serverManager)
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "   ", "fixture reply — 測試"])
    fun `Given evicted history when assembling action event then only local reply history is removed`(replyText: String?) = runTest {
        coEvery { notificationDao.get(FIXTURE_DATABASE_ID.toInt()) } returns null
        coEvery { serverManager.getServer(webhookId = FIXTURE_WEBHOOK_ID) } returns server(id = 8)
        val expected = mapOf(
            NotificationData.WEBHOOK_ID to FIXTURE_WEBHOOK_ID,
            "action" to if (replyText == null) "FIXTURE_OPEN" else "REPLY",
            "custom_data" to "{ \"fixture\": [1, 2] }",
            "prefix_${MessagingManager.SOURCE_REPLY_HISTORY}0" to "not a history entry",
        ) + (replyText?.let { mapOf("reply_text" to it) } ?: emptyMap())
        val originalData = expected + mapOf(
            "action" to "fixture stale action",
            "${MessagingManager.SOURCE_REPLY_HISTORY}0" to "fixture previous reply",
            "${MessagingManager.SOURCE_REPLY_HISTORY}1" to "fixture second reply",
            "${MessagingManager.SOURCE_REPLY_HISTORY}custom" to "fixture prefixed metadata",
        )
        val action = action(data = originalData).copy(
            key = expected.getValue("action"),
            behavior = if (replyText == null) null else "textInput",
        )

        assertEquals(8, resolveServer(action))
        assertEquals(expected, notificationActionEventData(action))
        assertEquals(originalData, action.data)
        coVerify(exactly = 1) { notificationDao.get(FIXTURE_DATABASE_ID.toInt()) }
        coVerify(exactly = 1) { serverManager.getServer(webhookId = FIXTURE_WEBHOOK_ID) }
        confirmVerified(notificationDao, serverManager)
    }

    private suspend fun resolveServer(action: NotificationAction): Int = resolveNotificationActionServerId(
        notificationDao = notificationDao,
        serverManager = serverManager,
        databaseId = FIXTURE_DATABASE_ID,
        action = action,
    )

    private fun action(data: Map<String, String> = mapOf(NotificationData.WEBHOOK_ID to FIXTURE_WEBHOOK_ID)) = NotificationAction(
        key = "FIXTURE_OPEN",
        title = "Fixture action",
        uri = null,
        behavior = null,
        data = data,
    )

    private fun notification(serverId: Int?) = NotificationItem(
        id = FIXTURE_DATABASE_ID.toInt(),
        received = 1L,
        message = "Fixture notification",
        data = "{}",
        source = "FCM",
        serverId = serverId,
    )

    private fun server(id: Int): Server = mockk {
        every { this@mockk.id } returns id
    }
}

private const val FIXTURE_DATABASE_ID = 42L
private const val FIXTURE_WEBHOOK_ID = "fixture-webhook"
