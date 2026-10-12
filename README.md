# 待办提醒（Todolist）

Android 原生待办事项 App：添加待办（名称 / 起始时间 / 终止时间 / 提前提醒量），
按时间顺序展示，到点提醒。支持**周期待办**、**两种提醒方式**、**日/周/月三种日历视图**。

## 功能一览

| 功能 | 说明 |
|---|---|
| 新增 / 编辑待办 | **整屏表单页**（非弹窗）：名称、起始与终止时间、提前提醒量、提醒方式、周期；日期与时间可分别调整 |
| 两种提醒方式 | **通知**（安静）或 **闹钟式**（响铃 + 点亮屏幕 + 全屏提醒页，锁屏也生效） |
| 周期待办 | 每天 / 每周 / 每月；**周重复可精确勾选星期几，月重复可精确勾选几号**（多选）；修改或删除时询问「仅这一次」还是「整个系列」 |
| 时段配色 | 未来=淡蓝、即将=琥珀、正在=绿、过去与已完成=灰；**未来时段不允许标记完成** |
| 智能默认 | 新建时自动沿用你上次填写的提前量与提醒方式 |
| 顶部布局 | 标题居中，左右各一个对称图标按钮（设置 / 新增，不再悬浮遮挡列表）；标题下方显示当前视图的日期范围与倒计时 |
| 日 / 周 / 月视图 | 默认进入日视图；日期范围在顶部明确显示（**周标题带年份**）；三者都支持左右滑动切换 |
| 冲突提示 | 时间段与已有待办重叠时**仍然创建成功**，但明确告知是哪几条 |
| 删除确认 | 删除前二次确认（可在设置里关闭） |
| 设置 | 独立界面（非弹窗）：默认提醒方式、精确闹钟权限状态、删除确认、冲突提示、默认提前量 |
| 整屏表单 | 字段较多，用整屏页面而非对话框承载；按「名称 / 重复 / 时间 / 提醒」分卡，重复选择紧跟名称 |
| 提醒可靠性检查 | 一页看全「通知权限 / 精确闹钟 / 允许后台运行 / 全屏提醒 / 国产 ROM 自启动」，
缺项时顶部横幅提示「有 N 项未开启，可能到点不提醒」 |

权限缺失不会崩、也不会静默失效：闹钟式提醒在缺少精确闹钟权限时自动退化为普通通知，
并在界面顶部横幅 + Snackbar 告知用户；**「到点不响」时第一个该看的就是「提醒可靠性检查」页**。

