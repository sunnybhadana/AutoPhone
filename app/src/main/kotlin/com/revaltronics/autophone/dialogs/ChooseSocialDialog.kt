package com.revaltronics.autophone.dialogs

import android.app.Activity
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.revaltronics.commons.extensions.beGone
import com.revaltronics.commons.extensions.getAlertDialogBuilder
import com.revaltronics.commons.extensions.setupDialogStuff
import com.revaltronics.autophone.extensions.getPackageDrawable
import com.revaltronics.autophone.databinding.DialogChooseSocialBinding
import com.revaltronics.autophone.databinding.ItemChooseSocialBinding
import com.revaltronics.commons.models.contacts.SocialAction

class ChooseSocialDialog(val activity: Activity, actions: ArrayList<SocialAction>, val callback: (action: SocialAction) -> Unit) {
    private lateinit var dialog: AlertDialog

    init {
        val binding = DialogChooseSocialBinding.inflate(activity.layoutInflater)
        actions.sortBy { it.type }
        actions.forEach { action ->
            val item = ItemChooseSocialBinding.inflate(activity.layoutInflater).apply {
                itemSocialLabel.text = action.label
                root.setOnClickListener {
                    callback(action)
                    dialog.dismiss()
                }

                val drawable = activity.getPackageDrawable(action.packageName)
                if (drawable == null) {
                    itemSocialImage.beGone()
                } else {
                    itemSocialImage.setImageDrawable(drawable)
                }
            }

            binding.dialogChooseSocial.addView(item.root, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val builder = activity.getAlertDialogBuilder()

        builder.apply {
            activity.setupDialogStuff(binding.root, this) { alertDialog ->
                dialog = alertDialog
            }
        }
    }
}
