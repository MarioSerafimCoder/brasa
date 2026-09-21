param(
    [ValidateRange(1, 65535)]
    [int]$Port = 4173
)

$ErrorActionPreference = "Stop"
$taskName = "BRasa - Preparar rede local"
$ensureScript = Join-Path $PSScriptRoot "ensure-brasa-network.ps1"
$taskAction = "powershell.exe -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$ensureScript`" -Port $Port"

& schtasks.exe `
    /Create `
    /TN $taskName `
    /SC ONEVENT `
    /EC "Microsoft-Windows-NetworkProfile/Operational" `
    /MO "*[System[(EventID=10000)]]" `
    /TR $taskAction `
    /RU SYSTEM `
    /RL HIGHEST `
    /F

if ($LASTEXITCODE -ne 0) {
    throw "Nao foi possivel criar a tarefa automatica do BRasa. Codigo: $LASTEXITCODE"
}

& $ensureScript -Port $Port
Write-Host "Configuracao automatica de rede do BRasa instalada."
