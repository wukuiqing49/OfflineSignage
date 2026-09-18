param([Parameter(Mandatory = $true)][string]$Apk)

$ErrorActionPreference = 'Stop'
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
$buildTools = Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and [version]$_.Name -ge [version]'35.0.0' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildTools) { throw 'Android SDK Build Tools 35+ are required for artifact verification' }
$apkPath = (Resolve-Path -LiteralPath $Apk).Path
$logDirectory = Join-Path $PSScriptRoot '../../build'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
& (Join-Path $buildTools.FullName 'apksigner.bat') verify --verbose $apkPath > (Join-Path $logDirectory 'apk-signature-verification.log') 2>&1
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed; see build/apk-signature-verification.log' }
& (Join-Path $buildTools.FullName 'zipalign.exe') -c -P 16 4 $apkPath > (Join-Path $logDirectory 'apk-zip-alignment.log') 2>&1
if ($LASTEXITCODE -ne 0) { throw 'APK ZIP alignment verification failed; see build/apk-zip-alignment.log' }
Write-Output 'APK signature and ZIP alignment verified. Native ELF layout and 16KB device execution require separate verification.'
