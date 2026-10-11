# 待办提醒（Todolist）

Android 原生待办事项 App：添加待办（名称 / 起始时间 / 终止时间 / 提前提醒量），
按起始时间列出最近事项，在「起始时间 − 提前量」通过通知提醒。

每条待办可以**自己选择提醒策略**：

| 策略 | 实现 | 权限 | 精度 |
|---|---|---|---|
| 精确闹钟 | `AlarmManager.setExactAndAllowWhileIdle` | Android 12+ 需「闹钟和提醒」特殊权限 | 秒级 |
| 普通通知 | WorkManager 延迟任务 | 不需要 | 系统可能延后几分钟（Doze 下更久） |

权限缺失不会崩、也不会静默失效：自动降级为普通通知，并在界面顶部横幅 + Snackbar 告知用户。

---

## 1. 技术选型

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin 2.1.0 + Jetpack Compose（Material 3） |
| 架构 | MVVM + Repository；`domain` 层为**纯 Kotlin**，不含 Android API |
| 持久化 | Room 2.6.1（KSP 生成代码） |
| 调度 | `AlarmManager`（精确）+ `androidx.work`（非精确），统一在 `ReminderScheduler` 抽象之下 |
| 依赖注入 | 手写装配根 `AppGraph`（依赖图很小，不引入 Hilt） |
| 构建 | Gradle 8.11.1 + AGP 8.9.2，compileSdk/targetSdk 35，minSdk 26 |

设计上的两个关键取舍：

1. **调度决策与执行分离**。`SchedulingCoordinator` 只决定「谁、在什么时刻、用哪条策略」，
   `ReminderFiringService` 只负责「真的去发通知」。两者都有接口边界，
   于是全部调度语义都能用 Fake 在纯 JVM 上确定性断言，不需要真的等时间流逝。
2. **时间源可注入**。业务代码统一通过 `Clock` 取「现在」，测试换成 `FakeClock`，
   就能精确构造「提醒时刻刚好过去 / 刚好还没到」这类边界。

---

## 2. 目录结构

```
app/src/main/java/com/example/todolist/
├─ TodoApp.kt                  # Application + 依赖装配根（AppGraph）
│                              #   graphOrNull() / requireGraph() 为统一读取入口
├─ MainActivity.kt             # 单 Activity；通知权限在首帧之后申请
├─ domain/                     # 纯业务规则，无 Android 依赖
│  ├─ Todo.kt                  # 领域模型
│  ├─ ReminderTime.kt          # 提醒时刻计算、状态判定、校验规则
│  ├─ FormValidator.kt         # 表单字段级校验（返回枚举，不返回资源 id）
│  ├─ ReminderPlan.kt          # 「何时 + 用什么策略」的决策结果
│  ├─ TodoStatus.kt
│  └─ Clock.kt                 # 可注入的时间源
├─ data/
│  ├─ local/                   # Room：Entity / Dao / Database
│  ├─ TodoRepository.kt        # 只做持久化，不碰调度
│  └─ TodoDraft.kt             # 表单 → 实体的合并逻辑（含已提醒标记的重置规则）
├─ reminder/
│  ├─ ReminderScheduler.kt     # 调度抽象
│  ├─ AlarmReminderScheduler.kt# 精确闹钟实现
│  ├─ WorkReminderScheduler.kt # WorkManager 实现
│  ├─ ReminderWorker.kt        # 非精确策略的落地执行
│  ├─ SchedulingCoordinator.kt # 增删改 + 排定/撤销 + 全量对账
│  ├─ ReminderAlarmReceiver.kt # 精确闹钟到点回调
│  └─ BootReceiver.kt          # 开机/改时间/升级后重排
├─ notify/NotificationHelper.kt# 通知渠道与通知内容（唯一接触系统通知的地方）
├─ sys/ExactAlarmPermission.kt # 精确闹钟权限查询与授权引导
└─ ui/                         # Compose 界面 + ViewModel + 表单状态
```

---

## 3. 构建与运行

### 3.1 本机环境（已就绪）

| 项目 | 位置 |
|---|---|
| JDK 21 | `D:\Download\java` |
| Android SDK | `D:\Android\sdk`（platform-tools 37.0.1 / android-35 / build-tools 35.0.0 / emulator 37.2.12） |
| Gradle 8.11.1 | `D:\Android\gradle-8.11.1` |
| Gradle 依赖缓存 | `D:\Android\gradle-home` |
| AVD | `todo_api35`（API 35，google_apis，x86_64） |

