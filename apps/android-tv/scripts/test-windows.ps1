[CmdletBinding()]
param([string[]]$Tasks = @('testDebugUnitTest', 'lintDebug', 'assembleDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdk = Join-Path $projectRoot '.toolchain\android-sdk'
if (!$env:ANDROID_HOME -and (Test-Path -LiteralPath $sdk)) { $env:ANDROID_HOME = $sdk }
$jdk = Get-ChildItem (Join-Path $projectRoot '.toolchain\jdk') -Directory -ErrorAction SilentlyContinue |
    Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
if (!$env:JAVA_HOME -and $jdk) { $env:JAVA_HOME = $jdk.FullName }

# Robolectric's Windows native loader cannot open a runtime URL containing %20.
# Copy every cached SDK runtime, not only the newest, so mixed SDK suites work.
$cached = @(Get-ChildItem (Join-Path $env:USERPROFILE '.m2\repository\org\robolectric\android-all-instrumented') -Recurse -Filter 'android-all-instrumented-*.jar' -ErrorAction SilentlyContinue)
$arguments = @($Tasks)
if ($cached.Count -gt 0) {
    $runtime = Join-Path $env:PUBLIC 'BRasaTVTestRuntime'
    New-Item -ItemType Directory -Path $runtime -Force | Out-Null
    foreach ($jar in $cached) {
        $copy = Join-Path $runtime $jar.Name
        if (!(Test-Path -LiteralPath $copy) -or (Get-Item -LiteralPath $copy).Length -ne $jar.Length) {
            Copy-Item -LiteralPath $jar.FullName -Destination $copy -Force
        }
    }
    $arguments += "-PbrasaRobolectricRuntimeDir=$($runtime.Replace('\', '/'))"
} else {
    Write-Host 'Runtime do Robolectric ainda não está em cache. A primeira execução permite o download; repita este comando se o carregador nativo reclamar de %20.'
}
Push-Location $projectRoot
try {
    & .\gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) { throw 'A validação Android falhou. Consulte o relatório em app/build/reports.' }
} finally { Pop-Location }
