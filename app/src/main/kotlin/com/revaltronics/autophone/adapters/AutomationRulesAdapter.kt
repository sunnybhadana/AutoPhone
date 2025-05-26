package com.revaltronics.autophone.adapters

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.revaltronics.autophone.R // Import R for string resources
import com.revaltronics.autophone.databinding.ItemAutomationRuleBinding
import com.revaltronics.autophone.models.SimpleAutomationSetting

class AutomationRulesAdapter(
    private val onRuleClick: (SimpleAutomationSetting) -> Unit,
    private val onRuleLongClick: (SimpleAutomationSetting) -> Boolean = { false } // Added for toggling active state
) : ListAdapter<SimpleAutomationSetting, AutomationRulesAdapter.RuleViewHolder>(RuleDiffCallback()) {

    private var textColor: Int = Color.BLACK // Default color

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RuleViewHolder {
        val binding = ItemAutomationRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RuleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RuleViewHolder, position: Int) {
        val rule = getItem(position)
        holder.bind(rule, textColor)
        // Main content click triggers the onRuleClick (for editing via AutomationActivity)
        holder.binding.mainContentContainer.setOnClickListener { onRuleClick(rule) }
        
        // Long press to toggle active state
        holder.binding.mainContentContainer.setOnLongClickListener { onRuleLongClick(rule) }

        // Removed setOnClickListeners for buttonEditRule and buttonDeleteRule
        // as swipe action will now directly trigger delete confirmation
        // and edit is handled by mainContentContainer click.
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateTextColor(color: Int) {
        textColor = color
        notifyDataSetChanged() // Notify adapter to rebind views with new color
    }

    inner class RuleViewHolder(val binding: ItemAutomationRuleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(rule: SimpleAutomationSetting, textColor: Int) { // Receive textColor
            // Apply alpha to make inactive rules appear faded
            val alpha = if (rule.isActive) 1.0f else 0.5f
            binding.mainContentContainer.alpha = alpha
            
            binding.textViewContactNameRule.text = rule.contactName ?: itemView.context.getString(R.string.no_contact_name)
            binding.textViewContactNameRule.setTextColor(textColor) // Apply textColor

            binding.textViewPhoneNumberRule.text = rule.phoneNumber
            binding.textViewPhoneNumberRule.setTextColor(textColor) // Apply textColor

            binding.textViewPickupDelayRule.text = itemView.context.getString(R.string.pickup_delay_formatted, rule.pickupDelaySeconds)
            binding.textViewPickupDelayRule.setTextColor(textColor) // Apply textColor

            val autoDisconnectText = if (rule.autoDisconnectCall) itemView.context.getString(R.string.yes) else itemView.context.getString(R.string.no) // Changed rule.autoDisconnect to rule.autoDisconnectCall
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
            
            // Display active/inactive status
            val statusText = if (rule.isActive) 
                itemView.context.getString(R.string.status_active) 
            else 
                itemView.context.getString(R.string.status_inactive)
            val statusColor = if (rule.isActive) Color.parseColor("#4CAF50") else Color.RED // Green for active, red for inactive
            binding.textViewRuleStatus.text = statusText
            binding.textViewRuleStatus.setTextColor(statusColor)
        }
    }

    class RuleDiffCallback : DiffUtil.ItemCallback<SimpleAutomationSetting>() {
        override fun areItemsTheSame(oldItem: SimpleAutomationSetting, newItem: SimpleAutomationSetting):
                Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SimpleAutomationSetting, newItem: SimpleAutomationSetting):
                Boolean = oldItem == newItem
    }
}
