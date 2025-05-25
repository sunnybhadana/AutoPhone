package com.revaltronics.autophone.activities

import android.os.Bundle
import com.revaltronics.autophone.databinding.ActivityAutomationBinding
import com.revaltronics.commons.extensions.viewBinding

class AutomationActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityAutomationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
    }
}

