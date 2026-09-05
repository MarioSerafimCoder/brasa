$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$StatePath = Join-Path $Root ".brasa-server.json"
$SettingsPath = Join-Path $Root "data\network-settings.json"
$ServerScript = Join-Path $Root "scripts\brasa-server.mjs"
$PreparedMediaPath = Join-Path $Root "data\prepared-media"
$LauncherLog = Join-Path $Root "data\brasa-launcher.log"

trap {
    [IO.File]::WriteAllText($LauncherLog, $_.Exception.Message, (New-Object Text.UTF8Encoding($false)))
    Write-Error $_.Exception.Message
    exit 1
}

function Initialize-PreparedMediaCache {
    $item = Get-Item -LiteralPath $PreparedMediaPath -Force -ErrorAction SilentlyContinue
    if ($item -and (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) {
        try {
            # Test-Path pode considerar uma junction válida mesmo quando o disco
            # de destino está ausente, então forçamos uma leitura do conteúdo.
            [IO.Directory]::GetFileSystemEntries($PreparedMediaPath) | Out-Null
        } catch {
            # Removemos somente o vínculo, sem tentar acessar ou apagar o destino.
            [IO.Directory]::Delete($PreparedMediaPath, $false)
            $item = $null
        }
    }

    if (-not $item) {
        New-Item -ItemType Directory -Path $PreparedMediaPath -Force | Out-Null
    }

    $probe = Join-Path $PreparedMediaPath ".brasa-write-test-$PID.tmp"
    try {
        [IO.File]::WriteAllText($probe, "ok", (New-Object Text.UTF8Encoding($false)))
    } finally {
        Remove-Item -LiteralPath $probe -Force -ErrorAction SilentlyContinue
    }
}

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
        $_.IPAddressToString -match '^(192\.168\.|10\.|172\.(1[6-9]|2[0-9]|3[01])\.)'
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

Initialize-PreparedMediaCache

$state = Get-RunningState
if (-not $state) {
    $node = (Get-Command node.exe -ErrorAction SilentlyContinue).Source
    if (-not $node -and (Test-Path -LiteralPath "C:\Program Files\nodejs\node.exe")) { $node = "C:\Program Files\nodejs\node.exe" }
    if (-not $node) { throw "Node.js nao foi encontrado. Instale o Node.js e tente novamente." }

    $pathValue = $env:Path
    [Environment]::SetEnvironmentVariable("PATH", $null, "Process")
    [Environment]::SetEnvironmentVariable("Path", $pathValue, "Process")
    # A varredura ocorre em segundo plano depois que o servidor começa a responder.
    # Não a ignore: arquivos adicionados enquanto o BRasa estava fechado também
    # precisam entrar no catálogo da TV.
    Remove-Item Env:BRASA_SKIP_STARTUP_SYNC -ErrorAction SilentlyContinue
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

# Solicitar tambem ao reutilizar o processo: o servidor pode ter ficado aberto
# antes de novos arquivos serem copiados. O POST apenas agenda a busca (202).
$baseUrl = "http://127.0.0.1:$($state.port)"
$deadline = (Get-Date).AddSeconds(20)
$ready = $false
do {
    try {
        $bootstrap = Invoke-RestMethod -Uri "$baseUrl/api/v1/bootstrap" -TimeoutSec 2
        $ready = $bootstrap.ok -eq $true
    } catch { Start-Sleep -Milliseconds 250 }
} while (-not $ready -and (Get-Date) -lt $deadline)
if (-not $ready) { throw "O servidor nao respondeu. Tente abrir o BRasa TV novamente." }
$scan = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/library/scan" -Headers @{ "X-BRasa-Request" = "1"; "Origin" = $baseUrl } -TimeoutSec 10
if (-not $scan.ok -or -not $scan.data.id) { throw "O servidor nao confirmou a busca de novos titulos." }
[IO.File]::WriteAllText($LauncherLog, "Busca de novos titulos solicitada: $($scan.data.id)", (New-Object Text.UTF8Encoding($false)))

$ip = Get-LanAddress
if (-not $ip) { $ip = "IP deste computador" }
Write-Output "Servidor BRasa ativo na rede. Busca de novos titulos iniciada em segundo plano.`n`nNa TV, use: http://${ip}:$($state.port)"
