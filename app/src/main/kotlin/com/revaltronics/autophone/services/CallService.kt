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
            // Phone number normalization can be expanded if needed
            val normalizedNumber = normalizePhoneNumber(phoneNumber)
            try {
                appDatabase.simpleAutomationSettingDao().getSettingByPhoneNumber(normalizedNumber)
            } catch (e: Exception) {
                Log.e(TAG, "Error finding automation rule for $normalizedNumber: ${e.message}")
                null
            }
        }
    }
    
    /**
     * Normalize the phone number by removing any non-digit characters
     * This is a simple implementation and may need to be expanded based on your requirements
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        // Remove any non-digit character except + at the beginning
        val normalized = if (phoneNumber.startsWith("+")) {
            "+" + phoneNumber.substring(1).replace(Regex("[^0-9]"), "")
        } else {
            phoneNumber.replace(Regex("[^0-9]"), "")
        }
        Log.d(TAG, "Normalized phone number $phoneNumber to $normalized")
        return normalized
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
                Log.d(TAG, "Found automation rule for $phoneNumber: pickup delay ${rule.pickupDelaySeconds}s, DTMF steps: ${rule.dtmfSequence.size}")
                executeAutomationRule(call, rule)
            } else {
                Log.d(TAG, "No automation rule found for $phoneNumber")
                handleRegularCall(call)
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
            
            // If there are DTMF steps, execute them sequentially
            if (rule.dtmfSequence.isNotEmpty()) {
                executeDtmfSequence(call, rule.dtmfSequence, 0, rule.autoDisconnectCall)
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
        Log.d(TAG, "Playing DTMF tone: ${step.key}, delay after: ${step.delayAfterSeconds}s")
        
        // Play the DTMF tone
        if (step.key.isNotEmpty()) {
            call.playDtmfTone(step.key[0]) // Take the first character of the key
            
            // Stop the tone after a short duration
            Handler(mainLooper).postDelayed({
                call.stopDtmfTone()
                
                // Schedule the next step after the specified delay
                val delayMs = (step.delayAfterSeconds * 1000).toLong()
                Handler(mainLooper).postDelayed({
                    executeDtmfSequence(call, sequence, index + 1, disconnectAfter)
                }, delayMs)
                
            }, 300) // Play each tone for 300ms
        } else {
            // If key is empty, skip to next step
            val delayMs = (step.delayAfterSeconds * 1000).toLong()
            Handler(mainLooper).postDelayed({
                executeDtmfSequence(call, sequence, index + 1, disconnectAfter)
            }, delayMs)
        }
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

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)
        
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
                startActivity(CallActivity.getStartIntent(this, needSelectSIM = call.details.accountHandle == null))
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

