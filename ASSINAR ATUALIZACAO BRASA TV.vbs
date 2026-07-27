Option Explicit

Dim shell, files, root, scriptPath, command
Set shell = CreateObject("WScript.Shell")
Set files = CreateObject("Scripting.FileSystemObject")

root = files.GetParentFolderName(WScript.ScriptFullName)
scriptPath = files.BuildPath(root, "apps\android-tv\scripts\build-release-gui.ps1")

If Not files.FileExists(scriptPath) Then
    MsgBox "O assistente de assinatura nao foi encontrado.", 16, "BRasa TV"
    WScript.Quit 1
End If

command = "powershell.exe -NoProfile -STA -ExecutionPolicy Bypass -File """ & scriptPath & """"
' A janela do processo precisa permanecer visível para que o formulário de
' assinatura apareça em primeiro plano. O formulário mascara as duas senhas.
shell.Run command, 1, False
