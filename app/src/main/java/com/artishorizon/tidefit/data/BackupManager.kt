package com.artishorizon.tidefit.data

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.artishorizon.tidefit.core.database.AppDatabase
import com.artishorizon.tidefit.domain.repository.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.exitProcess

/** Checks that a file is a TIDE Fit database this build can open. Pure file work, so it is unit-testable. */
object BackupValidator {
    sealed interface Result {
        data class Ok(val schemaVersion: Int, val hasProfile: Boolean) : Result
        data class Bad(val reason: String) : Result
    }

    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun validate(file: File, maxSchemaVersion: Int): Result {
        if (!file.isFile || file.length() < 100) return Result.Bad("That file is too small to be a TIDE Fit backup.")
        val header = ByteArray(SQLITE_MAGIC.size)
        file.inputStream().use { if (it.read(header) != header.size) return Result.Bad("That isn't a TIDE Fit backup.") }
        if (!header.contentEquals(SQLITE_MAGIC)) return Result.Bad("That isn't a TIDE Fit backup (not a database file).")
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val version = db.version
                if (version > maxSchemaVersion) return Result.Bad("This backup comes from a newer version of TIDE Fit (schema $version). Update the app first.")
                val tables = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                    buildSet { while (c.moveToNext()) add(c.getString(0)) }
                }
                if ("user_profile" !in tables || "room_master_table" !in tables) return Result.Bad("That database isn't a TIDE Fit backup.")
                val integrity = db.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else "unknown" }
                if (integrity != "ok") return Result.Bad("The backup file is damaged.")
                val hasProfile = db.rawQuery("SELECT COUNT(*) FROM user_profile", null).use { c -> c.moveToFirst() && c.getInt(0) > 0 }
                Result.Ok(version, hasProfile)
            }
        } catch (e: Exception) {
            Result.Bad("Couldn't read that file as a database.")
        }
    }
}

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: AppSettings
) {
    sealed interface RestoreResult {
        /** Data was replaced; call [restartApp] next. */
        data object Replaced : RestoreResult
        data class Failed(val reason: String) : RestoreResult
    }

    private val dbFile: File get() = context.getDatabasePath(AppDatabase.NAME)

    /** Writes a consistent copy of the database to [target]. Returns the size in bytes. */
    suspend fun export(target: Uri): Long = withContext(Dispatchers.IO) {
        // Fold the write-ahead log into the main file so the single file we copy is complete.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        val out = context.contentResolver.openOutputStream(target, "w") ?: error("Couldn't open the destination.")
        out.use { dbFile.inputStream().use { input -> input.copyTo(it) } }
        dbFile.length()
    }

    /**
     * Replaces all app data with the backup in [source]. The file is validated first, and the current data is kept
     * as `pre_restore_backup.db` inside the app's private storage in case the restore needs undoing.
     */
    suspend fun restore(source: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val staged = File(context.cacheDir, "restore_candidate.db")
        try {
            val input = context.contentResolver.openInputStream(source) ?: return@withContext RestoreResult.Failed("Couldn't open that file.")
            input.use { i -> staged.outputStream().use { i.copyTo(it) } }

            val verdict = BackupValidator.validate(staged, AppDatabase.VERSION)
            if (verdict is BackupValidator.Result.Bad) return@withContext RestoreResult.Failed(verdict.reason)
            verdict as BackupValidator.Result.Ok

            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            dbFile.copyTo(File(context.filesDir, "pre_restore_backup.db"), overwrite = true)

            db.close()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            staged.copyTo(dbFile, overwrite = true)

            // Onboarding state lives outside the database; without this a restored install would re-run onboarding.
            settings.setOnboardingDone(verdict.hasProfile)
            RestoreResult.Replaced
        } catch (e: Exception) {
            RestoreResult.Failed("The restore failed: ${e.message ?: "unknown error"}")
        } finally {
            staged.delete()
        }
    }

    /** Restarts the process so every cached connection and in-memory state starts fresh on the restored data. */
    fun restartApp() {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        exitProcess(0)
    }
}
