package io.homeassistant.companion.android.database.notification

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.homeassistant.companion.android.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: NotificationDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = database.notificationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addingMoreThan500RetainsOnlyNewestRows() = runBlocking {
        assertEquals(500, MAX_NOTIFICATION_HISTORY)
        val inserted = List(525) { index ->
            addFixture(notification(received = index.toLong()))
        }

        assertRetained(inserted.drop(25).reversed())
        inserted.take(25).forEach { assertNull(dao.get(it.id)) }
    }

    @Test
    fun tiedTimestampsRetainHighestIdsDeterministically() = runBlocking {
        val inserted = List(MAX_NOTIFICATION_HISTORY + 1) { index ->
            addFixture(notification(received = 1L).copy(message = "fixture tied message $index"))
        }

        assertRetained(inserted.drop(1).reversed())
        assertNull(dao.get(inserted.first().id))
    }

    @Test
    fun concurrentInsertsRetainNewestRowsInOrder() = runBlocking {
        val inserted = List(MAX_NOTIFICATION_HISTORY + 50) { index ->
            async(Dispatchers.Default) {
                addFixture(notification(received = index.toLong() / 2))
            }
        }.awaitAll()
        val ordered = inserted.sortedWith(
            compareByDescending<NotificationItem> { it.received }.thenByDescending { it.id },
        )

        assertRetained(ordered.take(MAX_NOTIFICATION_HISTORY))
        ordered.drop(MAX_NOTIFICATION_HISTORY).forEach { assertNull(dao.get(it.id)) }
    }

    @Test
    fun outOfOrderInsertsRetainByReceivedTime() = runBlocking {
        val inserted = (MAX_NOTIFICATION_HISTORY downTo 1).map { received ->
            addFixture(notification(received = received.toLong()))
        }
        val older = addFixture(notification(received = 0L))
        assertRetained(inserted)
        assertNull(dao.get(older.id))

        val newest = addFixture(notification(received = 501L))
        assertRetained(listOf(newest) + inserted.dropLast(1))
        assertNull(dao.get(inserted.last().id))
    }

    @Test
    fun mixedServersShareCapAndPreserveSurvivingPayloads() = runBlocking {
        val inserted = List(525) { index ->
            addFixture(
                notification(received = index.toLong()).copy(
                    serverId = if (index % 2 == 0) 1 else 2,
                    source = if (index % 2 == 0) "FCM" else "Websocket",
                ),
            )
        }
        val expected = inserted.drop(25).reversed()

        assertRetained(expected)
        assertEquals(mapOf(1 to 250, 2 to 250), dao.getAll().groupingBy { it.serverId }.eachCount())
        inserted.take(25).forEach { assertNull(dao.get(it.id)) }
    }

    private suspend fun addFixture(item: NotificationItem): NotificationItem = item.copy(id = dao.add(item).toInt())

    private suspend fun assertRetained(expected: List<NotificationItem>) {
        val retained = dao.getAll().toList()
        assertEquals(500, retained.size)
        // Data class equality includes the exact JSON string, message, source, server and both ordering keys.
        assertEquals(expected, retained)
        assertEquals(expected.take(3), dao.getLastItems(3).toList())
        expected.forEach { assertEquals(it, dao.get(it.id)) }
    }

    private fun notification(received: Long) = NotificationItem(
        id = 0,
        received = received,
        message = "fixture message $received — 測試",
        data = """
            { "fixture": "$received", "webhook_id": "fixture-webhook", "actions": [
              { "action": "FIXTURE_OPEN", "title": "Open fixture" },
              { "action": "REPLY", "title": "Reply fixture", "behavior": "textInput" }
            ], "reply_text": "fixture reply", "reply_history_0": "fixture previous reply" }
        """.trimIndent(),
        source = "FCM",
        serverId = 1,
    )
}
