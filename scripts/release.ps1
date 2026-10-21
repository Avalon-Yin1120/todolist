<#
.SYNOPSIS
    本机构建、校验并（可选）发布「待办提醒」的 release 包到 GitHub Release。

.DESCRIPTION
    不带 -Publish 时只做：跑测试 → 出 release 包 → 三条校验 → 生成 dist\todolist-<版本>.apk
    与 dist\SHA256SUMS.txt。**不会碰远端**，适合先看看包对不对。

    带 -Publish 时再做：打 annotated tag → push main 与 tag → 用 gh 创建 Release 并上传
    APK 与 SHA256SUMS.txt。

.PARAMETER Publish
    推 tag 并创建 GitHub Release（需要已登录的 gh CLI）。

.PARAMETER Tag
    Release 的 tag，默认 `v<versionName>`。

.PARAMETER SkipTests
    跳过单元测试（不建议；只在急着复现某个打包问题时用）。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\release.ps1
        # 只出包 + 校验，产物在 dist\

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -Publish
        # 校验通过后推 tag 并发布 Release
#>
[CmdletBinding()]
param(
    [switch]$Publish,
    [string]$Tag,
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

function Step($text) { Write-Host "`n== $text ==" -ForegroundColor Cyan }
function Ok($text)   { Write-Host "   $text" -ForegroundColor Green }
function Warn($text) { Write-Host "   $text" -ForegroundColor Yellow }
function Die($text)  { Write-Host "`n!! $text" -ForegroundColor Red; exit 1 }

# ---------------------------------------------------------------- 环境
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'D:\Download\java'; Warn "JAVA_HOME 未设置，临时用 $env:JAVA_HOME" }
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = 'D:\Android\sdk'; Warn "ANDROID_HOME 未设置，临时用 $env:ANDROID_HOME" }
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = 'D:\Android\gradle-home' }

$buildTools = Get-ChildItem "$env:ANDROID_HOME\build-tools" -Directory |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { Die "找不到 build-tools，检查 ANDROID_HOME=$env:ANDROID_HOME" }
$aapt2     = Join-Path $buildTools.FullName 'aapt2.exe'
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
$gradlew   = Join-Path $repo 'gradlew.bat'

# ---------------------------------------------------------------- 0. 工作区必须干净
Step '0. 检查工作区'
$dirty = git status --porcelain
if ($dirty) {
    $dirty | ForEach-Object { Warn $_ }
    Die '工作区有未提交的改动。发版必须对应一个确定的提交，请先 commit 或 stash。'
}
$head = (git rev-parse --short HEAD).Trim()
$commitCount = (git rev-list --count HEAD).Trim()
Ok "HEAD=$head（$commitCount 个提交），工作区干净"

# 签名材料不能进版本库
$leak = git ls-files | Select-String -Pattern '(?i)(\.jks$|\.keystore$|keystore\.properties|^release/)'
if ($leak) { Die "版本库里出现了签名材料：$leak" }
if (-not (Test-Path 'release\keystore.properties')) {
    Die '缺少 release\keystore.properties。没有它打出来的包是 debug 签名，不能发。'
}
Ok '签名材料未入库，且本地已就绪'

# ---------------------------------------------------------------- 1. 测试
if (-not $SkipTests) {
    Step '1. 单元测试'
    & $gradlew :app:testDebugUnitTest --offline --console=plain
    if ($LASTEXITCODE -ne 0) { Die '单元测试失败，停止发版。' }
    Ok '测试通过'
} else {
    Warn '已跳过单元测试（-SkipTests）'
}

# ---------------------------------------------------------------- 2. 出包
Step '2. 构建 release 包'
& $gradlew :app:assembleRelease --offline --console=plain
if ($LASTEXITCODE -ne 0) { Die '构建失败。' }
$apk = 'app\build\outputs\apk\release\app-release.apk'
if (-not (Test-Path $apk)) { Die "没找到产物：$apk" }

