package com.avalonyin1120.todolist.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.avalonyin1120.todolist.notify.ReminderLog

/**
 * 「提醒诊断日志」页。
 *
 * ## 为什么做成用户能自己看的页面
 *
 * 「到点没响」这类问题只能在真机上定位：模拟器没有厂商省电策略、没有厂商削掉的后台启动，
 * 也复现不出系统把渠道判成静默。以前每一轮排查都靠用户描述 + 我猜，往返好几轮。
 * 现在把关键节点按时间记下来（排定 / 触发 / 是否被跳过 / 发通知时的渠道与声音 /
 * 起铃用的是内置音频还是系统铃声 / 是哪条路径停掉的），用户把这段文字复制回来，
 * 断在哪一环是确定的，不用猜。
 *
 * 日志本身**不联网、不外传**：只写在本机文件里，只有用户主动复制才会离开应用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    // 手动刷新：日志会持续追加，但没必要为了它做实时刷新——
    // 用户通常是「响过一遍之后」才进来看，进页面时读一次、必要时点刷新即可。
    var refreshTick by remember { mutableIntStateOf(0) }
    val lines = remember(refreshTick) { ReminderLog.snapshotNewestFirst() }

    Scaffold(
        modifier = Modifier.testTag(TAG_REMINDER_LOG_SCREEN),
        topBar = {
            TopAppBar(
                title = { Text("提醒诊断日志") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_REMINDER_LOG_BACK)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "最近 ${lines.size} 条。到点没响时，把这里的内容复制发给开发者即可定位。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = {
                        val text = lines.joinToString("\n")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("提醒诊断日志", text))
                    },
                    modifier = Modifier.testTag(TAG_REMINDER_LOG_COPY),
                ) { Text("复制全部") }
                TextButton(
                    onClick = { refreshTick++ },
                    modifier = Modifier.testTag(TAG_REMINDER_LOG_REFRESH),
                ) { Text("刷新") }
                TextButton(
                    onClick = {
                        ReminderLog.clear()
                        refreshTick++
                    },
                    modifier = Modifier.testTag(TAG_REMINDER_LOG_CLEAR),
                ) { Text("清空") }
            }
            if (lines.isEmpty()) {
                Text(
                    text = "还没有记录。等到过一次提醒之后再来看。",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(lines) { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

const val TAG_REMINDER_LOG_SCREEN = "reminder_log_screen"
const val TAG_REMINDER_LOG_BACK = "reminder_log_back"
const val TAG_REMINDER_LOG_COPY = "reminder_log_copy"
const val TAG_REMINDER_LOG_REFRESH = "reminder_log_refresh"
const val TAG_REMINDER_LOG_CLEAR = "reminder_log_clear"
