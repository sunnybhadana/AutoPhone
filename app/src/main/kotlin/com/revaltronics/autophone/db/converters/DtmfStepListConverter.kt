package com.revaltronics.autophone.db.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.revaltronics.autophone.models.DtmfStep

class DtmfStepListConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromDtmfStepList(dtmfSteps: List<DtmfStep>?): String? {
        return gson.toJson(dtmfSteps)
    }

    @TypeConverter
    fun toDtmfStepList(dtmfStepsString: String?): List<DtmfStep>? {
        if (dtmfStepsString == null) {
            return null
        }
        val listType = object : TypeToken<List<DtmfStep>>() {}.type
        return gson.fromJson(dtmfStepsString, listType)
    }
}
