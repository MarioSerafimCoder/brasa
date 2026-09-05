[CmdletBinding()]
param()
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$defaultKeyStore = Join-Path $HOME ".brasa\signing\brasa-tv-release.jks"
$keytool = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\keytool.exe"))) { Join-Path $env:JAVA_HOME "bin\keytool.exe" } else { (Get-Command keytool.exe -ErrorAction SilentlyContinue).Source }
if (-not $keytool) { throw "keytool não foi encontrado. Configure JAVA_HOME com um JDK 17 ou superior." }
if (Test-Path -LiteralPath $defaultKeyStore) { throw "A chave já existe em $defaultKeyStore. Nada foi sobrescrito." }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $defaultKeyStore) | Out-Null
$alias = Read-Host "Alias da chave [brasa-tv-release]"
if ([string]::IsNullOrWhiteSpace($alias)) { $alias = "brasa-tv-release" }
if ($alias -notmatch '^[A-Za-z0-9._-]{3,64}$') { throw "Alias inválido." }
$storeSecure = Read-Host "Senha do keystore (mínimo 12 caracteres)" -AsSecureString
$storeConfirm = Read-Host "Confirme a senha do keystore" -AsSecureString
$keySecure = Read-Host "Senha da chave (mínimo 12 caracteres)" -AsSecureString
$keyConfirm = Read-Host "Confirme a senha da chave" -AsSecureString
$certificateName = Read-Host "Nome do certificado [BRasa TV]"
if ([string]::IsNullOrWhiteSpace($certificateName)) { $certificateName = "BRasa TV" }
$organization = Read-Host "Organização [BRasa]"
if ([string]::IsNullOrWhiteSpace($organization)) { $organization = "BRasa" }
$city = Read-Host "Cidade [Local]"
if ([string]::IsNullOrWhiteSpace($city)) { $city = "Local" }
$state = Read-Host "Estado [Local]"
if ([string]::IsNullOrWhiteSpace($state)) { $state = "Local" }
$country = Read-Host "País, código de 2 letras [BR]"
if ([string]::IsNullOrWhiteSpace($country)) { $country = "BR" }
$certificateFields=@($certificateName,$organization,$city,$state)
if($certificateFields.Where({$_ -notmatch '^[^,=+"<>#;]{2,64}$'}).Count -gt 0){throw "Os dados do certificado contêm caracteres inválidos."}
if($country -notmatch '^[A-Za-z]{2}$'){throw "O país deve usar exatamente duas letras."}
$distinguishedName="CN=$certificateName, OU=$organization, O=$organization, L=$city, ST=$state, C=$($country.ToUpperInvariant())"
function Convert-Secure([Security.SecureString]$value) { $ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($value);try{[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)}finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)} }
$storePassword=Convert-Secure $storeSecure;$storeConfirmation=Convert-Secure $storeConfirm;$keyPassword=Convert-Secure $keySecure;$keyConfirmation=Convert-Secure $keyConfirm
$temporaryCertificate=Join-Path ([IO.Path]::GetTempPath()) ("brasa-tv-release-"+[Guid]::NewGuid().ToString("N")+".der")
try {
    if ($storePassword.Length -lt 12 -or $keyPassword.Length -lt 12) { throw "Use senhas com pelo menos 12 caracteres." }
    if ($storePassword -cne $storeConfirmation -or $keyPassword -cne $keyConfirmation) { throw "As confirmações de senha não coincidem." }
    $env:BRASA_TV_KEYSTORE_PASSWORD=$storePassword;$env:BRASA_TV_KEY_PASSWORD=$keyPassword
    & $keytool -genkeypair -keystore $defaultKeyStore -storetype JKS -alias $alias -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10950 -dname $distinguishedName -storepass:env BRASA_TV_KEYSTORE_PASSWORD -keypass:env BRASA_TV_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $defaultKeyStore)) { throw "Não foi possível criar a chave release." }
    & $keytool -exportcert -keystore $defaultKeyStore -storetype JKS -alias $alias -file $temporaryCertificate -storepass:env BRASA_TV_KEYSTORE_PASSWORD
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $temporaryCertificate)) { throw "Não foi possível exportar o certificado público." }
    $fingerprint=(Get-FileHash -LiteralPath $temporaryCertificate -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($fingerprint -notmatch '^[A-F0-9]{64}$') { throw "Não foi possível extrair o fingerprint público." }
    [IO.File]::WriteAllText((Join-Path $projectRoot "release-certificate.sha256"),$fingerprint+[Environment]::NewLine,(New-Object Text.UTF8Encoding($false)))
    Write-Host "Chave criada em: $defaultKeyStore"
    Write-Host "Fingerprint público salvo em release-certificate.sha256."
    Write-Warning "Faça backup seguro da chave e das credenciais. Perder a chave impede atualizar instalações existentes."
} finally {
    Remove-Item -LiteralPath $temporaryCertificate -Force -ErrorAction SilentlyContinue
    Remove-Item Env:BRASA_TV_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue;Remove-Item Env:BRASA_TV_KEY_PASSWORD -ErrorAction SilentlyContinue
    $storePassword=$storeConfirmation=$keyPassword=$keyConfirmation=$null
}
