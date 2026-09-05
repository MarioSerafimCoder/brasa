[CmdletBinding()]
param([Parameter(Mandatory=$true,Position=0)][ValidateSet("patch","minor","major")][string]$Part)
$ErrorActionPreference="Stop";$file=Join-Path (Split-Path -Parent $PSScriptRoot) "version.properties";$values=ConvertFrom-StringData (Get-Content $file -Raw)
if($values.VERSION_CODE-notmatch'^\d+$'-or[int64]$values.VERSION_CODE-lt 1){throw"VERSION_CODE inválido."};if($values.VERSION_NAME-notmatch'^(\d+)\.(\d+)\.(\d+)$'){throw"VERSION_NAME deve estar no formato X.Y.Z."}
$major=[int]$Matches[1];$minor=[int]$Matches[2];$patch=[int]$Matches[3];$oldName=$values.VERSION_NAME;$oldCode=[int64]$values.VERSION_CODE
switch($Part){"major"{$major++;$minor=0;$patch=0};"minor"{$minor++;$patch=0};"patch"{$patch++}}
$newCode=$oldCode+1;$newName="$major.$minor.$patch";[IO.File]::WriteAllText($file,"VERSION_CODE=$newCode`nVERSION_NAME=$newName`n",(New-Object Text.UTF8Encoding($false)))
Write-Host "Versão: $oldName ($oldCode) -> $newName ($newCode)";Write-Host "Nenhum commit foi criado."
