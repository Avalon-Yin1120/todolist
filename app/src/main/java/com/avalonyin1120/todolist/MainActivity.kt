package com.avalonyin1120.todolist

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.avalonyin1120.todolist.sys.SystemExactAlarmPermission
import com.avalonyin1120.todolist.ui.TodoRoute
import com.avalonyin1120.todolist.ui.TodoViewModel
import com.avalonyin1120.todolist.ui.theme.TodolistTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TodoViewModel by viewModels {
        TodoViewModel.factory(application)
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果通过横幅体现 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TodolistTheme {
                // 权限申请放在首帧之后：在 onCreate 里立即弹系统对话框会把本 Activity 压到 PAUSED，
                // 界面尚未完成首次绘制，既影响用户观感，也会让 UI 测试找不到 Compose 层级。
                LaunchedEffect(Unit) {
                    requestNotificationPermissionIfNeeded()
                }

                TodoRoute(
                    viewModel = viewModel,
                    onRequestExactAlarmPermission = {
                        val intent = SystemExactAlarmPermission(applicationContext).requestIntent()
                        if (intent != null) startActivity(intent)
                    },
                    onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户可能刚从系统设置里回来，重新读一次精确闹钟权限。
        viewModel.refreshPermissions()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
