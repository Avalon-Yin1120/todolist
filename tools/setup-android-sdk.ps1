# Reproduce the Android build & test environment from scratch.
#
#   powershell -ExecutionPolicy Bypass -File tools\setup-android-sdk.ps1
#   (or `pwsh -File tools\setup-android-sdk.ps1` where PowerShell 7 is installed)
#
# Steps:
#   1. download + extract Android cmdline-tools
#   2. accept SDK licenses non-interactively
#   3. install platform-tools / android-35 / build-tools 35.0.0 / emulator / system image
#   4. download + extract Gradle 8.11.1 (Tencent mirror: gradle.org is unreachable here)
#   5. create and tune the AVD
#
# Already-installed pieces are skipped, so this script is safe to re-run.
# NOTE: kept ASCII-only on purpose (cmd/PowerShell parsing of non-ASCII differs by host codepage).

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# ---------------------------------------------------------------- configuration
$SdkRoot     = 'D:\Android\sdk'
$DistDir     = 'D:\Android\dist'
$GradleHome  = 'D:\Android\gradle-8.11.1'
$JavaHome    = 'D:\Download\java'
$AvdName     = 'todo_api35'
$AvdDevice   = 'pixel_7'
$SystemImage = 'system-images;android-35;google_apis;x86_64'
$SdkPackages = @(
    'platform-tools',
    'platforms;android-35',
    'build-tools;35.0.0',
    'emulator',
    $SystemImage
)

# google() is reachable; repo.maven.apache.org is NOT, hence the Gradle mirror below.
$CmdlineToolsUrl = 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip'
$GradleZipUrl    = 'https://mirrors.cloud.tencent.com/gradle/gradle-8.11.1-bin.zip'

$SdkManager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
$AvdManager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\avdmanager.bat'
$Emulator   = Join-Path $SdkRoot 'emulator\emulator.exe'
$Adb        = Join-Path $SdkRoot 'platform-tools\adb.exe'

function Write-Step([string] $Text) {
    Write-Host ''
    Write-Host ('=== ' + $Text + ' ===') -ForegroundColor Cyan
}

function Invoke-RemoteFile([string] $Url, [string] $Destination) {
    if (Test-Path $Destination) {
        Write-Host ('skip (already downloaded): ' + $Destination)
        return
    }
    Write-Host ('download ' + $Url)
    Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing -TimeoutSec 1800
    $sizeMb = [math]::Round((Get-Item $Destination).Length / 1MB, 1)
    Write-Host ('done: ' + $Destination + ' (' + $sizeMb + ' MB)')
}

