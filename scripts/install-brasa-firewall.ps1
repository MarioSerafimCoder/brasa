param(
    [ValidateRange(1, 65535)]
    [int]$Port = 4173
)

$ErrorActionPreference = "Stop"
$ruleName = "BRasa Local Server"
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = [Security.Principal.WindowsPrincipal]::new($identity)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Execute este script em um PowerShell aberto como administrador."
}

Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue |
    Remove-NetFirewallRule

New-NetFirewallRule `
    -DisplayName $ruleName `
    -Description "Permite o servidor BRasa somente na rede local privada." `
    -Direction Inbound `
    -Action Allow `
    -Enabled True `
    -Profile Private `
    -Protocol TCP `
    -LocalPort $Port `
    -RemoteAddress LocalSubnet | Out-Null

Write-Host "Regra '$ruleName' instalada para TCP $Port, perfil Private e LocalSubnet."
