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
    
    @Query("SELECT * FROM simple_automation_settings WHERE phoneNumber = :phoneNumber AND isActive = :isActive")
    suspend fun getSettingByPhoneNumberAndActiveStatus(phoneNumber: String, isActive: Boolean): SimpleAutomationSetting?

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
    
    /**
     * Get the next available integer batch group ID
     * @return The next available integer as a string
     */
    @Query("SELECT COALESCE(MAX(CAST(batch_group_id AS INTEGER)), 0) + 1 FROM simple_automation_settings WHERE batch_group_id != '' AND CAST(batch_group_id AS INTEGER) = batch_group_id")
    suspend fun getNextBatchGroupId(): Int

    /**
     * Find rules related to this phone number by batch group
     * This is useful for finding all numbers from the same contact
     * @param phoneNumber The phone number to check
     * @return A list of related rules that share the same batch group
     */
    @Transaction
    suspend fun findRelatedRulesByBatchGroup(phoneNumber: String): List<SimpleAutomationSetting> {
        val rule = getSettingByPhoneNumber(phoneNumber) ?: return emptyList()
        
        // If the rule doesn't have a batch group, just return the rule itself
        if (rule.batch_group_id.isEmpty()) return listOf(rule)
        
        // Return all rules that share the same batch group
        return getSettingsByBatchGroup(rule.batch_group_id)
    }
    
    /**
     * Synchronize all configuration settings across a batch group
     * This ensures that all rules in the same batch group share the same configuration
     * @param rule The rule containing the configuration to sync to all batch members
     */
    @Transaction
    suspend fun syncBatchGroupConfiguration(rule: SimpleAutomationSetting) {
        // Only proceed if this rule belongs to a batch group
        if (rule.batch_group_id.isEmpty()) return
        
        // Get all rules in this batch group
        val batchRules = getSettingsByBatchGroup(rule.batch_group_id)
        
        // Skip if there's only one rule (no need to sync)
        if (batchRules.size <= 1) return
        
        // Update each rule in the batch group with the configuration from the source rule
        batchRules.forEach { batchRule ->
            // Skip the rule itself
            if (batchRule.id == rule.id) return@forEach
            
            // Create updated rule with synced configuration
            val updatedRule = batchRule.copy(
                // Keep these fields as is
                id = batchRule.id,
                contactName = batchRule.contactName,
                phoneNumber = batchRule.phoneNumber,
                // Ensure batch_group_id is preserved
                batch_group_id = rule.batch_group_id, // Use the source rule's batch_group_id to ensure consistency
                answered_calls_count = batchRule.answered_calls_count,
                last_reset_timestamp = batchRule.last_reset_timestamp,
                
                // Sync these configuration fields
                pickupDelaySeconds = rule.pickupDelaySeconds,
                autoDisconnectCall = rule.autoDisconnectCall,
                dtmfSequence = rule.dtmfSequence,
                max_auto_answers = rule.max_auto_answers,
                reset_interval_minutes = rule.reset_interval_minutes,
                isActive = rule.isActive
            )
            
            // Update the rule in the database
            updateSetting(updatedRule)
        }
    }
    
    /**
     * Get all batch group representatives (one rule per batch group)
     * This is used to display batched rules as a single entry in the UI
     * @return List of representative rules, one per batch group
     */
    @Transaction
    suspend fun getBatchGroupRepresentatives(): List<SimpleAutomationSetting> {
        val result = mutableListOf<SimpleAutomationSetting>()
        val processedBatchGroups = mutableSetOf<String>()
        
        // First, get all settings
        val allSettings = getAllSettings()
        
        // Group settings by batch group ID
        val batchGroups = allSettings
            .filter { it.batch_group_id.isNotEmpty() }
            .groupBy { it.batch_group_id }
            
        // Find the best representative for each batch group and add it
        batchGroups.forEach { (batchId, settings) ->
            if (!processedBatchGroups.contains(batchId)) {
                processedBatchGroups.add(batchId)
                
                // Choose the best representative - prefer rules with contact names
                val representative = settings.firstOrNull { it.contactName != null && it.contactName.isNotEmpty() }
                    ?: settings.firstOrNull() // Fall back to any rule in the batch
                    
                representative?.let { result.add(it) }
            }
        }
        
        // Add all non-batch (standalone) rules
        allSettings
            .filter { it.batch_group_id.isEmpty() }
            .forEach { result.add(it) }
        
        return result
    }
    
    /**
     * Get the count of rules in a batch group
     * @param batchGroupId The batch group ID
     * @return Number of rules in this batch group
     */
    @Query("SELECT COUNT(*) FROM simple_automation_settings WHERE batch_group_id = :batchGroupId")
    suspend fun getBatchGroupSize(batchGroupId: String): Int
    
    /**
     * Delete all rules in a batch group
     * @param batchGroupId The batch group ID to delete
     */
    @Query("DELETE FROM simple_automation_settings WHERE batch_group_id = :batchGroupId")
    suspend fun deleteBatchGroup(batchGroupId: String)
}
