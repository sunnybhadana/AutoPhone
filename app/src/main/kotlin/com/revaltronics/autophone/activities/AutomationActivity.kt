package com.revaltronics.autophone.activities

import android.graphics.Color
import android.os.Bundle
import android.provider.CalendarContract.Colors
import com.revaltronics.autophone.databinding.ActivityAutomationBinding
import com.revaltronics.autophone.extensions.config
import com.revaltronics.commons.extensions.*

class AutomationActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityAutomationBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true // Consistent with MainActivity's setup
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        // Setup Toolbar as ActionBar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true) // Show back arrow icon
            setDisplayShowHomeEnabled(true) // Ensure it's shown
        }
        // Handle navigation icon click (e.g., back press)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val properBackgroundColor = getProperBackgroundColor()
        val properTextColor = getProperTextColor()

        // Apply background colors
        binding.mainLayout.setBackgroundColor(properBackgroundColor)
        binding.appBarLayout.setBackgroundColor(properBackgroundColor)

        // This existing call should handle the toolbar's background color.
        updateActionbarColor(properBackgroundColor)

        // Apply text/icon colors to Toolbar
        binding.toolbar.setTitleTextColor(properTextColor)
        // Ensure the navigation icon drawable is mutated before tinting to avoid affecting other instances
        binding.toolbar.navigationIcon?.mutate()?.setTint(properTextColor)

        // Update text colors for the main content area - this should cover many views
        updateTextColors(binding.mainHolder)

        // Explicitly update specific text views and other elements
        binding.apply {
            // Ensure these specific MaterialTextViews get the correct color
            arrayOf(
                buttonAddDtmfStep, buttonSaveAutomation
            ).forEach {
                it.setTextColor(Color.WHITE)
                it.setBackgroundColor(getProperPrimaryColor())
            }

            // Update TextInputLayout hint colors
            textInputLayoutPhoneNumber.defaultHintTextColor = properTextColor.getColorStateList()
            textInputLayoutPickupDelay.defaultHintTextColor = properTextColor.getColorStateList()
//            buttonAddDtmfStep.setBackgroundColor(getProperPrimaryColor())
//            buttonAddDtmfStep.setTextColor(Color.WHITE)
            // If you have dynamically added DTMF TextInputLayouts, they would need similar treatment

            // Update SwitchMaterial text color
            switchDisconnectCall.setTextColor(properTextColor)

            // TextInputEditText text colors should be handled by updateTextColors(binding.mainHolder)
            // or you can set them explicitly if needed:
            // editTextPhoneNumber.setTextColor(properTextColor)
            // editTextPickupDelay.setTextColor(properTextColor)

            // Button text colors are typically handled by updateTextColors or theme attributes.
            // Icon tints for buttons might need specific handling if they don't follow properTextColor:
            // Example: buttonPickContact.iconTint = ColorStateList.valueOf(properPrimaryColor) // If you want primary for this
            // buttonRemoveDtmfStep.imageTintList = ColorStateList.valueOf(yourErrorColor) // If using a specific error color
        }

        // TODO: Add specific functionality for AutomationActivity here
        // For example, setting up listeners or loading automation tasks.
    }
}

