# 参与开发

谢谢愿意帮忙。这个项目的目标很小：**一个不联网、数据只在自己手机上的待办提醒 App**，
把「到点真的会响」这件事做到尽量可靠。所以下面几条比代码风格更重要。

## 一、先跑起来

```bash
# 需要 JDK 17 与 Android SDK（compileSdk 35）
export ANDROID_HOME=$HOME/Android/Sdk      # Windows: $env:ANDROID_HOME="D:\Android\sdk"

./gradlew :app:testDebugUnitTest      # 单元测试，392 条，不需要设备
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:assembleRelease        # 发布包（需要 release/keystore.properties）
```

没有 `release/keystore.properties` 时 `assembleRelease` 会用 debug 签名并在构建日志里警告，
**这不影响本地调试**，但请不要把这种包装给任何人（与正式版签名不同，装上去等于换了个应用）。

模拟器上跑：

```bash
emulator -avd <你的AVD> -no-boot-anim
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.avalonyin1120.todolist/com.avalonyin1120.todolist.MainActivity
```

## 二、几条硬约束

1. **不许加 `INTERNET` 权限**。这是产品的核心承诺，不是「暂时不需要」。
   需要网络的功能请先开 issue 讨论，不要直接提 PR。
2. **数据库改表结构必须配 `Migration` + 迁移测试**。
   用户数据是真实资产；`TodoDatabase.MIGRATIONS` 的注释里写了加迁移的四步。
   忘了写迁移，升级时会直接抛 `IllegalStateException`（宁可崩，也不静默清库）。
3. **`versionCode` 只能加不能减**，正式版这条线不能回退（装了新版的人装不上旧版）。
4. **到点提醒相关的改动必须说清验证方式**：真机 / 模拟器、前台 / 后台 / 锁屏、
   通知式 vs 闹钟式。只跑单测不算验证。
5. 中文文案请用**中文全角标点**，语气尽量口语化（这是一个给人用的 App，不是给机器看的日志）。

## 三、提交与 PR

- 一个 PR 只做一件事；改动大就先开 issue 说思路。
- 提交信息用中文或英文都行，**第一行说清「改了什么、为什么」**。
  推荐格式：`fix(提醒): 锁屏后闹钟不响`、`feat(视图): 月视图加件数角标`。
- 提 PR 前请确认：`./gradlew :app:testDebugUnitTest` 全绿；新增行为有对应测试；
  界面改动附截图（Android 截图请用真实数据，别用 `11:11` 这种假时间）。
- 不要提交：`.jks` / `keystore.properties` / `local.properties` / 构建产物 / 日志
  （`.gitignore` 已经覆盖，但请自己 `git status` 看一眼）。

## 四、报告问题

请用 issue 模板，**尽量带上这些信息**（能省很多来回）：

- 「设置 → 版本号」那一行（例如 `2.0.0（2）· 正式构建`）+ 数据库版本；
- 手机型号与 Android 版本（例如 OPPO Find X8 / Android 15）；
- 出问题的待办的**时间设置**：起止时间、提前量、提醒方式、是否周期（周期规则）；
- 现象：到点完全没响 / 晚了多久 / 响了但停不掉 / 通知上点完成没反应……
- 如果方便，附上「设置 → 事件日志」里的记录（点「导出（名称脱敏）」再粘贴，会把待办名换成 `〈待办#3：5 字〉`）。

## 五、许可

提交代码即表示同意以 **GPLv3** 发布（见 [LICENSE](LICENSE)）。
本项目不要求 CLA，也不会把你的贡献放进闭源版本。

## 六、Windows 脚本的编码约定

`scripts/*.ps1` 里有中文，而 Windows PowerShell 5.1 **在没有 BOM 时按 ANSI(GBK) 解码**：
中文会变成乱码，严重时连引号都会被解错、脚本直接语法报错。

所以：**新增或修改 `.ps1` 时请存成「UTF-8 with BOM」**（VS Code 右下角可切换）。
可以这样自查：

```powershell
$b = [IO.File]::ReadAllBytes('scripts\release.ps1')
"BOM: $($b[0]) $($b[1]) $($b[2])"   # 应该是 239 187 191
[System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path 'scripts\release.ps1'), [ref]$null, [ref]$e)
$e.Count                              # 应该是 0
```

`.yml` / `.md` / `.kt` 一律 UTF-8 **无 BOM**（GitHub Actions 与 Gradle 都不喜欢 BOM）。