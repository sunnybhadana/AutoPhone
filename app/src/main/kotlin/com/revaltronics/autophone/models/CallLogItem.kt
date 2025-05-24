package com.goodwy.autophone.models

import java.io.Serializable

@kotlinx.serialization.Serializable
sealed class CallLogItem : Serializable {
    data class Date(
        val timestamp: Long,
        val dayCode: String,
    ) : CallLogItem()

    fun getItemId(): Int {
        return when (this) {
            is Date -> dayCode.hashCode()
            is RecentCall -> id
        }
    }
}
