package com.revaltronics.autophone.helpers

import android.content.Context
import com.revaltronics.autophone.extensions.timerDb
import com.revaltronics.autophone.models.Timer
import com.revaltronics.commons.helpers.ensureBackgroundThread

class TimerHelper(val context: Context) {
    private val timerDao = context.timerDb

    fun getTimers(callback: (timers: List<Timer>) -> Unit) {
        ensureBackgroundThread {
            callback.invoke(timerDao.getTimers())
        }
    }

    fun getTimer(timerId: Int, callback: (timer: Timer) -> Unit) {
        ensureBackgroundThread {
            callback.invoke(timerDao.getTimer(timerId)!!)
        }
    }

    fun tryGetTimer(timerId: Int, callback: (timer: Timer?) -> Unit) {
        ensureBackgroundThread {
            callback.invoke(timerDao.getTimer(timerId))
        }
    }

    fun findTimers(seconds: Int, label: String, callback: (timers: List<Timer>) -> Unit) {
        ensureBackgroundThread {
            callback.invoke(timerDao.findTimers(seconds, label))
        }
    }

    fun insertOrUpdateTimer(timer: Timer, callback: (id: Long) -> Unit = {}) {
        ensureBackgroundThread {
            val id = timerDao.insertOrUpdateTimer(timer)
            callback.invoke(id)
        }
    }

    fun deleteTimer(id: Int, callback: () -> Unit = {}) {
        ensureBackgroundThread {
            timerDao.deleteTimer(id)
            callback.invoke()
        }
    }

    fun deleteTimers(timers: List<Timer>, callback: () -> Unit = {}) {
        ensureBackgroundThread {
            timerDao.deleteTimers(timers)
            callback.invoke()
        }
    }
}
