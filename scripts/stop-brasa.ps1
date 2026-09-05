$StatePath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..")) ".brasa-server.json"
$Stopped = New-Object System.Collections.Generic.List[int]

function Stop-BrasaProcess {
    param(
        [int]$ProcessId,
        [string]$Reason
    )

    if ($ProcessId -le 0 -or $ProcessId -eq $PID -or $Stopped.Contains($ProcessId)) {
        return
    }

    $Process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue

    if (-not $Process -or $Process.ProcessName -ne "node") {
        return
    }

    Stop-Process -Id $ProcessId -Force -ErrorAction SilentlyContinue

    if (-not $Stopped.Contains($ProcessId)) {
        $Stopped.Add($ProcessId) | Out-Null
    }
}

if (Test-Path -LiteralPath $StatePath) {
    $State = Get-Content -Raw -LiteralPath $StatePath | ConvertFrom-Json
    Stop-BrasaProcess -ProcessId ([int]$State.pid) -Reason "estado"
}

& netstat -ano | ForEach-Object {
    $Line = $_.Trim()
    if (-not $Line.StartsWith("TCP")) {
        return
    }

    $Parts = $Line -split "\s+"
    if ($Parts.Length -lt 5 -or $Parts[3] -ne "LISTENING") {
        return
    }

    if ($Parts[1] -notmatch ":(\d+)$") {
        return
    }

    $Port = [int]$Matches[1]
    if ($Port -lt 4173 -or $Port -gt 4273) {
        return
    }

    Stop-BrasaProcess -ProcessId ([int]$Parts[4]) -Reason "porta $Port"
}

Remove-Item -LiteralPath $StatePath -Force -ErrorAction SilentlyContinue

if ($Stopped.Count) {
    Write-Host "BRasa encerrado."
} else {
    Write-Host "BRasa nao parece estar rodando."
}
