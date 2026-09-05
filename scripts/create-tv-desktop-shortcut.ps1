$ErrorActionPreference = "Stop"
$projectDirectory = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$desktopDirectory = [Environment]::GetFolderPath("DesktopDirectory")
$shortcutPath = Join-Path $desktopDirectory "Iniciar BRasa TV.lnk"
$launcherPath = Join-Path $projectDirectory "INICIAR BRASA TV.vbs"
$scriptHost = Join-Path $env:WINDIR "System32\wscript.exe"
if (-not (Test-Path -LiteralPath $launcherPath)) { throw "Inicializador da TV nao encontrado." }
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
if ((Test-Path -LiteralPath $shortcutPath) -and $shortcut.Arguments -ne ('"{0}"' -f $launcherPath)) {
    throw "Ja existe um atalho com esse nome apontando para outro destino: $shortcutPath"
}
$shortcut.TargetPath = $scriptHost
$shortcut.Arguments = '"{0}"' -f $launcherPath
$shortcut.WorkingDirectory = $projectDirectory
$shortcut.Description = "Inicia o BRasa TV e busca novos filmes e episodios"
$shortcut.IconLocation = "$scriptHost,0"
$shortcut.Save()
Write-Output "Atalho criado: $shortcutPath"
