package com.artishorizon.tidefit.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.artishorizon.tidefit.core.database.AppDatabase
import com.artishorizon.tidefit.core.database.UserProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class BackupAndImportTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 11, 16)
    private lateinit var db: AppDatabase
    private lateinit var dbFile: File

    @Before fun setUp() {
        context.deleteDatabase("backup_test.db")
        db = Room.databaseBuilder(context, AppDatabase::class.java, "backup_test.db").allowMainThreadQueries().build()
        db.openHelper.writableDatabase // force creation
        dbFile = context.getDatabasePath("backup_test.db")
    }

    @After fun tearDown() { db.close(); context.deleteDatabase("backup_test.db") }

    private suspend fun seedProfile(kg: Double = 80.0) {
        val now = Instant.now(clock)
        db.userProfileDao().upsert(
            UserProfileEntity(
                name = "T", age = 30, sex = null, heightCm = 180.0, initialWeightKg = kg, currentWeightKg = kg, targetWeightKg = 75.0,
                experience = "BEGINNER", trainingDaysPerWeek = 3, activityLevel = "MODERATE", goal = "FAT_LOSS",
                dietaryPreferences = emptyList(), foodRestrictions = emptyList(), allergies = emptyList(), equipment = emptyList(),
                weightUnit = "KG", heightUnit = "CM", createdAt = now, updatedAt = now
            )
        )
    }

    private fun closedCopy(): File {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        return File(context.cacheDir, "copy.db").also { dbFile.copyTo(it, overwrite = true) }
    }

    // ---- backup validation
    @Test fun acceptsRealDatabaseWithAndWithoutProfile() = runBlocking {
        val empty = BackupValidator.validate(closedCopy(), AppDatabase.VERSION)
        assertEquals(BackupValidator.Result.Ok(AppDatabase.VERSION, hasProfile = false), empty)
        seedProfile()
        val withProfile = BackupValidator.validate(closedCopy(), AppDatabase.VERSION)
        assertEquals(BackupValidator.Result.Ok(AppDatabase.VERSION, hasProfile = true), withProfile)
    }

    @Test fun rejectsGarbageTinyAndMissingFiles() {
        val garbage = File(context.cacheDir, "g.db").apply { writeBytes(ByteArray(4096) { 7 }) }
        assertTrue(BackupValidator.validate(garbage, AppDatabase.VERSION) is BackupValidator.Result.Bad)
        val tiny = File(context.cacheDir, "t.db").apply { writeText("hi") }
        assertTrue(BackupValidator.validate(tiny, AppDatabase.VERSION) is BackupValidator.Result.Bad)
        assertTrue(BackupValidator.validate(File(context.cacheDir, "nope.db"), AppDatabase.VERSION) is BackupValidator.Result.Bad)
    }

    @Test fun rejectsForeignSqliteDatabase() {
        val f = File(context.cacheDir, "other.db").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(f, null).use { it.execSQL("CREATE TABLE notes(id INTEGER)") }
        val r = BackupValidator.validate(f, AppDatabase.VERSION)
        assertTrue(r is BackupValidator.Result.Bad)
    }

    @Test fun rejectsBackupFromNewerSchema() {
        val copy = closedCopy()
        SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = AppDatabase.VERSION + 1 }
        val r = BackupValidator.validate(copy, AppDatabase.VERSION)
        assertTrue(r is BackupValidator.Result.Bad && "newer" in r.reason)
    }

    // ---- Health Connect style imports
    @Test fun weightImportNeverOverwritesManualAndUpdatesImported() = runBlocking {
        seedProfile(90.0)
        val repo = WeightRepositoryImpl(db, clock)
        repo.add(today, 85.0) // manual entry today
        val d1 = today.minusDays(1)
        val d2 = today.minusDays(2)

        val n = repo.importExternal(mapOf(today to 70.0, d1 to 84.0, d2 to 83.0), "HEALTH_CONNECT")
        assertEquals(2, n) // today's manual entry is left alone
        assertEquals(85.0, repo.observeAll().first().last { it.date == today }.weightKg, 0.001)

        assertEquals(0, repo.importExternal(mapOf(d1 to 84.0), "HEALTH_CONNECT")) // same value: nothing to do
        assertEquals(1, repo.importExternal(mapOf(d1 to 83.5), "HEALTH_CONNECT")) // changed upstream: updated
        assertEquals(83.5, repo.observeAll().first().single { it.date == d1 }.weightKg, 0.001)
        assertEquals(85.0, db.userProfileDao().get()!!.currentWeightKg, 0.001) // latest dated entry is still today's manual one
    }

    @Test fun weightImportSkipsFutureAndImplausibleDates() = runBlocking {
        val repo = WeightRepositoryImpl(db, clock)
        assertEquals(0, repo.importExternal(mapOf(today.plusDays(1) to 80.0, today to 3.0), "HEALTH_CONNECT"))
    }

    @Test fun sleepImportOnlyFillsEmptyDates() = runBlocking {
        val repo = SleepRepositoryImpl(db, clock)
        repo.save(com.artishorizon.tidefit.domain.model.SleepEntry(date = today, durationMinutes = 420, quality = 4))
        val n = repo.importExternal(mapOf(today to 300, today.minusDays(1) to 480))
        assertEquals(1, n)
        val rows = repo.observeRange(today.minusDays(3), today).first()
        assertEquals(420, rows.single { it.date == today }.durationMinutes)
        assertEquals(480, rows.single { it.date == today.minusDays(1) }.durationMinutes)
    }
}
