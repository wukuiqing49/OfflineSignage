param([switch]$SkipRelease)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Push-Location $projectRoot
try {
    python -X utf8 -m unittest discover -s .agents/scripts/tests
    if ($LASTEXITCODE -ne 0) { throw 'Python regression tests failed' }
    python -X utf8 .agents/scripts/validate_android_workflows.py --project-root . --skip-figma
    if ($LASTEXITCODE -ne 0) { throw 'Android workflow validation failed' }
    $verificationTasks = @(':app:testDebugUnitTest', ':feature:feature_app:testDebugUnitTest', ':core:core_google:testDebugUnitTest', ':app:lintDebug', ':feature:feature_app:lintDebug', ':core:core_google:lintDebug', ':app:assembleDebug')
    & ./gradlew.bat @verificationTasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed' }
    if (-not $SkipRelease) {
        & ./gradlew.bat :app:assembleRelease --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }
    }
    $artifactPath = if ($SkipRelease) { 'app/build/outputs/apk/debug/app-debug.apk' } else { 'app/build/outputs/apk/release/app-release.apk' }
    python -X utf8 .agents/scripts/validate_release_readiness.py --project-root . --apk $artifactPath --require-apk --require-tests
    if ($LASTEXITCODE -ne 0) { throw 'Artifact readiness validation failed' }
    & ./.agents/scripts/verify_apk.ps1 -Apk $artifactPath
    Get-FileHash -LiteralPath $artifactPath -Algorithm SHA256
    Write-Output 'Automated checks passed. Device, payment, power-loss and endurance acceptance are still required.'
} finally {
    Pop-Location
}