> 工程路径 `D:\Download\Todolist` 刻意不含空格与中文；所有参与编译的路径都遵守该约束。

### 3.2 常用命令

```powershell
$env:JAVA_HOME='D:\Download\java'
$env:ANDROID_HOME='D:\Android\sdk'
$env:GRADLE_USER_HOME='D:\Android\gradle-home'
cd D:\Download\Todolist

.\gradlew.bat assembleDebug              # 产出 app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat testDebugUnitTest          # 单元测试（纯 JVM + Robolectric，不需要设备）
.\gradlew.bat connectedDebugAndroidTest  # 仪器化 UI 测试（需要模拟器/真机）
```

单元测试报告：`app\build\reports\tests\testDebugUnitTest\index.html`
仪器化测试报告：`app\build\reports\androidTests\connected\debug\index.html`

### 3.3 从零复现环境

`tools\setup-android-sdk.ps1` 会按顺序完成：下载 cmdline-tools → 接受许可证 →
安装 SDK 组件 → 下载并解压 Gradle 8.11.1 → 创建并调优 AVD。

```powershell
pwsh -File tools\setup-android-sdk.ps1
```

### 3.4 启动模拟器

```powershell
$emu='D:\Android\sdk\emulator\emulator.exe'
Start-Process $emu -ArgumentList '-avd','todo_api35','-no-boot-anim','-gpu','host'
& 'D:\Android\sdk\platform-tools\adb.exe' wait-for-device
```

---

## 4. 测试

### 4.1 单元测试（70 个，全部通过，无需设备）

| 测试类 | 用例数 | 覆盖内容 |
|---|---|---|
| `domain/ReminderTimeTest` | 12 | 提醒时刻计算、跨天/跨月、提前量上下限、起止时间与名称校验、状态边界（恰好等于起始/终止时间）、该不该排定的四种情况、策略映射 |
| `reminder/ReminderSchedulingTest` | 17 | 两种策略分别排到正确调度器、提前量体现在触发时刻、**无权限自动降级**、降级标记不残留、编辑后撤销旧调度、切换策略清掉旧调度、改时间清掉已提醒标记、改名字不清标记、完成/取消完成的排定语义、已过期不排定、删除清理、全量对账筛选 |
| `reminder/ReminderFiringServiceTest` | 4 | 到点发通知并打标记、重复触发不重复发、已完成不发、不存在的 id 安全返回 |
| `data/TodoRepositoryTest` | 7 | 真实 Room 内存库：增删改查、按未完成优先+起始时间排序、标记已提醒、Flow 重新发射 |
| `ui/DraftFormStateHolderTest` | 13 | 默认时间取下一个整点、编辑回填、四类校验错误、修改字段清错误、修改起始时间自动顺延结束时间、提前量文案 |
| `ui/TodoViewModelTest` | 10 | 表单开合、非法表单不落库、合法表单落库并排定、降级提示、编辑不新增、切换完成、删除关闭表单、权限变化提示 |
| `notify/NotificationHelperTest` | 7 | 渠道创建与重要性、重复创建幂等、通知 id 映射稳定、通知内容、点击跳转 PendingIntent、取消生效、权限缺失识别 |

### 4.2 仪器化测试（5 个，在 API 35 模拟器上全部通过）

`TodoListUiTest`：标题与新增按钮展示、新增后出现在列表、名称校验拦截、勾选后展示已完成、
打开精确闹钟开关后策略徽章更新。

要点：

* 通知权限用 `GrantPermissionRule` 预先授予。**否则系统的权限对话框会盖在界面之上，
  把 Activity 压到 PAUSED，Compose 规则会报「No compose hierarchies found」。**
* 行与勾选框用「带 id 的标签」（`tagForTodo(TAG_TODO_ROW, id)`）定位，
  不依赖坐标、也不依赖列表顺序——列表会随「已完成」重排。

### 4.3 系统行为验证（adb，已完成）