> 真机提醒为什么可能不响（重要）：见 [§5 提醒的可靠性](#5-提醒的可靠性为什么到点不响)。

---

## 1. 技术选型

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin 2.1.0 + Jetpack Compose（Material 3） |
| 架构 | MVVM + Repository；`domain` 层为**纯 Kotlin**，不含 Android API |
| 持久化 | Room 2.6.1（KSP 生成代码，schema 已导出到 `app/schemas`） |
| 调度 | `AlarmManager`（精确通道）+ `androidx.work`（非精确通道），统一在 `ReminderScheduler` 抽象之下 |
| 设置 | SharedPreferences（项少，不值得引入 DataStore 的异步初始化） |
| 依赖注入 | 手写装配根 `AppGraph`（依赖图很小，不引入 Hilt） |
| 构建 | Gradle 8.11.1 + AGP 8.9.2，compileSdk/targetSdk 35，minSdk 26 |

三处关键设计取舍：

1. **调度决策与执行分离**。`SchedulingCoordinator` 只决定「谁、在什么时刻、用哪条通道」，
   `ReminderFiringService` 只负责「真的去提醒 + 记下场次 + 排下一场」。
   两者都有接口边界，于是全部调度语义都能用 Fake 在纯 JVM 上确定性断言。
2. **时间源与周期展开可注入**。业务代码通过 `Clock` 取「现在」、通过固定 `ZoneId` 展开周期规则，
   测试因此能精确构造「提醒时刻刚好过去」「跨夏令时」「月末 31 日」这类边界。
3. **周期待办不为每场建数据库行**。只存一条规则 + 例外日期列表，
   展示某个日期范围时按需展开。修改整个周期只改一行，数据也不随时间堆积。

---

## 2. 目录结构

```
app/src/main/java/com/example/todolist/
├─ TodoApp.kt                  # Application + 依赖装配根（AppGraph）
├─ MainActivity.kt             # 单 Activity；通知权限在首帧之后申请
├─ domain/                     # 纯业务规则，无 Android 依赖
│  ├─ Todo.kt                  # 领域模型（含 mode / recurrence / 例外日期）
│  ├─ ReminderTime.kt          # 提醒时刻计算与状态判定
│  ├─ ReminderPlan.kt          # 「何时 + 哪条通道 + 哪一场」
│  ├─ Recurrence.kt            # 周期与例外
│  ├─ RecurrenceExpander.kt    # 把周期规则展开成具体场次
│  ├─ TodoClassifier.kt        # 未来/即将/正在/过去 + 是否可标记完成
│  ├─ ListOrdering.kt          # 按时间 / 按状态排序
│  ├─ FormValidator.kt         # 表单字段级校验（返回枚举）
│  └─ Clock.kt
├─ data/
│  ├─ local/                   # Room：Entity / Dao / Database（当前 v5）
│  ├─ TodoRepository.kt        # 只做持久化与查询
│  ├─ TodoDraft.kt             # 表单 → 实体的合并规则
│  └─ SettingsStore.kt         # 设置项（接口 + 生产/内存两个实现）
├─ reminder/
│  ├─ ReminderScheduler.kt     # 调度抽象
│  ├─ AlarmReminderScheduler.kt / WorkReminderScheduler.kt
│  ├─ SchedulingCoordinator.kt # 增删改 + 排定/撤销 + 冲突检测 + 对账 + 场次重排
│  ├─ ReminderAlarmReceiver.kt / ReminderWorker.kt / BootReceiver.kt
├─ notify/NotificationHelper.kt# 通知渠道；闹钟式走全屏 Intent
├─ sys/ExactAlarmPermission.kt # 精确闹钟权限查询与授权引导
└─ ui/
   ├─ TodoListScreen.kt        # 顶部栏 + 底部导航 + 总列表 + 各类对话框
   ├─ TodoViews.kt             # 日/周/月视图 + 待办卡片（配色规则 TodoColors）
   ├─ CalendarLayout.kt        # 日期与聚合的纯函数（周起止、月网格、忙闲分档）
   ├─ TodoEditSheet.kt         # 表单（时间、提前量、提醒方式、周期）
   ├─ DateTimePickerField.kt   # 日期/时间选择（只读框 + 独立按钮 + 明确确认键）
   ├─ SettingsScreen.kt        # 设置页
   ├─ alarm/AlarmActivity.kt   # 闹钟式提醒页（响铃 + 常亮 + 锁屏显示）
   ├─ TodoViewModel.kt         # 视图状态聚合（导航/设置/对话框/表单）
   └─ DraftFormStateHolder.kt  # 表单状态与校验
```

---

## 3. 构建与运行

### 3.1 本机环境

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
.\gradlew.bat testDebugUnitTest          # 单元测试（纯 JVM + Robolectric）
.\gradlew.bat connectedDebugAndroidTest  # 仪器化 UI 测试（需要模拟器/真机）
```

### 3.3 从零复现环境

```powershell
powershell -ExecutionPolicy Bypass -File tools\setup-android-sdk.ps1
```

### 3.4 启动模拟器

```powershell
$emu='D:\Android\sdk\emulator\emulator.exe'
Start-Process $emu -ArgumentList '-avd','todo_api35','-no-boot-anim','-gpu','host'
& 'D:\Android\sdk\platform-tools\adb.exe' wait-for-device
```

---

## 4. 测试

### 4.1 单元测试（无需设备）

| 测试类 | 覆盖内容 |
|---|---|
| `domain/ReminderTimeTest` | 提醒时刻计算、跨天/跨月、提前量上下限、字段校验、状态边界、策略映射 |
| `domain/RecurrenceExpanderTest` | 一次性/每天/每周/每月展开、时分保留、**每月 31 日在小月落到月末**、例外日期跳过、范围展开排序、下一次场次查找、日期键按本地时区 |
| `domain/TodoClassifierTest` | 四个时段的判定与全部边界（恰好等于提醒时刻/起始/终止）、**未来时段不可标记完成**、已完成可取消完成 |
| `reminder/ReminderSchedulingTest` | 两种通道分别排定、提前量体现在触发时刻、无权限自动降级、**时间段冲突仍创建且带回冲突项**、编辑不把自己算冲突、改时间清已提醒标记、周期跳过某场、周期提醒后顺延、全量对账 |
| `reminder/ReminderFiringServiceTest` | 触发后记下场次、**同一场次不重复提醒**、已完成不提醒、周期触发后顺延下一场 |
| `data/TodoRepositoryTest` | 真实 Room 内存库：增删改查、排序、标记场次、**重叠查询（含紧邻不算重叠、已完成不计入）**、周期与例外日期往返 |
| `ui/DraftFormStateHolderTest` | 默认时间取整点、编辑回填、四类校验错误、**提前量为 0 时输入框为空且可删空**、超范围输入标记非法、记住上次提前量 |
| `ui/TodoViewModelTest` | 表单开合、非法表单不落库、落库并排定、降级提示、编辑不新增、切换完成、**删除需确认**、权限变化提示、**只改本次中途返回不多出待办**、**日历跳转请求只发一次**、**提醒开关刷新与检查页开合** |
| `ui/CalendarPagingTest` | 页↔日期互逆、同页等价（同一周/同一月任意一天）、页号 +1 恰好前进一期、跨年跨月、**页码预算覆盖今天前后 50 年且越界会断言** |
| `notify/NotificationHelperTest` | 渠道创建与重要性、幂等、通知 id 映射、通知内容、点击跳转、取消、权限识别 |

### 4.2 仪器化测试（API 35 模拟器）

`TodoListUiTest` 覆盖：标题/倒计时/设置入口、新增待办、名称校验拦截、勾选完成后状态、
**选择闹钟式后徽章更新**、**未来时段不出现勾选框**、**删除先弹确认框（取消/确认两条路径）**、
底部四个视图切换、设置页开合，以及日历相关的三条回归：
**打开日视图就停在今天**、**翻页按钮依次前进且来回对称**、**从月视图切回日视图仍停在今天**。

要点：
* 通知权限用 `GrantPermissionRule` 预先授予，否则系统权限对话框会盖住界面，
  把 Activity 压到 PAUSED，Compose 会报「No compose hierarchies found」。
* 行与勾选框用「带 id 的标签」定位，不依赖坐标与列表顺序。

### 4.3 系统行为验证（adb）

```powershell
$adb='D:\Android\sdk\platform-tools\adb.exe'
$pkg='com.example.todolist'

& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell cmd appops set $pkg SCHEDULE_EXACT_ALARM allow
& $adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS

& $adb shell dumpsys alarm | Select-String $pkg          # 精确闹钟是否注册（看 exactAllowReason=permission）
& $adb shell dumpsys jobscheduler | Select-String $pkg   # 通知方式的 WorkManager 作业

# 手动触发一次提醒：注意 extra 是 long，必须用 -el
& $adb shell am broadcast -a com.example.todolist.action.FIRE_REMINDER `
    --el extra_todo_id 1 --el extra_occurrence_start <场次开始毫秒> `
    -n $pkg/com.example.todolist.reminder.ReminderAlarmReceiver

& $adb logcat -s TodoReminder      # 应用侧提醒日志
& $adb reboot                      # 验证重启后自动重排
```

---

### 4.4 日历翻页：设计、踩过的坑与验证

日历（日/周/月视图）用 `HorizontalPager` + **绝对基准的页码换算**实现，相关代码集中在
`ui/TodoListScreen.kt`：

| 位置 | 作用 |
|---|---|
| `CalendarPager`（约 L614） | 分页容器：导航条 + `HorizontalPager` |
| `MIDDLE` / `PAGE_COUNT`（约 L900） | 页码预算：锚点页 40000、总页数 80001（日视图 ±109 年） |
| `dateOfPage` / `pageOfDate` | 页 ↔ 日期互逆换算（纯函数，`ui/CalendarPagingTest` 钉住不变量） |
| `checkPagingBudget` | 边界自检：调试构建启动即断言，越界立刻崩而不是等用户滑到头 |
| `CalendarPager` 内的 `LaunchedEffect(tab, jumpRequest)` | **唯一起点**：切视图时先对齐页码，再持续上报当前显示的那一期 |

三个反复出现、最终定位到根因的问题（都已加回归测试）：

1. **页码预算按日视图算**。三种粒度共用一套页号空间，1 页在日视图是 1 天、在月视图是 1 月。
   早期 `MIDDLE = 6000`（锚点 2000-01-01）时日视图只覆盖到 **2016 年**，
   「回到今天」被 `coerceIn` 夹到末页，表现就是「怎么滑都在远古、回不到今天」。
2. **切视图时页码必须先对齐再上报**。页码含义随粒度变化，切到月视图的那一帧若拿日视图的
   页码按「月」解释，会得到公元几千年这种垃圾日期并被当成「当前日期」写回上层。
   因此「切视图对齐」和「上报当前期」是**同一个** `LaunchedEffect` 里的先后两步，
   且切视图用瞬时跳转（视图切换本来就不该有滑动动画）。
3. **长期运行的效应必须用 `rememberUpdatedState` 取最新回调**。上面那个效应只以
   `(tab, jumpRequest)` 为键、会一直跑，`collect` 里若直接用 `onDisplay` 参数，
   拿到的永远是首次组合时那份（内部闭包着过期的 `uiState.selectedDate`），
   于是「是否变化」的判断用过期值，上报被静默丢弃——实测连点「下一个」再点「上一个」，
   顶部标题回不去。**这个坑和 Compose 无关的直觉都要靠日志才定位到。**

另外两个交互细节：

* 翻页基准用 `PagerState.targetPage`（动画目标页）而不是 `currentPage`：
  后者在动画途中还没变，连点两次「下一个」会算出同一个目标、第二次被吞。
* 「回到今天/本周/本月」除了翻页，还会把上层日期显式落回今天；
  否则月视图里日期会停在「当月 1 号」，切回日视图就落到 1 号而不是今天。

验证方式见 `ui/CalendarPagingTest`（11 个不变量用例：互逆、单调、同页等价、预算覆盖、
越界断言）与 `TodoListUiTest` 里的「打开日视图就停在今天」「翻页按钮依次前进且来回对称」
「从月视图切回日视图仍停在今天」。

---

## 5. 提醒的可靠性（为什么到点不响）

**结论先说**：提醒能不能按时响，主要取决于系统开关，其次取决于用了哪条调度通道。
App 侧做了「主通道 + 保险通道」，并在界面里提供「提醒可靠性检查」页把开关逐个摆出来。

### 5.1 三条调度通道

| 通道 | 用什么 API | 需要权限 | 用途 |
|---|---|---|---|
| 精确闹钟 | `AlarmManager.setAlarmClock`（失败退 `setExactAndAllowWhileIdle`、再退 `setAndAllowWhileIdle`） | `SCHEDULE_EXACT_ALARM` | 闹钟式提醒的主通道。`setAlarmClock` 被系统当作「用户可见的闹钟」，Doze 下必定唤醒，国产 ROM 的省电策略普遍对它放行 |
| 非精确闹钟 | `AlarmManager.setAndAllowWhileIdle` | 无 | 通知方式的主通道；同时是精确通道的保险。**比 WorkManager 可靠得多** |
| WorkManager | `OneTimeWorkRequest` + 延迟 | 无 | 只作最后保险 |

每次提醒都会排「主通道 + 晚 3 分钟的保险通道」。重复触发由
`ReminderFiringService` 的「这一场是否已提醒」判断挡掉，因此多排一条不会重复提醒。

> 为什么不用 WorkManager 当主通道：JobScheduler 的任务会被省电策略整体推迟，
> 在 ColorOS / MIUI 上常见表现就是「只有打开 App 的那一刻才响」——
> 这正是本版本要修的缺陷。闹钟即使设备处于 Doze 也会被投递（同一应用约 9 分钟节流一次）。

### 5.2 「迟到」宽限期

提醒时刻已过但**在 15 分钟内**的场次，对账时仍会补排一次（系统立刻投递），
用于补救「设备深休眠 / 被厂商冻结导致没投递」的情况；超过 15 分钟不再补发，
避免装上新版本后被历史事项刷屏。已经提醒过的场次不会因为这条规则重复提醒。

### 5.3 一次提醒的完整链路（真机验证步骤）

```powershell
$adb='D:\Android\sdk\platform-tools\adb.exe'; $pkg='com.example.todolist'
& $adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS
& $adb shell cmd appops set $pkg SCHEDULE_EXACT_ALARM allow

# debug 专用：造一条「40 秒后提醒」的待办（走真实的 create + 调度链路）
& $adb shell am broadcast -a com.example.todolist.debug.SEED_TODO `
    -n "$pkg/com.example.todolist.debug.DebugSeedReceiver" `
    -e name 后台验证 --el trigger_in_seconds 40 --ez alarm true

& $adb shell dumpsys alarm | Select-String $pkg   # 应看到两条：主通道 + 3 分钟后的保险
& $adb shell input keyevent KEYCODE_HOME           # 退到后台
& $adb shell input keyevent KEYCODE_SLEEP          # 熄屏（模拟 Doze）
& $adb logcat -s TodoReminder TodoAlarm            # 到点应打印「已发送提醒」「闹钟开始响铃」
```

本机（API 35 模拟器）实测：退到后台 + 熄屏状态下，闹钟式提醒准时触发、
通知送达、响铃服务启动并**点亮了屏幕**；通知方式（非精确闹钟）在后台会晚几十秒到几分钟，
但一定能送到——不再需要打开 App 才响。

### 5.4 真机（尤其 OPPO / ColorOS）还需要用户手动开的开关

`提醒可靠性检查` 页会逐项检查并给出入口，ColorOS 需要额外打开：

1. 设置 → 应用管理 → 待办提醒 → **允许自启动**
2. 同页 → 电池 → **允许后台活动 / 不优化**
3. 同页 → 权限管理 → 通知 → 允许**锁屏显示 / 允许打扰**
4. 通知权限、精确闹钟、全屏提醒（Android 14+）分别由对应卡片引导开启

小米/华为/荣耀/vivo 的对应路径也写在该页的「国产 ROM 需要额外设置」卡片里。
**自启动没开时，系统会直接拦截广播拉起应用，闹钟登记了也不会响——这不是 App 能绕过的。**

---

## 6. 行为约定与已知限制

- **不会补发过期提醒**：提醒时刻已过去的场次在对账时被跳过，避免装上 App 后被历史事项刷屏。
- **周期待办按场次记录已提醒**：用 `remindedOccurrenceStart` 记住是哪一场，因此既不会重复提醒也不会漏。
- **「仅修改本次」的实现**：把这一次落成独立待办（`parentId` 指向原规则），
  并在原规则对应日期记一条 `OVERRIDDEN` 例外；「删除本次」只记 `SKIPPED` 例外。
- **提醒方式变了会重置已提醒标记**，改过提醒时间/方式的待办会再次提醒；只改名称不会。
- **通知方式的延迟由系统决定**，国产 ROM 省电策略可能延迟很久甚至杀掉任务，
  这是系统行为而非代码缺陷；需要准时请选闹钟式。
- **闹钟式需要精确闹钟权限**（Android 12+ 默认关闭），设置页与顶部横幅都提供入口。
- **全屏 Intent 的显示受系统策略约束**：部分厂商 ROM 需要额外允许「后台弹出界面」。
- **数据库当前 v5，全部使用 `fallbackToDestructiveMigration`**：仍在测试阶段、每次都允许清库，
  正式发布前必须替换为真正的 `Migration`（已导出 schema，可用于比对），
  并把 `versionCode` / `versionName` / 包名（现在是 `com.example.todolist`）一起改掉。
- 设置中的「默认使用闹钟式」会影响新建待办的默认选项，不改变已有待办。

---

## 7. 环境搭建中踩到的坑（复现时注意）

1. **`gradle.org` / `repo.maven.apache.org` 在本机网络下不可达**。
   `mavenCentral()` 默认解析到 `repo.maven.apache.org`（TCP 超时，构建会长时间卡住），
   必须显式换成 `repo1.maven.org`；Gradle 发行包走腾讯云镜像。
2. **Gradle 发行包不含可直接使用的 `gradle-wrapper.jar`**（8.11 拆成 main/shared 两个 jar，
   手工拼合会 `NoSuchMethodError`）→ `gradlew.bat` 直接驱动已解压的发行包。
3. **AGP 8.9.2 要求 Gradle ≥ 8.11.1**。
4. **androidTest 编译单元访问不到 app 的伴生对象成员**，
   因此依赖图通过 Application 实例 + 顶层扩展函数 `graphOrNull()/requireGraph()` 暴露。
5. **在 `Activity.onCreate` 里直接申请权限会破坏 UI 测试**，
   权限申请改到 `setContent` 之后的 `LaunchedEffect`。
6. **Robolectric 测试很慢**（本机单次全量约 20 分钟以上），
   调试单个用例时用 `--tests "*ClassName*"` 只跑关心的类。

---

## 8. 模拟器调优（本机实测）

默认 AVD 配置跑 UI 测试很慢，主要瓶颈是软件渲染 + 高分辨率。已调整为：

| 项 | 原值 | 现值 | 原因 |
|---|---|---|---|
| hw.lcd | 1080×2400 @420dpi | **720×1600 @320dpi** | 像素量减少约 56%，渲染压力大幅下降 |
| hw.gpu.enabled | `no` | **`yes`**（`hw.gpu.mode=host`） | 原来完全靠 CPU 软渲染 |
| hw.cpu.ncore | 4 | **6** | 宿主 16 线程，多给核心 |
| hw.ramSize | 4096 | **6144** | 减少 GC 与换页 |
| 启动参数 | — | `-cores 6 -memory 6144 -no-audio -no-boot-anim -netdelay none -netspeed full` | 关闭音频与网络模拟开销 |

另外把三个系统动画缩放设为 0（`window_animation_scale` 等），让 UI 操作更快更稳定。

效果（本机）：

| 指标 | 调优前 | 调优后 |
|---|---|---|
| 模拟器冷启动 | 约 60s+ | **约 20s** |
| 应用冷启动（`am start -W` TotalTime） | — | **2.27s** |
| 一次完整 instrumented 测试（构建+安装+22 个用例） | 2–3 分钟 | **约 40–60s** |
| 完整单测（182 用例 / 11 类） | 曾卡住 20 分钟以上 | **约 5–10s** |

单测变快还有两个代码层面的原因：给测试 JVM 加了堆（`-Xmx2048m`），
以及把 ViewModel 里常驻的每秒 `while(true)` 轮询改成按需冷流
（无限循环会让 `runTest` 的虚拟调度器永远不空闲）。

> 稳定性提醒：本机模拟器跑整批 instrumented 测试时偶发 `INSTRUMENTATION_ABORTED:
> System has crashed`（guest 的 system_server 崩了，与用例无关，重启模拟器即可）。
> 遇到时先 `adb shell uptime` 看负载，必要时用 `-no-snapshot-load` 冷启动，
> 并且务必等 `qemu-system-x86_64` 进程完全退出后再启动，否则会报
> 「Running multiple emulators with the same AVD」。

---

## 9. 切换动画的验证方法

`adb` 单张截图约 600ms，正常切换动画只有 280ms，靠连拍抓不到过渡帧。
为此加了一个**只在 debug 生效**的开关：

```powershell
# 把切换动画放慢到 2.5 秒，便于观测
adb shell setprop debug.todo.anim_ms 2500
adb shell am force-stop com.example.todolist
adb shell am start -n com.example.todolist/.MainActivity

# 跑验证脚本：连拍截图并对比帧间差异
python tools\verify_swipe_animation.py

# 复位
adb shell setprop debug.todo.anim_ms 0
```

脚本判据：若存在过渡动画，就必然能抓到「与切换前、切换后都明显不同」的中间帧。

实测结果：

| 场景 | 中间帧 vs 切换前 | 中间帧 vs 切换后 | 判定 |
|---|---|---|---|
| 日→周（动画开） | 平均差 5.2 | 平均差 0.1 | **过渡帧**，且画面里旧周与新周并存、水平位移 |
| 日→周（系统动画缩放=0） | 平均差 5.2 | 平均差 0.1 | 第一帧就已是切换后状态，符合预期 |

发布构建里 `TodoRouteAnim.durationMs` 恒定返回 280，系统属性完全不参与。