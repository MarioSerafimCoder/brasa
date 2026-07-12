[CmdletBinding()]
param([string]$KeyStorePath=(Join-Path $HOME ".brasa\signing\brasa-tv-release.jks"),[string]$KeyAlias="")
$ErrorActionPreference="Stop";$projectRoot=Split-Path -Parent $PSScriptRoot
if(-not(Test-Path -LiteralPath $KeyStorePath)){throw "Keystore não encontrado em $KeyStorePath. Execute setup-release-signing.ps1 primeiro."}
$keytool=if($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\keytool.exe"))){Join-Path $env:JAVA_HOME "bin\keytool.exe"}else{(Get-Command keytool.exe -ErrorAction SilentlyContinue).Source}
if(-not $keytool){throw "keytool não foi encontrado. Configure JAVA_HOME com um JDK 17 ou superior."}
$storeSecure=Read-Host "Senha do keystore" -AsSecureString;$keySecure=Read-Host "Senha da chave" -AsSecureString
function Convert-Secure([Security.SecureString]$value){$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($value);try{[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)}}
$storePassword=Convert-Secure $storeSecure;$keyPassword=Convert-Secure $keySecure
try{
    $env:BRASA_TV_KEYSTORE_PATH=(Resolve-Path -LiteralPath $KeyStorePath).Path;$env:BRASA_TV_KEYSTORE_PASSWORD=$storePassword;$env:BRASA_TV_KEY_PASSWORD=$keyPassword
    if([string]::IsNullOrWhiteSpace($KeyAlias)){
        $previousErrorAction=$ErrorActionPreference
        try{$ErrorActionPreference="Continue";$listing=& $keytool -list -keystore $env:BRASA_TV_KEYSTORE_PATH -storetype JKS -storepass:env BRASA_TV_KEYSTORE_PASSWORD 2>$null;$listExitCode=$LASTEXITCODE}finally{$ErrorActionPreference=$previousErrorAction}
        if($listExitCode -ne 0){throw "Não foi possível abrir o keystore. Confira a senha informada."}
        $aliases=@($listing|ForEach-Object{if($_.ToString() -match '^\s*([A-Za-z0-9._-]{3,64}),\s'){$Matches[1]}}|Select-Object -Unique)
        if($aliases.Count -ne 1){throw "O keystore deve conter exatamente uma chave release para detectar o alias com segurança."}
        $KeyAlias=$aliases[0]
    }
    if($KeyAlias -notmatch '^[A-Za-z0-9._-]{3,64}$'){throw "Alias de chave inválido."}
    $env:BRASA_TV_KEY_ALIAS=$KeyAlias
    Push-Location $projectRoot
    try{& .\gradlew.bat test lint assembleRelease;if($LASTEXITCODE-ne 0){throw "Testes, lint ou build release falharam."}}finally{Pop-Location}
    $apk=Join-Path $projectRoot "app\build\outputs\apk\release\app-release.apk";if(-not(Test-Path $apk)){throw "APK release não foi gerado."}
    $sdkRoot=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $projectRoot ".toolchain\android-sdk"}
    $apksigner=Get-ChildItem (Join-Path $sdkRoot "build-tools") -Recurse -Filter apksigner.bat -ErrorAction SilentlyContinue|Sort-Object FullName -Descending|Select-Object -First 1
    if(-not$apksigner){throw "apksigner não encontrado no Android SDK."};$signatureOutput=& $apksigner.FullName verify --verbose --print-certs $apk;if($LASTEXITCODE-ne 0){throw "O APK não possui assinatura release válida."}
    if(($signatureOutput-join "`n")-match 'CN=Android Debug'){throw "APK assinado com certificado debug foi bloqueado."}
    $actualFingerprint=(($signatureOutput|Select-String -Pattern 'certificate SHA-256 digest:\s*([A-Fa-f0-9]{64})').Matches.Groups[1].Value).ToUpperInvariant()
    $expectedFingerprint=(Get-Content (Join-Path $projectRoot "release-certificate.sha256") -Raw).Trim().ToUpperInvariant()
    if($actualFingerprint -notmatch '^[A-F0-9]{64}$' -or $actualFingerprint -cne $expectedFingerprint){throw "O certificado do APK não corresponde ao fingerprint release configurado."}
    $signatureOutput|Write-Host
    $properties=ConvertFrom-StringData (Get-Content (Join-Path $projectRoot "version.properties") -Raw);$item=Get-Item $apk;$hash=(Get-FileHash $apk -Algorithm SHA256).Hash
    Write-Host "Release $($properties.VERSION_NAME) ($($properties.VERSION_CODE))";Write-Host "APK: $apk";Write-Host "Tamanho: $($item.Length) bytes";Write-Host "SHA-256: $hash"
}finally{Remove-Item Env:BRASA_TV_KEYSTORE_PATH,Env:BRASA_TV_KEYSTORE_PASSWORD,Env:BRASA_TV_KEY_ALIAS,Env:BRASA_TV_KEY_PASSWORD -ErrorAction SilentlyContinue;$storePassword=$keyPassword=$null}
