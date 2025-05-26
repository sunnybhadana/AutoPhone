package com.revaltronics.autophone.services

import android.os.Handler
import android.telecom.CallAudioState
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.revaltronics.autophone.activities.CallActivity
import com.revaltronics.autophone.databases.AppDatabase
import com.revaltronics.autophone.extensions.config
import com.revaltronics.autophone.extensions.isOutgoing
import com.revaltronics.autophone.extensions.powerManager
import com.revaltronics.autophone.helpers.*
import com.revaltronics.autophone.models.DtmfStep
import com.revaltronics.autophone.models.Events
import com.revaltronics.autophone.models.SimpleAutomationSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus

class CallService : InCallService() {
    private val callNotificationManager by lazy { CallNotificationManager(this) }
    private val appDatabase by lazy { AppDatabase.getInstance(this) }
    private val scope = CoroutineScope(Dispatchers.Main)
    private val TAG = "CallService"

    private val callListener = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                callNotificationManager.cancelNotification()
            } else {
                callNotificationManager.setupNotification()
            }
        }
    }
    
    /**
     * Find automation rule by phone number
     * @param phoneNumber The incoming call number to check for automation rules
     * @return The matching automation rule or null if no rule is found
     */
    private suspend fun findAutomationRuleByPhoneNumber(phoneNumber: String): SimpleAutomationSetting? {
        return withContext(Dispatchers.IO) {
            try {
                // Phone number normalization to handle various formats
                val normalizedNumber = normalizePhoneNumber(phoneNumber)
                
                // First try exact match
                var rule = appDatabase.simpleAutomationSettingDao().getSettingByPhoneNumber(normalizedNumber)
                
                // If no exact match, try with different formats
                if (rule == null) {
                    // Try without country code if the number starts with +
                    if (normalizedNumber.startsWith("+")) {
                        val withoutCountryCode = normalizedNumber.substring(3) // Skip +XX
                        rule = appDatabase.simpleAutomationSettingDao().getSettingByPhoneNumber(withoutCountryCode)
                        
                        // If still no match, try just the last 10 digits
                        if (rule == null && normalizedNumber.length > 10) {
                            val last10Digits = normalizedNumber.substring(normalizedNumber.length - 10)
                            rule = appDatabase.simpleAutomationSettingDao().getSettingByPhoneNumber(last10Digits)
                        }
                    }
                }
                
                rule
            } catch (e: Exception) {
                Log.e(TAG, "Error finding automation rule: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Normalize a phone number by removing non-digit characters except leading +
     * @param phoneNumber The phone number to normalize
     * @return The normalized phone number
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        val result = StringBuilder()
        
        // Preserve leading +
        if (phoneNumber.startsWith("+")) {
            result.append("+")
        }
        
        // Add only digits
        phoneNumber.forEach { char ->
            if (char.isDigit()) {
                result.append(char)
            }
        }
        
        return result.toString()
    }
    
    /**
     * Check if there's a matching automation rule and handle the call accordingly
     * @param call The incoming call
     * @param phoneNumber The incoming call number
     */
    private fun handleAutomatedCallResponse(call: Call, phoneNumber: String) {
        scope.launch {
            val rule = findAutomationRuleByPhoneNumber(phoneNumber)
            if (rule != null) {
                // Log detailed rule information
                val batchInfo = if (rule.batch_group_id.isNotEmpty()) 
                    "batch group: ${rule.batch_group_id}" else "no batch group"
                val limitInfo = if (rule.max_auto_answers > 0) 
                    "limit: ${rule.answered_calls_count}/${rule.max_auto_answers}" else "no limit"
                val resetInfo = if (rule.reset_interval_minutes > 0)
                    "resets every: ${rule.reset_interval_minutes} min" else "never resets"
                
                Log.d(TAG, "Found automation rule for $phoneNumber: pickup delay ${rule.pickupDelaySeconds}s, " +
                      "DTMF steps: ${rule.dtmfSequence.size}, $batchInfo, $limitInfo, $resetInfo")
                
                // Check if we should auto-answer based on batch limits
                val shouldAutoAnswer = shouldAutoAnswerCall(rule)
                
                if (shouldAutoAnswer) {
                    Log.d(TAG, "Auto-answering call from $phoneNumber")
                    executeAutomationRule(call, rule)
                    
                    // Update the call counter after successful auto-answer
                    updateCallCounter(rule)
                } else {
                    Log.d(TAG, "Auto-answer limit reached for $phoneNumber (count: ${rule.answered_calls_count}/${rule.max_auto_answers}). Handling call normally.")
                    handleRegularCall(call)
                }
            } else {
                Log.d(TAG, "No automation rule found for $phoneNumber")
                handleRegularCall(call)
            }
        }
    }
    
    /**
     * Check if we should auto-answer this call based on batch limits
     * @param rule The automation rule to check
     * @return true if the call should be auto-answered, false otherwise
     */
    private suspend fun shouldAutoAnswerCall(rule: SimpleAutomationSetting): Boolean {
        // First check if the timer needs to be reset due to reset interval
        val resetTimerResult = checkAndUpdateResetTimestamp(rule)
        if (resetTimerResult) {
            // If the timer was reset, we're definitely under the limit
            return true
        }
        
        // Get the latest version of the rule from database to ensure counters are up to date
        val latestRule = withContext(Dispatchers.IO) {
            appDatabase.simpleAutomationSettingDao().getSettingById(rule.id) ?: rule
        }
        
        // If this rule belongs to a batch group, check the batch limit
        if (latestRule.batch_group_id.isNotEmpty()) {
            val isUnderLimit = withContext(Dispatchers.IO) {
                appDatabase.simpleAutomationSettingDao().isUnderBatchLimit(latestRule)
            }
            
            val batchRules = withContext(Dispatchers.IO) {
                appDatabase.simpleAutomationSettingDao().getSettingsByBatchGroup(latestRule.batch_group_id)
            }
            val totalAnswered = batchRules.sumOf { it.answered_calls_count }
            val maxAnswers = latestRule.max_auto_answers
            
            Log.d(TAG, "Batch group ${latestRule.batch_group_id} status: $totalAnswered/$maxAnswers calls answered, under limit: $isUnderLimit")
            
            return isUnderLimit
        }
        
        // Otherwise, just check if this individual rule is under its limit
        val isUnderLimit = latestRule.max_auto_answers <= 0 || latestRule.answered_calls_count < latestRule.max_auto_answers
        
        Log.d(TAG, "Individual rule ${latestRule.id} status: ${latestRule.answered_calls_count}/${latestRule.max_auto_answers} calls answered, under limit: $isUnderLimit")
        
        return isUnderLimit
    }
    
    /**
     * Check and update the reset timestamp if needed
     * @param rule The automation rule to check
     * @return true if the rule should allow auto-answering, false otherwise
     */
    private suspend fun checkAndUpdateResetTimestamp(rule: SimpleAutomationSetting): Boolean {
        // If no max auto answers set, always allow
        if (rule.max_auto_answers <= 0) {
            return true
        }
        
        val currentTime = System.currentTimeMillis()
        
        // If this is the first call (last_reset_timestamp is 0), initialize it
        if (rule.last_reset_timestamp == 0L) {
            withContext(Dispatchers.IO) {
                val updatedRule = rule.copy(last_reset_timestamp = currentTime)
                appDatabase.simpleAutomationSettingDao().updateSetting(updatedRule)
                Log.d(TAG, "Initialized last_reset_timestamp for rule ID ${rule.id} (${rule.phoneNumber})")
            }
            // It's the first call, so we're definitely under the limit
            return true
        }
        
        // If reset interval is set and the time has passed, reset the counter
        if (rule.reset_interval_minutes > 0) {
            val resetIntervalMs = rule.reset_interval_minutes * 60 * 1000L
            val timeSinceReset = currentTime - rule.last_reset_timestamp
            val timeLeftUntilReset = resetIntervalMs - timeSinceReset
            
            Log.d(TAG, "Rule ID ${rule.id}: Time since last reset: ${timeSinceReset/1000/60} minutes, " +
                      "reset interval: ${rule.reset_interval_minutes} minutes, " +
                      "time left: ${timeLeftUntilReset/1000/60} minutes")
            
            if (timeSinceReset >= resetIntervalMs) {
                Log.d(TAG, "Reset interval of ${rule.reset_interval_minutes} minutes passed for rule ID ${rule.id} (${rule.phoneNumber})")
                
                withContext(Dispatchers.IO) {
                    try {
                        if (rule.batch_group_id.isNotEmpty()) {
                            // Reset the batch group
                            appDatabase.simpleAutomationSettingDao().resetAnsweredCallsCount(rule.batch_group_id)
                            Log.d(TAG, "Reset counter for batch group ${rule.batch_group_id}")
                        } else {
                            // Reset just this rule
                            appDatabase.simpleAutomationSettingDao().resetAnsweredCallsCountForRule(rule.id)
                            Log.d(TAG, "Reset counter for individual rule ID ${rule.id}")
                        }
                    } catch (e: Exception) {
                        // Log any errors but don't crash
                        Log.e(TAG, "Error resetting call counter: ${e.message}", e)
                    }
                }
                return true
            }
        }
        
        // Check if we're under the limit (max_auto_answers > 0 already checked at the beginning)
        val underLimit = rule.answered_calls_count < rule.max_auto_answers
        
        // Log detailed information about the rule counters
        Log.d(TAG, "Rule ID ${rule.id}: answered calls ${rule.answered_calls_count}/${rule.max_auto_answers}, " +
                  "under limit: $underLimit, " +
                  "last reset: ${if (rule.last_reset_timestamp > 0) java.util.Date(rule.last_reset_timestamp) else "never"}")
        
        if (!underLimit) {
            Log.w(TAG, "Auto-answer limit reached for rule ID ${rule.id} (${rule.phoneNumber}). " +
                      "Count: ${rule.answered_calls_count}/${rule.max_auto_answers}. " +
                      "Will handle call normally without automation.")
        }
        
        return underLimit
    }
    
    /**
     * Update the call counter after successfully auto-answering a call
     * @param rule The automation rule that was used
     */
    private suspend fun updateCallCounter(rule: SimpleAutomationSetting) {
        withContext(Dispatchers.IO) {
            try {
                // Get the latest version of the rule to ensure we have updated counters
                val updatedRule = appDatabase.simpleAutomationSettingDao().getSettingById(rule.id)
                
                if (updatedRule != null) {
                    // Update the individual rule counter
                    val newCount = updatedRule.answered_calls_count + 1
                    val currentTime = System.currentTimeMillis()
                    
                    // Initialize timestamp if it's not set or keep the existing one
                    // We shouldn't update the timestamp here as it's used to track when the reset interval started
                    // The timestamp should only be updated when the counter is reset
                    val timestamp = if (updatedRule.last_reset_timestamp == 0L) {
                        currentTime
                    } else {
                        updatedRule.last_reset_timestamp
                    }
                    
                    // Update the rule with incremented count
                    val ruleToUpdate = updatedRule.copy(
                        answered_calls_count = newCount,
                        last_reset_timestamp = timestamp
                    )
                    
                    appDatabase.simpleAutomationSettingDao().updateSetting(ruleToUpdate)
                    
                    val limitInfo = if (updatedRule.max_auto_answers > 0) {
                        "${newCount}/${updatedRule.max_auto_answers}"
                    } else {
                        "unlimited"
                    }
                    
                    Log.d(TAG, "Updated counter for ${rule.phoneNumber}: $limitInfo " +
                             "(batch: ${rule.batch_group_id}, last reset: ${timestamp})")
                    
                    // If this rule belongs to a batch group, log the total batch count
                    if (rule.batch_group_id.isNotEmpty()) {
                        val batchRules = appDatabase.simpleAutomationSettingDao().getSettingsByBatchGroup(rule.batch_group_id)
                        val totalAnswered = batchRules.sumOf { it.answered_calls_count }
                        val maxAnswers = batchRules.firstOrNull()?.max_auto_answers ?: 0
                        
                        if (maxAnswers > 0) {
                            Log.d(TAG, "Batch ${rule.batch_group_id} total: $totalAnswered/$maxAnswers")
                        } else {
                            Log.d(TAG, "Batch ${rule.batch_group_id} has unlimited auto answers")
                        }
                    } else {
                        // No batch group for this rule
                    }
                } else {
                    // Fallback to incrementing counter if we couldn't get the latest rule
                    appDatabase.simpleAutomationSettingDao().incrementAnsweredCallsCount(rule.id)
                    Log.d(TAG, "Incremented answered calls count for ${rule.phoneNumber} (batch: ${rule.batch_group_id})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating call counter: ${e.message}", e)
            }
        }
    }
    
    /**
     * Execute the automation rule for a given call
     * @param call The call to automate
     * @param rule The automation rule to follow
     */
    private fun executeAutomationRule(call: Call, rule: SimpleAutomationSetting) {
        // Answer the call after the configured delay
        val delayMs = rule.pickupDelaySeconds * 1000L
        
        Handler(mainLooper).postDelayed({
            Log.d(TAG, "Auto-answering call from ${rule.phoneNumber} after ${rule.pickupDelaySeconds}s delay")
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
            
            // If there are DTMF steps, wait for call to be active before sending tones
            if (rule.dtmfSequence.isNotEmpty()) {
                // Register a callback to wait for the call to become active
                call.registerCallback(object : Call.Callback() {
                    override fun onStateChanged(call: Call, state: Int) {
                        if (state == Call.STATE_ACTIVE) {
                            // The call is now active, wait an additional safety delay and then send DTMF tones
                            Log.d(TAG, "Call is now active, waiting 4 seconds before starting DTMF sequence")
                            Handler(mainLooper).postDelayed({
                                // Log the full sequence timing for clarity
                                var cumulativeTime = 0f
                                // Minimum delay for reliable DTMF tone recognition
                                val minimumDelaySeconds = 2.0f
                                
                                rule.dtmfSequence.forEachIndexed { i, step ->
                                    // For all steps (including the first), add its delay to the cumulative time
                                    // The timing of each step should account for all previous delays
                                    if (i > 0) {
                                        // Apply minimum delay enforcement
                                        val actualPrevDelay = if (rule.dtmfSequence[i-1].delayAfterSeconds < minimumDelaySeconds)
                                            minimumDelaySeconds else rule.dtmfSequence[i-1].delayAfterSeconds
                                        cumulativeTime += actualPrevDelay
                                    }
                                    // Apply minimum delay in the logs to match what will actually happen
                                    val actualDelay = if (step.delayAfterSeconds < minimumDelaySeconds)
                                        minimumDelaySeconds else step.delayAfterSeconds
                                    
                                    if (step.delayAfterSeconds < minimumDelaySeconds) {
                                        Log.d(TAG, "DTMF step delay for key '${step.key}' was too short (${step.delayAfterSeconds}s), enforcing minimum delay of ${minimumDelaySeconds}s")
                                    }
                                    
                                    Log.d(TAG, "DTMF sequence plan: Key '${step.key}' will be sent at ${cumulativeTime + 4}s after call is active")
                                }
                                
                                // Start the DTMF sequence with proper delays
                                // We'll handle the first tone's delay in the executeDtmfSequence function
                                executeDtmfSequence(call, rule.dtmfSequence, 0, rule.autoDisconnectCall)
                                
                                // Unregister this callback since we no longer need it
                                call.unregisterCallback(this)
                            }, 4000) // Wait additional 4 seconds after call becomes active
                        }
                    }
                })
            } else if (rule.autoDisconnectCall) {
                // If no DTMF but auto-disconnect is set, hang up after a short delay
                Handler(mainLooper).postDelayed({
                    Log.d(TAG, "Auto-disconnecting call from ${rule.phoneNumber}")
                    call.disconnect()
                }, 1000)
            }
        }, delayMs)
    }
    
    /**
     * Execute DTMF sequence recursively
     * @param call The call to send DTMF tones to
     * @param sequence The sequence of DTMF steps to execute
     * @param index Current index in the sequence
     * @param disconnectAfter Whether to disconnect the call after the sequence completes
     */
    private fun executeDtmfSequence(call: Call, sequence: List<DtmfStep>, index: Int, disconnectAfter: Boolean) {
        if (index >= sequence.size) {
            // End of sequence
            if (disconnectAfter) {
                Log.d(TAG, "DTMF sequence complete, disconnecting call")
                call.disconnect()
            }
            return
        }
        
        val step = sequence[index]
        
        // Enforce minimum delay of 2 seconds for reliable DTMF tone recognition
        val minimumDelaySeconds = 2.0f
        // Apply the minimum delay if the configured delay is too short
        val actualDelay = if (step.delayAfterSeconds < minimumDelaySeconds) {
            Log.d(TAG, "DTMF step delay was too short (${step.delayAfterSeconds}s), enforcing minimum delay of ${minimumDelaySeconds}s")
            minimumDelaySeconds
        } else {
            step.delayAfterSeconds
        }
        
        // For each step, we need to wait the delay time before playing the tone
        // For the first tone (index 0), we need to wait its own delay
        val initialDelayMs = if (index == 0 && actualDelay > 0) {
            // For the first tone, delay by its own delayAfterSeconds value first
            Log.d(TAG, "Waiting ${actualDelay}s before first DTMF tone '${step.key}'")
            (actualDelay * 1000).toLong()
        } else {
            // For subsequent tones, the delay is handled at the end of the previous tone's processing
            0L
        }
        
        Handler(mainLooper).postDelayed({
            Log.d(TAG, "Playing DTMF tone ${index + 1}/${sequence.size}: '${step.key}', delay after: ${actualDelay}s")
            
            // Play the DTMF tone
            if (step.key.isNotEmpty()) {
                // Log before playing the tone
                Log.d(TAG, "Sending DTMF tone '${step.key[0]}' now")
                
                call.playDtmfTone(step.key[0]) // Take the first character of the key
                
                // Stop the tone after a short duration
                Handler(mainLooper).postDelayed({
                    call.stopDtmfTone()
                    Log.d(TAG, "Stopped DTMF tone '${step.key[0]}'")
                    
                    // Schedule the next step after the specified delay
                    val delayMs = (actualDelay * 1000).toLong()
                    if (index < sequence.size - 1) {
                        Log.d(TAG, "Waiting ${actualDelay}s before next DTMF tone")
                    } else {
                        Log.d(TAG, "This was the last DTMF tone in the sequence")
                    }
                    
                    Handler(mainLooper).postDelayed({
                        executeDtmfSequence(call, sequence, index + 1, disconnectAfter)
                    }, delayMs)
                    
                }, 500) // Play each tone for 500ms (increased from 300ms for better reliability)
            } else {
                // If key is empty, skip to next step
                val delayMs = (actualDelay * 1000).toLong()
                Log.d(TAG, "Empty key, waiting ${actualDelay}s before next DTMF tone")
                
                Handler(mainLooper).postDelayed({
                    executeDtmfSequence(call, sequence, index + 1, disconnectAfter)
                }, delayMs)
            }
        }, initialDelayMs)
    }
    
    /**
     * Handle regular call when no automation rule is found
     * @param call The call to handle normally
     */
    private fun handleRegularCall(call: Call) {
        //val isScreenLocked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        when {
            !powerManager.isInteractive /*|| isScreenLocked*/ -> {
                try {
                    startActivity(CallActivity.getStartIntent(this))
                    callNotificationManager.setupNotification(true)
                } catch (e: Exception) {
                    // seems like startActivity can throw AndroidRuntimeException and ActivityNotFoundException, not yet sure when and why, lets show a notification
                    callNotificationManager.setupNotification()
                }
            }

            call.isOutgoing() -> {
                try {
                    startActivity(CallActivity.getStartIntent(this, needSelectSIM = call.details.accountHandle == null))
                    callNotificationManager.setupNotification(true)
                } catch (e: Exception) {
                    // seems like startActivity can throw AndroidRuntimeException and ActivityNotFoundException, not yet sure when and why, lets show a notification
                    callNotificationManager.setupNotification()
                }
            }

            config.showIncomingCallsFullScreen /*&& getPhoneSize() < 2*/ -> {
                try {
                    startActivity(CallActivity.getStartIntent(this))
                    callNotificationManager.setupNotification(true)
                } catch (e: Exception) {
                    // seems like startActivity can throw AndroidRuntimeException and ActivityNotFoundException, not yet sure when and why, lets show a notification
                    callNotificationManager.setupNotification()
                }
            }

            else -> callNotificationManager.setupNotification()
        }
        if (!call.isOutgoing() && !powerManager.isInteractive && config.flashForAlerts) MyCameraImpl.newInstance(this).toggleSOS()
    }

    /**
     * Check all rules with reset intervals and update any with expired timers
     * This can be called periodically to ensure rules get reset even if they haven't received calls
     */
    private suspend fun checkAndResetExpiredRules() {
        withContext(Dispatchers.IO) {
            try {
                val currentTime = System.currentTimeMillis()
                val rules = appDatabase.simpleAutomationSettingDao().getAllSettings()
                
                // Process rules with reset intervals
                val rulesWithIntervals = rules.filter { it.reset_interval_minutes > 0 }
                
                if (rulesWithIntervals.isNotEmpty()) {
                    Log.d(TAG, "Checking ${rulesWithIntervals.size} rules for expired reset intervals...")
                    
                    // Track batch groups we've already processed
                    val processedBatchGroups = mutableSetOf<String>()
                    
                    for (rule in rulesWithIntervals) {
                        val resetIntervalMs = rule.reset_interval_minutes * 60 * 1000L
                        val lastResetTimestamp = rule.last_reset_timestamp
                        
                        // Skip if there's no last reset timestamp (rule hasn't been used yet)
                        if (lastResetTimestamp == 0L) continue
                        
                        // Skip if we already processed this batch group
                        if (rule.batch_group_id.isNotEmpty() && rule.batch_group_id in processedBatchGroups) continue
                        
                        val timeSinceReset = currentTime - lastResetTimestamp
                        Log.d(TAG, "Rule ID ${rule.id}: Last reset was ${timeSinceReset/1000/60} minutes ago, " +
                              "reset interval: ${rule.reset_interval_minutes} minutes")
                        
                        if (timeSinceReset >= resetIntervalMs) {
                            if (rule.batch_group_id.isNotEmpty()) {
                                appDatabase.simpleAutomationSettingDao().resetAnsweredCallsCount(rule.batch_group_id)
                                processedBatchGroups.add(rule.batch_group_id)
                                Log.d(TAG, "Reset expired timer for batch group ${rule.batch_group_id}")
                            } else {
                                appDatabase.simpleAutomationSettingDao().resetAnsweredCallsCountForRule(rule.id)
                                Log.d(TAG, "Reset expired timer for rule ID ${rule.id} (${rule.phoneNumber})")
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "No rules with reset intervals found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for expired rules: ${e.message}", e)
            }
        }
    }

    /**
     * Reset counters for rules that belong to the same batch group
     * @param batchGroupId The batch group ID to reset
     * @return The number of rules that were reset
     */
    private suspend fun resetBatchGroupCounters(batchGroupId: String): Int {
        if (batchGroupId.isEmpty()) return 0
        return withContext(Dispatchers.IO) {
            try {
                val rules = appDatabase.simpleAutomationSettingDao().getSettingsByBatchGroup(batchGroupId)
                if (rules.isNotEmpty()) {
                    appDatabase.simpleAutomationSettingDao().resetAnsweredCallsCount(batchGroupId)
                    Log.d(TAG, "Reset counters for batch group $batchGroupId (${rules.size} rules)")
                    rules.size
                } else {
                    0
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting batch group $batchGroupId: ${e.message}")
                0
            }
        }
    }
    
    /**
     * Reset counter for an individual rule
     * @param ruleId The rule ID to reset
     * @return True if the rule was reset successfully
     */
    private suspend fun resetRuleCounter(ruleId: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val rule = appDatabase.simpleAutomationSettingDao().getSettingById(ruleId)
                if (rule != null) {
                    // If this rule is part of a batch group, reset the whole group
                    if (rule.batch_group_id.isNotEmpty()) {
                        resetBatchGroupCounters(rule.batch_group_id)
                    } else {
                        appDatabase.simpleAutomationSettingDao().resetAnsweredCallsCountForRule(ruleId)
                        Log.d(TAG, "Reset counter for individual rule ID $ruleId (${rule.phoneNumber})")
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting rule $ruleId: ${e.message}")
                false
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)
        
        // For any call (incoming or outgoing), check if any rules need to be reset due to expired timers
        scope.launch {
            checkAndResetExpiredRules()
            
            // Get the incoming phone number
            val incomingNumber = call.details.handle?.schemeSpecificPart
            // Only process incoming calls
            if (!call.isOutgoing() && !incomingNumber.isNullOrEmpty()) {
                Log.d(TAG, "Incoming call from $incomingNumber")
                // Check for automation rules for this number
                handleAutomatedCallResponse(call, incomingNumber)
            } else if (call.isOutgoing()) {
                // Handle outgoing calls normally
                Log.d(TAG, "Outgoing call")
                try {
                    startActivity(CallActivity.getStartIntent(this@CallService, needSelectSIM = call.details.accountHandle == null))
                    callNotificationManager.setupNotification(true)
                } catch (e: Exception) {
                    callNotificationManager.setupNotification()
                    Log.e(TAG, "Error starting activity for outgoing call: ${e.message}")
                }
            } else {
                // Handle other cases (calls with no number)
                Log.d(TAG, "Call with no number or unknown direction")
                handleRegularCall(call)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callListener)
        callNotificationManager.cancelNotification()
        val wasPrimaryCall = call == CallManager.getPrimaryCall()
        CallManager.onCallRemoved(call)
        if (CallManager.getPhoneState() == NoCall) {
            CallManager.inCallService = null
            callNotificationManager.cancelNotification()
        } else {
            callNotificationManager.setupNotification()
            if (wasPrimaryCall) {
                startActivity(CallActivity.getStartIntent(this))
            }
        }
        call.details?.let {
            if (config.flashForAlerts) MyCameraImpl.newInstance(this).stopSOS()
        }

        EventBus.getDefault().post(Events.RefreshCallLog)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        if (audioState != null) {
            CallManager.onAudioStateChanged(audioState)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        callNotificationManager.cancelNotification()
        if (config.flashForAlerts) MyCameraImpl.newInstance(this).stopSOS()
    }
}

