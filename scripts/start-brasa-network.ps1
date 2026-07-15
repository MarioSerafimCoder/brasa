$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$StatePath = Join-Path $Root ".brasa-server.json"
$SettingsPath = Join-Path $Root "data\network-settings.json"
$ServerScript = Join-Path $Root "scripts\brasa-server.mjs"

function Get-RunningState {
    if (-not (Test-Path -LiteralPath $StatePath)) { return $null }
    try {
        $state = Get-Content -Raw -LiteralPath $StatePath | ConvertFrom-Json
        $process = Get-Process -Id ([int]$state.pid) -ErrorAction SilentlyContinue
        if ($process -and $process.ProcessName -eq "node" -and [int]$state.port -gt 0) { return $state }
    } catch {}
    Remove-Item -LiteralPath $StatePath -Force -ErrorAction SilentlyContinue
    return $null
}

function Get-LanAddress {
    $addresses = [Net.Dns]::GetHostAddresses([Net.Dns]::GetHostName()) | Where-Object {
        $_.AddressFamily -eq [Net.Sockets.AddressFamily]::InterNetwork -and
        -not $_.IPAddressToString.StartsWith("127.") -and
        -not $_.IPAddressToString.StartsWith("169.254.")
    }
    return @($addresses)[0].IPAddressToString
}

if (Test-Path -LiteralPath $SettingsPath) {
    $settings = Get-Content -Raw -LiteralPath $SettingsPath | ConvertFrom-Json
} else {
    $settings = [pscustomobject]@{ lanAccessEnabled = $true; pairingRequired = $true; allowNewDevices = $true; serverName = "BRasa"; maxAuthorizedDevices = 20 }
}
$settings.lanAccessEnabled = $true
$temporary = "$SettingsPath.$PID.tmp"
[IO.File]::WriteAllText($temporary, ($settings | ConvertTo-Json -Depth 5) + "`n", (New-Object Text.UTF8Encoding($false)))
Move-Item -LiteralPath $temporary -Destination $SettingsPath -Force

$state = Get-RunningState
if (-not $state) {
    $node = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
    if (-not $node -and (Test-Path -LiteralPath "C:\Program Files\nodejs\node.exe")) { $node = "C:\Program Files\nodejs\node.exe" }
    if (-not $node) { throw "Node.js nao foi encontrado. Instale o Node.js e tente novamente." }

    $pathValue = $env:Path
    [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
    [Environment]::SetEnvironmentVariable("Path", $pathValue, "Process")
    $env:BRASA_SKIP_STARTUP_SYNC = "1"
    $stdout = Join-Path $Root "data\brasa-server.stdout.log"
    $stderr = Join-Path $Root "data\brasa-server.stderr.log"
    Start-Process $node -ArgumentList ('"{0}"' -f $ServerScript) -WorkingDirectory $Root -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr | Out-Null

    $deadline = (Get-Date).AddSeconds(20)
    do { Start-Sleep -Milliseconds 250; $state = Get-RunningState } while (-not $state -and (Get-Date) -lt $deadline)
    if (-not $state) {
        $details = Get-Content -LiteralPath $stderr -Tail 5 -ErrorAction SilentlyContinue
        throw "O servidor nao iniciou. $($details -join ' ')"
    }
}

$ip = Get-LanAddress
if (-not $ip) { $ip = "IP deste computador" }
Write-Output "Servidor BRasa ativo na rede.`n`nNa TV, use: http://${ip}:$($state.port)"
