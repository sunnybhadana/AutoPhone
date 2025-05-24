package com.goodwy.autophone.activities

import android.content.Intent
import android.os.Bundle
import com.goodwy.commons.extensions.viewBinding
import com.goodwy.commons.helpers.NavigationIcon
import com.goodwy.autophone.adapters.ConferenceCallsAdapter
import com.goodwy.autophone.databinding.ActivityConferenceBinding
import com.goodwy.autophone.helpers.CallManager
import com.goodwy.autophone.helpers.NoCall

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
