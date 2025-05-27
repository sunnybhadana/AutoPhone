package com.revaltronics.autophone.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revaltronics.autophone.R // Import R for string resources
import com.revaltronics.autophone.databinding.ItemAutomationRuleBinding
import com.revaltronics.autophone.models.SimpleAutomationSetting

class AutomationRulesAdapter(
    private val onRuleClick: (SimpleAutomationSetting) -> Unit,
    private val onRuleLongClick: (SimpleAutomationSetting) -> Boolean = { false }, // Added for toggling active state
    private val getBatchSize: suspend (String) -> Int = { 0 } // Function to get batch size
) : ListAdapter<SimpleAutomationSetting, AutomationRulesAdapter.RuleViewHolder>(RuleDiffCallback()) {

    private var textColor: Int = Color.BLACK // Default color
    private val batchSizes = mutableMapOf<String, Int>() // Cache for batch sizes
    private var recyclerView: RecyclerView? = null // Store reference to RecyclerView

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemAutomationRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RuleViewHolder(binding)
    }
    
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }
    
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        val rule = getItem(position)
        holder.bind(rule, textColor, batchSizes[rule.batch_group_id] ?: 0)
        
        // Ensure the view is in its reset state (not in swiped position)
        resetViewHolderSwipeState(holder)
        
        // Main content click triggers the onRuleClick (for editing via AutomationActivity)
        holder.binding.mainContentContainer.setOnClickListener { onRuleClick(rule) }
        
        // Long press to toggle active state
        holder.binding.mainContentContainer.setOnLongClickListener { onRuleLongClick(rule) }
    }
    
    /**
     * Reset the swipe state of a view holder
     * This ensures recycled views aren't stuck in swiped position
     */
    private fun resetViewHolderSwipeState(holder: RuleViewHolder) {
        val mainContent = holder.binding.mainContentContainer
        val actionButtons = holder.binding.root.findViewById<View>(R.id.swipe_actions_container)
        
        // Reset translation to normal state
        mainContent.translationX = 0f
        actionButtons.visibility = View.GONE
        actionButtons.translationX = holder.itemView.width.toFloat()
    }
    
    /**
     * Set the batch size for a specific batch group
     * @param batchGroupId The batch group ID
     * @param size The size of the batch group
     */
    fun setBatchSize(batchGroupId: String, size: Int) {
        if (batchGroupId.isNotEmpty() && size > 0) {
            batchSizes[batchGroupId] = size
            notifyDataSetChanged() // Update display to show batch sizes
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateTextColor(color: Int) {
        textColor = color
        notifyDataSetChanged() // Notify adapter to rebind views with new color
    }

    inner class RuleViewHolder(val binding: ItemAutomationRuleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: SimpleAutomationSetting, textColor: Int, batchSize: Int = 0) { // Added batchSize param
            // Remove the alpha change, keeping the UI consistent regardless of active state
            binding.mainContentContainer.alpha = 1.0f
            
            // Display contact name with batch count if applicable
            val contactName = rule.contactName ?: itemView.context.getString(R.string.no_contact_name)
            val displayName = if (rule.batch_group_id.isNotEmpty() && batchSize > 1) {
                itemView.context.getString(R.string.batch_contact_format, contactName, batchSize - 1)
            } else {
                contactName
            }
            
            binding.textViewContactNameRule.text = displayName
            binding.textViewContactNameRule.setTextColor(textColor) // Apply textColor

            // For batch groups, clearly indicate this is a group by showing the count
            val phoneNumber = if (rule.batch_group_id.isNotEmpty() && batchSize > 1) {
                itemView.context.getString(R.string.batch_number_format, rule.phoneNumber, batchSize - 1)
            } else {
                rule.phoneNumber
            }
            binding.textViewPhoneNumberRule.text = phoneNumber
            binding.textViewPhoneNumberRule.setTextColor(textColor) // Apply textColor

            binding.textViewPickupDelayRule.text = itemView.context.getString(R.string.pickup_delay_formatted, rule.pickupDelaySeconds)
            binding.textViewPickupDelayRule.setTextColor(textColor) // Apply textColor

            val autoDisconnectText = if (rule.autoDisconnectCall) itemView.context.getString(R.string.yes) else itemView.context.getString(R.string.no)
            binding.textViewAutoDisconnectRule.text = itemView.context.getString(R.string.auto_disconnect_formatted, autoDisconnectText)
            binding.textViewAutoDisconnectRule.setTextColor(textColor) // Apply textColor
            
            // Format and display auto-answer limits
            val limitsText = when {
                rule.max_auto_answers <= 0 -> itemView.context.getString(R.string.unlimited)
                rule.reset_interval_minutes <= 0 -> "${rule.answered_calls_count}/${rule.max_auto_answers} (no reset)"
                else -> "${rule.answered_calls_count}/${rule.max_auto_answers} (resets every ${rule.reset_interval_minutes} min)"
            }
            binding.textViewAutoAnswerLimitsRule.text = itemView.context.getString(R.string.auto_answer_limits_formatted, limitsText)
            binding.textViewAutoAnswerLimitsRule.setTextColor(textColor) // Apply textColor
            
            // Display active/inactive status and batch info if applicable
            val baseStatusText = if (rule.isActive) 
                itemView.context.getString(R.string.status_active) 
            else 
                itemView.context.getString(R.string.status_inactive)
                
            // Add batch info to status if applicable
            val statusText = if (rule.batch_group_id.isNotEmpty() && batchSize > 1) {
                "$baseStatusText · Batch group with $batchSize numbers"
            } else {
                baseStatusText
            }
            
            // Use standard material colors: green for active, red for inactive
            val statusColor = if (rule.isActive) 
                Color.parseColor("#43A047")  // Material Green 600 - slightly darker for better readability
            else 
                Color.parseColor("#E53935")  // Material Red 600 - slightly darker for better readability
                
            // Make text bold for better visibility
            binding.textViewRuleStatus.setTypeface(binding.textViewRuleStatus.typeface, android.graphics.Typeface.BOLD)
                
            binding.textViewRuleStatus.text = statusText
            binding.textViewRuleStatus.setTextColor(statusColor)
        }
    }

    override fun submitList(list: List<SimpleAutomationSetting>?) {
        // Use the super implementation to handle list diff and updates
        super.submitList(list)
        
        // After the list is submitted, reset all visible views in the recycler view
        resetAllVisibleItems()
    }
    
    /**
     * Reset the swipe state of all visible items in the RecyclerView
     * This ensures no views are stuck in swiped position after list updates
     */
    private fun resetAllVisibleItems() {
        recyclerView?.let { rv ->
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i)
                val viewHolder = rv.getChildViewHolder(child) as? RuleViewHolder ?: continue
                
                resetViewHolderSwipeState(viewHolder)
            }
        }
    }

    class RuleDiffCallback : DiffUtil.ItemCallback<SimpleAutomationSetting>() {
        override fun areItemsTheSame(oldItem: SimpleAutomationSetting, newItem: SimpleAutomationSetting):
                Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SimpleAutomationSetting, newItem: SimpleAutomationSetting):
                Boolean = oldItem == newItem
    }
}
