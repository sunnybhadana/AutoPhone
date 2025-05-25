package com.revaltronics.autophone.services

import android.os.Handler
import android.telecom.CallAudioState
import android.telecom.Call
import android.telecom.InCallService
import com.revaltronics.autophone.activities.CallActivity
import com.revaltronics.autophone.extensions.config
import com.revaltronics.autophone.extensions.isOutgoing
import com.revaltronics.autophone.extensions.powerManager
import com.revaltronics.autophone.helpers.*
import com.revaltronics.autophone.models.Events
import org.greenrobot.eventbus.EventBus

class CallService : InCallService() {
    private val callNotificationManager by lazy { CallNotificationManager(this) }

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

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.onCallAdded(call)
        CallManager.inCallService = this
        call.registerCallback(callListener)
        val incomingNumber = call.details.handle?.schemeSpecificPart
        // Replace with your desired number(s)
        val targetNumbers = listOf("+911234567890", "1234567890")
        if (!call.isOutgoing() && incomingNumber in targetNumbers) {
            // Auto-answer
            call.answer(0)

            // Send DTMF after answering, then hang up
            Handler(mainLooper).postDelayed({
                call.playDtmfTone('3')
                Handler(mainLooper).postDelayed({
                    call.stopDtmfTone()
                    call.playDtmfTone('4')
                    Handler(mainLooper).postDelayed({
                        call.stopDtmfTone()
                        call.disconnect()
                    }, 1000) // Delay before hanging up
                }, 1000) // Delay between DTMF tones
            }, 2000) // Delay after answering before sending DTMF
        } else {
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

