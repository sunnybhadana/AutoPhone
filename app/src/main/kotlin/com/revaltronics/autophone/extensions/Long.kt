package com.revaltronics.autophone.extensions


import com.revaltronics.commons.extensions.getFormattedDuration
import kotlin.math.roundToInt

fun Long.getFormattedDuration(forceShowHours: Boolean = false): String {
    return this.div(1000F).roundToInt().getFormattedDuration(forceShowHours)
}
