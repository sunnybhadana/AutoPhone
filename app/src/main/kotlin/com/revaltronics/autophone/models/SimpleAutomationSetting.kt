package com.revaltronics.autophone.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.revaltronics.autophone.db.converters.DtmfStepListConverter

@Entity(
    tableName = "simple_automation_settings",
    indices = [
        Index(value = ["phoneNumber"], unique = true),
        Index(value = ["batch_group_id"]) // Add index for batch grouping
    ]
)
@TypeConverters(DtmfStepListConverter::class)
data class SimpleAutomationSetting(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val contactName: String? = null,
    val phoneNumber: String,
    val pickupDelaySeconds: Int = 0,
    val autoDisconnectCall: Boolean = false,
    val dtmfSequence: List<DtmfStep> = emptyList(),
    
    // New fields for call limiting
    val batch_group_id: String = "", // Group identifier for related rules (e.g., "pagerduty")
    val answered_calls_count: Int = 0, // Number of calls auto-answered in the current interval
    val max_auto_answers: Int = 0, // 0 means unlimited auto-answers
    val reset_interval_minutes: Int = 0, // 0 means no time-based reset
    val last_reset_timestamp: Long = 0 // When the counter was last reset (milliseconds since epoch)
)
