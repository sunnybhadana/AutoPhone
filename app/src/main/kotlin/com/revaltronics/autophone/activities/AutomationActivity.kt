package com.revaltronics.autophone.activities

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.graphics.Color
import android.os.Bundle
import android.provider.CalendarContract.Colors
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.revaltronics.autophone.R
import com.revaltronics.autophone.databases.AppDatabase
import com.revaltronics.autophone.databinding.ActivityAutomationBinding
import com.revaltronics.autophone.databinding.ItemDtmfStepBinding // Import the binding for the item
import com.revaltronics.autophone.extensions.config
import com.revaltronics.autophone.models.DtmfStep
import com.revaltronics.autophone.models.SimpleAutomationSetting
import com.revaltronics.commons.extensions.*
import kotlinx.coroutines.launch

class AutomationActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityAutomationBinding::inflate)
    private lateinit var appDatabase: AppDatabase
    private val currentDtmfViews = mutableListOf<View>() // To keep track of DTMF views
    private var currentSettingId: Int? = null // To keep track of the current setting being edited

    override fun onCreate(savedInstanceState: Bundle?) {
        // ... existing onCreate setup ...
        isMaterialActivity = true // Consistent with MainActivity\\\'s setup
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        appDatabase = AppDatabase.getInstance(applicationContext) // Initialize AppDatabase

        // Setup Toolbar as ActionBar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true) // Show back arrow icon
            setDisplayShowHomeEnabled(true) // Ensure it\\\'s shown
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

        // This existing call should handle the toolbar\\\'s background color.
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
                textDtmf
            ).forEach {
                it.setTextColor(properTextColor)
            }

            // Update TextInputLayout hint colors
            textInputLayoutPhoneNumber.defaultHintTextColor = properTextColor.getColorStateList()
            textInputLayoutPickupDelay.defaultHintTextColor = properTextColor.getColorStateList()
            textInputLayoutContactName.defaultHintTextColor = properTextColor.getColorStateList()
            // If you have dynamically added DTMF TextInputLayouts, they would need similar treatment

            // Update SwitchMaterial text color
            switchDisconnectCall.setTextColor(properTextColor)
        }

        binding.buttonSaveAutomation.setOnClickListener {
            saveAutomationSettings()
        }

        binding.buttonAddDtmfStep.setOnClickListener {
            addDtmfStepView()
        }

        // Consider adding a button to create a new rule, which would call resetFields()
        // For now, load the first rule by default or a new one if none exist.
        loadOrCreateAutomationSetting()
    }

    private fun saveAutomationSettings() {
        val contactName = binding.editTextContactName.text.toString()
        val phoneNumber = binding.editTextPhoneNumber.text.toString().trim() // Now a single phone number
        val pickupDelayString = binding.editTextPickupDelay.text.toString()
        val autoDisconnect = binding.switchDisconnectCall.isChecked

        if (phoneNumber.isBlank()) {
            Toast.makeText(this, "Phone number cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val pickupDelay = pickupDelayString.toIntOrNull() ?: 0

        val dtmfSequence = mutableListOf<DtmfStep>()
        currentDtmfViews.forEach { view ->
            val itemBinding = ItemDtmfStepBinding.bind(view)
            val key = itemBinding.editTextDtmfKey.text.toString()
            val delayAfter = itemBinding.editTextDtmfDelay.text.toString().toFloatOrNull() ?: 0f
            if (key.isNotBlank()) {
                dtmfSequence.add(DtmfStep(key, delayAfter))
            }
        }

        lifecycleScope.launch {
            try {
                val settingToSave = SimpleAutomationSetting(
                    id = currentSettingId ?: 0,
                    contactName = contactName.ifBlank { null },
                    phoneNumber = phoneNumber, // Use the single phone number
                    pickupDelaySeconds = pickupDelay,
                    autoDisconnectCall = autoDisconnect,
                    dtmfSequence = dtmfSequence
                )
                if (currentSettingId == null || currentSettingId == 0) { // New setting
                    appDatabase.simpleAutomationSettingDao().insertOrUpdateSetting(settingToSave.copy(id = 0))
                } else { // Existing setting
                    appDatabase.simpleAutomationSettingDao().insertOrUpdateSetting(settingToSave)
                }
                Toast.makeText(this@AutomationActivity, "Automation settings saved", Toast.LENGTH_SHORT).show()
                resetFieldsAndPrepareForNew() // Reset fields after saving
            } catch (e: SQLiteConstraintException) {
                Toast.makeText(this@AutomationActivity, "Error: This phone number is already configured.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@AutomationActivity, "Error saving settings: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadOrCreateAutomationSetting(settingId: Int = 0) { // Default to 0 to signify new/last or specific load
        lifecycleScope.launch {
            // If settingId is 0 or not provided, you might want to load the last edited, or a list.
            // For now, if ID is 0, it prepares for a new entry. If ID is specific, it loads that.
            // This part will need refinement when you implement a list/selection UI for multiple rules.
            if (settingId != 0) {
                val setting = appDatabase.simpleAutomationSettingDao().getSettingById(settingId)
                if (setting != null) {
                    currentSettingId = setting.id
                    binding.editTextContactName.setText(setting.contactName ?: "")
                    binding.textInputLayoutContactName.visibility = if (setting.contactName.isNullOrEmpty()) View.GONE else View.VISIBLE
                    binding.editTextPhoneNumber.setText(setting.phoneNumber) // Set single phone number
                    binding.editTextPickupDelay.setText(setting.pickupDelaySeconds.toString())
                    binding.switchDisconnectCall.isChecked = setting.autoDisconnectCall

                    binding.linearLayoutDtmf.removeAllViews()
                    currentDtmfViews.clear()
                    setting.dtmfSequence.forEach { addDtmfStepView(it) }
                } else {
                    // Setting with specific ID not found, prepare for new
                    currentSettingId = null
                    resetFieldsAndPrepareForNew()
                    Toast.makeText(this@AutomationActivity, "Setting not found, creating new.", Toast.LENGTH_SHORT).show()
                }
            } else {
                // No specific ID, prepare for a new one
                currentSettingId = null
                resetFieldsAndPrepareForNew()
            }
        }
    }

    private fun resetFieldsAndPrepareForNew() {
        currentSettingId = null
        binding.editTextContactName.setText("")
        binding.textInputLayoutContactName.visibility = View.GONE // Ensure it's hidden
        binding.editTextPhoneNumber.setText("")
        binding.editTextPickupDelay.setText("0")
        binding.switchDisconnectCall.isChecked = false
        binding.linearLayoutDtmf.removeAllViews()
        currentDtmfViews.clear()
        binding.editTextPhoneNumber.requestFocus()
    }


    // Renamed from loadAutomationSettings to reflect it might load a specific or new one
    // Keep the old loadAutomationSettings logic if you intend to always load a single, fixed setting.
    // For multiple settings, you'll need a UI to list and select them.
    // This example simplifies to loading ID 1 or preparing for a new entry.

//    private fun loadAutomationSettings() { // Original function, can be removed or adapted
//        lifecycleScope.launch {
//            val setting = appDatabase.simpleAutomationSettingDao().getSetting() // This gets the one with ID=1
//            setting?.let {
//                currentSettingId = it.id // Store the ID
//                binding.editTextContactName.setText(it.contactName ?: "") // Assuming editTextContactName exists
//                binding.editTextPhoneNumber.setText(it.phoneNumbers.joinToString(\", \"))
//                binding.editTextPickupDelay.setText(it.pickupDelaySeconds.toString())
//                binding.switchDisconnectCall.isChecked = it.autoDisconnectCall
//
//                binding.linearLayoutDtmf.removeAllViews()
//                currentDtmfViews.clear()
//                it.dtmfSequence.forEach { dtmfStep ->
//                    addDtmfStepView(dtmfStep)
//                }
//            }
//        }
//    }

    private fun addDtmfStepView(dtmfStep: DtmfStep? = null) {
        val inflater = LayoutInflater.from(this)
        // Ensure you have a layout file named 'item_dtmf_step.xml'
        // This layout should contain TextInputEditText for key and delay, and an ImageButton for removal.
        // Example: R.layout.item_dtmf_step
        val itemBinding = ItemDtmfStepBinding.inflate(inflater, binding.linearLayoutDtmf, false)

        dtmfStep?.let {
            itemBinding.editTextDtmfKey.setText(it.key)
            itemBinding.editTextDtmfDelay.setText(it.delayAfterSeconds.toString())
        }

        // Apply text color to new views
        val properTextColor = getProperTextColor()
        itemBinding.editTextDtmfKey.setTextColor(properTextColor)
        itemBinding.editTextDtmfDelay.setTextColor(properTextColor)
        itemBinding.textInputLayoutDtmfKey.defaultHintTextColor = properTextColor.getColorStateList()
        itemBinding.textInputLayoutDtmfDelay.defaultHintTextColor = properTextColor.getColorStateList()


        itemBinding.buttonRemoveDtmfStep.setOnClickListener {
            binding.linearLayoutDtmf.removeView(itemBinding.root)
            currentDtmfViews.remove(itemBinding.root)
        }

        binding.linearLayoutDtmf.addView(itemBinding.root)
        currentDtmfViews.add(itemBinding.root)
    }
}

