package org.torproject.vpn.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.torproject.vpn.database.app.App
import org.torproject.vpn.database.app.AppDao

@Database(
    entities = [
        App::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        private const val DATABASE_APP = "app_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_APP
                ).setDriver(BundledSQLiteDriver()).build()

                INSTANCE = instance
                instance
            }
        }
    }
}
