package com.revaltronics.autophone.dialogs

import android.app.Activity
import android.content.DialogInterface.BUTTON_POSITIVE
import androidx.appcompat.app.AlertDialog
import com.revaltronics.commons.extensions.getAlertDialogBuilder
import com.revaltronics.commons.extensions.setupDialogStuff
import com.revaltronics.commons.extensions.showKeyboard
import com.revaltronics.autophone.R
import com.revaltronics.autophone.databinding.DialogAddSpeedDialBinding
import com.revaltronics.autophone.models.SpeedDial

class AddSpeedDialDialog(
    private val activity: Activity,
    private val speedDial: SpeedDial,
    private val callback: (name: String) -> Unit,
) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogAddSpeedDialBinding.inflate(activity.layoutInflater).apply {
            addSpeedDialEditText.apply {
                setText(speedDial.number)
                hint = speedDial.number
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.revaltronics.commons.R.string.ok, null)
            .setNegativeButton(com.revaltronics.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.speed_dial) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.showKeyboard(binding.addSpeedDialEditText)
                    alertDialog.getButton(BUTTON_POSITIVE).apply {
                        setOnClickListener {
                            val newTitle = binding.addSpeedDialEditText.text.toString()
                            callback(newTitle)
                            alertDialog.dismiss()
                        }
                    }
                }
            }
    }
}
