package com.workcount.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.workcount.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activeShift = context.appContainer.shiftRepository.observeActiveShift().first()
                if (activeShift != null) {
                    ShiftForegroundService.sync(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
