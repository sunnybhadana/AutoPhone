package com.revaltronics.autophone.fragments

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.revaltronics.autophone.R
import com.revaltronics.autophone.databinding.FragmentAutoAnswerSettingsBinding
import com.revaltronics.autophone.interfaces.RefreshItemsListener
import com.revaltronics.commons.extensions.beGone
import com.revaltronics.commons.extensions.beVisible
import com.revaltronics.commons.extensions.getProperBackgroundColor
import com.revaltronics.commons.extensions.getProperPrimaryColor
import com.revaltronics.commons.extensions.getProperTextColor
import com.revaltronics.commons.views.MyRecyclerView
import com.revaltronics.commons.views.MyTextView

class AutoAnswerSettingsFragment(context: Context, attributeSet: AttributeSet) : 
    MyViewPagerFragment<AutoAnswerSettingsFragment.AutomationInnerBinding>(context, attributeSet),
    RefreshItemsListener {
    private lateinit var binding: FragmentAutoAnswerSettingsBinding
    
    class AutomationInnerBinding(val binding: FragmentAutoAnswerSettingsBinding) : InnerBinding {
        override val fragmentList = null
        override val recentsList = null
    }
    
    override fun onFinishInflate() {
        super.onFinishInflate()
        binding = FragmentAutoAnswerSettingsBinding.bind(this)
        innerBinding = AutomationInnerBinding(binding)
    }

    override fun setupFragment() {
        val textColor = context.getProperTextColor()
        val backgroundColor = context.getProperBackgroundColor()
        val primaryColor = context.getProperPrimaryColor()
        // Set background color
        setBackgroundColor(backgroundColor)
        
        // Setup UI elements
        val emptyText = findViewById<MyTextView>(R.id.auto_answer_empty_text)
        val rulesList = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.auto_answer_list)
        val editorView = findViewById<ScrollView>(R.id.auto_answer_editor)
        
        // Set text colors
        emptyText?.setTextColor(textColor)
        
        // Set up RecyclerView (empty for now)
        rulesList?.layoutManager = LinearLayoutManager(context)
        // For demo purposes, show empty state
        emptyText?.beVisible()
        rulesList?.beGone()

        
        // Setup editor buttons
        val cancelButton = findViewById<MaterialButton>(R.id.auto_answer_cancel)
        val saveButton = findViewById<MaterialButton>(R.id.auto_answer_save)
        val addDtmfButton = findViewById<MaterialButton>(R.id.auto_answer_add_dtmf)
        val pickContactButton = findViewById<ImageView>(R.id.auto_answer_pick_contact)
        
        cancelButton?.setOnClickListener {
            showEditor(false)
        }
        
        saveButton?.setOnClickListener {
            // This would save the rule - just hiding editor for now
            showEditor(false)
        }
        
        addDtmfButton?.setOnClickListener {
            // This would add a DTMF key to the sequence
            // UI-only implementation - no backend functionality
        }
        
        pickContactButton?.setOnClickListener {
            // This would show contact picker
            // UI-only implementation - no backend functionality
        }
        
        // Initially hide the editor
        showEditor(false)
    }
    
    private fun showEditor(show: Boolean) {
        val editorView = findViewById<ScrollView>(R.id.auto_answer_editor)
        if (show) {
            editorView?.beVisible()
        } else {
            editorView?.beGone()
        }
    }

    override fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int) {
        // Update colors when theme changes
        setBackgroundColor(context.getProperBackgroundColor())
        
        // Update text colors
        val emptyText = findViewById<MyTextView>(R.id.auto_answer_empty_text)
        val editorTitle = findViewById<MyTextView>(R.id.auto_answer_editor_title)

        emptyText?.setTextColor(textColor)
        editorTitle?.setTextColor(textColor)
    }

    override fun onSearchClosed() {
        // Handle search closure if needed
    }

    override fun onSearchQueryChanged(text: String) {
        // Handle search query changes if needed
    }

    override fun myRecyclerView(): MyRecyclerView? {
        // This fragment doesn't use a RecyclerView, so return null
        return null
    }

    override fun refreshItems(invalidate: Boolean, callback: (() -> Unit)?) {
        // No items to refresh in a static UI
        callback?.invoke()
    }
}
