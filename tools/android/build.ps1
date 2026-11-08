<#
.SYNOPSIS
Builds the Android APK: copies the native libraries of the CMake build (out/build/android-arm64)
into android/app/src/main/jniLibs/arm64-v8a, adds the Turnip driver the app falls back to
(libvulkan_turnip.so) and runs Gradle's assembleDebug, or assembleRelease with -Release (signed
with the key named in C:/dev/keys/ssarecomp-release.properties, see android/app/build.gradle).

.USAGE
    tools\android\build.ps1 [-Ndk <path>] [-Release] [-Install]
Build the native side first (see docs/android.md). -Install also installs the APK with adb.
#>
param(
    [string]$Ndk = "$env:USERPROFILE\Android\sdk\ndk\30.0.16248370",
    [string]$Sdk = "$env:USERPROFILE\Android\sdk",
    [string]$Gradle = "$env:USERPROFILE\Android\gradle-8.9\bin\gradle.bat",
    [string]$JavaHome = "",
    [string]$RenderDocLayer = "C:\dev\tools\rdlayer\lib\arm64-v8a\libVkLayer_GLES_RenderDoc.so",
    [string]$Driver = "C:\dev\tools\turnip\T30\vulkan.purple.so",
    [switch]$RenderDoc,
    [switch]$Release,
    [switch]$Install
)
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$BuildDir = Join-Path $Root "out\build\android-arm64"
$JniDir = Join-Path $Root "android\app\src\main\jniLibs\arm64-v8a"
if (-not (Test-Path $Ndk)) { $Ndk = Join-Path $Sdk "ndk\30.0.16248370" }

New-Item -ItemType Directory -Force $JniDir | Out-Null
Get-ChildItem $JniDir -Filter *.so | Remove-Item -Force

# Every shared library of the build (the game is libmain.so), except test programs' helpers.
$libs = Get-ChildItem $BuildDir -Recurse -Filter *.so | Where-Object { $_.FullName -notmatch '\\(CMakeFiles|_deps\\.*-subbuild)\\' }
if (-not ($libs | Where-Object Name -eq 'libmain.so')) { throw "libmain.so not found in ${BuildDir}: build the native side first" }
foreach ($l in $libs) { Copy-Item $l.FullName (Join-Path $JniDir $l.Name) -Force }
$cxx = Join-Path $Ndk "toolchains\llvm\prebuilt\windows-x86_64\sysroot\usr\lib\aarch64-linux-android\libc++_shared.so"
Copy-Item $cxx $JniDir -Force
# The Turnip driver (Mesa, MIT): SSAActivity uses it on Adreno GPUs unless files/drivers has one.
if (Test-Path $Driver) { Copy-Item $Driver (Join-Path $JniDir "libvulkan_turnip.so") -Force }
else { Write-Warning "Turnip driver not found ($Driver): the APK uses the system driver" }
# -RenderDoc: ship RenderDoc's capture layer (debug builds only; start with --vulkan_renderdoc_layer=true).
if ($RenderDoc) {
    if (-not (Test-Path $RenderDocLayer)) { throw "RenderDoc layer not found: $RenderDocLayer" }
    Copy-Item $RenderDocLayer $JniDir -Force
}

# Release builds would be stripped by Gradle; debug keeps symbols for tombstones.
Get-ChildItem $JniDir -Filter *.so | ForEach-Object { "{0,-32} {1,8:N1} MB" -f $_.Name, ($_.Length / 1MB) }

if (-not $JavaHome) {
    $JavaHome = (Get-ChildItem "$env:USERPROFILE\Android" -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1).FullName
    if (-not $JavaHome) { $JavaHome = (Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-17*' | Select-Object -First 1).FullName }
}
$env:JAVA_HOME = $JavaHome
Push-Location (Join-Path $Root "android")
try {
    # Gradle writes notes to stderr; PowerShell 5.1 would treat them as errors under "Stop".
    $ErrorActionPreference = "Continue"
    $task = if ($Release) { "assembleRelease" } else { "assembleDebug" }
    & $Gradle --quiet $task 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    $ErrorActionPreference = "Stop"
    if ($code -ne 0) { throw "gradle failed" }
} finally { Pop-Location }
$apk = if ($Release) { Join-Path $Root "android\app\build\outputs\apk\release\app-release.apk" }
       else { Join-Path $Root "android\app\build\outputs\apk\debug\app-debug.apk" }
"APK: $apk ({0:N0} MB)" -f ((Get-Item $apk).Length / 1MB)

if ($Install) {
    & "C:\platform-tools\adb.exe" install -r $apk
}
