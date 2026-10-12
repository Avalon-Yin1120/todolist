package com.example.todolist.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todolist.BuildConfig
import com.example.todolist.data.local.TodoDatabase

/**
 * 版本与构建信息。
 *
 * 从 [BuildConfig] 与 `ApplicationInfo` 读，而不是在界面里写死字符串：
 * 发版时只要改 gradle，界面自动跟着变，不会出现「界面写 v1.0，包其实是别的」。
 */
data class VersionInfo(
    val versionName: String = "",
    val versionCode: Int = 0,
    val packageName: String = "",
    val databaseVersion: Int = TodoDatabase.DB_VERSION,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val deviceSdk: Int = Build.VERSION.SDK_INT,
    val isDebug: Boolean = BuildConfig.DEBUG,
) {
    /** 设置页那一行显示的短文本。 */
    fun shortLabel(): String =
        if (versionName.isBlank()) "未知" else "$versionName（$versionCode）"

    /** 构建类型文案：用户反馈问题时让它一眼看出装的是哪种包。 */
    val buildTypeLabel: String
        get() = if (isDebug) "调试构建" else "正式构建"

    companion object {
        /** 从运行环境读取真实信息。 */
        @Suppress("DEPRECATION")
        fun of(context: Context): VersionInfo = VersionInfo(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            packageName = context.packageName,
            minSdk = context.applicationInfo.minSdkVersion,
            // targetSdkVersion 在新 SDK 上被标记为废弃，但仍是唯一能读到的来源
            targetSdk = context.applicationInfo.targetSdkVersion,
        )
    }
}

/**
 * 「关于待办提醒」页。
 *
 * 它不是一段自我介绍，而是回答用户最常问的两个问题：
 * * 说好的提醒，到底是谁在什么时候把它弹出来的？
 * * 到点没响，我该去开哪个开关？
 *
 * 因此正文按「提醒的三层通道 → 两种提醒方式 → 检查入口」组织，
 * 末尾直接给一个跳到「提醒可靠性检查」的按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    version: VersionInfo,
    onBack: () -> Unit,
    onOpenReminderHealth: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag(TAG_ABOUT_SCREEN),
        topBar = {
            TopAppBar(
                title = { Text("关于待办提醒") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_ABOUT_BACK)) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "待办提醒",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "版本 ${version.shortLabel()} · ${version.buildTypeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "记录待办的名称与起止时间，并在你设定的提前量到达时提醒你。" +
                    "数据全部保存在本机，不需要注册也不需要联网。",
                style = MaterialTheme.typography.bodyMedium,
            )

            AboutCard(title = "提醒是怎么工作的") {
                BodyText(
                    "每条待办都可以设一个「提前量」（准点、5 分钟前、1 小时前……）。" +
                        "到达提醒时刻后，应用会用三层通道接力，任何一层成功都会提醒到你：",
                )
                Bullet("精确闹钟", "最准时，需要你在系统设置里允许「闹钟和提醒」权限。")
                Bullet("非精确闹钟", "不需要任何特殊权限，系统打盹（Doze）时也能唤醒，可能晚几分钟。")
                Bullet("后台任务保险", "前两层都失败时由 WorkManager 兜底，国产 ROM 上可能被推迟。")
                BodyText(
                    "三层通道指向同一个提醒，所以不会重复打扰；如果提醒晚了，" +
                        "15 分钟内仍会补发一次。",
                )
            }

            AboutCard(title = "两种提醒方式") {
                Bullet(
                    "闹钟式",
                    "到点持续响铃、点亮屏幕并弹出提醒页，直到你点「知道了」或「标记完成」。" +
                        "响铃与提醒页同生命周期：页面一关，铃声立刻停。",
                )
                Bullet(
                    "通知式",
                    "只弹一条通知并响一下。通知上可以直接「完成」或「10 分钟后再提醒」，" +
                        "不必打开应用。",
                )
                BodyText("默认用哪种方式可以在设置里改；通知的提示音也能单独关掉。")
            }

            AboutCard(title = "到点没响怎么办") {
                BodyText(
                    "按「允许通知 → 允许精确闹钟 → 允许后台运行 → 关闭电池优化 / 打开自启动」" +
                        "的顺序检查，绝大多数漏提醒都出在这四项上。",
                )
                TextButton(
                    onClick = onOpenReminderHealth,
                    modifier = Modifier.testTag(TAG_ABOUT_HEALTH),
                ) { Text("打开提醒可靠性检查") }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * 「隐私」页。
 *
 * 结论放最前面，然后才给依据（存在哪、不做什么、权限各自干什么）。
 * 权限清单刻意逐条写「为什么需要」，让用户能自己核对有没有越界。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.testTag(TAG_PRIVACY_SCREEN),
        topBar = {
            TopAppBar(
                title = { Text("隐私") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_PRIVACY_BACK)) {
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "你的待办只存在这台手机上。",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "应用没有申请联网权限（安装包里没有 INTERNET 权限），" +
                    "因此从技术上就不具备把数据发出去的能力。",
                style = MaterialTheme.typography.bodyMedium,
            )

            AboutCard(title = "数据存在哪") {
                Bullet("待办内容", "本机数据库 todolist.db（名称、起止时间、重复规则、提醒方式）。")
                Bullet("设置项", "本机配置文件（默认提醒方式、提示音开关、上次用的提前量等）。")
                BodyText("卸载应用即全部删除，没有服务端副本，也没有找回一说。")
            }

            AboutCard(title = "不做什么") {
                Bullet("不联网", "没有网络权限，没有账号、云同步、天气/日历等外部数据源。")
                Bullet("不收集", "没有统计、广告、崩溃上报等第三方 SDK。")
                Bullet("不读取", "不读通讯录、位置、相册、短信、通话记录。")
            }

            AboutCard(title = "权限都用在哪") {
                PermissionRow("通知", "弹出到点提醒。Android 13 起需要你手动允许。")
                PermissionRow("闹钟和提醒", "让闹钟式提醒精确到分钟触发。")
                PermissionRow("开机自启动", "重启后重新排定未完成的提醒，否则重启会丢提醒。")
                PermissionRow("全屏通知", "锁屏状态下直接弹出提醒页。")
                PermissionRow("唤醒与保持屏幕", "提醒时点亮屏幕，并在响铃期间保持亮屏。")
                PermissionRow("振动", "提醒时振动。")
                PermissionRow("前台服务", "闹钟式提醒响铃期间保持运行，否则系统会几秒内杀掉它。")
                PermissionRow("忽略电池优化", "申请后台运行白名单，避免省电策略把提醒推迟。")
            }

            AboutCard(title = "关于系统备份") {
                BodyText(
                    "应用允许参与系统的「备份与恢复」（Android 的 allowBackup 机制），" +
                        "这意味着换机时系统可能把本机数据备份到你自己的系统账号里。" +
                        "这是系统行为、不经过本应用，可以在系统设置的备份选项中关闭。",
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun Bullet(title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 6.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionRow(name: String, usage: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = usage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

const val TAG_ABOUT_SCREEN = "about_screen"
const val TAG_ABOUT_BACK = "about_back"
const val TAG_ABOUT_HEALTH = "about_health"
const val TAG_PRIVACY_SCREEN = "privacy_screen"
const val TAG_PRIVACY_BACK = "privacy_back"
