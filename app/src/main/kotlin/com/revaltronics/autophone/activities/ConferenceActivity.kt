package com.revaltronics.autophone.activities

import android.content.Intent
import android.os.Bundle
import com.revaltronics.commons.extensions.viewBinding
import com.revaltronics.commons.helpers.NavigationIcon
import com.revaltronics.autophone.adapters.ConferenceCallsAdapter
import com.revaltronics.autophone.databinding.ActivityConferenceBinding
import com.revaltronics.autophone.helpers.CallManager
import com.revaltronics.autophone.helpers.NoCall

class ConferenceActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityConferenceBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            updateMaterialActivityViews(conferenceCoordinator, conferenceList, useTransparentNavigation = true, useTopSearchMenu = false)
            setupMaterialScrollListener(conferenceList, conferenceToolbar)
            conferenceList.adapter = ConferenceCallsAdapter(this@ConferenceActivity, conferenceList, ArrayList(CallManager.getConferenceCalls())) {}
        }
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.conferenceToolbar, NavigationIcon.Arrow)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (CallManager.getPhoneState()) {
            NoCall -> {
                finishAndRemoveTask()
            }
            else -> {
                startActivity(Intent(this, CallActivity::class.java))
                super.onBackPressed()
            }
        }
    }
}
