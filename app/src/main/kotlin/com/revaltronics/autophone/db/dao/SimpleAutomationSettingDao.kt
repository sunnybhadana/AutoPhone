package com.revaltronics.autophone.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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
    
    // New methods for batch management
    
    @Query("SELECT * FROM simple_automation_settings WHERE batch_group_id = :batchGroupId")
    suspend fun getSettingsByBatchGroup(batchGroupId: String): List<SimpleAutomationSetting>
    
    @Update
    suspend fun updateSetting(setting: SimpleAutomationSetting)
    
    @Transaction
    suspend fun incrementAnsweredCallsCount(id: Int) {
        getSettingById(id)?.let { setting ->
            val updated = setting.copy(answered_calls_count = setting.answered_calls_count + 1)
            updateSetting(updated)
        }
    }
    
    @Transaction
    suspend fun updateBatchGroupSettings(batchGroupId: String, maxAutoAnswers: Int, resetIntervalMinutes: Int) {
        if (batchGroupId.isNotEmpty()) {
            val settings = getSettingsByBatchGroup(batchGroupId)
            settings.forEach { setting ->
                val updated = setting.copy(
                    max_auto_answers = maxAutoAnswers,
                    reset_interval_minutes = resetIntervalMinutes
                )
                updateSetting(updated)
            }
        }
    }
    
    @Transaction
    suspend fun resetAnsweredCallsCount(batchGroupId: String) {
        val currentTime = System.currentTimeMillis()
        
        if (batchGroupId.isNotEmpty()) {
            // Reset all settings in the batch group
            val settings = getSettingsByBatchGroup(batchGroupId)
            settings.forEach { setting ->
                val updated = setting.copy(
                    answered_calls_count = 0,
                    last_reset_timestamp = currentTime
                )
                updateSetting(updated)
            }
        }
    }
    
    @Transaction
    suspend fun resetAnsweredCallsCountForRule(ruleId: Int) {
        val currentTime = System.currentTimeMillis()
        getSettingById(ruleId)?.let { setting ->
            val updated = setting.copy(
                answered_calls_count = 0,
                last_reset_timestamp = currentTime
            )
            updateSetting(updated)
        }
    }
    
    @Query("SELECT * FROM simple_automation_settings WHERE batch_group_id != ''")
    suspend fun getAllBatchedSettings(): List<SimpleAutomationSetting>
    
    @Query("SELECT DISTINCT batch_group_id FROM simple_automation_settings WHERE batch_group_id != ''")
    suspend fun getAllBatchGroupIds(): List<String>

    /**
     * Reset the answered_calls_count for all automation settings
     * This is useful for debugging or handling special cases
     */
    @Transaction
    suspend fun resetAllCounters() {
        val currentTime = System.currentTimeMillis()
        val allSettings = getAllSettings()
        allSettings.forEach { setting ->
            val updated = setting.copy(
                answered_calls_count = 0,
                last_reset_timestamp = currentTime
            )
            updateSetting(updated)
        }
    }
    
    /**
     * Set the counter for a specific rule to a specific value
     * Useful for manual adjustments
     */
    @Transaction 
    suspend fun setAnsweredCallsCount(ruleId: Int, count: Int) {
        getSettingById(ruleId)?.let { setting ->
            val updated = setting.copy(answered_calls_count = count)
            updateSetting(updated)
        }
    }
    
    /**
     * Checks if a rule that belongs to a batch group is under the limit
     * @param rule The automation rule to check
     * @return True if the rule is under the batch limit or doesn't have a batch group, false otherwise
     */
    @Transaction
    suspend fun isUnderBatchLimit(rule: SimpleAutomationSetting): Boolean {
        // No limit set, always allow
        if (rule.max_auto_answers <= 0) return true
        
        // Not part of a batch group, check individual rule limit
        if (rule.batch_group_id.isEmpty()) return rule.answered_calls_count < rule.max_auto_answers
        
        // Part of a batch group, check total limit for all rules in the group
        val settings = getSettingsByBatchGroup(rule.batch_group_id)
        val totalAnswered = settings.sumOf { it.answered_calls_count }
        return totalAnswered < rule.max_auto_answers
    }
}