# ---------------------------------------------------------------- 3. 三条校验
Step '3. 校验产物'
$badging = & $aapt2 dump badging $apk
$packageLine = $badging | Select-Object -First 1
Ok $packageLine
if ($packageLine -notmatch "name='com\.avalonyin1120\.todolist'") {
    Die '包名不对：正式版必须是 com.avalonyin1120.todolist（不带 .preview）。'
}
$ver = if ($packageLine -match "versionName='([^']+)'") { $Matches[1] } else { Die '读不到 versionName' }
$code = if ($packageLine -match "versionCode='([^']+)'") { $Matches[1] } else { Die '读不到 versionCode' }
if ($ver -match 'preview') { Warn "versionName=$ver 带 preview 后缀，会被显示成「预览构建」" }

$sig = & $apksigner verify --print-certs $apk 2>&1
$sigLine = $sig | Select-String -Pattern 'SHA-256 digest' | Select-Object -First 1
if ($sig -match 'CN=Android Debug') { Die '产物是 debug 签名，不能发布。' }
if (-not $sigLine) { Die '读不到签名证书。' }
Ok $sigLine.Line.Trim()

if ((& $aapt2 dump permissions $apk) -match 'INTERNET') {
    Die 'APK 里出现了 INTERNET 权限，拒绝发布。'
}
Ok '没有 INTERNET 权限'

$label = ($badging | Select-String -Pattern 'application-label:' | Select-Object -First 1).Line
Ok $label

# ---------------------------------------------------------------- 4. 归档 + 摘要
Step '4. 归档与摘要'
$dist = Join-Path $repo 'dist'
New-Item -ItemType Directory -Force $dist | Out-Null
$outName = "todolist-$ver.apk"
$outApk  = Join-Path $dist $outName
Copy-Item $apk $outApk -Force
$hash = (Get-FileHash $outApk -Algorithm SHA256).Hash.ToLower()
$sumFile = Join-Path $dist 'SHA256SUMS.txt'
"$hash  $outName" | Out-File -FilePath $sumFile -Encoding ascii
Ok "$outName（$([math]::Round((Get-Item $outApk).Length/1MB,2)) MB）"
Ok "SHA-256: $hash"
Ok "摘要文件: dist\SHA256SUMS.txt"

# ---------------------------------------------------------------- 5. 发布
if (-not $Publish) {
    Step '完成（未发布）'
    Warn '要推 tag 并创建 Release，重跑一次并加 -Publish'
    exit 0
}

Step '5. 打 tag 并发布'
if (-not $Tag) { $Tag = "v$ver" }
if ($Tag -ne "v$ver") { Die "tag（$Tag）与包内 versionName（$ver）不一致。" }

if (git tag --list $Tag) { Die "tag $Tag 已存在。要重发请先删掉它：git tag -d $Tag; git push origin :refs/tags/$Tag" }
git tag -a $Tag -m "待办提醒 $ver"
if ($LASTEXITCODE -ne 0) { Die '打 tag 失败。' }
Ok "已打 tag $Tag"

git push origin main
if ($LASTEXITCODE -ne 0) { Die 'push main 失败。' }
git push origin $Tag
if ($LASTEXITCODE -ne 0) { Die 'push tag 失败（本地 tag 已留，重试时先删）。' }
Ok '已推送 main 与 tag'

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Warn '没装 gh CLI。请到 GitHub 网页 Releases → Draft a new release 手动上传：'
    Warn "  $outApk"
    Warn "  $sumFile"
    exit 0
}

$notesFile = "docs/release-notes/$Tag.md"
$ghArgs = @('release','create',$Tag,"$outApk","$sumFile",'--title',"待办提醒 $ver")
if (Test-Path $notesFile) { $ghArgs += @('--notes-file',$notesFile) } else { $ghArgs += '--generate-notes' }
& gh @ghArgs
if ($LASTEXITCODE -ne 0) { Die 'gh release create 失败。' }

$url = (& gh release view $Tag --json url --jq '.url').Trim()
Step '发布完成'
Ok $url
