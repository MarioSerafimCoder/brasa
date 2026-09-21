param(
    [ValidateRange(1, 65535)]
    [int]$Port = 4173
)

$ErrorActionPreference = "Stop"
$trustedWifiNames = @(
    "MORORO - Oi Fibra",
    "ZTE_8A06"
)

foreach ($profile in @(Get-NetConnectionProfile -ErrorAction SilentlyContinue)) {
    $trustedWifi = $profile.InterfaceAlias -eq "Wi-Fi" -and $profile.Name -in $trustedWifiNames
    $trustedEthernet = $profile.InterfaceAlias -like "Ethernet*"

    if (($trustedWifi -or $trustedEthernet) -and $profile.NetworkCategory -ne "Private") {
        Set-NetConnectionProfile -InterfaceIndex $profile.InterfaceIndex -NetworkCategory Private
    }
}

$firewallScript = Join-Path $PSScriptRoot "install-brasa-firewall.ps1"
& $firewallScript -Port $Port
