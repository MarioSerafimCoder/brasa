$NodeFromCodex = Join-Path $env:USERPROFILE ".cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe"
$Node = "node"

if (Test-Path -LiteralPath $NodeFromCodex) {
    $Node = $NodeFromCodex
}

& $Node (Join-Path $PSScriptRoot "sync-movies.mjs") @args
exit $LASTEXITCODE
