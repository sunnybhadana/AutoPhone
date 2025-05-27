package com.revaltronics.autophone.dialogs

import androidx.appcompat.app.AlertDialog
import com.revaltronics.commons.activities.BaseSimpleActivity
import com.revaltronics.commons.extensions.getAlertDialogBuilder
import com.revaltronics.commons.extensions.setupDialogStuff
import com.revaltronics.commons.extensions.viewBinding
import com.revaltronics.autophone.activities.SimpleActivity
import com.revaltronics.autophone.adapters.RecentCallsAdapter
import com.revaltronics.autophone.databinding.DialogShowGroupedCallsBinding
import com.revaltronics.autophone.models.RecentCall

class ShowGroupedCallsDialog(val activity: BaseSimpleActivity, recentCalls: List<RecentCall>) {
    private var dialog: AlertDialog? = null
    private val binding by activity.viewBinding(DialogShowGroupedCallsBinding::inflate)

    init {
        activity.runOnUiThread {
            RecentCallsAdapter(
                activity = activity as SimpleActivity,
                recyclerView = binding.selectGroupedCallsList,
                refreshItemsListener = null,
                showOverflowMenu = false,
                itemClick = {}
            ).apply {
                binding.selectGroupedCallsList.adapter = this
                updateItems(recentCalls)
            }
        }

        activity.getAlertDialogBuilder()
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }
}
