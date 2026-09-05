[CmdletBinding()]
param([string]$DownloadUrl="https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip")
$ErrorActionPreference="Stop"
$root=Split-Path -Parent $PSScriptRoot
$tools=Join-Path $root "tools\ffmpeg"
$archive=Join-Path $env:TEMP "brasa-ffmpeg-release-essentials.zip"
$extract=Join-Path $env:TEMP "brasa-ffmpeg-extract"
if(Test-Path (Join-Path $tools "ffmpeg.exe")){Write-Host "FFmpeg já está instalado no BRasa.";exit 0}
if(Test-Path $extract){$resolved=(Resolve-Path $extract).Path;if(-not $resolved.StartsWith((Resolve-Path $env:TEMP).Path)){throw "Diretório temporário inválido."};Remove-Item -LiteralPath $resolved -Recurse -Force}
Invoke-WebRequest -Uri $DownloadUrl -OutFile $archive
Expand-Archive -LiteralPath $archive -DestinationPath $extract -Force
$bin=Get-ChildItem -LiteralPath $extract -Directory|ForEach-Object{Join-Path $_.FullName "bin"}|Where-Object{Test-Path (Join-Path $_ "ffmpeg.exe")}|Select-Object -First 1
if(-not $bin){throw "O pacote baixado não contém ffmpeg.exe."}
New-Item -ItemType Directory -Path $tools -Force|Out-Null
Copy-Item -LiteralPath (Join-Path $bin "ffmpeg.exe"),(Join-Path $bin "ffprobe.exe") -Destination $tools -Force
& (Join-Path $tools "ffmpeg.exe") -version|Select-Object -First 1
Write-Host "FFmpeg e FFprobe instalados em $tools"