function Test-SdkPackageInstalled([string] $Package) {
    $probe = $null
    if ($Package -eq 'platform-tools') {
        $probe = Join-Path $SdkRoot 'platform-tools\adb.exe'
    } elseif ($Package -like 'platforms;*') {
        $probe = Join-Path $SdkRoot ('platforms\' + ($Package -replace 'platforms;', ''))
    } elseif ($Package -like 'build-tools;*') {
        $probe = Join-Path $SdkRoot ('build-tools\' + ($Package -replace 'build-tools;', ''))
    } elseif ($Package -eq 'emulator') {
        $probe = Join-Path $SdkRoot 'emulator\emulator.exe'
    } elseif ($Package -like 'system-images;*') {
        $probe = Join-Path $SdkRoot ($Package -replace ';', '\')
    }
    return ($probe -ne $null) -and (Test-Path $probe)
}

# ---------------------------------------------------------------- 1. cmdline-tools
Write-Step '1/5 Android cmdline-tools'

$javac = Join-Path $JavaHome 'bin\javac.exe'
if (-not (Test-Path $javac)) {
    throw ('JDK not found at ' + $javac + ' (a full JDK is required, not just a JRE)')
}
Write-Host ('JDK: ' + ((& $javac -version 2>&1 | Out-String).Trim()) + ' at ' + $JavaHome)

New-Item -ItemType Directory -Force -Path $DistDir, $SdkRoot | Out-Null

if (-not (Test-Path $SdkManager)) {
    $zip = Join-Path $DistDir 'cmdline-tools.zip'
    Invoke-RemoteFile $CmdlineToolsUrl $zip

    $tmp = Join-Path $DistDir 'cltmp'
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $tmp -Force

    $destination = Join-Path $SdkRoot 'cmdline-tools\latest'
    New-Item -ItemType Directory -Force -Path $destination | Out-Null

    # The archive contains a single cmdline-tools/ level; its contents belong in latest/.
    $inner = Join-Path $tmp 'cmdline-tools'
    $source = $tmp
    if (Test-Path $inner) { $source = $inner }
    Copy-Item -Recurse -Force (Join-Path $source '*') $destination
    Remove-Item $tmp -Recurse -Force
}
Write-Host ('sdkmanager: ' + $SdkManager)

# ---------------------------------------------------------------- 2. licenses
Write-Step '2/5 Accept SDK licenses'

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot

$yes = (1..60 | ForEach-Object { 'y' }) -join "`n"
$yes | & $SdkManager ('--sdk_root=' + $SdkRoot) --licenses 2>&1 | Select-Object -Last 3

# ---------------------------------------------------------------- 3. SDK packages
Write-Step '3/5 Install SDK packages'

$missing = @()
foreach ($package in $SdkPackages) {
    if (Test-SdkPackageInstalled $package) {
        Write-Host ('skip (installed): ' + $package)
    } else {
        $missing += $package
    }
}

if ($missing.Count -gt 0) {
    Write-Host ('installing: ' + ($missing -join ', '))
    & $SdkManager ('--sdk_root=' + $SdkRoot) @missing 2>&1 | Select-Object -Last 5
} else {
    Write-Host 'all SDK packages already present'
}
& $SdkManager ('--sdk_root=' + $SdkRoot) --list_installed 2>&1 | Select-Object -Last 12

# ---------------------------------------------------------------- 4. Gradle
Write-Step '4/5 Gradle 8.11.1 (AGP 8.9.2 requires >= 8.11.1)'

$gradleBat = Join-Path $GradleHome 'bin\gradle.bat'
if (Test-Path $gradleBat) {
    Write-Host ('skip (installed): ' + $GradleHome)
} else {
    $zip = Join-Path $DistDir 'gradle-8.11.1-bin.zip'
    Invoke-RemoteFile $GradleZipUrl $zip
    Expand-Archive -Path $zip -DestinationPath 'D:\Android' -Force
}
Write-Host ('gradle: ' + $gradleBat)

# ---------------------------------------------------------------- 5. AVD
Write-Step '5/5 Create AVD'

$existingAvds = & $Emulator -list-avds 2>&1
if ($existingAvds -contains $AvdName) {
    Write-Host ('skip (exists): ' + $AvdName)
} else {
    $no = (1..10 | ForEach-Object { 'no' }) -join "`n"
    $no | & $AvdManager create avd -n $AvdName -k $SystemImage -d $AvdDevice --force 2>&1 |
        Select-Object -Last 5
}

# Tune config.ini: more RAM, host GPU, usable keyboard.
$config = Join-Path $env:USERPROFILE ('.android\avd\' + $AvdName + '.avd\config.ini')
if (Test-Path $config) {
    $lines = Get-Content $config
    $overrides = @(
        'hw.ramSize=4096',
        'hw.gpu.mode=host',
        'disk.dataPartition.size=6442450944',
        'hw.keyboard=yes',
        'hw.lcd.density=420',
        'vm.heapSize=576'
    )
    foreach ($entry in $overrides) {
        $key = ($entry -split '=')[0]
        if ($lines -match ('^' + $key + '=')) {
            $lines = $lines -replace ('^' + $key + '=.*$'), $entry
        } else {
            $lines += $entry
        }
    }
    Set-Content -Path $config -Value $lines -Encoding UTF8
    Write-Host ('tuned: ' + $config)
}

# ---------------------------------------------------------------- self check
Write-Step 'Self check'

& $Adb version 2>&1 | Select-Object -First 1
& $gradleBat --version 2>&1 | Select-String -Pattern '^Gradle '
& $Emulator -accel-check 2>&1 | Select-Object -Last 2

Write-Host ''
Write-Host 'Environment ready. Verify a build with:' -ForegroundColor Green
Write-Host '  $env:GRADLE_USER_HOME="D:\Android\gradle-home"'
Write-Host '  cd D:\Download\Todolist'
Write-Host '  .\gradlew.bat assembleDebug'
