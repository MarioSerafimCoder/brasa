[CmdletBinding()]
param()
$ErrorActionPreference="Stop"
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$versionProperties=ConvertFrom-StringData (Get-Content (Join-Path (Split-Path -Parent $PSScriptRoot) "version.properties") -Raw)
$versionName=$versionProperties.VERSION_NAME

$form=New-Object Windows.Forms.Form
$form.Text="BRasa TV $versionName - Assinatura"
$form.Size=New-Object Drawing.Size(470,250)
$form.StartPosition="CenterScreen"
$form.TopMost=$true
$form.FormBorderStyle="FixedDialog"
$form.MaximizeBox=$false
$form.MinimizeBox=$false

$title=New-Object Windows.Forms.Label
$title.Text="Assinar o APK release $versionName"
$title.Font=New-Object Drawing.Font("Segoe UI",14,[Drawing.FontStyle]::Bold)
$title.AutoSize=$true;$title.Location=New-Object Drawing.Point(24,18)
$form.Controls.Add($title)

$storeLabel=New-Object Windows.Forms.Label
$storeLabel.Text="Senha do keystore";$storeLabel.AutoSize=$true;$storeLabel.Location=New-Object Drawing.Point(25,64)
$form.Controls.Add($storeLabel)
$store=New-Object Windows.Forms.TextBox
$store.Location=New-Object Drawing.Point(25,84);$store.Size=New-Object Drawing.Size(400,28);$store.UseSystemPasswordChar=$true
$form.Controls.Add($store)

$keyLabel=New-Object Windows.Forms.Label
$keyLabel.Text="Senha da chave";$keyLabel.AutoSize=$true;$keyLabel.Location=New-Object Drawing.Point(25,120)
$form.Controls.Add($keyLabel)
$key=New-Object Windows.Forms.TextBox
$key.Location=New-Object Drawing.Point(25,140);$key.Size=New-Object Drawing.Size(400,28);$key.UseSystemPasswordChar=$true
$form.Controls.Add($key)

$ok=New-Object Windows.Forms.Button
$ok.Text="Gerar APK";$ok.Location=New-Object Drawing.Point(245,178);$ok.Size=New-Object Drawing.Size(86,30);$ok.DialogResult=[Windows.Forms.DialogResult]::OK
$form.Controls.Add($ok);$form.AcceptButton=$ok
$cancel=New-Object Windows.Forms.Button
$cancel.Text="Cancelar";$cancel.Location=New-Object Drawing.Point(339,178);$cancel.Size=New-Object Drawing.Size(86,30);$cancel.DialogResult=[Windows.Forms.DialogResult]::Cancel
$form.Controls.Add($cancel);$form.CancelButton=$cancel
$form.Add_Shown({$store.Select()})

if($form.ShowDialog()-ne [Windows.Forms.DialogResult]::OK){exit 2}
if([string]::IsNullOrWhiteSpace($store.Text)-or[string]::IsNullOrWhiteSpace($key.Text)){
    [Windows.Forms.MessageBox]::Show("Preencha as duas senhas.","BRasa TV",0,48)|Out-Null;exit 3
}

$log=Join-Path $env:TEMP "brasa-tv-release-build.log"
try{
    $env:BRASA_TV_KEYSTORE_PASSWORD=$store.Text;$env:BRASA_TV_KEY_PASSWORD=$key.Text
    & (Join-Path $PSScriptRoot "build-release.ps1") *>&1 | Out-File -LiteralPath $log -Encoding utf8
    [Windows.Forms.MessageBox]::Show("APK $versionName gerado e assinado com sucesso.","BRasa TV",0,64)|Out-Null
}catch{
    $_|Out-File -LiteralPath $log -Append -Encoding utf8
    [Windows.Forms.MessageBox]::Show("Não foi possível gerar o APK. Confira as senhas. O diagnóstico foi salvo em $log","BRasa TV",0,16)|Out-Null
    exit 1
}finally{
    $store.Text="";$key.Text=""
    Remove-Item Env:BRASA_TV_KEYSTORE_PASSWORD,Env:BRASA_TV_KEY_PASSWORD -ErrorAction SilentlyContinue
    $form.Dispose()
}
