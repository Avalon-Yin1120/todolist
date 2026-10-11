# 构建「当前 HEAD」的 APK 并归档，同时刷新版本清单。
#
# 为什么需要这个脚本：逐版本重建（build_versioned_apks.ps1）会把所有历史提交都编译一遍，
# 很慢；而每次改完代码只需要「当前这一版」的 APK 给用户装。这个脚本只建 HEAD，
# 几秒钟就能出包。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File tools\build_current_apk.ps1
#   powershell -ExecutionPolicy Bypass -File tools\build_current_apk.ps1 -Label "翻页修复"

param(
    # 归档文件名里的中文要点；不传就从提交信息里截取
    [string]$Label = ''
)

$Repo = 'D:\Download\Todolist'
$Dest = 'D:\Download\deepseek harness\dsh projects\Todolist\APK'
$ApkOut = Join-Path $Repo 'app\build\outputs\apk\debug\app-debug.apk'

$env:JAVA_HOME = 'D:\Download\java'
$env:ANDROID_HOME = 'D:\Android\sdk'
$env:ANDROID_SDK_ROOT = 'D:\Android\sdk'
$env:GRADLE_USER_HOME = 'D:\Android\gradle-home'

$ErrorActionPreference = 'Continue'
Set-Location $Repo
New-Item -ItemType Directory -Force -Path $Dest | Out-Null

# 提交信息里可能有空格与标点，文件名要干净
function Get-SafeName([string]$text) {
    $clean = $text -replace '[\\/:*?"<>|]', ''
    $clean = $clean -replace '\s+', ''
    if ($clean.Length -gt 24) { $clean = $clean.Substring(0, 24) }
    return $clean
}

$commit = (& git rev-parse --short HEAD).Trim()
$subject = (& git log -1 --pretty=%s).Trim()
# 去掉 "fix: " / "feat: " 这类前缀
$subject = $subject -replace '^[a-z]+:\s*', ''
if ([string]::IsNullOrWhiteSpace($Label)) { $Label = $subject }
$labelSafe = Get-SafeName $Label

Write-Host "构建 HEAD ($commit) ..."
& .\gradlew.bat assembleDebug --console=plain -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "构建失败"
    exit 1
}

# 版本号 = 归档目录里已有的最大 vN + 1
$existing = Get-ChildItem $Dest -Filter 'v*.apk' -ErrorAction SilentlyContinue |
    ForEach-Object { if ($_.Name -match '^v(\d+)') { [int]$Matches[1] } }
$nextVer = if ($existing) { ($existing | Measure-Object -Maximum).Maximum + 1 } else { 1 }
$version = "v$nextVer"

$name = "$($version)_$($labelSafe)_$($commit).apk"
Copy-Item $ApkOut (Join-Path $Dest $name) -Force
$size = [math]::Round((Get-Item $ApkOut).Length / 1MB, 2)

Write-Host ""
Write-Host "已归档: $name ($size MB)"
Write-Host "路径:   $(Join-Path $Dest $name)"
Write-Host "提交:   $commit  $subject"

# 刷新清单 CSV
Get-ChildItem $Dest -Filter 'v*.apk' |
    ForEach-Object {
        $v = if ($_.Name -match '^(v\d+)_(.*)_([0-9a-f]+)\.apk$') { $Matches[1] } else { '' }
        $l = if ($_.Name -match '^(v\d+)_(.*)_([0-9a-f]+)\.apk$') { $Matches[2] } else { '' }
        $c = if ($_.Name -match '^(v\d+)_(.*)_([0-9a-f]+)\.apk$') { $Matches[3] } else { '' }
        [pscustomobject]@{
            Version = $v
            Commit  = $c
            File    = $_.Name
            SizeMB  = [math]::Round($_.Length / 1MB, 2)
        }
    } | Sort-Object { [int]($_.Version -replace 'v', '') } |
    Export-Csv -Path (Join-Path $Dest '版本清单.csv') -NoTypeInformation -Encoding UTF8

Write-Host "清单已刷新: 版本清单.csv"
