package com.revaltronics.autophone.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.revaltronics.autophone.models.SimpleAutomationSetting

@Dao
interface SimpleAutomationSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSetting(setting: SimpleAutomationSetting)

    @Query("SELECT * FROM simple_automation_settings WHERE id = :id")
    suspend fun getSettingById(id: Int): SimpleAutomationSetting?

    @Query("SELECT * FROM simple_automation_settings WHERE phoneNumber = :phoneNumber")
    suspend fun getSettingByPhoneNumber(phoneNumber: String): SimpleAutomationSetting?

    @Query("SELECT * FROM simple_automation_settings ORDER BY id DESC")
    suspend fun getAllSettings(): List<SimpleAutomationSetting>

    @Query("DELETE FROM simple_automation_settings WHERE id = :id")
    suspend fun deleteSettingById(id: Int)
}
