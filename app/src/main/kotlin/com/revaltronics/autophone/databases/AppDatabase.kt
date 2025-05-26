package com.revaltronics.autophone.databases

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.revaltronics.autophone.extensions.config
import com.revaltronics.autophone.helpers.Converters
import com.revaltronics.autophone.interfaces.TimerDao
import com.revaltronics.autophone.models.Timer
import com.revaltronics.autophone.models.TimerState
import com.revaltronics.autophone.models.SimpleAutomationSetting
import com.revaltronics.autophone.db.dao.SimpleAutomationSettingDao
import com.revaltronics.autophone.db.converters.DtmfStepListConverter
import java.util.concurrent.Executors

@Database(entities = [Timer::class, SimpleAutomationSetting::class], version = 7)
@TypeConverters(Converters::class, DtmfStepListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun TimerDao(): TimerDao
    abstract fun simpleAutomationSettingDao(): SimpleAutomationSettingDao

    companion object {
        private var db: AppDatabase? = null
        
        // Migration from version 6 to 7 - Adding call tracking fields
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to the simple_automation_settings table
                db.execSQL("ALTER TABLE simple_automation_settings ADD COLUMN batch_group_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE simple_automation_settings ADD COLUMN answered_calls_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE simple_automation_settings ADD COLUMN max_auto_answers INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE simple_automation_settings ADD COLUMN reset_interval_minutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE simple_automation_settings ADD COLUMN last_reset_timestamp INTEGER NOT NULL DEFAULT 0")
                
                // Create index for batch_group_id
                db.execSQL("CREATE INDEX IF NOT EXISTS index_simple_automation_settings_batch_group_id ON simple_automation_settings(batch_group_id)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            if (db == null) {
                synchronized(AppDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app.db")
                            .fallbackToDestructiveMigration()
                            .addMigrations(MIGRATION_6_7)
                            .addCallback(object : Callback() {
                                override fun onCreate(db: SupportSQLiteDatabase) {
                                    super.onCreate(db)
                                    insertDefaultTimer(context)
                                }
                            })
                            .build()
                    }
                }
            }
            return db!!
        }

        private fun insertDefaultTimer(context: Context) {
            Executors.newSingleThreadScheduledExecutor().execute {
                val config = context.config
                db!!.TimerDao().insertOrUpdateTimer(
                    Timer(
                        id = null,
                        seconds = config.timerSeconds,
                        state = TimerState.Idle,
                        vibrate = config.timerVibrate,
                        soundUri = config.timerSoundUri,
                        soundTitle = config.timerSoundTitle,
                        title = config.timerTitle ?: "",
                        label = config.timerLabel ?: "",
                        description = config.timerDescription ?: "",
                        createdAt = System.currentTimeMillis(),
                        channelId = config.timerChannelId,
                    )
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `timers` ADD COLUMN `oneShot` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
