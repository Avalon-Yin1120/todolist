# 待办提醒（Todolist）

Android 原生待办事项 App：添加待办（名称 / 起始时间 / 终止时间 / 提前提醒量），
按时间顺序展示，到点提醒。支持**周期待办**、**三种提醒方式**、**日/周/月三种日历视图**。

- 包名 `com.avalonyin1120.todolist`｜版本 `1.0.0-preview1`（**预览版，尚未上架**；版本号带 preview，
  「设置 → 版本号」会显示「预览构建」）
- **默认用通知式提醒**（安静、不需要特殊权限、通知上就能直接完成）；想要到点响铃 + 亮屏 + 全屏提醒页，
  在设置的「新建待办时默认选择」里改成「闹钟式」（也可以改成「不通知」），或在单条待办上单独选

## 功能一览

| 功能 | 说明 |
|---|---|
| 新增 / 编辑待办 | **整屏表单页**（非弹窗）：名称、起始与终止时间、提醒方式、提前提醒量、周期；日期与时间可分别调整 |
| 提醒方式三档 | **闹钟式**（响铃 + 点亮屏幕 + 全屏提醒页，锁屏也生效）/ **通知**（默认，安静）/ **不通知**（到点什么都不做，只把这条待办当记录留着；选它时提前提醒量整块隐藏，也不需要提前量） |
| 周期待办 | 每天 / 每周 / 每月；**周重复可精确勾选星期几，月重复可精确勾选几号**（多选）；**完成按场次记**——勾掉今天这一场不影响明天/后天，同一周的另一天也不受影响；修改或删除时询问「仅这一次」还是「整个系列」 |
| 时段配色 | 未来=淡蓝、即将=琥珀、正在=绿、过去与已完成=灰；**未来时段不允许标记完成** |
| 智能默认 | 新建时自动沿用你上次填写的提前量；提醒方式则用设置里的「新建待办时默认选择」（通知 / 闹钟式 / 不通知） |
| 顶部布局 | 标题居中，左右各一个对称图标按钮（设置 / 新增，不再悬浮遮挡列表）；标题下方显示当前视图的日期范围与倒计时 |
| 日 / 周 / 月视图 | 默认进入日视图；日期范围在顶部明确显示（**周标题带年份**）；三者都支持左右滑动切换；**点底部视图直接回到今天 / 本周 / 本月**，不停在刚才翻到的那一期（再点一次当前视图同样回到本期） |
| **周视图 = 时间轴** | 只画设置里选定的时段（默认 **06:00–22:00**，可在设置里改），等比压进一屏、不滚动；每条待办是一个**高度即时长的实心圆角色块**（不再是一小时一个方块）；时间重叠的自动**平分列宽、左右错开**；颜色与日视图同一套（未来蓝 / 即将琥珀 / 进行中绿 / 已过去灰 / 已完成浅灰）；今天那一列有一条「现在」红线 |
| 冲突提示 | 时间段与已有待办重叠时**仍然创建成功**，但明确告知是哪几条 |
| 删除确认 | 删除前二次确认（可在设置里关闭） |
| 设置 | 独立界面（非弹窗）：**新建待办时默认选择（通知 / 闹钟式 / 不通知 三选一）**、**通知提示音开关**、**周视图显示时段**、精确闹钟权限状态、删除确认、冲突提示、默认提前量、**关于 / 隐私 / 版本号** |
| 关于与隐私 | 「关于待办提醒」讲清三层提醒通道与三种提醒方式；「隐私」逐条说明数据存在哪、不做什么、每个权限用在哪；「版本号」展示版本/包名/构建类型/数据库版本/设备 API |
| 整屏表单 | 字段较多，用整屏页面而非对话框承载；按「名称 / 重复 / 时间 / 提醒」分卡，重复选择紧跟名称 |
| 提醒通知可就地操作 | 通知上直接「完成」或「10 分钟后再提醒」，不必打开 App（后台拉不起界面时这尤其关键） |
| **响铃随时停得掉** | 铃声只有一个持有者（`AlarmRinger`），「知道了 / 完成 / 稍后 / 返回键 / 上滑回桌面 / 按音量键 / 按锁屏键 / 通知上的停止」全都收敛到同一个出口；响铃时常驻通知自带**停止 / 完成**按钮（闹钟页被系统拦住时也能停）；无论如何最长 5 分钟自动停 |
| 通知会响一下 | 通知式提醒到达时播放一次系统提示音，可在设置里单独关掉（关掉后仍会弹通知，只是不出声） |
| 提醒可靠性检查 | 一页看全「通知权限 / 通知样式（静默·锁屏·横幅）/ 精确闹钟 / 允许后台运行 / 全屏提醒 / 国产 ROM 自启动」，
缺项时顶部横幅提示「有 N 项未开启，可能到点不提醒」 |

权限缺失不会崩、也不会静默失效：闹钟式提醒在缺少精确闹钟权限时自动退化为普通通知，
并在界面顶部横幅 + Snackbar 告知用户；**「到点不响」时第一个该看的就是「提醒可靠性检查」页**。

