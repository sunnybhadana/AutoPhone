package com.goodwy.autophone.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.goodwy.autophone.activities.CallActivity
import com.goodwy.autophone.extensions.audioManager
import com.goodwy.autophone.helpers.ACCEPT_CALL
import com.goodwy.autophone.helpers.CallManager
import com.goodwy.autophone.helpers.DECLINE_CALL
import com.goodwy.autophone.helpers.MICROPHONE_CALL

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACCEPT_CALL -> {
                context.startActivity(CallActivity.getStartIntent(context))
                CallManager.accept()
            }

            DECLINE_CALL -> CallManager.reject()
            MICROPHONE_CALL -> {
                val isMicrophoneMute = context.audioManager.isMicrophoneMute
                CallManager.inCallService?.setMuted(!isMicrophoneMute)
            }
        }
    }
}