```powershell
$adb='D:\Android\sdk\platform-tools\adb.exe'
$pkg='com.example.todolist'

& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell cmd appops set $pkg SCHEDULE_EXACT_ALARM allow
& $adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS

# 精确闹钟是否真的进入 AlarmManager（关键看 exactAllowReason=permission）
& $adb shell dumpsys alarm | Select-String $pkg

# 非精确策略的 WorkManager 作业
& $adb shell dumpsys jobscheduler | Select-String $pkg

# 手动触发一次提醒（id 必须是 long，用 -el 而不是 -ei）
& $adb shell am broadcast -a com.example.todolist.action.FIRE_REMINDER `
    --el extra_todo_id 1 `
    -n com.example.todolist/com.example.todolist.reminder.ReminderAlarmReceiver

# 看应用侧日志
& $adb logcat -s TodoReminder

# 重启后重排
& $adb reboot
```

实测结果（详见 `验证截图/`）：

| 验证项 | 结果 |
|---|---|
| 精确闹钟注册 | `RTC_WAKEUP ... com.example.todolist`，`tag=*walarm*:com.example.todolist.action.FIRE_REMINDER`，**`exactAllowReason=permission`** |
| 非精确策略 | `jobscheduler` 中出现 WorkManager 作业，`Minimum latency: +8m11s` 对应预设提醒时刻 |
| 通知渲染 | 通知栏显示「待办提醒」/ 标题 `ExactAlarmVerify` / 内容 `时间：10-10 04:00 - 10-10 05:00`；`NotificationRecord(... channel=todo_reminder ...)` |
| 通知渠道 | `todo_reminder`，重要性 4（HIGH），描述与声音正常 |
| 重启重排 | `adb reboot` 后无需打开应用，AlarmManager 中闹钟自动重新注册，进程被拉起 |
| 降级路径 | 撤销 `SCHEDULE_EXACT_ALARM` 后界面出现降级横幅；策略自动落到非精确调度器 |

---

## 5. 行为约定与已知限制

- **不会补发过期提醒**：提醒时刻已过去的待办在启动/开机对账时被跳过，
  避免装上 App 后立刻被历史事项刷屏（`ReminderTime.shouldSchedule` 与
  `SchedulingCoordinator.scheduleFor` 双重保证）。
- **编辑提醒时刻会重置「已提醒」标记**，改过时间的待办会再次提醒；只改名称不会。
- **非精确策略的延迟由系统决定**，国产 ROM 的省电策略可能延迟很久甚至杀掉任务，
  这是系统行为而非代码缺陷；需要准时请选择精确闹钟策略。
- **精确闹钟在 Android 12+ 默认关闭**，需用户在系统设置里允许；界面顶部横幅提供入口。
- **数据库版本为 1**，尚未编写迁移；后续改表结构必须补 `Migration`。
- `ReminderAlarmReceiver` 在 release 构建中为 `exported="false"`；
  `app/src/debug/AndroidManifest.xml` 只在 debug 构建里把它导出，便于 adb 触发验证。

---

## 6. 环境搭建中踩到的五个坑（复现时注意）

1. **`gradle.org` / `repo.maven.apache.org` 在本机网络下不可达**。
   `mavenCentral()` 默认解析到 `repo.maven.apache.org`（TCP 超时，构建会长时间卡住），
   必须显式换成 `repo1.maven.org`；Gradle 发行包走腾讯云镜像。
2. **Gradle 发行包不含可直接使用的 `gradle-wrapper.jar`**。
   8.9/8.11 把 wrapper 拆成 `gradle-wrapper-main-*.jar`（内含真正的 wrapper jar）
   与 `gradle-wrapper-shared-*.jar`；手工拼合会因 `gradle-cli` 版本不匹配报
   `NoSuchMethodError`。因此 `gradlew.bat` 直接驱动已解压的发行包。
3. **AGP 8.9.2 要求 Gradle ≥ 8.11.1**，用 Gradle 8.9 会在配置阶段直接失败。
4. **androidTest 编译单元访问不到 app 的伴生对象成员**（如 `TodoApp.graph` 之类的
   静态成员会报 unresolved），但可以正常访问 app 的类、顶层函数与实例成员；
   因此依赖图通过 Application 实例 + 顶层扩展函数暴露。
5. **在 `Activity.onCreate` 里直接申请权限会破坏 UI 测试**：系统对话框把 Activity
   压到 PAUSED，Compose 找不到层级。权限申请改到 `setContent` 之后的 `LaunchedEffect`。
