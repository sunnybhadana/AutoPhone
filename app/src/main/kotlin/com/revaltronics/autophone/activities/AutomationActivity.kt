package com.revaltronics.autophone.activities

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteConstraintException
import android.graphics.Color
import android.os.Bundle
import android.provider.CalendarContract.Colors
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.revaltronics.autophone.adapters.AutomationRulesAdapter // Remove if not used

class AutomationActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityAutomationBinding::inflate)
    private lateinit var appDatabase: AppDatabase
    private val currentDtmfViews = mutableListOf<View>()
    private var currentSettingId: Int? = null
    private var selectedPhoneNumbers: List<String> = emptyList()

    private lateinit var requestContactPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var pickContactLauncher: ActivityResultLauncher<Intent>
    // private lateinit var rulesAdapter: AutomationRulesAdapter // No longer needed here

    companion object {
        const val EXTRA_SETTING_ID = "extra_setting_id"
    }

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

        binding.buttonPickContact.setOnClickListener {
            pickContact()
        }

        binding.buttonSaveAutomation.setOnClickListener {
            saveAutomationSettings()
        }

        binding.buttonAddDtmfStep.setOnClickListener {
            addDtmfStepView()
        }

        // binding.buttonAddNewRule.setOnClickListener { // No longer needed here
        //     showFormView() // No longer needed here
        //     resetFieldsAndPrepareForNew()
        // }

        // Retrieve setting ID from intent
        val settingIdFromIntent = intent.getIntExtra(EXTRA_SETTING_ID, 0)
        loadOrCreateAutomationSetting(settingIdFromIntent)

        // The form is always visible in this activity now
        // showFormView() // No longer needed, layout should be set up for form display by default
        supportActionBar?.title = if (settingIdFromIntent == 0) "Add New Rule" else "Edit Rule"

    }

    private fun setupContactPickerLaunchers() {
        requestContactPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchContactPicker()
            } else {
                Toast.makeText(this, "Contact permission denied. Cannot pick contacts.", Toast.LENGTH_LONG).show()
            }
        }

        pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { contactUri -> // contactUri is for the contact itself
                    var contactId: String? = null
                    var displayName: String? = null

                    // First, get the Contact ID and Display Name
                    contentResolver.query(contactUri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            contactId = cursor.getString(idIndex)
                            displayName = cursor.getString(nameIndex)
                        }
                    }

                    if (contactId != null && displayName != null) {
                        binding.editTextContactName.setText(displayName)
                        binding.textInputLayoutContactName.visibility = View.VISIBLE

                        // Now, get all phone numbers for this contactId
                        val tempPhoneNumbers = mutableListOf<String>()
                        val phoneProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val phoneQueryUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                        val phoneSelection = ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?"
                        val phoneSelectionArgs = arrayOf(contactId)

                        contentResolver.query(phoneQueryUri, phoneProjection, phoneSelection, phoneSelectionArgs, null)?.use { phoneCursor ->
                            val numberIndex = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            while (phoneCursor.moveToNext()) {
                                val number = phoneCursor.getString(numberIndex)
                                tempPhoneNumbers.add(normalizePhoneNumber(number)) // Assuming normalizePhoneNumber exists
                            }
                        }

                        if (tempPhoneNumbers.isNotEmpty()) {
                            selectedPhoneNumbers = tempPhoneNumbers // Store them
                            binding.editTextPhoneNumber.setText(selectedPhoneNumbers.joinToString(", "))
                            binding.editTextPhoneNumber.isEnabled = false // Make non-editable
                        } else {
                            selectedPhoneNumbers = emptyList()
                            binding.editTextPhoneNumber.setText("") // Clear if no numbers found
                            binding.editTextPhoneNumber.isEnabled = true // Make editable
                            Toast.makeText(this, "No phone numbers found for this contact.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Contact ID or display name not found, reset
                        selectedPhoneNumbers = emptyList()
                        binding.editTextContactName.setText("")
                        binding.textInputLayoutContactName.visibility = View.GONE
                        binding.editTextPhoneNumber.setText("")
                        binding.editTextPhoneNumber.isEnabled = true // Make editable
                        Toast.makeText(this, "Could not retrieve contact details.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun pickContact() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            launchContactPicker()
        } else {
            requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun launchContactPicker() {
        // Changed to pick a contact instead of a specific phone number
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    private fun saveAutomationSettings() {
        val contactNameToSave = binding.editTextContactName.text.toString().ifBlank { null }
        // val phoneNumber = binding.editTextPhoneNumber.text.toString().trim() // No longer read directly if selectedPhoneNumbers is used
        val pickupDelayString = binding.editTextPickupDelay.text.toString()
        val autoDisconnect = binding.switchDisconnectCall.isChecked

        // Phone number validation will happen inside the loop for multiple numbers
        // or before for a single number.

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

        val numbersToSave: List<String> = if (selectedPhoneNumbers.isNotEmpty()) {
            selectedPhoneNumbers
        } else {
            val manualNumber = binding.editTextPhoneNumber.text.toString().trim()
            if (manualNumber.isBlank()) {
                Toast.makeText(this, "Phone number cannot be empty", Toast.LENGTH_SHORT).show()
                return
            }
            listOf(normalizePhoneNumber(manualNumber))
        }

        if (numbersToSave.all { it.isBlank() }) {
            Toast.makeText(this, "No valid phone numbers to save.", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            var settingsSavedCount = 0
            var settingsFailedCount = 0
            val errors = mutableListOf<String>()

            if (selectedPhoneNumbers.isNotEmpty()) {
                // If contact name is blank but we have selected numbers from a contact,
                // it implies the contact had no name or failed to retrieve.
                // User should be prompted or it should be handled.
                // For now, we use contactNameToSave which could be null.
                // The SimpleAutomationSetting allows nullable contactName.

                selectedPhoneNumbers.forEach { number ->
                    val normalizedNumber = normalizePhoneNumber(number) // Ensure normalization
                    if (normalizedNumber.isBlank()) {
                        settingsFailedCount++
                        errors.add("A selected number was blank.")
                        return@forEach // Skip blank numbers
                    }
                    val settingToSave = SimpleAutomationSetting(
                        id = 0, // Always new when saving multiple from contact
                        contactName = contactNameToSave, // This comes from editTextContactName
                        phoneNumber = normalizedNumber,
                        pickupDelaySeconds = pickupDelay,
                        autoDisconnectCall = autoDisconnect,
                        dtmfSequence = dtmfSequence
                    )
                    try {
                        appDatabase.simpleAutomationSettingDao().insertOrUpdateSetting(settingToSave)
                        settingsSavedCount++
                    } catch (e: SQLiteConstraintException) {
                        settingsFailedCount++
                        errors.add("Number $normalizedNumber already configured.")
                    } catch (e: Exception) {
                        settingsFailedCount++
                        errors.add("Error saving $normalizedNumber: ${e.localizedMessage}")
                    }
                }
            } else { // Fallback to manual entry in editTextPhoneNumber
                val phoneNumberFromInput = normalizePhoneNumber(binding.editTextPhoneNumber.text.toString().trim())
                if (phoneNumberFromInput.isBlank()) {
                    Toast.makeText(this@AutomationActivity, "Phone number cannot be empty", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val settingToSave = SimpleAutomationSetting(
                    id = currentSettingId ?: 0,
                    contactName = contactNameToSave,
                    phoneNumber = phoneNumberFromInput,
                    pickupDelaySeconds = pickupDelay,
                    autoDisconnectCall = autoDisconnect,
                    dtmfSequence = dtmfSequence
                )
                try {
                    if (currentSettingId == null || currentSettingId == 0) { // New setting
                        appDatabase.simpleAutomationSettingDao().insertOrUpdateSetting(settingToSave.copy(id = 0))
                    } else { // Existing setting
                        appDatabase.simpleAutomationSettingDao().insertOrUpdateSetting(settingToSave)
                    }
                    settingsSavedCount++
                } catch (e: SQLiteConstraintException) {
                    settingsFailedCount++
                    errors.add("Number $phoneNumberFromInput already configured.")
                } catch (e: Exception) {
                    settingsFailedCount++
                    errors.add("Error saving $phoneNumberFromInput: ${e.localizedMessage}")
                }
            }

            // Report results
            var message = ""
            if (settingsSavedCount > 0) {
                message += "$settingsSavedCount setting(s) saved. "
            }
            if (settingsFailedCount > 0) {
                message += "$settingsFailedCount setting(s) failed. (${errors.joinToString("; ")})"
            }
            if (message.isBlank()) {
                message = "No settings were changed."
            }

            Toast.makeText(this@AutomationActivity, message.trim(), Toast.LENGTH_LONG).show()

            if (settingsSavedCount > 0) {
                setResult(Activity.RESULT_OK) // Set result for the calling fragment/activity
                finish() // Close activity after successful save
            } else {
                // If save failed or no changes, do not finish, allow user to correct.
                // Consider if RESULT_CANCELED should be set in some failure cases before finishing,
                // but typically, if the activity isn't finished, no result is sent yet.
            }
        }
    }

    private fun loadOrCreateAutomationSetting(settingId: Int = 0) {
        currentSettingId = if (settingId == 0) null else settingId
        supportActionBar?.title = if (currentSettingId == null) "Add New Rule" else "Edit Rule"

        lifecycleScope.launch {
            if (settingId != 0) {
                val setting = appDatabase.simpleAutomationSettingDao().getSettingById(settingId)
                if (setting != null) {
                    currentSettingId = setting.id
                    binding.editTextContactName.setText(setting.contactName ?: "")
                    binding.textInputLayoutContactName.visibility = if (setting.contactName.isNullOrEmpty()) View.GONE else View.VISIBLE
                    binding.editTextPhoneNumber.setText(setting.phoneNumber)
                    binding.editTextPhoneNumber.isEnabled = true // When editing, phone number should be editable
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
                currentSettingId = null
                resetFieldsAndPrepareForNew()
            }
            // Ensure form is visible (though it should be by default now)
            // showFormView() // No longer needed
        }
    }

    private fun resetFieldsAndPrepareForNew() {
        currentSettingId = null
        selectedPhoneNumbers = emptyList() // Clear the selected numbers
        binding.editTextContactName.setText("")
        binding.textInputLayoutContactName.visibility = View.GONE
        binding.editTextPhoneNumber.setText("")
        binding.editTextPhoneNumber.isEnabled = true // Make editable for new manual entry
        binding.editTextPickupDelay.setText("0")
        binding.switchDisconnectCall.isChecked = false
        binding.linearLayoutDtmf.removeAllViews()
        currentDtmfViews.clear()
        binding.editTextPhoneNumber.requestFocus()
        supportActionBar?.title = "Add New Rule"
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

    // It's good practice to have normalizePhoneNumber accessible, e.g. as a private fun or extension
    // Assuming it's defined elsewhere or like this:
    private fun normalizePhoneNumber(number: String): String {
        // Example: Remove all non-numeric characters except leading '+'
        val digits = number.filter { it.isDigit() }
        return if (number.startsWith("+")) "+$digits" else digits
    }
}

