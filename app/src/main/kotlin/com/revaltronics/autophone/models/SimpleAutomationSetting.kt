package com.revaltronics.autophone.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.revaltronics.autophone.db.converters.DtmfStepListConverter

@Entity(
    tableName = "simple_automation_settings",
    indices = [Index(value = ["phoneNumber"], unique = true)] // Added unique index for phoneNumber
)
@TypeConverters(DtmfStepListConverter::class) // Removed StringListConverter
data class SimpleAutomationSetting(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val contactName: String? = null,
    val phoneNumber: String, // Changed back to String from List<String>
    val pickupDelaySeconds: Int = 0,
    val autoDisconnectCall: Boolean = false,
    val dtmfSequence: List<DtmfStep> = emptyList()
)
