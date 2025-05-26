package com.revaltronics.autophone.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.util.AttributeSet
import android.util.Log 
import android.util.TypedValue // Added for TypedValue.applyDimension
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.revaltronics.autophone.R
import com.revaltronics.autophone.activities.AutomationActivity
import com.revaltronics.autophone.adapters.AutomationRulesAdapter
import com.revaltronics.autophone.databases.AppDatabase
import com.revaltronics.autophone.databinding.FragmentAutoAnswerSettingsBinding
import com.revaltronics.autophone.interfaces.RefreshItemsListener
import com.revaltronics.autophone.models.SimpleAutomationSetting
import com.revaltronics.commons.extensions.beGone
import com.revaltronics.commons.extensions.beVisible
import com.revaltronics.commons.extensions.getProperBackgroundColor
import com.revaltronics.commons.extensions.getProperPrimaryColor
import com.revaltronics.commons.extensions.getProperTextColor
import com.revaltronics.commons.extensions.isVisible
import com.revaltronics.commons.views.MyRecyclerView
import com.revaltronics.commons.views.MyTextView
import kotlinx.coroutines.launch

class AutoAnswerSettingsFragment(context: Context, attributeSet: AttributeSet) :
    MyViewPagerFragment<AutoAnswerSettingsFragment.AutomationInnerBinding>(context, attributeSet),
    RefreshItemsListener {
    private lateinit var binding: FragmentAutoAnswerSettingsBinding
    private lateinit var appDatabase: AppDatabase
    private lateinit var rulesAdapter: AutomationRulesAdapter
    private var automationActivityLauncher: ActivityResultLauncher<Intent>? = null
    private var launcherRegistered = false
    val swipeWidth = 70f
    private var hostingFragment: Fragment? = null

    // Implementation for MyViewPagerFragment
    override fun myRecyclerView(): MyRecyclerView? {
        // Assuming R.id.auto_answer_list is a MyRecyclerView or can be safely cast.
        // If it's a standard RecyclerView, you might need to adjust MyViewPagerFragment
        // or ensure auto_answer_list is indeed a MyRecyclerView in your layout.
        return findViewById<MyRecyclerView>(R.id.auto_answer_list)
    }

    fun setHostingFragment(fragment: Fragment?) {
        if (fragment != null) {
            Log.d("AutoAnswerSettingsFragment", "setHostingFragment: Called with ${fragment::class.java.simpleName}")
            this.hostingFragment = fragment
            // Reset launcherRegistered status if hostingFragment changes, to allow new registration attempt
            if (launcherRegistered && automationActivityLauncher == null) {
                // This case implies previous registration might have been for a different context or failed subtly
                // Or if we want to strictly tie launcher to hostingFragment if provided
                Log.i("AutoAnswerSettingsFragment", "setHostingFragment: Resetting launcher registration due to new hosting fragment.")
                launcherRegistered = false
                automationActivityLauncher = null
            }
            tryRegisterLauncher(fragment) // Pass the specific owner
        } else {
            Log.d("AutoAnswerSettingsFragment", "setHostingFragment: Called with null, will try to find lifecycle owner automatically")
            tryRegisterLauncher() // Use default lifecycle owner lookup
        }
    }

    class AutomationInnerBinding(val binding: FragmentAutoAnswerSettingsBinding) : InnerBinding {
        override val fragmentList = null
        override val recentsList = null
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentAutoAnswerSettingsBinding.bind(this)
        innerBinding = AutomationInnerBinding(binding)
        if (!::appDatabase.isInitialized) {
            appDatabase = AppDatabase.getInstance(context.applicationContext)
        }
        Log.d("AutoAnswerSettingsFragment", "onFinishInflate: Called.")
        tryRegisterLauncher() // Attempt registration
    }

    private fun tryRegisterLauncher(ownerOverride: LifecycleOwner? = null) {
        if (launcherRegistered) {
            Log.d("AutoAnswerSettingsFragment", "tryRegisterLauncher: Launcher already registered.")
            return
        }

        val owner = ownerOverride ?: hostingFragment ?: findViewTreeLifecycleOwner()

        if (owner == null) {
            Log.w("AutoAnswerSettingsFragment", "tryRegisterLauncher: No LifecycleOwner available to register launcher.")
            return
        }

        // Only proceed if the lifecycle is at least in CREATED state
        // This check is correct and should remain to avoid registering when the lifecycle is too early
        if (!owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.CREATED)) {
            Log.w("AutoAnswerSettingsFragment", "tryRegisterLauncher: LifecycleOwner (${owner::class.java.simpleName}) is in state ${owner.lifecycle.currentState}, which is before CREATED. Cannot register launcher at this state.")
            return
        }
        
        // IMPORTANT: This was the previous error - we incorrectly checked if the state was STARTED or later,
        // and then returned, which prevented registration. We should only return if NOT at least CREATED.
        // The above check is sufficient, DO NOT add another check here that returns when state is STARTED or greater.

        Log.d("AutoAnswerSettingsFragment", "tryRegisterLauncher: Attempting registration with owner ${owner::class.java.simpleName} in state ${owner.lifecycle.currentState}.")
        try {
            val activityResultContract = ActivityResultContracts.StartActivityForResult()
            val callback: (ActivityResult) -> Unit = { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    Log.d("AutoAnswerSettingsFragment", "ActivityResultLauncher: Received RESULT_OK, loading rules.")
                    loadAutomationRules()
                } else {
                    Log.d("AutoAnswerSettingsFragment", "ActivityResultLauncher: Received result code: ${result.resultCode}")
                }
            }

            try {
                Log.d("AutoAnswerSettingsFragment", "Registering launcher with owner: ${owner::class.java.simpleName}, state: ${owner.lifecycle.currentState}")
                automationActivityLauncher = when (owner) {
                    is ComponentActivity -> {
                        Log.d("AutoAnswerSettingsFragment", "Owner is ComponentActivity, registering with activity")
                        owner.registerForActivityResult(activityResultContract, callback)
                    }
                    is Fragment -> {
                        Log.d("AutoAnswerSettingsFragment", "Owner is Fragment, registering with fragment")
                        owner.registerForActivityResult(activityResultContract, callback)
                    }
                    else -> {
                        Log.e("AutoAnswerSettingsFragment", "tryRegisterLauncher: LifecycleOwner is not a ComponentActivity or Fragment. Type: ${owner::class.java.simpleName}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoAnswerSettingsFragment", "Error registering activity launcher: ${e.message}", e)
                automationActivityLauncher = null
            }

            if (automationActivityLauncher != null) {
                launcherRegistered = true
                Log.i("AutoAnswerSettingsFragment", "Launcher registered successfully with ${owner::class.java.simpleName}.")
            } else if (owner is ComponentActivity || owner is Fragment) {
                // This case should ideally not be hit if owner is of correct type and no exception occurred
                Log.e("AutoAnswerSettingsFragment", "tryRegisterLauncher: Failed to register launcher with ${owner::class.java.simpleName} despite correct type and state.")
            }
        } catch (e: IllegalStateException) {
            // This catch should ideally not be hit due to the preemptive state check,
            // but is a safeguard.
            Log.e("AutoAnswerSettingsFragment", "tryRegisterLauncher: IllegalStateException during registerForActivityResult for owner ${owner::class.java.simpleName} (state: ${owner.lifecycle.currentState}): ${e.message}")
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!::appDatabase.isInitialized) {
            appDatabase = AppDatabase.getInstance(context.applicationContext)
        }
        Log.d("AutoAnswerSettingsFragment", "onAttachedToWindow: Called.")
        tryRegisterLauncher() // Attempt registration
        loadAutomationRules()
    }

    override fun setupFragment() {
        val textColor = context.getProperTextColor()
        val backgroundColor = context.getProperBackgroundColor()
        setBackgroundColor(backgroundColor)

        val emptyText = findViewById<MyTextView>(R.id.auto_answer_empty_text)
        val rulesList = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.auto_answer_list)

        emptyText?.setTextColor(textColor)
        setupRecyclerView(rulesList)

        emptyText?.beGone()
        rulesList?.beVisible()

        findViewById<MaterialButton>(R.id.auto_answer_cancel)?.beGone()
        findViewById<MaterialButton>(R.id.auto_answer_save)?.beGone()
        findViewById<MaterialButton>(R.id.auto_answer_add_dtmf)?.beGone()
        findViewById<ImageView>(R.id.auto_answer_pick_contact)?.beGone()
        findViewById<ScrollView>(R.id.auto_answer_editor)?.beGone()
    }

    private fun setupRecyclerView(rulesList: androidx.recyclerview.widget.RecyclerView?) {
        rulesList?.apply {
            layoutManager = LinearLayoutManager(context)
            if (!::rulesAdapter.isInitialized) {
                // Adapter now only takes onRuleClick for editing
                rulesAdapter = AutomationRulesAdapter(
                    onRuleClick = { rule ->
                        launchAutomationActivityForRule(rule.id)
                    }
                    // Removed onEditClick and onDeleteClick lambdas
                )
            }
            adapter = rulesAdapter

            val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false // We don't want to handle drag & drop
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    if (direction == ItemTouchHelper.LEFT) {
                        val position = viewHolder.adapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            val rule = rulesAdapter.currentList[position]
                            showDeleteConfirmationDialog(rule)
                            // The dialog's negative button listener should call notifyItemChanged
                            // to reset the swipe appearance if deletion is cancelled.
                        }
                    }
                }

                override fun onChildDraw(
                    c: Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    val itemView = viewHolder.itemView
                    val mainContent = itemView.findViewById<View>(R.id.main_content_container)
                    val actionButtons = itemView.findViewById<View>(R.id.swipe_actions_container)
                    val actionsWidthPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, swipeWidth, itemView.context.resources.displayMetrics
                    )

                    // If the item is idle and not being actively interacted with, reset and use default drawing.
                    if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && !isCurrentlyActive) {
                        // Ensure views are reset. Animate only if necessary.
                        if (mainContent.translationX != 0f) {
                            mainContent.animate().translationX(0f).setDuration(150).start()
                        }
                        if (actionButtons.isVisible()) {
                            actionButtons.animate().translationX(itemView.width.toFloat()).setDuration(150).withEndAction {
                                // Check if still relevant to hide (e.g., not swiped open again quickly)
                                if (mainContent.translationX == 0f) { 
                                    actionButtons.visibility = View.GONE
                                }
                            }.start()
                        } else {
                            // Ensure it's correctly positioned if already GONE (e.g. after a delete)
                            actionButtons.translationX = itemView.width.toFloat()
                        }
                        // Call super with original dX, dY for idle state to let ItemTouchHelper handle it.
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                        return
                    }

                    // Handle active swipe gestures
                    if (dX > 0) { // Swiping Right - prevent
                        mainContent.translationX = 0f
                        actionButtons.translationX = itemView.width.toFloat()
                        actionButtons.visibility = View.GONE
                        super.onChildDraw(c, recyclerView, viewHolder, 0f, dY, actionState, isCurrentlyActive)
                        return
                    }
                    
                    // Swiping Left (dX <= 0)
                    actionButtons.visibility = View.VISIBLE // Keep visible during swipe

                    val clampedDx = dX.coerceIn(-actionsWidthPx, 0f)
                    mainContent.translationX = clampedDx
                    actionButtons.translationX = itemView.width + clampedDx

                    if (!isCurrentlyActive) { // Snap logic when swipe gesture is released
                        if (clampedDx < -actionsWidthPx / 2) { // Swiped more than half
                            // Snap open (revealing the delete button)
                            mainContent.animate().translationX(-actionsWidthPx).setDuration(150).start()
                            actionButtons.animate().translationX(itemView.width - actionsWidthPx).setDuration(150).start()
                        } else { // Snap closed
                            mainContent.animate().translationX(0f).setDuration(150).start()
                            actionButtons.animate().translationX(itemView.width.toFloat()).setDuration(150).withEndAction {
                                actionButtons.visibility = View.GONE
                            }.start()
                        }
                    }
                    // For active left swipe, call super with dX=0 as we are manually handling X-translation.
                    super.onChildDraw(c, recyclerView, viewHolder, 0f, dY, actionState, isCurrentlyActive)
                }

                override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
                    // Trigger onSwiped if user swipes at least the width of the actions container (70dp)
                    val actionsWidthPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, swipeWidth, viewHolder.itemView.context.resources.displayMetrics
                    )
                    // If itemView width is 0, avoid division by zero, return default.
                    return if (viewHolder.itemView.width == 0) 0.5f else actionsWidthPx / viewHolder.itemView.width.toFloat()
                }

                override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
                    return Float.MAX_VALUE // Make it very hard to "fling" away and trigger swipe if not intended
                }

                override fun getSwipeVelocityThreshold(defaultValue: Float): Float {
                    return Float.MAX_VALUE // Require a deliberate swipe, not a fast fling
                }
            }
            val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    private fun launchAutomationActivityForRule(ruleId: Int?) {
        // Try to use MainActivity if available
        val mainActivity = activity as? Activity
        
        if (mainActivity != null) {
            // We have direct access to an activity, we can launch directly
            Log.d("AutoAnswerSettingsFragment", "launchAutomationActivityForRule: Using direct activity launch")
            val intent = Intent(context, AutomationActivity::class.java).apply {
                ruleId?.let { putExtra(AutomationActivity.EXTRA_SETTING_ID, it) }
            }
            try {
                mainActivity.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.e("AutoAnswerSettingsFragment", "Failed to launch activity directly: ${e.message}", e)
                // Fall back to launcher approach
            }
        }
        
        // Try using the activity result launcher
        if (!launcherRegistered) {
            Log.w("AutoAnswerSettingsFragment", "launchAutomationActivityForRule: Launcher not registered. Attempting registration now.")
            if (mainActivity != null) {
                // If we have a direct activity reference, use its lifecycle owner
                tryRegisterLauncher(mainActivity as? LifecycleOwner)
            } else {
                // Otherwise try with whatever is available
                tryRegisterLauncher()
            }
        }

        if (launcherRegistered && automationActivityLauncher != null) {
            Log.d("AutoAnswerSettingsFragment", "launchAutomationActivityForRule: Launching AutomationActivity for rule ID: $ruleId")
            val intent = Intent(context, AutomationActivity::class.java).apply {
                ruleId?.let { putExtra(AutomationActivity.EXTRA_SETTING_ID, it) }
            }
            try {
                automationActivityLauncher!!.launch(intent)
            } catch (e: Exception) {
                Log.e("AutoAnswerSettingsFragment", "Failed to launch with registered launcher: ${e.message}", e)
                fallbackLaunch(ruleId)
            }
        } else {
            Log.e("AutoAnswerSettingsFragment", "launchAutomationActivityForRule: Launcher not registered or null after attempt. Cannot start AutomationActivity. (Registered: $launcherRegistered, Launcher: $automationActivityLauncher)")
            fallbackLaunch(ruleId)
        }
    }
    
    private fun fallbackLaunch(ruleId: Int?) {
        // Last resort - try context.startActivity
        try {
            Log.d("AutoAnswerSettingsFragment", "Attempting fallback launch with context.startActivity")
            val intent = Intent(context, AutomationActivity::class.java).apply {
                ruleId?.let { putExtra(AutomationActivity.EXTRA_SETTING_ID, it) }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Required for launching from non-Activity context
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AutoAnswerSettingsFragment", "All launch attempts failed: ${e.message}", e)
            Toast.makeText(context, "Error: Could not prepare to edit rule.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeleteConfirmationDialog(rule: SimpleAutomationSetting) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.delete_rule_confirmation)
            .setMessage("Do you want to delete the rule for ${rule.contactName ?: rule.phoneNumber}?")
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                // Important: Notify adapter to redraw the item to hide swipe actions
                // and reset its state if deletion is cancelled.
                val position = rulesAdapter.currentList.indexOf(rule)
                if (position != -1) {
                    rulesAdapter.notifyItemChanged(position)
                }
            }
            .setPositiveButton(R.string.delete_rule) { dialog, _ ->
                deleteRule(rule)
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteRule(rule: SimpleAutomationSetting) {
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            appDatabase.simpleAutomationSettingDao().deleteSettingById(rule.id)
            
            // Remove the deleted rule from our stored list
            allAutomationRules = allAutomationRules.filter { it.id != rule.id }
            
            // If we have a search query active, apply the filter directly without reloading from database
            if (currentSearchQuery.isNotEmpty()) {
                val filteredRules = allAutomationRules.filter { searchRule ->
                    searchRule.phoneNumber.contains(currentSearchQuery, ignoreCase = true) ||
                    (searchRule.contactName?.contains(currentSearchQuery, ignoreCase = true) ?: false) ||
                    searchRule.dtmfSequence.any { it.key.contains(currentSearchQuery, ignoreCase = true) }
                }
                rulesAdapter.submitList(filteredRules.toMutableList())
                updateEmptyViewVisibility(filteredRules.isEmpty())
            } else {
                // No active search, just show all remaining rules
                rulesAdapter.submitList(allAutomationRules.toMutableList())
                updateEmptyViewVisibility(allAutomationRules.isEmpty())
            }
        }
    }
    
    private fun loadAutomationRules() {
        if (!::appDatabase.isInitialized) { 
            appDatabase = AppDatabase.getInstance(context.applicationContext)
        }

        val rulesListView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.auto_answer_list)
        if (!::rulesAdapter.isInitialized) {
            if (rulesListView != null) {
                setupRecyclerView(rulesListView)
            } else {
                Log.e("AutoAnswerSettingsFragment", "RecyclerView not found to initialize adapter in loadAutomationRules.")
                return 
            }
        }
        
        if (!::rulesAdapter.isInitialized) {
            Log.e("AutoAnswerSettingsFragment", "Adapter could not be initialized before loading rules.")
            return
        }

        // Use findViewTreeLifecycleOwner() to get the LifecycleScope
        findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            val rules = appDatabase.simpleAutomationSettingDao().getAllSettings()
            // Store all rules for search filtering
            allAutomationRules = rules
            
            // If we have a search query, apply it
            val displayedRules = if (currentSearchQuery.isNotEmpty()) {
                rules.filter { rule ->
                    rule.phoneNumber.contains(currentSearchQuery, ignoreCase = true) ||
                    (rule.contactName?.contains(currentSearchQuery, ignoreCase = true) ?: false) ||
                    rule.dtmfSequence.any { it.key.contains(currentSearchQuery, ignoreCase = true) }
                }
            } else {
                rules
            }
            
            // Ensure rulesAdapter is still the correct type and initialized
            if (::rulesAdapter.isInitialized && rulesAdapter is AutomationRulesAdapter) {
                rulesAdapter.submitList(displayedRules.toMutableList()) // submitList is part of ListAdapter
            } else {
                Log.e("AutoAnswerSettingsFragment", "rulesAdapter not an instance of AutomationRulesAdapter or not initialized in lifecycleScope.")
            }
            
            // Update empty view visibility based on filtered results
            updateEmptyViewVisibility(displayedRules.isEmpty())
        }
    }
    
    // Helper method to update empty view visibility
    private fun updateEmptyViewVisibility(isEmpty: Boolean) {
        val emptyText = findViewById<MyTextView>(R.id.auto_answer_empty_text)
        val rulesListView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.auto_answer_list)
        
        if (isEmpty) {
            // If we have a search query, show a "no results found" message
            if (currentSearchQuery.isNotEmpty()) {
                emptyText?.text = "No rules found matching \"$currentSearchQuery\""
            } else {
                emptyText?.text = context.getString(R.string.no_auto_answer_rules)
            }
            emptyText?.beVisible()
            rulesListView?.beGone()
        } else {
            emptyText?.beGone()
            rulesListView?.beVisible()
        }
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        // It's good practice to ensure the DB is ready before loading.
        if (!::appDatabase.isInitialized) {
            appDatabase = AppDatabase.getInstance(context.applicationContext)
        }
        loadAutomationRules()
        callback?.invoke()
    }
    
    // Keep a reference to all rules to make search easier
    private var allAutomationRules: List<SimpleAutomationSetting> = emptyList()
    private var currentSearchQuery: String = ""

    override fun onSearchClosed() {
        // When search is closed, reset the search query and display all rules
        currentSearchQuery = ""
        if (::rulesAdapter.isInitialized) {
            rulesAdapter.submitList(allAutomationRules.toMutableList())
            updateEmptyViewVisibility(allAutomationRules.isEmpty())
        } else {
            loadAutomationRules()
        }
    }

    override fun onSearchQueryChanged(text: String) {
        // Store the current search query
        currentSearchQuery = text.trim()
        
        if (!::rulesAdapter.isInitialized || allAutomationRules.isEmpty()) {
            // If adapter isn't initialized or we have no rules, there's nothing to filter
            return
        }
        
        // If search query is empty, show all rules
        if (currentSearchQuery.isEmpty()) {
            rulesAdapter.submitList(allAutomationRules.toMutableList())
            updateEmptyViewVisibility(allAutomationRules.isEmpty())
            return
        }
        
        // Filter rules based on search query
        val filteredRules = allAutomationRules.filter { rule ->
            // Search in phone number
            rule.phoneNumber.contains(currentSearchQuery, ignoreCase = true) ||
            // Search in contact name if it exists
            (rule.contactName?.contains(currentSearchQuery, ignoreCase = true) ?: false) ||
            // Search in DTMF keys
            rule.dtmfSequence.any { it.key.contains(currentSearchQuery, ignoreCase = true) }
        }
        
        // Update the adapter with filtered rules
        rulesAdapter.submitList(filteredRules.toMutableList())
        
        // Update empty view visibility based on filtered results
        updateEmptyViewVisibility(filteredRules.isEmpty())
    }

    // override fun onSortChanged() { /* ... */ }
    
    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        val properBackgroundColor = context.getProperBackgroundColor()
        // Setup colors for UI elements in this fragment
        findViewById<MyTextView>(R.id.auto_answer_empty_text)?.setTextColor(textColor)
        setBackgroundColor(properBackgroundColor) // Set background for the fragment itself

        // Explicitly set background for the RecyclerView as well
        findViewById<MyRecyclerView>(R.id.auto_answer_list)?.setBackgroundColor(properBackgroundColor)

        // If your rulesAdapter needs color updates, call its methods here
        if (::rulesAdapter.isInitialized) {
            rulesAdapter.updateTextColor(textColor) // Update adapter text color
            // rulesAdapter.updatePrimaryColor(properPrimaryColor) // If you add this to adapter
            // rulesAdapter.updateBackgroundColor(properBackgroundColor) // If adapter items need specific bg
        }
    }

}
