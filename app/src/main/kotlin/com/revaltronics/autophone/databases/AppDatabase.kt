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

@Database(entities = [Timer::class, SimpleAutomationSetting::class], version = 6)
@TypeConverters(Converters::class, DtmfStepListConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun TimerDao(): TimerDao
    abstract fun simpleAutomationSettingDao(): SimpleAutomationSettingDao

    companion object {
        private var db: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            if (db == null) {
                synchronized(AppDatabase::class) {
                    if (db == null) {
                        db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app.db")
                            .fallbackToDestructiveMigration()
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
