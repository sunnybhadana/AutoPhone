package com.revaltronics.autophone.dialogs

import com.revaltronics.commons.activities.BaseSimpleActivity
import com.revaltronics.commons.extensions.getAlertDialogBuilder
import com.revaltronics.commons.extensions.setupDialogStuff
import com.revaltronics.commons.extensions.viewBinding
import com.revaltronics.commons.helpers.TAB_CALL_HISTORY
import com.revaltronics.commons.helpers.TAB_CONTACTS
import com.revaltronics.commons.helpers.TAB_FAVORITES
import com.revaltronics.autophone.helpers.TAB_AUTOMATION
import com.revaltronics.commons.views.MyAppCompatCheckbox
import com.revaltronics.autophone.R
import com.revaltronics.autophone.databinding.DialogManageVisibleTabsBinding
import com.revaltronics.autophone.extensions.config
import com.revaltronics.autophone.helpers.ALL_TABS_MASK

class ManageVisibleTabsDialog(val activity: BaseSimpleActivity) {
    private val binding by activity.viewBinding(DialogManageVisibleTabsBinding::inflate)
    private val tabs = LinkedHashMap<Int, Int>()

    init {
        tabs.apply {
            put(TAB_FAVORITES, R.id.manage_visible_tabs_favorites)
            put(TAB_CALL_HISTORY, R.id.manage_visible_tabs_call_history)
            put(TAB_CONTACTS, R.id.manage_visible_tabs_contacts)
            put(TAB_AUTOMATION, R.id.manage_visible_tabs_automation)
        }

        val showTabs = activity.config.showTabs
        for ((key, value) in tabs) {
            binding.root.findViewById<MyAppCompatCheckbox>(value).isChecked = showTabs and key != 0
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.manage_shown_tabs)
            }
    }

    private fun dialogConfirmed() {
        var result = 0
        for ((key, value) in tabs) {
            if (binding.root.findViewById<MyAppCompatCheckbox>(value).isChecked) {
                result += key
            }
        }

        if (result == 0) {
            result = ALL_TABS_MASK
        }

        activity.config.showTabs = result
    }
}
