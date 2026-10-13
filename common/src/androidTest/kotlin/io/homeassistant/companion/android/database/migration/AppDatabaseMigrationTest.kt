package io.homeassistant.companion.android.database.migration

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.homeassistant.companion.android.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests for AppDatabase.
 *
 * These tests verify that database migrations correctly transform the schema
 * and preserve existing data.
 *
 * @see <a href="https://developer.android.com/training/data-storage/room/migrating-db-versions">Room Migration Testing</a>
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test"
    private val context: Context = ApplicationProvider.getApplicationContext()

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    /** Verifies that upgrading legacy history retains the newest 500 rows deterministically. */
    @Test
    fun oversizedVersion51HistoryRetainsNewest500AfterMigration() {
        helper.createDatabase(testDbName, 51).use { db ->
            repeat(502) {
                db.execSQL(
                    "INSERT INTO notification_history (received, message, data, source, server_id) " +
                        "VALUES (1000, 'message', '{}', 'FCM', 1)",
                )
            }
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(*migrationPath(context))
            .build()

        try {
            database.openHelper.writableDatabase.query(
                "SELECT id FROM notification_history ORDER BY received DESC, id DESC",
            ).use { cursor ->
                assertEquals("notification history should be capped at 500 rows", 500, cursor.count)
                assertTrue("newest tied row must exist", cursor.moveToFirst())
                assertEquals("newest tied row should be retained", 502, cursor.getInt(0))
                assertTrue("oldest retained row must exist", cursor.moveToLast())
                assertEquals("oldest tied rows should be pruned", 3, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun migrateFromVersion24ToLatest() {
        // Create database at version 24 - the earliest version with an exported schema
        helper.createDatabase(testDbName, 24).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='sensors'").use { cursor ->
                assert(cursor.count == 1) { "sensors table should exist at version 24" }
            }
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='Authentication_List'").use { cursor ->
                assert(cursor.count == 1) { "Authentication_List table should exist at version 24" }
            }
        }

        val database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            testDbName,
        ).addMigrations(*migrationPath(context)).build()

        try {
            database.openHelper.writableDatabase
            // If we get here without exception, all migrations from v24 to current succeeded
        } finally {
            database.close()
        }
    }
}
