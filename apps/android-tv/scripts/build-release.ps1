[CmdletBinding()]
param([string]$KeyStorePath=(Join-Path $HOME ".brasa\signing\brasa-tv-release.jks"),[string]$KeyAlias="brasa-tv-release")
$ErrorActionPreference="Stop";$projectRoot=Split-Path -Parent $PSScriptRoot
if(-not(Test-Path -LiteralPath $KeyStorePath)){throw "Keystore não encontrado em $KeyStorePath. Execute setup-release-signing.ps1 primeiro."}
$storeSecure=Read-Host "Senha do keystore" -AsSecureString;$keySecure=Read-Host "Senha da chave" -AsSecureString
function Convert-Secure([Security.SecureString]$value){$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($value);try{[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)}}
$storePassword=Convert-Secure $storeSecure;$keyPassword=Convert-Secure $keySecure
try{
    $env:BRASA_TV_KEYSTORE_PATH=(Resolve-Path -LiteralPath $KeyStorePath).Path;$env:BRASA_TV_KEYSTORE_PASSWORD=$storePassword;$env:BRASA_TV_KEY_ALIAS=$KeyAlias;$env:BRASA_TV_KEY_PASSWORD=$keyPassword
    Push-Location $projectRoot
    try{& .\gradlew.bat test lint assembleRelease;if($LASTEXITCODE-ne 0){throw "Testes, lint ou build release falharam."}}finally{Pop-Location}
    $apk=Join-Path $projectRoot "app\build\outputs\apk\release\app-release.apk";if(-not(Test-Path $apk)){throw "APK release não foi gerado."}
    $sdkRoot=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $projectRoot ".toolchain\android-sdk"}
    $apksigner=Get-ChildItem (Join-Path $sdkRoot "build-tools") -Recurse -Filter apksigner.bat -ErrorAction SilentlyContinue|Sort-Object FullName -Descending|Select-Object -First 1
    if(-not$apksigner){throw "apksigner não encontrado no Android SDK."};& $apksigner.FullName verify --verbose --print-certs $apk;if($LASTEXITCODE-ne 0){throw "O APK não possui assinatura release válida."}
    $properties=ConvertFrom-StringData (Get-Content (Join-Path $projectRoot "version.properties") -Raw);$item=Get-Item $apk;$hash=(Get-FileHash $apk -Algorithm SHA256).Hash
    Write-Host "Release $($properties.VERSION_NAME) ($($properties.VERSION_CODE))";Write-Host "APK: $apk";Write-Host "Tamanho: $($item.Length) bytes";Write-Host "SHA-256: $hash"
}finally{Remove-Item Env:BRASA_TV_KEYSTORE_PATH,Env:BRASA_TV_KEYSTORE_PASSWORD,Env:BRASA_TV_KEY_ALIAS,Env:BRASA_TV_KEY_PASSWORD -ErrorAction SilentlyContinue;$storePassword=$keyPassword=$null}
