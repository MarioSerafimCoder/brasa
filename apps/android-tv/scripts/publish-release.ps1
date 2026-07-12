[CmdletBinding()]param()
$ErrorActionPreference="Stop";$root=Resolve-Path (Join-Path $PSScriptRoot "..\..\..");$notes=@();Write-Host "Digite uma nota por linha. Pressione Enter em uma linha vazia para concluir."
while($notes.Count-lt 20){$note=Read-Host "Nota";if([string]::IsNullOrWhiteSpace($note)){break};if($note.Length-gt 240){throw"Cada nota pode ter no máximo 240 caracteres."};$notes+=$note.Trim()}
$temporary=Join-Path ([IO.Path]::GetTempPath()) "brasa-tv-release-notes-$PID.json"
try{[IO.File]::WriteAllText($temporary,($notes|ConvertTo-Json),(New-Object Text.UTF8Encoding($false)));Push-Location $root;try{& node scripts/publish-android-tv-release.mjs --notes-file $temporary;if($LASTEXITCODE-ne 0){throw"A publicação local falhou. Nenhum release parcial deve ser utilizado."}}finally{Pop-Location}}finally{Remove-Item -LiteralPath $temporary -Force -ErrorAction SilentlyContinue}