> 真机提醒为什么可能不响（重要）：见 [§5 提醒的可靠性](#5-提醒的可靠性为什么到点不响)。
> 正式发布前还差哪些事：见 [§10 发布清单](#10-发布清单v100)。

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
   展示某个日期范围时按需展开。修改整个周期只改一行，数据也不随时间堆积；
   「跳过本次」「仅修改本次」和「完成本次」都只是这一天上的一条例外（见 §6）。

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
├─ notify/
│  ├─ NotificationHelper.kt    # 两个通知渠道（有声 / 静音，按设置二选一）
│  ├─ AlarmRingService.kt      # 闹钟响铃前台服务（与闹钟页同生命周期，见 §4.6）
│  └─ ReminderActionReceiver.kt# 通知上的「完成 / 10 分钟后再提醒」
├─ sys/                        # 精确闹钟 / 电池优化 / 提醒可靠性体检
└─ ui/
   ├─ TodoListScreen.kt        # 顶部栏 + 底部导航 + 总列表 + 各类对话框 + 页面路由
   ├─ TodoViews.kt             # 日/周/月视图 + 待办卡片（配色规则 TodoColors）
   ├─ WeekLayout.kt            # 周视图时间轴的纯排版算法（重叠分簇 + 泳道）
   ├─ CalendarLayout.kt        # 日期与聚合的纯函数（周起止、月网格、忙闲分档）
   ├─ CompactCalendar.kt       # 自绘日历（可左右滑动翻月）
   ├─ DateTimePickerField.kt   # 日期/时间选择（只读框 + 独立按钮 + 明确确认键）
   ├─ SettingsScreen.kt        # 设置页
   ├─ AboutScreen.kt           # 「关于待办提醒」「隐私」两页 + 版本信息
   ├─ ReminderHealthScreen.kt  # 提醒可靠性检查
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

# 发布包：R8 + 资源压缩 + release 签名（约 1.4 MB，debug 包约 17 MB）
.\gradlew.bat assembleRelease            # 产出 app\build\outputs\apk\release\app-release.apk
.\gradlew.bat bundleRelease              # 上架用 AAB

# 发布前建议在有网环境跑一次完整检查（本机离线缓存里没有 lint-gradle，见 §7）
.\gradlew.bat :app:lint
```

> release 签名从 `release/keystore.properties` 读取；该目录**不进版本库**。
> 文件不存在时 `assembleRelease` 会退回 debug 签名照常构建（便于别人 clone 后编译），
> 只是产物不能上架。签名材料与口令的备份方式见 [§10 发布清单](#10-发布清单v100)。

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
| `domain/RecurrenceExpanderTest` | 一次性/每天/每周/每月展开、时分保留、**每月 31 日在小月落到月末**、例外日期跳过、**完成的那一场仍然出现且只标记为已完成（按场次完成）**、**跳过/仅改本次的例外仍然隐藏该场**、**列表条目取起始日那一场的完成状态**、范围展开排序、下一次场次查找、日期键按本地时区 |
| `domain/TodoClassifierTest` | 四个时段的判定与全部边界（恰好等于提醒时刻/起始/终止）、**未来时段不可标记完成**、已完成可取消完成 |
| `reminder/ReminderSchedulingTest` | 两种通道分别排定、提前量体现在触发时刻、无权限自动降级、**时间段冲突仍创建且带回冲突项**、编辑不把自己算冲突、改时间清已提醒标记、周期跳过某场、周期提醒后顺延、**按场次完成（不改整条 isDone、后续场次照常排定、三通道断言）**、**取消完成恢复该场**、**完成/取消完成不动同一场的跳过例外**、**每周完成一天不影响同周另一天**、**通知里的完成落到刚提醒过的那一场**、**一次性待办的完成行为不变**、全量对账 |
| `reminder/ReminderFiringServiceTest` | 触发后记下场次、**同一场次不重复提醒**、已完成不提醒、周期触发后顺延下一场 |
| `data/TodoRepositoryTest` | 真实 Room 内存库：增删改查、排序、标记场次、**重叠查询（含紧邻不算重叠、已完成不计入）**、周期与例外日期往返 |
| `ui/DraftFormStateHolderTest` | 默认时间取整点、编辑回填、四类校验错误、**提前量为 0 时输入框为空且可删空**、超范围输入标记非法、记住上次提前量、**新建时三档默认提醒方式都能带出来（编辑时用待办自己的档，不被默认档顶掉）**、**默认档是「不通知」也能存且不校验提前量、切回通知后先前填的提前量还在** |
| `data/SettingsStoreTest` | 设置存储（Robolectric 真实 SharedPreferences）：全新安装默认通知式、**三档默认方式都能落库并原样读回**、认不出的脏值回落到默认档、**老用户旧布尔键迁移（`true → 闹钟式`、`false → 通知`）**、**已存过新键的用户不会被残留的旧键覆盖**、内存实现三档读回 |
| `ui/TodoViewModelTest` | 表单开合、非法表单不落库、落库并排定、降级提示、编辑不新增、切换完成、**删除需确认**、权限变化提示、**只改本次中途返回不多出待办**、**日历跳转请求只发一次**、**提醒开关刷新与检查页开合**、**默认提醒方式三档都能落库读回且新建表单跟随设置（编辑时仍用待办自己的档）** |
| `ui/TodoOccurrenceDoneTest` | 周期待办打勾**只完成这一场**（整条 `isDone` 不变、列表与日视图显示已完成、明天/后天仍未完成且照常排定）、**再次打勾取消完成**、**一次性待办打勾仍然改整条状态** |
| `ui/CalendarPagingTest` | 页↔日期互逆、同页等价（同一周/同一月任意一天）、页号 +1 恰好前进一期、跨年跨月、**页码预算覆盖今天前后 50 年且越界会断言** |
| `ui/WeekLayoutTest` | 周视图时间轴排版：不重叠各占整列宽、**完全/部分/链式重叠分成泳道**、首尾相接不算重叠、隔开的簇各算各的、跨午夜裁剪、**可见窗口裁剪与「窗口外的条目不参与分簇」**、零时长与结束早于开始被丢弃、结果按开始时间升序 |
| `domain/WeekWindowTest` | 周视图时段：默认值、合法值原样返回、**起始/结束互相夹取（永远至少 2 小时）**、越界与负数兜底、候选范围选不出非法组合、刻度步长随窗口宽度自动稀疏且数量受控 |
| `ui/CompactCalendarPagingTest` | 日期对话框翻月：页↔月互逆、相邻月差一页、**固定 42 格（6 行 × 7 列）**、首日周一末日周日、闰年二月也补齐 |
| `notify/NotificationHelperTest` | 渠道创建与重要性、幂等、通知 id 映射、通知内容、点击跳转、**有声/静音两个渠道按设置二选一且开关运行时生效**、**升级时清掉旧渠道**、取消、权限识别 |
| `sys/ReminderHealthTest` | 渠道体检：有声渠道**非静默 + 有横幅 + 锁屏可见**、静音渠道被判为静默、**人为改成低重要性后判为静默且无横幅**、锁屏判定只认 `SECRET`、渠道数量与顺序稳定、标题取系统里的实际名字、**体检只读不改渠道**、厂商候选页都不存在时跳转不抛异常 |
| `notify/AlarmRingServiceTest` | 响铃服务生命周期：启动即响、**页面解绑后停铃**、**宽限期内重新绑定则继续响**、销毁即停、立刻静音入口、重复投递不会留下第二条无人持有的铃声、**响铃通知自带「停止/完成」两个按钮**、**「停止全部」不会误停另一条待办的铃声**、**迟到的启动指令不会重新响铃且服务自行退出** |
| `notify/AlarmRingerTest` | 铃声持有者的契约（v18 新增）：起铃后状态与待办一致、换一条待办即替换、同一条重复投递不打断、能识别「正在响的是另一条」、**超时兜底自动停**、**刚被结束的待办不会因为迟到启动又响**、抑制窗口过后可再次响铃、抑制只针对刚结束的那一条、先前的超时不会波及下一次 |

> 当前共 **19 个测试类 / 324 个用例**，`.\gradlew :app:testDebugUnitTest --offline` 全绿。

### 4.2 仪器化测试（API 35 模拟器）

`TodoListUiTest` 覆盖：标题/倒计时/设置入口、新增待办、名称校验拦截、勾选完成后状态、
**默认提醒方式是通知且切到闹钟式后预览随之更新**（表单里的三档芯片与设置页的
「新建待办时默认选择」是同一套控件）、**未来时段不出现勾选框**、
**删除先弹确认框（取消/确认两条路径）**、底部四个视图切换、设置页开合、
**关于页/隐私页进出**、**版本号一行与详情弹窗**、**通知提示音开关落进设置**、
**周视图时段设置（改起始时间、恢复默认）**、**周视图只画窗口内的时段**、
**日期对话框左右滑动翻月**、**周视图把待办画成按时间的圆角色块**、**重叠的两条并排错开**，
以及日历相关的三条回归：**打开日视图就停在今天**、**翻页按钮依次前进且来回对称**、
**从月视图切回日视图仍停在今天**。

要点：
* 通知权限用 `GrantPermissionRule` 预先授予，否则系统权限对话框会盖住界面，
  把 Activity 压到 PAUSED，Compose 会报「No compose hierarchies found」。
* 行与勾选框用「带 id 的标签」定位，不依赖坐标与列表顺序。
* 周视图的色块要**用未合并语义树**查（`useUnmergedTree = true`）：整列是 `clickable` 的，
  会把子节点合并成一个语义节点，默认查询看不到里面的色块。
* 造数据尽量不走界面：涉及「今天某个固定时刻」的用例直接调
  `SchedulingCoordinator.create`，避免「默认起始时间是下一个整点」在深夜跑用例时
  悄悄跨到明天（这个坑真实发生过，见 `setStartToYesterday` 的注释）。
* **点在表单靠下位置的控件前必须先 `performScrollTo()`**：`performClick` 是按节点的
  root 坐标点下去的，节点在屏幕外时点击不会生效。有一条用例正因为默认提醒方式从
  「闹钟式」改成「通知」才暴露出来——它以前「通过」只是因为预览文案本来就是闹钟式，
  点没点上根本不影响断言。

### 4.3 系统行为验证（adb）

```powershell
$adb='D:\Android\sdk\platform-tools\adb.exe'
$pkg='com.avalonyin1120.todolist'

& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell cmd appops set $pkg SCHEDULE_EXACT_ALARM allow
& $adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS

& $adb shell dumpsys alarm | Select-String $pkg          # 精确闹钟是否注册（看 exactAllowReason=permission）
& $adb shell dumpsys jobscheduler | Select-String $pkg   # 通知方式的 WorkManager 作业

# 手动触发一次提醒：注意 extra 是 long，必须用 -el
& $adb shell am broadcast -a com.avalonyin1120.todolist.action.FIRE_REMINDER `
    --el extra_todo_id 1 --el extra_occurrence_start <场次开始毫秒> `
    -n $pkg/com.avalonyin1120.todolist.reminder.ReminderAlarmReceiver

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

另外三个交互细节：

* 翻页基准用 `PagerState.targetPage`（动画目标页）而不是 `currentPage`：
  后者在动画途中还没变，连点两次「下一个」会算出同一个目标、第二次被吞。
* 「回到今天/本周/本月」除了翻页，还会把上层日期显式落回今天；
  否则月视图里日期会停在「当月 1 号」，切回日视图就落到 1 号而不是今天。
* **点底部视图 = 回到今天 / 本周 / 本月**（`TodoViewModel.selectTab`）：切到日/周/月视图时不停在刚才
  在翻的那一期，而是复用上面那套做法——同一个 `selectTab` 里把日期软写入目标期、再让
  `jumpRequest` 自增一次，pager 就滚到当期。两个边界是刻意选的：
  **①「再点一次已经在看的视图」也回到本期**（底部按钮因此只有一条规则：点日/周/月视图 = 看该视图下的
  「现在」，不会「有时生效、有时静默」；「滑远了点一下日视图」顺带成了回到今天的入口）；
  **② 目标按注入时钟现算**（今天 / 本周一 / 本月 1 号），**不**从 `selectedDate` 推，
  所以月视图里存着的「某个月的 1 号」永远不会漏进日视图；而切到月视图时沿用 `selectDateSoft`
  的软写入语义，同一期里更具体的那一天（今天）不被粗粒度的代表日 1 号覆盖。
  翻页回写只走 `selectDateSoft`、**不**自增 `jumpRequest`，因此左右滑动不会被「自动跳回今天」打断。

验证方式见 `ui/CalendarPagingTest`（11 个不变量用例：互逆、单调、同页等价、预算覆盖、
越界断言）、`ui/TodoViewModelTest` 里的「切到周视图会回到本周一并发一次跳转请求」
「切到月视图会回到本月一号并发一次跳转请求」「从月视图切到日视图会回到今天」
「原地再点一次日视图也会回到今天」「切到月视图不会把本月里更具体的那一天降级成一号」
「切到待办视图不改日期也不发跳转请求」，与 `TodoListUiTest` 里的「打开日视图就停在今天」
「翻页按钮依次前进且来回对称」「从月视图切回日视图仍停在今天」。

---

### 4.5 周视图 = 时间轴：为什么这样画

用户给出的样例是一张「左列时间刻度 + 每天一列 + 圆角矩形色块」的图。之前那一版的问题有两个：
**纵向只能看到 0 点到 18 点**（每小时固定 22dp，24 小时要 528dp 加表头，超出一屏），
以及**用「每小时一个方块」表达忙碌**，看不出每条待办本身。

现在的做法：

| 关注点 | 做法 |
|---|---|
| 只画有用的时段 | 纵向只画设置里选定的窗口（默认 **06:00–22:00**，设置 → 周视图显示时段 可改，两端至少间隔 2 小时）。凌晨那一截极少有人排待办，把它算进去等于让每个小时矮掉四分之一 |
| 一屏看完 | `BoxWithConstraints` 量出可用高度，`hourHeight = 可用高度 / 窗口小时数`，窗口内等比压进一屏，**不滚动** |
| 高度即时长 | 色块高度 = `hourHeight × 时长`；短到看不见的给 12dp 下限（15 分钟在压缩后只有几 dp） |
| 刻度骑在整点线上 | 刻度文字放在固定高度的容器里再上移半行高。早先只按整点设 `offset`，文字的上边缘压在线上，视觉上比它标注的时刻低约 7dp，而色块是精确对齐整点线的，两相对照就会觉得「色块偏了」 |
| 重叠错开 | `WeekLayout.blocksOf` 先划分**重叠簇**，再在簇内**贪心分泳道**；同一簇的色块各占 `1/泳道数` 列宽并左右偏移，因此「两个重叠的待办」变成各半宽、并排显示 |
| 左右留缝对称 | 宽度减 `2×1dp` 的同时**也平移 1dp**。早先只减宽度不平移，色块紧贴列分隔线、右边却空着，整列看上去整体偏左 |
| 一矩形 = 一条待办 | 不再把同一时段的忙碌合并成一个色块，也不再画逐格底格 |
| 状态配色 | 直接用日视图那套 `TodoColors.paletteOf(...).accent`：未来蓝 / 即将琥珀 / 进行中绿 / 已过去灰 / 已完成浅灰 |
| 今天 | 表头日号用主色加粗，该列额外画一条「现在」红线（当前时刻落在窗口外时不画） |
| 跨午夜 | `WeekLayout` 会把「前几天开始、还没结束」的待办也捞进来并裁剪到窗口。`CalendarLayout.todosOn` 是按**开始日期**归类的，只看当天的话「前一天 23:00 → 今天 01:00」在第二天的列上会整条消失 |
| 裁剪顺序 | 先按窗口裁、再分簇。反过来的话，「凌晨 5 点那条」（根本看不见）会把清晨的簇撑成两条泳道，让看得见的那条白白变成半宽 |

`WeekLayout` 与 `WeekWindow` 都是纯 Kotlin（不引用任何 Android/Compose 类型），
所以泳道划分、窗口夹取这类「画在屏幕上很难断言」的逻辑由 `ui/WeekLayoutTest`
与 `domain/WeekWindowTest` 钉住。

验证：`TodoListUiTest` 的「周视图把待办画成按时间的圆角色块」「重叠的两条待办会并排错开而不是叠在一起」，
以及真机截图 `验证截图/27-周视图时间轴.png`。

---

### 4.6 闹钟响铃：一个持有者、一个出口

用户先后反馈过两次「闹钟关不掉」「界面关了还在响」。两次的根因都不是某条停止路径写错，
而是**铃声有两个可能的持有者**（前台服务一个、闹钟页一个），导致每个停止入口都得先判断
「现在是谁在响」——只要漏掉一个分支，就留下一条永远停不下来的路径：

| 停止动作 | 旧实现的漏洞 | 用户看到的现象 |
|---|---|---|
| 关掉闹钟页（返回键 / 划掉） | 页面停自己那条 + 解绑服务（600ms 宽限） | 基本可用 |
| 通知里的「完成 / 稍后」 | 只停服务那条（`muteNow` + `stopService`），**页面自己补响的那条没人停**，页面也不关 | 点了「完成」界面还留着并且一直在响 |
| 页面被划到后台（上滑回桌面） | 页面没销毁 → 什么都不停 | 看不到界面却一直响，只能等兜底超时 |
| 服务被系统限制没能启动 | `muteNow` 里 `live == null`、`stopService` 无事可做 | 同上 |
| 闹钟页拉不起来（后台启动界面被拦） | 响铃常驻通知**没有停止按钮**且不可划掉 | 只能听着响满 5 分钟 |

现在的结构：

* **铃声只有一个持有者**：`AlarmRinger`（`notify/AlarmRinger.kt`）。服务与页面都只是
  「启动者」，无论是谁把声音放出来的，任何停止入口都直接调它，不再有分支判断。
  它自带超时兜底（`MAX_RING_MS`），所以「一直响」在物理上不可能超过 5 分钟——
  页面侧原先**没有任何超时**，这是最难查的一块。
* **停止也只有一个出口**：`AlarmRingService.stopEverything(context, todoId)`
  = 静音 + 关掉闹钟页（`AlarmActivity.finishLive()`）+ 撤掉常驻通知 + 退出服务。
  「完成 / 稍后 / 停止 / 关页面」四条路径都调它。若正在响的其实是**另一条**待办
  （新闹钟刚起来、页面还没收到 `onNewIntent`），它只撤本待办的通知，不碰对方的铃声与通知。
* **响铃通知自带「停止」「完成」按钮**，并且 `setDeleteIntent` 也指向「停止」：
  真机上闹钟页有可能根本拉不起来（厂商限制后台启动界面），这时通知就是唯一的交互面。
* **闹钟页的每一种离开方式都有对应回调**：`onUserLeaveHint`（上滑回桌面 / 切应用）、
  `onDestroy`（返回键 / 划掉 / 被回收）、`onStop` 之后 15 秒的看门狗
  （看不见页面 → 判定用户已经走了）。
* **按音量键、按锁屏键也算「我收到了」**（v19 按用户要求补上）：
  音量键在 `onKeyDown` 里直接结束并吞掉事件；电源键不会被投递给应用，
  只能监听运行时注册的 `ACTION_SCREEN_OFF`。注意这两条与早期的取舍**正好相反**——
  早期刻意让熄屏后继续响（模仿系统闹钟），用户反馈那是「关不掉」，于是改掉。
  配套改动见下方「唤醒锁」。
* **屏幕唤醒锁必须收短**：`SCREEN_BRIGHT_WAKE_LOCK` 只要在手上，**电源键按不灭屏**
  （实测 `mWakefulness` 一直 `Awake`，`ACTION_SCREEN_OFF` 永远不来）。
  所以它只在「点亮屏幕 + 拉起闹钟页」期间持有（`onStartCommand` 起算、页面 `onBind` 即释放，
  另有 30 秒硬超时兜底），页面自己用 `FLAG_KEEP_SCREEN_ON` 保持常亮。
* **新闹钟复用同一个页面实例**（`singleInstance` + `CLEAR_TOP`）时会走 `onNewIntent`，
  否则会出现「标题还是上一条，响的却是新闹钟」——这是「关不掉」的另一种版本。

验证方式（模拟器/真机均可复现）：

```powershell
$adb='D:\Android\sdk\platform-tools\adb.exe'
& $adb shell appops set com.avalonyin1120.todolist SCHEDULE_EXACT_ALARM allow
& $adb logcat -c
# 造一条 12 秒后触发的闹钟式提醒
& $adb shell am broadcast -a com.avalonyin1120.todolist.debug.SEED_TODO `
    -n com.avalonyin1120.todolist/com.avalonyin1120.todolist.debug.DebugSeedReceiver `
    -e name 闹钟关闭验证 --el trigger_in_seconds 12 --ez alarm true

# 路径 1：返回键      → input keyevent 4
# 路径 2：上滑回桌面  → input keyevent 3
# 路径 3：通知「停止」→ am broadcast -a com.avalonyin1120.todolist.action.STOP_ALARM_RING `
#                        -n com.avalonyin1120.todolist/com.avalonyin1120.todolist.notify.ReminderActionReceiver
# 路径 4：通知「完成」→ am broadcast -a com.avalonyin1120.todolist.action.REMINDER_DONE `
#                        -n com.avalonyin1120.todolist/com.avalonyin1120.todolist.notify.ReminderActionReceiver `
#                        --el extra_todo_id <id>
& $adb logcat -d -s TodoAlarm
# 期望：闹钟开始响铃 →（对应路径的日志）→ 闹钟停止响铃，且不再有 AlarmRingService
& $adb shell dumpsys activity services com.avalonyin1120.todolist
```

实测结果见 §4.7。

---

### 4.7 四条停止路径的实测记录（API 35 模拟器）

脚本：`发布\验证脚本_闹钟停止.ps1`（工作区）——造一条 8 秒后触发的闹钟式提醒，
等到开始响铃，施加对应动作，再读 `logcat -s TodoAlarm` 与
`dumpsys activity services` / `dumpsys window`。

| 路径 | 日志 | 结论 |
|---|---|---|
| 返回键关页面 | `闹钟开始响铃：todo=21` → `闹钟停止响铃：todo=21` | 服务消失 |
| 上滑回桌面 | `闹钟开始响铃：todo=18` → `用户主动离开闹钟页，结束本次闹钟` → `闹钟停止响铃：todo=18` | 服务消失 |
| 通知「停止」 | `闹钟开始响铃：todo=19` → `闹钟停止响铃：todo=19` | 服务消失 |
| 通知「完成」 | `闹钟开始响铃：todo=20` → `闹钟停止响铃：todo=20` | 服务消失 |

声音本身也做了验证（不只是状态字段）：响铃期间 `dumpsys audio` 里能看到

```
AudioPlaybackConfiguration piid:631 type:android.media.MediaPlayer u/pid:10216/6298
  state:started attr: usage=USAGE_ALARM content=CONTENT_TYPE_SONIFICATION
```

即**真的有一个 MediaPlayer 在 USAGE_ALARM 通道上播放**；执行「停止」后该播放器消失。
另外日志里能看到 `同一条待办的重复投递，继续沿用当前铃声`，
说明「服务与页面同时起铃」不会变成两声或断一下。

#### 实测中查明的两个系统限制（决定了闹钟页什么时候能出现）

跑脚本时遇到过「明明到点响铃了，闹钟页却没弹出来」。查 `logcat` 才看清：

1. **前台服务在后台不能拉起界面。**
   ```
   E ActivityTaskManager: Background activity launch blocked!
     callingUidProcState: FOREGROUND_SERVICE; callingUidHasAnyVisibleWindow: false ... BAL_BLOCK
   ```
   屏幕亮着、用户正在用别的应用时，`AlarmRingService` 里那句 `startActivity` 会被系统拦掉。
   此时**只有通知**（响铃通知现在自带「停止」「完成」，见 §4.6）。
2. **Android 14+ 会收回全屏 Intent。** 非闹钟/通话类应用用过一次全屏 Intent 之后，
   系统把 `USE_FULL_SCREEN_INTENT` 标记为拒绝（`appops get ... USE_FULL_SCREEN_INTENT`
   会显示 `default; rejectTime=...`），后续提醒降级成横幅通知。
   只有在**屏幕关闭/锁屏**时发的全屏 Intent 才会真正拉起闹钟页（这正是系统闹钟的行为），
   而且是用户手动在 设置 → 通知 → 全屏提醒 里允许之后。

所以这张表的路径 1/2 是「应用前台或锁屏时页面已经显示」的情形；
「页面根本拉不起来」时，出口是响铃通知上的两个按钮——那条路径也单独测了（表里的路径 3/4
就是在页面未出现、`ring-only` 的状态下完成的）。这两条限制**不是本应用能绕过的**，
应用能做的都做了：通知给按钮、检查页给开关入口（§5.4 第 4 条）。

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
$adb='D:\Android\sdk\platform-tools\adb.exe'; $pkg='com.avalonyin1120.todolist'
& $adb shell pm grant $pkg android.permission.POST_NOTIFICATIONS
& $adb shell cmd appops set $pkg SCHEDULE_EXACT_ALARM allow

# debug 专用：造一条「40 秒后提醒」的待办（走真实的 create + 调度链路）
& $adb shell am broadcast -a com.avalonyin1120.todolist.debug.SEED_TODO `
    -n "$pkg/com.avalonyin1120.todolist.debug.DebugSeedReceiver" `
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
2. 同页 → **耗电管理 → 允许完全后台行为**（不优化）。应用内这一项的按钮走的是一条
   **候选意图链**：ColorOS 的耗电管理 / 后台冻结 → ColorOS 自启动管理 → AOSP 电池优化列表页
   （`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`）→ 本应用的应用详情页。每个候选都先
   `resolveActivity` 再 `startActivity`，全都不存在才退到应用详情页，因此**不会因为厂商页面
   不存在而崩，也不会跳到不存在的页面**。厂商组件名各版本不一致（`com.coloros.oppoguardelf` /
   `com.oplus.battery` 下的 `PowerUsageModelActivity` 一类），**只作候选、未在真机实测**，
   所以我们才逐个 resolve 而不是写死一个；文案里同时给出文字路径，跳偏了用户也能自己走。
   本应用**不申请** `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限，因为它违反 Google Play 政策，
   详见 §7.9
3. 同页 → 权限管理 → 通知 → 允许**锁屏显示 / 允许打扰**
4. 同页 → 权限管理 → **其他权限 / 特殊应用权限 → 后台弹出界面**（ColorOS 12+ 里这一档叫
   「特殊应用权限」）。这是 ColorOS 上**真正卡住「闹钟页弹不出来」**的开关：不允许时应用在后台
   拉不起界面（Android 10+ 系统本身就禁止后台启动界面——实测日志 `Background activity launch
   blocked!`——厂商再收紧一层），到点的表现就是「什么都没发生」，而铃声/通知可能已经正常触发。
   它与第 6 条的「发送全屏通知」是两件事：那条管的是 Android 14+ 的全屏 Intent 会不会被降级，
   这条管的是厂商允不允许后台拉界面；两者都缺时现象相似，所以检查页分成两项。
   这一项**读不到真实状态**（OEM 的 appop 名字各版本不同，没有公开 API 能读），所以检查页
   **不判定、不谎报**：`ok` 只用于中性配色、右上角写「需人工确认」、不计入顶部缺项数，
   「去设置」始终可点。按钮走一条**候选意图链**（ColorOS 的「特殊应用权限」页 →
   `com.coloros.safecenter` / `com.oplus.safecenter` / `com.coloros.oppoguardelf` 下的
   「权限管理」页 → MIUI 的两个权限编辑页（带 `extra_pkgname`）→ EMUI 的权限管理页），
   每个候选都先 `resolveActivity` 再 `startActivity`，全都不存在才退到应用详情页（从那里也能
   走到权限管理）。**这些厂商组件名都未在真机实测过**（只有模拟器），同一个页面在不同 ColorOS
   版本上归属的包都不一样，因此只作候选；正因为跳偏的概率不低，文案里同时给出文字路径
5. **设置 → 通知 → 待办提醒 → 逐条类别（渠道）**：关掉「静默通知」，打开「锁屏」与「横幅」。
   只开最上面的「允许通知」远远不够——本项目有两个渠道（有声 / 静音），**每一条**的静默、
   横幅、锁屏都是独立的开关。检查页的「通知样式（静默 / 锁屏 / 横幅）」一项会把两条渠道的
   现状列出来，每条各带一个「去设置」直达它自己的渠道页（另有「打开通知设置（全部类别）」）。
   这一项的通过条件只看**当前实际使用的那条渠道**（由设置里的「通知提示音」决定走有声还是静音）。
   「静默」只按**重要性**判定（`importance <= IMPORTANCE_LOW`），理由见 §6 里「通知渠道（类别）的
   三个开关是逐个渠道的」那一条；
   每条渠道行末尾的括号里会原样带出应用读到的原始值（`importance=4 / 声音=读不到 / 振动=开 /
   锁屏=可见`），供真机排查
6. **设置 → 通知 → 待办提醒 → 发送全屏通知（允许）**：Android 14+ 起这是闹钟页能否自动弹出的
   决定性开关，非闹钟类应用的全屏 Intent 会被系统收回（§4.7 第 2 条）。检查页这一项的标题与
   文案直接用系统里那个开关的名字「发送全屏通知」（叫「全屏提醒」的话用户在设置里搜不到），
   判定沿用 `NotificationManager.canUseFullScreenIntent()`；按钮先跳
   `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`，跳不到再退到本应用的通知设置页（Android 14+ 上
   同一个开关也在那里）→ 应用详情页。不开也能用，但到点只会出现横幅通知（铃声照常响、
   通知上可停止/完成）

小米/华为/荣耀/vivo 的对应路径也写在该页的「国产 ROM 需要额外设置」卡片里。
**自启动没开时，系统会直接拦截广播拉起应用，闹钟登记了也不会响——这不是 App 能绕过的。**

### 5.5 「进程被杀之后还能不能响」实测

用户反馈「把后台进程杀掉之后，通知和闹钟都不来了」。这件事要分成两种「杀」，
它们的后果完全不同（API 35 模拟器实测，脚本见下）：

| 杀法 | 命令 | 闹钟登记 | 到点触发 | 说明 |
|---|---|---|---|---|
| 杀进程（系统回收内存 / 低内存清理） | `am kill <pkg>` | 保留 | ✅ **照常触发**，进程被拉起 | 日志：`已发送提醒：id=5 todo=5 mode=NOTIFICATION`，`pidof` 变成了新进程 |
| **强行停止**（force-stop；部分 ROM 的「一键清理」「上滑划掉」会等效于它） | `am force-stop <pkg>` | 被撤销 | ❌ **不会触发** | 日志：`Updating package stopped masked state ... isStopped true` |

第二种是 **Android 的硬规则，不是应用能绕过的**：被置为 stopped 的包在所有广播（含闹钟投递、
开机广播、WorkManager 作业）中都收不到，直到用户再次手动点开这个应用。此时我们做的三件事是：

1. **减少它发生**：引导用户打开「允许自启动」并把耗电管理设为「允许完全后台行为」（§5.4 第 1、2 条），
   ROM 就不会把上滑划掉当成强停；
2. **两种提醒方式都走精确闹钟**（`setAlarmClock` / `setExactAndAllowWhileIdle`），
   在 Doze、后台冻结这些「不杀进程」的省电手段下仍会被投递；
3. **打开应用时全量对账**（`reconcileAll`）：被强停期间错过的场次，只要还在 15 分钟宽限期内会被补发，
   更早的则不补（避免一次刷屏），这是取舍，写在 §5.2。

复现命令：

```powershell
$adb='D:\Android\sdk\platform-tools\adb.exe'; $pkg='com.avalonyin1120.todolist'
& $adb shell am broadcast -a "$pkg.debug.SEED_TODO" -n "$pkg/$pkg.debug.DebugSeedReceiver" `
    -e name 杀进程验证 --el trigger_in_seconds 45 --ez alarm true
& $adb shell dumpsys alarm | Select-String FIRE_REMINDER     # 登记数
& $adb shell am kill $pkg                                    # 或 am force-stop $pkg
& $adb shell dumpsys alarm | Select-String FIRE_REMINDER     # 强停后会被清掉
& $adb logcat -s TodoReminder TodoAlarm                      # 45 秒后看有没有触发
```

---

## 6. 行为约定与已知限制

- **不会补发过期提醒**：提醒时刻已过去的场次在对账时被跳过，避免装上 App 后被历史事项刷屏。
- **周期待办按场次记录已提醒**：用 `remindedOccurrenceStart` 记住是哪一场，因此既不会重复提醒也不会漏。
- **周期待办的完成也是按场次的**：勾掉某一场只在那一天的日期上记一条 `COMPLETED` 例外，
  **不改整条记录的 `isDone`**——`isDone` 会被每一场继承，改它就会让所有场次一起变已完成
  （用户报过的 bug）。因此：完成的那一场仍按「已完成」样式显示，可以再次点击取消完成；
  其它场次照常显示「未开始 / 进行中」并照常提醒。只有该场不再提醒，后续场次照常排定，
  不会因为勾了一场就把整个周期一起废掉。`isDone` 只对一次性待办有意义；
  旧版本误写在周期待办上的 `isDone=true` 会被忽略（不再当作「整个系列已完成」）。
- **「仅修改本次」的实现**：把这一次落成独立待办（`parentId` 指向原规则），
  并在原规则对应日期记一条 `SKIPPED` 例外（`OVERRIDDEN` 为此预留，当前实现不写它）；
  「删除本次」同样只记 `SKIPPED` 例外。三种例外（`SKIPPED` / `OVERRIDDEN` / `COMPLETED`）
  互不干扰：只有前两种会让那一场从日历里消失，`COMPLETED` 只是把这一场标记为已完成。
- **提醒方式变了会重置已提醒标记**，改过提醒时间/方式的待办会再次提醒；只改名称不会。
- **「10 分钟后再提醒」只看整条的 `isDone`**：稍后提醒是在通知的广播里直接重发通知的
  （`ReminderActionReceiver`，与调度编排无耦合），因此周期待办被勾掉的那一场如果之前点过
  「稍后提醒」，那一次补发仍会到达；它不会再排下一场，也不会影响其它场次。
- **通知方式的延迟由系统决定**，国产 ROM 省电策略可能延迟很久甚至杀掉任务，
  这是系统行为而非代码缺陷；需要准时请选闹钟式。
- **闹钟式需要精确闹钟权限**（Android 12+ 默认关闭）。本应用**同时声明** `SCHEDULE_EXACT_ALARM`
  （Android 12 用）与 `USE_EXACT_ALARM`（Android 13+ 安装即授予、用户关不掉），因此新版上
  「精确闹钟」这一项通常是恒真的，界面文案会变成「已由系统自动授予（USE_EXACT_ALARM），
  到点精确触发」；卡片上仍留一个「系统设置」入口，以防某些 ROM 上被额外限制。
- **通知渠道（类别）的三个开关是逐个渠道的**：只开最上面的「允许通知」还不够，
  还要逐条关掉「静默通知」并打开「锁屏」「横幅」。检查页的「通知样式」一项把每条渠道的
  现状列出来并各配一个直达入口，判定依据是：
  **静默** = `importance <= IMPORTANCE_LOW`——**只看重要性，`sound == null` 不再单独算静默**；
  **横幅** = `importance >= IMPORTANCE_HIGH`（heads-up 只由高重要性触发）；
  **锁屏** = `lockscreenVisibility != VISIBILITY_SECRET`。这一项只看**当前实际使用的那条渠道**
  （由「通知提示音」设置决定走有声还是静音），所以关掉提示音不会让这一项变红。
  **为什么把 `sound == null` 这一支去掉（Android 15 / ColorOS 真机误报）**：旧判定是
  `importance <= IMPORTANCE_LOW || sound == null`，在用户真机上被证伪——系统通知设置里
  「待办提醒（有声）」的静默通知**明明是关的**，应用却判它静默，于是这一项永远显示「未开启」，
  还劝用户去关一个已经关掉的开关（渠道建好后应用改不动，只能用户改，而用户那边已经是对的，
  因此**永远无法消除**）。误报只可能来自 `sound == null`：那台 ROM 上 `getSound()` 读回 null
  （渠道创建时传的默认提示音 URI 解析不到，系统响铃时用它自己的音源；也有 ROM 把铃声记在
  自家字段里，AOSP 这侧永远是 null）。也就是说这个字段在真机上**既不是静默的充分条件，
  也不是必要条件**，只能当噪声。低重要性一定不出声（系统保证），高重要性不保证有声，
  但绝不能据此说它静默——误报的代价是用户永远修不好，漏报的代价只是不出声，
  而界面会把「声音=读不到」这个原始值摆出来（每条渠道行末尾括号里
  `importance=4 / 声音=读不到 / 振动=开 / 锁屏=可见`），谁都能看到线索。
- **「后台弹出界面」这一项不做状态判定**：ColorOS / MIUI 的这道厂商开关没有公开 API 可读，
  硬判就等于猜（要么永远红、要么永远绿），所以检查页把它做成**始终可点的引导项**：
  中性配色、右上角写「需人工确认」、不计入顶部缺项数，只给入口与文字路径，由用户自己在
  系统里核对。详见 §5.4 第 4 条。
- **闹钟页能不能自动弹出来由系统决定**：Android 10+ 禁止后台的前台服务直接拉起界面
  （实测 `Background activity launch blocked!`），厂商的「后台弹出界面」再拦一层
  （ColorOS：权限管理 → 其他权限 / 特殊应用权限 → 后台弹出界面），Android 14+ 又会收回
  非闹钟类应用的全屏 Intent（用户侧开关名是**「发送全屏通知」**，可读，走的还是
  `canUseFullScreenIntent()`）。屏幕关闭/锁屏 + 这两处都允许时才会像系统闹钟那样整屏弹出；
  否则只有横幅通知（铃声照常响，通知上有「停止 / 完成」）。详见 §4.7。
- **迟到的启动指令会被忽略 60 秒**：Android 12+ 可能把「后台启动前台服务」推迟十几秒，
  实测出现过「用户已经关掉闹钟页，`onStartCommand` 才到」。这种迟到的启动如果照常起铃，
  用户就会看到「明明关掉了，过一会儿又响」。因此同一条待办在停止后 60 秒内不再起铃，
  这个窗口远小于最短的真实重复提醒间隔（稍后提醒 10 分钟），不会吞掉下一次提醒。
- **按音量键或锁屏键也算「我收到了」**（两种情形都要成立）：
  * **闹钟页在前台**时：音量键在 `onKeyDown` 里直接结束本次闹钟并吞掉事件
    （不要变成「只是把音量调小、铃还在响」）；电源键不会投递给应用，靠 `ACTION_SCREEN_OFF`。
  * **闹钟页根本没弹出来**时（Android 10+ 禁止后台前台服务拉起界面、14+ 还会收回全屏 Intent，
    这种情形很常见）：上面两招都失效，因为没有任何界面在听。所以**服务侧自己也听一份**——
    `ACTION_SCREEN_OFF` 停铃，另外监听 alarm/music/ring/notification 四个音量流，
    响铃期间**任一流的值真的变了**就认定用户按了音量键（只认「值变了」是防止某些 ROM
    在免打扰/铃声模式变化时误停闹钟）。
  * 这里有一个**必须成对理解**的坑：响铃服务原本握着一把 `SCREEN_BRIGHT_WAKE_LOCK`，
    只要它在手上，**电源键根本按不灭屏**（实测 `mWakefulness` 一直是 `Awake`），
    上面那条广播永远不会来。所以唤醒锁只在「点亮屏幕、把闹钟页拉起来」期间持有
    （页面 `onBind` 即释放，另有 **10 秒**硬超时），页面自己用 `FLAG_KEEP_SCREEN_ON` 保持常亮。
- **铃声用应用自带的音频，不再依赖系统闹钟铃声**：`AlarmRinger` 优先播放
  `res/raw/alarm_beep.wav`（MediaPlayer + `USAGE_ALARM` + 循环），放不出来才退回系统铃声并打日志。
  原因是真机上 `RingtoneManager.getDefaultUri(TYPE_ALARM)` 可能取到 null 或取到无权限播放的音源，
  而「闹钟不响」正是用户最在意的问题——宁可声音统一，也不要时响时不响。
- **通知渠道用 `todo_reminder_v3`**：渠道的**声音创建后不可更改**，历史上两代渠道都因此废掉
  （`todo_reminder` 建的时候就没声音；`_v2` 在部分 ROM 上系统提示音 URI 解析不到、被建成无声渠道）。
  v3 的提示音取不到系统默认值时会退回自带音频，因此不会再出现「有声渠道其实没声音」；
  旧的两代渠道在启动时删除。日志里会打印 `通知渠道就绪：todo_reminder_v3 声音=... 重要性=...`。
- **「迟到启动」的抑制按「待办 + 这次闹钟的身份」判定**：身份取待办的 `updatedAt`，
  所以「关掉闹钟 → 编辑提醒时间 → 到点」**照响**（用户真机上遇到过被这段窗口吞掉的情况），
  而没有编辑过的迟到投递仍然被挡掉。
- **版本号里带 `preview` 时，版本页显示「预览构建」**：目前不发布正式版，
  装 release 包的人是在帮忙试用，把预览包说成「正式构建」会误导对问题的判断。
- **数据库当前 v5，已移除 `fallbackToDestructiveMigration`**：从 1.0.0 起用户数据就是真实资产，
  **任何 schema 变更都必须配一条 `Migration`**（忘写会在升级时直接抛异常，开发期立刻暴露，
  而不是悄悄清空用户数据）。本版本把包名从 `com.example.todolist` 改成了
  `com.avalonyin1120.todolist`，对所有人都是全新安装，因此现在不需要迁移历史库。
  加迁移的步骤写在 `TodoDatabase.MIGRATIONS` 的注释里。
- **默认提醒方式是「通知」**：设置里的「新建待办时默认选择」是三选一（通知 / 闹钟式 / 不通知），
  想默认响铃就改成「闹钟式」。它**只预先选中新建表单里的那一档**，之后每条待办都能单独改。
  老版本（v17 及更早）那个布尔开关会在读取时翻译一次（`true → 闹钟式`、`false → 通知`），
  所以升级安装不会把用户之前选的「闹钟式」悄悄改回「通知」。
- **选了「不通知」就不排任何调度**：这一档到点不发通知、也不响铃，只作为记录留在列表里
  （卡片上标「不提醒」）。提前量对它没有意义，因此表单里整块隐藏、保存时也不做提前量校验；
  从其它两档改成「不通知」时，该待办已有的调度（含 3 分钟后的保险通道）会被立刻撤销。
- **通知提示音是「响一下」，闹钟式是「一直响到处理」**：两者互不影响，
  关闭「通知提示音」不会让闹钟式提醒变安静。
- **通知渠道创建后声音不可更改**，所以「有声 / 静音」是两个固定渠道按设置二选一；
  升级安装时会主动删掉旧版本留下的渠道（`todo_reminder`），避免系统设置里堆同名项。
  同理，**静默 / 横幅 / 锁屏这三项应用只能读、不能改**（渠道的重要性与声音创建后就由用户与
  系统掌握），所以检查页的做法是读出真实状态再引导用户去系统设置里改；真去调
  `createNotificationChannel` 传新值也不会生效，只会让人误以为已经改好了。
- 设置里的「新建待办时默认选择」只影响**新建**表单的预选项，不改变已有待办；
  编辑某条待办时用的永远是它自己存着的提醒方式（否则一进表单再保存就会把「不通知」的
  记录改成响铃提醒）。
- **点日 / 周 / 月视图 = 回到本天 / 本周 / 本月**：切换视图不会停在刚才翻到的那一期
  （再点一次当前已选中的视图同样回到本期，因此底部按钮也兼作「回到今天」的入口）；
  「待办」不是日期视图，点它只切列表、不动日期。左右滑动与左右翻页按钮照常只移动一期。
- **周视图只画设置里选定的时段**（默认 06:00–22:00）：窗口外的部分被裁掉，
  整条都在窗口外的待办不画。想看被裁掉的细节，把窗口拉宽或点该列进日视图。
- **周视图把窗口压进一屏**，因此极短的待办（十几分钟）会贴着 12dp 的高度下限。
- **列表底部预留了 96dp**：最后一个待办不会被贴死在屏幕底边，也不会被从底部弹出的
  Snackbar（保存成功、时间段冲突提示）挡住。
- 设置页/关于页/隐私页的滚动位置会保留（`rememberSaveableStateHolder`），
  从「关于」返回不会跳回顶部。

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
7. **`lint-gradle` 拉不到，`assembleRelease` 会被 `lintVitalAnalyzeRelease` 卡住**：
   `com.android.tools.lint:lint-gradle:31.9.2` 不在离线缓存里，Google Maven 也拉不动
   （在线解析长时间无响应），于是 release 构建直接失败。已在 `app/build.gradle.kts` 里
   `lint { checkReleaseBuilds = false }` 关掉构建期检查，发布前请在有网环境手动跑
   `.\gradlew :app:lint`。**后来联网跑通了一次**（见 §7.9、§7.10），
   连它一起抓出了两个会让 release 直接失败的检查项——所以这一步不要省。
8. **改包名比想象中碎**：`namespace` / `applicationId`、四个源码集的目录、
   `app/schemas/<包名>.data.local.TodoDatabase/` 的目录名、以及所有 `import` 与
   文档里的 adb 命令都要一起改。Kotlin 不强制目录与包名一致，所以漏搬目录**不会**
   编译报错，只会让源码树变得莫名其妙——改完请 `Get-ChildItem -Recurse -Directory` 核一遍。
9. **`java.time` 里有些方法 minSdk 26 用不了，而只跑 API 35 根本测不出来**：
   `LocalDate.ofInstant` 要 API 34（Android 官方 API 数据库里写的是 `since="34"`），
   本项目 minSdk 26，Android 8~13 上会直接 `NoSuchMethodError`——而它会出现在
   `TodoViewModel` 的初始化里，也就是**一打开应用就崩**。
   单测跑在 Robolectric 35、模拟器也是 API 35，两边都发现不了；
   是 `.\gradlew :app:lint` 的 `NewApi` 检查把它标成 release 阻断错误的。
   遇到 `java.time` 的可疑方法，先查 `<sdk>/platforms/android-35/data/api-versions.xml`
   里的 `since=`；替换成 `Instant.atZone(zone).toLocalDate()` 这类 API 26 就有的写法。
10. **`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 会被 lint 判为违反 Play 政策**
    （`BatteryLife`），Google Play 也曾因此拒绝过应用。本应用的做法是不声明该权限，
    改为打开系统的电池优化列表页让用户自己设（`ReminderHealth.openBatteryOptimizationSettings`）；
    读取「是否已豁免」用的是 `PowerManager.isIgnoringBatteryOptimizations`，**不需要权限**。
11. **实现了 `Configuration.Provider` 还要把库自带的初始化器删掉**：否则 WorkManager
    在进程启动时就用默认配置初始化了一次，我们那份配置被静默忽略——lint 的
    `RemoveWorkManagerInitializer` 会直接报错。修法是在清单里用
    `tools:node="merge"` + meta-data `tools:node="remove"` 摘掉（见 `AndroidManifest.xml`）；
    摘完务必真机/模拟器验一次 `dumpsys jobscheduler` 里还有 WorkManager 的 job（§5.3）。
12. **本机的 `pwsh` 其实是 Windows PowerShell 5.1**：它按 ANSI 代码页读取**没有 BOM** 的
   `.ps1`，中文注释会变成乱码并触发一堆「意外的标记」语法错误。
   写脚本时统一存成 **UTF-8 with BOM**（`tools/*.ps1` 同理）。

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
| 一次完整 instrumented 测试（构建+安装+30 个用例） | 2–3 分钟 | **约 45–60s** |
| 完整单测（306 用例 / 18 类） | 曾卡住 20 分钟以上 | **约 10–35s** |
| release APK 体积（R8 + 资源压缩） | — | **1.4 MB**（debug 包 17.1 MB） |

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
adb shell am force-stop com.avalonyin1120.todolist
adb shell am start -n com.avalonyin1120.todolist/.MainActivity

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

---

## 10. 授权、分发与发版

> **本项目不上架应用商店**：以开源方式发布，构建好的 APK 通过 **GitHub Release** 分发。
> 版本号保持 `-previewN` 后缀，应用内版本页显示「预览构建」，提醒下载的人这是开发中的包。

### 10.1 授权

本项目以 **Apache License 2.0** 发布，全文见 [LICENSE](LICENSE)。要点：

- 可以自由使用、修改、商用、**闭源**再分发，只需保留版权声明与许可证副本；
- 第 3 条附带了**专利授权**——这正是 Android 生态绝大多数库都用 Apache-2.0 的原因；
- 第 6 条不授予商标使用权：请不要用本项目的名称或图标暗示「官方出品」。

随 APK 分发的第三方组件**全部是宽松许可**（以 Apache-2.0 为主），
**没有任何 GPL / AGPL / LGPL 代码**，完整清单见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
（由 `tools/gen_third_party_notices.py` 从真实解析出的依赖树生成，不是手写的）。
测试依赖里存在 EPL-1.0（JUnit 4）、CDDL+GPLv2-with-CPE 等，但它们**只在构建/测试期使用，
不会进入 APK**，也不构成对源码的传染。

### 10.2 发布一个版本（GitHub Release）

1. **改版本号**：`versionCode` **必须比上一版 +1**（系统与安装器据此判断新旧），
   `versionName` 只给人看，预览版带 `-previewN`。
2. **构建**：见 §3.2，产物 `app/build/outputs/apk/release/app-release.apk`。
3. **校验**（两条都不能省）：
   ```powershell
   $bt = "$env:ANDROID_HOME\build-tools\35.0.0"
   & "$bt\aapt2.exe" dump badging app\build\outputs\apk\release\app-release.apk | Select-String 'package:|sdkVersion'
   & "$bt\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
   ```
   第二条**不能**出现 `CN=Android Debug`；第一条的 `versionCode` 必须比上一版大。
4. **上传**：GitHub Release 里附上 APK、SHA-256 校验值、更新说明，
   并链接 `LICENSE` 与 `THIRD_PARTY_NOTICES.md`（Apache-2.0 要求随分发提供许可证副本）。
5. **签名永远是同一套 keystore**（`release/` 目录，已在 `.gitignore` 里排除）。
   换签名会导致老用户装不上新版本（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。

### 10.3 签名指纹（供下载者校验）

| 项 | 值 |
|---|---|
| SHA-256 | `32:26:B6:1F:65:03:D5:2B:0C:5B:9E:F1:B5:0E:88:E3:95:21:90:A7:83:67:4F:61:CA:F3:02:74:65:B3:33:A6` |
| SHA-1 | `D8:17:D5:A4:B6:35:23:3C:74:1B:93:D0:32:89:7E:8B:5C:BD:FD:75` |
| DN | `CN=Avalon-Yin1120, OU=Mobile, O=Avalon-Yin1120, L=Shenzhen, ST=Guangdong, C=CN` |

装之前可以自己核对：`apksigner verify --print-certs 待办提醒.apk`。

> ⚠️ **只从本仓库的 Releases 下载 APK。** 任何第三方站点重新打包的版本都可能被植入代码，
> 而签名指纹一比对就能看出来。

### 10.4 发布前复核清单

| 项 | 状态 | 说明 / 证据 |
|---|---|---|
| 包名 | ✅ | `com.avalonyin1120.todolist`；开机后不可修改 |
| 版本号 | ✅ | 当前 `versionCode = 2`、`versionName = "1.0.1-preview1"`；`aapt2 dump badging` 实测与产物一致 |
| release 签名 | ✅ | `apksigner verify` 实测 v2 + v3 已签名，DN `CN=Avalon-Yin1120`；keystore 与口令**不在版本库**里，务必离线备份两处以上 |
| R8 + 资源压缩 | ✅ | release 包 **1.4 MB** 量级（debug 约 16.5 MB）；`app/proguard-rules.pro` 逐条写了为什么需要那条规则 |
| 单元测试 | ✅ | **351 条 / 21 个类**全绿（`:app:testDebugUnitTest`，Robolectric，无需设备） |
| 数据库迁移策略 | ✅ | 显式 `MIGRATIONS` 数组，没有 `fallbackToDestructiveMigration`（升级即清库）。加迁移的步骤写在 `TodoDatabase.MIGRATIONS` 注释里 |
| 权限最小化 | ✅ | release 清单实测 11 条 = 9 个自有权限 + WorkManager 自动声明的 `ACCESS_NETWORK_STATE` + AndroidX 自动生成的 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`；**没有 `INTERNET`**、**没有 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`** |
| 无联网 | ✅ | 没有 `INTERNET` 权限 ⇒ 系统在内核层面拒绝本进程建连。数据全部留在本机 Room 数据库里 |
| `USE_EXACT_ALARM` | ✅ 已声明 | Android 13+ 安装即授予精确闹钟；`SCHEDULE_EXACT_ALARM` 同时保留给 Android 12。两者都没有时调度器自动退化为非精确（不会崩、不会静默失败） |
| 两种提醒都精确 | ✅ | 通知方式用 `setExactAndAllowWhileIdle`，闹钟式用 `setAlarmClock` |
| 后台运行跳转 | ✅ | 「去设置」为候选意图链：ColorOS 的耗电管理/自启动组件（逐个 `resolveActivity`）→ 应用详情页 → 系统电池优化列表 |
| 检查页 | ✅ | 「照着做的清单」：人话标题 + 系统里的实际操作路径 + 「去设置」+「我已设置好」勾选；读不到状态的项交给用户确认，不谎报 |
| debug 专用组件不导出 | ✅ | `DebugAlarmReceiver` / `DebugSeedReceiver` 只在 `src/debug/AndroidManifest.xml`；其余组件 `exported="false"`，已用 `aapt2 dump xmltree` 逐条核对 |
| lint | ✅ | **0 error**；余下 warning（未用字符串资源、`ObsoleteSdkInt`、`UseKtx` 等）逐类复核后接受 |
| release 包实测 | ✅ | 装到 API 35 模拟器：正常启动、无异常日志；`dumpsys alarm` 有 `action.FIRE_REMINDER`、`dumpsys jobscheduler` 有 WorkManager 的 job（按需初始化正常） |
| 应用图标 | ✅ | 自适应图标（品牌色 `#4F46E5` + 勾选图形）+ Android 13 主题图标单色层，**矢量自绘**，无第三方素材 |
| 提示音 | ✅ | `res/raw/alarm_beep.wav` 由 `tools/make_alarm_beep.py` **程序合成**（880/1170 Hz 纯正弦，频谱实测无谐波），非第三方音效 |
| 闹钟随时停得掉 | ✅ | 铃声单一持有者 + 单一停止出口 + 通知上的「停止/完成」+ 迟到启动抑制；**按音量键 / 按锁屏键即视为已收到**（为此把服务的唤醒锁收短到只在拉起页面期间持有，否则电源键按不灭屏） |
| 提醒方式三档 | ✅ | 通知 / 闹钟式 / 不通知；「不通知」只记录不排调度，表单隐藏提前量，卡片标「不提醒」 |

### 10.5 每次发版的固定流程

改代码 → `versionCode + 1`、必要时改 `versionName` → `:app:testDebugUnitTest` →
`assembleRelease` → 上面 §10.2 的两条校验 → 打 tag（`v1.0.1-preview1`）→
GitHub Release 上传 APK + SHA-256 + 说明。
