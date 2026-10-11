# 为每一轮提交各构建一个 APK，归档到工作区 APK 目录。
#
# 为什么要逐提交重建：build/outputs 里的 APK 会被后续构建覆盖，
# 只按字节数保存无法确定它对应哪一版代码。逐提交构建才能保证
# 归档的 APK 与 git 历史严格一一对应，方便回溯"哪一版改了什么"。
#
# 用法：powershell -ExecutionPolicy Bypass -File tools\build_versioned_apks.ps1

$Repo = 'D:\Download\Todolist'
$Dest = 'D:\Download\deepseek harness\dsh projects\Todolist\APK'
$ApkOut = Join-Path $Repo 'app\build\outputs\apk\debug\app-debug.apk'

$env:JAVA_HOME = 'D:\Download\java'
$env:ANDROID_HOME = 'D:\Android\sdk'
$env:ANDROID_SDK_ROOT = 'D:\Android\sdk'
$env:GRADLE_USER_HOME = 'D:\Android\gradle-home'

New-Item -ItemType Directory -Force -Path $Dest | Out-Null

# 版本号 -> 提交号 -> 归档文件名
# 顺序与 git 历史一致，v1 最早
$versions = @(
    @{ Ver = 'v1'; Commit = '4d27920'; Label = '首个可用版本' },
    @{ Ver = 'v2'; Commit = '843cb26'; Label = '修复日期与提前量输入' },
    @{ Ver = 'v3'; Commit = '78320c9'; Label = '周期待办双提醒日月周视图' },
    @{ Ver = 'v4'; Commit = 'ab4e55e'; Label = '修复单测卡死与降级提示' },
    @{ Ver = 'v5'; Commit = '1e473e0'; Label = '顶部按钮对称默认日视图设置独立' },
    @{ Ver = 'v6'; Commit = 'bc713f0'; Label = '表单改整屏页面' },
    @{ Ver = 'v7'; Commit = '8309ac2'; Label = '验证切换动画' },
    @{ Ver = 'v8'; Commit = '86609c9'; Label = '修复14项含闹钟滑动日期' },
    @{ Ver = 'v9'; Commit = '8299a55'; Label = '重复生效起始日与次数互算' },
    @{ Ver = 'v10'; Commit = '755018d'; Label = '自绘日历与翻页修复' }
)

Set-Location $Repo

# 刻意不使用 $ErrorActionPreference='Stop'：
# git 的正常提示（如 "Previous HEAD position was ..."）输出到 stderr，
# 在 Stop 模式下会被当成终止性错误，脚本会在建第一个版本时就中断。
# 这里改为每一步显式检查 $LASTEXITCODE。
$ErrorActionPreference = 'Continue'

# 记录起始状态，结束后恢复
$startRef = (& git rev-parse --abbrev-ref HEAD).Trim()
$dirty = & git status --porcelain
if ($dirty) {
    Write-Host "工作区有未提交改动，先 stash 以保证构建对应该提交："
    Write-Host $dirty
    & git stash push -u -m 'build-versioned-apks'
    $stashed = $true
} else {
    $stashed = $false
}

$built = @()
$failed = $false
try {
    foreach ($v in $versions) {
        Write-Host ""
        Write-Host "=== 构建 $($v.Ver) @ $($v.Commit)（$($v.Label)） ==="

        & git checkout --detach $v.Commit --quiet
        if ($LASTEXITCODE -ne 0) { throw "切换到 $($v.Commit) 失败" }

        & .\gradlew.bat assembleDebug --console=plain -q
        if ($LASTEXITCODE -ne 0) { throw "$($v.Ver) 构建失败" }

        $name = "$($v.Ver)_$($v.Label)_$($v.Commit).apk"
        Copy-Item $ApkOut (Join-Path $Dest $name) -Force
        $size = [math]::Round((Get-Item $ApkOut).Length / 1MB, 2)
        Write-Host "  -> $name ($size MB)"
        $built += [pscustomobject]@{
            Version = $v.Ver
            Commit  = $v.Commit
            File    = $name
            SizeMB  = $size
        }
    }
} catch {
    Write-Host "构建中断：$_"
    $failed = $true
} finally {
    & git checkout $startRef --quiet
    if ($stashed) {
        Write-Host ""
        Write-Host "恢复 stash"
        & git stash pop
    }
    Write-Host "已回到分支 $startRef"
}

Write-Host ""
Write-Host "=== 归档结果 ==="
$built | Format-Table -AutoSize
if ($built.Count -gt 0) {
    $built | Export-Csv -Path (Join-Path $Dest '版本清单.csv') -NoTypeInformation -Encoding UTF8
    Write-Host "清单已写入 $Dest\版本清单.csv"
}
if ($failed) { exit 1 }
