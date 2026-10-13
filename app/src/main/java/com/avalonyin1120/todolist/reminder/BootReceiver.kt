package com.avalonyin1120.todolist.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avalonyin1120.todolist.TodoApp
import com.avalonyin1120.todolist.graphOrNull
import kotlinx.coroutines.launch

/**
 * 开机、系统时间变化、应用升级后重新排定提醒。
 *
 * 系统在重启后会清空 AlarmManager 与 WorkManager 中已排定的任务，
 * 因此这里必须做一次全量对账，否则重启后所有提醒都会静默丢失。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val app = context.applicationContext as? TodoApp ?: return
        val coordinator = app.graphOrNull()?.coordinator ?: return
        val pending = goAsync()
        TodoApp.scope.launch {
            try {
                coordinator.reconcileAll()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
